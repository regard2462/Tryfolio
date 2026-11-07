package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Globus Ebenen: Rangliste, Depotanteile je Land, Rezessionszeichen aus FRED-CSV, Farben der neuen Ebenen.
class GlobusEbenenTest {
    @Test
    fun ranglisteTeiltBeiWenigWerten() {
        val w = (1..8).map { GlobusEbenen.Rang("L$it", it.toDouble()) }
        val (oben, unten) = GlobusEbenen.rangliste(w, 5)
        assertEquals(listOf("L8", "L7", "L6", "L5", "L4"), oben.map { it.code })
        assertEquals(listOf("L1", "L2", "L3"), unten.map { it.code })
        assertTrue(GlobusEbenen.rangliste(emptyList()).first.isEmpty())
    }

    @Test
    fun depotAnteile() {
        val land = mapOf("AAPL" to "US", "SAP.DE" to "DE", "MSFT" to "US")
        val a = GlobusEbenen.anteile(listOf("AAPL" to 300.0, "MSFT" to 200.0, "SAP.DE" to 100.0, "EUNL.DE" to 300.0, "BTC-USD" to 100.0)) { land[it] }
        assertEquals(0.5, a.nachLand["US"]!!, 1e-9)
        assertEquals(0.1, a.nachLand["DE"]!!, 1e-9)
        assertEquals(0.4, a.unzuordenbar, 1e-9)
        assertEquals("US", a.groesstes!!.key)
        assertTrue(GlobusEbenen.anteile(emptyList()) { null }.leer)
    }

    @Test
    fun fredReihenLesen() {
        val csv = "observation_date,T10Y3M\n2026-11-03,0.71\n2026-11-04,0.69\n2026-11-05,0.67\n"
        val w = WeltRezession.letzter(csv)!!
        assertEquals(0.67, w.wert, 1e-9)
        assertEquals(java.time.LocalDate.of(2026, 11, 5).toEpochDay(), w.tag)
        // Punkt als fehlender Wert faellt weg
        assertEquals(0.4, WeltRezession.letzter("observation_date,RECPROUSM156N\n2026-08-01,0.20\n2026-09-01,0.40\n2026-10-01,.\n")!!.wert, 1e-9)
        assertNull(WeltRezession.letzter("observation_date,X\n"))
    }

    @Test
    fun rezessionDatenZusammenUndSpeichern() {
        val alt = WeltRezession.Daten(1L, WeltRezession.Wert(1, 0.5), WeltRezession.Wert(2, 0.0), null)
        val neu = WeltRezession.zusammen(WeltRezession.Daten(9L, null, WeltRezession.Wert(3, 0.1), WeltRezession.Wert(3, 0.4)), alt)
        assertEquals(0.5, neu.zinskurve!!.wert, 1e-9)
        assertEquals(0.1, neu.sahm!!.wert, 1e-9)
        assertEquals(neu, WeltRezession.lesenGespeichert(WeltRezession.schreiben(neu)))
        assertTrue(WeltRezession.veraltet(neu, 9L + WeltRezession.GUELTIG_MS))
        assertFalse(WeltRezession.veraltet(neu, 10L))
    }

    @Test
    fun farbenDerNeuenEbenen() {
        val up = 0xFF00FF00.toInt()
        val down = 0xFFFF0000.toInt()
        val n = 0xFFFFAA00.toInt()
        // Arbeitslosigkeit und Schulden: niedrig gut
        assertEquals(up, GlobusFarbe.farbe(GlobusModus.ARBEITSLOS, 0.0, up, down, n))
        assertEquals(down, GlobusFarbe.farbe(GlobusModus.ARBEITSLOS, 20.0, up, down, n))
        assertEquals(up, GlobusFarbe.farbe(GlobusModus.SCHULDEN, 0.0, up, down, n))
        assertEquals(down, GlobusFarbe.farbe(GlobusModus.SCHULDEN, 120.0, up, down, n))
        assertEquals(up, GlobusFarbe.farbe(GlobusModus.LEISTUNG, 8.0, up, down, n))
        assertEquals(down, GlobusFarbe.farbe(GlobusModus.LEISTUNG, -8.0, up, down, n))
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.farbe(GlobusModus.SCHULDEN, null, up, down, n))
        assertEquals(up, GlobusFarbe.phase(Fruehindikator.Phase.AUFSCHWUNG, up, down, n))
        assertEquals(n, GlobusFarbe.phase(Fruehindikator.Phase.ABSCHWAECHUNG, up, down, n))
        assertEquals(down, GlobusFarbe.phase(Fruehindikator.Phase.ABSCHWUNG, up, down, n))
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.phase(null, up, down, n))
        assertEquals(up, GlobusFarbe.handel(true, up))
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.handel(null, up))
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.anteil(0.0, n))
        assertEquals(n, GlobusFarbe.anteil(0.5, n))
        // Die ersten vier Ebenen behalten ihre Reihenfolge (gespeicherte Auswahl, Widget)
        assertEquals(0, GlobusModus.BOERSE.ordinal)
        assertEquals(3, GlobusModus.INFLATION.ordinal)
    }
}
