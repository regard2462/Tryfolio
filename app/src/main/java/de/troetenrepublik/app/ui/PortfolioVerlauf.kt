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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Reihe
import de.troetenrepublik.app.market.Portfolioverlauf.Waehrung
import de.troetenrepublik.app.market.Strategien
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

// Zeitraeume des Portfoliographen in Tagen; 0 = alles.
enum class VerlaufZeitraum(val tage: Int) { M1(30), M6(182), J1(365), J3(1096), J5(1826), MAX(0) }

// Laedt Portfolioverlaeufe fuer den Graphen und den Vergleich. Schluessel wie beim Folgen: „m:“ Massstab
// (Index oder Welt-ETF), „i:“ Investor, „s:“ Strategie, „k:“ Abgeordnete(r). Ergebnisse bleiben drei Stunden im
// Speicher; die Auswahl der Vergleiche gilt fuer alle Seiten, bis man sie aendert.
object VerlaufLader {

    private val cache = ConcurrentHashMap<String, Pair<Long, Pair<Reihe, Waehrung>>>()
    private val kongressCache = ConcurrentHashMap<String, Pair<Long, Portfolioverlauf.KongressVerlauf>>()
    private val gleichzeitig = Semaphore(4)
    private const val HALTBAR = 3 * 3_600_000L
    // Hoechstens so viele Werte mit Kursen im Verlauf einer Person.
    private const val MAX_WERTE = 80
    private const val PRAESIDENT = "r:trump"

    // Praesident: vorberechneter Verlauf aus allen gemeldeten Geschaeften, vom Stichtag bis heute mit den
    // Kursen des Bestands fortgeschrieben (je 20 Werte eine Abfrage, die Werte mit 95 Prozent des Bestands).
    // Null ohne mitgelieferte Datei; dann rechnet die App wie bei den Abgeordneten.
    private suspend fun praesident(state: AppState): Portfolioverlauf.KongressVerlauf? {
        val d = state.praesident ?: return null
        val werte = de.troetenrepublik.app.market.Praesident.fortschreibWerte(d)
        val bereich = de.troetenrepublik.app.market.Praesident.zeitraum(d.tage.last(), Portfolioverlauf.tag(System.currentTimeMillis()))
        val kurse = HashMap<String, List<Candle>>()
        coroutineScope {
            werte.chunked(20).map { teil -> async { gleichzeitig.withPermit { state.schlusskurse(teil, bereich) } } }.awaitAll()
        }.forEach { kurse.putAll(it) }
        val gehalten = d.copy(bestand = d.bestand.filter { it.k in werte })
        val reihe = de.troetenrepublik.app.market.Praesident.reihe(d) + de.troetenrepublik.app.market.Praesident.fortschreiben(gehalten, kurse)
        val z = d.zahlen
        fun n(x: Int) = Fmt.num(x.toDouble(), 0)
        fun wennDa(x: Int) = if (x > 0) n(x) else null
        val v = Portfolioverlauf.KongressVerlauf(
            reihe, de.troetenrepublik.app.market.Praesident.handel(d), z.ohneKurs, z.ohneBestand, d.bestand.map { it.k to it.g },
            hinweis = S.pvNotePresident(
                n(z.geschaefte), n(z.jahresmeldung), n(z.einzelmeldungen), n(z.eingerechnet), n(z.werte), n(z.anleihen),
                Fmt.pct(d.anleihen * 100, 0), wennDa(z.ohneKuerzel), wennDa(z.ohneKurs), wennDa(z.ohneBestand),
                Fmt.date(Portfolioverlauf.iso(d.stichtag)),
            ),
        )
        kongressCache[PRAESIDENT] = System.currentTimeMillis() to v
        cache["k:$PRAESIDENT"] = System.currentTimeMillis() to (v.reihe to Waehrung.USD)
        return v
    }

    // Zuletzt gewaehlte Vergleiche (fuer die naechste Seite).
    var zuletzt: List<String> = listOf("m:msci")

    data class Wahl(val schluessel: String, val name: String, val unter: String)

    // Was sich vergleichen laesst, in Gruppen: Massstaebe, Investoren, Strategien, gefolgte Abgeordnete.
    fun kandidaten(state: AppState, gefolgt: Set<String>): List<Pair<String, List<Wahl>>> {
        val de = I18n.lang == Lang.DE
        val personen = gefolgt.filter { it.startsWith("k:") || it.startsWith("n:") || it.startsWith("r:") }.map { Wahl(it, name(state, it), "") }
        val depot = if (state.account.value.positions.isNotEmpty() || state.account.value.transactions.any { it.type == TxType.BUY })
            S.ptYourDepot to listOf(Wahl(DEPOT, S.ptYourDepot, S.pvDepotSub)) else null
        return listOfNotNull(
            depot,
            S.pvGroupIndex to Portfolioverlauf.MASSSTAEBE.map { Wahl("m:" + it.id, it.name, it.symbol) },
            S.copyInvestors to state.investoren.filter { it.id in state.verlaeufe.investoren }.map { Wahl("i:" + it.id, it.name, it.firma) },
            S.copyStrategies to Strategien.alle.map { Wahl("s:" + it.id, if (de) it.nameDe else it.nameEn, "") },
            if (personen.isEmpty()) null else S.pvGroupMembers to personen,
        )
    }

    // Schluessel des eigenen Depots.
    const val DEPOT = "d:depot"

    fun name(state: AppState, schluessel: String): String {
        val id = schluessel.substringAfter(':')
        return when (schluessel.substringBefore(':')) {
            "d" -> S.ptYourDepot
            "m" -> Portfolioverlauf.MASSSTAEBE.firstOrNull { it.id == id }?.name
            "i" -> state.investoren.firstOrNull { it.id == id }?.name
            "s" -> Strategien.finde(id)?.let { if (I18n.lang == Lang.DE) it.nameDe else it.nameEn }
            "n" -> state.senat?.meldungen?.firstOrNull { it.person == id }?.name
            "r" -> state.regierung?.personen?.firstOrNull { it.id == id }?.name
            "k" -> state.copy?.stand?.value?.meldungen?.firstOrNull { it.person == id }?.name
                ?: id.split('|').let { "${it.getOrElse(1) { "" }.substringBefore(' ')} ${it[0]}".trim() }
            else -> null
        } ?: id
    }

    // Wie reihe, Massstaebe aber in der Aufloesung der Depotkurve: bei einem jungen Depot Kurse alle 30
    // Minuten statt einmal am Tag, damit der Vergleich so fein ist wie das Depot.
    suspend fun reiheWieDepot(state: AppState, schluessel: String): Pair<Reihe, Waehrung>? {
        if (!schluessel.startsWith("m:")) return reihe(state, schluessel)
        // Ab einem Jahr kaeme die Depotaufloesung als Wochenkerzen mit Sonntagsdatum, die auf der Handelszeit-Achse
        // wegfallen; dann die Tageskurse wie bei den anderen Vergleichen.
        val ersterKauf = de.troetenrepublik.app.engine.Trading.firstPurchase(state.account.value)
        if (ersterKauf <= 0L || System.currentTimeMillis() - ersterKauf > 370L * 86_400_000L) return reihe(state, schluessel)
        val k = "$schluessel#depot"
        cache[k]?.takeIf { System.currentTimeMillis() - it.first < 15 * 60_000L }?.let { return it.second }
        val m = Portfolioverlauf.MASSSTAEBE.firstOrNull { it.id == schluessel.substringAfter(':') } ?: return null
        val kerzen = state.benchmarkSeries(m.symbol, de.troetenrepublik.app.data.Range.MAX)
        val r = Portfolioverlauf.ausKursen(kerzen).takeIf { it.size >= 2 }?.let { it to Waehrung.EUR } ?: return reihe(state, schluessel)
        cache[k] = System.currentTimeMillis() to r
        return r
    }

    suspend fun reihe(state: AppState, schluessel: String): Pair<Reihe, Waehrung>? {
        // das eigene Depot aendert sich mit jedem Kauf: nur fuenf Minuten merken
        val haltbar = if (schluessel == DEPOT) 5 * 60_000L else HALTBAR
        cache[schluessel]?.takeIf { System.currentTimeMillis() - it.first < haltbar }?.let { return it.second }
        val id = schluessel.substringAfter(':')
        val r: Pair<Reihe, Waehrung>? = when (schluessel.substringBefore(':')) {
            "m" -> Portfolioverlauf.MASSSTAEBE.firstOrNull { it.id == id }?.let { m ->
                // Euro-Kurse; die Tageskurse der Kursliste (zum heutigen Kurs umgerechnet) haben dieselben Veraenderungen
                val kurse = state.kurseNativ(m.symbol).ifEmpty { state.dailyCloses(m.symbol) }
                Portfolioverlauf.ausKursen(kurse).takeIf { it.size >= 2 }?.let { it to Waehrung.EUR }
            }
            "i" -> investor(state, id)?.let { it to Waehrung.USD }
            "s" -> strategie(state, id)?.let { it to Waehrung.EUR }
            "k" -> kongress(state, id)?.reihe?.takeIf { it.size >= 2 }?.let { it to Waehrung.USD }
            "n" -> state.senat?.meldungen?.filter { it.person == id }?.takeIf { it.isNotEmpty() }?.let { m ->
                kongress(state, schluessel, de.troetenrepublik.app.market.Politik.daten(m).flatMap { it.second.orEmpty() })?.reihe?.takeIf { it.size >= 2 }?.let { it to Waehrung.USD }
            }
            "r" -> state.regierung?.personen?.firstOrNull { it.id == id }?.let { p ->
                kongress(state, schluessel, de.troetenrepublik.app.market.Politik.daten(p).flatMap { it.second.orEmpty() })?.reihe?.takeIf { it.size >= 2 }?.let { it to Waehrung.USD }
            }
            "d" -> depot(state)?.let { it to Waehrung.EUR }
            else -> null
        }
        if (r != null) cache[schluessel] = System.currentTimeMillis() to r
        return r
    }

    // Mitgelieferte Reihe in Dollar, danach mit den zehn groessten Aktien des letzten Quartals fortgeschrieben.
    suspend fun investor(state: AppState, id: String): Reihe? {
        val v = state.verlaeufe.investoren[id] ?: return null
        val reihe = v.usd.takeIf { it.size >= 2 } ?: return null
        if (System.currentTimeMillis() - reihe.last().first < 4 * 86_400_000L) return reihe
        val inv = state.investoren.firstOrNull { it.id == id } ?: return reihe
        val q = inv.aktuell ?: return reihe
        val gewichte = Investoren.gewichte(inv, 10).map { CopyRouten.symbol(it.first) to it.second }
        val kurse = coroutineScope { gewichte.map { (s, _) -> async { gleichzeitig.withPermit { s to state.kurseNativ(s) } } }.awaitAll() }.toMap()
        return Portfolioverlauf.fortschreiben(reihe, Portfolioverlauf.iso(q.stichtag), gewichte, kurse)
    }

    // Zeitgewichteter Verlauf des eigenen Depots ueber seine ganze Zeit: Wertpapiere zu echten Kursen,
    // Kaeufe und Verkaeufe veraendern den Verlauf nicht; in Euro, von 100 an.
    suspend fun depot(state: AppState): Reihe? {
        val parts = state.portfolioParts(de.troetenrepublik.app.data.Range.MAX) ?: return null
        val v = state.valuation.value
        val jetzt = System.currentTimeMillis()
        var werte = parts.raw.candles.map { it.t to it.close }
        if (v.positions.isNotEmpty() && v.securities > 0 && (werte.lastOrNull()?.first ?: 0L) < jetzt - 60_000L) werte = werte + (jetzt to v.securities)
        if (werte.size < 2) return null
        return de.troetenrepublik.app.engine.PortfolioTools.timeWeighted(werte, parts.flows).map { it.first to 100.0 + it.second }
            .takeIf { it.size >= 2 }
    }

    suspend fun strategie(state: AppState, id: String): Reihe? {
        val s = Strategien.finde(id) ?: return null
        val reihen = coroutineScope { s.anteile.map { (sym, _) -> async { sym to state.dailyCloses(sym) } }.awaitAll() }.toMap()
        return Strategien.kennzahlen(reihen, s.anteile, System.currentTimeMillis())?.verlauf?.map { it.first to it.second * 100 }
    }

    // Verlauf aus allen gemeldeten Geschaeften einer Person (Verzeichnis des laufenden und des Vorjahres).
    suspend fun kongress(state: AppState, schluessel: String, geschaefte: List<Kongress.Geschaeft>? = null): Portfolioverlauf.KongressVerlauf? {
        kongressCache[schluessel]?.takeIf { System.currentTimeMillis() - it.first < HALTBAR }?.let { return it.second }
        if (schluessel == PRAESIDENT) praesident(state)?.let { return it }
        val alle = geschaefte ?: run {
            val repo = state.copy ?: return null
            if (repo.stand.value.meldungen.isEmpty()) runCatching { repo.verzeichnis() }
            repo.geschaefte(repo.meldungenVon(schluessel, tage = 800)).flatMap { it.second.orEmpty() }
        }
        val handel = alle.flatMap { Portfolioverlauf.handelAlle(it) }
            .distinctBy { "${it.t}|${it.position}|${it.kauf}|${it.betrag}|${it.option}" }
        if (handel.isEmpty()) return null
        // Bei sehr vielen Werten nur die meistgehandelten mit Kursen, der Rest zaehlt als ohne Kurs
        val werte = handel.groupBy { it.kuerzel }.map { (k, l) -> k to l.sumOf { it.betrag } }.sortedByDescending { it.second }.take(MAX_WERTE).map { it.first }
        val kurse = coroutineScope {
            werte.map { k -> async { gleichzeitig.withPermit { k to state.kurseNativ(CopyRouten.symbol(k)) } } }.awaitAll()
        }.toMap()
        val v = Portfolioverlauf.kongress(handel, kurse, System.currentTimeMillis()) ?: return null
        kongressCache[schluessel] = System.currentTimeMillis() to v
        cache["k:$schluessel"] = System.currentTimeMillis() to (v.reihe to Waehrung.USD)
        return v
    }
}

// Kauf- und Verkaufsmarken aus den Geschaeften eines Abgeordneten: je Tag und Richtung eine Marke.
fun kongressMarken(v: Portfolioverlauf.KongressVerlauf): List<Marke> =
    v.handel.groupBy { Portfolioverlauf.tag(it.t) to it.kauf }.map { (k, l) ->
        val (tag, kauf) = k
        val werte = l.groupBy { it.kuerzel }.map { (s, h) -> s to h.sumOf { it.betrag } }.sortedByDescending { it.second }
            .joinToString(", ") { (s, b) -> "$s ≈ ${Fmt.dollarKompakt(b)}" }
        val datum = Fmt.date(Portfolioverlauf.ms(tag))
        Marke(Portfolioverlauf.ms(tag), if (kauf) S.pvMarkBuys(datum, werte) else S.pvMarkSells(datum, werte), kauf)
    }.sortedBy { it.t }

// Kurzform einer Quartalsbewegung: „AAPL neu“, „OXY +12 %“, „HPQ raus“.
private fun bewegungKurz(b: Portfolioverlauf.Bewegung): String = when (b.art) {
    "neu" -> "${b.kuerzel} ${S.pvMoveNew}"
    "raus" -> "${b.kuerzel} ${S.pvMoveOut}"
    else -> "${b.kuerzel} ${Fmt.signedPct(b.prozent, 0)}"
}

fun quartalName(stichtag: Long): String {
    val d = java.time.Instant.ofEpochMilli(stichtag).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    return S.pvQuarter((d.monthValue - 1) / 3 + 1, d.year)
}

// Marken je Quartal am Stichtag: Kaeufe und Verkaeufe gegenueber dem Vorquartal.
fun investorMarken(inv: Portfolioverlauf.Investor): List<Marke> =
    Portfolioverlauf.quartalsMarken(inv) { q, b, kauf ->
        val werte = b.joinToString(", ") { bewegungKurz(it) }
        if (kauf) S.pvMarkQBuys(quartalName(q.stichtag), werte) else S.pvMarkQSells(quartalName(q.stichtag), werte)
    }.map { (t, text, kauf) -> Marke(t, text, kauf) }

// Portfoliograph: Verlauf des Portfolios haupt mit Kauf- und Verkaufsmarken und Vergleichslinien.
// Alle Linien beginnen am Anfang des Zeitraums bei null Prozent; Waehrung wie die Anzeige der App (Euro oder
// US-Dollar), mit dem Kurs des jeweiligen Tages umgerechnet. Ohne haupt (Vergleichsseite) sind alle Linien
// Vergleiche. Darunter je Linie Gesamtrendite, Rendite pro Jahr, Schwankung und groesster Rueckgang.
@Composable
fun PortfolioKarte(
    state: AppState,
    titel: String?,
    haupt: Pair<Reihe, Waehrung>?,
    laedt: Boolean,
    hinweis: String,
    eigener: String? = null,
    marken: List<Marke> = emptyList(),
    standardZeitraum: VerlaufZeitraum = VerlaufZeitraum.J3,
    leerText: String = S.pvNone,
    vorauswahl: List<String>? = null,
    // Beginn des gewaehlten Zeitraums in ms.
    onZeitraum: ((Long) -> Unit)? = null,
    // Erklaerung zum Tag unter dem Fadenkreuz statt der Markentexte.
    erklaerung: (@Composable (Long) -> Unit)? = null,
    // Kalender statt des Graphen, umschaltbar neben den Zeitraeumen.
    kalender: (@Composable () -> Unit)? = null,
) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val ziel = if (Fmt.inUsd) Waehrung.USD else Waehrung.EUR
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var fx by remember { mutableStateOf<List<Candle>?>(null) }
    LaunchedEffect(Unit) { fx = state.kurseNativ(Portfolioverlauf.EURUSD) }
    var gewaehltText by rememberSaveable(eigener) {
        mutableStateOf((vorauswahl ?: VerlaufLader.zuletzt).filter { it != eigener }.joinToString("\n"))
    }
    val gewaehlt = gewaehltText.lines().filter { it.isNotBlank() }
    fun setzen(neu: List<String>) {
        gewaehltText = neu.joinToString("\n")
        if (vorauswahl == null) VerlaufLader.zuletzt = neu
    }
    val geladen = remember { mutableStateMapOf<String, Pair<Reihe, Waehrung>?>() }
    val inArbeit = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(gewaehltText) {
        gewaehlt.filter { it !in geladen && inArbeit[it] != true }.forEach { k ->
            inArbeit[k] = true
            scope.launch {
                // bis zu drei Versuche, falls ein Kursabruf ausfaellt (etwa bei vielen gleichzeitigen Abrufen)
                var r: Pair<Reihe, Waehrung>? = null
                for (versuch in 0..2) {
                    r = runCatching { if (eigener == VerlaufLader.DEPOT) VerlaufLader.reiheWieDepot(state, k) else VerlaufLader.reihe(state, k) }.getOrNull()
                    if (r != null) break
                    kotlinx.coroutines.delay(2_000L + versuch * 4_000L)
                }
                geladen[k] = r
                inArbeit[k] = false
            }
        }
    }
    var zeitraum by rememberSaveable(eigener) { mutableStateOf(standardZeitraum) }
    var mehr by rememberSaveable(eigener) { mutableStateOf(false) }
    var kalenderAn by rememberSaveable(eigener) { mutableStateOf(false) }
    var auswahl by remember { mutableStateOf(false) }

    val farbeHaupt = MaterialTheme.colorScheme.onSurface
    val fxStand = fx
    // Linien im Zeitraum, umgerechnet und auf 100 gesetzt
    val roh: List<Triple<String, Pair<Reihe, Waehrung>, Boolean>> =
        listOfNotNull(if (titel != null && haupt != null) Triple(titel, haupt, true) else null) +
            gewaehlt.mapNotNull { k -> geladen[k]?.let { Triple(VerlaufLader.name(state, k), it, false) } }
    val spanneTage = (if (titel != null) haupt?.first else roh.firstOrNull()?.second?.first)
        ?.let { if (it.size < 2) 0L else (it.last().first - it.first().first) / 86_400_000L } ?: 0L
    val angeboten = VerlaufZeitraum.entries.filter { it == VerlaufZeitraum.MAX || it.tage < spanneTage - 5 }
    val zr = if (zeitraum in angeboten) zeitraum else VerlaufZeitraum.MAX
    val hauptBeginn = haupt?.first?.firstOrNull()?.first ?: 0L
    LaunchedEffect(zr, hauptBeginn) {
        onZeitraum?.invoke(if (zr.tage == 0) hauptBeginn else maxOf(hauptBeginn, System.currentTimeMillis() - zr.tage * 86_400_000L))
    }
    val linien = remember(roh, fxStand, ziel, zr) {
        if (roh.isEmpty()) return@remember emptyList<Linie>()
        val umgerechnet = roh.map { (n, r, h) ->
            val (reihe, w) = r
            Triple(n, if (w != ziel && fxStand == null) emptyList() else Portfolioverlauf.umrechnen(reihe, w, ziel, fxStand.orEmpty()), h)
        }.filter { it.second.size >= 2 }
        if (umgerechnet.isEmpty()) return@remember emptyList<Linie>()
        val basis = if (titel != null) umgerechnet.firstOrNull { it.third }?.second?.first()?.first ?: return@remember emptyList<Linie>()
        else umgerechnet.maxOf { it.second.first().first }
        val jetzt = System.currentTimeMillis()
        val start = if (zr.tage == 0) basis else maxOf(basis, jetzt - zr.tage * 86_400_000L)
        var farbe = 0
        umgerechnet.map { (n, r, h) ->
            Linie(n, Portfolioverlauf.fenster(r, start), if (h) farbeHaupt else VERGLEICHS_FARBEN[farbe++ % VERGLEICHS_FARBEN.size])
        }.filter { it.punkte.size >= 2 }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val kal = kalenderAn && kalender != null
        // Zeitleiste wie beim Chart statt Pillen; der Kalender rechts daneben
        ZeitLeiste(
            angeboten.map { S.pvRange(it.tage) }, selected = if (kal) -1 else angeboten.indexOf(zr),
            onSelect = { i -> zeitraum = angeboten[i]; kalenderAn = false },
            modifier = Modifier.padding(horizontal = 12.dp),
            trailing = if (kalender != null) { { CalendarToggle(active = kal) { kalenderAn = !kal } } } else null,
        )
        if (kal) {
            kalender?.invoke()
        } else {
            val hauptDa = titel == null || linien.any { it.farbe == farbeHaupt }
            when {
                laedt && titel != null -> Text(S.pvLoading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
                !hauptDa -> Text(leerText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
                linien.isEmpty() -> Text(if (gewaehlt.any { inArbeit[it] == true }) S.pvLoading else leerText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
                else -> VergleichsChart(
                    linien = linien,
                    format = { Fmt.signedPct(it - 100.0, 1) },
                    modifier = Modifier.padding(top = 6.dp),
                    hoehe = 210.dp,
                    marken = if (titel != null) marken else emptyList(),
                    nullLinie = 100.0,
                    markenHinweis = if (titel != null && erklaerung != null) S.dvDragHint else S.pvDragHint,
                    handelszeit = true,
                    erklaerung = if (titel != null) erklaerung else null,
                )
            }
            // Vergleiche: gewaehlte zum Entfernen, dazu „Vergleichen“
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                gewaehlt.forEach { k ->
                    val l = linien.firstOrNull { it.name == VerlaufLader.name(state, k) }
                    val laeuft = inArbeit[k] == true
                    val fehlt = !laeuft && k in geladen && geladen[k] == null
                    Row(
                        Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { setzen(gewaehlt - k) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(l?.farbe ?: MaterialTheme.colorScheme.outline))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            VerlaufLader.name(state, k) + when { laeuft -> " …"; fehlt -> " (–)"; else -> "" } + "  ×",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Chip("+ " + S.pvCompare, active = false) { auswahl = true }
            }
            // Kennzahlen je Linie im Zeitraum als Tabelle
            KennzahlTabelle(linien, if (titel != null) linien.firstOrNull { it.farbe == farbeHaupt } else null,
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp))
            // Ein Satz unter dem Chart, der Rest hinter Mehr
            AufklappText(
                hinweis + " " + S.pvCurrencyNote(if (ziel == Waehrung.USD) "US-Dollar" else "Euro"),
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }

    if (auswahl) {
        VergleichAuswahl(state, gewaehlt, eigener, onDismiss = { auswahl = false }) { k ->
            setzen(if (k in gewaehlt) gewaehlt - k else if (gewaehlt.size >= 6) gewaehlt else gewaehlt + k)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VergleichAuswahl(state: AppState, gewaehlt: List<String>, eigener: String?, onDismiss: () -> Unit, onToggle: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val gruppen = remember(settings.copyGefolgt) { VerlaufLader.kandidaten(state, state.gefolgt(settings)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Text(S.pvCompareTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                Text(S.pvCompareMax, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            gruppen.forEach { (titel, wahl) ->
                item(key = "g-$titel") { SectionHeader(titel) }
                items(wahl.filter { it.schluessel != eigener }, key = { "w-" + it.schluessel }) { w ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        OptionRow(w.name, w.unter, selected = w.schluessel in gewaehlt) { onToggle(w.schluessel) }
                    }
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

// Kaeufe und Verkaeufe eines Investors je Quartal (neueste zuerst), aus der Verlaufsdatei.
@Composable
fun QuartalsBewegungen(state: AppState, inv: Portfolioverlauf.Investor, onOpen: (String) -> Unit) {
    var alle by rememberSaveable { mutableStateOf(false) }
    val signal = LocalSignal.current
    val quartale = inv.quartale.drop(1).filter { it.bewegungen.isNotEmpty() }.reversed()
    if (quartale.isEmpty()) return
    SectionHeader(S.pvMoves)
    (if (alle) quartale else quartale.take(3)).forEach { q ->
        Text(
            S.pvQuarterHead(quartalName(q.stichtag), Fmt.date(q.eingereicht)),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 2.dp),
        )
        q.bewegungen.sortedByDescending { kotlin.math.abs(it.usd) }.take(8).forEach { b ->
            val sym = CopyRouten.symbol(b.kuerzel)
            val name = CopyRouten.name(b.kuerzel, b.name)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(sym) }.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentLogo(state, sym, name, size = 28)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$name (${b.kuerzel})", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (b.art) {
                            "neu" -> S.copyNew
                            "mehr" -> S.copyIncreased(Fmt.pct(b.prozent, 0))
                            "weniger" -> S.copyReduced(Fmt.pct(-b.prozent, 0))
                            else -> S.copySold
                        },
                        style = MaterialTheme.typography.bodySmall, color = if (b.kauf) signal.up else signal.down,
                    )
                }
                Text(Fmt.dollarKompakt(b.usd), style = MaterialTheme.typography.bodySmall, color = if (b.kauf) signal.up else signal.down)
            }
        }
    }
    if (quartale.size > 3) ActionLine(if (alle) S.pvFewerQuarters else S.pvAllQuarters(quartale.size)) { alle = !alle }
}

// Vergleichsseite: mehrere Portfolios nebeneinander, ohne eigenes Hauptportfolio. vorauswahl „s“ fuer
// Strategien, „i“ fuer Investoren.
@Composable
fun PortfolioVergleichScreen(state: AppState, vorauswahl: String, onBack: () -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val start = remember(vorauswahl) {
        when (vorauswahl) {
            "i" -> listOf("i:buffett", "i:ackman", "i:wood", "m:msci")
            else -> listOf("s:welt", "s:klassisch", "s:dauer", "s:allwetter")
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.pvCompareScreen, null, onBack) }
        item {
            Text(S.pvCompareIntro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        item {
            PortfolioKarte(
                state, titel = null, haupt = null, laedt = false, hinweis = S.pvNoteCompare,
                eigener = "vergleich-$vorauswahl", standardZeitraum = VerlaufZeitraum.J5, vorauswahl = start,
            )
        }
    }
}
