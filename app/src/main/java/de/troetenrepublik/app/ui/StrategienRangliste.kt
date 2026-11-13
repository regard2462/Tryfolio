package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Strategien
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// Rangliste der Musterdepots (0.78) oben auf dem Reiter Strategien: eingeklappt eine Zeile, aufgeklappt je Muster
// der Name in eigener Zeile und darunter Rendite, Schwankung, groesster Rueckgang und Sharpe Ratio des gewaehlten
// Zeitraums. Ein Tipp auf eine Spalte sortiert, ein zweiter kehrt die Reihenfolge um. Die Kurse laden erst beim
// Aufklappen (Tagesschlusskurse in Euro wie auf der Seite eines Musterdepots).

// Wahl bleibt bis zum Programmende.
private object RanglisteZustand {
    var offen by mutableStateOf(false)
    var jahre by mutableIntStateOf(5)
    var spalte by mutableStateOf(Strategien.Spalte.RENDITE)
    var besteZuerst by mutableStateOf(true)
}

@Composable
internal fun StrategienRangliste(state: AppState, oeffnen: (String) -> Unit) {
    val z = RanglisteZustand
    AufklappZeile(btText("rang.titel"), btText("rang.unter", Strategien.alle.size.toString()), z.offen) { z.offen = !z.offen }
    if (!z.offen) return
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val de = I18n.lang == Lang.DE
    val reihen by produceState<Map<String, List<Candle>>?>(null) {
        val symbole = Strategien.alle.flatMap { s -> s.anteile.map { it.first } }.distinct()
        val aus = HashMap<String, List<Candle>>()
        for (gruppe in symbole.chunked(6)) {
            coroutineScope { gruppe.map { sym -> async { sym to state.dailyCloses(sym) } }.awaitAll() }.forEach { (sym, k) -> aus[sym] = k }
        }
        value = aus
    }
    val r = reihen
    if (r == null) { CopyLade(); return }
    val jetzt = remember { System.currentTimeMillis() }
    val zins = settings.interestPct / 100.0
    val listen by produceState<Map<Int, List<Strategien.Rang>>?>(null, r, zins) {
        value = withContext(Dispatchers.Default) { Strategien.RANG_JAHRE.associateWith { j -> Strategien.rangliste(r, jetzt, j, zins) } }
    }
    val alle = listen
    if (alle == null) { CopyLade(); return }
    // Nur Zeitraeume, die mindestens ein Muster deckt
    val zeiten = Strategien.RANG_JAHRE.filter { j -> alle[j].orEmpty().any { it.rendite != null } }
    if (zeiten.isEmpty()) { EmptyHint(S.copyNoHistory); return }
    val jahre = if (z.jahre in zeiten) z.jahre else zeiten.last()
    ToolZeit(S.widgetZeitraum, zeiten.map { S.toolYearsKurz(it) to it }, jahre) { z.jahre = it }
    val liste = remember(alle, jahre, z.spalte, z.besteZuerst) { Strategien.sortiert(alle[jahre].orEmpty(), z.spalte, z.besteZuerst) }

    // Spaltenkoepfe: die Sortierspalte kraeftig mit Pfeil
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Strategien.Spalte.entries.forEach { s ->
            val aktiv = s == z.spalte
            val name = btText("rang." + s.name)
            val beschreibung = btText("rang.sortiert", name)
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) {
                    if (aktiv) z.besteZuerst = !z.besteZuerst else { z.spalte = s; z.besteZuerst = true }
                }.then(if (aktiv) Modifier.semantics { contentDescription = beschreibung } else Modifier),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (aktiv) {
                    ChevronDown(width = 12.dp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.rotate(if (z.besteZuerst) 0f else 180f))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    name, style = MaterialTheme.typography.labelMedium, fontWeight = if (aktiv) FontWeight.SemiBold else null,
                    color = if (aktiv) MaterialTheme.colorScheme.onSurface else grau, maxLines = 1, softWrap = false,
                )
            }
        }
    }
    // Balken der Rendite auf gemeinsamer Skala, damit der Abstand der Muster auf einen Blick zu sehen ist
    val skala = liste.maxOfOrNull { abs(it.rendite ?: 0.0) }?.takeIf { it > 0.0 } ?: 1.0
    liste.forEachIndexed { i, rang ->
        val s = Strategien.finde(rang.id) ?: return@forEachIndexed
        if (i > 0) Trennlinie()
        Column(Modifier.fillMaxWidth().clickable { oeffnen(CopyRouten.strategie(s.id)) }.padding(horizontal = 20.dp, vertical = 9.dp)) {
            Text(if (de) s.nameDe else s.nameEn, style = MaterialTheme.typography.titleSmall)
            if (rang.rendite != null) RenditeBalken(abs(rang.rendite) / skala, signal.forChange(rang.rendite))
            if (rang.rendite == null) {
                Text(btText("rang.ohne"), style = MaterialTheme.typography.bodySmall, color = grau)
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val schrift = MaterialTheme.colorScheme.onSurface
                    RangWert(Fmt.signedPct(rang.rendite * 100, 1), signal.forChange(rang.rendite))
                    RangWert(rang.schwankung?.let { Fmt.pct(it * 100, 1) } ?: "–", schrift)
                    RangWert(rang.rueckgang?.let { Fmt.signedPct(it * 100, 1) } ?: "–", if ((rang.rueckgang ?: 0.0) < 0) signal.down else schrift)
                    RangWert(rang.sharpe?.let { Fmt.num(it, 2) } ?: "–", schrift)
                }
            }
        }
    }
    HinweisInfo(btText("rang.hinweis"), kurz = hinweisText("hist"))
    Trennlinie()
}

// Ein Wert der Rangliste: rechtsbuendig in seiner Spalte, einzeilig.
@Composable
private fun RowScope.RangWert(text: String, farbe: Color) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = farbe, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
    }
}

// Schmaler Balken unter dem Namen: Laenge nach Rendite, Farbe nach Vorzeichen.
@Composable
private fun RenditeBalken(anteil: Double, farbe: Color) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(spur, cornerRadius = r)
        val w = (size.width * anteil.coerceIn(0.0, 1.0)).toFloat()
        if (w > 0f) drawRoundRect(farbe, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}
