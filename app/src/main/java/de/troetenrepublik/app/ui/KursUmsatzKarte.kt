package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.KursUmsatz
import de.troetenrepublik.app.engine.KursUmsatz.Zone
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.kursUmsatz

// [0.78] Kurs und Umsatz nach Kostolany im Reiter Stimmung: je Index eine Zeile mit der Zone; ein Tipp klappt die
// Messwerte auf. Rechnung in engine/KursUmsatz.kt. Ohne Umsatzdaten erscheint der Abschnitt nicht.
@Composable
internal fun KursUmsatzInhalt(state: AppState) {
    val liste by produceState<List<Pair<String, KursUmsatz.Ergebnis>>>(emptyList()) { value = runCatching { state.kursUmsatz() }.getOrNull().orEmpty() }
    if (liste.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.ml("ku.titel"), S.ml("ku.info"))
        for ((name, e) in liste) KursUmsatzZeile(name, e)
    }
}

@Composable
private fun KursUmsatzZeile(name: String, e: KursUmsatz.Ergebnis) {
    val signal = LocalSignal.current
    var offen by rememberSaveable(name) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen }.heightIn(min = 48.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            S.ml("ku.zone." + e.zone.name), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false,
            color = when (e.zone) {
                Zone.KAUFZONE, Zone.FRUEHER_AUFSCHWUNG -> signal.up
                Zone.VERKAUFSZONE, Zone.FRUEHER_ABSCHWUNG -> signal.down
                Zone.KEINE -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(8.dp))
        AufklappPfeil(offen)
    }
    if (offen) {
        KeyValueRow(S.ml("ku.kurs"), Fmt.signedPct(e.kurs, 1), if (e.kurs >= 0) signal.up else signal.down)
        KeyValueRow(S.ml("ku.kursLang"), Fmt.signedPct(e.kursLang, 1), if (e.kursLang >= 0) signal.up else signal.down)
        KeyValueRow(S.ml("ku.umsatz"), Fmt.signedPct(e.umsatz, 0))
        KeyValueRow(S.ml("ku.stand"), Fmt.date(e.tag))
    }
}
