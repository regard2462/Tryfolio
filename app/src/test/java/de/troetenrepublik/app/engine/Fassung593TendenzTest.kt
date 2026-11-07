package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Tendenz.Richtung
import org.junit.Assert.assertEquals
import org.junit.Test

class Fassung593TendenzTest {
    @Test
    fun zinsenAusDenAusgaengen() {
        val fed = Tendenz.erwartung("fed", listOf("25 bps decrease" to 0.8, "No change" to 0.2), null)
        assertEquals(Tendenz.Lage(Richtung.BULLISH, Tendenz.AKTIEN), fed)
        assertEquals(Richtung.NEUTRAL, Tendenz.erwartung("fed", listOf("No change" to 0.9), null).richtung)
        assertEquals(Richtung.BEARISH, Tendenz.erwartung("ezb", listOf("25 bps increase" to 0.6, "No change" to 0.4), null).richtung)
        assertEquals(Tendenz.AKTIEN_EU, Tendenz.erwartung("ezb", emptyList(), null).ziel)
        // Ohne lesbaren Ausgang die Futures
        assertEquals(Richtung.BULLISH, Tendenz.erwartung("fed", listOf("???" to 0.9), -2.0).richtung)
        assertEquals(Richtung.BULLISH, Tendenz.erwartung("fed_jahr", listOf("3 (75 bps)" to 0.4, "2 (50 bps)" to 0.3), null).richtung)
        assertEquals(Richtung.BEARISH, Tendenz.erwartung("fed_jahr", listOf("0 (0 bps)" to 0.5, "1 (25 bps)" to 0.3), null).richtung)
        // Verneinungen und Wortteile
        assertEquals(0, Tendenz.zinsSchritt("No cut"))
        assertEquals(0, Tendenz.zinsSchritt("No hike"))
        assertEquals(null, Tendenz.zinsSchritt("Above threshold"))
        assertEquals(-1, Tendenz.zinsSchritt("50+ bps decrease"))
        assertEquals(Richtung.BEARISH, Tendenz.erwartung("fed_jahr", listOf("No cuts" to 0.6, "1 (25 bps)" to 0.3), -3.0).richtung)
    }

    @Test
    fun rezessionInflationOel() {
        assertEquals(Richtung.BEARISH, Tendenz.erwartung("rezession", listOf("Yes" to 0.6), null).richtung)
        assertEquals(Richtung.BULLISH, Tendenz.erwartung("rezession", listOf("Yes" to 0.1), null).richtung)
        // Kumulierte Schwellen: 3 % sicher, 4 % zu 60 %
        val kumuliert = listOf("≥3%" to 0.99, "≥4%" to 0.6, "≥5%" to 0.2)
        assertEquals(Tendenz.Lage(Richtung.BEARISH, Tendenz.ANLEIHEN), Tendenz.erwartung("inflation", kumuliert, null))
        assertEquals(Richtung.BULLISH, Tendenz.erwartung("inflation", listOf("≥3%" to 0.3, "≥4%" to 0.1), null).richtung)
        assertEquals(Richtung.BULLISH, Tendenz.erwartung("oel", listOf("↑ \$80" to 0.7, "↓ \$60" to 0.2), null).richtung)
        assertEquals(Richtung.BEARISH, Tendenz.erwartung("oel", listOf("↑ \$80" to 0.1, "↓ \$60" to 0.5), null).richtung)
        assertEquals(Tendenz.Lage(Richtung.NEUTRAL, Tendenz.AKTIEN), Tendenz.erwartung("wahlen", listOf("Democrats" to 0.6), null))
    }

    @Test
    fun lagerUndAmpeln() {
        fun lager(k: String, l: Fruehindikatoren.Lage) = Fruehindikatoren.Signal(k, Fruehwarnung.Ampel.GELB, 1.0, 0L, null, lage = l)
        assertEquals(Tendenz.Lage(Richtung.BULLISH, Tendenz.OEL), Tendenz.fruehsignal(lager(Fruehindikatoren.ROHOEL, Fruehindikatoren.Lage.KNAPP)))
        assertEquals(Richtung.BEARISH, Tendenz.fruehsignal(lager(Fruehindikatoren.GAS_EU, Fruehindikatoren.Lage.DARUEBER)).richtung)
        assertEquals(Richtung.BULLISH, Tendenz.fruehsignal(lager(Fruehindikatoren.DUERRE, Fruehindikatoren.Lage.DARUEBER)).richtung)
        val antraege = Fruehindikatoren.Signal(Fruehindikatoren.ERSTANTRAEGE, Fruehwarnung.Ampel.ROT, 1.0, 0L, 25.0)
        assertEquals(Tendenz.Lage(Richtung.BEARISH, Tendenz.AKTIEN), Tendenz.fruehsignal(antraege))
        assertEquals(Tendenz.Lage(Richtung.BULLISH, Tendenz.CHIPS), Tendenz.ersatz(Ersatzquellen.HALBLEITER, Fruehwarnung.Ampel.GRUEN))
        assertEquals(Richtung.NEUTRAL, Tendenz.containerfracht(1.0).richtung)
    }
}

class Fassung593KerzenTest {
    @Test
    fun kerzenWerdenGebuendelt() {
        val k = (0 until 10).map { de.troetenrepublik.app.data.Candle(it * 60_000L, 10.0 + it, 11.0 + it, 9.0 + it, 10.5 + it, 1.0) }
        val b = de.troetenrepublik.app.ui.kerzenBuendeln(k, 4)
        assertEquals(4, b.size)
        assertEquals(de.troetenrepublik.app.data.Candle(0L, 10.0, 13.0, 9.0, 12.5, 3.0), b[0])
        assertEquals(19.5, b.last().close, 1e-9)
        assertEquals(k, de.troetenrepublik.app.ui.kerzenBuendeln(k, 10))
    }
}
