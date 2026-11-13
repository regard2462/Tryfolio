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
import androidx.compose.runtime.remember
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
import de.troetenrepublik.app.wirkungsBestand
import de.troetenrepublik.app.finderAlarmAus
import de.troetenrepublik.app.finderAlarmeLesen
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
fun MitteilungenScreen(state: AppState, onBack: () -> Unit, zuGlocken: Boolean = false, oeffnen: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val account by state.account.collectAsStateWithLifecycle()
    val keinStand = remember { kotlinx.coroutines.flow.MutableStateFlow(de.troetenrepublik.app.market.CopyRepository.Stand()) }
    val stand by (state.copy?.stand ?: keinStand).collectAsStateWithLifecycle()
    val personen = remember(settings.copyGlocke) { state.glocken(settings).sorted() }
    var wertBlatt by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Alarme des Aktienfinders (Vorlage und Markt), in der Liste „Glocken“ mit abschaltbar
    var finder by remember { mutableStateOf<List<de.troetenrepublik.app.FinderAlarm>>(emptyList()) }
    LaunchedEffect(Unit) { finder = state.finderAlarmeLesen().filter { de.troetenrepublik.app.engine.FinderVorlagen.vorlage(it.vorlage) != null } }
    val arten = remember { Mitteilungsarten.arten(Plattform.aktualisierbar) }
    // Stelle der Ueberschrift „Glocken“: Kopf, Hauptschalter, die Arten
    val glockenStelle = Mitteilungsarten.glockenStelle(arten.size)
    LaunchedEffect(zuGlocken) { if (zuGlocken) liste.scrollToItem(glockenStelle) }

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
        arten.forEach { art ->
            item(key = "art-${art.name}") { ArtZeile(state, art, oeffnen, scope, personen.size) { scope.launch { liste.animateScrollToItem(glockenStelle) } } }
        }
        glockenListe(
            state, account.alerts, personen, stand, oeffnen, finder,
            finderAus = { a -> scope.launch { finder = state.finderAlarmAus(a.universum, a.vorlage).filter { de.troetenrepublik.app.engine.FinderVorlagen.vorlage(it.vorlage) != null } } },
        ) { symbol, name -> wertBlatt = symbol to name }
        item { Spacer(Modifier.height(40.dp)) }
    }
    wertBlatt?.let { (symbol, name) -> AlertsSheet(state, symbol, name, onDismiss = { wertBlatt = null }) }
}

// Eine Art: Name mit Pfeil (Tipp klappt auf) und Schalter; darunter Erklaerung und Unterpunkte.
@Composable
private fun ArtZeile(state: AppState, art: Mitteilungsarten.Art, oeffnen: (String) -> Unit, scope: CoroutineScope, personen: Int, zuGlocken: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var offen by rememberSaveable(art.name) { mutableStateOf(false) }
    val an = Mitteilungsarten.an(settings, art)
    val titel = when (art) {
        Mitteilungsarten.Art.DEPOT -> S.mtDepot
        Mitteilungsarten.Art.ALARME -> S.mtAlarme
        Mitteilungsarten.Art.RUECKBLICK -> S.rbTitel
        Mitteilungsarten.Art.NEWS -> S.news
        Mitteilungsarten.Art.COPY -> S.mtCopy
        Mitteilungsarten.Art.WELT -> S.mtWelt
        Mitteilungsarten.Art.UPDATES -> S.channelUpdates
    }
    val abstand = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ohneInhalt = art == Mitteilungsarten.Art.UPDATES
        Row(
            Modifier.weight(1f).then(if (ohneInhalt) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more) { offen = !offen }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!ohneInhalt) { AufklappPfeil(offen); Spacer(Modifier.width(10.dp)) }
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
            Mitteilungsarten.Art.UPDATES -> Unit
            Mitteilungsarten.Art.DEPOT -> AufklappText(S.mtDepotHinweis, abstand)
            Mitteilungsarten.Art.ALARME -> AufklappText(S.mtAlarmeHinweis, abstand)
            Mitteilungsarten.Art.RUECKBLICK -> {
                val rueckblicke by state.rueckblicke.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { state.rueckblickeLaden() }
                AufklappText(S.rbMitteilungHinweis, abstand)
                // Schalter und Uhrzeit stehen auf der Seite des Rueckblicks
                NavRow(S.rbTitel, S.rbProfil(rueckblicke?.tage?.size ?: 0, settings.tagesrueckblick)) { oeffnen("rueckblick") }
            }
            Mitteilungsarten.Art.NEWS -> {
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
                NewsMeldeAuswahl(state, settings)
            }
            // Die Glocken zu Personen und Insidern stehen in der Liste „Glocken“ weiter unten
            Mitteilungsarten.Art.COPY -> NavRow(S.glockeListe, S.copyBellsSubtitle(personen)) { zuGlocken() }
            Mitteilungsarten.Art.WELT -> {
                AufklappText(S.mtWeltHinweis, abstand)
                // [0.75] Werte im Depot, fuer die nur abgelaufene Einordnungen bestehen: keine Mitteilung mehr, das steht hier
                var alt by remember { mutableStateOf<Pair<Int, Long>?>(null) }
                LaunchedEffect(Unit) {
                    alt = try {
                        val depot = state.wirkungsBestand().first
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            de.troetenrepublik.app.engine.WeltMeldung.ausgeblendet(
                                de.troetenrepublik.app.engine.Einflussverzeichnis.geladen, depot, java.time.LocalDate.now().toEpochDay(),
                            )
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
                }
                alt?.let { (n, bis) -> WeltZeile(S.abhAbgelaufen, n.toString(), listOf(S.abhAbgelaufenHinweis(Fmt.date(bis * 86_400_000L)))) }
            }
        }
    }
}
