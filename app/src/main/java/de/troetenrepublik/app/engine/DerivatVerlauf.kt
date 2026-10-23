package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.isFactor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

// Kursverlauf eines Hebelprodukts aus der Historie des Basiswerts, auch fuer ausgeknockte Produkte und Faktoren.
object DerivatVerlauf {

    // Knock-out oder Optionsschein. Bei einem ausgeknockten Produkt endet die Linie mit einem Punkt zum Knock-out
    // mit dem Restwert, spaetere Kurse fallen weg.
    fun bewertet(d: Derivative, candles: List<Candle>, fx: Double, rate: Double): List<Candle> {
        if (!d.isKnockedOut) return candles.map { c -> punkt(c.t, Pricing.price(d, c.close, c.t, fx, rate)) }
        val lebend = d.copy(knockedOutAt = 0L)
        val davor = candles.filter { it.t < d.knockedOutAt }.map { c -> punkt(c.t, Pricing.price(lebend, c.close, c.t, fx, rate)) }
        if (davor.isEmpty()) return emptyList()
        return davor + punkt(d.knockedOutAt, Pricing.KO_RESIDUAL)
    }

    // Faktor-Zertifikat, rueckwaerts vom heutigen Preis gerechnet. Der Faktor wirkt auf die Rendite des Basiswerts
    // seit dem Vortagesschluss, bei jedem Tageswechsel gehen die Finanzierungskosten wie in Pricing.rollFactor ab.
    // Ein Tagesverlust von 85 % oder mehr zaehlt als Reset mit 15 % Restwert. Fehlt vortagesschluss, beginnt der
    // erste Tag an seinem ersten Kurs. Bei Tages- und Wochenkerzen zaehlt jede Kerze als ein Tag.
    fun faktor(d: Derivative, candles: List<Candle>, heute: Double, zone: ZoneId, vortagesschluss: Double = 0.0, rate: Double = Pricing.RATE): List<Candle> {
        if (!d.kind.isFactor || candles.isEmpty() || d.factor == 0.0) return emptyList()
        val f = d.factor
        val kosten = 1.0 - abs(f) * rate / 365.0
        fun tag(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val tage = ArrayList<List<Candle>>()
        var aktuell = ArrayList<Candle>()
        var aktuellerTag: LocalDate? = null
        for (c in candles) {
            val t = tag(c.t)
            if (aktuellerTag != null && t != aktuellerTag) { tage += aktuell; aktuell = ArrayList() }
            aktuellerTag = t
            aktuell += c
        }
        tage += aktuell
        // Bezug ist der Schluss des Vortags, fuer den ersten Tag vortagesschluss.
        val bezug = DoubleArray(tage.size) { k ->
            if (k > 0) tage[k - 1].last().close else if (vortagesschluss > 0) vortagesschluss else tage[0].first().close
        }
        fun hebel(kurs: Double, ref: Double): Double = max(RESET_REST, 1.0 + f * (kurs / ref - 1.0))
        // Stand am Ende jedes Tages: V_end(k) = V_end(k-1) * Kosten * Hebel(Schluss_k)
        val ende = DoubleArray(tage.size)
        val letzterTag = tage.lastIndex
        val startWert = heute.takeIf { it > 0 } ?: 1.0
        // Startwert so, dass der letzte Kurs genau den heutigen Preis ergibt.
        var vorher = startWert / ((if (letzterTag > 0) kosten else 1.0) * hebel(tage[letzterTag].last().close, bezug[letzterTag]))
        ende[letzterTag] = startWert
        for (k in letzterTag - 1 downTo 0) {
            ende[k] = vorher
            vorher = if (k > 0) vorher / (kosten * hebel(tage[k].last().close, bezug[k])) else vorher
        }
        val out = ArrayList<Candle>(candles.size)
        for (k in tage.indices) {
            val basis = (if (k == 0) vorher else ende[k - 1]) * (if (k > 0) kosten else 1.0)
            for (c in tage[k]) out += punkt(c.t, max(Pricing.MIN_PRICE, basis * hebel(c.close, bezug[k])))
        }
        return out
    }

    // Restwert nach einem Reset, Schwelle wie in Pricing.rollFactor.
    private const val RESET_REST = 0.15

    private fun punkt(t: Long, p: Double) = Candle(t, p, p, p, p)
}
