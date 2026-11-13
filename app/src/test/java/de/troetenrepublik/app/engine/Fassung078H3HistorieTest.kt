package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.HistorieJst.Art
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

// Paket H3: Laenderrenditen und Krisen seit 1870 (Jorda-Schularick-Taylor) und Hauspreise real seit 1975 (Dallas Fed).
// Rechenlogik mit festen Beispielen, die Bundeldateien gegen unabhaengig aus der Quelltabelle gerechnete Werte.
class Fassung078H3HistorieTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val jst by lazy { HistorieJst.lesen(asset("globus/jst.json").readText(Charsets.UTF_8))!! }
    private val haus by lazy { Hauspreise.lesen(asset("globus/hauspreise.json").readText(Charsets.UTF_8))!! }

    private fun land(akt: List<Double?>, von: Int = 1900, krisen: List<Int> = emptyList()) =
        HistorieJst.Land("XXX", von, mapOf(Art.AKTIEN to akt), emptyList(), krisen)

    // ------------------------------------------------------------ Rechnen mit festen Beispielen

    @Test fun spanneIstGeometrischUndUeberspringtLuecken() {
        val l = land(listOf(0.10, -0.10, null, 0.05))
        val s = HistorieJst.spanne(l, Art.AKTIEN)!!
        assertEquals((1.10 * 0.90 * 1.05).pow(1.0 / 3) - 1.0, s.rendite, 1e-12)
        assertEquals(3, s.jahre)
        assertEquals(1900, s.erstes)
        assertEquals(1903, s.letztes)
        // ab 1901: nur die letzten beiden Werte mit Daten
        val ab = HistorieJst.spanne(l, Art.AKTIEN, 1901)!!
        assertEquals(2, ab.jahre)
        assertEquals(1901, ab.erstes)
        assertNull(HistorieJst.spanne(l, Art.ANLEIHEN))
        assertNull(HistorieJst.spanne(land(listOf(null, null)), Art.AKTIEN))
    }

    @Test fun vermoegenStartetBeiEinsImVorjahrDesErstenWerts() {
        val v = HistorieJst.vermoegen(land(listOf(null, 0.10, null, -0.50)), Art.AKTIEN)
        assertEquals(listOf(1900, 1901, 1903), v.map { it.first })
        assertEquals(1.0, v[0].second, 1e-12)
        assertEquals(1.10, v[1].second, 1e-12)
        assertEquals(0.55, v[2].second, 1e-12)
        assertTrue(HistorieJst.vermoegen(land(listOf(null, null)), Art.AKTIEN).isEmpty())
    }

    @Test fun schlechtesteZehnjahreOhneGemeinsameJahre() {
        // 25 Jahre: ab Jahr 3 bis Jahr 14 jedes Jahr -5 %, sonst +3 %; ein Loch im Jahr 20
        val r = List(25) { i -> if (i in 3..14) -0.05 else 0.03 }.toMutableList<Double?>()
        r[20] = null
        val w = HistorieJst.schlechteste(land(r), Art.AKTIEN, 3)
        assertTrue(w.isNotEmpty())
        // schlechtester Zeitraum liegt ganz im Block der Verluste, Rendite genau -5 %
        assertEquals(-0.05, w[0].rendite, 1e-12)
        assertTrue(w[0].von in 1903..1905)
        // keine zwei Zeitraeume teilen ein Jahr, jeder hat zehn Jahre
        for (a in w) assertEquals(9, a.bis - a.von)
        for (i in w.indices) for (k in i + 1 until w.size) assertTrue(w[i].von > w[k].bis || w[k].von > w[i].bis)
        // aufsteigend sortiert
        assertEquals(w.sortedBy { it.rendite }.map { it.von }, w.map { it.von })
        // Zeitraeume mit Luecke zaehlen nicht
        for (a in w) assertTrue(a.bis < 1920 || a.von > 1920)
        assertTrue(HistorieJst.schlechteste(land(List(9) { 0.01 }), Art.AKTIEN).isEmpty())
    }

    @Test fun ranglisteSortiertUndLaesstKurzeReihenWeg() {
        val lang = List(40) { 0.05 }
        val kurz = List(10) { 0.20 }
        val d = HistorieJst.Datei(
            "2026-11-12", 2020,
            mapOf(
                "AAA" to HistorieJst.Land("AAA", 1900, mapOf(Art.AKTIEN to lang), emptyList(), emptyList()),
                "BBB" to HistorieJst.Land("BBB", 1900, mapOf(Art.AKTIEN to List(40) { 0.07 }), emptyList(), emptyList()),
                "CCC" to HistorieJst.Land("CCC", 1900, mapOf(Art.AKTIEN to kurz), emptyList(), emptyList()),
                "DDD" to HistorieJst.Land("DDD", 1900, mapOf(Art.ZINS to lang), emptyList(), emptyList()),
            ),
            emptyList(), "",
        )
        val r = HistorieJst.rangliste(d)
        assertEquals(listOf("BBB", "AAA"), r.map { it.code })
        assertEquals(7.0, r[0].wert, 1e-9)
        // seit 1920: nur 20 Jahre, unter der Mindestzahl
        assertTrue(HistorieJst.rangliste(d, 1920).isEmpty())
    }

    @Test fun hauspreiseKernzahlen() {
        val l = Hauspreise.Land("XXX", listOf(100.0, 150.0, 200.0), listOf(100.0, 100.0, 200.0))
        val d = Hauspreise.Datei("2026-11-12", "1975Q4", "1976Q2", mapOf("XXX" to l), emptyList(), "")
        val k = Hauspreise.kern(d, l)
        assertEquals(100.0, k.aenderung, 1e-9)
        // Verhaeltnis 1,0 / 1,5 / 1,0, Schnitt 3,5 / 3, letzter Stand 1,0 gegen 7/6
        assertEquals((1.0 / (3.5 / 3.0) - 1.0) * 100.0, k.abweichung, 1e-9)
        assertEquals(1975 to 4, k.ab)
        assertEquals(1976 to 2, k.bis)
        assertEquals(listOf(100.0, 150.0, 200.0), Hauspreise.auf100(l.preis))
        assertEquals(3, Hauspreise.anzahl("1975Q4", "1976Q2"))
        assertEquals(1976 to 1, Hauspreise.quartalAn(d, 1))
        assertNull(Hauspreise.quartal("1975-Q1"))
        assertNull(Hauspreise.quartal("1975Q5"))
    }

    @Test fun hauspreiseLesenVerlangtPassendeLaengen() {
        val gut = """{"stand":"2026-11-12","ab":"1975Q1","bis":"1975Q2","l":{"AAA":{"p":[10000,11000],"e":[10000,10000]}}}"""
        assertNotNull(Hauspreise.lesen(gut))
        assertEquals(110.0, Hauspreise.lesen(gut)!!.laender.getValue("AAA").preis[1], 1e-9)
        val kurz = """{"stand":"2026-11-12","ab":"1975Q1","bis":"1975Q3","l":{"AAA":{"p":[10000,11000],"e":[10000,10000]}}}"""
        assertNull(Hauspreise.lesen(kurz))
        assertNull(Hauspreise.lesen("{}"))
        assertNull(Hauspreise.lesen("kein json"))
    }

    // ------------------------------------------------------------ Bundeldateien gegen die Quelltabelle

    @Test fun jstHatAchtzehnLaenderMitKrisenUndKredit() {
        assertEquals(18, jst.laender.size)
        val erwartet = setOf("AUS", "BEL", "CAN", "CHE", "DEU", "DNK", "ESP", "FIN", "FRA", "GBR", "IRL", "ITA", "JPN", "NLD", "NOR", "PRT", "SWE", "USA")
        assertEquals(erwartet, jst.laender.keys)
        for (l in jst.laender.values) {
            assertTrue(l.a3, l.krisen.isNotEmpty())
            assertTrue(l.a3, HistorieJst.letzterKredit(l) != null)
            assertEquals(l.a3, 2020 - 1871 + 1, l.reihen.getValue(Art.ZINS).size)
            assertEquals(l.a3, 2020 - 1871 + 1, l.kredit.size)
        }
        // Die Quelle hat fuer Kanada und Irland keine Renditespalten
        for (a3 in listOf("CAN", "IRL")) {
            assertNull(HistorieJst.aktienRendite(jst.laender.getValue(a3)))
            assertNull(HistorieJst.spanne(jst.laender.getValue(a3), Art.IMMOBILIEN))
        }
        for (a3 in erwartet - setOf("CAN", "IRL")) {
            val l = jst.laender.getValue(a3)
            for (a in listOf(Art.AKTIEN, Art.ANLEIHEN, Art.IMMOBILIEN)) assertNotNull("$a3 $a", HistorieJst.spanne(l, a))
        }
        assertEquals(listOf(1873, 1893, 1907, 1930, 1984, 2007), jst.laender.getValue("USA").krisen)
    }

    @Test fun usaAktienGegenUnabhaengigeRechnung() {
        // Werte aus der Quelltabelle (JSTdatasetR6.xlsx) mit Python gerechnet: reale Rendite aus eq_tr und cpi
        val usa = jst.laender.getValue("USA")
        assertEquals(6.8222, HistorieJst.aktienRendite(usa)!!, 0.01)
        assertEquals(7.568, HistorieJst.aktienRendite(usa, 1950)!!, 0.01)
        assertEquals(149, HistorieJst.spanne(usa, Art.AKTIEN)!!.jahre)
        assertEquals(1872, HistorieJst.spanne(usa, Art.AKTIEN)!!.erstes)
        assertEquals(-0.4102, usa.reihen.getValue(Art.AKTIEN)[2008 - 1871]!!, 0.0001)
        val w = HistorieJst.schlechteste(usa, Art.AKTIEN, 3)
        assertEquals(listOf(1911, 1999, 1965), w.map { it.von })
        assertEquals(-0.04118, w[0].rendite, 0.0002)
        assertEquals(-0.0402, w[1].rendite, 0.0002)
        assertEquals(-0.03464, w[2].rendite, 0.0002)
    }

    @Test fun deutschlandOhneHyperinflationsjahre() {
        val deu = jst.laender.getValue("DEU")
        assertEquals(1922 to 1924, deu.leer)
        val akt = deu.reihen.getValue(Art.AKTIEN)
        for (j in 1922..1924) assertNull(akt[j - 1871])
        assertNotNull(akt[1921 - 1871])
        assertNotNull(akt[1925 - 1871])
        assertEquals(147, HistorieJst.spanne(deu, Art.AKTIEN)!!.jahre)
        assertEquals(4.6092, HistorieJst.aktienRendite(deu)!!, 0.01)
        assertEquals(8.3924, HistorieJst.aktienRendite(deu, 1950)!!, 0.01)
        val w = HistorieJst.schlechteste(deu, Art.AKTIEN, 2)
        assertEquals(listOf(1939, 1911), w.map { it.von })
        // Kein Zehnjahreszeitraum enthaelt ein Jahr ohne Wert
        for (z in HistorieJst.schlechteste(deu, Art.AKTIEN, 50)) assertTrue(z.bis < 1922 || z.von > 1924)
    }

    @Test fun ranglisteDerAktienrenditenHatSechzehnLaender() {
        val seit1870 = HistorieJst.rangliste(jst)
        val seit1950 = HistorieJst.rangliste(jst, 1950)
        assertEquals(16, seit1870.size)
        assertEquals(16, seit1950.size)
        assertEquals(seit1870.map { it.wert }.sortedDescending(), seit1870.map { it.wert })
        assertEquals(seit1950.map { it.wert }.sortedDescending(), seit1950.map { it.wert })
        assertTrue(seit1870.none { it.code == "CAN" || it.code == "IRL" })
        // Japan seit 1950 mit 6,7859 % gegen die Quelltabelle
        assertEquals(6.7859, seit1950.first { it.code == "JPN" }.wert, 0.01)
        // Platz der USA seit 1950 ergibt sich aus der Liste
        val platz = seit1950.indexOfFirst { it.code == "USA" } + 1
        assertTrue(platz in 1..16)
        assertEquals(seit1950.count { it.wert > seit1950.first { r -> r.code == "USA" }.wert } + 1, platz)
    }

    @Test fun hauspreiseSechsundzwanzigLaenderMitVollenReihen() {
        assertEquals(26, haus.laender.size)
        assertEquals("1975Q1", haus.ab)
        assertEquals(206, Hauspreise.anzahl(haus.ab, haus.bis))
        for (l in haus.laender.values) {
            assertEquals(l.a3, 206, l.preis.size)
            assertEquals(l.a3, 206, l.einkommen.size)
        }
        assertEquals(2026 to 2, Hauspreise.quartalAn(haus, 205))
        // US und Deutschland gegen die Quelltabelle (RHPI und RPDI, Python)
        val us = Hauspreise.kern(haus, haus.laender.getValue("USA"))
        assertEquals(139.553, us.aenderung, 0.05)
        assertEquals(11.623, us.abweichung, 0.05)
        val de = Hauspreise.kern(haus, haus.laender.getValue("DEU"))
        assertEquals(7.617, de.aenderung, 0.05)
        assertEquals(-23.853, de.abweichung, 0.05)
    }

    @Test fun lizenzUndZitatStehenInDenDateien() {
        val roh = asset("globus/jst.json").readText(Charsets.UTF_8)
        assertTrue(roh.contains("CC BY-NC-SA 4.0"))
        assertTrue(roh.contains("Weitergabe nur unter gleichen Bedingungen"))
        assertEquals(2, jst.zitat.size)
        assertTrue(jst.zitat[0].startsWith("Òscar Jordà, Moritz Schularick, and Alan M. Taylor. 2017."))
        assertTrue(jst.zitat[1].contains("The Rate of Return on Everything, 1870–2015."))
        assertTrue(jst.lizenz.contains("CC BY-NC-SA 4.0"))
        assertEquals(1, haus.zitat.size)
        assertTrue(haus.zitat[0].startsWith("Mack, A., and E. Martínez-García. 2011."))
        assertTrue(haus.hinweis.startsWith("The authors acknowledge use of the dataset described in Mack and Martínez-García (2011)."))
        // Nennung und Lizenz in der Quellenliste des Projekts
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)
        assertTrue(notice.contains("globus/jst.json") && notice.contains("CC BY-NC-SA 4.0") && notice.contains("same licence terms"))
        assertTrue(notice.contains("globus/hauspreise.json") && notice.contains("private gain"))
    }

    @Test fun dateienSindGepflegtUndAlsGueltigErkannt() {
        for (n in listOf("globus/jst.json", "globus/hauspreise.json")) {
            assertTrue(n in Alter.dateien())
            assertEquals(1, Datenbuendel.SCHEMA[n])
            assertTrue(asset(n).length() < 300_000)
            val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
            assertEquals(asset(n).readText(Charsets.UTF_8), im.readText(Charsets.UTF_8))
            assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, asset(n).readText(Charsets.UTF_8)))
        }
        assertEquals(Alter.QUARTAL, Alter.profilFuer("globus/hauspreise.json"))
    }
}
