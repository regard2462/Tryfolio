package de.troetenrepublik.app.engine

// Insider-Trend einer Aktie (seit 5.6): ob die Fuehrung eines Unternehmens in einem Zeitraum eher kauft oder verkauft.
// Jede Person zaehlt einmal, mit der Richtung ihres Nettogeschaefts im Zeitraum, gewichtet nach ihrer Beziehung zum
// Unternehmen. Verkaeufe aus vorab festgelegten Verkaufsplaenen (US 10b5-1) zaehlen halb. Nur Kaeufe und Verkaeufe an
// der Boerse, keine Zuteilungen, Ausuebungen oder Schenkungen.
object InsiderTrend {
    // Beziehung zum Unternehmen mit Gewicht.
    enum class Rolle(val gewicht: Double) {
        // Vorstand, CEO, CFO, Vorsitz
        SPITZE(3.0),
        // andere Fuehrungskraefte
        FUEHRUNG(2.0),
        // Aufsichtsrat, Direktoren
        AUFSICHT(1.5),
        // Grossaktionaere ueber 10 %, Personen in enger Beziehung
        NAHE(1.0),
    }

    enum class Stufe { KLAR_VERKAUFEND, EHER_VERKAUFEND, GEMISCHT, EHER_KAUFEND, KLAR_KAUFEND }

    data class Geschaeft(
        val person: String,
        val rolle: Rolle,
        val kauf: Boolean,
        val betrag: Double,
        // JJJJ-MM-TT
        val tag: String,
        // Verkauf aus einem vorab festgelegten Plan
        val plan: Boolean = false,
    )

    data class Ergebnis(
        // -100 (nur Verkaeufe der Fuehrung) bis +100 (nur Kaeufe)
        val wert: Int,
        val stufe: Stufe,
        val kaeufer: Int,
        val verkaeufer: Int,
        val kaufBetrag: Double,
        val verkaufBetrag: Double,
        // Personen je Rolle: Kaeufer und Verkaeufer
        val nachRolle: Map<Rolle, Pair<Int, Int>>,
        // Mindestens drei verschiedene Personen haben innerhalb von 30 Tagen gekauft.
        val cluster: Boolean,
    )

    fun stufe(wert: Int): Stufe = when {
        wert <= -50 -> Stufe.KLAR_VERKAUFEND
        wert < -15 -> Stufe.EHER_VERKAUFEND
        wert <= 15 -> Stufe.GEMISCHT
        wert < 50 -> Stufe.EHER_KAUFEND
        else -> Stufe.KLAR_KAUFEND
    }

    // Auswertung ab abTag (JJJJ-MM-TT); null ohne Kaeufe oder Verkaeufe.
    fun auswerten(liste: List<Geschaeft>, abTag: String): Ergebnis? {
        val im = liste.filter { it.tag >= abTag && it.betrag > 0 }
        if (im.isEmpty()) return null
        // Je Person: Richtung des Nettogeschaefts, hoechste Rolle, Verkauf nur aus Plaenen
        data class Person(val kauf: Boolean, val rolle: Rolle, val nurPlan: Boolean, val kaufBetrag: Double, val verkaufBetrag: Double)
        val personen = im.groupBy { it.person.trim().lowercase() }.mapNotNull { (_, g) ->
            val k = g.filter { it.kauf }.sumOf { it.betrag }
            val v = g.filter { !it.kauf }.sumOf { it.betrag }
            if (k == v) return@mapNotNull null
            Person(k > v, g.minByOrNull { it.rolle.ordinal }!!.rolle, g.filter { !it.kauf }.all { it.plan }, k, v)
        }
        if (personen.isEmpty()) return null
        fun gewicht(p: Person) = p.rolle.gewicht * if (!p.kauf && p.nurPlan) 0.5 else 1.0
        val plus = personen.filter { it.kauf }.sumOf { gewicht(it) }
        val minus = personen.filter { !it.kauf }.sumOf { gewicht(it) }
        val wert = Math.round((plus - minus) / (plus + minus) * 100).toInt()
        val nachRolle = Rolle.entries.associateWith { r -> personen.count { it.rolle == r && it.kauf } to personen.count { it.rolle == r && !it.kauf } }
            .filterValues { it.first + it.second > 0 }
        return Ergebnis(
            wert, stufe(wert), personen.count { it.kauf }, personen.count { !it.kauf },
            personen.sumOf { it.kaufBetrag }, personen.sumOf { it.verkaufBetrag }, nachRolle, cluster(im),
        )
    }

    // Drei verschiedene Kaeufer innerhalb von 30 Tagen.
    fun cluster(liste: List<Geschaeft>): Boolean {
        val kaeufe = liste.filter { it.kauf }.sortedBy { it.tag }
        for (i in kaeufe.indices) {
            val bis = java.time.LocalDate.parse(kaeufe[i].tag).plusDays(30).toString()
            if (kaeufe.filter { it.tag >= kaeufe[i].tag && it.tag <= bis }.map { it.person.trim().lowercase() }.toSet().size >= 3) return true
        }
        return false
    }

    // Rolle aus der Angabe der BaFin (Position/Status).
    fun rolleBafin(r: String): Rolle = when (r.trim().lowercase()) {
        "vorstand", "geschäftsführer", "geschäftsführung" -> Rolle.SPITZE
        "sonstige führungsperson" -> Rolle.FUEHRUNG
        "aufsichtsrat", "verwaltungsrat" -> Rolle.AUFSICHT
        else -> Rolle.NAHE
    }
}
