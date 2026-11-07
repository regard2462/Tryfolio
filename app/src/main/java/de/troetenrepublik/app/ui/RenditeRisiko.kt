package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Aktienkennzahlen
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs

// Wie tief kann es fallen: zuerst der groesste Rueckgang und wie lange es bis zum
// alten Hoch dauerte, damit klar ist, welche Durststrecke man mit dem Wert aushalten muesste. Darunter Rendite pro Jahr
// ueber 1, 5 und 10 Jahre, Beta und Korrelation zum Index der Heimatboerse. Jeder Begriff hat eine kurze Erklaerung
// als Unterzeile. Ohne Sharpe-Ratio und aehnliche Kennzahlen, die bei einem einzelnen Wert mehr versprechen, als sie halten.
// Eingeklappt als Zeile unter Werkzeuge (5.93): die Kurse werden erst beim Aufklappen geladen. Die Schwankung steht in den Kennzahlen.
@Composable
internal fun RenditeRisiko(state: AppState, symbol: String, land: String?) {
    var offen by rememberSaveable(symbol) { mutableStateOf(false) }
    AufklappZeile(S.rrTitel, S.bwRrUnter, offen) { offen = !offen }
    if (!offen) return
    var ergebnis by remember(symbol) { mutableStateOf<Pair<Aktienkennzahlen.Ergebnis?, String?>?>(null) }
    var kurse by remember(symbol) { mutableStateOf<List<Candle>>(emptyList()) }
    LaunchedEffect(symbol, land) {
        ergebnis = runCatching { state.aktienkennzahlen(symbol, land) }.getOrDefault(null to null)
        kurse = state.dailyCloses(symbol)
    }
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        Text(S.rrNutzen, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
        val geladen = ergebnis
        val e = geladen?.first
        if (e == null) {
            Text(if (geladen == null) S.rrLaedt else S.rrKeine, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        } else {
            // Groesster Rueckgang ueber alle Kurse und die Zeit vom Tief zurueck zum alten Hoch
            val tage = if (Symbols.typeOf(symbol) == AssetType.CRYPTO) 365 else 252
            val r = remember(kurse) { kurse.firstOrNull()?.let { Analysis.risk(kurse, it.t, tage) } }
            if (r != null) {
                KeyValueRow(S.copyMaxDrawdown, "−" + Fmt.pct(abs(r.maxDrawdown), 1) + " · " + S.rrVonBis(Fmt.date(r.peakAt), Fmt.date(r.troughAt)), signal.down, hint = S.rrHintMax)
                KeyValueRow(S.rrErholung, r.recoveredAt?.let { S.rrDauer((it - r.troughAt) / 86_400_000L) } ?: S.rrNochNicht, hint = S.rrHintErholung)
            } else {
                KeyValueRow(S.copyMaxDrawdown, Fmt.signedPct(e.maxRueckgang * 100, 1) + " (" + Fmt.date(e.maxRueckgangTag) + ")", signal.down, hint = S.rrHintMax)
            }
            KeyValueRow(S.rrLaengster, S.rrDauer(e.laengsterRueckgangTage) + if (e.laufend) " " + S.rrLaeuft else "", hint = S.rrHintLaengster)
            fun rendite(v: Double?) = v?.let { Fmt.signedPct(it * 100, 1) } ?: "–"
            fun farbe(v: Double?) = v?.let { if (it >= 0) signal.up else signal.down }
            KeyValueRow(S.rrRendite(1), rendite(e.rendite1), farbe(e.rendite1), hint = S.rrHintRendite)
            KeyValueRow(S.rrRendite(5), rendite(e.rendite5), farbe(e.rendite5), hint = S.rrHintRendite)
            KeyValueRow(S.rrRendite(10), rendite(e.rendite10), farbe(e.rendite10), hint = S.rrHintRendite)
            val index = geladen.second
            if (index != null && e.beta != null) KeyValueRow(S.rrBeta(index), Fmt.num(e.beta, 2), hint = S.rrHintBeta)
            if (index != null && e.korrelation != null) KeyValueRow(S.rrKorrelation(index), Fmt.num(e.korrelation, 2), hint = S.rrHintKorrelation)
            AufklappText(S.rrHinweis(Fmt.date(e.ab), index, e.beta?.let { Fmt.num(it, 2) }), Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp))
        }
    }
}
