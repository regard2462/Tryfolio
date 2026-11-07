package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Namen der Tresoreintraege, die beim Start geloescht werden.
class Fassung593TresorBereinigungTest {
    @Test fun tresorNamenJeArt() {
        val alpaca = TresorBereinigung.tresorNamen("ALPACA")
        assertEquals(TresorBereinigung.ARTEN.getValue("ALPACA").size, alpaca.size)
        TresorBereinigung.ARTEN.getValue("ALPACA").forEach { feld ->
            assertTrue(alpaca.contains("broker_alpaca_$feld"))
        }
        val comdirect = TresorBereinigung.tresorNamen("COMDIRECT")
        listOf("clientId", "clientSecret", "zugang", "pin").forEach { feld ->
            assertTrue(comdirect.contains("broker_comdirect_$feld"))
        }
        val ibkr = TresorBereinigung.tresorNamen("IBKR")
        assertTrue(ibkr.contains("broker_ibkr_adresse"))
        assertTrue(ibkr.contains("broker_ibkr_zertifikat"))
    }

    @Test fun unbekannteArtIstLeer() {
        assertTrue(TresorBereinigung.tresorNamen("UNBEKANNT").isEmpty())
    }

    @Test fun alleOhneDuplikate() {
        val alle = TresorBereinigung.alleTresorNamen()
        assertEquals(alle.size, alle.toSet().size)
        TresorBereinigung.ARTEN.keys.forEach { art ->
            TresorBereinigung.tresorNamen(art).forEach { name -> assertTrue(alle.contains(name)) }
        }
    }

    @Test fun schemaPräfix() {
        TresorBereinigung.alleTresorNamen().forEach { name ->
            assertTrue(name.startsWith("broker_"))
            assertTrue(name.drop("broker_".length).contains('_'))
        }
    }
}
