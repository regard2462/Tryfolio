package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.engine.EigenerFeed
import de.troetenrepublik.app.engine.Wertglocke
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Rss

// Alle Glocken in einer Liste (Teil der Seite Mitteilungen): Wertpapiere mit einer Zeile der aktiven Arten, danach
// Personen aus Copytrading. Ein Tipp oeffnet das Blatt des Werts bzw. die Seite der Person, die Glocke schaltet ab.
internal fun LazyListScope.glockenListe(
    state: AppState,
    alarme: List<PriceAlert>,
    personen: List<String>,
    stand: de.troetenrepublik.app.market.CopyRepository.Stand,
    oeffnen: (String) -> Unit,
    finder: List<de.troetenrepublik.app.FinderAlarm> = emptyList(),
    finderAus: (de.troetenrepublik.app.FinderAlarm) -> Unit = {},
    wertBlatt: (String, String) -> Unit,
) {
    val werte = Wertglocke.werte(alarme)
    item(key = "glocken-kopf", contentType = "kopf") { SectionHeader(S.glockeListe) }
    if (werte.isEmpty() && personen.isEmpty() && finder.isEmpty()) item(key = "glocken-leer", contentType = "leer") { EmptyHint(S.glockeLeer) }
    items(werte.size, key = { "w-" + werte[it].first }, contentType = { "wert" }) { i ->
        val (symbol, name) = werte[i]
        WertGlockeZeile(state, symbol, name, alarme.filter { it.symbol == symbol }) { wertBlatt(symbol, name) }
    }
    items(personen.size, key = { "g-" + personen[it] }, contentType = { "person" }) { i ->
        val key = personen[i]
        val (name, gruppe) = glockenName(state, key, stand.meldungen)
        Row(
            Modifier.fillMaxWidth().clickable { oeffnen(glockenRoute(key)) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonBild(Fotos.von(state, key, stand.meldungen.firstOrNull { "k:" + it.person == key }?.wahlkreis), name, size = 38)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(gruppe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GlockenKnopf(state, key, name)
        }
    }
    // Alarme des Aktienfinders: Vorlage und Markt; ein Tipp oeffnet den Finder mit beidem
    items(finder.size, key = { "a-" + finder[it].universum + "-" + finder[it].vorlage }, contentType = { "finder" }) { i ->
        val a = finder[i]
        Row(
            Modifier.fillMaxWidth().clickable { finderAlarmOeffnen(a); oeffnen(AKTIENFINDER_ROUTE) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(S.afVorlage(a.vorlage), style = MaterialTheme.typography.titleSmall)
                PunktText(universumName(a.universum) + PUNKT + S.afTitel)
            }
            IconButton(onClick = { finderAus(a) }) {
                Icon(Icons.Filled.NotificationsActive, S.glockeAus, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    item(key = "glocken-neu", contentType = "aktion") { ActionLineWide(S.copyBellsAdd) { oeffnen(CopyRouten.START) } }
}

@Composable
private fun WertGlockeZeile(state: AppState, symbol: String, name: String, mine: List<PriceAlert>, onClick: () -> Unit) {
    val zeile = remember(mine) {
        Wertglocke.zusammenfassung(
            mine, { S.glockeArtKurz(it) },
            { l -> if (l.size == 1) S.glockeMarkeKurz(Fmt.priceEuro(l[0])) else S.glockeMarkenKurz(l.size) },
            { S.glockeBewegungKurz(Fmt.pct(it, 1)) },
            { S.glockeWeitere(it) },
        )
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(state, symbol, name, size = 38)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, style = MaterialTheme.typography.titleSmall)
            Text(zeile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { state.glockeAus(symbol) }) {
            Icon(Icons.Filled.NotificationsActive, S.glockeAus, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// Name und Art der Pruefung zu einem Schluessel.
private fun glockenName(state: AppState, key: String, meldungen: List<Kongress.Meldung>): Pair<String, String> {
    val rest = key.substringAfter(':')
    return when (key.substringBefore(':')) {
        "k" -> (meldungen.firstOrNull { it.person == rest }?.name ?: rest.split('|').let { "${it.getOrElse(1) { "" }} ${it[0]}".trim() }) to S.copyBellHouse
        "n" -> (state.senat?.meldungen?.filter { it.person == rest }?.maxByOrNull { Politik.tag(it.eingang) }?.name ?: rest.replace('|', ' ')) to S.copyBellSenate
        "r" -> (state.regierung?.personen?.firstOrNull { it.id == rest }?.name ?: rest) to S.copyBellGovernment
        "i" -> (state.investorMit(rest)?.name ?: rest) to S.copyBellInvestor
        else -> key to ""
    }
}

private fun glockenRoute(key: String): String {
    val rest = key.substringAfter(':')
    return when (key.substringBefore(':')) {
        "k" -> CopyRouten.person(rest)
        "n" -> CopyRouten.senator(rest)
        "r" -> CopyRouten.regierung(rest)
        else -> CopyRouten.investor(rest)
    }
}

// Eigene Feeds und einzelne Quellen, aus denen Schlagzeilen gemeldet werden; je nach Stufe der Nachrichten-Mitteilungen.
@Composable
internal fun NewsMeldeAuswahl(state: AppState, settings: Settings) {
    val feeds = remember(settings.newsFeedKategorien) { state.newsFeeds(settings) }
    when (settings.newsMitteilung) {
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE, de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE -> {
            if (feeds.isEmpty()) Text(S.feedOhneFeeds, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            else feeds.forEach { f -> ToggleRow(f.name, f.melden, hint = S.feedMelden) { state.newsFeedMelden(f.kennung, it) } }
        }
        de.troetenrepublik.app.data.MITTEILUNG_QUELLEN -> {
            Zwischenkopf(S.feedMeldeQuellen)
            val meldeQuellen = remember(settings.newsMeldeQuellen) { EigenerFeed.meldeQuellen(settings.newsMeldeQuellen) }
            state.newsQuellen(settings).groupBy { it.anbieter }.forEach { (a, liste) ->
                Zwischenkopf(S.newsAnbieter(a))
                liste.forEach { q ->
                    HakenZeile(q.name, if (a == Rss.Anbieter.EIGENE) Rss.domain(q.adresse) else S.newsCategory(q.kategorie), q.schluessel in meldeQuellen) { state.newsMeldeQuelleSchalten(q.schluessel, it) }
                }
            }
        }
    }
}
