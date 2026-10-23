package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.plattform.Plattform

// Mitteilungen bei Copytrading: alle Personen mit Glocke, je mit Schalter, und wie die App neue Meldungen
// erkennt. Die Glocke selbst sitzt oben auf der Seite jeder Person.
@Composable
fun GlockenScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val stand by (state.copy?.stand ?: kotlinx.coroutines.flow.MutableStateFlow(de.troetenrepublik.app.market.CopyRepository.Stand())).collectAsStateWithLifecycle()
    val keys = state.glocken(settings).sorted()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.copyBellsTitle, null, onBack) }
        item { CopyHinweis(if (Plattform.desktop) S.copyBellsIntroDesktop else S.copyBellsIntro) }
        if (!settings.notifications) item { CopyHinweis(S.copyBellNotificationsOff) }
        if (keys.isEmpty()) item { EmptyHint(S.copyBellsEmpty) }
        items(keys, key = { "g-$it" }) { key ->
            val (name, gruppe) = glockenName(state, key, stand.meldungen)
            Row(
                Modifier.fillMaxWidth().clickable { oeffnen(glockenRoute(key)) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonBild(Fotos.von(state, key, stand.meldungen.firstOrNull { "k:" + it.person == key }?.wahlkreis), name, size = 38)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(gruppe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
                GlockenKnopf(state, key, name)
            }
        }
        item { ActionLineWide(S.copyBellsAdd) { oeffnen(CopyRouten.START) } }
    }
}

// Name und Art der Pruefung zu einem Schluessel.
private fun glockenName(state: AppState, key: String, meldungen: List<de.troetenrepublik.app.market.Kongress.Meldung>): Pair<String, String> {
    val rest = key.substringAfter(':')
    return when (key.substringBefore(':')) {
        "k" -> (meldungen.firstOrNull { it.person == rest }?.name ?: rest.split('|').let { "${it.getOrElse(1) { "" }} ${it[0]}".trim() }) to S.copyBellHouse
        "n" -> (state.senat?.meldungen?.filter { it.person == rest }?.maxByOrNull { Politik.tag(it.eingang) }?.name ?: rest.replace('|', ' ')) to S.copyBellSenate
        "r" -> (state.regierung?.personen?.firstOrNull { it.id == rest }?.name ?: rest) to S.copyBellGovernment
        "i" -> (state.investoren.firstOrNull { it.id == rest }?.name ?: rest) to S.copyBellInvestor
        else -> key to ""
    }
}

private fun glockenRoute(key: String): String {
    val rest = key.substringAfter(':')
    return when (key.substringBefore(':')) {
        "k" -> CopyRouten.person(rest)
        "n" -> CopyRouten.senator(rest)
        "r" -> CopyRouten.regierung(rest)
        else -> CopyRouten.investor(rest)
    }
}
