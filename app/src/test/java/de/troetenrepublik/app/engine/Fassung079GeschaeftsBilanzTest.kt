package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.CopyUebersicht.Geschaeft
import de.troetenrepublik.app.i18n.GeschaeftTexte
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Kauf und Verkauf einer Person heute (0.79): Kurs seit dem Geschaeft aus Sicht der Person, Rest im Bestand, Gewinn nur mit
// belegtem Einstandspreis.
class Fassung079GeschaeftsBilanzTest {

    private val T = CopyUebersicht.TAG
    private val jetzt = 1_000 * T
    private val kurse = KongressAuswertung.Kurse.aus(mapOf(900L to 10.0, 950L to 20.0, 1000L to 15.0))
    private fun kurs(s: String, t: Long): Double? = if (s == "X") kurse.am(t) else null
    private fun heute(s: String): Double? = if (s == "X") kurse.letzter else null
    private val ohneSplit: (String, Long, Long) -> Double = { _, _, _ -> 1.0 }

    private fun v(tage: Int, betrag: Double, preis: Double?, vor: Double?, nach: Double?, einstand: Double? = null) =
        Geschaeft("a", "A", "X", false, jetzt - tage * T, betrag, preis, vor = vor, nach = nach, einstand = einstand)

    @Test
    fun farbeFolgtDerSichtDerPerson() {
        // Kurs nach einem Verkauf gefallen: gut fuer die Person; nach einem Kauf gestiegen: gut
        assertEquals(0.23, GeschaeftsBilanz.sicht(-0.23, true), 1e-12)
        assertEquals(-0.1, GeschaeftsBilanz.sicht(0.1, true), 1e-12)
        assertEquals(0.1, GeschaeftsBilanz.sicht(0.1, false), 1e-12)
        assertEquals(-0.05, GeschaeftsBilanz.sicht(-0.05, false), 1e-12)
    }

    @Test
    fun restUndKaufWertHeute() {
        assertEquals(1_500.0, GeschaeftsBilanz.restWert(100.0, 15.0)!!, 1e-9)
        assertNull(GeschaeftsBilanz.restWert(0.0, 15.0))
        assertNull(GeschaeftsBilanz.restWert(100.0, null))
        assertNull(GeschaeftsBilanz.restWert(null, 15.0))
        assertEquals(880.0, GeschaeftsBilanz.kaufWertHeute(1_000.0, -0.12), 1e-9)
    }

    @Test
    fun gewinnNurMitEinstand() {
        // Erloes 1.100 bei Kurs 22 (50 Stueck), Einstand 10: Gewinn 600, 120 % ueber Einstand
        val g = GeschaeftsBilanz.gewinn(1_100.0, 22.0, 10.0)!!
        assertEquals(600.0, g.betrag, 1e-9)
        assertEquals(1.2, g.prozent, 1e-9)
        // Verlust bei Einstand ueber dem Verkaufskurs
        assertEquals(-250.0, GeschaeftsBilanz.gewinn(1_000.0, 20.0, 25.0)!!.betrag, 1e-9)
        assertNull(GeschaeftsBilanz.gewinn(1_000.0, 20.0, 0.0))
        // ganz verkaufter Kauf: Kaufbetrag 3.000 zu 10, verkauft zu 15
        val k = GeschaeftsBilanz.gewinnAusKauf(3_000.0, 10.0, 15.0)!!
        assertEquals(1_500.0, k.betrag, 1e-9)
        assertEquals(0.5, k.prozent, 1e-9)
        assertNull(GeschaeftsBilanz.gewinnAusKauf(3_000.0, 10.0, null))
    }

    @Test
    fun verkaufMitAusuebungspreisHatGewinnUndRest() {
        // 50 Stueck zu 22 (Erloes 1.100), Option zu 10 ausgeuebt; Bestand 150 -> 100
        val o = CopyUebersicht.einstiege(listOf(v(20, 1_100.0, 22.0, 150.0, 100.0, einstand = 10.0)), ::kurs, ::heute, ohneSplit).ohneKauf.single()
        assertEquals(100.0, o.restStueck!!, 1e-9)
        assertEquals(15.0, o.aktuell!!, 1e-9)
        val g = o.gewinn!!
        assertEquals(600.0, g.betrag, 1e-9)
        assertEquals(1.2, g.prozent, 1e-9)
    }

    @Test
    fun ohneBelegKeinGewinnUndKeinRest() {
        // Verkauf ohne Ausuebungspreis: kein Gewinn, der Rest steht trotzdem da
        val a = CopyUebersicht.einstiege(listOf(v(20, 1_100.0, 22.0, 150.0, 100.0)), ::kurs, ::heute, ohneSplit).ohneKauf.single()
        assertNull(a.gewinn)
        assertEquals(100.0, a.restStueck!!, 1e-9)
        // Einer von zwei Verkaeufen ohne Beleg: kein Gewinn
        val b = CopyUebersicht.einstiege(
            listOf(v(60, 550.0, 11.0, 200.0, 150.0, einstand = 5.0), v(20, 1_100.0, 22.0, 150.0, 100.0)), ::kurs, ::heute, ohneSplit,
        ).ohneKauf.single()
        assertNull(b.gewinn)
        // Bestand fehlt beim letzten Verkauf: kein Rest (der aeltere Bestand waere falsch)
        val c = CopyUebersicht.einstiege(
            listOf(v(60, 550.0, 11.0, 200.0, 150.0), v(20, 1_100.0, 22.0, null, null)), ::kurs, ::heute, ohneSplit,
        ).ohneKauf.single()
        assertNull(c.restStueck)
        assertNull(c.reduziert)
        // Heutiger Kurs steht auch ohne Verkaufspreis da (Rest bewerten)
        val d = CopyUebersicht.einstiege(listOf(v(20, 1_100.0, null, 150.0, 100.0)), ::kurs, ::heute, ohneSplit).ohneKauf.single()
        assertNull(d.verkaufskurs)
        assertNull(d.abstand)
        assertEquals(15.0, d.aktuell!!, 1e-9)
    }

    @Test
    fun ausuebungspreisKommtAusDenMeldungen() {
        val s = SecInsider.Geschaeft(
            a = "1", k = "X", e = "X", p = "Muster A", kauf = false, d = "1970-01-01", n = 50.0, preis = 22.0, wert = 1_100.0, nach = 100.0, ap = 10.0,
        )
        val trades = CopyUebersicht.ausInsidern(listOf(s)).first
        assertEquals(10.0, trades.single().einstand!!, 1e-9)
        assertNull(CopyUebersicht.ausInsidern(listOf(s.copy(ap = null))).first.single().einstand)
    }

    @Test
    fun textePassenInBeideSprachen() {
        assertEquals(GeschaeftTexte.schluessel(true), GeschaeftTexte.schluessel(false))
        for (k in GeschaeftTexte.schluessel(true)) {
            assertTrue(k, GeschaeftTexte.text(true, k).isNotBlank())
            assertTrue(k, GeschaeftTexte.text(false, k).isNotBlank())
        }
        assertEquals("seit Verkauf", GeschaeftTexte.text(true, "seit.verkauf"))
        assertEquals("since sale", GeschaeftTexte.text(false, "seit.verkauf"))
        assertEquals("Rest 15,99 Mio. Stück", GeschaeftTexte.text(true, "rest", listOf("15,99 Mio. Stück")))
        assertNotNull(GeschaeftTexte.text(true, "gewinnBeleg"))
    }
}
