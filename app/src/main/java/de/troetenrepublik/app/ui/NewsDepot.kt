package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.AnalystenStufen
import de.troetenrepublik.app.engine.Analystenbilanz
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.delay

// Neue Hoch- und Abstufungen der letzten sieben Tage zu Werten im Depot, oben im Reiter „Depot“ der Nachrichtenseite.
// Urteile wie im Werkzeug „Stufen“ (gleicher Zwischenspeicher); ohne Treffer erscheint nichts.
@Composable
internal fun DepotStufen(state: AppState, symbole: List<String>, onOpen: (String) -> Unit) {
    var eintraege by remember(symbole) { mutableStateOf<List<AnalystenStufen.Eintrag>>(emptyList()) }
    LaunchedEffect(symbole) {
        eintraege = emptyList()
        for (s in symbole) {
            val jetzt = System.currentTimeMillis()
            val urteile = StufenUrteile.karte[s]?.takeIf { jetzt - it.first < StufenUrteile.FRIST_MS }?.second ?: run {
                delay(300)
                runCatching { state.market.analysten(s) }.getOrNull()?.urteile?.also { StufenUrteile.karte[s] = System.currentTimeMillis() to it }
            }
            if (urteile != null) eintraege = AnalystenStufen.ordnen(eintraege + AnalystenStufen.neue(s, urteile, System.currentTimeMillis()))
        }
    }
    if (eintraege.isEmpty()) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    fun note(g: String) = Analystenbilanz.stufe(g)?.let { S.anStufe(it) } ?: g.ifBlank { "–" }
    // Mehr als drei Stufen: zugeklappt auf eine Zeile mit der Zahl der Hoch- und Abstufungen
    val hoch = eintraege.count { it.richtung > 0 }
    val ab = eintraege.count { it.richtung < 0 }
    val kern = KernZeile(listOf(KernTeil(hoch.toString(), if (hoch > 0) 1.0 else 0.0), KernTeil(ab.toString(), if (ab > 0) -1.0 else 0.0)))
    var alle by remember { mutableStateOf(false) }
    KernAbschnitt(S.newsDepotStufen, kern, null, "depot-stufen", gross = false, lohnt = eintraege.size > 3) {
        (if (alle) eintraege else eintraege.take(15)).forEachIndexed { i, e ->
            if (i > 0) Trennlinie()
            val farbe = if (e.richtung > 0) signal.up else signal.down
            Column(Modifier.fillMaxWidth().clickable { onOpen(e.symbol) }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChangeValue(e.richtung.toDouble(), Symbols.find(e.symbol)?.name ?: e.symbol, Modifier.weight(1f), nullAusText = false)
                    Text(
                        when (e.aktion) { "up" -> S.anHochgestuft; "down" -> S.anAbgestuft; else -> S.stErst },
                        style = MaterialTheme.typography.labelSmall, color = farbe, maxLines = 1, softWrap = false,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.haus, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
                    Text(Fmt.date(e.zeit), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 12.dp))
                }
                val stufen = if (e.von.isBlank() || e.aktion == "init") note(e.nach) else S.st("an.wechsel", listOf(note(e.von), note(e.nach)))
                Text(stufen, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (eintraege.size > 15) ListenPfeil(alle, label = if (alle) S.less else "${S.more} (${eintraege.size - 15})") { alle = !alle }
        Trennlinie()
    }
}
