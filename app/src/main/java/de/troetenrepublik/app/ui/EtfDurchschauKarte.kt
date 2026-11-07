package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import de.troetenrepublik.app.engine.Durchschau
import de.troetenrepublik.app.i18n.S

// ETF-Durchschau auf der Seite eines ETFs: groesste Positionen, Branchen und Anlageklassen; haelt das Depot mehrere
// Werte, darunter die Firmen dieses ETFs, die im ganzen Depot den groessten Anteil haben. Ohne Daten bleibt sie weg.
@Composable
internal fun EtfDurchschauKarte(state: AppState, symbol: String) {
    var bestand by remember(symbol) { mutableStateOf<Durchschau.Bestand?>(null) }
    var wirkung by remember(symbol) { mutableStateOf<List<Durchschau.Wirkung>>(emptyList()) }
    LaunchedEffect(symbol) {
        bestand = state.etfBestand(symbol)
        wirkung = state.depotDurchschau()
    }
    val b = bestand ?: return
    // Nur die Kosten bekannt (5.94): die Karte bleibt weg, die TER nutzt der Depot-Check
    if (b.positionen.isEmpty() && b.branchen.isEmpty() && b.aktien + b.anleihen + b.bar <= 0) return
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.etfTitel)
        fun pct(v: Double) = if (v > 0.0005) Fmt.pct(v * 100, 1) else ""
        val klassen = S.etfKlassen(pct(b.aktien), pct(b.anleihen), pct(b.bar))
        if (klassen.isNotBlank()) {
            Text(klassen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (b.positionen.isNotEmpty()) {
            Zwischenkopf(S.etfTop)
            val groesste = b.positionen.maxOf { it.anteil }
            b.positionen.forEach { p -> ShareBar(p.name, Fmt.pct(p.anteil * 100, 1), p.anteil / groesste, MaterialTheme.colorScheme.onSurface) }
        }
        if (b.branchen.isNotEmpty()) {
            Zwischenkopf(S.etfBranchen)
            b.branchen.take(6).forEach { (k, a) -> ShareBar(S.sectorName(k), Fmt.pct(a * 100, 1), a, MaterialTheme.colorScheme.onSurface) }
        }
        // Nur die Firmen dieses ETFs, die im Depot zusammen den groessten Anteil haben
        val eigene = remember(wirkung, b) {
            val hier = b.positionen.map { Durchschau.gleich(it.symbol) }.toSet()
            wirkung.filter { it.symbol in hier && it.gesamt >= 0.005 }.take(5)
        }
        if (eigene.isNotEmpty()) {
            Zwischenkopf(S.etfDepotTitel)
            eigene.forEach { w ->
                KeyValueRow(w.name, Fmt.pct(w.gesamt * 100, 1))
                Text(
                    S.etfDepotZeile(pct(w.direkt), Fmt.pct(w.ueberEtfs * 100, 1), w.etfs.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            AufklappText(S.etfDepotHinweis, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        AufklappText(S.etfHinweis, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    }
}
