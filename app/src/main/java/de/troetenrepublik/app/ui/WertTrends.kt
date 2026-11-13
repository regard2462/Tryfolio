package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.ListenKern
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
    // Die sieben Tage davor, fuer die Veraenderung zur Vorwoche
    val vorwoche: Int,
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
        vorwoche = jahr.takeLast(14).take(7).sum(),
        monat = jahr.takeLast(30).sum(),
        jahr = jahr.sum(),
        spitze = Trendverlauf.spitze(a, quelle, k, REDDIT_ZEITEN.last(), heute),
        balken = jahr.takeLast(30),
        bekanntAb = (seit - (heute - 29)).toInt().coerceAtLeast(0),
    )
}

/// Eine Quelle der Trends als Tabellenzeile: Name, Verlauf und Veraenderung zur Vorwoche in der ersten Zeile, die
// Kennzahl darunter; was nicht hineinpasst, steht im Detail nach dem Aufklappen der Zeile.
private class TrendZeileDaten(
    val id: String,
    val name: String,
    val kennzahl: String,
    val prozent: Double?,
    val verlauf: List<Int> = emptyList(),
    val detail: (@Composable () -> Unit)? = null,
)

// Trends auf der Seite eines Werts, vor den Werkzeugen: wie oft er auf Reddit besprochen wird (je Quelle,
// nur wenn er dort vorkommt), ob er bei Yahoo Finance gerade gefragt ist und wie oft sein Wikipedia-Artikel gelesen
// wird. Hacker News nur bei Technikwerten. Ohne Daten erscheint nichts. Zugeklappt steht die Kernaussage; die Karte
// laedt und rechnet, die Zeilen kommen als Eintraege der Seite (wertTrendsAbschnitt).
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
    val uri = appLinks()
    val modell = remember(geladen, reddit, wiki, yahoo, seit, beitraege, netz, bsky, symbol, sektor, type) {
        // Vor dem Laden kein Platzhalter (5.95): Die Karte erscheint einmal, wenn alles da ist.
        if (!geladen) null else trendsModell(merker, symbol, type, sektor, reddit, wiki, yahoo, seit, beitraege, netz, bsky, uri)
    }
    ModellAusgabe(merker, "trends", modell)
}

internal fun LazyListScope.wertTrendsAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "trends")
}

private fun trendsModell(
    merker: SeitenMerker?, symbol: String, type: AssetType, sektor: String?,
    reddit: List<RedditStand>, w: AppState.WikiInteresse?, yahoo: Boolean, seit: Long?,
    bl: List<RedditAuswertung.Beitrag>?, netz: de.troetenrepublik.app.market.Ersatzabrufe.Netz?,
    bs: de.troetenrepublik.app.engine.WeitereQuellen.BlueskyWoche?, uri: androidx.compose.ui.platform.UriHandler,
): ListenModell? {
    val hn = netz?.hn?.takeIf { TrendsErklaerung.hackerNewsZeigen(sektor, it.woche, it.schnittVorher) }
    val masto = netz?.mastodon?.takeIf { l -> l.sumOf { it.beitraege } > 0 }
    if (reddit.isEmpty() && w == null && !yahoo && bl == null && hn == null && masto == null && bs == null) return null
    val heute = Trendverlauf.tag(System.currentTimeMillis())
    val zeilen = ArrayList<TrendZeileDaten>()
    val quellen = ArrayList<ListenKern.TrendQuelle>()
    for (r in reddit) {
        val label = redditFilter(true).firstOrNull { it.first == r.quelle }?.second?.invoke() ?: r.quelle
        // Rangaenderung als gezeichnetes Dreieck wie im Portfolio (positiv: besser als vor 24 Stunden)
        val rangAenderung = r.live?.let { e -> e.rangVorher?.takeIf { it > 0 }?.let { it - e.rang } }
        // Monat und Jahr nur, wenn die Aufzeichnung so weit reicht, sonst „seit“ (der Verlauf reicht nur so weit zurueck,
        // wie die App mitschreibt)
        val seitTag = seit?.takeIf { heute - it < 364 }?.let { Fmt.dayMonthNum(it * TAG_MS) }
        val detail = listOfNotNull(
            r.live?.let { e ->
                val delta = e.erwaehnungenVorher?.takeIf { it > 0 }?.let { (e.erwaehnungen.toDouble() / it - 1) * 100 }
                S.trHeuteKurz(e.rang, delta?.let { (if (it >= 0) "+" else "−") + Fmt.pct(abs(it), 0) })
            } ?: S.trWertHeuteNicht,
            S.trSummenKurz(
                Fmt.num(r.woche.toDouble(), 0),
                Fmt.num(r.monat.toDouble(), 0).takeIf { seit == null || heute - seit >= 30 },
                Fmt.num(r.jahr.toDouble(), 0),
                seitTag,
                r.spitze?.let { (t, n) -> Fmt.num(n.toDouble(), 0) to Fmt.dayMonthNum(t * TAG_MS) },
            ),
        )
        val prozent = ListenKern.prozent(r.woche.toDouble(), r.vorwoche.toDouble())
        zeilen.add(
            TrendZeileDaten("reddit-${r.quelle}", "Reddit · $label", S.trKennErw(r.woche), prozent, r.balken) {
                Column {
                    detail.forEach { PunktText(it) }
                    if (rangAenderung != null && rangAenderung != 0) ChangeValue(rangAenderung.toDouble(), S.trRangAenderung(abs(rangAenderung), rangAenderung > 0), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
        // Reddit zuerst, die staerkste Quelle nach Erwaehnungen der Woche
        quellen.add(ListenKern.TrendQuelle(0, prozent, 1_000_000.0 + r.woche))
    }
    if (bl != null) {
        val st = RedditAuswertung.stimmung(bl)
        zeilen.add(
            TrendZeileDaten("beitraege", S.trRedditBeitraege, if (bl.isEmpty()) "–" else S.trRedditAnzahl(bl.size), null) {
                RedditBeitraegeDetail(bl, if (bl.isEmpty()) S.trRedditKeineBeitraege else S.trRedditStimmung(st.steigend, st.fallend, st.offen), uri)
            },
        )
    }
    if (yahoo) zeilen.add(TrendZeileDaten("yahoo", "Yahoo Finance", S.trYahooKurz, null) { PunktText(S.trWertYahooText) })
    if (w != null) {
        val beschriftung = balkenBeschriftung(w.aufrufe.size, heute - 1)
        zeilen.add(
            TrendZeileDaten("wiki", S.trWiki, S.trWikiProTag(Fmt.num(w.wert.proTag, 0)), w.wert.veraenderung, w.aufrufe) {
                Erwaehnungsbalken(w.aufrufe, beschriftung = beschriftung)
            },
        )
        quellen.add(ListenKern.TrendQuelle(1, w.wert.veraenderung, 0.5))
    }
    if (hn != null) {
        zeilen.add(TrendZeileDaten("hn", S.ersHnTitel, S.trKennBeitraege(hn.woche), hn.veraenderung) {
            PunktText(S.ersHnZeile(hn.woche, Fmt.num(hn.schnittVorher, if (hn.schnittVorher < 10) 1 else 0)))
        })
        quellen.add(ListenKern.TrendQuelle(0, hn.veraenderung, hn.woche.toDouble()))
    }
    if (masto != null) {
        val summe = masto.sumOf { it.beitraege }
        val beschriftung = balkenBeschriftung(masto.size, heute)
        zeilen.add(TrendZeileDaten("mastodon", S.ersMastodonTitel(netz?.hashtag.orEmpty()), S.ersMastodonKurz(summe), null, masto.map { it.beitraege }) {
            Erwaehnungsbalken(masto.map { it.beitraege }, beschriftung = beschriftung)
        })
        quellen.add(ListenKern.TrendQuelle(0, null, summe.toDouble()))
    }
    if (bs != null) {
        zeilen.add(TrendZeileDaten("bluesky", S.wqBskyTitel, S.trKennBeitraege(bs.woche), bskyVeraenderung(bs)) { PunktText(S.wqBskyZeile(bs.woche, bs.vorwoche, bs.gekappt)) })
        quellen.add(ListenKern.TrendQuelle(0, bskyVeraenderung(bs), bs.woche.toDouble()))
    }
    val k = ListenKern.trend(quellen)
    val kern = k?.let {
        if (it.prozent != null) KernZeile(listOf(KernTeil(S.kernTrend(Fmt.pct(abs(it.prozent), 0), it.einheit), if (abs(it.prozent) < 0.5) 0.0 else if (it.prozent > 0) 1.0 else -1.0)))
        else KernZeile(listOf(KernTeil(
            // Ohne Veraenderung zaehlt der Stand der staerksten Quelle statt der Zahl der Quellen
            reddit.maxByOrNull { r -> r.woche }?.let { S.trKennErw(it.woche) }
                ?: hn?.let { S.trKennBeitraege(it.woche) } ?: bs?.let { S.trKennBeitraege(it.woche) }
                ?: masto?.let { m -> S.ersMastodonKurz(m.sumOf { it.beitraege }) }
                ?: w?.let { S.trWikiProTag(Fmt.num(it.wert.proTag, 0)) }
                ?: (if (yahoo) S.trYahooKurz else S.kernTrendQuellen(zeilen.size)),
        )))
    }
    // Woher die Zahlen kommen, nur auf Wunsch; ein Satz je sichtbarer Zeile (5.97)
    val teile = TrendsErklaerung.teile(
        TrendsErklaerung.Sichtbar(
            redditApewisdom = reddit.any { it.quelle != RedditAuswertung.QUELLE },
            redditDeutsch = reddit.any { it.quelle == RedditAuswertung.QUELLE },
            redditBeitraege = bl != null, yahoo = yahoo, wikipedia = w != null,
            hackerNews = hn != null, mastodon = masto != null, bluesky = bs != null,
        ),
    )
    val erklaerung = TrendsErklaerung.text(teile) { t ->
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
    }
    return ListenModell(
        titel = S.trendsTitel,
        gross = true,
        kern = kern,
        zeilen = zeilen.map { z -> ZeilenDaten(z.id) { TrendTabellenZeile(merker, symbol, z) } },
        immerZu = true,
        nachspann = {
            Column {
                // X und Stocktwits lassen keine freien Abrufe zu, nur der Link in den Browser (5.92)
                if (type == AssetType.STOCK) SozialeLinks(symbol)
                if (teile.isNotEmpty()) ErklaerungAufklappen(erklaerung, Modifier.padding(bottom = 6.dp), titel = S.aufZahlenHerkunft)
            }
        },
    )
}

// Zeile der Tabelle: Name, kleiner Verlauf und Veraenderung (Dreieck mit Prozent) oben, die Kennzahl darunter. Mit Detail
// klappt ein Tipp die Einzelheiten auf; der Zustand bleibt je Seite erhalten.
@Composable
private fun TrendTabellenZeile(merker: SeitenMerker?, symbol: String, z: TrendZeileDaten) {
    var auf by gemerkt<Boolean>(merker, "trends/$symbol/auf/${z.id}") { false }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().then(if (z.detail != null) Modifier.clickable { auf = !auf } else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(z.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (z.verlauf.size >= 2 && z.verlauf.any { it > 0 }) {
                Spacer(Modifier.width(10.dp))
                MiniVerlauf(z.verlauf, Modifier.width(56.dp).height(22.dp))
            }
            if (z.prozent != null) {
                Spacer(Modifier.width(12.dp))
                ChangeValue(z.prozent, Fmt.pct(abs(z.prozent), 0), style = MaterialTheme.typography.titleSmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(z.kennzahl, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
            if (z.detail != null) AufklappPfeil(auf)
        }
        if (auf && z.detail != null) Column(Modifier.padding(top = 6.dp)) { z.detail.invoke() }
    }
}

// Kleiner Verlauf als Linie ueber die Werte, ohne Achsen und Beschriftung.
@Composable
private fun MiniVerlauf(werte: List<Int>, modifier: Modifier) {
    val farbe = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val hoch = werte.max().coerceAtLeast(1).toFloat()
        val schritt = size.width / (werte.size - 1)
        val pfad = Path()
        werte.forEachIndexed { i, v ->
            val x = i * schritt
            val y = size.height - v / hoch * size.height
            if (i == 0) pfad.moveTo(x, y) else pfad.lineTo(x, y)
        }
        drawPath(pfad, farbe, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Die neuesten Reddit-Beitraege zu einem Wert: Stimmung nach einfachen Wortregeln, darunter die Beitraege mit Subreddit,
// Punkten, Kommentaren und Alter; ein Tipp oeffnet den Beitrag bei Reddit.
@Composable
private fun RedditBeitraegeDetail(liste: List<RedditAuswertung.Beitrag>, stimmung: String, uri: androidx.compose.ui.platform.UriHandler) {
    var alle by remember(liste) { mutableStateOf(false) }
    Column {
        PunktText(stimmung)
        for (b in liste.take(if (alle) 15 else 4)) {
            Column(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(b.link) } }.padding(vertical = 8.dp)) {
                Text(b.titel, style = MaterialTheme.typography.bodyMedium)
                PunktText(
                    listOf("r/" + b.sub, S.trRedditPunkte(b.punkte, b.kommentare), Fmt.relative(b.zeit)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (liste.size > 4) ListenPfeil(alle, label = if (alle) null else S.bbAlle(minOf(liste.size, 15))) { alle = !alle }
    }
}
