package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Wertpapierbilanz
import de.troetenrepublik.app.i18n.S

// Eine Zahl mit Beschriftung darueber; der Wert bleibt in einer Zeile.
private class WbZelle(val label: String, val wert: String, val farbe: Color? = null)

// Gewinne und Rendite je Wertpapier im gewaehlten Zeitraum, nach Summe sortiert, am Ende die Summe wie in der Kapitalbilanz.
// Je Wert steht der Name oben mit der Summe, darunter die Zahlen in Spalten (bei breitem Platz vier, sonst drei).
@Composable
internal fun WertpapierBilanzAbschnitt(e: Wertpapierbilanz.Ergebnis, onOpen: (String) -> Unit) {
    if (e.zeilen.isEmpty() && e.sonstiges.leer) return
    val signal = LocalSignal.current
    fun vz(v: Double) = signal.forChange(if (kotlin.math.abs(v) < 0.005) 0.0 else v)
    MarktInfoKopf(S.wbTitel, S.wbInfo)
    e.zeilen.forEach { z ->
        val zellen = listOf(
            WbZelle(S.calRealized, Fmt.signedEuro(z.realisiert), vz(z.realisiert)),
            WbZelle(S.wbUnrealisiert, Fmt.signedEuro(z.unrealisiert), vz(z.unrealisiert)),
            WbZelle(S.widgetDividenden, Fmt.euro(z.dividenden)),
            WbZelle(S.calFees, Fmt.signedEuro(-z.gebuehren)),
            WbZelle(S.taxes, Fmt.signedEuro(-z.steuern)),
        )
        // Renditen stehen unter dem Namen, damit die Euro-Zahlen ein volles Raster aus zwei Reihen bilden
        val rendite = listOf(
            (S.wbTtwror + " " + (z.ttwror?.let { Fmt.signedPct(it * 100) } ?: "–")) to z.ttwror?.let { vz(it) },
            (S.wbIzf + " " + (z.izf?.let { Fmt.signedPct(it * 100) } ?: "–")) to z.izf?.let { vz(it) },
        )
        WbZeile(
            name = z.name, zusatz = if (z.imDepot) null else S.copySold, rendite = rendite, summe = z.summe, zellen = zellen,
            onClick = z.symbol.takeIf { it.isNotBlank() }?.let { s -> { onOpen(s) } },
        )
    }
    if (!e.sonstiges.leer) {
        val s = e.sonstiges
        WbZeile(
            name = S.wbOhneWertpapier, zusatz = null, summe = s.summe,
            zellen = listOf(
                WbZelle(S.interest, Fmt.euro(s.zinsen)),
                WbZelle(S.calFees, Fmt.signedEuro(-s.gebuehren)),
                WbZelle(S.taxes, Fmt.signedEuro(-s.steuern)),
            ),
            onClick = null,
        )
    }
    WbZeile(
        name = S.total, zusatz = null, summe = e.summe, kopf = true,
        zellen = listOf(
            WbZelle(S.calRealized, Fmt.signedEuro(e.realisiert), vz(e.realisiert)),
            WbZelle(S.wbUnrealisiert, Fmt.signedEuro(e.unrealisiert), vz(e.unrealisiert)),
            WbZelle(S.widgetDividenden, Fmt.euro(e.dividenden)),
            WbZelle(S.interest, Fmt.euro(e.sonstiges.zinsen)),
            WbZelle(S.calFees, Fmt.signedEuro(-e.gebuehren)),
            WbZelle(S.taxes, Fmt.signedEuro(-e.steuern)),
        ),
        onClick = null,
    )
}

@Composable
private fun WbZeile(name: String, zusatz: String?, summe: Double, rendite: List<Pair<String, Color?>>? = null, zellen: List<WbZelle>, onClick: (() -> Unit)?, kopf: Boolean = false) {
    val signal = LocalSignal.current
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                if (kopf) Text(name, style = MaterialTheme.typography.titleSmall)
                else NamenText(name, style = MaterialTheme.typography.bodyMedium)
                zusatz?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = sv) }
                // Zwei Renditen nebeneinander, jede in ihrer Farbe
                rendite?.let { liste ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        liste.forEach { (t, f) -> Text(t, style = MaterialTheme.typography.bodySmall, color = f ?: sv, maxLines = 1, softWrap = false) }
                    }
                }
            }
            Text(
                Fmt.signedEuro(summe), style = if (kopf) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = signal.forChange(if (kotlin.math.abs(summe) < 0.005) 0.0 else summe), maxLines = 1, softWrap = false,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val spalten = if (maxWidth >= 440.dp) 4 else 3
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                zellen.chunked(spalten).forEach { reihe ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reihe.forEach { z ->
                            Column(Modifier.weight(1f)) {
                                Text(z.label, style = MaterialTheme.typography.labelSmall, color = sv, maxLines = 1, softWrap = false)
                                Text(z.wert, style = MaterialTheme.typography.bodySmall, color = z.farbe ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                            }
                        }
                        repeat(spalten - reihe.size) { Column(Modifier.weight(1f)) {} }
                    }
                }
            }
        }
    }
}
