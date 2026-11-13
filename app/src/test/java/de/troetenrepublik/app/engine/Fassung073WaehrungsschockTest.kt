package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Waehrungsschock: nur die Umrechnung, ETFs nach der Waehrung ihrer Laender aufgeloest.
class Fassung073WaehrungsschockTest {

    // Welt-ETF in Euro: 60 % USA, 10 % Japan, 30 % Deutschland
    private val welt = mapOf("US" to 0.6, "JP" to 0.1, "DE" to 0.3)
    private val etf: (String) -> Map<String, Double>? = { s -> if (s == "WELT") welt else null }

    private fun nahe(soll: Double, ist: Double) = assertEquals(soll, ist, 1e-12)

    @Test
    fun codeFuehrtKleinstwaehrungenZurueck() {
        assertEquals("GBP", Waehrungsschock.code("GBp"))
        assertEquals("GBP", Waehrungsschock.code("GBX"))
        assertEquals("USD", Waehrungsschock.code("USd"))
        assertEquals("ILS", Waehrungsschock.code("ILA"))
        assertEquals("ZAR", Waehrungsschock.code("ZAc"))
        assertEquals("EUR", Waehrungsschock.code("EUR"))
    }

    @Test
    fun verteilungEinesEinzelwertsUndEinesFonds() {
        assertEquals(mapOf("USD" to 1.0), Waehrungsschock.verteilung("AAPL", "USD", etf))
        assertEquals(mapOf("GBP" to 1.0), Waehrungsschock.verteilung("SHEL.L", "GBp", etf))
        val v = Waehrungsschock.verteilung("WELT", "EUR", etf)
        nahe(0.6, v.getValue("USD")); nahe(0.1, v.getValue("JPY")); nahe(0.3, v.getValue("EUR"))
        assertEquals(3, v.size)
    }

    @Test
    fun verteilungLaesstUnbekannteLaenderBeiDerHandelswaehrung() {
        // 50 % USA, 50 % ohne bekannte Waehrung: der Rest bleibt in Euro
        val v = Waehrungsschock.verteilung("TEIL", "EUR") { mapOf("US" to 0.5, "ZZ" to 0.5) }
        nahe(0.5, v.getValue("USD")); nahe(0.5, v.getValue("EUR"))
        // Laenderanteile ueber 100 % werden auf 100 % gebracht
        val ueber = Waehrungsschock.verteilung("UEBER", "EUR") { mapOf("US" to 0.75, "JP" to 0.75) }
        nahe(0.5, ueber.getValue("USD")); nahe(0.5, ueber.getValue("JPY"))
    }

    @Test
    fun fremdanteilGiltGegenDieHeimatwaehrung() {
        val v = Waehrungsschock.verteilung("WELT", "EUR", etf)
        nahe(0.7, Waehrungsschock.fremdanteil(v, "EUR"))
        nahe(0.4, Waehrungsschock.fremdanteil(v, "USD"))
        nahe(0.0, Waehrungsschock.fremdanteil(mapOf("GBP" to 1.0), "GBp"))
        nahe(0.0, Waehrungsschock.fremdanteil(emptyMap(), "EUR"))
    }

    @Test
    fun umrechnungSteigtUndFaellt() {
        nahe(-1.0 / 11.0, Waehrungsschock.umrechnung(0.10))
        nahe(1.0 / 0.9 - 1.0, Waehrungsschock.umrechnung(-0.10))
        nahe(0.0, Waehrungsschock.umrechnung(0.0))
        // der Schock bleibt vor der Null: -200 % zaehlt wie -95 %
        nahe(19.0, Waehrungsschock.umrechnung(-2.0))
    }

    private val werte = listOf(1000.0, 1000.0, 2000.0)
    private val verteilungen = listOf(
        mapOf("EUR" to 1.0),
        mapOf("USD" to 1.0),
        Waehrungsschock.verteilung("WELT", "EUR", etf),
    )

    @Test
    fun euroSteigtUmZehnProzentMitGemischtenWaehrungen() {
        val e = Waehrungsschock.rechnen(werte, verteilungen, "EUR", 0.10)
        // Euro-Aktie bleibt, Dollar-Aktie -1/11, der Fonds zu 70 % in Fremdwaehrung: -0,7/11
        nahe(0.0, e.bewegung[0])
        nahe(-1.0 / 11.0, e.bewegung[1])
        nahe(-0.7 / 11.0, e.bewegung[2])
        // Fremdwaehrung: (0 + 1000 + 1400) / 4000
        nahe(0.6, e.fremdanteil)
        // Depot: 1000 * 0 + 1000 * (-1/11) + 2000 * (-0,7/11) = -2400/11 von 4000
        val depot = werte.indices.sumOf { werte[it] * e.bewegung[it] } / werte.sum()
        nahe(-0.6 / 11.0, depot)
        nahe(-2400.0 / 11.0 / 4000.0, depot)
    }

    @Test
    fun euroFaelltUmZehnProzent() {
        val e = Waehrungsschock.rechnen(werte, verteilungen, "EUR", -0.10)
        nahe(0.0, e.bewegung[0])
        nahe(1.0 / 0.9 - 1.0, e.bewegung[1])
        nahe(0.7 * (1.0 / 0.9 - 1.0), e.bewegung[2])
        nahe(0.6, e.fremdanteil)
    }

    @Test
    fun heimatwaehrungIstNichtFestDerEuro() {
        // Heimat US-Dollar: Euro-Aktie ganz fremd, Dollar-Aktie nicht, der Fonds nur zu 40 % (Yen und Euro)
        val e = Waehrungsschock.rechnen(werte, verteilungen, "USD", 0.10)
        nahe(-1.0 / 11.0, e.bewegung[0])
        nahe(0.0, e.bewegung[1])
        nahe(-0.4 / 11.0, e.bewegung[2])
        // (1000 + 0 + 800) / 4000
        nahe(0.45, e.fremdanteil)
    }

    @Test
    fun nurHeimatwaehrungHatKeinenSchock() {
        val e = Waehrungsschock.rechnen(listOf(500.0, 300.0), listOf(mapOf("EUR" to 1.0), mapOf("EUR" to 1.0)), "EUR", 0.10)
        assertTrue(e.bewegung.all { it == 0.0 })
        nahe(0.0, e.fremdanteil)
    }

    @Test
    fun leeresDepotUndKleinstwaehrungAlsHeimat() {
        val leer = Waehrungsschock.rechnen(emptyList(), emptyList(), "EUR", 0.10)
        assertTrue(leer.bewegung.isEmpty())
        nahe(0.0, leer.fremdanteil)
        // Pence-Notierung zaehlt als Pfund: Heimat GBP, Wert in GBp ist keine Fremdwaehrung
        val e = Waehrungsschock.rechnen(listOf(100.0), listOf(Waehrungsschock.verteilung("X.L", "GBp") { null }), "GBP", 0.10)
        nahe(0.0, e.bewegung[0])
    }
}
