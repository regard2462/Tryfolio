package de.troetenrepublik.app.engine

// Squeeze (0.78): Die Bollinger-Baender (20, 2) liegen innerhalb des Keltner-Kanals (EMA 20 mit 1,5 mal ATR 20). Die
// Schwankung ist dann ungewoehnlich klein. Das Signal faellt an der ersten Kerze, an der die Baender den Kanal wieder
// verlassen, in Richtung des Momentums. Momentum: Abstand des Schlusskurses zur Mitte aus Donchian-Mitte und
// Durchschnitt der letzten n Kerzen, geglaettet als Endwert der Regressionsgeraden ueber n Kerzen.
object Squeeze {

    const val ART = "squeeze"
    const val N = 20
    const val BB_K = 2.0
    const val KELTNER_K = 1.5

    // eng: Baender im Kanal (null, solange Werte fehlen). momentum je Kerze (null am Anfang).
    class Reihe(val eng: List<Boolean?>, val momentum: List<Double?>)

    // Austritt aus der engen Phase an der Kerze index, auf: Momentum ueber null.
    data class Signal(val index: Int, val auf: Boolean)

    fun rechnen(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, n: Int = N): Reihe {
        val bb = Indikatoren.bollinger(schluss, n, BB_K)
        val kc = Indikatoren.keltner(hoch, tief, schluss, n, KELTNER_K, n)
        val eng = schluss.indices.map { i ->
            val bo = bb.oben[i]; val bu = bb.unten[i]; val ko = kc.oben[i]; val ku = kc.unten[i]
            if (bo == null || bu == null || ko == null || ku == null) null else bo < ko && bu > ku
        }
        val mitte = Indikatoren.sma(schluss, n)
        val abstand = schluss.indices.map { i ->
            val m = mitte[i]
            if (m == null) null else {
                val oben = (i - n + 1..i).maxOf { hoch[it] }
                val unten = (i - n + 1..i).minOf { tief[it] }
                schluss[i] - ((oben + unten) / 2 + m) / 2
            }
        }
        // Endwert der Regressionsgeraden ueber die letzten n Abstaende
        val xm = (n - 1) / 2.0
        val sxx = (0 until n).sumOf { (it - xm) * (it - xm) }
        val momentum = schluss.indices.map { i ->
            if (i < n - 1 || abstand[i - n + 1] == null) null else {
                var ym = 0.0
                for (j in 0 until n) ym += abstand[i - n + 1 + j]!!
                ym /= n
                var sxy = 0.0
                for (j in 0 until n) sxy += (j - xm) * (abstand[i - n + 1 + j]!! - ym)
                ym + (if (sxx > 0) sxy / sxx else 0.0) * (n - 1 - xm)
            }
        }
        return Reihe(eng, momentum)
    }

    // Alle Austritte: Vorkerze eng, diese nicht mehr; ohne Momentum oder bei Momentum null kein Signal.
    fun signale(r: Reihe): List<Signal> {
        val aus = ArrayList<Signal>()
        for (i in 1 until r.eng.size) {
            if (r.eng[i - 1] == true && r.eng[i] == false) {
                val m = r.momentum[i] ?: continue
                if (m != 0.0) aus += Signal(i, m > 0)
            }
        }
        return aus
    }

    // Wie viele Kerzen ein Austritt zurueckliegen darf, damit er als Signal zaehlt.
    const val FENSTER = 10

    // Deutung zur letzten Kerze: „eng“ (stufe 0, wert = Kerzen in der engen Phase), „auf“ oder „ab“ nach einem Austritt
    // in den letzten FENSTER Kerzen (stufe 1 oder -1, wert = Kerzen seit dem Austritt), sonst „offen“ (stufe 0, wert =
    // Momentum). Null, solange die Reihe zu kurz ist.
    fun deutung(r: Reihe): Indikatoren.Deutung? {
        val ende = r.eng.size - 1
        val jetzt = r.eng.lastOrNull() ?: return null
        val m = r.momentum.lastOrNull() ?: return null
        if (jetzt) {
            var seit = 0
            while (ende - seit >= 0 && r.eng[ende - seit] == true) seit++
            return Indikatoren.Deutung(ART, 0, "eng", seit.toDouble())
        }
        val s = signale(r).lastOrNull()?.takeIf { ende - it.index < FENSTER }
            ?: return Indikatoren.Deutung(ART, 0, "offen", m)
        return Indikatoren.Deutung(ART, if (s.auf) 1 else -1, if (s.auf) "auf" else "ab", (ende - s.index).toDouble())
    }

    // Linien fuer den erweiterten Chart: das Momentum als Balken und ein Punkt auf der Nulllinie je Kerze der engen Phase.
    fun punkte(r: Reihe): List<Double?> = r.eng.map { if (it == true) 0.0 else null }
}
