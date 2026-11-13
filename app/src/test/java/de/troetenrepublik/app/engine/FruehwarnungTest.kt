package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Antworten im Format von US-Finanzministerium, BLS und AAII, erfundene Werte.
class FruehwarnungTest {

    private val tag = 86_400_000L
    private val start = BrokerExport.zeit("2026-01-02")

    @Test
    fun zinskurve() {
        val csv = """Date,"1 Mo","2 Mo","3 Mo","6 Mo","1 Yr","2 Yr","10 Yr","30 Yr"
            10/20/2026,4.10,4.08,4.00,3.90,3.80,3.70,4.25,4.70
            10/17/2026,4.12,4.09,4.05,3.92,3.81,3.71,3.95,4.60
        """.trimIndent()
        val r = Fruehwarnung.treasuryLesen(csv)
        assertEquals(2, r.size)
        assertTrue(r[0].first < r[1].first)
        assertEquals(0.25, r[1].second, 1e-9)
        assertEquals(-0.10, r[0].second, 1e-9)
        // Nach einer Inversion wieder ueber null: Rot.
        assertEquals(Ampel.ROT, Fruehwarnung.zinskurve(r)!!.ampel)
        assertEquals(Ampel.GELB, Fruehwarnung.zinskurve(r.take(1))!!.ampel)
        assertEquals(Ampel.GRUEN, Fruehwarnung.zinskurve(r.drop(1))!!.ampel)
        // Eine Inversion vor mehr als 18 Monaten zaehlt nicht mehr.
        val alt = listOf(r[1].first - 600 * tag to -0.5, r[1])
        assertEquals(Ampel.GRUEN, Fruehwarnung.zinskurve(alt)!!.ampel)
        assertTrue(Fruehwarnung.treasuryLesen("kein csv").isEmpty())
        assertNull(Fruehwarnung.zinskurve(emptyList()))
    }

    @Test
    fun sahm() {
        // 2025 konstant 4,0 %, danach steigt die Quote.
        val quoten = List(12) { 4.0 } + listOf(4.0, 4.1, 4.3, 4.6, 4.8)
        val daten = quoten.mapIndexed { i, q ->
            val jahr = 2025 + i / 12
            """{"year": "$jahr", "period": "M${(i % 12 + 1).toString().padStart(2, '0')}", "value": "$q"}"""
        }.reversed().joinToString(",")
        val r = Fruehwarnung.blsLesen("""{"status": "REQUEST_SUCCEEDED", "Results": {"series": [{"seriesID": "LNS14000000", "data": [$daten]}]}}""")
        assertEquals(17, r.size)
        assertTrue(r.first().first < r.last().first)
        val z = Fruehwarnung.sahm(r)!!
        // (4,3 + 4,6 + 4,8) / 3 - 4,0
        assertEquals(0.5667, z.wert, 1e-3)
        assertEquals(Ampel.ROT, z.ampel)
        assertEquals(Ampel.GRUEN, Fruehwarnung.sahm(r.take(15))!!.ampel)
        assertNull(Fruehwarnung.sahm(r.take(10)))
        assertTrue(Fruehwarnung.blsLesen("""{"status": "REQUEST_NOT_PROCESSED"}""").isEmpty())
    }

    @Test
    fun vix() {
        val vix = listOf(start to 18.0, start + tag to 24.0)
        assertEquals(Ampel.GELB, Fruehwarnung.vix(vix)!!.ampel)
        assertEquals(Ampel.ROT, Fruehwarnung.vix(vix + (start + 2 * tag to 31.0))!!.ampel)
        val drei = listOf(start to 20.0, start + tag + 3_600_000 to 22.0)
        val s = Fruehwarnung.vixStruktur(vix, drei)!!
        assertEquals(24.0 / 22.0, s.wert, 1e-9)
        assertEquals(Ampel.ROT, s.ampel)
        assertEquals(Ampel.GRUEN, Fruehwarnung.vixStruktur(vix.take(1), drei)!!.ampel)
        assertNull(Fruehwarnung.vixStruktur(vix, emptyList()))
    }

    @Test
    fun trend() {
        val kurse = (0 until 260).map { start + it * tag to 100.0 + it }
        val z = Fruehwarnung.trend(Fruehwarnung.TREND_SP500, kurse)!!
        // Letzter Kurs 359, Schnitt der letzten 200: 259,5.
        assertEquals((359.0 / 259.5 - 1) * 100, z.wert, 1e-9)
        assertEquals(Ampel.GRUEN, z.ampel)
        assertEquals(61, z.verlauf.size)
        val fallend = kurse + (start + 260 * tag to 250.0)
        assertEquals(Ampel.ROT, Fruehwarnung.trend(Fruehwarnung.TREND_DAX, fallend)!!.ampel)
        assertNull(Fruehwarnung.trend(Fruehwarnung.TREND_DAX, kurse.take(150)))
    }

    @Test
    fun aaii() {
        fun zeile(d: String, b: String, n: String, s: String) =
            """<tr><td class="tableTxt">$d</td><td class="tableTxt">$b</td><td class="tableTxt">$n</td><td class="tableTxt">$s</td></tr>"""
        val html = "<table>" + zeile("Jan 7", "40.0%", "30.0%", "30.0%") + zeile("Dec 31", "30.0%", "30.0%", "40.0%") +
            zeile("Dec 24", "20.0%", "40.0%", "40.0%") + "</table>"
        val jetzt = BrokerExport.zeit("2027-01-09")
        val w = Fruehwarnung.aaiiLesen(html, jetzt)
        assertEquals(3, w.size)
        assertEquals(BrokerExport.zeit("2027-01-07"), w[0].zeit)
        assertEquals(BrokerExport.zeit("2026-12-31"), w[1].zeit)
        assertEquals(40.0, w[0].bullisch, 1e-9)
        val b = Stimmungsbarometer.aaiiBarometer(w)!!
        assertEquals(100.0 * 40 / 70, b.barometer.wert, 1e-9)
        assertEquals(50.0 * 30 / 35, b.barometer.vortag!!, 1e-9)
        assertEquals(listOf(w[2].zeit, w[1].zeit, w[0].zeit), b.barometer.verlauf.map { it.first })
        assertTrue(Fruehwarnung.aaiiLesen("<html></html>", jetzt).isEmpty())
        assertNull(Stimmungsbarometer.aaiiBarometer(emptyList()))
        // Steht im Oktober eine Woche aus dem Dezember oben, ist sie aus dem Vorjahr.
        assertEquals(BrokerExport.zeit("2025-12-31"), Fruehwarnung.aaiiLesen(zeile("Dec 31", "1%", "1%", "1%"), BrokerExport.zeit("2026-10-21"))[0].zeit)
    }

    @Test
    fun jahrMonat() {
        assertEquals(2026 to 10, Fruehwarnung.jahrMonat(BrokerExport.zeit("2026-10-21")))
        assertEquals(2024 to 2, Fruehwarnung.jahrMonat(BrokerExport.zeit("2024-02-29")))
        assertEquals(1970 to 1, Fruehwarnung.jahrMonat(0L))
    }
}
