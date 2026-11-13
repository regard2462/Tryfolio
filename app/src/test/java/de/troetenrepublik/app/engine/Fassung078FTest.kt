package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import org.junit.Assert.*
import org.junit.Test

// Maerkte (q78f): Themenkoerbe, Beitrag zum Index, Mover, Art der Schlagzeile, Sektortabelle, Reddit, erster Handelstag
class Fassung078FTest {
    @Test
    fun korbMedianBreiteUndGroessterMover() {
        val korb = Themenkoerbe.Korb("T", listOf("A", "B", "C", "D", "X"))
        val e = Themenkoerbe.auswerten(korb, mapOf("A" to 4.0, "B" to -1.0, "C" to 2.0, "D" to -6.0))
        assertEquals(4, e.anzahl)
        assertEquals(0.5, e.median!!, 1e-9)
        assertEquals(0.5, e.steigend!!, 1e-9)
        assertEquals(2, e.anzahlSteigend)
        assertEquals("D", e.groesster!!.first)
        assertEquals(listOf("A", "C", "B", "D"), e.werte.map { it.first })
    }

    @Test
    fun korbOhneKurseIstLeer() {
        val e = Themenkoerbe.auswerten(Themenkoerbe.KOERBE.first(), emptyMap())
        assertEquals(0, e.anzahl)
        assertNull(e.median)
        assertNull(e.steigend)
        assertNull(e.groesster)
    }

    @Test
    fun medianBeiGeraderAnzahl() {
        assertEquals(2.5, Themenkoerbe.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertNull(Themenkoerbe.median(emptyList()))
    }

    @Test
    fun korbSymboleHabenKeineDoppelten() {
        assertEquals(12, Themenkoerbe.KOERBE.size)
        assertEquals(Themenkoerbe.ALLE_SYMBOLE.size, Themenkoerbe.ALLE_SYMBOLE.toSet().size)
    }

    @Test
    fun indexBeitragGewichtMalAenderung() {
        val e = IndexBeitrag.berechnen(listOf(
            IndexBeitrag.Eingabe("A", "A", 60.0, 2.0),
            IndexBeitrag.Eingabe("B", "B", 30.0, -1.0),
            IndexBeitrag.Eingabe("C", "C", 10.0, 0.0),
            IndexBeitrag.Eingabe("D", "D", 100.0, null),
        ))!!
        assertEquals(1.2, e.hoch.single().punkte, 1e-9)
        assertEquals("A", e.hoch.single().symbol)
        assertEquals(-0.3, e.runter.single().punkte, 1e-9)
        assertEquals(0.9, e.gesamt, 1e-9)
    }

    @Test
    fun indexBeitragNimmtNurDieGroessten() {
        val werte = (1..6).map { IndexBeitrag.Eingabe("S$it", "S$it", 10.0, it.toDouble()) }
        val e = IndexBeitrag.berechnen(werte, 3)!!
        assertEquals(listOf("S6", "S5", "S4"), e.hoch.map { it.symbol })
        assertTrue(e.runter.isEmpty())
        assertNull(IndexBeitrag.berechnen(listOf(IndexBeitrag.Eingabe("A", "A", 1.0, 1.0))))
    }

    private fun quote(preis: Double, hoch: Double = 0.0, tief: Double = 0.0, vol: Double = 0.0, schnitt: Double = 0.0) =
        Quote(symbol = "X", price = preis, weekHigh52 = hoch, weekLow52 = tief, volume = vol, avgVolume10 = schnitt)

    @Test
    fun rvolAusVolumenUndSchnitt() {
        assertEquals(2.5, MoverGrund.rvol(quote(10.0, vol = 5_000_000.0, schnitt = 2_000_000.0))!!, 1e-9)
        assertNull(MoverGrund.rvol(quote(10.0, vol = 5_000_000.0)))
        assertNull(MoverGrund.rvol(quote(10.0, schnitt = 2_000_000.0)))
    }

    @Test
    fun grundReihenfolgeUndNaehe() {
        val q = quote(100.0, hoch = 100.5, tief = 50.0)
        assertEquals(MoverGrund.Grund.ZAHLEN, MoverGrund.grund(q, zahlen = true, analyst = true))
        assertEquals(MoverGrund.Grund.ANALYST, MoverGrund.grund(q, zahlen = false, analyst = true))
        assertEquals(MoverGrund.Grund.HOCH, MoverGrund.grund(q, zahlen = false, analyst = false))
        assertEquals(MoverGrund.Grund.TIEF, MoverGrund.grund(quote(50.4, hoch = 100.0, tief = 50.0), false, false))
        assertNull(MoverGrund.grund(quote(70.0, hoch = 100.0, tief = 50.0), false, false))
    }

    @Test
    fun artenEnglisch() {
        assertEquals(NachrichtArt.Art.ZAHLEN, NachrichtArt.arten("Apple beats estimates as iPhone sales rise").first())
        assertTrue(NachrichtArt.Art.ANALYST in NachrichtArt.arten("Morgan Stanley upgrades Tesla to overweight"))
        assertTrue(NachrichtArt.Art.UEBERNAHME in NachrichtArt.arten("Chevron agrees to acquire rival in 50 billion deal"))
        assertTrue(NachrichtArt.Art.KAPITAL in NachrichtArt.arten("Nvidia announces stock split and raises dividend"))
        assertTrue(NachrichtArt.Art.ZULASSUNG in NachrichtArt.arten("FDA approves new obesity drug"))
        assertTrue(NachrichtArt.Art.AUFTRAG in NachrichtArt.arten("Rheinmetall wins 2 billion contract from the army"))
        assertTrue(NachrichtArt.Art.SEKTOR in NachrichtArt.arten("Chip stocks slide as export curbs loom"))
    }

    @Test
    fun artenDeutsch() {
        assertTrue(NachrichtArt.Art.ZAHLEN in NachrichtArt.arten("SAP legt Quartalszahlen vor und hebt die Prognose"))
        assertTrue(NachrichtArt.Art.ANALYST in NachrichtArt.arten("Goldman hebt Kursziel für Siemens an"))
        assertTrue(NachrichtArt.Art.UEBERNAHME in NachrichtArt.arten("Konzern kündigt Übernahme an"))
        assertTrue(NachrichtArt.Art.KAPITAL in NachrichtArt.arten("Bayer beschließt Aktienrückkauf"))
        assertTrue(NachrichtArt.Art.ZULASSUNG in NachrichtArt.arten("EU erteilt Zulassung für Impfstoff"))
        // 0.77: Rekordgewinn zaehlt als Zahlen, ein Produkt-Upgrade nicht als Analystenstimme
        assertTrue(NachrichtArt.Art.ZAHLEN in NachrichtArt.arten("Energietechnik: 4,1 Milliarden Euro: Rekordgewinn bei Siemens Energy"))
        assertTrue(NachrichtArt.Art.ZAHLEN in NachrichtArt.arten("Taisei Earnings Up In H1; Raises FY26 Guidance"))
        assertTrue(NachrichtArt.Art.ANALYST !in NachrichtArt.arten("Rivian's Vans Got a Serious Upgrade. Could Another Major Fleet Deal Finally Follow?"))
        assertTrue(NachrichtArt.Art.ANALYST in NachrichtArt.arten("Tesla upgraded to buy at Jefferies"))
        assertTrue(NachrichtArt.Art.AUFTRAG in NachrichtArt.arten("Hensoldt erhält Großauftrag der Bundeswehr"))
        assertTrue(NachrichtArt.Art.SEKTOR in NachrichtArt.arten("Rüstungswerte legen kräftig zu"))
    }

    @Test
    fun harmloseSchlagzeileHatKeineArt() {
        assertTrue(NachrichtArt.arten("Das Wetter bleibt am Wochenende wechselhaft").isEmpty())
        assertTrue(NachrichtArt.arten("In order to continue, the committee met on Tuesday").isEmpty())
    }

    @Test
    fun sektorZeileMitAusweichfeldern() {
        val z = Sektortabelle.zeile("XLK", 0.8, mapOf("trailingPE" to 24.0, "trailingAnnualDividendYield" to 0.009, "netAssets" to 5e10))
        assertEquals(24.0, z.kgv!!, 1e-9)
        assertEquals(0.9, z.rendite!!, 1e-9)
        assertEquals(5e10, z.marktwert!!, 1.0)
        val f = Sektortabelle.zeile("XLK", null, mapOf("forwardPE" to 20.0, "trailingPE" to 24.0, "yield" to 1.2, "marketCap" to 7e10, "netAssets" to 5e10))
        assertEquals(20.0, f.kgv!!, 1e-9)
        assertEquals(1.2, f.rendite!!, 1e-9)
        assertEquals(7e10, f.marktwert!!, 1.0)
        val leer = Sektortabelle.zeile("XLK", null, null)
        assertNull(leer.kgv); assertNull(leer.rendite); assertNull(leer.marktwert)
    }

    private fun archiv(vararg tage: Pair<Long, Int>) = Trendverlauf.Archiv(
        tage = mapOf("q" to tage.associate { (t, n) -> t to mapOf("AAA" to n) }),
    )

    @Test
    fun redditAenderungZurVorperiode() {
        // 20 Tage Verlauf, Zeitraum 7 Tage: vorher 7 mal 2, jetzt 7 mal 3
        val a = archiv(*(1L..20L).map { it to (if (it > 13L) 3 else 2) }.toTypedArray())
        assertEquals(50.0, RedditVorperiode.aenderung(a, "q", "AAA", 7, 20)!!, 1e-9)
    }

    @Test
    fun redditAenderungNurMitGanzerVorperiode() {
        val a = archiv(*(10L..20L).map { it to 3 }.toTypedArray())
        assertNull(RedditVorperiode.aenderung(a, "q", "AAA", 7, 20))
    }

    @Test
    fun ersterTagGegenAusgabepreis() {
        val tag = 20_000L
        val ms = 86_400_000L
        val k = listOf(
            Candle((tag - 1) * ms + 1, 1.0, 1.0, 1.0, 5.0),
            Candle(tag * ms + 14 * 3_600_000L, 12.0, 12.0, 12.0, 12.0),
            Candle((tag + 1) * ms + 14 * 3_600_000L, 15.0, 15.0, 15.0, 15.0),
        )
        assertEquals(20.0, ErsterTag.ergebnis(k, tag, 10.0)!!, 1e-9)
        assertNull(ErsterTag.ergebnis(k, tag, null))
        assertNull(ErsterTag.ergebnis(emptyList(), tag, 10.0))
        // Erster Kurs erst Tage nach dem Handelsbeginn: der Tag fehlt
        assertNull(ErsterTag.ergebnis(listOf(Candle((tag + 9) * ms, 1.0, 1.0, 1.0, 1.0)), tag, 10.0))
    }
}
