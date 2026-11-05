package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.engine.Waehrungsfolge
import de.troetenrepublik.app.i18n.S

// Name eines Eintrags der Waehrungsfolge: Originalwaehrung oder „US-Dollar (USD)“.
fun waehrungsFolgeName(eintrag: String): String =
    if (eintrag == Waehrungsfolge.ORIGINAL) S.wfOriginal else "${S.waehrungName(eintrag)} ($eintrag)"

// Kurzform fuer die Zeile im Profil, etwa „EUR · USD · Original“.
fun waehrungsFolgeText(folge: List<String>, basis: String): String =
    Waehrungsfolge.wirksam(folge, basis).joinToString(" · ") { if (it == Waehrungsfolge.ORIGINAL) S.wfOriginalKurz else it }

// Blatt „Waehrungen beim Antippen“ (seit 5.92): oben die gewaehlten Eintraege in ihrer Reihenfolge zum Verschieben und
// Entfernen, darunter alle weiteren zum Hinzufuegen. Jede Aenderung gilt sofort.
@Composable
internal fun ColumnScope.WaehrungsFolgeAuswahl(state: AppState) {
    val folge = Waehrungsfolge.wirksam(Fmt.folge, Fmt.basis)
    val eigen = Fmt.folge.any { it != Waehrungsfolge.ORIGINAL }
    val echte = folge.count { it != Waehrungsfolge.ORIGINAL }
    Text(S.wfTitel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp))
    Text(S.wfText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

    Text(S.wfReihenfolge, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp))
    folge.forEachIndexed { i, e ->
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
            Text(waehrungsFolgeName(e), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { state.waehrungFolgeSetzen(Waehrungsfolge.verschieben(folge, i, -1)) }, enabled = i > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, S.wfHoch)
            }
            IconButton(onClick = { state.waehrungFolgeSetzen(Waehrungsfolge.verschieben(folge, i, 1)) }, enabled = i < folge.lastIndex) {
                Icon(Icons.Filled.KeyboardArrowDown, S.wfRunter)
            }
            // Die letzte echte Waehrung bleibt
            IconButton(onClick = { state.waehrungFolgeSetzen(Waehrungsfolge.umschalten(folge, e)) }, enabled = e == Waehrungsfolge.ORIGINAL || echte > 1) {
                Icon(Icons.Filled.Close, S.newsRemove, modifier = Modifier.size(20.dp))
            }
        }
    }

    val voll = folge.size >= Waehrungsfolge.MAX
    Text(
        if (voll) S.wfVoll(Waehrungsfolge.MAX) else S.add,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
    )
    if (!voll) {
        val weitere = (listOf(Waehrungsfolge.ORIGINAL, Fmt.basis) + Regionen.WAEHRUNGEN).distinct().filter { it !in folge }
        Column(Modifier.fillMaxWidth()) {
            weitere.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                        .clickable { state.waehrungFolgeSetzen(Waehrungsfolge.umschalten(folge, e)) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(waehrungsFolgeName(e), style = MaterialTheme.typography.bodyLarge)
                        if (e == Waehrungsfolge.ORIGINAL) Text(S.wfOriginalHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    PlusGlyph(size = 15.dp, color = MaterialTheme.colorScheme.onSurfaceVariant, stroke = 2.dp)
                }
            }
        }
    }
    if (eigen) {
        Text(
            S.wfVorgabe, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).clip(RoundedCornerShape(Radius.chip))
                .clickable { state.waehrungFolgeSetzen(emptyList()) }.padding(8.dp),
        )
    }
}
