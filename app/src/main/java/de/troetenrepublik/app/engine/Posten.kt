package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lot
import de.troetenrepublik.app.data.Position
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Kaufposten einer Position (seit 5.94). Verkauft wird nach FIFO, die zuerst gekauften Stueck gehen zuerst
// (§ 20 Abs. 4 Satz 7 EStG). Der Einstandskurs einer Position ist der Schnitt der verbliebenen Posten.
object Posten {

    // Posten der Position, aelteste zuerst. Passen sie nicht zur Stueckzahl (etwa bei Positionen von vor 5.94),
    // gilt der Einstand als ein einziger Posten.
    fun von(p: Position): List<Lot> {
        val l = p.lots
        if (l.isNotEmpty() && abs(l.sumOf { it.qty } - p.qty) <= 1e-6 * max(1.0, p.qty)) return l.sortedBy { it.ts }
        return listOf(Lot(p.qty, p.avgPrice, p.openedAt))
    }

    // Ein Verkauf von qty Stueck: Anschaffungskosten und Kaufgebuehren der verbrauchten Teile, die Teile selbst und der Rest.
    data class Abgang(val kosten: Double, val gebuehren: Double, val teile: List<Lot>, val rest: List<Lot>)

    fun abgang(lots: List<Lot>, qty: Double): Abgang {
        var offen = qty
        var kosten = 0.0
        var gebuehren = 0.0
        val teile = ArrayList<Lot>()
        val rest = ArrayList<Lot>()
        for (l in lots) {
            if (l.qty <= 1e-12) continue
            if (offen <= 1e-12) { rest += l; continue }
            val n = min(offen, l.qty)
            val anteil = n / l.qty
            kosten += n * l.price
            gebuehren += l.fee * anteil
            teile += Lot(n, l.price, l.ts, l.fee * anteil)
            if (l.qty - n > 1e-9) rest += Lot(l.qty - n, l.price, l.ts, l.fee * (1.0 - anteil))
            offen -= n
        }
        return Abgang(kosten, gebuehren, teile, rest)
    }

    // Schnitt je Stueck ohne Gebuehren, 0 ohne Bestand.
    fun einstand(lots: List<Lot>): Double {
        val n = lots.sumOf { it.qty }
        return if (n > 1e-12) lots.sumOf { it.qty * it.price } / n else 0.0
    }

    // Ein weiterer Posten, nach Kaufzeit eingeordnet.
    fun dazu(p: Position, neu: Lot): List<Lot> = (von(p) + neu).sortedBy { it.ts }

    // Zwei Bestaende derselben Aktie.
    fun zusammen(a: Position, b: Position): List<Lot> = (von(a) + von(b)).sortedBy { it.ts }

    // Split: Posten vor dem Stichtag in neuen Einheiten.
    fun split(lots: List<Lot>, r: Double, stichtag: Long): List<Lot> =
        lots.map { if (it.ts < stichtag) it.copy(qty = it.qty * r, price = it.price / r) else it }
}
