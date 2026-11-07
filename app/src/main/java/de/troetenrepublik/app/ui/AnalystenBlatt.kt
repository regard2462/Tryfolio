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
            ChevronRight(height = 13.dp)
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
            MetricValue(potential, "${Fmt.pct(abs(potential))} ${S.toTarget}")
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
        // Im Preis? (5.92): Lauf seit der juengsten Hoch- oder Abstufung
        AnalystenImPreis(state, d, profile?.country, oeffnen)
        val b = d.bilanz
        if (b == null) {
            if (d.verlauf.urteile.isEmpty()) ToolNote(S.anNurUs)
            QuellenZeile(S.sourceYahoo)
            return@DetailBlatt
        }
        // Bereinigte Einschaetzung: Haeuser nach Trefferquote, Alter und Herde gewichtet
        SectionHeader(S.anBereinigt)
        b.roh?.let { KeyValueRow(S.anAlleHaeuser(b.aktive), S.anStufe(Analystenbilanz.stufeAus(it)) + " · " + Fmt.num(it, 1)) }
        b.bereinigt?.let { KeyValueRow(S.anGewichtet, S.anStufe(Analystenbilanz.stufeAus(it)) + " · " + Fmt.num(it, 1)) }
        if (b.zielRoh != null && b.zielBereinigt != null) {
            KeyValueRow(S.anZiel, seite.preis(b.zielRoh, zielWaehrung) + " · " + S.anZielGewichtet(seite.preis(b.zielBereinigt, zielWaehrung)))
        }
        b.gesamt.getOrNull(b.horizont)?.let { (t, n) ->
            KeyValueRow(S.anTrefferAlle, b.gesamt.joinToString(" · ") { (tt, nn) -> if (nn > 0) Fmt.pct(tt * 100.0 / nn, 0) else "–" })
            Text(
                S.anTrefferSatz(Fmt.pct(t * 100.0 / n, 0), n, Analystenbilanz.HORIZONTE[b.horizont] / 21, d.indexName, b.wenigAussagekraft),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        AufklappText(S.anHinweis(Fmt.pct(b.herdenAnteil * 100, 0)), Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 4.dp))

        // Haeuser: aktuelle Haltung, Kursziel und Trefferbilanz
        SectionHeader(S.anHaeuser)
        var alle by remember(symbol) { mutableStateOf(false) }
        val liste = if (alle) b.haeuser else b.haeuser.take(12)
        val bestaende = halter?.halter.orEmpty()
        liste.forEach { h ->
            val e = AnalystenHalter.passend(h.name, bestaende)
            HausZeile(h, seite, zielWaehrung, e?.let { it to seitStichtag[it.stichtag] })
        }
        if (b.haeuser.size > 12) {
            Text(
                if (alle) S.less else S.bbAlle(b.haeuser.size), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
                modifier = Modifier.clickable { alle = !alle }.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (bestaende.isNotEmpty()) {
            val treffer = b.haeuser.count { AnalystenHalter.passend(it.name, bestaende) != null }
            ToolNote(if (treffer > 0) S.anHalterHinweis else S.anHalterKeiner(bestaende.size))
        }
        QuellenZeile(S.sourceYahoo)
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
private fun HausZeile(h: Analystenbilanz.Haus, seite: SeitenWaehrung, zielWaehrung: String, halt: Pair<Halter.Eintrag, Double?>? = null) {
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
        Text(
            if (h.bewertet < Analystenbilanz.MIN_URTEILE) S.anZuWenige(h.bewertet)
            else S.anTreffer(h.treffer.map { (t, n) -> if (n > 0) "$t/$n" else "–" }, Fmt.pct(h.geschrumpft * 100, 0)),
            style = MaterialTheme.typography.bodySmall, color = grau,
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
