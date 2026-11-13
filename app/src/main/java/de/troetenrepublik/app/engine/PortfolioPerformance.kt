package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

// Buchungen fuer Portfolio Performance (https://www.portfolio-performance.info) als CSV-Datei fuer Datei > Importieren >
// CSV-Dateien mit der Art „Kontoumsätze“. Spalten und Buchungsarten heissen wie in Portfolio Performance (deutsch oder
// englisch wie die App), damit der Import sie ohne Zuordnung erkennt. Deutsch mit Semikolon und Dezimalkomma, Englisch
// mit Komma und Dezimalpunkt, wie Portfolio Performance es dort vorschlaegt.
// Einzahlungen, Auszahlungen und Wetten werden Einlagen und Entnahmen; Wetten haben kein Wertpapier. Die Gebuehr einer
// Order ist in der App eine eigene Buchung zur selben Zeit und steht hier in „Gebühren“; „Wert“ ist der Betrag auf dem
// Konto. Knock-out und Verfall sind Verkaeufe zum Restwert, auch zu null. Uebernommene Positionen (Import ohne Geld)
// werden Einlage und Kauf zum Einstand, damit Bestand und Konto stimmen. Das Wertpapier erkennt Portfolio Performance an
// ISIN und Yahoo-Kuerzel; Derivate haben keine ISIN und stehen mit ihrem Namen da. Verkaufte Optionen (0.77) stehen als
// Verkauf zur Eroeffnung mit negativem Bestand da, Rueckkauf und Verfall als Kauf; Steuern darauf als eigene Zeile.
object PortfolioPerformance {

    private val SPALTEN_DE = listOf("Datum", "Uhrzeit", "Typ", "Wert", "Buchungswährung", "Stück", "ISIN", "Ticker-Symbol", "Wertpapiername", "Gebühren", "Steuern", "Notiz")
    private val SPALTEN_EN = listOf("Date", "Time", "Type", "Value", "Transaction Currency", "Shares", "ISIN", "Ticker Symbol", "Security Name", "Fees", "Taxes", "Note")

    private enum class Typ(val de: String, val en: String) {
        EINLAGE("Einlage", "Deposit"),
        ENTNAHME("Entnahme", "Withdrawal"),
        KAUF("Kauf", "Buy"),
        VERKAUF("Verkauf", "Sell"),
        DIVIDENDE("Dividende", "Dividend"),
        ZINSEN("Zinsen", "Interest"),
        GEBUEHREN("Gebühren", "Fees"),
        ZINSBELASTUNG("Zinsbelastung", "Interest Charge"),
        GEBUEHRENERSTATTUNG("Gebührenerstattung", "Fees Refund"),
        STEUERN("Steuern", "Taxes"),
        STEUERERSTATTUNG("Steuerrückerstattung", "Tax Refund"),
    }

    private class Zeile(
        val ts: Long, val typ: Typ, val wert: Double, val stueck: Double? = null, val isin: String = "", val ticker: String = "",
        val name: String = "", val gebuehr: Double = 0.0, val steuer: Double = 0.0, val notiz: String = "",
    )

    // Anzahl der Zeilen, die csv fuer account schreiben wuerde (ohne Kopfzeile).
    fun anzahl(account: Account): Int = zeilen(account, { "" }).size

    // Die CSV-Datei. isin liefert die ISIN eines Symbols (leer, wenn unbekannt); deutsch waehlt Spalten, Buchungsarten
    // und Zahlenformat; zone die Zeitzone fuer Datum und Uhrzeit.
    fun csv(account: Account, isin: (String) -> String, deutsch: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val trenner = if (deutsch) ';' else ','
        fun feld(s: String): String =
            if (s.any { it == trenner || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        fun zahl(v: Double, stellen: Int): String {
            val s = java.math.BigDecimal.valueOf(v).setScale(stellen, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            return if (deutsch) s.replace('.', ',') else s
        }
        val sb = StringBuilder()
        sb.append((if (deutsch) SPALTEN_DE else SPALTEN_EN).joinToString(trenner.toString())).append("\r\n")
        for (z in zeilen(account, isin)) {
            val zeit = Instant.ofEpochMilli(z.ts).atZone(zone)
            sb.append(listOf(
                zeit.toLocalDate().toString(),
                zeit.hour.toString().padStart(2, '0') + ":" + zeit.minute.toString().padStart(2, '0'),
                if (deutsch) z.typ.de else z.typ.en,
                zahl(abs(z.wert), 2),
                "EUR",
                z.stueck?.let { zahl(abs(it), 8) }.orEmpty(),
                z.isin,
                z.ticker,
                z.name,
                if (z.gebuehr != 0.0) zahl(abs(z.gebuehr), 2) else "",
                if (z.steuer != 0.0) zahl(abs(z.steuer), 2) else "",
                z.notiz,
            ).joinToString(trenner.toString()) { feld(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun zeilen(account: Account, isin: (String) -> String): List<Zeile> {
        val txs = account.transactions.sortedBy { it.ts }
        // Gebuehren gehoeren zur Order zur selben Zeit (das Symbol steht in ihrer Notiz)
        val gebuehren = HashMap<String, Transaction>()
        txs.filter { it.type == TxType.FEE && it.note.isNotBlank() }.forEach { gebuehren.putIfAbsent("${it.ts}|${it.note}", it) }
        val verbraucht = HashSet<String>()
        // Gebuehren zur Zeit eines Handels, die kein Symbol nennen oder ein anderes (Importe nennen die ISIN oder das Kuerzel
        // des Brokers): gehoeren zur Order, wenn zu der Zeit nur ein Handel steht.
        val handelJeZeit = txs.filter { (it.type == TxType.BUY || it.type == TxType.SELL) && it.qty > 0 }.groupBy { it.ts }
        val gebuehrenJeZeit = txs.filter { it.type == TxType.FEE && it.amount != 0.0 }.groupBy { it.ts }
        fun gebuehrZu(t: Transaction): Double {
            for (k in listOf("${t.ts}|${t.key}", "${t.ts}|${t.symbol}", "${t.ts}|${isin(t.symbol)}")) {
                val f = gebuehren[k] ?: continue
                if (verbraucht.add(f.id)) return abs(f.amount)
            }
            if (handelJeZeit[t.ts]?.size == 1) {
                val f = gebuehrenJeZeit[t.ts]?.singleOrNull()
                if (f != null && f.id !in verbraucht && f.note.isNotBlank() && verbraucht.add(f.id)) return abs(f.amount)
            }
            return 0.0
        }
        fun wertpapier(t: Transaction): Triple<String, String, String> {
            val derivat = t.derivative != null || (t.key.isNotBlank() && t.key != t.symbol)
            val ohneKurs = t.symbol.startsWith(TradeRepublicImport.OHNE_KURS)
            val name = t.title.ifBlank { t.symbol }
            if (derivat || ohneKurs) return Triple("", "", name)
            return Triple(isin(t.symbol), t.symbol, name)
        }

        // Steuer einer Buchung als eigene Zeile (Rueckkauf und Verfall verkaufter Optionen), null ohne Steuer.
        fun steuerZeile(t: Transaction, name: String): Zeile? =
            if (abs(t.tax) < 0.005) null else Zeile(t.ts, if (t.tax < 0) Typ.STEUERERSTATTUNG else Typ.STEUERN, -t.tax, notiz = name)

        val aus = ArrayList<Zeile>()
        for (t in txs) {
            when (t.type) {
                TxType.DEPOSIT -> if (t.amount != 0.0) aus += Zeile(t.ts, Typ.EINLAGE, t.amount, notiz = t.note)
                TxType.WITHDRAWAL -> if (t.amount != 0.0) aus += Zeile(t.ts, Typ.ENTNAHME, t.amount, notiz = t.note)
                TxType.BET -> if (t.amount != 0.0) aus += Zeile(t.ts, if (t.amount > 0) Typ.EINLAGE else Typ.ENTNAHME, t.amount, notiz = listOf(t.title, t.subtitle).filter { it.isNotBlank() }.joinToString(" · "))
                TxType.BUY -> {
                    if (t.qty <= 0) continue
                    val (i, ticker, name) = wertpapier(t)
                    if (t.verkaufteOption) {
                        // Rueckkauf einer verkauften Option: Kauf zum Kurswert samt Gebuehr, die Steuererstattung eigens
                        val g = gebuehrZu(t)
                        aus += Zeile(t.ts, Typ.KAUF, t.qty * t.price + g, t.qty, i, ticker, name, gebuehr = g, notiz = t.note)
                        steuerZeile(t, name)?.let { aus += it }
                    } else if (t.tag == "TRANSFER_IN" || t.amount == 0.0) {
                        // Stuecke ohne Geld (Uebertrag, Split, Bonusaktien): ohne Einstand nur der Kauf zu null, damit der Bestand stimmt
                        val einstand = t.qty * t.price
                        if (einstand > 0) aus += Zeile(t.ts, Typ.EINLAGE, einstand, notiz = name)
                        aus += Zeile(t.ts, Typ.KAUF, einstand, t.qty, i, ticker, name, notiz = t.note)
                    } else {
                        val g = gebuehrZu(t)
                        aus += Zeile(t.ts, Typ.KAUF, abs(t.amount) + g, t.qty, i, ticker, name, gebuehr = g, notiz = t.note)
                    }
                }
                TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY -> {
                    if (t.qty <= 0) continue
                    val (i, ticker, name) = wertpapier(t)
                    if (t.verkaufteOption && t.type != TxType.SELL) {
                        // Verfall einer verkauften Option: Kauf zum Ausgleich (auch zu null), damit der Bestand auf null kommt
                        aus += Zeile(t.ts, Typ.KAUF, t.qty * t.price, t.qty, i, ticker, name, notiz = t.note)
                        steuerZeile(t, name)?.let { aus += it }
                        continue
                    }
                    val g = if (t.type == TxType.SELL) gebuehrZu(t) else 0.0
                    aus += Zeile(t.ts, Typ.VERKAUF, (t.amount - g).coerceAtLeast(0.0), t.qty, i, ticker, name, gebuehr = g, steuer = t.tax, notiz = t.note)
                }
                TxType.DIVIDEND -> {
                    if (t.amount == 0.0 && t.tax == 0.0) continue
                    val (i, ticker, name) = wertpapier(t)
                    // Eine Korrekturbuchung mit negativem Betrag kennt Portfolio Performance nur als Steuern zum Wertpapier
                    if (t.amount < 0) { aus += Zeile(t.ts, Typ.STEUERN, t.amount, null, i, ticker, name, notiz = t.note); continue }
                    aus += Zeile(t.ts, Typ.DIVIDENDE, t.amount, t.qty.takeIf { it > 0 }, i, ticker, name, steuer = t.tax, notiz = t.note)
                }
                TxType.INTEREST -> if (t.amount != 0.0) aus += Zeile(t.ts, if (t.amount > 0) Typ.ZINSEN else Typ.ZINSBELASTUNG, t.amount, steuer = t.tax, notiz = t.note)
                TxType.TAX -> if (t.amount != 0.0) aus += Zeile(t.ts, if (t.amount > 0) Typ.STEUERERSTATTUNG else Typ.STEUERN, t.amount, notiz = t.title)
                TxType.FEE -> Unit
                TxType.ALERT, TxType.PLAN -> Unit
                // Splits bucht Portfolio Performance selbst (Kapitalmassnahme am Wertpapier), die CSV kennt sie nicht
                TxType.SPLIT -> Unit
            }
        }
        // Gebuehren ohne passende Order
        txs.filter { it.type == TxType.FEE && it.amount != 0.0 && it.id !in verbraucht }.forEach { f ->
            aus += Zeile(f.ts, if (f.amount > 0) Typ.GEBUEHRENERSTATTUNG else Typ.GEBUEHREN, f.amount, notiz = f.note)
        }
        return aus.sortedBy { it.ts }
    }
}
