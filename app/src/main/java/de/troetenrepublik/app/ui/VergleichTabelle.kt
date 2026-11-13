package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Vergleich
import de.troetenrepublik.app.i18n.S

// Spalte einer Vergleichstabelle: kurzer Name, davor optional der Farbpunkt der Linie im Diagramm.
internal class VergleichSpalte(val name: String, val farbe: Color? = null)

// Zeile: Bezeichnung links (bricht um, optional kleine Unterzeile), je Spalte ein Wert in fester Spalte rechts. Mit balken
// (Anteil 0 bis 1) steht unter der Bezeichnung ein Balken in balkenFarbe; fett hebt die Zeile hervor, balkenAlpha daempft
// den Balken der uebrigen Zeilen.
internal class VergleichZeile(
    val label: String,
    val werte: List<String>,
    val farben: List<Color?> = emptyList(),
    val hint: String? = null,
    val onClick: (() -> Unit)? = null,
    val balken: Double? = null,
    val balkenFarbe: Color = Color.Unspecified,
    val balkenAlpha: Float = 1f,
    val fett: Boolean = false,
)

// Renditewerte einheitlich nach Vorzeichen faerben (wie im Rest der App); ohne Wert bleibt die Farbe neutral.
@Composable
internal fun renditeFarben(vararg werte: Double?): List<Color?> {
    val signal = LocalSignal.current
    return werte.map { v -> v?.let { signal.forChange(it) } }
}

// Vergleich von zwei oder mehr Spalten: Kopfzeile mit Farbpunkt und Namen, darunter je Kennzahl eine Zeile mit festen
// Wertspalten in tabellarischen Ziffern. Werte bleiben einzeilig und unverkleinert, Bezeichnungen brechen um. Mit info
// steht links in der Kopfzeile ein kleines Info-Symbol, das eine kurze Erklaerung oeffnet. Der Name einer Spalte darf
// auf zwei Zeilen umbrechen, der Farbpunkt steht als Inhalt vor dem ersten Wort; alle Wertspalten sind gleich breit, die Koepfe stehen rechtsbuendig an einer gemeinsamen Grundlinie unten.
@Composable
internal fun VergleichTabelle(spalten: List<VergleichSpalte>, zeilen: List<VergleichZeile>, info: String? = null, infoTitel: String = "") {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val stil = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    val kopfStil = MaterialTheme.typography.labelMedium
    val werteSchluessel = zeilen.map { it.werte }
    val namen = spalten.map { it.name }
    val punkte = spalten.map { it.farbe != null }
    val breiten = remember(werteSchluessel, namen, punkte, stil, kopfStil, dichte) {
        List(spalten.size) { i ->
            var b = 0
            werteSchluessel.forEach { w -> w.getOrNull(i)?.let { b = maxOf(b, messer.measure(it, stil, maxLines = 1, softWrap = false).size.width) } }
            val wert = Vergleich.spaltenBreite(with(dichte) { b.toDp().value })
            val punkt = if (punkte[i]) Vergleich.PUNKT_DP else 0f
            val kopf = kopfBreitePx(messer, namen[i], kopfStil).let { with(dichte) { it.toDp().value } } + punkt + Vergleich.POLSTER_DP
            maxOf(wert, kopf)
        }.let { l -> val gleich = (l.maxOrNull() ?: 0f).dp; List(l.size) { gleich } }
    }
    var infoOffen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (info != null) Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = { infoOffen = true }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(16.dp)) }
        }
        spalten.forEachIndexed { i, s ->
            // Der Farbpunkt steht als Inhalt im Text vor dem ersten Wort; der Name bricht um und wird nie abgeschnitten
            val kopfText = buildAnnotatedString {
                if (s.farbe != null) { appendInlineContent("punkt", "●"); append(" ") }
                append(s.name)
            }
            val punkt = if (s.farbe != null) mapOf("punkt" to InlineTextContent(
                Placeholder(with(dichte) { 8.dp.toSp() }, with(dichte) { 8.dp.toSp() }, PlaceholderVerticalAlign.TextCenter),
            ) { Box(Modifier.fillMaxSize().clip(CircleShape).background(s.farbe)) }) else emptyMap()
            Text(kopfText, style = kopfStil, color = grau, textAlign = TextAlign.End, inlineContent = punkt, modifier = Modifier.width(breiten[i]).padding(start = 8.dp))
        }
    }
    zeilen.forEach { z ->
        val werte = Vergleich.auffuellen(z.werte, spalten.size)
        Row(
            Modifier.fillMaxWidth().then(if (z.onClick != null) Modifier.clickable(role = Role.Button, onClick = z.onClick) else Modifier)
                .heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(z.label, style = MaterialTheme.typography.bodyMedium, color = if (z.fett) MaterialTheme.colorScheme.onSurface else grau, fontWeight = if (z.fett) FontWeight.SemiBold else null)
                if (z.hint != null) Text(z.hint, style = MaterialTheme.typography.labelSmall, color = grau)
                if (z.balken != null) {
                    Spacer(Modifier.height(4.dp))
                    LageBalken(z.balken, z.balkenFarbe.copy(alpha = z.balkenAlpha), Modifier.padding(end = 8.dp))
                }
            }
            werte.forEachIndexed { i, w ->
                Text(
                    w, style = stil, color = z.farben.getOrNull(i) ?: MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, modifier = Modifier.width(breiten[i]).padding(start = 8.dp),
                )
            }
        }
    }
    if (infoOffen && info != null) MarktInfoBlatt(infoTitel, info) { infoOffen = false }
}

// Schmalste Breite in Pixeln, bei der der Name hoechstens zwei Zeilen braucht (mindestens das laengste Wort).
private fun kopfBreitePx(messer: androidx.compose.ui.text.TextMeasurer, name: String, stil: androidx.compose.ui.text.TextStyle): Int {
    val ganz = messer.measure(name, stil, maxLines = 1, softWrap = false).size.width
    var w = name.split(' ').maxOf { messer.measure(it, stil, maxLines = 1, softWrap = false).size.width }
    while (w < ganz && messer.measure(name, stil, constraints = Constraints(maxWidth = w)).lineCount > 2) w += 4
    return minOf(w, ganz)
}
