package de.troetenrepublik.app.market

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// 0.79 Regelpruefung Teil 7: kein Geviertstrich in Nachrichtentiteln, Texte ohne Semikolon und ohne Fehlalarm.
class Fassung079R7TitelTest {
    @Test fun geviertstrichWirdMittelpunkt() {
        assertEquals("ATYM Aktienkurs und Chart · LSE:ATYM", Rss.titelBereinigen("ATYM Aktienkurs und Chart — LSE:ATYM"))
        assertEquals("–", Rss.titelBereinigen("—"))
        assertEquals("Kurs 5–7", Rss.titelBereinigen("Kurs 5—7"))
    }

    @Test fun anleitungOhneIntroUndPillen() {
        for (s in listOf(De, En)) {
            assertEquals("", s.guideAllesText)
            assertFalse(s.guideFunktionen(true, true).joinToString { it.second }.contains("Pillen"))
            for ((_, t) in s.guideFunktionen(true, true)) assertFalse(t, t.contains(';'))
        }
    }

    @Test fun begriffeEinheitlich() {
        assertEquals("Median", De.vbMedian)
        assertEquals("Median", En.vbMedian)
        assertFalse(De.nyBilanz("30", 8, 9, 2, 1968).contains("Fehlalarm"))
        assertFalse(En.nyBilanz("30", 8, 9, 2, 1968).contains("false alarm"))
    }
}
