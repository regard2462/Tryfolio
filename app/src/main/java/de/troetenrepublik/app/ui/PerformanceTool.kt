package de.troetenrepublik.app.ui

import kotlinx.coroutines.launch
import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.engine.Monatsraster
import de.troetenrepublik.app.engine.Performanceanalyse
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private val PERF_ALLE = listOf("YTD", "Y1", "Y3", "Y5", "MAX")

// Zeitraeume, die das Depot ueberdeckt: Jahre nur, wenn es mindestens so lange besteht, „Seit 1.1.“ nur, wenn es vor dem 1.1. bestand; Gesamt immer.
internal fun perfVerfuegbar(start: Long?, jetzt: Long, zone: ZoneId): List<String> {
    if (start == null) return listOf("MAX")
    return PERF_ALLE.filter { k ->
        when (k) {
            "MAX" -> true
            "YTD" -> start < perfVon("YTD", jetzt, zone)
            else -> start <= perfVon(k, jetzt, zone)
        }
    }
}

internal fun perfVon(key: String, jetzt: Long, zone: ZoneId): Long = when (key) {
    "YTD" -> LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
    "Y1" -> jetzt - de.troetenrepublik.app.engine.PortfolioTools.JAHR_MS
    "Y3" -> jetzt - 3 * de.troetenrepublik.app.engine.PortfolioTools.JAHR_MS
    "Y5" -> jetzt - 5 * de.troetenrepublik.app.engine.PortfolioTools.JAHR_MS
    else -> 0L
}

private fun vorzeichen(anteil: Double, stellen: Int = 2): String {
    val v = anteil * 100
    return (if (v > 0.000_5) "+" else if (v < -0.000_5) "−" else "") + Fmt.pct(abs(v), stellen)
}

// Rendite des ganzen Depots, mit Wertpapieren und Cash, nach Gebuehren und Steuern.
// Die Zeitraeume sind Seiten zum Wischen; alle werden auf einmal im Hintergrund gerechnet.
// Kurve gegen einen Index sowie Kaeufe und Ertraege je Monat zeigen andere Werkzeuge, der Hinweis unten nennt sie.
@Composable
internal fun PerformanceTool(state: AppState, onOpen: (String) -> Unit = {}) {
    val account = depotKonto(state)
    val valuation = depotBewertung(state)
    var zeitraum by rememberSaveable { mutableStateOf("MAX") }
    var eigenAn by rememberSaveable { mutableStateOf(false) }
    var eigenVon by rememberSaveable { mutableLongStateOf(0L) }
    var eigenBis by rememberSaveable { mutableLongStateOf(0L) }
    var dialog by remember { mutableStateOf(false) }
    var eigenErgebnis by remember { mutableStateOf<Performanceanalyse.Ergebnis?>(null) }
    var reihe by remember { mutableStateOf<List<Pair<Long, Double>>?>(null) }
    var laedt by remember { mutableStateOf(true) }
    var ergebnisse by remember { mutableStateOf<Map<String, Performanceanalyse.Ergebnis?>?>(null) }
    // Werte je Tag fuer den Abschnitt je Wertpapier; nur im eigenen Depot, ein fremdes hat keine Buchungen
    val eigenes = LocalFremdesDepot.current == null
    var zerlegung by remember { mutableStateOf<AppState.Zerlegung?>(null) }
    LaunchedEffect(account.transactions.size, account.positions.size, eigenes) {
        if (eigenes) zerlegung = runCatching { state.depotZerlegung() }.getOrNull()
    }
    LaunchedEffect(account.transactions.size, account.positions.size) {
        laedt = true
        reihe = runCatching { depotWertReihe(state, valuation) }.getOrNull()
        laedt = false
    }
    LaunchedEffect(reihe, account.transactions.size, account.cash) {
        val r = reihe ?: return@LaunchedEffect
        val jetzt = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        ergebnisse = withContext(Dispatchers.Default) {
            PERF_ALLE.associateWith { Performanceanalyse.berechnen(r, account.transactions, account.cash, perfVon(it, jetzt, zone), jetzt, zone) }
        }
    }

    LaunchedEffect(reihe, account.transactions.size, account.cash, eigenAn, eigenVon, eigenBis) {
        val r = reihe
        if (!eigenAn || r == null) { eigenErgebnis = null; return@LaunchedEffect }
        val jetzt = System.currentTimeMillis()
        eigenErgebnis = withContext(Dispatchers.Default) {
            Performanceanalyse.berechnenTage(r, account.transactions, account.cash, eigenVon, eigenBis, jetzt, ZoneId.systemDefault())
        }
    }

    val startTs = remember(account.transactions.size, reihe) {
        val t = account.transactions.minOfOrNull { it.ts }
        val r = reihe?.firstOrNull()?.first
        if (t != null && r != null) minOf(t, r) else t ?: r
    }
    val zeitraeume = remember(startTs) { perfVerfuegbar(startTs, System.currentTimeMillis(), ZoneId.systemDefault()) }
    // Gemerkte Wahl, die es nicht mehr gibt, faellt auf Gesamt zurueck
    val pager = rememberPagerState(initialPage = zeitraeume.indexOf(zeitraum).let { if (it < 0) zeitraeume.lastIndex else it }) { zeitraeume.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, zeitraeume) {
        if (zeitraum !in zeitraeume) { zeitraum = "MAX"; pager.scrollToPage(zeitraeume.lastIndex) }
        else if (pager.currentPage != zeitraeume.indexOf(zeitraum)) pager.scrollToPage(zeitraeume.indexOf(zeitraum))
        snapshotFlow { pager.currentPage }.collect { zeitraum = zeitraeume.getOrElse(it) { "MAX" } }
    }
    Text(S.widgetZeitraum, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    PerfLeiste(pager, zeitraeume, eigenAn, onEigen = { dialog = true }) { i -> eigenAn = false; scope.launch { pager.animateScrollToPage(i) } }
    val alle = ergebnisse
    if (eigenAn) {
        Column(Modifier.fillMaxWidth()) {
            val e = eigenErgebnis
            if (e == null) ToolNote(if (laedt || reihe == null) S.toolLoading else S.ptNoPurchases)
            else PerformanceSeite(e, account.transactions, zerlegung, onOpen, state.riskFree, state)
        }
    } else HorizontalPager(
        state = pager, modifier = Modifier.fillMaxWidth().mausWischen(pager),
        beyondViewportPageCount = 1, verticalAlignment = Alignment.Top, key = { zeitraeume.getOrElse(it) { "MAX" } },
    ) { i ->
        Column(Modifier.fillMaxWidth()) {
            val e = alle?.get(zeitraeume.getOrElse(i) { "MAX" })
            if (e == null) ToolNote(if (laedt || reihe == null || alle == null) S.toolLoading else S.ptNoPurchases)
            else PerformanceSeite(e, account.transactions, zerlegung, onOpen, state.riskFree, state)
        }
    }
    HinweisInfo(S.ppfHinweis)
    if (dialog) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { dialog = false }) { v, b ->
        eigenVon = minOf(v, b); eigenBis = maxOf(v, b); eigenAn = true; dialog = false
    }
}

// Wert der Wertpapiere im Depot ueber die ganze Zeit, der letzte Punkt moeglichst jetzt.
internal suspend fun depotWertReihe(state: AppState, valuation: de.troetenrepublik.app.data.Valuation): List<Pair<Long, Double>> {
    val parts = state.portfolioParts(Range.MAX)
    val jetzt = System.currentTimeMillis()
    var werte = parts?.raw?.candles?.map { it.t to it.close }.orEmpty()
    if (valuation.positions.isNotEmpty() && (werte.lastOrNull()?.first ?: 0L) < jetzt - 60_000L) werte = werte + (jetzt to valuation.securities)
    return werte
}

// Zeitleiste ueber den Seiten; liest die Seite selbst, damit nur sie beim Wischen neu gezeichnet wird.
@Composable
private fun PerfLeiste(pager: PagerState, zeitraeume: List<String>, eigenAn: Boolean, onEigen: () -> Unit, onSelect: (Int) -> Unit) {
    ZeitLeiste(
        zeitraeume.map { S.ppfZeitraum(it) }, selected = if (eigenAn) -1 else pager.currentPage, onSelect = onSelect,
        modifier = Modifier.padding(horizontal = 12.dp), trailing = { ZeitraumKnopf(eigenAn, onEigen) },
    )
}

// Ein Zeitraum: Kennzahlen, Kurve, Kapitalbilanz, Monate und Jahre.
@Composable
private fun PerformanceSeite(
    e: Performanceanalyse.Ergebnis, txs: List<de.troetenrepublik.app.data.Transaction>, zerlegung: AppState.Zerlegung?, onOpen: (String) -> Unit,
    zins: Double = 0.0, state: AppState? = null,
) {
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
    KeyValueRow(if (e.izfProJahr) S.ppfIzf else S.ppfIzfZeitraum, e.izf?.let { vorzeichen(it) } ?: "–", valueColor = e.izf?.let { signal.forChange(if (abs(it * 100) < 0.000_5) 0.0 else it) }, hint = S.ppfIzfHinweis) // 0,00 % nicht rot
    KeyValueRow(S.ppfAbsolut, Fmt.signedEuro(b.absolut), valueColor = signal.forChange(b.absolut))
    e.letzterTag?.let { KeyValueRow(S.ppfLetzterTag, vorzeichen(it), valueColor = signal.forChange(if (abs(it * 100) < 0.000_5) 0.0 else it)) }
    e.nurWertpapiere?.let { KeyValueRow(S.ppfNurWertpapiere, vorzeichen(it), valueColor = signal.forChange(it), hint = S.ppfNurWertpapiereHinweis) }

    SectionHeader(S.ppfRisiko)
    KeyValueRow(S.ppfMaxRueckgang, if (e.maxRueckgang > 0.000_05) "−" + Fmt.pct(e.maxRueckgang * 100, 1) else Fmt.pct(0.0, 1))
    KeyValueRow(S.ppfRueckgangDauer, S.ppfTage(e.rueckgangTage), hint = S.ppfRueckgangDauerHinweis)
    val index = remember(e) { e.kurve.map { it.first to 1.0 + it.second / 100.0 } }
    // Erst ab einem vollen Tag: am Kauftag stuende sonst „100 %“ neben „0 Tage“
    remember(index) { if (index.size >= 2 && index.last().first - index.first().first >= 86_400_000L) Performanceanalyse.rueckgaenge(index) else null }?.let { r ->
        KeyValueRow(S.ppfZeitUnterHoch, Fmt.pct(r.anteilUnterHoch * 100, 0))
        KeyValueRow(S.ppfMittlererRueckgang, if (r.mittlerer > 0.000_05) "−" + Fmt.pct(r.mittlerer * 100, 1) else Fmt.pct(0.0, 1), hint = S.ppfMittlererRueckgangHinweis)
    }
    KeyValueRow(S.toolVolatility, e.volatilitaet?.let { Fmt.pct(it * 100, 1) } ?: "–")
    KeyValueRow(S.ppfSemivolatilitaet, e.semivolatilitaet?.let { Fmt.pct(it * 100, 1) } ?: "–", hint = S.ppfSemivolatilitaetHinweis)
    // Sharpe und Sortino aus derselben Rechnung wie in der Tabelle des Depotverlaufs (Risiko.kennzahlen)
    remember(index, zins) { de.troetenrepublik.app.engine.Risiko.kennzahlen(index, zins) }?.let { k ->
        k.sharpe?.let { InfoWertZeile(S.pvMetric("sharpe"), Fmt.num(it, 2), S.pvMetric("sharpe"), S.pvMetricExplain("sharpe")) }
        k.sortino?.let { InfoWertZeile(S.pvMetric("sortino"), Fmt.num(it, 2), S.pvMetric("sortino"), S.pvMetricExplain("sortino")) }
    }
    KeyValueRow(S.ppfHoch, vorzeichen(e.hoch.second / 100), hint = Fmt.date(e.hoch.first))
    // [0.73] Index der zeitgewichteten Kurve (Start 1), daraus Rueckgang, Verteilung und rollierende Werte
    RisikoAbschnitte("depot", index, monatsraster = false)
    if (state != null) RenditeZusatz(state, index)

    SectionHeader(S.ppfBilanz)
    KeyValueRow(S.ppfWertAm(Fmt.date(e.von)), Fmt.euro(b.anfang))
    KeyValueRow(S.ppfEinlagen, zuEuro(b.einlagen, "+"))
    KeyValueRow(S.ppfEntnahmen, zuEuro(b.entnahmen, "−"))
    KeyValueRow(S.ppfKursgewinne, Fmt.signedEuro(b.kursgewinne), valueColor = signal.forChange(b.kursgewinne), hint = S.ppfRealisiert(Fmt.signedEuro(b.realisiert)))
    KeyValueRow(S.ppfDividenden, zuEuro(b.dividenden, "+"))
    KeyValueRow(S.ppfZinsen, zuEuro(b.zinsen, "+"))
    KeyValueRow(S.calFees, zuEuro(b.gebuehren, "−"))
    KeyValueRow(S.taxes, zuEuro(b.steuern, "−"))
    KeyValueRow(S.ppfWertAm(Fmt.date(e.bis)), Fmt.euro(b.ende))

    // Geldfluss und Ergebnis der Verkaeufe als Sankey-Diagramme (5.94)
    val fluss = remember(b) { de.troetenrepublik.app.engine.Sankey.depot(b) }
    if (!fluss.leer) {
        SectionHeader(S.skGeldfluss)
        SankeyDiagramm(fluss, name = { S.skKnoten(it.id) }, betrag = { Fmt.euro(it) })
    }
    val verkauf = remember(txs, e.von, e.bis) { de.troetenrepublik.app.engine.Sankey.verkaeufe(txs, e.von, e.bis) }
    if (!verkauf.leer) {
        SectionHeader(S.skVerkaeufe)
        SankeyDiagramm(verkauf, name = { S.skKnoten(it.id) }, betrag = { Fmt.euro(it) })
    }

    // Gewinne und Rendite je Wertpapier, im Hintergrund gerechnet
    val je by produceState<de.troetenrepublik.app.engine.Wertpapierbilanz.Ergebnis?>(null, e, zerlegung, txs) {
        value = zerlegung?.let { z ->
            withContext(Dispatchers.Default) {
                de.troetenrepublik.app.engine.Wertpapierbilanz.berechnen(z.tage, txs, z.namen, z.symbole, z.imDepot, e.von, e.bis, e.izfProJahr)
            }
        }
    }
    je?.let { WertpapierBilanzAbschnitt(it, onOpen) }

    // Raster Jahr mal Monat aus den zeitgewichteten Monatswerten, rechts die Jahresrendite
    val raster = remember(e) { Monatsraster.ausMonaten(e.monate.mapKeys { it.key.year to it.key.monthValue }, e.jahre) }
    if (raster != null) {
        SectionHeader(S.ppfMonate)
        RasterTabelle(raster)
    }
}

// Betrag mit dem Vorzeichen seiner Richtung (Einlage +, Gebuehr −); ein negativer Wert (Erstattung, Korrektur)
// dreht das Zeichen, null steht ohne Vorzeichen.
private fun zuEuro(v: Double, zeichen: String): String = when {
    abs(v) < 0.005 -> Fmt.euro(0.0)
    v < 0 -> (if (zeichen == "−") "+" else "−") + Fmt.euro(-v)
    else -> zeichen + Fmt.euro(v)
}
