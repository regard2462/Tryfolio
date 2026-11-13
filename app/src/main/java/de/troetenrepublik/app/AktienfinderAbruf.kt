package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.FinderBilanz
import de.troetenrepublik.app.engine.FinderVorlagen
import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.InsiderTrend
import de.troetenrepublik.app.engine.Konflikte
import de.troetenrepublik.app.engine.Laenderrisiken
import de.troetenrepublik.app.engine.LaenderStrukturen
import de.troetenrepublik.app.engine.LandRisiko
import de.troetenrepublik.app.engine.Reisehinweise
import de.troetenrepublik.app.engine.Taetigkeit
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.Region
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// Felder des Sammelabrufs fuer den Aktienfinder zusaetzlich zu denen des Depotfinders.
private val FINDER_FELDER = listOf("forwardPE", "marketCap", "averageAnalystRating", "fiftyTwoWeekChangePercent", "twoHundredDayAverageChangePercent") +
    // Fuer Vorlagen und harte Grenzen (5.94): Kurs, Tagesaenderung, Eroeffnung, Volumen, Linien, 52-Wochen-Spanne, Zahlentermin
    listOf(
        "regularMarketPrice", "regularMarketChangePercent", "regularMarketOpen", "regularMarketPreviousClose", "regularMarketVolume",
        "averageDailyVolume3Month", "averageDailyVolume10Day", "fiftyDayAverage", "fiftyDayAverageChangePercent", "twoHundredDayAverage",
        "fiftyTwoWeekHigh", "fiftyTwoWeekLow", "earningsTimestamp", "earningsTimestampStart",
    )

// Felder der Vorlagen aus dem Sammelabruf; Kurs in Euro (Pence durch 100). Der Zahlentermin ist der naechste der
// beiden Zeitstempel, der nicht laenger als zwei Tage zurueckliegt.
internal fun sammelWerte(k: Map<String, Double>, eurofaktor: Double?, waehrung: String, jetzt: Long): Pair<Map<Feld, Double>, Long?> {
    val aus = HashMap<Feld, Double>()
    fun pos(f: String) = k[f]?.takeIf { it > 0 && it.isFinite() }
    val kurs = pos("regularMarketPrice")
    if (kurs != null && eurofaktor != null && eurofaktor > 0) aus[Feld.KURS] = kurs / eurofaktor / (if (waehrung == "GBp" || waehrung == "GBX") 100.0 else 1.0)
    k["regularMarketChangePercent"]?.takeIf { it.isFinite() }?.let { aus[Feld.TAG] = it }
    pos("regularMarketOpen")?.let { o -> pos("regularMarketPreviousClose")?.let { v -> aus[Feld.LUECKE] = (o / v - 1) * 100 } }
    k["regularMarketVolume"]?.takeIf { it >= 0 }?.let { aus[Feld.VOLUMEN] = it }
    pos("averageDailyVolume3Month")?.let { aus[Feld.VOLUMEN_SCHNITT] = it }
    pos("averageDailyVolume10Day")?.let { s -> k["regularMarketVolume"]?.let { v -> de.troetenrepublik.app.engine.RelativesVolumen.wert(v, s) } }
        ?.let { aus[Feld.REL_VOLUMEN] = it }
    k["fiftyDayAverageChangePercent"]?.takeIf { it.isFinite() }?.let { aus[Feld.LINIE50] = it * 100 }
    pos("fiftyDayAverage")?.let { a -> pos("twoHundredDayAverage")?.let { b -> aus[Feld.SMA50_200] = (a / b - 1) * 100 } }
    if (kurs != null) {
        pos("fiftyTwoWeekHigh")?.let { aus[Feld.HOCH52] = (kurs / it - 1) * 100 }
        pos("fiftyTwoWeekLow")?.let { aus[Feld.TIEF52] = (kurs / it - 1) * 100 }
    }
    val termin = listOfNotNull(pos("earningsTimestamp"), pos("earningsTimestampStart"))
        .map { (it * 1000).toLong() }.filter { it > jetzt - 2 * 86_400_000L }.minOrNull()
    return aus to termin
}

// Kennzahlen des Sammelabrufs 30 Minuten, Einzelabrufe der besten Kandidaten zwoelf Stunden.
private const val SAMMEL_MS = 30 * 60_000L
private const val DETAIL_MS = 12 * 3_600_000L

private val finderKennzahlen = ConcurrentHashMap<String, Pair<Long, Map<String, Double>>>()
private val finderDetails = ConcurrentHashMap<String, Pair<Long, FinderDetails>>()

// Felder der Vorlagen aus den Tagesschlusskursen eines Jahres (5.94), sechs Stunden gemerkt; leer, wenn keine kamen.
private val finderKerzen = ConcurrentHashMap<String, Pair<Long, Map<Feld, Double>>>()
private const val KERZEN_MS = 6 * 3_600_000L

// Werte ohne Antwort (k: Tageskurse, d: Profil) mit Zeitpunkt: eine Stunde lang nicht erneut fuer Alarme ausgewaehlt,
// damit nicht jeder Lauf dieselben Werte abfragt. Sie gelten dabei nicht als geladen.
private val finderOhneAntwort = ConcurrentHashMap<String, Long>()
private const val OHNE_ANTWORT_MS = 3_600_000L

private fun kuerzlichOhneAntwort(schluessel: String, jetzt: Long): Boolean =
    finderOhneAntwort[schluessel]?.let { jetzt - it < OHNE_ANTWORT_MS } == true

// Fuer den Bildschirm: art 'k' Tageskurse, 'd' Profil.
internal fun finderOhneAntwortKuerzlich(art: Char, symbol: String, jetzt: Long = System.currentTimeMillis()): Boolean =
    kuerzlichOhneAntwort("$art:$symbol", jetzt)

// Einzelabruf (Profil) eines Werts fuer den Aktienfinder; Anteile in Prozent. Die Bilanz kommt aus FinderBilanzSpeicher.
data class FinderDetails(
    val kuv: Double? = null,
    val evEbitda: Double? = null,
    val peg: Double? = null,
    val marge: Double? = null,
    val eigenkapitalRendite: Double? = null,
    val verschuldung: Double? = null,
    val kursziel: Double? = null,
    val leerQuote: Double? = null,
    // [0.73] Fuer die Waage wie auf der Wertpapierseite und fuer das Landrisiko: Kasse, Schulden und freier Cashflow in
    // Euro, Sektor und Branche, Zahl der Analysten, Quartalsergebnisse, Taetigkeitsland (alpha-2)
    val kasse: Double? = null,
    val schulden: Double? = null,
    val freierCashflow: Double? = null,
    val sektor: String = "",
    val industrie: String = "",
    val analysten: Int = 0,
    val ergebnisse: List<Pair<Double, Double>> = emptyList(),
    val taetig: String? = null,
    // Weitere Felder fuer Vorlagen (5.94)
    val werte: Map<Feld, Double> = emptyMap(),
)

// Letztes Ergebnis je Universum, damit der Aktienfinder beim Wiederoeffnen sofort steht.
object AktienfinderSpeicher {
    @Volatile var universum: String? = null
    @Volatile var daten: List<Aktienfinder.Daten> = emptyList()
    @Volatile var zeit: Long = 0L
}

// Firmennamen aus Meldungen (Emittent) den Katalogaktien zuordnen: gleicher Namensstamm oder gleich nach
// Gleichwerte.namenGleich. Ohne eindeutige Zuordnung fehlt der Name im Ergebnis.
internal fun namenZuSymbolen(namen: Collection<String>, aktien: List<Catalog.Stock>): Map<String, String> {
    if (aktien.isEmpty()) return emptyMap()
    val staemme = aktien.map { it to Gleichwerte.stamm(it.n) }.filter { it.second.isNotEmpty() }
    val aus = HashMap<String, String>()
    for (n in namen.toSet()) {
        val s = Gleichwerte.stamm(n)
        if (s.isEmpty()) continue
        val treffer = staemme.filter { (a, st) -> (st == s && !Gleichwerte.incSperre(n, a.n)) || Gleichwerte.namenGleich(n, a.n) }.map { it.first.s }.distinct()
        if (treffer.size == 1) aus[n] = treffer[0]
    }
    return aus
}

private fun pfund(waehrung: String): String = if (waehrung == "GBp" || waehrung == "GBX") "GBP" else waehrung

private fun euroKurs(waehrung: String, fx: Map<String, Double>): Double? = ExchangeRates.rate(pfund(waehrung), fx)

// [0.73] Landrisiko-Fakten je Landeskuerzel (a2) fuer den Aktienfinder: einmal geladen, ein halber Tag gueltig.
// Alles Quellen, die die Laenderblaetter ohnehin nutzen: Weltbank-Regierungsfuehrung (ein Abruf fuer alle Laender),
// Sanktionslisten, OpenSanctions, UCDP-Konflikte, US-Reisehinweise und die strukturellen Probleme aus dem Bündel.
// Kam die Weltbank-Abfrage leer zurueck, wird nichts festgeschrieben und erst nach zehn Minuten neu versucht.
@Volatile internal var finderLandFakten: Map<String, LandRisiko.Fakten>? = null
@Volatile private var finderLandUm: Long = 0L
@Volatile private var finderLandVersuch: Long = 0L
@Volatile private var strukturStandCache: LaenderStrukturen.Stand? = null
private val finderLandLaeuft = AtomicBoolean(false)

private const val LAND_GUELTIG_MS = 12 * 3_600_000L
private const val LAND_PAUSE_MS = 10 * 60_000L

// Landrisiko im Hintergrund holen, ohne den Start des Finders aufzuhalten; danach den Zustand neu zusammensetzen.
internal fun AppState.finderLandVorbereiten() {
    if (finderLandFakten != null && System.currentTimeMillis() - finderLandUm < LAND_GUELTIG_MS) return
    if (!finderLandLaeuft.compareAndSet(false, true)) return
    val state = this
    hintergrundScope.launch {
        try {
            sicher {
                state.finderLandRisiko()
                FinderLauf.landNeu(state)
            }
        } finally {
            finderLandLaeuft.set(false)
        }
    }
}

suspend fun AppState.finderLandRisiko() {
    val jetzt = System.currentTimeMillis()
    if (finderLandFakten != null && jetzt - finderLandUm < LAND_GUELTIG_MS) return
    if (jetzt - finderLandVersuch < LAND_PAUSE_MS) return
    finderLandVersuch = jetzt
    val karte = runCatching { globusKarte() }.getOrNull() ?: return
    val wgi = runCatching {
        weltbankViele(Laenderrisiken.WGI.map { Laenderrisiken.wgiKennung(it) }, quelle = 3)
    }.getOrDefault(emptyMap())
    // Ohne Weltbank-Zahlen finge jede Rechnung bei 50 an und die Werte stuenden zu tief: nichts merken
    if (wgi.values.none { it != null }) return
    val sanktionen = runCatching { risikoSanktionenListe() }.getOrNull()
    val os = runCatching { risikoOpenSanctions() }.getOrNull()
    val konflikte = runCatching { risikoKonflikte() }.getOrNull()
    val us = runCatching { risikoUs() }.getOrNull()
    if (strukturStandCache == null) {
        strukturStandCache = runCatching {
            de.troetenrepublik.app.market.Daten.strukturen()
        }.getOrNull()
    }
    val aus = HashMap<String, LandRisiko.Fakten>()
    for (land in karte.laender) {
        val perzentile = Laenderrisiken.WGI.mapNotNull { k -> wgi[Laenderrisiken.wgiKennung(k)]?.let { Laenderrisiken.perzentil(it, land.a3)?.perzentil } }
        aus[land.a2] = LandRisiko.Fakten(
            wgi = perzentile.takeIf { it.isNotEmpty() }?.map { it.toDouble() }?.average(),
            stabilitaet = wgi[Laenderrisiken.wgiKennung("PV")]?.let { Laenderrisiken.perzentil(it, land.a3)?.perzentil }?.toDouble()
                ?.takeIf { perzentile.size == Laenderrisiken.WGI.size },
            sanktionenEu = sanktionen?.eu?.get(land.a2)?.size ?: 0,
            sanktionenUs = sanktionen?.us?.get(land.a2)?.size ?: 0,
            openSanctions = os?.laender?.get(land.a2),
            konflikt = konflikte?.let { LandRisiko.konfliktStufe(Konflikte.fuer(it, land), Laenderrisiken.schluessel(land)) },
            reise = us?.let { Reisehinweise.usStufe(it, land) },
            strukturen = strukturStandCache?.let { LaenderStrukturen.fuer(it, land).size } ?: 0,
        )
    }
    finderLandFakten = aus
    finderLandUm = jetzt
}

// Landrisiko-Punkte je Landeskuerzel, oder null ohne Fakten.
internal fun finderLandPunkte(a2: String): Double? = finderLandFakten?.get(a2)?.let { LandRisiko.punkte(it) }

// Klar riskantes Land nach LandRisiko.hoch, oder null ohne Fakten.
internal fun finderLandHoch(a2: String): Boolean? = finderLandFakten?.get(a2)?.let { LandRisiko.hoch(it) }

// Daten des Aktienfinders fuer die Katalogaktien: ein Sammelabruf je 100 Werte (Kennzahlen, Boersenwert, Note der
// Analysten, Kurstrend), dazu ohne weiteren Abruf die Insidermeldungen und Leerverkaeufe auf dem Geraet, die
// Reddit-Erwaehnungen (ein Abruf, 20 Minuten gespeichert) und die schon geholten Einzelabrufe.
suspend fun AppState.aktienfinderLaden(universum: String, aktien: List<Catalog.Stock>, fortschritt: (Float) -> Unit): List<Aktienfinder.Daten> {
    val jetzt = System.currentTimeMillis()
    val liste = aktien.distinctBy { it.s }
    // Landrisiko im Hintergrund vorbereiten, falls noch nicht da; ohne es zaehlt der Bereich neutral, bis es eintrifft
    finderLandVorbereiten()
    val offen = liste.map { it.s }.filter { s -> finderKennzahlen[s]?.takeIf { jetzt - it.first < SAMMEL_MS } == null }
    val teile = offen.chunked(100)
    var erhalten = 0
    teile.forEachIndexed { i, t ->
        kennzahlen(t, FINDER_FELDER).forEach { (s, m) -> finderKennzahlen[s] = jetzt to m; erhalten++ }
        fortschritt(0.75f * (i + 1) / teile.size)
    }
    // [0.75] Kam fuer keinen offenen Wert eine Antwort, ist das ein Ausfall (meist keine Verbindung), kein Ergebnis:
    // Der letzte Stand bleibt, statt dass alle Werte bei 50 Punkten mit "Stand gerade eben" stuenden.
    if (FinderStand.ausfall(offen.size, erhalten)) throw FinderOhneAntwort()
    val insider = withContext(Dispatchers.Default) { insiderJeWert(liste) }
    fortschritt(0.85f)
    val leer = withContext(Dispatchers.Default) { leerJeWert(liste) }
    val reddit = runCatching { redditTrends() }.getOrDefault(emptyList()).associateBy { it.ticker.uppercase() }
    fortschritt(0.95f)
    val fx = market.fxKurse.value
    finderBilanzVorbereiten()
    val daten = liste.map { a -> finderDaten(a, insider[a.s], leer[a.s], reddit[a.s.uppercase()]?.takeIf { a.c == "US" }, fx, jetzt) }
    AktienfinderSpeicher.universum = universum
    AktienfinderSpeicher.daten = daten
    // Stand ist der aelteste verwendete Sammelabruf, nicht der Zeitpunkt dieses Laufs
    AktienfinderSpeicher.zeit = FinderStand.stand(liste.mapNotNull { finderKennzahlen[it.s]?.first }, jetzt)
    fortschritt(1f)
    return daten
}

// [0.75] Der Sammelabruf des Aktienfinders brachte fuer keinen offenen Wert eine Antwort.
class FinderOhneAntwort : Exception("finder: no response")

// [0.75] Regeln fuer Ausfall und Stand des Aktienfinders (rein, fuer Tests).
object FinderStand {
    // Ausfall: Es gab offene Werte, aber fuer keinen kam etwas.
    fun ausfall(offen: Int, erhalten: Int): Boolean = offen > 0 && erhalten == 0

    // Stand der Daten: der aelteste Zeitpunkt der verwendeten Sammelabrufe; ohne welche jetzt.
    fun stand(zeiten: List<Long>, jetzt: Long): Long = zeiten.filter { it > 0 }.minOrNull() ?: jetzt
}

private fun finderDaten(
    a: Catalog.Stock, ins: InsiderTrend.Ergebnis?, leer: Double?, reddit: de.troetenrepublik.app.market.MarketApi.Erwaehnung?,
    fx: Map<String, Double>, jetzt: Long,
): Aktienfinder.Daten {
    val k = finderKennzahlen[a.s]?.second.orEmpty()
    val d = finderDetails[a.s]?.takeIf { jetzt - it.first < DETAIL_MS }?.second
    val bereinigt = analystenGemerkt(a.s)?.bilanz?.takeIf { !it.wenigAussagekraft }?.bereinigt
    val (sammel, termin) = sammelWerte(k, euroKurs(a.w, fx), a.w, jetzt)
    val kerzen = finderKerzen[a.s]?.takeIf { jetzt - it.first < KERZEN_MS }?.second
    return mitBilanz(Aktienfinder.Daten(
        symbol = a.s, name = a.n, land = a.c, branche = a.i,
        kgv = k["trailingPE"], kgvErwartet = k["forwardPE"], kbv = k["priceToBook"],
        gewinn = k["epsTrailingTwelveMonths"], gewinnErwartet = k["epsForward"], buchwert = k["bookValue"],
        dividende = k["trailingAnnualDividendYield"]?.times(100.0),
        boersenwert = k["marketCap"]?.let { mc -> euroKurs(a.w, fx)?.let { mc / it } },
        note = k["averageAnalystRating"]?.takeIf { it in 1.0..5.0 },
        jahr = k["fiftyTwoWeekChangePercent"],
        linie200 = k["twoHundredDayAverageChangePercent"]?.times(100.0),
        kuv = d?.kuv, evEbitda = d?.evEbitda, peg = d?.peg, marge = d?.marge, eigenkapitalRendite = d?.eigenkapitalRendite,
        verschuldung = d?.verschuldung, kursziel = d?.kursziel, bereinigt = bereinigt,
        insider = ins?.let { Aktienfinder.insiderWert(it) }, insiderCluster = ins?.cluster == true,
        leer = d?.leerQuote ?: leer, leerUs = d?.leerQuote != null,
        erwaehnungen = reddit?.erwaehnungen, erwaehnungenVorher = reddit?.erwaehnungenVorher,
        landPunkte = finderLandPunkte(d?.taetig ?: a.c), taetig = d?.taetig,
        rohstoffPunkte = de.troetenrepublik.app.engine.Einflussverzeichnis.rohstoffBindung(
            de.troetenrepublik.app.engine.Einflussverzeichnis.geladen,
            de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild(a.s, a.i, null, industrie = d?.industrie?.takeIf { it.isNotBlank() }),
        ),
        kasse = d?.kasse, schulden = d?.schulden, freierCashflow = d?.freierCashflow,
        sektor = d?.sektor.orEmpty(), industrie = d?.industrie.orEmpty(), analysten = d?.analysten ?: 0, ergebnisse = d?.ergebnisse.orEmpty(),
        mitDetails = d != null,
        werte = sammel + d?.werte.orEmpty() + kerzen.orEmpty(), zahlenTermin = termin, mitKerzen = kerzen != null,
        mitSammel = k.isNotEmpty(),
    ), a.w, fx, jetzt)
}

// Neu zusammensetzen ohne Abruf, etwa nachdem Einzelabrufe oder Bilanzdaten dazugekommen sind.
fun AppState.aktienfinderAuffrischen(liste: List<Aktienfinder.Daten>): List<Aktienfinder.Daten> {
    val jetzt = System.currentTimeMillis()
    val fx = market.fxKurse.value
    val waehrung = Catalog.loaded()?.aktien?.associate { it.s to it.w }.orEmpty()
    return liste.map { alt ->
        val d = finderDetails[alt.symbol]?.takeIf { jetzt - it.first < DETAIL_MS }?.second
        val mitDetail = if (d == null) alt else alt.copy(
            kuv = d.kuv, evEbitda = d.evEbitda, peg = d.peg, marge = d.marge, eigenkapitalRendite = d.eigenkapitalRendite,
            verschuldung = d.verschuldung, kursziel = d.kursziel,
            bereinigt = analystenGemerkt(alt.symbol)?.bilanz?.takeIf { !it.wenigAussagekraft }?.bereinigt ?: alt.bereinigt,
            leer = d.leerQuote ?: alt.leer, leerUs = d.leerQuote != null || alt.leerUs, mitDetails = true,
            taetig = d.taetig, kasse = d.kasse, schulden = d.schulden, freierCashflow = d.freierCashflow,
            sektor = d.sektor, industrie = d.industrie, analysten = d.analysten, ergebnisse = d.ergebnisse,
            werte = alt.werte + d.werte,
        )
        val kerzen = finderKerzen[alt.symbol]?.takeIf { jetzt - it.first < KERZEN_MS }?.second
        val mitKerzen = if (kerzen == null) mitDetail else mitDetail.copy(werte = mitDetail.werte + kerzen, mitKerzen = true)
        // Landrisiko jedes Mal neu zuordnen: es kommt im Hintergrund und kann nach den Profilen eintreffen
        val neu = mitKerzen.copy(landPunkte = finderLandPunkte(mitKerzen.taetig ?: mitKerzen.land) ?: mitKerzen.landPunkte)
        mitBilanz(neu, waehrung[alt.symbol].orEmpty(), fx, jetzt)
    }.also { AktienfinderSpeicher.daten = it }
}

// Jahresabschluesse fuer die Bilanzqualitaet im Aktienfinder, auf dem Geraet in finder-bilanz.json (30 Tage gueltig,
// FinderBilanz). So steht die Bilanz beim zweiten Aufruf sofort.
object FinderBilanzSpeicher {
    internal val eintraege = ConcurrentHashMap<String, FinderBilanz.Eintrag>()
    // Zaehlt jeden neuen Eintrag; der Marktmacht (MarktmachtQuelle) rechnet nur neu, wenn sich etwas geaendert hat
    internal val version = AtomicInteger()
    internal val sperre = Mutex()
    @Volatile internal var datei: SichereDatei? = null

    fun vorhanden(symbol: String, jetzt: Long = System.currentTimeMillis()): Boolean =
        eintraege[symbol]?.let { FinderBilanz.aktuell(it, jetzt) } == true

    // Wie viele Werte mit Bilanz (ohne Banken und Versicherungen) ihre Jahresabschluesse schon haben, und wie viele es sind.
    fun stand(daten: List<Aktienfinder.Daten>): Pair<Int, Int> {
        val jetzt = System.currentTimeMillis()
        val mit = daten.filter { Aktienfinder.mitBilanz(it.branche) }
        return mit.count { vorhanden(it.symbol, jetzt) } to mit.size
    }
}

// Abstand zwischen zwei Abrufen je Strang, laengere Pause nach einem Fehler, Abbruch nach so vielen Fehlern in Folge.
private const val BILANZ_PAUSE_MS = 400L
private const val BILANZ_FEHLER_PAUSE_MS = 4_000L
private const val BILANZ_ABBRUCH = 5

// Gespeicherte Jahresabschluesse einmal je Prozess von der Platte lesen.
suspend fun AppState.finderBilanzVorbereiten() {
    val sp = FinderBilanzSpeicher
    if (sp.datei != null) return
    sp.sperre.withLock {
        if (sp.datei != null) return
        val datei = SichereDatei(File(store.ordner, "finder-bilanz.json"))
        val m = withContext(Dispatchers.IO) { runCatching { FinderBilanz.lesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrDefault(emptyMap()) }
        m.forEach { (s, e) -> sp.eintraege.putIfAbsent(s, e) }
        sp.version.incrementAndGet()
        sp.datei = datei
    }
}

private suspend fun finderBilanzSichern() {
    val sp = FinderBilanzSpeicher
    val datei = sp.datei ?: return
    sp.sperre.withLock {
        withContext(Dispatchers.IO) {
            runCatching { datei.schreiben(FinderBilanz.schreiben(HashMap(sp.eintraege), System.currentTimeMillis()).toByteArray(Charsets.UTF_8)) }
        }
    }
}

// Fehlende Jahresabschluesse nachholen, ueber den gewohnten Abruf (AppState.jahresabschluesse, Selbstreparatur der
// Quellen greift): hoechstens zwei gleichzeitig, nach jedem eine kurze Pause, nach einem Fehler eine laengere. Nach
// fuenf Fehlern in Folge endet der Durchlauf (false). fertig meldet jeden neuen Wert. Gespeichert wird alle 25 Werte
// und am Ende, auch beim Abbruch.
suspend fun AppState.finderBilanzHolen(symbole: List<String>, fertig: (String) -> Unit): Boolean {
    finderBilanzVorbereiten()
    if (symbole.isEmpty()) return true
    val naechster = AtomicInteger()
    val fehlerFolge = AtomicInteger()
    val abbruch = AtomicBoolean(false)
    val neu = AtomicInteger()
    try {
        coroutineScope {
            repeat(2) {
                launch {
                    while (!abbruch.get()) {
                        val i = naechster.getAndIncrement()
                        if (i >= symbole.size) break
                        val s = symbole[i]
                        if (FinderBilanzSpeicher.vorhanden(s)) continue
                        val jahre = jahresabschluesseOderNull(s)
                        if (jahre == null) {
                            if (fehlerFolge.incrementAndGet() >= BILANZ_ABBRUCH) abbruch.set(true)
                            delay(BILANZ_FEHLER_PAUSE_MS)
                            continue
                        }
                        fehlerFolge.set(0)
                        FinderBilanzSpeicher.eintraege[s] = FinderBilanz.eintrag(jahre, System.currentTimeMillis())
                        FinderBilanzSpeicher.version.incrementAndGet()
                        fertig(s)
                        if (neu.incrementAndGet() % 25 == 0) finderBilanzSichern()
                        delay(BILANZ_PAUSE_MS)
                    }
                }
            }
        }
    } finally {
        if (neu.get() > 0) withContext(NonCancellable) { finderBilanzSichern() }
    }
    return !abbruch.get()
}

// Bilanzqualitaet aus dem Zwischenspeicher eintragen; der Boersenwert (Euro) in der Berichtswaehrung fuer Altman.
// waehrung: Handelswaehrung, falls der Abschluss keine nennt.
private fun mitBilanz(d: Aktienfinder.Daten, waehrung: String, fx: Map<String, Double>, jetzt: Long): Aktienfinder.Daten =
    mitBilanzQualitaet(d, waehrung, fx, jetzt).copy(marktmacht = MarktmachtQuelle.fuer(d.symbol, fx))

private fun mitBilanzQualitaet(d: Aktienfinder.Daten, waehrung: String, fx: Map<String, Double>, jetzt: Long): Aktienfinder.Daten {
    if (!Aktienfinder.mitBilanz(d.branche)) return d
    val e = FinderBilanzSpeicher.eintraege[d.symbol]?.takeIf { FinderBilanz.gueltig(it, jetzt) } ?: return d
    val bericht = e.j.lastOrNull()?.waehrung?.ifBlank { null } ?: pfund(waehrung)
    val jeEuro = ExchangeRates.rate(bericht, fx)
    val boersenwert = d.boersenwert?.let { b -> jeEuro?.let { b * it } }
    val w = FinderBilanz.werte(e.j, boersenwert, de.troetenrepublik.app.engine.Qualitaet.fassungFinder(d.branche))
    // [0.78] Felder der Auswahlraster; ein Eintrag aus der Zeit vor den neuen Abruffeldern gilt dafuer als noch offen
    val neu = e.f >= FinderBilanz.FELDSTAND
    val raster = de.troetenrepublik.app.engine.FinderRaster.bilanzWerte(e.j, boersenwert, jeEuro, neu, jetzt)
    return d.copy(
        fScore = w?.fScore, zone = w?.zone, bilanz = w?.bilanz?.ampel, bilanzGruende = w?.bilanz?.gruende.orEmpty(),
        werte = d.werte + raster, mitAbschluss = FinderBilanz.aktuell(e, jetzt),
    )
}

// Einzelabrufe fuer die besten Kandidaten: Profil (KUV, EV/EBITDA, PEG, Marge, Eigenkapitalrendite, Verschuldung,
// Kursziel, Leerverkaufsquote). Drei gleichzeitig, zwoelf Stunden gemerkt.
suspend fun AppState.aktienfinderDetails(symbole: List<String>, katalog: Map<String, Catalog.Stock>, fortschritt: (Float) -> Unit) {
    if (symbole.isEmpty()) return
    val gleichzeitig = Semaphore(3)
    val fertig = AtomicInteger()
    val fx = market.fxKurse.value
    val sitze = (Catalog.loaded()?.aktien ?: emptyList()).associateBy { it.s }
    // Taetigkeitsland aus der Firmenbeschreibung wie beim Depot (Taetigkeit); ohne Karte gilt der Sitz
    val namen = runCatching { globusKarte() }.getOrNull()?.let { k -> Taetigkeit.Namen(k.laender.filter { it.a2.isNotEmpty() }.associate { it.en to it.a2 }) }
    coroutineScope {
        symbole.map { s ->
            async {
                gleichzeitig.withPermit {
                    val jetzt = System.currentTimeMillis()
                    // Ohne Antwort nichts merken (5.94): sonst gilt der Wert als geladen und faellt aus Vorlagen heraus
                    val p = runCatching { profileFor(s) }.getOrNull()
                    if (p == null) { finderOhneAntwort["d:$s"] = jetzt; fortschritt(fertig.incrementAndGet().toFloat() / symbole.size); return@withPermit }
                    // [0.76] Rueckkauf der letzten zwoelf Monate fuer die Vorlage „Rueckkaeufe“; ohne Antwort fehlt nur dieser Wert
                    val rueckText = runCatching { market.jahresabschluesse(market.dataSymbol(s), FinderVorlagen.RUECKKAUF_FELDER) }.getOrNull()
                    val det = run {
                        fun pos(v: Double) = v.takeIf { it > 0 && it.isFinite() }
                        // Betraege des Abschlusses in Euro; ohne bekannte Berichtswaehrung fehlen sie
                        val euro = p.financialCurrency.takeIf { it.isNotBlank() }?.let { euroKurs(it, fx) }?.takeIf { it > 0 }
                        fun eur(v: Double): Double? = if (euro != null && v.isFinite() && v != 0.0) v / euro else null
                        val k = sitze[s]
                        val taetig = namen?.let { n ->
                            Taetigkeit.herkunft(k?.c?.takeIf { it.isNotBlank() }, s, p.description, Taetigkeit.ressource(p.sector, k?.i), n).taetig
                        }
                        FinderDetails(
                            kuv = pos(p.priceToSales), evEbitda = pos(p.evToEbitda), peg = pos(p.mehr.peg),
                            marge = p.profitMargin.takeIf { p.revenue > 0 && it != 0.0 },
                            eigenkapitalRendite = p.returnOnEquity.takeIf { it != 0.0 },
                            verschuldung = p.debtToEquity.takeIf { it > 0 },
                            kursziel = if (p.targetPrice > 0 && p.currentPrice > 0) (p.targetPrice / p.currentPrice - 1.0) * 100.0 else null,
                            leerQuote = pos(p.mehr.leerQuote),
                            kasse = eur(p.totalCash)?.takeIf { it > 0 }, schulden = eur(p.totalDebt)?.takeIf { it > 0 },
                            freierCashflow = eur(p.freeCashflow),
                            sektor = p.sector, industrie = p.industry, analysten = p.analystCount,
                            ergebnisse = p.epsHistory.map { it.actual to it.estimate }, taetig = taetig,
                            werte = profilWerte(p) + listOfNotNull(
                                rueckText?.let { FinderVorlagen.rueckkaufLesen(it) }?.let { eur(it) }?.let { Feld.RUECKKAUF_BETRAG to it },
                            ),
                        )
                    }
                    finderDetails[s] = jetzt to det
                    fortschritt(fertig.incrementAndGet().toFloat() / symbole.size)
                }
            }
        }.awaitAll()
    }
}

// Felder der Vorlagen aus dem Profil (5.94); 0 heisst bei Yahoo keine Angabe, ausser bei der Ausschuettungsquote.
internal fun profilWerte(p: de.troetenrepublik.app.market.MarketApi.Profile): Map<Feld, Double> {
    val m = p.mehr
    fun nz(v: Double) = v.takeIf { it != 0.0 && it.isFinite() }
    fun pos(v: Double) = v.takeIf { it > 0 && it.isFinite() }
    return listOfNotNull(
        nz(m.gesamtkapitalRendite)?.let { Feld.ROA to it },
        nz(p.grossMargin)?.takeIf { p.revenue > 0 }?.let { Feld.BRUTTOMARGE to it },
        nz(p.earningsGrowth)?.let { Feld.GEWINN_QUARTAL to it },
        nz(p.revenueGrowth)?.let { Feld.UMSATZ_QUARTAL to it },
        nz(m.gewinnWachstumJahr)?.let { Feld.GEWINN_JAHR to it },
        nz(m.gewinnWachstumFolgejahr)?.let { Feld.GEWINN_FOLGEJAHR to it },
        pos(m.institutionen)?.let { Feld.INSTITUTIONEN to it },
        nz(p.beta)?.let { Feld.BETA to it },
        pos(m.liquiditaet1)?.let { Feld.LIQUIDITAET to it },
        pos(m.liquiditaet2)?.let { Feld.LIQUIDITAET_SCHNELL to it },
        p.payoutRatio.takeIf { it >= 0 && it.isFinite() }?.let { Feld.AUSSCHUETTUNG to it },
    ).toMap()
}

// Tagesschlusskurse eines Jahres fuer die Vorlagen (5.94): je Abfrage 20 Werte, nacheinander. Fehlt ein Wert in der
// Antwort, wird er nicht gemerkt (sonst fiele ein bisheriger Alarmtreffer heraus und kaeme spaeter erneut).
suspend fun AppState.aktienfinderKerzen(symbole: List<String>, fortschritt: (Float) -> Unit) {
    if (symbole.isEmpty()) return
    val teile = symbole.distinct().chunked(20)
    teile.forEachIndexed { i, t ->
        val r = schlusskurse(t, "1y")
        val jetzt = System.currentTimeMillis()
        if (r.isNotEmpty()) for (s in t) {
            val k = r[s]
            if (k == null) { finderOhneAntwort["k:$s"] = jetzt; continue }
            finderKerzen[s] = jetzt to withContext(Dispatchers.Default) { FinderVorlagen.kerzenWerte(k.map { it.t }, k.map { it.close }) }
        }
        fortschritt((i + 1).toFloat() / teile.size)
    }
}

// Ob fuer einen Wert Tageskurse vorliegen (noch gueltig).
internal fun finderKerzenDa(symbol: String): Boolean =
    finderKerzen[symbol]?.let { System.currentTimeMillis() - it.first < KERZEN_MS } == true

// Insider-Trend der letzten 180 Tage je Katalogaktie aus den Meldungen auf dem Geraet: SEC (Kuerzel), BaFin
// (Kuerzel oder Name des Emittenten) und Europa (Name des Emittenten im Land der Aktie). Ohne Abruf.
private suspend fun AppState.insiderJeWert(aktien: List<Catalog.Stock>): Map<String, InsiderTrend.Ergebnis> {
    val ab = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(180).toString()
    val aus = HashMap<String, InsiderTrend.Ergebnis>()
    val nachKuerzel = aktien.associateBy { it.s.uppercase() }
    runCatching { secOhneNetz() }
    sec.value?.geschaefte?.filter { it.d >= ab }?.groupBy { it.k.uppercase() }?.forEach { (k, l) ->
        val a = nachKuerzel[k] ?: return@forEach
        SecInsider.trend(l, ab)?.let { aus[a.s] = it }
    }
    runCatching { bafinLaden(netz = false) }.getOrNull()?.let { d ->
        val deutsche = aktien.filter { it.c == "DE" }
        val l = d.eigengeschaefte.filter { it.d >= ab }
        val namen = namenZuSymbolen(l.filter { it.k.isNullOrBlank() }.map { it.e }, deutsche)
        l.groupBy { g -> g.k?.uppercase()?.let { nachKuerzel[it]?.s } ?: namen[g.e] }.forEach { (s, g) ->
            if (s != null && s !in aus) Bafin.trend(g, ab)?.let { aus[s] = it }
        }
    }
    for (land in EuInsider.LAENDER) {
        val d = runCatching { euLaden(land, netz = false) }.getOrNull() ?: continue
        val l = d.geschaefte.filter { it.d >= ab }
        if (l.isEmpty()) continue
        val namen = namenZuSymbolen(l.map { it.e }, aktien.filter { it.c == land })
        l.groupBy { namen[it.e] }.forEach { (s, g) -> if (s != null && s !in aus) EuInsider.trend(g, ab)?.let { aus[s] = it } }
    }
    return aus
}

// Gemeldete Leerverkaeufe (Anteil an allen Aktien in Prozent) aus den Listen auf dem Geraet, je Katalogaktie.
private suspend fun AppState.leerJeWert(aktien: List<Catalog.Stock>): Map<String, Double> {
    val aus = HashMap<String, Double>()
    for (land in listOf("FR", "SE", "GB")) {
        val d = runCatching { lpLaden(land, netz = false) }.getOrNull() ?: continue
        val titel = d.titel.values.filter { it.summe > 0 }
        val namen = namenZuSymbolen(titel.map { it.e }, aktien.filter { it.c == land })
        titel.forEach { t -> namen[t.e]?.let { s -> aus[s] = maxOf(aus[s] ?: 0.0, t.summe) } }
    }
    return aus
}

// Laender, die zur Region Europa zaehlen.
private val FINDER_EUROPA = setOf("DE","GB","FR","IT","ES","CH","NL","SE","DK","FI","AT","NO","IE","LU","BE","CZ","PT","PL")

// Universen, die nach Land oder Region aus dem Katalog gebildet werden (kein Index).
val FINDER_REGIONEN = setOf("ALLE","USA","EUROPA","DEUTSCHLAND","GROSSBRITANNIEN","SCHWEIZ","JAPAN","KANADA","ASIEN","SCHWELLEN")

// So viele der besten Werte bekommen die Einzelabrufe.
internal const val FINDER_DETAILS = 15

// Aktien eines Universums aus dem Katalog: Laender und Regionen ueber das Land, Indizes ueber die Mitgliedschaft.
fun finderAktienIm(code: String, k: Catalog.Data): List<Catalog.Stock> = when (code) {
    "ALLE" -> k.aktien
    "USA" -> k.aktien.filter { it.c == "US" }
    "EUROPA" -> k.aktien.filter { it.c in FINDER_EUROPA }
    "DEUTSCHLAND" -> k.aktien.filter { it.c == "DE" }
    "GROSSBRITANNIEN" -> k.aktien.filter { it.c == "GB" }
    "SCHWEIZ" -> k.aktien.filter { it.c == "CH" }
    "JAPAN" -> k.aktien.filter { it.c == "JP" }
    "KANADA" -> k.aktien.filter { it.c == "CA" }
    "ASIEN" -> k.aktien.filter { Region.ofCountry(it.c) == "ASIEN_PAZIFIK" }
    "SCHWELLEN" -> k.aktien.filter { Region.ofCountry(it.c) == "SCHWELLENLAENDER" }
    else -> k.aktien.filter { code in it.x }
}.distinctBy { it.s }

// Zuletzt gewaehltes Universum als eine Zeile Text merken, damit der Android-Hintergrundauftrag es kennt.
internal suspend fun AppState.finderWahlSchreiben(code: String) {
    withContext(Dispatchers.IO) { runCatching { SichereDatei(File(store.ordner, "finder-wahl.txt")).schreiben(code.toByteArray()) } }
}

internal suspend fun AppState.finderWahlLesen(): String? = withContext(Dispatchers.IO) {
    runCatching { SichereDatei(File(store.ordner, "finder-wahl.txt")).lesen().toString(Charsets.UTF_8).trim().ifBlank { null } }.getOrNull()
}

// Ausnahmen im app-weiten Scope abfangen, damit ein Netzwerkfehler die App nicht beendet; Abbrueche laufen durch.
private suspend fun sicher(block: suspend () -> Unit) {
    try { block() } catch (e: Exception) { if (e is CancellationException) throw e }
}

// Hintergrund-Auftrag des Aktienfinders: Kurse, Einzelabrufe und Jahresabschluesse laufen im app-weiten Scope, auch
// wenn der Bildschirm verlassen wird; der Bildschirm liest nur den Zustand. Ein neuer Start ersetzt den alten Auftrag.
object FinderLauf {

    // Universum, Daten und Fortschritt des laufenden oder letzten Durchlaufs.
    data class Zustand(
        val universum: String? = null,
        val daten: List<Aktienfinder.Daten> = emptyList(),
        val stand: Long = 0L,
        val laedt: Float? = null,
        val details: Float? = null,
        val bilanzAktiv: Boolean = false,
        val bilanzNeu: Int = 0,
        // [0.75] Der letzte Lauf bekam keine Antwort; die Daten sind die vorherigen
        val ohneAntwort: Boolean = false,
    )

    val zustand = MutableStateFlow(Zustand(AktienfinderSpeicher.universum, AktienfinderSpeicher.daten, AktienfinderSpeicher.zeit))

    // Nur ein Lade- oder Bilanzdurchlauf gleichzeitig; Einzelabrufe duerfen parallel laufen.
    private val sperre = Mutex()
    private var lauf: Job? = null
    private var detailLauf: Job? = null
    // Fuer welchen Markt der Hauptauftrag laeuft; null, wenn keiner lief (der Android-Hintergrundauftrag zaehlt nicht dazu).
    private var laufUniversum: String? = null

    // Aktiv auch, solange der Android-Hintergrundauftrag die Sperre haelt; dann startet kein zweiter Durchlauf.
    val aktiv: Boolean get() = lauf?.isActive == true || sperre.isLocked

    fun starten(state: AppState, universum: String, aktien: List<Catalog.Stock>, gewichte: Map<Aktienfinder.Bereich, Double>) {
        lauf?.cancel()
        detailLauf?.cancel()
        laufUniversum = universum
        lauf = state.hintergrundScope.launch {
            // Universum sofort beanspruchen und die Wahl gleich merken, auch wenn der Lauf noch auf die Sperre wartet
            zustand.update {
                it.copy(
                    universum = universum, daten = if (it.universum == universum) it.daten else emptyList(), laedt = 0f, details = null, bilanzAktiv = false,
                    ohneAntwort = false,
                )
            }
            state.finderWahlSchreiben(universum)
            sperre.withLock { ladenKern(state, universum, aktien, gewichte) }
        }
    }

    fun bilanzStarten(state: AppState, universum: String, gewichte: Map<Aktienfinder.Bereich, Double>) {
        val z = zustand.value
        if (z.universum != universum || z.daten.isEmpty()) return
        // Laeuft noch der Auftrag eines anderen Markts und laedt gerade kein Markt neu, wird er abgebrochen, damit der
        // neue Markt drankommt; der neue Auftrag wartet dann kurz auf die Sperre. Sonst kuemmert sich der laufende.
        if (laufUniversum != null && laufUniversum != universum && z.laedt == null) lauf?.cancel() else if (aktiv) return
        laufUniversum = universum
        lauf = state.hintergrundScope.launch {
            sperre.withLock { sicher { bilanzKern(state, universum, gewichte) } }
        }
    }

    fun detailsStarten(state: AppState, symbole: List<String>) {
        if (symbole.isEmpty() || detailLauf?.isActive == true) return
        val universum = zustand.value.universum ?: return
        detailLauf = state.hintergrundScope.launch { sicher { detailsKern(state, universum, symbole) } }
    }

    // Fuer Vorlagen (5.94): Einzelabrufe und Tageskurse der noch ungeprueften Werte, danach neu zusammensetzen.
    fun vorlageStarten(state: AppState, einzeln: List<String>, kerzen: List<String>) {
        if ((einzeln.isEmpty() && kerzen.isEmpty()) || detailLauf?.isActive == true) return
        val universum = zustand.value.universum ?: return
        detailLauf = state.hintergrundScope.launch {
            sicher {
                zustand.update { if (it.universum == universum) it.copy(details = 0f) else it }
                try {
                    val teil = if (einzeln.isNotEmpty() && kerzen.isNotEmpty()) 0.5f else 1f
                    if (einzeln.isNotEmpty()) state.aktienfinderDetails(einzeln, emptyMap()) { p ->
                        zustand.update { if (it.universum == universum) it.copy(details = p * teil) else it }
                    }
                    val start = if (einzeln.isNotEmpty()) teil else 0f
                    state.aktienfinderKerzen(kerzen) { p -> zustand.update { if (it.universum == universum) it.copy(details = start + p * (1f - start)) else it } }
                    auffrischen(state, universum)
                } finally {
                    zustand.update { if (it.universum == universum) it.copy(details = null) else it }
                }
            }
        }
    }

    // Landrisiko ist eingetroffen: die Punkte in den Daten des gewaehlten Markts neu zuordnen.
    fun landNeu(state: AppState) {
        val universum = zustand.value.universum ?: return
        auffrischen(state, universum)
    }

    fun abbrechen() { lauf?.cancel() }

    // Daten aus dem Speicher in den Zustand uebernehmen, wenn der Hintergrund-Auftrag diesen Markt zuletzt geladen hat.
    fun uebernehmen(universum: String) {
        if (zustand.value.universum == universum || AktienfinderSpeicher.universum != universum) return
        zustand.update { it.copy(universum = universum, daten = AktienfinderSpeicher.daten, stand = AktienfinderSpeicher.zeit) }
    }

    // Fuer den Android-Hintergrundauftrag: laeuft im Worker-Scope, damit WorkManager ihn bei Ablauf der Zeit abbrechen
    // kann. Fasst weder den Bildschirm-Zustand noch die gemerkte Wahl an; das Ergebnis steht im AktienfinderSpeicher
    // und in finder-bilanz.json. Wartet ein echter Auftrag auf die Sperre, gibt dieser Lauf den Rest auf.
    suspend fun vorladen(state: AppState, universum: String, aktien: List<Catalog.Stock>, gewichte: Map<Aktienfinder.Bereich, Double>) {
        sperre.withLock {
            sicher {
                val d = state.aktienfinderLaden(universum, aktien) { }
                val fehlend = withContext(Dispatchers.Default) { Aktienfinder.fuerBilanz(Aktienfinder.bewerten(d, gewichte)) { FinderBilanzSpeicher.vorhanden(it) } }
                // In Stuecken holen, damit ein wartender Bildschirmauftrag nicht lange blockiert
                fehlend.chunked(25).forEach { teil ->
                    if (zustand.value.laedt != null) return@sicher
                    state.finderBilanzHolen(teil) { }
                }
                // Finder-Alarme dieses Markts mit Nachladen der offenen Werte (5.94)
                if (zustand.value.laedt == null) state.finderAlarmePruefen(universum, d, mitAbruf = true)
            }
        }
    }

    private suspend fun ladenKern(state: AppState, universum: String, aktien: List<Catalog.Stock>, gewichte: Map<Aktienfinder.Bereich, Double>) {
        try {
            sicher {
                val d = try {
                    state.aktienfinderLaden(universum, aktien) { p -> zustand.update { if (it.universum == universum) it.copy(laedt = p) else it } }
                } catch (e: FinderOhneAntwort) {
                    // Kein neuer Stand und keine Alarmpruefung; die Seite zeigt den Hinweis mit Knopf fuer einen neuen Versuch
                    zustand.update { if (it.universum == universum) it.copy(ohneAntwort = true) else it }
                    return@sicher
                }
                // Hat ein anderer Lauf inzwischen den Markt gewechselt, zaehlt dieses Ergebnis nicht mehr
                if (zustand.value.universum != universum) return@sicher
                zustand.update { it.copy(daten = d, stand = AktienfinderSpeicher.zeit, laedt = null) }
                // Widgets zum Aktienfinder merken das Ergebnis, auch wenn die App danach geschlossen wird
                state.widgetsNachladen()
                val besten = withContext(Dispatchers.Default) { Aktienfinder.fuerDetails(Aktienfinder.bewerten(d, gewichte), FINDER_DETAILS) }
                detailsKern(state, universum, besten)
                bilanzKern(state, universum, gewichte)
                // Finder-Alarme mit dem, was jetzt geladen ist (auch auf dem Desktop ohne Hintergrundauftrag)
                if (zustand.value.universum == universum) state.finderAlarmePruefen(universum, zustand.value.daten, mitAbruf = false)
            }
        } finally {
            // Nur aufraeumen, solange kein neuerer Lauf den Zustand schon uebernommen hat
            zustand.update { if (it.universum == universum) it.copy(laedt = null, details = null) else it }
        }
    }

    private suspend fun detailsKern(state: AppState, universum: String, symbole: List<String>) {
        if (symbole.isEmpty()) return
        zustand.update { if (it.universum == universum) it.copy(details = 0f) else it }
        try {
            state.aktienfinderDetails(symbole, (Catalog.loaded() ?: return).aktien.associateBy { it.s }) { p -> zustand.update { if (it.universum == universum) it.copy(details = p) else it } }
            auffrischen(state, universum)
        } finally {
            zustand.update { if (it.universum == universum) it.copy(details = null) else it }
        }
    }

    // Neu zusammensetzen ohne Abruf; bis auf das Setzen des Speichers rein und deshalb wiederholbar.
    private fun auffrischen(state: AppState, universum: String) {
        zustand.update { if (it.universum == universum) it.copy(daten = state.aktienfinderAuffrischen(it.daten)) else it }
    }

    private suspend fun bilanzKern(state: AppState, universum: String, gewichte: Map<Aktienfinder.Bereich, Double>) {
        state.finderBilanzVorbereiten()
        auffrischen(state, universum)
        val fehlend = withContext(Dispatchers.Default) { Aktienfinder.fuerBilanz(Aktienfinder.bewerten(zustand.value.daten, gewichte)) { FinderBilanzSpeicher.vorhanden(it) } }
        if (fehlend.isEmpty()) return
        // Hat ein anderer Lauf den Markt gewechselt, gibt dieser die Sperre frei, statt fuer den alten weiter zu holen
        if (zustand.value.universum != universum) return
        zustand.update { it.copy(bilanzAktiv = true) }
        val seit = AtomicInteger()
        try {
            state.finderBilanzHolen(fehlend) {
                zustand.update { z -> if (z.universum == universum) z.copy(bilanzNeu = z.bilanzNeu + 1) else z }
                if (seit.incrementAndGet() % 10 == 0) auffrischen(state, universum)
            }
            auffrischen(state, universum)
        } finally {
            zustand.update { if (it.universum == universum) it.copy(bilanzAktiv = false) else it }
        }
    }
}

// Fuer den Android-Hintergrundauftrag: zuletzt gewaehlten Markt auffrischen und fehlende Jahresabschluesse nachholen.
suspend fun AppState.finderImHintergrund() {
    if (FinderLauf.aktiv) return
    val code = finderWahlLesen() ?: return
    val aktien = finderAktienIm(code, Catalog.get())
    if (aktien.isEmpty()) return
    FinderLauf.vorladen(this, code, aktien, Aktienfinder.Bereich.entries.associateWith { it.gewicht.toDouble() })
}

// ------------------------------------------------------------ Finder-Alarme (5.94)

// Alarm auf eine Vorlage im Markt universum: grenzen sind die Nummern der eingeschalteten Grenzen der Vorlage, bekannt
// die gemerkten Treffer (null vor der ersten Pruefung; dann wird nur gemerkt, nicht gemeldet).
@kotlinx.serialization.Serializable
data class FinderAlarm(val universum: String, val vorlage: String, val grenzen: List<Int>, val bekannt: List<String>? = null)

private val alarmJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
private val alarmListe = kotlinx.serialization.builtins.ListSerializer(FinderAlarm.serializer())
private val alarmSperre = Mutex()

// Hoechstens so viele Werte holt eine Hintergrundpruefung je Alarm nach (Tageskurse in Zwanzigern, Profile einzeln).
private const val ALARM_KERZEN = 200
private const val ALARM_EINZELN = 40

suspend fun AppState.finderAlarmeLesen(): List<FinderAlarm> = withContext(Dispatchers.IO) {
    runCatching {
        alarmJson.decodeFromString(alarmListe, SichereDatei(File(store.ordner, "finder-alarme.json")).lesen().toString(Charsets.UTF_8))
    }.getOrDefault(emptyList())
}

private suspend fun AppState.finderAlarmeSchreiben(l: List<FinderAlarm>) {
    withContext(Dispatchers.IO) { runCatching { SichereDatei(File(store.ordner, "finder-alarme.json")).schreiben(alarmJson.encodeToString(alarmListe, l).toByteArray()) } }
}

// Alarm fuer Vorlage und Markt an- oder ausschalten; true, wenn er danach an ist.
suspend fun AppState.finderAlarmUmschalten(universum: String, vorlage: String, grenzen: List<Int>): Boolean = alarmSperre.withLock {
    val alt = finderAlarmeLesen()
    val da = alt.any { it.universum == universum && it.vorlage == vorlage }
    finderAlarmeSchreiben(if (da) alt.filterNot { it.universum == universum && it.vorlage == vorlage } else alt + FinderAlarm(universum, vorlage, grenzen))
    !da
}

// Liste ohne den Alarm fuer Markt und Vorlage.
fun finderAlarmeOhne(l: List<FinderAlarm>, universum: String, vorlage: String): List<FinderAlarm> =
    l.filterNot { it.universum == universum && it.vorlage == vorlage }

// Alarm abschalten (Glocke in der Liste der Mitteilungen); liefert die verbleibenden Alarme.
suspend fun AppState.finderAlarmAus(universum: String, vorlage: String): List<FinderAlarm> = alarmSperre.withLock {
    val neu = finderAlarmeOhne(finderAlarmeLesen(), universum, vorlage)
    finderAlarmeSchreiben(neu)
    neu
}

// Eingeschaltete Grenzen eines Alarms; ohne gueltige Nummer alle der Vorlage.
fun finderAlarmGrenzen(v: FinderVorlagen.Vorlage, a: FinderAlarm): List<FinderVorlagen.Grenze> =
    a.grenzen.mapNotNull { v.grenzen.getOrNull(it) }.ifEmpty { v.grenzen }

// Neue Merkliste: bisherige Treffer und neue, ohne die Werte, die jetzt sicher nicht mehr passen. Werte, die nur noch
// auf Daten warten, bleiben gemerkt, damit sie nach dem naechsten Abruf nicht erneut gemeldet werden.
fun finderAlarmAbgleich(daten: List<Aktienfinder.Daten>, v: FinderVorlagen.Vorlage, a: FinderAlarm, jetzt: Long): Pair<List<String>, List<String>> {
    val g = finderAlarmGrenzen(v, a)
    val pruefung = de.troetenrepublik.app.engine.FinderRaster.pruefen(daten.filter { !v.nurUs || it.land == "US" }, g, v, jetzt)
    val treffer = pruefung.filterValues { it == FinderVorlagen.Pruefung.JA }.keys
    val vorher = a.bekannt.orEmpty().toSet()
    val neu = if (a.bekannt == null) emptyList() else treffer.filter { it !in vorher }
    val bekannt = (vorher + treffer).filter { pruefung[it] != FinderVorlagen.Pruefung.NEIN && it in pruefung }
    return bekannt to neu
}

// Prueft die Alarme eines Markts und meldet neue Treffer. mitAbruf: fuer noch offene Werte erst Tageskurse und Profil
// holen (Hintergrundauftrag); sonst zaehlt nur, was schon geladen ist. Die Abrufe laufen ausserhalb der Sperre, damit
// das Umschalten eines Alarms nicht auf sie wartet; Abgleich und Schreiben lesen die Datei in der Sperre frisch.
suspend fun AppState.finderAlarmePruefen(universum: String, daten: List<Aktienfinder.Daten>, mitAbruf: Boolean) {
    val vorab = finderAlarmeLesen().filter { it.universum == universum }
    if (vorab.isEmpty() || daten.isEmpty()) return
    var d = aktienfinderAuffrischen(daten)
    val jetzt = System.currentTimeMillis()
    if (mitAbruf) for (a in vorab) {
        val v = FinderVorlagen.vorlage(a.vorlage) ?: continue
        val g = finderAlarmGrenzen(v, a)
        val stufen = FinderVorlagen.stufen(g)
        fun offen() = d.filter { !v.nurUs || it.land == "US" }.let { l ->
            val p = de.troetenrepublik.app.engine.FinderRaster.pruefen(l, g, v, jetzt)
            l.filter { p[it.symbol] == FinderVorlagen.Pruefung.OFFEN }
        }
        if (FinderVorlagen.Stufe.KERZEN in stufen) {
            val kerzen = offen().filter { !it.mitKerzen && !kuerzlichOhneAntwort("k:${it.symbol}", jetzt) }.take(ALARM_KERZEN).map { it.symbol }
            if (kerzen.isNotEmpty()) { aktienfinderKerzen(kerzen) { }; d = aktienfinderAuffrischen(d) }
        }
        if (FinderVorlagen.Stufe.EINZEL in stufen) {
            val einzeln = offen().filter {
                !it.mitDetails && (it.mitKerzen || FinderVorlagen.Stufe.KERZEN !in stufen) && !kuerzlichOhneAntwort("d:${it.symbol}", jetzt)
            }.take(ALARM_EINZELN).map { it.symbol }
            if (einzeln.isNotEmpty()) { aktienfinderDetails(einzeln, emptyMap()) { }; d = aktienfinderAuffrischen(d) }
        }
    }
    val namen = d.associate { it.symbol to it.name.ifBlank { it.symbol } }
    alarmSperre.withLock {
        val alle = finderAlarmeLesen()
        val hier = alle.filter { it.universum == universum }
        if (hier.isEmpty()) return@withLock
        val neu = hier.map { a ->
            val v = FinderVorlagen.vorlage(a.vorlage) ?: return@map a
            val (bekannt, frisch) = finderAlarmAbgleich(d, v, a, jetzt)
            if (frisch.isNotEmpty()) finderAlarmMelden(S.afAlarmTitel(S.afVorlage(v.id)), S.afAlarmText(frisch.take(5).map { namen[it] ?: it }, frisch.size - 5))
            a.copy(bekannt = bekannt)
        }
        finderAlarmeSchreiben(alle.filter { it.universum != universum } + neu)
    }
}

// Wie lange Kennzahlen fuer die Marktbreite der Gesamtstimmung gueltig sind (wie die Einzelabrufe des Aktienfinders)
private const val BREITE_MS = 12 * 3_600_000L

// Kennzahlen der S&P-500-Werte fuer die Marktbreite der Gesamtstimmung (5.95) aus dem Zwischenspeicher des Aktienfinders.
// holen: fehlende oder ueber zwoelf Stunden alte Werte mit demselben Sammelabruf nachladen (nie im Hintergrund).
// Rueckgabe: Kennzahlen je Symbol (nur Eintraege bis zwoelf Stunden) und der aelteste Zeitstempel davon (0 ohne Daten).
internal suspend fun AppState.sp500Kennzahlen(symbole: List<String>, holen: Boolean): Pair<Map<String, Map<String, Double>>, Long> {
    val jetzt = System.currentTimeMillis()
    if (holen) for (t in symbole.filter { s -> finderKennzahlen[s]?.let { jetzt - it.first < BREITE_MS } != true }.chunked(100))
        kennzahlen(t, FINDER_FELDER).forEach { (s, m) -> finderKennzahlen[s] = jetzt to m }
    val aus = LinkedHashMap<String, Map<String, Double>>()
    var aeltester = 0L
    for (s in symbole) {
        val e = finderKennzahlen[s] ?: continue
        if (jetzt - e.first >= BREITE_MS) continue
        aus[s] = e.second
        if (aeltester == 0L || e.first < aeltester) aeltester = e.first
    }
    return aus to aeltester
}
