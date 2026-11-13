package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.engine.WarnRueckblick.Reihe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Rueckblicke: Warnphasen vor 1970 (negative Tage), Shiller-KGV als Monatswert der Quelle, Buffett-Indikator mit dem
// Wilshire 5000 wie in der App und davor mit der angeschlossenen Finanzierungsrechnung, die beiden mitgelieferten Dateien.
class Fassung078Z8RueckblickTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun jahr(t: Int) = LocalDate.ofEpochDay(t.toLong()).year

    // ------------------------------------------------------------ Warnphasen und negative Tage

    // Zustand 1, an den genannten Plaetzen 3 (Warnung)
    private fun zustand(n: Int, vararg warn: IntRange) = IntArray(n) { i -> if (warn.any { i in it }) 3 else 1 }

    @Test fun warnphasenVor1970Zaehlen() {
        // Alle Tage vor 1970. Zwei Warnphasen; auf die erste folgt ein Einbruch, die zweite bleibt ohne.
        val tage = LongArray(3000) { -15_000L + it }
        val z = zustand(3000, 400..450, 1500..1550)
        val einbruch = WarnRueckblick.Ereignis(20, tage[500].toInt(), tage[600].toInt(), 30.0)
        val b = WarnRueckblick.bilanz("x", tage, z, 3, listOf(einbruch))
        assertEquals(2, b.phasen)
        assertEquals(1, b.phasenTreffer)
        assertEquals(1, b.fehlalarme)
        assertEquals(0, b.offen)
        assertEquals(listOf(tage[400], tage[450], tage[1500], tage[1550]).map { it.toInt() }, b.phasenListe)
        assertEquals(1 to 1, b.auswertbar to b.treffer)
        assertEquals(-15_000, b.ab)
    }

    @Test fun warnphaseUeber1970Hinweg() {
        // Tage -200 bis 199, Warnung von Tag -100 bis 100: eine Phase, nicht am Jahreswechsel 1970 zerschnitten
        val tage = LongArray(400) { -200L + it }
        val b = WarnRueckblick.bilanz("x", tage, zustand(400, 100..300), 3, emptyList())
        assertEquals(listOf(-100, 100), b.phasenListe)
        // Phasen, die genau am Tag -1, 0 oder 1 beginnen oder enden
        val p = WarnRueckblick.phasen(longArrayOf(-400, -1, 0, 1, 400), booleanArrayOf(true, true, true, true, true), 183)
        assertEquals(listOf(listOf(-400L, -400L), listOf(-1L, 1L), listOf(400L, 400L)), p.map { it.toList() })
    }

    @Test fun rechnungHaengtNichtAmVorzeichenDerTage() {
        // Dieselbe Reihe einmal ganz vor, einmal ueber und einmal nach 1970: gleiche Zahlen, nur verschoben
        val n = 4000
        val kurs = DoubleArray(n) { 100.0 + 40.0 * kotlin.math.sin(it / 150.0) + it * 0.01 }
        val z = IntArray(n) { i -> if (i < 50) 0 else if ((i / 90) % 4 == 0) 3 else 1 }
        fun lauf(start: Long): List<Any?> {
            val tage = LongArray(n) { start + it }
            val ev = WarnRueckblick.ereignisse(tage, kurs)
            val b = WarnRueckblick.bilanz("x", tage, z, 3, ev)
            val a = ZeichenRueckblick.alarm("x", tage, IntArray(n) { if (z[it] == 3) 2 else z[it] }, ev.map { it.tief.toLong() }, null)!!
            val w = ZeichenRueckblick.wenden(kurs)
            return listOf(
                ev.map { listOf(it.stufe, it.hoch - start, it.tief - start, it.tiefe, it.erholt?.let { e -> e - start }) },
                b.ab - start, b.auswertbar, b.treffer, b.auswertbar10, b.treffer10, b.phasen, b.phasenTreffer, b.fehlalarme, b.offen,
                b.vorlaufMittel, b.zeitAnteil, b.phasenListe.map { it - start },
                a.ab - start, a.phasen, a.mitBoden, a.fehlalarme, a.offen, a.boeden, a.erfuellt, a.zeit,
                w.boeden.map { it.hoch to it.tief }, ZeichenRueckblick.danach(tage, kurs, 10),
            )
        }
        val nach = lauf(10_000L)
        assertTrue((nach[6] as Int) > 3)
        assertEquals(nach, lauf(-20_000L))
        assertEquals(nach, lauf(-2_000L))
        // Kursmuster und Baerenmaerkte rechnen mit Kalenderdaten bzw. Abstaenden: Jahre vor 1970 zaehlen wie alle anderen
        val tage = LongArray(n) { -20_000L + it }
        val m = Kursmuster.rechnen("x", tage, kurs, true)!!
        assertEquals(LocalDate.ofEpochDay(-20_000L).year + 1, m.vonJahr)
        assertEquals(m.bisJahr - m.vonJahr + 1, m.zyklus.sumOf { it.anzahl })
    }

    // ------------------------------------------------------------ Shiller-KGV

    @Test fun capeIstDerMonatswertDerQuelle() {
        // Monatswerte wie in der Quelle (September 1929 und die Monate um das Hoch im Maerz 2000)
        fun tag(j: Int, m: Int, t: Int = 1) = LocalDate.of(j, m, t).toEpochDay()
        val monate = listOf(tag(1929, 8) to 31.48, tag(1929, 9) to 32.564, tag(1999, 12) to 44.198, tag(2000, 1) to 43.773, tag(2000, 2) to 42.186, tag(2000, 3) to 43.221)
        val cape = Reihe(monate.map { it.first }.toLongArray(), monate.map { it.second }.toDoubleArray(), WarnRueckblick.VERZUG_CAPE, 70)
        // Der Tageskurs liegt weit ueber dem Monatsmittel: Der Wert bleibt der der Quelle
        val sp = Reihe(longArrayOf(tag(1929, 9, 16), tag(2000, 3, 24)), doubleArrayOf(31.86, 1527.46))
        val q = WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_CAPE to cape, WarnRueckblick.R_SP to sp))
        assertEquals(32.564, ZeichenRueckblick.cape(q, tag(1929, 9, 16))!!, 1e-9)
        assertEquals(43.221, ZeichenRueckblick.cape(q, tag(2000, 3, 24))!!, 1e-9)
        assertEquals(44.198, ZeichenRueckblick.cape(q, tag(1999, 12, 31))!!, 1e-9)
        // Der Monat fehlt noch: der Monat davor gilt, aber nicht laenger als CAPE_ALTER Tage
        assertEquals(43.221, ZeichenRueckblick.cape(q, tag(2000, 4, 20))!!, 1e-9)
        assertNull(ZeichenRueckblick.cape(q, tag(2000, 6, 15)))
        assertNull(ZeichenRueckblick.cape(q, tag(1929, 7, 31)))
    }

    @Test fun capeInDerDatei() {
        val d = ZeichenRueckblick.lesen(asset(ZeichenRueckblick.DATEI).readText(Charsets.UTF_8))!!
        fun cape(j: Int) = d.blasen.first { jahr(it.hoch) == j }.werte.first { it.id == Blasen.CAPE }
        // September 1929: 32,6. Maerz 2000: 43,2, Hoechstwert im Jahr davor 44,2 (Dezember 1999)
        assertEquals(32.56, cape(1929).wert, 0.005)
        assertEquals(43.22, cape(2000).wert, 0.005)
        assertEquals(44.2, cape(2000).zusatz!!, 0.005)
        assertEquals(Ampel.ROT, ZeichenRueckblick.ampel(cape(1929)))
        assertEquals(Ampel.ROT, ZeichenRueckblick.ampel(cape(2000)))
        // Die Marken des Blasen-Detektors nennen dieselben Monatswerte
        assertEquals(44.2, Blasen.VERGLEICHE.getValue(Blasen.CAPE).first { it.vonJahr == 1999 }.wert!!, 0.05)
    }

    // ------------------------------------------------------------ Buffett-Indikator

    private val t0 = 10_000L

    @Test fun buffettMitDemWilshireWieInDerApp() {
        // Wilshire 14.000 Punkte, BIP 10 Billionen Dollar (Millionen): 140 % wie Blasen.buffett in der App
        val bip = Reihe(longArrayOf(t0), doubleArrayOf(1.0e7), WarnRueckblick.VERZUG_BIP, 120)
        val wilshire = Reihe(LongArray(60) { t0 + 40 + it }, DoubleArray(60) { 14_000.0 })
        // Die Finanzierungsrechnung laege tiefer; mit Wilshire-Wert zaehlt sie nicht
        val z1 = Reihe(longArrayOf(t0 - 80), doubleArrayOf(1.2e7), WarnRueckblick.VERZUG_Z1, 120)
        val sp = Reihe(LongArray(200) { t0 - 100 + it }, DoubleArray(200) { 1000.0 })
        val q = WarnRueckblick.Quellen(
            mapOf(WarnRueckblick.R_BIP to bip, WarnRueckblick.R_WILSHIRE to wilshire, WarnRueckblick.R_AKTIEN_WERT to z1, WarnRueckblick.R_SP to sp),
        )
        val (wert, b) = WarnRueckblick.buffettWerte(q, t0 + 50)!!
        assertEquals(Blasen.buffett(14_000.0, 1.0e13)!!.wert, Blasen.buffett(wert, b)!!.wert, 1e-9)
        assertEquals(140.0, ZeichenRueckblick.blasenWerte(q, t0 + 50).getValue(Blasen.BUFFETT), 1e-9)
        // Vor dem Beginn der Wilshire-Reihe: Finanzierungsrechnung, 12 Billionen durch 10 Billionen
        assertEquals(120.0, ZeichenRueckblick.blasenWerte(q, t0 + 35).getValue(Blasen.BUFFETT), 1e-9)
        // Ohne BIP kein Wert
        assertNull(WarnRueckblick.buffettWerte(WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_WILSHIRE to wilshire)), t0 + 50))
    }

    @Test fun finanzierungsrechnungAnDenWilshireAngeschlossen() {
        // 16 Quartale Finanzierungsrechnung (Millionen Dollar); der Wilshire beginnt im fuenften Quartal und liegt in den
        // ersten acht gemeinsamen Quartalen 10 % hoeher, danach 50 %
        val z1 = Reihe(LongArray(16) { t0 + it * 91L }, DoubleArray(16) { 1.0e6 * (it + 1) }, WarnRueckblick.VERZUG_Z1, 120)
        val ab = t0 + 4 * 91L - 30
        val wilshire = Reihe(LongArray(1200) { ab + it }, DoubleArray(1200) { i ->
            val quartal = ((ab + i - t0 + 45) / 91).toInt()
            1000.0 * (quartal + 1) * (if (quartal < 12) 1.1 else 1.5)
        })
        assertEquals(1.1, WarnRueckblick.anschlussFaktor(z1, wilshire)!!, 1e-9)
        val a = WarnRueckblick.angeschlossen(z1, wilshire)
        assertEquals(1.1e6, a.werte[0], 1e-3)
        assertEquals(z1.tage.toList(), a.tage.toList())
        assertEquals(z1.verzug to z1.maxAlter, a.verzug to a.maxAlter)
        // Zu wenige gemeinsame Quartale oder kein Wilshire: Reihe unveraendert
        val kurz = Reihe(LongArray(200) { ab + it }, DoubleArray(200) { 5000.0 })
        assertNull(WarnRueckblick.anschlussFaktor(z1, kurz))
        assertSame(z1, WarnRueckblick.angeschlossen(z1, kurz))
        assertSame(z1, WarnRueckblick.angeschlossen(z1, null))
        assertEquals(8, WarnRueckblick.ANSCHLUSS_QUARTALE)
    }

    @Test fun buffettInDerDatei() {
        val d = ZeichenRueckblick.lesen(asset(ZeichenRueckblick.DATEI).readText(Charsets.UTF_8))!!
        fun buffett(j: Int) = d.blasen.first { jahr(it.hoch) == j }.werte.first { it.id == Blasen.BUFFETT }
        // Maerz 2000 mit dem Wilshire 5000: knapp unter der roten Schwelle der App (Rot heisst dort: hoeher als 2000)
        assertTrue(buffett(2000).wert in 145.0..150.0)
        assertEquals(150.0, Blasen.SCHWELLEN.getValue(Blasen.BUFFETT).rot, 0.0)
        assertEquals(Ampel.GELB, ZeichenRueckblick.ampel(buffett(2000)))
        assertEquals(Ampel.ROT, ZeichenRueckblick.ampel(buffett(2020)))
        assertEquals(Ampel.ROT, ZeichenRueckblick.ampel(buffett(2022)))
        // Vor 1989 aus der angeschlossenen Finanzierungsrechnung
        assertEquals(Ampel.GRUEN, ZeichenRueckblick.ampel(buffett(1987)))
        assertTrue(buffett(1987).wert in 60.0..80.0)
    }

    // ------------------------------------------------------------ Warnzeichen-Datei

    @Test fun warnzeichenDateiMitPhasenVor1970() {
        val d = WarnRueckblick.lesen(asset("indikatoren/warnzeichen_rueckblick.json").readText(Charsets.UTF_8))
        assertNotNull(d)
        val sp = d!!.indizes.first { it.id == "sp500" }
        assertTrue(sp.von < 0)
        for (ix in d.indizes) for (b in ix.zeichen) {
            assertEquals("${ix.id} ${b.id}", b.phasen, b.phasenTreffer + b.fehlalarme + b.offen)
            assertEquals("${ix.id} ${b.id}", b.phasen * 2, b.phasenListe.size)
            assertEquals("${ix.id} ${b.id}", b.phasenListe.sorted(), b.phasenListe)
        }
        // Zeichen mit Daten vor 1970 tragen auch Warnphasen aus dieser Zeit
        for (id in listOf(WarnRueckblick.TREND, WarnRueckblick.HITZE, WarnRueckblick.SAHM, WarnRueckblick.CAPE, WarnRueckblick.KGV)) {
            val b = sp.zeichen.first { it.id == id }
            assertTrue(id, b.ab < 0 && b.phasenListe.first() < 0)
        }
        // Der Abschwung von 1929 lag in einer Warnphase des Trends
        val trend = sp.zeichen.first { it.id == WarnRueckblick.TREND }
        assertTrue(trend.phasenListe.chunked(2).any { jahr(it[0]) <= 1930 && jahr(it[1]) >= 1930 })
    }
}
