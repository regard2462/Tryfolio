package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.ui.Fmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Anzeige in US-Dollar: Kurse und Werte umgerechnet, Gebuchtes und Eingaben bleiben in Euro.
class AnzeigeWaehrungTest {

    @After
    fun zurueck() {
        Fmt.stufe = 0
        Fmt.usdJeEuro = 0.0
        I18n.lang = Lang.DE
    }

    @Test
    fun ohneKursBleibtEsBeimEuro() {
        I18n.lang = Lang.DE
        Fmt.stufe = 1
        assertFalse(Fmt.inUsd)
        assertEquals("€", Fmt.symbol)
        assertEquals("1.234,56 €", Fmt.money(1234.56))
    }

    @Test
    fun dollarMitKursDeutsch() {
        I18n.lang = Lang.DE
        Fmt.stufe = 1
        Fmt.usdJeEuro = 1.1
        assertTrue(Fmt.inUsd)
        assertEquals("$", Fmt.symbol)
        assertEquals("1.358,02 $", Fmt.money(1234.56))
        assertEquals("+11,00 $", Fmt.signedMoney(10.0))
        assertEquals("−5,50 $", Fmt.money(-5.0))
        assertEquals("0,5500 $", Fmt.price(0.5))
        assertEquals("55,0 Tsd. $", Fmt.compactMoney(50_000.0))
        // Feste Euro-Betraege
        assertEquals("1.234,56 €", Fmt.euro(1234.56))
        assertEquals("+10,00 €", Fmt.signedEuro(10.0))
        assertEquals("10,00 €", Fmt.priceEuro(10.0))
        assertEquals("50,0 Tsd. €", Fmt.compactEuro(50_000.0))
        // Kurse mit eigener Waehrung (Heimatboerse, Kennzahlen) bleiben, wie sie sind
        assertEquals("200,00 $", Fmt.price(200.0, "$"))
        assertEquals("3,10 CHF", Fmt.price(3.1, "CHF"))
    }

    @Test
    fun dollarMitKursEnglisch() {
        I18n.lang = Lang.EN
        Fmt.stufe = 1
        Fmt.usdJeEuro = 1.1
        assertEquals("$1,358.02", Fmt.money(1234.56))
        assertEquals("−$5.50", Fmt.money(-5.0))
        assertEquals("€1,234.56", Fmt.euro(1234.56))
    }

    @Test
    fun ausschaltenZeigtWiederEuro() {
        I18n.lang = Lang.DE
        Fmt.usdJeEuro = 1.1
        Fmt.stufe = 1
        assertEquals("110,00 $", Fmt.money(100.0))
        Fmt.stufe = 0
        assertEquals("100,00 €", Fmt.money(100.0))
        assertEquals(100.0, Fmt.anzeige(100.0), 0.0)
    }
}
