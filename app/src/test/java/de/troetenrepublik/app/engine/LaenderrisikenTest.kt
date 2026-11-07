package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Abschnitt Risiken im Laenderblatt: Leser der Quellen mit gekuerzten echten Antworten.
class LaenderrisikenTest {
    private fun land(a2: String, a3: String, en: String, de: String = en) = GlobusLand(a2, a3, de, en, emptyArray(), IntArray(0))
    private fun asset(name: String) = listOf("src/main/assets/globus/$name", "../app/src/main/assets/globus/$name").map { File(it) }.first { it.exists() }

    @Test
    fun namenVergleichen() {
        assertEquals("gambia", Laenderrisiken.norm("The Gambia"))
        assertEquals("cotedivoire", Laenderrisiken.norm("Côte d’Ivoire"))
        assertTrue("usa" in Laenderrisiken.schluessel(land("US", "USA", "United States of America")))
        assertTrue("gaza" in Laenderrisiken.schluessel(land("PS", "PSE", "Palestine")))
    }

    @Test
    fun abweichendeNamenWerdenZugeordnet() {
        // Schreibweisen aus US-Hinweisen, Wikipedia-Ratings und Wahlliste gegen die Namen im Globus
        val paare = mapOf(
            "Bahamas" to land("BS", "BHS", "The Bahamas"), "Brunei Darussalam" to land("BN", "BRN", "Brunei"),
            "Cabo Verde" to land("CV", "CPV", "Cape Verde"), "China" to land("CN", "CHN", "People's Republic of China"),
            "DR Congo" to land("CD", "COD", "Democratic Republic of the Congo"), "Congo" to land("CG", "COG", "Republic of the Congo"),
            "Korea" to land("KR", "KOR", "South Korea"), "Macao" to land("MO", "MAC", "Macau"), "UAE" to land("AE", "ARE", "United Arab Emirates"),
            "Timor Leste" to land("TL", "TLS", "East Timor"), "Turkiye" to land("TR", "TUR", "Turkey"), "Micronesia" to land("FM", "FSM", "Federated States of Micronesia"),
            "Burma" to land("MM", "MMR", "Myanmar"), "Naoero" to land("NR", "NRU", "Nauru"), "Kyrgyz Republic" to land("KG", "KGZ", "Kyrgyzstan"),
            "Czechia" to land("CZ", "CZE", "Czech Republic"), "Gambia" to land("GM", "GMB", "The Gambia"), "Cote d'Ivoire" to land("CI", "CIV", "Ivory Coast"),
            "Sahrawi Arab Democratic Republic" to land("EH", "ESH", "Western Sahara"),
        )
        for ((name, l) in paare) {
            val n = Laenderrisiken.norm(name.replace("Timor Leste", "Timor-Leste"))
            assertTrue(name, n in Laenderrisiken.schluessel(l))
        }
    }

    @Test
    fun perzentilUndStruktur() {
        fun reihe(vararg p: Pair<String, Double>) = Weltbank.Reihe("X", 0, p.associate { it.first to Weltbank.Wert(2025, it.second) } + mapOf("OLD" to Weltbank.Wert(2017, 50.0)))
        val r = reihe("AAA" to 10.0, "BBB" to 40.0, "CCC" to 90.0)
        // 4 Werte, CCC liegt ueber den drei anderen
        assertEquals(100, Laenderrisiken.perzentil(r, "CCC")!!.perzentil)
        assertEquals(0, Laenderrisiken.perzentil(r, "AAA")!!.perzentil)
        assertNull(Laenderrisiken.perzentil(r, "ZZZ"))
        assertEquals(2017, Laenderrisiken.perzentil(r, "OLD")!!.jahr)
        assertNull(Laenderrisiken.perzentil(r, "AAA")!!.jahr)
        val res = reihe("AAA" to 2.1, "BBB" to 6.0)
        val brenn = reihe("AAA" to 79.0, "BBB" to 2.0)
        val erze = reihe("AAA" to 7.0, "BBB" to 3.0)
        val jug = reihe("AAA" to 28.0, "WLD" to 13.0)
        val alt = reihe("AAA" to 21.5, "BBB" to 9.0)
        val aus = reihe("AAA" to 45.2)
        val a = Laenderrisiken.struktur("AAA", res, brenn, erze, jug, alt, aus)
        assertEquals(
            listOf(Laenderrisiken.Art.RESERVEN, Laenderrisiken.Art.ROHSTOFF, Laenderrisiken.Art.JUGEND, Laenderrisiken.Art.ALTER, Laenderrisiken.Art.AUSLAND),
            a.map { it.art },
        )
        assertEquals(86.0, a[1].wert, 1e-9)
        assertEquals(13.0, a[2].b!!, 1e-9)
        // BBB: Reserven 6 Monate, Rohstoffe 5 %, 65+ nur 9 %: keine Hinweise
        assertTrue(Laenderrisiken.struktur("BBB", res, brenn, erze, null, alt, null).isEmpty())
    }

    @Test
    fun usReisehinweise() {
        val text = """[{"Title":"Iran - Level 4: Do Not Travel","Link":"https://travel.state.gov/x","Category":["IR"],"Summary":"Do not travel<p>to <b>Iran </b>"},
            {"Title":"Mexico Travel Advisory - Level 2: Exercise Increased Caution","Category":["MX"]},
            {"Title":"Switzerland  - Level 1: Exercise Normal Precautions","Category":["SR"]},
            {"Title":"Gaza - Level 4: Do Not Travel","Category":["IS"]},
            {"Title":"The West Bank - Level 3: Reconsider Travel","Category":["IS"]},
            {"Title":"Côte d’Ivoire - Level 2: Exercise Increased Caution","Category":["IV"]}]"""
        val s = Reisehinweise.usLesen(text, 5)!!
        assertEquals(4, Reisehinweise.usStufe(s, land("IR", "IRN", "Iran")))
        assertEquals(2, Reisehinweise.usStufe(s, land("MX", "MEX", "Mexico")))
        assertEquals(1, Reisehinweise.usStufe(s, land("CH", "CHE", "Switzerland")))
        assertEquals(4, Reisehinweise.usStufe(s, land("PS", "PSE", "Palestine")))
        assertEquals(2, Reisehinweise.usStufe(s, land("CI", "CIV", "Ivory Coast")))
        assertNull(Reisehinweise.usStufe(s, land("DE", "DEU", "Germany")))
        assertNull(Reisehinweise.usLesen("<html>nope</html>", 0))
    }

    @Test
    fun auswaertigesAmt() {
        // Aufbau laut Beschreibung der OpenData-Schnittstelle; eine Pruefungsseite ergibt null
        val text = """{"response":{"lastModified":1.7E12,"contentList":["1","2","3"],
            "1":{"countryCode":"AF","countryName":"Afghanistan","warning":true,"partialWarning":false},
            "2":{"countryCode":"EG","countryName":"Ägypten","warning":false,"partialWarning":true},
            "3":{"countryCode":"AT","countryName":"Österreich","warning":false,"partialWarning":false}}}"""
        val s = Reisehinweise.aaLesen(text, 1)!!
        assertEquals(setOf("AF"), s.warnung)
        assertEquals(setOf("EG"), s.teilwarnung)
        assertNull(Reisehinweise.aaLesen("<html><body>Verification</body></html>", 1))
    }

    @Test
    fun sanktionenOpenSanctions() {
        val text = """{"last_change":"2026-11-06T13:43:46","targets":{"total":73878,"countries":[{"code":"ru","count":23434,"label":"Russia"},{"code":"pk","count":5644,"label":"Pakistan"},{"code":"zz","count":0,"label":"x"}],"schemata":[]}}"""
        val s = Sanktionen.osLesen(text, 1)!!
        assertEquals(23434, s.laender["RU"])
        assertEquals(2, s.laender.size)
        assertNull(Sanktionen.osLesen("{}", 1))
    }

    @Test
    fun sanktionenListeImPaket() {
        val l = Sanktionen.listeLesen(asset("sanktionen.json").readText())!!
        assertTrue(l.eu.getValue("RU").size >= 3)
        assertEquals("Iran Sanctions", l.us.getValue("IR")[0])
        assertFalse(l.eu.containsKey("US"))
        assertFalse(l.eu.containsKey("TN"))
    }

    @Test
    fun kreditratings() {
        val text = """
==Standard & Poor's==
{{static row numbers}}
{| class="wikitable sortable"
|-
! data-sort-type=text | Country/Territory
|-
| {{flag|Albania}} || data-sort-value=111 | {{Percentage bar|100|c=#ffcc66;|BB|color=black|width=50}}|| data-sort-value=2 | Stable&nbsp;{{Steady}} || 21 March 2025 || <ref>{{Cite web |url=https://x |date=9 January 2026}}</ref>
|-
| {{flag|Andorra}} || data-sort-value=161 | {{Percentage bar|100|c=#e0ff00;|A−|color=black|width=50}} || data-sort-value=2 | Stable&nbsp;{{Steady}} || 10 May 2024 || <ref>{{Cite web}}</ref>
|}
==Fitch==
{|
|-
| {{flag|Andorra}} || data-sort-value=151 | {{Percentage bar|100|c=#e0ff00;|A−|color=black|width=50}} || data-sort-value=2 | Negative&nbsp;{{Decrease}} || 18 September 2026 || <ref>{{cite web}}</ref>
|}
==Moody's==
|-
| {{flag|Angola}} || data-sort-value=062 | {{Percentage bar|100|c=#ff6666;|B3|color=black|width=50}} || data-sort-value=3 | Positive&nbsp;{{Increase}} || 24 October 2023 || <ref>{{Cite web}}</ref>
==Others==
===DBRS Morningstar===
|-
| {{flag|Austria}} || data-sort-value=221 | {{Percentage bar|100|c=#009900;|AA<small>(high)</small>|color=white|width=60}}|| data-sort-value="2" | Stable&nbsp;{{Steady}} || {{date|05.06.2026|DMY}}<ref>{{Cite web}}</ref>
===Scope Ratings===
|-
| {{flag|Austria}} || data-sort-value=201 | {{Percentage bar|100|c=#009900;|AA+|color=white|width=50}} || data-sort-value=1 | Negative&nbsp;{{Decrease}}|| 12 September 2025
|-
| {{flag|China}}|| data-sort-value="161" | {{Percentage bar|100|c=#ccff66;|A|color=black|width=50}} || data-sort-value=2 | Stable&nbsp;{{Steady}} || 26 July 2024
===JCR===
|-
| {{flag|Andorra}} || data-sort-value=1 | {{Percentage bar|100|c=#ff0000;|ZZ|color=black|width=50}} || Stable || 2020
"""
        val s = Kreditratings.lesen(text, 1)!!
        val andorra = Kreditratings.fuer(s, land("AD", "AND", "Andorra"))
        assertEquals(listOf("S&P", "Fitch"), andorra.map { it.agentur })
        assertEquals("A−", andorra[0].note)
        assertEquals("Stable", andorra[0].ausblick)
        assertEquals(2024, andorra[0].jahr)
        assertEquals("Negative", andorra[1].ausblick)
        val albania = Kreditratings.fuer(s, land("AL", "ALB", "Albania")).single()
        assertEquals("BB", albania.note)
        assertEquals(2025, albania.jahr)
        assertEquals("B3", Kreditratings.fuer(s, land("AO", "AGO", "Angola")).single().note)
        val at = Kreditratings.fuer(s, land("AT", "AUT", "Austria"))
        assertEquals(listOf("DBRS Morningstar", "Scope"), at.map { it.agentur })
        assertEquals("AA (high)", at[0].note)
        assertEquals(2026, at[0].jahr)
        assertEquals(1, Kreditratings.fuer(s, land("CN", "CHN", "People's Republic of China")).size)
        assertEquals("""{"parse":{"wikitext":"a"}}""".let { Kreditratings.wikitext(it) }, "a")
    }

    @Test
    fun konflikteImPaket() {
        val s = Konflikte.lesen(asset("konflikte.json").readText())!!
        assertEquals(2025, s.jahr)
        val ukr = Konflikte.fuer(s, land("UA", "UKR", "Ukraine"))
        assertEquals(2, ukr.single().stufe)
        assertTrue(Konflikte.fuer(s, land("MM", "MMR", "Myanmar")).size >= 4)
        assertTrue(Konflikte.fuer(s, land("DE", "DEU", "Germany")).isEmpty())
        assertEquals("Regierung Syria", Konflikte.seite("Government of Syria", true))
    }

    @Test
    fun katastrophen() {
        val text = """{"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"eventtype":"FL","name":"Flood in Italy","alertlevel":"Green","iso3":"ITA","fromdate":"2026-10-07T01:00:00","todate":"2026-11-07T01:00:00","affectedcountries":[{"iso2":"IT","iso3":"ITA"}]}},
            {"type":"Feature","properties":{"eventtype":"EQ","alertlevel":"Orange","iso3":"IDN","fromdate":"2026-11-01T01:00:00","todate":"2026-11-01T01:00:00","affectedcountries":[{"iso2":"ID","iso3":"IDN"},{"iso2":"TL","iso3":"TLS"}]}},
            {"type":"Feature","properties":{"eventtype":"TC","alertlevel":"Red","iso3":"PHL","fromdate":"2026-09-01T01:00:00","todate":"2026-09-05T01:00:00","affectedcountries":[{"iso2":"PH","iso3":"PHL"}]}}]}"""
        val s = Katastrophen.lesen(text, 1)!!
        assertEquals(2, s.ereignisse.size)
        val heute = LocalDate.of(2026, 11, 6)
        assertEquals(listOf("EQ"), Katastrophen.fuer(s, "TLS", heute).map { it.typ })
        assertTrue(Katastrophen.fuer(s, "ITA", heute).isEmpty())
        assertTrue(Katastrophen.fuer(s, "PHL", heute).isEmpty())
        assertNull(Katastrophen.lesen("nope", 1))
    }

    @Test
    fun wahlen() {
        val text = """
{| class="wikitable sortable"
|-
! rowspan=2|Country
|-
|{{Flag|Angola}}
|5
|[[2022 Angolan general election|{{dts|24 Aug 2022}}]]
|[[2027 Angolan general election|{{dts|24 Aug 2027}}]]
|5
|[[2022 Angolan general election|{{dts|24 Aug 2022}}]]
|[[2027 Angolan general election|{{dts|24 Aug 2027}}]]
|[[Dominant-party system]], [[2022 Angolan general election#Aftermath|alleged fraud]]
|[[People's Movement for the Liberation of Angola|MPLA]]
|-
|{{Flag|Botswana}}
|5
|[[2024 Botswana general election|{{dts|30 Oct 2024}}]]
|[[Next Botswana general election|{{dts|7 Jan 2030}}]]
|colspan=3 {{n/a}}
|
|[[Umbrella for Democratic Change|UDC]]
|-
|{{Flag|Burkina Faso}}
|5
|[[2020 Burkinabè general election|{{dts|22 Nov 2020}}]]
|[[September 2022 Burkina Faso coup d'état|Next]]
|5
|[[2020 Burkinabè general election|{{dts|22 Nov 2020}}]]
|[[September 2022 Burkina Faso coup d'état|Next]]
|[[Coup d'état]]
|-
|{{Flag|Burundi}}
|5
|[[2025 Burundian parliamentary election|{{dts|5 Jun 2025}}]]
|{{dts|Jul 2030}}
|7
|[[2020 Burundian general election|{{dts|20 May 2020}}]]
|[[2027 Burundian presidential election|{{dts|3 May 2027}}]]
|[[Dominant-party system]]
|}
"""
        val s = Wahlen.lesen(text, 1)!!
        val heute = LocalDate.of(2026, 11, 6)
        val ao = Wahlen.fuer(s, land("AO", "AGO", "Angola"), heute)!!
        assertEquals(Wahlen.Datum(2027, 8, 24).spaetestens(), ao.parlament!!.spaetestens())
        assertEquals(2027, ao.praesident!!.jahr)
        val bw = Wahlen.fuer(s, land("BW", "BWA", "Botswana"), heute)!!
        assertEquals(2030, bw.parlament!!.jahr)
        assertNull(bw.praesident)
        assertNull(Wahlen.fuer(s, land("BF", "BFA", "Burkina Faso"), heute))
        val bi = Wahlen.fuer(s, land("BI", "BDI", "Burundi"), heute)!!
        assertEquals(7, bi.parlament!!.monat)
        assertEquals(0, bi.parlament!!.tag)
        assertEquals(3, bi.praesident!!.tag)
        assertNotNull(Wahlen.datum("2030"))
        assertNull(Wahlen.fuer(s, land("AO", "AGO", "Angola"), LocalDate.of(2028, 1, 1)))
    }
}
