package de.troetenrepublik.app.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.ui.MitlaufendeReiter
import de.troetenrepublik.app.ui.WischZeile
import de.troetenrepublik.app.ui.Wischwahl
import de.troetenrepublik.app.ui.rememberWischwahl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsNotDisplayed
import org.junit.Test

// Seiten zum Wischen ohne Pager: Wischen und Pillen wechseln die Seite aller Zeilen gemeinsam, senkrecht scrollt
// die Liste, und an der letzten Seite geht die Bewegung an den umgebenden Pager.
@OptIn(ExperimentalTestApi::class)
class WischwahlTest {

    @Test
    fun wischenPillenUndRand() = runDesktopComposeUiTest(width = 420, height = 800) {
        lateinit var wahl: Wischwahl
        lateinit var liste: LazyListState
        setContent {
            val aussen = rememberPagerState { 2 }
            HorizontalPager(aussen, Modifier.fillMaxSize()) { a ->
                if (a == 1) {
                    Text("Aussen 1")
                    return@HorizontalPager
                }
                wahl = rememberWischwahl(3)
                liste = remember { LazyListState() }
                LazyColumn(Modifier.fillMaxSize(), state = liste) {
                    item { MitlaufendeReiter(3, stand = { wahl.stand() }, titel = { "Seite $it" }) { wahl.zu(it) } }
                    items(40) { k ->
                        WischZeile(wahl, Modifier.testTag("z$k")) { s ->
                            Column { Text("S$s-Z$k", Modifier.fillMaxWidth().height(48.dp)) }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("S0-Z0").assertExists()

        // Nach links wischen: naechste Seite in allen Zeilen
        onNodeWithTag("z1").performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(1, wahl.seite)
        onNodeWithText("S1-Z0").assertExists()
        onNodeWithText("S1-Z3").assertExists()
        // Die vorherige Seite bleibt aufgebaut, liegt aber ausserhalb des Bilds
        onNodeWithText("S0-Z0").assertIsNotDisplayed()

        // Zurueck nach rechts, dann per Pille zur letzten Seite
        onNodeWithTag("z2").performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals(0, wahl.seite)
        onNodeWithText("Seite 2").performClick()
        waitForIdle()
        assertEquals(2, wahl.seite)
        onNodeWithText("S2-Z0").assertExists()

        // Senkrecht scrollt die Liste
        onNodeWithTag("z2").performTouchInput { swipeUp() }
        waitForIdle()
        assertTrue("Liste gescrollt", liste.firstVisibleItemIndex > 0)
        assertEquals(2, wahl.seite)

        // An der letzten Seite gehoert ein Wisch nach links dem umgebenden Pager
        onNodeWithText("S2-Z${liste.firstVisibleItemIndex + 1}").performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithText("Aussen 1").assertExists()
    }

    // Zweimal schnell nach links, waehrend die erste Bewegung noch gleitet: weiter zur uebernaechsten Seite.
    @Test
    fun schnellesZweitesWischen() = runDesktopComposeUiTest(width = 420, height = 800) {
        lateinit var wahl: Wischwahl
        setContent {
            wahl = rememberWischwahl(3)
            LazyColumn(Modifier.fillMaxSize()) {
                items(10) { k -> WischZeile(wahl, Modifier.testTag("z$k")) { s -> Text("S$s-Z$k", Modifier.fillMaxWidth().height(48.dp)) } }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag("z1").performTouchInput { swipeLeft(durationMillis = 120) }
        mainClock.advanceTimeBy(80)
        val mitten = wahl.stand()
        assertTrue("erste Bewegung gleitet noch ($mitten)", mitten > 0f && mitten < 1.2f)
        onNodeWithTag("z1").performTouchInput { swipeLeft(durationMillis = 120) }
        // Kein Ruecksprung waehrend des zweiten Wischens, der Stand waechst nur
        var vorher = wahl.stand()
        repeat(40) {
            mainClock.advanceTimeBy(16)
            val jetzt = wahl.stand()
            assertTrue("Stand springt nicht zurueck ($vorher -> $jetzt)", jetzt >= vorher - 0.02f)
            vorher = jetzt
        }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(2, wahl.seite)
        onNodeWithText("S2-Z0").assertExists()
    }

    // Pager im Pager wie die Heatmap im Watchlist-Blatt: innen wird geblaettert, am ersten Blatt innen nimmt der aeussere.
    @Test
    fun pagerImPager() = runDesktopComposeUiTest(width = 420, height = 800) {
        lateinit var aussen: androidx.compose.foundation.pager.PagerState
        lateinit var innen: androidx.compose.foundation.pager.PagerState
        setContent {
            aussen = rememberPagerState(initialPage = 2) { 3 }
            HorizontalPager(aussen, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { a ->
                if (a != 2) { Text("Aussen $a"); return@HorizontalPager }
                innen = rememberPagerState { 3 }
                HorizontalPager(innen, Modifier.fillMaxSize().testTag("innen"), beyondViewportPageCount = 1) { i -> Text("Innen $i", Modifier.fillMaxSize()) }
            }
        }
        waitForIdle()
        onNodeWithTag("innen").performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(1, innen.currentPage)
        assertEquals(2, aussen.currentPage)
        onNodeWithTag("innen").performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals(0, innen.currentPage)
        onNodeWithTag("innen").performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals("am Rand blaettert das Blatt", 1, aussen.currentPage)
    }
}
