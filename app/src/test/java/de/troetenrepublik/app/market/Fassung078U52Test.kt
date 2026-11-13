package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.KursDatei
import de.troetenrepublik.app.engine.Splits
import de.troetenrepublik.app.i18n.KursDateiTexte
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

// 0.78: Eigene Kursdateien einlesen (Stooq-Textdateien, Yahoo-CSV, deutsches Semikolon-CSV, ZIP) und als Rueckfall und
// Ergaenzung der Kursreihen einhaengen.
class Fassung078U52Test {
    private val tag = 86_400_000L

    private fun datei(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream("kursdatei/$name")!!.readBytes()

    private fun einlesen(name: String) = KursDatei.einlesen(name, datei(name))

    private fun tageSeit(j: Int, m: Int, t: Int) = KursDatei.tageSeit1970(j, m, t)!!

    @Test fun stooqTextdatei() {
        val e = einlesen("aapl.us.txt")
        assertEquals(1, e.reihen.size)
        assertEquals(0, e.uebersprungen)
        val r = e.reihen.first()
        assertEquals("AAPL", r.symbol)
        assertEquals("USD", r.waehrung)
        assertEquals(3, r.kerzen.size)
        assertEquals(tageSeit(1984, 12, 7) * tag, r.kerzen.first().t)
        assertEquals(0.11, r.kerzen[1].close, 1e-9)
        assertEquals(0.11, r.kerzen[1].high, 1e-9)
        assertEquals(200.0, r.kerzen[1].volume, 1e-9)
        assertEquals(listOf(1984, 12, 7), KursDatei.datumAusTagen(tageSeit(1984, 12, 7)).toList())
    }

    @Test fun einfacheFormOhneTickerNimmtSymbolAusDemDateinamen() {
        val e = einlesen("einfach.txt")
        val r = e.reihen.single()
        assertEquals("EINFACH", r.symbol)
        assertEquals(2, r.kerzen.size)
        assertEquals(11.5, r.kerzen.last().close, 1e-9)
        assertEquals(12.0, r.kerzen.last().high, 1e-9)
    }

    @Test fun yahooCsvNimmtCloseNichtAdjClose() {
        val e = einlesen("yahoo.csv")
        val r = e.reihen.single()
        assertEquals(3, r.kerzen.size)
        assertEquals(1, e.uebersprungen)
        assertEquals(185.6, r.kerzen.first().close, 1e-9)
        assertEquals(82_488_700.0, r.kerzen.first().volume, 1e-9)
        assertEquals(tageSeit(2024, 1, 4) * tag, r.kerzen.last().t)
    }

    @Test fun deutschesSemikolonCsvMitKommaUndPunkt() {
        val e = einlesen("deutsch.csv")
        val r = e.reihen.single()
        assertEquals(2, r.kerzen.size)
        assertEquals(0, e.uebersprungen)
        val k = r.kerzen.first()
        assertEquals(tageSeit(2024, 1, 2) * tag, k.t)
        assertEquals(1234.5, k.open, 1e-9)
        assertEquals(1250.0, k.high, 1e-9)
        assertEquals(1230.0, k.low, 1e-9)
        assertEquals(1245.75, k.close, 1e-9)
        assertEquals(1_200_000.0, k.volume, 1e-9)
    }

    @Test fun zipLiestAlleReihenUndIgnoriertPfadeUndFremdeDateien() {
        val e = einlesen("zwei.zip")
        assertEquals(listOf("AAPL", "SAP.DE"), e.reihen.map { it.symbol })
        assertEquals(listOf("USD", "EUR"), e.reihen.map { it.waehrung })
        assertEquals(listOf(3, 2), e.reihen.map { it.kerzen.size })
        assertEquals(0, e.uebersprungen)
        assertFalse(e.zuGross)
    }

    @Test fun kaputteZeilenWerdenUebersprungenUndGezaehlt() {
        val e = einlesen("kaputt.csv")
        assertEquals(2, e.reihen.single().kerzen.size)
        assertEquals(3, e.uebersprungen)
        assertEquals(2.5, e.reihen.single().kerzen.last().close, 1e-9)
    }

    @Test fun keineKurseUndOhneKopfzeile() {
        assertTrue(KursDatei.einlesen("x.txt", "hallo welt\nnoch eine zeile".encodeToByteArray()).reihen.isEmpty())
        assertTrue(KursDatei.einlesen("x.txt", ByteArray(0)).reihen.isEmpty())
        val r = KursDatei.einlesen("msft.csv", "20240102,10\n20240103,11\n".encodeToByteArray()).reihen.single()
        assertEquals("MSFT", r.symbol)
        assertEquals(listOf(10.0, 11.0), r.kerzen.map { it.close })
    }

    @Test fun datumsformate() {
        val soll = tageSeit(2024, 1, 2)
        for (s in listOf("2024-01-02", "02.01.2024", "20240102", "Jan 2, 2024", "2 Jan 2024", "01/02/2024", "2024-01-02 00:00:00", "2024/01/02")) {
            assertEquals(s, soll, KursDatei.datumTage(s, false))
        }
        assertEquals(soll, KursDatei.datumTage("02/01/2024", true))
        assertTrue(KursDatei.tagZuerstErkennen(listOf("01/02/2024", "13/01/2024")))
        assertFalse(KursDatei.tagZuerstErkennen(listOf("01/02/2024", "01/13/2024")))
        assertNull(KursDatei.datumTage("2024-02-30", false))
        assertNull(KursDatei.datumTage("abc", false))
    }

    @Test fun zahlenInBeidenSchreibweisen() {
        assertEquals(1234.56, KursDatei.zahl("1.234,56", true)!!, 1e-9)
        assertEquals(1234.56, KursDatei.zahl("1,234.56", false)!!, 1e-9)
        assertEquals(1234.56, KursDatei.zahl("1234.56", false)!!, 1e-9)
        assertEquals(0.5, KursDatei.zahl("0,5", true)!!, 1e-9)
        assertEquals(1_200_000.0, KursDatei.zahl("1.2M", false)!!, 1e-6)
        assertNull(KursDatei.zahl("abc", false))
        assertNull(KursDatei.zahl("", false))
    }

    @Test fun symbolAusDateinameUndTicker() {
        assertEquals("AAPL", KursDatei.symbolNormal("aapl.us"))
        assertEquals("VOD.L", KursDatei.symbolNormal("vod.uk"))
        assertEquals("SAP.DE", KursDatei.symbolNormal("sap.de"))
        assertEquals("AAPL", KursDatei.symbolAusName("aapl.us.txt"))
        assertEquals("NVDA", KursDatei.symbolAusName("NVDA Historical Data.csv"))
        assertEquals("BRK-B", KursDatei.symbolAusName("BRK-B.csv"))
        assertEquals("PASSWD", KursDatei.symbolAusName("../../etc/passwd.txt"))
        assertEquals("GBp", KursDatei.waehrung("VOD.L"))
        assertEquals("EUR", KursDatei.waehrung("SAP.DE"))
        assertEquals("USD", KursDatei.waehrung("AAPL"))
    }

    // ------------------------------------------------------------ Speicher und Einhaengen

    private fun reihe(symbol: String, vararg tage: Pair<Int, Double>) =
        KursDatei.Reihe(symbol, tage.map { (d, c) -> Candle(d * tag, c, c, c, c, 10.0) }, "USD")

    @Test fun speichernListeLoeschenUndNeuLaden() {
        val ordner = Files.createTempDirectory("kursdatei").toFile()
        try {
            EigeneKurse.einrichten(ordner)
            assertEquals(0, EigeneKurse.eintraege.value.size)
            val e = einlesen("zwei.zip")
            assertEquals(2, EigeneKurse.speichern(e.reihen))
            assertEquals(listOf("AAPL", "SAP.DE"), EigeneKurse.eintraege.value.map { it.symbol })
            val a = EigeneKurse.eintraege.value.first()
            assertEquals(3, a.anzahl)
            assertEquals(tageSeit(1984, 12, 7), a.vonTag)
            assertEquals(tageSeit(1984, 12, 11), a.bisTag)
            assertEquals(3, EigeneKurse.reihe("aapl").size)
            // Neuer Start aus der Liste und aus den Dateien (Liste fehlt)
            EigeneKurse.einrichten(ordner)
            assertEquals(2, EigeneKurse.eintraege.value.size)
            java.io.File(ordner, "liste.txt").delete()
            EigeneKurse.einrichten(ordner)
            assertEquals(listOf("AAPL", "SAP.DE"), EigeneKurse.eintraege.value.map { it.symbol })
            assertEquals("EUR", EigeneKurse.waehrung("SAP.DE"))
            // Umbenennen bei einer Reihe, Zusammenfuehren mit vorhandenen Tagen
            assertEquals(1, EigeneKurse.speichern(listOf(reihe("X", 20_000 to 5.0)), "aapl"))
            assertEquals(4, EigeneKurse.reihe("AAPL").size)
            EigeneKurse.loeschen("AAPL")
            assertFalse(EigeneKurse.hat("AAPL"))
            assertEquals(listOf("SAP.DE"), EigeneKurse.eintraege.value.map { it.symbol })
            assertTrue(EigeneKurse.reihe("AAPL").isEmpty())
        } finally {
            EigeneKurse.einrichten(null)
            ordner.deleteRecursively()
        }
    }

    private object LeereQuelle : KursQuelle {
        override val praefix = ""
        override val waehrung = ""
        override suspend fun kurse(kennungen: List<String>): Map<String, Quote> = emptyMap()
        override suspend fun kerzen(kennung: String, range: String, interval: String) = Series(emptyList())
        override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String) = Series(emptyList())
        override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()
        override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()
        override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()
        override suspend fun wechselkurse(): Map<String, Double> = emptyMap()
    }

    @Test fun eigeneQuelleAlsRueckfallHinterLeererHauptquelle() = runBlocking {
        val ordner = Files.createTempDirectory("kursdatei").toFile()
        try {
            EigeneKurse.einrichten(ordner)
            EigeneKurse.speichern(einlesen("sap.de.txt").reihen)
            val kette = KursQuellenKette(listOf(LeereQuelle, EigeneQuelle()))
            val q = kette.kurse(listOf("SAP.DE"))["SAP.DE"]
            assertNotNull(q)
            assertEquals(120.6, q!!.price, 1e-9)
            assertEquals(121.4, q.prevClose, 1e-9)
            assertEquals(EigeneKurse.NAME, q.exchange)
            assertEquals("EUR", q.nativeCurrency)
            assertEquals(2, kette.kerzen("SAP.DE", "max", "1d").candles.size)
            assertEquals(1, kette.kerzenZwischen("SAP.DE", tageSeit(2020, 1, 3) * tag, tageSeit(2020, 2, 1) * tag, "1d").candles.size)
            // Untertaegige Kerzen und unbekannte Symbole: nichts
            assertTrue(kette.kerzen("SAP.DE", "1d", "2m").candles.isEmpty())
            assertTrue(kette.kurse(listOf("NIX")).isEmpty())
            assertTrue(kette.kerzen("NIX", "max", "1d").candles.isEmpty())
        } finally {
            EigeneKurse.einrichten(null)
            ordner.deleteRecursively()
        }
    }

    private fun tage(vonTag: Int, bisTag: Int, kurs: Double = 100.0) = (vonTag..bisTag).map { Candle(it * tag, kurs, kurs, kurs, kurs, 1.0) }

    @Test fun luecken_vor_dem_Anfang_und_in_der_Mitte() {
        val basis = tage(30_010, 30_020).filter { it.t / tag !in 30_014L..30_016L }
        val eigen = tage(30_000, 30_030)
        val neu = KursDatei.ergaenzen(basis, eigen, 0L)
        val tageNeu = neu.map { it.t / tag }
        assertEquals((30_000L..30_030L).toList(), tageNeu)
        assertTrue(neu.zipWithNext().all { (a, b) -> a.t < b.t })
        // Fenster begrenzt die Ergaenzung
        val fenster = KursDatei.ergaenzen(basis, eigen, 30_005 * tag, 30_012 * tag)
        assertEquals(30_005L, fenster.first().t / tag)
        assertEquals(30_020L, fenster.last().t / tag)
    }

    @Test fun handelstagEinenTagFruehereintragungWirdAusgeglichen() {
        fun kurs(d: Int) = 100.0 + (d % 7) * 3
        val eigen = (30_000..30_030).map { Candle(it * tag, kurs(it), kurs(it), kurs(it), kurs(it), 1.0) }
        // Der Anbieter traegt den Tag um 23 Uhr des Vortags ein
        val basis = (30_010..30_030).map { Candle((it - 1) * tag + 23 * 3_600_000L, kurs(it), kurs(it), kurs(it), kurs(it), 1.0) }
        assertEquals(-1, KursDatei.verschiebung(basis, eigen))
        val neu = KursDatei.ergaenzen(basis, eigen, 0L)
        assertEquals(31, neu.size)
        assertEquals(31, neu.map { it.t / tag }.toSet().size)
        assertEquals(0, KursDatei.verschiebung(tage(30_010, 30_030), tage(30_000, 30_030)))
    }

    @Test fun massstabPasstSonstKommtNichtsDazu() {
        val basis = tage(30_010, 30_020, 100.0)
        // Andere Waehrung oder Splits: alle gemeinsamen Tage im selben Verhaeltnis, die Ergaenzung wird umgerechnet
        val doppelt = tage(30_000, 30_020, 200.0)
        val skaliert = KursDatei.ergaenzen(basis, doppelt, 0L)
        assertEquals(21, skaliert.size)
        assertEquals(100.0, skaliert.first().close, 1e-9)
        // Ungleichmaessige Verhaeltnisse: Basis bleibt, wie sie ist
        val wirr = tage(30_000, 30_009, 100.0) + (30_010..30_020).map { Candle(it * tag, 100.0 + (it % 5) * 40, 1.0, 1.0, 100.0 + (it % 5) * 40, 1.0) }
        assertSame(basis, KursDatei.ergaenzen(basis, wirr, 0L))
        // Ohne gemeinsame Tage und ohne Anschluss
        val fern = tage(30_000, 30_005, 1000.0)
        assertSame(basis, KursDatei.ergaenzen(basis, fern, 0L))
        // Ohne gemeinsame Tage mit Anschluss
        assertEquals(17, KursDatei.ergaenzen(basis, tage(30_000, 30_005, 98.0), 0L).size)
    }

    @Test fun ergaenztImSpeicherUndBeiWochenkerzen() {
        val ordner = Files.createTempDirectory("kursdatei").toFile()
        try {
            EigeneKurse.einrichten(ordner)
            EigeneKurse.speichern(listOf(KursDatei.Reihe("ABC", tage(30_000, 30_030), "USD")))
            val basis = Series(tage(30_015, 30_030), 1L, 2L)
            val tag1 = EigeneKurse.ergaenzt("ABC", basis, 0L, Long.MAX_VALUE, "1d")
            assertEquals(31, tag1.candles.size)
            assertEquals(1L, tag1.dayStart)
            // Wochenkerzen: nur vor der ersten Kerze, ohne deren Woche
            val woche = EigeneKurse.ergaenzt("ABC", Series(KursDatei.verdichten(tage(30_015, 30_030), "1wk")), 0L, Long.MAX_VALUE, "1wk")
            assertTrue(woche.candles.size > 3)
            assertTrue(woche.candles.zipWithNext().all { (a, b) -> a.t < b.t })
            // Untertaegig, anderes Symbol, Moskauer Boerse: unveraendert
            assertSame(basis, EigeneKurse.ergaenzt("ABC", basis, 0L, Long.MAX_VALUE, "5m"))
            assertSame(basis, EigeneKurse.ergaenzt("XYZ", basis, 0L, Long.MAX_VALUE, "1d"))
            assertSame(basis, EigeneKurse.ergaenzt("MOEX:ABC", basis, 0L, Long.MAX_VALUE, "1d"))
        } finally {
            EigeneKurse.einrichten(null)
            ordner.deleteRecursively()
        }
    }

    @Test fun textenInBeidenSprachenGleichUndOhneGedankenstriche() {
        assertEquals(KursDateiTexte.schluessel(true), KursDateiTexte.schluessel(false))
        for (de in listOf(true, false)) for (k in KursDateiTexte.schluessel(de)) {
            val t = KursDateiTexte.text(de, k, listOf("1", "2"))
            assertTrue("$k $de", t.isNotBlank())
            assertFalse("$k $de", t.contains('–') || t.contains('—') || t.contains(" - "))
        }
        assertEquals("Eigene Datei", KursDateiTexte.text(true, "quelle"))
        assertEquals("Own file", KursDateiTexte.text(false, "quelle"))
        assertEquals("3 Zeilen übersprungen", KursDateiTexte.text(true, "uebersprungen", listOf("3")))
    }
}
