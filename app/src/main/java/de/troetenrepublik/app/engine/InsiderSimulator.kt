package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.SecInsider
import java.time.LocalDate

// Insider-Simulator (0.77): die Rueckschau der Cluster-Kaeufe aus „Wirkt das?“ mit verstellbaren Regeln, gerechnet aus
// den Kaeufen der Form-4-Meldungen (SecInsider, mit Person, Rolle, Betrag und Meldetag) und den Tagesschlusskursen. Die
// Standardregel ist die der mitgelieferten Rechnung (tools/sec/cluster_rueckschau.py): mindestens drei Insider in zehn
// Tagen, ab der Meldung, Einstieg zum Schlusskurs des ersten Handelstags danach, 60 Handelstage, je Aktie hoechstens
// ein Cluster in 90 Tagen, gegen den S&P 500. Reine Rechnung ohne Netz.
object InsiderSimulator {

    // Mindestbetrag: je Kauf (jeder Kauf zaehlt erst ab dem Betrag) oder je Cluster (Summe der Kaeufe im Fenster).
    enum class Je { KAUF, CLUSTER }

    data class Betrag(val je: Je, val dollar: Double) {
        val keiner: Boolean get() = dollar <= 0
    }

    data class Regeln(
        val mindest: Int = 3,
        val fenster: Int = 10,
        val betrag: Betrag = Betrag(Je.KAUF, 0.0),
        // Nur Vorstand und andere Fuehrungskraefte (Rollen S und F)
        val nurFuehrung: Boolean = false,
        val halten: Int = 60,
        // Kosten je Kauf und je Verkauf in Prozent
        val kosten: Double = 0.0,
    ) {
        val standard: Boolean get() = this == STANDARD
    }

    val STANDARD = Regeln()

    // Auswahl der Stellschrauben
    val MINDEST = listOf(1, 2, 3, 4, 5)
    val FENSTER = listOf(5, 10, 20, 30)
    val BETRAEGE = listOf(
        Betrag(Je.KAUF, 0.0), Betrag(Je.KAUF, 25_000.0), Betrag(Je.KAUF, 100_000.0),
        Betrag(Je.CLUSTER, 250_000.0), Betrag(Je.CLUSTER, 1_000_000.0),
    )
    val HALTEN = listOf(20, 60, 120, 250)
    val KOSTEN = listOf(0.0, 0.1, 0.25, 0.5, 1.0)

    // Je Aktie hoechstens ein Cluster in so vielen Tagen
    private const val SPERRE = 90

    // Ein Cluster: Aktie, Meldetag des Kaeufers, der die Regel erfuellt (JJJJ-MM-TT), Zahl der Insider, Summe der Kaeufe
    class Cl(val symbol: String, val tag: String, val insider: Int, val betrag: Double)

    private fun nr(tag: String): Long = LocalDate.parse(tag).toEpochDay()

    // Cluster nach den Regeln, aelteste zuerst. Zaehlt die Meldung (Meldetag), nicht den Tag des Geschaefts.
    fun cluster(liste: List<SecInsider.Geschaeft>, r: Regeln): List<Cl> {
        val kaeufe = liste.filter { g ->
            g.kauf && g.wert > 0 && g.f.isNotBlank() &&
                (!r.nurFuehrung || g.r == "S" || g.r == "F") &&
                (r.betrag.je != Je.KAUF || g.wert >= r.betrag.dollar)
        }
        val aus = ArrayList<Cl>()
        for ((k, l0) in kaeufe.groupBy { it.k }) {
            val l = l0.sortedBy { it.f }
            val tage = LongArray(l.size) { nr(l[it].f) }
            var letzter: Long? = null
            for (i in l.indices) {
                val t = tage[i]
                // Kaeufe der Meldetage t - fenster bis t (die Liste ist nach Meldetag sortiert)
                val im = l.indices.filter { tage[it] in (t - r.fenster)..t }
                val personen = im.map { l[it].p.trim().lowercase() }.toSet()
                if (personen.size < r.mindest) continue
                if (letzter != null && t - letzter < SPERRE) continue
                val summe = im.sumOf { l[it].wert }
                if (r.betrag.je == Je.CLUSTER && summe < r.betrag.dollar) continue
                aus += Cl(k, l[i].f, personen.size, summe)
                letzter = t
            }
        }
        return aus.sortedWith(compareBy<Cl> { it.tag }.thenBy { it.symbol })
    }

    // Erster Index mit Tag > tag (Tagesnummer); anzahl, wenn es keinen gibt.
    private fun nachTag(k: KongressAuswertung.Kurse, tag: Long): Int {
        var lo = 0
        var hi = k.anzahl
        while (lo < hi) { val m = (lo + hi) ushr 1; if (k.tagBei(m) > tag) hi = m else lo = m + 1 }
        return lo
    }

    // Erster Index mit Tag >= tag (Tagesnummer); anzahl, wenn es keinen gibt.
    private fun abTag(k: KongressAuswertung.Kurse, tag: Int): Int {
        var lo = 0
        var hi = k.anzahl
        while (lo < hi) { val m = (lo + hi) ushr 1; if (k.tagBei(m) >= tag) hi = m else lo = m + 1 }
        return lo
    }

    // Rendite nach Kosten je Kauf und Verkauf (k als Anteil): Einstieg kostet k, Ausstieg kostet k vom Erloes.
    internal fun nachKosten(r: Double, k: Double): Double = (1 + r) * (1 - k) / (1 + k) - 1

    private class Ergebnis(val c: Cl, val r: Double, val i: Double, val pfad: DoubleArray, val ip: DoubleArray)

    // Ergebnis in der Form der mitgelieferten Rueckschau, damit „Wirkt das?“ beides gleich zeigt. null ohne ausgewertete
    // Cluster. kurse liefert die Schlusskurse einer Aktie (null oder leer: ohne Kurse), index die des S&P 500. stand und
    // beginn: letzter und erster Tag der Meldungen (JJJJ-MM-TT). Kosten gelten nur fuer die Aktien, nicht fuer den Index.
    fun rechnen(
        cluster: List<Cl>, kurse: (String) -> KongressAuswertung.Kurse?, index: KongressAuswertung.Kurse?, r: Regeln, stand: String, beginn: String,
    ): WirktDas.Cluster? {
        index ?: return null
        if (index.leer) return null
        val k = r.kosten / 100.0
        val h = r.halten
        val erg = ArrayList<Ergebnis>()
        var ohne = 0
        var offen = 0
        for (c in cluster) {
            val kk = kurse(c.symbol)
            if (kk == null || kk.leer) { ohne++; continue }
            val i = nachTag(kk, nr(c.tag))
            if (i + h >= kk.anzahl) { offen++; continue }
            val e = kk.wertBei(i).toDouble()
            val j = abTag(index, kk.tagBei(i))
            if (j + h >= index.anzahl) { offen++; continue }
            val ie = index.wertBei(j).toDouble()
            erg += Ergebnis(
                c, nachKosten(kk.wertBei(i + h) / e - 1, k), index.wertBei(j + h) / ie - 1,
                DoubleArray(h + 1) { s -> nachKosten(kk.wertBei(i + s) / e - 1, k) }, DoubleArray(h + 1) { s -> index.wertBei(j + s) / ie - 1 },
            )
        }
        if (erg.isEmpty()) return null
        val n = erg.size
        fun r4(x: Double) = Math.round(x * 10_000.0) / 10_000.0
        val jahre = erg.groupBy { it.c.tag.take(4) }.entries.sortedBy { it.key }.map { (j, l) ->
            WirktDas.ClusterJahr(
                j.toInt(), l.size, r4(l.sumOf { it.r } / l.size), r4(l.sumOf { it.i } / l.size), r4(l.count { it.r > it.i }.toDouble() / l.size),
            )
        }
        val schritt = maxOf(1, h / 20)
        val stufen = (0..h step schritt).toMutableList().also { if (it.last() != h) it += h }
        val kurve = stufen.map { s -> listOf(s.toDouble(), r4(erg.sumOf { it.pfad[s] } / n), r4(erg.sumOf { it.ip[s] } / n)) }
        val schlecht = erg.minBy { it.r }
        val gut = erg.maxBy { it.r }
        return WirktDas.Cluster(
            stand = stand, ab = beginn, halten = h, fenster = r.fenster, mindest = r.mindest,
            cluster = cluster.size, ausgewertet = n, ohneKurse = ohne, offen = offen,
            rendite = r4(erg.sumOf { it.r } / n), index = r4(erg.sumOf { it.i } / n),
            median = r4(erg.map { it.r }.sorted()[n / 2]), indexMedian = r4(erg.map { it.i }.sorted()[n / 2]),
            treffer = r4(erg.count { it.r > it.i }.toDouble() / n),
            schlechtester = WirktDas.Extrem(schlecht.c.symbol, schlecht.c.tag, r4(schlecht.r)),
            bester = WirktDas.Extrem(gut.c.symbol, gut.c.tag, r4(gut.r)),
            jahre = jahre, kurve = kurve,
        )
    }
}
