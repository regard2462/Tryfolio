package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Tendenz.Richtung

// [Ausblick 0.70] Gesamtaussage im Reiter Ausblick: aus den Tendenzen aller Eintraege (Tendenz.kt) wird je Markt und
// Zeithorizont eine Richtung mit Staerke. Jeder Eintrag zaehlt mit einem Gewicht aus Verlaesslichkeit der Quelle
// und Aktualitaet des Stands, doppelt zaehlende Signale (gleiche Gruppe) werden zu einem zusammengefasst. Zeigt eine
// Gewichtung der Signale, keine Prognose und keine Empfehlung.
object Ausblickgesamt {
    enum class Markt { AKTIEN, OEL, GAS, ANLEIHEN, GOLD, GETREIDE }
    enum class Horizont { KURZ, MITTEL }
    enum class Urteil { AUFWAERTS, SEITWAERTS, ABWAERTS, UNEINHEITLICH, ZU_WENIG }
    enum class Staerke { SCHWACH, MAESSIG, DEUTLICH }

    // Ein Eintrag aus dem Reiter. ziel: Markt im Sinne von Tendenz.Lage.ziel. alter: Tage seit dem Stand der Daten.
    // zustand und wert: Anzeige in den staerksten Gruenden (Zustand in Worten, Wert dazu), leer ohne Angabe.
    data class Beitrag(
        val schluessel: String, val name: String, val ziel: String, val richtung: Richtung, val alter: Long = 0,
        val zustand: String = "", val wert: String = "",
    )

    // Mehrere Beitraege einer Gruppe zusammengefasst. wert: -1 (bearish) bis 1 (bullish).
    data class Einheit(
        val schluessel: String, val name: String, val markt: Markt, val wert: Double, val gewicht: Double,
        val horizonte: Set<Horizont>, val abschnitt: String, val mitglieder: Int, val zustand: String = "", val wertText: String = "",
    ) {
        val richtung: Richtung get() = if (wert >= SCHWELLE_EINHEIT) Richtung.BULLISH else if (wert <= -SCHWELLE_EINHEIT) Richtung.BEARISH else Richtung.NEUTRAL
    }

    data class Ergebnis(
        val markt: Markt, val horizont: Horizont?, val urteil: Urteil, val staerke: Staerke, val punktzahl: Double,
        val positiv: Int, val neutral: Int, val negativ: Int,
        val gewichtPositiv: Double, val gewichtNeutral: Double, val gewichtNegativ: Double,
        val treiberPositiv: List<Einheit>, val treiberNegativ: List<Einheit>,
    ) {
        val anzahl: Int get() = positiv + neutral + negativ
        private val summe: Double get() = gewichtPositiv + gewichtNeutral + gewichtNegativ
        // Anteile am Gesamtgewicht in ganzen Prozent; neutral ist der Rest, die drei ergeben immer 100.
        val prozentPositiv: Int get() = if (summe > 0) Math.round(gewichtPositiv / summe * 100).toInt() else 0
        val prozentNegativ: Int get() = if (summe > 0) Math.round(gewichtNegativ / summe * 100).toInt().coerceAtMost(100 - prozentPositiv) else 0
        val prozentNeutral: Int get() = if (summe > 0) 100 - prozentPositiv - prozentNegativ else 0
    }

    // Verlaesslichkeit und Takt je Quelle. basis: Gewicht vor der Frische (Marktpreise mit echtem Geld und amtliche
    // Wochenreihen 1,0 bis 1,2, Umfragen, Wetten auf Randthemen und Branchenzahlen 0,4 bis 0,8). takt: Tage zwischen
    // zwei Veroeffentlichungen. gruppe: gleiche Grundlage, zaehlt nur einmal (hoechstes Gewicht der Gruppe).
    data class Quelle(val basis: Double, val takt: Int, val horizonte: Set<Horizont>, val gruppe: String? = null)

    private val BEIDE = setOf(Horizont.KURZ, Horizont.MITTEL)
    private val MITTEL = setOf(Horizont.MITTEL)
    private val KURZ = setOf(Horizont.KURZ)

    private val QUELLEN: Map<String, Quelle> = mapOf(
        // Zinsen: Futures und Wetten haengen am selben Ereignis
        "zins" to Quelle(1.2, 7, BEIDE, "fed"),
        "fed" to Quelle(0.8, 7, BEIDE, "fed"),
        "fed_jahr" to Quelle(0.5, 7, BEIDE, "fed"),
        "ezb" to Quelle(0.6, 7, BEIDE, "ezb"),
        // Wetten und Zinskurve zielen auf dasselbe Ereignis
        "rezession" to Quelle(1.2, 7, MITTEL, "rezession"),
        // Gewicht nach der gemessenen Stufe 2 von 4 (AusblickStaerke), monatlich
        NyFedMessung.SCHLUESSEL to Quelle(0.6, 30, MITTEL, "rezession"),
        "inflation" to Quelle(0.8, 7, MITTEL),
        "oel" to Quelle(0.6, 7, BEIDE),
        "hormus" to Quelle(0.6, 7, MITTEL),
        "iran" to Quelle(0.6, 7, BEIDE),
        // Fruehe Signale (woechentlich)
        Fruehindikatoren.ROHOEL to Quelle(1.0, 7, BEIDE),
        Fruehindikatoren.GAS_US to Quelle(1.0, 7, BEIDE),
        Fruehindikatoren.GAS_EU to Quelle(1.0, 7, BEIDE),
        Fruehindikatoren.ERSTANTRAEGE to Quelle(1.0, 7, BEIDE),
        Fruehindikatoren.FLUGGAESTE to Quelle(0.4, 7, KURZ),
        Fruehindikatoren.DUERRE to Quelle(0.8, 7, BEIDE),
        // Ersatzquellen (monatlich) und weitere Quellen
        Ersatzquellen.LIEFERKETTEN to Quelle(0.7, 45, MITTEL),
        Ersatzquellen.FRACHT to Quelle(0.5, 45, MITTEL),
        Ersatzquellen.BAHN to Quelle(0.5, 45, BEIDE, "bahn"),
        "aar" to Quelle(0.5, 7, BEIDE, "bahn"),
        Ersatzquellen.HALBLEITER to Quelle(0.5, 45, MITTEL, "chips"),
        "wsts" to Quelle(0.5, 45, MITTEL, "chips"),
        "wci" to Quelle(0.4, 7, KURZ),
    )

    private val STANDARD = Quelle(0.5, 30, BEIDE)

    fun quelle(schluessel: String): Quelle = QUELLEN[schluessel] ?: STANDARD

    // Alle gesetzten Quellen mit Gewicht und Takt, fuer die Tabelle im Infotext.
    fun tabelle(): List<Pair<String, Quelle>> = QUELLEN.entries.map { it.key to it.value }

    // Abschnitt im Reiter, zu dem ein Eintrag gehoert (Schluessel der Listeneintraege in ui/Ausblick.kt)
    fun abschnitt(schluessel: String): String = when (schluessel) {
        "zins" -> "zins"
        "fed", "fed_jahr", "ezb", "rezession", "inflation", "oel", "hormus", "iran" -> "e-$schluessel"
        Fruehindikatoren.ROHOEL, Fruehindikatoren.GAS_US, Fruehindikatoren.GAS_EU, Fruehindikatoren.ERSTANTRAEGE,
        Fruehindikatoren.FLUGGAESTE, Fruehindikatoren.DUERRE, NyFedMessung.SCHLUESSEL -> "fruehsignale"
        Ersatzquellen.LIEFERKETTEN, Ersatzquellen.FRACHT, Ersatzquellen.BAHN, Ersatzquellen.HALBLEITER, "aar", "wsts", "wci" -> "ersatzsignale"
        else -> "kern"
    }

    // Markt eines Tendenz-Ziels. Branchen und Regionen zaehlen zum Aktienmarkt (Beitraege mit kleinem Basisgewicht).
    fun markt(ziel: String): Markt? = when (ziel) {
        Tendenz.AKTIEN, Tendenz.AKTIEN_EU, Tendenz.CHIPS, Tendenz.TRANSPORT, Tendenz.REEDEREIEN, Tendenz.FLUGLINIEN -> Markt.AKTIEN
        Tendenz.OEL, Tendenz.BENZIN, Tendenz.DIESEL -> Markt.OEL
        Tendenz.GAS_US, Tendenz.GAS_EU -> Markt.GAS
        Tendenz.ANLEIHEN -> Markt.ANLEIHEN
        Tendenz.GOLD -> Markt.GOLD
        Tendenz.GETREIDE -> Markt.GETREIDE
        else -> null
    }

    // Frische: bis anderthalb Takte nach der Veroeffentlichung voll, danach linear bis 0,4 nach vier Takten.
    fun frische(alter: Long, takt: Int): Double {
        val t = takt.coerceAtLeast(1).toDouble()
        val a = alter.coerceAtLeast(0).toDouble()
        return when {
            a <= t * 1.5 -> 1.0
            a >= t * 4 -> 0.4
            else -> 1.0 - 0.6 * (a - t * 1.5) / (t * 2.5)
        }
    }

    fun gewicht(b: Beitrag): Double = quelle(b.schluessel).basis * frische(b.alter, quelle(b.schluessel).takt)

    fun wert(r: Richtung): Double = when (r) { Richtung.BULLISH -> 1.0; Richtung.NEUTRAL -> 0.0; Richtung.BEARISH -> -1.0 }

    // Eintraege ohne Markt (etwa Wahlen) und ohne Gewicht fallen weg. Je Gruppe und Markt eine Einheit: Wert als nach
    // Gewicht gemittelte Richtung, Gewicht das groesste der Gruppe, Name und Abschnitt vom schwersten Mitglied.
    fun einheiten(beitraege: List<Beitrag>): List<Einheit> {
        val liste = beitraege.mapNotNull { b -> markt(b.ziel)?.let { m -> Triple(b, m, gewicht(b)) } }.filter { it.third > 0 }
        val gruppiert = liste.groupBy { (b, m, _) -> (quelle(b.schluessel).gruppe ?: b.schluessel) + "|" + m.name }
        return gruppiert.values.map { g ->
            val summe = g.sumOf { it.third }
            val schwer = g.maxByOrNull { it.third }!!
            val q = quelle(schwer.first.schluessel)
            Einheit(
                schluessel = schwer.first.schluessel, name = schwer.first.name, markt = schwer.second,
                wert = g.sumOf { wert(it.first.richtung) * it.third } / summe,
                gewicht = schwer.third, horizonte = g.flatMap { quelle(it.first.schluessel).horizonte }.toSet().ifEmpty { q.horizonte },
                abschnitt = abschnitt(schwer.first.schluessel), mitglieder = g.size,
                zustand = schwer.first.zustand, wertText = schwer.first.wert,
            )
        }
    }

    // Ab dieser Mittelung gilt eine zusammengefasste Einheit als positiv oder negativ.
    const val SCHWELLE_EINHEIT = 0.25
    // Ab dieser Punktzahl (gewichteter Mittelwert von -1 bis 1) zeigt die Richtung nach oben oder unten.
    const val SCHWELLE_RICHTUNG = 0.2
    // Beide Seiten mit mindestens diesem Anteil am Gesamtgewicht und keine klare Punktzahl: uneinheitlich.
    const val ANTEIL_GEGENSEITE = 0.25
    const val PUNKTZAHL_UNEINHEITLICH = 0.35

    // Mindestmenge fuer eine Aussage: Aktien aus vielen Quellen, andere Maerkte haben nur wenige Signale.
    private fun mindestEinheiten(m: Markt) = if (m == Markt.AKTIEN) 4 else 2
    private fun mindestGewicht(m: Markt) = if (m == Markt.AKTIEN) 2.5 else 1.0

    fun staerke(punktzahl: Double): Staerke = when {
        kotlin.math.abs(punktzahl) < 0.35 -> Staerke.SCHWACH
        kotlin.math.abs(punktzahl) < 0.6 -> Staerke.MAESSIG
        else -> Staerke.DEUTLICH
    }

    fun berechnen(beitraege: List<Beitrag>, markt: Markt, horizont: Horizont? = null): Ergebnis =
        auswerten(einheiten(beitraege), markt, horizont)

    fun auswerten(alle: List<Einheit>, markt: Markt, horizont: Horizont? = null): Ergebnis {
        val e = alle.filter { it.markt == markt && (horizont == null || horizont in it.horizonte) }
        val pos = e.filter { it.richtung == Richtung.BULLISH }
        val neg = e.filter { it.richtung == Richtung.BEARISH }
        val neu = e.filter { it.richtung == Richtung.NEUTRAL }
        val gp = pos.sumOf { it.gewicht }
        val gn = neg.sumOf { it.gewicht }
        val gz = neu.sumOf { it.gewicht }
        val gesamt = gp + gn + gz
        val punkt = if (gesamt > 0) e.sumOf { it.wert * it.gewicht } / gesamt else 0.0
        // Eine Richtung zeigt die Karte nur, wenn auch das Gewicht der Seite das der Gegenseite uebertrifft; so
        // widerspricht der Satz mit den Gewichtsanteilen nie der Richtung darueber.
        val beideSeiten = gesamt > 0 && gp / gesamt >= ANTEIL_GEGENSEITE && gn / gesamt >= ANTEIL_GEGENSEITE
        val urteil = when {
            e.size < mindestEinheiten(markt) || gesamt < mindestGewicht(markt) -> Urteil.ZU_WENIG
            beideSeiten && kotlin.math.abs(punkt) < PUNKTZAHL_UNEINHEITLICH -> Urteil.UNEINHEITLICH
            punkt >= SCHWELLE_RICHTUNG && gp > gn -> Urteil.AUFWAERTS
            punkt <= -SCHWELLE_RICHTUNG && gn > gp -> Urteil.ABWAERTS
            beideSeiten -> Urteil.UNEINHEITLICH
            else -> Urteil.SEITWAERTS
        }
        return Ergebnis(
            markt, horizont, urteil, staerke(punkt), punkt, pos.size, neu.size, neg.size, gp, gz, gn,
            pos.sortedByDescending { it.gewicht * it.wert }.take(3), neg.sortedByDescending { it.gewicht * -it.wert }.take(3),
        )
    }
}
