package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.S

// Eine Quelle fuer den Anhang: Name, wie ihn die Bedingungen der Quelle verlangen (z. B. mit Lizenz), und Link.
data class QuelleLink(val name: String, val url: String? = null)

// Quellen einer Seite gesammelt am Seitenende (0.70): zugeklappt nur die graue Zeile „Quellen“ mit Pfeil, aufgeklappt
// die Namen nebeneinander, jeder mit Link antippbar (oeffnet im Betrachter der App). Nur Quellen aufnehmen, deren
// Bedingungen eine Nennung verlangen. Doppelte Namen erscheinen einmal.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuellenAnhang(quellen: List<QuelleLink>, modifier: Modifier = Modifier) {
    val liste = quellen.distinctBy { it.name }
    if (liste.isEmpty()) return
    var offen by rememberSaveable { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val links = appLinks()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen }.heightIn(min = 40.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.quellenAnhang, style = MaterialTheme.typography.labelSmall, color = grau)
            Spacer(Modifier.width(6.dp))
            AufklappPfeil(offen, color = grau)
        }
        if (offen) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                liste.forEach { q ->
                    val url = q.url
                    Text(
                        q.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = grau,
                        textDecoration = if (url != null) TextDecoration.Underline else null,
                        modifier = if (url != null) Modifier.textKlick(0.dp, role = Role.Button) { links.openUri(url) } else Modifier,
                    )
                }
            }
        }
    }
}
