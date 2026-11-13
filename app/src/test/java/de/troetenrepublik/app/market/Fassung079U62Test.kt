package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Levermann
import de.troetenrepublik.app.engine.LynchTyp
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.ui.levSkala
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Uebersetzung ganzer Texte mit DeepL (Richtung, Merken, ohne Schluessel nichts), Lynch-Typen mit den etablierten
// englischen Namen, Skalen der Levermann-Kriterien.
class Fassung079U62Test {
    @Test fun uebersetzerMerktJedenTextUndKenntDieRichtung() = runBlocking {
        val k = Abrufkontingent(null)
        val formulare = ArrayList<List<Pair<String, String>>>()
        val u = Uebersetzer({ _, _, formular ->
            formulare += formular
            MarketApi.Antwort(200, """{"translations":[{"text":"Übersetzt"}]}""")
        }, k, null)
        assertNull(u.uebersetzen("Hello", "DE", ""))
        assertTrue(formulare.isEmpty())
        assertEquals("Übersetzt", u.uebersetzen("Hello world", "DE", "abc:fx"))
        assertEquals("EN", formulare.single().first { it.first == "source_lang" }.second)
        assertEquals("DE", formulare.single().first { it.first == "target_lang" }.second)
        assertEquals("Hello world".length.toLong(), k.stand(Abrufkontingent.DEEPL).first)
        // Derselbe Text kostet nichts und fragt nicht erneut
        assertEquals("Übersetzt", u.uebersetzen("Hello world", "DE", "abc:fx"))
        assertEquals("Übersetzt", u.gemerkt("Hello world", "DE"))
        assertEquals(1, formulare.size)
        // Andere Richtung: deutscher Text ins Englische
        assertEquals("Übersetzt", u.uebersetzen("Hallo Welt", "EN-US", "abc:fx"))
        assertEquals("DE", formulare.last().first { it.first == "source_lang" }.second)
        assertEquals("EN-US", formulare.last().first { it.first == "target_lang" }.second)
        assertNull(u.gemerkt("Hello world", "EN-US"))
    }

    @Test fun uebersetzerMeldetFehlerOhneAbsturz() = runBlocking {
        val k = Abrufkontingent(null)
        assertNull(Uebersetzer({ _, _, _ -> MarketApi.Antwort(456, "{}") }, k, null).uebersetzen("Text", "DE", "abc:fx"))
        assertNull(Uebersetzer({ _, _, _ -> MarketApi.Antwort(403, "{}") }, Abrufkontingent(null), null).uebersetzen("Text", "DE", "abc:fx"))
        assertNull(Uebersetzer({ _, _, _ -> MarketApi.Antwort(200, "kaputt") }, Abrufkontingent(null), null).uebersetzen("Text", "DE", "abc:fx"))
    }

    @Test fun lynchTypenMitEtabliertenNamen() {
        val erwartet = mapOf(
            "LANGSAM" to "Slow Grower", "STETIG" to "Stalwart", "SCHNELL" to "Fast Grower",
            "ZYKLIKER" to "Cyclical", "TURNAROUND" to "Turnaround", "SUBSTANZ" to "Asset Play",
        )
        LynchTyp.Typ.entries.forEach {
            // Im Deutschen heisst der zyklische Typ Zykliker (gaengiges Wort), sonst bleiben die englischen Namen
            assertEquals(if (it.name == "ZYKLIKER") "Zykliker" else erwartet.getValue(it.name), De.lyTyp(it.name))
            assertEquals(erwartet.getValue(it.name), En.lyTyp(it.name))
        }
    }

    @Test fun skalenZeigenDieSchwellenDerRechnung() {
        val rendite = levSkala(Levermann.Kriterium.EK_RENDITE, false)!!
        assertEquals(Levermann.EK_RENDITE_SCHLECHT, rendite.schlecht, 0.0)
        assertEquals(Levermann.EK_RENDITE_GUT, rendite.gut, 0.0)
        assertTrue(rendite.gutOben)
        // KGV: niedrig ist gut
        assertFalse(levSkala(Levermann.Kriterium.KGV, false)!!.gutOben)
        // Analystenmeinung: Gegenzeichen, bei wenigen Analysten umgekehrt
        assertTrue(levSkala(Levermann.Kriterium.ANALYSTEN, false)!!.gutOben)
        assertFalse(levSkala(Levermann.Kriterium.ANALYSTEN, true)!!.gutOben)
        // Finanzwerte haben niedrigere Grenzen der Eigenkapitalquote
        assertEquals(Levermann.EK_QUOTE_FINANZ_GUT, levSkala(Levermann.Kriterium.EK_QUOTE, true)!!.gut, 0.0)
        // Momentum und Umkehr haben keine Skala, aber eine Kurzbeschreibung
        listOf(Levermann.Kriterium.MOMENTUM, Levermann.Kriterium.UMKEHR).forEach {
            assertNull(levSkala(it, false))
            assertTrue(De.levKurz(it.name, false).isNotBlank())
            assertTrue(En.levKurz(it.name, false).isNotBlank())
        }
        Levermann.Kriterium.entries.filter { it != Levermann.Kriterium.MOMENTUM && it != Levermann.Kriterium.UMKEHR }.forEach {
            assertNotNull(it.name, levSkala(it, false))
            assertEquals("", De.levKurz(it.name, false))
        }
        // Die alte Textzeile steht nicht mehr
        assertFalse(De.levKurz("MOMENTUM", false).contains("Halbjahr gut und Jahr nicht"))
    }
}
