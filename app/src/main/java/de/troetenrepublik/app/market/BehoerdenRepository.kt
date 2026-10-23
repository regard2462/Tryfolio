package de.troetenrepublik.app.market

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    data class Leerverkauf(val tage: List<Behoerden.LeerTag>, val bestand: List<Behoerden.LeerBestand>)

    suspend fun leerverkauf(symbol: String): Leerverkauf? = merken("finra|$symbol") {
        val h = heute()
        val kopf = mapOf("Accept" to "application/json")
        val tage = api.anfrage("${Behoerden.FINRA}/regShoDaily", kopf, json = Behoerden.leerVolumenKoerper(symbol, h.minusDays(45).toString(), h.toString()))
            .takeIf { it.ok }?.text?.let { Behoerden.leerVolumenLesen(it) }.orEmpty()
        val bestand = api.anfrage("${Behoerden.FINRA}/consolidatedShortInterest", kopf, json = Behoerden.leerBestandKoerper(symbol, h.minusMonths(7).toString(), h.toString()))
            .takeIf { it.ok }?.text?.let { Behoerden.leerBestandLesen(it) }.orEmpty()
        if (tage.isEmpty() && bestand.isEmpty()) null else Leerverkauf(tage, bestand)
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

    suspend fun texas(name: String): List<Behoerden.Entlassung>? = merken("tx|$name", 24 * 3_600_000L) {
        val url = Behoerden.texasAdresse(Behoerden.stamm(name)) ?: return@merken null
        api.anfrage(url).takeIf { it.ok }?.text?.let { Behoerden.texasLesen(it) }
    }

    suspend fun patente(name: String, schluessel: String): Behoerden.Patente? {
        if (schluessel.isBlank()) return null
        return merken("uspto|$name", 24 * 3_600_000L) {
            val h = heute()
            gezaehlt(Abrufkontingent.USPTO, Behoerden.patenteAdresse(name, h.minusYears(1).toString(), h.toString()), mapOf("X-API-KEY" to schluessel.trim()))
                ?.let { Behoerden.patenteLesen(it) }
        }
    }
}
