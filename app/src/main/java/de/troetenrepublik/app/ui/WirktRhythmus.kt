package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Rhythmus16
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.rhythmus16
import kotlin.math.abs
import kotlin.math.ln

// [0.78] Pruefung des 16-Wochen-Rhythmus in "Wirkt das?": Ergebnis zuerst, dann die Zahlen und die Aenderung je Woche des
// Zyklus als Balken. Rechnung und Anker in engine/Rhythmus16.kt, ohne Kosten; die letzte Zeile oeffnet den Strategietest
// des DAX mit dieser Regel (mit Spread und Gebuehr). Ohne Kursreihe erscheint der Abschnitt nicht.
@Composable
internal fun RhythmusAbschnitt(state: AppState) {
    val ergebnis by produceState<Rhythmus16.Ergebnis?>(null) { value = runCatching { state.rhythmus16() }.getOrNull() }
    val r = ergebnis ?: return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun faktor(f: Double) = Fmt.signedPct((f - 1) * 100, 0)
    // Strategietest des DAX mit vorgewaehlter Regel: dieselbe Regel mit Spread und Gebuehr
    var labor by remember { mutableStateOf(false) }
    if (labor) StrategieLaborSheet(
        state, de.troetenrepublik.app.MarktzeichenSpeicher.RHYTHMUS_INDEX, "DAX", onDismiss = { labor = false },
        startRegel = StrategieLabor.REGELN.firstOrNull { it.art == StrategieLabor.Art.RHYTHMUS },
    )
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.ml("rh.titel"))
        Text(
            S.ml(if (r.strategie > r.halten) "rh.besser" else "rh.schlechter"), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Kennzahlen(
            listOf(
                S.ml("rh.strategie") to Fmt.signedPct(r.strategieJahr * 100, 1),
                S.ml("rh.halten") to Fmt.signedPct(r.haltenJahr * 100, 1),
                S.ml("rh.zeit") to Fmt.pct(r.anteilZeit * 100, 0),
                S.ml("rh.plus") to Fmt.pct(r.imPlus * 100, 0),
                S.ml("rh.beste", listOf(r.beste.toString())) to faktor(r.jeWoche[r.beste - 1]),
                S.ml("rh.schwaechste", listOf(r.schwaechste.toString())) to faktor(r.jeWoche[r.schwaechste - 1]),
            ),
        )
        Text(S.ml("rh.achse"), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
        WochenBalken(r.jeWoche)
        Text(
            S.ml("rh.regel", listOf(Fmt.date(r.von * 86_400_000L + 43_200_000L), Fmt.date(r.bis * 86_400_000L + 43_200_000L))),
            style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        HinweisInfo(hinweisText("hist"))
        NavRow(S.slTitel, S.ml("vw.rh.kosten")) { labor = true }
    }
}

// Je Woche des Zyklus ein Balken um die Nulllinie, Hoehe nach dem Logarithmus des Faktors (gleiche Hoehe fuer Verdoppeln
// und Halbieren). Die Nummer steht unter dem Balken, die investierten Wochen sind hervorgehoben.
@Composable
internal fun WochenBalken(jeWoche: List<Double>, rand: androidx.compose.ui.unit.Dp = 20.dp) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.outlineVariant
    val logs = jeWoche.map { ln(it.coerceAtLeast(1e-9)) }
    val spanne = logs.maxOf { abs(it) }.coerceAtLeast(1e-9)
    Column(Modifier.fillMaxWidth().padding(horizontal = rand, vertical = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val feld = size.width / jeWoche.size
            val mitte = size.height / 2
            val breite = feld * 0.62f
            logs.forEachIndexed { i, v ->
                val h = (abs(v) / spanne).toFloat() * (mitte - 2.dp.toPx())
                val x = i * feld + (feld - breite) / 2
                drawRoundRect(
                    if (v >= 0) signal.up else signal.down, Offset(x, if (v >= 0) mitte - h else mitte), Size(breite, h.coerceAtLeast(1f)),
                    CornerRadius(2.dp.toPx()),
                )
            }
            drawLine(linie, Offset(0f, mitte), Offset(size.width, mitte), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            for (i in jeWoche.indices) {
                val an = (i + 1) in Rhythmus16.INVESTIERT
                Text(
                    (i + 1).toString(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    color = if (an) MaterialTheme.colorScheme.onSurface else grau, fontWeight = if (an) FontWeight.Bold else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
