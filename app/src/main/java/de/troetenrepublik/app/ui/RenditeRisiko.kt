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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Aktienkennzahlen
import de.troetenrepublik.app.i18n.S

// Rendite pro Jahr ueber 1, 5 und 10 Jahre, Schwankung, groesster und laengster Rueckgang, Beta und Korrelation
// zum Index der Heimatboerse. Ohne Sharpe-Ratio und aehnliche Kennzahlen, die bei einem einzelnen Wert mehr
// versprechen, als sie halten.
@Composable
internal fun RenditeRisiko(state: AppState, symbol: String, land: String?) {
    var ergebnis by remember(symbol) { mutableStateOf<Pair<Aktienkennzahlen.Ergebnis?, String?>?>(null) }
    LaunchedEffect(symbol, land) { ergebnis = runCatching { state.aktienkennzahlen(symbol, land) }.getOrDefault(null to null) }
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.rrTitel)
        val geladen = ergebnis
        val e = geladen?.first
        if (e == null) {
            Text(if (geladen == null) S.rrLaedt else S.rrKeine, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            return@Column
        }
        fun rendite(v: Double?) = v?.let { Fmt.signedPct(it * 100, 1) } ?: "–"
        fun farbe(v: Double?) = v?.let { if (it >= 0) signal.up else signal.down }
        KeyValueRow(S.rrRendite(1), rendite(e.rendite1), farbe(e.rendite1))
        KeyValueRow(S.rrRendite(5), rendite(e.rendite5), farbe(e.rendite5))
        KeyValueRow(S.rrRendite(10), rendite(e.rendite10), farbe(e.rendite10))
        e.schwankung?.let { KeyValueRow(S.rrSchwankung, Fmt.pct(it * 100, 1)) }
        KeyValueRow(S.rrMaxRueckgang, Fmt.signedPct(e.maxRueckgang * 100, 1) + " (" + Fmt.date(e.maxRueckgangTag) + ")", signal.down)
        KeyValueRow(S.rrLaengster, S.rrDauer(e.laengsterRueckgangTage) + if (e.laufend) " " + S.rrLaeuft else "")
        val index = geladen.second
        if (index != null && e.beta != null) KeyValueRow(S.rrBeta(index), Fmt.num(e.beta, 2))
        if (index != null && e.korrelation != null) KeyValueRow(S.rrKorrelation(index), Fmt.num(e.korrelation, 2))
        AufklappText(S.rrHinweis(Fmt.date(e.ab), index, e.beta?.let { Fmt.num(it, 2) }), Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp))
    }
}
