package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.PortfolioTools
import de.troetenrepublik.app.engine.Stueckliste
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.StuecklisteTexte
import kotlin.math.abs

// Stueckliste: Auftraege in Euro als kaufbare Stueckzahlen (engine/Stueckliste.kt), eingeklappt unter den Auftraegen
// des Umschichtens und unter dem Vorschlagsdepot.

private fun stText(key: String, vararg w: String): String = StuecklisteTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Unter dem Umschichten: trades in der Reihenfolge von items, Auftraege unter klein zaehlen nicht.
@Composable
internal fun StuecklisteUmschichten(items: List<ValuedPosition>, trades: List<PortfolioTools.Trade>, klein: Double, onOpen: (String) -> Unit) {
    if (trades.none { abs(it.amount) >= klein }) return
    AufklappAbschnitt("umschichten-stuecke", stText("titel"), stText("info")) {
        var bruch by rememberSaveable { mutableStateOf(false) }
        SchalterZeile(stText("bruch"), bruch) { bruch = it }
        val posten = items.mapIndexedNotNull { i, vp ->
            val t = trades.getOrNull(i) ?: return@mapIndexedNotNull null
            if (abs(t.amount) < klein) null else Stueckliste.Posten(vp.key, vp.price, t.amount, vp.qty)
        }
        val e = remember(posten, bruch) { Stueckliste.rechnen(posten, bruch) }
        StuecklisteZeilen(e, items.associate { it.key to (it.position.name to it.position.symbol) }, bruch, onOpen)
    }
}

// Unter einem Depot aus Gewichten: Betrag waehlen, Kurse aus den geladenen Kursen der App (Euro).
// werte: Kuerzel, Name, Gewicht.
@Composable
internal fun StuecklisteGewichte(state: AppState, werte: List<Triple<String, String, Double>>, onOpen: (String) -> Unit) {
    if (werte.isEmpty()) return
    AufklappAbschnitt("gewichte-stuecke", stText("titel"), stText("info")) {
        var betrag by rememberSaveable { mutableDoubleStateOf(10_000.0) }
        var bruch by rememberSaveable { mutableStateOf(false) }
        ToolChoice(stText("betrag"), listOf(1_000.0, 5_000.0, 10_000.0, 25_000.0, 50_000.0, 100_000.0).map { Fmt.euro(it, 0) to it }, betrag) { betrag = it }
        SchalterZeile(stText("bruch"), bruch) { bruch = it }
        // Kurse in Euro, erst beim Aufklappen geholt
        val kurse by produceState<Map<String, Double>?>(null, werte.map { it.first }) {
            value = runCatching { state.market.snapshot(werte.map { it.first }) }.getOrDefault(emptyMap()).mapValues { it.value.price }
        }
        val geladen = kurse
        if (geladen == null) { LeerZustand(de.troetenrepublik.app.i18n.S.toolLoading); return@AufklappAbschnitt }
        val mitKurs = werte.mapNotNull { (s, _, g) -> geladen[s]?.takeIf { it.isFinite() && it > 0 }?.let { Triple(s, it, g) } }
        if (mitKurs.isEmpty()) { LeerZustand(stText("ohneKurs")); return@AufklappAbschnitt }
        val e = remember(mitKurs, betrag, bruch) { Stueckliste.ausGewichten(mitKurs, betrag, bruch) }
        StuecklisteZeilen(e, werte.associate { it.first to (it.second to it.first) }, bruch, onOpen)
        if (mitKurs.size < werte.size) LeerZustand(stText("fehlend", (werte.size - mitKurs.size).toString()))
    }
}

@Composable
private fun StuecklisteZeilen(e: Stueckliste.Ergebnis, namen: Map<String, Pair<String, String>>, bruch: Boolean, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    if (e.zeilen.isEmpty()) { LeerZustand(stText("leer")); return }
    fun anzahl(n: Double) = if (bruch) Fmt.qty(abs(n)) else Fmt.num(abs(n), 0)
    e.zeilen.sortedByDescending { abs(it.betrag) }.forEach { z ->
        val (name, symbol) = namen[z.key] ?: (z.key to z.key)
        KeyValueRow(
            name, stText(if (z.stuecke > 0) "kauf" else "verkauf", anzahl(z.stuecke)),
            valueColor = if (z.stuecke > 0) signal.up else signal.down, onClick = { onOpen(symbol) },
            hint = stText("je", Fmt.euro(z.preis), Fmt.euro(abs(z.betrag), 0)),
        )
    }
    val kauf = e.zeilen.filter { it.stuecke > 0 }.sumOf { it.betrag }
    val verkauf = -e.zeilen.filter { it.stuecke < 0 }.sumOf { it.betrag }
    if (kauf > 0) KeyValueRow(stText("summeKauf"), Fmt.euro(kauf))
    if (verkauf > 0) KeyValueRow(stText("summeVerkauf"), Fmt.euro(verkauf))
    KeyValueRow(stText("rest"), Fmt.euro(e.rest), hint = stText("restHinweis"))
}
