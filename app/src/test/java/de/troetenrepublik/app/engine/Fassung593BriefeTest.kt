package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: die ganze Textstelle zur Aktie im Brief (Umfeld eines Auszugs). Alle Texte sind selbst gebaut.
class Fassung593BriefeTest {

    @Test fun nachbarAbsaetzeMitDabei() {
        val text = "Erster Absatz mit ein paar Worten ueber das Quartal.\n" +
            "Zweiter Absatz nennt die Aktie Alpha und sagt Gutes.\n" +
            "Dritter Absatz schliesst den Brief ab."
        val u = Investorenbriefe.umfeld(text, "Zweiter Absatz nennt die Aktie")
        assertNotNull(u)
        assertEquals(
            "Erster Absatz mit ein paar Worten ueber das Quartal.\n\n" +
                "Zweiter Absatz nennt die Aktie Alpha und sagt Gutes.\n\n" +
                "Dritter Absatz schliesst den Brief ab.",
            u,
        )
    }

    @Test fun auszugMitAuslassungszeichenUndMehrfachLeerzeichen() {
        val text = "Anfang.\nDer  Satz   hat mehrfache   Leerzeichen und nennt Beta.\nEnde."
        val u = Investorenbriefe.umfeld(text, "… Satz hat mehrfache …")
        assertNotNull(u)
        assertTrue(u!!.contains("Der Satz hat mehrfache"))
    }

    @Test fun langerAbsatzWirdGekuerzt() {
        val absatz = (1..300).joinToString(" ") { if (it == 250) "Gamma" else "wort$it" }
        val text = "Kopf.\n$absatz\nFuss."
        val stelle = absatz.indexOf("Gamma")
        val auszug = absatz.substring(stelle, stelle + 20)
        val u = Investorenbriefe.umfeld(text, auszug)
        assertNotNull(u)
        assertTrue(u!!.contains("Gamma wort251"))
        assertTrue(Investorenbriefe.woerterZahl(u) <= 250)
    }

    @Test fun nachbarAbsaetzeNurSolangeEsPasst() {
        val mitte = "Der mittlere Absatz nennt Delta und bleibt kurz."
        val davor = (1..250).joinToString(" ") { "wort$it" }
        val danach = (1..100).joinToString(" ") { "nach$it" }
        val text = "$davor\n$mitte\n$danach"
        val u = Investorenbriefe.umfeld(text, "Der mittlere Absatz nennt Delta")
        assertNotNull(u)
        assertEquals("$mitte\n\n$danach", u)
    }

    @Test fun auszugNichtImTextUndLeererAuszug() {
        assertNull(Investorenbriefe.umfeld("Ganz anderer Text.", "Diese Stelle gibt es nicht."))
        assertNull(Investorenbriefe.umfeld("Irgendein Text.", ""))
        assertNull(Investorenbriefe.umfeld("Irgendein Text.", "… "))
    }
}
