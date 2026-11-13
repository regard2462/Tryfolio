package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.widgetKartenBytes
import de.troetenrepublik.app.widgetKartenLesen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Gemerkte Widget-Karten gelten nur fuer die Fassung, die sie gerechnet hat.
class Fassung077KartenStandTest {
    private val karten = mapOf("meistgekauft" to WidgetKarte("Meistgekauft", am = 5L))

    @Test
    fun gleicheFassungBleibt() {
        assertEquals(karten, widgetKartenLesen(widgetKartenBytes(karten, "0.77"), "0.77"))
    }

    @Test
    fun andereFassungWirdNeuGerechnet() {
        assertTrue(widgetKartenLesen(widgetKartenBytes(karten, "0.76"), "0.77").isEmpty())
    }

    @Test
    fun kartenAusDerAppBleibenMitAbgelaufenerFrist() {
        val alt = mapOf("vorschlag" to WidgetKarte("Vorschlagsdepot", am = 5L), "meistgekauft" to WidgetKarte("Meistgekauft", am = 5L))
        val neu = widgetKartenLesen(widgetKartenBytes(alt, "0.76"), "0.77")
        assertEquals(setOf("vorschlag"), neu.keys)
        assertEquals(1L, neu.getValue("vorschlag").am)
    }

    @Test
    fun ablageOhneFassungWirdNeuGerechnet() {
        val alt = """{"meistgekauft":{"titel":"Meistgekauft","am":5}}""".toByteArray(Charsets.UTF_8)
        assertTrue(widgetKartenLesen(alt, "0.77").isEmpty())
    }

    @Test
    fun firmennameOhneRechtsform() {
        assertEquals("ADARx Pharmaceuticals", de.troetenrepublik.app.widgetFirmenname("ADARx Pharmaceuticals, Inc."))
        assertEquals("Kodiak Sciences", de.troetenrepublik.app.widgetFirmenname("Kodiak Sciences Inc."))
        assertEquals("Amrize", de.troetenrepublik.app.widgetFirmenname("Amrize Ltd"))
        assertEquals("Howard Hughes Holdings", de.troetenrepublik.app.widgetFirmenname("Howard Hughes Holdings"))
        assertEquals("Cemex SAB DE CV", de.troetenrepublik.app.widgetFirmenname("Cemex SAB DE CV"))
    }

    @Test
    fun kaputteAblageIstLeer() {
        assertTrue(widgetKartenLesen("x".toByteArray(Charsets.UTF_8), "0.77").isEmpty())
    }
}
