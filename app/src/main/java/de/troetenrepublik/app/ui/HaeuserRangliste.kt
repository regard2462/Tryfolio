package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Haeuserliste
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Daten
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// [0.77] Rangliste der Analystenhaeuser nach Trefferanteil (Datei analysten/haeuser.json): als Werkzeug und als
// aufklappbarer Abschnitt im Analystenblatt einer Aktie. Das Mass ist sachlich: Anteil der Urteile, nach denen die Aktie
// in Richtung des Urteils gegen ihren Index lief, und der mittlere Vorsprung.

// Gelesene Datei, einmal je Programmlauf.
internal object HaeuserBestand {
    private var gelesen = false
    private var liste: Haeuserliste.Liste? = null

    @Synchronized
    fun holen(): Haeuserliste.Liste? {
        if (!gelesen) {
            liste = runCatching { Haeuserliste.lesen(Daten.text("analysten/haeuser.json")) }.getOrNull()
            gelesen = true
        }
        return liste
    }

    @Synchronized
    fun bereit(): Pair<Boolean, Haeuserliste.Liste?> = gelesen to liste
}

// Liste im Hintergrund lesen; erstes Glied: Lesen abgeschlossen.
@Composable
internal fun haeuserliste(): Pair<Boolean, Haeuserliste.Liste?> {
    val stand by produceState(HaeuserBestand.bereit()) {
        value = withContext(Dispatchers.Default) { HaeuserBestand.holen() }.let { true to it }
    }
    return stand
}

// Zeile eines Hauses: Rang und Name, Trefferanteil, Balken (Strich bei 50 %), Zahl der Urteile und Vorsprung.
@Composable
internal fun HausRangZeile(rang: Int, h: Haeuserliste.Haus, sortierung: Haeuserliste.Sortierung = Haeuserliste.Sortierung.TREFFER) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    // Sortiert nach "Gesichert": der gesicherte Wert ist die Hauptzahl, der Trefferanteil steht klein bei den Urteilen
    val gesichert = sortierung == Haeuserliste.Sortierung.GESICHERT
    val haupt = if (gesichert) Haeuserliste.gesichert(h) else h.quote
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rang.toString(), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.width(28.dp))
            Text(h.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(Fmt.pct(haupt * 100, 0), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            // Über der Zufallsmarke von 50 % dezent grün, darunter dezent rot
            val signal = LocalSignal.current
            Box(Modifier.fillMaxWidth(haupt.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background((if (haupt >= 0.5) signal.up else signal.down).copy(alpha = 0.8f)))
            Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val urteile = S.hrUrteile(Fmt.num(h.urteile.toDouble(), 0))
            Text(if (gesichert) S.hrUrteileTreffer(urteile, Fmt.pct(h.quote * 100, 0)) else urteile, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            Text(S.hrVorsprung, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            ChangeValue(h.vorsprung, Fmt.pct(abs(h.vorsprung), 1), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Zeile mit Stand und Zeitraum der Daten.
@Composable
private fun StandZeile(l: Haeuserliste.Liste) {
    val von = Haeuserliste.zeit(l.von)?.let { Fmt.date(it) }
    val bis = Haeuserliste.zeit(l.bis)?.let { Fmt.date(it) }
    val stand = Haeuserliste.zeit(l.stand)?.let { Fmt.date(it) }
    PunktText(
        S.hrStand(stand.orEmpty(), von.orEmpty(), bis.orEmpty(), l.aktien, l.urteile),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

// Zeilen der Rangliste, ab der Grenze eingeklappt.
@Composable
private fun HaeuserZeilen(l: Haeuserliste.Liste, zuerst: Int, schluessel: String, sortierung: Haeuserliste.Sortierung = Haeuserliste.Sortierung.TREFFER) {
    var alle by rememberSaveable(schluessel) { mutableStateOf(false) }
    val geordnet = remember(l, sortierung) { if (sortierung == Haeuserliste.Sortierung.TREFFER) l.haeuser else Haeuserliste.sortiert(l.haeuser, sortierung) }
    val sichtbar = if (alle) geordnet else geordnet.take(zuerst)
    sichtbar.forEachIndexed { i, h ->
        if (i > 0) Trennlinie()
        HausRangZeile(i + 1, h, sortierung)
    }
    if (l.haeuser.size > zuerst) {
        Text(
            if (alle) S.less else S.bbAlle(l.haeuser.size), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
            modifier = Modifier.textKlick(20.dp) { alle = !alle }.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

// Werkzeug: alle Haeuser ab der Mindestzahl an Urteilen.
@Composable
internal fun HaeuserRanglisteTool(state: AppState) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val (fertig, liste) = haeuserliste()
    val l = liste
    if (l == null) {
        ToolNote(if (fertig) S.hrKeine else S.anLaedt)
        return
    }
    MarktInfoKopf(S.hrTrefferTitel, S.hrInfo(l.minUrteile))
    val sortierung = Haeuserliste.Sortierung.von(settings.haeuserSortierung)
    StandZeile(l)
    // Zahl der Haeuser links, rechts der Sortierwaehler wie in der Watchlist: ein Tipp oeffnet das Menue
    var sortMenue by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(S.hrAnzahl(l.haeuser.size), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Box {
            Row(
                Modifier
                    .textKlick(4.dp, role = androidx.compose.ui.semantics.Role.Button) { sortMenue = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.hrSortierung(sortierung.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(6.dp))
                ChevronDown(width = 10.dp)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = sortMenue, onDismissRequest = { sortMenue = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Haeuserliste.Sortierung.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(S.hrSortierung(s.name)) },
                        trailingIcon = if (s == sortierung) { { CheckMark(size = 15.dp) } } else null,
                        onClick = { sortMenue = false; scope.launch { state.settings.update { it.copy(haeuserSortierung = s.name) } } },
                    )
                }
            }
        }
    }
    HaeuserZeilen(l, 10, "hr-tool", sortierung)
}

// Abschnitt im Analystenblatt: zugeklappt eine Zeile mit Zahl der Haeuser und Spanne des Trefferanteils.
@Composable
internal fun HaeuserRanglisteAbschnitt(l: Haeuserliste.Liste) {
    var offen by rememberSaveable { mutableStateOf(false) }
    val unter = S.hrUnter(l.haeuser.size, Fmt.pct(l.haeuser.minOf { it.quote } * 100, 0), Fmt.pct(l.haeuser.maxOf { it.quote } * 100, 0))
    AufklappKopf(S.hrTitel, unter, offen) { offen = !offen }
    if (!offen) return
    StandZeile(l)
    HaeuserZeilen(l, 5, "hr-blatt")
}
