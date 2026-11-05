package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.isKnockout
import kotlin.math.abs

// Eigene Geschaefte im Kurschart eines Werts (seit 5.91): Kaeufe, Verkaeufe und Dividenden als Marken an ihrem Tag,
// dazu waagerechte Linien fuer Einstand, offene Limit- und Stop-Orders, Preisalarme und die KO-Schwellen eigener
// Knock-outs auf den Wert. Alle Preise in EUR wie die Kurse des Charts; Derivate selbst setzen keine Marken.
object EigeneMarken {

    enum class Art { KAUF, VERKAUF, DIVIDENDE }

    // Je Tag und Art eine Marke: Zeitpunkt der ersten Buchung, Stueck und Betrag zusammen, mittlerer Kurs (bei
    // Dividenden 0).
    data class Marke(val t: Long, val art: Art, val stueck: Double, val betrag: Double, val kurs: Double)

    enum class LinienArt { EINSTAND, LIMIT, STOPP, ALARM, KO }

    data class Linie(val art: LinienArt, val preis: Double)

    data class Ergebnis(val marken: List<Marke>, val linien: List<Linie>) {
        val leer: Boolean get() = marken.isEmpty() && linien.isEmpty()
    }

    private const val TAG = 86_400_000L

    // Marken und Linien fuer symbol. fx: Kurse je Euro wie in ExchangeRates; tagVon ordnet einen Zeitpunkt seinem
    // Handelstag zu (in der App die Boersenzeit).
    fun fuer(account: Account, symbol: String, fx: Map<String, Double>, tagVon: (Long) -> Long = { Math.floorDiv(it, TAG) }): Ergebnis {
        val buchungen = account.transactions.filter { t ->
            t.symbol == symbol && t.derivative == null && (t.key.isEmpty() || t.key == symbol) &&
                (t.type == TxType.BUY || t.type == TxType.SELL || t.type == TxType.DIVIDEND)
        }
        val marken = buchungen.groupBy { tagVon(it.ts) to it.type }.map { (k, l) ->
            val art = when (k.second) { TxType.BUY -> Art.KAUF; TxType.SELL -> Art.VERKAUF; else -> Art.DIVIDENDE }
            val stueck = l.sumOf { it.qty }
            if (art == Art.DIVIDENDE) Marke(l.minOf { it.ts }, art, stueck, l.sumOf { it.amount }, 0.0)
            else {
                val betrag = l.sumOf { it.qty * it.price }
                Marke(l.minOf { it.ts }, art, stueck, betrag, if (stueck > 0) betrag / stueck else 0.0)
            }
        }.sortedBy { it.t }

        val linien = ArrayList<Linie>()
        account.positions.firstOrNull { it.key == symbol && it.derivative == null && it.qty > 0 && it.avgPrice > 0 }
            ?.let { linien += Linie(LinienArt.EINSTAND, it.avgPrice) }
        account.orders.filter { it.status == OrderStatus.OPEN && it.derivative == null && it.key == symbol && it.limitPrice > 0 }.forEach { o ->
            when (o.type) {
                OrderType.LIMIT -> linien += Linie(LinienArt.LIMIT, o.limitPrice)
                OrderType.STOP, OrderType.TRAILING_STOP -> linien += Linie(LinienArt.STOPP, o.limitPrice)
                OrderType.MARKET -> {}
            }
        }
        // Tagesalarme haben keine Kursmarke
        account.alerts.filter { it.symbol == symbol && it.tagesProzent <= 0 && it.target > 0 }.forEach { linien += Linie(LinienArt.ALARM, it.target) }
        account.positions.forEach { p ->
            val d = p.derivative ?: return@forEach
            if (d.underlying != symbol || !d.kind.isKnockout || d.isKnockedOut || d.barrier <= 0 || p.qty <= 0) return@forEach
            val rate = ExchangeRates.rate(d.ccy, fx) ?: return@forEach
            linien += Linie(LinienArt.KO, d.barrier / rate)
        }
        // Gleiche Linien nur einmal
        val einmal = linien.fold(ArrayList<Linie>()) { acc, l ->
            if (acc.none { it.art == l.art && abs(it.preis - l.preis) < 1e-9 }) acc += l
            acc
        }
        return Ergebnis(marken, einmal)
    }

    // Marken am Tag von t, etwa fuer den Text unter dem Fadenkreuz.
    fun amTag(marken: List<Marke>, t: Long, tagVon: (Long) -> Long = { Math.floorDiv(it, TAG) }): List<Marke> {
        val tag = tagVon(t)
        return marken.filter { tagVon(it.t) == tag }
    }
}
