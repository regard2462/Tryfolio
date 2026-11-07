package de.troetenrepublik.app.plattform

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier

// Auf dem Telefon wischt der Finger, das kann der Pager selbst. Die Mausvariante liegt im Desktop-Modul.
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun Modifier.mausWischen(pager: PagerState): Modifier = this
