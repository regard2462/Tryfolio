package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
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
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.RedditAuswertung
import de.troetenrepublik.app.engine.TrendsErklaerung
import de.troetenrepublik.app.engine.Trendverlauf
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
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
// wird. Hacker News nur bei Technikwerten. Ohne Daten erscheint nichts.
@Composable
internal fun WertTrends(state: AppState, symbol: String, name: String, type: AssetType, sektor: String?, merker: SeitenMerker? = null) {
    if (type != AssetType.STOCK && type != AssetType.ETF && type != AssetType.CRYPTO) return
    var reddit by gemerkt<List<RedditStand>>(merker, "trends/$symbol/reddit") { emptyList() }
    var wiki by gemerkt<AppState.WikiInteresse?>(merker, "trends/$symbol/wiki") { null }
    var yahoo by gemerkt<Boolean>(merker, "trends/$symbol/yahoo") { false }
    var seit by gemerkt<Long?>(merker, "trends/$symbol/seit") { null }
    // Reddit-Beitraege zum Wert, nur mit eigenem Zugang
    var beitraege by gemerkt<List<RedditAuswertung.Beitrag>?>(merker, "trends/$symbol/beitraege") { null }
    // Hacker News (nur bei Technikwerten) und Mastodon als offene Signale, nur fuer Aktien
    var netz by gemerkt<de.troetenrepublik.app.market.Ersatzabrufe.Netz?>(merker, "trends/$symbol/netz") { null }
    // Bluesky mit eigenem App-Passwort (seit 5.92), nur fuer Aktien
    var bsky by gemerkt<de.troetenrepublik.app.engine.WeitereQuellen.BlueskyWoche?>(merker, "trends/$symbol/bsky") { null }
    // Geladen erst nach allen Abrufen: Die Karte aendert ihre Hoehe einmal statt mit jedem Teilergebnis (5.93)
    var geladen by gemerkt<Boolean>(merker, "trends/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        // Schon geladen (Merker der Seite): beim Zurueckscrollen nichts neu holen
        if (geladen) return@LaunchedEffect
        try {
            coroutineScope {
                launch { wiki = state.wikiInteresseFuer(symbol) }
                launch { yahoo = state.yahooTrends().any { it.symbol == symbol } }
                launch { beitraege = state.redditBeitraege(symbol, name) }
                if (type == AssetType.STOCK) launch { netz = state.netzErwaehnungen(name) }
                if (type == AssetType.STOCK) launch { bsky = state.bluesky(name) }
                val quellen = (if (type == AssetType.CRYPTO) listOf("all-crypto") else listOf("all-stocks", "wallstreetbets")) +
                    listOfNotNull(RedditAuswertung.QUELLE.takeIf { state.redditAktiv() })
                val listen = quellen.map { q -> async { q to state.redditTrends(q) } }.awaitAll()
                // Erst nach den Abrufen lesen: sie schreiben den heutigen Tag in den Verlauf
                val archiv = state.trendArchiv()
                reddit = listen.mapNotNull { (q, l) -> redditStand(archiv, q, l, symbol, name) }
                seit = quellen.mapNotNull { Trendverlauf.seit(archiv, it) }.minOrNull()
            }
        } finally {
            // Beim Symbolwechsel abgebrochene Aufrufe duerfen das neue Flag nicht vorzeitig setzen
            if (isActive) geladen = true
        }
    }
    val w = wiki
    val bl = beitraege
    val hn = netz?.hn?.takeIf { TrendsErklaerung.hackerNewsZeigen(sektor, it.woche, it.schnittVorher) }
    val masto = netz?.mastodon?.takeIf { l -> l.sumOf { it.beitraege } > 0 }
    val bs = bsky
    // Vor dem Laden kein Platzhalter (5.95): Bei langsamen Abrufen stand sonst eine leere Flaeche unter „Hintergrund“.
    // Die Karte erscheint einmal, wenn alles da ist.
    if (!geladen) return
    if (reddit.isEmpty() && w == null && !yahoo && bl == null && hn == null && masto == null && bs == null) return
    val heute = Trendverlauf.tag(System.currentTimeMillis())

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.trendsTitel)
        for (r in reddit) {
            val label = redditFilter(true).firstOrNull { it.first == r.quelle }?.second?.invoke() ?: r.quelle
            // Rangaenderung als gezeichnetes Dreieck wie im Portfolio (positiv: besser als vor 24 Stunden)
            val rangAenderung = r.live?.let { e -> e.rangVorher?.takeIf { it > 0 }?.let { it - e.rang } }
            // Schlank (5.94): heute und die Summen je in einer Zeile, ohne Balken (der Verlauf reicht nur so weit zurueck,
            // wie die App mitschreibt). Monat und Jahr nur, wenn die Aufzeichnung so weit reicht, sonst „seit“.
            val seitTag = seit?.takeIf { heute - it < 364 }?.let { Fmt.dayMonthNum(it * TAG_MS) }
            TrendBlock(
                titel = "Reddit · $label",
                rechts = r.live?.let { S.trErwaehnungen(it.erwaehnungen) },
                rangAenderung = rangAenderung,
                zeilen = listOfNotNull(
                    r.live?.let { e ->
                        val delta = e.erwaehnungenVorher?.takeIf { it > 0 }?.let { (e.erwaehnungen.toDouble() / it - 1) * 100 }
                        S.trHeuteKurz(e.rang, delta?.let { (if (it >= 0) "+" else "−") + Fmt.pct(abs(it), 0) })
                    } ?: S.trWertHeuteNicht,
                    S.trSummenKurz(
                        Fmt.num(r.woche.toDouble(), 0),
                        Fmt.num(r.monat.toDouble(), 0).takeIf { seit == null || heute - seit!! >= 30 },
                        Fmt.num(r.jahr.toDouble(), 0),
                        seitTag,
                        r.spitze?.let { (t, n) -> Fmt.num(n.toDouble(), 0) to Fmt.dayMonthNum(t * TAG_MS) },
                    ),
                ),
            )
        }
        if (bl != null) RedditBeitraege(bl)
        if (yahoo) TrendBlock(titel = S.trYahoo, rechts = null, zeilen = listOf(S.trWertYahooText))
        if (w != null) {
            TrendBlock(
                titel = S.trWiki,
                rechts = null,
                wert = w.wert.veraenderung,
                zeilen = listOf(S.trWikiProTag(Fmt.num(w.wert.proTag, 0))),
            ) {
                Erwaehnungsbalken(w.aufrufe, beschriftung = remember(heute, w.aufrufe.size) { balkenBeschriftung(w.aufrufe.size, heute - 1) },
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (hn != null) TrendBlock(
            titel = S.ersHnTitel, rechts = null, wert = hn.veraenderung,
            zeilen = listOf(S.ersHnZeile(hn.woche, Fmt.num(hn.schnittVorher, if (hn.schnittVorher < 10) 1 else 0))),
        )
        if (masto != null) TrendBlock(
            titel = S.ersMastodonTitel(netz?.hashtag.orEmpty()), rechts = null,
            zeilen = listOf(S.ersMastodonKurz(masto.sumOf { it.beitraege })),
        ) {
            Erwaehnungsbalken(masto.map { it.beitraege }, beschriftung = remember(heute, masto.size) { balkenBeschriftung(masto.size, heute) },
                modifier = Modifier.padding(top = 8.dp))
        }
        if (bs != null) TrendBlock(
            titel = S.wqBskyTitel, rechts = null, wert = bskyVeraenderung(bs),
            zeilen = listOf(S.wqBskyZeile(bs.woche, bs.vorwoche, bs.gekappt)),
        )
        // X und Stocktwits lassen keine freien Abrufe zu, nur der Link in den Browser (5.92)
        if (type == AssetType.STOCK) SozialeLinks(symbol)
        // Woher die Zahlen kommen, nur auf Wunsch; ein Satz je sichtbarer Zeile (5.97)
        val teile = TrendsErklaerung.teile(
            TrendsErklaerung.Sichtbar(
                redditApewisdom = reddit.any { it.quelle != RedditAuswertung.QUELLE },
                redditDeutsch = reddit.any { it.quelle == RedditAuswertung.QUELLE },
                redditBeitraege = bl != null, yahoo = yahoo, wikipedia = w != null,
                hackerNews = hn != null, mastodon = masto != null, bluesky = bs != null,
            ),
        )
        // Leer kommt praktisch nicht vor: die Karte erscheint nur mit Daten
        if (teile.isNotEmpty()) ErklaerungAufklappen(
            TrendsErklaerung.text(teile) { t ->
                when (t) {
                    TrendsErklaerung.Teil.REDDIT -> S.trErkReddit
                    TrendsErklaerung.Teil.REDDIT_DEUTSCH -> S.trErkRedditDeutsch
                    TrendsErklaerung.Teil.VERLAUF -> S.trErkVerlauf(seit?.let { Fmt.date(it * TAG_MS) })
                    TrendsErklaerung.Teil.BEITRAEGE -> S.trErkBeitraege
                    TrendsErklaerung.Teil.YAHOO -> S.trErkYahoo
                    TrendsErklaerung.Teil.WIKIPEDIA -> S.trErkWiki
                    TrendsErklaerung.Teil.HACKER_NEWS -> S.trErkHn
                    TrendsErklaerung.Teil.MASTODON -> S.trErkMastodon(netz?.hashtag.orEmpty())
                    TrendsErklaerung.Teil.BLUESKY -> S.trErkBluesky
                    TrendsErklaerung.Teil.NAMEN -> S.ersNetzHinweis
                }
            },
            Modifier.padding(bottom = 6.dp),
            titel = S.aufZahlenHerkunft,
        )
    }
}

// Ein Eintrag der Trends eines Werts: Titel, rechts eine Zahl oder Veraenderung, Zeilen darunter, optional Balken.
@Composable
private fun TrendBlock(titel: String, rechts: String?, zeilen: List<String>, wert: Double? = null, rangAenderung: Int? = null, balken: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            when {
                wert != null -> MetricValue(wert, Fmt.pct(abs(wert), 0))
                // Kurzangabe rechts bleibt einzeilig
                rechts != null -> Text(rechts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
        for (z in zeilen) PunktText(z)
        if (rangAenderung != null && rangAenderung != 0) ChangeValue(rangAenderung.toDouble(), S.trRangAenderung(abs(rangAenderung), rangAenderung > 0), style = MaterialTheme.typography.bodySmall)
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
            AuslaufText(b.titel, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            PunktText(
                listOf("r/" + b.sub, S.trRedditPunkte(b.punkte, b.kommentare), Fmt.relative(b.zeit)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (liste.size > 4) ListenPfeil(alle) { alle = !alle }
}
