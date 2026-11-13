package de.troetenrepublik.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KurzNameTest {
    @Test
    fun fonds() {
        assertEquals("Vanguard FTSE All-World", kurzerFondsname("Vanguard FTSE All-World UCITS ETF USD Acc"))
        assertEquals("Sprott Junior Copper Miners", kurzerFondsname("Sprott Junior Copper Miners UCITS ETF"))
        assertEquals("Gold Miners", kurzerFondsname("Gold Miners USD (Acc)"))
        assertEquals("iShares Core MSCI World", kurzerFondsname("iShares Core MSCI World UCITS ETF USD (Acc)"))
        assertEquals("Xtrackers MSCI World", kurzerFondsname("Xtrackers MSCI World UCITS ETF 1C"))
    }

    @Test
    fun firmen() {
        assertEquals("Novo Nordisk A/S", kurzerFondsname("Novo Nordisk A/S"))
        assertEquals("Alphabet Inc.", kurzerFondsname("Alphabet Inc."))
        assertEquals("Alphamin Resources Corp.", kurzerFondsname("Alphamin Resources Corp."))
    }

    @Test
    fun fassungenLaengsteZuerst() {
        // Nur ganze Namen: Rechtsform und Fonds-Beiwerk fallen weg, kein Wort des Namens und kein Buchstabe
        assertEquals(listOf("Nebius Group N.V.", "Nebius Group", "Nebius"), namensKuerzungen("Nebius Group N.V."))
        assertEquals(listOf("Sprott Junior Copper Miners UCITS ETF", "Sprott Junior Copper Miners"), namensKuerzungen("Sprott Junior Copper Miners UCITS ETF"))
        assertEquals("Alphabet", namensKuerzungen("Alphabet Inc. Class A").last())
        assertEquals(listOf("Bank of America Corp", "Bank of America"), namensKuerzungen("Bank of America Corp"))
        assertEquals("Procter & Gamble", namensKuerzungen("Procter & Gamble Co").last())
        assertEquals(listOf("Nornickel"), namensKuerzungen("Nornickel"))
        assertEquals(listOf("Moscow Exchange"), namensKuerzungen("Moscow Exchange"))
    }
}
