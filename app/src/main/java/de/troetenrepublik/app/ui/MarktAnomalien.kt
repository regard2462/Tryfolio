package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Anomalien
import de.troetenrepublik.app.engine.MarktSchlagen

// Baustein des Werkzeugs „Markt schlagen“: Renditemuster aus der Forschung in der Stichprobe ihrer Studie gegen die Zeit
// nach der Veroeffentlichung. Kernzahl ist der Rueckgang des Mittels, darunter das Streubild aller Muster (unter der
// gestrichelten Linie: danach schwaecher) und die Mittelwerte als Balken. Ein Finger auf dem Streubild zeigt das
// naechste Muster mit seinen Werten. Rechnung in engine/Anomalien.kt; ohne lesbare Datei entfaellt der Baustein.
@Composable
internal fun MarktAnomalien() {
    val datei = remember { Anomalien.datei } ?: return
    val a = remember(datei) { Anomalien.auswerten(datei.signale) } ?: return
    val signal = LocalSignal.current
    val bezug = Kategorie.blau
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var gewaehlt by remember(datei) { mutableStateOf<Anomalien.Signal?>(null) }
    MarktBaustein(
        MarktSchlagen.Baustein.ANOMALIEN, Fmt.pct(a.rueckgang, 0), signal.down,
        satzWerte = listOf(Fmt.num(a.anzahl.toDouble(), 0), Fmt.num(a.schwaecher.toDouble(), 0)),
        infoWerte = listOf(Fmt.num(MarktSchlagen.MUSTER_ANZAHL.wert, 0), Fmt.pct(MarktSchlagen.MUSTER_DANACH.wert, 0), Fmt.pct(MarktSchlagen.MUSTER_DAVOR.wert, 0)),
    ) {
        AnomalienStreubild(datei.signale, gewaehlt) { gewaehlt = it }
        Spacer(Modifier.height(8.dp))
        val g = gewaehlt
        Text(
            if (g == null) marktText("anomalien.alle") else marktText("anomalien.signal", g.name, g.autoren, g.jahr.toString()),
            style = MaterialTheme.typography.labelMedium, color = grau,
        )
        val studie = g?.studie ?: a.studie
        val zwischen = if (g != null) g.zwischen else a.zwischen
        val danach = g?.danach ?: a.danach
        val max = maxOf(studie, zwischen ?: 0.0, danach, 1e-9)
        LageBalkenZeile(marktText("anomalien.studie"), Fmt.pct(studie, 2), studie / max, bezug, null)
        zwischen?.let { LageBalkenZeile(marktText("anomalien.zwischen"), Fmt.pct(it, 2), it / max, MaterialTheme.colorScheme.onSurface, null) }
        LageBalkenZeile(marktText("anomalien.danach"), Fmt.pct(danach, 2), danach / max, if (danach < studie) signal.down else signal.up, null)
        val stil = MaterialTheme.typography.bodySmall
        Text(marktText("anomalien.grundlage", datei.bis.take(4)), style = stil, color = grau, modifier = Modifier.padding(top = 6.dp))
    }
    Trennlinie()
}

// Streubild: nach rechts die Monatsrendite in der Studie, nach oben die nach der Veroeffentlichung, beide auf derselben
// Skala. Auf der gestrichelten Linie waere die Rendite gleich geblieben. Die Achsennamen stehen ausserhalb der Flaeche.
@Composable
private fun AnomalienStreubild(signale: List<Anomalien.Signal>, gewaehlt: Anomalien.Signal?, onWahl: (Anomalien.Signal?) -> Unit) {
    val signal = LocalSignal.current
    val linie = MaterialTheme.colorScheme.outlineVariant
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val klein = MaterialTheme.typography.labelSmall.copy(color = schrift)
    val messer = rememberTextMeasurer()
    val (lo, hi) = remember(signale) { Anomalien.spanne(signale) }
    val achseX = marktText("anomalien.studie")
    val achseY = marktText("anomalien.danach")
    val gleich = marktText("anomalien.gleich")
    val randDp = 18.dp
    val rand = with(androidx.compose.ui.platform.LocalDensity.current) { randDp.toPx() }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1.5f).fingerLesen(signale) { p, groesse ->
            val hoehe = groesse.height - 2 * rand
            onWahl(
                if (p != null && hoehe > 0 && groesse.width > 0) {
                    val x = lo + (hi - lo) * (p.x / groesse.width)
                    val y = hi - (hi - lo) * ((p.y - rand) / hoehe)
                    Anomalien.naechstes(signale, x, y, groesse.width.toDouble() / hoehe)
                } else null,
            )
        },
    ) {
        val hoehe = size.height - 2 * rand
        fun px(v: Double) = size.width * ((v - lo) / (hi - lo)).toFloat()
        fun py(v: Double) = rand + hoehe * (1f - ((v - lo) / (hi - lo)).toFloat())
        val strich = 1.dp.toPx()
        drawLine(linie, Offset(px(0.0), rand), Offset(px(0.0), rand + hoehe), strokeWidth = strich)
        drawLine(linie, Offset(0f, py(0.0)), Offset(size.width, py(0.0)), strokeWidth = strich)
        drawLine(
            schrift.copy(alpha = 0.7f), Offset(px(lo), py(lo)), Offset(px(hi), py(hi)), strokeWidth = strich,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        val blass = if (gewaehlt == null) 0.7f else 0.25f
        signale.forEach { s ->
            drawCircle((if (s.danach < s.studie) signal.down else signal.up).copy(alpha = blass), 3.dp.toPx(), Offset(px(s.studie), py(s.danach)))
        }
        gewaehlt?.let { s -> drawCircle(if (s.danach < s.studie) signal.down else signal.up, 5.5.dp.toPx(), Offset(px(s.studie), py(s.danach))) }
        val tY = messer.measure(achseY, klein, softWrap = false, maxLines = 1)
        drawText(tY, topLeft = Offset(0f, 0f))
        val tG = messer.measure(gleich, klein, softWrap = false, maxLines = 1)
        drawText(tG, topLeft = Offset((size.width - tG.size.width).coerceAtLeast(0f), 0f))
        val tX = messer.measure(achseX, klein, softWrap = false, maxLines = 1)
        drawText(tX, topLeft = Offset((size.width - tX.size.width).coerceAtLeast(0f), size.height - tX.size.height))
    }
}
