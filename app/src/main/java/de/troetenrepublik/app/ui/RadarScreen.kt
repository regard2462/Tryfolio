package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.RadarSpeicher
import de.troetenrepublik.app.RadarStand
import de.troetenrepublik.app.engine.Radar
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.radarLaden
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

// Route des Radars (Kachel der Startseite).
const val RADAR_ROUTE = "radar"

// Ein Wert: Punkte, Name, Arten der Hinweise und juengster Tag; aufgeklappt jedes Signal mit Datum.
@Composable
internal fun RadarZeile(state: AppState, t: Radar.Treffer, finder: Double?, name: String, heute: Long, offen: Boolean, onToggle: () -> Unit, onOpen: (String) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Text(finder?.let { Fmt.num(it, 0) } ?: "–", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            AuslaufText("$name (${t.symbol})", maxLines = 1, style = MaterialTheme.typography.titleSmall)
            PunktZeile(listOf(S.afRadarStaerke(Fmt.num(t.punkte, 0))) + t.signale.map { S.radArt(it.art.name) }.distinct() + listOfNotNull(S.radBekannt.takeIf { t.bekannt }), color = grau, maxZeilen = 2)
        }
        Text(tagText(t.neuester, heute), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(start = 8.dp))
    }
    if (!offen) return
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, bottom = 6.dp)) {
        t.signale.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(S.radArt(s.art.name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(signalText(s), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                Text(tagText(s.tag, heute), style = MaterialTheme.typography.labelSmall, color = grau)
            }
        }
        if (t.arten > 1) Text(S.radArten(t.arten), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 4.dp))
        // Im Preis? (5.92): Lauf seit dem staerksten Hinweis gegen den Heimatindex
        t.signale.firstOrNull()?.let { s ->
            val art = when (s.art) {
                Radar.Art.INSIDER_GRUPPE, Radar.Art.INSIDER_KAUF -> "INSIDER"
                Radar.Art.KONGRESS, Radar.Art.REGIERUNG -> "MELDUNG"
                else -> "HINWEIS"
            }
            ImPreisSeitZeile(state, t.symbol, s.tag * 86_400_000L + 12 * 3_600_000L, 1, art, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            S.afZurAktie, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
            modifier = Modifier.clickable { onOpen(t.symbol) }.padding(vertical = 10.dp),
        )
    }
}

private fun tagText(tag: Long, heute: Long): String = if (tag >= heute) S.radHeute else Fmt.dayMonthNum(tag * 86_400_000L + 12 * 3_600_000L)

// Satz zu einem Signal; Betraege der Insider in Euro, der Politik, Optionen und Wetten in Dollar.
internal fun signalText(s: Radar.Signal): String {
    val (a, b) = when (s.art) {
        Radar.Art.ERWAEHNUNGEN -> Fmt.num(s.zahl, 0) to Fmt.num(s.zahl2, 0)
        Radar.Art.WIKIPEDIA -> Fmt.pct(s.zahl, 0) to ""
        Radar.Art.INSIDER_GRUPPE -> "" to Fmt.compactEuro(s.zahl2)
        Radar.Art.INSIDER_KAUF -> Fmt.compactEuro(s.zahl2) to ""
        Radar.Art.KONGRESS, Radar.Art.REGIERUNG, Radar.Art.OPTIONEN -> "" to Fmt.dollarKompakt(s.zahl2)
        Radar.Art.GROSSAKTIONAER -> (if (s.zahl > 0) Fmt.pct(s.zahl, 1) else "") to ""
        Radar.Art.VOLUMEN -> Fmt.num(s.zahl, 1) + "×" to Fmt.signedPct(s.zahl2, 1)
        Radar.Art.PROGNOSE -> Fmt.dollarKompakt(s.zahl) to ""
    }
    return S.radSignal(s.art.name, s.zahl.roundToInt(), a, b, s.text)
}
