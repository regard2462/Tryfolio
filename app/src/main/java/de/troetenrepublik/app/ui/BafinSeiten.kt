package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.Politik

// Eigengeschaefte von Vorstaenden und Aufsichtsraeten deutscher Emittenten und bedeutende Stimmrechtsanteile aus
// den Datenbanken der BaFin (seit 5.5, siehe market/Bafin.kt).

// Betrag einer Meldung oder Firma: Euro wie ueberall, andere Waehrungen mit ihrem Zeichen.
internal fun bafinBetrag(v: Double, w: String): String =
    if (w.isBlank() || w == "EUR") Fmt.compactEuro(v) else Fmt.mitSymbol(Fmt.compact(v), Derivatives.currencySymbol(w))

private fun tag(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

// Reiter „Insider“ im Copytrading: Insidergeschaefte an US-Boersen (SEC). Die deutschen Meldungen stehen seit 5.6 nur
// auf der Seite der jeweiligen Aktie.
@Composable
internal fun VorstaendeListe(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit) {
    VorstaendeUsa(state, liste, oeffnen) {}
}

// Eine Meldung: Firma (oder bei mitFirma = false die Person) links, Betrag und Art mit Datum rechts.
@Composable
private fun MeldungZeile(state: AppState, g: Bafin.Geschaeft, mitFirma: Boolean, onClick: (() -> Unit)? = null) {
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mitFirma) {
            InstrumentLogo(state, g.k ?: g.isin, g.e, size = 36)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            NamenText(if (mitFirma) g.e else g.p, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(if (mitFirma) g.p else "", S.vdRolle(g.r)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                g.vol?.let { bafinBetrag(it, g.w) } ?: "–", style = MaterialTheme.typography.titleSmall,
                color = when { g.kauf -> signal.up; g.verkauf -> signal.down; else -> MaterialTheme.colorScheme.onSurface },
            )
            Text(S.vdArt(g.art) + " · " + tag(g.d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun trendFarbe(wert: Int): androidx.compose.ui.graphics.Color {
    val signal = LocalSignal.current
    return when {
        wert > 15 -> signal.up
        wert < -15 -> signal.down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// Insider-Trend auf einen Blick: Balken von verkaufend bis kaufend, Personen je Rolle, Cluster-Kauf, Erklaerung.
@Composable
internal fun InsiderTrendBlock(t: de.troetenrepublik.app.engine.InsiderTrend.Ergebnis) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.itTitel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(S.itStufe(t.stufe.ordinal), style = MaterialTheme.typography.titleSmall, color = trendFarbe(t.wert))
        }
        // Anteil der gewichteten Kaeufer links gruen, Verkaeufer rechts rot
        val plus = ((t.wert + 100) / 200f).coerceIn(0f, 1f)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
            if (plus > 0f) Box(Modifier.weight(plus).fillMaxHeight().background(signal.up))
            if (plus < 1f) Box(Modifier.weight(1f - plus).fillMaxHeight().background(signal.down))
        }
        Text(S.itAnzahl(t.kaeufer, t.verkaeufer), style = MaterialTheme.typography.bodyMedium)
        if (t.cluster) {
            Text(S.itCluster, style = MaterialTheme.typography.bodySmall, color = signal.up, modifier = Modifier.padding(top = 4.dp))
        }
        // Vorn ein Satz in einfachen Worten; wer wie gehandelt hat und wie gerechnet wird, steht hinter „mehr“
        val rollen = t.nachRolle.entries.joinToString(" ") { (rolle, kv) -> S.itRolleZeile(S.itRolle(rolle.ordinal), kv.first, kv.second) + "." }
        AufklappText(
            listOf(S.itSatz(t.stufe.ordinal), rollen, S.itHinweis).filter { it.isNotBlank() }.joinToString(" "),
            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// Auf der Seite einer Aktie: Eigengeschaefte der Fuehrung (zwoelf Monate) und Grossaktionaere, nur mit Daten.
@Composable
internal fun BafinKarte(state: AppState, symbol: String, isin: String, name: String) {
    val daten by state.bafin.collectAsStateWithLifecycle()
    // Erst den Stand, dann (hoechstens alle zwoelf Stunden) neue Meldungen des letzten Monats bei der BaFin
    LaunchedEffect(Unit) {
        runCatching { state.bafinLaden(netz = false) }
        if (isin.isNotBlank() && !isin.startsWith("US")) runCatching { state.bafinLaden() }
    }
    val d = daten ?: return
    val eigen = remember(d, isin, symbol) { d.eigengeschaefte.filter { (isin.isNotBlank() && it.isin == isin) || it.k == symbol } }
    val anteile = remember(d, name) { Bafin.anteileAn(name, d.stimmrechte) }
    if (eigen.isEmpty() && anteile.isEmpty()) return
    Column {
        if (eigen.isNotEmpty()) {
            SectionHeader(S.vdKarteTitel)
            val ab90 = remember { java.time.LocalDate.now().minusDays(90).toString() }
            remember(eigen) { Bafin.trend(eigen, ab90) }?.let { InsiderTrendBlock(it) }
            val kaeufe = eigen.filter { it.kauf }
            val verkaeufe = eigen.filter { it.verkauf }
            val w = eigen.firstOrNull { it.w.isNotBlank() }?.w.orEmpty()
            Text(
                S.vdZusammenfassung(
                    kaeufe.size, bafinBetrag(kaeufe.filter { it.w == w || it.w.isBlank() }.sumOf { it.vol ?: 0.0 }, w),
                    verkaeufe.size, bafinBetrag(verkaeufe.filter { it.w == w || it.w.isBlank() }.sumOf { it.vol ?: 0.0 }, w),
                ),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            eigen.take(5).forEach { g -> MeldungZeile(state, g, mitFirma = false) }
        }
        if (anteile.isNotEmpty()) {
            SectionHeader(S.grTitel)
            anteile.take(8).forEach { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NamenText(a.m, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOf(a.ml, tag(a.d)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Fmt.pct(a.gesamt), style = MaterialTheme.typography.titleSmall)
                }
            }
            Text(
                S.grHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Text(
            S.vdQuelle(d.stand), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
