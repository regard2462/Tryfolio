package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// Rendite des ganzen Depots wie in Portfolio Performance: Wertpapiere und Cash zusammen, nach Gebuehren und Steuern.
// Geldfluesse von aussen sind nur Ein- und Auszahlungen. Zufluesse zaehlen am Anfang eines Abschnitts, Abfluesse am Ende:
// Rendite = (Wert am Ende + Abfluesse) / (Wert am Anfang + Zufluesse) - 1.
object Performanceanalyse {

    // Kapitalbilanz in Euro. Kursgewinne realisiert und nicht realisiert zusammen.
    data class Bilanz(
        val anfang: Double,
        val einlagen: Double,
        val entnahmen: Double,
        val kursgewinne: Double,
        // Davon realisiert laut den Verkaeufen im Zeitraum, vor Steuern.
        val realisiert: Double,
        val dividenden: Double,
        val zinsen: Double,
        val gebuehren: Double,
        val steuern: Double,
        val ende: Double,
    ) {
        // Wertzuwachs ohne Ein- und Auszahlungen.
        val absolut: Double get() = ende - anfang - einlagen + entnahmen
    }

    data class Ergebnis(
        val von: Long,
        val bis: Long,
        // Zeitgewichtete Rendite (TTWROR) als Anteil, 0,12 = 12 %.
        val ttwror: Double,
        // Pro Jahr, null bei weniger als einem Jahr.
        val ttwrorJahr: Double?,
        // Interner Zinsfuss pro Jahr als Anteil, null ohne eindeutige Loesung.
        val izf: Double?,
        // Groesster Rueckgang der zeitgewichteten Kurve als positiver Anteil.
        val maxRueckgang: Double,
        // Laengste Zeit in Tagen von einem Hoch bis zu dem Tag, an dem es wieder erreicht wurde (oder bis heute).
        val rueckgangTage: Long,
        // Hoechster Stand der Kurve (Prozent wie kurve) und wann.
        val hoch: Pair<Long, Double>,
        // Rendite des letzten Abschnitts, meist des letzten Handelstags; null ohne Abschnitt.
        val letzterTag: Double?,
        // Schwankung pro Jahr als Anteil, null bei zu wenigen Punkten.
        val volatilitaet: Double?,
        // Nur die Schwankung nach unten (Semivolatilitaet) pro Jahr, wie in Portfolio Performance.
        val semivolatilitaet: Double?,
        // Nur die Wertpapiere zu Kurswerten, ohne Kosten und Dividenden wie die Depotkurve.
        val nurWertpapiere: Double?,
        val bilanz: Bilanz,
        val monate: Map<YearMonth, Double>,
        val jahre: Map<Int, Double>,
        // Zeitgewichtete Kurve in Prozent, beginnt bei 0.
        val kurve: List<Pair<Long, Double>>,
    )

    private const val TAG = 86_400_000L
    private const val JAHR = 365.25 * TAG

    // Geldfluss von aussen: Ein- und Auszahlungen, Wetten, ohne Geld uebernommene Positionen zum Einstand.
    fun vonAussen(t: Transaction): Double = when (t.type) {
        TxType.DEPOSIT, TxType.WITHDRAWAL, TxType.BET -> t.amount
        TxType.BUY -> if (t.qty > 0 && t.tag != "EXCHANGE_IN" && (t.tag == "TRANSFER_IN" || t.amount == 0.0)) t.qty * t.price else 0.0
        else -> 0.0
    }

    // wertpapiere: Wert der Wertpapiere je Zeitpunkt, aufsteigend, in Euro, der letzte Punkt moeglichst jetzt.
    // cashJetzt ist das Guthaben heute, aeltere Staende folgen aus den Buchungen danach.
    // null bei weniger als zwei Punkten im Zeitraum.
    fun berechnen(
        wertpapiere: List<Pair<Long, Double>>,
        buchungen: List<Transaction>,
        cashJetzt: Double,
        von: Long,
        bis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Ergebnis? {
        val reihe = wertpapiere.filter { it.second.isFinite() }.sortedBy { it.first }
        if (reihe.isEmpty()) return null
        val start = maxOf(von, reihe.first().first)
        val raster = buildList {
            add(start)
            reihe.forEach { (t, _) -> if (t > start && t <= bis) add(t) }
            if (last() < bis) add(bis)
        }.distinct()
        if (raster.size < 2) return null

        val txs = buchungen.sortedBy { it.ts }
        val zeiten = LongArray(txs.size) { txs[it].ts }
        val summe = DoubleArray(txs.size + 1).also { s -> txs.forEachIndexed { i, t -> s[i + 1] = s[i] + t.amount } }
        // Guthaben zum Zeitpunkt t: heute abzueglich aller Betraege danach.
        fun cash(t: Long): Double = cashJetzt - (summe[txs.size] - summe[bisEinschliesslich(zeiten, t)])
        fun wp(t: Long): Double {
            var lo = 0
            var hi = reihe.size - 1
            if (reihe[0].first > t) return 0.0
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (reihe[mid].first <= t) lo = mid else hi = mid - 1
            }
            return reihe[lo].second
        }

        val werte = raster.map { wp(it) + cash(it) }
        val renditen = DoubleArray(raster.size - 1)
        val nurWp = DoubleArray(raster.size - 1)
        var j = bisEinschliesslich(zeiten, start)
        for (i in 1 until raster.size) {
            var zu = 0.0
            var ab = 0.0
            var wpZu = 0.0
            var wpAb = 0.0
            while (j < txs.size && txs[j].ts <= raster[i]) {
                val t = txs[j]
                val f = vonAussen(t)
                if (f > 0) zu += f else ab -= f
                val g = Depotzerlegung.fluss(t)
                if (g > 0) wpZu += g else wpAb -= g
                j++
            }
            renditen[i - 1] = rendite(werte[i - 1], werte[i], zu, ab)
            nurWp[i - 1] = rendite(wp(raster[i - 1]), wp(raster[i]), wpZu, wpAb)
        }

        val kurve = ArrayList<Pair<Long, Double>>(raster.size)
        var index = 1.0
        var hoch = 1.0
        var hochZeit = raster[0]
        var rueckgang = 0.0
        var dauer = 0L
        kurve += raster[0] to 0.0
        renditen.forEachIndexed { i, r ->
            index *= 1 + r
            val t = raster[i + 1]
            // Ein neues Hoch beendet die Zeit unter dem alten
            if (index >= hoch) { dauer = maxOf(dauer, t - hochZeit); hoch = index; hochZeit = t }
            if (hoch > 0) rueckgang = maxOf(rueckgang, 1 - index / hoch)
            kurve += t to (index - 1) * 100
        }
        dauer = maxOf(dauer, raster.last() - hochZeit)
        val hochPunkt = kurve.maxByOrNull { it.second } ?: kurve.first()
        val ttwror = index - 1
        val jahre = (raster.last() - raster.first()) / JAHR
        val ttwrorJahr = if (jahre >= 1.0 && index > 0) index.pow(1 / jahre) - 1 else null
        val volatilitaet = if (renditen.size >= 10 && jahre > 0) {
            val l = renditen.filter { it > -1 }.map { ln(1 + it) }
            val m = l.average()
            val varianz = l.sumOf { (it - m) * (it - m) } / (l.size - 1).coerceAtLeast(1)
            sqrt(varianz * (renditen.size / jahre))
        } else null
        // Abweichungen unter dem Mittel, geteilt durch alle Abschnitte
        val semivolatilitaet = if (renditen.size >= 10 && jahre > 0) {
            val l = renditen.filter { it > -1 }.map { ln(1 + it) }
            val m = l.average()
            sqrt(l.sumOf { if (it < m) (it - m) * (it - m) else 0.0 } / l.size * (renditen.size / jahre))
        } else null
        val nurWertpapiere = nurWp.fold(1.0) { a, r -> a * (1 + r) } - 1

        // Anfangswert als Einlage am Anfang, dann die Fluesse von aussen, der Endwert als Rest.
        val fluesse = buildList {
            if (werte.first() != 0.0) add(PortfolioTools.Flow(raster.first(), werte.first(), ""))
            txs.filter { it.ts > raster.first() && it.ts <= raster.last() }.forEach { t ->
                vonAussen(t).takeIf { it != 0.0 }?.let { add(PortfolioTools.Flow(t.ts, it, "")) }
            }
        }
        val izf = if (fluesse.any { it.amount > 0 }) PortfolioTools.moneyWeighted(fluesse, werte.last(), raster.last()) else null

        // Monate und Jahre nach dem Monat, in dem ein Abschnitt endet.
        val monate = LinkedHashMap<YearMonth, Double>()
        val jahresIndex = LinkedHashMap<Int, Double>()
        renditen.forEachIndexed { i, r ->
            val ym = YearMonth.from(Instant.ofEpochMilli(raster[i + 1]).atZone(zone))
            monate[ym] = ((1 + (monate[ym] ?: 0.0)) * (1 + r)) - 1
            jahresIndex[ym.year] = ((1 + (jahresIndex[ym.year] ?: 0.0)) * (1 + r)) - 1
        }

        return Ergebnis(
            von = raster.first(), bis = raster.last(), ttwror = ttwror, ttwrorJahr = ttwrorJahr, izf = izf,
            maxRueckgang = rueckgang, rueckgangTage = dauer / TAG, hoch = hochPunkt, letzterTag = renditen.lastOrNull(),
            volatilitaet = volatilitaet, semivolatilitaet = semivolatilitaet, nurWertpapiere = nurWertpapiere,
            bilanz = bilanz(txs, raster.first(), raster.last(), werte.first(), werte.last()),
            monate = monate, jahre = jahresIndex, kurve = kurve,
        )
    }

    // 0, wenn zu Beginn nichts im Depot lag.
    private fun rendite(vorher: Double, nachher: Double, zu: Double, ab: Double): Double {
        val basis = vorher + zu
        if (basis <= 1e-6) return 0.0
        return ((nachher + ab) / basis - 1).takeIf { it.isFinite() } ?: 0.0
    }

    // Zahl der Buchungen bis einschliesslich t, also Index der ersten danach.
    private fun bisEinschliesslich(zeiten: LongArray, t: Long): Int {
        var lo = 0
        var hi = zeiten.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (zeiten[mid] <= t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    // Buchungen nach von bis einschliesslich bis. Die Kursgewinne ergeben sich als Rest.
    fun bilanz(txs: List<Transaction>, von: Long, bis: Long, anfang: Double, ende: Double): Bilanz {
        var einlagen = 0.0
        var entnahmen = 0.0
        var realisiert = 0.0
        var dividenden = 0.0
        var zinsen = 0.0
        var gebuehren = 0.0
        var steuern = 0.0
        for (t in txs) {
            if (t.ts <= von || t.ts > bis) continue
            val f = vonAussen(t)
            if (f > 0) einlagen += f else entnahmen -= f
            when (t.type) {
                TxType.DIVIDEND -> { dividenden += t.amount + t.tax; steuern += t.tax }
                TxType.INTEREST -> { zinsen += t.amount + t.tax; steuern += t.tax }
                TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY -> { realisiert += t.realized; steuern += t.tax }
                TxType.TAX -> steuern -= t.amount
                TxType.FEE -> gebuehren -= t.amount
                else -> Unit
            }
        }
        val kursgewinne = ende - anfang - einlagen + entnahmen - dividenden - zinsen + gebuehren + steuern
        return Bilanz(anfang, einlagen, entnahmen, kursgewinne, realisiert, dividenden, zinsen, gebuehren, steuern, ende)
    }
}
