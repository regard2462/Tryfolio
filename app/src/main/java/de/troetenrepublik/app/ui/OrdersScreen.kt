package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextAlign
import de.troetenrepublik.app.data.Validity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.i18n.S
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch

// Seite „Orders“ (5.93): Reiter Orders und Sparplaene. Orders zeigt die offenen Auftraege mit Gueltigkeit und
// Stornieren, darunter die Historie; Sparplaene die Liste aller Sparplaene. startSeite 1 oeffnet die Sparplaene.
@Composable
fun OrdersScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, onOpenDerivative: (String) -> Unit, onSearch: () -> Unit = {}, startSeite: Int = 0) {
    val account by state.account.collectAsStateWithLifecycle()
    val open = account.orders.filter { it.status == OrderStatus.OPEN }.sortedByDescending { it.createdAt }
    val done = account.orders.filter { it.status != OrderStatus.OPEN }.sortedByDescending { it.executedAt }

    // Offene Order in Bearbeitung
    var bearbeiten by remember { mutableStateOf<String?>(null) }
    // Offene Order, deren Stornieren noch bestaetigt werden muss
    var stornieren by remember { mutableStateOf<String?>(null) }

    val pager = rememberPagerState(initialPage = startSeite.coerceIn(0, 1)) { 2 }
    val scope = rememberCoroutineScope()
    val listen = remember { List(2) { LazyListState() } }
    val dismiss = rememberSwipeDownDismiss(onBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { !listen[pager.currentPage].canScrollBackward },
    ) {
        // Ohne Untertitel, damit die Reiter beim Wechseln nicht springen; die Zusammenfassung steht in jedem Reiter
        ScreenHeader(S.orders, null, onBack)
        MitlaufendeReiter(2, pager, titel = { if (it == 0) S.orders else S.savingsPlans }, modifier = Modifier.padding(vertical = 12.dp)) { i ->
            scope.launch { pager.animateScrollToPage(i) }
        }
        HorizontalPager(
            state = pager, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().weight(1f).mausWischen(pager),
        ) { seite ->
            if (seite == 1) PlaeneSeite(state, listen[1], onSearch)
            else LazyColumn(Modifier.fillMaxSize(), state = listen[0]) {
        item { ReiterHinweis(S.ordersOpenHint(open.size)) }
        item { SectionHeader(S.open) }
        if (open.isEmpty()) item { EmptyHint(S.noOpenOrders, S.search to onSearch) }
        items(open, key = { it.id }) { o ->
            OrderLine(
                o, onCancel = { stornieren = o.id },
                onEdit = if (o.type != OrderType.MARKET) ({ bearbeiten = o.id }) else null,
            ) { if (o.derivative != null) onOpenDerivative(o.key) else onOpen(o.symbol) }
        }
        if (done.isNotEmpty()) {
            item { SectionHeader(S.doneOrders) }
            items(done.take(50), key = { it.id }) { o ->
                OrderLine(o, onCancel = null) { if (o.derivative != null) onOpenDerivative(o.key) else onOpen(o.symbol) }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    val offen = bearbeiten?.let { id -> open.firstOrNull { it.id == id } }
    if (offen != null) OrderAendernSheet(state, offen, onDismiss = { bearbeiten = null }, onStornieren = { stornieren = offen.id })

    // Ein Tipp storniert nicht sofort: erst die Rueckfrage wie beim Loeschen eines Sparplans. Ist die Order inzwischen
    // ausgefuehrt, verschwindet die Frage.
    stornieren?.let { id -> open.firstOrNull { it.id == id } }?.let { o ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { stornieren = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.auStornierenTitel) },
            text = { Text(S.auStornierenText(o.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.cancelOrder(o.id)
                    stornieren = null
                    if (bearbeiten == o.id) bearbeiten = null
                }) { Text(S.cancelOrder) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { stornieren = null }) { Text(S.auBehalten) } },
        )
    }
}

// Kurze Zusammenfassung oben in einem Reiter der Seite Orders.
@Composable
internal fun ReiterHinweis(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun OrderLine(o: Order, onCancel: (() -> Unit)?, onEdit: (() -> Unit)? = null, onClick: () -> Unit) {
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(o.symbol, o.name, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NamenText(o.name, style = MaterialTheme.typography.titleSmall)
            PunktText(
                buildString {
                    append(if (o.side == Side.BUY) S.buy else S.sell)
                    append(" · ").append(Fmt.qty(o.qty)).append(" ").append(S.pieces)
                    when (o.type) {
                        OrderType.LIMIT -> append(" · ").append(S.limit).append(" ").append(Fmt.priceEuro(o.limitPrice))
                        OrderType.STOP -> append(" · ").append(S.stop).append(" ").append(Fmt.priceEuro(o.limitPrice))
                        OrderType.TRAILING_STOP -> append(" · ").append(S.trailingInfo(Fmt.pct(o.trailPct), Fmt.priceEuro(o.limitPrice)))
                        OrderType.MARKET -> {}
                    }
                    if (o.status == OrderStatus.OPEN) append(" · ").append(S.validityLabel(o.validity))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (o.status == OrderStatus.OPEN && o.expiresAt > 0) {
                Text(S.validUntil(Fmt.dateTime(o.expiresAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val reasonText = o.reason.takeIf { it.isNotBlank() }?.let { S.reason(it) } ?: o.note.takeIf { it.isNotBlank() && o.status != OrderStatus.OPEN }
            if (reasonText != null) {
                Text(reasonText, style = MaterialTheme.typography.bodySmall, color = if (o.status == OrderStatus.REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onCancel != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onEdit != null) Box(Modifier.auswahlChip(false, onClick = onEdit), contentAlignment = Alignment.Center) {
                    Text(S.orderAendern, style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.auswahlChip(false, onClick = onCancel), contentAlignment = Alignment.Center) {
                    Text(S.cancelOrder, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    S.orderStatus(o.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (o.status) {
                        OrderStatus.FILLED -> MaterialTheme.colorScheme.onSurface
                        OrderStatus.REJECTED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (o.executedAt > 0) Text(Fmt.dateTime(o.executedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (o.status == OrderStatus.FILLED && o.executedPrice > 0) Text(Fmt.priceEuro(o.executedPrice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Stueckzahl, Limit- oder Stoppreis oder Trailing-Abstand ueber das Ziffernfeld, dazu die Gueltigkeit.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderAendernSheet(state: AppState, o: Order, onDismiss: () -> Unit, onStornieren: () -> Unit) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val trailing = o.type == OrderType.TRAILING_STOP
    // 0 = Stueckzahl, 1 = Preis oder Abstand
    var feld by remember(o.id) { mutableStateOf(1) }
    var menge by remember(o.id) { mutableStateOf(toInput(o.qty, 6)) }
    var preis by remember(o.id) {
        mutableStateOf(if (trailing) toInput(o.trailPct, 2) else toInput(o.limitPrice, if (o.limitPrice >= 1) 2 else 4))
    }
    var gueltig by remember(o.id) { mutableStateOf(o.validity) }
    val qty = parseAmount(menge)
    val wert = parseAmount(preis)
    val gueltigeEingabe = qty > 0 && wert > 0 && (!trailing || wert <= 50)
    val geaendert = qty != o.qty || gueltig != o.validity || (if (trailing) wert != o.trailPct else wert != o.limitPrice)
    val preisName = when (o.type) { OrderType.STOP -> S.stopPrice; OrderType.TRAILING_STOP -> S.trailDistance; else -> S.limitPrice }
    val kurs = quotes[o.symbol]?.price?.takeIf { o.derivative == null } ?: 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentLogo(o.symbol, o.name, size = 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PunktText((if (o.side == Side.BUY) S.buy else S.sell) + " · " + preisName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NamenText(o.name, style = MaterialTheme.typography.titleMedium)
                }
                if (kurs > 0) Text(Fmt.priceEuro(kurs), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(S.quantity, feld == 0) { feld = 0 }
                Chip(preisName, feld == 1) { feld = 1 }
            }
            Spacer(Modifier.height(14.dp))
            BetragText(
                if (feld == 0) "${displayAmount(menge)}\u00A0${S.pieces}" else if (trailing) "${displayAmount(preis)}\u00A0%" else "${displayAmount(preis)}\u00A0€",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (feld == 0) "$preisName ${if (trailing) Fmt.pct(wert) else Fmt.priceEuro(wert)}"
                else "${S.quantity} ${Fmt.qty(qty)}" + if (kurs > 0 && !trailing && wert > 0) " · " + Fmt.signedPct((wert / kurs - 1) * 100) else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            Keypad(
                allowDecimal = feld == 1 || o.derivative == null,
                onDigit = { d ->
                    if (feld == 0) menge = appendDigit(menge, d, maxDecimals = 6)
                    else preis = appendDigit(preis, d, maxDecimals = if (trailing) 2 else 4)
                },
                onBackspace = { if (feld == 0) menge = menge.dropLast(1) else preis = preis.dropLast(1) },
            )
            Spacer(Modifier.height(14.dp))
            Text(S.validity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Validity.entries.forEach { v -> Chip(S.validityLabel(v), gueltig == v) { gueltig = v } }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingPill(label = S.cancelOrder, filled = false, modifier = Modifier.weight(1f)) { onStornieren() }
                FloatingPill(label = S.orderAendernSpeichern, enabled = gueltigeEingabe && geaendert, modifier = Modifier.weight(1f)) {
                    val p = if (trailing) o.limitPrice else wert
                    if (state.orderAendern(o.id, qty, p, if (trailing) wert else o.trailPct, gueltig)) onDismiss()
                }
            }
        }
    }
}
