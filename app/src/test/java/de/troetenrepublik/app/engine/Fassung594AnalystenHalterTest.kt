package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Halter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Analystenhaeuser gegen die groessten Halter (5.94) mit erfundenen Bestaenden.
class Fassung594AnalystenHalterTest {

    private fun h(name: String) = Halter.Eintrag(name, 1.0, 1e6, 1e8, 0L, null)

    private val halter = listOf(
        h("Vanguard Group Inc"), h("JPMorgan Chase & Co"), h("Bank of America Corporation"), h("Morgan Stanley"),
        h("Goldman Sachs Group Inc"), h("UBS Group AG"), h("Capital Research Global Investors"),
    )

    @Test
    fun kurznamenUndRechtsformen() {
        assertEquals("JPMorgan Chase & Co", AnalystenHalter.passend("JP Morgan", halter)?.name)
        assertEquals("Bank of America Corporation", AnalystenHalter.passend("BofA Securities", halter)?.name)
        assertEquals("Goldman Sachs Group Inc", AnalystenHalter.passend("Goldman Sachs", halter)?.name)
        assertEquals("UBS Group AG", AnalystenHalter.passend("UBS", halter)?.name)
        assertEquals("Morgan Stanley", AnalystenHalter.passend("Morgan Stanley", halter)?.name)
    }

    @Test
    fun keineFalschenTreffer() {
        // „Capital One“ ist nicht „Capital Research“, „Stanley“ allein nicht Morgan Stanley
        assertNull(AnalystenHalter.passend("Capital One", halter))
        assertNull(AnalystenHalter.passend("Stanley Black", halter))
        assertNull(AnalystenHalter.passend("Wells Fargo", halter))
    }
}
