package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Markttechnik
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Trend aus Hoch- und Tiefpunkten (0.78): Kernzeile unter dem erweiterten Chart und Stopp-Vorschlag in der
// Positionsgroesse.

// Zustand des Trends links, die Bruchmarke rechts; gerechnet auf den gezeigten Kerzen wie die Linien im Chart.
@Composable
internal fun TrendZeile(kerzen: List<Candle>) {
    if (kerzen.size < 2) return
    val la = remember(kerzen) { Markttechnik.lage(kerzen.map { it.high }, kerzen.map { it.low }, kerzen.map { it.close }) }
    val marke = la.marke
    if (marke == null) {
        Text(
            S.mtZustand(la.zustand.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    } else KeyValueRow(S.mtZustand(la.zustand.name), S.mtMarke(la.zustand.name, Fmt.priceEuro(marke)))
}

// Letztes Korrekturtief eines laufenden Aufwaertstrends als Stoppkurs; ein Tipp uebernimmt den Abstand in die Rechnung.
// Ohne Aufwaertstrend erscheint nichts. kurs: aktueller Kurs in Euro.
@Composable
internal fun TrendStopp(state: AppState, symbol: String, kurs: Double, onWahl: (Double) -> Unit) {
    val stopp by produceState<Markttechnik.Stopp?>(null, symbol) {
        val l = state.dailyCloses(symbol).filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        value = withContext(Dispatchers.Default) {
            // Fehlen Hoch und Tief, zaehlt der Schlusskurs
            Markttechnik.stopp(
                l.map { if (it.high > 0 && it.high.isFinite()) maxOf(it.high, it.close) else it.close },
                l.map { if (it.low > 0 && it.low.isFinite()) minOf(it.low, it.close) else it.close },
                l.map { it.close },
            )
        }
    }
    val s = stopp ?: return
    if (!(s.abstandPct > 0 && s.abstandPct < 100)) return
    KeyValueRow(
        S.mtStopp, Fmt.priceEuro(kurs * (1 - s.abstandPct / 100)), hint = S.mtStoppHinweis(Fmt.pct(s.abstandPct, 1)),
        onClick = { onWahl(s.abstandPct) },
    )
}
