package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecFormen
import kotlinx.coroutines.isActive

// Auf der Seite einer US-Aktie (seit 5.91): angekuendigte Verkaeufe nach Form 144 und Grossaktionaere nach
// Schedule 13D/13G. Geholt wird nur mit Kontaktadresse unter Zugaenge, wie bei Form 4.
@Composable
internal fun SecFormenKarte(state: AppState, symbol: String, merker: SeitenMerker? = null) {
    val alle by state.secFormen.collectAsStateWithLifecycle()
    // Platzhalter nur, solange der Abruf laeuft (5.93): Ohne Kontaktadresse oder Treffer bleibt danach kein Leerraum
    var geladen by gemerkt<Boolean>(merker, "secformen/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        // Einmal je Seite: beim Zurueckscrollen nicht erneut nachfragen
        if (geladen) return@LaunchedEffect
        try { runCatching { state.secFormenLaden(symbol) } } finally { if (isActive) geladen = true }
    }
    val s = alle[symbol] ?: run { if (!geladen) Spacer(Modifier.height(96.dp)); return }
    val ab = remember { java.time.LocalDate.now().minusDays(90).toString() }
    val verkaeufe = remember(s) { SecFormen.angekuendigt(s, ab) }
    val halter = remember(s) { SecFormen.grossaktionaere(s) }
    fun dollar(v: Double) = Fmt.mitSymbol(Fmt.compact(v), "$")
    fun tag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
    Column {
        if (verkaeufe.isNotEmpty()) {
            SectionHeader(S.f144Titel)
            AufklappText(S.f144Hinweis, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            verkaeufe.take(8).forEach { v ->
                InsiderZeile(
                    name = v.p,
                    angaben = listOfNotNull(v.rolle.ifBlank { null }, S.f144Stueck(Fmt.compact(v.n))),
                    kauf = false,
                    verkauf = v.wert > 0,
                    betrag = if (v.wert > 0) dollar(v.wert) else "–",
                    datum = S.f144Geplant(tag(v.tag.ifBlank { v.gemeldet })),
                )
            }
        }
        if (halter.isNotEmpty()) {
            SectionHeader(S.f13Titel)
            AufklappText(S.f13Hinweis, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            halter.take(10).forEach { h ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        NamenText(h.name, style = MaterialTheme.typography.titleSmall)
                        PunktText(
                            listOfNotNull(if (h.aktiv) S.f13Aktiv else S.f13Passiv, tag(h.tag.ifBlank { h.gemeldet }).ifBlank { null }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.pct(h.anteil, 1), style = MaterialTheme.typography.titleSmall)
                        if (h.n > 0) Text(S.f144Stueck(Fmt.compact(h.n)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (verkaeufe.isNotEmpty() || halter.isNotEmpty()) {
            Text(
                S.f13Quelle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}
