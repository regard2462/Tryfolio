package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Tagesrueckblick.Bewegung
import de.troetenrepublik.app.engine.Tagesrueckblick.Markt
import de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick
import de.troetenrepublik.app.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// Fazit des Tagesrueckblicks: Depot gegen den MSCI World, staerkste Bewegung, Branchen; ohne Depot der Markt.
class RueckblickFazitTest {

    private val markt = listOf(Markt("DAX", 0.8), Markt("WORLD", 0.4), Markt("SP500", 0.6))
    private val branchen = listOf(Markt("SMH", 2.1), Markt("KBE", 0.3), Markt("XLU", -1.0))

    @Test
    fun mitDepot() {
        I18n.lang = Lang.DE
        val r = Rueckblick(
            0L, 0L, markt, depotWert = 10_000.0, depotAenderung = 130.0, depotProzent = 1.3,
            bewegungen = listOf(Bewegung("NVDA", "Nvidia", 4.1, 80.0)), branchen = branchen,
        )
        val f = rueckblickFazit(r)
        assertEquals(
            "Ein starker Tag: Dein Depot stieg um 1,3 % (+130,00 €) und lag damit 0,9 Prozentpunkte vor dem MSCI World. " +
                "Am stärksten bewegte sich Nvidia (+4,1 %). Stärkste Branche: Halbleiter (+2,1 %), schwächste: Versorger (−1,0 %).",
            f.replace(' ', ' '),
        )
        // Fuer die Mitteilung ohne Branchen
        assertFalse(rueckblickFazit(r, branchen = false).contains("Branche"))
        // Hinter dem Markt und etwa gleichauf
        assertEquals(
            "Ein ruhiger Tag: Dein Depot fiel um 0,2 % (−20,00 €) und lag damit 0,6 Prozentpunkte hinter dem MSCI World.",
            rueckblickFazit(r.copy(depotProzent = -0.2, depotAenderung = -20.0, bewegungen = emptyList()), branchen = false).replace(' ', ' '),
        )
        assertEquals(
            "Ein ruhiger Tag: Dein Depot stieg um 0,5 % (+50,00 €) und lief etwa wie der MSCI World.",
            rueckblickFazit(r.copy(depotProzent = 0.5, depotAenderung = 50.0, bewegungen = emptyList()), branchen = false).replace(' ', ' '),
        )
    }

    @Test
    fun ohneDepotUndEnglisch() {
        I18n.lang = Lang.DE
        val r = Rueckblick(0L, 0L, markt)
        assertEquals("Der MSCI World schloss 0,4 % höher.", rueckblickFazit(r).replace(' ', ' '))
        I18n.lang = Lang.EN
        assertEquals("The MSCI World closed 0.4% higher.", rueckblickFazit(r).replace(' ', ' '))
        I18n.lang = Lang.DE
    }
}
