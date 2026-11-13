package de.troetenrepublik.app.engine

// Was ein Kauf oder Verkauf einer Person aus den Meldungen heute hergibt, ohne etwas zu erfinden: der Kurs seit dem Geschaeft
// aus Sicht der Person, der Rest im Bestand mit heutigem Wert und der Gewinn nur dort, wo der Einstandspreis belegt ist
// (Kauf am Markt in den Meldungen oder Ausuebungspreis einer Option am Tag des Verkaufs). Ein Form 4 nennt fuer frei
// gekaufte Aktien keinen Einstandspreis; ohne Beleg gibt es keinen Gewinn.
object GeschaeftsBilanz {

    // Gewinn (betrag, negativ bei Verlust) und Prozent (0,35 = 35 % ueber Einstand).
    data class Gewinn(val betrag: Double, val prozent: Double)

    // Kursabstand seit dem Geschaeft (heute gegen Kauf- oder Verkaufskurs, -0,23 = 23 % darunter) aus Sicht der Person:
    // positiv ist gut fuer sie. Nach einem Verkauf ist ein gefallener Kurs gut (richtig verkauft), nach einem Kauf ein gestiegener.
    fun sicht(abstand: Double, verkauf: Boolean): Double = if (verkauf) -abstand else abstand

    // Wert des Rests im Bestand zum heutigen Kurs; null ohne Bestand oder Kurs.
    fun restWert(stueck: Double?, kurs: Double?): Double? =
        if (stueck == null || kurs == null || stueck <= 0 || kurs <= 0) null else stueck * kurs

    // Wert eines Kaufs heute: der Kaufbetrag mal (1 + Abstand).
    fun kaufWertHeute(betrag: Double, abstand: Double): Double = betrag * (1 + abstand)

    // Gewinn eines Verkaufs gegen einen belegten Einstandspreis je Stueck (Erloes durch Verkaufskurs ergibt die Stueckzahl).
    fun gewinn(erloes: Double, verkaufskurs: Double, einstand: Double): Gewinn? {
        if (erloes <= 0 || verkaufskurs <= 0 || einstand <= 0) return null
        return Gewinn(erloes - erloes / verkaufskurs * einstand, verkaufskurs / einstand - 1)
    }

    // Gewinn eines ganz verkauften Kaufs: kaufBetrag ist der Betrag zum Einstiegskurs, verkaufskurs der mittlere Verkaufskurs.
    fun gewinnAusKauf(kaufBetrag: Double, einstieg: Double, verkaufskurs: Double?): Gewinn? {
        if (verkaufskurs == null || kaufBetrag <= 0 || einstieg <= 0 || verkaufskurs <= 0) return null
        val p = verkaufskurs / einstieg - 1
        return Gewinn(kaufBetrag * p, p)
    }
}
