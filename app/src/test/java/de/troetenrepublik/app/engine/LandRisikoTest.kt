package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.73] Punktzahl des politischen und strukturellen Landrisikos fuer den Aktienfinder
class LandRisikoTest {

    @Test
    fun ohneAllesNull() {
        assertNull(LandRisiko.punkte(LandRisiko.Fakten()))
    }

    @Test
    fun nurWgi() {
        assertEquals(90.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 90.0))!!, 1e-9)
        assertEquals(12.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 12.0))!!, 1e-9)
        // Ohne Weltbankzahl, aber mit einer Angabe: Basis 50
        assertEquals(50.0, LandRisiko.punkte(LandRisiko.Fakten(reise = 2))!!, 1e-9)
    }

    @Test
    fun abzuege() {
        // Sanktionen beider Listen: 50 - 20
        assertEquals(30.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, sanktionenEu = 2, sanktionenUs = 1))!!, 1e-9)
        // Krieg -20, bewaffneter Konflikt -10
        assertEquals(30.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, konflikt = 2))!!, 1e-9)
        assertEquals(40.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, konflikt = 1))!!, 1e-9)
        // Reisehinweis Stufe 4 -10, Stufe 3 -5, Stufe 2 nichts
        assertEquals(40.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, reise = 4))!!, 1e-9)
        assertEquals(45.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, reise = 3))!!, 1e-9)
        assertEquals(50.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, reise = 2))!!, 1e-9)
        // OpenSanctions: wenige Treffer -5, viele -10
        assertEquals(45.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, openSanctions = 7))!!, 1e-9)
        assertEquals(40.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, openSanctions = 120))!!, 1e-9)
        // Strukturelle Probleme: 3 je, hoechstens drei
        assertEquals(41.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, strukturen = 3))!!, 1e-9)
        assertEquals(41.0, LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, strukturen = 5))!!, 1e-9)
    }

    @Test
    fun untenGekappt() {
        // Alles schlimm: bleibt bei 0 stehen
        val p = LandRisiko.punkte(LandRisiko.Fakten(wgi = 3.0, sanktionenEu = 1, sanktionenUs = 1, openSanctions = 99, konflikt = 2, reise = 4, strukturen = 4))!!
        assertEquals(0.0, p, 1e-9)
    }

    @Test
    fun landZaehltStandardmaessigNicht() {
        // Standard: Land 0, Kurstrend 20, Insider 10, Summe 100; Landpunkte aendern die Gesamtpunktzahl dann nicht
        assertEquals(0, Aktienfinder.Bereich.LAND.gewicht)
        assertEquals(15, Aktienfinder.Bereich.TREND.gewicht)
        assertEquals(10, Aktienfinder.Bereich.INSIDER.gewicht)
        assertEquals(100, Aktienfinder.Bereich.entries.sumOf { it.gewicht })
        val mit = Aktienfinder.bewerten(listOf(Aktienfinder.Daten(symbol = "A", name = "A", kgv = 10.0, landPunkte = 100.0))).single()
        val ohne = Aktienfinder.bewerten(listOf(Aktienfinder.Daten(symbol = "A", name = "A", kgv = 10.0, landPunkte = 0.0))).single()
        assertEquals(ohne.gesamt, mit.gesamt, 1e-9)
        // Der Bereich wird trotzdem berechnet
        assertEquals(100.0, mit.bereiche.getValue(Aktienfinder.Bereich.LAND)!!, 1e-9)
    }

    private fun namen(vararg n: String) = n.map { Laenderrisiken.norm(it) }

    @Test
    fun koalitionsbeteiligungZaehltNichtAlsKonfliktImLand() {
        val jemen = Konflikte.Konflikt("Government of United Kingdom, Government of United States of America", "Government of Yemen (North Yemen)", 1)
        // USA und Grossbritannien sind nur Teil der Koalition, der Jemen wird angegriffen
        assertNull(LandRisiko.konfliktStufe(listOf(jemen), namen("United States", "USA", "United States of America")))
        assertNull(LandRisiko.konfliktStufe(listOf(jemen), namen("United Kingdom")))
        assertEquals(1, LandRisiko.konfliktStufe(listOf(jemen), namen("Yemen")))
    }

    @Test
    fun krieg2ZwischenRegierungenZaehltBeiderseits() {
        val krieg = Konflikte.Konflikt("Government of Russia (Soviet Union)", "Government of Ukraine", 2)
        assertEquals(2, LandRisiko.konfliktStufe(listOf(krieg), namen("Russia")))
        assertEquals(2, LandRisiko.konfliktStufe(listOf(krieg), namen("Ukraine")))
        // Mehrere Konflikte: der schwerste zaehlt, ein fremder Name bleibt ohne Wirkung auf die Wahl
        val rebellen = Konflikte.Konflikt("Government of Ukraine", "Gruppe", 1)
        assertEquals(2, LandRisiko.konfliktStufe(listOf(rebellen, krieg), namen("Ukraine")))
        assertNull(LandRisiko.konfliktStufe(emptyList(), namen("Ukraine")))
    }

    @Test
    fun konfliktUndStabilitaetZaehlenNichtDoppelt() {
        // WGI-Mittel 60 mit Stabilitaet 0: ohne Konflikt zaehlt das Mittel von sechs, mit Konflikt das der anderen fuenf
        val ohne = LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, stabilitaet = 5.0))!!
        assertEquals(50.0, ohne, 1e-9)
        val mit = LandRisiko.punkte(LandRisiko.Fakten(wgi = 50.0, stabilitaet = 5.0, konflikt = 1))!!
        // Mittel der anderen fuenf: (300 - 5) / 5 = 59, dazu -10 fuer den Konflikt
        assertEquals(49.0, mit, 1e-9)
    }

    @Test
    fun bereichLandZaehlt() {
        // Der Bereich Land wirkt in der Gesamtpunktzahl; ohne Landpunkte zaehlt er neutral mit 50
        val mit = Aktienfinder.Daten(symbol = "A", name = "A", landPunkte = 100.0)
        val ohne = Aktienfinder.Daten(symbol = "A", name = "A")
        val g = mapOf(Aktienfinder.Bereich.LAND to 100.0)
        val a = Aktienfinder.bewerten(listOf(mit), g).single()
        val b = Aktienfinder.bewerten(listOf(ohne), g).single()
        assertEquals(100.0, a.gesamt, 1e-9)
        assertEquals(50.0, b.gesamt, 1e-9)
        assertEquals(100.0, a.bereiche.getValue(Aktienfinder.Bereich.LAND)!!, 1e-9)
        assertTrue(a.gruende.first().kennzahl == Aktienfinder.Kennzahl.LAND)
    }

    @Test
    fun hohesRisikoNurBeiHartenBefunden() {
        fun f(wgi: Double?, konflikt: Int? = null, reise: Int? = null, eu: Int = 0, us: Int = 0) =
            LandRisiko.Fakten(wgi = wgi, konflikt = konflikt, reise = reise, sanktionenEu = eu, sanktionenUs = us, openSanctions = 400, strukturen = 4)
        // Schwellenlaender mit mittlerer Regierungsfuehrung und Reisehinweis 2 oder Konflikt der Stufe 1
        assertEquals(false, LandRisiko.hoch(f(46.0, konflikt = 1, reise = 2)))
        assertEquals(false, LandRisiko.hoch(f(31.0, reise = 2)))
        assertEquals(false, LandRisiko.hoch(f(45.0, eu = 1, reise = 2)))
        // Krieg bei hoher Regierungsfuehrung und Reisehinweis 3 zaehlt nicht
        assertEquals(false, LandRisiko.hoch(f(68.0, konflikt = 2, reise = 3)))
        // Unterstes Viertel, Krieg mit schwacher Regierung, Reisehinweis 4, drei Sanktionsregime
        assertEquals(true, LandRisiko.hoch(f(20.0)))
        assertEquals(true, LandRisiko.hoch(f(35.0, konflikt = 2)))
        assertEquals(true, LandRisiko.hoch(f(60.0, reise = 4)))
        assertEquals(true, LandRisiko.hoch(f(60.0, eu = 2, us = 1)))
        assertEquals(false, LandRisiko.hoch(f(60.0, eu = 1, us = 1)))
        assertNull(LandRisiko.hoch(LandRisiko.Fakten()))
    }
}
