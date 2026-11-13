package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.PortfolioBacktest as pb
import de.troetenrepublik.app.i18n.BacktestTexte
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.market.Strategien
import de.troetenrepublik.app.ui.BtEingabe
import de.troetenrepublik.app.ui.BtMischung
import de.troetenrepublik.app.ui.BtPosten
import de.troetenrepublik.app.ui.BtReihe
import de.troetenrepublik.app.ui.PortfolioTool
import de.troetenrepublik.app.ui.WERKZEUG_GRUPPEN
import de.troetenrepublik.app.ui.btRaster
import de.troetenrepublik.app.ui.btRechnen
import de.troetenrepublik.app.ui.werkzeugFormen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.pow

// Portfolio-Backtest: Rechner mit Umschichtregeln, Kosten und Zahlungen, Rendite je Haltedauer, Rangliste der
// Musterdepots, Texte und Platz in der Werkzeugliste.
class Fassung078U23BacktestTest {
    private val tag = pb.TAG

    private fun ms(iso: String) = LocalDate.parse(iso).toEpochDay() * tag

    // Ein Kurs je Kalendertag von von bis bis (einschliesslich); kurs bekommt die Zahl der Tage seit von.
    private fun reihe(von: String, bis: String, kurs: (Int) -> Double): List<Pair<Long, Double>> {
        val a = LocalDate.parse(von).toEpochDay()
        val b = LocalDate.parse(bis).toEpochDay()
        return (a..b).map { d -> d * tag to kurs((d - a).toInt()) }
    }

    private fun wachstum(proJahr: Double): (Int) -> Double = { i -> 100.0 * (1 + proJahr).pow(i / 365.25) }

    @Test
    fun kalenderStimmtMitDerLaufzeitUeberein() {
        var d = LocalDate.of(1899, 12, 25).toEpochDay()
        val ende = LocalDate.of(2101, 1, 5).toEpochDay()
        while (d <= ende) {
            val l = LocalDate.ofEpochDay(d)
            assertEquals(Triple(l.year, l.monthValue, l.dayOfMonth), pb.datum(d))
            assertEquals(d, pb.tagVon(l.year, l.monthValue, l.dayOfMonth))
            assertEquals(l.lengthOfMonth(), pb.monatsLaenge(l.year, l.monthValue))
            d += 3
        }
        val start = LocalDate.of(2024, 1, 31).toEpochDay()
        assertEquals(0, pb.monateSeit(start, LocalDate.of(2024, 2, 28).toEpochDay()))
        // Im kuerzeren Monat ist der Monat am letzten Tag voll
        assertEquals(1, pb.monateSeit(start, LocalDate.of(2024, 2, 29).toEpochDay()))
        assertEquals(12, pb.monateSeit(start, LocalDate.of(2025, 1, 31).toEpochDay()))
        assertEquals(11, pb.monateSeit(start, LocalDate.of(2025, 1, 30).toEpochDay()))
    }

    @Test
    fun einWertOhneKostenFolgtDemKurs() {
        val a = reihe("2020-01-01", "2023-01-01", wachstum(0.10))
        val e = pb.rechnen(pb.Eingabe(listOf(a), listOf(1.0), pb.Regel(pb.Takt.JAEHRLICH)))!!
        assertEquals(a.size, e.verlauf.size)
        assertEquals(10_000.0 * a.last().second / a.first().second, e.endwert, 1e-6)
        assertEquals(a.last().second / a.first().second, e.index.last().second, 1e-9)
        assertEquals(1.0, e.index.first().second, 0.0)
        assertEquals(0, e.umschichtungen)
        assertEquals(0.0, e.kosten, 0.0)
        val k = pb.kennzahlen(e)
        assertEquals(0.10, k.zeitgewichtet!!, 1e-4)
        assertEquals(0.10, k.geldgewichtet!!, 1e-3)
        assertEquals(0.0, k.rueckgang!!.tiefe, 0.0)
        assertEquals(10_000.0, k.eingezahlt, 0.0)
    }

    @Test
    fun umschichtenNachTaktUndBand() {
        val a = reihe("2020-01-01", "2023-01-01") { i -> 100.0 + i * 0.1 }
        val b = reihe("2020-01-01", "2023-01-01") { 50.0 }
        fun lauf(r: pb.Regel) = pb.rechnen(pb.Eingabe(listOf(a, b), listOf(1.0, 1.0), r))!!
        val nie = lauf(pb.Regel(pb.Takt.NIE))
        assertEquals(0, nie.umschichtungen)
        // Ohne Umschichten: die Haelfte waechst mit A, die andere bleibt
        assertEquals(5_000.0 * a.last().second / 100.0 + 5_000.0, nie.endwert, 1e-6)
        assertEquals(3, lauf(pb.Regel(pb.Takt.JAEHRLICH)).umschichtungen)
        assertEquals(6, lauf(pb.Regel(pb.Takt.HALBJAEHRLICH)).umschichtungen)
        assertEquals(12, lauf(pb.Regel(pb.Takt.QUARTAL)).umschichtungen)
        assertEquals(36, lauf(pb.Regel(pb.Takt.MONATLICH)).umschichtungen)
        val jahr = lauf(pb.Regel(pb.Takt.JAEHRLICH))
        // Umschichten nimmt dem steigenden Wert Gewicht: weniger Endwert als ohne
        assertTrue(jahr.endwert < nie.endwert)
        // Jede Umschichtung verkauft A und kauft B im selben Betrag
        jahr.geschaefte.filter { it.umschichtung }.groupBy { it.t }.values.forEach { g ->
            assertEquals(2, g.size)
            assertEquals(0.0, g.sumOf { it.betrag }, 1e-6)
            assertTrue(g.first { it.wert == 0 }.betrag < 0)
        }
        // Band von 5 Punkten: A muss von 50 auf ueber 55 % wachsen
        val band = lauf(pb.Regel(pb.Takt.NIE, 0.05))
        assertTrue(band.umschichtungen in 1..3)
        val erste = band.geschaefte.first { it.umschichtung }
        val kursA = a.first { it.first == erste.t }.second
        assertTrue(kursA / 100.0 / (kursA / 100.0 + 1.0) > 0.55)
        // Ein weites Band loest nie aus
        assertEquals(0, lauf(pb.Regel(pb.Takt.JAEHRLICH, 0.40)).umschichtungen)
        // Kleine Bausteine haben ein engeres Band, wie die feste Schwelle bei 10 Punkten
        assertEquals(Umschichtschwelle.schwelle(0.10), pb.erlaubt(0.10, Umschichtschwelle.PUNKTE), 1e-12)
        assertEquals(Umschichtschwelle.schwelle(0.50), pb.erlaubt(0.50, Umschichtschwelle.PUNKTE), 1e-12)
        assertEquals(0.02, pb.erlaubt(0.50, 0.02), 1e-12)
    }

    @Test
    fun kostenGehenVomWertUndVomIndexAb() {
        val flach = reihe("2022-01-01", "2023-01-01") { 20.0 }
        val e = pb.rechnen(pb.Eingabe(listOf(flach, flach), listOf(50.0, 50.0), pb.Regel(pb.Takt.NIE), kosten = pb.Kosten(20.0, 1.0)))!!
        // Je Kauf 0,1 % halber Spread und 1 Euro
        assertEquals(12.0, e.kosten, 1e-9)
        assertEquals(9_988.0, e.endwert, 1e-6)
        assertEquals(1.0, e.index.first().second, 0.0)
        assertEquals(0.9988, e.index.last().second, 1e-9)
        assertEquals(2, e.geschaefte.size)
    }

    @Test
    fun sparrateUndEntnahme() {
        val von = ms("2020-01-31")
        val bis = ms("2021-01-31")
        val raten = pb.sparrate(von, bis, 100.0)
        assertEquals(12, raten.size)
        // Im Februar am letzten Tag, danach wieder am 31. oder Monatsende
        assertEquals(ms("2020-02-29"), raten[0].t)
        assertEquals(ms("2020-03-31"), raten[1].t)
        assertEquals(ms("2020-04-30"), raten[2].t)
        assertEquals(ms("2021-01-31"), raten.last().t)
        assertTrue(pb.sparrate(von, bis, 0.0).isEmpty())

        val flach = reihe("2020-01-31", "2021-01-31") { 10.0 }
        val e = pb.rechnen(pb.Eingabe(listOf(flach), listOf(1.0), pb.Regel(pb.Takt.NIE), start = 1_000.0, zahlungen = raten))!!
        assertEquals(2_200.0, e.endwert, 1e-6)
        assertEquals(2_200.0, e.eingezahlt, 1e-9)
        // Einzahlungen veraendern den Index nicht
        assertEquals(1.0, e.index.last().second, 1e-12)
        assertEquals(13, e.zahlungen.size)

        // Wachsender Wert: zeit- und geldgewichtete Rendite beide beim Wachstum des Werts
        val w = reihe("2020-01-31", "2023-01-31", wachstum(0.08))
        val s = pb.rechnen(pb.Eingabe(listOf(w), listOf(1.0), pb.Regel(pb.Takt.NIE), start = 1_000.0, zahlungen = pb.sparrate(von, ms("2023-01-31"), 100.0)))!!
        val k = pb.kennzahlen(s)
        assertEquals(0.08, k.zeitgewichtet!!, 1e-3)
        assertEquals(0.08, k.geldgewichtet!!, 2e-3)
        assertEquals(1_000.0 + 36 * 100.0, k.eingezahlt, 1e-9)

        // Entnahme: anteilig aus dem Depot, hoechstens alles
        val raus = pb.rechnen(pb.Eingabe(listOf(flach), listOf(1.0), pb.Regel(pb.Takt.NIE), start = 1_000.0, zahlungen = listOf(pb.Zahlung(ms("2020-06-01"), -400.0))))!!
        assertEquals(600.0, raus.endwert, 1e-6)
        assertEquals(1.0, raus.index.last().second, 1e-12)
    }

    @Test
    fun gemeinsamerZeitraumUndGrenzen() {
        val a = reihe("2015-01-01", "2023-01-01", wachstum(0.05))
        val b = reihe("2018-06-01", "2022-12-29", wachstum(0.02))
        val z = pb.zeitraum(listOf(a, b))!!
        assertEquals(ms("2018-06-01"), z.first)
        // B endet drei Tage frueher und gilt als laufend
        assertEquals(ms("2023-01-01"), z.second)
        val alt = reihe("2015-01-01", "2020-01-01", wachstum(0.02))
        assertEquals(ms("2020-01-01"), pb.zeitraum(listOf(a, alt))!!.second)
        assertNull(pb.zeitraum(listOf(a, emptyList())))
        val e = pb.rechnen(pb.Eingabe(listOf(a, b), listOf(60.0, 40.0), von = ms("2020-01-01"), bis = ms("2021-01-01")))!!
        assertEquals(ms("2020-01-01"), e.verlauf.first().first)
        assertEquals(ms("2021-01-01"), e.verlauf.last().first)
        // Werte ohne Gewicht begrenzen den Zeitraum nicht
        val nurA = pb.rechnen(pb.Eingabe(listOf(a, b), listOf(1.0, 0.0)))!!
        assertEquals(ms("2015-01-01"), nurA.verlauf.first().first)
        assertNull(pb.rechnen(pb.Eingabe(listOf(a), listOf(1.0, 1.0))))
        assertNull(pb.rechnen(pb.Eingabe(listOf(a), listOf(0.0))))
        assertNull(pb.rechnen(pb.Eingabe(listOf(a, reihe("2024-01-01", "2024-06-01") { 1.0 }), listOf(1.0, 1.0))))
        // Luecken: der letzte Kurs gilt fort
        val luecke = listOf(ms("2020-01-01") to 10.0, ms("2020-01-10") to 20.0)
        val voll = reihe("2020-01-01", "2020-01-10") { 5.0 }
        val l = pb.rechnen(pb.Eingabe(listOf(luecke, voll), listOf(1.0, 1.0), pb.Regel(pb.Takt.NIE)))!!
        assertEquals(10, l.verlauf.size)
        assertEquals(10_000.0, l.verlauf[8].second, 1e-6)
        assertEquals(15_000.0, l.endwert, 1e-6)
    }

    @Test
    fun rueckgangMitDauer() {
        val t = { i: Int -> i * 10 * tag }
        val erholt = listOf(t(0) to 100.0, t(1) to 120.0, t(2) to 90.0, t(3) to 100.0, t(4) to 130.0, t(5) to 125.0)
        val r = pb.rueckgang(erholt)!!
        assertEquals(-0.25, r.tiefe, 1e-12)
        assertEquals(t(1), r.von)
        assertEquals(t(2), r.tief)
        assertEquals(t(4), r.bis)
        assertEquals(30L, r.tage)
        val offen = pb.rueckgang(erholt.take(4))!!
        assertNull(offen.bis)
        assertEquals(20L, offen.tage)
        assertEquals(listOf(0.0, 0.0, -0.25), pb.rueckgangReihe(erholt).take(3).map { it.second })
        assertEquals(0.0, pb.rueckgang(listOf(t(0) to 1.0, t(1) to 2.0))!!.tiefe, 0.0)
    }

    @Test
    fun haltedauernNurMitGenugFenstern() {
        // Sieben Jahre gleichmaessig 6 % im Jahr: jede Haltedauer bringt 6 %, zehn Jahre passen nicht hinein
        val gleich = reihe("2016-01-01", "2023-01-01", wachstum(0.06))
        val h = pb.haltedauern(gleich)
        assertEquals(listOf(1, 3, 5), h.map { it.jahre })
        h.forEach {
            assertEquals(0.06, it.median, 1e-4)
            assertEquals(0.06, it.min, 1e-4)
            assertEquals(0.06, it.max, 1e-4)
            assertEquals(0.0, it.verlust, 0.0)
            assertTrue(it.fenster >= pb.MIN_FENSTER)
        }
        // Fuenf Jahre und ein halbes: fuer fuenf Jahre Haltedauer bleiben zu wenige Startzeitpunkte
        assertEquals(listOf(1, 3), pb.haltedauern(reihe("2017-07-01", "2023-01-01", wachstum(0.06))).map { it.jahre })
        assertTrue(pb.haltedauern(reihe("2022-06-01", "2023-01-01", wachstum(0.06))).isEmpty())
        // Erst ein Jahr minus 20 %, dann zwei Jahre plus: ein Teil der Einjahresfenster endet im Verlust
        val knick = reihe("2020-01-01", "2023-01-01") { i -> if (i <= 365) 100.0 - 20.0 * i / 365 else 80.0 + 60.0 * (i - 365) / 731 }
        val ein = pb.haltedauer(knick, 1)!!
        assertTrue(ein.verlust > 0.1 && ein.verlust < 0.5)
        assertTrue(ein.min < 0 && ein.max > 0)
        assertTrue(ein.min <= ein.q1 && ein.q1 <= ein.median && ein.median <= ein.q3 && ein.q3 <= ein.max)
        assertEquals(-0.20, ein.min, 0.01)
    }

    @Test
    fun realTeiltDurchDiePreissteigerung() {
        val a = reihe("2020-01-01", "2022-01-01", wachstum(0.10))
        val e = pb.rechnen(pb.Eingabe(listOf(a), listOf(1.0), pb.Regel(pb.Takt.NIE), zahlungen = listOf(pb.Zahlung(ms("2021-01-01"), 1_000.0))))!!
        val a0 = ms("2020-01-01")
        // Preise steigen gleichmaessig um 10 % im Jahr wie der Wert
        val real = pb.real(e) { t -> 1.10.pow((t - a0) / (365.25 * tag)) }!!
        assertEquals(1.0, real.index.last().second, 1e-9)
        assertEquals(0.0, pb.kennzahlen(real).zeitgewichtet!!, 1e-9)
        assertTrue(real.endwert < e.endwert)
        assertTrue(real.zahlungen[1].betrag < 1_000.0)
        assertNull(pb.real(e) { t -> if (t > a0) 1.0 else null })
    }

    @Test
    fun regelVergleichNebeneinander() {
        val a = reihe("2019-01-01", "2023-01-01", wachstum(0.12))
        val b = reihe("2019-01-01", "2023-01-01", wachstum(0.01))
        val v = pb.regelVergleich(pb.Eingabe(listOf(a, b), listOf(1.0, 1.0), kosten = pb.Kosten(20.0, 1.0)), 0.05)
        assertEquals(4, v.size)
        assertEquals(listOf(pb.Takt.NIE, pb.Takt.JAEHRLICH, pb.Takt.QUARTAL, pb.Takt.JAEHRLICH), v.map { it.first.takt })
        assertEquals(0.05, v[3].first.band!!, 0.0)
        assertEquals(0, v[0].second.umschichtungen)
        assertTrue(v[2].second.umschichtungen > v[1].second.umschichtungen)
        assertTrue(v[2].second.kosten > v[1].second.kosten && v[1].second.kosten > v[0].second.kosten)
    }

    private fun kerzen(r: List<Pair<Long, Double>>) = r.map { Candle(it.first, it.second, it.second, it.second, it.second) }

    @Test
    fun musterdepotsMitJaehrlicherUmschichtungUndRangliste() {
        assertNotNull(Strategien.finde("dreifonds"))
        assertNotNull(Strategien.finde("siebzigdreissig"))
        assertNotNull(Strategien.finde("klassisch"))
        assertNotNull(Strategien.finde("butterfly"))
        // Musterdepot: jaehrlich auf die Anteile zurueck, also weniger als ohne Umschichten, wenn ein Teil davonlaeuft
        val a = reihe("2019-01-01", "2023-01-01", wachstum(0.20))
        val b = reihe("2019-01-01", "2023-01-01") { 100.0 }
        val k = Strategien.kennzahlen(mapOf("A" to kerzen(a), "B" to kerzen(b)), listOf("A" to 0.5, "B" to 0.5), ms("2023-01-01"))!!
        val nie = pb.rechnen(pb.Eingabe(listOf(a, b), listOf(1.0, 1.0), pb.Regel(pb.Takt.NIE)))!!
        val jahr = pb.rechnen(pb.Eingabe(listOf(a, b), listOf(1.0, 1.0), pb.Regel(pb.Takt.JAEHRLICH)))!!
        assertEquals(jahr.index.last().second - 1, k.gesamt, 1e-9)
        assertTrue(k.gesamt < nie.index.last().second - 1)
        assertEquals(listOf(2019, 2020, 2021, 2022, 2023), k.jahre.map { it.first })
        assertEquals(0.10, k.jahre[1].second, 0.002)

        // Rangliste: alle Muster, sortierbar, ohne Kurse am Ende
        val jetzt = ms("2023-01-01")
        val symbole = Strategien.alle.flatMap { s -> s.anteile.map { it.first } }.distinct()
        val reihen = symbole.mapIndexed { i, s ->
            // Je Wert ein anderes Wachstum und eine eigene Welle; der letzte Wert hat nur zwei Jahre Kurse
            val von = if (i == symbole.lastIndex) "2021-01-01" else "2012-06-01"
            s to kerzen(reihe(von, "2023-01-01") { n -> 100.0 * (1 + 0.01 * (i + 1)).pow(n / 365.25) * (1 + 0.01 * (1 + i % 4) * kotlin.math.sin(n / 9.0)) })
        }.toMap()
        val liste = Strategien.rangliste(reihen, jetzt, 5, 0.02)
        assertEquals(Strategien.alle.map { it.id }, liste.map { it.id })
        val kurzes = Strategien.alle.filter { s -> s.anteile.any { it.first == symbole.last() } }.map { it.id }
        assertTrue(kurzes.isNotEmpty())
        liste.forEach { r -> assertEquals(r.id, r.id in kurzes, r.rendite == null) }
        // Ein Jahr deckt auch der kurze Wert
        assertTrue(Strategien.rangliste(reihen, jetzt, 1, 0.02).all { it.rendite != null && it.schwankung != null && it.rueckgang != null && it.sharpe != null })
        for (s in Strategien.Spalte.entries) {
            val beste = Strategien.sortiert(liste, s, true)
            val mit = beste.filter { it.wert(s) != null }
            assertEquals(liste.size, beste.size)
            assertTrue(beste.drop(mit.size).all { it.wert(s) == null })
            val werte = mit.map { it.wert(s)!! }
            assertEquals(if (s == Strategien.Spalte.SCHWANKUNG) werte.sorted() else werte.sortedDescending(), werte)
            assertEquals(mit.reversed().map { it.id }, Strategien.sortiert(liste, s, false).take(mit.size).map { it.id })
        }
        // Sharpe ueber dem Zins: mit hoeherem Zins kleiner
        val ohneZins = Strategien.rang(Strategien.finde("welt")!!, reihen, jetzt, 5, 0.0)!!
        val mitZins = Strategien.rang(Strategien.finde("welt")!!, reihen, jetzt, 5, 0.03)!!
        assertTrue(mitZins.sharpe!! < ohneZins.sharpe!!)
        assertEquals(ohneZins.rendite!!, mitZins.rendite!!, 0.0)
    }

    @Test
    fun werkzeugRechnetMischungenGegenDenIndex() {
        val reihen = mapOf(
            "A" to BtReihe(reihe("2014-01-01", "2023-01-01", wachstum(0.08)), true),
            "B" to BtReihe(reihe("2016-03-01", "2023-01-01", wachstum(0.02)), false),
            "IDX" to BtReihe(reihe("2010-01-01", "2023-01-01", wachstum(0.06)), true),
        )
        fun eingabe(jahre: Int, sparrate: Double = 0.0, mischungen: List<BtMischung> = listOf(
            BtMischung("", listOf(BtPosten("A", "Wert A", 60.0), BtPosten("B", "Wert B", 40.0))),
            BtMischung("", listOf(BtPosten("A", "Wert A", 100.0), BtPosten("B", "Wert B", 0.0))),
            BtMischung("", listOf(BtPosten("C", "Wert C", 100.0))),
        )) = BtEingabe(mischungen, pb.Regel(pb.Takt.JAEHRLICH), 0.05, 10_000.0, sparrate, false, "IDX", jahre, pb.Kosten(20.0, 1.0), 0.02, 0)
        val e = btRechnen(eingabe(0), reihen, "IDX", null, 0L)
        // Laengster gemeinsamer Zeitraum aller nutzbaren Werte: B begrenzt
        assertEquals(ms("2016-03-01"), e.von)
        assertEquals(ms("2023-01-01"), e.bis)
        assertEquals("Wert B", e.begrenzer)
        assertFalse(e.alleMitDividenden)
        assertEquals(3, e.laeufe.size)
        assertNotNull(e.laeufe[0].kennzahlen)
        assertNotNull(e.laeufe[1].kennzahlen)
        assertNull(e.laeufe[2].ergebnis)
        assertTrue(e.laeufe[2].fehler!!.contains("Wert C"))
        assertEquals(e.von, e.laeufe[1].ergebnis!!.verlauf.first().first)
        assertEquals(e.von, e.index!!.ergebnis!!.verlauf.first().first)
        // Index mit denselben Kosten: knapp unter 6 % im Jahr
        assertEquals(0.06, e.index!!.kennzahlen!!.zeitgewichtet!!, 0.002)
        assertTrue(e.laeufe[1].kennzahlen!!.zeitgewichtet!! > e.laeufe[0].kennzahlen!!.zeitgewichtet!!)
        assertEquals(listOf(1, 3, 5), e.halte.map { it.jahre })
        assertEquals(4, e.regeln.size)
        // Zeitleiste: drei Jahre
        val drei = btRechnen(eingabe(3), reihen, "IDX", null, 0L)
        assertEquals(3.0, (drei.bis - drei.von) / (365.25 * tag), 0.01)
        assertEquals(e.jahreDa, drei.jahreDa, 1e-9)
        // Sparrate: eingezahlt waechst, Index bekommt dieselben Zahlungen
        val spar = btRechnen(eingabe(3, 200.0), reihen, "IDX", null, 0L)
        assertEquals(10_000.0 + 36 * 200.0, spar.laeufe[0].kennzahlen!!.eingezahlt, 1e-6)
        assertEquals(spar.laeufe[0].kennzahlen!!.eingezahlt, spar.index!!.kennzahlen!!.eingezahlt, 1e-6)
        // Eine Mischung mit einem Wert hat keinen Regelvergleich
        assertTrue(btRechnen(eingabe(0, mischungen = listOf(BtMischung("", listOf(BtPosten("A", "Wert A", 100.0))))), reihen, "IDX", null, 0L).regeln.isEmpty())
        // Ohne nutzbare Mischung kein Zeitraum
        val leer = btRechnen(eingabe(0, mischungen = listOf(BtMischung("", emptyList()))), reihen, "IDX", null, 0L)
        assertEquals(0L, leer.bis)
        assertNotNull(leer.laeufe[0].fehler)

        // Real: der Zeitraum beginnt fruehestens mit dem Preisindex
        val monate = (2018..2023).flatMap { j -> (1..12).map { m -> Kaufkraft.Monat("$j-${m.toString().padStart(2, '0')}", 100.0 + (j - 2018) * 2.0 + m / 6.0) } }
        val real = btRechnen(eingabe(0).copy(real = true), reihen, "IDX", Kaufkraft.Index(monate), ms("2018-01-01"))
        assertEquals(ms("2018-01-01"), real.von)
        val nominal = btRechnen(eingabe(0).copy(mischungen = eingabe(0).mischungen.take(1)), reihen.mapValues { (_, r) -> BtReihe(r.kurse.filter { it.first >= ms("2018-01-01") }, r.mitDividenden) }, "IDX", null, 0L)
        assertTrue(real.laeufe[0].kennzahlen!!.endwert < nominal.laeufe[0].kennzahlen!!.endwert)
        assertEquals(nominal.laeufe[0].kennzahlen!!.sharpe!!, real.laeufe[0].kennzahlen!!.sharpe!!, 1e-9)

        assertEquals(listOf(1.0, 1.0, 2.0, 3.0, 3.0), btRaster(listOf(0L to 1.0, 50L to 2.0, 75L to 3.0), 0L, 100L, 5))
    }

    @Test
    fun texteInBeidenSprachenUndPlatzInDerListe() {
        assertEquals(BacktestTexte.schluessel(true), BacktestTexte.schluessel(false))
        for (de in listOf(true, false)) for (k in BacktestTexte.schluessel(de)) {
            val t = BacktestTexte.text(de, k)
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('?') || t.contains('–') || t.contains('—') || t.contains('…'))
            // Gleiche Platzhalter in beiden Sprachen
            assertEquals(k, Regex("\\{\\d}").findAll(BacktestTexte.text(true, k)).map { it.value }.toSet(), Regex("\\{\\d}").findAll(BacktestTexte.text(false, k)).map { it.value }.toSet())
        }
        for (t in pb.Takt.entries) assertTrue(t.name, "takt." + t.name in BacktestTexte.schluessel(true))
        for (s in Strategien.Spalte.entries) assertTrue(s.name, "rang." + s.name in BacktestTexte.schluessel(true))
        assertEquals("Mischung 2", BacktestTexte.text(true, "mischung", listOf("2")))
        assertEquals("+6.1% p.a.", BacktestTexte.text(false, "pa", listOf("+6.1%")))

        val name = PortfolioTool.BACKTEST.name
        assertEquals(1, WERKZEUG_GRUPPEN.count { name in it.second })
        assertTrue(werkzeugFormen(name).isNotEmpty())
        assertEquals("Portfolio-Backtest", De.ptTitle(name))
        assertEquals("Portfolio backtest", En.ptTitle(name))
        for (s in listOf(De, En)) {
            assertTrue(s.ptSubtitle(name).isNotBlank())
            assertFalse(s.ptSubtitle(name).contains('?'))
            assertTrue(s.copyStrategiesNote.isNotBlank() && s.pvNoteStrategy.isNotBlank())
        }
        assertTrue(De.copyStrategiesNote.contains("jährlicher Umschichtung"))
        assertFalse(De.pvNoteStrategy.contains("täglich"))
        assertFalse(En.copyStrategiesNote.contains("every day"))
    }
}
