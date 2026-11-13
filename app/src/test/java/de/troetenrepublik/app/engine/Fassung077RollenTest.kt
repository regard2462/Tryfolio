package de.troetenrepublik.app.engine

import de.troetenrepublik.app.ui.Ident
import de.troetenrepublik.app.ui.SeitenMerker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Paket 077o: Die Wertpapierseite rollt ruckelfrei. Gerechnetes bleibt im Merker der Seite (SeitenMerker.gerechnet), und
// jeder Eintrag der LazyColumn traegt einen festen key (Suchtest ueber den Quelltext).
class Fassung077RollenTest {
    private fun quellen(): File =
        listOf("src/main/java/de/troetenrepublik/app/ui", "../app/src/main/java/de/troetenrepublik/app/ui").map { File(it) }.first { it.isDirectory }

    // ---- gerechnet

    @Test fun gleicheEingabenRechnenNurEinmal() {
        val m = SeitenMerker()
        var aufrufe = 0
        val a = m.gerechnet("k", listOf(1, "x")) { aufrufe++; listOf(1, 2, 3) }
        val b = m.gerechnet("k", listOf(1, "x")) { aufrufe++; listOf(9) }
        assertEquals(1, aufrufe)
        assertSame(a, b)
    }

    @Test fun andereEingabenRechnenNeu() {
        val m = SeitenMerker()
        var aufrufe = 0
        assertEquals("a1", m.gerechnet("k", listOf(1)) { aufrufe++; "a1" })
        assertEquals("a2", m.gerechnet("k", listOf(2)) { aufrufe++; "a2" })
        assertEquals(2, aufrufe)
        // Danach gilt der neue Stand
        assertEquals("a2", m.gerechnet("k", listOf(2)) { aufrufe++; "a3" })
        assertEquals(2, aufrufe)
    }

    @Test fun schluesselSindUnabhaengig() {
        val m = SeitenMerker()
        assertEquals(1, m.gerechnet("a", listOf(0)) { 1 })
        assertEquals(2, m.gerechnet("b", listOf(0)) { 2 })
        assertEquals(1, m.gerechnet("a", listOf(0)) { 99 })
        assertEquals(2, m.gerechnet("b", listOf(0)) { 99 })
    }

    @Test fun ergebnisNullBleibtGemerkt() {
        val m = SeitenMerker()
        var aufrufe = 0
        val a: String? = m.gerechnet("k", listOf(1)) { aufrufe++; null }
        val b: String? = m.gerechnet("k", listOf(1)) { aufrufe++; "neu" }
        assertEquals(null, a)
        assertEquals(null, b)
        assertEquals(1, aufrufe)
    }

    @Test fun identGiltNurFuerDieselbeInstanz() {
        val liste = listOf(1, 2, 3)
        val kopie = listOf(1, 2, 3)
        assertEquals(Ident(liste), Ident(liste))
        assertNotEquals(Ident(liste), Ident(kopie))
        val m = SeitenMerker()
        var aufrufe = 0
        m.gerechnet("k", listOf(Ident(liste))) { aufrufe++; 1 }
        m.gerechnet("k", listOf(Ident(liste))) { aufrufe++; 1 }
        assertEquals(1, aufrufe)
        m.gerechnet("k", listOf(Ident(kopie))) { aufrufe++; 1 }
        assertEquals(2, aufrufe)
    }

    @Test fun zustandBleibtJeSchluesselDerselbe() {
        val m = SeitenMerker()
        val a = m.zustand("x") { 1 }
        a.value = 5
        assertEquals(5, m.zustand("x") { 1 }.value)
    }

    // ---- Suchtests ueber ui/InstrumentScreen.kt

    // Argumente eines Aufrufs ab der oeffnenden Klammer bis zur passenden schliessenden (Zeichenketten uebersprungen)
    private fun argumente(text: String, offen: Int): String {
        var tiefe = 0
        var i = offen
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '(' -> tiefe++
                ')' -> { tiefe--; if (tiefe == 0) return text.substring(offen, i + 1) }
            }
            i++
        }
        return text.substring(offen)
    }

    private fun aufrufe(text: String): List<Pair<Int, String>> {
        // Auch item { ... } ohne Klammern zaehlt: Es hat keinen key
        val treffer = Regex("""(?<![A-Za-z0-9_.])(item|items|itemsIndexed)\s*[({]""").findAll(text)
        return treffer.map { m ->
            val zeile = text.substring(0, m.range.first).count { it == '\n' } + 1
            zeile to (if (text[m.range.last] == '(') argumente(text, m.range.last) else "{")
        }.toList()
    }

    @Test fun jederEintragDerSeiteTraegtEinenKey() {
        val text = File(quellen(), "InstrumentScreen.kt").readText()
        val alle = aufrufe(text)
        assertTrue("keine Eintraege gefunden", alle.size > 20)
        val ohne = alle.filter { !Regex("""\bkey\s*=""").containsMatchIn(it.second) }.map { it.first }
        assertTrue("item/items ohne key in InstrumentScreen.kt, Zeilen $ohne", ohne.isEmpty())
    }

    @Test fun jederEintragDerSeiteTraegtEinenContentTypeOderIstEinzeln() {
        // Listen mit mehreren Eintraegen gleicher Art (items, itemsIndexed) brauchen den contentType
        val text = File(quellen(), "InstrumentScreen.kt").readText()
        val ohne = Regex("""(?<![A-Za-z0-9_.])(items|itemsIndexed)\(""").findAll(text).map { m ->
            val zeile = text.substring(0, m.range.first).count { it == '\n' } + 1
            zeile to argumente(text, m.range.last)
        }.filter { !it.second.contains("contentType") }.map { it.first }.toList()
        assertTrue("items ohne contentType, Zeilen $ohne", ohne.isEmpty())
    }

    @Test fun suchtestErkenntEintragOhneKey() {
        val probe = "LazyColumn {\n    item { Text(\"a\") }\n    item(key = \"b\") { Text(\"b\") }\n    items(liste, key = { it }) { }\n}\n"
        val ohne = aufrufe(probe).filter { !Regex("""\bkey\s*=""").containsMatchIn(it.second) }.map { it.first }
        assertEquals(listOf(2), ohne)
    }

    @Test fun rollpositionWirdNurInLambdasGelesen() {
        val text = File(quellen(), "InstrumentScreen.kt").readText()
        assertFalse("firstVisibleItemIndex im Aufbau der Seite", text.contains("firstVisibleItemIndex"))
        val zeilen = text.lines().filter { it.contains("canScrollBackward") }
        assertTrue(zeilen.isNotEmpty())
        assertTrue("canScrollBackward ausserhalb eines Lambdas: $zeilen", zeilen.all { it.contains("{ !liste.canScrollBackward }") })
    }

    @Test fun karteneinzeilenSindEintraegeDerListe() {
        // Die Karten, die ihre Zeilen als einzelne Eintraege geben, tragen ihre Eintraege ueber listenAbschnitt in die Liste
        val text = File(quellen(), "InstrumentScreen.kt").readText()
        listOf(
            "chancenAbschnitt(merker)", "abhaengigkeitenAbschnitt(merker)", "investorenBriefeAbschnitt(merker)", "eodhdAbschnitt(merker)",
            "renditeRisikoAbschnitt(merker)", "etfDurchschauAbschnitt(merker)", "indexKomponentenAbschnitt(merker)",
            "stimmrechteGbAbschnitt(merker)", "fdaAbschnitte(merker)", "behoerdenAbschnitt(merker)", "lobbyAbschnitt(merker)",
        ).forEach { assertTrue("fehlt: $it", text.contains(it)) }
    }
}
