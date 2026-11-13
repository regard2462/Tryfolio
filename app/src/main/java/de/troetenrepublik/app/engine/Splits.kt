package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import kotlin.math.abs

// Aktiensplits im Uebungsdepot. Yahoo rechnet alte Kurse und Dividenden rueckwirkend auf die neue Stueckzahl um;
// damit Depotwert, Einstand, Auftraege und Alarme dazu passen, geht ein Split auf alles, was am Wert haengt:
// Stueckzahl mal Verhaeltnis, Einstand, Limit- und Stoppkurse, Alarmmarken und Watchlist-Kurse durch das Verhaeltnis.
// Derivate auf den Wert bekommen wie bei echten Emittenten angepasste Basispreise, Schwellen und
// Bezugsverhaeltnisse, ihr Preis bleibt gleich. Es zaehlt nur, was vor dem Split bestand; uebernommene Positionen
// erst ab der Uebernahme, weil der Broker den Split dort schon gebucht hat. Jeder Split wird einmal gebucht
// (Buchung SPLIT am Splittag), ein zweiter Aufruf aendert nichts.
object Splits {
    private const val TAG = 86_400_000L

    // t Beginn des Splittags; zaehler:nenner wie bei Yahoo, 10:1 heisst aus einer Aktie werden zehn.
    data class Split(val t: Long, val zaehler: Double, val nenner: Double) {
        val verhaeltnis: Double get() = zaehler / nenner
        val text: String get() = "${zahl(zaehler)}:${zahl(nenner)}"

        private fun zahl(x: Double) = if (x == Math.rint(x)) x.toLong().toString() else x.toString()
    }

    fun gebucht(account: Account, symbol: String, s: Split): Boolean =
        account.transactions.any { it.type == TxType.SPLIT && it.symbol == symbol && it.ts / TAG == s.t / TAG }

    fun anwenden(account: Account, symbol: String, name: String, s: Split, txId: String): Account {
        val r = s.verhaeltnis
        if (!r.isFinite() || r <= 0.0 || abs(r - 1.0) < 1e-9 || gebucht(account, symbol, s)) return account
        var betroffen = false

        val positionen = account.positions.map { p ->
            val d = p.derivative
            when {
                d == null && p.key == symbol && p.openedAt < s.t && (p.importedAt < s.t || account.transactions.any { it.posKey == symbol && it.importedAt > 0 }) -> {
                    // Stueck, die erst nach dem Split dazukamen, sind schon in neuen Einheiten; ebenso Uebernahmen nach dem
                    // Split, deren Buchung das alte Kaufdatum traegt (importedAt)
                    val nachSplitUebernommen = account.transactions
                        .filter { it.posKey == symbol && it.type == TxType.BUY && it.importedAt >= s.t && it.ts < s.t }.sumOf { it.qty }
                    val danach = account.transactions.filter { it.posKey == symbol && it.ts >= s.t }
                        .sumOf { it.bestandsDelta } + nachSplitUebernommen
                    val alt = (p.qty - danach).coerceAtLeast(0.0)
                    val qty = alt * r + (p.qty - alt)
                    betroffen = true
                    // Posten tragen die Uebernahmezeit nicht: bei gemischtem Bestand gilt der Einstand als ein Posten
                    val posten = if (p.lots.isEmpty() || nachSplitUebernommen > 0) emptyList() else Posten.split(p.lots, r, s.t)
                    if (qty > 0) p.copy(qty = qty, avgPrice = p.invested / qty, lots = posten) else p
                }
                d != null && d.underlying == symbol && d.createdAt < s.t -> { betroffen = true; p.copy(derivative = derivat(d, r)) }
                else -> p
            }
        }
        val orders = account.orders.map { o ->
            val d = o.derivative
            when {
                o.status != OrderStatus.OPEN || o.createdAt >= s.t -> o
                d == null && o.key == symbol -> { betroffen = true; o.copy(qty = o.qty * r, limitPrice = o.limitPrice / r, trailRef = o.trailRef / r) }
                d != null && d.underlying == symbol -> { betroffen = true; o.copy(derivative = derivat(d, r)) }
                else -> o
            }
        }
        val alarme = account.alerts.map { a ->
            if (a.symbol != symbol || a.createdAt >= s.t) a
            else { betroffen = true; a.copy(target = a.target / r, referencePrice = a.referencePrice / r) }
        }
        val watch = account.watchMeta[symbol]?.let { m ->
            val neu = m.copy(
                price = if (m.addedAt < s.t) m.price / r else m.price,
                firstPrice = if (m.firstAddedAt in 1 until s.t) m.firstPrice / r else m.firstPrice,
            )
            if (neu != m) betroffen = true
            account.watchMeta + (symbol to neu)
        } ?: account.watchMeta
        if (!betroffen) return account

        val pos = positionen.firstOrNull { it.key == symbol && it.derivative == null }
        val tx = Transaction(
            id = txId, ts = s.t, type = TxType.SPLIT, title = name, subtitle = s.text, qty = pos?.qty ?: 0.0,
            symbol = symbol, key = symbol, tag = "SPLIT", extra = r,
        )
        // Aktien, die verkaufte Calls decken, in neuen Einheiten
        return StillhalterDepot.split(
            account.copy(positions = positionen, orders = orders, alerts = alarme, watchMeta = watch, transactions = account.transactions + tx), symbol, r,
        )
    }

    // Wert eines Derivats bleibt gleich: Basispreis und Schwelle durch r, Bezugsverhaeltnis durch r (ein Zertifikat
    // bezieht sich danach auf r-mal so viele neue Aktien), Referenzkurs der Faktor-Zertifikate durch r.
    private fun derivat(d: Derivative, r: Double): Derivative =
        d.copy(strike = d.strike / r, barrier = d.barrier / r, ratio = d.ratio / r, factorRef = d.factorRef / r)
}
