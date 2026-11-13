package de.troetenrepublik.app.engine

// Stichpunktzeilen („• “) in Mitteilungen und Texten der Oberflaeche. Reine Funktionen ohne Compose, damit sie sich
// einfach testen lassen.
object Stichpunkte {

    // Gehoert die Zeile zu einer Aufzaehlung mit Stichpunkt?
    fun istPunkt(zeile: String): Boolean = zeile.trimStart().startsWith("• ")

    // Text in Zeilen, je Zeile ob es eine Stichpunktzeile ist und ihr Inhalt ohne Punkt und Rand.
    fun zeilen(text: String): List<Pair<Boolean, String>> =
        text.split('\n').map { zeile -> if (istPunkt(zeile)) true to zeile.substringAfter("• ").trim() else false to zeile }

    // Text fuer Systemmitteilungen: der Punkt steht am Rand, U+00A0 haelt den Abstand zum Text. Den haengenden
    // Einzug der Folgezeilen setzt Android ueber punktAbsaetze; ohne ihn beginnen sie am Rand wie der Punkt.
    fun fuerMitteilung(text: String): String =
        zeilen(text).joinToString("\n") { (punkt, rest) -> if (punkt) "•\u00A0" + rest else rest }

    // Bereiche der Stichpunkt-Absaetze in einem Text aus fuerMitteilung (Anfang bis vor dem Zeilenumbruch), fuer
    // den haengenden Einzug.
    fun punktAbsaetze(text: String): List<IntRange> {
        val bereiche = mutableListOf<IntRange>()
        var anfang = 0
        for (zeile in text.split('\n')) {
            if (zeile.startsWith("•")) bereiche += anfang until anfang + zeile.length
            anfang += zeile.length + 1
        }
        return bereiche
    }
}
