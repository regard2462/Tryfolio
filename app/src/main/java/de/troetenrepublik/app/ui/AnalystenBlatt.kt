package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AnalystenDaten
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.analystenDaten
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.AnalystenHalter
import de.troetenrepublik.app.engine.Analystenbilanz
import de.troetenrepublik.app.engine.Haeuserliste
import de.troetenrepublik.app.engine.KurszielLage
import de.troetenrepublik.app.market.Halter
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.abs

// Analysten auf der Wertpapierseite (5.92): oben unter „Chancen und Risiken“ die Empfehlung mit Kursziel, ein Tipp
// oeffnet das Blatt mit Verteilung, einzelnen Haeusern, ihrer Trefferbilanz und der bereinigten Einschaetzung.

// Karte mit Empfehlung, Zahl der Einschaetzungen und Kursziel; ein Tipp auf das Kursziel wechselt die Waehrung.
@Composable
internal fun AnalystenKarte(p: MarketApi.Profile, quote: Quote?, ziel: Geldanzeige, onClick: () -> Unit) {
    val zielWaehrung = quote?.nativeCurrency ?: ""
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.analysts, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            ChevronRight()
        }
        Spacer(Modifier.height(10.dp))
        // Ein Tipp auf die Empfehlung oeffnet die Einzelheiten, einer auf das Kursziel wechselt die Waehrung
        Text(
            S.recommendation(p.recommendation) + if (p.analystCount > 0) S.fromEstimates(p.analystCount) else "",
            style = MaterialTheme.typography.titleMedium,
            lineHeight = 26.sp,
        )
        if (p.targetPrice > 0) {
            Text(
                S.anKursziel(ziel.preis(p.targetPrice, zielWaehrung)),
                style = MaterialTheme.typography.titleMedium,
                lineHeight = 26.sp,
                modifier = if (zielWaehrung.isNotBlank()) Modifier.clickable(interactionSource = null, indication = null, onClickLabel = S.switchCurrency) { ziel.weiter(zielWaehrung) } else Modifier,
            )
        }
        val nativePrice = quote?.nativePrice ?: 0.0
        if (p.targetPrice > 0 && nativePrice > 0) {
            Spacer(Modifier.height(12.dp))
            val potential = (p.targetPrice / nativePrice - 1.0) * 100.0
            // Liegt der Kurs ueber dem Ziel, steht der Abstand des Kurses zum Ziel da, sonst die Luft bis zum Ziel
            val ueber = (nativePrice / p.targetPrice - 1.0) * 100.0
            val richtung = if (abs(potential) < 0.05) 0 else if (potential > 0) 1 else -1
            MetricValue(
                if (richtung == 0) 0.0 else potential,
                S.toTargetLage(Fmt.pct(if (richtung > 0) potential else ueber, 1), richtung),
            )
        }
    }
}

// Farben der fuenf Stufen von stark kaufen (+2) bis stark verkaufen (-2).
@Composable
private fun stufenFarbe(s: Int): Color {
    val signal = LocalSignal.current
    return when (s) {
        2 -> signal.up
        1 -> signal.up.copy(alpha = 0.55f)
        0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        -1 -> signal.down.copy(alpha = 0.55f)
        else -> signal.down
    }
}

// Blatt mit allen Einzelheiten zu den Analysten.
@Composable
internal fun AnalystenBlatt(
    state: AppState, symbol: String, name: String, profile: MarketApi.Profile?, seite: SeitenWaehrung,
    // Routen fuer den Uebungsvorschlag unter „Im Preis?“ (5.92), ohne keiner
    oeffnen: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var daten by remember(symbol) { mutableStateOf<AnalystenDaten?>(null) }
    var geladen by remember(symbol) { mutableStateOf(false) }
    LaunchedEffect(symbol) { daten = runCatching { state.analystenDaten(symbol, profile?.country) }.getOrNull(); geladen = true }
    // Groesste Halter und Kurs am Meldestichtag (5.94): halten die Haeuser die Aktie selbst?
    var halter by remember(symbol) { mutableStateOf<Halter.Daten?>(null) }
    var seitStichtag by remember(symbol) { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    LaunchedEffect(symbol) {
        val h = state.halterFor(symbol) ?: return@LaunchedEffect
        halter = h
        val kurse = state.dailyCloses(symbol)
        val jetzt = kurse.lastOrNull()?.close ?: return@LaunchedEffect
        seitStichtag = h.halter.map { it.stichtag }.distinct().mapNotNull { t ->
            de.troetenrepublik.app.engine.PortfolioTools.closeAt(kurse, t)?.takeIf { it > 0 }?.let { t to (jetzt / it - 1) * 100 }
        }.toMap()
    }
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val rangliste = haeuserliste().second
    DetailBlatt(S.analysts, name, onDismiss) {
        val d = daten
        if (d == null) {
            ToolNote(if (geladen) S.anKeineDaten else S.anLaedt)
            return@DetailBlatt
        }
        val zielWaehrung = quotes[d.quelle]?.nativeCurrency ?: quotes[symbol]?.nativeCurrency.orEmpty()
        d.verlauf.verteilung.firstOrNull { it.periode == "0m" }?.takeIf { it.summe > 0 }?.let { v ->
            SectionHeader(S.anVerteilung)
            Verteilungsbalken(v)
            val vorher = d.verlauf.verteilung.firstOrNull { it.periode == "-3m" }?.mittel
            val jetzt = v.mittel
            if (vorher != null && jetzt != null) {
                Text(
                    S.anVergleich(jetzt - vorher), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
        // Spanne der Kursziele (q78h): tiefstes, Median und hoechstes Ziel gegen den Kurs, dazu Hoch- und Abstufungen
        val kursNativ = quotes[symbol]?.nativePrice ?: 0.0
        val zWaehrung = quotes[symbol]?.nativeCurrency.orEmpty()
        val spanne = profile?.let { KurszielLage.lage(kursNativ, it.targetLow, it.targetHigh, it.targetPrice.takeIf { m -> m > 0 }) }
        // Zuerst die Zahl der Signale-Zeile (30 Tage), darunter die laengere Sicht (90 Tage), beide mit derselben Zaehlung
        val jetzt = System.currentTimeMillis()
        val kurz = de.troetenrepublik.app.engine.Signale.stufungen(d.verlauf.urteile, jetzt)
        val lang = de.troetenrepublik.app.engine.Signale.stufungen(d.verlauf.urteile, jetzt, 90)?.takeIf { it != kurz }
        if (spanne != null || kurz != null || lang != null) SectionHeader(S.vbKursziele)
        if (spanne != null) {
            KurszielSpanne(
                spanne, seite.preis(spanne.tief, zWaehrung), spanne.median?.let { seite.preis(it, zWaehrung) },
                seite.preis(spanne.hoch, zWaehrung), seite.preis(spanne.kurs, zWaehrung),
            )
        }
        listOfNotNull(kurz?.let { de.troetenrepublik.app.engine.Signale.STUFUNG_TAGE to it }, lang?.let { 90 to it }).forEach { (tage, st) ->
            Text(
                S.vbStufen(tage, st.hoch, st.ab), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        // Im Preis? (5.92): Lauf seit der juengsten Hoch- oder Abstufung
        AnalystenImPreis(state, d, profile?.country, oeffnen)
        val b = d.bilanz
        if (b == null) {
            if (d.verlauf.urteile.isEmpty()) ToolNote(S.anNurUs)
            return@DetailBlatt
        }
        // Bereinigte Einschaetzung: Haeuser nach Trefferquote, Alter und Herde gewichtet
        SectionHeader(S.anBereinigt)
        // Alle Haeuser gegen gewichtet in zwei Spalten, die Werte stehen je Zeile in festen Spalten
        val roh = b.roh
        val ber = b.bereinigt
        if (roh != null || ber != null) {
            val zeilen = buildList {
                add(Triple(S.anStufeZeile, listOf(roh, ber).map { w -> if (w == null) "–" else S.anStufe(Analystenbilanz.stufeAus(w)) }, emptyList<Color?>()))
                add(Triple(S.anMittelwert, listOf(roh, ber).map { w -> if (w == null) "–" else Fmt.num(w, 1) }, emptyList<Color?>()))
                if (b.zielRoh != null && b.zielBereinigt != null) {
                    add(Triple(S.anZiel, listOf(seite.preis(b.zielRoh, zielWaehrung), seite.preis(b.zielBereinigt, zielWaehrung)), emptyList<Color?>()))
                }
            }
            SpaltenTabelle(listOf(S.anAlleHaeuser(b.aktive), S.anSpalteGewichtet), zeilen)
        }
        b.gesamt.getOrNull(b.horizont)?.let { (t, n) ->
            val signal = LocalSignal.current
            val quoten = b.gesamt.map { (tt, nn) -> if (nn > 0) tt * 100.0 / nn else null }
            SpaltenTabelle(
                listOf(S.rangeLabel(de.troetenrepublik.app.data.Range.M3), S.rangeLabel(de.troetenrepublik.app.data.Range.M6), S.rangeLabel(de.troetenrepublik.app.data.Range.Y1)),
                listOf(Triple(S.anTrefferZeile, quoten.map { q -> if (q == null) "–" else Fmt.pct(q, 0) }, quoten.map { q -> if (q == null) null else if (q >= 55) signal.up else if (q <= 45) signal.down else null })),
                spalte = 64.dp,
            )
            Text(
                S.anTrefferSatz(Fmt.pct(t * 100.0 / n, 0), n, Analystenbilanz.HORIZONTE[b.horizont] / 21, d.indexName, b.wenigAussagekraft),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        HinweisInfo(S.anHinweis(Fmt.pct(b.herdenAnteil * 100, 0)))

        // Haeuser: aktuelle Haltung, Kursziel und Trefferbilanz
        SectionHeader(S.anHaeuser)
        var alle by remember(symbol) { mutableStateOf(false) }
        val liste = if (alle) b.haeuser else b.haeuser.take(12)
        val bestaende = halter?.halter.orEmpty()
        liste.forEach { h ->
            val e = AnalystenHalter.passend(h.name, bestaende)
            HausZeile(h, seite, zielWaehrung, e?.let { it to seitStichtag[it.stichtag] }, rangliste?.finden(h.name))
        }
        if (b.haeuser.size > 12) {
            Text(
                if (alle) S.less else S.bbAlle(b.haeuser.size), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
                modifier = Modifier.textKlick(20.dp) { alle = !alle }.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (bestaende.isNotEmpty()) {
            val treffer = b.haeuser.count { AnalystenHalter.passend(it.name, bestaende) != null }
            if (treffer > 0) HinweisInfo(S.anHalterHinweis) else ToolNote(S.anHalterKeiner(bestaende.size))
        }
        rangliste?.let { HaeuserRanglisteAbschnitt(it) }
    }
}

// Kleine Tabelle: Kopf mit je einer Spalte, davor die Bezeichnung der Zeile. Werte stehen einzeilig rechts in festen
// Spalten, die Bezeichnung bricht um. Die optionalen Farben gehoeren je Zeile zu den Werten.
@Composable
private fun SpaltenTabelle(kopf: List<String>, zeilen: List<Triple<String, List<String>, List<Color?>>>, spalte: androidx.compose.ui.unit.Dp = 112.dp) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.weight(1f))
            kopf.forEach { k ->
                Text(k, style = MaterialTheme.typography.bodySmall, color = grau, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(spalte))
            }
        }
        zeilen.forEach { (name, werte, farben) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.weight(1f))
                werte.forEachIndexed { i, w ->
                    Text(
                        w, style = MaterialTheme.typography.bodyMedium, color = farben.getOrNull(i) ?: MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(spalte),
                    )
                }
            }
        }
    }
}

// Verteilung als ein Balken aus fuenf Teilen, darunter die Zahlen je Stufe.
@Composable
private fun Verteilungsbalken(v: Analystenbilanz.Verteilung) {
    val teile = listOf(2 to v.starkKaufen, 1 to v.kaufen, 0 to v.halten, -1 to v.verkaufen, -2 to v.starkVerkaufen)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(10.dp).clip(RoundedCornerShape(5.dp))) {
        teile.filter { it.second > 0 }.forEach { (s, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(stufenFarbe(s))) }
    }
    teile.forEach { (s, n) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(stufenFarbe(s)))
            Spacer(Modifier.width(8.dp))
            Text(S.anStufe(s), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(n.toString(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        }
    }
}

// Ein Haus: Name, aktuelle Einstufung, Datum mit Hoch- oder Abstufung und Kursziel, Trefferbilanz.
@Composable
private fun HausZeile(h: Analystenbilanz.Haus, seite: SeitenWaehrung, zielWaehrung: String, halt: Pair<Halter.Eintrag, Double?>? = null, rang: Haeuserliste.Haus? = null) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val u = h.letztes
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val s = h.stufe ?: Analystenbilanz.stufe(u.nach)
            Text(
                s?.let { S.anStufe(it) } ?: u.nach.ifBlank { "–" }, style = MaterialTheme.typography.titleSmall,
                color = if (h.stufe == null) grau else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false,
            )
        }
        // Hoch- oder Abstufung beim letzten Urteil in Worten.
        val zeile = buildList {
            add(Fmt.date(u.zeit))
            when (u.aktion) {
                "up" -> add(S.anHochgestuft)
                "down" -> add(S.anAbgestuft)
            }
            if (u.ziel > 0) add(S.anKursziel(seite.preis(u.ziel, zielWaehrung)))
            if (h.stufe == null) add(S.anVeraltet)
        }.joinToString(" · ")
        PunktText(zeile, color = grau)
        // Trefferanteil des Hauses ueber alle Aktien der Rangliste
        rang?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.hrHausLabel, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                val signal = LocalSignal.current
                Text(
                    Fmt.pct(it.quote * 100, 0), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                    color = if (it.quote >= 0.55) signal.up else if (it.quote <= 0.45) signal.down else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(S.hrUrteile(Fmt.num(it.urteile.toDouble(), 0)), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
            }
        }
        PunktText(
            if (h.bewertet < Analystenbilanz.MIN_URTEILE) S.anZuWenige(h.bewertet)
            else S.anTreffer(h.treffer.map { (t, n) -> if (n > 0) "$t/$n" else "–" }, Fmt.pct(h.geschrumpft * 100, 0)),
            color = grau,
        )
        if (h.widerspruch) Text(S.anWiderspruch, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.warn)
        // Eigener Bestand des Hauses laut letzter Meldung, mit Kursaenderung seit dem Stichtag (5.94)
        halt?.let { (e, seit) ->
            Text(
                S.anHaelt(Fmt.pct(e.anteil, 2), "", Fmt.date(e.stichtag), seit?.let { Fmt.signedPct(it, 0) }),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    seit == null -> grau
                    seit >= 0 -> LocalSignal.current.up
                    else -> LocalSignal.current.down
                },
            )
        }
    }
}
