package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Handelskalender: je Tag die geschlossenen Positionen mit realisiertem Ergebnis und die Dividenden.
// Geschlossen wird eine Position durch jeden Verkauf, auch Teilverkauf, die Rueckzahlung eines Knock-outs und den
// Verfall eines Optionsscheins. Das Ergebnis steht in der Buchung, vor Gebuehren und Steuern. Einsatz ist der
// Einstandswert der geschlossenen Stuecke, also Erloes minus Ergebnis. Tagesergebnis in Prozent: Summe der Ergebnisse
// durch Summe der Einsaetze. Knock-outs zaehlen am Tag des Knock-outs, nicht an der Rueckzahlung zwei Tage spaeter.
object TradeCalendar {

    // einsatz: Einstandswert der geschlossenen Stuecke.
    data class Trade(val tx: Transaction, val ts: Long, val einsatz: Double) {
        val realisiert: Double get() = tx.realized
        val prozent: Double get() = if (einsatz > 0) realisiert / einsatz * 100.0 else 0.0
    }

    // Erhalten: aus der Buchung, netto nach Steuern. Erwartet: Betrag vor Steuern nach dem Ausschuettungsplan
    // der letzten zwoelf Monate.
    data class Dividende(
        val key: String,
        val name: String,
        val symbol: String,
        val ts: Long,
        val brutto: Double,
        val steuer: Double,
        val stueck: Double,
        val jeStueck: Double,
        val erwartet: Boolean,
    ) {
        val netto: Double get() = brutto - steuer
    }

    data class Tag(val datum: LocalDate, val trades: List<Trade>, val dividenden: List<Dividende>) {
        val realisiert: Double get() = trades.sumOf { it.realisiert }
        val einsatz: Double get() = trades.sumOf { it.einsatz }
        val prozent: Double get() = if (einsatz > 0) realisiert / einsatz * 100.0 else 0.0
        val gebuehren: Double get() = trades.sumOf { it.tx.fee }
        val steuern: Double get() = trades.sumOf { it.tx.tax }
        val dividendenErhalten: Double get() = dividenden.filter { !it.erwartet }.sumOf { it.netto }
        val dividendenErwartet: Double get() = dividenden.filter { it.erwartet }.sumOf { it.brutto }
        val hatTrades: Boolean get() = trades.isNotEmpty()
    }

    data class Monat(
        val monat: YearMonth,
        val realisiert: Double,
        val trades: Int,
        val gewinne: Int,
        val verluste: Int,
        val dividendenErhalten: Double,
        val dividendenErwartet: Double,
    )

    fun schliesst(tx: Transaction): Boolean = tx.type == TxType.SELL || tx.type == TxType.KNOCKOUT || tx.type == TxType.EXPIRY

    // Beim Knock-out zaehlt der Knock-out selbst, sonst die Buchung.
    fun endeVon(tx: Transaction): Long = if (tx.closedAt > 0L) tx.closedAt else tx.ts

    fun trade(tx: Transaction): Trade {
        val erloes = tx.qty * tx.price
        return Trade(tx, endeVon(tx), (erloes - tx.realized).coerceAtLeast(0.0))
    }

    // In der Buchung steht der Betrag nach Steuern, die Steuer daneben.
    fun dividende(tx: Transaction): Dividende =
        Dividende(tx.posKey, tx.title, tx.symbol, tx.ts, tx.amount + tx.tax, tx.tax, tx.qty, tx.price, erwartet = false)

    // erwartet: kommende Dividenden aus dem Dividendenkalender, sie stehen am erwarteten Zahltag.
    fun tage(transactions: List<Transaction>, erwartet: List<Dividende>, zone: ZoneId): Map<LocalDate, Tag> {
        fun tag(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val trades = transactions.filter { schliesst(it) }.map { trade(it) }.groupBy { tag(it.ts) }
        val dividenden = (transactions.filter { it.type == TxType.DIVIDEND }.map { dividende(it) } + erwartet).groupBy { tag(it.ts) }
        return (trades.keys + dividenden.keys).associateWith { d ->
            Tag(d, trades[d].orEmpty().sortedBy { it.ts }, dividenden[d].orEmpty().sortedBy { it.ts })
        }
    }

    fun monat(tage: Map<LocalDate, Tag>, ym: YearMonth): Monat {
        val drin = tage.values.filter { YearMonth.from(it.datum) == ym }
        val alle = drin.flatMap { it.trades }
        return Monat(
            monat = ym,
            realisiert = alle.sumOf { it.realisiert },
            trades = alle.size,
            gewinne = alle.count { it.realisiert > 0.005 },
            verluste = alle.count { it.realisiert < -0.005 },
            dividendenErhalten = drin.sumOf { it.dividendenErhalten },
            dividendenErwartet = drin.sumOf { it.dividendenErwartet },
        )
    }

    // Staerke der Faerbung, 0 bis 1, im Verhaeltnis zum groessten Betrag des Monats.
    // Mindestens 0,25, damit jeder Handelstag sichtbar bleibt.
    fun staerke(realisiert: Double, groesster: Double): Float {
        if (!(groesster > 0)) return 0.25f
        val anteil = (kotlin.math.abs(realisiert) / groesster).coerceIn(0.0, 1.0)
        return (0.25 + 0.75 * kotlin.math.sqrt(anteil)).toFloat()
    }

    // Vom ersten Monat mit Buchungen bis zum letzten mit erwarteter Dividende.
    fun spanne(transactions: List<Transaction>, erwartet: List<Dividende>, heute: YearMonth, zone: ZoneId): ClosedRange<YearMonth> {
        val erster = transactions.minOfOrNull { it.ts }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: heute
        val letzter = erwartet.maxOfOrNull { it.ts }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: heute
        return minOf(erster, heute)..maxOf(letzter, heute)
    }
}
