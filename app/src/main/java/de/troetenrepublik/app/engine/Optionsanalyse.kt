package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.log10

// Optionsanalyse aus der frei abrufbaren Optionskette, Stand letzter Handelstag, offene Kontrakte vom Vortag.
// Ohne Orderbuch laesst sich nur schaetzen, ob gekauft oder verkauft wurde, am letzten Kurs nahe Brief oder Geld.
object Optionsanalyse {

    // verfall in Sekunden wie bei Yahoo, iv als Anteil (0,38 = 38 %).
    data class Kontrakt(
        val call: Boolean,
        val strike: Double,
        val verfall: Long,
        val letzter: Double,
        val geld: Double,
        val brief: Double,
        val umsatz: Long,
        val offen: Long,
        val iv: Double,
    ) {
        val mitte: Double get() = if (geld > 0 && brief > 0) (geld + brief) / 2 else letzter
    }

    enum class Seite { KAUF, VERKAUF, UNKLAR }

    data class Auffaellig(val kontrakt: Kontrakt, val praemie: Double, val verhaeltnis: Double, val seite: Seite, val punkte: Double)

    data class Ergebnis(
        val pcrUmsatz: Double?,
        val pcrOffen: Double?,
        val maxPain: Double?,
        val maxPainVerfall: Long?,
        val bewegung: Double?,
        val bewegungVerfall: Long?,
        val ivAmGeld: Double?,
        val auffaellig: List<Auffaellig>,
    )

    private const val TAG_S = 86_400L

    // Max Pain: Basispreis, bei dem der innere Wert aller Calls und Puts zusammen am kleinsten ist.
    fun maxPain(kontrakte: List<Kontrakt>): Double? {
        val strikes = kontrakte.map { it.strike }.distinct().sorted()
        if (strikes.size < 3 || kontrakte.all { it.offen == 0L }) return null
        return strikes.minByOrNull { s ->
            kontrakte.sumOf { k -> k.offen * if (k.call) maxOf(0.0, s - k.strike) else maxOf(0.0, k.strike - s) }
        }
    }

    // Letzter Kurs am Brief heisst gekauft, am Geld verkauft, sonst unklar.
    fun seite(k: Kontrakt): Seite {
        if (k.geld <= 0 || k.brief <= 0 || k.brief < k.geld) return Seite.UNKLAR
        val spanne = k.brief - k.geld
        return when {
            k.letzter >= k.brief - 0.1 * spanne -> Seite.KAUF
            k.letzter <= k.geld + 0.1 * spanne -> Seite.VERKAUF
            else -> Seite.UNKLAR
        }
    }

    fun auswerten(kurs: Double, kontrakte: List<Kontrakt>, jetztS: Long = System.currentTimeMillis() / 1000): Ergebnis {
        val gueltig = kontrakte.filter { it.verfall > jetztS - TAG_S }
        val calls = gueltig.filter { it.call }
        val puts = gueltig.filter { !it.call }
        val cu = calls.sumOf { it.umsatz }
        val pu = puts.sumOf { it.umsatz }
        val co = calls.sumOf { it.offen }
        val po = puts.sumOf { it.offen }
        val termine = gueltig.map { it.verfall }.distinct().sorted()
        val naechster = termine.firstOrNull { it - jetztS >= 2 * TAG_S } ?: termine.firstOrNull()
        val amNaechsten = gueltig.filter { it.verfall == naechster }
        // Straddle am Geld: Call und Put mit dem Basispreis am naechsten zum Kurs.
        val atm = amNaechsten.map { it.strike }.distinct().minByOrNull { abs(it - kurs) }
        val bewegung = atm?.let { s ->
            val c = amNaechsten.firstOrNull { it.call && it.strike == s }?.mitte
            val p = amNaechsten.firstOrNull { !it.call && it.strike == s }?.mitte
            if (c != null && p != null && c > 0 && p > 0) c + p else null
        }
        val um30 = termine.minByOrNull { abs((it - jetztS) / TAG_S.toDouble() - 30) }
        val iv = gueltig.filter { it.verfall == um30 }.let { l ->
            val s = l.map { it.strike }.distinct().minByOrNull { abs(it - kurs) }
            l.filter { it.strike == s && it.iv in 0.01..5.0 }.map { it.iv }.takeIf { it.isNotEmpty() }?.average()
        }
        // Auffaellig: viel Umsatz gegen die offenen Kontrakte, grosses Praemienvolumen, Verfall in 60 Tagen.
        val umsaetze = gueltig.map { it.umsatz }.filter { it > 0 }.sorted()
        val median = if (umsaetze.isEmpty()) 0L else umsaetze[umsaetze.size / 2]
        val schwelle = maxOf(200L, minOf(500L, median * 5))
        val auffaellig = gueltig.filter { it.umsatz >= schwelle && it.verfall - jetztS <= 60 * TAG_S }.mapNotNull { k ->
            val praemie = k.umsatz * k.mitte * 100
            val r = k.umsatz.toDouble() / maxOf(k.offen, 1L)
            if (r < 1.0 || praemie < 50_000) return@mapNotNull null
            val abstand = if (kurs > 0) abs(k.strike / kurs - 1) else 0.0
            Auffaellig(k, praemie, r, seite(k), log10(praemie) * minOf(r, 10.0) * (1 + abstand))
        }.sortedByDescending { it.punkte }.take(3)
        return Ergebnis(
            pcrUmsatz = if (cu > 0) pu.toDouble() / cu else null,
            pcrOffen = if (co > 0) po.toDouble() / co else null,
            maxPain = maxPain(amNaechsten),
            maxPainVerfall = naechster,
            bewegung = bewegung,
            bewegungVerfall = naechster,
            ivAmGeld = iv,
            auffaellig = auffaellig,
        )
    }
}
