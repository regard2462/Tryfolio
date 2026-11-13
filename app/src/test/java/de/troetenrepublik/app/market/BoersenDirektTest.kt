package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// [Globus Boersen 0.73] Direktparser gegen gekuerzte echte Antworten der Boersen vom 09.11.2026 (Testressourcen boerse/)
class BoersenDirektTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("boerse/$name")!!.readText()

    private fun pruefe(s: BoersenDirekt.Stand?, prozent: Double, wert: Double?, datum: LocalDate?, genau: Double = 0.01) {
        assertNotNull(s)
        assertEquals(prozent, s!!.prozent, genau)
        if (wert != null) assertEquals(wert, s.wert!!, 0.01)
        assertEquals(datum, s.datum)
    }

    private fun d(j: Int, m: Int, t: Int) = LocalDate.of(j, m, t)

    @Test(timeout = 20_000)
    fun moexTagesaenderung() {
        val text = """
            {"securities":{"columns":["SECID"],"data":[["IMOEX"]]},
            "marketdata":{"columns":["SECID","BOARDID","TRADEDATE","LASTVALUE","OPENVALUE","CURRENTVALUE","LASTCHANGE","LASTCHANGEPRC"],
            "data":[["IMOEX","SNDX","2026-11-09",2463.82,2480.33,2483.24,19.42,0.79]]}}
        """.trimIndent()
        pruefe(BoersenDirekt.moex(text), 0.79, 2463.82, d(2026, 11, 9))
    }

    // Echte Antwort vom 09.11.2026 (Abendsitzung, Wochenendhandel inbegriffen): LASTCHANGEPRC ist die Aenderung gegen den letzten Hauptschluss
    @Test(timeout = 20_000)
    fun moexEchteAntwort() = pruefe(BoersenDirekt.moex(res("moex.json")), 0.25, 2463.82, d(2026, 11, 9))

    @Test(timeout = 20_000)
    fun belgradUeberHttps() {
        pruefe(BoersenDirekt.belex15(res("bgdx.html")), -0.07, 1400.44, d(2026, 11, 9))
        assertEquals("https://bgdx.rs/", BoersenDirekt.QUELLEN.first { it.id == "bgdx" }.url)
        assertEquals("bgdx", BoersenDirekt.laender()["RS"]!!.id)
    }

    @Test(timeout = 20_000)
    fun zusatzQuellenHaengenHinten() {
        val eingebaut = BoersenDirekt.QUELLEN.size - BoersenZusatz.QUELLEN.size
        assertEquals(BoersenZusatz.QUELLEN, BoersenDirekt.QUELLEN.drop(eingebaut))
        // keine Quelle im Klartext
        assertTrue(BoersenDirekt.QUELLEN.all { it.url.startsWith("https://") })
    }

    @Test(timeout = 20_000)
    fun moexOhneSpalte() {
        assertNull(BoersenDirekt.moex("{\"marketdata\":{\"columns\":[\"SECID\"],\"data\":[[\"IMOEX\"]]}}"))
        assertNull(BoersenDirekt.moex("kein json"))
    }

    @Test(timeout = 20_000)
    fun pftsAusEchterAntwort() = pruefe(BoersenDirekt.pfts(res("pfts.json")), 0.0, 428.8237, d(2026, 11, 9))

    @Test(timeout = 20_000)
    fun pftsAenderungInPunkten() {
        // Wert 431,0 nach 428,0: +0,70 %
        val text = """[{"rownum":3,"caption":"PFTS Index ","value":431.0,"change":3.0,"tradedate":"06.11.2026"}]"""
        pruefe(BoersenDirekt.pfts(text), 0.7009, 431.0, d(2026, 11, 6), 1e-3)
        assertNull(BoersenDirekt.pfts("""[{"caption":"Trading Value, UAH","value":1.0,"change":0.0}]"""))
    }

    @Test(timeout = 20_000)
    fun brvmGilt_fuerAchtLaender() {
        pruefe(BoersenDirekt.brvm(res("brvm.html")), 0.2189, 521.88, d(2026, 11, 9), 1e-3)
        val q = BoersenDirekt.QUELLEN.first { it.id == "brvm" }
        assertEquals(setOf("BJ", "BF", "CI", "GW", "ML", "NE", "SN", "TG"), q.laender.toSet())
    }

    @Test(timeout = 20_000)
    fun psxKse100() = pruefe(BoersenDirekt.psx(res("psx.html")), -0.85, 173886.68, d(2026, 11, 6))

    @Test(timeout = 20_000)
    fun dsexMitUnicodeMinus() = pruefe(BoersenDirekt.dsex(res("dse_bd.html")), -0.18, 5392.82, null)

    @Test(timeout = 20_000)
    fun colomboPostAntwort() {
        val s = BoersenDirekt.aspi(res("cse_aspi.json"))
        pruefe(s, 1.3735, 21531.37, d(2026, 11, 9), 1e-3)
        val q = BoersenDirekt.QUELLEN.first { it.id == "cse-lk" }
        assertNotNull("POST mit Formularrumpf", q.formular)
    }

    @Test(timeout = 20_000)
    fun nairobiAusPunkten() = pruefe(BoersenDirekt.nasi(res("nse_ke.html")), -0.2419, 239.14, d(2026, 11, 9), 1e-3)

    @Test(timeout = 20_000)
    fun sofiaSofix() = pruefe(BoersenDirekt.sofix(res("sofia.html")), 0.10, 2105.86, null)

    @Test(timeout = 20_000)
    fun zagrebUndLjubljana() {
        pruefe(BoersenDirekt.crobex(res("zse.json")), 0.43, 4377.21, d(2026, 11, 9))
        pruefe(BoersenDirekt.sbitop(res("ljse.json")), 0.49, 2977.82, d(2026, 11, 9))
        assertNull(BoersenDirekt.sbitop(res("zse.json")))
    }

    @Test(timeout = 20_000)
    fun daressalam() = pruefe(BoersenDirekt.dsei(res("dse_tz.html")), -0.03, 4375.13, null)

    @Test(timeout = 20_000)
    fun bratislava() = pruefe(BoersenDirekt.sax(res("bsse.html")), 0.0, 321.08, null)

    @Test(timeout = 20_000)
    fun almaty() = pruefe(BoersenDirekt.kase(res("kase.html")), 0.73, 7872.77, null)

    @Test(timeout = 20_000)
    fun jamaikaAusPunkten() = pruefe(BoersenDirekt.jamaika(res("jamstockex.html")), -0.4137, 385479.64, null, 1e-3)

    @Test(timeout = 20_000)
    fun bahamas() = pruefe(BoersenDirekt.bisx(res("bisx.html")), 0.0, 3365.59, null)

    @Test(timeout = 20_000)
    fun bischkek() = pruefe(BoersenDirekt.kseKg(res("kse_kg.html")), 0.0, 8408.35, d(2026, 11, 9))

    @Test(timeout = 20_000)
    fun skopje() = pruefe(BoersenDirekt.mbi10(res("mse_mk.html")), -0.14, 8923.49, d(2026, 11, 9))

    @Test(timeout = 20_000)
    fun wienerBoerseBatx() = pruefe(BoersenDirekt.wiener(res("wien_batx.html")), -0.65, 962.70, null)

    @Test(timeout = 20_000)
    fun lagos() = pruefe(BoersenDirekt.ngx(res("ngx.json")), -0.3337, 248877.22, d(2026, 11, 6), 1e-3)

    @Test(timeout = 20_000)
    fun algier() {
        val s = BoersenDirekt.dzair(res("sgbv.html"))
        assertNotNull(s)
        // letzter Punkt 08.11.2026 gleich dem vorletzten: keine Aenderung
        assertEquals(3809.446, s!!.wert!!, 1e-3)
        assertEquals(0.0, s.prozent, 1e-9)
        assertEquals(d(2026, 11, 8), s.datum)
    }

    @Test(timeout = 20_000)
    fun accra() = pruefe(BoersenDirekt.gseGh(res("gse_gh.html")), 0.0689, 14010.02, d(2026, 11, 9), 1e-3)

    @Test(timeout = 20_000)
    fun gaboronePostOhneKoerper() {
        val s = BoersenDirekt.dciBw(res("bse_bw.json"))
        assertNotNull(s)
        assertTrue(s!!.wert!! > 11000)
        assertEquals("", BoersenDirekt.QUELLEN.first { it.id == "bse-bw" }.json)
    }

    @Test(timeout = 20_000)
    fun malta() {
        // 4300.644 gegen 4301.565
        pruefe(BoersenDirekt.malta(res("malta.json")), -0.0214, 4300.644, d(2026, 11, 9), 1e-3)
    }

    @Test(timeout = 20_000)
    fun panamaDoppeltGeparst() = pruefe(BoersenDirekt.latinex(res("latinex.json")), 0.2143, 729.39, d(2026, 11, 9), 1e-3)

    @Test(timeout = 20_000)
    fun phnomPenhMitVorzeichenAusRichtung() = pruefe(BoersenDirekt.csx(res("csx.json")), -0.07, 606.83, d(2026, 11, 6))

    @Test(timeout = 20_000)
    fun taschkentMitDatum() = pruefe(BoersenDirekt.uci(res("uzse.html")), -0.35, 1940.44, d(2026, 11, 9))

    @Test(timeout = 20_000)
    fun nepalAusZweiZeilen() {
        val s = BoersenDirekt.nepse(res("merolagani.html"))
        assertNotNull(s)
        assertEquals(2655.22, s!!.wert!!, 0.01)
        assertEquals(d(2026, 11, 6), s.datum)
    }

    @Test(timeout = 20_000)
    fun yangonMitPfeilbild() = pruefe(BoersenDirekt.myanpix(res("ysx.html")), 0.9028, 880.65, d(2026, 11, 9), 1e-3)

    @Test(timeout = 20_000)
    fun guayaquilAusZweiZeilen() = pruefe(BoersenDirekt.guayaquil(res("bvg.html")), -1.0258, 914.41, null, 1e-3)

    @Test(timeout = 20_000)
    fun zahlenformate() {
        assertEquals(1400.44, BoersenDirekt.zahl("1.400,44", true)!!, 1e-9)
        assertEquals(1400.44, BoersenDirekt.zahl("1,400.44")!!, 1e-9)
        assertEquals(7872.77, BoersenDirekt.zahl("7 872,77", true)!!, 1e-9)
        assertEquals(-0.18, BoersenDirekt.zahl("−0.18")!!, 1e-9)
    }

    @Test(timeout = 20_000)
    fun leereAntwortenGebenNull() {
        for (q in BoersenDirekt.QUELLEN) assertNull(q.id, runCatching { q.parse("<html><body>Wartung</body></html>") }.getOrNull())
    }

    @Test(timeout = 20_000)
    fun jedesLandHatEineQuelleUndKeineGeorgischeReihe() {
        val l = BoersenDirekt.laender()
        assertTrue(l.size >= 37)
        assertNull(l["GE"])
        assertEquals("moex", l["RU"]!!.id)
        assertEquals(BoersenDirekt.QUELLEN.size, BoersenDirekt.QUELLEN.map { it.id }.toSet().size)
    }
}
