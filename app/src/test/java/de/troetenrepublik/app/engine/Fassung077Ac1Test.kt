package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Edgar13f
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung077Ac1Test {
    @Test
    fun zufallsKontaktHatGueltigeKennung() {
        val k = SecInsider.zufallsKontakt()
        assertTrue(Regex("^[a-z0-9]{12}@example\\.org$").matches(k))
        assertEquals("Tryfolio/" + de.troetenrepublik.app.market.Aktualisierung.FASSUNG + " " + k, SecInsider.kennung(k))
        assertNull(SecInsider.kennung(""))
    }

    @Test
    fun trefferJeMelderEinmalOhneZusatz() {
        val j = """{"hits":{"hits":[
          {"_source":{"ciks":["0001649339"],"display_names":["Scion Asset Management, LLC  (CIK 0001649339)"],"file_date":"2025-11-03"}},
          {"_source":{"ciks":["0001649339"],"display_names":["Scion Asset Management, LLC  (CIK 0001649339)"],"file_date":"2025-08-14"}},
          {"_source":{"ciks":["0001067983"],"display_names":["BERKSHIRE HATHAWAY INC  (CIK 0001067983)"],"file_date":"2026-08-14"}}]}}"""
        val t = Edgar13f.treffer(j)
        assertEquals(2, t.size)
        assertEquals("Scion Asset Management, LLC", t[0].name)
        assertEquals("1649339", t[0].cik)
        assertEquals("2025-11-03", t[0].eingang)
        assertTrue(Edgar13f.suchAdresse("Scion & Co", "2025-01-01", "2026-01-01").contains("forms=13F-HR"))
    }

    @Test
    fun trefferMitSuchwortImNamenZuerstOhneKuerzel() {
        val j = """{"hits":{"hits":[
          {"_source":{"ciks":["0000913144"],"display_names":["RENAISSANCERE HOLDINGS LTD  (RNR, RNR-PF)  (CIK 0000913144)"],"file_date":"2026-08-07"}},
          {"_source":{"ciks":["0001500000"],"display_names":["Main Street Group, LTD  (CIK 0001500000)"],"file_date":"2026-10-28"}},
          {"_source":{"ciks":["0001037389"],"display_names":["RENAISSANCE TECHNOLOGIES LLC  (CIK 0001037389)"],"file_date":"2026-08-13"}},
          {"_source":{"ciks":["0001600000"],"display_names":["Abc Partners (UK)  (CIK 0001600000)"],"file_date":"2026-08-13"}}]}}"""
        val t = Edgar13f.treffer(j, wort = "renaissance")
        assertEquals(listOf("Renaissancere Holdings Ltd", "Renaissance Technologies LLC", "Main Street Group, LTD", "Abc Partners (UK)"), t.map { it.name })
        assertEquals("Bank of America Corp", Edgar13f.lesbar("BANK OF AMERICA CORP"))
        assertEquals("The Vanguard Group, Inc.", Edgar13f.lesbar("THE VANGUARD GROUP, INC."))
        assertEquals("AT&T Trust II", Edgar13f.lesbar("AT&T TRUST II"))
        assertEquals("JPMorgan Chase & Co", Edgar13f.lesbar("JPMORGAN CHASE & CO"))
        assertEquals("FMR LLC", Edgar13f.lesbar("FMR LLC"))
        assertEquals("HSBC Holdings plc", Edgar13f.lesbar("HSBC HOLDINGS PLC"))
        assertEquals("Scion Asset Management, LLC", Edgar13f.lesbar("Scion Asset Management, LLC"))
    }

    @Test
    fun meldungenNeuesteZuerstOhneBerichtigung() {
        val j = """{"name":"X Fund","filings":{"recent":{"form":["13F-HR","10-K","13F-HR/A","13F-HR"],
          "accessionNumber":["a-1","b","c","d-2"],"reportDate":["2025-06-30","","2025-09-30","2025-09-30"],
          "filingDate":["2025-08-14","","2025-12-01","2025-11-03"]}}}"""
        val m = Edgar13f.meldungen(j)
        assertEquals(listOf("d-2", "a-1"), m.map { it.accession })
        assertEquals("X Fund", Edgar13f.name(j))
    }

    @Test
    fun tabellenDateiUndPositionen() {
        val idx = """{"directory":{"item":[{"name":"0001-index.html"},{"name":"infotable.xml"},{"name":"primary_doc.xml"}]}}"""
        assertEquals("infotable.xml", Edgar13f.tabellenDatei(idx))
        val xml = """<informationTable xmlns="x"><infoTable><nameOfIssuer>APPLE INC</nameOfIssuer><titleOfClass>COM</titleOfClass>
          <cusip>037833100</cusip><value>1000</value><shrsOrPrnAmt><sshPrnamt>10</sshPrnamt><sshPrnamtType>SH</sshPrnamtType></shrsOrPrnAmt></infoTable>
          <infoTable><nameOfIssuer>APPLE INC</nameOfIssuer><cusip>037833100</cusip><value>500</value>
          <shrsOrPrnAmt><sshPrnamt>5</sshPrnamt><sshPrnamtType>SH</sshPrnamtType></shrsOrPrnAmt></infoTable>
          <infoTable><nameOfIssuer>APPLE INC</nameOfIssuer><cusip>037833100</cusip><value>700</value>
          <shrsOrPrnAmt><sshPrnamt>7</sshPrnamt><sshPrnamtType>SH</sshPrnamtType></shrsOrPrnAmt><putCall>Put</putCall></infoTable>
          <infoTable><nameOfIssuer>ACME &amp; CO</nameOfIssuer><cusip>111111111</cusip><value>200</value>
          <shrsOrPrnAmt><sshPrnamt>2</sshPrnamt><sshPrnamtType>SH</sshPrnamtType></shrsOrPrnAmt></infoTable></informationTable>"""
        val p = Edgar13f.positionen(xml, "2025-09-30") { if (it == "037833100") "AAPL" else null }
        assertEquals(3, p.size)
        val aapl = p.first { it.art == "aktie" && it.cusip == "037833100" }
        assertEquals(1500.0, aapl.wert, 0.0)
        assertEquals(15.0, aapl.stueck, 0.0)
        assertEquals("AAPL", aapl.kuerzel)
        assertEquals("ACME & CO", p.first { it.cusip == "111111111" }.name)
        assertNull(p.first { it.cusip == "111111111" }.kuerzel)
        // Werte vor Ende September 2022 stehen in Tausend Dollar
        assertEquals(1_500_000.0, Edgar13f.positionen(xml, "2022-06-30") { null }.first { it.art == "aktie" && it.cusip == "037833100" }.wert, 0.0)
        val q = Edgar13f.quartal("2025-09-30", "2025-11-03", p)
        assertEquals(1700.0, q.summe, 0.0)
        val inv = Edgar13f.investor("X", "0000001234", listOf(q))
        assertEquals("x1234", inv.id)
        assertNotNull(Edgar13f.lesen(Edgar13f.schreiben(listOf(inv))).firstOrNull())
    }
}
