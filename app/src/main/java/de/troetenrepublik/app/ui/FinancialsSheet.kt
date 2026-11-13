package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.DividendenUeberblick
import de.troetenrepublik.app.engine.FinanzTabelle
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.launch
import kotlin.math.abs

// Finanzen: Umsatz und Gewinn je Jahr oder Quartal als Balken, darunter Gewinn und Verlust, Margen, Bilanz,
// Cashflow und die Quartalsgewinne je Aktie. Jaehrlich und Quartal sind zwei Seiten zum Wischen; die Pillen folgen.
// Mit seite stehen die Betraege in der Waehrung, in der die Wertpapierseite gerade Kurse zeigt (5.92).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialsSheet(
    name: String, p: MarketApi.Profile, seite: SeitenWaehrung? = null, guv: de.troetenrepublik.app.engine.Sankey.Guv? = null,
    jahre: List<de.troetenrepublik.app.engine.Qualitaet.Jahr> = emptyList(),
    dividende: de.troetenrepublik.app.engine.DividendenUeberblick.Werte? = null, stueck: Double? = null,
    zusatz: (@Composable () -> Unit)? = null, state: de.troetenrepublik.app.AppState? = null, onDismiss: () -> Unit,
) {
    val ccy = p.financialCurrency.ifBlank { "" }
    val umr = seite ?: SeitenWaehrung(ccy, emptyMap())
    val gezeigt = umr.umrechnen(1.0, ccy).second
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
                Text(S.financials, style = MaterialTheme.typography.headlineLarge)
                PunktText(name + if (gezeigt.isNotBlank()) " · " + S.reportingCurrency(gezeigt) else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MitlaufendeReiter(2, pager, titel = { if (it == 0) S.yearly else S.quarterly }, modifier = Modifier.padding(vertical = 14.dp)) {
                scope.launch { pager.animateScrollToPage(it) }
            }
            // [0.77] Erklaer-Assistent: erklaert die gerade gezeigte Seite (Jahre oder Quartale)
            if (state != null) ErklaerKnopf(state, { de.troetenrepublik.app.engine.Erklaerer.kontextFinanzen(name, p, pager.currentPage == 1) }, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).mausWischen(pager)) { page ->
                FinancialsPage(p, quarterly = page == 1, ccy = ccy, umr = umr, guv = if (page == 0) guv else null, jahre = if (page == 0) jahre else emptyList(),
                    dividende = if (page == 0) dividende else null, stueck = if (page == 0) stueck else null,
                    zusatz = if (page == 0) zusatz else null)
            }
        }
    }
}

// Erklaerung zu einer Beschriftung, falls es einen Text gibt (0.68).
private fun erklaerung(label: String): String? = S.kennzahlErklaerung(label) ?: S.finErklaerung(label)

// Aufgeklappter Erklaerungstext unter einer Zeile.
@Composable
private fun ErklaerText(text: String, modifier: Modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
    StichpunktText(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

// Kennzahlzeile, die per Tipp ihre Erklaerung zeigt (0.68).
@Composable
private fun ErklaerZeile(label: String, value: String, erklaert: String = label) {
    var offen by rememberSaveable(label) { mutableStateOf(false) }
    val text = erklaerung(erklaert)
    Column {
        KeyValueRow(label, value, onClick = if (text != null) ({ offen = !offen }) else null)
        if (offen && text != null) ErklaerText(text)
    }
}

// Wachstumszeile mit gezeichnetem Dreieck; ein Tipp zeigt die Erklaerung.
@Composable
private fun ErklaerWachstum(label: String, wachstum: Double) {
    var offen by rememberSaveable(label) { mutableStateOf(false) }
    val text = erklaerung(label)
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (text != null) Modifier.clickable { offen = !offen } else Modifier).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            MetricValue(wachstum, Fmt.pct(abs(wachstum), 1), style = MaterialTheme.typography.bodyMedium)
        }
        if (offen && text != null) ErklaerText(text)
    }
}

// Kennzahl mit Anteilsbalken (anteil von 0 bis 1); ein Tipp zeigt die Erklaerung.
@Composable
private fun ErklaerBalken(label: String, wert: String, anteil: Double, farbe: Color) {
    var offen by rememberSaveable(label) { mutableStateOf(false) }
    val text = erklaerung(label)
    Column {
        ShareBar(label, wert, anteil, farbe, onClick = if (text != null) ({ offen = !offen }) else null)
        if (offen && text != null) ErklaerText(text)
    }
}

// Kleines Info-Symbol hinter einer antippbaren Bezeichnung.
@Composable
private fun InfoMini() {
    androidx.compose.material3.Icon(
        androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
    )
}

// Eine Seite der Finanzen: Balken und Kennzahlen fuer Jahre oder Quartale.
@Composable
private fun FinancialsPage(
    p: MarketApi.Profile, quarterly: Boolean, ccy: String, umr: SeitenWaehrung, guv: de.troetenrepublik.app.engine.Sankey.Guv? = null,
    jahre: List<de.troetenrepublik.app.engine.Qualitaet.Jahr> = emptyList(),
    dividende: de.troetenrepublik.app.engine.DividendenUeberblick.Werte? = null, stueck: Double? = null,
    zusatz: (@Composable () -> Unit)? = null,
) {
    val signal = LocalSignal.current
    val periods = if (quarterly) p.quarterly else p.yearly
    val latest = periods.lastOrNull()
    val prev = periods.dropLast(1).lastOrNull()
    fun g(a: Double?, b: Double?): Double? = Bewertungsblick.veraenderung(a, b)
    // Umsatz blau, Gewinn ocker, ein Verlust rot
    val revenueColor = Kategorie.blau
    val earningsColor = Kategorie.ocker

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (periods.isEmpty()) {
                EmptyHint(S.noFinancials)
            } else {
                BarChart(
                    values = periods.map { umr.umrechnen(it.revenue, ccy).first },
                    secondary = periods.map { umr.umrechnen(it.earnings, ccy).first },
                    labels = periods.map { shortLabel(it.label) },
                    color = revenueColor, secondaryColor = earningsColor, negativeColor = signal.down,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 20.dp),
                )
                // Zahlen je Periode, der Kopf mit den Farbpunkten ist zugleich die Legende (0.68)
                FinTabelle(
                    kopf = listOf(
                        Kopf(if (quarterly) S.finQuartal else S.finJahr),
                        Kopf(S.revenue, revenueColor, erklaerung(S.revenue)),
                        Kopf(S.netIncome, earningsColor, erklaerung(S.netIncome)),
                    ),
                    zeilen = periods.reversed().map {
                        (if (quarterly) FinanzTabelle.periode(it.label) else it.label.take(4)) to listOf(Zelle(umr.kompakt(it.revenue, ccy)), Zelle(umr.kompakt(it.earnings, ccy)))
                    },
                )
                if (periods.any { it.revenue < 0 || it.earnings < 0 }) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Legend(signal.down, S.finVerlust) }
                }
            }
        }
        // Vom Umsatz zum Gewinn als Sankey-Diagramm (5.94), juengstes Geschaeftsjahr
        guv?.let { g ->
            val d = de.troetenrepublik.app.engine.Sankey.guv(g)
            if (!d.leer) item {
                SectionHeader(S.skGuvTitel(g.ende.take(4)))
                val w = g.waehrung.ifBlank { ccy }
                SankeyDiagramm(d, name = { S.skKnoten(it.id) }, betrag = { umr.kompakt(it, w) })
            }
        }
        if (latest != null) {
            item {
                SectionHeader(S.income)
                GrowthRow(S.revenue, latest.revenue, g(latest.revenue, prev?.revenue), ccy, umr, erklaerung(S.revenue), prev?.revenue)
                if (latest.grossProfit != 0.0) GrowthRow(S.grossProfit, latest.grossProfit, g(latest.grossProfit, prev?.grossProfit), ccy, umr, erklaerung(S.grossProfit), prev?.grossProfit)
                if (latest.operatingIncome != 0.0) GrowthRow(S.operatingIncome, latest.operatingIncome, g(latest.operatingIncome, prev?.operatingIncome), ccy, umr, erklaerung(S.operatingIncome), prev?.operatingIncome)
                if (p.ebitda != 0.0 && !quarterly) GrowthRow(S.ebitda, p.ebitda, null, ccy, umr, erklaerung(S.ebitda))
                GrowthRow(S.netIncome, latest.earnings, g(latest.earnings, prev?.earnings), ccy, umr, erklaerung(S.netIncome), prev?.earnings)
                if (p.eps != 0.0) ErklaerZeile(if (quarterly) S.epsZwoelf else S.eps, umr.preis(p.eps, ccy), erklaert = S.eps)
                // Umsatzschaetzung fuer das naechste Quartal gegen dasselbe Quartal des Vorjahrs (5.94)
                if (quarterly && p.mehr.umsatzErwartet > 0) {
                    val naechstes = naechstesQuartal(latest.label)
                    val vorjahr = naechstes?.let { (q, j) -> periods.firstOrNull { it.label == "${q}Q${j - 1}" } }
                    GrowthRow(
                        naechstes?.let { (q, j) -> S.umsatzSchaetzung("Q$q ${j.toString().takeLast(2)}") } ?: S.umsatzSchaetzung(""),
                        p.mehr.umsatzErwartet, g(p.mehr.umsatzErwartet, vorjahr?.revenue), ccy, umr, S.finUmsatzSchaetzungErklaerung, vorjahr?.revenue,
                    )
                }
            }
        }
        // Wachstum gehoert zum Ertrag, die Eigenkapitalrendite mit den Margen unter „Rentabilitaet“
        if (p.revenueGrowth != 0.0 || p.earningsGrowth != 0.0) {
            item {
                if (latest == null) SectionHeader(S.income)
                if (p.revenueGrowth != 0.0) ErklaerWachstum(S.revenueGrowth, p.revenueGrowth)
                if (p.earningsGrowth != 0.0) ErklaerWachstum(S.earningsGrowth, p.earningsGrowth)
            }
        }
        if (p.grossMargin != 0.0 || p.operatingMargin != 0.0 || p.profitMargin != 0.0 || p.returnOnEquity != 0.0) {
            item {
                SectionHeader(S.bkGruppe("RENTABILITAET"))
                if (p.returnOnEquity != 0.0) ErklaerZeile(S.returnOnEquity, Fmt.pct(p.returnOnEquity, 1))
                // Margen als Anteil am Umsatz auf der Skala 0 bis 100 %
                if (p.grossMargin != 0.0) ErklaerBalken(S.grossMargin, Fmt.pct(p.grossMargin, 1), p.grossMargin / 100.0, if (p.grossMargin < 0) signal.down else AnteilsFarben.folge(0))
                if (p.operatingMargin != 0.0) ErklaerBalken(S.operatingMargin, Fmt.pct(p.operatingMargin, 1), p.operatingMargin / 100.0, if (p.operatingMargin < 0) signal.down else AnteilsFarben.folge(1))
                if (p.profitMargin != 0.0) ErklaerBalken(S.profitMargin, Fmt.pct(p.profitMargin, 1), p.profitMargin / 100.0, if (p.profitMargin < 0) signal.down else AnteilsFarben.folge(2))
            }
        }
        if (p.totalAssets != 0.0 || p.totalCash != 0.0 || p.totalDebt != 0.0) {
            item {
                SectionHeader(S.balance)
                if (p.totalAssets != 0.0) ErklaerZeile(S.totalAssets, umr.kompakt(p.totalAssets, ccy))
                // Schulden und Eigenkapital als Anteil der Bilanzsumme
                if (p.totalLiabilities != 0.0) {
                    if (p.totalAssets > 0) ErklaerBalken(S.totalLiabilities, umr.kompakt(p.totalLiabilities, ccy), p.totalLiabilities / p.totalAssets, AnteilsFarben.folge(1))
                    else ErklaerZeile(S.totalLiabilities, umr.kompakt(p.totalLiabilities, ccy))
                }
                if (p.equity != 0.0) {
                    if (p.totalAssets > 0) ErklaerBalken(S.equity, umr.kompakt(p.equity, ccy), p.equity / p.totalAssets, AnteilsFarben.folge(0))
                    else ErklaerZeile(S.equity, umr.kompakt(p.equity, ccy))
                }
                if (p.totalCash != 0.0) ErklaerZeile(S.totalCash, umr.kompakt(p.totalCash, ccy))
                if (p.totalDebt != 0.0) ErklaerZeile(S.totalDebt, umr.kompakt(p.totalDebt, ccy))
                if (p.priceToBook != 0.0) ErklaerZeile(S.priceToBook, Fmt.num(p.priceToBook) + "×")
            }
        }
        if (p.operatingCashflow != 0.0 || p.freeCashflow != 0.0) {
            item {
                SectionHeader(S.cashflow)
                if (p.operatingCashflow != 0.0) ErklaerZeile(S.operatingCashflow, umr.kompakt(p.operatingCashflow, ccy))
                if (p.freeCashflow != 0.0) ErklaerZeile(S.freeCashflow, umr.kompakt(p.freeCashflow, ccy))
            }
        }
        // Kompakte Dividende (5.97): Rendite, Termine und Jahressummen je Aktie
        dividende?.let { d -> item { SectionHeader(S.finDivTitel); DividendenKompakt(d, umr, stueck) } }
        // Bilanz und Cashflow ueber die Jahre (5.94): je Kennzahl die Jahre in einem gleichmaessigen Raster (0.68)
        val reihen = jahresReihen(jahre.takeLast(4))
        if (reihen.isNotEmpty()) item {
            SectionHeader(S.finJahreTitel)
            JahresRaster(reihen, umr, jahre.lastOrNull()?.waehrung?.ifBlank { null } ?: ccy)
        }
        // Gewinn je Aktie je Quartal: Punktdiagramm und Tabelle, neuestes Quartal oben (0.68)
        val eps = p.epsHistory.filter { it.estimate != 0.0 || it.actual != 0.0 }.takeLast(8)
        if (eps.isNotEmpty()) {
            item {
                SectionHeader(S.epsHistory)
                if (eps.size >= 2) {
                    EpsPunkte(eps.map { it.actual }, eps.map { it.estimate }, eps.map { quartalKurz(it.label) })
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        EpsLegende(gefuellt = true, S.actual); EpsLegende(gefuellt = false, S.estimate)
                    }
                }
                FinTabelle(
                    kopf = listOf(
                        Kopf(S.finQuartal), Kopf(S.actual, erklaerung = erklaerung(S.actual)),
                        Kopf(S.estimate, erklaerung = erklaerung(S.estimate)), Kopf(S.finAbweichung, erklaerung = erklaerung(S.finAbweichung)),
                    ),
                    zeilen = eps.reversed().map { e ->
                        val u = Bewertungsblick.ueberraschung(e.actual, e.estimate)
                        FinanzTabelle.periode(e.label) to listOf(
                            Zelle(umr.preis(e.actual, ccy)), Zelle(umr.preis(e.estimate, ccy)),
                            if (u != null) Zelle(Fmt.pct(abs(u.abweichung), 1), u.abweichung, u.ampel != Bewertungsblick.Ampel.GELB) else Zelle(""),
                        )
                    },
                )
                if (p.earningsDate > System.currentTimeMillis() - 86_400_000L) {
                    Text(
                        S.zuNaechster(Fmt.date(p.earningsDate)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (zusatz != null) item { zusatz() }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Zelle einer Tabelle; mit richtung steht das Dreieck vor dem Wert.
private class Zelle(val text: String, val richtung: Double? = null, val farbig: Boolean = false)

// Kopf einer Spalte, optional mit Farbpunkt und Erklaerung.
private class Kopf(val text: String, val punkt: Color? = null, val erklaerung: String? = null)

@Composable
private fun ZelleText(z: Zelle) {
    if (z.richtung != null) ChangeValue(change = z.richtung, text = z.text, style = MaterialTheme.typography.titleSmall, colored = z.farbig)
    else Text(z.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
}

@Composable
private fun KopfPunkt(farbe: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(farbe))
    Spacer(Modifier.width(6.dp))
}

// Tabelle mit fester Wertbreite je Spalte. Passt nicht alles nebeneinander, steht je Zeile die Periode oben und
// darunter je Wert eine Zeile mit Kopftext links und Wert rechts (0.68).
@Composable
private fun FinTabelle(kopf: List<Kopf>, zeilen: List<Pair<String, List<Zelle>>>) {
    val messer = rememberTextMeasurer()
    val typo = MaterialTheme.typography
    val dichte = LocalDensity.current
    var offen by rememberSaveable { mutableStateOf<Int?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val (abstand, punktB, dreieckB, rand) = with(dichte) { listOf(16.dp.roundToPx(), 16.dp.roundToPx(), (9f + 9f * 0.6f + 2f).dp.roundToPx(), 40.dp.roundToPx()) }
        val infoB = with(dichte) { 20.dp.roundToPx() }
        fun w(t: String, stil: androidx.compose.ui.text.TextStyle) = messer.measure(t, stil, maxLines = 1, softWrap = false).size.width
        val breiten = kopf.indices.map { i ->
            val k = kopf[i]
            val kopfB = w(k.text, typo.bodySmall) + (if (k.punkt != null) punktB else 0) + (if (k.erklaerung != null) infoB else 0)
            val zellenB = zeilen.maxOfOrNull { z ->
                if (i == 0) w(z.first, typo.bodyMedium)
                else z.second[i - 1].let { c -> w(c.text, typo.titleSmall) + if (c.richtung != null) dreieckB else 0 }
            } ?: 0
            maxOf(kopfB, zellenB)
        }
        if (FinanzTabelle.spaltenPassen(breiten, abstand, constraints.maxWidth - rand)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    kopf.forEachIndexed { i, k ->
                        val tippen = if (k.erklaerung != null) Modifier.clickable { offen = if (offen == i) null else i } else Modifier
                        val zelle = @Composable {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (k.punkt != null) KopfPunkt(k.punkt)
                                Text(k.text, style = typo.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                                if (k.erklaerung != null) { Spacer(Modifier.width(6.dp)); InfoMini() }
                            }
                        }
                        if (i == 0) Box(Modifier.weight(1f).then(tippen)) { zelle() }
                        else Box(Modifier.widthIn(min = with(dichte) { breiten[i].toDp() }).then(tippen), contentAlignment = Alignment.CenterEnd) { zelle() }
                    }
                }
                offen?.let { i -> kopf.getOrNull(i)?.erklaerung?.let { ErklaerText(it, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) } }
                zeilen.forEach { (periode, zellen) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(periode, style = typo.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                        zellen.forEachIndexed { j, z ->
                            Box(Modifier.widthIn(min = with(dichte) { breiten[j + 1].toDp() }), contentAlignment = Alignment.CenterEnd) { ZelleText(z) }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                zeilen.forEachIndexed { r, (periode, zellen) ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(periode, style = typo.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                        zellen.forEachIndexed { j, z ->
                            val k = kopf[j + 1]
                            val schluessel = r * kopf.size + j + 1
                            val tippen = if (k.erklaerung != null) Modifier.clickable { offen = if (offen == schluessel) null else schluessel } else Modifier
                            Row(Modifier.fillMaxWidth().then(tippen).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    if (k.punkt != null) KopfPunkt(k.punkt)
                                    Text(k.text, style = typo.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (k.erklaerung != null) { Spacer(Modifier.width(6.dp)); InfoMini() }
                                }
                                Spacer(Modifier.width(12.dp))
                                ZelleText(z)
                            }
                            if (offen == schluessel) k.erklaerung?.let { ErklaerText(it, Modifier.padding(top = 4.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// Punktdiagramm: je Quartal ein Slot, Erwartet als hohler Ring, Tatsaechlich als gefuellter Punkt auf derselben Mitte (0.68).
@Composable
private fun EpsPunkte(ist: List<Double>, erwartet: List<Double>, labels: List<String>) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val kraeftig = MaterialTheme.colorScheme.onSurface
    val messer = rememberTextMeasurer()
    val stil = MaterialTheme.typography.labelSmall.copy(color = grau)
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 20.dp)) {
        val n = ist.size
        if (n == 0) return@Canvas
        val alle = ist + erwartet
        val maxV = alle.max()
        val minV = alle.min()
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val labelH = 18.dp.toPx()
        val rand = 10.dp.toPx()
        val oben = rand
        val h = size.height - labelH - 2 * rand
        fun y(v: Double) = oben + (h * ((maxV - v) / span)).toFloat()
        val slot = size.width / n
        val gemessen = labels.map { messer.measure(it, stil) }
        val jedes = if (gemessen.any { it.size.width > slot - 4.dp.toPx() }) 2 else 1
        if (minV < 0 && maxV > 0) drawLine(grau.copy(alpha = 0.5f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        for (i in 0 until n) {
            val x = slot * i + slot / 2
            val yi = y(ist[i])
            val ye = y(erwartet.getOrElse(i) { 0.0 })
            drawLine(grau.copy(alpha = 0.5f), Offset(x, ye), Offset(x, yi), strokeWidth = 1.5.dp.toPx())
            drawCircle(grau, radius = 6.dp.toPx(), center = Offset(x, ye), style = Stroke(2.dp.toPx()))
            drawCircle(kraeftig, radius = 4.5.dp.toPx(), center = Offset(x, yi))
            val m = gemessen.getOrNull(i) ?: continue
            if ((n - 1 - i) % jedes == 0) drawText(m, topLeft = Offset(x - m.size.width / 2f, size.height - labelH + 2.dp.toPx()))
        }
    }
}

// Legendenzeichen wie im Diagramm: gefuellter Punkt oder hohler Ring.
@Composable
private fun EpsLegende(gefuellt: Boolean, label: String) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val kraeftig = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            if (gefuellt) drawCircle(kraeftig, radius = 4.5.dp.toPx())
            else drawCircle(grau, radius = 5.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Folgequartal zu einer Bezeichnung wie „3Q2026“ als Quartal und Jahr; null bei anderer Form.
private fun naechstesQuartal(label: String): Pair<Int, Int>? {
    val m = Regex("(\\d)Q(\\d{4})").matchEntire(label) ?: return null
    val q = m.groupValues[1].toInt()
    val j = m.groupValues[2].toInt()
    return if (q >= 4) 1 to j + 1 else q + 1 to j
}

// Kennzahlen der Jahresabschluesse als Reihen (Titel, Jahr zu Betrag); Reihen ohne einen Wert fallen weg.
private fun jahresReihen(jahre: List<de.troetenrepublik.app.engine.Qualitaet.Jahr>): List<Pair<String, List<Pair<String, Double>>>> {
    fun reihe(titel: String, f: (de.troetenrepublik.app.engine.Qualitaet.Jahr) -> Double?) =
        titel to jahre.mapNotNull { j -> f(j)?.takeIf { it.isFinite() }?.let { j.ende.take(4) to it } }
    return listOf(
        reihe(S.totalAssets) { it.bilanzsumme },
        reihe(S.totalLiabilities) { it.verbindlichkeiten },
        reihe(S.equity) { j -> j.bilanzsumme?.let { b -> j.verbindlichkeiten?.let { b - it } } },
        reihe(S.finLangfristSchulden) { it.langfristSchulden },
        reihe(S.operatingCashflow) { it.cashflow },
        reihe(S.finInvestitionen) { it.capex },
        reihe(S.freeCashflow) { it.freierCashflow ?: it.cashflow?.let { c -> it.capex?.let { k -> c - kotlin.math.abs(k) } } },
    ).filter { it.second.isNotEmpty() }
}

// Ein Raster fuer alle Reihen: die breiteste Zelle bestimmt, ob eine oder zwei Spalten passen (0.68).
@Composable
private fun JahresRaster(reihen: List<Pair<String, List<Pair<String, Double>>>>, umr: SeitenWaehrung, ccy: String) {
    val messer = rememberTextMeasurer()
    val typo = MaterialTheme.typography
    val dichte = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        fun w(t: String, stil: androidx.compose.ui.text.TextStyle) = messer.measure(t, stil, maxLines = 1, softWrap = false).size.width
        val zelle = reihen.maxOf { (_, werte) -> werte.maxOf { (j, v) -> w(j, typo.bodyMedium) + w(umr.kompakt(v, ccy), typo.titleSmall) } } + with(dichte) { 8.dp.roundToPx() }
        val spalten = FinanzTabelle.rasterSpalten(zelle, with(dichte) { 16.dp.roundToPx() }, constraints.maxWidth)
        Column(Modifier.fillMaxWidth()) {
            reihen.forEach { (titel, werte) -> JahresReihe(titel, werte, spalten, umr, ccy) }
        }
    }
}

@Composable
private fun JahresReihe(titel: String, werte: List<Pair<String, Double>>, spalten: Int, umr: SeitenWaehrung, ccy: String) {
    var offen by rememberSaveable(titel) { mutableStateOf(false) }
    val text = erklaerung(titel)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().then(if (text != null) Modifier.clickable { offen = !offen } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (text != null) { Spacer(Modifier.width(8.dp)); InfoMini() }
        }
        if (offen && text != null) ErklaerText(text, Modifier.padding(top = 4.dp))
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            werte.chunked(spalten).forEach { zeile ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    zeile.forEach { (jahr, v) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(jahr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                            Spacer(Modifier.weight(1f))
                            Text(umr.kompakt(v, ccy), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                        }
                    }
                    repeat(spalten - zeile.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// Kompakte Dividende (5.97): Kacheln zu Rendite, Betrag und Termin, dahinter die Jahre und der Bestandsertrag
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DividendenKompakt(w: DividendenUeberblick.Werte, umr: SeitenWaehrung, stueck: Double?) {
    // Zuletzt getippte Kachel, ihre Erklaerung steht unter den Kacheln (0.68)
    var offen by rememberSaveable { mutableStateOf<String?>(null) }
    // Eine Kachel: Beschriftung, darunter der Wert, beide ohne Umbruch
    @Composable
    fun kachel(titel: String, wert: String) {
        val tippen = if (erklaerung(titel) != null) Modifier.clickable { offen = if (offen == titel) null else titel } else Modifier
        Column(tippen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                if (erklaerung(titel) != null) { Spacer(Modifier.width(6.dp)); InfoMini() }
            }
            Text(wert, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
    }

    // Feste zwei Spalten gleicher Breite, beide Spalten bündig
    val felder = buildList<Pair<String, String>> {
        w.rendite?.let { add(S.dividendYield to Fmt.pct(it, 2)) }
        w.jeAktieEuro?.let { add(S.finDivJeAktie to umr.preis(it, "EUR")) }
        if (w.jahreErhoeht > 0) add(S.finDivJahreErhoeht to w.jahreErhoeht.toString())
        (w.wachstum5?.let { S.finDiv5J to it } ?: w.wachstum?.let { S.toolDividendGrowth to it })
            ?.let { (titel, wert) -> add(titel to Fmt.signedPct(wert, 1)) }
        w.quote?.let { add(S.payoutRatio to Fmt.pct(it, 0)) }
        (w.naechsterExTag?.let { S.finDivExTag to Fmt.date(it) }
            ?: w.naechsteZahlung?.let { S.finDivZahltag to Fmt.date(it) })
            ?.let { (titel, wert) -> add(titel to wert) }
        if (w.naechsterExTag != null) w.kauftag?.let { add(S.finDivKauftag to Fmt.date(it)) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        felder.chunked(2).forEach { paar ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                paar.forEach { (titel, wert) -> Box(Modifier.weight(1f)) { kachel(titel, wert) } }
                if (paar.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    offen?.let { t -> erklaerung(t)?.let { ErklaerText(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) } }
    // Jahressummen je Aktie als schmales Balkendiagramm
    if (w.jahre.size >= 2) {
        DividendenBalken(
            werte = w.jahre.map { umr.umrechnen(it.second, "EUR").first },
            jahre = w.jahre.map { "'" + (it.first % 100).toString().padStart(2, '0') },
            text = { umr.preis(it, "EUR") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
    // Jaehrlicher Ertrag des Bestands, vor Steuer
    if (stueck != null && stueck > 0 && w.jeAktieEuro != null) {
        // Stückzahl höchstens mit zwei Nachkommastellen, ganze Zahl ohne
        val anzahl = if (kotlin.math.abs(stueck - Math.round(stueck)) < 1e-9) Math.round(stueck).toString() else Fmt.num(stueck, 2)
        KeyValueRow(S.finDivBestand, Fmt.euro(DividendenUeberblick.jahresErtrag(w.jeAktieEuro, stueck)), hint = anzahl + " " + S.pieces)
    }
}

// Jahressummen als kleine Balken in der Dividendenfarbe der App, der Betrag je Jahr klein über dem Balken, wenn er passt.
@Composable
private fun DividendenBalken(werte: List<Double>, jahre: List<String>, text: (Double) -> String, modifier: Modifier = Modifier) {
    val messer = rememberTextMeasurer()
    val stil = MaterialTheme.typography.labelSmall
    val farbe = LocalSignal.current.warn.copy(alpha = 0.75f)
    val hoechst = (werte.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
    BoxWithConstraints(modifier) {
        val betraege = remember(werte) { werte.map(text) }
        val breite = constraints.maxWidth / werte.size
        val passt = betraege.all { messer.measure(it, stil, maxLines = 1, softWrap = false).size.width + 8 <= breite }
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            werte.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (passt) Text(betraege[i], style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Box(Modifier.padding(top = 2.dp).fillMaxWidth(0.7f).height((44.0 * (v / hoechst).coerceIn(0.0, 1.0)).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(3.dp)).background(farbe))
                    Text(jahre[i], style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
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
private fun GrowthRow(label: String, value: Double, growth: Double?, ccy: String, umr: SeitenWaehrung, erklaerung: String?, vorher: Double? = null) {
    val wechsel = Bewertungsblick.wechsel(value, vorher)
    var offen by rememberSaveable(label) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().then(if (erklaerung != null) Modifier.clickable { offen = !offen } else Modifier).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(umr.kompakt(value, ccy), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            if (wechsel == null && growth != null && growth != 0.0) {
                Spacer(Modifier.width(12.dp))
                MetricValue(growth, Fmt.pct(abs(growth), 1), style = MaterialTheme.typography.titleSmall)
            }
        }
        if (wechsel != null) {
            val signal = LocalSignal.current
            Text(
                S.finWechsel(wechsel.name),
                style = MaterialTheme.typography.bodySmall,
                color = if (wechsel == Bewertungsblick.Wechsel.VERLUST_ZU_GEWINN) signal.up else signal.down,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            )
        }
        if (offen && erklaerung != null) ErklaerText(erklaerung)
    }
}
