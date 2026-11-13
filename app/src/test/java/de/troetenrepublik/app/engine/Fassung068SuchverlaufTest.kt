package de.troetenrepublik.app.engine

import org.junit.Assert.*
import org.junit.Test

// Suchverlauf: Speichern, Begrenzen und Rangfolge aus zuletzt und oft gesucht (0.68)
class Fassung068SuchverlaufTest {
    private val jetzt = 1_800_000_000_000L
    private val tag = 86_400_000L

    @Test
    fun lesenUeberspringtFehlerhafteZeilen() {
        assertTrue(Suchverlauf.lesen("").isEmpty())
        assertTrue(Suchverlauf.rangfolge(emptyList(), jetzt).isEmpty())
        val kaputt = "AAPL\n" +
            "AAPL\tApple\tzwei\t100\n" +
            "AAPL\tApple\t2\tspaeter\n" +
            "\tApple\t2\t100\n" +
            "AAPL\tApple\t0\t100\n" +
            "AAPL\tApple\t5\t100\n" +
            "AAPL\tAnders\t9\t200"
        val liste = Suchverlauf.lesen(kaputt)
        assertEquals(1, liste.size)
        assertEquals("Apple", liste[0].name)
        assertEquals(5, liste[0].anzahl)
    }

    @Test
    fun textUndLesenAlsRundweg() {
        val liste = listOf(
            Suchverlauf.Eintrag("AAPL", "App\tple", 2, 100),
            Suchverlauf.Eintrag("BTC-EUR", "Bit\ncoin", 4, 200),
        )
        val zurueck = Suchverlauf.lesen(Suchverlauf.text(liste))
        assertEquals(listOf("AAPL", "BTC-EUR"), zurueck.map { it.symbol })
        assertEquals("App ple", zurueck[0].name)
        assertEquals("Bit coin", zurueck[1].name)
        assertEquals(2, zurueck[0].anzahl)
        assertEquals(200L, zurueck[1].zuletzt)
    }

    @Test
    fun merkenZaehltHochUndAktualisiert() {
        val start = listOf(Suchverlauf.Eintrag("AAPL", "Apple", 2, 100))
        val hoch = Suchverlauf.merken(start, " AAPL ", "Apple Inc.", 500)
        assertEquals(1, hoch.size)
        assertEquals(3, hoch[0].anzahl)
        assertEquals(500L, hoch[0].zuletzt)
        assertEquals("Apple Inc.", hoch[0].name)
        // Leerer neuer Name behaelt den alten Namen
        val ohneNamen = Suchverlauf.merken(hoch, "AAPL", " ", 900)
        assertEquals(4, ohneNamen[0].anzahl)
        assertEquals(900L, ohneNamen[0].zuletzt)
        assertEquals("Apple Inc.", ohneNamen[0].name)
        val zwei = Suchverlauf.merken(ohneNamen, "SAP.DE", "SAP", 901)
        assertEquals(listOf("SAP.DE", "AAPL"), zwei.map { it.symbol })
    }

    @Test
    fun merkenBegrenztUndBehaeltHaeufige() {
        val einmalige = (1 until Suchverlauf.MAX_GESPEICHERT).map { i -> Suchverlauf.Eintrag("A$i", "Wert $i", 1, jetzt - 2 * tag) }
        val haeufig = Suchverlauf.Eintrag("SAP.DE", "SAP", 12, jetzt - 10 * tag)
        val nach = Suchverlauf.merken(einmalige + haeufig, "NEU", "Neuer Wert", jetzt)
        assertEquals(Suchverlauf.MAX_GESPEICHERT, nach.size)
        assertTrue(nach.any { it.symbol == "SAP.DE" })
        assertTrue(nach.any { it.symbol == "NEU" })
        assertEquals(Suchverlauf.MAX_GESPEICHERT - 2, nach.count { it.symbol.startsWith("A") })
    }

    @Test
    fun rangfolgeMischtZuletztUndHaeufig() {
        val liste = listOf(
            Suchverlauf.Eintrag("A4", "Vier", 1, jetzt - 4 * tag),
            Suchverlauf.Eintrag("AAPL", "Apple", 1, jetzt),
            Suchverlauf.Eintrag("A5", "Fuenf", 1, jetzt - 5 * tag),
            Suchverlauf.Eintrag("BTC-EUR", "Bitcoin", 12, jetzt - 10 * tag),
            Suchverlauf.Eintrag("MSFT", "Microsoft", 1, jetzt - tag),
            Suchverlauf.Eintrag("SAP.DE", "SAP", 1, jetzt - 2 * tag),
            Suchverlauf.Eintrag("A3", "Drei", 1, jetzt - 3 * tag),
            Suchverlauf.Eintrag("A6", "Sechs", 1, jetzt - 6 * tag),
        )
        assertEquals(listOf("AAPL", "MSFT", "SAP.DE", "BTC-EUR", "A3"), Suchverlauf.rangfolge(liste, jetzt).map { it.symbol })
    }

    @Test
    fun rangfolgeMitWenigenZeigtAlleJuengsteZuerst() {
        val liste = listOf(
            Suchverlauf.Eintrag("BTC-EUR", "Bitcoin", 9, 100),
            Suchverlauf.Eintrag("SAP.DE", "SAP", 1, 300),
            Suchverlauf.Eintrag("AAPL", "Apple", 1, 500),
        )
        assertEquals(listOf("AAPL", "SAP.DE", "BTC-EUR"), Suchverlauf.rangfolge(liste, jetzt).map { it.symbol })
    }

    @Test
    fun entfernenWirftNurDasSymbolRaus() {
        val liste = listOf(
            Suchverlauf.Eintrag("AAPL", "Apple", 1, 5),
            Suchverlauf.Eintrag("SAP.DE", "SAP", 2, 6),
        )
        assertEquals(listOf("SAP.DE"), Suchverlauf.entfernen(liste, "AAPL").map { it.symbol })
        assertEquals(2, Suchverlauf.entfernen(liste, "MSFT").size)
    }
}
