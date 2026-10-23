package de.troetenrepublik.app.market

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

// Dieselbe Aktie an verschiedenen Boersenplaetzen, damit alle Notierungen das Logo der Firma zeigen.
// Notierungen gelten als dieselbe Aktie, wenn ihre Namen ohne Rechtsform, Gattung und Satzzeichen gleich sind
// (Abkuerzungen wie „Res.“ zaehlen als gleich, zusaetzliche Woerter nicht) und ihre Euro-Kurse hoechstens
// ABWEICHUNG auseinanderliegen. Der Kurs trennt gleichnamige Firmen wie Castellum Inc. und Castellum AB.
// Die deutschen Zweitnotizen prueft MarketRepository selbst am Kurs.
object Gleichwerte {

    // Hoechste Abweichung der Euro-Kurse zweier Notierungen derselben Aktie.
    const val ABWEICHUNG = 0.15

    // Bei Pennystocks reicht eine kleine absolute Abweichung (Kursschritte an kleinen Plaetzen).
    private const val CENT = 0.005

    // Mehr Notierungen merkt die App je Firma nicht.
    private const val HOECHSTENS = 16

    private val ZUSATZ = setOf(
        "inc", "incorporated", "corp", "corporation", "co", "company", "ltd", "limited", "plc", "ag", "se", "sa", "nv",
        "ab", "publ", "asa", "oyj", "as", "spa", "kgaa", "gmbh", "group", "holding", "holdings", "class", "cl", "the",
        "adr", "ads", "reg", "registered", "shares", "share", "ordinary", "common", "stock", "sponsored", "bearer",
        "a", "b", "c", "o", "n", "on", "eo", "dl", "sf", "dk", "nk", "ls", "nam", "inh", "vz", "akt", "aktie", "aktien",
        "rc", "new", "sp", "spons", "unsp", "unsponsored", "rep", "repr",
    )

    // Woerter des Namens ohne Rechtsform, Gattung, Nennwert, Satzzeichen und Gross- und Kleinschreibung.
    fun stamm(name: String): List<String> =
        name.lowercase().replace("&", " and ").replace(Regex("\\(.*?\\)"), " ").map { if (it.istWortzeichen()) it else ' ' }.joinToString("").trim()
            .split(' ').filter { it.length >= 2 && it !in ZUSATZ && it.any { c -> c.isLetter() } }

    // Gleiche Firma nach dem Namen: gleich viele Woerter, das erste gleich, die uebrigen gleich oder abgekuerzt.
    fun namenGleich(a: String, b: String): Boolean {
        val x = stamm(a)
        val y = stamm(b)
        if (x.isEmpty() || x.size != y.size || x.sumOf { it.length } < 4) return false
        if (x[0] != y[0]) return false
        return x.indices.all { i ->
            val p = x[i]
            val q = y[i]
            p == q || (minOf(p.length, q.length) >= 3 && (p.startsWith(q) || q.startsWith(p)))
        }
    }

    fun kursGleich(a: Double, b: Double): Boolean =
        a > 0 && b > 0 && (abs(a / b - 1) <= ABWEICHUNG || abs(a - b) <= CENT)

    // Gruppen gleicher Aktien unter treffer (Kuerzel und Name). euro: Kurs eines Kuerzels in Euro, null wenn
    // unbekannt; Notierungen ohne Kurs kommen in keine Gruppe. Je Gruppe mindestens zwei Kuerzel.
    fun gruppen(treffer: List<Pair<String, String>>, euro: (String) -> Double?): List<Set<String>> {
        val l = treffer.distinctBy { it.first }
            .mapNotNull { (k, n) -> euro(k)?.takeIf { it.isFinite() && it > 0 }?.let { Triple(k, n, it) } }
        val wurzel = IntArray(l.size) { it }
        fun finde(i: Int): Int { var r = i; while (wurzel[r] != r) r = wurzel[r]; return r }
        for (i in l.indices) for (j in i + 1 until l.size) {
            if (namenGleich(l[i].second, l[j].second) && kursGleich(l[i].third, l[j].third)) wurzel[finde(j)] = finde(i)
        }
        return l.indices.groupBy { finde(it) }.values.filter { it.size >= 2 }.map { g -> g.map { l[it].first }.toSet() }
    }

    private val stand = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    // Kuerzel -> alle bekannten Notierungen derselben Aktie (mit sich selbst).
    val familien: StateFlow<Map<String, Set<String>>> = stand.asStateFlow()

    // Ablage fuer neu gefundene Gruppen (die Oberflaeche speichert sie auf dem Geraet).
    @Volatile var ablage: ((Set<String>) -> Unit)? = null

    // Notierungen gruppe als dieselbe Aktie merken; merken = auf dem Geraet ablegen.
    fun verbinden(gruppe: Set<String>, merken: Boolean = true) {
        if (gruppe.size < 2) return
        var neu: Set<String>? = null
        stand.update { m ->
            val alle = gruppe.flatMapTo(LinkedHashSet()) { m[it] ?: setOf(it) }
            if (alle.size > HOECHSTENS || gruppe.all { m[it] == alle }) return@update m
            neu = alle
            m + alle.associateWith { alle }
        }
        if (merken) neu?.let { n -> ablage?.invoke(n) }
    }

    // Fuer Tests.
    internal fun leeren() { stand.value = emptyMap() }
}
