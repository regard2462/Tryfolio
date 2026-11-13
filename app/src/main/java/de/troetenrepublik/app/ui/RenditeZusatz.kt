package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Renditeprofil
import de.troetenrepublik.app.i18n.DepotZusatzTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.Portfolioverlauf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Zusatz zur Rendite des Depots: Tabelle der groessten Rueckgaenge und, eingeklappt, die Kennzahlen gegen den
// Vergleichsindex. index ist die zeitgewichtete Kurve des Depots (Start 1).

internal fun dzText(key: String, vararg w: String): String = DepotZusatzTexte.text(I18n.lang == Lang.DE, key, w.toList())

@Composable
internal fun RenditeZusatz(state: AppState, index: List<Pair<Long, Double>>) {
    if (index.size < 3) return
    AufklappAbschnitt("depot-rueckgaenge", dzText("rg.titel"), dzText("rg.info")) { RueckgangTabelle(index) }
    AufklappAbschnitt("depot-index", dzText("ix.titel"), dzText("ix.info")) { IndexKennzahlen(state, index) }
}

@Composable
private fun RueckgangTabelle(index: List<Pair<Long, Double>>) {
    val phasen = remember(index) { Renditeprofil.rueckgaenge(index) }
    if (phasen.isEmpty()) { LeerZustand(dzText("rg.leer")); return }
    val signal = LocalSignal.current
    val ende = index.last().first
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val klein = MaterialTheme.typography.labelSmall
    phasen.forEachIndexed { i, p ->
        if (i > 0) Trennlinie()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dzText("rg.ab", Fmt.date(p.beginn)), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                Text("−" + Fmt.pct(p.tiefe * 100, 1), style = MaterialTheme.typography.titleSmall, color = signal.down, maxLines = 1, softWrap = false)
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dzText("rg.tief", Fmt.date(p.tief)), style = klein, color = grau, maxLines = 1, softWrap = false)
                Text(
                    p.erholung?.let { dzText("rg.erholt", Fmt.date(it)) } ?: dzText("rg.offen"), style = klein,
                    color = if (p.erholung == null) signal.down else grau, maxLines = 1, softWrap = false,
                )
                Text(dzText("tage", Fmt.num(p.tageGesamt(ende).toDouble(), 0)), style = klein, color = grau, maxLines = 1, softWrap = false)
            }
            // Tiefe im Verhaeltnis zum tiefsten Rueckgang
            LageBalken((p.tiefe / phasen.first().tiefe).coerceIn(0.0, 1.0), signal.down, Modifier.padding(top = 5.dp))
        }
    }
}

private class IndexWerte(val anteile: Renditeprofil.Anteile?, val omega: Double?, val schmerz: Double?)

@Composable
private fun IndexKennzahlen(state: AppState, index: List<Pair<Long, Double>>) {
    val massstab = Portfolioverlauf.MASSSTAEBE.first()
    val werte by produceState<IndexWerte?>(null, index) {
        val kurse = runCatching { state.dailyCloses(massstab.symbol) }.getOrDefault(emptyList()).map { it.t to it.close }
        value = withContext(Dispatchers.Default) {
            val paare = Renditeprofil.monatsPaare(index, kurse)
            // Ohne Vergleichskurse bleiben Omega und Gewinn zu Schmerz aus den Monaten des Depots allein
            val monate = paare.map { it.first }.ifEmpty { Renditeprofil.monatsPaare(index, index).map { it.first } }
            IndexWerte(paare.takeIf { it.isNotEmpty() }?.let { Renditeprofil.anteile(it) }, Renditeprofil.omega(monate), Renditeprofil.gewinnZuSchmerz(monate))
        }
    }
    val w = werte
    if (w == null) { LeerZustand(de.troetenrepublik.app.i18n.S.toolLoading); return }
    val a = w.anteile
    if (a?.aufwaerts == null && a?.abwaerts == null && w.omega == null && w.schmerz == null) { LeerZustand(dzText("ix.leer")); return }
    val signal = LocalSignal.current
    // Balken bis 200 %, Marke bei 100 % (gleich stark wie der Index)
    @Composable
    fun anteil(name: String, v: Double, farbe: androidx.compose.ui.graphics.Color, hinweis: String) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            LageBalkenZeile(name, Fmt.pct(v * 100, 0), (v / 2).coerceIn(0.0, 1.0), farbe, null, marke = 0.5)
            Text(hinweis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (a != null) a.aufwaerts?.let { anteil(dzText("ix.auf"), it, if (it >= 1) signal.up else MaterialTheme.colorScheme.onSurface, dzText("ix.monateAuf", a.monateAuf.toString(), massstab.name)) }
    if (a != null) a.abwaerts?.let { anteil(dzText("ix.ab"), it, if (it > 1) signal.down else signal.up, dzText("ix.monateAb", a.monateAb.toString(), massstab.name)) }
    w.omega?.let { KeyValueRow(dzText("ix.omega"), Fmt.num(it, 2), valueColor = if (it >= 1) signal.up else signal.down, hint = dzText("ix.omegaHinweis")) }
    w.schmerz?.let { KeyValueRow(dzText("ix.schmerz"), Fmt.num(it, 2), valueColor = if (it >= 0) signal.up else signal.down, hint = dzText("ix.schmerzHinweis")) }
}
