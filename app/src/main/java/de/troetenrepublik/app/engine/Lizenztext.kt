package de.troetenrepublik.app.engine

// Bereitet die Lizenzsammlung fuer die Anzeige auf: Titel werden erkannt,
// harte Umbrueche der fest umbrochenen Texte werden gelockert.
object Lizenztext {

    data class Absatz(val text: String, val titel: Boolean)

    // Pfad unter assets
    const val DATEI = "lizenzen/THIRD_PARTY_LICENSES.txt"

    // Aufzaehlungen und Copyright-Zeilen beginnen eine eigene Zeile
    private val neuZeile = Regex("^(?:[-*+] |[0-9]+[.)] |[A-Za-z][.)] |\\([0-9A-Za-z]{1,3}\\) )")

    fun absaetze(roh: String): List<Absatz> {
        val ergebnis = mutableListOf<Absatz>()
        var block = mutableListOf<String>()
        for (zeile in roh.replace("\r", "").split("\n")) {
            if (zeile.isBlank()) {
                ergebnis.addAll(verarbeiteBlock(block))
                block = mutableListOf()
            } else {
                block.add(zeile)
            }
        }
        ergebnis.addAll(verarbeiteBlock(block))
        return ergebnis
    }

    // Wandelt einen Block ohne Leerzeilen in Absaetze; Linien ergeben Titel
    private fun verarbeiteBlock(zeilen: List<String>): List<Absatz> {
        if (zeilen.isEmpty()) return emptyList()
        if (zeilen.size >= 3 && istGleichlinie(zeilen.first()) && istGleichlinie(zeilen.last())) {
            val text = zeilen.subList(1, zeilen.size - 1).joinToString("\n") { it.trim() }
            return if (text.isBlank()) emptyList() else listOf(Absatz(text, true))
        }
        val teile = mutableListOf<Absatz>()
        var puffer = mutableListOf<String>()
        fun abschicken() {
            if (puffer.isNotEmpty()) {
                teile.add(Absatz(zusammenfuehren(puffer), false))
                puffer = mutableListOf()
            }
        }
        for (zeile in zeilen) {
            if (istLinie(zeile)) {
                if (puffer.isNotEmpty()) {
                    val titel = puffer.removeAt(puffer.size - 1).trim()
                    abschicken()
                    teile.add(Absatz(titel, true))
                }
            } else {
                puffer.add(zeile)
            }
        }
        abschicken()
        return teile
    }

    private fun istLinie(zeile: String): Boolean = istGleichlinie(zeile) || istStrichlinie(zeile)

    private fun istGleichlinie(zeile: String): Boolean {
        val t = zeile.trim()
        return t.length >= 3 && t.all { it == '=' }
    }

    private fun istStrichlinie(zeile: String): Boolean {
        val t = zeile.trim()
        return t.length >= 3 && t.all { it == '-' }
    }

    // Fuehrt umbrochene Zeilen zusammen, solange es fortlaufender Text ist
    private fun zusammenfuehren(zeilen: List<String>): String {
        val ergebnis = mutableListOf<String>()
        var aktuell = zeilen.first().trim()
        for (i in 1 until zeilen.size) {
            val naechste = zeilen[i].trim()
            if (aktuell.length >= 50 && !beginntNeu(naechste)) {
                aktuell += " " + naechste
            } else {
                ergebnis.add(aktuell)
                aktuell = naechste
            }
        }
        ergebnis.add(aktuell)
        return ergebnis.joinToString("\n")
    }

    private fun beginntNeu(zeile: String): Boolean =
        zeile.startsWith("Copyright") || neuZeile.containsMatchIn(zeile)
}
