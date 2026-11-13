package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import de.troetenrepublik.app.i18n.S
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.MarktSchlagen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.MarktTexte

// Werkzeug „Markt schlagen“ (0.78): je Baustein eine Kernzahl, ein Satz und eine Grafik; Zahlen und Rechnungen aus
// engine/MarktSchlagen.kt, Quellen gesammelt am Seitenende.

internal fun marktText(key: String, vararg w: String): String = MarktTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Spalten des Punktrasters; 100 Punkte ergeben fuenf Zeilen
private const val PUNKT_SPALTEN = 20

@Composable
internal fun MarktSchlagenTool(onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    val bezug = Kategorie.blau
    val m = MarktSchlagen

    // Aktive Aktienfonds gegen passive Fonds und ETFs
    MarktBaustein(MarktSchlagen.Baustein.FONDS, marktText("punkte", Fmt.num(m.fondsAbstand(), 1)), signal.down) {
        val max = m.FONDS_ETF.wert
        LageBalkenZeile(marktText("fonds.etf"), Fmt.pct(m.FONDS_ETF.wert, 1), m.FONDS_ETF.wert / max, bezug, null)
        LageBalkenZeile(marktText("fonds.passiv"), Fmt.pct(m.FONDS_PASSIV.wert, 1), m.FONDS_PASSIV.wert / max, bezug, null)
        LageBalkenZeile(marktText("fonds.aktiv"), Fmt.pct(m.FONDS_AKTIV.wert, 1), m.FONDS_AKTIV.wert / max, signal.down, null)
    }
    Trennlinie()

    // Indexfonds gegen ihren Index: Meldungen der Fonds an die SEC
    IndexAbstandBaustein()

    // Kosten ueber die Jahre: zwei Verlaeufe aus derselben Anlage
    val etf = remember { m.kostenVerlauf(m.KOSTEN_ETF.wert) }
    val aktiv = remember { m.kostenVerlauf(m.KOSTEN_AKTIV.wert) }
    MarktBaustein(
        MarktSchlagen.Baustein.KOSTEN, Fmt.euro(m.kostenLuecke(), 0), signal.down,
        titelWerte = listOf(Fmt.num(m.RECHNUNG_JAHRE.toDouble(), 0)),
        satzWerte = listOf(Fmt.pct(m.KOSTEN_AKTIV.wert, 2), Fmt.pct(m.KOSTEN_ETF.wert, 2), Fmt.euro(m.RECHNUNG_START, 0), Fmt.pct(m.RECHNUNG_BRUTTO, 0)),
    ) {
        MarktVerlauf(etf, aktiv, bezug, signal.down)
        Spacer(Modifier.height(6.dp))
        LageBalkenZeile(marktText("kosten.etf"), Fmt.euro(etf.last(), 0), 1.0, bezug, null)
        LageBalkenZeile(marktText("kosten.aktiv"), Fmt.euro(aktiv.last(), 0), aktiv.last() / etf.last(), signal.down, null)
    }
    Trennlinie()

    // Wenige Unternehmen erklaeren den Mehrertrag
    MarktBaustein(MarktSchlagen.Baustein.WENIGE, Fmt.pct(m.WENIGE_USA.wert, 0), signal.up) {
        MarktPunkte(m.punkte(m.WENIGE_USA.wert), signal.up)
    }
    Trennlinie()

    // Mehrheit der Aktien hinter kurzfristigen Staatsanleihen
    MarktBaustein(MarktSchlagen.Baustein.SCHATZ, Fmt.pct(m.SCHATZ_WELT.wert, 1), signal.down) {
        LageBalkenZeile(marktText("schatz.welt"), Fmt.pct(m.SCHATZ_WELT.wert, 1), m.SCHATZ_WELT.wert / 100, signal.down, null, marke = 0.5)
        LageBalkenZeile(marktText("schatz.usa"), Fmt.pct(m.SCHATZ_USA.wert, 1), m.SCHATZ_USA.wert / 100, signal.down, null, marke = 0.5)
    }
    Trennlinie()

    // Lebensrenditen der US-Aktien als Verteilung (mitgelieferte Auszaehlung)
    val leben = lebensrenditenDatei
    if (leben != null) {
        LebensrenditenBaustein(leben)
        Trennlinie()
    }

    // Renditemuster aus der Forschung vor und nach ihrer Veroeffentlichung (ui/MarktAnomalien.kt)
    MarktAnomalien()

    // Haushalte nach Handelshaeufigkeit
    MarktBaustein(
        MarktSchlagen.Baustein.HAUSHALTE, marktText("punkte", Fmt.num(m.haushalteAbstand(), 1)), signal.down,
        satzWerte = listOf(Fmt.num(m.HAUSHALTE_ANZAHL.wert, 0)),
    ) {
        val max = m.HAUSHALTE_MARKT.wert
        LageBalkenZeile(marktText("haushalte.markt"), Fmt.pct(m.HAUSHALTE_MARKT.wert, 1), 1.0, bezug, null)
        LageBalkenZeile(marktText("haushalte.schnitt"), Fmt.pct(m.HAUSHALTE_SCHNITT.wert, 1), m.HAUSHALTE_SCHNITT.wert / max, MaterialTheme.colorScheme.onSurface, null)
        LageBalkenZeile(marktText("haushalte.viel"), Fmt.pct(m.HAUSHALTE_VIEL.wert, 1), m.HAUSHALTE_VIEL.wert / max, signal.down, null)
    }
    Trennlinie()

    // Daytrader in Taiwan: hoechstens ein Punkt von hundert
    MarktBaustein(MarktSchlagen.Baustein.TAIWAN, marktText("unter", Fmt.pct(m.TAIWAN_GRENZE.wert, 0)), signal.up) {
        MarktPunkte(m.punkte(m.TAIWAN_GRENZE.wert), signal.up)
    }
    Trennlinie()

    // Daytrader in Brasilien
    val tage = Fmt.num(m.BRASILIEN_TAGE.wert, 0)
    MarktBaustein(
        MarktSchlagen.Baustein.BRASILIEN, Fmt.pct(m.BRASILIEN_VERLUST.wert, 0), signal.down, titelWerte = listOf(tage), satzWerte = listOf(tage),
    ) {
        MarktPunkte(m.punkte(m.BRASILIEN_VERLUST.wert), signal.down)
    }
    Trennlinie()

    // CFD-Konten: untere Grenze kraeftig, Spanne bis zur oberen Grenze blass
    MarktBaustein(MarktSchlagen.Baustein.CFD, marktText("spanne", Fmt.num(m.CFD_VON.wert, 0), Fmt.pct(m.CFD_BIS.wert, 0)), signal.down) {
        MarktPunkte(m.punkte(m.CFD_VON.wert), signal.down, blassBis = m.punkte(m.CFD_BIS.wert))
    }
    Trennlinie()

    // Was die Zahlen nicht sagen: hinter dem Info-Symbol, kein offener Textblock
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        MarktTitel(
            marktText("einordnung.titel"),
            listOf(
                marktText("einordnung.anleihen", Fmt.pct(m.ANLEIHEN_AKTIV.wert, 1), Fmt.pct(m.ANLEIHEN_PASSIV.wert, 1)),
                marktText("einordnung.taiwan", Fmt.num(m.TAIWAN_BESTE_ANZAHL.wert, 0), Fmt.pct(m.TAIWAN_BESTE.wert / 100.0, 2)),
                marktText("einordnung.zeitraum"),
            ).joinToString("\n\n"),
        )
    }
    Trennlinie()
    NavRow(marktText("depot.titel"), marktText("depot.unter")) { onOpen(SEITEN_PRAEFIX + "depotverlauf") }
    QuellenAnhang(
        m.QUELLEN.map { QuelleLink(it.name, it.url) } +
            listOfNotNull(
                leben?.let { QuelleLink(de.troetenrepublik.app.engine.Lebensrenditen.QUELLE_NAME, de.troetenrepublik.app.engine.Lebensrenditen.QUELLE_ADRESSE) },
                indexAbstandQuelle(),
            ),
    )
}

// Titel eines Bausteins; mit Erklaerung steht dahinter das Info-Symbol.
@Composable
internal fun MarktTitel(titel: String, info: String) {
    var offen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().then(if (info.isNotEmpty()) Modifier.clickable { offen = true } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
        if (info.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
    if (offen) MarktInfoBlatt(titel, info) { offen = false }
}

// Ein Baustein: Aussage, Kernzahl (einzeilig), ein Satz, darunter die Grafik.
@Composable
internal fun MarktBaustein(
    baustein: MarktSchlagen.Baustein, kernzahl: String, farbe: Color, titelWerte: List<String> = emptyList(), satzWerte: List<String> = emptyList(), infoWerte: List<String> = emptyList(),
    grafik: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        MarktTitel(
            marktText(baustein.name + ".titel", *titelWerte.toTypedArray()),
            marktText(baustein.name.lowercase() + ".info", *infoWerte.toTypedArray()),
        )
        Text(kernzahl, style = MaterialTheme.typography.headlineSmall, color = farbe, maxLines = 1, softWrap = false)
        Text(
            marktText(baustein.name + ".satz", *satzWerte.toTypedArray()), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        grafik()
    }
}

// Raster aus 100 Punkten: die ersten voll kraeftig, danach bis blassBis blass, der Rest in der Farbe der Bahn.
@Composable
private fun MarktPunkte(voll: Int, farbe: Color, blassBis: Int = voll) {
    val leer = MaterialTheme.colorScheme.surfaceContainerHighest
    val zeilen = MarktSchlagen.RASTER / PUNKT_SPALTEN
    Canvas(Modifier.fillMaxWidth().aspectRatio(PUNKT_SPALTEN.toFloat() / zeilen)) {
        val zelle = size.width / PUNKT_SPALTEN
        val radius = zelle * 0.34f
        for (i in 0 until MarktSchlagen.RASTER) {
            val mitte = Offset((i % PUNKT_SPALTEN + 0.5f) * zelle, (i / PUNKT_SPALTEN + 0.5f) * zelle)
            drawCircle(if (i < voll) farbe else if (i < blassBis) farbe.copy(alpha = 0.4f) else leer, radius, mitte)
        }
    }
}

// Zwei Verlaeufe derselben Anlage auf einer Skala ab null, je mit Endpunkt; die Werte stehen in den Zeilen darunter.
@Composable
private fun MarktVerlauf(oben: List<Double>, unten: List<Double>, farbeOben: Color, farbeUnten: Color) {
    if (oben.size < 2 || unten.size != oben.size) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val rand = 4.dp.toPx()
        val max = maxOf(oben.max(), unten.max()).takeIf { it > 0 } ?: return@Canvas
        val breite = size.width - 2 * rand
        val hoehe = size.height - 2 * rand
        fun punkt(i: Int, v: Double) = Offset(rand + breite * i / (oben.size - 1), rand + hoehe * (1f - (v / max).toFloat()))
        fun pfad(werte: List<Double>) = Path().apply {
            werte.forEachIndexed { i, v -> punkt(i, v).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawLine(grau.copy(alpha = 0.6f), Offset(rand, size.height - rand), Offset(size.width - rand, size.height - rand), strokeWidth = 1.dp.toPx())
        val strich = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(pfad(unten), farbeUnten, style = strich)
        drawPath(pfad(oben), farbeOben, style = strich)
        drawCircle(farbeUnten, 3.dp.toPx(), punkt(unten.size - 1, unten.last()))
        drawCircle(farbeOben, 3.dp.toPx(), punkt(oben.size - 1, oben.last()))
    }
}
