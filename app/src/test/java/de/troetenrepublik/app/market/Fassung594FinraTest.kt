package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Fassung 5.94: FINRA nur mit eigenem Zugang, Dark-Pool-Wochen (ATS).
class Fassung594FinraTest {

    @Test fun tokenUndKopf() {
        assertEquals("abc" to 43170L, Finra.tokenLesen("""{"access_token":"abc","scope":"any","token_type":"Bearer","expires_in":"43170"}"""))
        assertNull(Finra.tokenLesen("""{"error":"invalid_client"}"""))
        assertTrue(Finra.tokenKopf("id", "geheim")["Authorization"]!!.startsWith("Basic "))
        assertEquals("BRK.B", Finra.kuerzel("brk.b\"}"))
    }

    // Feldnamen klein wie in den Beispielen der Spezifikation und in camelCase wie in der Feldliste
    private val wochen = """[
        {"issuesymbolidentifier":"EXM","summaryTypeCode":"ATS_W_SMBL","totalWeeklyShareQuantity":300000,"totalweeklytradecount":1200,"tierIdentifier":"T1","weekStartDate":"2026-09-07"},
        {"issueSymbolIdentifier":"EXM","summaryTypeCode":"ATS_W_SMBL","totalWeeklyShareQuantity":500000,"totalWeeklyTradeCount":2000,"tierIdentifier":"T1","weekStartDate":"2026-09-14"}
    ]"""

    @Test fun wochenUndPlaetze() {
        val w = Finra.atsWochenLesen(wochen)
        assertEquals(listOf("2026-09-07", "2026-09-14"), w.map { it.woche })
        assertEquals(500000.0, w.last().aktien, 0.0)
        assertEquals(2000L, w.last().abschluesse)
        val p = Finra.atsPlaetzeLesen("""[
            {"marketParticipantName":"PLATZ A","totalWeeklyShareQuantity":100,"weekStartDate":"2026-09-14"},
            {"marketParticipantName":"PLATZ B","totalWeeklyShareQuantity":300,"weekStartDate":"2026-09-14"},
            {"marketParticipantName":"PLATZ C","totalWeeklyShareQuantity":900,"weekStartDate":"2026-09-07"},
            {"marketParticipantName":null,"totalWeeklyShareQuantity":50,"weekStartDate":"2026-09-14"}
        ]""", "2026-09-14")
        assertEquals(listOf("PLATZ B", "PLATZ A"), p.map { it.name })
    }

    @Test fun anteilAmWochenumsatz() {
        fun kerze(tag: String, umsatz: Double) = Candle(LocalDate.parse(tag).atTime(14, 30).toInstant(ZoneOffset.UTC).toEpochMilli(), 10.0, 11.0, 9.0, 10.0, umsatz)
        val k = listOf("2026-09-14", "2026-09-15", "2026-09-16", "2026-09-17", "2026-09-18").map { kerze(it, 1_000_000.0) } +
            listOf(kerze("2026-09-08", 1_000_000.0), kerze("2026-09-09", 1_000_000.0))
        val a = Finra.anteile(Finra.atsWochenLesen(wochen), k)
        // Woche ab 07.09. hat nur zwei Tage mit Umsatz und faellt weg
        assertEquals(1, a.size)
        assertEquals("2026-09-14", a.single().first)
        assertEquals(0.1, a.single().second, 1e-12)
    }
}
