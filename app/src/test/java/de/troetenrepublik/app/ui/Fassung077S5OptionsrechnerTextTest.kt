package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Optionsrechner
import de.troetenrepublik.app.engine.Optionsrechner.Art
import de.troetenrepublik.app.engine.Optionsrechner.Bein
import de.troetenrepublik.app.engine.Optionsrechner.Vorlage
import de.troetenrepublik.app.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Kernaussage und Klartextzeile des Optionsrechners aus festen Beispielzahlen (Long Call, Bull-Call-Spread, Covered Call).
class Fassung077S5OptionsrechnerTextTest {
    private val kurs = 30.33
    private fun fest(t: String) = t.replace(' ', ' ')
    private fun preis(v: Double) = fest(Fmt.price(v, "USD"))
    private fun geld(v: Double) = fest(Fmt.moneyIn(v, "USD", 0))

    private fun satz(beine: List<Bein>): Pair<Optionsrechner.Ergebnis, String> {
        val e = Optionsrechner.auswerten(beine, kurs, emptyList(), 1_800_000_000L, 1_797_000_000L)
        return e to kernSatz("SMCI", "27. Nov.", kurs, e, beine)
    }

    private val longCall = listOf(Bein(Art.CALL, 1, 30.0, 2.08, 0.74))
    private val bull = listOf(Bein(Art.CALL, 1, 30.0, 2.08, 0.74), Bein(Art.CALL, -1, 35.0, 0.50, 0.70))
    private val covered = listOf(Bein(Art.AKTIE, 1, 0.0, 30.33, 0.0), Bein(Art.CALL, -1, 35.0, 2.10, 0.60))

    private fun fuerSprache(l: Lang, block: () -> Unit) {
        val alt = I18n.lang
        I18n.lang = l
        try { block() } finally { I18n.lang = alt }
    }

    @Test
    fun longCall() = fuerSprache(Lang.DE) {
        val (e, s) = satz(longCall)
        assertEquals("Call kaufen: Gewinn über ${preis(32.08)}", klarText(Vorlage.LONG_CALL, e, longCall))
        assertTrue(s, s.startsWith("Einsatz ${geld(208.0)}. Gewinn, wenn SMCI am 27. Nov. über ${preis(32.08)} steht ("))
        assertTrue(s, s.contains(Fmt.signedPct((32.08 / kurs - 1) * 100, 1).replace(' ', ' ')))
        assertTrue(s, s.endsWith("Größter Verlust ${geld(208.0)}."))
        assertFalse(s, s.contains("Größter Gewinn"))
    }

    @Test
    fun bullCallSpread() = fuerSprache(Lang.DE) {
        val (e, s) = satz(bull)
        assertEquals("Call kaufen und höheren Call verkaufen: Gewinn über ${preis(31.58)}, höchstens ${geld(342.0)}", klarText(Vorlage.BULL_CALL_SPREAD, e, bull))
        assertTrue(s, s.startsWith("Einsatz ${geld(158.0)}. Gewinn, wenn SMCI am 27. Nov. über ${preis(31.58)} steht ("))
        assertTrue(s, s.endsWith("Größter Gewinn ${geld(342.0)}. Größter Verlust ${geld(158.0)}."))
    }

    @Test
    fun coveredCall() = fuerSprache(Lang.DE) {
        val (e, s) = satz(covered)
        assertEquals("Aktie halten und Call verkaufen: ${geld(210.0)} Einnahme, Gewinn begrenzt ab ${preis(35.0)}", klarText(Vorlage.COVERED_CALL, e, covered))
        assertTrue(s, s.startsWith("Einnahme ${geld(210.0)}. Gewinn, wenn SMCI am 27. Nov. über ${preis(28.23)} steht ("))
        assertTrue(s, s.contains("Größter Gewinn ${geld(677.0)}."))
        assertTrue(s, s.endsWith("Größter Verlust ${geld(2823.0)}."))
    }

    @Test
    fun englischeSaetze() = fuerSprache(Lang.EN) {
        val (e, s) = satz(longCall)
        assertEquals("Buy a call: profit above ${preis(32.08)}", klarText(Vorlage.LONG_CALL, e, longCall))
        assertTrue(s, s.startsWith("Cost ${geld(208.0)}. Profit if SMCI is above ${preis(32.08)} on 27. Nov. ("))
        assertTrue(s, s.endsWith("Largest loss ${geld(208.0)}."))
    }

    @Test
    fun unbegrenzterVerlustUndEigeneOptionen() = fuerSprache(Lang.DE) {
        val shortCall = listOf(Bein(Art.CALL, -1, 30.0, 2.0, 0.5))
        val (e, s) = satz(shortCall)
        assertTrue(s, s.startsWith("Einnahme ${geld(200.0)}."))
        assertTrue(s, s.endsWith("Verlust unbegrenzt."))
        assertNull(klarText(Vorlage.EIGENE, e, shortCall))
    }

    @Test
    fun zweiGewinnschwellen() = fuerSprache(Lang.DE) {
        val straddle = listOf(Bein(Art.CALL, 1, 30.0, 2.0, 0.5), Bein(Art.PUT, 1, 30.0, 1.5, 0.5))
        val (e, s) = satz(straddle)
        assertTrue(s, s.contains("außerhalb von ${preis(26.5)} und ${preis(33.5)} steht."))
        assertEquals("Call und Put kaufen: Gewinn über ${preis(33.5)} oder unter ${preis(26.5)}", klarText(Vorlage.STRADDLE, e, straddle))
    }
}
