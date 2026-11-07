package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols

// Dividenden wiederanlegen (seit 5.92).

// Ob die Dividenden eines Werts wiederangelegt werden koennen: Aktien und ETFs.
@Composable
fun wiederanlageMoeglich(state: AppState, symbol: String? = null): Boolean {
    if (symbol == null) return true
    val typ = Symbols.typeOf(symbol)
    return typ == AssetType.STOCK || typ == AssetType.ETF
}

// Schalter im Sparplan (Anlegen und Verwalten). Ohne eigene Wahl gilt die Einstellung im Profil; jeder
// Tipp setzt eine eigene Wahl fuer diesen Wert.
@Composable
fun WiederanlageZeile(state: AppState, symbol: String) {
    if (!wiederanlageMoeglich(state, symbol)) return
    val account by state.account.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val eigene = account.wiederanlage[symbol]
    ToggleRow(S.wlZeile, eigene ?: settings.divWiederanlage, hint = if (eigene == null) S.wlWieEinstellung else S.wlEigeneWahl) {
        state.wiederanlageSetzen(symbol, it)
    }
}

// Wahl wird nicht gespeichert, das macht der Aufrufer erst beim Anlegen des Sparplans.
@Composable
fun WiederanlageZeile(state: AppState, symbol: String, gewaehlt: Boolean?, onChange: (Boolean) -> Unit) {
    if (!wiederanlageMoeglich(state, symbol)) return
    val account by state.account.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val eigene = account.wiederanlage[symbol]
    // Vorbelegung: gespeicherte eigene Wahl, sonst die Einstellung im Profil
    ToggleRow(S.wlZeile, gewaehlt ?: eigene ?: settings.divWiederanlage,
        hint = if (gewaehlt == null && eigene == null) S.wlWieEinstellung else S.wlEigeneWahl) { onChange(it) }
}
