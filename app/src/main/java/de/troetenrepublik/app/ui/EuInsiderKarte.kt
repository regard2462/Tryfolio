package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.InsiderWelt
import de.troetenrepublik.app.market.Politik

// Insidergeschaefte einer Aktie aus den Registern weiterer Laender (Schweden seit 5.8; Frankreich, Niederlande,
// Grossbritannien, Schweiz, Indien, Hongkong und Australien seit 5.9): Insider-Trend und die letzten Geschaefte.
// Europa und die Schweiz kommen aus den Landesstaenden, die uebrigen Laender werden je Aktie geholt. Bei kanadischen
// Aktien steht nur ein Link zu SEDI.
@Composable
internal fun EuInsiderKarte(state: AppState, symbol: String, isin: String) {
    val alle by state.euInsider.collectAsStateWithLifecycle()
    val welt by state.weltInsider.collectAsStateWithLifecycle()
    val boerse = remember(symbol) { InsiderWelt.boerse(symbol) }
    LaunchedEffect(symbol, isin) {
        if (boerse != null) {
            runCatching { state.weltLaden(symbol, isin) }
            return@LaunchedEffect
        }
        if (isin.length != 12) return@LaunchedEffect
        for (land in EuInsider.LAENDER) {
            val d = runCatching { state.euLaden(land, netz = false) }.getOrNull() ?: continue
            if (EuInsider.landFuerIsin(isin) == land || d.geschaefte.any { it.isin == isin }) runCatching { state.euLaden(land) }
        }
    }
    // Kanada: Stand aus den SEDI-Wochenberichten; steht die Aktie nicht darin, bleibt der Link zu SEDI
    if (boerse?.first == "CA" && welt[symbol]?.second.isNullOrEmpty()) { SediHinweis(); return }
    // Land und Geschaefte: je Aktie geholt oder aus einem Register, das die Aktie fuehrt (bei mehreren das mit den meisten)
    val (land, stand, eigen) = remember(alle, welt, symbol, isin) {
        welt[symbol]?.let { (l, liste) -> Triple(l, "", liste.sortedByDescending { it.d }) }
            ?: alle.values.filter { isin.isNotEmpty() }.maxByOrNull { l -> l.geschaefte.count { it.isin == isin } }
                ?.let { l -> Triple(l.land, l.stand, l.geschaefte.filter { it.isin == isin }) }
            ?: Triple("", "", emptyList())
    }
    if (eigen.isEmpty()) return
    val signal = LocalSignal.current
    val links = LocalUriHandler.current
    fun betrag(v: Double, w: String) = Fmt.mitSymbol(Fmt.compact(v), Derivatives.currencySymbol(w))
    fun tag(iso: String) = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso
    Column {
        SectionHeader(S.vdKarteTitel)
        val ab90 = remember { java.time.LocalDate.now().minusDays(90).toString() }
        remember(eigen) { EuInsider.trend(eigen, ab90) }?.let { InsiderTrendBlock(it) }
        val kaeufe = eigen.filter { it.kauf }
        val verkaeufe = eigen.filter { it.verkauf }
        val w = eigen.firstOrNull { it.w.isNotBlank() }?.w.orEmpty()
        Text(
            S.vdZusammenfassung(kaeufe.size, betrag(kaeufe.filter { it.w == w }.sumOf { it.wert }, w), verkaeufe.size, betrag(verkaeufe.filter { it.w == w }.sumOf { it.wert }, w)),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        // Zuerst echte Kaeufe und Verkaeufe; Zuteilungen und Aehnliches nur, wenn es nichts anderes gibt
        eigen.filter { it.kauf || it.verkauf }.ifEmpty { eigen }.take(5).forEach { g ->
            Row(
                Modifier.fillMaxWidth().then(if (g.q.isNotBlank()) Modifier.clickable { runCatching { links.openUri(g.q) } } else Modifier)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // Schweizer Meldungen nennen keine Namen, dann steht die Rolle oben
                    NamenText(g.p.ifBlank { S.euRolle(g.r) }, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(g.fuer, if (g.p.isBlank()) g.t else S.euRolle(g.r)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (g.wert > 0) betrag(g.wert, g.w) else "–", style = MaterialTheme.typography.titleSmall,
                        color = when { g.kauf -> signal.up; g.verkauf -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
                    )
                    Text(S.euArt(g.art) + " · " + tag(g.d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            S.euQuelle(land, if (stand.isBlank()) "" else tag(stand)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

// Kanada: SEDI laesst sich nur im Browser abfragen.
@Composable
private fun SediHinweis() {
    val links = LocalUriHandler.current
    Column {
        SectionHeader(S.vdKarteTitel)
        Text(S.euSedi, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Text(
            S.euSediLink, style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link,
            modifier = Modifier.clickable { runCatching { links.openUri(InsiderWelt.SEDI) } }.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
