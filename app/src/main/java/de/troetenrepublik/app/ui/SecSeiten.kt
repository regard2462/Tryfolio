package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecInsider

// Insidergeschaefte an US-Boersen (SEC Form 4, seit 5.6): Liste im Reiter „Vorstände“ und Karte auf der Aktienseite.

private fun dollar(v: Double): String = Fmt.mitSymbol(Fmt.compact(v), "$")

private fun tagUs(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

@Composable
internal fun VorstaendeUsa(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit, kopf: @Composable () -> Unit) {
    val daten by state.sec.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var laedt by remember { mutableStateOf(daten == null) }
    LaunchedEffect(Unit) { runCatching { state.secLaden() }; laedt = false }
    var filter by rememberSaveable { mutableStateOf("ALLE") }
    val alle = daten?.geschaefte.orEmpty()
    val ab = remember { java.time.LocalDate.now().minusDays(90).toString() }
    val firmen = remember(alle) { SecInsider.firmen(alle, ab) }
    val trends = remember(alle) { alle.filter { it.d >= ab }.groupBy { it.k }.mapValues { (_, g) -> SecInsider.trend(g, ab) } }
    val gefiltert = remember(alle, filter) {
        when (filter) {
            "KAEUFE" -> alle.filter { it.kauf }
            "VERKAEUFE" -> alle.filter { !it.kauf }
            else -> alle
        }.take(300)
    }
    val ohneKontakt = SecInsider.kennung(settings.secKontakt) == null

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { kopf() }
        item { AufklappText(S.usHinweis, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (ohneKontakt) item {
            Text(
                S.usOhneKontakt, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
                modifier = Modifier.clickable { oeffnen("zugaenge") }.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        if (laedt && alle.isEmpty()) item { CopyLade() }
        val kaeufer = firmen.filter { it.netto > 0 }.take(10)
        val verkaeufer = firmen.filter { it.netto < 0 }.sortedBy { it.netto }.take(5)
        if (kaeufer.isNotEmpty()) {
            item(key = "uk-kopf") { SectionHeader(S.vdMeistgekauft) }
            items(kaeufer, key = { "uk-" + it.kuerzel }) { f -> UsFirmaZeile(state, f, trends[f.kuerzel]) { oeffnen("instrument/" + f.kuerzel) } }
        }
        if (verkaeufer.isNotEmpty()) {
            item(key = "uv-kopf") { SectionHeader(S.vdMeistverkauft) }
            items(verkaeufer, key = { "uv-" + it.kuerzel }) { f -> UsFirmaZeile(state, f, trends[f.kuerzel]) { oeffnen("instrument/" + f.kuerzel) } }
        }
        if (alle.isNotEmpty()) {
            item(key = "un-kopf") {
                SectionHeader(S.vdNeueste)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALLE" to S.vdAlle, "KAEUFE" to S.vdKaeufe, "VERKAEUFE" to S.vdVerkaeufe).forEach { (k, titel) ->
                        val an = filter == k
                        Box(
                            Modifier.clip(RoundedCornerShape(Radius.chip))
                                .background(if (an) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { filter = k }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(titel, style = MaterialTheme.typography.labelMedium, color = if (an) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
            items(gefiltert, key = { "um-" + it.a + it.kauf }) { g -> UsMeldungZeile(state, g, mitFirma = true) { oeffnen("instrument/" + g.k) } }
        }
        if (!laedt && alle.isEmpty()) item { EmptyHint(S.vdLeer) }
        item { Quelle(S.usQuelle(daten?.stand.orEmpty())) }
    }
}

@Composable
private fun UsFirmaZeile(state: AppState, f: SecInsider.Firma, trend: de.troetenrepublik.app.engine.InsiderTrend.Ergebnis?, onClick: () -> Unit) {
    val signal = LocalSignal.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        InstrumentLogo(state, f.kuerzel, f.name, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NamenText(f.name, style = MaterialTheme.typography.titleSmall)
            Text(f.kuerzel + " · " + S.vdAnzahl(f.anzahlKaeufe, f.anzahlVerkaeufe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            trend?.let { t ->
                Text(S.itStufe(t.stufe.ordinal) + if (t.cluster) " · " + S.itClusterKurz else "", style = MaterialTheme.typography.labelSmall, color = trendFarbe(t.wert))
            }
        }
        Text((if (f.netto > 0) "+" else "−") + dollar(kotlin.math.abs(f.netto)), style = MaterialTheme.typography.titleSmall, color = if (f.netto > 0) signal.up else signal.down)
    }
}

@Composable
private fun UsMeldungZeile(state: AppState, g: SecInsider.Geschaeft, mitFirma: Boolean, onClick: (() -> Unit)? = null) {
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mitFirma) {
            InstrumentLogo(state, g.k, g.e, size = 36)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            NamenText(if (mitFirma) g.e else g.p, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(if (mitFirma) g.p else "", g.t.ifBlank { S.usRolle(g.r) }).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(dollar(g.wert), style = MaterialTheme.typography.titleSmall, color = if (g.kauf) signal.up else signal.down)
            Text(
                (if (g.kauf) S.vdKaeufe1 else S.vdVerkaeufe1) + (if (g.plan) " · " + S.usPlan else "") + " · " + tagUs(g.d),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Auf der Seite einer US-Aktie: Insider-Trend und die Geschaefte der Fuehrung aus zwoelf Monaten. Mit Kontaktadresse
// holt die Seite neue Meldungen dieses Werts nach.
@Composable
internal fun SecKarte(state: AppState, symbol: String) {
    val daten by state.sec.collectAsStateWithLifecycle()
    LaunchedEffect(symbol) { runCatching { state.secLaden(symbol = symbol) } }
    val d = daten ?: return
    val eigen = remember(d, symbol) { d.geschaefte.filter { it.k.equals(symbol, true) } }
    if (eigen.isEmpty()) return
    Column {
        SectionHeader(S.vdKarteTitel)
        val ab90 = remember { java.time.LocalDate.now().minusDays(90).toString() }
        remember(eigen) { SecInsider.trend(eigen, ab90) }?.let { InsiderTrendBlock(it) }
        val kaeufe = eigen.filter { it.kauf }
        val verkaeufe = eigen.filter { !it.kauf }
        Text(
            S.vdZusammenfassung(kaeufe.size, dollar(kaeufe.sumOf { it.wert }), verkaeufe.size, dollar(verkaeufe.sumOf { it.wert })),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        eigen.take(6).forEach { g -> UsMeldungZeile(state, g, mitFirma = false) }
        Text(
            S.usQuelle(d.stand), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
