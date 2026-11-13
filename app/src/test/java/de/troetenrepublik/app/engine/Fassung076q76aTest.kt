package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

// 0.76 Engpass-Ausschnitt: Begrenzungsrahmen, Radius, Projektion, Anker und Platzierung der Namen.
class Fassung076q76aTest {

    private val karte: GlobusKarte by lazy {
        val f = listOf("src/main/assets/globus/laender.json", "../app/src/main/assets/globus/laender.json").map { File(it) }.first { it.exists() }
        GlobusLesen.lesen(f.readText())!!
    }

    private fun stelle(id: String) = Engpaesse.stelle(id)!!

    private fun abstand(l0: Double, b0: Double, l1: Double, b1: Double): Double {
        val a = Math.toRadians(b0); val c = Math.toRadians(b1); val d = Math.toRadians(l1 - l0)
        return Math.toDegrees(acos((sin(a) * sin(c) + cos(a) * cos(c) * cos(d)).coerceIn(-1.0, 1.0)))
    }

    @Test
    fun radiusLaesstDieHalbeBreiteGenauSoWeitReichen() {
        val s = stelle("chokepoint6")
        val a = GlobusAnsicht()
        val rahmen = EngpassAusschnitt.ansicht(s, 600f, 400f, a)
        val aus = DoubleArray(2)
        assertEquals(EngpassAusschnitt.halbGrad(s.id), abstandRand(a, 0f, 200f, aus, s), 1e-3)
        assertEquals(EngpassAusschnitt.halbGrad(s.id), abstandRand(a, 600f, 200f, aus, s), 1e-3)
        assertTrue(rahmen.enthaelt(s.lon, s.lat))
        assertEquals(s.lon, rahmen.lon, 1e-9)
    }

    private fun abstandRand(a: GlobusAnsicht, x: Float, y: Float, aus: DoubleArray, s: Engpaesse.Stelle): Double {
        assertTrue(a.rueck(x, y, aus))
        return abstand(s.lon, s.lat, aus[0], aus[1])
    }

    @Test
    fun engpassSitztInDerBildmitte() {
        for (s in Engpaesse.STELLEN) {
            val a = GlobusAnsicht()
            EngpassAusschnitt.ansicht(s, 600f, 400f, a)
            val p = FloatArray(2)
            assertTrue(s.id, a.bildpunkt(s.lon, s.lat, p))
            assertEquals(s.id, 300f, p[0], 0.5f)
            assertEquals(s.id, 200f, p[1], 0.5f)
        }
    }

    @Test
    fun hoeheFolgtDemSeitenverhaeltnis() {
        val h = EngpassAusschnitt.halbHoeheGrad(6.0)
        assertEquals(6.0 / EngpassAusschnitt.SEITENVERHAELTNIS, h, 0.05)
        assertTrue(EngpassAusschnitt.halbHoeheGrad(6.0, 1f) > h)
    }

    @Test
    fun laengeWirdMitDerBreiteGedehnt() {
        val aeq = EngpassAusschnitt.rahmen(stelle("chokepoint15"))
        val hoch = EngpassAusschnitt.rahmen(stelle("chokepoint26"))
        assertTrue(aeq.halbLon < hoch.halbLon)
        assertTrue(hoch.halbLon > EngpassAusschnitt.halbGrad("chokepoint26"))
        // Der Rand des Bildes liegt im Rahmen
        val s = stelle("chokepoint26")
        val a = GlobusAnsicht()
        val r = EngpassAusschnitt.ansicht(s, 600f, 400f, a)
        val aus = DoubleArray(2)
        for ((x, y) in listOf(0f to 200f, 600f to 200f, 300f to 0f, 300f to 400f)) {
            assertTrue(a.rueck(x, y, aus))
            // knapp vom Rand nach innen, wegen der Rundung der Randpunkte
            val b = aus[1] + (if (y == 0f) -0.01 else if (y == 400f) 0.01 else 0.0)
            assertTrue("$x $y ${aus[0]} $b", r.enthaelt(aus[0], b) || r.enthaelt(aus[0] + 360.0, b) || r.enthaelt(aus[0] - 360.0, b))
        }
    }

    @Test
    fun rahmenKenntDenSprungUeberDieDatumsgrenze() {
        val r = EngpassAusschnitt.rahmen(stelle("chokepoint26"))
        // Beringstrasse bei -165,5 Grad: der Rahmen reicht ueber -180 hinaus, Chukotka bei +178 liegt darin
        assertTrue(r.minLon < -180.0)
        assertTrue(r.enthaelt(178.0, 66.0))
        assertFalse(r.enthaelt(100.0, 66.0))
        assertFalse(r.enthaelt(-165.0, 40.0))
    }

    @Test
    fun nurNaheLaenderBeruehrenDenRahmen() {
        val r = EngpassAusschnitt.rahmen(stelle("chokepoint6"))
        assertTrue(EngpassAusschnitt.beruehrt(karte.nachA3("IRN")!!, r))
        assertTrue(EngpassAusschnitt.beruehrt(karte.nachA3("OMN")!!, r))
        assertFalse(EngpassAusschnitt.beruehrt(karte.nachA3("BRA")!!, r))
        val b = EngpassAusschnitt.rahmen(stelle("chokepoint26"))
        assertTrue(EngpassAusschnitt.beruehrt(karte.nachA3("RUS")!!, b))
        assertTrue(EngpassAusschnitt.beruehrt(karte.nachA3("USA")!!, b))
    }

    @Test
    fun ankerLiegtImBildAufDemLand() {
        val s = stelle("chokepoint6")
        val a = GlobusAnsicht()
        EngpassAusschnitt.ansicht(s, 600f, 400f, a)
        val iran = karte.nachA3("IRN")!!
        val an = EngpassAusschnitt.anker(iran, a, 600f, 400f)
        assertNotNull(an)
        assertTrue(an!![0] in 0f..600f && an[1] in 0f..400f)
        val aus = DoubleArray(2)
        assertTrue(a.rueck(an[0], an[1], aus))
        assertTrue(iran.enthaelt(aus[0], aus[1]))
        // Iran liegt nördlich der Strasse, also oberhalb der Bildmitte
        assertTrue(an[1] < 200f)
        assertNull(EngpassAusschnitt.anker(karte.nachA3("BRA")!!, a, 600f, 400f))
    }

    @Test
    fun jederEngpassNenntMindestensEinenAnrainer() {
        for (s in Engpaesse.STELLEN) {
            val a = GlobusAnsicht()
            val r = EngpassAusschnitt.ansicht(s, 600f, 400f, a)
            val namen = EngpassAusschnitt.namen(karte, s, a, 600f, 400f, r)
            assertTrue(s.id, namen.any { it.vorrang })
            // Anrainer stehen vor den uebrigen
            val erster = namen.indexOfFirst { !it.vorrang }
            if (erster >= 0) assertTrue(s.id, namen.drop(erster).none { it.vorrang })
        }
    }

    @Test
    fun platzierenLaesstNamenWeichenOderEntfallen() {
        val sperre = listOf(floatArrayOf(90f, 40f, 110f, 60f))
        val p = listOf(
            EngpassAusschnitt.Platz(100f, 50f, 60f, 14f, true),
            EngpassAusschnitt.Platz(100f, 50f, 60f, 14f, false),
            EngpassAusschnitt.Platz(5f, 5f, 60f, 14f, false),
            EngpassAusschnitt.Platz(100f, 50f, 400f, 14f, true),
        )
        val g = EngpassAusschnitt.platzieren(p, sperre, 200f, 100f, 3f)
        // Mit Vorrang: weicht der Sperre aus
        val e = g[0]!!
        assertFalse(e[0] - 3f < 110f && e[0] + 60f + 3f > 90f && e[1] - 3f < 60f && e[1] + 14f + 3f > 40f)
        // Ohne Vorrang auf demselben Platz: entfaellt
        assertNull(g[1])
        // Am Rand: bleibt ganz im Bild
        val r = g[2]!!
        assertTrue(r[0] >= 3f && r[1] >= 3f)
        // Breiter als das Bild: entfaellt
        assertNull(g[3])
        // Alle gesetzten Texte bleiben im Bild
        for (k in g.filterNotNull()) assertTrue(k[0] + 60f <= 200f && k[1] + 14f <= 100f)
    }
}
