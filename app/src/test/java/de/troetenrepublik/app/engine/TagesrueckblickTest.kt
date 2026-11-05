package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Tagesrueckblick.Archiv
import de.troetenrepublik.app.engine.Tagesrueckblick.Markt
import de.troetenrepublik.app.engine.Tagesrueckblick.Position
import de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class TagesrueckblickTest {

    private val zone = ZoneOffset.UTC
    private val montag = LocalDate.of(2026, 10, 19).toEpochDay()
    private fun um(tag: Long, stunde: Int, minute: Int = 0) =
        LocalDateTime.of(LocalDate.ofEpochDay(tag), java.time.LocalTime.of(stunde, minute)).toInstant(zone).toEpochMilli()

    @Test
    fun faelligAbendsUndMorgensNachgeholtNichtAmWochenende() {
        val leer = Archiv()
        assertEquals(montag, Tagesrueckblick.faellig(um(montag, 22, 15), leer, zone))
        assertNull(Tagesrueckblick.faellig(um(montag, 21, 0), leer, zone))
        assertEquals(montag, Tagesrueckblick.faellig(um(montag + 1, 7, 0), leer, zone))
        assertNull(Tagesrueckblick.faellig(um(montag + 1, 9, 0), leer, zone))
        // Samstagabend nichts, Samstagmorgen der Freitag
        assertNull(Tagesrueckblick.faellig(um(montag + 5, 23, 0), leer, zone))
        assertEquals(montag + 4, Tagesrueckblick.faellig(um(montag + 5, 7, 0), leer, zone))
        // Schon erstellt: nicht mehr faellig; von Hand vorlaeufig erstellt: am Abend doch
        val fertig = Archiv(listOf(Rueckblick(montag, 0L)))
        assertNull(Tagesrueckblick.faellig(um(montag, 22, 30), fertig, zone))
        val vorlaeufig = Archiv(listOf(Rueckblick(montag, 0L, vorlaeufig = true)))
        assertEquals(montag, Tagesrueckblick.faellig(um(montag, 22, 30), vorlaeufig, zone))
    }

    @Test
    fun bewegungenMitNachrichtOderMarkt() {
        val nvidia = Rss.Eintrag("Nvidia shares tumble 5% after new export curbs", "https://example.org/a", um(montag, 15), quelle = "Reuters")
        val alt = Rss.Eintrag("Nvidia jumps 8% on record results", "https://example.org/b", um(montag - 2, 15), quelle = "Reuters")
        val r = Tagesrueckblick.erstellen(
            tag = montag, jetzt = um(montag, 22, 15),
            markt = listOf(Markt("DAX", 0.8), Markt("SP500", -1.0)),
            positionen = listOf(
                Position("NVDA", "Nvidia", 1000.0, -50.0, listOf("Nvidia", "NVDA")),
                Position("SAP.DE", "SAP", 505.0, 5.0, listOf("SAP")),
                Position("ALV.DE", "Allianz", 300.0, 0.3, listOf("Allianz")),
            ),
            nachrichten = listOf(nvidia, alt),
            zone = zone,
        )
        assertEquals(listOf("NVDA", "SAP.DE"), r.bewegungen.map { it.symbol })
        val n = r.bewegungen[0]
        assertEquals(-50.0 / 1050.0 * 100, n.prozent, 1e-9)
        assertEquals(nvidia.titel, n.grund?.titel)
        assertNull(n.markt)
        // SAP ohne Nachricht, aber in dieselbe Richtung wie der DAX
        val s = r.bewegungen[1]
        assertNull(s.grund)
        assertEquals("DAX", s.markt)
        assertEquals(0.8, s.marktAenderung!!, 1e-9)
        assertEquals(1805.0, r.depotWert, 1e-9)
        assertEquals(-44.7, r.depotAenderung, 1e-9)
        assertEquals(-44.7 / (1805.0 + 44.7) * 100, r.depotProzent!!, 1e-9)
        // Die Schlagzeile von vor zwei Tagen zaehlt nicht, die heutige schon
        assertEquals(listOf(nvidia.titel), r.nachrichten.map { it.titel })
        assertFalse(r.vorlaeufig)
    }

    @Test
    fun gegenDenMarktOhneNachrichtBleibtOhneGrund() {
        val r = Tagesrueckblick.erstellen(
            montag, um(montag, 22, 15), listOf(Markt("SP500", 1.0)),
            listOf(Position("AAPL", "Apple", 950.0, -50.0, listOf("Apple"))), emptyList(), zone = zone,
        )
        assertNull(r.bewegungen.single().grund)
        assertNull(r.bewegungen.single().markt)
    }

    @Test
    fun eintragenErsetztDenTagUndHaeltNeuesteZuerst() {
        var a = Archiv()
        a = Tagesrueckblick.eintragen(a, Rueckblick(montag, 1L, vorlaeufig = true))
        a = Tagesrueckblick.eintragen(a, Rueckblick(montag + 1, 2L))
        a = Tagesrueckblick.eintragen(a, Rueckblick(montag, 3L))
        assertEquals(listOf(montag + 1, montag), a.tage.map { it.tag })
        assertEquals(3L, a.tage.last().erstellt)
        a = Tagesrueckblick.eintragen(a, Rueckblick(montag + Tagesrueckblick.AUFBEWAHREN_TAGE + 1, 4L))
        assertTrue(a.tage.none { it.tag == montag })
    }

    @Test
    fun wocheVerkettetTageUndSammeltBewegungen() {
        val b = { p: Double, e: Double -> Tagesrueckblick.Bewegung("NVDA", "Nvidia", p, e) }
        val mo = Rueckblick(montag, 0L, listOf(Markt("DAX", 1.0)), 1000.0, 10.0, 1.0, listOf(b(2.0, 20.0)))
        val di = Rueckblick(montag + 1, 0L, listOf(Markt("DAX", -1.0)), 990.0, -10.0, -1.0, listOf(b(-3.0, -31.0)))
        val naechste = Rueckblick(montag + 7, 0L, depotAenderung = 5.0, depotProzent = 0.5)
        val wochen = Tagesrueckblick.wochen(Archiv(listOf(naechste, di, mo)))
        assertEquals(listOf(montag + 7, montag), wochen.map { it.montag })
        val w = wochen[1]
        assertEquals(listOf(montag + 1, montag), w.tage.map { it.tag })
        assertEquals(0.0, w.depotAenderung, 1e-9)
        assertEquals((1.01 * 0.99 - 1) * 100, w.depotProzent!!, 1e-9)
        assertEquals((1.01 * 0.99 - 1) * 100, w.markt.single { it.schluessel == "DAX" }.aenderung, 1e-9)
        val nv = w.bewegungen.single()
        assertEquals(-11.0, nv.betrag, 1e-9)
        assertEquals((1.02 * 0.97 - 1) * 100, nv.prozent, 1e-9)
        assertEquals(montag + 4, w.freitag)
        assertTrue(Tagesrueckblick.wochenende(montag + 4))
        assertFalse(Tagesrueckblick.wochenende(montag + 3))
    }

    @Test
    fun branchenSpitzenUndWatchlist() {
        val apple = Rss.Eintrag("Apple soars 6% as iPhone sales beat estimates", "https://example.org/c", um(montag, 16), quelle = "Reuters")
        val sp = { s: String, n: String, p: Double -> Tagesrueckblick.Spitze(s, n, p) }
        val r = Tagesrueckblick.erstellen(
            montag, um(montag, 22, 15), listOf(Markt("SP500", 0.4), Markt("WORLD", 0.3)),
            listOf(Position("NVDA", "Nvidia", 1000.0, 20.0, listOf("Nvidia"))), listOf(apple), zone = zone,
            branchen = listOf(Markt("XLU", -1.0), Markt("SMH", 2.0), Markt("KBE", 0.5)),
            marktwerte = listOf(sp("AAPL", "Apple", 6.0), sp("MSFT", "Microsoft", 1.0), sp("TSLA", "Tesla", -4.0), sp("KO", "Coca-Cola", 0.2),
                sp("XOM", "Exxon Mobil", 2.5), sp("META", "Meta Platforms", 3.0)),
            merkliste = listOf(sp("NVDA", "Nvidia", 2.0), sp("AMD", "AMD", -3.0), sp("SAP.DE", "SAP", 0.4)),
        )
        // Branchen staerkste zuerst
        assertEquals(listOf("SMH", "KBE", "XLU"), r.branchen.map { it.schluessel })
        // Drei Gewinner, ein Verlierer (Coca-Cola bewegte sich zu wenig)
        assertEquals(listOf("AAPL", "META", "XOM"), r.gewinner.map { it.symbol })
        assertEquals(apple.titel, r.gewinner.first().grund?.titel)
        assertNull(r.gewinner[1].grund)
        assertEquals(listOf("TSLA"), r.verlierer.map { it.symbol })
        // Watchlist ohne Werte im Depot und ohne kleine Bewegungen
        assertEquals(listOf("AMD"), r.merkliste.map { it.symbol })
        // Massstab ist der MSCI World
        assertEquals("WORLD", Tagesrueckblick.leitmarkt(r.markt)?.schluessel)
        assertEquals("SP500", Tagesrueckblick.leitmarkt(listOf(Markt("DAX", 1.0), Markt("SP500", 0.5)))?.schluessel)
        // Woche: Branchen ueber die Tage verkettet
        val di = Rueckblick(montag + 1, 0L, branchen = listOf(Markt("SMH", -1.0), Markt("XLU", 1.0)))
        val w = Tagesrueckblick.woche(montag, listOf(r, di))
        assertEquals((1.02 * 0.99 - 1) * 100, w.branchen.single { it.schluessel == "SMH" }.aenderung, 1e-9)
        assertEquals(listOf("SMH", "KBE", "XLU"), w.branchen.map { it.schluessel })
    }

    @Test
    fun marktJeWert() {
        assertEquals("SP500", Tagesrueckblick.marktFuer("AAPL"))
        assertEquals("DAX", Tagesrueckblick.marktFuer("SAP.DE"))
        assertEquals("ESTX50", Tagesrueckblick.marktFuer("MC.PA"))
        assertEquals("BTC", Tagesrueckblick.marktFuer("BTC-EUR"))
        assertEquals("NIKKEI", Tagesrueckblick.marktFuer("7203.T"))
    }
}
