package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.ohneAbbruch

// [0.78] Blaetter, die sich aus der Lage ueber der Lage oeffnen: Laenderblatt und Thema der Durchbrueche. Schliessen oder
// Zurueck fuehrt wieder in die Lage an dieselbe Stelle; aus dem Blatt geht es nur durch einen ausdruecklichen Tipp weiter.

// Laenderblatt eines Landes (alpha-3) ueber der Lage. Oeffnet ein Tipp im Blatt eine Seite (Aktie, Wirkungsnetz), schliesst
// sich das Blatt und kommt nach dem Zurueck an derselben Stelle wieder (wie im Globus).
@Composable
internal fun LaenderBlattUeberLage(state: AppState, a3: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var karte by remember { mutableStateOf(state.globusKarteBekannt()) }
    var imf by remember { mutableStateOf(state.imfGespeichert()) }
    val staende by state.globusStaende.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (karte == null) karte = ohneAbbruch { state.globusKarte() }.getOrNull()
        ohneAbbruch { state.imfDaten() }.getOrNull()?.let { imf = it }
        ohneAbbruch { state.globusIndizes() }
        ohneAbbruch { state.imfDatenVoll() }.getOrNull()?.let { imf = it }
    }
    val land = karte?.nachA3(a3)
    if (land == null) {
        // Land ohne Umriss auf der Karte: nichts zu zeigen, die Lage bleibt ohne Blatt
        if (karte != null) LaunchedEffect(a3) { onDismiss() }
        return
    }
    val jahr = remember { java.time.LocalDate.now().year }
    val rueck = rememberWiederkehr()
    val halter = rememberSaveableStateHolder()
    val scroll = rememberSaveable(a3, saver = LazyListState.Saver) { LazyListState() }
    val schluessel = "lage-laender-$a3"
    if (!rueck.wartet) halter.SaveableStateProvider(schluessel) {
        LaenderBlatt(state, land, staende, imf, jahr, scroll, rueck.sofort, onOpen = { s -> rueck.verlassen(); onOpen(s) }) {
            rueck.vergessen()
            halter.removeState(schluessel)
            onDismiss()
        }
    }
    BlattBild(rueck, schluessel) { LaenderBlattInhalt(state, land, staende, imf, jahr, scroll, {}) }
}

// Thema der Durchbrueche ueber der Lage: dieselbe Tabelle wie im Reiter Trends (Neu offen), mit Fonds und Weg zur Wirkung
@Composable
internal fun DurchbruchBlattOeffnen(state: AppState, id: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    // Nach dem geoeffneten Papier steht das Blatt wieder da, mit Rollstand und aufgeklapptem Abschnitt
    WiederkehrBlatt("durchbruch-$id") { w -> DurchbruchBlattInnen(state, id, { s -> w.verlassen(); onOpen(s) }, onDismiss) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurchbruchBlattInnen(state: AppState, id: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val thema = Durchbrueche.thema(id) ?: return
    val deutsch = I18n.lang == Lang.DE
    val meldungen = remember { state.durchbruecheStand() }
    var inhalt by remember { mutableStateOf(state.durchbruecheInhaltBekannt()) }
    LaunchedEffect(Unit) { if (inhalt == null) inhalt = ohneAbbruch { state.durchbruecheInhalt() }.getOrNull() }
    val jetzt = remember { System.currentTimeMillis() }
    val block = remember(inhalt) {
        Durchbrueche.ansicht(inhalt, meldungen.orEmpty(), jetzt).firstOrNull { it.thema.id == id } ?: Durchbrueche.Block(thema, emptyList(), null, jetzt)
    }
    var unter by rememberSaveable(id) { mutableStateOf<String?>("n") }
    var artikel by remember { mutableStateOf<MarketApi.NewsItem?>(null) }
    val links = appLinks()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustandHier(),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().rollenMitStand().padding(bottom = 24.dp)) {
            Text(thema.name(deutsch), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            DurchbruchTabelle(
                state, block, jetzt, deutsch, if (meldungen != null) NeuStand.GELADEN else NeuStand.FEHLT, unter, { k -> unter = if (unter == k) null else k },
                { s -> onOpen(s) }, { artikel = it }, { links.openUri(it) }, einzug = PaddingValues(start = 20.dp, end = 20.dp),
            )
        }
    }
    artikel?.let { NewsSheet(it, onDismiss = { artikel = null }) }
}
