package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.ui.Fmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// Beitraege zur Indexbewegung stehen in Punkten, nicht in Prozent.
class Fassung077S2PunkteTest {
    @After
    fun zurueck() {
        I18n.lang = Lang.DE
    }

    @Test fun deutschMitGeschuetztemLeerzeichen() {
        I18n.lang = Lang.DE
        assertEquals("+0,19 Pkt.", Fmt.signedPunkte(0.19))
        assertEquals("−0,10 Pkt.", Fmt.signedPunkte(-0.104))
    }

    @Test fun englisch() {
        I18n.lang = Lang.EN
        assertEquals("+0.19 pts", Fmt.signedPunkte(0.19))
        assertEquals("−0.10 pts", Fmt.signedPunkte(-0.104))
    }

    @Test fun nullOhneVorzeichen() {
        I18n.lang = Lang.DE
        assertEquals("0,00 Pkt.", Fmt.signedPunkte(0.001))
        assertEquals("0,00 Pkt.", Fmt.signedPunkte(-0.001))
    }

    @Test fun keinProzentzeichen() {
        I18n.lang = Lang.DE
        assertFalse(Fmt.signedPunkte(1.5).contains("%"))
    }
}
