package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Monatsraster
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.RasterTexte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlin.math.abs

internal fun rasterText(key: String, vararg w: String): String = RasterTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Jahre, die das Raster zuerst zeigt; die aelteren stehen hinter dem Pfeil
private const val RASTER_ZEILEN = 8
private val JAHR_BREITE = 48.dp
private val MONAT_BREITE = 64.dp
private val RENDITE_BREITE = 72.dp
private val ZEILE_HOEHE = 32.dp

// Kernzeile des eingeklappten Abschnitts: Rendite des neuesten Jahres mit Jahresrendite
internal fun rasterKern(r: Monatsraster.Raster): Pair<Int, Double>? =
    r.zeilen.firstOrNull { it.rendite != null }?.let { it.jahr to it.rendite!! }

// Jahre als Zeilen (neuestes oben), zwoelf Monate als Spalten, Zelle nach Monatsrendite eingefaerbt, rechts die Jahresrendite.
// Werte bleiben einzeilig und in voller Schrift: Jahr links und Jahresrendite rechts stehen fest, die Monate in der Mitte
// laufen bei schmalem Bildschirm waagrecht.
@Composable
internal fun RasterTabelle(r: Monatsraster.Raster) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val scroll = rememberScrollState()
    var alle by rememberSaveable { mutableStateOf(false) }
    val zeilen = if (alle) r.zeilen else r.zeilen.take(RASTER_ZEILEN)
    val kopf = MaterialTheme.typography.labelSmall
    val wert = MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp)) {
        Column(Modifier.width(JAHR_BREITE)) {
            Box(Modifier.height(ZEILE_HOEHE))
            zeilen.forEach { z ->
                Box(Modifier.height(ZEILE_HOEHE), contentAlignment = Alignment.CenterStart) {
                    Text(z.jahr.toString(), style = wert, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }
        Column(Modifier.weight(1f).horizontalScrollMaus(scroll)) {
            Row {
                for (m in 1..12) {
                    Box(Modifier.width(MONAT_BREITE).height(ZEILE_HOEHE), contentAlignment = Alignment.Center) {
                        Text(S.vmMonat(m).take(3), style = kopf, color = grau, maxLines = 1, softWrap = false)
                    }
                }
            }
            zeilen.forEach { z ->
                Row {
                    z.monate.forEach { v ->
                        Box(Modifier.width(MONAT_BREITE).height(ZEILE_HOEHE).padding(1.dp)) {
                            val farbe = v?.let { signal.forChange(it) }
                            // Kraeftiger, je groesser die Rendite, voll ab 10 % im Monat
                            val flaeche = if (v == null || farbe == null) MaterialTheme.colorScheme.surfaceContainer
                            else farbe.copy(alpha = 0.10f + 0.40f * (abs(v) / 0.10).coerceAtMost(1.0).toFloat())
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(flaeche), contentAlignment = Alignment.Center) {
                                if (v != null && farbe != null) Text(Fmt.signedPct(v * 100.0, 1), style = wert, color = farbe, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.width(RENDITE_BREITE)) {
            Box(Modifier.height(ZEILE_HOEHE), contentAlignment = Alignment.CenterEnd) {
                Text(rasterText("jahr"), style = kopf, color = grau, maxLines = 1, softWrap = false)
            }
            zeilen.forEach { z ->
                Box(Modifier.fillMaxWidth().height(ZEILE_HOEHE), contentAlignment = Alignment.CenterEnd) {
                    val v = z.rendite
                    Text(
                        v?.let { Fmt.signedPct(it * 100.0, 1) } ?: "–", style = wert, fontWeight = FontWeight.Medium,
                        color = v?.let { signal.forChange(it) } ?: grau, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
    if (r.zeilen.size > RASTER_ZEILEN) ListenPfeil(alle) { alle = !alle }
}
