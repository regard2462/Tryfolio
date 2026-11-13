package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// IPO-Kalender von Finnhub und Einschaetzung (5.94), Aufbau wie die Antwort von /calendar/ipo, Werte erfunden.
class Fassung594IpoTest {

    private val kalender = """{"ipoCalendar":[
        {"date":"2026-10-30","exchange":"NASDAQ Global Select","name":"Bambusa Therapeutics, Inc.","numberOfShares":10555555,"price":"16.00","status":"priced","symbol":"BBTX","totalSharesValue":168888880},
        {"date":"2026-11-12","exchange":"NYSE","name":"Acme, PBC","numberOfShares":null,"price":"14.00-16.00","status":"expected","symbol":"ANT","totalSharesValue":1000000000},
        {"date":"2026-10-26","exchange":"NYSE","name":"Acme PBC","numberOfShares":null,"price":null,"status":"filed","symbol":"ANT","totalSharesValue":null},
        {"date":"2026-10-30","exchange":null,"name":"UCSB Acquisition Corp.","numberOfShares":6900000,"price":"10.00","status":"filed","symbol":"UCSBU","totalSharesValue":69000000},
        {"date":"2026-10-26","exchange":null,"name":"EvoNexus Group LTD","numberOfShares":null,"price":null,"status":"withdrawn","symbol":null,"totalSharesValue":null}
    ]}"""

    @Test
    fun kalenderLesen() {
        val l = IpoKalender.finnhubLesen(kalender)!!
        // Eingereicht und spaeter angekuendigt: ein Angebot mit dem neueren Stand
        assertEquals(4, l.size)
        val p = l.first { it.symbol == "BBTX" }
        assertEquals(IpoKalender.Status.PREIS, p.status)
        assertEquals(10_555_555L, p.aktien)
        assertEquals(168_888_880.0, p.volumen!!, 0.5)
        assertEquals(LocalDate.of(2026, 10, 30).toEpochDay(), p.tag)
        assertEquals(16.0, p.preisZahl!!, 1e-9)
        val ant = l.first { it.symbol == "ANT" }
        assertEquals(IpoKalender.Status.ANGEKUENDIGT, ant.status)
        assertEquals(15.0, ant.preisZahl!!, 1e-9)
        assertTrue(l.first { it.name.startsWith("UCSB") }.spac)
        assertFalse(p.spac)
        assertNull(l.first { it.status == IpoKalender.Status.ZURUECK }.symbol)
        assertEquals(emptyList<IpoKalender.Angebot>(), IpoKalender.finnhubLesen("""{"ipoCalendar":[]}"""))
        assertNull(IpoKalender.finnhubLesen("""{"error":"Invalid API key"}"""))
        assertTrue(IpoKalender.edgarSuche("Acme, PBC").contains("q=%22Acme%2C%20PBC%22"))
    }

    @Test
    fun einschaetzung() {
        val a = IpoKalender.Angebot("small bio", "SMOL", "Small Bio Therapeutics", null, "5.00", 2_000_000, 10_000_000.0, 1L, IpoKalender.Status.PREIS)
        val p = IpoBewertung.punkte(a).associateBy { it.merkmal }
        assertEquals(Fruehwarnung.Ampel.GELB, p.getValue(IpoBewertung.Merkmal.GROESSE).ampel)
        assertFalse(IpoBewertung.Merkmal.SPAC in p)
        assertEquals(IpoBewertung.Branche.BIOTECH, IpoBewertung.branche(a.name, null))
        assertEquals(IpoBewertung.Branche.KI, IpoBewertung.branche("Acme, PBC", "Our frontier AI, Atlas, is delivered through our models."))
        assertEquals(IpoBewertung.Branche.SPAC, IpoBewertung.branche("UCSB Acquisition Corp.", null))
        assertEquals(IpoBewertung.Branche.BANK, IpoBewertung.branche("GEORGIA BANKING CO INC", null))
    }

    @Test
    fun dokumente() {
        val j = """{"filings":{"recent":{"form":["S-1","4","S-1/A","424B4"],"filingDate":["2026-10-26","2026-10-27","2026-10-29","2026-11-01"],
            "accessionNumber":["0001-26-000001","0001-26-000002","0001-26-000003","0001-26-000004"],"primaryDocument":["s1.htm","f4.xml","s1a.htm",""]}}}"""
        val d = IpoKalender.dokumenteLesen("2130357", j)
        assertEquals(listOf("424B4", "S-1/A", "S-1"), d.map { it.formular })
        assertEquals("https://www.sec.gov/Archives/edgar/data/2130357/000126000003/s1a.htm", d[1].link)
        assertEquals("https://www.sec.gov/Archives/edgar/data/2130357/000126000004/", d[0].link)
    }
}
