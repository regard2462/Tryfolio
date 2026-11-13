package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Gesamt13f
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 0.77: 13F-Gesamtbild. Lesen der Bundledatei, Netto-Kaeufer, Ranglisten ohne Sonderfaelle und kleine Werte,
// Suche nach Kuerzel mit Aktienklasse, dazu die mitgelieferte Datei selbst.
class Fassung077Gesamt13fTest {

    private fun w(h: Int, neu: Int, weg: Int, auf: Int, ab: Int, f: Long, x: Int = 0, sv: Long = 1000, sg: Long = 1100) =
        """{"h":$h,"hv":$h,"neu":$neu,"weg":$weg,"auf":$auf,"ab":$ab,"w":5000000,"s":2000,"sg":$sg,"sv":$sv,"f":$f,"n":"Wert","t":[["Beispiel Fonds",900,4000,2.5],["Neuer Fonds",100,500,null]]""" +
            (if (x == 1) ""","x":1""" else "") + "}"

    private val text = """
        {"stichtag":"2026-06-30","vorher":"2026-03-31","bis":"2026-08-31","melder":8827,"melderVorher":8839,"gruppe":8696,
         "aktien":{
          "AAA":${w(500, 10, 5, 100, 50, 300)},
          "BBB":${w(400, 2, 30, 20, 120, -700)},
          "CCC":${w(300, 200, 0, 0, 0, 900, x = 1)},
          "DDD":${w(20, 15, 0, 3, 0, 100)},
          "BRK-B":${w(100, 1, 1, 10, 5, 50)}
         }}
    """.trimIndent()

    @Test fun lesenUndNetto() {
        val d = Gesamt13f.lesen(text)!!
        assertEquals("2026-06-30", d.stichtag)
        assertEquals(8827, d.melder)
        assertEquals(5, d.werte.size)
        val a = d.werte.getValue("AAA")
        assertEquals(500, a.halter)
        // 100 Aufstocker + 10 neue - 50 Abbauer - 5 Ausgestiegene
        assertEquals(55, a.netto)
        assertEquals(10.0, a.aenderung!!, 1e-9)
        assertEquals(2, a.top.size)
        assertEquals(2.5, a.top[0].aenderung!!, 1e-9)
        assertNull(a.top[1].aenderung)
        assertEquals(-128, d.werte.getValue("BBB").netto)
    }

    @Test fun ranglistenOhneSonderfaelleUndKleineWerte() {
        val d = Gesamt13f.lesen(text)!!
        // CCC ist Sonderfall, DDD hat zu wenige Halter, BBB verkauft
        assertEquals(listOf("AAA", "BRK-B").sortedBy { -d.werte.getValue(it).netto }, Gesamt13f.meistgekauft(d).map { it.kuerzel })
        assertEquals(listOf("BBB"), Gesamt13f.meistverkauft(d).map { it.kuerzel })
        assertEquals(1, Gesamt13f.meistgekauft(d, 1).size)
    }

    @Test fun kuerzelMitAktienklasse() {
        val d = Gesamt13f.lesen(text)
        assertNotNull(Gesamt13f.fuer(d, "BRK.B"))
        assertNotNull(Gesamt13f.fuer(d, "BRK-B"))
        assertNull(Gesamt13f.fuer(d, "ZZZ"))
        assertNull(Gesamt13f.fuer(null, "AAA"))
    }

    @Test fun kaputtesJsonGibtNull() {
        assertNull(Gesamt13f.lesen("kein json"))
        assertNull(Gesamt13f.lesen("""{"aktien":{}}"""))
        assertNull(Gesamt13f.lesen("""{"stichtag":"x"}"""))
    }

    @Test fun mitgelieferteDateiIstLesbarUndKlein() {
        val f = listOf("src/main/assets/copytrading/gesamt13f.json", "../app/src/main/assets/copytrading/gesamt13f.json").map { File(it) }.first { it.exists() }
        assertTrue("unter 1 MB", f.length() < 1_000_000)
        val d = Gesamt13f.lesen(f.readText(Charsets.UTF_8))!!
        assertTrue(d.werte.size > 500)
        assertTrue(d.melder > 5000)
        val apple = Gesamt13f.fuer(d, "AAPL")!!
        assertTrue(apple.halter > 3000)
        assertEquals(5, apple.top.size)
        assertTrue(Gesamt13f.meistgekauft(d).size >= 5)
        assertTrue(Gesamt13f.meistverkauft(d).size >= 5)
        // Rang nach Zahl der Fonds, aber nur mit Geldfluss in dieselbe Richtung
        assertTrue(Gesamt13f.meistgekauft(d).all { it.netto > 0 && it.fluss > 0 })
        assertTrue(Gesamt13f.meistverkauft(d).all { it.netto < 0 && it.fluss < 0 })
        // Archivkopie ist dieselbe Datei
        val im = listOf(File("../daten/copytrading/gesamt13f.json"), File("daten/copytrading/gesamt13f.json")).first { it.exists() }
        assertTrue(f.readBytes().contentEquals(im.readBytes()))
    }

    @Test
    fun zuordnungCusip() {
        val d = Gesamt13f.lesen("""{"stichtag":"2026-06-30","aktien":{"AAPL":{"h":3,"n":"Apple"}},"cusip":{"037833100":"AAPL","X":5}}""")!!
        assertEquals(mapOf("037833100" to "AAPL"), d.cusip)
        assertEquals(emptyMap<String, String>(), Gesamt13f.lesen("""{"stichtag":"2026-06-30","aktien":{"AAPL":{"h":3}}}""")!!.cusip)
    }
}
