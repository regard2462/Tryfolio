package de.troetenrepublik.app.docs

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.Tax
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.Fmt

// Abrechnungen, Kontoauszuege und Jahressteuerbescheinigung, nur als Text in der App, ohne Dateien.
object Documents {

    // Zeilen sind Paare aus Bezeichnung und Wert, totals markiert Summenzeilen.
    data class Document(val title: String, val subtitle: String, val lines: List<Pair<String, String>>, val totals: Set<String> = emptySet())

    fun hasDocument(tx: Transaction): Boolean = if (tx.tag == "TRANSFER_IN") false else when (tx.type) {
        TxType.BUY, TxType.SELL, TxType.DIVIDEND, TxType.INTEREST, TxType.KNOCKOUT, TxType.EXPIRY,
        TxType.DEPOSIT, TxType.WITHDRAWAL -> true
        else -> false
    }

    fun title(tx: Transaction): String = S.docTitle(tx.type)

    fun lines(tx: Transaction, owner: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out += S.docCustomer to owner.ifBlank { S.docNoTaxEffect }
        out += S.docDate to Fmt.dateTime(tx.ts)
        out += S.docNumber to tx.id.takeLast(12).uppercase()
        if (tx.tag == "TRANSFER_IN") {
            out += S.docTradeType to S.docTransferIn
            out += S.docSecurity to tx.title
            if (tx.symbol.isNotBlank()) out += S.docSymbol to tx.symbol
            out += S.docPieces to Fmt.qty(tx.qty)
            out += S.importPrice to Fmt.priceEuro(tx.price)
            out += S.importCostBasis to Fmt.euro(tx.qty * tx.price)
            out += S.importDate to Fmt.date(tx.ts)
            out += S.docCredit to S.docNoCashFlow
            return out
        }
        when (tx.type) {
            TxType.BUY, TxType.SELL -> {
                out += S.docTradeType to if (tx.type == TxType.BUY) S.buy else S.sell
                out += S.docSecurity to tx.title
                if (tx.symbol.isNotBlank()) out += S.docSymbol to tx.symbol
                out += S.docPieces to Fmt.qty(tx.qty)
                out += S.docExecutionPrice to Fmt.priceEuro(tx.price)
                out += S.docMarketValue to Fmt.euro(tx.qty * tx.price)
                if (tx.fee > 0) out += S.docFee to Fmt.euro(-tx.fee)
                if (tx.type == TxType.SELL) {
                    out += S.docRealized to Fmt.signedEuro(tx.realized)
                    if (tx.tax > 0) out += S.docTax to Fmt.euro(-tx.tax)
                }
                out += S.docVenue to S.docVenueValue
                out += if (tx.type == TxType.BUY) S.docDebit to Fmt.euro(-(tx.qty * tx.price + tx.fee))
                else S.docCredit to Fmt.euro(tx.amount - tx.fee)
            }
            TxType.DIVIDEND -> {
                out += S.docSecurity to tx.title
                out += S.docPieces to Fmt.qty(tx.qty)
                out += S.docDividendPerShare to Fmt.priceEuro(tx.price)
                out += S.docGross to Fmt.euro(tx.amount + tx.tax)
                if (tx.tax > 0) out += S.docTax to Fmt.euro(-tx.tax)
                out += S.docCredit to Fmt.euro(tx.amount)
            }
            TxType.INTEREST -> {
                out += S.docPeriod to S.docUntil(Fmt.date(tx.ts))
                out += S.docInterestGross to Fmt.euro(tx.amount + tx.tax)
                if (tx.tax > 0) out += S.docTax to Fmt.euro(-tx.tax)
                out += S.docCredit to Fmt.euro(tx.amount)
            }
            TxType.KNOCKOUT, TxType.EXPIRY -> {
                out += S.docProduct to tx.title
                out += S.docPieces to Fmt.qty(tx.qty)
                out += S.docRefundPerPiece to Fmt.priceEuro(tx.price)
                if (tx.realized != 0.0) out += S.docRealized to Fmt.signedEuro(tx.realized)
                if (tx.tax > 0) out += S.docTax to Fmt.euro(-tx.tax)
                out += S.docCredit to Fmt.euro(tx.amount)
            }
            TxType.DEPOSIT -> out += S.docCredit to Fmt.euro(tx.amount)
            TxType.WITHDRAWAL -> {
                out += S.docDebit to Fmt.euro(tx.amount)
                out += S.docRecipient to S.referenceAccount
            }
            else -> {}
        }
        if (tx.note.isNotBlank() && tx.type != TxType.FEE && tx.type != TxType.PLAN) out += S.docNote to tx.note
        return out
    }

    fun settlement(tx: Transaction, owner: String): Document =
        Document(title(tx), "${tx.title.ifBlank { S.txTitle(tx.tag, tx.title) }} · ${Fmt.dateTime(tx.ts)}", lines(tx, owner), setOf(S.docCredit, S.docDebit))

    // Alle Buchungen eines Monats mit Anfangs- und Endsaldo.
    fun statement(account: Account, year: Int, month: Int, owner: String): Document {
        val zone = java.time.ZoneId.systemDefault()
        fun ym(ts: Long) = java.time.Instant.ofEpochMilli(ts).atZone(zone).let { it.year * 100 + it.monthValue }
        val key = year * 100 + month
        val sorted = account.transactions.sortedBy { it.ts }
        val before = sorted.filter { ym(it.ts) < key }.sumOf { it.amount }
        val inMonth = sorted.filter { ym(it.ts) == key }
        val out = mutableListOf<Pair<String, String>>()
        out += S.docOpening to Fmt.euro(before)
        for (t in inMonth) {
            if (t.amount == 0.0) continue
            val title = S.txTitle(t.tag, t.title).ifBlank { S.txLabel(t.type) }
            out += "${Fmt.date(t.ts)}  $title" to Fmt.euro(t.amount)
        }
        out += S.docClosing to Fmt.euro(before + inMonth.sumOf { it.amount })
        val label = Fmt.monthYear(java.time.YearMonth.of(year, month))
        return Document(S.statementTitle(label), owner.ifBlank { S.docNoTaxEffect }, out, setOf(S.docOpening, S.docClosing))
    }

    fun taxCertificate(account: Account, year: Int, allowance: Double, owner: String): Document {
        val zone = java.time.ZoneId.systemDefault()
        val inYear = account.transactions.filter { java.time.Instant.ofEpochMilli(it.ts).atZone(zone).year == year }
        val gainsSell = inYear.filter { it.type == TxType.SELL || it.type == TxType.KNOCKOUT || it.type == TxType.EXPIRY }.sumOf { it.realized }
        val dividends = inYear.filter { it.type == TxType.DIVIDEND }.sumOf { it.amount + it.tax }
        val interest = inYear.filter { it.type == TxType.INTEREST }.sumOf { it.amount + it.tax }
        val taxPaid = inYear.sumOf { it.tax }
        val t: TaxState = account.tax
        return Document(
            S.taxCertificateTitle(year), owner.ifBlank { S.docNoTaxEffect },
            listOf(
                S.docTaxYear to year.toString(),
                S.docRealizedGains to Fmt.signedEuro(gainsSell),
                S.docDividendsGross to Fmt.euro(dividends),
                S.docInterestGrossYear to Fmt.euro(interest),
                S.docAllowance to Fmt.euro(allowance),
                S.docAllowanceUsed to Fmt.euro(if (t.year == year) t.allowanceUsed else 0.0),
                S.docLossPotStocks to Fmt.euro(t.lossPotStocks),
                S.docLossPotGeneral to Fmt.euro(t.lossPotGeneral),
                S.docTaxWithheld to Fmt.euro(taxPaid),
                S.taxRate to Fmt.pct(Tax.RATE * 100, 3),
            ),
            setOf(S.docTaxWithheld),
        )
    }
}
