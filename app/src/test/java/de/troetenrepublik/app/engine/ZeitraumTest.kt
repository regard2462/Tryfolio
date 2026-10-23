package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Range
import org.junit.Assert.assertEquals
import org.junit.Test

class ZeitraumTest {
    private val tag = Zeitraum.TAG
    private val heute = 20_745L // 19.10.2026
    private val jetzt = heute * tag + 15 * 3_600_000L

    @Test fun grenzenUmfassenGanzeTageUndEndenJetzt() {
        assertEquals((heute - 10) * tag to (heute - 4) * tag, Zeitraum.grenzen(heute - 10, heute - 5, jetzt))
        // vertauscht gewaehlt
        assertEquals((heute - 10) * tag to (heute - 4) * tag, Zeitraum.grenzen(heute - 5, heute - 10, jetzt))
        // bis heute: das Ende ist jetzt, nicht Mitternacht
        assertEquals((heute - 3) * tag to jetzt, Zeitraum.grenzen(heute - 3, heute, jetzt))
    }

    @Test fun intervallNachLaengeUndAlter() {
        fun iv(vonTag: Long, bisTag: Long): String {
            val (von, bis) = Zeitraum.grenzen(vonTag, bisTag, jetzt)
            return Zeitraum.intervall(von, bis, jetzt)
        }
        assertEquals("15m", iv(heute - 3, heute))
        assertEquals("30m", iv(heute - 30, heute))
        // 15 und 30 Minuten gibt es bei Yahoo nur fuer die letzten 60 Tage
        assertEquals("1h", iv(heute - 200, heute - 197))
        assertEquals("1h", iv(heute - 90, heute))
        assertEquals("1d", iv(heute - 400, heute))
        // Stunden nur fuer zwei Jahre
        assertEquals("1d", iv(heute - 1000, heute - 990))
        assertEquals("1wk", iv(heute - 3000, heute))
    }

    @Test fun grobReichtBisZumBeginn() {
        assertEquals(Range.W1, Zeitraum.grob(jetzt - 3 * tag, jetzt))
        assertEquals(Range.M1, Zeitraum.grob(jetzt - 20 * tag, jetzt))
        assertEquals(Range.M3, Zeitraum.grob(jetzt - 80 * tag, jetzt))
        assertEquals(Range.M6, Zeitraum.grob(jetzt - 150 * tag, jetzt))
        assertEquals(Range.Y1, Zeitraum.grob(jetzt - 200 * tag, jetzt))
        assertEquals(Range.MAX, Zeitraum.grob(jetzt - 800 * tag, jetzt))
    }

    @Test fun intraday() {
        assertEquals(true, Zeitraum.intraday("15m"))
        assertEquals(true, Zeitraum.intraday("1h"))
        assertEquals(false, Zeitraum.intraday("1d"))
        assertEquals(false, Zeitraum.intraday("1wk"))
    }
}
