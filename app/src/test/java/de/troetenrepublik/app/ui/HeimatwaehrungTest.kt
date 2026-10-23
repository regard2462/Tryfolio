package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeimatwaehrungTest {

    // Einheiten je Euro, wie die App sie von Yahoo laedt.
    private val kurse = mapOf("EUR" to 1.0, "USD" to 1.10, "CNY" to 8.0, "HKD" to 8.5, "GBP" to 0.85, "DKK" to 7.46)

    @After
    fun zurueck() {
        Fmt.stufe = 0
        Fmt.usdJeEuro = 0.0
        Fmt.basis = "EUR"
        Fmt.basisJeEuro = 0.0
        I18n.lang = Lang.DE
    }

    @Test fun heimatwaehrungNurWennSieWederEuroNochDollarIst() {
        assertEquals("CNY", Heimatwaehrung.waehrung("CNY", kurse))
        assertEquals("HKD", Heimatwaehrung.waehrung("HKD", kurse))
        assertEquals("DKK", Heimatwaehrung.waehrung("DKK", kurse))
        assertNull(Heimatwaehrung.waehrung("EUR", kurse))
        assertNull(Heimatwaehrung.waehrung("USD", kurse))
        assertNull(Heimatwaehrung.waehrung(null, kurse))
        // Pence zaehlen als Pfund, US-Cent als Dollar.
        assertEquals("GBP", Heimatwaehrung.waehrung("GBp", kurse))
        assertNull(Heimatwaehrung.waehrung("USX", kurse))
        // Ohne Wechselkurs bleibt es bei Euro und Dollar.
        assertNull(Heimatwaehrung.waehrung("ILA", kurse))
    }

    @Test fun folgeEuroDollarHeimat() {
        // Euro als Waehrung: Euro, Dollar, Heimatwaehrung, wieder Euro.
        var a = 0 to false
        val folge = (1..4).map { a = Heimatwaehrung.naechste(a.first, 2, heimatMoeglich = true, inHeimat = a.second); a }
        assertEquals(listOf(1 to false, 1 to true, 0 to false, 1 to false), folge)
        // Werte in Euro oder Dollar: nur Euro und Dollar.
        assertEquals(0 to false, Heimatwaehrung.naechste(1, 2, heimatMoeglich = false, inHeimat = false))
    }

    @Test fun yuanAlsWaehrung() {
        I18n.lang = Lang.DE
        Fmt.basis = "CNY"
        Fmt.basisJeEuro = 8.0
        Fmt.usdJeEuro = 1.1
        // Stufen Yuan, Dollar, Euro. Der Euro ist dann keine Heimatwaehrung deutscher Aktien, er ist schon Stufe.
        assertEquals(listOf("CNY", "USD", "EUR"), Fmt.stufen)
        assertEquals("80,00 CNY", Fmt.money(10.0))
        Fmt.stufe = 1
        assertEquals("11,00 $", Fmt.money(10.0))
        Fmt.stufe = 2
        assertEquals("10,00 €", Fmt.money(10.0))
        assertTrue(Fmt.letzteStufe)
        assertNull(Heimatwaehrung.waehrung("EUR", kurse))
        assertNull(Heimatwaehrung.waehrung("CNY", kurse))
        assertEquals("HKD", Heimatwaehrung.waehrung("HKD", kurse))
        // Ohne Kurs fuer Yuan bleibt es beim Euro.
        Fmt.stufe = 0
        Fmt.basisJeEuro = 0.0
        assertEquals("10,00 €", Fmt.money(10.0))
        // US-Dollar als Waehrung: Dollar, dann Euro.
        Fmt.basis = "USD"
        assertEquals(listOf("USD", "EUR"), Fmt.stufen)
        assertEquals("11,00 $", Fmt.money(10.0))
    }

    @Test fun betraegeInDerHeimatwaehrung() {
        I18n.lang = Lang.DE
        val yuan = KursFormat("CNY", 8.0)
        assertTrue(yuan.inHeimat)
        assertEquals("80,00 CNY", yuan.preis(10.0))
        assertEquals("8.000,00 CNY", yuan.geld(1000.0))
        assertEquals("+40,00 CNY", yuan.geldMitVorzeichen(5.0))
        assertEquals("−40,00 CNY", yuan.geldMitVorzeichen(-5.0))
        assertEquals("8,00 Mio. CNY", yuan.kompakt(1_000_000.0))
        assertEquals("850,00 HK$", KursFormat("HKD", 8.5).preis(100.0))
        // Ohne Heimatwaehrung wie ueberall: Euro, mit eingeschaltetem Dollar in Dollar.
        val sonst = KursFormat(null, null)
        assertFalse(sonst.inHeimat)
        assertEquals("10,00 €", sonst.preis(10.0))
        Fmt.stufe = 1
        Fmt.usdJeEuro = 1.1
        assertEquals("11,00 $", sonst.preis(10.0))
        assertEquals("80,00 CNY", yuan.preis(10.0))
        I18n.lang = Lang.EN
        assertEquals("80.00 CNY", yuan.preis(10.0))
        assertEquals("850.00 HK$", KursFormat("HKD", 8.5).preis(100.0))
    }
}
