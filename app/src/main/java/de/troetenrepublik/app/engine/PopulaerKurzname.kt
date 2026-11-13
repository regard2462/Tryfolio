package de.troetenrepublik.app.engine

// Kurznamen fuer die Zellen von „Beliebt“: jede Fassung ist ein ganzer Name, es wird nichts angeschnitten.
object PopulaerKurzname {
    // Allgemeine Endwoerter, ohne die der Name derselbe bleibt („Micron Technology“ wird „Micron“).
    private val ALLGEMEIN = setOf(
        "technology", "technologies", "computer", "computers", "platforms", "systems", "holdings", "holding",
        "group", "company", "corporation", "incorporated", "inc", "corp", "international", "industries",
    )

    // Fassungen eines Namens, laengste zuerst: die uebergebenen Fassungen (ganzer Name, ohne Rechtsform) und danach
    // die letzte ohne allgemeine Endwoerter. Ein Name aus einem Wort bleibt, wie er ist.
    fun kandidaten(fassungen: List<String>): List<String> {
        val aus = LinkedHashSet<String>()
        fassungen.map { it.trim() }.filter { it.isNotEmpty() }.forEach { aus += it }
        var n = aus.lastOrNull().orEmpty()
        while (true) {
            val teile = n.split(' ').filter { it.isNotEmpty() }
            if (teile.size < 2 || teile.last().trimEnd('.', ',').lowercase() !in ALLGEMEIN) break
            n = teile.dropLast(1).joinToString(" ").trimEnd(',')
            if (n.length < 3) break
            aus += n
        }
        return aus.toList()
    }

    // Die laengste Fassung, die passt; null, wenn keine passt.
    fun waehlen(kandidaten: List<String>, passt: (String) -> Boolean): String? = kandidaten.firstOrNull(passt)

    // Raster nur, wenn jeder Wert eine passende Fassung hat; sonst steht „Beliebt“ als Liste mit einer Zeile je Wert.
    fun alsRaster(namen: List<List<String>>, passt: (String) -> Boolean): Boolean =
        namen.isNotEmpty() && namen.all { waehlen(it, passt) != null }
}
