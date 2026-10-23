package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

// Profilbilder fuer Copytrading. Kongressmitglieder: amtliche, gemeinfreie Portraets des U.S. Government Publishing
// Office ueber unitedstates.github.io, zur Laufzeit per Bioguide-Kennung geladen (congress-legislators, CC0).
// Investoren und Regierung: freie Bilder von Wikimedia Commons mit Urheber und Lizenz, verkleinert unter
// assets/copytrading/bilder, weil Wikimedia Bildabrufe von Apps zeitweise ablehnt. Sonst bleiben die Initialen.
@Serializable
data class Personenbilder(
    val stand: String = "",
    val kongressFoto: String = "",
    // „CA11|pelosi“ und „CA|pelosi“ -> Bioguide-Kennung.
    val haus: Map<String, String> = emptyMap(),
    // „sanders|bernard“ und „sanders“ (wenn eindeutig) -> Bioguide-Kennung.
    val senat: Map<String, String> = emptyMap(),
    val personen: Map<String, Foto> = emptyMap(),
) {
    @Serializable
    data class Foto(
        val url: String = "",
        val autor: String = "",
        val lizenz: String = "",
        val quelle: String = "",
        val seite: String = "",
        val datei: String = "",
        // Mitgelieferte Fassung unter assets, leer bei Kongressfotos.
        val lokal: String = "",
        // Logo der Firma statt eines Fotos; quelle ist die Domain.
        val logo: Boolean = false,
        // Zweite Adresse, wenn die erste nicht laedt.
        val ersatz: String = "",
    ) {
        // Adresse fuer die Anzeige: asset: und Pfad fuer mitgelieferte Bilder, sonst die Internetadresse.
        val adresse: String get() = if (lokal.isNotBlank()) "asset:$lokal" else url

        // Amtliches Kongressportraet (gemeinfrei, ohne Urheberangabe je Bild).
        val kongress: Boolean get() = autor.isEmpty() && lizenz.isEmpty()
    }

    // Neue Mitglieder fehlen anfangs bei unitedstates.github.io, als Ersatz dient das amtliche Foto im Bioguide.
    private fun kongress(id: String) = kongressFoto.takeIf { it.contains("{id}") }?.let {
        Foto(url = it.replace("{id}", id), ersatz = "https://bioguide.congress.gov/photo/$id.jpg")
    }

    // Abgeordnete nach Nachname und Wahlkreis („CA11“; ohne Nummer nur der Bundesstaat).
    fun haus(nachname: String, wahlkreis: String, vorname: String = ""): Foto? {
        val st = wahlkreis.take(2).uppercase()
        val n = norm(ohneZusatz(nachname))
        if (st.length < 2 || n.isEmpty()) return null
        val d = wahlkreis.drop(2).toIntOrNull()
        // Doppelnamen: „Delaney, April McClain“ steht in der Liste als „McClain Delaney“
        val namen = listOf(n, norm(vorname.trim().substringAfterLast(' ') + nachname), norm(ohneZusatz(nachname).substringAfterLast(' '))).filter { it.isNotEmpty() }.distinct()
        val id = namen.firstNotNullOfOrNull { k -> d?.let { haus["$st${it.toString().padStart(2, '0')}|$k"] } ?: haus["$st|$k"] } ?: return null
        return kongress(id)
    }

    // Senatoren nach Nachname und Vorname (Rufname oder erster Vorname), sonst nach eindeutigem Nachnamen.
    fun senator(nachname: String, vorname: String): Foto? {
        val n = norm(ohneZusatz(nachname))
        if (n.isEmpty()) return null
        val v = norm(vorname.trim().substringBefore(' '))
        val id = senat["$n|$v"] ?: senat[n] ?: return null
        return kongress(id)
    }

    fun person(id: String): Foto? = personen[id]?.takeIf { it.url.isNotBlank() || it.lokal.isNotBlank() }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun lesen(text: String): Personenbilder? = runCatching { json.decodeFromString<Personenbilder>(text) }.getOrNull()

        // Die Meldungen schreiben „McConnell, Jr.“ oder „Justice, II“, die Liste der Mitglieder nur den Namen.
        fun ohneZusatz(nachname: String): String =
            nachname.trim().replace(Regex(",?\\s+(Jr\\.?|Sr\\.?|II|III|IV|V)\\s*$", RegexOption.IGNORE_CASE), "").trim()

        // Nur Kleinbuchstaben a bis z, ohne Akzente (wie beim Erzeugen der Datei).
        fun norm(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFKD).lowercase().filter { it in 'a'..'z' }
    }
}
