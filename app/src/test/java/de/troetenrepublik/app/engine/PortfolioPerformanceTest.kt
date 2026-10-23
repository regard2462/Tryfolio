package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

// Export fuer Portfolio Performance, mit erfundenen Buchungen.
class PortfolioPerformanceTest {
    private val zone = ZoneOffset.UTC
    private fun at(tag: Int, stunde: Int = 10) = LocalDateTime.of(2026, 3, tag, stunde, 15).toInstant(zone).toEpochMilli()

    private val konto = Account(transactions = listOf(
        Transaction("d1", at(2), TxType.DEPOSIT, "", amount = 5000.0, tag = "DEPOSIT"),
        Transaction("b1", at(3), TxType.BUY, "Muster AG", amount = -1000.0, qty = 10.0, price = 100.0, symbol = "MUS.DE", key = "MUS.DE", tag = "BUY"),
        Transaction("f1", at(3), TxType.FEE, "Muster AG", amount = -1.0, tag = "FEE", note = "MUS.DE"),
        Transaction("s1", at(5), TxType.SELL, "Muster AG", amount = 590.0, qty = 5.0, price = 120.0, symbol = "MUS.DE", key = "MUS.DE", tax = 10.0, tag = "SELL"),
        Transaction("f2", at(5), TxType.FEE, "Muster AG", amount = -1.0, tag = "FEE", note = "MUS.DE"),
        Transaction("v1", at(6), TxType.DIVIDEND, "Muster AG", amount = 7.5, qty = 5.0, symbol = "MUS.DE", key = "MUS.DE", tax = 2.5, tag = "DIVIDEND"),
        Transaction("i1", at(7), TxType.BUY, "Beispiel; Holding", amount = 0.0, qty = 2.0, price = 50.0, symbol = "BSP", key = "BSP", tag = "TRANSFER_IN", via = "IMPORT"),
        Transaction("k1", at(8), TxType.KNOCKOUT, "Turbo Long", amount = 0.03, qty = 10.0, price = 0.003, symbol = "MUS.DE", key = "ko-1", tag = "KO_SETTLE"),
        Transaction("a1", at(9), TxType.ALERT, "Muster AG", tag = "ALERT_ABOVE"),
        Transaction("w1", at(10), TxType.WITHDRAWAL, "", amount = -200.0, tag = "WITHDRAWAL"),
    ))

    private fun zeilen(deutsch: Boolean) =
        PortfolioPerformance.csv(konto, { if (it == "MUS.DE") "DE000MUS0001" else "" }, deutsch, zone).trimEnd().split("\r\n")

    @Test fun kopfzeileWieInPortfolioPerformance() {
        assertEquals("Datum;Uhrzeit;Typ;Wert;Buchungswährung;Stück;ISIN;Ticker-Symbol;Wertpapiername;Gebühren;Steuern;Notiz", zeilen(true)[0])
        assertEquals("Date,Time,Type,Value,Transaction Currency,Shares,ISIN,Ticker Symbol,Security Name,Fees,Taxes,Note", zeilen(false)[0])
    }

    @Test fun buchungenMitGebuehrenUndSteuern() {
        val z = zeilen(true).drop(1)
        assertEquals("2026-03-02;10:15;Einlage;5000;EUR;;;;;;;", z[0])
        // Gebuehr steckt im Wert des Kaufs und steht bei der Order
        assertEquals("2026-03-03;10:15;Kauf;1001;EUR;10;DE000MUS0001;MUS.DE;Muster AG;1;;", z[1])
        // Verkauf: Wert nach Steuer und Gebuehr
        assertEquals("2026-03-05;10:15;Verkauf;589;EUR;5;DE000MUS0001;MUS.DE;Muster AG;1;10;", z[2])
        assertEquals("2026-03-06;10:15;Dividende;7,5;EUR;5;DE000MUS0001;MUS.DE;Muster AG;;2,5;", z[3])
        // Uebernommene Position: Einlage und Kauf zum Einstand; der Name mit Semikolon in Anfuehrungszeichen
        assertEquals("2026-03-07;10:15;Einlage;100;EUR;;;;;;;\"Beispiel; Holding\"", z[4])
        assertEquals("2026-03-07;10:15;Kauf;100;EUR;2;;BSP;\"Beispiel; Holding\";;;", z[5])
        // Knock-out eines Derivats: Verkauf zum Restwert, ohne ISIN und Ticker
        assertEquals("2026-03-08;10:15;Verkauf;0,03;EUR;10;;;Turbo Long;;;", z[6])
        assertEquals("2026-03-10;10:15;Entnahme;200;EUR;;;;;;;", z[7])
        // Alarme und zugeordnete Gebuehren erscheinen nicht
        assertEquals(8, z.size)
        assertEquals(8, PortfolioPerformance.anzahl(konto))
    }

    @Test fun englischMitKommaUndDezimalpunkt() {
        val z = zeilen(false).drop(1)
        assertEquals("2026-03-06,10:15,Dividend,7.5,EUR,5,DE000MUS0001,MUS.DE,Muster AG,,2.5,", z[3])
        assertTrue(z.any { it.contains(",Withdrawal,200,") })
    }
}
