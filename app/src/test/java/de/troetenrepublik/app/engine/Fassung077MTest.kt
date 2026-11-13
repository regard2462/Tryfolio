package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Investorenbriefe.Fundstelle
import org.junit.Assert.assertEquals
import org.junit.Test

// Investorenbriefe (0.77): Anzahl je Richtung und Auswahl durch Tipp auf einen Abschnitt der Uebersicht.
class Fassung077MTest {

    private fun f(name: String, r: Int) = Fundstelle(absender = "a", name = name, link = "https://x/$name", auszug = "Text", richtung = r)

    private val liste = listOf(f("A", 1), f("B", -1), f("C", 0), f("D", 1), f("E", 1), f("F", -1))

    @Test
    fun zaehltJeRichtung() {
        val z = Investorenbriefe.richtungen(liste)
        assertEquals(3, z.positiv)
        assertEquals(1, z.neutral)
        assertEquals(2, z.negativ)
        assertEquals(6, z.summe)
        assertEquals(3, z.von(5))
        assertEquals(2, z.von(-3))
        assertEquals(1, z.von(0))
    }

    @Test
    fun leereListeZaehltNull() {
        val z = Investorenbriefe.richtungen(emptyList())
        assertEquals(0, z.summe)
    }

    @Test
    fun auswahlFiltertUndBehaeltReihenfolge() {
        assertEquals(listOf("A", "D", "E"), Investorenbriefe.auswahl(liste, 1) { it.richtung }.map { it.name })
        assertEquals(listOf("B", "F"), Investorenbriefe.auswahl(liste, -1) { it.richtung }.map { it.name })
        assertEquals(listOf("C"), Investorenbriefe.auswahl(liste, 0) { it.richtung }.map { it.name })
    }

    @Test
    fun ohneAuswahlBleibtAlles() {
        assertEquals(liste, Investorenbriefe.auswahl(liste, null) { it.richtung })
    }

    @Test
    fun auswahlPasstZurAnzahl() {
        val z = Investorenbriefe.richtungen(liste)
        for (r in listOf(1, 0, -1)) assertEquals(z.von(r), Investorenbriefe.auswahl(liste, r) { it.richtung }.size)
    }
}
