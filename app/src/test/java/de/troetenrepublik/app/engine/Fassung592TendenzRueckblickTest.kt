package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sin
import kotlin.math.sqrt

// Fassung 5.92: Rueckblick auf die Tendenz. Stichtage ohne Blick in die Zukunft, Renditen mit Roll, Trefferquote
// gegen Grundquote, Newey-West, Bilanz und Uebungsvorschlag nur bei belegter Tendenz.
class Fassung592TendenzRueckblickTest {

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay()

    private fun rauschen(i: Int) = sin(i * 12.9898 + 78.233) * 43758.5453 % 1.0

    // Woechentliche Reihen ab 2000: x schwingt, y folgt -x mit Rauschen.
    private val start = tag("2000-01-07")
    private val x = (0 until 900).map { i -> start + 7L * i to 10 * sin(i / 9.0) }
    private val y = (0 until 900).map { i -> start + 7L * i to -10 * sin(i / 9.0) + 3 * rauschen(i) }

    // ------------------------------------------------------------ Stichtage

    @Test fun stichtageSehenKeineZukunft() {
        val a = TendenzRueckblick.faelle(x, y, -1, saison = false)
        assertTrue(a.size > 300)
        // Alles nach dem Schnitt grob veraendert: die Faelle bis zum Schnitt bleiben gleich
        val schnitt = start + 7L * 600
        val y2 = y.map { (t, v) -> if (t > schnitt) t to v * 5 + 40 else t to v }
        val x2 = x.map { (t, v) -> if (t > schnitt) t to -v else t to v }
        val b = TendenzRueckblick.faelle(x2, y2, -1, saison = false)
        assertEquals(a.filter { it.tag <= schnitt }, b.filter { it.tag <= schnitt })
        assertTrue(a.filter { it.tag > schnitt } != b.filter { it.tag > schnitt })
    }

    @Test fun lernzeitFuenfJahre() {
        val f = TendenzRueckblick.faelle(x, y, -1, saison = false)
        assertTrue(f.first().tag - start >= TendenzRueckblick.LERN_TAGE)
        assertTrue(f.first().tag - start < TendenzRueckblick.LERN_TAGE + 14)
    }

    @Test fun stichtagWieDieApp() {
        // Am Stichtag dieselbe Einordnung wie Fruehindikatoren.einordnungAusReihen mit den Reihen bis dahin
        val f = TendenzRueckblick.faelle(x, y, -1, saison = false)
        val k = f[200]
        val e = Fruehindikatoren.einordnungAusReihen(x.filter { it.first <= k.tag }, y.filter { it.first <= k.tag }, -1, saison = false)!!
        assertEquals(Eingepreist.tendenz(e), k.tendenz)
        assertEquals(e.ampel, k.ampel)
        // Bei den Zinsen umgekehrt
        val z = TendenzRueckblick.faelle(x, y, -1, saison = false, umgekehrt = true)
        assertEquals(-k.tendenz, z[200].tendenz)
    }

    @Test fun datierteSignaleSehenKeineZukunft() {
        val t0 = tag("2024-01-01") * 86_400_000L
        val kurse = (0 until 400).map { i -> Candle(t0 + i * 86_400_000L, 0.0, 0.0, 0.0, 100.0 + i * 0.1) }
        val index = (0 until 400).map { i -> Candle(t0 + i * 86_400_000L, 0.0, 0.0, 0.0, 100.0) }
        val zeit = t0 + 100 * 86_400_000L
        val a = TendenzRueckblick.datiert(kurse, index, zeit, 1, emptyList(), 21)!!
        // Kurse nach dem Stichtag aendern nichts
        val spaeter = kurse.mapIndexed { i, c -> if (i > 121) c.copy(close = 500.0) else c }
        val b = TendenzRueckblick.datiert(spaeter, index, zeit, 1, emptyList(), 21)!!
        assertEquals(a, b)
        assertEquals(kurse[121].t, a.first)
        // Zu wenig Kurse nach dem Signal: kein Fall
        assertNull(TendenzRueckblick.datiert(kurse, index, t0 + 390 * 86_400_000L, 1, emptyList(), 21))
    }

    // ------------------------------------------------------------ Renditen

    @Test fun renditeUndZins() {
        val k = listOf(0L to 100.0, 7L to 110.0, 14L to 121.0, 21L to 99.0)
        assertEquals(0.1, TendenzRueckblick.rendite(k, 0, 1)!!, 1e-9)
        assertEquals(0.21, TendenzRueckblick.rendite(k, 0, 2)!!, 1e-9)
        // Einstieg erst am naechsten Kurs
        assertEquals(0.1, TendenzRueckblick.rendite(k, 5, 1)!!, 1e-9)
        // Reicht nicht so weit
        assertNull(TendenzRueckblick.rendite(k, 0, 5))
        // Rendite faellt um 0,5 Punkte: Anleihen gewinnen +0,5
        val z = listOf(0L to 4.0, 7L to 3.5)
        assertEquals(0.5, TendenzRueckblick.rendite(z, 0, 1, TendenzRueckblick.Art.ZINS)!!, 1e-9)
    }

    @Test fun rollOhneSprung() {
        // Contango: Kontrakt 2 kostet 2 mehr. Am Verfall wird der alte zweite zum ersten, der Kurs bleibt gleich
        val tab = listOf(
            1L to doubleArrayOf(50.0, 52.0, 54.0, 56.0),
            2L to doubleArrayOf(50.0, 52.0, 54.0, 56.0),
            3L to doubleArrayOf(52.0, 54.0, 56.0, 58.0),
            4L to doubleArrayOf(52.52, 54.54, 56.56, 58.58),
        )
        val mit = TendenzRueckblick.rollVerlauf(tab)
        assertEquals(100.0, mit[2].second, 1e-9)
        assertEquals(101.0, mit[3].second, 1e-9)
        assertEquals(1, TendenzRueckblick.rollen(tab))
        // Ohne Roll entstuende ein Sprung von 4 %, der nie gehandelt wurde
        val ohne = TendenzRueckblick.rollVerlauf(tab, bereinigt = false)
        assertEquals(104.0, ohne[2].second, 1e-9)
    }

    // ------------------------------------------------------------ Auswertung

    @Test fun trefferquoteGegenGrundquote() {
        // 10 Stichtage, Abstand 4 Wochen: 6 mit long (5 steigen), 2 mit short (1 faellt), 2 ohne Richtung (fallen)
        val m = listOf(
            1 to 0.02, 1 to 0.01, 1 to 0.03, 1 to 0.01, 1 to 0.02, 1 to -0.01,
            -1 to -0.02, -1 to 0.01, 0 to -0.01, 0 to -0.03,
        ).mapIndexed { i, (r, v) -> TendenzRueckblick.Messung(28L * i, r, v) }
        val z = TendenzRueckblick.auswerten(m, 1)!!
        assertEquals(8, z.n)
        assertEquals(6.0 / 8, z.treffer, 1e-9)
        // Grundquote: 6 von 10 stiegen, 4 fielen; 6 long und 2 short
        assertEquals((6 * 0.6 + 2 * 0.4) / 8, z.grund, 1e-9)
        assertEquals((0.02 + 0.01 + 0.03 + 0.01 + 0.02 - 0.01 + 0.02 - 0.01) / 8, z.mittel, 1e-9)
        assertEquals(8, z.unabhaengig)
        assertFalse(z.belegt)
    }

    @Test fun neweyWestOhneUndMitUeberlappung() {
        val z = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        // Abstand groesser als die Laenge: einfacher Fehler (mit Teiler n)
        val weit = TendenzRueckblick.neweyWest(longArrayOf(0, 10, 20, 30, 40), z, 7)
        val m = z.average()
        assertEquals(sqrt(z.sumOf { (it - m) * (it - m) }) / 5, weit, 1e-12)
        // Gleiche Werte nah beieinander zaehlen gemeinsam: der Fehler waechst
        val d = doubleArrayOf(1.0, 1.0, 3.0, 3.0)
        val getrennt = TendenzRueckblick.neweyWest(longArrayOf(0, 100, 200, 300), d, 7)
        val zusammen = TendenzRueckblick.neweyWest(longArrayOf(0, 0, 200, 200), d, 7)
        assertEquals(getrennt * sqrt(2.0), zusammen, 1e-12)
    }

    @Test fun ueberlappendeFaelleZaehlenNichtDoppelt() {
        val m = (0 until 52).map { TendenzRueckblick.Messung(7L * it, 1, 0.01) }
        assertEquals(4, TendenzRueckblick.unabhaengig(m, 7 * 13).size)
        // Je Wert getrennt gezaehlt
        val zwei = m + m.map { it.copy(gruppe = "B") }
        assertEquals(8, TendenzRueckblick.unabhaengig(zwei, 7 * 13).size)
    }

    @Test fun belegtNurMitGenugFaellenUndHohemT() {
        // 60 Wochen, jede zweite steigt deutlich in Tendenzrichtung, Stichtage ohne Richtung schwanken um null
        val m = (0 until 120).map { i ->
            if (i % 2 == 0) TendenzRueckblick.Messung(7L * i, 1, 0.02 + 0.01 * rauschen(i))
            else TendenzRueckblick.Messung(7L * i, 0, 0.01 * rauschen(i))
        }
        val z = TendenzRueckblick.auswerten(m, 1)!!
        assertTrue(z.t > TendenzRueckblick.T_SCHWELLE)
        assertTrue(z.treffer - z.grund >= TendenzRueckblick.VORSPRUNG)
        assertTrue(z.belegt)
        // Nur 15 Faelle: zu wenige
        assertFalse(TendenzRueckblick.auswerten(m.take(30), 1)!!.belegt)
    }

    // ------------------------------------------------------------ Bilanz und Vorschlag

    private fun zr(w: Int, t: Double, belegt: Boolean) = TendenzRueckblick.Zeitraum(w, 50, 30, 0.6, 0.5, 0.02, t, t, listOf(0.01, 0.02), belegt)

    @Test fun bilanzNimmtDenBestenBelegten() {
        val s = TendenzRueckblick.Signal(
            TendenzRueckblick.GEPRUEFT,
            gruppen = mapOf("alle" to listOf(zr(4, 2.9, true), zr(13, 3.4, true), zr(26, 3.8, false)), "rot" to listOf(zr(26, 4.0, true))),
        )
        assertEquals(13, TendenzRueckblick.bilanz(s, Fruehwarnung.Ampel.GELB)!!.zeitraum!!.wochen)
        // Heute Rot und die roten Faelle sind fuer sich belegt
        val rot = TendenzRueckblick.bilanz(s, Fruehwarnung.Ampel.ROT)!!
        assertEquals("rot", rot.gruppe)
        assertEquals(26, rot.zeitraum!!.wochen)
        // Nichts belegt: keine Bilanz mit Zeitraum, also kein Vorschlag
        val leer = TendenzRueckblick.bilanz(TendenzRueckblick.Signal(TendenzRueckblick.GEPRUEFT, gruppen = mapOf("alle" to listOf(zr(4, 1.5, false)))))!!
        assertFalse(leer.belegt)
        assertNull(TendenzRueckblick.bilanz(null))
        assertFalse(TendenzRueckblick.bilanz(TendenzRueckblick.Signal(TendenzRueckblick.ZU_KURZ))!!.geprueft)
    }

    @Test fun dateiHinUndZurueck() {
        val d = TendenzRueckblick.Datei("2026-10-28", 2.71, mapOf("rohoel" to TendenzRueckblick.Signal(TendenzRueckblick.GEPRUEFT, gruppen = mapOf("alle" to listOf(zr(13, 3.0, true))))))
        assertEquals(d, TendenzRueckblick.lesen(TendenzRueckblick.schreiben(d)))
    }

    @Test fun vorschlagRichtungUndLaufzeit() {
        val jetzt = LocalDate.of(2026, 10, 28).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val call = TendenzRueckblick.wette("CL=F", 1, 13, 71.3, 0.35, jetzt)!!
        assertTrue(call.call)
        // Laufzeit mindestens das 1,5-Fache von 13 Wochen, aber nicht viel laenger
        val tage = (call.verfall - jetzt) / 86_400_000.0
        assertTrue(tage >= TendenzRueckblick.laufzeitTage(13))
        assertTrue(tage < TendenzRueckblick.laufzeitTage(13) + 35)
        // Basispreis am Geld
        assertEquals(72.5, call.strike, 1e-9)
        assertTrue(call.hebel > 1)
        assertTrue(call.zeitwertWoche > 0)
        val put = TendenzRueckblick.wette("SHY", -1, 4, 82.4, 0.02, jetzt)!!
        assertFalse(put.call)
        assertNull(TendenzRueckblick.wette("CL=F", 0, 13, 71.3, 0.35, jetzt))
        // Basiswerte: Oel als Future, Anleihen als SHY, zyklisch gegen defensiv in zwei Beinen
        assertEquals(listOf("CL=F" to 1), TendenzRueckblick.basiswerte(Fruehindikatoren.CUSHING))
        assertEquals(listOf("ZW=F" to 1), TendenzRueckblick.basiswerte(TendenzRueckblick.schluessel(Fruehindikatoren.DUERRE, "ZW=F")))
        assertEquals(listOf("SHY" to 1), TendenzRueckblick.basiswerte(Fruehindikatoren.ERSTANTRAEGE))
        assertEquals(listOf("XLY" to 1, "XLP" to -1), TendenzRueckblick.basiswerte(TendenzRueckblick.ZYKLISCH))
    }
}
