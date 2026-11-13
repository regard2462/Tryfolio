package de.troetenrepublik.app

import de.troetenrepublik.app.data.WidgetSpalten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Widget-Zeilen: Trenner nur zwischen Teilen mit Inhalt, grosser Betrag mit Waehrungszeichen ohne Umbruch, Namen ohne
// Abschneiden, Kursspalte faellt vor den Namen weg.
class Fassung078U59Test {

    private val res = listOf("src/main/res", "../app/src/main/res").map { File(it) }.first { it.exists() }
    private val nbsp = 160.toChar().toString()

    private fun layout(name: String) = File(res, "layout/$name.xml").readText()

    // Alle TextView-Bloecke einer Layoutdatei
    private fun textViews(xml: String) = Regex("<TextView.*?/>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.value }.toList()

    @Test
    fun trennerNurZwischenTeilenMitInhalt() {
        assertEquals("−127,00 € · −0,76 %", WidgetSpalten.mitTrenner("−127,00 €", "−0,76 %"))
        assertEquals("−127,00 €", WidgetSpalten.mitTrenner("−127,00 €", ""))
        assertEquals("−0,76 %", WidgetSpalten.mitTrenner("", "−0,76 %"))
        assertEquals("a · c", WidgetSpalten.mitTrenner("a", "  ", "c"))
        assertEquals("", WidgetSpalten.mitTrenner("", " "))
    }

    @Test
    fun aenderungOhneLosenTrenner() {
        for (breite in listOf(0, 90, 104, 160, 300)) {
            val a = WidgetSpalten.aenderung("−127,00 €", "−0,76 %", breite)
            for (z in listOf(a.erste, a.zweite)) {
                assertFalse("loser Trenner bei $breite: $z", z.trim().endsWith("·") || z.trim().startsWith("·"))
            }
            // Beide Angaben sind immer da: in einer Zeile oder untereinander
            val alles = a.erste + a.zweite
            assertTrue(alles.contains("127,00")); assertTrue(alles.contains("0,76"))
        }
        // Fehlt ein Teil, steht der andere allein
        assertEquals("−0,76 %".replace(" ", nbsp), WidgetSpalten.aenderung("", "−0,76 %", 104).erste)
        assertEquals("", WidgetSpalten.aenderung("", "−0,76 %", 104).zweite)
        assertEquals("−127,00 €".replace(" ", nbsp), WidgetSpalten.aenderung("−127,00 €", " ", 104).erste)
    }

    @Test
    fun aenderungSchmalUntereinanderBreitInEinerZeile() {
        val schmal = WidgetSpalten.aenderung("−127,00 €", "−0,76 %", 104)
        assertTrue(schmal.zweite.isNotEmpty())
        assertFalse(schmal.erste.contains("·"))
        val breit = WidgetSpalten.aenderung("−127,00 €", "−0,76 %", 300)
        assertEquals("", breit.zweite)
        assertTrue(breit.erste.contains(" · "))
    }

    @Test
    fun grosserBetragMitWaehrungszeichenOhneUmbruch() {
        val voll = "16.607,77 €"
        val kurz = "16.608 €"
        // Schmal: die Schreibweise ohne Nachkommastellen, nie verkleinert und nie ohne Zeichen
        val schmal = WidgetSpalten.betragWahl(listOf(voll, kurz), 104, 22f)
        assertEquals(kurz.replace(" ", nbsp), schmal)
        assertTrue(schmal.endsWith("€"))
        // Breit: mit Nachkommastellen
        assertEquals(voll.replace(" ", nbsp), WidgetSpalten.betragWahl(listOf(voll, kurz), 220, 22f))
        // Keine gewoehnliche Leerstelle, also keine Umbruchstelle
        assertFalse(schmal.contains(' '))
        // Unbekannte Breite: die ausfuehrliche Schreibweise
        assertEquals(voll.replace(" ", nbsp), WidgetSpalten.betragWahl(listOf(voll, kurz), 0, 22f))
        assertEquals("", WidgetSpalten.betragWahl(listOf("", " "), 104, 22f))
    }

    @Test
    fun kursspalteFaelltVorDenNamenWeg() {
        val namen = listOf("Nasdaq", "Bitcoin", "Dow Jones")
        val kurse = listOf("19.234,56 $", "88.100,00 €", "41.000,12 $")
        val proz = listOf("+1,23 %", "−0,45 %", "+0,05 %")
        // Schmal: Kurs weg, damit die Namen ganz stehen
        assertFalse(WidgetSpalten.kursSichtbar(namen, kurse, proz, 132, 1f))
        // Breit: Kurs bleibt
        assertTrue(WidgetSpalten.kursSichtbar(namen, kurse, proz, 300, 1f))
        // Unbekannte Breite: nichts streichen
        assertTrue(WidgetSpalten.kursSichtbar(namen, kurse, proz, 0, 1f))
        // Grosse Systemschrift braucht mehr Platz
        assertFalse(WidgetSpalten.kursSichtbar(namen, kurse, proz, 300, 2f))
        // Ohne Kurse gibt es nichts zu streichen
        assertTrue(WidgetSpalten.kursSichtbar(namen, listOf("", "", ""), proz, 132, 1f))
    }

    @Test
    fun keinLayoutKuerztNamenMitEllipse() {
        val dateien = File(res, "layout").listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!
        assertTrue(dateien.isNotEmpty())
        dateien.forEach { assertFalse("ellipsize in ${it.name}", it.readText().contains("ellipsize")) }
    }

    @Test
    fun namensspaltenDuerfenUmbrechen() {
        val namen = mapOf(
            "widget_listenzeile" to listOf("zeile_name"),
            "widget_werte" to (1..6).map { "name$it" },
            "widget_mover" to (1..6).map { "name$it" },
            "widget_dividenden" to listOf("div_erhalten_text"),
            "widget_stimmung_zeile" to listOf("alle_name"),
            "widget_einzel" to listOf("widget_titel"),
        )
        for ((datei, ids) in namen) {
            val bloecke = textViews(layout(datei))
            for (id in ids) {
                val b = bloecke.firstOrNull { it.contains("android:id=\"@+id/$id\"") } ?: continue
                assertFalse("$datei/$id einzeilig", b.contains("android:maxLines=\"1\""))
            }
        }
    }

    @Test
    fun listenzeileHatKursUndWertSpaltenOhneUmbruchZwang() {
        val xml = layout("widget_listenzeile")
        val bloecke = textViews(xml)
        // Der Name nimmt den Rest (Gewicht), Kurs und Wert nur ihre Breite
        val name = bloecke.first { it.contains("zeile_name") }
        assertTrue(name.contains("android:layout_weight=\"1\""))
        assertTrue(bloecke.first { it.contains("zeile_kurs") }.contains("wrap_content"))
        assertTrue(bloecke.first { it.contains("zeile_proz") }.contains("wrap_content"))
    }

    @Test
    fun depotLayoutHatZweiteAenderungszeile() {
        val xml = layout("widget_depot")
        assertTrue(xml.contains("@+id/widget_aenderung2"))
        val zweite = textViews(xml).first { it.contains("widget_aenderung2") }
        assertTrue(zweite.contains("android:visibility=\"gone\""))
        assertTrue(zweite.contains("android:maxLines=\"1\""))
    }
}
