package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.launch
import kotlin.math.abs

// Finanzen: Umsatz und Gewinn je Jahr oder Quartal als Balken, darunter Gewinn und Verlust, Margen, Bilanz,
// Cashflow und die Quartalsgewinne je Aktie. Jaehrlich und Quartal sind zwei Seiten zum Wischen; die Pillen folgen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialsSheet(name: String, p: MarketApi.Profile, onDismiss: () -> Unit) {
    val ccy = p.financialCurrency.ifBlank { "" }
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(S.financialsTitle, style = MaterialTheme.typography.headlineLarge)
                Text(name + if (ccy.isNotBlank()) " · " + S.reportingCurrency(ccy) else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MitlaufendeReiter(2, pager, titel = { if (it == 0) S.yearly else S.quarterly }, modifier = Modifier.padding(vertical = 14.dp)) {
                scope.launch { pager.animateScrollToPage(it) }
            }
            HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).mausWischen(pager)) { page ->
                FinancialsPage(p, quarterly = page == 1, ccy = ccy)
            }
        }
    }
}

// Eine Seite der Finanzen: Balken und Kennzahlen fuer Jahre oder Quartale.
@Composable
private fun FinancialsPage(p: MarketApi.Profile, quarterly: Boolean, ccy: String) {
    val signal = LocalSignal.current
    val periods = if (quarterly) p.quarterly else p.yearly
    val latest = periods.lastOrNull()
    val prev = periods.dropLast(1).lastOrNull()
    fun g(a: Double?, b: Double?): Double? = if (a != null && b != null && b != 0.0) (a / abs(b) - 1.0) * 100.0 else null
    val revenueColor = signal.link
    val earningsColor = signal.up

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (periods.isEmpty()) {
                EmptyHint(S.noFinancials)
            } else {
                BarChart(
                    values = periods.map { it.revenue },
                    secondary = periods.map { it.earnings },
                    labels = periods.map { shortLabel(it.label) },
                    color = revenueColor, secondaryColor = earningsColor,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 20.dp),
                )
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(revenueColor, S.revenue); Legend(earningsColor, S.netIncome)
                }
            }
        }
        if (latest != null) {
            item {
                SectionHeader(S.income)
                GrowthRow(S.revenue, latest.revenue, g(latest.revenue, prev?.revenue), ccy)
                if (latest.grossProfit != 0.0) GrowthRow(S.grossProfit, latest.grossProfit, g(latest.grossProfit, prev?.grossProfit), ccy)
                if (latest.operatingIncome != 0.0) GrowthRow(S.operatingIncome, latest.operatingIncome, g(latest.operatingIncome, prev?.operatingIncome), ccy)
                if (p.ebitda != 0.0 && !quarterly) GrowthRow(S.ebitda, p.ebitda, null, ccy)
                GrowthRow(S.netIncome, latest.earnings, g(latest.earnings, prev?.earnings), ccy)
                if (p.eps != 0.0) KeyValueRow(S.eps, Fmt.price(p.eps, Derivatives.currencySymbol(ccy)))
            }
        }
        if (p.grossMargin != 0.0 || p.operatingMargin != 0.0 || p.profitMargin != 0.0) {
            item {
                SectionHeader(S.margins)
                if (p.grossMargin != 0.0) KeyValueRow(S.grossMargin, Fmt.pct(p.grossMargin, 1))
                if (p.operatingMargin != 0.0) KeyValueRow(S.operatingMargin, Fmt.pct(p.operatingMargin, 1))
                if (p.profitMargin != 0.0) KeyValueRow(S.profitMargin, Fmt.pct(p.profitMargin, 1))
                if (p.returnOnEquity != 0.0) KeyValueRow(S.returnOnEquity, Fmt.pct(p.returnOnEquity, 1))
                if (p.revenueGrowth != 0.0) KeyValueRow(S.revenueGrowth, Fmt.signedPct(p.revenueGrowth, 1))
                if (p.earningsGrowth != 0.0) KeyValueRow(S.earningsGrowth, Fmt.signedPct(p.earningsGrowth, 1))
            }
        }
        if (p.totalAssets != 0.0 || p.totalCash != 0.0 || p.totalDebt != 0.0) {
            item {
                SectionHeader(S.balance)
                if (p.totalAssets != 0.0) KeyValueRow(S.totalAssets, Fmt.compact(p.totalAssets) + " " + ccy)
                if (p.totalLiabilities != 0.0) KeyValueRow(S.totalLiabilities, Fmt.compact(p.totalLiabilities) + " " + ccy)
                if (p.equity != 0.0) KeyValueRow(S.equity, Fmt.compact(p.equity) + " " + ccy)
                if (p.totalCash != 0.0) KeyValueRow(S.totalCash, Fmt.compact(p.totalCash) + " " + ccy)
                if (p.totalDebt != 0.0) KeyValueRow(S.totalDebt, Fmt.compact(p.totalDebt) + " " + ccy)
                if (p.priceToBook != 0.0) KeyValueRow(S.priceToBook, Fmt.num(p.priceToBook) + "×")
            }
        }
        if (p.operatingCashflow != 0.0 || p.freeCashflow != 0.0) {
            item {
                SectionHeader(S.cashflow)
                if (p.operatingCashflow != 0.0) KeyValueRow(S.operatingCashflow, Fmt.compact(p.operatingCashflow) + " " + ccy)
                if (p.freeCashflow != 0.0) KeyValueRow(S.freeCashflow, Fmt.compact(p.freeCashflow) + " " + ccy)
                if (p.dividendRate != 0.0) KeyValueRow(S.dividendPerShare, Fmt.price(p.dividendRate, Derivatives.currencySymbol(ccy)))
                if (p.payoutRatio != 0.0) KeyValueRow(S.payoutRatio, Fmt.pct(p.payoutRatio, 1))
            }
        }
        if (p.epsHistory.isNotEmpty()) {
            item {
                SectionHeader(S.epsHistory)
                p.epsHistory.reversed().forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("${S.actual} ${Fmt.price(e.actual, Derivatives.currencySymbol(ccy))}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(12.dp))
                        val beat = e.actual - e.estimate
                        MetricValue(beat, "${S.estimate} ${Fmt.price(e.estimate, Derivatives.currencySymbol(ccy))}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

private fun shortLabel(label: String): String = when {
    label.matches(Regex("\\d{4}")) -> label
    label.matches(Regex("\\dQ\\d{4}")) -> "Q${label[0]} ${label.takeLast(2)}"
    label.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> label.substring(5, 7) + "/" + label.substring(2, 4)
    else -> label.take(7)
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GrowthRow(label: String, value: Double, growth: Double?, ccy: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(Fmt.compact(value) + " " + ccy, style = MaterialTheme.typography.titleSmall)
        if (growth != null && growth != 0.0) {
            Spacer(Modifier.width(12.dp))
            MetricValue(growth, Fmt.pct(abs(growth), 1), style = MaterialTheme.typography.bodySmall)
        }
    }
}
