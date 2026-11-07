package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Summenlisten der FCA als selbst geladene CSV (5.94): aktuelle und fruehere Liste werden erkannt, erfundene Werte.
class Fassung594FcaImportTest {

    private val heute = java.time.LocalDate.of(2026, 11, 3)

    @Test
    fun erkennt_aktuelle_und_fruehere_liste() {
        val aktuell = "﻿Name of Company,International Securities Identification Number (ISIN),Aggregated net short position (%),Position date (of latest position date notified)\n" +
            "Beispiel Plc,GB0000000001,1.2,22/09/2026\n"
        val frueher = "Name of Company,International Securities Identification Number (ISIN),Aggregated net short position (%),Position date,Date the aggregated net short position became historical\n" +
            "Beispiel Plc,GB0000000001,0.8,01/06/2026,22/09/2026\n"
        val a = RegisterImport.erkennen(aktuell.toByteArray(), heute) as RegisterImport.Ergebnis.Fca
        assertFalse(a.historisch)
        val f = RegisterImport.erkennen(frueher.toByteArray(), heute) as RegisterImport.Ergebnis.Fca
        assertTrue(f.historisch)
        val t = Leerpositionen.fcaAuswerten(a.text, f.text, heute).getValue("GB0000000001")
        assertEquals(1.2, t.gesamt ?: 0.0, 1e-9)
    }

    @Test
    fun fremdeUsFirmaGleichenAnfangs() {
        val k = listOf(Zweitnotiz.Kandidat("PBYI", "NMS", 5.0, "Puma Biotechnology, Inc."), Zweitnotiz.Kandidat("SAP", "NYQ", 200.0, "SAP SE"))
        assertEquals(null, Zweitnotiz.usNotierung("PUMA", k))
        assertEquals("SAP", Zweitnotiz.usNotierung("SAP", k))
    }

    // Namen wie sie die Yahoo-Suche am 03.11.2026 lieferte
    @Test
    fun usNamenMitZusatz() {
        fun us(begriff: String, vararg t: Pair<String, String>) = Zweitnotiz.usNotierung(begriff, t.map { Zweitnotiz.Kandidat(it.first, "NYQ", 10.0, it.second) })
        assertEquals("TM", us("Toyota", "TM" to "Toyota Motor Corporation"))
        assertEquals("SONY", us("Sony", "SONY" to "Sony Group Corporation"))
        assertEquals("SAN", us("Santander", "SAN" to "Banco Santander, S.A. Sponsored", "BSBR" to "Banco Santander Brasil SA"))
        assertEquals("BP", us("BP", "BPOP" to "Popular, Inc.", "BP" to "BP p.l.c."))
        assertEquals("HSBC", us("HSBC", "HSBC" to "HSBC Holdings, plc."))
        assertEquals("HMC", us("Honda", "HMC" to "Honda Motor Company, Ltd."))
        assertEquals("PHG", us("Philips", "PHG" to "Koninklijke Philips N.V. NY Reg"))
        assertEquals("ASML", us("ASML", "ASML" to "ASML Holding N.V. - New York Re"))
        assertEquals("NOK", us("Nokia", "NOK" to "Nokia Corporation Sponsored"))
        assertEquals("NVO", us("Novo Nordisk (B)", "NVO" to "Novo Nordisk A/S"))
        assertEquals("TEF", us("Telefónica", "TEF" to "Telefonica, S.A."))
        assertEquals(null, us("Mitsubishi Corporation", "MUFG" to "Mitsubishi UFJ Financial Group,"))
        assertEquals(null, us("Santander", "BSBR" to "Banco Santander Brasil SA"))
        assertEquals(null, us("Orion", "ORN" to "Orion Group Holdings, Inc."))
        assertEquals("NMR", us("Nomura Holdings", "NMR" to "Nomura Holdings, Inc."))
    }
}
