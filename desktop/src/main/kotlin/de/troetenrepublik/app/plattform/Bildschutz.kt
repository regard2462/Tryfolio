package de.troetenrepublik.app.plattform

import androidx.compose.runtime.Composable

// Die Einstellung „Screenshots“ gibt es nur unter Android.
val bildschutzMoeglich: Boolean = false

@Composable
fun Bildschutz(aktiv: Boolean) {}

// Unter Android: kein Bild in der App-Uebersicht, solange aktiv. Hier ohne Wirkung.
@Composable
fun VorschauSchutz(aktiv: Boolean) {}
