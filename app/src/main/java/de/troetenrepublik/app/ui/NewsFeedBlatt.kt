package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.EigenerFeed
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Rss
import kotlinx.coroutines.launch

// Zeile mit Haekchen zum An- und Abwaehlen einer Quelle (5.96): Titel und Hinweis untereinander, die ganze Zeile schaltet.
@Composable
internal fun HakenZeile(titel: String, hint: String?, an: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = an, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = an, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Eigenen Feed anlegen oder bearbeiten (5.96): Name, gewaehlte Quellen der App und eigene RSS-Zugaenge. Eine feste
// Adresse der App wird als gewaehlte Quelle uebernommen, ein eigener Zugang erst nach erfolgreichem Abruf.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewsFeedBlatt(state: AppState, feed: EigenerFeed.Feed?, onGespeichert: (String) -> Unit, onDismiss: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val deutsch = settings.sprache == de.troetenrepublik.app.data.Lang.DE
    val quellen = remember(settings) { state.newsQuellen(settings) }
    val feeds = remember(settings.newsFeedKategorien) { state.newsFeeds(settings) }
    val scope = rememberCoroutineScope()

    // Zustand des Blatts: Name, gewaehlte Quellen, Zugaenge, Melden-Flag und die Pruefung einer Adresse.
    var name by remember(feed?.kennung) { mutableStateOf(feed?.name ?: "") }
    val gewaehlt = remember(feed?.kennung) { mutableStateListOf<String>().apply { feed?.quellen?.let { addAll(it) } } }
    val zugaenge = remember(feed?.kennung) { mutableStateListOf<EigenerFeed.Zugang>().apply { feed?.zugaenge?.let { addAll(it) } } }
    var melden by remember(feed?.kennung) { mutableStateOf(feed?.melden ?: true) }
    var eingabe by remember(feed?.kennung) { mutableStateOf("https://") }
    var meldung by remember(feed?.kennung) { mutableStateOf<Pair<String, Boolean>?>(null) }
    var prueft by remember(feed?.kennung) { mutableStateOf(false) }
    var loeschenFrage by remember(feed?.kennung) { mutableStateOf(false) }

    // Quellen der App zur Auswahl; ausgeschaltete gewaehlte Quellen kommen aus dem festen Bestand dazu.
    val app = quellen.filterNot { EigenerFeed.istZugang(it) }
    val appSchluessel = app.mapTo(HashSet()) { it.schluessel }
    val gruppen = (app + gewaehlt.filter { it !in appSchluessel }
        .mapNotNull { s -> Rss.eingebaut(deutsch).firstOrNull { it.schluessel == s } })
        .groupBy { it.anbieter }

    // Eingabe pruefen: feste Quelle der App uebernehmen oder eigenen Zugang nach Abruf hinzufuegen.
    fun pruefen() {
        val a = EigenerFeed.adresse(eingabe)
        val fehler = a.fehler
        if (fehler != null) {
            meldung = S.feedFehler(fehler) to true
            return
        }
        val adr = a.adresse ?: return
        // Ausgeblendete Quellen anderer Sprachen zaehlen nicht als feste Quelle: die Adresse wird ein eigener Zugang
        val fest = EigenerFeed.eingebautZu(adr, Rss.nurEnglisch(Rss.eingebaut(deutsch), settings.nurEnglischeQuellen) + app)
        if (fest != null) {
            if (fest.schluessel !in gewaehlt) gewaehlt.add(fest.schluessel)
            meldung = S.feedSchonInApp(fest.name) to false
            eingabe = "https://"
        } else if (zugaenge.any { it.adresse == adr }) {
            meldung = S.feedSchonDrin to true
        } else {
            prueft = true
            meldung = S.newsFeedChecking to false
            scope.launch {
                val p = state.newsZugangPruefen(adr)
                prueft = false
                when {
                    !p.erreichbar -> meldung = S.feedNichtErreichbar to true
                    p.anzahl == 0 -> meldung = S.feedKeinFeed to true
                    else -> {
                        val titel = p.titel.ifBlank { Rss.domain(adr) }
                        if (zugaenge.none { it.adresse == adr }) zugaenge.add(EigenerFeed.Zugang(titel, adr))
                        meldung = S.feedZugangOk(titel, p.anzahl) to false
                        eingabe = "https://"
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (feed == null) S.feedErstellen else S.feedBearbeiten, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(
                    enabled = EigenerFeed.gueltig(EigenerFeed.Feed(feed?.kennung ?: "", name, gewaehlt, zugaenge, melden)) &&
                        !prueft && (feed != null || feeds.size < EigenerFeed.MAX_FEEDS),
                    onClick = {
                        val k = feed?.kennung ?: EigenerFeed.neueKennung(feeds, System.currentTimeMillis())
                        state.newsFeedSpeichern(EigenerFeed.Feed(k, name.trim(), gewaehlt.toList(), zugaenge.toList(), melden))
                        onGespeichert(k)
                        onDismiss()
                    },
                ) { Text(S.save) }
            }
            if (feed == null && feeds.size >= EigenerFeed.MAX_FEEDS) {
                Text(S.feedZuViele(EigenerFeed.MAX_FEEDS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(EigenerFeed.MAX_NAME) },
                label = { Text(S.feedName) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (gewaehlt.isEmpty() && zugaenge.isEmpty()) {
                Text(S.feedOhneQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            ToggleRow(S.feedMelden, melden) { melden = it }
            SectionHeader(S.feedZugaenge)
            zugaenge.forEach { z ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(z.titel.ifBlank { Rss.domain(z.adresse) }, style = MaterialTheme.typography.titleSmall)
                        Text(z.adresse, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { zugaenge.remove(z) }) { Text(S.newsRemove, color = MaterialTheme.colorScheme.error) }
                }
            }
            OutlinedTextField(
                value = eingabe,
                onValueChange = { eingabe = it.take(EigenerFeed.MAX_ADRESSE + 20); meldung = null },
                label = { Text(S.newsFeedUrl) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.field),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !prueft && zugaenge.size < EigenerFeed.MAX_ZUGAENGE, onClick = { pruefen() }) { Text(S.feedPruefen) }
                if (prueft) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            meldung?.let { (text, fehler) ->
                Text(
                    text, style = MaterialTheme.typography.bodySmall,
                    color = if (fehler) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            SectionHeader(S.feedQuellenApp, unten = TITEL_VOR_ZWISCHENKOPF)
            gruppen.entries.forEachIndexed { i, (anbieter, liste) ->
                Zwischenkopf(S.newsAnbieter(anbieter), oben = if (i == 0) 0.dp else 12.dp)
                liste.forEach { q ->
                    HakenZeile(
                        q.name,
                        if (q.schluessel !in appSchluessel) S.feedQuelleAus
                        else if (q.anbieter == Rss.Anbieter.EIGENE) Rss.domain(q.adresse)
                        else S.newsCategory(q.kategorie),
                        q.schluessel in gewaehlt,
                    ) { an -> if (an) { if (q.schluessel !in gewaehlt) gewaehlt.add(q.schluessel) } else gewaehlt.remove(q.schluessel) }
                }
            }
            if (feed != null) {
                Spacer(Modifier.height(12.dp))
                ActionLineWide(S.feedLoeschen, danger = true) { loeschenFrage = true }
            }
        }
    }
    // Loeschen erst nach Bestaetigung.
    if (loeschenFrage && feed != null) {
        AlertDialog(
            onDismissRequest = { loeschenFrage = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.feedLoeschenFrage(feed.name)) },
            text = { Text(S.feedLoeschenText) },
            confirmButton = {
                TextButton(onClick = { loeschenFrage = false; state.newsFeedLoeschen(feed.kennung); onDismiss() }) {
                    Text(S.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { loeschenFrage = false }) { Text(S.cancel) } },
        )
    }
}

// Kopf eines eigenen Feed-Reiters (5.96): Quellennamen, ausgeschaltete in der Warnfarbe und der Stift zum Bearbeiten.
@Composable
internal fun FeedKopf(feed: EigenerFeed.Feed, quellen: List<Rss.Quelle>, deutsch: Boolean, onBearbeiten: () -> Unit) {
    val (namen, fehlendNamen) = remember(feed, quellen) {
        val fest = Rss.eingebaut(deutsch)
        val namen = feed.quellen.map { s ->
            quellen.firstOrNull { it.schluessel == s }?.name ?: fest.firstOrNull { it.schluessel == s }?.name ?: s
        }.distinct() + feed.zugaenge.map { it.titel.ifBlank { Rss.domain(it.adresse) } }
        val fehlendNamen = EigenerFeed.fehlend(feed, quellen).map { s -> fest.firstOrNull { it.schluessel == s }?.name ?: s }
        namen to fehlendNamen
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            PunktText(namen.joinToString(PUNKT))
            if (fehlendNamen.isNotEmpty()) {
                Text(
                    S.feedAusgeschaltet(fehlendNamen.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSignal.current.warn,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onBearbeiten) { Icon(Icons.Filled.Edit, S.feedBearbeiten, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
