package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.FredRegeln
import de.troetenrepublik.app.engine.GdpNow
import de.troetenrepublik.app.engine.GdpNowAtlanta
import de.troetenrepublik.app.engine.Splits
import de.troetenrepublik.app.i18n.KursRueckfallTexte
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// 0.78: Tiingo und Twelve Data als Rueckfaelle mit Schluessel der Person (Attrappen-Antworten nach dem Aufbau der
// Dokumentation, gegen die echten Dienste lief nichts), Reihenfolge der Kette, FRED ohne Zwischenspeicher mit Zitierung
// und GDPNow aus der Tabelle der Atlanta Fed.
class Fassung078U55Test {
    private val tag = 86_400_000L
    private fun epoch(j: Int, m: Int, t: Int) = LocalDate.of(j, m, t).toEpochDay()

    // 12.11.2026, 12 Uhr UTC
    private val mittag = epoch(2026, 11, 12) * tag + 12 * 3_600_000L

    // Anbieter im Test: antwortet je Adresse, merkt sich Adressen und Kopfzeilen
    private class Netz(var antwort: (String) -> MarketApi.Antwort) {
        val adressen = ArrayList<String>()
        val koepfe = ArrayList<Map<String, String>>()
        val abruf: suspend (String, Map<String, String>) -> MarketApi.Antwort = { a, k -> adressen += a; koepfe += k; antwort(a) }
    }

    private fun ok(text: String) = MarketApi.Antwort(200, text)

    // ---------------------------------------------------------------- Tiingo

    // Unbereinigte Kurse; am 09.11. ein Split 4:1 und am 05.11. sowie 10.11. Dividenden
    private val tiingoKerzen = """[
        {"date":"2026-11-05T00:00:00.000Z","close":400.0,"high":405.0,"low":395.0,"open":398.0,"volume":1000,"adjClose":99.0,"divCash":0.8,"splitFactor":1.0},
        {"date":"2026-11-06T00:00:00.000Z","close":404.0,"high":406.0,"low":399.0,"open":400.0,"volume":1200,"adjClose":100.0,"divCash":0.0,"splitFactor":1.0},
        {"date":"2026-11-09T00:00:00.000Z","close":101.0,"high":102.0,"low":99.0,"open":100.0,"volume":4400,"adjClose":101.0,"divCash":0.0,"splitFactor":4.0},
        {"date":"2026-11-10T00:00:00.000Z","close":103.0,"high":104.0,"low":100.5,"open":101.0,"volume":4000,"adjClose":103.0,"divCash":0.25,"splitFactor":1.0},
        {"date":"2026-11-11T00:00:00.000Z","close":105.0,"high":106.0,"low":102.0,"open":103.5,"volume":3900,"adjClose":105.0,"divCash":0.0,"splitFactor":1.0}
    ]"""

    @Test
    fun tiingoAdresseKopfUndKuerzel() {
        val heute = mittag / tag
        assertEquals(
            "https://api.tiingo.com/tiingo/daily/AAPL/prices?startDate=2006-11-17&endDate=2026-11-12",
            TiingoKurse.kerzenAdresse("AAPL", heute - TiingoKurse.VERLAUF_TAGE, heute),
        )
        // Schluessel nur in der Kopfzeile, nie in der Adresse
        assertEquals(mapOf("Authorization" to "Token geheim"), TiingoKurse.kopf("geheim"))
        assertFalse(TiingoKurse.kerzenAdresse("AAPL", 0, 1).contains("geheim"))
        // Gattungen mit Bindestrich wie bei der App, nichts ausser US-Werten
        assertEquals("BRK-B", TiingoKurse.ticker("BRK-B"))
        for (s in listOf("SAP.DE", "^GSPC", "EURUSD=X", "BTC-USD", "MOEX:GAZP", "aapl", "", "ABCDEF")) assertNull(s, TiingoKurse.ticker(s))
    }

    @Test
    fun tiingoKerzenMitSplitUndDividende() {
        val r = TiingoKurse.lesen(tiingoKerzen)!!
        assertEquals(5, r.kerzen.size)
        // Kurse vor dem Split durch vier, Stueckzahl mal vier
        assertEquals(Candle(epoch(2026, 11, 5) * tag + 5 * 3_600_000L, 99.5, 101.25, 98.75, 100.0, 4000.0), r.kerzen[0])
        assertEquals(101.0, r.kerzen[1].close, 1e-9)
        assertEquals(101.0, r.kerzen[2].close, 1e-9)
        assertEquals(4400.0, r.kerzen[2].volume, 1e-9)
        assertEquals(105.0, r.kerzen[4].close, 1e-9)
        assertTrue(r.kerzen.zipWithNext().all { (a, b) -> a.t < b.t })
        // Dividenden auf heutige Stueckzahl gerechnet
        assertEquals(listOf(MarketApi.DividendEvent(epoch(2026, 11, 5) * tag, 0.2), MarketApi.DividendEvent(epoch(2026, 11, 10) * tag, 0.25)), r.dividenden)
        assertEquals(listOf(Splits.Split(epoch(2026, 11, 9) * tag, 4.0, 1.0)), r.splits)
        // Umgekehrter Split 1:10
        val rueck = TiingoKurse.lesen("""[{"date":"2026-11-09","close":50.0,"splitFactor":0.1},{"date":"2026-11-10","close":51.0}]""")!!
        assertEquals(Splits.Split(epoch(2026, 11, 9) * tag, 1.0, 10.0), rueck.splits.single())
        assertEquals(1.0, rueck.splits.single().verhaeltnis * 10.0, 1e-9)
        // Kein Fehlerobjekt als Reihe, keine Seite; bekannter Wert ohne Kerzen: leer
        assertNull(TiingoKurse.lesen("""{"detail":"Invalid token."}"""))
        assertNull(TiingoKurse.lesen("<html>"))
        assertTrue(TiingoKurse.lesen("[]")!!.kerzen.isEmpty())
    }

    @Test
    fun tiingoQuelleKursKerzenSplitsDividenden() = runBlocking {
        val netz = Netz { ok(tiingoKerzen) }
        val quelle = TiingoQuelle(netz.abruf, { " geheim " }, uhr = { mittag })
        val q = quelle.kurse(listOf("AAPL")).getValue("AAPL")
        assertEquals(105.0, q.price, 1e-9)
        assertEquals(103.0, q.prevClose, 1e-9)
        assertEquals(TiingoKurse.NAME, q.exchange)
        assertEquals("CLOSED", q.marketState)
        assertEquals("USD", q.nativeCurrency)
        assertEquals("Token geheim", netz.koepfe[0]["Authorization"])
        assertTrue(netz.adressen[0].startsWith("https://api.tiingo.com/tiingo/daily/AAPL/prices?startDate="))
        // Alles aus derselben gemerkten Antwort: ein einziger Abruf
        assertEquals(2, quelle.dividenden("AAPL").size)
        assertEquals(1, quelle.splits("AAPL").size)
        assertEquals(5, quelle.kerzen("AAPL", "1y", "1d").candles.size)
        assertEquals(1, quelle.kerzen("AAPL", "1y", "1mo").candles.size)
        assertEquals(5, quelle.kerzenZwischen("AAPL", mittag - 30 * tag, mittag, "1d").candles.size)
        assertEquals(1, quelle.splitsOderNull("AAPL")!!.size)
        assertEquals(1, netz.adressen.size)
        // Kein Intraday, nichts ausserhalb des Verlaufs, keine Werte anderer Boersen
        assertTrue(quelle.kerzen("AAPL", "1d", "2m").candles.isEmpty())
        assertTrue(quelle.kerzen("AAPL", "max", "1wk").candles.isEmpty())
        assertTrue(quelle.kurse(listOf("SAP.DE")).isEmpty())
        assertEquals(1, netz.adressen.size)
    }

    @Test
    fun tiingoOhneSchluesselStummUndMeldungen() = runBlocking {
        val netz = Netz { ok(tiingoKerzen) }
        var schluessel = ""
        var jetzt = mittag
        val quelle = TiingoQuelle(netz.abruf, { schluessel }, uhr = { jetzt })
        assertTrue(quelle.kurse(listOf("AAPL")).isEmpty())
        assertNull(quelle.splitsOderNull("AAPL"))
        assertEquals(0, netz.adressen.size)
        // 401: Schluessel abgelehnt, kein zweiter Versuch
        schluessel = "falsch"
        netz.antwort = { MarketApi.Antwort(401, """{"detail":"Invalid token."}""") }
        assertTrue(quelle.kurse(listOf("AAPL")).isEmpty())
        assertEquals(NewsRepository.SCHLUESSEL, quelle.meldung)
        quelle.kurse(listOf("MSFT"))
        assertEquals(1, netz.adressen.size)
        // 429: eine Minute Pause
        schluessel = "richtig"
        netz.antwort = { MarketApi.Antwort(429, null) }
        assertTrue(quelle.kurse(listOf("AAPL")).isEmpty())
        assertEquals(NewsRepository.LIMIT, quelle.meldung)
        quelle.kurse(listOf("MSFT"))
        assertEquals(2, netz.adressen.size)
        jetzt += 61_000L
        // 404: Wert unbekannt, gemerkt, kein weiterer Abruf fuer diesen Wert
        netz.antwort = { MarketApi.Antwort(404, """{"detail":"Not found."}""") }
        assertTrue(quelle.kurse(listOf("ZZZZ")).isEmpty())
        assertTrue(quelle.kurse(listOf("ZZZZ")).isEmpty())
        assertEquals(3, netz.adressen.size)
        assertNull(quelle.meldung)
        assertEquals(emptyList<Splits.Split>(), quelle.splitsOderNull("ZZZZ"))
        // Ohne Verbindung: fuenf Minuten Ruhe
        netz.antwort = { MarketApi.Antwort(0, null) }
        assertTrue(quelle.kurse(listOf("NVDA")).isEmpty())
        quelle.kurse(listOf("AMZN"))
        assertEquals(4, netz.adressen.size)
    }

    @Test
    fun tiingoKontingentFuenfzigJeStundeUndTausendAmTag() = runBlocking {
        assertEquals(
            listOf(Abrufkontingent.Grenze(50, 3_600_000L), Abrufkontingent.Grenze(1_000, 86_400_000L)),
            Abrufkontingent.REGELN.getValue(Abrufkontingent.TIINGO).grenzen,
        )
        val netz = Netz { ok(tiingoKerzen) }
        var jetzt = mittag
        val kontingent = Abrufkontingent(null) { jetzt }
        val quelle = TiingoQuelle(netz.abruf, { "geheim" }, { kontingent }, { jetzt })
        val werte = ('A'..'Z').flatMap { a -> listOf("${a}A", "${a}B", "${a}C") }.take(60)
        assertEquals(50, quelle.kurse(werte).size)
        assertEquals(50, netz.adressen.size)
        assertEquals(50L to 1_000L, kontingent.stand(Abrufkontingent.TIINGO))
        // Nach einer Stunde wieder frei fuer den Rest
        jetzt += 3_700_000L
        assertEquals(60, quelle.kurse(werte).size)
        assertEquals(60, netz.adressen.size)
    }

    // ---------------------------------------------------------------- Twelve Data

    private val zwoelfReihe = """{"meta":{"symbol":"AAPL","interval":"1day","currency":"USD","exchange":"NASDAQ","type":"Common Stock"},
        "values":[
        {"datetime":"2026-11-10","open":"100.0","high":"102.0","low":"99.0","close":"101.5","volume":"1000"},
        {"datetime":"2026-11-11","open":"101.5","high":"104.0","low":"101.0","close":"103.2","volume":"1100"},
        {"datetime":"2026-11-09","open":"98.0","high":"100.5","low":"97.5","close":"100.0","volume":"900"}],
        "status":"ok"}"""

    private val zwoelfQuote = """{"symbol":"AAPL","name":"Apple Inc","exchange":"NASDAQ","currency":"USD","datetime":"2026-11-12",
        "timestamp":1794506400,"open":"104.5","high":"106.0","low":"103.9","close":"105.20","volume":"5000","previous_close":"104.00",
        "change":"1.2","percent_change":"1.15","is_market_open":false,"fifty_two_week":{"low":"80.0","high":"120.0"}}"""

    private fun zwoelfFehler(code: Int) = """{"code":$code,"message":"fehler","status":"error"}"""

    @Test
    fun twelveAdressenKopfUndFehlerImStatus200() {
        assertEquals(
            "https://api.twelvedata.com/time_series?symbol=BRK.B&interval=1day&outputsize=5000&order=ASC&adjust=splits",
            TwelveDataKurse.reiheAdresse(TwelveDataKurse.ticker("BRK-B")!!),
        )
        assertEquals("https://api.twelvedata.com/quote?symbol=AAPL", TwelveDataKurse.quoteAdresse("AAPL"))
        assertEquals(mapOf("Authorization" to "apikey geheim"), TwelveDataKurse.kopf("geheim"))
        assertNull(TwelveDataKurse.ticker("SAP.DE"))
        assertNull(TwelveDataKurse.ticker("^GSPC"))
        // Fehler als Antwort mit Status 200
        assertEquals(401, TwelveDataKurse.fehlerCode(zwoelfFehler(401)))
        assertEquals(500, TwelveDataKurse.fehlerCode("""{"status":"error"}"""))
        assertNull(TwelveDataKurse.fehlerCode(zwoelfReihe))
        assertNull(TwelveDataKurse.fehlerCode("<html>"))
        assertNull(TwelveDataKurse.kerzenLesen(zwoelfFehler(404)))
        assertNull(TwelveDataKurse.quoteLesen("AAPL", zwoelfFehler(429), mittag))
        assertNull(TwelveDataKurse.kerzenLesen("<html>"))
    }

    @Test
    fun twelveKerzenUndKurs() {
        val k = TwelveDataKurse.kerzenLesen(zwoelfReihe)!!
        assertEquals(3, k.size)
        // Neueste zuerst in der Antwort, aelteste zuerst im Ergebnis
        assertEquals(Candle(epoch(2026, 11, 9) * tag + 5 * 3_600_000L, 98.0, 100.5, 97.5, 100.0, 900.0), k.first())
        assertEquals(103.2, k.last().close, 1e-9)
        val q = TwelveDataKurse.quoteLesen("AAPL", zwoelfQuote, mittag)!!
        assertEquals(105.2, q.price, 1e-9)
        assertEquals(104.0, q.prevClose, 1e-9)
        assertEquals(80.0, q.weekLow52, 1e-9)
        assertEquals(120.0, q.weekHigh52, 1e-9)
        assertEquals(TwelveDataKurse.NAME, q.exchange)
        assertEquals(1_794_506_400_000L, q.marketTime)
        val aus = TwelveDataKurse.kursAus("AAPL", k, mittag)!!
        assertEquals(103.2, aus.price, 1e-9)
        assertEquals(TwelveDataKurse.NAME, aus.exchange)
    }

    @Test
    fun twelveQuelleFehlerMeldungenKontingent() = runBlocking {
        val netz = Netz { ok(zwoelfReihe) }
        var jetzt = mittag
        var schluessel = "falsch"
        val quelle = TwelveDataQuelle(netz.abruf, { schluessel }, uhr = { jetzt })
        // 401 im Text einer Antwort mit Status 200: Schluessel abgelehnt, kein zweiter Versuch
        netz.antwort = { ok(zwoelfFehler(401)) }
        assertTrue(quelle.kerzen("AAPL", "1y", "1d").candles.isEmpty())
        assertEquals(NewsRepository.SCHLUESSEL, quelle.meldung)
        quelle.kerzen("MSFT", "1y", "1d")
        assertEquals(1, netz.adressen.size)
        // 429 im Text: Pause
        schluessel = "richtig"
        netz.antwort = { ok(zwoelfFehler(429)) }
        assertTrue(quelle.kerzen("AAPL", "1y", "1d").candles.isEmpty())
        assertEquals(NewsRepository.LIMIT, quelle.meldung)
        quelle.kerzen("MSFT", "1y", "1d")
        assertEquals(2, netz.adressen.size)
        jetzt += 61_000L
        // 404 im Text: Wert unbekannt, gemerkt
        netz.antwort = { ok(zwoelfFehler(404)) }
        assertTrue(quelle.kerzen("ZZZZ", "1y", "1d").candles.isEmpty())
        assertTrue(quelle.kerzen("ZZZZ", "1y", "1d").candles.isEmpty())
        assertEquals(3, netz.adressen.size)
        assertNull(quelle.meldung)
        // Kontingent acht je Minute und 800 am Tag
        assertEquals(
            listOf(Abrufkontingent.Grenze(8, 60_000L), Abrufkontingent.Grenze(800, 86_400_000L)),
            Abrufkontingent.REGELN.getValue(Abrufkontingent.TWELVEDATA).grenzen,
        )
        netz.antwort = { ok(zwoelfReihe) }
        val kontingent = Abrufkontingent(null) { jetzt }
        val q2 = TwelveDataQuelle(netz.abruf, { "geheim" }, { kontingent }, { jetzt })
        val vorher = netz.adressen.size
        for (w in listOf("AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ")) q2.kerzen(w, "1y", "1d")
        assertEquals(8, netz.adressen.size - vorher)
        assertEquals(8L to 800L, kontingent.stand(Abrufkontingent.TWELVEDATA))
        // Keine Dividenden und Splits
        assertTrue(q2.dividenden("AA").isEmpty())
        assertTrue(q2.splits("AA").isEmpty())
    }

    @Test
    fun twelveKursUeberQuoteSonstAusDenKerzen() = runBlocking {
        val netz = Netz { a -> if ("/quote?" in a) ok(zwoelfQuote) else ok(zwoelfReihe) }
        val quelle = TwelveDataQuelle(netz.abruf, { "geheim" }, uhr = { mittag })
        assertEquals(105.2, quelle.kurse(listOf("AAPL")).getValue("AAPL").price, 1e-9)
        assertEquals(1, netz.adressen.size)
        // Fuenf Minuten aus dem Speicher
        quelle.kurse(listOf("AAPL"))
        assertEquals(1, netz.adressen.size)
        // Quote kaputt: Schluss aus den Tageskerzen
        val netz2 = Netz { a -> if ("/quote?" in a) ok(zwoelfFehler(400)) else ok(zwoelfReihe) }
        val q2 = TwelveDataQuelle(netz2.abruf, { "geheim" }, uhr = { mittag })
        assertEquals(103.2, q2.kurse(listOf("AAPL")).getValue("AAPL").price, 1e-9)
        assertEquals(2, netz2.adressen.size)
        assertEquals("apikey geheim", netz2.koepfe[0]["Authorization"])
        assertFalse(netz2.adressen.any { it.contains("geheim") })
    }

    // ---------------------------------------------------------------- Kette

    private class Stumm(val name: String, val ereignisse: MutableList<String>, val liefert: Boolean = false) : KursQuelle {
        override val praefix = ""
        override val waehrung = ""
        override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
            ereignisse += name
            return if (liefert) kennungen.associateWith { Quote(it, name = name, price = 1.0) } else emptyMap()
        }
        override suspend fun kerzen(kennung: String, range: String, interval: String): Series = Series(emptyList())
        override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series = Series(emptyList())
        override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()
        override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()
        override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()
        override suspend fun wechselkurse(): Map<String, Double> = emptyMap()
    }

    @Test
    fun ketteMassiveTiingoTwelveDataEigeneDatei() = runBlocking {
        val ereignisse = ArrayList<String>()
        // Jeder Anbieter kennt den Wert nicht (404): die Anfrage geht der Reihe nach durch bis zur eigenen Datei
        fun netz(host: String, antwort: MarketApi.Antwort): suspend (String, Map<String, String>) -> MarketApi.Antwort = { a, _ ->
            ereignisse += host; assertTrue(a.startsWith("https://$host/")); antwort
        }
        val kette = KursQuellenKette(listOf(
            Stumm("haupt", ereignisse),
            MassiveQuelle(netz("api.massive.com", MarketApi.Antwort(404, null)), { "m" }, uhr = { mittag }),
            TiingoQuelle(netz("api.tiingo.com", MarketApi.Antwort(404, null)), { "t" }, uhr = { mittag }),
            TwelveDataQuelle(netz("api.twelvedata.com", ok(zwoelfFehler(404))), { "z" }, uhr = { mittag }),
            Stumm("eigene", ereignisse, liefert = true),
        ))
        // Twelve Data fragt zuerst den Kurs (quote), fuer ein unbekanntes Papier danach die Tageskerzen
        val q = kette.kurse(listOf("ZZZZ")).getValue("ZZZZ")
        assertEquals("eigene", q.name)
        assertEquals(4, kette.zuletztAntwortete)
        assertEquals(
            listOf("haupt", "api.massive.com", "api.tiingo.com", "api.twelvedata.com", "api.twelvedata.com", "eigene"),
            ereignisse,
        )
        // Liefert Tiingo, kommen Twelve Data und die eigene Datei nicht mehr an die Reihe
        ereignisse.clear()
        val kette2 = KursQuellenKette(listOf(
            Stumm("haupt", ereignisse),
            MassiveQuelle(netz("api.massive.com", MarketApi.Antwort(404, null)), { "m" }, uhr = { mittag }),
            TiingoQuelle(netz("api.tiingo.com", ok(tiingoKerzen)), { "t" }, uhr = { mittag }),
            TwelveDataQuelle(netz("api.twelvedata.com", ok(zwoelfReihe)), { "z" }, uhr = { mittag }),
            Stumm("eigene", ereignisse, liefert = true),
        ))
        val q2 = kette2.kurse(listOf("AAPL")).getValue("AAPL")
        assertEquals(TiingoKurse.NAME, q2.exchange)
        assertEquals(listOf("haupt", "api.massive.com", "api.tiingo.com"), ereignisse)
    }

    @Test
    fun texteBeiderSprachenUndHerkunft() {
        assertEquals(KursRueckfallTexte.schluessel(true), KursRueckfallTexte.schluessel(false))
        for (deutsch in listOf(true, false)) for (k in listOf("name.tiingo", "zweck.tiingo", "name.twelve", "zweck.twelve", "stand.tag")) {
            val t = KursRueckfallTexte.text(deutsch, k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank() && !t.contains('–') && !t.contains('—') && !t.contains('{'))
        }
        assertEquals("Abrufe in den letzten 24 Stunden: 3 von 50", KursRueckfallTexte.text(true, "stand.tag", listOf("3", "50")))
        val host = de.troetenrepublik.app.engine.Quellenbuendel.ARTEN
        assertEquals("kursrueckfall", host.single { "tiingo.com" in it.hosts }.id)
        assertEquals("kursrueckfall", host.single { "twelvedata.com" in it.hosts }.id)
    }

    // ---------------------------------------------------------------- FRED ohne Zwischenspeicher

    @Test
    fun fredAdressenUndZitierung() {
        assertTrue(FredRegeln.istFred("https://fred.stlouisfed.org/graph/fredgraph.csv?id=DFF&cosd=2015-01-01"))
        assertTrue(FredRegeln.istFred("https://alfred.stlouisfed.org/graph/alfredgraph.csv?id=GDPNOW"))
        assertFalse(FredRegeln.istFred("https://www.federalreserve.gov/releases/h15/"))
        assertFalse(FredRegeln.istFred("https://example.com/?u=https://fred.stlouisfed.org/"))
        assertEquals("DFF", FredRegeln.reiheAus("https://fred.stlouisfed.org/graph/fredgraph.csv?id=DFF&cosd=2015-01-01"))
        assertEquals("GDPNOW", FredRegeln.reiheAus("https://fred.stlouisfed.org/series/GDPNOW"))
        assertNull(FredRegeln.reiheAus("https://fred.stlouisfed.org/"))
        assertEquals(
            "Federal Reserve Bank of Atlanta, GDPNOW, retrieved from FRED, Federal Reserve Bank of St. Louis",
            FredRegeln.zitat("GDPNOW", deutsch = false),
        )
        assertEquals(
            "ICE Data Indices, LLC, BAMLH0A0HYM2, abgerufen über FRED, Federal Reserve Bank of St. Louis",
            FredRegeln.zitat("BAMLH0A0HYM2", deutsch = true),
        )
        // Laender-Renditen gehoeren der OECD; unbekannte Reihen nennen nur die Kennung
        assertTrue(FredRegeln.zitat("IRLTLT01CHM156N", false).startsWith("Organisation for Economic Co-operation and Development, IRLTLT01CHM156N, retrieved from FRED"))
        assertEquals("EONIARATE, retrieved from FRED, Federal Reserve Bank of St. Louis", FredRegeln.zitat("EONIARATE", false))
        assertEquals("https://fred.stlouisfed.org/series/STLFSI4", FredRegeln.serienSeite("STLFSI4"))
    }

    @Test
    fun fredAbrufBleibtNichtHaengen() = runBlocking {
        val tor = CompletableDeferred<Unit>()
        var aufrufe = 0
        // Gleichzeitige Anfragen teilen sich einen Abruf
        val a = async { FredAbruf.geteilt("reihe") { aufrufe++; tor.await(); "A" } }
        val b = async { FredAbruf.geteilt("reihe") { aufrufe++; "B" } }
        yield()
        assertEquals(1, FredAbruf.laufende())
        tor.complete(Unit)
        assertEquals("A", a.await())
        assertEquals("A", b.await())
        assertEquals(1, aufrufe)
        // Danach bleibt nichts: der naechste Aufruf ruft neu ab
        assertEquals(0, FredAbruf.laufende())
        assertEquals("C", FredAbruf.geteilt("reihe") { aufrufe++; "C" })
        assertEquals(2, aufrufe)
        // Ein Fehler bleibt ebenso nicht haengen
        runCatching { FredAbruf.geteilt<String>("reihe") { throw IllegalStateException("x") } }
        assertEquals(0, FredAbruf.laufende())
        assertEquals("D", FredAbruf.geteilt("reihe") { "D" })
        // Verschiedene Reihen laufen getrennt
        assertEquals("E" to "F", FredAbruf.geteilt("x") { "E" } to FredAbruf.geteilt("y") { "F" })
    }

    // ---------------------------------------------------------------- GDPNow von der Atlanta Fed

    private fun excel(t: LocalDate) = (t.toEpochDay() + 25_569L).toString()

    private fun xlsx(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun datei(name: String, inhalt: String) { z.putNextEntry(ZipEntry(name)); z.write(inhalt.toByteArray()); z.closeEntry() }
            datei("xl/workbook.xml", """<workbook xmlns:r="http://x"><sheets><sheet name="Other" sheetId="1" r:id="rId9"/><sheet name="TrackRecord" sheetId="70" r:id="rId2"/><sheet name="CurrentQtrEvolution" sheetId="87" r:id="rId1"/></sheets></workbook>""")
            datei("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Type="t" Target="worksheets/sheet44.xml"/><Relationship Id="rId2" Type="t" Target="worksheets/sheet34.xml"/><Relationship Id="rId9" Type="t" Target="worksheets/sheet1.xml"/></Relationships>""")
            datei("xl/sharedStrings.xml", """<sst><si><t>Date</t></si><si><t>Major Releases</t></si><si><t>GDP*</t></si><si><t>Initial GDPNow 30:Q4 forecast</t></si><si><t>Employment situation</t></si><si><t>Quarter being forecasted</t></si><si><t>Model Forecast &amp; more</t></si></sst>""")
            datei("xl/worksheets/sheet1.xml", """<worksheet><sheetData><row r="1"><c r="A1"><v>999</v></c></row></sheetData></worksheet>""")
            datei(
                "xl/worksheets/sheet44.xml",
                """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                <row r="2"><c r="A2"><v>${excel(LocalDate.of(2030, 10, 29))}</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>1.5</v></c></row>
                <row r="3"><c r="A3"><v>${excel(LocalDate.of(2030, 11, 1))}</v></c><c r="B3" t="s"><v>4</v></c><c r="C3"><v>1.2</v></c></row>
                <row r="4"><c r="A4"><v>${excel(LocalDate.of(2030, 11, 5))}</v></c><c r="B4" t="s"><v>4</v></c><c r="C4"><v>0.8</v></c></row>
                <row r="5"><c r="A5"><v>${excel(LocalDate.of(2030, 11, 8))}</v></c><c r="B5" t="s"><v>4</v></c><c r="C5"><v>0.8</v></c></row>
                <row r="6"/>
                </sheetData></worksheet>""",
            )
            datei(
                "xl/worksheets/sheet34.xml",
                """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>5</v></c><c r="B1" t="s"><v>6</v></c></row>
                <row r="2"><c r="A2"><v>${excel(LocalDate.of(2030, 12, 31))}</v></c><c r="B2"><v>9.0</v></c></row>
                <row r="3"><c r="A3"><v>${excel(LocalDate.of(2030, 9, 30))}</v></c><c r="B3"><v>2.2</v></c></row>
                <row r="4"><c r="A4"><v>${excel(LocalDate.of(2030, 6, 30))}</v></c><c r="B4"><v>1.9</v></c></row>
                </sheetData></worksheet>""",
            )
        }
        return out.toByteArray()
    }

    @Test
    fun gdpNowAusDerTabelleDerAtlantaFed() {
        val s = GdpNowAtlanta.lesen(xlsx())!!
        assertEquals(GdpNow.Quelle.ATLANTA, s.quelle)
        // Das Quartal steht im Anlass der ersten Zeile
        assertEquals(epoch(2030, 10, 1), s.quartal)
        assertEquals(4, s.nummer)
        assertEquals(2030, s.jahr)
        assertEquals(0.8, s.wert, 1e-9)
        // Der Wert gilt seit dem ersten Tag der letzten gleichen Staende
        assertEquals(epoch(2030, 11, 5), s.stand)
        assertEquals(listOf(epoch(2030, 10, 29), epoch(2030, 11, 1), epoch(2030, 11, 5), epoch(2030, 11, 8)), s.verlauf.map { it.first })
        assertEquals(1.5, s.verlauf.first().second, 1e-9)
        // Vorquartal: juengste Schaetzung vor dem Beginn des Quartals, nicht die danach
        assertEquals(2.2, s.vorquartal!!, 1e-9)
        // FRED-Staende tragen die Herkunft FRED
        assertEquals(GdpNow.Quelle.FRED, GdpNow.Stand(1L, 1.0, 1L, emptyList(), null).quelle)
    }

    @Test
    fun gdpNowUnlesbaresErgibtNichts() {
        assertNull(GdpNowAtlanta.lesen(ByteArray(0)))
        assertNull(GdpNowAtlanta.lesen("<html>".toByteArray()))
        val ohneBlatt = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z ->
                z.putNextEntry(ZipEntry("xl/workbook.xml")); z.write("""<workbook xmlns:r="http://x"><sheets><sheet name="Other" sheetId="1" r:id="rId9"/></sheets></workbook>""".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels")); z.write("""<Relationships><Relationship Id="rId9" Target="worksheets/sheet1.xml"/></Relationships>""".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertNull(GdpNowAtlanta.lesen(ohneBlatt))
        assertNotNull(GdpNowAtlanta.lesen(xlsx()))
        assertTrue(GdpNowAtlanta.DATEI.startsWith("https://www.atlantafed.org/"))
        assertFalse(GdpNowAtlanta.DATEI.contains("token", ignoreCase = true))
    }
}
