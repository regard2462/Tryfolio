package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.widgetBezeichnung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Zahlenkuerzel (Korea, Japan, Hongkong, Taiwan, China) werden nie als Beschriftung gezeigt, dort steht der Name.
class Fassung077S2BezeichnungTest {
    @Before fun deutsch() { I18n.lang = Lang.DE }

    @Test fun zahlenkuerzelErkennen() {
        for (s in listOf("000660.KS", "005930.KS", "7203.T", "0700.HK", "2330.TW", "600519.SS", "300750.SZ", "9988", "285A.T")) {
            assertTrue(s, HeatmapBeschriftung.nurZiffern(s))
        }
        for (s in listOf("NVDA", "SAP.DE", "BRK-B", "BTC-EUR", "^GDAXI", "3IN.L", "HY9H.F", "D05.SI", "", "SKHY")) {
            assertFalse(s, HeatmapBeschriftung.nurZiffern(s))
        }
    }

    @Test fun nameStattZahl() {
        assertEquals("SK Hynix", HeatmapBeschriftung.bezeichnung("000660.KS", "SK Hynix"))
        assertEquals("Samsung Electronics", HeatmapBeschriftung.bezeichnung("005930.KS", " Samsung Electronics "))
        assertEquals("Toyota Motor", HeatmapBeschriftung.bezeichnung("7203.T", "Toyota Motor"))
        assertEquals("Tencent Holdings", HeatmapBeschriftung.bezeichnung("0700.HK", "Tencent Holdings"))
    }

    @Test fun nameWirdNieGekuerzt() {
        val lang = "Taiwan Semiconductor Manufacturing Company Limited"
        assertEquals(lang, HeatmapBeschriftung.bezeichnung("2330.TW", lang))
    }

    @Test fun zahlOhneNamenErgibtNull() {
        assertNull(HeatmapBeschriftung.bezeichnung("000660.KS", null))
        assertNull(HeatmapBeschriftung.bezeichnung("000660.KS", "  "))
    }

    @Test fun buchstabenkuerzelBleibenKuerzel() {
        assertEquals("NVDA", HeatmapBeschriftung.bezeichnung("NVDA", "NVIDIA"))
        assertEquals("SAP", HeatmapBeschriftung.bezeichnung("SAP.DE", "SAP SE"))
        assertEquals("BRK", HeatmapBeschriftung.bezeichnung("BRK-B", "Berkshire"))
        assertEquals("SAP.DE", HeatmapBeschriftung.bezeichnung("SAP.DE", "SAP SE", ohneEndung = false))
        assertEquals("SK Hynix", HeatmapBeschriftung.bezeichnung("000660.KS", "SK Hynix", ohneEndung = false))
    }

    @Test fun widgetBezeichnungNimmtBeiZahlDenGanzenNamen() {
        assertEquals("SK Hynix", widgetBezeichnung("SK Hynix", "000660"))
        assertEquals("Samsung Electronics Co", widgetBezeichnung("Samsung Electronics Co", "005930"))
        // wie bisher: kurzer Name in einem Wort, sonst das Kuerzel
        assertEquals("Apple", widgetBezeichnung("Apple", "AAPL"))
        assertEquals("ABBV", widgetBezeichnung("AbbVie Inc Holdings Corporation", "ABBV"))
    }

    @Test fun widgetTitelMitNameStattZahl() {
        val karte = WidgetKartenBau.volatilitaet("000660.KS", 0.5, 0.4, 1.25, null, "", 0L, "SK Hynix")
        assertTrue(karte.titel, karte.titel.endsWith("SK Hynix"))
        assertFalse(karte.titel.contains("000660"))
        val ohneName = WidgetKartenBau.signale("000660.KS", emptyList(), "", 0L)
        assertFalse(ohneName.titel.contains("000660"))
        val us = WidgetKartenBau.signale("NVDA", emptyList(), "", 0L, "NVIDIA")
        assertTrue(us.titel, us.titel.endsWith("NVDA"))
    }

    @Test fun kandidatenBeiZahlenkuerzel() {
        assertEquals(listOf("NVDA"), de.troetenrepublik.app.engine.HeatmapBeschriftung.kandidaten("NVDA", "NVIDIA"))
        assertEquals(listOf("Samsung Electronics", "Samsung"), de.troetenrepublik.app.engine.HeatmapBeschriftung.kandidaten("005930.KS", "Samsung Electronics"))
        // Ein erstes Wort mit zwei Zeichen sagt nichts, der Name bleibt ganz
        assertEquals(listOf("SK Hynix"), de.troetenrepublik.app.engine.HeatmapBeschriftung.kandidaten("000660.KS", "SK Hynix"))
        assertEquals(listOf("Toyota"), de.troetenrepublik.app.engine.HeatmapBeschriftung.kandidaten("7203.T", "Toyota"))
        assertEquals(emptyList<String>(), de.troetenrepublik.app.engine.HeatmapBeschriftung.kandidaten("7203.T", ""))
    }
}
