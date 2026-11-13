package de.troetenrepublik.app

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.Abhaengigkeiten
import de.troetenrepublik.app.engine.Durchschau
import de.troetenrepublik.app.engine.Einflussverzeichnis
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Empfindlichkeit
import de.troetenrepublik.app.engine.Engpaesse
import de.troetenrepublik.app.engine.Enso
import de.troetenrepublik.app.engine.GlobusGesamt
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.Katastrophen
import de.troetenrepublik.app.engine.Konflikte
import de.troetenrepublik.app.engine.Lagebild
import de.troetenrepublik.app.engine.LandRisiko
import de.troetenrepublik.app.engine.Spannungen
import de.troetenrepublik.app.engine.Taetigkeit
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.KursQuellen
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

// [Grosses Ganzes 0.73] Laden der Eingaben fuer Karte "Abhaengigkeiten und Risiken", Wirkungsnetz und Lagebild. Alles
// nutzt Abrufe und Speicher der App; nichts wird eigens dauerhaft nachgeladen. Einzelne Ausfaelle lassen nur die
// betroffene Zeile weg.

// Branche (Schluessel wie in Industry) aus dem Yahoo-Branchennamen, wenn der Katalog keine hat; erste Uebereinstimmung gilt
private val YAHOO_BRANCHEN: List<Pair<String, String>> = listOf(
    "semiconductor" to "HALBLEITER", "auto manufacturers" to "FAHRZEUGE", "auto parts" to "FAHRZEUGE", "airlines" to "LUFTFAHRT",
    "aerospace" to "LUFTFAHRT", "oil & gas" to "OEL_GAS", "uranium" to "BERGBAU", "gold" to "BERGBAU", "silver" to "BERGBAU",
    "copper" to "BERGBAU", "metals & mining" to "BERGBAU", "steel" to "BERGBAU", "aluminum" to "BERGBAU", "coal" to "BERGBAU",
    "chemicals" to "CHEMIE", "agricultural inputs" to "CHEMIE", "banks" to "BANKEN", "insurance" to "VERSICHERUNG",
    "reit" to "IMMOBILIEN", "real estate" to "IMMOBILIEN", "utilities" to "VERSORGER", "software" to "SOFTWARE",
    "computer hardware" to "COMPUTER", "consumer electronics" to "ELEKTRO", "electronic components" to "ELEKTRO",
    "electrical equipment" to "ELEKTRO", "biotechnology" to "BIOTECH", "drug manufacturers" to "PHARMA", "medical" to "GESUNDHEIT",
    "healthcare" to "GESUNDHEIT", "telecom" to "TELEKOM", "communication equipment" to "TELEKOM", "machinery" to "MASCHINEN",
    "engineering & construction" to "BAU", "building" to "BAU", "construction" to "BAU", "marine shipping" to "VERKEHR",
    "trucking" to "VERKEHR", "railroads" to "VERKEHR", "freight" to "VERKEHR", "packaged foods" to "NAHRUNG", "beverages" to "NAHRUNG",
    "farm products" to "NAHRUNG", "confectioners" to "NAHRUNG", "solar" to "UMWELT", "waste management" to "UMWELT",
)

internal fun branchenAusYahoo(industry: String?, sector: String? = null): String? {
    val i = industry?.lowercase().orEmpty()
    if (i.isNotBlank()) YAHOO_BRANCHEN.firstOrNull { i.contains(it.first) }?.let { return it.second }
    return null
}

// Sitz, Taetigkeitsland und Boerse eines einzelnen Werts, auf demselben Weg wie die Streuung im Depot
suspend fun AppState.wertHerkunft(symbol: String, profile: MarketApi.Profile?): Taetigkeit.Herkunft? = runCatching {
    val t = Taetigkeit
    val katalog = withContext(Dispatchers.IO) { Catalog.get() }.aktien.firstOrNull { it.s == symbol }
    val katSitz = katalog?.c?.takeIf { it.isNotBlank() } ?: if (KursQuellen.istExtern(symbol)) t.boerse(symbol) else null
    val karte = globusKarte() ?: return@runCatching Taetigkeit.Herkunft(katSitz, null, t.boerse(symbol))
    val namen = Taetigkeit.Namen(karte.laender.filter { it.a2.isNotEmpty() }.associate { it.en to it.a2 })
    val text = Plattform.asset("globus/taetigkeit.json").decodeToString()
    val sitz = katSitz ?: profile?.country?.takeIf { it.isNotBlank() }?.let { namen.code(it) }
    val roh = t.herkunft(sitz, symbol, profile?.description.orEmpty(), t.ressource(profile?.sector, katalog?.i), namen)
    t.veredeln(roh, symbol, t.lesenDatei(text), account.value.herkunftWahl[symbol], t.lesenZentralen(text))
}.getOrNull()

// Wertbild eines Katalogwerts oder eines Werts mit Herkunft; die Branche kommt aus dem Katalog, sonst aus Yahoo
// zusatz: weitere Beschreibungen des Werts (Wikipedia, englisch und deutsch), damit die Zuordnung mit dem arbeitet, was die
// Seite unter "Informationen" zeigt, auch wenn Yahoo kein Profil liefert
fun wertbildVon(symbol: String, herkunft: Taetigkeit.Herkunft?, profile: MarketApi.Profile?, zusatz: String = ""): Wertbild {
    val katalog = Catalog.loaded()?.aktien?.firstOrNull { it.s == symbol }
    val branche = katalog?.i?.takeIf { it.isNotBlank() } ?: branchenAusYahoo(profile?.industry, profile?.sector)
    return Wertbild(
        symbol, branche, herkunft?.land ?: katalog?.c?.takeIf { it.isNotBlank() }, herkunft?.sitz ?: katalog?.c?.takeIf { it.isNotBlank() },
        industrie = profile?.industry?.takeIf { it.isNotBlank() }, beschreibung = listOf(profile?.description.orEmpty(), zusatz).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() },
    )
}

// Leichtes Wertbild nur aus dem Katalog (ohne Abrufe), etwa fuer Positionen im Depot und ETF-Bestandteile
fun katalogWertbild(symbol: String): Wertbild {
    val k = Catalog.loaded()?.aktien?.firstOrNull { it.s == symbol }
    return Wertbild(symbol, k?.i?.takeIf { it.isNotBlank() }, k?.c?.takeIf { it.isNotBlank() }, k?.c?.takeIf { it.isNotBlank() })
}

// Karte der Weltlaender fuer Namen und Seitenlinks; null ohne Daten
suspend fun AppState.landNamen(): Map<String, Pair<String, String>> = runCatching {
    // Teilen sich Gebiete ein Kuerzel (Australien und seine Aussengebiete, Brasilien und eine Insel), gilt das Hauptgebiet
    globusKarte()?.let { k -> k.laender.map { it.a2 }.filter { it.isNotEmpty() }.distinct().mapNotNull { a2 -> k.nachA2(a2)?.let { a2 to (it.de to it.en) } }.toMap() }.orEmpty()
}.getOrDefault(emptyMap())

// Spannungswerte, die schon gespeichert sind (ohne Abruf)
suspend fun AppState.spannungenGespeichert(): Map<String, Spannungen.Wert> = withContext(Dispatchers.IO) {
    Spannungen.GEBIETE.mapNotNull { g -> spannungGespeichert(g.id)?.let { g.id to it } }.toMap()
}

// Kursempfindlichkeit eines Werts gegen Markt und die Stellvertreter-Reihen; Ergebnis null bei zu kurzer Reihe
suspend fun AppState.kursempfindlichkeit(symbol: String): Empfindlichkeit.Ergebnis? = coroutineScope {
    val wert = async { kurseNativ(symbol) }
    val markt = async { kurseNativ(Empfindlichkeit.Faktor.MARKT.symbol) }
    val weitere = Empfindlichkeit.Faktor.entries.filter { it != Empfindlichkeit.Faktor.MARKT }.map { f -> f to async { kurseNativ(f.symbol) } }
    val w = wert.await(); val m = markt.await()
    val reihen = weitere.mapNotNull { (f, a) -> a.await().takeIf { it.size >= 200 }?.let { f to it } }.toMap()
    if (w.size < 200 || m.size < 200) null else withContext(Dispatchers.Default) { Empfindlichkeit.rechnen(w, m, reihen, 0L) }
}

// Alles fuer die Zeilen einer Aktie. nurLeicht: ohne die Abrufe der Live-Staende und der Kursempfindlichkeit.
suspend fun AppState.abhaengigkeitenEingabe(w: Wertbild, herkunft: Taetigkeit.Herkunft?, usWert: Boolean): Abhaengigkeiten.Eingabe = coroutineScope {
    val land = w.land
    val karteJob = async { runCatching { globusKarte() }.getOrNull() }
    val engpass = async { runCatching { engpassGespeichert() }.getOrNull() ?: runCatching { engpassUebersicht() }.getOrNull() }
    val spannung = async { runCatching { spannungenGespeichert() }.getOrDefault(emptyMap()) }
    val empf = async { runCatching { kursempfindlichkeit(w.symbol) }.getOrNull() }
    val landJob = async { if (land != null) runCatching { finderLandRisiko() } }
    val karte = karteJob.await()
    val l = land?.let { karte?.nachA2(it) }
    landJob.await()
    val landPunkte = land?.let { finderLandFakten?.get(it)?.let { f -> LandRisiko.punkte(f) } }
    val imf = runCatching { imfGespeichert() ?: imfDaten() }.getOrNull()
    val jahr = LocalDate.now().year
    // Dieselben Zusatzzahlen wie die Globus-Ebene, damit die Punkte uebereinstimmen
    val zusatz = if (l == null) null else (globusGesamtGespeichert() ?: runCatching { kotlinx.coroutines.withTimeoutOrNull(10_000L) { globusGesamtZusatz() } }
        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull())?.get(l.a3)
    val gesamt = if (l != null && imf != null) GlobusGesamt.rechnen(imf, l.a3, jahr, zusatz) else null
    val kat = if (l != null) runCatching { risikoKatastrophen() }.getOrNull()?.let { Katastrophen.fuer(it, l.a3, LocalDate.now()) } else null
    val u = engpass.await()
    val e = empf.await()
    Abhaengigkeiten.Eingabe(
        wert = w, taetigkeit = herkunft?.taetig, gesamtbild = gesamt?.punkte, gesamtbildJahr = gesamt?.jahr ?: 0,
        landPunkte = landPunkte, landFakten = land?.let { finderLandFakten?.get(it) },
        katastrophen = kat?.size, katastrophenRot = kat?.count { it.rot } ?: 0,
        engpaesse = u?.lage.orEmpty(),
        spannungen = spannung.await(), empfindlichkeit = e, empfindlichkeitZuKurz = e == null, usWert = usWert,
        landStand = LocalDate.now().toEpochDay(),
    )
}

// ETF: die groessten Positionen mit Wertbild aus dem Katalog und ihrem Anteil
suspend fun AppState.etfPositionen(symbol: String): Pair<List<Pair<Wertbild, Double>>, Double>? {
    val b = etfBestand(symbol) ?: return null
    if (b.positionen.isEmpty()) return null
    return b.positionen.map { katalogWertbild(it.symbol) to it.anteil } to b.positionen.sumOf { it.anteil }
}

// Bestand fuer das Wirkungsnetz: Depot (direkt gehaltene Aktien und Firmen in ETFs mit ihrem Anteil am ganzen Depot) und
// Watchlist (ohne Anteil). Derivate und die ETFs selbst zaehlen nicht.
suspend fun AppState.wirkungsBestand(): Pair<List<Bestand>, List<Bestand>> {
    val pos = valuation.value.positions.filter { !it.position.isDerivative && it.value > 0 }
    val summe = pos.sumOf { it.value }
    val gewichte = pos.groupBy { it.position.symbol }.mapValues { (_, l) -> if (summe > 0) l.sumOf { it.value } / summe else 0.0 }
    val namen = pos.associate { it.position.symbol to it.position.name }
    val durch = runCatching { depotDurchschau() }.getOrDefault(emptyList())
    val depot = LinkedHashMap<String, Bestand>()
    for ((s, g) in gewichte) {
        if (Symbols.typeOf(s) != AssetType.STOCK) continue
        depot[s] = Bestand(katalogWertbild(s), namen[s] ?: s, g)
    }
    for (d in durch) {
        val alt = depot[d.symbol]
        depot[d.symbol] = Bestand(katalogWertbild(d.symbol), d.name, maxOf(alt?.gewicht ?: 0.0, d.gesamt))
    }
    val watch = account.value.watchlist.filter { it !in depot && Symbols.typeOf(it) == AssetType.STOCK }
        .map { Bestand(katalogWertbild(it), Symbols.find(it)?.name ?: it, 0.0) }
    return depot.values.toList() to watch
}

// Messungen der Kursempfindlichkeit fuer die groessten Positionen (hoechstens zehn), fuer die gemessene Richtung im Netz
suspend fun AppState.wirkungsMessungen(bestand: List<Bestand>): Map<String, Empfindlichkeit.Ergebnis> = coroutineScope {
    bestand.sortedByDescending { it.gewicht }.take(10).map { b -> async { b.wert.symbol to runCatching { kursempfindlichkeit(b.wert.symbol) }.getOrNull() } }
        .awaitAll().mapNotNull { (s, e) -> e?.let { s to it } }.toMap()
}

// Zeitgrenze je Abruf der Lage: ein Abruf, der haengt, haelt die Seite nicht auf; was fehlt, bleibt vom letzten Stand stehen
private const val LAGE_FRIST_MS = 15_000L

private suspend fun <T : Any> lageFrist(ms: Long, block: suspend () -> T?): T? =
    try { kotlinx.coroutines.withTimeoutOrNull(ms) { block() } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }

// Gespeicherter Stand einer Risiko-Quelle ohne Abruf (Datei risiken/<name>.json: "<Abrufzeit>\n<Inhalt>")
private suspend fun <T : Any> AppState.risikoGespeichert(name: String, ser: kotlinx.serialization.KSerializer<T>): T? = withContext(Dispatchers.IO) {
    runCatching {
        val text = SichereDatei(File(File(store.ordner, "risiken"), "$name.json")).lesen().toString(Charsets.UTF_8)
        de.troetenrepublik.app.engine.Laenderrisiken.lesen(ser, text.substringAfter('\n'))
    }.getOrNull()
}

// Eingabe des Lagebilds aus den Speichern und Abrufen der App.
// gespeichert = true: nur was schon auf dem Geraet liegt, ohne Netz (sofortige erste Anzeige).
// Sonst laufen alle Abrufe gleichzeitig, jeder mit Zeitgrenze und mit dem Zwischenspeicher der App als Rueckfall; die
// Durchbrueche (viele Abrufe, nur eine Zahl der Seite) werden nie abgewartet, wenn schon ein Stand da ist.
suspend fun AppState.lagebildEingabe(gespeichert: Boolean = false): Lagebild.Eingabe = coroutineScope {
    val jetzt = System.currentTimeMillis()
    if (gespeichert) {
        val konflikte = async { runCatching { risikoKonflikte() }.getOrNull() }
        val spannung = async { runCatching { spannungenGespeichert() }.getOrDefault(emptyMap()) }
        val engpass = async { runCatching { engpassGespeichert() }.getOrNull() }
        val status = async { risikoGespeichert("enso-status", Enso.Status.serializer()) }
        val index = async { risikoGespeichert("enso-index", Enso.Index.serializer()) }
        val kat = async { risikoGespeichert("gdacs", Katastrophen.Stand.serializer()) }
        return@coroutineScope Lagebild.Eingabe(
            jetzt = jetzt, imf = imfGespeichert(), jahr = LocalDate.now().year, konflikte = konflikte.await(),
            spannungen = spannung.await(), engpaesse = engpass.await(), ensoStatus = status.await(), ensoIndex = index.await(),
            katastrophen = kat.await(), durchbrueche = durchbruecheStand(),
        )
    }
    val imf = async { lageFrist(LAGE_FRIST_MS) { imfDaten() } ?: imfGespeichert() }
    val zins = async { lageFrist(LAGE_FRIST_MS) { zinsErwartung() } }
    val konflikte = async { runCatching { risikoKonflikte() }.getOrNull() }
    val spannung = async { runCatching { spannungenGespeichert() }.getOrDefault(emptyMap()) }
    val engpass = async { lageFrist(LAGE_FRIST_MS) { engpassUebersicht() } ?: runCatching { engpassGespeichert() }.getOrNull() }
    val status = async { lageFrist(LAGE_FRIST_MS) { ensoStatus() } }
    val index = async { lageFrist(LAGE_FRIST_MS) { ensoIndex() } }
    val kat = async { lageFrist(LAGE_FRIST_MS) { risikoKatastrophen() } }
    val db = async {
        val stand = durchbruecheStand()
        if (stand != null) { durchbruecheImHintergrund(); stand }
        else (lageFrist(4_000L) { durchbrueche() } ?: run { durchbruecheImHintergrund(); null })
    }
    Lagebild.Eingabe(
        jetzt = jetzt, imf = imf.await(), jahr = LocalDate.now().year, zins = zins.await(), konflikte = konflikte.await(),
        spannungen = spannung.await(), engpaesse = engpass.await(), ensoStatus = status.await(), ensoIndex = index.await(),
        katastrophen = kat.await(), durchbrueche = db.await(),
    )
}

// Zuletzt bekannter Stand der Lage: im Speicher, sonst aus lagebild-stand.json
@Volatile private var lagebildLetzterStand: Lagebild.Stand? = null

// Zuletzt bekannter Stand, nur aus dem Speicher (sofort, ohne Datei): fuer den ersten Aufbau der Seite ohne Sprung
fun AppState.lagebildImSpeicher(): Lagebild.Stand? = lagebildLetzterStand

suspend fun AppState.lagebildLetzter(): Lagebild.Stand? {
    lagebildLetzterStand?.let { return it }
    val s = withContext(Dispatchers.IO) {
        runCatching { Lagebild.lesenStand(SichereDatei(File(store.ordner, "lagebild-stand.json")).lesen().toString(Charsets.UTF_8)) }.getOrNull()
    }
    if (s != null && lagebildLetzterStand == null) lagebildLetzterStand = s
    return lagebildLetzterStand
}

suspend fun AppState.lagebildSpeichern(s: Lagebild.Stand) {
    if (s.aussagen.isEmpty()) return
    lagebildLetzterStand = s
    withContext(Dispatchers.IO) {
        runCatching { SichereDatei(File(store.ordner, "lagebild-stand.json")).schreiben(Lagebild.schreibenStand(s).toByteArray(Charsets.UTF_8)) }
    }
}

// Stand beim letzten Oeffnen der Lagebild-Seite (lagebild.json im Speicherordner)
private fun AppState.lagebildDatei() = SichereDatei(File(store.ordner, "lagebild.json"))

suspend fun AppState.lagebildGemerkt(): Lagebild.Gemerkt? = withContext(Dispatchers.IO) {
    runCatching { Lagebild.lesen(lagebildDatei().lesen().toString(Charsets.UTF_8)) }.getOrNull()
}

suspend fun AppState.lagebildMerken(g: Lagebild.Gemerkt) = withContext(Dispatchers.IO) {
    runCatching { lagebildDatei().schreiben(Lagebild.schreiben(g).toByteArray(Charsets.UTF_8)) }
}
