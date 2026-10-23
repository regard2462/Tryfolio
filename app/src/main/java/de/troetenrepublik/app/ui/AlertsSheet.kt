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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.i18n.S
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Preisalarme zu einem Wert: bestehende Alarme mit Loeschen, darunter das Anlegen ueber das Ziffernfeld.
// Ob der Alarm ueber oder unter gilt, ergibt sich aus der Marke im Vergleich zum aktuellen Kurs. Seit 5.4 auch als
// Alarm auf die Tagesbewegung in Prozent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsSheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val price = quotes[symbol]?.price ?: 0.0
    val mine = account.alerts.filter { it.symbol == symbol }
    var creating by remember { mutableStateOf(mine.isEmpty()) }
    var input by remember { mutableStateOf("") }
    var bewegung by remember { mutableStateOf(false) }
    val target = parseAmount(input)
    val bewegungOk = bewegung && target in 0.5..50.0

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
                    Text(S.priceAlerts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NamenText(name, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.weight(1f))
                Text(Fmt.priceEuro(price), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(18.dp))

            if (!creating) {
                mine.forEach { a -> AlertRow(a, onDelete = { state.removeAlert(a.id) }) }
                Spacer(Modifier.height(16.dp))
                FloatingPill(label = S.createAlert, plus = true, modifier = Modifier.fillMaxWidth()) { creating = true }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to S.alertArtMarke, true to S.alertArtBewegung).forEach { (art, titel) ->
                        val an = bewegung == art
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip))
                                .background(if (an) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { if (!an) { bewegung = art; input = "" } }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(titel, style = MaterialTheme.typography.labelMedium, color = if (an) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
                Text("${displayAmount(input)} ${if (bewegung) "%" else "€"}", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    when {
                        bewegung && target <= 0 -> S.enterMovePct
                        bewegung -> if (bewegungOk) S.alertMove(Fmt.pct(target, 1)) else S.alertMoveUngueltig
                        target <= 0 -> S.enterTargetPrice
                        target > price -> S.alertRises(Fmt.priceEuro(target), Fmt.pct((target / price - 1) * 100))
                        target < price -> S.alertFalls(Fmt.priceEuro(target), Fmt.pct((1 - target / price) * 100))
                        else -> S.targetEqualsPrice
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (bewegung) listOf(2, 3, 5, 10) else listOf(-10, -5, 5, 10)).forEach { pct ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { input = if (bewegung) pct.toString() else toInput(price * (1 + pct / 100.0)) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (bewegung) "±$pct %" else (if (pct > 0) "+" else "−") + "${kotlin.math.abs(pct)} %", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Keypad(allowDecimal = true, onDigit = { input = appendDigit(input, it, maxDecimals = if (bewegung) 1 else 4) }, onBackspace = { input = input.dropLast(1) })
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mine.isNotEmpty()) FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { creating = false }
                    FloatingPill(label = S.setAlert, enabled = if (bewegung) bewegungOk else target > 0 && target != price && price > 0, modifier = Modifier.weight(1f)) {
                        val ok = if (bewegung) state.addMoveAlert(symbol, name, target) else state.addAlert(symbol, name, target)
                        if (ok) { input = ""; creating = false }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AlertRow(a: PriceAlert, onDelete: () -> Unit, showName: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showName) {
            InstrumentLogo(a.symbol, a.name, size = 36)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            val bewegung = a.tagesProzent > 0
            val marke = if (bewegung) S.alertMoveRow(Fmt.pct(a.tagesProzent, 1)) else Fmt.priceEuro(a.target)
            NamenText(if (showName) a.name else marke, style = MaterialTheme.typography.titleSmall)
            Text(
                (if (showName) "$marke · " else "") + (if (bewegung) S.alertMoveWiederholt else if (a.above) S.whenRises else S.whenFalls) +
                    (if (a.referencePrice > 0 && !bewegung) " · " + S.setAt(Fmt.priceEuro(a.referencePrice)) else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onDelete).padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text(S.delete, style = MaterialTheme.typography.labelSmall) }
    }
}

// Alle Preisalarme, erreichbar aus dem Profil.
@Composable
fun AlertsScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item {
            ScreenHeader(S.priceAlerts, S.alertCount(account.alerts.size), onBack)
            Spacer(Modifier.height(10.dp))
        }
        if (account.alerts.isEmpty()) item { EmptyHint(S.alertsHint) }
        items(account.alerts, key = { it.id }) { a ->
            Box(Modifier.padding(horizontal = 20.dp)) {
                AlertRow(a, onDelete = { state.removeAlert(a.id) }, showName = true, onClick = { onOpen(a.symbol) })
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}
