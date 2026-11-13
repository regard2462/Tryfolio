package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Investoren
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

// Aktiensplits fuer Meldungen, die Stueckzahl und Preis in der Einheit ihres Tages nennen (13F, Form 4). Yahoo liefert
// Kurse rueckwirkend auf die heutige Stueckelung umgerechnet; Preis und Stueckzahl einer Meldung muessen dazu passen.
// Die Splitlisten holt die App je Kuerzel (siehe AppState.splitsHolen) und legt sie hier ab.
object SplitFaktoren {
    private const val TAG = 86_400_000L
    private val liste = ConcurrentHashMap<String, List<Splits.Split>>()

    // Zaehlt hoch, sobald neue Splitlisten da sind (Anzeigen rechnen dann neu).
    val stand = MutableStateFlow(0)

    // Aktienklassen wie BRK.B der Meldungen heissen bei Yahoo BRK-B.
    private val KLASSE = Regex("^([A-Z0-9]+)[./]([A-KM-Z])$")
    private fun schluessel(symbol: String) = symbol.trim().replace(KLASSE, "$1-$2")

    fun setzen(symbol: String, l: List<Splits.Split>) {
        val k = schluessel(symbol)
        if (liste[k] == l) return
        liste[k] = l
        stand.value = stand.value + 1
    }

    fun loeschen() { liste.clear(); stand.value = stand.value + 1 }

    fun bekannt(symbol: String) = liste.containsKey(schluessel(symbol))

    // Verhaeltnis aller Splits, die nach dem Ende des Tages nach und bis zum Ende des Tages bis wirksam wurden
    // (Splittag t zaehlt als Beginn der neuen Stueckelung: Bestand und Geschaefte dieses Tages sind schon neu).
    // Aus 1 Stueck damals werden so viele heute. Ohne bekannte Splits 1.
    fun zwischen(symbol: String, nach: Long, bis: Long = Long.MAX_VALUE): Double {
        val l = liste[schluessel(symbol)] ?: return 1.0
        var f = 1.0
        for (s in l) if (s.t > nach / TAG * TAG && s.t <= bis / TAG * TAG && s.verhaeltnis.isFinite() && s.verhaeltnis > 0) f *= s.verhaeltnis
        return f
    }

    // Gaengige Splitverhaeltnisse (mehr Aktien je Stueck); Reverse-Splits sind die Kehrwerte.
    private val SCHONE = doubleArrayOf(1.25, 4.0 / 3, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 100.0)

    private fun naheSplit(r: Double, toleranz: Double): Boolean {
        if (!r.isFinite() || r <= 0) return false
        return SCHONE.any { abs(r / it - 1) <= toleranz || abs(r * it - 1) <= toleranz }
    }

    // Kuerzel, bei denen die zwei gespeicherten Quartale einen Split nahelegen: die Stueckzahl springt auf ein
    // gaengiges Splitverhaeltnis, oder der Preis (Wert durch Stueck) faellt um ein solches bei zugleich anderer Stueckzahl.
    fun kandidaten(liste: List<Investoren.Investor>, symbol: (String) -> String = { it }): Set<String> {
        val aus = HashSet<String>()
        for (inv in liste) {
            val q = inv.aktuell ?: continue
            val v = inv.vorher ?: continue
            fun karte(qu: Investoren.Quartal) = qu.positionen.filter { it.art == "aktie" && !it.kuerzel.isNullOrBlank() && it.stueck > 0 && it.wert > 0 }
                .groupBy { symbol(it.kuerzel!!) }.mapValues { (_, l) -> l.sumOf { it.stueck } to l.sumOf { it.wert } }
            val jetzt = karte(q)
            val vorher = karte(v)
            for ((s, n) in jetzt) {
                val a = vorher[s] ?: continue
                val st = n.first / a.first
                val kurs = (a.second / a.first) / (n.second / n.first)
                if (naheSplit(st, 0.02) && abs(st - 1) > 0.2) aus += s
                else if (abs(st - 1) > 0.12 && naheSplit(kurs, 0.3) && abs(kurs - 1) > 0.3) aus += s
            }
        }
        return aus
    }
}
