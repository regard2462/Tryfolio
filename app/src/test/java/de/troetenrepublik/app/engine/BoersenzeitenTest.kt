package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

// Globus Boersenzeiten: Handelsfenster mit Mittagspause, Wochenende, Anzeige in Nutzerzeit, Sonnenstand und Nachtflaeche.
class BoersenzeitenTest {
    private fun at(s: String) = Instant.parse(s)

    @Test
    fun tokioMitMittagspause() {
        // Mittwoch 4.11.2026, 09:30 Ortszeit = 00:30 UTC
        val offen = Boersenzeiten.status("JP", at("2026-11-04T00:30:00Z"))!!
        assertTrue(offen.offen)
        assertEquals(at("2026-11-04T02:30:00Z"), offen.wechsel.toInstant())
        // 12:00 Ortszeit: Pause, naechster Beginn 12:30
        val pause = Boersenzeiten.status("JP", at("2026-11-04T03:00:00Z"))!!
        assertFalse(pause.offen)
        assertEquals(at("2026-11-04T03:30:00Z"), pause.wechsel.toInstant())
    }

    @Test
    fun wochenendeBleibtZu() {
        // Samstag 7.11.2026 12:00 UTC: Frankfurt oeffnet Montag 9:00 (8:00 UTC)
        val s = Boersenzeiten.status("DE", at("2026-11-07T12:00:00Z"))!!
        assertFalse(s.offen)
        assertEquals(at("2026-11-09T08:00:00Z"), s.wechsel.toInstant())
        val text = Boersenzeiten.kurz(s.wechsel, ZoneId.of("Europe/Berlin"), at("2026-11-07T12:00:00Z"), Locale.GERMAN)
        assertTrue(text, text.startsWith("Mo") && text.endsWith("09:00"))
        // am selben Tag nur die Uhrzeit
        val offen = Boersenzeiten.status("DE", at("2026-11-04T10:00:00Z"))!!
        assertTrue(offen.offen)
        assertEquals("17:30", Boersenzeiten.kurz(offen.wechsel, ZoneId.of("Europe/Berlin"), at("2026-11-04T10:00:00Z"), Locale.GERMAN))
    }

    @Test
    fun saudiArabienHandeltSonntagBisDonnerstag() {
        // Sonntag 8.11.2026, 11:00 Ortszeit
        assertEquals(true, Boersenzeiten.offen("SA", at("2026-11-08T08:00:00Z")))
        // Freitag
        assertEquals(false, Boersenzeiten.offen("SA", at("2026-11-06T08:00:00Z")))
        assertNull(Boersenzeiten.status("XX", at("2026-11-06T08:00:00Z")))
    }

    @Test
    fun zeitzonenDerTabelleSindGueltig() {
        for ((land, b) in Boersenzeiten.NACH_LAND) {
            assertNotNull(land, Boersenzeiten.status(land, at("2026-11-06T12:00:00Z")))
            for ((von, bis) in b.sitzungen) assertTrue(land, von.isBefore(bis))
        }
    }

    @Test
    fun sonnenstand() {
        val maerz = Boersenzeiten.sonne(at("2026-03-20T12:00:00Z"))
        assertTrue(abs(maerz.breite) < 1.0)
        assertTrue(abs(maerz.laenge) < 4.0)
        val juni = Boersenzeiten.sonne(at("2026-06-21T12:00:00Z"))
        assertEquals(23.4, juni.breite, 0.4)
        // sechs Stunden spaeter 90 Grad weiter westlich
        val spaeter = Boersenzeiten.sonne(at("2026-06-21T18:00:00Z"))
        assertEquals(-90.0, spaeter.laenge - juni.laenge, 2.0)
    }

    private class Flaeche : GlobusPfad {
        val x = ArrayList<Float>()
        val y = ArrayList<Float>()
        override fun nach(x: Float, y: Float) { this.x += x; this.y += y }
        override fun linie(x: Float, y: Float) { this.x += x; this.y += y }
        override fun schliessen() {}
        fun inhalt(): Double {
            var a = 0.0
            for (i in x.indices) {
                val j = (i + 1) % x.size
                a += x[i].toDouble() * y[j] - x[j].toDouble() * y[i]
            }
            return abs(a) / 2
        }
    }

    @Test
    fun nachtflaecheVollHalbOderLeer() {
        val sonne = Boersenzeiten.Sonne(0.0, 0.0)
        val a = GlobusAnsicht()
        // Blick auf die Nachtmitte (Gegenpunkt der Sonne): ganze Scheibe
        a.setze(180.0, 0.0, 100f, 100f, 100f)
        val voll = Flaeche()
        assertTrue(Boersenzeiten.nacht(sonne, a, voll))
        assertEquals(Math.PI * 100 * 100, voll.inhalt(), 400.0)
        // Blick auf die Sonne: keine Nacht
        a.setze(0.0, 0.0, 100f, 100f, 100f)
        assertFalse(Boersenzeiten.nacht(sonne, a, Flaeche()))
        // Terminator durch die Mitte: halbe Scheibe
        a.setze(90.0, 0.0, 100f, 100f, 100f)
        val halb = Flaeche()
        assertTrue(Boersenzeiten.nacht(sonne, a, halb))
        assertEquals(Math.PI * 100 * 100 / 2, halb.inhalt(), 400.0)
    }
}
