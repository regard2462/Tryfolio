package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Range

// Kleine Verlaufsdiagramme in den Maerkten: wie viele Punkte fuer eine Linie noetig sind, welche Kursaufloesung zu
// welchem Zeitraum passt, wie Beitraege in Koerbe gezaehlt werden und wie ein Kurs auf den Vortagesschluss bezogen wird.
// Rein rechnerisch, ohne Netz.
object Verlaufsdarstellung {

    // Unter dieser Punktzahl ist eine Linie keine; dann stehen Balken, je Punkt einer.
    const val MIN_LINIE = 10

    enum class Art { LINIE, BALKEN, KEINE }

    fun art(punkte: Int): Art = when {
        punkte >= MIN_LINIE -> Art.LINIE
        punkte >= 1 -> Art.BALKEN
        else -> Art.KEINE
    }

    private const val STUNDE = 3_600_000L
    private const val TAG = 24 * STUNDE

    // Kursaufloesung je Zeitraum in Tagen: Tag in 2-Minuten-Schritten, Woche in 15 Minuten, Monat in 30 Minuten, laenger
    // taeglich (siehe Range).
    fun rangeFuer(tage: Int): Range = when {
        tage <= 1 -> Range.D1
        tage <= 7 -> Range.W1
        tage <= 31 -> Range.M1
        tage <= 92 -> Range.M3
        tage <= 183 -> Range.M6
        else -> Range.Y1
    }

    // Breite eines Korbs fuer Zaehlungen: stuendlich am Tag, in sechs Stunden in der Woche, sonst taeglich.
    fun korbBreite(tage: Int): Long = when {
        tage <= 1 -> STUNDE
        tage <= 7 -> 6 * STUNDE
        else -> TAG
    }

    // Anzahl Koerbe fuer den Zeitraum
    fun korbAnzahl(tage: Int): Int = ((tage * TAG) / korbBreite(tage)).toInt()

    // Zaehlt zeiten (Millisekunden) in anzahl Koerbe der Breite breite, der letzte endet bei ende; aeltester Korb zuerst.
    // Zeiten ausserhalb fallen weg.
    fun koerbe(zeiten: List<Long>, ende: Long, anzahl: Int, breite: Long): List<Int> {
        val aus = IntArray(anzahl)
        val start = ende - anzahl * breite
        for (t in zeiten) {
            if (t < start || t >= ende) continue
            aus[((t - start) / breite).toInt().coerceIn(0, anzahl - 1)]++
        }
        return aus.toList()
    }

    // Kurs als Wert eines Betrags (start), der am Bezugskurs eingesetzt wurde: aelteste Kerze zuerst, nur der Zeitraum ab
    // ab (Millisekunden). bezug null: die erste Kerze des Zeitraums. Leer ohne Kerzen im Zeitraum.
    fun verlauf(kerzen: List<Candle>, ab: Long, bezug: Double?, start: Double = 1000.0): List<Pair<Long, Double>> {
        val k = kerzen.filter { it.close.isFinite() && it.close > 0 && it.t >= ab }.sortedBy { it.t }
        if (k.isEmpty()) return emptyList()
        val basis = bezug?.takeIf { it.isFinite() && it > 0 } ?: k.first().close
        return k.map { it.t to it.close / basis * start }
    }

    // Vortagesschluss in der Waehrung der Tageskerzen der Intraday-Reihe: Verhaeltnis der letzten beiden Tagesschluesse
    // (taeglich, gleiche Quelle) auf den letzten Kurs der Intraday-Reihe uebertragen. Null ohne zwei Tagesschluesse.
    fun vortagesschluss(taeglich: List<Candle>, letzterKurs: Double): Double? {
        val k = taeglich.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        if (k.size < 2 || !letzterKurs.isFinite() || letzterKurs <= 0) return null
        return letzterKurs * k[k.size - 2].close / k.last().close
    }

    // Korb, in den der Zeitpunkt t faellt (Koerbe ab start, je breite Millisekunden, n Stueck); null ausserhalb.
    fun korbVon(t: Long, start: Long, breite: Long, n: Int): Int? =
        if (t < start || breite <= 0 || t >= start + n * breite) null else ((t - start) / breite).toInt()

    // Index des Punkts, der t am naechsten liegt; null bei leerer Liste.
    fun naechster(zeiten: List<Long>, t: Long): Int? = zeiten.indices.minByOrNull { kotlin.math.abs(zeiten[it] - t) }

    // Beginn des Zeitraums in Millisekunden: beim Tag der Beginn der juengsten Handelssitzung der Reihe, sonst Tage vor dem
    // letzten Punkt.
    fun beginn(kerzen: List<Candle>, tage: Int): Long {
        val letzte = kerzen.maxOfOrNull { it.t } ?: return 0L
        if (tage > 1) return letzte - tage * TAG
        // Sitzung: alle Kerzen, die nicht mehr als zehn Stunden vor der juengsten liegen und keine Luecke ueber sechs Stunden haben
        val t = kerzen.map { it.t }.sortedDescending()
        var ab = t.first()
        for (i in 1 until t.size) { if (ab - t[i] > 6 * STUNDE) break; ab = t[i] }
        return ab
    }
}
