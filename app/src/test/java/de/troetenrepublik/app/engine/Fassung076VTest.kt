package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

// Freies Fenster im Performance-Werkzeug rechnet wie ein festes Fenster mit denselben Grenzen.
class Fassung076VTest {
    private val h = 3_600_000L
    private val tag = 24 * h

    private fun tx(id: String, ts: Long, type: TxType, amount: Double, qty: Double = 0.0, price: Double = 0.0) =
        Transaction(id, ts, type, id, amount = amount, qty = qty, price = price, symbol = "AAA", key = "AAA")

    private val buchungen = listOf(
        tx("e", 10 * tag + h, TxType.DEPOSIT, 1000.0),
        tx("k", 11 * tag + 10 * h, TxType.BUY, -500.0, qty = 10.0, price = 50.0),
        tx("e2", 13 * tag + 13 * h, TxType.DEPOSIT, 100.0),
        tx("dv", 14 * tag + 14 * h, TxType.DIVIDEND, 7.5),
    )
    private val reihe = listOf(
        11 * tag + 12 * h to 500.0, 12 * tag + 12 * h to 550.0, 13 * tag + 12 * h to 540.0,
        14 * tag + 12 * h to 600.0, 15 * tag + 12 * h to 605.0,
    )
    private val cash = 1000.0 - 500 + 100 + 7.5
    private val jetzt = 16 * tag - 1

    @Test
    fun freiesFensterGleichFestemFenster() {
        val frei = Performanceanalyse.berechnenTage(reihe, buchungen, cash, 12, 20, jetzt, ZoneOffset.UTC)!!
        val fest = Performanceanalyse.berechnen(reihe, buchungen, cash, 12 * tag, jetzt, ZoneOffset.UTC)!!
        assertEquals(fest, frei)
    }

    @Test
    fun bisTagInDerVergangenheitEndetAmTagesschluss() {
        val frei = Performanceanalyse.berechnenTage(reihe, buchungen, cash, 11, 13, jetzt, ZoneOffset.UTC)!!
        val fest = Performanceanalyse.berechnen(reihe, buchungen, cash, 11 * tag, 14 * tag, ZoneOffset.UTC)!!
        assertEquals(fest, frei)
        assertEquals(14 * tag, frei.bis)
    }

    @Test
    fun anfangsbestandAmVonTagIstStartwert() {
        val e = Performanceanalyse.berechnenTage(reihe, buchungen, cash, 13, 15, jetzt, ZoneOffset.UTC)!!
        // Am 13. um 0 Uhr: 500 Cash plus Wertpapiere vom 12. (550)
        assertEquals(500.0 + 550.0, e.bilanz.anfang, 1e-9)
        assertEquals(100.0, e.bilanz.einlagen, 1e-9)
        assertEquals(500.0 + 100.0 + 7.5 + 605.0, e.bilanz.ende, 1e-9)
    }

    @Test
    fun vertauschteTageUndLeeresFenster() {
        val a = Performanceanalyse.berechnenTage(reihe, buchungen, cash, 12, 14, jetzt, ZoneOffset.UTC)
        val b = Performanceanalyse.berechnenTage(reihe, buchungen, cash, 14, 12, jetzt, ZoneOffset.UTC)
        assertNotNull(a)
        assertEquals(a, b)
        assertNull(Performanceanalyse.berechnenTage(emptyList(), buchungen, cash, 12, 14, jetzt, ZoneOffset.UTC))
    }
}
