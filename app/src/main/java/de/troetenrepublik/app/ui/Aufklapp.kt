package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.S

// Zeigt den ersten Satz, der Rest steht hinter „Mehr“, damit keine Seite mit einem Textblock anfaengt.
// Kurze Texte erscheinen ganz.
@Composable
fun AufklappText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val (kurz, rest) = remember(text) { ersterSatz(text) }
    var offen by rememberSaveable(text) { mutableStateOf(false) }
    Column(modifier) {
        Text(if (offen || rest.isEmpty()) text else kurz, style = style, color = color)
        if (rest.isNotEmpty()) {
            Text(
                if (offen) S.aufklappWeniger else S.aufklappMehr, style = style, color = LocalSignal.current.link,
                modifier = Modifier.clickable { offen = !offen }.padding(top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

private val SATZENDE = Regex("(?<=[a-zäöüß0-9%)“”\"]{2})[.!?](?=\\s+[A-ZÄÖÜ„\"(0-9])")

// Einbuchstabige Abkuerzungen wie „z. B.“ trennen nicht. Zu kurze Texte bleiben ganz.
fun ersterSatz(text: String): Pair<String, String> {
    // Ein sehr kurzer erster Satz nimmt den naechsten mit.
    val m = SATZENDE.findAll(text).firstOrNull { it.range.first + 1 >= 40 } ?: return text to ""
    val schnitt = m.range.first + 1
    if (text.length - schnitt < 40) return text to ""
    return text.substring(0, schnitt) to text.substring(schnitt).trim()
}
