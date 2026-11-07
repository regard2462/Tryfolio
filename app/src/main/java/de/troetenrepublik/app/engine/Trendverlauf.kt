package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable

// Verlauf der Reddit-Erwaehnungen. ApeWisdom nennt nur die letzten 24 Stunden und die 24 Stunden davor,
// deshalb schreibt die App sie je Tag mit. Der Verlauf beginnt mit dem ersten Abruf.
object Trendverlauf {

    @Serializable
    data class Archiv(
        // Je Quelle (ApeWisdom-Filter) und Tag (Tage seit 1970) die Erwaehnungen je Kuerzel in 24 Stunden.
        val tage: Map<String, Map<Long, Map<String, Int>>> = emptyMap(),
        // Zuletzt gesehene Namen der Kuerzel.
        val namen: Map<String, String> = emptyMap(),
        // Je Quelle der letzte Abruf in Millisekunden.
        val abgerufen: Map<String, Long> = emptyMap(),
    )

    // Erwaehnungen der letzten 24 Stunden und, falls bekannt, der 24 Stunden davor.
    data class Messung(val kuerzel: String, val name: String, val heute: Int, val gestern: Int?)

    // Gut ein Jahr.
    const val AUFBEWAHREN_TAGE = 400

    private const val TAG = 86_400_000L

    // Waehrungen hinter dem Bindestrich von Kryptowerten wie BTC-EUR.
    private val WAEHRUNGEN = setOf("EUR", "USD", "USDT", "USDC", "GBP", "CHF")

    fun tag(ms: Long): Long = ms / TAG

    // ApeWisdom kennzeichnet Kryptowerte mit .X, etwa BTC.X.
    fun normal(kuerzel: String): String = kuerzel.trim().uppercase().removeSuffix(".X")

    // Heute zaehlt die groesste Zahl des Tages je Kuerzel, der Vortag wird nur ergaenzt, wo er fehlt.
    // Tage aelter als AUFBEWAHREN_TAGE fallen weg, ebenso Namen ohne Eintrag.
    fun eintragen(a: Archiv, quelle: String, heute: Long, messungen: List<Messung>, jetzt: Long = heute * TAG): Archiv {
        if (messungen.isEmpty()) return a
        val alt = a.tage[quelle].orEmpty()
        val h = HashMap(alt[heute].orEmpty())
        val g = HashMap(alt[heute - 1].orEmpty())
        for (m in messungen) {
            val k = normal(m.kuerzel)
            if (k.isEmpty()) continue
            if (m.heute > (h[k] ?: -1)) h[k] = m.heute
            val v = m.gestern
            if (v != null && v > 0 && k !in g) g[k] = v
        }
        val neu = HashMap(alt)
        neu[heute] = h
        if (g.isNotEmpty()) neu[heute - 1] = g
        val grenze = heute - AUFBEWAHREN_TAGE
        val quellen = a.tage + (quelle to neu.filterKeys { it > grenze })
        val bekannt = quellen.values.flatMapTo(HashSet()) { t -> t.values.flatMap { it.keys } }
        val namen = (a.namen + messungen.filter { it.name.isNotBlank() }.associate { normal(it.kuerzel) to it.name }).filterKeys { it in bekannt }
        return Archiv(quellen, namen, a.abgerufen + (quelle to jetzt))
    }

    // Summe je Kuerzel ueber tage Tage bis heute einschliesslich.
    fun summen(a: Archiv, quelle: String, tage: Int, heute: Long): Map<String, Int> {
        val aus = HashMap<String, Int>()
        val von = heute - tage + 1
        a.tage[quelle].orEmpty().forEach { (t, werte) -> if (t in von..heute) werte.forEach { (k, n) -> aus[k] = (aus[k] ?: 0) + n } }
        return aus
    }

    // Die meisten Erwaehnungen zuerst.
    fun rangliste(a: Archiv, quelle: String, tage: Int, heute: Long): List<Pair<String, Int>> =
        summen(a, quelle, tage, heute).entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value }

    // Aeltester Tag zuerst, 0 wo nichts bekannt ist.
    fun reihe(a: Archiv, quelle: String, kuerzel: String, tage: Int, heute: Long): List<Int> {
        val t = a.tage[quelle].orEmpty()
        return (heute - tage + 1..heute).map { t[it]?.get(kuerzel) ?: 0 }
    }

    // Je groesse Tage zusammengezaehlt, der juengste Block steht rechts.
    fun buendeln(reihe: List<Int>, groesse: Int): List<Int> {
        if (groesse <= 1) return reihe
        val rest = reihe.size % groesse
        return (if (rest > 0) listOf(reihe.take(rest).sum()) else emptyList()) + reihe.drop(rest).chunked(groesse).map { it.sum() }
    }

    // Tag mit den meisten Erwaehnungen, null ohne Erwaehnung.
    fun spitze(a: Archiv, quelle: String, kuerzel: String, tage: Int, heute: Long): Pair<Long, Int>? {
        val von = heute - tage + 1
        return a.tage[quelle].orEmpty().entries.filter { it.key in von..heute }
            .mapNotNull { (t, w) -> w[kuerzel]?.takeIf { it > 0 }?.let { t to it } }
            .maxWithOrNull(compareBy<Pair<Long, Int>> { it.second }.thenBy { it.first })
    }

    // null, solange nichts mitgeschrieben ist.
    fun seit(a: Archiv, quelle: String): Long? = a.tage[quelle]?.keys?.minOrNull()

    // Kuerzel bei ApeWisdom: US-Werte unter ihrem Kuerzel, Kryptowerte ohne Waehrung. Ausserhalb der USA (SAP.DE)
    // nur, wenn der Name zu namen passt, dort stehen gleiche Kuerzel oft fuer andere Firmen. Aktiengattungen wie
    // BRK-B fuehrt ApeWisdom anders, sie bleiben aussen vor.
    fun kuerzelFuer(symbol: String, name: String, namen: Map<String, String>): String? {
        val s = symbol.trim().uppercase()
        if (s.isEmpty() || s.startsWith("^") || s.contains("=")) return null
        if (s.contains('-')) return s.substringBefore('-').takeIf { it.isNotEmpty() && s.substringAfter('-') in WAEHRUNGEN }
        if (!s.contains('.')) return s
        val basis = s.substringBefore('.')
        val ihrName = namen[basis] ?: return null
        val wort = { t: String -> t.lowercase().split(' ', ',', '.', '-').firstOrNull { it.length >= 3 }.orEmpty() }
        return basis.takeIf { wort(ihrName).isNotEmpty() && wort(ihrName) == wort(name) }
    }
}
