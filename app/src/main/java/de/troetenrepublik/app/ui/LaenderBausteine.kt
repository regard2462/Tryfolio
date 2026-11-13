package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.Treemap
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// [Laenderblatt 0.70] Gemeinsame Bausteine des Laenderblatts: Abschnitte, einzeilige Zeilen, Kacheln, Quellenleiste und
// die selbst gezeichneten Diagramme (Jahresbalken, Ring, Balkenliste, Treemap).

// Eine Zeile: Bezeichnung links, Wert rechts. Die Bezeichnung steht immer ganz da und bricht bei Platzmangel um; der Wert
// bleibt einzeilig rechts auf Hoehe ihrer ersten Zeile und rutscht nie unter die Bezeichnung.
@Composable
internal fun LbZeile(label: String, wert: String, farbe: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            wert, style = MaterialTheme.typography.bodyMedium, color = farbe ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

// Fliesstext-Absatz: laengere Angaben stehen hier, nie als Wert einer Zeile.
@Composable
internal fun LbAbsatz(text: String, onClick: (() -> Unit)? = null, farbe: Color? = null) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = farbe ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
internal fun LbHinweis(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

// [0.71] Ein Stichpunkt mit haengendem Einzug: Der Punkt steht vorne, Folgendezeilen beginnen unter dem Text, nicht
// unter dem Punkt. So stehen mehrzeilige Stichpunkte ordentlich untereinander.
@Composable
internal fun LbStichpunkt(text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 3.dp),
    ) {
        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 4.dp))
    }
}

@Composable
internal fun LbUnterkopf(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 2.dp),
    )
}

// Abschnitt mit Titel; wer ihn antippt, klappt ihn zu oder auf. tief: Unterabschnitt mit kleinerer Ueberschrift.
@Composable
internal fun LaenderAbschnitt(titel: String, schluessel: String, startOffen: Boolean = true, tief: Boolean = false, inhalt: @Composable ColumnScope.() -> Unit) {
    var offen by rememberSaveable(schluessel) { mutableStateOf(startOffen) }
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp).padding(top = if (tief) 12.dp else 22.dp, bottom = 4.dp).heightIn48(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = if (tief) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        AufklappPfeil(offen)
    }
    if (offen) Column(Modifier.fillMaxWidth(), content = inhalt)
}

private fun Modifier.heightIn48(): Modifier = this.defaultMinSize(minHeight = 40.dp)

// Sammler der Quellen eines Blatts: Abschnitte melden ihre Quellen, das Blatt zeigt sie einmal unten im QuellenAnhang.
internal val LocalQuellenSammler = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.snapshots.SnapshotStateList<QuelleLink>?> { null }

// Nur Quellen, deren Bedingungen eine Nennung verlangen, mit der verlangten Lizenzangabe; alle anderen entfallen.
internal fun pflichtQuelle(name: String, url: String): QuelleLink? = when {
    // Feste Namen: Weltbank und IWF erscheinen im Anhang einmal, gleich wie ihre Seiten im Blatt heissen
    "imf.org" in url -> QuelleLink(if (deutschSprache()) "IWF" else "IMF", url)
    "worldbank.org" in url -> QuelleLink((if (deutschSprache()) "Weltbank" else "World Bank") + " (CC BY 4.0)", url)
    "oecd.org" in url -> QuelleLink("OECD (CC BY 4.0)", url)
    "ucdp.uu.se" in url -> QuelleLink("UCDP (CC BY 4.0)", url)
    "opensanctions.org" in url -> QuelleLink("OpenSanctions (CC BY-NC 4.0)", url)
    // Jeder Wikipedia-Artikel steht einzeln mit seinem Titel
    "wikipedia.org" in url -> QuelleLink("Wikipedia" + (wikiTitel(url)?.let { ": $it" } ?: "") + " (CC BY-SA 4.0)", url)
    "population.un.org" in url -> QuelleLink("$name (CC BY 3.0 IGO)", url)
    "comtradeplus.un.org" in url -> QuelleLink(name, url)
    "data.un.org" in url -> QuelleLink(if (deutschSprache()) "Vereinte Nationen" else "United Nations", url)
    "gdacs.org" in url -> QuelleLink(name, url)
    // Verlangt Namensnennung und das Zitat der Autoren; Lizenz und Zitat stehen im Infotext der Kennzahlen
    "macrohistory.net" in url -> QuelleLink("Jordà-Schularick-Taylor Macrohistory Database (CC BY-NC-SA 4.0)", url)
    "dallasfed.org" in url -> QuelleLink("Federal Reserve Bank of Dallas, International House Price Database", url)
    // ENTSO-E und SMARD (Bundesnetzagentur, CC BY 4.0, Nennung "Bundesnetzagentur | SMARD.de"): Name wie vom Aufrufer genannt
    "entsoe.eu" in url || "smard.de" in url -> QuelleLink(name, url)
    // Die BIS verlangt die Nennung als Quelle ihrer Statistiken
    "bis.org" in url -> QuelleLink(if (deutschSprache()) "BIZ (BIS), Leitzinsen der Zentralbanken" else "BIS, central bank policy rates", url)
    else -> null
}

private fun deutschSprache() = de.troetenrepublik.app.i18n.I18n.locale.language == "de"

// Titel des Artikels aus der Adresse (.../wiki/List_of_countries -> "List of countries"); null bei der Startseite
private fun wikiTitel(url: String): String? =
    url.substringAfter("/wiki/", "").substringBefore('#').substringBefore('?').replace('_', ' ').trim().takeIf { it.isNotEmpty() }

// Meldet Quellen an das Blatt (keine Zeile im Abschnitt).
@Composable
internal fun QuellenLeiste(quellen: List<Pair<String, String>>) {
    val sammler = LocalQuellenSammler.current ?: return
    val neu = quellen.mapNotNull { (n, u) -> pflichtQuelle(n, u) }
    androidx.compose.runtime.SideEffect { for (q in neu) if (sammler.none { it.name == q.name }) sammler.add(q) }
}

// Quellen mit Adresse; Namen in der App-Sprache.
internal object LaenderQuellen {
    private fun de() = de.troetenrepublik.app.i18n.I18n.locale.language == "de"
    fun iwf(a3: String) = (if (de()) "IWF" else "IMF") to "https://www.imf.org/external/datamapper/profile/$a3"
    fun weltbank(a2: String) = (if (de()) "Weltbank" else "World Bank") to (if (a2.isNotBlank()) "https://data.worldbank.org/country/$a2" else "https://data.worldbank.org/")
    fun bis() = "BIS" to "https://data.bis.org/topics/CBPOL"
    fun ezb() = (if (de()) "EZB" else "ECB") to "https://data.ecb.europa.eu/data/datasets/IRS"
    fun fred() = "FRED" to "https://fred.stlouisfed.org/"
    fun yahoo(symbol: String? = null) = "Yahoo Finance" to (if (symbol.isNullOrBlank()) "https://finance.yahoo.com/" else "https://finance.yahoo.com/quote/$symbol")
    fun oecd() = "OECD" to "https://data-explorer.oecd.org/"
    fun eurostat() = "Eurostat" to "https://ec.europa.eu/eurostat/web/products-euro-indicators"
    fun ucdp() = "UCDP" to "https://ucdp.uu.se/"
    fun un() = (if (de()) "Vereinte Nationen" else "United Nations") to "https://data.un.org/"
    fun openSanctions() = "OpenSanctions" to "https://www.opensanctions.org/"
    fun euSanktionen() = (if (de()) "EU-Sanktionskarte" else "EU Sanctions Map") to "https://www.sanctionsmap.eu/"
    fun ofac() = "OFAC" to "https://ofac.treasury.gov/sanctions-programs-and-country-information"
    fun gdacs() = "GDACS" to "https://www.gdacs.org/"
    fun wikipedia(url: String = "https://www.wikipedia.org/") = "Wikipedia" to url
    fun wikidata() = "Wikidata" to "https://www.wikidata.org/"
    fun comtrade() = "UN Comtrade" to "https://comtradeplus.un.org/"
    fun unWpp() = (if (de()) "UN-Bevölkerung" else "UN Population") to "https://population.un.org/wpp/"
    fun nager() = "Nager.Date" to "https://date.nager.at/"
    fun usReise() = (if (de()) "US-Außenministerium" else "US State Dept") to "https://travel.state.gov/content/travel/en/traveladvisories/traveladvisories.html"
    fun auswaertiges() = (if (de()) "Auswärtiges Amt" else "German Foreign Office") to "https://www.auswaertiges-amt.de/de/ReiseUndSicherheit"
    fun wgi() = (if (de()) "Weltbank" else "World Bank") to "https://www.worldbank.org/en/publication/worldwide-governance-indicators"
    fun news() = "Google News" to "https://news.google.com/"
    fun jst() = "Jordà-Schularick-Taylor Macrohistory Database" to "https://www.macrohistory.net/database/"
    fun dallas() = "Federal Reserve Bank of Dallas" to "https://www.dallasfed.org/research/international/houseprice"
}

// Eine Kennzahl als Kachel: Bezeichnung, Wert, optional eine Zusatzzeile. Ohne Umbruch.
internal class KachelDaten(val label: String, val wert: String, val zusatz: String? = null, val farbe: Color? = null, val onClick: (() -> Unit)? = null)

// [0.73] Spaltenzahl nach Inhalt: Es sind so viele Spalten, wie hoechstens angegeben, solange jede Bezeichnung, jeder Wert und
// jede Zusatzzeile ganz in eine Kachel passt; sonst eine Spalte weniger. Nichts wird gekuerzt oder verkleinert.
@Composable
internal fun KachelRaster(kacheln: List<KachelDaten>, spalten: Int = 3) {
    if (kacheln.isEmpty()) return
    val messer = rememberTextMeasurer()
    val klein = MaterialTheme.typography.labelSmall
    val gross = MaterialTheme.typography.titleSmall
    val dichte = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val breite = maxWidth
        val n = remember(kacheln, breite, klein, gross, dichte, spalten) {
            fun passt(spaltenZahl: Int): Boolean {
                val innen = with(dichte) { ((breite - 8.dp * (spaltenZahl - 1)) / spaltenZahl - 20.dp).roundToPx() }
                fun breiteVon(text: String, stil: TextStyle) = messer.measure(text, stil, softWrap = false, maxLines = 1).size.width
                return kacheln.all { k ->
                    breiteVon(k.label, klein) <= innen && breiteVon(k.wert, gross) <= innen && (k.zusatz == null || breiteVon(k.zusatz, klein) <= innen)
                }
            }
            var z = spalten
            while (z > 1 && !passt(z)) z--
            z
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (zeile in kacheln.chunked(n)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in zeile) Box(Modifier.weight(1f)) { Kachel(k) }
                repeat(n - zeile.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Kachel(k: KachelDaten) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (k.onClick != null) Modifier.clickable(onClick = k.onClick) else Modifier).padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(k.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Text(k.wert, style = MaterialTheme.typography.titleSmall, color = k.farbe ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
        Text(k.zusatz ?: " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

// Jahresbalken: je Jahr ein Balken, Prognosejahre ab prognoseAb blasser und umrandet auf getoenter Flaeche. Beschriftung am Element:
// Jahreszahlen unten, "Prognose" im Band. [0.71] Der Wert eines Jahres erscheint nur solange Finger oder Maus auf dem
// Bild ruhen (wie im Portfoliografen) und steht im freien Band ueber dem Balken, damit er in keinen Balken und in keine
// andere Zahl ragt. Ausreisser werden gekappt.
@Composable
internal fun JahresBalken(
    titel: String, reihe: List<Pair<Int, Double>>, prognoseAb: Int, farbeFuer: (Double) -> Color, wertText: (Double) -> String,
    onClick: (() -> Unit)? = null,
) {
    if (reihe.size < 3) return
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.outlineVariant
    val band = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val schrift = MaterialTheme.colorScheme.onSurface
    val kappMarke = MaterialTheme.colorScheme.background
    val prognoseText = S.lbPrognose
    val beschreibung = titel
    // Jahr unter dem Finger; null, wenn nichts beruehrt wird
    var leseJahr by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
        Text(titel, style = MaterialTheme.typography.labelLarge, color = grau)
        Canvas(
            Modifier.fillMaxWidth().height(132.dp).padding(top = 4.dp).semantics { contentDescription = beschreibung }
                .pointerInput(reihe, onClick) {
                    // Wie im VergleichsChart: Ein Finger zeigt sofort den Wert, waagerechtes Ziehen liest mit, senkrechtes
                    // Ziehen bleibt beim Blatt-Scroll. Beim Loslassen verschwindet die Zahl. Nur ein kurzes Tippen ohne
                    // Bewegung oeffnet das Info-Blatt; Halten und Lesen oeffnet nichts.
                    awaitEachGesture {
                        val start = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        val tippGrenze = viewConfiguration.longPressTimeoutMillis
                        var bewegt = false
                        var getippt = false
                        fun jahrBei(x: Float): Int? {
                            val i = (x / (size.width / reihe.size)).toInt().coerceIn(0, reihe.size - 1)
                            return reihe[i].first
                        }
                        leseJahr = jahrBei(start.position.x)
                        var horizontal: Boolean? = null
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == start.id } ?: break
                            if (!c.pressed) { getippt = !bewegt && !c.isConsumed && c.uptimeMillis - start.uptimeMillis < tippGrenze; break }
                            val dxy = c.position - start.position
                            if (abs(dxy.x) > slop || abs(dxy.y) > slop) bewegt = true
                            if (horizontal == null && (abs(dxy.x) > slop || abs(dxy.y) > slop)) horizontal = abs(dxy.x) >= abs(dxy.y)
                            if (horizontal == false) break
                            if (horizontal == true) c.consume()
                            leseJahr = jahrBei(c.position.x)
                        }
                        leseJahr = null
                        if (getippt) onClick?.invoke()
                    }
                },
        ) {
            val n = reihe.size
            val d = density
            val oben = 30f * d; val unten = 16f * d
            val h = size.height - oben - unten
            val betraege = reihe.map { abs(it.second) }.sorted()
            val median = betraege[betraege.size / 2]
            val kappe = maxOf(15.0, median * 4.0)
            val hi = maxOf(reihe.maxOf { it.second }.coerceAtMost(kappe), 0.0)
            val lo = minOf(reihe.minOf { it.second }.coerceAtLeast(-kappe), 0.0)
            val spanne = (hi - lo).coerceAtLeast(1.0)
            fun y(v: Double) = oben + h * (1f - ((v.coerceIn(lo, hi) - lo) / spanne).toFloat())
            val y0 = y(0.0)
            val breite = size.width / n
            val ersterPrognose = reihe.indexOfFirst { it.first >= prognoseAb }
            val kleinStil = TextStyle(fontSize = 10.sp, color = grau)
            val wertStil = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = schrift)
            if (ersterPrognose >= 0) {
                drawRect(band, Offset(ersterPrognose * breite, 0f), Size(size.width - ersterPrognose * breite, size.height - unten + 4f * d))
                // Beim Lesen steht der Wert im selben Band, dann weicht die Aufschrift
                val t = messer.measure(prognoseText, kleinStil, maxLines = 1, softWrap = false)
                if (leseJahr == null && t.size.width + 8f * d < size.width - ersterPrognose * breite) drawText(t, topLeft = Offset(ersterPrognose * breite + 4f * d, 0f))
            }
            drawLine(linie, Offset(0f, y0), Offset(size.width, y0), strokeWidth = 1f * d)
            val balken = breite * 0.64f
            var letzteJahrMarke = -1e9f
            var leseX = -1f; var leseSpitze = 0f; var leseText: androidx.compose.ui.text.TextLayoutResult? = null
            for (i in 0 until n) {
                val (jahr, v) = reihe[i]
                val farbe = farbeFuer(v)
                val x = i * breite + (breite - balken) / 2
                val spitze = y(v)
                val prognose = ersterPrognose in 0..i
                val oberkante = minOf(spitze, y0); val hoehe = maxOf(abs(spitze - y0), 1.5f * d)
                if (prognose) {
                    drawRoundRect(farbe.copy(alpha = 0.35f), Offset(x, oberkante), Size(balken, hoehe), CornerRadius(1.5f * d))
                    drawRoundRect(farbe, Offset(x, oberkante), Size(balken, hoehe), CornerRadius(1.5f * d), style = Stroke(width = 1f * d))
                } else {
                    drawRoundRect(farbe, Offset(x, oberkante), Size(balken, hoehe), CornerRadius(1.5f * d))
                }
                // Gekappter Ausreisser: ein heller Strich durch den Balken kurz unter der Spitze
                if (v > hi || v < lo) {
                    val ky = if (v > hi) spitze + 5f * d else spitze - 5f * d
                    drawLine(kappMarke, Offset(x, ky), Offset(x + balken, ky), strokeWidth = 1.5f * d)
                }
                // Jahreszahl alle fuenf Jahre und am Ende
                if (jahr % 5 == 0 || i == n - 1 && x - letzteJahrMarke > 30f * d) {
                    val t = messer.measure(jahr.toString(), kleinStil, maxLines = 1, softWrap = false)
                    val tx = (x + balken / 2 - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
                    if (tx - letzteJahrMarke > 4f * d) { drawText(t, topLeft = Offset(tx, size.height - unten + 3f * d)); letzteJahrMarke = tx + t.size.width }
                }
                if (jahr == leseJahr) {
                    leseX = x + balken / 2
                    leseSpitze = spitze
                    leseText = messer.measure("$jahr · ${wertText(v)}", wertStil, maxLines = 1, softWrap = false)
                    // Kleine Marke an der Balkenspitze
                    drawCircle(farbe, 2.5f * d, Offset(leseX, spitze))
                }
            }
            // Wert des gewaehlten Jahres im Band ueber den Balken, samt Lot von der Zahl zur Balkenspitze
            leseText?.let { t ->
                val tx = (leseX - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
                drawLine(schrift.copy(alpha = 0.35f), Offset(leseX, t.size.height + 2f * d), Offset(leseX, leseSpitze), strokeWidth = 1f * d)
                drawText(t, topLeft = Offset(tx, (oben - t.size.height) / 2))
            }
        }
    }
}

// Teil eines Rings: Name, Wert (Anteil, beliebige Einheit), Farbe und Text rechts in der Zeile.
internal class RingTeil(val name: String, val wert: Double, val farbe: Color, val text: String, val onClick: (() -> Unit)? = null)

// Ring links, rechts je Teil eine Zeile mit Farbpunkt, Name und Anteil: Beschriftung direkt am Element, keine Legende.
@Composable
internal fun RingDiagramm(teile: List<RingTeil>, mitte: String? = null) {
    val gueltig = teile.filter { it.wert > 0.0 && it.wert.isFinite() }
    val summe = gueltig.sumOf { it.wert }
    if (gueltig.isEmpty() || summe <= 0.0) return
    val grund = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strich = 20f * density
                val kante = Size(size.width - strich, size.height - strich)
                val ecke = Offset(strich / 2, strich / 2)
                drawArc(grund, 0f, 360f, useCenter = false, topLeft = ecke, size = kante, style = Stroke(width = strich))
                var start = -90f
                val luecke = if (gueltig.size > 1) 2f else 0f
                for (t in gueltig) {
                    val bogen = (t.wert / summe * 360.0).toFloat()
                    drawArc(t.farbe, start + luecke / 2, (bogen - luecke).coerceAtLeast(0.5f), useCenter = false, topLeft = ecke, size = kante, style = Stroke(width = strich, cap = StrokeCap.Butt))
                    start += bogen
                }
            }
            if (mitte != null) Text(mitte, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Der Name steht ganz da und bricht bei Platzmangel um; Punkt und Wert bleiben auf Hoehe der ersten Zeile
            for (t in gueltig) Row(
                Modifier.fillMaxWidth().then(if (t.onClick != null) Modifier.clickable(onClick = t.onClick) else Modifier).padding(vertical = 6.dp),
            ) {
                Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(t.farbe))
                Spacer(Modifier.width(8.dp))
                Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).alignByBaseline())
                Spacer(Modifier.width(8.dp))
                Text(t.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

// anteil: Fuellung von 0 bis 1. Ist die Zahl ein Anteil in Prozent, gilt der Massstab 0 bis 100 % (Blattaufbau.balkenAnteil),
// nie das Verhaeltnis zum groessten Eintrag.
internal class BalkenZeile(val name: String, val anteil: Float, val text: String, val farbe: Color, val onClick: (() -> Unit)? = null)

// Waagerechte Balken: Name links, Balken, Zahl rechts. Der Name steht ganz da und bricht bei Platzmangel um; Balken und
// Zahl bleiben auf Hoehe seiner ersten Zeile.
@Composable
internal fun BalkenListe(zeilen: List<BalkenZeile>) {
    if (zeilen.isEmpty()) return
    val grund = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        for (z in zeilen) Row(
            Modifier.fillMaxWidth().then(if (z.onClick != null) Modifier.zeilenKlick(onClick = z.onClick) else Modifier).padding(vertical = 8.dp),
        ) {
            Text(z.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).alignByBaseline())
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.padding(top = 5.dp).width(88.dp).height(10.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(grund, Offset.Zero, size, r)
                val w = (size.width * z.anteil.coerceIn(0f, 1f)).coerceAtLeast(if (z.anteil > 0f) size.height else 0f)
                if (w > 0f) drawRoundRect(z.farbe, Offset.Zero, Size(w, size.height), r)
            }
            Text(z.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 52.dp).alignByBaseline())
        }
    }
}

// Feld mit kleiner Bezeichnung ueber dem Wert (zwei Felder je Zeile, breite Felder allein). Der Wert steht als Fliesstext unter
// der Bezeichnung und zeigt sich ganz, auch wenn er mehrere Zeilen braucht (Regierungsform, Sprachen, Mitgliedschaften).
// Zahlen gehoeren nicht hierher, sie stehen in einer LbZeile.
internal class FeldDaten(val label: String, val wert: String, val breit: Boolean = false, val onClick: (() -> Unit)? = null)

@Composable
internal fun FeldRaster(felder: List<FeldDaten>) {
    if (felder.isEmpty()) return
    val messer = rememberTextMeasurer()
    val klein = MaterialTheme.typography.labelSmall
    val gross = MaterialTheme.typography.bodyMedium
    val dichte = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val breite = maxWidth
        val breit = remember(felder, breite, klein, gross, dichte) {
            val halb = with(dichte) { ((breite - 12.dp) / 2).roundToPx() }
            fun w(text: String, stil: TextStyle) = messer.measure(text, stil, softWrap = false, maxLines = 1).size.width
            felder.map { it.breit || w(it.wert, gross) > halb || w(it.label, klein) > halb }
        }
        val zeilen = remember(breit) { de.troetenrepublik.app.engine.Blattaufbau.feldZeilen(breit) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (z in zeilen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in z) {
                        val f = felder[i]
                        Column(
                            Modifier.weight(1f).then(if (f.onClick != null) Modifier.clickable(onClick = f.onClick) else Modifier).padding(vertical = 8.dp),
                        ) {
                            Text(f.label, style = klein, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(f.wert, style = gross)
                        }
                    }
                    // Ein uebrig gebliebenes schmales Feld am Ende behaelt seine halbe Breite
                    if (z.size == 1 && !breit[z[0]]) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
