package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.isFactor
import de.troetenrepublik.app.data.isKnockout
import de.troetenrepublik.app.market.Symbols
import kotlin.math.max
import kotlin.math.min

// Holt nach einer Pause der App nach, was in der Zwischenzeit faellig war: Limit-, Stopp- und Trailing-Orders, Sparplaene
// und die monatliche Einzahlung. Gerechnet wird Kerze fuer Kerze in zeitlicher Folge ueber alle Werte, damit Guthaben
// und Bestand zu jedem Zeitpunkt stimmen; die Buchungen tragen den Zeitpunkt der Kerze. Wo eine Kerze die Reihenfolge
// der Kurse nicht verraet, gilt die fuer den Nutzer unguenstigere. Fuer einen Wert ohne Kerzen aendert sich nichts;
// was danach noch offen ist, erledigen runOrders und runPlans mit dem aktuellen Kurs.
object Nachholen {

    // Kuerzere Pausen deckt der laufende Takt ab.
    const val MIN_LUECKE_MS = 15 * 60_000L

    private const val TAG_MS = 86_400_000L
    private const val STUNDE_MS = 3_600_000L
    private const val NIE = Long.MAX_VALUE

    // Kerzen eines Werts in EUR, aufsteigend. tage: Tageskerzen; sie zaehlen mit Tageshoch und -tief zum Handelsschluss.
    data class Reihe(val kerzen: List<Candle>, val tage: Boolean = false)

    // Werte mit offener Limit-, Stopp- oder Trailing-Order oder laufendem Sparplan.
    fun symbole(account: Account): List<String> =
        (account.orders.filter { it.status == OrderStatus.OPEN && it.type != OrderType.MARKET }.map { it.symbol } +
            account.plans.filter { !it.paused }.map { it.symbol }).filter { it.isNotBlank() }.distinct()

    // Ohne gemerkten letzten Lauf (erster Start mit dieser Fassung) wird nichts nachgeholt.
    fun noetig(account: Account, now: Long): Boolean =
        account.letzterLauf > 0L && now - account.letzterLauf >= MIN_LUECKE_MS && symbole(account).isNotEmpty()

    // Feinster Kerzenabstand, den die Kursquelle fuer die ganze Pause liefert: 5 Minuten fuer knapp 60 Tage, Stunden
    // fuer knapp zwei Jahre, sonst Tage.
    fun intervall(seit: Long, now: Long): String = when {
        now - seit <= 55 * TAG_MS -> "5m"
        now - seit <= 700 * TAG_MS -> "1h"
        else -> "1d"
    }

    // Ob die gelieferten Kerzen Tageskerzen sind (manche Quellen kennen nur diese), am mittleren Abstand gemessen.
    fun tageskerzen(kerzen: List<Candle>, intervall: String): Boolean {
        if (kerzen.size < 3) return intervall == "1d"
        val abstaende = kerzen.zipWithNext { a, b -> b.t - a.t }.sorted()
        return abstaende[abstaende.size / 2] >= 20 * STUNDE_MS
    }

    // kerze null: monatliche Einzahlung des Monats monat.
    private class Ereignis(val ts: Long, val symbol: String, val kerze: Candle?, val tage: Boolean, val monat: Int)

    // Offener Termin eines Sparplans. vermerkt: der Fehlschlag steht schon in den Buchungen; folge: der Termin danach
    // (0 = noch nicht bestimmt).
    private class PlanStand(val termin: Long, var folge: Long = 0L, var vermerkt: Boolean = false)

    // seit: letzter Lauf des Takts. reihen: Kerzen je Wert, nur fuer Werte, die nachgeholt werden sollen. fx: Euro je
    // Einheit der Handelswaehrung je Basiswert, fuer Basispreis und Schwelle der Hebelprodukte.
    fun ausfuehren(
        account: Account,
        seit: Long,
        now: Long,
        reihen: Map<String, Reihe>,
        spread: Trading.Spread = Trading.Spread(),
        allowance: Double = 0.0,
        rate: Double = Pricing.RATE,
        fx: Map<String, Double> = emptyMap(),
        respectHours: Boolean = true,
    ): Account {
        if (seit <= 0L || now - seit < MIN_LUECKE_MS) return account
        val betroffen = symbole(account).toSet()
        val zone = MarketHours.zone
        fun tagVon(ts: Long): java.time.LocalDate = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val ersterTag = tagVon(seit)
        val heute = tagVon(now)

        val ereignisse = ArrayList<Ereignis>()
        for ((symbol, reihe) in reihen) {
            if (symbol !in betroffen) continue
            val fenster = MarketHours.windowFor(Symbols.typeOf(symbol), false)
            for (k in reihe.kerzen) {
                if (!(k.open > 0.0 && k.high > 0.0 && k.low > 0.0 && k.close > 0.0) || !(k.open + k.high + k.low + k.close).isFinite()) continue
                if (!reihe.tage) {
                    // Eine Kerze, die vor dem letzten Lauf begann, enthaelt Kurse, die der Takt schon gesehen hat
                    if (k.t in seit..now) ereignisse += Ereignis(k.t, symbol, k, false, 0)
                } else {
                    // Nur volle Tage der Pause; der heutige bleibt dem aktuellen Kurs ueberlassen
                    val tag = tagVon(k.t)
                    if (!tag.isAfter(ersterTag) || !tag.isBefore(heute)) continue
                    val schluss = if (fenster != null) tag.atTime(MarketHours.schlussAm(fenster, tag)).atZone(zone).toInstant().toEpochMilli()
                        else tag.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    ereignisse += Ereignis(schluss - 1, symbol, k, true, 0)
                }
            }
        }
        if (ereignisse.isEmpty()) return account

        // Einzahlungen, deren Tag in der Pause lag, zum Beginn dieses Tages: so stehen sie vor dem Sparplan desselben Tages
        account.einzahlplan?.let { plan ->
            val termine = Einzahlplaene.faelligeMonate(plan, now).map { it to Einzahlplaene.termin(plan.tag, it) }
            if (termine.all { it.second in (seit + 1)..now }) termine.forEach { (m, t) -> ereignisse += Ereignis(t, "", null, false, m) }
        }
        ereignisse.sortBy { it.ts }

        var acc = account
        val gesperrt = HashSet<String>()
        val planStaende = HashMap<String, PlanStand>()
        var i = 0
        while (i < ereignisse.size) {
            val ts = ereignisse[i].ts
            var j = i
            while (j < ereignisse.size && ereignisse[j].ts == ts) j++
            val gruppe = ereignisse.subList(i, j)
            i = j
            acc = verfallen(acc, ts)
            for (e in gruppe) if (e.kerze == null) acc = Einzahlplaene.buchen(acc, e.monat, ts)
            // Im selben Augenblick zuerst alles, was Guthaben braucht: ein Verkauf bezahlt keinen Kauf derselben Kerze
            for (e in gruppe) {
                val k = e.kerze ?: continue
                acc = plaene(acc, e, k, now, spread, planStaende)
                acc = orders(acc, e, k, Side.BUY, spread, allowance, rate, fx[e.symbol] ?: 1.0, respectHours, gesperrt)
            }
            for (e in gruppe) {
                val k = e.kerze ?: continue
                acc = orders(acc, e, k, Side.SELL, spread, allowance, rate, fx[e.symbol] ?: 1.0, respectHours, gesperrt)
            }
        }
        return verfallen(acc, now)
    }

    // Abgelaufene Orders verfallen zu ihrem Ablauf, nicht zum Zeitpunkt des Nachholens.
    private fun verfallen(account: Account, bis: Long): Account {
        if (account.orders.none { it.status == OrderStatus.OPEN && it.expiresAt in 1..bis }) return account
        return account.copy(orders = account.orders.map {
            if (it.status == OrderStatus.OPEN && it.expiresAt in 1..bis) it.copy(status = OrderStatus.EXPIRED, executedAt = it.expiresAt, reason = Err.EXPIRED)
            else it
        })
    }

    private fun terminOderNie(plan: SavingsPlan, now: Long): Long = Trading.planTermin(plan, now).takeIf { it > 0L } ?: NIE

    // Jeder Termin eines Sparplans einzeln, zum Briefkurs der ersten Kerze ab dem Termin (Tageskerze: Schlusskurs).
    // Reicht das Guthaben nicht, steht ein Vermerk je Termin in den Buchungen; der Kauf wird bis zum naechsten Termin mit
    // jeder Kerze erneut versucht, wie es der laufende Takt tut.
    private fun plaene(konto: Account, e: Ereignis, k: Candle, now: Long, spread: Trading.Spread, staende: HashMap<String, PlanStand>): Account {
        var acc = konto
        for (p in konto.plans) {
            if (p.symbol != e.symbol || p.paused) continue
            var plan = p
            var stand = staende.getOrPut(plan.id) { PlanStand(terminOderNie(plan, now)) }
            if (stand.termin > e.ts) continue
            if (stand.vermerkt) {
                if (stand.folge == 0L) stand.folge = terminOderNie(plan.copy(lastRun = stand.termin), now)
                if (stand.folge <= e.ts) {
                    // Der naechste Termin ist da: der gescheiterte gilt als vorbei
                    plan = plan.copy(lastRun = stand.termin)
                    val neu = plan
                    acc = acc.copy(plans = acc.plans.map { if (it.id == neu.id) neu else it })
                    stand = PlanStand(stand.folge)
                    staende[plan.id] = stand
                }
            }
            val preis = Pricing.ask(if (e.tage) k.close else k.open, spread.stockBps)
            val res = Trading.buy(acc, plan.symbol, plan.name, plan.symbol, plan.amount / preis, preis, null, e.ts, fee = 0.0, via = "PLAN")
            val id = plan.id
            val betrag = plan.amount
            if (res.ok) {
                acc = res.account.copy(plans = res.account.plans.map {
                    if (it.id == id) it.copy(lastRun = e.ts, invested = it.invested + betrag, executions = it.executions + 1, lastFailed = 0L) else it
                })
                staende.remove(id)
            } else if (!stand.vermerkt) {
                stand.vermerkt = true
                val tx = Transaction(
                    id = Trading.newId("plan", e.ts), ts = e.ts, type = TxType.PLAN,
                    title = plan.name, symbol = plan.symbol, tag = "PLAN_FAILED", note = res.error ?: Err.NOT_ENOUGH_CASH, extra = betrag,
                )
                acc = acc.copy(
                    plans = acc.plans.map { if (it.id == id) it.copy(lastFailed = e.ts) else it },
                    transactions = Trading.kuerzen(acc.transactions + tx),
                )
            }
        }
        return acc
    }

    // Kurs des Produkts zu Eroeffnung, Tief und Hoch der Kerze. null, wenn er sich aus der Kerze nicht sicher ergibt:
    // verkaufte Optionen, Knock-out in der Kerze, Verfall, Faktor-Zertifikate ab dem naechsten Tageswechsel oder bei
    // einem Reset. Die Order bleibt dann fuer den Rest der Pause unberuehrt.
    private fun produktKerze(d: Derivative?, k: Candle, ts: Long, fx: Double, rate: Double): Triple<Double, Double, Double>? {
        val hoch = maxOf(k.high, k.open, k.close)
        val tief = minOf(k.low, k.open, k.close)
        if (d == null) return Triple(k.open, tief, hoch)
        if (d.isKnockedOut || d.verkauft || d.expiry in 1..ts + STUNDE_MS) return null
        if (d.kind.isKnockout && (Pricing.isKnockedOut(d, tief, fx) || Pricing.isKnockedOut(d, hoch, fx))) return null
        if (d.kind.isFactor) {
            if (d.factorRef <= 0.0 || d.factorRefTag <= 0L || ts / TAG_MS != d.factorRefTag) return null
            val schlecht = if (d.factor > 0) tief else hoch
            if (d.factor * (schlecht / d.factorRef - 1.0) <= -(1.0 - Pricing.RESET_REST)) return null
        }
        val auf = Pricing.price(d, k.open, ts, fx, rate)
        val a = Pricing.price(d, tief, ts, fx, rate)
        val b = Pricing.price(d, hoch, ts, fx, rate)
        if (!(auf > 0.0 && a > 0.0 && b > 0.0) || !(auf + a + b).isFinite()) return null
        // Short-Produkte und Puts stehen hoch, wenn der Basiswert tief steht
        return Triple(auf, min(a, b), max(a, b))
    }

    // Offene Orders einer Seite auf den Wert der Kerze. Limit: zum Limit, bei einer Eroeffnung jenseits davon zur
    // Eroeffnung. Stopp: zum Stopp, bei einer Kurslucke zur Eroeffnung. Trailing-Stopp: erst der bisherige Stopp gegen das
    // unguenstige Extrem, dann nachziehen mit dem guenstigen; die Eroeffnung zaehlt vorab, weil sie sicher zuerst kam.
    private fun orders(
        konto: Account, e: Ereignis, k: Candle, seite: Side, spread: Trading.Spread, allowance: Double, rate: Double,
        fx: Double, respectHours: Boolean, gesperrt: HashSet<String>,
    ): Account {
        var acc = konto
        val kauf = seite == Side.BUY
        for (o0 in konto.orders) {
            if (o0.status != OrderStatus.OPEN || o0.symbol != e.symbol || o0.side != seite || o0.type == OrderType.MARKET || o0.id in gesperrt) continue
            val o = acc.orders.firstOrNull { it.id == o0.id && it.status == OrderStatus.OPEN } ?: continue
            // Nur Kurse nach der Aufgabe der Order
            if (if (e.tage) MarketHours.dayStart(e.ts) <= o.createdAt else k.t < o.createdAt) continue
            // Bei gehaltenen Hebelprodukten gilt der Stand der Position, nicht der bei Orderaufgabe
            val deriv = acc.positions.firstOrNull { it.key == o.key }?.derivative ?: o.derivative
            val produkt = produktKerze(deriv, k, e.ts, fx, rate)
            if (produkt == null) { gesperrt += o.id; continue }
            val (auf, tief, hoch) = produkt
            val bps = spread.forProduct(deriv)
            fun mitSpanne(mitte: Double) = Pricing.withSpread(mitte, bps, kauf)
            val handel = !respectHours || MarketHours.isOpen(Symbols.typeOf(o.symbol), deriv != null, e.ts)
            // Ausfuehrungskurs; 0 = nicht ausgeloest
            var kurs = 0.0
            when (o.type) {
                OrderType.LIMIT -> if (handel && !OrderPaar.stoppGehtVor(acc.orders, o, tief)) {
                    if (kauf) { if (mitSpanne(tief) <= o.limitPrice) kurs = min(o.limitPrice, mitSpanne(auf)) }
                    else if (mitSpanne(hoch) >= o.limitPrice) kurs = max(o.limitPrice, mitSpanne(auf))
                }
                OrderType.STOP -> if (handel) {
                    if (kauf) { if (hoch >= o.limitPrice) kurs = mitSpanne(max(o.limitPrice, auf)) }
                    else if (tief <= o.limitPrice) kurs = mitSpanne(min(o.limitPrice, auf))
                }
                OrderType.TRAILING_STOP -> {
                    val faktor = if (kauf) 1.0 + o.trailPct / 100.0 else 1.0 - o.trailPct / 100.0
                    val bezug = when {
                        o.trailRef <= 0 -> auf
                        kauf -> min(o.trailRef, auf)
                        else -> max(o.trailRef, auf)
                    }
                    val stopp = bezug * faktor
                    val erreicht = handel && (if (kauf) hoch >= stopp else tief <= stopp)
                    val neu = if (erreicht) bezug else if (kauf) min(bezug, tief) else max(bezug, hoch)
                    if (neu != o.trailRef || neu * faktor != o.limitPrice) {
                        acc = acc.copy(orders = acc.orders.map { if (it.id == o.id) it.copy(trailRef = neu, limitPrice = neu * faktor) else it })
                    }
                    if (erreicht) kurs = mitSpanne(if (kauf) max(stopp, auf) else min(stopp, auf))
                }
                OrderType.MARKET -> {}
            }
            if (!(kurs > 0.0) || !kurs.isFinite()) continue

            val res = if (kauf) Trading.buy(acc, o.key, o.name, o.symbol, o.qty, kurs, deriv, e.ts, note = o.note, via = Trading.VIA_ORDER)
                else Trading.sell(acc, o.key, o.qty, kurs, e.ts, allowance = allowance, note = o.note, via = Trading.VIA_ORDER)
            acc = if (res.ok) {
                OrderPaar.partnerStreichen(res.account.copy(orders = res.account.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.FILLED, executedAt = e.ts, executedPrice = kurs) else it
                }), o.id, e.ts)
            } else {
                acc.copy(orders = acc.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.REJECTED, executedAt = e.ts, reason = res.error ?: "") else it
                })
            }
        }
        return acc
    }
}
