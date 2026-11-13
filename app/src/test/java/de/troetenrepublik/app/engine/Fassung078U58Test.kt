package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Entnahmeplan: Zielvermoegen als Vorgabe, Achse bei frueh aufgebrauchtem Geld, Auswahlreihen; Levermann-Schwelle beschreibend.
class Fassung078U58Test {
    @Test fun zielvermoegenIstVorgabe() {
        assertEquals(2, PortfolioTools.entnahmeVermoegen(-1, 24_900.0, 800_000.0))
        // Ohne Ziel wie bisher
        assertEquals(0, PortfolioTools.entnahmeVermoegen(-1, 24_900.0, null))
        assertEquals(1, PortfolioTools.entnahmeVermoegen(-1, 500.0, 0.0))
        // Eigene Wahl bleibt
        assertEquals(0, PortfolioTools.entnahmeVermoegen(0, 24_900.0, 800_000.0))
        assertEquals(3, PortfolioTools.entnahmeVermoegen(3, 24_900.0, 800_000.0))
    }

    @Test fun achseBeiFruehAufgebrauchtemGeld() {
        val median = doubleArrayOf(24_900.0, 900.0, 0.0, 0.0, 0.0, 0.0)
        val oben = doubleArrayOf(24_900.0, 3_000.0, 1_200.0, 0.0, 0.0, 0.0)
        assertEquals(3_000.0, PortfolioTools.entnahmeAchsenMax(median, oben)!!, 0.0)
        // Geld reicht lange: normale Achse
        assertNull(PortfolioTools.entnahmeAchsenMax(doubleArrayOf(100.0, 90.0, 80.0, 70.0), doubleArrayOf(100.0, 120.0, 140.0, 150.0)))
        assertNull(PortfolioTools.entnahmeAchsenMax(doubleArrayOf(0.0, 0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0, 0.0)))
    }

    @Test fun auswahlReiheBrichtUmWennZuBreit() {
        assertTrue(PortfolioTools.auswahlZuBreit(listOf("Fester Betrag", "Fester Prozentsatz", "Leitplanken", "Nach Restlaufzeit")))
        assertFalse(PortfolioTools.auswahlZuBreit(listOf("15J", "20J", "25J")))
        // Lange Listen (Laender, Jahre) bleiben waagerecht blaetterbar
        assertFalse(PortfolioTools.auswahlZuBreit((1..20).map { "Land $it lang" }))
    }

    @Test fun levermannSchwelleBeschreibend() {
        val texte = listOf(
            De.levKaufAb(4, true), De.levKaufAb(7, false), En.levKaufAb(4, true), En.levKaufAb(7, false),
        )
        texte.forEach {
            assertFalse(it, it.contains("Kauf ab") || it.contains("Buy from"))
            assertTrue(it, it.contains("4") || it.contains("7"))
        }
        assertEquals("Schwelle der Methode: 4 Punkte bei großen Werten", De.levKaufAb(4, true))
        assertNotNull(En.levKaufAb(7, false))
    }
}
