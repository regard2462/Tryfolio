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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.MITTEILUNG_ALLE
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE
import de.troetenrepublik.app.data.MITTEILUNG_QUELLEN
import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.engine.Mitteilungsarten
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val MITTEILUNGEN = "mitteilungen"

// Mitteilungen nach Arten, je Art ein Schalter; ein Tipp auf den Namen zeigt Erklaerung und Unterpunkte.
@Composable
fun MitteilungenScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.notifications, null, onBack) }
        item {
            ToggleRow(S.mtAlle, settings.notifications) { v -> scope.launch { state.settings.update { it.copy(notifications = v) } } }
            if (!settings.notifications) Text(
                S.mtAlleAus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        Mitteilungsarten.arten(Plattform.aktualisierbar).forEach { art ->
            item(key = "art-${art.name}") { ArtZeile(state, art, oeffnen, scope) }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Eine Art: Name mit Pfeil (Tipp klappt auf) und Schalter; darunter Erklaerung und Unterpunkte.
@Composable
private fun ArtZeile(state: AppState, art: Mitteilungsarten.Art, oeffnen: (String) -> Unit, scope: CoroutineScope) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    var offen by rememberSaveable(art.name) { mutableStateOf(false) }
    val an = Mitteilungsarten.an(settings, art)
    val titel = when (art) {
        Mitteilungsarten.Art.DEPOT -> S.mtDepot
        Mitteilungsarten.Art.ALARME -> S.mtAlarme
        Mitteilungsarten.Art.RUECKBLICK -> S.rbTitel
        Mitteilungsarten.Art.NEWS -> S.news
        Mitteilungsarten.Art.COPY -> S.mtCopy
        Mitteilungsarten.Art.UPDATES -> S.channelUpdates
    }
    val abstand = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more) { offen = !offen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AufklappPfeil(offen)
            Spacer(Modifier.width(10.dp))
            Text(titel, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = an,
            onCheckedChange = { v -> scope.launch { state.settings.update { Mitteilungsarten.setzen(it, art, v) } } },
            // Farben wie ToggleRow
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
    if (!offen) return
    Column(Modifier.fillMaxWidth()) {
        when (art) {
            Mitteilungsarten.Art.DEPOT -> AufklappText(S.mtDepotHinweis, abstand)
            Mitteilungsarten.Art.ALARME -> {
                AufklappText(S.mtAlarmeHinweis, abstand)
                NavRow(S.priceAlerts, S.nActive(account.alerts.size)) { oeffnen("alerts") }
            }
            Mitteilungsarten.Art.RUECKBLICK -> {
                val rueckblicke by state.rueckblicke.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { state.rueckblickeLaden() }
                AufklappText(S.rbMitteilungHinweis, abstand)
                ToggleRow(S.rbSchalter, settings.tagesrueckblick) { v -> scope.launch { state.settings.update { it.copy(tagesrueckblick = v) } } }
                if (settings.tagesrueckblick) RueckblickUhrzeit(state)
                NavRow(S.rbTitel, S.rbProfil(rueckblicke?.tage?.size ?: 0, settings.tagesrueckblick)) { oeffnen("rueckblick") }
            }
            Mitteilungsarten.Art.NEWS -> {
                AufklappText(S.mtNewsHinweis, abstand)
                Column(Modifier.padding(horizontal = 8.dp)) {
                    listOf(MITTEILUNG_WICHTIGE, MITTEILUNG_EIGENE_WICHTIGE, MITTEILUNG_EIGENE, MITTEILUNG_ALLE, MITTEILUNG_QUELLEN).forEach { stufe ->
                        val neu = stufe == MITTEILUNG_EIGENE || stufe == MITTEILUNG_EIGENE_WICHTIGE || stufe == MITTEILUNG_QUELLEN
                        OptionRow(
                            if (neu) S.feedMeldeOption(stufe) else S.newsNotifyOption(stufe),
                            if (neu) S.feedMeldeOptionHint(stufe) else S.newsNotifyOptionHint(stufe),
                            settings.newsMitteilung == stufe,
                        ) { state.newsMitteilungSetzen(stufe) }
                    }
                }
            }
            Mitteilungsarten.Art.COPY -> {
                AufklappText(S.mtCopyHinweis, abstand)
                NavRow(S.copyBellsTitle, S.copyBellsSubtitle(state.glocken().size)) { oeffnen(CopyRouten.GLOCKEN) }
            }
            Mitteilungsarten.Art.UPDATES -> AufklappText(S.updSwitchHint, abstand)
        }
    }
}
