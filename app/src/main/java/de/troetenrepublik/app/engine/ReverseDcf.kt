package de.troetenrepublik.app.engine

import kotlin.math.pow

// Umgekehrte Bewertung (Reverse DCF): Statt aus einer Wachstumsannahme einen fairen Wert zu rechnen (Analysis.fairValue),
// sucht sie das jaehrliche Gewinnwachstum, das im heutigen Kurs steckt. Annahme wie beim fairen Wert: Der Gewinn je
// Aktie waechst jahre Jahre lang gleichmaessig, danach wird die Aktie zum Ziel-KGV bewertet, und der Kaeufer will
// pro Jahr die Rendite rendite (Prozent) erzielen. Zaehlt nur das KGV: Kurs und Gewinn kuerzen sich heraus.
object ReverseDcf {

    // Suchbereich der Bisektion: -50 % bis +150 % pro Jahr.
    const val MIN_WACHSTUM = -50.0
    const val MAX_WACHSTUM = 150.0

    // Ergebnis: Wachstum in Prozent pro Jahr; ausserhalb gibt an, dass die Loesung am Rand des Suchbereichs liegt.
    data class Ergebnis(val wachstum: Double, val ausserhalb: Boolean)

    // Wert heute, wenn der Gewinn je Aktie (1) mit wachstum Prozent waechst: Kurs in jahre Jahren, abgezinst.
    fun wertHeute(wachstum: Double, jahre: Int, zielKgv: Double, rendite: Double): Double =
        (1.0 + wachstum / 100.0).pow(jahre) * zielKgv / (1.0 + rendite / 100.0).pow(jahre)

    // Im Kurs steckendes Wachstum fuer ein heutiges KGV kgv. null bei negativem oder fehlendem Gewinn (KGV nicht
    // positiv) und bei ungueltigen Annahmen.
    fun wachstum(kgv: Double, rendite: Double, zielKgv: Double, jahre: Int = 10): Ergebnis? {
        if (!(kgv > 0) || !kgv.isFinite() || !(zielKgv > 0) || jahre <= 0 || rendite <= -100.0) return null
        var lo = MIN_WACHSTUM
        var hi = MAX_WACHSTUM
        if (wertHeute(lo, jahre, zielKgv, rendite) >= kgv) return Ergebnis(lo, true)
        if (wertHeute(hi, jahre, zielKgv, rendite) <= kgv) return Ergebnis(hi, true)
        repeat(100) {
            val mid = (lo + hi) / 2
            if (wertHeute(mid, jahre, zielKgv, rendite) < kgv) lo = mid else hi = mid
        }
        return Ergebnis((lo + hi) / 2, false)
    }

    // Durchschnittliches Wachstum pro Jahr (Prozent) aus einer Reihe von Jahresgewinnen, aelteste zuerst. null, wenn
    // Anfang oder Ende nicht positiv sind oder weniger als zwei Jahre vorliegen.
    fun bisher(gewinne: List<Double>): Double? {
        if (gewinne.size < 2) return null
        val a = gewinne.first()
        val b = gewinne.last()
        if (!(a > 0) || !(b > 0)) return null
        return ((b / a).pow(1.0 / (gewinne.size - 1)) - 1.0) * 100.0
    }

    // Mittleres KGV einer Branche aus den KGVs ihrer Werte (nur 3 bis 100, mindestens fuenf Werte); sonst null.
    fun branchenKgv(kgvs: List<Double>): Double? {
        val s = kgvs.filter { it in 3.0..100.0 }.sorted()
        if (s.size < 5) return null
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
