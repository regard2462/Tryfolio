package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.engine.WatchBlatt
import de.troetenrepublik.app.engine.Watchlisten
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import de.troetenrepublik.app.i18n.S

// Anzeigename einer Liste: die erste ohne eigenen Namen heisst wie die Watchlist.
fun watchlisteName(a: Account, i: Int): String = a.watchlisten.getOrNull(i)?.name?.ifBlank { null } ?: S.watchlist

// Reiterleiste der Listen oben in der Watchlist: ein Tipp blaettert den Pager (onWaehle), langes Druecken oeffnet Umbenennen und Loeschen,
// der letzte Reiter legt eine neue Liste an.
@Composable
fun WatchlistenReiter(state: AppState, account: Account, pager: PagerState, modifier: Modifier = Modifier, onWaehle: (Int) -> Unit) {
    val n = account.watchlisten.size
    var menueFuer by remember { mutableStateOf<Int?>(null) }
    var namensDialog by remember { mutableStateOf<Int?>(null) }
    var loeschen by remember { mutableStateOf<Int?>(null) }
    MitlaufendeReiter(
        n + 1, { pager.currentPage + pager.currentPageOffsetFraction },
        titel = { i -> if (i < n) watchlisteName(account, i) else "+" },
        // Wischen ueber die Reiter blaettert die Listen wie Wischen ueber die Liste
        modifier = modifier,
        wischen = Modifier.pagerZiehen(pager),
        klein = true,
        langDruecken = { i -> if (i < n) menueFuer = i },
        menue = { i ->
            DropdownMenu(
                expanded = menueFuer == i, onDismissRequest = { menueFuer = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                if (state.widgetAnheftbar) DropdownMenuItem(text = { Text(S.alsWidget) }, onClick = {
                    menueFuer = null
                    val liste = account.watchlisten.getOrNull(i)
                    if (liste != null) {
                        val v = de.troetenrepublik.app.data.WidgetVorgabe.watchliste(watchlisteName(account, i), liste.symbole)
                        state.alsKategorieWidget(v.art, v.einstellung)
                    }
                })
                DropdownMenuItem(text = { Text(S.wllUmbenennen) }, onClick = { menueFuer = null; namensDialog = i })
                if (n > 1) DropdownMenuItem(text = { Text(S.delete) }, onClick = { menueFuer = null; loeschen = i })
            }
        },
    ) { i -> if (WatchBlatt.seiteFuerReiter(i, n) != null) onWaehle(i) else namensDialog = -1 }

    namensDialog?.let { i ->
        WatchlisteNameDialog(
            titel = if (i < 0) S.wllNeu else S.wllUmbenennen,
            start = if (i < 0) "" else watchlisteName(account, i),
            onDismiss = { namensDialog = null },
        ) { name ->
            namensDialog = null
            if (i < 0) state.watchlisteNeu(name) else state.watchlisteUmbenennen(i, name)
        }
    }
    loeschen?.let { i ->
        AlertDialog(
            onDismissRequest = { loeschen = null },
            title = { Text(S.wllLoeschenTitel) },
            text = { Text(S.wllLoeschenText(watchlisteName(account, i))) },
            confirmButton = { TextButton(onClick = { loeschen = null; state.watchlisteLoeschen(i) }) { Text(S.delete) } },
            dismissButton = { TextButton(onClick = { loeschen = null }) { Text(S.cancel) } },
        )
    }
}

@Composable
private fun WatchlisteNameDialog(titel: String, start: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titel) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(Watchlisten.MAX_NAME) }, singleLine = true,
                label = { Text(S.wllName) },
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(name) }) { Text(S.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}

// Auswahl der Listen mit Haekchen fuer einen Wert, wenn es mehrere Listen gibt (Stern, Plus, Menue nach langem Druecken).
@Composable
fun WatchlistenWahlDialog(state: AppState) {
    val symbol by state.watchWahl.collectAsStateWithLifecycle()
    val s = symbol ?: return
    val konto by state.account.collectAsStateWithLifecycle()
    val wahl = remember(s) {
        mutableStateListOf<Int>().also { l ->
            val drin = Watchlisten.listenMit(state.account.value, s)
            l.addAll(if (drin.isEmpty()) setOf(state.watchAktiv.value.coerceIn(0, state.account.value.weitereListen.size)) else drin)
        }
    }
    AlertDialog(
        onDismissRequest = { state.watchWahlSchliessen() },
        title = { Text(S.wllWaehlen) },
        text = {
            androidx.compose.foundation.layout.Column {
                konto.watchlisten.indices.forEach { i ->
                    val an = i in wahl
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Checkbox) { if (an) wahl.remove(i) else wahl.add(i) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = an, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(watchlisteName(konto, i), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { state.watchWaehlen(s, wahl.toSet()) }) { Text(S.save) } },
        dismissButton = { TextButton(onClick = { state.watchWahlSchliessen() }) { Text(S.cancel) } },
    )
}

// Waagrechtes Ziehen ueber einer Flaeche neben dem Pager (Reiterleiste) bewegt den Pager mit; beim Loslassen rastet er
// auf der Seite ein, die WatchBlatt.zielSeite bestimmt. Senkrechtes Ziehen bleibt unberuehrt.
@Composable
private fun Modifier.pagerZiehen(pager: PagerState): Modifier {
    val scope = rememberCoroutineScope()
    val zustand = rememberDraggableState { dx -> pager.dispatchRawDelta(-dx) }
    return this.draggable(
        state = zustand,
        orientation = Orientation.Horizontal,
        onDragStopped = { v ->
            val ziel = WatchBlatt.zielSeite(pager.currentPage, pager.currentPageOffsetFraction, -v, pager.pageCount)
            scope.launch { pager.animateScrollToPage(ziel) }
        },
    )
}
