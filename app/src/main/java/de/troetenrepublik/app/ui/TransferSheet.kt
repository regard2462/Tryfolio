package de.troetenrepublik.app.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Einzahlplaene
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus

private enum class TransferStep { CHOOSE, AMOUNT, REVIEW, DONE, PLAN }
private enum class TransferKind { DEPOSIT, WITHDRAW }

// Blatt „Überweisung“: Einzahlen oder Auszahlen, Betrag ueber das Ziffernfeld, Uebersicht zum Pruefen,
// bei Auszahlungen die PIN.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(state: AppState, onDismiss: () -> Unit, initial: String? = null) {
    val account by state.account.collectAsStateWithLifecycle()

    var kind by rememberSaveable { mutableStateOf(initial?.let { if (it == "withdraw") TransferKind.WITHDRAW else TransferKind.DEPOSIT }) }
    var step by rememberSaveable { mutableStateOf(if (kind == null) TransferStep.CHOOSE else TransferStep.AMOUNT) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Monatliche Einzahlung (seit 5.91): Tag im Monat und ob gerade bearbeitet wird
    var ezTag by rememberSaveable { mutableStateOf(account.einzahlplan?.tag ?: 1) }
    var ezBearbeiten by rememberSaveable { mutableStateOf(false) }

    val amount = parseAmount(input)
    val withdrawable = state.withdrawable()
    val valid = amount > 0 && amount.isFinite() && (kind == TransferKind.DEPOSIT || amount <= withdrawable + 1e-9)
    fun title(k: TransferKind) = if (k == TransferKind.DEPOSIT) S.deposit else S.withdraw

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            when (step) {
                TransferStep.CHOOSE -> {
                    Text(S.transfer, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        S.availableAmount(Fmt.euro(account.cash)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                    )
                    ChoiceCard(title = S.deposit, subtitle = S.depositSubtitle, icon = Icons.Filled.ArrowDownward) { kind = TransferKind.DEPOSIT; step = TransferStep.AMOUNT }
                    Spacer(Modifier.height(10.dp))
                    ChoiceCard(
                        title = S.withdraw,
                        subtitle = S.withdrawToReference,
                        icon = Icons.Filled.ArrowUpward,
                    ) { kind = TransferKind.WITHDRAW; step = TransferStep.AMOUNT }
                    Spacer(Modifier.height(10.dp))
                    val plan = account.einzahlplan
                    ChoiceCard(
                        title = S.ezTitel,
                        subtitle = when {
                            plan == null -> S.ezUntertitel
                            plan.pausiert -> S.paused
                            else -> S.ezAktiv(Fmt.euro(plan.betrag), S.ezTagWert(plan.tag))
                        },
                        icon = Icons.Filled.Autorenew,
                    ) {
                        input = plan?.betrag?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',') } ?: ""
                        ezTag = plan?.tag ?: 1
                        ezBearbeiten = plan == null
                        step = TransferStep.PLAN
                    }
                }

                TransferStep.PLAN -> {
                    val plan = account.einzahlplan
                    Text(S.ezTitel, style = MaterialTheme.typography.headlineLarge)
                    AufklappText(S.ezText, Modifier.padding(top = 4.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    if (plan != null && !ezBearbeiten) {
                        val jetzt = System.currentTimeMillis()
                        KeyValueRow(S.amount, Fmt.euro(plan.betrag))
                        KeyValueRow(S.ezTag, S.ezTagWert(plan.tag))
                        KeyValueRow(S.status, if (plan.pausiert) S.paused else S.ezNaechste(Fmt.date(Einzahlplaene.naechste(plan, jetzt))))
                        KeyValueRow(S.ezBisher, S.ezBisherWert(plan.ausfuehrungen, Fmt.euro(plan.summe)))
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FloatingPill(label = if (plan.pausiert) S.resume else S.pause, filled = false, modifier = Modifier.weight(1f)) {
                                state.einzahlplanPausieren(!plan.pausiert)
                            }
                            FloatingPill(label = S.change, modifier = Modifier.weight(1f)) { ezBearbeiten = true }
                        }
                        Text(
                            S.ezLoeschen, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(Radius.chip)).clickable { state.einzahlplanLoeschen(); onDismiss() }.padding(6.dp),
                        )
                    } else {
                        BetragText(
                            "${displayAmount(input)}\u00A0€", style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(S.ezTag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                        Row(Modifier.horizontalScrollMaus(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 5, 10, 15, 20, 25, 31).forEach { t -> Chip(S.ezTagWert(t), ezTag == t) { ezTag = t } }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(50.0, 100.0, 250.0, 500.0).forEach { v ->
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { input = java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(Fmt.euro(v, 0), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Keypad(allowDecimal = true, onDigit = { input = appendDigit(input, it) }, onBackspace = { input = input.dropLast(1) })
                        Spacer(Modifier.height(18.dp))
                        FloatingPill(label = S.save, enabled = amount > 0 && amount.isFinite(), modifier = Modifier.fillMaxWidth()) {
                            if (state.einzahlplanSetzen(amount, ezTag)) ezBearbeiten = false
                        }
                    }
                }

                TransferStep.AMOUNT -> {
                    val k = kind ?: TransferKind.DEPOSIT
                    Text(title(k), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (k == TransferKind.DEPOSIT) S.enterAmount else S.availableForWithdrawal(Fmt.euro(withdrawable)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    BetragText(
                        "${displayAmount(input)}\u00A0€",
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (amount > 0 && !valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (k == TransferKind.WITHDRAW && amount > withdrawable + 1e-9) {
                        Text(
                            S.moreThanAvailable, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = if (k == TransferKind.DEPOSIT) listOf(100.0, 500.0, 1_000.0, 5_000.0)
                        else listOf(50.0, 100.0, 500.0, withdrawable)
                        presets.filter { it > 0 }.distinct().forEach { v ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { input = java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString().replace('.', ',') }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (v == withdrawable && k == TransferKind.WITHDRAW) S.everything else Fmt.euro(v, 0), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Keypad(allowDecimal = true, onDigit = { input = appendDigit(input, it) }, onBackspace = { input = input.dropLast(1) })
                    Spacer(Modifier.height(18.dp))
                    FloatingPill(label = S.next, enabled = valid, modifier = Modifier.fillMaxWidth()) { step = TransferStep.REVIEW }
                    // Weg zur monatlichen Einzahlung, auch wenn das Blatt direkt beim Betrag beginnt
                    if (k == TransferKind.DEPOSIT) {
                        Text(
                            S.ezTitel, style = MaterialTheme.typography.titleSmall, color = LocalSignal.current.link,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(Radius.chip)).clickable {
                                ezTag = account.einzahlplan?.tag ?: 1
                                ezBearbeiten = account.einzahlplan == null
                                step = TransferStep.PLAN
                            }.padding(8.dp),
                        )
                    }
                }

                TransferStep.REVIEW -> {
                    val k = kind ?: TransferKind.DEPOSIT
                    Text(S.overview, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(18.dp))
                    Text(Fmt.euro(amount), style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(18.dp))
                    KeyValueRow(S.process, title(k))
                    if (k == TransferKind.DEPOSIT) {
                        KeyValueRow(S.from, S.virtualFunds)
                        KeyValueRow(S.to, S.cashAccount)
                        KeyValueRow(S.availableAfter, Fmt.euro(account.cash + amount))
                    } else {
                        KeyValueRow(S.from, S.cashAccount)
                        KeyValueRow(S.arrival, S.bankDays)
                        KeyValueRow(S.availableAfter, Fmt.euro(account.cash - amount))
                    }
                    KeyValueRow(S.feeLabel, S.free)
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { step = TransferStep.AMOUNT }
                        FloatingPill(label = if (k == TransferKind.DEPOSIT) S.deposit else S.send, modifier = Modifier.weight(1f)) {
                            if (k == TransferKind.DEPOSIT) {
                                state.deposit(amount); step = TransferStep.DONE
                            } else {
                                if (state.withdraw(amount)) step = TransferStep.DONE else error = S.withdrawFailed
                            }
                        }
                    }
                }

                TransferStep.DONE -> {
                    val k = kind ?: TransferKind.DEPOSIT
                    Spacer(Modifier.height(30.dp))
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center,
                    ) { CheckMark(size = 28.dp) }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (k == TransferKind.DEPOSIT) S.depositBooked else S.withdrawalSent,
                        style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (k == TransferKind.DEPOSIT) S.depositAvailableNow(Fmt.euro(amount)) else S.withdrawalArrives(Fmt.euro(amount)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    FloatingPill(label = S.done, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ChevronRight(height = 14.dp)
    }
}

