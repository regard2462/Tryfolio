package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
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
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.MoverGrund
import de.troetenrepublik.app.engine.NachrichtArt
import de.troetenrepublik.app.engine.NachrichtEinordnung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

// Mover und Schlagzeilen (Paket q78f): Hinweise zu Zahlen und Analystenstufen, Zeile mit Grund und RVOL, Art und
// Kursbewegung je Schlagzeile mit den Filtern dazu.

// Zeitraeume der Mover in Tagen: 1T, 1W, 1M, 1J, 3J, 5J
internal val MOVER_TAGE = listOf(1, 7, 30, 365, 1095, 1825)

internal class MoverEintrag(val symbol: String, val name: String, val q: Quote, val aenderung: Double)

internal class MoverHinweise(val zahlen: Set<String>, val analyst: Set<String>) {
    companion object { val LEER = MoverHinweise(emptySet(), emptySet()) }
}

// Zahlen aus dem Kalender der Nasdaq (heute und gestern, montags auch der Freitag) und aus Schlagzeilen, Analystenstufen
// aus den Schlagzeilen der letzten zwei Tage. Genannte Werte erkennt der Katalog der Kandidaten.
@Composable
internal fun rememberMoverHinweise(state: AppState, kandidaten: List<Pair<String, String>>): MoverHinweise {
    val stand by state.news.stand.collectAsStateWithLifecycle()
    var kalender by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        val heute = LocalDate.now(ZoneId.of("America/New_York"))
        val tage = buildList {
            add(heute); add(heute.minusDays(1))
            if (heute.dayOfWeek == DayOfWeek.MONDAY) add(heute.minusDays(3))
        }
        kalender = runCatching { state.quartalszahlen(tage).values.filterNotNull().flatten().map { it.symbol }.toSet() }.getOrDefault(emptySet())
    }
    LaunchedEffect(Unit) { if (stand.eintraege.isEmpty()) runCatching { state.newsLaden() } }
    var aus by remember { mutableStateOf(MoverHinweise.LEER) }
    LaunchedEffect(stand.eintraege, kandidaten, kalender) {
        if (kandidaten.isEmpty()) { aus = MoverHinweise(kalender, emptySet()); return@LaunchedEffect }
        val grenze = System.currentTimeMillis() - 2 * 86_400_000L
        aus = withContext(Dispatchers.Default) {
            val zahlen = HashSet(kalender)
            val analyst = HashSet<String>()
            for (e in stand.eintraege) {
                if (e.zeit in 1 until grenze) continue
                val arten = NachrichtArt.arten(e.titel)
                val z = NachrichtArt.Art.ZAHLEN in arten
                val a = NachrichtArt.Art.ANALYST in arten
                if (!z && !a) continue
                for ((s, _) in NachrichtEinordnung.firmenIn(e.titel, kandidaten)) { if (z) zahlen += s; if (a) analyst += s }
            }
            MoverHinweise(zahlen, analyst)
        }
    }
    return aus
}

// Zeile eines Movers: darunter Kurs und Grund, rechts die Aenderung mit RVOL.
@Composable
internal fun MoverZeile(state: AppState, e: MoverEintrag, hinweise: MoverHinweise, onOpen: (String) -> Unit) {
    val rvol = MoverGrund.rvol(e.q)
    val grund = MoverGrund.grund(e.q, e.symbol in hinweise.zahlen, e.symbol in hinweise.analyst)
    AssetRow(
        state = state, symbol = e.symbol, name = e.name,
        subtitle = listOfNotNull(Fmt.price(e.q.price), grund?.let { S.moverGrund(it.ordinal) }).joinToString(PUNKT),
        change = e.aenderung, changeText = Fmt.pct(abs(e.aenderung)),
        rightCaption = rvol?.let { S.moverRvol(Fmt.num(it, 1)) },
        onClick = { onOpen(e.symbol) },
    )
}

// Schlagzeile mit Art und, wenn eine Aktie genannt ist, deren Symbol
internal class NewsInfo(val arten: List<NachrichtArt.Art>, val symbol: String?)

// Art und genannte Aktie der juengsten 200 Schlagzeilen, dazu aller Schlagzeilen aus extra (Reiter Depot, aeltere Schlagzeilen
// einer Art), im Hintergrund gerechnet. Schluessel ist die Kennung der Meldung; Gerechnetes bleibt erhalten.
@Composable
internal fun rememberNewsInfos(eintraege: List<Rss.Eintrag>, extra: List<Rss.Eintrag> = emptyList()): Map<String, NewsInfo> {
    var aus by remember { mutableStateOf<Map<String, NewsInfo>>(emptyMap()) }
    val katalog = remember { de.troetenrepublik.app.market.Catalog.get().aktien.map { it.s to it.n } }
    val bekannt = remember { java.util.concurrent.ConcurrentHashMap<String, NewsInfo>() }
    LaunchedEffect(eintraege, extra) {
        aus = withContext(Dispatchers.Default) {
            (eintraege.sortedByDescending { it.zeit }.take(200) + extra).associate { e ->
                e.id to bekannt.getOrPut(e.id + "|" + e.titel) { NewsInfo(NachrichtArt.arten(e.titel), NachrichtEinordnung.firmenIn(e.titel, katalog).firstOrNull()?.first) }
            }
        }
    }
    return aus
}

// Filter der Schlagzeilen: Art (null = alle) und Mindestbewegung der genannten Aktie in Prozent (0 = aus)
internal fun newsFilter(
    liste: List<Rss.Eintrag>, art: NachrichtArt.Art?, kursAb: Int, infos: Map<String, NewsInfo>, kurse: Map<String, Quote>,
): List<Rss.Eintrag> {
    if (art == null && kursAb <= 0) return liste
    return liste.filter { e ->
        val i = infos[e.id]
        (art == null || (i != null && art in i.arten)) &&
            (kursAb <= 0 || (i?.symbol?.let { s -> kurse[s]?.heutePct(System.currentTimeMillis())?.let { abs(it) >= kursAb } } == true))
    }
}

internal val NEWS_KURS_AB = listOf(1, 2, 5)

// Pillen ueber der Liste: Art der Schlagzeile und Mindestbewegung
@Composable
internal fun NewsArtenLeiste(art: NachrichtArt.Art?, kursAb: Int, onArt: (NachrichtArt.Art?) -> Unit, onKurs: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(S.all, active = art == null) { onArt(null) }
        NachrichtArt.Art.entries.forEach { a -> Chip(S.nwArt(a.ordinal), active = art == a) { onArt(if (art == a) null else a) } }
        NEWS_KURS_AB.forEach { x -> Chip(S.nwKursAb(x), active = kursAb == x) { onKurs(if (kursAb == x) 0 else x) } }
    }
}

// Zeile unter der Schlagzeile: Art, genannte Aktie und deren Kursaenderung
@Composable
internal fun NewsArtZeile(art: NachrichtArt.Art?, symbol: String?, aenderung: Double?, modifier: Modifier = Modifier) {
    if (art == null && (symbol == null || aenderung == null)) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val grau = MaterialTheme.colorScheme.onSurfaceVariant
        if (art != null) Text(S.nwArt(art.ordinal), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        if (art != null && symbol != null && aenderung != null) Text(PUNKT, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        // Zahlenkuerzel (7203.T, 000660.KS) ersetzt der Name aus dem Katalog; ohne Namen entfaellt die Angabe
        val anzeige = symbol?.let { HeatmapBeschriftung.bezeichnung(it, de.troetenrepublik.app.market.Symbols.find(it)?.name, ohneEndung = false) }
        if (anzeige != null && aenderung != null) {
            Text(anzeige + " ", style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
            MetricValue(aenderung, Fmt.pct(abs(aenderung)), style = MaterialTheme.typography.labelSmall)
        }
    }
}
