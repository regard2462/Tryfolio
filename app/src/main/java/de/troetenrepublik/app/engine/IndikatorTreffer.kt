package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.abs
import kotlin.math.sqrt

// Trefferbilanz der Indikatoren (seit 5.92): Wie oft lag ein Signal eines Indikators bei diesem Wert in der
// Vergangenheit richtig? Ein Signal entsteht am Tagesschluss, wenn der Indikator nach der ueblichen Lesart die Seite
// wechselt (etwa Kurs kreuzt den Durchschnitt). Richtig heisst: TAGE Handelstage spaeter stand der Kurs in der
// Signalrichtung. Zum Vergleich zaehlt, wie oft der Kurs in derselben Zeit ohnehin in diese Richtung lief; erst der
// Abstand dazu sagt etwas ueber die Aussagekraft. Bei der ATR (ohne Richtung) zaehlt eine Unruhe-Warnung als
// richtig, wenn die Bewegung danach groesser war als die uebliche.
object IndikatorTreffer {

    // Handelstage bis zur Pruefung eines Signals.
    const val TAGE = 20

    // Weniger Signale reichen fuer keine Bilanz.
    const val MIN_SIGNALE = 5

    enum class Stufe { ERKENNBAR, GERING, UMGEKEHRT, ZU_WENIG }

    // treffer und vergleich als Anteil (0,58 = 58 %); null bei zu wenigen Signalen. ab: Tagesnummer des ersten
    // pruefbaren Tags.
    data class Bilanz(
        val art: String,
        val signale: Int,
        val richtig: Int,
        val treffer: Double?,
        val vergleich: Double?,
        val stufe: Stufe,
        val ab: Long,
    )

    // Signale je Tag: 1 = steigende Kurse erwartet, -1 = fallende, 0 = keines. Bei der ATR steht 1 fuer eine
    // Unruhe-Warnung. Leer, wenn der Indikator fuer diese Reihe nicht rechenbar ist (OBV ohne Umsaetze).
    // eroeffnung braucht nur das Kerzenmuster; ohne sie ist es nicht rechenbar.
    fun signale(art: String, schluss: List<Double>, hoch: List<Double>, tief: List<Double>, umsatz: List<Double>, eroeffnung: List<Double> = emptyList()): IntArray {
        val n = schluss.size
        val s = IntArray(n)
        // Wechsel von a ueber b (1) bzw. darunter (-1) zwischen Tag i-1 und i.
        fun kreuz(a: List<Double?>, b: List<Double?>) {
            for (i in 1 until n) {
                val a0 = a[i - 1] ?: continue; val b0 = b[i - 1] ?: continue
                val a1 = a[i] ?: continue; val b1 = b[i] ?: continue
                if (a0 <= b0 && a1 > b1) s[i] = 1 else if (a0 >= b0 && a1 < b1) s[i] = -1
            }
        }
        // Verlassen der Zonen: zurueck ueber unten (1), zurueck unter oben (-1).
        fun zonen(r: List<Double?>, unten: Double, oben: Double) {
            for (i in 1 until n) {
                val v0 = r[i - 1] ?: continue; val v1 = r[i] ?: continue
                if (v0 < unten && v1 >= unten) s[i] = 1 else if (v0 > oben && v1 <= oben) s[i] = -1
            }
        }
        when (art) {
            "sma50" -> kreuz(schluss, Indikatoren.sma(schluss, 50))
            "sma200" -> kreuz(schluss, Indikatoren.sma(schluss, 200))
            "kreuz" -> kreuz(Indikatoren.sma(schluss, 50), Indikatoren.sma(schluss, 200))
            "bb" -> {
                // Unter das untere Band gefallen: Erholung erwartet; ueber das obere gestiegen: Ruecksetzer erwartet
                val b = Indikatoren.bollinger(schluss)
                for (i in 1 until n) {
                    val u0 = b.unten[i - 1] ?: continue; val u1 = b.unten[i] ?: continue
                    val o0 = b.oben[i - 1] ?: continue; val o1 = b.oben[i] ?: continue
                    if (schluss[i - 1] >= u0 && schluss[i] < u1) s[i] = 1
                    else if (schluss[i - 1] <= o0 && schluss[i] > o1) s[i] = -1
                }
            }
            "rsi" -> zonen(Indikatoren.rsi(schluss, 14), 30.0, 70.0)
            "stoch" -> zonen(Indikatoren.stochastik(hoch, tief, schluss).k, 20.0, 80.0)
            "macd" -> Indikatoren.macd(schluss).let { kreuz(it.linie, it.signal) }
            "ichimoku" -> {
                // Kurs verlaesst die Wolke nach oben oder nach unten
                val ich = Indikatoren.ichimoku(hoch, tief)
                fun seite(i: Int): Int? {
                    val a = ich.spanA[i] ?: return null; val b = ich.spanB[i] ?: return null
                    return when { schluss[i] > maxOf(a, b) -> 1; schluss[i] < minOf(a, b) -> -1; else -> 0 }
                }
                for (i in 1 until n) {
                    val v = seite(i - 1) ?: continue; val j = seite(i) ?: continue
                    if (j != v && j != 0) s[i] = j
                }
            }
            "sar" -> {
                val sr = Indikatoren.sar(hoch, tief)
                for (i in 2 until n) if (sr.aufwaerts[i] != sr.aufwaerts[i - 1]) s[i] = if (sr.aufwaerts[i]) 1 else -1
            }
            "obv" -> {
                // Nur mit Umsaetzen an den meisten Tagen; OBV kreuzt seinen Durchschnitt ueber 20 Tage
                if (n == 0 || umsatz.size != n || umsatz.count { it > 0 } < n * 2 / 3) return IntArray(0)
                val o = Indikatoren.obv(schluss, umsatz)
                kreuz(o, Indikatoren.sma(o, 20))
            }
            "kerzen" -> {
                // Muster mit Richtung (5.94); nur wenn die meisten Tage einen Eroeffnungskurs haben
                if (n == 0 || eroeffnung.size != n || eroeffnung.count { it.isFinite() && it > 0 } < n * 2 / 3) return IntArray(0)
                return Kerzenmuster.signale(eroeffnung, hoch, tief, schluss)
            }
            "atr" -> {
                // Unruhe-Warnung: ATR in Prozent des Kurses steigt ueber das 1,3-Fache ihres Medians im Jahr davor
                val a = Indikatoren.atr(hoch, tief, schluss)
                val p = schluss.indices.map { i -> a[i]?.let { it / schluss[i] } }
                var vorher = false
                for (i in 250 until n) {
                    val v = p[i] ?: continue
                    val fenster = p.subList(i - 250, i).filterNotNull().sorted()
                    if (fenster.size < 100) continue
                    val jetzt = v >= fenster[fenster.size / 2] * 1.3
                    if (jetzt && !vorher) s[i] = 1
                    vorher = jetzt
                }
            }
        }
        return s
    }

    // Bilanz eines Indikators. Signale ohne vollen Pruefzeitraum zaehlen nicht.
    fun bilanz(art: String, schluss: List<Double>, hoch: List<Double>, tief: List<Double>, umsatz: List<Double>, tage: List<Long>, h: Int = TAGE, eroeffnung: List<Double> = emptyList()): Bilanz? {
        val n = schluss.size
        if (n <= h + 30 || tage.size != n) return null
        val s = signale(art, schluss, hoch, tief, umsatz, eroeffnung)
        if (s.isEmpty()) return null
        val ende = n - h
        val bewegung = DoubleArray(ende) { i -> schluss[i + h] / schluss[i] - 1 }
        // Nur Signale, deren Pruefzeitraeume sich nicht ueberlappen: mindestens h Handelstage nach dem letzten gezaehlten.
        // Sonst zaehlte dieselbe Kursbewegung mehrfach, und der Standardfehler waere zu klein (Hansen und Hodrick 1980).
        val gezaehlt = ArrayList<Int>()
        for (i in 0 until ende) if (s[i] != 0 && (gezaehlt.isEmpty() || i - gezaehlt.last() >= h)) gezaehlt += i
        val anzahl: Int
        val richtig: Int
        val vergleich: Double
        if (art == "atr") {
            // Groesser als ueblich: ueber dem Median der Betraege aller Bewegungen
            val betrag = bewegung.map { abs(it) }
            val median = betrag.sorted()[betrag.size / 2]
            val idx = gezaehlt
            anzahl = idx.size
            richtig = idx.count { betrag[it] > median }
            vergleich = betrag.count { it > median }.toDouble() / betrag.size
        } else {
            // Ohne Signal: Anteil aller Tage, an denen der Kurs danach stieg bzw. fiel, gewichtet nach den Signalen
            val auf = bewegung.count { it > 0 }.toDouble() / ende
            val ab = bewegung.count { it < 0 }.toDouble() / ende
            val idx = gezaehlt
            anzahl = idx.size
            richtig = idx.count { (s[it] > 0 && bewegung[it] > 0) || (s[it] < 0 && bewegung[it] < 0) }
            vergleich = if (anzahl == 0) 0.0 else idx.sumOf { if (s[it] > 0) auf else ab } / anzahl
        }
        // Erster Tag mit einem Signal: davor liegt die Anlaufzeit des Indikators (SMA 200, Ichimoku), die nichts prueft
        val ab = tage[s.indexOfFirst { it != 0 }.takeIf { it >= 0 } ?: 0]
        if (anzahl < MIN_SIGNALE) return Bilanz(art, anzahl, richtig, null, null, Stufe.ZU_WENIG, ab)
        val treffer = richtig.toDouble() / anzahl
        return Bilanz(art, anzahl, richtig, treffer, vergleich, stufe(treffer, vergleich, anzahl), ab)
    }

    // Erkennbar: mindestens 5 Prozentpunkte und zwei Standardfehler ueber dem Vergleich; umgekehrt: ebenso deutlich
    // darunter; sonst gering. Kleine Stichproben bleiben so meist bei gering.
    fun stufe(treffer: Double, vergleich: Double, anzahl: Int): Stufe {
        if (anzahl < MIN_SIGNALE) return Stufe.ZU_WENIG
        val p = vergleich.coerceIn(0.05, 0.95)
        val fehler = sqrt(p * (1 - p) / anzahl)
        val d = treffer - vergleich
        return when {
            d >= 0.05 && d >= 2 * fehler -> Stufe.ERKENNBAR
            d <= -0.05 && d <= -2 * fehler -> Stufe.UMGEKEHRT
            else -> Stufe.GERING
        }
    }

    // Bilanzen aller Indikatoren aus Tageskerzen (aufsteigend); Hoch und Tief fehlen manchen Reihen, dann zaehlt der
    // Schlusskurs.
    fun alle(kerzen: List<Candle>, arten: List<String>, h: Int = TAGE): Map<String, Bilanz> {
        val l = kerzen.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        val schluss = l.map { it.close }
        val hoch = l.map { if (it.high > 0 && it.high.isFinite()) maxOf(it.high, it.close) else it.close }
        val tief = l.map { if (it.low > 0 && it.low.isFinite()) minOf(it.low, it.close) else it.close }
        val umsatz = l.map { if (it.volume.isFinite() && it.volume > 0) it.volume else 0.0 }
        val tage = l.map { Math.floorDiv(it.t, 86_400_000L) }
        val eroeffnung = l.map { if (it.open > 0 && it.open.isFinite()) it.open else Double.NaN }
        return arten.mapNotNull { a -> bilanz(a, schluss, hoch, tief, umsatz, tage, h, eroeffnung)?.let { a to it } }.toMap()
    }
}
