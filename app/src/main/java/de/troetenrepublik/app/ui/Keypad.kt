package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.S

// Eigenes Ziffernfeld statt der Systemtastatur: nur Ziffern, Dezimaltrennzeichen und Loeschen.
// Das Trennzeichen folgt der Sprache.
@Composable
fun Keypad(allowDecimal: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val sep = Fmt.decimalSep.toString()
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (allowDecimal) sep else "", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(Radius.chip))
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "⌫") onBackspace() else onDigit(if (key == sep) "," else key)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                S.delete,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        } else if (key.isNotEmpty()) {
                            Text(key, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

// Intern wird der Betrag immer mit Komma gefuehrt, die Anzeige uebersetzt in die Schreibweise der Sprache.
fun appendDigit(current: String, digit: String, maxDecimals: Int = 2): String {
    if (digit == "," || digit == ".") {
        if (current.contains(',')) return current
        return if (current.isEmpty()) "0," else "$current,"
    }
    if (current == "0") return digit
    val comma = current.indexOf(',')
    if (comma >= 0 && current.length - comma > maxDecimals) return current
    if (current.length >= 12) return current
    return current + digit
}

// Interne Betragseingabe wie 1234,56 in eine Zahl wandeln.
fun parseAmount(s: String): Double = s.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0

// Mit Tausendertrennung und dem Trennzeichen der Sprache.
fun displayAmount(input: String): String {
    if (input.isEmpty()) return "0"
    val comma = input.indexOf(',')
    val intPart = if (comma >= 0) input.substring(0, comma) else input
    val decPart = if (comma >= 0) input.substring(comma + 1) else null
    val grouped = intPart.reversed().chunked(3).joinToString(Fmt.groupSep.toString()).reversed()
    return if (decPart != null) grouped + Fmt.decimalSep + decPart else grouped
}

// Zahl als interne Eingabe mit Komma, etwa fuer Schnellwahl-Knoepfe.
fun toInput(v: Double, scale: Int = 2): String =
    java.math.BigDecimal.valueOf(v).setScale(scale, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
