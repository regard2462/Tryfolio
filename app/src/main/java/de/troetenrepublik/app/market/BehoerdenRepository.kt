package de.troetenrepublik.app.market

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

// Laedt die Behoerdendaten einer Firma und merkt sie sich: Live-Abrufe sechs Stunden im Speicher, mitgelieferte
// Dateien einmal je Sitzung. Jeder Abruf liefert null ohne Antwort; die Seite laesst die Zeile dann weg.
class BehoerdenRepository(private val api: MarketApi, private val kontingent: Abrufkontingent) {

    private val gemerkt = ConcurrentHashMap<String, Pair<Long, Any?>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> merken(schluessel: String, ms: Long = 6 * 3_600_000L, laden: suspend () -> T?): T? {
        gemerkt[schluessel]?.takeIf { System.currentTimeMillis() - it.first < ms }?.let { return it.second as T? }
        val v = runCatching { laden() }.getOrNull()
        if (v != null) gemerkt[schluessel] = System.currentTimeMillis() to v
        return v
    }

    val lobbying: Behoerden.LobbyingDatei? by lazy {
        runCatching { Behoerden.lobbyingLesen(de.troetenrepublik.app.plattform.Plattform.asset("behoerden/lobbying.json").decodeToString()) }.getOrNull()
    }

    val lobbyWelt: LobbyWelt.Datei? by lazy {
        runCatching { LobbyWelt.lesen(de.troetenrepublik.app.plattform.Plattform.asset("behoerden/lobby_welt.json").decodeToString()) }.getOrNull()
    }

    val entlassungen: Behoerden.EntlassungDatei? by lazy {
        runCatching { Behoerden.entlassungenLesen(de.troetenrepublik.app.plattform.Plattform.asset("behoerden/entlassungen.json").decodeToString()) }.getOrNull()
    }

    private fun heute() = LocalDate.now(ZoneOffset.UTC)

    // Verpflichtungen je Fiskaljahr (1. Oktober bis 30. September) und die groessten Auftraege der letzten zwoelf
    // Monate, nur von Empfaengern, deren Name zur Firma passt.
    data class Auftraege(val jahre: List<Pair<Int, Double>>, val groesste: List<Behoerden.Auftrag>)

    suspend fun auftraege(name: String): Auftraege? = merken("usa|$name") {
        val h = heute()
        val stamm = Behoerden.stamm(name)
        val laufend = if (h.monthValue >= 10) h.year + 1 else h.year
        val jahre = kotlinx.coroutines.coroutineScope {
            (laufend - 3..laufend).map { fj ->
                async {
                    val a = api.anfrage("${Behoerden.USA}/search/spending_by_category/recipient/", json = Behoerden.auftraegeEmpfaengerKoerper(name, "${fj - 1}-10-01", "$fj-09-30"))
                    if (!a.ok) null else fj to Behoerden.auftraegeEmpfaengerLesen(a.text!!).filter { Behoerden.passtEmpfaenger(it.first, stamm) }.sumOf { it.second }
                }
            }.awaitAll()
        }
        if (jahre.any { it == null }) return@merken null
        val liste = api.anfrage("${Behoerden.USA}/search/spending_by_award/", json = Behoerden.auftraegeListeKoerper(name, h.minusYears(1).toString(), h.toString()))
            .takeIf { it.ok }?.text?.let { Behoerden.auftraegeListeLesen(it) }.orEmpty().filter { Behoerden.passtEmpfaenger(it.empfaenger, stamm) }
        val j = jahre.filterNotNull()
        if (j.all { it.second == 0.0 } && liste.isEmpty()) null else Auftraege(j, liste)
    }

    // Staatsgeld (Block „Unternehmen“): Auftraege, Zuschuesse und Kredite aus USAspending, TED und Contracts Finder samt
    // EU-Haushalt, sechs Stunden gemerkt; null ohne Antwort der Online-Quellen.
    suspend fun staatsgeld(symbol: String, namen: List<String>): StaatsgeldAbruf.Ergebnis? {
        val r = merken("sg|$symbol") { StaatsgeldAbruf.laden(api, symbol, namen, heute()) }
        // Ein Ergebnis, dem eine Quelle fehlte, bleibt nicht gemerkt
        if (r != null && r.unvollstaendig) gemerkt.remove("sg|$symbol")
        return r
    }

    data class Leerverkauf(val tage: List<Behoerden.LeerTag>, val bestand: List<Behoerden.LeerBestand>)

    // FINRA nur mit eigenem Zugang (5.94): Token je Client-ID, hoechstens 30 Minuten gemerkt, wie FINRA es empfiehlt.
    private var finraToken: Triple<String, String, Long>? = null
    private val finraSperre = kotlinx.coroutines.sync.Mutex()

    // Letzte Meldung zum FINRA-Zugang fuer die Seite „Zugänge“ (abgelehnt oder Limit), sonst null.
    @Volatile var finraMeldung: String? = null
        private set

    private suspend fun finraKopf(id: String, secret: String): Map<String, String>? {
        if (id.isBlank() || secret.isBlank()) return null
        val jetzt = System.currentTimeMillis()
        val t = finraSperre.withLock {
            finraToken?.takeIf { it.first == id && jetzt < it.third }?.second ?: run {
                if (!kontingent.erlaubt(Abrufkontingent.FINRA)) { finraMeldung = NewsRepository.LIMIT; return@withLock null }
                kontingent.buchen(Abrufkontingent.FINRA)
                val a = api.anfrage(Finra.TOKEN, Finra.tokenKopf(id, secret), methode = "POST")
                if (a.code == 400 || a.code == 401 || a.code == 403) { finraMeldung = NewsRepository.SCHLUESSEL; return@withLock null }
                val neu = a.takeIf { it.ok }?.text?.let { Finra.tokenLesen(it) } ?: return@withLock null
                finraMeldung = null
                finraToken = Triple(id, neu.first, jetzt + minOf(neu.second * 1000 - 60_000L, 30 * 60_000L).coerceAtLeast(60_000L))
                neu.first
            }
        } ?: return null
        return mapOf("Accept" to "application/json", "Authorization" to "Bearer $t")
    }

    private suspend fun finraAbruf(datensatz: String, koerper: String, kopf: Map<String, String>): String? {
        if (!kontingent.erlaubt(Abrufkontingent.FINRA)) { finraMeldung = NewsRepository.LIMIT; return null }
        kontingent.buchen(Abrufkontingent.FINRA)
        val a = api.anfrage("${Behoerden.FINRA}/$datensatz", kopf, json = koerper)
        if (a.code == 401) finraToken = null
        if (a.code == 429) { kontingent.erschoepft(Abrufkontingent.FINRA); finraMeldung = NewsRepository.LIMIT }
        return a.takeIf { it.ok }?.text
    }

    suspend fun leerverkauf(symbol: String, id: String, secret: String): Leerverkauf? = merken("finra|$symbol") {
        val kopf = finraKopf(id, secret) ?: return@merken null
        val h = heute()
        val tage = finraAbruf("regShoDaily", Behoerden.leerVolumenKoerper(Finra.kuerzel(symbol), h.minusDays(45).toString(), h.toString()), kopf)
            ?.let { Behoerden.leerVolumenLesen(it) }.orEmpty()
        val bestand = finraAbruf("consolidatedShortInterest", Behoerden.leerBestandKoerper(Finra.kuerzel(symbol), h.minusYears(3).toString(), h.toString()), kopf)
            ?.let { Behoerden.leerBestandLesen(it) }.orEmpty()
        if (tage.isEmpty() && bestand.isEmpty()) null else Leerverkauf(tage, bestand)
    }

    // Handel ueber alternative Handelssysteme (ATS, „Dark Pools“) je Woche, etwa ein halbes Jahr, dazu die
    // Plattformen der juengsten Woche. FINRA veroeffentlicht mit zwei (Tier 1) bzw. vier Wochen (Tier 2) Verzug.
    data class DarkPool(val wochen: List<Finra.AtsWoche>, val plaetze: List<Finra.AtsPlatz>, val stufe: String?)

    suspend fun darkPool(symbol: String, id: String, secret: String): DarkPool? = merken("ats|$symbol", 12 * 3_600_000L) {
        val kopf = finraKopf(id, secret) ?: return@merken null
        val h = heute()
        val text = finraAbruf("weeklySummary", Finra.atsKoerper(symbol, Finra.ATS_WERT, h.minusWeeks(30).toString(), h.toString()), kopf) ?: return@merken null
        val wochen = Finra.atsWochenLesen(text)
        val letzte = wochen.lastOrNull()?.woche ?: return@merken null
        val stufe = Regex("\"tierIdentifier\"\\s*:\\s*\"(T1|T2)\"", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.uppercase()
        val plaetze = finraAbruf("weeklySummary", Finra.atsKoerper(symbol, Finra.ATS_WERT_PLATZ, letzte, letzte), kopf)
            ?.let { Finra.atsPlaetzeLesen(it, letzte) }.orEmpty()
        DarkPool(wochen, plaetze, stufe)
    }

    data class Spenden(val pac: Behoerden.Pac, val summen: Behoerden.PacSummen?, val empfaenger: List<Pair<String, Double>>, val zyklus: Int)

    // FEC-Zyklus: zwei Jahre bis zum geraden Wahljahr.
    private fun zyklus(): Int = heute().year.let { if (it % 2 == 0) it else it + 1 }

    // Ohne Ergebnis auch beim DEMO_KEY ein Ergebnis merken (null wird nicht gemerkt), damit die Grenze von 30
    // Abfragen je Stunde nicht an einer Firma ohne PAC haengt.
    private val ohnePac = ConcurrentHashMap<String, Long>()

    // Ein Abruf im Rahmen des Kontingents; null, wenn es erschoepft ist.
    private suspend fun gezaehlt(gruppe: String, url: String, kopf: Map<String, String> = emptyMap()): String? {
        if (!kontingent.erlaubt(gruppe)) return null
        kontingent.buchen(gruppe)
        val a = api.anfrage(url, kopf)
        if (a.code == 429) {
            kontingent.erschoepft(gruppe)
            if (gruppe == Abrufkontingent.FEC_DEMO || gruppe == Abrufkontingent.FEC) fecGesperrt = true
        } else if (a.ok && (gruppe == Abrufkontingent.FEC_DEMO || gruppe == Abrufkontingent.FEC)) fecGesperrt = false
        return a.takeIf { it.ok }?.text
    }

    // Die FEC hat zuletzt mit „zu viele Abrufe“ geantwortet (beim gemeinsamen Testschluessel haeufig).
    @Volatile var fecGesperrt: Boolean = false
        private set

    suspend fun spenden(name: String, schluessel: String): Spenden? {
        val k = schluessel.trim().ifBlank { Behoerden.DEMO }
        val gruppe = if (schluessel.isBlank()) Abrufkontingent.FEC_DEMO else Abrufkontingent.FEC
        val stamm = Behoerden.stamm(name)
        ohnePac[stamm]?.takeIf { System.currentTimeMillis() - it < 24 * 3_600_000L }?.let { return null }
        if (!kontingent.erlaubt(gruppe)) { fecGesperrt = true; return null }
        return merken("fec|$stamm", 24 * 3_600_000L) {
            val pac = gezaehlt(gruppe, Behoerden.pacSuche(stamm, k))
                ?.let { t -> Behoerden.pacLesen(t) { Behoerden.stamm(it).startsWith(stamm) } }
            if (pac == null) { if (!fecGesperrt) ohnePac[stamm] = System.currentTimeMillis(); return@merken null }
            var z = zyklus()
            var summen = gezaehlt(gruppe, Behoerden.pacSummen(pac.id, z, k))?.let { Behoerden.pacSummenLesen(it) }
            // Am Anfang eines Zyklus ist der vorige aussagekraeftiger
            if (summen == null || summen.einnahmen <= 0) {
                z -= 2
                summen = gezaehlt(gruppe, Behoerden.pacSummen(pac.id, z, k))?.let { Behoerden.pacSummenLesen(it) }
            }
            val empfaenger = gezaehlt(gruppe, Behoerden.pacEmpfaenger(pac.id, z, k))?.let { Behoerden.pacEmpfaengerLesen(it) }.orEmpty()
            Spenden(pac, summen, empfaenger, z)
        }
    }

    // Abruf beim USPTO im Rahmen des Kontingents (ein Abruf zur Zeit); null, wenn es erschoepft ist
    private suspend fun uspto(url: String, schluessel: String): MarketApi.Antwort? {
        if (!kontingent.erlaubt(Abrufkontingent.USPTO)) return null
        kontingent.buchen(Abrufkontingent.USPTO)
        val a = api.anfrage(url, mapOf("X-API-KEY" to schluessel.trim()))
        if (a.code == 429) kontingent.erschoepft(Abrufkontingent.USPTO)
        return a
    }

    // Patente, Stufe 1: Anmeldernamen der Firma (nur eindeutige), sieben Tage gemerkt. Leer ohne Treffer, null ohne Antwort.
    suspend fun patentNamen(firma: List<String>, schluessel: String): List<String>? {
        if (schluessel.isBlank() || firma.isEmpty()) return null
        val w = FirmaPatente.suchwoerter(firma.first())
        val h = heute()
        val url = FirmaPatente.namenAdresse(w, h.year - FirmaPatente.JAHRE, h.year) ?: return null
        return merken("pn|${w.joinToString(" ")}", 7 * 24 * 3_600_000L) {
            val a = uspto(url, schluessel) ?: return@merken null
            if (a.code == 404) return@merken emptyList<String>()
            if (!a.ok) return@merken null
            FirmaPatente.eigeneNamen(FirmaPatente.facettenLesen(a.text!!), FirmaName.schluessel(firma))
        }
    }

    // Patente, Stufe 2: erteilte Patente der Namen in einem Kalenderjahr; 404 heisst keine Treffer. Vergangene Jahre ein Tag
    // gemerkt, das Vorjahr sechs Stunden.
    suspend fun patentJahr(namen: List<String>, jahr: Int, schluessel: String): Int? {
        if (schluessel.isBlank() || namen.isEmpty()) return null
        return merken("pj|${namen.joinToString("|")}|$jahr", if (jahr < heute().year - 1) 24 * 3_600_000L else 6 * 3_600_000L) {
            val a = uspto(FirmaPatente.jahrAdresse(namen, jahr), schluessel) ?: return@merken null
            if (a.code == 404) 0 else if (a.ok) FirmaPatente.zahlLesen(a.text!!) else null
        }
    }

    // Patente, Stufe 3: CPC-Klassen der letzten Patente (Stichprobe), sieben Tage gemerkt
    suspend fun patentFelder(namen: List<String>, schluessel: String): FirmaPatente.Felder? {
        if (schluessel.isBlank() || namen.isEmpty()) return null
        val h = heute()
        return merken("pf|${namen.joinToString("|")}", 7 * 24 * 3_600_000L) {
            val a = uspto(FirmaPatente.felderAdresse(namen, h.year - 3, h.year), schluessel) ?: return@merken null
            if (!a.ok) null else FirmaPatente.felderLesen(a.text!!)
        }
    }
}
