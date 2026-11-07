package de.troetenrepublik.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign

// Beschriftung in Kacheln, Rastern und Tabellenkoepfen (5.96): bricht nur zwischen Woertern um, nie mitten im Wort.
// Stufen: 0 Text mit geschuetzten Leerzeichen, 1 roher Text, wenn Stufe 0 ein Wort zerreisst; nie Silbentrennung.
// Gezeichnet wird erst die fertige Stufe.
@Composable
fun Beschriftung(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    var stufe by remember(text) { mutableStateOf(0) }
    var fertig by remember(text) { mutableStateOf(false) }
    // Breite, fuer die die Stufe gilt; bei anderer Breite beginnt die Pruefung von vorn
    val fuerBreite = remember(text) { IntArray(1) { -1 } }
    val inhalt = remember(text, stufe) { if (stufe == 0) beschriftungsText(text) else text }
    Text(
        inhalt,
        modifier = modifier.drawWithContent { if (fertig) drawContent() },
        style = style.copy(
            hyphens = Hyphens.None,
            lineBreak = LineBreak.Heading,
        ),
        color = color,
        textAlign = textAlign,
        onTextLayout = { r ->
            val breite = r.layoutInput.constraints.maxWidth
            var pruefen = !fertig
            if (breite != fuerBreite[0]) {
                fuerBreite[0] = breite
                fertig = false
                // Mit hoeherer Stufe folgt ein neues Layout mit Stufe 0, dort wird geprueft
                pruefen = stufe == 0
                stufe = 0
            }
            if (pruefen) {
                val imWort = (0 until r.lineCount - 1).any { i ->
                    val e = r.getLineEnd(i)
                    e in 1 until inhalt.length && inhalt[e - 1].isLetterOrDigit() && inhalt[e].isLetterOrDigit()
                }
                // Ohne geschuetzte Leerzeichen gleicht Stufe 1 der Stufe 0, dann bleibt es bei Stufe 0,
                // sonst aenderte sich nichts und es gaebe kein neues Layout
                if (imWort && stufe == 0 && inhalt != text) stufe = 1 else fertig = true
            }
        },
    )
}

private val ZAHL_UND_WORT = Regex("([0-9]) (?=[A-Za-zÄÖÜäöüß])")

// Geschuetzte Leerzeichen halten Zahl und Einheit („12 Monate“) sowie „S&P 500“ beim Umbruch zusammen.
internal fun beschriftungsText(text: String): String =
    text
        .replace(ZAHL_UND_WORT, "\$1\u00A0")
        .replace("S&P 500", "S&P\u00A0500")
