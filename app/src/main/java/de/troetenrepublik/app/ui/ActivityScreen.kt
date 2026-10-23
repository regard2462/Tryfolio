package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.docs.Documents
import de.troetenrepublik.app.i18n.S
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Titel in der gewaehlten Sprache, aeltere Buchungen behalten ihren Text.
fun txTitleOf(tx: Transaction): String = when (tx.tag) {
    "FEE" -> S.txTitle("FEE", tx.title)
    "" -> tx.title
    else -> S.txTitle(tx.tag, tx.title).ifBlank { S.txLabel(tx.type) }
}

fun txSubtitleOf(tx: Transaction): String {
    if (tx.tag.isBlank()) return tx.subtitle
    if (tx.tag == "FEE") return tx.title
    val extra = when (tx.tag) {
        "INTEREST" -> Fmt.pct(tx.extra)
        "ALERT_ABOVE", "ALERT_BELOW" -> Fmt.priceEuro(tx.extra)
        "ALERT_MOVE" -> Fmt.signedPct(tx.extra)
        "PLAN_FAILED" -> Fmt.euro(tx.extra)
        else -> ""
    }
    return S.txSubtitle(tx.tag, tx.via, Fmt.qty(tx.qty), Fmt.priceEuro(tx.price), Fmt.signedEuro(tx.realized), extra, tx.subtitle)
}

// Alle Ereignisse des Depots nach Tag gruppiert. Jeder Eintrag oeffnet ein Blatt mit Einzelheiten und Abrechnung.
@Composable
fun ActivityScreen(
    state: AppState,
    onOpen: (String) -> Unit,
    onBack: () -> Unit = {},
    onOpenDerivative: (String) -> Unit,
    onDeposit: () -> Unit = {},
) {
    val account by state.account.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Transaction?>(null) }
    val scope = rememberCoroutineScope()
    // Die Reiter sind Seiten, zwischen denen man waagerecht wischt
    val filters = listOf(null, TxType.BUY, TxType.DEPOSIT, TxType.DIVIDEND)
    val labels = listOf(S.filterAll, S.filterTrade, S.filterCash, S.filterIncome)
    val pager = rememberPagerState { filters.size }
    val sorted = remember(account.transactions) { account.transactions.sortedByDescending { it.ts } }
    // Eigene Liste je Reiter, nach unten weggewischt wird nur, wenn sie oben steht
    val listen = remember { List(filters.size) { LazyListState() } }
    val dismiss = rememberSwipeDownDismiss(onBack)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { !listen[pager.currentPage].canScrollBackward },
    ) {
        ScreenHeader(S.activityTitle, null, onBack)
        MitlaufendeReiter(labels.size, pager, titel = { labels[it] }, modifier = Modifier.padding(vertical = 14.dp)) {
            scope.launch { pager.animateScrollToPage(it) }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1) { page ->
            val filter = filters[page]
            val transactions = remember(sorted, filter) { sorted.filter { matches(it, filter) } }
            val grouped = transactions.groupBy { Fmt.date(it.ts) }
            LazyColumn(Modifier.fillMaxSize(), state = listen[page]) {
                if (transactions.isEmpty()) item { EmptyHint(S.noActivity, if (account.transactions.isEmpty()) S.deposit to onDeposit else null) }
                grouped.forEach { (day, list) ->
                    item(key = "day-$day") {
                        Text(
                            day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { tx -> TimelineRow(tx) { selected = tx } }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    // Nach dem Wertpapier wieder die Buchung
    val rueck = rememberWiederkehr()
    selected?.let { tx ->
        if (!rueck.wartet) TransactionSheet(state, tx, onDismiss = { selected = null; rueck.vergessen() }, sofort = rueck.sofort, onOpen = {
            // Importierte Werte ohne Kurs (echte Derivate von Trade Republic) haben keine eigene Seite
            if (tx.symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS)) Unit
            else if (tx.key.isNotBlank() && tx.key != tx.symbol) { rueck.verlassen(); onOpenDerivative(tx.key) }
            else if (tx.symbol.isNotBlank()) { rueck.verlassen(); onOpen(tx.symbol) }
        })
        BlattBild(rueck, "buchung") { TransactionInhalt(state, tx, onOpen = {}) }
    }
}

// Filter "Handel" umfasst Kauf und Verkauf, "Cash" Ein- und Auszahlungen, "Ertraege" Dividenden und Zinsen.
private fun matches(tx: Transaction, filter: TxType?): Boolean = when (filter) {
    null -> true
    TxType.BUY -> tx.type == TxType.BUY || tx.type == TxType.SELL || tx.type == TxType.KNOCKOUT || tx.type == TxType.EXPIRY
    TxType.DEPOSIT -> tx.type == TxType.DEPOSIT || tx.type == TxType.WITHDRAWAL
    TxType.DIVIDEND -> tx.type == TxType.DIVIDEND || tx.type == TxType.INTEREST
    else -> tx.type == filter
}

@Composable
fun TimelineRow(tx: Transaction, onClick: (() -> Unit)? = null) {
    val signal = LocalSignal.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tx.symbol.isNotBlank() && tx.type != TxType.FEE) {
            InstrumentLogo(tx.symbol, tx.title, size = 38)
        } else {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(iconFor(tx.type), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(txTitleOf(tx), style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(S.txLabel(tx.type), txSubtitleOf(tx).takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (tx.amount != 0.0) {
                Text(
                    Fmt.signedEuro(tx.amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tx.amount > 0) signal.up else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(Fmt.time(tx.ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun iconFor(t: TxType): ImageVector = when (t) {
    TxType.DEPOSIT -> Icons.Filled.ArrowDownward
    TxType.WITHDRAWAL -> Icons.Filled.ArrowUpward
    TxType.INTEREST -> Icons.Filled.Percent
    TxType.FEE, TxType.TAX -> Icons.Filled.Receipt
    TxType.ALERT -> Icons.Filled.Notifications
    TxType.PLAN -> Icons.Filled.Autorenew
    TxType.KNOCKOUT, TxType.EXPIRY -> Icons.Filled.Bolt
    else -> Icons.Filled.Savings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(state: AppState, tx: Transaction, onDismiss: () -> Unit, onOpen: () -> Unit, sofort: Boolean = false) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        TransactionInhalt(state, tx, onOpen)
    }
}

// Auch als Bild unter dem geoeffneten Wertpapier, siehe Wiederkehr.
@Composable
fun TransactionInhalt(state: AppState, tx: Transaction, onOpen: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var doc by remember { mutableStateOf<Documents.Document?>(null) }
    val lines = Documents.lines(tx, settings.ownerName)
    run {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(S.txLabel(tx.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NamenText(txTitleOf(tx), style = MaterialTheme.typography.headlineSmall)
                }
                if (tx.amount != 0.0) Text(Fmt.signedEuro(tx.amount), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            lines.forEach { (k, v) -> KeyValueRow(k, v) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tx.symbol.isNotBlank()) FloatingPill(label = S.toSecurity, filled = false, modifier = Modifier.weight(1f), onClick = onOpen)
                if (Documents.hasDocument(tx)) {
                    FloatingPill(label = S.showDocument, modifier = Modifier.weight(1f)) { doc = Documents.settlement(tx, settings.ownerName) }
                }
            }
        }
    }
    doc?.let { DocumentSheet(it, onDismiss = { doc = null }) }
}
