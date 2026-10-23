package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Zeichnungen im erweiterten Chart, je Wert gemerkt. Ein Punkt ist Zeit und Kurs in Euro,
// damit eine Zeichnung in jedem Zeitraum an derselben Stelle steht.
@Serializable
data class Zeichnung(
    val art: String,
    val t1: Long,
    val p1: Double,
    val t2: Long = 0L,
    val p2: Double = 0.0,
    // Text der Notiz oder das Emoji.
    val text: String = "",
)

object ChartZeichnungen {
    const val LINIE = "linie"
    const val WAAGERECHT = "waagerecht"
    const val NOTIZ = "notiz"
    const val EMOJI = "emoji"

    const val MAX = 200

    private val json = Json { ignoreUnknownKeys = true }

    // Symbol auf Liste, ein unlesbarer Stand gilt als leer.
    fun lesen(text: String): Map<String, List<Zeichnung>> =
        if (text.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, List<Zeichnung>>>(text) }.getOrDefault(emptyMap())

    fun schreiben(alle: Map<String, List<Zeichnung>>): String = json.encodeToString(alle.filterValues { it.isNotEmpty() })

    // Ungueltige Punkte fallen weg.
    fun setzen(alle: Map<String, List<Zeichnung>>, symbol: String, liste: List<Zeichnung>): Map<String, List<Zeichnung>> {
        val gut = liste.filter { it.p1.isFinite() && it.p2.isFinite() && it.t1 > 0 }.takeLast(MAX)
        return if (gut.isEmpty()) alle - symbol else alle + (symbol to gut)
    }
}
