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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class PlanStep { AMOUNT, INTERVAL, DAY, TIME, REVIEW }

// Sparplan anlegen: Betrag ueber das Ziffernfeld, Intervall, Ausfuehrung Anfang oder Mitte des Monats,
// dann Uebersicht und Erstellen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(PlanStep.AMOUNT) }
    var input by rememberSaveable { mutableStateOf("") }
    var interval by rememberSaveable { mutableStateOf(PlanInterval.MONTHLY) }
    var day by rememberSaveable { mutableStateOf(2) }
    var uhrzeit by rememberSaveable { mutableStateOf(-1) }
    val amount = parseAmount(input)
    val valid = amount >= 1.0 && amount <= 10_000.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentLogo(state, symbol, name, size = 36)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(S.savingsPlan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NamenText(name, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(22.dp))

            when (step) {
                PlanStep.AMOUNT -> {
                    Text("${displayAmount(input)} €", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(
                        S.planAmountHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25.0, 50.0, 100.0, 250.0).forEach { v ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { input = v.toInt().toString() }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(Fmt.euro(v, 0), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Keypad(allowDecimal = true, onDigit = { input = appendDigit(input, it) }, onBackspace = { input = input.dropLast(1) })
                    Spacer(Modifier.height(18.dp))
                    FloatingPill(label = S.next, enabled = valid, modifier = Modifier.fillMaxWidth()) { step = PlanStep.INTERVAL }
                }
                PlanStep.INTERVAL -> {
                    Text(S.interval, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    PlanInterval.entries.forEach { i ->
                        OptionRow(S.intervalLabel(i), S.intervalSubtitle(i), selected = interval == i) { interval = i }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { step = PlanStep.AMOUNT }
                        FloatingPill(label = S.next, modifier = Modifier.weight(1f)) {
                            step = if (interval == PlanInterval.WEEKLY || interval == PlanInterval.BIWEEKLY) PlanStep.TIME else PlanStep.DAY
                        }
                    }
                }
                PlanStep.DAY -> {
                    Text(S.execution, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    OptionRow(S.startOfMonth, S.onDay(2), selected = day == 2) { day = 2 }
                    OptionRow(S.midOfMonth, S.onDay(16), selected = day == 16) { day = 16 }
                    if (interval == PlanInterval.MONTHLY) {
                        OptionRow(S.onDay(9).replaceFirstChar { it.uppercase() }, "", selected = day == 9) { day = 9 }
                        OptionRow(S.onDay(23).replaceFirstChar { it.uppercase() }, "", selected = day == 23) { day = 23 }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { step = PlanStep.INTERVAL }
                        FloatingPill(label = S.next, modifier = Modifier.weight(1f)) { step = PlanStep.TIME }
                    }
                }
                PlanStep.TIME -> {
                    Text(S.planUhrzeit, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    UhrzeitWahl(uhrzeit) { uhrzeit = it }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) {
                            step = if (interval == PlanInterval.WEEKLY || interval == PlanInterval.BIWEEKLY) PlanStep.INTERVAL else PlanStep.DAY
                        }
                        FloatingPill(label = S.next, modifier = Modifier.weight(1f)) { step = PlanStep.REVIEW }
                    }
                }
                PlanStep.REVIEW -> {
                    val preview = SavingsPlan("preview", symbol, name, amount, day, createdAt = System.currentTimeMillis(), interval = interval, uhrzeit = uhrzeit)
                    val next = Trading.nextExecution(preview, System.currentTimeMillis())
                    Text(S.planReview, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow(S.savingsRate, Fmt.euro(amount))
                    KeyValueRow(S.interval, S.intervalLabel(interval))
                    if (interval == PlanInterval.MONTHLY || interval == PlanInterval.QUARTERLY) KeyValueRow(S.executionDay, S.onDay(day))
                    if (interval == PlanInterval.BIWEEKLY) KeyValueRow(S.executionDays, S.intervalSubtitle(PlanInterval.BIWEEKLY))
                    KeyValueRow(S.planUhrzeit, uhrzeitText(uhrzeit))
                    KeyValueRow(S.firstExecution, if (next <= 0) "–" else if (uhrzeit >= 0) Fmt.dateTime(next) else Fmt.date(next))
                    KeyValueRow(S.fee, Fmt.euro(0.0))
                    KeyValueRow(S.paymentMethod, S.cashAccount)
                    Text(
                        S.planNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { step = PlanStep.TIME }
                        FloatingPill(label = S.create, modifier = Modifier.weight(1f)) {
                            if (state.addPlan(symbol, name, amount, day, interval, uhrzeit)) onDismiss()
                        }
                    }
                }
            }
        }
    }
}

// Sparplan verwalten: Sparrate, Intervall, Pausieren, Loeschen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePlanSheet(state: AppState, plan: SavingsPlan, onDismiss: () -> Unit) {
    var editAmount by remember { mutableStateOf(false) }
    var editInterval by remember { mutableStateOf(false) }
    var editUhrzeit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val next = Trading.nextExecution(plan, System.currentTimeMillis())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentLogo(state, plan.symbol, plan.name, size = 36)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(S.managePlan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NamenText(plan.name, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                editAmount -> {
                    Text("${displayAmount(input)} €", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Keypad(allowDecimal = true, onDigit = { input = appendDigit(input, it) }, onBackspace = { input = input.dropLast(1) })
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.cancel, filled = false, modifier = Modifier.weight(1f)) { editAmount = false }
                        FloatingPill(label = S.save, enabled = parseAmount(input) in 1.0..10_000.0, modifier = Modifier.weight(1f)) {
                            state.updatePlan(plan.id, amount = parseAmount(input)); editAmount = false
                        }
                    }
                }
                editUhrzeit -> {
                    UhrzeitWahl(plan.uhrzeit) { state.updatePlan(plan.id, uhrzeit = it); editUhrzeit = false }
                    Spacer(Modifier.height(12.dp))
                    FloatingPill(label = S.cancel, filled = false, modifier = Modifier.fillMaxWidth()) { editUhrzeit = false }
                }
                editInterval -> {
                    PlanInterval.entries.forEach { i ->
                        OptionRow(S.intervalLabel(i), S.intervalSubtitle(i), selected = plan.interval == i) { state.updatePlan(plan.id, interval = i); editInterval = false }
                    }
                    Spacer(Modifier.height(12.dp))
                    FloatingPill(label = S.cancel, filled = false, modifier = Modifier.fillMaxWidth()) { editInterval = false }
                }
                else -> {
                    KeyValueRow(S.savingsRate, Fmt.euro(plan.amount))
                    KeyValueRow(S.interval, S.intervalLabel(plan.interval) + if (plan.interval == PlanInterval.MONTHLY || plan.interval == PlanInterval.QUARTERLY) " · " + S.onDay(plan.dayOfMonth) else "")
                    KeyValueRow(S.status, if (plan.paused) S.paused else S.active)
                    KeyValueRow(S.planUhrzeit, uhrzeitText(plan.uhrzeit))
                    KeyValueRow(S.nextExecution, if (next <= 0) "–" else if (plan.uhrzeit >= 0) Fmt.dateTime(next) else Fmt.date(next))
                    KeyValueRow(S.executions, plan.executions.toString())
                    KeyValueRow(S.investedSoFar, Fmt.euro(plan.invested))
                    if (plan.lastFailed > 0) KeyValueRow(S.lastFailure, Fmt.date(plan.lastFailed), valueColor = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    ActionLine(S.changeRate) { input = ""; editAmount = true }
                    ActionLine(S.changeInterval) { editInterval = true }
                    ActionLine(S.planUhrzeitAendern) { editUhrzeit = true }
                    ActionLine(if (plan.paused) S.resume else S.pause) { state.updatePlan(plan.id, paused = !plan.paused) }
                    ActionLine(S.delete, danger = true) { confirmDelete = true }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.deletePlanTitle) },
            text = { Text(S.deletePlanText) },
            confirmButton = { TextButton(onClick = { state.removePlan(plan.id); confirmDelete = false; onDismiss() }) { Text(S.delete) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(S.cancel) } },
        )
    }
}

// Liste aller Sparplaene, erreichbar ueber die Kachel.
@Composable
fun PlansScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, onSearch: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    var manage by remember { mutableStateOf<SavingsPlan?>(null) }
    val monthly = account.plans.filter { !it.paused }.sumOf { p ->
        when (p.interval) {
            PlanInterval.WEEKLY -> p.amount * 52 / 12
            PlanInterval.BIWEEKLY -> p.amount * 2
            PlanInterval.MONTHLY -> p.amount
            PlanInterval.QUARTERLY -> p.amount / 3
        }
    }

    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }) {
        LazyColumn(Modifier.fillMaxWidth(), state = liste) {
            item {
                ScreenHeader(
                    S.savingsPlans, if (account.plans.isEmpty()) S.noPlans else S.perMonthPlans(Fmt.euro(monthly, 0), account.plans.size), onBack,
                )
                Spacer(Modifier.height(12.dp))
            }
            items(account.plans, key = { it.id }) { plan ->
                val next = Trading.nextExecution(plan, System.currentTimeMillis())
                Row(
                    Modifier.fillMaxWidth().clickable { manage = plan }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstrumentLogo(state, plan.symbol, plan.name, size = 40)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        NamenText(plan.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (plan.paused) "${S.paused} · ${S.intervalLabel(plan.interval)}" else "${S.intervalLabel(plan.interval)} · ${S.nextOn(if (next <= 0) "–" else Fmt.dayMonth(next) + if (plan.uhrzeit >= 0) ", " + uhrzeitText(plan.uhrzeit) else "")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.euro(plan.amount, 0), style = MaterialTheme.typography.titleSmall)
                        Text(S.investedAmount(Fmt.euro(plan.invested, 0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
        FloatingPill(
            label = S.createPlan,
            plus = true,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp),
            onClick = onSearch,
        )
    }
    manage?.let { ManagePlanSheet(state, account.plans.firstOrNull { p -> p.id == it.id } ?: it, onDismiss = { manage = null }) }
}

// Waehlbare Uhrzeiten fuer Sparplaene, in Minuten nach Mitternacht.
private val PLAN_UHRZEITEN = listOf(8, 9, 10, 12, 14, 16, 18, 20).map { it * 60 }

// Uhrzeit eines Sparplans, z. B. „10:00 Uhr“; ohne Uhrzeit „Automatisch“.
fun uhrzeitText(minuten: Int): String =
    if (minuten !in 0 until 24 * 60) S.planUhrzeitAuto
    else S.planUhrzeitWert((minuten / 60).toString().padStart(2, '0') + ":" + (minuten % 60).toString().padStart(2, '0'))

// Automatisch oder eine feste Uhrzeit, dazu der Hinweis, wann ausgefuehrt wird.
@Composable
private fun UhrzeitWahl(gewaehlt: Int, onWahl: (Int) -> Unit) {
    OptionRow(S.planUhrzeitAuto, S.planUhrzeitAutoText, selected = gewaehlt !in 0 until 24 * 60) { onWahl(-1) }
    Spacer(Modifier.height(8.dp))
    PLAN_UHRZEITEN.chunked(4).forEach { reihe ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reihe.forEach { m ->
                val aktiv = m == gewaehlt
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip))
                        .background(if (aktiv) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onWahl(m) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (m / 60).toString().padStart(2, '0') + ":00", style = MaterialTheme.typography.labelMedium,
                        color = if (aktiv) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
    Text(
        S.planUhrzeitHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}
