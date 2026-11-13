package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.i18n.FirmaFaktenTexte

// Gemeinsamer Rahmen der Firmenbausteine Entlassungen, Patente und Emissionen: eingeklappt eine Kopfzeile mit Kernaussage
// und Pfeil, Erklaerung hinter dem Info-Symbol am Titel, aufgeklappt der Inhalt. Der Zustand (auf oder zu) liegt im Merker der
// Seite, damit er das Herausscrollen ueberdauert.

// Zustand eines Abschnitts: aufgeklappt oder nicht
@Composable
internal fun firmaOffen(merker: SeitenMerker?, schluessel: String): MutableState<Boolean> {
    val lokal = rememberSaveable(schluessel) { mutableStateOf(false) }
    return merker?.zustand<Boolean>("kern/$schluessel") { false } ?: lokal
}

@Composable
internal fun FirmaAbschnitt(
    titel: String,
    kern: KernZeile,
    info: String,
    offen: MutableState<Boolean>,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    var auf by offen
    var infoOffen by remember { mutableStateOf(false) }
    KernKopf(titel, kern, auf, gross = true, onInfo = { infoOffen = true }) { auf = !auf }
    if (auf) Column { inhalt() }
    if (infoOffen) MarktInfoBlatt(titel, info) { infoOffen = false }
}

// Text eines Firmenbausteins in der Sprache der App
internal fun firmaText(key: String, vararg w: String): String = FirmaFaktenTexte.t(key, *w)
