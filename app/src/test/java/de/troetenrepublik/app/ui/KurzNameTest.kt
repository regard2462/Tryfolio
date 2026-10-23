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
    fun kuerzungenLaengsteZuerst() {
        assertEquals(listOf("Nebius Group N.V.", "Nebius Group", "Nebius", "Nebiu", "Nebi", "Neb"), namensKuerzungen("Nebius Group N.V."))
        val k = namensKuerzungen("Sprott Junior Copper Miners UCITS ETF")
        assertEquals("Sprott Junior Copper Miners", k[1])
        assertEquals("Sprott Junior Copper", k[2])
        assertEquals("Alphabet", namensKuerzungen("Alphabet Inc. Class A")[2])
        // Kein Name endet auf ein Bindewort.
        assertEquals(listOf("Bank of America Corp", "Bank of America", "Bank"), namensKuerzungen("Bank of America Corp").take(3))
        assertEquals("Procter", namensKuerzungen("Procter & Gamble Co")[2])
    }
}
