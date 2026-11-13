package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.FinderRueckblickMessung.Stichtag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Fassung 5.93: kaufen und halten im Rueckblick-Test. Rendite vom Stichtag bis heute, der erste Stichtag als eigene
// Rechnung, die langen Horizonte und das Lesen alter und neuer Dateien.
class Fassung593FinderHaltenTest {

    private val tagMs = FinderRueckblick.TAG_MS
    private fun t(d: String) = LocalDate.parse(d).toEpochDay()
    private fun kerze(tag: Long, kurs: Double) = Candle(tag * tagMs + 14 * 3_600_000L, kurs, kurs, kurs, kurs)

    private fun wert(symbol: String, bewertung: Double, rendite: Double, h: Int = FinderRueckblick.BIS_HEUTE) =
        FinderRueckblickMessung.Wert(symbol, mapOf(Bereich.BEWERTUNG to bewertung), mapOf(h to rendite))

    private fun stichtag(tag: Long, werte: List<FinderRueckblickMessung.Wert>, msci: Double, spx: Double, h: Int = FinderRueckblick.BIS_HEUTE) =
        Stichtag(tag, werte, mapOf(h to msci), mapOf(h to spx), werte.size)

    @Test fun renditeBisZumLetztenKurs() {
        val k = listOf(kerze(0, 100.0), kerze(1000, 150.0))
        assertEquals(0.5, FinderRueckblick.renditeBis(k, 0, 1000)!!, 1e-9)
        // Letzter Kurs liegt auf dem Stichtag: nichts danach gekauft
        assertNull(FinderRueckblick.renditeBis(k, 1000, 2000))
        // Kurs am Stichtag aelter als sieben Tage
        assertNull(FinderRueckblick.renditeBis(k, 8, 1000))
        // Reihe endet vor heute: Rendite zum letzten Kurs
        val kurz = listOf(kerze(t("2020-01-01"), 100.0), kerze(t("2020-02-15"), 80.0))
        assertEquals(-0.2, FinderRueckblick.renditeBis(kurz, t("2020-01-01"), t("2021-12-31"))!!, 1e-9)
    }

    @Test fun haltenVomErstenStichtag() {
        val g = mapOf(Bereich.BEWERTUNG to 1.0)
        val heute = t("2025-01-31")
        // Drei Werte, alle unter den Top 20: Mittelwert
        val drei = listOf(stichtag(t("2012-01-31"), listOf(wert("A", 90.0, 0.10), wert("B", 50.0, 0.20), wert("C", 10.0, 0.30)), 0.05, 0.07))
        val h = FinderRueckblickMessung.haltenHeute(drei, g, heute)!!
        assertEquals(0.20, h.top20, 1e-9)
        assertEquals(0.20, h.universum, 1e-9)
        assertEquals(3, h.anzahl)
        assertEquals((heute - t("2012-01-31")) / 365.25, h.jahre, 1e-9)
        assertEquals("2012-01-31", h.von)
        assertEquals("2025-01-31", h.bis)
        // 25 Werte: nur die 20 mit den meisten Punkten zaehlen (S5 bis S24, Schnitt 0,145)
        val viele = listOf(stichtag(t("2012-01-31"), (0 until 25).map { wert("S$it", it.toDouble(), it / 100.0) }, 0.05, 0.07))
        val h2 = FinderRueckblickMessung.haltenHeute(viele, g, heute)!!
        assertEquals(0.145, h2.top20, 1e-9)
        assertEquals(20, h2.anzahl)
        // Ohne MSCI-Rendite bis heute oder ohne Stichtage: null
        val ohneMsci = listOf(Stichtag(t("2012-01-31"), listOf(wert("A", 90.0, 0.10)), emptyMap(), mapOf(FinderRueckblick.BIS_HEUTE to 0.07), 1))
        assertNull(FinderRueckblickMessung.haltenHeute(ohneMsci, g, heute))
        assertNull(FinderRueckblickMessung.haltenHeute(emptyList(), g, heute))
    }

    @Test fun horizontSechsunddreissigMonate() {
        val g = mapOf(Bereich.BEWERTUNG to 1.0)
        fun st(tag: Long, r: (Int) -> Double) = stichtag(tag, (0 until 25).map { wert("S$it", it.toDouble(), r(it), 36) }, 0.05, 0.07, h = 36)
        val st = listOf(st(t("2015-01-30")) { it / 100.0 }, st(t("2015-02-27")) { it / 50.0 })
        val h = FinderRueckblickMessung.horizont(st, g, 36)!!
        assertEquals(36, h.monate)
        assertEquals(2, h.stichtage)
        // Top 20 je Stichtag sind S5 bis S24: Schnitt 0,145 und 0,29, zusammen 0,2175
        assertEquals(0.2175, h.top20, 1e-9)
        assertNull(FinderRueckblickMessung.horizont(st.take(1), g, 36))
    }

    @Test fun dateiAltUndNeuLesen() {
        // Altes Format ohne halten und haltenHeute
        val alt = FinderRueckblickMessung.lesen("""{"stand":"2025-01-01","von":"2012-01-31","bis":"2024-12-31","stichtage":165,"werte":400}""")!!
        assertEquals(165, alt.stichtage)
        assertTrue(alt.halten.isEmpty())
        assertNull(alt.haltenHeute)
        // Neue Felder ueberstehen Schreiben und Lesen
        val hh = FinderRueckblickMessung.HaltenHeute("2012-01-31", "2025-06-30", 13.4, 2.0, 1.5, 1.8, 1.2, 18)
        val hz = FinderRueckblickMessung.Horizont(36, 100, 1.0, 1.1, 1.2, 1.3, 1.4, 0.1, 0.2, 0.5, 0.01, 0.02, 0.3, 0.04)
        val d = FinderRueckblickMessung.Datei(stand = "2025-07-01", halten = listOf(hz), haltenHeute = hh)
        assertEquals(d, FinderRueckblickMessung.lesen(FinderRueckblickMessung.schreiben(d)))
    }

    @Test fun zusammenfassenOhneHeute() {
        val g = mapOf(Bereich.BEWERTUNG to 1.0)
        val st = (0 until 13).map { m ->
            stichtag(LocalDate.of(2015, 1, 31).plusMonths(m.toLong()).toEpochDay(), (0 until 25).map { wert("S$it", it.toDouble(), 0.01, 1) }, 0.005, 0.006, h = 1)
        }
        val d = FinderRueckblickMessung.zusammenfassen(st, g, "2026-01-01", false)
        assertNull(d.haltenHeute)
        assertTrue(d.halten.isEmpty())
    }
}
