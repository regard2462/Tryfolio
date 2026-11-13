package de.troetenrepublik.app.engine

// Neue Hoch- und Abstufungen der letzten Tage je Wert (0.78), aus den Yahoo-Urteilen. Bestaetigungen und neutrale
// Erstbewertungen zaehlen nicht (Analystenbilanz.richtung).
object AnalystenStufen {
    const val TAGE = 7

    // aktion: up, down oder init wie bei Yahoo; richtung +1 hoch, -1 runter
    data class Eintrag(
        val symbol: String, val haus: String, val zeit: Long, val von: String, val nach: String,
        val aktion: String, val richtung: Int, val ziel: Double, val zielVorher: Double,
    )

    fun neue(symbol: String, urteile: List<Analystenbilanz.Urteil>, jetztMs: Long, tage: Int = TAGE): List<Eintrag> {
        val ab = jetztMs - tage * 86_400_000L
        return urteile.filter { it.zeit in ab..jetztMs }.mapNotNull { u ->
            val r = Analystenbilanz.richtung(u) ?: return@mapNotNull null
            Eintrag(symbol, u.haus, u.zeit, u.von, u.nach, u.aktion.lowercase(), r, u.ziel, u.zielVorher)
        }
    }

    // Neueste zuerst; bei gleicher Zeit nach Symbol und Haus
    fun ordnen(alle: List<Eintrag>): List<Eintrag> =
        alle.sortedWith(compareByDescending<Eintrag> { it.zeit }.thenBy { it.symbol }.thenBy { it.haus })

    fun hoch(alle: List<Eintrag>) = alle.count { it.richtung > 0 }
    fun runter(alle: List<Eintrag>) = alle.count { it.richtung < 0 }

    // Werkzeug „Neue Analystenstufen“ (0.78): je Unternehmen eine Gruppe, Zeitraum und Eingrenzung waehlbar.

    // Waehlbare Zeitraeume in Tagen. Yahoo liefert je Wert den ganzen Verlauf im selben Abruf, aeltere Stufen kosten
    // also keinen weiteren Abruf.
    val ZEITRAEUME = listOf(7, 30, 90, 365)
    const val ZEITRAUM_VORGABE = 30

    enum class Kreis { ALLE, DEPOT, WATCHLIST }

    // Hoechstzahl der Werte (und damit der Abrufe) je Oeffnen, davon hoechstens EIGENE_MAX aus Depot und Watchlist
    // und GEZIELT_MAX gezielt gesuchte.
    const val OBERGRENZE = 120
    const val EIGENE_MAX = 60
    const val GEZIELT_MAX = 20

    // Nach so vielen Abrufen ohne Antwort in Folge endet das Laden (keine Wiederholung gegen eine Sperre).
    const val FEHLER_MAX = 5

    // Reihenfolge der Abrufe: gezielt gesuchte Werte, dann Depot und Watchlist, dann die grossen Werte; jeder nur einmal.
    fun auswahl(
        gezielt: List<String>, depot: List<String>, watchlist: List<String>, grosse: List<String>, obergrenze: Int = OBERGRENZE,
    ): List<String> =
        (gezielt.distinct().take(GEZIELT_MAX) + (depot + watchlist).distinct().take(EIGENE_MAX) + grosse).distinct().take(obergrenze.coerceAtLeast(0))

    // Stufen eines Unternehmens im Zeitraum, neueste zuerst. zielMittel: Mittel der juengsten Kursziele je Haus im
    // Zeitraum (0 = keins), zielZahl: Zahl dieser Haeuser.
    data class Gruppe(
        val symbol: String, val eintraege: List<Eintrag>, val hoch: Int, val runter: Int, val juengste: Long,
        val zielMittel: Double, val zielZahl: Int,
    )

    // Mittel der juengsten Kursziele je Haus und Zahl der Haeuser mit Ziel.
    fun zielMittel(eintraege: List<Eintrag>): Pair<Double, Int> {
        val ziele = eintraege.filter { it.ziel > 0 }.groupBy { it.haus.trim() }.values.map { l -> l.maxByOrNull { it.zeit }!!.ziel }
        return if (ziele.isEmpty()) 0.0 to 0 else ziele.average() to ziele.size
    }

    fun gruppe(symbol: String, urteile: List<Analystenbilanz.Urteil>, jetztMs: Long, tage: Int): Gruppe {
        val e = ordnen(neue(symbol, urteile, jetztMs, tage))
        val (mittel, zahl) = zielMittel(e)
        return Gruppe(symbol, e, hoch(e), runter(e), e.firstOrNull()?.zeit ?: 0L, mittel, zahl)
    }

    // Gruppen im Zeitraum, das Unternehmen mit der juengsten Stufe zuerst. erlaubt grenzt auf Depot oder Watchlist ein
    // (null = alle). Unternehmen ohne Stufe im Zeitraum entfallen; die aus immer stehen dann ohne Eintraege vorn.
    fun buendeln(
        urteile: Map<String, List<Analystenbilanz.Urteil>>, jetztMs: Long, tage: Int,
        erlaubt: Set<String>? = null, immer: List<String> = emptyList(),
    ): List<Gruppe> {
        val alle = urteile.filterKeys { erlaubt == null || it in erlaubt }.map { (s, u) -> gruppe(s, u, jetztMs, tage) }
        val leer = immer.distinct().mapNotNull { s -> alle.firstOrNull { it.symbol == s && it.eintraege.isEmpty() } }
        return leer + alle.filter { it.eintraege.isNotEmpty() }.sortedWith(compareByDescending<Gruppe> { it.juengste }.thenBy { it.symbol })
    }

    // Hoch- und Abstufungen aller Gruppen (Kopf der Seite).
    fun zaehler(gruppen: List<Gruppe>): Pair<Int, Int> = gruppen.sumOf { it.hoch } to gruppen.sumOf { it.runter }

    fun erlaubt(kreis: Kreis, depot: Collection<String>, watchlist: Collection<String>): Set<String>? = when (kreis) {
        Kreis.ALLE -> null
        Kreis.DEPOT -> depot.toSet()
        Kreis.WATCHLIST -> watchlist.toSet()
    }
}
