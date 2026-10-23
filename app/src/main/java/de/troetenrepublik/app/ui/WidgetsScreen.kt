package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Widgets
import kotlinx.coroutines.launch

// Widgets fuer den Startbildschirm: Erklaerung der Widgets und die Watchlist-Auswahl des Widgets „Werte“.
@Composable
fun WidgetsScreen(state: AppState, onBack: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val liste = rememberScrollState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val gewaehlt = settings.widgetWerte.split(',').map { it.trim() }.filter { it.isNotEmpty() && it in account.watchlist }

    fun umschalten(s: String) {
        val neu = if (s in gewaehlt) gewaehlt - s else gewaehlt + s
        scope.launch { state.settings.update { it.copy(widgetWerte = neu.joinToString(",")) } }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste)) {
        ScreenHeader(S.widgetsTitel, null, onBack)
        AufklappText(if (Widgets.verfuegbar) S.widgetsText else S.widgetsNurAndroid, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        if (Widgets.verfuegbar) {
            Text(
                S.widgetsArten, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            // Hinweis zur Akku-Optimierung mit Sprung in die App-Einstellungen des Systems
            AufklappText(S.widgetsAkku, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Text(
                S.widgetsAkkuOeffnen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                modifier = Modifier.clickable { de.troetenrepublik.app.plattform.Plattform.appEinstellungenOeffnen() }.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SectionHeader(S.widgetsAuswahl)
            if (account.watchlist.isEmpty()) Text(S.widgetLeer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
            account.watchlist.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { umschalten(s) }.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s in gewaehlt, onCheckedChange = { umschalten(s) })
                    NamenText(kurzName(s, quotes[s]?.name.orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
