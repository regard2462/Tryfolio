package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.SecInsider
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class Fassung078ATest {
    private val t = 86_400_000L

    // ------------------------------------------------------------ Meldeverzug

    @Test fun medianUndTage() {
        assertEquals(3, KongressKennzahlen.median(listOf(5, 1, 3)))
        assertEquals(4, KongressKennzahlen.median(listOf(1, 3, 5, 7)))
        assertNull(KongressKennzahlen.median(emptyList()))
        assertEquals(10, KongressKennzahlen.tage(100 * t, 110 * t + 5))
        assertEquals(0, KongressKennzahlen.tage(110 * t, 100 * t))
        assertNull(KongressKennzahlen.tage(0, 5 * t))
    }

    @Test fun verzugMitAnteilUndFenster() {
        val jetzt = 1000 * t
        val l = listOf(
            KongressKennzahlen.Eintrag(900 * t, 910 * t, 1_000.0),
            KongressKennzahlen.Eintrag(950 * t, 1000 * t, 2_000.0),
            KongressKennzahlen.Eintrag(960 * t, 970 * t, 3_000.0),
            KongressKennzahlen.Eintrag(980 * t, 1_030 * t, 4_000.0),
        )
        val v = KongressKennzahlen.verzug(l, jetzt)
        // Tage: 10, 50, 10, 50; Median 30; zwei von vier ueber 45 Tage
        assertEquals(30, v.median)
        assertEquals(0.5, v.spaet!!, 1e-9)
        assertEquals(4, v.anzahl)
        // Letzte 90 Tage ab Tag 910: drei Geschaefte
        assertEquals(3, v.geschaefte90)
        assertEquals(9_000.0, v.volumen90, 1e-9)
        val leer = KongressKennzahlen.verzug(emptyList(), jetzt)
        assertNull(leer.median)
        assertNull(leer.spaet)
    }

    @Test fun ranglisteTraegtVerzug() {
        val jetzt = 400 * t
        val tage = (0 until 400).associate { it.toLong() to 100.0 + it }
        val aktie = KongressAuswertung.Kurse.aus(tage)
        val index = KongressAuswertung.Kurse.aus(tage)
        val handel = (0 until 3).map { KongressAuswertung.Handel("k:A", "A", 'H', "S", true, (280 - it).toLong() * t, (300 - it).toLong() * t, 10_000.0) } +
            KongressAuswertung.Handel("k:A", "A", 'H', "S", false, 390 * t, 400 * t, 5_000.0)
        val r = KongressAuswertung.rangliste(handel, mapOf("S" to aktie), index, jetzt).single()
        // Verzug aller vier Geschaefte: 20, 20, 20, 10 Tage
        assertEquals(20, r.verzug)
        assertEquals(0.0, r.spaet!!, 1e-9)
        assertEquals(1, r.geschaefte90)
        assertEquals(5_000.0, r.volumen90, 1e-9)
        // Speichern und Lesen behaelt die neuen Felder
        val text = RanglisteSpeicher.schreiben(jetzt, listOf(r))
        assertEquals(listOf(r), RanglisteSpeicher.lesen(text)!!.second)
        val ohne = r.copy(verzug = null, spaet = null)
        assertEquals(listOf(ohne), RanglisteSpeicher.lesen(RanglisteSpeicher.schreiben(jetzt, listOf(ohne)))!!.second)
    }

    // ------------------------------------------------------------ Kongress-Flow

    @Test fun flussNachSektorKammerFamilie() {
        val jetzt = 1000 * t
        fun h(g: Char, sym: String, kauf: Boolean, tag: Int, betrag: Double, inh: String = "") =
            KongressAuswertung.Handel(if (g == 'H') "k:X" else "n:Y", "X", g, sym, kauf, tag.toLong() * t, (tag + 5).toLong() * t, betrag, inh)
        val handel = listOf(
            h('H', "AAA", true, 990, 1_000.0),
            h('H', "AAA", false, 980, 500.0, "SP"),
            h('S', "BBB", true, 970, 2_000.0, "JT"),
            h('S', "CCC", true, 960, 100.0),
            // zu alt
            h('H', "AAA", true, 800, 99_999.0),
        )
        val f = KongressKennzahlen.fluss(handel, jetzt, sektor = { when (it) { "AAA" -> "Technik"; "BBB" -> "Banken"; else -> null } })
        assertEquals(4, f.gesamt)
        assertEquals(1, f.ohneSektor)
        assertEquals(listOf("Banken", "Technik"), f.sektoren.map { it.name })
        assertEquals(1, f.sektoren.first { it.name == "Technik" }.seite.kaeufe)
        assertEquals(1, f.sektoren.first { it.name == "Technik" }.seite.verkaeufe)
        assertEquals(2, f.haus.kaeufe + f.haus.verkaeufe)
        assertEquals(2, f.senat.kaeufe)
        assertEquals(2_500.0, f.familie.summe, 1e-9)
        assertEquals(1_100.0, f.eigene.summe, 1e-9)
        assertTrue(KongressKennzahlen.familie("dc"))
        assertTrue(!KongressKennzahlen.familie(""))
    }

    // ------------------------------------------------------------ Signale

    private fun geschaeft(kauf: Boolean, datum: Long) = Kongress.Geschaeft(
        inhaber = "", wert = "", kuerzel = "AAA", typ = "ST", art = if (kauf) Kongress.Art.KAUF else Kongress.Art.VERKAUF,
        datum = datum, gemeldet = datum, von = 1_000, bis = 15_000,
    )

    @Test fun kongressSignal() {
        // Seit 0.78 zaehlt der Tag der Meldung im Fenster der Konvergenz (90 Tage bis zum Ende der Insider-Daten)
        val jetzt = Konvergenz.fenster(java.time.LocalDate.ofEpochDay(1000))
        val haus = Handelnde.Eintrag(
            Handelnde.Gruppe.HAUS, "s", "n", geschaefte = listOf(geschaeft(true, 990 * t), geschaeft(true, 950 * t), geschaeft(false, 700 * t)),
        )
        val regierung = Handelnde.Eintrag(Handelnde.Gruppe.REGIERUNG, "r", "n", geschaefte = listOf(geschaeft(true, 990 * t)))
        assertEquals(Signale.Kongress(2, 0), Signale.kongress(listOf(haus, regierung), jetzt))
        assertNull(Signale.kongress(listOf(regierung), jetzt))
    }

    @Test fun stufungenSignal() {
        val jetzt = 1000 * t
        fun u(tag: Int, aktion: String, von: String, nach: String) = Analystenbilanz.Urteil("H", tag.toLong() * t, nach, von, aktion)
        val l = listOf(
            u(990, "up", "Hold", "Buy"),
            u(985, "down", "Buy", "Hold"),
            u(980, "up", "Hold", "Buy"),
            u(975, "main", "Buy", "Buy"),
            u(900, "up", "Hold", "Buy"),
        )
        assertEquals(Signale.Stufen(2, 1), Signale.stufungen(l, jetzt))
        assertNull(Signale.stufungen(listOf(u(900, "up", "Hold", "Buy")), jetzt))
    }

    @Test fun optionenSignal() {
        fun k(call: Boolean) = Optionsanalyse.Kontrakt(call, 100.0, 0L, 1.0, 0.9, 1.1, 1_000, 100, 0.4)
        fun a(call: Boolean, p: Double) = Optionsanalyse.Auffaellig(k(call), p, 5.0, Optionsanalyse.Seite.KAUF, 3.0)
        val e = Optionsanalyse.Ergebnis(null, null, null, null, null, null, null, listOf(a(true, 100_000.0), a(true, 50_000.0), a(false, 25_000.0)))
        assertEquals(Signale.Optionen(3, 2, 1, 175_000.0), Signale.optionen(e))
        assertNull(Signale.optionen(e.copy(auffaellig = emptyList())))
    }

    // ------------------------------------------------------------ Insiderliste

    @Test fun insiderKennzahlen() {
        assertEquals(0.25, Signale.seitKauf(100.0, 125.0)!!, 1e-9)
        assertEquals(-0.5, Signale.seitKauf(10.0, 5.0)!!, 1e-9)
        assertNull(Signale.seitKauf(0.0, 5.0))
        assertEquals(0.001, Signale.vomMarktwert(1_000_000.0, 1_000_000_000.0)!!, 1e-12)
        assertNull(Signale.vomMarktwert(1_000.0, 0.0))
        fun g(kauf: Boolean, wert: Double, plan: Boolean) = SecInsider.Geschaeft(a = "a", k = "AAA", e = "E", p = "P", kauf = kauf, d = "2026-01-01", wert = wert, plan = plan)
        assertEquals(0.75, Signale.planAnteil(listOf(g(false, 300.0, true), g(false, 100.0, false), g(true, 5_000.0, false)))!!, 1e-9)
        assertNull(Signale.planAnteil(listOf(g(true, 5_000.0, false))))
    }

    @Test fun dollarAusEuroKurs() {
        // 100 US-Dollar sind 80 Euro: Marktwert in Euro 80 Mrd, in Dollar 100 Mrd
        val q = Quote(symbol = "AAA", price = 80.0, marketCap = 80e9, nativeCurrency = "USD", nativePrice = 100.0)
        val d = Signale.dollar(q)!!
        assertEquals(100.0, d.kurs, 1e-9)
        assertEquals(100e9, d.marktwert, 1.0)
        assertNull(Signale.dollar(q.copy(nativeCurrency = "EUR")))
        assertNull(Signale.dollar(null))
    }

    // ------------------------------------------------------------ POTUS

    @Test fun sektorenAusText() {
        assertEquals(listOf("XLE"), PotusReaktion.etfs("We will drill, drill, drill. Oil prices fall"))
        assertEquals(listOf("XLI", "XME"), PotusReaktion.etfs("Tariffs on steel").sorted())
        assertEquals(emptyList<String>(), PotusReaktion.etfs("Have a great day"))
        assertTrue(PotusReaktion.etfs("Zölle auf Stahl, Öl und Chips").size <= PotusReaktion.MAX_ETFS)
    }

    // 30-Minuten-Kerzen von 14:30 bis 20:30 UTC an Tag 100, Eroeffnung steigt je Kerze um 1
    private fun kerzen(): List<Candle> = (0 until 12).map { i ->
        val ts = 100 * t + 14 * 3_600_000L + 30 * 60_000L + i * 30 * 60_000L
        Candle(ts, 100.0 + i, 101.0 + i, 99.0 + i, 100.5 + i)
    }

    @Test fun reaktionAusKerzen() {
        val k = kerzen()
        assertEquals(30 * 60_000L, PotusReaktion.abstand(k))
        val zeit = 100 * t + 15 * 3_600_000L + 10 * 60_000L
        // Beitrag 15:10 liegt in der Kerze ab 15:00 (Eroeffnung 101), eine Stunde spaeter 16:10 in der Kerze ab 16:00 (103)
        assertEquals(103.0 / 101.0 - 1, PotusReaktion.aenderung(k, zeit, 1)!!, 1e-12)
        assertEquals(107.0 / 101.0 - 1, PotusReaktion.aenderung(k, zeit, 3)!!, 1e-12)
        // Vor der ersten Kerze und nach der letzten: kein Wert
        assertNull(PotusReaktion.aenderung(k, 100 * t + 10 * 3_600_000L, 1))
        assertNull(PotusReaktion.aenderung(k, 100 * t + 19 * 3_600_000L, 3))
        // Kerzen von einer Stunde reichen nicht
        val stuendlich = k.filterIndexed { i, _ -> i % 2 == 0 }
        assertNull(PotusReaktion.aenderung(stuendlich, zeit, 1))
    }

    @Test fun zeilenNurMitWert() {
        val k = kerzen()
        val zeit = 100 * t + 15 * 3_600_000L + 10 * 60_000L
        val z = PotusReaktion.zeilen("Oil and drilling", zeit) { if (it == "SPY" || it == "XLE") k else null }!!
        assertEquals(listOf("SPY", "XLE"), z.map { it.symbol })
        assertEquals(2, z.first().werte.size)
        assertNull(PotusReaktion.zeilen("Oil", zeit) { null })
    }
}
