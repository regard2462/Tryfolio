package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import kotlin.math.abs
import kotlin.math.sqrt

// Volatilitaetskarte (0.76): IV30 aus den Verfaellen interpoliert, realisierte Volatilitaet der letzten 30 Tage,
// Put-Aufschlag bei 25 Delta und Laufzeitstruktur der Volatilitaet am Geld.
object OptionsVola {

    data class Punkt(val tage: Int, val iv: Double)

    data class Ergebnis(
        val iv30: Double?,
        val rv30: Double?,
        val putAufschlag: Double?,
        val struktur: List<Punkt>,
    ) {
        val verhaeltnis: Double? get() = if (iv30 != null && rv30 != null && rv30 > 0) iv30 / rv30 else null
    }

    private const val TAG_S = 86_400L
    private const val IV_MIN = 0.01
    private const val IV_MAX = 5.0

    private fun gueltig(k: Kontrakt) = k.iv in IV_MIN..IV_MAX && (k.geld > 0 || k.offen > 0)

    // Volatilitaet am Geld: Mittel von Call und Put am Basispreis, der dem Kurs am naechsten liegt.
    fun ivAmGeld(am: List<Kontrakt>, kurs: Double): Double? {
        val s = am.filter { gueltig(it) }.map { it.strike }.distinct().minByOrNull { abs(it - kurs) } ?: return null
        return am.filter { it.strike == s && gueltig(it) }.map { it.iv }.average()
    }

    fun struktur(kurs: Double, kontrakte: List<Kontrakt>, jetztS: Long): List<Pair<Long, Punkt>> =
        kontrakte.map { it.verfall }.distinct().sorted().mapNotNull { v ->
            val tage = ((v - jetztS) / TAG_S).toInt()
            if (tage < 2) return@mapNotNull null
            ivAmGeld(kontrakte.filter { it.verfall == v }, kurs)?.let { v to Punkt(tage, it) }
        }

    // Linear in der Gesamtvarianz (iv^2 x Tage) zwischen den beiden Verfaellen um 30 Tage. Liegen alle Verfaelle auf
    // einer Seite, gilt der naechste nur dann, wenn er hoechstens 15 Tage von 30 entfernt ist.
    fun iv30(s: List<Punkt>): Double? {
        if (s.isEmpty()) return null
        val unter = s.filter { it.tage <= 30 }.maxByOrNull { it.tage }
        val ueber = s.filter { it.tage >= 30 }.minByOrNull { it.tage }
        if (unter != null && ueber != null) {
            if (unter.tage == ueber.tage) return unter.iv
            val wa = unter.iv * unter.iv * unter.tage
            val wb = ueber.iv * ueber.iv * ueber.tage
            val w = wa + (wb - wa) * (30 - unter.tage).toDouble() / (ueber.tage - unter.tage)
            return sqrt(w / 30.0)
        }
        val n = s.minByOrNull { abs(it.tage - 30) } ?: return null
        return n.iv.takeIf { abs(n.tage - 30) <= 15 }
    }

    // Realisierte Volatilitaet der letzten 30 Handelstage (31 Schlusskurse), auf ein Jahr gerechnet.
    fun realisiert(schluesse: List<Double>): Double? {
        if (schluesse.size < 31) return null
        return Pricing.historicalVol(schluesse.takeLast(31), Double.NaN, 252.0).takeIf { it.isFinite() }
    }

    // IV des Puts bei Delta -0,25 minus IV am Geld, am Verfall um 30 Tage. Das Delta kommt aus Black-Scholes mit der
    // Volatilitaet des jeweiligen Puts; der Wert entsteht nur zwischen zwei Puts, die -0,25 einschliessen.
    fun putAufschlag(kurs: Double, am: List<Kontrakt>, atm: Double, tage: Int): Double? {
        val t = tage / 365.0
        val punkte = am.filter { !it.call && gueltig(it) && it.strike < kurs * 1.02 }.map { k ->
            Pricing.blackScholes(kurs, k.strike, t, k.iv, false).delta to k.iv
        }.sortedBy { it.first }
        val a = punkte.lastOrNull { it.first <= -0.25 } ?: return null
        val b = punkte.firstOrNull { it.first >= -0.25 } ?: return null
        val iv = if (b.first == a.first) a.second else a.second + (b.second - a.second) * (-0.25 - a.first) / (b.first - a.first)
        return iv - atm
    }

    fun berechnen(kurs: Double, kontrakte: List<Kontrakt>, schluesse: List<Double>, jetztS: Long): Ergebnis? {
        if (kurs <= 0) return null
        val mit = struktur(kurs, kontrakte, jetztS)
        val punkte = mit.map { it.second }
        val iv30 = iv30(punkte)
        val rv = realisiert(schluesse)
        if (iv30 == null && rv == null) return null
        val naechster = mit.minByOrNull { abs(it.second.tage - 30) }
        val aufschlag = naechster?.let { (v, p) ->
            if (abs(p.tage - 30) > 20) null else putAufschlag(kurs, kontrakte.filter { it.verfall == v }, p.iv, p.tage)
        }
        return Ergebnis(iv30, rv, aufschlag, punkte)
    }
}

// Tageswerte der 30-Tage-Volatilitaet je Symbol, lokal gespeichert: "Tag:Wert" durch Leerzeichen getrennt, Tag als
// Tageszahl seit 1970, Wert in Promille. Hoechstens 400 Tage; ab 20 Tagen gibt es Rang und Perzentil.
object IvHistorie {
    const val MAX = 400
    const val MIN_TAGE = 20
    private const val FENSTER = 252

    data class Lage(val tage: Int, val seit: Int, val rang: Double?, val perzentil: Double?)

    fun lesen(text: String?): List<Pair<Int, Double>> =
        text.orEmpty().split(' ').mapNotNull { z ->
            val i = z.indexOf(':')
            if (i <= 0) return@mapNotNull null
            val tag = z.substring(0, i).toIntOrNull() ?: return@mapNotNull null
            val v = z.substring(i + 1).toIntOrNull() ?: return@mapNotNull null
            tag to v / 1000.0
        }.sortedBy { it.first }

    fun schreiben(l: List<Pair<Int, Double>>): String =
        l.joinToString(" ") { "${it.first}:${Math.round(it.second * 1000)}" }

    // Ersetzt den Wert desselben Tages und behaelt nur die letzten MAX Tage.
    fun merken(text: String?, tag: Int, iv: Double): String {
        val neu = lesen(text).filter { it.first != tag } + (tag to iv)
        return schreiben(neu.sortedBy { it.first }.takeLast(MAX))
    }

    // Lage des Werts im Fenster der letzten 252 gespeicherten Tage; Rang und Perzentil erst ab MIN_TAGE Tagen.
    fun lage(l: List<Pair<Int, Double>>, iv: Double): Lage? {
        if (l.isEmpty()) return null
        val f = l.takeLast(FENSTER).map { it.second }
        if (f.size < MIN_TAGE) return Lage(f.size, l.first().first, null, null)
        val lo = f.min()
        val hi = f.max()
        val rang = if (hi > lo) ((iv - lo) / (hi - lo)).coerceIn(0.0, 1.0) else 0.5
        val perz = f.count { it < iv }.toDouble() / f.size
        return Lage(f.size, l.first().first, rang, perz)
    }
}
