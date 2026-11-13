package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.MarktLaenderTexte
import de.troetenrepublik.app.i18n.MarktzeichenTexte
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.GUIDE_FOTOS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

// Einheitliche Begriffe (0.78): ein Name je Sache in beiden Sprachen, der groesste Rueckgang mit Minuszeichen,
// zusammengelegte Erklaertexte und die gekuerzte Kurzanleitung.
class Fassung078U1BegriffeTest {
    @Before fun deutsch() { I18n.lang = Lang.DE }

    private fun quelle(name: String) =
        File(listOf(File("src/main/java"), File("../app/src/main/java")).first { it.exists() }, "de/troetenrepublik/app/i18n/$name").readText()

    @Test fun abgeloesteBegriffeFehlen() {
        val de = quelle("De.kt")
        for (alt in listOf(
            "Stop-Loss", " Merkliste", "Preisalarm\"", "Preisalarme", "Preisalarm:", "Kursmarke", "Marktkapitalisierung", "Rückgang vom Hoch", "Max. Rückgang",
            "Angst-Gier", "Bewertung CAPE", "Kaufen und halten", "Schwankung (1 Jahr)", "VIX-Terminstruktur", "Rezessionsrisiko USA",
            "Mittleres Kursziel", "Kursrückgang S&P 500", "nur kaufen, nicht schreiben", "frei handelbaren Aktien",
            "Verschlingen", "bullisch", "Bullisch", "bärisch", "Bärisch", "Morgenstern", "Abendstern", "mgekehrter Hammer", "Sternschnuppe", "ängender Mann", "unkle Wolke",
            "Durchstoßendes", "weiße Soldaten", "schwarze Krähen", "Kerzenmuster", "Umkehrmuster",
        )) assertFalse(alt, de.contains(alt))
        val en = quelle("En.kt")
        for (alt in listOf("stop-loss", "Max drawdown", "Largest drop", "Valuation CAPE", "VIX term structure", "can only be bought, not written")) {
            assertFalse(alt, en.contains(alt, ignoreCase = true))
        }
        // „Market value“ bleibt nur beim Wert einer Position in den Dokumenten
        assertEquals(1, Regex("[Mm]arket value").findAll(en).count())
        for (t in listOf(quelle("MarktLaenderTexte.kt"), quelle("MarktzeichenTexte.kt"))) {
            assertFalse(t.contains("im Jahr\""))
            assertFalse(t.contains("Kursrückgang S&P 500"))
        }
    }

    @Test fun gleicheNamenFuerGleicheGroessen() {
        for ((s, deutsch) in listOf(De to true, En to false)) {
            // Blasen-Detektor, Bodenzeichen und Rueckblick nennen KGV und Renditeabstand gleich
            assertEquals(s.blName("kgv"), MarktzeichenTexte.text(deutsch, "b.n.kgv", emptyList()))
            assertEquals(s.blName("praemie"), MarktzeichenTexte.text(deutsch, "b.n.praemie", emptyList()))
            assertEquals(s.blName("kgv"), s.wrZeichen("kgv"))
            assertEquals(s.blName("praemie"), s.wrZeichen("praemie"))
            assertEquals(s.blName("trend"), s.wrZeichen("hitze"))
            assertEquals(s.fwName("vix_struktur"), s.wrZeichen("vix_struktur"))
            assertEquals(s.maxDrawdown, s.ppfMaxRueckgang)
            assertEquals(s.maxDrawdown, s.copyMaxDrawdown)
            assertEquals(s.maxDrawdown, s.widgetRueckgang)
            assertEquals(s.lbName("ZINS_HEUTE", "").trim(), s.zeTitel)
        }
        assertEquals("Überdehnung über der 200-Tage-Linie", De.blName("trend"))
        assertEquals("Fear & Greed Aktien", De.blName("stimmung"))
        assertEquals("VIX gegen VIX3M", De.fwName("vix_struktur"))
        assertEquals("Größter Rückgang", De.maxDrawdown)
        assertEquals("Größter Rückgang (nur Wertpapiere)", De.pvMetric("rueckgang.depot"))
        assertEquals("Schwankung (nur Wertpapiere)", De.pvMetric("schwankung.depot"))
        assertEquals("Kursziel (Median)", De.bkZielMedian)
        assertEquals("Umsatz Quartal", De.revenueGrowth)
        assertEquals("Gewinn Quartal", De.earningsGrowth)
        assertEquals("Rezession jetzt (USA)", De.rezWahrsch)
        assertEquals("Fed Funds laut Futures", De.zeTitel)
        assertEquals("Phasenzähler (USA)", MarktLaenderTexte.text(true, "pz.titel", emptyList()))
        assertEquals("Zinsstruktur 10 J − 1 J normal", MarktLaenderTexte.text(true, "pz.ZINSSTRUKTUR.1", emptyList()))
        assertEquals("S&P 500 in 12 Monaten", MarktzeichenTexte.text(true, "b.n.rueckgang", emptyList()))
        assertTrue(MarktzeichenTexte.text(true, "v.kopf", listOf("investiert")).startsWith("Vierer-Indikator (DAX)"))
    }

    @Test fun varNenntDenZeitraum() {
        for (s in listOf(De, En)) {
            val namen = listOf(s.pvMetric("var"), s.pvMetric("var.woche"), s.ptVar("95"))
            assertEquals(3, namen.toSet().size)
            assertEquals(3, listOf(s.pvMetric("es"), s.pvMetric("es.woche"), s.ptEs("95")).toSet().size)
            assertTrue(namen.all { it.endsWith(")") })
        }
        assertTrue(De.pvMetric("var").endsWith("(1 Tag)"))
        assertTrue(De.ptVar("95").endsWith("(1 Jahr)"))
    }

    @Test fun erklaertexteStehenEinmal() {
        for (s in listOf(De, En)) {
            assertEquals(s.zgLimit, s.wqMeldung("limit"))
            assertEquals(s.ensoKurz, s.lbErk("ENSO", ""))
            val lizenz = Ranglisten.Index.entries.map { s.rlInfo(it) }.filter { it.contains("UNDP") }.map { it.substringAfterLast("\n\n") }
            assertEquals(3, lizenz.size)
            assertEquals(1, lizenz.toSet().size)
        }
        for (name in listOf("De.kt", "En.kt")) {
            val t = quelle(name)
            assertEquals(name, 1, Regex("UNDP, Human Development Report").findAll(t).count())
            assertEquals(name, 1, Regex("Dreimonatsschnitt der US-Arbeitslosenquote|Three-month average of the US unemployment rate").findAll(t).count())
        }
    }

    @Test fun groessterRueckgangMitMinus() {
        assertEquals("−8,1 %", Fmt.signedPct(-kotlin.math.abs(8.1), 1))
        assertEquals("−8,1 %", Fmt.signedPct(-kotlin.math.abs(-8.1), 1))
        assertEquals("0,0 %", Fmt.signedPct(-kotlin.math.abs(0.0), 1))
        val analytics = WidgetKartenBau.analytics(12.3, -8.1, "Apple", 23.4, "", 0L)
        assertEquals(De.maxDrawdown, analytics.zeilen[1].text)
        assertEquals("−8,1 %", analytics.zeilen[1].wert)
        val performance = WidgetKartenBau.performance(12.3, 8.1, 7.4, 9.8, listOf(100.0, 101.0), 1L)
        assertEquals("−9,8 %", performance.zeilen.last().wert)
        assertEquals("pro Jahr", De.widgetZinsfuss.substringAfter(' '))
    }

    @Test fun kurzanleitungHatVierBildseiten() {
        assertEquals(listOf("start_raster", "suche", "kaufen", "profil"), GUIDE_FOTOS)
        for (s in listOf(De, En)) {
            assertEquals(4, s.guidePages.size)
            assertEquals(4, s.guidePagesDesktop.size)
            assertEquals(s.guidePages.map { it.first }, s.guidePagesDesktop.map { it.first })
        }
        val ueberblick = De.guideFunktionen(widgets = true, bildschutz = true).joinToString("\n") { it.first + ": " + it.second }
        for (wort in listOf("Stimmung, Ausblick und Lage", "Liste, Radar, Branchen und Vorschlagsdepot", "„Rückblick“", "Dividendentermine des nächsten Handelstags", "Preismarken")) {
            assertTrue(wort, ueberblick.contains(wort))
        }
        for (alt in listOf("„Vergleich“", "der Woche", "Preisalarm", "Kursmarke", "Buchungen")) assertFalse(alt, ueberblick.contains(alt))
        assertFalse(De.guidePages.joinToString { it.second }.contains("Buchungen"))
        val overview = En.guideFunktionen(widgets = true, bildschutz = true).joinToString("\n") { it.second }
        for (wort in listOf("Outlook and Situation", "List, Radar, Industries and Suggested portfolio", "“Review”", "dividend dates of the next trading day")) {
            assertTrue(wort, overview.contains(wort))
        }
    }
}
