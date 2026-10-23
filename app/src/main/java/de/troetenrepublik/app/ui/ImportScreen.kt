package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.plattform.rememberSpeichern
import de.troetenrepublik.app.plattform.ZurueckTaste
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Uebernimmt ein anderswo gekauftes Wertpapier mit Kaufdatum, Stueckzahl und Kaufkurs ins Depot.
// Es fliesst kein Geld. Verlauf und Ergebnis rechnen ab dem Kaufdatum.
@Composable
fun ImportScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var symbol by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val chosen = symbol
    if (chosen == null) {
        ImportSearch(state, onBack) { s, n -> symbol = s; name = n }
    } else {
        ZurueckTaste { symbol = null }
        ImportForm(state, chosen, name, onChange = { symbol = null }, onDone = { onBack(); onOpen(chosen) })
    }
}

// Schritt 1: Wertpapier waehlen

@Composable
private fun ImportSearch(state: AppState, onBack: () -> Unit, onPick: (String, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var remote by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }
    val local = remember(query) { Symbols.search(query).filter { it.type.directlyTradable } }

    LaunchedEffect(query) {
        remote = emptyList()
        if (query.trim().length < 2) return@LaunchedEffect
        try {
            delay(350)
            val known = local.map { it.symbol }.toSet()
            remote = state.market.search(query.trim()).distinctBy { it.symbol }
                .filter { it.symbol !in known && Symbols.typeOf(it.symbol).directlyTradable }.take(12)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { remote = emptyList() }
    }

    // Ganzes Depot aus einer Portfolio-Datei oder der Sicherung.
    val portfolioImport = rememberPortfolioImport(state)
    val portfolioExport = rememberSpeichern(inhalt = { state.exportJson() }) { text, ok ->
        state.showToast(if (ok) S.exportDone(text.length / 1024 + 1) else S.exportFailed)
    }

    // Nach unten wegwischen, sobald die Liste oben steht.
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().sheetDismiss(dismiss) { !liste.canScrollBackward },
        state = liste,
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(S.importPosition, style = MaterialTheme.typography.headlineLarge)
                Text(
                    S.importIntro, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.field))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, modifier = Modifier.width(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(S.importSearchHint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (query.isBlank()) {
            item {
                SectionHeader(S.importFileSection)
                NavRow(S.importPortfolioJson, "") { portfolioImport() }
                NavRow(S.exportPortfolioJson, "") { portfolioExport(Backup.fileName()) }
            }
            item { SectionHeader(S.popular) }
            items(Symbols.popular, key = { "pop-" + it.symbol }) { inst ->
                AssetRow(symbol = inst.symbol, name = inst.name, subtitle = inst.symbol) { onPick(inst.symbol, inst.name) }
            }
        } else {
            items(local, key = { "loc-" + it.symbol }) { inst ->
                AssetRow(symbol = inst.symbol, name = inst.name, subtitle = inst.symbol) { onPick(inst.symbol, inst.name) }
            }
            items(remote, key = { "rem-" + it.symbol }) { hit ->
                AssetRow(symbol = hit.symbol, name = hit.name, subtitle = listOf(hit.symbol, hit.exchange).filter { it.isNotBlank() }.joinToString(" · ")) {
                    onPick(hit.symbol, hit.name)
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Schritt 2: Kaufdatum, Stueckzahl, Kaufkurs

private enum class ImportField { QTY, PRICE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportForm(state: AppState, symbol: String, name: String, onChange: () -> Unit, onDone: () -> Unit) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    LaunchedEffect(symbol) { state.market.track(listOf(symbol)) }

    val today = remember { LocalDate.now() }
    var dayEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var qtyInput by rememberSaveable { mutableStateOf("") }
    var priceInput by rememberSaveable { mutableStateOf("") }
    var priceTouched by rememberSaveable { mutableStateOf(false) }
    var field by rememberSaveable { mutableStateOf(ImportField.QTY) }
    var showPicker by remember { mutableStateOf(false) }
    var close by remember { mutableStateOf<Candle?>(null) }
    var closeLoaded by remember { mutableStateOf(false) }

    val day = LocalDate.ofEpochDay(dayEpoch)
    // Zeitpunkt der Buchung: Mittag des Kaufdatums, heute hoechstens jetzt.
    val purchasedAt = minOf(day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), System.currentTimeMillis())
    val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    LaunchedEffect(symbol, dayEpoch) {
        closeLoaded = false
        close = state.closeOn(symbol, dayStart)
        closeLoaded = true
        val c = close
        if (c != null && !priceTouched) priceInput = toInput(c.close, if (c.close < 1) 4 else 2)
    }

    val qty = parseAmount(qtyInput)
    val price = parseAmount(priceInput)
    val current = quotes[symbol]?.price?.takeIf { it.isFinite() && it > 0 }
    val cost = qty * price
    val valueNow = current?.let { qty * it }
    val ready = qty > 0 && price > 0

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onChange) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
            }
            Text(S.importPosition, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            AssetRow(
                symbol = symbol, name = name,
                subtitle = listOfNotNull(symbol, current?.let { Fmt.priceEuro(it) }).joinToString(" · "),
                rightPlain = S.importChange, onClick = onChange,
            )

            Row(
                Modifier.fillMaxWidth().clickable { showPicker = true }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(S.importDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.date(purchasedAt), style = MaterialTheme.typography.titleMedium)
                }
                Text(S.importChange, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                ChevronRight(height = 13.dp)
            }

            // Antippen waehlt das Feld, darunter erscheint das Ziffernfeld.
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                InputBox(S.importQty, displayAmount(qtyInput), active = field == ImportField.QTY, modifier = Modifier.weight(1f)) { field = ImportField.QTY }
                Spacer(Modifier.width(12.dp))
                InputBox(S.importPrice, displayAmount(priceInput) + " €", active = field == ImportField.PRICE, modifier = Modifier.weight(1f)) { field = ImportField.PRICE }
            }
            val c = close
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        c != null -> S.importCloseOn(Fmt.date(c.t), Fmt.priceEuro(c.close))
                        closeLoaded -> S.importNoClose
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (c != null && abs(parseAmount(priceInput) - c.close) > 1e-9) {
                    Text(
                        S.importUseClose, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).clickable {
                            priceInput = toInput(c.close, if (c.close < 1) 4 else 2); priceTouched = true
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            KeyValueRow(S.importCostBasis, Fmt.euro(cost))
            if (valueNow != null) {
                KeyValueRow(S.importValueToday, Fmt.euro(valueNow))
                val diff = valueNow - cost
                val pct = if (cost > 0) diff / cost * 100.0 else 0.0
                KeyValueRow(
                    S.importResult,
                    if (ready) Fmt.signedEuro(diff) + "  " + (if (pct >= 0) "+" else "−") + Fmt.pct(abs(pct)) else "–",
                    valueColor = if (!ready) null else LocalSignal.current.forChange(diff),
                )
            }
            Text(
                S.importDividendNote, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
            Keypad(allowDecimal = true, onDigit = { dgt ->
                if (field == ImportField.QTY) qtyInput = appendDigit(qtyInput, dgt, 6)
                else { priceInput = appendDigit(priceInput, dgt, 4); priceTouched = true }
            }, onBackspace = {
                if (field == ImportField.QTY) qtyInput = qtyInput.dropLast(1)
                else { priceInput = priceInput.dropLast(1); priceTouched = true }
            })
            Spacer(Modifier.height(10.dp))
            FloatingPill(label = S.importConfirm, enabled = ready, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                if (state.importPosition(symbol, name, qty, price, purchasedAt)) onDone()
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1980..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (picked <= today) { dayEpoch = picked.toEpochDay(); priceTouched = false }
                    }
                    showPicker = false
                }) { Text(S.apply) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(S.cancel) } },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

@Composable
private fun InputBox(label: String, value: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.field))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(Radius.field),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}
