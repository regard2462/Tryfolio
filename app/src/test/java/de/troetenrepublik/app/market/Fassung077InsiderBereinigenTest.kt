package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Test

class Fassung077InsiderBereinigenTest {
    private fun g(a: String, k: String, p: String, r: String, n: Double, preis: Double, nach: Double?, kauf: Boolean = false, d: String = "2026-09-16") =
        SecInsider.Geschaeft(a = a, k = k, e = k, p = p, r = r, kauf = kauf, d = d, f = d, n = n, preis = preis, wert = n * preis, nach = nach)

    @Test
    fun gesamterloesImPreisfeldWirdNichtMitDerStueckzahlMalgenommen() {
        val aus = SecInsider.bereinigen(listOf(g("1", "IHT", "Wirth James F", "S", 25_000.0, 33_000.0, 8_008_386.0)))
        assertEquals(33_000.0, aus[0].wert, 0.01)
        assertEquals(1.32, aus[0].preis, 0.001)
        // Teure Aktie mit kleiner Stueckzahl und Berkshire bleiben
        val teuer = SecInsider.bereinigen(listOf(g("2", "NVR", "A", "S", 5.0, 11_000.0, 100.0), g("3", "BRK-A", "B", "S", 2.0, 700_000.0, 10.0)))
        assertEquals(55_000.0, teuer[0].wert, 0.01)
        assertEquals(1_400_000.0, teuer[1].wert, 0.01)
    }

    @Test
    fun gemeinsamGemeldetesGeschaeftZaehltEinmal() {
        val liste = listOf(
            g("10", "FANG", "SGF FANG Holdings, LP", "N", 9_079_675.0, 205.26, 64_957_047.0),
            g("11", "FANG", "Greth Lyndal", "A", 9_079_675.0, 205.26, 64_957_047.0),
            g("12", "FANG", "Dick Teresa L.", "F", 5_000.0, 210.0, 75_755.0, d = "2026-09-15"),
        )
        val aus = SecInsider.bereinigen(liste)
        assertEquals(listOf("11", "12"), aus.map { it.a })
        val f = SecInsider.firmen(aus, "2026-01-01").single()
        assertEquals(9_079_675.0 * 205.26 + 1_050_000.0, f.verkaeufe, 1.0)
        // Dieselbe Person mit zwei gleichen Zeilen bleibt unveraendert
        val gleich = listOf(g("20", "X", "A", "S", 100.0, 10.0, 500.0), g("21", "X", "A", "S", 100.0, 10.0, 500.0))
        assertEquals(2, SecInsider.bereinigen(gleich).size)
    }
}
