package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.Risikobeitrag
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// Hoechstens so viele Werte im Risikobeitrag (je einer ein Kursabruf).
private const val RB_MAX = 20

// Vorschlag mit gleichem Risikoanteil fuer das Umschichten: Gewicht je Positionsschluessel (Anteil). Das Umschichten
// uebernimmt ihn einmal und setzt ihn dann zurueck.
internal var risikoVorschlag by mutableStateOf<Map<String, Double>?>(null)

// Ergebnis mit den eingerechneten Werten (Symbol, Name) und der Zahl der weggelassenen Positionen.
private class RbStand(val werte: List<Pair<String, String>>, val ergebnis: Risikobeitrag.Ergebnis?, val fehlend: Int)

// Risikobeitrag je Position in der Streuung: Gewicht gegen Anteil an der Schwankung als Balken, ein Satz zur
// auffaelligsten Position und der Vorschlag mit gleichem Risikoanteil, der sich ins Umschichten uebernehmen laesst.
@Composable
internal fun RisikobeitragTeil(state: AppState, items: List<ValuedPosition>, onUmschichten: () -> Unit) {
    val signal = LocalSignal.current
    // Je Symbol eine Zeile (mehrere Positionen desselben Werts zusammen), die groessten zuerst
    val gruppen = items.filter { !it.position.isDerivative && it.value > 0 }.groupBy { it.position.symbol }
        .map { (s, l) -> Triple(s, l.first().position.name, l.sumOf { it.value }) }.sortedByDescending { it.third }
    if (gruppen.size < 2) return
    SectionHeader(S.rskTitel)
    var stand by remember { mutableStateOf<RbStand?>(null) }
    LaunchedEffect(gruppen.joinToString("|") { it.first }) {
        stand = null
        val auswahl = gruppen.take(RB_MAX)
        val reihen = coroutineScope { auswahl.map { g -> async { state.dailyCloses(g.first) } }.awaitAll() }
        val since = System.currentTimeMillis() - 365L * 86_400_000L
        // Werte mit zu kurzer Kursreihe fallen weg, sonst bliebe kaum ein gemeinsamer Tag
        val mit = auswahl.indices.filter { i -> reihen[i].count { it.t >= since } >= 120 }
        val e = if (mit.size < 2) null else withContext(Dispatchers.Default) {
            Risikobeitrag.berechnen(mit.map { auswahl[it].third }, mit.map { reihen[it] }, since)
        }
        stand = RbStand(mit.map { auswahl[it].first to auswahl[it].second }, e, gruppen.size - mit.size)
    }
    val st = stand ?: run { ToolNote(S.rskLaden); return }
    val e = st.ergebnis ?: run { ToolNote(S.rskOhneDaten); return }
    Spacer(Modifier.height(4.dp))
    KeyValueRow(S.rskDepotSchwankung(Fmt.pct(e.schwankung * 100, 1)), "")
    val satz = Risikobeitrag.auffaellig(e)?.let { i ->
        S.rskSatz(st.werte[i].second, Fmt.pct(e.gewichte[i] * 100, 0), Fmt.pct(e.risiko[i] * 100, 0))
    } ?: S.rskAusgeglichen
    Text(satz, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    ChartLegend(listOf(S.rskGewicht to grau.copy(alpha = 0.5f), S.rskRisiko to MaterialTheme.colorScheme.onSurface))
    // Nach Risikoanteil sortiert; ein negativer Anteil (absichernde Position) zeigt einen leeren Balken
    e.risiko.indices.sortedByDescending { e.risiko[it] }.forEach { i ->
        GewichtRisikoZeile(st.werte[i].second, e.gewichte[i], e.risiko[i], grau.copy(alpha = 0.5f), MaterialTheme.colorScheme.onSurface)
    }
    if (st.fehlend > 0) ToolNote(S.rskNichtEingerechnet(st.fehlend))
    ToolNote(S.rskHinweis)

    // Vorschlag mit gleichem Risikoanteil
    val p = e.paritaet ?: return
    var offen by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.rskParitaetTitel, style = MaterialTheme.typography.titleSmall, color = signal.link, modifier = Modifier.weight(1f))
        ChevronRight(height = 12.dp, color = signal.link)
    }
    if (!offen) return
    ToolNote(S.rskParitaetText)
    p.indices.sortedByDescending { p[it] }.forEach { i ->
        KeyValueRow(st.werte[i].second, "${Fmt.pct(e.gewichte[i] * 100, 1)} → ${Fmt.pct(p[i] * 100, 1)}")
    }
    ToolNote(S.rskWarnung)
    ActionLineWide(S.rskUebernehmen) {
        // Je Symbol der Anteil am eingerechneten Teil; nicht eingerechnete Positionen behalten ihr heutiges Gewicht
        val gesamt = items.filter { !it.position.isDerivative && it.value > 0 }.sumOf { it.value }
        if (gesamt <= 0) return@ActionLineWide
        val eingerechnet = st.werte.map { it.first }.toSet()
        val teil = gruppen.filter { it.first in eingerechnet }.sumOf { it.third } / gesamt
        val vorschlag = HashMap<String, Double>()
        items.filter { !it.position.isDerivative && it.value > 0 }.forEach { vp ->
            val i = st.werte.indexOfFirst { it.first == vp.position.symbol }
            vorschlag[vp.key] = if (i < 0) vp.value / gesamt else {
                val symbolWert = gruppen.first { it.first == vp.position.symbol }.third
                p[i] * teil * vp.value / symbolWert
            }
        }
        risikoVorschlag = vorschlag
        onUmschichten()
    }
}

// Zwei duenne Balken uebereinander: Gewicht (grau) und Anteil an der Schwankung (farbig), Werte rechts.
@Composable
private fun GewichtRisikoZeile(name: String, gewicht: Double, risiko: Double, farbeGewicht: Color, farbeRisiko: Color) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NamenText(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(10.dp))
            Text(S.rskZeile(Fmt.pct(gewicht * 100, 0), Fmt.pct(risiko * 100, 0)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        listOf(gewicht to farbeGewicht, risiko to farbeRisiko).forEach { (anteil, farbe) ->
            Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp)) {
                val r = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(spur, cornerRadius = r)
                val w = (size.width * anteil.coerceIn(0.0, 1.0)).toFloat()
                if (w > 0f) drawRoundRect(farbe, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
            }
        }
    }
}
