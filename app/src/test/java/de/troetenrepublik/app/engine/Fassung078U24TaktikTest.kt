package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.TaktikTexte
import de.troetenrepublik.app.market.Catalog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Fassung 0.78: Signal auf einem anderen Wert, Ausweichwert statt Bargeld und Momentum-Rotation ueber mehrere Werte.
class Fassung078U24TaktikTest {

    private val ms = 86_400_000L
    private val ohneKosten = StrategieLabor.Kosten(0.0, 0.0)
    private val mitKosten = StrategieLabor.Kosten(20.0, 1.0)

    private fun werktage(n: Int, start: LocalDate = LocalDate.of(2018, 1, 1)): List<LocalDate> {
        val aus = ArrayList<LocalDate>()
        var d = start
        while (aus.size < n) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) aus += d
            d = d.plusDays(1)
        }
        return aus
    }

    // Kerzen an Werktagen aus Schlusskursen; Eroeffnung = Schluss des Vortags.
    private fun kerzen(schluss: List<Double>, tage: List<LocalDate> = werktage(schluss.size)): List<Candle> = schluss.mapIndexed { i, c ->
        val o = if (i == 0) c else schluss[i - 1]
        Candle(tage[i].toEpochDay() * ms, o, maxOf(o, c), minOf(o, c), c)
    }

    private fun wachstum(n: Int, jeTag: Double, start: Double = 100.0) = kerzen((0 until n).map { start * (1.0 + jeTag).pow(it) })

    private fun zufall(seed: Int, n: Int = 1600, drift: Double = 0.0003): List<Candle> {
        val r = Random(seed)
        var c = 100.0
        return kerzen((0 until n).map { c *= 1.0 + drift + (r.nextDouble() - 0.5) * 0.03; c })
    }

    private fun nah(erwartet: Double, ist: Double) = assertEquals(erwartet, ist, erwartet * 1e-9)

    // Gemeinsame Handelstage

    @Test fun gemeinsameHandelstage() {
        val tage = werktage(40)
        val a = kerzen((0 until 40).map { 100.0 + it }, tage)
        // b fehlt an jedem fuenften Tag und liegt spaeter am Tag
        val b = a.filterIndexed { i, _ -> i % 5 != 0 }.map { it.copy(t = it.t + 6 * 3_600_000L, close = it.close * 2) }
        val g = Taktik.gemeinsam(listOf(a, b))
        assertEquals(2, g.size)
        assertEquals(32, g[0].size)
        assertEquals(32, g[1].size)
        for (i in g[0].indices) {
            assertEquals(g[0][i].t.floorDiv(ms), g[1][i].t.floorDiv(ms))
            nah(g[0][i].close * 2, g[1][i].close)
            if (i > 0) assertTrue(g[0][i].t > g[0][i - 1].t)
        }
        assertTrue(Taktik.gemeinsam(emptyList()).isEmpty())
        val r = Taktik.reihen(a, null, b)
        assertNull(r.signal)
        assertEquals(32, r.handel.size)
        assertEquals(32, r.ausweich!!.size)
        // Ein kuerzerer Zeitraum schneidet alle Reihen gleich
        val d = r.daten(20)
        assertEquals(20, d.kerzen.size)
        assertEquals(20, d.ausweich!!.kerzen.size)
    }

    // Ohne Signalwert und Ausweichwert bleibt der Test, wie er war

    @Test fun ohneWahlUnveraendert() {
        val k = zufall(3)
        val alt = StrategieLabor.pruefen(k, 300, mitKosten)
        val neu = StrategieLabor.pruefen(Taktik.reihen(k, null, null).daten(), 300, mitKosten)
        assertEquals(StrategieLabor.REGELN.size, alt.size)
        for (i in alt.indices) {
            assertEquals(alt[i].gesamt.endwert, neu[i].gesamt.endwert, 0.0)
            assertEquals(alt[i].pruef.endwert, neu[i].pruef.endwert, 0.0)
            assertEquals(alt[i].gesamt.trades, neu[i].gesamt.trades)
        }
    }

    // Ausweichwert

    @Test fun ausweichwertStattBargeld() {
        val n = 60
        val handel = wachstum(n, 0.01)
        val aus = wachstum(n, 0.005, 50.0)
        val d = StrategieLabor.Daten(handel, ausweich = StrategieLabor.Daten(aus))
        // Signal immer aus: das Geld liegt ab der ersten Eroeffnung im Ausweichwert
        val nie = StrategieLabor.simulieren(d, BooleanArray(n), 1, n, ohneKosten)!!
        nah(StrategieLabor.START / aus[1].open * aus[n - 1].close, nie.endwert)
        assertEquals(0, nie.trades)
        assertEquals(0.0, nie.imMarkt, 0.0)
        // Signal an nach dem Schluss der Tage 10 bis 19: Kauf zur Eroeffnung von Tag 11, Verkauf zur Eroeffnung von Tag 21
        val z = BooleanArray(n) { it in 10..19 }
        val e = StrategieLabor.simulieren(d, z, 1, n, ohneKosten)!!
        nah(StrategieLabor.START / aus[1].open * aus[11].open / handel[11].open * handel[21].open / aus[21].open * aus[n - 1].close, e.endwert)
        assertEquals(1, e.trades)
        assertEquals(1, e.geschaefte.size)
        assertEquals(10, e.geschaefte[0].tage)
        nah(handel[21].open / handel[11].open, e.geschaefte[0].ergebnis + 1.0)
        assertEquals(10.0 / (n - 1), e.imMarkt, 1e-12)
        // Ohne Ausweichwert bleibt in denselben Tagen Bargeld
        val bar = StrategieLabor.simulieren(StrategieLabor.Daten(handel), z, 1, n, ohneKosten)!!
        nah(StrategieLabor.START / handel[11].open * handel[21].open, bar.endwert)
    }

    @Test fun ausweichwertKostetSpreadUndGebuehr() {
        val n = 60
        val handel = wachstum(n, 0.01)
        val aus = wachstum(n, 0.005, 50.0)
        val d = StrategieLabor.Daten(handel, ausweich = StrategieLabor.Daten(aus))
        val z = BooleanArray(n) { it in 10..19 }
        val e = StrategieLabor.simulieren(d, z, 1, n, mitKosten)!!
        val bps = mitKosten.spreadBps
        val park = (StrategieLabor.START - 1.0) / Pricing.ask(aus[1].open, bps)
        val frei = park * Pricing.bid(aus[11].open, bps) - 1.0
        val stueck = (frei - 1.0) / Pricing.ask(handel[11].open, bps)
        val erloes = stueck * Pricing.bid(handel[21].open, bps) - 1.0
        val park2 = (erloes - 1.0) / Pricing.ask(aus[21].open, bps)
        nah(park2 * aus[n - 1].close, e.endwert)
        nah(erloes / frei, e.geschaefte[0].ergebnis + 1.0)
        assertTrue(e.endwert < StrategieLabor.simulieren(d, z, 1, n, ohneKosten)!!.endwert)
    }

    @Test fun nachStoppErstAmNaechstenTagInDenAusweichwert() {
        val n = 20
        val tage = werktage(n)
        val handel = (0 until n).map { i ->
            when {
                i < 5 -> Candle(tage[i].toEpochDay() * ms, 100.0, 100.0, 100.0, 100.0)
                i == 5 -> Candle(tage[i].toEpochDay() * ms, 100.0, 100.0, 90.0, 92.0)
                else -> Candle(tage[i].toEpochDay() * ms, 92.0, 92.0, 92.0, 92.0)
            }
        }
        val aus = wachstum(n, 0.01)
        val d = StrategieLabor.Daten(handel, ausweich = StrategieLabor.Daten(aus))
        val e = StrategieLabor.simulieren(d, BooleanArray(n) { true }, 1, n, ohneKosten, ausstieg = StrategieLabor.Ausstieg(StrategieLabor.AusstiegArt.STOPP, 5))!!
        // Kauf zu 100, Stopp bei 95 im Verlauf von Tag 5, Ausweichwert zur Eroeffnung von Tag 6
        nah(StrategieLabor.START / 100.0 * 95.0 / aus[6].open * aus[n - 1].close, e.endwert)
        assertEquals(1, e.trades)
    }

    // Signal auf einem anderen Wert

    @Test fun signalVomAnderenWert() {
        val n = 400
        val handel = kerzen(List(n) { 100.0 })
        val signal = kerzen((0 until n).map { 100.0 + 30.0 * sin(it * 2 * PI / 120) })
        val r = Taktik.reihen(handel, signal, null)
        val d = r.daten()
        val regel = StrategieLabor.Regel(StrategieLabor.Art.SMA, 20)
        val z = StrategieLabor.ziel(d, regel)
        assertArrayEquals(StrategieLabor.ziel(StrategieLabor.Daten(signal), regel), z)
        // Der gehandelte Wert selbst gaebe nie ein Signal (Kurs nie ueber dem Durchschnitt)
        assertTrue(StrategieLabor.ziel(StrategieLabor.Daten(handel), regel).none { it })
        assertTrue(z.any { it } && z.any { !it })
        val b = StrategieLabor.pruefen(d, 30, ohneKosten, listOf(StrategieLabor.Regel(StrategieLabor.Art.HALTEN), regel))
        assertEquals(2, b.size)
        // Gehandelt wird der flache Wert: ohne Kosten bleibt das Kapital, mit Kosten sinkt es je Geschaeft
        nah(StrategieLabor.START, b[1].gesamt.endwert)
        assertTrue(b[1].gesamt.trades >= 3)
        assertTrue(b[1].gesamt.imMarkt > 0.2 && b[1].gesamt.imMarkt < 0.8)
        val teuer = StrategieLabor.pruefen(d, 30, mitKosten, listOf(regel)).first().gesamt
        assertTrue(teuer.endwert < StrategieLabor.START)
        // Kaufen und Halten haelt immer den gehandelten Wert
        assertEquals(1.0, b[0].gesamt.imMarkt, 0.0)
    }

    @Test fun pruefungenRechnenMitTaktik() {
        val handel = zufall(5)
        val signal = zufall(6)
        val aus = zufall(7, drift = 0.0001)
        val t = Taktik.reihen(handel, signal, aus)
        val ohne = StrategieLabor.pruefen(handel, 300, mitKosten)
        val mit = StrategieLabor.pruefen(t.daten(), 300, mitKosten)
        assertEquals(ohne.size, mit.size)
        // Kaufen und Halten bleibt gleich, die Regeln aendern sich
        assertEquals(ohne[0].gesamt.endwert, mit[0].gesamt.endwert, 1e-9)
        assertTrue(ohne.indices.any { it > 0 && ohne[it].gesamt.endwert != mit[it].gesamt.endwert })
        // Lern- und Pruefzeitraum, Kennzahlen und Aussagekraft wie ohne Taktik
        val sma = mit[1]
        assertEquals(StrategieLabor.teilung(300, handel.size), handel.indexOfFirst { it.t == sma.pruef.verlauf.first().first })
        assertNotNull(StrategieLabor.kennzahlen(sma.gesamt.geschaefte))
        assertNotNull(StrategieLabor.aussage(sma.gesamt))
        // Zufallstest, Lotterie und Walk-Forward laufen auf denselben Daten
        val l = StrategieLabor.lotterie(t.daten(), 300, mitKosten, StrategieLabor.Familie.DURCHSCHNITT)!!
        assertEquals(50, l.anzahl)
        val d = t.daten()
        assertNotNull(StrategieLabor.zufallsvergleich(d, StrategieLabor.ziel(d, StrategieLabor.REGELN[1]), 300, handel.size, mitKosten, laeufe = 50))
        assertNotNull(StrategieLabor.walkForward(t.daten(), 0, mitKosten, StrategieLabor.Familie.DURCHSCHNITT, lernJahre = 2))
    }

    // Rotation

    private fun dreiWerte(n: Int = 700) = Rotation.daten(listOf(wachstum(n, 0.001), wachstum(n, 0.0005), wachstum(n, 0.0)), null)

    @Test fun ranglisteUndGewichte() {
        val d = dreiWerte()
        val w = Rotation.Wahl(monate = 3, halten = 1)
        assertTrue(d.monatsenden.size > 20)
        assertTrue(Rotation.rangliste(d, 2, 3).isEmpty())
        assertNull(Rotation.gewichte(d, 2, w))
        val rang = Rotation.rangliste(d, 3, 3)
        assertEquals(listOf(0, 1, 2), rang.map { it.wert })
        nah(d.werte[0].schluss[d.monatsenden[3]] / d.werte[0].schluss[d.monatsenden[0]], rang[0].rendite + 1.0)
        assertArrayEquals(doubleArrayOf(1.0, 0.0, 0.0), Rotation.gewichte(d, 3, w), 1e-12)
        assertArrayEquals(doubleArrayOf(0.5, 0.5, 0.0), Rotation.gewichte(d, 3, w.copy(halten = 2)), 1e-12)
        // Mehr Plaetze als Werte: alle gleich
        assertArrayEquals(doubleArrayOf(1.0 / 3, 1.0 / 3, 1.0 / 3), Rotation.gewichte(d, 3, w.copy(halten = 9)), 1e-12)
        // Absolutes Momentum ohne Ausweichwert: nur Werte mit Rendite ueber null, der Rest ist Bargeld
        assertArrayEquals(doubleArrayOf(1.0 / 3, 1.0 / 3, 0.0), Rotation.gewichte(d, 3, w.copy(halten = 3, absolut = true)), 1e-12)
        assertEquals(d.monatsenden[3] + 1, Rotation.ersterTag(d, w))
        assertEquals(d.monatsenden[12] + 1, Rotation.ersterTag(d, w.copy(monate = 12)))
    }

    @Test fun absolutesMomentumGegenDenAusweichwert() {
        val n = 700
        val d = Rotation.daten(listOf(wachstum(n, 0.001), wachstum(n, 0.0002), wachstum(n, -0.0005)), wachstum(n, 0.0005))
        assertEquals(3, d.werte.size)
        assertEquals(4, d.alle.size)
        // Nur der erste Wert liegt ueber dem Ausweichwert; der freie Platz geht in den Ausweichwert
        assertArrayEquals(doubleArrayOf(0.5, 0.0, 0.0, 0.5), Rotation.gewichte(d, 6, Rotation.Wahl(6, 2, absolut = true)), 1e-12)
        // Ohne die Schwelle bleiben die besten zwei
        assertArrayEquals(doubleArrayOf(0.5, 0.5, 0.0, 0.0), Rotation.gewichte(d, 6, Rotation.Wahl(6, 2)), 1e-12)
        val fallend = Rotation.daten(listOf(wachstum(n, -0.001), wachstum(n, -0.0002)), wachstum(n, 0.0001))
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 1.0), Rotation.gewichte(fallend, 6, Rotation.Wahl(6, 1, absolut = true)), 1e-12)
        val b = Rotation.pruefen(fallend, 0, ohneKosten, Rotation.Wahl(6, 1, absolut = true))!!
        // Nie ein Wert des Universums im Depot: kein Geschaeft, das Geld waechst mit dem Ausweichwert
        assertEquals(0, b.gesamt.trades)
        assertEquals(0.0, b.gesamt.imMarkt, 0.0)
        val aus = fallend.ausweich!!
        nah(StrategieLabor.START / aus.eroeffnung(b.von) * aus.schluss[n - 1], b.gesamt.endwert)
        assertEquals(Handelsstil.POSITION, Handelsstil.rotation(b.gesamt))
    }

    @Test fun inversZurSchwankung() {
        val n = 400
        val ruhig = kerzen((0 until n).map { 100.0 * 1.001.pow(it) * (1.0 + 0.002 * sin(it * 1.7)) })
        val wild = kerzen((0 until n).map { 100.0 * 1.001.pow(it) * (1.0 + 0.02 * sin(it * 1.7)) })
        val d = Rotation.daten(listOf(ruhig, wild), null)
        val j = d.monatsenden.size - 2
        val g = Rotation.gewichte(d, j, Rotation.Wahl(3, 2, Rotation.Gewichtung.SCHWANKUNG))!!
        assertEquals(1.0, g.sum(), 1e-12)
        assertTrue(g[0] > 0.8)
        val sr = Rotation.schwankung(d.werte[0], d.monatsenden[j])!!
        val sw = Rotation.schwankung(d.werte[1], d.monatsenden[j])!!
        nah((1 / sr) / (1 / sr + 1 / sw), g[0])
        // Zu Beginn fehlen die Tage fuer die Schwankung: gleich gewichtet
        assertNull(Rotation.schwankung(d.werte[0], Rotation.SCHWANKUNG_TAGE - 1))
    }

    @Test fun rotationHaeltDenStaerksten() {
        val d = dreiWerte()
        val n = d.n
        val w = Rotation.Wahl(monate = 3, halten = 1)
        val b = Rotation.pruefen(d, 0, ohneKosten, w)!!
        assertEquals(Rotation.ersterTag(d, w), b.von)
        assertEquals(StrategieLabor.teilung(b.von, n), b.grenze)
        val a = d.werte[0]
        nah(StrategieLabor.START / a.eroeffnung(b.von) * a.schluss[n - 1], b.gesamt.endwert)
        assertEquals(1, b.gesamt.trades)
        assertEquals(1, b.gesamt.geschaefte.size)
        assertTrue(b.gesamt.geschaefte[0].offen)
        assertEquals(n - 1 - b.von, b.gesamt.geschaefte[0].tage)
        assertEquals(1.0, b.gesamt.imMarkt, 0.0)
        assertEquals(Handelsstil.BUY_AND_HOLD, Handelsstil.rotation(b.gesamt))
        // Kaufen und Halten: alle Werte am ersten Tag zu gleichen Teilen
        val mittel = d.werte.map { it.schluss[n - 1] / it.eroeffnung(b.von) }.average()
        nah(StrategieLabor.START * mittel, b.haltenGesamt.endwert)
        assertEquals(3, b.haltenGesamt.trades)
        assertTrue(b.gesamt.endwert > b.haltenGesamt.endwert)
        // Lern- und Pruefzeitraum je mit frischem Kapital
        nah(StrategieLabor.START / a.eroeffnung(b.grenze) * a.schluss[n - 1], b.pruef.endwert)
        nah(StrategieLabor.START / a.eroeffnung(b.von) * a.schluss[b.grenze - 1], b.lern.endwert)
        assertEquals(d.werte[0].kerzen[b.grenze].t, b.pruef.verlauf.first().first)
        assertArrayEquals(doubleArrayOf(1.0, 0.0, 0.0), b.heute, 1e-12)
        assertEquals(0, b.rang.first().wert)
        // Mit Kosten weniger
        assertTrue(Rotation.pruefen(d, 0, mitKosten, w)!!.gesamt.endwert < b.gesamt.endwert)
        // Spaeterer Beginn zaehlt
        assertEquals(500, Rotation.pruefen(d, 500, ohneKosten, w)!!.von)
        assertNull(Rotation.pruefen(d, n - 50, ohneKosten, w))
        assertNull(Rotation.pruefen(Rotation.daten(listOf(wachstum(700, 0.001)), null), 0, ohneKosten, w))
    }

    // Zwei Werte, die sich an der Spitze abwechseln
    private fun wechsel(n: Int = 1500): Rotation.Daten {
        val a = kerzen((0 until n).map { 100.0 * 1.0003.pow(it) * (1.0 + 0.25 * sin(it * 2 * PI / 250)) })
        val b = kerzen((0 until n).map { 100.0 * 1.0003.pow(it) * (1.0 - 0.25 * sin(it * 2 * PI / 250)) })
        return Rotation.daten(listOf(a, b), null)
    }

    @Test fun rotationWechseltUndFuelltDieKennzahlen() {
        val d = wechsel()
        val w = Rotation.Wahl(monate = 3, halten = 1)
        val b = Rotation.pruefen(d, 0, mitKosten, w)!!
        val e = b.gesamt
        assertTrue(e.trades >= 8)
        assertEquals(e.trades, e.geschaefte.size)
        // Gehalten wird mindestens bis zum naechsten Monatsende
        assertTrue(e.geschaefte.all { it.tage >= 15 })
        assertTrue(e.geschaefte.zipWithNext().all { (x, y) -> x.ausstieg <= y.ausstieg })
        assertTrue(e.imMarkt > 0.99)
        assertEquals(e.treffer!!, e.geschaefte.count { it.ergebnis > 0 }.toDouble() / e.trades, 1e-12)
        val k = StrategieLabor.kennzahlen(e.geschaefte)!!
        assertEquals(Handelsstil.nachHaltedauer(k.haltedauer), Handelsstil.rotation(e))
        assertEquals(Handelsstil.POSITION, Handelsstil.rotation(e))
        assertTrue(Handelsstil.rotation(e).testbar)
        assertEquals(StrategieLabor.aussage(e.trades, e.jahre), StrategieLabor.aussage(e))
        assertEquals(e.verlauf.size, e.renditen.size)
        assertEquals(StrategieLabor.sharpe(e.renditen), e.sharpe, 0.0)
        assertTrue(e.maxRueckgang > 0 && e.maxRueckgang < 1)
        // Der Depotwert ist an jedem Tag die Summe aus Bargeld und Bestand: kein Sprung ueber die Tagesbewegung der Werte hinaus
        assertTrue(e.renditen.all { it > -0.1 && it < 0.1 })
        assertEquals(2, b.haltenGesamt.trades)
    }

    @Test fun rotationKenntKeineSpaeterenKurse() {
        val n = 900
        val a = zufall(11, n)
        val b = zufall(12, n)
        val c = zufall(13, n)
        val schnitt = 600
        fun anders(k: List<Candle>) = k.mapIndexed { i, x -> if (i <= schnitt) x else x.copy(open = x.open * 3, high = x.high * 3, low = x.low * 3, close = x.close * 3) }
        val w = Rotation.Wahl(6, 2, Rotation.Gewichtung.SCHWANKUNG, absolut = true)
        val d1 = Rotation.daten(listOf(a, b, c), null)
        val d2 = Rotation.daten(listOf(anders(a), b, anders(c)), null)
        val z1 = Rotation.ziele(d1, w)
        val z2 = Rotation.ziele(d2, w)
        var entscheidungen = 0
        for (i in 0..schnitt) {
            if (z1[i] == null) assertNull(z2[i]) else { assertArrayEquals(z1[i], z2[i], 0.0); entscheidungen++ }
        }
        assertTrue(entscheidungen > 10)
        // Entscheidungen fallen nur am letzten Handelstag eines Monats, gehandelt wird am Tag danach
        for (i in 0 until n) if (z1[i] != null) assertTrue(d1.werte[0].monatsende(i))
        val von = Rotation.ersterTag(d1, w)
        val e1 = Rotation.simulieren(d1, z1, von, schnitt + 1, mitKosten)!!
        val e2 = Rotation.simulieren(d2, z2, von, schnitt + 1, mitKosten)!!
        assertEquals(e1.endwert, e2.endwert, 0.0)
        assertTrue(z1.filterNotNull().all { it.sum() <= 1.0 + 1e-9 && it.all { g -> g >= 0.0 } })
        assertNull(Rotation.simulieren(d1, z1, 5, 300, mitKosten))
    }

    @Test fun rueckblickeImSelbenZeitraumUndIndex() {
        val d = wechsel()
        val w = Rotation.Wahl(monate = 3, halten = 1)
        val liste = Rotation.rueckblicke(d, 0, mitKosten, w)
        assertEquals(Rotation.MONATE, liste.map { it.first })
        assertEquals(1, liste.map { it.second.von }.distinct().size)
        assertEquals(Rotation.ersterTag(d, w.copy(monate = 12)), liste.first().second.von)
        assertEquals(1, liste.map { it.second.haltenGesamt.endwert }.distinct().size)
        // Vergleichsindex: Kaufen und Halten im selben Zeitraum
        val zeit = d.werte[0].kerzen
        val index = zufall(21, 1500)
        val von = liste.first().second.von
        val e = Rotation.index(index, zeit[von].t, zeit.last().t, ohneKosten)!!
        nah(StrategieLabor.START / index[von].open * index.last().close, e.endwert)
        assertNull(Rotation.index(index, zeit.last().t + 10 * ms, zeit.last().t + 20 * ms, ohneKosten))
    }

    // Vorschlaege aus dem Katalog

    @Test fun vorschlaegeAusDemKatalog() {
        fun etf(s: String, n: String, i: String, r: String) = Catalog.Etf(s = s, n = n, i = i, r = r)
        val etfs = listOf(
            etf("WELT", "World", "SEKTORUEBERGREIFEND", "GLOBAL"), etf("USA1", "USA", "SEKTORUEBERGREIFEND", "NORDAMERIKA"),
            etf("USA2", "USA 2", "SEKTORUEBERGREIFEND", "NORDAMERIKA"), etf("EUR1", "Europe", "SEKTORUEBERGREIFEND", "EUROPA"),
            etf("EM1", "EM", "SEKTORUEBERGREIFEND", "SCHWELLENLAENDER"), etf("JP1", "Japan", "SEKTORUEBERGREIFEND", "ASIEN_PAZIFIK"),
            etf("SILB", "Physical Silver", "ROHSTOFFE", "GLOBAL"), etf("GOLD", "Physical Gold", "ROHSTOFFE", "GLOBAL"),
            etf("UST", "Treasury", "STAATSANLEIHEN", "NORDAMERIKA"), etf("BUND", "Euro Government", "STAATSANLEIHEN", "EUROPA"),
            etf("CORP", "Corporate", "UNTERNEHMENSANLEIHEN", "EUROPA"), etf("GELD", "Overnight", "GELDMARKT", "EUROPA"),
        )
        assertEquals(listOf("GELD", "BUND", "UST", "CORP", "GOLD"), Taktik.ausweichVorschlaege(etfs))
        assertEquals(listOf("USA1", "EUR1", "EM1", "JP1", "BUND", "GOLD"), Taktik.rotationAuswahl(etfs))
        assertTrue(Taktik.ausweichVorschlaege(emptyList()).isEmpty())
        // Der mitgelieferte Katalog fuellt beide Listen
        val datei = listOf("src/main/assets/katalog.json", "../app/src/main/assets/katalog.json").map { java.io.File(it) }.first { it.exists() }
        val echt = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<Catalog.Data>(datei.readText(Charsets.UTF_8)).etfs
        assertEquals(5, Taktik.ausweichVorschlaege(echt).size)
        assertEquals(6, Taktik.rotationAuswahl(echt).size)
        val auswahl = Taktik.rotationAuswahl(etfs)
        assertEquals(listOf("A", "B", "C"), Taktik.vorbelegung(listOf("A", "B"), listOf("B", "C"), auswahl))
        assertEquals(auswahl, Taktik.vorbelegung(listOf("A"), emptyList(), auswahl))
        assertEquals(Rotation.MAX_WERTE, Taktik.vorbelegung((1..30).map { "S$it" }, emptyList(), auswahl).size)
    }

    // Texte

    @Test fun texteInBeidenSprachen() {
        assertEquals(TaktikTexte.schluessel(true), TaktikTexte.schluessel(false))
        for (deutsch in listOf(true, false)) for (k in TaktikTexte.schluessel(deutsch)) {
            val t = TaktikTexte.text(deutsch, k, emptyList())
            assertTrue(k, t.isNotBlank())
            // Keine Gedankenstriche, keine Fragen
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('?'))
        }
        assertEquals("3 Monate · beste 2 · gleich gewichtet", TaktikTexte.text(true, "rot.regel.kurz", listOf("3 Monate", "2", "gleich gewichtet")))
        assertTrue(TaktikTexte.text(true, "rot.methode", listOf("0,10 %")).contains("hypothetisch"))
        assertTrue(TaktikTexte.text(false, "rot.methode", listOf("0.10%")).contains("hypothetical"))
        for (g in Rotation.Gewichtung.entries) assertTrue(TaktikTexte.text(true, "rot." + g.name, emptyList()).isNotBlank())
        for (s in listOf(De, En)) {
            assertTrue(s.ptTitle("ROTATION") != "ROTATION")
            assertTrue(s.ptSubtitle("ROTATION").isNotBlank())
            assertFalse(s.ptSubtitle("ROTATION").contains('?'))
        }
    }
}
