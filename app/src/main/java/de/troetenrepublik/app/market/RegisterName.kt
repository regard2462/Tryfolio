package de.troetenrepublik.app.market

// Zuordnung einer Firma zu den Namen in einem Register (Studien, Rueckrufe): Namensglaettung wie bei der FDA, dazu
// je Register eine kleine Liste bekannter Toechter und Marken. Lieber kein Treffer als ein falscher.
object RegisterName {

    // Name der Firma und bekannte Toechter, normalisiert. Die Liste je Register ordnet normalisierte Firmennamen (oder
    // deren erste ein oder zwei Woerter) weiteren Namen zu.
    fun namen(firma: String, alias: Map<String, List<String>>): List<String> {
        val n = Fda.normal(firma)
        if (n.isBlank()) return emptyList()
        val kurz = n.split(' ').take(2).joinToString(" ")
        return (listOf(n) + (alias[n] ?: alias[kurz] ?: alias[n.substringBefore(' ')]).orEmpty()).distinct()
    }

    // Grossbuchstaben ohne Akzente und Satzzeichen, "&" als AND, einzelne Leerzeichen, vorn und hinten ein Leerzeichen.
    fun text(s: String): String {
        val roh = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD)
            .replace(de.troetenrepublik.app.engine.Muster.re("\\p{M}"), "").uppercase().replace("&", " AND ")
        return " " + roh.replace(Regex("[^A-Z0-9]+"), " ").trim() + " "
    }

    // Suchbegriff fuer das Register: die ersten beiden Woerter eines Namens.
    fun suchbegriffe(namen: List<String>): List<String> = namen.map { it.split(' ').take(2).joinToString(" ") }.distinct()

    private val TOCHTER = listOf(" SUBSIDIARY ", " AFFILIATE ", " DIVISION ", " BRAND ", " COMPANY OF ", " UNIT ")

    // Ob ein Registername zu einem der Namen passt. Gleich oder mit dem Namen als Wortanfang gilt immer, ebenso
    // „... a subsidiary of <NAME>“. Mit enthaelt gilt der Name auch mitten im Registernamen („AMERICAN HONDA MOTOR“).
    // Eine Firma mit „KGaA“ im Registernamen gehoert nicht zu einem Namen ohne „KGaA“ („Merck“ gegen „Merck KGaA“).
    fun passt(registerName: String, namen: List<String>, enthaelt: Boolean = false): Boolean {
        val t = text(registerName)
        if (t.isBlank()) return false
        val f = Fda.normal(registerName)
        return namen.any { n ->
            if (n.isBlank()) return@any false
            if (t.contains(" KGAA ") && !n.contains("KGAA")) return@any false
            f == n || f.startsWith("$n ") || (enthaelt && t.contains(" $n ")) ||
                (TOCHTER.any { t.contains(it) } && t.contains(" OF $n "))
        }
    }
}
