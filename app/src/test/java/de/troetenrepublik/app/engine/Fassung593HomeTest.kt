package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.ui.StockTool
import de.troetenrepublik.app.ui.toolsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Startseite und Werkzeuge (seit 5.93): Dividenden stehen kompakt unter Finanzen, kein eigenes Werkzeug.
class Fassung593HomeTest {

    @Test
    fun dividendenKeinWerkzeugMehrBeiAktien() {
        val tools = toolsFor(AssetType.STOCK, false, null)
        assertTrue(tools.none { it.name == "DIVIDENDS" })
    }

    @Test
    fun dividendenKeinWerkzeugMehrBeiEtfs() {
        val tools = toolsFor(AssetType.ETF, true, null)
        assertTrue(tools.none { it.name == "DIVIDENDS" })
    }

    @Test
    fun aktienMitPositionBeginnenMitSteuerrechner() {
        val tools = toolsFor(AssetType.STOCK, true, null)
        assertEquals(StockTool.SELL_TAX, tools.first())
        // Der Rueckblick klappt seit 0.77 unter dem Chart auf
        assertTrue(tools.none { it == StockTool.LUMP_SUM })
    }
}
