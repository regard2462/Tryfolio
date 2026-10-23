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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Strategien
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

// Copytrading: Geschaefte von US-Politikern, Bestaende bekannter Investoren und bewaehrte Anlagestrategien. Jede Person
// und Strategie hat eine eigene Seite mit Aufschluesselung, Portfolioverlauf und Nachbilden im Uebungsdepot.
// Die Daten kommen aus amtlichen Meldungen (siehe Kongress, Investoren). Die App zeigt nur die ausgelesenen Angaben,
// nie die Dokumente selbst.
object CopyRouten {
    const val START = "copytrading"
    fun person(schluessel: String) = "copy-k/" + URLEncoder.encode(schluessel, "UTF-8")
    fun investor(id: String) = "copy-i/$id"
    // Senat und Regierung.
    fun senator(schluessel: String) = "copy-n/" + URLEncoder.encode(schluessel, "UTF-8")
    fun regierung(id: String) = "copy-r/$id"
    fun strategie(id: String) = "copy-s/$id"
    // Strategie aus allen gemeldeten Kaeufen und Rangliste der Abgeordneten und Senatoren.
    const val KONGRESS_KAUFT = "copy-kk"
    const val RANGLISTE = "copy-rang"
    // Mitteilungen bei Copytrading.
    const val GLOCKEN = "copy-glocken"
    // Truth-Social-Beitraege des Praesidenten aus dem Archiv Trump's Truth.
    const val TRUTH = "copy-truth"
    // Wer einen Wert handelt.
    fun aktie(symbol: String) = "copy-a/" + URLEncoder.encode(symbol, "UTF-8")
    // Vergleichsseite, s mit Strategien und i mit Investoren vorausgewaehlt.
    fun vergleich(vorauswahl: String) = "copy-v/$vorauswahl"
    fun schluessel(route: String) = URLDecoder.decode(route.substringAfter('/'), "UTF-8")

    // Kuerzel fuer Yahoo: Aktienklassen wie BRK.B oder BF/B werden zu BRK-B, Boersenendungen wie NESN.SW bleiben.
    fun symbol(kuerzel: String) = kuerzel.trim().replace(Regex("^([A-Z0-9]+)[./]([A-KM-Z])$"), "$1-$2")

    // Aus der Kursliste der App oder dem Katalog der Suche.
    fun katalogName(sym: String): String? =
        Symbols.find(sym)?.name ?: Catalog.loaded()?.let { k -> k.aktien.firstOrNull { it.s == sym }?.n ?: k.etfs.firstOrNull { it.s == sym }?.n }

    // Zusaetze der Meldungen, die nur die Art des Papiers nennen, etwa Common Stock.
    private val ZUSATZ = Regex(
        "\\s*-?\\s*(Class [A-Z] )?(Common Stock|Common Shares|Ordinary Shares?|Ordinary Share|Units|Shares|American Depositary Shares.*|ADS.*|Sponsored ADR.*|Depositary Shares.*)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    // Aus dem Katalog, sonst der gemeldete Name ohne Zusatz zur Papierart und ohne Grossschrift.
    fun name(kuerzel: String?, gemeldet: String): String {
        kuerzel?.let { katalogName(symbol(it))?.let { n -> return n } }
        // Papiermeldung, deren Name sich nicht lesen liess
        if (gemeldet.isBlank()) return kuerzel ?: S.copyNameUnreadable
        val klasse = Regex("Class ([A-Z])\\b").find(gemeldet)?.groupValues?.get(1)
        val ohne = gemeldet.replace(ZUSATZ, "").trim().trimEnd('-', ',', ' ').ifBlank { gemeldet }
        val mitKlasse = if (klasse != null && !ohne.contains("Class $klasse")) "$ohne Class $klasse" else ohne
        if (mitKlasse.any { it.isLowerCase() }) return mitKlasse
        val gemeldet = mitKlasse
        return gemeldet.lowercase().split(' ').joinToString(" ") { w ->
            if (w.length <= 3 && w.uppercase() in setOf("INC", "LLC", "LTD", "PLC", "CORP", "CO", "NV", "AG", "SA", "LP", "ADR", "ETF", "CL", "A", "B", "C")) w.uppercase()
            else w.replaceFirstChar { it.titlecase() }
        }
    }
}

// POLITIK fasst Repraesentantenhaus, Senat und Regierung zusammen, VORSTAENDE zeigt die Eigengeschaefte der Fuehrung
// deutscher Emittenten (BaFin, seit 5.5).
private enum class CopyReiter { POLITIK, VORSTAENDE, INVESTOREN, STRATEGIEN, GEFOLGT }

private enum class PolitikTeil { KONGRESS, SENAT, REGIERUNG }

// Bekannte Abgeordnete nach Nachnamen. Sie stehen oben, wenn sie im Verzeichnis vorkommen.
private val BEKANNT = listOf("Pelosi", "Khanna", "Gottheimer", "McCaul", "Taylor", "Hern", "Cisneros")

// Katalog im Hintergrund laden, die Seite zeichnet sich neu, sobald die Namen da sind.
@Composable
internal fun katalogBereit(): Boolean {
    var da by remember { mutableStateOf(Catalog.loaded() != null) }
    LaunchedEffect(Unit) { if (!da) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Catalog.get() }; da = true } }
    return da
}

@Composable
fun CopytradingScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    katalogBereit()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val repo = state.copy
    val stand by (repo?.stand ?: kotlinx.coroutines.flow.MutableStateFlow(de.troetenrepublik.app.market.CopyRepository.Stand())).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val gefolgt = remember(settings.copyGefolgt) { state.gefolgt(settings) }
    LaunchedEffect(Unit) { runCatching { repo?.verzeichnis() } }

    val reiter = CopyReiter.entries.filter { it != CopyReiter.GEFOLGT || gefolgt.isNotEmpty() }
    var gewaehlt by rememberSaveable { mutableStateOf(CopyReiter.POLITIK.name) }
    val pager = rememberPagerState(initialPage = reiter.indexOfFirst { it.name == gewaehlt }.coerceAtLeast(0)) { reiter.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { i -> reiter.getOrNull(i)?.let { gewaehlt = it.name } } }
    val listen = remember(reiter.size) { List(reiter.size) { LazyListState() } }
    val dismiss = rememberSwipeDownDismiss(onBack)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.copyTitle, null, onBack)
        MitlaufendeReiter(
            anzahl = reiter.size,
            pager = pager,
            titel = { i ->
                when (reiter[i]) {
                    CopyReiter.POLITIK -> S.copyPolitics
                    CopyReiter.VORSTAENDE -> S.copyExecutives
                    CopyReiter.INVESTOREN -> S.copyInvestors
                    CopyReiter.STRATEGIEN -> S.copyStrategies
                    CopyReiter.GEFOLGT -> S.copyFollowed
                }
            },
            modifier = Modifier.padding(vertical = 12.dp),
            zusatz = { i -> if (reiter[i] == CopyReiter.GEFOLGT) gefolgt.size.toString() else null },
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1, key = { reiter.getOrNull(it)?.name ?: it }) { seite ->
            val liste = listen.getOrElse(seite) { rememberLazyListState() }
            when (reiter.getOrElse(seite) { CopyReiter.POLITIK }) {
                CopyReiter.POLITIK -> PolitikSeite(state, stand, gefolgt, liste, oeffnen)
                CopyReiter.VORSTAENDE -> VorstaendeListe(state, liste, oeffnen)
                CopyReiter.INVESTOREN -> InvestorenListe(state, gefolgt, liste, oeffnen)
                CopyReiter.STRATEGIEN -> StrategienListe(liste, oeffnen)
                CopyReiter.GEFOLGT -> GefolgtListe(state, stand, gefolgt, liste, oeffnen)
            }
        }
    }
}

// Politik

// Oben die Wahl zwischen Repraesentantenhaus, Senat und Regierung, darunter die Liste.
@Composable
private fun PolitikSeite(
    state: AppState,
    stand: de.troetenrepublik.app.market.CopyRepository.Stand,
    gefolgt: Set<String>,
    liste: LazyListState,
    oeffnen: (String) -> Unit,
) {
    var teil by rememberSaveable { mutableStateOf(PolitikTeil.KONGRESS.name) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PolitikTeil.entries.forEach { t ->
                val titel = when (t) { PolitikTeil.KONGRESS -> S.copyCongress; PolitikTeil.SENAT -> S.copySenate; PolitikTeil.REGIERUNG -> S.copyGovernment }
                Chip(titel, active = teil == t.name) { teil = t.name; scope.launch { liste.scrollToItem(0) } }
            }
        }
        Box(Modifier.weight(1f)) {
            when (PolitikTeil.valueOf(teil)) {
                PolitikTeil.KONGRESS -> KongressListe(state, stand, gefolgt, liste, oeffnen)
                PolitikTeil.SENAT -> SenatListe(state, gefolgt, liste, oeffnen)
                PolitikTeil.REGIERUNG -> RegierungListe(state, gefolgt, liste, oeffnen)
            }
        }
    }
}

// Kongress

@Composable
private fun KongressListe(
    state: AppState,
    stand: de.troetenrepublik.app.market.CopyRepository.Stand,
    gefolgt: Set<String>,
    liste: LazyListState,
    oeffnen: (String) -> Unit,
) {
    var suche by rememberSaveable { mutableStateOf("") }
    val personen = remember(stand.meldungen) {
        ListenMerker.von("kongress-personen", stand.meldungen) { Kongress.personen(stand.meldungen.filter { it.datum >= System.currentTimeMillis() - 365L * 86_400_000L }) }
    }
    val gezeigt = remember(personen, suche) {
        val q = suche.trim()
        val treffer = if (q.isEmpty()) personen else personen.filter { it.name.contains(q, true) || it.wahlkreis.startsWith(q, true) }
        val bekannt = treffer.filter { p -> BEKANNT.any { b -> p.schluessel.startsWith("$b|") } }.sortedBy { p -> BEKANNT.indexOfFirst { p.schluessel.startsWith("$it|") } }
        bekannt + (treffer - bekannt.toSet())
    }
    var neueste by remember { mutableStateOf(ListenMerker.vorhanden<List<Pair<Kongress.Meldung, Kongress.Geschaeft>>>("kongress-neueste", stand.meldungen)) }
    LaunchedEffect(stand.meldungen) {
        val repo = state.copy ?: return@LaunchedEffect
        if (stand.meldungen.isEmpty()) return@LaunchedEffect
        ListenMerker.vorhanden<List<Pair<Kongress.Meldung, Kongress.Geschaeft>>>("kongress-neueste", stand.meldungen)?.let { neueste = it; return@LaunchedEffect }
        val juengste = stand.meldungen.filter { repo.lesbar(it) }.take(10)
        neueste = repo.geschaefte(juengste).flatMap { (m, g) -> g.orEmpty().filter { it.kuerzel != null && it.art != Kongress.Art.TAUSCH }.map { m to it } }
            .sortedWith(compareByDescending<Pair<Kongress.Meldung, Kongress.Geschaeft>> { it.first.datum }.thenByDescending { it.second.datum })
            .take(25)
        ListenMerker.merken("kongress-neueste", stand.meldungen, neueste)
    }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item {
            OutlinedTextField(
                value = suche, onValueChange = { suche = it.take(40) }, singleLine = true,
                placeholder = { Text(S.copySearchMember) },
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        if (suche.isBlank()) {
            item { NavRow(S.rgTitel, S.rgNav) { oeffnen(CopyRouten.RANGLISTE) } }
            item { NavRow(S.kaTitel, S.kaNav) { oeffnen(CopyRouten.KONGRESS_KAUFT) } }
        }
        if (stand.meldungen.isEmpty()) {
            item { EmptyHint(if (stand.laedt || stand.geladenAm == 0L && !stand.fehler) S.copyLoading else S.copyUnavailable) }
        }
        if (suche.isBlank() && stand.meldungen.isNotEmpty()) {
            item { SectionHeader(S.copyLatestTrades) }
            val n = neueste
            if (n == null) item { CopyLade() }
            // Laufnummer im Schluessel, weil eine Meldung dasselbe Geschaeft zweimal enthalten kann
            else itemsIndexed(n, key = { i, (m, _) -> "n-$i-${m.docId}" }) { _, (m, g) ->
                GeschaeftZeile(state, g, untertitel = Fmt.date(g.datum), person = m.name) { oeffnen(CopyRouten.person(m.person)) }
            }
            item { SectionHeader(S.copyMembers) }
        }
        items(gezeigt, key = { "p-" + it.schluessel }) { p ->
            PersonZeile(
                name = p.name,
                unter = listOf(staat(p.wahlkreis), S.copyFilings(p.meldungen.size), S.copyLast(Fmt.date(p.letzte))).joinToString(" · "),
                gefolgt = "k:${p.schluessel}" in gefolgt,
                foto = Fotos.von(state, "k:${p.schluessel}", p.wahlkreis),
            ) { oeffnen(CopyRouten.person(p.schluessel)) }
        }
        item { CopyFuss(S.copyCongressIntro, S.copyCongressSource + " " + S.copyPhotosCongress) }
    }
}

// CA11 wird zu CA-11.
internal fun staat(w: String) = if (w.length > 2) w.take(2) + "-" + w.drop(2).trimStart('0').ifEmpty { "0" } else w

@Composable
internal fun PersonZeile(name: String, unter: String, gefolgt: Boolean, foto: de.troetenrepublik.app.market.Personenbilder.Foto? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonBild(foto, name, size = 38)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (gefolgt) Icon(Icons.Filled.Star, S.copyFollowing, tint = LocalSignal.current.link, modifier = Modifier.size(18.dp))
    }
}

// Logo, Name, Art und Betragsspanne.
@Composable
internal fun GeschaeftZeile(state: AppState, g: Kongress.Geschaeft, untertitel: String, person: String? = null, onClick: () -> Unit) {
    val signal = LocalSignal.current
    val sym = g.kuerzel?.let { CopyRouten.symbol(it) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    // Optionen mit eigener Zeile: Call oder Put, Basispreis, Verfall, Kontrakte
    val option = remember(g) { optionText(g) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sym != null) InstrumentLogo(state, sym, CopyRouten.name(g.kuerzel, g.wert), size = 36) else InstrumentAvatar(g.wert, g.wert, size = 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(
                CopyRouten.name(g.kuerzel, g.wert), zusatz = g.kuerzel?.let { " ($it)" } ?: "",
                style = MaterialTheme.typography.titleSmall,
            )
            if (option != null) VollText(option, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            // Immer gleich: die Person in einer eigenen Zeile, darunter das volle Datum mit der Art des Papiers
            if (person != null) NamenText(person, style = MaterialTheme.typography.bodySmall, color = grau)
            val art = if (option != null) null else typText(g.typ)
            VollText(listOfNotNull(untertitel, art).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = grau)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                artText(g.art),
                style = MaterialTheme.typography.labelMedium,
                color = when { g.kauf -> signal.up; g.verkauf -> signal.down; else -> grau },
                maxLines = 1,
                softWrap = false,
            )
            Text(spanne(g.von, g.bis), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

internal fun artText(a: Kongress.Art): String = when (a) {
    Kongress.Art.KAUF -> S.copyBuy; Kongress.Art.VERKAUF -> S.copySell; Kongress.Art.TEILVERKAUF -> S.copyPartialSale; Kongress.Art.TAUSCH -> S.copyExchange
}

// Gehandelte Option, soweit die Meldung sie nennt. null, wenn das Geschaeft keine Option ist.
internal fun optionText(g: Kongress.Geschaeft): String? {
    if (g.typ != "OP" && de.troetenrepublik.app.market.Derivate.aus(g) == null) return null
    val d = de.troetenrepublik.app.market.Derivate.aus(g) ?: return S.copyTypeOption
    return derivatText(d)
}

private fun typText(t: String): String? = when (t) {
    "ST" -> null
    "OP" -> S.copyTypeOption
    "GS" -> S.copyTypeGovBond
    "MB" -> S.copyTypeMuniBond
    "CS" -> S.copyTypeCorpBond
    "EF", "MF" -> S.copyTypeFund
    else -> null
}

// Betragsspanne wie in den Meldungen. Deren Untergrenzen enden auf 1 (1.001, 15.001), gezeigt wird die runde Zahl
// darunter. In der Anzeigewaehrung auf zwei gueltige Stellen gerundet, genauer sind die Spannen ohnehin nicht.
internal fun spanne(von: Long, bis: Long?): String {
    val unten = if (von % 1000 == 1L) von - 1 else von
    val (u, zeichen) = Fmt.ausDollar(unten.toDouble())
    val umgerechnet = zeichen != "$"
    fun k(x: Double) = betragKurz(if (umgerechnet) zweiStellen(x) else x)
    if (bis == null) return S.copyOver(Fmt.mitSymbol(k(u), zeichen))
    return Fmt.mitSymbol(k(u) + "–" + k(Fmt.ausDollar(bis.toDouble()).first), zeichen)
}

// 12.937 wird zu 13.000, 862 zu 860.
internal fun zweiStellen(x: Double): Double {
    if (x <= 0.0 || !x.isFinite()) return 0.0
    val stelle = kotlin.math.floor(kotlin.math.log10(x)).let { e -> (0 until (e - 1).toInt().coerceAtLeast(0)).fold(1.0) { p, _ -> p * 10 } }
    return kotlin.math.round(x / stelle) * stelle
}

private fun betragKurz(v: Double): String =
    if (v >= 1_000_000) Fmt.num(v / 1_000_000.0, if (v % 1_000_000 == 0.0) 0 else 1) + S.copyMillion else Fmt.num(v, 0)

@Composable
internal fun CopyHinweis(text: String) {
    AufklappText(text, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}

// Ende einer Liste: was sie zeigt und woher die Daten kommen, damit oben gleich die Eintraege stehen.
@Composable
internal fun CopyFuss(hinweis: String, quelle: String) {
    // Erklaerung und Quelle hinter Mehr, die Quelle allein ist sieben Zeilen lang
    AufklappText(hinweis + " " + quelle, Modifier.padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 40.dp))
}

@Composable
internal fun Quelle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 40.dp),
    )
}

@Composable
internal fun CopyLade() {
    LinearProgressIndicator(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurface, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

// Darstellung wie bei Senat und Regierung.
@Composable
fun KongressPersonScreen(state: AppState, schluessel: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repo = state.copy
    val stand by (repo?.stand ?: kotlinx.coroutines.flow.MutableStateFlow(de.troetenrepublik.app.market.CopyRepository.Stand())).collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { repo?.verzeichnis() } }
    val meldungen = remember(stand.meldungen, schluessel) { repo?.meldungenVon(schluessel).orEmpty() }
    val kopf = meldungen.firstOrNull() ?: stand.meldungen.firstOrNull { it.person == schluessel }
    var daten by remember { mutableStateOf<List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>>?>(null) }
    LaunchedEffect(meldungen) { if (repo != null && meldungen.isNotEmpty()) daten = repo.geschaefte(meldungen) }
    val papier = remember(daten) { daten.orEmpty().filter { (m, g) -> repo?.lesbar(m) != true || g == null } }
    // Ausgelesene Papiermeldungen: Zahl und unsicher gelesene Zeilen
    val ausPapier = remember(meldungen) { meldungen.filter { !it.elektronisch && repo?.papier?.enthaelt(it.docId) == true } }
    PolitikerSeite(
        state = state,
        titel = kopf?.name ?: schluessel.split('|').let { "${it.getOrElse(1) { "" }} ${it[0]}" },
        untertitel = kopf?.let { S.copyHouseOf(staat(it.wahlkreis)) },
        folgeKey = "k:$schluessel",
        daten = daten?.map { (m, g) -> m to (if (repo?.lesbar(m) == true) g else null) },
        meldungenText = S.copyFilings12m,
        papier = papier,
        verlaufBereit = stand.meldungen.isNotEmpty(),
        verlauf = { VerlaufLader.kongress(state, schluessel) },
        hinweis = if (ausPapier.isEmpty()) null else S.copyPaperRead(ausPapier.size, ausPapier.sumOf { repo?.papier?.unlesbar(it.docId) ?: 0 }),
        quelle = S.copyCongressSource,
        foto = Fotos.von(state, "k:$schluessel", kopf?.wahlkreis),
        onBack = onBack,
        onOpen = onOpen,
        meldungenAnzahl = meldungen.size,
        leer = meldungen.isEmpty() && stand.geladenAm > 0,
        zusatz = if (state.vermoegen.containsKey(schluessel)) ({ VermoegenZeile(state, schluessel) }) else null,
    )
}

// Glocke: Mitteilungen bei neuen Meldungen dieser Person.
@Composable
internal fun GlockenKnopf(state: AppState, key: String, name: String) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val an = key in state.glocken(settings)
    IconButton(onClick = {
        state.glocke(key, !an)
        state.showToast(
            when {
                an -> S.copyBellOffToast(name)
                !settings.notifications -> S.copyBellNotificationsOff
                key.startsWith("n:") -> S.copyBellOnToast(name) + " " + S.copyBellSenateNote
                else -> S.copyBellOnToast(name)
            },
        )
    }) {
        Icon(
            if (an) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
            if (an) S.copyBellOff else S.copyBellOn,
            tint = if (an) LocalSignal.current.link else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun FolgenKnopf(an: Boolean, onChange: (Boolean) -> Unit) {
    IconButton(onClick = { onChange(!an) }) {
        Icon(if (an) Icons.Filled.Star else Icons.Outlined.StarBorder, if (an) S.copyUnfollow else S.copyFollow, tint = if (an) LocalSignal.current.link else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun Kennzahlen(werte: List<Pair<String, String>>) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
        werte.chunked(2).forEach { zeile ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                zeile.forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (zeile.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// Investoren

@Composable
private fun InvestorenListe(state: AppState, gefolgt: Set<String>, liste: LazyListState, oeffnen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { ActionLineWide(S.pvCompareInvestors) { oeffnen(CopyRouten.vergleich("i")) } }
        items(state.investoren, key = { "i-" + it.id }) { inv ->
            val q = inv.aktuell
            val top = q?.positionen?.sortedByDescending { it.wert }?.take(3)?.joinToString(", ") { it.kuerzel ?: CopyRouten.name(null, it.name) }.orEmpty()
            Row(Modifier.fillMaxWidth().clickable { oeffnen(CopyRouten.investor(inv.id)) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PersonBild(Fotos.von(state, "i:${inv.id}"), inv.name, size = 38)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(inv.name, style = MaterialTheme.typography.titleSmall)
                    Text(inv.firma, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (q != null) Text(
                        S.copyInvestorLine(Fmt.date(iso(q.stichtag)), q.positionen.size, Fmt.dollarKompakt(q.summe)) + if (top.isNotEmpty()) " · $top" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Entwicklung laut Verlaufsdatei in der Anzeigewaehrung
                state.verlaeufe.investoren[inv.id]?.reihe(if (Fmt.inUsd) Portfolioverlauf.Waehrung.USD else Portfolioverlauf.Waehrung.EUR)
                    ?.takeIf { it.size >= 2 && it.first().second > 0 }?.let { r ->
                        val g = r.last().second / r.first().second - 1
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                            Text(Fmt.signedPct(g * 100, 0), style = MaterialTheme.typography.titleSmall, color = LocalSignal.current.forChange(g))
                            Text(S.pvSince(Fmt.monthShortYear(java.time.YearMonth.from(java.time.Instant.ofEpochMilli(r.first().first).atZone(java.time.ZoneOffset.UTC)))),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                if ("i:${inv.id}" in gefolgt) Icon(Icons.Filled.Star, S.copyFollowing, tint = LocalSignal.current.link, modifier = Modifier.size(18.dp).padding(start = 4.dp))
            }
        }
        item { CopyFuss(S.copyInvestorsIntro, S.copyInvestorsSource + " " + S.copyPhotosCommons) }
    }
}

private fun iso(s: String): Long = runCatching { java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)

@Composable
fun InvestorScreen(state: AppState, id: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    katalogBereit()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val secNeuer by state.secNeuer.collectAsStateWithLifecycle()
    val inv = state.investoren.firstOrNull { it.id == id }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    var nachbilden by remember { mutableStateOf(false) }
    var offen by remember { mutableStateOf(emptySet<String>()) }
    val signal = LocalSignal.current
    val zeilen = remember(inv) { inv?.let { Investoren.zeilen(it) }.orEmpty() }
    val de = I18n.lang == Lang.DE
    val pv = state.verlaeufe.investoren[id]
    var pvReihe by remember { mutableStateOf<List<Pair<Long, Double>>?>(null) }
    var pvLaedt by remember { mutableStateOf(pv != null) }
    LaunchedEffect(id) { if (pv != null) { pvReihe = runCatching { VerlaufLader.investor(state, id) }.getOrNull() ?: pv.usd; pvLaedt = false } }
    val pvMarken = remember(pv) { pv?.let { investorMarken(it) }.orEmpty() }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item {
            ScreenHeader(inv?.name ?: id, inv?.firma, onBack, trailing = {
                if (inv != null) Row {
                    GlockenKnopf(state, "i:$id", inv.name)
                    FolgenKnopf("i:$id" in state.gefolgt(settings)) { state.folgen("i:$id", it) }
                }
            })
        }
        if (inv == null) { item { EmptyHint(S.copyUnavailable) }; return@LazyColumn }
        item { PersonKopfbild(Fotos.von(state, "i:$id"), inv.name) }
        val q = inv.aktuell
        secNeuer[id]?.let { (stichtag, tag) ->
            item { CopyHinweis(S.copyNewer13f(Fmt.date(iso(stichtag)), Fmt.date(iso(tag)), q?.stichtag?.let { Fmt.date(iso(it)) } ?: "")) }
        }
        item { CopyHinweis(if (de) inv.stilDe else inv.stilEn) }
        if (q != null) {
            item {
                Kennzahlen(
                    listOf(
                        S.copyAsOf to Fmt.date(iso(q.stichtag)),
                        S.copyFiled to Fmt.date(iso(q.eingereicht)),
                        S.copyValue to Fmt.dollarKompakt(q.summe),
                        S.copyPositions to q.positionen.size.toString(),
                    ),
                )
            }
            item { ActionLineWide(S.copyReplicate) { nachbilden = true } }
        }
        if (pv != null) {
            item { SectionHeader(S.pvTitle) }
            item {
                PortfolioKarte(
                    state, titel = inv.name, haupt = pvReihe?.let { it to Portfolioverlauf.Waehrung.USD },
                    laedt = pvLaedt, hinweis = S.pvNoteInvestor(pv.quartale.size, pv.quartale.map { it.abgedeckt }.average()), eigener = "i:$id", marken = pvMarken,
                    standardZeitraum = VerlaufZeitraum.J5,
                )
            }
            item { Column { QuartalsBewegungen(state, pv, onOpen) } }
        } else {
            item { CopyHinweis(S.pvNoInvestorHistory) }
        }
        // Calls und Puts als Unterzweige bei ihrer Aktie (gleiche CUSIP), groesste Position zuerst
        val gruppen = investorGruppen(zeilen.filter { it.wandel != Investoren.Wandel.VERKAUFT })
        val mitZweigen = gruppen.any { g -> g.any { it.position.art == "call" || it.position.art == "put" } }
        item { SectionHeader(S.copyHoldings) }
        itemsIndexed(gruppen, key = { i, g -> "z-$i-" + g.first().position.cusip }) { _, g ->
            val schluessel = g.first().position.cusip + g.first().position.name
            InvestorGruppe(state, g, signal, onOpen, mitZweigen, schluessel in offen) { offen = if (schluessel in offen) offen - schluessel else offen + schluessel }
        }
        if (mitZweigen) item {
            Text(S.derivInvestorIntro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp))
        }
        val weg = zeilen.filter { it.wandel == Investoren.Wandel.VERKAUFT }
        if (weg.isNotEmpty()) {
            item { SectionHeader(S.copySoldOut) }
            itemsIndexed(weg, key = { i, z -> "w-$i-" + z.position.cusip + z.position.art }) { _, z -> InvestorZeile(state, z, signal, onOpen) }
        }
        item { Quelle(S.copyInvestorsSource) }
    }

    if (nachbilden && inv != null) {
        NachbildenBlatt(
            state = state, titel = inv.name, mitAnzahl = true, notiz = S.copyNote(inv.name),
            gewichte = { n -> Investoren.gewichte(inv, n).map { CopyRouten.symbol(it.first) to it.second } },
            namen = { sym -> inv.aktuell?.positionen?.firstOrNull { p -> p.kuerzel?.let { CopyRouten.symbol(it) } == sym }?.let { CopyRouten.name(it.kuerzel, it.name) } },
            onDismiss = { nachbilden = false },
        )
    }
}

// Zeilen derselben CUSIP zusammen, Aktie zuerst, groesste Gruppe zuerst.
private fun investorGruppen(zeilen: List<Investoren.Zeile>): List<List<Investoren.Zeile>> =
    zeilen.groupBy { it.position.cusip.ifBlank { it.position.kuerzel ?: it.position.name } }.values
        .map { g -> g.sortedWith(compareBy<Investoren.Zeile> { artRang(it.position.art) }.thenByDescending { it.position.wert }) }
        .sortedByDescending { g -> g.sumOf { it.anteil } }

private fun artRang(art: String) = when (art) { "aktie" -> 0; "anleihe" -> 1; "call" -> 2; else -> 3 }

private fun artText(art: String): String? = when (art) { "call" -> S.copyCall; "put" -> S.copyPut; "anleihe" -> S.copyTypeCorpBond; else -> null }

private fun wandelText(z: Investoren.Zeile): String? = when (z.wandel) {
    Investoren.Wandel.NEU -> S.copyNew
    Investoren.Wandel.ERHOEHT -> S.copyIncreased(Fmt.pct((z.position.stueck / z.stueckVorher - 1) * 100, 0))
    Investoren.Wandel.VERRINGERT -> S.copyReduced(Fmt.pct((1 - z.position.stueck / z.stueckVorher) * 100, 0))
    Investoren.Wandel.VERKAUFT -> S.copySold
    Investoren.Wandel.GLEICH -> null
}

@Composable
private fun wandelFarbe(z: Investoren.Zeile, signal: Signal) = when (z.wandel) {
    Investoren.Wandel.NEU, Investoren.Wandel.ERHOEHT -> signal.up
    Investoren.Wandel.VERRINGERT, Investoren.Wandel.VERKAUFT -> signal.down
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun InvestorZeile(state: AppState, z: Investoren.Zeile, signal: Signal, onOpen: (String) -> Unit) =
    InvestorGruppe(state, listOf(z), signal, onOpen, mitZweigen = false, auf = false) {}

// Bestand eines Investors an einem Wertpapier. Mit Calls oder Puts klappt die Zeile ueber das V rechts auf, darunter
// stehen Aktie und Optionen mit Wert, Anteil an der Position und Veraenderung.
// mitZweigen haelt Platz fuer das V frei, damit die Anteile aller Zeilen untereinander stehen.
@Composable
private fun InvestorGruppe(
    state: AppState, g: List<Investoren.Zeile>, signal: Signal, onOpen: (String) -> Unit, mitZweigen: Boolean, auf: Boolean, umschalten: () -> Unit,
) {
    val z = g.first()
    val p = z.position
    val sym = g.firstNotNullOfOrNull { it.position.kuerzel }?.let { CopyRouten.symbol(it) }
    val kuerzel = g.firstNotNullOfOrNull { it.position.kuerzel }
    val name = CopyRouten.name(kuerzel, p.name)
    val verzweigt = g.any { it.position.art == "call" || it.position.art == "put" }
    val wert = g.sumOf { it.position.wert }
    val anteil = g.sumOf { it.anteil }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = sym != null) { sym?.let(onOpen) }
            .padding(start = 20.dp, end = if (mitZweigen) 8.dp else 20.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sym != null) InstrumentLogo(state, sym, name, size = 36) else InstrumentAvatar(name, name, size = 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, zusatz = kuerzel?.let { " ($it)" } ?: "", style = MaterialTheme.typography.titleSmall)
            // Mit Unterzweigen stehen Art und Veraenderung dort, hier nur der Wert der ganzen Position
            Text(
                if (verzweigt) Fmt.dollarKompakt(wert) else listOfNotNull(Fmt.dollarKompakt(wert), artText(p.art), wandelText(z)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (verzweigt) MaterialTheme.colorScheme.onSurfaceVariant else wandelFarbe(z, signal),
            )
            if (anteil > 0) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(anteil.toFloat().coerceIn(0.01f, 1f)).fillMaxSize().background(MaterialTheme.colorScheme.onSurface))
            }
        }
        if (anteil > 0) Text(Fmt.pct(anteil * 100, 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
        if (mitZweigen) AufklappKnopf(auf, verzweigt, umschalten)
    }
    if (auf && verzweigt) {
        g.forEachIndexed { i, t ->
            val tp = t.position
            ZweigZeile(
                logo = { if (sym != null) InstrumentLogo(state, sym, name, size = 22) else InstrumentAvatar(name, name, size = 22) },
                titel = artText(tp.art) ?: S.copyPartShares,
                untertitel = listOfNotNull(S.copyPartShare(Fmt.pct(if (wert > 0) tp.wert / wert * 100 else 0.0, 1)), wandelText(t)).joinToString(" · "),
                untertitelFarbe = if (t.wandel == Investoren.Wandel.GLEICH) null else wandelFarbe(t, signal),
                wert = Fmt.dollarKompakt(tp.wert),
                letzter = i == g.lastIndex,
                rechts = ZWEIG_KNOPF + 8.dp,
            ) { sym?.let(onOpen) }
        }
    }
}

// Strategien

@Composable
private fun StrategienListe(liste: LazyListState, oeffnen: (String) -> Unit) {
    val de = I18n.lang == Lang.DE
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { ActionLineWide(S.pvCompareStrategies) { oeffnen(CopyRouten.vergleich("s")) } }
        item { NavRow(S.kaTitel, S.kaNav) { oeffnen(CopyRouten.KONGRESS_KAUFT) } }
        Strategien.Risiko.entries.forEach { r ->
            item(key = "r-" + r.name) { SectionHeader(risikoName(r)) }
            items(Strategien.alle.filter { it.risiko == r }, key = { "s-" + it.id }) { s ->
                Column(Modifier.fillMaxWidth().clickable { oeffnen(CopyRouten.strategie(s.id)) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(if (de) s.nameDe else s.nameEn, style = MaterialTheme.typography.titleSmall)
                    Text(
                        (if (de) s.textDe else s.textEn).substringBefore(". ") + ".",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Aufteilung(s.anteile, Modifier.padding(top = 8.dp))
                }
            }
        }
        item { CopyFuss(S.copyStrategiesIntro, S.copyStrategiesNote) }
    }
}

private fun risikoName(r: Strategien.Risiko) = when (r) {
    Strategien.Risiko.DEFENSIV -> S.copyDefensive
    Strategien.Risiko.AUSGEWOGEN -> S.copyBalanced
    Strategien.Risiko.OFFENSIV -> S.copyOffensive
}

private val TEILFARBEN = listOf(Color(0xFF5B8DEF), Color(0xFF2EC27E), Color(0xFFF5C211), Color(0xFFE66100), Color(0xFF9141AC), Color(0xFF63C5DA))

@Composable
private fun Aufteilung(anteile: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
        anteile.forEachIndexed { i, (_, w) -> Box(Modifier.weight(w.toFloat()).fillMaxSize().background(TEILFARBEN[i % TEILFARBEN.size])) }
    }
}

@Composable
fun StrategieScreen(state: AppState, id: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    katalogBereit()
    val s = Strategien.finde(id)
    val de = I18n.lang == Lang.DE
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    var kennzahlen by remember { mutableStateOf<Strategien.Kennzahlen?>(null) }
    var laedt by remember { mutableStateOf(true) }
    var nachbilden by remember { mutableStateOf(false) }
    var sparplan by remember { mutableStateOf(false) }
    val signal = LocalSignal.current
    LaunchedEffect(id) {
        if (s == null) return@LaunchedEffect
        val reihen = coroutineScope { s.anteile.map { (sym, _) -> async { sym to state.dailyCloses(sym) } }.awaitAll() }.toMap()
        kennzahlen = Strategien.kennzahlen(reihen, s.anteile, System.currentTimeMillis())
        laedt = false
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(s?.let { if (de) it.nameDe else it.nameEn } ?: id, s?.let { risikoName(it.risiko) }, onBack) }
        if (s == null) { item { EmptyHint(S.copyUnavailable) }; return@LazyColumn }
        item { CopyHinweis(if (de) s.textDe else s.textEn) }
        item { SectionHeader(S.copyAllocation) }
        items(s.anteile.withIndex().toList(), key = { "a-" + it.value.first }) { (i, a) ->
            val (sym, w) = a
            Row(Modifier.fillMaxWidth().clickable { onOpen(sym) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(TEILFARBEN[i % TEILFARBEN.size]))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(CopyRouten.katalogName(sym) ?: sym, style = MaterialTheme.typography.titleSmall)
                    Text(sym, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Fmt.pct(w * 100, if (w * 100 % 1.0 == 0.0) 0 else 1), style = MaterialTheme.typography.titleSmall)
            }
        }
        item { SectionHeader(S.copyHistory) }
        val k = kennzahlen
        when {
            laedt -> item { CopyLade() }
            k == null -> item { EmptyHint(S.copyNoHistory) }
            else -> {
                item {
                    Kennzahlen(
                        listOf(
                            S.copyPeriod to S.copyRange(Fmt.date(k.von), Fmt.date(k.bis)),
                            S.copyReturnPa to Fmt.signedPct(k.renditePa * 100, 1),
                            S.copyVolatility to Fmt.pct(k.schwankungPa * 100, 1),
                            S.copyMaxDrawdown to Fmt.signedPct(k.maxVerlust * 100, 1),
                        ),
                    )
                }
                item {
                    PortfolioKarte(
                        state, titel = if (de) s.nameDe else s.nameEn,
                        haupt = k.verlauf.map { it.first to it.second * 100 } to Portfolioverlauf.Waehrung.EUR,
                        laedt = false, hinweis = S.pvNoteStrategy, eigener = "s:$id", standardZeitraum = VerlaufZeitraum.J5,
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(S.copyYears, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        k.jahre.chunked(4).forEach { zeile ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                zeile.forEach { (j, r) ->
                                    Column(Modifier.weight(1f)) {
                                        Text(j.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(Fmt.signedPct(r * 100, 1), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(r))
                                    }
                                }
                                repeat(4 - zeile.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        item { ActionLineWide(S.copyReplicate) { nachbilden = true } }
        item { ActionLineWide(S.copyAsPlans) { sparplan = true } }
        item { Quelle(S.copyStrategiesNote) }
    }
    if (s != null && nachbilden) {
        NachbildenBlatt(state, titel = if (de) s.nameDe else s.nameEn, gewichte = { s.anteile }, mitAnzahl = false, notiz = S.copyNote(if (de) s.nameDe else s.nameEn), onDismiss = { nachbilden = false })
    }
    if (s != null && sparplan) SparplanBlatt(state, s, onDismiss = { sparplan = false })
}

// Gefolgt

@Composable
private fun GefolgtListe(
    state: AppState,
    stand: de.troetenrepublik.app.market.CopyRepository.Stand,
    gefolgt: Set<String>,
    liste: LazyListState,
    oeffnen: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { CopyHinweis(S.copyFollowedIntro) }
        item { NavRow(S.copyBellsTitle, S.copyBellsSubtitle(state.glocken().size)) { oeffnen(CopyRouten.GLOCKEN) } }
        items(gefolgt.sorted(), key = { "f-$it" }) { key ->
            if (key.startsWith("n:")) {
                val sk = key.removePrefix("n:")
                val m = state.senat?.meldungen?.filter { it.person == sk }.orEmpty()
                val name = m.maxByOrNull { de.troetenrepublik.app.market.Politik.tag(it.eingang) }?.name ?: sk.replace('|', ' ')
                PersonZeile(name, listOfNotNull(S.copySenatorOf, m.maxOfOrNull { de.troetenrepublik.app.market.Politik.tag(it.eingang) }?.let { S.copyLast(Fmt.date(it)) }).joinToString(" · "), true, Fotos.von(state, key)) {
                    oeffnen(CopyRouten.senator(sk))
                }
            } else if (key.startsWith("r:")) {
                val p = state.regierung?.personen?.firstOrNull { it.id == key.removePrefix("r:") && it.bestand == null } ?: return@items
                PersonZeile(p.name, if (p.id == "vance") S.copyVicePresident else S.copyPresident, true, Fotos.von(state, key)) { oeffnen(CopyRouten.regierung(p.id)) }
            } else if (key.startsWith("k:")) {
                val sk = key.removePrefix("k:")
                val m = stand.meldungen.filter { it.person == sk }
                val name = m.firstOrNull()?.name ?: sk.split('|').let { "${it.getOrElse(1) { "" }} ${it[0]}" }
                PersonZeile(name, listOfNotNull(m.firstOrNull()?.let { staat(it.wahlkreis) }, m.maxOfOrNull { it.datum }?.let { S.copyLast(Fmt.date(it)) }).joinToString(" · "), true, Fotos.von(state, key, m.firstOrNull()?.wahlkreis)) {
                    oeffnen(CopyRouten.person(sk))
                }
            } else {
                val inv = state.investoren.firstOrNull { it.id == key.removePrefix("i:") } ?: return@items
                PersonZeile(inv.name, inv.firma, true, Fotos.von(state, key)) { oeffnen(CopyRouten.investor(inv.id)) }
            }
        }
    }
}

// Nachbilden

// Nachbilden im Uebungsdepot: Betrag und bei Personen die Zahl der Werte waehlen, Aufteilung ansehen, kaufen.
// Ausserhalb der Handelszeit legt die App Limit-Orders an (siehe AppState.nachbilden).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NachbildenBlatt(
    state: AppState,
    titel: String,
    gewichte: (Int) -> List<Pair<String, Double>>,
    mitAnzahl: Boolean,
    notiz: String,
    onDismiss: () -> Unit,
    // Name aus der Meldung fuer Werte, die der Katalog nicht kennt.
    namen: (String) -> String? = { null },
) {
    val account by state.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var betragText by remember { mutableStateOf(Fmt.num(minOf(1000.0, account.cash).coerceAtLeast(0.0), 0).replace(".", "").replace(",", "")) }
    var anzahl by remember { mutableStateOf(10) }
    var laeuft by remember { mutableStateOf(false) }
    val betrag = betragText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val liste = remember(anzahl) { gewichte(anzahl) }
    val gebuehren = liste.size * de.troetenrepublik.app.engine.Trading.FEE
    val geht = betrag > 0 && betrag + gebuehren <= account.cash + 1e-9 && liste.isNotEmpty() && !laeuft
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(S.copyReplicateTitle(titel), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            CopyHinweis(S.copyReplicateHint)
            OutlinedTextField(
                value = betragText, onValueChange = { t -> betragText = t.filter { it.isDigit() || it == ',' || it == '.' }.take(10) },
                label = { Text(S.copyAmount) }, singleLine = true, suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(S.copyCash(Fmt.euro(account.cash)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            if (mitAnzahl) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(S.copyCount, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp))
                    listOf(5, 10, 20).forEach { n -> Chip(n.toString(), active = anzahl == n) { anzahl = n } }
                }
            }
            liste.forEach { (sym, w) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(CopyRouten.katalogName(sym) ?: namen(sym) ?: sym, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Fmt.pct(w * 100, 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
                    Text(Fmt.euro(betrag * w), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (liste.isEmpty()) EmptyHint(S.copyNothingToCopy)
            Text(S.copyFees(liste.size, Fmt.euro(gebuehren)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.chip))
                    .background(if (geht) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = geht) {
                        laeuft = true
                        scope.launch { state.nachbilden(liste, betrag, notiz); laeuft = false; onDismiss() }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (laeuft) S.copyWorking else S.copyReplicateNow, style = MaterialTheme.typography.titleMedium, color = if (geht) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparplanBlatt(state: AppState, s: Strategien.Strategie, onDismiss: () -> Unit) {
    var betragText by remember { mutableStateOf("200") }
    var tag by remember { mutableStateOf(1) }
    val betrag = betragText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val de = I18n.lang == Lang.DE
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(S.copyPlansTitle(if (de) s.nameDe else s.nameEn), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            CopyHinweis(S.copyPlansHint)
            OutlinedTextField(
                value = betragText, onValueChange = { t -> betragText = t.filter { it.isDigit() || it == ',' || it == '.' }.take(8) },
                label = { Text(S.copyMonthly) }, singleLine = true, suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.copyPlanDay, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp))
                listOf(1, 15).forEach { d -> Chip("$d.", active = tag == d) { tag = d } }
            }
            s.anteile.forEach { (sym, w) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(CopyRouten.katalogName(sym) ?: sym, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Fmt.euro(betrag * w), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val geht = betrag >= s.anteile.size
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.chip))
                    .background(if (geht) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = geht) { state.sparplaeneAnlegen(s.anteile, betrag, tag); onDismiss() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(S.copyPlansCreate, style = MaterialTheme.typography.titleMedium, color = if (geht) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
