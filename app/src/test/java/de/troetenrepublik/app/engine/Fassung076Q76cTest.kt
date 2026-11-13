package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.engine.Indikatoren.Deutung
import de.troetenrepublik.app.ui.INDIKATOREN
import de.troetenrepublik.app.ui.IndikatorReihen
import de.troetenrepublik.app.ui.indikatorSchalten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 0.76 (Paket q76c): Kerzenmuster in der Indikator-Ansicht, Gesamtrichtung, Golden/Death Cross.
class Fassung076Q76cTest {

    private val TAG0 = 20_000L

    private fun tage(n: Int) = List(n) { TAG0 + it }

    private fun kerzenAus(schluss: List<Double>, umsatz: Double = 0.0) = schluss.mapIndexed { i, c ->
        Candle((TAG0 + i) * 86_400_000L + 14 * 3_600_000L, c, c + 1, c - 1, c, umsatz)
    }

    // 300 Tage auf 100, dann 100 Tage mit +2 je Tag: die 50-Tage-Linie hebt sich am Tag 300 ueber die 200-Tage-Linie.
    private fun flachDannSteigend(): List<Double> = List(300) { 100.0 } + List(100) { 100.0 + 2.0 * (it + 1) }

    // ---- Golden/Death Cross ----

    @Test fun genauEinGoldenCrossAmBekanntenTag() {
        val s = flachDannSteigend()
        val k = Indikatoren.alleKreuzungen(Indikatoren.sma(s, 50), Indikatoren.sma(s, 200))
        assertEquals(listOf(300 to true), k)
        val d = Indikatoren.kreuzDeutung(tage(s.size), Indikatoren.sma(s, 50), Indikatoren.sma(s, 200))!!
        assertEquals("kreuz", d.art)
        assertEquals("golden", d.lage)
        assertEquals(1, d.stufe)
        assertEquals(TAG0 + 300, d.kreuzTag)
        assertTrue(d.kreuzAuf)
        // Abstand der Linien heute: 50er ueber 200er
        assertTrue(d.wert > 0)
        assertEquals(Indikatoren.Tendenz.POSITIV, Indikatoren.tendenz(d))
    }

    @Test fun deathCrossNachGoldenCrossZaehltAlsLetztes() {
        val s = flachDannSteigend() + List(150) { 300.0 - 3.0 * (it + 1) }
        val k = Indikatoren.alleKreuzungen(Indikatoren.sma(s, 50), Indikatoren.sma(s, 200))
        assertEquals(2, k.size)
        assertEquals(true, k[0].second)
        assertEquals(false, k[1].second)
        val d = Indikatoren.kreuzDeutung(tage(s.size), Indikatoren.sma(s, 50), Indikatoren.sma(s, 200))!!
        assertEquals("death", d.lage)
        assertEquals(-1, d.stufe)
        assertEquals(TAG0 + k[1].first, d.kreuzTag)
        assertTrue(d.wert < 0)
    }

    @Test fun keinSignalOhneKreuzung() {
        // Gleichbleibend: beide Linien liegen aufeinander, nichts kreuzt
        val flach = List(400) { 100.0 }
        assertTrue(Indikatoren.alleKreuzungen(Indikatoren.sma(flach, 50), Indikatoren.sma(flach, 200)).isEmpty())
        val d = Indikatoren.kreuzDeutung(tage(400), Indikatoren.sma(flach, 50), Indikatoren.sma(flach, 200))!!
        assertEquals("keins", d.lage)
        assertEquals(0, d.stufe)
        assertNull(d.kreuzTag)
        // Dauernd steigend: die 50er Linie liegt vom ersten Tag der 200er Linie an darueber, ein Kreuzen gab es nicht
        val steigend = List(400) { 100.0 + it }
        assertTrue(Indikatoren.alleKreuzungen(Indikatoren.sma(steigend, 50), Indikatoren.sma(steigend, 200)).isEmpty())
        val sig = IndikatorTreffer.signale("kreuz", flach, flach.map { it + 1 }, flach.map { it - 1 }, emptyList())
        assertTrue(sig.all { it == 0 })
    }

    @Test fun zuKurzeReiheGibtKeineDeutung() {
        val s = List(150) { 100.0 + it % 7 }
        assertNull(Indikatoren.kreuzDeutung(tage(s.size), Indikatoren.sma(s, 50), Indikatoren.sma(s, 200)))
        assertTrue(Indikatoren.alleKreuzungen(Indikatoren.sma(s, 50), Indikatoren.sma(s, 200)).isEmpty())
        assertTrue(Indikatoren.deuten(tage(s.size), s).none { it.art == "kreuz" })
    }

    @Test fun trefferSignalLiegtAmKreuzungstag() {
        val s = flachDannSteigend()
        val sig = IndikatorTreffer.signale("kreuz", s, s.map { it + 1 }, s.map { it - 1 }, emptyList())
        assertEquals(1, sig[300])
        assertEquals(1, sig.count { it != 0 })
        // Die Deutungen der Seite enthalten das Kreuz ab 200 Tagen
        assertTrue(Indikatoren.deuten(tage(s.size), s).any { it.art == "kreuz" && it.lage == "golden" })
    }

    // ---- Gesamtrichtung ----

    private fun d(art: String, stufe: Int) = Deutung(art, stufe, "x", 0.0)

    @Test fun leereListeHatKeineGesamtrichtung() {
        assertNull(Gesamtrichtung.berechnen(emptyList()))
    }

    @Test fun gleichgerichteteDoppelungenZaehlenAlsEinSignal() {
        // Drei Durchschnitts-Signale und ein Ichimoku, alle steigend: zwei Signale, nicht vier
        val r = Gesamtrichtung.berechnen(listOf(d("sma50", 1), d("sma200", 1), d("kreuz", 1), d("ichimoku", 1)))!!
        assertEquals(2, r.steigend)
        assertEquals(0, r.fallend)
        assertEquals(0, r.neutral)
        assertEquals(2, r.einheiten.size)
        assertEquals(3, r.einheiten.first { it.schluessel == "durchschnitt" }.mitglieder.size)
        // Fuenf Indikatoren derselben Art wiegen nicht mehr als einer
        val viele = Gesamtrichtung.berechnen(listOf(d("sma20", 1), d("sma50", 1), d("sma200", 1), d("ema20", 1), d("ema50", 1), d("sar", -1)))!!
        assertEquals(1, viele.steigend)
        assertEquals(1, viele.fallend)
        assertEquals(Gesamtrichtung.Wort.OFFEN, viele.wort)
    }

    @Test fun gegenlaeufigeDurchschnitteHeben_sichAuf() {
        // Kurz steigend, lang fallend: die Familie hat keine Mehrheit und zaehlt neutral
        val r = Gesamtrichtung.berechnen(listOf(d("sma50", 1), d("sma200", -1)))!!
        assertEquals(0, r.steigend)
        assertEquals(0, r.fallend)
        assertEquals(1, r.neutral)
        // Zwei von drei in dieselbe Richtung reichen
        val m = Gesamtrichtung.berechnen(listOf(d("sma50", 1), d("sma200", 1), d("kreuz", -1)))!!
        assertEquals(1, m.steigend)
    }

    @Test fun wortAusDemSaldo() {
        fun wort(vararg l: Deutung) = Gesamtrichtung.berechnen(l.toList())!!.wort
        // Verschiedene Familien, je ein Signal
        assertEquals(Gesamtrichtung.Wort.STEIGEND, wort(d("macd", 1), d("sar", 1), d("ichimoku", 1), d("rsi", 0)))
        assertEquals(Gesamtrichtung.Wort.EHER_STEIGEND, wort(d("macd", 1), d("sar", 1), d("ichimoku", 0), d("kerzen", 0), d("atr", 0)))
        assertEquals(Gesamtrichtung.Wort.OFFEN, wort(d("macd", 1), d("sar", -1), d("ichimoku", 0)))
        assertEquals(Gesamtrichtung.Wort.OFFEN, wort(d("atr", 0), d("rsi", 0)))
        assertEquals(Gesamtrichtung.Wort.EHER_FALLEND, wort(d("macd", -1), d("sar", -1), d("ichimoku", 0), d("kerzen", 0), d("atr", 0)))
        assertEquals(Gesamtrichtung.Wort.FALLEND, wort(d("macd", -1), d("sar", -1), d("ichimoku", -1), d("kerzen", 0)))
    }

    @Test fun ueberkauftUndATRZaehlenNeutral() {
        val r = Gesamtrichtung.berechnen(listOf(d("rsi", 2), d("stoch", 2), d("atr", 2)))!!
        assertEquals(0, r.steigend)
        assertEquals(0, r.fallend)
        assertEquals(2, r.neutral)
        assertEquals(Indikatoren.Tendenz.GEMISCHT, r.einheiten.first { it.schluessel == "zone" }.tendenz)
    }

    @Test fun zusatzIndikatorenAusTageskerzen() {
        val s = List(300) { 100.0 + it }
        val z = Gesamtrichtung.zusatz(s, s.map { it + 1 }, s.map { it - 1 }, List(300) { 1000.0 })
        val arten = z.map { it.art }.toSet()
        // Der Squeeze zaehlt nur nach einem Austritt mit (0.78)
        assertEquals(Gesamtrichtung.ZUSATZ.toSet() - "squeeze", arten)
        // Stetiger Anstieg: Durchschnitte, Momentum, Trendstaerke und Geldfluss zeigen nach oben
        for (a in listOf("sma20", "ema20", "ema50", "roc", "adx", "aroon")) assertEquals(a, 1, z.first { it.art == a }.stufe)
        // Schluss genau in der Mitte der Tagesspanne: der Geldfluss ist ausgeglichen
        assertEquals(0, z.first { it.art == "cmf" }.stufe)
        // Ohne Umsaetze fehlen die Umsatzindikatoren
        val ohne = Gesamtrichtung.zusatz(s, s.map { it + 1 }, s.map { it - 1 }, List(300) { 0.0 }).map { it.art }
        assertTrue("mfi" !in ohne && "cmf" !in ohne)
        // Zu kurz
        assertTrue(Gesamtrichtung.zusatz(s.take(20), s.take(20), s.take(20), List(20) { 0.0 }).isEmpty())
    }

    @Test fun gesamtrichtungAusDenReihenDerSeite() {
        val r = IndikatorReihen.aus(kerzenAus(List(300) { 100.0 + it }, 1000.0))
        val g = r.gesamt!!
        assertEquals(Gesamtrichtung.Wort.STEIGEND, g.wort)
        assertEquals(0, g.fallend)
        // Durchschnitte, Baender, Zonen, Umsatz, Schwung, Trendstaerke, Ichimoku, SAR, ATR, Kerzen: je ein Signal
        val schluessel = g.einheiten.map { it.schluessel }
        assertTrue("durchschnitt" in schluessel && "trendstaerke" in schluessel)
        assertEquals(1, schluessel.count { it == "durchschnitt" })
        // Das Kreuz steht in der Familie der Durchschnitte, wenn es eine Deutung gibt
        assertTrue(r.deutungen.any { it.art == "kreuz" })
    }

    // ---- Kerzenmuster: Antippen und Sprung in den erweiterten Chart ----

    private fun flags(s: Settings) = listOf(
        s.indSma50, s.indSma200, s.indKreuz, s.indBollinger, s.indRsi, s.indStoch, s.indAtr, s.indObv, s.indIchimoku, s.indSar, s.indKerzen, s.indMacd,
    )

    @Test fun jederIndikatorHatSeinenEigenenSchalter() {
        val aus = Settings()
        val geaendert = HashSet<Int>()
        for (k in INDIKATOREN) {
            val an = indikatorSchalten(aus, k)
            val diff = flags(an).indices.filter { flags(an)[it] != flags(aus)[it] }
            assertEquals("Schluessel $k", 1, diff.size)
            assertTrue("Schluessel $k doppelt belegt", geaendert.add(diff.single()))
            // Zweimal tippen stellt den Ausgangszustand her
            assertEquals(flags(aus), flags(indikatorSchalten(an, k)))
        }
        // Kerzenmuster schaltet nicht mehr den MACD
        val k = indikatorSchalten(aus, "kerzen")
        assertTrue(k.indKerzen)
        assertTrue(!k.indMacd)
        assertEquals(aus, indikatorSchalten(aus, "gibtsnicht"))
    }

    // 30 ruhige Tage, sechs Tage Rueckgang, dann ein Hammer
    private fun hammerReihe(): List<Candle> {
        val l = ArrayList<DoubleArray>()
        repeat(30) { l += if (it % 2 == 0) doubleArrayOf(100.0, 101.5, 98.5, 101.0) else doubleArrayOf(101.0, 101.5, 98.5, 100.0) }
        var p = 100.0
        repeat(6) { val q = p - 1; l += doubleArrayOf(p, p + 0.3, q - 0.3, q); p = q }
        l += doubleArrayOf(94.0, 94.5, 91.0, 94.5)
        return l.mapIndexed { i, k -> Candle((TAG0 + i) * 86_400_000L + 14 * 3_600_000L, k[0], k[1], k[2], k[3], 0.0) }
    }

    @Test fun kerzenmusterHatEigeneDeutungUndMarke() {
        val r = IndikatorReihen.aus(hammerReihe())
        val dk = r.deutungen.first { it.art == "kerzen" }
        assertEquals("hammer", dk.lage)
        assertEquals(1, dk.stufe)
        val m = r.kerzenMarke!!
        assertEquals("hammer", m.muster)
        assertEquals(1, m.richtung)
        // Ein Hammer ist eine einzelne Kerze: erster und letzter Tag fallen zusammen, es ist der letzte Handelstag
        assertEquals(TAG0 + 36, m.vonTag)
        assertEquals(TAG0 + 36, m.bisTag)
        assertEquals(TAG0 + 36, dk.kreuzTag)
    }

    @Test fun mehrtaegigesMusterMarkiertAlleTage() {
        assertEquals(3, Kerzenmuster.laenge("morgenstern"))
        assertEquals(2, Kerzenmuster.laenge("verschlingen_ab"))
        assertEquals(1, Kerzenmuster.laenge("doji"))
        // Bullisches Verschlingen am Ende der Reihe: zwei Tage
        val l = hammerReihe().dropLast(1)
        val ende = Candle((TAG0 + 36) * 86_400_000L + 14 * 3_600_000L, 93.8, 95.7, 93.6, 95.5, 0.0)
        val reihe = l + ende
        val r = IndikatorReihen.aus(reihe)
        val m = r.kerzenMarke!!
        assertEquals("verschlingen_auf", m.muster)
        assertEquals(TAG0 + 35, m.vonTag)
        assertEquals(TAG0 + 36, m.bisTag)
    }

    @Test fun keinMusterKeineMarke() {
        // Ruhige Tage mit kleinen Koerpern, kein Muster am Ende
        val r = IndikatorReihen.aus(hammerReihe().take(30))
        assertNull(r.kerzenMarke)
        assertEquals("keins", r.deutungen.first { it.art == "kerzen" }.lage)
    }

    @Test fun sprungRouteUndZurueck() {
        val m = Kerzenmuster.Marke("morgenstern", 1, 20_100, 20_102)
        val route = ChartSprung.route("BRK-B", m)
        val (kuerzel, muster) = ChartSprung.lesen(route)
        assertEquals("BRK-B", kuerzel)
        assertEquals("morgenstern", muster!!.name)
        assertEquals(20_100L, muster.vonTag)
        assertEquals(20_102L, muster.bisTag)
        // Der Zeitraum ist lang genug fuer Tageskerzen und schliesst das Muster ein
        val (von, bis) = ChartSprung.zeitraum(muster)
        assertTrue(bis - von > 100)
        assertTrue(von < muster.vonTag && bis > muster.bisTag)
    }

    @Test fun sprungMitSchlechtenAngabenOeffnetNurDenChart() {
        assertEquals("AAPL" to null, ChartSprung.lesen("chart/AAPL").let { it.first to it.second?.name })
        assertNull(ChartSprung.lesen("chart/AAPL?muster=quatsch&von=1&bis=2").second)
        assertNull(ChartSprung.lesen("chart/AAPL?muster=hammer&von=x&bis=2").second)
        assertNull(ChartSprung.lesen("chart/AAPL?muster=hammer&von=5&bis=2").second)
        assertEquals("AAPL", ChartSprung.lesen("chart/AAPL?muster=quatsch").first)
        assertNotNull(ChartSprung.lesen("AAPL?muster=hammer&von=1&bis=2").second)
    }
}
