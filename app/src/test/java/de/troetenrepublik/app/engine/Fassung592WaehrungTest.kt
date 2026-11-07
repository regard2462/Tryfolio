package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.Geldanzeige
import de.troetenrepublik.app.ui.Heimatwaehrung
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Waehrungen beim Antippen (5.92): eigene Folge mit Originalwaehrung an beliebiger Stelle, Mehrfachauswahl und
// Reihenfolge; Depotwert, Heimatwaehrung einer Aktie und fremde Betraege folgen ihr.
class Fassung592WaehrungTest {
    private val kurse = mapOf("EUR" to 1.0, "USD" to 1.10, "JPY" to 160.0, "GBP" to 0.85, "INR" to 95.0, "CNY" to 8.0)
    private val O = Waehrungsfolge.ORIGINAL

    @After
    fun zurueck() {
        Fmt.stufe = 0
        Fmt.usdJeEuro = 0.0
        Fmt.basis = "EUR"
        Fmt.basisJeEuro = 0.0
        Fmt.folge = emptyList()
        Fmt.kurse = emptyMap()
        I18n.lang = Lang.DE
    }

    @Test fun vorgabeWieBisher() {
        assertEquals(listOf("EUR", "USD", O), Waehrungsfolge.wirksam(emptyList(), "EUR"))
        assertEquals(listOf("CNY", "USD", "EUR"), Waehrungsfolge.stufen(emptyList(), "CNY"))
        assertEquals(3, Waehrungsfolge.originalNach(emptyList(), "CNY"))
        assertEquals(listOf("EUR", "USD"), Fmt.stufen)
        assertTrue(Fmt.mitOriginal)
        assertEquals(2, Fmt.originalNach)
    }

    @Test fun lesenUndSchreiben() {
        assertEquals(listOf(O, "JPY", "GBP"), Waehrungsfolge.lesen("ORIG, jpy,GBP,GBP,XYZ"))
        assertEquals("ORIG,JPY,GBP", Waehrungsfolge.schreiben(listOf(O, "JPY", "GBP")))
        assertTrue(Waehrungsfolge.lesen("").isEmpty())
        // Nur Original ohne echte Waehrung gilt als Vorgabe
        assertEquals(Waehrungsfolge.vorgabe("EUR"), Waehrungsfolge.wirksam(listOf(O), "EUR"))
        assertEquals(Waehrungsfolge.MAX, Waehrungsfolge.lesen("EUR,USD,JPY,GBP,CHF,CNY,INR,ORIG").size)
    }

    @Test fun auswaehlenUndOrdnen() {
        var f = listOf("EUR", "USD", O)
        f = Waehrungsfolge.umschalten(f, "USD")
        assertEquals(listOf("EUR", O), f)
        // Die letzte echte Waehrung bleibt
        assertEquals(f, Waehrungsfolge.umschalten(f, "EUR"))
        f = Waehrungsfolge.umschalten(f, "JPY")
        assertEquals(listOf("EUR", O, "JPY"), f)
        f = Waehrungsfolge.verschieben(f, 1, -1)
        assertEquals(listOf(O, "EUR", "JPY"), f)
        assertEquals(f, Waehrungsfolge.verschieben(f, 0, -1))
        assertEquals(listOf(O, "JPY", "EUR"), Waehrungsfolge.verschieben(f, 2, -1))
        // Neue gewaehlte Waehrung kommt nach vorn, die Vorgabe bleibt leer
        assertEquals(listOf("GBP", O, "EUR", "JPY"), Waehrungsfolge.mitBasis(f, "GBP"))
        assertEquals(f, Waehrungsfolge.mitBasis(f, "JPY"))
        assertTrue(Waehrungsfolge.mitBasis(emptyList(), "GBP").isEmpty())
    }

    @Test fun depotwertInYenUndPfund() {
        I18n.lang = Lang.DE
        Fmt.kurse = kurse
        Fmt.folge = listOf(O, "JPY", "GBP")
        assertEquals(listOf("JPY", "GBP"), Fmt.stufen)
        assertEquals(Fmt.mitSymbol("1.600,00", "¥"), Fmt.money(10.0))
        Fmt.stufe = 1
        assertEquals(Fmt.mitSymbol("8,50", "£"), Fmt.money(10.0))
        // Ohne Kurs fuer Yen bleibt es beim Euro
        Fmt.stufe = 0
        Fmt.kurse = emptyMap()
        assertEquals(Fmt.mitSymbol("10,00", "€"), Fmt.money(10.0))
    }

    @Test fun heimatwaehrungAnIhrerStelle() {
        // Folge Original, Euro, Dollar: Original steht vor der ersten Stufe, also nach der letzten
        Fmt.folge = listOf(O, "EUR", "USD")
        assertEquals(0, Fmt.originalNach)
        var a = 0 to false
        val folge = (1..4).map { a = Heimatwaehrung.naechste(a.first, 2, heimatMoeglich = true, inHeimat = a.second); a }
        assertEquals(listOf(1 to false, 1 to true, 0 to false, 1 to false), folge)
        // Folge Euro, Original, Dollar: die Heimatwaehrung kommt zwischen Euro und Dollar
        Fmt.folge = listOf("EUR", O, "USD")
        assertEquals(1, Fmt.originalNach)
        assertEquals(0 to true, Heimatwaehrung.naechste(0, 2, heimatMoeglich = true, inHeimat = false))
        assertEquals(1 to false, Heimatwaehrung.naechste(0, 2, heimatMoeglich = true, inHeimat = true))
        assertEquals(0 to false, Heimatwaehrung.naechste(1, 2, heimatMoeglich = true, inHeimat = false))
        // Nur eine Stufe: Euro und Heimatwaehrung im Wechsel
        assertEquals(0 to true, Heimatwaehrung.naechste(0, 1, heimatMoeglich = true, inHeimat = false, nach = 1))
        assertEquals(0 to false, Heimatwaehrung.naechste(0, 1, heimatMoeglich = true, inHeimat = true, nach = 1))
    }

    @Test fun ohneOriginalKeineHeimatwaehrung() {
        assertEquals("CNY", Heimatwaehrung.waehrung("CNY", kurse))
        Fmt.folge = listOf("EUR", "USD")
        assertFalse(Fmt.mitOriginal)
        assertNull(Heimatwaehrung.waehrung("CNY", kurse))
        // Fremde Betraege beginnen dann in der angezeigten Waehrung
        assertEquals("EUR", Geldanzeige.startEintrag(0))
    }

    @Test fun fremdeBetraegeFolgenDerEinstellung() {
        I18n.lang = Lang.DE
        var wahl = O
        fun anzeige() = Geldanzeige(kurse, wahl, { wahl = it }, listOf(O, "JPY", "USD"))
        assertEquals("INR", anzeige().ziel("INR"))
        anzeige().weiter("INR")
        assertEquals("JPY", wahl)
        assertEquals(950.0 / 95.0 * 160.0, anzeige().umrechnen(950.0, "INR").first, 1e-9)
        anzeige().weiter("INR")
        assertEquals("USD", wahl)
        anzeige().weiter("INR")
        assertEquals(O, wahl)
        // Ein Betrag in Dollar: Original und Dollar zeigen dasselbe, der naechste Tipp springt darueber hinweg
        wahl = "JPY"
        anzeige().weiter("USD")
        assertEquals("USD", wahl)
        anzeige().weiter("USD")
        assertEquals("JPY", wahl)
        // Ohne Kurs bleibt der Betrag in der Originalwaehrung
        assertEquals("ZAR", Geldanzeige(kurse, "JPY", {}, listOf(O, "JPY")).ziel("ZAR"))
    }
}
