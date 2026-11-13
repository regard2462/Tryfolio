package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.S

// Fassungen des Namens einer Position fuer eine Kennzahlzeile, laengste zuerst: Katalogname (Anzeigename), ohne Fonds-
// und Rechtsformzusaetze, zuletzt das Kuerzel.
private val ZUSATZ_ENDE = Regex("\\s+(Accumulation|Distribution|Accumulating|Distributing|\\(?(Acc|Dist)\\)?)$", RegexOption.IGNORE_CASE)

fun positionsKandidaten(symbol: String, name: String): List<String> {
    val namen = namensKuerzungen(kurzName(symbol, name)).flatMap { n -> listOf(n, n.replace(ZUSATZ_ENDE, "").trim()) }
        .filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
    return (namen + symbol.substringBefore('.').substringBefore('-').removePrefix("^")).filter { it.isNotBlank() }.distinct()
}

// Kennzahlzeile mit einem Namen als Wert (Regel: Groesste Position, Groesster Verlierer ...): Bezeichnung links, rechts
// Kurzname und Zahl, immer in EINER Zeile. Es steht die laengste Fassung des Namens, die neben der Bezeichnung passt.
@Composable
fun NamenWertZeile(label: String, namen: List<String>, wert: String, wertFarbe: Color? = null, onClick: (() -> Unit)? = null) {
    val stil = MaterialTheme.typography.bodyMedium
    val messer = rememberTextMeasurer()
    val abstand = with(LocalDensity.current) { 12.dp.roundToPx() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        val breite = constraints.maxWidth
        val rechts = remember(label, namen, wert, breite, stil) {
            fun w(t: String) = messer.measure(t, stil, maxLines = 1, softWrap = false).size.width
            val frei = breite - w(label) - abstand
            val name = namen.firstOrNull { w("$it  $wert") <= frei } ?: namen.lastOrNull().orEmpty()
            if (name.isEmpty()) wert else "$name  $wert"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            val text = androidx.compose.ui.text.buildAnnotatedString {
                val zahlStart = rechts.length - wert.length
                append(rechts.substring(0, zahlStart))
                withStyle(androidx.compose.ui.text.SpanStyle(color = wertFarbe ?: MaterialTheme.colorScheme.onSurface)) { append(wert) }
            }
            Text(text, style = stil, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
        }
    }
}

// Kennzahlzeile mit kleinem Info-Symbol hinter der Bezeichnung; ein Tipp zeigt die Erklaerung. Wert einzeilig rechts.
@Composable
fun InfoWertZeile(label: String, wert: String, titel: String, info: String, wertFarbe: Color? = null) {
    var offen by remember { mutableStateOf(false) }
    val stil = MaterialTheme.typography.bodyMedium
    Row(
        Modifier.fillMaxWidth().clickable { offen = true }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.weight(1f))
        Text(wert, style = stil, color = wertFarbe ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
    }
    if (offen) MarktInfoBlatt(titel, info) { offen = false }
}
