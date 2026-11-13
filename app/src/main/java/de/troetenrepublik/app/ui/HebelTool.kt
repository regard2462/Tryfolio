package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.PortfolioTools
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Achsen
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.HebelSimulator
import de.troetenrepublik.app.i18n.HebelGrenzeTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ln

// Werkzeug „Hebel-Simulator“ (0.78): taeglich zurueckgesetzter Hebel auf die Tageskurse eines Werts. Rechnung in
// engine/HebelSimulator.kt. Steht bei den Werkzeugen der Wertpapierseite (fester Wert) und unter Analytics (Wahl ueber
// die Suche).

internal fun hgText(key: String, vararg w: String): String = HebelGrenzeTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Betrag, an dem die Endwerte gezeigt werden
private const val HEBEL_BETRAG = 1_000.0
private const val HEBEL_JAHR_MS = 365L * 86_400_000L
// Hoechstens so viele Punkte je Linie im Verlauf
private const val HEBEL_PUNKTE = 600

// Zuletzt unter Analytics gewaehlter Wert (Symbol, Name)
internal var hebelZuletzt: Pair<String, String>? = null

// Hebel ohne Nachkommastellen, wo er ganz ist, mit echtem Minuszeichen
internal fun hebelZahl(h: Double): String {
    val ganz = abs(h - Math.round(h)) < 1e-9
    val zehntel = abs(h * 10 - Math.round(h * 10)) < 1e-9
    return (if (h < 0) "−" else "") + Fmt.num(abs(h), if (ganz) 0 else if (zehntel) 1 else 2)
}

// Regler: Bereich -3 bis 5 in Schritten von 0,25, ohne den Bereich zwischen -1 und 1 (0 ist kein Hebel).
internal const val HEBEL_MIN = -3.0
internal const val HEBEL_MAX = 5.0
internal const val HEBEL_SCHRITT = 0.25
// Spur: -3 bis -1 auf 0 bis 2, danach 1 bis 5 auf 2,25 bis 6,25
internal const val HEBEL_POS_MAX = 6.25
internal const val HEBEL_POS_SCHRITTE = 25

// Naechster erlaubter Hebel: aufs Raster gerundet, im Bereich, und statt der Werte zwischen -1 und 1 gilt -1 oder 1
internal fun hebelEinrasten(h: Double): Double {
    if (!h.isFinite()) return HebelSimulator.HEBEL_STANDARD
    val r = (Math.round(h / HEBEL_SCHRITT) * HEBEL_SCHRITT).coerceIn(HEBEL_MIN, HEBEL_MAX)
    return if (abs(r) < 1.0) (if (h < 0) -1.0 else 1.0) else r
}

internal fun hebelPos(h: Double): Double {
    val r = hebelEinrasten(h)
    return if (r < 0) r - HEBEL_MIN else r + 1.25
}

internal fun posHebel(u: Double): Double {
    val r = (Math.round(u / HEBEL_SCHRITT) * HEBEL_SCHRITT).coerceIn(0.0, HEBEL_POS_MAX)
    return if (r <= 2.0) r + HEBEL_MIN else r - 1.25
}

// Startwert ohne Wahl: die Positionen nach Wert (je Symbol eine, Derivate nicht), danach ein breiter Index
internal val HEBEL_START_INDEX = "EUNL.DE" to "MSCI World"

internal fun hebelStartKandidaten(positionen: List<Triple<String, String, Double>>): List<Pair<String, String>> =
    positionen.filter { it.third > 0 && it.third.isFinite() }.groupBy { it.first }
        .map { (s, l) -> Triple(s, l.first().second.ifBlank { s }, l.sumOf { it.third }) }
        .sortedByDescending { it.third }.map { it.first to it.second } + HEBEL_START_INDEX

@Composable
internal fun HebelTool(state: AppState, festSymbol: String? = null) {
    var wahl by remember { mutableStateOf(hebelZuletzt) }
    if (festSymbol == null) {
        val kandidaten = hebelStartKandidaten(
            depotBewertung(state).positions.filter { !it.position.isDerivative }.map { Triple(it.position.symbol, it.position.name, it.value) },
        )
        // Ohne eigene Wahl steht die groesste Position mit Kursreihe da, sonst der MSCI World
        LaunchedEffect(wahl == null) {
            if (wahl != null) return@LaunchedEffect
            wahl = kandidaten.firstOrNull { (s, _) -> s == HEBEL_START_INDEX.first || de.troetenrepublik.app.market.ohneAbbruch { state.dailyCloses(s) }.getOrNull().orEmpty().isNotEmpty() }
        }
        HebelSuche(state, wahl) { wahl = it; hebelZuletzt = it }
    }
    val symbol = festSymbol ?: wahl?.first ?: return
    HebelInhalt(state, symbol)
}

// Suchfeld wie im Testdepot: Katalog sofort, die Suche im Netz kurz danach. Darunter der gewaehlte Wert.
@Composable
private fun HebelSuche(state: AppState, wahl: Pair<String, String>?, onWahl: (Pair<String, String>) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var suche by rememberSaveable { mutableStateOf("") }
    val fokus = LocalFocusManager.current
    val tastatur = LocalSoftwareKeyboardController.current
    var treffer by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(suche) {
        val q = suche.trim()
        val lokal = if (q.length >= 2) Symbols.search(q).map { it.symbol to it.name.ifBlank { it.symbol } } else emptyList()
        treffer = lokal.take(8)
        if (q.length < 2) return@LaunchedEffect
        try {
            delay(350)
            val bekannt = lokal.map { it.first }.toSet()
            val arten = setOf("EQUITY", "ETF", "CRYPTOCURRENCY", "INDEX", "MUTUALFUND")
            val fern = state.market.search(q).filter { it.type in arten && it.symbol !in bekannt }.map { it.symbol to it.name.ifBlank { it.symbol } }
            treffer = (lokal + fern).distinctBy { it.first }.take(10)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    OutlinedTextField(
        value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, placeholder = { Text(S.ptTestSuche) },
        shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (suche.trim().length >= 2) {
        treffer.forEach { (symbol, name) ->
            Column(Modifier.fillMaxWidth().clickable { onWahl(symbol to name); suche = ""; fokus.clearFocus(); tastatur?.hide() }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(symbol, style = MaterialTheme.typography.bodySmall, color = grau)
            }
        }
    } else if (wahl != null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp)) {
            Text(wahl.second, style = MaterialTheme.typography.titleMedium)
            if (wahl.second != wahl.first) Text(wahl.first, style = MaterialTheme.typography.bodySmall, color = grau)
        }
    }
}

// Tageskurse, Handelswaehrung und Zinsreihe eines Werts
private class HebelDaten(
    val kurse: List<Pair<Long, Double>>, val waehrung: String, val zins: List<Pair<Long, Double>>, val quellen: List<HebelSimulator.Zinsquelle>,
    val ohneNetz: Boolean,
)

@Composable
private fun HebelInhalt(state: AppState, symbol: String) {
    val signal = LocalSignal.current
    var daten by remember(symbol) { mutableStateOf<HebelDaten?>(null) }
    var versuch by remember(symbol) { mutableIntStateOf(0) }
    LaunchedEffect(symbol, versuch) {
        daten = null
        val d = state.dailyCloses(symbol)
        // Die Tageskurse sind mit dem heutigen Wechselkurs umgerechnet, ihre Renditen sind also die der Handelswaehrung
        val w = ExchangeRates.hauptwaehrung(state.quotes.value[symbol]?.nativeCurrency.orEmpty().ifEmpty { "EUR" })
        val zins = if (d.isEmpty()) null else state.finanzierungszins(w)
        daten = HebelDaten(d.map { it.t to it.close }, w, zins?.reihe.orEmpty(), zins?.quellen.orEmpty(), d.isEmpty() && state.market.ohneVerbindung())
    }
    var hebel by rememberSaveable { mutableDoubleStateOf(HebelSimulator.HEBEL_STANDARD) }
    // Ein gemerkter Wert liegt immer auf dem Raster und nie bei 0
    val hebelRaster = hebelEinrasten(hebel)
    if (hebelRaster != hebel) hebel = hebelRaster
    var jahre by rememberSaveable { mutableIntStateOf(5) }
    var aufschlag by rememberSaveable { mutableFloatStateOf(HebelSimulator.AUFSCHLAG_STANDARD.toFloat()) }
    var kosten by rememberSaveable { mutableFloatStateOf(HebelSimulator.KOSTEN_STANDARD.toFloat()) }
    var annahmenOffen by rememberSaveable { mutableStateOf(false) }

    val d = daten ?: run { ToolNote(S.toolLoading); return }
    if (d.kurse.isEmpty()) {
        if (d.ohneNetz) EmptyHint(S.ohneVerbindung, S.erneutVersuchen to { versuch++ }) else ToolNote(S.toolNoData)
        return
    }
    HebelRegler(hgText("h.hebel"), hebel) { hebel = it }
    val jetzt = remember(d) { System.currentTimeMillis() }
    val vorhanden = (jetzt - d.kurse.first().first) / HEBEL_JAHR_MS.toDouble()
    val zeiten = remember(d) {
        val feste = listOf(1, 3, 5, 10).filter { it <= vorhanden + 0.05 }.map { S.toolYearsKurz(it) to it }
        if (feste.isEmpty() || vorhanden < 9.9) feste + (S.toolMax to 0) else feste
    }
    val zeit = if (zeiten.any { it.second == jahre }) jahre else zeiten.last().second
    ToolZeit(S.widgetZeitraum, zeiten, zeit) { jahre = it }

    val reihe = remember(d, zeit) { if (zeit <= 0) d.kurse else d.kurse.filter { it.first >= jetzt - zeit * HEBEL_JAHR_MS } }
    val annahmen = remember(d, aufschlag, kosten) { HebelSimulator.Annahmen(aufschlag.toDouble(), kosten.toDouble(), d.zins) }
    val e = remember(reihe, hebel, annahmen) { HebelSimulator.rechnen(reihe, hebel, annahmen) } ?: run { ToolNote(hgText("h.zuKurz")); return }
    val kurve = remember(reihe, annahmen) { HebelSimulator.kurve(reihe, annahmen) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farbeMit = Kategorie.blau
    val hebelText = hebelZahl(hebel)

    // Kernaussage: Endwert mit Hebel gegen ohne
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Text(
            if (hebel < 0) hgText("h.kern.titelShort", Fmt.euro(HEBEL_BETRAG, 0), hebelText) else hgText("h.kern.titel", Fmt.euro(HEBEL_BETRAG, 0), hebelText),
            style = MaterialTheme.typography.bodySmall, color = grau,
        )
        Text(
            Fmt.euro(HEBEL_BETRAG * e.endMit, 0), style = MaterialTheme.typography.headlineMedium,
            color = signal.forChange(e.endMit - e.endOhne), maxLines = 1, softWrap = false,
        )
        Text(
            hgText(
                if (e.proJahr) "h.kern.ohne" else "h.kern.ohneZeitraum", Fmt.euro(HEBEL_BETRAG * e.endOhne, 0),
                Fmt.signedPct(e.renditeMit * 100, 1), Fmt.signedPct(e.renditeOhne * 100, 1),
            ),
            style = MaterialTheme.typography.bodySmall, color = grau,
        )
        e.totalverlustAm?.let { Text(hgText("h.total", Fmt.date(it)), style = MaterialTheme.typography.titleSmall, color = signal.down, modifier = Modifier.padding(top = 4.dp)) }
    }
    HebelVerlauf(e, hgText("h.mal", hebelText), hgText("h.mal", hebelZahl(1.0)), farbeMit, MaterialTheme.colorScheme.onSurface)

    KeyValueRow(hgText("h.rueckgang.mit"), "−" + Fmt.pct(e.rueckgangMit * 100, 1), valueColor = signal.down)
    KeyValueRow(hgText("h.rueckgang.ohne"), "−" + Fmt.pct(e.rueckgangOhne * 100, 1))
    HebelSimulator.totalverlustSchwelle(hebel)?.let { s ->
        val art = if (hebel > 0) "long" else "short"
        KeyValueRow(hgText("h.schwelle.$art"), Fmt.pct(s * 100, 1), hint = hgText("h.schwelle.hint.$art", Fmt.pct(e.schlimmsterTag * 100, 1)))
    }

    HebelKrisen(state, symbol, hebel, annahmen, farbeMit, MaterialTheme.colorScheme.onSurface)

    // Hebel mal Rendite gegen das Ergebnis: drei Balken fuer das, was dazwischen liegt
    SectionHeader(hgText("h.zerlegung"))
    val z = e.zerlegung
    KeyValueRow(hgText("h.z.naiv"), Fmt.signedPct(z.naiv * 100, 1))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val groesster = maxOf(abs(z.pfad), abs(z.finanzierung), abs(z.kosten), 1e-9)
        listOf(
            (if (z.pfad >= 0) "h.z.pfad" else "h.z.pfadGewinn") to z.pfad,
            (if (z.finanzierung >= 0) "h.z.fin" else "h.z.finErtrag") to z.finanzierung,
            "h.z.kosten" to z.kosten,
        ).forEach { (k, v) ->
            LageBalkenZeile(hgText(k), Fmt.signedPunkte(-v * 100, 1), abs(v) / groesster, if (v > 0) signal.down else signal.up, null)
        }
    }
    KeyValueRow(hgText("h.z.ist"), Fmt.signedPct(z.tatsaechlich * 100, 1), valueColor = signal.forChange(z.tatsaechlich))
    InfoZeile(hgText("h.z.kurz"), hgText("h.z.infoTitel"), hgText("h.z.info"))

    // Rendite je Hebel mit dem Hebel, der im Rueckblick am meisten brachte
    val bester = HebelSimulator.bester(kurve)
    if (bester != null) {
        SectionHeader(hgText(if (e.proJahr) "h.kurve.titel" else "h.kurve.titelZeitraum"))
        Text(
            hgText("h.kurve.satz", hebelZahl(bester.first), Fmt.signedPct(bester.second * 100, 1)), style = MaterialTheme.typography.bodySmall, color = grau,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HebelKurve(kurve, bester, hebel, hgText("h.mal", hebelZahl(bester.first)), hgText("h.mal", hebelZahl(hebel)), farbeMit, Kategorie.ocker) { hebel = it }
    }

    val zinsText = Fmt.pct(e.zinsMittel, 2)
    AufklappZeile(
        hgText("h.annahmen"), hgText("h.annahmen.unter", zinsText, Fmt.pct(aufschlag.toDouble(), 2), Fmt.pct(kosten.toDouble(), 2)), annahmenOffen,
    ) { annahmenOffen = !annahmenOffen }
    if (annahmenOffen) {
        KeyValueRow(if (d.zins.isEmpty()) hgText("h.zins.fest") else hgText("h.zins.reihe", d.waehrung), zinsText)
        SliderRow(hgText("h.aufschlag"), Fmt.pct(aufschlag.toDouble(), 2), aufschlag, 0f..3f, 0.25f) { aufschlag = it }
        SliderRow(hgText("h.kosten"), Fmt.pct(kosten.toDouble(), 2), kosten, 0f..2f, 0.05f) { kosten = it }
    }
    HinweisInfo(hgText("h.hinweis"), kurz = hinweisText("modell"))
    // Die Krisen rechnen mit dem Tagesgeldsatz der Fed; er gehoert in den Anhang, auch wenn der Wert in einer anderen Waehrung notiert
    QuellenAnhang((d.quellen + if (d.quellen.none { it === HebelSimulator.DFF }) listOf(HebelSimulator.DFF) else emptyList()).map { q ->
        // FRED verlangt die eigene Zitierung der Reihe
        val reihe = if (q.fred) de.troetenrepublik.app.engine.FredRegeln.reiheAus(q.seite) else null
        if (reihe != null) fredQuelle(reihe) else QuelleLink(hgText(q.text), q.seite)
    })
}

// Regler fuer den Hebel: links die Bezeichnung, rechts der Wert gross und einzeilig. Die Spur laesst den Bereich
// zwischen -1 und 1 aus, 0 ist deshalb nicht waehlbar und -1 und 1 liegen einen Schritt auseinander.
@Composable
private fun HebelRegler(label: String, hebel: Double, onWahl: (Double) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        var info by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { info = true }) {
                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            // Negativer Hebel ist ein Short
            Text(
                hgText(if (hebel < 0) "h.malShort" else "h.mal", hebelZahl(hebel)), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false,
            )
        }
        ToolSlider(
            value = hebelPos(hebel).toFloat(),
            onChange = { v -> val h = posHebel(v.toDouble()); if (h != hebel) onWahl(h) },
            range = 0f..HEBEL_POS_MAX.toFloat(),
            steps = HEBEL_POS_SCHRITTE - 1,
        )
        // Bereiche der Spur: -3 bis -1 liegen auf den ersten 2,25 von 6,25 Teilen
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            Text(hgText("h.short"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2.25f))
            Text(hgText("h.long"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(4f))
        }
        if (info) MarktInfoBlatt(hgText("h.info.titel"), hgText("h.info")) { info = false }
    }
}

// Beide Verlaeufe ab demselben Start auf logarithmischer Skala; die Bezeichnung steht rechts am Ende jeder Linie,
// darunter Anfang und Ende des Zeitraums.
@Composable
private fun HebelVerlauf(e: HebelSimulator.Ergebnis, nameMit: String, nameOhne: String, farbeMit: Color, farbeOhne: Color) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val messer = rememberTextMeasurer()
    val stil = MaterialTheme.typography.labelMedium
    val textMit = remember(nameMit, stil) { messer.measure(nameMit, stil.copy(color = farbeMit)) }
    val textOhne = remember(nameOhne, stil) { messer.measure(nameOhne, stil.copy(color = farbeOhne)) }
    val rechts = maxOf(textMit.size.width, textOhne.size.width) + with(LocalDensity.current) { 8.dp.toPx() }
    // Untergrenze der Skala: der kleinste positive Wert; ein Totalverlust laeuft an den unteren Rand
    val grenzen = remember(e) {
        val positiv = (e.mit.asSequence() + e.ohne.asSequence()).filter { it > 0.0 }
        val lo = minOf(positiv.minOrNull() ?: 1.0, 1.0)
        val hi = maxOf(positiv.maxOrNull() ?: 1.0, 1.0)
        val lnLo = ln(lo)
        val lnHi = ln(hi)
        if (lnHi - lnLo < 1e-6) (lnLo - 0.05) to (lnHi + 0.05) else lnLo to lnHi
    }
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp).height(180.dp)) {
        val rand = 6.dp.toPx()
        val breite = size.width - rechts
        val hoehe = size.height - 2 * rand
        val n = e.mit.size
        val (lnLo, lnHi) = grenzen
        fun x(i: Int) = breite * i / (n - 1).toFloat()
        fun y(v: Double) = rand + hoehe * (1f - ((ln(maxOf(v, 1e-300)).coerceIn(lnLo, lnHi) - lnLo) / (lnHi - lnLo)).toFloat())
        // Startwert als Bezugslinie
        drawLine(grau.copy(alpha = 0.4f), Offset(0f, y(1.0)), Offset(breite, y(1.0)), strokeWidth = 1.dp.toPx())
        val schritt = maxOf(1, n / HEBEL_PUNKTE)
        fun pfad(w: DoubleArray) = Path().apply {
            var i = 0
            while (i < n) {
                if (i == 0) moveTo(x(i), y(w[i])) else lineTo(x(i), y(w[i]))
                // Der letzte Punkt gehoert immer dazu
                i = if (i < n - 1 && i + schritt > n - 1) n - 1 else i + schritt
            }
        }
        val strich = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(pfad(e.ohne), farbeOhne, style = strich)
        drawPath(pfad(e.mit), farbeMit, style = strich)
        // Bezeichnungen rechts neben den Endpunkten, bei Naehe auseinandergerueckt
        val h = textMit.size.height.toFloat()
        var yMit = y(e.endMit) - h / 2
        var yOhne = y(e.endOhne) - h / 2
        if (abs(yMit - yOhne) < h) {
            val mitte = (yMit + yOhne) / 2
            if (yMit <= yOhne) { yMit = mitte - h / 2; yOhne = mitte + h / 2 } else { yOhne = mitte - h / 2; yMit = mitte + h / 2 }
        }
        val oben = 0f
        val unten = size.height - h
        if (unten - oben >= h) {
            // Beide im Bild halten, ohne sie wieder zu ueberlagern
            val obere = minOf(yMit, yOhne).coerceIn(oben, unten - h)
            val untere = maxOf(yMit, yOhne).coerceIn(obere + h, unten)
            if (yMit <= yOhne) { yMit = obere; yOhne = untere } else { yOhne = obere; yMit = untere }
        }
        drawText(textMit, topLeft = Offset(breite + 6.dp.toPx(), yMit))
        drawText(textOhne, topLeft = Offset(breite + 6.dp.toPx(), yOhne))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(Fmt.date(e.zeiten.first()), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        Text(Fmt.date(e.zeiten.last()), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
    }
}

// Rendite je Hebel als Kurve: das Maximum als Punkt mit Bezeichnung, der gewaehlte Hebel als senkrechte Linie mit eigener Beschriftung oben.
@Composable
private fun HebelKurve(kurve: List<Pair<Double, Double>>, bester: Pair<Double, Double>, gewaehlt: Double, nameBester: String, nameGewaehlt: String, farbe: Color, farbeBester: Color, onWahl: (Double) -> Unit) {
    if (kurve.size < 2) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val text = MaterialTheme.colorScheme.onSurface
    val messer = rememberTextMeasurer()
    val stil = MaterialTheme.typography.labelSmall.copy(color = grau)
    val xVon = kurve.first().first
    val xBis = kurve.last().first
    val yLo = minOf(0.0, kurve.minOf { it.second })
    val yHi = maxOf(0.0, kurve.maxOf { it.second }).let { if (it - yLo < 1e-6) yLo + 0.01 else it }
    val yTicks = remember(yLo, yHi) { listOf(yHi, 0.0, yLo).distinctBy { Math.round(it * 1000) } }
    val yTexte = remember(yTicks, stil) { yTicks.map { messer.measure(Fmt.signedPct(it * 100, 0), stil) } }
    val xTicks = remember(xVon, xBis) { (xVon.toInt()..xBis.toInt()).toList() }
    val xTexte = remember(xTicks, stil) { xTicks.map { messer.measure(hebelZahl(it.toDouble()), stil) } }
    val marke = remember(nameBester, farbeBester) { messer.measure(nameBester, stil.copy(color = farbeBester)) }
    val wahlText = remember(nameGewaehlt, text) { messer.measure(nameGewaehlt, stil.copy(color = text)) }
    val dichte = LocalDensity.current
    val links = yTexte.maxOf { it.size.width } + with(dichte) { 8.dp.toPx() }
    val wahlJetzt by rememberUpdatedState(onWahl)
    // Tippen auf die Kurve oder den Punkt setzt den Regler auf den Hebel an dieser Stelle
    val tippen = Modifier.pointerInput(xVon, xBis, links) {
        detectTapGestures { o ->
            val bx = size.width - links - 6.dp.toPx()
            if (bx > 0f) wahlJetzt(hebelEinrasten(xVon + ((o.x - links) / bx).coerceIn(0f, 1f) * (xBis - xVon)))
        }
    }
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 6.dp).height(184.dp).then(tippen)) {
        // Zwei Zeilen oben: ganz oben die Beschriftung des gewaehlten Hebels, darunter die des Maximums
        val kopf = marke.size.height + wahlText.size.height + 14.dp.toPx()
        val fuss = xTexte.first().size.height + 6.dp.toPx()
        val boden = size.height - fuss
        val breite = size.width - links - 6.dp.toPx()
        fun x(h: Double) = links + (breite * (h - xVon) / (xBis - xVon)).toFloat()
        fun y(v: Double) = kopf + ((yHi - v) / (yHi - yLo)).toFloat() * (boden - kopf)
        // Beschriftungen, die sich beruehren wuerden, fallen weg; die Nulllinie hat Vorrang
        val gezeigt = Achsen.ohneUeberlappung(
            yTicks.map { y(it) }, yTicks.indices.sortedBy { if (yTicks[it] == 0.0) 0 else 1 }, yTexte.maxOf { it.size.height }.toFloat(), 2.dp.toPx(),
        )
        yTicks.forEachIndexed { i, tk ->
            drawLine(grau.copy(alpha = if (tk == 0.0) 0.45f else 0.15f), Offset(links, y(tk)), Offset(size.width, y(tk)), strokeWidth = 1.dp.toPx())
            if (i !in gezeigt) return@forEachIndexed
            val t = yTexte[i]
            drawText(t, topLeft = Offset(links - 4.dp.toPx() - t.size.width, (y(tk) - t.size.height / 2f).coerceIn(0f, boden - t.size.height)))
        }
        xTicks.forEachIndexed { i, tk ->
            val t = xTexte[i]
            drawText(t, topLeft = Offset((x(tk.toDouble()) - t.size.width / 2f).coerceIn(links, size.width - t.size.width), boden + 4.dp.toPx()))
        }
        val c = Offset(x(bester.first), y(bester.second))
        val markeX = (c.x - marke.size.width / 2f).coerceIn(links, size.width - marke.size.width)
        if (gewaehlt in xVon..xBis) {
            val gx = x(gewaehlt)
            val wx = (gx - wahlText.size.width / 2f).coerceIn(links, size.width - wahlText.size.width)
            // Die Linie beginnt unter der eigenen Beschriftung; liegt sie im Bereich der Maximum-Beschriftung, erst darunter
            val ueberMarke = gx > markeX - 2.dp.toPx() && gx < markeX + marke.size.width + 2.dp.toPx()
            drawLine(text.copy(alpha = 0.5f), Offset(gx, if (ueberMarke) kopf else wahlText.size.height + 2.dp.toPx()), Offset(gx, boden), strokeWidth = 1.dp.toPx())
            drawText(wahlText, topLeft = Offset(wx, 0f))
        }
        // Eine durchgehende Linie ueber den ganzen Bereich, auch zwischen -1 und 1 (Hebel 0 = nur Zins)
        val pfad = Path().apply { kurve.forEachIndexed { i, (h, v) -> if (i == 0) moveTo(x(h), y(v)) else lineTo(x(h), y(v)) } }
        drawPath(pfad, farbe, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(farbeBester, 4.5.dp.toPx(), c)
        // Bezeichnung ueber dem Maximum: Dort liegt die Kurve nie
        drawText(marke, topLeft = Offset(markeX, c.y - marke.size.height - 6.dp.toPx()))
    }
}

// Kurse fuer die Krisen: Tageskurse des Werts ab 1999 (frueher und rollender Teil zusammengesetzt), die des S&P 500 und der Tagesgeldsatz der Fed
private class KrisenDaten(val wert: List<Candle>, val index: List<Candle>, val zins: List<Pair<Long, Double>>)

private const val KRISEN_BETRAG = 1_000.0
private const val KRISEN_SPALTE = 98

// Gleicher Hebel in den grossen Krisen (Hoch bis Tief des S&P 500): Tabelle mit den Spalten ohne Hebel und mit Hebel, je Krise der Wert aus 1.000 EUR.
@Composable
private fun HebelKrisen(
    state: AppState, symbol: String, hebel: Double, a: HebelSimulator.Annahmen, farbeMit: Color, farbeOhne: Color,
) {
    val daten by produceState<KrisenDaten?>(null, symbol) {
        value = null
        value = try {
            coroutineScope {
                val fruehW = async { state.longHistory(symbol) }
                val tagW = async { state.dailyCloses(symbol) }
                val fruehI = async { state.longHistory(REFERENCE) }
                val tagI = async { state.dailyCloses(REFERENCE) }
                val zins = async { state.finanzierungszins("USD", abJahr = 1999).reihe }
                KrisenDaten(
                    PortfolioTools.zusammengesetzt(fruehW.await(), tagW.await()), PortfolioTools.zusammengesetzt(fruehI.await(), tagI.await()), zins.await(),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    }
    val k = daten ?: return
    // Die Krisen rechnen mit dem Tagesgeldsatz der Fed (Dollar), dem Aufschlag und den Kosten der Annahmen
    val krisen = remember(k, hebel, a.aufschlag, a.kosten) {
        HebelSimulator.krisen(k.wert, k.index, hebel, HebelSimulator.Annahmen(a.aufschlag, a.kosten, k.zins))
    }
    if (krisen.isEmpty()) return
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    SectionHeader(hgText("h.krisen.titel"))
    // Spaltenkopf mit Farbpunkt wie die Linien im Verlauf
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        listOf(farbeOhne to hgText("h.mal", hebelZahl(1.0)), farbeMit to hgText("h.mal", hebelZahl(hebel))).forEach { (farbe, name) ->
            Row(Modifier.width(KRISEN_SPALTE.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium, color = grau, maxLines = 1, softWrap = false)
            }
        }
    }
    krisen.forEachIndexed { i, kr ->
        if (i > 0) Trennlinie()
        val von = Instant.ofEpochMilli(kr.von).atZone(ZoneOffset.UTC).year
        val bis = Instant.ofEpochMilli(kr.bis).atZone(ZoneOffset.UTC).year
        val zeit = if (von == bis) "$von" else hgText("h.krisen.zeit", "$von", "$bis")
        val e = kr.ergebnis
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(S.ptCrashName(kr.schluessel), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (kr.aus) zeit + " · " + hgText("h.krisen.index") else zeit, style = MaterialTheme.typography.labelSmall, color = grau,
                )
            }
            Text(
                Fmt.euro(KRISEN_BETRAG * e.endOhne, 0), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(e.endOhne - 1.0),
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(KRISEN_SPALTE.dp),
            )
            Text(
                if (e.totalverlustAm != null) hgText("h.krisen.total") else Fmt.euro(KRISEN_BETRAG * e.endMit, 0),
                style = MaterialTheme.typography.titleSmall, color = if (e.totalverlustAm != null) signal.down else signal.forChange(e.endMit - 1.0),
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(KRISEN_SPALTE.dp),
            )
        }
    }
    ToolNote(hgText("h.krisen.note", Fmt.euro(KRISEN_BETRAG, 0)))
}
