package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType

// Auswertung der eigenen abgeschlossenen Geschaefte (0.78): Haltedauer je Geschaeft, Ergebnis nach Haltedauer und nach
// Ausloeser des Verkaufs, laengste Verlustserie. Gezaehlt wird wie in der Verkaufsbilanz (jede schliessende Buchung mit
// ihrem realisierten Ergebnis vor Gebuehren und Steuern); die Haltedauer kommt aus der FIFO-Verrechnung des
// Anlegerprofils und fehlt, wenn der Kauf nicht in den Buchungen steht.
object Geschaeftsauswertung {

    enum class Dauer { WOCHE, MONAT, LAENGER }

    // HAND: selbst verkauft. STOPP, LIMIT, TRAILING: Art der ausgefuehrten Order; ORDER, wenn die Order nicht mehr in
    // der Liste steht. ENDE: Knock-out, Verfall oder Ausgleich einer verkauften Option.
    enum class Ausloeser { HAND, STOPP, LIMIT, TRAILING, ORDER, ENDE }

    data class Geschaeft(val id: String, val ts: Long, val name: String, val euro: Double, val tage: Double?, val ausloeser: Ausloeser)

    data class Gruppe(val anzahl: Int, val gewinne: Int, val summe: Double) {
        val trefferquote: Double get() = if (anzahl > 0) gewinne.toDouble() / anzahl else 0.0
    }

    // dauer und ausloeser enthalten nur Klassen mit mindestens einem Geschaeft. mittelTage: mittlere Haltedauer der
    // Geschaefte mit bekanntem Kauf.
    data class Auswertung(
        val geschaefte: List<Geschaeft>, val dauer: Map<Dauer, Gruppe>, val ausloeser: Map<Ausloeser, Gruppe>,
        val verlustserie: Int, val mittelTage: Double?,
    )

    // Unter einer Woche, bis einen Monat (31 Tage), laenger.
    fun dauerKlasse(tage: Double): Dauer = when {
        tage < 7.0 -> Dauer.WOCHE
        tage <= 31.0 -> Dauer.MONAT
        else -> Dauer.LAENGER
    }

    fun ausloeser(tx: Transaction, orders: List<Order>): Ausloeser {
        if (tx.type == TxType.KNOCKOUT || tx.type == TxType.EXPIRY) return Ausloeser.ENDE
        if (tx.via.isBlank()) return Ausloeser.HAND
        if (tx.via != Trading.VIA_ORDER) return Ausloeser.ENDE
        val o = orders.firstOrNull { it.status == OrderStatus.FILLED && it.executedAt == tx.ts && it.key == tx.posKey }
        return when (o?.type) {
            OrderType.STOP -> Ausloeser.STOPP
            OrderType.LIMIT -> Ausloeser.LIMIT
            OrderType.TRAILING_STOP -> Ausloeser.TRAILING
            else -> Ausloeser.ORDER
        }
    }

    // Alle zaehlenden Geschaefte mit Ergebnis, in zeitlicher Folge.
    fun geschaefte(txs: List<Transaction>, orders: List<Order>): List<Geschaeft> {
        val tage = Anlegerprofil.verkaeufe(txs).filter { it.id.isNotEmpty() }.associate { it.id to it.tage }
        return txs.filter { Verkaufsbilanz.zaehlt(it) && (Verkaufsbilanz.gewinn(it.realized) || Verkaufsbilanz.verlust(it.realized)) }
            .sortedBy { it.ts }
            .map { Geschaeft(it.id, it.ts, it.title, it.realized, tage[it.id], ausloeser(it, orders)) }
    }

    // Laengste Folge von Verlusten hintereinander (Ergebnisse in zeitlicher Folge).
    fun verlustserie(ergebnisse: List<Double>): Int {
        var laengste = 0
        var lauf = 0
        for (e in ergebnisse) {
            if (Verkaufsbilanz.verlust(e)) { lauf++; if (lauf > laengste) laengste = lauf }
            else if (Verkaufsbilanz.gewinn(e)) lauf = 0
        }
        return laengste
    }

    private fun gruppe(l: List<Geschaeft>) = Gruppe(l.size, l.count { Verkaufsbilanz.gewinn(it.euro) }, l.sumOf { it.euro })

    // null unter Verkaufsbilanz.MIN_VERKAEUFE Geschaeften mit Ergebnis.
    fun auswerten(txs: List<Transaction>, orders: List<Order>): Auswertung? {
        val g = geschaefte(txs, orders)
        if (g.size < Verkaufsbilanz.MIN_VERKAEUFE) return null
        val mitDauer = g.filter { it.tage != null }
        return Auswertung(
            geschaefte = g,
            dauer = Dauer.entries.mapNotNull { d -> mitDauer.filter { dauerKlasse(it.tage!!) == d }.takeIf { it.isNotEmpty() }?.let { d to gruppe(it) } }.toMap(),
            ausloeser = Ausloeser.entries.mapNotNull { a -> g.filter { it.ausloeser == a }.takeIf { it.isNotEmpty() }?.let { a to gruppe(it) } }.toMap(),
            verlustserie = verlustserie(g.map { it.euro }),
            mittelTage = mitDauer.takeIf { it.isNotEmpty() }?.map { it.tage!! }?.average(),
        )
    }
}
