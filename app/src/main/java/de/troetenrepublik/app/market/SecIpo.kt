package de.troetenrepublik.app.market

// Boersengaenge aus dem Tagesindex der SEC (seit 5.93): Boersenprospekte (S-1, F-1, mit /A als Aenderung)
// und Preisfestsetzungen (424B4). Wie alle SEC-Abrufe nur mit Kontaktadresse unter Zugaenge.
object SecIpo {
    enum class Art { PROSPEKT, AENDERUNG, PREIS }

    // Eine Meldung im Tagesindex: Art, Firma, CIK, Einreichungstag (JJJJ-MM-TT), Datei im Archiv und Formular.
    data class Eintrag(
        val art: Art,
        val name: String,
        val cik: String,
        val tag: String,
        val datei: String,
        val formular: String,
    ) {
        val link: String get() = "https://www.sec.gov/Archives/$datei"
    }

    // Zeile des Tagesindex: Formular, Firmenname, CIK, Datum (JJJJMMTT) und Datei im Archiv.
    private val zeile = Regex("^(S-1/A|S-1|F-1/A|F-1|424B4)\\s+(.+?)\\s+(\\d+)\\s+(\\d{8})\\s+(edgar/data/\\S+)$")

    // Eintraege eines Tagesverzeichnisses: nur die fuenf Formtypen, doppelte Dateien nur einmal,
    // neuester Tag zuerst, gleiche Tage alphabetisch nach Firma.
    fun lesen(idx: String): List<Eintrag> = idx.lineSequence().map { it.trim() }.mapNotNull { z ->
        val m = zeile.matchEntire(z) ?: return@mapNotNull null
        val formular = m.groupValues[1]
        val art = when (formular) {
            "S-1", "F-1" -> Art.PROSPEKT
            "S-1/A", "F-1/A" -> Art.AENDERUNG
            else -> Art.PREIS
        }
        val d = m.groupValues[4]
        Eintrag(art, m.groupValues[2].trim(), m.groupValues[3], "${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6, 8)}", m.groupValues[5], formular)
    }.distinctBy { it.datei }.sortedWith(compareByDescending<Eintrag> { it.tag }.thenBy { it.name }).toList()
}
