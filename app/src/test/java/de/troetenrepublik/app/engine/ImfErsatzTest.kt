package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// [Globus Gesamtbild 0.73] Rueckfallkette hinter den IWF-Reihen: Weltbank, dann Datei (UN, Factbook); Alter, Prognosejahre, Vorrang
class ImfErsatzTest {
    private val heute = java.time.LocalDate.now().year

    private fun iwf(vararg zeilen: Triple<String, String, Map<Int, Double>>): Imf.Datensatz {
        val w = HashMap<String, HashMap<String, Map<Int, Double>>>()
        for ((k, l, reihe) in zeilen) w.getOrPut(k) { HashMap() }[l] = reihe
        return Imf.Datensatz(0L, w, 3)
    }

    private fun wb(kennung: String, vararg l: Triple<String, Int, Double>) =
        Weltbank.Reihe(kennung, 0L, l.associate { it.first to Weltbank.Wert(it.second, it.third) })

    private val karte = setOf("AAA", "BBB", "CCC")

    @Test
    fun weltbankFuelltLuecken() {
        val d = iwf(Triple(Imf.WACHSTUM, "AAA", mapOf(heute to 2.0, heute + 1 to 3.0)))
        val r = ImfErsatz.anwenden(
            d, karte,
            mapOf(
                "NY.GDP.MKTP.KD.ZG" to wb("NY.GDP.MKTP.KD.ZG", Triple("AAA", heute - 1, 9.0), Triple("BBB", heute - 2, -1.5)),
                "NY.GDP.MKTP.CD" to wb("NY.GDP.MKTP.CD", Triple("BBB", heute - 2, 19.9e9)),
                "SP.POP.TOTL" to wb("SP.POP.TOTL", Triple("BBB", heute - 1, 24.2e6)),
            ),
            null, heute,
        )
        // der IWF-Wert von AAA bleibt, BBB bekommt Ersatz mit Jahr und Quelle, Einheiten wie beim IWF
        assertEquals(2.0, Imf.wert(r, Imf.WACHSTUM, "AAA", heute)!!, 1e-9)
        assertNull(Imf.herkunft(r, Imf.WACHSTUM, "AAA", heute))
        assertEquals(-1.5, Imf.wert(r, Imf.WACHSTUM, "BBB", heute)!!, 1e-9)
        assertEquals(Imf.ErsatzWert(heute - 2, -1.5, "WB"), Imf.herkunft(r, Imf.WACHSTUM, "BBB", heute))
        assertEquals(19.9, Imf.wert(r, Imf.BIP, "BBB", heute)!!, 1e-9)
        assertEquals(24.2, Imf.wert(r, Imf.BEVOELKERUNG, "BBB", heute)!!, 1e-9)
        assertNull(Imf.wert(r, Imf.WACHSTUM, "CCC", heute))
    }

    @Test
    fun ersatzGiltNurVomJahrDesWertsBisHeuteUndHoechstensFuenfJahre() {
        val r = ImfErsatz.anwenden(
            iwf(), karte, mapOf("FP.CPI.TOTL.ZG" to wb("FP.CPI.TOTL.ZG", Triple("AAA", heute - 3, 7.0))), null, heute,
        )
        assertNull(Imf.wert(r, Imf.INFLATION, "AAA", heute - 4))
        assertEquals(7.0, Imf.wert(r, Imf.INFLATION, "AAA", heute - 3)!!, 1e-9)
        assertEquals(7.0, Imf.wert(r, Imf.INFLATION, "AAA", heute)!!, 1e-9)
        // Prognosejahre bleiben leer, es wird nichts fortgeschrieben
        assertNull(Imf.wert(r, Imf.INFLATION, "AAA", heute + 1))
        // Ein Wert, der um mehr als fuenf Jahre hinter dem Jahr liegt, gilt nicht mehr
        val alt = r.copy(ersatz = mapOf(Imf.INFLATION to mapOf("AAA" to Imf.ErsatzWert(heute - 8, 5.0, "CIA"))))
        assertNull(Imf.wert(alt, Imf.INFLATION, "AAA", heute))
        assertEquals(5.0, Imf.wert(alt, Imf.INFLATION, "AAA", heute - 3)!!, 1e-9)
        // wertGenau schreibt nicht fort
        assertNull(Imf.wertGenau(r, Imf.INFLATION, "AAA", heute))
        assertEquals(7.0, Imf.wertGenau(r, Imf.INFLATION, "AAA", heute - 3)!!, 1e-9)
    }

    @Test
    fun zuAlteWerteUndFremdeLaenderFallenWeg() {
        val r = ImfErsatz.anwenden(
            iwf(), karte,
            mapOf("SL.UEM.TOTL.ZS" to wb("SL.UEM.TOTL.ZS", Triple("AAA", heute - 6, 5.0), Triple("WLD", heute - 1, 5.5), Triple("BBB", heute + 1, 4.0))),
            null, heute,
        )
        assertTrue(r.ersatz.isEmpty())
    }

    @Test
    fun iwfReiheMitAktuellenJahrenWirdNichtErsetztAlteSchon() {
        // Syrien-Fall: der IWF fuehrt die Reihe nur bis weit in die Vergangenheit
        val d = iwf(Triple(Imf.INFLATION, "AAA", mapOf(2008 to 4.0, 2010 to 4.4)), Triple(Imf.INFLATION, "BBB", mapOf(heute - 1 to 3.0)))
        val r = ImfErsatz.anwenden(
            d, karte, mapOf("FP.CPI.TOTL.ZG" to wb("FP.CPI.TOTL.ZG", Triple("AAA", heute - 2, 94.1), Triple("BBB", heute - 2, 50.0))), null, heute,
        )
        assertEquals("WB", Imf.herkunft(r, Imf.INFLATION, "AAA", heute - 1)!!.quelle)
        assertEquals(94.1, Imf.wert(r, Imf.INFLATION, "AAA", heute - 1)!!, 1e-9)
        assertNull(Imf.herkunft(r, Imf.INFLATION, "BBB", heute - 1))
        assertEquals(3.0, Imf.wert(r, Imf.INFLATION, "BBB", heute - 1)!!, 1e-9)
        // fruehere IWF-Jahre bleiben unberuehrt
        assertEquals(4.4, Imf.wert(r, Imf.INFLATION, "AAA", 2010)!!, 1e-9)
    }

    @Test
    fun dateiKommtNachDerWeltbankUndLiestSich() {
        val datei = ImfErsatz.dateiLesen(
            """{"stand":"2026-11-09","w":{"AAA":{"NGDP_RPCH":[${heute - 4},1.8,"UN"],"PCPIPCH":[${heute - 4},76.1,"CIA"]},"BBB":{"NGDP_RPCH":[${heute - 3},2.0,"CIA"]}}}""",
        )!!
        assertEquals("2026-11-09", datei.stand)
        val r = ImfErsatz.anwenden(
            iwf(), karte, mapOf("NY.GDP.MKTP.KD.ZG" to wb("NY.GDP.MKTP.KD.ZG", Triple("BBB", heute - 1, 4.5))), datei, heute,
        )
        // BBB: die Weltbank geht vor; AAA: UN und Factbook aus der Datei
        assertEquals(Imf.ErsatzWert(heute - 1, 4.5, "WB"), r.ersatz[Imf.WACHSTUM]!!["BBB"])
        assertEquals(Imf.ErsatzWert(heute - 4, 1.8, "UN"), r.ersatz[Imf.WACHSTUM]!!["AAA"])
        assertEquals("CIA", r.ersatz[Imf.INFLATION]!!["AAA"]!!.quelle)
        assertNull(ImfErsatz.dateiLesen("kein json"))
    }

    @Test
    fun gebuendelteDateiIstLesbarUndDatiert() {
        val f = listOf("src/main/assets/globus/ersatz.json", "../app/src/main/assets/globus/ersatz.json").map { File(it) }.first { it.exists() }
        val datei = ImfErsatz.dateiLesen(f.readText())!!
        assertTrue(datei.stand.isNotEmpty())
        assertNotNull(datei.werte["CUB"])
        for ((_, je) in datei.werte) for ((kennung, e) in je) {
            assertTrue(kennung, kennung in ImfErsatz.WELTBANK.keys)
            assertTrue(e.quelle == "UN" || e.quelle == "CIA")
            assertTrue(e.wert.isFinite())
        }
    }

    @Test
    fun gesamtbildNutztErsatzOhneFortschreibenDesWachstums() {
        val r = ImfErsatz.anwenden(
            iwf(), karte,
            mapOf(
                "NY.GDP.MKTP.KD.ZG" to wb("NY.GDP.MKTP.KD.ZG", Triple("AAA", heute - 1, 3.0)),
                "NY.GDP.PCAP.PP.CD" to wb("NY.GDP.PCAP.PP.CD", Triple("AAA", heute - 1, 20000.0)),
                "FP.CPI.TOTL.ZG" to wb("FP.CPI.TOTL.ZG", Triple("AAA", heute - 1, 2.0)),
                "GC.DOD.TOTL.GD.ZS" to wb("GC.DOD.TOTL.GD.ZS", Triple("AAA", heute - 1, 30.0)),
            ),
            null, heute,
        )
        val e = GlobusGesamt.rechnen(r, "AAA", heute, GlobusGesamt.Zusatz(sicherheit = GlobusGesamt.Sicherheit(0, 0, 0, 0)))!!
        // Wachstum: nur ein Jahr genau vorhanden, also kein Bereich Wachstum
        assertNull(e.bereich(GlobusGesamt.Bereich.WACHSTUM))
        val w = e.bereich(GlobusGesamt.Bereich.WOHLSTAND)!!.zutaten.single()
        assertEquals(heute - 1, w.jahr)
        assertEquals("WB", w.quelle)
        val s = e.bereich(GlobusGesamt.Bereich.STABILITAET)!!
        assertEquals(100.0, s.punkte, 1e-9)
        assertTrue(s.zutaten.all { it.quelle == "WB" && it.jahr == heute - 1 })
        // In einem Prognosejahr gibt es keinen Ersatzwert: Wohlstand und Stabilitaet fehlen, das Land hat keine Punkte
        assertNull(GlobusGesamt.rechnen(r, "AAA", heute + 1, GlobusGesamt.Zusatz(sicherheit = GlobusGesamt.Sicherheit(0, 0, 0, 0))))
    }

    @Test
    fun taiwanLiegtInDenGovernanceDatenUnterEinemFalschenKuerzel() {
        val json = """[{"page":1},[
            {"country":{"id":"SY","value":"Syrian Arab Republic"},"countryiso3code":"SYR","date":"2025","value":25.0},
            {"country":{"id":"","value":"Taiwan, China"},"countryiso3code":"SYR","date":"2025","value":83.0}]]"""
        val r = Weltbank.lesen("GOV_WGI_PV.SC", json, 0L)!!
        assertEquals(25.0, r.werte["SYR"]!!.wert, 1e-9)
        assertEquals(83.0, r.werte["TWN"]!!.wert, 1e-9)
    }
}
