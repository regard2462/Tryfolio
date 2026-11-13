package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor.Art
import de.troetenrepublik.app.engine.StrategieLabor.Kosten
import de.troetenrepublik.app.engine.StrategieLabor.Regel
import de.troetenrepublik.app.engine.Zufallspfade.Verfahren
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.ZufallTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

// Fassung 0.78: Zufallspfade, viele Startzeitpunkte und Sparplan-Variante im Strategietest.
class Fassung078U31ZufallTest {

    private val tagMs = 86_400_000L

    // i-ter Werktag ab Montag, 3. Januar 2000
    private fun zeit(i: Int): Long = (10_959L + (i / 5) * 7L + i % 5) * tagMs

    // Kerzen aus Tagesfaktoren; Eroeffnung, Hoch und Tief haengen vom Tag ab, damit jeder Tag erkennbar bleibt.
    private fun kerzen(faktoren: List<Double>, start: Double = 100.0): List<Candle> {
        var c = start
        val aus = ArrayList<Candle>()
        aus += Candle(zeit(0), c, c * 1.01, c * 0.99, c)
        faktoren.forEachIndexed { i, f ->
            val o = c * (1.0 + 0.001 * ((i % 7) - 3))
            c *= f
            aus += Candle(zeit(i + 1), o, maxOf(o, c) * 1.004, minOf(o, c) * 0.996, c)
        }
        return aus
    }

    private fun zufall(seed: Long, n: Int, drift: Double = 0.0003, vola: Double = 0.012): List<Candle> {
        val rng = PortfolioTools.Rng(seed)
        return kerzen(List(n) { exp(drift + vola * rng.gaussian()) })
    }

    // Trend in langen Abschnitten: abwechselnd 150 Tage aufwaerts und 60 Tage abwaerts, mit wenig Rauschen
    private fun trend(n: Int): List<Candle> {
        val rng = PortfolioTools.Rng(5L)
        return kerzen(List(n) { exp((if (it % 210 < 150) 0.0030 else -0.0045) + 0.004 * rng.gaussian()) })
    }

    private fun faktoren(k: List<Candle>, von: Int): List<Double> = (von until k.size).map { k[it].close / k[it - 1].close }

    // Gerundet, damit die neu ausgerechneten Faktoren eines Pfads den echten gleichen
    private fun rund(l: List<Double>) = l.map { Math.round(it * 1e10) }

    private val sma = Regel(Art.SMA, 50)
    private val ohneKosten = Kosten(0.0, 0.0)

    // ------------------------------------------------------------ Pfade

    @Test fun pfadeSindWiederholbarUndBehaltenDenAnfang() {
        val k = zufall(1L, 900)
        val b = Zufallspfade.Bausteine(k, 300, k.size)
        for (v in Verfahren.entries) {
            val a = Zufallspfade.pfad(b, v, PortfolioTools.Rng(3L), 20)
            val c = Zufallspfade.pfad(b, v, PortfolioTools.Rng(3L), 20)
            val anders = Zufallspfade.pfad(b, v, PortfolioTools.Rng(4L), 20)
            assertEquals(v.name, a, c)
            assertFalse(v.name, a == anders)
            assertEquals(k.size, a.size)
            assertEquals(k.subList(0, 300), a.subList(0, 300))
            assertEquals(k.map { it.t }, a.map { it.t })
            for (x in a) {
                assertTrue(x.close > 0 && x.open > 0)
                assertTrue(x.high >= maxOf(x.open, x.close) - 1e-9)
                assertTrue(x.low <= minOf(x.open, x.close) + 1e-9)
            }
        }
    }

    @Test fun einfacheZiehungNimmtGanzeTage() {
        val k = zufall(2L, 700)
        val b = Zufallspfade.Bausteine(k, 1, k.size)
        val p = Zufallspfade.pfad(b, Verfahren.EINFACH, PortfolioTools.Rng(9L))
        // Jeder Tag des Pfads ist ein echter Tag mit allen vier Kursen im Verhaeltnis zum Vortag
        val echte = (1 until k.size).map { i -> k[i - 1].close.let { v -> listOf(k[i].close / v, k[i].open / v, k[i].high / v, k[i].low / v) } }
        for (i in 1 until p.size) {
            val v = p[i - 1].close
            val tag = listOf(p[i].close / v, p[i].open / v, p[i].high / v, p[i].low / v)
            assertTrue(echte.any { e -> e.indices.all { abs(e[it] - tag[it]) < 1e-9 } })
        }
        // Mit Zuruecklegen: nicht jeder Tag kommt vor, die Reihenfolge ist eine andere
        assertTrue(rund(faktoren(p, 1)).toSet().size < rund(faktoren(k, 1)).toSet().size)
        assertFalse(rund(faktoren(p, 1)) == rund(faktoren(k, 1)))
    }

    @Test fun blockziehungHaeltNachbarnZusammen() {
        val k = zufall(3L, 2000)
        val b = Zufallspfade.Bausteine(k, 1, k.size)
        val echt = rund(faktoren(k, 1))
        val folge = HashSet<Pair<Long, Long>>()
        for (i in 1 until echt.size) folge += echt[i - 1] to echt[i]
        fun nachbarn(p: List<Candle>): Double {
            val f = rund(faktoren(p, 1))
            return (1 until f.size).count { (f[it - 1] to f[it]) in folge }.toDouble() / (f.size - 1)
        }
        val kurz = nachbarn(Zufallspfade.pfad(b, Verfahren.BLOCK, PortfolioTools.Rng(1L), 10))
        val lang = nachbarn(Zufallspfade.pfad(b, Verfahren.BLOCK, PortfolioTools.Rng(1L), 120))
        val einzeln = nachbarn(Zufallspfade.pfad(b, Verfahren.EINFACH, PortfolioTools.Rng(1L)))
        // Mittlere Blocklaenge 10: rund 90 % der Tage folgen ihrem echten Vortag, bei 120 rund 99 %
        assertTrue("kurz $kurz", kurz in 0.85..0.95)
        assertTrue("lang $lang", lang > 0.97)
        assertTrue("einzeln $einzeln", einzeln < 0.05)
    }

    @Test fun trendumkehrBehaeltGroesseUndFolge() {
        val k = zufall(4L, 1500, drift = 0.001)
        val b = Zufallspfade.Bausteine(k, 1, k.size)
        val p = Zufallspfade.pfad(b, Verfahren.UMKEHR, PortfolioTools.Rng(2L))
        val echt = faktoren(k, 1)
        val neu = faktoren(p, 1)
        var gedreht = 0
        var wechsel = 0
        var vorher = false
        for (i in echt.indices) {
            // Betrag der Tagesrendite im Logarithmus bleibt, nur das Vorzeichen kann drehen
            assertEquals(abs(ln(echt[i])), abs(ln(neu[i])), 1e-9)
            val d = abs(ln(echt[i])) > 1e-9 && abs(ln(neu[i]) + ln(echt[i])) < 1e-9
            if (d) gedreht++
            if (i > 0 && d != vorher) wechsel++
            vorher = d
        }
        assertTrue(gedreht in 150..1350)
        // Abschnitte von im Mittel 60 Tagen: weit weniger Wechsel als Tage
        assertTrue("wechsel $wechsel", wechsel in 5..80)
        // Gedrehter Tag: Hoch und Tief tauschen und umschliessen weiter Eroeffnung und Schluss
        val i = (1 until p.size).first { abs(ln(neu[it - 1]) + ln(echt[it - 1])) < 1e-9 && abs(ln(echt[it - 1])) > 1e-9 }
        assertEquals(k[i - 1].close / k[i].low, p[i].high / p[i - 1].close, 1e-9)
        assertEquals(k[i - 1].close / k[i].high, p[i].low / p[i - 1].close, 1e-9)
    }

    // ------------------------------------------------------------ Auswertung

    @Test fun auswertungZaehltAnteile() {
        val k = zufall(6L, 1600)
        val e = Zufallspfade.rechnen(k, 300, Kosten(10.0), sma, pfade = 120)!!
        val d = StrategieLabor.Daten(k)
        val echt = StrategieLabor.simulieren(d, StrategieLabor.ziel(d, sma), 300, k.size, Kosten(10.0))!!
        assertEquals(echt.renditeJahr!!, e.regelRendite, 1e-12)
        assertEquals(echt.maxRueckgang, e.regelRueckgang, 1e-12)
        assertEquals(Verfahren.entries, e.verfahren.map { it.verfahren })
        for (v in e.verfahren) {
            assertEquals(120, v.pfade)
            assertEquals(120, v.rendite.size)
            assertTrue(v.rendite.toList() == v.rendite.sorted() && v.rueckgang.toList() == v.rueckgang.sorted())
            assertEquals(v.rendite.count { it >= e.regelRendite - 1e-12 } / 120.0, v.mindestens, 1e-12)
            assertEquals(v.rueckgang.count { it <= e.regelRueckgang + 1e-12 } / 120.0, v.hoechstens, 1e-12)
            assertEquals(1.0, v.mindestens + v.lageRendite, 1e-12)
            assertEquals(1.0, v.hoechstens + v.lageRueckgang, 1e-12)
            assertTrue(v.beides <= minOf(v.mindestens, v.hoechstens) + 1e-12)
            assertTrue(v.beides >= v.mindestens + v.hoechstens - 1.0 - 1e-12)
            assertTrue(v.schlaegtRendite in 0.0..1.0 && v.schlaegtRueckgang in 0.0..1.0)
        }
        // Wiederholbar, und zu kurze Zeitraeume liefern nichts
        val nochmal = Zufallspfade.rechnen(k, 300, Kosten(10.0), sma, pfade = 120)!!
        assertEquals(e.verfahren.map { it.rendite.toList() }, nochmal.verfahren.map { it.rendite.toList() })
        assertNull(Zufallspfade.rechnen(k, k.size - 100, Kosten(10.0), sma, pfade = 10))
    }

    @Test fun kaufenUndHaltenSchlaegtSichNie() {
        val k = zufall(7L, 900)
        val e = Zufallspfade.rechnen(k, 1, Kosten(10.0), Regel(Art.HALTEN), pfade = 40)!!
        for (v in e.verfahren) {
            assertEquals(0.0, v.schlaegtRendite, 0.0)
            assertEquals(0.0, v.schlaegtRueckgang, 0.0)
        }
        // Die Trendumkehr laesst die Groesse der Bewegungen, die Rendite streut um die echte
        assertTrue(e.verfahren[2].rendite.first() < e.regelRendite)
    }

    @Test fun trendregelVerliertOhneZeitlicheOrdnung() {
        // Lange Trends: der Durchschnitt schlaegt Halten in der echten Geschichte. Einzeln gezogene Tage haben keinen
        // Trend mehr, dort liegt die Regel nach Kosten selten vorn; in langen Bloecken bleibt er.
        val k = trend(2400)
        val e = Zufallspfade.rechnen(k, 260, Kosten(10.0), sma, pfade = 200)!!
        assertTrue(e.regelRendite > e.haltenRendite)
        val einfach = e.verfahren[0]
        val block = e.verfahren[1]
        assertTrue("einfach ${einfach.schlaegtRendite}", einfach.schlaegtRendite < 0.3)
        assertTrue("block ${block.schlaegtRendite} einfach ${einfach.schlaegtRendite}", block.schlaegtRendite > einfach.schlaegtRendite + 0.2)
        val kern = Zufallspfade.kernRendite(e)
        assertTrue(kern.echtVorn)
        assertTrue(Verfahren.EINFACH in kern.faellt)
        assertEquals(Verfahren.entries.toSet(), (kern.haelt + kern.faellt).toSet())
    }

    @Test fun kernaussageJeGroesse() {
        fun a(v: Verfahren, r: Double, d: Double) = Zufallspfade.Auswertung(v, 10, DoubleArray(10), DoubleArray(10), 0.0, 0.0, 0.0, 0.0, 0.0, r, d)
        val e = Zufallspfade.Ergebnis(0.08, 0.20, 0.06, 0.45, listOf(a(Verfahren.EINFACH, 0.5, 0.9), a(Verfahren.BLOCK, 0.7, 0.95), a(Verfahren.UMKEHR, 0.49, 0.8)))
        val r = Zufallspfade.kernRendite(e)
        assertTrue(r.echtVorn)
        assertEquals(listOf(Verfahren.EINFACH, Verfahren.BLOCK), r.haelt)
        assertEquals(listOf(Verfahren.UMKEHR), r.faellt)
        val d = Zufallspfade.kernRueckgang(e)
        assertTrue(d.echtVorn)
        assertTrue(d.faellt.isEmpty())
        val schwach = Zufallspfade.Ergebnis(0.04, 0.50, 0.06, 0.45, e.verfahren)
        assertFalse(Zufallspfade.kernRendite(schwach).echtVorn)
        assertFalse(Zufallspfade.kernRueckgang(schwach).echtVorn)
    }

    @Test fun quantilUndPfadzahl() {
        val s = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(1.0, Zufallspfade.quantil(s, 0.0), 1e-12)
        assertEquals(3.0, Zufallspfade.quantil(s, 0.5), 1e-12)
        assertEquals(4.5, Zufallspfade.quantil(s, 0.875), 1e-12)
        assertEquals(5.0, Zufallspfade.quantil(s, 1.0), 1e-12)
        assertEquals(1000, Zufallspfade.pfadzahl(252))
        assertEquals(240, Zufallspfade.pfadzahl(2500))
        assertEquals(200, Zufallspfade.pfadzahl(12_000))
    }

    @Test fun rechenzeitBleibtKlein() {
        // Grobe Messung fuer die Pfadzahl: zehn und vierzig Jahre Tageskurse, alle drei Verfahren
        for (n in listOf(2520, 10_080)) {
            val k = zufall(8L, n)
            val t = System.nanoTime()
            assertNotNull(Zufallspfade.rechnen(k, 1, Kosten(10.0), Regel(Art.KREUZ, 50, 200)))
            val ms = (System.nanoTime() - t) / 1_000_000
            println("Zufallspfade $n Tage, ${Zufallspfade.pfadzahl(n)} Pfade je Verfahren: $ms ms")
            assertTrue("$ms ms", ms < 20_000)
        }
    }

    // ------------------------------------------------------------ Startzeitpunkte

    @Test fun startfensterJeMonat() {
        // Rund 12 Jahre mit gleichmaessigem Wachstum
        val f = 1.0004
        val k = kerzen(List(3130) { f })
        val liste = Startfenster.rechnen(k, ohneKosten, sma)
        // 20 Jahre passen nicht, 10 Jahre nur mit Starts aus dem ersten Jahr nach dem Einschwingen
        assertEquals(listOf(3, 5, 10), liste.map { it.jahre })
        val d3 = liste[0]
        val monate = SparplanRegel.monate(k)
        val starts = (250 until k.size).filter { monate[it] != monate[it - 1] && k[it].t + (3 * 365.25 * tagMs).toLong() <= k.last().t }
        assertEquals(starts.size, d3.fenster)
        assertTrue(liste[2].fenster in 12..13)
        // Halten waechst in jedem Fenster gleich: 260,9 Handelstage je Jahr
        val jahr = f.pow(5 * 365.25 / 7) - 1.0
        assertEquals(jahr, d3.haltenRendite.median, 0.002)
        assertEquals(d3.haltenRendite.min, d3.haltenRendite.max, 0.004)
        assertEquals(0.0, d3.haltenRueckgang.max, 0.005)
        // Der Kurs liegt immer ueber dem Durchschnitt: die Regel ist ab dem ersten Tag investiert und nie vorn
        assertEquals(0, d3.vorn)
        assertEquals(d3.haltenRendite.median, d3.regelRendite.median, 1e-9)
        // Kaufen und Halten gegen sich selbst
        assertEquals(0, Startfenster.rechnen(k, ohneKosten, Regel(Art.HALTEN))[0].vorn)
        // Zu kurze Reihe: keine Haltedauer
        assertTrue(Startfenster.rechnen(k.take(700), ohneKosten, sma).isEmpty())
    }

    @Test fun startfensterZaehltVorsprung() {
        val k = trend(3200)
        val liste = Startfenster.rechnen(k, Kosten(10.0), sma)
        val d = liste.first { it.jahre == 3 }
        assertTrue(d.fenster >= 12)
        // Lange Trends mit tiefen Rueckschlaegen: die Regel liegt meist vorn und faellt weniger tief
        assertTrue("vorn ${d.vorn} von ${d.fenster}", d.vorn > d.fenster / 2)
        assertTrue(d.regelRueckgang.median < d.haltenRueckgang.median)
        assertTrue(d.regelRendite.min <= d.regelRendite.q1 && d.regelRendite.q1 <= d.regelRendite.median)
        assertTrue(d.regelRendite.median <= d.regelRendite.q3 && d.regelRendite.q3 <= d.regelRendite.max)
        // Mit Sparplan: gleiche Fenster, Rendite geldgewichtet
        val sp = Startfenster.rechnen(k, Kosten(10.0), sma, sparplan = true).first { it.jahre == 3 }
        assertEquals(d.fenster, sp.fenster)
        assertTrue(sp.haltenRendite.median.isFinite() && sp.regelRendite.median.isFinite())
    }

    // ------------------------------------------------------------ Sparplan

    @Test fun sturesSparenKauftJedeRate() {
        val f = 1.0003
        val k = kerzen(List(1300) { f })
        val d = StrategieLabor.Daten(k)
        val immer = BooleanArray(k.size) { true }
        val e = SparplanRegel.simulieren(d, immer, 0, k.size, ohneKosten, rate = 100.0)!!
        val monate = SparplanRegel.monate(k)
        val kauftage = k.indices.filter { it == 0 || monate[it] != monate[it - 1] }
        assertEquals(kauftage.size, e.kaeufe)
        assertEquals(0, e.verkaeufe)
        assertEquals(100.0 * kauftage.size, e.eingezahlt, 1e-9)
        assertEquals(kauftage.sumOf { 100.0 / k[it].open } * k.last().close, e.endwert, 1e-6)
        assertEquals(1.0, e.imMarkt, 1e-12)
        assertEquals(k.size, e.verlauf.size)
        assertEquals(e.eingezahlt, e.einzahlungen.last(), 1e-9)
        // Gleichmaessiges Wachstum: der interne Zinsfuss ist die Jahresrendite des Kurses, ohne Rueckgang
        assertEquals(f.pow(5 * 365.25 / 7) - 1.0, e.zins!!, 0.003)
        assertEquals(0.0, e.maxRueckgang, 0.005)
        // Mit Gebuehr bleibt weniger
        val mit = SparplanRegel.simulieren(d, immer, 0, k.size, Kosten(0.0, 1.0), rate = 100.0)!!
        assertEquals(kauftage.sumOf { 99.0 / k[it].open } * k.last().close, mit.endwert, 1e-6)
    }

    @Test fun signalAusSammeltBargeld() {
        // Kurs bleibt bei 100. Signal bis Tag 99 aus, ab Tag 100 an, ab Tag 200 wieder aus
        val k = kerzen(List(300) { 1.0 }).map { it.copy(open = 100.0, high = 100.0, low = 100.0) }
        val d = StrategieLabor.Daten(k)
        val ziel = BooleanArray(k.size) { it in 100..199 }
        val monate = SparplanRegel.monate(k)
        fun raten(bis: Int) = (0..bis).count { it == 0 || monate[it] != monate[it - 1] }
        val e = SparplanRegel.simulieren(d, ziel, 0, k.size, Kosten(0.0, 1.0), rate = 100.0)!!
        // Erster Kauf am Tag 101 mit allen bis dahin angesammelten Raten in einer Order
        val bisEinstieg = raten(101)
        val imSignal = raten(200) - bisEinstieg
        assertEquals(1 + imSignal, e.kaeufe)
        assertEquals(1, e.verkaeufe)
        // Je Order 1 Euro, sonst geht nichts verloren
        assertEquals(e.eingezahlt - e.kaeufe - e.verkaeufe, e.endwert, 1e-6)
        assertEquals(100.0 * raten(k.size - 1), e.eingezahlt, 1e-9)
        assertEquals(100.0 / k.size, e.imMarkt, 1e-12)
        // Bis zum Einstieg liegt alles als Bargeld da
        assertEquals(100.0 * raten(100), e.verlauf[100], 1e-9)
        // Einzahlungen sind kein Gewinn: die bereinigte Entwicklung faellt nur um die Gebuehren
        assertTrue(e.maxRueckgang < 0.02)
        // Leichte Rechnung: gleiche Zahlen ohne Verlauf
        val leicht = SparplanRegel.simulieren(d, ziel, 0, k.size, Kosten(0.0, 1.0), rate = 100.0, leicht = true)!!
        assertEquals(e.endwert, leicht.endwert, 1e-9)
        assertTrue(leicht.verlauf.isEmpty())
    }

    @Test fun sparplanGegenSturesSparen() {
        val k = trend(2400)
        val (r, s) = SparplanRegel.vergleichen(k, 260, Kosten(10.0), sma)!!
        assertEquals(s.eingezahlt, r.eingezahlt, 1e-9)
        assertTrue(r.verkaeufe > 0 && r.imMarkt < 1.0)
        assertEquals(0, s.verkaeufe)
        assertTrue(r.maxRueckgang < s.maxRueckgang)
        assertNotNull(r.zins)
        // Kaufen und Halten als Regel ist stures Sparen
        val (h, s2) = SparplanRegel.vergleichen(k, 260, Kosten(10.0), Regel(Art.HALTEN))!!
        assertEquals(s2.endwert, h.endwert, 1e-9)
        assertNull(SparplanRegel.vergleichen(k, k.size - 5, Kosten(10.0), sma))
    }

    // ------------------------------------------------------------ Texte

    @Test fun texteInBeidenSprachen() {
        assertEquals(ZufallTexte.schluessel(true), ZufallTexte.schluessel(false))
        for (k in ZufallTexte.schluessel(true)) for (s in listOf(De, En)) {
            val t = s.st(k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains("—") || t.contains("–") || t.contains("…") || t.contains("{"))
        }
        for (v in Verfahren.entries) for (s in listOf(De, En)) {
            assertTrue(s.st("zp." + v.name).isNotBlank())
            assertTrue(s.st("zp.ohne." + v.name).isNotBlank())
        }
        assertEquals("Regel lag in 7 von 12 Startzeitpunkten vorn", De.st("sf.kern", listOf("7", "12")))
        assertEquals("Der Renditevorsprung verschwindet ohne Trend", De.st("zp.r.teils", listOf(De.st("zp.ohne.UMKEHR"))))
        // Die alten Schluessel und unbekannte bleiben wie bisher
        assertEquals("Stopp bei 2 ATR", De.st("atr.stopp", listOf("2")))
        assertEquals("", De.st("gibt.es.nicht"))
    }
}
