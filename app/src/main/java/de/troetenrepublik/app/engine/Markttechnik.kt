package de.troetenrepublik.app.engine

// Trend aus Bewegung und Korrektur (0.78). Die Kerzen werden in Hoch- und Tiefpunkte zerlegt: Ein Hoch gilt, sobald der
// Kurs danach um mindestens MINDEST_ATR mittlere Tagesspannen (ATR ueber ATR_TAGE Kerzen) faellt, ein Tief, sobald er
// danach um so viel steigt. Kleinere Schwankungen gehoeren zur laufenden Bewegung.
// Aufwaertstrend: das letzte Hoch und das letzte Tief liegen je ueber ihrem Vorgaenger. Abwaertstrend: beide darunter.
// Der Aufwaertstrend bricht, wenn der Kurs unter das letzte Korrekturtief faellt, der Abwaertstrend, wenn er ueber das
// letzte Korrekturhoch steigt.
object Markttechnik {

    const val ATR_TAGE = 14
    const val MINDEST_ATR = 2.0

    data class Punkt(val index: Int, val wert: Double, val hoch: Boolean)

    // punkte: bestaetigte Hoch- und Tiefpunkte im Wechsel. kandidat: Extrem der laufenden Bewegung (noch nicht
    // bestaetigt); hoch = true heisst, der Kurs steigt gerade.
    class Zerlegung(val punkte: List<Punkt>, val kandidat: Punkt?)

    fun zerlegen(h: List<Double>, l: List<Double>, c: List<Double>, faktor: Double = MINDEST_ATR): Zerlegung {
        val n = c.size
        if (h.size != n || l.size != n || n < ATR_TAGE + 2) return Zerlegung(emptyList(), null)
        val atr = Indikatoren.atr(h, l, c, ATR_TAGE)
        val start = atr.indexOfFirst { it != null }
        if (start < 0) return Zerlegung(emptyList(), null)
        val punkte = ArrayList<Punkt>()
        var hi = start
        var lo = start
        var richtung = 0
        fun tiefstes(von: Int, bis: Int) = (von..bis).minByOrNull { l[it] } ?: bis
        fun hoechstes(von: Int, bis: Int) = (von..bis).maxByOrNull { h[it] } ?: bis
        for (i in start + 1 until n) {
            val schwelle = faktor * (atr[i] ?: continue)
            if (!(schwelle > 0)) continue
            when (richtung) {
                0 -> {
                    if (h[i] > h[hi]) hi = i
                    if (l[i] < l[lo]) lo = i
                    if (hi < i && h[hi] - l[i] >= schwelle) { punkte += Punkt(hi, h[hi], true); richtung = -1; lo = tiefstes(hi + 1, i) }
                    else if (lo < i && h[i] - l[lo] >= schwelle) { punkte += Punkt(lo, l[lo], false); richtung = 1; hi = hoechstes(lo + 1, i) }
                }
                1 -> {
                    if (h[i] > h[hi]) hi = i
                    else if (h[hi] - l[i] >= schwelle) { punkte += Punkt(hi, h[hi], true); richtung = -1; lo = tiefstes(hi + 1, i) }
                }
                else -> {
                    if (l[i] < l[lo]) lo = i
                    else if (h[i] - l[lo] >= schwelle) { punkte += Punkt(lo, l[lo], false); richtung = 1; hi = hoechstes(lo + 1, i) }
                }
            }
        }
        val kandidat = when (richtung) { 1 -> Punkt(hi, h[hi], true); -1 -> Punkt(lo, l[lo], false); else -> null }
        return Zerlegung(punkte, kandidat)
    }

    enum class Zustand { AUF, AB, AUF_GEBROCHEN, AB_GEBROCHEN, KEINER }

    // marke: Kurs, an dem der Trend bricht (AUF: letztes Korrekturtief, AB: letztes Korrekturhoch) bzw. gebrochen ist;
    // markeIndex: Kerze dieses Punkts.
    class Lage(val zustand: Zustand, val marke: Double?, val markeIndex: Int?, val zerlegung: Zerlegung)

    // 1 = der letzte Punkt liegt ueber dem vorletzten, -1 = darunter, 0 = zu wenige Punkte oder gleich.
    private fun folge(p: List<Punkt>): Int {
        if (p.size < 2) return 0
        val a = p[p.size - 2].wert
        val b = p[p.size - 1].wert
        return if (b > a) 1 else if (b < a) -1 else 0
    }

    fun lage(h: List<Double>, l: List<Double>, c: List<Double>): Lage {
        val z = zerlegen(h, l, c)
        val hc = z.punkte.filter { it.hoch }
        val tc = z.punkte.filter { !it.hoch }
        val k = z.kandidat
        // Das laufende Extrem zaehlt erst, wenn es ueber das letzte Hoch bzw. unter das letzte Tief hinausgeht
        val neuesHoch = k != null && k.hoch && hc.isNotEmpty() && k.wert > hc.last().wert
        val neuesTief = k != null && !k.hoch && tc.isNotEmpty() && k.wert < tc.last().wert
        val hs = if (neuesHoch) hc + k!! else hc
        val ts = if (neuesTief) tc + k!! else tc
        return when {
            folge(hs) > 0 && folge(ts) > 0 -> Lage(Zustand.AUF, tc.last().wert, tc.last().index, z)
            folge(hs) < 0 && folge(ts) < 0 -> Lage(Zustand.AB, hc.last().wert, hc.last().index, z)
            neuesTief && folge(hc) > 0 && folge(tc) > 0 -> Lage(Zustand.AUF_GEBROCHEN, tc.last().wert, tc.last().index, z)
            neuesHoch && folge(hc) < 0 && folge(tc) < 0 -> Lage(Zustand.AB_GEBROCHEN, hc.last().wert, hc.last().index, z)
            else -> Lage(Zustand.KEINER, null, null, z)
        }
    }

    // Stopp fuer einen Kauf aus dem Trend: das letzte Korrekturtief eines laufenden Aufwaertstrends, wenn es unter dem
    // Kurs liegt. abstandPct: Abstand vom Kurs in Prozent.
    class Stopp(val kurs: Double, val abstandPct: Double)

    fun stopp(h: List<Double>, l: List<Double>, c: List<Double>): Stopp? {
        val la = lage(h, l, c)
        val marke = la.marke ?: return null
        val kurs = c.lastOrNull() ?: return null
        if (la.zustand != Zustand.AUF || !(marke > 0) || marke >= kurs) return null
        return Stopp(marke, (1 - marke / kurs) * 100)
    }

    // Linien fuer den Chart, je Kerze ein Wert oder null: der Verlauf von Punkt zu Punkt (gerade verbunden, bis zum
    // laufenden Extrem), die Punkte selbst und die Bruchmarke ab ihrem Punkt bis zur letzten Kerze.
    class Linien(val verlauf: List<Double?>, val punkte: List<Double?>, val marke: List<Double?>)

    fun linien(la: Lage, n: Int): Linien {
        val alle = la.zerlegung.punkte + listOfNotNull(la.zerlegung.kandidat?.takeIf { k -> la.zerlegung.punkte.none { it.index == k.index } })
        val verlauf = arrayOfNulls<Double>(n)
        val punkte = arrayOfNulls<Double>(n)
        alle.zipWithNext { a, b ->
            for (i in a.index..b.index) if (i in 0 until n) {
                verlauf[i] = if (b.index == a.index) a.wert else a.wert + (b.wert - a.wert) * (i - a.index) / (b.index - a.index)
            }
        }
        alle.forEach { if (it.index in 0 until n) punkte[it.index] = it.wert }
        val marke = arrayOfNulls<Double>(n)
        val ab = la.markeIndex
        if (la.marke != null && ab != null) for (i in ab until n) marke[i] = la.marke
        return Linien(verlauf.toList(), punkte.toList(), marke.toList())
    }
}
