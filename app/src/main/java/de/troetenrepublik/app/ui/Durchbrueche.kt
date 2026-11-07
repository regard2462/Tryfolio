package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.ohneAbbruch

// Abschnitt „Durchbrueche“ im Reiter Trends: je Thema eine Zeile mit der neuesten Schlagzeile, ein Tipp klappt bis zu
// fuenf Schlagzeilen und die zugeordneten Fonds auf. Es ist nur ein Thema zugleich offen. Der Abruf beginnt erst, wenn
// der Abschnitt ins Bild kommt (die Liste baut ihn erst dann auf), der Stand bleibt im AppState.
@Composable
internal fun DurchbruecheAbschnitt(state: AppState, onOpen: (String) -> Unit) {
    val deutsch = I18n.lang == Lang.DE
    var meldungen by remember { mutableStateOf(state.durchbruecheStand()) }
    var fertig by remember { mutableStateOf(meldungen != null) }
    LaunchedEffect(deutsch) {
        ohneAbbruch { state.durchbrueche() }.getOrNull()?.let { meldungen = it }
        fertig = true
    }
    // Zeitpunkt des Aufbaus, damit die Alter beim Scrollen nicht springen
    val jetzt = remember(meldungen) { System.currentTimeMillis() }
    val gruppen = remember(meldungen, jetzt) { Durchbrueche.gruppieren(meldungen.orEmpty(), jetzt) }
    var offen by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf(false) }
    var artikel by remember { mutableStateOf<MarketApi.NewsItem?>(null) }

    // Ohne Meldungen und nach dem Laden zeigt der Abschnitt nichts
    if (gruppen.isEmpty() && fertig) return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.dbTitel, onClick = { info = true })
        if (gruppen.isEmpty()) EmptyHint(S.trLaedt)
        gruppen.forEach { g ->
            key(g.thema.id) {
                DurchbruchThema(state, g, jetzt, deutsch, offen == g.thema.id, onOpen, { artikel = it }) { offen = if (offen == g.thema.id) null else g.thema.id }
            }
        }
        if (gruppen.isNotEmpty()) Text(
            S.dbQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 14.dp),
        )
    }
    if (info) MarktInfoBlatt(S.dbTitel, S.dbInfo) { info = false }
    artikel?.let { NewsSheet(it, onDismiss = { artikel = null }) }
}

@Composable
private fun DurchbruchThema(
    state: AppState,
    g: Durchbrueche.Gruppe,
    jetzt: Long,
    deutsch: Boolean,
    offen: Boolean,
    onOpen: (String) -> Unit,
    onArtikel: (MarketApi.NewsItem) -> Unit,
    onKlick: () -> Unit,
) {
    fun alterText(zeit: Long): String = Durchbrueche.alter(zeit, jetzt).let { S.dbAlter(it.wert, it.einheit) }
    val neueste = g.meldungen.first()
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onKlick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AuslaufText(g.thema.name(deutsch), maxLines = 1, style = MaterialTheme.typography.titleSmall)
                AuslaufText(neueste.titel, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(alterText(neueste.zeit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            AufklappPfeil(offen)
        }
        if (offen) {
            g.meldungen.forEachIndexed { i, m ->
                if (i > 0) Trennlinie()
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { onArtikel(MarketApi.NewsItem(title = m.titel, link = m.link, published = m.zeit, source = m.quelle)) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(m.titel, style = MaterialTheme.typography.bodyMedium.schlagzeile())
                    PunktText(
                        listOfNotNull(m.quelle.takeIf { it.isNotBlank() }, alterText(m.zeit)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DurchbruchFonds(state, g.thema.etfs, onOpen)
        }
    }
}

// Die zugeordneten Fonds in einer Zeile mit dem Tageskurs; ein Tipp oeffnet die Detailseite.
@Composable
private fun DurchbruchFonds(state: AppState, symbole: List<String>, onOpen: (String) -> Unit) {
    var kurse by remember(symbole) { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    LaunchedEffect(symbole) { kurse = state.durchbruchKurse(symbole) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        symbole.forEach { s ->
            Row(Modifier.clickable { onOpen(s) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                kurse[s]?.let { q ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Fmt.signedPct(q.changePct, 1), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                        color = if (q.changePct >= 0) LocalSignal.current.up else LocalSignal.current.down,
                    )
                }
            }
        }
    }
}
