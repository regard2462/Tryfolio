package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.LaenderFirmen.Kandidat
import de.troetenrepublik.app.engine.LaenderFirmen.Kurs
import de.troetenrepublik.app.market.Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Kandidaten, Rangliste nach Boersenwert und Branchenanteile.
class LaenderFirmenTest {
    private val buendel = """{"stand":"2026-11-06","s":["PETR4.SA"],"l":{"IN":[["RELIANCE.NS","Reliance Industries","OEL_GAS",0],["SBIN.NS","State Bank of India","BANKEN",1]]}}"""
    private val privat = """{"stand":"2026-11-06","l":{"DE":[["Schwarz Gruppe","EINZELHANDEL",0],["Deutsche Bahn","VERKEHR",1]]}}"""

    @Test
    fun buendelUndPrivatWerdenGelesen() {
        val b = LaenderFirmen.buendel(buendel)["IN"]!!
        assertEquals(2, b.size)
        assertEquals(Kandidat("SBIN.NS", "State Bank of India", "BANKEN", true), b[1])
        val p = LaenderFirmen.privat(privat)["DE"]!!
        assertEquals("Schwarz Gruppe", p[0].name)
        assertEquals(true, p[1].staat)
        assertTrue(LaenderFirmen.buendel("kaputt").isEmpty())
    }

    @Test
    fun staatSymboleGeltenAuchFuerKatalogwerte() {
        val staat = LaenderFirmen.staatSymbole(buendel)
        assertEquals(setOf("PETR4.SA"), staat)
        val katalog = listOf(Catalog.Stock("PETR4.SA", "Petrobras", "BR", "OEL_GAS"), Catalog.Stock("VALE3.SA", "Vale", "BR", "BERGBAU"))
        val k = LaenderFirmen.kandidaten("BR", emptyList(), katalog, staat)
        assertEquals(listOf(true, false), k.map { it.staat })
        assertTrue(LaenderFirmen.staatSymbole("kaputt").isEmpty())
    }

    @Test
    fun kandidatenKatalogZuerstOhneDoppelte() {
        val katalog = listOf(
            Catalog.Stock("INFY.NS", "Infosys", "IN", "SOFTWARE"),
            Catalog.Stock("RELIANCE.NS", "Reliance", "IN", "OEL_GAS"),
            Catalog.Stock("AAPL", "Apple", "US", "COMPUTER"),
        )
        val k = LaenderFirmen.kandidaten("IN", LaenderFirmen.buendel(buendel)["IN"]!!, katalog)
        assertEquals(listOf("INFY.NS", "RELIANCE.NS", "SBIN.NS"), k.map { it.symbol })
        // Name und Branche des Katalogs haben Vorrang
        assertEquals("Reliance", k[1].name)
    }

    private val kand = listOf(
        Kandidat("A", "Alpha Bank", "BANKEN", false), Kandidat("B", "Beta Oel", "OEL_GAS", true), Kandidat("C", "Gamma Software", "SOFTWARE", false),
        Kandidat("D", "Delta Bank", "BANKEN", false), Kandidat("E", "Epsilon", "", false), Kandidat("F", "Kein Kurs", "BANKEN", false),
    )

    @Test
    fun ranglisteNachBoersenwertMitTagesaenderung() {
        val kurse = mapOf("A" to Kurs(50.0, 1.0), "B" to Kurs(200.0, -2.0), "C" to Kurs(100.0, null), "D" to Kurs(10.0, 0.5), "E" to Kurs(5.0, 0.0))
        val r = LaenderFirmen.rangliste("XX", kand, kurse, n = 3)
        assertEquals(listOf("B", "C", "A"), r.map { it.kandidat.symbol })
        assertEquals(200.0, r[0].boersenwert!!, 0.0)
        assertEquals(-2.0, r[0].aenderung!!, 0.0)
        assertNull(r[1].aenderung)
    }

    @Test
    fun ranglisteOhneKurseBehaeltBuendelReihenfolge() {
        val r = LaenderFirmen.rangliste("XX", kand, emptyMap(), n = 3)
        assertEquals(listOf("A", "B", "C"), r.map { it.kandidat.symbol })
        assertNull(r[0].boersenwert)
    }

    @Test
    fun doppelteNotierungDerselbenFirmaZaehltEinmal() {
        val k = listOf(Kandidat("X1", "Delta Holding", "BANKEN", false), Kandidat("X2", "Delta Holding", "BANKEN", false), Kandidat("Y", "Zeta", "BANKEN", false))
        val r = LaenderFirmen.rangliste("XX", k, mapOf("X1" to Kurs(10.0, null), "X2" to Kurs(30.0, null), "Y" to Kurs(20.0, null)))
        assertEquals(listOf("X2", "Y"), r.map { it.kandidat.symbol })
    }

    @Test
    fun firmenOhneYahooKuerzelFuellenDenRestAuf() {
        val b = LaenderFirmen.buendel("""{"l":{"NG":[["","Dangote Cement","BAU",0,"Nigerian Exchange"],["","MTN Nigeria","TELEKOM",0,"Nigerian Exchange"]]}}""")["NG"]!!
        assertEquals("Nigerian Exchange", b[0].boerse)
        // nur Firmen ohne Kuerzel: Reihenfolge des Buendels, ohne Werte
        val r = LaenderFirmen.rangliste("NG", LaenderFirmen.kandidaten("NG", b, emptyList()), emptyMap())
        assertEquals(listOf("Dangote Cement", "MTN Nigeria"), r.map { it.kandidat.name })
        assertNull(r[0].boersenwert)
        // gemischt: erst die mit Boersenwert, dann die ohne Kuerzel
        val k = listOf(Kandidat("X", "Alpha", "BANKEN", false)) + b
        val g = LaenderFirmen.rangliste("NG", k, mapOf("X" to Kurs(5.0, null)), n = 2)
        assertEquals(listOf("Alpha", "Dangote Cement"), g.map { it.kandidat.name })
    }

    @Test
    fun wikidataWerteUndMoexKennungWerdenGelesenUndGemeinsamGereiht() {
        val b = LaenderFirmen.buendel("""{"l":{"RU":[["","Gazprom","OEL_GAS",1,"Moscow Exchange",0,"GAZP"],["","Lukoil","OEL_GAS",0,"Moscow Exchange",[3000000000000,"RUB",2023],""]]}}""")["RU"]!!
        assertEquals("GAZP", b[0].kennung)
        assertEquals(0.0, b[0].wert, 0.0)
        assertEquals(3.0E12, b[1].wert, 0.0)
        assertEquals("RUB", b[1].waehrung)
        assertEquals(2023, b[1].jahr)
        assertEquals("#Gazprom", b[0].schluessel)
        val kurse = mapOf("#Gazprom" to Kurs(40.0, null), "#Lukoil" to Kurs(30.0, null, 2023))
        val r = LaenderFirmen.rangliste("RU", b, kurse)
        assertEquals(listOf("Gazprom", "Lukoil"), r.map { it.kandidat.name })
        assertNull(r[0].jahr)
        assertEquals(2023, r[1].jahr)
    }

    @Test
    fun branchenAnteileErstAbFuenfFirmen() {
        val kurse = mapOf("A" to Kurs(50.0, null), "B" to Kurs(200.0, null), "C" to Kurs(100.0, null), "D" to Kurs(10.0, null), "E" to Kurs(40.0, null))
        val b = LaenderFirmen.branchen("XX", kand, kurse)!!
        // Summe 400, Oel 200, Software 100, Banken 60; Epsilon ohne Branche zaehlt nur zur Summe
        assertEquals(listOf("OEL_GAS", "SOFTWARE", "BANKEN"), b.map { it.branche })
        assertEquals(0.5, b[0].anteil, 1e-9)
        assertEquals(0.15, b[2].anteil, 1e-9)
        assertNull(LaenderFirmen.branchen("XX", kand, mapOf("A" to Kurs(1.0, null), "B" to Kurs(2.0, null))))
        assertNotNull(b)
    }
}
