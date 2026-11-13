package de.troetenrepublik.app.market

import java.net.URLDecoder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// OSHA-Daten des Arbeitsministeriums: Suchmuster, Adressen, Lesen der Antworten im Aufbau {"data":[...]} (Datensaetze
// OSHA/inspection und OSHA/violation, selbst formulierte Auszuege) und Zusammenfassung.
class FirmaArbeitsschutzTest {

    @Test fun musterNurFuerEindeutigeNamen() {
        assertEquals("EXXON%MOBIL%", Arbeitsschutz.muster("Exxon Mobil Corp"))
        assertEquals("WALMART%", Arbeitsschutz.muster("Walmart Inc."))
        assertEquals("HOME%DEPOT%", Arbeitsschutz.muster("The Home Depot, Inc."))
        assertNull(Arbeitsschutz.muster("Target Corporation"))
        assertNull(Arbeitsschutz.muster("Ace Inc."))
    }

    @Test fun adresseMitFilterNachHandbuch() {
        val u = Arbeitsschutz.inspektionAdresse("EXXON%MOBIL%", LocalDate.of(2021, 11, 14), "abc/def+1")
        assertTrue(u.startsWith("https://apiprod.dol.gov/v4/get/OSHA/inspection/json?"))
        assertTrue(u.contains("&X-API-KEY=abc%2Fdef%2B1"))
        val filter = URLDecoder.decode(u.substringAfter("filter_object=").substringBefore("&"), "UTF-8")
        assertEquals(
            "{\"and\":[{\"field\":\"estab_name\",\"operator\":\"like\",\"value\":\"EXXON%MOBIL%\"}," +
                "{\"field\":\"open_date\",\"operator\":\"gt\",\"value\":\"2021-11-14\"}]}",
            filter,
        )
        val v = Arbeitsschutz.verstossAdresse(listOf("306953084", "12x34"), "k")
        assertTrue(v.contains("/violation/json?"))
        assertEquals(
            "{\"field\":\"activity_nr\",\"operator\":\"in\",\"value\":[\"306953084\",\"1234\"]}",
            URLDecoder.decode(v.substringAfter("filter_object=").substringBefore("&"), "UTF-8"),
        )
    }

    private val inspektionen = """
        {"data":[
         {"activity_nr":1700001,"estab_name":"EXXONMOBIL BAYTOWN REFINERY","site_city":"BAYTOWN","site_state":"TX","open_date":"2024-03-04T00:00:00"},
         {"activity_nr":"1700002.0","estab_name":"EXXON MOBIL CHEMICAL PLANT","site_city":"BEAUMONT","site_state":"TX","open_date":"2022-06-01T00:00:00"},
         {"activity_nr":1700003,"estab_name":"EXXONMOBILITY SERVICES","site_city":"X","site_state":"TX","open_date":"2023-01-01T00:00:00"},
         {"activity_nr":1700004,"estab_name":"EXXON MOBIL OLD SITE","site_city":"X","site_state":"TX","open_date":"2018-01-01T00:00:00"},
         {"activity_nr":1700005,"estab_name":null,"site_city":"X","site_state":"TX","open_date":"2024-01-01T00:00:00"},
         {"activity_nr":1700001,"estab_name":"EXXONMOBIL BAYTOWN REFINERY","site_city":"BAYTOWN","site_state":"TX","open_date":"2024-03-04T00:00:00"}
        ]}"""

    @Test fun inspektionenLesenUndZuordnen() {
        val roh = Arbeitsschutz.inspektionenLesen(inspektionen)
        assertEquals(5, roh.size)
        val passend = Arbeitsschutz.passende("Exxon Mobil Corp", roh, LocalDate.of(2021, 11, 14))
        assertEquals(listOf("1700001", "1700002"), passend.map { it.nummer })
        assertEquals("2024-03-04", passend[0].datum)
        assertEquals("TX", passend[0].staat)
    }

    private val verstoesse = """
        {"data":[
         {"activity_nr":1700001,"viol_type":"S","standard":"19100119 D01","current_penalty":"4500.0","delete_flag":null,"issuance_date":"2024-04-01T00:00:00"},
         {"activity_nr":1700001,"viol_type":"W","standard":"19101200 E04","current_penalty":15000.0,"delete_flag":null,"issuance_date":"2024-04-01T00:00:00"},
         {"activity_nr":1700001,"viol_type":"O","standard":"19101030 G02","current_penalty":"0","delete_flag":null,"issuance_date":"2024-04-01T00:00:00"},
         {"activity_nr":1700001,"viol_type":"S","standard":"19100147 C04","current_penalty":"9999.0","delete_flag":"X","issuance_date":"2024-04-01T00:00:00"},
         {"activity_nr":1700002,"viol_type":"O","standard":"5A0001","current_penalty":"1,250.50","delete_flag":null,"issuance_date":"2022-07-01T00:00:00"},
         {"activity_nr":1700009,"viol_type":"R","standard":"19260451 B01","current_penalty":"700","delete_flag":null,"issuance_date":"2023-01-01T00:00:00"}
        ]}"""

    @Test fun verstoesseNurMitStrafeUndOhneGeloeschte() {
        val v = Arbeitsschutz.verstoesseLesen(verstoesse)
        assertEquals(4, v.size)
        assertEquals(listOf(4500.0, 15000.0, 1250.5, 700.0), v.map { it.strafe })
    }

    @Test fun normAusStandard() {
        assertEquals("29 CFR 1910.1030", Arbeitsschutz.norm("19101030 G02   IIA"))
        assertEquals("29 CFR 1926.451", Arbeitsschutz.norm("19260451 B01"))
        assertEquals("", Arbeitsschutz.norm("5A0001"))
    }

    @Test fun zusammenfassung() {
        val passend = Arbeitsschutz.passende("Exxon Mobil Corp", Arbeitsschutz.inspektionenLesen(inspektionen), LocalDate.of(2021, 11, 14))
        val e = Arbeitsschutz.zusammenfassen(passend, Arbeitsschutz.verstoesseLesen(verstoesse), false)
        assertNotNull(e)
        e!!
        assertEquals(19500.0 + 1250.5, e.summe, 0.001)
        assertEquals(2, e.inspektionen)
        assertFalse(e.mindestens)
        // groesster Fall zuerst, Art vom gewichtigsten Verstoss (vorsaetzlich), Vorschrift von ihm
        val f = e.faelle[0]
        assertEquals("1700001", f.nummer)
        assertEquals(19500.0, f.strafe, 0.001)
        assertEquals(2, f.verstoesse)
        assertEquals("W", f.art)
        assertEquals("29 CFR 1910.1200", f.norm)
        assertTrue(f.link.endsWith("id=1700001"))
        assertEquals("O", e.faelle[1].art)
    }

    @Test fun ohneStrafenKeinErgebnis() {
        val passend = Arbeitsschutz.passende("Exxon Mobil Corp", Arbeitsschutz.inspektionenLesen(inspektionen), LocalDate.of(2021, 11, 14))
        assertNull(Arbeitsschutz.zusammenfassen(passend, emptyList(), false))
        assertTrue(Arbeitsschutz.inspektionenLesen("kein json").isEmpty())
        assertTrue(Arbeitsschutz.verstoesseLesen("{\"data\":42}").isEmpty())
    }

    @Test fun betriebsnamenLesbar() {
        assertEquals("Exxonmobil Baytown Refinery", Arbeitsschutz.lesbar("EXXONMOBIL BAYTOWN REFINERY"))
        assertEquals("Wal-Mart Stores Inc", Arbeitsschutz.lesbar("WAL-MART STORES INC"))
        assertEquals("Store 1234", Arbeitsschutz.lesbar("STORE 1234"))
    }

    @Test fun betriebPasstNurEindeutig() {
        assertTrue(Verfahren.betriebPasst("Apple Inc.", "APPLE INC"))
        assertTrue(Verfahren.betriebPasst("Walmart Inc.", "WAL-MART STORES"))
        assertFalse(Verfahren.betriebPasst("Apple Inc.", "APPLE VALLEY FOODS"))
        assertFalse(Verfahren.betriebPasst("Target Corp", "TARGET STORE T-0123"))
    }
}
