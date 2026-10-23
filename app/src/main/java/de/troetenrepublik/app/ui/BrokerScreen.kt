package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.broker.BrokerArt
import de.troetenrepublik.app.plattform.Tresor
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.pngBild
import androidx.compose.foundation.rememberScrollState
import de.troetenrepublik.app.plattform.rememberSpeichern
import kotlinx.coroutines.launch

// Eigenen Broker verbinden in vier Schritten: Broker waehlen, Hinweise und Haftung bestaetigen, Uebungsdepot sichern,
// da es ersetzt wird, Schluessel eingeben. Mit Verbindung: Stand, Abgleich und Trennen.
@Composable
fun BrokerScreen(state: AppState, onBack: () -> Unit) {
    val zustand by state.brokerZustand.collectAsStateWithLifecycle()
    val freigabe by state.brokerFreigabe.collectAsStateWithLifecycle()
    val ohneSchluessel by state.brokerOhneSchluessel.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    // Eingerichtet, aber Schluessel nicht lesbar: neu eingeben oder trennen
    val eingerichtet = BrokerArt.entries.firstOrNull { it.name == settings.broker }
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    var art by remember { mutableStateOf<BrokerArt?>(null) }
    var live by remember { mutableStateOf(false) }
    var schritt by remember { mutableStateOf(0) }
    var gelesen by remember { mutableStateOf(false) }
    var echtesGeld by remember { mutableStateOf(false) }
    var gesichert by remember { mutableStateOf<String?>(null) }
    val felder = remember { mutableStateMapOf<String, String>() }
    var zeigen by remember { mutableStateOf(false) }
    var laeuft by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var trennenFragen by remember { mutableStateOf(false) }
    // Fingerabdruck des IBKR-Gateway-Zertifikats, vor dem Verbinden zu bestaetigen
    var zertifikatFrage by remember { mutableStateOf<String?>(null) }
    // Schluessel stehen hier im Klartext: keine Screenshots und keine Vorschau in der App-Uebersicht,
    // ausser sie sind in den Einstellungen ueberall erlaubt
    de.troetenrepublik.app.plattform.Bildschutz(settings.screenshots != de.troetenrepublik.app.data.Bildschirmfotos.ERLAUBT)
    val sichern = rememberSpeichern(inhalt = { state.exportJson() }) { text, ok ->
        gesichert = if (ok) S.exportDone(text.length / 1024 + 1) else S.exportFailed
    }

    fun zurueck() { if (schritt > 0 && !zustand.verbunden) schritt-- else onBack() }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward },
        state = liste,
    ) {
        item { ScreenHeader(S.brTitel, null, ::zurueck) }

        if (!Tresor.verfuegbar) {
            item { Absatz(S.brNurApp) }
            return@LazyColumn
        }

        // TAN-Freigabe der Sitzung bei comdirect: vor dem ersten Verbinden nur sie, sonst ueber dem Stand des Kontos
        if (freigabe.noetig && freigabe.art != null) {
            item { FreigabeBlock(state, freigabe, verbunden = zustand.verbunden) }
            if (!zustand.verbunden) {
                item { Spacer(Modifier.height(40.dp)) }
                return@LazyColumn
            }
        }

        val verbunden = zustand.art
        if (verbunden != null) {
            item {
                SectionHeader(verbunden.anzeige)
                KeyValueRow(S.brKontoArt, if (zustand.live) S.brLive else S.brPapier, if (zustand.live) LocalSignal.current.down else null)
                if (zustand.konto.isNotBlank()) KeyValueRow(S.brKonto, zustand.konto)
                if (zustand.waehrung.isNotBlank()) KeyValueRow(S.brKaufkraft, Fmt.num(zustand.kaufkraft, 2) + " " + zustand.waehrung)
                KeyValueRow(S.brZuletzt, if (zustand.zuletzt > 0) Fmt.dateTime(zustand.zuletzt) else "–")
                zustand.fehler?.let { Absatz(it, fehlerFarbe = true) }
                Absatz(S.brVerbundenText(verbunden.anzeige))
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterPill(S.brAbgleichen) { scope.launch { state.brokerAbgleich() } }
                    FilterPill(S.brTrennen) { trennenFragen = true }
                }
            }
            item { SchutzListe() }
            item { Spacer(Modifier.height(40.dp)) }
            return@LazyColumn
        }

        when (schritt) {
            // 1. Broker waehlen
            0 -> {
                if (ohneSchluessel && eingerichtet != null) item {
                    SectionHeader(eingerichtet.anzeige)
                    Absatz(S.brokerSchluesselFehlen, fehlerFarbe = true)
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        FilterPill(S.brTrennen) { trennenFragen = true }
                    }
                }
                item { Absatz(S.brEinleitung) }
                item { SchutzListe() }
                item { SectionHeader(S.brWaehlen) }
                BrokerArt.entries.forEach { a ->
                    item(key = a.name) {
                        OptionRow(a.anzeige, S.brAngebot(a), art == a, modifier = Modifier.padding(horizontal = 8.dp)) { art = a; live = false; felder.clear(); fehler = null }
                    }
                }
                item { Absatz(S.brNichtMoeglich) }
                item {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) {
                        FloatingPill(S.brWeiter, enabled = art != null) { schritt = 1; gelesen = false; echtesGeld = false }
                    }
                }
            }
            // 2. Hinweise und Haftung
            1 -> {
                val a = art ?: return@LazyColumn
                item { SectionHeader(S.brHinweiseTitel) }
                S.brHinweise(a.anzeige).forEach { (titel, text) ->
                    item {
                        Text(titel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 2.dp))
                        Absatz(text, oben = 0)
                    }
                }
                if (a.papier) item {
                    SectionHeader(S.brKontoArt)
                    OptionRow(S.brPapier, S.brPapierHint, !live, modifier = Modifier.padding(horizontal = 8.dp)) { live = false; echtesGeld = false }
                    OptionRow(S.brLive, S.brLiveHint, live, modifier = Modifier.padding(horizontal = 8.dp)) { live = true }
                }
                item {
                    Haken(S.brGelesen, gelesen) { gelesen = it }
                    if (live || !a.papier) Haken(S.brEchtesGeld, echtesGeld) { echtesGeld = it }
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) {
                        FloatingPill(S.brZustimmen, enabled = gelesen && (echtesGeld || (a.papier && !live))) { schritt = 2 }
                    }
                }
            }
            // 3. Uebungsdepot sichern
            2 -> {
                val a = art ?: return@LazyColumn
                item {
                    SectionHeader(S.brSichernTitel)
                    Absatz(S.brSichernText(a.anzeige))
                    NavRow(S.exportPortfolio, gesichert ?: S.exportSubtitle) { sichern(Backup.fileName()) }
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) {
                        FloatingPill(if (gesichert != null) S.brWeiter else S.brOhneSichern, filled = gesichert != null) { schritt = 3 }
                    }
                }
            }
            // 4. Schluessel
            else -> {
                val a = art ?: return@LazyColumn
                item {
                    SectionHeader(S.brSchluesselTitel(a.anzeige))
                    Absatz(S.brAnleitung(a, live))
                    Text(
                        S.brSchluesselHolen(a.anzeige), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { uri.openUri(a.schluesselSeite) } }.padding(8.dp),
                    )
                    if (a == BrokerArt.IBKR) {
                        val ziel = de.troetenrepublik.app.market.broker.IbkrBroker.adresse(felder["adresse"].orEmpty())
                        if (ziel != null) Text(
                            S.ibGatewayOeffnen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                            modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { uri.openUri(ziel) } }.padding(8.dp),
                        )
                    }
                }
                a.felder.forEach { f ->
                    item(key = "feld-$f") {
                        OutlinedTextField(
                            value = felder[f].orEmpty(),
                            onValueChange = { felder[f] = it.trim().take(4000); fehler = null },
                            label = { Text(S.brFeld(f)) },
                            singleLine = true,
                            // Passwort-Tastatur ohne Vorschlaege, damit die Tastatur den Schluessel nicht lernt
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            visualTransformation = if (zeigen) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { TextButton(onClick = { zeigen = !zeigen }) { Text(if (zeigen) S.zgHide else S.zgShow) } },
                            shape = RoundedCornerShape(Radius.field),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
                item {
                    Absatz(S.brSchluesselSchutz(Tresor.schutz))
                    fehler?.let { Absatz(it, fehlerFarbe = true) }
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (laeuft) CircularProgressIndicator(Modifier.padding(end = 16.dp))
                        FloatingPill(S.brVerbinden, enabled = !laeuft && a.felder.all { !felder[it].isNullOrBlank() }) {
                            laeuft = true
                            fehler = null
                            scope.launch {
                                if (a == BrokerArt.IBKR) {
                                    // Erst das Zertifikat des Gateways zeigen, verbunden wird nach der Bestaetigung
                                    val fp = state.gatewayZertifikat(felder["adresse"].orEmpty())
                                    laeuft = false
                                    if (fp == null) fehler = S.ibNichtErreichbar else zertifikatFrage = fp
                                    return@launch
                                }
                                fehler = state.brokerVerbinden(a, live || !a.papier, felder.toMap())
                                laeuft = false
                                if (fehler == null) { felder.clear(); schritt = 0 }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    zertifikatFrage?.let { fp ->
        val a = art
        AlertDialog(
            onDismissRequest = { zertifikatFrage = null },
            title = { Text(S.ibZertTitel) },
            text = { Text(S.ibZertText(fp)) },
            confirmButton = {
                TextButton(onClick = {
                    zertifikatFrage = null
                    if (a == null) return@TextButton
                    laeuft = true
                    scope.launch {
                        fehler = state.brokerVerbinden(a, live || !a.papier, felder.toMap() + ("zertifikat" to fp))
                        laeuft = false
                        if (fehler == null) { felder.clear(); schritt = 0 }
                    }
                }) { Text(S.ibZertJa) }
            },
            dismissButton = { TextButton(onClick = { zertifikatFrage = null }) { Text(S.cancel) } },
        )
    }

    if (trennenFragen) {
        AlertDialog(
            onDismissRequest = { trennenFragen = false },
            title = { Text(S.brTrennenTitel) },
            text = { Text(S.brTrennenText((zustand.art ?: eingerichtet)?.anzeige.orEmpty())) },
            confirmButton = { TextButton(onClick = { trennenFragen = false; state.brokerTrennen() }) { Text(S.brTrennen) } },
            dismissButton = { TextButton(onClick = { trennenFragen = false }) { Text(S.cancel) } },
        )
    }
}

@Composable
private fun Absatz(text: String, fehlerFarbe: Boolean = false, oben: Int = 6) {
    AufklappText(
        text, Modifier.padding(horizontal = 20.dp).padding(top = oben.dp, bottom = 6.dp),
        color = if (fehlerFarbe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Haken(text: String, an: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!an) }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = an, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
    }
}

// Freigabe der Sitzung mit TAN: Verfahren waehlen, TAN nur per Knopf anfordern, dann in der photoTAN-App freigeben
// oder die TAN eingeben. Mit Zaehler und Warnung vor der Sperre des Onlinebankings.
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
@Composable
private fun FreigabeBlock(state: AppState, f: AppState.FreigabeZustand, verbunden: Boolean) {
    val name = f.art?.anzeige.orEmpty()
    var verfahren by remember { mutableStateOf<String?>(null) }
    var tan by remember { mutableStateOf("") }
    val grenze = f.angefordert >= 3
    SectionHeader(S.frTitel)
    Absatz(S.frText(name))
    Absatz(S.frWarnung)
    val c = f.challenge
    if (c == null) {
        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf<String?>(null, "P_TAN_PUSH", "P_TAN", "M_TAN").forEach { v -> FilterPill(S.frVerfahren(v), active = verfahren == v) { verfahren = v } }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (f.laeuft) CircularProgressIndicator(Modifier.padding(end = 16.dp))
            FloatingPill(S.frAnfordern, enabled = !f.laeuft && !grenze) { state.freigabeAnfordern(verfahren) }
        }
    } else {
        when (c.verfahren) {
            "P_TAN_PUSH" -> Absatz(S.frPush)
            "P_TAN" -> {
                val bild = remember(c.bild) { c.bild?.let { b -> runCatching { pngBild(kotlin.io.encoding.Base64.decode(b.trim())) }.getOrNull() } }
                if (bild != null) androidx.compose.foundation.Image(
                    bild, contentDescription = S.frPhoto,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(220.dp),
                )
                Absatz(S.frPhoto)
            }
            else -> Absatz(S.frSms(c.text))
        }
        if (c.verfahren != "P_TAN_PUSH") {
            OutlinedTextField(
                value = tan, onValueChange = { tan = it.filter { ch -> ch.isDigit() }.take(8) },
                label = { Text(S.frTan) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (f.laeuft) CircularProgressIndicator(Modifier.padding(end = 6.dp))
            FilterPill(S.frNeuAnfordern) { if (!f.laeuft && !grenze) { tan = ""; state.freigabeAnfordern(verfahren) } }
            FloatingPill(
                if (c.verfahren == "P_TAN_PUSH") S.frPushBestaetigt else S.frEinloesen,
                enabled = !f.laeuft && (c.verfahren == "P_TAN_PUSH" || tan.length >= 6),
            ) { state.freigabeBestaetigen(tan.takeIf { c.verfahren != "P_TAN_PUSH" }); tan = "" }
        }
    }
    Absatz(S.frZaehler(f.angefordert, f.falsch))
    f.fehler?.let { Absatz(it, fehlerFarbe = true) }
    if (!verbunden) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        FilterPill(S.cancel) { state.freigabeAbbrechen() }
    }
}

@Composable
private fun SchutzListe() {
    var offen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.brSchutzTitel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        ChevronDown(width = 11.dp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(if (offen) 180f else 0f))
    }
    if (offen) S.brSchutzListe.forEach { (titel, text) ->
        Text(titel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
        Absatz(text, oben = 2)
    }
}
