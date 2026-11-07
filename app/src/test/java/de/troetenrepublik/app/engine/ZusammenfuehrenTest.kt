package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Snapshot
import de.troetenrepublik.app.data.TaxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Zwei Depots zusammenfuehren: Guthaben, gleiche Positionen, Verlauf und Steuertoepfe.
class ZusammenfuehrenTest {

    private fun pos(key: String, qty: Double, preis: Double, seit: Long) = Position(key, key, key, qty, preis, seit)

    @Test fun guthabenUndPositionen() {
        val a = Account(cash = 1_000.0, deposited = 5_000.0, positions = listOf(pos("AAA", 10.0, 50.0, 100L), pos("BBB", 1.0, 10.0, 50L)), watchlist = listOf("X", "Y"))
        val b = Account(cash = 500.0, deposited = 2_000.0, positions = listOf(pos("AAA", 30.0, 70.0, 80L), pos("CCC", 2.0, 5.0, 90L)), watchlist = listOf("Y", "Z"))
        val m = Zusammenfuehren.konten(a, b)
        assertEquals(1_500.0, m.cash, 1e-9)
        assertEquals(7_000.0, m.deposited, 1e-9)
        assertEquals(listOf("AAA", "BBB", "CCC"), m.positions.map { it.key })
        val aaa = m.positions.first { it.key == "AAA" }
        assertEquals(40.0, aaa.qty, 1e-9)
        assertEquals(65.0, aaa.avgPrice, 1e-9)
        assertEquals(80L, aaa.openedAt)
        assertEquals(listOf("X", "Y", "Z"), m.watchlist)
    }

    @Test fun verlaufIstDieSummeBeider() {
        val v = Zusammenfuehren.verlauf(
            listOf(Snapshot(10, 100.0, 60.0), Snapshot(30, 120.0, 70.0)),
            listOf(Snapshot(20, 50.0, 20.0), Snapshot(30, 55.0, null)),
        )
        assertEquals(listOf(10L, 20L, 30L), v.map { it.ts })
        assertEquals(listOf(100.0, 150.0, 175.0), v.map { it.value })
        assertEquals(80.0, v[1].securities!!, 1e-9)
        assertNull(v[2].securities)
    }

    @Test fun steuertoepfe() {
        val s = Zusammenfuehren.steuer(TaxState(2026, allowanceUsed = 300.0, taxPaid = 10.0), TaxState(2026, allowanceUsed = 200.0, taxPaid = 5.0))
        assertEquals(500.0, s.allowanceUsed, 1e-9)
        assertEquals(15.0, s.taxPaid, 1e-9)
        assertEquals(2027, Zusammenfuehren.steuer(TaxState(2026), TaxState(2027, taxPaid = 1.0)).year)
    }
}
