package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [Globus Depot 0.70] Taetigkeitsland aus der Firmenbeschreibung, Boerse, ETF-Verteilung und Bilanz der Streuung.
class TaetigkeitTest {
    private val namen = Taetigkeit.Namen(
        mapOf(
            "Mauritius" to "MU", "Democratic Republic of the Congo" to "CD", "Republic of the Congo" to "CG", "United States of America" to "US",
            "China" to "CN", "Canada" to "CA", "Chile" to "CL", "Peru" to "PE", "Cayman Islands" to "KY", "United Kingdom" to "GB",
            "Australia" to "AU", "Brazil" to "BR", "Luxembourg" to "LU", "Mexico" to "MX", "Germany" to "DE", "Bermuda" to "BM", "Japan" to "JP",
        ),
    )

    @Test
    fun alphaminMauritiusTaetigImKongo() {
        val t = "Alphamin Resources Corp. explores for and produces tin in Africa. It operates the Bisie tin mine in the Democratic Republic of the Congo. " +
            "The company was incorporated in Canada and is headquartered in Ebene, Mauritius."
        assertEquals("CD", Taetigkeit.taetigkeitsland("MU", t, true, namen))
        val h = Taetigkeit.herkunft("MU", "AFM.V", t, true, namen)
        assertEquals("CD", h.land)
        assertTrue(h.abweichend)
        assertEquals("CA", h.boerse)
    }

    @Test
    fun kongoRepublikUndKurzformen() {
        assertEquals("CD", Taetigkeit.taetigkeitsland("MU", "Its mine is located in the DRC.", true, namen))
        assertEquals("CD", Taetigkeit.taetigkeitsland("MU", "Projects in Congo.", true, namen))
        assertEquals("CG", Taetigkeit.taetigkeitsland("MU", "A license in the Republic of the Congo.", true, namen))
    }

    @Test
    fun kaimanHoldingMitChinaGeschaeft() {
        val t = "The company operates e-commerce platforms in China and sells goods to customers in China, the United States of America and Canada."
        assertEquals("CN", Taetigkeit.taetigkeitsland("KY", t, false, namen))
    }

    @Test
    fun holdingOhneKlaresLandBleibtAmSitz() {
        val t = "The company has operations in Brazil, Germany, Mexico and Japan."
        assertNull(Taetigkeit.taetigkeitsland("LU", t, false, namen))
        assertNull(Taetigkeit.taetigkeitsland("KY", "A holding company.", false, namen))
        assertNull(Taetigkeit.taetigkeitsland("KY", "", false, namen))
    }

    @Test
    fun normalerSitzBleibt() {
        val t = "The company sells software to customers in China and Germany."
        assertNull(Taetigkeit.taetigkeitsland("US", t, false, namen))
        // Rohstofffirma, die auch zu Hause foerdert oder mehrere Laender nennt, bleibt am Sitz
        assertNull(Taetigkeit.taetigkeitsland("GB", "It operates mines in Australia, Canada, Chile and Peru.", true, namen))
        assertNull(Taetigkeit.taetigkeitsland("CA", "It operates mines in Canada and Chile.", true, namen))
    }

    @Test
    fun rohstofffirmaMitEinemAuslandsprojekt() {
        val t = "The company is listed on the Toronto Stock Exchange in Canada. Its sole project is the Tia mine located in Peru."
        assertEquals("PE", Taetigkeit.taetigkeitsland("CA", t, true, namen))
        // Keine Rohstofffirma: Sitz bleibt
        assertNull(Taetigkeit.taetigkeitsland("CA", t, false, namen))
    }

    @Test
    fun unechteNennungenZaehlenNicht() {
        assertNull(Taetigkeit.taetigkeitsland("KY", "It drills in the Gulf of Mexico and New Mexico.", false, namen))
        assertEquals(listOf("MX"), namen.nennungen("Sales in Mexico.").map { it.first })
    }

    @Test
    fun boersenEndung() {
        assertEquals("US", Taetigkeit.boerse("AAPL"))
        assertEquals("CA", Taetigkeit.boerse("AFM.V"))
        assertEquals("DE", Taetigkeit.boerse("SAP.DE"))
        assertNull(Taetigkeit.boerse("BTC-USD"))
        assertNull(Taetigkeit.boerse("^GSPC"))
    }

    @Test
    fun etfSchluesselUndVerteilung() {
        val d = EtfLaender.lesen("""{"w":{"MSCIWORLD":{"US":70,"JP":30},"SP500":{"US":100},"DAX":{"DE":100},"MSCIEM":{"CN":100}},"welt":{"US":60,"CN":40}}""")
        assertEquals("MSCIWORLD", EtfLaender.schluessel("MSCI World", "MSCIWORLD", "GLOBAL", "SEKTORUEBERGREIFEND", d))
        assertEquals("MSCIEM", EtfLaender.schluessel("MSCI EM IMI", null, "SCHWELLENLAENDER", "SEKTORUEBERGREIFEND", d))
        assertEquals("SP500", EtfLaender.schluessel("S&P 500 Health Care", null, "NORDAMERIKA", "GESUNDHEIT", d))
        assertNull(EtfLaender.schluessel("Semiconductor", null, "GLOBAL", "HALBLEITER", d))
        assertNull(EtfLaender.schluessel("Physical Gold", null, "GLOBAL", "ROHSTOFFE", d))
        assertEquals(0.7, d.verteilung("MSCIWORLD")!!["US"]!!, 1e-9)
        assertNull(d.verteilung("X"))
    }

    @Test
    fun anteileMitUndOhneEtf() {
        val land = mapOf("AAPL" to "US", "SAP.DE" to "DE")
        val posten = listOf(Triple("AAPL", 100.0, false), Triple("SAP.DE", 100.0, false), Triple("EUNL.DE", 200.0, true), Triple("GLD", 100.0, true))
        val etf = mapOf("EUNL.DE" to mapOf("US" to 0.7, "JP" to 0.3))
        val mit = DepotStreuung.anteile(posten, { land[it] }) { etf[it] }
        assertEquals(0.48, mit.nachLand["US"]!!, 1e-9)
        assertEquals(0.12, mit.nachLand["JP"]!!, 1e-9)
        assertEquals(0.2, mit.nachLand["DE"]!!, 1e-9)
        assertEquals(0.2, mit.unzuordenbar, 1e-9)
        val ohne = DepotStreuung.anteile(posten, { land[it] }, null)
        assertEquals(0.6, ohne.unzuordenbar, 1e-9)
    }

    @Test
    fun bilanzZahlen() {
        val a = GlobusEbenen.Anteile(mapOf("US" to 0.5, "DE" to 0.25, "BR" to 0.15, "JP" to 0.1), 0.0, 1000.0)
        val b = DepotStreuung.bilanz(a, mapOf("US" to 0.6, "DE" to 0.05, "BR" to 0.05, "JP" to 0.3), "DE")!!
        assertEquals(4, b.laender)
        assertEquals("US", b.groesstes.first)
        assertEquals(0.25, b.heimAnteil, 1e-9)
        assertEquals(0.85, b.industrie, 1e-9)
        assertEquals(0.5, b.regionen[DepotStreuung.Region.NORDAMERIKA]!!, 1e-9)
        assertEquals(0.15, b.regionen[DepotStreuung.Region.LATEINAMERIKA]!!, 1e-9)
        assertEquals(0.95, b.weltIndustrie, 1e-9)
        assertEquals(DepotStreuung.Konzentration.MAESSIG, b.konzentration)
        // Nur ein Land: sehr hohe Konzentration; leeres Depot: keine Bilanz
        assertEquals(DepotStreuung.Konzentration.SEHR_HOCH, DepotStreuung.bilanz(GlobusEbenen.Anteile(mapOf("US" to 1.0), 0.0, 1.0), emptyMap(), "DE")!!.konzentration)
        assertNull(DepotStreuung.bilanz(GlobusEbenen.Anteile(emptyMap(), 0.0, 0.0), emptyMap(), "DE"))
        assertTrue(DepotStreuung.industrieland("CH") && !DepotStreuung.industrieland("CN"))
    }

    @Test
    fun holdingSitzOhneBeschreibungNimmtDasLandDerHauptboerse() {
        // Bermuda-Versicherer an der US-Boerse: gezaehlt wird die USA
        val h = Taetigkeit.herkunftErweitert("BM", "ACGL", "", false, namen)
        assertEquals("US", h.land)
        assertTrue(h.abweichend)
        // Sitz im Holding-Land und Boerse im selben Land: kein Wechsel
        assertEquals("BM", Taetigkeit.herkunftErweitert("BM", "XYZ.BM", "", false, namen).land)
        // Normaler Sitz bleibt
        assertEquals("DE", Taetigkeit.herkunftErweitert("DE", "SAP.DE", "", false, namen).land)
        assertEquals("CY", Taetigkeit.herkunftErweitert("CY", "FRO.XX", "", false, namen).land)
    }

    @Test
    fun belegtesLandUndEigeneWahlGehenVor() {
        val datei = Taetigkeit.lesenDatei("""{"stand":"2026-11","w":{"CRH":"US","AD.AS":"US","KAPUTT":"USA"}}""")
        assertEquals(mapOf("CRH" to "US", "AD.AS" to "US"), datei)
        assertEquals("US", Taetigkeit.herkunftErweitert("IE", "CRH", "", false, namen, datei).land)
        // Die Person gewinnt gegen Datei und Beschreibung
        val h = Taetigkeit.herkunftErweitert("IE", "CRH", "", false, namen, datei, "CA")
        assertEquals("CA", h.land)
        assertEquals("IE", h.sitz)
        assertTrue(Taetigkeit.lesenDatei("kein json").isEmpty())
    }

    @Test
    fun beschreibungSchlaegtBoerseBeiHoldingSitz() {
        val t = "Alphamin Resources Corp. operates the Bisie tin mine in the Democratic Republic of the Congo. Headquartered in Ebene, Mauritius."
        assertEquals("CD", Taetigkeit.herkunftErweitert("MU", "AFM.V", t, true, namen).land)
    }

    @Test
    fun fondsOhneEinLandUndKanadaBekommenKeinenFalschenIndex() {
        val d = EtfLaender.lesen("""{"w":{"MSCIWORLD":{"US":70,"JP":30},"MSCIACWI":{"US":60,"JP":40},"SP500":{"US":100},"PACXJP":{"AU":100}},"welt":{"US":60}}""")
        assertNull(EtfLaender.schluessel("iShares MSCI World ex USA", null, "GLOBAL", "SEKTORUEBERGREIFEND", d))
        assertNull(EtfLaender.schluessel("Vanguard FTSE All-World ex-US", null, "GLOBAL", "SEKTORUEBERGREIFEND", d))
        assertNull(EtfLaender.schluessel("MSCI ACWI ex Japan", null, "GLOBAL", "SEKTORUEBERGREIFEND", d))
        assertNull(EtfLaender.schluessel("iShares MSCI Canada", null, "NORDAMERIKA", "SEKTORUEBERGREIFEND", d))
        assertNull(EtfLaender.schluessel("Xtrackers MSCI Mexico", null, "NORDAMERIKA", "SEKTORUEBERGREIFEND", d))
        assertEquals("SP500", EtfLaender.schluessel("iShares Core S&P 500", null, "NORDAMERIKA", "SEKTORUEBERGREIFEND", d))
        assertEquals("MSCIWORLD", EtfLaender.schluessel("Xtrackers MSCI World", null, "GLOBAL", "SEKTORUEBERGREIFEND", d))
    }

    @Test
    fun weltweitTaetigeFirmenZaehlenInDerZentraleUndSindGekennzeichnet() {
        val text = """{"stand":"2026-11","w":{"CRH":"US"},"z":{"ACN":"IE","KAPUTT":"IRL"}}"""
        assertEquals(mapOf("ACN" to "IE"), Taetigkeit.lesenZentralen(text))
        assertEquals(mapOf("CRH" to "US"), Taetigkeit.lesenDatei(text))
        val z = Taetigkeit.lesenZentralen(text)
        val h = Taetigkeit.veredeln(Taetigkeit.Herkunft("IE", null, "US"), "ACN", emptyMap(), null, z)
        assertEquals("IE", h.land)
        assertTrue(h.weltweit)
        // Die eigene Wahl und ein belegtes Land gehen vor
        assertEquals("CA", Taetigkeit.veredeln(Taetigkeit.Herkunft("IE", null, "US"), "ACN", emptyMap(), "CA", z).land)
        assertTrue(!Taetigkeit.veredeln(Taetigkeit.Herkunft("IE", null, "US"), "ACN", emptyMap(), "CA", z).weltweit)
        assertTrue(Taetigkeit.lesenZentralen("kein json").isEmpty())
    }
}
