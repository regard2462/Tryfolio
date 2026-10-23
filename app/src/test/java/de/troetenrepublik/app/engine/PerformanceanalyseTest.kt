package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

// Rendite wie in Portfolio Performance: Wertpapiere und Cash, Geld von aussen, Kapitalbilanz.
class PerformanceanalyseTest {

    private val h = 3_600_000L
    private fun d(n: Int) = n * 24 * h

    private fun tx(id: String, ts: Long, type: TxType, amount: Double, qty: Double = 0.0, price: Double = 0.0, tax: Double = 0.0, tag: String = "") =
        Transaction(id, ts, type, id, amount = amount, qty = qty, price = price, tax = tax, symbol = "AAA", key = "AAA", tag = tag)

    // 1000 eingezahlt, 10 Stueck zu 50 gekauft (1 Gebuehr), Kurs steigt auf 55 und 60,50. Zwischendurch 100 eingezahlt
    // und 10 Dividende (2,50 Steuer)
    private val buchungen = listOf(
        tx("e", d(0) + h, TxType.DEPOSIT, 1000.0),
        tx("k", d(1) + 10 * h, TxType.BUY, -500.0, qty = 10.0, price = 50.0),
        tx("g", d(1) + 10 * h, TxType.FEE, -1.0),
        tx("e2", d(2) + 13 * h, TxType.DEPOSIT, 100.0),
        tx("dv", d(2) + 14 * h, TxType.DIVIDEND, 7.5, tax = 2.5),
    )
    private val wertpapiere = listOf(d(1) + 12 * h to 500.0, d(2) + 12 * h to 550.0, d(3) + 12 * h to 605.0)
    private val cash = 1000.0 - 500 - 1 + 100 + 7.5

    @Test
    fun zeitgewichtetMitEinlageUndDividende() {
        val e = Performanceanalyse.berechnen(wertpapiere, buchungen, cash, 0L, d(3) + 12 * h, ZoneOffset.UTC)!!
        // Abschnitt 1: 999 zu 1049. Abschnitt 2: 1049 + 100 eingezahlt zu 605 + 606,50
        val erwartet = (1049.0 / 999.0) * (1211.5 / 1149.0) - 1
        assertEquals(erwartet, e.ttwror, 1e-9)
        assertNull(e.ttwrorJahr)
        assertEquals(605.0 / 500.0 - 1, e.nurWertpapiere!!, 1e-9)
        assertEquals(0.0, e.maxRueckgang, 1e-12)
        assertEquals(setOf(YearMonth.of(1970, 1)), e.monate.keys)
        assertEquals(e.ttwror, e.monate.getValue(YearMonth.of(1970, 1)), 1e-9)
        assertEquals(setOf(1970), e.jahre.keys)
    }

    @Test
    fun zinsfussUndJahresrendite() {
        val jahr = (365.25 * 24 * h).toLong()
        // Ein Jahr von 1000 auf 1100 ohne Geldfluesse: beide Renditen 10 %
        val e = Performanceanalyse.berechnen(listOf(0L to 1000.0, jahr to 1100.0), emptyList(), 0.0, 0L, jahr, ZoneOffset.UTC)!!
        assertEquals(0.1, e.ttwror, 1e-9)
        assertEquals(0.1, e.ttwrorJahr!!, 1e-6)
        assertEquals(0.1, e.izf!!, 1e-6)
        // 500 nach einem halben Jahr uebernommen: zeitgewichtet bleibt es bei 10 %, wenn der Kurs gleichmaessig steigt
        val halb = jahr / 2
        val mitte = 1000.0 * Math.sqrt(1.1)
        val f = Performanceanalyse.berechnen(
            listOf(0L to 1000.0, halb to mitte, halb + 2 to mitte + 500, jahr to (mitte + 500) * Math.sqrt(1.1)),
            listOf(tx("i", halb + 1, TxType.BUY, 0.0, qty = 5.0, price = 100.0, tag = "TRANSFER_IN")), 0.0, 0L, jahr, ZoneOffset.UTC,
        )!!
        assertEquals(0.1, f.ttwror, 1e-9)
        assertNotNull(f.izf)
        assertTrue(f.izf!! > 0.09 && f.izf!! < 0.11)
    }

    @Test
    fun kapitalbilanzGehtAuf() {
        val b = Performanceanalyse.berechnen(wertpapiere, buchungen, cash, 0L, d(3) + 12 * h, ZoneOffset.UTC)!!.bilanz
        assertEquals(999.0, b.anfang, 1e-9)
        assertEquals(1211.5, b.ende, 1e-9)
        assertEquals(100.0, b.einlagen, 1e-9)
        assertEquals(0.0, b.entnahmen, 1e-9)
        assertEquals(10.0, b.dividenden, 1e-9)
        assertEquals(2.5, b.steuern, 1e-9)
        // Kauf und Gebuehr lagen vor dem Beginn, der erste Kurs kommt nach dem Kauf
        assertEquals(0.0, b.gebuehren, 1e-9)
        assertEquals(105.0, b.kursgewinne, 1e-9)
        assertEquals(b.ende, b.anfang + b.einlagen - b.entnahmen + b.kursgewinne + b.dividenden + b.zinsen - b.gebuehren - b.steuern, 1e-9)
        assertEquals(1211.5 - 999 - 100, b.absolut, 1e-9)
    }

    @Test
    fun geldVonAussen() {
        assertEquals(100.0, Performanceanalyse.vonAussen(tx("e", 0, TxType.DEPOSIT, 100.0)), 0.0)
        assertEquals(-40.0, Performanceanalyse.vonAussen(tx("a", 0, TxType.WITHDRAWAL, -40.0)), 0.0)
        assertEquals(-5.0, Performanceanalyse.vonAussen(tx("w", 0, TxType.BET, -5.0)), 0.0)
        // Ohne Geld uebernommen: zum Einstand
        assertEquals(300.0, Performanceanalyse.vonAussen(tx("i", 0, TxType.BUY, 0.0, qty = 3.0, price = 100.0, tag = "TRANSFER_IN")), 0.0)
        assertEquals(0.0, Performanceanalyse.vonAussen(tx("k", 0, TxType.BUY, -300.0, qty = 3.0, price = 100.0)), 0.0)
        assertEquals(0.0, Performanceanalyse.vonAussen(tx("d", 0, TxType.DIVIDEND, 4.0)), 0.0)
    }

    @Test
    fun rueckgangUndZuWenigPunkte() {
        val reihe = listOf(d(1) to 100.0, d(2) to 120.0, d(3) to 90.0, d(4) to 110.0)
        val e = Performanceanalyse.berechnen(reihe, emptyList(), 0.0, 0L, d(4), ZoneOffset.UTC)!!
        assertEquals(0.25, e.maxRueckgang, 1e-9)
        assertEquals(0.1, e.ttwror, 1e-9)
        assertNull(Performanceanalyse.berechnen(listOf(d(1) to 100.0), emptyList(), 0.0, d(1), d(1), ZoneOffset.UTC))
        assertNull(Performanceanalyse.berechnen(emptyList(), emptyList(), 0.0, 0L, d(4), ZoneOffset.UTC))
    }
}
