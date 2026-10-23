package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.InsiderTrend.Geschaeft
import de.troetenrepublik.app.engine.InsiderTrend.Rolle
import de.troetenrepublik.app.engine.InsiderTrend.Stufe
import de.troetenrepublik.app.market.Bafin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.6: Insider-Trend mit Gewichtung nach Beziehung zum Unternehmen.
class Fassung56Test {
    @Test fun gewichtungNachRolle() {
        // Vorstand kauft (3), zwei Nahestehende verkaufen (je 1): (3 - 2) / 5 = +20
        val t = InsiderTrend.auswerten(listOf(
            Geschaeft("Anna", Rolle.SPITZE, true, 50_000.0, "2026-10-01"),
            Geschaeft("Holding A", Rolle.NAHE, false, 20_000.0, "2026-10-02"),
            Geschaeft("Holding B", Rolle.NAHE, false, 30_000.0, "2026-10-03"),
        ), "2026-07-01")!!
        assertEquals(20, t.wert)
        assertEquals(Stufe.EHER_KAUFEND, t.stufe)
        assertEquals(1, t.kaeufer)
        assertEquals(2, t.verkaeufer)
        assertEquals(1 to 0, t.nachRolle[Rolle.SPITZE])
        assertEquals(0 to 2, t.nachRolle[Rolle.NAHE])
    }

    @Test fun jedePersonZaehltEinmalMitNettorichtung() {
        val t = InsiderTrend.auswerten(listOf(
            Geschaeft("Bernd", Rolle.AUFSICHT, true, 10_000.0, "2026-09-01"),
            Geschaeft("Bernd", Rolle.AUFSICHT, false, 40_000.0, "2026-09-10"),
        ), "2026-07-01")!!
        assertEquals(-100, t.wert)
        assertEquals(Stufe.KLAR_VERKAUFEND, t.stufe)
        assertEquals(0, t.kaeufer)
        assertEquals(1, t.verkaeufer)
    }

    @Test fun planverkaeufeZaehlenHalb() {
        // Spitze verkauft nur ueber Plan (3 x 0,5 = 1,5), Aufsicht kauft (1,5): ausgeglichen
        val t = InsiderTrend.auswerten(listOf(
            Geschaeft("CEO", Rolle.SPITZE, false, 1_000_000.0, "2026-10-01", plan = true),
            Geschaeft("Direktor", Rolle.AUFSICHT, true, 100_000.0, "2026-10-02"),
        ), "2026-07-01")!!
        assertEquals(0, t.wert)
        assertEquals(Stufe.GEMISCHT, t.stufe)
    }

    @Test fun clusterUndZeitraum() {
        val kaeufe = listOf("A", "B", "C").mapIndexed { i, p -> Geschaeft(p, Rolle.NAHE, true, 1_000.0, "2026-10-0${i + 1}") }
        assertTrue(InsiderTrend.cluster(kaeufe))
        assertFalse(InsiderTrend.cluster(kaeufe.take(2) + Geschaeft("C", Rolle.NAHE, true, 1.0, "2026-12-01")))
        assertNull(InsiderTrend.auswerten(kaeufe, "2026-11-01"))
    }

    @Test fun bafinRollenUndNurAktien() {
        assertEquals(Rolle.SPITZE, InsiderTrend.rolleBafin("Vorstand"))
        assertEquals(Rolle.FUEHRUNG, InsiderTrend.rolleBafin("Sonstige Führungsperson"))
        assertEquals(Rolle.AUFSICHT, InsiderTrend.rolleBafin("Aufsichtsrat"))
        assertEquals(Rolle.NAHE, InsiderTrend.rolleBafin("in enger Beziehung"))
        val liste = listOf(
            Bafin.Geschaeft(id = "1", isin = "DE1", e = "X", p = "Anna", r = "Vorstand", i = "Aktie", art = "Kauf", d = "2026-10-01", vol = 1000.0),
            Bafin.Geschaeft(id = "2", isin = "DE1", e = "X", p = "Bob", r = "Aufsichtsrat", i = "Schuldtitel", art = "Verkauf", d = "2026-10-01", vol = 1000.0),
            Bafin.Geschaeft(id = "3", isin = "DE1", e = "X", p = "Cem", r = "Vorstand", i = "Aktie", art = "Sonstiges", d = "2026-10-01", vol = 1000.0),
        )
        val t = Bafin.trend(liste, "2026-07-01")!!
        assertEquals(100, t.wert)
        assertEquals(1, t.kaeufer)
        assertEquals(0, t.verkaeufer)
    }

    private val form4 = """<?xml version="1.0"?>
<ownershipDocument>
    <issuer><issuerCik>0001045810</issuerCik><issuerName>NVIDIA CORP</issuerName><issuerTradingSymbol>NVDA</issuerTradingSymbol></issuer>
    <reportingOwner>
        <reportingOwnerId><rptOwnerCik>0001197647</rptOwnerCik><rptOwnerName>Huang Jen Hsun</rptOwnerName></reportingOwnerId>
        <reportingOwnerRelationship><isDirector>1</isDirector><isOfficer>1</isOfficer><officerTitle>President and CEO</officerTitle></reportingOwnerRelationship>
    </reportingOwner>
    <aff10b5One>1</aff10b5One>
    <nonDerivativeTable>
        <nonDerivativeTransaction>
            <transactionDate><value>2026-09-15</value></transactionDate>
            <transactionCoding><transactionFormType>4</transactionFormType><transactionCode>S</transactionCode></transactionCoding>
            <transactionAmounts><transactionShares><value>1000</value></transactionShares><transactionPricePerShare><value>180.50</value><footnoteId id="F1"/></transactionPricePerShare><transactionAcquiredDisposedCode><value>D</value></transactionAcquiredDisposedCode></transactionAmounts>
            <postTransactionAmounts><sharesOwnedFollowingTransaction><value>9000</value></sharesOwnedFollowingTransaction></postTransactionAmounts>
        </nonDerivativeTransaction>
        <nonDerivativeTransaction>
            <transactionDate><value>2026-09-16</value></transactionDate>
            <transactionCoding><transactionCode>S</transactionCode></transactionCoding>
            <transactionAmounts><transactionShares><value>1000</value></transactionShares><transactionPricePerShare><value>181.50</value></transactionPricePerShare></transactionAmounts>
            <postTransactionAmounts><sharesOwnedFollowingTransaction><value>8000</value></sharesOwnedFollowingTransaction></postTransactionAmounts>
        </nonDerivativeTransaction>
        <nonDerivativeTransaction>
            <transactionDate><value>2026-09-16</value></transactionDate>
            <transactionCoding><transactionCode>M</transactionCode></transactionCoding>
            <transactionAmounts><transactionShares><value>5000</value></transactionShares><transactionPricePerShare><value>10</value></transactionPricePerShare></transactionAmounts>
        </nonDerivativeTransaction>
    </nonDerivativeTable>
</ownershipDocument>"""

    @Test fun form4ZusammengefasstOhneAusuebung() {
        val g = de.troetenrepublik.app.market.SecInsider.form4(form4, "0001197647-26-000001", "2026-09-17").single()
        assertEquals("NVDA", g.k)
        assertEquals("1045810", g.c)
        assertEquals("Huang Jen Hsun", g.p)
        assertEquals("S", g.r)
        assertFalse(g.kauf)
        assertTrue(g.plan)
        assertEquals(2000.0, g.n, 1e-9)
        assertEquals(362000.0, g.wert, 1e-6)
        assertEquals(181.0, g.preis, 1e-9)
        assertEquals("2026-09-16", g.d)
        assertEquals(8000.0, g.nach!!, 1e-9)
    }

    @Test fun secRollenUndKennung() {
        val s = de.troetenrepublik.app.market.SecInsider
        assertEquals("S", s.rolle(director = false, officer = true, titel = "Chief Financial Officer"))
        assertEquals("F", s.rolle(director = false, officer = true, titel = "General Counsel"))
        assertEquals("A", s.rolle(director = true, officer = false, titel = ""))
        assertEquals("N", s.rolle(director = false, officer = false, titel = ""))
        assertNull(s.kennung(""))
        assertNull(s.kennung("keine adresse"))
        assertTrue(s.kennung("jemand@example.org")!!.endsWith("jemand@example.org"))
        assertEquals(listOf("0001197647-26-000001" to "1197647"),
            s.neuesteLesen("<link href=\"https://www.sec.gov/Archives/edgar/data/1197647/000119764726000001/0001197647-26-000001-index.htm\"/>"))
    }
}
