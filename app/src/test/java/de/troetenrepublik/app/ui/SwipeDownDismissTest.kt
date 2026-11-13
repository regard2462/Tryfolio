package de.troetenrepublik.app.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Blaetter schliessen sich mit einem Wisch nach unten. Die Geste bewegt die Seite im Seitenstapel selbst,
// Bild fuer Bild aus der Fingerlage.
class SwipeDownDismissTest {

    // Bildtakt fuer Animationen im Test: jedes Bild 16 ms, sofort.
    private class Takt : MonotonicFrameClock {
        var t = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            t += 16_000_000L
            return onFrame(t)
        }
    }

    // Bildtakt, der nie ein Bild liefert: Animationen bleiben am Anfang stehen.
    private class Stillstand : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = suspendCancellableCoroutine { }
    }

    private fun stapel(): Seitenstapel =
        Seitenstapel(CoroutineScope(Dispatchers.Unconfined + Takt()), emptyList()).also { it.hoehe = 2000f }

    // Ohne Stapel (Rueckfall)

    private class Geste {
        var geschlossen = false
        var fertig = 0
        val swipe = SwipeDownDismiss(null) { geschlossen = true }
    }

    @Test
    fun downSwipePastThresholdCloses() {
        val g = Geste()
        g.swipe.drag(300f)
        g.swipe.drag(300f)
        g.swipe.release(0f) { g.fertig++ }
        assertTrue(g.geschlossen)
        assertEquals(1, g.fertig)
    }

    @Test
    fun shortOrUpwardSwipeKeepsPageOpen() {
        val g = Geste()
        // Nach oben bewegt sich nichts
        assertEquals(0f, g.swipe.drag(-200f), 0.001f)
        g.swipe.drag(100f)
        g.swipe.release(-300f) { g.fertig++ }
        assertFalse(g.geschlossen)
        assertEquals(1, g.fertig)
    }

    @Test
    fun fastFlickDownClosesEvenWhenShort() {
        val g = Geste()
        g.swipe.drag(60f)
        g.swipe.release(2500f)
        assertTrue(g.geschlossen)
    }

    @Test
    fun releaseWithoutDragOnlyReportsDone() {
        val g = Geste()
        g.swipe.release(3000f) { g.fertig++ }
        assertFalse(g.geschlossen)
        assertEquals(1, g.fertig)
    }

    // Im Stapel

    @Test
    fun pageFollowsTheFingerPixelForPixel() {
        val s = stapel()
        s.oeffnen("instrument/AAPL")
        val e = s.oberste()!!
        assertEquals(0f, e.y, 0.01f)
        val geste = SwipeDownDismiss(e) {}
        // Jeder Fingerschritt verschiebt die Seite um genau diesen Betrag, ohne Vorlauf und ohne Nachlauf
        var summe = 0f
        for (schritt in listOf(3f, 12f, 40f, 7f, 90f)) {
            assertEquals(schritt, geste.drag(schritt), 0.001f)
            summe += schritt
            assertEquals(summe, e.y, 0.001f)
        }
        // Nach oben nie ueber den Platz hinaus
        assertEquals(-summe, geste.drag(-5000f), 0.001f)
        assertEquals(0f, e.y, 0.001f)
    }

    @Test
    fun releasedFarEnoughTheSheetLeavesTheStack() {
        val s = stapel()
        s.oeffnen("search")
        s.oeffnen("instrument/AAPL")
        val oben = s.oberste()!!
        val geste = SwipeDownDismiss(oben) {}
        geste.drag(600f)
        var fertig = 0
        geste.release(1500f) { fertig++ }
        assertEquals(1, fertig)
        assertEquals(listOf("search"), s.eintraege.map { it.route })
        assertFalse(s.eintraege.any { it === oben })
    }

    @Test
    fun releasedTooEarlyTheSheetReturns() {
        val s = stapel()
        s.oeffnen("tax")
        val e = s.oberste()!!
        val geste = SwipeDownDismiss(e) {}
        geste.drag(200f)
        geste.release(100f)
        assertEquals(0f, e.y, 0.01f)
        assertEquals(1, s.eintraege.size)
    }

    @Test
    fun fingerCatchesAnEnteringSheet() {
        // Ein Finger, der die hineingleitende Seite greift, haelt sie an und bewegt sie von dort aus
        val s = Seitenstapel(CoroutineScope(Dispatchers.Unconfined + Stillstand()), emptyList()).also { it.hoehe = 2000f }
        s.oeffnen("orders")
        val e = s.oberste()!!
        // Die Einblendung wartet auf ihr erstes Bild; die Seite steht noch unten
        assertEquals(2000f, e.y, 0.01f)
        val geste = SwipeDownDismiss(e) {}
        geste.drag(-300f)
        assertEquals(1700f, e.y, 0.01f)
        assertFalse(e.eingang)
    }
}
