package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.GdpNowTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// GDPNow im Ausblick (0.78): Staende der Reihe an mehreren Tagen lesen. Die Dateien unter resources/konjunktur haben
// den Aufbau der CSV der Quelle, die Zahlen sind erfunden.
class Fassung078U34GdpNowTest {

    private fun datei(name: String): String = javaClass.classLoader!!.getResourceAsStream("konjunktur/$name.txt")!!.readBytes().toString(Charsets.UTF_8)

    private fun tag(j: Int, m: Int, t: Int) = LocalDate.of(j, m, t).toEpochDay()

    @Test
    fun laufendesQuartalMitVerlauf() {
        val s = GdpNow.lesen(listOf(datei("gdpnow-fern"), datei("gdpnow-nah")))!!
        assertEquals(tag(2030, 10, 1), s.quartal)
        assertEquals(4, s.nummer)
        assertEquals(2030, s.jahr)
        assertEquals(-0.3, s.wert, 1e-9)
        // Der Wert gilt seit dem ersten Tag der letzten gleichen Staende
        assertEquals(tag(2030, 11, 9), s.stand)
        // Tage ohne Schaetzung fuer das Quartal fehlen im Verlauf
        assertEquals(listOf(tag(2030, 10, 29), tag(2030, 11, 5), tag(2030, 11, 8), tag(2030, 11, 9)), s.verlauf.take(4).map { it.first })
        assertEquals(7, s.verlauf.size)
        assertEquals(0.8, s.verlauf.first().second, 1e-9)
        // Vorquartal: juengster Stand
        assertEquals(2.2, s.vorquartal!!, 1e-9)
    }

    @Test
    fun eineDateiReicht() {
        val s = GdpNow.lesen(listOf(datei("gdpnow-fern")))!!
        assertEquals(0.4, s.wert, 1e-9)
        assertEquals(tag(2030, 11, 5), s.stand)
        assertEquals(2, s.verlauf.size)
    }

    @Test
    fun unlesbaresErgibtNichts() {
        assertNull(GdpNow.lesen(emptyList()))
        assertNull(GdpNow.lesen(listOf("<html>Fehler</html>")))
        assertNull(GdpNow.lesen(listOf("observation_date,GDPNOW\n2030-10-01,0.4")))
        assertNull(GdpNow.lesen(listOf("observation_date,GDPNOW_20301105\n2030-10-01,.")))
    }

    @Test
    fun adresseUndTage() {
        val heute = LocalDate.of(2030, 11, 12)
        val nah = GdpNow.tageNah(heute)
        val fern = GdpNow.tageFern(heute)
        assertEquals(GdpNow.SPALTEN, nah.size)
        assertEquals(heute, nah.last())
        assertEquals(heute.minusDays(11), nah.first())
        assertEquals(heute.minusWeeks(2), fern.last())
        assertTrue(fern.first() < nah.first())
        val a = GdpNow.adresse(listOf(LocalDate.of(2030, 11, 5), heute))
        assertEquals("https://alfred.stlouisfed.org/graph/alfredgraph.csv?id=GDPNOW,GDPNOW&vintage_date=2030-11-05,2030-11-12", a)
    }

    @Test
    fun stufen() {
        assertEquals(0, GdpNow.stufe(-0.1))
        assertEquals(1, GdpNow.stufe(0.0))
        assertEquals(1, GdpNow.stufe(0.9))
        assertEquals(2, GdpNow.stufe(1.0))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(GdpNowTexte.schluessel(true), GdpNowTexte.schluessel(false))
        for (k in GdpNowTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = GdpNowTexte.text(d, k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains("{"))
            assertFalse(k, t.contains("—") || t.contains("–"))
        }
    }
}
