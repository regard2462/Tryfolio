package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Luecke
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik

// Stand einer mitgelieferten Momentaufnahme von Meldungen: "Daten bis TT.MM.JJJJ" und, wenn die App den Zeitraum seit dem
// Stand nicht nachholen konnte, die Luecke dazwischen. Ohne lesbaren Stand nichts.
@Composable
internal fun InsiderStand(stand: String, luecke: Luecke.Spanne?) {
    val a = remember(stand, luecke) { Luecke.anzeige(stand, luecke) } ?: return
    fun tag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(S.insStand(tag(a.bis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        a.luecke?.let { Text(S.insLuecke(tag(it.von), tag(it.bis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
