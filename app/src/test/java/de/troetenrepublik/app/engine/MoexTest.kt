package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Boersenwerte der Moskauer Boerse aus der ISS-Antwort (gekuerzt).
class MoexTest {
    @Test
    fun liestBoersenwerteUndLaesstLeereAus() {
        val antwort = """{"marketdata":{"columns":["SECID","ISSUECAPITALIZATION"],"data":[["ABIO",4393247286.42],["AFLT",152749130080.3],["AIIQ",null],["XX",0]]}}"""
        val m = Moex.lesen(antwort)
        assertEquals(2, m.size)
        assertEquals(152749130080.3, m.getValue("AFLT"), 0.0)
        assertTrue("AIIQ" !in m && "XX" !in m)
        assertTrue(Moex.lesen("kaputt").isEmpty())
    }
}
