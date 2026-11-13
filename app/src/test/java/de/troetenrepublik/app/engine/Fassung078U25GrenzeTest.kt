package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.HebelGrenzeTexte
import de.troetenrepublik.app.ui.PortfolioTool
import de.troetenrepublik.app.ui.WERKZEUG_GRUPPEN
import de.troetenrepublik.app.ui.werkzeugFormen
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

// Effizienzgrenze: Projektion, geringste Schwankung, Grenze, hoechste Sharpe-Quote, Punkt gleicher Rendite, Kursreihen.
class Fassung078U25GrenzeTest {
    private val g = Effizienzgrenze

    private fun diag(vararg v: Double) = Array(v.size) { i -> DoubleArray(v.size) { j -> if (i == j) v[i] else 0.0 } }

    // Drei Werte mit Gleichlauf zwischen den ersten beiden
    private val mu3 = doubleArrayOf(0.04, 0.08, 0.12)
    private val cov3 = arrayOf(
        doubleArrayOf(0.010, 0.006, 0.002),
        doubleArrayOf(0.006, 0.040, 0.010),
        doubleArrayOf(0.002, 0.010, 0.090),
    )

    private fun vol(w: DoubleArray, cov: Array<DoubleArray>) = sqrt(Risikobeitrag.varianz(w, cov))
    private fun ret(w: DoubleArray, mu: DoubleArray) = w.indices.sumOf { w[it] * mu[it] }

    // Alle Mischungen dreier Werte in Schritten von einem Prozent
    private fun raster(): List<DoubleArray> = buildList {
        for (a in 0..100) for (b in 0..100 - a) add(doubleArrayOf(a / 100.0, b / 100.0, (100 - a - b) / 100.0))
    }

    @Test
    fun projektionAufGewichte() {
        assertArrayEquals(doubleArrayOf(0.5, 0.5), g.aufSimplex(doubleArrayOf(0.5, 0.5)), 1e-12)
        assertArrayEquals(doubleArrayOf(1.0, 0.0), g.aufSimplex(doubleArrayOf(2.0, 0.0)), 1e-12)
        assertArrayEquals(doubleArrayOf(0.5, 0.5), g.aufSimplex(doubleArrayOf(0.6, 0.6)), 1e-12)
        assertArrayEquals(doubleArrayOf(0.0, 0.25, 0.75), g.aufSimplex(doubleArrayOf(-1.0, 0.2, 0.7)), 1e-12)
        val w = g.aufSimplex(doubleArrayOf(0.3, -0.2, 5.0, 0.1))
        assertEquals(1.0, w.sum(), 1e-12)
        assertTrue(w.all { it >= 0.0 })
    }

    @Test
    fun geringsteSchwankungOhneGleichlauf() {
        // Ohne Gleichlauf sind die Gewichte umgekehrt proportional zur Varianz
        val e = g.rechnen(doubleArrayOf(0.5, 0.5), doubleArrayOf(0.05, 0.10), diag(0.04, 0.01))!!
        assertArrayEquals(doubleArrayOf(0.2, 0.8), e.minimal.gewichte, 1e-5)
        assertEquals(sqrt(0.008), e.minimal.schwankung, 1e-7)
        assertEquals(0.2 * 0.05 + 0.8 * 0.10, e.minimal.rendite, 1e-6)
        val drei = g.rechnen(doubleArrayOf(1.0, 1.0, 1.0), doubleArrayOf(0.05, 0.06, 0.07), diag(0.01, 0.02, 0.04))!!
        assertArrayEquals(doubleArrayOf(4.0 / 7, 2.0 / 7, 1.0 / 7), drei.minimal.gewichte, 1e-5)
    }

    @Test
    fun grenzeSteigtUndEndetBeimBestenWert() {
        val e = g.rechnen(doubleArrayOf(0.4, 0.4, 0.2), mu3, cov3)!!
        assertTrue(e.grenze.size >= 10)
        assertTrue(e.grenze.first() === e.minimal)
        for (i in 1 until e.grenze.size) {
            assertTrue(e.grenze[i].rendite > e.grenze[i - 1].rendite)
            assertTrue(e.grenze[i].schwankung >= e.grenze[i - 1].schwankung - 1e-9)
        }
        assertEquals(0.12, e.grenze.last().rendite, 1e-6)
        assertEquals(0.30, e.grenze.last().schwankung, 1e-6)
        e.grenze.forEach { p ->
            assertEquals(1.0, p.gewichte.sum(), 1e-9)
            assertTrue(p.gewichte.all { it >= 0.0 })
            assertEquals(vol(p.gewichte, cov3), p.schwankung, 1e-12)
            assertEquals(ret(p.gewichte, mu3), p.rendite, 1e-12)
        }
        // Keine Mischung des Rasters liegt links der Grenze: bei mindestens gleicher Rendite nie weniger Schwankung
        val alle = raster()
        e.grenze.forEach { p ->
            val beste = alle.filter { ret(it, mu3) >= p.rendite - 1e-9 }.minOf { vol(it, cov3) }
            assertTrue("Rendite ${p.rendite}", p.schwankung <= beste + 1e-6)
        }
        assertEquals(alle.minOf { vol(it, cov3) }, e.minimal.schwankung, 2e-4)
        // Die einzelnen Werte und die Gleichgewichtung
        assertEquals(3, e.einzeln.size)
        assertEquals(sqrt(0.04), e.einzeln[1].schwankung, 1e-12)
        assertArrayEquals(doubleArrayOf(1.0 / 3, 1.0 / 3, 1.0 / 3), e.gleich.gewichte, 1e-12)
    }

    @Test
    fun hoechsteSharpeZahl() {
        val e = g.rechnen(doubleArrayOf(0.4, 0.4, 0.2), mu3, cov3)!!
        val s = e.sharpe!!
        val beste = raster().maxOf { ret(it, mu3) / vol(it, cov3) }
        assertTrue(s.rendite / s.schwankung >= beste - 1e-6)
        e.grenze.forEach { assertTrue(s.rendite / s.schwankung >= it.rendite / it.schwankung - 1e-9) }
        // Ohne Gleichlauf: Gewichte proportional zu Rendite durch Varianz
        val frei = g.rechnen(doubleArrayOf(0.5, 0.5), doubleArrayOf(0.05, 0.10), diag(0.04, 0.01))!!.sharpe!!
        assertArrayEquals(doubleArrayOf(1.25 / 11.25, 10.0 / 11.25), frei.gewichte, 1e-4)
        // Ein Wert mit Verlust und hoher Schwankung bekommt kein Gewicht; Leerverkauf gibt es nicht
        val mit = g.rechnen(doubleArrayOf(0.3, 0.3, 0.4), doubleArrayOf(0.06, 0.09, -0.05), diag(0.02, 0.03, 0.20))!!.sharpe!!
        assertEquals(0.0, mit.gewichte[2], 1e-9)
        // Nur Verluste: kein Punkt
        assertNull(g.rechnen(doubleArrayOf(0.5, 0.5), doubleArrayOf(-0.05, -0.10), diag(0.04, 0.01))!!.sharpe)
    }

    @Test
    fun gleicheRenditeMitWenigerSchwankung() {
        val depot = doubleArrayOf(0.1, 0.6, 0.3)
        val e = g.rechnen(depot, mu3, cov3)!!
        assertEquals(ret(depot, mu3), e.depot.rendite, 1e-12)
        assertEquals(vol(depot, cov3), e.depot.schwankung, 1e-12)
        val p = e.gleicheRendite!!
        assertEquals(e.depot.rendite, p.rendite, 1e-6)
        assertTrue(p.schwankung < e.depot.schwankung)
        assertEquals(1.0, p.gewichte.sum(), 1e-9)
        val beste = raster().filter { ret(it, mu3) >= e.depot.rendite - 1e-9 }.minOf { vol(it, cov3) }
        assertTrue(p.schwankung <= beste + 1e-6)
        // Heutige Betraege statt Anteile ergeben dasselbe
        val betraege = g.rechnen(doubleArrayOf(100.0, 600.0, 300.0), mu3, cov3)!!
        assertEquals(e.depot.schwankung, betraege.depot.schwankung, 1e-12)
        // Depot unter der Rendite der geringsten Schwankung: kein Punkt gleicher Rendite
        val unten = g.rechnen(doubleArrayOf(0.9, 0.1), doubleArrayOf(0.02, 0.10), diag(0.04, 0.01))!!
        assertTrue(unten.depot.rendite < unten.minimal.rendite)
        assertNull(unten.gleicheRendite)
        // Depot aus dem Wert mit der hoechsten Rendite: liegt selbst auf der Grenze
        val spitze = g.rechnen(doubleArrayOf(0.0, 0.0, 1.0), mu3, cov3)!!
        assertEquals(spitze.depot.schwankung, spitze.gleicheRendite!!.schwankung, 1e-6)
    }

    @Test
    fun festesErgebnisUndUnbrauchbareEingaben() {
        val a = g.rechnen(doubleArrayOf(0.2, 0.5, 0.3), mu3, cov3)!!
        val b = g.rechnen(doubleArrayOf(0.2, 0.5, 0.3), mu3, cov3)!!
        assertEquals(a.grenze.size, b.grenze.size)
        a.grenze.indices.forEach { assertArrayEquals(a.grenze[it].gewichte, b.grenze[it].gewichte, 0.0) }
        assertArrayEquals(a.sharpe!!.gewichte, b.sharpe!!.gewichte, 0.0)
        assertNull(g.rechnen(doubleArrayOf(1.0), doubleArrayOf(0.1), diag(0.04)))
        assertNull(g.rechnen(doubleArrayOf(0.5, 0.5), mu3, cov3))
        assertNull(g.rechnen(doubleArrayOf(0.5, 0.5), doubleArrayOf(0.1, Double.NaN), diag(0.04, 0.01)))
        assertNull(g.rechnen(doubleArrayOf(0.5, 0.5), doubleArrayOf(0.1, 0.2), diag(0.0, 0.0)))
        assertNull(g.rechnen(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.1, 0.2), diag(0.04, 0.01)))
        assertNull(g.rechnen(doubleArrayOf(-0.5, 1.5), doubleArrayOf(0.1, 0.2), diag(0.04, 0.01)))
        // Gleiche Renditen: Die Grenze ist ein Punkt
        val gleich = g.rechnen(doubleArrayOf(0.5, 0.5), doubleArrayOf(0.07, 0.07), diag(0.04, 0.01))!!
        assertEquals(1, gleich.grenze.size)
        assertArrayEquals(doubleArrayOf(0.2, 0.8), gleich.minimal.gewichte, 1e-5)
        // Zwei fast gleiche Werte: Die Rechnung bleibt stabil
        val nah = arrayOf(doubleArrayOf(0.04, 0.0399999), doubleArrayOf(0.0399999, 0.04))
        val e = g.rechnen(doubleArrayOf(0.5, 0.5), doubleArrayOf(0.07, 0.08), nah)!!
        assertEquals(0.2, e.minimal.schwankung, 1e-4)
        assertEquals(15, g.MAX_WERTE)
    }

    // Wochenkurse mit festem Muster: Trend je Woche und ein Ausschlag, der sich je Reihe anders wiederholt
    private fun kerzen(wochen: Int, trend: Double, ausschlag: Double, takt: Int): List<Candle> {
        val start = 19_000L * 86_400_000L + 12 * 3_600_000L
        var k = 100.0
        return (0..wochen).map { i ->
            if (i > 0) k *= exp(trend + ausschlag * if ((i / takt) % 2 == 0) 1 else -1)
            Candle(start + i * 7L * 86_400_000L, k, k, k, k)
        }
    }

    @Test
    fun ausKursreihen() {
        val reihen = listOf(kerzen(104, 0.001, 0.010, 1), kerzen(104, 0.002, 0.020, 2), kerzen(104, 0.003, 0.040, 3))
        val e = g.berechnen(listOf(500.0, 300.0, 200.0), reihen, 0L)!!
        assertEquals(104, e.wochen)
        assertArrayEquals(doubleArrayOf(0.5, 0.3, 0.2), e.depot.gewichte, 1e-12)
        // Rendite je Wert wie aus Anfang und Ende der Reihe
        val r = Risikobeitrag.renditen(reihen, 0L)!!
        val mu = g.erwartung(r)
        reihen.forEachIndexed { i, c -> assertEquals(exp(ln(c.last().close / c.first().close) / 2.0) - 1.0, mu[i], 1e-9) }
        assertEquals(sqrt(Risikobeitrag.kovarianz(r)[0][0]), e.einzeln[0].schwankung, 1e-12)
        assertTrue(e.minimal.schwankung <= e.depot.schwankung + 1e-9)
        assertNotNull(e.sharpe)
        assertEquals(0.1, g.erwartung(listOf(DoubleArray(52) { ln(1.1) / 52 }))[0], 1e-12)
        // Zu wenige Werte, zu viele, unpassende Laengen, zu kurze Geschichte
        assertNull(g.berechnen(listOf(1.0), reihen.take(1), 0L))
        assertNull(g.berechnen(listOf(1.0, 2.0), reihen, 0L))
        assertNull(g.berechnen(List(16) { 1.0 }, List(16) { reihen[it % 3] }, 0L))
        assertNull(g.berechnen(listOf(1.0, 2.0), listOf(kerzen(10, 0.001, 0.01, 1), kerzen(10, 0.002, 0.02, 2)), 0L))
    }

    @Test
    fun texteUndPlatzInDerListe() {
        val name = PortfolioTool.GRENZE.name
        assertEquals(1, WERKZEUG_GRUPPEN.count { name in it.second })
        // In der Gruppe des eigenen Depots
        assertTrue(name in WERKZEUG_GRUPPEN.first().second)
        assertTrue(werkzeugFormen(name).isNotEmpty())
        listOf(De, En).forEach { s ->
            assertTrue(s.ptTitle(name) != name && s.ptSubtitle(name).isNotBlank())
            assertFalse(s.ptSubtitle(name).contains('?'))
        }
        // Hinweis auf Ueberanpassung in beiden Sprachen, keine Empfehlung
        val de = HebelGrenzeTexte.text(true, "g.warnung")
        assertTrue(de.contains("vergangenen Renditen") && de.contains("keine Empfehlung") && de.contains("historisch und hypothetisch"))
        val en = HebelGrenzeTexte.text(false, "g.warnung")
        assertTrue(en.contains("past returns") && en.contains("not a recommendation"))
        assertEquals(
            "Dein Depot: 18,2 % Schwankung für +11,4 % Rendite pro Jahr",
            HebelGrenzeTexte.text(true, "g.kern", listOf("18,2 %", "+11,4 %")),
        )
        assertEquals("Dieselbe Rendite gab es mit 13,0 % Schwankung.", HebelGrenzeTexte.text(true, "g.kern.gleiche", listOf("13,0 %")))
    }
}
