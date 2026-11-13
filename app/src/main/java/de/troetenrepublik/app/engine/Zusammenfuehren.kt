package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.DepotRegeln
import de.troetenrepublik.app.data.Einzahlplan
import de.troetenrepublik.app.data.Lot
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Snapshot
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.TheseZaehler
import de.troetenrepublik.app.data.These

// Zwei Depots zusammenfuehren.
// Guthaben, Einzahlungen und Auszahlungen addieren sich, gleiche Aktien werden zu einer Position mit
// gewichtetem Einstand, Buchungen, Orders, Sparplaene, Preisalarme und Wetten kommen dazu (gleiche Kennung nur
// einmal), die Merkliste wird vereinigt und der Depotverlauf zur Summe beider Verlaeufe.
object Zusammenfuehren {

    // Steht jede Buchung des neuen Depots schon im alten (dieselbe Datei noch einmal, eine aeltere Sicherung), ist alles
    // Geld und jeder Bestand schon darin: Guthaben, Einzahlungen und Positionen werden dann nicht ein zweites Mal addiert.
    fun schonEnthalten(alt: Account, neu: Account): Boolean {
        if (neu.transactions.isEmpty()) return false
        val ids = alt.transactions.mapTo(HashSet()) { it.id }
        return neu.transactions.all { it.id in ids }
    }

    fun konten(alt: Account, neu: Account): Account {
        val ids = alt.transactions.mapTo(HashSet()) { it.id }
        val neue = neu.transactions.filter { it.id !in ids }
        // Keine Buchung gemeinsam: beide Depots addieren sich
        if (neue.size == neu.transactions.size) return summe(alt, neu)
        // Alles schon da: nichts addieren, nur Listen wie Orders, Plaene, Alarme und Notizen vereinigen
        if (neue.isEmpty()) return summe(alt, neu).copy(
            cash = alt.cash, deposited = alt.deposited, withdrawn = alt.withdrawn, transferredIn = alt.transferredIn,
            interestAccrued = alt.interestAccrued, positions = alt.positions, history = alt.history, tax = alt.tax,
            theseZaehler = alt.theseZaehler, einzahlplan = alt.einzahlplan, bindungen = alt.bindungen,
        )
        // Teilweise Ueberschneidung (zwei Sicherungen desselben Depots zu verschiedenen Zeiten): nur die neuen Buchungen
        // wirken auf Guthaben, Einzahlungen und Bestand.
        val ts = neue.sortedBy { it.ts }
        var pos = alt.positions
        for (t in ts) pos = buchen(pos, t, neu.positions)
        return StillhalterDepot.bereinigen(summe(alt, neu).copy(
            cash = alt.cash + ts.sumOf { it.amount },
            deposited = alt.deposited + ts.filter { it.type == TxType.DEPOSIT }.sumOf { it.amount },
            withdrawn = alt.withdrawn + ts.filter { it.type == TxType.WITHDRAWAL }.sumOf { -it.amount },
            transferredIn = alt.transferredIn + ts.filter { it.type == TxType.BUY && it.tag == "TRANSFER_IN" }.sumOf { it.qty * it.price },
            interestAccrued = alt.interestAccrued, positions = pos,
            history = (alt.history + neu.history).distinctBy { it.ts }.sortedBy { it.ts },
            tax = alt.tax, theseZaehler = alt.theseZaehler, einzahlplan = alt.einzahlplan,
        ))
    }

    // Eine Buchung auf die Positionen anwenden: Kauf erhoeht den Bestand zum gewichteten Einstand, Verkauf, Knock-out und
    // Verfall verringern ihn. Derivate bringen ihr Produkt aus dem neuen Depot mit. Verkaufte Optionen (0.77): der Verkauf
    // eroeffnet eine Position mit negativem Bestand, Rueckkauf und Verfall bringen ihn zurueck auf null.
    private fun buchen(pos: List<Position>, t: Transaction, neuePos: List<Position>): List<Position> {
        if (t.qty <= 0 || t.type != TxType.BUY && t.type != TxType.SELL && t.type != TxType.KNOCKOUT && t.type != TxType.EXPIRY) return pos
        val key = t.posKey
        val i = pos.indexOfFirst { it.key == key }
        if (t.verkaufteOption) {
            val lot = Lot(-t.qty, t.price, t.ts, t.fee)
            if (t.type == TxType.SELL) {
                if (i < 0) return pos + Position(key, t.title, t.symbol.ifBlank { key }, -t.qty, t.price, t.ts, t.derivative, lots = listOf(lot))
                val p = pos[i]
                val menge = p.qty - t.qty
                return pos.toMutableList().also { it[i] = p.copy(qty = menge, avgPrice = (p.invested - t.qty * t.price) / menge, lots = Posten.dazu(p, lot)) }
            }
            if (i < 0) return pos
            val p = pos[i]
            val rest = -p.qty - t.qty
            if (rest <= 1e-9) return pos.filterIndexed { k, _ -> k != i }
            val ab = Posten.abgang(Posten.von(p).map { it.copy(qty = -it.qty) }, t.qty)
            return pos.toMutableList().also { it[i] = p.copy(qty = -rest, avgPrice = Posten.einstand(ab.rest), lots = ab.rest.map { l -> l.copy(qty = -l.qty) }) }
        }
        if (t.type == TxType.BUY) {
            if (i < 0) {
                val vorlage = neuePos.firstOrNull { it.key == key }
                return pos + (vorlage?.copy(qty = t.qty, avgPrice = t.price, openedAt = t.ts, lots = listOf(Lot(t.qty, t.price, t.ts, t.fee)))
                    ?: Position(key, t.title, t.symbol.ifBlank { key }, t.qty, t.price, t.ts, t.derivative, t.importedAt, listOf(Lot(t.qty, t.price, t.ts, t.fee))))
            }
            val p = pos[i]
            val menge = p.qty + t.qty
            return pos.toMutableList().also { it[i] = p.copy(qty = menge, avgPrice = (p.invested + t.qty * t.price) / menge, openedAt = minOf(p.openedAt, t.ts), lots = Posten.dazu(p, Lot(t.qty, t.price, t.ts, t.fee))) }
        }
        if (i < 0) return pos
        val p = pos[i]
        val rest = p.qty - t.qty
        if (rest <= 1e-9) return pos.filterIndexed { k, _ -> k != i }
        val ab = Posten.abgang(Posten.von(p), t.qty)
        return pos.toMutableList().also { it[i] = p.copy(qty = rest, avgPrice = Posten.einstand(ab.rest), lots = ab.rest) }
    }

    private fun summe(alt: Account, neu: Account): Account = Watchlisten.vereinigen(alt, neu).copy(
        cash = alt.cash + neu.cash,
        deposited = alt.deposited + neu.deposited,
        withdrawn = alt.withdrawn + neu.withdrawn,
        transferredIn = alt.transferredIn + neu.transferredIn,
        interestAccrued = alt.interestAccrued + neu.interestAccrued,
        positions = positionen(alt.positions, neu.positions),
        orders = vereinigt(alt.orders, neu.orders) { it.id },
        bindungen = vereinigt(alt.bindungen, neu.bindungen) { it.gruppe },
        transactions = vereinigt(alt.transactions, neu.transactions) { it.id }.sortedBy { it.ts },
        plans = vereinigt(alt.plans, neu.plans) { it.id },
        alerts = vereinigt(alt.alerts, neu.alerts) { it.id },
        wetten = vereinigt(alt.wetten, neu.wetten) { it.id },
        watchMeta = neu.watchMeta + alt.watchMeta,
        notizen = notizen(alt.notizen, neu.notizen),
        thesen = thesen(alt.thesen, neu.thesen),
        depotRegeln = regeln(alt.depotRegeln, neu.depotRegeln),
        theseZaehler = TheseZaehler(
            alt.theseZaehler.kaeufe + neu.theseZaehler.kaeufe, alt.theseZaehler.mitThese + neu.theseZaehler.mitThese,
            alt.theseZaehler.bestaetigt + neu.theseZaehler.bestaetigt, alt.theseZaehler.erwogen + neu.theseZaehler.erwogen,
            alt.theseZaehler.regelbrueche + neu.theseZaehler.regelbrueche,
        ),
        einzahlplan = einzahlplan(alt.einzahlplan, neu.einzahlplan),
        wiederanlage = neu.wiederanlage + alt.wiederanlage,
        herkunftWahl = neu.herkunftWahl + alt.herkunftWahl,
        history = verlauf(alt.history, neu.history),
        tax = steuer(alt.tax, neu.tax),
        createdAt = listOf(alt.createdAt, neu.createdAt).filter { it > 0 }.minOrNull() ?: 0L,
    )

    private fun <T> vereinigt(a: List<T>, b: List<T>, id: (T) -> String): List<T> {
        val da = a.map(id).toHashSet()
        return a + b.filter { id(it) !in da }
    }

    // Gleiche Position (gleicher Schluessel, auch bei Derivaten): Stueck addiert, Einstand nach Stueck gewichtet.
    // Bei einem Derivat gilt der Stand des Produkts, der weiter fortgeschritten ist (ausgeknockt vor lebend).
    fun positionen(a: List<Position>, b: List<Position>): List<Position> {
        val aus = a.toMutableList()
        for (p in b) {
            val i = aus.indexOfFirst { it.key == p.key }
            if (i < 0) { aus += p; continue }
            val q = aus[i]
            val produkt = when {
                q.derivative == null -> p.derivative
                p.derivative == null -> q.derivative
                q.derivative.isKnockedOut || !p.derivative.isKnockedOut -> q.derivative
                else -> p.derivative
            }
            val stueck = q.qty + p.qty
            aus[i] = q.copy(
                qty = stueck,
                avgPrice = if (stueck != 0.0) (q.qty * q.avgPrice + p.qty * p.avgPrice) / stueck else q.avgPrice,
                openedAt = minOf(q.openedAt, p.openedAt),
                importedAt = listOf(q.importedAt, p.importedAt).filter { it > 0 }.minOrNull() ?: 0L,
                lots = Posten.zusammen(q, p),
                derivative = produkt,
            )
        }
        return aus
    }

    // Notizen zum selben Wert: beide Texte bleiben, getrennt durch eine Leerzeile.
    private fun notizen(a: Map<String, String>, b: Map<String, String>): Map<String, String> {
        val aus = LinkedHashMap(b)
        for ((k, t) in a) {
            val andere = b[k]
            aus[k] = if (andere.isNullOrBlank() || andere.trim() == t.trim()) t else if (t.isBlank()) andere else t.trimEnd() + "\n\n" + andere.trim()
        }
        return aus
    }

    // Thesen zum selben Wert: Ausstiegsbedingungen beider bleiben, die strengere Obergrenze und der fruehere Termin gelten.
    private fun thesen(a: Map<String, These>, b: Map<String, These>): Map<String, These> {
        val aus = LinkedHashMap(b)
        for ((k, t) in a) {
            val n = b[k]
            aus[k] = if (n == null) t else t.copy(
                ausstieg = if (n.ausstieg.isBlank() || n.ausstieg.trim() == t.ausstieg.trim()) t.ausstieg
                    else if (t.ausstieg.isBlank()) n.ausstieg else t.ausstieg.trimEnd() + "\n" + n.ausstieg.trim(),
                obergrenze = listOf(t.obergrenze, n.obergrenze).filter { it > 0 }.minOrNull() ?: 0.0,
                pruefAm = listOf(t.pruefAm, n.pruefAm).filter { it > 0 }.minOrNull() ?: 0L,
                seit = listOf(t.seit, n.seit).filter { it > 0 }.minOrNull() ?: 0L,
                gemeldetAm = maxOf(t.gemeldetAm, n.gemeldetAm),
            )
        }
        return aus
    }

    // Depotregeln: die strengere Grenze gilt, ein gesetzter Schalter bleibt gesetzt.
    private fun regeln(a: DepotRegeln, b: DepotRegeln) = DepotRegeln(
        maxPosition = listOf(a.maxPosition, b.maxPosition).filter { it > 0 }.minOrNull() ?: 0.0,
        keineHebel = a.keineHebel || b.keineHebel,
        minBargeld = maxOf(a.minBargeld, b.minBargeld),
        maxVerlustGeschaeft = listOf(a.maxVerlustGeschaeft, b.maxVerlustGeschaeft).filter { it > 0 }.minOrNull() ?: 0.0,
        maxVerlustTag = listOf(a.maxVerlustTag, b.maxVerlustTag).filter { it > 0 }.minOrNull() ?: 0.0,
        maxGeschaefteTag = listOf(a.maxGeschaefteTag, b.maxGeschaefteTag).filter { it > 0 }.minOrNull() ?: 0,
        minChanceRisiko = maxOf(a.minChanceRisiko, b.minChanceRisiko),
    )

    // Einzahlplaene: ein Plan bleibt, wie er ist; bei zwei Plaenen laeuft der Betrag beider weiter (nur nicht pausierte
    // zaehlen), der Tag des ersten gilt.
    private fun einzahlplan(a: Einzahlplan?, b: Einzahlplan?): Einzahlplan? {
        if (a == null || b == null) return a ?: b
        val laufend = listOf(a, b).filter { !it.pausiert }
        val betrag = (laufend.ifEmpty { listOf(a, b) }).sumOf { it.betrag }
        return a.copy(
            betrag = betrag,
            pausiert = a.pausiert && b.pausiert,
            letzterMonat = maxOf(a.letzterMonat, b.letzterMonat),
            erstellt = listOf(a.erstellt, b.erstellt).filter { it > 0 }.minOrNull() ?: 0L,
            ausfuehrungen = a.ausfuehrungen + b.ausfuehrungen,
            summe = a.summe + b.summe,
        )
    }

    // Summe zweier Depotverlaeufe: zu jedem Zeitpunkt der letzte Wert beider Depots (vor dem ersten: 0).
    fun verlauf(a: List<Snapshot>, b: List<Snapshot>): List<Snapshot> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val x = a.sortedBy { it.ts }
        val y = b.sortedBy { it.ts }
        val aus = ArrayList<Snapshot>(x.size + y.size)
        var i = 0
        var j = 0
        var la: Snapshot? = null
        var lb: Snapshot? = null
        while (i < x.size || j < y.size) {
            val t = minOf(x.getOrNull(i)?.ts ?: Long.MAX_VALUE, y.getOrNull(j)?.ts ?: Long.MAX_VALUE)
            while (i < x.size && x[i].ts == t) la = x[i++]
            while (j < y.size && y[j].ts == t) lb = y[j++]
            fun summe(f: (Snapshot) -> Double?): Double? {
                val u = la?.let(f)
                val v = lb?.let(f)
                return if ((la != null && u == null) || (lb != null && v == null)) null else (u ?: 0.0) + (v ?: 0.0)
            }
            aus += Snapshot(t, (la?.value ?: 0.0) + (lb?.value ?: 0.0), summe { it.securities }, summe { it.profit })
        }
        return aus
    }

    // Steuertoepfe desselben Jahres addieren; sonst gilt der Stand des juengeren Jahres.
    fun steuer(a: TaxState, b: TaxState): TaxState = when {
        a.year == b.year -> TaxState(
            a.year, a.allowanceUsed + b.allowanceUsed, a.lossPotStocks + b.lossPotStocks, a.lossPotGeneral + b.lossPotGeneral,
            a.taxPaid + b.taxPaid, a.gainsStocks + b.gainsStocks, a.gainsGeneral + b.gainsGeneral,
            a.taxedStocks + b.taxedStocks, a.taxedGeneral + b.taxedGeneral,
            a.allowanceStocks + b.allowanceStocks, a.allowanceGeneral + b.allowanceGeneral,
            a.privateGains + b.privateGains,
        )
        b.year > a.year -> b
        else -> a
    }
}
