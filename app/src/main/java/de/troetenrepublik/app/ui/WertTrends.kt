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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.RedditAuswertung
import de.troetenrepublik.app.engine.Trendverlauf
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// Stand eines Werts in einer Quelle auf Reddit: heute laut ApeWisdom, Woche bis Jahr aus dem Verlauf.
private class RedditStand(
    val quelle: String,
    val live: MarketApi.Erwaehnung?,
    val woche: Int,
    val monat: Int,
    val jahr: Int,
    val spitze: Pair<Long, Int>?,
    // Erwaehnungen der letzten 30 Tage, aeltester zuerst.
    val balken: List<Int>,
    // Erster Balken mit Daten (davor schrieb die App noch nicht mit).
    val bekanntAb: Int,
)

private const val TAG_MS = 86_400_000L

private fun redditStand(a: Trendverlauf.Archiv, quelle: String, liste: List<MarketApi.Erwaehnung>, symbol: String, name: String): RedditStand? {
    val namen = a.namen + liste.associate { Trendverlauf.normal(it.ticker) to it.name }
    // Die deutschen Subreddits zaehlt die App selbst, unter dem Kuerzel der App
    val k = if (quelle == RedditAuswertung.QUELLE) Trendverlauf.normal(symbol) else Trendverlauf.kuerzelFuer(symbol, name, namen) ?: return null
    val live = liste.firstOrNull { Trendverlauf.normal(it.ticker) == k }
    val heute = Trendverlauf.tag(System.currentTimeMillis())
    val jahr = Trendverlauf.reihe(a, quelle, k, REDDIT_ZEITEN.last(), heute)
    if (live == null && jahr.all { it <= 0 }) return null
    val seit = Trendverlauf.seit(a, quelle) ?: heute
    return RedditStand(
        quelle = quelle,
        live = live,
        woche = jahr.takeLast(7).sum(),
        monat = jahr.takeLast(30).sum(),
        jahr = jahr.sum(),
        spitze = Trendverlauf.spitze(a, quelle, k, REDDIT_ZEITEN.last(), heute),
        balken = jahr.takeLast(30),
        bekanntAb = (seit - (heute - 29)).toInt().coerceAtLeast(0),
    )
}

// Trends auf der Seite eines Werts, vor den Werkzeugen: wie oft er auf Reddit besprochen wird (je Quelle,
// nur wenn er dort vorkommt), ob er bei Yahoo Finance gerade gefragt ist und wie oft sein Wikipedia-Artikel gelesen
// wird. Ohne Daten erscheint nichts.
@Composable
internal fun WertTrends(state: AppState, symbol: String, name: String, type: AssetType) {
    if (type != AssetType.STOCK && type != AssetType.ETF && type != AssetType.CRYPTO) return
    var reddit by remember(symbol) { mutableStateOf<List<RedditStand>>(emptyList()) }
    var wiki by remember(symbol) { mutableStateOf<AppState.WikiInteresse?>(null) }
    var yahoo by remember(symbol) { mutableStateOf(false) }
    var seit by remember(symbol) { mutableStateOf<Long?>(null) }
    // Reddit-Beitraege zum Wert, nur mit eigenem Zugang
    var beitraege by remember(symbol) { mutableStateOf<List<RedditAuswertung.Beitrag>?>(null) }
    LaunchedEffect(symbol) {
        coroutineScope {
            launch { wiki = state.wikiInteresseFuer(symbol) }
            launch { yahoo = state.yahooTrends().any { it.symbol == symbol } }
            launch { beitraege = state.redditBeitraege(symbol, name) }
            val quellen = (if (type == AssetType.CRYPTO) listOf("all-crypto") else listOf("all-stocks", "wallstreetbets")) +
                listOfNotNull(RedditAuswertung.QUELLE.takeIf { state.redditAktiv() })
            val listen = quellen.map { q -> async { q to state.redditTrends(q) } }.awaitAll()
            // Erst nach den Abrufen lesen: sie schreiben den heutigen Tag in den Verlauf
            val archiv = state.trendArchiv()
            reddit = listen.mapNotNull { (q, l) -> redditStand(archiv, q, l, symbol, name) }
            seit = quellen.mapNotNull { Trendverlauf.seit(archiv, it) }.minOrNull()
        }
    }
    val w = wiki
    val bl = beitraege
    if (reddit.isEmpty() && w == null && !yahoo && bl == null) return
    val heute = Trendverlauf.tag(System.currentTimeMillis())

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.trendsTitel)
        for (r in reddit) {
            val label = redditFilter(true).firstOrNull { it.first == r.quelle }?.second?.invoke() ?: r.quelle
            TrendBlock(
                titel = "Reddit · $label",
                rechts = r.live?.let { S.trErwaehnungen(it.erwaehnungen) },
                zeilen = listOfNotNull(
                    r.live?.let { e ->
                        val delta = e.erwaehnungenVorher?.takeIf { it > 0 }?.let { (e.erwaehnungen.toDouble() / it - 1) * 100 }
                        S.trWertHeute(e.rang, rangPfeil(e.rang, e.rangVorher), delta?.let { (if (it >= 0) "+" else "−") + Fmt.pct(abs(it), 0) })
                    } ?: S.trWertHeuteNicht,
                    S.trWertSummen(Fmt.num(r.woche.toDouble(), 0), Fmt.num(r.monat.toDouble(), 0), Fmt.num(r.jahr.toDouble(), 0)),
                    r.spitze?.let { (t, n) -> S.trSpitzeTag(Fmt.date(t * TAG_MS), Fmt.num(n.toDouble(), 0)) },
                ),
            ) {
                Erwaehnungsbalken(r.balken, beschriftung = remember(heute) { balkenBeschriftung(r.balken.size, heute) }, bekanntAb = r.bekanntAb,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (bl != null) RedditBeitraege(bl)
        if (yahoo) TrendBlock(titel = S.trWertYahoo, rechts = null, zeilen = listOf(S.trWertYahooText))
        if (w != null) {
            TrendBlock(
                titel = S.trWiki,
                rechts = null,
                wert = w.wert.veraenderung,
                zeilen = listOf(S.trWikiProTag(Fmt.num(w.wert.proTag, 0)) + " · " + S.trWikiVergleich),
            ) {
                Erwaehnungsbalken(w.aufrufe, beschriftung = remember(heute, w.aufrufe.size) { balkenBeschriftung(w.aufrufe.size, heute - 1) },
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        AufklappText(S.trWertHinweis(seit?.let { Fmt.date(it * TAG_MS) }), Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp))
    }
}

// Ein Eintrag der Trends eines Werts: Titel, rechts eine Zahl oder Veraenderung, Zeilen darunter, optional Balken.
@Composable
private fun TrendBlock(titel: String, rechts: String?, zeilen: List<String>, wert: Double? = null, balken: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            when {
                wert != null -> MetricValue(wert, Fmt.pct(abs(wert), 0))
                rechts != null -> Text(rechts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (z in zeilen) Text(z, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        balken?.invoke()
    }
}

// Die neuesten Reddit-Beitraege zu einem Wert: Stimmung nach einfachen Wortregeln,
// darunter die Beitraege mit Subreddit, Punkten, Kommentaren und Alter; ein Tipp oeffnet den Beitrag bei Reddit.
@Composable
private fun RedditBeitraege(liste: List<RedditAuswertung.Beitrag>) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var alle by remember(liste) { mutableStateOf(false) }
    val st = remember(liste) { RedditAuswertung.stimmung(liste) }
    TrendBlock(
        titel = S.trRedditBeitraege,
        rechts = if (liste.isEmpty()) null else S.trRedditAnzahl(liste.size),
        zeilen = listOf(if (liste.isEmpty()) S.trRedditKeineBeitraege else S.trRedditStimmung(st.steigend, st.fallend, st.offen)),
    )
    for (b in liste.take(if (alle) 15 else 4)) {
        Column(
            Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(b.link) } }.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(b.titel, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                listOf("r/" + b.sub, S.trRedditPunkte(b.punkte, b.kommentare), Fmt.relative(b.zeit)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (!alle && liste.size > 4) Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { FilterPill(S.trMehr) { alle = true } }
}
