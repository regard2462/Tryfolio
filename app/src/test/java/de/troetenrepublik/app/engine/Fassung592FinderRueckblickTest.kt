package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.FinderRueckblick.Abschluss
import de.troetenrepublik.app.engine.FinderRueckblick.Feld
import de.troetenrepublik.app.engine.FinderRueckblick.Wert
import de.troetenrepublik.app.engine.FinderRueckblickMessung.Stichtag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Fassung 5.92: Rueckblick-Test des Aktienfinders. Stichtag-Logik (keine Zahl nach dem Stichtag), Rendite und
// Rang-Korrelation, Walk-forward und das Lesen der SEC-Bulkdatei (Mitglieder zum Stichtag: Fassung078H1).
class Fassung592FinderRueckblickTest {

    private val tagMs = FinderRueckblick.TAG_MS
    private fun t(d: String) = LocalDate.parse(d).toEpochDay()

    // Taegliche Kurse (auch am Wochenende, reicht fuer die Rechnung) von a bis b, Preis aus dem Tag.
    private fun kurse(von: String, bis: String, preis: (Long) -> Double): List<Candle> =
        (t(von)..t(bis)).map { d -> preis(d).let { Candle(d * tagMs + 14 * 3_600_000L, it, it, it, it) } }

    @Test fun abschlussNurMitBekanntenFeldern() {
        val a = listOf(
            Abschluss("2020-12-31", mapOf(Feld.NETTOGEWINN to Wert(t("2021-02-20"), 100.0), Feld.EPS to Wert(t("2021-02-20"), 2.0))),
            Abschluss("2021-12-31", mapOf(Feld.NETTOGEWINN to Wert(t("2022-02-20"), 120.0), Feld.EPS to Wert(t("2022-02-20"), 2.4),
                Feld.EIGENKAPITAL to Wert(t("2022-05-01"), 900.0))),
        )
        // Vor der Einreichung des zweiten Jahres zaehlt nur das erste
        val vorher = FinderRueckblick.standZum(a, t("2022-02-19"))!!
        assertEquals(listOf("2020-12-31"), vorher.enden)
        assertEquals(2.0, vorher.letztes[Feld.EPS]!!, 1e-9)
        // Danach das zweite, aber das Eigenkapital erst ab Mai
        val maerz = FinderRueckblick.standZum(a, t("2022-03-01"))!!
        assertEquals(2.4, maerz.letztes[Feld.EPS]!!, 1e-9)
        assertNull(maerz.letztes[Feld.EIGENKAPITAL])
        assertEquals(900.0, FinderRueckblick.standZum(a, t("2022-05-01"))!!.letztes[Feld.EIGENKAPITAL]!!, 1e-9)
        // Vor jeder Einreichung nichts, und zu alte Abschluesse gelten nicht mehr
        assertNull(FinderRueckblick.standZum(a, t("2021-01-15")))
        assertNull(FinderRueckblick.standZum(a, t("2024-01-01")))
    }

    @Test fun yahooGiltNeunzigTageNachPeriodenende() {
        val text = """{"timeseries":{"result":[
            {"meta":{"type":["annualDilutedEPS"]},"annualDilutedEPS":[{"asOfDate":"2023-09-30","reportedValue":{"raw":6.13}}]},
            {"meta":{"type":["annualNetIncome"]},"annualNetIncome":[{"asOfDate":"2023-09-30","reportedValue":{"raw":97.0}},null]}
        ]}}"""
        val a = FinderRueckblick.ausYahoo(text).single()
        assertEquals("2023-09-30", a.ende)
        assertEquals(t("2023-09-30") + 90, a.felder.getValue(Feld.EPS).bekannt)
        assertNull(FinderRueckblick.standZum(listOf(a), t("2023-12-28")))
        assertNotNull(FinderRueckblick.standZum(listOf(a), t("2023-12-29")))
    }

    @Test fun keineZahlNachDemStichtag() {
        val stichtag = t("2020-06-30")
        val ruhig = kurse("2018-01-01", "2020-06-30") { 100.0 + (it - t("2018-01-01")) * 0.05 }
        // Gleiche Vergangenheit, danach ein Kurssprung, eine spaete Hochstufung und ein spaeter Abschluss
        val zukunft = ruhig + kurse("2020-07-01", "2021-06-30") { 500.0 }
        val urteil = Analystenbilanz.Urteil("Haus A", t("2020-05-01") * tagMs, "Buy", "Hold", "up", 150.0)
        val spaet = Analystenbilanz.Urteil("Haus B", t("2020-07-02") * tagMs, "Buy", "Sell", "up", 900.0)
        val abschluss = Abschluss("2020-03-31", mapOf(Feld.NETTOGEWINN to Wert(t("2020-08-15"), 10.0), Feld.EPS to Wert(t("2020-08-15"), 5.0)))
        val ohne = FinderRueckblick.Eingaben("X", "X", "BAU", ruhig, urteile = listOf(urteil))
        val mit = FinderRueckblick.Eingaben("X", "X", "BAU", zukunft, listOf(abschluss), listOf(urteil, spaet))
        val a = FinderRueckblick.datenZum(ohne, stichtag, null)!!
        val b = FinderRueckblick.datenZum(mit, stichtag, null)!!
        assertEquals(a, b)
        assertNull(b.kgv)
        // Eine Hochstufung in den letzten 90 Tagen: Note ein Viertel besser als 2,5
        assertEquals(2.25, b.note!!, 1e-9)
        assertNotNull(b.jahr)
        assertNotNull(b.linie200)
        // Nach der Einreichung zaehlt der Abschluss: KGV aus Kurs und Gewinn je Aktie
        val spaeter = FinderRueckblick.datenZum(mit, t("2020-08-20"), null)!!
        assertEquals(100.0, spaeter.kgv!!, 1e-9)
        // Ohne Kurs am Stichtag keine Daten
        assertNull(FinderRueckblick.datenZum(ohne, t("2020-07-20"), null))
    }

    @Test fun ersatzNoteAusNettoHochstufungen() {
        assertEquals(2.5, FinderRueckblick.noteErsatz(0), 1e-9)
        assertEquals(2.0, FinderRueckblick.noteErsatz(2), 1e-9)
        assertEquals(3.5, FinderRueckblick.noteErsatz(-4), 1e-9)
        assertEquals(1.0, FinderRueckblick.noteErsatz(20), 1e-9)
    }

    @Test fun renditeBisZumHorizont() {
        val k = kurse("2020-01-01", "2021-03-31") { if (it < t("2020-04-30")) 100.0 else 110.0 }
        assertEquals(0.10, FinderRueckblick.rendite(k, t("2020-01-31"), 3, t("2021-03-31"))!!, 1e-9)
        assertEquals(0.0, FinderRueckblick.rendite(k, t("2020-01-31"), 1, t("2021-03-31"))!!, 1e-9)
        // Horizont nach dem letzten Kurstag: offen
        assertNull(FinderRueckblick.rendite(k, t("2021-01-31"), 3, t("2021-03-31")))
        // Wert nicht mehr gehandelt: letzter Kurs gilt
        val kurz = kurse("2020-01-01", "2020-03-15") { if (it < t("2020-03-01")) 100.0 else 80.0 }
        assertEquals(-0.2, FinderRueckblick.rendite(kurz, t("2020-01-31"), 6, t("2021-03-31"))!!, 1e-9)
    }

    @Test fun splitsBringenWerteAufDenHeutigenStand() {
        val s = listOf(Splits.Split(t("2020-08-31") * tagMs, 4.0, 1.0))
        assertEquals(4.0, FinderRueckblick.splitFaktor(s, t("2020-01-01")), 1e-9)
        assertEquals(1.0, FinderRueckblick.splitFaktor(s, t("2020-09-01")), 1e-9)
    }

    @Test fun spearmanUndRaenge() {
        val a = (1..12).map { it.toDouble() }
        assertEquals(1.0, FinderRueckblickMessung.spearman(a, a.map { it * it })!!, 1e-9)
        assertEquals(-1.0, FinderRueckblickMessung.spearman(a, a.map { -it })!!, 1e-9)
        assertEquals(listOf(1.0, 2.5, 2.5, 4.0), FinderRueckblickMessung.raenge(listOf(1.0, 5.0, 5.0, 9.0)).toList())
        assertNull(FinderRueckblickMessung.spearman(a.take(5), a.take(5)))
        // Ohne Ueberlappung entspricht der Fehler dem einfachen Standardfehler (Varianz durch n)
        val (m, se) = FinderRueckblickMessung.mittelUndFehler(listOf(1.0, 3.0), 0)!!
        assertEquals(2.0, m, 1e-9)
        assertEquals(Math.sqrt(1.0 / 2), se, 1e-9)
    }

    // Stichtag mit n Werten: Trend-Punkte i, Rendite nach f(i).
    private fun stichtag(tag: Long, n: Int = 40, msci: Double = 0.0, rendite: (Int) -> Double): Stichtag {
        val werte = (0 until n).map { i ->
            FinderRueckblickMessung.Wert("S$i", mapOf(Bereich.TREND to i.toDouble(), Bereich.ANALYSTEN to (n - i).toDouble()),
                listOf(1, 3, 6, 12).associateWith { rendite(i) })
        }
        val m = listOf(1, 3, 6, 12).associateWith { msci }
        return Stichtag(tag, werte, m, m, n)
    }

    @Test fun topRenditeIcUndSpanne() {
        val g = mapOf(Bereich.TREND to 1.0)
        val s = stichtag(t("2015-01-30")) { it / 100.0 }
        // Top 20 sind S20 bis S39: Schnitt 0,295
        assertEquals(0.295, FinderRueckblickMessung.topRendite(s, g, 20, 6)!!, 1e-9)
        assertEquals(1.0, FinderRueckblickMessung.ic(s, g, 6)!!, 1e-9)
        assertEquals(-1.0, FinderRueckblickMessung.bereichIc(s, Bereich.ANALYSTEN, 6)!!, 1e-9)
        assertNull(FinderRueckblickMessung.bereichIc(s, Bereich.BEWERTUNG, 6))
        // Oberes Fuenftel S32..S39 minus unteres S0..S7
        assertEquals(0.32, FinderRueckblickMessung.spanne(s, g, 6)!!, 1e-9)
        val h = FinderRueckblickMessung.horizont(listOf(s, stichtag(t("2015-02-27")) { it / 100.0 }), g, 6)!!
        assertEquals(1.0, h.treffer, 1e-9)
        assertEquals(1.0, h.ic, 1e-9)
    }

    @Test fun walkForwardLerntNurAusDerVergangenheit() {
        val standard = FinderRueckblick.testGewichte()
        // Bis 2018 sagt der Trend die Rendite voraus, ab 2019 die Analysten
        val st = (0 until 120).map { m ->
            val tag = LocalDate.of(2012, 1, 31).plusMonths(m.toLong()).toEpochDay()
            val trend = LocalDate.ofEpochDay(tag).year < 2019
            stichtag(tag) { i -> if (trend) i / 100.0 else (40 - i) / 100.0 }
        }
        // Gelernt fuer 2019: der Trend zaehlt mehr, die Analysten weniger als im Standard (Juni 2018 ist der letzte
        // Stichtag, dessen sechs Monate vor 2019 endeten)
        val l = FinderRueckblickMessung.walkForward(st, standard)!!
        val g2019 = l.jeJahr.getValue(2019)
        assertTrue(g2019.getValue("TREND") > standard.getValue(Bereich.TREND))
        assertTrue((g2019["ANALYSTEN"] ?: 0.0) < standard.getValue(Bereich.ANALYSTEN))
        // Summe der geprueften Gewichte bleibt
        assertEquals(FinderRueckblick.IM_TEST.sumOf { standard.getValue(it) }, g2019.values.sum(), 1e-9)
        // Im Pruefjahr 2019 liegen die gelernten Gewichte daneben: kein klarer Vorteil
        assertFalse(l.klarBesser)
        // Ohne Daten zu einem Bereich bleibt sein Standardgewicht
        val gelernt = FinderRueckblickMessung.gelernt(emptyList(), standard)
        assertEquals(standard.getValue(Bereich.BEWERTUNG), gelernt.getValue(Bereich.BEWERTUNG), 1e-9)
    }

    @Test fun monatlichesUmschichtenMitKosten() {
        val st = (0 until 13).map { m -> stichtag(LocalDate.of(2015, 1, 31).plusMonths(m.toLong()).toEpochDay(), msci = 0.01) { 0.02 } }
        val g = mapOf(Bereich.TREND to 1.0)
        val m = FinderRueckblickMessung.monate(st, g)
        // Immer dieselben 20: nur im ersten Monat Kosten (halber Spread)
        assertEquals(0.0, m[1].umschlag, 1e-9)
        assertEquals(1.02 * (1 - FinderRueckblickMessung.SPREAD_BPS / 20_000.0) - 1, m[0].netto, 1e-12)
        assertEquals(0.02, m[1].netto, 1e-12)
        val s = FinderRueckblickMessung.strategie(m)!!
        assertEquals(0.0, s.rueckstand, 1e-9)
        val d = FinderRueckblickMessung.zusammenfassen(st, g, "2026-10-28", false)
        assertEquals(d, FinderRueckblickMessung.lesen(FinderRueckblickMessung.schreiben(d)))
    }

    // Kleine companyfacts-Datei im Aufbau der SEC (Werte erfunden).
    private val fakten = """{"cik":1234,"entityName":"Beispiel Werke Inc.","facts":{
        "dei":{"EntityCommonStockSharesOutstanding":{"units":{"shares":[
            {"end":"2020-02-01","val":100,"accn":"a1","fy":2019,"fp":"FY","form":"10-K","filed":"2020-02-20"}]}}},
        "us-gaap":{
        "NetIncomeLoss":{"units":{"USD":[
            {"start":"2019-01-01","end":"2019-12-31","val":50,"accn":"a1","fy":2019,"fp":"FY","form":"10-K","filed":"2020-02-20"},
            {"start":"2019-10-01","end":"2019-12-31","val":15,"accn":"a1","fy":2019,"fp":"FY","form":"10-K","filed":"2020-02-20"},
            {"start":"2019-01-01","end":"2019-12-31","val":45,"accn":"a2","fy":2020,"fp":"FY","form":"10-K","filed":"2021-02-20"},
            {"start":"2020-01-01","end":"2020-12-31","val":60,"accn":"a2","fy":2020,"fp":"FY","form":"10-K","filed":"2021-02-20"},
            {"start":"2020-01-01","end":"2020-03-31","val":12,"accn":"q1","fy":2020,"fp":"Q1","form":"10-Q","filed":"2020-05-01"}]}},
        "EarningsPerShareDiluted":{"units":{"USD/shares":[
            {"start":"2019-01-01","end":"2019-12-31","val":0.5,"accn":"a1","fy":2019,"fp":"FY","form":"10-K","filed":"2020-02-20"},
            {"start":"2020-01-01","end":"2020-12-31","val":0.15,"accn":"a2","fy":2020,"fp":"FY","form":"10-K","filed":"2021-02-20"}]}},
        "StockholdersEquity":{"units":{"USD":[
            {"end":"2019-12-31","val":500,"accn":"a1","fy":2019,"fp":"FY","form":"10-K","filed":"2020-02-20"}]}}
        }}}"""

    @Test fun faktenMitEinreichungstagUndSplits() {
        // Split 4:1 im Sommer 2020: Werte von davor werden auf den heutigen Stand gebracht
        val splits = listOf(Splits.Split(t("2020-08-31") * tagMs, 4.0, 1.0))
        val (name, l) = FinderRueckblick.ausFakten(fakten, splits)!!
        assertEquals("Beispiel Werke Inc.", name)
        assertEquals(listOf("2019-12-31", "2020-12-31"), l.map { it.ende })
        val j2019 = l[0].felder
        // Zuerst eingereichter Wert (50, nicht die spaetere Korrektur 45), Quartal zaehlt nicht
        assertEquals(50.0, j2019.getValue(Feld.NETTOGEWINN).wert, 1e-9)
        assertEquals(t("2020-02-20"), j2019.getValue(Feld.NETTOGEWINN).bekannt)
        assertEquals(0.125, j2019.getValue(Feld.EPS).wert, 1e-9)
        // Aktienzahl vom Deckblatt desselben Berichts, mal 4
        assertEquals(400.0, j2019.getValue(Feld.AKTIEN).wert, 1e-9)
        assertEquals(0.15, l[1].felder.getValue(Feld.EPS).wert, 1e-9)
        // Zum Stichtag Ende 2020 gilt nur 2019
        assertEquals(listOf("2019-12-31"), FinderRueckblick.standZum(l, t("2020-12-31"))!!.enden)
    }

    @Test fun zipLesen() {
        val datei = File.createTempFile("companyfacts", ".zip")
        try {
            ZipOutputStream(datei.outputStream()).use { z ->
                z.putNextEntry(ZipEntry("CIK0000001234.json")); z.write(fakten.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("CIK0000009999.json")); z.write("""{"cik":9999,"entityName":"Andere Firma","facts":{}}""".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("liesmich.txt")); z.write("x".toByteArray()); z.closeEntry()
            }
            val gefragt = ArrayList<Pair<Long, String>>()
            val m = FinderRueckblick.ausZip(datei, { cik, name -> gefragt += cik to name; if (cik == 1234L) "BSP" else null })
            assertEquals(setOf("BSP"), m.keys)
            assertEquals(2, m.getValue("BSP").size)
            assertEquals(listOf(1234L to "Beispiel Werke Inc.", 9999L to "Andere Firma"), gefragt)
        } finally {
            datei.delete()
        }
    }

    @Test fun gesamtWieDieEngine() {
        val d = listOf(Aktienfinder.Daten("A", "A", jahr = 10.0, linie200 = 5.0, note = 2.0), Aktienfinder.Daten("B", "B", jahr = -5.0, linie200 = -3.0))
        val g = FinderRueckblick.testGewichte()
        Aktienfinder.bewerten(d, g).forEach { e -> assertEquals(e.gesamt, Aktienfinder.gesamt(e.bereiche, g), 1e-9) }
        assertEquals(0.0, g.getValue(Bereich.INSIDER), 0.0)
    }
}
