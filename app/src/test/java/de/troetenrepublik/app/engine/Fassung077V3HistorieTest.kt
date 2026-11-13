package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.InsiderSeltenheit.Art
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.77 V3] Seltene Kaeufe gegen die verdichtete Kaufhistorie ab 2016 (tools/sec/historie.py).
class Fassung077V3HistorieTest {

    private val beginn = "2025-05-12"
    private val ende = "2026-09-30"

    // Auszug im Format der Datei: [Zahl der Kaeufe, Rekordkette neueste zuerst, Wochenkette neueste zuerst]
    private val text = """{"stand":"2025-03-31","von":"2016-01-01","bis":"2025-03-31","quelle":"SEC","firmen":{
        "ALT":[3,[[20210301,200000],[20190611,900000]],[]],
        "NUR":[0,[],[]],
        "WOC":[5,[[20230102,100000]],[[20190305,4],[20170301,5]]],
        "GRO":[2,[[20240101,400000],[20230101,500000]],[]],
        "ZUL":[1,[[20250301,600000]],[]]}}"""

    private fun h() = InsiderHistorie.lesen(text)!!

    private fun g(k: String, person: String, tag: String, wert: Double, kauf: Boolean = true) =
        SecInsider.Geschaeft(a = "$k-$person-$tag", k = k, e = "$k Inc.", p = person, kauf = kauf, d = tag, f = tag, wert = wert)

    @Test(timeout = 20_000)
    fun liestDieDatei() {
        val x = h()
        assertEquals("2016-01-01", x.von)
        assertEquals(2016, x.jahr)
        assertEquals(5, x.firmen.size)
        val a = x.firmen.getValue("ALT")
        assertEquals(3, a.n)
        assertEquals(900_000.0, a.groesster, 0.0)
        assertEquals(java.time.LocalDate.of(2021, 3, 1).toEpochDay(), a.letzter!!.tag)
        assertEquals(java.time.LocalDate.of(2019, 6, 11).toEpochDay(), a.letzterMit(300_000.0)!!.tag)
        assertNull(a.letzterMit(1_000_000.0))
        assertEquals(java.time.LocalDate.of(2017, 3, 1).toEpochDay(), x.firmen.getValue("WOC").letzteWoche(5)!!.tag)
        assertNull(InsiderHistorie.lesen("kaputt"))
        assertNull(InsiderHistorie.lesen("""{"firmen":{}}"""))
    }

    @Test(timeout = 20_000)
    fun ersterKaufSeitJahren() {
        val l = listOf(g("ALT", "A", "2026-09-20", 700_000.0))
        val f = InsiderSeltenheit.fuerFirma(l, ende, beginn, historie = h()).single { it.art == Art.ERSTER }
        assertEquals("2021-03-01", f.seit)
        assertEquals(false, f.seitBeginn)
        assertEquals(5, f.monate / 12)
        // ohne Historie die bisherige Aussage: es fehlt jede fruehere Meldung, also keine
        assertTrue(InsiderSeltenheit.fuerFirma(l, ende, beginn).isEmpty())
    }

    @Test(timeout = 20_000)
    fun ersterKaufSeitBeginnDerHistorie() {
        val l = listOf(g("NUR", "A", "2026-09-20", 700_000.0))
        val f = InsiderSeltenheit.fuerFirma(l, ende, beginn, historie = h()).single()
        assertTrue(f.seitBeginn)
        assertEquals(2016, f.seitJahr)
        assertEquals("2016-01-01", f.seit)
        assertEquals("Erster Kauf seit 2016", De.skAussage(f.art.ordinal, f.monate, f.seitBeginn, f.zahl, "0,0", f.seitJahr))
        assertEquals("First buy since 2016", En.skAussage(f.art.ordinal, f.monate, f.seitBeginn, f.zahl, "0.0", f.seitJahr))
    }

    @Test(timeout = 20_000)
    fun kuerzlicherKaufInDerHistorieGibtKeineAussage() {
        // letzter Kauf 1. März 2025, neuer Kauf im Dezember 2025: unter 365 Tage Abstand
        assertTrue(InsiderSeltenheit.fuerFirma(listOf(g("ZUL", "A", "2025-12-01", 700_000.0)), "2025-12-15", beginn, historie = h()).isEmpty())
    }

    @Test(timeout = 20_000)
    fun groessterKaufSeitBeginnNurGegenDieHistorie() {
        val l = listOf(g("GRO", "A", "2026-09-20", 1_700_000.0))
        // 3,4 mal der Rekord der Historie (500.000): groesster Kauf seit 2016
        val f = InsiderSeltenheit.fuerFirma(l, ende, beginn, historie = h()).single { it.art == Art.GROESSTER }
        assertTrue(f.seitBeginn)
        assertEquals(2016, f.seitJahr)
        assertEquals(3.4, f.faktor, 1e-9)
        assertEquals("Größter Kauf, 3,4× der bisherige Rekord", De.skAussage(1, f.monate, true, 0, "3,4", 2016))
        // nur das Doppelte des Historienrekords genuegt nicht, auch wenn es die eigenen Daten uebertrifft
        assertTrue(InsiderSeltenheit.fuerFirma(listOf(g("GRO", "A", "2026-09-20", 1_000_000.0)), ende, beginn, historie = h()).none { it.art == Art.GROESSTER })
        // ohne Historie fehlen die frueheren Kaeufe als Vergleich
        assertTrue(InsiderSeltenheit.fuerFirma(l, ende, beginn).none { it.art == Art.GROESSTER })
    }

    @Test(timeout = 20_000)
    fun wocheMitHistorie() {
        val l = listOf(g("WOC", "A", "2026-09-22", 400_000.0), g("WOC", "B", "2026-09-24", 400_000.0), g("WOC", "C", "2026-09-28", 400_000.0))
        val f = InsiderSeltenheit.fuerFirma(l, ende, beginn, historie = h()).single { it.art == Art.WOCHE }
        assertEquals(3, f.zahl)
        // letzte Woche mit mindestens drei Kaeufern: 5. März 2019
        assertEquals("2019-03-05", f.seit)
        assertEquals(false, f.seitBeginn)
        assertTrue(f.monate >= 24)
    }

    @Test(timeout = 20_000)
    fun ohneEintragGiltDasBisherige() {
        val l = listOf(g("XYZ", "A", "2025-06-02", 50_000.0), g("XYZ", "B", "2026-09-15", 800_000.0))
        val a = InsiderSeltenheit.fuerFirma(l, ende, beginn)
        val b = InsiderSeltenheit.fuerFirma(l, ende, beginn, historie = h())
        assertEquals(a.map { it.art to it.luecke }, b.map { it.art to it.luecke })
        assertNotNull(b.firstOrNull())
        assertEquals(0, b.first().seitJahr)
    }

    @Test(timeout = 20_000)
    fun texteMitJahren() {
        assertEquals("Erster Kauf seit 5 Jahren", De.skAussage(2, 62, false, 0, "0", 0))
        assertEquals("Erster Kauf seit 14 Monaten", De.skAussage(2, 14, false, 0, "0", 0))
        assertEquals("First buy in 5 years", En.skAussage(2, 62, false, 0, "0", 0))
        assertEquals("Erster Kauf in 15 Monaten", De.skAussage(2, 15, true, 0, "0", 0))
        assertEquals("3 Käufer in einer Woche, 6 J. Pause", De.skAussage(0, 75, false, 3, "0", 0))
    }

    @Test(timeout = 20_000)
    fun mitgelieferteDateiIstLesbar() {
        val f = java.io.File("src/main/assets/copytrading/sec_historie.json")
        if (!f.exists()) return
        val x = InsiderHistorie.lesen(f.readText())!!
        assertEquals("2016-01-01", x.von)
        assertTrue(x.firmen.size > 5000)
        assertTrue(f.length() < 1_500_000)
    }
}
