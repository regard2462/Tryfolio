package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.LobbyWelt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Lobbyarbeit in Europa (0.77): die Spanne des Aufwands steht kurz in einer Zeile neben dem Register.
class Fassung077LobbySpanneTest {
    @Test
    fun gleicheEinheitWirdZusammengezogen() {
        assertEquals("8,00–9,00 Mio. €", LobbyWelt.spanne("8,00 Mio. €", "9,00 Mio. €"))
        assertEquals("280,0–290,0 Tsd. €", LobbyWelt.spanne("280,0 Tsd. €", "290,0 Tsd. €"))
        assertEquals("8,00–9,00 Mio. €", LobbyWelt.spanne("8,00 Mio. €", "9,00 Mio. €"))
    }

    @Test
    fun verschiedeneEinheitBleibtGetrennt() {
        assertNull(LobbyWelt.spanne("900,0 Tsd. €", "1,00 Mio. €"))
        assertNull(LobbyWelt.spanne("500", "600"))
    }
}
