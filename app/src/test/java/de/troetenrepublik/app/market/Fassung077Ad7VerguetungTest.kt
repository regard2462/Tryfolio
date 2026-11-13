package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Verguetung der Fuehrung aus den ecd-Werten der Proxy-Meldung (Antwortausschnitte data.sec.gov, companyconcept).
class Fassung077Ad7VerguetungTest {
    private val MSFT_CEO = """{"cik":789019,"taxonomy":"ecd","tag":"PeoTotalCompAmt","label":"","description":"","entityName":"MICROSOFT CORP","units":{"USD":[{"start":"2021-07-01","end":"2022-06-30","val":54946310,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2022-07-01","end":"2023-06-30","val":48512537,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2023-07-01","end":"2024-06-30","val":79106183,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2024-07-01","end":"2025-06-30","val":96496790,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2025-07-01","end":"2026-06-30","val":90628977,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"}]}}"""
    private val MSFT_ZUG = """{"cik":789019,"taxonomy":"ecd","tag":"PeoActuallyPaidCompAmt","label":"","description":"","entityName":"MICROSOFT CORP","units":{"USD":[{"start":"2021-07-01","end":"2022-06-30","val":56936384,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2022-07-01","end":"2023-06-30","val":93155708,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2023-07-01","end":"2024-06-30","val":171304590,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2024-07-01","end":"2025-06-30","val":131138799,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2025-07-01","end":"2026-06-30","val":30083543,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"}]}}"""
    private val MSFT_UEB = """{"cik":789019,"taxonomy":"ecd","tag":"NonPeoNeoAvgTotalCompAmt","label":"","description":"","entityName":"MICROSOFT CORP","units":{"USD":[{"start":"2021-07-01","end":"2022-06-30","val":20074444,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2022-07-01","end":"2023-06-30","val":16009805,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2023-07-01","end":"2024-06-30","val":21081007,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2024-07-01","end":"2025-06-30","val":24456053,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2025-07-01","end":"2026-06-30","val":28986966,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"}]}}"""
    private val MSFT_TSR = """{"cik":789019,"taxonomy":"ecd","tag":"TotalShareholderRtnAmt","label":"","description":"","entityName":"MICROSOFT CORP","units":{"USD":[{"start":"2021-07-01","end":"2022-06-30","val":96,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2022-07-01","end":"2023-06-30","val":128,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2023-07-01","end":"2024-06-30","val":169,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2024-07-01","end":"2025-06-30","val":190,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"},{"start":"2025-07-01","end":"2026-06-30","val":143,"accn":"0001193125-26-427849","form":"DEF 14A","filed":"2026-10-21"}]}}"""
    private val TSLA_CEO = """{"cik":1318605,"taxonomy":"ecd","tag":"PeoTotalCompAmt","label":"","description":"","entityName":"Tesla, Inc.","units":{"USD":[{"start":"2025-01-01","end":"2025-12-31","val":158359000000,"accn":"0001104659-26-116700","fy":null,"fp":null,"form":"DEF 14A","filed":"2026-10-19","frame":"CY2025"}]}}"""

    private fun msft() = Verguetung.auswerten(
        Verguetung.reihe(MSFT_CEO), Verguetung.reihe(MSFT_ZUG), Verguetung.reihe(MSFT_UEB), Verguetung.reihe(MSFT_TSR),
    )

    @Test fun adresseMitZehnStellen() {
        assertEquals("https://data.sec.gov/api/xbrl/companyconcept/CIK0000789019/ecd/PeoTotalCompAmt.json", Verguetung.adresse("789019", Verguetung.CEO))
        assertEquals("https://data.sec.gov/api/xbrl/companyconcept/CIK0000789019/ecd/PeoTotalCompAmt.json", Verguetung.adresse("0000789019", Verguetung.CEO))
    }

    @Test fun reiheLiestAlleJahre() {
        val r = Verguetung.reihe(MSFT_CEO)
        assertEquals(5, r.size)
        assertEquals("2022-06-30", r.first().ende)
        assertEquals(54946310.0, r.first().wert, 0.0)
        assertEquals("DEF 14A", r.first().form)
    }

    @Test fun kaputteAntwortIstLeer() {
        assertTrue(Verguetung.reihe("").isEmpty())
        assertTrue(Verguetung.reihe("{\"units\":{}}").isEmpty())
        assertTrue(Verguetung.reihe("<html>").isEmpty())
    }

    @Test fun fuenfJahreMitAktuellemJahrZuletzt() {
        val e = msft()!!
        assertEquals(listOf(2022, 2023, 2024, 2025, 2026), e.jahre.map { it.jahr })
        val j = e.aktuell
        assertEquals(90628977.0, j.ceo!!, 0.0)
        assertEquals(30083543.0, j.zugeflossen!!, 0.0)
        assertEquals(28986966.0, j.uebrige!!, 0.0)
    }

    // 100 $ zu Beginn des ersten Jahres: 96, 128, 169, 190, 143
    @Test fun renditeJeJahrAusDerKumulierten() {
        val r = msft()!!.jahre.map { it.rendite!! }
        assertEquals(-4.0, r[0], 1e-9)
        assertEquals((128.0 / 96 - 1) * 100, r[1], 1e-9)
        assertEquals((169.0 / 128 - 1) * 100, r[2], 1e-9)
        assertEquals((190.0 / 169 - 1) * 100, r[3], 1e-9)
        assertEquals((143.0 / 190 - 1) * 100, r[4], 1e-9)
    }

    @Test fun hoechstensDreiJahreWennGewuenscht() {
        val e = Verguetung.auswerten(Verguetung.reihe(MSFT_CEO), emptyList(), emptyList(), emptyList(), hoechstens = 3)!!
        assertEquals(listOf(2024, 2025, 2026), e.jahre.map { it.jahr })
        assertNull(e.aktuell.zugeflossen)
        assertNull(e.aktuell.rendite)
    }

    @Test fun einJahrOhneVerlaufIstMoeglich() {
        val e = Verguetung.auswerten(Verguetung.reihe(TSLA_CEO), emptyList(), emptyList(), emptyList())!!
        assertEquals(1, e.jahre.size)
        assertEquals(158359000000.0, e.aktuell.ceo!!, 0.0)
    }

    @Test fun ohneVorstandschefKeinErgebnis() {
        assertNull(Verguetung.auswerten(emptyList(), Verguetung.reihe(MSFT_ZUG), Verguetung.reihe(MSFT_UEB), Verguetung.reihe(MSFT_TSR)))
    }

    // Dieselbe Zahl steht in mehreren Meldungen; es gilt die endgueltige und juengste, Teiljahre fallen weg.
    @Test fun neuesteEndgueltigeMeldungGewinntUndTeiljahreFallenWeg() {
        val p = { beginn: String, wert: Double, form: String, eingang: String ->
            Verguetung.Punkt(beginn, "2025-12-31", wert, "x", form, eingang)
        }
        val reihe = Verguetung.jahresreihe(
            listOf(
                p("2025-01-01", 10.0, "DEF 14A", "2026-03-01"),
                p("2025-01-01", 11.0, "DEF 14A", "2026-04-01"),
                p("2025-01-01", 12.0, "PRE 14A", "2026-05-01"),
                p("2025-10-01", 99.0, "DEF 14A", "2026-06-01"),
            ),
        )
        assertEquals(mapOf("2025-12-31" to 11.0), reihe)
    }

    @Test fun renditenRechnenNurInnerhalbEinerMeldung() {
        val a = { ende: String, wert: Double, accn: String, eingang: String ->
            Verguetung.Punkt(ende.take(4) + "-01-01", ende, wert, accn, "DEF 14A", eingang)
        }
        val r = Verguetung.renditen(
            listOf(
                a("2023-12-31", 120.0, "alt", "2025-03-01"), a("2024-12-31", 150.0, "alt", "2025-03-01"),
                a("2024-12-31", 110.0, "neu", "2026-03-01"), a("2025-12-31", 121.0, "neu", "2026-03-01"),
            ),
        )
        assertEquals(setOf("2024-12-31", "2025-12-31"), r.keys)
        assertEquals(10.0, r["2024-12-31"]!!, 1e-9)
        assertEquals(10.0, r["2025-12-31"]!!, 1e-9)
    }

    @Test fun abrufListeNichtLeer() {
        assertNotNull(msft())
    }
}
