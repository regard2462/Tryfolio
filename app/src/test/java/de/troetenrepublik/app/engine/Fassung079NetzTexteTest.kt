package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.NetzTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Texte der Verflechtungen: beide Sprachen haben dieselben Schluessel und Platzhalter, keine verbotenen Zeichen, alle Arten,
// Gruppen, Status und Kernaussagen sind abgedeckt.
class Fassung079NetzTexteTest {

    private val platzhalter = Regex("\\{(\\d)\\}")

    @Test fun beideSprachenHabenDieselbenSchluessel() {
        val de = NetzTexte.schluessel(true)
        val en = NetzTexte.schluessel(false)
        assertEquals("nur deutsch: ${de - en}, nur englisch: ${en - de}", de, en)
        assertTrue(de.size > 100)
    }

    @Test fun platzhalterStimmenUeberein() {
        for (k in NetzTexte.schluessel(true)) {
            val d = platzhalter.findAll(textMitPlatzhaltern(true, k)).map { it.groupValues[1] }.toSet()
            val e = platzhalter.findAll(textMitPlatzhaltern(false, k)).map { it.groupValues[1] }.toSet()
            assertEquals(k, d, e)
        }
    }

    // Text mit den Platzhaltern: ohne Werte eingesetzt, bleiben die {n} stehen
    private fun textMitPlatzhaltern(deutsch: Boolean, key: String): String = NetzTexte.text(deutsch, key)

    @Test fun keineVerbotenenZeichenUndKeineLeerenTexte() {
        for (deutsch in listOf(true, false)) for (k in NetzTexte.schluessel(deutsch)) {
            val t = NetzTexte.text(deutsch, k)
            if (k != "unter.leer") assertTrue("$k leer", t.isNotBlank())
            assertFalse("$k Gedankenstrich", t.contains('—') || t.contains('–'))
            assertFalse("$k Semikolon", t.contains(';'))
            assertFalse("$k Fragezeichen", t.contains('?'))
            assertFalse("$k Dreieckszeichen", t.any { it in "▲▼▶△▽" })
        }
    }

    @Test fun artenGruppenStatusUndBezugSindBeschriftet() {
        for (a in Netz.Art.entries) for (d in listOf(true, false)) {
            assertTrue(a.schluessel, NetzTexte.text(d, "art." + a.schluessel).isNotBlank())
            if (a.gerichtet && a != Netz.Art.LIEFERT && a != Netz.Art.KAUFT && a != Netz.Art.RECHENZENTRUM) {
                assertTrue(a.schluessel, NetzTexte.text(d, "r." + a.schluessel + ".aus").isNotBlank())
                assertTrue(a.schluessel, NetzTexte.text(d, "r." + a.schluessel + ".ein").isNotBlank())
            }
        }
        for (g in Netz.Gruppe.entries) for (d in listOf(true, false)) assertTrue(g.name, NetzTexte.text(d, "gruppe." + g.name).isNotBlank())
        for (s in Netz.Status.entries) for (d in listOf(true, false)) assertTrue(s.name, NetzTexte.text(d, "status." + s.schluessel).isNotBlank())
        for (b in Netz.Bezug.entries) for (d in listOf(true, false)) assertTrue(b.name, NetzTexte.text(d, "bezug." + b.name, listOf("5 %")).contains("5 %"))
        for (gu in Netz.Guete.entries) for (d in listOf(true, false)) assertTrue(gu.name, NetzTexte.text(d, "guete." + gu.name).isNotBlank())
        for (d in listOf(true, false)) assertTrue(NetzTexte.text(d, "status.abgelaufen").isNotBlank())
    }

    @Test fun jedeKernaussageHatEinenTextInBeidenSprachen() {
        val keys = listOf(
            "a.kreis", "a.konz", "a.konz1", "a.lief", "a.gegen.handel", "a.gegen.anteil", "a.bedingt", "a.bedingt.opt", "a.bedingt.nur.opt",
            "a.klumpen", "a.kontrolle.mehrheit", "a.kreis.bedingt", "a.kreis.bedingtanteil", "a.wett", "a.mandat", "a.staat", "a.gemischt",
        )
        for (k in keys) for (d in listOf(true, false)) assertTrue(k, NetzTexte.text(d, k).isNotBlank())
        // Jede Regel nennt eine Zahl oder einen Namen ueber Platzhalter
        for (k in keys.filter { it != "a.gemischt" }) assertTrue(k, platzhalter.containsMatchIn(NetzTexte.text(true, k)))
    }

    @Test fun einzahlUndMehrzahl() {
        assertEquals("1 Zulieferer", NetzTexte.zahl(true, "zulieferer", 1))
        assertEquals("5 Zulieferer", NetzTexte.zahl(true, "zulieferer", 5))
        assertEquals("1 supplier", NetzTexte.zahl(false, "zulieferer", 1))
        assertEquals("5 suppliers", NetzTexte.zahl(false, "zulieferer", 5))
        assertEquals("1 Kreislauf", NetzTexte.zahl(true, "kreis", 1))
        assertEquals("2 Kreisläufe", NetzTexte.zahl(true, "kreis", 2))
        assertEquals("2 cycles", NetzTexte.zahl(false, "kreis", 2))
        assertEquals("3 Abnehmer", NetzTexte.zahl(true, "abnehmer", 3))
        assertEquals("1 customer", NetzTexte.zahl(false, "abnehmer", 1))
    }

    @Test fun titelUndEinstieg() {
        assertEquals("Verflechtungen", NetzTexte.text(true, "titel"))
        assertEquals("Connections", NetzTexte.text(false, "titel"))
        assertEquals("Was das Netz zeigt", NetzTexte.text(true, "aussagen.titel"))
        assertEquals("What the network shows", NetzTexte.text(false, "aussagen.titel"))
        assertEquals("KI-Kreisläufe", NetzTexte.text(true, "start.ki"))
    }
}
