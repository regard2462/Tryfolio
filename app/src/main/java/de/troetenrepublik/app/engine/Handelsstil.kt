package de.troetenrepublik.app.engine

// Handelsstile nach Haltedauer (0.78). Die Regeln des Strategietests rechnen mit Tageskursen: Buy and Hold,
// Positionstrading (Trendfolge) und Swing Trading lassen sich pruefen, Daytrading und Scalping brauchen Kurse
// innerhalb des Tages und haben deshalb keine Regel.
enum class Handelsstil(val testbar: Boolean) {
    BUY_AND_HOLD(true),
    POSITION(true),
    SWING(true),
    DAYTRADING(false),
    SCALPING(false);

    companion object {
        // Mittlere Haltedauer in Handelstagen: bis hierher Swing Trading (rund sechs Wochen)
        const val SWING_BIS = 30.0

        // Bis hierher Positionstrading (ein Boersenjahr), darueber Buy and Hold
        const val POSITION_BIS = 252.0

        // Stil, dem die Regel nach ihrem Aufbau angehoert.
        // HALTEN: ein Kauf, kein Verkauf. SMA, KREUZ, MACD, DONCHIAN, MOMENTUM: investiert, solange der Trend
        // steht (Kurs ueber Durchschnitt, Ausbruch ueber das Hoch, Schluss ueber dem von vor einem Jahr).
        // RSI, BOLLINGER, RSI_KURZ: Kauf nach einem Rueckschlag, Verkauf nach der Erholung (ein Kursschwung).
        // RHYTHMUS: drei Wochen von sechzehn investiert. MONATSWECHSEL: vier Handelstage um das Monatsende.
        // ZIELVOLA: dauerhaft investiert, nur die Groesse der Position wechselt mit der Schwankung.
        fun von(art: StrategieLabor.Art): Handelsstil = when (art) {
            StrategieLabor.Art.HALTEN, StrategieLabor.Art.ZIELVOLA -> BUY_AND_HOLD
            StrategieLabor.Art.SMA, StrategieLabor.Art.KREUZ, StrategieLabor.Art.MACD,
            StrategieLabor.Art.DONCHIAN, StrategieLabor.Art.MOMENTUM -> POSITION
            StrategieLabor.Art.RSI, StrategieLabor.Art.BOLLINGER, StrategieLabor.Art.RHYTHMUS,
            StrategieLabor.Art.RSI_KURZ, StrategieLabor.Art.MONATSWECHSEL -> SWING
        }

        // Stil nach der mittleren Haltedauer je Geschaeft in Handelstagen. Unter einem Handelstag waere es
        // Daytrading; das kommt mit Tageskursen nur vor, wenn ein Stopp am Kauftag greift.
        fun nachHaltedauer(tage: Double): Handelsstil? = when {
            !tage.isFinite() || tage < 0 -> null
            tage < 1.0 -> DAYTRADING
            tage <= SWING_BIS -> SWING
            tage <= POSITION_BIS -> POSITION
            else -> BUY_AND_HOLD
        }

        // Stil eines Testergebnisses: Kaufen und Halten bleibt Buy and Hold, auch in einem kurzen Zeitraum. Sonst
        // zaehlt die gemessene mittlere Haltedauer; ohne Geschaefte gilt der Stil der Regel.
        fun von(art: StrategieLabor.Art, e: StrategieLabor.Ergebnis): Handelsstil {
            if (art == StrategieLabor.Art.HALTEN) return BUY_AND_HOLD
            val tage = StrategieLabor.kennzahlen(e.geschaefte)?.haltedauer ?: return von(art)
            return nachHaltedauer(tage) ?: von(art)
        }

        // Stil einer monatlichen Rotation: die gemessene mittlere Haltedauer je Wert. Ohne Geschaefte Positionstrading,
        // weil ein Wert mindestens bis zum naechsten Monatsende im Depot bleibt.
        fun rotation(e: StrategieLabor.Ergebnis): Handelsstil =
            StrategieLabor.kennzahlen(e.geschaefte)?.haltedauer?.let { nachHaltedauer(it) } ?: POSITION

        // Regeln nach Stil geordnet, in der Reihenfolge der Stile; die Reihenfolge in jeder Gruppe bleibt.
        fun <T> gruppen(liste: List<T>, art: (T) -> StrategieLabor.Art): List<Pair<Handelsstil, List<T>>> =
            entries.mapNotNull { s -> liste.filter { von(art(it)) == s }.takeIf { it.isNotEmpty() }?.let { s to it } }
    }
}
