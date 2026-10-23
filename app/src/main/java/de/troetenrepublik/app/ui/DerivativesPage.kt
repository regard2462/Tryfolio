package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.lerp
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.isFactor
import de.troetenrepublik.app.data.isKnockout
import de.troetenrepublik.app.data.isLong
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.Pricing
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DerivTab { KNOCKOUT, WARRANT, FACTOR }

// Derivate eines Basiswerts: Reiter fuer Knock-Outs, Optionsscheine und Faktoren zum Wischen, Filterpillen,
// sortierbare Spalten und der Waehler am unteren Rand (Hebel, Delta oder Faktor). Ein geoeffnetes Produkt liegt
// ueber dieser Seite, nach unten weggewischt ist man wieder hier. Die Akzentfarbe kommt aus dem Logo des Basiswerts.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DerivativesPage(
    state: AppState,
    symbol: String,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    // Richtung beim Oeffnen: Long (Vorgabe) oder Short, etwa vom Long/Short-Balken der Wertpapierseite.
    initialLong: Boolean = true,
) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val instrument = Symbols.find(symbol)
    val q = quotes[symbol]
    val name = q?.name?.ifBlank { null } ?: instrument?.name ?: symbol
    val spot = q?.price ?: 0.0
    val spotNative = q?.nativePrice ?: 0.0
    val ccy = q?.nativeCurrency ?: "EUR"
    val fx = q?.fx ?: 1.0
    val rate = state.riskFree
    val accent = LogoColors.colors[symbol] ?: MaterialTheme.colorScheme.onSurface
    val accentIsCustom = LogoColors.colors[symbol] != null

    val pager = rememberPagerState { DerivTab.entries.size }
    val scope = rememberCoroutineScope()
    var long by rememberSaveable { mutableStateOf(initialLong) }
    // Je Reiter eigene Sortierung und eigener Waehler
    var ascending by rememberSaveable { mutableStateOf(listOf(true, true, true)) }
    var leverage by rememberSaveable { mutableStateOf(0) }
    var factor by rememberSaveable { mutableStateOf(0) }
    var deltaMin by rememberSaveable { mutableStateOf(0.0) }
    var customDialog by remember { mutableStateOf(false) }
    var custom by remember(symbol) { mutableStateOf<Derivative?>(null) }
    var vol by remember(symbol) { mutableStateOf(Derivatives.defaultVol(Symbols.typeOf(symbol))) }
    // Filter fuer Optionsscheine
    var strikeMin by rememberSaveable(symbol) { mutableStateOf(0.0) }
    var strikeMax by rememberSaveable(symbol) { mutableStateOf(0.0) }
    var expiryFrom by rememberSaveable(symbol) { mutableStateOf(0L) }
    var expiryTo by rememberSaveable(symbol) { mutableStateOf(0L) }
    var strikeSheet by remember { mutableStateOf(false) }
    var maturitySheet by remember { mutableStateOf(false) }
    var issuerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(symbol) { vol = state.volatilityFor(symbol) }

    // Alle Produkte eines Reiters (vor den Filtern).
    @Composable
    fun alle(tab: DerivTab): List<Derivative> {
        val now = System.currentTimeMillis()
        val liste = remember(symbol, spotNative, spot, tab, long, vol) {
            if (spot <= 0 || spotNative <= 0) emptyList() else when (tab) {
                DerivTab.KNOCKOUT -> Derivatives.knockouts(symbol, name, spotNative, ccy, long, now = now)
                DerivTab.FACTOR -> Derivatives.factors(symbol, name, spot, now = now).filter { if (long) it.factor > 0 else it.factor < 0 }
                DerivTab.WARRANT -> Derivatives.warrants(symbol, name, spotNative, ccy, vol, long, now)
            }
        }
        LaunchedEffect(liste) { state.registerDerivatives(liste) }
        return liste
    }

    // Kennzahl je Produkt: Hebel, Delta oder Faktor
    fun measure(d: Derivative): Double {
        val now = System.currentTimeMillis()
        return when {
            d.kind.isFactor -> abs(d.factor)
            d.kind.isKnockout -> Pricing.leverage(d, spot, now, fx, rate)
            else -> Pricing.greeks(d, spot, now, fx, rate)?.delta ?: 0.0
        }
    }

    val listen = remember { List(DerivTab.entries.size) { LazyListState() } }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val warrantsAll = alle(DerivTab.WARRANT)
    val strikeAll = remember(warrantsAll) { warrantsAll.map { it.strike } }
    val strikeStep = Derivatives.strikeStep(spotNative)
    LaunchedEffect(strikeAll) {
        if (strikeAll.isNotEmpty() && strikeMin == 0.0) { strikeMin = strikeAll.min(); strikeMax = strikeAll.max() }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { !listen[pager.currentPage].canScrollBackward },
    ) {
        ScreenHeader(S.derivatives, "$name · ${Fmt.price(spot)}", onBack)
        val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, DerivTab.entries.size - 1f)
        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            listOf(S.knockOuts, S.warrants, S.factors).forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface, (1f - abs(pos - i)).coerceIn(0f, 1f)),
                    modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(vertical = 6.dp),
                )
            }
        }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize().mausWischen(pager)) { seite ->
            val tab = DerivTab.entries[seite]
            val all = if (tab == DerivTab.WARRANT) warrantsAll else alle(tab)
            val auf = ascending[seite]
            val shown = remember(all, leverage, factor, deltaMin, auf, spot, strikeMin, strikeMax, expiryFrom, expiryTo, custom, long) {
                val list = all.map { it to measure(it) }
                    .filter { (d, m) ->
                        when (tab) {
                            DerivTab.KNOCKOUT -> leverage == 0 || m >= leverage - 0.5
                            DerivTab.FACTOR -> factor == 0 || m >= factor - 0.5
                            // Praktisch wertlose Scheine (Delta unter 0,005) fuehren die Liste nicht an
                            DerivTab.WARRANT -> abs(m) >= 0.005 && abs(m) >= deltaMin - 1e-9 &&
                                (strikeMin <= 0 || d.strike >= strikeMin - 1e-9) && (strikeMax <= 0 || d.strike <= strikeMax + 1e-9) &&
                                (expiryFrom <= 0 || d.expiry >= expiryFrom) && (expiryTo <= 0 || d.expiry <= expiryTo + 86_400_000L)
                        }
                    }
                    .sortedBy { (_, m) -> m }
                    .let { if (auf) it else it.reversed() }
                val c = custom
                if (c != null && tab == DerivTab.KNOCKOUT && (c.kind.isLong == long)) listOf(c to measure(c)) + list else list
            }
            val listState = listen[seite]
            when (tab) {
                DerivTab.KNOCKOUT -> LaunchedEffect(leverage, long, custom) { listState.scrollToItem(0) }
                DerivTab.FACTOR -> LaunchedEffect(factor, long) { listState.scrollToItem(0) }
                DerivTab.WARRANT -> LaunchedEffect(deltaMin, long) { listState.scrollToItem(0) }
            }

            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val pillAccent = if (accentIsCustom) accent else null
                            if (tab == DerivTab.WARRANT) {
                                FilterPill(S.call, active = long, accent = pillAccent) { long = true }
                                FilterPill(S.put, active = !long, accent = pillAccent) { long = false }
                                FilterPill(S.strikePrice, chevron = true) { strikeSheet = true }
                                FilterPill(S.maturity, chevron = true) { maturitySheet = true }
                                FilterPill(S.issuer, trailing = Derivatives.HOUSE, chevron = true) { issuerSheet = true }
                            } else {
                                FilterPill(S.long, active = long, accent = pillAccent) { long = true }
                                FilterPill(S.short, active = !long, accent = pillAccent) { long = false }
                                FilterPill(S.issuer, trailing = Derivatives.HOUSE, chevron = true) { issuerSheet = true }
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                Modifier.clip(RoundedCornerShape(Radius.chip))
                                    .clickable { ascending = ascending.toMutableList().also { it[seite] = !it[seite] } }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(when (tab) { DerivTab.FACTOR -> S.factor; DerivTab.WARRANT -> S.delta; else -> S.leverage }, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(6.dp))
                                SortArrow(auf)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                when (tab) { DerivTab.KNOCKOUT -> S.knockOut; DerivTab.FACTOR -> S.ratio; else -> S.strikePrice },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (shown.isEmpty()) {
                        item { EmptyHint(if (spot <= 0) S.noPriceForProduct else S.noProductForFilter) }
                    } else {
                        items(shown, key = { it.first.id }) { (d, m) ->
                            DerivativeRow(d, m, spot, fx, rate, tab, isCustom = d.id == custom?.id) { onPick(d.id) }
                        }
                    }

                    item { Spacer(Modifier.height(150.dp)) }
                }

                // Waehler am unteren Rand
                when (tab) {
                    DerivTab.KNOCKOUT -> Stepper(
                        text = if (leverage <= 0) S.allLeverages else "$leverage×",
                        minusEnabled = leverage > 0, plusEnabled = leverage < Derivatives.MAX_LEVERAGE,
                        accent = accent,
                        onMinus = { leverage = (leverage - 1).coerceAtLeast(0) }, onPlus = { leverage = (leverage + 1).coerceAtMost(Derivatives.MAX_LEVERAGE) },
                        onTap = { customDialog = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    DerivTab.FACTOR -> Stepper(
                        text = if (factor <= 0) S.allFactors else "$factor×",
                        minusEnabled = factor > 0, plusEnabled = factor < Derivatives.MAX_FACTOR,
                        accent = accent,
                        onMinus = { factor = (factor - 1).coerceAtLeast(0) }, onPlus = { factor = (factor + 1).coerceAtMost(Derivatives.MAX_FACTOR) },
                        onTap = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    DerivTab.WARRANT -> Stepper(
                        text = String.format(I18n.locale, "%.2f", if (long) deltaMin else -deltaMin),
                        // Call: Delta laeuft von 0 nach 1 (Plus aktiv); Put: von 0 nach -1 (Minus aktiv)
                        minusEnabled = if (long) deltaMin > 0 else deltaMin < 0.999,
                        plusEnabled = if (long) deltaMin < 0.999 else deltaMin > 0,
                        accent = accent,
                        onMinus = { deltaMin = if (long) (deltaMin - 0.05).coerceAtLeast(0.0) else (deltaMin + 0.05).coerceAtMost(1.0) },
                        onPlus = { deltaMin = if (long) (deltaMin + 0.05).coerceAtMost(1.0) else (deltaMin - 0.05).coerceAtLeast(0.0) },
                        onTap = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                        hint = S.deltaFilter,
                    )
                }
            }
        }
    }

    if (customDialog) {
        CustomLeverageDialog(
            onDismiss = { customDialog = false },
            onConfirm = { lev ->
                customDialog = false
                if (lev == null) { leverage = 0; custom = null }
                else {
                    val whole = lev == lev.roundToInt().toDouble()
                    // Ganze Zahlen bis 200 filtern die Liste, sonst wird ein eigenes Produkt begeben
                    if (whole && lev <= Derivatives.MAX_LEVERAGE) { leverage = lev.roundToInt(); custom = null }
                    else { custom = state.customKnockout(symbol, long, lev); leverage = 0 }
                }
            },
        )
    }

    if (strikeSheet && strikeAll.isNotEmpty()) {
        StrikeFilterSheet(
            min = strikeMin, max = strikeMax, step = strikeStep, ccy = ccy,
            bounds = strikeAll.min() to strikeAll.max(),
            count = { lo, hi -> warrantsAll.count { it.strike >= lo - 1e-9 && it.strike <= hi + 1e-9 } },
            onDismiss = { strikeSheet = false },
            onApply = { lo, hi -> strikeMin = lo; strikeMax = hi; strikeSheet = false },
        )
    }
    if (maturitySheet) {
        MaturityFilterSheet(
            from = expiryFrom, to = expiryTo,
            count = { lo, hi -> warrantsAll.count { (lo <= 0 || it.expiry >= lo) && (hi <= 0 || it.expiry <= hi + 86_400_000L) } },
            onDismiss = { maturitySheet = false },
            onApply = { lo, hi -> expiryFrom = lo; expiryTo = hi; maturitySheet = false },
        )
    }
    if (issuerSheet) IssuerSheet(count = warrantsAll.size) { issuerSheet = false }
}

@Composable
private fun SortArrow(ascending: Boolean) {
    Text(if (ascending) "↑" else "↓", style = MaterialTheme.typography.titleSmall)
}

// Waehler unten: Minus, Anzeigefeld (antippbar) und Plus in der Akzentfarbe des Basiswerts.
@Composable
private fun Stepper(
    text: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    accent: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)).padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton(accent = accent, enabled = minusEnabled, onClick = onMinus) { MinusGlyph(color = if (minusEnabled) Color.Black else accent.copy(alpha = 0.5f)) }
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(Radius.field))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onTap),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            StepButton(accent = accent, enabled = plusEnabled, onClick = onPlus) { PlusGlyph(color = if (plusEnabled) Color.Black else accent.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
private fun StepButton(accent: Color, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(Radius.field))
            .background(if (enabled) accent else accent.copy(alpha = 0.22f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// Eigener Hebel: beliebige Zahl von 1 bis 200, auch mit Nachkommastellen.
@Composable
private fun CustomLeverageDialog(onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
    val valid = text.isBlank() || (parsed != null && parsed >= Derivatives.MIN_LEVERAGE && parsed <= Derivatives.MAX_CUSTOM_LEVERAGE)
    fun confirm() { if (text.isBlank()) onConfirm(null) else if (valid) onConfirm(parsed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.card),
        title = { Text(S.customLeverage, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(S.customLeverageHint + " " + S.leverageDialogHint(Derivatives.MIN_LEVERAGE, Derivatives.MAX_CUSTOM_LEVERAGE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    singleLine = true,
                    isError = !valid,
                    shape = RoundedCornerShape(Radius.field),
                    suffix = { Text("×") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { confirm() }) { Text(S.apply) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}

// Ausuebungspreis: minimaler und maximaler Strike mit Plus/Minus, Ergebniszahl, Anwenden.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrikeFilterSheet(
    min: Double, max: Double, step: Double, ccy: String, bounds: Pair<Double, Double>,
    count: (Double, Double) -> Int, onDismiss: () -> Unit, onApply: (Double, Double) -> Unit,
) {
    var lo by remember { mutableStateOf(if (min > 0) min else bounds.first) }
    var hi by remember { mutableStateOf(if (max > 0) max else bounds.second) }
    val n = count(lo, hi)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.sheet),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(S.strikePrice, style = MaterialTheme.typography.headlineMedium)
            Text(S.resultsCount(n), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            StrikeStepper(S.minStrike, lo, ccy, minusEnabled = lo > bounds.first + 1e-9, plusEnabled = lo + step <= hi + 1e-9,
                onMinus = { lo = (lo - step).coerceAtLeast(bounds.first) }, onPlus = { lo = (lo + step).coerceAtMost(hi) })
            Spacer(Modifier.height(24.dp))
            StrikeStepper(S.maxStrike, hi, ccy, minusEnabled = hi - step >= lo - 1e-9, plusEnabled = hi < bounds.second - 1e-9,
                onMinus = { hi = (hi - step).coerceAtLeast(lo) }, onPlus = { hi = (hi + step).coerceAtMost(bounds.second) })
            Spacer(Modifier.height(28.dp))
            Row { Spacer(Modifier.weight(1f)); FloatingPill(label = S.apply, trailing = "✓", enabled = n > 0) { onApply(lo, hi) } }
        }
    }
}

@Composable
private fun StrikeStepper(title: String, value: Double, ccy: String, minusEnabled: Boolean, plusEnabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val accent = LocalSignal.current.up
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(accent = accent, enabled = minusEnabled, onClick = onMinus) { MinusGlyph(color = if (minusEnabled) Color.Black else accent.copy(alpha = 0.5f)) }
        Text(Derivatives.money(value, ccy, I18n.locale), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton(accent = accent, enabled = plusEnabled, onClick = onPlus) { PlusGlyph(color = if (plusEnabled) Color.Black else accent.copy(alpha = 0.5f)) }
    }
}

// Faelligkeit: Startdatum und Endtermin.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaturityFilterSheet(from: Long, to: Long, count: (Long, Long) -> Int, onDismiss: () -> Unit, onApply: (Long, Long) -> Unit) {
    var lo by remember { mutableStateOf(from) }
    var hi by remember { mutableStateOf(to) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val n = count(lo, hi)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.sheet),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(S.maturity, style = MaterialTheme.typography.headlineMedium)
            Text(S.resultsCount(n), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            DateField(S.startDateFilter, lo) { picking = 0 }
            Spacer(Modifier.height(18.dp))
            DateField(S.endDateFilter, hi) { picking = 1 }
            Spacer(Modifier.height(28.dp))
            Row {
                Text(S.none, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { lo = 0L; hi = 0L }.padding(12.dp))
                Spacer(Modifier.weight(1f))
                FloatingPill(label = S.apply, trailing = "✓", enabled = n > 0) { onApply(lo, hi) }
            }
        }
    }
    picking?.let { which ->
        val st = rememberDatePickerState(initialSelectedDateMillis = (if (which == 0) lo else hi).takeIf { it > 0 } ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { if (which == 0) lo = it else hi = it }; picking = null }) { Text(S.apply) } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(S.cancel) } },
        ) { DatePicker(state = st, showModeToggle = false) }
    }
}

@Composable
private fun DateField(label: String, value: Long, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.field)).clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (value > 0) Fmt.date(value) else "–", style = MaterialTheme.typography.headlineSmall)
    }
}

// Emittent: Es gibt nur das Haus; das Blatt erklaert das, statt eine Auswahl vorzutaeuschen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssuerSheet(count: Int, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.sheet),
    ) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp)) {
            Text(S.issuer, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 12.dp))
            Text(S.resultsCount(count), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(12.dp))
            OptionRow(Derivatives.HOUSE, "", selected = true) {}
            Spacer(Modifier.height(8.dp))
            Text(S.issuerNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.padding(horizontal = 12.dp)) {
                Spacer(Modifier.weight(1f))
                FloatingPill(label = S.ok, trailing = "✓", enabled = true) { onDismiss() }
            }
        }
    }
}

@Composable
private fun DerivativeRow(d: Derivative, m: Double, spot: Double, fx: Double, rate: Double, tab: DerivTab, isCustom: Boolean, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val price = Pricing.price(d, spot, now, fx, rate)
    val dist = Pricing.distanceToBarrier(d, spot, fx)
    val locale = I18n.locale

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when (tab) {
                    DerivTab.WARRANT -> String.format(locale, "%.2f", m)
                    DerivTab.FACTOR -> "${Fmt.num(m, 0)}×"
                    else -> "${Fmt.num(m, 2)}×"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            // Eine Zeile ohne Emittent (steht in der Filterpille); sonst Umbruch statt kleinerer Schrift
            VollText(
                when (tab) {
                    DerivTab.KNOCKOUT -> (if (isCustom) S.leverageOwn + " · " else "") + "${S.productKind("TURBO")} · ${S.distance(Fmt.pct(dist, 2))}"
                    DerivTab.FACTOR -> "${S.openEnd} · ${S.productKind("FACTOR")}"
                    DerivTab.WARRANT -> "${Fmt.shortDate(d.expiry)} · ${S.productKind("WARRANT")}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (tab) {
                    DerivTab.KNOCKOUT -> Derivatives.money(d.barrier, d.ccy, locale)
                    DerivTab.FACTOR -> ratioText(d.ratio)
                    DerivTab.WARRANT -> Derivatives.money(d.strike, d.ccy, locale)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(Fmt.price(price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
