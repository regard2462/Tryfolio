package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Auswertung
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Portfolioverlauf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// So viele der meistgehandelten Werte bekommen Kurse; je 20 eine Sammelabfrage.
private const val AUSWERTUNG_WERTE = 60

// Auswertung der Geschaefte einer Person: Kaeufe und Verkaeufe danach, Groesse und Haeufigkeit, auffaellige
// Monate und Geschaefte vor grossen Kursbewegungen. Kurse: fuenf Jahre Tageskurse, bis zu 20 Werte je Abfrage,
// dazu der S&P 500 als Vergleich.
@Composable
fun AuswertungKarte(state: AppState, geschaefte: List<Kongress.Geschaeft>) {
    val signal = LocalSignal.current
    var ergebnis by remember(geschaefte) { mutableStateOf<Auswertung.Ergebnis?>(null) }
    var laedt by remember(geschaefte) { mutableStateOf(true) }
    LaunchedEffect(geschaefte) {
        if (geschaefte.isEmpty()) { laedt = false; return@LaunchedEffect }
        val werte = Auswertung.werte(geschaefte, AUSWERTUNG_WERTE)
        val symbole = werte.associateWith { CopyRouten.symbol(it) }
        val kerzen = coroutineScope {
            (symbole.values.toList() + "^GSPC").distinct().chunked(20).map { teil -> async { state.schlusskurse(teil, "5y") } }.awaitAll()
        }.fold(HashMap<String, List<de.troetenrepublik.app.data.Candle>>()) { m, x -> m.apply { putAll(x) } }
        ergebnis = withContext(Dispatchers.Default) {
            val kurse = werte.mapNotNull { k -> kerzen[symbole.getValue(k)]?.takeIf { it.size > 20 }?.let { k to Portfolioverlauf.karte(it) } }.toMap()
            Auswertung.rechnen(
                geschaefte, kurse, kerzen["^GSPC"]?.let { Portfolioverlauf.karte(it) },
                { k -> Catalog.industryOf(CopyRouten.symbol(k))?.let { S.industryName(it) } },
                System.currentTimeMillis(),
            )
        }
        laedt = false
    }

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.awTitle)
        val e = ergebnis
        if (e == null) {
            Text(if (laedt) S.awLoading else S.awNone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        } else {
            AuswertungInhalt(e, signal)
        }
    }
}

@Composable
private fun AuswertungInhalt(e: Auswertung.Ergebnis, signal: Signal) {
    Column(Modifier.fillMaxWidth()) {
        fun pct(teil: Int, ganz: Int) = Fmt.pct(if (ganz > 0) teil * 100.0 / ganz else 0.0, 0)

        if (e.kaeufe.isNotEmpty()) {
            Unterkopf(S.awBuysAfter)
            e.kaeufe.forEach { h ->
                Zeile(S.awHorizon(h.tage), S.awBuyLine(pct(h.treffer, h.anzahl), pct(h.gegenMarkt, h.anzahl), Fmt.signedPct(h.median * 100, 1), h.medianMarkt?.let { Fmt.signedPct(it * 100, 1) }, h.anzahl))
            }
        }
        if (e.verkaeufe.isNotEmpty()) {
            Unterkopf(S.awSalesAfter)
            e.verkaeufe.forEach { h ->
                Zeile(S.awHorizon(h.tage), S.awSellLine(pct(h.treffer, h.anzahl), pct(h.gegenMarkt, h.anzahl), Fmt.signedPct(h.median * 100, 1), h.anzahl))
            }
        }

        Unterkopf(S.awFrequency)
        val f = e.haeufigkeit
        KeyValueRow(S.awPerMonth, Fmt.num(f.proMonat, 1))
        f.staerksterMonat?.let { KeyValueRow(S.awBusiest, "${Fmt.monthYear(it)} · ${Fmt.num(f.staerksterAnzahl.toDouble(), 0)}") }
        KeyValueRow(S.awTypical, Fmt.dollarKompakt(f.medianBetrag))
        KeyValueRow(S.awOverMillion, Fmt.num(f.ueberMillion.toDouble(), 0))
        KeyValueRow(S.awBuyShare, Fmt.pct(f.kaufAnteil * 100, 0))

        if (e.auffaellig.isNotEmpty()) {
            Unterkopf(S.awUnusual)
            e.auffaellig.forEach { m -> Zeile(Fmt.monthYear(m.monat), S.awUnusualLine(Fmt.num(m.anzahl.toDouble(), 0), Fmt.num(m.schnitt, 0))) }
        }

        e.vorBewegung?.let { v ->
            Unterkopf(S.awBeforeMoves)
            if (v.kaeufe > 0) Text(S.awBeforeBuy(pct(v.vorAnstieg, v.kaeufe), Fmt.pct(v.basisKauf * 100, 0), v.kaeufe), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (v.verkaeufe > 0) Text(S.awBeforeSell(pct(v.vorRueckgang, v.verkaeufe), Fmt.pct(v.basisVerkauf * 100, 0), v.verkaeufe), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        AufklappText(S.awNote(e.bewertet, e.werte, AUSWERTUNG_WERTE), Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

@Composable
private fun Unterkopf(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun Zeile(titel: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}
