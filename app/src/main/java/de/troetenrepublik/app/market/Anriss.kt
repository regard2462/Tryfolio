package de.troetenrepublik.app.market

// Anriss fuer Feeds, die nur Titel liefern (EZB, SNB, Fed; deren Bedingungen erlauben die Nutzung mit Quellenangabe):
// die ersten Absaetze nach der Ueberschrift, hoechstens 400 Zeichen, am Satzende gekuerzt. Navigation, Cookie-Hinweise
// und die .gov-Kopfzeilen fallen weg; die Fed schreibt „For release at … Share“ vor den ersten Absatz. Seiten ohne Text
// (Datenportal der SNB) ergeben einen leeren Anriss. Endet der Anriss mitten im Text, steht das in Ergebnis.gekuerzt;
// die Oberflaeche laesst ihn dann in die Blende auslaufen.
object Anriss {
    // Text des Anrisses; gekuerzt: er endet nicht an einem Satzende.
    data class Ergebnis(val text: String, val gekuerzt: Boolean = false)

    private val OHNE = Regex("<(script|style|noscript|nav|header|footer|template|svg)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BLOCK = Regex("<(p|li|h[1-4])\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val STOP = Regex("(?i)cookie|einwilligung|consent|\\.gov website|https?://|for media inquiries|©")
    private val FREIGABE = Regex("^For release at .*?(-->\\s*)+", RegexOption.IGNORE_CASE)

    fun ausHtml(html: String, titel: String): String = lesen(html, titel).text

    fun lesen(html: String, titel: String): Ergebnis {
        val bloecke = BLOCK.findAll(html.replace(OHNE, " ")).map { klartext(it.groupValues[2]) }.toList()
        val ganz = norm(titel)
        // SNB: „Medienmitteilung - Zwischenbericht …“, die Seite nennt nur den Teil nach dem Strich
        val teil = norm(titel.substringAfterLast(" - "))
        fun istTitel(b: String): Boolean {
            val n = norm(b)
            return n.isNotEmpty() && (n == ganz || (teil.length >= 12 && n.startsWith(teil)) || (n.length >= 12 && ganz.endsWith(n)))
        }
        val ab = bloecke.indexOfFirst { istTitel(it) }.let { if (it >= 0) it + 1 else 0 }
        val sb = StringBuilder()
        for (roh in bloecke.drop(ab)) {
            val b = roh.replace(FREIGABE, "").trim()
            if (b.length < 60 || STOP.containsMatchIn(b) || istTitel(b)) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(b)
            if (b.last() !in ".!?:…\"“”") sb.append('.')
            if (sb.length >= 160) break
        }
        return kuerzen(sb.toString(), 400)
    }

    private fun kuerzen(s: String, max: Int): Ergebnis {
        if (s.length <= max) return Ergebnis(s)
        val ende = Regex("[.!?](\\s|$)").findAll(s.take(max)).lastOrNull()?.range?.first
        return if (ende != null && ende > 80) Ergebnis(s.take(ende + 1)) else Ergebnis(s.take(max).substringBeforeLast(' ').trimEnd(), gekuerzt = true)
    }

    private fun norm(s: String): String = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun klartext(s: String): String = Rss.klartext(Sonderleser.entitaeten(s.replace(Regex("<[^>]+>"), " ")))
}
