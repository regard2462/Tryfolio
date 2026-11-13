package de.troetenrepublik.app

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.Netz
import de.troetenrepublik.app.engine.NetzZulieferer
import de.troetenrepublik.app.engine.Rohstoffprofil
import de.troetenrepublik.app.market.Behoerden
import de.troetenrepublik.app.market.FirmaWikidata
import de.troetenrepublik.app.market.Gleif
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NetzAuto
import de.troetenrepublik.app.market.SecBericht
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// [0.79] Automatische Kanten je Firma fuer die Verflechtungen. Erst beim Aufklappen der Karte und beim Erweitern eines Knotens
// geladen, nie beim Oeffnen der Seite: SEC (Grosskunden aus dem vorhandenen Firmenbericht, Anlage 21, Form 4, Wettbewerber im
// Jahresbericht), Halter aus dem Dienst fuer Eigentuemer und Rechte, GLEIF (Mutter, Toechter), Wikidata (Mutter, Toechter,
// Beteiligungen, Board) und USAspending (Zuschuesse, Kredite; Anfrage und Leser in Behoerden). Ergebnisse anderer Bausteine
// (Firmenbericht, Konzernmutter, Struktur) werden wiederverwendet. Je Knoten 14 Tage auf dem Geraet; ist eine Quelle gescheitert,
// gilt der Stand nur einen Tag. Die SEC bekommt hoechstens zwei Abrufe je Sekunde.

@Serializable
private class NetzEintrag(val t: Long = 0L, val j: String = "")

@Serializable
private class NetzDatei(val werte: Map<String, NetzEintrag> = emptyMap())

private val netzJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val netzDateiSperre = Mutex()
private val netzKnotenSperren = ConcurrentHashMap<String, Mutex>()
private const val NETZ_DATEI = "netz_auto.json"
private const val NETZ_TAGE_MS = 14L * 86_400_000L
private const val NETZ_TAG_MS = 86_400_000L
private const val NETZ_MAX = 150
private const val NETZ_SEC_PAUSE_MS = 500L
private const val NETZ_DIREKTOREN = 5

private fun AppState.netzDatei(): SichereDatei = SichereDatei(File(store.ordner, NETZ_DATEI))

private fun AppState.netzLesen(): MutableMap<String, NetzEintrag> =
    runCatching { netzJson.decodeFromString(NetzDatei.serializer(), netzDatei().lesen().toString(Charsets.UTF_8)).werte.toMutableMap() }.getOrDefault(mutableMapOf())

private suspend fun AppState.netzSpeichern(k: String, e: NetzEintrag) = netzDateiSperre.withLock {
    val m = netzLesen()
    m[k] = e
    if (m.size > NETZ_MAX) m.entries.sortedBy { it.value.t }.take(m.size - NETZ_MAX).forEach { m.remove(it.key) }
    runCatching { netzDatei().schreiben(netzJson.encodeToString(NetzDatei.serializer(), NetzDatei(m)).toByteArray(Charsets.UTF_8)) }
}

// Gespeicherter Stand eines Knotens (ohne Abruf, ohne Sperre); null ohne Eintrag
suspend fun AppState.netzGespeichert(k: Netz.Knoten, graph: Netz.Graph): NetzAuto.Zusatz? = withContext(Dispatchers.IO) {
    netzLesen()[k.id]?.let { (NetzAuto.lesen(it.j, graph) ?: NetzAuto.LEER) + netzZulieferer(k, graph) }
}

// Zulieferer rueckwaerts und genannte Kunden aus der mitgelieferten Datei (Jahresberichte der SEC); ohne Abruf, nie im Zwischenspeicher
private fun netzZulieferer(k: Netz.Knoten, graph: Netz.Graph): NetzAuto.Zusatz {
    val d = NetzZulieferer.eingebaut ?: return NetzAuto.LEER
    val katalog = Symbols.alle().filter { it.type == AssetType.STOCK }.map { it.name to it.symbol }
    return runCatching { NetzAuto.zulieferer(k, d, katalog, graph) }.getOrDefault(NetzAuto.LEER)
}

// Ohne Netz: Grosskunden aus dem schon gespeicherten Firmenbericht (der Abschnitt Umsatzquellen laedt ihn). Damit erscheint die
// Karte bei Aktien mit Angaben zu Grosskunden, ohne dass die Seite etwas abruft.
suspend fun AppState.netzLeicht(k: Netz.Knoten, graph: Netz.Graph): NetzAuto.Zusatz = withContext(Dispatchers.IO) {
    val lokal = netzZulieferer(k, graph)
    val symbol = k.symbol ?: return@withContext lokal
    val e = runCatching { firmenberichte.gespeichert(symbol) }.getOrNull() ?: return@withContext lokal
    val b = e.bericht?.takeIf { e.status == SecBericht.Status.OK } ?: return@withContext lokal
    val ein = runCatching { firmenberichte.einreichung(symbol) }.getOrNull() ?: return@withContext lokal
    NetzAuto.kunden(k, b, graph, NetzAuto.einreichungsseite(ein.first, ein.second), LocalDate.now().toString()) + lokal
}

// Meldungen zu einem Knoten; frischer gespeicherter Stand zuerst, sonst Abruf (je Knoten gesperrt, andere Knoten warten nicht).
// Fehlschlag ohne gespeicherten Stand gibt leer zurueck und speichert nichts.
suspend fun AppState.netzAuto(k: Netz.Knoten, graph: Netz.Graph): NetzAuto.Zusatz = withContext(Dispatchers.IO) {
    netzAbgerufen(k, graph) + netzZulieferer(k, graph)
}

private suspend fun AppState.netzAbgerufen(k: Netz.Knoten, graph: Netz.Graph): NetzAuto.Zusatz = withContext(Dispatchers.IO) {
    val jetzt = System.currentTimeMillis()
    val alt = netzLesen()[k.id]
    if (alt != null && jetzt - alt.t < NETZ_TAGE_MS) return@withContext NetzAuto.lesen(alt.j, graph) ?: NetzAuto.LEER
    netzKnotenSperren.getOrPut(k.id) { Mutex() }.withLock {
        val erneut = netzLesen()[k.id]
        if (erneut != null && System.currentTimeMillis() - erneut.t < NETZ_TAGE_MS) return@withLock NetzAuto.lesen(erneut.j, graph) ?: NetzAuto.LEER
        val z = Zaehler()
        val neu = runCatching { netzAbrufen(k, graph, z) }.getOrNull()
        if (neu == null) return@withLock alt?.let { NetzAuto.lesen(it.j, graph) } ?: NetzAuto.LEER
        val ids = neu.kanten.flatMap { listOf(it.von, it.nach) }.toSet()
        val noetig = graph.knoten.values.filter { it.id in ids } + k
        // Eine gescheiterte Quelle gilt nur einen Tag als erledigt
        val t = if (z.fehler > 0) jetzt - NETZ_TAGE_MS + NETZ_TAG_MS else jetzt
        netzSpeichern(k.id, NetzEintrag(t, if (neu.leer) "" else NetzAuto.schreiben(neu, noetig)))
        neu
    }
}

private class Zaehler { var abrufe = 0; var fehler = 0; var letzterSec = 0L }

private suspend fun MarketApi.holen(z: Zaehler, url: String, kopf: Map<String, String>, sec: Boolean, max: Int = 8_000_000): String? {
    if (sec) {
        val warten = NETZ_SEC_PAUSE_MS - (System.currentTimeMillis() - z.letzterSec)
        if (warten > 0) delay(warten)
        z.letzterSec = System.currentTimeMillis()
    }
    z.abrufe++
    val t = runCatching { anfrage(url, kopf, max = max).takeIf { it.ok }?.text }.getOrNull()
    if (t == null) z.fehler++
    return t
}

private suspend fun AppState.netzAbrufen(k: Netz.Knoten, graph: Netz.Graph, z: Zaehler): NetzAuto.Zusatz? {
    val heute = LocalDate.now()
    val stand = heute.toString()
    val api = MarketApi()
    val kontakt = SecInsider.kennung(settingsFlow.value.secKontakt)
    val secKopf = kontakt?.let { mapOf("User-Agent" to it, "Accept" to "application/json") }
    val symbol = k.symbol
    var halter = NetzAuto.LEER
    var kunden = NetzAuto.LEER
    var gleif = NetzAuto.LEER
    var wikidata = NetzAuto.LEER
    var ex21 = NetzAuto.LEER
    var wettbewerb = NetzAuto.LEER
    var mandate = NetzAuto.LEER
    var staat = NetzAuto.LEER

    // Halter: derselbe Dienst wie "Eigentuemer und Rechte", kein eigener Abruf der Meldungen ueber 5 %
    if (symbol != null && k.typ == Netz.Typ.FIRMA) {
        val e = runCatching { eigentuemerStruktur(symbol, k.name) }.getOrNull()
        if (e != null) halter = NetzAuto.halter(k, e, graph, stand)
    }

    // SEC: Firmenbericht (vorhanden, Zwischenspeicher einer Woche), Anlage 21, Form 4, Wettbewerber
    if (secKopf != null && symbol != null && k.typ == Netz.Typ.FIRMA) {
        val agent = secKopf.getValue("User-Agent")
        val xmlKopf = mapOf("User-Agent" to agent, "Accept" to "application/xml, text/xml, */*")
        val bericht = firmenbericht(symbol, k.name)
        val ein = firmenberichte.einreichung(symbol)
        val cik = k.cik ?: ein?.first
        val acc = ein?.second
        val b = bericht.bericht
        if (ein != null && bericht.status == SecBericht.Status.OK && b != null) {
            kunden = NetzAuto.kunden(k, b, graph, NetzAuto.einreichungsseite(ein.first, ein.second), stand)
        }
        if (cik != null) {
            // Board-Mitglieder (Form 4, Rolle Director), hoechstens fuenf Meldende
            val f4 = api.holen(z, NetzAuto.form4Adresse(cik, heute.minusYears(1).toString(), stand), secKopf, true)
            if (f4 != null) {
                val liste = ArrayList<Triple<NetzAuto.Form4Treffer, NetzAuto.Direktor, String>>()
                for (t in NetzAuto.form4Treffer(f4, cik, NETZ_DIREKTOREN)) {
                    val url = NetzAuto.form4Dokument(cik, t)
                    val xml = api.holen(z, url, xmlKopf, true, 1_000_000) ?: continue
                    NetzAuto.direktor(xml)?.let { liste += Triple(t, it, url) }
                }
                mandate = NetzAuto.direktoren(k, liste, graph, stand)
            }
        }
        if (cik != null && acc != null) {
            // Jahresbericht: Anlage 21 (Tochterliste) und Abschnitt Competition
            val subs = api.holen(z, SecBericht.submissionsAdresse(cik), secKopf, true)
            if (subs != null) {
                val index = api.holen(z, SecBericht.verzeichnisAdresse(cik, acc), secKopf, true)
                val datei = index?.let { NetzAuto.exhibit21Datei(it) }
                if (datei != null) {
                    val adresse = SecBericht.archivAdresse(cik, acc, datei)
                    val html = api.holen(z, adresse, mapOf("User-Agent" to agent, "Accept" to "text/html, */*"), true, 3_000_000)
                    if (html != null) ex21 = NetzAuto.exhibit21(k, NetzAuto.exhibit21Namen(html), adresse, graph, stand)
                }
                val haupt = SecBericht.hauptdokument(subs, acc)
                if (haupt != null) {
                    val adresse = SecBericht.archivAdresse(cik, acc, haupt.datei)
                    delay(NETZ_SEC_PAUSE_MS)
                    z.abrufe++
                    val text = runCatching { api.strom(adresse, mapOf("User-Agent" to agent, "Accept" to "text/html, */*")) { SecBericht.lesenAbschnitt(it) } }.getOrNull()
                    if (text == null) z.fehler++
                    else {
                        val katalog = Symbols.alle().filter { it.type == AssetType.STOCK }.map { it.name to it.symbol }
                        val abschnitt = NetzAuto.wettbewerbAbschnitt(Rohstoffprofil.klartext(text))
                        wettbewerb = NetzAuto.wettbewerber(k, abschnitt, graph, katalog, adresse, stand)
                    }
                }
            }
        }
        // Zuschuesse und Kredite des Bundes (USAspending, ohne Schluessel): Anfrage und Leser wie die Regierungsauftraege
        if (cik != null) {
            val antworten = ArrayList<String>()
            for (codes in Behoerden.ZUSCHUSS_GRUPPEN) {
                z.abrufe++
                val body = Behoerden.zuschussListeKoerper(k.name, codes, "2015-10-01", stand)
                val t = runCatching { api.anfrage("${Behoerden.USA}/search/spending_by_award/", mapOf("Accept" to "application/json"), methode = "POST", json = body).takeIf { it.ok }?.text }.getOrNull()
                if (t == null) z.fehler++ else antworten += t
            }
            staat = NetzAuto.zuschuesse(k, antworten, graph, stand)
        }
    }

    // GLEIF: Mutter aus dem vorhandenen Baustein (Zwischenspeicher eines Monats), Toechter neu
    val isin = symbol?.let { isinFor(it) }.orEmpty()
    if (isin.length == 12 && k.typ == Netz.Typ.FIRMA) {
        val kopf = mapOf("Accept" to "application/vnd.api+json")
        val satz = api.holen(z, Gleif.leiSuche(isin), kopf, false)?.let { Gleif.satzLesen(it) }
        if (satz != null) {
            val mutter = konzernmutter(symbol!!)
            val kinder = api.holen(z, NetzAuto.gleifKinderAdresse(satz.lei), kopf, false)?.let { NetzAuto.gleifListe(it) }.orEmpty()
            gleif = NetzAuto.gleif(k, satz.lei, mutter, kinder, graph, stand)
        }
    }

    // Wikidata (CC0): Mutter, Toechter, Beteiligungen, Board; Eigentuemer liefert der Dienst fuer Eigentuemer und Rechte
    if (k.typ == Netz.Typ.FIRMA) {
        val kopfWd = mapOf("Accept" to "application/json")
        val kandidaten = k.wikidata?.let { listOf(it) }
            ?: api.holen(z, FirmaWikidata.suchAdresse(k.name, "en") { java.net.URLEncoder.encode(it, "UTF-8") }, kopfWd, false)?.let { FirmaWikidata.treffer(it).take(2) }.orEmpty()
        for (q in kandidaten) {
            val text = api.holen(z, NetzAuto.wikidataAdresse(q), kopfWd, false) ?: continue
            if (FirmaWikidata.ersteFirma(text, listOf(q)) == null) continue
            val angaben = NetzAuto.wikidataLesen(text, q) ?: continue
            val ids = angaben.ids.take(50)
            if (ids.isEmpty()) break
            val deutsch = de.troetenrepublik.app.i18n.I18n.locale.language == "de"
            val namen = api.holen(z, FirmaWikidata.eintraegeAdresse(ids, "labels", if (deutsch) "de|en" else "en"), kopfWd, false)
                ?.let { FirmaWikidata.beschriftungen(it, if (deutsch) "de" else "en") }.orEmpty()
            wikidata = NetzAuto.wikidata(k, q, angaben, namen, graph, stand)
            break
        }
    }

    // Dieselbe Tochter aus mehreren Quellen: GLEIF vor Anlage 21 vor Wikidata, je Name einmal
    val bekannt = HashSet<String>()
    fun ohneBekannte(zu: NetzAuto.Zusatz): NetzAuto.Zusatz {
        val ausNamen = zu.knoten.filter { Netz.nameVoll(it.name).let { n -> n.isNotEmpty() && n in bekannt } }.map { it.id }.toSet()
        val neu = NetzAuto.Zusatz(
            zu.knoten.filter { it.id !in ausNamen },
            zu.kanten.filter { e -> e.von !in ausNamen && e.nach !in ausNamen },
        )
        neu.knoten.forEach { n -> Netz.nameVoll(n.name).takeIf { it.isNotEmpty() }?.let { bekannt += it } }
        return neu
    }
    gleif.knoten.forEach { n -> Netz.nameVoll(n.name).takeIf { it.isNotEmpty() }?.let { bekannt += it } }
    val ex21Neu = ohneBekannte(ex21)
    val wikidataNeu = ohneBekannte(NetzAuto.ohneDoppelteMandate(k, mandate, wikidata, graph))
    val alle = halter + kunden + gleif + ex21Neu + wettbewerb + mandate + staat + wikidataNeu
    // Nichts erreicht und nur Fehler: kein gespeicherter Stand (der naechste Aufruf versucht es erneut)
    if (alle.leer && z.fehler > 0 && z.fehler >= z.abrufe) return null
    return alle
}
