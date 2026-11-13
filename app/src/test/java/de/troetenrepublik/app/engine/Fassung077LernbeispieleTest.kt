package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.77] Beispielwerte fuer die Lektionen des Finanzwissens und Mitarbeiter-Verlauf aus Wikidata.
class Fassung077LernbeispieleTest {

    private fun lesen(b: ByteArray?): JsonObject = Json.parseToJsonElement(b!!.decodeToString()).jsonObject

    @Test fun ersteAktieMitWertZaehlt() {
        val a = Lernbeispiele.Aktie("Alpha", 31.23, null, 24.34, 1.234, -3.0)
        val b = Lernbeispiele.Aktie("Beta Corp", 12.0, 2.456, 50.0, 0.9, 10.0)
        val o = lesen(Lernbeispiele.zusammenstellen(listOf(a, b)))
        assertEquals("Alpha", o["pe"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(31.2, o["pe"]!!.jsonObject["wert"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertEquals("Beta Corp", o["div"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(2.46, o["div"]!!.jsonObject["wert"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertEquals(1.23, o["beta"]!!.jsonObject["wert"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertEquals(-3.0, o["marge"]!!.jsonObject["wert"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
    }

    @Test fun ohneWerteKeineAntwort() {
        assertNull(Lernbeispiele.zusammenstellen(emptyList()))
        assertNull(Lernbeispiele.zusammenstellen(listOf(Lernbeispiele.Aktie("X", null, null, null, null, null))))
        assertNull(Lernbeispiele.zusammenstellen(listOf(Lernbeispiele.Aktie("", 10.0, 1.0, 1.0, 1.0, 1.0))))
    }

    @Test fun leereWebAntwortOhneStand() {
        Lernbeispiele.setzen(null)
        assertEquals("{}", Lernbeispiele.fuerWebansicht().decodeToString())
        assertTrue(Lernbeispiele.WEB_PFAD.endsWith("beispiele.json"))
    }
}
