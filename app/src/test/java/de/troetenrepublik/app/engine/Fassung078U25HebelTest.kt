package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.HebelGrenzeTexte
import de.troetenrepublik.app.ui.PortfolioTool
import de.troetenrepublik.app.ui.StockTool
import de.troetenrepublik.app.ui.WERKZEUG_GRUPPEN
import de.troetenrepublik.app.ui.toolsFor
import de.troetenrepublik.app.ui.werkzeugFormen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// Hebel-Simulator: Rechnung je Tag, Zerlegung, Totalverlust, Kurve je Hebel, Zinsreihe, Texte und Platz in den Listen.
class Fassung078U25HebelTest {
    private val h = HebelSimulator
    private val tag = 86_400_000L
    private val start = 19_000L * tag

    // Kurse an aufeinanderfolgenden Kalendertagen
    private fun reihe(kurse: List<Double>) = kurse.mapIndexed { i, k -> (start + i * tag) to k }

    private fun ohneAlles(zins: Double = 0.0) = HebelSimulator.Annahmen(aufschlag = 0.0, kosten = 0.0, zinsFest = zins)

    // 400 Tage mit festem Wechsel aus Anstieg und Rueckgang, im Ganzen steigend
    private val wechsel = reihe((0 until 400).runningFold(100.0) { k, i -> k * if (i % 2 == 0) 1.03 else 0.975 })

    @Test
    fun hebelEinsOhneKostenIstDerKurs() {
        val e = h.rechnen(wechsel, 1.0, ohneAlles(5.0))!!
        assertEquals(e.endOhne, e.endMit, 1e-9)
        assertEquals(e.renditeOhne, e.renditeMit, 1e-12)
        assertEquals(e.rueckgangOhne, e.rueckgangMit, 1e-12)
        assertNull(e.totalverlustAm)
        assertEquals(wechsel.size, e.mit.size)
        assertEquals(1.0, e.mit[0], 0.0)
    }

    @Test
    fun gleichmaessigerAnstiegMitHebel() {
        val r = 0.01
        val e = h.rechnen(reihe((0..30).map { 100.0 * (1 + r).pow(it) }), 3.0, ohneAlles())!!
        assertEquals((1 + 3 * r).pow(30), e.endMit, 1e-9)
        // Unter einem Jahr: Rendite des Zeitraums, nicht hochgerechnet
        assertFalse(e.proJahr)
        assertEquals(e.endMit - 1, e.renditeMit, 1e-12)
        // Stetiger Trend: mehr als Hebel mal Rendite, der Pfad bringt einen Gewinn
        assertTrue(e.zerlegung.pfad < 0)
    }

    @Test
    fun zerlegungGehtAuf() {
        val a = HebelSimulator.Annahmen(aufschlag = 0.5, kosten = 0.75, zinsFest = 3.0)
        for (hebel in h.HEBEL) {
            val e = h.rechnen(wechsel, hebel, a)!!
            val z = e.zerlegung
            assertTrue(e.proJahr)
            assertEquals(hebel * e.renditeOhne, z.naiv, 1e-12)
            assertEquals(e.renditeMit, z.tatsaechlich, 0.0)
            assertEquals("Hebel $hebel", z.tatsaechlich, z.naiv - z.pfad - z.finanzierung - z.kosten, 1e-9)
            assertTrue("Hebel $hebel", z.kosten > 0)
        }
        // Schwankender Kurs: Der Pfad kostet bei Hebel 3, die Finanzierung ebenfalls
        val drei = h.rechnen(wechsel, 3.0, a)!!.zerlegung
        assertTrue(drei.pfad > 0)
        assertTrue(drei.finanzierung > 0)
        // Short: Das Bargeld bringt mehr Zins, als der Aufschlag auf die geliehenen Stuecke kostet
        assertTrue(h.rechnen(wechsel, -1.0, a)!!.zerlegung.finanzierung < 0)
    }

    @Test
    fun finanzierungUndKostenJeKalendertag() {
        // Kurs unveraendert, Hebel 2, Zins 3,6 %: je Tag 3,6 % / 360 auf das Geliehene
        val flach = reihe(List(31) { 50.0 })
        val e = h.rechnen(flach, 2.0, ohneAlles(3.6))!!
        assertEquals((1 - 0.0001).pow(30), e.endMit, 1e-12)
        // Aufschlag nur auf das Geliehene: Hebel 3 leiht das Doppelte
        val mitAufschlag = h.rechnen(flach, 3.0, HebelSimulator.Annahmen(aufschlag = 1.8, kosten = 0.0, zinsFest = 0.0))!!
        assertEquals((1 - 2 * 0.018 / 360).pow(30), mitAufschlag.endMit, 1e-12)
        // Kosten auf den ganzen Wert, Tage durch 365
        val mitKosten = h.rechnen(flach, 2.0, HebelSimulator.Annahmen(aufschlag = 0.0, kosten = 0.73, zinsFest = 0.0))!!
        assertEquals((1 - 0.0073 / 365).pow(30), mitKosten.endMit, 1e-12)
        // Wochenende: drei Kalendertage zwischen zwei Kursen zaehlen dreifach
        val luecke = (0..30).map { (start + it * 3 * tag) to 50.0 }
        assertEquals((1 - 0.0003).pow(30), h.rechnen(luecke, 2.0, ohneAlles(3.6))!!.endMit, 1e-12)
        // Unter Hebel 1 bringt das Bargeld Zins
        assertTrue(h.rechnen(flach, 0.0, ohneAlles(3.6))!!.endMit > 1.0)
    }

    @Test
    fun totalverlust() {
        val kurse = List(15) { 100.0 } + listOf(50.0) + List(15) { 60.0 }
        val e = h.rechnen(reihe(kurse), 2.0, ohneAlles())!!
        assertEquals(start + 15 * tag, e.totalverlustAm)
        assertEquals(0.0, e.endMit, 0.0)
        assertEquals(-1.0, e.renditeMit, 0.0)
        assertEquals(1.0, e.rueckgangMit, 0.0)
        assertEquals(0.5, e.schlimmsterTag, 1e-12)
        // Ohne Hebel bleibt der Wert erhalten
        assertEquals(0.6, e.endOhne, 1e-12)
        // Short: ein Anstieg um die Haelfte loescht Hebel -2
        val hoch = h.rechnen(reihe(List(15) { 100.0 } + listOf(150.0) + List(15) { 150.0 }), -2.0, ohneAlles())!!
        assertNotNull(hoch.totalverlustAm)
        assertEquals(0.5, hoch.schlimmsterTag, 1e-12)

        assertEquals(0.5, h.totalverlustSchwelle(2.0)!!, 0.0)
        assertEquals(0.2, h.totalverlustSchwelle(5.0)!!, 0.0)
        assertEquals(1.0 / 3, h.totalverlustSchwelle(-3.0)!!, 1e-15)
        assertEquals(1.0, h.totalverlustSchwelle(-1.0)!!, 0.0)
        assertNull(h.totalverlustSchwelle(1.0))
        assertNull(h.totalverlustSchwelle(0.5))
    }

    @Test
    fun shortGegenDenKurs() {
        val e = h.rechnen(reihe((0..30).map { 100.0 * 1.01.pow(it) }), -1.0, ohneAlles())!!
        assertEquals(0.99.pow(30), e.endMit, 1e-12)
        assertTrue(e.renditeMit < 0 && e.renditeOhne > 0)
    }

    @Test
    fun kurveJeHebel() {
        val a = ohneAlles()
        val k = h.kurve(wechsel, a)
        // -3 bis 5 lueckenlos in Schritten von 0,25, auch zwischen -1 und 1
        assertEquals(33, k.size)
        assertEquals(-3.0, k.first().first, 0.0)
        assertEquals(5.0, k.last().first, 0.0)
        assertTrue(listOf(-0.5, 0.0, 0.5).all { x -> k.any { it.first == x } })
        assertEquals(k.size, k.map { it.first }.toSet().size)
        assertTrue(h.HEBEL.all { x -> k.any { it.first == x } })
        k.zipWithNext().forEach { (p, q) -> assertEquals(0.25, q.first - p.first, 1e-12) }
        // Hebel 1: der Kurs selbst
        val eins = h.rechnen(wechsel, 1.0, a)!!
        assertEquals(eins.renditeOhne, k.first { it.first == 1.0 }.second, 1e-12)
        // Negativer Hebel auf einen im Ganzen steigenden Kurs verliert
        assertTrue(k.first { it.first == -3.0 }.second < 0 && k.first { it.first == -1.0 }.second < 0)
        // Jeder Punkt der Kurve ist die Rechnung mit diesem Hebel
        k.forEach { (hebel, r) -> assertEquals(h.rechnen(wechsel, hebel, a)!!.renditeMit, r, 1e-12) }
        val b = h.bester(k)!!
        assertTrue(k.all { it.second <= b.second })
        // Der Pfadverlust waechst mit dem Quadrat des Hebels: Das Maximum liegt hier im Inneren
        assertTrue(b.first > 0.0 && b.first < 5.0)
        assertTrue(h.kurve(wechsel.take(5), a).isEmpty())
        assertNull(h.bester(emptyList()))
    }

    @Test
    fun zuKurzOderUnbrauchbar() {
        assertNull(h.rechnen(wechsel.take(h.MIN_PUNKTE - 1), 2.0))
        assertNotNull(h.rechnen(wechsel.take(h.MIN_PUNKTE), 2.0))
        assertNull(h.rechnen(emptyList(), 2.0))
        assertNull(h.rechnen(wechsel, Double.NaN))
        // Kurse ohne Wert fallen heraus, mehrere Kurse eines Tages zaehlen einmal
        val mitLuecken = wechsel + listOf((start + 500 * tag) to 0.0, (start + 501 * tag) to Double.NaN)
        assertEquals(h.rechnen(wechsel, 2.0)!!.endMit, h.rechnen(mitLuecken, 2.0)!!.endMit, 0.0)
        assertEquals(wechsel.size, h.kurse(wechsel + wechsel.map { it.first + 1000 to it.second }).size)
    }

    @Test
    fun zinsreihe() {
        val a = HebelSimulator.Annahmen(zinsreihe = listOf(100L to 1.0, 200L to 2.5, 300L to 0.5), zinsFest = 9.0)
        assertEquals(1.0, a.zinsAm(50), 0.0)
        assertEquals(1.0, a.zinsAm(100), 0.0)
        assertEquals(1.0, a.zinsAm(199), 0.0)
        assertEquals(2.5, a.zinsAm(200), 0.0)
        assertEquals(0.5, a.zinsAm(10_000), 0.0)
        assertEquals(9.0, HebelSimulator.Annahmen(zinsFest = 9.0).zinsAm(150), 0.0)
        // Die Reihe geht in die Rechnung ein: Der mittlere Zins ist der der Kurstage
        val reiheZins = listOf(0L to 2.0, 19_010L to 4.0)
        val e = h.rechnen(reihe(List(21) { 10.0 }), 2.0, HebelSimulator.Annahmen(aufschlag = 0.0, kosten = 0.0, zinsreihe = reiheZins))!!
        assertEquals(3.0, e.zinsMittel, 1e-12)
        assertEquals((1 - 0.02 / 360).pow(10) * (1 - 0.04 / 360).pow(10), e.endMit, 1e-12)
        assertEquals(0.5, h.AUFSCHLAG_STANDARD, 0.0)
        assertEquals(0.75, h.KOSTEN_STANDARD, 0.0)
        assertEquals(3.0, h.ZINS_ANNAHME, 0.0)
    }

    @Test
    fun zinsDatei() {
        val csv = "FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE\nD,US,2026-11-05,3.875\nD,US,2026-11-06,3.875\nD,US,2026-11-07,3.625\nD,US,2026-11-08,\nD,US,x,1\n"
        val kurz = h.zinsKuerzen(csv)!!
        val r = h.zinsLesen(kurz)
        assertEquals(2, r.size)
        assertEquals(java.time.LocalDate.of(2026, 11, 5).toEpochDay(), r[0].first)
        assertEquals(3.875, r[0].second, 0.0)
        assertEquals(java.time.LocalDate.of(2026, 11, 7).toEpochDay(), r[1].first)
        assertEquals(3.625, r[1].second, 0.0)
        assertNull(h.zinsKuerzen(""))
        assertNull(h.zinsKuerzen("FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE\n"))
        assertNull(h.zinsKuerzen("<html>Fehler</html>\nzwei"))
        assertTrue(h.zinsLesen("").isEmpty())
        assertEquals("US", h.zinsGebiet("USD"))
        assertEquals("XM", h.zinsGebiet("EUR"))
        assertEquals("GB", h.zinsGebiet("GBp"))
        assertEquals("CH", h.zinsGebiet("CHF"))
        assertNull(h.zinsGebiet("XXX"))
        val adresse = h.bisAdresse("US", 2015)
        assertTrue(adresse.startsWith("https://stats.bis.org/") && adresse.contains("/D.US?") && adresse.contains("startPeriod=2015-01-01"))
    }

    @Test
    fun tagesgeldUndVerkettung() {
        assertEquals(listOf(h.DFF), h.tagesgeld("USD"))
        assertEquals(listOf(h.EONIA, h.ESTR), h.tagesgeld("EUR"))
        assertTrue(h.tagesgeld("CHF").isEmpty())
        assertTrue(h.DFF.adresse(2015).endsWith("id=DFF&cosd=2015-01-01"))
        assertTrue(h.ESTR.adresse(2015).startsWith("https://data-api.ecb.europa.eu/") && h.ESTR.adresse(2015).contains("startPeriod=2015-01-01"))
        val leit = h.leitzins("CHF")!!
        assertEquals("bis-verlauf-CH", leit.schluessel)
        assertEquals(h.bisAdresse("CH", 2015), leit.adresse(2015))
        assertNull(h.leitzins("XXX"))
        // Jede Quelle hat eine Nennung in beiden Sprachen und eine Seite
        listOf(h.DFF, h.EONIA, h.ESTR, leit).forEach { q ->
            assertTrue(q.schluessel, HebelGrenzeTexte.text(true, q.text).isNotBlank() && HebelGrenzeTexte.text(false, q.text).isNotBlank())
            assertTrue(q.schluessel, q.seite.startsWith("https://"))
        }
        // FRED: fehlende Werte stehen als Punkt
        val fred = h.DFF.kuerzen("observation_date,DFF\n2026-11-05,3.87\n2026-11-06,.\n2026-11-07,3.87\n2026-11-08,3.62\n")!!
        assertEquals(listOf(java.time.LocalDate.of(2026, 11, 5).toEpochDay() to 3.87, java.time.LocalDate.of(2026, 11, 8).toEpochDay() to 3.62), h.zinsLesen(fred))
        assertNull(h.fredKuerzen("<html></html>"))
        val ezb = h.ESTR.kuerzen("KEY,FREQ,BENCHMARK_ITEM,DATA_TYPE_EST,TIME_PERIOD,OBS_VALUE\nEST.B.X.WT,B,X,WT,2019-10-01,-0.549\nEST.B.X.WT,B,X,WT,2019-10-02,-0.551\n")!!
        assertEquals(-0.551, h.zinsLesen(ezb).last().second, 0.0)
        // Die spaetere Reihe loest die fruehere ab ihrem ersten Tag ab
        val kette = h.verketten(listOf(listOf(10L to 1.0, 20L to 2.0, 30L to 3.0), emptyList(), listOf(20L to 9.0, 40L to 8.0)))
        assertEquals(listOf(10L to 1.0, 20L to 9.0, 40L to 8.0), kette)
        assertTrue(h.verketten(emptyList()).isEmpty())
    }

    @Test
    fun texteInBeidenSprachen() {
        val de = HebelGrenzeTexte.schluessel(true)
        assertEquals(de, HebelGrenzeTexte.schluessel(false))
        val platz = Regex("\\{\\d}")
        de.forEach { k ->
            val d = HebelGrenzeTexte.text(true, k)
            val e = HebelGrenzeTexte.text(false, k)
            assertTrue(k, d.isNotBlank() && e.isNotBlank())
            assertEquals(k, platz.findAll(d).map { it.value }.toSet(), platz.findAll(e).map { it.value }.toSet())
            // Keine Gedankenstriche, keine Fragen
            listOf(d, e).forEach { t -> assertFalse(k, t.contains('–') || t.contains('—') || t.contains('?') || t.contains(" - ")) }
        }
        assertEquals("Aus 1.000 € mit Hebel 2", HebelGrenzeTexte.text(true, "h.kern.titel", listOf("1.000 €", "2")))
        assertEquals("", HebelGrenzeTexte.text(true, "gibt.es.nicht"))
        // Kennzeichnung als Modell und Rueckblick
        assertTrue(HebelGrenzeTexte.text(true, "h.hinweis").contains("historisch und hypothetisch"))
        assertTrue(HebelGrenzeTexte.text(false, "h.hinweis").contains("historical and hypothetical"))
        assertTrue(HebelGrenzeTexte.text(true, "h.kurve.satz").contains("Rückblick"))
        assertTrue(HebelGrenzeTexte.text(true, "h.aufschlag").contains("Annahme") && HebelGrenzeTexte.text(true, "h.kosten").contains("Annahme"))
    }

    @Test
    fun platzInDenListen() {
        val name = PortfolioTool.HEBEL.name
        assertEquals(1, WERKZEUG_GRUPPEN.count { name in it.second })
        assertTrue(werkzeugFormen(name).isNotEmpty())
        listOf(De, En).forEach { s ->
            assertTrue(s.ptTitle(name) != name && s.ptSubtitle(name).isNotBlank())
            assertFalse(s.ptSubtitle(name).contains('?'))
            assertTrue(s.toolTitle(StockTool.HEBEL.name) != StockTool.HEBEL.name && s.toolSubtitle(StockTool.HEBEL.name).isNotBlank())
        }
        // Auf der Wertpapierseite bei jedem Wert, nach den Werkzeugen zur eigenen Position
        AssetType.entries.forEach { assertEquals(StockTool.HEBEL, toolsFor(it, false, null).last()) }
        assertEquals(StockTool.SELL_TAX, toolsFor(AssetType.STOCK, true, null).first())
    }
}
