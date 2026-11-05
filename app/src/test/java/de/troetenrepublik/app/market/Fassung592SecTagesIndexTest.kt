package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Fassung 5.92: neueste Form-4-Meldungen aus den Tagesverzeichnissen der SEC (nur synthetische Datei).
class Fassung592SecTagesIndexTest {

    @Test
    fun adresseImQuartalsordner() {
        assertEquals("https://www.sec.gov/Archives/edgar/daily-index/2026/QTR4/form.20261027.idx", SecInsider.tagesIndex(LocalDate.of(2026, 10, 27)))
        assertEquals("https://www.sec.gov/Archives/edgar/daily-index/2026/QTR1/form.20260302.idx", SecInsider.tagesIndex(LocalDate.of(2026, 3, 2)))
        assertEquals("https://www.sec.gov/Archives/edgar/daily-index/2026/QTR3/form.20260701.idx", SecInsider.tagesIndex(LocalDate.of(2026, 7, 1)))
        assertFalse(SecInsider.tagesIndex(LocalDate.of(2026, 10, 27)).contains("cgi-bin"))
    }

    @Test
    fun handelstageOhneWochenendeUndFeiertag() {
        // Montag, 30.11.2026: davor Wochenende und Thanksgiving (26.11.)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 25)),
            SecInsider.letzteHandelstage(LocalDate.of(2026, 11, 30)),
        )
        // Sonntag: die drei Tage davor ab Freitag
        assertEquals(
            listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 21)),
            SecInsider.letzteHandelstage(LocalDate.of(2026, 10, 25)),
        )
        assertEquals(1, SecInsider.letzteHandelstage(LocalDate.of(2026, 10, 27), 1).size)
    }

    @Test
    fun nurForm4UndJedeMeldungEinmal() {
        val idx = """
            Description:           Daily Index of EDGAR Dissemination Feed by Form Type
            Last Data Received:    October 27, 2026

            Form Type   Company Name                                                  CIK         Date Filed  File Name
            ---------------------------------------------------------------------------------------------------------------------------------------------
            3           NEUE PERSON                                                   1999999     20261027    edgar/data/1999999/0001999999-26-000001.txt
            4           BEISPIEL CORP                                                 1000001     20261027    edgar/data/1000001/0001000001-26-000010.txt
            4           MUSTER JANE                                                   1888888     20261027    edgar/data/1888888/0001000001-26-000010.txt
            4           ANDERE INC                                                    1000002     20261027    edgar/data/1000002/0001000002-26-000020.txt
            4/A         BEISPIEL CORP                                                 1000001     20261027    edgar/data/1000001/0001000001-26-000011.txt
            8-K         BEISPIEL CORP                                                 1000001     20261027    edgar/data/1000001/0001000001-26-000012.txt
        """.trimIndent()
        val e = SecInsider.tagesIndexLesen(idx)
        assertEquals(listOf("0001000001-26-000010" to "1000001", "0001000002-26-000020" to "1000002"), e)
        assertTrue(SecInsider.tagesIndexLesen("").isEmpty())
        assertTrue(SecInsider.tagesIndexLesen("<html>Not Found</html>").isEmpty())
    }
}
