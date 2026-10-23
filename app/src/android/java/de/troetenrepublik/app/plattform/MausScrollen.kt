package de.troetenrepublik.app.plattform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Modifier

// Auf dem Telefon wischt der Finger. Gegenstuecke fuer die Maus: MausScrollen.kt in desktop und web.
fun Modifier.horizontalScrollMaus(state: ScrollState): Modifier = horizontalScroll(state)
