package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Radar.Art
import de.troetenrepublik.app.engine.Radar.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: Frueherkennung „Radar“ mit Gewichten nach Art und Alter, Daempfung bekannter Werte und den Erkennern.
class Fassung592RadarTest {

    private val heute = 20_000L

    @Test fun neuheitHalbiertNachHalbwertszeit() {
        assertEquals(1.0, Radar.neuheit(0, 3.0), 1e-9)
        assertEquals(0.5, Radar.neuheit(3, 3.0), 1e-9)
        assertEquals(0.25, Radar.neuheit(42, 21.0), 1e-9)
        // Zukunft (Zeitzonen) zaehlt wie heute
        assertEquals(1.0, Radar.neuheit(-1, 3.0), 1e-9)
    }

    @Test fun mehrereArtenZaehlenMehr() {
        val eine = Radar.bewerten(listOf(Signal("A", Art.VOLUMEN, heute, 0.6)), heute).single()
        val zwei = Radar.bewerten(listOf(Signal("A", Art.VOLUMEN, heute, 0.6), Signal("A", Art.ERWAEHNUNGEN, heute, 0.6)), heute).single()
        assertEquals(2, zwei.arten)
        assertTrue(zwei.punkte > eine.punkte * 1.5)
        // Weitere Signale derselben Art zaehlen nur ein Drittel
        val doppelt = Radar.bewerten(listOf(Signal("A", Art.VOLUMEN, heute, 0.6), Signal("A", Art.VOLUMEN, heute - 1, 0.6)), heute).single()
        assertTrue(doppelt.punkte < zwei.punkte)
        assertTrue(doppelt.punkte > eine.punkte)
        assertTrue(zwei.punkte in 0.0..100.0)
    }

    @Test fun altesFaelltWegUndNeuesGehtVor() {
        val l = Radar.bewerten(
            listOf(
                Signal("ALT", Art.INSIDER_GRUPPE, heute - 60, 1.0),
                Signal("NEU", Art.INSIDER_GRUPPE, heute - 2, 1.0),
                Signal("WEG", Art.INSIDER_GRUPPE, heute - 120, 1.0),
                Signal("NULL", Art.VOLUMEN, heute, 0.0),
            ),
            heute,
        )
        assertEquals(listOf("NEU", "ALT"), l.map { it.symbol })
        assertEquals(heute - 2, l.first().neuester)
    }

    @Test fun bekannteWerteGedaempft() {
        val s = listOf(Signal("GME", Art.ERWAEHNUNGEN, heute, 1.0), Signal("XYZ", Art.ERWAEHNUNGEN, heute, 1.0))
        val l = Radar.bewerten(s, heute, bekannt = setOf("GME"))
        assertEquals("XYZ", l.first().symbol)
        assertTrue(l.last().bekannt)
        assertFalse(l.first().bekannt)
    }

    @Test fun erwaehnungen() {
        // Zu wenige Erwaehnungen oder kein Anstieg
        assertNull(Radar.erwaehnungen(10, 2))
        assertNull(Radar.erwaehnungen(40, 35))
        assertNull(Radar.erwaehnungen(40, null))
        val stark = Radar.erwaehnungen(200, 20)!!
        val maessig = Radar.erwaehnungen(40, 20)!!
        assertTrue(stark > maessig)
        assertTrue(stark <= 1.0 && maessig >= 0.15)
        // Mit Verlauf zaehlt der Schnitt der Woche davor statt des Vortags
        assertNull(Radar.erwaehnungen(60, 10, listOf(50, 55, 60, 58, 52, 60)))
        assertNotNull(Radar.erwaehnungen(60, 50, listOf(5, 6, 4, 5, 6, 60)))
    }

    @Test fun volumenUndWikipedia() {
        assertNull(Radar.volumen(200.0, 100.0))
        assertNull(Radar.volumen(500.0, 0.0))
        assertEquals(0.1, Radar.volumen(250.0, 100.0)!!, 1e-9)
        assertEquals(1.0, Radar.volumen(900.0, 100.0)!!, 1e-9)
        assertNull(Radar.wikipedia(30.0))
        assertEquals(0.5, Radar.wikipedia(150.0)!!, 1e-9)
        assertEquals(1.0, Radar.wikipedia(900.0)!!, 1e-9)
    }

    @Test fun insiderGruppeUndEinzelkauf() {
        val r = InsiderTrend.Rolle.AUFSICHT
        val gruppe = Radar.insider(
            "A",
            listOf(
                Radar.InsiderKauf("Eins", r, 50_000.0, heute - 20),
                Radar.InsiderKauf("Zwei", r, 80_000.0, heute - 10),
                Radar.InsiderKauf("Drei", InsiderTrend.Rolle.SPITZE, 1_000_000.0, heute - 5),
            ),
        )
        assertEquals(1, gruppe.size)
        assertEquals(Art.INSIDER_GRUPPE, gruppe[0].art)
        assertEquals(3.0, gruppe[0].zahl, 1e-9)
        assertEquals(1_130_000.0, gruppe[0].zahl2, 1e-9)
        assertEquals(heute - 5, gruppe[0].tag)
        // Drei Kaeufer, aber mehr als 30 Tage auseinander: nur der grosse Kauf der Spitze
        val einzeln = Radar.insider(
            "B",
            listOf(
                Radar.InsiderKauf("Eins", r, 50_000.0, heute - 80),
                Radar.InsiderKauf("Zwei", r, 80_000.0, heute - 40),
                Radar.InsiderKauf("Chef", InsiderTrend.Rolle.SPITZE, 500_000.0, heute - 2),
            ),
        )
        assertEquals(listOf(Art.INSIDER_KAUF), einzeln.map { it.art })
        assertEquals("Chef", einzeln[0].text)
        // Kleine Kaeufe der Aufsicht allein ergeben nichts
        assertTrue(Radar.insider("C", listOf(Radar.InsiderKauf("Eins", r, 500_000.0, heute))).isEmpty())
    }

    @Test fun politikGrossaktionaerOptionenPrognose() {
        val p = Radar.politik("NVDA", Art.KONGRESS, listOf(Triple("Anna", 8_000.0, heute - 3), Triple("Ben", 32_500.0, heute - 1), Triple("Anna", 8_000.0, heute - 2)))!!
        assertEquals(2.0, p.zahl, 1e-9)
        assertEquals(48_500.0, p.zahl2, 1e-9)
        assertEquals(heute - 1, p.tag)
        assertEquals("", p.text)
        assertEquals("Ben", Radar.politik("X", Art.REGIERUNG, listOf(Triple("Ben", 1_000.0, heute)))!!.text)
        assertNull(Radar.politik("X", Art.KONGRESS, emptyList()))

        assertTrue(Radar.grossaktionaer("A", heute, 9.8, "Fonds").staerke > Radar.grossaktionaer("A", heute, null, "Fonds").staerke)

        val k = Optionsanalyse.Kontrakt(true, 100.0, 0L, 2.0, 1.9, 2.0, 5_000, 1_000, 0.5)
        val call = Optionsanalyse.Auffaellig(k, 1_000_000.0, 5.0, Optionsanalyse.Seite.KAUF, 30.0)
        val put = call.copy(kontrakt = k.copy(call = false))
        val verkauft = call.copy(seite = Optionsanalyse.Seite.VERKAUF)
        assertNotNull(Radar.optionen("A", heute, listOf(call, put)))
        assertNull(Radar.optionen("A", heute, listOf(put, verkauft)))
        assertEquals(1.0, Radar.optionen("A", heute, listOf(call))!!.zahl, 1e-9)

        assertNull(Radar.prognose("A", heute, 5_000.0, "x"))
        assertTrue(Radar.prognose("A", heute, 1_000_000.0, "x")!!.staerke > Radar.prognose("A", heute, 20_000.0, "x")!!.staerke)
    }

    @Test fun titelNenntUnternehmen() {
        assertTrue(Radar.nennt("Will Nvidia be the largest company by end of 2026?", "NVIDIA"))
        assertTrue(Radar.nennt("Tesla deliveries above 500k in Q4?", "Tesla Inc."))
        assertFalse(Radar.nennt("Will Nvidia...?", "Intel"))
        // Kurze Namen und Teile anderer Woerter zaehlen nicht
        assertFalse(Radar.nennt("Will SAP...", "SAP"))
        assertFalse(Radar.nennt("Applebee's closes stores", "Apple"))
        assertTrue(Radar.nennt("Apple announces...", "Apple"))
    }
}
