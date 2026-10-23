package de.troetenrepublik.app.ui

import androidx.compose.foundation.rememberScrollState
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.lerp
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs

private enum class WatchSort { NAME, CHANGE, PRICE, SINCE_ADDED }

// Watchlist als Blatt von unten, mit Sortierung und Zaehler. Die Kennzahl rechts wechselt per Tipp: Tagestrend,
// seit dem Hinzufuegen und, wenn ein Wert wieder aufgenommen wurde, seit dem ersten Hinzufuegen; je relativ
// oder absolut.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit = {},
    // Seite: 0 Watchlist, 1 Trends, 2 Heatmap; onSeite meldet jeden Wechsel, damit das Blatt dort wiederkommt (Wiederkehr).
    seite: Int = 0,
    onSeite: (Int) -> Unit = {},
    sofort: Boolean = false,
    // Scrolllage und Auswahl, solange das Blatt fuer eine aus ihm geoeffnete Seite zu ist (Wiederkehr).
    lage: WatchlistLage = remember { WatchlistLage() },
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        WatchlistInhalt(state, onOpen, onAdd, seite, onSeite, lage, merken = true)
    }
}

// Bild der Watchlist unter einer aus ihr geoeffneten Seite (siehe BlattBild), an derselben Stelle wie das Blatt.
@Composable
fun WatchlistBild(state: AppState, seite: Int, lage: WatchlistLage) =
    WatchlistInhalt(state, onOpen = {}, onAdd = {}, seite = seite, onSeite = {}, lage = lage, merken = false)

// Wo das Watchlist-Blatt stand: Scrolllage beider Seiten und die Auswahl in den Trends. Das Blatt schliesst
// sich, waehrend eine aus ihm geoeffnete Seite offen ist; danach und im Bild darunter steht es wieder genau dort.
@Stable
class WatchlistLage {
    var watchIndex by mutableIntStateOf(0)
    var watchVersatz by mutableIntStateOf(0)
    val trends = TrendsLage()
}

@Composable
private fun WatchlistInhalt(
    state: AppState,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    seite: Int,
    onSeite: (Int) -> Unit,
    lage: WatchlistLage,
    // Nur das echte Blatt schreibt seine Lage zurueck, das Bild darunter nicht.
    merken: Boolean,
) {
    val account by state.account.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(WatchSort.NAME) }
    val metric = settings.watchMetric

    val entries = remember(account.watchlist, quotes, sort, account.watchMeta) {
        val list = account.watchlist.map { it to quotes[it] }
        when (sort) {
            WatchSort.NAME -> list.sortedBy { (s, q) -> (q?.name?.ifBlank { Symbols.name(s) } ?: Symbols.name(s)).lowercase() }
            WatchSort.CHANGE -> list.sortedByDescending { (_, q) -> q?.changePct ?: -999.0 }
            WatchSort.PRICE -> list.sortedByDescending { (_, q) -> q?.price ?: 0.0 }
            WatchSort.SINCE_ADDED -> list.sortedByDescending { (s, q) ->
                q?.let { watchChange(it, account.watchMeta[s], de.troetenrepublik.app.data.WatchMetric.SINCE_ADDED_PCT) } ?: -999.0
            }
        }
    }
    val sortLabel = when (sort) {
        WatchSort.NAME -> S.sortByName; WatchSort.CHANGE -> S.sortByChange; WatchSort.PRICE -> S.sortByPrice; WatchSort.SINCE_ADDED -> S.sortBySinceAdded
    }

    run {
      // Drei Seiten: Watchlist, Trends und Heatmap; per Wischen oder Tipp auf den Titel
      val pager = rememberPagerState(initialPage = seite.coerceIn(0, 2)) { 3 }
      LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { onSeite(it) } }
      val scope = rememberCoroutineScope()
      Column(Modifier.fillMaxWidth()) {
        val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 2f)
        // Drei Titel passen nicht immer nebeneinander: etwas kleiner und die Zeile rollt mit
        Row(Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf(S.watchlist, S.trendsTitel, S.heatmap).forEachIndexed { i, titel ->
                Text(
                    titel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface, (1f - abs(pos - i)).coerceIn(0f, 1f)),
                    // Ohne abgerundete Klickflaeche, die sonst die Buchstaben an den Ecken abschneidet
                    modifier = Modifier.clickable(interactionSource = null, indication = null) { scope.launch { pager.animateScrollToPage(i) } },
                )
            }
        }
        // Oben ausgerichtet, sonst haengt eine leere Watchlist in der Mitte der Hoehe, die die Trends vorgeben
        HorizontalPager(state = pager, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).mausWischen(pager)) { seite ->
          if (seite == 1) {
            TrendsSeite(state, onOpen, lage.trends, merken)
            return@HorizontalPager
          }
          if (seite == 2) {
            HeatmapReiter(state, onOpen)
            return@HorizontalPager
          }
      // Die Pille steht unter der Liste, damit sie keinen Eintrag verdeckt
      Column(Modifier.fillMaxWidth()) {
        val liste = rememberListeAb({ lage.watchIndex }, { lage.watchVersatz })
        if (merken) LaunchedEffect(liste) {
            snapshotFlow { liste.firstVisibleItemIndex to liste.firstVisibleItemScrollOffset }.collect { (i, v) -> lage.watchIndex = i; lage.watchVersatz = v }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = liste) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(S.entriesCount(account.watchlist.size), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(Radius.chip))
                            .clickable { sort = WatchSort.entries[(sort.ordinal + 1) % WatchSort.entries.size] }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sortLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        ChevronDown(width = 10.dp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(Radius.chip)).clickable { state.cycleWatchMetric() }.padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(S.watchMetricLabel(metric), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        ChevronDown(width = 10.dp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (entries.isEmpty()) {
                item { EmptyHint(S.noWatchlist) }
            } else {
                items(entries, key = { it.first }) { (symbol, q) ->
                    val meta = account.watchMeta[symbol]
                    val change = q?.let { watchChange(it, meta, metric) }
                    // Zweitzeile: Kurs, bei „seit Hinzufuegen“ das Datum, bei „seit erstem Hinzufuegen“ das erste Datum
                    val seit = when {
                        meta == null -> ""
                        metric.isSinceAdded -> " · " + S.addedOn(Fmt.date(meta.addedAt))
                        metric.isSinceFirst -> " · " + S.firstAddedOn(Fmt.date(meta.ersterZeitpunkt))
                        else -> ""
                    }
                    AssetRow(
                        symbol = symbol,
                        name = kurzName(symbol, q?.name.orEmpty()),
                        subtitle = if (q != null) Fmt.price(q.price) + seit else "–",
                        change = change,
                        changeText = change?.let { if (metric.isAbsolute) Fmt.money(abs(it)) else Fmt.pct(abs(it)) } ?: if (q != null && (metric.isSinceAdded || metric.isSinceFirst)) "–" else null,
                        onValueClick = { state.cycleWatchMetric() },
                        onClick = { onOpen(symbol) },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        FloatingPill(
            label = S.add,
            plus = true,
            filled = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            onClick = onAdd,
        )
      }
        }
      }
    }
}

// Aktionspille, wie sie unten ueber den Listen schwebt: voll abgerundet, gefuellt weiss mit
// schwarzer Schrift oder als ruhige Variante in Dunkelgrau. accent faerbt die Pille (Derivate).
@Composable
fun FloatingPill(
    label: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    filled: Boolean = true,
    icon: ImageVector? = null,
    plus: Boolean = false,
    enabled: Boolean = true,
    accent: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val background = when {
        accent != null && filled -> accent
        filled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        accent != null && filled -> androidx.compose.ui.graphics.Color.Black
        filled -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onSurface
    }
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .background(background.copy(alpha = alpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = if (icon != null || trailing != null || plus) Modifier.weight(1f) else Modifier,
        )
        when {
            plus -> {
                Spacer(Modifier.width(10.dp))
                PlusGlyph(size = 17.dp, color = content, stroke = 2.2.dp)
            }
            icon != null -> {
                Spacer(Modifier.width(10.dp))
                Icon(icon, null, modifier = Modifier.size(19.dp), tint = content)
            }
            trailing != null -> {
                Spacer(Modifier.width(10.dp))
                Text(trailing, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}
