package de.troetenrepublik.app.engine

import kotlin.math.abs

// Kerzenmuster aus Tageskerzen (5.94), nach den ueblichen Lehrbuchregeln (Nison 1991). Ein Muster zaehlt nur nach
// einem passenden Vorlauf: Umkehrmuster nach oben erst nach fuenf Tagen Rueckgang, nach unten erst nach fuenf Tagen
// Anstieg. Kerzen ohne Eroeffnungskurs (manche Reihen haben nur Schlusskurse) bleiben aussen vor.
object Kerzenmuster {

    // richtung: 1 = steigende Kurse erwartet, -1 = fallende, 0 = Unentschlossenheit (Doji).
    data class Treffer(val index: Int, val muster: String, val richtung: Int)

    // Reihenfolge der Pruefung: Muster ueber drei Tage vor zwei Tagen vor einem Tag, damit ein Morgenstern nicht als
    // einzelner Hammer gemeldet wird.
    val MUSTER = listOf(
        "morgenstern", "abendstern", "soldaten", "kraehen",
        "verschlingen_auf", "verschlingen_ab", "durchstossen", "wolke",
        "hammer", "inv_hammer", "haengender", "sternschnuppe", "doji",
    )

    // Tage, ueber die der Vorlauf gemessen wird.
    private const val VORLAUF = 5

    // Wie viele Tage zurueck ein Muster in der Deutung noch genannt wird.
    const val FENSTER = 5

    // Je Tag das erkannte Muster oder null. o, h, l, c: Eroeffnung, Hoch, Tief, Schluss, aufsteigend und gleich lang.
    fun erkennen(o: List<Double>, h: List<Double>, l: List<Double>, c: List<Double>): List<Treffer?> {
        val n = c.size
        if (o.size != n || h.size != n || l.size != n) return List(n) { null }
        fun gueltig(i: Int) = o[i].isFinite() && o[i] > 0 && h[i] >= maxOf(o[i], c[i]) && l[i] <= minOf(o[i], c[i]) && h[i] > l[i]
        fun koerper(i: Int) = abs(c[i] - o[i])
        fun spanne(i: Int) = h[i] - l[i]
        fun oben(i: Int) = h[i] - maxOf(o[i], c[i])
        fun unten(i: Int) = minOf(o[i], c[i]) - l[i]
        fun steigt(i: Int) = c[i] > o[i]
        fun faellt(i: Int) = c[i] < o[i]
        // Mittlerer Koerper der zehn Tage davor: Massstab fuer lange und kleine Kerzen
        fun mittel(i: Int): Double? {
            val r = (maxOf(0, i - 10) until i).filter { gueltig(it) }
            return if (r.size < 5) null else r.sumOf { koerper(it) } / r.size
        }
        // Vorlauf bis zum Tag vor dem Muster (ab = erster Tag des Musters)
        fun abwaerts(ab: Int) = ab - 1 - VORLAUF >= 0 && c[ab - 1] < c[ab - 1 - VORLAUF]
        fun aufwaerts(ab: Int) = ab - 1 - VORLAUF >= 0 && c[ab - 1] > c[ab - 1 - VORLAUF]
        fun lang(i: Int, m: Double) = koerper(i) >= m
        fun klein(i: Int, m: Double) = koerper(i) <= m * 0.5

        return List(n) { i ->
            if (!gueltig(i)) return@List null
            val m = mittel(i) ?: return@List null
            val zwei = i >= 1 && gueltig(i - 1)
            val drei = zwei && i >= 2 && gueltig(i - 2)
            val gefunden = MUSTER.firstOrNull { k ->
                when (k) {
                    // Langer Abwaertstag, kleiner Koerper, langer Aufwaertstag bis ueber die Mitte des ersten
                    "morgenstern" -> drei && abwaerts(i - 2) && faellt(i - 2) && lang(i - 2, m) && klein(i - 1, m) &&
                        maxOf(o[i - 1], c[i - 1]) <= c[i - 2] && steigt(i) && c[i] > (o[i - 2] + c[i - 2]) / 2
                    "abendstern" -> drei && aufwaerts(i - 2) && steigt(i - 2) && lang(i - 2, m) && klein(i - 1, m) &&
                        minOf(o[i - 1], c[i - 1]) >= c[i - 2] && faellt(i) && c[i] < (o[i - 2] + c[i - 2]) / 2
                    // Drei steigende Tage, jeder eroeffnet im Koerper des Vortags und schliesst hoeher, kurze obere Schatten
                    "soldaten" -> drei && abwaerts(i - 2) && (i - 2..i).all { steigt(it) && oben(it) <= koerper(it) * 0.5 && koerper(it) >= m * 0.6 } &&
                        (i - 1..i).all { o[it] in o[it - 1]..c[it - 1] && c[it] > c[it - 1] }
                    "kraehen" -> drei && aufwaerts(i - 2) && (i - 2..i).all { faellt(it) && unten(it) <= koerper(it) * 0.5 && koerper(it) >= m * 0.6 } &&
                        (i - 1..i).all { o[it] in c[it - 1]..o[it - 1] && c[it] < c[it - 1] }
                    // Der Koerper umschliesst den gegenlaeufigen Koerper des Vortags
                    "verschlingen_auf" -> zwei && abwaerts(i - 1) && faellt(i - 1) && steigt(i) &&
                        o[i] <= c[i - 1] && c[i] >= o[i - 1] && koerper(i) > koerper(i - 1)
                    "verschlingen_ab" -> zwei && aufwaerts(i - 1) && steigt(i - 1) && faellt(i) &&
                        o[i] >= c[i - 1] && c[i] <= o[i - 1] && koerper(i) > koerper(i - 1)
                    // Eroeffnet unter dem Vortagestief und schliesst ueber der Mitte des langen Abwaertstags (Gegenstueck: dunkle Wolke)
                    "durchstossen" -> zwei && abwaerts(i - 1) && faellt(i - 1) && lang(i - 1, m) && steigt(i) &&
                        o[i] < l[i - 1] && c[i] > (o[i - 1] + c[i - 1]) / 2 && c[i] < o[i - 1]
                    "wolke" -> zwei && aufwaerts(i - 1) && steigt(i - 1) && lang(i - 1, m) && faellt(i) &&
                        o[i] > h[i - 1] && c[i] < (o[i - 1] + c[i - 1]) / 2 && c[i] > o[i - 1]
                    // Kleiner Koerper oben, unterer Schatten mindestens doppelt so lang, kaum oberer Schatten
                    "hammer", "haengender" -> koerper(i) > spanne(i) * 0.05 && koerper(i) <= spanne(i) / 3 &&
                        unten(i) >= 2 * koerper(i) && oben(i) <= spanne(i) * 0.1 && (if (k == "hammer") abwaerts(i) else aufwaerts(i))
                    // Kleiner Koerper unten, oberer Schatten mindestens doppelt so lang, kaum unterer Schatten
                    "inv_hammer", "sternschnuppe" -> koerper(i) > spanne(i) * 0.05 && koerper(i) <= spanne(i) / 3 &&
                        oben(i) >= 2 * koerper(i) && unten(i) <= spanne(i) * 0.1 && (if (k == "inv_hammer") abwaerts(i) else aufwaerts(i))
                    // Eroeffnung und Schluss fast gleich bei normaler Tagesspanne
                    "doji" -> koerper(i) <= spanne(i) * 0.05 && spanne(i) >= m
                    else -> false
                }
            } ?: return@List null
            val richtung = when (gefunden) {
                "morgenstern", "soldaten", "verschlingen_auf", "durchstossen", "hammer", "inv_hammer" -> 1
                "doji" -> 0
                else -> -1
            }
            Treffer(i, gefunden, richtung)
        }
    }

    // Signale je Tag fuer die Trefferbilanz; Doji ohne Richtung zaehlen nicht.
    fun signale(o: List<Double>, h: List<Double>, l: List<Double>, c: List<Double>): IntArray {
        val t = erkennen(o, h, l, c)
        return IntArray(c.size) { t[it]?.richtung ?: 0 }
    }

    // Deutung zum letzten Kurs: das juengste Muster der letzten FENSTER Handelstage, sonst "keins". wert: Handelstage
    // seit dem Muster, kreuzTag: sein Tag. null ohne Eroeffnungskurse in der juengsten Zeit.
    fun deutung(tage: List<Long>, o: List<Double>, h: List<Double>, l: List<Double>, c: List<Double>): Indikatoren.Deutung? {
        val n = c.size
        if (n < 30 || tage.size != n || o.size != n) return null
        if ((maxOf(0, n - 20) until n).count { o[it].isFinite() && o[it] > 0 } < 15) return null
        val t = erkennen(o, h, l, c)
        val j = (n - 1 downTo maxOf(0, n - FENSTER)).firstOrNull { t[it] != null }
            ?: return Indikatoren.Deutung("kerzen", 0, "keins", 0.0)
        val treffer = t[j]!!
        return Indikatoren.Deutung("kerzen", treffer.richtung, treffer.muster, (n - 1 - j).toDouble(), tage[j], treffer.richtung > 0)
    }
}
