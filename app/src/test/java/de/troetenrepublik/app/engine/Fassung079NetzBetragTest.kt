package de.troetenrepublik.app.engine

import de.troetenrepublik.app.ui.NetzAnzeige
import org.junit.Test
import org.junit.Assert.assertEquals

class Fassung079NetzBetragTest {
    @Test
    fun betraegeOhneUnnoetigeNachkommastellen() {
        assertEquals("30 Mrd. $", NetzAnzeige.kurz("30,00 Mrd. $"))
        assertEquals("2,5 Mrd. $", NetzAnzeige.kurz("2,50 Mrd. $"))
        assertEquals("$36bn", NetzAnzeige.kurz("$36.00bn"))
        assertEquals("1,25 Mrd. $", NetzAnzeige.kurz("1,25 Mrd. $"))
        assertEquals("1,000", NetzAnzeige.kurz("1,000"))
    }
}
