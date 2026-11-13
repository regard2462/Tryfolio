package de.troetenrepublik.app.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Paket 077h: Richtungsdreiecke werden gezeichnet (DirectionTriangle, ChangeValue, richtungsDreieck), nie als Textzeichen.
// Der Test liest die Quelltexte und schlaegt fehl, sobald in der Oberflaeche wieder ein Dreieck oder Pfeil als Zeichen steht.
class Fassung077HTest {
    private fun quellen(): File =
        listOf("src/main/java/de/troetenrepublik/app", "../app/src/main/java/de/troetenrepublik/app").map { File(it) }.first { it.isDirectory }

    // Dreiecke und Zeigerzeichen
    private val dreiecke = "\u25B2\u25BC\u25B6\u25C0\u25B3\u25BD\u25B4\u25BE\u25B8\u25C2\u25BA\u25C4\u25E2\u25E3\u25E4\u25E5\u2B06\u2B07\u23F6\u23F7\u23F5\u23F4"
    // Pfeile, die in der Oberflaeche eine Kursrichtung zeigen wuerden
    private val pfeile = "\u2191\u2193\u2197\u2198\u2195"

    // Dateien, in denen ein Zeichen aus fremden Quelltexten gelesen wird (kein Anzeigetext)
    private val ausnahmen = setOf("market/BoersenDirekt.kt")

    private fun dateien(unter: String) =
        File(quellen(), unter).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun treffer(dateien: List<File>, zeichen: String): List<String> {
        val escape = Regex("\\\\u(?i:" + zeichen.map { "%04x".format(it.code) }.joinToString("|") + ")")
        val gefunden = ArrayList<String>()
        for (d in dateien) {
            val rel = runCatching { d.relativeTo(quellen()).path.replace('\\', '/') }.getOrDefault(d.name)
            if (rel in ausnahmen) continue
            d.readLines().forEachIndexed { i, z ->
                if (z.any { it in zeichen } || escape.containsMatchIn(z)) gefunden += "$rel:${i + 1}"
            }
        }
        return gefunden
    }

    @Test
    fun keinDreieckAlsTextzeichen() {
        val alle = dateien("")
        val t = treffer(alle, dreiecke)
        assertTrue("Dreieck als Textzeichen, stattdessen DirectionTriangle oder ChangeValue benutzen: $t", t.isEmpty())
    }

    @Test
    fun keinPfeilAlsTextzeichenInOberflaecheUndTexten() {
        val t = treffer(dateien("ui") + dateien("i18n"), pfeile)
        assertTrue("Richtungspfeil als Textzeichen in ui oder i18n, stattdessen das gezeichnete Dreieck benutzen: $t", t.isEmpty())
    }

    @Test
    fun suchtestErkenntEinDreieck() {
        val probe = File.createTempFile("probe", ".kt")
        try {
            probe.writeText("val x = \"" + '\u25B2' + " 1\"\nval y = \"\\u25BC\"\n")
            val t = treffer(listOf(probe), dreiecke)
            assertTrue(t.size == 2)
        } finally {
            probe.delete()
        }
    }
}
