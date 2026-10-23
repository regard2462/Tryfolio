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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.DepotCheck
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Symbols

// Gelb der Ampel, wie die Dividenden im Kalender.
private val GELB = Color(0xFFF5C33B)

// Depot-Check: die Regeln aus DepotCheck mit Ampel, Kennzahl und Begruendung. Gezaehlt wird das Depot
// ohne Wetten; Gebuehren und Ordervolumen aus den Kaeufen und Verkaeufen der letzten zwoelf Monate.
@Composable
internal fun DepotCheckTool(state: AppState) {
    val valuation = depotBewertung(state)
    val account = depotKonto(state)
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val fremd = LocalFremdesDepot.current
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
        )
        // Bei Personen: Optionen auf einen Wert als eigenes Hebelprodukt
        val opt = fremd?.optionen?.get(vp.position.symbol) ?: 0.0
        if (opt <= 0 || opt >= w.wert) listOf(w)
        else listOf(w.copy(wert = w.wert - opt), w.copy(wert = opt, art = DepotCheck.Art.DERIVAT))
    }
    if (werte.isEmpty()) { ToolNote(S.ptNoPositions); return }
    val seit = System.currentTimeMillis() - 365L * 86_400_000L
    val orders = account.transactions.filter { (it.type == TxType.BUY || it.type == TxType.SELL) && it.ts >= seit }
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
            DepotCheck.Stufe.ACHTUNG -> GELB
            DepotCheck.Stufe.KRITISCH -> signal.down
            DepotCheck.Stufe.HINWEIS -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(farbe))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(S.dcTitle(b.regel.name), style = MaterialTheme.typography.titleSmall)
                Text(
                    S.dcValue(b.regel.name, Fmt.pct(b.anteil * 100, if (b.regel == DepotCheck.Regel.KOSTEN) 2 else 0), b.name, b.anzahl),
                    style = MaterialTheme.typography.bodyMedium, color = farbe,
                )
                Text(S.dcExplain(b.regel.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    ToolNote(S.dcNote)
}
