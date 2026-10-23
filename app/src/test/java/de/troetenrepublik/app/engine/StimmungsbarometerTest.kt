package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Stimmungsbarometer.Stufe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Stimmungsbarometer: Antworten im Format von CNN, alternative.me und Tradestie, erfundene Werte.
class StimmungsbarometerTest {

    @Test
    fun stufen() {
        assertEquals(Stufe.EXTREME_ANGST, Stimmungsbarometer.stufe(10.0))
        assertEquals(Stufe.ANGST, Stimmungsbarometer.stufe(28.0))
        assertEquals(Stufe.NEUTRAL, Stimmungsbarometer.stufe(50.0))
        assertEquals(Stufe.GIER, Stimmungsbarometer.stufe(63.0))
        assertEquals(Stufe.EXTREME_GIER, Stimmungsbarometer.stufe(90.0))
        assertEquals(Stufe.EXTREME_ANGST, Stimmungsbarometer.stufe("extreme fear"))
        assertEquals(Stufe.GIER, Stimmungsbarometer.stufe("Greed"))
        assertNull(Stimmungsbarometer.stufe("unbekannt"))
    }

    @Test
    fun cnn() {
        val text = """{"fear_and_greed": {"score": 28.0571428571429, "rating": "fear", "timestamp": "2026-10-21T07:20:01+00:00",
            "previous_close": 27.2, "previous_1_week": 26.65, "previous_1_month": 34.17, "previous_1_year": 28.54},
            "fear_and_greed_historical": {"timestamp": 1792567201000.0, "score": 28.05, "rating": "fear", "data": [
              {"x": 1790899200000.0, "y": 39.97, "rating": "fear"}, {"x": 1790812800000.0, "y": 28.97, "rating": "fear"}]},
            "market_momentum_sp500": {"timestamp": 1792529145000.0, "score": 33.8, "rating": "fear", "data": []},
            "market_volatility_vix": {"score": 80.0, "rating": "extreme greed", "data": []}}"""
        val b = Stimmungsbarometer.cnnLesen(text)!!
        assertEquals(28.057, b.wert, 0.001)
        assertEquals(Stufe.ANGST, b.stufe)
        assertEquals(27.2, b.vortag!!, 1e-9)
        assertEquals(28.54, b.jahr!!, 1e-9)
        assertEquals(listOf(1790812800000L, 1790899200000L), b.verlauf.map { it.first })
        assertEquals(listOf("market_momentum_sp500", "market_volatility_vix"), b.teile.map { it.schluessel })
        assertEquals(Stufe.EXTREME_GIER, b.teile[1].stufe)
        assertEquals(java.time.Instant.parse("2026-10-21T07:20:01Z").toEpochMilli(), b.stand)
        // Die Sperrseite fuer Bots ist kein Index
        assertNull(Stimmungsbarometer.cnnLesen("I'm a teapot. You're a bot."))
    }

    @Test
    fun krypto() {
        val tage = (0 until 31).joinToString(",") { i -> """{"value": "${60 - i}", "value_classification": "Greed", "timestamp": "${1792540800 - i * 86400}"}""" }
        val b = Stimmungsbarometer.kryptoLesen("""{"name": "Fear and Greed Index", "data": [$tage], "metadata": {"error": null}}""")!!
        assertEquals(60.0, b.wert, 1e-9)
        assertEquals(Stufe.GIER, b.stufe)
        assertEquals(59.0, b.vortag!!, 1e-9)
        assertEquals(53.0, b.woche!!, 1e-9)
        assertEquals(30.0, b.monat!!, 1e-9)
        assertEquals(1792540800000L, b.stand)
        assertTrue(b.verlauf.first().first < b.verlauf.last().first)
        assertNull(Stimmungsbarometer.kryptoLesen("""{"data": []}"""))
    }

    @Test
    fun wallstreetbets() {
        val w = Stimmungsbarometer.wsbLesen(
            """[{"ticker": "AI", "sentiment": "Bullish", "sentiment_score": 0.05, "no_of_comments": 150},
                {"ticker": "XYZ", "sentiment": "Bearish", "sentiment_score": -0.13, "no_of_comments": 50},
                {"ticker": "ABC", "sentiment": "Bullish", "sentiment_score": 0.3, "no_of_comments": 0}]""",
        )
        assertEquals(3, w.size)
        val r = Stimmungsbarometer.wsbBarometer(w, 1L)!!
        assertEquals(75.0, r.barometer.wert, 1e-9)
        assertEquals("AI", r.werte.first().ticker)
        assertNull(Stimmungsbarometer.wsbBarometer(emptyList(), 1L))
        assertTrue(Stimmungsbarometer.wsbLesen("""{"error": "Invalid date format"}""").isEmpty())
    }

    @Test
    fun beitraege() {
        assertNull(Stimmungsbarometer.beitragsBarometer(RedditAuswertung.Stimmung(2, 1, 10), 1L))
        val b = Stimmungsbarometer.beitragsBarometer(RedditAuswertung.Stimmung(6, 2, 10), 1L)!!
        assertEquals(75.0, b.wert, 1e-9)
    }
}
