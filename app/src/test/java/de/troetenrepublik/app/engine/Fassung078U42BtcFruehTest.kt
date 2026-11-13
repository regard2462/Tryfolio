package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.ForschungTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Fruehe Bitcoin-Kurse: Leser, Voranstellen vor den gewoehnlichen Kursweg, mitgelieferte Datei, Pflege und Nennung.
class Fassung078U42BtcFruehTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val tagMs = 86_400_000L
    private fun tag(iso: String) = LocalDate.parse(iso).toEpochDay()
    private fun kerze(iso: String, kurs: Double) = Candle(tag(iso) * tagMs, kurs, kurs, kurs, kurs)

    private val klein = """{"stand":"2026-11-12","schema":1,"lizenz":"L","von":"2014-09-14","w":[4770000,null,4640000,4560000,4270000]}"""

    @Test
    fun leser() {
        val d = BtcFrueh.lesen(klein)!!
        assertEquals(tag("2014-09-14"), d.von)
        assertEquals(tag("2014-09-18"), d.bis)
        assertEquals(listOf(477.0, null, 464.0, 456.0, 427.0), d.kurse)
        assertEquals("L", d.lizenz)
        assertNull(BtcFrueh.lesen("{}"))
        assertNull(BtcFrueh.lesen("kein json"))
        assertNull(BtcFrueh.lesen("""{"stand":"2026-11-12","von":"2014-09-14","w":[]}"""))
        assertNull(BtcFrueh.lesen("""{"stand":"2026-11-12","von":"2014-09-14","w":[null,5]}"""))
        assertNull(BtcFrueh.lesen("""{"stand":"gestern","von":"2014-09-14","w":[5]}"""))
        assertNull(BtcFrueh.lesen("""{"stand":"2026-11-12","von":"2014-09-14","w":["x"]}"""))
    }

    @Test
    fun voranstellenNurVorDemErstenEigenenKurs() {
        val d = BtcFrueh.lesen(klein)!!
        val eigene = listOf(kerze("2014-09-17", 457.3), kerze("2014-09-18", 424.4))
        val r = BtcFrueh.verlaengern("BTC-USD", eigene, d)
        // 14. und 16. September kommen dazu, der 15. ist eine Luecke, ab dem 17. gilt der eigene Kurs
        assertEquals(2, r.ergaenzt)
        assertEquals(tag("2014-09-17"), r.uebernahme)
        assertEquals(listOf(477.0, 464.0, 457.3, 424.4), r.kerzen.map { it.close })
        assertEquals(listOf("2014-09-14", "2014-09-16", "2014-09-17", "2014-09-18").map { tag(it) * tagMs }, r.kerzen.map { it.t })
        assertTrue(r.kerzen.zipWithNext().all { (a, b) -> a.t < b.t })
        // Umrechnung mit festem Faktor
        val euro = BtcFrueh.verlaengern("BTC-USD", eigene, d, 0.5)
        assertEquals(238.5, euro.kerzen.first().close, 1e-9)
        assertEquals(238.5, euro.kerzen.first().open, 1e-9)
        assertEquals(457.3, euro.kerzen[2].close, 0.0)
    }

    @Test
    fun unveraendertOhneAnlass() {
        val d = BtcFrueh.lesen(klein)!!
        val eigene = listOf(kerze("2014-09-17", 457.3))
        for (r in listOf(
            BtcFrueh.verlaengern("ETH-USD", eigene, d), BtcFrueh.verlaengern("BTC-EUR", eigene, d),
            BtcFrueh.verlaengern("BTC-USD", eigene, null), BtcFrueh.verlaengern("BTC-USD", emptyList(), d),
            BtcFrueh.verlaengern("BTC-USD", eigene, d, 0.0), BtcFrueh.verlaengern("BTC-USD", eigene, d, Double.NaN),
            // Die eigene Reihe beginnt schon am ersten Tag der Datei oder davor
            BtcFrueh.verlaengern("BTC-USD", listOf(kerze("2014-09-14", 1.0)), d),
            BtcFrueh.verlaengern("BTC-USD", listOf(kerze("2010-01-01", 1.0)), d),
        )) {
            assertNull(r.uebernahme)
            assertEquals(0, r.ergaenzt)
        }
        assertEquals(eigene, BtcFrueh.verlaengern("ETH-USD", eigene, d).kerzen)
    }

    @Test
    fun faktorAusGemeinsamemTag() {
        val dollar = listOf(kerze("2014-09-17", 400.0), kerze("2014-09-18", 500.0))
        val euro = listOf(kerze("2014-09-17", 360.0), kerze("2014-09-18", 450.0))
        assertEquals(0.9, BtcFrueh.faktor(dollar, euro)!!, 1e-12)
        assertNull(BtcFrueh.faktor(dollar, emptyList()))
        assertNull(BtcFrueh.faktor(listOf(kerze("2014-09-18", 500.0)), euro))
    }

    @Test
    fun mitgelieferteDatei() {
        val d = BtcFrueh.lesen(asset(BtcFrueh.DATEI).readText(Charsets.UTF_8))!!
        assertEquals(tag("2010-07-18"), d.von)
        assertEquals(tag("2014-12-31"), d.bis)
        assertEquals(1628, d.kurse.size)
        assertTrue(d.kurse.all { it != null && it > 0 })
        // Werte der Quelle (PriceUSD), auf 0,0001 gerundet
        assertEquals(0.0858, d.kurse[0]!!, 1e-9)
        assertEquals(29.0299, d.kurse[(tag("2011-06-08") - d.von).toInt()]!!, 1e-9)
        assertEquals(1128.7255, d.kurse[(tag("2013-11-29") - d.von).toInt()]!!, 1e-9)
        assertEquals(464.1778, d.kurse[(tag("2014-09-16") - d.von).toInt()]!!, 1e-9)
        assertTrue(d.lizenz.contains("CC BY-NC 4.0"))
        // Vor einer Reihe ab dem 17.09.2014 stehen alle Tage seit dem 18.07.2010
        val r = BtcFrueh.verlaengern(BtcFrueh.SYMBOL, listOf(kerze("2014-09-17", 457.33)), d)
        assertEquals((tag("2014-09-17") - tag("2010-07-18")).toInt(), r.ergaenzt)
        assertEquals(tag("2014-09-17"), r.uebernahme)
        assertEquals(0.0858, r.kerzen.first().close, 1e-9)
    }

    @Test
    fun pflegeUndNennung() {
        val n = BtcFrueh.DATEI
        assertTrue(n in Alter.dateien())
        assertEquals(Alter.REKORD, Alter.profilFuer(n))
        assertEquals(1, Datenbuendel.SCHEMA[n])
        assertTrue(asset(n).length() < 20_000)
        val text = asset(n).readText(Charsets.UTF_8)
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, text))
        val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
        assertEquals(text, im.readText(Charsets.UTF_8))
        val plan = listOf(File("../tools/daten/dateien.json"), File("tools/daten/dateien.json")).first { it.exists() }.readText(Charsets.UTF_8)
        assertTrue(plan.contains("\"name\": \"$n\""))
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("indikatoren/btc_frueh.json: daily closing prices of Bitcoin"))
        assertTrue(notice.contains("Coin Metrics Community Data"))
        assertTrue(notice.contains("licensed under CC BY-NC 4.0 (https://creativecommons.org/licenses/by-nc/4.0/)"))
        assertTrue(BtcFrueh.QUELLE_NAME.contains("CC BY-NC 4.0"))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(ForschungTexte.schluessel(true), ForschungTexte.schluessel(false))
        for (k in ForschungTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = ForschungTexte.text(d, k, listOf("1", "2", "3", "4", "5", "6"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{'))
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('…') || t.contains('?'))
        }
        assertNotNull(ForschungTexte.text(true, "btc.hinweis", listOf("17.09.2014")).takeIf { it.contains("17.09.2014") })
    }
}
