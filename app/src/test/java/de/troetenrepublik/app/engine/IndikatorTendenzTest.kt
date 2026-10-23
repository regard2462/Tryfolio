package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Indikatoren.Deutung
import de.troetenrepublik.app.engine.Indikatoren.Tendenz
import org.junit.Assert.assertEquals
import org.junit.Test

// Einordnung der Indikatoren: positiv, negativ, neutral oder gemischt.
class IndikatorTendenzTest {

    private fun t(art: String, stufe: Int, lage: String) = Indikatoren.tendenz(Deutung(art, stufe, lage, 0.0))

    @Test
    fun bollinger() {
        // im oberen Teil gestiegen, im unteren gefallen, ausserhalb weit gelaufen
        assertEquals(Tendenz.POSITIV, t("bb", 1, "nahe_oben"))
        assertEquals(Tendenz.NEGATIV, t("bb", -1, "nahe_unten"))
        assertEquals(Tendenz.NEUTRAL, t("bb", 0, "mitte"))
        assertEquals(Tendenz.GEMISCHT, t("bb", 2, "oben"))
        assertEquals(Tendenz.GEMISCHT, t("bb", 2, "unten"))
    }

    @Test
    fun trendfolgerUndOszillatoren() {
        assertEquals(Tendenz.POSITIV, t("sma50", 1, "ueber"))
        assertEquals(Tendenz.NEGATIV, t("sma200", -1, "unter"))
        assertEquals(Tendenz.POSITIV, t("sar", 1, "aufwaerts"))
        assertEquals(Tendenz.NEGATIV, t("ichimoku", -1, "unter"))
        assertEquals(Tendenz.NEUTRAL, t("ichimoku", 0, "in"))
        assertEquals(Tendenz.NEGATIV, t("macd", -1, "faellt"))
        assertEquals(Tendenz.GEMISCHT, t("rsi", 2, "ueberverkauft"))
        assertEquals(Tendenz.GEMISCHT, t("stoch", 2, "ueberkauft"))
        assertEquals(Tendenz.NEUTRAL, t("rsi", 0, "neutral"))
        assertEquals(Tendenz.POSITIV, t("obv", 1, "bestaetigt_auf"))
        assertEquals(Tendenz.GEMISCHT, t("obv", 2, "schwach_ab"))
        assertEquals(Tendenz.NEUTRAL, t("obv", 0, "seitwaerts_ab"))
        // ATR sagt nie etwas ueber die Richtung, auch wenn es unruhig ist
        assertEquals(Tendenz.NEUTRAL, t("atr", 2, "unruhig"))
        assertEquals(Tendenz.NEUTRAL, t("atr", 0, "normal"))
    }
}
