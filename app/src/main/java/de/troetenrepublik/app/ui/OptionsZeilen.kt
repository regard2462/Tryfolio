package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Optionszeile
import de.troetenrepublik.app.i18n.S

// Gemeinsame Zeilen der Optionslisten (Optionen verkaufen, auffaellige Kontrakte, Optionen der Wertpapierseite): Kopf
// eines Werts, Titel eines Kontrakts und Bezeichnung-Wert-Paare in festen Spalten. Bezeichnungen duerfen umbrechen,
// Werte nie; passt etwas nicht nebeneinander, aendert sich der Aufbau, nicht die Schrift.

internal data class WertPaar(val titel: String, val wert: String, val warnung: Boolean = false)

// Name des Werts ganz, der Kurs rechts auf Hoehe der ersten Namenszeile.
@Composable
internal fun OptionsKopf(name: String, kurs: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).alignByBaseline())
        Text(kurs, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 12.dp).alignByBaseline())
    }
}

// Call oder Put mit Basispreis in der Farbe der Art, daneben die Faelligkeit und ein kurzer Zusatz. Die Teile bleiben
// je fuer sich ganz und ruecken bei wenig Platz als Ganzes in die naechste Zeile.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun KontraktTitel(call: Boolean, basis: String, verfallMs: Long, modifier: Modifier = Modifier, zusatz: String? = null) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farbe = if (call) LocalSignal.current.up else LocalSignal.current.down
    FlowRow(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            (if (call) S.slCall else S.slPut) + " " + basis, style = MaterialTheme.typography.labelLarge, color = farbe,
            maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
        )
        Text(
            S.ozFaellig(Fmt.dayMonthNumJahr(verfallMs)), style = MaterialTheme.typography.labelLarge, color = grau,
            maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
        )
        if (zusatz != null) Text(zusatz, style = MaterialTheme.typography.labelLarge, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
    }
}

// Paare in gleich breiten Spalten, Bezeichnung ueber dem Wert. Wie viele nebeneinander stehen, richtet sich nach dem
// breitesten Paar in der eingestellten Schriftgroesse; passt auch die Haelfte nicht, steht je Paar eine Zeile mit der
// Bezeichnung links und dem Wert rechts.
@Composable
internal fun WertePaare(paare: List<WertPaar>, modifier: Modifier = Modifier) {
    if (paare.isEmpty()) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val warn = LocalSignal.current.warn
    val titelStil = MaterialTheme.typography.labelSmall
    val wertStil = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val messer = rememberTextMeasurer()
    val luecke = 12.dp
    val lueckePx = with(LocalDensity.current) { luecke.roundToPx() }
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val breite = constraints.maxWidth
        val je = remember(paare, breite, titelStil, wertStil) {
            fun w(t: String, s: androidx.compose.ui.text.TextStyle) = messer.measure(t, s, maxLines = 1, softWrap = false).size.width
            Optionszeile.spalten(paare.map { maxOf(w(it.titel, titelStil), w(it.wert, wertStil)) }, breite, lueckePx)
        }
        if (je == 1) {
            Column {
                paare.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(p.titel, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f).alignByBaseline())
                        Text(
                            p.wert, style = wertStil, color = if (p.warnung) warn else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, softWrap = false, modifier = Modifier.padding(start = luecke).alignByBaseline(),
                        )
                    }
                }
            }
        } else {
            Column {
                paare.chunked(je).forEachIndexed { i, zeile ->
                    Row(Modifier.fillMaxWidth().padding(top = if (i > 0) 6.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(luecke)) {
                        zeile.forEach { p ->
                            Column(Modifier.weight(1f)) {
                                Text(p.titel, style = titelStil, color = grau, maxLines = 1, softWrap = false)
                                Text(p.wert, style = wertStil, color = if (p.warnung) warn else MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                            }
                        }
                        repeat(je - zeile.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// Kleine Tabelle mit festen Spalten: jede Spalte so breit wie ihr breitester Eintrag, die erste links, die uebrigen
// rechtsbuendig. Passen die Spalten nicht nebeneinander, steht je Zeile der erste Eintrag als Titel und darunter
// jede weitere Angabe mit Bezeichnung links und Wert rechts.
@Composable
internal fun FesteTabelle(kopf: List<String>, zeilen: List<List<String>>, modifier: Modifier = Modifier, onZeile: ((Int) -> Unit)? = null) {
    if (zeilen.isEmpty()) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val kopfStil = MaterialTheme.typography.labelSmall
    val wertStil = MaterialTheme.typography.bodySmall
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val lueckePx = with(dichte) { 12.dp.roundToPx() }
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val breite = constraints.maxWidth
        val spalten = remember(kopf, zeilen, kopfStil, wertStil) {
            fun w(t: String, s: androidx.compose.ui.text.TextStyle) = messer.measure(t, s, maxLines = 1, softWrap = false).size.width
            kopf.indices.map { i -> maxOf(w(kopf[i], kopfStil), zeilen.maxOf { z -> w(z.getOrElse(i) { "" }, wertStil) }) }
        }
        if (Optionszeile.tabellePasst(spalten, breite, lueckePx)) {
            @Composable
            fun Reihe(zellen: List<String>, kopfzeile: Boolean, klick: (() -> Unit)? = null) {
                Row(
                    Modifier.fillMaxWidth().then(if (klick != null) Modifier.zeilenKlick(onClick = klick).padding(vertical = 8.dp) else Modifier.padding(vertical = 2.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    zellen.forEachIndexed { i, t ->
                        Text(
                            t, style = if (kopfzeile) kopfStil else wertStil, color = if (kopfzeile) grau else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, softWrap = false, textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                            modifier = Modifier.width(with(dichte) { spalten[i].toDp() }),
                        )
                    }
                }
            }
            Column {
                Reihe(kopf, true)
                zeilen.forEachIndexed { i, z -> Reihe(z, false, onZeile?.let { f -> { f(i) } }) }
            }
        } else {
            Column {
                zeilen.forEachIndexed { nr, z ->
                    Column(if (onZeile != null) Modifier.fillMaxWidth().zeilenKlick { onZeile(nr) } else Modifier.fillMaxWidth()) {
                        Text(kopf[0] + " " + z[0], style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        for (i in 1 until kopf.size) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(kopf[i], style = wertStil, color = grau, modifier = Modifier.weight(1f).alignByBaseline())
                                Text(z.getOrElse(i) { "" }, style = wertStil, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 12.dp).alignByBaseline())
                            }
                        }
                    }
                }
            }
        }
    }
}
