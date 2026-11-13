package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Faktorzerlegung
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

// Faktorrenditen fuer die Faktorzerlegung des Depots (0.78), zur Laufzeit von den Herausgebern geladen und nur im
// Speicher gehalten; nichts davon liegt der App bei.
// - Kenneth R. French Data Library (Fama/French fuenf Faktoren und Momentum, USA, monatlich): Dateien mit dem Vermerk
//   „Copyright Eugene F. Fama and Kenneth R. French“, ohne weitere Bedingungen auf der Seite.
// - Global Factor Data (Jensen, Kelly, Pedersen): Markt und Themen je Region, monatlich, CC BY-NC 4.0.
object FaktorAbruf {

    const val FRENCH_SEITE = "https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/data_library.html"
    private const val FRENCH = "https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp/"
    private const val FRENCH_FUENF = FRENCH + "F-F_Research_Data_5_Factors_2x3_CSV.zip"
    private const val FRENCH_MOMENTUM = FRENCH + "F-F_Momentum_Factor_CSV.zip"
    private const val FRENCH_SECHS = FRENCH + "6_Portfolios_2x3_CSV.zip"

    const val JKP_SEITE = "https://jkpfactors.com/"
    const val JKP_LIZENZ = "https://creativecommons.org/licenses/by-nc/4.0/"
    private const val JKP = "https://jkpfactors-data.s3.amazonaws.com/public/"

    // Regionen der weltweiten Faktoren
    const val WELT = "world"
    const val WELT_OHNE_USA = "world_ex_us"

    private const val HALTBAR = 24 * 3_600_000L
    private const val MAX_ENTPACKT = 16_000_000

    private val api by lazy { MarketApi() }
    private val kopf = mapOf("User-Agent" to MarketApi.BROWSER_UA)
    private val speicher = ConcurrentHashMap<String, Pair<Long, Faktorzerlegung.Faktordaten>>()
    private val sperre = Mutex()

    private fun frisch(k: String): Faktorzerlegung.Faktordaten? = speicher[k]?.takeIf { System.currentTimeMillis() - it.first < HALTBAR }?.second

    // Datei eines Themas je Region: [region]_[thema]_[monthly]_[vw_cap].zip
    fun jkpAdresse(region: String, thema: String): String = "$JKP%5B$region%5D_%5B$thema%5D_%5Bmonthly%5D_%5Bvw_cap%5D.zip"

    // Erste CSV-Datei eines ZIP-Archivs als Text; null, wenn sie fehlt oder groesser als die Grenze ist.
    fun csvAusZip(zip: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.name.endsWith(".csv", true)) continue
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(65_536)
                while (true) {
                    val n = z.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_ENTPACKT) return@use null
                }
                return@use out.toByteArray().toString(Charsets.UTF_8)
            }
            null
        }
    }.getOrNull()

    private suspend fun csv(url: String): String? = api.datei(url, 4_000_000, kopf)?.let { csvAusZip(it) }

    // Fuenf Faktoren und Momentum fuer die USA mit dem Monatszins; null ohne Antwort.
    suspend fun french(): Faktorzerlegung.Faktordaten? {
        frisch("french")?.let { return it }
        return sperre.withLock {
            frisch("french")?.let { return@withLock it }
            val fuenf = csv(FRENCH_FUENF) ?: return@withLock null
            val mom = csv(FRENCH_MOMENTUM) ?: return@withLock null
            Faktorzerlegung.ausFrench(fuenf, mom)?.also { speicher["french"] = System.currentTimeMillis() to it }
        }
    }

    private var kleinWert: Pair<Long, Map<Int, Double>>? = null

    // Jahresrenditen der Small-Cap-Value-Aktien der USA (Portfolio SMALL HiBM aus sechs Portfolios nach Groesse und
    // Buchwert/Kurs, wertgewichtet, nominal, in Anteilen) fuer die Boersengeschichte; null ohne Antwort.
    suspend fun kleinWertJahre(): Map<Int, Double>? {
        kleinWert?.takeIf { System.currentTimeMillis() - it.first < HALTBAR }?.let { return it.second }
        return sperre.withLock {
            kleinWert?.takeIf { System.currentTimeMillis() - it.first < HALTBAR }?.let { return@withLock it.second }
            val text = csv(FRENCH_SECHS) ?: return@withLock null
            de.troetenrepublik.app.engine.LangeReihen.kleinWertLesen(text).takeIf { it.size >= 30 }
                ?.also { kleinWert = System.currentTimeMillis() to it }
        }
    }

    // Markt und Themen einer Region; der Zins (US-Schatzwechsel, die Renditen sind Ueberrenditen in Dollar) kommt aus
    // den Fama/French-Dateien. null ohne Antwort.
    suspend fun jkp(region: String): Faktorzerlegung.Faktordaten? {
        frisch("jkp|$region")?.let { return it }
        val zins = french()?.zins ?: return null
        return sperre.withLock {
            frisch("jkp|$region")?.let { return@withLock it }
            val markt = csv(jkpAdresse(region, "mkt")) ?: return@withLock null
            val themen = csv(jkpAdresse(region, "all_themes")) ?: return@withLock null
            Faktorzerlegung.ausJkp(markt, themen, zins)?.also { speicher["jkp|$region"] = System.currentTimeMillis() to it }
        }
    }
}
