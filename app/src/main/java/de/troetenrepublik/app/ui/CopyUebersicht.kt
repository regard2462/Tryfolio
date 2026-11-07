package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.CopyUebersicht
import de.troetenrepublik.app.engine.KongressAuswertung
import de.troetenrepublik.app.engine.KurseSpeicher
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.RegierungOge
import de.troetenrepublik.app.plattform.SichereDatei
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Uebersicht je Gruppe im Copytrading (seit 0.69): groesste Positionen, Meistgehandeltes und Werte unter dem
// Einstiegskurs; dazu der Abschnitt „Unter Einstiegskurs“ auf der Seite einer Person. Gerechnet wird in
// engine/CopyUebersicht.kt, hier stehen das Laden der Geschaefte und Kurse und die Darstellung.

internal enum class UebersichtGruppe { HAUS, SENAT, REGIERUNG, INVESTOREN, INSIDER }

private enum class UebersichtSeite { POSITIONEN, GEHANDELT, EINSTIEG }

// Seite einer Person zum Schluessel wie beim Folgen (k:, n:, r:, o:, i:); null, wo es keine Personenseite gibt.
internal fun uebersichtRoute(key: String): String? {
    val r = key.substringAfter(':')
    return when (key.substringBefore(':')) {
        "k" -> CopyRouten.person(r)
        "n" -> CopyRouten.senator(r)
        "r" -> CopyRouten.regierung(r)
        "o" -> ogeRoute(r)
        "i" -> CopyRouten.investor(r)
        else -> null
    }
}

internal object CopyUebersichtDaten {

    class Satz(val geschaefte: List<CopyUebersicht.Geschaeft>, val bestaende: List<CopyUebersicht.Bestand>)

    // Aktien, Fonds und Sonstiges mit Kuerzel; Optionen und Anleihen nicht.
    private val ARTEN = setOf("ST", "EF", "OT")
    private const val FRISCH_MS = 6 * 3_600_000L

    // Hoechstens so viele Aktien je Gruppe bekommen Kurse (die mit den groessten Kaeufen), sonst werden es tausende Abrufe.
    private const val MAX_AKTIEN = 1200

    fun geschaeft(person: String, name: String, g: Kongress.Geschaeft): CopyUebersicht.Geschaeft? {
        val k = g.kuerzel?.takeIf { it.isNotBlank() } ?: return null
        if (g.typ !in ARTEN || !(g.kauf || g.verkauf) || g.mitte <= 0 || g.datum <= 0) return null
        return CopyUebersicht.Geschaeft(person, name, CopyRouten.symbol(k), g.kauf, g.datum, g.mitte)
    }

    private val lock = Mutex()
    @Volatile private var kongress: Pair<Long, Map<UebersichtGruppe, Satz>>? = null

    fun fertig(g: UebersichtGruppe): Satz? = when (g) {
        UebersichtGruppe.INVESTOREN, UebersichtGruppe.INSIDER -> null
        else -> kongress?.takeIf { System.currentTimeMillis() - it.first < FRISCH_MS }?.second?.get(g)
    }

    // Geschaefte und Bestaende einer Gruppe. Haus, Senat und Regierung werden zusammen aufbereitet und sechs Stunden
    // gemerkt.
    suspend fun laden(state: AppState, g: UebersichtGruppe): Satz = withContext(Dispatchers.Default) {
        when (g) {
            UebersichtGruppe.INVESTOREN -> CopyUebersicht.ausInvestoren(state.investoren, CopyRouten::symbol).let { Satz(it.first, it.second) }
            UebersichtGruppe.INSIDER -> {
                runCatching { state.secOhneNetz() }
                CopyUebersicht.ausInsidern(state.sec.value?.geschaefte.orEmpty(), CopyRouten::symbol).let { Satz(it.first, it.second) }
            }
            else -> kongressSaetze(state).getValue(g)
        }
    }

    private suspend fun kongressSaetze(state: AppState): Map<UebersichtGruppe, Satz> = lock.withLock {
        kongress?.takeIf { System.currentTimeMillis() - it.first < FRISCH_MS }?.let { return@withLock it.second }
        val haus = ArrayList<CopyUebersicht.Geschaeft>()
        val senat = ArrayList<CopyUebersicht.Geschaeft>()
        val regierung = ArrayList<CopyUebersicht.Geschaeft>()
        val bestand = ArrayList<CopyUebersicht.Bestand>()
        fun dazu(l: MutableList<CopyUebersicht.Geschaeft>, person: String, name: String, g: Kongress.Geschaeft) { geschaeft(person, name, g)?.let { l += it } }
        // Repraesentantenhaus: mitgelieferte und schon gelesene Meldungen, dazu bis zu 150 neuere aus dem Verzeichnis
        val h = state.haus
        val bekannt = HashSet<String>()
        h?.meldungen?.forEach { m ->
            bekannt += m.docId
            val g = h.geschaefte[m.docId] ?: state.copy?.papier?.geschaefte(m.docId) ?: return@forEach
            g.forEach { dazu(haus, "k:" + m.person, m.name, it) }
        }
        state.copy?.let { r ->
            runCatching { r.verzeichnis() }
            val neu = r.stand.value.meldungen.filter { it.docId !in bekannt && r.lesbar(it) }.take(150)
            runCatching { r.geschaefte(neu) }.getOrDefault(emptyList()).forEach { (m, g) -> g?.forEach { dazu(haus, "k:" + m.person, m.name, it) } }
        }
        state.senat?.meldungen?.forEach { sm ->
            if (sm.papier) return@forEach
            val t = Politik.tag(sm.eingang)
            sm.geschaefte.forEach { dazu(senat, "n:" + sm.person, sm.name, Politik.geschaeft(it, t)) }
        }
        state.regierung?.personen?.forEach { p ->
            Politik.daten(p).forEach { (_, g) -> g?.forEach { dazu(regierung, "r:" + p.id, p.name, it) } }
            // Bestand aus der Vermoegensmeldung (etwa der Vizepraesident)
            p.bestand?.positionen?.forEach { b ->
                val k = Politik.kuerzel(b.k)
                if (k != null && b.t in ARTEN && b.mitte > 0) bestand += CopyUebersicht.Bestand("r:" + p.id, p.name, CopyRouten.symbol(k), b.mitte)
            }
        }
        // Weitere Regierungsmitglieder: nur Meldungen, die schon auf dem Geraet liegen
        (state.ogeRegierung.value ?: runCatching { state.ogeLaden(netz = false) }.getOrNull())?.personen?.forEach { p ->
            RegierungOge.geschaefte(p).forEach { dazu(regierung, "o:" + p.id, p.name, it) }
        }
        val saetze = mapOf(
            UebersichtGruppe.HAUS to Satz(haus, emptyList()),
            UebersichtGruppe.SENAT to Satz(senat, emptyList()),
            UebersichtGruppe.REGIERUNG to Satz(regierung, bestand),
        )
        kongress = System.currentTimeMillis() to saetze
        saetze
    }

    class EinstiegErgebnis(
        val einstaende: List<CopyUebersicht.Einstand>,
        // Alter des aeltesten benutzten Kurses.
        val stand: Long,
        // Kurse konnten nicht neu geholt werden, gezeigt wird der letzte Stand.
        val veraltet: Boolean,
        val keineKurse: Boolean,
    )

    // Einstiegskurse zu den Geschaeften: Kurse gebuendelt holen (siehe CopyKurse), dann rechnen.
    suspend fun einstieg(state: AppState, geschaefte: List<CopyUebersicht.Geschaeft>, fortschritt: (Float) -> Unit = {}): EinstiegErgebnis =
        withContext(Dispatchers.Default) {
            val jetzt = System.currentTimeMillis()
            val kaeufe = geschaefte.filter { it.kauf }
            val symbole = kaeufe.groupBy { it.symbol }.entries.sortedByDescending { e -> e.value.sumOf { it.betrag } }.take(MAX_AKTIEN).map { it.key }.toSet()
            // Wie weit zurueck Kurse noetig sind: bis zum aeltesten Kauf ohne eigenen Preis, sonst nur der heutige Kurs
            val bedarf = HashMap<String, Long>()
            for (g in kaeufe) if (g.symbol in symbole) bedarf.merge(g.symbol, if (g.preis != null && g.preis > 0) jetzt else g.datum) { a, b -> minOf(a, b) }
            val k = CopyKurse.laden(state, bedarf, fortschritt)
            val e = CopyUebersicht.einstaende(geschaefte.filter { it.symbol in symbole }, { s, t -> k.kurse[s]?.am(t) }, { s -> k.kurse[s]?.letzter })
            EinstiegErgebnis(e, k.stand, k.veraltet, symbole.isNotEmpty() && k.kurse.isEmpty())
        }
}

// Tagesschlusskurse fuer die Einstiegsrechnung: gebuendelt (20 Aktien je Abruf), auf dem Geraet zwischengespeichert
// (engine/KurseSpeicher.kt) und sechs Stunden frisch. Klappt der Abruf nicht, gilt der letzte Stand.
internal object CopyKurse {

    class Ergebnis(val kurse: Map<String, KongressAuswertung.Kurse>, val stand: Long, val veraltet: Boolean)

    private val lock = Mutex()
    private var speicher: HashMap<String, KurseSpeicher.Eintrag>? = null
    private const val TAG = KongressAuswertung.TAG

    // bedarf: je Aktie der fruehste Tag, fuer den ein Kurs gebraucht wird.
    suspend fun laden(state: AppState, bedarf: Map<String, Long>, fortschritt: (Float) -> Unit = {}): Ergebnis = withContext(Dispatchers.Default) {
        lock.withLock {
            val datei = SichereDatei(File(state.store.ordner, "einstiegskurse.bin"))
            val sp = speicher ?: HashMap<String, KurseSpeicher.Eintrag>().also { m ->
                runCatching { KurseSpeicher.lesen(datei.lesen())?.forEach { m[it.symbol] = it } }
                speicher = m
            }
            val jetzt = System.currentTimeMillis()
            val noetig = bedarf.mapValues { (_, ab) -> KurseSpeicher.bereich((jetzt - ab) / TAG + 7) }
            val offen = noetig.filter { (s, b) -> !KurseSpeicher.frisch(sp[s], jetzt, b.second) }
            var geholt = 0
            if (offen.isNotEmpty()) {
                val teile = offen.entries.groupBy({ it.value }) { it.key }.flatMap { (b, l) -> l.chunked(20).map { b to it } }
                val neu = ConcurrentHashMap<String, KurseSpeicher.Eintrag>()
                val fertig = AtomicInteger(0)
                val gleichzeitig = Semaphore(4)
                coroutineScope {
                    teile.map { (b, symbole) ->
                        async {
                            gleichzeitig.withPermit {
                                val antwort = state.schlusskurse(symbole, b.first, "1d")
                                // Ohne Antwort bleibt der alte Stand; Aktien, die Yahoo nicht kennt, merken sich als leer
                                if (antwort.isNotEmpty()) {
                                    val t = System.currentTimeMillis()
                                    for (s in symbole) {
                                        val f = antwort[s]?.filter { it.close > 0 }.orEmpty()
                                        val kurse = if (f.isEmpty()) KongressAuswertung.Kurse(IntArray(0), FloatArray(0))
                                        else KongressAuswertung.Kurse(IntArray(f.size) { Math.floorDiv(f[it].t, TAG).toInt() }, FloatArray(f.size) { f[it].close.toFloat() })
                                        neu[s] = KurseSpeicher.Eintrag(s, t, b.second, kurse)
                                    }
                                }
                                fortschritt(fertig.incrementAndGet().toFloat() / teile.size)
                            }
                        }
                    }.awaitAll()
                }
                sp.putAll(neu)
                geholt = neu.size
                if (neu.isNotEmpty()) {
                    // Eintraege, die eine Woche nicht gebraucht wurden, fallen weg
                    sp.entries.removeAll { jetzt - it.value.am > 7 * 24 * 3_600_000L }
                    val kopie = sp.values.toList()
                    withContext(Dispatchers.IO) { runCatching { datei.schreiben(KurseSpeicher.schreiben(kopie)) } }
                }
            }
            val benutzt = bedarf.keys.mapNotNull { s -> sp[s]?.let { s to it } }
            Ergebnis(
                kurse = benutzt.filter { !it.second.kurse.leer }.associate { it.first to it.second.kurse },
                stand = benutzt.minOfOrNull { it.second.am } ?: jetzt,
                veraltet = offen.isNotEmpty() && geholt < offen.size && benutzt.any { jetzt - it.second.am > 6 * 3_600_000L },
            )
        }
    }
}

@Composable
private fun UebersichtLaden(f: Float?) {
    if (f == null) CopyLade()
    else Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
        Text(S.kaLaedt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun gruppenName(g: UebersichtGruppe): String = when (g) {
    UebersichtGruppe.HAUS -> S.copyCongress
    UebersichtGruppe.SENAT -> S.copySenate
    UebersichtGruppe.REGIERUNG -> S.copyGovernment
    UebersichtGruppe.INVESTOREN -> S.copyInvestors
    UebersichtGruppe.INSIDER -> S.copyExecutives
}

@Composable
private fun Erklaerung(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
}

// Seite „Uebersicht“ einer Gruppe: drei Seiten zum Wischen. Positionen und Meistgehandeltes brauchen nur die
// Meldungen, die Kurse fuer „Unter Einstiegskurs“ werden erst beim ersten Oeffnen dieser Seite geholt.
@Composable
fun CopyUebersichtScreen(state: AppState, gruppe: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    katalogBereit()
    val g = remember(gruppe) { UebersichtGruppe.entries.firstOrNull { it.name == gruppe } ?: UebersichtGruppe.HAUS }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val scope = rememberCoroutineScope()
    var satz by remember(g) { mutableStateOf(CopyUebersichtDaten.fertig(g)) }
    var fehler by remember(g) { mutableStateOf(false) }
    LaunchedEffect(g) {
        if (satz != null) return@LaunchedEffect
        satz = runCatching { CopyUebersichtDaten.laden(state, g) }.getOrNull()
        fehler = satz == null
    }
    val seiten = UebersichtSeite.entries
    var gewaehlt by rememberSaveable { mutableStateOf(UebersichtSeite.POSITIONEN.name) }
    val pager = rememberPagerState(initialPage = seiten.indexOfFirst { it.name == gewaehlt }.coerceAtLeast(0)) { seiten.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { i -> seiten.getOrNull(i)?.let { gewaehlt = it.name } } }
    val listen = remember { List(seiten.size) { LazyListState() } }
    var einstiegBesucht by remember { mutableStateOf(gewaehlt == UebersichtSeite.EINSTIEG.name) }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { i -> if (seiten.getOrNull(i) == UebersichtSeite.EINSTIEG) einstiegBesucht = true } }

    // Kurse und Einstiegskurse, sobald die Seite einmal offen war
    var einstieg by remember(g) { mutableStateOf<CopyUebersichtDaten.EinstiegErgebnis?>(null) }
    var fortschritt by remember(g) { mutableStateOf<Float?>(null) }
    LaunchedEffect(satz, einstiegBesucht) {
        val s = satz ?: return@LaunchedEffect
        if (!einstiegBesucht || einstieg != null) return@LaunchedEffect
        fortschritt = 0f
        einstieg = runCatching { CopyUebersichtDaten.einstieg(state, s.geschaefte) { fortschritt = it } }.getOrNull()
            ?: CopyUebersichtDaten.EinstiegErgebnis(emptyList(), System.currentTimeMillis(), false, true)
        fortschritt = null
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.cuTitel, gruppenName(g), onBack)
        MitlaufendeReiter(
            anzahl = seiten.size, pager = pager, titel = { i -> S.cuSeite(seiten[i].name) },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1) { seite ->
            when (seiten[seite]) {
                UebersichtSeite.POSITIONEN -> PositionenSeite(state, g, satz, fehler, listen[seite], onOpen)
                UebersichtSeite.GEHANDELT -> GehandeltSeite(state, satz, fehler, listen[seite], onOpen)
                UebersichtSeite.EINSTIEG -> EinstiegSeite(state, einstieg, fortschritt, satz != null, fehler, listen[seite], onOpen)
            }
        }
    }
}

@Composable
private fun PositionenSeite(state: AppState, g: UebersichtGruppe, satz: CopyUebersichtDaten.Satz?, fehler: Boolean, liste: LazyListState, onOpen: (String) -> Unit) {
    val positionen by produceState<List<CopyUebersicht.Position>?>(null, satz) {
        value = satz?.let { s -> withContext(Dispatchers.Default) { CopyUebersicht.positionen(s.geschaefte, s.bestaende).take(40) } }
    }
    val bestand = g == UebersichtGruppe.INVESTOREN || g == UebersichtGruppe.INSIDER
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { Erklaerung(S.cuErklaerung(if (bestand) "POSITIONEN_BESTAND" else "POSITIONEN")) }
        val p = positionen
        when {
            fehler -> item { EmptyHint(S.cuLeer) }
            p == null -> item { CopyLade() }
            p.isEmpty() -> item { EmptyHint(S.cuLeer) }
            else -> items(p, key = { "p-" + it.symbol }) { PositionZeile(state, it, onOpen) }
        }
        item { QuellenZeile(S.cuQuelle, Modifier.padding(top = 12.dp, bottom = 40.dp)) }
    }
}

@Composable
private fun PositionZeile(state: AppState, p: CopyUebersicht.Position, onOpen: (String) -> Unit) {
    val name = CopyRouten.katalogName(p.symbol) ?: p.symbol
    Row(Modifier.fillMaxWidth().clickable { onOpen(p.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        InstrumentLogo(state, p.symbol, name, size = 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, zusatz = " (${p.symbol})".takeIf { name != p.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
            PunktText(S.cuPersonen(p.personen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Groesste Halter je eine Zeile, Name und Betrag bleiben zusammen
            for (h in p.halter) PunktText(
                ungeteilt(h.name + " " + Fmt.dollarKompakt(h.wert)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxZeilen = 2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(Fmt.dollarKompakt(p.wert), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun GehandeltSeite(state: AppState, satz: CopyUebersichtDaten.Satz?, fehler: Boolean, liste: LazyListState, onOpen: (String) -> Unit) {
    var tage by rememberSaveable { mutableStateOf(90) }
    var ordnung by rememberSaveable { mutableStateOf(CopyUebersicht.Ordnung.ANZAHL.name) }
    val umsatz by produceState<List<CopyUebersicht.Umsatz>?>(null, satz, tage, ordnung) {
        value = satz?.let { s ->
            withContext(Dispatchers.Default) { CopyUebersicht.meistgehandelt(s.geschaefte, System.currentTimeMillis(), tage, CopyUebersicht.Ordnung.valueOf(ordnung)).take(40) }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { Erklaerung(S.cuErklaerung("GEHANDELT")) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CopyUebersicht.ZEITRAEUME.forEach { t -> Chip(S.kaFensterKurz(t), active = tage == t) { tage = t } }
                Chip(S.cuNachAnzahl, active = ordnung == CopyUebersicht.Ordnung.ANZAHL.name) { ordnung = CopyUebersicht.Ordnung.ANZAHL.name }
                Chip(S.cuNachVolumen, active = ordnung == CopyUebersicht.Ordnung.VOLUMEN.name) { ordnung = CopyUebersicht.Ordnung.VOLUMEN.name }
            }
        }
        val u = umsatz
        when {
            fehler -> item { EmptyHint(S.cuLeer) }
            u == null -> item { CopyLade() }
            u.isEmpty() -> item { EmptyHint(S.cuLeerZeitraum) }
            else -> items(u, key = { "u-" + it.symbol }) { UmsatzZeile(state, it, onOpen) }
        }
        item { QuellenZeile(S.cuQuelle, Modifier.padding(top = 12.dp, bottom = 40.dp)) }
    }
}

@Composable
private fun UmsatzZeile(state: AppState, u: CopyUebersicht.Umsatz, onOpen: (String) -> Unit) {
    val name = CopyRouten.katalogName(u.symbol) ?: u.symbol
    Row(Modifier.fillMaxWidth().clickable { onOpen(u.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        InstrumentLogo(state, u.symbol, name, size = 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, zusatz = " (${u.symbol})".takeIf { name != u.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
            PunktText(
                listOfNotNull(
                    if (u.kaeufe > 0) S.cuKaeufeAnzahl(u.kaeufe) else null,
                    if (u.verkaeufe > 0) S.cuVerkaeufeAnzahl(u.verkaeufe) else null,
                    S.cuPersonen(u.personen),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        // Kaeufe und Verkaeufe getrennt, in den Farben fuer Gewinn und Verlust
        Column(horizontalAlignment = Alignment.End) {
            if (u.kaeufe > 0) RichtungsBetrag(true, false, Fmt.dollarKompakt(u.kaufVolumen))
            if (u.verkaeufe > 0) RichtungsBetrag(false, true, Fmt.dollarKompakt(u.verkaufVolumen))
        }
    }
}

@Composable
private fun EinstiegSeite(
    state: AppState,
    einstieg: CopyUebersichtDaten.EinstiegErgebnis?,
    fortschritt: Float?,
    datenDa: Boolean,
    fehler: Boolean,
    liste: LazyListState,
    onOpen: (String) -> Unit,
) {
    val abschlaege by produceState<List<CopyUebersicht.Abschlag>?>(null, einstieg) {
        value = einstieg?.let { e -> withContext(Dispatchers.Default) { CopyUebersicht.abschlaege(e.einstaende).take(50) } }
    }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { Erklaerung(S.cuErklaerung("EINSTIEG")) }
        val a = abschlaege
        when {
            fehler -> item { EmptyHint(S.cuLeer) }
            einstieg == null || a == null -> item { if (datenDa || fortschritt != null) UebersichtLaden(fortschritt) else CopyLade() }
            else -> {
                if (einstieg.keineKurse) item { Erklaerung(S.cuKeineKurse) }
                else if (einstieg.veraltet) item { Erklaerung(S.cuStand(Fmt.relative(einstieg.stand))) }
                if (a.isEmpty() && !einstieg.keineKurse) item { EmptyHint(S.cuKeineUnter) }
                items(a, key = { "a-" + it.symbol }) { AbschlagZeile(state, it, onOpen) }
            }
        }
        item { QuellenZeile(S.cuQuelle, Modifier.padding(top = 12.dp, bottom = 40.dp)) }
    }
}

@Composable
private fun AbschlagZeile(state: AppState, a: CopyUebersicht.Abschlag, onOpen: (String) -> Unit) {
    val name = CopyRouten.katalogName(a.symbol) ?: a.symbol
    Row(Modifier.fillMaxWidth().clickable { onOpen(a.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        InstrumentLogo(state, a.symbol, name, size = 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, zusatz = " (${a.symbol})".takeIf { name != a.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
            PunktText(S.cuKaeufer(a.kaeufer) + " · " + Fmt.dollarKompakt(a.betrag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Groesste Kaeufer je eine Zeile mit ihrem eigenen Abstand zum Einstieg
            for ((n, ab) in a.namen) PunktText(
                ungeteilt(n + " " + Fmt.signedPct(ab * 100, 1)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxZeilen = 2,
            )
        }
        Spacer(Modifier.width(12.dp))
        ChangeValue(a.abstand, Fmt.signedPct(a.abstand * 100, 1))
    }
}

// Abschnitt „Unter Einstiegskurs“ auf der Seite einer Person aus dem Kongress oder der Regierung.
@Composable
internal fun UnterEinstiegskurs(state: AppState, geschaefte: List<Kongress.Geschaeft>, onOpen: (String) -> Unit) {
    val kaeufe = remember(geschaefte) { geschaefte.mapNotNull { CopyUebersichtDaten.geschaeft("p", "", it) } }
    UnterEinstiegsAbschnitt(state, kaeufe, onOpen)
}

// Dasselbe fuer fertig umgerechnete Geschaefte (Investoren). Je gekaufter Aktie, die noch gehalten wird und heute unter
// dem Einstiegskurs steht: Abstand, Kaufdatum und Betrag.
@Composable
internal fun UnterEinstiegsAbschnitt(state: AppState, kaeufe: List<CopyUebersicht.Geschaeft>, onOpen: (String) -> Unit) {
    if (kaeufe.none { it.kauf }) return
    var erg by remember(kaeufe) { mutableStateOf<CopyUebersichtDaten.EinstiegErgebnis?>(null) }
    var fertig by remember(kaeufe) { mutableStateOf(false) }
    LaunchedEffect(kaeufe) {
        erg = runCatching { CopyUebersichtDaten.einstieg(state, kaeufe) }.getOrNull()
        fertig = true
    }
    val unter = remember(erg) { erg?.let { CopyUebersicht.unterEinstieg(it.einstaende) }.orEmpty() }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    SectionHeader(S.cuSeite("EINSTIEG"))
    val e = erg
    when {
        !fertig -> CopyLade()
        e == null || e.keineKurse -> Text(S.cuKeineKurse, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp))
        else -> {
            if (e.veraltet) Text(S.cuStand(Fmt.relative(e.stand)), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (unter.isEmpty()) Text(S.cuKeineUnter, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp))
            unter.take(15).forEach { z ->
                val name = CopyRouten.katalogName(z.symbol) ?: z.symbol
                Row(Modifier.fillMaxWidth().clickable { onOpen(z.symbol) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        NamenText(name, zusatz = " (${z.symbol})".takeIf { name != z.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        val betrag = Fmt.dollarKompakt(z.betrag)
                        PunktText(
                            if (z.kaeufe == 1) S.cuKaufAm(Fmt.date(z.erstes), betrag) else S.cuKaeufeBis(z.kaeufe, Fmt.date(z.letztes), betrag),
                            style = MaterialTheme.typography.bodySmall, color = grau,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    ChangeValue(z.abstand, Fmt.signedPct(z.abstand * 100, 1))
                }
            }
            Text(S.cuErklaerung("EINSTIEG_PERSON"), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
    }
}
