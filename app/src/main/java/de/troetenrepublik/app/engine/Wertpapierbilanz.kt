package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import kotlin.math.abs

// Gewinne und Rendite je Wertpapier wie in der Ansicht je Wertpapier von Portfolio Performance, auch fuer verkaufte Werte.
// Rechnet auf ganzen Tagen: Der Zeitraum beginnt am Tag von "von" (Wert am Ende des Vortags) und endet mit dem Tag von "bis".
// Je Wert: Summe = Wert am Ende - Wert am Anfang - Kaeufe + Verkaeufe + Dividenden - Gebuehren - Steuern.
// Gebuehren ohne Zuordnung, Kontosteuern und Zinsen stehen in Sonstiges; zusammen ergeben beide die Kapitalbilanz
// (Performanceanalyse.Bilanz.absolut).
object Wertpapierbilanz {

    data class Zeile(
        val key: String,
        val name: String,
        val symbol: String,
        val imDepot: Boolean,
        // Kursgewinn aus den Verkaeufen im Zeitraum, vor Steuern und Gebuehren.
        val realisiert: Double,
        // Rest des Kursgewinns: Wertzuwachs der Stuecke ohne die Kaeufe und Verkaeufe.
        val unrealisiert: Double,
        // Vor Steuern.
        val dividenden: Double,
        val gebuehren: Double,
        val steuern: Double,
        val summe: Double,
        // Zeitgewichtete Rendite als Anteil, null ohne Bestand im Zeitraum.
        val ttwror: Double?,
        // Zinsfuss als Anteil (pro Jahr, sonst fuer den Zeitraum), null ohne eindeutige Loesung.
        val izf: Double?,
    ) {
        val kursgewinn: Double get() = realisiert + unrealisiert
    }

    // Was zu keinem Wertpapier gehoert.
    data class Sonstiges(val zinsen: Double, val gebuehren: Double, val steuern: Double) {
        val summe: Double get() = zinsen - gebuehren - steuern
        val leer: Boolean get() = abs(zinsen) < 0.005 && abs(gebuehren) < 0.005 && abs(steuern) < 0.005
    }

    data class Ergebnis(val zeilen: List<Zeile>, val sonstiges: Sonstiges, val izfProJahr: Boolean) {
        val realisiert: Double get() = zeilen.sumOf { it.realisiert }
        val unrealisiert: Double get() = zeilen.sumOf { it.unrealisiert }
        val dividenden: Double get() = zeilen.sumOf { it.dividenden }
        val gebuehren: Double get() = zeilen.sumOf { it.gebuehren } + sonstiges.gebuehren
        val steuern: Double get() = zeilen.sumOf { it.steuern } + sonstiges.steuern
        // Gleich der Kapitalbilanz: Wertzuwachs ohne Ein- und Auszahlungen.
        val summe: Double get() = zeilen.sumOf { it.summe } + sonstiges.summe
    }

    private const val TAG = Depotzerlegung.TAG
    private const val JAHR = PortfolioTools.JAHR_MS.toDouble()

    private fun handel(t: Transaction) = Depotzerlegung.imDepot(t.type) && t.qty > 0 && t.posKey.isNotBlank()

    // tage: Depotzerlegung.tage (aufsteigend, mit Wert je Schluessel), buchungen: alle Buchungen des Kontos.
    fun berechnen(
        tage: List<Depotzerlegung.Tag>,
        buchungen: List<Transaction>,
        namen: Map<String, String>,
        symbole: Map<String, String>,
        imDepot: Set<String>,
        von: Long,
        bis: Long,
        izfProJahr: Boolean = (Depotzerlegung.tagBeginn(bis) + TAG - Depotzerlegung.tagBeginn(von)) >= JAHR - 1e-3 * TAG,
    ): Ergebnis {
        val vonTag = Depotzerlegung.tagBeginn(von)
        val bisTag = Depotzerlegung.tagBeginn(bis)
        val ende = bisTag + TAG - 1
        val txs = buchungen.filter { it.ts >= vonTag && it.ts <= ende }.sortedBy { it.ts }

        // Gebuehren gehoeren zur Order zur selben Zeit; das Symbol steht in ihrer Notiz
        val handelJeZeit = txs.filter { handel(it) }.groupBy { it.ts }
        val gebuehrZu = HashMap<String, Transaction>()
        var sonstigeGebuehren = 0.0
        for (f in txs) {
            if (f.type != TxType.FEE) continue
            val kandidaten = handelJeZeit[f.ts].orEmpty()
            val ziel = kandidaten.firstOrNull { f.note.isNotBlank() && (it.posKey == f.note || it.symbol == f.note) }
                ?: kandidaten.singleOrNull()?.takeIf { f.note.isNotBlank() }
            if (ziel == null) sonstigeGebuehren -= f.amount else gebuehrZu[f.id] = ziel
        }

        class Acc {
            var realisiert = 0.0
            var dividenden = 0.0
            var gebuehren = 0.0
            var steuern = 0.0
            var handelNetto = 0.0
            var beruehrt = false
            // Verkaufte Option: ohne eingesetztes Kapital gibt es keine Rendite in Prozent
            var verkauft = false
            // Fluesse je Tag: Zufluss (Kauf samt Gebuehr), Abfluss (Verkauf nach Gebuehr und Steuer, Dividende nach Steuer)
            val zu = HashMap<Long, Double>()
            val ab = HashMap<Long, Double>()
        }
        val je = LinkedHashMap<String, Acc>()
        fun acc(k: String) = je.getOrPut(k) { Acc() }
        var zinsen = 0.0
        var sonstigeSteuern = 0.0
        for (t in txs) {
            val tag = Depotzerlegung.tagBeginn(t.ts)
            when (t.type) {
                TxType.BUY, TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY -> {
                    if (!handel(t)) continue
                    val a = acc(t.posKey)
                    a.beruehrt = true
                    if (t.verkaufteOption) a.verkauft = true
                    val wert = t.qty * t.price
                    // Ergebnis und Steuer stehen in den schliessenden Buchungen, bei verkauften Optionen auch die Steuer
                    // auf die Praemie im Verkauf zur Eroeffnung
                    a.realisiert += t.realized
                    a.steuern += t.tax
                    // Stueck ins Depot (Kauf, Rueckkauf, Ausgleich einer verkauften Option) sind Zufluesse, Stueck heraus Abfluesse
                    if (t.bestandsDelta > 0) { a.zu[tag] = (a.zu[tag] ?: 0.0) + wert + t.tax; a.handelNetto += wert }
                    else {
                        a.handelNetto -= wert
                        a.ab[tag] = (a.ab[tag] ?: 0.0) + wert - t.tax
                    }
                }
                TxType.DIVIDEND -> {
                    val k = t.posKey
                    if (k.isBlank()) { zinsen += t.amount + t.tax; sonstigeSteuern += t.tax; continue }
                    val a = acc(k)
                    a.beruehrt = true
                    a.dividenden += t.amount + t.tax
                    a.steuern += t.tax
                    a.ab[tag] = (a.ab[tag] ?: 0.0) + t.amount
                }
                TxType.INTEREST -> { zinsen += t.amount + t.tax; sonstigeSteuern += t.tax }
                TxType.TAX -> sonstigeSteuern -= t.amount
                TxType.FEE -> {
                    val ziel = gebuehrZu[t.id] ?: continue
                    val a = acc(ziel.posKey)
                    val g = -t.amount
                    a.gebuehren += g
                    if (ziel.bestandsDelta > 0) a.zu[tag] = (a.zu[tag] ?: 0.0) + g
                    else a.ab[tag] = (a.ab[tag] ?: 0.0) - g
                }
                else -> Unit
            }
        }

        // Werte je Tag aus der Zerlegung; der Tag vor dem Zeitraum liefert den Anfangswert
        val erster = tage.indexOfFirst { it.beginn >= vonTag }
        val letzter = tage.indexOfLast { it.beginn <= bisTag }
        val vorTag = tage.getOrNull(if (erster < 0) tage.size - 1 else erster - 1)
        val imZeitraum = if (erster in 0..letzter) tage.subList(erster, letzter + 1) else emptyList()
        val schluessel = LinkedHashSet<String>()
        schluessel += je.keys
        vorTag?.wert?.keys?.let { schluessel += it }
        imZeitraum.forEach { schluessel += it.wert.keys }

        val zeilen = ArrayList<Zeile>()
        for (k in schluessel) {
            val a = je[k]
            val anfang = vorTag?.wert?.get(k) ?: 0.0
            val schluss = (if (imZeitraum.isEmpty()) vorTag else imZeitraum.last())?.wert?.get(k) ?: 0.0
            val kursgewinn = schluss - anfang - (a?.handelNetto ?: 0.0)
            val realisiert = a?.realisiert ?: 0.0
            val dividenden = a?.dividenden ?: 0.0
            val gebuehren = a?.gebuehren ?: 0.0
            val steuern = a?.steuern ?: 0.0
            val summe = kursgewinn + dividenden - gebuehren - steuern
            if (anfang == 0.0 && schluss == 0.0 && a?.beruehrt != true && abs(summe) < 0.005) continue

            // Zeitgewichtet: Zufluesse zaehlen am Anfang, Abfluesse am Ende eines Tages
            var index = 1.0
            var gehalten = false
            var vorher = anfang
            for (t in imZeitraum) {
                val w = t.wert[k] ?: 0.0
                val zu = a?.zu?.get(t.beginn) ?: 0.0
                val ab = a?.ab?.get(t.beginn) ?: 0.0
                val basis = vorher + zu
                if (basis > 1e-6) {
                    gehalten = true
                    val r = (w + ab) / basis - 1
                    if (r.isFinite()) index *= 1 + r
                }
                vorher = w
            }
            val ttwror = if (gehalten && a?.verkauft != true) index - 1 else null

            val fluesse = ArrayList<PortfolioTools.Flow>()
            if (anfang > 0) fluesse += PortfolioTools.Flow(vonTag, anfang, k)
            a?.zu?.forEach { (tag, v) -> if (v != 0.0) fluesse += PortfolioTools.Flow(tag + TAG / 2, v, k) }
            a?.ab?.forEach { (tag, v) -> if (v != 0.0) fluesse += PortfolioTools.Flow(tag + TAG / 2, -v, k) }
            val periode = (ende - vonTag).toDouble()
            val izf = if (fluesse.none { it.amount > 0 } || a?.verkauft == true) null
            else if (izfProJahr) PortfolioTools.moneyWeighted(fluesse, schluss, ende)
            else PortfolioTools.moneyWeighted(fluesse, schluss, ende, periode)

            zeilen += Zeile(
                key = k, name = namen[k] ?: k, symbol = symbole[k] ?: k, imDepot = k in imDepot,
                realisiert = realisiert, unrealisiert = kursgewinn - realisiert, dividenden = dividenden,
                gebuehren = gebuehren, steuern = steuern, summe = summe, ttwror = ttwror, izf = izf,
            )
        }
        return Ergebnis(zeilen.sortedByDescending { it.summe }, Sonstiges(zinsen, sonstigeGebuehren, sonstigeSteuern), izfProJahr)
    }
}
