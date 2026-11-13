package de.troetenrepublik.app.desktop

import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.PdfText
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// Haertetest von Hand: alle Meldungen eines Ordners lesen (-Dkongress.ordner=<pfad>). Schreibt je Meldung die Zahl
// der Geschaefte und die Geschaefte selbst nach build/kongress-massen.tsv; ohne Ordner wird nichts getan.
class KongressMassenTest {
    @Test
    fun alleMeldungen() {
        val ordner = System.getProperty("kongress.ordner")?.let { File(it) }
        assumeTrue(ordner != null && ordner.isDirectory)
        val aus = StringBuilder()
        ordner!!.listFiles { f -> f.name.endsWith(".pdf") }!!.sortedBy { it.name }.forEach { f ->
            val g = Kongress.geschaefte(PdfText.zeilen(f.readBytes()))
            aus.append(f.nameWithoutExtension).append('\t').append(g.size).append('\n')
            g.forEach { aus.append("\t").append(listOf(it.inhaber, it.kuerzel ?: "-", it.typ, it.art, it.datum, it.von, it.bis ?: "-", it.wert).joinToString("|")).append('\n') }
        }
        File("build/kongress-massen.tsv").writeText(aus.toString())
    }
}
