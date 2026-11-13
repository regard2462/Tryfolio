package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 5.94: Netto-Leerverkaufspositionen aus der CSV des Bundesanzeigers, Katar nur als Import ohne Seite.
class Fassung594QuellenTest {
    private val heute = java.time.LocalDate.of(2026, 11, 2)

    @Test
    fun aktuelleListe() {
        val csv = "﻿\"Positionsinhaber\",\"Emittent\",\"ISIN\",\"Position\",\"Datum\"\n" +
            "\"Citadel Advisors LLC\",\"CANCOM SE\",\"DE0005419105\",\"1,30\",\"2026-10-30\"\n" +
            "\"Marshall Wace LLP\",\"CANCOM SE\",\"DE0005419105\",\"0,62\",\"2026-09-01\"\n" +
            "\"SIH Partners, LLLP\",\"Aurubis AG\",\"DE0006766504\",\"1,22\",\"2026-10-30\"\n"
        val e = RegisterImport.erkennen(csv.toByteArray(), heute)
        assertTrue(e is RegisterImport.Ergebnis.Leer)
        e as RegisterImport.Ergebnis.Leer
        assertEquals("DE", e.datei.land)
        assertEquals("2026-10-30", e.datei.stand)
        val cancom = e.datei.titel.getValue("DE0005419105")
        assertEquals(1.92, cancom.summe, 1e-9)
        assertEquals("Citadel Advisors LLC", cancom.positionen.first().h)
        // Vor dem 1. September gab es keine veroeffentlichte Position
        assertEquals(0.0, cancom.verlauf.first().s, 1e-9)
        // Nur aktuelle Positionen: getrennt fuer das Fortschreiben
        assertNotNull(e.offen)
        assertEquals(2, e.offen!!.getValue("DE0005419105").second.size)
        assertEquals("DE", Leerpositionen.landFuerIsin("DE0005419105"))
    }

    @Test
    fun historischeDaten() {
        val csv = "﻿\"Positionsinhaber\",\"Emittent\",\"ISIN\",\"Position\",\"Datum\"\n" +
            "\"Citadel Advisors LLC\",\"PUMA SE\",\"DE0006969603\",\"0,59\",\"2026-10-30\"\n" +
            "\"Two Sigma Investments, LP\",\"PUMA SE\",\"DE0006969603\",\"0,49\",\"2026-10-01\"\n" +
            "\"Two Sigma Investments, LP\",\"PUMA SE\",\"DE0006969603\",\"0,89\",\"2026-08-03\"\n"
        val e = RegisterImport.erkennen(csv.toByteArray(), heute) as RegisterImport.Ergebnis.Leer
        // Eine Meldung unter 0,5 % schliesst die Position: nur Citadel bleibt offen
        val puma = e.datei.titel.getValue("DE0006969603")
        assertEquals(listOf("Citadel Advisors LLC"), puma.positionen.map { it.h })
        assertEquals(0.59, puma.summe, 1e-9)
        // Im September zaehlt Two Sigma noch mit
        assertEquals(0.89, puma.verlauf.first { it.t == "2026-09-07" }.s, 1e-9)
        assertNull(e.offen)
    }

    @Test
    fun bundesanleihen() {
        val k = { j: String -> "BBSIS.D.I.ZST.ZI.EUR.S1311.B.A604.R${j}XX.R.A.A._Z._Z.A" }
        val csv = "﻿\"\"," + listOf("01", "02", "10").joinToString(",") { k(it) + "," + k(it) + "_FLAGS" } + "\n" +
            "Decimals,2,,2,,2,\n" +
            "2026-11-01,.,No value available,.,No value available,.,No value available\n" +
            "2026-11-02,2.92,,3.03,,3.37,\n"
        val kurven = de.troetenrepublik.app.engine.Marktkurven.bundesbankKurven(csv)
        assertEquals(1, kurven.size)
        assertEquals(3.37, kurven[0].wert(de.troetenrepublik.app.engine.Marktkurven.ZEHN_JAHRE)!!, 1e-9)
        assertEquals(listOf(365, 730, 3650), kurven[0].punkte.map { it.tage })
        assertTrue(de.troetenrepublik.app.engine.Marktkurven.bundesbankAdresse("2025-09-28").contains("R01XX+R02XX+R03XX+R05XX+R07XX+R10XX+R20XX+R30XX"))
    }

    @Test
    fun katarNurImport() {
        assertEquals("QA" to "QNBK", InsiderWelt.ort("QNBK.QA", ""))
        assertTrue(RegisterImportWelt.QUELLEN.single { it.land == "QA" }.url.isEmpty())
        assertTrue("QA" in InsiderWelt.NEU)
    }
}
