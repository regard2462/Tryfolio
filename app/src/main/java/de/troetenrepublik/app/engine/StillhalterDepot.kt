package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Bindung
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Lot
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.isOption
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

// Verkaufte Optionen im Uebungsdepot (0.77). Eine verkaufte Option steht mit negativer Stueckzahl im Depot, die Praemie
// fliesst beim Verkauf aufs Guthaben, der Wert der Position ist eine Verbindlichkeit. Ungedeckt wird nichts verkauft:
// verkaufte Calls binden Aktien des Basiswerts (eine je Stueck), die hoechste moegliche Zahlung bei Verfall bindet
// Guthaben. Bei Spreads deckt das gekaufte Bein das verkaufte, gebunden wird nur, was bei Verfall hoechstens zu zahlen
// ist; das freie Guthaben sinkt dadurch genau um den hoechsten moeglichen Verlust. Steuer wie beim Stillhaltergeschaeft:
// die Praemie ist bei Zufluss ein Ertrag, Rueckkauf und Ausgleich bei Verfall sind negative Ertraege (allgemeiner Topf).
object StillhalterDepot {

    const val TAG_OEFFNEN = "SELL_OPEN"
    const val TAG_RUECKKAUF = "BUY_CLOSE"
    // Aktienverkauf, wenn das Guthaben den Ausgleich bei Verfall nicht traegt.
    const val VIA_AUSGLEICH = "SETTLE_COVER"

    // Ein Bein fuer die Deckungsrechnung: Stueck (verkauft negativ), Call oder Put, Basispreis in der Handelswaehrung,
    // Bezugsverhaeltnis (Stueck je Aktie).
    data class Option(val stueck: Double, val call: Boolean, val strike: Double, val ratio: Double = 1.0)

    // Ein Bein einer Strategie zum Anlegen: stueck > 0 kaufen, < 0 verkaufen zur Eroeffnung; derivative null = Aktie.
    // preis: Briefkurs beim Kauf, Geldkurs beim Verkauf (EUR je Stueck).
    data class Bein(val key: String, val name: String, val symbol: String, val stueck: Double, val derivative: Derivative?, val preis: Double)

    // Aktien, die verkaufte Calls ueber die gekauften Calls hinaus brauchen.
    fun aktienBedarf(optionen: List<Option>): Double = max(0.0, -optionen.filter { it.call }.sumOf { it.stueck / it.ratio })

    // Hoechste Zahlung bei Verfall in der Handelswaehrung, wenn aktien Aktien mitzaehlen (sie sind bei Verfall den Kurs
    // wert). Die Auszahlung ist stueckweise linear, das Tief liegt bei null oder einem Basispreis. null = unbegrenzt.
    fun hoechsteZahlung(optionen: List<Option>, aktien: Double): Double? {
        val steigung = optionen.filter { it.call }.sumOf { it.stueck / it.ratio } + aktien
        if (steigung < -1e-9) return null
        fun wert(s: Double) = aktien * s + optionen.sumOf { o ->
            o.stueck * (if (o.call) max(0.0, s - o.strike) else max(0.0, o.strike - s)) / o.ratio
        }
        val tief = (listOf(0.0) + optionen.map { it.strike }).minOf { wert(it) }
        return max(0.0, -tief)
    }

    fun optionen(positionen: List<Position>, gruppe: String): List<Option> = positionen.mapNotNull { p ->
        val d = p.derivative ?: return@mapNotNull null
        if (gruppe.isBlank() || d.gruppe != gruppe || !d.kind.isOption) null
        else Option(p.qty, d.kind == DerivativeKind.CALL, d.strike, d.ratio)
    }

    fun gruppe(account: Account, gruppe: String): List<Position> =
        if (gruppe.isBlank()) emptyList() else account.positions.filter { it.derivative?.gruppe == gruppe }

    fun bindung(account: Account, gruppe: String): Bindung? = account.bindungen.firstOrNull { it.gruppe == gruppe }

    // Bindung einer Gruppe nach einer Aenderung ihrer Beine neu rechnen. Ohne verkaufte Option faellt sie weg. null, wenn
    // die Gruppe danach ungedeckt waere: mehr Aktien noetig als gebunden, unbegrenzte Zahlung oder zu wenig freies Guthaben
    // fuer eine hoehere Bindung.
    fun neuBinden(account: Account, gruppe: String): Account? {
        if (gruppe.isBlank()) return account
        val b = bindung(account, gruppe)
        val opt = optionen(account.positions, gruppe)
        if (opt.none { it.stueck < 0 }) return if (b == null) account else account.copy(bindungen = account.bindungen - b)
        if (b == null) return null
        val bedarf = aktienBedarf(opt)
        if (bedarf > b.stueck + 1e-9) return null
        val zahlung = hoechsteZahlung(opt, bedarf) ?: return null
        val neu = b.copy(betrag = zahlung, stueck = bedarf)
        val acc = account.copy(bindungen = account.bindungen.map { if (it.gruppe == gruppe) neu else it })
        if (neu.cash > b.cash + 0.005 && acc.frei < -0.005) return null
        return acc
    }

    // Bindungen ohne offene verkaufte Option (etwa nach dem Zusammenfuehren zweier Sicherungen) entfallen.
    fun bereinigen(account: Account): Account {
        val offen = account.positions.filter { it.verkauft }.mapNotNull { it.derivative?.gruppe }.toSet()
        val bleibt = account.bindungen.filter { it.gruppe in offen }.distinctBy { it.gruppe }
        return if (bleibt.size == account.bindungen.size) account else account.copy(bindungen = bleibt)
    }

    // Liegen alle gebundenen Aktien noch im Depot? (Etwa nach dem Zusammenfuehren mit uebernommenen Verkaeufen.)
    fun aktienGedeckt(account: Account): Boolean = account.bindungen.map { it.symbol }.distinct().all { s ->
        (account.positions.firstOrNull { it.key == s && it.derivative == null }?.qty ?: 0.0) >= account.gebundeneStueck(s) - 1e-9
    }

    // Gebundene Aktien folgen einem Split (eine Option bezieht sich danach auf r neue Aktien).
    fun split(account: Account, symbol: String, r: Double): Account =
        if (account.bindungen.none { it.symbol == symbol }) account
        else account.copy(bindungen = account.bindungen.map { if (it.symbol == symbol) it.copy(stueck = it.stueck * r) else it })

    // Wechselkurs der Bindungen mit frischen Kursen nachfuehren.
    fun fxNachfuehren(account: Account, fx: (String) -> Double?): Account {
        if (account.bindungen.isEmpty()) return account
        var geaendert = false
        val neu = account.bindungen.map { b ->
            val f = fx(b.symbol)?.takeIf { it.isFinite() && it > 0 } ?: return@map b
            if (abs(f - b.fx) < 1e-12) b else { geaendert = true; b.copy(fx = f) }
        }
        return if (geaendert) account.copy(bindungen = neu) else account
    }

    // ------------------------------------------------------------ Handel

    // Verkauf zur Eroeffnung: qty Stueck (positiv) zum Geldkurs preis. Die Deckung prueft eroeffnen.
    fun verkaufOeffnen(
        account: Account, key: String, name: String, symbol: String, qty: Double, preis: Double, derivative: Derivative,
        now: Long, fee: Double = Trading.FEE, allowance: Double = 0.0,
    ): TradeResult {
        if (!qty.isFinite() || qty <= 0.0) return TradeResult(account, Err.INVALID_QTY)
        if (qty != floor(qty)) return TradeResult(account, Err.WHOLE_PIECES)
        if (!preis.isFinite() || preis <= 0.0) return TradeResult(account, Err.NO_PRICE)
        if (!fee.isFinite() || fee < 0) return TradeResult(account, Err.INVALID_FEE)
        if (!derivative.verkauft || !derivative.kind.isOption || !Derivatives.tradable(derivative, now)) return TradeResult(account, Err.NOT_TRADABLE)
        val existing = account.positions.firstOrNull { it.key == key }
        if (existing != null && !existing.verkauft) return TradeResult(account, Err.NOT_TRADABLE)
        val praemie = qty * preis
        if (!praemie.isFinite()) return TradeResult(account, Err.TOO_LARGE)
        if (account.cash + praemie < fee) return TradeResult(account, Err.FEE_NOT_COVERED)
        val lot = Lot(-qty, preis, now, fee)
        val positions = if (existing == null) {
            account.positions + Position(key, name, symbol, -qty, preis, now, derivative, lots = listOf(lot))
        } else account.positions.map {
            if (it.key != key) it else {
                val menge = it.qty - qty
                it.copy(qty = menge, avgPrice = (it.invested - praemie) / menge, lots = Posten.dazu(it, lot))
            }
        }
        // Die Praemie ist bei Zufluss ein Ertrag, die Gebuehr mindert ihn
        val taxed = Tax.apply(account.tax, praemie - fee, Tax.Kind.GENERAL, allowance, now)
        val tx = Transaction(
            id = Trading.newId("tx", now), ts = now, type = TxType.SELL, title = name, amount = praemie - taxed.tax,
            qty = qty, price = preis, symbol = symbol, tax = taxed.tax, fee = fee, key = key, tag = TAG_OEFFNEN, derivative = derivative,
        )
        var acc = account.copy(
            cash = Trading.kasse(account.cash + praemie - taxed.tax), positions = positions,
            transactions = Trading.kuerzen(account.transactions + tx), tax = taxed.state,
        )
        if (fee > 0) acc = Trading.addFee(acc, fee, now, name, symbol)
        return TradeResult(acc)
    }

    // Rueckkauf von qty Stueck (positiv) zum Briefkurs preis. Die Bindung der Gruppe sinkt entsprechend; das freie
    // Guthaben darf dadurch nicht unter den Stand vorher fallen.
    fun rueckkauf(
        account: Account, key: String, qty: Double, preis: Double, now: Long, fee: Double = Trading.FEE,
        allowance: Double = 0.0, via: String = "",
    ): TradeResult {
        val pos = account.positions.firstOrNull { it.key == key } ?: return TradeResult(account, Err.NOT_IN_DEPOT)
        val d = pos.derivative
        if (d == null || !pos.verkauft) return TradeResult(account, Err.NOT_TRADABLE)
        if (!qty.isFinite() || qty <= 0.0) return TradeResult(account, Err.INVALID_QTY)
        if (qty > -pos.qty + 1e-9) return TradeResult(account, Err.NOT_ENOUGH_PIECES)
        if (!preis.isFinite() || preis <= 0.0) return TradeResult(account, Err.NO_PRICE)
        if (!fee.isFinite() || fee < 0) return TradeResult(account, Err.INVALID_FEE)
        val kosten = qty * preis
        if (!kosten.isFinite()) return TradeResult(account, Err.TOO_LARGE)
        // Posten als Betraege, aelteste zuerst
        val ab = Posten.abgang(Posten.von(pos).map { it.copy(qty = -it.qty) }, qty)
        val realized = ab.kosten - kosten
        val taxed = Tax.apply(account.tax, -(kosten + fee), Tax.Kind.GENERAL, allowance, now)
        if (account.cash - kosten - taxed.tax - fee < -0.005) return TradeResult(account, Err.NOT_ENOUGH_CASH)
        val rest = -pos.qty - qty
        val positions = if (rest <= 1e-9) account.positions.filter { it.key != key }
        else account.positions.map {
            if (it.key != key) it else it.copy(qty = -rest, avgPrice = Posten.einstand(ab.rest).takeIf { e -> e > 0 } ?: it.avgPrice, lots = ab.rest.map { l -> l.copy(qty = -l.qty) })
        }
        val tx = Transaction(
            id = Trading.newId("tx", now), ts = now, type = TxType.BUY, title = pos.name, amount = -kosten - taxed.tax,
            qty = qty, price = preis, symbol = pos.symbol, tax = taxed.tax, realized = realized, fee = fee,
            key = key, tag = TAG_RUECKKAUF, via = via, derivative = d,
        )
        var acc = account.copy(
            cash = Trading.kasse(account.cash - kosten - taxed.tax), positions = positions,
            transactions = Trading.kuerzen(account.transactions + tx), tax = taxed.state,
            orders = if (rest <= 1e-9) account.orders.map {
                if (it.key == key && it.status == OrderStatus.OPEN) it.copy(status = OrderStatus.CANCELLED, executedAt = now, reason = Err.PRODUCT_GONE) else it
            } else account.orders,
        )
        if (fee > 0) acc = Trading.addFee(acc, fee, now, pos.name, pos.symbol)
        acc = neuBinden(acc, d.gruppe) ?: acc
        if (acc.frei < minOf(0.0, account.frei) - 0.005) return TradeResult(account, Err.NOT_ENOUGH_CASH)
        return TradeResult(acc)
    }

    // Strategie in einem Zug anlegen, alles oder nichts: verkaufte Optionen zur Eroeffnung, gekaufte Optionen, das
    // Aktienbein. Verkaufte Calls decken zuerst freie Aktien im Depot, nur der Rest wird gekauft; ohne verkauften Call
    // wird das Aktienbein ganz gekauft. Leerverkauf von Aktien und ungedeckte Optionen werden abgelehnt.
    // gruppe: Kennung der Gruppe (steht schon in den Produkten der Beine), fx: Euro je Einheit der Handelswaehrung.
    fun eroeffnen(
        account: Account, beine: List<Bein>, gruppe: String, fx: Double, now: Long, fee: Double = Trading.FEE, allowance: Double = 0.0,
    ): TradeResult {
        if (beine.isEmpty() || beine.any { !it.stueck.isFinite() || it.stueck == 0.0 }) return TradeResult(account, Err.INVALID_QTY)
        if (beine.any { !it.preis.isFinite() || it.preis <= 0.0 }) return TradeResult(account, Err.NO_PRICE)
        val aktie = beine.filter { it.derivative == null }
        if (aktie.any { it.stueck < 0 }) return TradeResult(account, Err.LEERVERKAUF)
        val symbol = beine.first().symbol
        val opt = beine.mapNotNull { b -> b.derivative?.let { Option(b.stueck, it.kind == DerivativeKind.CALL, it.strike, it.ratio) } }
        val verkauft = opt.any { it.stueck < 0 }
        val aktienBein = aktie.sumOf { it.stueck }
        val bedarf = aktienBedarf(opt)
        val vorhanden = account.positions.firstOrNull { it.key == symbol && it.derivative == null }
        val frei = max(0.0, (vorhanden?.qty ?: 0.0) - account.gebundeneStueck(symbol))
        // Liegen die Aktien schon im Depot, werden sie nur gebunden
        val kaufen = if (bedarf > 0) max(0.0, aktienBein - frei) else aktienBein
        if (bedarf > frei + kaufen + 1e-9) return TradeResult(account, Err.UNGEDECKT)
        val zahlung = if (verkauft) hoechsteZahlung(opt, bedarf) ?: return TradeResult(account, Err.UNGEDECKT) else 0.0
        if (verkauft && !(fx.isFinite() && fx > 0)) return TradeResult(account, Err.NO_PRICE)

        var acc = account
        // Zuerst die Verkaeufe, die Praemie steht dann fuer die Kaeufe bereit
        for (b in beine) {
            val d = b.derivative ?: continue
            if (b.stueck > 0) continue
            val r = verkaufOeffnen(acc, b.key, b.name, b.symbol, -b.stueck, b.preis, d, now, fee, allowance)
            if (!r.ok) return TradeResult(account, r.error, extra = r.extra)
            acc = r.account
        }
        for (b in beine) {
            if (b.derivative == null || b.stueck < 0) continue
            val r = Trading.buy(acc, b.key, b.name, b.symbol, b.stueck, b.preis, b.derivative, now, fee)
            if (!r.ok) return TradeResult(account, r.error, extra = r.extra)
            acc = r.account
        }
        if (kaufen > 1e-9) {
            val b = aktie.first()
            val r = Trading.buy(acc, b.key, b.name, b.symbol, kaufen, b.preis, null, now, fee)
            if (!r.ok) return TradeResult(account, r.error, extra = r.extra)
            acc = r.account
        }
        if (verkauft) {
            val bis = beine.mapNotNull { it.derivative?.expiry }.maxOrNull() ?: 0L
            acc = acc.copy(bindungen = acc.bindungen + Bindung(gruppe, symbol, zahlung, fx, bedarf, bis))
        }
        if (acc.frei < -0.005) return TradeResult(account, Err.NOT_ENOUGH_CASH)
        return TradeResult(acc)
    }

    // Alle Beine einer Gruppe schliessen, alles oder nichts: zuerst die verkauften Optionen zurueckkaufen (die Bindung
    // faellt weg), dann die gekauften verkaufen. preis liefert je Position den Brief- (Rueckkauf) bzw. Geldkurs.
    fun gruppeSchliessen(
        account: Account, gruppe: String, preis: (Position) -> Double, now: Long, fee: Double = Trading.FEE, allowance: Double = 0.0,
    ): TradeResult {
        val beine = gruppe(account, gruppe).filter { !it.isKnockedOut }
        if (beine.isEmpty()) return TradeResult(account, Err.NOT_IN_DEPOT)
        var acc = account
        for (p in beine.sortedBy { !it.verkauft }) {
            val r = if (p.verkauft) rueckkauf(acc, p.key, -p.qty, preis(p), now, fee, allowance)
            else Trading.sell(acc, p.key, p.qty, preis(p), now, fee, allowance)
            if (!r.ok) return TradeResult(account, r.error, extra = r.extra)
            acc = r.account
        }
        return TradeResult(acc)
    }

    // Reicht das Guthaben nach dem Ausgleich einer verkauften Option bei Verfall nicht, werden Aktien des Basiswerts zum
    // Geldkurs verkauft, bis es wieder traegt (bei gedeckten Calls die eben frei gewordenen).
    // Die Steuer auf einen Kursgewinn kann wieder ein Loch reissen, daher hoechstens drei Runden.
    internal fun ausgleichen(account: Account, symbol: String, geldkurs: Double, now: Long, allowance: Double): Account {
        var acc = account
        repeat(3) {
            if (acc.cash >= 0.0 || !(geldkurs > 0.0)) return acc
            val pos = acc.positions.firstOrNull { it.key == symbol && it.derivative == null } ?: return acc
            val frei = pos.qty - acc.gebundeneStueck(symbol)
            if (frei <= 1e-9) return acc
            val menge = minOf(frei, (-acc.cash + Trading.FEE) / geldkurs)
            val r = Trading.sell(acc, pos.key, menge, geldkurs, now, allowance = allowance, via = VIA_AUSGLEICH)
            if (!r.ok) return acc
            acc = r.account
        }
        return acc
    }
}
