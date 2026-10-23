package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi

// Anstehende Termine (Geschaeftszahlen mit erwartetem Gewinn je Aktie, Ex-Dividende, Zahltag) und vergangene
// (gezahlte Dividenden, Quartalszahlen mit Ist und Erwartung).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsSheet(state: AppState, symbol: String, name: String, profile: MarketApi.Profile?, onDismiss: () -> Unit) {
    var dividends by remember(symbol) { mutableStateOf<List<MarketApi.DividendEvent>>(emptyList()) }
    LaunchedEffect(symbol) { dividends = state.dividendsFor(symbol) }
    val now = System.currentTimeMillis()
    val ccy = Derivatives.currencySymbol(profile?.financialCurrency.orEmpty())

    data class Ev(val title: String, val ts: Long, val subtitle: String?)
    val upcoming = buildList {
        profile?.earningsDate?.takeIf { it > 0 }?.let { add(Ev(S.eventEarnings, it, profile.earningsEstimate.takeIf { e -> e != 0.0 }?.let { e -> S.expectedEps(Fmt.price(e, ccy)) })) }
        profile?.exDividendDate?.takeIf { it > now - 86_400_000L }?.let { add(Ev(S.eventExDividend, it, null)) }
        profile?.dividendDate?.takeIf { it > now - 86_400_000L }?.let { add(Ev(S.eventDividendPayment, it, null)) }
    }.sortedBy { it.ts }
    val past = buildList {
        dividends.filter { it.exDate <= now }.forEach { add(Ev(S.eventExDividend, it.exDate, Fmt.price(it.amount) + " · " + S.dividendPerShare)) }
        profile?.epsHistory.orEmpty().forEach { e ->
            val ts = runCatching { java.time.LocalDate.parse(e.label).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            if (ts != null) add(Ev(S.eventEarnings, ts, S.epsActualVsEstimate(Fmt.price(e.actual, ccy), Fmt.price(e.estimate, ccy))))
        }
    }.sortedByDescending { it.ts }.take(16)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(S.events, style = MaterialTheme.typography.headlineLarge)
                    NamenText(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionHeader(S.upcomingEvents) }
            if (upcoming.isEmpty()) item { EmptyHint(S.noEvents) }
            items(upcoming) { e -> EventRow(e.title, e.ts, subtitle = listOfNotNull(Fmt.untilDay(e.ts), e.subtitle).joinToString(" · ")) }
            if (past.isNotEmpty()) {
                item { SectionHeader(S.pastEvents) }
                items(past) { e -> EventRow(e.title, e.ts, subtitle = listOfNotNull(Fmt.date(e.ts), e.subtitle).joinToString(" · ")) }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
