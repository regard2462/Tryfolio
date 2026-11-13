package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.data.WidgetGrafik
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Langdruck auf Kategorien und Widget Blasen-Gefahr (0.77): Abbildung Leiste -> Art und Voreinstellung, Blasen-Stufe im
// Widget-Modell, Auswahl der Termine, Zuschnitt mit Hauptaussage und Grafik.
class Fassung077ATest {

    @Test fun maerktReiterAufArten() {
        assertEquals(WidgetKarten.LAGE, WidgetVorgabe.maerkte("LAGE")!!.art)
        assertEquals(WidgetKarten.TRENDS, WidgetVorgabe.maerkte("TRENDS")!!.art)
        assertEquals(WidgetKarten.BEWEGUNGEN, WidgetVorgabe.maerkte("BEWEGUNGEN")!!.art)
        assertEquals(WidgetKarten.STIMMUNG, WidgetVorgabe.maerkte("STIMMUNG")!!.art)
        assertEquals(WidgetKarten.AUSBLICK, WidgetVorgabe.maerkte("AUSBLICK")!!.art)
        assertEquals(WidgetKarten.GLOBUS, WidgetVorgabe.maerkte("GLOBUS")!!.art)
        assertEquals("MARKT", WidgetVorgabe.maerkte("HEATMAP")!!.einstellung["heatmapQuelle"])
        assertNull(WidgetVorgabe.maerkte("unbekannt"))
    }

    @Test fun abschnitteAufArten() {
        assertEquals(WidgetKarten.DURCHBRUECHE, WidgetVorgabe.abschnitt("durchbrueche")!!.art)
        assertEquals(WidgetKarten.FRUEHWARNUNG, WidgetVorgabe.abschnitt("fruehwarnung")!!.art)
        assertEquals(WidgetKarten.MARKTPHASE, WidgetVorgabe.abschnitt("marktphase")!!.art)
        assertEquals(WidgetKarten.BLASEN, WidgetVorgabe.abschnitt("blasen")!!.art)
    }

    @Test fun copytradingReiterMitVoreinstellung() {
        fun v(r: String, t: String? = null) = WidgetVorgabe.copytrading(r, t).let { assertEquals(WidgetKarten.COPYTRADING, it.art); it.einstellung[WidgetVorgabe.VARIANTE] }
        assertEquals(WidgetVorgabe.COPY_POLITIK, v("POLITIK"))
        assertEquals(WidgetVorgabe.COPY_POLITIK, v("POLITIK", "KONGRESS"))
        assertEquals(WidgetVorgabe.COPY_SENAT, v("POLITIK", "SENAT"))
        assertEquals(WidgetVorgabe.COPY_REGIERUNG, v("POLITIK", "REGIERUNG"))
        assertEquals(WidgetVorgabe.COPY_VORSTAENDE, v("VORSTAENDE"))
        assertEquals(WidgetVorgabe.COPY_INVESTOREN, v("INVESTOREN"))
        assertEquals(WidgetVorgabe.COPY_STRATEGIEN, v("STRATEGIEN"))
        assertEquals(WidgetVorgabe.COPY_GEFOLGT, v("GEFOLGT"))
    }

    @Test fun newsReiterMitVoreinstellung() {
        assertEquals(WidgetDaten.NEWS_WICHTIG, WidgetVorgabe.news("WICHTIG").einstellung["news"])
        assertEquals(WidgetDaten.NEWS_ALLE, WidgetVorgabe.news("ALLE").einstellung["news"])
        assertEquals(WidgetDaten.NEWS_GEMERKT, WidgetVorgabe.news("GEMERKT").einstellung["news"])
        assertEquals("anbieter:TRUMP", WidgetVorgabe.news("TRUMP").einstellung["news"])
        assertEquals(WidgetDaten.newsFeed("abc"), WidgetVorgabe.news(null, "abc").einstellung["news"])
        assertEquals(WidgetKarten.NEWS, WidgetVorgabe.news("YAHOO").art)
    }

    @Test fun ordersPrognoseAktienfinderTermine() {
        assertEquals(WidgetVorgabe.ORDERS_ORDERS, WidgetVorgabe.orders(0).einstellung[WidgetVorgabe.VARIANTE])
        assertEquals(WidgetVorgabe.ORDERS_PLAENE, WidgetVorgabe.orders(1).einstellung[WidgetVorgabe.VARIANTE])
        assertEquals(WidgetKarten.ORDERS, WidgetVorgabe.orders(1).art)
        for (r in listOf("POLYMARKET", "KALSHI", "GROSS", "WETTEN")) {
            val v = WidgetVorgabe.prognose(r)
            assertEquals(WidgetKarten.PROGNOSE, v.art)
            assertEquals(r.lowercase(), v.einstellung[WidgetVorgabe.VARIANTE])
        }
        assertEquals("SP500", WidgetVorgabe.aktienfinder("SP500").einstellung[WidgetVorgabe.VARIANTE])
        assertEquals("ESTX50", WidgetVorgabe.aktienfinder("ESTX50").einstellung[WidgetVorgabe.VARIANTE])
        assertEquals(WidgetKarten.AKTIENFINDER, WidgetVorgabe.aktienfinder("DAX").art)
        for (t in WidgetVorgabe.TERMINE) {
            assertEquals(WidgetKarten.TERMINE, WidgetVorgabe.termine(t).art)
            assertEquals(t, WidgetVorgabe.termine(t).einstellung[WidgetVorgabe.VARIANTE])
        }
    }

    @Test fun heatmapUndWatchlist() {
        val h = WidgetVorgabe.heatmap("SP500", 2)
        assertEquals(WidgetKarten.HEATMAP, h.art)
        assertEquals("SP500", h.einstellung["heatmapQuelle"])
        assertEquals("2", h.einstellung["heatmapZeit"])
        val w = WidgetVorgabe.watchliste("Tech", listOf("AAPL", "MSFT", "AAPL"))
        assertEquals(WidgetKarten.WERTE, w.art)
        assertEquals("liste", w.einstellung["quelle"])
        assertEquals("AAPL,MSFT", w.einstellung["liste"])
        assertEquals("Tech", w.einstellung["name"])
    }

    @Test fun werkzeugeAufArten() {
        assertEquals(WidgetKarten.PERFORMANCE, WidgetVorgabe.werkzeug("PERFORMANCE")!!.art)
        assertEquals(WidgetKarten.DEPOTCHECK, WidgetVorgabe.werkzeug("CHECK")!!.art)
        assertEquals(WidgetKarten.STREUUNG, WidgetVorgabe.werkzeug("DIVERSIFICATION")!!.art)
        assertEquals(WidgetKarten.UMSCHICHTEN, WidgetVorgabe.werkzeug("REBALANCE")!!.art)
        assertEquals(WidgetKarten.CRASHTEST, WidgetVorgabe.werkzeug("CRASH")!!.art)
        assertEquals(WidgetKarten.DIVIDENDEN, WidgetVorgabe.werkzeug("DIVIDENDS")!!.art)
        assertEquals(WidgetVorgabe.ORDERS_PLAENE, WidgetVorgabe.werkzeug("SPARPLAN")!!.einstellung[WidgetVorgabe.VARIANTE])
        assertEquals(WidgetKarten.AKTIENFINDER, WidgetVorgabe.werkzeug("FINDER")!!.art)
        // Rechner und Szenarien haben kein Widget
        assertNull(WidgetVorgabe.werkzeug("WEALTH"))
        assertNull(WidgetVorgabe.werkzeug("WITHDRAWAL"))
        assertNull(WidgetVorgabe.werkzeug("SCENARIO"))
        assertEquals(WidgetKarten.DEPOT, WidgetVorgabe.depotverlauf().art)
        assertEquals(WidgetDaten.ZEIT_JAHR, WidgetVorgabe.depotverlauf().einstellung["zeitraum"])
    }

    @Test fun variantenNurFuerBekannteArten() {
        assertNull(WidgetVorgabe.gueltig(WidgetKarten.COPYTRADING, "unsinn"))
        assertNull(WidgetVorgabe.gueltig(WidgetKarten.BLASEN, "politik"))
        assertEquals("orders:plaene", WidgetVorgabe.kartenSchluessel(WidgetKarten.ORDERS, "plaene"))
        assertEquals("orders", WidgetVorgabe.kartenSchluessel(WidgetKarten.ORDERS, null))
        assertEquals("aktienfinder:DAX", WidgetVorgabe.kartenSchluessel(WidgetKarten.AKTIENFINDER, "DAX"))
        assertEquals("aktienfinder", WidgetVorgabe.kartenSchluessel(WidgetKarten.AKTIENFINDER, "kein universum"))
        assertEquals(WidgetKarten.ORDERS, WidgetVorgabe.art("orders:plaene"))
        assertEquals("plaene", WidgetVorgabe.variante("orders:plaene"))
        assertNull(WidgetVorgabe.variante("orders"))
    }

    @Test fun alleNeuenArtenSindAnlegbarMitZielUndTitel() {
        for (art in WidgetKarten.NEUE_ARTEN) {
            assertTrue(art in WidgetKarten.ANLEGBAR)
            assertTrue(art in WidgetKarten.ARTEN)
            val ziel = WidgetKarten.ziel(art)
            assertTrue("Ziel $art", ziel.isNotBlank())
            assertNotNull("Ziel erlaubt $art", WidgetZiel.pruefen(ziel))
            assertTrue(WidgetKarten.titel(art).isNotBlank())
        }
        assertEquals("maerkte/ausblick/blasen", WidgetKarten.ziel(WidgetKarten.BLASEN))
        for (t in listOf("discover/ipos", "discover/earnings", "discover/economy", "discover/dividends")) assertNotNull(WidgetZiel.pruefen(t))
        assertTrue(WidgetKarten.DEPOT in WidgetKarten.ANLEGBAR && WidgetKarten.DIVIDENDEN in WidgetKarten.ANLEGBAR)
    }

    // ------------------------------------------------------------ Blasen-Gefahr

    private fun ind(k: String, w: Double, a: Ampel) = Blasen.Indikator(k, w, a)

    @Test fun blasenStufeImWidgetModell() {
        val gering = listOf(ind(Blasen.CAPE, 20.0, Ampel.GRUEN), ind(Blasen.KGV, 15.0, Ampel.GRUEN), ind(Blasen.TREND, 2.0, Ampel.GRUEN), ind(Blasen.BREITE, 1.0, Ampel.GRUEN))
        val erhoeht = listOf(ind(Blasen.CAPE, 38.0, Ampel.ROT), ind(Blasen.KGV, 22.0, Ampel.GELB), ind(Blasen.TREND, 2.0, Ampel.GRUEN), ind(Blasen.BREITE, 1.0, Ampel.GRUEN))
        val hoch = listOf(ind(Blasen.CAPE, 38.0, Ampel.ROT), ind(Blasen.KGV, 30.0, Ampel.ROT), ind(Blasen.TREND, 20.0, Ampel.ROT), ind(Blasen.BREITE, 1.0, Ampel.GRUEN))
        for ((liste, stufe) in listOf(gering to Blasen.Stufe.GERING, erhoeht to Blasen.Stufe.ERHOEHT, hoch to Blasen.Stufe.HOCH)) {
            assertEquals(stufe, Blasen.gesamt(liste))
            val z = Blasen.zaehlung(liste)
            val k = WidgetKartenBau.blasen(
                stufe.ordinal, z.rot, z.gesamt, Blasen.anteil(liste)!!,
                Blasen.staerkste(liste, 3).map { WidgetKartenBau.Anzeichen(it.schluessel, it.wert.toString(), it.ampel == Ampel.ROT) },
                Blasen.GRENZE_ERHOEHT, Blasen.GRENZE_HOCH, "", 1L,
            )
            val farbe = listOf(WidgetGrafik.GRUEN, WidgetGrafik.GELB, WidgetGrafik.ROT)[stufe.ordinal]
            assertEquals(farbe, k.hauptTon)
            assertTrue(k.haupt.isNotBlank())
            assertEquals(WidgetGrafik.TACHO, k.grafik!!.art)
            assertEquals(3, k.grafik!!.ton.size)
            assertEquals(1.0, k.grafik!!.werte.sum(), 1e-9)
            assertEquals(Blasen.anteil(liste)!!, k.grafik!!.marke, 1e-9)
            assertEquals("maerkte/ausblick/blasen", k.ziel)
        }
    }

    @Test fun blasenAnteilUndZeigerLiegenInDerStufe() {
        // Der Zeiger steht im Abschnitt der Stufe: Anteil gegen die Grenzen
        val hoch = listOf(ind(Blasen.CAPE, 38.0, Ampel.ROT), ind(Blasen.KGV, 30.0, Ampel.ROT), ind(Blasen.TREND, 20.0, Ampel.ROT))
        assertTrue(Blasen.anteil(hoch)!! >= Blasen.GRENZE_HOCH)
        assertNull(Blasen.anteil(hoch.take(2)))
        assertNull(Blasen.gesamt(hoch.take(2)))
    }

    @Test fun staerksteAnzeichenRotVorGelbNachAbstand() {
        val liste = listOf(
            ind(Blasen.CAPE, 33.0, Ampel.ROT), ind(Blasen.KGV, 22.0, Ampel.GELB), ind(Blasen.BUFFETT, 200.0, Ampel.ROT),
            ind(Blasen.TREND, 3.0, Ampel.GRUEN), ind(Blasen.MARGIN, 50.0, Ampel.ROT), ind(Blasen.PRAEMIE, 3.0, Ampel.GRUEN),
        )
        val st = Blasen.staerkste(liste, 3).map { it.schluessel }
        // Buffett 200/150 = 1,33, Margin 50/40 = 1,25, CAPE 33/30 = 1,1
        assertEquals(listOf(Blasen.BUFFETT, Blasen.MARGIN, Blasen.CAPE), st)
        // Weniger als drei rote: Gelb fuellt auf
        val zwei = listOf(ind(Blasen.CAPE, 33.0, Ampel.ROT), ind(Blasen.KGV, 22.0, Ampel.GELB), ind(Blasen.TREND, 3.0, Ampel.GRUEN))
        assertEquals(listOf(Blasen.CAPE, Blasen.KGV), Blasen.staerkste(zwei, 3).map { it.schluessel })
        // Weder rot noch gelb: nichts
        assertTrue(Blasen.staerkste(listOf(ind(Blasen.CAPE, 15.0, Ampel.GRUEN), ind(Blasen.KGV, 14.0, Ampel.GRUEN), ind(Blasen.TREND, 1.0, Ampel.GRUEN)), 3).isEmpty())
    }

    // ------------------------------------------------------------ Termine

    @Test fun termineNaechsteEigeneZuerstBeiGleichemTag() {
        val l = listOf(
            WidgetKartenBau.Termin("F", "6", "", false, 106), WidgetKartenBau.Termin("A", "1", "", false, 101),
            WidgetKartenBau.Termin("B", "1", "", true, 101), WidgetKartenBau.Termin("C", "2", "", false, 102),
            WidgetKartenBau.Termin("D", "3", "", false, 103), WidgetKartenBau.Termin("E", "5", "", true, 105),
        )
        val k = WidgetKartenBau.termine(WidgetVorgabe.TERMIN_DIVIDENDEN, l, "", 1L)
        assertEquals(listOf("B", "A", "C", "D", "E", "F"), k.zeilen.map { it.text })
        assertEquals(true, k.zeilen[0].hoch)
        assertNull(k.zeilen[1].hoch)
        assertEquals("discover/dividends", k.ziel)
        assertEquals("discover/ipos", WidgetKartenBau.termine(WidgetVorgabe.TERMIN_IPO, emptyList(), "", 1L).ziel)
        assertEquals("discover/economy", WidgetKartenBau.termine(WidgetVorgabe.TERMIN_WIRTSCHAFT, emptyList(), "", 1L).ziel)
        assertEquals("discover/earnings", WidgetKartenBau.termine(WidgetVorgabe.TERMIN_QUARTALE, emptyList(), "", 1L).ziel)
    }

    // ------------------------------------------------------------ Zuschnitt mit Hauptaussage und Grafik

    private fun blasenKarte() = WidgetKartenBau.blasen(
        1, 4, 12, 0.5,
        listOf(WidgetKartenBau.Anzeichen("Shiller-KGV", "38,2", true), WidgetKartenBau.Anzeichen("Buffett-Indikator", "189 %", true), WidgetKartenBau.Anzeichen("Konzentration", "36 %", true)),
        Blasen.GRENZE_ERHOEHT, Blasen.GRENZE_HOCH, "Stand 12:30", 1L,
    )

    @Test fun zuschnittGrossMitHauptGrafikUndDreiZeilen() {
        val z = de.troetenrepublik.app.data.WidgetKarten.zuschneiden(blasenKarte(), 222, 290)
        assertTrue(z.haupt)
        assertTrue(z.grafikHoehe > 0)
        assertEquals(3, z.zeilen.size)
    }

    @Test fun zuschnittKleinZeigtHauptohneGrafikWennNichtsMehrPasst() {
        val z = de.troetenrepublik.app.data.WidgetKarten.zuschneiden(blasenKarte(), 146, 146)
        assertTrue(z.haupt)
        assertEquals(0, z.grafikHoehe)
        // Nichts wird abgeschnitten: was gezeigt wird, passt
        assertTrue(z.zeilen.size <= 1)
        assertFalse(z.hinweis.isNotBlank() && z.haupt)
    }

    @Test fun zuschnittOhneHauptWennSieNichtPasst() {
        val k = blasenKarte().copy(haupt = "Ein sehr langes Wort wie Hauptaussagenzusammenfassung")
        val z = de.troetenrepublik.app.data.WidgetKarten.zuschneiden(k, 110, 200)
        assertFalse(z.haupt)
        assertEquals(0, z.grafikHoehe)
    }

    @Test fun ordersVariantenZeigenNurIhreArt() {
        val o = listOf(WidgetKartenBau.Auftrag("SAP", "SAP", "Kauf", "230"))
        val p = listOf(WidgetKartenBau.Plan("MSCI", "100", 5L, "01.12."))
        assertEquals(2, WidgetKartenBau.orders(o, p, "", 0L).zeilen.size)
        assertEquals(1, WidgetKartenBau.orders(o, p, "", 0L, WidgetVorgabe.ORDERS_ORDERS).zeilen.size)
        assertEquals("MSCI", WidgetKartenBau.orders(o, p, "", 0L, WidgetVorgabe.ORDERS_PLAENE).zeilen.single().text)
        assertEquals("plans", WidgetKartenBau.orders(o, p, "", 0L, WidgetVorgabe.ORDERS_PLAENE).ziel)
    }

    @Test fun umschichtenUndCrashtestAufbau() {
        val u = WidgetKartenBau.umschichten(listOf(WidgetKartenBau.Abweichung("Aktien", 70.0, 60.0), WidgetKartenBau.Abweichung("Anleihen", 20.0, 30.0)), 1L)
        // zu viel Aktien: Minus und rot; zu wenig Anleihen: Plus und gruen
        assertEquals(false, u.zeilen.first { it.text == "Aktien" }.hoch)
        assertEquals(true, u.zeilen.first { it.text == "Anleihen" }.hoch)
        val c = WidgetKartenBau.crashtest(listOf(WidgetKartenBau.Krise("A", -0.2), WidgetKartenBau.Krise("B", -0.4)), 1L)
        assertEquals(WidgetGrafik.ROT, c.hauptTon)
        assertEquals(1, c.zeilen.size)
        assertEquals("A", c.zeilen[0].text)
    }
}
