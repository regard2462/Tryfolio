package de.troetenrepublik.app.engine

import kotlin.math.abs

// Chartformationen aus den Hoch- und Tiefpunkten der Markttechnik (0.78): Doppeltop und Doppelboden,
// Schulter-Kopf-Schulter (auch umgekehrt) und Dreiecke. Eine Formation zaehlt erst, wenn ein Schlusskurs die Nackenlinie
// bzw. die Dreieckslinie durchbricht. Toleranzen:
// - Doppeltop/Doppelboden: die beiden Hochs (Tiefs) liegen hoechstens TOL_DOPPEL auseinander, davor lief der Kurs von
//   unterhalb (oberhalb) der Nackenlinie heran. Nackenlinie: das Tief (Hoch) dazwischen.
// - Schulter-Kopf-Schulter: der Kopf ueberragt beide Schultern um mindestens KOPF_MIN, die Schultern liegen hoechstens
//   TOL_SCHULTER auseinander. Nackenlinie: die Gerade durch die beiden Tiefs (Hochs) neben dem Kopf.
// - Dreieck: zwei Hochs und zwei Tiefs in Folge. Eine Linie gilt als waagerecht, wenn ihre Punkte hoechstens TOL_FLACH
//   auseinanderliegen. Symmetrisch: Hochs fallen, Tiefs steigen. Aufsteigend: Hochs waagerecht, Tiefs steigen.
//   Absteigend: Tiefs waagerecht, Hochs fallen. Der Ausbruch muss vor DREIECK_ANTEIL des Wegs bis zur Spitze kommen.
// - Der Durchbruch muss spaetestens so viele Kerzen nach dem letzten Punkt kommen, wie die Formation lang ist. Steigt
//   der Kurs vorher ueber das hoehere Hoch (die rechte Schulter) bzw. faellt er darunter, verfaellt sie.
object Chartformationen {

    const val TOL_DOPPEL = 0.03
    const val TOL_SCHULTER = 0.05
    const val KOPF_MIN = 0.02
    const val TOL_FLACH = 0.015
    const val DREIECK_ANTEIL = 0.8

    // Wie viele Handelstage zurueck eine bestaetigte Formation in der Deutung noch genannt wird.
    const val FENSTER = 10

    val ARTEN = listOf("sks", "sks_umgekehrt", "doppeltop", "doppelboden", "dreieck_sym", "dreieck_auf", "dreieck_ab")

    // richtung: 1 = Durchbruch nach oben, -1 = nach unten. von: Kerze des ersten Punkts, bis: Kerze des Durchbruchs.
    // linie: Wert der durchbrochenen Linie an dieser Kerze.
    data class Treffer(val art: String, val richtung: Int, val von: Int, val bis: Int, val linie: Double)

    // Erste Kerze nach ab, deren Kurs unter der Linie schliesst; null, wenn er vorher ueber grenze steigt oder bis
    // zur Kerze ende nichts geschieht.
    private inline fun bruch(ab: Int, ende: Int, grenze: Double, kurs: (Int) -> Double, linie: (Int) -> Double): Int? {
        for (j in ab + 1..ende) {
            if (kurs(j) > grenze) return null
            if (kurs(j) < linie(j)) return j
        }
        return null
    }

    // Umkehrformationen an einem Hoch. Mit s = -1 und gespiegelten Punkten findet dieselbe Regel die Gegenstuecke am Tief.
    private fun umkehr(p: List<Markttechnik.Punkt>, c: List<Double>, s: Int): List<Treffer> {
        val aus = ArrayList<Treffer>()
        val n = c.size
        val kurs = { j: Int -> s * c[j] }
        for (i in 1 until p.size) {
            if (!p[i].hoch) continue
            val davor = p[i - 1]
            if (i + 2 < p.size) {
                val h1 = p[i]; val t = p[i + 1]; val h2 = p[i + 2]
                if (davor.wert < t.wert && abs(h2.wert - h1.wert) <= TOL_DOPPEL * abs(h1.wert)) {
                    val ende = minOf(n - 1, h2.index + (h2.index - h1.index))
                    bruch(h2.index, ende, maxOf(h1.wert, h2.wert), kurs) { t.wert }?.let { j ->
                        aus += Treffer(if (s > 0) "doppeltop" else "doppelboden", -s, h1.index, j, s * t.wert)
                    }
                }
            }
            if (i + 4 < p.size) {
                val h1 = p[i]; val t1 = p[i + 1]; val k = p[i + 2]; val t2 = p[i + 3]; val h2 = p[i + 4]
                val schultern = maxOf(h1.wert, h2.wert)
                val mass = abs(k.wert)
                if (davor.wert < minOf(t1.wert, t2.wert) && k.wert - schultern >= KOPF_MIN * mass && abs(h1.wert - h2.wert) <= TOL_SCHULTER * mass) {
                    val steigung = (t2.wert - t1.wert) / (t2.index - t1.index)
                    val nacken = { j: Int -> t1.wert + steigung * (j - t1.index) }
                    val ende = minOf(n - 1, h2.index + (h2.index - h1.index))
                    bruch(h2.index, ende, h2.wert, kurs, nacken)?.let { j ->
                        aus += Treffer(if (s > 0) "sks" else "sks_umgekehrt", -s, h1.index, j, s * nacken(j))
                    }
                }
            }
        }
        return aus
    }

    private fun dreiecke(p: List<Markttechnik.Punkt>, c: List<Double>): List<Treffer> {
        val aus = ArrayList<Treffer>()
        val n = c.size
        for (i in 0..p.size - 4) {
            val vier = p.subList(i, i + 4)
            val (ha, hb) = vier.filter { it.hoch }
            val (ta, tb) = vier.filter { !it.hoch }
            val hFlach = abs(hb.wert - ha.wert) <= TOL_FLACH * ha.wert
            val tFlach = abs(tb.wert - ta.wert) <= TOL_FLACH * ta.wert
            val hFaellt = hb.wert < ha.wert && !hFlach
            val tSteigt = tb.wert > ta.wert && !tFlach
            val art = when {
                hFaellt && tSteigt -> "dreieck_sym"
                hFlach && tSteigt -> "dreieck_auf"
                tFlach && hFaellt -> "dreieck_ab"
                else -> continue
            }
            val sh = (hb.wert - ha.wert) / (hb.index - ha.index)
            val st = (tb.wert - ta.wert) / (tb.index - ta.index)
            val oben = { j: Int -> ha.wert + sh * (j - ha.index) }
            val unten = { j: Int -> ta.wert + st * (j - ta.index) }
            if (st - sh <= 1e-12) continue
            val erste = vier.first().index
            val letzte = vier.last().index
            // Kerze, an der sich die Linien treffen
            val spitze = erste + (oben(erste) - unten(erste)) / (st - sh)
            val ende = minOf(n - 1, (erste + DREIECK_ANTEIL * (spitze - erste)).toInt())
            for (j in letzte + 1..ende) {
                if (c[j] > oben(j)) { aus += Treffer(art, 1, erste, j, oben(j)); break }
                if (c[j] < unten(j)) { aus += Treffer(art, -1, erste, j, unten(j)); break }
            }
        }
        return aus
    }

    // Alle bestaetigten Formationen, nach der Kerze des Durchbruchs geordnet; bei gleicher Kerze steht die Umkehrformation
    // nach dem Dreieck (ARTEN nennt die Rangfolge), damit sie als juengste gilt.
    fun erkennen(h: List<Double>, l: List<Double>, c: List<Double>): List<Treffer> {
        val p = Markttechnik.zerlegen(h, l, c).punkte
        if (p.size < 4) return emptyList()
        val gespiegelt = p.map { Markttechnik.Punkt(it.index, -it.wert, !it.hoch) }
        return (umkehr(p, c, 1) + umkehr(gespiegelt, c, -1) + dreiecke(p, c))
            .sortedWith(compareBy<Treffer> { it.bis }.thenByDescending { ARTEN.indexOf(it.art) })
    }

    // Deutung zum letzten Kurs: die juengste Formation, deren Durchbruch hoechstens FENSTER Handelstage zurueckliegt.
    // wert: Handelstage seit dem Durchbruch, kreuzTag: sein Tag. null ohne solche Formation.
    fun deutung(tage: List<Long>, h: List<Double>, l: List<Double>, c: List<Double>): Indikatoren.Deutung? {
        val n = c.size
        if (n < 60 || tage.size != n) return null
        val t = erkennen(h, l, c).lastOrNull()?.takeIf { it.bis >= n - FENSTER } ?: return null
        return Indikatoren.Deutung(ART, t.richtung, t.art, (n - 1 - t.bis).toDouble(), tage[t.bis], t.richtung > 0)
    }

    // Schluessel der Deutung neben den Kerzenmustern.
    const val ART = "formation"
}
