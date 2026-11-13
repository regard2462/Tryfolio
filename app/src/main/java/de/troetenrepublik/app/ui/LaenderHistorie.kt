package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Hauspreise
import de.troetenrepublik.app.engine.HistorieJst
import de.troetenrepublik.app.engine.KreditMessung
import de.troetenrepublik.app.engine.HistorieJst.Art
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// [Laender Historie 0.78] Laenderblatt: Renditen und Krisen seit 1870 (Jorda-Schularick-Taylor) und Hauspreise real seit
// 1975 (Dallas Fed). Je Abschnitt steht die Kernzeile immer da, Verlauf und Einzelheiten klappen auf.

// Beide Dateien einmal gelesen, gelten fuer alle Laender und fuer die Globus-Ebenen
internal object HistorieLader {
    class Stand(val jst: HistorieJst.Datei?, val haus: Hauspreise.Datei?)

    @Volatile private var stand: Stand? = null

    fun gespeichert(): Stand? = stand

    suspend fun laden(): Stand {
        stand?.let { return it }
        val s = withContext(Dispatchers.Default) {
            Stand(
                runCatching { HistorieJst.lesen(de.troetenrepublik.app.market.Daten.text(HistorieJst.DATEI)) }.getOrNull(),
                runCatching { Hauspreise.lesen(de.troetenrepublik.app.market.Daten.text(Hauspreise.DATEI)) }.getOrNull(),
            )
        }
        stand = s
        return s
    }
}

// Tag der Datei (JJJJ-MM-TT) als Datum der Anzeigesprache
private fun datumText(tag: String): String {
    val t = tag.split("-")
    return if (t.size == 3 && I18n.locale.language == "de") t[2] + "." + t[1] + "." + t[0] else tag
}

// Kopfzeile eines Abschnitts mit Aufklapppfeil; die Kernzeile darunter bleibt bei zugeklapptem Abschnitt stehen.
@Composable
internal fun HistorieKopf(titel: String, offen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 4.dp)
            .defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        AufklappPfeil(offen)
    }
}

// Abschnitt "Seit 1870": Kernzeile mit der realen Rendite je Jahr fuer Aktien, Anleihen und Immobilien, aufgeklappt der
// Verlauf des realen Vermoegens (logarithmisch) mit den Finanzkrisen als Marken, die Krisenjahre und die schlechtesten
// Zehnjahreszeitraeume der Aktien. Gilt fuer alle 18 Laender der Quelle.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LaenderHistorie(land: GlobusLand) {
    var st by remember { mutableStateOf(HistorieLader.gespeichert()) }
    LaunchedEffect(Unit) { if (st == null) st = HistorieLader.laden() }
    val d = st?.jst ?: return
    val l = d.laender[land.a3] ?: return
    var offen by rememberSaveable("hist-jst") { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farben = mapOf(
        Art.AKTIEN to MaterialTheme.colorScheme.primary, Art.ANLEIHEN to MaterialTheme.colorScheme.tertiary,
        Art.IMMOBILIEN to Kategorie.ocker, Art.ZINS to grau,
    )
    val namen = mapOf(Art.AKTIEN to S.histAktien, Art.ANLEIHEN to S.histAnleihen, Art.IMMOBILIEN to S.histImmobilien, Art.ZINS to S.histZins)
    val quelle = remember(d, I18n.locale) { quellenText(d) + (l.leer?.let { "\n\n" + S.histLueckeInfo(it.first, it.second) }.orEmpty()) }
    QuellenLeiste(listOf(LaenderQuellen.jst()))

    HistorieKopf(S.histTitel, offen) { offen = !offen }
    val kacheln = listOf(Art.AKTIEN, Art.ANLEIHEN, Art.IMMOBILIEN).mapNotNull { a ->
        val sp = HistorieJst.spanne(l, a) ?: return@mapNotNull null
        val titel = namen.getValue(a)
        val prozent = sp.rendite * 100.0
        KachelDaten(
            S.histReal(titel), Fmt.signedPct(prozent, 1), S.histJeJahrAb(sp.erstes), if (prozent >= 0) signal.up else signal.down,
        ) { info = S.histReal(titel) to S.histKernInfo + "\n\n" + quelle }
    }
    if (kacheln.isEmpty()) LbHinweis(S.histOhneRenditen) else KachelRaster(kacheln, 3)
    if (offen) {
        val linien = remember(l, farben, namen) {
            listOf(Art.AKTIEN, Art.ANLEIHEN, Art.IMMOBILIEN, Art.ZINS).mapNotNull { a ->
                val v = HistorieJst.vermoegen(l, a)
                if (v.size < 3) null else HistLinie(namen.getValue(a), v.map { it.first.toDouble() to it.second }, farben.getValue(a))
            }
        }
        if (linien.isNotEmpty()) {
            LbUnterkopf(S.histVermoegen)
            HistDiagramm(
                linien, 1870.0, (d.ende).toDouble(), log = true,
                marken = l.krisen.filter { it > 1870 }.map { it.toDouble() },
                achsenText = { "×" + Fmt.num(it, if (it < 1.0) 1 else 0) }, endeText = { "×" + Fmt.num(it, 0) },
                beschreibung = S.histVermoegen,
            )
        }
        if (l.krisen.isNotEmpty()) {
            LbUnterkopf(S.histKrisen(l.krisen.size))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (j in l.krisen) Text(j.toString(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
            }
            LbHinweis(S.histKrisenInfo)
        }
        val schlecht = remember(l) { HistorieJst.schlechteste(l, Art.AKTIEN, 3) }
        if (schlecht.isNotEmpty()) {
            LbUnterkopf(S.histSchlecht)
            for (z in schlecht) LbZeile(
                S.histZeitraum(z.von, z.bis), S.histJe(Fmt.signedPct(z.rendite * 100.0, 1)),
                if (z.rendite >= 0) signal.up else signal.down,
            ) { info = S.histSchlecht to S.histSchlechtInfo }
        }
        HistorieJst.spanne(l, Art.INFLATION)?.let { sp ->
            LbZeile(S.histInflation, Fmt.pct(sp.rendite * 100.0, 1)) { info = S.histInflation to S.histInflationInfo(sp.erstes) }
        }
        HistorieJst.letzterKredit(l)?.let { (jahr, v) ->
            LbZeile(S.histKredit(jahr), Fmt.pct(v, 0)) { info = S.histKredit(jahr) to S.histKreditInfo }
            // Aenderung in fuenf Jahren; der Infotext nennt, wie oft auf starke Anstiege eine Finanzkrise folgte
            val anstieg = remember(l, jahr) { KreditMessung.anstieg(l, jahr) }
            val bilanz = remember(d) { KreditMessung.bilanz(d) }
            if (anstieg != null && bilanz != null && bilanz.faelle > 0) {
                val titel = S.hrKreditAnstieg(KreditMessung.JAHRE)
                LbZeile(titel, S.hrPunkte((if (anstieg >= 0) "+" else "−") + Fmt.num(abs(anstieg), 0))) {
                    info = titel to S.hrKreditInfo(
                        Fmt.num(KreditMessung.ANSTIEG, 0), KreditMessung.JAHRE, bilanz.mitKrise, bilanz.faelle,
                        Fmt.num(bilanz.quote * 100, 0), Fmt.num(bilanz.grund * 100, 0), bilanz.laender, bilanz.von, bilanz.bis,
                    )
                }
            }
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Zitat und Lizenz der JST-Daten, wie die Quelle sie vorgibt
internal fun quellenText(d: HistorieJst.Datei): String =
    S.histZitatKopf + "\n" + d.zitat.joinToString("\n\n") + "\n\n" + S.histLizenzJst + "\n" + S.histAbruf(datumText(d.stand))

// Abschnitt "Hauspreise real seit 1975": Kernzeile mit der realen Veraenderung und dem Verhaeltnis Preis zu Einkommen gegen den
// eigenen Schnitt, aufgeklappt beide Verlaeufe. Nur Zahlen, kein Urteil.
@Composable
internal fun LaenderHauspreise(land: GlobusLand) {
    var st by remember { mutableStateOf(HistorieLader.gespeichert()) }
    LaunchedEffect(Unit) { if (st == null) st = HistorieLader.laden() }
    val d = st?.haus ?: return
    val l = d.laender[land.a3] ?: return
    var offen by rememberSaveable("hist-haus") { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val kern = remember(d, l) { Hauspreise.kern(d, l) }
    QuellenLeiste(listOf(LaenderQuellen.dallas()))
    val ab = kern.ab.first
    val abweichung = kern.abweichung.roundToInt()
    val infoText = S.hpInfo(S.hpQuartal(kern.bis.first, kern.bis.second)) + "\n\n" + hauspreiseQuelle(d)

    HistorieKopf(S.hpTitel(ab), offen) { offen = !offen }
    KachelRaster(
        listOf(
            KachelDaten(S.hpPreise, Fmt.signedPct(kern.aenderung, 0), S.hpSeit(ab)) { info = S.hpTitel(ab) to infoText },
            KachelDaten(S.hpVerhaeltnis, Fmt.pct(abs(kern.abweichung), 0), S.hpSchnitt(abweichung, ab)) { info = S.hpVerhaeltnis to infoText },
        ),
        2,
    )
    if (offen) {
        val preis = remember(l) { Hauspreise.auf100(l.preis) }
        val einkommen = remember(l) { Hauspreise.auf100(l.einkommen) }
        val x0 = Hauspreise.quartalAn(d, 0).let { it.first + (it.second - 1) / 4.0 }
        fun xs(reihe: List<Double>) = reihe.mapIndexed { i, v -> (x0 + i / 4.0) to v }
        LbUnterkopf(S.hpVerlauf(ab))
        HistDiagramm(
            listOf(
                HistLinie(S.hpPreisLinie, xs(preis), MaterialTheme.colorScheme.primary),
                HistLinie(S.hpEinkommenLinie, xs(einkommen), MaterialTheme.colorScheme.tertiary),
            ),
            x0, x0 + (preis.size - 1) / 4.0, log = false, marken = emptyList(),
            achsenText = { Fmt.num(it, 0) }, endeText = { Fmt.num(it, 0) }, beschreibung = S.hpVerlauf(ab),
        )
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

internal fun hauspreiseQuelle(d: Hauspreise.Datei): String =
    S.hpZitatKopf + "\n" + d.hinweis.substringBefore(" Quelle:") + "\n\n" + d.zitat.joinToString("\n\n") + "\n\n" + S.hpNutzung + "\n" + S.histAbruf(datumText(d.stand))

// Eine Linie des Diagramms: Name am Ende der Linie, Punkte als (x, y)
internal class HistLinie(val name: String, val punkte: List<Pair<Double, Double>>, val farbe: Color)

// Schoene Schrittweite (1, 2, 5 mal Zehnerpotenz) fuer etwa `n` Teilstriche
private fun schritt(spanne: Double, n: Int): Double {
    val roh = spanne / n
    val zehner = 10.0.pow(floor(log10(roh)))
    val f = roh / zehner
    return zehner * (if (f <= 1.0) 1.0 else if (f <= 2.0) 2.0 else if (f <= 5.0) 5.0 else 10.0)
}

// Liniendiagramm mit selbst gezeichneten Achsen. log: Rechenachse zehnerlogarithmisch (Striche bei jeder Zehnerpotenz).
// Beschriftung am Element: Name und Endwert stehen am Ende jeder Linie (bei Naehe auseinandergerueckt), keine Legende.
// marken: senkrechte Striche (Krisenjahre).
@Composable
internal fun HistDiagramm(
    linien: List<HistLinie>, xVon: Double, xBis: Double, log: Boolean, marken: List<Double>,
    achsenText: (Double) -> String, endeText: (Double) -> String, beschreibung: String, hoehe: Dp = 190.dp,
) {
    if (linien.isEmpty()) return
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val raster = MaterialTheme.colorScheme.outlineVariant
    val klein = TextStyle(fontSize = 10.sp, color = grau)
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp).height(hoehe).semantics { contentDescription = beschreibung }) {
        val d = density
        val alle = linien.flatMap { l -> l.punkte.map { it.second } }.filter { !log || it > 0.0 }
        if (alle.isEmpty()) return@Canvas
        fun t(v: Double) = if (log) log10(v) else v
        var lo = t(alle.min())
        var hi = t(alle.max())
        val striche = ArrayList<Double>()
        if (log) {
            lo = floor(lo); hi = maxOf(ceil(hi), lo + 1.0)
            var e = lo
            while (e <= hi + 1e-9) { striche += e; e += 1.0 }
        } else {
            val s = schritt(hi - lo, 4)
            lo = floor(lo / s) * s; hi = ceil(hi / s) * s
            var v = lo
            while (v <= hi + 1e-9) { striche += v; v += s }
        }
        fun wertVon(tv: Double) = if (log) 10.0.pow(tv) else tv
        val achse = striche.map { messer.measure(achsenText(wertVon(it)), klein, maxLines = 1, softWrap = false) }
        val links = (achse.maxOf { it.size.width } + 6f * d)
        val enden = linien.map { messer.measure(it.name + " " + endeText(it.punkte.last().second), klein.copy(color = it.farbe), maxLines = 1, softWrap = false) }
        val rechts = enden.maxOf { it.size.width } + 6f * d
        val oben = 6f * d
        val unten = 16f * d
        val b = size.width - links - rechts
        val h = size.height - oben - unten
        if (b <= 0f || h <= 0f) return@Canvas
        fun px(x: Double) = links + b * ((x - xVon) / (xBis - xVon)).toFloat()
        fun py(v: Double) = oben + h * (1f - ((t(v) - lo) / (hi - lo)).toFloat())
        // Waagerechte Striche mit Beschriftung links
        for ((i, s) in striche.withIndex()) {
            val y = oben + h * (1f - ((s - lo) / (hi - lo)).toFloat())
            drawLine(raster, Offset(links, y), Offset(links + b, y), strokeWidth = 1f * d)
            drawText(achse[i], topLeft = Offset(links - 6f * d - achse[i].size.width, y - achse[i].size.height / 2f))
        }
        // Jahreszahlen unten
        val sx = schritt(xBis - xVon, 5).coerceAtLeast(1.0)
        var x = ceil(xVon / sx) * sx
        while (x <= xBis + 1e-9) {
            val z = messer.measure(x.roundToInt().toString(), klein, maxLines = 1, softWrap = false)
            val mitte = px(x)
            if (mitte - z.size.width / 2f >= links - 4f * d && mitte + z.size.width / 2f <= size.width) {
                drawText(z, topLeft = Offset(mitte - z.size.width / 2f, size.height - unten + 3f * d))
            }
            x += sx
        }
        // Marken (Krisenjahre) als gestrichelte senkrechte Linien
        for (m in marken) {
            if (m < xVon || m > xBis) continue
            drawLine(
                grau.copy(alpha = 0.45f), Offset(px(m), oben), Offset(px(m), oben + h), strokeWidth = 1f * d,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * d, 3f * d)),
            )
        }
        // Linien
        for (l in linien) {
            val pfad = Path()
            var erster = true
            for ((xx, yy) in l.punkte) {
                if (log && yy <= 0.0) continue
                val p = Offset(px(xx), py(yy))
                if (erster) { pfad.moveTo(p.x, p.y); erster = false } else pfad.lineTo(p.x, p.y)
            }
            drawPath(pfad, l.farbe, style = Stroke(width = 1.6f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Namen am Ende, bei Naehe nach unten auseinandergerueckt
        val hoeheText = enden.maxOf { it.size.height }.toFloat()
        val ys = linien.indices.map { i -> py(linien[i].punkte.last().second) - enden[i].size.height / 2f }
        val reihenfolge = linien.indices.sortedBy { ys[it] }
        val gesetzt = FloatArray(linien.size)
        var vorher = -1e9f
        for (i in reihenfolge) {
            gesetzt[i] = maxOf(ys[i], vorher + hoeheText)
            vorher = gesetzt[i]
        }
        for (i in linien.indices) drawText(enden[i], topLeft = Offset(links + b + 6f * d, gesetzt[i].coerceIn(0f, size.height - unten - hoeheText + 8f * d)))
    }
}
