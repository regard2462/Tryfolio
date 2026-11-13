package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.WertNachrichten
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss

// Nachrichten auf der Seite eines Werts: Zeile mit Merken wie im News-Reiter; der Tipp oeffnet dasselbe Blatt wie dort
// (NachrichtBlatt, mit Einordnung).

// Kennungen der gemerkten Schlagzeilen.
@Composable
internal fun rememberGemerkteNachrichten(state: AppState): Set<String> {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    return remember(settings.newsGemerkt) { state.newsGemerkt(settings).mapTo(HashSet()) { it.id } }
}

@Composable
internal fun WertNachrichtZeile(state: AppState, n: MarketApi.NewsItem, gemerkt: Set<String>, onClick: () -> Unit) {
    val e = remember(n) { WertNachrichten.eintrag(n) }
    val istGemerkt = e.id in gemerkt
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(n.title, style = MaterialTheme.typography.titleSmall.schlagzeile(), lineHeight = 22.sp)
            PunktText(
                listOfNotNull(n.published.takeIf { it > 0 }?.let { Fmt.relative(it) }, n.source.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!Rss.nurAnsicht(e)) IconButton(onClick = { state.newsMerken(e) }) {
            Icon(
                if (istGemerkt) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                if (istGemerkt) S.newsUnsave else S.newsSave,
                tint = if (istGemerkt) LocalSignal.current.link else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
