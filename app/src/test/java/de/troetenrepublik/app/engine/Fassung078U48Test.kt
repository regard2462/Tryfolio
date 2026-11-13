package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor.Art
import de.troetenrepublik.app.engine.StrategieLabor.Kosten
import de.troetenrepublik.app.engine.StrategieLabor.Regel
import de.troetenrepublik.app.engine.Zufallspfade.Verfahren
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// Fassung 0.78: Zufallspfade und Startfenster mit Signalwert und Ausweichwert (Taktik).
class Fassung078U48Test {

    private val tagMs = 86_400_000L

    // i-ter Werktag ab Montag, 3. Januar 2000
    private fun zeit(i: Int): Long = (10_959L + (i / 5) * 7L + i % 5) * tagMs

    private fun reihe(seed: Long, n: Int, drift: Double, vola: Double): List<Candle> {
        val rng = PortfolioTools.Rng(seed)
        var c = 100.0
        val aus = ArrayList<Candle>()
        aus += Candle(zeit(0), c, c * 1.01, c * 0.99, c)
        for (i in 1..n) {
            val o = c * (1.0 + 0.001 * ((i % 7) - 3))
            c *= exp(drift + vola * rng.gaussian())
            aus += Candle(zeit(i), o, max(o, c) * 1.004, min(o, c) * 0.996, c)
        }
        return aus
    }

    private val sma = Regel(Art.SMA, 50)
    private val ohneKosten = Kosten(0.0, 0.0)

    // Der bisherige Pfadbau (vor der Trennung in Zug und Pfad), als Vergleich fuer bitgleiche Ergebnisse.
    private fun altPfad(b: Zufallspfade.Bausteine, verfahren: Verfahren, rng: PortfolioTools.Rng, block: Int): List<Candle> {
        val m = b.tage
        val aus = ArrayList<Candle>(b.bis)
        for (i in 0 until b.start) aus.add(b.kerzen[i])
        var vor = b.kerzen[b.start - 1].close
        val wechsel = 1.0 / max(block, 1)
        var quelle = 0
        var gedreht = verfahren == Verfahren.UMKEHR && rng.nextDouble() < 0.5
        for (j in 0 until m) {
            when (verfahren) {
                Verfahren.EINFACH -> quelle = (rng.nextDouble() * m).toInt().coerceIn(0, m - 1)
                Verfahren.BLOCK -> quelle = if (j == 0 || rng.nextDouble() < wechsel) (rng.nextDouble() * m).toInt().coerceIn(0, m - 1) else (quelle + 1) % m
                Verfahren.UMKEHR -> {
                    quelle = j
                    if (j > 0 && rng.nextDouble() < wechsel) gedreht = !gedreht
                }
            }
            val c: Double
            val o: Double
            val h: Double
            val l: Double
            if (gedreht) {
                c = 1.0 / b.schluss[quelle]; o = 1.0 / b.eroeffnung[quelle]; h = 1.0 / b.tief[quelle]; l = 1.0 / b.hoch[quelle]
            } else {
                c = b.schluss[quelle]; o = b.eroeffnung[quelle]; h = b.hoch[quelle]; l = b.tief[quelle]
            }
            val echt = b.kerzen[b.start + j]
            aus.add(Candle(echt.t, vor * o, vor * h, vor * l, vor * c, echt.volume))
            vor *= c
        }
        return aus
    }

    private fun gleich(a: Zufallspfade.Ergebnis, b: Zufallspfade.Ergebnis) {
        assertEquals(a.regelRendite, b.regelRendite, 0.0)
        assertEquals(a.haltenRueckgang, b.haltenRueckgang, 0.0)
        assertEquals(a.verfahren.size, b.verfahren.size)
        a.verfahren.zip(b.verfahren).forEach { (x, y) ->
            assertArrayEquals(x.rendite, y.rendite, 0.0)
            assertArrayEquals(x.rueckgang, y.rueckgang, 0.0)
            assertEquals(x.mindestens, y.mindestens, 0.0)
            assertEquals(x.schlaegtRendite, y.schlaegtRendite, 0.0)
        }
    }

    // (a) Ohne Taktik bitgleich zum bisherigen Verfahren
    @Test fun ohneTaktikBitgleich() {
        val k = reihe(1L, 900, 0.0003, 0.012)
        val b = Zufallspfade.Bausteine(k, 300, k.size)
        for (v in Verfahren.entries) {
            val a = Zufallspfade.pfad(b, v, PortfolioTools.Rng(11L), 20)
            val c = altPfad(b, v, PortfolioTools.Rng(11L), 20)
            assertEquals(c, a)
        }
        // Zug und Pfad verbrauchen den Zufallsstrom wie der alte Bau: zwei Pfade hintereinander bleiben gleich
        for (v in Verfahren.entries) {
            val r1 = PortfolioTools.Rng(5L)
            val r2 = PortfolioTools.Rng(5L)
            val a1 = Zufallspfade.pfad(b, v, r1, 20)
            val a2 = Zufallspfade.pfad(b, v, r1, 20)
            assertEquals(altPfad(b, v, r2, 20), a1)
            assertEquals(altPfad(b, v, r2, 20), a2)
        }
        // Eine Taktik ohne Signal und Ausweich ist keine
        val ohne = Zufallspfade.rechnen(k, 300, ohneKosten, sma, pfade = 30)
        val leer = Zufallspfade.rechnen(k, 300, ohneKosten, sma, pfade = 30, taktik = Taktik.Reihen(k, null, null))
        assertNotNull(ohne)
        gleich(ohne!!, leer!!)
    }

    // (b) Mit Signalwert ziehen alle Reihen dieselben Tage
    @Test fun signalwertNutztDieselbenIndizes() {
        val handel = reihe(2L, 900, 0.0003, 0.012)
        val signal = reihe(3L, 900, 0.0002, 0.015)
        val ausweich = reihe(4L, 900, 0.0001, 0.003)
        val t = Taktik.reihen(handel, signal, ausweich)
        val n = t.handel.size
        val start = 300
        val bh = Zufallspfade.Bausteine(t.handel, start, n)
        val bs = Zufallspfade.Bausteine(t.signal!!, start, n)
        val ba = Zufallspfade.Bausteine(t.ausweich!!, start, n)
        for (v in Verfahren.entries) {
            val z = Zufallspfade.zug(bh.tage, v, PortfolioTools.Rng(9L), 20)
            val ph = Zufallspfade.pfad(bh, z)
            val ps = Zufallspfade.pfad(bs, z)
            val pa = Zufallspfade.pfad(ba, z)
            assertEquals(n, ps.size)
            assertEquals(n, pa.size)
            for (j in 0 until bh.tage) {
                val i = start + j
                val q = z.quelle[j]
                val sg = z.gedreht[j]
                // Jeder Tag der drei Pfade stammt aus demselben Baustein und ist gleich gedreht
                fun faktor(p: List<Candle>) = p[i].close / p[i - 1].close
                val fh = if (sg) 1.0 / bh.schluss[q] else bh.schluss[q]
                val fs = if (sg) 1.0 / bs.schluss[q] else bs.schluss[q]
                val fa = if (sg) 1.0 / ba.schluss[q] else ba.schluss[q]
                assertEquals(fh, faktor(ph), 1e-9)
                assertEquals(fs, faktor(ps), 1e-9)
                assertEquals(fa, faktor(pa), 1e-9)
            }
            // Die Zeitpunkte bleiben die echten
            assertEquals(t.handel.map { it.t }, ps.map { it.t })
        }
        // Gesamtrechnung: ist das Signal der gehandelte Wert selbst, kommt dasselbe heraus wie ohne Taktik
        val k = t.handel
        val ohne = Zufallspfade.rechnen(k, start, ohneKosten, sma, pfade = 30)
        val selbst = Zufallspfade.rechnen(k, start, ohneKosten, sma, pfade = 30, taktik = Taktik.Reihen(k, k, null))
        gleich(ohne!!, selbst!!)
        // Ein anderes Signal verandert das Ergebnis
        val mitSignal = Zufallspfade.rechnen(k, start, ohneKosten, sma, pfade = 30, taktik = t)
        assertNotNull(mitSignal)
        assertTrue(mitSignal!!.verfahren.zip(ohne.verfahren).any { (x, y) -> !x.rendite.contentEquals(y.rendite) })
    }

    // (c) Startfenster mit Taktik rechnet wie die Hauptrechnung
    @Test fun startfensterMitTaktikWieHauptrechnung() {
        val handel = reihe(6L, 3300, 0.0004, 0.011)
        val signal = reihe(7L, 3300, 0.0002, 0.014)
        val ausweich = reihe(8L, 3300, 0.0001, 0.002)
        val t = Taktik.reihen(handel, signal, ausweich)
        val k = t.handel
        val n = k.size
        val d = t.daten(n)
        val ziel = StrategieLabor.ziel(d, sma)
        // Erwartete Rendite je Start nach demselben Zuschnitt, gerechnet mit der Hauptrechnung
        val monate = SparplanRegel.monate(k)
        val ms = (10 * 365.25 * 86_400_000.0).toLong()
        val erwartet = ArrayList<Double>()
        var ende = 0
        for (von in StrategieLabor.EINSCHWINGEN until n) {
            if (monate[von] == monate[von - 1]) continue
            val bisZeit = k[von].t + ms
            if (bisZeit > k.last().t) break
            while (ende < n && k[ende].t < bisZeit) ende++
            val bis = min(ende + 1, n)
            erwartet += StrategieLabor.simulieren(d, ziel, von, bis, ohneKosten, leicht = true)?.renditeJahr ?: continue
        }
        assertTrue(erwartet.size >= Startfenster.MIN_FENSTER)
        val liste = Startfenster.rechnen(k, ohneKosten, sma, dauern = listOf(10), taktik = t)
        val z = liste.single()
        assertEquals(erwartet.size, z.fenster)
        val s = Startfenster.spanne(erwartet)
        assertEquals(s.min, z.regelRendite.min, 1e-12)
        assertEquals(s.median, z.regelRendite.median, 1e-12)
        assertEquals(s.max, z.regelRendite.max, 1e-12)
        // Ohne Taktik kommt etwas anderes heraus, die Taktik wirkt also
        val ohne = Startfenster.rechnen(k, ohneKosten, sma, dauern = listOf(10)).single()
        assertTrue(abs(ohne.regelRendite.median - z.regelRendite.median) > 1e-9)
        // Sparplan mit Taktik rechnet (Ausweichwert zaehlt als Bargeld) und liefert Fenster
        val sp = Startfenster.rechnen(k, ohneKosten, sma, sparplan = true, dauern = listOf(10), taktik = t)
        assertEquals(1, sp.size)
        assertTrue(sp.single().regelRendite.median.isFinite())
    }

    // Sparplan (Hauptansicht): Signal aus dem Signalwert
    @Test fun sparplanMitSignalwert() {
        val handel = reihe(12L, 1500, 0.0004, 0.011)
        val signal = reihe(13L, 1500, 0.0002, 0.014)
        val ausweich = reihe(14L, 1500, 0.0001, 0.002)
        val t = Taktik.reihen(handel, signal, ausweich)
        val k = t.handel
        val ohne = SparplanRegel.vergleichen(k, 250, ohneKosten, sma)
        val selbst = SparplanRegel.vergleichen(k, 250, ohneKosten, sma, taktik = Taktik.Reihen(k, k, null))
        assertNotNull(ohne)
        assertNotNull(selbst)
        assertEquals(ohne!!.first.endwert, selbst!!.first.endwert, 0.0)
        assertEquals(ohne.first.kaeufe, selbst.first.kaeufe)
        assertEquals(ohne.second.endwert, selbst.second.endwert, 0.0)
        val mit = SparplanRegel.vergleichen(k, 250, ohneKosten, sma, taktik = t)
        assertNotNull(mit)
        assertTrue(abs(mit!!.first.endwert - ohne.first.endwert) > 1e-6)
        // Stures Sparen hat kein Signal und bleibt gleich
        assertEquals(ohne.second.endwert, mit.second.endwert, 0.0)
    }
}
