package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.ln

// KGV auf den Durchschnittsgewinn (0.77): Kurs geteilt durch den mittleren Gewinn je Aktie der letzten bis zu fuenf
// Geschaeftsjahre. Ein einzelnes Rekord- oder Verlustjahr verzerrt das normale KGV, vor allem bei Zyklikern; der Schnitt
// glaettet das. Splits werden je Jahr an der Aktienzahl erkannt: Yahoo rechnet den Gewinn je Aktie meist selbst um, wo
// nicht, kommt der Splitfaktor dazu.
object GewinnSchnitt {
    const val MAX_JAHRE = 5
    const val MIN_JAHRE = 3

    // Ab dieser Abweichung des letzten Jahresgewinns vom Schnitt (Prozent) steht ein Hinweis. Bei +50 % liegt das KGV auf
    // den Schnitt um die Haelfte ueber dem normalen KGV.
    const val SCHWELLE = 50.0

    // Kleinere Splitverhaeltnisse gehen in der Streuung der Aktienzahl unter und werden nicht erkannt
    private const val SPLIT_MIN = 1.35

    // Abstand zweier Geschaeftsjahre in Tagen, ab dem die Reihe als unterbrochen gilt (52 und 53 Wochen eingerechnet)
    private const val LUECKE_TAGE = 400L

    private const val TAG_MS = 86_400_000L

    enum class Lage { GLEICH, UEBER, UNTER }

    class Ergebnis(
        val kgv: Double,
        val schnitt: Double,
        val jahre: Int,
        val letztesJahr: Int,
        val letzter: Double,
        // Letzter Jahresgewinn gegen den Schnitt in Prozent
        val abweichung: Double,
        val lage: Lage,
        // Jedes Jahr der Reihe lag ueber dem Vorjahr: stetiges Wachstum, keine Spitze nach einem Rueckgang
        val stetig: Boolean = false,
    )

    class Gewinn(val tag: Long, val jahr: Int, val wert: Double)

    // Gewinn je Aktie eines Geschaeftsjahrs; ohne die Angabe Nettogewinn durch Aktien im Umlauf
    fun gewinnJeAktie(j: Qualitaet.Jahr): Double? {
        j.gewinnJeAktie?.takeIf { it.isFinite() }?.let { return it }
        val n = j.nettogewinn ?: return null
        val a = j.aktien?.takeIf { it > 0 } ?: return null
        return (n / a).takeIf { it.isFinite() }
    }

    // Gewinn je Aktie je Jahr in heutigen Aktien. aktienJetzt: Aktien im Umlauf heute (0 oder null, wenn unbekannt; dann gilt
    // die Zahl des letzten Jahres).
    fun reihe(jahre: List<Qualitaet.Jahr>, splits: List<Splits.Split>, aktienJetzt: Double?): List<Gewinn> {
        val mitGewinn = jahre.sortedBy { it.ende }.mapNotNull { j ->
            val tag = Historie078.datum(j.ende) ?: return@mapNotNull null
            val g = gewinnJeAktie(j) ?: return@mapNotNull null
            Triple(j, tag, g)
        }
        val bezug = aktienJetzt?.takeIf { it > 0 } ?: mitGewinn.lastOrNull()?.first?.aktien?.takeIf { it > 0 }
        return mitGewinn.map { (j, tag, g) ->
            val r = splits.filter { it.t / TAG_MS > tag && it.verhaeltnis > 0 }.fold(1.0) { f, s -> f * s.verhaeltnis }
            val a = j.aktien?.takeIf { it > 0 }
            val unbereinigt = bezug != null && a != null && r.isFinite() && (r >= SPLIT_MIN || r <= 1 / SPLIT_MIN) &&
                abs(ln(bezug / (a * r))) < abs(ln(bezug / a))
            Gewinn(tag, j.ende.take(4).toIntOrNull() ?: 0, if (unbereinigt) g / r else g)
        }
    }

    // Ob die Aktienzahl eines der letzten Jahre so weit von der heutigen abweicht, dass ein Split dahinterstecken kann. Nur
    // dann braucht die Rechnung die Splitliste.
    fun splitVerdacht(jahre: List<Qualitaet.Jahr>, aktienJetzt: Double?): Boolean {
        val bezug = aktienJetzt?.takeIf { it > 0 } ?: jahre.lastOrNull()?.aktien?.takeIf { it > 0 } ?: return false
        return jahre.takeLast(MAX_JAHRE + 1).any { j ->
            val a = j.aktien?.takeIf { it > 0 } ?: return@any false
            maxOf(a, bezug) / minOf(a, bezug) >= SPLIT_MIN
        }
    }

    // Letzte zusammenhaengende Jahre (hoechstens MAX_JAHRE), aelteste zuerst
    private fun letzte(r: List<Gewinn>): List<Gewinn> {
        val aus = ArrayList<Gewinn>()
        for (g in r.asReversed()) {
            if (aus.isNotEmpty() && aus.last().tag - g.tag > LUECKE_TAGE) break
            aus += g
            if (aus.size == MAX_JAHRE) break
        }
        return aus.asReversed()
    }

    // kurs in der Berichtswaehrung der Gewinne. null mit weniger als MIN_JAHRE Jahren, bei nicht positivem Schnitt oder
    // unbrauchbarem Kurs.
    fun berechne(jahre: List<Qualitaet.Jahr>, splits: List<Splits.Split>, aktienJetzt: Double?, kurs: Double): Ergebnis? {
        if (!kurs.isFinite() || kurs <= 0) return null
        val r = letzte(reihe(jahre, splits, aktienJetzt))
        if (r.size < MIN_JAHRE) return null
        val schnitt = r.sumOf { it.wert } / r.size
        if (!schnitt.isFinite() || schnitt <= 0) return null
        val kgv = kurs / schnitt
        if (!kgv.isFinite() || kgv >= 1000) return null
        val l = r.last()
        val abw = (l.wert / schnitt - 1.0) * 100.0
        val lage = when {
            l.wert <= 0 -> Lage.GLEICH
            abw >= SCHWELLE -> Lage.UEBER
            abw <= -SCHWELLE -> Lage.UNTER
            else -> Lage.GLEICH
        }
        return Ergebnis(kgv, schnitt, r.size, l.jahr, l.wert, abw, lage, r.zipWithNext().all { (a, b) -> b.wert > a.wert })
    }
}
