package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor.AusstiegArt as A
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Fassung 0.78: Strategietest mit vier weiteren Regeln, Trendfilter, Monatsende, waehlbarem Ausstieg (Stopp,
// Trailing-Stopp, Zeit, Gewinnziel) und Kennzahlen je Geschaeft.
class Fassung078U10StrategieTest {

    private val ms = 86_400_000L
    private val ohneKosten = StrategieLabor.Kosten(0.0, 0.0)

    // Werktage ab Montag, 1.1.2024
    private fun werktage(n: Int): List<LocalDate> {
        val aus = ArrayList<LocalDate>()
        var d = LocalDate.of(2024, 1, 1)
        while (aus.size < n) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) aus += d
            d = d.plusDays(1)
        }
        return aus
    }

    private fun naechsterWerktag(d: LocalDate): LocalDate {
        var x = d.plusDays(1)
        while (x.dayOfWeek == DayOfWeek.SATURDAY || x.dayOfWeek == DayOfWeek.SUNDAY) x = x.plusDays(1)
        return x
    }

    // Kerzen an Werktagen aus Schlusskursen; Eroeffnung = Schluss des Vortags.
    private fun kerzen(schluss: List<Double>): List<Candle> {
        val tage = werktage(schluss.size)
        return schluss.mapIndexed { i, c ->
            val o = if (i == 0) c else schluss[i - 1]
            Candle(tage[i].toEpochDay() * ms, o, maxOf(o, c) * 1.002, minOf(o, c) * 0.998, c)
        }
    }

    // Kerzen aus (Eroeffnung, Hoch, Tief, Schluss) an Werktagen.
    private fun ohlc(vararg k: List<Double>): List<Candle> {
        val tage = werktage(k.size)
        return k.mapIndexed { i, x -> Candle(tage[i].toEpochDay() * ms, x[0], x[1], x[2], x[3]) }
    }

    private fun zufall(seed: Int, n: Int = 1600, drift: Double = 0.0): List<Candle> {
        val r = Random(seed)
        var p = 100.0
        return kerzen((0 until n).map {
            val u = r.nextDouble().coerceAtLeast(1e-12)
            p *= 1.0 + drift + 0.012 * kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * PI * r.nextDouble())
            p
        })
    }

    private fun regel(art: StrategieLabor.Art) = StrategieLabor.REGELN.last { it.art == art }

    @Test fun regelnUndZusatz() {
        // Die bisherigen acht Regeln stehen unveraendert vorn, die weiteren sind anfangs ausgeblendet
        val r = StrategieLabor.REGELN
        assertEquals(13, r.size)
        assertEquals(
            listOf("HALTEN-0-0-0", "SMA-200-0-0", "KREUZ-50-200-0", "RSI-30-70-14", "MACD-0-0-0", "DONCHIAN-20-10-0", "BOLLINGER-20-2-0", "RHYTHMUS-0-0-0"),
            r.take(8).map { Vergleichsrechnung.laborSchluessel(it) },
        )
        assertEquals(
            listOf("MOMENTUM-252-0-0", "RSI_KURZ-10-5-2", "MONATSWECHSEL-3-0-0", "DONCHIAN-55-20-0", "ZIELVOLA-15-100-20"),
            r.drop(8).map { Vergleichsrechnung.laborSchluessel(it) },
        )
        assertEquals(r.drop(8).toSet(), StrategieLabor.ZUSATZ)
        assertTrue(r.all { it.trend == 0 && !it.monatlich })
        assertEquals(13, StrategieLabor.AUSSTIEGE.size)
        assertEquals(StrategieLabor.REGELAUSSTIEG, StrategieLabor.AUSSTIEGE.first())
    }

    // Die Rechnung des Strategietests vor 0.78, zum Vergleich.
    private fun alt(d: StrategieLabor.Daten, ziel: BooleanArray, von: Int, bis: Int, kosten: StrategieLabor.Kosten): Triple<Double, Int, Int> {
        var bar = StrategieLabor.START
        var stueck = 0.0
        var einsatz = 0.0
        var trades = 0
        var gewinner = 0
        var wert = bar
        for (i in von until bis) {
            val soll = if (i == 0) ziel[0] else ziel[i - 1]
            val p = d.eroeffnung(i)
            if (soll && stueck == 0.0) {
                val geld = bar - kosten.gebuehr
                val kurs = Pricing.ask(p, kosten.spreadBps)
                if (geld > 0 && kurs > 0) { stueck = geld / kurs; einsatz = bar; bar = 0.0; trades++ }
            } else if (!soll && stueck > 0) {
                bar = stueck * Pricing.bid(p, kosten.spreadBps) - kosten.gebuehr
                if (bar > einsatz) gewinner++
                stueck = 0.0
            }
            wert = bar + stueck * d.schluss[i]
        }
        if (stueck > 0 && stueck * Pricing.bid(d.schluss[bis - 1], kosten.spreadBps) - kosten.gebuehr > einsatz) gewinner++
        return Triple(wert, trades, gewinner)
    }

    @Test fun alteRegelnRechnenWieBisher() {
        val kosten = StrategieLabor.Kosten(20.0)
        for (seed in 1..3) {
            val k = zufall(seed)
            val d = StrategieLabor.Daten(k)
            for (r in StrategieLabor.REGELN) {
                val z = StrategieLabor.ziel(d, r)
                val (wert, trades, gewinner) = alt(d, z, 100, k.size, kosten)
                val e = StrategieLabor.simulieren(d, z, 100, k.size, kosten)!!
                assertEquals("$r", wert, e.endwert, 0.0)
                assertEquals("$r", trades, e.trades)
                assertEquals("$r", if (trades > 0) gewinner.toDouble() / trades else null, e.treffer)
                // Die Liste der Geschaefte passt zu Zahl und Trefferquote
                assertEquals("$r", trades, e.geschaefte.size)
                assertEquals("$r", gewinner, e.geschaefte.count { it.ergebnis > 0 })
                assertTrue(e.geschaefte.dropLast(1).none { it.offen })
                assertTrue(StrategieLabor.simulieren(d, z, 100, k.size, kosten, leicht = true)!!.geschaefte.isEmpty())
            }
        }
    }

    @Test fun momentumNurAmMonatsende() {
        val schluss = (0 until 1300).map { 100.0 + 20.0 * sin(it / 60.0) + it * 0.01 }
        val k = kerzen(schluss)
        val tage = werktage(1300)
        val z = StrategieLabor.ziel(StrategieLabor.Daten(k), regel(StrategieLabor.Art.MOMENTUM))
        var erwartet = false
        var an = 0
        var ab = 0
        for (i in 0 until 1300) {
            val ende = naechsterWerktag(tage[i]).month != tage[i].month
            val vorher = erwartet
            if (ende) erwartet = i >= 252 && schluss[i] > schluss[i - 252]
            if (erwartet && !vorher) an++
            if (!erwartet && vorher) ab++
            assertEquals("Tag $i ${tage[i]}", erwartet, z[i])
        }
        assertTrue(an >= 2 && ab >= 1)
        // Vor einem Jahr Kursgeschichte bleibt die Regel draussen
        assertTrue((0 until 252).none { z[it] })
    }

    @Test fun monatswechselVomLetztenBisZumDrittenHandelstag() {
        val k = kerzen(List(300) { 100.0 + it })
        val tage = werktage(300)
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, regel(StrategieLabor.Art.MONATSWECHSEL))
        fun index(jahr: Int, monat: Int, tag: Int) = tage.indexOf(LocalDate.of(jahr, monat, tag))
        // Dienstag, 30.1.: der naechste Handelstag ist der letzte des Monats. Montag, 5.2. ist der dritte im Februar.
        assertFalse(z[index(2024, 1, 29)])
        assertTrue(z[index(2024, 1, 30)])
        assertTrue(z[index(2024, 1, 31)])
        assertTrue(z[index(2024, 2, 1)])
        assertTrue(z[index(2024, 2, 2)])
        assertFalse(z[index(2024, 2, 5)])
        // Ab Februar gegen den Kalender: investiert, wenn der naechste Handelstag der letzte oder einer der ersten drei ist
        for (i in index(2024, 2, 1) until 300) {
            val n = naechsterWerktag(tage[i])
            val letzter = naechsterWerktag(n).month != n.month
            var stelle = 1
            var x = n.withDayOfMonth(1)
            while (x < n) { if (x.dayOfWeek != DayOfWeek.SATURDAY && x.dayOfWeek != DayOfWeek.SUNDAY) stelle++; x = x.plusDays(1) }
            assertEquals("Tag ${tage[i]}", letzter || stelle <= 3, z[i])
        }
        // Gekauft zur Eroeffnung am 31.1., verkauft zur Eroeffnung am 6.2.: vier Handelstage
        val e = StrategieLabor.simulieren(d, z, index(2024, 1, 22), index(2024, 2, 20), ohneKosten)!!
        val g = e.geschaefte.single()
        assertEquals(tage[index(2024, 1, 31)].toEpochDay() * ms, g.einstieg)
        assertEquals(tage[index(2024, 2, 6)].toEpochDay() * ms, g.ausstieg)
        assertEquals(4, g.tage)
        assertFalse(g.offen)
    }

    @Test fun kurzerRsiKauftNurImAufwaertstrend() {
        val r = regel(StrategieLabor.Art.RSI_KURZ)
        // 220 Tage Anstieg, drei schwache Tage: RSI(2) faellt auf 33, 14 und 6,7. Danach ein starker Tag ueber die 5-Tage-Linie.
        val hoch = (0 until 220).map { 100.0 + 0.5 * it }
        val delle = listOf(hoch.last() - 1.0, hoch.last() - 2.0, hoch.last() - 3.0)
        val z = StrategieLabor.ziel(StrategieLabor.Daten(kerzen(hoch + delle + listOf(hoch.last() + 2.0, hoch.last() + 2.5))), r)
        assertTrue((0..221).none { z[it] })
        assertTrue(z[222])
        assertFalse(z[223])
        assertFalse(z[224])
        // Bleibt der Schluss unter der 5-Tage-Linie, endet das Geschaeft nach zehn Handelstagen
        val schwach = (1..14).map { delle.last() - 0.1 * it }
        val k = kerzen(hoch + delle + schwach)
        val d = StrategieLabor.Daten(k)
        val z2 = StrategieLabor.ziel(d, r)
        assertTrue((222..231).all { z2[it] })
        assertFalse(z2[232])
        val g = StrategieLabor.simulieren(d, z2, 200, 234, ohneKosten)!!.geschaefte.first()
        assertEquals(k[223].t, g.einstieg)
        assertEquals(k[233].t, g.ausstieg)
        assertEquals(10, g.tage)
        // Unter der 200-Tage-Linie kein Kauf, so tief der RSI auch steht
        val fallend = StrategieLabor.Daten(kerzen((0 until 400).map { 400.0 - 0.5 * it }))
        assertTrue(fallend.rsi(2)[300]!! < 10)
        assertTrue(StrategieLabor.ziel(fallend, r).none { it })
    }

    @Test fun donchian55Und20() {
        fun flach(c: Double) = listOf(c, c, c, c)
        // Spitze am Tag 10, Tag 60 schliesst ueber dem 20-Tage-Hoch, aber unter dem 55-Tage-Hoch; Tag 70 liegt darueber
        val k = ohlc(*Array(90) { i ->
            when {
                i == 10 -> flach(105.0)
                i in 60..69 -> flach(101.0)
                i in 70..79 -> flach(106.0)
                i >= 80 -> flach(99.0)
                else -> flach(100.0)
            }
        })
        val d = StrategieLabor.Daten(k)
        val lang = StrategieLabor.ziel(d, regel(StrategieLabor.Art.DONCHIAN))
        val kurz = StrategieLabor.ziel(d, StrategieLabor.Regel(StrategieLabor.Art.DONCHIAN, 20, 10))
        assertEquals(StrategieLabor.Regel(StrategieLabor.Art.DONCHIAN, 55, 20), regel(StrategieLabor.Art.DONCHIAN))
        assertTrue(kurz[60])
        assertFalse(lang[60])
        assertFalse(lang[69])
        assertTrue(lang[70])
        assertTrue(lang[79])
        // 99 liegt unter dem Tief der 20 Tage davor (100)
        assertFalse(lang[80])
    }

    @Test fun trendfilterNurBeimKauf() {
        for (basis in listOf(StrategieLabor.REGELN[3], StrategieLabor.REGELN[6])) {
            val mit = StrategieLabor.variante(basis, trendfilter = true, monatsende = true)
            assertEquals(basis.copy(trend = 200), mit)
            assertEquals(basis, StrategieLabor.variante(basis, trendfilter = false, monatsende = true))
            var kaeufe = 0
            for (seed in 1..4) {
                val d = StrategieLabor.Daten(zufall(seed, drift = 0.0004))
                val ohne = StrategieLabor.ziel(d, basis)
                val z = StrategieLabor.ziel(d, mit)
                for (i in 1 until z.size) if (z[i] && !z[i - 1]) {
                    kaeufe++
                    assertTrue("$basis $seed $i", d.schluss[i] > d.sma(200)[i]!!)
                    // Jeder Kauf der gefilterten Regel erfuellt auch die Bedingung der Regel selbst
                    if (basis.art == StrategieLabor.Art.RSI) assertTrue(d.rsi(14)[i]!! < 30) else assertTrue(d.schluss[i] < d.bollinger(20, 2).unten[i]!!)
                }
                assertTrue(ohne.count { it } > 0)
            }
            assertTrue("$basis", kaeufe > 0)
            // Im Abwaertstrend kauft die Regel, die gefilterte nie
            val fallend = StrategieLabor.Daten(zufall(9, drift = -0.002))
            assertTrue(StrategieLabor.ziel(fallend, basis).any { it })
            val zf = StrategieLabor.ziel(fallend, mit)
            assertTrue((250 until zf.size).none { zf[it] && !zf[it - 1] && fallend.schluss[it] < fallend.sma(200)[it]!! })
        }
        // Andere Regeln kennen den Schalter nicht
        val macd = StrategieLabor.REGELN[4]
        assertEquals(macd, StrategieLabor.variante(macd, trendfilter = true, monatsende = true))
    }

    @Test fun durchschnittNurAmMonatsende() {
        val basis = StrategieLabor.REGELN[1]
        val monatlich = StrategieLabor.variante(basis, trendfilter = true, monatsende = true)
        assertEquals(basis.copy(monatlich = true), monatlich)
        val k = zufall(5)
        val tage = werktage(k.size)
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, monatlich)
        val taeglich = StrategieLabor.ziel(d, basis)
        var erwartet = false
        for (i in k.indices) {
            if (naechsterWerktag(tage[i]).month != tage[i].month) erwartet = d.sma(200)[i]?.let { d.schluss[i] > it } ?: false
            assertEquals("Tag $i", erwartet, z[i])
        }
        fun wechsel(x: BooleanArray) = (1 until x.size).count { x[it] != x[it - 1] }
        assertTrue(wechsel(z) > 0)
        assertTrue(wechsel(z) < wechsel(taeglich))
    }

    @Test fun keinBlickInDieZukunft() {
        val a = zufall(7)
        val t = 1200
        val b = a.take(t + 1) + a.drop(t + 1).map { it.copy(open = it.open * 3, high = it.high * 5, low = it.low * 0.2, close = it.close * 3) }
        val regeln = StrategieLabor.REGELN + listOf(
            StrategieLabor.REGELN[1].copy(monatlich = true), StrategieLabor.REGELN[3].copy(trend = 200), StrategieLabor.REGELN[6].copy(trend = 200),
        )
        val kosten = StrategieLabor.Kosten(20.0)
        for (r in regeln) {
            val da = StrategieLabor.Daten(a)
            val db = StrategieLabor.Daten(b)
            val za = StrategieLabor.ziel(da, r)
            val zb = StrategieLabor.ziel(db, r)
            for (i in 0..t) assertEquals("$r Tag $i", za[i], zb[i])
            // Mit jedem Ausstieg: das Ergebnis bis Tag t haengt nicht an den Kerzen danach
            for (x in StrategieLabor.AUSSTIEGE) {
                val ea = StrategieLabor.simulieren(da, za, 50, t + 1, kosten, ausstieg = x)!!
                val eb = StrategieLabor.simulieren(db, zb, 50, t + 1, kosten, ausstieg = x)!!
                assertEquals("$r $x", ea.endwert, eb.endwert, 0.0)
                assertEquals("$r $x", ea.geschaefte, eb.geschaefte)
                // Der ganze Lauf gleicht bis Tag t dem gekuerzten
                val ganz = StrategieLabor.simulieren(db, zb, 50, b.size, kosten, ausstieg = x)!!
                assertEquals("$r $x", eb.verlauf, ganz.verlauf.take(eb.verlauf.size))
            }
        }
    }

    private fun immer(n: Int) = BooleanArray(n) { true }

    @Test fun stoppZumStoppUndBeiLueckeZurEroeffnung() {
        val stopp = StrategieLabor.Ausstieg(A.STOPP, 10)
        // Kauf zu 100, Stopp bei 90. Tag 2 faellt im Verlauf darunter: Verkauf zu 90.
        val k = ohlc(listOf(100.0, 100.0, 100.0, 100.0), listOf(100.0, 101.0, 95.0, 96.0), listOf(96.0, 97.0, 89.0, 92.0), listOf(92.0, 120.0, 92.0, 120.0), listOf(120.0, 130.0, 120.0, 130.0))
        val e = StrategieLabor.simulieren(StrategieLabor.Daten(k), immer(5), 0, 5, ohneKosten, ausstieg = stopp)!!
        assertEquals(9_000.0, e.endwert, 1e-9)
        assertEquals(1, e.trades)
        assertEquals(0.0, e.treffer!!, 0.0)
        val g = e.geschaefte.single()
        assertEquals(-0.10, g.ergebnis, 1e-12)
        assertEquals(2, g.tage)
        assertEquals(k[2].t, g.ausstieg)
        assertEquals(listOf(10_000.0, 9_600.0, 9_000.0, 9_000.0, 9_000.0), e.verlauf.map { it.second })
        assertEquals(2.0 / 5, e.imMarkt, 1e-12)
        // Eroeffnung unter dem Stopp: Verkauf zur Eroeffnung
        val luecke = ohlc(listOf(100.0, 100.0, 100.0, 100.0), listOf(100.0, 101.0, 95.0, 96.0), listOf(85.0, 88.0, 84.0, 86.0), listOf(86.0, 86.0, 86.0, 86.0))
        assertEquals(8_500.0, StrategieLabor.simulieren(StrategieLabor.Daten(luecke), immer(4), 0, 4, ohneKosten, ausstieg = stopp)!!.endwert, 1e-9)
        // Am Kauftag selbst gilt der Stopp schon
        val gleich = ohlc(listOf(100.0, 100.0, 88.0, 95.0), listOf(95.0, 95.0, 95.0, 95.0), listOf(95.0, 95.0, 95.0, 95.0))
        val eg = StrategieLabor.simulieren(StrategieLabor.Daten(gleich), immer(3), 0, 3, ohneKosten, ausstieg = stopp)!!
        assertEquals(9_000.0, eg.endwert, 1e-9)
        assertEquals(0, eg.geschaefte.single().tage)
        // Mit Kosten: Kauf zum Brief, Verkauf zum Geld des Stoppkurses, je 1 Euro Gebuehr
        val kosten = StrategieLabor.Kosten(40.0)
        val stueck = (10_000.0 - 1.0) / Pricing.ask(100.0, 40.0)
        assertEquals(stueck * Pricing.bid(90.0, 40.0) - 1.0, StrategieLabor.simulieren(StrategieLabor.Daten(k), immer(5), 0, 5, kosten, ausstieg = stopp)!!.endwert, 1e-9)
        // Ohne Ausstieg haelt dieselbe Lage bis zum Ende
        assertEquals(13_000.0, StrategieLabor.simulieren(StrategieLabor.Daten(k), immer(5), 0, 5, ohneKosten)!!.endwert, 1e-9)
    }

    @Test fun wiedereinstiegErstNachNeuemKaufsignal() {
        val stopp = StrategieLabor.Ausstieg(A.STOPP, 10)
        val flach = listOf(100.0, 100.0, 100.0, 100.0)
        val k = ohlc(flach, listOf(100.0, 100.0, 89.0, 95.0), flach, flach, flach, listOf(100.0, 110.0, 100.0, 110.0), listOf(110.0, 121.0, 110.0, 121.0))
        val d = StrategieLabor.Daten(k)
        // Die Regel meldet durchgehend investiert: nach dem Stopp am Tag 1 kein neuer Kauf
        val durchgehend = StrategieLabor.simulieren(d, immer(7), 0, 7, ohneKosten, ausstieg = stopp)!!
        assertEquals(1, durchgehend.trades)
        assertEquals(9_000.0, durchgehend.endwert, 1e-9)
        // Die Regel ist am Schluss von Tag 3 draussen und am Schluss von Tag 4 wieder drin: Kauf zur Eroeffnung von Tag 5
        val z = booleanArrayOf(true, true, true, false, true, true, true)
        val e = StrategieLabor.simulieren(d, z, 0, 7, ohneKosten, ausstieg = stopp)!!
        assertEquals(2, e.trades)
        assertEquals(k[5].t, e.geschaefte[1].einstieg)
        assertTrue(e.geschaefte[1].offen)
        assertEquals(9_000.0 * 1.21, e.endwert, 1e-9)
        assertEquals(0.5, e.treffer!!, 0.0)
        // Nach einem Verkauf durch die Regel selbst gibt es keine Sperre
        val z2 = booleanArrayOf(true, false, true, true, true, true, true)
        val ohne = StrategieLabor.simulieren(d, z2, 0, 7, ohneKosten)!!
        assertEquals(2, ohne.trades)
        assertEquals(k[3].t, ohne.geschaefte[1].einstieg)
    }

    @Test fun trailingStoppSteigtNur() {
        // 25 Tage mit Spanne 2: ATR(20) = 2. Kauf am Tag 25 zu 100, Stopp 2 ATR darunter bei 96.
        val ruhig = List(25) { listOf(100.0, 101.0, 99.0, 100.0) }
        val k = ohlc(*(ruhig + listOf(
            // Schluss 105, ATR 2,25: Stopp 100,5
            listOf(100.0, 106.0, 99.0, 105.0),
            // ATR 2,3875: 105 - 4,775 = 100,225 laege tiefer, der Stopp bleibt bei 100,5
            listOf(105.0, 106.0, 101.0, 102.0),
            // Tief 100,4 unterschreitet 100,5, nicht aber 100,225
            listOf(102.0, 103.0, 100.4, 101.0),
            listOf(101.0, 101.0, 101.0, 101.0),
        )).toTypedArray())
        val d = StrategieLabor.Daten(k)
        assertEquals(2.0, d.atr(20)[24]!!, 1e-12)
        assertEquals(2.25, d.atr(20)[25]!!, 1e-12)
        assertEquals(2.3875, d.atr(20)[26]!!, 1e-12)
        val z = BooleanArray(29) { it >= 24 }
        val e = StrategieLabor.simulieren(d, z, 0, 29, ohneKosten, ausstieg = StrategieLabor.Ausstieg(A.TRAILING, 2))!!
        val g = e.geschaefte.single()
        assertEquals(k[25].t, g.einstieg)
        assertEquals(k[27].t, g.ausstieg)
        assertEquals(0.005, g.ergebnis, 1e-12)
        assertEquals(10_050.0, e.endwert, 1e-9)
        // Mit 4 ATR liegt der Stopp bei 105 - 9 = 96, das Tief 100,4 loest nichts aus
        assertTrue(StrategieLabor.simulieren(d, z, 0, 29, ohneKosten, ausstieg = StrategieLabor.Ausstieg(A.TRAILING, 4))!!.geschaefte.single().offen)
    }

    @Test fun zeitstoppNachHandelstagen() {
        val k = ohlc(*Array(12) { val o = 100.0 + it; listOf(o, o + 1, o, o + 1) })
        val d = StrategieLabor.Daten(k)
        val e = StrategieLabor.simulieren(d, immer(12), 0, 12, ohneKosten, ausstieg = StrategieLabor.Ausstieg(A.ZEIT, 5))!!
        val g = e.geschaefte.single()
        assertEquals(5, g.tage)
        assertEquals(k[5].t, g.ausstieg)
        // Verkauf zur Eroeffnung des fuenften Tags nach dem Kauf, danach gesperrt
        assertEquals(10_500.0, e.endwert, 1e-9)
        assertEquals(1, e.trades)
        // Das Verkaufssignal der Regel gilt weiter und kommt hier frueher
        val z = booleanArrayOf(true, true, false) + BooleanArray(9)
        assertEquals(3, StrategieLabor.simulieren(d, z, 0, 12, ohneKosten, ausstieg = StrategieLabor.Ausstieg(A.ZEIT, 5))!!.geschaefte.single().tage)
    }

    @Test fun gewinnzielUndStoppAmSelbenTag() {
        val ziel2 = StrategieLabor.Ausstieg(A.ZIEL, 2)
        val start = listOf(100.0, 100.0, 100.0, 100.0)
        val ende = listOf(100.0, 100.0, 100.0, 100.0)
        fun endwert(tag: List<Double>, a: StrategieLabor.Ausstieg = ziel2) =
            StrategieLabor.simulieren(StrategieLabor.Daten(ohlc(start, tag, ende)), immer(3), 0, 3, ohneKosten, ausstieg = a)!!.endwert
        // Stopp 8 % bei 92, Ziel 2R bei 116
        assertEquals(11_600.0, endwert(listOf(100.0, 117.0, 99.0, 110.0)), 1e-9)
        // Beides am selben Tag: der Stopp zaehlt
        assertEquals(9_200.0, endwert(listOf(100.0, 117.0, 91.0, 110.0)), 1e-9)
        // Eroeffnung ueber dem Ziel: Verkauf zur Eroeffnung
        assertEquals(12_000.0, endwert(listOf(120.0, 121.0, 119.0, 120.0)), 1e-9)
        // Weder noch: gehalten
        assertEquals(10_000.0, endwert(listOf(100.0, 115.0, 93.0, 110.0)), 1e-9)
        // 3R liegt bei 124
        assertEquals(12_400.0, endwert(listOf(100.0, 125.0, 99.0, 110.0), StrategieLabor.Ausstieg(A.ZIEL, 3)), 1e-9)
        assertEquals(10_000.0, endwert(listOf(100.0, 123.0, 99.0, 110.0), StrategieLabor.Ausstieg(A.ZIEL, 3)), 1e-9)
    }

    @Test fun kennzahlenHandgerechnet() {
        fun g(ergebnis: Double, tage: Int) = StrategieLabor.Geschaeft(0L, 0L, ergebnis, tage)
        val k = StrategieLabor.kennzahlen(listOf(g(0.10, 2), g(-0.05, 4), g(-0.02, 6), g(0.04, 8), g(-0.03, 10)))!!
        assertEquals(5, k.bilanz.anzahl)
        assertEquals(0.4, k.bilanz.trefferquote, 1e-12)
        assertEquals(0.07, k.bilanz.mittelGewinn!!, 1e-12)
        assertEquals(-0.10 / 3, k.bilanz.mittelVerlust!!, 1e-12)
        assertEquals(2.1, k.verhaeltnis!!, 1e-12)
        assertEquals(0.008, k.bilanz.erwartung, 1e-12)
        assertEquals(1.4, k.bilanz.gewinnfaktor!!, 1e-12)
        assertEquals(6.0, k.haltedauer, 1e-12)
        assertEquals(2, k.verlustserie)
        // Nur Gewinne: kein Verhaeltnis und kein Gewinnfaktor
        val nurPlus = StrategieLabor.kennzahlen(listOf(g(0.5, 100)))!!
        assertNull(nurPlus.verhaeltnis)
        assertNull(nurPlus.bilanz.gewinnfaktor)
        assertEquals(0, nurPlus.verlustserie)
        assertEquals(3, StrategieLabor.kennzahlen(listOf(g(-0.1, 1), g(-0.1, 1), g(-0.1, 1), g(0.1, 1), g(-0.1, 1)))!!.verlustserie)
        assertNull(StrategieLabor.kennzahlen(emptyList()))
        // Dieselbe Rechnung wie bei den eigenen Verkaeufen; dort bleiben Schwelle und Mindestzahl
        val euro = listOf(100.0, -50.0, -20.0, 40.0, -30.0)
        val v = Risiko.verkaeufe(euro)!!
        assertEquals(Risiko.bilanz(euro, Verkaufsbilanz.SCHWELLE, Verkaufsbilanz.MIN_VERKAEUFE), v)
        assertEquals(1.4, v.gewinnfaktor!!, 1e-12)
        assertEquals(8.0, v.erwartung, 1e-12)
        assertNull(Risiko.verkaeufe(euro.take(4)))
        assertNull(Risiko.verkaeufe(euro.take(4) + 0.004))
    }

    @Test fun kennzahlenAusDerRechnung() {
        // Zwei Geschaefte: 100 auf 110, dann 110 auf 99
        val f = { c: Double -> listOf(c, c, c, c) }
        val k = ohlc(f(100.0), f(100.0), f(110.0), f(110.0), f(110.0), f(99.0), f(99.0))
        val z = booleanArrayOf(true, false, true, true, false, false, false)
        val e = StrategieLabor.simulieren(StrategieLabor.Daten(k), z, 0, 7, ohneKosten)!!
        assertEquals(listOf(0.10, -0.10), e.geschaefte.map { Math.round(it.ergebnis * 1e9) / 1e9 })
        assertEquals(listOf(2, 2), e.geschaefte.map { it.tage })
        val kz = StrategieLabor.kennzahlen(e.geschaefte)!!
        assertEquals(1.0, kz.verhaeltnis!!, 1e-9)
        assertEquals(0.0, kz.bilanz.erwartung, 1e-9)
        assertEquals(1, kz.verlustserie)
        assertEquals(0.5, e.treffer!!, 0.0)
    }

    @Test fun pruefenMitAusstiegUndAndereWerte() {
        val k = zufall(11)
        val kosten = StrategieLabor.Kosten(20.0)
        val stopp = StrategieLabor.Ausstieg(A.STOPP, 5)
        val ohne = StrategieLabor.pruefen(k, 300, kosten)
        val mit = StrategieLabor.pruefen(k, 300, kosten, ausstieg = stopp)
        // Kaufen und Halten bleibt der Massstab ohne Ausstieg
        assertEquals(ohne[0].gesamt.endwert, mit[0].gesamt.endwert, 0.0)
        assertNotEquals(ohne[3].gesamt.endwert, mit[3].gesamt.endwert)
        assertTrue(ohne[3].gesamt.geschaefte.any { it.ergebnis < -0.09 })
        // Kein Geschaeft verliert mit 5 % Stopp viel mehr als 5 % (Luecken und Kosten ausgenommen)
        assertTrue(mit.drop(1).all { b -> b.gesamt.geschaefte.all { it.ergebnis > -0.09 } })
        // Lotterie und Walk-Forward kennen keinen Ausstieg: ihre Varianten bleiben die der Regelfamilien
        assertTrue(StrategieLabor.varianten(StrategieLabor.Familie.ALLE).all { it.trend == 0 && !it.monatlich })

        val von = k[300].t
        val bis = k.last().t
        assertEquals(0.0, StrategieLabor.vorsprung(k, von, bis, StrategieLabor.REGELN[0], StrategieLabor.REGELAUSSTIEG, kosten)!!, 0.0)
        val sma = StrategieLabor.REGELN[1]
        assertEquals(mit[1].gesamt.renditeJahr!! - mit[0].gesamt.renditeJahr!!, StrategieLabor.vorsprung(k, von, bis, sma, stopp, kosten)!!, 1e-12)
        assertNull(StrategieLabor.vorsprung(k.take(299), von, bis, sma, stopp, kosten))
        assertNull(StrategieLabor.vorsprung(k, bis + ms, bis + 2 * ms, sma, stopp, kosten))
        assertEquals(2, StrategieLabor.schlaegtHalten(listOf(0.01, -0.02, 0.0, 0.03)))
        assertEquals(listOf("A", "B", "C"), StrategieLabor.andereWerte(listOf("A", "X", "B"), listOf("B", "C", "X"), "X"))
        assertEquals(20, StrategieLabor.andereWerte((1..30).map { "S$it" }, emptyList(), "S1").size)
    }

    @Test fun texte() {
        for (s in listOf(De, En)) {
            val namen = StrategieLabor.REGELN.map { s.slRegel(it.art.name, it.a, it.b, it.n) }
            val kurz = StrategieLabor.REGELN.map { s.slRegelKurz(it.art.name, it.a, it.b, it.n) }
            assertEquals(13, namen.toSet().size)
            assertEquals(13, kurz.toSet().size)
            assertTrue((namen + kurz).none { n -> StrategieLabor.Art.entries.any { it.name == n } })
            val ausstiege = StrategieLabor.AUSSTIEGE.map { s.slAusstiegName(it.art.name, it.wert, StrategieLabor.ZIEL_STOPP) }
            assertEquals(13, ausstiege.toSet().size)
            val alle = namen + kurz + ausstiege + StrategieLabor.AUSSTIEGE.map { s.slAusstiegHinweis(it.art.name, it.wert, StrategieLabor.ZIEL_STOPP) } +
                StrategieLabor.Art.entries.map { s.slRegelInfo(it.name) } +
                listOf(s.slAusstieg, s.slTrendfilter, s.slMonatsende, s.slMehrRegeln(4), s.slGv, s.slErwartung, s.slGewinnfaktor, s.slHaltedauer, s.slVerlustserie,
                    s.slKennzahlen, s.slKennzahlenText, s.slAndere, s.slAndereText, s.slAndereKern(3, 7), s.slAndereSpalte, s.slAndereKeine)
            assertTrue(alle.none { it.contains('—') || it.contains('–') || it.contains('?') })
            // Je neuer Regel hoechstens ein Satz
            for (art in listOf("MOMENTUM", "RSI_KURZ", "MONATSWECHSEL")) assertEquals(1, s.slRegelInfo(art).count { it == '.' })
            assertEquals("", s.slRegelInfo("DONCHIAN"))
        }
        assertEquals("Ziel 2R, Stopp 8 %", De.slAusstiegName("ZIEL", 2, 8))
        assertTrue(De.slAusstiegHinweis("ZIEL", 3, 8).contains("24 %"))
        assertEquals(De.ptClosedSalesLine(5, 2, "40 %", "1,40", null, null, "8 €").contains(De.slGewinnfaktor), true)
    }
}
