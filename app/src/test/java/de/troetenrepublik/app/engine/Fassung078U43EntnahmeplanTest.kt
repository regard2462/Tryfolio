package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Entnahmeplan.Plan
import de.troetenrepublik.app.engine.Entnahmeplan.Regel
import de.troetenrepublik.app.engine.HistorieJst.Art
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.EntnahmeTexte
import de.troetenrepublik.app.ui.PortfolioTool
import de.troetenrepublik.app.ui.WERKZEUG_GRUPPEN
import de.troetenrepublik.app.ui.werkzeugFormen
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Werkzeug „Entnahmeplan“: jede Entnahmeregel und die Steuer an kleinen Reihen, die sich von Hand nachrechnen lassen,
// Eckfaelle, Ansparen und Zielvermoegen, die gebuendelten Renditen, Texte in beiden Sprachen.
class Fassung078U43EntnahmeplanTest {
    private val e = Entnahmeplan

    private fun folge(vararg aktien: Double, inflation: Double = 0.0) =
        Entnahmeplan.Folge(aktien, DoubleArray(aktien.size), DoubleArray(aktien.size) { inflation })

    // Jahresreihe ab 1900 mit gleicher Rendite fuer Aktien und sichere Anlage
    private fun reihe(n: Int, rendite: (Int) -> Double = { 0.0 }, inflation: Double = 0.0) =
        Entnahmeplan.Reihe("TEST", IntArray(n) { 1900 + it }, DoubleArray(n) { rendite(it) }, DoubleArray(n) { rendite(it) }, DoubleArray(n) { inflation })

    private fun plan(v: Double, w: Double, jahre: Int, regel: Regel = Regel.FEST) = Plan(v, w, jahre, 1.0, 0.0, regel)

    // ------------------------------------------------------------ Regeln

    @Test fun festerBetragOhneRendite() {
        val l = e.lauf(plan(100.0, 10.0, 10), folge(*DoubleArray(10)))
        assertArrayEquals(DoubleArray(11) { 100.0 - 10.0 * it }, l.vermoegen, 1e-9)
        assertArrayEquals(DoubleArray(10) { 10.0 }, l.entnahmen, 1e-9)
        assertNull(l.leerIm)
        // Ein Euro mehr im Jahr: im zehnten Jahr reicht es nicht mehr
        val knapp = e.lauf(plan(100.0, 11.0, 10), folge(*DoubleArray(10)))
        assertEquals(9, knapp.leerIm)
        assertEquals(1.0, knapp.entnahmen[9], 1e-9)
        assertEquals(0.0, knapp.vermoegen[10], 0.0)
    }

    @Test fun festerBetragMitRenditeKostenUndMischung() {
        // Entnahme zu Jahresbeginn, dann 10 % Rendite: (100 - 10) * 1,1 = 99; (99 - 10) * 1,1 = 97,9
        val l = e.lauf(plan(100.0, 10.0, 2), folge(0.10, 0.10))
        assertArrayEquals(doubleArrayOf(100.0, 99.0, 97.9), l.vermoegen, 1e-9)
        // Halb Aktien (+20 %), halb sicher (0 %), 1 % Kosten: 90 * 1,10 * 0,99 = 98,01
        val gemischt = e.lauf(Plan(100.0, 10.0, 1, 0.5, 0.01), folge(0.20))
        assertEquals(98.01, gemischt.vermoegen[1], 1e-9)
    }

    @Test fun festerProzentsatzMitGrenzen() {
        val f = folge(0.0, 0.0, 0.0)
        assertArrayEquals(doubleArrayOf(10.0, 9.0, 8.1), e.lauf(plan(100.0, 10.0, 3, Regel.PROZENT), f).entnahmen, 1e-9)
        // Untergrenze 95 % des Bedarfs: 10; 9,5 statt 9; 9,5 statt 8,05
        val unten = e.lauf(plan(100.0, 10.0, 3, Regel.PROZENT).copy(untergrenze = 0.95), f)
        assertArrayEquals(doubleArrayOf(10.0, 9.5, 9.5), unten.entnahmen, 1e-9)
        // Obergrenze 110 % des Bedarfs nach einer Verdopplung: 10 % von 180 waeren 18
        val oben = e.lauf(plan(100.0, 10.0, 2, Regel.PROZENT).copy(obergrenze = 1.1), folge(1.0, 0.0))
        assertArrayEquals(doubleArrayOf(10.0, 11.0), oben.entnahmen, 1e-9)
        // Ohne Grenze geht das Geld nie aus
        assertNull(e.lauf(plan(100.0, 10.0, 3, Regel.PROZENT), folge(-0.5, -0.5, -0.5)).leerIm)
    }

    @Test fun restlaufzeitVerteiltAlsRente() {
        // Zins 0: ein Viertel, ein Drittel, die Haelfte, alles
        val gleich = e.lauf(plan(100.0, 0.0, 4, Regel.RESTLAUFZEIT), folge(0.0, 0.0, 0.0, 0.0))
        assertArrayEquals(doubleArrayOf(25.0, 25.0, 25.0, 25.0), gleich.entnahmen, 1e-9)
        assertEquals(0.0, gleich.vermoegen[4], 1e-9)
        // Zins 10 % und 10 % Rendite: zwei gleiche Raten von 100 * 1,1 / 2,1
        val mitZins = e.lauf(plan(100.0, 0.0, 2, Regel.RESTLAUFZEIT).copy(zins = 0.10), folge(0.10, 0.10))
        assertArrayEquals(doubleArrayOf(110.0 / 2.1, 110.0 / 2.1), mitZins.entnahmen, 1e-9)
        assertEquals(1.0, e.rentenAnteil(0.05, 1), 0.0)
        assertEquals(0.1, e.rentenAnteil(0.0, 10), 1e-12)
    }

    @Test fun leitplankenKuerzenUndErhoehen() {
        // Halbierung im ersten Jahr: 5 / 47,5 liegt mehr als 20 % ueber 5 %, also 10 % weniger
        val runter = e.lauf(plan(100.0, 5.0, 20, Regel.LEITPLANKEN), folge(-0.5, *DoubleArray(19)))
        assertEquals(5.0, runter.entnahmen[0], 1e-9)
        assertEquals(4.5, runter.entnahmen[1], 1e-9)
        // Verdopplung: 5 / 190 liegt mehr als 20 % unter 5 %, also 10 % mehr
        val hoch = e.lauf(plan(100.0, 5.0, 20, Regel.LEITPLANKEN), folge(1.0, *DoubleArray(19)))
        assertEquals(5.5, hoch.entnahmen[1], 1e-9)
        // Innerhalb des Bands bleibt die Entnahme
        val ruhig = e.lauf(plan(100.0, 5.0, 20, Regel.LEITPLANKEN), folge(0.05, *DoubleArray(19)))
        assertEquals(5.0, ruhig.entnahmen[1], 1e-9)
        // In den letzten 15 Jahren wird nicht mehr gekuerzt
        val spaet = e.lauf(plan(100.0, 5.0, 15, Regel.LEITPLANKEN), folge(-0.5, *DoubleArray(14)))
        assertEquals(5.0, spaet.entnahmen[1], 1e-9)
    }

    @Test fun leitplankenOhneInflationsausgleichNachVerlustjahr() {
        // Real -50 % bei 10 % Inflation: nominal Verlust, Quote ueber der anfaenglichen. Die Entnahme verliert die Inflation
        // (5 / 1,1) und wird danach um 10 % gekuerzt.
        val l = e.lauf(plan(100.0, 5.0, 20, Regel.LEITPLANKEN), folge(-0.5, *DoubleArray(19), inflation = 0.10))
        assertEquals(5.0 / 1.1 * 0.9, l.entnahmen[1], 1e-9)
        // Real -5 % bei 10 % Inflation ist nominal ein Gewinn: voller Ausgleich, Quote 5 / 90,25 im Band
        val gewinn = e.lauf(plan(100.0, 5.0, 20, Regel.LEITPLANKEN), folge(-0.05, *DoubleArray(19), inflation = 0.10))
        assertEquals(5.0, gewinn.entnahmen[1], 1e-9)
    }

    // ------------------------------------------------------------ Steuer

    @Test fun verkaufMitSteuer() {
        // Haelfte des Erloeses ist Gewinn, Satz 25 %: brutto = 1000 / (1 - 0,125)
        val v = e.verkauf(1_000.0, 10_000.0, 5_000.0, 0.25, 0.0, 0.0)
        assertEquals(1_000.0 / 0.875, v.brutto, 1e-9)
        assertEquals(v.brutto * 0.5 * 0.25, v.steuer, 1e-9)
        assertEquals(1_000.0, v.netto, 1e-9)
        // Pauschbetrag 100: brutto = (1000 - 25) / 0,875, Steuer auf den Gewinn ueber 100
        val frei = e.verkauf(1_000.0, 10_000.0, 5_000.0, 0.25, 0.0, 100.0)
        assertEquals(975.0 / 0.875, frei.brutto, 1e-9)
        assertEquals((frei.brutto * 0.5 - 100.0) * 0.25, frei.steuer, 1e-9)
        // Gewinn unter dem Pauschbetrag: keine Steuer
        assertEquals(0.0, e.verkauf(100.0, 10_000.0, 5_000.0, 0.25, 0.0, 100.0).steuer, 0.0)
        // Teilfreistellung 30 %: nur 70 % des Gewinns zaehlen
        val tf = e.verkauf(1_000.0, 10_000.0, 5_000.0, 0.25, 0.30, 0.0)
        assertEquals(1_000.0 / (1.0 - 0.25 * 0.5 * 0.7), tf.brutto, 1e-9)
        // Bestand im Verlust: keine Steuer
        assertEquals(0.0, e.verkauf(1_000.0, 10_000.0, 12_000.0, 0.25, 0.0, 0.0).steuer, 0.0)
        // Bestand reicht nicht: alles wird verkauft, netto bleibt darunter
        val alles = e.verkauf(1_000.0, 800.0, 400.0, 0.25, 0.0, 0.0)
        assertEquals(800.0, alles.brutto, 0.0)
        assertEquals(700.0, alles.netto, 1e-9)
    }

    @Test fun laufMitSteuer() {
        val steuer = Entnahmeplan.Steuer(0.25, 0.0, 0.0, 0.5)
        val l = e.lauf(Plan(10_000.0, 1_000.0, 2, 1.0, 0.0, steuer = steuer), folge(0.0, 0.0))
        // Jahr 1: brutto 1142,857; der Einstand sinkt im selben Verhaeltnis, der Gewinnanteil bleibt 50 %
        val brutto = 1_000.0 / 0.875
        assertEquals(10_000.0 - brutto, l.vermoegen[1], 1e-9)
        assertEquals(10_000.0 - 2 * brutto, l.vermoegen[2], 1e-9)
        assertEquals(2 * (brutto - 1_000.0), l.steuern, 1e-9)
        assertArrayEquals(doubleArrayOf(1_000.0, 1_000.0), l.entnahmen, 1e-9)
        // Teilfreistellung gilt nur fuer den Aktienanteil: halbe Aktien, halbe Wirkung
        val halb = e.lauf(Plan(10_000.0, 1_000.0, 1, 0.5, 0.0, steuer = steuer.copy(teilfreistellung = 0.30)), folge(0.0))
        assertEquals(10_000.0 - 1_000.0 / (1.0 - 0.25 * 0.5 * 0.85), halb.vermoegen[1], 1e-9)
        // Inflation laesst den Einstand in Kaufkraft schrumpfen: ohne Gewinn am Anfang entsteht im zweiten Jahr einer
        val ohne = e.lauf(Plan(10_000.0, 1_000.0, 2, 1.0, 0.0, steuer = steuer.copy(gewinnanteil = 0.0)), folge(0.0, 0.0, inflation = 0.10))
        assertEquals(9_000.0, ohne.vermoegen[1], 1e-9)
        assertTrue(ohne.steuern > 0.0)
        assertEquals(1_000.0 / (1.0 - 0.25 * (1.0 - 1.0 / 1.1)), 9_000.0 - ohne.vermoegen[2], 1e-9)
    }

    // ------------------------------------------------------------ Eckfaelle

    @Test fun eckfaelle() {
        val r = reihe(80)
        // Entnahme 0: das Geld reicht immer, das Vermoegen bleibt
        val null0 = e.historisch(r, plan(100.0, 0.0, 30))!!
        assertEquals(null0.faelle, null0.reicht)
        assertEquals(100.0, null0.p50[30], 1e-9)
        // Vermoegen 0: reicht nie, nichts wird ausgezahlt
        val leer = e.historisch(r, plan(0.0, 10.0, 30))!!
        assertEquals(0, leer.reicht)
        assertEquals(0, leer.schlechtLeerIm)
        assertEquals(0.0, leer.entMin, 0.0)
        // Vermoegen 0 und Entnahme 0: nichts fehlt
        assertEquals(51, e.historisch(r, plan(0.0, 0.0, 30))!!.reicht)
        // Dauer laenger als die Daten oder als die Obergrenze: kein Ergebnis
        assertNull(e.historisch(reihe(40), plan(100.0, 1.0, 50)))
        assertNull(e.historisch(r, plan(100.0, 1.0, 61)))
        assertNull(e.hoechsteEntnahme(reihe(40), plan(100.0, 1.0, 50), 1.0))
        assertNull(e.zielvermoegen(reihe(40), plan(0.0, 1.0, 50)))
        assertNull(e.ziehung(r, plan(100.0, 1.0, 61)))
        // Zu wenige Startjahre: 30 Jahre in 40 Jahren Daten ergeben nur 11
        assertNull(e.historisch(reihe(40), plan(100.0, 1.0, 30)))
        assertEquals(0.0, e.hoechsteEntnahme(r, plan(0.0, 1.0, 30), 1.0)!!, 0.0)
        assertEquals(0.0, e.zielvermoegen(r, plan(0.0, 0.0, 30))!!, 0.0)
    }

    @Test fun startsUndFolgen() {
        // Luecke nach dem dritten Jahr
        val r = Entnahmeplan.Reihe("X", intArrayOf(1900, 1901, 1902, 1904, 1905), DoubleArray(5), DoubleArray(5), DoubleArray(5))
        assertArrayEquals(intArrayOf(0, 1, 3), e.starts(r, 2))
        assertArrayEquals(intArrayOf(0), e.starts(r, 3))
        assertEquals(0, e.starts(r, 4).size)
        assertEquals(0, e.starts(r, 0).size)
        // Monatsreihe: zwoelf Perioden werden zu einem Jahr verkettet, jeder Monat ist ein Start
        val m = Entnahmeplan.Reihe("M", IntArray(36) { 1900 * 12 + it }, DoubleArray(36) { 0.01 }, DoubleArray(36), DoubleArray(36), perJahr = 12)
        assertEquals(13, e.starts(m, 2).size)
        val f = e.folge(m, 5, 2)
        assertEquals(2, f.jahre)
        assertEquals(Math.pow(1.01, 12.0) - 1.0, f.aktien[1], 1e-12)
        assertEquals(1900, m.jahr(11))
        assertEquals(1901, m.jahr(12))
        assertEquals(Math.pow(1.01, 12.0) - 1.0, e.mittlereRendite(m, 1.0, 0.0), 1e-12)
    }

    // ------------------------------------------------------------ Auswertung

    @Test fun historischZaehltUndFindetDenSchlechtestenStart() {
        // Nur das Jahr 1910 verliert 50 %, sonst 0 %: wer 1910 beginnt, verliert am meisten
        val r = reihe(60, { if (it == 10) -0.5 else 0.0 })
        val x = e.historisch(r, plan(100.0, 5.0, 20))!!
        assertEquals(41, x.faelle)
        assertEquals(1900, x.ersterStart)
        assertEquals(1940, x.letzterStart)
        // Start 1910: (100 - 5) / 2 = 47,5 reichen fuer neun weitere Jahre, im elften Jahr (Zaehlung ab 0: 10) fehlt Geld.
        // Start 1909 haelt ein Jahr laenger: (95 - 5) / 2 = 45 reichen fuer neun Jahre nach dem zweiten.
        assertEquals(1910, x.schlechtStart)
        assertEquals(10, x.schlechtLeerIm)
        // Ohne den Einbruch im Zeitraum reicht es genau (20 * 5 = 100): die 30 Starts ab 1911
        assertEquals(30, x.reicht)
        assertEquals(100.0, x.p50[0], 0.0)
        assertEquals(5.0, x.entMedian, 1e-9)
        assertEquals(0.0, x.entMin, 1e-9)
    }

    @Test fun hoechsteEntnahmeUndZielvermoegen() {
        val r = reihe(80)
        // Ohne Rendite haelt genau Vermoegen / Jahre
        assertEquals(100.0 / 30, e.hoechsteEntnahme(r, plan(100.0, 1.0, 30), 1.0)!!, 1e-6)
        assertEquals(300.0, e.zielvermoegen(r, plan(0.0, 10.0, 30))!!, 1e-6)
        // Fester Prozentsatz ohne Grenze haelt jede Quote
        assertEquals(100.0, e.hoechsteEntnahme(r, plan(100.0, 1.0, 30, Regel.PROZENT), 1.0)!!, 1e-6)
        // Mit einem schlechten Jahr: alle Verlaeufe verlangen weniger als 95 % der Verlaeufe
        val s = reihe(80, { if (it == 40) -0.5 else 0.0 })
        val alle = e.hoechsteEntnahme(s, plan(100.0, 1.0, 20), 1.0)!!
        val teil = e.hoechsteEntnahme(s, plan(100.0, 1.0, 20), 0.5)!!
        assertTrue(alle < teil)
        assertEquals(5.0, teil, 1e-6)
    }

    @Test fun blockziehungIstFestUndBegrenzt() {
        val r = reihe(80, { if (it % 7 == 0) -0.3 else 0.08 })
        val p = plan(100.0, 5.0, 30)
        val a = e.ziehung(r, p)!!
        val b = e.ziehung(r, p)!!
        assertEquals(Entnahmeplan.ZIEHUNGEN, a.verlaeufe)
        assertEquals(a.reicht, b.reicht)
        assertTrue(a.reicht in 1 until a.verlaeufe)
        assertEquals(Entnahmeplan.MAX_ZIEHUNGEN, e.ziehung(r, p, verlaeufe = 1_000_000)!!.verlaeufe)
        assertEquals(Entnahmeplan.BLOCK_JAHRE, a.block)
        // Ohne Rendite und ohne Entnahme reicht jeder Verlauf
        assertEquals(1.0, e.ziehung(reihe(80), plan(100.0, 0.0, 30))!!.anteil, 0.0)
    }

    @Test fun reihenfolgeAendertDasErgebnis() {
        val f = folge(-0.5, 0.0, 1.0)
        val p = plan(100.0, 10.0, 3)
        // (100-10)*0,5 = 45; 35; (35-10)*2 = 50   gegen   (100-10)*2 = 180; 170; (170-10)*0,5 = 80
        assertEquals(50.0, e.lauf(p, f).vermoegen[3], 1e-9)
        assertEquals(80.0, e.lauf(p, f.umgekehrt()).vermoegen[3], 1e-9)
    }

    // ------------------------------------------------------------ Ansparen

    @Test fun ansparenOhneRendite() {
        val r = reihe(80)
        // 100 im Monat bis 12.000: genau zehn Jahre in jedem Verlauf
        val a = e.ansparen(r, 0.0, 0.0, 100.0, 12_000.0, 1.0, 0.0)!!
        assertEquals(60, a.horizont)
        assertEquals(21, a.faelle)
        assertEquals(21, a.erreicht)
        assertEquals(10.0, a.min!!, 1e-9)
        assertEquals(10.0, a.max!!, 1e-9)
        assertEquals(10.0, a.median!!, 1e-9)
        assertEquals(0.0, a.gewinnanteil!!, 1e-9)
        assertEquals(6_000.0, a.p50[5], 1e-9)
        // Startwert schon am Ziel
        assertEquals(0.0, e.ansparen(r, 500.0, 500.0, 0.0, 400.0, 1.0, 0.0)!!.max!!, 0.0)
        // Ohne Sparrate und ohne Rendite wird das Ziel nie erreicht
        val nie = e.ansparen(r, 100.0, 100.0, 0.0, 400.0, 1.0, 0.0)!!
        assertEquals(0, nie.erreicht)
        assertNull(nie.median)
        assertNull(nie.min)
        // Kuerzere Reihe: das Fenster schrumpft, bis genug Startjahre da sind
        assertEquals(40, e.ansparen(reihe(65), 0.0, 0.0, 100.0, 12_000.0, 1.0, 0.0)!!.horizont)
        assertNull(e.ansparen(reihe(45), 0.0, 0.0, 100.0, 12_000.0, 1.0, 0.0))
        assertEquals(10, e.volleJahre(10.0))
        assertEquals(11, e.volleJahre(10.01))
    }

    @Test fun ansparenMitRenditeUndGewinnanteil() {
        // 1 % im Monat ist eine Jahresrendite von 1,01^12 - 1; nach zwoelf Monaten mit 100 im Monat: Rentenendwert
        val r = reihe(80, { Math.pow(1.01, 12.0) - 1.0 })
        val endwert = 100.0 * (Math.pow(1.01, 12.0) - 1.0) / 0.01
        val a = e.ansparen(r, 0.0, 0.0, 100.0, endwert - 1e-6, 1.0, 0.0)!!
        assertEquals(1.0, a.max!!, 1e-9)
        assertEquals(1.0 - 1_200.0 / endwert, a.gewinnanteil!!, 1e-6)
    }

    @Test fun freiheitVerbindetZielUndWeg() {
        val r = reihe(80)
        val ohne = e.freiheit(r, 0.0, 0.0, 1_000.0, plan(0.0, 12_000.0, 30))!!
        assertEquals(360_000.0, ohne.ziel, 1e-3)
        assertEquals(30.0, ohne.ansparen.median!!, 1.0 / 12 + 1e-9)
        // Mit Steuer und Inflation braucht dieselbe Entnahme mehr Vermoegen
        val teuer = reihe(80, inflation = 0.03)
        val mit = e.freiheit(teuer, 0.0, 0.0, 1_000.0, plan(0.0, 12_000.0, 30).copy(steuer = Entnahmeplan.Steuer(0.26375, 0.3, 1_000.0, 0.0)))!!
        assertTrue(mit.ziel > 360_000.0)
        assertTrue(mit.ansparen.gewinnanteil!! > 0.0)
    }

    // ------------------------------------------------------------ Gebuendelte Renditen

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val jst by lazy { HistorieJst.lesen(asset("globus/jst.json").readText(Charsets.UTF_8))!! }

    @Test fun reihenAusDerDatei() {
        val k = e.kennungen(jst)
        assertEquals("USA", k[0])
        assertEquals(Entnahmeplan.WELT, k[1])
        assertTrue(k.size >= 10)
        assertFalse("CAN" in k)
        val usa = e.reihe(jst, "USA")!!
        // Jedes Jahr der Reihe hat alle drei Werte der Quelle
        val land = jst.laender.getValue("USA")
        for (i in usa.zeit.indices) {
            val j = usa.zeit[i]
            assertEquals(land.reihen.getValue(Art.AKTIEN)[j - land.von]!!, usa.aktien[i], 0.0)
            assertEquals(land.reihen.getValue(Art.ANLEIHEN)[j - land.von]!!, usa.sicher[i], 0.0)
            assertEquals(land.reihen.getValue(Art.INFLATION)[j - land.von]!!, usa.inflation[i], 0.0)
        }
        assertTrue(usa.zeit.size >= 140)
        assertNull(e.reihe(jst, "CAN"))
        assertNull(e.reihe(jst, "XXX"))
        assertTrue(e.reihe(jst, Entnahmeplan.WELT)!!.laender >= HistorieRechner.MIN_LAENDER)
    }

    @Test fun rechnungMitEchtenRenditen() {
        val usa = e.reihe(jst, "USA")!!
        val p = Plan(1_000_000.0, 40_000.0, 30, 0.6, 0.002)
        val x = e.historisch(usa, p)!!
        assertTrue(x.faelle >= 100)
        assertTrue(x.anteil > 0.5 && x.anteil <= 1.0)
        // Mehr Entnahme reicht seltener, weniger oefter
        assertTrue(e.historisch(usa, p.copy(entnahme = 70_000.0))!!.reicht < x.reicht)
        assertTrue(e.historisch(usa, p.copy(entnahme = 20_000.0))!!.reicht >= x.reicht)
        val alle = e.hoechsteEntnahme(usa, p, 1.0)!!
        val teil = e.hoechsteEntnahme(usa, p, Entnahmeplan.ZIEL_ANTEIL)!!
        assertTrue(alle > 10_000.0 && alle <= teil && teil < 100_000.0)
        assertEquals(x.faelle, e.historisch(usa, p.copy(entnahme = alle))!!.reicht)
        // Zielvermoegen und hoechste Entnahme sind dieselbe Grenze von zwei Seiten
        val ziel = e.zielvermoegen(usa, p)!!
        assertEquals(40_000.0, e.hoechsteEntnahme(usa, p.copy(vermoegen = ziel), Entnahmeplan.ZIEL_ANTEIL)!!, 40.0)
        // Steuer und Kosten kosten Vermoegen
        val steuer = Entnahmeplan.Steuer(Steuerwerte.EINGEBAUT.jahre.getValue("2026").satz, 0.3, 1_000.0, 0.5)
        assertTrue(e.zielvermoegen(usa, p.copy(steuer = steuer))!! > ziel)
        assertTrue(e.zielvermoegen(usa, p.copy(kosten = 0.01))!! > ziel)
        // Leitplanken kuerzen in schwachen Starts: die niedrigste Entnahme liegt unter der ersten
        val gk = e.historisch(usa, p.copy(entnahme = 55_000.0, regel = Regel.LEITPLANKEN))!!
        assertTrue(gk.entMin < 55_000.0 && gk.entTief.size == 30)
        val rest = e.historisch(usa, p.copy(regel = Regel.RESTLAUFZEIT, zins = e.mittlereRendite(usa, 0.6, 0.002)))!!
        assertEquals(rest.faelle, rest.reicht)
        assertTrue(rest.entMin > 0.0 && rest.entMinJahr in 1..30)
        val f = e.freiheit(usa, 50_000.0, 40_000.0, 1_500.0, p.copy(steuer = steuer))!!
        assertTrue(f.ansparen.erreicht > 0 && f.ansparen.min!! < f.ansparen.max!!)
        assertNotNull(e.ziehung(usa, p))
    }

    // ------------------------------------------------------------ Texte und Platz in der Liste

    @Test fun texteInBeidenSprachen() {
        assertEquals(EntnahmeTexte.schluessel(true), EntnahmeTexte.schluessel(false))
        Regel.entries.forEach { assertTrue(it.name, EntnahmeTexte.text(true, "regel." + it.name).isNotBlank()) }
        for (deutsch in listOf(true, false)) {
            EntnahmeTexte.schluessel(deutsch).forEach { k ->
                val t = EntnahmeTexte.text(deutsch, k)
                assertTrue(k, t.isNotBlank())
                // Keine Gedankenstriche, keine Auslassungspunkte
                assertFalse(k, t.contains('–') || t.contains('—') || t.contains('…'))
            }
        }
        // Platzhalter gleich in beiden Sprachen
        EntnahmeTexte.schluessel(true).forEach { k ->
            fun platz(deutsch: Boolean) = Regex("\\{\\d}").findAll(EntnahmeTexte.text(deutsch, k)).map { it.value }.toSet()
            assertEquals(k, platz(true), platz(false))
        }
        assertTrue(EntnahmeTexte.text(true, "hinweis").startsWith("Historisch und hypothetisch"))
        assertTrue(EntnahmeTexte.text(false, "hinweis").startsWith("Historical and hypothetical"))
        assertEquals("Entnahmeplan", De.ptTitle("WITHDRAWAL"))
        assertEquals("Withdrawal plan", En.ptTitle("WITHDRAWAL"))
        assertTrue(De.ptSubtitle("WITHDRAWAL").isNotBlank() && En.ptSubtitle("WITHDRAWAL").isNotBlank())
    }

    @Test fun platzUndQuellen() {
        assertTrue(WERKZEUG_GRUPPEN[1].second.contains(PortfolioTool.WITHDRAWAL.name))
        assertTrue(werkzeugFormen("WITHDRAWAL").isNotEmpty())
        assertEquals(3, Entnahmeplan.QUELLEN.size)
        Entnahmeplan.QUELLEN.forEach { q -> assertTrue(q.name, q.url.startsWith("https://") && !q.url.contains(' ')) }
    }
}
