package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.docs.Documents
import de.troetenrepublik.app.engine.Tax
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Steuerübersicht: Steuer-ID, Freistellungsauftrag (Aktivieren und Verwalten), Verlustverrechnungstoepfe,
// einbehaltene Steuern, Jahressteuerbescheinigung.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxScreen(state: AppState, onBack: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editAllowance by remember { mutableStateOf(false) }
    var editTaxId by remember { mutableStateOf(false) }
    var doc by remember { mutableStateOf<Documents.Document?>(null) }
    val year = Tax.yearOf(de.troetenrepublik.app.engine.Uhr.jetzt())
    val tax = Tax.forYear(account.tax, System.currentTimeMillis())
    val taxInYear = account.transactions.filter { Tax.yearOf(it.ts) == year }.sumOf { it.tax }

    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item {
            ScreenHeader(S.taxes, null, onBack)
            Spacer(Modifier.height(18.dp))

            // Freistellungsauftrag als Karte
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            ) {
                Text(S.allowanceTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (settings.allowance <= 0) {
                    Text(S.allowanceNotSetTitle, style = MaterialTheme.typography.headlineSmall)
                } else {
                    Text(Fmt.euro(settings.allowance), style = MaterialTheme.typography.headlineSmall)
                    // Verbrauchter Anteil des Freibetrags als Balken
                    val verbraucht = (tax.allowanceUsed / settings.allowance).coerceIn(0.0, 1.0)
                    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
                    val balken = AnteilsFarben.folge(0)
                    Canvas(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp)) {
                        val r = CornerRadius(size.height / 2, size.height / 2)
                        drawRoundRect(spur, cornerRadius = r)
                        val w = (size.width * verbraucht).toFloat()
                        if (w > 0f) drawRoundRect(balken, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
                    }
                    Text(
                        S.allowanceUsed(Fmt.euro(tax.allowanceUsed), year, Fmt.euro((settings.allowance - tax.allowanceUsed).coerceAtLeast(0.0))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                FloatingPill(label = if (settings.allowance <= 0) S.activate else S.manage, modifier = Modifier.fillMaxWidth()) { editAllowance = true }
            }
        }

        item {
            SectionHeader(S.taxOverviewYear(year))
            KeyValueRow(S.taxRate, S.taxRateValue(Fmt.pct(Tax.RATE * 100, 3)))
            // Fuer ein Jahr ohne eigene Steuerwerte rechnet die App mit dem juengsten bekannten Jahr
            if (de.troetenrepublik.app.engine.Steuerwerte.geschaetzt(year)) {
                val mit = de.troetenrepublik.app.engine.Steuerwerte.let { s -> s.aktiv.jahre.keys.mapNotNull { it.toIntOrNull() }.filter { it < year }.maxOrNull() ?: s.letztesJahr() }
                Text(S.taxWerteGeschaetzt(year, mit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            KeyValueRow(S.taxWithheld, Fmt.euro(taxInYear))
            // Uebernommene Verkaeufe, deren Datei keine Steuern nennt: ehrlich sagen, was nicht eingerechnet ist
            val uebernommen = account.transactions.filter { it.via.startsWith("IMPORT") }
            if (uebernommen.any { it.type == de.troetenrepublik.app.data.TxType.SELL && it.tag == "SELL" } &&
                uebernommen.none { it.tax != 0.0 || it.type == de.troetenrepublik.app.data.TxType.TAX }) {
                Text(S.taxImportOhneSteuer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            KeyValueRow(S.realizedStockGains, Fmt.euro(tax.gainsStocks))
            KeyValueRow(S.otherIncome, Fmt.euro(tax.gainsGeneral))
            KeyValueRow(S.lossPotStocks, Fmt.euro(tax.lossPotStocks))
            KeyValueRow(S.lossPotGeneral, Fmt.euro(tax.lossPotGeneral))
            if (tax.privateGains != 0.0) KeyValueRow(S.privateGains, Fmt.euro(tax.privateGains))
            InfoZeile(null, S.taxes, S.taxNote)
        }

        item {
            SectionHeader(S.taxData)
            KeyValueRow(S.taxId, settings.taxId.ifBlank { S.notSet })
            ActionLineWide(S.editTaxId) { editTaxId = true }
            KeyValueRow(S.churchTax, S.notModelled)
        }

        item {
            SectionHeader(S.documents)
            ActionLineWide(S.taxCertificate(year)) { doc = Documents.taxCertificate(account, year, settings.allowance, settings.ownerName) }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (editAllowance) {
        AllowanceSheet(
            current = settings.allowance,
            onDismiss = { editAllowance = false },
            onSave = { v -> scope.launch { state.settings.update { it.copy(allowance = v) } }; editAllowance = false },
        )
    }
    if (editTaxId) {
        var text by remember { mutableStateOf(settings.taxId) }
        AlertDialog(
            onDismissRequest = { editTaxId = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.taxId) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter { c -> c.isDigit() }.take(11) },
                    singleLine = true, shape = RoundedCornerShape(Radius.field),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = { TextButton(onClick = { scope.launch { state.settings.update { it.copy(taxId = text) } }; editTaxId = false }) { Text(S.save) } },
            dismissButton = { TextButton(onClick = { editTaxId = false }) { Text(S.cancel) } },
        )
    }
    doc?.let { DocumentSheet(it, onDismiss = { doc = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllowanceSheet(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var input by remember { mutableStateOf(if (current > 0) current.toInt().toString() else "") }
    val v = parseAmount(input)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(S.allowanceTitle, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            BetragText("${displayAmount(input)}\u00A0€", style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Gesetzliche Werte aus den gepflegten Steuerwerten: Einzelperson und Paar
                listOf(0.0 to S.none, Tax.MAX_ALLOWANCE to S.allowanceSingle, Tax.MAX_ALLOWANCE_JOINT to S.allowanceJoint).forEach { (p, name) ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { input = if (p == 0.0) "" else p.toInt().toString() }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                            if (p != 0.0) Text(Fmt.euro(p, 0), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Keypad(allowDecimal = false, onDigit = { input = appendDigit(input, it) }, onBackspace = { input = input.dropLast(1) })
            Spacer(Modifier.height(16.dp))
            FloatingPill(label = S.save, enabled = v in 0.0..Tax.MAX_ALLOWANCE_JOINT, modifier = Modifier.fillMaxWidth()) { onSave(v) }
        }
    }
}
