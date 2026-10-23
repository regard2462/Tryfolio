package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.data.isFactor
import de.troetenrepublik.app.data.isKnockout
import de.troetenrepublik.app.i18n.S
import kotlin.math.floor

private enum class TradeStep { INPUT, REVIEW }
private enum class Editing { AMOUNT, LIMIT, TRAIL }

// Ordermaske in zwei Schritten: erst Betrag oder Stueckzahl, Ordertyp und bei Limit und Stop die Gueltigkeit,
// dann die Uebersicht mit Gegenwert, Fremdkostenpauschale, Handelsplatz und bei Hebelprodukten Produktkosten und
// Risikohinweis. Ausserhalb der Handelszeiten sind Market-Orders gesperrt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSheet(
    title: String,
    subtitle: String,
    price: Double,
    side: Side,
    cash: Double,
    ownedQty: Double,
    allowFractional: Boolean,
    derivative: Derivative?,
    marketOpen: Boolean = true,
    hoursHint: String = "",
    allowOrders: Boolean = true,
    onDismiss: () -> Unit,
    onMarket: (qty: Double, note: String) -> Boolean,
    onLimit: (qty: Double, limit: Double, type: OrderType, validity: Validity, note: String, trailPct: Double) -> Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = MaterialTheme.colorScheme.onSurface

    var step by rememberSaveable { mutableStateOf(TradeStep.INPUT) }
    var byAmount by rememberSaveable { mutableStateOf(side == Side.BUY && allowFractional) }
    var input by rememberSaveable { mutableStateOf("") }
    var orderType by rememberSaveable { mutableStateOf(OrderType.MARKET) }
    var validity by rememberSaveable { mutableStateOf(Validity.DAY) }
    var limitInput by rememberSaveable { mutableStateOf("") }
    var trailInput by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(Editing.AMOUNT) }
    var note by rememberSaveable { mutableStateOf("") }
    var submitError by remember { mutableStateOf<String?>(null) }

    val limitValue = parseAmount(limitInput)
    val trailValue = parseAmount(trailInput)
    val qty = remember(input, byAmount, price, allowFractional) {
        val v = parseAmount(input)
        when {
            price <= 0 -> 0.0
            byAmount -> if (allowFractional) v / price else floor(v / price)
            else -> v
        }
    }
    val isTrailing = orderType == OrderType.TRAILING_STOP
    val initialStop = if (isTrailing && trailValue > 0) (if (side == Side.SELL) price * (1 - trailValue / 100) else price * (1 + trailValue / 100)) else 0.0
    val estimate = if (orderType == OrderType.LIMIT && limitValue > 0) limitValue else if (isTrailing && initialStop > 0) initialStop else price
    val total = qty * estimate
    val enough = if (side == Side.BUY) total + 1.0 <= cash + 1e-9
        else qty <= ownedQty + 1e-9 && cash + total >= 1.0
    val limitOk = when (orderType) {
        OrderType.MARKET -> true
        OrderType.TRAILING_STOP -> trailValue.isFinite() && trailValue > 0 && trailValue <= 50
        else -> limitValue.isFinite() && limitValue > 0
    }
    val quantityOk = qty.isFinite() && qty > 0 && (allowFractional || qty == floor(qty))
    val hoursOk = marketOpen || orderType != OrderType.MARKET
    val canContinue = quantityOk && price.isFinite() && price > 0 && total.isFinite() && enough && limitOk && hoursOk

    fun selectType(type: OrderType) {
        if (type != OrderType.MARKET && byAmount) { byAmount = false; input = "" }
        orderType = type
        editing = when (type) { OrderType.MARKET -> Editing.AMOUNT; OrderType.TRAILING_STOP -> Editing.TRAIL; else -> Editing.LIMIT }
    }

    val sideLabel = if (side == Side.BUY) S.buy else S.sell

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        dragHandle = null,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 24.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (step == TradeStep.REVIEW) S.overview else sideLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NamenText(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Fmt.priceEuro(price), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(20.dp))

            if (step == TradeStep.INPUT) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when (editing) {
                            Editing.LIMIT -> displayAmount(limitInput) + " €"
                            Editing.TRAIL -> displayAmount(trailInput) + " %"
                            Editing.AMOUNT -> if (byAmount) displayAmount(input) + " €" else (input.ifEmpty { "0" }) + " " + S.pieces
                        },
                        style = MaterialTheme.typography.displayLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (editing) {
                        Editing.LIMIT -> if (orderType == OrderType.STOP) S.enterStop else S.enterLimit
                        Editing.TRAIL -> S.enterTrail
                        Editing.AMOUNT -> if (byAmount) S.corresponds(Fmt.qty(qty)) else S.correspondsAmount(Fmt.euro(total))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (allowFractional && orderType == OrderType.MARKET) {
                        Chip(S.amount, byAmount && editing == Editing.AMOUNT, accent) { if (!byAmount) input = ""; byAmount = true; editing = Editing.AMOUNT; submitError = null }
                    }
                    Chip(S.shares, !byAmount && editing == Editing.AMOUNT, accent) { if (byAmount) input = ""; byAmount = false; editing = Editing.AMOUNT; submitError = null }
                    Spacer(Modifier.weight(1f))
                    if (side == Side.SELL && ownedQty > 0) {
                        Chip(S.everything, false, accent) {
                            byAmount = false; editing = Editing.AMOUNT
                            input = java.math.BigDecimal.valueOf(ownedQty).stripTrailingZeros().toPlainString().replace('.', ',')
                        }
                    }
                }

                if (allowOrders) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScrollMaus(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(S.market, orderType == OrderType.MARKET, accent) { selectType(OrderType.MARKET) }
                        Chip(S.limit, orderType == OrderType.LIMIT, accent) { selectType(OrderType.LIMIT) }
                        Chip(S.stop, orderType == OrderType.STOP, accent) { selectType(OrderType.STOP) }
                        Chip(S.trailingStop, isTrailing, accent) { selectType(OrderType.TRAILING_STOP) }
                    }
                }

                if (orderType != OrderType.MARKET) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { editing = if (isTrailing) Editing.TRAIL else Editing.LIMIT }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (orderType) { OrderType.LIMIT -> S.limitPrice; OrderType.STOP -> S.stopPrice; else -> S.trailDistance },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (isTrailing) (if (trailValue > 0) Fmt.pct(trailValue) + " · " + Fmt.priceEuro(initialStop) else S.setValue)
                            else (if (limitValue > 0) Fmt.priceEuro(limitValue) else S.setValue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (editing != Editing.AMOUNT) accent else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(S.validity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Validity.entries.forEach { v -> Chip(S.validityLabel(v), validity == v, accent) { validity = v } }
                    }
                    Text(
                        when (orderType) {
                            OrderType.LIMIT -> if (side == Side.BUY) S.limitBuyHint else S.limitSellHint
                            OrderType.STOP -> if (side == Side.BUY) S.stopBuyHint else S.stopSellHint
                            else -> if (side == Side.BUY) S.trailingBuyHint else S.trailingSellHint
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!marketOpen && orderType == OrderType.MARKET) {
                    Text(
                        hoursHint.ifBlank { S.closed } + S.closedOrderHint,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Keypad(
                    allowDecimal = editing != Editing.AMOUNT || allowFractional,
                    onDigit = { d ->
                        submitError = null
                        when (editing) {
                            Editing.LIMIT -> limitInput = appendDigit(limitInput, d, 4)
                            Editing.TRAIL -> trailInput = appendDigit(trailInput, d, 2)
                            Editing.AMOUNT -> input = appendDigit(input, d, 4)
                        }
                    },
                    onBackspace = {
                        when (editing) {
                            Editing.LIMIT -> limitInput = limitInput.dropLast(1)
                            Editing.TRAIL -> trailInput = trailInput.dropLast(1)
                            Editing.AMOUNT -> input = input.dropLast(1)
                        }
                    },
                )

                Spacer(Modifier.height(14.dp))
                KeyValueRow(if (side == Side.BUY) S.askPrice else S.bidPrice, Fmt.priceEuro(price))
                if (side == Side.BUY) KeyValueRow(S.availableCash, Fmt.euro(cash))
                else KeyValueRow(S.inDepot, "${Fmt.qty(ownedQty)} ${S.pieces}")

                if (!enough && qty > 0) {
                    Text(
                        if (side == Side.BUY) S.notEnoughCash
                        else if (qty > ownedQty + 1e-9) S.notEnoughShares
                        else S.notEnoughForFee,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FloatingPill(label = S.next, enabled = canContinue, modifier = Modifier.fillMaxWidth()) { step = TradeStep.REVIEW }
            } else {
                // Uebersicht
                Text(
                    if (orderType == OrderType.MARKET) Fmt.euro(total) else "${Fmt.qty(qty)} ${S.pieces}",
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                KeyValueRow(S.tradeType, S.tradeTypeValue(sideLabel, orderType))
                KeyValueRow(S.piecesLabel, Fmt.qty(qty))
                when (orderType) {
                    OrderType.MARKET -> KeyValueRow(if (side == Side.BUY) S.askPrice else S.bidPrice, Fmt.priceEuro(price))
                    OrderType.LIMIT -> KeyValueRow(S.limitPrice, Fmt.priceEuro(limitValue))
                    OrderType.STOP -> KeyValueRow(S.stopPrice, Fmt.priceEuro(limitValue))
                    OrderType.TRAILING_STOP -> { KeyValueRow(S.trailDistance, Fmt.pct(trailValue)); KeyValueRow(S.initialStop, Fmt.priceEuro(initialStop)) }
                }
                KeyValueRow(if (orderType == OrderType.MARKET) S.counterValue else S.estCounterValue, Fmt.euro(total))
                KeyValueRow(S.fee, Fmt.euro(1.0))
                KeyValueRow(S.venue, S.bestPrice)
                if (orderType != OrderType.MARKET) KeyValueRow(S.validity, S.validityLabel(validity))
                if (derivative != null) {
                    val productCost = when {
                        derivative.kind.isKnockout -> 2.5
                        derivative.kind.isFactor -> 1.5 + kotlin.math.abs(derivative.factor) * 0.1
                        else -> 1.0
                    }
                    KeyValueRow(S.productCosts, Fmt.pct(productCost))
                    Text(
                        S.derivativeRisk,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                if (side == Side.BUY) KeyValueRow(S.cashAfter, Fmt.euro(cash - total - 1.0))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    placeholder = { Text(S.notePlaceholder) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.field),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                submitError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { step = TradeStep.INPUT }
                    FloatingPill(
                        label = if (orderType != OrderType.MARKET) S.placeOrder else sideLabel,
                        enabled = canContinue,
                        modifier = Modifier.weight(1f),
                    ) {
                        val success = if (orderType == OrderType.MARKET) onMarket(qty, note.trim())
                        else onLimit(qty, limitValue, orderType, validity, note.trim(), trailValue)
                        if (success) onDismiss()
                        else submitError = S.orderNotExecuted
                    }
                }
            }
        }
    }
}
