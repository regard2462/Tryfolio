package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.GleicheFirma.Notierung
import de.troetenrepublik.app.engine.HeatmapBeschriftung.Text
import de.troetenrepublik.app.engine.HeatmapBeschriftung.Titel
import de.troetenrepublik.app.engine.HeatmapZoomLage.Ausschnitt
import de.troetenrepublik.app.engine.LaenderFirmen.Kandidat
import de.troetenrepublik.app.engine.LaenderFirmen.Kurs
import de.troetenrepublik.app.market.Kursarchiv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung078X2Test {

    // ---------------------------------------------------------------- Punkt 1: dieselbe Firma einmal

    // Boersenwerte in Mrd. Euro wie am 12.11.2026 (Hinterlegungsschein in New York etwa 16 % ueber Taipeh)
    private val tsmAdr = Notierung("TSM", "Taiwan Semiconductor", "TW", 1933.0)
    private val tsmHeim = Notierung("2330.TW", "TSMC", "TW", 1660.0)

    @Test
    fun kurznameUndVollerNameSindEineFirma() {
        assertTrue(GleicheFirma.selbeFirma(tsmAdr, tsmHeim))
        val f = GleicheFirma.zusammenfuehren(listOf(tsmAdr, tsmHeim, Notierung("2454.TW", "MediaTek", "TW", 60.0)))
        assertEquals(2, f.size)
        // Hauptnotierung ist die Heimatboerse, ihr Wert gilt als Boersenwert der Firma
        assertEquals(1, f[0].haupt)
        assertEquals(listOf(0, 1), f[0].mitglieder)
        assertEquals(1660.0, f[0].boersenwert!!, 0.0)
        // United Microelectronics (UMC) in New York und Taipeh
        assertTrue(GleicheFirma.selbeFirma(Notierung("UMC", "United Microelectronics", "TW", 36.4), Notierung("2303.TW", "UMC", "TW", 34.6)))
    }

    @Test
    fun kurznameBrauchtLandUndFastGleichenWert() {
        // Anderes Land, unbekanntes Land, weit auseinander liegender Wert, fehlender Wert: je zwei Firmen
        assertFalse(GleicheFirma.selbeFirma(tsmAdr.copy(land = "US"), tsmHeim))
        assertFalse(GleicheFirma.selbeFirma(tsmAdr.copy(land = null), tsmHeim.copy(land = null)))
        assertFalse(GleicheFirma.selbeFirma(tsmAdr, tsmHeim.copy(boersenwert = 900.0)))
        assertFalse(GleicheFirma.selbeFirma(tsmAdr, tsmHeim.copy(boersenwert = null)))
        // Ein Kurzname, der nicht zu den Anfangsbuchstaben passt
        assertFalse(GleicheFirma.selbeFirma(Notierung("X.TW", "Taiwan Cement", "TW", 100.0), Notierung("Y.TW", "ASUS", "TW", 100.0)))
    }

    @Test
    fun aehnlicheNamenBleibenGetrennt() {
        val gazprom = Notierung("#Gazprom", "Gazprom", "RU", 33.7, kennung = "GAZP")
        val neft = Notierung("#Gazprom Neft", "Gazprom Neft", "RU", 21.7, kennung = "SIBN")
        assertFalse(GleicheFirma.selbeFirma(gazprom, neft))
        // Auch bei zufaellig fast gleichem Boersenwert: ein laengerer Name ist eine andere Firma
        assertFalse(GleicheFirma.selbeFirma(gazprom, neft.copy(boersenwert = 33.0)))
        assertEquals(2, GleicheFirma.zusammenfuehren(listOf(gazprom, neft)).size)
        assertFalse(GleicheFirma.selbeFirma(Notierung("A.KS", "Samsung Electronics", "KR", 300.0), Notierung("B.KS", "Samsung SDI", "KR", 290.0)))
    }

    @Test
    fun stammUndVorzugGattungenUndIsin() {
        // Moskauer Boerse: Kennung mit angehaengtem P
        assertTrue(GleicheFirma.vorzugKennung("SBER", "SBERP"))
        assertFalse(GleicheFirma.vorzugKennung("GAZP", "SIBN"))
        assertFalse(GleicheFirma.vorzugKennung("TRNF", "TRNFPP"))
        assertTrue(GleicheFirma.selbeFirma(Notierung("#Sberbank", "Sberbank", "RU", 30.0, kennung = "SBER"), Notierung("#Sber Vorzug", "Sberbank of Russia pref", "RU", 28.0, kennung = "SBERP")))
        // Gleicher Namensstamm im selben Land wie bisher
        assertTrue(GleicheFirma.selbeFirma(Notierung("GOOGL", "Alphabet (A)", "US", 3000.0), Notierung("GOOG", "Alphabet (C)", "US", 3000.0)))
        // Emittent laut ISIN: USA sechs Stellen, sonst neun Zeichen und gleiches erstes Wort
        assertEquals("US02079K", GleicheFirma.isinStamm("US02079K3059"))
        assertEquals("DE0007664", GleicheFirma.isinStamm("de0007664039"))
        assertNull(GleicheFirma.isinStamm("US02079"))
        assertTrue(GleicheFirma.selbeFirma(Notierung("BRK-A", "Berkshire Hathaway", "US", isin = "US0846701086"), Notierung("BRK-B", "Berkshire B Shares", "US", isin = "US0846707026")))
        assertTrue(GleicheFirma.selbeFirma(Notierung("VOW.DE", "Volkswagen Stammaktie", "DE", isin = "DE0007664005"), Notierung("VOW3.DE", "Volkswagen Vorzugsaktie", "DE", isin = "DE0007664039")))
        assertFalse(GleicheFirma.selbeFirma(Notierung("A.DE", "Alpha Werke", "DE", isin = "DE0007664005"), Notierung("B.DE", "Beta Werke", "DE", isin = "DE0007664039")))
    }

    @Test
    fun ranglisteZeigtJedeFirmaEinmalMitHauptnotierung() {
        val k = listOf(
            Kandidat("TSM", "Taiwan Semiconductor", "HALBLEITER", false), Kandidat("2330.TW", "TSMC", "HALBLEITER", false),
            Kandidat("2454.TW", "MediaTek", "HALBLEITER", false), Kandidat("2317.TW", "Hon Hai", "HALBLEITER", false),
            Kandidat("2412.TW", "Chunghwa Telecom", "TELEKOM", false), Kandidat("2881.TW", "Fubon Financial", "BANKEN", false),
        )
        val kurse = mapOf(
            "TSM" to Kurs(1933.0, 4.3, kgv = 31.0), "2330.TW" to Kurs(1660.0, -2.3, kgv = 27.4), "2454.TW" to Kurs(60.0, -3.5),
            "2317.TW" to Kurs(80.0, 1.0), "2412.TW" to Kurs(34.0, 0.1), "2881.TW" to Kurs(30.0, 0.2),
        )
        val r = LaenderFirmen.rangliste("TW", k, kurse)
        assertEquals(listOf("2330.TW", "2317.TW", "2454.TW", "2412.TW", "2881.TW"), r.map { it.kandidat.symbol })
        // Tagesaenderung und KGV der Hauptnotierung
        assertEquals(-2.3, r[0].aenderung!!, 0.0)
        assertEquals(27.4, r[0].kgv!!, 0.0)
        assertEquals(1660.0, r[0].boersenwert!!, 0.0)
        // Branchenanteile zaehlen die Firma einmal: 1660 von 1864
        val b = LaenderFirmen.branchen("TW", k, kurse)!!
        assertEquals("HALBLEITER", b[0].branche)
        assertEquals(1800.0 / 1864.0, b[0].anteil, 1e-9)
        // Russland: Gazprom und Gazprom Neft bleiben zwei Zeilen
        val ru = listOf(Kandidat("", "Gazprom", "OEL_GAS", true, kennung = "GAZP"), Kandidat("", "Gazprom Neft", "OEL_GAS", false, kennung = "SIBN"))
        val zeilen = LaenderFirmen.rangliste("RU", ru, mapOf("#Gazprom" to Kurs(33.7, null, 2017), "#Gazprom Neft" to Kurs(21.7, null, 2019)))
        assertEquals(listOf("Gazprom", "Gazprom Neft"), zeilen.map { it.kandidat.name })
    }

    // ---------------------------------------------------------------- Punkt 2: Zoom

    @Test
    fun zoomHaeltDenPunktUnterDenFingernUndBleibtImRahmen() {
        val a = HeatmapZoomLage.zoomen(Ausschnitt(), 100f, 50f, 2f, 0f, 0f, 400f, 200f, 8f)
        assertEquals(2f, a.zoom, 1e-6f)
        // Punkt (100, 50) der Flaeche liegt weiter bei (100, 50) auf dem Bildschirm
        assertEquals(100f, 100f * a.zoom + a.vx, 1e-4f)
        assertEquals(50f, 50f * a.zoom + a.vy, 1e-4f)
        assertTrue(a.vergroessert)
        // Verschieben ueber den Rand hinaus wird begrenzt
        val b = HeatmapZoomLage.zoomen(a, 100f, 50f, 1f, 5000f, 5000f, 400f, 200f, 8f)
        assertEquals(0f, b.vx, 0f); assertEquals(0f, b.vy, 0f)
        val c = HeatmapZoomLage.zoomen(a, 100f, 50f, 1f, -5000f, -5000f, 400f, 200f, 8f)
        assertEquals(-400f, c.vx, 1e-4f); assertEquals(-200f, c.vy, 1e-4f)
        // Grenzen des Zooms, zurueck auf 1 liegt die Flaeche wieder ganz im Rahmen
        assertEquals(8f, HeatmapZoomLage.zoomen(a, 0f, 0f, 100f, 0f, 0f, 400f, 200f, 8f).zoom, 0f)
        assertEquals(Ausschnitt(), HeatmapZoomLage.zoomen(c, 300f, 100f, 0.01f, 0f, 0f, 400f, 200f, 8f))
        // Unbestimmte Mitte beim Loslassen aendert nichts
        assertEquals(a, HeatmapZoomLage.zoomen(a, Float.NaN, Float.NaN, 1.5f, 0f, 0f, 400f, 200f, 8f))
    }

    @Test
    fun mausradStufenUndWemDieGesteGehoert() {
        val rein = HeatmapZoomLage.rad(Ausschnitt(), 200f, 100f, -1f, 400f, 200f, 8f)
        assertEquals(HeatmapZoomLage.RAD_SCHRITT, rein.zoom, 1e-6f)
        assertEquals(1f, HeatmapZoomLage.rad(rein, 200f, 100f, 1f, 400f, 200f, 8f).zoom, 1e-5f)
        assertEquals(Ausschnitt(), HeatmapZoomLage.rad(Ausschnitt(), 200f, 100f, 0f, 400f, 200f, 8f))
        val stufen = listOf(1f, 1.5f, 2f, 3f)
        assertEquals(1f, HeatmapZoomLage.stufe(1.49f, stufen), 0f)
        assertEquals(2f, HeatmapZoomLage.stufe(2.7f, stufen), 0f)
        assertEquals(3f, HeatmapZoomLage.stufe(9f, stufen), 0f)
        // Ein Finger bei Zoom 1 rollt die Seite; zwei Finger oder schon vergroessert gehoeren der Karte
        assertFalse(HeatmapZoomLage.gehoertKarte(1, Ausschnitt()))
        assertTrue(HeatmapZoomLage.gehoertKarte(2, Ausschnitt()))
        assertTrue(HeatmapZoomLage.gehoertKarte(1, Ausschnitt(2f)))
    }

    // ---------------------------------------------------------------- Punkt 2 und 5: Zeilen einer Kachel

    private val name = Text(120f, 30f)
    private val kuerzel = Text(40f, 15f)
    private val prozent = Text(50f, 14f)
    private val kgv = Text(60f, 14f)

    @Test
    fun kachelZeigtNameSonstKuerzelSonstNichts() {
        fun z(b: Float, h: Float, n: Text? = name, k: Text? = kuerzel) = HeatmapBeschriftung.firmenZeilen(b, h, 8f, n, k, prozent, kgv)
        assertEquals(Titel.NAME, z(130f, 40f).titel)
        // Name passt in der Breite nicht mehr: Kuerzel; ohne Kuerzel bleibt die Kachel leer
        assertEquals(Titel.KUERZEL, z(100f, 40f).titel)
        assertEquals(Titel.NICHTS, z(100f, 40f, k = null).titel)
        // Name zu hoch: Kuerzel
        assertEquals(Titel.KUERZEL, z(130f, 30f).titel)
        assertEquals(Titel.NICHTS, z(40f, 40f).titel)
        assertEquals(Titel.NICHTS, z(8f, 8f).titel)
        // Der umbrochene Name fehlt (passte nicht ganz): nie ein Teil davon
        assertEquals(Titel.KUERZEL, z(300f, 300f, n = null).titel)
    }

    @Test
    fun reihenfolgeNameAenderungKgv() {
        fun z(b: Float, h: Float, p: Text? = prozent, g: Text? = kgv) = HeatmapBeschriftung.firmenZeilen(b, h, 8f, name, kuerzel, p, g)
        // Hoehe 8 + 30: nur der Name; + 14: mit Aenderung; + 14: mit KGV
        assertEquals(HeatmapBeschriftung.Zeilen(Titel.NAME), z(200f, 51f))
        assertEquals(HeatmapBeschriftung.Zeilen(Titel.NAME, prozent = true), z(200f, 52f))
        assertEquals(HeatmapBeschriftung.Zeilen(Titel.NAME, prozent = true), z(200f, 65f))
        assertEquals(HeatmapBeschriftung.Zeilen(Titel.NAME, prozent = true, kgv = true), z(200f, 66f))
        // Ohne KGV (Verlust, unbekannt) keine dritte Zeile
        assertEquals(HeatmapBeschriftung.Zeilen(Titel.NAME, prozent = true), z(200f, 200f, g = null))
        // Passt die Aenderung nicht, steht auch kein KGV; ohne heutigen Kurs steht das KGV direkt unter dem Titel
        val breitesProzent = Text(500f, 14f)
        assertEquals(HeatmapBeschriftung.Zeilen(Titel.NAME), z(200f, 200f, p = breitesProzent))
        assertEquals(HeatmapBeschriftung.Zeilen(Titel.NAME, kgv = true), z(200f, 200f, p = null))
        // Bezugsbreiten: schmale Kachel bekommt die Aenderung nicht, obwohl die eigene Zahl passte
        assertFalse(HeatmapBeschriftung.firmenZeilen(140f, 100f, 8f, name, kuerzel, prozent, kgv, bezugProzent = 140f).prozent)
        // Zusatzzeile der Maerkte-Heatmap: nur ganz
        assertTrue(HeatmapBeschriftung.zusatzPasst(68f, 51f, 8f, 29f, kgv))
        assertFalse(HeatmapBeschriftung.zusatzPasst(67f, 51f, 8f, 29f, kgv))
        assertFalse(HeatmapBeschriftung.zusatzPasst(68f, 50f, 8f, 29f, kgv))
        assertFalse(HeatmapBeschriftung.zusatzPasst(68f, 51f, 8f, 0f, kgv))
    }

    @Test
    fun kgvUndKuerzelDerKachel() {
        assertEquals(27.4, HeatmapBeschriftung.kgvWert(27.4)!!, 0.0)
        assertNull(HeatmapBeschriftung.kgvWert(null))
        assertNull(HeatmapBeschriftung.kgvWert(0.0))
        assertNull(HeatmapBeschriftung.kgvWert(-12.0))
        assertNull(HeatmapBeschriftung.kgvWert(Double.NaN))
        assertEquals("TSM", HeatmapBeschriftung.firmenKuerzel("TSM"))
        assertEquals("SAP", HeatmapBeschriftung.firmenKuerzel("SAP.DE"))
        // Zahlenkuerzel sagen nichts: dann nur der Name
        assertNull(HeatmapBeschriftung.firmenKuerzel("2330.TW"))
        assertEquals("GAZP", HeatmapBeschriftung.firmenKuerzel("", "GAZP"))
        assertEquals("SIBN", HeatmapBeschriftung.firmenKuerzel("MOEX:SIBN"))
        assertNull(HeatmapBeschriftung.firmenKuerzel("", ""))
    }

    // ---------------------------------------------------------------- Punkt 3: Rollstand

    @Test
    fun stuetzeHaeltDenStandBisDerInhaltTraegt() {
        // Inhalt beim Wiederaufbau erst 900 hoch, gemerkter Stand 2400, Sichtfenster 1600
        assertEquals(4000, Rollstand.hoehe(900, 2400, 1600, 3000, halten = true))
        // Sichtfenster noch unbekannt: Ersatzhoehe
        assertEquals(5400, Rollstand.hoehe(900, 2400, 0, 3000, halten = true))
        // Nicht mehr halten oder kein Stand: echte Hoehe
        assertEquals(900, Rollstand.hoehe(900, 2400, 1600, 3000, halten = false))
        assertEquals(900, Rollstand.hoehe(900, 0, 1600, 3000, halten = true))
        // Echter Inhalt laenger als die Stuetze
        assertEquals(6000, Rollstand.hoehe(6000, 2400, 1600, 3000, halten = true))
        assertFalse(Rollstand.traegt(3999, 2400, 1600))
        assertTrue(Rollstand.traegt(4000, 2400, 1600))
        assertFalse(Rollstand.traegt(9000, 2400, 0))
        assertTrue(Rollstand.stuetzeEnde(900, 2400, 1600, rolltSelbst = true))
        assertFalse(Rollstand.stuetzeEnde(900, 2400, 1600, rolltSelbst = false))
    }

    @Test
    fun standWirdNurNachgeholtWennNichtSelbstGerollt() {
        assertTrue(Rollstand.nachholen(4000, 2400, 1600, 800, gerollt = false))
        assertFalse(Rollstand.nachholen(4000, 2400, 1600, 800, gerollt = true))
        assertFalse(Rollstand.nachholen(4000, 2400, 1600, 2400, gerollt = false))
        assertFalse(Rollstand.nachholen(3000, 2400, 1600, 800, gerollt = false))
        assertTrue(Rollstand.listeNachholen(12, 40, 3, 0, 30, gerollt = false))
        assertFalse(Rollstand.listeNachholen(12, 40, 3, 0, 13, gerollt = false))
        assertFalse(Rollstand.listeNachholen(12, 40, 12, 40, 30, gerollt = false))
        assertFalse(Rollstand.listeNachholen(12, 40, 3, 0, 30, gerollt = true))
        assertFalse(Rollstand.listeNachholen(0, 0, 3, 0, 30, gerollt = false))
    }

    // ---------------------------------------------------------------- Punkt 4: letzter Stand

    @Test
    fun letzterStandMitDatumUndFenster() {
        val tag = 86_400_000L
        // Letzter Kurs von GAZP.ME laut Kursabfrage: 08.07.2022 (regularMarketTime 1657295395)
        val letzter = 1_657_295_395_000L
        val jetzt = 1_794_480_000_000L
        assertTrue(Kursarchiv.veraltet(letzter, jetzt))
        assertFalse(Kursarchiv.veraltet(jetzt - 3 * tag, jetzt))
        assertFalse(Kursarchiv.veraltet(jetzt - 10 * tag, jetzt))
        assertTrue(Kursarchiv.veraltet(jetzt - 10 * tag - 1, jetzt))
        assertFalse(Kursarchiv.veraltet(0L, jetzt))
        assertEquals("GAZP.ME", Kursarchiv.ersatzSymbol("MOEX:GAZP"))
        assertNull(Kursarchiv.ersatzSymbol("GAZP"))
        assertNull(Kursarchiv.ersatzSymbol("SAP.DE"))
        // Ein Jahr bis zum letzten Stand: 08.07.2022 ist Tag 19181
        val (von, bis) = Kursarchiv.fensterTage("1y", letzter)
        assertEquals(19_181L, bis)
        assertEquals(19_181L - 366L, von)
        assertEquals((19_181L - 366L) * 86_400L to 19_182L * 86_400L, Kursarchiv.fensterSekunden("1y", letzter))
        assertEquals(19_181L - 7L, Kursarchiv.fensterTage("1d", letzter).first)
        assertEquals("1d", Kursarchiv.intervall("2m"))
        assertEquals("1wk", Kursarchiv.intervall("1wk"))
        assertEquals("1mo", Kursarchiv.intervall("1mo"))
    }

    @Test
    fun ersatzkursWirdStandDesWerts() {
        val q = Quote(symbol = "GAZP.ME", name = "Public Joint Stock Company Gazprom", price = 198.0, prevClose = 197.92, nativeCurrency = "RUB", nativePrice = 198.0, marketState = "REGULAR", marketTime = 1_657_295_395_000L)
        val s = Kursarchiv.alsStand("MOEX:GAZP", q)!!
        assertEquals("MOEX:GAZP", s.symbol)
        assertEquals("CLOSED", s.marketState)
        assertEquals(198.0, s.price, 0.0)
        assertEquals(1_657_295_395_000L, s.marketTime)
        assertNull(Kursarchiv.alsStand("MOEX:GAZP", null))
        assertNull(Kursarchiv.alsStand("MOEX:GAZP", q.copy(marketTime = 0L)))
        assertNull(Kursarchiv.alsStand("MOEX:GAZP", q.copy(price = 0.0)))
    }
}
