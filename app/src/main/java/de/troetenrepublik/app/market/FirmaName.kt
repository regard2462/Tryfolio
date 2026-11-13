package de.troetenrepublik.app.market

// Zuordnung eines Namens aus einer Behoerdenquelle (WARN-Meldung, Patentanmelder, Muttergesellschaft im GHGRP) zur
// Firma einer Wertpapierseite. Lieber kein Treffer als ein falscher: Der Name muss nach Abzug von Rechtsform und
// Zusaetzen genau dem Firmennamen entsprechen, oder er beginnt mit ihm und danach folgen nur Zusaetze.
object FirmaName {

    // Am Ende abzutragen: Rechtsformen und Zusaetze ohne eigene Bedeutung
    private val ENDE = setOf(
        "the", "group", "company", "companies", "co", "corp", "corporation", "inc", "incorporated", "llc", "lp", "ltd", "limited", "plc",
        "holdings", "holding", "usa", "us", "america", "americas", "north", "international", "and", "sa", "nv", "ag", "se", "na",
    )

    // Nach dem Firmennamen erlaubt, wenn der Registername mit ihm beginnt ("Amazon.com Services LLC", "Apple Operations")
    private val ZUSATZ = ENDE + setOf("services", "operations", "stores", "logistics", "enterprises", "technologies", "systems", "com", "dba")

    // Woerter des Namens ohne Rechtsform und Zusaetze am Ende, ohne Punkte, in Kleinbuchstaben
    fun woerter(name: String): List<String> {
        val w = Firmenname.worte(name).map { it.replace(Regex("\\.com$"), "").replace(".", "").replace("'", "") }.filter { it.isNotEmpty() }.toMutableList()
        if (w.size > 1 && w.first() == "the") w.removeAt(0)
        while (w.size > 1 && w.last() in ENDE) w.removeAt(w.size - 1)
        return w
    }

    // Zusammengeschriebener Schluessel ("ExxonMobil" und "Exxon Mobil" gleich); leer bei zu kurzem Namen
    fun schluessel(name: String): String = woerter(name).joinToString("").takeIf { it.length >= 4 }.orEmpty()

    // Schluessel der Firma aus allen bekannten Namen (Katalogname, Name der Seite, langer Name), ohne Doppelte
    fun schluessel(namen: List<String>): List<String> = namen.map { schluessel(it) }.filter { it.isNotEmpty() }.distinct()

    // Passt der Name aus der Quelle zu einem der Firmenschluessel?
    fun passt(quellenName: String, firma: List<String>): Boolean {
        if (firma.isEmpty()) return false
        val w = woerter(quellenName)
        if (w.isEmpty()) return false
        if (w.joinToString("") in firma) return true
        // Beginnt mit dem Firmennamen, danach nur Zusaetze
        for (i in w.size - 1 downTo 1) {
            if (w.subList(i, w.size).all { it in ZUSATZ } && w.subList(0, i).joinToString("") in firma) return true
        }
        return false
    }

    // Alle Namen, unter denen die Seite die Firma kennt: Katalogname, langer Name und Name der Seite
    fun namenDerSeite(symbol: String, name: String, langerName: String?): List<String> {
        val katalog = Catalog.loaded()?.aktien?.firstOrNull { it.s == symbol }?.n
        return listOfNotNull(katalog, langerName?.takeIf { it.isNotBlank() }, name.takeIf { it.isNotBlank() }).distinct()
    }
}
