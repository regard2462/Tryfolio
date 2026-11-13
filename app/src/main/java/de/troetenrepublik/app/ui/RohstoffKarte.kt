package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Rohstoffe
import de.troetenrepublik.app.i18n.S
import androidx.compose.ui.semantics.Role

// Erklaerung zu einem Rohstoff auf der Wertpapierseite: der erste Abschnitt steht offen, ein Tipp auf den Text
// oder den Pfeil zeigt die weiteren und klappt sie wieder zu.
@Composable
internal fun RohstoffKarte(symbol: String, schluessel: String) {
    val abschnitte = S.rohstoffAbschnitte(schluessel, Rohstoffe.istTermin(symbol))
    if (abschnitte.isEmpty()) return
    var offen by rememberSaveable(symbol) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.rohstoffTitel, unten = TITEL_VOR_ZWISCHENKOPF)
        Column(
            if (abschnitte.size > 1) Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen } else Modifier.fillMaxWidth()
        ) {
            (if (offen) abschnitte else abschnitte.take(1)).forEachIndexed { i, (titel, text) ->
                Zwischenkopf(titel, oben = if (i == 0) 0.dp else 12.dp)
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp))
            }
            // Der Pfeil zeigt an, dass es weitergeht; die Tippflaeche ist der ganze Text
            if (abschnitte.size > 1) AufklappPfeil(offen, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
}
