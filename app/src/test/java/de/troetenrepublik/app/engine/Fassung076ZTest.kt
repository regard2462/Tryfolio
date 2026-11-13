package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.data.WidgetZiel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Widgets 0.76: Standardwahl der Stimmung, Drehung des Globus, Kachelzahl des Schnellzugriffs.
class Fassung076ZTest {
    @Test fun stimmungStandardIstGesamt() {
        assertEquals(WidgetDaten.STIMMUNG_GESAMT, WidgetWahl.stimmung(null))
        assertEquals(WidgetDaten.STIMMUNG_GESAMT, WidgetWahl.stimmung(""))
        assertEquals(WidgetDaten.STIMMUNG_GESAMT, WidgetWahl.stimmung("unbekannt"))
    }

    @Test fun gespeicherteStimmungBleibt() {
        assertEquals(WidgetDaten.STIMMUNG_AKTIEN, WidgetWahl.stimmung(WidgetDaten.STIMMUNG_AKTIEN))
        assertEquals(WidgetDaten.STIMMUNG_KRYPTO, WidgetWahl.stimmung(WidgetDaten.STIMMUNG_KRYPTO))
        assertEquals(WidgetDaten.STIMMUNG_ALLE, WidgetWahl.stimmung(WidgetDaten.STIMMUNG_ALLE))
        assertEquals(WidgetDaten.STIMMUNG_GESAMT, WidgetWahl.stimmung(WidgetDaten.STIMMUNG_GESAMT))
    }

    @Test fun drehungSpeichernUndWeiterdrehen() {
        assertEquals(0, WidgetWahl.dreh(null))
        assertEquals(0, WidgetWahl.dreh("abc"))
        var d = 0
        d = WidgetWahl.weiterdrehen(d, 1)
        assertEquals(30, d)
        d = WidgetWahl.weiterdrehen(d, 1)
        assertEquals(60, d)
        // Gespeichert und wieder gelesen
        assertEquals(60, WidgetWahl.dreh(d.toString()))
        d = WidgetWahl.weiterdrehen(d, -1)
        assertEquals(30, d)
        d = WidgetWahl.weiterdrehen(d, -1)
        d = WidgetWahl.weiterdrehen(d, -1)
        assertEquals(-30, d)
    }

    @Test fun drehungUmlauf() {
        // Einmal um die Kugel: nach zwoelf Schritten wieder am Anfang
        var d = 0
        repeat(12) { d = WidgetWahl.weiterdrehen(d, 1) }
        assertEquals(0, d)
        var w = 150
        w = WidgetWahl.weiterdrehen(w, 1)
        assertEquals(180, w)
        w = WidgetWahl.weiterdrehen(w, 1)
        assertEquals(-150, w)
        assertEquals(180, WidgetWahl.weiterdrehen(-150, -1))
        // Ungueltige Richtung zaehlt als einfacher Schritt
        assertEquals(30, WidgetWahl.weiterdrehen(0, 5))
    }

    @Test fun laengeAusMitteUndDrehung() {
        assertEquals(12.0, WidgetWahl.laenge(12.0, 0), 1e-9)
        assertEquals(-18.0, WidgetWahl.laenge(12.0, -30), 1e-9)
        assertEquals(42.0, WidgetWahl.laenge(12.0, 30), 1e-9)
        // Asien (95) nach Osten ueber die Datumsgrenze
        assertEquals(-175.0, WidgetWahl.laenge(95.0, 90), 1e-9)
        assertEquals(175.0, WidgetWahl.laenge(-95.0, -90), 1e-9)
        assertTrue(WidgetWahl.laenge(-80.0, -150) in -180.0..180.0)
    }

    @Test fun kachelnJeBreiteUndHoehe() {
        assertEquals(1, WidgetWahl.spalten(110))
        assertEquals(2, WidgetWahl.spalten(180))
        assertEquals(3, WidgetWahl.spalten(200))
        assertEquals(4, WidgetWahl.spalten(300))
        assertEquals(4, WidgetWahl.spalten(900))
        assertEquals(1, WidgetWahl.spalten(0))
        assertEquals(1, WidgetWahl.zeilen(60))
        assertEquals(2, WidgetWahl.zeilen(120))
        // Vier Kacheln in einer Zeile bei breitem Widget, zwei Zeilen bei schmalem und hohem
        assertEquals(4, WidgetWahl.kacheln(300, 70))
        assertEquals(4, WidgetWahl.kacheln(180, 120))
        assertEquals(2, WidgetWahl.kacheln(110, 120))
        assertEquals(1, WidgetWahl.kacheln(110, 70))
    }

    @Test fun landZielUndRaster() {
        assertEquals("land/DE", WidgetZiel.pruefen(WidgetZiel.land("DE")))
        assertEquals(null, WidgetZiel.pruefen("land/de"))
        assertEquals(null, WidgetZiel.pruefen("land/DEU"))
        assertEquals(null, WidgetZiel.pruefen("land/"))
        // Raster liegt zwischen den Raendern und deckt die Hoehe ganz ab
        val (x0, y0) = WidgetWahl.zellenMitte(0, 0)
        val (x1, y1) = WidgetWahl.zellenMitte(WidgetWahl.GLOBUS_SPALTEN - 1, WidgetWahl.GLOBUS_ZEILEN - 1)
        assertTrue(x0 > WidgetWahl.GLOBUS_RAND && x1 < 1 - WidgetWahl.GLOBUS_RAND)
        assertEquals(1.0 - x1, x0, 1e-9)
        assertEquals(1.0 - y1, y0, 1e-9)
    }
}
