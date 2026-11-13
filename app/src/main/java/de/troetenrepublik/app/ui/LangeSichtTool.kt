package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.HistorieJst
import de.troetenrepublik.app.engine.LangeReihen
import de.troetenrepublik.app.engine.LangeReihen.Anlage
import de.troetenrepublik.app.engine.LangeVergleich
import de.troetenrepublik.app.engine.WaehrungReihen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.LangeSichtTexte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.FaktorAbruf
import kotlin.math.abs
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Werkzeug „Lange Sicht“ (0.78): Mischungen aus Aktien, Anleihen, Geldmarkt und Gold ueber die ganze belegte Zeit, real und in
// Jahresschritten. Eine bis drei Mischungen lassen sich vergleichen, jede in ihrer festen Farbe. Kernzahl ist die reale Rendite
// je Jahr, darunter der Verlauf, eingeklappt die Verteilung je Haltedauer. Die Rechnung steckt in engine/LangeReihen.kt, die
// Hilfen des Vergleichs in engine/LangeVergleich.kt.

internal fun langText(key: String, vararg w: String): String = LangeSichtTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Reale Jahresrenditen je Anlage, einmal je Prozess gelesen; laender: Zahl der Laender hinter „Welt ohne USA“
internal object LangeSichtLader {
    class Stand(val anlagen: Map<Anlage, LangeReihen.Jahre>, val laender: Int, val mitGold: Boolean, val datei: LangeReihen.Datei? = null, val jst: HistorieJst.Datei? = null)

    @Volatile private var stand: Stand? = null

    fun gespeichert(): Stand? = stand

    suspend fun laden(): Stand? {
        stand?.let { return it }
        val jst = HistorieLader.laden().jst ?: return null
        val s = withContext(Dispatchers.Default) {
            val d = runCatching { LangeReihen.lesen(de.troetenrepublik.app.market.Daten.text(LangeReihen.DATEI)) }.getOrNull()
            val anlagen = LangeReihen.anlagen(jst, d)
            val laender = jst.laender.values.count { it.a3 != LangeReihen.USA && it.reihen[HistorieJst.Art.AKTIEN] != null }
            Stand(anlagen, laender, Anlage.GOLD in anlagen, d, jst)
        }
        stand = s
        return s
    }

    // Small-Cap-Value kommt aus den Fama/French-Daten per Abruf; erst mit ihnen gibt es Mischungen damit. Ohne Antwort bleibt der Stand.
    suspend fun ergaenzen(): Stand? {
        val s = stand ?: return null
        if (Anlage.AKTIEN_KLEIN_WERT in s.anlagen) return s
        val jst = s.jst ?: return s
        val nominal = runCatching { de.troetenrepublik.app.market.FaktorAbruf.kleinWertJahre() }.getOrNull() ?: return s
        val neu = withContext(Dispatchers.Default) { LangeReihen.anlagen(jst, s.datei, nominal) }
        if (Anlage.AKTIEN_KLEIN_WERT !in neu) return s
        val n = Stand(neu, s.laender, s.mitGold, s.datei, s.jst)
        stand = n
        return n
    }
}

// Bezeichnung einer Vorgabe: Verhaeltnis Aktien zu Anleihen oder ein Name
private fun vorgabeName(v: LangeReihen.Vorgabe): String = when (v.name) {
    "A100" -> langText("v.A100", Fmt.pct(100.0, 0))
    "GOLD10" -> langText("v.GOLD10", Fmt.pct(v.gold.toDouble(), 0))
    "VIER" -> langText("v.VIER")
    "ALLWEATHER" -> langText("v.ALLWEATHER")
    "GOLDEN" -> langText("v.GOLDEN")
    else -> "${v.aktien}/${v.anleihen}"
}

// Feste Farbe je Mischung (nach Platz in der Liste aller Vorgaben), unabhaengig davon, was sonst gewaehlt ist
private fun vorgabeFarbe(v: LangeReihen.Vorgabe): Color = Kategorie.farbe(LangeReihen.VORGABEN.indexOfFirst { it.name == v.name }.coerceAtLeast(0))

// Eine gewaehlte Mischung mit Bezeichnung, Farbe und Ergebnis (bei mehreren auf den gemeinsamen Zeitraum zugeschnitten)
private class LangSpalte(val vorgabe: LangeReihen.Vorgabe, val name: String, val farbe: Color, val e: LangeReihen.Ergebnis)

private class LangLinie(val name: String, val farbe: Color, val e: LangeReihen.Ergebnis)

private class LangZelle(val text: String, val farbe: Color? = null, val unter: String? = null)

private class LangZeile(val name: String, val zellen: List<LangZelle>)

// Die Reihen laufen in US-Dollar
private fun usd(v: Double) = Fmt.mitSymbol(Fmt.num(v, 0), "$")

@Composable
internal fun LangeSichtTool() {
    var stand by remember { mutableStateOf(LangeSichtLader.gespeichert()) }
    LaunchedEffect(Unit) {
        if (stand == null) stand = LangeSichtLader.laden()
        stand = LangeSichtLader.ergaenzen() ?: stand
    }
    val st = stand ?: return
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var welt by rememberSaveable { mutableStateOf(false) }
    // Gewaehlte Mischungen als Namen mit Komma getrennt; leer ergibt die Vorgabe (reine Aktien und 60/40)
    var gemerkt by rememberSaveable { mutableStateOf("") }
    var dauer by rememberSaveable { mutableStateOf(10) }
    var offen by rememberSaveable { mutableStateOf(false) }

    // Nur Mischungen, deren Anlagen alle eine Reihe haben (Gold, Rohstoffe, Small-Cap-Value erst nach geladenen Daten)
    val vorgaben = remember(st) { LangeReihen.VORGABEN.filter { v -> v.anteile(Anlage.AKTIEN_USA).keys.all { it in st.anlagen } } }
    val wahl = remember(gemerkt, vorgaben) { LangeVergleich.lesen(gemerkt, vorgaben.map { it.name }) }
    val aktien = if (welt && Anlage.AKTIEN_WELT in st.anlagen) Anlage.AKTIEN_WELT else Anlage.AKTIEN_USA
    val roh = remember(st, wahl, aktien) {
        wahl.mapNotNull { n -> vorgaben.firstOrNull { it.name == n } }.mapNotNull { v ->
            LangeReihen.mischung(st.anlagen, v.anteile(aktien).filter { it.value > 0.0 })?.let { v to it }
        }
    }
    val geschnitten = remember(roh) { LangeVergleich.zuschneiden(roh.map { it.second }) }
    val sprache = I18n.lang
    val spalten = remember(roh, geschnitten, sprache) {
        val es = geschnitten ?: return@remember emptyList<LangSpalte>()
        roh.mapIndexed { i, (v, _) -> LangSpalte(v, vorgabeName(v), vorgabeFarbe(v), es[i]) }
    }

    if (Anlage.AKTIEN_WELT in st.anlagen) {
        ToolChoice(langText("aktien"), listOf(langText("aktien.usa") to false, langText("aktien.welt") to true), welt) { welt = it }
    }
    // Mehrfachauswahl: aktive Chips tragen die Farbe der Mischung
    Text(
        langText("mischung"), style = MaterialTheme.typography.bodySmall, color = grau,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 6.dp),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        vorgaben.forEach { v ->
            Chip(vorgabeName(v), v.name in wahl, accent = vorgabeFarbe(v)) { gemerkt = LangeVergleich.schreiben(LangeVergleich.umschalten(wahl, v.name)) }
        }
    }

    if (spalten.isEmpty()) {
        Text(
            langText(if (wahl.size > 1) "leer.mehr" else "leer"), style = MaterialTheme.typography.bodyMedium, color = grau,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        return
    }
    val linien = remember(spalten) { spalten.map { LangLinie(it.name, it.farbe, it.e) } }
    val e0 = spalten.first().e

    if (spalten.size == 1) {
        val s = spalten[0]
        val e = s.e
        val anteile = s.vorgabe.anteile(aktien).filter { it.value > 0.0 }
        // Kernaussage: reale Rendite je Jahr, darunter Zeitraum und Zusammensetzung
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 6.dp)) {
            Text(
                Fmt.signedPct(e.rendite, 1), style = MaterialTheme.typography.headlineMedium, color = if (e.rendite >= 0) signal.up else signal.down,
                maxLines = 1, softWrap = false,
            )
            val teile = anteile.entries.joinToString(", ") { (a, w) -> Fmt.pct(w * 100.0, if (abs(w * 100.0 - Math.round(w * 100.0)) > 1e-9) 1 else 0) + " " + langText("n." + a.name) }
            Text(langText("kern", e.von.toString(), e.bis.toString()) + ". " + teile, style = MaterialTheme.typography.bodySmall, color = grau)
        }
        LangVerlauf(linien, signal.down)
        KeyValueRow(langText("ende", usd(1000.0)), usd(e.verlauf.last() * 1000.0))
        e.rueckgang?.let { r ->
            val erholt = r.erholt
            val zeit = if (erholt != null) {
                langText("rueckgang.erholt", r.hoch.toString(), r.tief.toString(), erholt.toString(), S.toolYearsDativ(erholt - r.hoch))
            } else {
                langText("rueckgang.offen", r.hoch.toString(), r.tief.toString(), e.bis.toString())
            }
            KeyValueRow(langText("rueckgang"), Fmt.signedPct(r.tiefe, 0), signal.down, hint = zeit)
        }

        // Verteilung je Haltedauer: eingeklappt eine Zeile zur gewaehlten Dauer
        val dauern = e.haltedauern
        if (dauern.isNotEmpty()) {
            val gewaehlt = dauern.firstOrNull { it.jahre == dauer } ?: dauern[dauern.size / 2]
            AufklappKopf(
                langText("halte.titel"), langText("halte.kurz", S.toolYears(gewaehlt.jahre), Fmt.pct(gewaehlt.verlust * 100.0, 0)), offen,
            ) { offen = !offen }
            if (offen) {
                LangSpannen(dauern, signal.up, signal.down)
                ToolZeit(langText("halte.wahl"), dauern.map { S.toolYearsKurz(it.jahre) to it.jahre }, gewaehlt.jahre) { dauer = it }
                fun farbe(v: Double) = if (v >= 0) signal.up else signal.down
                KeyValueRow(langText("halte.max"), Fmt.signedPct(gewaehlt.max, 1), farbe(gewaehlt.max))
                KeyValueRow(langText("halte.q3"), Fmt.signedPct(gewaehlt.q3, 1), farbe(gewaehlt.q3))
                KeyValueRow(
                    langText("halte.median"), Fmt.signedPct(gewaehlt.median, 1), farbe(gewaehlt.median),
                    hint = langText("halte.faelle", gewaehlt.faelle.toString()),
                )
                KeyValueRow(langText("halte.q1"), Fmt.signedPct(gewaehlt.q1, 1), farbe(gewaehlt.q1))
                KeyValueRow(langText("halte.min"), Fmt.signedPct(gewaehlt.min, 1), farbe(gewaehlt.min))
                KeyValueRow(langText("halte.verlust"), Fmt.pct(gewaehlt.verlust * 100.0, 0))
            }
        }
    } else {
        // Kernaussage: reale Rendite je Jahr, eine Zeile je Mischung
        Text(
            langText("kern", e0.von.toString(), e0.bis.toString()), style = MaterialTheme.typography.bodySmall, color = grau,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 2.dp),
        )
        spalten.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                BtPunkt(s.farbe)
                Spacer(Modifier.width(10.dp))
                Text(s.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    Fmt.signedPct(s.e.rendite, 1), style = MaterialTheme.typography.titleLarge,
                    color = if (s.e.rendite >= 0) signal.up else signal.down, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LangVerlauf(linien, signal.down)

        val es = remember(spalten) { spalten.map { it.e } }
        val dauern = remember(es) { LangeVergleich.gemeinsameDauern(es) }
        val gew = LangeVergleich.waehleDauer(dauern, dauer)
        val halten = gew?.let { g -> es.map { LangeVergleich.halte(it, g) } }?.takeIf { l -> l.all { it != null } }?.map { it!! }

        // Vergleichstabelle: Farbpunkt und Name je Spalte, Werte in festen Spalten
        val zeilen = listOfNotNull(
            LangZeile(langText("ende", usd(1000.0)), spalten.map { LangZelle(usd(it.e.verlauf.last() * 1000.0)) }),
            LangZeile(
                langText("rueckgang"),
                spalten.map { s ->
                    val r = s.e.rueckgang
                    if (r == null) LangZelle("–") else LangZelle(Fmt.signedPct(r.tiefe, 0), signal.down, langText("t.zeitraum", r.hoch.toString(), r.tief.toString()))
                },
            ),
            LangZeile(
                langText("t.erholt"),
                spalten.map { s ->
                    val r = s.e.rueckgang
                    val j = r?.jahreBisErholt
                    when {
                        r == null -> LangZelle("–")
                        j != null -> LangZelle(S.toolYears(j))
                        else -> LangZelle(langText("t.offen"), signal.down)
                    }
                },
            ),
            if (gew != null && halten != null) {
                LangZeile(langText("t.verlust", S.toolYearsDativ(gew)), halten.map { LangZelle(Fmt.pct(it.verlust * 100.0, 0)) })
            } else null,
        )
        LangTabelle(spalten, zeilen)

        // Verteilung je Haltedauer: eingeklappt der Verlustanteil je Mischung
        if (gew != null && halten != null) {
            val kurz = spalten.indices.joinToString(", ") { spalten[it].name + " " + Fmt.pct(halten[it].verlust * 100.0, 0) }
            AufklappKopf(langText("halte.titel"), langText("halte.kurz.mehr", S.toolYears(gew), kurz), offen, maxZeilen = 3) { offen = !offen }
            if (offen) {
                LangSpannenMehr(spalten, halten)
                ToolZeit(langText("halte.wahl"), dauern.map { S.toolYearsKurz(it) to it }, gew) { dauer = it }
                fun farbe(v: Double) = if (v >= 0) signal.up else signal.down
                fun zeile(name: String, wert: (LangeReihen.Haltedauer) -> Double) =
                    LangZeile(name, halten.map { LangZelle(Fmt.signedPct(wert(it), 1), farbe(wert(it))) })
                LangTabelle(
                    spalten,
                    listOf(
                        zeile(langText("halte.max")) { it.max }, zeile(langText("halte.q3")) { it.q3 }, zeile(langText("halte.median")) { it.median },
                        zeile(langText("halte.q1")) { it.q1 }, zeile(langText("halte.min")) { it.min },
                    ),
                )
            }
        }
    }

    WaehrungAbschnitt(st.datei)

    // Naeherungen der gewaehlten Mischungen hinter dem Info-Symbol
    val naeherungen = spalten.flatMap { it.vorgabe.naeherungen }.distinct()
    if (naeherungen.isNotEmpty()) {
        HinweisInfo(naeherungen.joinToString("\n\n") { langText("nae.$it") }, kurz = langText("nae.kurz"))
    }
    HinweisInfo(langText("hinweis", st.laender.toString()), kurz = hinweisText("hist"))
    val gold = spalten.any { it.vorgabe.gold > 0 }
    val rohstoffe = spalten.any { it.vorgabe.prozent.containsKey(LangeReihen.Teil.ROHSTOFFE) }
    val klein = spalten.any { it.vorgabe.prozent.containsKey(LangeReihen.Teil.KLEIN_WERT) }
    val kurse = remember(st.datei) { st.datei?.let { WaehrungReihen.verfuegbar(it).isNotEmpty() } == true }
    QuellenAnhang(
        listOfNotNull(
            histQuelle(),
            if (gold || rohstoffe) pflichtQuelle("World Bank", "https://www.worldbank.org/en/research/commodity-markets") else null,
            if (gold || klein) QuelleLink("U.S. Bureau of Labor Statistics", "https://www.bls.gov/cpi/") else null,
            if (rohstoffe) QuelleLink("Board of Governors of the Federal Reserve System", "https://www.federalreserve.gov/releases/h15/") else null,
            if (kurse) QuelleLink("Board of Governors of the Federal Reserve System (H.10)", "https://www.federalreserve.gov/releases/h10/") else null,
            if (klein) QuelleLink("Kenneth R. French Data Library", FaktorAbruf.FRENCH_SEITE) else null,
        ),
    )
}

// Tabelle mit Bezeichnung links und festen Wertspalten rechts, je Mischung eine Spalte mit Farbpunkt und Namen im Kopf.
// Werte bleiben einzeilig, Bezeichnungen und Namen brechen um; die Unterzeile einer Zelle ist klein.
@Composable
private fun LangTabelle(spalten: List<LangSpalte>, zeilen: List<LangZeile>) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val breite = when (spalten.size) { 1 -> 112.dp; 2 -> 100.dp; else -> 86.dp }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.weight(1f))
            spalten.forEach { s ->
                Column(Modifier.width(breite).padding(start = 6.dp), horizontalAlignment = Alignment.End) {
                    BtPunkt(s.farbe)
                    Spacer(Modifier.height(4.dp))
                    Text(s.name, style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.End)
                }
            }
        }
        zeilen.forEachIndexed { i, z ->
            if (i > 0) Trennlinie(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(z.name, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.weight(1f))
                z.zellen.forEach { c ->
                    Column(Modifier.width(breite).padding(start = 6.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            c.text, style = MaterialTheme.typography.bodyMedium, color = c.farbe ?: MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                        )
                        c.unter?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }
    }
}

// Anteil der Hoehe, den der Rand oben und unten im Verlauf frei laesst (4 dp von 140 dp)
private const val LANG_RAND = 4f / 140f
private val LANG_BESCHRIFTUNG = 108.dp

// Reales Vermoegen ueber die ganze Zeit, logarithmisch, alle Linien auf einer Achse. Bei einer Mischung der tiefste Rueckgang
// vom Hoch bis zum Tief in der Verlustfarbe; bei mehreren Name und Endwert am Linienende (weichen einander aus), ohne
// Rueckgangsmarkierung, damit das Bild ruhig bleibt. Darunter erstes und letztes Jahr.
@Composable
private fun LangVerlauf(linien: List<LangLinie>, farbeRueckgang: Color) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val text = MaterialTheme.colorScheme.onSurface
    val skala = remember(linien) { LangeVergleich.logSkala(linien.map { it.e.verlauf }) } ?: return
    val einzeln = linien.size == 1
    val e0 = linien[0].e
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Canvas(Modifier.weight(1f).height(140.dp)) {
            val n = e0.verlauf.size
            if (n < 2) return@Canvas
            val rand = 4.dp.toPx()
            val breite = size.width - 2 * rand
            fun punkt(l: LangLinie, i: Int) =
                Offset(rand + breite * i / (n - 1), size.height * LangeVergleich.yBruch(skala.n(l.e.verlauf[i]), LANG_RAND))
            fun pfad(l: LangLinie, von: Int, bis: Int) = Path().apply {
                for (i in von..bis) punkt(l, i).let { if (i == von) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
            }
            drawLine(grau.copy(alpha = 0.6f), Offset(rand, size.height - rand), Offset(size.width - rand, size.height - rand), strokeWidth = 1.dp.toPx())
            val strich = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            linien.forEach { l -> drawPath(pfad(l, 0, n - 1), l.farbe, style = strich) }
            if (einzeln) {
                val l = linien[0]
                l.e.rueckgang?.let { r ->
                    val a = r.hoch - (l.e.von - 1)
                    val b = r.tief - (l.e.von - 1)
                    drawPath(pfad(l, a, b), farbeRueckgang, style = strich)
                    drawCircle(farbeRueckgang, 3.dp.toPx(), punkt(l, b))
                }
            }
            linien.forEach { l -> drawCircle(l.farbe, 3.dp.toPx(), punkt(l, n - 1)) }
        }
        if (!einzeln) {
            Spacer(Modifier.width(8.dp))
            LangEndLabels(
                linien.map { LangeVergleich.yBruch(skala.n(it.e.verlauf.last()), LANG_RAND) },
                Modifier.width(LANG_BESCHRIFTUNG).height(140.dp),
            ) {
                linien.forEach { l ->
                    Column {
                        Text(l.name, style = MaterialTheme.typography.labelSmall, color = l.farbe)
                        Text(
                            usd(l.e.verlauf.last() * 1000.0), style = MaterialTheme.typography.labelLarge, color = text,
                            maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = if (einzeln) 20.dp else 20.dp + LANG_BESCHRIFTUNG + 8.dp)) {
        val stil = MaterialTheme.typography.labelSmall
        Text((e0.von - 1).toString(), style = stil, color = grau, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Text(e0.bis.toString(), style = stil, color = grau, maxLines = 1, softWrap = false)
    }
}

// Beschriftungen am Linienende: jede steht auf der Hoehe ihrer Linie (ziele: Anteil der Hoehe), ueberlappende rutschen
// auseinander (LangeVergleich.ausweichen); gemessen wird die echte Texthoehe, nichts wird abgeschnitten.
@Composable
private fun LangEndLabels(ziele: List<Float>, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { messbar, c ->
        val teile = messbar.map { it.measure(Constraints(maxWidth = c.maxWidth)) }
        val h = c.maxHeight.toFloat()
        val oben = LangeVergleich.ausweichen(ziele.map { it * h }, teile.map { it.height.toFloat() }, h)
        layout(c.maxWidth, c.maxHeight) { teile.forEachIndexed { i, p -> p.place(0, oben[i].roundToInt()) } }
    }
}

// Je Haltedauer eine Zeile auf gemeinsamer Skala: Strich vom schlechtesten zum besten Zeitraum (unter null in der
// Verlustfarbe), Kasten vom unteren zum oberen Viertel, Marke am Median; rechts der Median als Zahl.
@Composable
private fun LangSpannen(dauern: List<LangeReihen.Haltedauer>, farbeAuf: Color, farbeAb: Color) {
    val text = MaterialTheme.colorScheme.onSurface
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val unten = minOf(0.0, dauern.minOf { it.min })
    val oben = maxOf(0.0, dauern.maxOf { it.max })
    val spanne = (oben - unten).takeIf { it > 0 } ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        dauern.forEach { h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    S.toolYearsKurz(h.jahre), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false,
                    modifier = Modifier.widthIn(min = 36.dp),
                )
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    fun x(v: Double) = (size.width * ((v - unten) / spanne)).toFloat()
                    val mitte = size.height / 2
                    val null0 = x(0.0)
                    drawLine(grau.copy(alpha = 0.5f), Offset(null0, 0f), Offset(null0, size.height), strokeWidth = 1.dp.toPx())
                    val strich = 2.dp.toPx()
                    if (h.min < 0) drawLine(farbeAb, Offset(x(h.min), mitte), Offset(x(minOf(0.0, h.max)), mitte), strokeWidth = strich, cap = StrokeCap.Round)
                    if (h.max > 0) drawLine(farbeAuf, Offset(x(maxOf(0.0, h.min)), mitte), Offset(x(h.max), mitte), strokeWidth = strich, cap = StrokeCap.Round)
                    val kasten = 10.dp.toPx()
                    drawRect(text.copy(alpha = 0.28f), Offset(x(h.q1), mitte - kasten / 2), Size(maxOf(1f, x(h.q3) - x(h.q1)), kasten))
                    drawLine(text, Offset(x(h.median), mitte - kasten / 2 - 2.dp.toPx()), Offset(x(h.median), mitte + kasten / 2 + 2.dp.toPx()), strokeWidth = strich)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    Fmt.signedPct(h.median, 1), style = MaterialTheme.typography.bodySmall, color = if (h.median >= 0) farbeAuf else farbeAb,
                    maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 56.dp),
                )
            }
        }
    }
}

// Spannen der gewaehlten Haltedauer, je Mischung eine Zeile in ihrer Farbe auf gemeinsamer Skala: Strich vom schlechtesten
// zum besten Zeitraum, farbiger Kasten vom unteren zum oberen Viertel, Marke am Median; rechts neben dem Namen der Median.
@Composable
private fun LangSpannenMehr(spalten: List<LangSpalte>, halten: List<LangeReihen.Haltedauer>) {
    val signal = LocalSignal.current
    val text = MaterialTheme.colorScheme.onSurface
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val unten = minOf(0.0, halten.minOf { it.min })
    val oben = maxOf(0.0, halten.maxOf { it.max })
    val spanne = (oben - unten).takeIf { it > 0 } ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        spalten.forEachIndexed { i, s ->
            val h = halten[i]
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BtPunkt(s.farbe)
                    Spacer(Modifier.width(8.dp))
                    Text(s.name, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Fmt.signedPct(h.median, 1), style = MaterialTheme.typography.bodySmall, color = if (h.median >= 0) signal.up else signal.down,
                        maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                    )
                }
                Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                    fun x(v: Double) = (size.width * ((v - unten) / spanne)).toFloat()
                    val mitte = size.height / 2
                    val null0 = x(0.0)
                    drawLine(grau.copy(alpha = 0.5f), Offset(null0, 0f), Offset(null0, size.height), strokeWidth = 1.dp.toPx())
                    val strich = 2.dp.toPx()
                    drawLine(grau.copy(alpha = 0.8f), Offset(x(h.min), mitte), Offset(x(h.max), mitte), strokeWidth = strich, cap = StrokeCap.Round)
                    val kasten = 12.dp.toPx()
                    drawRect(s.farbe, Offset(x(h.q1), mitte - kasten / 2), Size(maxOf(2f, x(h.q3) - x(h.q1)), kasten))
                    drawLine(text, Offset(x(h.median), mitte - kasten / 2 - 2.dp.toPx()), Offset(x(h.median), mitte + kasten / 2 + 2.dp.toPx()), strokeWidth = strich)
                }
            }
        }
    }
}
