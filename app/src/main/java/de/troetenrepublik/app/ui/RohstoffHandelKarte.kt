package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Eingepreist
import de.troetenrepublik.app.engine.RohstoffHandel
import de.troetenrepublik.app.engine.Rohstoffe
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.terminkurve

// Terminkurve und Optionshandel bei Rohstoffen (0.78). Die Terminkurve zeigt die naechsten gehandelten Kontrakte mit Preis
// und Abstand zum Kurs der Seite. Der Optionshandel stuetzt sich auf den ETF, der den Rohstoff abbildet
// (engine/RohstoffHandel.kt); ohne ETF mit Optionen oder ohne Daten entfaellt der Abschnitt.
@Composable
internal fun RohstoffHandelKarte(state: AppState, symbol: String, name: String, kurs: Double, waehrung: String, merker: SeitenMerker) {
    val termin = remember(symbol) { RohstoffHandel.termin(symbol) }
    val etf = remember(symbol) { RohstoffHandel.etf(symbol) }
    if (termin == null && etf == null) return
    Column(Modifier.fillMaxWidth()) {
        if (termin != null) Terminkurve(state, symbol, termin, kurs, waehrung, merker)
        if (etf != null) OptionenUeberEtf(state, symbol, name, etf, merker)
    }
}

@Composable
private fun Terminkurve(state: AppState, symbol: String, termin: RohstoffHandel.Termin, kurs: Double, waehrung: String, merker: SeitenMerker) {
    var kurve by gemerkt<List<Pair<Eingepreist.Kontrakt, Double>>?>(merker, "rohhandel/kurve") { null }
    LaunchedEffect(symbol) { if (kurve == null) kurve = runCatching { state.terminkurve(termin, 6) }.getOrNull() }
    val zeilen = remember(kurve, kurs) { RohstoffHandel.zeilen(kurve.orEmpty(), kurs) }
    if (zeilen.size < 2) return
    // Kern: Richtung der Kurve am letzten Kontrakt, auf ein Jahr gerechnet
    val ende = zeilen.last()
    val art = ende.aufsJahr?.let { if (it >= 1.0) 0 else if (it <= -1.0) 1 else 2 } ?: 2
    KernAbschnitt(
        S.roh("t.titel"),
        KernZeile(listOf(KernTeil(S.roh("t.kern.$art"))), ende.aufsJahr?.let { KernTeil(Fmt.signedPct(it, 1) + " " + S.roh("t.jahr")) }),
        merker, "rohhandel/kurve",
    ) {
        val grau = MaterialTheme.colorScheme.onSurfaceVariant
        @Composable
        fun Reihe(a: String, b: String, c: String, kopf: Boolean) {
            val stil = if (kopf) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
            val farbe = if (kopf) grau else MaterialTheme.colorScheme.onSurface
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (kopf) 2.dp else 6.dp)) {
                Text(a, Modifier.weight(1.2f), style = stil, color = farbe, maxLines = 1, softWrap = false)
                Text(b, Modifier.weight(1f), style = stil, color = farbe, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
                Text(c, Modifier.weight(1f), style = stil, color = farbe, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
            }
        }
        // Die Kurve der Kontrakte mit dem Kurs der Seite als gestrichelter Linie: steigt sie, ist die spaetere Lieferung teurer
        TerminLinie(zeilen.map { it.preis }, kurs, Fmt.monthShortYear(java.time.YearMonth.of(zeilen.first().jahr, zeilen.first().monat)),
            Fmt.monthShortYear(java.time.YearMonth.of(ende.jahr, ende.monat)), Kategorie.blau)
        Reihe(S.roh("t.lieferung"), S.roh("t.preis"), S.roh("t.abstand"), true)
        zeilen.forEach { z ->
            Reihe(Fmt.monthShortYear(java.time.YearMonth.of(z.jahr, z.monat)), Fmt.price(z.preis, waehrung), Fmt.signedPct(z.abstand, 1), false)
        }
        HinweisInfo(S.roh("t.info"))
    }
}

// Preise der Kontrakte als Linie mit Punkten, der Kurs der Seite gestrichelt; links und rechts der erste und letzte Monat.
@Composable
private fun TerminLinie(preise: List<Double>, kurs: Double, von: String, bis: String, farbe: Color) {
    if (preise.size < 2) return
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val stil = MaterialTheme.typography.labelSmall.copy(color = grau)
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(88.dp)) {
        val fuss = 16.dp.toPx()
        val rand = 5.dp.toPx()
        val alle = if (kurs > 0) preise + kurs else preise
        val lo = alle.min()
        val hi = alle.max().let { if (it - lo < 1e-9) lo + 1.0 else it }
        val h = size.height - fuss - 2 * rand
        fun x(i: Int) = rand + (size.width - 2 * rand) * i / (preise.size - 1)
        fun y(v: Double) = rand + h * (1f - ((v - lo) / (hi - lo)).toFloat())
        if (kurs > 0) drawLine(grau, Offset(0f, y(kurs)), Offset(size.width, y(kurs)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        val pfad = Path()
        preise.forEachIndexed { i, v -> if (i == 0) pfad.moveTo(x(i), y(v)) else pfad.lineTo(x(i), y(v)) }
        drawPath(pfad, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        preise.forEachIndexed { i, v -> drawCircle(farbe, 3.dp.toPx(), Offset(x(i), y(v))) }
        val a = messer.measure(von, stil)
        val b = messer.measure(bis, stil)
        drawText(a, topLeft = Offset(0f, size.height - a.size.height))
        drawText(b, topLeft = Offset(size.width - b.size.width, size.height - b.size.height))
    }
}

@Composable
private fun OptionenUeberEtf(state: AppState, symbol: String, name: String, etf: RohstoffHandel.Etf, merker: SeitenMerker) {
    // Gibt es fuer den ETF ueberhaupt gelistete Optionen? Ein Abruf je Sitzung; ohne Termine kein Abschnitt.
    var termine by gemerkt<List<Long>?>(merker, "rohhandel/termine") { null }
    var umsatz by gemerkt<MarketApi.OptionVolumes?>(merker, "rohhandel/umsatz") { null }
    LaunchedEffect(etf.kuerzel) {
        if (termine == null) termine = runCatching { state.market.optionsTermine(etf.kuerzel) }.getOrNull().orEmpty()
        if (termine.orEmpty().isNotEmpty() && umsatz == null) umsatz = runCatching { state.market.optionVolumes(etf.kuerzel) }.getOrNull()
    }
    if (termine.orEmpty().isEmpty()) return
    var detail by remember(symbol) { mutableStateOf(false) }
    var rechner by remember(symbol) { mutableStateOf(false) }
    val rohstoff = S.roh("n." + (Rohstoffe.schluessel(symbol) ?: "KORB"))
    SectionHeader(S.roh("o.titel"))
    HinweisInfo(S.roh("o.auf." + etf.art.name, listOf(etf.kuerzel, rohstoff)))
    umsatz?.takeIf { it.total > 0 }?.let { LongShortBars(it.longPct, onLong = { detail = true }, onShort = { detail = true }) }
    NavRow(S.opTitel, etf.kuerzel) { detail = true }
    NavRow(S.orKarte, etf.kuerzel) { rechner = true }
    if (detail) DetailBlatt(S.opTitel, name + PUNKT + etf.kuerzel, { detail = false }) {
        val hv by produceState<Double?>(null, etf.kuerzel) { value = runCatching { state.historischeVol(etf.kuerzel) }.getOrNull() }
        OptionenKarte(state, etf.kuerzel, "USD", hv, kopf = false)
    }
    if (rechner) OptionsrechnerBlatt(state, etf.kuerzel, onDismiss = { rechner = false })
}
