package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Gewinne und Rendite je Wertpapier: Die Summe aller Zeilen ist die Kapitalbilanz.
class Fassung076WTest {

    private val tag = Depotzerlegung.TAG
    private fun d(n: Int) = n * tag
    private val h = 3_600_000L

    private fun tx(id: String, ts: Long, type: TxType, amount: Double, key: String = "", qty: Double = 0.0, price: Double = 0.0,
                   tax: Double = 0.0, realized: Double = 0.0, note: String = "") =
        Transaction(id, ts, type, id, amount = amount, qty = qty, price = price, tax = tax, realized = realized, symbol = key, key = key, note = note)

    // Tag 0: 1000 eingezahlt. Tag 1: 10 AAA zu 50 (Gebuehr 1), 5 BBB zu 20. Tag 3: 4 AAA zu 60 verkauft (Steuer 5, Gebuehr 1).
    // Tag 4: Dividende AAA 10 (2,50 Steuer), Kontosteuer 3, Zinsen 2. Tag 5: BBB komplett zu 22 verkauft.
    private val buchungen = listOf(
        tx("e", d(0) + 10 * h, TxType.DEPOSIT, 1000.0),
        tx("k1", d(1) + 10 * h, TxType.BUY, -500.0, "AAA", 10.0, 50.0),
        tx("g1", d(1) + 10 * h, TxType.FEE, -1.0, note = "AAA"),
        tx("k2", d(1) + 11 * h, TxType.BUY, -100.0, "BBB", 5.0, 20.0),
        tx("v1", d(3) + 10 * h, TxType.SELL, 235.0, "AAA", 4.0, 60.0, tax = 5.0, realized = 40.0),
        tx("g2", d(3) + 10 * h, TxType.FEE, -1.0, note = "AAA"),
        tx("dv", d(4) + 10 * h, TxType.DIVIDEND, 7.5, "AAA", 6.0, 0.0, tax = 2.5),
        tx("st", d(4) + 11 * h, TxType.TAX, -3.0),
        tx("zi", d(4) + 12 * h, TxType.INTEREST, 2.0),
        tx("v2", d(5) + 10 * h, TxType.SELL, 110.0, "BBB", 5.0, 22.0, realized = 10.0),
    )

    // Kurse am Ende der Tage 0 bis 5
    private val kursAaa = listOf(0.0, 52.0, 55.0, 60.0, 58.0, 59.0)
    private val kursBbb = listOf(0.0, 21.0, 20.0, 19.0, 21.0, 22.0)

    private fun stueck(k: String, tagNr: Int): Double = buchungen.filter { it.posKey == k && it.ts < d(tagNr + 1) }.sumOf {
        when (it.type) { TxType.BUY -> it.qty; TxType.SELL -> -it.qty; else -> 0.0 }
    }

    private fun wert(k: String, t: Long): Double {
        val n = (t / tag).toInt()
        val kurse = if (k == "AAA") kursAaa else kursBbb
        return stueck(k, n) * kurse[n.coerceAtMost(5)]
    }

    private val zufluesse: Map<Pair<String, Long>, Double> = buchungen.filter { Depotzerlegung.imDepot(it.type) }
        .groupBy { it.posKey to Depotzerlegung.tagBeginn(it.ts) }.mapValues { (_, l) -> l.sumOf { Depotzerlegung.fluss(it) } }

    private val tage = Depotzerlegung.tage(d(1), d(5), listOf("AAA", "BBB"), zufluesse) { k, t -> wert(k, t) }

    private fun cashEnde(tagNr: Int) = buchungen.filter { it.ts < d(tagNr + 1) }.sumOf { it.amount }
    private fun vermoegenEnde(tagNr: Int) = if (tagNr < 0) 0.0 else wert("AAA", d(tagNr)) + wert("BBB", d(tagNr)) + cashEnde(tagNr)

    private fun rechnen(vonTag: Int, bisTag: Int) = Wertpapierbilanz.berechnen(
        tage, buchungen, mapOf("AAA" to "Alpha", "BBB" to "Beta"), mapOf("AAA" to "AAA", "BBB" to "BBB"), setOf("AAA"),
        d(vonTag) + 5 * h, d(bisTag) + 5 * h,
    )

    // Kapitalbilanz fuer dieselben ganzen Tage wie die Rechnung je Wertpapier
    private fun bilanz(vonTag: Int, bisTag: Int) =
        Performanceanalyse.bilanz(buchungen, d(vonTag) - 1, d(bisTag + 1) - 1, vermoegenEnde(vonTag - 1), vermoegenEnde(bisTag))

    @Test
    fun summeIstKapitalbilanzGanzerZeitraum() {
        val e = rechnen(0, 5)
        val b = bilanz(0, 5)
        assertEquals(b.absolut, e.summe, 1e-9)
        assertEquals(b.dividenden, e.dividenden, 1e-9)
        assertEquals(b.gebuehren, e.gebuehren, 1e-9)
        assertEquals(b.steuern, e.steuern, 1e-9)
        assertEquals(b.zinsen, e.sonstiges.zinsen, 1e-9)
        assertEquals(b.realisiert, e.realisiert, 1e-9)
        assertEquals(b.kursgewinne, e.realisiert + e.unrealisiert, 1e-9)
    }

    @Test
    fun summeIstKapitalbilanzTeilzeitraum() {
        for ((v, b) in listOf(2 to 5, 3 to 4, 4 to 5, 1 to 3)) {
            val e = rechnen(v, b)
            assertEquals("Zeitraum $v-$b", bilanz(v, b).absolut, e.summe, 1e-9)
        }
    }

    @Test
    fun zeileAaaMitKaufTeilverkaufDividendeGebuehrSteuer() {
        val e = rechnen(0, 5)
        val a = e.zeilen.first { it.key == "AAA" }
        // 6 Stueck zu 59 am Ende, 500 gekauft, 240 verkauft
        val kursgewinn = 6 * 59.0 - 500.0 + 240.0
        assertEquals(kursgewinn, a.kursgewinn, 1e-9)
        assertEquals(40.0, a.realisiert, 1e-9)
        assertEquals(kursgewinn - 40.0, a.unrealisiert, 1e-9)
        assertEquals(10.0, a.dividenden, 1e-9)
        assertEquals(2.0, a.gebuehren, 1e-9)
        // Steuer auf den Verkauf und auf die Dividende, die Kontosteuer gehoert nicht dazu
        assertEquals(7.5, a.steuern, 1e-9)
        assertEquals(kursgewinn + 10.0 - 2.0 - 7.5, a.summe, 1e-9)
        assertEquals(3.0, e.sonstiges.steuern, 1e-9)
        assertEquals(2.0, e.sonstiges.zinsen, 1e-9)
        assertTrue(a.imDepot)
    }

    @Test
    fun verkaufterWertBleibtSichtbar() {
        val e = rechnen(0, 5)
        val b = e.zeilen.first { it.key == "BBB" }
        assertEquals(110.0 - 100.0, b.summe, 1e-9)
        assertEquals(10.0, b.realisiert, 1e-9)
        assertEquals(0.0, b.unrealisiert, 1e-9)
        assertTrue(!b.imDepot)
        assertEquals("Beta", b.name)
        // Sortiert nach Summe, groesste zuerst
        assertEquals(e.zeilen.sortedByDescending { it.summe }.map { it.key }, e.zeilen.map { it.key })
    }

    @Test
    fun renditeZeitgewichtetUndZinsfuss() {
        val e = rechnen(0, 5)
        val b = e.zeilen.first { it.key == "BBB" }
        // BBB: 5 Stueck zu 20 gekauft, Tagesenden 21, 20, 19, 21, dann am Tag 5 zu 22 verkauft
        // Tag 1: 105 / 100, Tag 2: 100 / 105, Tag 3: 95 / 100, Tag 4: 105 / 95, Tag 5: Abfluss 110 bei Wert 0 auf 105
        val erwartet = (105.0 / 100.0) * (100.0 / 105.0) * (95.0 / 100.0) * (105.0 / 95.0) * (110.0 / 105.0) - 1
        assertEquals(erwartet, b.ttwror!!, 1e-9)
        assertNotNull(b.izf)
        // Bis zum Verkauf 10 Gewinn auf 100 in vier Tagen: der Zinsfuss ist positiv
        assertTrue(b.izf!! > 0)
        val a = e.zeilen.first { it.key == "AAA" }
        assertNotNull(a.ttwror)
        assertTrue(a.ttwror!! > 0)
    }

    @Test
    fun ohneBestandImZeitraumKeineZeile() {
        // Nur Tag 0: es gab noch keinen Kauf
        val e = rechnen(0, 0)
        assertTrue(e.zeilen.isEmpty())
        assertEquals(0.0, e.summe, 1e-9)
        val leer = Wertpapierbilanz.berechnen(emptyList(), emptyList(), emptyMap(), emptyMap(), emptySet(), 0L, d(3))
        assertTrue(leer.zeilen.isEmpty())
        assertNull(leer.zeilen.firstOrNull()?.ttwror)
    }
}
