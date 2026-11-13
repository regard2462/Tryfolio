package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.HistorieJst.Art
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

// Historische Annahmen der Rechner: Kennzahlen, Bandbreite und historische Folgen aus HistorieJst, an festen Beispielen
// und an der mitgelieferten Datei. Jede Aussage „in N von M“ wird hier unabhaengig nachgezaehlt.
class Fassung078M1HistorieRechnerTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private val datei: HistorieJst.Datei by lazy { HistorieJst.lesen(asset(HistorieJst.DATEI).readText(Charsets.UTF_8))!! }

    private fun basis(von: Int, vararg r: Double) = HistorieRechner.Basis("X", IntArray(r.size) { von + it }, r, 1)
    private fun gleich(von: Int, n: Int, r: Double) = HistorieRechner.Basis("X", IntArray(n) { von + it }, DoubleArray(n) { r }, 1)

    @Test fun kennzahlenGleichbleibend() {
        val k = HistorieRechner.kennzahlen(gleich(1900, 80, 0.05))
        assertEquals(5.0, k.rendite, 1e-9)
        assertEquals(0.0, k.schwankung, 1e-9)
        assertEquals(1900, k.erstes)
        assertEquals(1979, k.letztes)
        assertEquals(80, k.jahre)
    }

    @Test fun kennzahlenGeometrisch() {
        // +100 % und -50 % heben sich auf
        val k = HistorieRechner.kennzahlen(basis(2000, 1.0, -0.5))
        assertEquals(0.0, k.rendite, 1e-9)
        assertTrue(k.schwankung > 90)
    }

    @Test fun startsOhneLuecke() {
        // 1900..1904, Luecke, 1907..1909
        val b = HistorieRechner.Basis("X", intArrayOf(1900, 1901, 1902, 1903, 1904, 1907, 1908, 1909), DoubleArray(8) { 0.01 }, 1)
        assertEquals(listOf(0, 1, 2, 5), HistorieRechner.starts(b, 3).toList())
        assertEquals(listOf(0), HistorieRechner.starts(b, 5).toList())
        assertTrue(HistorieRechner.starts(b, 6).isEmpty())
    }

    @Test fun bandbreiteBeispiel() {
        val b = basis(2000, 0.10, 0.10, -0.20, -0.20, 0.30, 0.30)
        val band = HistorieRechner.bandbreite(b, 2)!!
        assertEquals(5, band.faelle)
        assertEquals(2002, band.schlecht.von)
        assertEquals(2003, band.schlecht.bis)
        assertEquals(-20.0, band.schlecht.rendite, 1e-9)
        assertEquals(2004, band.best.von)
        assertEquals(30.0, band.best.rendite, 1e-9)
        assertNull(HistorieRechner.bandbreite(b, 7))
    }

    @Test fun nominalAusReal() {
        assertEquals(7.1, HistorieRechner.nominal(5.0, 2.0), 1e-9)
        assertEquals(5.0, HistorieRechner.nominal(5.0, 0.0), 1e-9)
    }

    @Test fun aufbauOhneRateWaechstMitDerRendite() {
        val a = HistorieRechner.aufbau(gleich(1900, 60, 0.05), 1_000.0, 0.0, 10)!!
        assertEquals(51, a.faelle)
        assertEquals(1900, a.ersterStart)
        assertEquals(1950, a.letzterStart)
        val soll = 1_000.0 * 1.05.pow(10)
        assertEquals(soll, a.projektion.p10[10], 1e-6)
        assertEquals(soll, a.projektion.p50[10], 1e-6)
        assertEquals(soll, a.projektion.p90[10], 1e-6)
        assertEquals(1_000.0, a.projektion.invested[10], 1e-9)
    }

    @Test fun aufbauMitRateOhneRendite() {
        val a = HistorieRechner.aufbau(gleich(1900, 60, 0.0), 500.0, 100.0, 5)!!
        assertEquals(500.0 + 60 * 100.0, a.projektion.p50[5], 1e-6)
        assertEquals(500.0 + 60 * 100.0, a.projektion.invested[5], 1e-6)
        // Ziel 3.000 ist nach 25 Monaten erreicht (500 + 25 * 100)
        val z = HistorieRechner.aufbau(gleich(1900, 60, 0.0), 500.0, 100.0, 5, target = 3_000.0)!!
        assertEquals(z.faelle, z.erreicht)
        assertEquals(1.0, z.projektion.targetProbability, 1e-9)
        assertEquals(25 / 12.0, z.projektion.targetYears!!, 1e-9)
    }

    @Test fun aufbauRateVerliertKaufkraft() {
        val ohne = HistorieRechner.aufbau(gleich(1900, 60, 0.0), 0.0, 100.0, 10)!!
        val mit = HistorieRechner.aufbau(gleich(1900, 60, 0.0), 0.0, 100.0, 10, inflationPct = 2.0)!!
        assertEquals(12_000.0, ohne.projektion.p50[10], 1e-6)
        assertTrue(mit.projektion.p50[10] < 11_000.0)
        assertEquals(mit.projektion.invested[10], mit.projektion.p50[10], 1e-6)
    }

    @Test fun aufbauSchlechtesterUndBesterStart() {
        // 30 ruhige Jahre, dann ein Einbruch 1930, danach wieder ruhig: Folgen von 5 Jahren mit 1930 enden am tiefsten
        val r = DoubleArray(60) { if (it == 30) -0.5 else 0.02 }
        val b = HistorieRechner.Basis("X", IntArray(60) { 1900 + it }, r, 1)
        val a = HistorieRechner.aufbau(b, 1_000.0, 0.0, 5)!!
        assertTrue(a.schlechtStart in 1926..1930)
        assertEquals(1_000.0 * 0.5 * 1.02.pow(4), a.schlechtEnde, 1e-6)
        assertEquals(1_000.0 * 1.02.pow(5), a.bestEnde, 1e-6)
        // 5 von 56 Folgen enthalten 1930
        assertEquals(56, a.faelle)
        val z = HistorieRechner.aufbau(b, 1_000.0, 0.0, 5, target = 1_050.0)!!
        assertEquals(51, z.erreicht)
    }

    @Test fun aufbauZuWenigeFolgen() {
        assertNull(HistorieRechner.aufbau(gleich(1900, 25, 0.03), 1_000.0, 0.0, 10))
        assertNotNull(HistorieRechner.aufbau(gleich(1900, 29, 0.03), 1_000.0, 0.0, 10))
    }

    @Test fun entnahmeOhneRenditeReichtGenauSoLange() {
        // 12.000 und 100 im Monat ohne Rendite und ohne Inflation: nach 120 Monaten ist nichts mehr da
        val b = gleich(1900, 60, 0.0)
        val knapp = HistorieRechner.entnahme(b, 12_000.0, 100.0, 9, 0.0, indexed = true)!!
        assertEquals(knapp.faelle, knapp.reicht)
        assertEquals(1.0, knapp.plan.success, 1e-9)
        assertEquals(12_000.0 - 108 * 100.0, knapp.plan.p50[9], 1e-6)
        val leer = HistorieRechner.entnahme(b, 12_000.0, 100.0, 12, 0.0, indexed = true)!!
        assertEquals(0, leer.reicht)
        assertEquals(119 / 12.0, leer.plan.depletionYears!!, 1e-9)
        assertEquals(119 / 12.0, leer.schlechtLeerNach!!, 1e-9)
        // Hoechste Rate, die in 90 % der Folgen reicht: knapp unter 12.000 / 144
        assertEquals(12_000.0 / 144, leer.plan.safeMonthly, 0.5)
    }

    @Test fun entnahmeOhneAnpassungVerliertKaufkraft() {
        val b = gleich(1900, 60, 0.0)
        val fest = HistorieRechner.entnahme(b, 12_000.0, 100.0, 9, 2.0, indexed = false)!!
        val angepasst = HistorieRechner.entnahme(b, 12_000.0, 100.0, 9, 2.0, indexed = true)!!
        // Ohne Anpassung wird real weniger entnommen, es bleibt mehr uebrig
        assertTrue(fest.plan.p50[9] > angepasst.plan.p50[9])
        assertTrue(angepasst.plan.p50[9] > 12_000.0 - 108 * 100.0)
    }

    @Test fun entnahmeSchlechtesterStart() {
        val r = DoubleArray(60) { if (it == 30) -0.6 else 0.03 }
        val b = HistorieRechner.Basis("X", IntArray(60) { 1900 + it }, r, 1)
        val e = HistorieRechner.entnahme(b, 100_000.0, 500.0, 20, 0.0, indexed = true)!!
        // Der Einbruch gleich im ersten Jahr trifft am haertesten
        assertEquals(1930, e.schlechtStart)
        assertTrue(e.reicht < e.faelle)
        assertEquals(e.reicht.toDouble() / e.faelle, e.plan.success, 1e-9)
    }

    // ------------------------------------------------------------ mitgelieferte Datei

    @Test fun weltAusDerDatei() {
        val w = HistorieRechner.welt(datei)!!
        assertEquals(1871, w.erstes)
        assertEquals(2020, w.letztes)
        assertEquals(150, w.jahre.size)
        assertEquals(16, w.laender)
        val k = HistorieRechner.kennzahlen(w)
        assertEquals(6.0, k.rendite, 0.1)
        assertEquals(13.3, k.schwankung, 0.2)
        // Jedes Jahr ist das Mittel der Laender mit Wert
        val jahr = 1950
        val werte = datei.laender.values.mapNotNull { it.reihen[Art.AKTIEN]?.getOrNull(jahr - it.von) }
        assertTrue(werte.size >= HistorieRechner.MIN_LAENDER)
        assertEquals(werte.average(), w.renditen[w.jahre.indexOf(jahr)], 1e-12)
    }

    @Test fun bandbreiteWelt() {
        val w = HistorieRechner.welt(datei)!!
        val b30 = HistorieRechner.bandbreite(w, 30)!!
        assertEquals(121, b30.faelle)
        assertEquals(1891, b30.schlecht.von)
        assertEquals(1920, b30.schlecht.bis)
        assertEquals(2.5, b30.schlecht.rendite, 0.1)
        assertEquals(1978, b30.best.von)
        assertEquals(10.7, b30.best.rendite, 0.1)
        val b20 = HistorieRechner.bandbreite(w, 20)!!
        assertEquals(131, b20.faelle)
        assertEquals(1901, b20.schlecht.von)
        assertEquals(0.6, b20.schlecht.rendite, 0.1)
        assertEquals(1980, b20.best.von)
        assertEquals(14.2, b20.best.rendite, 0.1)
    }

    @Test fun laenderAusDerDatei() {
        // 16 Laender mit Aktienrenditen; Kanada und Irland haben keine
        val mit = datei.laender.keys.filter { HistorieRechner.land(datei, it) != null }
        assertEquals(16, mit.size)
        assertEquals(mit.toSet(), HistorieRechner.A3.values.toSet())
        assertNull(HistorieRechner.land(datei, "CAN"))
        // Deutschland: Luecke 1922 bis 1924, Folgen laufen nicht darueber
        val de = HistorieRechner.land(datei, "DEU")!!
        assertEquals(147, de.jahre.size)
        assertTrue(1923 !in de.jahre)
        val s = HistorieRechner.starts(de, 30)
        assertEquals(89, s.size)
        assertTrue(s.none { de.jahre[it] <= 1921 && de.jahre[it] + 29 >= 1922 })
        val usa = HistorieRechner.kennzahlen(HistorieRechner.land(datei, "USA")!!)
        assertEquals(6.8, usa.rendite, 0.1)
    }

    @Test fun aufbauWeltNachgezaehlt() {
        val w = HistorieRechner.welt(datei)!!
        val a = HistorieRechner.aufbau(w, 10_000.0, 0.0, 20, target = 30_000.0)!!
        assertEquals(131, a.faelle)
        assertEquals(1871, a.ersterStart)
        assertEquals(2001, a.letzterStart)
        // Unabhaengig nachgerechnet: Endwert je Startjahr als Produkt der Jahresrenditen
        val enden = (0..w.jahre.size - 20).map { i -> (i until i + 20).fold(10_000.0) { v, k -> v * (1.0 + w.renditen[k]) } }
        assertEquals(enden.count { it >= 30_000.0 }, a.erreicht)
        assertEquals(enden.min(), a.schlechtEnde, 1e-3)
        assertEquals(enden.max(), a.bestEnde, 1e-3)
        assertEquals(w.jahre[enden.indexOf(enden.min())], a.schlechtStart)
        assertEquals(1901, a.schlechtStart)
        assertEquals(1980, a.bestStart)
        assertTrue(a.erreicht in 1 until a.faelle)
    }

    @Test fun entnahmeWeltNachgezaehlt() {
        val w = HistorieRechner.welt(datei)!!
        // 4 % im Jahr von 300.000, 30 Jahre, Entnahme haelt ihre Kaufkraft
        val e = HistorieRechner.entnahme(w, 300_000.0, 1_000.0, 30, 2.0, indexed = true)!!
        assertEquals(121, e.faelle)
        // Nachgezaehlt in realer Rechnung ohne Inflation (die Anpassung gleicht sie jaehrlich aus; innerhalb des Jahres
        // sinkt die Kaufkraft der Entnahme leicht, darum reicht es dort mindestens so oft)
        var reicht = 0
        for (i in 0..w.jahre.size - 30) {
            var v = 300_000.0
            var lebt = true
            for (k in 0 until 30) {
                val g = (1.0 + w.renditen[i + k]).pow(1.0 / 12.0)
                for (m in 0 until 12) { v -= 1_000.0; if (v <= 0) { lebt = false; break }; v *= g }
                if (!lebt) break
            }
            if (lebt) reicht++
        }
        val real = HistorieRechner.entnahme(w, 300_000.0, 1_000.0, 30, 0.0, indexed = true)!!
        assertEquals(reicht, real.reicht)
        assertTrue(e.reicht >= real.reicht)
        assertTrue(real.reicht in 1..real.faelle)
        assertEquals(real.reicht.toDouble() / real.faelle, real.plan.success, 1e-9)
    }
}
