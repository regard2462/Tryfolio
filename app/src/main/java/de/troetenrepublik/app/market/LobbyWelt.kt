package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Lobbyarbeit ausserhalb der USA je Aktie (seit 5.9): EU-Transparenzregister, Lobbyregister des Bundestages und
// Repertoire der HATVP (Frankreich), mitgeliefert aus tools/lobby/welt.py (assets/behoerden/lobby_welt.json).
object LobbyWelt {
    // Register (EU, DE, FR), Name im Register, Aufwand als Spanne in der Waehrung w, Geschaeftsjahr (von/bis),
    // Vollzeitstellen, gemeldete Taetigkeiten (nur FR), Themen und Link zum Eintrag.
    @Serializable
    data class Eintrag(
        val l: String,
        val n: String = "",
        val w: String = "EUR",
        val von: Long? = null,
        val bis: Long? = null,
        val j: String = "",
        val fte: Double? = null,
        val taetigkeiten: Int? = null,
        val themen: List<String> = emptyList(),
        val q: String = "",
    )

    @Serializable
    data class Datei(val format: Int, val stand: String = "", val firmen: Map<String, List<Eintrag>> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()?.takeIf { it.format == 1 }

    // Reihenfolge der Anzeige: EU, Deutschland, Frankreich.
    val REIHENFOLGE = listOf("EU", "DE", "FR")
}
