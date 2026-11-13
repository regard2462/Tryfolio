package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.GewinnSchnitt.Lage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.77] KGV auf den Durchschnittsgewinn. Jahreswerte aus Abrufen der Yahoo-Zeitreihen (annualDilutedEPS,
// annualOrdinarySharesNumber) mit Kursen und KGV vom selben Tag, gekuerzt.
class Fassung077Ad3GewinnSchnittTest {

    private fun jahr(ende: String, eps: Double?, aktien: Double?, netto: Double? = null) =
        Qualitaet.Jahr(ende = ende, waehrung = "USD", nettogewinn = netto, aktien = aktien, gewinnJeAktie = eps)

    // SMCI: Gewinnspitze im letzten Jahr, KGV 6,5 bei Kurs 30,95
    private val smci = listOf(
        jahr("2023-06-30", 1.143, 529_013_580.0), jahr("2024-06-30", 2.009, 588_087_410.0),
        jahr("2025-06-30", 1.68, 594_136_852.0), jahr("2026-06-30", 3.26, 656_882_499.0),
    )

    // KO: stabiles Geschaeft, KGV 28,6 bei Kurs 96,79
    private val ko = listOf(
        jahr("2022-12-31", 2.19, 4_328_000_000.0), jahr("2023-12-31", 2.47, 4_308_000_000.0),
        jahr("2024-12-31", 2.46, 4_302_000_000.0), jahr("2025-12-31", 3.04, 4_301_608_845.0),
    )

    // UBER: Verlustjahr 2022, KGV 41,5 bei Kurs 70,61
    private val uber = listOf(
        jahr("2022-12-31", -4.65, 2_005_486_000.0), jahr("2023-12-31", 0.87, 2_071_144_000.0),
        jahr("2024-12-31", 4.56, 2_107_953_000.0), jahr("2025-12-31", 4.73, 2_067_905_000.0),
    )

    @Test fun zyklikerMitGewinnspitze() {
        val e = GewinnSchnitt.berechne(smci, emptyList(), 659_227_367.0, 30.9539)!!
        assertEquals(4, e.jahre)
        assertEquals(2.0229, e.schnitt, 1e-4)
        assertEquals(15.30, e.kgv, 0.01)
        assertEquals(2026, e.letztesJahr)
        assertEquals(61.2, e.abweichung, 0.1)
        assertEquals(Lage.UEBER, e.lage)
    }

    @Test fun stabilerWertOhneHinweis() {
        val e = GewinnSchnitt.berechne(ko, emptyList(), 4_301_902_335.0, 96.785)!!
        assertEquals(2.54, e.schnitt, 1e-9)
        assertEquals(38.1, e.kgv, 0.05)
        assertEquals(19.7, e.abweichung, 0.1)
        assertEquals(Lage.GLEICH, e.lage)
    }

    @Test fun verlustjahrGehtInDenSchnittEin() {
        val e = GewinnSchnitt.berechne(uber, emptyList(), 2_041_898_070.0, 70.61)!!
        assertEquals(1.3775, e.schnitt, 1e-9)
        assertEquals(51.26, e.kgv, 0.01)
        assertEquals(Lage.UEBER, e.lage)
    }

    @Test fun letztesJahrWeitUnterSchnittWirktDasKgvHoch() {
        val j = listOf(jahr("2022-12-31", 5.0, 1e9), jahr("2023-12-31", 6.0, 1e9), jahr("2024-12-31", 5.5, 1e9), jahr("2025-12-31", 1.5, 1e9))
        val e = GewinnSchnitt.berechne(j, emptyList(), 1e9, 30.0)!!
        assertEquals(Lage.UNTER, e.lage)
        assertEquals(-66.7, e.abweichung, 0.1)
    }

    @Test fun letztesJahrMitVerlustGibtKeinenHinweis() {
        val j = listOf(jahr("2022-12-31", 5.0, 1e9), jahr("2023-12-31", 6.0, 1e9), jahr("2024-12-31", 5.5, 1e9), jahr("2025-12-31", -2.0, 1e9))
        val e = GewinnSchnitt.berechne(j, emptyList(), 1e9, 30.0)!!
        assertEquals(Lage.GLEICH, e.lage)
    }

    @Test fun schwelleLiegtBeiFuenfzigProzent() {
        fun lage(letzter: Double) = GewinnSchnitt.berechne(
            listOf(jahr("2022-12-31", 2.0, 1e9), jahr("2023-12-31", 2.0, 1e9), jahr("2024-12-31", 2.0, 1e9), jahr("2025-12-31", letzter, 1e9)),
            emptyList(), 1e9, 20.0,
        )!!.lage
        // Mit letztem Jahr 3,0 liegt der Schnitt bei 2,25 (33 % ueber), mit 5,0 bei 2,75 (82 % ueber)
        assertEquals(Lage.GLEICH, lage(3.0))
        assertEquals(Lage.UEBER, lage(5.0))
        // Mit 1,0 liegt der Schnitt bei 1,75 (43 % unter), mit 0,2 bei 1,55 (87 % unter)
        assertEquals(Lage.GLEICH, lage(1.0))
        assertEquals(Lage.UNTER, lage(0.2))
    }

    @Test fun wenigerAlsDreiJahreOderNegativerSchnittGibtNichts() {
        assertNull(GewinnSchnitt.berechne(smci.takeLast(2), emptyList(), 6.5e8, 30.0))
        val verluste = listOf(jahr("2023-12-31", -1.0, 1e9), jahr("2024-12-31", -2.0, 1e9), jahr("2025-12-31", 0.5, 1e9))
        assertNull(GewinnSchnitt.berechne(verluste, emptyList(), 1e9, 30.0))
        assertNull(GewinnSchnitt.berechne(smci, emptyList(), 6.5e8, 0.0))
    }

    @Test fun hoechstensFuenfJahreUndLueckeBrichtDieReiheAb() {
        val sieben = (0..6).map { jahr("${2019 + it}-12-31", (it + 1).toDouble(), 1e9) }
        val e = GewinnSchnitt.berechne(sieben, emptyList(), 1e9, 10.0)!!
        assertEquals(5, e.jahre)
        assertEquals(5.0, e.schnitt, 1e-9)
        // Fehlt 2022, zaehlen nur 2023 bis 2025 (drei Jahre)
        val luecke = listOf(
            jahr("2020-12-31", 9.0, 1e9), jahr("2021-12-31", 9.0, 1e9), jahr("2023-12-31", 1.0, 1e9),
            jahr("2024-12-31", 2.0, 1e9), jahr("2025-12-31", 3.0, 1e9),
        )
        val l = GewinnSchnitt.berechne(luecke, emptyList(), 1e9, 10.0)!!
        assertEquals(3, l.jahre)
        assertEquals(2.0, l.schnitt, 1e-9)
    }

    @Test fun ohneGewinnJeAktieRechnetNettogewinnDurchAktien() {
        val j = listOf(
            jahr("2023-12-31", null, 1e9, netto = 1e9), jahr("2024-12-31", null, 1e9, netto = 2e9), jahr("2025-12-31", null, 2e9, netto = 3e9),
        )
        val e = GewinnSchnitt.berechne(j, emptyList(), 2e9, 15.0)!!
        assertEquals((1.0 + 2.0 + 1.5) / 3, e.schnitt, 1e-9)
    }

    // SMCI teilte am 01.10.2024 zehn zu eins. Meldet die Quelle die Jahre davor noch in alten Aktien (Gewinn 11,43 bei 52,9 Mio.
    // Aktien statt 1,143 bei 529 Mio.), rechnet der Splitfaktor sie um.
    @Test fun splitWirdBeiAlterZaehlungBereinigt() {
        val alt = listOf(
            jahr("2023-06-30", 11.43, 52_901_358.0), jahr("2024-06-30", 20.09, 58_808_741.0),
            jahr("2025-06-30", 1.68, 594_136_852.0), jahr("2026-06-30", 3.26, 656_882_499.0),
        )
        val split = Splits.Split(1_727_740_800_000L, 10.0, 1.0)
        assertTrue(GewinnSchnitt.splitVerdacht(alt, 659_227_367.0))
        val r = GewinnSchnitt.reihe(alt, listOf(split), 659_227_367.0)
        listOf(1.143, 2.009, 1.68, 3.26).forEachIndexed { i, w -> assertEquals(w, r[i].wert, 1e-9) }
        assertEquals(2.0229, GewinnSchnitt.berechne(alt, listOf(split), 659_227_367.0, 30.9539)!!.schnitt, 1e-4)
    }

    @Test fun bereitsUmgerechneteZahlenBleibenBeiSplitliste() {
        val split = Splits.Split(1_727_740_800_000L, 10.0, 1.0)
        assertEquals(listOf(1.143, 2.009, 1.68, 3.26), GewinnSchnitt.reihe(smci, listOf(split), 659_227_367.0).map { it.wert })
        assertFalse(GewinnSchnitt.splitVerdacht(smci, 659_227_367.0))
    }

    @Test fun splitNachDemLetztenBerichtWirdMitDerHeutigenAktienzahlErkannt() {
        // Letzter Bericht noch in alten Aktien, Split zwei zu eins danach: heute doppelt so viele Aktien
        val j = listOf(jahr("2023-12-31", 4.0, 5e8), jahr("2024-12-31", 5.0, 5e8), jahr("2025-12-31", 6.0, 5e8))
        val split = Splits.Split(1_770_000_000_000L, 2.0, 1.0)
        assertTrue(GewinnSchnitt.splitVerdacht(j, 1e9))
        assertEquals(listOf(2.0, 2.5, 3.0), GewinnSchnitt.reihe(j, listOf(split), 1e9).map { it.wert })
    }

    @Test fun jahresreiheAusAbrufTextWirdGelesen() {
        val text = """{"timeseries":{"result":[{"meta":{"symbol":["SMCI"],"type":["annualDilutedEPS"]},"timestamp":[1688083200,1719705600],"annualDilutedEPS":[{"dataId":29009,"asOfDate":"2023-06-30","periodType":"12M","currencyCode":"USD","reportedValue":{"raw":1.143,"fmt":"1.14"}},{"dataId":29009,"asOfDate":"2024-06-30","periodType":"12M","currencyCode":"USD","reportedValue":{"raw":2.009,"fmt":"2.01"}}]},{"meta":{"symbol":["SMCI"],"type":["annualOrdinarySharesNumber"]},"timestamp":[1688083200,1719705600],"annualOrdinarySharesNumber":[{"dataId":23393,"asOfDate":"2023-06-30","periodType":"12M","currencyCode":"USD","reportedValue":{"raw":5.2901358E8,"fmt":"529.01M"}},{"dataId":23393,"asOfDate":"2024-06-30","periodType":"12M","currencyCode":"USD","reportedValue":{"raw":5.8808741E8,"fmt":"588.09M"}}]}]}}"""
        val j = Qualitaet.lesen(text)
        assertEquals(2, j.size)
        assertEquals(1.143, j[0].gewinnJeAktie!!, 1e-9)
        assertEquals(2.009, j[1].gewinnJeAktie!!, 1e-9)
        assertTrue("annualDilutedEPS" in Qualitaet.FELDER)
        assertNotNull(GewinnSchnitt.gewinnJeAktie(j[1]))
    }

    // Stetig heisst: jedes Jahr ueber dem Vorjahr. SMCI fiel 2025 zurueck, eine steigende Reihe gilt als stetig.
    @Test fun stetigesWachstumIstKeineSpitze() {
        assertFalse(GewinnSchnitt.berechne(smci, emptyList(), 659_227_367.0, 30.9539)!!.stetig)
        val steigend = listOf(
            jahr("2022-12-31", 1.0, 1_000_000.0), jahr("2023-12-31", 1.6, 1_000_000.0),
            jahr("2024-12-31", 2.6, 1_000_000.0), jahr("2025-12-31", 4.4, 1_000_000.0),
        )
        val e = GewinnSchnitt.berechne(steigend, emptyList(), 1_000_000.0, 100.0)!!
        assertTrue(e.stetig)
        assertEquals(Lage.UEBER, e.lage)
    }
}
