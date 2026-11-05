package de.troetenrepublik.app.ui

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Seitenstapel: Seiten oeffnen und schliessen, was gezeichnet wird, Auslauf nach dem Loslassen.
class SeitenstapelTest {

    private class Takt : MonotonicFrameClock {
        var t = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            t += 16_000_000L
            return onFrame(t)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined + Takt())
    private fun stapel() = Seitenstapel(scope, emptyList()).also { it.hoehe = 2000f }

    @Test
    fun sheetsSlideInAndArrowSlidesThemOut() {
        val s = stapel()
        assertTrue(s.leer)
        s.oeffnen("search")
        s.oeffnen("instrument/NVDA")
        assertEquals(listOf("search", "instrument/NVDA"), s.eintraege.map { it.route })
        assertTrue(s.eintraege.all { it.y == 0f && !it.eingang })
        s.schliessen()
        assertEquals(listOf("search"), s.eintraege.map { it.route })
        s.schliessen()
        assertTrue(s.leer)
        assertNull(s.oberste())
    }

    @Test
    fun guideFadesInsteadOfSliding() {
        val s = stapel()
        s.oeffnen("guide")
        val e = s.oberste()!!
        assertFalse(e.blatt)
        assertEquals(1f, e.alpha, 0.001f)
        assertEquals(0f, e.y, 0.001f)
    }

    @Test
    fun onlyTopPageAndTheOneBelowAreDrawn() {
        val s = stapel()
        // Leerer Stapel: die Startseite (Index -1) ist sichtbar
        assertTrue(s.sichtbar(-1))
        s.oeffnen("search")
        assertTrue(s.sichtbar(-1))
        assertTrue(s.sichtbar(0))
        s.oeffnen("instrument/AAPL")
        s.oeffnen("derivative/X")
        // Oberste Seite und die direkt darunter; Startseite und die erste Seite sind ganz verdeckt
        assertFalse(s.sichtbar(-1))
        assertFalse(s.sichtbar(0))
        assertTrue(s.sichtbar(1))
        assertTrue(s.sichtbar(2))
    }

    @Test
    fun releaseGlideStartsAtFingerSpeed() {
        // Schneller Finger: gleichmaessig weiter (Anfangssteigung 1), Dauer = Weg / Geschwindigkeit
        val (ms, easing) = Seitenstapel.auslauf(600f, 3000f)
        assertEquals(200, ms)
        val d = 0.001f
        assertEquals(0.5f, easing.transform(0.5f), 0.01f)
        // Anfangsgeschwindigkeit in Pixeln pro Sekunde: Steigung am Anfang mal Weg durch Dauer
        val steigung = (easing.transform(d) - easing.transform(0f)) / d
        assertEquals(3000f, steigung * 600f / 0.2f, 60f)
    }

    @Test
    fun slowReleaseAcceleratesSmoothlyAndEndsWithinTime() {
        val (ms, easing) = Seitenstapel.auslauf(1500f, 500f)
        assertEquals(320, ms)
        val d = 0.001f
        val anfang = (easing.transform(d) - easing.transform(0f)) / d * 1500f / 0.32f
        val ende = (easing.transform(1f) - easing.transform(1f - d)) / d * 1500f / 0.32f
        // Beginnt mit der Fingergeschwindigkeit und wird gleichmaessig schneller
        assertEquals(500f, anfang, 20f)
        assertTrue(ende > anfang)
        assertEquals(1f, easing.transform(1f), 0.0001f)
        // Ohne Fingergeschwindigkeit: aus dem Stand beschleunigend, ebenfalls hoechstens 320 ms
        assertEquals(320, Seitenstapel.auslauf(1500f, 0f).first)
    }

    @Test
    fun savedStackKeepsPageIds() {
        val s = stapel()
        s.oeffnen("search")
        s.oeffnen("instrument/SAP.DE")
        val ids = s.eintraege.map { it.id }
        val saver = Seitenstapel.saver(scope)
        val gespeichert = with(saver) { SaverScope { true }.save(s) }!!
        val wieder = saver.restore(gespeichert)!!
        assertEquals(ids, wieder.eintraege.map { it.id })
        assertEquals(listOf("search", "instrument/SAP.DE"), wieder.eintraege.map { it.route })
        // Neue Seiten bekommen danach neue Kennungen
        wieder.hoehe = 2000f
        wieder.oeffnen("tax")
        assertTrue(wieder.eintraege.last().id > ids.max())
    }

    @Test
    fun systemBackPreviewFollowsProgressAndCancelReturns() {
        val s = stapel()
        s.oeffnen("activity")
        val e = s.oberste()!!
        s.vorschau(e, 0.5f)
        assertTrue(e.y > 0f && e.y < 2000f)
        s.vorschauAbbrechen(e)
        assertEquals(0f, e.y, 0.01f)
        s.vorschau(e, 0.3f)
        s.schliessen(e)
        assertTrue(s.leer)
    }
}
