package de.troetenrepublik.app.ui

import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.key
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshots.Snapshot
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.engine.Branchentrends
import de.troetenrepublik.app.engine.Trendverlauf
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// Zweite Seite der Watchlist: Entwicklung der Branchen (US-Branchen-ETFs), was bei Yahoo Finance gefragt ist und welche
// Werte auf Reddit am meisten erwaehnt werden (ApeWisdom). Woche, Monat und Jahr fuer Reddit kommen aus dem Verlauf,
// den die App mitschreibt.
@Composable
internal fun TrendsSeite(
    state: AppState,
    onOpen: (String) -> Unit,
    // Auswahl und Scrolllage; bleibt im Watchlist-Blatt erhalten, waehrend eine aus ihm geoeffnete Seite offen ist.
    lage: TrendsLage = remember { TrendsLage() },
    // Nur die echte Seite schreibt ihre Scrolllage zurueck, das Bild darunter nicht.
    merken: Boolean = true,
) {
    var kurse by remember { mutableStateOf<Map<String, List<Candle>>?>(null) }
    var alleYahoo by lage::alleYahoo
    var yahoo by remember { mutableStateOf<List<Quote>?>(null) }
    // Reddit: alle Quellen auf einmal, damit beim Wischen die Nachbarseite schon da ist
    var reddit by remember { mutableStateOf<Map<String, List<MarketApi.Erwaehnung>>>(emptyMap()) }
    var archiv by remember { mutableStateOf<Trendverlauf.Archiv?>(null) }
    // Mit eigenem Reddit-Zugang kommen die deutschen Subreddits als weitere Seite dazu
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    val filter = remember(state.redditAktiv(einstellungen)) { redditFilter(state.redditAktiv(einstellungen)) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { kurse = state.branchenKurse() }
            launch { yahoo = state.yahooTrends() }
        }
    }
    LaunchedEffect(filter) {
        coroutineScope {
            filter.forEach { (q, _) ->
                launch {
                    val l = state.redditTrends(q)
                    // Erst nach dem Abruf lesen: der Abruf schreibt den heutigen Tag in den Verlauf
                    archiv = state.trendArchiv()
                    reddit = reddit + (q to l)
                }
            }
        }
    }
    val deutsch = I18n.lang == Lang.DE

    // Branchen: Zeitraum wie beim Chart (1T bis 1J) waehlen, ohne Pillen und ohne Wischen
    var branchenZeitraum by lage::branchenZeitraum
    // Reddit-Quellen als Seiten eines Pagers wie im Copytrading: die ganze Seite folgt dem Finger, die Pille gleitet
    // mit. Die gemerkte Seite nur zum Start lesen.
    val redditPager = rememberPagerState(initialPage = Snapshot.withoutReadObservation { lage.redditSeite }.coerceIn(0, filter.size - 1)) { filter.size }
    val redditScope = rememberCoroutineScope()
    if (merken) LaunchedEffect(redditPager) {
        snapshotFlow { redditPager.currentPage }.collect { lage.redditSeite = it }
    }
    val branchenJe = remember(kurse) {
        Branchentrends.ZEITRAEUME.map { tage ->
            Branchentrends.BRANCHEN.mapNotNull { b -> kurse?.get(b.symbol)?.let { k -> Branchentrends.veraenderung(k, tage)?.let { b to it } } }
                .sortedByDescending { it.second }
        }
    }
    var redditZeit by lage::redditZeit
    var redditAlle by remember(redditZeit) { mutableStateOf(false) }
    val heute = Trendverlauf.tag(System.currentTimeMillis())
    val redditJe = remember(filter, reddit, archiv, redditZeit, redditAlle, heute) {
        filter.map { (q, _) -> redditDaten(q, REDDIT_ZEITEN[redditZeit], reddit[q], archiv, redditAlle, heute) }
    }
    // Startlage ohne Beobachtung lesen, sonst baut jeder Scrollschritt die ganze Seite neu auf.
    val liste = rememberListeAb({ lage.index }, { lage.versatz })
    if (merken) LaunchedEffect(liste) {
        snapshotFlow { liste.firstVisibleItemIndex to liste.firstVisibleItemScrollOffset }.collect { (i, v) -> lage.index = i; lage.versatz = v }
    }

    Box(Modifier.fillMaxSize()) {
    // Ohne Hoehe: misst nur die Seitenbreite fuer den gemeinsamen Zustand der Reddit-Zeilen und bleibt immer aufgebaut,
    // auch wenn der Kopf der Reddit-Liste aus dem Bild gescrollt ist
    HorizontalPager(state = redditPager, modifier = Modifier.fillMaxWidth().height(0.dp), userScrollEnabled = false) { }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        // Fear & Greed und Reddit-Stimmung
        item(key = "stimmung") { StimmungsAbschnitt(state, onOpen) }
        item(key = "branchen-kopf") {
            SectionHeader(S.trBranchen)
            RangeSelector(
                BRANCHEN_ZEITRAEUME, selected = BRANCHEN_ZEITRAEUME.getOrNull(branchenZeitraum),
                onSelect = { r -> branchenZeitraum = BRANCHEN_ZEITRAEUME.indexOf(r) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        when {
            kurse == null -> item(key = "branchen-laedt") { EmptyHint(S.trLaedt) }
            branchenJe.all { it.isEmpty() } -> item(key = "branchen-leer") { EmptyHint(S.trKeineDaten) }
            else -> items(branchenJe.getOrNull(branchenZeitraum).orEmpty(), key = { "b-" + it.first.symbol }) { (b, v) ->
                AssetRow(
                    symbol = b.symbol,
                    name = if (deutsch) b.de else b.en,
                    subtitle = b.symbol,
                    change = v,
                    changeText = Fmt.pct(abs(v)),
                    onClick = { onOpen(b.symbol) },
                )
            }
        }
        item(key = "branchen-hinweis") { Hinweis(S.trBranchenHinweis) }

        item(key = "yahoo-kopf") { SectionHeader(S.trYahoo) }
        val yl = yahoo
        when {
            yl == null -> item(key = "yahoo-laedt") { EmptyHint(S.trLaedt) }
            yl.isEmpty() -> item(key = "yahoo-leer") { EmptyHint(S.trKeineDaten) }
            else -> items(yl.take(if (alleYahoo) 30 else 10), key = { "y-" + it.symbol }) { q ->
                AssetRow(
                    symbol = q.symbol,
                    name = q.name.ifBlank { q.symbol },
                    subtitle = q.symbol + " · " + Fmt.price(q.price),
                    change = q.changePct,
                    changeText = Fmt.pct(abs(q.changePct)),
                    onClick = { onOpen(q.symbol) },
                )
            }
        }
        if (!alleYahoo && (yl?.size ?: 0) > 10) item(key = "yahoo-mehr") {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { FilterPill(S.trMehr) { alleYahoo = true } }
        }
        item(key = "yahoo-hinweis") { Hinweis(S.trYahooHinweis) }

        item(key = "reddit-kopf") {
            SectionHeader(S.trReddit)
            MitlaufendeReiter(
                filter.size, pager = redditPager, titel = { filter[it].second() },
                modifier = Modifier.padding(vertical = 4.dp),
            ) { i -> redditScope.launch { redditPager.animateScrollToPage(i) } }
            // Zeitraum der Erwaehnungen in der Zeitleiste wie beim Chart
            ZeitLeiste(REDDIT_ZEITEN.map { S.trRedditZeit(it) }, selected = redditZeit, onSelect = { redditZeit = it },
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        // Jede Zeile ein eigener Eintrag: aufgebaut wird nur, was ins Bild kommt. Beim Wischen folgen alle Zeilen demselben
        // Seitenzustand, die Nachbarseite steht daneben bereit.
        item(key = "reddit-stand") { RedditSynchron(redditPager) { q -> redditJe.getOrNull(q)?.let { RedditStand(it) } } }
        items(redditJe.maxOfOrNull { it.zeilen.size } ?: 0, key = { "reddit-$it" }) { k ->
            RedditSynchron(redditPager) { q -> redditJe.getOrNull(q)?.zeilen?.getOrNull(k)?.let { RedditZeile(it, onOpen) } }
        }
        if (redditJe.any { it.mehr }) item(key = "reddit-mehr") {
            RedditSynchron(redditPager) { q ->
                if (redditJe.getOrNull(q)?.mehr == true) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { FilterPill(S.trMehr) { redditAlle = true } }
            }
        }
        item(key = "reddit-hinweis") { Hinweis(S.trRedditHinweis + if (filter.size > REDDIT_FILTER.size) " " + S.trRedditDeHinweis else "") }
        item { Spacer(Modifier.height(40.dp)) }
    }
    }
}

// Zeitraeume der Branchen in der Schreibweise des Charts, in derselben Reihenfolge wie Branchentrends.ZEITRAEUME.
private val BRANCHEN_ZEITRAEUME = listOf(Range.D1, Range.W1, Range.M1, Range.M3, Range.M6, Range.Y1)

private class RedditDaten(val hinweis: String?, val leer: String?, val zeilen: List<RedditEintrag>, val mehr: Boolean)

private class RedditEintrag(
    val symbol: String,
    val name: String,
    val untertitel: String,
    val aenderung: Double?,
    val aenderungText: String?,
    val rechts: String?,
    val rechtsKlein: String,
    val balken: List<Int>?,
    val beschriftung: List<String?>,
    val bekanntAb: Int,
)

// Eine Quelle auf Reddit: bei 24 Stunden die Liste von ApeWisdom mit Rang und Veraenderung, sonst die Rangliste aus dem
// Verlauf mit Balken je Tag (Jahr: je Woche) und dem Tag, an dem der Wert am meisten besprochen wurde.
private fun redditDaten(quelle: String, tage: Int, liste: List<MarketApi.Erwaehnung>?, a: Trendverlauf.Archiv?, alle: Boolean, heute: Long): RedditDaten {
    val krypto = quelle == "all-crypto"
    // Kryptowerte stehen bei ApeWisdom als „BTC.X“, in der App als „BTC-EUR“
    val symbolFuer = { k: String -> if (krypto) Trendverlauf.normal(k) + "-EUR" else k }
    val zahl = if (alle) 50 else 15
    if (tage == 1) {
        if (liste == null) return RedditDaten(null, S.trLaedt, emptyList(), false)
        if (liste.isEmpty()) return RedditDaten(null, S.trRedditKeine, emptyList(), false)
        val zeilen = liste.take(zahl).map { e ->
            // Veraenderung der Erwaehnungen gegenueber vor 24 Stunden; ohne Vorwert steht der Wert neu in der Liste
            val vorher = e.erwaehnungenVorher?.takeIf { it > 0 }
            val delta = vorher?.let { (e.erwaehnungen.toDouble() / it - 1) * 100 }
            val kuerzel = Trendverlauf.normal(e.ticker)
            RedditEintrag(
                symbol = symbolFuer(e.ticker),
                name = e.name.ifBlank { kuerzel },
                untertitel = S.trRang(e.rang, rangPfeil(e.rang, e.rangVorher), kuerzel),
                aenderung = delta,
                aenderungText = delta?.let { Fmt.pct(abs(it), 0) },
                rechts = if (delta == null) S.trNeu else null,
                rechtsKlein = S.trErwaehnungen(e.erwaehnungen),
                balken = null, beschriftung = emptyList(), bekanntAb = 0,
            )
        }
        return RedditDaten(null, null, zeilen, liste.size > zahl)
    }
    if (a == null) return RedditDaten(null, S.trLaedt, emptyList(), false)
    val seit = Trendverlauf.seit(a, quelle)
    // Hinweis, solange der Verlauf kuerzer ist als der Zeitraum
    val hinweis = if (seit == null || heute - seit + 1 < tage) S.trVerlaufSeit(seit?.let { Fmt.date(it * TAG_MS) }) else null
    val rangliste = Trendverlauf.rangliste(a, quelle, tage, heute)
    if (rangliste.isEmpty()) return RedditDaten(hinweis, if (liste?.isEmpty() == true) S.trRedditKeine else S.trKeineDaten, emptyList(), false)
    val woche = tage > 60
    // Unbekannte Tage (vor dem ersten Abruf) nur als Strich
    val bekannt = (seit ?: heute).let { s -> if (woche) ((s - (heute - tage + 1)) / 7).toInt() else (s - (heute - tage + 1)).toInt() }.coerceAtLeast(0)
    var beschriftung: List<String?>? = null
    val zeilen = rangliste.take(zahl).mapIndexed { i, (k, summe) ->
        val spitze = Trendverlauf.spitze(a, quelle, k, tage, heute)
        val reihe = Trendverlauf.reihe(a, quelle, k, tage, heute)
        val balken = if (woche) Trendverlauf.buendeln(reihe, 7) else reihe
        val spitzeText = spitze?.let { (t, _) ->
            if (woche) S.trSpitzeWoche(Fmt.dayMonth(wochenAnfang(t, heute, tage) * TAG_MS)) else S.trSpitze(Fmt.dayMonth(t * TAG_MS))
        }
        val b = beschriftung ?: balkenBeschriftung(balken.size, heute, woche).also { beschriftung = it }
        RedditEintrag(
            symbol = symbolFuer(k),
            name = a.namen[k]?.ifBlank { null } ?: k,
            untertitel = listOfNotNull(S.trRang(i + 1, "", k), spitzeText).joinToString(" · "),
            aenderung = null, aenderungText = null,
            rechts = Fmt.num(summe.toDouble(), 0),
            rechtsKlein = S.trErwaehnungenKurz,
            balken = balken, beschriftung = b, bekanntAb = bekannt,
        )
    }
    return RedditDaten(hinweis, null, zeilen, rangliste.size > zahl)
}

@Composable
private fun RedditStand(d: RedditDaten) {
    Column(Modifier.fillMaxWidth()) {
        d.hinweis?.let { Hinweis(it) }
        d.leer?.let { EmptyHint(it) }
    }
}

// Ein Abschnitt der Reddit-Seiten: die aktuelle Seite und ihre Nachbarn nebeneinander, verschoben nach dem gemeinsamen
// Seitenzustand. Die Lage wird erst beim Platzieren gelesen, das Wischen baut also nichts neu auf. Horizontales Ziehen
// blaettert wie ein Pager; am ersten und letzten Filter geht der Rest an das Watchlist-Blatt.
@Composable
private fun RedditSynchron(pager: PagerState, inhalt: @Composable (Int) -> Unit) {
    val schwung = PagerDefaults.flingBehavior(pager)
    val umgekehrt = ScrollableDefaults.reverseDirection(LocalLayoutDirection.current, Orientation.Horizontal, false)
    val seite = pager.currentPage
    val seiten = (seite - 1..seite + 1).filter { it in 0 until pager.pageCount }
    Layout(
        content = { seiten.forEach { q -> key(q) { Box(Modifier.fillMaxWidth()) { inhalt(q) } } } },
        modifier = Modifier.fillMaxWidth().clipToBounds()
            .scrollable(pager, Orientation.Horizontal, reverseDirection = umgekehrt, flingBehavior = schwung),
    ) { teile, c ->
        val breite = c.maxWidth
        val gemessen = teile.map { it.measure(c.copy(minWidth = breite, maxWidth = breite, minHeight = 0)) }
        layout(breite, gemessen.maxOfOrNull { it.height } ?: 0) {
            val lage = pager.currentPage + pager.currentPageOffsetFraction
            gemessen.forEachIndexed { i, p -> p.placeRelative(((seiten[i] - lage) * breite).roundToInt(), 0) }
        }
    }
}

@Composable
private fun RedditZeile(e: RedditEintrag, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AssetRow(
            symbol = e.symbol,
            name = e.name,
            subtitle = e.untertitel,
            change = e.aenderung,
            changeText = e.aenderungText,
            rightPlain = e.rechts,
            rightCaption = e.rechtsKlein,
            onClick = { onOpen(e.symbol) },
        )
        e.balken?.let { b ->
            Erwaehnungsbalken(
                b, beschriftung = e.beschriftung, bekanntAb = e.bekanntAb,
                modifier = Modifier.padding(start = 70.dp, end = 20.dp).padding(bottom = 10.dp),
            )
        }
    }
}

// Erster Tag des Wochenblocks, in dem tag liegt (Bloecke wie in Trendverlauf.buendeln, der juengste endet heute).
private fun wochenAnfang(tag: Long, heute: Long, tage: Int): Long {
    val von = heute - tage + 1
    val rest = tage % 7
    if (rest > 0 && tag < von + rest) return von
    return von + rest + (tag - von - rest) / 7 * 7
}

// Beschriftung unter den Balken: bei bis zu sieben Tagen jeder Wochentag, bei 30 oder 60 Tagen jeder siebte
// bzw. vierzehnte Tag vom juengsten aus, beim Jahr (Wochen) Januar, April, Juli und Oktober. heute: juengster Tag.
internal fun balkenBeschriftung(anzahl: Int, heute: Long, wochen: Boolean = false): List<String?> {
    val sprache = de.troetenrepublik.app.i18n.I18n.locale
    fun datum(tag: Long) = java.time.LocalDate.ofEpochDay(tag)
    if (wochen) {
        // Bloecke wie in Trendverlauf.buendeln: der juengste endet heute
        val start = heute - anzahl * 7L + 1
        var vorher = -1
        return List(anzahl) { j ->
            val d = datum(start + j * 7L)
            val m = d.monthValue
            val text = if (m != vorher && m in setOf(1, 4, 7, 10) && vorher != -1) {
                d.month.getDisplayName(java.time.format.TextStyle.SHORT, sprache).removeSuffix(".")
            } else null
            vorher = m
            text
        }
    }
    if (anzahl <= 7) {
        return List(anzahl) { i ->
            datum(heute - anzahl + 1 + i).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, sprache).removeSuffix(".")
        }
    }
    val abstand = if (anzahl > 40) 14 else 7
    return List(anzahl) { i ->
        val zurueck = anzahl - 1 - i
        if (zurueck % abstand == 0) Fmt.dayMonth((heute - zurueck) * TAG_MS).removeSuffix(".") else null
    }
}

// Erwaehnungen als Balken, aeltester links, darunter beschriftung (Tage oder Monate, siehe balkenBeschriftung).
// Der hoechste Balken ist hervorgehoben und traegt seine Zahl; Werte vor bekanntAb sind unbekannt (die App schrieb
// noch nicht mit) und nur als feine Grundlinie gezeichnet.
@Composable
internal fun Erwaehnungsbalken(werte: List<Int>, beschriftung: List<String?>, modifier: Modifier = Modifier, bekanntAb: Int = 0) {
    if (werte.isEmpty()) return
    val hoch = MaterialTheme.colorScheme.primary
    val normal = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val leer = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val klein = MaterialTheme.typography.labelSmall
    val messer = rememberTextMeasurer()
    val max = werte.max()
    val spitze = if (max > 0) werte.indexOf(max) else -1
    // Eigene Ebene: beim Scrollen wird die fertige Grafik verschoben statt neu gezeichnet
    Canvas(modifier.fillMaxWidth().height(60.dp).graphicsLayer()) {
        val n = werte.size
        val oben = 14.dp.toPx()
        val unten = 16.dp.toPx()
        val flaeche = size.height - oben - unten
        val platz = size.width / n
        val breite = (platz * 0.62f).coerceAtMost(12.dp.toPx()).coerceAtLeast(1f)
        val grund = 1.dp.toPx()
        werte.forEachIndexed { i, w ->
            val mitte = platz * i + platz / 2
            val bekannt = i >= bekanntAb && w > 0
            val h = if (bekannt) (flaeche * w / max).coerceAtLeast(2.dp.toPx()) else grund
            drawRoundRect(
                if (!bekannt) leer else if (i == spitze) hoch else normal,
                Offset(mitte - breite / 2, oben + flaeche - h), Size(breite, h), CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            fun text(s: String, y: Float, farbe: Color) {
                val m = messer.measure(s, klein.copy(color = farbe))
                val x = (mitte - m.size.width / 2).coerceIn(0f, (size.width - m.size.width).coerceAtLeast(0f))
                drawText(m, topLeft = Offset(x, y))
            }
            if (i == spitze) text(Fmt.num(w.toDouble(), 0), oben + flaeche - h - 13.dp.toPx(), hoch)
            beschriftung.getOrNull(i)?.let { text(it, size.height - unten + 2.dp.toPx(), schrift) }
        }
    }
}

private const val TAG_MS = 86_400_000L

@Stable
class TrendsLage {
    var branchenZeitraum by mutableIntStateOf(1)
    var alleYahoo by mutableStateOf(false)
    var redditSeite by mutableIntStateOf(0)
    var redditZeit by mutableIntStateOf(0)
    var index by mutableIntStateOf(0)
    var versatz by mutableIntStateOf(0)
}

// Zeitraeume fuer Reddit: 24 Stunden (live), Woche, Monat, Jahr (52 Wochen).
internal val REDDIT_ZEITEN = listOf(1, 7, 30, 364)

// Filter fuer ApeWisdom: alle Aktien-Subreddits, nur r/wallstreetbets, Krypto.
internal val REDDIT_FILTER: List<Pair<String, () -> String>> = listOf(
    "all-stocks" to { S.trFilterAktien },
    "wallstreetbets" to { "r/wallstreetbets" },
    "all-crypto" to { S.trFilterKrypto },
)

// Die Filter fuer die Trends; mit eigenem Reddit-Zugang dazu die deutschen Subreddits, gezaehlt von der App.
internal fun redditFilter(deutsch: Boolean): List<Pair<String, () -> String>> =
    if (deutsch) REDDIT_FILTER + (de.troetenrepublik.app.engine.RedditAuswertung.QUELLE to { S.trFilterDeutsch }) else REDDIT_FILTER

@Composable
private fun Hinweis(text: String) {
    AufklappText(text, Modifier.padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 14.dp))
}

// Rangaenderung als Pfeil: ↑3 heisst drei Plaetze besser als vor 24 Stunden.
internal fun rangPfeil(rang: Int, vorher: Int?): String = when {
    vorher == null || vorher <= 0 -> ""
    vorher > rang -> " ↑" + (vorher - rang)
    vorher < rang -> " ↓" + (rang - vorher)
    else -> " ="
}
