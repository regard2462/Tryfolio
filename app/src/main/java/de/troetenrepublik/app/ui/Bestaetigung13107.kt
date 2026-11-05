package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.launch

// Seiten mit Berichten von US-Abgeordneten, Senatoren und Regierungsmitgliedern (seit 5.94): Nach 5 U.S.C. 13107
// duerfen sie nicht kommerziell genutzt werden. Vor dem ersten Abruf bestaetigt der Nutzer das einmal.
internal fun braucht13107(route: String): Boolean =
    route == CopyRouten.START || route == CopyRouten.KONGRESS_KAUFT || route == CopyRouten.RANGLISTE ||
        listOf("copy-k/", "copy-n/", "copy-r/", "copy-o/").any { route.startsWith(it) }

@Composable
internal fun Bestaetigung13107(state: AppState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        ScreenHeader(S.b13107Titel, null, onBack)
        Text(S.b13107Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onBack) { Text(S.b13107Zurueck) }
            TextButton(onClick = { scope.launch { state.bestaetige13107() } }) { Text(S.b13107Ok) }
        }
    }
}
