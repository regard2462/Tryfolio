package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Halter

// Halten die bewertenden Haeuser die Aktie selbst? (5.94) Abgleich der Analystenhaeuser mit den groessten
// institutionellen Haltern (Halter, bis zu zehn) ueber den Namenskern: Rechtsform und Fuellwoerter fallen weg, ein
// paar uebliche Kurznamen werden ausgeschrieben. Ein Haus passt, wenn ein Kern mit dem anderen beginnt. Den Einstand
// melden die Institute nicht; gezeigt wird deshalb die Kursaenderung seit dem Meldestichtag.
object AnalystenHalter {

    private val KURZ = listOf(
        Regex("\\bbofa\\b") to "bank of america",
        Regex("\\bj\\s?p\\s?morgan\\b") to "jpmorgan",
        Regex("\\bciti\\b") to "citigroup",
        Regex("\\bsocgen\\b") to "societe generale",
    )

    private val FUELL = setOf(
        "inc", "llc", "lp", "llp", "plc", "ag", "sa", "se", "nv", "co", "corp", "corporation", "group", "holdings", "holding",
        "securities", "capital", "markets", "the", "and", "company", "management", "advisors", "advisers", "ltd", "limited",
        "research", "global", "partners",
    )

    fun kern(name: String): String {
        var s = name.lowercase().replace(Regex("[.,&'()/-]"), " ").replace(Regex("\\s+"), " ").trim()
        for ((r, ersatz) in KURZ) s = s.replace(r, ersatz)
        return s.split(' ').filter { it.isNotBlank() && it !in FUELL }.joinToString(" ")
    }

    fun passend(haus: String, halter: List<Halter.Eintrag>): Halter.Eintrag? {
        val a = kern(haus)
        if (a.length < 3) return null
        return halter.firstOrNull { h ->
            val b = kern(h.name)
            b.length >= 3 && (a == b || b.startsWith("$a ") || a.startsWith("$b "))
        }
    }
}
