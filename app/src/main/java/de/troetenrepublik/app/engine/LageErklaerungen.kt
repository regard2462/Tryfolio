package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// [0.78] Gepflegte Erklaerungen der Lage (assets/welt/erklaerungen.json): je Konflikt, Spannungsgebiet und Engpass ein Satz,
// worum es geht, deutsch und englisch. Konflikte tragen dazu einen kurzen Titel (Parteien in der App-Sprache). Schluessel:
// Konflikte "SeiteA|SeiteB" wie in den Konfliktdaten, Gebiete die Kennung aus Spannungen, Engpaesse die Kennung aus
// Engpaesse. Fehlt ein Eintrag, bleibt die Zeile ohne Erklaerung.
object LageErklaerungen {
    const val DATEI = "welt/erklaerungen.json"

    // de, en: der Satz; tde, ten: kurzer Titel (nur bei Konflikten)
    @Serializable
    class Text(val de: String = "", val en: String = "", val tde: String = "", val ten: String = "") {
        fun satz(deutsch: Boolean): String? = (if (deutsch) de else en).ifBlank { null }
        fun titel(deutsch: Boolean): String? = (if (deutsch) tde else ten).ifBlank { null }
    }

    @Serializable
    class Datei(
        val stand: String = "", val konflikte: Map<String, Text> = emptyMap(), val gebiete: Map<String, Text> = emptyMap(),
        val engpaesse: Map<String, Text> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    @Volatile private var geladen: Datei? = null

    // Schon gelesene Datei (sofort, ohne Lesen); null vor dem ersten laden()
    fun bekannt(): Datei? = geladen

    // Liest die Datei einmal je Prozess; eine fehlende oder kaputte Datei ergibt eine leere
    fun laden(): Datei = geladen ?: (WeltDaten.text(DATEI)?.let { lesen(it) } ?: Datei()).also { geladen = it }
}
