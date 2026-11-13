package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.AnalystenStufen
import de.troetenrepublik.app.engine.Analystenbilanz
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Zwischenspeicher der Urteile je Aktie: eine Stunde fuer Depot, Watchlist und gesuchte Werte, zwoelf Stunden fuer
// die grossen Werte der Indexlisten.
internal object StufenUrteile {
    val karte = HashMap<String, Pair<Long, List<Analystenbilanz.Urteil>>>()
    const val FRIST_MS = 60 * 60_000L
    const val FRIST_GROSS_MS = 12 * 60 * 60_000L

    // Pause vor jedem Abruf
    const val PAUSE_MS = 300L

    // Grosse US-Werte aus dem Katalog (Dow Jones und Nasdaq 100); Urteile fuehrt Yahoo nur fuer US-Aktien.
    val INDIZES = setOf("DJIA", "NDX")

    // Gezielt gesuchte Werte, juengster zuerst, bis zum Programmende
    val gezielt = mutableStateListOf<String>()

    // Namen gesuchter Werte, die der Katalog nicht kennt
    val namen = HashMap<String, String>()

    fun name(symbol: String): String = Symbols.find(symbol)?.name?.ifBlank { null } ?: namen[symbol] ?: symbol
}

// Hoch- und Abstufungen je Unternehmen gebuendelt (0.78): eine Zeile je Unternehmen, aufgeklappt die einzelnen Stufen.
// Werte: gesuchte, Depot, Watchlist und die grossen US-Werte der Indexlisten, zusammen hoechstens
// AnalystenStufen.OBERGRENZE. Die Abrufe laufen nacheinander mit Pause; der Zeitraum filtert nur, was der Abruf je
// Wert ohnehin liefert.
@Composable
internal fun StufenSeite(state: AppState, onOpen: (String) -> Unit) {
    val konto by state.account.collectAsState()
    val depot = remember(konto.positions) {
        konto.positions.filter { !it.isDerivative }.map { it.symbol }.distinct().filter { Symbols.typeOf(it) == AssetType.STOCK }
    }
    val watchlist = remember(konto.watchlist) { konto.watchlist.filter { Symbols.typeOf(it) == AssetType.STOCK } }
    val grosse by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching { Catalog.get().aktien.filter { a -> a.x.any { it in StufenUrteile.INDIZES } }.map { it.s } }.getOrDefault(emptyList())
        }
    }
    val gezielt = StufenUrteile.gezielt.toList()
    val symbole = remember(depot, watchlist, grosse, gezielt) {
        grosse?.let { AnalystenStufen.auswahl(gezielt, depot, watchlist, it) }
    }
    val jetzt = remember { System.currentTimeMillis() }
    val geladen = remember { HashMap<String, List<Analystenbilanz.Urteil>>() }
    var stand by remember { mutableIntStateOf(0) }
    var fertig by remember { mutableIntStateOf(0) }
    var abgebrochen by remember { mutableStateOf(false) }
    LaunchedEffect(symbole) {
        val liste = symbole ?: return@LaunchedEffect
        fertig = 0
        abgebrochen = false
        geladen.keys.retainAll(liste.toSet())
        stand++
        val kurz = (gezielt + depot + watchlist).toSet()
        var fehler = 0
        for (s in liste) {
            val alt = StufenUrteile.karte[s]
            val frist = if (s in kurz) StufenUrteile.FRIST_MS else StufenUrteile.FRIST_GROSS_MS
            var urteile = alt?.takeIf { System.currentTimeMillis() - it.first < frist }?.second
            if (urteile == null) {
                delay(StufenUrteile.PAUSE_MS)
                val neu = try { state.market.analysten(s)?.urteile } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                if (neu != null) {
                    StufenUrteile.karte[s] = System.currentTimeMillis() to neu
                    fehler = 0
                } else fehler++
                // Ohne Antwort bleibt der aeltere Stand stehen
                urteile = neu ?: alt?.second
            }
            if (urteile != null) {
                geladen[s] = urteile
                stand++
            }
            if (fehler >= AnalystenStufen.FEHLER_MAX) { abgebrochen = true; break }
            fertig++
        }
    }

    var tage by rememberSaveable { mutableIntStateOf(AnalystenStufen.ZEITRAUM_VORGABE) }
    var kreisName by rememberSaveable { mutableStateOf(AnalystenStufen.Kreis.ALLE.name) }
    val kreis = AnalystenStufen.Kreis.valueOf(kreisName)
    var offen by rememberSaveable { mutableStateOf(setOf<String>()) }
    val gruppen = remember(stand, tage, kreis, depot, watchlist, gezielt) {
        AnalystenStufen.buendeln(HashMap(geladen), jetzt, tage, AnalystenStufen.erlaubt(kreis, depot, watchlist), gezielt)
    }
    val zaehler = remember(gruppen) { AnalystenStufen.zaehler(gruppen) }

    // Suche: Treffer aus dem Katalog sofort, die von Yahoo nach kurzer Wartezeit dazu
    var suche by rememberSaveable { mutableStateOf("") }
    var online by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }
    LaunchedEffect(suche) {
        val q = suche.trim()
        online = emptyList()
        if (q.length < 2) return@LaunchedEffect
        delay(300)
        online = try { state.market.yahooSuche(q) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }
    val treffer = remember(suche, online) {
        val q = suche.trim()
        if (q.length < 2) emptyList()
        else (Symbols.search(q).filter { it.type == AssetType.STOCK }.take(6).map { Triple(it.symbol, it.name.ifBlank { it.symbol }, it.symbol) } +
            online.filter { it.type == "EQUITY" }.map { h ->
                Triple(h.symbol, h.name.ifBlank { h.symbol }, listOf(h.symbol, h.exchange).filter { it.isNotBlank() }.joinToString(" · "))
            }).distinctBy { it.first }.take(10)
    }

    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val gesamt = symbole?.size ?: 0
    val laedt = symbole == null || (!abgebrochen && fertig < gesamt)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "kopf", contentType = "kopf") {
            Column {
                Text(S.ptTitle(PortfolioTool.STUFEN.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
                Text(S.ptSubtitle(PortfolioTool.STUFEN.name), style = MaterialTheme.typography.bodyMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
                ChipReihe {
                    AnalystenStufen.ZEITRAEUME.forEach { t ->
                        Chip(if (t == 365) S.su("jahr") else S.su("tage", listOf(t.toString())), active = t == tage) { tage = t }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ChipReihe {
                    AnalystenStufen.Kreis.entries.forEach { k ->
                        Chip(S.su(k.name.lowercase()), active = k == kreis) { kreisName = k.name }
                    }
                }
                OutlinedTextField(
                    value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, placeholder = { Text(S.su("suche")) },
                    shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(treffer, key = { "t-" + it.first }, contentType = { "treffer" }) { (symbol, name, unter) ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).zeilenKlick(role = Role.Button) {
                    StufenUrteile.namen[symbol] = name
                    StufenUrteile.gezielt.remove(symbol)
                    StufenUrteile.gezielt.add(0, symbol)
                    while (StufenUrteile.gezielt.size > AnalystenStufen.GEZIELT_MAX) StufenUrteile.gezielt.removeAt(StufenUrteile.gezielt.size - 1)
                    kreisName = AnalystenStufen.Kreis.ALLE.name
                    suche = ""
                }.heightIn(min = 48.dp).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(unter, style = MaterialTheme.typography.bodySmall, color = grau)
            }
        }
        item(key = "stand", contentType = "stand") {
            Column {
                if (zaehler.first + zaehler.second > 0) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChangeValue(1.0, Fmt.num(zaehler.first.toDouble(), 0), style = MaterialTheme.typography.titleSmall, nullAusText = false)
                        ChangeValue(-1.0, Fmt.num(zaehler.second.toDouble(), 0), style = MaterialTheme.typography.titleSmall, nullAusText = false)
                    }
                }
                val zeile = when {
                    abgebrochen -> S.su("abbruch", listOf(fertig.toString(), gesamt.toString()))
                    symbole == null -> S.slLaedt
                    laedt -> S.su("laedt", listOf(fertig.toString(), gesamt.toString()))
                    gruppen.isEmpty() -> S.su("leer")
                    else -> null
                }
                zeile?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            }
        }
        gruppen.forEachIndexed { i, g ->
            val auf = g.symbol in offen && g.eintraege.isNotEmpty()
            item(key = "g-" + g.symbol, contentType = "gruppe") {
                Column {
                    if (i > 0) Trennlinie()
                    GruppenZeile(g, StufenUrteile.name(g.symbol), auf) {
                        if (g.eintraege.isEmpty()) onOpen(g.symbol)
                        else offen = if (g.symbol in offen) offen - g.symbol else offen + g.symbol
                    }
                }
            }
            if (auf) itemsIndexed(g.eintraege, key = { n, _ -> "e-" + g.symbol + "-" + n }, contentType = { _, _ -> "stufe" }) { _, e ->
                StufenZeile(e) { onOpen(e.symbol) }
            }
        }
    }
}

@Composable
private fun ChipReihe(inhalt: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { inhalt() }
}

private fun kursziel(v: Double) = Fmt.price(v, "USD")

// Zeile eines Unternehmens: Name, Zahl der Hoch- und Abstufungen, darunter juengstes Datum und Kursziel.
@Composable
private fun GruppenZeile(g: AnalystenStufen.Gruppe, name: String, offen: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val leer = g.eintraege.isEmpty()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .zeilenKlick(role = Role.Button, onClickLabel = if (leer) null else if (offen) S.less else S.more, onClick = onClick)
            .heightIn(min = 48.dp).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (g.hoch > 0) {
                Spacer(Modifier.width(12.dp))
                ChangeValue(1.0, Fmt.num(g.hoch.toDouble(), 0), style = MaterialTheme.typography.titleSmall, nullAusText = false)
            }
            if (g.runter > 0) {
                Spacer(Modifier.width(12.dp))
                ChangeValue(-1.0, Fmt.num(g.runter.toDouble(), 0), style = MaterialTheme.typography.titleSmall, nullAusText = false)
            }
            if (!leer) {
                Spacer(Modifier.width(10.dp))
                AufklappPfeil(offen)
            }
        }
        if (leer) {
            Text(S.su("ohne"), style = MaterialTheme.typography.bodySmall, color = grau)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.date(g.juengste), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
                Spacer(Modifier.weight(1f))
                if (g.zielZahl > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (g.zielZahl > 1) S.su("zielMittel", listOf(kursziel(g.zielMittel))) else S.anKursziel(kursziel(g.zielMittel)),
                        style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

// Eine Stufe im aufgeklappten Bereich: Haus und Datum, alte und neue Stufe mit Dreieck, Kursziel in eigener Zeile.
// Ein Tipp oeffnet die Wertpapierseite.
@Composable
private fun StufenZeile(e: AnalystenStufen.Eintrag, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun note(g: String) = Analystenbilanz.stufe(g)?.let { S.anStufe(it) } ?: g.ifBlank { "–" }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).zeilenKlick(onClick = onClick).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(e.haus, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(Fmt.date(e.zeit), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
        val stufen = if (e.von.isBlank() || e.aktion == "init") S.stErst + ": " + note(e.nach) else note(e.von) + " → " + note(e.nach)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionTriangle(e.richtung.toDouble(), width = 9.dp)
            Spacer(Modifier.width(6.dp))
            Text(stufen, style = MaterialTheme.typography.bodyMedium)
        }
        if (e.ziel > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.su("ziel"), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (e.zielVorher > 0 && e.zielVorher != e.ziel) kursziel(e.zielVorher) + " → " + kursziel(e.ziel) else kursziel(e.ziel),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}
