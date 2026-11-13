package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Rangliste der Analystenhaeuser [0.77]: Lesen der Buendeldatei, Reihenfolge, Namensabgleich; das mitgelieferte Asset.
class Fassung077AC4Test {

    private val probe = """{"schema":1,"stand":"2026-11-11","von":"2012-09-04","bis":"2026-11-10","aktien":10,"urteile":300,"minUrteile":20,
        "haeuser":[
        {"h":"Beta Research","u":40,"hoch":25,"ab":15,"q":0.5,"q3":0.5,"q6":0.5,"q12":null,"v":0.4,"a":8,"z":"2026-10"},
        {"h":"Alpha Capital","u":30,"hoch":20,"ab":10,"q":0.6,"q3":0.6,"q6":0.6,"q12":0.6,"v":2.5,"a":6,"z":"2026-09"},
        {"h":"Gamma","u":50,"hoch":20,"ab":30,"q":0.5,"q3":0.5,"q6":0.5,"q12":0.5,"v":-0.8,"a":9,"z":"2026-08"},
        {"h":"","u":50,"q":0.9},
        {"h":"Kaputt","u":50,"q":1.7}
        ]}"""

    @Test
    fun lesenSortiertNachTrefferanteilDannUrteilen() {
        val l = Haeuserliste.lesen(probe)!!
        assertEquals(listOf("Alpha Capital", "Gamma", "Beta Research"), l.haeuser.map { it.name })
        assertEquals(20, l.minUrteile)
        assertEquals(300, l.urteile)
        assertNull(l.haeuser.last().q12)
        assertEquals(-0.8, l.haeuser[1].vorsprung, 1e-9)
    }

    @Test
    fun namensabgleichIgnoriertSchreibweise() {
        val l = Haeuserliste.lesen(probe)!!
        assertNotNull(l.finden("ALPHA capital"))
        assertNotNull(l.finden("beta-research"))
        assertNull(l.finden("Delta"))
    }

    @Test
    fun falscheFormGibtNull() {
        assertNull(Haeuserliste.lesen("[]"))
        assertNull(Haeuserliste.lesen("""{"haeuser":[]}"""))
        assertNull(Haeuserliste.lesen("kein json"))
    }

    @Test
    fun zeitAusDatum() {
        assertEquals(1_794_355_200_000L, Haeuserliste.zeit("2026-11-11"))
        assertNull(Haeuserliste.zeit("morgen"))
    }

    private fun asset(): File = listOf(
        File("src/main/assets/analysten/haeuser.json"), File("app/src/main/assets/analysten/haeuser.json"), File("../app/src/main/assets/analysten/haeuser.json"),
    ).first { it.exists() }

    @Test
    fun mitgeliefertesAssetTraegt() {
        val f = asset()
        assertTrue(f.length() in 1000..1_000_000)
        val l = Haeuserliste.lesen(f.readText())!!
        assertTrue(l.haeuser.size >= 10)
        assertTrue(l.haeuser.all { it.urteile >= l.minUrteile && it.quote in 0.0..1.0 })
        assertEquals(l.haeuser, l.haeuser.sortedWith(compareByDescending<Haeuserliste.Haus> { it.quote }.thenByDescending { it.urteile }.thenBy { it.name }))
        assertTrue(l.stand.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        // Kopie unter daten/ ist dieselbe Datei
        val kopie = listOf(File("../daten/analysten/haeuser.json"), File("daten/analysten/haeuser.json")).first { it.exists() }
        assertEquals(f.readText(), kopie.readText())
    }

    @Test
    fun dateiStehtInPlanUndProfilen() {
        assertTrue("analysten/haeuser.json" in Alter.dateien())
        assertEquals(1, Datenbuendel.SCHEMA["analysten/haeuser.json"])
    }
}
