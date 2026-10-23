package de.troetenrepublik.app.market

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.TreeMap
import kotlin.math.sqrt

// Auswertung der Geschaefte einer Person. Nur Aktien und Fonds mit Kuerzel und Kursen, Optionen, Anleihen und
// Tausch zaehlen nicht. Kurse sind Tagesschlusskurse je Tag wie in Portfolioverlauf.tag.
object Auswertung {

    // Wie sich gekaufte (oder verkaufte) Werte nach tage Kalendertagen entwickelt haben.
    data class Horizont(
        val tage: Int,
        val anzahl: Int,
        // Kaeufe: Kurs danach hoeher. Verkaeufe: Kurs danach niedriger, der Verkauf war also gut getimt.
        val treffer: Int,
        // Kaeufe: besser als der Markt. Verkaeufe: schlechter als der Markt.
        val gegenMarkt: Int,
        val median: Double,
        val medianMarkt: Double?,
    )

    data class Haeufigkeit(
        val geschaefte: Int,
        val monate: Int,
        val proMonat: Double,
        val staerksterMonat: YearMonth?,
        val staerksterAnzahl: Int,
        val medianBetrag: Double,
        val ueberMillion: Int,
        val kaufAnteil: Double,
    )

    data class Monat(val monat: YearMonth, val anzahl: Int, val schnitt: Double)

    // Anteile der Kaeufe nach Branche und Betrag, frueher und zuletzt. abgedeckt ist der Anteil mit bekannter Branche.
    data class Branchen(val frueher: List<Pair<String, Double>>, val zuletzt: List<Pair<String, Double>>, val grenze: YearMonth, val abgedeckt: Double)

    // Kaeufe, nach denen der Kurs binnen HANDELSTAGE Handelstagen um mindestens SCHWELLE stieg, und Verkaeufe vor einem
    // ebenso grossen Rueckgang. basisKauf und basisVerkauf sind die Anteile an allen Handelstagen derselben Werte im
    // selben Zeitraum, also was zufaellig zu erwarten waere.
    data class VorBewegung(val kaeufe: Int, val vorAnstieg: Int, val basisKauf: Double, val verkaeufe: Int, val vorRueckgang: Int, val basisVerkauf: Double)

    data class Ergebnis(
        val kaeufe: List<Horizont>,
        val verkaeufe: List<Horizont>,
        val haeufigkeit: Haeufigkeit,
        val branchen: Branchen?,
        val auffaellig: List<Monat>,
        val vorBewegung: VorBewegung?,
        // Geschaefte in Aktien und Fonds mit Kursen, die in die Kursauswertung eingingen, und in wie vielen Werten.
        val bewertet: Int,
        val werte: Int,
    )

    val HORIZONTE = listOf(30, 90, 365)
    const val HANDELSTAGE = 10
    const val SCHWELLE = 0.10

    private fun monat(ms: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC))

    private fun relevant(g: Kongress.Geschaeft) = g.kuerzel != null && g.typ in setOf("ST", "EF") && (g.kauf || g.verkauf) && g.datum > 0

    // Werte fuer die Kursauswertung: die nach Betrag meistgehandelten Aktien und Fonds.
    fun werte(geschaefte: List<Kongress.Geschaeft>, anzahl: Int): List<String> =
        geschaefte.filter { relevant(it) }.groupBy { it.kuerzel!! }.map { (k, l) -> k to l.sumOf { it.mitte } }
            .sortedByDescending { it.second }.take(anzahl).map { it.first }

    private fun kurs(m: TreeMap<Long, Double>, tag: Long): Double? =
        m.floorEntry(tag)?.value ?: m.ceilingEntry(tag)?.takeIf { it.key - tag <= 5 }?.value

    private fun median(l: List<Double>): Double {
        if (l.isEmpty()) return 0.0
        val s = l.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    fun rechnen(
        geschaefte: List<Kongress.Geschaeft>,
        kurse: Map<String, TreeMap<Long, Double>>,
        markt: TreeMap<Long, Double>?,
        branche: (String) -> String?,
        heute: Long,
    ): Ergebnis {
        val heuteTag = Portfolioverlauf.tag(heute)
        val mitKurs = geschaefte.filter { relevant(it) && kurse[it.kuerzel!!]?.isNotEmpty() == true }

        fun horizonte(kauf: Boolean): List<Horizont> = HORIZONTE.mapNotNull { h ->
            val r = ArrayList<Double>()
            val rm = ArrayList<Double>()
            var treffer = 0
            var gegen = 0
            for (g in mitKurs.filter { if (kauf) it.kauf else it.verkauf }) {
                val m = kurse.getValue(g.kuerzel!!)
                val t0 = Portfolioverlauf.tag(g.datum)
                val t1 = t0 + h
                if (t1 > heuteTag || t1 > m.lastKey()) continue
                val p0 = kurs(m, t0) ?: continue
                val p1 = m.floorEntry(t1)?.value ?: continue
                if (p0 <= 0) continue
                val ret = p1 / p0 - 1
                r += ret
                if (if (kauf) ret > 0 else ret < 0) treffer++
                val mr = markt?.let { mk -> val a = kurs(mk, t0); val b = mk.floorEntry(t1)?.value; if (a != null && b != null && a > 0) b / a - 1 else null }
                if (mr != null) {
                    rm += mr
                    if (if (kauf) ret > mr else ret < mr) gegen++
                }
            }
            if (r.isEmpty()) null else Horizont(h, r.size, treffer, gegen, median(r), if (rm.isEmpty()) null else median(rm))
        }

        // Haeufigkeit ueber alle gemeldeten Kaeufe und Verkaeufe (auch Anleihen und Optionen)
        val alle = geschaefte.filter { it.datum > 0 && (it.kauf || it.verkauf) }
        val proMonat = alle.groupBy { monat(it.datum) }.mapValues { it.value.size }
        val erster = proMonat.keys.minOrNull()
        val letzter = proMonat.keys.maxOrNull()
        val monate = if (erster == null || letzter == null) 0 else ((letzter.year - erster.year) * 12 + letzter.monthValue - erster.monthValue + 1)
        val staerkster = proMonat.maxByOrNull { it.value }
        val haeufigkeit = Haeufigkeit(
            geschaefte = alle.size,
            monate = monate,
            proMonat = if (monate > 0) alle.size.toDouble() / monate else 0.0,
            staerksterMonat = staerkster?.key,
            staerksterAnzahl = staerkster?.value ?: 0,
            medianBetrag = median(alle.map { it.mitte }),
            ueberMillion = alle.count { it.von > 1_000_000 },
            kaufAnteil = if (alle.isEmpty()) 0.0 else alle.count { it.kauf }.toDouble() / alle.size,
        )

        // Auffaellige Monate: mehr Geschaefte als Mittel plus zwei Standardabweichungen, mindestens doppelt so viele
        val auffaellig = if (erster == null || monate < 6) emptyList() else {
            val reihe = (0 until monate).map { i -> erster.plusMonths(i.toLong()).let { m -> m to (proMonat[m] ?: 0) } }
            val mittel = reihe.sumOf { it.second }.toDouble() / reihe.size
            val sd = sqrt(reihe.sumOf { (it.second - mittel) * (it.second - mittel) } / reihe.size)
            reihe.filter { it.second >= mittel + 2 * sd && it.second >= 2 * mittel && it.second >= 5 }
                .sortedByDescending { it.second }.take(3).map { Monat(it.first, it.second, mittel) }
        }

        // Branchen der Kaeufe: die letzten zwoelf Monate gegen die Zeit davor, bei kuerzerer Zeit die beiden Haelften
        val kaeufe = geschaefte.filter { relevant(it) && it.kauf }
        val branchen = run {
            if (kaeufe.isEmpty()) return@run null
            val mitBranche = kaeufe.mapNotNull { g -> branche(g.kuerzel!!)?.let { g to it } }
            val abgedeckt = mitBranche.sumOf { it.first.mitte } / kaeufe.sumOf { it.mitte }
            if (mitBranche.size < 4) return@run null
            val ende = mitBranche.maxOf { monat(it.first.datum) }
            val anfang = mitBranche.minOf { monat(it.first.datum) }
            var grenze = ende.minusMonths(11)
            if (!grenze.isAfter(anfang)) grenze = anfang.plusMonths(((ende.year - anfang.year) * 12L + ende.monthValue - anfang.monthValue + 1) / 2)
            fun anteile(l: List<Pair<Kongress.Geschaeft, String>>): List<Pair<String, Double>> {
                val summe = l.sumOf { it.first.mitte }
                if (summe <= 0) return emptyList()
                return l.groupBy { it.second }.map { (k, v) -> k to v.sumOf { it.first.mitte } / summe }.sortedByDescending { it.second }
            }
            val frueher = anteile(mitBranche.filter { monat(it.first.datum) < grenze })
            val zuletzt = anteile(mitBranche.filter { monat(it.first.datum) >= grenze })
            if (zuletzt.isEmpty()) null else Branchen(frueher, zuletzt, grenze, abgedeckt)
        }

        val vorBewegung = run {
            var k = 0; var kTreffer = 0; var v = 0; var vTreffer = 0
            var basisK = 0.0; var basisV = 0.0
            for ((wert, l) in mitKurs.groupBy { it.kuerzel!! }) {
                val m = kurse.getValue(wert)
                val tage = m.keys.toList()
                val preise = m.values.toList()
                fun aufwaerts(i: Int): Boolean = (i + 1..minOf(i + HANDELSTAGE, preise.size - 1)).any { preise[it] / preise[i] - 1 >= SCHWELLE }
                fun abwaerts(i: Int): Boolean = (i + 1..minOf(i + HANDELSTAGE, preise.size - 1)).any { preise[it] / preise[i] - 1 <= -SCHWELLE }
                val ersterTag = Portfolioverlauf.tag(l.minOf { it.datum })
                val letzterTag = Portfolioverlauf.tag(l.maxOf { it.datum })
                val fenster = tage.indices.filter { tage[it] in ersterTag..letzterTag && it + HANDELSTAGE < preise.size }
                if (fenster.isEmpty()) continue
                val rateAuf = fenster.count { aufwaerts(it) }.toDouble() / fenster.size
                val rateAb = fenster.count { abwaerts(it) }.toDouble() / fenster.size
                for (g in l) {
                    val i = tage.binarySearch(Portfolioverlauf.tag(g.datum)).let { if (it >= 0) it else -it - 2 }
                    if (i < 0 || i + HANDELSTAGE >= preise.size) continue
                    if (g.kauf) { k++; if (aufwaerts(i)) kTreffer++; basisK += rateAuf }
                    else { v++; if (abwaerts(i)) vTreffer++; basisV += rateAb }
                }
            }
            if (k + v < 5) null else VorBewegung(k, kTreffer, if (k > 0) basisK / k else 0.0, v, vTreffer, if (v > 0) basisV / v else 0.0)
        }

        return Ergebnis(horizonte(true), horizonte(false), haeufigkeit, branchen, auffaellig, vorBewegung, mitKurs.size, mitKurs.map { it.kuerzel }.distinct().size)
    }
}
