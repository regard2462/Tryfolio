package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.Aktienfinder.Kennzahl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.77, Teil AD1: Marktmacht, Entdecken je Branche, Vorschlagsdepot.
class Fassung077Ad1MarktmachtTest {

    // Gekuerzter Ausschnitt der Antwort von ws/fundamentals-timeseries (Altria, vier Geschaeftsjahre)
    private val altria = """{"timeseries":{"result":[
{"meta":{"symbol":["MO"],"type":["annualTotalRevenue"]},"annualTotalRevenue":[
{"asOfDate":"2022-12-31","currencyCode":"USD","reportedValue":{"raw":2.0688E10}},{"asOfDate":"2023-12-31","currencyCode":"USD","reportedValue":{"raw":2.0502E10}},
{"asOfDate":"2024-12-31","currencyCode":"USD","reportedValue":{"raw":2.0444E10}},{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":2.0139E10}}]},
{"meta":{"symbol":["MO"],"type":["annualGrossProfit"]},"annualGrossProfit":[
{"asOfDate":"2022-12-31","currencyCode":"USD","reportedValue":{"raw":1.4246E10}},{"asOfDate":"2023-12-31","currencyCode":"USD","reportedValue":{"raw":1.4284E10}},
{"asOfDate":"2024-12-31","currencyCode":"USD","reportedValue":{"raw":1.4367E10}},{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":1.4542E10}}]},
{"meta":{"symbol":["MO"],"type":["annualEBIT"]},"annualEBIT":[
{"asOfDate":"2022-12-31","currencyCode":"USD","reportedValue":{"raw":8.517E9}},{"asOfDate":"2023-12-31","currencyCode":"USD","reportedValue":{"raw":1.2077E10}},
{"asOfDate":"2024-12-31","currencyCode":"USD","reportedValue":{"raw":1.4782E10}},{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":1.0566E10}}]},
{"meta":{"symbol":["MO"],"type":["annualTotalAssets"]},"annualTotalAssets":[
{"asOfDate":"2022-12-31","currencyCode":"USD","reportedValue":{"raw":3.6954E10}},{"asOfDate":"2023-12-31","currencyCode":"USD","reportedValue":{"raw":3.857E10}},
{"asOfDate":"2024-12-31","currencyCode":"USD","reportedValue":{"raw":3.5177E10}},{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":3.5017E10}}]},
{"meta":{"symbol":["MO"],"type":["annualCurrentLiabilities"]},"annualCurrentLiabilities":[
{"asOfDate":"2022-12-31","currencyCode":"USD","reportedValue":{"raw":8.616E9}},{"asOfDate":"2023-12-31","currencyCode":"USD","reportedValue":{"raw":1.1319E10}},
{"asOfDate":"2024-12-31","currencyCode":"USD","reportedValue":{"raw":8.781E9}},{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":9.154E9}}]},
{"meta":{"symbol":["MO"],"type":["annualTaxRateForCalcs"]},"annualTaxRateForCalcs":[
{"asOfDate":"2022-12-31","currencyCode":"USD","reportedValue":{"raw":0.22}},{"asOfDate":"2023-12-31","currencyCode":"USD","reportedValue":{"raw":0.256}},
{"asOfDate":"2024-12-31","currencyCode":"USD","reportedValue":{"raw":0.175282}},{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":0.260092}}]}
],"error":null}}"""

    private fun e(symbol: String, umsatz: Double?, brutto: List<Double>, betrieb: List<Double>, rendite: List<Double>, branche: String = "B") =
        Marktmacht.Eingabe(symbol, branche, umsatz, brutto, betrieb, rendite)

    @Test fun reihenAusDemAbschluss() {
        val j = Qualitaet.lesen(altria)
        assertEquals(4, j.size)
        // Dollar je Euro 1,16: 20,139 Mrd. USD
        val x = Marktmacht.eingabe("MO", "ALKOHOL_TABAK", j, 1.16)
        assertEquals(20.139e9 / 1.16, x.umsatz!!, 1.0)
        assertEquals(4, x.brutto.size)
        assertEquals(14246.0 / 20688.0 * 100, x.brutto.first(), 1e-6)
        assertEquals(10566.0 / 20139.0 * 100, x.betrieb.last(), 1e-6)
        // Kapitalrendite 2025: EBIT mal (1 minus Steuersatz) durch (Bilanzsumme minus kurzfristige Schulden)
        assertEquals(10566.0 * (1 - 0.260092) / (35017.0 - 9154.0) * 100, x.rendite.last(), 1e-6)
        // Ohne Wechselkurs bleibt der Umsatz unbekannt, die Margen nicht
        assertNull(Marktmacht.eingabe("MO", "X", j, null).umsatz)
    }

    @Test fun marktstellungNachUmsatzanteil() {
        // Groesster mit 40 % und unter den drei Groessten mit zusammen 85 %: Oligopol
        assertEquals(100.0, Marktmacht.stellungPunkte(40.0, 1, 85.0), 1e-9)
        assertEquals(50.0, Marktmacht.stellungPunkte(10.0, 5, 85.0), 1e-9)
        assertEquals(58.0, Marktmacht.stellungPunkte(10.0, 2, 85.0), 1e-9)
        assertEquals(28.0, Marktmacht.stellungPunkte(5.0, 8, 30.0), 0.5)
        assertEquals(0.0, Marktmacht.stellungPunkte(0.5, 20, 30.0), 1e-9)
    }

    @Test fun anteilUndRangNurMitAbdeckung() {
        val umsaetze = listOf(400.0, 300.0, 150.0, 100.0, 50.0)
        val ein = umsaetze.mapIndexed { i, u -> e("S$i", u * 1e9, emptyList(), emptyList(), emptyList()) }
        // Fuenf Werte im Katalog, alle mit Umsatz: Anteil gilt
        val r = Marktmacht.auswerten(ein, mapOf("B" to 5))
        assertEquals(40.0, r.getValue("S0").angaben.stellung!!.anteil, 1e-9)
        assertEquals(1, r.getValue("S0").angaben.stellung!!.rang)
        assertEquals(3, r.getValue("S2").angaben.stellung!!.rang)
        // Zehn Werte im Katalog, nur fuenf mit Umsatz (50 %): zu wenig, der Anteil wird nicht geraten
        assertTrue(Marktmacht.auswerten(ein, mapOf("B" to 10)).isEmpty())
        // Unter fuenf Werten kein Vergleich
        assertTrue(Marktmacht.auswerten(ein.take(4), mapOf("B" to 4)).isEmpty())
    }

    @Test fun preismachtGegenDenMedianDerBranche() {
        // Bruttomargen der Branche: 30, 32, 34, 36, 60 (Median 34)
        val margen = listOf(30.0, 32.0, 34.0, 36.0, 60.0)
        val ein = margen.mapIndexed { i, m -> e("S$i", null, listOf(m, m, m), listOf(10.0, 10.0, 10.0), emptyList()) }
        val r = Marktmacht.auswerten(ein)
        val hoch = r.getValue("S4")
        assertEquals(100.0, hoch.teile.getValue(Marktmacht.Teil.PREISMACHT), 1e-9)
        assertEquals(34.0, hoch.angaben.branchenMarge!!, 1e-9)
        assertEquals(3, hoch.angaben.jahreUeber)
        assertTrue(hoch.angaben.brutto)
        assertEquals(42.0, r.getValue("S0").teile.getValue(Marktmacht.Teil.PREISMACHT), 1e-9)
        // Nur ein Baustein: keine Stufe
        assertNull(hoch.stufe)
    }

    @Test fun preismachtFaelltAufDieOperativeMargeZurueck() {
        val ein = (0 until 5).map { i -> e("S$i", null, emptyList(), listOf(8.0 + i, 8.0 + i), emptyList()) }
        val a = Marktmacht.auswerten(ein).getValue("S4")
        assertTrue(!a.angaben.brutto)
        assertEquals(10.0, a.angaben.branchenMarge!!, 1e-9)
        assertEquals(50.0 + (12.0 - 10.0) * 2.0, a.teile.getValue(Marktmacht.Teil.PREISMACHT), 1e-9)
    }

    @Test fun bestaendigkeitBrauchtDreiJahre() {
        assertEquals(100.0, Marktmacht.renditePunkte(25.0), 1e-9)
        assertEquals(0.0, Marktmacht.renditePunkte(-3.0), 1e-9)
        assertEquals(100.0, Marktmacht.ruhePunkte(listOf(20.0, 20.0, 20.0)), 1e-9)
        assertEquals(0.0, Marktmacht.ruhePunkte(listOf(-5.0, -2.0, -4.0)), 1e-9)
        val ein = (0 until 5).map { i ->
            e("S$i", null, emptyList(), listOf(20.0, 20.0, 20.0).take(if (i == 4) 2 else 3), listOf(24.0, 30.0, 26.0).take(if (i == 4) 2 else 3))
        }
        val r = Marktmacht.auswerten(ein)
        assertEquals(100.0, r.getValue("S0").teile.getValue(Marktmacht.Teil.BESTAENDIGKEIT), 1e-9)
        assertEquals(24.0, r.getValue("S0").angaben.renditeMin!!, 1e-9)
        assertNull(r["S4"]?.teile?.get(Marktmacht.Teil.BESTAENDIGKEIT))
    }

    @Test fun stufenAbZweiBausteinen() {
        assertEquals(Marktmacht.Stufe.HOCH, Marktmacht.stufe(62.0, 2))
        assertEquals(Marktmacht.Stufe.MITTEL, Marktmacht.stufe(61.9, 3))
        assertEquals(Marktmacht.Stufe.GERING, Marktmacht.stufe(41.9, 3))
        assertNull(Marktmacht.stufe(90.0, 1))
    }

    @Test fun neuerBereichMitGewichtFuenfzehn() {
        assertEquals(100, Bereich.entries.sumOf { it.gewicht })
        assertEquals(15, Bereich.MARKTMACHT.gewicht)
        assertEquals(25, Bereich.BEWERTUNG.gewicht)
        assertEquals(15, Bereich.QUALITAET.gewicht)
        assertEquals(15, Bereich.TREND.gewicht)
        Kennzahl.entries.forEach { assertTrue(it.bereich in Bereich.entries) }
    }

    private fun mitMarktmacht(symbol: String, punkte: Double, branche: String = "SOFTWARE", land: String = "US", stufe: Marktmacht.Stufe? = Marktmacht.Stufe.HOCH) =
        Aktienfinder.Daten(
            symbol, symbol, land = land, branche = branche, kgv = 10.0, note = 1.5, jahr = 20.0, linie200 = 10.0,
            marktmacht = Marktmacht.Ergebnis(punkte, stufe, mapOf(Marktmacht.Teil.PREISMACHT to punkte), Marktmacht.Angaben(marge = 40.0, branchenMarge = 20.0, jahre = 3, jahreUeber = 3)),
        )

    @Test fun marktmachtGehtInDiePunkteEin() {
        val hoch = mitMarktmacht("A", 100.0)
        val niedrig = mitMarktmacht("B", 0.0)
        val e = Aktienfinder.bewerten(listOf(hoch, niedrig)).associateBy { it.symbol }
        assertEquals(100.0, e.getValue("A").bereiche.getValue(Bereich.MARKTMACHT)!!, 1e-9)
        assertEquals(0.0, e.getValue("B").bereiche.getValue(Bereich.MARKTMACHT)!!, 1e-9)
        // Der Abstand der Gesamtpunkte kommt allein aus der Marktmacht: 15 % von 100 Punkten
        assertEquals(15.0, e.getValue("A").gesamt - e.getValue("B").gesamt, 1e-9)
        assertTrue(e.getValue("A").gruende.any { it.kennzahl == Kennzahl.PREIS_BRUTTO && it.gut })
        // Ohne Marktmacht-Daten zaehlt der Bereich neutral und ist leer
        val ohne = Aktienfinder.bewerten(listOf(Aktienfinder.Daten("C", "C", kgv = 10.0))).single()
        assertNull(ohne.bereiche[Bereich.MARKTMACHT])
    }

    @Test fun branchenGruppenUndKleineBranchenSammeln() {
        val gross = (1..6).map { mitMarktmacht("G$it", 50.0 + it, "SOFTWARE") }
        val kleinA = (1..3).map { mitMarktmacht("K$it", 70.0 + it, "FORST") }
        val kleinB = (1..2).map { mitMarktmacht("L$it", 60.0 + it, "UMWELT") }
        val einzeln = listOf(mitMarktmacht("Z", 99.0, "BAU"))
        val g = FinderBranchen.gruppen(Aktienfinder.bewerten(gross + kleinA + kleinB + einzeln))
        assertEquals(listOf("SOFTWARE", FinderBranchen.SAMMEL), g.map { it.branche }.sortedBy { if (it.isEmpty()) 1 else 0 })
        val sammel = g.first { it.branche == FinderBranchen.SAMMEL }
        // Sechs Werte: FORST, UMWELT und BAU zusammen
        assertEquals(6, sammel.werte.size)
        assertEquals("Z", sammel.werte.first().symbol)
        assertTrue(g.first { it.branche == "SOFTWARE" }.werte.zipWithNext().all { (a, b) -> a.gesamt >= b.gesamt })
        // Weniger als fuenf Werte in allen kleinen Branchen zusammen: weg
        assertEquals(1, FinderBranchen.gruppen(Aktienfinder.bewerten(gross + kleinB)).size)
    }

    @Test fun gewichteMitDeckel() {
        val w = Vorschlagsdepot.gewichte(List(15) { if (it == 0) 200.0 else 60.0 })
        assertEquals(1.0, w.sum(), 1e-9)
        assertTrue(w.all { it <= Vorschlagsdepot.DECKEL + 1e-9 })
        assertEquals(Vorschlagsdepot.DECKEL, w[0], 1e-9)
        // Wenige Werte: gleicher Anteil statt Deckel
        val wenige = Vorschlagsdepot.gewichte(listOf(90.0, 60.0, 60.0, 60.0))
        assertEquals(1.0, wenige.sum(), 1e-9)
        assertTrue(wenige.all { it <= 0.25 + 1e-9 })
    }

    @Test fun vorschlagsdepotStreutUndFiltert() {
        val branchen = listOf("SOFTWARE", "HALBLEITER", "COMPUTER", "ELEKTRO", "PHARMA", "BANKEN", "OEL_GAS", "BAU", "NAHRUNG", "TELEKOM")
        val laender = listOf("US", "DE", "JP", "GB")
        // 40 Werte, viele in Technologie und in den USA, hohe Punkte
        val daten = (0 until 40).map { i ->
            mitMarktmacht("T$i", 80.0, branchen[i % branchen.size], laender[i % laender.size])
        } + listOf(
            mitMarktmacht("KEIN", 80.0, "SOFTWARE", "DE", Marktmacht.Stufe.GERING),
            Aktienfinder.Daten("OHNE", "OHNE", land = "DE", branche = "BAU", kgv = 10.0, note = 1.5, jahr = 20.0, linie200 = 10.0),
            // Ein Bereich klar im Minus (Trend), obwohl die Marktmacht hoch ist
            mitMarktmacht("TREND", 80.0, "PHARMA", "GB").copy(jahr = -60.0, linie200 = -40.0),
        )
        val erg = Aktienfinder.bewerten(daten)
        val depot = Vorschlagsdepot.zusammenstellen(erg, 0L)
        val syms = depot.eintraege.map { it.ergebnis.symbol }
        assertTrue(syms.size in 10..Vorschlagsdepot.MAX_WERTE)
        assertTrue("KEIN" !in syms && "OHNE" !in syms)
        assertEquals(1.0, depot.eintraege.sumOf { it.gewicht }, 1e-9)
        assertTrue(depot.eintraege.groupBy { it.ergebnis.daten.branche }.values.all { it.size <= Vorschlagsdepot.JE_BRANCHE })
        assertTrue(depot.eintraege.groupBy { it.sektor }.values.all { it.size <= Vorschlagsdepot.JE_SEKTOR })
        assertTrue(depot.eintraege.groupBy { it.ergebnis.daten.land }.values.all { it.size <= Vorschlagsdepot.JE_LAND })
        assertTrue(depot.eintraege.all { it.gewicht <= Vorschlagsdepot.DECKEL + 1e-9 })
        assertNotNull(Vorschlagsdepot.sektor("SOFTWARE"))
        assertNull(Vorschlagsdepot.sektor("GIBTS_NICHT"))
    }

    @Test fun alleKatalogBranchenHabenEinenSektor() {
        val branchen = listOf(
            "BANKEN", "SOFTWARE", "HALBLEITER", "GESUNDHEIT", "MASCHINEN", "VERSORGER", "VERSICHERUNG", "BAU", "EINZELHANDEL", "IMMOBILIEN", "CHEMIE", "VERKEHR",
            "FAHRZEUGE", "OEL_GAS", "ELEKTRO", "DIENSTLEISTUNGEN", "PHARMA", "LUFTFAHRT", "COMPUTER", "NAHRUNG", "TELEKOM", "BERGBAU", "GASTGEWERBE", "MEDIEN",
            "HANDEL", "HOLDINGS", "KONSUM", "ALKOHOL_TABAK", "TEXTIL", "BIOTECH", "FORST", "UMWELT",
        )
        assertTrue(branchen.all { Vorschlagsdepot.sektor(it) != null })
    }
}
