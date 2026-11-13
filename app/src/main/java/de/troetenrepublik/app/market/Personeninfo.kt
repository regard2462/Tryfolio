package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Kurzinfos zu Personen bei Copytrading (seit 5.92): Partei, Kammer, Staat und Wahlkreis, seit wann in der Kammer und
// Ausschuesse mit Funktion. Abgeordnete und Senatoren nach Bioguide-Kennung (p) aus unitedstates/congress-legislators
// (CC0), weitere Regierungsmitglieder nach Kennung (x, nur Partei, aus Wikidata, CC0). Mitgeliefert unter
// copytrading/personen.json, gebaut von tools/copytrading/personen.py und regierung_bilder.py.
@Serializable
data class Personeninfo(
    val stand: String = "",
    val quelle: String = "",
    val p: Map<String, Info> = emptyMap(),
    val x: Map<String, Info> = emptyMap(),
) {
    @Serializable
    data class Info(
        // Partei: R, D, I (parteilos) oder L.
        val pa: String = "",
        // Kammer: rep (Repraesentantenhaus) oder sen (Senat).
        val k: String = "",
        // Bundesstaat (Kuerzel) und Wahlkreis (0 = ganzer Staat).
        val st: String = "",
        val d: Int? = null,
        // Beginn der laufenden Folge von Amtszeiten in dieser Kammer (JJJJ-MM-TT).
        val seit: String = "",
        // Ausschuesse: Name und Funktion (leer, Chair, Chairman, Ranking Member, Vice Chair ...).
        val a: List<List<String>> = emptyList(),
        // Ende der Mitgliedschaft bei Ehemaligen (5.96), sonst leer.
        val bis: String = "",
    ) {
        val seitJahr: Int? get() = seit.take(4).toIntOrNull()
        val bisJahr: Int? get() = bis.take(4).toIntOrNull()

        // Ausschuesse mit Funktion zuerst; Funktion leer fuer einfache Mitglieder.
        val ausschuesse: List<Pair<String, String>> get() = a.mapNotNull { z -> z.getOrNull(0)?.let { it to z.getOrElse(1) { "" } } }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun lesen(text: String): Personeninfo? = runCatching { json.decodeFromString<Personeninfo>(text) }.getOrNull()

        // Namen der Bundesstaaten und Gebiete (Eigennamen, in beiden Sprachen gleich ausser Kalifornien).
        val STAATEN = mapOf(
            "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California", "CO" to "Colorado",
            "CT" to "Connecticut", "DE" to "Delaware", "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho",
            "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa", "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana",
            "ME" to "Maine", "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi",
            "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey",
            "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio", "OK" to "Oklahoma",
            "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina", "SD" to "South Dakota",
            "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington",
            "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming", "DC" to "District of Columbia", "PR" to "Puerto Rico",
            "GU" to "Guam", "VI" to "U.S. Virgin Islands", "AS" to "American Samoa", "MP" to "Northern Mariana Islands",
        )
    }
}
