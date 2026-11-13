package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.market.Verguetung
import de.troetenrepublik.app.market.VerguetungAbruf

// Auf der Seite einer US-Aktie (0.77), bei den Insidern: Verguetung des Vorstandschefs laut Proxy-Meldung. Zuerst die
// Zahl des letzten Jahres und was tatsaechlich zugeflossen ist, darunter der Durchschnitt der uebrigen Vorstaende und
// der Verlauf der letzten Jahre mit der Rendite der Aktie im selben Jahr. Geholt wird nur mit Kontaktadresse unter
// Zugaenge, einmal je Seite und nur bei Daten (hoechstens vier kleine Abfragen).
@Composable
internal fun VerguetungKarte(state: AppState, symbol: String, merker: SeitenMerker? = null) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var ergebnis by gemerkt<Verguetung.Ergebnis?>(merker, "vg/$symbol") { null }
    var geladen by gemerkt(merker, "vg/$symbol/geladen") { false }
    LaunchedEffect(symbol, settings.secKontakt) {
        if (geladen) return@LaunchedEffect
        val kennung = SecInsider.kennung(settings.secKontakt) ?: return@LaunchedEffect
        val cik = runCatching { state.ipoCik(symbol) }.getOrNull() ?: return@LaunchedEffect
        val e = runCatching { VerguetungAbruf.laden(cik, kennung) }.getOrNull()
        if (e != null) ergebnis = e
        geladen = true
    }
    val modell = remember(ergebnis) { ergebnis?.let { verguetungModell(it) } }
    ModellAusgabe(merker, "vg", modell)
}

internal fun LazyListScope.verguetungAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "vg")
}

private fun dollar(v: Double) = Fmt.mitSymbol(Fmt.compact(v), "$")

// Kurzform fuer die Beschriftung der Saeulen: 74,6 Mio / 1,2 Mrd
private fun kurz(v: Double): String = when {
    v >= 1e9 -> S.vgMrd(Fmt.num(v / 1e9, 1))
    v >= 1e6 -> S.vgMio(Fmt.num(v / 1e6, 1))
    else -> S.vgTsd(Fmt.num(v / 1e3, 0))
}

internal fun verguetungModell(e: Verguetung.Ergebnis): ListenModell {
    val j = e.aktuell
    val zeilen = ArrayList<ZeilenDaten>()
    j.ceo?.let { c -> zeilen.add(ZeilenDaten("ceo") { KeyValueRow(S.vgCeo(j.jahr), dollar(c)) }) }
    j.zugeflossen?.let { z -> zeilen.add(ZeilenDaten("zugeflossen") { KeyValueRow(S.vgZugeflossen, dollar(z)) }) }
    j.uebrige?.let { u -> zeilen.add(ZeilenDaten("uebrige") { KeyValueRow(S.vgUebrige(j.jahr), dollar(u)) }) }
    val verlauf = e.jahre.filter { it.ceo != null }
    if (verlauf.size >= 2) zeilen.add(ZeilenDaten("verlauf") { VerguetungDiagramm(verlauf) })
    return ListenModell(titel = S.vgTitel, gross = true, kern = null, zeilen = zeilen, immerOffen = true, info = S.vgInfo)
}

// Saeulen: Verguetung je Geschaeftsjahr, Wert am Kopf der Saeule, darunter das Jahr und die Rendite der Aktie in diesem
// Jahr mit Richtungsdreieck.
@Composable
private fun VerguetungDiagramm(jahre: List<Verguetung.Jahr>) {
    val hoechste = remember(jahre) { jahre.maxOf { it.ceo ?: 0.0 }.coerceAtLeast(1.0) }
    val hatRendite = remember(jahre) { jahre.any { it.rendite != null } }
    val farbe = MaterialTheme.colorScheme.primary
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        // Passen alle Beschriftungen nebeneinander (gemessen), stehen sie an jeder Saeule, sonst nur an der ersten und letzten
        val messer = rememberTextMeasurer()
        val dichte = LocalDensity.current
        val klein = MaterialTheme.typography.labelSmall
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spalte = with(dichte) { ((maxWidth - 6.dp * (jahre.size - 1)) / jahre.size).toPx() }
        val alleBeschriftet = jahre.all { messer.measure(kurz(it.ceo ?: 0.0), klein).size.width <= spalte }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            jahre.forEachIndexed { i, j ->
                val anteil = ((j.ceo ?: 0.0) / hoechste).toFloat().coerceIn(0.02f, 1f)
                Column(Modifier.weight(1f).height(110.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (alleBeschriftet || i == 0 || i == jahre.size - 1) {
                        Text(kurz(j.ceo ?: 0.0), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
                    }
                    Spacer(Modifier.height(3.dp))
                    Canvas(Modifier.fillMaxWidth().height(72.dp * anteil)) {
                        drawRoundRect(farbe, topLeft = Offset(size.width * 0.18f, 0f), size = Size(size.width * 0.64f, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            jahre.forEach { j ->
                Text(j.jahr.toString(), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (hatRendite) {
            Text(S.vgRendite, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                jahre.forEach { j ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        val r = j.rendite
                        if (r == null) Text("–", style = MaterialTheme.typography.labelSmall, color = grau)
                        else ChangeValue(r, Fmt.signedPct(r, 0), style = MaterialTheme.typography.labelSmall, triangleWidth = 7.dp)
                    }
                }
            }
        }
    }
}
