package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Analystenbilanz.Urteil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: Trefferbilanz der Analystenhaeuser und bereinigte Einschaetzung.
class Fassung592AnalystenTest {

    private val tag = 86_400_000L
    // 14 Uhr UTC, etwa Handelsbeginn in New York
    private val start = 18_500L * tag + 14 * 3_600_000L

    private fun t(d: Int) = start + d * tag

    // Tageskurse aus einer Funktion des Tages
    private fun kurse(tage: Int, kurs: (Int) -> Double) = (0 until tage).map { d -> Candle(t(d), kurs(d), kurs(d), kurs(d), kurs(d)) }

    @Test fun einstufungen() {
        assertEquals(2, Analystenbilanz.stufe("Strong Buy"))
        assertEquals(1, Analystenbilanz.stufe("Buy"))
        assertEquals(1, Analystenbilanz.stufe("Outperform"))
        assertEquals(1, Analystenbilanz.stufe("Overweight"))
        assertEquals(1, Analystenbilanz.stufe("Sector Outperform"))
        assertEquals(0, Analystenbilanz.stufe("Market Perform"))
        assertEquals(0, Analystenbilanz.stufe("Equal-Weight"))
        assertEquals(0, Analystenbilanz.stufe("Neutral"))
        assertEquals(0, Analystenbilanz.stufe("Hold"))
        assertEquals(0, Analystenbilanz.stufe("Sector Weight"))
        assertEquals(-1, Analystenbilanz.stufe("Underweight"))
        assertEquals(-1, Analystenbilanz.stufe("Underperform"))
        assertEquals(-1, Analystenbilanz.stufe("Market Underperform"))
        assertEquals(-1, Analystenbilanz.stufe("Reduce"))
        assertEquals(-1, Analystenbilanz.stufe("Sell"))
        assertEquals(-2, Analystenbilanz.stufe("Strong Sell"))
        assertNull(Analystenbilanz.stufe(""))
        assertNull(Analystenbilanz.stufe("Speculative"))
        assertEquals(1, Analystenbilanz.stufeAus(0.6))
        assertEquals(0, Analystenbilanz.stufeAus(-0.4))
        assertEquals(-2, Analystenbilanz.stufeAus(-7.0))
    }

    @Test fun richtungen() {
        assertEquals(1, Analystenbilanz.richtung(Urteil("A", 0, "Buy", "Hold", "up")))
        assertEquals(-1, Analystenbilanz.richtung(Urteil("A", 0, "Hold", "Buy", "down")))
        assertEquals(1, Analystenbilanz.richtung(Urteil("A", 0, "Outperform", "", "init")))
        assertEquals(-1, Analystenbilanz.richtung(Urteil("A", 0, "Underweight", "", "init")))
        // Neutrale Erstbewertung und Bestaetigungen sind kein Urteil ueber die Richtung
        assertNull(Analystenbilanz.richtung(Urteil("A", 0, "Neutral", "", "init")))
        assertNull(Analystenbilanz.richtung(Urteil("A", 0, "Buy", "Buy", "main")))
        assertNull(Analystenbilanz.richtung(Urteil("A", 0, "Buy", "Buy", "reit")))
        // Widerspruechliche Angabe (Hochstufung nach unten) zaehlt nicht
        assertNull(Analystenbilanz.richtung(Urteil("A", 0, "Hold", "Buy", "up")))
        // Unbekannte Stufen: die Art entscheidet
        assertEquals(1, Analystenbilanz.richtung(Urteil("A", 0, "Speculative", "", "up")))
    }

    @Test fun renditeGegenIndex() {
        val aktie = kurse(400) { 100.0 * Math.pow(1.001, it.toDouble()) }
        val flach = kurse(400) { 100.0 }
        val schnell = kurse(400) { 100.0 * Math.pow(1.002, it.toDouble()) }
        val gegenFlach = Analystenbilanz.mehrRendite(aktie, flach, t(10), 63)!!
        assertEquals(Math.pow(1.001, 63.0) - 1.0, gegenFlach, 1e-9)
        // Gegen einen staerker steigenden Index lief die Aktie schlechter
        assertTrue(Analystenbilanz.mehrRendite(aktie, schnell, t(10), 63)!! < 0)
        // Ohne Index die einfache Rendite
        assertEquals(gegenFlach, Analystenbilanz.mehrRendite(aktie, null, t(10), 63)!!, 1e-9)
        // Noch nicht verstrichen
        assertNull(Analystenbilanz.mehrRendite(aktie, flach, t(300), 252))
        // Urteil vor Handelsbeginn zaehlt ab dem Schlusskurs desselben Tags
        assertEquals(gegenFlach, Analystenbilanz.mehrRendite(aktie, flach, t(10) - 10 * 3_600_000L, 63)!!, 1e-9)
    }

    @Test fun herde() {
        val u = listOf(Urteil("A", t(5), "Buy", "Hold", "up"), Urteil("B", t(6), "Buy", "Hold", "up"), Urteil("C", t(7), "Buy", "Hold", "up"),
            Urteil("D", t(40), "Buy", "Hold", "up"))
        val b = Analystenbilanz.bewerten(u, kurse(400) { 100.0 + it }, null)
        assertTrue(b.first { it.urteil.haus == "A" }.herde)
        assertTrue(b.first { it.urteil.haus == "C" }.herde)
        assertFalse(b.first { it.urteil.haus == "D" }.herde)
    }

    // Kurs steigt und faellt in Abschnitten von 300 Tagen; Haus A liegt immer richtig, Haus B immer falsch.
    private fun wechselkurse(): List<Candle> {
        var k = 100.0
        return (0 until 1520).map { d ->
            if (d > 0) k *= if ((d / 300) % 2 == 0) 1.002 else 0.998
            Candle(t(d), k, k, k, k)
        }
    }

    private fun wechselurteile(): List<Urteil> = buildList {
        listOf(0, 600, 1200).forEach { d ->
            add(Urteil("A", t(d), "Buy", "Hold", "up", ziel = 1000.0))
            add(Urteil("B", t(d), "Underperform", "Hold", "down"))
        }
        listOf(300, 900).forEach { d ->
            add(Urteil("A", t(d), "Hold", "Buy", "down"))
            add(Urteil("B", t(d), "Buy", "Hold", "up"))
        }
    }

    @Test fun trefferbilanzUndBereinigung() {
        val kurse = wechselkurse()
        val index = kurse(1520) { 100.0 }
        val e = Analystenbilanz.auswerten(wechselurteile(), kurse, index, kurs = 120.0, jetzt = t(1519))
        val a = e.haeuser.first { it.name == "A" }
        val b = e.haeuser.first { it.name == "B" }
        // Fuenf Urteile je Haus, alle drei Horizonte verstrichen (ausser beim juengsten: 1200 + 252 < 1519)
        assertEquals(5, a.bewertet)
        assertEquals(1.0, a.quote!!, 1e-9)
        assertEquals(0.0, b.quote!!, 1e-9)
        assertEquals(listOf(5 to 5, 5 to 5, 5 to 5), a.treffer)
        // Schrumpfung zur Mitte: auch fuenf Treffer ergeben keine 100 %
        assertTrue(a.geschrumpft < 1.0 && a.geschrumpft > 0.6)
        assertTrue(b.geschrumpft > 0.0 && b.geschrumpft < 0.4)
        assertTrue(a.gewicht > b.gewicht)
        assertTrue(a.gewicht <= 1.5 && b.gewicht >= 0.5)
        // Roh heben sich Kaufen (+1) und Untergewichten (-1) auf, bereinigt zaehlt A mehr
        assertEquals(0.0, e.roh!!, 1e-9)
        assertTrue(e.bereinigt!! > 0.3)
        assertEquals(2, e.aktive)
        assertTrue(e.mitIndex)
        // Gesamt: Haelfte richtig, das ist Zufall
        assertEquals(10, e.gesamt[1].second)
        assertTrue(e.wenigAussagekraft)
        // Kursziel nur von A
        assertEquals(1000.0, e.zielRoh!!, 1e-9)
    }

    @Test fun wenigeUndAlteUrteile() {
        val kurse = kurse(800) { 100.0 + it * 0.1 }
        val u = listOf(
            Urteil("Neu", t(700), "Buy", "Hold", "up", ziel = 90.0),
            Urteil("Alt", t(100), "Sell", "Hold", "down"),
        )
        val e = Analystenbilanz.auswerten(u, kurse, null, kurs = 180.0, jetzt = t(799))
        val neu = e.haeuser.first { it.name == "Neu" }
        val alt = e.haeuser.first { it.name == "Alt" }
        // Unter der Mindestzahl keine eigene Quote; ein Treffer zieht die geschrumpfte nur wenig ueber 50 %
        assertNull(neu.quote)
        assertEquals(1, neu.bewertet)
        assertTrue(neu.geschrumpft > 0.5 && neu.geschrumpft < 0.7)
        assertNull(alt.quote)
        assertTrue(alt.geschrumpft < 0.5)
        // Aelter als ein Jahr: keine aktuelle Haltung
        assertNull(alt.stufe)
        assertEquals(1, neu.stufe)
        assertEquals(1, e.aktive)
        // Kaufen mit Kursziel unter dem Kurs passt nicht zusammen
        assertTrue(neu.widerspruch)
        assertFalse(e.mitIndex)
        // Aktuelle Haeuser zuerst
        assertEquals("Neu", e.haeuser.first().name)
    }

    @Test fun verteilung() {
        val v = Analystenbilanz.Verteilung("0m", 6, 18, 14, 3, 3)
        assertEquals(44, v.summe)
        assertEquals((12.0 + 18 - 3 - 6) / 44, v.mittel!!, 1e-9)
        assertNull(Analystenbilanz.Verteilung("0m", 0, 0, 0, 0, 0).mittel)
        assertNotNull(Analystenbilanz.Verteilung("-1m", 0, 1, 0, 0, 0).mittel)
    }
}
