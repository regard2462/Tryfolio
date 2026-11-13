package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import de.troetenrepublik.app.engine.BewegungsSkala
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Zahlenreaktion
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Quartalsblick
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.ohneAbbruch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val NY: ZoneId = ZoneId.of("America/New_York")

// Bewegung zu den Zahlen (0.76, 0.77): Ergebnis gross, eine gemeinsame Skala fuer uebliche Spanne und tatsaechliche
// (oder erwartete) Bewegung, die Gewinnzeile und die Abweichung der letzten Quartale als Saeulen. tag: Tag des Termins (Tage seit 1970), null = letzter Termin laut
// Nasdaq. vergangen: der Termin liegt hinter uns, dann gibt es die tatsaechliche Bewegung, vorher die erwartete.
// nach: Zahlen nach Boersenschluss (die Bewegung kommt am Folgetag). Ohne Daten bleibt der Abschnitt leer.
@Composable
internal fun ZahlenReaktionKarte(
    state: AppState,
    symbol: String,
    tag: Long?,
    nach: Boolean,
    vergangen: Boolean,
    verlauf: List<Quartalsblick.Quartal>,
    epsAbweichung: Double? = null,
    umsatzAbweichung: Double? = null,
    epsText: (Double) -> String = { Fmt.num(it, 2) },
    // Auf der Wertpapierseite bleibt das Ergebnis im Merker: Beim Zurueckscrollen wird nichts neu geholt und gerechnet
    merker: SeitenMerker? = null,
) {
    var ein by gemerkt<Zahlenreaktion.Einordnung?>(merker, "zr/$symbol/$tag/$vergangen/$nach") { null }
    LaunchedEffect(symbol, tag, vergangen, nach) {
        if (ein != null) return@LaunchedEffect
        val kerzen = state.zahlenKerzen(symbol)
        val zeile = ohneAbbruch { state.nasdaqUeberraschung(symbol) }.getOrNull().orEmpty()
        val zone = if ('.' in symbol) Quartalszahlen.platz(symbol)?.zone ?: NY else NY
        val enden = verlauf.mapNotNull { q -> runCatching { LocalDate.parse(q.ende).toEpochDay() }.getOrNull() }
        val reaktionen = Zahlenreaktion.reaktionen(kerzen, zeile.map { it.tag }, enden, zone)
        val heute = LocalDate.now(zone).toEpochDay()
        // Ohne Termin der letzte gemeldete, wenn er noch frisch ist
        val termin = tag ?: zeile.firstOrNull()?.tag?.takeIf { heute - it in 0..120 }
        var erwartet: Zahlenreaktion.Erwartet? = null
        if (!vergangen && termin != null) {
            val kette = ohneAbbruch { state.market.optionskette(symbol) }.getOrNull()
            if (kette != null) erwartet = Zahlenreaktion.erwartet(kette.first, kette.second, termin + if (nach) 1 else 0, System.currentTimeMillis() / 1000)
        }
        ein = Zahlenreaktion.einordnen(termin ?: heute, erwartet, reaktionen, vergangen || tag == null)
    }
    val e = ein ?: return
    val mitReihe = verlauf.count { it.ist != null && it.erwartet != null } >= 2
    if (e.erwartet == null && e.ueblich == null && e.tatsaechlich == null && !mitReihe) return

    MarktInfoKopf(S.zrTitel, S.zrInfo)
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    val tat = e.tatsaechlich
    // Ergebnis: tatsaechliche Bewegung, vor dem Termin die erwartete, sonst die uebliche
    val spitze = tat?.let { S.zrKursNach to Fmt.signedPct(it, 1) }
        ?: e.erwartet?.let { S.zrErwartet(it.art == Zahlenreaktion.Art.STRADDLE) to "±" + Fmt.pct(it.prozent, 1) }
        ?: e.ueblich?.let { S.zrUeblichBand.replaceFirstChar { c -> c.uppercase() } to "±" + Fmt.pct(it, 1) }
    if (spitze != null) {
        val farbe = tat?.let { if (it >= 0) signal.up else signal.down } ?: MaterialTheme.colorScheme.onSurface
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(spitze.first, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(spitze.second, style = MaterialTheme.typography.headlineSmall, color = farbe, maxLines = 1, softWrap = false)
            }
            if (e.ueblich != null && (tat != null || e.erwartet != null)) {
                Text(S.zrUeblichZeile("±" + Fmt.pct(e.ueblich, 1)), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
            }
        }
    }
    val breit = max(e.ueblich ?: 0.0, e.erwartet?.prozent ?: 0.0)
    if (breit > 0.0) {
        BewegungsAchse(BewegungsSkala.skala(breit, tat), e.ueblich, e.erwartet?.prozent, tat)
    }
    // Gewinn gegen Schaetzung und Umsatz
    val letztes = verlauf.lastOrNull { it.ist != null && it.erwartet != null }
    if (epsAbweichung != null) {
        if (letztes != null) EpsZeile(epsText(letztes.ist!!), epsText(letztes.erwartet!!), epsAbweichung)
        else KeyValueRow(S.zrEpsUeberraschung, Fmt.signedPct(epsAbweichung, 1), valueColor = abweichungsFarbe(epsAbweichung))
    }
    umsatzAbweichung?.let { KeyValueRow(S.zrUmsatzUeberraschung, Fmt.signedPct(it, 1), valueColor = abweichungsFarbe(it)) }
    if (mitReihe) EpsSaeulen(verlauf.filter { it.ist != null && it.erwartet != null && it.erwartet != 0.0 }.takeLast(6), epsText)
}

@Composable
private fun abweichungsFarbe(p: Double): Color? = when {
    p > de.troetenrepublik.app.engine.Gewinnueberraschung.SCHWELLE -> LocalSignal.current.up
    p < -de.troetenrepublik.app.engine.Gewinnueberraschung.SCHWELLE -> LocalSignal.current.down
    else -> null
}

// Gemeinsame Achse: 0 in der Mitte, graues Band fuer die uebliche Spanne (Umriss fuer die eingepreiste), Marke fuer die
// tatsaechliche Bewegung. Beschriftung am Band (unten) und an der Marke (oben). Hinter dem Band gestrichelt, wenn gestaucht.
@Composable
private fun BewegungsAchse(skala: BewegungsSkala.Skala, ueblich: Double?, erwartet: Double?, marke: Double?) {
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.outline
    val stil = MaterialTheme.typography.bodySmall.copy(color = grau)
    val markeStil = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val signal = LocalSignal.current
    val markeFarbe = if ((marke ?: 0.0) >= 0) signal.up else signal.down
    val bandText = S.zrUeblichBand
    val erwartetText = S.zrEingepreistBand
    val markeText = marke?.let { Fmt.signedPct(it, 1) }
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(72.dp)) {
        val rand = 8.dp.toPx()
        val breite = size.width - 2 * rand
        val mitte = size.width / 2f
        val y = 34.dp.toPx()
        fun x(w: Double) = mitte + (breite / 2f) * skala.lage(w).toFloat()
        // Achse: innerhalb des Bands durchgezogen, dahinter gestrichelt, wenn gestaucht
        val bandL = x(-skala.band)
        val bandR = x(skala.band)
        val strich = 1.5.dp.toPx()
        if (skala.gestaucht) {
            val gestrichelt = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawLine(linie, Offset(rand, y), Offset(bandL, y), strokeWidth = strich, pathEffect = gestrichelt)
            drawLine(linie, Offset(bandR, y), Offset(size.width - rand, y), strokeWidth = strich, pathEffect = gestrichelt)
            drawLine(linie, Offset(bandL, y), Offset(bandR, y), strokeWidth = strich)
        } else {
            drawLine(linie, Offset(rand, y), Offset(size.width - rand, y), strokeWidth = strich)
        }
        // Band der ueblichen Spanne
        val bh = 18.dp.toPx()
        ueblich?.let { u ->
            val l = x(-u)
            val r = x(u)
            drawRoundRect(grau.copy(alpha = 0.35f), Offset(l, y - bh / 2), androidx.compose.ui.geometry.Size(r - l, bh), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        }
        erwartet?.let { u ->
            val l = x(-u)
            val r = x(u)
            drawRoundRect(grau, Offset(l, y - bh / 2), androidx.compose.ui.geometry.Size(r - l, bh), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()))
        }
        // Nullmarke
        drawLine(grau, Offset(mitte, y - 12.dp.toPx()), Offset(mitte, y + 12.dp.toPx()), strokeWidth = 1.dp.toPx())
        // Beschriftung unter dem Band und, bei eingepreister Spanne ohne uebliche, ebenfalls
        val unten = messer.measure(if (ueblich != null) bandText else erwartetText, stil)
        drawText(unten, topLeft = Offset(mitte - unten.size.width / 2f, y + bh / 2 + 6.dp.toPx()))
        // Zweite Spanne (eingepreist neben ueblich) oben
        if (ueblich != null && erwartet != null) {
            val oben = messer.measure(erwartetText, stil)
            drawText(oben, topLeft = Offset(mitte - oben.size.width / 2f, y - bh / 2 - 6.dp.toPx() - oben.size.height))
        }
        // Marke mit Wert darueber
        if (marke != null && markeText != null) {
            val mx = x(marke)
            drawLine(markeFarbe, Offset(mx, y - 16.dp.toPx()), Offset(mx, y + 16.dp.toPx()), strokeWidth = 3.dp.toPx())
            drawCircle(markeFarbe, radius = 7.dp.toPx(), center = Offset(mx, y))
            val t = messer.measure(markeText, markeStil)
            drawText(t, topLeft = Offset((mx - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), y - 16.dp.toPx() - 4.dp.toPx() - t.size.height))
        }
    }
}

// Gewinn je Aktie links, rechts Ist statt Schaetzung und die Abweichung; passt es nicht nebeneinander, bricht die Bezeichnung um
@Composable
private fun EpsZeile(ist: String, schaetzung: String, abweichung: Double) {
    val stil = MaterialTheme.typography.bodyMedium
    val messer = rememberTextMeasurer()
    val statt = S.zrEpsStatt(ist, schaetzung)
    val abw = Fmt.signedPct(abweichung, 1)
    val farbe = abweichungsFarbe(abweichung) ?: MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        val breite = constraints.maxWidth
        val abstand = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
        val art = remember(statt, abw, breite, stil) {
            fun w(t: String) = messer.measure(t, stil, maxLines = 1, softWrap = false).size.width
            de.troetenrepublik.app.engine.ZeilenLayout.art(w(S.zrEpsReihe), w(statt) + abstand / 2 + w(abw), breite, abstand)
        }
        val nebeneinander = art != de.troetenrepublik.app.engine.ZeilenLayout.Art.WERT_DARUNTER
        val eineZeile = art == de.troetenrepublik.app.engine.ZeilenLayout.Art.NEBEN
        val wert = @Composable {
            Text(statt, style = stil, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Text(abw, style = stil, color = farbe, maxLines = 1, softWrap = false)
        }
        if (nebeneinander) Row(Modifier.fillMaxWidth(), verticalAlignment = if (eineZeile) Alignment.CenterVertically else Alignment.Top) {
            Text(S.zrEpsReihe, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (eineZeile) 1 else 3, softWrap = !eineZeile, modifier = Modifier.weight(1f).padding(end = 12.dp))
            wert()
        } else Column(Modifier.fillMaxWidth()) {
            Text(S.zrEpsReihe, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { wert() }
        }
    }
}

// Abweichung des Gewinns vom erwarteten je Quartal als Saeulen: gruen ueber, rot unter der Nulllinie, Prozent an der Saeule,
// Quartal darunter. Ein Tipp auf eine Saeule zeigt Ist und Schaetzung des Quartals.
@Composable
private fun EpsSaeulen(q: List<Quartalsblick.Quartal>, epsText: (Double) -> String) {
    if (q.size < 2) return
    val hoch = LocalSignal.current.up
    val tief = LocalSignal.current.down
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.outline
    val messer = rememberTextMeasurer()
    val stil = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val abw = q.map { BewegungsSkala.abweichung(it.ist!!, it.erwartet!!) ?: 0.0 }
    val texte = abw.map { Fmt.signedPct(it, if (abs(it) < 10) 1 else 0) }
    var gewaehlt by remember(q) { mutableStateOf<Int?>(null) }
    val hi = max(abw.max(), 0.0)
    val lo = min(abw.min(), 0.0)
    val spanne = max(hi - lo, 1.0)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(116.dp).padding(top = 4.dp)
                .pointerInput(q) { detectTapGestures { p -> val i = (p.x / (size.width / q.size)).toInt().coerceIn(0, q.size - 1); gewaehlt = if (gewaehlt == i) null else i } },
        ) {
            val n = q.size
            val slot = size.width / n
            val breite = min(slot * 0.5f, 28.dp.toPx())
            val oben = 18.dp.toPx()
            val unten = size.height - 18.dp.toPx()
            val null0 = oben + (unten - oben) * (hi / spanne).toFloat()
            drawLine(linie, Offset(0f, null0), Offset(size.width, null0), strokeWidth = 1.dp.toPx())
            abw.forEachIndexed { i, v ->
                val mitte = slot * (i + 0.5f)
                val h = ((unten - oben) * (abs(v) / spanne).toFloat()).coerceAtLeast(2.dp.toPx())
                val farbe = (when (de.troetenrepublik.app.engine.Gewinnueberraschung.urteil(v)) {
                    de.troetenrepublik.app.engine.Gewinnueberraschung.Urteil.UEBERTROFFEN -> hoch
                    de.troetenrepublik.app.engine.Gewinnueberraschung.Urteil.VERFEHLT -> tief
                    de.troetenrepublik.app.engine.Gewinnueberraschung.Urteil.WIE_ERWARTET -> grau
                }).copy(alpha = if (gewaehlt == null || gewaehlt == i) 1f else 0.45f)
                val top = if (v >= 0) null0 - h else null0
                drawRoundRect(farbe, Offset(mitte - breite / 2, top), androidx.compose.ui.geometry.Size(breite, h), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                val t = messer.measure(texte[i], stil)
                val ty = if (v >= 0) top - t.size.height - 2.dp.toPx() else top + h + 2.dp.toPx()
                drawText(t, topLeft = Offset((mitte - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), ty))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            q.forEach { p ->
                val ende = runCatching { LocalDate.parse(p.ende) }.getOrNull()
                Text(
                    ende?.let { z -> z.monthValue.toString().padStart(2, '0') + "/" + (z.year % 100).toString().padStart(2, '0') } ?: p.ende,
                    style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f), maxLines = 1, softWrap = false,
                )
            }
        }
    }
    gewaehlt?.let { i ->
        val p = q[i]
        val ende = runCatching { LocalDate.parse(p.ende) }.getOrNull()
        val titel = ende?.let { z -> z.monthValue.toString().padStart(2, '0') + "/" + (z.year % 100).toString().padStart(2, '0') } ?: p.ende
        KeyValueRow(titel, S.zrSaeule(epsText(p.ist!!), epsText(p.erwartet!!)))
    }
}

// Tag eines Zeitpunkts in New York
internal fun nyTagVon(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(NY).toLocalDate().toEpochDay()

// Stunde eines Zeitpunkts in New York
internal fun nyStundeVon(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(NY).hour
