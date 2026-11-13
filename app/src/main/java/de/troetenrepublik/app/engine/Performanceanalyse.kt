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
        // Ob izf pro Jahr gilt; unter einem Jahr ist er der Zinsfuss fuer den Zeitraum (nicht hochgerechnet, wie GIPS es verlangt).
        val izfProJahr: Boolean = true,
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
        // Nur die Schwankung nach unten (Semivolatilitaet) pro Jahr. Portfolio Performance zeigt sie fuer den ganzen Zeitraum.
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
    // Jahreslaenge wie in allen Renditerechnungen des Depots: 365,25 Tage.
    private const val JAHR = PortfolioTools.JAHR_MS.toDouble()

    // Geldfluss von aussen: Ein- und Auszahlungen, Wetten, ohne Geld uebernommene Positionen zum Einstand.
    fun vonAussen(t: Transaction): Double = when (t.type) {
        TxType.DEPOSIT, TxType.WITHDRAWAL, TxType.BET -> t.amount
        TxType.BUY -> if (t.qty > 0 && t.tag != "EXCHANGE_IN" && (t.tag == "TRANSFER_IN" || t.amount == 0.0)) t.qty * t.price else 0.0
        else -> 0.0
    }

    // Freies Fenster aus Von- und Bis-Tag (Tage seit 1970, wie im Zeitraumdialog); der Bestand am Von-Tag ist der Startwert.
    fun berechnenTage(
        wertpapiere: List<Pair<Long, Double>>,
        buchungen: List<Transaction>,
        cashJetzt: Double,
        vonTag: Long,
        bisTag: Long,
        jetzt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Ergebnis? {
        val (von, bis) = Zeitraum.grenzen(vonTag, bisTag, jetzt)
        return berechnen(wertpapiere, buchungen, cashJetzt, von, bis, zone)
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
        // Buchungen am Tag des ersten Kurspunkts und vor ihm zaehlen mit (0.77): Eroeffnung und uebernommene Positionen
        // sind dann Einlagen zum Einstand und stehen nicht als Anfangswert zum ersten Kurs da.
        val erster = reihe.first().first
        val davor = buchungen.asSequence().map { it.ts }.filter { it <= erster && it > erster - TAG }.minOrNull()
        val start = maxOf(von, if (davor != null) davor - 1 else erster)
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
        val ttwrorJahr = if (jahre >= 1.0 - 1e-3 && index > 0) index.pow(1 / jahre) - 1 else null
        // Fuer die Schwankung ab vier Wochen ein Wert je Kalendertag (UTC): Teilabschnitte desselben Tages werden
        // verkettet. Werte an mehreren Boersen ergeben sonst mehrere Abschnitte je Tag, und die Schwankung waere zu klein.
        val schwankRenditen: List<Double> = if (raster.last() - raster.first() < 28 * TAG) renditen.toList() else buildList {
            var tagNr = Long.MIN_VALUE
            var f = 1.0
            for (i in renditen.indices) {
                val d = Math.floorDiv(raster[i + 1], TAG)
                if (d != tagNr && tagNr != Long.MIN_VALUE) { add(f - 1); f = 1.0 }
                tagNr = d
                f *= 1 + renditen[i]
            }
            if (tagNr != Long.MIN_VALUE) add(f - 1)
        }
        val volatilitaet = if (schwankRenditen.size >= 10 && jahre > 0) {
            val l = schwankRenditen.filter { it > -1 }.map { ln(1 + it) }
            val m = l.average()
            val varianz = l.sumOf { (it - m) * (it - m) } / (l.size - 1).coerceAtLeast(1)
            sqrt(varianz * (schwankRenditen.size / jahre))
        } else null
        // Abweichungen unter dem Mittel, geteilt durch alle Abschnitte
        val semivolatilitaet = if (schwankRenditen.size >= 10 && jahre > 0) {
            val l = schwankRenditen.filter { it > -1 }.map { ln(1 + it) }
            val m = l.average()
            sqrt(l.sumOf { if (it < m) (it - m) * (it - m) else 0.0 } / (l.size - 1).coerceAtLeast(1) * (schwankRenditen.size / jahre))
        } else null
        val nurWertpapiere = nurWp.fold(1.0) { a, r -> a * (1 + r) } - 1

        // Anfangswert als Einlage am Anfang, dann die Fluesse von aussen, der Endwert als Rest.
        val fluesse = buildList {
            if (werte.first() != 0.0) add(PortfolioTools.Flow(raster.first(), werte.first(), ""))
            txs.filter { it.ts > raster.first() && it.ts <= raster.last() }.forEach { t ->
                vonAussen(t).takeIf { it != 0.0 }?.let { add(PortfolioTools.Flow(t.ts, it, "")) }
            }
        }
        val izfProJahr = jahre >= 1.0 - 1e-3
        val izf = if (fluesse.none { it.amount > 0 }) null
        else if (izfProJahr) PortfolioTools.moneyWeighted(fluesse, werte.last(), raster.last())
        else PortfolioTools.moneyWeighted(fluesse, werte.last(), raster.last(), (raster.last() - raster.first()).toDouble())

        // Monate und Jahre nach dem Monat, in dem ein Abschnitt endet.
        val monate = LinkedHashMap<YearMonth, Double>()
        val jahresIndex = LinkedHashMap<Int, Double>()
        renditen.forEachIndexed { i, r ->
            val ym = YearMonth.from(Instant.ofEpochMilli(raster[i + 1]).atZone(zone))
            monate[ym] = ((1 + (monate[ym] ?: 0.0)) * (1 + r)) - 1
            jahresIndex[ym.year] = ((1 + (jahresIndex[ym.year] ?: 0.0)) * (1 + r)) - 1
        }

        return Ergebnis(
            von = raster.first(), bis = raster.last(), ttwror = ttwror, ttwrorJahr = ttwrorJahr, izf = izf, izfProJahr = izfProJahr,
            maxRueckgang = rueckgang, rueckgangTage = dauer / TAG, hoch = hochPunkt, letzterTag = renditen.lastOrNull(),
            volatilitaet = volatilitaet, semivolatilitaet = semivolatilitaet, nurWertpapiere = nurWertpapiere,
            bilanz = bilanz(txs, raster.first(), raster.last(), werte.first(), werte.last()),
            monate = monate, jahre = jahresIndex, kurve = kurve,
        )
    }

    // Groesster Rueckgang des Gesamtvermoegens in Prozent (negativ, 0 ohne Rueckgang), zeitgewichtet: Ein- und
    // Auszahlungen (fluesse: Zeit, Betrag, Einzahlung positiv) verschieben die Kurve nicht, ein frueher Verlust bleibt auf
    // das damalige Kapital bezogen. verlauf: Gesamtvermoegen je Zeitpunkt, aufsteigend.
    fun maxRueckgangProzent(verlauf: List<Pair<Long, Double>>, fluesse: List<Pair<Long, Double>>): Double {
        val kurve = PortfolioTools.timeWeighted(verlauf.sortedBy { it.first }, fluesse)
        var hoch = Double.NEGATIVE_INFINITY
        var schlimmster = 0.0
        for ((_, pct) in kurve) {
            val index = 1.0 + pct / 100.0
            if (index > hoch) hoch = index
            if (hoch > 0) schlimmster = minOf(schlimmster, index / hoch - 1.0)
        }
        return schlimmster * 100.0
    }

    // Zeit unter dem Hoechststand und mittlerer Rueckgang, beide nach Zeit gewichtet.
    // anteilUnterHoch: Anteil der Zeit (0 bis 1), in der die Kurve unter ihrem bisherigen Hoechststand lag.
    // mittlerer: mittlerer Abstand zum bisherigen Hoechststand ueber den ganzen Zeitraum als positiver Anteil.
    data class Rueckgaenge(val anteilUnterHoch: Double, val mittlerer: Double)

    // index: Zeit und Stand der Kurve (etwa 1,0 am Anfang), aufsteigend. Jeder Abschnitt zaehlt mit seiner Dauer und dem
    // Stand an seinem Ende. null bei weniger als zwei Punkten oder ohne Dauer.
    fun rueckgaenge(index: List<Pair<Long, Double>>): Rueckgaenge? {
        val punkte = index.filter { it.second.isFinite() && it.second > 0 }
        if (punkte.size < 2) return null
        val gesamt = (punkte.last().first - punkte.first().first).toDouble()
        if (gesamt <= 0) return null
        var hoch = punkte.first().second
        var unter = 0.0
        var summe = 0.0
        for (i in 1 until punkte.size) {
            val dauer = (punkte[i].first - punkte[i - 1].first).toDouble()
            val stand = punkte[i].second
            if (stand >= hoch) { hoch = stand; continue }
            val abstand = 1 - stand / hoch
            // Rundungsreste zaehlen nicht als Rueckgang
            if (abstand < 1e-9) continue
            unter += dauer
            summe += abstand * dauer
        }
        return Rueckgaenge(unter / gesamt, summe / gesamt)
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
                // Rueckkauf einer verkauften Option: Ergebnis und Steuer wie bei einem Verkauf
                TxType.BUY -> { realisiert += t.realized; steuern += t.tax }
                TxType.TAX -> steuern -= t.amount
                TxType.FEE -> gebuehren -= t.amount
                else -> Unit
            }
        }
        val kursgewinne = ende - anfang - einlagen + entnahmen - dividenden - zinsen + gebuehren + steuern
        return Bilanz(anfang, einlagen, entnahmen, kursgewinne, realisiert, dividenden, zinsen, gebuehren, steuern, ende)
    }
}
