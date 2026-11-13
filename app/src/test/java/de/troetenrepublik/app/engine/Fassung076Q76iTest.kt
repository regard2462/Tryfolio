package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.market.SerieCodec
import de.troetenrepublik.app.market.SichereStandDatei
import de.troetenrepublik.app.market.StandDatei
import de.troetenrepublik.app.market.StandRegel
import de.troetenrepublik.app.market.StandSpeicher
import de.troetenrepublik.app.ui.ChartInhalt
import de.troetenrepublik.app.ui.chartInhalt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 0.76 (q76i): gespeicherter Stand, der sofort erscheint und im Hintergrund durch frische Daten ersetzt wird.
class Fassung076Q76iTest {

    private class Speicherlos(var text: String? = null, var schreibfehler: Boolean = false, var lesefehler: Boolean = false) : StandDatei {
        var geschrieben = 0
        override fun lesen(): String? { if (lesefehler) error("unlesbar"); return text }
        override fun schreiben(text: String) { if (schreibfehler) error("voll"); this.text = text; geschrieben++ }
    }

    private fun serie(n: Int, t0: Long = 1_000L) = Series(List(n) { Candle(t0 + it * 60_000L, 10.0 + it, 11.0 + it, 9.0 + it, 10.5 + it, 100.0 + it) }, t0, t0 + n * 60_000L)

    // ---- Speicher: Schreiben, Lesen, Verdraengen, beschaedigte Datei

    @Test fun schreibenUndNachNeustartLesen() {
        val d = Speicherlos()
        val a = StandSpeicher(listOf(d))
        a.schreiben("AAPL|Y1", "{\"x\":1}", 5_000L)
        assertEquals(StandSpeicher.Eintrag(5_000L, "{\"x\":1}"), a.lesen("AAPL|Y1"))
        assertEquals(0, d.geschrieben)           // geschrieben wird erst bei sichern()
        assertTrue(a.offen())
        assertEquals(1, a.sichern())
        assertFalse(a.offen())
        val b = StandSpeicher(listOf(d))          // neuer Start
        assertEquals(StandSpeicher.Eintrag(5_000L, "{\"x\":1}"), b.lesen("AAPL|Y1"))
        assertNull(b.lesen("MSFT|Y1"))
    }

    @Test fun aelteste_werden_verdraengt_der_juengste_bleibt() {
        val a = StandSpeicher(listOf(Speicherlos()), maxEintraege = 3)
        for (i in 1..5) a.schreiben("k$i", "w$i", i * 1000L)
        assertEquals(3, a.eintraege())
        assertNull(a.lesen("k1")); assertNull(a.lesen("k2"))
        assertNotNull(a.lesen("k5"))
    }

    @Test fun groessenbegrenzungVerdraengtAelteste() {
        val a = StandSpeicher(listOf(Speicherlos()), maxZeichen = 400, maxEintraege = 100)
        for (i in 1..10) a.schreiben("k$i", "x".repeat(100), i * 1000L)
        assertTrue(a.zeichen() <= 400)
        assertNotNull(a.lesen("k10"))
        assertNull(a.lesen("k1"))
        // Ein einzelner zu grosser Eintrag bleibt als juengster stehen
        val b = StandSpeicher(listOf(Speicherlos()), maxZeichen = 50)
        b.schreiben("gross", "y".repeat(500), 1L)
        assertNotNull(b.lesen("gross"))
    }

    @Test fun beschaedigteDateiFaelltNurZeilenweiseWeg() {
        val d = Speicherlos("müll ohne tabs\nA|Y1\t123\t{\"ok\":1}\nB|Y1\tabc\t{}\n\t5\tx\nC|Y1\t7\t\n\u0000\u0000")
        val a = StandSpeicher(listOf(d))
        assertEquals(StandSpeicher.Eintrag(123L, "{\"ok\":1}"), a.lesen("A|Y1"))
        assertNull(a.lesen("B|Y1")); assertNull(a.lesen("C|Y1"))
        assertEquals(1, a.eintraege())
        // Unlesbare Datei gilt als leer und blockiert das Schreiben nicht
        val kaputt = StandSpeicher(listOf(Speicherlos(lesefehler = true)))
        assertNull(kaputt.lesen("A"))
        kaputt.schreiben("A", "1", 1L)
        assertNotNull(kaputt.lesen("A"))
    }

    @Test fun schreibfehlerLaesstDateiOffen() {
        val d = Speicherlos(schreibfehler = true)
        val a = StandSpeicher(listOf(d))
        a.schreiben("k", "w", 1L)
        assertEquals(0, a.sichern())
        assertTrue(a.offen())
        d.schreibfehler = false
        assertEquals(1, a.sichern())
        assertFalse(a.offen())
    }

    @Test fun zeilenumbruecheInWertenBrechenDieDateiNicht() {
        val d = Speicherlos()
        val a = StandSpeicher(listOf(d))
        a.schreiben("a\tb", "zeile1\nzeile2\tx", 9L)
        a.sichern()
        val b = StandSpeicher(listOf(d))
        assertEquals(1, b.eintraege())
        assertEquals(9L, b.lesen("a\tb")?.t)
    }

    @Test fun verteiltAufMehrereDateien() {
        val ds = List(4) { Speicherlos() }
        val a = StandSpeicher(ds)
        for (i in 1..20) a.schreiben("SYM$i|D1", "w$i", i.toLong())
        a.sichern()
        assertTrue(ds.count { it.geschrieben > 0 } > 1)
        val b = StandSpeicher(ds)
        assertEquals(20, b.eintraege())
        assertEquals(20, b.alle().size)
    }

    @Test fun serieRundreiseUndBeschaedigung() {
        val s = serie(30)
        assertEquals(s, SerieCodec.lesen(SerieCodec.kodieren(s)))
        assertNull(SerieCodec.lesen("{kaputt"))
        assertNull(SerieCodec.lesen("""{"s":0,"e":0,"t":[1,2],"o":[1],"h":[1],"l":[1],"c":[1],"v":[1]}"""))
    }

    // ---- Regel: gespeichert sofort, frisch darueber

    @Test fun gespeichertSofortDannFrisch() = runBlocking {
        val folge = mutableListOf<StandRegel.Anzeige<String>>()
        StandRegel.laden(StandRegel.Gespeichert("alt", 1_000L, frisch = false), { 9_000L }, { "neu" }) { folge += it }
        assertEquals(2, folge.size)
        assertEquals("alt", folge[0].wert); assertTrue(folge[0].alt); assertTrue(folge[0].laedt); assertEquals(1_000L, folge[0].abgerufen)
        assertEquals("neu", folge[1].wert); assertFalse(folge[1].alt); assertFalse(folge[1].laedt); assertEquals(9_000L, folge[1].abgerufen)
    }

    @Test fun stand_innerhalb_der_frist_gilt_nicht_als_alt_wird_aber_trotzdem_abgerufen() = runBlocking {
        var abgerufen = 0
        val folge = mutableListOf<StandRegel.Anzeige<String>>()
        StandRegel.laden(StandRegel.Gespeichert("x", 1L, frisch = true), { 2L }, { abgerufen++; "y" }) { folge += it }
        assertEquals(1, abgerufen)
        assertFalse(folge[0].alt)
    }

    @Test fun fehlerfallBehaeltStandMitZeitangabe() = runBlocking {
        val folge = mutableListOf<StandRegel.Anzeige<String>>()
        StandRegel.laden(StandRegel.Gespeichert("alt", 1_000L, frisch = false), { 9_000L }, { null }) { folge += it }
        val ende = folge.last()
        assertEquals("alt", ende.wert); assertTrue(ende.alt); assertFalse(ende.laedt); assertEquals(1_000L, ende.abgerufen)
        // Eine Ausnahme im Abruf wirkt wie keine Antwort
        val folge2 = mutableListOf<StandRegel.Anzeige<String>>()
        StandRegel.laden(StandRegel.Gespeichert("alt", 1_000L, frisch = false), { 9_000L }, { error("Netz") }) { folge2 += it }
        assertTrue(folge2.last().alt)
        // Ohne Stand und ohne Antwort bleibt nichts, nicht laufend
        val folge3 = mutableListOf<StandRegel.Anzeige<String>>()
        StandRegel.laden<String>(null, { 9_000L }, { null }) { folge3 += it }
        assertNull(folge3.last().wert); assertFalse(folge3.last().laedt)
    }

    @Test fun abbruchWirdNichtVerschluckt() {
        var gefangen = false
        try {
            runBlocking { StandRegel.laden<String>(null, { 0L }, { throw CancellationException("weg") }) { } }
        } catch (e: CancellationException) { gefangen = true }
        assertTrue(gefangen)
    }

    @Test fun fristUndBeschriftung() {
        assertTrue(StandRegel.frisch(1_000L, 500L, 1_400L))
        assertFalse(StandRegel.frisch(1_000L, 500L, 1_500L))
        assertFalse(StandRegel.frisch(2_000L, 500L, 1_000L))   // Uhr zurueckgestellt
        val zeit = { t: Long -> "Z$t" }; val dz = { t: Long -> "D$t" }; val tag = { t: Long -> (t / 100).toString() }
        assertEquals("Z150", StandRegel.beschriftung(150L, 199L, zeit, dz, tag))
        assertEquals("D150", StandRegel.beschriftung(150L, 250L, zeit, dz, tag))
    }

    // ---- Chart: Platzhalter waehrend des Ladens, „Kein Verlauf“ erst nach dem Abruf

    @Test fun chartZustand() {
        assertEquals(ChartInhalt.PLATZHALTER, chartInhalt(0, laedt = true))
        assertEquals(ChartInhalt.PLATZHALTER, chartInhalt(1, laedt = true))
        assertEquals(ChartInhalt.KEIN_VERLAUF, chartInhalt(0, laedt = false))
        assertEquals(ChartInhalt.CHART, chartInhalt(2, laedt = true))
        assertEquals(ChartInhalt.CHART, chartInhalt(50, laedt = false))
    }

    // ---- Repository: Charts und Kurse aus dem Speicher

    private fun temp(): File = File(System.getProperty("java.io.tmpdir"), "q76i-" + System.nanoTime()).also { it.mkdirs() }

    private fun kerzenSpeicher(dir: File) = StandSpeicher(List(8) { SichereStandDatei(File(dir, "kerzen$it.txt")) })

    @Test fun repositoryZeigtGespeicherteSerieMitFrischemOderAltemStand() = runBlocking {
        val dir = temp()
        val jetzt = System.currentTimeMillis()
        val k = kerzenSpeicher(dir)
        k.schreiben("SAP.DE|Y1", SerieCodec.kodieren(serie(40)), jetzt - 60_000L)        // Frist 15 Minuten: frisch
        k.schreiben("SAP.DE|M3", SerieCodec.kodieren(serie(40)), jetzt - 3_600_000L)     // aelter als die Frist: alt
        k.schreiben("SAP.DE|D1", "{kaputt", jetzt)                                         // beschaedigt: ignoriert
        k.sichern()
        val repo = MarketRepository(MarketApi(), CoroutineScope(Job()), null, dir)
        val frisch = repo.serieGespeichert("SAP.DE", Range.Y1)
        assertNotNull(frisch); assertTrue(frisch!!.frisch); assertEquals(40, frisch.wert.candles.size)
        val alt = repo.serieGespeichert("SAP.DE", Range.M3)
        assertNotNull(alt); assertFalse(alt!!.frisch)
        assertNull(repo.serieGespeichert("SAP.DE", Range.D1))
        assertNull(repo.serieGespeichert("SAP.DE", Range.MAX))
        // Innerhalb der Frist braucht series() keinen Abruf; ausserhalb der Frist wird es nie als frisch ausgegeben
        assertEquals(40, repo.series("SAP.DE", Range.Y1).candles.size)
    }

    @Test fun gespeicherteKurseStehenNieInQuotes() = runBlocking {
        val dir = temp()
        val st = StandSpeicher(listOf(SichereStandDatei(File(dir, "kurse.txt"))))
        val q = Quote(symbol = "SAP.DE", name = "SAP", price = 150.0, prevClose = 148.0)
        st.schreiben("SAP.DE", Json { allowSpecialFloatingPointValues = true }.encodeToString(q), 12_345L)
        st.sichern()
        val repo = MarketRepository(MarketApi(), CoroutineScope(Job()), null, dir)
        val alt = withTimeout(10_000) { repo.altKurse.first { it.isNotEmpty() } }
        assertEquals(150.0, alt["SAP.DE"]!!.quote.price, 0.0)
        assertEquals(12_345L, alt["SAP.DE"]!!.abgerufen)
        // Orders, Kaeufe, Alarme und Bewertungen lesen quotes: dort ist der gespeicherte Kurs nicht
        assertTrue(repo.quotes.value.isEmpty())
        assertNull(repo.quotes.value["SAP.DE"])
    }

    @Test fun nurDieAnzeigeLiestGespeicherteKurse() {
        val wurzel = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory } ?: return
        val treffer = wurzel.walkTopDown().filter { it.isFile && it.extension == "kt" && it.readText().contains("altKurse") }.map { it.name }.toSet()
        // Erlaubt: das Repository selbst und die beiden Anzeigen. Handel, Orders, Alarme und Depot gehoeren nicht dazu.
        assertEquals(setOf("MarketRepository.kt", "InstrumentScreen.kt", "Sheets.kt"), treffer)
    }

    @Test fun datenStandUeberlebtNeustartMitBeschaedigung() {
        val dir = temp()
        val a = MarketRepository(MarketApi(), CoroutineScope(Job()), null, dir)
        a.datenMerken("profil|AAPL", "{\"x\":1}")
        a.datenMerken("news|AAPL", "[]")
        a.standJetztSichern()
        val b = MarketRepository(MarketApi(), CoroutineScope(Job()), null, dir)
        assertEquals("{\"x\":1}", b.datenGespeichert("profil|AAPL")?.wert)
        // Dateien beschaedigen: kein Absturz, Eintraege fehlen einfach
        dir.listFiles()!!.forEach { it.writeBytes(byteArrayOf(0, 1, 2, 3, 10, 9, 9)) }
        val c = MarketRepository(MarketApi(), CoroutineScope(Job()), null, dir)
        assertNull(c.datenGespeichert("profil|AAPL"))
    }

    @Test fun profilUndNachrichtenSindSerialisierbar() {
        val j = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
        val p = MarketApi.Profile(description = "Text", targetPrice = 12.5, yearly = listOf(MarketApi.FinPeriod("2024", 1L, 2.0, 3.0)), beta = Double.NaN)
        val p2 = j.decodeFromString<MarketApi.Profile>(j.encodeToString(p))
        assertEquals("Text", p2.description); assertEquals(1, p2.yearly.size); assertTrue(p2.beta.isNaN())
        val n = listOf(MarketApi.NewsItem("Titel", "http://x", 5L, "Zusammenfassung", "Quelle"))
        assertEquals(n, j.decodeFromString<List<MarketApi.NewsItem>>(j.encodeToString(n)))
    }

    // ---- Neustart: neue Instanzen auf derselben Datei lesen den Stand

    @Test fun neustartLiestStandAusEchterDatei() {
        val datei = File(temp(), "stand.txt")
        val a = StandSpeicher(listOf(SichereStandDatei(datei)))
        a.schreiben("k", "wert", 4_242L)
        a.sichern()
        assertTrue(datei.length() > 0)
        // Prozess beendet: alles im Arbeitsspeicher ist weg, nur die Datei bleibt
        val b = StandSpeicher(listOf(SichereStandDatei(File(datei.path))))
        assertEquals(StandSpeicher.Eintrag(4_242L, "wert"), b.lesen("k"))
    }

    private fun eintrag(titel: String, zeit: Long, quelle: String = "Feed A") =
        de.troetenrepublik.app.market.Rss.Eintrag(titel = titel, link = "http://x/$titel", zeit = zeit, quelle = quelle, absaetze = listOf("Volltext"))

    private fun newsStandSchreiben(datei: File, liste: List<de.troetenrepublik.app.market.Rss.Eintrag>, t: Long) {
        val sp = StandSpeicher(listOf(SichereStandDatei(datei)))
        sp.schreiben("alle", Json.encodeToString(liste), t)
        sp.sichern()
    }

    @Test fun newsNachNeustartSofortAusDemStandMitZeit() = runBlocking {
        val datei = File(temp(), "news.txt")
        newsStandSchreiben(datei, listOf(eintrag("Eins", 2_000L), eintrag("Zwei", 1_000L)), 777L)
        val repo = de.troetenrepublik.app.market.NewsRepository(MarketApi(), standDatei = SichereStandDatei(datei))
        assertTrue(repo.stand.value.eintraege.isEmpty())
        repo.ausStand()
        val st = repo.stand.value
        assertEquals(listOf("Eins", "Zwei"), st.eintraege.map { it.titel })
        assertTrue(st.gespeichert); assertEquals(777L, st.geladenAm)
        // Ohne Antwort aller Feeds bleibt der Stand mit seiner Zeit sichtbar und gilt weiter als gespeichert
        repo.laden(de.troetenrepublik.app.market.NewsRepository.Auftrag(emptyList(), true))
        assertEquals(2, repo.stand.value.eintraege.size)
        assertTrue(repo.stand.value.gespeichert); assertEquals(777L, repo.stand.value.geladenAm); assertFalse(repo.stand.value.laedt)
    }

    @Test fun newsStandBeschaedigtOderFehltBleibtLeer() = runBlocking {
        val d1 = File(temp(), "n.txt")
        d1.writeText("alle	5	{kaputt")
        val r1 = de.troetenrepublik.app.market.NewsRepository(MarketApi(), standDatei = SichereStandDatei(d1))
        r1.ausStand()
        assertTrue(r1.stand.value.eintraege.isEmpty()); assertFalse(r1.stand.value.gespeichert)
        val r2 = de.troetenrepublik.app.market.NewsRepository(MarketApi(), standDatei = SichereStandDatei(File(temp(), "gibtsnicht.txt")))
        r2.ausStand()
        assertTrue(r2.stand.value.eintraege.isEmpty())
        val r3 = de.troetenrepublik.app.market.NewsRepository(MarketApi())
        r3.ausStand()
        assertTrue(r3.stand.value.eintraege.isEmpty())
    }
}
