package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits
import de.troetenrepublik.app.i18n.KursRueckfallTexte
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Zweite Kursquelle mit eigenem Schluessel. Die Dateien in src/test/resources/massive sind nach der Dokumentation des
// Anbieters aufgebaut (Feldnamen und Form), die Werte sind gesetzt; gegen den echten Dienst lief nichts.
class MassiveKurseTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("massive/$name.json")!!.readText()

    private val tag = 86_400_000L
    // 12.11.2026, 12 Uhr UTC
    private val mittag = 1_794_484_800_000L

    @Test
    fun kuerzelNurFuerUsWerte() {
        assertEquals("AAPL", MassiveKurse.ticker("AAPL"))
        assertEquals("BRK.B", MassiveKurse.ticker("BRK-B"))
        assertEquals("F", MassiveKurse.ticker("F"))
        for (s in listOf("SAP.DE", "BT-A.L", "^GSPC", "EURUSD=X", "BTC-USD", "GC=F", "MOEX:GAZP", "7203.T", "aapl", "", "ABCDEF")) {
            assertNull(s, MassiveKurse.ticker(s))
        }
    }

    @Test
    fun adressenOhneSchluessel() {
        val heute = mittag / tag
        assertEquals(
            "https://api.massive.com/v2/aggs/ticker/AAPL/range/1/day/2024-11-13/2026-11-12?adjusted=true&sort=asc&limit=5000",
            MassiveKurse.kerzenAdresse("AAPL", heute - MassiveKurse.VERLAUF_TAGE, heute),
        )
        assertEquals(
            "https://api.massive.com/stocks/v1/dividends?ticker=BRK.B&ex_dividend_date.gte=2024-11-13&limit=1000&sort=ex_dividend_date.asc",
            MassiveKurse.dividendenAdresse("BRK.B", heute - MassiveKurse.VERLAUF_TAGE),
        )
        assertEquals(mapOf("Authorization" to "Bearer geheim"), MassiveKurse.kopf("geheim"))
    }

    @Test
    fun kerzenLeser() {
        val k = MassiveKurse.kerzenLesen(res("kerzen"))!!
        assertEquals(5, k.size)
        assertEquals(Candle(1_793_854_800_000L, 224.0, 226.5, 223.1, 225.9, 41_000_000.0), k.first())
        assertEquals(231.6, k.last().close, 1e-9)
        assertTrue(k.zipWithNext().all { (a, b) -> a.t < b.t })
        // Bekannter Wert ohne Kerzen: leer. Fehlerantwort und alles andere: keine Antwort.
        assertEquals(emptyList<Candle>(), MassiveKurse.kerzenLesen(res("kerzen_leer")))
        assertNull(MassiveKurse.kerzenLesen(res("fehler")))
        assertNull(MassiveKurse.kerzenLesen("<html>"))
        assertNull(MassiveKurse.kerzenLesen("""{"status":"OK","results":{}}"""))
    }

    @Test
    fun kursAusDenKerzen() {
        val q = MassiveKurse.kursAus("AAPL", MassiveKurse.kerzenLesen(res("kerzen"))!!, mittag)!!
        assertEquals(231.6, q.price, 1e-9)
        assertEquals(233.1, q.prevClose, 1e-9)
        assertEquals(234.2, q.dayHigh, 1e-9)
        assertEquals(230.4, q.dayLow, 1e-9)
        assertEquals(234.2, q.weekHigh52, 1e-9)
        assertEquals(223.1, q.weekLow52, 1e-9)
        assertEquals("USD", q.nativeCurrency)
        // Als Schlusskurs gekennzeichnet: Markt geschlossen, Kurszeit am Handelstag, Quelle im Feld exchange
        assertEquals("CLOSED", q.marketState)
        assertEquals(MassiveKurse.NAME, q.exchange)
        assertEquals(1_794_373_200_000L + 16 * 3_600_000L, q.marketTime)
        assertEquals(mittag, q.updatedAt)
        assertNull(MassiveKurse.kursAus("AAPL", emptyList(), mittag))
    }

    @Test
    fun dividendenLeser() {
        val d = MassiveKurse.dividendenLesen(res("dividenden"))!!
        // Fremde Waehrung und unlesbares Datum fallen heraus; der Betrag ist auf heutige Stueckzahl gerechnet
        assertEquals(2, d.size)
        assertEquals(MarketApi.DividendEvent(20_311L * tag, 0.26), d[0])
        assertEquals(0.27, d[1].amount, 1e-9)
        assertNull(MassiveKurse.dividendenLesen(res("fehler")))
        assertEquals(20_311L, MassiveKurse.tagAus("2025-08-11"))
        assertNull(MassiveKurse.tagAus("2025-13-40"))
        assertNull(MassiveKurse.tagAus("11.08.2025"))
    }

    @Test
    fun wochenUndMonate() {
        val k = MassiveKurse.kerzenLesen(res("kerzen"))!!
        val w = MassiveKurse.verdichten(k, "1wk")
        // Donnerstag und Freitag, dann Montag bis Mittwoch
        assertEquals(2, w.size)
        assertEquals(Candle(1_793_854_800_000L, 224.0, 229.4, 223.1, 228.8, 80_500_000.0), w[0])
        assertEquals(229.0, w[1].open, 1e-9)
        assertEquals(234.2, w[1].high, 1e-9)
        assertEquals(227.5, w[1].low, 1e-9)
        assertEquals(231.6, w[1].close, 1e-9)
        assertEquals(1, MassiveKurse.verdichten(k, "1mo").size)
        assertEquals(k, MassiveKurse.verdichten(k, "1d"))
    }

    // Hauptquelle der Kette im Test: liefert oder bleibt leer
    private class Haupt(var liefert: Boolean) : KursQuelle {
        var aufrufe = 0
        override val praefix = ""
        override val waehrung = ""
        override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
            aufrufe++
            return if (liefert) kennungen.associateWith { Quote(it, name = "haupt", price = 1.0) } else emptyMap()
        }
        override suspend fun kerzen(kennung: String, range: String, interval: String): Series {
            aufrufe++
            return if (liefert) Series(listOf(Candle(1L, 1.0, 1.0, 1.0, 1.0))) else Series(emptyList())
        }
        override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series = kerzen(kennung, "", interval)
        override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()
        override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()
        override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()
        override suspend fun wechselkurse(): Map<String, Double> = emptyMap()
    }

    // Anbieter im Test: merkt sich Adressen und Kopfzeilen, antwortet mit festem Status
    private inner class Netz(var code: Int = 200, var text: String? = res("kerzen")) {
        val adressen = ArrayList<String>()
        val koepfe = ArrayList<Map<String, String>>()
        val abruf: suspend (String, Map<String, String>) -> MarketApi.Antwort = { a, k -> adressen += a; koepfe += k; MarketApi.Antwort(code, text) }
    }

    @Test
    fun ketteHauptquelleZuerstDannRueckfall() = runBlocking {
        val netz = Netz()
        val haupt = Haupt(true)
        val kette = KursQuellenKette(listOf(haupt, MassiveQuelle(netz.abruf, { "geheim" }, uhr = { mittag })))
        // Die Hauptquelle liefert: kein Abruf beim Rueckfall
        assertEquals("haupt", kette.kurse(listOf("AAPL")).getValue("AAPL").name)
        assertTrue(kette.kerzen("AAPL", "1y", "1d").candles.isNotEmpty())
        assertEquals(0, netz.adressen.size)
        // Die Hauptquelle bleibt leer: der Rueckfall liefert den Schlusskurs
        haupt.liefert = false
        val q = kette.kurse(listOf("AAPL")).getValue("AAPL")
        assertEquals(231.6, q.price, 1e-9)
        assertEquals(MassiveKurse.NAME, q.exchange)
        assertEquals(1, kette.zuletztAntwortete)
        // Der Schluessel steht nur in der Kopfzeile
        assertEquals(1, netz.adressen.size)
        assertTrue(netz.adressen[0].startsWith("https://api.massive.com/v2/aggs/ticker/AAPL/range/1/day/"))
        assertFalse(netz.adressen[0].contains("geheim"))
        assertEquals("Bearer geheim", netz.koepfe[0]["Authorization"])
        // Kerzen kommen aus derselben gemerkten Antwort
        assertEquals(5, kette.kerzen("AAPL", "1y", "1d").candles.size)
        assertEquals(2, kette.kerzen("AAPL", "6mo", "1wk").candles.size)
        assertEquals(5, kette.kerzenZwischen("AAPL", mittag - 30 * tag, mittag, "1d").candles.size)
        assertEquals(1, netz.adressen.size)
        // Kein Intraday, nichts ueber zwei Jahre, kein Fenster vor dem Verlauf des Tarifs
        assertTrue(kette.kerzen("AAPL", "1d", "2m").candles.isEmpty())
        assertTrue(kette.kerzen("AAPL", "1mo", "30m").candles.isEmpty())
        assertTrue(kette.kerzen("AAPL", "10y", "1wk").candles.isEmpty())
        assertTrue(kette.kerzenZwischen("AAPL", 0L, mittag, "1d").candles.isEmpty())
        // Werte anderer Boersen: die Quelle greift nicht und fragt nicht
        assertTrue(kette.kurse(listOf("SAP.DE")).isEmpty())
        assertTrue(kette.kerzen("^GSPC", "1y", "1d").candles.isEmpty())
        assertEquals(1, netz.adressen.size)
    }

    @Test
    fun ohneSchluesselStumm() = runBlocking {
        val netz = Netz()
        var schluessel = ""
        val quelle = MassiveQuelle(netz.abruf, { schluessel }, uhr = { mittag })
        val kette = KursQuellenKette(listOf(Haupt(false), quelle))
        assertTrue(kette.kurse(listOf("AAPL")).isEmpty())
        assertTrue(kette.kerzen("AAPL", "1y", "1d").candles.isEmpty())
        assertTrue(quelle.dividenden("AAPL").isEmpty())
        assertEquals(0, netz.adressen.size)
        // Mit Schluessel liefert sie, nach dem Entfernen ist auch das Gemerkte weg
        schluessel = " geheim "
        assertEquals(1, kette.kurse(listOf("AAPL")).size)
        assertEquals("Bearer geheim", netz.koepfe[0]["Authorization"])
        schluessel = ""
        assertTrue(kette.kurse(listOf("AAPL")).isEmpty())
        assertEquals(1, netz.adressen.size)
    }

    @Test
    fun kontingentVierJeMinuteUndZwischenspeicher() = runBlocking {
        val netz = Netz()
        var jetzt = mittag
        val kontingent = Abrufkontingent(null) { jetzt }
        val quelle = MassiveQuelle(netz.abruf, { "geheim" }, { kontingent }, { jetzt })
        val werte = listOf("AAPL", "MSFT", "NVDA", "AMZN", "META", "TSLA")
        // Sechs Werte, vier Abrufe; der Rest wartet auf die naechste Minute
        assertEquals(listOf("AAPL", "MSFT", "NVDA", "AMZN"), quelle.kurse(werte).keys.sortedBy { werte.indexOf(it) })
        assertEquals(4, netz.adressen.size)
        assertEquals(4L to 4L, kontingent.stand(Abrufkontingent.MASSIVE))
        jetzt += 61_000L
        assertEquals(6, quelle.kurse(werte).size)
        assertEquals(6, netz.adressen.size)
        // Sechs Stunden lang kein neuer Abruf, danach wieder
        jetzt += 5 * 3_600_000L
        assertEquals(6, quelle.kurse(werte).size)
        assertEquals(6, netz.adressen.size)
        jetzt += 3_600_000L
        quelle.kurse(listOf("AAPL"))
        assertEquals(7, netz.adressen.size)
    }

    @Test
    fun meldungenUndPausen() = runBlocking {
        val netz = Netz(code = 401, text = """{"status":"ERROR","error":"API Key was not provided"}""")
        var jetzt = mittag
        var schluessel = "falsch"
        val quelle = MassiveQuelle(netz.abruf, { schluessel }, uhr = { jetzt })
        // Abgelehnter Schluessel: Meldung, kein zweiter Versuch mit demselben Schluessel
        assertTrue(quelle.kurse(listOf("AAPL")).isEmpty())
        assertEquals(NewsRepository.SCHLUESSEL, quelle.meldung)
        quelle.kurse(listOf("AAPL", "MSFT"))
        assertEquals(1, netz.adressen.size)
        // Limit des Anbieters: eine Minute Pause
        schluessel = "richtig"
        netz.code = 429
        assertTrue(quelle.kurse(listOf("AAPL")).isEmpty())
        assertEquals(NewsRepository.LIMIT, quelle.meldung)
        quelle.kurse(listOf("MSFT"))
        assertEquals(2, netz.adressen.size)
        jetzt += 61_000L
        netz.code = 200
        netz.text = res("kerzen")
        assertEquals(1, quelle.kurse(listOf("AAPL")).size)
        assertNull(quelle.meldung)
        // Nicht im Tarif oder unbekannt: gemerkt wie ein leeres Ergebnis, kein weiterer Abruf fuer diesen Wert
        netz.code = 403
        netz.text = res("fehler")
        assertTrue(quelle.kurse(listOf("ZZZZ")).isEmpty())
        assertTrue(quelle.kurse(listOf("ZZZZ")).isEmpty())
        assertEquals(4, netz.adressen.size)
        assertNull(quelle.meldung)
        // Ohne Verbindung: fuenf Minuten Ruhe
        netz.code = 0
        netz.text = null
        assertTrue(quelle.kurse(listOf("NVDA")).isEmpty())
        quelle.kurse(listOf("AMZN"))
        assertEquals(5, netz.adressen.size)
    }

    @Test
    fun dividendenUeberDieQuelle() = runBlocking {
        val netz = Netz(text = res("dividenden"))
        val quelle = MassiveQuelle(netz.abruf, { "geheim" }, uhr = { mittag })
        assertEquals(2, quelle.dividenden("AAPL").size)
        assertEquals(2, quelle.dividenden("AAPL").size)
        assertEquals(1, netz.adressen.size)
        assertTrue(netz.adressen[0].startsWith("https://api.massive.com/stocks/v1/dividends?ticker=AAPL&ex_dividend_date.gte=2024-11-13"))
        assertTrue(quelle.dividenden("SAP.DE").isEmpty())
        assertTrue(quelle.splits("AAPL").isEmpty())
        assertEquals(1, netz.adressen.size)
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(KursRueckfallTexte.schluessel(true), KursRueckfallTexte.schluessel(false))
        for (deutsch in listOf(true, false)) for (k in KursRueckfallTexte.schluessel(deutsch)) {
            val t = KursRueckfallTexte.text(deutsch, k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank() && !t.contains('–') && !t.contains('—') && !t.contains('{'))
        }
        assertEquals("Schlusskurs vom 11.11.2026", KursRueckfallTexte.text(true, "schluss", listOf("11.11.2026")))
    }

    @Test
    fun kontingentUndQuellenliste() {
        assertEquals(listOf(Abrufkontingent.Grenze(4, 60_000L)), Abrufkontingent.REGELN.getValue(Abrufkontingent.MASSIVE).grenzen)
        assertEquals("kursrueckfall", de.troetenrepublik.app.engine.Quellenbuendel.ARTEN.single { "massive.com" in it.hosts }.id)
    }
}
