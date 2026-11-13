package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Weitere Kursquelle Moskauer Boerse. Die Dateien in src/test/resources/moex sind echte, gekuerzte Antworten von iss.moex.com
// (09.11.2026): Kurse des Handelsplatzes TQBR, Kurs GAZP, Kerzen zu 10 und 60 Minuten und zum Tag, Splits, Suche, Fixing der Zentralbank.
// Dividenden fuehrt die Schnittstelle nicht (der Pfad .../dividends.json liefert nur die Stammdaten), deshalb gibt es keinen Leser.
class MoexQuelleTest {
    private fun datei(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/moex/$name")) { "Testdatei fehlt: $name" }.readBytes().toString(Charsets.UTF_8)

    @Test
    fun kurseDesHandelsplatzesMitVortagsschlussUndStatus() {
        val m = Iss.kurse(datei("kurse_tqbr.json"), 1_000L)
        val g = m.getValue("GAZP")
        assertTrue(g.price > 0 && g.prevClose > 0)
        assertEquals("RUB", g.nativeCurrency)
        assertEquals(g.price, g.nativePrice, 1e-9)
        assertTrue(g.name.isNotBlank())
        // Handelszeit des letzten Kurses liegt am 9. November 2026
        assertEquals(java.time.LocalDate.of(2026, 11, 9), java.time.LocalDate.ofEpochDay(Iss.tagMsk(g.marketTime)))
        // Werte ohne Kurs (Handel hat noch nicht begonnen) gelten mit dem Vortagsschluss, sonst fehlen sie
        for ((_, q) in m) assertTrue(q.price > 0)
        assertTrue("SBER" in m)
    }

    @Test
    fun einzelKursHatDieSpaltenDerEchtenAntwort() {
        // kurs.json ist der Block marketdata einer Einzelabfrage; die Spalten heissen wie im Sammelabruf
        val z = Iss.tabelle(datei("kurs.json"), "marketdata").single()
        assertEquals("GAZP", z["SECID"].toString().trim('"'))
        for (spalte in listOf("LAST", "OPEN", "HIGH", "LOW", "VOLTODAY", "LASTCHANGEPRCNT", "ISSUECAPITALIZATION", "SYSTIME", "TRADINGSTATUS")) {
            assertTrue("Spalte fehlt: $spalte", spalte in z)
        }
        assertTrue(Iss.KURSE_ADRESSE.contains("LASTCHANGEPRCNT"))
    }

    @Test
    fun kerzenZuStundeZehnMinutenUndTag() {
        for ((datei, minDauer) in listOf("candles_60.json" to 3_600_000L, "candles_10.json" to 600_000L, "candles_24.json" to 86_400_000L)) {
            val k = Iss.kerzen(datei(datei))
            assertTrue("$datei leer", k.size >= 10)
            assertTrue(k.zipWithNext().all { (a, b) -> b.t - a.t >= minDauer })
            assertTrue(k.all { it.close > 0 && it.high >= it.low && it.volume >= 0 })
        }
        // Stundenkerzen am 6.11.2026 beginnen um 6:00 Moskauer Zeit
        val erste = Iss.kerzen(datei("candles_60.json")).first()
        assertEquals(java.time.LocalDate.of(2026, 11, 6).toEpochDay() * 86_400_000L + 3 * 3_600_000L, erste.t)
        assertTrue(Iss.kerzen("kein json").isEmpty())
    }

    @Test
    fun splitsVomSammelabruf() {
        val sp = Iss.splits(datei("splits.json"), "VTBB")
        assertTrue(sp.isNotEmpty())
        // before 1, after 10: aus einer Aktie werden zehn
        assertEquals(10.0, sp[0].verhaeltnis, 1e-9)
        assertEquals(java.time.LocalDate.of(2021, 4, 12).toEpochDay() * 86_400_000L, sp[0].t)
        assertEquals(0.1, Iss.splits(datei("splits.json"), "FXRU").first().verhaeltnis, 1e-9)
        assertTrue(Iss.splits(datei("splits.json"), "GAZP").isEmpty())
    }

    @Test
    fun sucheNurGehandelteAktienAmHauptplatz() {
        val h = Iss.suche(datei("suche.json"))
        assertTrue(h.isNotEmpty())
        assertTrue(h.all { it.symbol.startsWith("MOEX:") && it.type == "EQUITY" })
        assertTrue("MOEX:SBER" in h.map { it.symbol })
        // Fixing-Indizes (Gruppe stock_index) kommen nicht vor
        assertFalse(h.any { it.symbol.startsWith("MOEX:FIX") })
    }

    @Test
    fun rubelkursAusFixingUndZentralbank() {
        assertEquals(92.3944, Iss.eurFixing(datei("fix.json"))!!, 1e-9)
        assertNull(Iss.eurFixing("{}"))
        val xml = """<ValCurs Date="09.11.2026"><Valute ID="R01235"><CharCode>USD</CharCode><Nominal>1</Nominal><Value>80,5000</Value></Valute>
            <Valute ID="R01239"><CharCode>EUR</CharCode><Nominal>1</Nominal><Name>Евро</Name><Value>93,2500</Value></Valute></ValCurs>"""
        assertEquals(93.25, Iss.eurZentralbank(xml)!!, 1e-9)
        assertNull(Iss.eurZentralbank("<x/>"))
    }

    @Test
    fun kuerzelMitQuelleUndAdressen() {
        assertEquals("MOEX" to "GAZP", KursQuellen.teile("MOEX:GAZP"))
        assertTrue(KursQuellen.istExtern("MOEX:SBERP"))
        assertFalse(KursQuellen.istExtern("GAZP"))
        assertFalse(KursQuellen.istExtern("SAP.DE"))
        assertFalse(KursQuellen.istExtern("XXXX:GAZP"))
        assertFalse(KursQuellen.istExtern("MOEX:../x"))
        assertFalse(KursQuellen.istExtern("MOEX:"))
        assertEquals("MOEX:GAZP", KursQuellen.symbolFuerLand("RU", "GAZP"))
        assertNull(KursQuellen.symbolFuerLand("DE", "GAZP"))
        assertNull(KursQuellen.symbolFuerLand("RU", ""))
        assertEquals(10, Iss.intervall("2m"))
        assertEquals(60, Iss.intervall("30m"))
        assertEquals(24, Iss.intervall("1d"))
        assertEquals(7, Iss.intervall("1wk"))
        assertTrue(Iss.kerzenAdresse("GAZP", 24, "2025-11-09", "2026-11-10", 500).contains("securities/GAZP/candles.json"))
    }

    @Test
    fun dividendenFuehrtDieSchnittstelleNicht() {
        // Der Pfad securities/SBER/dividends.json liefert nur Stammdaten und Handelsplaetze, keinen Block dividends
        val t = datei("stammdaten_sber.json")
        assertTrue(Iss.tabelle(t, "dividends").isEmpty())
        assertTrue(Iss.tabelle(t, "description").isNotEmpty() && Iss.tabelle(t, "boards").isNotEmpty())
    }

    @Test
    fun jedeRussischeFirmaDerListeLiegtAnTqbr() {
        val tqbr = datei("tqbr_kennungen.txt").lines().toSet()
        val text = java.io.File(listOf(java.io.File("src/main/assets"), java.io.File("../app/src/main/assets")).first { it.exists() }, "globus/firmen.json").readText()
        val ru = de.troetenrepublik.app.engine.LaenderFirmen.buendel(text).getValue("RU")
        val mit = ru.filter { it.kennung.isNotBlank() }
        assertTrue(mit.size >= 20)
        val fehlt = mit.filter { it.kennung !in tqbr }.map { it.name + ":" + it.kennung }
        assertTrue("Nicht an TQBR: $fehlt", fehlt.isEmpty())
        // Kennungen ohne Handel an TQBR bleiben leer, damit sie die Info-Seite oeffnen
        assertTrue(ru.filter { it.kennung.isBlank() }.all { KursQuellen.symbolFuerLand("RU", it.kennung) == null })
    }

    @Test
    fun quelleMeldetAusfallErstNachMehrerenFehlschlaegenUndErholtSich() = runBlocking {
        var netz = false
        val q = MoexQuelle { if (netz) datei("kurse_tqbr.json") else null }
        assertFalse(q.antwortetNicht)
        q.kurse(listOf("GAZP"))
        q.kurse(listOf("GAZP"))
        assertFalse(q.antwortetNicht)
        q.kurse(listOf("GAZP"))
        assertTrue(q.antwortetNicht)
        netz = true
        assertTrue(q.kurse(listOf("GAZP")).isNotEmpty())
        assertFalse(q.antwortetNicht)
    }

    @Test
    fun quelleLeitetKurseKerzenUndHaeltDenLetztenStand() = runBlocking {
        val aufrufe = ArrayList<String>()
        var netz = true
        val q = MoexQuelle { url ->
            aufrufe += url
            if (!netz) null else when {
                "candles.json" in url && "start=0" in url -> datei("candles_10.json")
                "boards/TQBR/securities.json" in url -> datei("kurse_tqbr.json")
                else -> null
            }
        }
        val k = q.kurse(listOf("GAZP", "FEHLT"))
        assertEquals(setOf("MOEX:GAZP"), k.keys)
        assertEquals("MOEX:GAZP", k.getValue("MOEX:GAZP").symbol)
        // Zweiter Abruf kommt aus dem Speicher
        q.kurse(listOf("SBER"))
        assertEquals(1, aufrufe.count { "boards/TQBR/securities.json" in it })
        val s = q.kerzen("GAZP", "5d", "15m")
        assertTrue(s.candles.size >= 10)
        assertTrue(s.dayStart > 0 && s.dayEnd > s.dayStart)
        // Morgensitzung ab 6:50: Fenster beginnt dort
        assertEquals(java.time.LocalDate.of(2026, 11, 9).toEpochDay() * 86_400_000L - 3 * 3_600_000L + (6 * 60 + 50) * 60_000L, s.dayStart)
        // Ohne Netz: drei Versuche je Abruf, dann der letzte bekannte Stand statt einer leeren Reihe
        netz = false
        aufrufe.clear()
        val spaeter = q.kerzen("GAZP", "5d", "15m")
        assertEquals(s.candles.size, spaeter.candles.size)
        assertEquals(3, aufrufe.count { "candles.json" in it })
        // Fuer einen Zeitraum ohne bekannten Stand bleibt es leer, nichts wird zwischengespeichert
        assertTrue(q.kerzen("GAZP", "1y", "1d").candles.isEmpty())
        netz = true
        assertTrue(q.kerzen("GAZP", "1y", "1d").candles.isNotEmpty())
        // Dividenden fuehrt die Schnittstelle nicht
        assertTrue(q.dividenden("GAZP").isEmpty())
    }

    @Test
    fun ohneNetzLeerStattFehler() = runBlocking {
        val aus = MoexQuelle { null }
        assertTrue(aus.kurse(listOf("GAZP")).isEmpty())
        assertTrue(aus.kerzen("GAZP", "1y", "1d").candles.isEmpty())
        assertTrue(aus.splits("GAZP").isEmpty())
        assertTrue(aus.suche("sber").isEmpty())
        assertTrue(aus.wechselkurse().isEmpty())
    }

    // Kette weiterer Kursquellen: die naechste Quelle antwortet, wenn die vorige nichts liefert oder abbricht
    private class Fake(val name: String, var ok: Boolean, var wirft: Boolean = false) : KursQuelle {
        override val praefix = "MOEX"
        override val waehrung = "RUB"
        var aufrufe = 0
        private fun pruefe() { aufrufe++; if (wirft) error("Abbruch") }
        override suspend fun kurse(kennungen: List<String>): Map<String, Quote> { pruefe(); return if (ok) kennungen.associate { "MOEX:$it" to Quote("MOEX:$it", name, price = 1.0) } else emptyMap() }
        override suspend fun kerzen(kennung: String, range: String, interval: String): Series { pruefe(); return if (ok) Series(listOf(Candle(1L, 1.0, 1.0, 1.0, 1.0))) else Series(emptyList()) }
        override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String) = kerzen(kennung, "", interval)
        override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()
        override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()
        override suspend fun suche(text: String): List<MarketApi.SearchHit> = if (ok) listOf(MarketApi.SearchHit("MOEX:SBER", name, "EQUITY", "x")) else emptyList()
        override suspend fun wechselkurse(): Map<String, Double> = if (ok) mapOf("RUB" to 90.0) else emptyMap()
    }

    @Test
    fun ersteQuelleGenuegtUndZweiteSpringtEin() = runBlocking {
        val a = Fake("a", true)
        val b = Fake("b", true)
        val k = KursQuellenKette(listOf(a, b))
        assertEquals("a", k.kurse(listOf("GAZP")).getValue("MOEX:GAZP").name)
        assertEquals(0, b.aufrufe)
        a.ok = false
        assertEquals("b", k.kurse(listOf("GAZP")).getValue("MOEX:GAZP").name)
        assertEquals(1, k.zuletztAntwortete)
        a.wirft = true
        assertTrue(k.kerzen("GAZP", "1y", "1d").candles.isNotEmpty())
        assertEquals(90.0, k.wechselkurse()["RUB"]!!, 1e-9)
        assertEquals(listOf("MOEX:SBER"), k.suche("sber").map { it.symbol })
    }

    @Test
    fun alleAusgefallenLeerStattFehler() = runBlocking {
        val k = KursQuellenKette(listOf(Fake("a", false, wirft = true), Fake("b", false)))
        assertTrue(k.kurse(listOf("GAZP")).isEmpty())
        assertTrue(k.kerzen("GAZP", "1y", "1d").candles.isEmpty())
        assertTrue(k.wechselkurse().isEmpty())
    }
}
