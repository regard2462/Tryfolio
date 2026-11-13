package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Beneish.Kennzahl
import de.troetenrepublik.app.i18n.BeneishTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Beneish-M-Score: acht Kennzahlen, Gewichte und Schwelle der Studie von 1999, nur mit allen Angaben.
class Fassung078U41BeneishTest {

    private val vorjahr = Qualitaet.Jahr(
        ende = "2024-12-31", umsatz = 1000.0, forderungen = 100.0, bruttogewinn = 400.0, umlaufvermoegen = 500.0, sachanlagen = 300.0,
        bilanzsumme = 1000.0, abschreibungen = 50.0, vertriebVerwaltung = 200.0, langfristSchulden = 200.0, kurzfristSchulden = 200.0,
        kasse = 100.0, kurzfristFinanzschulden = 40.0, steuerschuld = 8.0,
    )
    private val jahr = Qualitaet.Jahr(
        ende = "2025-12-31", umsatz = 1200.0, forderungen = 150.0, bruttogewinn = 450.0, umlaufvermoegen = 600.0, sachanlagen = 320.0,
        bilanzsumme = 1150.0, abschreibungen = 52.0, vertriebVerwaltung = 230.0, langfristSchulden = 220.0, kurzfristSchulden = 240.0,
        kasse = 110.0, kurzfristFinanzschulden = 50.0, steuerschuld = 10.0,
    )

    @Test
    fun gewichteUndSchwelle() {
        assertEquals(8, Kennzahl.entries.size)
        assertEquals(-4.840, Beneish.KONSTANTE, 0.0)
        assertEquals(-1.78, Beneish.SCHWELLE, 0.0)
        assertEquals(listOf(0.920, 0.528, 0.404, 0.892, 0.115, -0.172, 4.679, -0.327), Kennzahl.entries.map { it.gewicht })
        assertEquals(listOf("DSRI", "GMI", "AQI", "SGI", "DEPI", "SGAI", "TATA", "LVGI"), Kennzahl.entries.map { it.name })
    }

    @Test
    fun kennzahlen() {
        val k = Beneish.kennzahlen(jahr, vorjahr)!!
        assertEquals(1.25, k.getValue(Kennzahl.DSRI), 1e-9)
        assertEquals(0.4 / 0.375, k.getValue(Kennzahl.GMI), 1e-9)
        assertEquals(1.0, k.getValue(Kennzahl.AQI), 1e-9)
        assertEquals(1.2, k.getValue(Kennzahl.SGI), 1e-9)
        assertEquals((50.0 / 350.0) / (52.0 / 372.0), k.getValue(Kennzahl.DEPI), 1e-9)
        assertEquals((230.0 / 1200.0) / 0.2, k.getValue(Kennzahl.SGAI), 1e-9)
        assertEquals(1.0, k.getValue(Kennzahl.LVGI), 1e-9)
        // Umlaufvermoegen +100, Kasse +10, Verbindlichkeiten +40 ohne Finanzschulden +10 und Steuern +2, Abschreibungen 52
        assertEquals(10.0, Beneish.abgrenzungen(jahr, vorjahr)!!, 1e-9)
        assertEquals(10.0 / 1150.0, k.getValue(Kennzahl.TATA), 1e-9)
        val e = Beneish.berechne(listOf(vorjahr, jahr))!!
        val erwartet = -4.840 + 0.920 * 1.25 + 0.528 * (0.4 / 0.375) + 0.404 + 0.892 * 1.2 + 0.115 * (50.0 / 350.0) / (52.0 / 372.0) -
            0.172 * (230.0 / 1200.0) / 0.2 + 4.679 * 10.0 / 1150.0 - 0.327
        assertEquals(erwartet, e.m, 1e-9)
        assertEquals("2025-12-31", e.jahr)
        assertEquals("2024-12-31", e.vorjahr)
        assertEquals(8, e.teile.size)
        assertEquals(e.m, Beneish.KONSTANTE + e.teile.sumOf { it.beitrag }, 1e-9)
        assertFalse(e.auffaellig)
    }

    @Test
    fun unveraendertesJahr() {
        // Alle Indizes 1, Abgrenzungen nur die Abschreibungen
        val e = Beneish.berechne(listOf(vorjahr, vorjahr.copy(ende = "2025-12-31")))!!
        assertEquals(-4.840 + 2.360 + 4.679 * (-50.0 / 1000.0), e.m, 1e-9)
        assertFalse(e.auffaellig)
    }

    @Test
    fun auffaellig() {
        // Forderungen verdreifacht, Umsatz verdoppelt, Umlaufvermoegen ohne Geldzufluss gestiegen
        val j = jahr.copy(umsatz = 2000.0, forderungen = 600.0, bruttogewinn = 700.0, umlaufvermoegen = 900.0, bilanzsumme = 1500.0, vertriebVerwaltung = 380.0)
        val e = Beneish.berechne(listOf(vorjahr, j))!!
        assertTrue(e.m > Beneish.SCHWELLE)
        assertTrue(e.auffaellig)
        assertTrue(e.teile.first { it.kennzahl == Kennzahl.DSRI }.wert > 1)
    }

    @Test
    fun nurMitAllenAngaben() {
        assertNull(Beneish.berechne(listOf(jahr)))
        assertNull(Beneish.berechne(emptyList()))
        val luecken = listOf<(Qualitaet.Jahr) -> Qualitaet.Jahr>(
            { it.copy(umsatz = null) }, { it.copy(forderungen = null) }, { it.copy(bruttogewinn = null) }, { it.copy(umlaufvermoegen = null) },
            { it.copy(sachanlagen = null) }, { it.copy(bilanzsumme = null) }, { it.copy(abschreibungen = null) }, { it.copy(vertriebVerwaltung = null) },
            { it.copy(kurzfristSchulden = null) }, { it.copy(kasse = null) },
        )
        for ((i, l) in luecken.withIndex()) {
            assertNull("Jahr $i", Beneish.berechne(listOf(vorjahr, l(jahr))))
            assertNull("Vorjahr $i", Beneish.berechne(listOf(l(vorjahr), jahr)))
        }
        // Nenner null oder negativ
        assertNull(Beneish.berechne(listOf(vorjahr.copy(forderungen = 0.0), jahr)))
        assertNull(Beneish.berechne(listOf(vorjahr, jahr.copy(bruttogewinn = -10.0))))
        assertNull(Beneish.berechne(listOf(vorjahr.copy(umsatz = 0.0), jahr)))
        // Ohne langfristige Schulden, kurzfristige Finanzschulden und Steuerschulden zaehlen diese als null
        assertNotNull(Beneish.berechne(listOf(vorjahr.copy(langfristSchulden = null, kurzfristFinanzschulden = null, steuerschuld = null), jahr.copy(langfristSchulden = null, kurzfristFinanzschulden = null, steuerschuld = null))))
    }

    @Test
    fun skalaUndFelder() {
        assertEquals(0.0, Beneish.lage(-9.0), 0.0)
        assertEquals(1.0, Beneish.lage(3.0), 0.0)
        assertEquals(0.555, Beneish.lage(Beneish.SCHWELLE), 1e-9)
        for (f in listOf("annualAccountsReceivable", "annualDepreciationAndAmortization", "annualSellingGeneralAndAdministration", "annualCurrentDebt", "annualIncomeTaxPayable")) {
            assertTrue(f, f in Qualitaet.FELDER)
        }
        val text = """{"timeseries":{"result":[
            {"meta":{"type":["annualAccountsReceivable"]},"annualAccountsReceivable":[{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":150.0}}]},
            {"meta":{"type":["annualReceivables"]},"annualReceivables":[{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":170.0}},{"asOfDate":"2024-12-31","currencyCode":"USD","reportedValue":{"raw":120.0}}]},
            {"meta":{"type":["annualDepreciationAndAmortization"]},"annualDepreciationAndAmortization":[{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":52.0}}]},
            {"meta":{"type":["annualSellingGeneralAndAdministration"]},"annualSellingGeneralAndAdministration":[{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":230.0}}]},
            {"meta":{"type":["annualCurrentDebt"]},"annualCurrentDebt":[{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":50.0}}]},
            {"meta":{"type":["annualIncomeTaxPayable"]},"annualIncomeTaxPayable":[{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":10.0}}]}
        ]}}"""
        val j = Qualitaet.lesen(text)
        assertEquals(2, j.size)
        // Forderungen aus Lieferungen und Leistungen, sonst alle Forderungen
        assertEquals(120.0, j[0].forderungen!!, 0.0)
        assertEquals(150.0, j[1].forderungen!!, 0.0)
        assertEquals(52.0, j[1].abschreibungen!!, 0.0)
        assertEquals(230.0, j[1].vertriebVerwaltung!!, 0.0)
        assertEquals(50.0, j[1].kurzfristFinanzschulden!!, 0.0)
        assertEquals(10.0, j[1].steuerschuld!!, 0.0)
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(BeneishTexte.schluessel(true), BeneishTexte.schluessel(false))
        for (k in BeneishTexte.schluessel(true)) for (de in listOf(true, false)) {
            val t = BeneishTexte.text(de, k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{') || t.contains('–') || t.contains('—') || t.contains('…') || t.contains('?'))
        }
        for (k in Kennzahl.entries) assertTrue(k.name, k.name in BeneishTexte.schluessel(true))
        for (de in listOf(true, false)) assertTrue(BeneishTexte.text(de, "satz", listOf("31.12.2025", "x")).let { it.contains("Hinweis") || it.contains("hint") })
    }
}
