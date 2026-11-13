package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Lage 0.77: Verlauf des ENSO-Index, Themen der Durchbruchmeldungen, Stufen der Kennzahlen.
class Fassung077GTest {
    private val jetzt = 1_800_000_000_000L

    @Test(timeout = 20_000)
    fun ensoVerlaufDieLetztenZwoelfWerte() {
        val punkte = (1..20).map { Enso.Punkt("ASO", 2000 + it, it / 10.0) }
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, ensoIndex = Enso.Index(jetzt, punkte)))
        val k = a.single().kennzahlen.first { it.schluessel == "ENSO_INDEX" }
        assertEquals(Lagebild.MAX_VERLAUF, k.posten.size)
        assertEquals("ASO 2009", k.posten.first().name)
        assertEquals(2.0, k.posten.last().wert, 1e-9)
        assertEquals(2.0, k.wert, 1e-9)
    }

    @Test(timeout = 20_000)
    fun durchbruchThemenNachAnzahl() {
        fun m(t: String) = Durchbrueche.Meldung(t, "x", "u", jetzt, "q")
        val p = Lagebild.durchbruchThemen(listOf(m("ki"), m("ki"), m("ki"), m("fusion"), m("fusion"), m("batterie")), 2)
        assertEquals(listOf("ki", "fusion"), p.map { it.name })
        assertEquals(3.0, p[0].wert, 1e-9)
        assertTrue(Lagebild.durchbruchThemen(emptyList()).isEmpty())
    }

    @Test(timeout = 20_000)
    fun stufenDerKennzahlen() {
        fun k(s: String, w: Double, auff: Boolean = false, zusatz: String? = null) = Lagebild.Kennzahl(s, Lagebild.Einheit.ANZAHL, w, auffaellig = auff, zusatz = zusatz)
        assertEquals(2, Lagebild.stufe(k("WACHSTUM", -0.5)))
        assertEquals(0, Lagebild.stufe(k("WACHSTUM", 3.0)))
        assertEquals(1, Lagebild.stufe(k("INFLATION", Imf.SCHWELLE_INFLATION_ERHOEHT)))
        assertEquals(0, Lagebild.stufe(k("INFLATION", 3.0)))
        assertEquals(1, Lagebild.stufe(k("ENGPASS_SCHLECHTESTER", Engpaesse.STOERUNG_PROZENT)))
        assertEquals(0, Lagebild.stufe(k("ENGPASS_SCHLECHTESTER", -5.0)))
        assertEquals(2, Lagebild.stufe(k("KATASTROPHEN", 4.0, true, "2")))
        assertEquals(1, Lagebild.stufe(k("KATASTROPHEN", 4.0, true, "0")))
        assertEquals(1, Lagebild.stufe(k("SPANNUNG", 2.0, true)))
        val a = Lagebild.Aussage(Lagebild.Thema.KONJUNKTUR, listOf(k("WACHSTUM", 3.0), k("INFLATION", 6.0)), "globus")
        val b = Lagebild.Aussage(Lagebild.Thema.KLIMA, listOf(k("KATASTROPHEN", 1.0)), "globus")
        assertEquals(1, Lagebild.stufe(a))
        assertEquals(1 to 3, Lagebild.auffaellige(listOf(a, b)))
    }
}
