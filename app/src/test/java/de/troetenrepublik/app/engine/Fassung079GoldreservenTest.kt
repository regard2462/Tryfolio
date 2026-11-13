package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.GoldReservenTexte
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Goldreserven der Zentralbanken je Land (IWF IRFCL): Datei lesen, Rangfolge, Umrechnung in Tonnen, Kaeufer und Verkaeufer,
// Texte, mitgelieferte Datei samt Kopie, Registrierung.
class Fassung079GoldreservenTest {

    private fun datei(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private fun repoDatei(name: String) = listOf(name, "../$name").map { File(it) }.first { it.exists() }

    // AAA 1000 t (vor 12 Monaten 900 t), BBB 500 t (550 t), CCC 100 t (ohne Vorwert), DDD 10 t (unveraendert)
    private val text = """
        {"stand":"2026-11-14","quelle":"Test","laender":[
          {"c":"AAA","en":"Alpha","de":"Alpha-Land","m":"2026-09","u":32150746.6,"u12":28935671.94},
          {"c":"BBB","en":"Beta","m":"2026-09","u":16075373.3,"u12":17682910.63},
          {"c":"CCC","en":"Gamma","m":"2026-08","u":3215074.66},
          {"c":"DDD","en":"Delta","m":"2026-09","u":321507.466,"u12":321507.466}]}
    """.trimIndent()

    @Test fun dateiWirdGelesen() {
        val d = GoldReserven.lesen(text)!!
        assertEquals(4, d.laender.size)
        assertEquals("2026-09", d.laender[0].m)
        assertEquals("Alpha-Land", d.laender[0].name(true))
        assertEquals("Alpha", d.laender[0].name(false))
        // ohne deutschen Namen steht der englische da
        assertEquals("Beta", d.laender[1].name(true))
        assertNull(d.laender[2].u12)
    }

    @Test fun kaputteDateienWerdenAbgelehnt() {
        assertNull(GoldReserven.lesen("kaputt"))
        assertNull(GoldReserven.lesen(text.replace("\"stand\":\"2026-11-14\"", "\"stand\":\"gestern\"")))
        // Bestand von 0
        assertNull(GoldReserven.lesen(text.replace("\"u\":321507.466,", "\"u\":0,")))
        // Monat fehlt oder ist unsinnig
        assertNull(GoldReserven.lesen(text.replace("\"m\":\"2026-08\",", "")))
        assertNull(GoldReserven.lesen(text.replace("\"m\":\"2026-08\"", "\"m\":\"2026-13\"")))
        // Wert vor 12 Monaten von 0
        assertNull(GoldReserven.lesen(text.replace("\"u12\":17682910.63", "\"u12\":0")))
    }

    @Test fun dateiOhneLaenderIstGueltigUndZeigtNichts() {
        val d = GoldReserven.lesen("{\"stand\":\"2026-11-14\",\"quelle\":\"x\"}")!!
        assertFalse(GoldReserven.hat(d))
        assertNull(GoldReserven.rang(d))
        assertNull(GoldReserven.haeufigsterMonat(d))
        assertFalse(GoldReserven.hat(null))
        assertTrue(GoldReserven.hat(GoldReserven.lesen(text)))
    }

    @Test fun tonnenAusFeinunzen() {
        assertEquals(32150.7466, GoldReserven.UNZEN_JE_TONNE, 1e-9)
        val d = GoldReserven.lesen(text)!!
        assertEquals(1000.0, d.laender[0].tonnen, 1e-6)
        assertEquals(900.0, d.laender[0].tonnenVor!!, 1e-6)
        assertEquals(10.0, d.laender[3].tonnen, 1e-6)
        // Die USA melden rund 261,5 Mio. Unzen, das sind rund 8133 Tonnen
        assertEquals(8133.5, 261_499_000.0 / GoldReserven.UNZEN_JE_TONNE, 0.1)
    }

    @Test fun rangfolgeMitAnteilen() {
        val r = GoldReserven.rang(GoldReserven.lesen(text)!!)!!
        assertEquals(1610.0, r.summe, 1e-6)
        assertEquals(listOf("AAA", "BBB", "CCC", "DDD"), r.eintraege.map { it.land.c })
        assertEquals(1000.0 / 1610 * 100, r.eintraege[0].anteil, 1e-6)
        assertEquals(1500.0 / 1610 * 100, r.spitze(2), 1e-6)
        assertEquals(1500.0, r.spitzeTonnen(2), 1e-6)
        assertEquals(100.0, r.eintraege.sumOf { it.anteil }, 1e-9)
        // Reihenfolge der Datei ist egal
        val umgekehrt = GoldReserven.lesen(text)!!.let { GoldReserven.Datei(it.stand, it.quelle, it.laender.reversed()) }
        assertEquals(listOf("AAA", "BBB", "CCC", "DDD"), GoldReserven.rang(umgekehrt)!!.eintraege.map { it.land.c })
    }

    @Test fun kaeuferUndVerkaeufer() {
        val a = GoldReserven.aenderungen(GoldReserven.lesen(text)!!)
        // CCC hat keinen Wert von vor 12 Monaten
        assertEquals(listOf("AAA", "BBB", "DDD"), a.map { it.land.c })
        assertEquals(100.0, a[0].tonnen, 1e-6)
        assertEquals(-50.0, a[1].tonnen, 1e-6)
        assertEquals(listOf("AAA"), GoldReserven.kaeufer(a, 5).map { it.land.c })
        assertEquals(listOf("BBB"), GoldReserven.verkaeufer(a, 5).map { it.land.c })
        // unveraenderte Bestaende sind weder Kaeufer noch Verkaeufer
        assertFalse(GoldReserven.kaeufer(a, 5).any { it.land.c == "DDD" })
        assertFalse(GoldReserven.verkaeufer(a, 5).any { it.land.c == "DDD" })
        // Begrenzung auf n, groesste zuerst
        val viele = (1..8).joinToString(",") { i ->
            """{"c":"L$i","en":"Land $i","m":"2026-09","u":${(1000 + i * 100) * 32150.7466},"u12":${1000 * 32150.7466}}"""
        }
        val d = GoldReserven.lesen("""{"stand":"2026-11-14","laender":[$viele]}""")!!
        val k = GoldReserven.kaeufer(GoldReserven.aenderungen(d), 5)
        assertEquals(listOf("L8", "L7", "L6", "L5", "L4"), k.map { it.land.c })
    }

    @Test fun haeufigsterMonat() {
        val d = GoldReserven.lesen(text)!!
        assertEquals("2026-09", GoldReserven.haeufigsterMonat(d))
        // Gleichstand: der juengere Monat
        val zwei = GoldReserven.Datei(d.stand, d.quelle, d.laender.take(3).filter { it.c != "BBB" })
        assertEquals("2026-09", GoldReserven.haeufigsterMonat(zwei))
    }

    @Test fun textePassenZusammen() {
        assertEquals(GoldReservenTexte.schluessel(true), GoldReservenTexte.schluessel(false))
        val k = GoldReservenTexte.schluessel(true)
        listOf("titel", "kern", "rang", "alle", "weitere", "netto.titel", "netto.unter", "netto.kauf", "netto.verkauf", "info").forEach { assertTrue(it, it in k) }
        listOf(true, false).forEach { de ->
            k.forEach { key ->
                val t = GoldReservenTexte.text(de, key)
                assertTrue(key, t.isNotEmpty())
                assertFalse(key, t.contains("—") || t.contains(" – ") || t.contains(";") || t.contains("?"))
            }
        }
        assertEquals("Goldreserven der Zentralbanken", GoldReservenTexte.text(true, "titel"))
        assertEquals("Central bank gold reserves", GoldReservenTexte.text(false, "titel"))
        assertEquals("Käufe und Verkäufe in 12 Monaten", GoldReservenTexte.text(true, "netto.titel"))
        assertEquals("Alle Länder (73)", GoldReservenTexte.text(true, "alle", listOf("73")))
        assertEquals("USA hält am meisten Gold: 8.133 t (22 % der gemeldeten Bestände). Die zehn größten Halter besitzen zusammen 30.000 t (80 %).",
            GoldReservenTexte.text(true, "kern", listOf("USA", "8.133", "22 %", "30.000", "80 %")))
        assertTrue(GoldReservenTexte.text(false, "info", listOf("September 2026", "73")).contains("September 2026"))
    }

    @Test fun mitgelieferteDateiUndKopie() {
        val a = datei("welt/gold.json")
        val s = a.readText()
        val d = GoldReserven.lesen(s)
        assertNotNull(d)
        assertTrue(d!!.laender.size > 40)
        // Kopie unter daten/ ist byteweise gleich, ohne Wagenruecklauf
        assertArrayEquals(a.readBytes(), repoDatei("daten/welt/gold.json").readBytes())
        assertFalse(s.contains("\r"))
        // keine Doppelten, keine Nicht-Laender, keine unplausiblen Werte
        assertEquals(d.laender.size, d.laender.map { it.c }.toSet().size)
        assertTrue(d.laender.all { it.c.length == 3 && it.c.all { ch -> ch in 'A'..'Z' } })
        assertTrue(d.laender.none { it.c in setOf("EZB", "ECB", "WBG", "G163") })
        val r = GoldReserven.rang(d)!!
        assertEquals("USA", r.eintraege.first().land.c)
        assertTrue(r.eintraege.first().tonnen in 7000.0..9000.0)
        assertTrue(r.eintraege.all { it.tonnen <= r.eintraege.first().tonnen })
        assertTrue(GoldReserven.kaeufer(GoldReserven.aenderungen(d), 5).isNotEmpty())
        assertNotNull(GoldReserven.haeufigsterMonat(d))
    }

    @Test fun registrierung() {
        val s = datei("welt/gold.json").readText()
        assertEquals(1, Datenbuendel.SCHEMA["welt/gold.json"])
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen("welt/gold.json", s))
        assertFalse(de.troetenrepublik.app.market.Daten.probeLesen("welt/gold.json", "{\"stand\":\"x\"}"))
        assertEquals(Alter.MONATLICH, Alter.profilFuer("welt/gold.json"))
        val liste = repoDatei("tools/daten/dateien.json").readText()
        assertTrue(liste.contains("\"welt/gold.json\""))
        assertTrue(repoDatei("tools/daten/goldreserven.py").exists())
    }
}
