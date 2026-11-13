package de.troetenrepublik.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.RegelGlocke
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.i18n.S

// Wert, fuer den der Strategietest gerade offen ist; der Glocken-Knopf an der gewaehlten Regel liest ihn von hier.
internal class RegelGlockeZiel(val state: AppState, val symbol: String, val name: String)

internal val LocalRegelGlocke = compositionLocalOf<RegelGlockeZiel?> { null }

// Glocke auf die gewaehlte Regel fuer diesen Wert (Kauf- und Verkaufssignal am Tagesschluss). Fehlt bei Kaufen und
// Halten; abschalten laesst sie sich auch im Glocken-Blatt des Werts.
@Composable
internal fun RegelGlockeKnopf(regel: StrategieLabor.Regel) {
    val ziel = LocalRegelGlocke.current ?: return
    if (!RegelGlocke.moeglich(regel)) return
    val konto by ziel.state.account.collectAsStateWithLifecycle()
    val art = RegelGlocke.art(regel)
    val an = konto.alerts.any { it.symbol == ziel.symbol && it.art == art }
    IconButton(onClick = {
        ziel.state.wertArtenSetzen(ziel.symbol, ziel.name, listOf(art), !an)
        if (!an) ziel.state.showToast(S.st("rg.gesetzt"))
    }) {
        Icon(
            if (an) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
            if (an) S.glockeAus else S.st("rg.setzen"),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
