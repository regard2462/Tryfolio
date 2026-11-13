package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.AuffaelligeKontrakte
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Optionszeile
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.delay

// Auffaellige Kontrakte je Wert aus Depot und Watchlist (0.78): Tagesvolumen im Verhaeltnis zu den offenen Kontrakten,
// mit Mindestvolumen und Art waehlbar. Die Ketten kommen aus demselben Zwischenspeicher wie bei den Stillhaltern und
// werden nacheinander mit Pause abgerufen.
@Composable
internal fun AuffaelligeTool(state: AppState, onOpen: (String) -> Unit) {
    val konto by state.account.collectAsState()
    val quotes by state.quotes.collectAsState()
    val symbole = remember(konto.positions, konto.watchlist) {
        (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).distinct()
            .filter { Symbols.typeOf(it).let { t -> t == AssetType.STOCK || t == AssetType.ETF } }.take(40)
    }
    var minimum by rememberSaveable { mutableStateOf(1) }
    var art by rememberSaveable { mutableStateOf(0) }
    var ketten by remember { mutableStateOf<Map<String, Pair<Double, List<Optionsanalyse.Kontrakt>>>>(emptyMap()) }
    var fertig by remember { mutableStateOf(0) }
    LaunchedEffect(symbole) {
        fertig = 0
        for (s in symbole) {
            val jetzt = System.currentTimeMillis()
            val cache = StillhalterKetten.karte[s]?.takeIf { jetzt - it.first < StillhalterKetten.FRIST_MS }?.second
            val kette = cache ?: run {
                delay(400)
                runCatching { state.market.optionskette(s) }.getOrNull()?.also { StillhalterKetten.karte[s] = System.currentTimeMillis() to it }
            }
            if (kette != null) ketten = ketten + (s to kette)
            fertig++
        }
    }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val mengen = AuffaelligeKontrakte.MIN_VOLUMEN
    val arten = AuffaelligeKontrakte.Art.entries
    Text(S.akMinVolumen, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
    SegmentRow(mengen.map { Fmt.num(it.toDouble(), 0) }, minimum) { minimum = it }
    SegmentRow(listOf(S.all, S.opLegendeCalls, S.opLegendePuts), art) { art = it }

    val jetztS = System.currentTimeMillis() / 1000
    val werte = symbole.mapNotNull { s ->
        val k = ketten[s] ?: return@mapNotNull null
        val w = AuffaelligeKontrakte.auswerten(k.first, k.second, mengen[minimum], arten[art], jetztS) ?: return@mapNotNull null
        Triple(s, k.first, w)
    }.sortedByDescending { it.third.zeilen.first().verhaeltnis }
    if (fertig < symbole.size) {
        Text(S.slLaedt + " " + S.slFortschritt(fertig, symbole.size), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    } else if (werte.isEmpty()) {
        Text(S.akLeer, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
    }
    val groesstes = werte.maxOfOrNull { it.third.zeilen.maxOf { z -> z.verhaeltnis } }?.coerceAtLeast(1.0) ?: 1.0
    val calls = LocalSignal.current.up
    val puts = LocalSignal.current.down
    fun preis(v: Double) = Fmt.price(v, "USD")
    werte.forEach { (s, kurs, w) ->
        Column(Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(top = 18.dp)) {
            OptionsKopf(
                Optionszeile.name(s, Symbols.find(s)?.name, konto.positions.firstOrNull { it.symbol == s && !it.isDerivative }?.name, quotes[s]?.name),
                preis(kurs),
            )
            // Tagesvolumen der ganzen Kette: Calls gegen Puts
            val summe = (w.callUmsatz + w.putUmsatz).coerceAtLeast(1L).toFloat()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp).height(6.dp)) {
                if (w.callUmsatz > 0) Box(Modifier.weight(w.callUmsatz / summe).height(6.dp).background(calls))
                if (w.putUmsatz > 0) Box(Modifier.weight(w.putUmsatz / summe).height(6.dp).background(puts))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp)) {
                Text(S.opLegendeCalls + " " + Fmt.num(w.callUmsatz.toDouble(), 0) + " (" + Fmt.pct(w.callUmsatz / summe * 100.0, 0) + ")", style = MaterialTheme.typography.labelSmall, color = calls, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                Text(S.opLegendePuts + " " + Fmt.num(w.putUmsatz.toDouble(), 0) + " (" + Fmt.pct(w.putUmsatz / summe * 100.0, 0) + ")", style = MaterialTheme.typography.labelSmall, color = puts, maxLines = 1, softWrap = false)
            }
            w.zeilen.forEach { z ->
                val k = z.kontrakt
                KontraktTitel(k.call, preis(k.strike), k.verfall * 1000, Modifier.padding(top = 10.dp))
                WertePaare(
                    listOf(
                        WertPaar(S.akVolumen, Fmt.num(k.umsatz.toDouble(), 0)),
                        WertPaar(S.akOffen, Fmt.num(k.offen.toDouble(), 0)),
                        WertPaar(S.akVerhaeltnis, if (z.neu) S.copyNew else Fmt.num(z.verhaeltnis, 1) + "×"),
                        WertPaar(S.ozAbstandKurs, Fmt.signedPct(z.abstand * 100, 1)),
                        WertPaar(S.ozGehandelt, Fmt.dollarKompakt(z.praemie)),
                    ),
                    Modifier.padding(top = 4.dp),
                )
                if (!z.neu) Box(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp).fillMaxWidth((z.verhaeltnis / groesstes).toFloat().coerceIn(0.02f, 1f)).height(4.dp).background(if (k.call) calls else puts))
            }
        }
    }
    ToolNote(S.opStand)
}
