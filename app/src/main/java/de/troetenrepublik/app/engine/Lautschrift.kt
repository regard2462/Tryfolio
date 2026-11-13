package de.troetenrepublik.app.engine

// Lautschrift aus Beschreibungstexten entfernen (0.78): Angaben wie „[ˈæpəlˌɪŋk]“ oder „/ˈæpəl/“ stehen in
// Lexikon-Auszuegen hinter dem Namen und stoeren im kurzen Text der App. Eckige Klammern und Schraegstrich-Paare
// fallen nur weg, wenn sie Zeichen der Lautschrift enthalten; Jahreszahlen, Fussnoten oder Kuerzel in Klammern bleiben.
object Lautschrift {

    // Zeichen, die nur in Lautschrift vorkommen: IPA-Erweiterungen, Betonungs- und Laengenzeichen, einzelne Buchstaben
    private fun laut(c: Char): Boolean =
        c in 'ɐ'..'ʯ' || c == 'ˈ' || c == 'ˌ' || c == 'ː' || c == 'ˑ' ||
            c == 'æ' || c == 'ŋ' || c == 'θ' || c == 'ð' || c == 'œ' || c == 'ø' || c in '̀'..'ͯ'

    private val ECKIG = Regex("""\s*\[([^\[\]]{1,80})\]""")
    private val SCHRAEG = Regex("""\s*/([^/\s][^/]{0,60})/""")
    // Reste in runden Klammern nach dem Entfernen: fuehrende Trennzeichen, leere Klammern
    private val KLAMMER_ANFANG = Regex("""\(\s*[;,]\s*""")
    private val KLAMMER_LEER = Regex("""\s*\(\s*\)""")
    private val LEER_VOR_ZEICHEN = Regex("""\s+([,.;:)])""")
    private val MEHRFACH = Regex("""[ \t]{2,}""")

    fun ohne(text: String): String {
        if (text.none { laut(it) }) return text
        var t = ECKIG.replace(text) { m -> if (m.groupValues[1].any { laut(it) }) "" else m.value }
        t = SCHRAEG.replace(t) { m -> if (m.groupValues[1].any { laut(it) }) "" else m.value }
        t = KLAMMER_ANFANG.replace(t, "(")
        t = KLAMMER_LEER.replace(t, "")
        t = LEER_VOR_ZEICHEN.replace(t, "$1")
        return MEHRFACH.replace(t, " ").trim()
    }
}
