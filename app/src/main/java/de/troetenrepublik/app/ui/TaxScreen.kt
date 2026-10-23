package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
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
    val year = Tax.yearOf(System.currentTimeMillis())
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
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
            ) {
                Text(S.allowanceTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (settings.allowance <= 0) {
                    Text(S.allowanceNotSetTitle, style = MaterialTheme.typography.headlineSmall)
                    Text(S.allowanceIntro(Fmt.euro(Tax.MAX_ALLOWANCE, 0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(Fmt.euro(settings.allowance), style = MaterialTheme.typography.headlineSmall)
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
            KeyValueRow(S.taxWithheld, Fmt.euro(taxInYear))
            KeyValueRow(S.realizedStockGains, Fmt.euro(tax.gainsStocks))
            KeyValueRow(S.otherIncome, Fmt.euro(tax.gainsGeneral))
            KeyValueRow(S.lossPotStocks, Fmt.euro(tax.lossPotStocks))
            KeyValueRow(S.lossPotGeneral, Fmt.euro(tax.lossPotGeneral))
            AufklappText(S.taxNote, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
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
                    supportingText = { Text(S.taxIdHint) },
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
            Text(S.allowanceSheetIntro(Fmt.euro(Tax.MAX_ALLOWANCE, 0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            Text("${displayAmount(input)} €", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0, 500.0, 801.0, 1000.0).forEach { p ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { input = if (p == 0.0) "" else p.toInt().toString() }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (p == 0.0) S.none else Fmt.euro(p, 0), style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Keypad(allowDecimal = false, onDigit = { input = appendDigit(input, it) }, onBackspace = { input = input.dropLast(1) })
            Spacer(Modifier.height(16.dp))
            FloatingPill(label = S.save, enabled = v in 0.0..Tax.MAX_ALLOWANCE, modifier = Modifier.fillMaxWidth()) { onSave(v) }
        }
    }
}
