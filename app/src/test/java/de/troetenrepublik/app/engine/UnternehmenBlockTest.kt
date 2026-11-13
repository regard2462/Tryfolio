package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.UnternehmenTexte
import de.troetenrepublik.app.market.SecFormen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Block „Unternehmen“ (Texte), Gattungsname ohne Nennwertzusatz, Stimmen im Organigramm und Grossaktionaere ohne 0-%-Meldung.
// Auszuege sind kurz und selbst formuliert.
class UnternehmenBlockTest {

    @Test
    fun texteInBeidenSprachenGleich() {
        assertEquals(UnternehmenTexte.schluessel(true), UnternehmenTexte.schluessel(false))
        for (k in UnternehmenTexte.schluessel(true)) {
            assertTrue(k, UnternehmenTexte.text(true, k).isNotBlank())
            assertTrue(k, UnternehmenTexte.text(false, k).isNotBlank())
            assertFalse(k, UnternehmenTexte.text(true, k).let { it.contains('—') || it.contains(';') })
        }
        // Jedes Thema des Blocks hat einen Namen
        for (t in listOf("STANDORTE", "GESCHICHTE", "AUFTRAG", "PRUEFER", "RECHT", "ENTLASSUNGEN", "PATENTE", "EMISSIONEN")) {
            assertTrue(t, "t.$t" in UnternehmenTexte.schluessel(true))
        }
        assertEquals("Unternehmen", UnternehmenTexte.text(true, "titel"))
        assertEquals("Company", UnternehmenTexte.text(false, "titel"))
    }

    @Test
    fun zusammenfassungszeile() {
        assertEquals("1 Thema: Standorte", UnternehmenTexte.unter(true, listOf("Standorte")))
        assertEquals("5 Themen: Standorte, Geschichte, Prüfer, Rechtsstreit, Emissionen", UnternehmenTexte.unter(true, listOf("Standorte", "Geschichte", "Prüfer", "Rechtsstreit", "Emissionen")))
        assertEquals("2 topics: Locations, History", UnternehmenTexte.unter(false, listOf("Locations", "History")))
        assertEquals("1 topic: Patents", UnternehmenTexte.unter(false, listOf("Patents")))
    }

    @Test
    fun gattungsnameOhneNennwert() {
        assertEquals("Common Stock", StrukturRegeln.anzeigeTitel("Common Stock, \$0.00001 par value per share"))
        assertEquals("Common Stock", StrukturRegeln.anzeigeTitel("Common Stock, \$0.01 par value"))
        assertEquals("Class A Common Stock", StrukturRegeln.anzeigeTitel("Class A Common Stock, par value \$0.001 per share"))
        assertEquals("Common Stock", StrukturRegeln.anzeigeTitel("Common Stock, no par value"))
        assertEquals("Common Shares", StrukturRegeln.anzeigeTitel("Common Shares, without par value"))
        assertEquals("Ordinary Shares", StrukturRegeln.anzeigeTitel("Ordinary Shares, nominal value €0.01 each"))
        assertEquals("Common Stock", StrukturRegeln.anzeigeTitel("Common Stock (\$1.00 par value)"))
        // Ohne Zusatz bleibt der Name, wie er ist
        assertEquals("Class B Common Stock", StrukturRegeln.anzeigeTitel("Class B Common Stock"))
        assertEquals("American Depositary Shares", StrukturRegeln.anzeigeTitel("American Depositary Shares"))
    }

    @Test
    fun deckblattGattungMitKurznamen() {
        val html = "<xbrli:context id=\"c-1\"><xbrli:entity></xbrli:entity></xbrli:context>" +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:Security12bTitle\">Common Stock, \$0.00001 par value per share</ix:nonNumeric>" +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:TradingSymbol\">XYZ</ix:nonNumeric>" +
            "<ix:nonFraction contextRef=\"c-1\" name=\"dei:EntityCommonStockSharesOutstanding\" scale=\"0\">100,000,000</ix:nonFraction>"
        val d = StrukturRegeln.deckblatt(html)
        // Das Deckblatt behaelt den Titel, die Gattung zeigt ihn gekuerzt
        assertEquals("Common Stock, \$0.00001 par value per share", d.titel[0].titel)
        val g = StrukturRegeln.gattungen(d, setOf(Struktur.Flag.EINE_STIMME))
        assertEquals("Common Stock", g[0].titel)
        assertEquals(1.0, g[0].stimmen!!, 1e-9)
    }

    @Test
    fun stimmenSpalteNurBeiAbweichung() {
        // Eine Gattung mit einer Stimme je Aktie: Stimmenanteil gleich Kapitalanteil, die Spalte sagt nichts zusaetzlich
        val g = Struktur.Gattung(id = "S0", titel = "Common Stock", aktien = 1e9, stimmen = 1.0)
        val v = StrukturRegeln.verteilung(listOf(g))
        val h = Struktur.Halter("Vanguard Group", Struktur.Art.HOLDING, kapital = 9.4)
        val (k, s) = StrukturRegeln.halterAnteile(h, v)
        assertEquals(9.4, k!!, 1e-9)
        assertFalse(StrukturRegeln.weichtAb(k, s))
        // Mehrere Klassen mit verschiedener Stimmenzahl: die Anteile weichen ab
        assertTrue(StrukturRegeln.weichtAb(10.0, 40.0))
    }

    @Test
    fun grossaktionaerOhneNullProzentMeldung() {
        fun h(name: String, anteil: Double, gemeldet: String) = SecFormen.Halter("a-$gemeldet", name, anteil, 0.0, false, true, "", gemeldet)
        val s = SecFormen.Stand(
            halter = listOf(
                h("The Vanguard Group", 8.1, "2025-02-10"),
                h("The Vanguard Group", 0.0, "2026-03-13"),
                h("BlackRock, Inc.", 6.4, "2026-01-30"),
            ),
        )
        val gross = SecFormen.grossaktionaere(s)
        assertEquals(listOf("BlackRock, Inc."), gross.map { it.name })
        // Ein Melder unter 5 % bleibt mit seinem letzten Stand
        val s2 = SecFormen.Stand(halter = listOf(h("Fonds X", 4.2, "2026-02-01")))
        assertEquals(1, SecFormen.grossaktionaere(s2).size)
    }
}
