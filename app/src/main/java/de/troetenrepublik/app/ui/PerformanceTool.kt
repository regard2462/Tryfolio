package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.engine.Performanceanalyse
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val PERF_ZEITRAEUME = listOf("YTD", "Y1", "Y3", "Y5", "MAX")

private fun perfVon(key: String, jetzt: Long, zone: ZoneId): Long = when (key) {
    "YTD" -> LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
    "Y1" -> jetzt - 365L * 86_400_000L
    "Y3" -> jetzt - 3 * 365L * 86_400_000L
    "Y5" -> jetzt - 5 * 365L * 86_400_000L
    else -> 0L
}

private fun vorzeichen(anteil: Double, stellen: Int = 2): String {
    val v = anteil * 100
    return (if (v > 0.000_5) "+" else if (v < -0.000_5) "−" else "") + Fmt.pct(abs(v), stellen)
}

// Rendite des ganzen Depots wie in Portfolio Performance, mit Wertpapieren und Cash, nach Gebuehren und Steuern.
// Die Zeitraeume sind Seiten zum Wischen; alle werden auf einmal im Hintergrund gerechnet.
// Kurve gegen einen Index sowie Kaeufe und Ertraege je Monat zeigen andere Werkzeuge, der Hinweis unten nennt sie.
@Composable
internal fun PerformanceTool(state: AppState) {
    val account = depotKonto(state)
    val valuation = depotBewertung(state)
    var zeitraum by rememberSaveable { mutableStateOf("MAX") }
    var reihe by remember { mutableStateOf<List<Pair<Long, Double>>?>(null) }
    var laedt by remember { mutableStateOf(true) }
    var ergebnisse by remember { mutableStateOf<Map<String, Performanceanalyse.Ergebnis?>?>(null) }
    LaunchedEffect(account.transactions.size, account.positions.size) {
        laedt = true
        reihe = runCatching {
            val parts = state.portfolioParts(Range.MAX)
            val jetzt = System.currentTimeMillis()
            var werte = parts?.raw?.candles?.map { it.t to it.close }.orEmpty()
            if (valuation.positions.isNotEmpty() && (werte.lastOrNull()?.first ?: 0L) < jetzt - 60_000L) werte = werte + (jetzt to valuation.securities)
            werte
        }.getOrNull()
        laedt = false
    }
    LaunchedEffect(reihe, account.transactions.size, account.cash) {
        val r = reihe ?: return@LaunchedEffect
        val jetzt = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        ergebnisse = withContext(Dispatchers.Default) {
            PERF_ZEITRAEUME.associateWith { Performanceanalyse.berechnen(r, account.transactions, account.cash, perfVon(it, jetzt, zone), jetzt, zone) }
        }
    }

    val pager = rememberPagerState(initialPage = PERF_ZEITRAEUME.indexOf(zeitraum).coerceAtLeast(0)) { PERF_ZEITRAEUME.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { zeitraum = PERF_ZEITRAEUME[it] } }
    Text(S.toolPeriod, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    PerfLeiste(pager) { i -> scope.launch { pager.animateScrollToPage(i) } }
    val alle = ergebnisse
    HorizontalPager(
        state = pager, modifier = Modifier.fillMaxWidth().mausWischen(pager),
        beyondViewportPageCount = 1, verticalAlignment = Alignment.Top, key = { PERF_ZEITRAEUME[it] },
    ) { i ->
        Column(Modifier.fillMaxWidth()) {
            val e = alle?.get(PERF_ZEITRAEUME[i])
            if (e == null) ToolNote(if (laedt || reihe == null || alle == null) S.toolLoading else S.ptNoPurchases)
            else PerformanceSeite(e)
        }
    }
    ToolNote(S.ppfHinweis)
}

// Zeitleiste ueber den Seiten; liest die Seite selbst, damit nur sie beim Wischen neu gezeichnet wird.
@Composable
private fun PerfLeiste(pager: PagerState, onSelect: (Int) -> Unit) {
    ZeitLeiste(PERF_ZEITRAEUME.map { S.ppfZeitraum(it) }, selected = pager.currentPage, onSelect = onSelect, modifier = Modifier.padding(horizontal = 12.dp))
}

// Ein Zeitraum: Kennzahlen, Kurve, Kapitalbilanz, Monate und Jahre.
@Composable
private fun PerformanceSeite(e: Performanceanalyse.Ergebnis) {
    val signal = LocalSignal.current
    val b = e.bilanz
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Text(S.ppfTtwror, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(vorzeichen(e.ttwror), style = MaterialTheme.typography.headlineMedium, color = signal.forChange(e.ttwror))
        Text(S.ppfVonBis(Fmt.date(e.von), Fmt.date(e.bis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (e.kurve.size >= 3) {
        LinesChart(listOf(e.kurve.map { it.second } to MaterialTheme.colorScheme.primary), Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 20.dp, vertical = 12.dp))
    }
    KeyValueRow(S.ppfTtwrorJahr, e.ttwrorJahr?.let { vorzeichen(it) } ?: S.ppfUnterJahr, valueColor = e.ttwrorJahr?.let { signal.forChange(it) })
    KeyValueRow(S.ppfIzf, e.izf?.let { vorzeichen(it) } ?: "—", valueColor = e.izf?.let { signal.forChange(if (abs(it * 100) < 0.000_5) 0.0 else it) }, hint = S.ppfIzfHinweis) // 0,00 % nicht rot
    KeyValueRow(S.ppfAbsolut, Fmt.signedEuro(b.absolut), valueColor = signal.forChange(b.absolut))
    e.letzterTag?.let { KeyValueRow(S.ppfLetzterTag, vorzeichen(it), valueColor = signal.forChange(if (abs(it * 100) < 0.000_5) 0.0 else it)) }
    e.nurWertpapiere?.let { KeyValueRow(S.ppfNurWertpapiere, vorzeichen(it), valueColor = signal.forChange(it), hint = S.ppfNurWertpapiereHinweis) }

    SectionHeader(S.ppfRisiko)
    KeyValueRow(S.ppfMaxRueckgang, if (e.maxRueckgang > 0.000_05) "−" + Fmt.pct(e.maxRueckgang * 100, 1) else Fmt.pct(0.0, 1))
    KeyValueRow(S.ppfRueckgangDauer, S.ppfTage(e.rueckgangTage), hint = S.ppfRueckgangDauerHinweis)
    KeyValueRow(S.ppfVolatilitaet, e.volatilitaet?.let { Fmt.pct(it * 100, 1) } ?: "—")
    KeyValueRow(S.ppfSemivolatilitaet, e.semivolatilitaet?.let { Fmt.pct(it * 100, 1) } ?: "—", hint = S.ppfSemivolatilitaetHinweis)
    KeyValueRow(S.ppfHoch, vorzeichen(e.hoch.second / 100), hint = Fmt.date(e.hoch.first))

    SectionHeader(S.ppfBilanz)
    KeyValueRow(S.ppfWertAm(Fmt.date(e.von)), Fmt.euro(b.anfang))
    KeyValueRow(S.ppfEinlagen, "+" + Fmt.euro(b.einlagen))
    KeyValueRow(S.ppfEntnahmen, "−" + Fmt.euro(b.entnahmen))
    KeyValueRow(S.ppfKursgewinne, Fmt.signedEuro(b.kursgewinne), valueColor = signal.forChange(b.kursgewinne), hint = S.ppfRealisiert(Fmt.signedEuro(b.realisiert)))
    KeyValueRow(S.ppfDividenden, "+" + Fmt.euro(b.dividenden))
    KeyValueRow(S.ppfZinsen, "+" + Fmt.euro(b.zinsen))
    KeyValueRow(S.ppfGebuehren, "−" + Fmt.euro(b.gebuehren))
    KeyValueRow(S.ppfSteuern, "−" + Fmt.euro(b.steuern))
    KeyValueRow(S.ppfWertAm(Fmt.date(e.bis)), Fmt.euro(b.ende))

    if (e.jahre.isNotEmpty()) {
        SectionHeader(S.ppfMonate)
        e.jahre.keys.sortedDescending().forEach { jahr -> JahresZeile(jahr, e.jahre[jahr] ?: 0.0, e.monate) }
    }
}

// Rendite des Jahres, darunter die zwoelf Monate in zwei Reihen, leer, wo der Zeitraum nicht hinreicht.
@Composable
private fun JahresZeile(jahr: Int, rendite: Double, monate: Map<YearMonth, Double>) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$jahr", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(vorzeichen(rendite, 1), style = MaterialTheme.typography.titleSmall, color = signal.forChange(rendite))
        }
        for (reihe in listOf(1..6, 7..12)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (m in reihe) {
                    val ym = YearMonth.of(jahr, m)
                    val r = monate[ym]
                    val farbe = r?.let { signal.forChange(it) }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            // Kraeftiger, je groesser die Rendite, voll ab 10 % im Monat
                            .background(farbe?.copy(alpha = 0.10f + 0.40f * (abs(r!!) / 0.10).coerceAtMost(1.0).toFloat()) ?: MaterialTheme.colorScheme.surfaceContainer)
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            ym.format(DateTimeFormatter.ofPattern("MMM", I18n.locale)).replace(".", "").take(3),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(r?.let { vorzeichen(it, 1) } ?: "", style = MaterialTheme.typography.labelSmall, color = farbe ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}
