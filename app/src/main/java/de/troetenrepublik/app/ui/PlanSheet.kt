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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.engine.SparplanVorgabe
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class PlanStep { AMOUNT, REVIEW }

// Was in der Uebersicht gerade zum Aendern aufgeklappt ist.
private enum class PlanWahl { TAG, UHRZEIT }

// Sparplan anlegen in zwei Schritten: Betrag und Intervall, dann die Uebersicht mit vorbelegtem Tag (2.)
// und automatischer Uhrzeit; beides laesst sich dort aendern, danach Erstellen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(PlanStep.AMOUNT) }
    var input by rememberSaveable { mutableStateOf("") }
    var interval by rememberSaveable { mutableStateOf(PlanInterval.MONTHLY) }
    var day by rememberSaveable { mutableStateOf(SparplanVorgabe.TAG) }
    var uhrzeit by rememberSaveable { mutableStateOf(SparplanVorgabe.UHRZEIT) }
    var wahl by rememberSaveable { mutableStateOf<PlanWahl?>(null) }
    // Dividenden wiederanlegen: erst beim Erstellen speichern, null heisst noch unberuehrt
    var wiederanlage by rememberSaveable { mutableStateOf<Boolean?>(null) }
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
                    BetragText("${displayAmount(input)}\u00A0€", style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25.0, 50.0, 100.0, 250.0).forEach { v ->
                            val aktiv = amount == v
                            Box(
                                Modifier.weight(1f).auswahlChip(aktiv, horizontal = 4.dp) { input = v.toInt().toString() },
                                contentAlignment = Alignment.Center,
                            ) { Text(Fmt.euro(v, 0), style = MaterialTheme.typography.labelLarge, color = auswahlChipSchrift(aktiv)) }
                        }
                    }
                    // Intervall gleich hier, als Reihe zum Wischen
                    Text(S.interval, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanInterval.entries.forEach { i -> Chip(S.intervalLabel(i), interval == i) { interval = i } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Keypad(allowDecimal = true, onDigit = { input = appendDigit(input, it) }, onBackspace = { input = input.dropLast(1) })
                    Spacer(Modifier.height(18.dp))
                    FloatingPill(label = S.next, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                        // Ein Tag, den es beim neuen Intervall nicht gibt, faellt auf die Vorgabe zurueck
                        day = SparplanVorgabe.tagFuer(interval, day)
                        wahl = null
                        step = PlanStep.REVIEW
                    }
                }
                PlanStep.REVIEW -> {
                    val preview = SavingsPlan("preview", symbol, name, amount, day, createdAt = System.currentTimeMillis(), interval = interval, uhrzeit = uhrzeit)
                    val next = Trading.nextExecution(preview, System.currentTimeMillis())
                    val tage = SparplanVorgabe.tage(interval)
                    Text(S.overview, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow(S.savingsRate, Fmt.euro(amount), onClick = { step = PlanStep.AMOUNT })
                    KeyValueRow(S.interval, S.intervalLabel(interval), onClick = { step = PlanStep.AMOUNT })
                    if (tage.isNotEmpty()) {
                        KeyValueRow(S.executionDay, S.onDay(day), onClick = { wahl = if (wahl == PlanWahl.TAG) null else PlanWahl.TAG })
                        if (wahl == PlanWahl.TAG) tage.forEach { t ->
                            val titel = when (t) {
                                2 -> S.startOfMonth
                                16 -> S.midOfMonth
                                else -> S.onDay(t).replaceFirstChar { it.uppercase() }
                            }
                            OptionRow(titel, if (t == 2 || t == 16) S.onDay(t) else "", selected = day == t) { day = t; wahl = null }
                        }
                    }
                    if (interval == PlanInterval.BIWEEKLY) KeyValueRow(S.executionDays, S.intervalBiweeklyDays)
                    KeyValueRow(S.planUhrzeit, uhrzeitText(uhrzeit), onClick = { wahl = if (wahl == PlanWahl.UHRZEIT) null else PlanWahl.UHRZEIT })
                    if (wahl == PlanWahl.UHRZEIT) {
                        UhrzeitWahl(uhrzeit) { uhrzeit = it; wahl = null }
                        Spacer(Modifier.height(8.dp))
                    }
                    KeyValueRow(S.firstExecution, if (next <= 0) "–" else if (uhrzeit >= 0) Fmt.dateTime(next) else Fmt.date(next))
                    KeyValueRow(S.fee, Fmt.euro(0.0))
                    KeyValueRow(S.paymentMethod, S.cashAccount)
                    WiederanlageZeile(state, symbol, wiederanlage) { wiederanlage = it }
                    // Erklaerung hinter dem Info-Symbol; das Blatt hat schon 20 dp Rand
                    HinweisInfo(S.planNote, kurz = hinweisText("annahmen"), modifier = Modifier.offset(x = (-20).dp))
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { step = PlanStep.AMOUNT }
                        FloatingPill(label = S.create, modifier = Modifier.weight(1f)) {
                            if (state.addPlan(symbol, name, amount, day, interval, uhrzeit)) {
                                wiederanlage?.let { state.wiederanlageSetzen(symbol, it) }
                                onDismiss()
                            }
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
                    BetragText("${displayAmount(input)}\u00A0€", style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth())
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
                        OptionRow(S.intervalLabel(i), "", selected = plan.interval == i) { state.updatePlan(plan.id, interval = i); editInterval = false }
                    }
                    Spacer(Modifier.height(12.dp))
                    FloatingPill(label = S.cancel, filled = false, modifier = Modifier.fillMaxWidth()) { editInterval = false }
                }
                else -> {
                    KeyValueRow(S.savingsRate, Fmt.euro(plan.amount))
                    KeyValueRow(S.interval, S.intervalLabel(plan.interval))
                    if (plan.interval == PlanInterval.MONTHLY || plan.interval == PlanInterval.QUARTERLY) KeyValueRow(S.executionDay, S.onDay(plan.dayOfMonth))
                    KeyValueRow(S.status, if (plan.paused) S.paused else S.active)
                    KeyValueRow(S.planUhrzeit, uhrzeitText(plan.uhrzeit))
                    KeyValueRow(S.nextExecution, if (next <= 0) "–" else if (plan.uhrzeit >= 0) Fmt.dateTime(next) else Fmt.date(next))
                    KeyValueRow(S.executions, plan.executions.toString())
                    KeyValueRow(S.investedSoFar, Fmt.euro(plan.invested))
                    if (plan.lastFailed > 0) KeyValueRow(S.lastFailure, Fmt.date(plan.lastFailed), valueColor = MaterialTheme.colorScheme.error)
                    WiederanlageZeile(state, plan.symbol)
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

// Monatliche Summe der laufenden Sparplaene.
internal fun planMonatlich(plans: List<SavingsPlan>): Double = plans.filter { !it.paused }.sumOf { p ->
    when (p.interval) {
        PlanInterval.WEEKLY -> p.amount * 52 / 12
        PlanInterval.BIWEEKLY -> p.amount * 2
        PlanInterval.MONTHLY -> p.amount
        PlanInterval.QUARTERLY -> p.amount / 3
    }
}

// Reiter „Sparplaene“ der Seite Orders: Liste aller Sparplaene, der Knopf fuehrt zur Auswahl eines Werts.
@Composable
internal fun PlaeneSeite(state: AppState, liste: LazyListState, onSearch: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    var manage by remember { mutableStateOf<SavingsPlan?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxWidth(), state = liste) {
            item {
                ReiterHinweis(if (account.plans.isEmpty()) S.noPlans else S.perMonthPlans(Fmt.euro(planMonatlich(account.plans), 0), account.plans.size))
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(account.plans, key = { _, p -> p.id }) { zeile, plan ->
                val next = Trading.nextExecution(plan, System.currentTimeMillis())
                Column {
                if (zeile > 0) Trennlinie()
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
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
        FloatingPill(
            label = S.createPlan,
            plus = true,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = 24.dp),
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
                Box(Modifier.weight(1f).auswahlChip(aktiv, horizontal = 4.dp) { onWahl(m) }, contentAlignment = Alignment.Center) {
                    Text((m / 60).toString().padStart(2, '0') + ":00", style = MaterialTheme.typography.labelMedium, color = auswahlChipSchrift(aktiv))
                }
            }
        }
    }
    HinweisInfo(S.planUhrzeitHinweis, modifier = Modifier.padding(top = 6.dp).offset(x = (-20).dp))
}
