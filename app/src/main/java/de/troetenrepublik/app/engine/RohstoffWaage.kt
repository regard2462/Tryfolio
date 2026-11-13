package de.troetenrepublik.app.engine

import kotlin.math.abs

// Fuer und Wider bei Rohstoffen (0.78): je Rohstoffgruppe eine feste Liste von Treibern, jeder aus einer messbaren Groesse,
// die die App schon laedt. Ein Treiber stuetzt den Preis, belastet ihn oder ist neutral; die Schwellen stehen unten als
// Konstanten. Ein Treiber ohne Wert fehlt im Ergebnis, nichts wird geschaetzt oder ersetzt.
//
// Treiber, Messgroesse, Regel:
//   REALZINS       Rendite inflationsgeschuetzter US-Anleihen, 10 Jahre (FRED DFII10). Aenderung in 63 Handelstagen (rund
//                  drei Monate): ab -0,15 Punkte stuetzt, ab +0,15 Punkte belastet. Edelmetalle zahlen keine Zinsen.
//   INFLATION      Inflationserwartung aus Anleihen, 10 Jahre (FRED T10YIE). Aenderung in 63 Handelstagen: ab +0,10 Punkte
//                  stuetzt, ab -0,10 Punkte belastet.
//   DOLLAR         Dollar-Index (DX-Y.NYB). Aenderung in 63 Handelstagen: ab -2 % stuetzt, ab +2 % belastet. Rohstoffe
//                  werden in Dollar gehandelt.
//   POSITIONIERUNG Nettoposition der Spekulanten (CFTC) als Rang in drei Jahren (0 bis 100): ab 90 belastet (alle schon
//                  dabei), bis 10 stuetzt.
//   TREND          Kurs gegen den Schnitt der letzten 200 Handelstage: ab +2 % stuetzt, ab -2 % belastet.
//   SAISON         Laufender Kalendermonat in der eigenen Kursgeschichte (mindestens 10 Jahre): in mindestens 65 % der Jahre
//                  im Plus und im Mittel positiv stuetzt, in hoechstens 35 % und im Mittel negativ belastet.
//   LAGER          US-Lager der EIA gegen den Fuenfjahresschnitt derselben Woche: ab -5 % stuetzt, ab +5 % belastet.
//   BOERSENLAGER   Lager der Metallboerse gegen den Vormonat: ab -10 % stuetzt, ab +10 % belastet.
//   TERMINKURVE    Spanne vom naechsten zum spaeteren Kontrakt auf ein Jahr gerechnet: ab +2 % (nahe Lieferung teurer,
//                  Backwardation) stuetzt, ab -10 % (steiles Contango) belastet.
//   ENSO           El Nino oder La Nina nach NOAA mit der typischen Preiswirkung je Rohstoff (Enso.WIRKUNG_*).
//   KONJUNKTUR     Rezessionswahrscheinlichkeit der New York Fed fuer die USA: ab 30 % belastet, bis 10 % stuetzt.
//   OPEC           Rohoelfoerderung der OPEC+ gegen den Vorjahresmonat (EIA): ab +2 % belastet, ab -2 % stuetzt.
//   WELTLAGER      Kommerzielle Oellager der OECD gegen den Vorjahresmonat (EIA): ab +2 % belastet, ab -2 % stuetzt.
object RohstoffWaage {

    enum class Gruppe { EDELMETALL, ENERGIE, INDUSTRIEMETALL, AGRAR, KORB }

    enum class Treiber { REALZINS, INFLATION, DOLLAR, POSITIONIERUNG, TREND, SAISON, LAGER, BOERSENLAGER, TERMINKURVE, ENSO, KONJUNKTUR, OPEC, WELTLAGER }

    enum class Richtung { STUETZT, BELASTET, NEUTRAL }

    const val FENSTER_TAGE = 63
    const val REALZINS_PUNKTE = 0.15
    const val INFLATION_PUNKTE = 0.10
    const val DOLLAR_PROZENT = 2.0
    const val POSITION_HOCH = 90
    const val POSITION_TIEF = 10
    const val TREND_TAGE = 200
    const val TREND_PROZENT = 2.0
    const val SAISON_JAHRE = 10
    const val SAISON_STARK = 0.65
    const val SAISON_SCHWACH = 0.35
    const val LAGER_PROZENT = 5.0
    const val BOERSENLAGER_PROZENT = 10.0
    const val KURVE_BACKWARDATION = 2.0
    const val KURVE_CONTANGO = -10.0
    const val REZESSION_HOCH = 30.0
    const val REZESSION_TIEF = 10.0
    const val FOERDERUNG_PROZENT = 2.0

    // Gruppe je Rohstoffschluessel aus Rohstoffe.schluessel; null, wenn der Wert kein Rohstoff mit eigenem Preis ist
    // (Fonds auf Minenaktien).
    fun gruppe(schluessel: String): Gruppe? = when (schluessel) {
        "GOLD", "SILBER", "PLATIN", "PALLADIUM" -> Gruppe.EDELMETALL
        "OEL", "GAS" -> Gruppe.ENERGIE
        "KUPFER" -> Gruppe.INDUSTRIEMETALL
        "WEIZEN", "MAIS", "KAFFEE", "KAKAO", "ZUCKER" -> Gruppe.AGRAR
        "KORB" -> Gruppe.KORB
        else -> null
    }

    // Treiber je Gruppe in der Reihenfolge der Anzeige. Silber, Platin und Palladium sind auch Industriemetalle: dort
    // zaehlt zusaetzlich die Konjunktur.
    fun treiber(schluessel: String): List<Treiber> = when (gruppe(schluessel)) {
        Gruppe.EDELMETALL -> listOf(Treiber.REALZINS, Treiber.DOLLAR, Treiber.INFLATION, Treiber.POSITIONIERUNG, Treiber.TREND, Treiber.SAISON) +
            if (schluessel == "GOLD") emptyList() else listOf(Treiber.KONJUNKTUR)
        Gruppe.ENERGIE -> listOf(Treiber.LAGER, Treiber.TERMINKURVE) +
            (if (schluessel == "OEL") listOf(Treiber.OPEC, Treiber.WELTLAGER, Treiber.KONJUNKTUR) else listOf(Treiber.ENSO)) +
            listOf(Treiber.DOLLAR, Treiber.POSITIONIERUNG, Treiber.TREND, Treiber.SAISON)
        Gruppe.INDUSTRIEMETALL -> listOf(Treiber.BOERSENLAGER, Treiber.TERMINKURVE, Treiber.KONJUNKTUR, Treiber.DOLLAR, Treiber.POSITIONIERUNG, Treiber.TREND, Treiber.SAISON)
        Gruppe.AGRAR -> listOf(Treiber.ENSO, Treiber.DOLLAR, Treiber.POSITIONIERUNG, Treiber.TREND, Treiber.SAISON)
        Gruppe.KORB -> listOf(Treiber.DOLLAR, Treiber.TREND)
        null -> emptyList()
    }

    // Gewicht in der Waage: 3 fuer den Haupttreiber der Gruppe, 2 fuer starke, 1 fuer schwache Treiber
    fun gewicht(schluessel: String, t: Treiber): Int = when (t) {
        Treiber.REALZINS -> if (schluessel == "GOLD") 3 else 2
        Treiber.LAGER, Treiber.BOERSENLAGER -> 3
        Treiber.DOLLAR, Treiber.POSITIONIERUNG, Treiber.TREND, Treiber.TERMINKURVE, Treiber.OPEC -> 2
        Treiber.ENSO -> if (gruppe(schluessel) == Gruppe.AGRAR) 2 else 1
        Treiber.INFLATION, Treiber.SAISON, Treiber.KONJUNKTUR, Treiber.WELTLAGER -> 1
    }

    // Schluessel des Markts in Positionierung.MAERKTE
    fun cotMarkt(schluessel: String): String? = when (schluessel) {
        "GOLD" -> "gold"; "SILBER" -> "silber"; "PLATIN" -> "platin"; "PALLADIUM" -> "palladium"; "KUPFER" -> "kupfer"
        "OEL" -> "oel"; "GAS" -> "gas"; "WEIZEN" -> "weizen"; "MAIS" -> "mais"; "KAFFEE" -> "kaffee"; "KAKAO" -> "kakao"; "ZUCKER" -> "zucker"
        else -> null
    }

    // Schluessel des Rohstoffs in Enso.WIRKUNG_NINO und WIRKUNG_NINA
    fun ensoSchluessel(schluessel: String): String? = when (schluessel) {
        "GAS" -> "erdgas"; "WEIZEN" -> "weizen"; "MAIS" -> "mais"; "KAFFEE" -> "kaffee"; "KAKAO" -> "kakao"; "ZUCKER" -> "zucker"
        else -> null
    }

    // Preiswirkung der laufenden ENSO-Phase auf den Rohstoff: +1 eher hoeher, -1 eher tiefer, null ohne bekannte Wirkung
    fun ensoWirkung(schluessel: String, phase: Int): Int? {
        val k = ensoSchluessel(schluessel) ?: return null
        val liste = if (phase > 0) Enso.WIRKUNG_NINO else if (phase < 0) Enso.WIRKUNG_NINA else return null
        return liste.firstOrNull { it.schluessel == k }?.richtung
    }

    // Eine Messung: Wert, Vergleichsgroesse (Aenderung, Rang, Anteil; je Treiber anders) und Stand in Millisekunden.
    class Messung(val wert: Double, val vergleich: Double, val stand: Long, val zusatz: Double? = null)

    // Alles, was die Seite geladen hat; null oder leer, wenn die Quelle nichts lieferte.
    class Eingaben(
        // Tagesreihen als (Tag seit 1970, Wert), aelteste zuerst
        val realzins: List<Pair<Long, Double>> = emptyList(),
        val inflation: List<Pair<Long, Double>> = emptyList(),
        val dollar: List<Pair<Long, Double>> = emptyList(),
        // Schlusskurse des Rohstoffs selbst, so lang wie vorhanden
        val kurse: List<Pair<Long, Double>> = emptyList(),
        // Rang der Nettoposition in drei Jahren (0 bis 100), Anteil an den offenen Kontrakten in Prozent, Stand
        val positionRang: Int? = null, val positionAnteil: Double? = null, val positionStand: Long = 0L,
        // Abweichung des US-Lagers vom Fuenfjahresschnitt in Prozent, Stand (Tag seit 1970)
        val lagerAbweichung: Double? = null, val lagerTag: Long = 0L,
        // Aenderung des Boersenlagers gegen den Vormonat in Prozent, Bestand, Stand (Tag seit 1970)
        val boersenlagerAenderung: Double? = null, val boersenlager: Double = 0.0, val boersenlagerTag: Long = 0L,
        // Spanne der Terminkurve auf ein Jahr in Prozent (Eingepreist.spanne), Stand in Millisekunden
        val kurvenSpanne: Double? = null, val kurveStand: Long = 0L,
        // ENSO: +1 El Nino, -1 La Nina, 0 neutral; juengster Index; Stand in Millisekunden
        val ensoPhase: Int? = null, val ensoIndex: Double? = null, val ensoStand: Long = 0L,
        // Rezessionswahrscheinlichkeit in Prozent und erster Tag ihres Monats in Millisekunden
        val rezession: Double? = null, val rezessionStand: Long = 0L,
        // Rohoel der OPEC+ und Lager der OECD: Wert, Aenderung zum Vorjahresmonat in Prozent, erster Tag des Monats in ms
        val opecPlus: Messung? = null, val oecdLager: Messung? = null,
        // Heutiger Tag seit 1970 (fuer den Kalendermonat der Saison)
        val heute: Long = 0L,
    )

    // Ein Punkt der Waage. wert und vergleich in der Einheit des Treibers (siehe Kopf), stand in Millisekunden.
    data class Punkt(val treiber: Treiber, val richtung: Richtung, val wert: Double, val vergleich: Double, val stand: Long, val gewicht: Int, val zusatz: Double? = null)

    private const val TAG_MS = 86_400_000L

    // Der Wert wird auf sechs Stellen gerundet, damit Rechenreste an der Schwelle nichts entscheiden.
    private fun richtung(wert: Double, stuetztAb: Double, belastetAb: Double, steigendStuetzt: Boolean): Richtung = richtungGerundet(Math.round(wert * 1e6) / 1e6, stuetztAb, belastetAb, steigendStuetzt)

    private fun richtungGerundet(v: Double, stuetztAb: Double, belastetAb: Double, steigendStuetzt: Boolean): Richtung = when {
        steigendStuetzt && v >= stuetztAb -> Richtung.STUETZT
        steigendStuetzt && v <= belastetAb -> Richtung.BELASTET
        !steigendStuetzt && v <= stuetztAb -> Richtung.STUETZT
        !steigendStuetzt && v >= belastetAb -> Richtung.BELASTET
        else -> Richtung.NEUTRAL
    }

    // Letzter Wert und der Wert tage Eintraege davor; null bei zu kurzer Reihe.
    internal fun gegenFrueher(reihe: List<Pair<Long, Double>>, tage: Int = FENSTER_TAGE): Triple<Double, Double, Long>? {
        val r = reihe.filter { it.second.isFinite() }
        if (r.size <= tage) return null
        return Triple(r.last().second, r[r.size - 1 - tage].second, r.last().first)
    }

    // Abstand des letzten Kurses zum Schnitt der letzten 200 Schlusskurse in Prozent; null bei weniger Kursen.
    fun trendAbstand(kurse: List<Pair<Long, Double>>): Triple<Double, Double, Long>? {
        val r = kurse.filter { it.second.isFinite() && it.second > 0 }
        if (r.size < TREND_TAGE) return null
        val schnitt = r.takeLast(TREND_TAGE).sumOf { it.second } / TREND_TAGE
        return Triple(r.last().second, (r.last().second / schnitt - 1) * 100, r.last().first)
    }

    // Kalendermonat des Tages heute in der Kursgeschichte: mittlere Monatsrendite in Prozent, Anteil positiver Jahre,
    // Zahl der Jahre; null bei weniger als SAISON_JAHRE.
    fun saison(kurse: List<Pair<Long, Double>>, heute: Long): Kursmuster.Wert? {
        val r = kurse.filter { it.second.isFinite() && it.second > 0 }
        if (r.size < 250) return null
        val enden = Kursmuster.monatsEnden(LongArray(r.size) { r[it].first }, DoubleArray(r.size) { r[it].second })
        val w = Kursmuster.saison(Kursmuster.monatsRenditen(enden))[java.time.LocalDate.ofEpochDay(heute).monthValue - 1]
        return w.takeIf { it.anzahl >= SAISON_JAHRE }
    }

    // Die Punkte des Rohstoffs in der Reihenfolge von treiber(); Treiber ohne Messung fehlen.
    fun auswerten(schluessel: String, e: Eingaben): List<Punkt> = treiber(schluessel).mapNotNull { t ->
        val g = gewicht(schluessel, t)
        when (t) {
            Treiber.REALZINS -> gegenFrueher(e.realzins)?.let { (jetzt, vor, tag) ->
                Punkt(t, richtung(jetzt - vor, -REALZINS_PUNKTE, REALZINS_PUNKTE, steigendStuetzt = false), jetzt, jetzt - vor, tag * TAG_MS, g)
            }
            Treiber.INFLATION -> gegenFrueher(e.inflation)?.let { (jetzt, vor, tag) ->
                Punkt(t, richtung(jetzt - vor, INFLATION_PUNKTE, -INFLATION_PUNKTE, steigendStuetzt = true), jetzt, jetzt - vor, tag * TAG_MS, g)
            }
            Treiber.DOLLAR -> gegenFrueher(e.dollar)?.takeIf { it.second > 0 }?.let { (jetzt, vor, tag) ->
                val p = (jetzt / vor - 1) * 100
                Punkt(t, richtung(p, -DOLLAR_PROZENT, DOLLAR_PROZENT, steigendStuetzt = false), jetzt, p, tag * TAG_MS, g)
            }
            Treiber.POSITIONIERUNG -> e.positionRang?.let { rang ->
                val r = if (rang >= POSITION_HOCH) Richtung.BELASTET else if (rang <= POSITION_TIEF) Richtung.STUETZT else Richtung.NEUTRAL
                Punkt(t, r, e.positionAnteil ?: 0.0, rang.toDouble(), e.positionStand, g)
            }
            Treiber.TREND -> trendAbstand(e.kurse)?.let { (kurs, abstand, tag) ->
                Punkt(t, richtung(abstand, TREND_PROZENT, -TREND_PROZENT, steigendStuetzt = true), kurs, abstand, tag * TAG_MS, g)
            }
            Treiber.SAISON -> saison(e.kurse, e.heute)?.let { w ->
                val anteil = w.positiv.toDouble() / w.anzahl
                val r = when {
                    anteil >= SAISON_STARK && w.mittel > 0 -> Richtung.STUETZT
                    anteil <= SAISON_SCHWACH && w.mittel < 0 -> Richtung.BELASTET
                    else -> Richtung.NEUTRAL
                }
                Punkt(t, r, w.mittel, w.positiv.toDouble(), e.heute * TAG_MS, g, zusatz = w.anzahl.toDouble())
            }
            Treiber.LAGER -> e.lagerAbweichung?.let { a ->
                Punkt(t, richtung(a, -LAGER_PROZENT, LAGER_PROZENT, steigendStuetzt = false), a, a, e.lagerTag * TAG_MS, g)
            }
            Treiber.BOERSENLAGER -> e.boersenlagerAenderung?.let { a ->
                Punkt(t, richtung(a, -BOERSENLAGER_PROZENT, BOERSENLAGER_PROZENT, steigendStuetzt = false), e.boersenlager, a, e.boersenlagerTag * TAG_MS, g)
            }
            Treiber.TERMINKURVE -> e.kurvenSpanne?.let { s ->
                Punkt(t, richtung(s, KURVE_BACKWARDATION, KURVE_CONTANGO, steigendStuetzt = true), s, s, e.kurveStand, g)
            }
            Treiber.ENSO -> e.ensoPhase?.let { phase ->
                val w = ensoWirkung(schluessel, phase) ?: return@let null
                Punkt(t, if (w > 0) Richtung.STUETZT else Richtung.BELASTET, e.ensoIndex ?: 0.0, phase.toDouble(), e.ensoStand, g)
            }
            Treiber.KONJUNKTUR -> e.rezession?.let { p ->
                Punkt(t, richtung(p, REZESSION_TIEF, REZESSION_HOCH, steigendStuetzt = false), p, p, e.rezessionStand, g)
            }
            Treiber.OPEC -> e.opecPlus?.let { m ->
                Punkt(t, richtung(m.vergleich, -FOERDERUNG_PROZENT, FOERDERUNG_PROZENT, steigendStuetzt = false), m.wert, m.vergleich, m.stand, g)
            }
            Treiber.WELTLAGER -> e.oecdLager?.let { m ->
                Punkt(t, richtung(m.vergleich, -FOERDERUNG_PROZENT, FOERDERUNG_PROZENT, steigendStuetzt = false), m.wert, m.vergleich, m.stand, g)
            }
        }
    }

    // Waage aus den Gewichten: +100 nur stuetzend, -100 nur belastend; null ohne stuetzenden oder belastenden Punkt.
    // Die Stufen sind die von BullBear.
    fun waage(punkte: List<Punkt>): BullBear.Waage? {
        val plus = punkte.filter { it.richtung == Richtung.STUETZT }.sumOf { it.gewicht }.toDouble()
        val minus = punkte.filter { it.richtung == Richtung.BELASTET }.sumOf { it.gewicht }.toDouble()
        if (plus + minus <= 0.0) return null
        val wert = Math.round((plus - minus) / (plus + minus) * 100).toInt()
        val stufe = when {
            wert <= -50 -> BullBear.Stufe.KLAR_DAGEGEN
            wert < -15 -> BullBear.Stufe.EHER_DAGEGEN
            wert <= 15 -> BullBear.Stufe.AUSGEWOGEN
            wert < 50 -> BullBear.Stufe.EHER_DAFUER
            else -> BullBear.Stufe.KLAR_DAFUER
        }
        return BullBear.Waage(wert, stufe, plus, minus)
    }

    // ------------------------------------------------------------ Signale aus dem Kurs

    // Kurssignale, die fuer jeden Wert mit Tageskursen gelten: Trend, Durchbruch, Abstand zu Hoch und Tief, Schwankung.
    // trend: Abstand zur 200-Tage-Linie in Prozent (null bei zu wenig Kursen). hoch und tief: Abstand zum hoechsten und
    // tiefsten Schluss der letzten 252 Handelstage in Prozent (hoch <= 0, tief >= 0). durchbruch: +1, wenn der letzte
    // Schluss ueber allen der 20 Handelstage davor liegt, -1 darunter, 0 sonst. vola: Schwankung der letzten 20 Handelstage
    // aufs Jahr gerechnet in Prozent, volaJahr dieselbe ueber 252 Tage.
    data class Kurssignale(val trend: Double?, val hoch: Double, val tief: Double, val durchbruch: Int, val vola: Double?, val volaJahr: Double?, val stand: Long)

    const val JAHR_TAGE = 252
    const val DURCHBRUCH_TAGE = 20

    private fun schwankung(kurse: List<Double>): Double? {
        if (kurse.size < 3) return null
        val r = kurse.zipWithNext().mapNotNull { (a, b) -> if (a > 0 && b > 0) kotlin.math.ln(b / a) else null }
        if (r.size < 2) return null
        val m = r.average()
        val v = r.sumOf { (it - m) * (it - m) } / (r.size - 1)
        return kotlin.math.sqrt(v) * kotlin.math.sqrt(JAHR_TAGE.toDouble()) * 100
    }

    fun kurssignale(kurse: List<Pair<Long, Double>>): Kurssignale? {
        val r = kurse.filter { it.second.isFinite() && it.second > 0 }
        if (r.size < DURCHBRUCH_TAGE + 2) return null
        val letzter = r.last().second
        val jahr = r.takeLast(JAHR_TAGE).map { it.second }
        val davor = r.dropLast(1).takeLast(DURCHBRUCH_TAGE).map { it.second }
        val durchbruch = if (letzter > davor.max()) 1 else if (letzter < davor.min()) -1 else 0
        return Kurssignale(
            trend = trendAbstand(r)?.second, hoch = (letzter / jahr.max() - 1) * 100, tief = (letzter / jahr.min() - 1) * 100,
            durchbruch = durchbruch, vola = schwankung(r.takeLast(DURCHBRUCH_TAGE + 1).map { it.second }),
            volaJahr = if (r.size > JAHR_TAGE) schwankung(jahr) else null, stand = r.last().first * TAG_MS,
        )
    }

    // Schwankung zuletzt deutlich ueber (+1) oder unter (-1) der des Jahres, sonst 0
    fun volaLage(s: Kurssignale): Int {
        val kurz = s.vola ?: return 0
        val lang = s.volaJahr ?: return 0
        if (lang <= 0) return 0
        return if (kurz >= lang * 1.5) 1 else if (kurz <= lang * 0.67) -1 else 0
    }

    // Ist der Abstand zum Jahreshoch (oder -tief) klein genug, um als "am Hoch" zu gelten (hoechstens 2 %)?
    fun amHoch(s: Kurssignale): Boolean = abs(s.hoch) <= 2.0
    fun amTief(s: Kurssignale): Boolean = abs(s.tief) <= 2.0
}
