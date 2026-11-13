package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.IndexBeitrag
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Unter der Heatmap (Paket q78f): die groessten positiven und negativen Beitraege zur Bewegung, Gewicht mal
// Aenderung in Prozentpunkten. Links die Treiber nach oben, rechts die nach unten, je mit Balken.
@Composable
internal fun IndexBeitragKarte(state: AppState, quelle: String, werte: List<AppState.HeatmapWert>, onOpen: (String) -> Unit) {
    val e = remember(werte) { IndexBeitrag.berechnen(werte.map { IndexBeitrag.Eingabe(it.symbol, it.name, it.gewicht, it.aenderung) }) } ?: return
    if (e.hoch.isEmpty() && e.runter.isEmpty()) return
    val groesst = (e.hoch + e.runter).maxOf { abs(it.punkte) }.takeIf { it > 0 } ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.indexBeweger(quelle), Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.indexBeitragTitel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
                MetricValue(e.gesamt, Fmt.signedPct(e.gesamt, 2), style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(Modifier.weight(1f)) { e.hoch.forEach { BeitragZeile(it, groesst, onOpen) } }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { e.runter.forEach { BeitragZeile(it, groesst, onOpen) } }
        }
    }
}

@Composable
private fun BeitragZeile(b: IndexBeitrag.Beitrag, groesst: Double, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    val farbe = if (b.punkte >= 0) signal.up else signal.down
    // Zahlenkuerzel (000660, 7203) ersetzt der Name; der steht ueber dem Balken, damit Balken und Wert nicht verdraengt werden
    val zahl = HeatmapBeschriftung.nurZiffern(b.symbol)
    val bezeichnung = HeatmapBeschriftung.bezeichnung(b.symbol, b.name).orEmpty()
    Column(Modifier.fillMaxWidth().zeilenKlick { onOpen(b.symbol) }.padding(vertical = 4.dp)) {
        // Einzeilig: der volle Name, passt er nicht, sein erstes Wort (wie in der Kachel)
        if (zahl && bezeichnung.isNotEmpty()) PassenderText(HeatmapBeschriftung.kandidaten(b.symbol, b.name), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!zahl) {
                Text(bezeichnung, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.weight(1f).height(6.dp)) {
                val anteil = (abs(b.punkte) / groesst).toFloat().coerceIn(0.04f, 1f)
                Box(Modifier.fillMaxWidth(anteil).height(6.dp).clip(RoundedCornerShape(3.dp)).background(farbe))
            }
            Spacer(Modifier.width(6.dp))
            Text(Fmt.signedPunkte(b.punkte, 2), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, color = farbe)
        }
    }
}
