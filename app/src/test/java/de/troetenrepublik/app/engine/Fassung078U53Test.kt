package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.ForschungTexte
import de.troetenrepublik.app.i18n.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 0.78 U53: eigene Wertungen der App beschreibend formuliert, Hinweis ohne Anlageberatung erreichbar,
// lange Reihen unveraendert wie in der Quelle.
class Fassung078U53Test {

    private fun geaendert(s: Strings): Map<String, String> = buildMap {
        for (i in 0..4) {
            put("bbStufe$i", s.bbStufe(i))
            put("bbWaageSatz$i", s.bbWaageSatz(i))
        }
        put("bbDafuer", s.bbDafuer)
        put("bbDagegen", s.bbDagegen)
        put("anBereinigt", s.anBereinigt)
        put("rdSatz", s.rdSatz("5 %"))
        put("rdSatzRand1", s.rdSatzRand("5 %", true))
        put("rdSatzRand0", s.rdSatzRand("5 %", false))
        for (art in listOf(1, -1, 2, 0)) put("bkBewertungSatz$art", s.bkBewertungSatz("5 %", art, "3 %"))
        for (e in listOf("GUENSTIG", "FAIR", "TEUER")) put("afEinordnung$e", s.afEinordnung(e))
        for (r in listOf(1, -1, 0)) put("epTendenz$r", s.epTendenz(r, "Öl"))
        for (u in listOf(1, 2)) for (l in listOf(1, -1)) for (gas in listOf(true, false)) put("epLagerSatz$u$l$gas", s.epLagerSatz(u, l, gas, false))
    }

    private val verbotenDe = listOf(
        "dafür", "dagegen", "lohnt", "Empfehl", "empfehl", "eher long", "eher short", "Eher zu", "günstig bewertet", "teuer bewertet", "fair bewertet", "bullish",
    )
    private val verbotenEn = listOf(
        "rather for", "rather against", "clearly for", "clearly against", "pay off", "pays off", "rather long", "rather short", "too cheap", "too expensive",
        "cheaply valued", "expensively valued", "bullish",
    )

    @Test
    fun keineHandlungsaufforderungUndKeineGedankenstriche() {
        for ((s, verboten) in listOf<Pair<Strings, List<String>>>(De to verbotenDe, En to verbotenEn)) {
            for ((k, t) in geaendert(s)) {
                assertTrue(k, t.isNotBlank())
                verboten.forEach { assertFalse("$k: $it", t.contains(it)) }
                listOf("—", "–", "…").forEach { assertFalse("$k: $it", t.contains(it)) }
            }
        }
    }

    @Test
    fun jedeStufeHatEinenEigenenText() {
        for (s in listOf<Strings>(De, En)) {
            assertEquals(5, (0..4).map { s.bbStufe(it) }.toSet().size)
            assertEquals(5, (0..4).map { s.bbWaageSatz(it) }.toSet().size)
        }
    }

    @Test
    fun hinweisKeineAnlageberatungErreichbar() {
        assertTrue(De.bbHinweis.contains("Keine Anlageberatung"))
        assertTrue(En.bbHinweis.contains("Not investment advice"))
        assertTrue(De.dcNote.contains("Keine Anlageberatung"))
        assertTrue(En.dcNote.contains("Not investment advice"))
        assertTrue(De.afHinweis.contains("Keine Anlageberatung"))
        assertTrue(En.afHinweis.contains("Not investment advice"))
        assertTrue(De.anHinweis("10 %").contains("Keine Anlageberatung"))
        assertTrue(En.anHinweis("10 %").contains("Not investment advice"))
    }

    @Test
    fun langreihenUnveraendertWieDieQuelle() {
        val f = listOf("src/main/assets/globus/langreihen.json", "../app/src/main/assets/globus/langreihen.json").map { File(it) }.first { it.exists() }
        val datei = Langreihen.lesen(f.readText(Charsets.UTF_8))!!
        // Norges Bank: Preisreihe mit mehr als fuenf geltenden Ziffern, Zinsen mit mehr als zwei Nachkommastellen
        val preise = datei.laender.getValue("NOR").reihen.getValue(Langreihen.Art.PREISE)
        assertTrue(preise.werte.filterNotNull().any { Math.abs(it * 1e4 - Math.rint(it * 1e4)) > 1e-6 })
        val zins = datei.laender.getValue("NOR").reihen.getValue(Langreihen.Art.ZINS)
        assertTrue(zins.werte.filterNotNull().any { Math.abs(it * 100 - Math.rint(it * 100)) > 1e-6 })
        assertFalse(ForschungTexte.text(true, "lr.gerundet", emptyList()).contains("gerundet"))
        assertFalse(ForschungTexte.text(false, "lr.gerundet", emptyList()).contains("rounded"))
    }
}
