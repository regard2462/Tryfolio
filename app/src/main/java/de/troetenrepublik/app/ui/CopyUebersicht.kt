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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.KongressAuswertung
import de.troetenrepublik.app.engine.KurseSpeicher
import de.troetenrepublik.app.engine.ListenKern
import de.troetenrepublik.app.engine.SplitFaktoren
import de.troetenrepublik.app.i18n.GeschaeftTexte
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

// Stand der Splitlisten fuer Anzeigen, die Investorenquartale vergleichen; holt die Listen der verdaechtigen Werte einmal am Tag.
// Aendert sich der Stand, rechnen die Anzeigen neu.
@Composable
internal fun splitStand(state: AppState): Int {
    LaunchedEffect(Unit) { runCatching { state.splitsHolen(SplitFaktoren.kandidaten(state.investoren, CopyRouten::symbol)) } }
    return SplitFaktoren.stand.collectAsState().value
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
        CopyNamen.merken(CopyRouten.symbol(k), g.wert)
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
            UebersichtGruppe.INVESTOREN -> {
                runCatching { state.splitsHolen(SplitFaktoren.kandidaten(state.investoren, CopyRouten::symbol)) }
                for (inv in state.investoren) for (q in listOfNotNull(inv.aktuell, inv.vorher)) for (pos in q.positionen) {
                    pos.kuerzel?.takeIf { it.isNotBlank() }?.let { CopyNamen.merken(CopyRouten.symbol(it), pos.name) }
                }
                CopyUebersicht.ausInvestoren(state.investoren, CopyRouten::symbol).let { Satz(it.first, it.second) }
            }
            UebersichtGruppe.INSIDER -> {
                runCatching { state.secOhneNetz() }
                val meldungen = state.sec.value?.geschaefte.orEmpty()
                for (m in meldungen) if (m.k.isNotBlank()) CopyNamen.merken(CopyRouten.symbol(m.k), m.e)
                CopyUebersicht.ausInsidern(meldungen, CopyRouten::symbol).let { Satz(it.first, it.second) }
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
        // Ganz verkaufte Positionen (je Person und Aktie die juengste abgeschlossene Runde).
        val verkauft: List<CopyUebersicht.Einstand> = emptyList(),
        // Verkaeufe ohne Kauf davor (ohne Einstiegskurs).
        val ohneKauf: List<CopyUebersicht.Verkauf> = emptyList(),
    )

    // Fuer so viele Aktien, die nur verkauft wurden, wird der heutige Kurs geholt (die mit den groessten Verkaeufen).
    private const val MAX_NUR_VERKAUFT = 60
    const val JAHR_MS = 365L * 24 * 3_600_000L

    // Einstiegskurse zu den Geschaeften: Kurse gebuendelt holen (siehe CopyKurse), dann rechnen.
    // verkaufAb: ab diesem Tag zaehlen Verkaeufe ohne Kauf davor; ohneKauf = false laesst sie weg (Investoren: dort sind
    // „Verkaeufe“ Aenderungen gegenueber dem Vorquartal und stehen auf der Seite des Investors).
    suspend fun einstieg(
        state: AppState, geschaefte: List<CopyUebersicht.Geschaeft>, verkaufAb: Long = 0L, ohneKauf: Boolean = true, fortschritt: (Float) -> Unit = {},
    ): EinstiegErgebnis =
        withContext(Dispatchers.Default) {
            val jetzt = System.currentTimeMillis()
            val kaeufe = geschaefte.filter { it.kauf }
            val gekauft = kaeufe.map { it.symbol }.toSet()
            val symbole = kaeufe.groupBy { it.symbol }.entries.sortedByDescending { e -> e.value.sumOf { it.betrag } }.take(MAX_AKTIEN).map { it.key }.toSet()
            // Aktien, die nur verkauft wurden: Geschaefte zaehlen alle, der heutige Kurs kommt fuer die groessten mit Preis
            val nurVerkauft = if (ohneKauf) geschaefte.filter { !it.kauf && it.symbol !in gekauft && it.datum >= verkaufAb } else emptyList()
            val nurSymbole = nurVerkauft.filter { (it.preis ?: 0.0) > 0 }.groupBy { it.symbol }.entries
                .sortedByDescending { e -> e.value.sumOf { it.betrag } }.take(MAX_NUR_VERKAUFT).map { it.key }.toSet()
            // Wie weit zurueck Kurse noetig sind: bis zum aeltesten Kauf (auch mit eigenem Preis: der Preis der Meldung
            // steht in der Stueckelung seines Tages und wird gegen den bereinigten Kurs geprueft)
            val bedarf = HashMap<String, Long>()
            for (g in kaeufe) if (g.symbol in symbole) bedarf.merge(g.symbol, g.datum) { a, b -> minOf(a, b) }
            for (g in nurVerkauft) if (g.symbol in nurSymbole) bedarf.merge(g.symbol, g.datum) { a, b -> minOf(a, b) }
            val k = CopyKurse.laden(state, bedarf, fortschritt)
            // Weicht der Preis einer Meldung deutlich vom bereinigten Schlusskurs des Tages ab, liegt ein Split dazwischen:
            // nur dann werden die Splits des Werts geholt
            val benutzt = geschaefte.filter { it.symbol in symbole } + nurVerkauft
            val verdaechtig = benutzt.filter { g ->
                val p = g.preis?.takeIf { it > 0 } ?: return@filter false
                if (g.symbol !in k.kurse) return@filter false
                val c = k.kurse[g.symbol]?.am(g.datum)?.takeIf { it > 0 } ?: return@filter true
                p / c < CopyUebersicht.PREIS_MIN || p / c > CopyUebersicht.PREIS_MAX
            }.map { it.symbol }.distinct()
            if (verdaechtig.isNotEmpty()) runCatching { state.splitsHolen(verdaechtig) }
            // Bleibt ein Preis nach den Splits unerklaert, nimmt die Rechnung den Schlusskurs des Tages. Ohne Schlusskurs
            // und ohne Splitdaten (Abruf fehlgeschlagen) zaehlt der Preis nicht; Aktien ganz ohne Kurse behalten ihn,
            // dort wird nichts verglichen.
            val e = CopyUebersicht.einstiege(
                benutzt, { s, t -> k.kurse[s]?.am(t) }, { s -> k.kurse[s]?.letzter }, verkaufAb = verkaufAb,
                splitBekannt = { s -> s !in k.kurse || SplitFaktoren.bekannt(s) },
            )
            EinstiegErgebnis(e.gehalten, k.stand, k.veraltet, symbole.isNotEmpty() && k.kurse.isEmpty(), e.verkauft, if (ohneKauf) e.ohneKauf else emptyList())
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

// Kernaussage in einer Zeile, die Erklaerung dahinter liegt im Blatt hinter dem Info-Symbol.
@Composable
private fun KurzMitInfo(kurz: String, titel: String, text: String) {
    var offen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(kurz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        InfoKnopf { offen = true }
    }
    if (offen) MarktInfoBlatt(titel, text) { offen = false }
}

@Composable
private fun InfoKnopf(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        androidx.compose.material3.Icon(
            Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Abschnittskopf mit Info-Symbol rechts.
@Composable
private fun KopfMitInfo(titel: String, text: String) {
    var offen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { SectionHeader(titel) }
        Row(Modifier.padding(end = 8.dp)) { InfoKnopf { offen = true } }
    }
    if (offen) MarktInfoBlatt(titel, text) { offen = false }
}

// Namen der Aktien aus den Meldungen selbst (Emittent laut Form 4, Bezeichnung in der Meldung, Position im 13F), fuer
// Werte, die der Katalog der App nicht kennt.
internal object CopyNamen {
    private val namen = ConcurrentHashMap<String, String>()

    fun merken(symbol: String, name: String) { if (symbol.isNotBlank() && name.isNotBlank()) namen.putIfAbsent(symbol, name) }

    // Name aus dem Katalog, sonst aus den Meldungen (ohne Zusatz zur Papierart und ohne Grossschrift), sonst das Kuerzel.
    fun titel(symbol: String, sonst: String? = null): String =
        CopyRouten.katalogName(symbol) ?: sonst ?: namen[symbol]?.let { CopyRouten.name(null, it) } ?: symbol
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
        // Verkaeufe ohne Kauf davor aus zwoelf Monaten (groesster Zeitraum von „Meistgehandelt“)
        einstieg = runCatching {
            CopyUebersichtDaten.einstieg(state, s.geschaefte, System.currentTimeMillis() - CopyUebersichtDaten.JAHR_MS, g != UebersichtGruppe.INVESTOREN) { fortschritt = it }
        }.getOrNull()
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
        value = satz?.let { s -> withContext(Dispatchers.Default) { CopyUebersicht.positionen(s.geschaefte, s.bestaende) } }
    }
    var alle by rememberSaveable { mutableStateOf(false) }
    val bestand = g == UebersichtGruppe.INVESTOREN || g == UebersichtGruppe.INSIDER
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { (if (bestand) "POSITIONEN_BESTAND" else "POSITIONEN").let { k -> KurzMitInfo(S.cuKurz(k), S.cuSeite("POSITIONEN"), S.cuErklaerung(k)) } }
        val p = positionen
        when {
            fehler -> item { EmptyHint(S.cuLeer) }
            p == null -> item { CopyLade() }
            p.isEmpty() -> item { EmptyHint(S.cuLeer) }
            else -> {
                items(if (alle) p else p.take(40), key = { "p-" + it.symbol }) { PositionZeile(state, it, onOpen) }
                if (p.size > 40) item(key = "p-alle") { ListenPfeil(alle, label = if (alle) null else S.bbAlle(p.size)) { alle = !alle } }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun PositionZeile(state: AppState, p: CopyUebersicht.Position, onOpen: (String) -> Unit) {
    val name = CopyNamen.titel(p.symbol)
    Column(Modifier.fillMaxWidth().clickable { onOpen(p.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            InstrumentLogo(state, p.symbol, name, size = 36)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NamenText(name, zusatz = " (${p.symbol})".takeIf { name != p.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
                PunktText(S.cuPersonen(p.personen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(ungeteilt(Fmt.dollarKompakt(p.wert)), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
        // Groesste Halter je eine Zeile ueber die ganze Breite
        PersonenZeilen(p.halter.map { it.name to Fmt.dollarKompakt(it.wert) })
    }
}

// Personen unter dem Kopf einer Aktie, je eine Zeile ueber die ganze Breite: Name links (bricht zwischen Woertern um),
// Wert rechts in einem Stueck. Gemeinsam fuer „Groesste Positionen“ und „Unter Einstiegskurs“ aller Gruppen.
@Composable
private fun PersonenZeilen(zeilen: List<Pair<String, String>>) {
    if (zeilen.isEmpty()) return
    val klein = MaterialTheme.typography.bodySmall
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        for ((n, wert) in zeilen) Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
            Text(n, style = klein, color = grau, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(ungeteilt(wert), style = klein, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun GehandeltSeite(state: AppState, satz: CopyUebersichtDaten.Satz?, fehler: Boolean, liste: LazyListState, onOpen: (String) -> Unit) {
    var tage by rememberSaveable { mutableStateOf(90) }
    var ordnung by rememberSaveable { mutableStateOf(CopyUebersicht.Ordnung.ANZAHL.name) }
    var alle by rememberSaveable { mutableStateOf(false) }
    val umsatz by produceState<List<CopyUebersicht.Umsatz>?>(null, satz, tage, ordnung) {
        value = satz?.let { s ->
            withContext(Dispatchers.Default) { CopyUebersicht.meistgehandelt(s.geschaefte, System.currentTimeMillis(), tage, CopyUebersicht.Ordnung.valueOf(ordnung)) }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { KurzMitInfo(S.cuKurz("GEHANDELT"), S.cuSeite("GEHANDELT"), S.cuErklaerung("GEHANDELT")) }
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
            else -> {
                items(if (alle) u else u.take(40), key = { "u-" + it.symbol }) { UmsatzZeile(state, it, onOpen) }
                if (u.size > 40) item(key = "u-alle") { ListenPfeil(alle, label = if (alle) null else S.bbAlle(u.size)) { alle = !alle } }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun UmsatzZeile(state: AppState, u: CopyUebersicht.Umsatz, onOpen: (String) -> Unit) {
    val name = CopyNamen.titel(u.symbol)
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
        value = einstieg?.let { e -> withContext(Dispatchers.Default) { CopyUebersicht.abschlaege(e.einstaende) } }
    }
    // Ganz verkaufte Positionen je Aktie, eigener Abschnitt unter den gehaltenen
    val ausstiege by produceState<List<CopyUebersicht.Ausstieg>>(emptyList(), einstieg) {
        value = einstieg?.let { e -> withContext(Dispatchers.Default) { CopyUebersicht.ausstiege(e.verkauft).take(50) } }.orEmpty()
    }
    val abgaben by produceState<List<CopyUebersicht.Abgabe>>(emptyList(), einstieg) {
        value = einstieg?.let { e -> withContext(Dispatchers.Default) { CopyUebersicht.abgaben(e.ohneKauf).take(50) } }.orEmpty()
    }
    var wegOffen by rememberSaveable { mutableStateOf(false) }
    var alleUnter by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item { KurzMitInfo(S.cuKurz("EINSTIEG"), S.cuSeite("EINSTIEG"), S.cuErklaerung("EINSTIEG") + "\n\n" + S.cuErklaerung("EINSTIEG_VERKAUFT") + "\n\n" + GeschaeftTexte.t("farben")) }
        val a = abschlaege
        when {
            fehler -> item { EmptyHint(S.cuLeer) }
            einstieg == null || a == null -> item { if (datenDa || fortschritt != null) UebersichtLaden(fortschritt) else CopyLade() }
            else -> {
                if (einstieg.keineKurse) item { Erklaerung(S.cuKeineKurse) }
                else if (einstieg.veraltet) item { Erklaerung(S.cuStand(Fmt.relative(einstieg.stand))) }
                if (a.isEmpty() && !einstieg.keineKurse) item { EmptyHint(S.cuKeineUnter) }
                items(if (alleUnter) a else a.take(50), key = { "a-" + it.symbol }) { AbschlagZeile(state, it, onOpen) }
                if (a.size > 50) item(key = "a-alle") { ListenPfeil(alleUnter, label = if (alleUnter) null else S.bbAlle(a.size)) { alleUnter = !alleUnter } }
                // Verkauft: zuerst ganz verkaufte Positionen mit Einstiegskurs, dann Verkaeufe ohne Kauf davor
                val w = ausstiege
                val v = abgaben
                if (w.isNotEmpty() || v.isNotEmpty()) {
                    val lohnt = ListenKern.lohnt(w.size + v.size)
                    item(key = "weg-kopf", contentType = "kern-kopf") {
                        if (lohnt) KernKopf(S.ieVerkauft, KernZeile(listOf(KernTeil(S.ieWerte(w.size + v.size)))), wegOffen, gross = false) { wegOffen = !wegOffen }
                        else Zwischenkopf(S.ieVerkauft)
                    }
                    if (!lohnt || wegOffen) {
                        items(w, key = { "w-" + it.symbol }, contentType = { "ausstieg" }) { AusstiegZeile(state, it, onOpen) }
                        items(v, key = { "v-" + it.symbol }, contentType = { "abgabe" }) { AbgabeZeile(state, it, onOpen) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun AbschlagZeile(state: AppState, a: CopyUebersicht.Abschlag, onOpen: (String) -> Unit) {
    val name = CopyNamen.titel(a.symbol)
    Column(Modifier.fillMaxWidth().clickable { onOpen(a.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            InstrumentLogo(state, a.symbol, name, size = 36)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NamenText(name, zusatz = " (${a.symbol})".takeIf { name != a.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
                PunktText(S.cuKaeufer(a.kaeufer) + " · " + ungeteilt(Fmt.dollarKompakt(a.betrag)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            SeitGeschaeft(a.abstand, verkauf = false)
        }
        // Groesste Kaeufer je eine Zeile mit ihrem eigenen Abstand zum Einstieg
        PersonenZeilen(a.namen.map { (n, ab, r) -> (n + (reduziertText(r)?.let { PUNKT + it } ?: "")) to Fmt.signedPct(ab * 100, 1) })
    }
}

// „Bestand um 40 % reduziert“ fuer eine gehaltene Position mit Verkaeufen; null ohne nennenswerten Verkauf.
private fun reduziertText(r: Double): String? = if (r < 0.005) null else ungeteilt(S.ieReduziert(Fmt.pct(r * 100, 0)))

// Eine Aktie, die Personen der Gruppe nach einem Kauf ganz verkauft haben.
@Composable
private fun AusstiegZeile(state: AppState, a: CopyUebersicht.Ausstieg, onOpen: (String) -> Unit) {
    val name = CopyNamen.titel(a.symbol)
    Column(Modifier.fillMaxWidth().clickable { onOpen(a.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            InstrumentLogo(state, a.symbol, name, size = 36)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NamenText(name, zusatz = " (${a.symbol})".takeIf { name != a.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
                PunktText(
                    S.cuPersonen(a.personen) + PUNKT + S.ieEinstieg(ungeteilt(Fmt.price(a.einstieg, "$"))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            SeitGeschaeft(a.abstand, verkauf = false)
        }
        PersonenZeilen(a.namen.map { (n, t) -> n to S.ieVerkauftAm(Fmt.date(t)) })
    }
}

// Eine Aktie, die Personen der Gruppe ohne Kauf davor verkauft haben: ohne Einstiegskurs, dafuer Summe der Verkaeufe
// und, wenn die Meldungen den Preis nennen, der heutige Kurs gegen den Verkaufskurs.
@Composable
private fun AbgabeZeile(state: AppState, a: CopyUebersicht.Abgabe, onOpen: (String) -> Unit) {
    val name = CopyNamen.titel(a.symbol)
    Column(Modifier.fillMaxWidth().clickable { onOpen(a.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            InstrumentLogo(state, a.symbol, name, size = 36)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NamenText(name, zusatz = " (${a.symbol})".takeIf { name != a.symbol }.orEmpty(), style = MaterialTheme.typography.titleSmall)
                PunktText(
                    listOfNotNull(
                        S.cuPersonen(a.personen),
                        ungeteilt(S.ieVerkauftAm(Fmt.date(a.letzter))),
                        ungeteilt(S.ieVerkaeufe(Fmt.dollarKompakt(a.betrag))),
                        a.verkaufskurs?.let { ungeteilt(S.ieVerkaufskurs(Fmt.price(it, "$"))) },
                    ).joinToString(PUNKT),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            a.abstand?.let {
                Spacer(Modifier.width(12.dp))
                SeitGeschaeft(it, verkauf = true)
            }
        }
        PersonenZeilen(a.namen.map { (n, b, r) -> (n + (r?.let { x -> reduziertText(x) }?.let { PUNKT + it } ?: "")) to Fmt.dollarKompakt(b) })
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
internal fun UnterEinstiegsAbschnitt(
    state: AppState, kaeufe: List<CopyUebersicht.Geschaeft>, onOpen: (String) -> Unit, quartal: Boolean = false, namen: (String) -> String? = { null },
) {
    if (kaeufe.isEmpty() || (quartal && kaeufe.none { it.kauf })) return
    var erg by remember(kaeufe) { mutableStateOf<CopyUebersichtDaten.EinstiegErgebnis?>(null) }
    var fertig by remember(kaeufe) { mutableStateOf(false) }
    LaunchedEffect(kaeufe) {
        erg = runCatching { CopyUebersichtDaten.einstieg(state, kaeufe, ohneKauf = !quartal) }.getOrNull()
        fertig = true
    }
    val unter = remember(erg) { erg?.let { CopyUebersicht.unterEinstieg(it.einstaende) }.orEmpty() }
    val weg = remember(erg) { erg?.verkauft.orEmpty().sortedByDescending { it.verkauftAm }.take(30) }
    val ohne = remember(erg) { erg?.ohneKauf.orEmpty().sortedByDescending { it.betrag }.take(30) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var alleUnter by rememberSaveable { mutableStateOf(false) }
    KopfMitInfo(
        S.cuSeite("EINSTIEG"),
        S.cuErklaerung(if (quartal) "EINSTIEG_INVESTOR" else "EINSTIEG_PERSON") + if (quartal) "" else "\n\n" + S.cuErklaerung("EINSTIEG_VERKAUFT_PERSON") +
            "\n\n" + GeschaeftTexte.t("farben") + "\n\n" + GeschaeftTexte.t("gewinnBeleg"),
    )
    val e = erg
    when {
        !fertig -> CopyLade()
        e == null || (e.keineKurse && ohne.isEmpty()) -> Text(S.cuKeineKurse, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp))
        else -> {
            if (e.veraltet) Text(S.cuStand(Fmt.relative(e.stand)), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (unter.isEmpty()) Text(S.cuKeineUnter, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp))
            (if (alleUnter) unter else unter.take(15)).forEach { z ->
                val name = CopyNamen.titel(z.symbol, namen(z.symbol))
                val hint = when {
                    quartal && z.kaeufe == 1 -> GeschaeftTexte.t("quartal.bis", Fmt.date(z.erstes))
                    quartal -> GeschaeftTexte.t("quartale.bis", z.kaeufe.toString(), Fmt.date(z.letztes))
                    z.kaeufe == 1 -> GeschaeftTexte.t("kauf.datum", Fmt.date(z.erstes))
                    else -> GeschaeftTexte.t("kaeufe.bis", z.kaeufe.toString(), Fmt.date(z.letztes))
                }
                KaeuferZeile(name, " (${z.symbol})".takeIf { name != z.symbol }.orEmpty(), hint, z, "$") { onOpen(z.symbol) }
            }
            if (unter.size > 15) ListenPfeil(alleUnter, label = if (alleUnter) null else S.bbAlle(unter.size)) { alleUnter = !alleUnter }
            // Nach einem Kauf ganz Verkauftes bleibt sichtbar, eingeklappt ab vier Werten
            // darunter Verkaeufe ohne Kauf davor, groesste Summe zuerst
            if (weg.isNotEmpty() || ohne.isNotEmpty()) KernAbschnitt(
                S.ieVerkauft, KernZeile(listOf(KernTeil(S.ieWerte(weg.size + ohne.size)))), null, "einstieg-verkauft", gross = false,
                lohnt = ListenKern.lohnt(weg.size + ohne.size),
            ) {
                weg.forEach { z ->
                    val name = CopyNamen.titel(z.symbol, namen(z.symbol))
                    AusgestiegenZeile(name, " (${z.symbol})".takeIf { name != z.symbol }.orEmpty(), z, "$") { onOpen(z.symbol) }
                }
                ohne.forEach { v ->
                    val name = CopyNamen.titel(v.symbol, namen(v.symbol))
                    VerkaufsZeile(name, " (${v.symbol})".takeIf { name != v.symbol }.orEmpty(), v, Fmt.dollarKompakt(v.betrag), "$") { onOpen(v.symbol) }
                }
            }
        }
    }
}

// „Unter Einstiegskurs“ auf der Seite einer Aktie (0.78): je Insider, der am Markt gekauft hat und laut Meldungen noch
// haelt, der Einstiegskurs und der Abstand zum heutigen Kurs. Gemeinsam fuer SEC, BaFin und die Register weiterer
// Laender; gezeigt werden die Personen unter ihrem Einstieg. waehrung: Waehrung der Kurse und der Meldungen.
// Mit Merker gehen die Zeilen an die Liste der Seite (listenAbschnitt mit "$id-einstieg"), sonst zeichnet die Karte selbst.
@Composable
internal fun InsiderEinstiegKarte(
    state: AppState, symbol: String, id: String, geschaefte: List<CopyUebersicht.Geschaeft>, waehrung: String, merker: SeitenMerker?,
) {
    // Ergebnis zu genau diesen Geschaeften: beim Zurueckscrollen wird nicht neu gerechnet
    var stand by gemerkt<Pair<List<CopyUebersicht.Geschaeft>, CopyUebersichtDaten.EinstiegErgebnis?>?>(merker, "einstieg/$id/$symbol") { null }
    LaunchedEffect(geschaefte) {
        if (stand?.first == geschaefte) return@LaunchedEffect
        // Verkaeufe ohne Kauf davor aus zwoelf Monaten, dem Zeitraum der Meldungen auf der Seite
        stand = geschaefte to if (geschaefte.isEmpty()) null
            else runCatching { CopyUebersichtDaten.einstieg(state, geschaefte, System.currentTimeMillis() - CopyUebersichtDaten.JAHR_MS) }.getOrNull()
    }
    val erg = stand?.takeIf { it.first == geschaefte }?.second
    val zeichen = Derivatives.currencySymbol(waehrung)
    // Wer nach einem Kauf alles verkauft hat, steht darunter in einem eigenen Abschnitt und zaehlt oben nicht mit
    val verkauft = remember(erg, zeichen) {
        val weg = erg?.verkauft.orEmpty().sortedByDescending { it.verkauftAm }.take(30)
        val ohne = erg?.ohneKauf.orEmpty().sortedByDescending { it.betrag }.take(30)
        if (weg.isEmpty() && ohne.isEmpty()) null else ListenModell(
            titel = S.ieVerkauft,
            gross = false,
            kern = KernZeile(listOf(KernTeil(S.cuPersonen(weg.size + ohne.size)))),
            zeilen = weg.mapIndexed { i, z -> ZeilenDaten("$i") { AusgestiegenZeile(z.name, "", z, zeichen) } } +
                ohne.mapIndexed { i, v -> ZeilenDaten("o$i") { VerkaufsZeile(v.name, "", v, Fmt.mitSymbol(Fmt.compact(v.betrag), zeichen), zeichen) } },
            info = S.cuErklaerung("EINSTIEG_VERKAUFT") + "\n\n" + GeschaeftTexte.t("farben") + "\n\n" + GeschaeftTexte.t("gewinnBeleg"),
        )
    }
    val modell = remember(erg, zeichen) {
        val alle = erg?.einstaende.orEmpty()
        if (alle.isEmpty()) return@remember null
        val unter = alle.filter { it.abstand < 0 }.sortedBy { it.abstand }
        ListenModell(
            titel = S.cuSeite("EINSTIEG"),
            gross = false,
            kern = KernZeile(listOf(KernTeil(S.ieKern(unter.size, alle.size)))),
            zeilen = unter.mapIndexed { i, z -> ZeilenDaten("$i") { InsiderEinstiegZeile(z, zeichen) } },
            kappe = 15,
            nachspann = if (unter.isEmpty()) ({
                Text(S.ieKeiner(alle.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }) else null,
            info = S.cuErklaerung("EINSTIEG_INSIDER") + "\n\n" + GeschaeftTexte.t("farben"),
        )
    }
    ModellAusgabe(merker, "$id-einstieg", modell)
    ModellAusgabe(merker, "$id-verkauft", verkauft)
}

// Eine Person: Name links (bricht um), Kurs seit dem Kauf rechts; darunter Einstieg und Tag, Kaufbetrag mit Wert heute.
@Composable
private fun InsiderEinstiegZeile(z: CopyUebersicht.Einstand, zeichen: String) {
    val tag = if (z.kaeufe == 1) GeschaeftTexte.t("kauf.datum", Fmt.date(z.erstes)) else GeschaeftTexte.t("kaeufe.bis", z.kaeufe.toString(), Fmt.date(z.letztes))
    KaeuferZeile(z.name, "", S.ieEinstieg(Fmt.price(z.einstieg, zeichen)) + PUNKT + tag, z, zeichen)
}
