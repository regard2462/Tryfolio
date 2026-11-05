package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Allgemeine Trefferbilanz der Indikatoren (seit 5.92): dieselbe Rechnung wie IndikatorTreffer, aber ueber viele Aktien
// und Indizes mit ihrer ganzen Kursgeschichte zusammengezaehlt. Mitgeliefert als assets/indikatoren/allgemein.json
// (nur Summen, keine Kurse), gebaut mit :desktop:indikatorAllgemein.
object IndikatorAllgemein {
    @Serializable
    data class Eintrag(val signale: Int, val richtig: Int, val treffer: Double, val vergleich: Double)

    @Serializable
    data class Datei(val stand: String = "", val werte: Int = 0, val ab: Int = 0, val tage: Int = IndikatorTreffer.TAGE, val arten: Map<String, Eintrag> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Bilanzen je Wert zu einer allgemeinen zusammenzaehlen: Treffer ueber alle Signale, Vergleich nach Signalen gewichtet
    // (jeder Wert zaehlt so viel, wie er Signale beitraegt).
    fun zusammenfassen(je: List<IndikatorTreffer.Bilanz>): Eintrag? {
        val mit = je.filter { it.signale > 0 && it.vergleich != null }
        val signale = mit.sumOf { it.signale }
        if (signale == 0) return null
        val richtig = mit.sumOf { it.richtig }
        val vergleich = mit.sumOf { it.vergleich!! * it.signale } / signale
        return Eintrag(signale, richtig, richtig.toDouble() / signale, vergleich)
    }
}
