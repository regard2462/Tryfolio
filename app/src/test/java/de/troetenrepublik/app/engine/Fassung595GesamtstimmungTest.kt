package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Tests der Gesamtstimmung (seit 5.95): Normierung der Bestandteile, Gewichte und ihre
// Neuverteilung bei fehlenden Bestandteilen und Gruppen, Mindestzahl, Stufen, Zusammensetzung der
// Eingaben, Marktbreite und Margin.
class Fassung595GesamtstimmungTest {

    // Perzentil in beide Richtungen, Fenster, Mindestzahl, NaN sowie Grenzen und Ampel
    @Test
    fun normierung() {
        val r = listOf(10.0, 20.0, 30.0, 25.0)
        assertEquals(75.0, Gesamtstimmung.perzentil(r, gier = true, fenster = 4, mindestens = 4)!!, 1e-9)
        assertEquals(50.0, Gesamtstimmung.perzentil(r, gier = false, fenster = 4, mindestens = 4)!!, 1e-9)
        // nur die letzten fenster Werte zaehlen, ohne Abschneiden waere der Rang 10 von 11
        val alt = listOf(100.0) + (1..10).map { it.toDouble() }
        assertEquals(100.0, Gesamtstimmung.perzentil(alt, gier = true, fenster = 10, mindestens = 5)!!, 1e-9)
        assertNull(Gesamtstimmung.perzentil(listOf(1.0, 2.0, 3.0), gier = true, fenster = 10, mindestens = 5))
        // NaN faellt auch am Ende raus, danach ist die 9 der letzte und groesste Wert
        val nan = listOf(Double.NaN) + (1..9).map { it.toDouble() } + listOf(Double.NaN)
        assertEquals(100.0, Gesamtstimmung.perzentil(nan, gier = true, fenster = 10, mindestens = 5)!!, 1e-9)
        assertEquals(25.0, Gesamtstimmung.grenzen(25.0, 0.0, 100.0), 1e-9)
        assertEquals(0.0, Gesamtstimmung.grenzen(-5.0, 0.0, 100.0), 1e-9)
        assertEquals(100.0, Gesamtstimmung.grenzen(150.0, 0.0, 100.0), 1e-9)
        // fallende Skala wie Sahm und die Punktgrenze
        assertEquals(50.0, Gesamtstimmung.grenzen(0.2, 0.5, -0.1), 1e-9)
        assertEquals(0.0, Gesamtstimmung.grenzen(0.5, 0.5, -0.1), 1e-9)
        assertEquals(100.0, Gesamtstimmung.grenzen(-0.1, 0.5, -0.1), 1e-9)
        assertEquals(50.0, Gesamtstimmung.grenzen(42.0, 10.0, 10.0), 1e-9)
        assertEquals(70.0, Gesamtstimmung.ampel(Fruehwarnung.Ampel.GRUEN), 1e-9)
        assertEquals(40.0, Gesamtstimmung.ampel(Fruehwarnung.Ampel.GELB), 1e-9)
        assertEquals(20.0, Gesamtstimmung.ampel(Fruehwarnung.Ampel.ROT), 1e-9)
    }

    // Gruppengewichte ergeben 100, ALLE ist vollstaendig, alle Teile zusammen geben einen Wert
    @Test
    fun gewichteSummieren() {
        assertEquals(100.0, Gesamtstimmung.GRUPPEN.sumOf { it.gewicht }, 1e-9)
        assertEquals(12, Gesamtstimmung.GRUPPEN.size)
        assertEquals(24, Gesamtstimmung.ALLE.size)
        assertEquals(24, Gesamtstimmung.ALLE.toSet().size)
        // Krypto hat ein Mitglied
        assertEquals(1, Gesamtstimmung.GRUPPEN.first { it.schluessel == "krypto" }.mitglieder.size)
        assertEquals("schwankung", Gesamtstimmung.gruppeVon("vix"))
        assertEquals(Gesamtstimmung.Art.PERZENTIL, Gesamtstimmung.artVon("vix"))
        assertNull(Gesamtstimmung.gruppeVon("gibtsnicht"))
        assertNull(Gesamtstimmung.artVon("gibtsnicht"))
        val e = Gesamtstimmung.rechnen(Gesamtstimmung.ALLE.associateWith { Gesamtstimmung.Wert(50.0) })!!
        assertEquals(50.0, e.wert, 1e-9)
        assertEquals(1.0, e.teile.sumOf { it.anteil }, 1e-9)
        assertEquals(1.0, e.gruppen.sumOf { it.second }, 1e-9)
        assertEquals(24, e.vorhanden)
        assertEquals(24, e.moeglich)
        assertTrue(e.fehlen.isEmpty())
        assertEquals(0L, e.stand)
        assertEquals(Stimmungsbarometer.Stufe.NEUTRAL, e.stufe)
    }

    // Fehlende Mitglieder geben ihr Gewicht an die uebrigen der Gruppe
    @Test
    fun neuverteilungInGruppe() {
        val e = Gesamtstimmung.rechnen(mapOf(
            "vix" to Gesamtstimmung.Wert(0.0), "vix_struktur" to Gesamtstimmung.Wert(100.0),
            "cnn" to Gesamtstimmung.Wert(50.0), "aaii" to Gesamtstimmung.Wert(50.0)))!!
        val vix = e.teile.first { it.schluessel == "vix" }
        val struktur = e.teile.first { it.schluessel == "vix_struktur" }
        // vvix und move fehlen, ihr Gewicht geht im Verhaeltnis 3 zu 2 an vix und vix_struktur
        assertEquals(3.0 / 2.0, vix.anteil / struktur.anteil, 1e-9)
        // die Gruppe zaehlt 14 von 14 + 10 + 10
        assertEquals(14.0 / 34.0, e.gruppen.first { it.first == "schwankung" }.second, 1e-9)
        // (14/34) * (2/5 * 100) + (10/34) * 50 + (10/34) * 50
        assertEquals(1560.0 / 34.0, e.wert, 1e-9)
        assertEquals(4, e.vorhanden)
        assertEquals(20, e.fehlen.size)
        assertTrue(e.fehlen.containsAll(listOf("vvix", "move", "margin")))
        // cnn und aaii halten Gleichstand, cnn zuerst, danach die Schwankungswerte
        assertEquals(listOf("cnn", "aaii", "vix", "vix_struktur"), e.teile.map { it.schluessel })
    }

    // Fehlende Gruppen fallen heraus, unbekannte Schluessel und NaN zaehlen nicht
    @Test
    fun neuverteilungGruppen() {
        val e = Gesamtstimmung.rechnen(mapOf(
            "cnn" to Gesamtstimmung.Wert(50.0, stand = 100L),
            "aaii" to Gesamtstimmung.Wert(50.0, stand = 300L),
            "margin" to Gesamtstimmung.Wert(50.0, stand = 200L),
            "sicher" to Gesamtstimmung.Wert(50.0),
            "unbekannt" to Gesamtstimmung.Wert(99.0),
            "vix" to Gesamtstimmung.Wert(Double.NaN)))!!
        assertEquals(4, e.vorhanden)
        assertEquals(24, e.moeglich)
        assertEquals(20, e.fehlen.size)
        assertTrue("vix" in e.fehlen)
        assertEquals(Gesamtstimmung.ALLE.filter { it != "cnn" && it != "aaii" && it != "sicher" && it != "margin" }, e.fehlen)
        // 10 + 10 + 8 + 2 werden auf 100 % hochgerechnet, cnn vor umfrage bei Gleichstand
        assertEquals(listOf("cnn", "umfrage", "sicher", "hebel"), e.gruppen.map { it.first })
        assertEquals(10.0 / 30.0, e.gruppen[0].second, 1e-9)
        assertEquals(10.0 / 30.0, e.gruppen[1].second, 1e-9)
        assertEquals(8.0 / 30.0, e.gruppen[2].second, 1e-9)
        assertEquals(2.0 / 30.0, e.gruppen[3].second, 1e-9)
        assertEquals(1.0, e.gruppen.sumOf { it.second }, 1e-9)
        assertEquals(50.0, e.wert, 1e-9)
        assertEquals(300L, e.stand)
    }

    // Mindestzahl an Bestandteilen und Gruppen sowie Mindestsumme der Gruppengewichte
    @Test
    fun mindestens() {
        assertNull(Gesamtstimmung.rechnen(mapOf(
            "cnn" to Gesamtstimmung.Wert(50.0), "aaii" to Gesamtstimmung.Wert(50.0))))
        // drei Teile, aber nur die Gruppe schwankung
        assertNull(Gesamtstimmung.rechnen(mapOf(
            "vix" to Gesamtstimmung.Wert(50.0), "vvix" to Gesamtstimmung.Wert(50.0),
            "move" to Gesamtstimmung.Wert(50.0))))
        // 14 + 10 = 24 Gruppengewicht sind zu wenig
        assertNull(Gesamtstimmung.rechnen(mapOf(
            "vix" to Gesamtstimmung.Wert(50.0), "vvix" to Gesamtstimmung.Wert(50.0),
            "cnn" to Gesamtstimmung.Wert(50.0))))
        // 6 + 2 = 8 Gruppengewicht sind zu wenig
        assertNull(Gesamtstimmung.rechnen(mapOf(
            "cot_sp500" to Gesamtstimmung.Wert(50.0), "cot_nasdaq" to Gesamtstimmung.Wert(50.0),
            "margin" to Gesamtstimmung.Wert(50.0))))
        // 14 + 10 + 10 = 34 Gruppengewicht genuegen
        assertNotNull(Gesamtstimmung.rechnen(mapOf(
            "vix" to Gesamtstimmung.Wert(50.0), "vvix" to Gesamtstimmung.Wert(50.0),
            "cnn" to Gesamtstimmung.Wert(50.0), "aaii" to Gesamtstimmung.Wert(50.0))))
    }

    // Fuenf Teile in drei Gruppen (Anteile 0.3125, 0.3125, 0.1875, 0.09375, 0.09375), deren Summe
    // ohne Rundungsfehler aufgeht; darum treffen die Stufengrenzen genau.
    private fun wertStufe(w: Double): Stimmungsbarometer.Stufe =
        Gesamtstimmung.rechnen(mapOf(
            "cnn" to Gesamtstimmung.Wert(w), "aaii" to Gesamtstimmung.Wert(w),
            "momentum" to Gesamtstimmung.Wert(w), "trend_sp500" to Gesamtstimmung.Wert(w),
            "trend_dax" to Gesamtstimmung.Wert(w)))!!.stufe

    // Stufen des Gesamtwerts samt Grenzen
    @Test
    fun stufen() {
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_ANGST, wertStufe(10.0))
        assertEquals(Stimmungsbarometer.Stufe.ANGST, wertStufe(30.0))
        assertEquals(Stimmungsbarometer.Stufe.NEUTRAL, wertStufe(50.0))
        assertEquals(Stimmungsbarometer.Stufe.GIER, wertStufe(70.0))
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_GIER, wertStufe(90.0))
        assertEquals(Stimmungsbarometer.Stufe.ANGST, wertStufe(25.0))
        assertEquals(Stimmungsbarometer.Stufe.NEUTRAL, wertStufe(55.0))
        assertEquals(Stimmungsbarometer.Stufe.GIER, wertStufe(75.0))
    }

    // Zusammensetzung der Eingaben samt Auswahl der Zeichen
    @Test
    fun eingabenUndZeichen() {
        fun aufruf(lage: Stimmungsbarometer.WsbLage?): Map<String, Gesamtstimmung.Wert> =
            Gesamtstimmung.eingaben(
                cnn = Stimmungsbarometer.Barometer(55.0, Stimmungsbarometer.Stufe.NEUTRAL, stand = 300L),
                aktien = Stimmungsbarometer.Barometer(60.0, Stimmungsbarometer.Stufe.NEUTRAL, stand = 100L, teile = listOf(
                    Stimmungsbarometer.Teil("momentum", 65.0, Stimmungsbarometer.Stufe.GIER),
                    Stimmungsbarometer.Teil("breite", 55.0, Stimmungsbarometer.Stufe.NEUTRAL))),
                vix = null, sicher = null, aaii = null, krypto = null,
                wsb = Stimmungsbarometer.RedditBarometer(
                    Stimmungsbarometer.Barometer(80.0, Stimmungsbarometer.Stufe.GIER, stand = 200L),
                    emptyList(), lage),
                sorgen = null,
                zeichen = listOf(
                    Fruehwarnung.Zeichen(Fruehwarnung.VIX, Fruehwarnung.Ampel.GRUEN, 15.0),
                    Fruehwarnung.Zeichen(Fruehwarnung.VIX_STRUKTUR, Fruehwarnung.Ampel.GRUEN, 5.0, listOf(1L to 1.0)),
                    Fruehwarnung.Zeichen(Fruehwarnung.ZINSKURVE, Fruehwarnung.Ampel.ROT, 1.0),
                    Fruehwarnung.Zeichen(Fruehwarnung.SAHM, Fruehwarnung.Ampel.GELB, 0.2, listOf(1000L to 0.1, 2000L to 0.2))),
                cot = listOf(
                    Positionierung.Lage("sp500", 42.0, 40.0, 63, emptyList(), 999L),
                    Positionierung.Lage("nasdaq", 30.0, 31.0, 20, emptyList(), 888L)),
                zusatz = emptyMap())
        val unklar = aufruf(Stimmungsbarometer.WsbLage(0.5, true, null))
        // unklare WSB-Lage, VIX-Zeichen und zu kurze Reihen bleiben weg
        assertTrue("wsb" !in unklar)
        assertTrue("vix" !in unklar)
        assertTrue("vix_struktur" !in unklar)
        assertEquals(20.0, unklar["zinskurve"]!!.wert, 1e-9)
        assertEquals(1.0, unklar["zinskurve"]!!.roh!!, 1e-9)
        assertEquals(50.0, unklar["sahm"]!!.wert, 1e-9)
        assertEquals(0.2, unklar["sahm"]!!.roh!!, 1e-9)
        assertEquals(2000L, unklar["sahm"]!!.stand)
        assertEquals(63.0, unklar["cot_sp500"]!!.wert, 1e-9)
        assertEquals(42.0, unklar["cot_sp500"]!!.roh!!, 1e-9)
        assertEquals(999L, unklar["cot_sp500"]!!.stand)
        assertEquals(20.0, unklar["cot_nasdaq"]!!.wert, 1e-9)
        assertEquals(65.0, unklar["momentum"]!!.wert, 1e-9)
        assertEquals(100L, unklar["momentum"]!!.stand)
        assertEquals(55.0, unklar["rsp"]!!.wert, 1e-9)
        assertEquals(100L, unklar["rsp"]!!.stand)
        assertEquals(55.0, unklar["cnn"]!!.wert, 1e-9)
        assertEquals(55.0, unklar["cnn"]!!.roh!!, 1e-9)
        assertEquals(300L, unklar["cnn"]!!.stand)
        val klar = aufruf(Stimmungsbarometer.WsbLage(0.9, false, null))
        assertEquals(80.0, klar["wsb"]!!.wert, 1e-9)
        assertEquals(80.0, klar["wsb"]!!.roh!!, 1e-9)
        assertEquals(200L, klar["wsb"]!!.stand)
    }

    // Marktbreite und Margin
    @Test
    fun breite() {
        // zwei ueber der Linie von dreien mit Linie, ein Hoch, ein Tief, vier mit Spanne
        val b = Gesamtstimmung.breite(listOf(
            Gesamtstimmung.Kurslage(120.0, 100.0, 130.0, 90.0),
            Gesamtstimmung.Kurslage(130.0, 100.0, 132.0, 100.0),
            Gesamtstimmung.Kurslage(90.0, 100.0, 110.0, 95.0),
            Gesamtstimmung.Kurslage(95.0, null, 100.0, null),
            Gesamtstimmung.Kurslage(0.0, 100.0, 100.0, 50.0),
            Gesamtstimmung.Kurslage(50.0, 0.0, 60.0, 40.0)))
        assertEquals(Gesamtstimmung.Breite(2, 3, 1, 1, 4), b)
        assertNull(Gesamtstimmung.breite200(b, 1L))
        assertNull(Gesamtstimmung.hochTief(b, 1L))
        // Anteil 60 % liegt bei (60 - 20) / (80 - 20); mindestens greift vor der Rechnung
        val voll = Gesamtstimmung.Breite(180, 300, 60, 20, 400)
        assertNull(Gesamtstimmung.breite200(voll, 1L, mindestens = 400))
        assertNull(Gesamtstimmung.hochTief(voll, 1L, mindestens = 500))
        val b200 = Gesamtstimmung.breite200(voll, 5L, mindestens = 300)!!
        assertEquals(100.0 * (60.0 - 20.0) / (80.0 - 20.0), b200.wert, 1e-9)
        assertEquals(60.0, b200.roh!!, 1e-9)
        assertEquals(5L, b200.stand)
        assertEquals(100.0, Gesamtstimmung.breite200(Gesamtstimmung.Breite(90, 100, 0, 0, 0), 1L)!!.wert, 1e-9)
        // 60 Hochs weniger 20 Tiefs von 400 Werten sind 10 %, fest zwischen -30 und +30
        val ht = Gesamtstimmung.hochTief(voll, 5L, mindestens = 400)!!
        assertEquals(100.0 * (10.0 + 30.0) / 60.0, ht.wert, 1e-9)
        assertEquals(60.0, ht.roh!!, 1e-9)
        assertEquals(20.0, ht.roh2!!, 1e-9)
        assertEquals(5L, ht.stand)
        // nur Tiefs geben die feste Angstgrenze, nur Hochs die Giergrenze
        assertEquals(0.0, Gesamtstimmung.hochTief(Gesamtstimmung.Breite(0, 0, 0, 200, 400), 1L, mindestens = 400)!!.wert, 1e-9)
        assertEquals(100.0, Gesamtstimmung.hochTief(Gesamtstimmung.Breite(0, 0, 200, 0, 400), 1L, mindestens = 400)!!.wert, 1e-9)
        // 5 % Wachstum liegt in der Mitte von -20 und +30, Tage werden zu Millisekunden
        val m = Gesamtstimmung.margin(Ersatzquellen.Margin(20_000L, 900.0, 5.0))
        assertEquals(50.0, m.wert, 1e-9)
        assertEquals(5.0, m.roh!!, 1e-9)
        assertEquals(20_000L * 86_400_000L, m.stand)
    }
}
