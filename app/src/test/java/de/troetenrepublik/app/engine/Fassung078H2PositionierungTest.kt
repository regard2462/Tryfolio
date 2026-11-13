package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Positionierung an den Terminmaerkten mit dem ganzen Archiv der CFTC: Datei lesen, neue Wochen anhaengen, Rang in der
// Geschichte und „so stark netto long wie seit … nicht“.
class Fassung078H2PositionierungTest {

    private val tag = 86_400_000L
    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private fun ms(d: String) = LocalDate.parse(d).toEpochDay() * tag

    @Test fun archivMitAusnahmenVomWochenabstand() {
        val a = Positionierung.archivLesen("""{"stand":"2020-01-28","maerkte":{"gold":{"code":"088691","ab":"2020-01-07","x":[[2,14]],"a":[100,-50,200]}}}""")
        assertEquals(listOf(ms("2020-01-07") to 10.0, ms("2020-01-14") to -5.0, ms("2020-01-28") to 20.0), a["gold"])
        assertTrue(Positionierung.archivLesen("kaputt").isEmpty())
    }

    // Ausschnitt einer Antwort der Schnittstelle (zwei Maerkte, zwei Wochen)
    private val antwort = """[
        {"cftc_contract_market_code":"088691","report_date_as_yyyy_mm_dd":"2026-11-03T00:00:00.000","open_interest_all":"400000","noncomm_positions_long_all":"250000","noncomm_positions_short_all":"50000"},
        {"cftc_contract_market_code":"13874A","report_date_as_yyyy_mm_dd":"2026-11-03T00:00:00.000","open_interest_all":"2000000","noncomm_positions_long_all":"300000","noncomm_positions_short_all":"500000"},
        {"cftc_contract_market_code":"088691","report_date_as_yyyy_mm_dd":"2026-11-10T00:00:00.000","open_interest_all":"500000","noncomm_positions_long_all":"300000","noncomm_positions_short_all":"50000"}
    ]"""

    @Test fun neueWochenJeMarkt() {
        val m = Positionierung.lesenMehrere(antwort)
        assertEquals(setOf("088691", "13874A"), m.keys)
        assertEquals(listOf(ms("2026-11-03"), ms("2026-11-10")), m.getValue("088691").map { it.tag })
        assertEquals(50.0, m.getValue("088691").last().anteil, 1e-9)
        assertEquals(-10.0, m.getValue("13874A").single().anteil, 1e-9)
        val adresse = Positionierung.adresseSeit(listOf("088691", "13874A"), LocalDate.parse("2026-11-03"))
        assertTrue(adresse.contains("%27088691%27,%2713874A%27"))
        assertTrue(adresse.contains("%272026-11-03%27"))
    }

    // Sechs Jahre Wochenwerte: Hoch von 40 am 03.01.2017, danach zwischen 0 und 20
    private fun archiv(): List<Pair<Long, Double>> {
        val start = LocalDate.parse("2016-01-05")
        return (0 until 300).map { i ->
            val d = start.plusWeeks(i.toLong())
            val w = if (d == LocalDate.parse("2017-01-03")) 40.0 else (i % 21).toDouble()
            d.toEpochDay() * tag to w
        }
    }

    @Test fun rangUndSeitAusArchivUndNeuerWoche() {
        val alt = archiv()
        val ende = alt.last().first
        val neu = listOf(Positionierung.Woche(ende + 7 * tag, 1000.0, 350.0, 50.0))
        val l = Positionierung.lageLang("gold", alt, neu, ende + 10 * tag)
        assertNotNull(l)
        l!!
        assertEquals(30.0, l.anteil, 1e-9)
        assertEquals(301, l.lang.size)
        assertEquals(Positionierung.DREI_JAHRE, l.verlauf.size)
        // In drei Jahren der hoechste Wert
        assertEquals(100, l.index)
        // In der ganzen Geschichte lag nur die Woche vom 03.01.2017 hoeher
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, ms("2017-01-03")), l.seit)
        assertEquals(Math.round(300 * 100.0 / 301).toInt(), l.rang)
        assertEquals(alt.first().first, l.ab)
    }

    @Test fun rekordNurMitArchiv() {
        val alt = archiv()
        val ende = alt.last().first
        val hoch = listOf(Positionierung.Woche(ende + 7 * tag, 1000.0, 600.0, 50.0))
        val l = Positionierung.lageLang("gold", alt, hoch, ende + 10 * tag)!!
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, alt.first().first, rekord = true), l.seit)
        assertEquals(100, l.rang)
        // Ohne Archiv (nur Wochen aus dem Netz): kein Rang, kein Rekord, keine lange Reihe
        val nurNetz = alt.map { Positionierung.Woche(it.first, 1000.0, 500.0 + it.second * 10, 500.0) } + hoch
        val o = Positionierung.lageLang("gold", emptyList(), nurNetz, ende + 10 * tag)!!
        assertNull(o.rang)
        assertNull(o.seit)
        assertTrue(o.lang.isEmpty())
    }

    @Test fun altesArchivOhneNeueWochenZaehltNicht() {
        val alt = archiv()
        val ende = alt.last().first
        assertNotNull(Positionierung.lageLang("gold", alt, emptyList(), ende + 30 * tag))
        assertNull(Positionierung.lageLang("gold", alt, emptyList(), ende + (Positionierung.ARCHIV_GUELTIG_TAGE + 1) * tag))
    }

    @Test fun mitgelieferteDateiTraegtAlleMaerkte() {
        val text = asset(Positionierung.ARCHIV).readText()
        val a = Positionierung.archivLesen(text)
        assertEquals(Positionierung.MAERKTE.map { it.first }.toSet(), a.keys)
        assertTrue(text.length < 300_000)
        for ((k, reihe) in a) {
            assertTrue(k, reihe.size >= 400)
            assertTrue(k, reihe.zipWithNext().all { (x, y) -> y.first > x.first })
            assertTrue(k, reihe.all { it.second in -100.0..100.0 })
        }
        assertEquals(1986, LocalDate.ofEpochDay(a.getValue("gold").first().first / tag).year)
        // Der Euro beginnt mit seiner Einfuehrung
        assertEquals(1999, LocalDate.ofEpochDay(a.getValue("euro").first().first / tag).year)
        // Jede Aussage „seit“ stimmt mit einer schlichten Suche ueberein
        for ((k, reihe) in a) {
            val l = Positionierung.lageLang(k, reihe, emptyList(), reihe.last().first) ?: continue
            val s = l.seit ?: continue
            val v = reihe.last().second
            if (s.rekord) continue
            val zwischen = reihe.dropLast(1).filter { it.first > s.seit }
            if (s.art == StandSeit.Art.HOCH) assertTrue(k, zwischen.all { it.second <= v } && reihe.first { it.first == s.seit }.second >= v)
            else assertTrue(k, zwischen.all { it.second >= v } && reihe.first { it.first == s.seit }.second <= v)
            assertTrue(k, reihe.last().first - s.seit >= StandSeit.JAHR_MS)
        }
        assertTrue(Datenbuendel.SCHEMA.containsKey(Positionierung.ARCHIV))
        assertTrue(Positionierung.ARCHIV in Alter.dateien())
    }
}
