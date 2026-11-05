package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.Anlegerprofil
import de.troetenrepublik.app.engine.DepotCheck
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Symbols


// Depot-Check: die Regeln aus DepotCheck mit Ampel, Kennzahl und Begruendung. Gezaehlt wird das Depot
// ohne Wetten; Gebuehren und Ordervolumen aus den Kaeufen und Verkaeufen der letzten zwoelf Monate.
@Composable
internal fun DepotCheckTool(state: AppState) {
    val valuation = depotBewertung(state)
    val account = depotKonto(state)
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val fremd = LocalFremdesDepot.current
    // Laufende Kosten der ETFs (5.94) aus der Durchschau, je Fonds einmal am Tag abgefragt
    val etfs = valuation.positions.filter { !it.position.isDerivative && it.value > 0 && Symbols.typeOf(it.position.symbol) == AssetType.ETF }
        .map { it.position.symbol }.distinct()
    val ter by produceState(emptyMap<String, Double>(), etfs) {
        value = etfs.mapNotNull { s -> state.etfBestand(s)?.ter?.let { s to it } }.toMap()
    }
    val werte = valuation.positions.filter { it.value > 0 }.flatMap { vp ->
        val s = vp.position.symbol
        val typ = Symbols.typeOf(s)
        val w = DepotCheck.Wert(
            name = vp.position.name,
            wert = vp.value,
            art = when {
                vp.position.isDerivative -> DepotCheck.Art.DERIVAT
                typ == AssetType.ETF -> DepotCheck.Art.ETF
                typ == AssetType.CRYPTO -> DepotCheck.Art.KRYPTO
                typ == AssetType.STOCK -> DepotCheck.Art.AKTIE
                else -> DepotCheck.Art.SONST
            },
            waehrung = quotes[s]?.nativeCurrency?.takeIf { it.isNotBlank() } ?: Symbols.find(s)?.currency ?: "EUR",
            branche = Catalog.industryOf(s)?.let { S.industryName(it) } ?: Symbols.find(s)?.sector?.takeIf { it.isNotBlank() }?.let { S.sectorName(it) },
            ergebnisProzent = vp.profitPct,
            ter = if (vp.position.isDerivative) null else ter[s],
            terVorher = if (vp.position.isDerivative || ter[s] == null) null else state.terVorher(s),
        )
        // Bei Personen: Optionen auf einen Wert als eigenes Hebelprodukt
        val opt = fremd?.optionen?.get(vp.position.symbol) ?: 0.0
        if (opt <= 0 || opt >= w.wert) listOf(w)
        else listOf(w.copy(wert = w.wert - opt), w.copy(wert = opt, art = DepotCheck.Art.DERIVAT))
    }
    // Eigene Regeln und Auswertung der Thesen (seit 5.91) auch bei leerem Depot
    if (werte.isEmpty()) {
        ToolNote(S.ptNoPositions)
        if (fremd == null) { EigeneRegeln(state); AnlegerprofilTeil(account.transactions); Selbstcheck() }
        return
    }
    val seit = System.currentTimeMillis() - 365L * 86_400_000L
    // Uebernommene Positionen sind keine Orders der App und kosten nichts
    val orders = account.transactions.filter { (it.type == TxType.BUY || it.type == TxType.SELL) && it.ts >= seit && it.via != "IMPORT" }
    val befunde = DepotCheck.pruefen(
        werte, account.cash, orders.sumOf { it.fee }, orders.sumOf { it.qty * it.price }, orders.size, account.plans.size,
        heim = fremd?.heim ?: "EUR", mitKonto = fremd == null,
    )
    val gut = befunde.count { it.stufe == DepotCheck.Stufe.GUT }
    val bewertet = befunde.count { it.stufe != DepotCheck.Stufe.HINWEIS }
    Text(
        S.dcSummary(gut, bewertet), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
    )
    befunde.forEach { b ->
        val farbe = when (b.stufe) {
            DepotCheck.Stufe.GUT -> signal.up
            DepotCheck.Stufe.ACHTUNG -> signal.warn
            DepotCheck.Stufe.KRITISCH -> signal.down
            DepotCheck.Stufe.HINWEIS -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            AmpelPunkt(
                when (b.stufe) {
                    DepotCheck.Stufe.GUT -> AmpelZeichen.GUT
                    DepotCheck.Stufe.ACHTUNG -> AmpelZeichen.ACHTUNG
                    DepotCheck.Stufe.KRITISCH -> AmpelZeichen.KRITISCH
                    DepotCheck.Stufe.HINWEIS -> AmpelZeichen.HINWEIS
                },
                farbe, Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(S.dcTitle(b.regel.name), style = MaterialTheme.typography.titleSmall)
                Text(
                    S.dcValue(b.regel.name, Fmt.pct(b.anteil * 100, if (b.regel == DepotCheck.Regel.KOSTEN || b.regel == DepotCheck.Regel.FONDSKOSTEN || b.regel == DepotCheck.Regel.TER_GESTIEGEN) 2 else 0), b.name, b.anzahl),
                    style = MaterialTheme.typography.bodyMedium, color = farbe,
                )
                Text(S.dcExplain(b.regel.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    ToolNote(S.dcNote)
    if (fremd == null) { EigeneRegeln(state); AnlegerprofilTeil(account.transactions); Selbstcheck() }
}

private fun ampelZeichen(s: DepotCheck.Stufe): String = when (s) {
    DepotCheck.Stufe.GUT -> AmpelZeichen.GUT
    DepotCheck.Stufe.ACHTUNG -> AmpelZeichen.ACHTUNG
    DepotCheck.Stufe.KRITISCH -> AmpelZeichen.KRITISCH
    DepotCheck.Stufe.HINWEIS -> AmpelZeichen.HINWEIS
}

// Anlegerprofil aus den eigenen Trades (5.94), mit Ampel wie die Regeln oben.
@Composable
private fun AnlegerprofilTeil(txs: List<de.troetenrepublik.app.data.Transaction>) {
    val signal = LocalSignal.current
    val befunde = androidx.compose.runtime.remember(txs) { Anlegerprofil.auswerten(txs) }
    SectionHeader(S.apUeberschrift)
    if (befunde.none { it.merkmal == Anlegerprofil.Merkmal.TREFFER }) ToolNote(S.apZuWenig)
    befunde.forEach { b ->
        val farbe = when (b.stufe) {
            DepotCheck.Stufe.GUT -> signal.up
            DepotCheck.Stufe.ACHTUNG -> signal.warn
            DepotCheck.Stufe.KRITISCH -> signal.down
            DepotCheck.Stufe.HINWEIS -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val (a, z) = when (b.merkmal) {
            Anlegerprofil.Merkmal.HALTEDAUER -> Fmt.num(b.wert, 0) to ""
            Anlegerprofil.Merkmal.TREFFER, Anlegerprofil.Merkmal.HEBEL -> Fmt.pct(b.wert * 100, 0) to ""
            Anlegerprofil.Merkmal.CHANCE_RISIKO -> Fmt.num(b.wert, 1) to Fmt.signedPct(b.wert2, 1)
            Anlegerprofil.Merkmal.DISPOSITION -> Fmt.num(b.wert, 0) to Fmt.num(b.wert2, 0)
            Anlegerprofil.Merkmal.HAEUFIGKEIT -> Fmt.num(b.wert, 1) to ""
            Anlegerprofil.Merkmal.NACHKAUF -> "" to ""
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            AmpelPunkt(ampelZeichen(b.stufe), farbe, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(S.apTitel(b.merkmal.name), style = MaterialTheme.typography.titleSmall)
                Text(S.apWert(b.merkmal.name, a, z, b.zahl), style = MaterialTheme.typography.bodyMedium, color = farbe)
                Text(S.apErklaerung(b.merkmal.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    ToolNote(S.apHinweis)
}

// Selbstcheck (5.94): Aussagen zum Abhaken, die keine Zahl der App pruefen kann. Nur fuer diese Seite gemerkt.
@Composable
private fun Selbstcheck() {
    val fragen = S.scFragen
    val an = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { mutableStateListOf(*it.toTypedArray()) })) {
        mutableStateListOf<Int>()
    }
    SectionHeader(S.scTitel)
    Text(S.scStand(an.size, fragen.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
    fragen.forEachIndexed { i, f ->
        val gesetzt = i in an
        Row(
            Modifier.fillMaxWidth().clickable { if (gesetzt) an.remove(i) else an.add(i) }.padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = gesetzt, onCheckedChange = { if (gesetzt) an.remove(i) else an.add(i) })
            Text(f, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    ToolNote(S.scHinweis)
}
