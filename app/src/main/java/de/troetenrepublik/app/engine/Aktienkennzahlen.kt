package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// Rendite und Risiko eines Werts aus Tagesschlusskursen, ohne Dividenden. Beta und Korrelation zum Index aus Wochenkursen
// der letzten fuenf Jahre, weil Boersen verschiedener Laender an unterschiedlichen Tagen und Uhrzeiten schliessen;
// mit Tageskursen faellt die Korrelation zu klein aus.
object Aktienkennzahlen {

    data class Ergebnis(
        // Rendite pro Jahr ueber 1, 5 und 10 Jahre; null, wenn die Kurse nicht so weit zurueckreichen.
        val rendite1: Double?,
        val rendite5: Double?,
        val rendite10: Double?,
        // Schwankung pro Jahr aus den Tagesrenditen des letzten Jahres.
        val schwankung: Double?,
        // Groesster Rueckgang vom Hoch (negativ) und der Tag seines Tiefs.
        val maxRueckgang: Double,
        val maxRueckgangTag: Long,
        // Laengste Zeit unter einem frueheren Hoch in Tagen; laufend, wenn sie noch andauert.
        val laengsterRueckgangTage: Long,
        val laufend: Boolean,
        // Beginn der Kurse, auf die sich die Rueckgaenge beziehen.
        val ab: Long,
        val beta: Double?,
        val korrelation: Double?,
    )

    private const val TAG = 86_400_000L
    private const val JAHR = 365.25 * TAG

    // Kennzahlen aus kurse (aelteste zuerst) und optional den Kursen des Index index; null bei unter 60 Kursen.
    fun berechnen(kurse: List<Candle>, index: List<Candle>? = null): Ergebnis? {
        val k = kurse.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        if (k.size < 60) return null
        val ende = k.last()

        fun renditeProJahr(jahre: Int): Double? {
            val ziel = ende.t - (jahre * JAHR).toLong()
            // Die Kurse muessen fast so weit zurueckreichen (zwei Wochen Spielraum)
            if (k.first().t > ziel + 14 * TAG) return null
            val start = k.lastOrNull { it.t <= ziel } ?: k.first()
            val dauer = (ende.t - start.t) / JAHR
            if (dauer <= 0) return null
            return (ende.close / start.close).pow(1 / dauer) - 1
        }

        val letztesJahr = k.filter { it.t >= ende.t - JAHR.toLong() }
        val renditen = letztesJahr.zipWithNext().map { (a, b) -> ln(b.close / a.close) }
        val schwankung = if (renditen.size >= 40) {
            val m = renditen.average()
            sqrt(renditen.sumOf { (it - m) * (it - m) } / (renditen.size - 1)) * sqrt(renditen.size / ((letztesJahr.last().t - letztesJahr.first().t) / JAHR))
        } else null

        var hoch = k.first().close
        var hochZeit = k.first().t
        var max = 0.0
        var maxTag = k.first().t
        var laengste = 0L
        // Zeit unter Wasser: vom Hoch bis zu dem Tag, an dem es wieder erreicht ist
        var unter = false
        for (c in k) {
            if (c.close >= hoch) {
                if (unter) laengste = maxOf(laengste, (c.t - hochZeit) / TAG)
                unter = false
                hoch = c.close
                hochZeit = c.t
            } else unter = true
            val dd = c.close / hoch - 1
            if (dd < max) { max = dd; maxTag = c.t }
        }
        val offen = if (unter) (ende.t - hochZeit) / TAG else 0L
        val laufend = unter && offen >= laengste
        laengste = maxOf(laengste, offen)

        val vergleich = index?.let { i ->
            val ab = ende.t - (5 * JAHR).toLong()
            Risiko.vergleich(woechentlich(k.filter { it.t >= ab }), woechentlich(i.filter { it.t >= ab - 14 * TAG }))
        }
        return Ergebnis(
            renditeProJahr(1), renditeProJahr(5), renditeProJahr(10), schwankung,
            max, maxTag, laengste, laufend, k.first().t, vergleich?.beta, vergleich?.korrelation,
        )
    }

    // Letzter Kurs jeder Kalenderwoche (Wochen ab Montag, UTC).
    fun woechentlich(kurse: List<Candle>): List<Pair<Long, Double>> =
        kurse.filter { it.close > 0 }.groupBy { Math.floorDiv(it.t + 3 * TAG, 7 * TAG) }.toSortedMap().values.map { w -> w.last().let { it.t to it.close } }

    // Passender Vergleichsindex nach Land (Angabe von Yahoo, englisch) oder Endung des Kuerzels; sonst der MSCI World
    // (ueber den iShares-ETF URTH). Kryptowerte und Indizes bekommen keinen.
    fun index(symbol: String, land: String?): Pair<String, String>? {
        if (symbol.startsWith("^") || symbol.endsWith("-USD") || symbol.endsWith("-EUR")) return null
        val nachLand = when (land?.lowercase()) {
            "united states" -> "US"; "germany" -> "DE"; "united kingdom" -> "GB"; "france" -> "FR"; "switzerland" -> "CH"
            "netherlands" -> "NL"; "italy" -> "IT"; "spain" -> "ES"; "japan" -> "JP"; "hong kong" -> "HK"; "canada" -> "CA"
            "sweden" -> "SE"; "denmark" -> "DK"; "finland" -> "FI"; "norway" -> "NO"; "austria" -> "AT"; "china" -> "CN"
            "australia" -> "AU"; "south korea" -> "KR"; "india" -> "IN"; "taiwan" -> "TW"; "brazil" -> "BR"
            else -> null
        }
        val nachEndung = when (symbol.substringAfterLast('.', "").uppercase()) {
            "" -> "US"; "DE", "F", "SG", "MU", "BE", "DU", "HM", "HA" -> "DE"; "L" -> "GB"; "PA" -> "FR"; "SW" -> "CH"; "AS" -> "NL"
            "MI" -> "IT"; "MC" -> "ES"; "T" -> "JP"; "HK" -> "HK"; "TO" -> "CA"; "ST" -> "SE"; "CO" -> "DK"; "HE" -> "FI"
            "OL" -> "NO"; "VI" -> "AT"; "SS", "SZ" -> "CN"; "AX" -> "AU"; "KS" -> "KR"; "NS", "BO" -> "IN"; "TW" -> "TW"; "SA" -> "BR"
            else -> null
        }
        return when (nachLand ?: nachEndung) {
            "US" -> "^GSPC" to "S&P 500"; "DE" -> "^GDAXI" to "DAX"; "GB" -> "^FTSE" to "FTSE 100"; "FR" -> "^FCHI" to "CAC 40"
            "CH" -> "^SSMI" to "SMI"; "NL" -> "^AEX" to "AEX"; "IT" -> "FTSEMIB.MI" to "FTSE MIB"; "ES" -> "^IBEX" to "IBEX 35"
            "JP" -> "^N225" to "Nikkei 225"; "HK" -> "^HSI" to "Hang Seng"; "CA" -> "^GSPTSE" to "S&P/TSX"; "SE" -> "^OMX" to "OMX Stockholm 30"
            "DK" -> "^OMXC25" to "OMX Copenhagen 25"; "FI" -> "^OMXH25" to "OMX Helsinki 25"; "NO" -> "OBX.OL" to "OBX"; "AT" -> "^ATX" to "ATX"
            "CN" -> "000001.SS" to "SSE Composite"; "AU" -> "^AXJO" to "S&P/ASX 200"; "KR" -> "^KS11" to "KOSPI"; "IN" -> "^NSEI" to "Nifty 50"
            "TW" -> "^TWII" to "TAIEX"; "BR" -> "^BVSP" to "Ibovespa"
            else -> "URTH" to "MSCI World"
        }
    }
}
