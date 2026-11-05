package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.Aktienfinder.Daten
import de.troetenrepublik.app.engine.Aktienfinder.Einordnung
import de.troetenrepublik.app.engine.Aktienfinder.Kennzahl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: Aktienfinder mit Bereichen, Gewichten, Einordnung, Gruenden, Filtern und Sortierung.
class Fassung592FinderTest {

    // Fuenf Aktien derselben Branche mit KGV 10 bis 40, damit das Branchenmittel 20 ist.
    private fun branche(): List<Daten> = listOf(10.0, 15.0, 20.0, 30.0, 40.0).mapIndexed { i, kgv ->
        Daten("S$i", "Firma $i", land = "US", branche = "SOFTWARE", kgv = kgv, kgvErwartet = kgv, kbv = 2.0 + i)
    }

    @Test fun standardgewichteErgebenHundert() {
        assertEquals(100, Bereich.entries.sumOf { it.gewicht })
        Kennzahl.entries.forEach { assertTrue(it.bereich in Bereich.entries) }
    }

    @Test fun relativZumBranchenmittel() {
        assertEquals(50.0, Aktienfinder.relativ(20.0, 20.0)!!, 1e-9)
        assertEquals(100.0, Aktienfinder.relativ(10.0, 20.0)!!, 1e-9)
        assertEquals(0.0, Aktienfinder.relativ(40.0, 20.0)!!, 1e-9)
        assertEquals(100.0, Aktienfinder.relativ(2.0, 20.0)!!, 1e-9)
        // Verlust oder fehlendes Mittel: keine Angabe
        assertNull(Aktienfinder.relativ(-5.0, 20.0))
        assertNull(Aktienfinder.relativ(10.0, null))
        assertEquals(20.0, Aktienfinder.median(listOf(10.0, 15.0, 20.0, 30.0, 40.0))!!, 1e-9)
        assertNull(Aktienfinder.median(listOf(10.0, 15.0, 20.0, 30.0)))
        // Ausreisser ueber der Grenze zaehlen nicht
        assertEquals(15.0, Aktienfinder.median(listOf(10.0, 15.0, 20.0, 30.0, 5000.0, -3.0, 12.0), min = 5)!!, 1e-9)
    }

    @Test fun punkteJeKennzahl() {
        assertEquals(70.0, Aktienfinder.pegPunkte(0.5), 1e-9)
        assertEquals(50.0, Aktienfinder.pegPunkte(1.0), 1e-9)
        assertEquals(0.0, Aktienfinder.pegPunkte(3.0), 1e-9)
        assertEquals(100.0, Aktienfinder.renditePunkte(20.0), 1e-9)
        assertEquals(30.0, Aktienfinder.renditePunkte(0.0), 1e-9)
        assertEquals(50.0, Aktienfinder.wachstumPunkte(0.0), 1e-9)
        assertEquals(100.0, Aktienfinder.notePunkte(1.5), 1e-9)
        assertEquals(50.0, Aktienfinder.notePunkte(2.5), 1e-9)
        assertEquals(0.0, Aktienfinder.notePunkte(3.5), 1e-9)
        assertEquals(60.0, Aktienfinder.insiderPunkte(0, true), 1e-9)
        assertEquals(0.0, Aktienfinder.insiderPunkte(-100, false), 1e-9)
        assertEquals(20.0, Aktienfinder.leerPunkte(10.0, us = true), 1e-9)
        assertEquals(85.0, Aktienfinder.leerPunkte(1.0, us = false), 1e-9)
        // Was im Kurs steckt: noetiges gleich erwartetem Wachstum ist neutral, 10 Punkte weniger noetig ist voll
        assertEquals(50.0, Aktienfinder.imKursPunkte(8.0, 8.0), 1e-9)
        assertEquals(100.0, Aktienfinder.imKursPunkte(0.0, 10.0), 1e-9)
        // Erwaehnungen: unter fuenf keine Angabe, verdoppelt deutlich ueber 50
        assertNull(Aktienfinder.erwaehnungPunkte(3, 1))
        assertTrue(Aktienfinder.erwaehnungPunkte(45, 20)!! > 65.0)
        assertTrue(Aktienfinder.erwaehnungPunkte(20, 45)!! < 35.0)
    }

    @Test fun wachstumUndNote() {
        assertEquals(10.0, Aktienfinder.wachstum(2.0, 2.2)!!, 1e-9)
        assertNull(Aktienfinder.wachstum(-1.0, 2.0))
        assertEquals(100.0, Aktienfinder.wachstum(1.0, 9.0)!!, 1e-9)
        assertEquals(2.3, Aktienfinder.noteAusText("2.3 - Buy")!!, 1e-9)
        assertNull(Aktienfinder.noteAusText("Buy"))
        assertNull(Aktienfinder.noteAusText("7.0 - ?"))
    }

    @Test fun raengeMitGleichstand() {
        val r = Aktienfinder.raenge(mapOf("a" to 1.0, "b" to 2.0, "c" to 2.0, "d" to 3.0))
        assertEquals(0.0, r.getValue("a"), 1e-9)
        assertEquals(r.getValue("b"), r.getValue("c"), 1e-9)
        assertEquals(100.0, r.getValue("d"), 1e-9)
        assertEquals(50.0, Aktienfinder.raenge(mapOf("x" to 5.0)).getValue("x"), 1e-9)
    }

    @Test fun guenstigeAktieDerBrancheVorn() {
        val e = Aktienfinder.bewerten(branche()).associateBy { it.symbol }
        val billig = e.getValue("S0")
        val teuer = e.getValue("S4")
        assertTrue(billig.gesamt > teuer.gesamt)
        assertEquals(Einordnung.GUENSTIG, billig.einordnung)
        assertEquals(Einordnung.TEUER, teuer.einordnung)
        assertEquals(Einordnung.FAIR, e.getValue("S2").einordnung)
        // Gruende: hoechstens drei, nach Gewicht; beim teuren Wert sprechen sie gegen ihn
        assertTrue(billig.gruende.size in 1..3)
        assertTrue(billig.gruende.all { it.gut })
        assertTrue(teuer.gruende.none { it.gut })
        assertEquals(Kennzahl.KGV, billig.gruende.first { it.kennzahl == Kennzahl.KGV }.kennzahl)
        assertEquals(20.0, billig.gruende.first { it.kennzahl == Kennzahl.KGV }.vergleich!!, 1e-9)
        // Nur die Bewertung hat Daten: 30 % Abdeckung, der Rest zaehlt mit 50
        assertEquals(0.30, billig.abdeckung, 1e-9)
        assertEquals(50.0 + (billig.bereiche.getValue(Bereich.BEWERTUNG)!! - 50.0) * 0.30, billig.gesamt, 1e-9)
        assertNull(billig.bereiche[Bereich.INSIDER])
    }

    @Test fun imKursMitBegrenztemWachstum() {
        // Gewinn verdoppelt sich im naechsten Jahr: fuer zehn Jahre zaehlen hoechstens 25 %
        val l = branche() + Daten("W", "Erholung", branche = "SOFTWARE", kgv = 20.0, gewinn = 1.0, gewinnErwartet = 2.0)
        val g = Aktienfinder.bewerten(l).first { it.symbol == "W" }
        assertEquals(Aktienfinder.IM_KURS_HOECHSTENS, g.gruende.first { it.kennzahl == Kennzahl.IM_KURS }.vergleich!!, 1e-9)
        assertEquals(100.0, g.kennzahlen.getValue(Kennzahl.WACHSTUM), 1e-9)
    }

    @Test fun ohneZweiBewertungszahlenUnklar() {
        val l = branche() + Daten("X", "Ohne Zahlen", branche = "SOFTWARE", kgv = 12.0)
        assertEquals(Einordnung.UNKLAR, Aktienfinder.bewerten(l).first { it.symbol == "X" }.einordnung)
    }

    @Test fun gewichteVerschiebenDieReihenfolge() {
        val a = Daten("A", "Billig ohne Freunde", branche = "BAU", kgv = 8.0, kgvErwartet = 8.0, note = 4.0)
        val b = Daten("B", "Teuer, aber beliebt", branche = "BAU", kgv = 30.0, kgvErwartet = 30.0, note = 1.5)
        val fueller = (1..5).map { Daten("F$it", "F$it", branche = "BAU", kgv = 15.0, kgvErwartet = 15.0) }
        val nurBewertung = Bereich.entries.associateWith { if (it == Bereich.BEWERTUNG) 100.0 else 0.0 }
        val nurAnalysten = Bereich.entries.associateWith { if (it == Bereich.ANALYSTEN) 100.0 else 0.0 }
        fun erster(g: Map<Bereich, Double>) = Aktienfinder.bewerten(listOf(a, b) + fueller, g).filter { it.symbol in setOf("A", "B") }.maxByOrNull { it.gesamt }!!.symbol
        assertEquals("A", erster(nurBewertung))
        assertEquals("B", erster(nurAnalysten))
        // Ohne jedes Gewicht kein Ergebnis
        assertTrue(Aktienfinder.bewerten(listOf(a), Bereich.entries.associateWith { 0.0 }).isEmpty())
    }

    @Test fun bankenOhneSchuldenUndBilanz() {
        val bank = Daten("BK", "Bank", branche = "BANKEN", verschuldung = 8.0, bilanz = Bewertungsblick.Ampel.ROT)
        val firma = Daten("FI", "Firma", branche = "BAU", verschuldung = 8.0, bilanz = Bewertungsblick.Ampel.ROT)
        val e = Aktienfinder.bewerten(listOf(bank, firma)).associateBy { it.symbol }
        assertFalse(Kennzahl.SCHULDEN in e.getValue("BK").kennzahlen)
        assertFalse(Kennzahl.BILANZ in e.getValue("BK").kennzahlen)
        assertEquals(0.0, e.getValue("FI").kennzahlen.getValue(Kennzahl.SCHULDEN), 1e-9)
        assertEquals(15.0, e.getValue("FI").kennzahlen.getValue(Kennzahl.BILANZ), 1e-9)
    }

    @Test fun trendAlsRangUndLinie() {
        val l = listOf(
            Daten("A", "A", jahr = -20.0, linie200 = -20.0),
            Daten("B", "B", jahr = 5.0, linie200 = 0.0),
            Daten("C", "C", jahr = 60.0, linie200 = 20.0),
        )
        val e = Aktienfinder.bewerten(l).associateBy { it.symbol }
        assertEquals(0.0, e.getValue("A").bereiche.getValue(Bereich.TREND)!!, 1e-9)
        assertEquals(50.0, e.getValue("B").bereiche.getValue(Bereich.TREND)!!, 1e-9)
        assertEquals(100.0, e.getValue("C").bereiche.getValue(Bereich.TREND)!!, 1e-9)
    }

    @Test fun insiderWertBeiWenigenPersonenGedaempft() {
        val eine = InsiderTrend.auswerten(listOf(InsiderTrend.Geschaeft("Chefin", InsiderTrend.Rolle.SPITZE, true, 50_000.0, "2026-09-01")), "2026-01-01")!!
        assertEquals(100, eine.wert)
        assertEquals(33, Aktienfinder.insiderWert(eine))
        val drei = InsiderTrend.auswerten((1..3).map { InsiderTrend.Geschaeft("P$it", InsiderTrend.Rolle.AUFSICHT, false, 10_000.0, "2026-09-0$it") }, "2026-01-01")!!
        assertEquals(-100, Aktienfinder.insiderWert(drei))
    }

    @Test fun filternUndSortieren() {
        val l = Aktienfinder.bewerten(
            branche().mapIndexed { i, d ->
                d.copy(land = if (i % 2 == 0) "US" else "DE", dividende = i.toDouble(), boersenwert = (i + 1) * 3e9)
            },
        )
        val alle = Aktienfinder.auswahl(l, Aktienfinder.Filter(), Aktienfinder.Sortierung.Gesamt)
        assertEquals(listOf("S0", "S1", "S2", "S3", "S4"), alle.map { it.symbol })
        assertEquals(listOf("S1", "S3"), Aktienfinder.auswahl(l, Aktienfinder.Filter(land = "DE"), Aktienfinder.Sortierung.Gesamt).map { it.symbol })
        assertEquals(listOf("S4", "S3"), Aktienfinder.auswahl(l, Aktienfinder.Filter(dividende = 3.0), Aktienfinder.Sortierung.Dividende).map { it.symbol })
        // Groesse: 3 Mrd. mittel, ab 12 Mrd. gross
        assertEquals(listOf("S0", "S1", "S2"), Aktienfinder.auswahl(l, Aktienfinder.Filter(groesse = Aktienfinder.Groesse.MITTEL), Aktienfinder.Sortierung.Gesamt).map { it.symbol })
        assertEquals(Aktienfinder.Groesse.KLEIN, Aktienfinder.groesse(1e9))
        assertNull(Aktienfinder.groesse(null))
        // Region ueber die Abbildung des Landes
        assertEquals(2, Aktienfinder.auswahl(l, Aktienfinder.Filter(region = "D"), Aktienfinder.Sortierung.Gesamt) { it.take(1) }.size)
        assertEquals(listOf("S0", "S1"), Aktienfinder.auswahl(l, Aktienfinder.Filter(einordnung = Einordnung.GUENSTIG), Aktienfinder.Sortierung.Gesamt).map { it.symbol })
        // Mindestpunktzahl im sortierten Bereich
        val teil = Aktienfinder.auswahl(l, Aktienfinder.Filter(mindest = 60.0), Aktienfinder.Sortierung.Teil(Bereich.BEWERTUNG))
        assertTrue(teil.isNotEmpty() && teil.all { it.bereiche.getValue(Bereich.BEWERTUNG)!! >= 60.0 })
        // Einzelabrufe fuer die besten ohne Details
        assertEquals(listOf("S0", "S1"), Aktienfinder.fuerDetails(l, 2))
    }
}
