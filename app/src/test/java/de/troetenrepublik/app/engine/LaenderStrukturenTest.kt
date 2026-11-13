package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// [Laender Strukturen 0.70] Gebuendelte Strukturprobleme: Datei laedt, Codes gueltig, Quellen mit URL, beide Sprachen gefuellt.
class LaenderStrukturenTest {
    private fun asset(name: String) = listOf("src/main/assets/globus/$name", "../app/src/main/assets/globus/$name").map { File(it) }.first { it.exists() }

    private val stand = LaenderStrukturen.lesen(asset("strukturen.json").readText())

    @Test
    fun dateiLaedt() {
        assertNotNull(stand)
        assertTrue(stand!!.l.size >= 150)
    }

    @Test
    fun codesSindGueltig() {
        val roh = Laenderrisiken.json.parseToJsonElement(asset("laender.json").readText())
        val codes = (roh as kotlinx.serialization.json.JsonObject)["c"]!!.let { c -> (c as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonArray)[1].toString().trim('"') } }.toSet()
        for (k in stand!!.l.keys) assertTrue(k, k in codes)
    }

    @Test
    fun jedeQuelleHatUrl() {
        for ((k, ps) in stand!!.l) for (p in ps) {
            assertTrue(k, p.q.isNotEmpty())
            for (q in p.q) assertTrue("$k ${q.u}", q.u.startsWith("https://") && q.u.length > 12)
        }
    }

    @Test
    fun deutschUndEnglischVollstaendig() {
        for ((k, ps) in stand!!.l) {
            assertTrue(k, ps.isNotEmpty())
            for (p in ps) {
                val texte = listOf(p.t, p.d) + p.p.flatMap { listOf(it.n, it.t) } + p.q.map { it.n }
                for (t in texte) { assertTrue("$k de", t.de.isNotBlank()); assertTrue("$k en", t.en.isNotBlank()) }
                assertTrue(k, p.t.de.length <= 40 && p.t.en.length <= 40)
            }
        }
    }

    private val karte: List<String> by lazy {
        val roh = Laenderrisiken.json.parseToJsonElement(asset("laender.json").readText())
        (roh as kotlinx.serialization.json.JsonObject)["c"]!!.let { c -> (c as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonArray)[1].toString().trim('"') } }
    }

    // Gebiete der Karte ohne eigene Wirtschaft (unbewohnt, Militaer- und Aussengebiete, Streitgebiete) tragen keine Eintraege
    private val ausnahmen = setOf(
        "ATA", "ATF", "HMD", "IOT", "PGA", "UMI", "USG", "ESB", "WSB", "BRT", "SCR", "SGS", "PCN", "NFK", "ASM", "CCK", "ALA", "SPM", "WLF", "BLM", "MAF", "SXM", "SHN", "AIA", "MSR",
        "COK", "NIU", "FLK", "GIB", "IMN", "GGY", "JEY", "VAT", "ESH", "ABW", "CUW", "CYM", "BMU", "TCA", "VGB", "VIR", "GUM", "MNP", "FRO",
    )

    // Noch nicht neu recherchiert: Eintrag fehlt oder ist der alte Stand (Liste schrumpft mit jedem Block)
    private val offen = setOf<String>()

    // Mit Kennzahl, Programm und Quelle neu geschrieben
    private val neu = setOf("AFG", "AGO", "ALB", "AND", "ARE", "ARG", "ARM", "ATG", "AUS", "AUT", "AZE", "BDI", "BEL", "BEN", "BFA", "BGD", "BGR", "BHR", "BHS", "BIH", "BLR", "BLZ", "BOL", "BRA", "BRB", "BRN", "BTN", "BWA", "CAF", "CAN", "CHE", "CHL", "CHN", "CIV", "CMR", "COD", "COG", "COL", "COM", "CPV", "CRI", "CUB", "CYP", "CZE", "DEU", "DJI", "DMA", "DNK", "DOM", "DZA", "ECU", "EGY", "ERI", "ESP", "EST", "ETH", "FIN", "FJI", "FRA", "FSM", "GAB", "GBR", "GEO", "GHA", "GIN", "GMB", "GNB", "GNQ", "GRC", "GRD", "GRL", "GTM", "GUY", "HKG", "HND", "HRV", "HTI", "HUN", "IDN", "IND", "IRL", "IRN", "IRQ", "ISL", "ISR", "ITA", "JAM", "JOR", "JPN", "KAZ", "KEN", "KGZ", "KHM", "KIR", "KNA", "KOR", "KOS", "KWT", "LAO", "LBN", "LBR", "LBY", "LCA", "LIE", "LKA", "LSO", "LTU", "LUX", "LVA", "MAC", "MAR", "MCO", "MDA", "MDG", "MDV", "MEX", "MHL", "MKD", "MLI", "MLT", "MMR", "MNE", "MNG", "MOZ", "MRT", "MUS", "MWI", "MYS", "NAM", "NCL", "NER", "NGA", "NIC", "NLD", "NOR", "NPL", "NRU", "NZL", "OMN", "PAK", "PAN", "PER", "PHL", "PLW", "PNG", "POL", "PRI", "PRK", "PRT", "PRY", "PSE", "PYF", "QAT", "ROU", "RUS", "RWA", "SAU", "SDN", "SEN", "SGP", "SLB", "SLE", "SLV", "SMR", "SOM", "SRB", "SSD", "STP", "SUR", "SVK", "SVN", "SWE", "SWZ", "SYC", "SYR", "TCD", "TGO", "THA", "TJK", "TKM", "TLS", "TON", "TTO", "TUN", "TUR", "TUV", "TWN", "TZA", "UGA", "UKR", "URY", "USA", "UZB", "VCT", "VEN", "VNM", "VUT", "WSM", "YEM", "ZAF", "ZMB", "ZWE")

    @Test
    fun karteAbgedeckt() {
        for (c in karte) if (c !in ausnahmen && c !in offen) assertTrue("Eintrag fehlt: $c", stand!!.l.containsKey(c))
        for (c in offen) assertTrue("offen, aber vorhanden: $c", !stand!!.l.containsKey(c))
    }

    @Test
    fun neueEintraegeVollstaendig() {
        for (k in neu) {
            val ps = stand!!.l[k].orEmpty()
            assertTrue(k, ps.size in 2..5)
            for (p in ps) {
                assertTrue("$k Kennzahl", p.d.de.any { it.isDigit() } && p.d.en.any { it.isDigit() })
                assertTrue("$k Programm ${p.t.de}", p.p.isNotEmpty())
                for (g in p.p) assertTrue("$k Programmtext", g.t.de.length > 10 && g.t.en.length > 10)
            }
        }
    }

    @Test
    fun stilUndEindeutigkeit() {
        val strich = Regex("[–—]| - ")
        for ((k, ps) in stand!!.l) {
            assertEquals("doppelte Bezeichnung $k", ps.size, ps.map { it.t.de }.toSet().size)
            assertEquals("doppelte Bezeichnung en $k", ps.size, ps.map { it.t.en }.toSet().size)
            for (p in ps) {
                val texte = listOf(p.t, p.d) + p.p.flatMap { listOf(it.n, it.t) } + p.q.map { it.n }
                for (t in texte) assertTrue("Gedankenstrich $k: ${t.de} / ${t.en}", !strich.containsMatchIn(t.de) && !strich.containsMatchIn(t.en))
                for (q in p.q) assertTrue("URL $k ${q.u}", runCatching { java.net.URI(q.u).let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false) && q.u.all { it.code in 33..126 })
            }
        }
    }

    @Test
    fun beispieleVorhanden() {
        val chn = stand!!.l["CHN"].orEmpty()
        assertTrue(chn.size >= 2)
        assertEquals(true, stand!!.l.containsKey("DEU"))
    }
}
