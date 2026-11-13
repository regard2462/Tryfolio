package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.MarktTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Lebensrenditen der US-Aktien: Leser mit Gueltigkeitspruefung, Anteile, mitgelieferte Auszaehlung, Pflege und Nennung.
class Fassung078U42LebensrenditenTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun datei(
        aktien: Int = 10, negativ: Int = 6, klassen: String = """[{"von":-100,"bis":0,"n":6},{"von":0,"bis":100,"n":3},{"von":100,"bis":null,"n":1}]""",
        firmen: Int = 8, mehrwert: Int = 3, haelfte: Int = 1, alles: Int = 2, stand: String = "2026-11-12",
    ) = """{"stand":"$stand","schema":1,"von":1926,"bis":2025,"aktien":$aktien,"negativ":$negativ,"median":-12.5,"klassen":$klassen,
        "firmen":$firmen,"mehrwert":$mehrwert,"haelfte":$haelfte,"alles":$alles}"""

    @Test
    fun leserUndAnteile() {
        val d = Lebensrenditen.lesen(datei())!!
        assertEquals(1926, d.von)
        assertEquals(2025, d.bis)
        assertEquals(3, d.klassen.size)
        assertNull(d.klassen.last().bis)
        assertEquals(-12.5, d.median, 0.0)
        assertEquals(60.0, Lebensrenditen.anteilNegativ(d), 1e-9)
        assertEquals(listOf(60.0, 30.0, 10.0), Lebensrenditen.anteile(d))
        assertEquals(62.5, Lebensrenditen.anteilUnterSchatz(d), 1e-9)
        assertEquals(25.0, Lebensrenditen.anteilAlles(d), 1e-9)
        assertEquals(12.5, Lebensrenditen.anteilHaelfte(d), 1e-9)
    }

    @Test
    fun ungueltigeDateienFallenWeg() {
        assertNull(Lebensrenditen.lesen("{}"))
        assertNull(Lebensrenditen.lesen("kein json"))
        assertNull(Lebensrenditen.lesen(datei(stand = "irgendwann")))
        // Klassen ergeben nicht die Zahl der Aktien
        assertNull(Lebensrenditen.lesen(datei(aktien = 11)))
        // Luecke zwischen den Klassen
        assertNull(Lebensrenditen.lesen(datei(klassen = """[{"von":-100,"bis":0,"n":6},{"von":10,"bis":100,"n":3},{"von":100,"bis":null,"n":1}]""")))
        // Letzte Klasse nicht offen, offene Klasse in der Mitte
        assertNull(Lebensrenditen.lesen(datei(klassen = """[{"von":-100,"bis":0,"n":6},{"von":0,"bis":100,"n":4}]""")))
        assertNull(Lebensrenditen.lesen(datei(klassen = """[{"von":-100,"bis":null,"n":6},{"von":0,"bis":null,"n":4}]""")))
        // Teilmengen groesser als das Ganze
        assertNull(Lebensrenditen.lesen(datei(negativ = 11)))
        assertNull(Lebensrenditen.lesen(datei(mehrwert = 9)))
        assertNull(Lebensrenditen.lesen(datei(haelfte = 3, alles = 2)))
        assertNull(Lebensrenditen.lesen(datei(alles = 9)))
        assertNull(Lebensrenditen.lesen(datei(klassen = """[{"von":-100,"bis":0,"n":-1},{"von":0,"bis":null,"n":11}]""")))
    }

    @Test
    fun mitgelieferteAuszaehlung() {
        val d = Lebensrenditen.lesen(asset(Lebensrenditen.DATEI).readText(Charsets.UTF_8))!!
        assertEquals(1926, d.von)
        assertEquals(2025, d.bis)
        assertEquals(29754, d.aktien)
        assertEquals(29081, d.firmen)
        assertEquals(8, d.klassen.size)
        assertEquals(-100, d.klassen.first().von)
        assertEquals(listOf(9417, 2443, 1798, 1719, 5072, 4757, 1386, 3162), d.klassen.map { it.anzahl })
        // Die Klassen unter null ergeben genau die Aktien mit Verlust
        assertEquals(d.negativ, d.klassen.filter { (it.bis ?: 1) <= 0 }.sumOf { it.anzahl })
        assertEquals(51.68, Lebensrenditen.anteilNegativ(d), 0.005)
        assertEquals(-6.87, d.median, 0.0)
        assertEquals(59.13, Lebensrenditen.anteilUnterSchatz(d), 0.005)
        assertEquals(3.72, Lebensrenditen.anteilAlles(d), 0.005)
        assertEquals(0.158, Lebensrenditen.anteilHaelfte(d), 0.0005)
        assertEquals(100.0, Lebensrenditen.anteile(d).sum(), 1e-9)
        // Nur Auszaehlung: kein Firmenname, kleine Datei
        val text = asset(Lebensrenditen.DATEI).readText(Charsets.UTF_8)
        assertTrue(text.length < 1_500)
        assertFalse(text.contains("INC") || text.contains("CORP"))
    }

    @Test
    fun pflegeUndNennung() {
        val n = Lebensrenditen.DATEI
        assertTrue(n in Alter.dateien())
        assertEquals(Alter.STANDARD, Alter.profilFuer(n))
        assertEquals(1, Datenbuendel.SCHEMA[n])
        val text = asset(n).readText(Charsets.UTF_8)
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, text))
        val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
        assertEquals(text, im.readText(Charsets.UTF_8))
        val plan = listOf(File("../tools/daten/dateien.json"), File("tools/daten/dateien.json")).first { it.exists() }.readText(Charsets.UTF_8)
        assertTrue(plan.contains("\"name\": \"$n\""))
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("indikatoren/lebensrenditen.json: counts made by this project"))
        assertTrue(notice.contains("Hendrik Bessembinder, Arizona State University"))
        assertTrue(notice.contains("No row of the table is included"))
        assertTrue(Lebensrenditen.QUELLE_ADRESSE.startsWith("https://wpcarey.asu.edu/"))
    }

    @Test
    fun texte() {
        for (k in listOf("leben.titel", "leben.satz", "leben.grafik", "leben.median", "leben.schatz", "leben.alles", "leben.haelfte")) {
            for (d in listOf(true, false)) assertNotNull(k, MarktTexte.text(d, k, listOf("1", "2", "3")).takeIf { it.isNotBlank() && !it.contains('{') })
        }
        assertEquals("Unternehmen, die den halben Zuwachs tragen (46)", MarktTexte.text(true, "leben.haelfte", listOf("46")))
    }
}
