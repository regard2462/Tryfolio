package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

// Rezessionszeichen der Welt (0.69): drei US-Reihen von FRED (CSV ohne Schluessel, nur Anzeige beim Nutzer).
// T10Y3M: Rendite 10 Jahre minus 3 Monate in Prozentpunkten (taeglich).
// SAHMREALTIME: Sahm-Regel in Prozentpunkten (monatlich), Schwelle 0,5.
// RECPROUSM156N: geschaetzte Rezessionswahrscheinlichkeit in Prozent (monatlich), Modell von Chauvet und Piger.
object WeltRezession {
    const val ZINSKURVE = "T10Y3M"
    const val SAHM = "SAHMREALTIME"
    const val WAHRSCHEINLICHKEIT = "RECPROUSM156N"

    // Schwelle der Sahm-Regel in Prozentpunkten
    const val SCHWELLE_SAHM = 0.5

    const val GUELTIG_MS = 24 * 3_600_000L

    // tag: Tage seit 1970 (Zeitstempel des Werts bei FRED)
    @Serializable
    data class Wert(val tag: Long, val wert: Double)

    @Serializable
    data class Daten(val abgerufen: Long = 0L, val zinskurve: Wert? = null, val sahm: Wert? = null, val wahrscheinlichkeit: Wert? = null) {
        val leer: Boolean get() = zinskurve == null && sahm == null && wahrscheinlichkeit == null
    }

    fun adresse(reihe: String, von: LocalDate) = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$reihe&cosd=$von"

    // Letzter Wert einer FRED-CSV
    fun letzter(csv: String): Wert? = Fruehindikatoren.fredLesen(csv).lastOrNull()?.let { Wert(it.first, it.second) }

    fun veraltet(d: Daten?, jetzt: Long) = d == null || jetzt - d.abgerufen >= GUELTIG_MS || jetzt < d.abgerufen

    // Fehlendes bleibt vom letzten Stand.
    fun zusammen(neu: Daten, alt: Daten?) = Daten(
        neu.abgerufen, neu.zinskurve ?: alt?.zinskurve, neu.sahm ?: alt?.sahm, neu.wahrscheinlichkeit ?: alt?.wahrscheinlichkeit,
    )

    private val json = Json { ignoreUnknownKeys = true }
    fun schreiben(d: Daten): String = json.encodeToString(Daten.serializer(), d)
    fun lesenGespeichert(text: String): Daten? = runCatching { json.decodeFromString(Daten.serializer(), text) }.getOrNull()?.takeIf { !it.leer }
}
