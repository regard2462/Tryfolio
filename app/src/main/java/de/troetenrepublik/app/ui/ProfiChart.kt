package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.troetenrepublik.app.market.StandRegel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.ChartIndikatoren
import de.troetenrepublik.app.engine.ChartLayout
import de.troetenrepublik.app.engine.ChartSprung
import de.troetenrepublik.app.engine.ChartZeichnungen
import de.troetenrepublik.app.engine.EigeneMarken
import de.troetenrepublik.app.engine.Indikatoren
import de.troetenrepublik.app.engine.LivePunkt
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Zeichnung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

// Farben der Indikatorlinien (Platz wie in ChartIndikatoren.Linie.farbe), in beiden Designs mit mindestens 3:1.
// Platz 9 und 10 sind Auf- und Abwaertslinien (+DI/-DI, Aroon) und stehen in den Signalfarben.
private val LINIEN_GRUND = listOf(
    Color(0xFFB7791F), Color(0xFF2F7DE1), Color(0xFF9F57E8), Color(0xFF0E8A8A), Color(0xFFD9468F), Color(0xFF0891B2),
    Color(0xFF7C8A00), Color(0xFFD97706), Color(0xFF6B7280),
)

private fun linienFarbe(i: Int, signal: Signal): Color = when (i) {
    9 -> signal.up
    10 -> signal.down
    else -> LINIEN_GRUND.getOrElse(i) { LINIEN_GRUND[0] }
}

// Farben der Vergleichswerte nach dem eigenen, der in der Farbe der Kurslinie steht.
private val VERGLEICH_FARBEN = Kategorie.farben

// Marken, die man auf den Chart setzen kann (gezeichnet, auf jedem Gerät gleich). Gespeicherte Zeichnungen aus früheren
// Fassungen tragen noch ein Zeichen und werden einer Marke zugeordnet.
private val MARKEN = listOf("hoch", "runter", "stern", "flagge", "ring")
private val MARKEN_ALT = mapOf(
    "\uD83D\uDE80" to "hoch", "\uD83D\uDCC8" to "hoch", "\uD83D\uDC02" to "hoch", "\u2705" to "hoch", "\uD83C\uDFAF" to "hoch",
    "\uD83D\uDCC9" to "runter", "\uD83D\uDC3B" to "runter", "\u274C" to "runter",
    "\u2B50" to "stern", "\uD83D\uDC8E" to "stern", "\uD83D\uDCB0" to "stern", "\uD83D\uDD25" to "stern",
    "\uD83D\uDCCC" to "flagge", "\uD83D\uDC40" to "ring", "\uD83E\uDD14" to "ring",
)
private fun markeVon(text: String): String = if (text in MARKEN) text else MARKEN_ALT[text] ?: "ring"

private fun markenFarbe(id: String, signal: Signal, gelb: Color): Color = when (id) {
    "hoch" -> signal.up
    "runter" -> signal.down
    "flagge" -> Kategorie.farben[0]
    else -> gelb
}

// Zeichnet eine Marke um den Mittelpunkt c mit dem Halbmaß r.
private fun DrawScope.markeZeichnen(id: String, c: Offset, r: Float, farbe: Color) {
    when (id) {
        "hoch", "runter" -> {
            val breite = r * 2.2f
            richtungsDreieck(c.x - breite / 2f, c.y - breite * 0.7778f / 2f, breite, id == "hoch", farbe)
        }
        "stern" -> drawPath(Path().apply {
            for (i in 0 until 10) {
                val w = kotlin.math.PI / 5 * i - kotlin.math.PI / 2
                val rr = if (i % 2 == 0) r * 1.15f else r * 0.5f
                val x = c.x + (kotlin.math.cos(w) * rr).toFloat(); val y = c.y + (kotlin.math.sin(w) * rr).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }, farbe)
        "flagge" -> {
            val x = c.x - r * 0.55f
            drawLine(farbe, Offset(x, c.y + r), Offset(x, c.y - r), strokeWidth = r * 0.22f)
            drawPath(Path().apply { moveTo(x, c.y - r); lineTo(c.x + r, c.y - r * 0.45f); lineTo(x, c.y + r * 0.1f); close() }, farbe)
        }
        else -> {
            drawCircle(farbe, r, c, style = Stroke(width = r * 0.3f))
            drawCircle(farbe, r * 0.28f, c)
        }
    }
}

// Breite der Achsenspalte rechts von Kurs und Indikatorfeldern, damit alle Felder dieselbe Zeitachse haben.
private val ACHSENSPALTE = 60.dp

// Hoechstens so viele Werte im Vergleich, der eigene eingeschlossen.
private const val VERGLEICH_MAX = 8

private enum class Werkzeug(val key: String) { KEINES(""), LINIE(ChartZeichnungen.LINIE), WAAGERECHT(ChartZeichnungen.WAAGERECHT), NOTIZ(ChartZeichnungen.NOTIZ), EMOJI(ChartZeichnungen.EMOJI) }

// Erweiterter Chart: Linie, Kerzen oder Heikin-Ashi an jedem Handelsplatz, den Yahoo fuer den Wert kennt.
// Vergleich in Prozent seit Beginn des Zeitraums, Zeichnungen werden je Wert gemerkt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfiChartScreen(state: AppState, symbol: String, onBack: () -> Unit, sprung: ChartSprung.Muster? = null) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    val fxKurse by state.market.fxKurse.collectAsStateWithLifecycle()
    val markenAn by state.chartEigeneMarken.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signal = LocalSignal.current
    val name = kurzName(symbol, quotes[symbol]?.name.orEmpty())

    var range by rememberSaveable { mutableStateOf(Range.M3) }
    // Mit einem Kerzenmuster (Sprung aus der Indikator-Ansicht) startet der Chart auf dessen Zeitraum in Kerzen
    var eigenVon by rememberSaveable { mutableLongStateOf(sprung?.let { ChartSprung.zeitraum(it).first } ?: 0L) }
    var eigenBis by rememberSaveable { mutableLongStateOf(sprung?.let { ChartSprung.zeitraum(it).second } ?: 0L) }
    var kerzenSprung by rememberSaveable { mutableStateOf(sprung != null) }
    var zeitraumWahl by remember { mutableStateOf(false) }
    val eigen = eigenVon > 0
    // Handelsplatz: das Symbol der gewaehlten Notierung; Vergleich: weitere Symbole, durch Komma getrennt
    var platz by rememberSaveable(symbol) { mutableStateOf(symbol) }
    var platzName by rememberSaveable(symbol) { mutableStateOf("") }
    var vergleichText by rememberSaveable(symbol) { mutableStateOf("") }
    val vergleich = vergleichText.split(',').filter { it.isNotBlank() }
    val art = if (kerzenSprung) "kerzen" else ChartLayout.art(settings.chartArtWahl)
    val aktiv = settings.chartIndikatoren.split(',').mapNotNull { ChartIndikatoren.Indikator.von(it.trim()) }.toSet()

    var werkzeug by remember { mutableStateOf(Werkzeug.KEINES) }
    var linienStart by remember { mutableStateOf<Pair<Long, Double>?>(null) }
    var emoji by remember { mutableStateOf(MARKEN.first()) }
    var notizOrt by remember { mutableStateOf<Pair<Long, Double>?>(null) }
    var allesLoeschen by remember { mutableStateOf(false) }
    var blatt by remember { mutableStateOf("") }   // "", "indikatoren", "platz", "vergleich", "emoji"
    var scrub by remember { mutableStateOf<Int?>(null) }

    var reihe by remember(platz) { mutableStateOf<Series?>(null) }
    // Zuerst der gespeicherte Stand (mit Zeitangabe, wenn er aelter als die Frist ist), dann der Abruf; „Keine
    // Kursdaten“ erst nach einem Abruf ohne Daten
    var reiheStand by remember(platz) { mutableStateOf<Long?>(null) }
    LaunchedEffect(platz, range, eigenVon, eigenBis) {
        reihe = null; scrub = null; reiheStand = null
        val gespeichert = if (eigenVon > 0) null else state.market.serieGespeichert(platz, range)
        StandRegel.laden(gespeichert, { System.currentTimeMillis() }, {
            (if (eigenVon > 0) state.zeitraum(platz, eigenVon, eigenBis) else state.series(platz, range)).takeIf { it.candles.isNotEmpty() }
        }) { a ->
            if (a.wert != null) reihe = a.wert else if (!a.laedt) reihe = Series(emptyList())
            reiheStand = if (a.alt) a.abgerufen else null
        }
    }
    // Tages- und Wochenchart laufen mit wie auf der Wertpapierseite (60 s bzw. 5 min, nur im Vordergrund und solange
    // der Platz handelt); dazwischen fuehrt der Kurs des Platzes die letzte Kerze fort
    val platzKurs = quotes[platz]
    val reiheLaeuft by rememberUpdatedState(state.marketOpen(platz, null) || System.currentTimeMillis() - (platzKurs?.marketTime ?: 0L) < 20 * 60_000L)
    LaunchedEffect(platz, range, eigenVon) {
        if (eigenVon > 0 || !range.intraday) return@LaunchedEffect
        while (true) {
            delay(if (range == Range.D1) 60_000L else 5 * 60_000L)
            if (!reiheLaeuft || !state.imVordergrund) continue
            runCatching { state.series(platz, range) }.getOrNull()?.takeIf { it.candles.isNotEmpty() }?.let { reihe = it; reiheStand = null }
        }
    }
    var vergleichsReihen by remember { mutableStateOf<Map<String, Series>>(emptyMap()) }
    LaunchedEffect(vergleichText, range, eigenVon, eigenBis) {
        vergleichsReihen = coroutineScope {
            vergleich.map { s ->
                async { s to runCatching { if (eigenVon > 0) state.zeitraum(s, eigenVon, eigenBis) else state.series(s, range) }.getOrDefault(Series(emptyList())) }
            }.awaitAll().toMap()
        }
    }

    val alleZeichnungen = remember(settings.chartZeichnungen) { ChartZeichnungen.lesen(settings.chartZeichnungen) }
    // Zeichnungen gehoeren zum genauen Kuerzel der Notierung (Preisachse und Waehrung des Handelsplatzes)
    val zeichnungen = alleZeichnungen[platz].orEmpty()
    fun zeichnungenSetzen(neu: List<Zeichnung>) {
        val text = ChartZeichnungen.schreiben(ChartZeichnungen.setzen(alleZeichnungen, platz, neu))
        scope.launch { state.settings.update { it.copy(chartZeichnungen = text) } }
    }
    fun indikatorUmschalten(i: ChartIndikatoren.Indikator) {
        val neu = if (i in aktiv) aktiv - i else aktiv + i
        val text = ChartIndikatoren.Indikator.entries.filter { it in neu }.joinToString(",") { it.key }
        scope.launch { state.settings.update { it.copy(chartIndikatoren = text) } }
    }

    // Kerzen brauchen Platz (5.93): in Zeitraeumen mit Minutenkerzen wird mehr als eine Kerze je 5 dp Breite zu
    // breiteren Kerzen zusammengefasst, sonst bleiben nur haarduenne Striche. Tageskerzen bleiben, damit lange
    // Indikatoren wie der 200-Tage-Schnitt stimmen. Die Linie zeigt weiter jeden Kurs.
    var chartBreitePx by remember { mutableStateOf(0) }
    val chartBreiteDp = with(LocalDensity.current) { chartBreitePx.toDp().value }.takeIf { it > 0f } ?: 360f
    val kerzenMax = ((chartBreiteDp - 60f) / 5f).toInt().coerceAtLeast(40)
    val kerzen = remember(reihe, platzKurs, art, kerzenMax, range, eigen) {
        val roh = reihe?.let { r -> if (range == Range.D1 && !eigen) LivePunkt.anhaengen(r, platzKurs, System.currentTimeMillis()) else r.candles }.orEmpty()
        if (art == "linie" || !range.intraday || eigen) roh else kerzenBuendeln(roh, kerzenMax)
    }
    // Der laufende Kurs haengt nur hinten an: das Fadenkreuz bleibt stehen, solange es eine Kerze trifft
    LaunchedEffect(art, kerzenMax) { scrub = null }
    LaunchedEffect(kerzen.size) { if ((scrub ?: -1) >= kerzen.size) scrub = null }
    val anzeige = remember(kerzen, art) { if (art == "heikin") Indikatoren.heikinAshi(kerzen) else kerzen }
    val tagVon = remember { { t: Long -> java.time.Instant.ofEpochMilli(t).atZone(MarketHours.zone).toLocalDate().toEpochDay() } }
    // Eigene Geschaefte als Marken, Einstand, Orders, Alarme und KO-Schwellen als Linien (seit 5.91); die Linien nur am
    // Heimatplatz des Werts, an anderen Plaetzen gelten andere Kurse
    val eigene = remember(account, symbol, fxKurse) { EigeneMarken.fuer(account, symbol, fxKurse, tagVon) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val eigeneLinien = if (!markenAn || platz != symbol) emptyList() else eigene.linien.map { l ->
        val farbe = when (l.art) {
            EigeneMarken.LinienArt.EINSTAND -> grau
            EigeneMarken.LinienArt.LIMIT -> Kategorie.farbe(2)
            EigeneMarken.LinienArt.ALARM -> signal.warn
            EigeneMarken.LinienArt.STOPP, EigeneMarken.LinienArt.KO -> signal.down
        }
        KursLinie(l.preis, S.kcLinie(l.art.name) + " " + preisText(l.preis), farbe, S.kcLinie(l.art.name))
    }
    val eigeneMarken = if (!markenAn) emptyList() else eigene.marken.map { m ->
        when (m.art) {
            EigeneMarken.Art.KAUF -> KursMarke(m.t, signal.up, 1, m.kurs)
            EigeneMarken.Art.VERKAUF -> KursMarke(m.t, signal.down, -1, m.kurs)
            EigeneMarken.Art.DIVIDENDE -> KursMarke(m.t, grau, 0)
        }
    }
    val ergebnisse = remember(kerzen, aktiv) { aktiv.associateWith { ChartIndikatoren.rechnen(it, kerzen, tagVon) } }
    // Golden und Death Cross als Raute auf dem Kurs, sobald beide Durchschnitte eingeblendet sind
    val kreuzMarken = remember(kerzen, ergebnisse) {
        val a = ergebnisse[ChartIndikatoren.Indikator.SMA50]?.linien?.firstOrNull()?.werte
        val b = ergebnisse[ChartIndikatoren.Indikator.SMA200]?.linien?.firstOrNull()?.werte
        if (a == null || b == null) emptyList() else Indikatoren.alleKreuzungen(a, b).mapNotNull { (i, auf) ->
            kerzen.getOrNull(i)?.let { KursMarke(it.t, if (auf) signal.up else signal.down, KursMarke.KREUZ) }
        }
    }
    // Rahmen um die Kerzen des Musters aus dem Sprung, nur am Heimatplatz des Werts
    val musterRahmen = if (sprung == null || platz != symbol) null else MusterRahmen(S.kzName(sprung.name), sprung.vonTag, sprung.bisTag)
    val vergleichAn = vergleich.isNotEmpty()
    val prozent = remember(kerzen, vergleichsReihen, vergleichText) {
        if (!vergleichAn || kerzen.isEmpty()) null else {
            val basis = kerzen.first().close
            listOf(kerzen.map { if (basis > 0) (it.close / basis - 1) * 100 else null }) + vergleich.map { s -> prozentAuf(kerzen, vergleichsReihen[s]) }
        }
    }

    val liste = rememberScrollState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }
            .verticalScroll(liste),
    ) {
        ScreenHeader(name, listOfNotNull(S.chartProfi, platzName.ifBlank { null }).joinToString(" · "), onBack)

        // Kurs am Fadenkreuz oder zuletzt: Eroeffnung, Hoch, Tief, Schluss und Veraenderung im Zeitraum
        val gezeigt = scrub?.let { anzeige.getOrNull(it) } ?: anzeige.lastOrNull()
        // Tagesansicht am Heimatplatz: Veraenderung zum Vortagesschluss wie auf der Wertpapierseite, sonst zur ersten Kerze
        val vortag = if (range == Range.D1 && !eigen && platz == symbol) quotes[symbol]?.prevClose?.takeIf { it > 0 } else null
        val erste = vortag ?: kerzen.firstOrNull()?.close ?: 0.0
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (gezeigt != null) {
                val roh = scrub?.let { kerzen.getOrNull(it) } ?: kerzen.last()
                Column(Modifier.weight(1f)) {
                    Text(Fmt.priceEuro(roh.close), style = MaterialTheme.typography.headlineSmall, maxLines = 1, softWrap = false)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (erste > 0) Text(
                        Fmt.signedPct((roh.close / erste - 1) * 100), style = MaterialTheme.typography.titleSmall,
                        color = if (roh.close >= erste) signal.up else signal.down,
                    )
                    Text(
                        if (eigen && scrub == null) zeitraumText(eigenVon, eigenBis) else if (range.intraday || eigen) Fmt.dateTime(roh.t) else Fmt.date(roh.t),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }

        // Anfang, Hoch, Tief, Ende in vier gleich breiten Spalten, nichts bricht um
        if (gezeigt != null) {
            val roh = scrub?.let { kerzen.getOrNull(it) } ?: kerzen.last()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                S.pcOhlc.zip(listOf(roh.open, roh.high, roh.low, roh.close)).forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                        Text(preisText(v), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
        // Eigene Geschaefte des Tags und die Linien mit ihren Namen beim Entlangfahren; die Zeilen sind reserviert,
        // solange es Marken oder Linien gibt, damit der Chart nicht springt
        val tagMarken = scrub?.takeIf { markenAn && kerzen.size >= 2 }?.let { i ->
            val luecke = kerzen[1].t - kerzen[0].t
            eigene.marken.filter { m -> m.t >= kerzen.first().t - luecke && m.t <= kerzen.last().t + luecke && nearestIndex(kerzen, m.t) == i }
        }.orEmpty()
        if (markenAn && eigene.marken.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp)) {
            // Jedes Geschaeft in eigener Zeile, damit nichts umbricht oder gekuerzt wird
            if (tagMarken.isEmpty()) Text(" ", style = MaterialTheme.typography.bodySmall)
            tagMarken.forEach {
                // Zahl und Einheit bleiben zusammen, umgebrochen wird nur zwischen den Wörtern
                Text(
                    S.kcMarke(it.art.name, Fmt.qty(it.stueck).replace(' ', '\u00A0'), Fmt.priceEuro(it.betrag).replace(' ', '\u00A0'), Fmt.priceEuro(it.kurs).replace(' ', '\u00A0')),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (eigeneLinien.isNotEmpty()) LinienNamen(if (scrub == null) null else eigene.linien.zip(eigeneLinien).map { (l, kl) -> S.kcLinie(l.art.name) to kl.farbe })

        RangeSelector(
            ranges = Range.entries, selected = if (eigen) null else range,
            onSelect = { range = it; eigenVon = 0L; eigenBis = 0L },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            trailing = { ZeitraumKnopf(aktiv = eigen) { zeitraumWahl = true } },
        )

        Box(Modifier.fillMaxWidth().height(340.dp).padding(start = 8.dp).onSizeChanged { chartBreitePx = it.width }, contentAlignment = Alignment.Center) {
            when {
                reihe == null -> CircularProgressIndicator()
                kerzen.size < 2 -> Text(S.chartKeineDaten, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> KursFlaeche(
                    kerzen = kerzen, anzeige = anzeige, art = art,
                    ueberlagerung = ergebnisse.filterKeys { it.ort == ChartIndikatoren.Ort.KURS }.values.flatMap { it.linien },
                    prozent = prozent, eigeneFarbe = signal.line,
                    zeichnungen = if (vergleichAn) emptyList() else zeichnungen, entwurf = linienStart,
                    eigeneLinien = eigeneLinien, eigeneMarken = eigeneMarken + kreuzMarken, musterRahmen = musterRahmen,
                    scrub = scrub, onScrub = { scrub = it },
                    onTipp = { t, p ->
                        // Im Vergleich (Prozent statt Kurs) wird nicht gezeichnet
                        when (if (vergleichAn) Werkzeug.KEINES else werkzeug) {
                            Werkzeug.KEINES -> scrub = null
                            Werkzeug.LINIE -> {
                                val start = linienStart
                                if (start == null) linienStart = t to p else {
                                    zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.LINIE, start.first, start.second, t, p))
                                    linienStart = null; werkzeug = Werkzeug.KEINES
                                }
                            }
                            Werkzeug.WAAGERECHT -> { zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.WAAGERECHT, t, p)); werkzeug = Werkzeug.KEINES }
                            Werkzeug.NOTIZ -> notizOrt = t to p
                            Werkzeug.EMOJI -> { zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.EMOJI, t, p, text = emoji)); werkzeug = Werkzeug.KEINES }
                        }
                    },
                    onLang = { i -> zeichnungenSetzen(zeichnungen.filterIndexed { k, _ -> k != i }) },
                    zeichnen = !vergleichAn && werkzeug != Werkzeug.KEINES,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            reiheStand?.let {
                Text(standText(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp))
            }
        }

        if (prozent == null && ChartIndikatoren.Indikator.TREND in aktiv) TrendZeile(kerzen)

        // Vergleich: Legende mit der Veraenderung jedes Werts
        if (prozent != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                (listOf(platz) + vergleich).forEachIndexed { i, s ->
                    val farbe = if (i == 0) signal.line else VERGLEICH_FARBEN[(i - 1) % VERGLEICH_FARBEN.size]
                    val letzter = prozent.getOrNull(i)?.lastOrNull { it != null }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(farbe))
                        Spacer(Modifier.width(8.dp))
                        NamenText(kurzName(s, quotes[s]?.name.orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(letzter?.let { Fmt.signedPct(it) } ?: "–", style = MaterialTheme.typography.bodyMedium)
                        if (i > 0) Box(
                            Modifier.padding(start = 4.dp).size(32.dp).clip(CircleShape).clickable { vergleichText = (vergleich - s).joinToString(",") },
                            contentAlignment = Alignment.Center,
                        ) {
                            val grau = MaterialTheme.colorScheme.onSurfaceVariant
                            Canvas(Modifier.size(12.dp)) {
                                val st = 1.8.dp.toPx()
                                drawLine(grau, Offset(0f, 0f), Offset(this.size.width, this.size.height), st, androidx.compose.ui.graphics.StrokeCap.Round)
                                drawLine(grau, Offset(this.size.width, 0f), Offset(0f, this.size.height), st, androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }

        if (kerzen.size >= 2) {
            ChartIndikatoren.Indikator.entries.filter { it.ort == ChartIndikatoren.Ort.UNTEN && it in aktiv }.forEach { i ->
                val e = ergebnisse[i] ?: return@forEach
                UnterFeld(S.chartIndikatorName(i.key), e, kerzen, scrub, Modifier.fillMaxWidth().height(114.dp).padding(start = 8.dp, top = 10.dp))
            }
        }

        // Die Knoepfe in drei beschrifteten Gruppen: Darstellung, Einblenden, Zeichnen (5.92)
        Zwischenkopf(S.pcDarstellung, Modifier.padding(top = 6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("kerzen", "linie", "heikin").forEach { a ->
                FilterPill(S.chartArt(a), active = art == a) { kerzenSprung = false; scope.launch { state.settings.update { it.copy(chartArtWahl = a) } } }
            }
        }
        Zwischenkopf(S.pcEinblenden)
        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(S.indButton, trailing = aktiv.size.takeIf { it > 0 }?.toString(), chevron = true) { blatt = "indikatoren" }
            FilterPill(S.venue, trailing = platzName.ifBlank { null }, chevron = true) { blatt = "platz" }
            FilterPill(S.chartVergleich, trailing = vergleich.size.takeIf { it > 0 }?.let { (it + 1).toString() }, chevron = true) { blatt = "vergleich" }
        }

        if (!vergleichAn) {
            Zwischenkopf(S.pcZeichnen)
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(Werkzeug.LINIE, Werkzeug.WAAGERECHT, Werkzeug.NOTIZ).forEach { w ->
                    FilterPill(S.pcWerkzeug(w.key), active = werkzeug == w) {
                        werkzeug = if (werkzeug == w) Werkzeug.KEINES else w; linienStart = null
                    }
                }
                FilterPill(S.chartWerkzeug(ChartZeichnungen.EMOJI), active = werkzeug == Werkzeug.EMOJI) { blatt = "emoji" }
                if (zeichnungen.isNotEmpty()) {
                    FilterPill(S.chartWerkzeug("zurueck")) { zeichnungenSetzen(zeichnungen.dropLast(1)) }
                    FilterPill(S.chartWerkzeug("loeschen")) { allesLoeschen = true }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (zeitraumWahl) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { zeitraumWahl = false }) { von, bis ->
        eigenVon = minOf(von, bis); eigenBis = maxOf(von, bis); zeitraumWahl = false
    }
    notizOrt?.let { (t, p) ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { notizOrt = null },
            title = { Text(S.notiz) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(80) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), shape = RoundedCornerShape(Radius.field),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.NOTIZ, t, p, text = text.trim()))
                    notizOrt = null; werkzeug = Werkzeug.KEINES
                }) { Text(S.save) }
            },
            dismissButton = { TextButton(onClick = { notizOrt = null }) { Text(S.cancel) } },
        )
    }
    if (allesLoeschen) AlertDialog(
        onDismissRequest = { allesLoeschen = false },
        text = { Text(S.chartAllesLoeschen(name)) },
        confirmButton = { TextButton(onClick = { zeichnungenSetzen(emptyList()); allesLoeschen = false }) { Text(S.delete) } },
        dismissButton = { TextButton(onClick = { allesLoeschen = false }) { Text(S.cancel) } },
    )
    if (blatt.isNotEmpty()) ModalBottomSheet(
        onDismissRequest = { blatt = "" },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        when (blatt) {
            "indikatoren" -> IndikatorWahl(aktiv, ::indikatorUmschalten)
            "platz" -> PlatzWahl(state, symbol, platz) { p -> platz = p.symbol; platzName = if (p.symbol == symbol) "" else p.boerse; blatt = "" }
            "vergleich" -> VergleichWahl(state, listOf(platz) + vergleich) { s ->
                if (s !in vergleich && s != platz) {
                    if (vergleich.size + 1 >= VERGLEICH_MAX) state.showToast(S.chartVergleichVoll) else vergleichText = (vergleich + s).joinToString(",")
                }
                blatt = ""
            }
            else -> EmojiWahl { e -> emoji = e; werkzeug = Werkzeug.EMOJI; linienStart = null; blatt = "" }
        }
    }
}

// Werte einer anderen Reihe auf die Zeitpunkte von kerzen gelegt, in Prozent seit ihrem ersten Wert im Zeitraum.
// Namen der eigenen Linien mit Farbpunkt; jeder Name rutscht als Ganzes in die nächste Zeile. Ohne Namen (kein Finger
// auf dem Chart) bleibt die Zeile als Platzhalter stehen, damit der Chart nicht springt.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LinienNamen(namen: List<Pair<String, Color>>?) {
    if (namen == null) { Text(" ", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp)); return }
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        namen.forEach { (n, f) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(f))
                Spacer(Modifier.width(6.dp))
                Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun prozentAuf(kerzen: List<Candle>, andere: Series?): List<Double?> {
    val k = andere?.candles.orEmpty()
    if (k.isEmpty()) return kerzen.map { null }
    val nachZeit = TreeMap<Long, Double>().apply { k.forEach { put(it.t, it.close) } }
    val werte = kerzen.map { c -> nachZeit.floorEntry(c.t)?.value }
    val basis = werte.firstOrNull { it != null && it > 0 } ?: return kerzen.map { null }
    return werte.map { v -> v?.let { (it / basis - 1) * 100 } }
}

// Kurs fuer die Achse und die Kopfzeile: mehr Stellen bei kleinen Kursen.
private fun preisText(v: Double): String = Fmt.num(v, when { abs(v) >= 1000 -> 0; abs(v) >= 1 -> 2; else -> 4 })

// Runde Schrittweite fuer etwa anzahl Hilfslinien zwischen lo und hi.
private fun schritt(lo: Double, hi: Double, anzahl: Int = 4): Double {
    val roh = (hi - lo) / anzahl
    if (!(roh > 0)) return 1.0
    val zehner = 10.0.pow(floor(log10(roh)))
    val f = roh / zehner
    return zehner * when { f < 1.5 -> 1.0; f < 2.25 -> 2.0; f < 3.5 -> 2.5; f < 7.5 -> 5.0; else -> 10.0 }
}

// Je g aufeinanderfolgende Kerzen werden eine: Eroeffnung der ersten, Schluss der letzten, Hoch und Tief aller, Umsatz
// zusammen, Zeit der ersten. g so, dass hoechstens max Kerzen bleiben.
internal fun kerzenBuendeln(kerzen: List<Candle>, max: Int): List<Candle> {
    if (max < 1 || kerzen.size <= max) return kerzen
    val g = (kerzen.size + max - 1) / max
    return kerzen.chunked(g) { b ->
        Candle(b.first().t, b.first().open, b.maxOf { it.high }, b.minOf { it.low }, b.last().close, b.sumOf { it.volume })
    }
}

// Kerzen in gleichem Abstand, Luecken wie Nacht und Wochenende fallen weg, die Zeit dazwischen gleitet.
private class Achse(val kerzen: List<Candle>, val breite: Float) {
    val n = kerzen.size
    val schritt = if (n > 0) breite / n else breite
    fun x(i: Int): Float = (i + 0.5f) * schritt
    fun index(x: Float): Int = (x / schritt).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))
    fun xVon(t: Long): Float {
        if (n == 0) return 0f
        if (n == 1) return x(0)
        if (t <= kerzen[0].t) return x(0) + (t - kerzen[0].t).toFloat() / (kerzen[1].t - kerzen[0].t).coerceAtLeast(1) * schritt
        if (t >= kerzen[n - 1].t) return x(n - 1) + (t - kerzen[n - 1].t).toFloat() / (kerzen[n - 1].t - kerzen[n - 2].t).coerceAtLeast(1) * schritt
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) { val m = (lo + hi) ushr 1; if (kerzen[m].t <= t) lo = m else hi = m }
        return x(lo) + (t - kerzen[lo].t).toFloat() / (kerzen[hi].t - kerzen[lo].t).coerceAtLeast(1) * schritt
    }
}

private class Skala(val lo: Double, val hi: Double, val oben: Float, val hoehe: Float) {
    fun y(v: Double): Float = oben + ((hi - v) / (hi - lo)).toFloat() * hoehe
    fun wert(y: Float): Double = hi - (y - oben) / hoehe * (hi - lo)
}

// Spanne aller Werte mit etwas Luft oben und unten.
private fun spanne(werte: Sequence<Double>, luft: Double = 0.06): Pair<Double, Double> {
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (v in werte) if (v.isFinite()) { if (v < lo) lo = v; if (v > hi) hi = v }
    if (lo > hi) return 0.0 to 1.0
    if (hi - lo < 1e-9) { val m = abs(hi).coerceAtLeast(1.0) * 0.01; return lo - m to hi + m }
    val d = (hi - lo) * luft
    return lo - d to hi + d
}

private fun DrawScope.linie(werte: List<Double?>, achse: Achse, skala: Skala, farbe: Color, breite: Float) {
    val pfad = Path()
    var offen = false
    werte.forEachIndexed { i, v ->
        if (v == null || !v.isFinite()) { offen = false; return@forEachIndexed }
        val x = achse.x(i); val y = skala.y(v)
        if (offen) pfad.lineTo(x, y) else { pfad.moveTo(x, y); offen = true }
    }
    drawPath(pfad, farbe, style = Stroke(width = breite))
}

// Hilfslinien mit Achsenzahlen rechts. Eine Zahl weicht, wenn eine Marke (ausweichY, Hoehe ausweichH) ihre Zeile
// beruehrt, und bleibt innerhalb der Gesamtbreite.
private fun DrawScope.hilfslinien(skala: Skala, breite: Float, messer: TextMeasurer, farbe: Color, ausweichY: List<Float>, ausweichH: Float, text: (Double) -> String) {
    val st = schritt(skala.lo, skala.hi)
    var v = kotlin.math.ceil(skala.lo / st) * st
    val stil = TextStyle(fontSize = 10.sp)
    val werte = ArrayList<Double>()
    while (v <= skala.hi) { werte += v; v += st }
    val texte = werte.map { messer.measure(text(it), stil) }
    val hoehe = texte.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
    val sichtbar = ChartLayout.achsenSichtbar(werte.map { skala.y(it) }, hoehe, ausweichY, ausweichH)
    werte.forEachIndexed { k, wert ->
        val y = skala.y(wert)
        drawLine(farbe.copy(alpha = 0.18f), Offset(0f, y), Offset(breite, y), strokeWidth = 1f)
        if (!sichtbar[k]) return@forEachIndexed
        val t = texte[k]
        drawText(t, color = farbe, topLeft = Offset(ChartLayout.achsenTextX(breite, size.width, t.size.width.toFloat(), 4 * density, 2 * density), y - t.size.height / 2f))
    }
}

// Marke in der Achsenspalte auf Hoehe einer waagerechten Linie: Wert (und Name, wenn er daneben passt) auf Flaechenfarbe.
// pfeil: 1 = Linie liegt oberhalb der Skala, -1 = unterhalb, 0 = im Bild.
private class AchsenMarke(val y: Float, val wert: String, val name: String, val farbe: Color, val pfeil: Int)

@Composable
private fun KursFlaeche(
    kerzen: List<Candle>,
    anzeige: List<Candle>,
    art: String,
    ueberlagerung: List<ChartIndikatoren.Linie>,
    prozent: List<List<Double?>>?,
    eigeneFarbe: Color,
    zeichnungen: List<Zeichnung>,
    entwurf: Pair<Long, Double>?,
    eigeneLinien: List<KursLinie>,
    eigeneMarken: List<KursMarke>,
    musterRahmen: MusterRahmen?,
    scrub: Int?,
    onScrub: (Int?) -> Unit,
    onTipp: (Long, Double) -> Unit,
    onLang: (Int) -> Unit,
    zeichnen: Boolean,
    modifier: Modifier,
) {
    val signal = LocalSignal.current
    val messer = rememberTextMeasurer()
    val achsenFarbe = MaterialTheme.colorScheme.onSurfaceVariant
    val notizGrund = MaterialTheme.colorScheme.surfaceContainerHighest
    val notizSchrift = MaterialTheme.colorScheme.onSurface
    val flaechenFarbe = MaterialTheme.colorScheme.background
    val dichte = LocalDensity.current
    val tipp by rememberUpdatedState(onTipp)
    val lang by rememberUpdatedState(onLang)
    val wisch by rememberUpdatedState(onScrub)
    val aktuelleZeichnungen by rememberUpdatedState(zeichnungen)
    BoxWithConstraints(modifier) {
        val rechts = with(dichte) { ACHSENSPALTE.toPx() }
        val unten = with(dichte) { 18.dp.toPx() }
        val breite = (constraints.maxWidth - rechts).coerceAtLeast(1f)
        val hoehe = (constraints.maxHeight - unten).coerceAtLeast(1f)
        val achse = remember(kerzen, breite) { Achse(kerzen, breite) }
        val skala = remember(anzeige, ueberlagerung, prozent, zeichnungen, art, hoehe, eigeneLinien) {
            val werte = if (prozent != null) prozent.asSequence().flatten().filterNotNull()
            else (if (art == "linie") anzeige.asSequence().map { it.close } else anzeige.asSequence().flatMap { sequenceOf(it.high, it.low) }) +
                ueberlagerung.asSequence().flatMap { it.werte.asSequence() }.filterNotNull()
            val (lo, hi) = spanne(werte)
            // Eigene Linien bis einen Kursbereich ausserhalb ziehen die Skala mit, weitere stehen als Hinweis am Rand
            val weite = hi - lo
            val nah = if (prozent != null) emptyList() else eigeneLinien.map { it.preis }.filter { it >= lo - weite && it <= hi + weite }
            val (lo2, hi2) = if (nah.isEmpty()) lo to hi else spanne(sequenceOf(lo, hi) + nah.asSequence(), 0.03)
            Skala(lo2, hi2, 0f, hoehe)
        }
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(achse, skala, zeichnen) {
                    detectTapGestures(
                        onTap = { p -> if (p.x <= breite && p.y <= hoehe) tipp(kerzen[achse.index(p.x)].t, skala.wert(p.y)) },
                        onLongPress = { p ->
                            // Naechste Zeichnung im Umkreis von 28 dp entfernen
                            val grenze = 28 * density
                            var beste = -1
                            var abstand = Float.MAX_VALUE
                            aktuelleZeichnungen.forEachIndexed { i, z ->
                                val d = when (z.art) {
                                    ChartZeichnungen.WAAGERECHT -> abs(p.y - skala.y(z.p1))
                                    ChartZeichnungen.LINIE -> strecke(p, Offset(achse.xVon(z.t1), skala.y(z.p1)), Offset(achse.xVon(z.t2), skala.y(z.p2)))
                                    else -> (p - Offset(achse.xVon(z.t1), skala.y(z.p1))).getDistance()
                                }
                                if (d < abstand) { abstand = d; beste = i }
                            }
                            if (beste >= 0 && abstand <= grenze) lang(beste)
                        },
                    )
                }
                .pointerInput(achse, zeichnen) {
                    if (zeichnen) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { wisch(achse.index(it.x)) },
                        onDragEnd = { wisch(null) },
                        onDragCancel = { wisch(null) },
                    ) { change, _ -> wisch(achse.index(change.position.x)) }
                },
        ) {
            val gelb = LINIEN_GRUND[0]
            val markenStil = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            val markenListe = ArrayList<AchsenMarke>()
            if (prozent == null) {
                eigeneLinien.forEach { l ->
                    val pfeil = if (l.preis > skala.hi) 1 else if (l.preis < skala.lo) -1 else 0
                    markenListe += AchsenMarke(skala.y(l.preis).coerceIn(0f, hoehe), preisText(l.preis), l.name, l.farbe, pfeil)
                }
                zeichnungen.filter { it.art == ChartZeichnungen.WAAGERECHT }.forEach { z ->
                    markenListe += AchsenMarke(skala.y(z.p1).coerceIn(0f, hoehe), preisText(z.p1), "", gelb, 0)
                }
            }
            val markenTexte = markenListe.map { messer.measure(it.wert, markenStil) }
            val markenH = (markenTexte.maxOfOrNull { it.size.height } ?: 0) + 4 * density
            val markenY = ChartLayout.markenMitten(markenListe.map { it.y }, markenH, hoehe)
            hilfslinien(skala, breite, messer, achsenFarbe, markenY, markenH) { v -> if (prozent != null) Fmt.signedPct(v, if (abs(skala.hi - skala.lo) < 10) 1 else 0) else preisText(v) }
            clipRect(0f, 0f, breite, hoehe) {
                if (prozent != null) {
                    if (skala.lo < 0 && skala.hi > 0) drawLine(achsenFarbe.copy(alpha = 0.5f), Offset(0f, skala.y(0.0)), Offset(breite, skala.y(0.0)), 1.5f)
                    prozent.forEachIndexed { i, werte ->
                        linie(werte, achse, skala, if (i == 0) eigeneFarbe else VERGLEICH_FARBEN[(i - 1) % VERGLEICH_FARBEN.size], (if (i == 0) 2.5f else 2f) * density)
                    }
                } else {
                    if (art == "linie") linie(anzeige.map { it.close }, achse, skala, eigeneFarbe, 2.5f * density)
                    else {
                        anzeige.forEachIndexed { i, c ->
                            val farbe = if (c.close >= c.open) signal.up else signal.down
                            val sp = ChartLayout.spalte(achse.x(i), achse.schritt, density)
                            val o = skala.y(maxOf(c.open, c.close))
                            val u = o + ChartLayout.koerperHoehe(o, skala.y(minOf(c.open, c.close)), density)
                            val docht = sp.dochtBreite
                            // Steigende Kerzen hohl, sobald der Koerper breit genug ist: Richtung auch ohne Farbunterscheidung
                            if (c.close > c.open && sp.breite >= 4f * density && u - o >= 2f * docht) {
                                drawRect(farbe, Offset(sp.dochtLinks, skala.y(c.high)), Size(docht, o - skala.y(c.high)))
                                drawRect(farbe, Offset(sp.dochtLinks, u), Size(docht, skala.y(c.low) - u))
                                drawRect(farbe, Offset(sp.links + docht / 2, o + docht / 2), Size(sp.breite - docht, u - o - docht), style = Stroke(docht))
                            } else {
                                drawRect(farbe, Offset(sp.dochtLinks, skala.y(c.high)), Size(docht, maxOf(skala.y(c.low) - skala.y(c.high), 1f)))
                                drawRect(farbe, Offset(sp.links, o), Size(sp.breite, u - o))
                            }
                        }
                    }
                    ueberlagerung.forEach { l ->
                        val farbe = linienFarbe(l.farbe, signal)
                        if (l.art == ChartIndikatoren.Art.PUNKTE) l.werte.forEachIndexed { i, v ->
                            if (v != null) drawCircle(farbe, 1.8f * density, Offset(achse.x(i), skala.y(v)))
                        } else linie(l.werte, achse, skala, farbe.copy(alpha = 0.9f), 1.5f * density)
                    }
                    zeichnungen.forEach { z ->
                        when (z.art) {
                            ChartZeichnungen.LINIE -> {
                                val a = Offset(achse.xVon(z.t1), skala.y(z.p1)); val b = Offset(achse.xVon(z.t2), skala.y(z.p2))
                                drawLine(gelb, a, b, strokeWidth = 2f * density)
                                drawCircle(gelb, 3f * density, a); drawCircle(gelb, 3f * density, b)
                            }
                            ChartZeichnungen.WAAGERECHT -> {
                                val y = skala.y(z.p1)
                                drawLine(gelb, Offset(0f, y), Offset(breite, y), strokeWidth = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                            }
                            ChartZeichnungen.NOTIZ -> {
                                val p = Offset(achse.xVon(z.t1), skala.y(z.p1))
                                val t = messer.measure(z.text, TextStyle(fontSize = 11.sp), maxLines = 2, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (160 * density).toInt()))
                                val pad = 5 * density
                                val links = (p.x - t.size.width / 2f - pad).coerceIn(0f, (breite - t.size.width - 2 * pad).coerceAtLeast(0f))
                                val oben = (p.y - t.size.height - 2 * pad - 6 * density).coerceAtLeast(0f)
                                drawLine(gelb, p, Offset(p.x, oben + t.size.height + 2 * pad), strokeWidth = 1f * density)
                                drawCircle(gelb, 3f * density, p)
                                drawRoundRect(notizGrund, Offset(links, oben), Size(t.size.width + 2 * pad, t.size.height + 2 * pad), CornerRadius(6 * density))
                                drawText(t, color = notizSchrift, topLeft = Offset(links + pad, oben + pad))
                            }
                            else -> {
                                val id = markeVon(z.text)
                                markeZeichnen(id, Offset(achse.xVon(z.t1), skala.y(z.p1)), 9f * density, markenFarbe(id, signal, gelb))
                            }
                        }
                    }
                    entwurf?.let { (t, p) -> drawCircle(gelb, 4f * density, Offset(achse.xVon(t), skala.y(p))) }
                    eigeneLinien.forEach { l ->
                        if (l.preis < skala.lo || l.preis > skala.hi) return@forEach
                        val y = skala.y(l.preis)
                        drawLine(l.farbe.copy(alpha = 0.8f), Offset(0f, y), Offset(breite, y), strokeWidth = 1f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * density, 4 * density)))
                    }
                    // Marken eigener Geschaefte an der Kerze ihres Zeitpunkts: Kauf unter dem Tief, Verkauf ueber dem Hoch
                    if (kerzen.size >= 2) eigeneMarken.forEach { m ->
                        val luecke = kerzen[1].t - kerzen[0].t
                        if (m.t < kerzen.first().t - luecke || m.t > kerzen.last().t + luecke) return@forEach
                        val i = nearestIndex(kerzen, m.t).takeIf { it >= 0 } ?: return@forEach
                        val c = anzeige.getOrNull(i) ?: return@forEach
                        val x = achse.x(i)
                        val s = 4.5f * density
                        if (m.richtung == KursMarke.KREUZ) { val y = skala.y(c.close); val r = s * 1.3f; drawPath(Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }, m.farbe) }
                        else {
                            // Ring genau auf dem gebuchten Kurs, in der Spanne der Kerze; Dividenden ohne Kurs auf dem Schluss
                            val roh = kerzen[i]
                            val y = skala.y(ChartLayout.markenKurs(m.kurs, roh.low, roh.high, roh.close))
                            drawCircle(flaechenFarbe, radius = s, center = Offset(x, y))
                            drawCircle(m.farbe, radius = s * 0.8f, center = Offset(x, y), style = Stroke(width = 1.6f * density))
                        }
                    }
                    // Rahmen um die Kerzen des Musters mit seinem Namen
                    musterRahmen?.let { r ->
                        val tag = { c: Candle -> Math.floorDiv(c.t, 86_400_000L) }
                        val i0 = kerzen.indexOfFirst { tag(it) >= r.vonTag }
                        val i1 = kerzen.indexOfLast { tag(it) <= r.bisTag }
                        if (i0 < 0 || i1 < i0) return@let
                        val hoch = (i0..i1).maxOf { anzeige[it].high }
                        val tief = (i0..i1).minOf { anzeige[it].low }
                        val x0 = achse.x(i0) - achse.schritt * 0.5f
                        val x1 = achse.x(i1) + achse.schritt * 0.5f
                        val oben = skala.y(hoch) - 5 * density
                        val unten = skala.y(tief) + 5 * density
                        drawRect(achsenFarbe.copy(alpha = 0.12f), Offset(x0, oben), Size(x1 - x0, unten - oben))
                        drawRect(achsenFarbe, Offset(x0, oben), Size(x1 - x0, unten - oben), style = Stroke(1.5f * density))
                        val t = messer.measure(r.name, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                        val tx = ((x0 + x1) / 2f - t.size.width / 2f).coerceIn(0f, (breite - t.size.width).coerceAtLeast(0f))
                        val ty = if (oben - t.size.height - 2 * density >= 0f) oben - t.size.height - 2 * density else unten + 2 * density
                        // Hintergrund unter dem Namen, damit er ueber den Kerzen lesbar bleibt
                        val rand = 4 * density
                        drawRoundRect(notizGrund.copy(alpha = 0.92f), Offset(tx - rand, ty - rand / 2), Size(t.size.width + 2 * rand, t.size.height + rand), androidx.compose.ui.geometry.CornerRadius(rand))
                        drawText(t, color = achsenFarbe, topLeft = Offset(tx, ty))
                    }
                }
                scrub?.let { i ->
                    val x = achse.x(i)
                    drawLine(achsenFarbe.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, hoehe), strokeWidth = 1f * density)
                    if (prozent == null) anzeige.getOrNull(i)?.let { c ->
                        val y = skala.y(c.close)
                        drawLine(achsenFarbe.copy(alpha = 0.5f), Offset(0f, y), Offset(breite, y), strokeWidth = 1f * density)
                    }
                }
            }
            // Marken der waagerechten Linien in der Achsenspalte, nie ueber den Kerzen
            val rand = 3 * density
            val gesamt = size.width
            markenListe.forEachIndexed { k, m ->
                val wert = markenTexte[k]
                val pfeilBreite = if (m.pfeil != 0) 5 * density else 0f
                val name = if (m.name.isEmpty()) null else messer.measure(m.name, markenStil)
                val mitName = name != null && ChartLayout.bezeichnungPasst(gesamt - breite, wert.size.width + 2 * rand + pfeilBreite, name.size.width.toFloat(), 4 * density)
                val boxB = wert.size.width + 2 * rand + pfeilBreite + (if (mitName) name!!.size.width + 4 * density else 0f)
                val links = ChartLayout.achsenTextX(breite, gesamt, boxB, 1 * density, 1 * density)
                val oben = markenY[k] - markenH / 2f
                drawRoundRect(flaechenFarbe, Offset(links, oben), Size(boxB, markenH), CornerRadius(3 * density))
                drawRoundRect(m.farbe, Offset(links, oben), Size(boxB, markenH), CornerRadius(3 * density), style = Stroke(1f * density))
                var x = links + rand
                if (m.pfeil != 0) {
                    val cy = markenY[k]; val h = 2.5f * density; val w = 2.5f * density
                    drawPath(Path().apply {
                        if (m.pfeil > 0) { moveTo(x + w, cy - h); lineTo(x + 2 * w, cy + h); lineTo(x, cy + h) } else { moveTo(x, cy - h); lineTo(x + 2 * w, cy - h); lineTo(x + w, cy + h) }
                        close()
                    }, m.farbe)
                    x += pfeilBreite
                }
                if (mitName) { drawText(name!!, color = m.farbe, topLeft = Offset(x, markenY[k] - name.size.height / 2f)); x += name.size.width + 4 * density }
                drawText(wert, color = m.farbe, topLeft = Offset(x, markenY[k] - wert.size.height / 2f))
            }
            datumsAchse(kerzen, achse, hoehe, messer, achsenFarbe)
        }
    }
}

// Abstand eines Punkts von einer Strecke.
private fun strecke(p: Offset, a: Offset, b: Offset): Float {
    val d = b - a
    val l2 = d.x * d.x + d.y * d.y
    if (l2 <= 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / l2).coerceIn(0f, 1f)
    val q = Offset(a.x + t * d.x, a.y + t * d.y)
    return sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y))
}

// Vier Zeitpunkte unter dem Chart: Uhrzeit bei Tagescharts, sonst Tag und Monat oder mit Jahr.
private fun DrawScope.datumsAchse(kerzen: List<Candle>, achse: Achse, oben: Float, messer: TextMeasurer, farbe: Color) {
    if (kerzen.size < 2) return
    val dauer = kerzen.last().t - kerzen.first().t
    val stil = TextStyle(fontSize = 10.sp)
    for (k in 0..3) {
        val i = ((kerzen.size - 1) * (k + 0.5) / 4).toInt()
        val t = kerzen[i].t
        val text = when {
            dauer < 2 * 86_400_000L -> Fmt.time(t)
            dauer < 200 * 86_400_000L -> Fmt.dayMonth(t)
            else -> Fmt.shortDate(t)
        }
        val m = messer.measure(text, stil)
        drawText(m, color = farbe, topLeft = Offset(achse.x(i) - m.size.width / 2f, oben + 3 * density))
    }
}

// Rahmen um die Kerzen eines Musters: Name und erster und letzter Tag (Tage seit 1970).
private class MusterRahmen(val name: String, val vonTag: Long, val bisTag: Long)

// Indikator in eigenem Feld unter dem Chart mit Namen und dem Wert am Fadenkreuz oder dem letzten.
@Composable
private fun UnterFeld(titel: String, e: ChartIndikatoren.Ergebnis, kerzen: List<Candle>, scrub: Int?, modifier: Modifier) {
    val signal = LocalSignal.current
    val messer = rememberTextMeasurer()
    val achsenFarbe = MaterialTheme.colorScheme.onSurfaceVariant
    val dichte = LocalDensity.current
    BoxWithConstraints(modifier) {
        val rechts = with(dichte) { ACHSENSPALTE.toPx() }
        val kopf = with(dichte) { 16.dp.toPx() }
        val breite = (constraints.maxWidth - rechts).coerceAtLeast(1f)
        val hoehe = (constraints.maxHeight - kopf).coerceAtLeast(1f)
        val achse = remember(kerzen, breite) { Achse(kerzen, breite) }
        val skala = remember(e, hoehe) {
            val (lo, hi) = spanne(e.linien.asSequence().flatMap { it.werte.asSequence() }.filterNotNull() + e.stufen.asSequence(), 0.08)
            Skala(e.min ?: lo, e.max ?: hi, kopf, hoehe)
        }
        Canvas(Modifier.fillMaxSize()) {
            val i = scrub ?: (kerzen.size - 1)
            val wert = e.linien.firstOrNull { it.art == ChartIndikatoren.Art.LINIE }?.werte?.getOrNull(i) ?: e.linien.firstOrNull()?.werte?.getOrNull(i)
            val kopfText = titel + (wert?.let { "  " + kompakt(it) } ?: "")
            drawText(messer.measure(kopfText, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)), color = achsenFarbe, topLeft = Offset(0f, 0f))
            e.stufen.forEach { s ->
                val y = skala.y(s)
                drawLine(achsenFarbe.copy(alpha = 0.35f), Offset(0f, y), Offset(breite, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val t = messer.measure(kompakt(s), TextStyle(fontSize = 9.sp))
                drawText(t, color = achsenFarbe, topLeft = Offset(ChartLayout.achsenTextX(breite, size.width, t.size.width.toFloat(), 4 * density, 2 * density), y - t.size.height / 2f))
            }
            if (e.stufen.isEmpty()) {
                listOf(skala.hi, skala.lo).forEach { v ->
                    val t = messer.measure(kompakt(v), TextStyle(fontSize = 9.sp))
                    drawText(t, color = achsenFarbe, topLeft = Offset(ChartLayout.achsenTextX(breite, size.width, t.size.width.toFloat(), 4 * density, 2 * density), (skala.y(v) - t.size.height / 2f).coerceIn(kopf, kopf + hoehe - t.size.height)))
                }
            }
            clipRect(0f, kopf, breite, kopf + hoehe) {
                e.linien.forEach { l ->
                    if (l.art == ChartIndikatoren.Art.BALKEN) {
                        val null0 = skala.y(maxOf(skala.lo, minOf(0.0, skala.hi)))
                        l.werte.forEachIndexed { k, v ->
                            if (v == null) return@forEachIndexed
                            val farbe = when (l.farbe) {
                                ChartIndikatoren.FARBE_RICHTUNG -> kerzen.getOrNull(k)?.let { if (it.close >= it.open) signal.up else signal.down } ?: achsenFarbe
                                ChartIndikatoren.FARBE_VORZEICHEN -> if (v >= 0) signal.up else signal.down
                                else -> linienFarbe(l.farbe, signal)
                            }.copy(alpha = 0.75f)
                            val y = skala.y(v)
                            val sp = ChartLayout.spalte(achse.x(k), achse.schritt, density)
                            drawRect(farbe, Offset(sp.links, minOf(y, null0)), Size(sp.breite, maxOf(abs(null0 - y), 1f)))
                        }
                    } else if (l.art == ChartIndikatoren.Art.PUNKTE) l.werte.forEachIndexed { k, v ->
                        if (v != null) drawCircle(linienFarbe(l.farbe, signal), 2f * density, Offset(achse.x(k), skala.y(v)))
                    } else linie(l.werte, achse, skala, linienFarbe(l.farbe, signal), 1.5f * density)
                }
                scrub?.let { k -> drawLine(achsenFarbe.copy(alpha = 0.7f), Offset(achse.x(k), kopf), Offset(achse.x(k), kopf + hoehe), strokeWidth = 1f * density) }
            }
        }
    }
}

// Kurze Zahl fuer Indikatorwerte: Tausender, Millionen und Milliarden abgekuerzt.
private fun kompakt(v: Double): String = when {
    abs(v) >= 1e9 -> Fmt.num(v / 1e9, 1) + (if (deutsch()) " Mrd." else " bn")
    abs(v) >= 1e6 -> Fmt.num(v / 1e6, 1) + (if (deutsch()) " Mio." else " m")
    abs(v) >= 1e4 -> Fmt.num(v / 1e3, 0) + (if (deutsch()) " Tsd." else " k")
    abs(v) >= 100 -> Fmt.num(v, 0)
    abs(v) >= 1 -> Fmt.num(v, 1)
    abs(v) < 1e-9 -> "0"
    else -> Fmt.num(v, 3)
}

// Indikatoren, mit denen Einsteiger am leichtesten anfangen (5.92): Trend, Umsatz, Schwung.
private val EINSTIEG = setOf("sma200", "volumen", "rsi", "macd")

@Composable
private fun IndikatorWahl(aktiv: Set<ChartIndikatoren.Indikator>, umschalten: (ChartIndikatoren.Indikator) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        listOf(ChartIndikatoren.Ort.KURS to S.chartIndikatorenKurs, ChartIndikatoren.Ort.UNTEN to S.chartIndikatorenUnten).forEach { (ort, titel) ->
            SectionHeader(titel)
            ChartIndikatoren.Indikator.entries.filter { it.ort == ort }.forEach { i ->
                Row(
                    Modifier.fillMaxWidth().clickable { umschalten(i) }.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = i in aktiv, onCheckedChange = { umschalten(i) })
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(S.chartIndikatorName(i.key), style = MaterialTheme.typography.bodyMedium)
                        if (i.key in EINSTIEG) Text(S.pcEinstieg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(S.chartIndikatorInfo(i.key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatzWahl(state: AppState, symbol: String, gewaehlt: String, onWahl: (MarketRepository.Handelsplatz) -> Unit) {
    var plaetze by remember(symbol) { mutableStateOf<List<MarketRepository.Handelsplatz>?>(null) }
    LaunchedEffect(symbol) { plaetze = state.market.handelsplaetze(symbol) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        SectionHeader(S.venue)
        val liste = plaetze
        when {
            liste == null -> Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(S.chartHandelsplatzSuche, style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                liste.forEach { p ->
                    OptionRow(
                        p.boerse.ifBlank { p.symbol },
                        listOfNotNull(p.symbol, p.waehrung.takeIf { it.isNotBlank() }, Fmt.priceEuro(p.kurs), p.zeit.takeIf { it > 0 }?.let { Fmt.dateTime(it) }).joinToString(" · "),
                        selected = p.symbol == gewaehlt,
                    ) { onWahl(p) }
                }
                if (liste.size <= 1) Text(
                    S.chartHandelsplatzLeer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// Vorschlaege im Vergleich: wichtige Indizes und ein Weltindex-ETF.
private val VERGLEICH_VORSCHLAEGE = listOf("^GDAXI" to "DAX", "^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "^STOXX50E" to "Euro Stoxx 50", "EUNL.DE" to "MSCI World", "BTC-EUR" to "Bitcoin")

@Composable
private fun VergleichWahl(state: AppState, schon: List<String>, onWahl: (String) -> Unit) {
    var suche by remember { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }
    LaunchedEffect(suche) {
        if (suche.isBlank()) { treffer = emptyList(); return@LaunchedEffect }
        delay(300)
        treffer = runCatching { state.market.yahooSuche(suche.trim()) }.getOrDefault(emptyList())
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        SectionHeader(S.chartVergleichTitel)
        OutlinedTextField(
            value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, label = { Text(S.chartVergleichSuche) },
            shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (suche.isBlank()) {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VERGLEICH_VORSCHLAEGE.filter { it.first !in schon }.forEach { (s, n) -> FilterPill(n) { onWahl(s) } }
            }
        }
        treffer.filter { it.symbol !in schon }.forEach { h ->
            Row(
                Modifier.fillMaxWidth().clickable { onWahl(h.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    NamenText(h.name.ifBlank { h.symbol }, style = MaterialTheme.typography.bodyMedium)
                    Text(listOf(h.symbol, h.exchange).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EmojiWahl(onWahl: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        val signal = LocalSignal.current
        val gelb = LINIEN_GRUND[0]
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MARKEN.forEach { e ->
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).clickable { onWahl(e) }, contentAlignment = Alignment.Center) {
                    val farbe = markenFarbe(e, signal, gelb)
                    Canvas(Modifier.size(28.dp)) { markeZeichnen(e, center, size.minDimension * 0.4f, farbe) }
                }
            }
        }
    }
}

private fun deutsch(): Boolean = de.troetenrepublik.app.i18n.I18n.lang == de.troetenrepublik.app.data.Lang.DE
