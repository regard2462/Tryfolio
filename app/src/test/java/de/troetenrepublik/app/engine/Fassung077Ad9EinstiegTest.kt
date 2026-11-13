package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Beschriftung unter „Unter Einstiegskurs“: Investoren melden zum Quartalsstichtag, Kongress und Regierung zum Kauftag.
class Fassung077Ad9EinstiegTest {
    @After
    fun zurueck() { I18n.lang = Lang.DE }

    @Test
    fun investorenZeigenQuartal() {
        I18n.lang = Lang.DE
        assertEquals("Quartal bis 30.06.2026 · 18,14 Mio. €", S.cuQuartalBis("30.06.2026", "18,14 Mio. €"))
        assertEquals("Kauf am 30.06.2026 · 18,14 Mio. €", S.cuKaufAm("30.06.2026", "18,14 Mio. €"))
        assertTrue(S.cuErklaerung("EINSTIEG_INVESTOR").contains("Quartalsende"))
        assertFalse(S.cuErklaerung("EINSTIEG_PERSON").contains("Quartalsende"))
        I18n.lang = Lang.EN
        assertEquals("Quarter to 06/30/2026 · $18.14M", S.cuQuartalBis("06/30/2026", "$18.14M"))
        assertTrue(S.cuErklaerung("EINSTIEG_INVESTOR").contains("end of the quarter"))
        assertFalse(S.cuErklaerung("EINSTIEG_PERSON").contains("quarter"))
    }
}
