package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Rohstoffprofil.Quelle
import de.troetenrepublik.app.engine.Rohstoffprofil.Weg
import de.troetenrepublik.app.market.SecBericht
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate

// [0.76 q76k] Rueckfall Branche, Uran, Datenmenge SEC
class Fassung076Q76kTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun ergebnis(w: Wertbild) = Rohstoffprofil.fuer(index, w)
    private fun ids(w: Wertbild) = ergebnis(w).ids

    // ---------------------------------------------------------------- 1. Rueckfall Branche
    @Test(timeout = 20_000)
    fun japanischeAutosHabenFirmenzuordnungStattBranche() {
        for (s in listOf("7203.T", "7267.T", "7201.T", "005380.KS", "000270.KS", "TMPV.NS", "MARUTI.NS", "MBG.DE", "F", "GM")) {
            val e = ergebnis(Wertbild(s, "FAHRZEUGE", "XX"))
            assertTrue(s, e.treffer.isNotEmpty())
            assertTrue(s, e.treffer.all { it.quelle == Quelle.FIRMA })
            assertTrue(s, e.ids.containsAll(listOf("LITHIUM", "KOBALT", "SELTENE_ERDEN", "PLATIN")))
        }
    }

    @Test(timeout = 20_000)
    fun beschreibungVonWikipediaGenuegtOhneFirmenzuordnung() {
        // Unbekannter Wert, nur die Wikipedia-Beschreibung (englisch)
        val en = Wertbild("ZZZ", null, null, beschreibung = "Zeta Motor Corporation is a Japanese multinational automotive manufacturer headquartered in Nagoya.")
        assertTrue(ids(en).containsAll(listOf("LITHIUM", "PLATIN", "SELTENE_ERDEN")))
        assertTrue(ergebnis(en).treffer.all { it.quelle == Quelle.BESCHREIBUNG })
        // Dieselbe Beschreibung auf Deutsch
        val de = Wertbild("ZZZ", null, null, beschreibung = "Zeta Motor ist ein japanischer Automobilhersteller mit Sitz in Nagoya.")
        assertTrue(ids(de).containsAll(listOf("LITHIUM", "PLATIN", "SELTENE_ERDEN")))
        val akku = Wertbild("ZZB", null, null, beschreibung = "Der Konzern fertigt Batteriezellen und Lithium-Ionen-Module.")
        assertTrue("LITHIUM" in ids(akku))
    }

    @Test(timeout = 20_000)
    fun brancheRueckfallNenntJeZeileEineVerwendung() {
        val brancheMitListe = index.branchenRohstoffe.keys + listOf("HALBLEITER", "TELEKOM", "VERSORGER")
        for (b in brancheMitListe) {
            val e = ergebnis(Wertbild("UNBEKANNT", b, "US"))
            assertTrue(b, e.treffer.all { it.quelle == Quelle.BRANCHE })
            for (t in e.treffer) {
                assertNotNull("$b ${t.id}", t.verwendung)
            }
        }
        // FAHRZEUGE: Batteriezellen ueblich, nicht eine Liste ohne Aussage
        val fz = ergebnis(Wertbild("UNBEKANNT", "FAHRZEUGE", "US")).treffer.associateBy { it.id }
        assertEquals("AKKU", fz.getValue("LITHIUM").verwendung)
        assertEquals("KATALYSATOR", fz.getValue("PLATIN").verwendung)
    }

    @Test(timeout = 30_000)
    fun katalogwerteRohstoffrelevanterBranchenBleibenNichtBeiDerBranche() {
        val katalog = kotlinx.serialization.json.Json.parseToJsonElement(datei("katalog.json")).jsonObject.getValue("aktien").jsonArray
        val relevant = setOf("HALBLEITER", "COMPUTER", "ELEKTRO", "FAHRZEUGE", "LUFTFAHRT", "MASCHINEN", "BAU", "VERSORGER", "UMWELT", "CHEMIE", "GESUNDHEIT", "TELEKOM")
        var gesamt = 0
        val nurBranche = ArrayList<String>()
        for (el in katalog) {
            val a = el.jsonObject
            fun f(k: String) = a[k]?.jsonPrimitive?.content.orEmpty()
            val b = f("i")
            if (b !in relevant) continue
            gesamt++
            val e = ergebnis(Wertbild(f("s"), b, f("c")))
            if (e.treffer.any { it.quelle == Quelle.BRANCHE }) nurBranche += f("s")
            assertTrue(f("s"), e.treffer.all { it.verwendung != null || it.quelle != Quelle.BRANCHE })
        }
        assertTrue("Katalogwerte in den Branchen: $gesamt", gesamt > 500)
        // Nach der Ausweitung bleibt hoechstens ein kleiner Rest bei der Branche
        assertTrue("nur Branche: $nurBranche", nurBranche.size <= 5)
    }

    // ---------------------------------------------------------------- 2. Uran
    @Test(timeout = 20_000)
    fun uranIstEinRohstoffMitFoerderlaendernUndQuelle() {
        val u = index.rohstoffe.getValue("URAN")
        assertEquals("Uran", u.de)
        assertEquals("Uranium", u.en)
        assertEquals("KZ", u.laender.first().land)
        assertEquals(39, u.laender.first().prozent)
        assertEquals(24 + 39 + 12, u.konzentration)
        assertEquals(2024, u.jahr)
        assertTrue(u.quelle.orEmpty().contains("World Nuclear Association"))
        assertTrue(u.quelleUrl.orEmpty().startsWith("https://world-nuclear.org/"))
        assertEquals("UX=F", u.kurs)
        // Die Anteile der aufgefuehrten Laender uebersteigen 100 Prozent nicht
        assertTrue(u.laender.sumOf { it.prozent } <= 100)
        // Kein anderer Rohstoff verliert seine USGS-Quelle
        assertNull(index.rohstoffe.getValue("KUPFER").quelle)
    }

    @Test(timeout = 20_000)
    fun foerdererBrennstoffUndKernkraft() {
        fun t(s: String, id: String = "URAN") = ergebnis(Wertbild(s, null, null)).treffer.first { it.id == id }
        for (s in listOf("CCJ", "KAP.L", "NXE", "DNN", "UUUU", "PDN.AX", "UEC", "BHP")) {
            assertEquals(s, "FOERDERUNG", t(s).verwendung)
            assertEquals(s, Weg.EIGEN, t(s).weg)
            assertEquals(s, Quelle.FIRMA, t(s).quelle)
        }
        assertEquals("ANREICHERUNG", t("LEU").verwendung)
        for (s in listOf("OKLO", "SMR", "BWXT")) assertEquals(s, Weg.ZULIEFERER, t(s).weg)
        for (s in listOf("CEG", "VST", "FORTUM.HE", "CEZ.PR", "015760.KS", "9501.T")) {
            assertEquals(s, "BRENNSTOFF", t(s).verwendung)
            assertTrue(s, ergebnis(Wertbild(s, "VERSORGER", null)).ids.containsAll(listOf("URAN", "KUPFER")))
        }
        // Cameco und Oklo haben nur Uran, nichts aus der Branche
        assertEquals(listOf("URAN"), ids(Wertbild("CCJ", "BERGBAU", "CA")))
        assertEquals(listOf("URAN"), ids(Wertbild("OKLO", "VERSORGER", "US")))
        // BHP: Eisenerz, Kupfer, Nickel und Uran als Nebenprodukt
        assertTrue(ids(Wertbild("BHP", "BERGBAU", "AU")).containsAll(listOf("EISENERZ", "KUPFER", "NICKEL", "URAN")))
        // Versorger ohne Kernkraft zeigen kein Uran
        assertFalse("URAN" in ids(Wertbild("NG.L", "VERSORGER", "GB")))
    }

    @Test(timeout = 20_000)
    fun uranAusBeschreibungUndIndustrie() {
        val miner = Wertbild("NEU", null, null, industrie = "Uranium", beschreibung = "Explores uranium deposits in the Athabasca Basin.")
        assertEquals("FOERDERUNG", ergebnis(miner).treffer.first { it.id == "URAN" }.verwendung)
        val reaktor = Wertbild("NEU2", null, null, beschreibung = "Develops small modular reactors for nuclear power.")
        assertEquals("BRENNSTOFF", ergebnis(reaktor).treffer.first { it.id == "URAN" }.verwendung)
        val anreicher = Wertbild("NEU3", null, null, beschreibung = "Supplies enriched uranium and nuclear fuel fabrication services.")
        assertTrue("URAN" in ids(anreicher))
        val deutsch = Wertbild("NEU4", null, null, beschreibung = "Betreibt Kernkraftwerke und handelt Strom.")
        assertEquals("BRENNSTOFF", ergebnis(deutsch).treffer.first { it.id == "URAN" }.verwendung)
        assertTrue("URAN" in Rohstoffprofil.ausText("We purchase uranium and conversion services from suppliers."))
    }

    @Test(timeout = 20_000)
    fun embargoKenntUran() {
        val cameco = Bestand(Wertbild("CCJ", "BERGBAU", "CA"), "Cameco", 0.1)
        val atom = Bestand(Wertbild("CEG", "VERSORGER", "US"), "Constellation", 0.2)
        val ohne = Bestand(Wertbild("NG.L", "VERSORGER", "GB"), "National Grid", 0.3)
        val z = Szenarien.ereignis(Szenarien.Ereignis.EMBARGO, index, listOf(cameco, atom, ohne), heute)
        val treffer = z.betroffen.associateBy { it.symbol }
        assertTrue(treffer.getValue("CEG").rohstoffe.any { it.id == "URAN" })
        // [0.77] Der Foerderer braucht kein Uran aus anderen Laendern: Er ist kein Abnehmer
        assertFalse("CCJ" in treffer)
        // Die Quelle nennt auch die World Nuclear Association
        assertTrue(treffer.getValue("CEG").beleg.contains("World Nuclear Association"))
        assertTrue(treffer.getValue("CEG").beleg.contains("Geological Survey"))
        assertFalse("NG.L" in treffer && treffer.getValue("NG.L").rohstoffe.any { it.id == "URAN" })
    }

    // ---------------------------------------------------------------- 3. Datenmenge SEC
    private class Zaehler(private val daten: ByteArray) : InputStream() {
        var gelesen = 0
        private var pos = 0
        override fun read(): Int = if (pos < daten.size) { gelesen++; daten[pos++].toInt() and 0xff } else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= daten.size) return -1
            val n = minOf(len, daten.size - pos)
            System.arraycopy(daten, pos, b, off, n)
            pos += n; gelesen += n
            return n
        }
    }

    private fun bericht(vorRisiko: String, nachRisiko: String, fuell: Int = 0): String =
        "<html><body><p>Table of Contents</p><p>Item 1. Business 3</p><p>Item 1A. Risk Factors 12</p><p>Item 1B. Unresolved Staff Comments 20</p>" +
            "<p>Item 1. Business</p><p>" + "x ".repeat(fuell) + "</p>" + vorRisiko + "<p>Item 1A. Risk Factors</p>" + nachRisiko + "</body></html>"

    @Test(timeout = 20_000)
    fun risikoBeginnUeberspringtInhaltsverzeichnisUndVerweise() {
        val k = Rohstoffprofil.klartext(bericht("<p>Raw materials: we buy lithium. See Item 1A. Risk Factors for more.</p>", "<p>Raw materials: cobalt.</p>"))
        val p = Rohstoffprofil.risikoBeginn(k)
        assertTrue(p > 0)
        // Der Beginn liegt hinter dem Verweis im Fliesstext und nicht beim Inhaltsverzeichnis
        assertTrue(k.substring(0, p).contains("see item 1a"))
        assertTrue(k.substring(p).contains("cobalt"))
        assertEquals(-1, Rohstoffprofil.risikoBeginn("item 1. business item 1a. risk factors 12 item 1b. unresolved staff comments"))
    }

    @Test(timeout = 20_000)
    fun nurDerRohstoffabschnittVorRisikofaktorenZaehlt() {
        val html = bericht("<p>Raw materials. We use lithium and cobalt in batteries.</p>", "<p>Raw materials. Prices of nickel and tungsten vary.</p>")
        val ids = Rohstoffprofil.ausJahresbericht(html)
        assertTrue(ids.containsAll(listOf("LITHIUM", "KOBALT")))
        assertFalse("NICKEL" in ids)
        assertFalse("WOLFRAM" in ids)
        // Ohne Risk-Factors-Abschnitt gilt der ganze Text wie bisher
        assertTrue("NICKEL" in Rohstoffprofil.ausJahresbericht("<p>Raw materials: nickel.</p>"))
    }

    @Test(timeout = 20_000)
    fun lesenBrichtBeiRisikofaktorenAb() {
        val html = bericht("<p>Raw materials: lithium.</p>", "<p>" + "y ".repeat(1_500_000) + "</p>", fuell = 300_000)
        val z = Zaehler(html.toByteArray())
        val text = SecBericht.lesenAbschnitt(z)
        assertTrue(text.contains("lithium"))
        assertTrue("gelesen ${z.gelesen} von ${html.length}", z.gelesen < 1_000_000)
        assertTrue(Rohstoffprofil.ausJahresbericht(text).contains("LITHIUM"))
    }

    @Test(timeout = 20_000)
    fun lesenHatEineHarteObergrenze() {
        val html = "<html><body><p>Raw materials: lithium.</p>" + "z ".repeat(5_000_000) + "</body></html>"
        val z = Zaehler(html.toByteArray())
        val text = SecBericht.lesenAbschnitt(z)
        assertEquals(SecBericht.MAX_TEXT_BYTES.toInt(), text.length)
        assertEquals(SecBericht.MAX_TEXT_BYTES.toInt(), z.gelesen)
        assertTrue(SecBericht.MAX_TEXT_BYTES <= 1_500_000L)
        // Kurzer Bericht wird ganz gelesen
        assertEquals("<p>kurz</p>", SecBericht.lesenAbschnitt(ByteArrayInputStream("<p>kurz</p>".toByteArray())))
    }
}
