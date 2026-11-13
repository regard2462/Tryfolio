package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Crypto Fear & Greed ueber die ganze Zeit: Archiv lesen, geladene Tage anschliessen, „tiefster Stand seit …“.
class Fassung078H2KryptoArchivTest {

    private val tag = 86_400_000L
    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private fun ms(d: String) = LocalDate.parse(d).toEpochDay() * tag

    @Test fun archivMitFehlendenTagen() {
        val a = KryptoArchiv.lesen("""{"stand":"2018-02-04","ab":"2018-02-01","w":[30,15,null,24]}""")
        assertEquals(listOf(ms("2018-02-01") to 30.0, ms("2018-02-02") to 15.0, ms("2018-02-04") to 24.0), a)
        assertTrue(KryptoArchiv.lesen("{}").isEmpty())
    }

    @Test fun abfrageReichtBisZumArchiv() {
        val a = listOf(ms("2026-01-01") to 50.0)
        assertEquals(31, KryptoArchiv.limit(ms("2026-01-10"), a))
        assertEquals(103, KryptoArchiv.limit(ms("2026-04-11"), a))
        assertEquals(KryptoArchiv.MAX_TAGE, KryptoArchiv.limit(ms("2036-01-01"), a))
        assertEquals(31, KryptoArchiv.limit(ms("2026-01-10"), emptyList()))
    }

    // Antwort von alternative.me, neueste zuerst (gekuerzt)
    private val antwort = """{"name":"Fear and Greed Index","data":[
        {"value":"12","value_classification":"Extreme Fear","timestamp":"1767484800"},
        {"value":"20","value_classification":"Extreme Fear","timestamp":"1767398400"},
        {"value":"41","value_classification":"Fear","timestamp":"1767312000"}],"metadata":{"error":null}}"""

    @Test fun geladeneTageSchliessenAn() {
        // 02.01.2026 bis 04.01.2026
        val geladen = Stimmungsbarometer.kryptoLesen(antwort)!!.verlauf
        assertEquals(listOf(ms("2026-01-02"), ms("2026-01-03"), ms("2026-01-04")), geladen.map { it.first })
        // Archiv 2023 bis 02.01.2026: am 10.06.2024 stand der Index bei 10, sonst bei 50
        val start = LocalDate.parse("2023-01-01")
        val archiv = (0..LocalDate.parse("2026-01-02").toEpochDay() - start.toEpochDay()).map { i ->
            val d = start.plusDays(i)
            d.toEpochDay() * tag to if (d == LocalDate.parse("2024-06-10")) 10.0 else 50.0
        }
        val reihe = KryptoArchiv.reihe(archiv, geladen)!!
        assertEquals(archiv.size + 2, reihe.size)
        // Der geladene Wert gilt fuer den gemeinsamen Tag
        assertEquals(41.0, reihe.first { it.first == ms("2026-01-02") }.second, 0.0)
        assertTrue(reihe.zipWithNext().all { (a, b) -> b.first - a.first == tag })
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.TIEF, ms("2024-06-10")), KryptoArchiv.seit(reihe))
        // Luecke zwischen Archiv und geladenen Tagen: keine Reihe, keine Aussage
        assertNull(KryptoArchiv.reihe(archiv.dropLast(5), geladen))
        assertNull(KryptoArchiv.seit(null))
        assertNull(KryptoArchiv.reihe(emptyList(), geladen))
    }

    @Test fun rekordtiefUeberDieGanzeReihe() {
        val start = LocalDate.parse("2023-01-01")
        val archiv = (0L..800L).map { start.plusDays(it).toEpochDay() * tag to 40.0 + it % 7 }
        val reihe = archiv + (archiv.last().first + tag to 5.0)
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.TIEF, archiv.first().first, rekord = true), KryptoArchiv.seit(reihe))
    }

    @Test fun mitgelieferteDatei() {
        val a = KryptoArchiv.lesen(asset(KryptoArchiv.DATEI).readText())
        assertEquals(ms("2018-02-01"), a.first().first)
        assertTrue(a.size > 3000)
        assertTrue(a.all { it.second in 0.0..100.0 })
        assertTrue(a.zipWithNext().all { (x, y) -> y.first > x.first })
        KryptoArchiv.seit(a)?.takeIf { !it.rekord }?.let { s ->
            val v = a.last().second
            val zwischen = a.dropLast(1).filter { it.first > s.seit }
            if (s.art == StandSeit.Art.TIEF) assertTrue(zwischen.all { it.second >= v } && a.first { it.first == s.seit }.second <= v)
            else assertTrue(zwischen.all { it.second <= v } && a.first { it.first == s.seit }.second >= v)
        }
        assertTrue(Datenbuendel.SCHEMA.containsKey(KryptoArchiv.DATEI))
        assertTrue(KryptoArchiv.DATEI in Alter.dateien())
    }
}
