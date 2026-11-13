package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import de.troetenrepublik.app.engine.IvHistorie
import de.troetenrepublik.app.engine.OptionsVola
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Optionszeile
import de.troetenrepublik.app.engine.StillhalterListe
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.widgetStillhalterMerken
import kotlinx.coroutines.delay

// Zwischenspeicher der Optionsketten fuer die Liste: gleiche Ketten werden 15 Minuten wiederverwendet.
internal object StillhalterKetten {
    val karte = HashMap<String, Pair<Long, Pair<Double, List<Optionsanalyse.Kontrakt>>>>()
    const val FRIST_MS = 15 * 60_000L
}

// Bester Covered Call und Cash-Secured Put je Wert aus Depot und Watchlist (0.76), nach Abstand und Laufzeit waehlbar.
// Die Ketten werden nacheinander mit Pause abgerufen.
@Composable
internal fun StillhalterTool(state: AppState, onOpen: (String) -> Unit) {
    val konto by state.account.collectAsState()
    val quotes by state.quotes.collectAsState()
    val symbole = remember(konto.positions, konto.watchlist) {
        (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).distinct()
            .filter { Symbols.typeOf(it).let { t -> t == AssetType.STOCK || t == AssetType.ETF } }.take(40)
    }
    var abstand by rememberSaveable { mutableStateOf(1) }
    var laufzeit by rememberSaveable { mutableStateOf(0) }
    var ketten by remember { mutableStateOf<Map<String, Pair<Double, List<Optionsanalyse.Kontrakt>>>>(emptyMap()) }
    var zahlen by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var fertig by remember { mutableStateOf(0) }
    LaunchedEffect(symbole, laufzeit) {
        fertig = 0
        val jetztS = System.currentTimeMillis() / 1000
        zahlen = runCatching { state.kennzahlen(symbole, listOf("earningsTimestamp", "earningsTimestampStart")) }.getOrDefault(emptyMap())
            .mapNotNull { (s, k) -> listOfNotNull(k["earningsTimestamp"], k["earningsTimestampStart"]).map { it.toLong() }.filter { it > jetztS }.minOrNull()?.let { s to it } }.toMap()
        for (s in symbole) {
            val jetzt = System.currentTimeMillis()
            val cache = StillhalterKetten.karte[s]?.takeIf { jetzt - it.first < StillhalterKetten.FRIST_MS }?.second
            val kette = cache ?: run {
                delay(400)
                runCatching { state.market.optionskette(s) }.getOrNull()?.also { StillhalterKetten.karte[s] = System.currentTimeMillis() to it }
            }
            if (kette != null) {
                // Reicht die Kette nicht bis zur gewaehlten Laufzeit, den passenden Termin nachladen und mit merken
                val termine = runCatching { state.market.optionsTermine(s) }.getOrNull().orEmpty()
                val fehlt = StillhalterListe.fehlenderTermin(termine, kette.second.mapTo(HashSet()) { it.verfall }, StillhalterListe.LAUFZEITEN[laufzeit], jetztS)
                val dazu = fehlt?.let { t -> runCatching { state.market.optionsTermin(s, t) }.getOrNull() }.orEmpty()
                val voll = if (dazu.isEmpty()) kette else (kette.first to (kette.second + dazu)).also { v ->
                    StillhalterKetten.karte[s] = (StillhalterKetten.karte[s]?.first ?: System.currentTimeMillis()) to v
                }
                ketten = ketten + (s to voll)
                val iv30 = OptionsVola.iv30(OptionsVola.struktur(kette.first, kette.second, jetztS).map { it.second })
                if (iv30 != null) {
                    val alt = state.market.ivVerlauf(s)
                    val neu = IvHistorie.merken(alt, (jetztS / 86_400L).toInt(), iv30)
                    if (neu != alt) state.market.ivMerken(s, neu)
                }
            }
            fertig++
        }
    }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val tage = StillhalterListe.LAUFZEITEN
    val abstaende = StillhalterListe.ABSTAENDE
    StichpunktText(S.ozErklaerung, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
    Text(S.ozAbstandKurs, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
    SegmentRow(abstaende.map { Fmt.pct(it.toDouble(), 0) }, abstand) { abstand = it }
    Text(S.slLaufzeit, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
    SegmentRow(tage.map { S.opTage(it) }, laufzeit) { laufzeit = it }

    val zeilen = remember(symbole, ketten, zahlen, abstand, laufzeit) {
        val jetztS = System.currentTimeMillis() / 1000
        symbole.mapNotNull { s ->
            val k = ketten[s] ?: return@mapNotNull null
            val e = StillhalterListe.waehlen(k.first, k.second, abstaende[abstand], tage[laufzeit], jetztS, zahlen[s], state.riskFree)
            if (e.call == null && e.put == null) null else Triple(s, k.first, e)
        }
    }
    if (fertig < symbole.size) {
        Text(S.slLaedt + " " + S.slFortschritt(fertig, symbole.size), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    } else if (zeilen.isEmpty()) {
        Text(S.slLeer, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
    }
    fun preis(v: Double) = Fmt.price(v, "USD")
    // Name aus Katalog, Position oder Kurs; das Kuerzel nur, wenn keiner bekannt ist
    fun name(s: String) = Optionszeile.name(s, Symbols.find(s)?.name, konto.positions.firstOrNull { it.symbol == s && !it.isDerivative }?.name, quotes[s]?.name)
    // Das Widget zeigt den zuletzt hier gerechneten Stand: die besten Werte nach Rendite, sobald alle Ketten da sind
    LaunchedEffect(fertig, abstand, laufzeit) {
        if (symbole.isEmpty() || fertig < symbole.size) return@LaunchedEffect
        val werte = zeilen.flatMap { (s, _, e) ->
            listOfNotNull(e.call, e.put).map { v ->
                de.troetenrepublik.app.data.WidgetKartenBau.Stillhalt(
                    kurzName(s, name(s)), s, v.call, v.strike, v.praemie, v.verfall * 1000, v.renditeJahr,
                )
            }
        }
        runCatching { state.widgetStillhalterMerken(werte) }
    }
    zeilen.forEach { (s, kurs, e) ->
        Column(Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(top = 18.dp)) {
            OptionsKopf(name(s), preis(kurs))
            listOfNotNull(e.call, e.put).forEach { v ->
                KontraktTitel(v.call, preis(v.strike), v.verfall * 1000, Modifier.padding(top = 10.dp))
                if (v.zahlenVorVerfall == true) {
                    Text(S.slZahlen(Fmt.dayMonthNumJahr((zahlen[s] ?: 0L) * 1000)), style = MaterialTheme.typography.labelSmall,
                        color = LocalSignal.current.warn, modifier = Modifier.padding(horizontal = 20.dp))
                }
                WertePaare(
                    listOf(
                        WertPaar(S.slPraemie, Fmt.priceStellen(v.praemie, "USD", Optionszeile.praemieStellen(v.praemie))),
                        WertPaar(S.ozErtragJahr, Fmt.pct(v.renditeJahr * 100, 1)),
                        WertPaar(S.slAusserhalb, v.ausserhalb?.let { Fmt.pct(it * 100, 0) } ?: "–"),
                        WertPaar(S.slSpread, v.spread?.let { Fmt.pct(it * 100, 1) } ?: "–", warnung = Optionszeile.spanneHoch(v.spread)),
                    ),
                    Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    ToolNote(S.opStand)
}
