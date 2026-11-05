package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Fassung 5.93: synchroner Lesezugriff auf den Optionsspeicher der Derivate-Karte. Ohne Abruf nur der
// Zwischenspeicher gelesen, daher ohne Netz pruefbar. Kein Eintrag heisst null (noch laden), Werte ohne
// US-Optionen gelten sofort als bekannt ohne Daten.
class Fassung593HebelCacheTest {

    private val repo = MarketRepository(MarketApi(), CoroutineScope(Job()))

    @Test fun usWertOhneEintragLiefertNichts() {
        assertNull(repo.optionVolumesCached("AAPL"))
        assertNull(repo.optionVolumesCached("NVDA"))
    }

    // Seit 5.94 suchen Aktien ohne US-Kuerzel ihre US-Notierung; vor dieser Suche heisst es noch laden.
    @Test fun auslandsaktieWartetAufUsNotierung() {
        assertNull(repo.optionVolumesCached("SAP.DE"))
    }

    @Test fun wertOhneUsOptionenGiltAlsBekanntOhneDaten() {
        assertEquals(Pair(true, null), repo.optionVolumesCached("^GDAXI"))
        assertEquals(Pair(true, null), repo.optionVolumesCached("BTC-EUR"))
    }
}
