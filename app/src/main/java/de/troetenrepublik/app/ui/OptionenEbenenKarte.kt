package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.engine.IvHistorie
import de.troetenrepublik.app.engine.NasdaqGriechen
import de.troetenrepublik.app.engine.OptionsEbenen
import de.troetenrepublik.app.engine.OptionsVola
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Optionsebenen und Volatilitaetskarte unter der Optionenkarte der Wertpapierseite (0.76).

@Composable
private fun WertZeile(
    titel: String, wert: String, schluessel: String? = null, farbe: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    // Anteil von 0 bis 1 als Balken unter der Zeile (Rang und Perzentil)
    anteil: Float? = null,
) {
    var auf by remember { mutableStateOf(false) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().then(if (schluessel != null) Modifier.clickable { auf = !auf } else Modifier).padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = farbe, maxLines = 1, softWrap = false)
            if (schluessel != null) {
                Spacer(Modifier.width(6.dp))
                AufklappPfeil(auf)
            }
        }
        anteil?.let { a ->
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(a.coerceIn(0.02f, 1f)).fillMaxHeight().background(Kategorie.blau))
            }
        }
        if (schluessel != null && auf) StichpunktText(S.opErklaerung(schluessel), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
    }
}

// Put/Call-Verhaeltnis als geteilter Balken: Puts rot links, Calls gruen rechts, die Anteile an den Enden, das Verhaeltnis
// rechts neben der Bezeichnung.
@Composable
internal fun PcrBalken(titel: String, verhaeltnis: Double) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val puts = (verhaeltnis / (1.0 + verhaeltnis)).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
            Text(Fmt.num(verhaeltnis, 2), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        Row(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            if (puts > 0f) Box(Modifier.weight(puts).fillMaxHeight().background(signal.down))
            if (puts < 1f) Box(Modifier.weight(1f - puts).fillMaxHeight().background(signal.up))
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(S.opLegendePuts + " " + Fmt.pct(puts * 100.0, 0), style = MaterialTheme.typography.labelSmall, color = signal.down, maxLines = 1, softWrap = false)
            Text(S.opLegendeCalls + " " + Fmt.pct((1f - puts) * 100.0, 0), style = MaterialTheme.typography.labelSmall, color = signal.up, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun Untertitel(text: String, rechts: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        rechts?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false) }
    }
}

// Offene Kontrakte je Basispreis als gespiegelte Balken, Puts links, Calls rechts; Put- und Call-Wand, Max Pain und
// die Naeherung des Gamma-Exposures mit kumulierter Netto-Linie und Nullpunkt.
@Composable
internal fun OptionsEbenenBlock(
    kurs: Double, kontrakte: List<Kontrakt>, maxPain: Double?, waehrung: String,
    nasdaq: Map<NasdaqGriechen.Schluessel, Double> = emptyMap(),
) {
    val jetztS = remember(kontrakte) { System.currentTimeMillis() / 1000 }
    val e = remember(kurs, kontrakte, nasdaq) { OptionsEbenen.berechnen(kurs, kontrakte, jetztS, maxPain, nasdaq) } ?: return
    val zeilen = remember(e) { OptionsEbenen.fuerAnzeige(e, kurs) }
    if (zeilen.isEmpty()) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun preis(v: Double) = Fmt.price(v, waehrung)
    val calls = LocalSignal.current.up
    val puts = LocalSignal.current.down
    Untertitel(S.opEbenen)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(S.opLegendePuts, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = puts)
        Text(S.opLegendeCalls, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = calls)
    }
    val groesst = zeilen.maxOf { maxOf(it.calls, it.puts) }.coerceAtLeast(1L).toFloat()
    val naechster = zeilen.minByOrNull { abs(it.strike - kurs) }
    Column(Modifier.padding(vertical = 4.dp)) {
        zeilen.forEach { z ->
            val markiert = z === naechster
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.fillMaxWidth((z.puts / groesst).coerceIn(0f, 1f)).height(9.dp).background(puts))
                }
                Text(
                    preis(z.strike), modifier = Modifier.width(88.dp), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (markiert) FontWeight.Bold else FontWeight.Normal,
                    color = if (markiert) MaterialTheme.colorScheme.onSurface else grau,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth((z.calls / groesst).coerceIn(0f, 1f)).height(9.dp).background(calls))
                }
            }
        }
    }
    e.putWand?.let { WertZeile(S.opPutWand, preis(it), "wand", puts) }
    e.callWand?.let { WertZeile(S.opCallWand, preis(it), "wand", calls) }

    if (e.strikes.size >= 3 && e.strikes.any { it.gexNetto != 0.0 }) {
        val echt = e.strikesEcht
        val naeher = e.strikesNaeherung
        Untertitel(S.opNettoGex, when { echt == 0 -> S.opNaeherung; naeher == 0 -> S.opNasdaqGamma; else -> S.opGexMischung(echt, naeher) })
        val kursIndex = e.strikes.indices.minByOrNull { abs(e.strikes[it].strike - kurs) }
        LinesChart(
            listOf(e.kumuliert to MaterialTheme.colorScheme.primary),
            Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp, vertical = 6.dp),
            selected = kursIndex, scrubben = false, baseline = 0.0,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(preis(e.strikes.first().strike), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
            Text(S.opKurs + " " + preis(kurs), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
            Text(preis(e.strikes.last().strike), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        }
        WertZeile(S.opNettoGex, Fmt.dollarKompakt(e.nettoGex), "gex")
        e.nullpunkt?.let { WertZeile(S.opNullpunkt, preis(it), "nullpunkt") }
    }
}

// IV30 gegen realisierte Volatilitaet, Put-Aufschlag, Laufzeitstruktur sowie Rang und Perzentil aus den lokal
// gespeicherten Tageswerten.
@Composable
internal fun OptionsVolaBlock(state: AppState, symbol: String, kurs: Double, kontrakte: List<Kontrakt>) {
    val jetztS = remember(kontrakte) { System.currentTimeMillis() / 1000 }
    var schluesse by remember(symbol) { mutableStateOf<List<Double>>(emptyList()) }
    LaunchedEffect(symbol) {
        schluesse = runCatching { state.candles(symbol, Range.Y1).map { it.close } }.getOrDefault(emptyList())
    }
    val v = remember(kurs, kontrakte, schluesse) { OptionsVola.berechnen(kurs, kontrakte, schluesse, jetztS) } ?: return
    var lage by remember(symbol) { mutableStateOf<IvHistorie.Lage?>(null) }
    val iv30 = v.iv30
    LaunchedEffect(symbol, iv30) {
        if (iv30 == null) return@LaunchedEffect
        val alt = state.market.ivVerlauf(symbol)
        val neu = IvHistorie.merken(alt, (jetztS / 86_400L).toInt(), iv30)
        if (neu != alt) state.market.ivMerken(symbol, neu)
        lage = IvHistorie.lage(IvHistorie.lesen(neu), iv30)
    }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.volatilitaet(symbol), Modifier.fillMaxWidth()) { Untertitel(S.opVola) }
    val groesst = maxOf(v.iv30 ?: 0.0, v.rv30 ?: 0.0).coerceAtLeast(0.01).toFloat()
    @Composable
    fun MitBalken(titel: String, wert: Double, farbe: androidx.compose.ui.graphics.Color) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Fmt.pct(wert * 100, 1), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            Box(Modifier.padding(top = 4.dp).fillMaxWidth((wert.toFloat() / groesst).coerceIn(0.02f, 1f)).height(6.dp).background(farbe))
        }
    }
    v.iv30?.let { MitBalken(S.opIv30, it, Kategorie.blau) }
    v.rv30?.let { MitBalken(S.opRv30, it, Kategorie.ocker) }
    v.verhaeltnis?.let { WertZeile(S.opIvZuRv, Fmt.num(it, 2), "ivrv") }
    v.putAufschlag?.let { WertZeile(S.opPutAufschlag, Fmt.signedPct(it * 100, 1), "skew") }
    lage?.let { l ->
        if (l.rang != null && l.perzentil != null) {
            WertZeile(S.opIvRang, Fmt.pct(l.rang * 100, 0), "ivrang", anteil = l.rang.toFloat())
            WertZeile(S.opIvPerzentil, Fmt.pct(l.perzentil * 100, 0), "ivperz", anteil = l.perzentil.toFloat())
        } else {
            Text(S.opIvSeit(Fmt.dayMonthNum(l.seit * 86_400_000L)), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
    }
    if (v.struktur.size >= 2) {
        Untertitel(S.opLaufzeit)
        LinesChart(
            listOf(v.struktur.map { it.iv } to MaterialTheme.colorScheme.primary),
            Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 20.dp, vertical = 6.dp),
            // Skala ab null: kleine Unterschiede zwischen den Laufzeiten sehen nicht wie Spruenge aus
            scrubben = false, baseline = 0.0,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val a = v.struktur.first()
            val b = v.struktur.last()
            Text(Fmt.pct(a.iv * 100, 0) + " (" + S.opTage(a.tage) + ")", style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
            Text(Fmt.pct(b.iv * 100, 0) + " (" + S.opTage(b.tage) + ")", style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}
