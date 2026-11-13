package de.troetenrepublik.app.ui

import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Handelsstil
import de.troetenrepublik.app.engine.Intraday
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.i18n.IntradayTexte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private fun tx(key: String, vararg w: String) = IntradayTexte.t(key, *w)

// Titel und Unterzeile der Zeile auf der Wertpapierseite
internal val intradayTitel: String get() = tx("titel")
internal val intradayUnter: String get() = tx("unter")

// Ziel als Zahl ohne unnoetige Nachkommastelle
private fun zielText(v: Double): String = Fmt.num(v, if (v % 1.0 == 0.0) 0 else 1)

// Geladene Handelstage eines Werts; tage leer ohne Handelsplatz oder ohne Kerzen.
private class IntradayDaten(val tage: List<Intraday.Tag>, val platz: Intraday.Platz?, val ohneNetz: Boolean)

private val STOPP_FEST = listOf(0.005, 0.01, 0.02)
private val ZIELE = listOf(1.0, 2.0, 3.0)

// Intraday-Rueckblick (0.78): Ausbruch aus der Eroeffnungsspanne ueber die verfuegbaren Handelstage und der Rest des
// Tages als Wiederholung vergangener 5-Minuten-Kerzen. Kosten wie im Strategietest.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntradaySheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var daten by remember(symbol) { mutableStateOf<IntradayDaten?>(null) }
    var versuch by remember(symbol) { mutableIntStateOf(0) }
    LaunchedEffect(symbol, versuch) {
        daten = null
        val platz = Intraday.platz(state.market.dataSymbol(symbol))
        val kerzen = if (platz == null) emptyList() else runCatching { state.market.intraday5m(symbol) }.getOrDefault(emptyList())
        val tage = if (platz == null) emptyList() else withContext(Dispatchers.Default) { Intraday.tage(kerzen, platz, System.currentTimeMillis()) }
        daten = IntradayDaten(tage, platz, platz != null && kerzen.isEmpty() && state.market.ohneVerbindung())
    }
    val kosten = StrategieLabor.Kosten(settings.spreadStockBps.toDouble())
    // Ziel in Vielfachen des Risikos, fuer beide Teile
    var ziel by remember { mutableDoubleStateOf(2.0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Die Strategie in einem Satz steht hinter dem Info-Symbol am Titel
            var stratInfo by remember { mutableStateOf(false) }
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tx("titel"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                androidx.compose.material3.IconButton(onClick = { stratInfo = true }) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            if (stratInfo) MarktInfoBlatt(tx("titel"), tx("strat.info")) { stratInfo = false }
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            val d = daten
            when {
                d == null -> ToolNote(S.toolLoading)
                d.ohneNetz -> EmptyHint(S.ohneVerbindung, S.erneutVersuchen to { versuch++ })
                d.platz == null || d.tage.size < Intraday.MIN_TAGE -> ToolNote(tx("zuwenig"))
                else -> {
                    Text(
                        tx("tage", d.tage.size.toString(), Fmt.date(d.tage.last().offen)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp),
                    )
                    AusbruchTeil(d.tage, kosten, ziel) { ziel = it }
                    RestTeil(d.tage, d.platz, kosten, ziel)
                }
            }
        }
    }
}

// Beschriftete Reihe von Auswahlpillen
@Composable
private fun <T> WahlReihe(titel: String, werte: List<T>, gewaehlt: T, name: (T) -> String, info: Pair<String, String>? = null, onWahl: (T) -> Unit) {
    // Mit info steht hinter der Bezeichnung das Info-Symbol (Titel und Erklaerung des Blatts)
    if (info != null) InfoZeile(titel, info.first, info.second, Modifier.padding(top = 4.dp))
    else Text(
        titel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 4.dp),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { werte.forEach { w -> Chip(name(w), w == gewaehlt) { onWahl(w) } } }
}

// Kernzahl mit einem Satz darunter
@Composable
private fun Kern(titel: String, zahl: String, farbe: Color, satz: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp)) {
        Text(titel, style = MaterialTheme.typography.titleSmall)
        Text(zahl, style = MaterialTheme.typography.headlineSmall, color = farbe, maxLines = 1, softWrap = false)
        if (satz != null) Text(satz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AusbruchTeil(tage: List<Intraday.Tag>, kosten: StrategieLabor.Kosten, ziel: Double, onZiel: (Double) -> Unit) {
    val signal = LocalSignal.current
    var spanne by remember { mutableIntStateOf(15) }
    // Stopp: Spannenmitte, Gegenseite der Spanne oder fester Abstand (als Anteil)
    var stopp by remember { mutableStateOf<Pair<Intraday.Stopp, Double>>(Intraday.Stopp.MITTE to 0.005) }
    var short by remember { mutableStateOf(false) }
    val regel = Intraday.Regel(spanneMin = spanne, stopp = stopp.first, stoppAnteil = stopp.second, ziel = ziel, short = short)
    val e = remember(tage, regel, kosten) { Intraday.pruefen(tage, regel, kosten) } ?: return
    val n = e.geschaefte.size
    val neutral = MaterialTheme.colorScheme.onSurface
    fun farbe(v: Double) = when (Intraday.vorzeichen(v)) { 1 -> signal.up; -1 -> signal.down; else -> neutral }

    Kern(
        tx("orb.titel"), if (n == 0) tx("orb.keine") else tx("orb.kern", Fmt.signedPct(e.mittel * 100, 2)), if (n == 0) MaterialTheme.colorScheme.onSurface else farbe(e.mittel),
        if (n == 0) null else tx("orb.satz", n.toString(), e.tage.toString()),
    )
    // Aussagekraft nach den Grenzen des Strategietests: bei wenigen Geschaeften eine kurze Zeile, ein Tipp erklaert sie
    var info by remember { mutableStateOf(false) }
    if (e.aussage != StrategieLabor.Aussage.BRAUCHBAR) Text(
        tx("orb.unsicher"), style = MaterialTheme.typography.bodySmall, color = signal.warn,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { info = true }.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    if (info) MarktInfoBlatt(S.slAussage, S.slAussageText(n, Fmt.num(e.jahre, 1))) { info = false }

    if (n > 0) {
        TrefferSkala(e.trefferquote, e.noetig)
        Text(
            tx("orb.balken"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
        )
        GeschaeftBalken(e.geschaefte.map { it.ergebnis })
    }

    WahlReihe(tx("wahl.spanne"), listOf(5, 15, 30), spanne, { tx("wahl.min", it.toString()) }) { spanne = it }
    WahlReihe(
        tx("wahl.stopp"), listOf(Intraday.Stopp.MITTE to 0.005, Intraday.Stopp.GEGENSEITE to 0.005) + STOPP_FEST.map { Intraday.Stopp.PROZENT to it }, stopp,
        { (art, wert) -> if (art == Intraday.Stopp.PROZENT) Fmt.pct(wert * 100, 1) else tx("stopp." + art.name) },
    ) { stopp = it }
    WahlReihe(tx("wahl.ziel"), ZIELE, ziel, { tx("wahl.r", zielText(it)) }, info = tx("r.titel") to tx("r.info"), onWahl = onZiel)
    WahlReihe(tx("wahl.richtung"), listOf(false, true), short, { if (it) tx("wahl.beide") else tx("wahl.long") }) { short = it }
    Spacer(Modifier.height(8.dp))

    if (n > 0) {
        EndenBalken(
            e.geschaefte.count { it.ende == Intraday.Ende.ZIEL }, e.geschaefte.count { it.ende == Intraday.Ende.STOPP },
            e.geschaefte.count { it.ende == Intraday.Ende.SCHLUSS },
        )
    }
    KeyValueRow(tx("z.summe"), Fmt.signedPct(e.summe * 100, 1), valueColor = farbe(e.summe), hint = tx("z.einsatz"))
    KeyValueRow(tx("z.morgens"), Fmt.signedPct(e.morgensAbends * 100, 1), valueColor = farbe(e.morgensAbends))
    KeyValueRow(tx("z.halten"), Fmt.signedPct(e.halten * 100, 1), valueColor = farbe(e.halten))
    KeyValueRow(S.st("hs.zeile"), S.st("hs." + Handelsstil.DAYTRADING.name), hint = S.st("hs.dauer." + Handelsstil.DAYTRADING.name))
    HinweisInfo(tx("orb.note", Fmt.pct(kosten.spreadBps / 200.0, 2)))
}

// Trefferquote als Skala von 0 bis 100 Prozent: Balken bis zur Quote, Marke bei der noetigen Quote (ab da lohnt sich die Regel)
@Composable
private fun TrefferSkala(quote: Double, noetig: Double) {
    val signal = LocalSignal.current
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val strich = MaterialTheme.colorScheme.onSurface
    val farbe = if (quote >= noetig) signal.up else signal.down
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tx("z.treffer"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(Fmt.pct(quote * 100, 0), style = MaterialTheme.typography.titleSmall, color = farbe, maxLines = 1, softWrap = false)
        }
        Canvas(Modifier.fillMaxWidth().height(20.dp).padding(vertical = 3.dp)) {
            val h = 8.dp.toPx()
            val oben = (size.height - h) / 2
            drawRoundRect(bahn, Offset(0f, oben), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(farbe, Offset(0f, oben), Size((size.width * quote.toFloat().coerceIn(0f, 1f)).coerceAtLeast(h), h), CornerRadius(h / 2))
            val x = size.width * noetig.toFloat().coerceIn(0f, 1f)
            drawLine(strich, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
        Text(tx("z.noetigKurz", Fmt.pct(noetig * 100, 0)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Wie die Geschaefte endeten: dreiteiliger Balken (Ziel gruen, Stopp rot, Schluss grau), darunter je Teil Name und Anzahl
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndenBalken(ziel: Int, stopp: Int, schluss: Int) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.outline
    val teile = listOf(Triple(tx("enden.ziel"), ziel, signal.up), Triple(tx("enden.stopp"), stopp, signal.down), Triple(tx("enden.schluss"), schluss, grau))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(tx("z.enden"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            teile.forEach { (_, n, farbe) -> if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(farbe)) }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            teile.forEach { (name, n, farbe) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
                    Spacer(Modifier.width(6.dp))
                    Text(name + "\u00A0" + n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// Ergebnis je Geschaeft in zeitlicher Folge als Balken um die Nulllinie
@Composable
private fun GeschaeftBalken(werte: List<Double>) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 20.dp, vertical = 6.dp)) {
        val oben = max(werte.maxOf { it }, 0.0)
        val unten = min(werte.minOf { it }, 0.0)
        val hoehe = (oben - unten).takeIf { it > 0 } ?: return@Canvas
        val schritt = min(size.width / werte.size, 14.dp.toPx())
        val breite = max(schritt * 0.7f, 1f)
        val null_ = size.height * (oben / hoehe).toFloat()
        werte.forEachIndexed { i, v ->
            val h = max(size.height * (abs(v) / hoehe).toFloat(), 1.dp.toPx())
            drawRect(if (v >= 0) signal.up else signal.down, Offset(i * schritt, if (v >= 0) null_ - h else null_), Size(breite, h))
        }
        drawLine(grau.copy(alpha = 0.6f), Offset(0f, null_), Offset(size.width, null_), strokeWidth = 1.dp.toPx())
    }
}

// Stand der Wiederholung: waehrend der Rechnung bleibt das letzte Ergebnis stehen
private class RestStand(val fertig: Boolean, val rest: Intraday.Rest?)

@Composable
private fun RestTeil(tage: List<Intraday.Tag>, platz: Intraday.Platz, kosten: StrategieLabor.Kosten, ziel: Double) {
    val signal = LocalSignal.current
    val letzter = tage.last()
    // Einstieg als Kerze ab Eroeffnung; -1 steht fuer jetzt (nur waehrend der Sitzung)
    val jetzt = remember(platz) { Intraday.schrittJetzt(platz, System.currentTimeMillis()) }
    val zeiten = remember(letzter, jetzt) {
        listOfNotNull(jetzt?.let { -1 }) + (listOf(3) + (12 until letzter.schritte - 12 step 12)).distinct()
    }
    var wahl by remember(zeiten) { mutableIntStateOf(if (jetzt != null) -1 else zeiten.firstOrNull { it >= 12 } ?: zeiten.first()) }
    var stoppAnteil by remember { mutableDoubleStateOf(0.01) }
    val ab = if (wahl == -1) jetzt ?: 0 else wahl
    val stand by produceState(RestStand(false, null), tage, ab, stoppAnteil, ziel, kosten) {
        value = RestStand(false, value.rest)
        value = RestStand(true, withContext(Dispatchers.Default) { Intraday.rest(tage, ab, stoppAnteil, ziel, kosten, abbruch = { ensureActive() }) })
    }
    val r = stand.rest
    val neutral = MaterialTheme.colorScheme.onSurface
    fun farbe(v: Double) = when (Intraday.vorzeichen(v)) { 1 -> signal.up; -1 -> signal.down; else -> neutral }

    when {
        r != null -> Kern(
            tx("rest.titel"), tx("rest.kern", Fmt.pct(r.ziel * 100, 0)), if (r.ziel >= r.noetig) signal.up else signal.down,
            tx("rest.satz", Fmt.pct(r.noetig * 100, 0), zielText(ziel), Fmt.signedPct(r.mittel * 100, 2)),
        )
        else -> Kern(tx("rest.titel"), if (stand.fertig) "–" else S.toolLoading, MaterialTheme.colorScheme.onSurface, if (stand.fertig) tx("rest.keine") else null)
    }
    Text(
        tx("rest.kennung"), style = MaterialTheme.typography.bodyMedium, color = signal.warn,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
    if (r != null) {
        AnteilBalken(r.ziel, r.offen, r.stopp, r.noetig)
        RestFaecher(r, 1 - stoppAnteil, 1 + ziel * stoppAnteil)
    }
    WahlReihe(tx("wahl.einstieg"), zeiten, wahl, { if (it == -1) tx("wahl.jetzt") else Fmt.time(letzter.offen + it * Intraday.KERZE_MS) }) { wahl = it }
    WahlReihe(tx("wahl.stopp"), STOPP_FEST, stoppAnteil, { Fmt.pct(it * 100, 1) }) { stoppAnteil = it }
    Spacer(Modifier.height(8.dp))
    if (r != null) {
        KeyValueRow(tx("rest.ziel"), Fmt.pct(r.ziel * 100, 0), valueColor = signal.up, hint = tx("z.noetig", Fmt.pct(r.noetig * 100, 0), zielText(ziel)))
        KeyValueRow(tx("rest.stopp"), Fmt.pct(r.stopp * 100, 0), valueColor = signal.down)
        KeyValueRow(tx("rest.offen"), Fmt.pct(r.offen * 100, 0))
        KeyValueRow(tx("rest.mittel"), Fmt.signedPct(r.mittel * 100, 2), valueColor = farbe(r.mittel))
        HinweisInfo(tx("rest.note", Fmt.num(r.laeufe.toDouble(), 0), r.tage.toString()))
    }
}

// Anteile Ziel, offen und Stopp als ein Balken; der Strich markiert die noetige Trefferquote
@Composable
private fun AnteilBalken(ziel: Double, offen: Double, stopp: Double, noetig: Double) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.surfaceContainerHighest
    val strich = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 20.dp, vertical = 4.dp)) {
        val h = 10.dp.toPx()
        val oben = (size.height - h) / 2
        val a = size.width * ziel.toFloat()
        val b = size.width * (ziel + offen).toFloat()
        drawRoundRect(grau, Offset(0f, oben), Size(size.width, h), CornerRadius(h / 2))
        if (ziel > 0) drawRoundRect(signal.up, Offset(0f, oben), Size(a, h), CornerRadius(h / 2))
        if (stopp > 0) drawRoundRect(signal.down, Offset(b, oben), Size(size.width - b, h), CornerRadius(h / 2))
        val x = size.width * noetig.toFloat()
        drawLine(strich, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

// Faecher der Pfade: mittlerer Pfad, darum die Haelfte und 90 % der Pfade; Ziel und Stopp als Linien mit Namen am Rand
@Composable
private fun RestFaecher(r: Intraday.Rest, stoppKurs: Double, zielKurs: Double) {
    val signal = LocalSignal.current
    val farbe = Kategorie.blau
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val messer = rememberTextMeasurer()
    val stil = MaterialTheme.typography.labelSmall
    val zielName = remember(stil, signal) { messer.measure(tx("graf.ziel"), stil.copy(color = signal.up), maxLines = 1, softWrap = false) }
    val stoppName = remember(stil, signal) { messer.measure(tx("graf.stopp"), stil.copy(color = signal.down), maxLines = 1, softWrap = false) }
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 20.dp, vertical = 8.dp)) {
        val rand = max(zielName.size.width, stoppName.size.width) + 6.dp.toPx()
        val breite = size.width - rand
        val luft = (zielKurs - stoppKurs) * 0.08
        val oben = zielKurs + luft
        val unten = stoppKurs - luft
        val n = r.faecher[0].size
        fun x(i: Int) = breite * i / (n - 1).coerceAtLeast(1)
        fun y(v: Double) = (size.height * (oben - v) / (oben - unten)).toFloat()
        fun band(a: DoubleArray, b: DoubleArray) = Path().apply {
            for (i in 0 until n) if (i == 0) moveTo(x(i), y(b[i])) else lineTo(x(i), y(b[i]))
            for (i in n - 1 downTo 0) lineTo(x(i), y(a[i]))
            close()
        }
        drawPath(band(r.faecher[0], r.faecher[4]), farbe.copy(alpha = 0.18f))
        drawPath(band(r.faecher[1], r.faecher[3]), farbe.copy(alpha = 0.32f))
        drawLine(grau.copy(alpha = 0.6f), Offset(0f, y(1.0)), Offset(breite, y(1.0)), strokeWidth = 1.dp.toPx())
        val mitte = Path().apply { for (i in 0 until n) if (i == 0) moveTo(x(i), y(r.faecher[2][i])) else lineTo(x(i), y(r.faecher[2][i])) }
        drawPath(mitte, farbe, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val gestrichelt = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        drawLine(signal.up, Offset(0f, y(zielKurs)), Offset(breite, y(zielKurs)), strokeWidth = 1.5.dp.toPx(), pathEffect = gestrichelt)
        drawLine(signal.down, Offset(0f, y(stoppKurs)), Offset(breite, y(stoppKurs)), strokeWidth = 1.5.dp.toPx(), pathEffect = gestrichelt)
        drawText(zielName, topLeft = Offset(breite + 6.dp.toPx(), y(zielKurs) - zielName.size.height / 2f))
        drawText(stoppName, topLeft = Offset(breite + 6.dp.toPx(), y(stoppKurs) - stoppName.size.height / 2f))
    }
}
