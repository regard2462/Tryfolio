package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.RadarSpeicher
import de.troetenrepublik.app.RadarStand
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.Radar
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.mausWischen
import de.troetenrepublik.app.radarLaden
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

// Radar-Ansicht im Aktienfinder (5.93): Hinweise des Radars, im Kreis die Punkte des Aktienfinders. Drei Seiten per
// Pille: alle Hinweise, Erwaehnungen, Wikipedia. Aufmerksamkeit aendert die Punkte nicht, sie wird nur angezeigt;
// Erklaerung und Hinweis zum Kreis eingeklappt (0.68).
@Composable
internal fun FinderRadarAnsicht(
    state: AppState, ergebnisse: List<Aktienfinder.Ergebnis>, nur: Set<String>?,
    stand: RadarStand?, onStand: (RadarStand) -> Unit, onOpen: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var laedt by remember { mutableStateOf<Float?>(null) }
    var offen by remember { mutableStateOf<String?>(null) }
    var zeigen by remember { mutableIntStateOf(20) }
    val heute = LocalDate.now(ZoneOffset.UTC).toEpochDay()
    val punkte = remember(ergebnisse) { ergebnisse.associate { it.symbol to it.gesamt } }

    fun laden() {
        if (laedt != null) return
        laedt = 0f
        scope.launch {
            runCatching { state.radarLaden { laedt = it } }.getOrNull()?.let(onStand)
            laedt = null
        }
    }
    LaunchedEffect(Unit) {
        val s = stand
        if (s == null || System.currentTimeMillis() - s.am > RadarSpeicher.FRISCH_MS) laden()
    }

    val f = laedt
    if (f != null) {
        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp))
        Text(S.radLaedt, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    } else if (stand == null) {
        Spacer(Modifier.height(8.dp))
        ActionLineWide(S.radStart) { laden() }
    }
    if (stand != null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(S.radStand(Fmt.relative(stand.am)), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f))
        if (laedt == null) Text(
            S.radNeu, style = MaterialTheme.typography.labelSmall, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(12.dp, 0.dp) { laden() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        )
    }

    // Drei Seiten per Pille: alle Hinweise, Erwaehnungen, Wikipedia. Beim Seitenwechsel zuklappen und weiter vorn anfangen.
    val pager = rememberPagerState { 3 }
    MitlaufendeReiter(3, pager, titel = { i -> when (i) { 0 -> S.afRadarAlle; 1 -> S.afRadarErwaehnungen; else -> S.afRadarWikipedia } }) { i ->
        scope.launch { pager.animateScrollToPage(i) }
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { zeigen = 20; offen = null } }
    HorizontalPager(state = pager, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().mausWischen(pager)) { seite ->
        Column(Modifier.fillMaxWidth()) {
            val st = stand ?: return@Column
            val arten = when (seite) { 1 -> setOf(Radar.Art.ERWAEHNUNGEN); 2 -> setOf(Radar.Art.WIKIPEDIA); else -> null }
            val liste = Radar.filtern(st.treffer, arten, nur)
            if (liste.isEmpty() && laedt == null) EmptyHint(S.radKeine)
            liste.take(zeigen).forEachIndexed { i, t ->
                if (i > 0) Trennlinie()
                RadarZeile(state, t, punkte[t.symbol], st.namen[t.symbol] ?: t.symbol, heute, offen == t.symbol, { offen = if (offen == t.symbol) null else t.symbol }, onOpen)
            }
            if (liste.size > zeigen) ActionLineWide(S.afMehrZeigen(minOf(20, liste.size - zeigen))) { zeigen += 20 }
        }
    }

    ErklaerungAufklappen(S.afRadarHinweis + "\n\n" + S.radErklaerung, Modifier.padding(top = 16.dp), titel = S.radSoRechnet)
}
