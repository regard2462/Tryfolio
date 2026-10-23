package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// Abrufkontingente fuer Schnittstellen mit eigenem Schluessel. Gezaehlt wird in gleitenden Fenstern (24 Stunden,
// eine Minute, 31 Tage), damit kein Limit ueberschritten wird, egal wann der Anbieter seinen Tag oder Monat beginnt.
// Die Abrufe stehen in einer Datei, damit ein Neustart das Kontingent nicht zuruecksetzt.
class Abrufkontingent(private val datei: File?, private val uhr: () -> Long = System::currentTimeMillis) {

    // menge: Abrufe oder Zeichen.
    data class Grenze(val menge: Long, val fensterMs: Long)

    data class Regel(val grenzen: List<Grenze>, val abstandMs: Long = 0L)

    companion object {
        const val GUARDIAN = "guardian"
        const val FINNHUB = "finnhub"
        const val ALPHAVANTAGE = "alphavantage"
        const val DEEPL = "deepl"
        const val REDDIT = "reddit"
        const val FEC = "fec"
        const val FEC_DEMO = "fec-demo"
        const val USPTO = "uspto"
        private const val TAG = 86_400_000L

        // Vorgaben der Anbieter und was die App davon nutzt: Guardian 500 am Tag und 1 je Sekunde (App 450).
        // Finnhub 60 je Minute (App 50, hoechstens 2.000 am Tag). Alpha Vantage 25 am Tag und 5 je Minute (App 22 und 4).
        // DeepL Free 500.000 Zeichen im Monat (App 450.000 in 31 Tagen). Reddit 100 je Minute im Mittel ueber 10 Minuten
        // (App 30 je Minute, 200 in 10 Minuten, 2.000 am Tag).
        val REGELN: Map<String, Regel> = mapOf(
            GUARDIAN to Regel(listOf(Grenze(450, TAG)), abstandMs = 1_100L),
            FINNHUB to Regel(listOf(Grenze(50, 60_000L), Grenze(2_000, TAG)), abstandMs = 250L),
            ALPHAVANTAGE to Regel(listOf(Grenze(22, TAG), Grenze(4, 60_000L)), abstandMs = 15_000L),
            DEEPL to Regel(listOf(Grenze(450_000, 31 * TAG))),
            REDDIT to Regel(listOf(Grenze(30, 60_000L), Grenze(200, 600_000L), Grenze(2_000, TAG)), abstandMs = 700L),
            // FEC ueber api.data.gov: eigener Schluessel 1.000 je Stunde (App 900), gemeinsamer DEMO_KEY 30 je Stunde
            // und 50 am Tag (App 25 und 45). USPTO: ein Abruf zur Zeit, die App hoechstens 500 am Tag.
            FEC to Regel(listOf(Grenze(900, 3_600_000L), Grenze(20_000, TAG))),
            FEC_DEMO to Regel(listOf(Grenze(25, 3_600_000L), Grenze(45, TAG))),
            USPTO to Regel(listOf(Grenze(500, TAG)), abstandMs = 1_100L),
        )
    }

    @Serializable
    private data class Eintrag(val t: Long, val n: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val buchungen = HashMap<String, MutableList<Eintrag>>()
    private val sperre = Any()

    init {
        runCatching {
            if (datei != null && datei.exists()) {
                val m = json.decodeFromString<Map<String, List<Eintrag>>>(datei.readText())
                m.forEach { (g, l) -> buchungen[g] = l.toMutableList() }
            }
        }
    }

    // Prueft alle Grenzen und den Mindestabstand.
    fun erlaubt(gruppe: String, menge: Long = 1): Boolean = synchronized(sperre) {
        val regel = REGELN[gruppe] ?: return true
        val jetzt = uhr()
        val l = buchungen[gruppe].orEmpty()
        if (regel.abstandMs > 0 && l.isNotEmpty() && jetzt - l.last().t < regel.abstandMs) return false
        regel.grenzen.all { g -> l.filter { jetzt - it.t < g.fensterMs }.sumOf { it.n } + menge <= g.menge }
    }

    // Wartezeit wegen des Mindestabstands in Millisekunden.
    fun warten(gruppe: String): Long = synchronized(sperre) {
        val regel = REGELN[gruppe] ?: return 0L
        val letzte = buchungen[gruppe]?.lastOrNull()?.t ?: return 0L
        (regel.abstandMs - (uhr() - letzte)).coerceAtLeast(0L)
    }

    // menge: Abrufe oder Zeichen.
    fun buchen(gruppe: String, menge: Long = 1) {
        synchronized(sperre) {
            val jetzt = uhr()
            val l = buchungen.getOrPut(gruppe) { mutableListOf() }
            l += Eintrag(jetzt, menge)
            // Nur aufheben, was noch in einem Fenster zaehlt.
            val laengstes = REGELN[gruppe]?.grenzen?.maxOfOrNull { it.fensterMs } ?: TAG
            l.removeAll { jetzt - it.t >= laengstes }
        }
        speichern()
    }

    // Verbraucht und erlaubt im laengsten Fenster der Regel, fuer die Anzeige.
    fun stand(gruppe: String): Pair<Long, Long> = synchronized(sperre) {
        val regel = REGELN[gruppe] ?: return 0L to 0L
        val g = regel.grenzen.maxBy { it.fensterMs }
        val jetzt = uhr()
        buchungen[gruppe].orEmpty().filter { jetzt - it.t < g.fensterMs }.sumOf { it.n } to g.menge
    }

    // Der Anbieter meldet sein Limit: fuellt das laengste Fenster auf.
    fun erschoepft(gruppe: String) {
        val (verbraucht, erlaubt) = stand(gruppe)
        if (erlaubt > verbraucht) buchen(gruppe, erlaubt - verbraucht)
    }

    private fun speichern() {
        val d = datei ?: return
        val kopie = synchronized(sperre) { buchungen.mapValues { it.value.toList() } }
        runCatching {
            d.parentFile?.mkdirs()
            val tmp = File(d.parentFile, d.name + ".tmp")
            tmp.writeText(json.encodeToString(kopie))
            if (!tmp.renameTo(d)) { d.delete(); tmp.renameTo(d) }
        }
    }
}
