package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor.Art
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

// Regel Zielvolatilitaet im Strategietest: Positionsgroesse = Zielschwankung durch gemessene Schwankung, gedeckelt.
class Fassung078U41ZielvolaTest {
    private val tagMs = 86_400_000L
    private val regel = StrategieLabor.REGELN.single { it.art == Art.ZIELVOLA }
    private val ohneKosten = StrategieLabor.Kosten(0.0, 0.0)

    // Kerzen mit Eroeffnung = Schluss des Vortags
    private fun reihe(schluss: List<Double>) = schluss.mapIndexed { i, c -> Candle((20_000L + i) * tagMs, if (i == 0) c else schluss[i - 1], c, c, c) }

    private fun ruhig(n: Int, start: Double = 100.0) = (0 until n).map { start * Math.pow(1.001, it.toDouble()) }

    // Abwechselnd +3 % und -3 %
    private fun unruhig(n: Int, start: Double = 100.0): List<Double> {
        var c = start
        return (0 until n).map { i -> if (i > 0) c *= if (i % 2 == 1) 1.03 else 0.97; c }
    }

    @Test fun regelInDerListe() {
        assertEquals(StrategieLabor.Regel(Art.ZIELVOLA, 15, 100, 20), regel)
        assertEquals(regel, StrategieLabor.REGELN.last())
        assertTrue(regel in StrategieLabor.ZUSATZ)
        assertEquals(Handelsstil.BUY_AND_HOLD, Handelsstil.von(Art.ZIELVOLA))
        assertTrue(StrategieLabor.ohneSignal(regel) && StrategieLabor.ohneSignal(StrategieLabor.REGELN.first()))
        assertFalse(StrategieLabor.ohneSignal(StrategieLabor.REGELN[1]))
        assertFalse(RegelGlocke.moeglich(regel))
        assertEquals(regel, Vergleichsrechnung.laborRegel(Vergleichsrechnung.laborSchluessel(regel)))
        assertEquals(0.10, Zielvolatilitaet.BAND, 0.0)
    }

    @Test fun schwankungUndAnteil() {
        // Zwei Renditen +10 % und -10 %: Stichprobenstreuung 0,2 / Wurzel 2, mal Wurzel 252
        val s = Zielvolatilitaet.schwankung(listOf(100.0, 110.0, 99.0), 2)
        assertNull(s[0])
        assertNull(s[1])
        assertEquals(0.2 / sqrt(2.0) * sqrt(252.0), s[2]!!, 1e-9)
        assertTrue(Zielvolatilitaet.schwankung(listOf(1.0, 2.0, 3.0), 1).all { it == null })
        assertEquals(0.0, Zielvolatilitaet.schwankung(listOf(100.0, 100.0, 100.0, 100.0), 3).last()!!, 0.0)
        assertEquals(0.5, Zielvolatilitaet.anteil(0.15, 0.30, 1.0), 1e-12)
        assertEquals(1.0, Zielvolatilitaet.anteil(0.15, 0.10, 1.0), 0.0)
        assertEquals(0.8, Zielvolatilitaet.anteil(0.15, 0.10, 0.8), 0.0)
        assertEquals(1.0, Zielvolatilitaet.anteil(0.15, 0.0, 1.0), 0.0)
        assertEquals(0.0, Zielvolatilitaet.anteil(0.15, null, 1.0), 0.0)
        val a = Zielvolatilitaet.anteile(unruhig(60), regel)
        assertEquals(0.0, a[19], 0.0)
        // 3 % am Tag sind rund 49 % im Jahr: Anteil rund 0,31
        assertEquals(0.15 / (sqrt(20.0 / 19.0 * 0.0009) * sqrt(252.0)), a[59], 0.01)
    }

    @Test fun ruhigerMarktVollInvestiert() {
        val k = reihe(ruhig(200))
        val d = StrategieLabor.Daten(k)
        val e = Zielvolatilitaet.simulieren(d, regel, 30, k.size, ohneKosten)!!
        // Ein Kauf zur ersten Eroeffnung, danach wie Kaufen und Halten
        assertEquals(1, e.trades)
        assertEquals(StrategieLabor.START * k.last().close / k[30].open, e.endwert, 1e-6)
        assertEquals(1.0, e.imMarkt, 1e-9)
        assertNull(e.treffer)
        assertTrue(e.geschaefte.isEmpty())
        assertEquals(k.size - 30, e.verlauf.size)
        val leicht = Zielvolatilitaet.simulieren(d, regel, 30, k.size, ohneKosten, leicht = true)!!
        assertEquals(e.endwert, leicht.endwert, 0.0)
        assertEquals(e.sharpe, leicht.sharpe, 1e-12)
        assertTrue(leicht.verlauf.isEmpty() && leicht.renditen.isEmpty())
        assertNull(Zielvolatilitaet.simulieren(d, regel, 0, 1, ohneKosten))
        assertNull(Zielvolatilitaet.simulieren(d, regel, 0, k.size + 1, ohneKosten))
    }

    @Test fun unruhigerMarktKleinePosition() {
        val k = reihe(unruhig(400))
        val d = StrategieLabor.Daten(k)
        val e = Zielvolatilitaet.simulieren(d, regel, 30, k.size, ohneKosten)!!
        val halten = StrategieLabor.simulieren(d, StrategieLabor.ziel(d, StrategieLabor.REGELN.first()), 30, k.size, ohneKosten)!!
        assertTrue(e.imMarkt > 0.2 && e.imMarkt < 0.45)
        assertTrue(e.maxRueckgang < halten.maxRueckgang)
        // Das Band haelt die Zahl der Orders klein
        assertTrue(e.trades in 1..5)
        // Ohne Band wird fast jeden Tag angepasst
        val ohneBand = Zielvolatilitaet.simulieren(d, regel, 30, k.size, ohneKosten, band = 0.0)!!
        assertTrue(ohneBand.trades > 100)
    }

    @Test fun wechselVonRuhigZuUnruhig() {
        val a = ruhig(120)
        val k = reihe(a + unruhig(120, a.last()).drop(1))
        val d = StrategieLabor.Daten(k)
        val e = Zielvolatilitaet.simulieren(d, regel, 30, k.size, ohneKosten)!!
        // Erst voll investiert, nach dem Umschwung verkleinert
        assertTrue(e.trades >= 2)
        assertTrue(e.imMarkt > 0.45 && e.imMarkt < 0.95)
        val mit = Zielvolatilitaet.simulieren(d, regel, 30, k.size, StrategieLabor.Kosten(20.0))!!
        assertTrue(mit.endwert < e.endwert)
    }

    @Test fun imStrategietest() {
        val a = ruhig(300)
        val k = reihe(a + unruhig(300, a.last()).drop(1))
        val kosten = StrategieLabor.Kosten(20.0)
        val berichte = StrategieLabor.pruefen(k, 30, kosten)
        assertEquals(StrategieLabor.REGELN.size, berichte.size)
        val b = berichte.single { it.regel == regel }
        val d = StrategieLabor.Daten(k)
        assertEquals(Zielvolatilitaet.simulieren(d, regel, 30, k.size, kosten)!!.endwert, b.gesamt.endwert, 0.0)
        // Ein gewaehlter Ausstieg aendert die Regel nicht
        val mitStopp = StrategieLabor.pruefen(k, 30, kosten, listOf(regel), StrategieLabor.Ausstieg(StrategieLabor.AusstiegArt.STOPP, 5)).single()
        assertEquals(b.gesamt.endwert, mitStopp.gesamt.endwert, 0.0)
        assertEquals(Handelsstil.BUY_AND_HOLD, Handelsstil.von(Art.ZIELVOLA, b.gesamt))
        assertNull(StrategieLabor.kennzahlen(b.gesamt.geschaefte))
        // Im Vergleich der Verlaeufe dieselbe Rechnung
        val verlauf = Vergleichsrechnung.laborReihe(k, regel, kosten)!!
        assertEquals(Zielvolatilitaet.simulieren(d, regel, 0, k.size, kosten)!!.endwert, verlauf.last().second, 0.0)
        val halten = berichte.first().gesamt
        assertTrue(b.gesamt.endwert != halten.endwert)
    }

    @Test fun texte() {
        for (s in listOf(De, En)) {
            val name = s.slRegel("ZIELVOLA", regel.a, regel.b, regel.n)
            val kurz = s.slRegelKurz("ZIELVOLA", regel.a, regel.b, regel.n)
            val info = s.slRegelInfo("ZIELVOLA")
            assertTrue(name.contains("15") && kurz.contains("15") && name != "ZIELVOLA" && kurz != "ZIELVOLA")
            // Die Erklaerung nennt die Werte, mit denen gerechnet wird
            assertTrue(info.contains("15") && info.contains("20") && info.contains("100") && info.contains("10 "))
            assertTrue(info.contains("Moreira"))
            for (t in listOf(name, kurz, info)) assertFalse(t, t.contains('—') || t.contains('–') || t.contains('?') || t.contains('…'))
        }
    }
}
