package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// [Chart-Rueckfall 0.73] Vortagsschluss aus echten Chart-Antworten von Yahoo (09.11.2026, range=5d, interval=1d)
class ChartVortagTest {
    private fun antwort(name: String) = javaClass.classLoader!!.getResource("boerse/$name.json")!!.readText()

    private fun prev(name: String, symbol: String) = MarketApi().chartQuoteAus(symbol, antwort(name))!!.prevClose

    // Asien am Vormittag: Kerze von heute ohne Schluss, Kurs schon von heute; Vortag ist der Schluss vom Freitag (Yahoo: +2,33 %)
    @Test(timeout = 20_000)
    fun nikkeiLaufenderTagOhneSchluss() {
        val q = MarketApi().chartQuoteAus("^N225", antwort("chart_n225"))!!
        assertEquals(57434.77, q.prevClose, 0.01)
        assertEquals(2.33, q.changePct, 0.01)
        // die Kerze davor (Donnerstag) war der falsche Vortag der alten Rechnung
        assertNotEquals(61077.9, q.prevClose, 0.01)
    }

    @Test(timeout = 20_000)
    fun hangSengNachSchlussOhneHeutigeKerze() = assertEquals(24481.86, prev("chart_hsi", "^HSI"), 0.01)

    // Kerze des laufenden Tages mit Kurs: der Vortag ist die Kerze davor (Ergebnis +0,099 % wie bei der Boerse)
    @Test(timeout = 20_000)
    fun australienLaufenderTagMitKerze() {
        val q = MarketApi().chartQuoteAus("^AXJO", antwort("chart_axjo"))!!
        assertEquals(9020.8, q.prevClose, 0.01)
        assertEquals(0.099, q.changePct, 0.001)
    }

    // USA nach Handelsschluss: Kerze von heute hat den Schluss, Vortag ist Donnerstag
    @Test(timeout = 20_000)
    fun usaNachboerslich() = assertEquals(7376.86, prev("chart_gspc", "^GSPC"), 0.01)

    @Test(timeout = 20_000)
    fun daxMitkerzeVonHeute() = assertEquals(25820.36, prev("chart_dax", "^GDAXI"), 0.01)

    // Index mit nur einer Tageskerze: chartPreviousClose ist der echte Vortagsschluss
    @Test(timeout = 20_000)
    fun einzelneKerze() {
        val q = MarketApi().chartQuoteAus("FPXAA.PR", antwort("chart_px"))!!
        assertEquals(2630.37, q.prevClose, 0.01)
        assertEquals(1.01, q.changePct, 0.01)
    }

    // Wochenende: Marktzeit Freitag Abend, letzte Kerze Freitag; Vortag ist Donnerstag
    @Test(timeout = 20_000)
    fun wochenende() {
        val tag = 86_400L
        val mo = 1_793_664_000L // Montag 02.11.2026 00:00 UTC
        val zeiten = (0L..4L).map { mo + it * tag + 9 * 3600 }
        val schluesse = listOf(100.0, 101.0, 102.0, 103.0, 104.0)
        val freitagAbend = mo + 4 * tag + 17 * 3600
        assertEquals(103.0, ChartVortag.schluss(zeiten, schluesse, freitagAbend, 3600L, null, 99.0, 104.0), 1e-9)
    }

    @Test(timeout = 20_000)
    fun asienAmMorgenAusKerzen() {
        val tag = 86_400L
        val mo = 1_793_664_000L
        val zeiten = (0L..3L).map { mo + it * tag + 3600 }
        val schluesse = listOf(100.0, 101.0, 102.0, 103.0) // bis Donnerstag
        // Freitag 09:05 Ortszeit (UTC+9): Marktzeit gehoert zum Freitag, die letzte Kerze zum Donnerstag
        val freitagMorgen = mo + 4 * tag + 5 * 60
        assertEquals(103.0, ChartVortag.schluss(zeiten, schluesse, freitagMorgen, 32_400L, null, 99.0, 104.0), 1e-9)
    }

    @Test(timeout = 20_000)
    fun ohneMarktzeitGiltDieVorletzteKerze() =
        assertEquals(101.0, ChartVortag.schluss(listOf(1L, 2L, 3L), listOf(100.0, 101.0, 102.0), null, null, null, 99.0, 103.0), 1e-9)

    @Test(timeout = 20_000)
    fun previousCloseGehtVor() = assertEquals(98.0, ChartVortag.schluss(listOf(1L), listOf(100.0), 5L, 0L, 98.0, 99.0, 103.0), 1e-9)

    @Test(timeout = 20_000)
    fun ohneKerzenChartPreviousClose() = assertEquals(99.0, ChartVortag.schluss(emptyList(), emptyList(), 5L, 0L, null, 99.0, 103.0), 1e-9)
}
