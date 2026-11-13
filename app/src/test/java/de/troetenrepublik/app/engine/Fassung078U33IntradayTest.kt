package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Intraday.Ende
import de.troetenrepublik.app.engine.Intraday.Regel
import de.troetenrepublik.app.engine.Intraday.Stopp
import de.troetenrepublik.app.i18n.IntradayTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.pow

// Fassung 0.78: Intraday-Rueckblick. Ausbruch aus der Eroeffnungsspanne und Rest des Tages auf konstruierten
// 5-Minuten-Kerzen.
class Fassung078U33IntradayTest {

    private val ohne = StrategieLabor.Kosten(0.0, 0.0)
    private val k5 = Intraday.KERZE_MS
    private val tagMs = 86_400_000L
    private val start = 1_780_000_200_000L / k5 * k5

    // Handelstag mit 78 Kerzen (6,5 Stunden). Ohne Vorgabe steht der Kurs bei 100.
    private fun tag(nr: Int = 0, n: Int = 78, kerze: (Int) -> List<Double>? = { null }): Intraday.Tag {
        val offen = start + nr * tagMs
        return Intraday.Tag(offen, offen + 78 * k5, (0 until n).map { i ->
            val (o, h, l, c) = kerze(i) ?: listOf(100.0, 100.0, 100.0, 100.0)
            Candle(offen + i * k5, o, h, l, c)
        })
    }

    // Spanne 99 bis 101 in den ersten drei Kerzen, Kerze 3 schliesst bei 101,5 ueber dem Hoch, Einstieg zur Eroeffnung
    // von Kerze 4 bei 101,5. Mit Stopp in der Spannenmitte (100) ist das Risiko 1,5 und das Ziel 104,5.
    private fun ausbruchTag(nr: Int = 0, danach: (Int) -> List<Double>? = { null }) = tag(nr) { i ->
        when {
            i < 3 -> listOf(100.0, 101.0, 99.0, 100.0)
            i == 3 -> listOf(100.0, 101.5, 100.0, 101.5)
            else -> danach(i) ?: listOf(101.5, 101.5, 101.5, 101.5)
        }
    }

    @Test fun stoppZaehltVorZiel() {
        val g = Intraday.ausbruch(ausbruchTag { i -> if (i == 5) listOf(101.5, 105.0, 99.5, 104.0) else null }, Regel(), ohne)!!
        assertTrue(g.long)
        assertEquals(101.5, g.einstieg, 1e-9)
        assertEquals(100.0, g.stopp, 1e-9)
        assertEquals(104.5, g.ziel, 1e-9)
        assertEquals(Ende.STOPP, g.ende)
        assertEquals(100.0, g.ausstieg, 1e-9)
        assertEquals(-1.0, g.r, 1e-9)
    }

    @Test fun zielOhneStopp() {
        val g = Intraday.ausbruch(ausbruchTag { i -> if (i == 6) listOf(101.5, 105.0, 101.0, 104.8) else null }, Regel(), ohne)!!
        assertEquals(Ende.ZIEL, g.ende)
        assertEquals(104.5, g.ausstieg, 1e-9)
        assertEquals(2.0, g.r, 1e-9)
        assertEquals(start + 6 * k5, g.ausstiegT)
    }

    @Test fun eroeffnungUnterDemStoppGiltZurEroeffnung() {
        val g = Intraday.ausbruch(ausbruchTag { i -> if (i == 5) listOf(99.0, 99.2, 98.5, 99.0) else null }, Regel(), ohne)!!
        assertEquals(Ende.STOPP, g.ende)
        assertEquals(99.0, g.ausstieg, 1e-9)
        assertTrue(g.r < -1.0)
    }

    @Test fun ohneMarkeGlattZehnMinutenVorSchluss() {
        val t = ausbruchTag { i -> if (i >= 70) listOf(102.0, 102.0, 102.0, 102.0 + (i - 70) * 0.1) else null }
        val g = Intraday.ausbruch(t, Regel(), ohne)!!
        assertEquals(Ende.SCHLUSS, g.ende)
        // Kerze 76 beginnt zehn Minuten vor Schluss, Kerze 77 zaehlt nicht mehr
        assertEquals(76, t.letzte)
        assertEquals(102.6, g.ausstieg, 1e-9)
        assertEquals(t.schluss - k5, g.ausstiegT)
        // Verkuerzter Tag: die letzte vorhandene Kerze
        assertEquals(41, tag(n = 42).letzte)
    }

    @Test fun einEinstiegJeTag() {
        // Nach dem Stopp bricht der Kurs noch einmal aus, es bleibt bei einem Geschaeft
        val t = ausbruchTag { i ->
            when (i) {
                5 -> listOf(101.5, 101.5, 99.5, 100.0)
                in 6..9 -> listOf(100.0, 100.5, 100.0, 100.5)
                10 -> listOf(100.5, 103.0, 100.5, 103.0)
                else -> if (i > 10) listOf(103.0, 103.0, 103.0, 103.0) else null
            }
        }
        val g = Intraday.ausbruch(t, Regel(), ohne)!!
        assertEquals(start + 4 * k5, g.einstiegT)
        assertEquals(Ende.STOPP, g.ende)
        val e = Intraday.pruefen(List(10) { nr -> ausbruchTag(nr) { i -> if (i == 5) listOf(101.5, 101.5, 99.5, 100.0) else null } }, Regel(), ohne)!!
        assertEquals(10, e.geschaefte.size)
        assertEquals(10, e.tage)
        assertEquals(0.0, e.trefferquote, 1e-9)
        assertEquals(-1.0, e.mittelR, 1e-9)
    }

    @Test fun signalNurImZeitfenster() {
        // Ausbruch erst in Kerze 50: sie endet 135 Minuten vor Schluss, das Fenster endet 150 Minuten davor
        fun spaet(kerze: Int) = tag { i ->
            when {
                i < 3 -> listOf(100.0, 101.0, 99.0, 100.0)
                i < kerze -> null
                else -> listOf(102.0, 102.0, 102.0, 102.0)
            }
        }
        assertNull(Intraday.ausbruch(spaet(50), Regel(), ohne))
        assertNotNull(Intraday.ausbruch(spaet(50), Regel(bisVorSchlussMin = 120), ohne))
        // Kerze 47 endet genau 150 Minuten vor Schluss
        assertNotNull(Intraday.ausbruch(spaet(47), Regel(), ohne))
        assertNull(Intraday.ausbruch(spaet(48), Regel(), ohne))
    }

    @Test fun spanneWaehlbar() {
        // Hoch 103 in Kerze 4 gehoert erst bei 30 Minuten zur Spanne
        val t = tag { i ->
            when {
                i < 3 -> listOf(100.0, 101.0, 99.0, 100.0)
                i == 4 -> listOf(100.0, 103.0, 100.0, 102.0)
                i > 4 -> listOf(102.0, 102.0, 102.0, 102.0)
                else -> null
            }
        }
        assertEquals(start + 5 * k5, Intraday.ausbruch(t, Regel(spanneMin = 15), ohne)!!.einstiegT)
        assertEquals(start + 5 * k5, Intraday.ausbruch(t, Regel(spanneMin = 5), ohne)!!.einstiegT)
        assertNull(Intraday.ausbruch(t, Regel(spanneMin = 30), ohne))
    }

    @Test fun stoppArtenUndMindestabstand() {
        val t = ausbruchTag()
        assertEquals(99.0, Intraday.ausbruch(t, Regel(stopp = Stopp.GEGENSEITE), ohne)!!.stopp, 1e-9)
        val fest = Intraday.ausbruch(t, Regel(stopp = Stopp.PROZENT, stoppAnteil = 0.01), ohne)!!
        assertEquals(101.5 * 0.99, fest.stopp, 1e-9)
        assertEquals(101.5 * 1.03, Intraday.ausbruch(t, Regel(stopp = Stopp.PROZENT, stoppAnteil = 0.01, ziel = 3.0), ohne)!!.ziel, 1e-9)
        // Stopp naeher als 0,1 % am Einstieg: kein Geschaeft
        assertNull(Intraday.ausbruch(t, Regel(stopp = Stopp.PROZENT, stoppAnteil = 0.0005), ohne))
        // Einstieg unter der Spannenmitte: kein Risiko, kein Geschaeft
        val luecke = tag { i ->
            when {
                i < 3 -> listOf(100.0, 101.0, 99.0, 100.0)
                i == 3 -> listOf(100.0, 101.5, 100.0, 101.5)
                else -> listOf(99.5, 99.5, 99.5, 99.5)
            }
        }
        assertNull(Intraday.ausbruch(luecke, Regel(), ohne))
    }

    @Test fun shortNurAufWunsch() {
        val t = tag { i ->
            when {
                i < 3 -> listOf(100.0, 101.0, 99.0, 100.0)
                i == 3 -> listOf(100.0, 100.0, 98.5, 98.5)
                i == 6 -> listOf(98.5, 98.6, 95.0, 95.5)
                else -> listOf(98.5, 98.5, 98.5, 98.5)
            }
        }
        assertNull(Intraday.ausbruch(t, Regel(), ohne))
        val g = Intraday.ausbruch(t, Regel(short = true), ohne)!!
        assertFalse(g.long)
        assertEquals(100.0, g.stopp, 1e-9)
        assertEquals(95.5, g.ziel, 1e-9)
        assertEquals(Ende.ZIEL, g.ende)
        assertEquals(2.0, g.r, 1e-9)
        // Short: Stopp und Ziel in einer Kerze, der Stopp zaehlt
        val beide = tag { i ->
            when {
                i < 3 -> listOf(100.0, 101.0, 99.0, 100.0)
                i == 3 -> listOf(100.0, 100.0, 98.5, 98.5)
                i == 6 -> listOf(98.5, 100.5, 95.0, 95.5)
                else -> listOf(98.5, 98.5, 98.5, 98.5)
            }
        }
        assertEquals(Ende.STOPP, Intraday.ausbruch(beide, Regel(short = true), ohne)!!.ende)
    }

    @Test fun kostenJeGeschaeft() {
        val kosten = StrategieLabor.Kosten(20.0, 1.0)
        // Ohne Kursbewegung bleiben Spread (0,1 % je Seite) und zwei Gebuehren von je 1 Euro auf 10.000 Euro
        val erwartet = (1 - 0.0001) * (1 - 0.001) / (1 + 0.001) - 0.0001 - 1
        assertEquals(erwartet, Intraday.nachKosten(true, 50.0, 50.0, kosten), 1e-12)
        assertEquals(0.0, Intraday.nachKosten(true, 50.0, 50.0, ohne), 1e-12)
        assertEquals(0.1, Intraday.nachKosten(true, 50.0, 55.0, ohne), 1e-12)
        assertEquals(0.1, Intraday.nachKosten(false, 50.0, 45.0, ohne), 1e-12)
        assertTrue(Intraday.nachKosten(false, 50.0, 50.0, kosten) < -0.002)
        val mit = Intraday.ausbruch(ausbruchTag(), Regel(), kosten)!!
        val frei = Intraday.ausbruch(ausbruchTag(), Regel(), ohne)!!
        assertEquals(0.0, frei.ergebnis, 1e-12)
        assertEquals(erwartet, mit.ergebnis, 1e-12)
        // In Vielfachen des Risikos: Risiko 1,5 bei Einstieg 101,5
        assertEquals(erwartet / (1.5 / 101.5), mit.r, 1e-9)
    }

    @Test fun vergleichMitMorgensAbendsUndHalten() {
        // Jeder Tag steigt von 100 + nr auf 101 + nr, ueber Nacht bleibt der Kurs stehen
        val tage = List(12) { nr -> tag(nr) { i -> (100.0 + nr + i / 77.0).let { o -> listOf(o, o + 1 / 77.0, o, o + 1 / 77.0) } } }
        val e = Intraday.pruefen(tage, Regel(), ohne)!!
        assertEquals(tage.sumOf { 1.0 / (100.0 + tage.indexOf(it)) }, e.morgensAbends, 1e-6)
        assertEquals(12.0 / 100.0, e.halten, 1e-6)
        assertEquals(e.geschaefte.sumOf { it.ergebnis }, e.summe, 1e-12)
        assertEquals(1.0 / 3.0, e.noetig, 1e-12)
        assertEquals(0.25, Intraday.noetigeQuote(3.0), 1e-12)
        // Rund zwei Wochen: Aussagekraft nach den Grenzen des Strategietests zu gering
        assertEquals(StrategieLabor.Aussage.ZU_WENIG, e.aussage)
        assertNull(Intraday.pruefen(tage.take(9), Regel(), ohne))
    }

    @Test fun handelstageMitSommerzeit() {
        val platz = Intraday.platz("AAPL")!!
        val ny = ZoneId.of("America/New_York")
        fun ms(tag: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 3, tag, h, m, 0, 0, ny).toInstant().toEpochMilli()
        fun kerzen(tag: Int, vonH: Int, vonM: Int, n: Int) = (0 until n).map { Candle(ms(tag, vonH, vonM) + it * k5, 10.0, 10.5, 9.5, 10.0) }
        // Freitag vor und Montag nach der Umstellung am 8. Maerz 2026, dazu Vorboerse, Samstag und der laufende Dienstag
        val alle = kerzen(6, 9, 0, 6) + kerzen(6, 9, 30, 78) + kerzen(6, 16, 0, 3) + kerzen(7, 9, 30, 78) + kerzen(9, 9, 30, 78) + kerzen(10, 9, 30, 20)
        val tage = Intraday.tage(alle, platz, ms(10, 11, 10))
        assertEquals(2, tage.size)
        assertEquals(ms(6, 9, 30), tage[0].offen)
        assertEquals(ms(6, 16, 0), tage[0].schluss)
        assertEquals(78, tage[0].kerzen.size)
        assertEquals(78, tage[1].kerzen.size)
        // In Weltzeit beginnt der Montag eine Stunde frueher
        assertEquals(3 * tagMs - 3_600_000L, tage[1].offen - tage[0].offen)
        assertEquals(78, tage[1].schritte)
        // Nach Handelsschluss zaehlt der Dienstag mit, Tage mit zu wenigen Kerzen nicht
        assertEquals(3, Intraday.tage(alle, platz, ms(10, 16, 0)).size)
        assertEquals(2, Intraday.tage(alle.filter { it.t < ms(10, 10, 15) }, platz, ms(11, 9, 0)).size)
        // Karfreitag 2026 ist Boersenfeiertag
        val karfreitag = (0 until 78).map { Candle(ZonedDateTime.of(2026, 4, 3, 9, 30, 0, 0, ny).toInstant().toEpochMilli() + it * k5, 10.0, 10.0, 10.0, 10.0) }
        assertTrue(Intraday.tage(karfreitag, platz, ms(31, 0, 0) + 30 * tagMs).isEmpty())
        // Kerze der Tageszeit jetzt
        assertEquals(20, Intraday.schrittJetzt(platz, ms(10, 11, 10)))
        assertNull(Intraday.schrittJetzt(platz, ms(10, 8, 0)))
        assertNull(Intraday.schrittJetzt(platz, ms(7, 11, 0)))
    }

    @Test fun hauptplatzNachKuerzel() {
        assertEquals("US", Intraday.platz("AAPL")?.land)
        assertEquals("US", Intraday.platz("BRK-B")?.land)
        assertEquals("DE", Intraday.platz("SAP.DE")?.land)
        assertEquals("CH", Intraday.platz("NESN.SW")?.land)
        assertEquals("JP", Intraday.platz("7203.T")?.land)
        assertEquals("HK", Intraday.platz("0700.HK")?.land)
        for (s in listOf("^GDAXI", "EURUSD=X", "GC=F", "MOEX:GAZP", "ABC.QQ", "")) assertNull(s, Intraday.platz(s))
    }

    // Tage, an denen jede Kerze um den Faktor f gegen den Schluss davor steigt oder faellt
    private fun trendTage(f: Double, hoch: Double = 1.0, tief: Double = 1.0) = List(12) { nr ->
        tag(nr) { i ->
            val o = 100.0 * f.pow(i)
            val c = o * f
            listOf(o, maxOf(o, c) * hoch, minOf(o, c) * tief, c)
        }
    }

    @Test fun restDesTagesStoppVorZiel() {
        val steigt = Intraday.rest(trendTage(1.001), 6, 0.01, 1.0, ohne, laeufe = 400)!!
        assertEquals(1.0, steigt.ziel, 1e-12)
        assertEquals(0.0, steigt.stopp, 1e-12)
        assertEquals(0.01, steigt.mittel, 1e-6)
        assertEquals(1.0, steigt.mittelR, 1e-4)
        val faellt = Intraday.rest(trendTage(0.999), 6, 0.01, 1.0, ohne, laeufe = 400)!!
        assertEquals(1.0, faellt.stopp, 1e-12)
        assertEquals(-0.01, faellt.mittel, 1e-6)
        // Kerzen, die Stopp und Ziel zugleich treffen: der Stopp zaehlt
        val beide = Intraday.rest(trendTage(1.0, hoch = 1.05, tief = 0.95), 6, 0.01, 2.0, ohne, laeufe = 400)!!
        assertEquals(1.0, beide.stopp, 1e-12)
        // Ohne Bewegung bleibt alles offen, mit Kosten im Minus
        val ruhig = Intraday.rest(trendTage(1.0), 6, 0.01, 2.0, StrategieLabor.Kosten(20.0, 1.0), laeufe = 400)!!
        assertEquals(1.0, ruhig.offen, 1e-12)
        assertTrue(ruhig.mittel < -0.002)
        assertEquals(1.0 / 3.0, ruhig.noetig, 1e-12)
    }

    @Test fun restDesTagesFesterStartwertUndFaecher() {
        // Wechselnde Kerzen: gerade Tage steigen, ungerade fallen, mit Spannen
        val tage = List(14) { nr ->
            tag(nr) { i ->
                val f = if ((nr + i) % 3 == 0) 1.002 else 0.9992
                val o = 100.0 * (1 + 0.0003 * ((nr * 7 + i * 3) % 11 - 5))
                listOf(o, o * maxOf(f, 1.0) * 1.0005, o * minOf(f, 1.0) * 0.9995, o * f)
            }
        }
        val a = Intraday.rest(tage, 12, 0.005, 2.0, ohne, laeufe = 2_000)!!
        val b = Intraday.rest(tage, 12, 0.005, 2.0, ohne, laeufe = 2_000)!!
        assertEquals(a.ziel, b.ziel, 0.0)
        assertEquals(a.mittel, b.mittel, 0.0)
        assertEquals(1.0, a.ziel + a.stopp + a.offen, 1e-12)
        assertTrue(a.ziel > 0 && a.stopp > 0)
        assertEquals(2_000, a.laeufe)
        // Einstieg in Kerze 12 von 78, die letzte Kerze bleibt aussen vor
        assertEquals(78 - 1 - 12, a.schritte)
        assertEquals(Intraday.QUANTILE.size, a.faecher.size)
        for (q in a.faecher) {
            assertEquals(a.schritte + 1, q.size)
            assertEquals(1.0, q[0], 1e-6)
            assertTrue(q.all { it >= 0.995 - 1e-6 && it <= 1.01 + 1e-6 })
        }
        for (i in 0..a.schritte) for (q in 1 until a.faecher.size) assertTrue(a.faecher[q][i] >= a.faecher[q - 1][i])
        // Ein anderer Startwert gibt andere Pfade
        assertFalse(a.mittel == Intraday.rest(tage, 12, 0.005, 2.0, ohne, laeufe = 2_000, startwert = 7L)!!.mittel)
        // Zu wenig Restzeit, zu wenige Tage
        assertNull(Intraday.rest(tage, 76, 0.005, 2.0, ohne))
        assertNull(Intraday.rest(tage.take(9), 12, 0.005, 2.0, ohne))
    }

    @Test fun texteInBeidenSprachen() {
        assertEquals(IntradayTexte.schluessel(true), IntradayTexte.schluessel(false))
        for (k in IntradayTexte.schluessel(true)) for (deutsch in listOf(true, false)) {
            val t = IntradayTexte.text(deutsch, k)
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('?'))
        }
        assertEquals("Erste 15 min", IntradayTexte.text(true, "wahl.min", listOf("15")))
        assertTrue(IntradayTexte.text(true, "rest.kennung").contains("keine Prognose"))
        assertTrue(IntradayTexte.text(false, "rest.kennung").contains("no forecast"))
        assertTrue(IntradayTexte.text(true, "orb.note").startsWith("Historisch und hypothetisch"))
    }
}
