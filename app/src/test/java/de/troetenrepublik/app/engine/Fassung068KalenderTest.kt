package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.Quartalszahlen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Vorschau der Dividendentermine und Quartalszahlen im Reiter Bewegungen (0.68)
class Fassung068KalenderTest {
    @Test
    fun dividendenVorschau() {
        // sieben Werte ohne Eintrag im Verzeichnis, nur ZZQD ist eigen
        val liste = ('A'..'G').map { c ->
            NasdaqKalender.Dividende(
                symbol = "ZZQ$c", name = "Wert $c", exTag = 1L, zahltag = null, stichtag = null,
                betrag = null, jeJahr = null, angekuendigt = null, eigen = c == 'D',
            )
        }
        // ohne Alle bleiben nur die eigenen
        assertEquals(listOf("ZZQD"), NasdaqKalender.dividendenVorschau(liste, alle = false).map { it.symbol })
        // mit Alle fuenf Eintraege: eigene zuerst, danach nach Name
        val fuenf = NasdaqKalender.dividendenVorschau(liste, alle = true)
        assertTrue(fuenf.size == 5)
        assertEquals(listOf("ZZQD", "ZZQA", "ZZQB", "ZZQC", "ZZQE"), fuenf.map { it.symbol })
        // mit groesserer Vorschau alle sieben
        assertEquals(7, NasdaqKalender.dividendenVorschau(liste, alle = true, anzahl = 10).size)
    }

    @Test
    fun quartalszahlenVorschau() {
        // sieben Werte ohne Eintrag im Verzeichnis, wert je klein, nur ZZQA ist eigen
        val liste = ('A'..'G').mapIndexed { i, c ->
            val s = "ZZQ$c"
            Quartalszahlen.Termin(s, s, 1L, Quartalszahlen.Zeit.NACH, wert = (i + 1).toDouble(), eigen = c == 'A')
        }
        // ohne Alle bleiben nur die eigenen
        assertEquals(listOf("ZZQA"), Quartalszahlen.vorschau(liste, alle = false).map { it.symbol })
        // mit Alle fuenf Eintraege: eigene zuerst, danach nach wert
        assertEquals(
            listOf("ZZQA", "ZZQG", "ZZQF", "ZZQE", "ZZQD"),
            Quartalszahlen.vorschau(liste, alle = true).map { it.symbol },
        )
    }
}
