package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Ton
import de.troetenrepublik.app.i18n.TonTexte
import de.troetenrepublik.app.market.FirmaName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max
import kotlin.math.min

// [Lange Lage] Aktienseite, bei den Nachrichten: Ton der Berichte ueber die Firma (GDELT, englischsprachige Medien der letzten
// drei Monate). Zugeklappt die Kernaussage (Richtung gegenueber den Monaten davor, mit Dreieck), aufgeklappt der Verlauf der
// Wochenmittel mit Nulllinie. Der Abruf laeuft im Bereich der Seite, ist sechs Stunden gespeichert und haelt den Abstand
// von fuenf Sekunden ein, den GDELT verlangt. Ohne Daten zeichnet der Baustein nichts.
private fun tonText(key: String, vararg w: String): String = TonTexte.t(key, *w)

@Composable
internal fun TonKarte(state: AppState, symbol: String, name: String, langerName: String?, merker: SeitenMerker?) {
    var wert by gemerkt<Ton.Wert?>(merker, "ton/$symbol") { null }
    var geladen by gemerkt(merker, "ton/$symbol/geladen") { false }
    val suche = remember(symbol, name, langerName) { Ton.suche(FirmaName.namenDerSeite(symbol, name, langerName)) }
    LaunchedEffect(symbol, suche) {
        if (suche == null || geladen) return@LaunchedEffect
        val lade: suspend CoroutineScope.() -> Unit = {
            wert = runCatching { state.firmaTon(symbol, suche) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            geladen = wert != null
        }
        if (merker != null) merker.abrufBisFertig("ton/$symbol", { geladen }, lade) else lade()
    }
    val w = wert ?: return
    val erg = remember(w) { Ton.bewerten(Ton.tage(w)) } ?: return
    val offen = firmaOffen(merker, "ton")
    val richtung = when (erg.richtung) {
        Ton.Richtung.DEUTLICH_NEGATIVER, Ton.Richtung.NEGATIVER -> -1.0
        Ton.Richtung.UNVERAENDERT -> 0.0
        else -> 1.0
    }
    val kern = KernZeile(
        listOf(KernTeil(tonText("kern." + erg.richtung.name), richtung)),
        KernTeil(tonText("unten", Fmt.num(erg.neu, 1), Fmt.num(erg.davor, 1))),
    )
    QuellenMelden(QuelleLink(tonText("quelle"), "https://www.gdeltproject.org/"))
    FirmaAbschnitt(
        tonText("titel"), kern,
        tonText("info", Fmt.num(Ton.SCHWELLE, 1), Fmt.num(Ton.DEUTLICH, 1), Fmt.date(w.abgerufen)), offen,
    ) {
        val wochen = remember(w) { Ton.wochen(Ton.tage(w)) }
        if (wochen.size >= 3) {
            Zwischenkopf(tonText("verlauf"), oben = 4.dp)
            TonLinie(wochen, erg.bis - Ton.NEU_TAGE, tonText("titel"))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val tage = Ton.tage(w)
                Text(tagKurz(tage.minOf { it.t }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                Text(tagKurz(erg.bis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun tagKurz(tag: Int): String = Fmt.date(tag * 86_400_000L + 43_200_000L)

// Wochenmittel des Tons als Linie mit Nulllinie; hinterlegt sind die Tage nach `neuAb` (die verglichenen letzten 30 Tage).
// Links stehen null und die Grenzen der Achse, rechts neben dem Ende der Linie der letzte Wert.
@Composable
private fun TonLinie(wochen: List<Pair<Int, Double>>, neuAb: Int, beschreibung: String) {
    val messer = rememberTextMeasurer()
    val farbe = MaterialTheme.colorScheme.primary
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val raster = MaterialTheme.colorScheme.outline
    val klein = TextStyle(fontSize = 10.sp, color = grau)
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(120.dp).semantics { contentDescription = beschreibung }) {
        val d = density
        val ys = wochen.map { it.second }
        val hi = max(ys.max(), 0.0) + 0.3
        val lo = min(ys.min(), 0.0) - 0.3
        val ende = messer.measure(Fmt.num(ys.last(), 1), klein.copy(color = farbe), maxLines = 1, softWrap = false)
        val links = 26f * d
        val rechts = ende.size.width + 6f * d
        val oben = 6f * d
        val unten = 4f * d
        val b = size.width - links - rechts
        val h = size.height - oben - unten
        if (b <= 0f || h <= 0f) return@Canvas
        val t0 = wochen.first().first.toDouble()
        val t1 = wochen.last().first.toDouble()
        fun px(t: Int) = links + b * (if (t1 > t0) ((t - t0) / (t1 - t0)).toFloat() else 0f)
        fun py(v: Double) = oben + h * (1f - ((v - lo) / (hi - lo)).toFloat())
        // Die verglichenen letzten 30 Tage leicht hinterlegt
        val x0 = px(neuAb).coerceIn(links, links + b)
        drawRect(farbe.copy(alpha = 0.08f), Offset(x0, oben), androidx.compose.ui.geometry.Size(links + b - x0, h))
        // Achse: Grenzen und Nulllinie
        for (v in listOf(hi, lo)) {
            val z = messer.measure(Fmt.num(v, 1), klein, maxLines = 1, softWrap = false)
            drawText(z, topLeft = Offset(links - 4f * d - z.size.width, py(v) - z.size.height / 2f))
        }
        drawLine(raster, Offset(links, py(0.0)), Offset(links + b, py(0.0)), strokeWidth = 1.4f * d)
        val nullText = messer.measure("0", klein, maxLines = 1, softWrap = false)
        drawText(nullText, topLeft = Offset(links - 4f * d - nullText.size.width, py(0.0) - nullText.size.height / 2f))
        val pfad = Path()
        wochen.forEachIndexed { i, (t, v) -> if (i == 0) pfad.moveTo(px(t), py(v)) else pfad.lineTo(px(t), py(v)) }
        drawPath(pfad, farbe, style = Stroke(width = 2f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val (tl, vl) = wochen.last()
        drawCircle(farbe, 3.5f * d, Offset(px(tl), py(vl)))
        drawText(ende, topLeft = Offset(px(tl) + 6f * d, py(vl) - ende.size.height / 2f))
    }
}
