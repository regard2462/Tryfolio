package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Wichtig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Punkte fuer wichtige Schlagzeilen; alle Titel sind erfunden.
class WichtigTest {

    private val t = 1_790_000_000_000L
    private fun e(titel: String, her: String = "cnbc.com", quelle: String = "CNBC", zeit: Long = t, quellen: Set<String> = emptySet(), link: String = titel) =
        Rss.Eintrag(titel, link, zeit, herausgeber = her, quelle = quelle, quellen = quellen)

    private fun punkte(titel: String, her: String = "cnbc.com", depot: List<String> = emptyList(), quellen: Set<String> = emptySet()) =
        Wichtig.bewerten(e(titel, her, quellen = quellen), depot).punkte

    @Test
    fun ereignisseBekommenPunkte() {
        assertTrue(punkte("Fed cuts rates by a quarter point, signals more easing") >= 7)
        assertTrue(punkte("EZB senkt den Leitzins auf 2,25 Prozent", "tagesschau.de") >= 7)
        assertTrue(punkte("Consumer prices rose 3.2% annually in May") >= 6)
        assertTrue(punkte("Ölpreise steigen nach Angriffen auf Tanker vor der Küste Irans", "tagesschau.de") >= 6)
        assertTrue(punkte("Tech selloff deepens as chip stocks tumble") >= 5)
        assertTrue(punkte("Samsung meldet Rekordgewinn im dritten Quartal", "tagesschau.de") >= 5)
        assertTrue(punkte("US imposes new tariffs on steel imports from three countries") >= 6)
    }

    @Test
    fun ramschUndRoutineZaehlenNicht() {
        assertEquals(0, punkte("3 AI Stocks to Buy Right Now and Hold for 10 Years", "fool.com"))
        assertEquals(0, punkte("2 Growth Stocks to Buy If the Market Crashes", "finance.yahoo.com"))
        assertEquals(0, punkte("Is the stock market about to crash?"))
        assertEquals(0, punkte("FICTIVE MINING DRILLS 1.2 G/T GOLD OVER 200M AT NORTH PROJECT", "finance.yahoo.com"))
        assertEquals(0, punkte("Speech by Commissioner Example on trade and tariffs", "EU-Kommission"))
        assertEquals(0, punkte("Coffee prices edge higher in quiet trading", "reuters.com"))
        assertEquals(0, punkte("Analyst raises price target for Example Corp to 120 dollars"))
    }

    @Test
    fun abzuegeUndAmtlicheQuellen() {
        val fakt = punkte("Germany's economy shrank 0.3% in the second quarter")
        val vermutung = punkte("Germany's economy could shrink in the second quarter, survey says")
        assertTrue(vermutung < fakt)
        assertTrue(punkte("Retail inflation jumps to 5.5% in India") < punkte("Retail inflation jumps to 5.5% in Germany"))
        // Behoerden zaehlen nur mit eigenen Entscheidungen und Daten
        assertTrue(punkte("Federal Reserve Board announces enforcement action against a bank", "Federal Reserve", quellen = setOf("fed")) <= 3)
        assertTrue(punkte("Inflationsrate im Mai bei +2,1 %", "Destatis", quellen = setOf("destatis")) >= 6)
        // Aussage mit angehaengter Frage zaehlt, mit Abzug
        assertTrue(punkte("Apple hits a $5 trillion milestone as rest of tech falters. Can it hold?") >= Wichtig.SCHWELLE)
    }

    @Test
    fun depotBringtZuschlag() {
        val ohne = punkte("Nvidia shares slide 8% after the company cuts its outlook")
        val mit = punkte("Nvidia shares slide 8% after the company cuts its outlook", depot = listOf("Nvidia"))
        assertEquals(ohne + 2, mit)
        val begriffe = Wichtig.depotBegriffe(listOf("Apple Inc.", "Deutsche Bank AG", "SAP SE"), listOf("AAPL", "SAP.DE", "EURUSD=X"))
        assertTrue("Apple" in begriffe && "Deutsche Bank" in begriffe && "AAPL" in begriffe && "SAP" in begriffe)
        assertFalse(begriffe.any { it.startsWith("EUR") && it.length > 3 })
        assertEquals("reuters.com", Rss.domain(e("Example", "reuters.com")))
    }

    @Test
    fun breiteBerichterstattungUndHoechstensZweiJeGeschichte() {
        val geschichte = listOf("reuters.com", "bloomberg.com", "marketwatch.com", "cnn.com").mapIndexed { i, h ->
            e("Example Robotics unveils humanoid factory robot for car makers", h, zeit = t + i * 600_000L, link = "g$i")
        }
        val rest = (1..20).map { e("Quiet trading in regional markets number $it", "reuters.com", link = "r$it") }
        val w = Wichtig.wichtige(geschichte + rest)
        assertEquals(2, geschichte.count { it.id in w })
        assertTrue(rest.none { it.id in w })
    }

    @Test
    fun obergrenzeZwanzigProzent() {
        val wichtig = listOf(
            "Fed cuts rates by half a point in emergency move",
            "EZB senkt den Leitzins auf 1,5 Prozent",
            "Consumer prices rose 4.1% annually in June",
            "US imposes new tariffs on chip imports",
            "Oil prices surge 9% after attack on tanker near Hormuz",
        ).mapIndexed { i, s -> e(s, link = "w$i") }
        val ruhig = (1..5).map { e("Quiet session for regional lenders number $it", link = "q$it") }
        val w = Wichtig.wichtige(wichtig + ruhig)
        assertEquals(2, w.size)
        assertTrue(w.all { it.startsWith("w") })
        assertTrue(Wichtig.wichtige(ruhig).isEmpty())
    }

    @Test
    fun sammelmeldungenZuEinemWert() {
        // Terminkalender, die Yahoo jedem genannten Unternehmen zuordnet (so bei Umicore)
        assertTrue(MarketApi.istSammelmeldung("TAGESVORSCHAU: Termine am 31. Juli 2026"))
        assertTrue(MarketApi.istSammelmeldung("WOCHENVORSCHAU: Termine bis 13. August 2026"))
        assertTrue(MarketApi.istSammelmeldung("dpa-AFX-Überblick: KONJUNKTUR vom 30.07.2026"))
        assertTrue(MarketApi.istSammelmeldung("Earnings calendar: what to watch this week"))
        // Meldungen ueber das Unternehmen bleiben
        assertFalse(MarketApi.istSammelmeldung("Umicore senkt Prognose für Batteriematerialien"))
        assertFalse(MarketApi.istSammelmeldung("Umicore: Zahlen am 31. Juli, Aktie legt zu"))
        assertFalse(MarketApi.istSammelmeldung("Nvidia unveils new chips ahead of earnings"))
    }

    @Test
    fun mitteilungenNurNeueUndWichtige() {
        val h = 3_600_000L
        val now = t
        val alt = e("Alt", zeit = now - 3 * h, link = "a")
        val frueher = e("Vor 13 Stunden", zeit = now - 13 * h, link = "f")
        val neu1 = e("Neu eins", zeit = now - 30 * 60_000L, link = "n1")
        val neu2 = e("Neu zwei", zeit = now - 10 * 60_000L, link = "n2")
        val zukunft = e("Falsche Zeit", zeit = now + h, link = "z")
        val liste = listOf(neu2, alt, frueher, zukunft, neu1)
        // Alles nach der letzten Meldung, aelteste zuerst; nichts aelter als zwoelf Stunden, nichts aus der Zukunft
        assertEquals(listOf("n1", "n2"), Rss.zuMelden(liste, now - h, now).map { it.link })
        assertEquals(listOf("a", "n1", "n2"), Rss.zuMelden(liste, 0L, now).map { it.link })
        // Nur wichtige: nur die markierten
        assertEquals(listOf("n2"), Rss.zuMelden(liste, now - h, now, setOf("n2", "a")).map { it.link })
        assertTrue(Rss.zuMelden(liste, now, now).isEmpty())
    }
}
