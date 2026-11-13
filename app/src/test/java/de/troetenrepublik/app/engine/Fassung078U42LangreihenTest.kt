package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Langreihen.Art
import de.troetenrepublik.app.i18n.ForschungTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Lange Reihen im Laenderblatt: Leser, Teuerung, Jahrhunderte, Zinsmittel, mitgelieferte Datei, Pflege und Nennung.
class Fassung078U42LangreihenTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val klein = """{"stand":"2026-11-12","schema":1,"l":{"XXX":{"quelle":"Notenbank","adresse":"https://example.org/","zitat":["A (2000)"],
        "preise":{"a":1898,"w":[100,null,121,133.1,66.55]},"zins":{"a":1900,"w":[4,6,null,2]}}}}"""

    private val datei by lazy { Langreihen.lesen(asset(Langreihen.DATEI).readText(Charsets.UTF_8))!! }

    @Test
    fun leser() {
        val l = Langreihen.lesen(klein)!!.laender.getValue("XXX")
        assertEquals("Notenbank", l.quelle)
        assertEquals(listOf("A (2000)"), l.zitat)
        assertEquals(setOf(Art.PREISE, Art.ZINS), l.reihen.keys)
        val p = l.reihen.getValue(Art.PREISE)
        assertEquals(1898, p.von)
        assertEquals(1902, p.bis)
        assertEquals(121.0, p.wert(1900)!!, 0.0)
        assertNull(p.wert(1899))
        assertNull(p.wert(1800))
        assertEquals(listOf(1898 to 100.0, 1900 to 121.0, 1901 to 133.1, 1902 to 66.55), Langreihen.punkte(p))
        assertNull(Langreihen.lesen("{}"))
        assertNull(Langreihen.lesen("kein json"))
        assertNull(Langreihen.lesen("""{"stand":"2026-11-12","l":{}}"""))
        assertNull(Langreihen.lesen("""{"stand":"2026-11-12","l":{"XXX":{"quelle":"Q","adresse":"https://example.org/"}}}"""))
        assertNull(Langreihen.lesen("""{"stand":"2026-11-12","l":{"XXX":{"quelle":"Q","adresse":"ohne","zins":{"a":1900,"w":[1]}}}}"""))
        assertNull(Langreihen.lesen("""{"stand":"2026-11-12","l":{"XXX":{"quelle":"Q","adresse":"https://example.org/","preise":{"a":1900,"w":[1,0]}}}}"""))
        assertNull(Langreihen.lesen("""{"stand":"2026-11-12","l":{"XXX":{"quelle":"Q","adresse":"https://example.org/","zins":{"a":1900,"w":["x"]}}}}"""))
    }

    @Test
    fun teuerung() {
        val p = Langreihen.lesen(klein)!!.laender.getValue("XXX").reihen.getValue(Art.PREISE)
        // 100 auf 121 in zwei Jahren: 10 Prozent pro Jahr
        val t = Langreihen.teuerung(p, bis = 1900)!!
        assertEquals(10.0, t.proJahr, 1e-9)
        assertEquals(1898, t.erstes)
        assertEquals(1900, t.letztes)
        assertEquals(1.21, t.faktor, 1e-12)
        // Ein Rand ohne Wert rueckt auf das naechste Jahr mit Wert
        assertEquals(1900, Langreihen.teuerung(p, ab = 1899)!!.erstes)
        assertEquals(-50.0, Langreihen.teuerung(p, ab = 1901)!!.proJahr, 1e-9)
        assertNull(Langreihen.teuerung(p, ab = 1902))
        assertNull(Langreihen.teuerung(p, ab = 1950))
        // Staerkster Anstieg nur zwischen aufeinanderfolgenden Jahren mit Wert
        val s = Langreihen.staerkstesJahr(p)!!
        assertEquals(1901, s.first)
        assertEquals(10.0, s.second, 1e-9)
        assertEquals(listOf(1898 to 1.0, 1900 to 1.21, 1901 to 1.331, 1902 to 0.6655), Langreihen.preisVerlauf(p).map { it.first to Math.round(it.second * 1e6) / 1e6 })
    }

    @Test
    fun jahrhunderte() {
        // 250 Jahre mit 1 Prozent im Jahr ab 1795: 1795 bis 1799 ist zu kurz, danach drei Abschnitte
        var v = 100.0
        val r = Langreihen.Reihe(1795, List(250) { val x = v; v *= 1.01; x })
        val j = Langreihen.jahrhunderte(r)
        assertEquals(listOf(1799 to 1899, 1899 to 1999, 1999 to 2044), j.map { it.erstes to it.letztes })
        j.forEach { assertEquals(1.0, it.proJahr, 1e-9) }
        assertTrue(Langreihen.jahrhunderte(Langreihen.Reihe(1995, listOf(1.0, 2.0, 3.0))).isEmpty())
    }

    @Test
    fun zinsMittel() {
        val z = Langreihen.lesen(klein)!!.laender.getValue("XXX").reihen.getValue(Art.ZINS)
        val m = Langreihen.mittel(z)!!
        assertEquals(4.0, m.wert, 1e-12)
        assertEquals(3, m.jahre)
        assertEquals(1900, m.erstes)
        assertEquals(1903, m.letztes)
        assertEquals(1901 to 6.0, m.hoch)
        assertEquals(1903 to 2.0, m.tief)
        assertNull(Langreihen.mittel(Langreihen.Reihe(1900, listOf(null, null))))
    }

    @Test
    fun schweden() {
        val l = datei.laender.getValue("SWE")
        val p = l.reihen.getValue(Art.PREISE)
        assertEquals(1290, p.von)
        assertEquals(2014, p.bis)
        assertTrue(p.werte.all { it != null && it > 0 })
        // Index der Quelle (1914 = 100)
        assertEquals(100.1, p.wert(1914)!!, 1e-9)
        val t = Langreihen.teuerung(p)!!
        assertEquals(2.50, t.proJahr, 0.005)
        assertEquals(1290, t.erstes)
        val j = Langreihen.jahrhunderte(p)
        assertEquals(8, j.size)
        assertEquals(1299 to 1399, j.first().erstes to j.first().letztes)
        assertEquals(6.65, j[2].proJahr, 0.01)
        assertEquals(3.92, j[6].proJahr, 0.01)
        assertEquals(1999 to 2014, j.last().erstes to j.last().letztes)
        val z = Langreihen.mittel(l.reihen.getValue(Art.ZINS))!!
        assertEquals(1856, z.erstes)
        assertEquals(2012, z.letztes)
        assertEquals(5.17, z.wert, 0.005)
        assertEquals(14.15, z.hoch.second, 1e-9)
        val a = Langreihen.mittel(l.reihen.getValue(Art.ANLEIHE))!!
        assertEquals(1874, a.erstes)
        assertEquals(5.32, a.wert, 0.005)
        assertTrue(l.quelle.startsWith("Sveriges Riksbank"))
        assertEquals(2, l.zitat.size)
        assertTrue(l.zitat[0].startsWith("Rodney Edvinsson and Johan S"))
        assertTrue(l.zitat[1].contains("(2014)"))
    }

    @Test
    fun norwegen() {
        val l = datei.laender.getValue("NOR")
        val p = l.reihen.getValue(Art.PREISE)
        assertEquals(1516, p.von)
        assertEquals(2017, p.bis)
        assertTrue(p.werte.all { it != null && it > 0 })
        // Index der Quelle (2015 = 100)
        assertEquals(100.0, p.wert(2015)!!, 1e-9)
        assertEquals(1.91, Langreihen.teuerung(p)!!.proJahr, 0.005)
        val z = Langreihen.mittel(l.reihen.getValue(Art.ZINS))!!
        assertEquals(1819, z.erstes)
        assertEquals(2016, z.letztes)
        assertEquals(197, z.jahre)
        assertEquals(5.45, z.wert, 0.005)
        assertEquals(14.54, z.hoch.second, 1e-9)
        val a = Langreihen.mittel(l.reihen.getValue(Art.ANLEIHE))!!
        assertEquals(1822, a.erstes)
        assertEquals(5.01, a.wert, 0.005)
        assertTrue(l.quelle.startsWith("Norges Bank"))
        assertTrue(l.zitat[0].startsWith("Grytten, Ola H."))
        assertEquals(setOf("NOR", "SWE"), datei.laender.keys)
    }

    @Test
    fun pflegeUndNennung() {
        val n = Langreihen.DATEI
        assertTrue(n in Alter.dateien())
        assertEquals(Alter.REKORD, Alter.profilFuer(n))
        assertEquals(1, Datenbuendel.SCHEMA[n])
        assertTrue(asset(n).length() < 40_000)
        val text = asset(n).readText(Charsets.UTF_8)
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, text))
        val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
        assertEquals(text, im.readText(Charsets.UTF_8))
        val plan = listOf(File("../tools/daten/dateien.json"), File("tools/daten/dateien.json")).first { it.exists() }.readText(Charsets.UTF_8)
        assertTrue(plan.contains("\"name\": \"$n\""))
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("globus/langreihen.json: long annual series per country"))
        // Jedes Land der Datei steht mit seiner Quelle in der Liste
        for (l in datei.laender.values) assertTrue(l.a3, notice.contains(l.quelle))
        for (k in listOf("lr.titel", "lr.info", "lr.quelle", "lr.gerundet", "lr.staerkstes")) {
            for (d in listOf(true, false)) assertNotNull(k, ForschungTexte.text(d, k, listOf("1290")).takeIf { it.isNotBlank() })
        }
    }
}
