package de.troetenrepublik.app.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Greift der Finger eine Seite im Auslauf und bringt sie zurueck, bleibt sie offen und zaehlt weiter.
class SeitenstapelHaltenTest {

    private class Takt : MonotonicFrameClock {
        var t = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            t += 16_000_000L
            return onFrame(t)
        }
    }

    @Test(timeout = 20_000)
    fun seiteImAuslaufGreifenHeisstOffen() {
        val s = Seitenstapel(CoroutineScope(Dispatchers.Unconfined + Takt()), emptyList()).also { it.hoehe = 2000f }
        s.oeffnen("search")
        val e = s.oberste()!!
        // Auslauf laeuft: die Seite gilt als gehend, die Zurueck-Taste waere gesperrt
        e.geht = true
        assertTrue(s.leer)
        e.halten()
        assertFalse(e.geht)
        assertFalse(s.leer)
        assertEquals(e, s.oberste())
        e.zurueck()
        assertEquals(0f, e.y, 0.001f)
        assertEquals(listOf("search"), s.offen.map { it.route })
    }
}
