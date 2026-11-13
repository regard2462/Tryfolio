package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Alter
import de.troetenrepublik.app.engine.IndexAbstand
import de.troetenrepublik.app.i18n.AbstandTexte
import de.troetenrepublik.app.i18n.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.math.abs

// Abstand zum Index (0.78): was US-Indexfonds der SEC je Geschaeftsjahr melden (engine/IndexAbstand.kt). Auf der Seite
// eines US-ETF die Kernzahl mit Balken je Jahr und dem Vergleich mit den Kosten, im Werkzeug „Markt schlagen“ die
// Verteilung ueber alle Indexfonds.

private fun at(key: String, vararg w: String): String = AbstandTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Prozentpunkte mit Vorzeichen, zwei Nachkommastellen
private fun punkte(v: Double): String = (if (v > 0.004) "+" else if (v < -0.004) "−" else "") + Fmt.num(abs(v), 2)

// Die Datei einmal gelesen, gilt fuer alle Seiten
internal object AbstandLader {
    @Volatile private var stand: IndexAbstand.Datei? = null
    @Volatile private var versucht = false

    fun gespeichert(): IndexAbstand.Datei? = stand

    suspend fun laden(): IndexAbstand.Datei? {
        if (versucht) return stand
        val d = withContext(Dispatchers.Default) {
            runCatching { IndexAbstand.lesen(de.troetenrepublik.app.market.Daten.text(IndexAbstand.DATEI)) }.getOrNull()
        }
        stand = d
        versucht = true
        return d
    }
}

// Abschnitt auf der Seite eines ETF. Ohne Meldung zu diesem Kuerzel bleibt er weg.
@Composable
internal fun EtfAbstandKarte(state: AppState, symbol: String) {
    var datei by remember { mutableStateOf(AbstandLader.gespeichert()) }
    LaunchedEffect(Unit) { if (datei == null) datei = AbstandLader.laden() }
    val d = datei ?: return
    val f = remember(d, symbol) { IndexAbstand.fuer(d, symbol) } ?: return
    val kern = remember(f) { IndexAbstand.kern(f) }
    val voll = remember(d) { IndexAbstand.vollesJahr(d) }
    var ter by remember(symbol) { mutableStateOf<Double?>(null) }
    LaunchedEffect(symbol) { ter = runCatching { state.etfBestand(symbol)?.ter }.getOrNull() }
    var info by remember { mutableStateOf(false) }
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun farbe(v: Double) = if (v < -0.004) signal.down else if (v > 0.004) signal.up else grau

    QuellenMelden(QuelleLink(at("quelle"), IndexAbstand.ADRESSE))
    SectionHeader(at("titel"), onClick = { info = true })
    Column(Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp)) {
        Text(at("kern", punkte(kern.mittel)), style = MaterialTheme.typography.headlineSmall, color = farbe(kern.mittel), maxLines = 1, softWrap = false)
        Text(
            if (kern.jahre > 1) at("kern.unter", kern.von.toString(), kern.bis.toString()) else at("kern.unter.ein", kern.bis.toString()),
            style = MaterialTheme.typography.bodySmall, color = grau,
        )
    }
    if (f.jahre.size > 1) AbstandBalken(f.jahre.map { it.jahr to it.nach }, at("balken"), ::farbe)
    kern.mittelVor?.let { KeyValueRow(at("vor"), at("punkte", punkte(it))) }
    kern.kosten?.let { KeyValueRow(at("kosten"), at("punkte", Fmt.num(it, 2))) }
    ter?.takeIf { it > 0 }?.let { KeyValueRow(at("ter"), Fmt.pct(it, 2)) }
    KeyValueRow(at("median", voll.jahr.toString()), at("punkte", punkte(voll.median)))
    if (info) {
        val letztes = Fmt.monthShortYear(YearMonth.of(kern.bis, f.monat))
        val stand = Alter.tag(d.stand)?.let { Fmt.date(it * 86_400_000L) } ?: d.stand
        MarktInfoBlatt(at("titel"), at("info", letztes, f.name, stand)) { info = false }
    }
}

// Balken je Geschaeftsjahr mit dem Wert am Balken und dem Jahr darunter. Der Massstab richtet sich nach dem groessten
// Betrag; ein einzelner Ausreisser (mehr als das Sechsfache des mittleren Betrags) wird gekappt und traegt seinen Wert.
@Composable
private fun AbstandBalken(reihe: List<Pair<Int, Double>>, beschreibung: String, farbeFuer: (Double) -> Color) {
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.outlineVariant
    val schrift = MaterialTheme.colorScheme.onSurface
    val kappMarke = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 4.dp)) {
        Text(beschreibung, style = MaterialTheme.typography.labelLarge, color = grau)
        Canvas(Modifier.fillMaxWidth().height(128.dp).padding(top = 4.dp).semantics { contentDescription = beschreibung }) {
            val d = density
            val n = reihe.size
            val rand = 14f * d // Platz fuer den Wert ueber und unter dem Balken
            val unten = 16f * d
            val betraege = reihe.map { abs(it.second) }.sorted()
            val kappe = maxOf(betraege[betraege.size / 2] * 6.0, 0.05)
            val hi = maxOf(reihe.maxOf { it.second }.coerceAtMost(kappe), 0.0)
            val lo = minOf(reihe.minOf { it.second }.coerceAtLeast(-kappe), 0.0)
            val spanne = (hi - lo).coerceAtLeast(0.02)
            val h = size.height - unten - 2 * rand
            fun y(v: Double) = rand + h * (1f - ((v.coerceIn(lo, hi) - lo) / spanne).toFloat())
            val y0 = y(0.0)
            val breite = size.width / n
            val balken = minOf(breite * 0.64f, 34f * d)
            val jahrStil = TextStyle(fontSize = 10.sp, color = grau)
            val wertStil = TextStyle(fontSize = 10.sp, color = schrift)
            drawLine(linie, Offset(0f, y0), Offset(size.width, y0), strokeWidth = 1f * d)
            // Werte nur, wenn der breiteste in seine Spalte passt
            val werte = reihe.map { messer.measure(punkte(it.second), wertStil, maxLines = 1, softWrap = false) }
            val mitWert = werte.maxOf { it.size.width } <= breite - 2f * d
            for (i in 0 until n) {
                val (jahr, v) = reihe[i]
                val x = i * breite + (breite - balken) / 2
                val spitze = y(v)
                val oberkante = minOf(spitze, y0)
                val hoehe = maxOf(abs(spitze - y0), 1.5f * d)
                drawRoundRect(farbeFuer(v), Offset(x, oberkante), Size(balken, hoehe), CornerRadius(1.5f * d))
                if (v > hi || v < lo) {
                    val ky = if (v > hi) spitze + 5f * d else spitze - 5f * d
                    drawLine(kappMarke, Offset(x, ky), Offset(x + balken, ky), strokeWidth = 1.5f * d)
                }
                if (mitWert) {
                    val t = werte[i]
                    val ty = if (v >= 0) oberkante - t.size.height - 1f * d else oberkante + hoehe + 1f * d
                    drawText(t, topLeft = Offset(i * breite + (breite - t.size.width) / 2, ty))
                }
                val j = messer.measure(if (n > 6) (jahr % 100).toString().padStart(2, '0') else jahr.toString(), jahrStil, maxLines = 1, softWrap = false)
                drawText(j, topLeft = Offset(i * breite + (breite - j.size.width) / 2, size.height - unten + 3f * d))
            }
        }
    }
}

// Baustein im Werkzeug „Markt schlagen“: Anteil der Indexfonds hinter ihrem Index und die Verteilung des Abstands
// im juengsten vollstaendigen Jahr. Ohne Datei bleibt er weg.
@Composable
internal fun IndexAbstandBaustein() {
    var datei by remember { mutableStateOf(AbstandLader.gespeichert()) }
    LaunchedEffect(Unit) { if (datei == null) datei = AbstandLader.laden() }
    val d = datei ?: return
    val v = remember(d) { IndexAbstand.vollesJahr(d) }
    val anteil = remember(d, v) { IndexAbstand.anteilDahinter(d, v) }
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(at("tool.titel"), style = MaterialTheme.typography.titleSmall)
        Text(Fmt.pct(anteil * 100, 0), style = MaterialTheme.typography.headlineSmall, color = signal.down, maxLines = 1, softWrap = false)
        Text(
            at("tool.satz", v.jahr.toString(), Fmt.num(v.anzahl.toDouble(), 0), punkte(v.median), v.medianVor?.let { punkte(it) } ?: "0"),
            style = MaterialTheme.typography.bodySmall, color = grau,
        )
        Spacer(Modifier.height(10.dp))
        AbstandVerteilung(d.grenzen, v, signal.down, Kategorie.blau)
        Text(at("tool.achse"), style = MaterialTheme.typography.labelSmall, color = grau)
    }
    Trennlinie()
}

// Quelle fuer den Anhang des Werkzeugs; null ohne Datei
internal fun indexAbstandQuelle(): QuelleLink? = AbstandLader.gespeichert()?.let { QuelleLink(at("quelle"), IndexAbstand.ADRESSE) }

// Saeulen je Klasse des Abstands, Anteil der Fonds ueber der Saeule, die Klassengrenzen an den Saeulenkanten darunter.
// Klassen unter null in der Farbe „dahinter“, die uebrigen in der Bezugsfarbe.
@Composable
private fun AbstandVerteilung(grenzen: List<Double>, v: IndexAbstand.Verteilung, dahinter: Color, davor: Color) {
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val schrift = MaterialTheme.colorScheme.onSurface
    val beschreibung = at("tool.bild")
    Canvas(Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = beschreibung }) {
        val d = density
        val n = v.klassen.size
        val oben = 14f * d
        val unten = 16f * d
        val h = size.height - oben - unten
        val max = v.klassen.max().coerceAtLeast(1)
        val breite = size.width / n
        val klein = TextStyle(fontSize = 9.sp, color = grau)
        val wertStil = TextStyle(fontSize = 9.sp, color = schrift)
        for (i in 0 until n) {
            val hoehe = maxOf(h * v.klassen[i] / max, 1.5f * d)
            val farbe = if (i < grenzen.size && grenzen[i] <= 0.0) dahinter else davor
            drawRoundRect(farbe, Offset(i * breite + 1.5f * d, oben + h - hoehe), Size(breite - 3f * d, hoehe), CornerRadius(1.5f * d))
            val t = messer.measure(Fmt.pct(v.klassen[i] * 100.0 / v.anzahl, 0), wertStil, maxLines = 1, softWrap = false)
            if (t.size.width <= breite) drawText(t, topLeft = Offset(i * breite + (breite - t.size.width) / 2, oben + h - hoehe - t.size.height - 1f * d))
        }
        drawLine(grau.copy(alpha = 0.6f), Offset(0f, oben + h), Offset(size.width, oben + h), strokeWidth = 1f * d)
        var letzte = -1e9f
        for (i in grenzen.indices) {
            val g = grenzen[i]
            val text = (if (g < 0) "−" else "") + Fmt.num(abs(g), if (abs(g) >= 1 || g == 0.0) 0 else if (abs(g * 10 - Math.round(g * 10)) < 1e-9) 1 else 2)
            val t = messer.measure(text, klein, maxLines = 1, softWrap = false)
            val x = ((i + 1) * breite - t.size.width / 2)
            if (x - letzte > 2f * d) { drawText(t, topLeft = Offset(x, oben + h + 3f * d)); letzte = x + t.size.width }
        }
    }
}
