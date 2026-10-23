package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Wette
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.plattform.LogoBild
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

// Routen der Prediction Markets im Seitenstapel.
object PmRouten {
    const val START = "pm"
    fun ereignis(q: Prognose.Quelle, id: String): String = "pm-e/${q.name}/" + URLEncoder.encode(id, "UTF-8")
    fun teile(route: String): Pair<Prognose.Quelle, String> {
        val rest = route.removePrefix("pm-e/")
        val q = runCatching { Prognose.Quelle.valueOf(rest.substringBefore('/')) }.getOrDefault(Prognose.Quelle.POLYMARKET)
        return q to URLDecoder.decode(rest.substringAfter('/'), "UTF-8")
    }
}

// Darstellung

// Wahrscheinlichkeit 0 bis 1 als Prozent; unter 1 % als „< 1 %“.
fun pmProzent(p: Double): String = when {
    p < 0.005 -> "< 1" + if (Fmt.decimalSep == ',') " %" else "%"
    p > 0.995 -> "> 99" + if (Fmt.decimalSep == ',') " %" else "%"
    else -> Fmt.pct(p * 100, 0)
}

// Name einer Seite in der Sprache der App (Yes/No uebersetzt, Mannschaften wie geliefert).
fun pmSeite(name: String): String = when (name.lowercase()) {
    "yes" -> S.pmYes
    "no" -> S.pmNo
    else -> pmText(name)
}

// Uebersetzungen der Marktinhalte. Polymarket und Kalshi liefern Titel und Ausgaenge nur auf Englisch;
// mit einem eigenen DeepL-Schluessel fuellt die App diese Tabelle (siehe AppState.pmUebersetzen), sonst greift nur
// das kleine Woerterbuch in pmText.
object PmTexte {
    val karte = androidx.compose.runtime.mutableStateMapOf<String, String>()
}

private val UNENTSCHIEDEN = Regex("^(Tie|Draw)(\\s*\\(.*\\))?$", RegexOption.IGNORE_CASE)

// Text eines Markts in der Sprache der App: auf Deutsch die DeepL-Uebersetzung, sonst feste Begriffe aus dem
// Woerterbuch („Tie“, „Draw“, „Other“, „More Markets“); auf Englisch unveraendert.
fun pmText(s: String): String {
    if (de.troetenrepublik.app.i18n.I18n.lang != Lang.DE || s.isBlank()) return s
    PmTexte.karte[s]?.let { return it }
    UNENTSCHIEDEN.matchEntire(s.trim())?.let { m -> return "Unentschieden" + m.groupValues[2] }
    return when (s.trim().lowercase()) {
        "other", "others" -> "Andere"
        "yes" -> S.pmYes
        "no" -> S.pmNo
        else -> s.replace(Regex("\\s+-\\s+More Markets$"), " – weitere Märkte").replace(Regex("\\bWinner\\b"), "Sieger")
    }
}

// Feste Begriffe, die das Woerterbuch selbst uebersetzt (kosten bei DeepL keine Zeichen).
fun pmFesterBegriff(s: String): Boolean =
    UNENTSCHIEDEN.matchEntire(s.trim()) != null || s.trim().lowercase() in setOf("yes", "no", "other", "others")

// Kategorie eines Ereignisses in der Sprache der App, soweit sie in den Kategorien der Boerse vorkommt.
private fun pmKategorie(e: Prognose.Ereignis): String {
    val liste = if (e.quelle == Prognose.Quelle.POLYMARKET) Prognose.POLY_KATEGORIEN else Prognose.KALSHI_KATEGORIEN
    val k = liste.firstOrNull { it.schluessel.isNotBlank() && (it.schluessel.equals(e.kategorie, true) || it.en.equals(e.kategorie, true)) }
    return k?.let { kategorieName(it) } ?: e.kategorie
}

private fun kategorieName(k: Prognose.Kategorie): String = if (de.troetenrepublik.app.i18n.I18n.lang == Lang.DE) k.de else k.en

private enum class PmReiter { POLYMARKET, KALSHI, GROSS, WETTEN }

// Prediction Markets: Reiter Polymarket, Kalshi und Meine Wetten. Je Boerse Kategorien als Chips, eine
// Suche und die Liste der Ereignisse mit Wahrscheinlichkeit, Veraenderung und Umsatz. Ein Tipp oeffnet das Ereignis
// mit Verlauf, Ausgaengen und dem Blatt zum Wetten mit Uebungsgeld.
@Composable
fun PrognoseScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reiter = PmReiter.entries
    var gewaehlt by rememberSaveable { mutableStateOf(PmReiter.POLYMARKET.name) }
    val pager = rememberPagerState(initialPage = reiter.indexOfFirst { it.name == gewaehlt }.coerceAtLeast(0)) { reiter.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { i -> reiter.getOrNull(i)?.let { gewaehlt = it.name } } }
    val listen = remember { List(reiter.size) { LazyListState() } }
    val dismiss = rememberSwipeDownDismiss(onBack)
    LaunchedEffect(Unit) { runCatching { state.wettenPruefen() } }
    val offen = account.wetten.count { it.offen }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.pmTitle, null, onBack)
        MitlaufendeReiter(
            anzahl = reiter.size,
            pager = pager,
            titel = { i ->
                when (reiter[i]) {
                    PmReiter.POLYMARKET -> "Polymarket"
                    PmReiter.KALSHI -> "Kalshi"
                    PmReiter.GROSS -> S.pmGross
                    PmReiter.WETTEN -> S.pmMyBets
                }
            },
            modifier = Modifier.padding(vertical = 12.dp),
            zusatz = { i -> if (reiter[i] == PmReiter.WETTEN && offen > 0) offen.toString() else null },
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1) { seite ->
            when (reiter[seite]) {
                PmReiter.POLYMARKET -> BoersenListe(state, Prognose.Quelle.POLYMARKET, listen[seite], oeffnen)
                PmReiter.KALSHI -> BoersenListe(state, Prognose.Quelle.KALSHI, listen[seite], oeffnen)
                PmReiter.GROSS -> GrosseWettenListe(state, listen[seite], oeffnen)
                PmReiter.WETTEN -> WettenListe(state, listen[seite], oeffnen)
            }
        }
    }
}

// Groesste Wetten bei Polymarket im gewaehlten Zeitraum, nach Einsatz, auf Wunsch nur ein Thema.
@Composable
private fun GrosseWettenListe(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit) {
    var zeit by rememberSaveable { mutableStateOf(Prognose.WettZeit.TAG) }
    var thema by rememberSaveable { mutableStateOf("") }
    var wetten by remember(zeit) { mutableStateOf<List<Prognose.GrosseWette>?>(null) }
    var fehler by remember(zeit) { mutableStateOf(false) }
    LaunchedEffect(zeit) {
        wetten = runCatching { state.prognose.grosseWetten(zeit) }.getOrNull()
        fehler = wetten == null
        wetten?.let { l -> runCatching { state.pmUebersetzen(l.map { it.titel } + l.map { it.ausgang }) } }
    }
    val l = wetten
    // Nur Themen, zu denen es im Zeitraum Wetten gibt
    val themen = remember(l) { l.orEmpty().map { it.thema }.toSet().let { da -> Prognose.THEMEN.filter { it.schluessel in da } } }
    val gezeigt = remember(l, thema) { if (thema.isBlank()) l else l?.filter { it.thema == thema } }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "zeit") {
            ZeitLeiste(Prognose.WettZeit.entries.map { S.pmGrossZeit(it.name) }, selected = zeit.ordinal,
                onSelect = { zeit = Prognose.WettZeit.entries[it] }, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (themen.size > 1) {
            item(key = "themen") {
                Row(
                    Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(S.pmGrossAlle, active = thema.isBlank()) { thema = "" }
                    themen.forEach { k -> Chip(kategorieName(k), active = k.schluessel == thema) { thema = k.schluessel } }
                }
            }
        }
        when {
            l == null && !fehler -> item { Lade(S.pmLoading) }
            gezeigt.isNullOrEmpty() -> item { Hinweis(S.pmGrossKeine) }
            else -> items(gezeigt.size, key = { "g-$it" }) { i ->
                GrosseWetteZeile(gezeigt[i]) { if (gezeigt[i].ereignis.isNotBlank()) oeffnen(PmRouten.ereignis(Prognose.Quelle.POLYMARKET, gezeigt[i].ereignis)) }
            }
        }
        item { Hinweis(S.pmGrossText) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Eine echte Position: Markt, Seite, Anteile zum Einstand, heutiger Wert und Ergebnis in Dollar.
@Composable
private fun EchteZeile(p: Prognose.EchtePosition, onClick: () -> Unit) {
    val signal = LocalSignal.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pmText(p.titel), style = MaterialTheme.typography.titleSmall)
            Text(
                S.pmEchtZeile(pmText(p.ausgang), Fmt.num(p.anteile, 0), pmProzent(p.einstand), pmProzent(p.kurs)) + if (p.einloesbar) " · " + S.pmEchtEinloesbar else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.dollarKompakt(p.wert), style = MaterialTheme.typography.titleSmall)
            Text((if (p.gewinn >= 0) "+" else "−") + Fmt.dollarKompakt(kotlin.math.abs(p.gewinn)), style = MaterialTheme.typography.bodySmall, color = signal.forChange(p.gewinn))
        }
    }
}

@Composable
private fun GrosseWetteZeile(w: Prognose.GrosseWette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pmText(w.titel), style = MaterialTheme.typography.titleSmall)
            Text(
                S.pmGrossZeile(pmText(w.ausgang), w.kauf, pmProzent(w.preis), Fmt.relative(w.zeit)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(Fmt.dollarKompakt(w.betrag), style = MaterialTheme.typography.titleSmall)
    }
}

// Liste einer Boerse: Suche, Kategorien, Ereignisse; am Ende „Mehr laden“.
@Composable
private fun BoersenListe(state: AppState, quelle: Prognose.Quelle, liste: LazyListState, oeffnen: (String) -> Unit) {
    val kategorien = if (quelle == Prognose.Quelle.POLYMARKET) Prognose.POLY_KATEGORIEN else Prognose.KALSHI_KATEGORIEN
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    var kategorie by rememberSaveable(quelle) { mutableStateOf(kategorien.first().schluessel) }
    var suche by rememberSaveable(quelle) { mutableStateOf("") }
    var ereignisse by remember(quelle, kategorie) { mutableStateOf<List<Prognose.Ereignis>?>(null) }
    var weiter by remember(quelle, kategorie) { mutableStateOf<String?>(null) }
    var fehler by remember(quelle, kategorie) { mutableStateOf(false) }
    var laedt by remember(quelle, kategorie) { mutableStateOf(false) }
    var treffer by remember(quelle) { mutableStateOf<List<Prognose.Ereignis>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(quelle, kategorie) {
        laedt = true
        val s = runCatching { state.prognose.liste(quelle, kategorie) }.getOrNull()
        ereignisse = s?.ereignisse ?: emptyList()
        weiter = s?.weiter
        fehler = s == null
        laedt = false
    }
    LaunchedEffect(quelle, suche) {
        val q = suche.trim()
        if (q.length < 2) { treffer = null; return@LaunchedEffect }
        delay(400)
        treffer = runCatching { state.prognose.suche(quelle, q) }.getOrNull() ?: emptyList()
    }
    val gezeigt = treffer ?: ereignisse
    // Marktinhalte ins Deutsche (mit DeepL-Schluessel); bekannte Uebersetzungen kosten nichts
    LaunchedEffect(gezeigt) {
        gezeigt?.let { l -> state.pmUebersetzen(l.flatMap { e -> listOf(e.titel) + e.maerkte.take(3).map { it.titel } }) }
    }

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "suche") {
            OutlinedTextField(
                value = suche,
                onValueChange = { suche = it.take(60) },
                placeholder = { Text(S.pmSearchHint) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (treffer == null) {
            item(key = "kategorien") {
                Row(
                    Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    kategorien.forEach { k -> Chip(kategorieName(k), active = k.schluessel == kategorie) { kategorie = k.schluessel } }
                }
            }
        }
        when {
            gezeigt == null -> item { Lade(S.pmLoading) }
            gezeigt.isEmpty() -> item { EmptyHint(if (fehler) S.pmUnreachable else S.pmNoResults) }
            else -> items(gezeigt.distinctBy { it.quelle to it.id }, key = { "e-${it.quelle}-${it.id}" }) { e -> EreignisZeile(e) { oeffnen(PmRouten.ereignis(e.quelle, e.id)) } }
        }
        if (treffer == null && weiter != null && !gezeigt.isNullOrEmpty()) {
            item(key = "mehr") {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    if (laedt) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else FloatingPill(S.pmMore, filled = false) {
                        scope.launch {
                            laedt = true
                            val s = runCatching { state.prognose.liste(quelle, kategorie, weiter) }.getOrNull()
                            if (s != null) {
                                val schon = ereignisse.orEmpty().map { it.id }.toHashSet()
                                ereignisse = ereignisse.orEmpty() + s.ereignisse.filter { it.id !in schon }
                                weiter = s.weiter
                            }
                            laedt = false
                        }
                    }
                }
            }
        }
        // Auf Deutsch ohne DeepL-Schluessel: woher das Englisch kommt und wie man uebersetzen laesst
        if (de.troetenrepublik.app.i18n.I18n.lang == Lang.DE && einstellungen.keyDeepl.isBlank()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(S.pmTranslateHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.newsKeyEnter, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.clickable { oeffnen("zugaenge") }.padding(vertical = 6.dp))
                }
            }
        }
        item { Hinweis(S.pmDataNote(quelle.anzeige)) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun Lade(text: String) {
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Hinweis(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

// Bild des Ereignisses (Polymarket) oder der Anfangsbuchstabe der Boerse.
@Composable
private fun EreignisBild(e: Prognose.Ereignis, groesse: androidx.compose.ui.unit.Dp = 40.dp) {
    var fehler by remember(e.bild) { mutableStateOf(e.bild.isBlank()) }
    Box(
        Modifier.size(groesse).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!fehler) LogoBild(e.bild, null, Modifier.size(groesse), ContentScale.Crop, onSuccess = {}, onError = { fehler = true })
        // ohne Bild (Kalshi liefert in der Suche keine): Anfangsbuchstabe des Ereignisses
        else Text(e.titel.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: e.quelle.anzeige.take(1),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Eine Zeile der Liste: Bild, Titel, die wichtigsten Ausgaenge oder Umsatz und Ende, rechts die Wahrscheinlichkeit.
@Composable
private fun EreignisZeile(e: Prognose.Ereignis, onClick: () -> Unit) {
    val signal = LocalSignal.current
    val oben = e.maerkte.first()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EreignisBild(e)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pmText(e.titel), style = MaterialTheme.typography.titleSmall)
            val zweite = if (e.binaer) {
                listOfNotNull(
                    e.volumen.takeIf { it > 0 }?.let { S.pmVolume(Fmt.dollarKompakt(it)) },
                    e.ende.takeIf { it > 0 }?.let { S.pmEnds(Fmt.date(it)) },
                ).joinToString(" · ")
            } else {
                e.maerkte.take(3).joinToString(" · ") { "${pmText(it.titel)} ${pmProzent(it.chance)}" }
            }
            Text(zweite, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(pmProzent(oben.chance), style = MaterialTheme.typography.titleMedium)
            val d = oben.aenderung
            if (d != null && kotlin.math.abs(d) >= 0.5) {
                Text(
                    (if (d > 0) "+" else "−") + Fmt.num(kotlin.math.abs(d), 0) + " " + S.pmPoints,
                    style = MaterialTheme.typography.bodySmall, color = signal.forChange(d),
                )
            }
        }
    }
}

// Meine Wetten

@Composable
private fun WettenListe(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val kurse by state.wettKurse.collectAsStateWithLifecycle()
    val offen = account.wetten.filter { it.offen }.sortedByDescending { it.eroeffnet }
    val zu = account.wetten.filter { !it.offen }.sortedByDescending { it.geschlossen }
    var verkaufen by remember { mutableStateOf<Wette?>(null) }
    // Echte Positionen bei Polymarket, wenn unter Zugaenge eine Adresse steht
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    val adresse = einstellungen.polyAdresse
    var echte by remember(adresse) { mutableStateOf<List<Prognose.EchtePosition>?>(null) }
    LaunchedEffect(adresse) {
        if (adresse.isBlank()) return@LaunchedEffect
        echte = runCatching { state.prognose.echtePositionen(adresse) }.getOrNull()
        echte?.let { l -> runCatching { state.pmUebersetzen(l.map { it.titel } + l.map { it.ausgang }) } }
    }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        if (adresse.isNotBlank()) {
            item(key = "echt-kopf") { SectionHeader(S.pmEchtTitel) }
            val l = echte
            when {
                l == null -> item(key = "echt-lade") { Lade(S.pmLoading) }
                l.isEmpty() -> item(key = "echt-leer") { Hinweis(S.pmEchtKeine) }
                else -> {
                    item(key = "echt-summe") {
                        val signal = LocalSignal.current
                        val g = l.sumOf { it.gewinn }
                        KeyValueRow(S.pmValue, Fmt.dollarKompakt(l.sumOf { it.wert }))
                        KeyValueRow(S.pmResult, (if (g >= 0) "+" else "−") + Fmt.dollarKompakt(kotlin.math.abs(g)), signal.forChange(g))
                    }
                    items(l.sortedByDescending { it.wert }, key = { "echt-${it.ereignis}-${it.ausgang}-${it.titel}" }) { p ->
                        EchteZeile(p) { if (p.ereignis.isNotBlank()) oeffnen(PmRouten.ereignis(Prognose.Quelle.POLYMARKET, p.ereignis)) }
                    }
                }
            }
            item(key = "echt-hinweis") { Hinweis(S.pmEchtHinweis) }
            item(key = "echt-uebung") { SectionHeader(S.pmUebungTitel) }
        }
        if (account.wetten.isEmpty()) {
            item { EmptyHint(S.pmNoBets) }
        } else {
            item {
                val u = de.troetenrepublik.app.engine.Wetten.uebersicht(account.wetten, kurse)
                val signal = LocalSignal.current
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
                ) {
                    KeyValueRow(S.pmOpenBets, u.offen.toString())
                    KeyValueRow(S.pmStake, Fmt.euro(u.einsatz))
                    KeyValueRow(S.pmValue, Fmt.euro(u.wert))
                    if (u.abgeschlossen == 0) {
                        KeyValueRow(S.pmResult, Fmt.signedEuro(u.ergebnisOffen), signal.forChange(u.ergebnisOffen))
                    } else {
                        // Mit abgeschlossenen Wetten: beide Teile und die Summe (wie am Ende des Verlaufs)
                        KeyValueRow(S.pmResultOpen, Fmt.signedEuro(u.ergebnisOffen), signal.forChange(u.ergebnisOffen))
                        KeyValueRow(S.pmResultClosed, Fmt.signedEuro(u.ergebnisAbgeschlossen), signal.forChange(u.ergebnisAbgeschlossen),
                            hint = S.pmClosedCount(u.abgeschlossen))
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                        KeyValueRow(S.pmResultTotal, Fmt.signedEuro(u.gesamt), signal.forChange(u.gesamt))
                    }
                }
            }
            item(key = "verlauf") { WettVerlauf(state) }
            if (offen.isNotEmpty()) item { SectionHeader(S.pmOpen) }
            items(offen, key = { "w-${it.id}" }) { w ->
                WettZeile(w, kurse[w.id] ?: w.kurs, onVerkaufen = { verkaufen = w }) {
                    oeffnen(PmRouten.ereignis(Prognose.Quelle.valueOf(w.quelle), w.ereignis))
                }
            }
            if (zu.isNotEmpty()) item { SectionHeader(S.pmClosed) }
            items(zu, key = { "z-${it.id}" }) { w ->
                WettZeile(w, w.kurs, onVerkaufen = null) { oeffnen(PmRouten.ereignis(Prognose.Quelle.valueOf(w.quelle), w.ereignis)) }
            }
        }
        item { Hinweis(S.pmPracticeNote) }
        item { Spacer(Modifier.height(40.dp)) }
    }
    verkaufen?.let { w -> VerkaufenDialog(state, w) { verkaufen = null } }
}

// Zeitraeume des Wettverlaufs; 0 = alles.
private enum class WettZeitraum(val tage: Int) { W1(7), M1(30), MAX(0) }

// Portfoliograph der Wetten: Ergebnis aller Wetten in Euro ueber die Zeit, mit Marken fuer Kaeufe und
// Abschluesse. Die Kursverlaeufe der einzelnen Wetten laedt die Karte selbst (zehn Minuten zwischengespeichert).
@Composable
private fun WettVerlauf(state: AppState) {
    val account by state.account.collectAsStateWithLifecycle()
    val kurse by state.wettKurse.collectAsStateWithLifecycle()
    val wetten = account.wetten
    var verlaeufe by remember { mutableStateOf<Map<String, List<Pair<Long, Double>>>?>(null) }
    LaunchedEffect(wetten.map { it.id to it.status }) {
        verlaeufe = kotlinx.coroutines.coroutineScope {
            wetten.map { w -> async { w.id to runCatching { state.prognose.wettVerlauf(w) }.getOrDefault(emptyList()) } }.awaitAll()
        }.toMap()
    }
    var zeitraum by rememberSaveable { mutableStateOf(WettZeitraum.MAX) }
    val v = verlaeufe
    val jetzt = remember(account.transactions.size, v) { System.currentTimeMillis() }
    val aktuell = remember(kurse, wetten) { wetten.filter { it.offen }.associate { it.id to (kurse[it.id] ?: it.kurs) } }
    val punkte = remember(account.transactions, v, aktuell) {
        if (v == null) emptyList() else de.troetenrepublik.app.engine.Wetten.verlauf(account.transactions, v, aktuell, jetzt)
    }
    val buchungen = remember(account.transactions) { de.troetenrepublik.app.engine.Wetten.buchungen(account.transactions) }
    if (wetten.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        SectionHeader(S.pmHistory)
        if (v == null) {
            Text(S.pmHistoryLoading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            return@Column
        }
        val start = if (zeitraum.tage == 0) 0L else jetzt - zeitraum.tage * 86_400_000L
        val sichtbar = punkte.filter { it.first >= start }.let { l ->
            // ein Punkt am Anfang des Zeitraums, damit die Linie dort beginnt
            val davor = punkte.lastOrNull { it.first < start }
            if (davor != null && start > 0) listOf(start to davor.second) + l else l
        }
        val marken = buchungen.filter { it.t >= start }.map { b ->
            val datum = Fmt.date(b.t)
            Marke(b.t, S.pmHistoryMark(b.tag, datum, Fmt.euro(kotlin.math.abs(b.betrag)), pmSeite(b.seite), b.titel), b.kauf)
        }
        ZeitLeiste(WettZeitraum.entries.map { S.pmHistoryRange(it.tage) }, selected = zeitraum.ordinal,
            onSelect = { zeitraum = WettZeitraum.entries[it] }, modifier = Modifier.padding(horizontal = 12.dp))
        if (sichtbar.size < 2) {
            Text(S.pmHistoryNone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        } else {
            VergleichsChart(
                linien = listOf(Linie(S.pmResult, sichtbar, MaterialTheme.colorScheme.onSurface)),
                format = { Fmt.signedEuro(it) },
                modifier = Modifier.padding(top = 6.dp),
                hoehe = 190.dp,
                marken = marken,
                // bis zwei Tage mit Uhrzeit, sonst das Datum
                zeit = { if (sichtbar.last().first - sichtbar.first().first < 2 * 86_400_000L) Fmt.dayMonth(it) + " " + Fmt.time(it) else Fmt.date(it) },
                nullLinie = 0.0,
                markenHinweis = S.pmHistoryHint,
            )
        }
        Text(S.pmHistoryNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }
}

@Composable
private fun WettZeile(w: Wette, kurs: Double, onVerkaufen: (() -> Unit)?, onClick: () -> Unit) {
    val signal = LocalSignal.current
    val wert = if (w.offen) w.anteile * kurs else w.erloes
    val ergebnis = wert - w.einsatz
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pmText(w.titel), style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(
                    if (w.frage.isNotBlank() && w.frage != w.titel) "${pmText(w.frage)}: ${pmSeite(w.seitenName)}" else pmSeite(w.seitenName),
                    if (w.offen) S.pmSharesAt(Fmt.qty(Math.round(w.anteile * 100) / 100.0), pmProzent(w.einstand), pmProzent(kurs)) else S.pmStatus(w.status),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.euro(wert), style = MaterialTheme.typography.titleSmall)
            Text(Fmt.signedEuro(ergebnis), style = MaterialTheme.typography.bodySmall, color = signal.forChange(ergebnis))
            if (onVerkaufen != null) {
                Text(
                    S.pmSell, style = MaterialTheme.typography.labelMedium, color = signal.link,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).clickable(onClick = onVerkaufen).padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun VerkaufenDialog(state: AppState, w: Wette, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val kurse by state.wettKurse.collectAsStateWithLifecycle()
    var laeuft by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.pmSellTitle) },
        text = { Text(S.pmSellText(Fmt.euro(w.anteile * (kurse[w.id] ?: w.kurs)))) },
        confirmButton = {
            TextButton(enabled = !laeuft, onClick = {
                laeuft = true
                scope.launch { state.wetteVerkaufen(w.id); laeuft = false; onDismiss() }
            }) { Text(S.pmSell) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}

// Ereignis

// Ein Ereignis: Kopf mit Bild, Kategorie, Ende und Umsatz; Verlauf der Wahrscheinlichkeiten (bis zu vier Ausgaenge);
// die Ausgaenge mit Ja- und Nein-Knopf; eigene Wetten auf dieses Ereignis; Beschreibung bzw. Regeln und der Link zur
// Boerse. Die Kurse laden alle 30 Sekunden neu.
@Composable
fun PrognoseEreignisScreen(state: AppState, quelle: Prognose.Quelle, id: String, onBack: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val kurse by state.wettKurse.collectAsStateWithLifecycle()
    var ereignis by remember { mutableStateOf<Prognose.Ereignis?>(null) }
    var fehler by remember { mutableStateOf(false) }
    var bereich by rememberSaveable { mutableStateOf(Prognose.Bereich.MONAT) }
    val verlaeufe = remember { mutableStateMapOf<String, List<Pair<Long, Double>>>() }
    var wettenAuf by remember { mutableStateOf<Pair<Prognose.Markt, Int>?>(null) }
    var verkaufen by remember { mutableStateOf<Wette?>(null) }
    var regelnOffen by remember { mutableStateOf(false) }
    var grosseWetten by remember { mutableStateOf<List<Prognose.GrosseWette>?>(null) }
    val uri = LocalUriHandler.current
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)

    LaunchedEffect(quelle, id) {
        while (true) {
            val e = runCatching { state.prognose.ereignis(quelle, id, neu = ereignis != null) }.getOrNull()
            if (e != null) ereignis = e else if (ereignis == null) fehler = true
            delay(30_000L)
        }
    }
    val e = ereignis
    val gezeigt = e?.maerkte?.filter { it.offen }?.take(4).orEmpty()
    LaunchedEffect(e?.nummer) {
        val n = e?.nummer?.takeIf { quelle == Prognose.Quelle.POLYMARKET && it.isNotBlank() } ?: return@LaunchedEffect
        grosseWetten = runCatching { state.prognose.grosseWetten(n) }.getOrNull()
    }
    LaunchedEffect(e?.id, e?.maerkte?.size) {
        e?.let { x -> state.pmUebersetzen(listOf(x.titel, x.untertitel) + x.maerkte.map { it.titel }) }
    }
    LaunchedEffect(e?.id, bereich, gezeigt.map { it.id }) {
        gezeigt.forEach { m ->
            val v = runCatching { state.prognose.verlauf(m, 0, bereich) }.getOrDefault(emptyList())
            verlaeufe["${m.id}|$bereich"] = v
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { liste.canScrollBackward != true },
    ) {
        ScreenHeader(e?.titel?.let { pmText(it) } ?: "", null, onBack)
        when {
            e == null && fehler -> EmptyHint(S.pmUnreachable)
            e == null -> Lade(S.pmLoading)
            else -> LazyColumn(Modifier.fillMaxSize(), state = liste) {
                item {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        EreignisBild(e, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            listOfNotNull(
                                e.quelle.anzeige,
                                pmKategorie(e).takeIf { it.isNotBlank() },
                                e.ende.takeIf { it > 0 }?.let { S.pmEnds(Fmt.date(it)) },
                                e.volumen.takeIf { it > 0 }?.let { S.pmVolume(Fmt.dollarKompakt(it)) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e.untertitel.isNotBlank()) {
                        Text(pmText(e.untertitel), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                }
                // Verlauf
                item {
                    val linien = gezeigt.mapIndexedNotNull { i, m ->
                        val v = verlaeufe["${m.id}|$bereich"].orEmpty()
                        if (v.size < 2) null else Linie(if (e.binaer) pmSeite(m.seiten[0].name) else pmText(m.titel), v, if (i == 0) LocalSignal.current.line else VERGLEICHS_FARBEN[(i - 1) % VERGLEICHS_FARBEN.size])
                    }
                    Spacer(Modifier.height(8.dp))
                    if (linien.isEmpty()) {
                        Text(S.pmNoHistory, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 30.dp))
                    } else {
                        VergleichsChart(
                            linien, format = { pmProzent(it) }, yMin = 0.0, yMax = 1.0, hoehe = 190.dp,
                            zeit = { if (bereich == Prognose.Bereich.TAG) Fmt.time(it) else Fmt.dayMonth(it) },
                        )
                    }
                    ZeitLeiste(Prognose.Bereich.entries.map { S.pmRange(it) }, selected = bereich.ordinal,
                        onSelect = { bereich = Prognose.Bereich.entries[it] }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                // Ausgaenge
                item { SectionHeader(if (e.binaer) S.pmBet else S.pmOutcomes) }
                items(e.maerkte.filter { it.offen }, key = { "m-${it.id}" }) { m ->
                    MarktZeile(m, e.binaer) { seite -> wettenAuf = m to seite }
                }
                val aufgeloest = e.maerkte.filter { !it.offen }
                if (aufgeloest.isNotEmpty() && !e.binaer) {
                    item { Hinweis(S.pmResolvedCount(aufgeloest.size)) }
                }
                // eigene Wetten
                val meine = account.wetten.filter { it.quelle == quelle.name && it.ereignis == id }
                if (meine.isNotEmpty()) {
                    item { SectionHeader(S.pmYourBets) }
                    items(meine, key = { "mw-${it.id}" }) { w ->
                        WettZeile(w, kurse[w.id] ?: w.kurs, onVerkaufen = if (w.offen) ({ verkaufen = w }) else null) {}
                    }
                }
                // Groesste Wetten auf dieses Ereignis (nur Polymarket)
                val gross = grosseWetten
                if (!gross.isNullOrEmpty()) {
                    item { SectionHeader(S.pmGross) }
                    items(gross.size, key = { "gw-$it" }) { i -> GrosseWetteZeile(gross[i]) {} }
                }
                // Beschreibung und Regeln
                val text = e.beschreibung.ifBlank { e.maerkte.firstOrNull()?.regeln.orEmpty() }
                if (text.isNotBlank()) {
                    item {
                        SectionHeader(S.pmRules, chevron = true, onClick = { regelnOffen = !regelnOffen })
                        Text(
                            text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (regelnOffen) Int.MAX_VALUE else 4, overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(horizontal = 20.dp).clickable { regelnOffen = !regelnOffen },
                        )
                    }
                }
                item {
                    Text(
                        S.pmOpenAt(e.quelle.anzeige), style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp).clickable { runCatching { uri.openUri(e.link) } },
                    )
                }
                item { Hinweis(S.pmPracticeNote) }
                item { Spacer(Modifier.height(60.dp).navigationBarsPadding()) }
            }
        }
    }
    val auswahl = wettenAuf
    if (auswahl != null && e != null) WettBlatt(state, e, auswahl.first, auswahl.second) { wettenAuf = null }
    verkaufen?.let { w -> VerkaufenDialog(state, w) { verkaufen = null } }
}

// Ein Ausgang: Titel, Wahrscheinlichkeit, Veraenderung; zwei Knoepfe fuer die beiden Seiten mit ihrem Kaufkurs.
@Composable
private fun MarktZeile(m: Prognose.Markt, binaer: Boolean, onWetten: (Int) -> Unit) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (!binaer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pmText(m.titel), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(pmProzent(m.chance), style = MaterialTheme.typography.titleMedium)
            }
            val d = m.aenderung
            if (d != null && kotlin.math.abs(d) >= 0.5) {
                Text((if (d > 0) "+" else "−") + Fmt.num(kotlin.math.abs(d), 0) + " " + S.pmPoints + " " + S.pmIn24h,
                    style = MaterialTheme.typography.bodySmall, color = signal.forChange(d))
            }
            Spacer(Modifier.height(6.dp))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(pmProzent(m.chance), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(8.dp))
                Text(S.pmChanceOf(pmSeite(m.seiten[0].name)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            m.seiten.forEachIndexed { i, s ->
                FloatingPill(
                    "${pmSeite(s.name)} ${pmProzent(s.kauf)}",
                    modifier = Modifier.weight(1f),
                    filled = true,
                    accent = if (i == 0) signal.up else signal.down,
                ) { onWetten(i) }
            }
        }
    }
}

// Wette platzieren: Seite, Kaufkurs, Einsatz (Uebungsgeld), Anteile und Auszahlung bei Eintreten.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WettBlatt(state: AppState, e: Prognose.Ereignis, m: Prognose.Markt, seite: Int, onDismiss: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val s = m.seiten[seite]
    var betragText by remember { mutableStateOf("10") }
    var laeuft by remember { mutableStateOf(false) }
    val betrag = betragText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val anteile = if (s.kauf > 0) betrag / s.kauf else 0.0
    val verfuegbar = account.cash - account.reserved
    val geht = betrag >= de.troetenrepublik.app.engine.Wetten.MINDESTEINSATZ && betrag <= verfuegbar + 1e-9 && !laeuft && s.kauf in 0.001..0.999
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(pmText(e.titel), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (!e.binaer) Text(pmText(m.titel), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Text(
                S.pmSideAt(pmSeite(s.name), pmProzent(s.kauf)), style = MaterialTheme.typography.bodyMedium,
                color = if (seite == 0) LocalSignal.current.up else LocalSignal.current.down,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            OutlinedTextField(
                value = betragText,
                onValueChange = { t -> betragText = t.filter { it.isDigit() || it == ',' || it == '.' }.take(9) },
                label = { Text(S.pmAmount) }, singleLine = true, suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 25, 50, 100).forEach { b -> Chip("$b €", active = betrag == b.toDouble()) { betragText = b.toString() } }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 4.dp)) {
                KeyValueRow(S.pmSharesLabel, Fmt.num(anteile, 2))
                KeyValueRow(S.pmPayoutIf, Fmt.euro(anteile))
                KeyValueRow(S.pmProfitIf, Fmt.signedEuro(anteile - betrag), LocalSignal.current.up)
                KeyValueRow(S.pmAvailable, Fmt.euro(verfuegbar))
            }
            Text(S.pmPracticeNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(Radius.chip))
                    .background(if (geht) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = geht) {
                        laeuft = true
                        scope.launch {
                            val ok = state.wetteKaufen(e, m, seite, betrag)
                            laeuft = false
                            if (ok) onDismiss()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(S.pmPlaceBet, style = MaterialTheme.typography.titleMedium,
                    color = if (geht) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
