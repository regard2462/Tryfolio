package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.Faktorzerlegung
import de.troetenrepublik.app.engine.Faktorzerlegung.Faktor
import de.troetenrepublik.app.i18n.FaktorTexte
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.FaktorAbruf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max

// Werkzeug „Faktoren“ (0.78): Regression der Monatsrenditen des Depots in heutiger Zusammensetzung auf Markt, Groesse,
// Wert, Qualitaet und Momentum (engine/Faktorzerlegung.kt). Kernzahl ist die nicht erklaerte Rendite im Jahr mit ihrer
// Spanne, darunter die Ladungen als Balken mit Unsicherheit, eingeklappt Beitraege und der rollende Verlauf.

private fun fkText(key: String, vararg w: String): String = FaktorTexte.text(I18n.lang == Lang.DE, key, w.toList())

private const val USA = "usa"
private const val TAG_MS = 86_400_000L
private val FAKTOR_JAHRE = listOf(3, 5, 10)

// Monatsrenditen des Depots in Dollar; usAnteil: Anteil der Werte mit Handelswaehrung Dollar; ohneKurse: Werte ohne Reihe.
private class DepotReihe(val monate: Map<Int, Pair<Double, Double>>, val usAnteil: Double, val ohneKurse: Int)

private suspend fun depotReihe(state: AppState, items: List<ValuedPosition>): DepotReihe = coroutineScope {
    val jetzt = System.currentTimeMillis()
    val summe = items.sumOf { it.value }
    // Tageskurse je Wert, vier gleichzeitig
    val kurse = HashMap<String, List<Pair<Long, Double>>>()
    for (teil in items.map { it.position.symbol }.distinct().chunked(4)) {
        teil.map { s -> async { s to state.dailyCloses(s).map { it.t to it.close } } }.awaitAll().forEach { kurse[it.first] = it.second }
    }
    val wechsel = HashMap<String, List<Pair<Long, Double>>>()
    var us = 0.0
    var ohne = 0
    val bestand = items.map { vp ->
        val s = vp.position.symbol
        val reihe = kurse[s].orEmpty()
        val waehrung = state.quotes.value[s]?.nativeCurrency?.let { ExchangeRates.hauptwaehrung(it) }
        if (waehrung == "USD") us += vp.value
        // Andere Handelswaehrungen ueber ihren Dollarkurs umrechnen; ohne ihn zaehlt der Wert nicht mit
        val fx: List<Pair<Long, Double>>? = when {
            waehrung == null -> null
            waehrung == "USD" -> emptyList()
            else -> wechsel.getOrPut(waehrung) { state.dailyCloses(waehrung + "USD=X").map { it.t to it.close } }.takeIf { it.size >= 40 }
        }
        val renditen = if (reihe.size < 40 || fx == null) emptyMap() else Faktorzerlegung.monatsrenditen(reihe, fx.takeIf { it.isNotEmpty() }, jetzt)
        if (renditen.isEmpty()) ohne++
        Faktorzerlegung.Bestand(vp.value, renditen)
    }
    DepotReihe(withContext(Dispatchers.Default) { Faktorzerlegung.depotMonate(bestand) }, if (summe > 0) us / summe else 0.0, ohne)
}

private class FaktorStand(
    val ergebnis: Faktorzerlegung.Ergebnis?, val rollend: List<Pair<Int, Faktorzerlegung.Ergebnis>>, val abdeckung: Double, val monate: Int,
    val datenFehlen: Boolean,
)

@Composable
internal fun FaktorTool(state: AppState) {
    val valuation = depotBewertung(state)
    val items = valuation.positions.filter { !it.position.isDerivative && it.value > 0 }
    if (items.isEmpty()) { ToolNote(S.ptNoPurchases); return }
    // Leer: nach dem Depot gewaehlt (ueberwiegend Dollarwerte: USA, sonst Welt)
    var wahl by rememberSaveable { mutableStateOf("") }
    var jahre by rememberSaveable { mutableIntStateOf(5) }
    val schluessel = items.joinToString("|") { it.key + ":" + it.qty }
    val depot by produceState<DepotReihe?>(null, schluessel) {
        value = runCatching { depotReihe(state, items) }.getOrNull() ?: DepotReihe(emptyMap(), 0.0, items.size)
    }
    val d = depot
    val region = wahl.ifEmpty { if (d == null || d.usAnteil >= 0.6) USA else FaktorAbruf.WELT }
    ToolChoice(
        fkText("faktoren"), listOf(fkText("region.usa") to USA, fkText("region.welt") to FaktorAbruf.WELT, fkText("region.ohneUsa") to FaktorAbruf.WELT_OHNE_USA),
        region,
    ) { wahl = it }
    ToolZeit(S.widgetZeitraum, FAKTOR_JAHRE.map { S.toolYearsKurz(it) to it }, jahre) { jahre = it }
    if (d == null) { ToolNote(S.toolLoading); return }
    val stand by produceState<FaktorStand?>(null, d, region, jahre) {
        value = null
        val daten = runCatching { if (region == USA) FaktorAbruf.french() else FaktorAbruf.jkp(region) }.getOrNull()
        value = withContext(Dispatchers.Default) {
            val renditen = d.monate.mapValues { it.value.first }
            if (daten == null) FaktorStand(null, emptyList(), 0.0, 0, true)
            else {
                val e = Faktorzerlegung.zerlegen(renditen, daten, jahre * 12)
                val gemeinsam = Faktorzerlegung.gemeinsameMonate(renditen, daten).takeLast(jahre * 12)
                FaktorStand(
                    e, Faktorzerlegung.rollend(renditen, daten, jahre * 12), gemeinsam.mapNotNull { d.monate[it]?.second }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                    gemeinsam.size, false,
                )
            }
        }
    }
    val s = stand
    if (s == null) { ToolNote(S.toolLoading); return }
    val e = s.ergebnis
    if (e == null) {
        ToolNote(if (s.datenFehlen) fkText("ohneDaten") else fkText("zuKurz", Faktorzerlegung.MIN_MONATE.toString(), s.monate.toString()))
        FaktorQuellen(region)
        return
    }
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant

    // Kernaussage in Alltagssprache: Rendite im Jahr gegen das, was die Ausrichtung erwarten laesst, mit Spanne
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 6.dp)) {
        InfoKopf(fkText("alpha.titel"), fkText("alpha.info"))
        Text(
            Fmt.signedPct(e.alphaJahr * 100, 1), style = MaterialTheme.typography.headlineMedium,
            color = if (e.alphaBelegt) signal.forChange(e.alphaJahr) else MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false,
        )
        Text(fkText(if (e.alphaJahr >= 0) "alpha.ueber" else "alpha.unter"), style = MaterialTheme.typography.bodyMedium)
        Text(fkText(if (e.alphaBelegt) "alpha.belegt" else "alpha.offen"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        Text(
            fkText("spanne", Fmt.signedPct(e.alphaVon * 100, 1), Fmt.signedPct(e.alphaBis * 100, 1)), style = MaterialTheme.typography.labelSmall,
            color = grau, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp),
        )
    }
    KeyValueRow(
        fkText("erklaert"), Fmt.pct(e.r2 * 100, 0),
        hint = fkText("zeitraum", e.monate.toString(), monatText(e.von), monatText(e.bis)),
    )
    KeyValueRow(
        fkText("abdeckung"), Fmt.pct(s.abdeckung * 100, 0),
        hint = if (d.ohneKurse > 0) fkText("ohneKurse", d.ohneKurse.toString()) else null,
    )

    // Ladungen als Wort und Skala mit Marker und Spanne
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp)) {
        InfoKopf(fkText("ladungen.titel"), fkText("ladungen.info"))
    }
    e.ladungen.forEach { l -> LadungZeile(l) }

    AufklappAbschnitt("faktor-beitraege", fkText("beitrag.titel"), fkText("beitrag.info")) {
        KeyValueRow(fkText("beitrag.zins"), Fmt.signedPct(e.zinsJahr * 100, 1))
        e.ladungen.forEach { l -> KeyValueRow(fkText("f." + l.faktor.name), Fmt.signedPct(l.beitragJahr * 100, 1), valueColor = signal.forChange(l.beitragJahr)) }
        KeyValueRow(fkText("beitrag.rest"), Fmt.signedPct(e.alphaJahr * 100, 1), valueColor = if (e.alphaBelegt) signal.forChange(e.alphaJahr) else null)
        KeyValueRow(fkText("beitrag.summe"), Fmt.signedPct(e.renditeJahr * 100, 1), hint = fkText("beitrag.summeHinweis"))
    }
    AufklappAbschnitt("faktor-rollend", fkText("rollend.titel", Faktorzerlegung.FENSTER.toString()), fkText("rollend.info", Faktorzerlegung.FENSTER.toString())) {
        if (s.rollend.size < 2) LeerZustand(fkText("rollend.leer", Faktorzerlegung.ROLLEND_AB.toString()))
        else Faktor.entries.forEach { f ->
            Zwischenkopf(fkText("f." + f.name))
            val punkte = remember(s.rollend, f) { s.rollend.mapNotNull { (m, r) -> r.ladung(f)?.let { monatsEnde(m) to it.wert } } }
            VerlaufDiagramm(
                punkte, Kategorie.blau, { Fmt.num(it, 2) }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), hoehe = 96.dp,
                lueckeMs = 100 * TAG_MS, linien = listOf(0.0 to grau.copy(alpha = 0.5f)),
            )
        }
    }
    HinweisInfo(fkText("hinweis"), kurz = hinweisText("histPrognose"))
    FaktorQuellen(region)
}

@Composable
private fun FaktorQuellen(region: String) {
    QuellenAnhang(
        listOfNotNull(
            QuelleLink(fkText("quelle.french"), FaktorAbruf.FRENCH_SEITE),
            if (region != USA) QuelleLink(fkText("quelle.jkp"), FaktorAbruf.JKP_SEITE) else null,
            if (region != USA) QuelleLink("CC BY-NC 4.0", FaktorAbruf.JKP_LIZENZ) else null,
        ),
    )
}

private fun monatText(m: Int): String = Fmt.monthShortYear(YearMonth.of(Faktorzerlegung.jahrVon(m), Faktorzerlegung.monatVon(m)))

private fun monatsEnde(m: Int): Long =
    LocalDate.of(Faktorzerlegung.jahrVon(m), Faktorzerlegung.monatVon(m), 1).plusMonths(1).toEpochDay() * TAG_MS - TAG_MS

// Ueberschrift mit Info-Symbol; die Erklaerung oeffnet sich im Blatt.
@Composable
private fun InfoKopf(titel: String, info: String) {
    var offen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { offen = true }) {
            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    if (offen) MarktInfoBlatt(titel, info) { offen = false }
}

// Eine Ladung: Name und Bedeutung als Wort links, Zahl und Spanne klein rechts, darunter eine Skala mit beschrifteten Enden:
// Marker fuer die Ladung, helles Band fuer die 95-%-Spanne, feine Linie bei neutral.
@Composable
private fun LadungZeile(l: Faktorzerlegung.Ladung) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val schrift = MaterialTheme.colorScheme.onSurface
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val neutral = if (l.faktor == Faktor.MARKT) 1.0 else 0.0
    val lo = if (l.faktor == Faktor.MARKT) 0.0 else -1.0
    val hi = if (l.faktor == Faktor.MARKT) 2.0 else 1.0
    // Nicht belegte Ladungen blasser
    val farbe = if (l.belegt) Kategorie.blau else Kategorie.blau.copy(alpha = 0.6f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(fkText("f." + l.faktor.name), style = MaterialTheme.typography.titleSmall)
                Text(fkWort(l.faktor, l.wert), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(ladungText(l.faktor, l.wert), style = MaterialTheme.typography.labelMedium, color = grau, maxLines = 1, softWrap = false)
                Text(
                    fkText("vonBis", ladungText(l.faktor, l.von), ladungText(l.faktor, l.bis)), style = MaterialTheme.typography.labelSmall, color = grau,
                    maxLines = 1, softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            fun x(v: Double) = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat() * size.width
            val mitte = size.height / 2
            val dick = 6.dp.toPx()
            drawRoundRect(bahn, Offset(0f, mitte - dick / 2), Size(size.width, dick), CornerRadius(dick / 2, dick / 2))
            // Spanne als dezentes Band
            val a = x(l.von)
            val b = x(l.bis)
            drawRoundRect(farbe.copy(alpha = 0.30f), Offset(a, mitte - dick / 2), Size(max(b - a, 2.dp.toPx()), dick), CornerRadius(dick / 2, dick / 2))
            // Neutral
            drawLine(grau, Offset(x(neutral), 1.dp.toPx()), Offset(x(neutral), size.height - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawCircle(schrift, radius = 7.dp.toPx(), center = Offset(x(l.wert), mitte))
            drawCircle(farbe, radius = 5.dp.toPx(), center = Offset(x(l.wert), mitte))
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fkText("f." + l.faktor.name + ".links"), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(fkText("f." + l.faktor.name + ".rechts"), style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

// Ladung in Alltagssprache; nahe null (bei Markt nahe eins) neutral.
internal fun fkWort(f: Faktor, v: Double): String = fkText(
    "w." + f.name + "." + when {
        f == Faktor.MARKT -> if (v < 0.85) "lo" else if (v > 1.15) "hi" else "mid"
        abs(v) < 0.15 -> "null"
        v > 0 -> "pos"
        else -> "neg"
    },
)

// Markt ohne Vorzeichen (um 1), die uebrigen Ladungen mit Vorzeichen (um 0).
private fun ladungText(f: Faktor, v: Double): String =
    if (f == Faktor.MARKT) Fmt.num(v, 2) else (if (v > 0.004_9) "+" else if (v < -0.004_9) "−" else "") + Fmt.num(abs(v), 2)
