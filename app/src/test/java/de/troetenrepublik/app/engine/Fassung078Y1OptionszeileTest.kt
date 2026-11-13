package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Aufbau der Optionszeilen: Name im Kopf, Stellen der Praemie, Warnschwelle der Spanne, Spalten der Paare und Tabelle.
class Fassung078Y1OptionszeileTest {

    @Test
    fun nameStattKuerzel() {
        assertEquals("Novo Nordisk", Optionszeile.name("NOV.DE", null, null, "Novo Nordisk"))
        assertEquals("Novo Nordisk A/S", Optionszeile.name("NOV.DE", null, "Novo Nordisk A/S", "Novo Nordisk"))
        assertEquals("Apple", Optionszeile.name("AAPL", "Apple", "Apple Inc.", "Apple Inc"))
        // Ein Name, der nur das Kuerzel wiederholt, zaehlt nicht
        assertEquals("Novo Nordisk", Optionszeile.name("NOV.DE", "NOV.DE", "", "Novo Nordisk"))
        assertEquals("XYZ", Optionszeile.name("XYZ", null, " ", "XYZ"))
    }

    @Test
    fun praemieMitZweiStellenWennNichtFeiner() {
        assertEquals(2, Optionszeile.praemieStellen(0.48))
        assertEquals(2, Optionszeile.praemieStellen(0.35))
        assertEquals(2, Optionszeile.praemieStellen(3.2))
        assertEquals(2, Optionszeile.praemieStellen(12.0))
        assertEquals(2, Optionszeile.praemieStellen(0.0))
        assertEquals(3, Optionszeile.praemieStellen(0.125))
        assertEquals(4, Optionszeile.praemieStellen(0.0375))
        assertEquals(4, Optionszeile.praemieStellen(0.00005))
    }

    @Test
    fun spanneUeberZwanzigProzentWarnt() {
        assertFalse(Optionszeile.spanneHoch(null))
        assertFalse(Optionszeile.spanneHoch(0.099))
        assertFalse(Optionszeile.spanneHoch(0.20))
        assertTrue(Optionszeile.spanneHoch(0.201))
        assertTrue(Optionszeile.spanneHoch(0.803))
    }

    @Test
    fun paareJeZeileNachBreite() {
        // Vier schmale Paare passen nebeneinander: (800 - 3 * 30) / 4 = 177
        assertEquals(4, Optionszeile.spalten(listOf(100, 120, 90, 170), 800, 30))
        // Das breiteste Paar braucht mehr als ein Viertel: zwei Zeilen mit je zwei
        assertEquals(2, Optionszeile.spalten(listOf(100, 120, 90, 300), 800, 30))
        // Genau passend zaehlt als passend: (800 - 30) / 2 = 385
        assertEquals(2, Optionszeile.spalten(listOf(385, 10, 10, 10), 800, 30))
        // Auch die Haelfte reicht nicht: je Paar eine Zeile
        assertEquals(1, Optionszeile.spalten(listOf(386, 10, 10, 10), 800, 30))
        // Fuenf Paare: alle, sonst drei, sonst zwei
        assertEquals(5, Optionszeile.spalten(listOf(50, 50, 50, 50, 50), 800, 30))
        assertEquals(3, Optionszeile.spalten(listOf(200, 50, 50, 50, 50), 800, 30))
        assertEquals(2, Optionszeile.spalten(listOf(300, 50, 50, 50, 50), 800, 30))
        // Drei Paare
        assertEquals(3, Optionszeile.spalten(listOf(200, 200, 200), 800, 30))
        assertEquals(2, Optionszeile.spalten(listOf(300, 200, 200), 800, 30))
        // Ein Paar oder keines
        assertEquals(1, Optionszeile.spalten(listOf(100), 800, 30))
        assertEquals(1, Optionszeile.spalten(emptyList(), 800, 30))
        // Grosse Schrift auf schmalem Geraet: Bezeichnung breiter als die halbe Zeile
        assertEquals(1, Optionszeile.spalten(listOf(160, 250, 330, 380), 640, 24))
    }

    @Test
    fun tabelleNurWennAlleSpaltenPassen() {
        assertTrue(Optionszeile.tabellePasst(listOf(200, 200, 150, 160), 800, 30))
        assertTrue(Optionszeile.tabellePasst(listOf(200, 200, 150, 160), 800, 30))
        assertFalse(Optionszeile.tabellePasst(listOf(200, 200, 150, 161), 800, 30))
        assertFalse(Optionszeile.tabellePasst(emptyList(), 800, 30))
    }
}
