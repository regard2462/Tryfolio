package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.Optionsanalyse
import de.troetenrepublik.app.engine.Optionszeile
import de.troetenrepublik.app.engine.RollHelfer
import de.troetenrepublik.app.engine.StillhalterDepot
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.rollen
import kotlin.math.abs

// Rollen eines verkauften Puts unter Druck (0.78), auf der Seite der Position: Kurs gegen Basispreis, darunter je
// Vorschlag aus der Optionskette der neue Put mit Nettopraemie und neuer Praemie, im Geld dazu der gedeckte Call fuer
// den Fall der Andienung. Die Kette wird erst geholt, wenn der Kurs nahe am Basispreis oder darunter steht. Gerollt
// wird in einem Zug (Rueckkauf und Neuverkauf, beide oder keiner); Beine einer Strategie werden nur angezeigt.
@Composable
internal fun RollHelferAbschnitt(state: AppState, account: Account, vp: ValuedPosition, onGerollt: () -> Unit) {
    val p = vp.position
    val d = p.derivative ?: return
    if (!d.verkauft || d.kind != DerivativeKind.PUT || d.isKnockedOut || d.ratio != 1.0) return
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val q = quotes[d.underlying] ?: return
    if (q.nativeCurrency != d.ccy) return
    val unterDruck = RollHelfer.druck(q.nativePrice, d.strike) != RollHelfer.Druck.KEINER
    var kette by remember(d.id) { mutableStateOf<Pair<Double, List<Optionsanalyse.Kontrakt>>?>(null) }
    LaunchedEffect(d.id, unterDruck) {
        if (!unterDruck || kette != null) return@LaunchedEffect
        val jetzt = System.currentTimeMillis()
        val basis = StillhalterKetten.karte[d.underlying]?.takeIf { jetzt - it.first < StillhalterKetten.FRIST_MS }?.second
            ?: runCatching { state.market.optionskette(d.underlying) }.getOrNull()?.also { StillhalterKetten.karte[d.underlying] = System.currentTimeMillis() to it }
            ?: return@LaunchedEffect
        // Die Kette reicht bei Werten mit Wochenoptionen nicht bis zur neuen Laufzeit: die fehlenden Termine nachladen
        val termine = runCatching { state.market.optionsTermine(d.underlying) }.getOrNull().orEmpty()
        val fehlend = RollHelfer.fehlendeTermine(termine, basis.second.mapTo(HashSet()) { it.verfall }, d.expiry / 86_400_000L, jetzt / 1000)
        val dazu = fehlend.flatMap { t -> runCatching { state.market.optionsTermin(d.underlying, t) }.getOrNull().orEmpty() }
        kette = basis.first to (basis.second + dazu)
    }
    val k = kette ?: return
    if (!unterDruck) return
    // Fehlt der verkaufte Kontrakt in der Kette, gilt der Briefkurs des Depots in der Handelswaehrung
    val ersatz = state.bidAsk(d.underlying, d).second.let { if (q.fx > 0 && it > 0) it / q.fx else null }
    val e = remember(k, d.id, ersatz != null) {
        RollHelfer.rechnen(k.first, d.strike, d.expiry / 86_400_000L, k.second, System.currentTimeMillis() / 1000, ersatz)
    } ?: return
    val allein = StillhalterDepot.gruppe(account, d.gruppe).size <= 1
    fun preis(v: Double) = Fmt.price(v, d.ccy)
    fun fein(v: Double) = Fmt.priceStellen(v, d.ccy, Optionszeile.praemieStellen(v))
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var wahl by remember(d.id) { mutableStateOf<RollHelfer.Vorschlag?>(null) }

    SectionHeader(S.rhTitel)
    KeyValueRow(S.rhDruck(e.druck == RollHelfer.Druck.IM_GELD), preis(k.first))
    if (e.vorschlaege.isEmpty()) {
        Text(S.rhKeine, style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }
    e.vorschlaege.forEach { v ->
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            KontraktTitel(false, preis(v.kontrakt.strike), v.kontrakt.verfall * 1000, zusatz = S.opTage(v.tage))
            WertePaare(
                listOf(
                    WertPaar(if (v.netto >= 0) S.rhGutschrift else S.rhKosten, fein(abs(v.netto)), warnung = v.netto < 0),
                    WertPaar(S.rhNeuePraemie, fein(v.praemie)),
                ),
                Modifier.padding(top = 4.dp),
            )
            if (allein) Box(Modifier.padding(horizontal = 20.dp).heightIn(min = 48.dp).textKlick(0.dp) { wahl = v }, contentAlignment = Alignment.CenterStart) {
                Text(S.rhRollen, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link)
            }
        }
    }
    e.call?.let { c ->
        Zwischenkopf(S.rhNachAndienung)
        KontraktTitel(true, preis(c.strike), c.verfall * 1000)
        WertePaare(listOf(WertPaar(S.slPraemie, fein(c.geld))), Modifier.padding(top = 4.dp))
    }
    wahl?.let { v ->
        AlertDialog(
            onDismissRequest = { wahl = null },
            title = { Text(S.rhRollen) },
            text = { Text(S.rhFrage(preis(d.strike), preis(v.kontrakt.strike), Fmt.date(v.kontrakt.verfall * 1000))) },
            confirmButton = { TextButton(onClick = { wahl = null; if (state.rollen(p.key, v.kontrakt, k.first)) onGerollt() }) { Text(S.rhRollen) } },
            dismissButton = { TextButton(onClick = { wahl = null }) { Text(S.cancel) } },
        )
    }
}
