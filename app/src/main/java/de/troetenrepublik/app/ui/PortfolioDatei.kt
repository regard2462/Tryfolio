package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.engine.BrokerExport
import de.troetenrepublik.app.engine.TradeRepublicImport
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.rememberOeffnen
import kotlinx.coroutines.launch

// Liest eine Portfolio-Datei (JSON aus „Portfolio exportieren“) oder CSV-Exporte von Brokern, siehe BrokerExport.
// Gibt die Funktion zurueck, die die Auswahl oeffnet, die Dialoge zeichnet sie selbst.
@Composable
fun rememberPortfolioImport(state: AppState): () -> Unit {
    val scope = rememberCoroutineScope()
    var fehler by remember { mutableStateOf<String?>(null) }
    var vorschau by remember { mutableStateOf<Pair<String, Backup.Payload>?>(null) }
    var trLaeuft by remember { mutableStateOf(false) }
    var trVorschau by remember { mutableStateOf<TradeRepublicImport.Ergebnis?>(null) }
    var wahl by remember { mutableStateOf(false) }
    val oeffnen = rememberOeffnen { text ->
        if (text == null) { fehler = S.importNotReadable; return@rememberOeffnen }
        // Export eines Brokers: Werte zuordnen, dann Vorschau
        if (BrokerExport.erkennen(text) != null) {
            trLaeuft = true
            scope.launch {
                val e = runCatching { state.brokerExportLesen(text) }.getOrNull()
                trLaeuft = false
                if (e == null) fehler = S.importNotReadable else trVorschau = e
            }
            return@rememberOeffnen
        }
        when (val parsed = Backup.parse(text)) {
            // Keine Sicherung und kein bekannter Export: sagen, welche Exporte die App liest
            is Backup.Outcome.Error -> fehler = if (text.trimStart('\uFEFF').trimStart().startsWith("{")) S.importError(parsed.code, parsed.extra)
                else S.importUnbekannt(BrokerExport.namen.joinToString(", "))
            is Backup.Outcome.Ok -> vorschau = text to parsed.payload
        }
    }

    fehler?.let { msg ->
        AlertDialog(
            onDismissRequest = { fehler = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.importFailedTitle) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { fehler = null }) { Text(S.ok) } },
        )
    }

    if (trLaeuft) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.importPortfolio) },
            text = {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.padding(end = 16.dp))
                    Text(S.trImportLaedt)
                }
            },
            confirmButton = {},
        )
    }

    trVorschau?.let { e ->
        val k = e.konto
        // Kein negatives Guthaben buchen: fehlen Einzahlungen, bleibt die jeweilige Uebernahme gesperrt
        val guthabenNachErsetzen = k.cash
        val guthabenNachZusammen = (e.ergaenzung ?: de.troetenrepublik.app.engine.Zusammenfuehren.konten(state.account.value, k)).cash
        AlertDialog(
            onDismissRequest = { trVorschau = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.trImportTitel(e.quelle)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.trImportZeitraum(Fmt.date(e.von), Fmt.date(e.bis), e.zeilen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ImportZahlen(k.cash, k.positions.size, 0, k.transactions.size)
                    if (k.positions.isNotEmpty()) {
                        var positionenAuf by remember { mutableStateOf(false) }
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable { positionenAuf = !positionenAuf }.padding(top = 6.dp, bottom = 2.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(S.kernPositionen(k.positions.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            AufklappPfeil(positionenAuf)
                        }
                        if (positionenAuf) Text(k.positions.joinToString("\n") { "${it.name}: ${Fmt.qty(it.qty)}" }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (e.ohneKurs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportOhneKurs(e.ohneKurs.joinToString(", ") { "${it.first} (${Fmt.qty(it.second)})" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (e.ungelesen > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportUngelesen(e.ungelesen, e.ungelesenBeispiele.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (e.ohneWechselkurs > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportWechselkurs(e.ohneWechselkurs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (e.ungeprueft) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportUngeprueft(e.quelle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (e.ueberGrenze > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportUeberGrenze(e.ueberGrenze, de.troetenrepublik.app.engine.Trading.MAX_BUCHUNGEN), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (guthabenNachErsetzen < -0.005 || guthabenNachZusammen < -0.005) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportGuthabenNegativ(Fmt.euro(minOf(guthabenNachZusammen, guthabenNachErsetzen)), e.quelle == BrokerExport.Format.DEGIRO.anzeige), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (e.bekannt > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportBekannt(e.bekannt, e.zeilen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.ohneKauf.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(S.trImportOhneKauf(e.ohneKauf.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    AufklappText(if (e.quelle == BrokerExport.Format.TRADE_REPUBLIC.anzeige) S.trImportHinweis else S.brokerImportHinweis, flaeche = MaterialTheme.colorScheme.surfaceContainer)
                    Spacer(Modifier.height(8.dp))
                    Text(S.importChoice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(enabled = guthabenNachZusammen >= -0.005, onClick = { state.brokerExportUebernehmen(e, zusammenfuehren = true); trVorschau = null }) { Text(S.importMerge) }
                    TextButton(enabled = guthabenNachErsetzen >= -0.005, onClick = { state.brokerExportUebernehmen(e, zusammenfuehren = false); trVorschau = null }) { Text(S.importReplace, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { trVorschau = null }) { Text(S.cancel) } },
        )
    }

    vorschau?.let { (text, payload) ->
        AlertDialog(
            onDismissRequest = { vorschau = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.importTitle) },
            text = {
                Column {
                    Text(S.exportedAt(Fmt.dateTime(payload.exportedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ImportZahlen(payload.account.cash, payload.account.positions.size, payload.account.plans.size, payload.account.transactions.size)
                    Spacer(Modifier.height(8.dp))
                    Text(S.importChoice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            // Ersetzen oder zum aktuellen Depot dazunehmen
            confirmButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { scope.launch { state.importJson(text, zusammenfuehren = true) }; vorschau = null }) { Text(S.importMerge) }
                    TextButton(onClick = { scope.launch { state.importJson(text) }; vorschau = null }) { Text(S.importReplace, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { vorschau = null }) { Text(S.cancel) } },
        )
    }
    if (wahl) {
        AlertDialog(
            onDismissRequest = { wahl = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.importPortfolio) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ImportWahlZeile(S.importWahlSicherung, S.importWahlSicherungText) { wahl = false; oeffnen() }
                    ImportWahlZeile(S.importWahlBroker, S.importWahlBrokerText(BrokerExport.namen.joinToString(", "))) { wahl = false; oeffnen() }
                    // [0.78] Abgleich mit dem Flex-Bericht; Ablauf und Vorschau in ui/FlexAbgleich.kt
                    if (de.troetenrepublik.app.plattform.Plattform.kursabruf) ImportWahlZeile(S.flex("zeile"), S.flex("zeile.unter")) { wahl = false; state.flex.starten() }
                    Spacer(Modifier.height(8.dp))
                    AufklappText(S.importWahlAnleitung, nachZeile = true, flaeche = MaterialTheme.colorScheme.surfaceContainer)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { wahl = false }) { Text(S.cancel) } },
        )
    }
    return { wahl = true }
}

// Zahlen einer Datei als Zeilen mit dem Wert rechts statt einer Zeile mit Trennpunkten.
@Composable
private fun ImportZahlen(guthaben: Double, positionen: Int, plaene: Int, buchungen: Int) {
    val zeilen = listOfNotNull(
        S.cash to Fmt.euro(guthaben),
        S.positions to Fmt.num(positionen.toDouble(), 0),
        if (plaene > 0) S.savingsPlans to Fmt.num(plaene.toDouble(), 0) else null,
        S.transactions to Fmt.num(buchungen.toDouble(), 0),
    )
    Column(Modifier.fillMaxWidth()) {
        zeilen.forEach { (name, wert) ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ImportWahlZeile(titel: String, text: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.field)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        ChevronRight()
    }
}
