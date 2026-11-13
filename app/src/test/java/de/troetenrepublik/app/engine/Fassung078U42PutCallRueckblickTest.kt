package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Rueckblick des Put/Call-Verhaeltnisses: Leser der Archivdatei (erfundene Zeilen im Format der Quelle), Rang, Median,
// Verteilung. Die Datei der Quelle liegt der App nicht bei.
class Fassung078U42PutCallRueckblickTest {

    // 300 Handelstage: Werte 0,40 bis 0,99 je fuenfmal, in zwei Schreibweisen des Datums und der Zahlen
    private fun csv(): String = buildString {
        append("Hinweistext der Quelle, mit Kommas, ueber dem Kopf,,,,\n")
        append(", PRODUCT: EQUITY,,EXCHANGE: Cboe,\n")
        append("DATE,CALL,PUT,TOTAL,P/C Ratio\n")
        var n = 0
        for (runde in 0 until 5) for (i in 0 until 60) {
            val v = 0.40 + i * 0.01
            val jahr = 2006 + n / 30
            val wert = String.format(java.util.Locale.ROOT, "%.2f", v)
            if (n % 2 == 0) append("${1 + n % 12}/${1 + n % 28}/$jahr,1000,640,1640,$wert\n") else append("${1 + n % 12}/0${1 + n % 9}/$jahr, 1000, 640, 1640, $wert\r\n")
            n++
        }
        append("kaputt,1,2,3,x\n13/1/2010,1,2,3,0.5\n1/1/2010,1,2,3,-1\n")
    }

    @Test
    fun leser() {
        val a = PutCallRueckblick.lesen(csv())!!
        assertEquals(300, a.werte.size)
        assertEquals(2006, a.vonJahr)
        assertEquals(2015, a.bisJahr)
        assertEquals(0.40, a.werte.first(), 1e-9)
        assertEquals(0.99, a.werte.last(), 1e-9)
        assertTrue(a.werte.zipWithNext().all { (x, y) -> x <= y })
        assertNull(PutCallRueckblick.lesen(""))
        assertNull(PutCallRueckblick.lesen("DATE,CALL,PUT,TOTAL,P/C Ratio\n11/1/2006,1,2,3,0.64\n"))
        assertNull(PutCallRueckblick.lesen("<html>403</html>"))
    }

    @Test
    fun rangUndMedian() {
        val a = PutCallRueckblick.lesen(csv())!!
        assertEquals(0.0, PutCallRueckblick.rang(a, 0.30), 1e-9)
        assertEquals(0.0, PutCallRueckblick.rang(a, 0.40), 1e-9)
        assertEquals(100.0, PutCallRueckblick.rang(a, 1.50), 1e-9)
        // Unter 0,70 liegen die 30 Werte 0,40 bis 0,69, also die Haelfte
        assertEquals(50.0, PutCallRueckblick.rang(a, 0.695), 1e-9)
        assertEquals(25.0, PutCallRueckblick.rang(a, 0.545), 1e-9)
        assertEquals(0.695, PutCallRueckblick.median(a), 1e-9)
    }

    @Test
    fun verteilung() {
        assertEquals(15, PutCallRueckblick.KLASSEN)
        assertEquals(0, PutCallRueckblick.klasse(0.10))
        assertEquals(0, PutCallRueckblick.klasse(0.35))
        assertEquals(1, PutCallRueckblick.klasse(0.40))
        assertEquals(3, PutCallRueckblick.klasse(0.50))
        assertEquals(9, PutCallRueckblick.klasse(0.80))
        assertEquals(14, PutCallRueckblick.klasse(1.05))
        assertEquals(14, PutCallRueckblick.klasse(2.40))
        val a = PutCallRueckblick.lesen(csv())!!
        val v = PutCallRueckblick.verteilung(a)
        assertEquals(15, v.size)
        assertEquals(100.0, v.sum(), 1e-9)
        assertEquals(0.0, v[0], 0.0)
        // Je Klasse fuenf Werte (0,40 bis 0,44 und so weiter) von 60
        for (i in 1..12) assertEquals(100.0 / 12, v[i], 1e-9)
        assertEquals(0.0, v[14], 0.0)
    }

    @Test
    fun nichtsDavonLiegtBei() {
        assertTrue(PutCallRueckblick.ADRESSE.startsWith("https://cdn.cboe.com/"))
        val assets = listOf("src/main/assets", "../app/src/main/assets").map { File(it) }.first { it.exists() }
        assertTrue(assets.walkTopDown().none { it.name.contains("equitypc") || it.name.contains("putcall") })
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("archive file of the equity put/call ratio (2006 to 2019) from the same site on the device and keeps it in memory only"))
    }
}
