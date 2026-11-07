package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Quote
import kotlin.math.abs

// Reihenfolge wie im Menue (seit 5.92 mit Hinzufuegedatum).
private enum class WatchSort { CHANGE, PRICE, NAME, MARKET_CAP, ADDED, SINCE_ADDED }

// Werte ohne Boersenwert einer Firma, die beim Sortieren nach Marktwert ans Ende kommen
private fun ohneFirmenwert(symbol: String): Boolean = Symbols.typeOf(symbol).let {
    it == AssetType.ETF || it == AssetType.INDEX || it == AssetType.COMMODITY || it == AssetType.CURRENCY
}

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
    sofort: Boolean = false,
    // Scrolllage, solange das Blatt fuer eine aus ihm geoeffnete Seite zu ist (Wiederkehr).
    lage: WatchlistLage = remember { WatchlistLage() },
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        WatchlistInhalt(state, onOpen, onAdd, lage, merken = true)
    }
}

// Bild der Watchlist unter einer aus ihr geoeffneten Seite (siehe BlattBild), an derselben Stelle wie das Blatt.
@Composable
fun WatchlistBild(state: AppState, lage: WatchlistLage) =
    WatchlistInhalt(state, onOpen = {}, onAdd = {}, lage = lage, merken = false)

// Wo das Watchlist-Blatt stand: die Scrolllage. Das Blatt schliesst sich, waehrend eine aus ihm geoeffnete Seite
// offen ist; danach und im Bild darunter steht es wieder genau dort.
@Stable
class WatchlistLage {
    var watchIndex by mutableIntStateOf(0)
    var watchVersatz by mutableIntStateOf(0)
}

@Composable
private fun WatchlistInhalt(
    state: AppState,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
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
            // Groesste Firmen zuerst; ETFs, Indizes, Rohstoffe und Waehrungen haben keinen Boersenwert einer Firma und
            // stehen immer am Ende, nach Art und Name (ein Fondsvolumen waere mit Firmenwerten nicht vergleichbar)
            WatchSort.MARKET_CAP -> list.sortedWith(
                compareBy<Pair<String, Quote?>> { (s, _) -> ohneFirmenwert(s) }
                    .thenByDescending { (s, q) -> if (ohneFirmenwert(s)) 0.0 else q?.marketCap?.takeIf { it > 0 } ?: -1.0 }
                    .thenBy { (s, _) -> Symbols.typeOf(s).ordinal }
                    .thenBy { (s, q) -> kurzName(s, q?.name.orEmpty()).lowercase() },
            )
            WatchSort.SINCE_ADDED -> list.sortedByDescending { (s, q) ->
                q?.let { watchChange(it, account.watchMeta[s], de.troetenrepublik.app.data.WatchMetric.SINCE_ADDED_PCT) } ?: -999.0
            }
            // Zuletzt hinzugefuegt zuerst; ohne Datum ans Ende
            WatchSort.ADDED -> list.sortedByDescending { (s, _) -> account.watchMeta[s]?.addedAt ?: 0L }
        }
    }
    fun sortText(w: WatchSort) = when (w) {
        WatchSort.NAME -> S.sortByName; WatchSort.CHANGE -> S.sortByChange; WatchSort.PRICE -> S.sortByPrice
        WatchSort.MARKET_CAP -> S.wlSortMarktwert
        WatchSort.SINCE_ADDED -> S.wlSortSeit; WatchSort.ADDED -> S.wlSortDatum
    }
    var sortMenue by remember { mutableStateOf(false) }

    // Nur die Watchlist; Trends, Stimmung und Heatmap stehen auf der Seite „Maerkte“
    Column(Modifier.fillMaxWidth()) {
        Text(S.watchlist, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp))
      // Die Pille steht unter der Liste, damit sie keinen Eintrag verdeckt
      Column(Modifier.fillMaxWidth().weight(1f, fill = false)) {
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
                    // Ein Tipp oeffnet das Menue der Sortierungen, wie die Auswahl der Zeitraeume auf der Startseite
                    androidx.compose.foundation.layout.Box {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(Radius.chip))
                                .clickable(role = Role.Button) { sortMenue = true }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sortText(sort), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            ChevronDown(width = 10.dp)
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = sortMenue, onDismissRequest = { sortMenue = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            WatchSort.entries.forEach { w ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(sortText(w)) },
                                    trailingIcon = if (w == sort) { { CheckMark(size = 15.dp) } } else null,
                                    onClick = { sort = w; sortMenue = false },
                                )
                            }
                        }
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
                    // Zweitzeile links der Kurs; bei „seit Hinzufuegen“ steht das Datum rechts unter dem Wert, bei „seit
                    // erstem Hinzufuegen“ das erste Datum (5.95: neben dem Kurs brach die Zeile bei langen Kursen dreizeilig um)
                    val seit = when {
                        meta == null -> null
                        metric.isSinceAdded -> S.addedOn(Fmt.date(meta.addedAt))
                        metric.isSinceFirst -> S.firstAddedOn(Fmt.date(meta.ersterZeitpunkt))
                        else -> null
                    }
                    // Nach Marktkapitalisierung sortiert: der Boersenwert in kurzer Form rechts unter dem Wert, ohne Firmenwert ein
                    // Strich, damit die Unterzeile in dieser Sortierung ueberall dasselbe zeigt (0.68)
                    val boersenwert = if (sort != WatchSort.MARKET_CAP) null
                        else q?.marketCap?.takeIf { it > 0 && !ohneFirmenwert(symbol) }?.let { Fmt.mitSymbol(Fmt.compact(Fmt.anzeige(it)), Fmt.symbol) } ?: "–"
                    AssetRow(
                        symbol = symbol,
                        name = kurzName(symbol, q?.name.orEmpty()),
                        subtitle = if (q != null) Fmt.price(q.price) else "–",
                        rightCaption = boersenwert ?: seit,
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
        accent != null && filled -> aufFarbe(accent)
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
