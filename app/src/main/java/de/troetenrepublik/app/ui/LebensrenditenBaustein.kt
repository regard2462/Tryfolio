package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.Lebensrenditen

// Baustein in „Markt schlagen“ (0.78): Lebensrenditen der US-Aktien seit 1926 als Verteilung. Kernzahl ist der Anteil der
// Aktien mit Verlust; darunter die Klassen als Saeulen und drei Zeilen zum geschaffenen Vermoegen je Unternehmen.

// Mitgelieferte Auszaehlung, einmal gelesen; null, wenn die Datei fehlt oder unlesbar ist (dann entfaellt der Baustein)
internal val lebensrenditenDatei: Lebensrenditen.Datei? by lazy {
    runCatching { Lebensrenditen.lesen(de.troetenrepublik.app.market.Daten.text(Lebensrenditen.DATEI)) }.getOrNull()
}

@Composable
internal fun LebensrenditenBaustein(d: Lebensrenditen.Datei) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val anteile = remember(d) { Lebensrenditen.anteile(d) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        MarktTitel(marktText("leben.titel"), marktText("leben.info"))
        Text(Fmt.pct(Lebensrenditen.anteilNegativ(d), 1), style = MaterialTheme.typography.headlineSmall, color = signal.down, maxLines = 1, softWrap = false)
        Text(
            marktText("leben.satz", d.von.toString(), d.bis.toString(), Fmt.num(d.aktien.toDouble(), 0)),
            style = MaterialTheme.typography.bodySmall, color = grau,
        )
        Spacer(Modifier.height(10.dp))
        LebensSaeulen(d, anteile, signal.down, signal.up, marktText("leben.grafik"))
        Text(marktText("leben.grafik"), style = MaterialTheme.typography.labelSmall, color = grau)
        Spacer(Modifier.height(6.dp))
        LageBalkenZeile(marktText("leben.median"), Fmt.signedPct(d.median, 1), 0.0, if (d.median < 0) signal.down else signal.up, null, balken = {})
        val unter = Lebensrenditen.anteilUnterSchatz(d)
        LageBalkenZeile(marktText("leben.schatz"), Fmt.pct(unter, 1), unter / 100, signal.down, null, marke = 0.5)
        val alles = Lebensrenditen.anteilAlles(d)
        LageBalkenZeile(marktText("leben.alles"), Fmt.pct(alles, 1), alles / 100, signal.up, null)
        val haelfte = Lebensrenditen.anteilHaelfte(d)
        LageBalkenZeile(marktText("leben.haelfte", Fmt.num(d.haelfte.toDouble(), 0)), Fmt.pct(haelfte, 2), haelfte / 100, signal.up, null)
    }
}

// Grenze einer Klasse als Beschriftung der Achse: "−75", "0", "+500"
private fun grenzText(prozent: Int): String = when {
    prozent < 0 -> "−" + Fmt.num(-prozent.toDouble(), 0)
    prozent > 0 -> "+" + Fmt.num(prozent.toDouble(), 0)
    else -> "0"
}

// Saeulen je Klasse, Verlust und Gewinn in den Signalfarben. Ueber jeder Saeule ihr Anteil, unter den Kanten die Grenzen
// der Klassen in Prozent (die erste Kante ist der Totalverlust, die letzte Klasse ist nach oben offen).
@Composable
private fun LebensSaeulen(d: Lebensrenditen.Datei, anteile: List<Double>, verlust: Color, gewinn: Color, beschreibung: String) {
    val messer = rememberTextMeasurer()
    val klein = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val max = anteile.maxOrNull()?.takeIf { it > 0 } ?: return
    val oben = remember(anteile) { anteile.map { Fmt.pct(it, 0) } }
    val kanten = remember(d) { d.klassen.map { grenzText(it.von) } }
    Canvas(Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = beschreibung }) {
        val n = anteile.size
        val kopf = 16.dp.toPx()
        val fuss = 16.dp.toPx()
        val luecke = 3.dp.toPx()
        val breite = size.width / n
        val hoehe = size.height - kopf - fuss
        val ecke = CornerRadius(2.dp.toPx())
        for (i in 0 until n) {
            val h = (hoehe * (anteile[i] / max)).toFloat().coerceAtLeast(1.dp.toPx())
            val x = i * breite
            val farbe = if ((d.klassen[i].bis ?: 1) <= 0) verlust else gewinn
            drawRoundRect(farbe, Offset(x + luecke / 2, kopf + hoehe - h), Size(breite - luecke, h), ecke)
            val wert = messer.measure(oben[i], klein.copy(color = farbe), maxLines = 1, softWrap = false)
            drawText(wert, topLeft = Offset(x + (breite - wert.size.width) / 2f, kopf + hoehe - h - wert.size.height - 1.dp.toPx()))
            val kante = messer.measure(kanten[i], klein, maxLines = 1, softWrap = false)
            // Die erste Grenze steht linksbuendig, alle weiteren mittig unter ihrer Kante
            val kx = if (i == 0) 0f else x - kante.size.width / 2f
            drawText(kante, topLeft = Offset(kx, size.height - fuss + 2.dp.toPx()))
        }
    }
}
