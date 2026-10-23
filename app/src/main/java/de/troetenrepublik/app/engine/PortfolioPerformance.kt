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
// ISIN und Yahoo-Kuerzel; Derivate haben keine ISIN und stehen mit ihrem Namen da.
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
                z.stueck?.let { zahl(abs(it), 6) }.orEmpty(),
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
        fun gebuehrZu(t: Transaction): Double {
            for (k in listOf("${t.ts}|${t.key}", "${t.ts}|${t.symbol}")) {
                val f = gebuehren[k] ?: continue
                if (verbraucht.add(f.id)) return abs(f.amount)
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

        val aus = ArrayList<Zeile>()
        for (t in txs) {
            when (t.type) {
                TxType.DEPOSIT -> if (t.amount != 0.0) aus += Zeile(t.ts, Typ.EINLAGE, t.amount, notiz = t.note)
                TxType.WITHDRAWAL -> if (t.amount != 0.0) aus += Zeile(t.ts, Typ.ENTNAHME, t.amount, notiz = t.note)
                TxType.BET -> if (t.amount != 0.0) aus += Zeile(t.ts, if (t.amount > 0) Typ.EINLAGE else Typ.ENTNAHME, t.amount, notiz = listOf(t.title, t.subtitle).filter { it.isNotBlank() }.joinToString(" · "))
                TxType.BUY -> {
                    if (t.qty <= 0) continue
                    val (i, ticker, name) = wertpapier(t)
                    if (t.tag == "TRANSFER_IN" || t.amount == 0.0) {
                        val einstand = t.qty * t.price
                        if (einstand <= 0) continue
                        aus += Zeile(t.ts, Typ.EINLAGE, einstand, notiz = name)
                        aus += Zeile(t.ts, Typ.KAUF, einstand, t.qty, i, ticker, name, notiz = t.note)
                    } else {
                        val g = gebuehrZu(t)
                        aus += Zeile(t.ts, Typ.KAUF, abs(t.amount) + g, t.qty, i, ticker, name, gebuehr = g, notiz = t.note)
                    }
                }
                TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY -> {
                    if (t.qty <= 0) continue
                    val (i, ticker, name) = wertpapier(t)
                    val g = if (t.type == TxType.SELL) gebuehrZu(t) else 0.0
                    aus += Zeile(t.ts, Typ.VERKAUF, (t.amount - g).coerceAtLeast(0.0), t.qty, i, ticker, name, gebuehr = g, steuer = t.tax, notiz = t.note)
                }
                TxType.DIVIDEND -> {
                    if (t.amount == 0.0 && t.tax == 0.0) continue
                    val (i, ticker, name) = wertpapier(t)
                    aus += Zeile(t.ts, Typ.DIVIDENDE, t.amount, t.qty.takeIf { it > 0 }, i, ticker, name, steuer = t.tax, notiz = t.note)
                }
                TxType.INTEREST -> if (t.amount != 0.0) aus += Zeile(t.ts, Typ.ZINSEN, t.amount, steuer = t.tax, notiz = t.note)
                TxType.TAX -> if (t.amount != 0.0) aus += Zeile(t.ts, if (t.amount > 0) Typ.STEUERERSTATTUNG else Typ.STEUERN, t.amount, notiz = t.title)
                TxType.FEE -> Unit
                TxType.ALERT, TxType.PLAN -> Unit
            }
        }
        // Gebuehren ohne passende Order
        txs.filter { it.type == TxType.FEE && it.amount != 0.0 && it.id !in verbraucht }.forEach { f ->
            aus += Zeile(f.ts, if (f.amount > 0) Typ.GEBUEHRENERSTATTUNG else Typ.GEBUEHREN, f.amount, notiz = f.note)
        }
        return aus.sortedBy { it.ts }
    }
}
