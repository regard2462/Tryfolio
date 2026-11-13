package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.MarketApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// 0.73: Rechenkern des Strategietests mit von Hand nachgerechneten Reihen, Aussagekraft und Ertragsreihe.
class Fassung073StrategieTest {

    private val tag = 86_400_000L
    private val beginn = 1_500_000_000_000L

    // Kerzen aus Schlusskursen; Eroeffnung = Schluss des Vortags, am ersten Tag der Schluss.
    private fun kerzen(schluss: List<Double>, abstandTage: Int = 1): List<Candle> = schluss.mapIndexed { i, c ->
        val o = if (i == 0) c else schluss[i - 1]
        Candle(beginn + i * abstandTage * tag, o, maxOf(o, c) * 1.001, minOf(o, c) * 0.999, c)
    }

    private val ohneKosten = StrategieLabor.Kosten(0.0, 0.0)

    private fun sim(k: List<Candle>, ziel: BooleanArray, kosten: StrategieLabor.Kosten = ohneKosten, von: Int = 0, bis: Int = k.size) =
        StrategieLabor.simulieren(StrategieLabor.Daten(k), ziel, von, bis, kosten)

    // Fuenf Tage: Signal nach Tag 1 (kaufen) und nach Tag 3 (verkaufen), Handel jeweils zur Eroeffnung des Folgetags.
    private val handKurse = listOf(100.0, 110.0, 99.0, 108.9, 108.9)
    private val handZiel = booleanArrayOf(false, true, true, false, false)

    @Test fun handrechnungOhneKosten() {
        val e = sim(kerzen(handKurse), handZiel)!!
        // Kauf zur Eroeffnung von Tag 2 (110), Verkauf zur Eroeffnung von Tag 4 (108,9): 10000 / 110 * 108,9
        assertEquals(9_900.0, e.endwert, 1e-9)
        assertEquals(1, e.trades)
        assertEquals(0.0, e.treffer!!, 0.0)
        // Tage 2 und 3 im Markt, von fuenf
        assertEquals(0.4, e.imMarkt, 1e-12)
        // Werte 10000, 10000, 9000, 9900, 9900: groesster Rueckgang 10 %
        assertEquals(0.1, e.maxRueckgang, 1e-12)
        assertEquals(listOf(10_000.0, 10_000.0, 9_000.0, 9_900.0, 9_900.0), e.verlauf.map { it.second })
    }

    @Test fun handrechnungMitGebuehr() {
        // Je Order 10: gekauft wird mit 9990, verkauft 9990 / 110 * 108,9 = 9890,1, davon 10 Gebuehr
        val e = sim(kerzen(handKurse), handZiel, StrategieLabor.Kosten(0.0, 10.0))!!
        assertEquals(9_880.1, e.endwert, 1e-9)
    }

    @Test fun handrechnungMitSpread() {
        // 200 Basispunkte Spanne: Kauf zu 110 mal 1,01, Verkauf zu 108,9 mal 0,99
        val e = sim(kerzen(handKurse), handZiel, StrategieLabor.Kosten(200.0, 0.0))!!
        assertEquals(10_000.0 / (110.0 * 1.01) * 108.9 * 0.99, e.endwert, 1e-9)
    }

    @Test fun signalVomSchlussWirdFruehestensAmNaechstenTagGehandelt() {
        // Signal nach Tag 1; am Tag 1 selbst ist noch nichts gekauft
        val e = sim(kerzen(handKurse), handZiel)!!
        assertEquals(10_000.0, e.verlauf[1].second, 1e-9)
        // Springt der Kurs von Tag 1 auf Tag 2, gilt fuer den Kauf die Eroeffnung von Tag 2, nicht der Schluss von Tag 1
        val k = kerzen(listOf(100.0, 100.0, 100.0, 100.0)).toMutableList()
        k[2] = k[2].copy(open = 150.0, close = 150.0)
        k[3] = k[3].copy(open = 150.0, close = 150.0)
        val e2 = sim(k, booleanArrayOf(false, true, true, true))!!
        // gekauft zu 150 (Eroeffnung Tag 2), nicht zu 100: kein Gewinn
        assertEquals(10_000.0, e2.endwert, 1e-9)
    }

    @Test fun fallendeReihe() {
        val schluss = (0 until 60).map { 100.0 * 0.99.pow(it) }
        val k = kerzen(schluss)
        val e = sim(k, BooleanArray(60) { true })!!
        // Halten ab Tag 0 zur Eroeffnung 100
        assertEquals(StrategieLabor.START * schluss.last() / 100.0, e.endwert, 1e-6)
        assertEquals(1.0 - 0.99.pow(59), e.maxRueckgang, 1e-9)
        assertTrue(e.renditeJahr == null || e.renditeJahr!! < 0)
        assertEquals(0.0, e.treffer!!, 0.0)
    }

    @Test fun steigendeReihe() {
        val schluss = (0 until 400).map { 100.0 * 1.001.pow(it) }
        val e = sim(kerzen(schluss), BooleanArray(400) { true })!!
        assertEquals(StrategieLabor.START * schluss.last() / 100.0, e.endwert, 1e-6)
        assertEquals(0.0, e.maxRueckgang, 1e-12)
        assertEquals(1.0, e.imMarkt, 1e-12)
        assertTrue(e.sharpe > 0)
        assertEquals(1.0, e.treffer!!, 0.0)
    }

    @Test fun seitwaertsReiheKostetNurGebuehr() {
        val k = kerzen(List(100) { 100.0 })
        val e = sim(k, BooleanArray(100) { true }, StrategieLabor.Kosten(0.0, 1.0))!!
        // eine Order, 9999 im Markt zum Schluss
        assertEquals(9_999.0, e.endwert, 1e-9)
        val ohne = sim(k, BooleanArray(100) { true })!!
        assertEquals(StrategieLabor.START, ohne.endwert, 1e-9)
        assertEquals(0.0, ohne.sharpe, 0.0)
        assertEquals(0.0, ohne.maxRueckgang, 0.0)
    }

    @Test fun sprungReihe() {
        val schluss = List(60) { 100.0 } + List(60) { 200.0 }
        val k = kerzen(schluss)
        val halten = sim(k, BooleanArray(120) { true })!!
        assertEquals(20_000.0, halten.endwert, 1e-9)
        // Eine Regel, die erst nach dem Sprung (Signal am Tag 70) einsteigt, verpasst ihn und gewinnt nichts
        val spaet = sim(k, BooleanArray(120) { it >= 70 })!!
        assertEquals(10_000.0, spaet.endwert, 1e-9)
        // Eine Regel, die vor dem Sprung aussteigt (Signal Tag 59), hat nichts vom Sprung
        val raus = sim(k, BooleanArray(120) { it < 59 })!!
        assertEquals(10_000.0, raus.endwert, 1e-9)
    }

    @Test fun sehrKurzeReihe() {
        val k = kerzen(listOf(100.0, 101.0, 102.0))
        assertNull(sim(k, BooleanArray(3) { true }, von = 0, bis = 1))
        assertNotNull(sim(k, BooleanArray(3) { true }, von = 0, bis = 2))
        assertNull(sim(k, BooleanArray(3) { true }, von = -1, bis = 3))
        assertNull(sim(k, BooleanArray(3) { true }, von = 0, bis = 4))
        // unter einem Vierteljahr keine Rendite pro Jahr
        assertNull(sim(k, BooleanArray(3) { true })!!.renditeJahr)
        assertTrue(StrategieLabor.pruefen(kerzen(List(15) { 100.0 }), 0, ohneKosten).isEmpty())
    }

    @Test fun reiheMitLuecken() {
        // 25 Kerzen im Abstand von 30 Tagen: 720 Tage, also knapp zwei Jahre, nicht 24 Handelstage
        val schluss = List(25) { if (it < 12) 100.0 else 200.0 }
        val k = kerzen(schluss, abstandTage = 30)
        val e = sim(k, BooleanArray(25) { true })!!
        val jahre = 24 * 30 * 86_400_000.0 / (365.25 * 86_400_000.0)
        assertEquals(jahre, e.jahre, 1e-9)
        assertEquals(2.0.pow(1.0 / jahre) - 1.0, e.renditeJahr!!, 1e-9)
    }

    @Test fun leichteRechnungGleichDerVollen() {
        val schluss = (0 until 300).map { 100.0 + 15.0 * kotlin.math.sin(it / 7.0) + it * 0.05 }
        val k = kerzen(schluss)
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, StrategieLabor.Regel(StrategieLabor.Art.SMA, 20))
        val voll = StrategieLabor.simulieren(d, z, 0, 300, StrategieLabor.Kosten(20.0))!!
        val leicht = StrategieLabor.simulieren(d, z, 0, 300, StrategieLabor.Kosten(20.0), leicht = true)!!
        assertEquals(voll.endwert, leicht.endwert, 0.0)
        assertEquals(voll.maxRueckgang, leicht.maxRueckgang, 0.0)
        assertEquals(voll.sharpe, leicht.sharpe, 0.0)
        // die laufende Rechnung stimmt mit der Formel ueber die Liste ueberein
        assertEquals(StrategieLabor.sharpe(voll.renditen), voll.sharpe, 1e-12)
    }

    // Aussagekraft

    @Test fun aussageStufen() {
        assertEquals(StrategieLabor.Aussage.ZU_WENIG, StrategieLabor.aussage(9, 10.0))
        assertEquals(StrategieLabor.Aussage.ZU_WENIG, StrategieLabor.aussage(50, 1.9))
        assertEquals(StrategieLabor.Aussage.BEGRENZT, StrategieLabor.aussage(10, 2.0))
        assertEquals(StrategieLabor.Aussage.BEGRENZT, StrategieLabor.aussage(29, 20.0))
        assertEquals(StrategieLabor.Aussage.BEGRENZT, StrategieLabor.aussage(30, 4.9))
        assertEquals(StrategieLabor.Aussage.BRAUCHBAR, StrategieLabor.aussage(30, 5.0))
        assertEquals(StrategieLabor.Aussage.BRAUCHBAR, StrategieLabor.aussage(200, 30.0))
    }

    @Test fun aussageEinesErgebnisses() {
        val k = kerzen(handKurse)
        val e = sim(k, handZiel)!!
        // ein Geschaeft in vier Tagen
        assertEquals(StrategieLabor.Aussage.ZU_WENIG, StrategieLabor.aussage(e))
    }

    // Ertragsreihe

    private fun ereignis(tagIndex: Int, betrag: Double) = MarketApi.DividendEvent(beginn + tagIndex * tag, betrag)

    @Test fun ohneDividendenBleibtDieReiheGleich() {
        val k = kerzen(List(50) { 100.0 + it })
        assertTrue(StrategieLabor.ertragsreihe(k, emptyList()) === k)
        val e = StrategieLabor.ertragsreihe(k, listOf(ereignis(-5, 2.0)))
        assertEquals(k.map { it.close }, e.map { it.close })
    }

    @Test fun dividendWirdWiederAngelegtUndNurHaltenBekommtSie() {
        val k = kerzen(List(40) { 100.0 })
        val tr = StrategieLabor.ertragsreihe(k, listOf(ereignis(10, 5.0)))
        // ab dem Ex-Tag 5 % mehr, davor unveraendert
        assertEquals(100.0, tr[9].close, 1e-12)
        assertEquals(105.0, tr[10].close, 1e-12)
        assertEquals(105.0, tr.last().close, 1e-12)
        assertEquals(105.0, tr[10].open, 1e-12)
        val halten = sim(tr, BooleanArray(40) { true })!!
        assertEquals(10_500.0, halten.endwert, 1e-9)
        // Eine Regel, die ueber den Ex-Tag ausserhalb des Marktes ist, bekommt sie nicht
        val draussen = sim(tr, BooleanArray(40) { it < 4 || it > 15 })!!
        assertEquals(10_000.0 * 105.0 / 105.0, draussen.endwert, 1e-9)
        assertTrue(halten.endwert > draussen.endwert)
    }

    @Test fun mehrereDividendenWirkenHintereinander() {
        val k = kerzen(List(40) { 100.0 })
        val tr = StrategieLabor.ertragsreihe(k, listOf(ereignis(10, 5.0), ereignis(20, 5.0)))
        assertEquals(105.0, tr[15].close, 1e-12)
        assertEquals(100.0 * 1.05 * 1.05, tr[25].close, 1e-9)
    }

    @Test fun unplausibleAusschuettungenZaehlenNicht() {
        val k = kerzen(List(40) { 100.0 })
        // 80 bei einem Kurs von 100 ist ein Einheitenfehler und wird verworfen, 3 bleibt
        val tr = StrategieLabor.ertragsreihe(k, listOf(ereignis(10, 80.0), ereignis(20, 3.0)))
        assertEquals(100.0, tr[15].close, 1e-12)
        assertEquals(103.0, tr[25].close, 1e-12)
        // Null, negativ und nicht endlich
        val leer = StrategieLabor.ertragsreihe(k, listOf(ereignis(10, 0.0), ereignis(11, -1.0), ereignis(12, Double.NaN)))
        assertEquals(100.0, leer.last().close, 1e-12)
    }

    @Test fun ausschuettungNachDemEndeDerReiheBleibtAussen() {
        val k = kerzen(List(40) { 100.0 })
        val tr = StrategieLabor.ertragsreihe(k, listOf(ereignis(100, 3.0)))
        assertEquals(100.0, tr.last().close, 1e-12)
    }

    // Parameter-Landschaft

    private fun zufallsReihe(seed: Int, n: Int = 2000): List<Candle> {
        val r = kotlin.random.Random(seed)
        var p = 100.0
        return kerzen((0 until n).map {
            val u = r.nextDouble().coerceAtLeast(1e-12)
            val v = r.nextDouble()
            p *= 1.0 + 0.012 * kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
            p
        })
    }

    @Test fun zellenGleichAnzahlUndBesteStimmt() {
        for (fam in listOf(StrategieLabor.Familie.DURCHSCHNITT, StrategieLabor.Familie.KREUZ, StrategieLabor.Familie.RSI)) {
            val l = StrategieLabor.lotterie(zufallsReihe(5), 0, StrategieLabor.Kosten(20.0), fam)!!
            assertEquals(fam.name, l.anzahl, l.zellen.size)
            assertEquals(StrategieLabor.varianten(fam).toSet(), l.zellen.map { it.regel }.toSet())
            val z = l.zellen.first { it.regel == l.beste }
            assertEquals(l.besteLern.renditeJahr!!, z.lernJahr!!, 1e-12)
            assertEquals(l.bestePruef.renditeJahr!!, z.pruefJahr!!, 1e-12)
            // Die gewaehlte Variante hat die hoechste Sharpe-Quote im Lernzeitraum, also auch die beste Rendite
            // unter den Zellen nur dann, wenn beide zusammenfallen; mindestens muss sie in der Menge liegen
            assertTrue(l.zellen.all { it.lernJahr != null })
        }
    }

    @Test fun zellenRechnenDenPruefzeitraumNach() {
        val k = zufallsReihe(6)
        val kosten = StrategieLabor.Kosten(20.0)
        val l = StrategieLabor.lotterie(k, 0, kosten, StrategieLabor.Familie.DURCHSCHNITT)!!
        val d = StrategieLabor.Daten(k)
        val s = StrategieLabor.teilung(0, k.size)
        for (z in l.zellen.take(5)) {
            val ziel = StrategieLabor.ziel(d, z.regel)
            assertEquals(StrategieLabor.simulieren(d, ziel, 0, s, kosten)!!.renditeJahr!!, z.lernJahr!!, 1e-12)
            assertEquals(StrategieLabor.simulieren(d, ziel, s, k.size, kosten)!!.renditeJahr!!, z.pruefJahr!!, 1e-12)
        }
        assertEquals(l.haltenLern.renditeJahr, StrategieLabor.simulieren(d, BooleanArray(k.size) { true }, 0, s, kosten)!!.renditeJahr)
    }

    @Test fun nachbarnImKreuzRaster() {
        val zellen = StrategieLabor.varianten(StrategieLabor.Familie.KREUZ).map { StrategieLabor.Zelle(it, 0.0, 0.0) }
        fun kreuz(a: Int, b: Int) = StrategieLabor.Regel(StrategieLabor.Art.KREUZ, a, b)
        assertEquals(8, StrategieLabor.nachbarn(zellen, kreuz(50, 100)).size)
        // Ecke links oben im Raster: nur (5,30), (10,20), (10,30)
        val ecke = StrategieLabor.nachbarn(zellen, kreuz(5, 20)).map { it.regel }.toSet()
        assertEquals(setOf(kreuz(5, 30), kreuz(10, 20), kreuz(10, 30)), ecke)
        // Rand: a = 5, b mittendrin
        assertEquals(5, StrategieLabor.nachbarn(zellen, kreuz(5, 100)).size)
        // Die Variante selbst ist nie ihr Nachbar
        assertTrue(StrategieLabor.nachbarn(zellen, kreuz(50, 100)).none { it.regel == kreuz(50, 100) })
    }

    @Test fun nachbarnBeiDurchschnittUndRsi() {
        val sma = StrategieLabor.varianten(StrategieLabor.Familie.DURCHSCHNITT).map { StrategieLabor.Zelle(it, 0.0, 0.0) }
        fun s(a: Int) = StrategieLabor.Regel(StrategieLabor.Art.SMA, a)
        assertEquals(setOf(s(45), s(55)), StrategieLabor.nachbarn(sma, s(50)).map { it.regel }.toSet())
        assertEquals(setOf(s(10)), StrategieLabor.nachbarn(sma, s(5)).map { it.regel }.toSet())
        assertEquals(setOf(s(245)), StrategieLabor.nachbarn(sma, s(250)).map { it.regel }.toSet())
        val rsi = StrategieLabor.varianten(StrategieLabor.Familie.RSI).map { StrategieLabor.Zelle(it, 0.0, 0.0) }
        fun r(n: Int, a: Int, b: Int) = StrategieLabor.Regel(StrategieLabor.Art.RSI, a, b, n)
        val innen = StrategieLabor.nachbarn(rsi, r(14, 25, 75))
        assertEquals(8, innen.size)
        // gleiche Laenge n, nie eine andere
        assertTrue(innen.all { it.regel.n == 14 })
        assertEquals(3, StrategieLabor.nachbarn(rsi, r(7, 10, 60)).size)
        // eine Regel ohne Raster hat keine Nachbarn
        assertTrue(StrategieLabor.nachbarn(sma, StrategieLabor.Regel(StrategieLabor.Art.MACD)).isEmpty())
    }

    private fun ergebnisMit(rendite: Double?) = StrategieLabor.Ergebnis(
        endwert = 1.0, renditeJahr = rendite, maxRueckgang = 0.0, trades = 0, treffer = null, imMarkt = 0.0,
        verlauf = emptyList(), renditen = emptyList(), sharpe = 0.0, jahre = 1.0,
    )

    @Test fun nachbarVorsprungHandrechnung() {
        fun s(a: Int) = StrategieLabor.Regel(StrategieLabor.Art.SMA, a)
        val zellen = listOf(
            StrategieLabor.Zelle(s(45), 0.10, 0.02),
            StrategieLabor.Zelle(s(50), 0.20, 0.01),
            StrategieLabor.Zelle(s(55), 0.14, null),
        )
        val l = StrategieLabor.Lotterie(
            3, s(50), ergebnisMit(0.2), ergebnisMit(0.01), ergebnisMit(0.08), ergebnisMit(0.03), 0.5, 0.0, zellen,
        )
        val (lern, pruef) = StrategieLabor.nachbarVorsprung(l)
        // Lernen: ((0,10 - 0,08) + (0,14 - 0,08)) / 2, Pruefen: nur 45 hat einen Wert, 0,02 - 0,03
        assertEquals(0.04, lern!!, 1e-12)
        assertEquals(-0.01, pruef!!, 1e-12)
        // ohne Halten-Rendite keine Aussage
        val ohne = StrategieLabor.Lotterie(3, s(50), ergebnisMit(0.2), ergebnisMit(0.01), ergebnisMit(null), ergebnisMit(null), 0.5, 0.0, zellen)
        assertNull(StrategieLabor.nachbarVorsprung(ohne).first)
        assertNull(StrategieLabor.nachbarVorsprung(ohne).second)
    }

    // Zufallsvergleich

    private fun mitWechseln(n: Int = 2000, seed: Int = 11): List<Candle> {
        val r = kotlin.random.Random(seed)
        var p = 100.0
        return kerzen((0 until n).map { i ->
            val drift = if ((i / 120) % 2 == 0) 0.005 else -0.005
            val u = r.nextDouble().coerceAtLeast(1e-12)
            val v = r.nextDouble()
            p *= 1.0 + drift + 0.006 * kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
            p
        })
    }

    private fun wechsel(ziel: BooleanArray, von: Int, bis: Int): Int {
        var n = 0
        for (i in von + 1 until bis) if (ziel[i] != ziel[i - 1]) n++
        return n
    }

    @Test fun verschiebungNullErgibtDieRegelSelbst() {
        val ziel = BooleanArray(1000) { (it / 37) % 2 == 0 }
        val puffer = BooleanArray(1000)
        StrategieLabor.verschieben(ziel, puffer, 100, 900, 0)
        for (i in 100 until 900) assertEquals("Tag $i", ziel[i - 1], puffer[i - 1])
    }

    @Test fun verschiebungErhaeltMarktzeitUndGeschaefte() {
        val r = kotlin.random.Random(4)
        val ziel = BooleanArray(1000) { (it / 53) % 2 == 0 || r.nextInt(40) == 0 }
        val puffer = BooleanArray(1000)
        val start = 100
        val bis = 900
        val imMarkt = (start - 1 until bis - 1).count { ziel[it] }
        val wechsel = wechsel(ziel, start - 1, bis - 1)
        for (v in listOf(1, 80, 399, 400, 799)) {
            StrategieLabor.verschieben(ziel, puffer, start, bis, v)
            assertEquals("Marktzeit bei $v", imMarkt, (start - 1 until bis - 1).count { puffer[it] })
            // Am Schnitt kann hoechstens ein Wechsel dazukommen oder wegfallen
            assertTrue("Wechsel bei $v", kotlin.math.abs(wechsel(puffer, start - 1, bis - 1) - wechsel) <= 1)
        }
        // eine Verschiebung um v schiebt das Muster genau um v Tage
        StrategieLabor.verschieben(ziel, puffer, start, bis, 10)
        assertEquals(ziel[start - 1 + 10], puffer[start - 1])
        assertEquals(ziel[start - 1], puffer[bis - 1 - 10])
    }

    @Test fun zufallsvergleichIstMitFestemStartwertWiederholbar() {
        val k = mitWechseln()
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, StrategieLabor.Regel(StrategieLabor.Art.SMA, 20))
        val kosten = StrategieLabor.Kosten(20.0)
        val a = StrategieLabor.zufallsvergleich(d, z, 0, k.size, kosten, laeufe = 200, seed = 5L)!!
        val b = StrategieLabor.zufallsvergleich(d, z, 0, k.size, kosten, laeufe = 200, seed = 5L)!!
        assertTrue(a.verteilung.contentEquals(b.verteilung))
        assertEquals(a.anteilBesser, b.anteilBesser, 0.0)
        val c = StrategieLabor.zufallsvergleich(d, z, 0, k.size, kosten, laeufe = 200, seed = 6L)!!
        assertTrue(!a.verteilung.contentEquals(c.verteilung))
        // aufsteigend sortiert, Zahl der Laeufe stimmt
        assertEquals(200, a.laeufe)
        assertEquals(200, a.verteilung.size)
        for (i in 1 until a.verteilung.size) assertTrue(a.verteilung[i] >= a.verteilung[i - 1])
    }

    @Test fun zufallsvergleichRegelBeiTrendwechselBeatsZufall() {
        val k = mitWechseln()
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, StrategieLabor.Regel(StrategieLabor.Art.SMA, 20))
        val v = StrategieLabor.zufallsvergleich(d, z, 0, k.size, StrategieLabor.Kosten(20.0), laeufe = 300)!!
        // Das Muster passt zu den Kursen, verschoben verliert es den Bezug
        assertTrue("Anteil ${v.anteilBesser}", v.anteilBesser < 0.25)
        assertTrue(v.verteilung[v.verteilung.size / 2] < v.regelJahr)
        assertTrue(v.imMarkt > 0.2 && v.imMarkt < 0.9)
        assertTrue(v.trades > 5)
        assertNotNull(v.haltenJahr)
    }

    @Test fun zufallsvergleichBeiKonstanterReihe() {
        val k = kerzen(List(1200) { 100.0 })
        val d = StrategieLabor.Daten(k)
        val z = BooleanArray(1200) { (it / 50) % 2 == 0 }
        val v = StrategieLabor.zufallsvergleich(d, z, 0, k.size, ohneKosten, laeufe = 100)!!
        // Alles bleibt bei null: kein Muster ist besser
        assertEquals(0.0, v.regelJahr, 0.0)
        assertEquals(1.0, v.anteilBesser, 0.0)
    }

    @Test fun zufallsvergleichZuKurzOderAusserhalb() {
        val k = mitWechseln(n = 800)
        val d = StrategieLabor.Daten(k)
        val z = BooleanArray(k.size) { true }
        assertNull(StrategieLabor.zufallsvergleich(d, z, 400, k.size, ohneKosten))
        assertNull(StrategieLabor.zufallsvergleich(d, z, 0, k.size + 1, ohneKosten))
        assertNotNull(StrategieLabor.zufallsvergleich(d, z, 0, k.size, ohneKosten, laeufe = 20))
    }

    @Test fun zufallsvergleichStartNullGiltWieEins() {
        val k = mitWechseln()
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, StrategieLabor.Regel(StrategieLabor.Art.SMA, 20))
        val a = StrategieLabor.zufallsvergleich(d, z, 0, k.size, ohneKosten, laeufe = 50)!!
        val b = StrategieLabor.zufallsvergleich(d, z, 1, k.size, ohneKosten, laeufe = 50)!!
        assertEquals(a.regelJahr, b.regelJahr, 0.0)
        assertTrue(a.verteilung.contentEquals(b.verteilung))
    }

    @Test fun zufallsvergleichBrichtAb() {
        val k = mitWechseln()
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, StrategieLabor.Regel(StrategieLabor.Art.SMA, 20))
        var aufrufe = 0
        try {
            StrategieLabor.zufallsvergleich(d, z, 0, k.size, ohneKosten, laeufe = 100) {
                aufrufe++
                if (aufrufe == 3) throw kotlinx.coroutines.CancellationException("Testabbruch")
            }
            org.junit.Assert.fail("haette abbrechen muessen")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(3, aufrufe)
        }
    }

    // Walk-Forward

    // Rund 14 Jahre Kalendertage (365 Kerzen je Jahr) mit wechselnden Trends und Rauschen.
    private fun langeReihe(n: Int = 5200, seed: Int = 21): List<Candle> {
        val r = kotlin.random.Random(seed)
        var p = 100.0
        return kerzen((0 until n).map { i ->
            val drift = if ((i / 150) % 2 == 0) 0.003 else -0.0025
            val u = r.nextDouble().coerceAtLeast(1e-12)
            val v = r.nextDouble()
            p *= 1.0 + drift + 0.008 * kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
            p
        })
    }

    private val wfKosten = StrategieLabor.Kosten(20.0)

    @Test fun walkForwardFensterUndGrenzen() {
        val k = langeReihe()
        val wf = StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT)!!
        val t0 = k[StrategieLabor.EINSCHWINGEN].t
        val jahrMs = 365.25 * 86_400_000.0
        // 5200 Tage: erstes Pruefjahr nach 250 + 5 Jahren, danach 8 volle Jahre und ein Rest von gut 200 Tagen
        assertEquals(StrategieLabor.abIndex(k, t0 + (5 * jahrMs).toLong()), wf.fenster.first().pruefVon)
        assertEquals(k.size, wf.fenster.last().pruefBis)
        for (i in 1 until wf.fenster.size) assertEquals(wf.fenster[i - 1].pruefBis, wf.fenster[i].pruefVon)
        // Lernfenster rollierend: je fuenf Jahre, jedes ein Jahr spaeter
        for ((i, f) in wf.fenster.withIndex()) {
            assertEquals(StrategieLabor.abIndex(k, t0 + (i * jahrMs).toLong()), f.lernVon)
            assertTrue(f.pruefVon > f.lernVon)
            val lernTage = (k[f.pruefVon].t - k[f.lernVon].t) / 86_400_000.0
            assertEquals(5 * 365.25, lernTage, 2.0)
        }
        // volle Pruefjahre sind ein Jahr lang, nur das letzte ist kuerzer (mindestens 126 Tage)
        for (f in wf.fenster.dropLast(1)) assertEquals(365.25, (k[f.pruefBis].t - k[f.pruefVon].t) / 86_400_000.0, 2.0)
        assertTrue(wf.fenster.last().pruefBis - wf.fenster.last().pruefVon >= 126)
        assertEquals(StrategieLabor.varianten(StrategieLabor.Familie.DURCHSCHNITT).size, wf.varianten)
    }

    @Test fun walkForwardWaehltNurAusDemLernfenster() {
        val a = langeReihe()
        val ref = StrategieLabor.walkForward(a, 0, wfKosten, StrategieLabor.Familie.KREUZ)!!
        assertTrue(ref.fenster.size >= 4)
        val s = ref.fenster[2].pruefVon
        // Ab dem dritten Pruefjahr voellig andere Kurse: Wahl und Lernergebnis der Fenster 0 bis 2 bleiben gleich
        val b = a.take(s) + a.drop(s).map { it.copy(open = it.open * 3, high = it.high * 3, low = it.low * 3, close = it.close * 3) }
        val neu = StrategieLabor.walkForward(b, 0, wfKosten, StrategieLabor.Familie.KREUZ)!!
        for (i in 0..2) {
            assertEquals("Wahl $i", ref.fenster[i].regel, neu.fenster[i].regel)
            assertEquals("Lernen $i", ref.fenster[i].lern.endwert, neu.fenster[i].lern.endwert, 0.0)
        }
        // Auch die durchgehende Rechnung bis zum Fenster 2 ist unveraendert
        val zr = ref.zusammen.verlauf
        val zn = neu.zusammen.verlauf
        val bis = ref.fenster[2].pruefVon - ref.fenster[0].pruefVon
        for (i in 0 until bis) assertEquals(zr[i].second, zn[i].second, 0.0)
    }

    @Test fun walkForwardWahlIstDieBesteSharpeQuoteDesLernfensters() {
        val k = langeReihe()
        val wf = StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT)!!
        val d = StrategieLabor.Daten(k)
        val regeln = StrategieLabor.varianten(StrategieLabor.Familie.DURCHSCHNITT)
        for (f in wf.fenster.take(3)) {
            val sharpes = regeln.map { StrategieLabor.simulieren(d, StrategieLabor.ziel(d, it), f.lernVon, f.pruefVon, wfKosten, leicht = true)!!.sharpe }
            assertEquals(sharpes.max(), sharpes[regeln.indexOf(f.regel)], 0.0)
            assertEquals(sharpes.max(), f.lern.sharpe, 0.0)
        }
    }

    @Test fun walkForwardZusammengesetzteKurveIstDurchgehend() {
        val k = langeReihe()
        val wf = StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.RSI)!!
        val erster = wf.fenster.first().pruefVon
        val letzter = wf.fenster.last().pruefBis
        assertEquals(letzter - erster, wf.zusammen.verlauf.size)
        assertEquals(letzter - erster, wf.halten.verlauf.size)
        assertEquals(k[erster].t, wf.zusammen.verlauf.first().first)
        for (i in 1 until wf.zusammen.verlauf.size) assertTrue(wf.zusammen.verlauf[i].first > wf.zusammen.verlauf[i - 1].first)
        // Nachrechnung: die Lagen der gewaehlten Regeln aneinandergehaengt, eine Rechnung
        val d = StrategieLabor.Daten(k)
        val z = BooleanArray(k.size)
        for (f in wf.fenster) {
            val ziel = StrategieLabor.ziel(d, f.regel)
            for (i in f.pruefVon - 1 until f.pruefBis - 1) z[i] = ziel[i]
        }
        val nach = StrategieLabor.simulieren(d, z, erster, letzter, wfKosten)!!
        assertEquals(nach.endwert, wf.zusammen.endwert, 1e-9)
        assertEquals(nach.trades, wf.zusammen.trades)
        // Kaufen und Halten im selben Zeitraum
        val halten = StrategieLabor.simulieren(d, BooleanArray(k.size) { true }, erster, letzter, wfKosten)!!
        assertEquals(halten.endwert, wf.halten.endwert, 1e-9)
        // Die Jahre je Fenster haben frisches Kapital, besserJahre zaehlt Endwert gegen Halten
        assertEquals(wf.fenster.count { it.pruef.endwert > it.halten.endwert }, wf.besserJahre)
    }

    @Test fun walkForwardInSteigenderReiheNaheHalten() {
        val k = kerzen((0 until 5200).map { 100.0 * 1.0006.pow(it) })
        val wf = StrategieLabor.walkForward(k, 0, ohneKosten, StrategieLabor.Familie.DURCHSCHNITT)!!
        val verhaeltnis = wf.zusammen.endwert / wf.halten.endwert
        assertTrue("Verhaeltnis $verhaeltnis", verhaeltnis in 0.97..1.03)
    }

    @Test fun walkForwardVerankertLerntAbDemAnfang() {
        val k = langeReihe()
        val wf = StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT, verankert = true)!!
        for (f in wf.fenster) assertEquals(StrategieLabor.EINSCHWINGEN, f.lernVon)
        // Das Lernfenster waechst
        assertTrue(wf.fenster.last().pruefVon - wf.fenster.last().lernVon > wf.fenster.first().pruefVon - wf.fenster.first().lernVon)
    }

    @Test fun walkForwardZuWenigKerzen() {
        assertNull(StrategieLabor.walkForward(langeReihe(n = 1500), 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT))
        // ein volles Lernfenster, aber kein Pruefjahr mit 126 Tagen mehr
        val knapp = langeReihe(n = StrategieLabor.EINSCHWINGEN + (5 * 365.25).toInt() + 100)
        assertNull(StrategieLabor.walkForward(knapp, 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT))
        assertNull(StrategieLabor.walkForward(langeReihe(), -1, wfKosten, StrategieLabor.Familie.DURCHSCHNITT))
    }

    @Test fun walkForwardMitLueckenRichtetSichNachDerZeit() {
        // Jede dritte Kerze fehlt: die Fenster bleiben ein Kalenderjahr lang
        val voll = langeReihe(n = 6000)
        val k = voll.filterIndexed { i, _ -> i % 3 != 2 }
        val wf = StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT)!!
        for (f in wf.fenster.dropLast(1)) {
            assertEquals(365.25, (k[f.pruefBis].t - k[f.pruefVon].t) / 86_400_000.0, 4.0)
        }
        assertTrue(wf.fenster.size >= 5)
    }

    @Test fun walkForwardFortschrittUndAbbruch() {
        val k = langeReihe()
        val stand = ArrayList<Float>()
        StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT, fortschritt = { stand += it })
        assertEquals(1f, stand.last(), 0f)
        for (i in 1 until stand.size) assertTrue(stand[i] >= stand[i - 1])
        var aufrufe = 0
        try {
            StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.DURCHSCHNITT) {
                aufrufe++
                if (aufrufe == 70) throw kotlinx.coroutines.CancellationException("Testabbruch")
            }
            org.junit.Assert.fail("haette abbrechen muessen")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(70, aufrufe)
        }
    }

    @Test fun monatUndJahrAusZeitpunkt() {
        assertEquals(1 to 1970, StrategieLabor.monatJahr(0L))
        assertEquals(7 to 2017, StrategieLabor.monatJahr(1_500_000_000_000L))
        assertEquals(2 to 2000, StrategieLabor.monatJahr(951_782_400_000L))
        assertEquals(12 to 1969, StrategieLabor.monatJahr(-86_400_000L))
        assertEquals(12 to 2020, StrategieLabor.monatJahr(1_609_372_800_000L))
        assertEquals(1 to 2021, StrategieLabor.monatJahr(1_609_459_200_000L))
        assertEquals(3 to 2024, StrategieLabor.monatJahr(1_709_251_200_000L))
    }

    @Test fun walkForwardAlleVariantenUeberDreissigJahre() {
        // 30 Jahre (rund 11000 Kalendertage), 587 Varianten: Dauer als Anhaltspunkt fuer den Rechenaufwand
        val k = langeReihe(n = 11_000, seed = 5)
        val von = System.nanoTime()
        val wf = StrategieLabor.walkForward(k, 0, wfKosten, StrategieLabor.Familie.ALLE)!!
        val ms = (System.nanoTime() - von) / 1_000_000
        println("Walk-Forward Alle, 30 Jahre, " + wf.fenster.size + " Fenster: " + ms + " ms")
        assertEquals(587, wf.varianten)
        assertTrue(wf.fenster.size >= 24)
        assertTrue(wf.zusammen.endwert > 0)
    }
}
