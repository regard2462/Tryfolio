package de.troetenrepublik.app.engine

import org.junit.Test
import org.junit.Assert.assertEquals

// [Laenderblatt 0.70] Konfliktseiten in der App-Sprache
class LaenderblattTest {
    private fun land(a2: String, a3: String, en: String, de: String) = GlobusLand(a2, a3, de, en, emptyArray(), IntArray(0))
    private val karte = GlobusKarte(
        listOf(land("RU", "RUS", "Russia", "Russland"), land("UA", "UKR", "Ukraine", "Ukraine"), land("MM", "MMR", "Myanmar", "Myanmar")),
        emptyList(),
    )

    @Test
    fun regierungenWerdenLaendernamen() {
        assertEquals("Russland", Konflikte.seiteAnzeige("Government of Russia (Soviet Union)", karte, true))
        assertEquals("Ukraine", Konflikte.seiteAnzeige("Government of Ukraine", karte, true))
        assertEquals("Russia", Konflikte.seiteAnzeige("Government of Russia (Soviet Union)", karte, false))
        assertEquals("Myanmar", Konflikte.seiteAnzeige("Government of Myanmar (Burma)", karte, true))
    }

    @Test
    fun unbekanntesBleibtKurz() {
        assertEquals("Regierung Freedonia", Konflikte.seiteAnzeige("Government of Freedonia (Old)", karte, true))
        assertEquals("KNU", Konflikte.seiteAnzeige("KNU", karte, true))
    }
}
