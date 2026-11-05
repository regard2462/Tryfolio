package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Prognose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: grosse Wetten mit genaueren Themen und 60 Tagen, auffaellige Wetten erfolgreicher Polymarket-Haendler.
// Wallets und Betraege sind erfunden.
class Fassung592WettprofisTest {
    private val a = "0x" + "a".repeat(40)
    private val b = "0x" + "b".repeat(40)
    private val c = "0x" + "c".repeat(40)
    private val jetzt = 1_800_000_000_000L
    private val stunde = 3_600_000L

    private fun wette(wallet: String, betrag: Double, preis: Double, vorStunden: Int, thema: String = "politics", titel: String = "Wahl?", ausgang: String = "Yes", kauf: Boolean = true) =
        Prognose.GrosseWette(titel, ausgang, kauf, betrag, preis, jetzt - vorStunden * stunde, "ereignis", "", thema, wallet)

    @Test fun ranglisteLesen() {
        // Aufbau der Data API v2 (seit 5.94)
        val t = """{"data":[{"rank":1,"user_id":"${a.uppercase().replace("0X", "0x")}","volume":1000000,"pnl":250000.5},""" +
            """{"rank":2,"user_id":"kaputt","pnl":5},{"rank":3,"user_id":"$b","pnl":30000,"volume":10}],"pagination":{"next_cursor":null}}"""
        val l = Prognose.ranglisteLesen(t, "POLITICS", "ALL")
        assertEquals(2, l.size)
        assertEquals(a, l[0].wallet)
        assertEquals(1, l[0].rang)
        assertEquals(250_000.5, l[0].gewinn, 1e-9)
        assertEquals(3, l[1].rang)
        assertTrue(Prognose.ranglisteAdresse("FINANCE", "MONTH").contains("category=FINANCE&timePeriod=MONTH&orderBy=PNL"))
        assertNull(Prognose.haendlerAdresse("0x123"))
        assertTrue(Prognose.haendlerAdresse(a)!!.contains("user=$a"))
    }

    @Test fun profisBesterPlatz() {
        val p = Wettprofis.profis(
            listOf(
                Wettprofis.Platz(a, "POLITICS", "ALL", 12, 900_000.0, 0.0),
                Wettprofis.Platz(a, "FINANCE", "MONTH", 3, 60_000.0, 0.0),
                // Zu wenig Gewinn und Sport zaehlen nicht
                Wettprofis.Platz(b, "POLITICS", "MONTH", 1, 10_000.0, 0.0),
                Wettprofis.Platz(c, "SPORTS", "ALL", 1, 5_000_000.0, 0.0),
            ),
        )
        assertEquals(setOf(a), p.keys)
        assertEquals(3, p.getValue(a).rang)
        assertEquals("FINANCE", p.getValue(a).kategorie)
        assertEquals(900_000.0, p.getValue(a).gewinn, 1e-9)
        assertEquals(1.0, Wettprofis.Profi(a, "POLITICS", "ALL", 1, 1.0).gewicht, 1e-9)
        assertEquals(0.3, Wettprofis.Profi(a, "POLITICS", "ALL", 80, 1.0).gewicht, 1e-9)
    }

    @Test fun auffaelligeWetten() {
        val profis = mapOf(a to Wettprofis.Profi(a, "POLITICS", "ALL", 2, 1e6), b to Wettprofis.Profi(b, "ECONOMICS", "MONTH", 40, 1e5))
        val ab = jetzt - 24 * stunde
        val l = Wettprofis.auffaellig(
            listOf(
                // Aussenseiter in zwei Teilen: zusammen 12.000 $ zu gewichtet 20 %
                wette(a, 8_000.0, 0.2, 2), wette(a, 4_000.0, 0.2, 1),
                // Sport zaehlt nicht, auch nicht bei einem Profi
                wette(a, 90_000.0, 0.1, 3, thema = "sports", titel = "Spiel?"),
                // Kein Profi
                wette(c, 200_000.0, 0.1, 3, titel = "Andere?"),
                // Verkauf und zu alt zaehlen nicht
                wette(b, 60_000.0, 0.5, 2, titel = "Zinsen?", kauf = false),
                wette(b, 60_000.0, 0.5, 30, titel = "Alt?"),
                // Gross, aber Favorit: Grund GROSS
                wette(b, 70_000.0, 0.8, 5, titel = "Zoll?"),
                // Klein und Favorit: kein Grund
                wette(b, 6_000.0, 0.9, 5, titel = "Klein?"),
            ),
            profis, ab,
        )
        assertEquals(2, l.size)
        val aussen = l.single { it.wette.wallet == a }
        assertEquals(12_000.0, aussen.wette.betrag, 1e-9)
        assertEquals(0.2, aussen.wette.preis, 1e-9)
        assertEquals(listOf(Wettprofis.Grund.AUSSENSEITER), aussen.gruende)
        val gross = l.single { it.wette.wallet == b }
        assertEquals(listOf(Wettprofis.Grund.GROSS), gross.gruende)
        // Aussenseiter eines besseren Profis steht vorn
        assertEquals(a, l.first().wette.wallet)
    }

    @Test fun ungewoehnlichGross() {
        val profis = mapOf(a to Wettprofis.Profi(a, "FINANCE", "ALL", 5, 1e6))
        val l = Wettprofis.auffaellig(
            listOf(
                wette(a, 6_000.0, 0.6, 1, titel = "A?"), wette(a, 7_000.0, 0.6, 1, titel = "B?"), wette(a, 6_500.0, 0.6, 1, titel = "C?"),
                wette(a, 30_000.0, 0.6, 1, titel = "D?"),
            ),
            profis, jetzt - 24 * stunde,
        )
        assertEquals("D?", l.single().wette.titel)
        assertEquals(listOf(Wettprofis.Grund.UNGEWOEHNLICH), l.single().gruende)
    }

    // Beide Seiten desselben Markts (Absicherung) und fast sichere Ausgaenge zaehlen nicht.
    @Test fun absicherungUndFavoriten() {
        val profis = mapOf(a to Wettprofis.Profi(a, "POLITICS", "MONTH", 1, 1e6), b to Wettprofis.Profi(b, "POLITICS", "MONTH", 2, 1e6))
        val l = Wettprofis.auffaellig(
            listOf(
                wette(a, 60_000.0, 0.4, 2, titel = "Wahl?", ausgang = "Yes"), wette(a, 80_000.0, 0.55, 1, titel = "Wahl?", ausgang = "No"),
                wette(b, 150_000.0, 0.95, 1, titel = "Zinsen?"),
                wette(b, 60_000.0, 0.3, 1, titel = "Wahl?"),
            ),
            profis, jetzt - 24 * stunde,
        )
        assertEquals(listOf(b to "Wahl?"), l.map { it.wette.wallet to it.wette.titel })
        assertEquals(listOf(Wettprofis.Grund.AUSSENSEITER, Wettprofis.Grund.GROSS), l.single().gruende)
    }

    @Test fun themenUndZeitraeume() {
        assertEquals("fed", Prognose.thema(listOf("economy", "fed-rates")))
        assertEquals("middle-east", Prognose.thema(listOf("geopolitics", "iran")))
        assertEquals("sports", Prognose.thema(listOf("sports", "ai")))
        assertEquals("economy", Prognose.thema(listOf("economy", "inflation")))
        assertTrue(Prognose.THEMEN.map { it.schluessel }.containsAll(listOf("fed", "tariffs", "earnings", "ukraine", "ai", "weather", "mentions")))
        assertEquals(4, Prognose.WettZeit.entries.size)
        assertEquals(60 * 24, Prognose.WettZeit.ZWEI_MONATE.stunden)
        // Wallet nur bei gueltiger Adresse
        val w = Prognose.grosseWettenLesen("""{"data":[{"proxy_wallet":"$a","side":"BUY","size":100,"price":0.5,"timestamp":1,"title":"T","outcome":"Yes","event_slug":"e"}]}""")
        assertEquals(a, w.single().wallet)
    }
}
