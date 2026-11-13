package de.troetenrepublik.app.plattform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.nestedScroll

// Auf dem Telefon wischt der Finger. Gegenstuecke fuer die Maus: MausScrollen.kt in desktop und web.
// Reste am Rand bleiben in der Zeile (ZeileBehaelt), damit eine Wischseite dahinter nicht mitwandert.
fun Modifier.horizontalScrollMaus(state: ScrollState, senkrechtesRad: Boolean = true): Modifier = composed {
    val behaelt = remember(state) { de.troetenrepublik.app.ui.ZeileBehaelt(state) }
    this.nestedScroll(behaelt).horizontalScroll(state)
}
