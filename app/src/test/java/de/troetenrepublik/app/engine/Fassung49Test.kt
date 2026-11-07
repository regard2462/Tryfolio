package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Behoerden
import de.troetenrepublik.app.market.HausDaten
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.market.Vermoegen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TreeMap

// Kongress kauft, Rangliste, Vermoegen laut Jahresmeldung, Behoerdendaten und grosse Wetten.
class Fassung49Test {

    private val tag = KongressAuswertung.TAG

    private fun kurse(start: Double, proTag: Double, tage: Int = 800): KongressAuswertung.Kurse =
        KongressAuswertung.Kurse.aus(TreeMap<Long, Double>().apply { for (i in 0 until tage) put(19_000L + i, start * Math.pow(1 + proTag, i.toDouble())) })

    @Test
    fun deckelnVerteiltUeberhang() {
        val summen = (1..12).associate { "S$it" to 1.0 } + ("GROSS" to 20.0)
        val w = KongressAuswertung.deckeln(summen)
        assertEquals(1.0, w.sumOf { it.second }, 1e-9)
        assertTrue(w.all { it.second <= 0.1 + 1e-9 })
        // Mit weniger als zehn Werten laesst sich nicht deckeln: Anteile bleiben verhaeltnisgleich
        val wenige = KongressAuswertung.deckeln(mapOf("A" to 3.0, "B" to 1.0))
        assertEquals(0.75, wenige.first().second, 1e-9)
    }

    @Test
    fun strategieUndRangliste() {
        val start = 19_000L * tag
        val handel = listOf(
            KongressAuswertung.Handel("k:A", "A", 'H', "UP", true, start + 10 * tag, start + 20 * tag, 50_000.0),
            KongressAuswertung.Handel("k:A", "A", 'H', "UP", true, start + 40 * tag, start + 50 * tag, 50_000.0),
            KongressAuswertung.Handel("k:A", "A", 'H', "UP", true, start + 70 * tag, start + 80 * tag, 50_000.0),
            KongressAuswertung.Handel("n:B", "B", 'S', "DOWN", true, start + 10 * tag, start + 20 * tag, 50_000.0),
            KongressAuswertung.Handel("n:B", "B", 'S', "DOWN", true, start + 40 * tag, start + 50 * tag, 50_000.0),
            KongressAuswertung.Handel("n:B", "B", 'S', "DOWN", true, start + 70 * tag, start + 80 * tag, 50_000.0),
        )
        val k = mapOf("UP" to kurse(100.0, 0.002), "DOWN" to kurse(100.0, -0.002))
        val index = kurse(100.0, 0.0)
        val jetzt = start + 300 * tag
        val s = KongressAuswertung.strategie(handel, k, index, jetzt)
        assertNotNull(s)
        // Ab Tag 50 (erstes volles Fenster) gleiche Gewichte, danach nur noch Meldungen bis Tag 80
        assertEquals(100.0, s!!.verlauf.first().second, 1e-9)
        assertTrue(s.verlauf.size > 30)
        assertEquals(100.0, s.vergleich.last().second, 1e-6)
        // Rangliste: Kaeufe von UP liegen vorn, DOWN dahinter
        val r = KongressAuswertung.rangliste(handel, k, index, jetzt)
        assertEquals(listOf("k:A", "n:B"), r.map { it.person })
        assertTrue(r[0].vorsprung > 0 && r[1].vorsprung < 0)
        assertEquals(1.0, r[0].quote, 1e-9)
        // Unter drei Kaeufen keine Wertung
        assertTrue(KongressAuswertung.rangliste(handel.take(2), k, index, jetzt).isEmpty())
    }

    @Test
    fun vermoegenAusJahresmeldung() {
        val zeilen = listOf(
            "Filing Type:", "Annual Report", "S", "A", ":", "A", "Asset", "Value of Asset",
            "Apple Inc. (AAPL) [ST]", "SP", "$1,000,001 -", "$5,000,000", "Dividends", "$15,001 - $50,000",
            "D", ":", "Beschreibung mit $10,000 - $20,000 darin",
            "Haus [RP]", "Over $50,000,000", "Rent", "None",
            "Konto [BA]", "None",
            "S", "B", ":", "None disclosed.",
            "S", "D", ":", "Bank Mortgage", "$250,001 - $500,000",
            "S", "E", ":", "Position",
        )
        val e = Vermoegen.auswerten(Vermoegen.Kopf("10000001", "Muster", "Max", "CA11", 2025, "05/15/2026"), zeilen)!!
        assertEquals(3, e.z)
        assertEquals(1_000_001L + 50_000_001L, e.av)
        assertTrue(e.ao)
        assertEquals(250_001L, e.sv)
        assertEquals(500_000L, e.sb)
        assertEquals("Muster|Max|CA", e.person)
    }

    @Test
    fun hausDatenRundlauf() {
        val g = Kongress.Geschaeft("SP", "Apple Inc.", "AAPL", "ST", Kongress.Art.KAUF, 1_760_000_000_000L, 1_761_000_000_000L, 1_001, 15_000)
        val text = HausDaten.schreiben(
            HausDaten.Datei(1, "2026-10-21", listOf(HausDaten.M("20030001", "Muster", "Max", "CA11", "2025-10-30", 2025, g = listOf(HausDaten.g(g))))),
        )
        val s = HausDaten.lesen(text)!!
        assertEquals("Muster|Max|CA", s.meldungen.single().person)
        assertEquals("AAPL", s.geschaefte.getValue("20030001").single().kuerzel)
        assertNull(HausDaten.lesen("""{"format":2}"""))
    }

    @Test
    fun behoerdenLesen() {
        val l = Behoerden.lobbyingLesen(
            """{"format":1,"stand":"2026-10-21","firmen":{"KO":{"quartale":[{"j":2026,"q":3,"b":1000000,"f":2}],"themen":[{"c":"BEV","n":21}],"gesetze":[{"c":"S. 351","n":4}]}}}""",
        )!!
        assertEquals(1_000_000L, l.firmen.getValue("KO").letztesJahr)
        assertEquals("https://www.congress.gov/bill/119th-congress/senate-bill/351", Behoerden.gesetzAdresse("S. 351", 2026))
        assertEquals("https://www.congress.gov/bill/119th-congress/house-bill/4016", Behoerden.gesetzAdresse("H.R. 4016", 2025))

        val empf = Behoerden.auftraegeEmpfaengerLesen("""{"results":[{"amount":3.4E10,"name":"LOCKHEED MARTIN CORPORATION"},{"amount":2.0E9,"name":"LOCKHEED MARTIN CORP"},{"amount":5.0E6,"name":"LOCKHEED MARTIN SPACE OPERATIONS"}]}""")
        assertEquals(3.6E10, empf.filter { Behoerden.passtEmpfaenger(it.first, "LOCKHEED MARTIN") }.sumOf { it.second }, 1.0)
        assertTrue(!Behoerden.passtEmpfaenger("MAYER BROS. APPLE PRODUCTS INC.", "APPLE"))
        assertTrue(!Behoerden.passtEmpfaenger("APPLEDORE MARINE ENGINEERING", "APPLE"))
        assertTrue(Behoerden.passtEmpfaenger("Apple Inc.", "APPLE"))
        assertTrue(!Behoerden.passtEmpfaenger("COCA-COLA BOTTLERS JAPAN INC.", "COCA-COLA"))

        val tage = Behoerden.leerVolumenLesen(
            """[{"tradeReportDate":"2026-10-14","shortParQuantity":100.0,"totalParQuantity":200.0},{"tradeReportDate":"2026-10-14","shortParQuantity":50.0,"totalParQuantity":100.0},{"tradeReportDate":"2026-10-15","shortParQuantity":10,"totalParQuantity":40}]""",
        )
        assertEquals(2, tage.size)
        assertEquals(0.5, tage[0].anteil, 1e-9)
        val bestand = Behoerden.leerBestandLesen("""[{"settlementDate":"2026-09-30","currentShortPositionQuantity":120763539,"previousShortPositionQuantity":128753092,"daysToCoverQuantity":3.03},{"settlementDate":"2026-06-15","currentShortPositionQuantity":1,"daysToCoverQuantity":999.99}]""")
        assertEquals("2026-09-30", bestand.last().stichtag)
        assertNull(bestand.first().tageEindecken)

        val pac = Behoerden.pacLesen(
            """{"results":[{"committee_id":"C1","name":"OTHER PAC","affiliated_committee_name":"OTHER"},{"committee_id":"C00303024","name":"LOCKHEED MARTIN CORPORATION EMPLOYEES' POLITICAL ACTION COMMITTEE","affiliated_committee_name":"LOCKHEED MARTIN CORPORATION"}]}""",
        ) { Behoerden.stamm(it).startsWith("LOCKHEED MARTIN") }
        assertEquals("C00303024", pac?.id)
        assertEquals("LOCKHEED MARTIN", Behoerden.stamm("Lockheed Martin Corporation"))
        assertEquals("COCA-COLA", Behoerden.stamm("The Coca-Cola Company"))

        val tx = Behoerden.texasLesen("""[{"notice_date":"2026-06-23T00:00:00.000","job_site_name":"JPMorgan Chase & Co.","total_layoff_number":"244","layoff_date":"2026-08-21T00:00:00.000","city_name":"Plano"}]""")
        assertEquals(244, tx.single().n)
        assertEquals("2026-06-23", tx.single().d)
        assertNull(Behoerden.texasAdresse("A'"))
    }

    @Test
    fun grosseWetten() {
        val w = Prognose.grosseWettenLesen(
            """{"data":[{"proxy_wallet":"0xabc","side":"SELL","size":14000.0,"price":0.996,"timestamp":1792608737,"title":"Fed?","outcome":"No","event_slug":"fed-decision","name":"x"}],"pagination":{}}""",
        )
        assertEquals(1, w.size)
        assertEquals(13_944.0, w[0].betrag, 1e-6)
        assertEquals("fed-decision", w[0].ereignis)
        assertTrue(!w[0].kauf)
        assertTrue(Prognose.grosseWettenAdresse(1000, "606422").endsWith("&event_id=606422"))
        assertTrue(!Prognose.grosseWettenAdresse(1000, "x;1").contains("event_id"))
    }
}
