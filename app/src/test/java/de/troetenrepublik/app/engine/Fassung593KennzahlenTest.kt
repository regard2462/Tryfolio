package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Fassung 5.93: neue Kennzahlen aus den Jahresabschluessen (Kurs/Cashflow, Zinsdeckung, ROIC, Owner Earnings) und
// die dazugehoerigen Zeilen des Kennzahlenvergleichs.
class Fassung593KennzahlenTest {

    // Antwort im Aufbau von ws/fundamentals-timeseries; Werte direkt als raw
    private fun eintrag(typ: String, werte: List<Pair<String, Double?>>): String =
        "{\"meta\":{\"symbol\":[\"TEST\"],\"type\":[\"$typ\"]},\"timestamp\":[1],\"$typ\":[" +
            werte.joinToString(",") { (d, v) ->
                if (v == null) "null" else "{\"asOfDate\":\"$d\",\"periodType\":\"12M\",\"currencyCode\":\"USD\",\"reportedValue\":{\"raw\":$v,\"fmt\":\"x\"}}"
            } + "]}"

    @Test fun lesenNimmtTrailingNichtAlsJahr() {
        val antwort = "{\"timeseries\":{\"result\":[" + listOf(
            eintrag("annualEBIT", listOf("2025-09-30" to 133050e6)),
            eintrag("annualInterestExpense", listOf("2023-09-30" to 3933e6)),
            eintrag("trailingOperatingCashFlow", listOf("2026-06-30" to 146724e6)),
            eintrag("annualTaxRateForCalcs", listOf("2025-09-30" to 0.156)),
            eintrag("annualInvestedCapital", listOf("2025-09-30" to 172390e6)),
            eintrag("annualOperatingCashFlow", listOf("2025-09-30" to 111482e6)),
            eintrag("annualCapitalExpenditure", listOf("2025-09-30" to -12715e6)),
        ).joinToString(",") + "],\"error\":null}}"
        val jahre = Qualitaet.lesen(antwort)
        // Der Stichtag des trailing-Werts darf kein zusaetzliches Jahr erzeugen
        assertEquals(listOf("2023-09-30", "2025-09-30"), jahre.map { it.ende })
        val letzte = jahre.last()
        assertEquals(146724e6, letzte.cashflowLaufend!!, 1.0)
        assertNull(letzte.zinsaufwand)
        assertEquals(0.156, letzte.steuersatz!!, 1e-9)
        assertNull(jahre.first().cashflowLaufend)
    }

    @Test fun kursCashflow() {
        assertEquals(20.45, Kapitalkennzahlen.kursCashflow(3e12, 146.724e9)!!, 0.01)
        assertNull(Kapitalkennzahlen.kursCashflow(3e12, 0.0))
        assertNull(Kapitalkennzahlen.kursCashflow(3e12, -1.0))
        assertNull(Kapitalkennzahlen.kursCashflow(null, 146.724e9))
        assertNull(Kapitalkennzahlen.kursCashflow(3e12, null))
    }

    @Test fun zinsdeckung() {
        assertEquals(25.0, Kapitalkennzahlen.zinsdeckung(Qualitaet.Jahr("2025", ebit = 100.0, zinsaufwand = 4.0))!!, 1e-9)
        // Ohne Zinsangabe im letzten Jahr (Apple) und bei Zins 0 bleibt die Kennzahl leer
        assertNull(Kapitalkennzahlen.zinsdeckung(Qualitaet.Jahr("2025", ebit = 100.0)))
        assertNull(Kapitalkennzahlen.zinsdeckung(Qualitaet.Jahr("2025", ebit = 100.0, zinsaufwand = 0.0)))
    }

    @Test fun roic() {
        val j = Qualitaet.Jahr("2025", ebit = 133050e6, steuersatz = 0.156, investiertesKapital = 172390e6)
        assertEquals(65.1, Kapitalkennzahlen.roic(j)!!, 0.1)
        assertNull(Kapitalkennzahlen.roic(j.copy(steuersatz = 1.2)))
        assertNull(Kapitalkennzahlen.roic(j.copy(investiertesKapital = 0.0)))
    }

    @Test fun ownerEarnings() {
        // CapEx ist negativ gemeldet, das Vorzeichen darf keine Rolle spielen
        assertEquals(98767e6, Kapitalkennzahlen.ownerEarnings(Qualitaet.Jahr("2025", cashflow = 111482e6, capex = -12715e6))!!, 1.0)
        assertEquals(98767e6, Kapitalkennzahlen.ownerEarnings(Qualitaet.Jahr("2025", cashflow = 111482e6, capex = 12715e6))!!, 1.0)
        // Ohne capex: freier Cashflow
        assertEquals(90e9, Kapitalkennzahlen.ownerEarnings(Qualitaet.Jahr("2025", cashflow = 111482e6, freierCashflow = 90e9))!!, 1.0)
        assertEquals(3.29, Kapitalkennzahlen.ownerRendite(98767e6, 3e12)!!, 0.01)
        assertNull(Kapitalkennzahlen.ownerRendite(null, 3e12))
        assertNull(Kapitalkennzahlen.ownerRendite(98767e6, null))
        assertNull(Kapitalkennzahlen.ownerEarnings(Qualitaet.Jahr("2025")))
    }

    @Test fun berechneNimmtLaufendenCashflow() {
        val jahre = listOf(Qualitaet.Jahr("2024", cashflow = 100e9), Qualitaet.Jahr("2025", cashflow = 100e9, cashflowLaufend = 150e9))
        assertEquals(20.0, Kapitalkennzahlen.berechne(jahre, 3e12)!!.kursCashflow!!, 1e-9)
        // Ohne trailing-Wert gilt der Jahreswert
        assertEquals(30.0, Kapitalkennzahlen.berechne(listOf(Qualitaet.Jahr("2025", cashflow = 100e9)), 3e12)!!.kursCashflow!!, 1e-9)
        assertNull(Kapitalkennzahlen.berechne(emptyList(), 3e12))
    }

    @Test fun vergleichZeilen() {
        assertEquals(12.0, Kennzahlvergleich.Werte(roic = 12.0).wert(Kennzahlvergleich.Zeile.ROIC)!!, 1e-9)
        assertEquals(Kennzahlvergleich.Richtung.NIEDRIG, Kennzahlvergleich.Zeile.KURS_CASHFLOW.richtung)
        assertEquals(Kennzahlvergleich.Richtung.HOCH, Kennzahlvergleich.Zeile.ZINSDECKUNG.richtung)
        assertEquals(1, Kennzahlvergleich.bester(listOf(10.0, 25.0, 5.0), Kennzahlvergleich.Zeile.ZINSDECKUNG.richtung)!!)
    }
}
