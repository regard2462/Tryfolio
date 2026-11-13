package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.TaktikTexte
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class Fassung078U46Test {

    @Test fun spaltenBreiteHatMindestmassUndPolster() {
        assertEquals(Vergleich.MIN_SPALTE_DP, Vergleich.spaltenBreite(40f))
        assertEquals(100f + Vergleich.POLSTER_DP, Vergleich.spaltenBreite(100f))
    }

    @Test fun besterNimmtGroesstenVorhandenenWert() {
        assertEquals(2, Vergleich.bester(listOf(0.1, null, 0.4, 0.3)))
        assertEquals(0, Vergleich.bester(listOf(-0.5, -0.9)))
        assertNull(Vergleich.bester(listOf(null, null)))
        assertNull(Vergleich.bester(emptyList()))
    }

    @Test fun auffuellenFuelltFehlendeSpaltenMitStrich() {
        assertEquals(listOf("a", "b"), Vergleich.auffuellen(listOf("a", "b"), 2))
        assertEquals(listOf("a", "–"), Vergleich.auffuellen(listOf("a"), 2))
        assertEquals(listOf("a"), Vergleich.auffuellen(listOf("a", "b"), 1))
    }

    @Test fun neueTexteSindDeutschUndEnglischVorhandenOhneGedankenstrich() {
        val schluessel = listOf(
            "vt.info.titel", "vt.lern", "vt.lern.hint", "vt.pruef", "vt.pruef.hint", "vt.aussage.gesamt", "vt.fakten", "vt.treffer",
            "vt.kz.kurz", "vt.rang.kurz", "vt.andere.kurz", "vt.beste", "vt.gesamt", "vt.pruefzeit",
            "pa.anderewerte", "pa.lotterie", "pa.zufall", "pa.startfenster", "pa.parameter", "pa.wf",
        )
        for (deutsch in listOf(true, false)) for (k in schluessel) {
            val t = TaktikTexte.text(deutsch, k, listOf("A", "B"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('—') || t.contains('–') || t.contains('?') || t.contains("{0}"))
        }
    }

    @Test fun bezeichnungenSindKurzUndDatumStehtInDerUnterzeile() {
        assertEquals("Rendite p. a. Lernzeit", TaktikTexte.text(true, "vt.lern", emptyList()))
        assertEquals("bis 29.05.2026", TaktikTexte.text(true, "vt.lern.hint", listOf("29.05.2026")))
        assertEquals("ab 29.05.2026", TaktikTexte.text(true, "vt.pruef.hint", listOf("29.05.2026")))
        assertEquals("10 Geschäfte in 1,5 Jahren", TaktikTexte.text(true, "vt.fakten", listOf("10", "1,5")))
        assertEquals("Trefferquote 60 %, Kaufen und Halten 55 %", TaktikTexte.text(true, "vt.kz.kurz", listOf("60 %", "55 %")))
        assertEquals("Vorn: Apple +43 %", TaktikTexte.text(true, "vt.rang.kurz", listOf("Apple", "+43 %")))
        assertTrue(TaktikTexte.text(true, "vt.lern", emptyList()).length <= 24)
        assertTrue(TaktikTexte.text(true, "vt.pruef", emptyList()).length <= 24)
    }

    @Test fun startZeileGehoertZumHinweisblock() {
        assertTrue(TaktikTexte.text(true, "rot.start", listOf("21.10.2024", "Nebius")).startsWith("• "))
        assertTrue(TaktikTexte.text(false, "rot.start", listOf("21.10.2024", "Nebius")).startsWith("• "))
        assertTrue(TaktikTexte.text(true, "rot.methode", listOf("0,10 %")).startsWith("Historisch und hypothetisch, nicht tatsächlich erzielt."))
    }
}
