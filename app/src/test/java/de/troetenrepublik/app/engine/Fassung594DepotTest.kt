package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Depot-Ideen (5.94): laufende Fondskosten im Depot-Check und Anlegerprofil aus erfundenen Trades.
class Fassung594DepotTest {

    private val tag = 86_400_000L
    private var n = 0
    private fun tx(typ: TxType, tage: Int, symbol: String, stueck: Double, preis: Double, key: String = symbol, via: String = "", ergebnis: Double = 0.0) =
        Transaction("t${n++}", 1_700_000_000_000L + tage * tag, typ, symbol, qty = stueck, price = preis, symbol = symbol, key = key, via = via, realized = ergebnis)

    @Test
    fun fondskostenGewichtet() {
        val werte = listOf(
            DepotCheck.Wert("Welt", 9000.0, DepotCheck.Art.ETF, "EUR", null, 0.0, ter = 0.2),
            DepotCheck.Wert("Thema", 1000.0, DepotCheck.Art.ETF, "EUR", null, 0.0, ter = 0.65),
            DepotCheck.Wert("Ohne Angabe", 5000.0, DepotCheck.Art.ETF, "EUR", null, 0.0),
        )
        val b = DepotCheck.pruefen(werte, 500.0, 0.0, 0.0, 0, 0).first { it.regel == DepotCheck.Regel.FONDSKOSTEN }
        // (9000 * 0,2 % + 1000 * 0,65 %) / 10000 = 0,245 %, 18 + 6,5 = 24,5 Euro
        assertEquals(0.00245, b.anteil, 1e-12)
        assertEquals(DepotCheck.Stufe.GUT, b.stufe)
        assertEquals("Thema", b.name)
        assertEquals(25, b.anzahl)
    }

    @Test
    fun fifoUndHaltedauer() {
        val txs = listOf(
            tx(TxType.BUY, 0, "A", 10.0, 100.0),
            tx(TxType.BUY, 10, "A", 10.0, 50.0),
            // Die ersten 15 Stueck: 10 zu 100 (20 Tage alt) und 5 zu 50 (10 Tage alt), Einstand 1250, Erloes 1200
            tx(TxType.SELL, 20, "A", 15.0, 80.0),
        )
        val v = Anlegerprofil.verkaeufe(txs).single()
        assertEquals((1200.0 / 1250.0 - 1) * 100, v.prozent, 1e-9)
        assertEquals((10 * 20.0 + 5 * 10.0) / 15, v.tage, 1e-9)
        // Der zweite Kauf lag 50 % unter dem Einstand: verbilligt
        assertEquals(1, Anlegerprofil.verbilligt(txs))
    }

    @Test
    fun dispositionseffekt() {
        // Gewinner nach 5 Tagen mit +5 % verkauft, Verlierer nach 60 Tagen mit -20 %. Die Trefferquote zaehlt seit 0.78
        // das Ergebnis der Buchung (Verkaufsbilanz), deshalb steht es hier dabei
        val txs = (0 until 3).flatMap { i ->
            listOf(tx(TxType.BUY, i * 100, "G$i", 1.0, 100.0), tx(TxType.SELL, i * 100 + 5, "G$i", 1.0, 105.0, ergebnis = 5.0))
        } + (0 until 3).flatMap { i ->
            listOf(tx(TxType.BUY, i * 100, "V$i", 1.0, 100.0), tx(TxType.SELL, i * 100 + 60, "V$i", 1.0, 80.0, ergebnis = -20.0))
        }
        val p = Anlegerprofil.auswerten(txs, jetzt = 1_700_000_000_000L + 400 * tag).associateBy { it.merkmal }
        assertEquals(0.5, p.getValue(Anlegerprofil.Merkmal.TREFFER).wert, 1e-9)
        assertEquals(DepotCheck.Stufe.KRITISCH, p.getValue(Anlegerprofil.Merkmal.CHANCE_RISIKO).stufe)
        assertEquals(DepotCheck.Stufe.ACHTUNG, p.getValue(Anlegerprofil.Merkmal.DISPOSITION).stufe)
        assertTrue(Anlegerprofil.Merkmal.HEBEL in p)
    }

    @Test
    fun zuWenigVerkaeufe() {
        val txs = listOf(tx(TxType.BUY, 0, "A", 1.0, 10.0), tx(TxType.SELL, 3, "A", 1.0, 11.0))
        val p = Anlegerprofil.auswerten(txs, jetzt = 1_700_000_000_000L + 10 * tag)
        assertTrue(p.none { it.merkmal == Anlegerprofil.Merkmal.TREFFER })
        // Haeufigkeit zaehlt trotzdem, Hebel nur ueber Depotschluessel anders als das Symbol
        assertEquals(DepotCheck.Stufe.GUT, p.first { it.merkmal == Anlegerprofil.Merkmal.HEBEL }.stufe)
    }
}
