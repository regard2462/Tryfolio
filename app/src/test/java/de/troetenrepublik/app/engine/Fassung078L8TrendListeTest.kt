package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung078L8TrendListeTest {

    private val jetzt = 1_794_000_000_000L

    private fun meldung(thema: String, titel: String, zeit: Long = jetzt - 1000) = Durchbrueche.Meldung(thema, titel, "https://x/$titel", zeit, "Quelle")
    private fun block(id: String, neu: Int) = Durchbrueche.Block(Durchbrueche.thema(id)!!, List(neu) { meldung(id, "$id$it") }, null, jetzt)
    private fun korb(schluessel: String, vararg werte: Double): Themenkoerbe.Ergebnis {
        val k = Themenkoerbe.KOERBE.first { it.schluessel == schluessel }
        return Themenkoerbe.auswerten(k, k.symbole.take(werte.size).mapIndexed { i, s -> s to werte[i] }.toMap())
    }

    private val alleKurse = Branchentrends.BRANCHEN.mapIndexed { i, b -> b.symbol to (i - 5).toDouble() }.toMap()
    private val alleKoerbe = Themenkoerbe.KOERBE.associate { it.schluessel to korb(it.schluessel, 1.0, -2.0, 3.0) }
    private val alleBloecke = Durchbrueche.THEMEN.map { block(it.id, 1) }

    @Test
    fun jedeBrancheJederKorbJedesThemaGenauEinmal() {
        val e = TrendListe.EINTRAEGE
        assertEquals(Branchentrends.BRANCHEN.map { it.symbol }.sorted(), e.mapNotNull { it.branche }.sorted())
        assertEquals(Themenkoerbe.KOERBE.map { it.schluessel }.sorted(), e.mapNotNull { it.korb }.sorted())
        assertEquals(Durchbrueche.THEMEN.map { it.id }.sorted(), e.flatMap { it.durchbrueche }.sorted())
        assertEquals(e.size, e.map { it.id }.toSet().size)
        e.forEach { assertTrue(it.id, it.teile >= 1) }
        // Summe der Teile: nichts fehlt, nichts doppelt
        assertEquals(Branchentrends.BRANCHEN.size + Themenkoerbe.KOERBE.size + Durchbrueche.THEMEN.size, e.sumOf { it.teile })
    }

    @Test
    fun zusammengelegtePaare() {
        fun e(id: String) = TrendListe.eintrag(id)!!
        assertEquals(listOf("SMH", "HALBLEITER", "halbleiter"), e("halbleiter").let { listOf(it.branche, it.korb) + it.durchbrueche })
        assertEquals("KI" to listOf("ki"), e("ki").let { it.korb to it.durchbrueche })
        assertEquals("ITA" to "VERTEIDIGUNG", e("ruestung").let { it.branche to it.korb })
        assertEquals("WELTRAUM" to listOf("raumfahrt"), e("raumfahrt").let { it.korb to it.durchbrueche })
        assertEquals("ROBOTIK" to listOf("robotik"), e("robotik").let { it.korb to it.durchbrueche })
        assertEquals("KERNENERGIE" to listOf("fusion"), e("kernenergie").let { it.korb to it.durchbrueche })
        assertEquals("ICLN" to listOf("energie"), e("energie").let { it.branche to it.durchbrueche })
        assertEquals("PPH" to listOf("medizin"), e("pharma").let { it.branche to it.durchbrueche })
        assertEquals("XBI" to listOf("krebs", "genetik"), e("biotech").let { it.branche to it.durchbrueche })
        assertEquals("XLC" to listOf("kommunikation"), e("kommunikation").let { it.branche to it.durchbrueche })
        // Seit 0.79 hat jede Zeile ein Thema der Durchbrueche; nur Zeilen ohne Branche und Korb haben einen Teil
        assertEquals(4, TrendListe.EINTRAEGE.count { it.teile == 1 })
        assertEquals(32, TrendListe.EINTRAEGE.size)
        assertEquals("biotech", TrendListe.zuDurchbruch("genetik")?.id)
        assertNull(TrendListe.zuDurchbruch("gibtesnicht"))
    }

    @Test
    fun jederThemenEtfGenauEinerZeile() {
        val etfs = TrendListe.EINTRAEGE.mapNotNull { it.etf }
        assertEquals(etfs, TrendListe.THEMEN_ETFS)
        assertEquals(etfs.size, etfs.toSet().size)
        // Kein Themen-ETF ist zugleich Branchen-ETF, keine Zeile hat beides
        assertTrue(etfs.none { s -> Branchentrends.BRANCHEN.any { it.symbol == s } })
        assertTrue(TrendListe.EINTRAEGE.none { it.branche != null && it.etf != null })
        // Vollstaendig: jede Zeile hat einen Fonds, ausser den zwei ohne passenden ETF
        assertEquals(listOf("abnehmen", "wasserstoff"), TrendListe.EINTRAEGE.filter { it.fonds == null }.map { it.id })
        assertEquals(
            mapOf("ki" to "AIQ", "cyber" to "CIBR", "quanten" to "QTUM", "spiele" to "ESPO", "robotik" to "BOTZ", "krypto" to "BLOK", "solar" to "TAN",
                "kernenergie" to "URA", "batterie" to "LIT", "raumfahrt" to "UFO", "materialien" to "XLB", "klima" to "MOO"),
            TrendListe.EINTRAEGE.filter { it.etf != null }.associate { it.id to it.etf!! },
        )
    }

    @Test
    fun themenEtfLiefertKursWieEineBranche() {
        val z = TrendListe.zeilen(alleKurse + mapOf("AIQ" to 4.5, "QTUM" to -1.0), alleKoerbe, alleBloecke)
        val ki = z.first { it.eintrag.id == "ki" }
        assertEquals("AIQ", ki.fonds)
        assertNull(ki.branche)
        assertEquals(4.5, ki.wert!!, 1e-9)
        // Der Median des Korbs steht dann als eigene Angabe im aufgeklappten Teil
        assertEquals(1.0, ki.korbMedian!!, 1e-9)
        val q = z.first { it.eintrag.id == "quanten" }
        assertEquals(-1.0, q.wert!!, 1e-9)
        assertTrue(!q.einTeil)
        // ETF ohne Kurs: kein Fonds an der Zeile, der Median des Korbs ist die Zahl
        val c = z.first { it.eintrag.id == "cyber" }
        assertNull(c.fonds)
        assertEquals(1.0, c.wert!!, 1e-9)
        // Nur der Kurs eines Themen-ETF: die Zeile steht da
        assertEquals("batterie", TrendListe.zeilen(mapOf("LIT" to 2.0), emptyMap(), emptyList()).single().eintrag.id)
        assertEquals("SMH", z.first { it.eintrag.id == "halbleiter" }.fonds)
    }

    @Test
    fun namen() {
        assertEquals("Halbleiter", TrendListe.name(TrendListe.eintrag("halbleiter")!!, true))
        assertEquals("Semiconductors", TrendListe.name(TrendListe.eintrag("halbleiter")!!, false))
        assertEquals("Kernenergie und Fusion", TrendListe.name(TrendListe.eintrag("kernenergie")!!, true))
        assertEquals("Nuclear energy and fusion", TrendListe.name(TrendListe.eintrag("kernenergie")!!, false))
        assertEquals("Künstliche Intelligenz", TrendListe.name(TrendListe.eintrag("ki")!!, true))
        // Zeile aus einem Korb und einem Thema: Name des Themas
        assertEquals("Cybersicherheit", TrendListe.name(TrendListe.eintrag("cyber")!!, true))
        assertEquals("Cybersecurity", TrendListe.name(TrendListe.eintrag("cyber")!!, false))
        // Jede andere Zeile hat einen Namen in beiden Sprachen
        TrendListe.EINTRAEGE.filter { it.branche != null || it.durchbrueche.isNotEmpty() }.forEach {
            assertTrue(it.id, !TrendListe.name(it, true).isNullOrBlank() && !TrendListe.name(it, false).isNullOrBlank())
        }
    }

    @Test
    fun mitAllenDatenKeinTeilVerlorenUndKeinerDoppelt() {
        val z = TrendListe.zeilen(alleKurse, alleKoerbe, alleBloecke)
        assertEquals(TrendListe.EINTRAEGE.size, z.size)
        assertEquals(z.size, z.map { it.eintrag.id }.toSet().size)
        assertEquals(Branchentrends.BRANCHEN.map { it.symbol }.sorted(), z.mapNotNull { it.branche?.symbol }.sorted())
        assertEquals(Themenkoerbe.KOERBE.map { it.schluessel }.sorted(), z.mapNotNull { it.korb?.korb?.schluessel }.sorted())
        assertEquals(Durchbrueche.THEMEN.map { it.id }.sorted(), z.flatMap { r -> r.bloecke.map { it.thema.id } }.sorted())
    }

    @Test
    fun zeileZeigtBeideSeiten() {
        val z = TrendListe.zeilen(alleKurse, alleKoerbe, alleBloecke)
        val h = z.first { it.eintrag.id == "halbleiter" }
        assertEquals(alleKurse["SMH"]!!, h.kurs!!, 1e-9)
        assertEquals(h.kurs!!, h.wert!!, 1e-9)
        assertEquals(1.0, h.korbMedian!!, 1e-9)
        assertEquals(2, h.korb!!.anzahlSteigend)
        assertEquals(1, h.neu)
        assertTrue(!h.einTeil)
        // Korb ohne Branche: der Median ist die Zahl der Zeile, nicht noch einmal darunter
        val ki = z.first { it.eintrag.id == "ki" }
        assertNull(ki.kurs)
        assertEquals(1.0, ki.wert!!, 1e-9)
        assertNull(ki.korbMedian)
        // Zwei Themen in einer Zeile zaehlen zusammen
        assertEquals(2, z.first { it.eintrag.id == "biotech" }.neu)
        assertTrue(!z.first { it.eintrag.id == "software" }.einTeil)
        assertTrue(z.first { it.eintrag.id == "quanten" }.einTeil)
    }

    @Test
    fun ordnung() {
        val z = TrendListe.zeilen(alleKurse, alleKoerbe, listOf(block("quanten", 1), block("batterie", 3), block("klima", 0).let { Durchbrueche.Block(it.thema, emptyList(), Durchbrueche.ThemaInhalt("klima", r = listOf(Durchbrueche.Eintrag(j = "2020", de = "x", en = "x"))), jetzt) }))
        val mit = z.takeWhile { it.wert != null }
        val ohne = z.drop(mit.size)
        assertTrue(ohne.all { it.wert == null })
        assertEquals(mit.map { it.wert!! }.sortedDescending(), mit.map { it.wert!! })
        // Ohne Zahl: die meisten neuen Durchbrueche zuerst, dann die Reihenfolge der Tabelle
        assertEquals(listOf("batterie", "quanten", "klima"), ohne.map { it.eintrag.id })
        // Themen ohne Block fehlen (materialien), Zeilen mit anderem Teil bleiben ohne den Teil
        assertTrue(z.none { it.eintrag.id == "materialien" })
        assertTrue(z.first { it.eintrag.id == "halbleiter" }.bloecke.isEmpty())
    }

    @Test
    fun teileOhneDatenFehlen() {
        // Nur Kurse: Zeilen ohne Branche fehlen, Koerbe und Themen haengen an keiner Zeile
        val nurKurse = TrendListe.zeilen(alleKurse, emptyMap(), emptyList())
        assertEquals(Branchentrends.BRANCHEN.size, nurKurse.size)
        assertTrue(nurKurse.all { it.korb == null && it.bloecke.isEmpty() && it.einTeil })
        // Korb ohne einen Wert mit Kurs zaehlt nicht
        val leer = Themenkoerbe.auswerten(Themenkoerbe.KOERBE.first { it.schluessel == "CYBER" }, emptyMap())
        assertTrue(TrendListe.zeilen(emptyMap(), mapOf("CYBER" to leer), emptyList()).isEmpty())
        // Branche ohne Kurs: die Zeile bleibt mit Korb, ohne Branche; der Median ist dann die Zahl der Zeile
        val r = TrendListe.zeilen(emptyMap(), mapOf("VERTEIDIGUNG" to korb("VERTEIDIGUNG", 2.0, 4.0)), emptyList()).single()
        assertEquals("ruestung", r.eintrag.id)
        assertNull(r.branche)
        assertEquals(3.0, r.wert!!, 1e-9)
        assertNotNull(TrendListe.name(r.eintrag, true))
        assertTrue(TrendListe.zeilen(emptyMap(), emptyMap(), emptyList()).isEmpty())
    }
}
