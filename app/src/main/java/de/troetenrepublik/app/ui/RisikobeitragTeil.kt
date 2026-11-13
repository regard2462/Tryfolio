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

// Je Symbol eine Zeile (Symbol, Name, Wert), die groessten zuerst.
private fun risikoGruppen(items: List<ValuedPosition>) =
    items.filter { !it.position.isDerivative && it.value > 0 }.groupBy { it.position.symbol }
        .map { (s, l) -> Triple(s, l.first().position.name, l.sumOf { it.value }) }.sortedByDescending { it.third }

private suspend fun risikoLaden(state: AppState, gruppen: List<Triple<String, String, Double>>): RbStand {
    val auswahl = gruppen.take(RB_MAX)
    val reihen = coroutineScope { auswahl.map { g -> async { state.dailyCloses(g.first) } }.awaitAll() }
    val since = System.currentTimeMillis() - 365L * 86_400_000L
    // Werte mit zu kurzer Kursreihe fallen weg, sonst bliebe kaum ein gemeinsamer Tag
    val mit = auswahl.indices.filter { i -> reihen[i].count { it.t >= since } >= 120 }
    val e = if (mit.size < 2) null else withContext(Dispatchers.Default) {
        Risikobeitrag.berechnen(mit.map { auswahl[it].third }, mit.map { reihen[it] }, since)
    }
    return RbStand(mit.map { auswahl[it].first to auswahl[it].second }, e, gruppen.size - mit.size)
}

// Gewicht je Positionsschluessel aus dem Paritaetsvorschlag p: je Symbol der Anteil am eingerechneten Teil, nicht
// eingerechnete Positionen behalten ihr heutiges Gewicht. Null ohne Depotwert.
private fun risikoVorschlagVon(items: List<ValuedPosition>, gruppen: List<Triple<String, String, Double>>, werte: List<Pair<String, String>>, p: DoubleArray): Map<String, Double>? {
    val gesamt = items.filter { !it.position.isDerivative && it.value > 0 }.sumOf { it.value }
    if (gesamt <= 0) return null
    val eingerechnet = werte.map { it.first }.toSet()
    val teil = gruppen.filter { it.first in eingerechnet }.sumOf { it.third } / gesamt
    val vorschlag = HashMap<String, Double>()
    items.filter { !it.position.isDerivative && it.value > 0 }.forEach { vp ->
        val i = werte.indexOfFirst { it.first == vp.position.symbol }
        vorschlag[vp.key] = if (i < 0) vp.value / gesamt else {
            val symbolWert = gruppen.first { it.first == vp.position.symbol }.third
            p[i] * teil * vp.value / symbolWert
        }
    }
    return vorschlag
}

// Vorschlag mit gleichem Risikoanteil fuer die Auswahl beim Umschichten; null, wenn die Kurse nicht reichen.
internal suspend fun risikoGleichgewicht(state: AppState, items: List<ValuedPosition>): Map<String, Double>? {
    val gruppen = risikoGruppen(items)
    if (gruppen.size < 2) return null
    val st = risikoLaden(state, gruppen)
    val p = st.ergebnis?.paritaet ?: return null
    return risikoVorschlagVon(items, gruppen, st.werte, p)
}

// Risikobeitrag je Position in der Streuung: Gewicht gegen Anteil an der Schwankung als Balken, ein Satz zur
// auffaelligsten Position und der Vorschlag mit gleichem Risikoanteil, der sich ins Umschichten uebernehmen laesst.
@Composable
internal fun RisikobeitragTeil(state: AppState, items: List<ValuedPosition>, onUmschichten: () -> Unit) {
    val signal = LocalSignal.current
    // Je Symbol eine Zeile (mehrere Positionen desselben Werts zusammen), die groessten zuerst
    val gruppen = risikoGruppen(items)
    if (gruppen.size < 2) return
    SectionHeader(S.rskTitel)
    var stand by remember { mutableStateOf<RbStand?>(null) }
    LaunchedEffect(gruppen.joinToString("|") { it.first }) {
        stand = null
        stand = risikoLaden(state, gruppen)
    }
    val st = stand ?: run { ToolNote(S.rskLaden); return }
    val e = st.ergebnis ?: run { ToolNote(S.rskOhneDaten); return }
    Spacer(Modifier.height(4.dp))
    KeyValueRow(S.rskDepotSchwankung(Fmt.pct(e.schwankung * 100, 1)), "")
    val satz = Risikobeitrag.auffaellig(e)?.let { i ->
        S.rskSatz(st.werte[i].second, Fmt.pct(e.gewichte[i] * 100, 0), Fmt.pct(e.risiko[i] * 100, 0))
    } ?: S.rskAusgeglichen
    Text(satz, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    val farbeGewicht = AnteilsFarben.folge(0)
    val farbeRisiko = AnteilsFarben.folge(2)
    ChartLegend(listOf(S.rskGewicht to farbeGewicht, S.rskRisiko to farbeRisiko))
    // Nach Risikoanteil sortiert; ein negativer Anteil (absichernde Position) zeigt einen leeren Balken
    e.risiko.indices.sortedByDescending { e.risiko[it] }.forEach { i ->
        GewichtRisikoZeile(st.werte[i].second, e.gewichte[i], e.risiko[i], farbeGewicht, farbeRisiko)
    }
    if (st.fehlend > 0) ToolNote(S.rskNichtEingerechnet(st.fehlend))
    HinweisInfo(S.rskHinweis)

    // Vorschlag mit gleichem Risikoanteil
    val p = e.paritaet ?: return
    var offen by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.rskParitaetTitel, style = MaterialTheme.typography.titleSmall, color = signal.link, modifier = Modifier.weight(1f))
        AufklappPfeil(offen, color = signal.link)
    }
    if (!offen) return
    ToolNote(S.rskParitaetText)
    p.indices.sortedByDescending { p[it] }.forEach { i ->
        KeyValueRow(st.werte[i].second, de.troetenrepublik.app.i18n.RegelTexte.text(de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE, "auf", Fmt.pct(e.gewichte[i] * 100, 1), Fmt.pct(p[i] * 100, 1)))
    }
    HinweisInfo(S.rskWarnung, kurz = hinweisText("annahmen"))
    ActionLineWide(S.rskUebernehmen) {
        val vorschlag = risikoVorschlagVon(items, gruppen, st.werte, p) ?: return@ActionLineWide
        risikoVorschlag = vorschlag
        onUmschichten()
    }
}

// Name, darunter zwei duenne Balken uebereinander: Gewicht und Anteil an der Schwankung, je mit dem Wert rechts in fester Spalte.
@Composable
private fun GewichtRisikoZeile(name: String, gewicht: Double, risiko: Double, farbeGewicht: Color, farbeRisiko: Color) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp)) {
        NamenText(name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
        listOf(gewicht to farbeGewicht, risiko to farbeRisiko).forEach { (anteil, farbe) ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.weight(1f).height(5.dp)) {
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(spur, cornerRadius = r)
                    val w = (size.width * anteil.coerceIn(0.0, 1.0)).toFloat()
                    if (w > 0f) drawRoundRect(farbe, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
                }
                Text(
                    Fmt.pct(anteil * 100, 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp), maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}
