package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Walk-Forward im Strategietest: gerechnet wird auf Tipp ueber die ganze Kursgeschichte, im Hintergrund mit
// Fortschritt; das Ergebnis je Wertpapier und Familie bleibt fuer die Sitzung gemerkt.

private class WfEintrag(val ergebnis: StrategieLabor.WalkForward?)

// Zuletzt gerechnete Ergebnisse, aelteste zuerst; nur aus der Oberflaeche heraus (Hauptfaden) benutzt.
private val wfSpeicher = LinkedHashMap<String, WfEintrag>()

private fun wfMerken(schluessel: String, e: WfEintrag) {
    wfSpeicher.remove(schluessel)
    wfSpeicher[schluessel] = e
    while (wfSpeicher.size > 8) wfSpeicher.remove(wfSpeicher.keys.first())
}

private const val WF_MIN_JAHRE = 12.0
private const val JAHR_MS = 365.25 * 86_400_000.0

private fun proJahr(v: Double?): String = v?.let { Fmt.signedPct(it * 100, 1) } ?: "–"

// Hoechstens max gleichmaessig verteilte Indizes von 0 bis n - 1, der letzte ist immer dabei.
internal fun duennIndizes(n: Int, max: Int): List<Int> =
    if (n <= max) (0 until n).toList() else (0 until max).map { (it * (n - 1).toDouble() / (max - 1)).toInt() }

private class WfZeile(val beginn: String, val regel: String, val lern: String, val pruef: String, val halten: String, val pruefWert: Double)

@Composable
internal fun WalkForwardInhalt(
    symbol: String, k: List<Candle>, kosten: StrategieLabor.Kosten, mitDividenden: Boolean,
    taktik: de.troetenrepublik.app.engine.Taktik.Reihen? = null, kennung: String = "",
) {
    val signal = LocalSignal.current
    val vorhanden = (k.last().t - k.first().t) / JAHR_MS
    if (vorhanden < WF_MIN_JAHRE) { ToolNote(S.slWfZuWenig); return }
    var familie by remember { mutableStateOf(StrategieLabor.Familie.ALLE) }
    ToolChoice(null, StrategieLabor.Familie.entries.map { S.slFamilie(it.name) to it }, familie) { familie = it }
    val schluessel = "$symbol|${familie.name}|${kosten.spreadBps}|${k.size}|${k.last().t}|$mitDividenden|$kennung"
    var gestartet by remember(schluessel) { mutableStateOf(wfSpeicher.containsKey(schluessel)) }
    if (!gestartet) {
        ActionLineWide(S.slWfStart) { gestartet = true }
        return
    }
    var fortschritt by remember(schluessel) { mutableFloatStateOf(0f) }
    val eintrag by produceState<WfEintrag?>(wfSpeicher[schluessel], schluessel) {
        if (value != null) return@produceState
        val e = withContext(Dispatchers.Default) {
            WfEintrag(StrategieLabor.walkForward(laborDaten(k, taktik), 0, kosten, familie, fortschritt = { fortschritt = it }) { ensureActive() })
        }
        wfMerken(schluessel, e)
        value = e
    }
    val e = eintrag ?: run { ToolNote(S.slWfLaeuft(Fmt.pct(fortschritt * 100.0, 0))); return }
    val wf = e.ergebnis ?: run { ToolNote(S.slWfZuWenig); return }

    val vorn = wf.zusammen.endwert >= wf.halten.endwert
    KeyValueRow(S.slWfFenster, wf.fenster.size.toString())
    VergleichTabelle(
        listOf(VergleichSpalte(S.slDieRegel, Kategorie.farbe(0)), VergleichSpalte(S.slHaltenKurz, Kategorie.farben.last())),
        listOf(
            VergleichZeile(
                S.slWfZusammen, listOf(proJahr(wf.zusammen.renditeJahr), proJahr(wf.halten.renditeJahr)),
                renditeFarben(wf.zusammen.renditeJahr, wf.halten.renditeJahr),
            ),
        ),
    )
    KeyValueRow(S.slWfBesser, S.slWfBesserWert(wf.besserJahre, wf.fenster.size))
    KeyValueRow(S.calTrades, wf.zusammen.trades.toString())
    wf.pruefZuLern?.let { KeyValueRow(S.slWfAbfall, Fmt.pct(it * 100.0, 0)) }
    AussageZeile(S.slAussage, wf.zusammen)

    // Kapitalkurve nur aus den Pruefjahren, gegen Kaufen und Halten
    val z = wf.zusammen.verlauf
    val h = wf.halten.verlauf
    val idx = remember(wf) { duennIndizes(minOf(z.size, h.size), 300) }
    val zeiten = remember(wf) { idx.map { z[it].first } }
    val regelWerte = remember(wf) { idx.map { z[it].second } }
    val haltenWerte = remember(wf) { idx.map { h[it].second } }
    val grenzen = remember(wf) { wf.fenster.map { k[it.pruefVon].t } }
    var gewaehlt by remember(wf) { mutableStateOf<Int?>(null) }
    val stelle = gewaehlt ?: (zeiten.size - 1)
    val farbeRegel = Kategorie.farbe(0)
    val farbeHalten = Kategorie.farben.last()
    VergleichTabelle(
        listOf(VergleichSpalte(S.slDieRegel, farbeRegel), VergleichSpalte(S.slHaltenKurz, farbeHalten)),
        listOf(VergleichZeile(Fmt.date(zeiten[stelle]), listOf(Fmt.euro(regelWerte[stelle], 0), Fmt.euro(haltenWerte[stelle], 0)))),
    )
    WalkForwardChart(
        zeiten, regelWerte, haltenWerte, grenzen, farbeRegel, farbeHalten,
        S.slDieRegel + " " + Fmt.euro(regelWerte.last(), 0), S.slHaltenKurz + " " + Fmt.euro(haltenWerte.last(), 0),
        StrategieLabor.START, gewaehlt, { gewaehlt = it }, S.slWf,
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )

    // Tabelle je Fenster, neueste zuerst, in festen Spalten (Breite aus dem breitesten Eintrag)
    val zeilen = remember(wf) {
        wf.fenster.reversed().map { f ->
            val (monat, jahr) = StrategieLabor.monatJahr(k[f.pruefVon].t)
            val ergebnis = f.pruef.endwert / StrategieLabor.START - 1.0
            WfZeile(
                (if (monat < 10) "0$monat" else "$monat") + "/$jahr", S.slWfRegel(f.regel.art.name, f.regel.a, f.regel.b, f.regel.n),
                proJahr(f.lern.renditeJahr), Fmt.signedPct(ergebnis * 100, 1),
                Fmt.signedPct((f.halten.endwert / StrategieLabor.START - 1.0) * 100, 1), ergebnis,
            )
        }
    }
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val stil = MaterialTheme.typography.bodySmall
    val kopfStil = MaterialTheme.typography.labelSmall
    val kopf = listOf(S.slWfKopfBeginn, S.slWfKopfRegel, S.slWfKopfLern, S.slWfKopfPruef, S.slWfKopfHalten)
    val breiten = remember(zeilen, stil, kopfStil, dichte) {
        val spalten = listOf<(WfZeile) -> String>({ it.beginn }, { it.regel }, { it.lern }, { it.pruef }, { it.halten })
        spalten.mapIndexed { i, f ->
            var b = messer.measure(kopf[i], kopfStil, maxLines = 1, softWrap = false).size.width
            zeilen.forEach { z -> b = maxOf(b, messer.measure(f(z), stil, maxLines = 1, softWrap = false).size.width) }
            with(dichte) { b.toDp() }
        }
    }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun reihe(texte: List<String>, stile: androidx.compose.ui.text.TextStyle, farben: List<Color>) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            texte.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.width(8.dp))
                Text(
                    t, style = stile, color = farben[i], maxLines = 1, softWrap = false,
                    textAlign = if (i == 1 || i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.width(breiten[i]),
                )
            }
        }
    }
    Column(Modifier.padding(top = 8.dp)) {
        reihe(kopf, kopfStil, List(5) { grau })
        zeilen.forEach { zl ->
            reihe(
                listOf(zl.beginn, zl.regel, zl.lern, zl.pruef, zl.halten), stil,
                listOf(grau, MaterialTheme.colorScheme.onSurface, grau, signal.forChange(zl.pruefWert), grau),
            )
        }
    }
}
