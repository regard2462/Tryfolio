package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Regelreste: kein festes Datum im Begruendungstext des Ausblicks, einheitliche Pfeilhoehen in der Oberflaeche.
class Fassung109RegelresteTest {

    private val monatJahr = Regex("""(Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|January|February|March|June|July|October|December) 20\d\d""")

    @Test fun ausblickTreiberOhneFestesFutureDatum() {
        for (t in De.wwTreiber + En.wwTreiber) assertFalse(t, monatJahr.containsMatchIn(t))
        assertEquals(De.wwTreiber.size, En.wwTreiber.size)
        assertTrue(De.wwTreiber.first().contains("Hormus"))
        assertTrue(En.wwTreiber.first().contains("Hormuz"))
    }

    @Test fun pfeilhoehenNurUeberDieKonstanten() {
        val wurzel = listOf("src/main/java/de/troetenrepublik/app/ui", "app/src/main/java/de/troetenrepublik/app/ui", "../app/src/main/java/de/troetenrepublik/app/ui")
            .map { File(it) }.first { it.isDirectory }
        val fest = Regex("""ChevronRight\(([^)]*,\s*)?height\s*=\s*[0-9.]+\.dp""")
        val treffer = wurzel.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Glyphs.kt" }
            .filter { fest.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue("Feste Pfeilhoehe in $treffer", treffer.isEmpty())
    }
}
