package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.data.WidgetStimmung
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Widget "Stimmung" 0.77: Übernahme der Wahl, Auswahlliste und Zeilen der Ansicht "Alle".
class Fassung077Ad10StimmungWidgetTest {
    private fun w(titel: String, wert: Int = 50) = WidgetStimmung(titel, wert, "Neutral", 2)

    private val alleDaten = WidgetDaten.STIMMUNG_REIHE.associateWith { w("T_$it") }

    @Test fun uebernahmeStelltAktienAufGesamt() {
        assertEquals(WidgetDaten.STIMMUNG_GESAMT, WidgetWahl.uebernahme077(WidgetDaten.STIMMUNG_AKTIEN))
    }

    @Test fun uebernahmeLaesstAndereWahlenUndNeueWidgets() {
        assertNull(WidgetWahl.uebernahme077(null))
        assertNull(WidgetWahl.uebernahme077(WidgetDaten.STIMMUNG_GESAMT))
        assertNull(WidgetWahl.uebernahme077(WidgetDaten.STIMMUNG_KRYPTO))
        assertNull(WidgetWahl.uebernahme077(WidgetDaten.STIMMUNG_ALLE))
        assertNull(WidgetWahl.uebernahme077(WidgetDaten.STIMMUNG_VIX))
    }

    @Test fun neuesWidgetZeigtGesamtstimmung() {
        assertEquals(WidgetDaten.STIMMUNG_GESAMT, WidgetWahl.stimmung(null))
        assertEquals(WidgetDaten.STIMMUNG_GESAMT, WidgetWahl.stimmung("gibtEsNicht"))
    }

    @Test fun jedeBarometerWahlBleibtErhalten() {
        for (k in WidgetDaten.STIMMUNG_REIHE) assertEquals(k, WidgetWahl.stimmung(k))
        assertEquals(WidgetDaten.STIMMUNG_ALLE, WidgetWahl.stimmung(WidgetDaten.STIMMUNG_ALLE))
    }

    @Test fun auswahlListeWieDieApp() {
        assertEquals(
            listOf("gesamt", "aktien", "aaii", "krypto", "wsb", "sorgen", "vix", "sicher", "putcall", "alle"),
            WidgetWahl.STIMMUNG_WAHL,
        )
        assertEquals(WidgetDaten.STIMMUNG_ALLE, WidgetWahl.STIMMUNG_WAHL.last())
    }

    @Test fun alleZeigtGesamtstimmungZuerstInReihenfolgeDerApp() {
        val z = WidgetWahl.alleZeilen(alleDaten, 400, false)
        assertEquals(WidgetDaten.STIMMUNG_REIHE.map { "T_$it" }, z.map { it.titel })
        assertEquals("T_gesamt", z.first().titel)
    }

    @Test fun alleLaesstFehlendeBarometerWeg() {
        val daten = mapOf("krypto" to w("Krypto"), "gesamt" to w("Gesamtstimmung"), "vix" to w("Schwankung"))
        assertEquals(listOf("Gesamtstimmung", "Krypto", "Schwankung"), WidgetWahl.alleZeilen(daten, 400, false).map { it.titel })
        assertTrue(WidgetWahl.alleZeilen(emptyMap(), 400, false).isEmpty())
    }

    @Test fun alleZeigtGleicheNamenNurEinmal() {
        // Aktien USA springt ohne CNN auf die Gesamtstimmung ein und trüge dann deren Namen
        val daten = mapOf("gesamt" to w("Gesamtstimmung", 37), "aktien" to w("Gesamtstimmung", 37), "krypto" to w("Krypto"))
        assertEquals(listOf("Gesamtstimmung", "Krypto"), WidgetWahl.alleZeilen(daten, 400, false).map { it.titel })
    }

    @Test fun alleZeilenZahlNachHoehe() {
        assertEquals(1, WidgetWahl.alleAnzahl(80, false))
        assertEquals(2, WidgetWahl.alleAnzahl(110, false))
        assertEquals(2, WidgetWahl.alleAnzahl(100, false))
        assertEquals(1, WidgetWahl.alleAnzahl(100, true))
        assertEquals(5, WidgetWahl.alleAnzahl(180, false))
        assertEquals(WidgetWahl.ALLE_MAX, WidgetWahl.alleAnzahl(1000, false))
        assertEquals(3, WidgetWahl.alleZeilen(alleDaten, 118, false).size)
    }

    @Test fun stufeNurBeiGenugBreite() {
        assertFalse(WidgetWahl.alleMitStufe(110))
        assertFalse(WidgetWahl.alleMitStufe(249))
        assertTrue(WidgetWahl.alleMitStufe(250))
    }

    @Test fun gespeicherteAelterWidgetDatenBleibenLesbar() {
        // Stand vor 0.77: nur gesamt, aktien, krypto; ohne die neuen Felder und mit unbekannten Feldern
        val roh = """{"stimmung":{"gesamt":{"titel":"Gesamtstimmung","wert":37,"stufe":"Angst","stufeNr":1,"quelle":"Alle Barometer"},"aktien":{"titel":"Aktien USA","wert":41,"stufe":"Neutral","stufeNr":2}},"zukunft":1}"""
        val d = Json { ignoreUnknownKeys = true }.decodeFromString(WidgetDaten.serializer(), roh)
        assertEquals(setOf("gesamt", "aktien"), d.stimmung.keys)
        assertEquals(37, d.stimmung.getValue("gesamt").wert)
        assertEquals(listOf("Gesamtstimmung", "Aktien USA"), WidgetWahl.alleZeilen(d.stimmung, 400, false).map { it.titel })
    }
}
