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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
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
import de.troetenrepublik.app.engine.Branchentrends
import de.troetenrepublik.app.engine.Trendverlauf
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import de.troetenrepublik.app.engine.Verlaufsdarstellung
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.layout.widthIn

// Reiter „Trends“ der Seite Maerkte: Branchen und Themen in einer Liste (TrendListe.kt) mit Verlauf je Branche,
// Sektorrotation, „Beschleunigt sich selbst“, was bei Yahoo Finance gefragt ist und welche Werte auf Reddit und 4chan /biz/ am meisten erwaehnt
// werden (ApeWisdom). Woche, Monat und Jahr fuer die Erwaehnungen kommen aus dem Verlauf, den die App mitschreibt.
@Composable
internal fun TrendsSeite(
    state: AppState,
    onOpen: (String) -> Unit,
    // Auswahl und Scrolllage der Seite.
    lage: TrendsLage = remember { TrendsLage() },
    // Nur die echte Seite schreibt ihre Scrolllage zurueck, das Bild darunter nicht.
    merken: Boolean = true,
    // Fear & Greed und Fruehwarnzeichen oben; auf der Seite Maerkte stehen sie in einem eigenen Reiter
    mitStimmung: Boolean = true,
    // Ziel von aussen: „durchbrueche“ (Anfang der Liste Branchen und Themen) oder „durchbrueche:thema“ (Zeile des Themas)
    startAbschnitt: String? = null,
) {
    var alleYahoo by lage::alleYahoo
    var yahoo by remember { mutableStateOf<List<Quote>?>(null) }
    // Reddit: alle Quellen auf einmal, damit beim Wischen die Nachbarseite schon da ist
    var reddit by remember { mutableStateOf<Map<String, List<MarketApi.Erwaehnung>>>(emptyMap()) }
    var archiv by remember { mutableStateOf<Trendverlauf.Archiv?>(null) }
    // Mit eigenem Reddit-Zugang kommen die deutschen Subreddits als weitere Seite dazu
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    val filter = remember(state.redditAktiv(einstellungen)) { redditFilter(state.redditAktiv(einstellungen)) }
    var rotation by remember { mutableStateOf<Branchentrends.Rotation?>(null) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { yahoo = state.yahooTrends() }
            launch { rotation = runCatching { state.sektorRotation() }.getOrNull() }
        }
    }
    // Branchen und Themen: ein Zeitraum wie beim Chart (1T bis 1J) fuer Branchen und Koerbe
    var branchenZeitraum by lage::branchenZeitraum
    val trend = rememberTrendListe(state, branchenZeitraum)
    val trendZustand = remember { TrendListeZustand() }
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

    // Reddit-Quellen als Seiten eines Pagers wie im Copytrading: die ganze Seite folgt dem Finger, die Pille gleitet
    // mit. Die gemerkte Seite nur zum Start lesen.
    val redditPager = rememberPagerState(initialPage = Snapshot.withoutReadObservation { lage.redditSeite }.coerceIn(0, filter.size - 1)) { filter.size }
    val redditScope = rememberCoroutineScope()
    if (merken) LaunchedEffect(redditPager) {
        snapshotFlow { redditPager.currentPage }.collect { lage.redditSeite = it }
    }
    var redditZeit by lage::redditZeit
    var redditAlle by remember(redditZeit) { mutableStateOf(false) }
    val heute = Trendverlauf.tag(System.currentTimeMillis())
    val redditJe = remember(filter, reddit, archiv, redditZeit, redditAlle, heute) {
        filter.map { (q, _) -> redditDaten(q, REDDIT_ZEITEN[redditZeit], reddit[q], archiv, redditAlle, heute, if (q == de.troetenrepublik.app.engine.RedditAuswertung.QUELLE) { sym, name -> state.redditDeStunden(sym, name) } else null, state.redditDeAbdeckung) }
    }
    // Startlage ohne Beobachtung lesen, sonst baut jeder Scrollschritt die ganze Seite neu auf.
    val liste = rememberListeAb({ lage.index }, { lage.versatz })
    if (merken && startAbschnitt != null) LaunchedEffect(trend.geladen) {
        if (trend.geladen) {
            val thema = startAbschnitt.substringAfter(':', "").ifBlank { null }
            // „reddit“: Kopf der Reddit-Liste (Verweis aus der Stimmung)
            liste.zuSchluessel(if (startAbschnitt == "reddit") "reddit-kopf" else thema?.let { trendZustand.zuThema(it) } ?: TREND_KOPF)
        }
    }
    if (merken) LaunchedEffect(liste) {
        snapshotFlow { liste.firstVisibleItemIndex to liste.firstVisibleItemScrollOffset }.collect { (i, v) -> lage.index = i; lage.versatz = v }
    }

    Box(Modifier.fillMaxSize()) {
    // Ohne Hoehe: misst nur die Seitenbreite fuer den gemeinsamen Zustand der Reddit-Zeilen und bleibt immer aufgebaut,
    // auch wenn der Kopf der Reddit-Liste aus dem Bild gescrollt ist
    HorizontalPager(state = redditPager, modifier = Modifier.fillMaxWidth().height(0.dp), userScrollEnabled = false) { }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        // Fear & Greed und Reddit-Stimmung
        if (mitStimmung) item(key = "stimmung") { StimmungsAbschnitt(state, onOpen) }
        trendListe(state, trend, trendZustand, branchenZeitraum, { branchenZeitraum = it }, onOpen)
        rotation?.let { r -> item(key = "rotation") { RotationKarte(r) } }
        item(key = "beschleunigt") { BeschleunigtAbschnitt(state, trend.inhalt) }

        item(key = "yahoo-kopf") { SectionHeader(S.trYahoo) }
        val yl = yahoo
        when {
            yl == null -> item(key = "yahoo-laedt") { EmptyHint(S.trLaedt) }
            yl.isEmpty() -> item(key = "yahoo-leer") { EmptyHint(S.heatmapLeer) }
            else -> items(yl.take(if (alleYahoo) yl.size else 10), key = { "y-" + it.symbol }) { q ->
                AssetRow(
                    state = state,
                    symbol = q.symbol,
                    name = q.name.ifBlank { q.symbol },
                    subtitle = q.symbol + " · " + Fmt.price(q.price),
                    change = q.changePct,
                    changeText = Fmt.pct(abs(q.changePct)),
                    onClick = { onOpen(q.symbol) },
                )
            }
        }
        if ((yl?.size ?: 0) > 10) item(key = "yahoo-mehr") { ListenPfeil(alleYahoo, label = if (alleYahoo) null else S.bbAlle(yl?.size ?: 0)) { alleYahoo = !alleYahoo } }

        item(key = "reddit-kopf") {
            // Methodik hinter dem Info-Symbol; sichtbar bleibt nur, was nicht erreicht wurde (siehe unten)
            val deMethodik = if (filter.size > REDDIT_FILTER.size) " " + S.trRedditDeHinweis + (state.redditDeAbdeckung?.let { " " + S.trRedditDeAbdeckung(it) } ?: "") else ""
            MarktInfoKopf(S.trReddit, S.trRedditHinweis + deMethodik)
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
            RedditSynchron(redditPager) { q -> redditJe.getOrNull(q)?.zeilen?.getOrNull(k)?.let { RedditZeile(state, it, onOpen) } }
        }
        // Pfeil, solange die Liste gekuerzt ist oder lang aufgeklappt steht
        val redditPfeil: (RedditDaten?) -> Boolean = { d -> d != null && (d.mehr || (redditAlle && d.zeilen.size > REDDIT_KURZ)) }
        if (redditJe.any(redditPfeil)) item(key = "reddit-mehr") {
            RedditSynchron(redditPager) { q ->
                if (redditPfeil(redditJe.getOrNull(q))) ListenPfeil(redditAlle, label = if (redditAlle) null else S.bbAlle(redditJe.getOrNull(q)?.gesamt ?: 0)) { redditAlle = !redditAlle }
            }
        }
        // Fehlende Subreddits der deutschen Zaehlung stehen erst nach dem Abruf fest
        item(key = "reddit-hinweis") {
            if (filter.size > REDDIT_FILTER.size) state.redditDeFehlt.takeIf { it.isNotEmpty() }?.let { fehlt ->
                Hinweis(S.trRedditDeFehlt(fehlt.joinToString(", ") { sub -> "r/$sub" }))
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    }
    TrendListeBlaetter(trend, trendZustand)
}

// Reddit-Zeilen in der gekuerzten Liste
private const val REDDIT_KURZ = 15

private class RedditDaten(val hinweis: String?, val leer: String?, val zeilen: List<RedditEintrag>, val mehr: Boolean, val gesamt: Int = 0)

private class RedditEintrag(
    val symbol: String,
    val name: String,
    val untertitel: String,
    val rangAenderung: Int = 0,
    val aenderung: Double?,
    val aenderungText: String?,
    val rechts: String?,
    val rechtsKlein: String,
    val balken: List<Int>?,
    val beschriftung: List<String?>,
    val bekanntAb: Int,
    // Zeitachse der Balken: Beginn des ersten Balkens, Breite eines Balkens (Millisekunden) und Zeitraum in Tagen fuer den Kurs
    val balkenStart: Long = 0L,
    val balkenBreite: Long = TAG_MS,
    val kursTage: Int = 1,
)

// Eine Quelle auf Reddit: bei 24 Stunden die Liste von ApeWisdom mit Rang und Veraenderung, sonst die Rangliste aus dem
// Verlauf mit Balken je Tag (Jahr: je Woche) und dem Tag, an dem der Wert am meisten besprochen wurde.
private fun redditDaten(
    quelle: String, tage: Int, liste: List<MarketApi.Erwaehnung>?, a: Trendverlauf.Archiv?, alle: Boolean, heute: Long,
    stunden: ((String, String) -> List<Int>?)? = null, abdeckung: Int? = null,
): RedditDaten {
    val krypto = quelle == "all-crypto"
    // Kryptowerte stehen bei ApeWisdom als „BTC.X“, in der App als „BTC-EUR“; 4chan mischt Aktien und Krypto
    val symbolFuer = { k: String -> if (krypto || k.trim().uppercase().endsWith(".X")) Trendverlauf.normal(k) + "-EUR" else k }
    val zahl = if (alle) 50 else REDDIT_KURZ
    val jetztMs = System.currentTimeMillis()
    if (tage == 1) {
        if (liste == null) return RedditDaten(null, S.trLaedt, emptyList(), false)
        if (liste.isEmpty()) return RedditDaten(null, S.trRedditKeine, emptyList(), false)
        val zeilen = liste.take(zahl).map { e ->
            // Veraenderung der Erwaehnungen gegenueber vor 24 Stunden; „neu“ nur bei bekanntem Vorwert 0, ohne Vorwert nichts
            val vorher = e.erwaehnungenVorher?.takeIf { it > 0 }
            val delta = vorher?.let { (e.erwaehnungen.toDouble() / it - 1) * 100 }
            val kuerzel = Trendverlauf.normal(e.ticker)
            RedditEintrag(
                symbol = symbolFuer(e.ticker),
                name = e.name.ifBlank { kuerzel },
                untertitel = S.trRang(e.rang, kuerzel),
                rangAenderung = e.rangVorher?.takeIf { it > 0 }?.let { it - e.rang } ?: 0,
                aenderung = delta,
                aenderungText = delta?.let { Fmt.pct(abs(it), 0) },
                rechts = if (e.erwaehnungenVorher == 0) S.copyNew else null,
                rechtsKlein = S.trErwaehnungen(e.erwaehnungen),
                // Erwaehnungen je Stunde aus den gelesenen Beitraegen; wo die Beitraege nicht 24 Stunden decken, sind die
                // frueheren Stunden unbekannt
                balken = stunden?.invoke(e.ticker, e.name)?.takeIf { l -> l.any { it > 0 } }, beschriftung = stundenBeschriftung(),
                bekanntAb = (24 - (abdeckung ?: 24)).coerceAtLeast(0),
                balkenStart = jetztMs - 24 * 3_600_000L, balkenBreite = 3_600_000L, kursTage = 1,
            )
        }
        return RedditDaten(null, null, zeilen, liste.size > zahl, minOf(liste.size, 50))
    }
    if (a == null) return RedditDaten(null, S.trLaedt, emptyList(), false)
    val seit = Trendverlauf.seit(a, quelle)
    // Hinweis, solange der Verlauf kuerzer ist als der Zeitraum
    val hinweis = if (seit == null || heute - seit + 1 < tage) S.trVerlaufSeit(seit?.let { Fmt.date(it * TAG_MS) }) else null
    val rangliste = Trendverlauf.rangliste(a, quelle, tage, heute)
    if (rangliste.isEmpty()) return RedditDaten(hinweis, if (liste?.isEmpty() == true) S.trRedditKeine else S.heatmapLeer, emptyList(), false)
    val woche = tage > 60
    // Unbekannte Tage (vor dem ersten Abruf) nur als Strich
    val bekannt = (seit ?: heute).let { s -> if (woche) ((s - (heute - tage + 1)) / 7).toInt() else (s - (heute - tage + 1)).toInt() }.coerceAtLeast(0)
    var beschriftung: List<String?>? = null
    val zeilen = rangliste.take(zahl).mapIndexed { i, (k, summe) ->
        val delta = de.troetenrepublik.app.engine.RedditVorperiode.aenderung(a, quelle, k, tage, heute)
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
            untertitel = listOfNotNull(S.trRang(i + 1, k), spitzeText).joinToString(" · "),
            aenderung = delta, aenderungText = delta?.let { Fmt.pct(abs(it), 0) },
            // Mit Aenderung zur Vorperiode steht sie rechts, die Zahl der Erwaehnungen darunter
            rechts = if (delta != null) null else Fmt.num(summe.toDouble(), 0),
            rechtsKlein = if (delta != null) S.trErwaehnungen(summe) else S.trErwaehnungenKurz,
            balken = balken, beschriftung = b, bekanntAb = bekannt,
            balkenStart = (heute - balken.size * (if (woche) 7L else 1L) + 1) * TAG_MS, balkenBreite = (if (woche) 7L else 1L) * TAG_MS, kursTage = tage,
        )
    }
    return RedditDaten(hinweis, null, zeilen, rangliste.size > zahl, minOf(rangliste.size, 50))
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
// blaettert wie ein Pager; am ersten und letzten Filter geht der Rest an die Seite.
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
private fun RedditZeile(state: AppState, e: RedditEintrag, onOpen: (String) -> Unit) {
    // Zeilen mit Balken klappen auf (Kurs und Erwaehnungen auf einer Zeitachse); ohne Balken oeffnet der Tipp den Wert
    var offen by remember(e.symbol, e.kursTage) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        AssetRow(
            symbol = e.symbol,
            name = e.name,
            subtitle = e.untertitel,
            subtitleRang = e.rangAenderung,
            change = e.aenderung,
            changeText = e.aenderungText,
            rightPlain = e.rechts,
            rightCaption = e.rechtsKlein,
            onClick = { if (e.balken == null) onOpen(e.symbol) else offen = !offen },
        )
        e.balken?.let { b ->
            if (offen) KursUndErwaehnungen(state, e, b) { onOpen(e.symbol) }
            else Erwaehnungsbalken(
                b, beschriftung = e.beschriftung, bekanntAb = e.bekanntAb,
                modifier = Modifier.padding(start = 70.dp, end = 20.dp).padding(bottom = 10.dp),
            )
        }
    }
}

// Aufgeklappte Zeile eines Werts: Erwaehnungen als Balken und der Kurs als Linie auf einer gemeinsamen Zeitachse, damit
// man sieht, ob das Gerede vor oder nach der Kursbewegung kam. Der Kurs wird erst beim Aufklappen geladen (eine Abfrage
// ueber den zwischengespeicherten Kursweg); bis dahin stehen die Balken allein. Tippen oder Ziehen liest Zeit, Kurs und
// Erwaehnungen der Stelle ab.
@Composable
private fun KursUndErwaehnungen(state: AppState, e: RedditEintrag, balken: List<Int>, onOpen: () -> Unit) {
    val start = e.balkenStart
    val ende = start + balken.size * e.balkenBreite
    val range = remember(e.kursTage) { Verlaufsdarstellung.rangeFuer(if (e.kursTage <= 1) 2 else e.kursTage) }
    var kerzen by remember(e.symbol, range) { mutableStateOf<List<Candle>?>(null) }
    LaunchedEffect(e.symbol, range) {
        val k = runCatching { state.candles(e.symbol, range) }.getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        kerzen = k
    }
    val kurs = remember(kerzen, start, ende) {
        kerzen.orEmpty().filter { it.t in start..ende && it.close.isFinite() && it.close > 0 }.sortedBy { it.t }.map { it.t to it.close }
    }
    val stunden = e.balkenBreite < TAG_MS
    val zeitText = { t: Long -> if (stunden) Fmt.time(t) else Fmt.dayMonth(t) }
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val balkenFarbe = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    // Kurslinie nach Richtung ueber den ganzen Zeitraum
    val signalFarben = LocalSignal.current
    val linie = when {
        kurs.size < 2 -> MaterialTheme.colorScheme.onSurface
        kurs.last().second > kurs.first().second -> signalFarben.up
        kurs.last().second < kurs.first().second -> signalFarben.down
        else -> MaterialTheme.colorScheme.onSurface
    }
    val klein = MaterialTheme.typography.labelSmall
    val messer = rememberTextMeasurer()
    var gewaehlt by remember(kurs, balken) { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth().padding(start = 70.dp, end = 20.dp).padding(bottom = 8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(110.dp).fingerLesen(start, ende) { p, groesse ->
                gewaehlt = p?.let { (it.x / groesse.width).coerceIn(0f, 1f) }
            },
        ) {
            val oben = 16.dp.toPx()
            val hoehe = size.height - oben
            fun x(t: Long) = (t - start).toFloat() / (ende - start) * size.width
            val max = balken.maxOrNull()?.coerceAtLeast(1) ?: 1
            val platz = size.width / balken.size
            val breite = (platz * 0.7f).coerceAtMost(14.dp.toPx())
            balken.forEachIndexed { i, w ->
                if (i < e.bekanntAb || w <= 0) return@forEachIndexed
                val h = (hoehe * 0.42f * w / max).coerceAtLeast(2.dp.toPx())
                drawRoundRect(balkenFarbe, Offset(platz * i + platz / 2 - breite / 2, size.height - h), Size(breite, h), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            }
            if (kurs.size >= 2) {
                val lo = kurs.minOf { it.second }
                val hi = kurs.maxOf { it.second }.let { if (it - lo < 1e-9) lo + 1 else it }
                // Der Kurs bekommt die oberen drei Fuenftel, damit er die Balken nicht ueberdeckt
                fun y(v: Double) = oben + hoehe * 0.55f * (1f - ((v - lo) / (hi - lo)).toFloat())
                val pfad = Path()
                kurs.forEachIndexed { i, (t, v) -> if (i == 0) pfad.moveTo(x(t), y(v)) else pfad.lineTo(x(t), y(v)) }
                drawPath(pfad, linie, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            gewaehlt?.let { f ->
                val t = start + (f * (ende - start)).toLong()
                val px = f * size.width
                drawLine(schrift, Offset(px, oben), Offset(px, size.height), strokeWidth = 1f)
                val ki = Verlaufsdarstellung.naechster(kurs.map { it.first }, t)
                val kw = ki?.let { kurs[it].second }
                val n = Verlaufsdarstellung.korbVon(t, start, e.balkenBreite, balken.size)?.let { balken[it] } ?: 0
                val text = S.trKursLese(zeitText(t), kw?.let { Fmt.euro(it, 2) } ?: "–", n)
                val m = messer.measure(text, klein.copy(color = schrift))
                drawText(m, topLeft = Offset((px - m.size.width / 2).coerceIn(0f, (size.width - m.size.width).coerceAtLeast(0f)), 0f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(zeitText(start), style = klein, color = schrift, modifier = Modifier.weight(1f))
            if (kerzen == null) Text(S.trLaedt, style = klein, color = schrift)
            else Text(zeitText(ende - 1), style = klein, color = schrift)
        }
        Text(S.trZumWert, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(0.dp, onClick = onOpen).padding(vertical = 8.dp))
    }
}

// Erster Tag des Wochenblocks, in dem tag liegt (Bloecke wie in Trendverlauf.buendeln, der juengste endet heute).
private fun wochenAnfang(tag: Long, heute: Long, tage: Int): Long {
    val von = heute - tage + 1
    val rest = tage % 7
    if (rest > 0 && tag < von + rest) return von
    return von + rest + (tag - von - rest) / 7 * 7
}

// Beschriftung unter den 24 Stundenbalken: jede sechste Stunde mit der Uhrzeit, der juengste Balken endet jetzt.
private fun stundenBeschriftung(jetzt: Long = System.currentTimeMillis()): List<String?> = List(24) { i ->
    if (i % 6 == 0) Fmt.time(jetzt - (24 - i) * 3_600_000L).substringBefore(':') else null
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
    val normal = hoch.copy(alpha = 0.35f)
    val leer = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val klein = MaterialTheme.typography.labelSmall
    val messer = rememberTextMeasurer()
    val max = werte.max()
    val spitze = if (max > 0) werte.indexOf(max) else -1
    // Eigene Ebene: beim Scrollen wird die fertige Grafik verschoben statt neu gezeichnet
    Canvas(modifier.fillMaxWidth().height(66.dp).graphicsLayer()) {
        val n = werte.size
        // Oben Platz fuer die Spitzenzahl, damit sie nicht in die Zeilen darueber ragt (5.94)
        val oben = 18.dp.toPx()
        val unten = 16.dp.toPx()
        val flaeche = size.height - oben - unten
        // Wenige Werte (etwa sieben Tage) stehen in fester Breite links, statt ueber die ganze Zeile verteilt
        val platz = minOf(size.width / n, 34.dp.toPx())
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
            if (i == spitze) text(Fmt.num(w.toDouble(), 0), (oben + flaeche - h - 16.dp.toPx()).coerceAtLeast(0f), hoch)
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

// Filter fuer ApeWisdom: alle Aktien-Subreddits, nur r/wallstreetbets, Krypto, 4chan /biz/ (seit 5.92).
internal val REDDIT_FILTER: List<Pair<String, () -> String>> = listOf(
    "all-stocks" to { S.stocks },
    "wallstreetbets" to { "r/wallstreetbets" },
    "all-crypto" to { S.crypto },
    "4chan" to { "4chan /biz/" },
)

// Die Filter fuer die Trends; mit eigenem Reddit-Zugang dazu die deutschen Subreddits, gezaehlt von der App.
internal fun redditFilter(deutsch: Boolean): List<Pair<String, () -> String>> =
    if (deutsch) REDDIT_FILTER + (de.troetenrepublik.app.engine.RedditAuswertung.QUELLE to { S.trFilterDeutsch }) else REDDIT_FILTER

@Composable
private fun Hinweis(text: String) {
    AufklappText(text, Modifier.padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 14.dp))
}

// Verlauf einer Branche als Depot (seit 5.92): was aus 1.000 Euro im gewaehlten Zeitraum geworden waere, als Linie.
// Die Aufloesung folgt dem Zeitraum (Tag 2 Minuten, Woche 15 Minuten, Monat 30 Minuten, laenger taeglich); beim Tag gilt der
// Vortagesschluss als Bezug. Ein Tipp auf die Zeile darunter oeffnet den ETF.
@Composable
internal fun BranchenDepot(
    state: AppState, symbol: String, taeglich: List<Candle>, tage: Int, fondsgroesse: Double?,
    einzug: PaddingValues = PaddingValues(start = 70.dp, end = 20.dp), onEtf: () -> Unit,
) {
    val range = remember(tage) { Verlaufsdarstellung.rangeFuer(tage) }
    var serie by remember(symbol, range) { mutableStateOf<List<Candle>?>(null) }
    LaunchedEffect(symbol, range) {
        val k = runCatching { state.candles(symbol, range) }.getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        serie = k
    }
    val verlauf = remember(serie, taeglich, tage) {
        val k = serie
        if (k != null && k.size >= 2) {
            // Derselbe Bezugskurs wie die Prozentzahl der Zeile, sonst widerspricht „Aus 1.000 € waeren …“ der Zeile
            val bezug = Branchentrends.bezugsVerhaeltnis(taeglich, tage)?.let { k.maxBy { c -> c.t }.close * it }
            Verlaufsdarstellung.verlauf(k, Verlaufsdarstellung.beginn(k, tage), bezug)
        } else Branchentrends.depot(taeglich, tage)
    }
    Column(Modifier.fillMaxWidth().padding(einzug).padding(bottom = 8.dp)) {
        if (serie == null) {
            Text(S.trLaedt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (verlauf.size >= 2) {
            val zeit = { t: Long -> if (tage <= 1) Fmt.time(t) else if (tage <= 31) Fmt.dayMonth(t) + " " + Fmt.time(t) else Fmt.date(t) }
            VerlaufFrei(verlauf, 1000.0, wertText = { Fmt.euro(it, 2) }, zeitText = zeit, ohneLuecken = true)
            Row(Modifier.fillMaxWidth()) {
                Text(zeit(verlauf.first().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(zeit(verlauf.last().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // ETF hinter der Branche: Kuerzel und Fondsgroesse
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
            fondsgroesse?.let {
                Text(S.stMarktwert + " " + Fmt.dollarKompakt(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
        Text(S.trZumEtf, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(0.dp, onClick = onEtf).padding(vertical = 8.dp))
    }
}

// Sektorrotation (seit 5.92): aus welchen Branchen Geld abfliesst, in welche es fliesst, und der vermutete Grund.
@Composable
private fun RotationKarte(r: Branchentrends.Rotation) {
    val deutsch = I18n.lang == Lang.DE
    fun namen(l: List<Branchentrends.Branche>) = l.joinToString(", ") { if (deutsch) it.de else it.en }
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.rotTitel, S.rotInfo)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        ) {
            if (r.nach.isEmpty() && r.aus.isEmpty()) {
                Text(S.rotKeine, style = MaterialTheme.typography.bodyMedium)
            } else {
                // Kernaussage sind die zwei Zeilen; der vermutete Grund steht klein darunter
                if (r.aus.isNotEmpty()) RotationZeile(S.rotAus, namen(r.aus), LocalSignal.current.down, oben = false)
                if (r.nach.isNotEmpty()) RotationZeile(S.rotNach, namen(r.nach), LocalSignal.current.up, oben = r.aus.isNotEmpty())
                if (r.grund != Branchentrends.Grund.UNKLAR) {
                    Text(S.rotGrund(r.grund.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// Beschriftung mit festem Abstand zu den Branchen, die Branchen brechen in ihrer eigenen Spalte um.
@Composable
private fun RotationZeile(titel: String, namen: String, farbe: Color, oben: Boolean = true) {
    Row(Modifier.padding(top = if (oben) 8.dp else 0.dp)) {
        Text(titel, style = MaterialTheme.typography.labelMedium, color = farbe, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline().widthIn(min = 56.dp))
        Spacer(Modifier.width(16.dp))
        Text(namen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.alignByBaseline().weight(1f))
    }
}
