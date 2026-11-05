package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik

// Zulassungen und Rueckrufe der FDA (seit 5.9) bei Pharma-, Biotech- und Medizintechnik-Aktien: die letzten zwoelf
// Monate, Zulassungen mit Marke, Wirkstoff und Art, Rueckrufe mit Klasse, Produkt und Grund.
@Composable
internal fun FdaKarte(state: AppState, symbol: String, firma: String) {
    val alle by state.fda.collectAsStateWithLifecycle()
    LaunchedEffect(symbol, firma) { runCatching { state.fdaLaden(symbol, firma) } }
    val e = alle[symbol] ?: return
    val signal = LocalSignal.current
    fun tag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
    Column {
        SectionHeader(S.fdaTitel)
        if (e.zulassungen.isEmpty() && e.rueckrufe.isEmpty()) {
            Text(S.fdaKeine, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (e.zulassungen.isNotEmpty()) {
            Zwischenkopf(S.fdaZulassungen)
            e.zulassungen.take(8).forEach { z ->
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    PunktZeile(listOf(z.marke, z.wirkstoff.lowercase().replaceFirstChar { it.uppercase() }).distinct(),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxZeilen = 2)
                    PunktText(S.fdaArt(z.art.name, z.vorrang) + " · " + tag(z.tag), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (e.rueckrufe.isNotEmpty()) {
            Zwischenkopf(S.fdaRueckrufe)
            e.rueckrufe.sortedWith(compareBy<de.troetenrepublik.app.market.Fda.Rueckruf> { if (it.klasse == 0) 9 else it.klasse }.thenByDescending { it.tag })
                .take(8).forEach { r ->
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        AuslaufText(r.produkt, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        AuslaufText(r.grund, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        PunktText(S.fdaKlasse(r.klasse, r.geraet, r.laufend) + " · " + tag(r.tag), style = MaterialTheme.typography.bodySmall,
                            color = if (r.klasse == 1) signal.warn else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
        }
        Text(S.fdaQuelle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        AufklappText(S.fdaHinweis, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp))
    }
}
