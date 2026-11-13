package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.Auswertung
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Portfolioverlauf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.TreeMap

// Auswertung je Person, Depot-Check und Chart-Indikatoren; alle Daten sind erfunden.
class Fassung41Test {

    private fun ms(iso: String) = LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun tag(iso: String) = Portfolioverlauf.tag(ms(iso))

    private fun g(datum: String, kuerzel: String, kauf: Boolean, von: Long = 1_001, bis: Long? = 15_000, typ: String = "ST") =
        Kongress.Geschaeft("", "Wert $kuerzel", kuerzel, typ, if (kauf) Kongress.Art.KAUF else Kongress.Art.VERKAUF, ms(datum), 0L, von, bis)

    // Tageskurse ab start fuer tage Kalendertage, Wert je Tag aus f.
    private fun reihe(start: String, tage: Int, f: (Int) -> Double) = TreeMap<Long, Double>().apply {
        val t0 = tag(start)
        for (i in 0 until tage) put(t0 + i, f(i))
    }

    @Test
    fun kaeufeUndVerkaeufeDanach() {
        // AAA steigt stetig, BBB faellt stetig, der Markt steigt halb so schnell wie AAA
        val kurse = mapOf(
            "AAA" to reihe("2025-01-01", 600) { 100.0 + it * 0.2 },
            "BBB" to reihe("2025-01-01", 600) { 200.0 - it * 0.2 },
        )
        val markt = reihe("2025-01-01", 600) { 100.0 + it * 0.1 }
        val l = listOf(g("2025-02-01", "AAA", true), g("2025-03-01", "BBB", true), g("2025-04-01", "BBB", false), g("2025-05-01", "AAA", true, typ = "OP"))
        val e = Auswertung.rechnen(l, kurse, markt, { if (it == "AAA") "Software" else "Banken" }, ms("2026-08-01"))
        val h30 = e.kaeufe.first { it.tage == 30 }
        assertEquals(2, h30.anzahl)
        assertEquals(1, h30.treffer)
        assertEquals(1, h30.gegenMarkt)
        val v30 = e.verkaeufe.first { it.tage == 30 }
        assertEquals(1, v30.anzahl)
        assertEquals(1, v30.treffer)
        // Optionen zaehlen nicht, die Haeufigkeit aber schon
        assertEquals(3, e.bewertet)
        assertEquals(4, e.haeufigkeit.geschaefte)
        assertEquals(0.75, e.haeufigkeit.kaufAnteil, 1e-9)
        assertEquals(4, e.haeufigkeit.monate)
        // nach einem Jahr ist der Kurs fuer den Verkauf vom April noch bekannt (Reihe bis August 2026)
        assertNotNull(e.verkaeufe.firstOrNull { it.tage == 365 })
    }

    @Test
    fun auffaelligeMonateUndBranchen() {
        val l = ArrayList<Kongress.Geschaeft>()
        // zwoelf Monate mit je zwei Kaeufen, im Juni 2025 dreissig
        for (m in 1..12) repeat(if (m == 6) 30 else 2) { l += g("2025-%02d-10".format(m), if (m <= 6) "AAA" else "BBB", true) }
        val e = Auswertung.rechnen(l, emptyMap(), null, { if (it == "AAA") "Software" else "Banken" }, ms("2026-01-01"))
        assertEquals(listOf(YearMonth.of(2025, 6)), e.auffaellig.map { it.monat })
        val b = e.branchen!!
        // die beiden Haelften: erst Software, dann Banken
        assertEquals("Software", b.frueher.first().first)
        assertEquals("Banken", b.zuletzt.first().first)
        assertEquals(1.0, b.abgedeckt, 1e-9)
    }

    @Test
    fun vorGrossenKursbewegungen() {
        // Kurs springt alle 20 Tage fuer einen Tag um 15 % nach oben; Kaeufe genau zwei Tage vor jedem Sprung
        val kurse = mapOf("AAA" to reihe("2025-01-01", 400) { if (it % 20 == 0 && it > 0) 115.0 else 100.0 })
        val l = (1..15).map { i -> g(LocalDate.parse("2025-01-01").plusDays(i * 20L - 2).toString(), "AAA", true) }
        val v = Auswertung.rechnen(l, kurse, null, { null }, ms("2026-06-01")).vorBewegung!!
        assertEquals(15, v.kaeufe)
        assertEquals(15, v.vorAnstieg)
        // an zufaelligen Tagen liegt binnen zehn Handelstagen nur etwa jeder zweite Tag vor einem Sprung
        assertTrue("Basis ${v.basisKauf}", v.basisKauf in 0.4..0.6)
    }

    @Test
    fun depotCheck() {
        val werte = listOf(
            DepotCheck.Wert("Apple", 3000.0, DepotCheck.Art.AKTIE, "USD", "Computer", 10.0),
            DepotCheck.Wert("Nvidia", 1000.0, DepotCheck.Art.AKTIE, "USD", "Halbleiter", -60.0),
            DepotCheck.Wert("Welt-ETF", 5000.0, DepotCheck.Art.ETF, "EUR", null, 5.0),
            DepotCheck.Wert("Turbo", 1000.0, DepotCheck.Art.DERIVAT, "EUR", null, 0.0),
        )
        val b = DepotCheck.pruefen(werte, cash = 100.0, gebuehren = 10.0, volumen = 1500.0, orders = 10, sparplaene = 0).associateBy { it.regel }
        assertEquals(DepotCheck.Stufe.KRITISCH, b.getValue(DepotCheck.Regel.GROESSTE).stufe)
        assertEquals("Apple", b.getValue(DepotCheck.Regel.GROESSTE).name)
        assertEquals(0.3, b.getValue(DepotCheck.Regel.GROESSTE).anteil, 1e-9)
        // die Haelfte in ETFs: die Zahl der Einzelwerte ist kein Problem
        assertEquals(DepotCheck.Stufe.GUT, b.getValue(DepotCheck.Regel.ANZAHL).stufe)
        assertEquals(DepotCheck.Stufe.ACHTUNG, b.getValue(DepotCheck.Regel.BRANCHE).stufe)
        assertEquals(DepotCheck.Stufe.GUT, b.getValue(DepotCheck.Regel.WAEHRUNG).stufe)
        assertEquals(DepotCheck.Stufe.GUT, b.getValue(DepotCheck.Regel.HEBEL).stufe)
        assertEquals(0.1, b.getValue(DepotCheck.Regel.HEBEL).anteil, 1e-9)
        assertEquals(DepotCheck.Stufe.ACHTUNG, b.getValue(DepotCheck.Regel.KOSTEN).stufe)
        assertEquals(DepotCheck.Stufe.ACHTUNG, b.getValue(DepotCheck.Regel.RESERVE).stufe)
        assertEquals(DepotCheck.Stufe.HINWEIS, b.getValue(DepotCheck.Regel.SPARPLAN).stufe)
        assertEquals("Nvidia", b.getValue(DepotCheck.Regel.VERLUSTE).name)
        assertNull(b[DepotCheck.Regel.KRYPTO])
        assertTrue(DepotCheck.pruefen(emptyList(), 100.0, 0.0, 0.0, 0, 0).isEmpty())
    }

    @Test
    fun indikatoren() {
        assertEquals(listOf(null, null, 2.0, 3.0, 4.0), Indikatoren.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3))
        // stetig steigend: RSI 100; abwechselnd gleich weit auf und ab: RSI 50
        assertEquals(100.0, Indikatoren.rsi((0..30).map { it.toDouble() }).last()!!, 1e-9)
        val zickzack = (0..60).map { if (it % 2 == 0) 10.0 else 11.0 }
        assertEquals(50.0, Indikatoren.rsi(zickzack).last()!!, 2.0)
        assertNull(Indikatoren.rsi(zickzack)[13])
        assertNotNull(Indikatoren.rsi(zickzack)[14])
        // auf die Punkte des Charts gelegt, mit Faktor fuer eine andere Waehrung
        val tage = TreeMap<Long, Double>().apply { put(tag("2026-01-05"), 10.0); put(tag("2026-01-06"), 20.0) }
        val c = listOf("2026-01-05", "2026-01-06", "2026-01-07").map { Candle(ms(it) + 15 * 3_600_000L, 1.0, 1.0, 1.0, 1.0) }
        assertEquals(listOf(20.0, 40.0, 40.0), Indikatoren.aufChart(c, tage, 2.0))
    }
}
