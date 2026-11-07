package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.SecInsider

// Filter fuer die Liste der Insidergeschaefte (seit 5.92): Richtung, Rolle, Groesse, Zeitraum, Branche, Land und
// Cluster-Kaeufe. Branche und Land kommen aus dem Katalog der App und werden als Funktionen uebergeben, damit die
// Rechnung ohne Katalog testbar bleibt.
object InsiderFilter {

    enum class Richtung { ALLE, KAEUFE, VERKAEUFE }

    // Groessenstufen in US-Dollar.
    val GROESSEN = listOf(0.0, 100_000.0, 1_000_000.0, 10_000_000.0)

    // Zeitraeume in Tagen, 0 = alles (die Daten reichen 18 Monate zurueck).
    val ZEITRAEUME = listOf(7, 30, 90, 0)

    data class Auswahl(
        val richtung: Richtung = Richtung.ALLE,
        val rollen: Set<InsiderTrend.Rolle> = emptySet(),
        val mindestens: Double = 0.0,
        val tage: Int = 0,
        val branche: String? = null,
        val land: String? = null,
        val nurCluster: Boolean = false,
        // Verkaeufe aus vorab festgelegten Plaenen (Rule 10b5-1) ausblenden.
        val ohnePlan: Boolean = false,
    ) {
        // Zahl der gesetzten Filter ausser der Richtung, fuer die Anzeige am Filterknopf.
        val aktiv: Int
            get() = listOf(rollen.isNotEmpty(), mindestens > 0, tage > 0, branche != null, land != null, nurCluster, ohnePlan).count { it }
    }

    // Geschaefte, die zur Auswahl passen. heute als JJJJ-MM-TT; cluster sind die Kuerzel mit Cluster-Kauf.
    fun filtern(
        liste: List<SecInsider.Geschaeft>,
        a: Auswahl,
        heute: String,
        branche: (String) -> String?,
        land: (String) -> String?,
        cluster: Set<String>,
    ): List<SecInsider.Geschaeft> {
        val ab = if (a.tage > 0) java.time.LocalDate.parse(heute).minusDays(a.tage.toLong()).toString() else ""
        return liste.filter { g ->
            (a.richtung == Richtung.ALLE || (a.richtung == Richtung.KAEUFE) == g.kauf) &&
                (a.rollen.isEmpty() || g.rolle in a.rollen) &&
                g.wert >= a.mindestens &&
                g.d >= ab &&
                (a.branche == null || branche(g.k) == a.branche) &&
                (a.land == null || (land(g.k) ?: "US") == a.land) &&
                (!a.nurCluster || g.k in cluster) &&
                (!a.ohnePlan || !g.plan)
        }
    }

    // Branchen und Laender, die in der Liste vorkommen, haeufigste zuerst: nur sie stehen zur Wahl.
    fun haeufigste(liste: List<SecInsider.Geschaeft>, schluessel: (String) -> String?): List<String> =
        liste.mapNotNull { schluessel(it.k) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
}
