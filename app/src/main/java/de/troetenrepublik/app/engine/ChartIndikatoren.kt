package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle

// Indikatoren des erweiterten Charts. Sie werden auf den gezeigten Kerzen gerechnet,
// damit sie zu jedem Zeitraum und jeder Kerzenbreite passen, auch im Tageschart.
object ChartIndikatoren {

    // Im Kurschart oder in einem eigenen Feld darunter.
    enum class Ort { KURS, UNTEN }

    enum class Art { LINIE, BALKEN, PUNKTE }

    // Farbe einer Linie: Platz in der Farbtabelle der Oberflaeche oder nach Richtung bzw. Vorzeichen.
    const val FARBE_RICHTUNG = -1
    const val FARBE_VORZEICHEN = -2

    // Eine Linie mit einem Wert je Kerze (null = noch keiner).
    data class Linie(val werte: List<Double?>, val farbe: Int, val art: Art = Art.LINIE)

    // stufen: Hilfslinien, etwa 30 und 70 beim RSI. min/max: feste Skala.
    data class Ergebnis(val linien: List<Linie>, val stufen: List<Double> = emptyList(), val min: Double? = null, val max: Double? = null)

    enum class Indikator(val key: String, val ort: Ort) {
        SMA20("sma20", Ort.KURS),
        SMA50("sma50", Ort.KURS),
        SMA200("sma200", Ort.KURS),
        EMA20("ema20", Ort.KURS),
        EMA50("ema50", Ort.KURS),
        BOLLINGER("bb", Ort.KURS),
        KELTNER("keltner", Ort.KURS),
        DONCHIAN("donchian", Ort.KURS),
        ICHIMOKU("ichimoku", Ort.KURS),
        SAR("sar", Ort.KURS),
        VWAP("vwap", Ort.KURS),
        VOLUMEN("volumen", Ort.UNTEN),
        RSI("rsi", Ort.UNTEN),
        MACD("macd", Ort.UNTEN),
        STOCH("stoch", Ort.UNTEN),
        ATR("atr", Ort.UNTEN),
        OBV("obv", Ort.UNTEN),
        CCI("cci", Ort.UNTEN),
        WILLIAMS("williams", Ort.UNTEN),
        MFI("mfi", Ort.UNTEN),
        ADX("adx", Ort.UNTEN),
        ROC("roc", Ort.UNTEN),
        AROON("aroon", Ort.UNTEN),
        CMF("cmf", Ort.UNTEN);

        companion object {
            fun von(key: String): Indikator? = entries.firstOrNull { it.key == key }
        }
    }

    // tagVon ordnet einem Zeitpunkt seinen Handelstag zu, gebraucht fuer den VWAP.
    fun rechnen(i: Indikator, k: List<Candle>, tagVon: (Long) -> Long): Ergebnis {
        val h = k.map { it.high }
        val l = k.map { it.low }
        val c = k.map { it.close }
        val v = k.map { it.volume }
        val I = Indikatoren
        return when (i) {
            Indikator.SMA20 -> Ergebnis(listOf(Linie(I.sma(c, 20), 0)))
            Indikator.SMA50 -> Ergebnis(listOf(Linie(I.sma(c, 50), 1)))
            Indikator.SMA200 -> Ergebnis(listOf(Linie(I.sma(c, 200), 2)))
            Indikator.EMA20 -> Ergebnis(listOf(Linie(I.ema(c, 20), 3)))
            Indikator.EMA50 -> Ergebnis(listOf(Linie(I.ema(c, 50), 4)))
            Indikator.BOLLINGER -> I.bollinger(c).let { b -> Ergebnis(listOf(Linie(b.oben, 5), Linie(b.mitte, 5), Linie(b.unten, 5))) }
            Indikator.KELTNER -> I.keltner(h, l, c).let { b -> Ergebnis(listOf(Linie(b.oben, 6), Linie(b.mitte, 6), Linie(b.unten, 6))) }
            Indikator.DONCHIAN -> I.donchian(h, l).let { b -> Ergebnis(listOf(Linie(b.oben, 7), Linie(b.mitte, 7), Linie(b.unten, 7))) }
            Indikator.ICHIMOKU -> I.ichimoku(h, l).let { m ->
                Ergebnis(listOf(Linie(m.tenkan, 1), Linie(m.kijun, 4), Linie(m.spanA, 3), Linie(m.spanB, 2)))
            }
            Indikator.SAR -> Ergebnis(listOf(Linie(I.sar(h, l).wert, 0, Art.PUNKTE)))
            Indikator.VWAP -> Ergebnis(listOf(Linie(I.vwap(k, tagVon), 8)))
            Indikator.VOLUMEN -> Ergebnis(listOf(Linie(v.map { it }, FARBE_RICHTUNG, Art.BALKEN)), min = 0.0)
            Indikator.RSI -> Ergebnis(listOf(Linie(I.rsi(c), 0)), listOf(30.0, 70.0), 0.0, 100.0)
            Indikator.MACD -> I.macd(c).let { m ->
                Ergebnis(listOf(Linie(m.histogramm, FARBE_VORZEICHEN, Art.BALKEN), Linie(m.linie, 1), Linie(m.signal, 0)), listOf(0.0))
            }
            Indikator.STOCH -> I.stochastik(h, l, c).let { s -> Ergebnis(listOf(Linie(s.k, 1), Linie(s.d, 0)), listOf(20.0, 80.0), 0.0, 100.0) }
            Indikator.ATR -> Ergebnis(listOf(Linie(I.atr(h, l, c), 2)))
            Indikator.OBV -> Ergebnis(listOf(Linie(I.obv(c, v), 1)))
            Indikator.CCI -> Ergebnis(listOf(Linie(I.cci(h, l, c), 3)), listOf(-100.0, 100.0))
            Indikator.WILLIAMS -> Ergebnis(listOf(Linie(I.williamsR(h, l, c), 4)), listOf(-80.0, -20.0), -100.0, 0.0)
            Indikator.MFI -> Ergebnis(listOf(Linie(I.mfi(h, l, c, v), 5)), listOf(20.0, 80.0), 0.0, 100.0)
            Indikator.ADX -> I.adx(h, l, c).let { a ->
                Ergebnis(listOf(Linie(a.adx, 0), Linie(a.plus, 9), Linie(a.minus, 10)), listOf(25.0), 0.0, 100.0)
            }
            Indikator.ROC -> Ergebnis(listOf(Linie(I.roc(c), 6)), listOf(0.0))
            Indikator.AROON -> I.aroon(h, l).let { a -> Ergebnis(listOf(Linie(a.auf, 9), Linie(a.ab, 10)), listOf(30.0, 70.0), 0.0, 100.0) }
            Indikator.CMF -> Ergebnis(listOf(Linie(I.cmf(h, l, c, v), 7)), listOf(0.0))
        }
    }
}
