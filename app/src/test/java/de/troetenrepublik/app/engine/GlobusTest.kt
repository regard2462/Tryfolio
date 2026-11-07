package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Globus 0.69: Projektion, Rueckprojektion, Treffertest, Zeichnen und Einlesen der Laenderdatei.
class GlobusTest {

    private fun karte(): GlobusKarte {
        val f = listOf("src/main/assets/globus/laender.json", "../app/src/main/assets/globus/laender.json").map { File(it) }.first { it.exists() }
        return GlobusLesen.lesen(f.readText())!!
    }

    private class Zaehler : GlobusPfad {
        var punkte = 0
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        override fun nach(x: Float, y: Float) = erfassen(x)
        override fun linie(x: Float, y: Float) = erfassen(x)
        override fun schliessen() {}
        private fun erfassen(x: Float) {
            punkte++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
        }
    }

    @Test
    fun blickmitteLiegtInDerBildmitte() {
        val a = GlobusAnsicht()
        a.setze(10.0, 50.0, 200f, 200f, 100f)
        val aus = DoubleArray(2)
        assertTrue(a.rueck(200f, 200f, aus))
        assertEquals(10.0, aus[0], 1e-3)
        assertEquals(50.0, aus[1], 1e-3)
        assertFalse(a.rueck(0f, 0f, aus))
    }

    @Test
    fun rueckprojektionKehrtDieProjektionUm() {
        val a = GlobusAnsicht()
        a.setze(-70.0, 20.0, 300f, 250f, 180f)
        val lon = Math.toRadians(-60.0)
        val lat = Math.toRadians(35.0)
        val x = (Math.cos(lat) * Math.cos(lon)).toFloat()
        val y = (Math.cos(lat) * Math.sin(lon)).toFloat()
        val z = Math.sin(lat).toFloat()
        assertTrue(a.tiefe(x, y, z) > 0f)
        val aus = DoubleArray(2)
        assertTrue(a.rueck(a.px(x, y, z), a.py(x, y, z), aus))
        assertEquals(-60.0, aus[0], 0.02)
        assertEquals(35.0, aus[1], 0.02)
        // Osten liegt rechts, Norden oben
        a.setze(0.0, 0.0, 0f, 0f, 100f)
        assertTrue(a.px(0f, 1f, 0f) > 50f)
        assertTrue(a.py(0f, 0f, 1f) < -50f)
    }

    @Test
    fun treffertestFindetLaender() {
        val k = karte()
        assertEquals("DE", k.landBei(10.4, 51.0)?.a2)
        assertEquals("US", k.landBei(-98.0, 39.0)?.a2)
        assertEquals("JP", k.landBei(138.5, 36.0)?.a2)
        assertEquals("BR", k.landBei(-52.0, -10.0)?.a2)
        assertNull(k.landBei(-30.0, 0.0))
        // kleiner Staat per Toleranz: Singapur
        assertEquals("SG", k.landBei(103.9, 1.35, 1.0)?.a2)
    }

    @Test
    fun einlesenUndZuordnung() {
        val k = karte()
        assertTrue(k.laender.size > 200)
        assertNotNull(k.nachA3("DEU"))
        assertEquals("Deutschland", k.nachA2("DE")!!.de)
        assertEquals("Germany", k.nachA2("DE")!!.en)
        assertTrue(k.linien.isNotEmpty())
        assertNull(GlobusLesen.lesen("{\"v\":2}"))
        assertNull(GlobusLesen.lesen("kein json"))
    }

    @Test
    fun zeichnenNurSichtbareHalbkugel() {
        val k = karte()
        val a = GlobusAnsicht()
        a.setze(10.0, 50.0, 200f, 200f, 150f)
        val de = Zaehler()
        assertTrue(GlobusZeichner.flaeche(k.nachA3("DEU")!!, a, de, 0.5f))
        assertTrue(de.punkte > 10)
        assertTrue(de.minX > 200f - 150f && de.maxX < 200f + 150f)
        // Australien liegt auf der Rueckseite
        assertFalse(GlobusZeichner.flaeche(k.nachA3("AUS")!!, a, Zaehler(), 0.5f))
        val z = Zaehler()
        GlobusZeichner.linien(k.linien, a, z, 0.5f)
        assertTrue(z.punkte > 100)
        assertTrue(z.minX >= 200f - 150.5f && z.maxX <= 200f + 150.5f)
    }

    @Test
    fun farbenNachWert() {
        val up = 0xFF00FF00.toInt()
        val down = 0xFFFF0000.toInt()
        val n = 0xFFCC9900.toInt()
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.farbe(GlobusModus.BOERSE, null, up, down, n))
        assertEquals(up, GlobusFarbe.farbe(GlobusModus.BOERSE, 3.0, up, down, n))
        assertEquals(down, GlobusFarbe.farbe(GlobusModus.BOERSE, -3.0, up, down, n))
        val klein = GlobusFarbe.farbe(GlobusModus.BOERSE, 0.3, up, down, n)
        assertTrue(((klein ushr 8) and 0xFF) > ((klein ushr 16) and 0xFF))
        assertEquals(n, GlobusFarbe.farbe(GlobusModus.INFLATION, 40.0, up, down, n))
    }
}
