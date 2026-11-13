package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

// Globus 0.69, Abschnitt Wirtschaft und Markt: Leitzins (BIS), Rendite (EZB, FRED), Waehrung, Laender-ETF, Jahr der Weltbank.
class LaenderMarktTest {

    // Gekuerzte echte Antwort der BIS (06.11.2026): Euroraum XM, ein Euroland mit altem Ende (AT 1998), NaN bei PH
    private val bis = """
        FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE
        D,AU,2026-10-29,4.6
        D,AT,1998-12-31,3
        D,CA,2026-11-02,2.5
        D,CH,2026-11-03,0
        D,PH,2026-11-03,NaN
        D,US,2026-11-03,3.875
        D,XM,2026-11-03,2.5
    """.trimIndent()

    // Gekuerzte echte Antwort der EZB (Datensatz IRS, detail=dataonly)
    private val ezb = """
        KEY,FREQ,REF_AREA,IR_TYPE,TR_TYPE,MATURITY_CAT,BS_COUNT_SECTOR,CURRENCY_TRANS,IR_BUS_COV,IR_FV_TYPE,TIME_PERIOD,OBS_VALUE
        IRS.M.AT.L.L40.CI.0000.EUR.N.Z,M,AT,L,L40,CI,0000,EUR,N,Z,2026-09,3.6867
        IRS.M.DE.L.L40.CI.0000.EUR.N.Z,M,DE,L,L40,CI,0000,EUR,N,Z,2026-09,3.473
        IRS.M.IT.L.L40.CI.0000.EUR.N.Z,M,IT,L,L40,CI,0000,EUR,N,Z,2026-09,4.362
    """.trimIndent()

    // Gekuerzte FRED-Antwort (OECD-Reihe, monatlich), mit fehlendem Wert
    private val fred = "observation_date,IRLTLT01GBM156N\n2026-07-01,4.9318\n2026-08-01,.\n2026-09-01,5.2421\n"

    @Test
    fun leitzinsLesen() {
        val z = LaenderMarkt.zinsLesen(bis)
        assertEquals(3.875, z["US"]!!.wert, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 3), z["US"]!!.datum)
        assertEquals(0.0, z["CH"]!!.wert, 1e-9)
        assertNull(z["PH"])
        // Euroländer nehmen den Wert des Euroraums, auch wenn es eine alte Einzelreihe gibt
        assertEquals(2.5, LaenderMarkt.zinsFuer("AT", z)!!.wert, 1e-9)
        assertEquals(2.5, LaenderMarkt.zinsFuer("DE", z)!!.wert, 1e-9)
        assertEquals(4.6, LaenderMarkt.zinsFuer("AU", z)!!.wert, 1e-9)
        assertNull(LaenderMarkt.zinsFuer("XX", z))
        assertTrue(LaenderMarkt.zinsLesen("<html>gesperrt</html>").isEmpty())
        assertTrue(LaenderMarkt.zinsLesen("").isEmpty())
    }

    @Test
    fun renditeEzb() {
        val r = LaenderMarkt.ezbLesen(ezb)
        assertEquals(3.473, r["DE"]!!.wert, 1e-9)
        assertEquals(YearMonth.of(2026, 9), r["DE"]!!.monat)
        assertEquals(3, r.size)
        // gekuerzte Form fuer die Speicherung liest sich genauso
        val klein = LaenderMarkt.ezbKuerzen(ezb)!!
        assertEquals(r, LaenderMarkt.ezbLesen(klein))
        assertNull(LaenderMarkt.ezbKuerzen("kaputt"))
    }

    @Test
    fun renditeFred() {
        val r = LaenderMarkt.fredLesen(fred)!!
        assertEquals(5.2421, r.wert, 1e-9)
        assertEquals(YearMonth.of(2026, 9), r.monat)
        assertEquals(r, LaenderMarkt.fredLesen(LaenderMarkt.fredKuerzen(fred)!!))
        assertNull(LaenderMarkt.fredLesen("observation_date,X\n2026-08-01,.\n"))
        assertNull(LaenderMarkt.fredLesen("<html>404</html>"))
        assertTrue(LaenderMarkt.fredAdresse("GB", LocalDate.of(2025, 5, 6)).endsWith("id=IRLTLT01GBM156N&cosd=2025-05-06"))
    }

    private fun reihe(vararg c: Pair<Long, Double>) = c.map { Candle(it.first, it.second, it.second, it.second, it.second) }

    @Test
    fun veraenderungen() {
        val tag = 86_400_000L
        // Kurs in Einheiten je Euro: von 150 auf 165, die Waehrung hat zum Euro verloren
        val yen = reihe(0L to 150.0, 200 * tag to 160.0, 360 * tag to 165.0)
        assertEquals((150.0 / 165.0 - 1) * 100, LaenderMarkt.waehrungVeraenderung(yen)!!, 1e-9)
        assertEquals(10.0, LaenderMarkt.kursVeraenderung(reihe(0L to 100.0, 360 * tag to 110.0))!!, 1e-9)
        // zu kurze Reihe oder leer
        assertNull(LaenderMarkt.kursVeraenderung(reihe(0L to 100.0, 30 * tag to 110.0)))
        assertNull(LaenderMarkt.waehrungVeraenderung(emptyList()))
        assertNull(LaenderMarkt.kursVeraenderung(reihe(0L to 0.0, 360 * tag to 110.0)))
    }

    @Test
    fun tabellen() {
        assertEquals("EUR", LaenderMarkt.waehrungsCode("DE"))
        assertEquals("EUR", LaenderMarkt.waehrungsCode("BG"))
        assertEquals("JPY", LaenderMarkt.waehrungsCode("JP"))
        assertEquals("EURJPY=X", LaenderMarkt.kursSymbol("JPY"))
        assertNull(LaenderMarkt.waehrungsCode("AQ"))
        assertEquals(44, LaenderMarkt.ETF.values.sumOf { it.size })
        assertEquals(listOf("MCHI", "FXI"), LaenderMarkt.ETF["CN"]!!.map { it.symbol })
        assertEquals("EWG", LaenderMarkt.ETF["DE"]!!.single().symbol)
        // jedes Euroland hat Euro, kein Euroland steht in der FRED-Liste
        assertTrue(LaenderMarkt.EURO.all { LaenderMarkt.waehrungsCode(it) == "EUR" })
        assertTrue(LaenderMarkt.EURO.none { it in LaenderMarkt.FRED_LAENDER })
        assertEquals(12, LaenderMarkt.STRUKTUR.size)
        assertEquals(listOf("FR.INR.LEND", "FR.INR.RINR"), LaenderMarkt.ZINS_WELTBANK.map { it.weltbank })
        assertEquals("ILS", LaenderMarkt.waehrungsCode("PS"))
    }

    @Test
    fun jahrDerWeltbank() {
        fun w(j: Int) = Weltbank.Wert(j, 1.0)
        // 10 Laender mit 2025, 2 mit 2024, eines mit 2019: ueblich 2025
        val r = Weltbank.Reihe("X", 0L, (1..10).associate { "A$it" to w(2025) } + mapOf("B1" to w(2024), "B2" to w(2024), "C1" to w(2019)))
        assertEquals(2025, LaenderMarkt.ueblichesJahr(r))
        assertFalse(LaenderMarkt.jahrZeigen(w(2025), 2025, 2026))
        assertTrue(LaenderMarkt.jahrZeigen(w(2024), 2025, 2026))
        assertTrue(LaenderMarkt.jahrZeigen(w(2019), 2025, 2026))
        // uebliches Jahr, aber alt (Rohstoffrenten 2021): immer zeigen
        assertTrue(LaenderMarkt.jahrZeigen(w(2021), 2021, 2026))
        // uebliches Jahr 2024, noch hoechstens zwei Jahre alt: nicht zeigen
        assertFalse(LaenderMarkt.jahrZeigen(w(2024), 2024, 2026))
        assertNull(LaenderMarkt.ueblichesJahr(Weltbank.Reihe("X", 0L, emptyMap())))
        assertNotNull(LaenderMarkt.ueblichesJahr(Weltbank.Reihe("X", 0L, mapOf("A" to w(2020)))))
    }
}
