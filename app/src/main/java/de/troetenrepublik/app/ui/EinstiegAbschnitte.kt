package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.engine.EinstiegVergleich
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.EinstiegTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

// Zwei Abschnitte des Sparplan-Rueckblicks (0.78): Einmalanlage gegen Raten ueber alle Startmonate und Ruecksetzer
// kaufen gegen stur sparen. Rechnung in engine/EinstiegVergleich.kt, Kurse kommen vom Werkzeug.

private fun einstiegText(key: String, vararg w: String): String = EinstiegTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Auswahl beider Abschnitte; lebt im Werkzeug, damit sie das Neuladen der Kurse uebersteht. horizont 0 = Ende der
// Raten, warten 0 = nie.
internal class EinstiegWahl(raten: Int = 12, horizont: Int = 0, schwelle: Int = 10, warten: Int = 12) {
    var raten by mutableIntStateOf(raten)
    var horizont by mutableIntStateOf(horizont)
    var schwelle by mutableIntStateOf(schwelle)
    var warten by mutableIntStateOf(warten)
    var einmalOffen by mutableStateOf(false)
    var dipOffen by mutableStateOf(false)

    companion object {
        val Sicherung = listSaver<EinstiegWahl, Int>(
            save = { listOf(it.raten, it.horizont, it.schwelle, it.warten, if (it.einmalOffen) 1 else 0, if (it.dipOffen) 1 else 0) },
            restore = { l -> EinstiegWahl(l[0], l[1], l[2], l[3]).apply { einmalOffen = l[4] == 1; dipOffen = l[5] == 1 } },
        )
    }
}

@Composable
internal fun rememberEinstiegWahl(): EinstiegWahl = rememberSaveable(saver = EinstiegWahl.Sicherung) { EinstiegWahl() }

private val RATEN = listOf(6, 12, 24, 36)
private val HORIZONTE = listOf(60, 120)
private val SCHWELLEN = listOf(5, 10, 15, 20)
private val WARTEN = listOf(6, 12, 24, 0)

private fun monat(t: Long): String = Fmt.monthYear(YearMonth.from(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())))

@Composable
internal fun EinstiegAbschnitte(
    name: String, daily: List<Candle>, divs: List<MarketApi.DividendEvent>, rate: Double, spreadBps: Double, wahl: EinstiegWahl,
) {
    val monate = remember(daily, divs) {
        EinstiegVergleich.monatsenden(Analysis.thesaurierend(daily.filter { it.close > 0 && it.close.isFinite() }, divs))
    }
    val kurse = remember(monate) { monate.map { it.kurs } }
    val kosten = remember(spreadBps) { EinstiegVergleich.Kosten(spreadBps) }
    Trennlinie()
    EinmalAbschnitt(name, monate, kurse, rate, kosten, wahl)
    Trennlinie()
    RuecksetzerAbschnitt(monate, kurse, rate, kosten, wahl)
    HinweisInfo(einstiegText("hinweis", Fmt.euro(Trading.FEE, 0)))
}

@Composable
private fun Kopf(titel: String, kernzahl: String?, farbe: Color, satz: String, zusatz: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp)) {
        Text(titel, style = MaterialTheme.typography.titleSmall)
        if (kernzahl != null) Text(kernzahl, style = MaterialTheme.typography.headlineSmall, color = farbe, maxLines = 1, softWrap = false)
        Text(satz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        if (zusatz != null) Text(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EinmalAbschnitt(name: String, monate: List<EinstiegVergleich.Monat>, kurse: List<Double>, rate: Double, kosten: EinstiegVergleich.Kosten, wahl: EinstiegWahl) {
    val raten = wahl.raten
    val betrag = rate * raten
    // Laengere Horizonte nur, wenn die Reihe genug Startmonate dafuer hat
    val horizonte = remember(kurse.size, raten) { HORIZONTE.filter { it > raten && kurse.size - it >= EinstiegVergleich.MIN_ZEITRAEUME } }
    val horizont = if (wahl.horizont in horizonte) wahl.horizont else raten
    val v = remember(kurse, betrag, raten, horizont, kosten) { EinstiegVergleich.einmalGegenRaten(kurse, betrag, raten, horizont, kosten) }
    val einstellungen: @Composable () -> Unit = {
        ToolChoice(einstiegText("einmal.raten"), RATEN.map { "$it" to it }, raten) { wahl.raten = it }
        if (horizonte.isNotEmpty()) ToolChoice(
            einstiegText("einmal.stand"), listOf(einstiegText("einmal.ende") to raten) + horizonte.map { S.toolYearsKurz(it / 12) to it }, horizont,
        ) { wahl.horizont = if (it == raten) 0 else it }
    }
    if (v == null) {
        Kopf(einstiegText("einmal.titel"), null, Color.Unspecified, einstiegText("kurz"))
        einstellungen()
        Spacer(Modifier.height(12.dp))
        return
    }
    val blau = Kategorie.blau
    val ocker = Kategorie.ocker
    val nach = if (horizont % 12 == 0 && horizont > raten) einstiegText("jahren", "${horizont / 12}") else einstiegText("monaten", "$horizont")
    Kopf(
        einstiegText("einmal.titel"), Fmt.pct(v.anteil, 0), if (v.anteil >= 50) blau else ocker,
        einstiegText("einmal.satz", "${v.vorn}", "${v.faelle.size}"),
        einstiegText("einmal.aufbau", Fmt.euro(betrag, 0), "$raten", name, nach),
    )
    val h = remember(v) { EinstiegVergleich.histogramm(v.faelle.map { it.vorsprung }) }
    if (h != null) VorsprungVerteilung(h, ocker, blau)
    einstellungen()
    AufklappZeile(
        einstiegText("details"), einstiegText("einmal.kurz", Fmt.signedPct(v.median, 1), Fmt.signedPct(v.mittel, 1)), wahl.einmalOffen,
    ) { wahl.einmalOffen = !wahl.einmalOffen }
    if (wahl.einmalOffen) {
        val signal = LocalSignal.current
        KeyValueRow(einstiegText("einmal.median"), Fmt.signedPct(v.median, 1), valueColor = signal.forChange(v.median))
        KeyValueRow(einstiegText("einmal.mittel"), Fmt.signedPct(v.mittel, 1), valueColor = signal.forChange(v.mittel))
        KeyValueRow(
            einstiegText("einmal.bester"), Fmt.signedPct(v.bester.vorsprung, 1), valueColor = signal.forChange(v.bester.vorsprung),
            hint = einstiegText("einmal.start", monat(monate[v.bester.start].t)),
        )
        KeyValueRow(
            einstiegText("einmal.schlechtester"), Fmt.signedPct(v.schlechtester.vorsprung, 1), valueColor = signal.forChange(v.schlechtester.vorsprung),
            hint = einstiegText("einmal.start", monat(monate[v.schlechtester.start].t)),
        )
        KeyValueRow(
            einstiegText("einmal.rueckgang"), einstiegText("paar", Fmt.pct(v.rueckgangEinmal, 1), Fmt.pct(v.rueckgangRaten, 1)),
            hint = einstiegText("einmal.rueckgangHint"),
        )
    }
    Spacer(Modifier.height(6.dp))
}

// Verteilung des Vorsprungs: Balken je Klasse um die Nulllinie in der Mitte, links Raten vorn, rechts Einmalanlage
// vorn. Die Beschriftung steht ueber und unter der Zeichnung, nie auf den Balken.
@Composable
private fun VorsprungVerteilung(h: EinstiegVergleich.Histogramm, links: Color, rechts: Color) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val achse = MaterialTheme.colorScheme.onSurface
    val grenze = remember(h) { maxOf(abs(h.klassen.first().von), abs(h.klassen.last().von + h.breite)) }
    val hoechste = remember(h) { h.klassen.maxOf { it.anzahl }.coerceAtLeast(1) }
    val klein = MaterialTheme.typography.labelSmall
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(einstiegText("achse.raten"), style = klein, color = links, modifier = Modifier.weight(1f))
            Text(einstiegText("achse.einmal"), style = klein, color = rechts, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(96.dp)) {
            val boden = size.height - 1.dp.toPx()
            fun x(wert: Double) = (size.width * (wert + grenze) / (2 * grenze)).toFloat()
            val luecke = 1.dp.toPx()
            h.klassen.forEach { k ->
                if (k.anzahl == 0) return@forEach
                val x0 = x(k.von)
                val x1 = x(k.von + h.breite)
                val hoehe = (boden * k.anzahl / hoechste).coerceAtLeast(2.dp.toPx())
                drawRect(if (k.von < 0) links else rechts, Offset(x0 + luecke / 2, boden - hoehe), Size((x1 - x0 - luecke).coerceAtLeast(1f), hoehe))
            }
            drawLine(grau.copy(alpha = 0.6f), Offset(0f, boden), Offset(size.width, boden), strokeWidth = 1.dp.toPx())
            drawLine(achse, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1.5.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(Fmt.signedPct(-grenze, 0), style = klein, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            Text(Fmt.pct(0.0, 0), style = klein, color = grau, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(Fmt.signedPct(grenze, 0), style = klein, color = grau, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RuecksetzerAbschnitt(monate: List<EinstiegVergleich.Monat>, kurse: List<Double>, rate: Double, kosten: EinstiegVergleich.Kosten, wahl: EinstiegWahl) {
    val schwelle = wahl.schwelle
    val warten = wahl.warten.takeIf { it > 0 }
    val lauf = remember(kurse, rate, schwelle, warten, kosten) {
        EinstiegVergleich.ruecksetzer(kurse, EinstiegVergleich.hochs(kurse), rate, schwelle / 100.0, warten, kosten)
    }
    val roll = remember(kurse, rate, schwelle, warten, kosten) {
        EinstiegVergleich.horizont(kurse.size)?.let { EinstiegVergleich.ruecksetzerRollierend(kurse, rate, schwelle / 100.0, warten, it, kosten) }
    }
    val einstellungen: @Composable () -> Unit = {
        ToolChoice(einstiegText("dip.schwelle"), SCHWELLEN.map { Fmt.pct(it.toDouble(), 0) to it }, schwelle) { wahl.schwelle = it }
        ToolChoice(einstiegText("dip.hoechst"), WARTEN.map { (if (it == 0) einstiegText("dip.nie") else "$it") to it }, wahl.warten) { wahl.warten = it }
    }
    // Unter einem Jahr Kursgeschichte sagt der Vergleich nichts
    if (lauf == null || lauf.bis - lauf.von < EinstiegVergleich.MIN_ZEITRAEUME) {
        Kopf(einstiegText("dip.titel"), null, Color.Unspecified, einstiegText("kurz"))
        einstellungen()
        return
    }
    val signal = LocalSignal.current
    val sturFarbe = Kategorie.blau
    val wartenFarbe = Kategorie.ocker
    val beginn = monat(monate[lauf.von].t)
    if (roll != null) Kopf(
        einstiegText("dip.titel"), Fmt.pct(roll.anteil, 0), if (roll.anteil >= 50) wartenFarbe else sturFarbe,
        einstiegText("dip.satz", "${roll.vorn}", "${roll.zeitraeume}", "${roll.horizont / 12}"),
    ) else Kopf(
        einstiegText("dip.titel"), Fmt.signedPct(lauf.vorsprung, 1), signal.forChange(lauf.vorsprung), einstiegText("dip.satzGanz", beginn),
    )
    Spacer(Modifier.height(4.dp))
    KeyValueRow(einstiegText("dip.stur"), Fmt.euro(lauf.endStur, 0))
    KeyValueRow(einstiegText("dip.warten"), Fmt.euro(lauf.endWarten, 0), valueColor = signal.forChange(lauf.endWarten - lauf.endStur))
    val zeiten = remember(monate, lauf) { monate.subList(lauf.von, lauf.bis + 1).map { it.t } }
    VergleichsChart(
        linien = listOf(
            Linie(einstiegText("dip.stur"), zeiten.zip(lauf.verlaufStur), sturFarbe),
            Linie(einstiegText("dip.warten"), zeiten.zip(lauf.verlaufWarten), wartenFarbe),
        ),
        format = { Fmt.euro(it, 0) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        datumKopf = false,
    )
    KeyValueRow(einstiegText("dip.liegen"), einstiegText("monate", Fmt.num(lauf.wartenMittel, 1)))
    KeyValueRow(einstiegText("dip.laengste"), einstiegText("monate", "${lauf.wartenLaengste}"))
    einstellungen()
    AufklappZeile(
        einstiegText("details"),
        if (roll != null) einstiegText("dip.kurz", "${lauf.kaeufe}", Fmt.signedPct(roll.median, 1)) else einstiegText("dip.kurzGanz", "${lauf.kaeufe}"),
        wahl.dipOffen,
    ) { wahl.dipOffen = !wahl.dipOffen }
    if (wahl.dipOffen) {
        KeyValueRow(einstiegText("dip.seit", beginn), Fmt.euro(lauf.eingezahlt, 0))
        if (roll != null) KeyValueRow(einstiegText("dip.ganz", beginn), Fmt.signedPct(lauf.vorsprung, 1), valueColor = signal.forChange(lauf.vorsprung))
        KeyValueRow(einstiegText("dip.kaeufe"), "${lauf.kaeufe}")
        if (lauf.restBar > 0) KeyValueRow(einstiegText("dip.rest"), Fmt.euro(lauf.restBar, 0))
        if (roll != null) {
            KeyValueRow(einstiegText("dip.median"), Fmt.signedPct(roll.median, 1), valueColor = signal.forChange(roll.median))
            KeyValueRow(einstiegText("dip.mittel"), Fmt.signedPct(roll.mittel, 1), valueColor = signal.forChange(roll.mittel))
        }
    }
}
