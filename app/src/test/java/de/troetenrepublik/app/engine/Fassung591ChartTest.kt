package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

// Fassung 5.91: eigene Kaeufe, Orders und Linien im Kurschart, erwartete Kursspanne.
class Fassung591ChartTest {
    private val tag = 86_400_000L
    private val t0 = 20_000 * tag

    private fun tx(id: String, ts: Long, type: TxType, qty: Double, price: Double, symbol: String = "AAPL", key: String = symbol, amount: Double = 0.0, derivative: Derivative? = null) =
        Transaction(id = id, ts = ts, type = type, title = symbol, qty = qty, price = price, symbol = symbol, key = key, amount = amount, derivative = derivative)

    @Test fun markenJeTagUndArt() {
        val ko = Derivative("ko1", "AAPL", "Apple", DerivativeKind.KO_LONG, strike = 150.0, barrier = 165.0, ccy = "USD")
        val acc = Account(
            transactions = listOf(
                tx("a", t0 + 3_600_000, TxType.BUY, 2.0, 100.0),
                tx("b", t0 + 7_200_000, TxType.BUY, 2.0, 110.0),
                tx("c", t0 + 5 * tag, TxType.SELL, 1.0, 120.0),
                tx("d", t0 + 9 * tag, TxType.DIVIDEND, 3.0, 0.0, amount = 1.5),
                // fremder Wert und Derivat auf den Wert: keine Marke
                tx("e", t0, TxType.BUY, 1.0, 50.0, symbol = "MSFT"),
                tx("f", t0 + 2 * tag, TxType.BUY, 10.0, 1.2, key = "ko1", derivative = ko),
                Transaction(id = "g", ts = t0, type = TxType.DEPOSIT, title = "", amount = 1000.0),
            ),
        )
        val e = EigeneMarken.fuer(acc, "AAPL", emptyMap())
        assertEquals(3, e.marken.size)
        val kauf = e.marken[0]
        assertEquals(EigeneMarken.Art.KAUF, kauf.art)
        assertEquals(t0 + 3_600_000, kauf.t)
        assertEquals(4.0, kauf.stueck, 1e-9)
        assertEquals(420.0, kauf.betrag, 1e-9)
        assertEquals(105.0, kauf.kurs, 1e-9)
        assertEquals(EigeneMarken.Art.VERKAUF, e.marken[1].art)
        assertEquals(EigeneMarken.Art.DIVIDENDE, e.marken[2].art)
        assertEquals(1.5, e.marken[2].betrag, 1e-9)
        assertEquals(1, EigeneMarken.amTag(e.marken, t0 + 5 * tag + 3_600_000).size)
        assertTrue(EigeneMarken.amTag(e.marken, t0 + 3 * tag).isEmpty())
    }

    @Test fun linienInEuro() {
        val ko = Derivative("ko1", "AAPL", "Apple", DerivativeKind.KO_LONG, strike = 150.0, barrier = 165.0, ccy = "USD")
        val weg = Derivative("ko2", "AAPL", "Apple", DerivativeKind.KO_SHORT, barrier = 300.0, ccy = "USD", knockedOutAt = 5L)
        val faktor = Derivative("f1", "AAPL", "Apple", DerivativeKind.FACTOR_LONG, factor = 3.0, barrier = 10.0, ccy = "USD")
        val acc = Account(
            positions = listOf(
                Position("AAPL", "Apple", "AAPL", 4.0, 150.0, 0L),
                Position("ko1", "Turbo", "AAPL", 10.0, 1.2, 0L, derivative = ko),
                Position("ko2", "Turbo", "AAPL", 10.0, 0.1, 0L, derivative = weg),
                Position("f1", "Faktor", "AAPL", 10.0, 3.0, 0L, derivative = faktor),
            ),
            orders = listOf(
                Order("o1", "AAPL", "Apple", "AAPL", Side.BUY, OrderType.LIMIT, 1.0, limitPrice = 140.0),
                Order("o2", "AAPL", "Apple", "AAPL", Side.SELL, OrderType.STOP, 1.0, limitPrice = 130.0),
                Order("o3", "AAPL", "Apple", "AAPL", Side.SELL, OrderType.TRAILING_STOP, 1.0, limitPrice = 135.0),
                Order("o4", "AAPL", "Apple", "AAPL", Side.SELL, OrderType.LIMIT, 1.0, limitPrice = 200.0, status = OrderStatus.FILLED),
                Order("o5", "ko1", "Turbo", "AAPL", Side.SELL, OrderType.LIMIT, 1.0, limitPrice = 2.0, derivative = ko),
                Order("o6", "MSFT", "Microsoft", "MSFT", Side.BUY, OrderType.LIMIT, 1.0, limitPrice = 300.0),
            ),
            alerts = listOf(
                PriceAlert("a1", "AAPL", "Apple", 180.0, above = true),
                PriceAlert("a2", "AAPL", "Apple", 0.0, above = true, tagesProzent = 5.0),
                PriceAlert("a3", "AAPL", "Apple", 180.0, above = true),
            ),
        )
        val e = EigeneMarken.fuer(acc, "AAPL", mapOf("USD" to 1.1))
        val nachArt = e.linien.groupBy { it.art }
        assertEquals(150.0, nachArt[EigeneMarken.LinienArt.EINSTAND]!!.single().preis, 1e-9)
        assertEquals(140.0, nachArt[EigeneMarken.LinienArt.LIMIT]!!.single().preis, 1e-9)
        assertEquals(listOf(130.0, 135.0), nachArt[EigeneMarken.LinienArt.STOPP]!!.map { it.preis })
        // Tagesalarm ohne Marke, doppelter Alarm nur einmal
        assertEquals(180.0, nachArt[EigeneMarken.LinienArt.ALARM]!!.single().preis, 1e-9)
        // KO-Schwelle 165 USD bei 1,1 USD je Euro, ausgeknockte und Faktor-Zertifikate ohne Linie
        assertEquals(150.0, nachArt[EigeneMarken.LinienArt.KO]!!.single().preis, 1e-9)
        // Ohne Wechselkurs keine KO-Linie in falscher Waehrung
        assertNull(EigeneMarken.fuer(acc, "AAPL", emptyMap()).linien.firstOrNull { it.art == EigeneMarken.LinienArt.KO })
        assertTrue(EigeneMarken.fuer(acc, "SAP.DE", emptyMap()).leer)
    }

    @Test fun kursspanneFormel() {
        val s = Kursspanne.spanne(100.0, 0.3, Kursspanne.Quelle.IMPLIZIT)!!
        assertEquals(31, s.punkte.size)
        assertEquals(100.0, s.punkte[0].unten, 1e-9)
        assertEquals(100.0, s.punkte[0].oben, 1e-9)
        val f = 0.3 * sqrt(30 / 365.0)
        assertEquals(100.0 * exp(f), s.oben, 1e-9)
        assertEquals(100.0 * exp(-f), s.unten, 1e-9)
        assertEquals(30, s.tage)
        assertNull(Kursspanne.spanne(0.0, 0.3, Kursspanne.Quelle.IMPLIZIT))
        assertNull(Kursspanne.spanne(100.0, 0.0, Kursspanne.Quelle.IMPLIZIT))
    }

    @Test fun sigmaHistorischUndWahl() {
        // Abwechselnd +1 % und -1 %: Tagesschwankung rund 1 %, jaehrlich rund 15,9 %
        val kurse = ArrayList<Double>()
        var k = 100.0
        repeat(200) { i -> kurse += k; k *= if (i % 2 == 0) 1.01 else 1 / 1.01 }
        val s = Kursspanne.sigmaHistorisch(kurse)!!
        val r = kotlin.math.ln(1.01)
        // 60 Renditen, Mittel fast null: Stichprobenabweichung r*sqrt(60/59)
        assertEquals(r * sqrt(60.0 / 59.0) * sqrt(252.0), s, 1e-6)
        assertNull(Kursspanne.sigmaHistorisch(kurse.take(10)))
        assertEquals(Kursspanne.Quelle.IMPLIZIT, Kursspanne.sigmaWahl(0.25, 0.4)!!.second)
        assertEquals(Kursspanne.Quelle.HISTORISCH, Kursspanne.sigmaWahl(null, 0.4)!!.second)
        assertEquals(0.4, Kursspanne.sigmaWahl(0.0, 0.4)!!.first, 1e-9)
        assertNull(Kursspanne.sigmaWahl(null, null))
        assertNotNull(Kursspanne.sigmaWahl(7.0, 0.2))
    }
}
