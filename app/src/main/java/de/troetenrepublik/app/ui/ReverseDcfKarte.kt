package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.ReverseDcf
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.roundToInt

// Mittleres KGV der Branche eines Werts aus den Kursen der Katalogaktien derselben Branche; null ohne genug Werte.
internal suspend fun branchenKgv(state: AppState, symbol: String): Double? {
    val katalog = Catalog.get()
    val branche = katalog.aktien.firstOrNull { it.s == symbol }?.i?.takeIf { it.isNotBlank() } ?: return null
    val andere = katalog.aktien.filter { it.i == branche && it.s != symbol }.map { it.s }.take(80)
    if (andere.size < 5) return null
    val kurse = runCatching { state.market.snapshot(andere) }.getOrDefault(emptyMap())
    return ReverseDcf.branchenKgv(kurse.values.map { it.peRatio })
}

// Reverse DCF bei den Bewertungszahlen einer Aktie: welches Gewinnwachstum ueber zehn Jahre im heutigen KGV steckt,
// bei waehlbarer Renditeerwartung und waehlbarem KGV am Ende. Bei Verlust (kein positives KGV) bleibt sie weg.
@Composable
fun ReverseDcfKarte(state: AppState, symbol: String, quote: Quote?, profile: MarketApi.Profile?) {
    val p = profile ?: return
    val kgv = p.trailingPe.takeIf { it > 0 } ?: quote?.peRatio?.takeIf { it > 0 } ?: return
    if (p.eps < 0) return
    var branche by remember(symbol) { mutableStateOf<Double?>(null) }
    var brancheGeladen by remember(symbol) { mutableStateOf(false) }
    LaunchedEffect(symbol) { branche = branchenKgv(state, symbol); brancheGeladen = true }
    var rendite by rememberSaveable(symbol) { mutableFloatStateOf(8f) }
    // Ziel-KGV: 0 = noch nicht gewaehlt, dann Branchenschnitt oder 15
    var ziel by rememberSaveable(symbol) { mutableFloatStateOf(0f) }
    val brancheRund = branche?.let { it.roundToInt().coerceIn(8, 35).toFloat() }
    val zielKgv = if (ziel > 0f) ziel else brancheRund ?: 15f
    val e = ReverseDcf.wachstum(kgv, rendite.toDouble(), zielKgv.toDouble()) ?: return

    SectionHeader(S.rdTitel)
    val satz = if (e.ausserhalb) S.rdSatzRand(Fmt.pct(e.wachstum, 0), e.wachstum > 0) else S.rdSatz(Fmt.pct(e.wachstum, 1))
    Text(satz, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    Text(S.rdKgvJetzt(Fmt.num(kgv, 1) + "×"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp))
    SliderRow(S.rdRendite, Fmt.pct(rendite.toDouble(), 1), rendite, 6f..12f, 0.5f) { rendite = it }
    SliderRow(S.rdZielKgv, Fmt.num(zielKgv.toDouble(), 0) + "×", zielKgv, 8f..35f, 1f) { ziel = it }
    if (brancheGeladen) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("15×", zielKgv == 15f) { ziel = 15f }
            brancheRund?.let { b -> Chip(S.rdBranche(Fmt.num(b.toDouble(), 0) + "×"), zielKgv == b) { ziel = b } }
        }
    }
    // Zum Vergleich: bisheriges Gewinnwachstum (Jahresgewinne) und die Erwartung der Analysten (erwartetes gegen
    // heutiges KGV, der Kurs kuerzt sich heraus)
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    ReverseDcf.bisher(p.yearly.map { it.earnings })?.let { g ->
        Text(S.rdBisher(Fmt.signedPct(g, 1), p.yearly.size - 1), style = MaterialTheme.typography.bodySmall, color = grau,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp))
    }
    p.forwardPe.takeIf { it > 0 }?.let { f ->
        Text(S.rdAnalysten(Fmt.signedPct((kgv / f - 1.0) * 100.0, 1)), style = MaterialTheme.typography.bodySmall, color = grau,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
    }
    ToolNote(S.rdHinweis)
}
