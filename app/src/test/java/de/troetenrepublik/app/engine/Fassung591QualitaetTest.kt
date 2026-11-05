package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Qualitaet.Punkt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.91: Piotroski-F-Score und Altman-Z-Score aus Jahresabschluessen.
class Fassung591QualitaetTest {

    // Antwort im Aufbau von ws/fundamentals-timeseries mit zwei Jahren (Werte in Milliarden)
    private fun eintrag(typ: String, werte: List<Pair<String, Double?>>): String =
        "{\"meta\":{\"symbol\":[\"TEST\"],\"type\":[\"$typ\"]},\"timestamp\":[1,2],\"$typ\":[" +
            werte.joinToString(",") { (d, v) ->
                if (v == null) "null" else "{\"asOfDate\":\"$d\",\"periodType\":\"12M\",\"currencyCode\":\"USD\",\"reportedValue\":{\"raw\":${v * 1e9},\"fmt\":\"x\"}}"
            } + "]}"

    private val antwort = "{\"timeseries\":{\"result\":[" + listOf(
        eintrag("annualNetIncome", listOf("2024-12-31" to 8.0, "2025-12-31" to 10.0)),
        eintrag("annualTotalAssets", listOf("2024-12-31" to 100.0, "2025-12-31" to 104.0)),
        eintrag("annualOperatingCashFlow", listOf("2024-12-31" to 9.0, "2025-12-31" to 14.0)),
        eintrag("annualLongTermDebt", listOf("2024-12-31" to 30.0, "2025-12-31" to 26.0)),
        eintrag("annualCurrentAssets", listOf("2024-12-31" to 40.0, "2025-12-31" to 45.0)),
        eintrag("annualCurrentLiabilities", listOf("2024-12-31" to 30.0, "2025-12-31" to 30.0)),
        eintrag("annualOrdinarySharesNumber", listOf("2024-12-31" to 1.0, "2025-12-31" to 0.98)),
        eintrag("annualGrossProfit", listOf("2024-12-31" to 40.0, "2025-12-31" to 45.0)),
        eintrag("annualTotalRevenue", listOf("2024-12-31" to 100.0, "2025-12-31" to 110.0)),
        eintrag("annualRetainedEarnings", listOf("2024-12-31" to 20.0, "2025-12-31" to 25.0)),
        eintrag("annualEBIT", listOf("2024-12-31" to 12.0, "2025-12-31" to 15.0)),
        eintrag("annualTotalLiabilitiesNetMinorityInterest", listOf("2024-12-31" to 60.0, "2025-12-31" to 58.0)),
        // Ein fehlender Eintrag (null) darf nicht stoeren
        eintrag("annualWorkingCapital", listOf("2024-12-31" to null, "2025-12-31" to 15.0)),
    ).joinToString(",") + "],\"error\":null}}"

    @Test fun lesenUndPiotroski() {
        val jahre = Qualitaet.lesen(antwort)
        assertEquals(listOf("2024-12-31", "2025-12-31"), jahre.map { it.ende })
        assertEquals("USD", jahre.last().waehrung)
        assertEquals(10e9, jahre.last().nettogewinn!!, 1.0)
        assertNull(jahre.first().betriebskapital)
        val p = Qualitaet.piotroski(jahre)!!
        assertEquals(9, p.geprueft)
        // Alle neun erfuellt: Gewinn, Cashflow, Rendite steigt (10/100 gegen 8/100), Cashflow ueber Gewinn,
        // weniger Schulden, mehr Liquiditaet, weniger Aktien, hoehere Marge, hoeherer Umschlag (110/100 gegen 100/100)
        assertEquals(9, p.score)
        assertEquals(Qualitaet.Stufe.STARK, Qualitaet.stufe(p))
        assertEquals(0.10, p.pruefungen.first { it.punkt == Punkt.RENDITE }.jetzt!!, 1e-12)
    }

    @Test fun schwacheWerteUndFehlendeFelder() {
        val v = Qualitaet.Jahr("2024", nettogewinn = 5.0, bilanzsumme = 100.0, cashflow = 6.0, langfristSchulden = 10.0, umlaufvermoegen = 50.0,
            kurzfristSchulden = 25.0, aktien = 100.0, bruttogewinn = 40.0, umsatz = 100.0)
        val j = Qualitaet.Jahr("2025", nettogewinn = -2.0, bilanzsumme = 100.0, cashflow = -3.0, langfristSchulden = 20.0, umlaufvermoegen = 40.0,
            kurzfristSchulden = 40.0, aktien = 110.0, bruttogewinn = 30.0, umsatz = 90.0)
        val p = Qualitaet.piotroski(listOf(v, j))!!
        assertEquals(0, p.score)
        assertEquals(Qualitaet.Stufe.SCHWACH, Qualitaet.stufe(p))
        // Cashflow (-1) liegt ueber dem Verlust (-2): dieser Punkt waere erfuellt, alle anderen nicht
        val mitCf = Qualitaet.piotroski(listOf(v, j.copy(cashflow = -1.0)))!!
        assertEquals(0, mitCf.pruefungen.count { it.erfuellt == true && it.punkt != Punkt.CASHFLOW_UEBER_GEWINN })
        // Fehlende Felder: nicht pruefbar statt falsch
        val luecken = Qualitaet.piotroski(listOf(v.copy(aktien = null, bruttogewinn = null), j.copy(langfristSchulden = null)))!!
        assertNull(luecken.pruefungen.first { it.punkt == Punkt.KEINE_NEUEN_AKTIEN }.erfuellt)
        assertNull(luecken.pruefungen.first { it.punkt == Punkt.MARGE_STEIGT }.erfuellt)
        assertEquals(7, luecken.geprueft)
        // Zu wenig pruefbar fuer eine Einordnung
        assertNull(Qualitaet.stufe(Qualitaet.Piotroski("a", "b", luecken.pruefungen.take(5))))
        // Ohne Schulden in beiden Jahren gilt der Punkt als erfuellt; ohne jede Angabe als nicht pruefbar
        val ohne = Qualitaet.piotroski(listOf(v.copy(langfristSchulden = 0.0), j.copy(langfristSchulden = 0.0)))!!
        assertTrue(ohne.pruefungen.first { it.punkt == Punkt.SCHULDEN_SINKEN }.erfuellt!!)
        val keine = Qualitaet.piotroski(listOf(v.copy(langfristSchulden = null), j.copy(langfristSchulden = null)))!!
        assertNull(keine.pruefungen.first { it.punkt == Punkt.SCHULDEN_SINKEN }.erfuellt)
        // Ein Jahr reicht nicht
        assertNull(Qualitaet.piotroski(listOf(j)))
    }

    @Test fun altmanZonen() {
        val j = Qualitaet.lesen(antwort).last()
        val a = Qualitaet.altman(j, 150e9)!!
        // 1,2·15/104 + 1,4·25/104 + 3,3·15/104 + 0,6·150/58 + 1,0·110/104
        val erwartet = 1.2 * 15 / 104 + 1.4 * 25 / 104 + 3.3 * 15 / 104 + 0.6 * 150 / 58 + 110.0 / 104
        assertEquals(erwartet, a.z, 1e-9)
        assertEquals(Qualitaet.Zone.SICHER, a.zone)
        assertEquals(Qualitaet.Zone.SICHER, Qualitaet.zone(3.0))
        assertEquals(Qualitaet.Zone.GRAU, Qualitaet.zone(2.99))
        assertEquals(Qualitaet.Zone.GRAU, Qualitaet.zone(1.81))
        assertEquals(Qualitaet.Zone.GEFAEHRDET, Qualitaet.zone(1.8))
        // Ohne Betriebskapital: Umlaufvermoegen minus kurzfristige Schulden
        val ohneWc = Qualitaet.altman(j.copy(betriebskapital = null), 150e9)!!
        assertEquals(a.z, ohneWc.z, 1e-9)
        // Fehlende Ruecklagen oder Boersenwert: kein Ergebnis
        assertNull(Qualitaet.altman(j.copy(gewinnruecklagen = null), 150e9))
        assertNull(Qualitaet.altman(j, 0.0))
        assertNotNull(Qualitaet.altman(j, 1.0))
    }

    @Test fun finanzwerteAusgenommen() {
        assertTrue(Qualitaet.ausgenommen("Financial Services", "Banks - Diversified"))
        assertTrue(Qualitaet.ausgenommen("", "Insurance - Life"))
        assertFalse(Qualitaet.ausgenommen("Technology", "Consumer Electronics"))
        assertTrue(Qualitaet.lesen("kein json").isEmpty())
    }
}
