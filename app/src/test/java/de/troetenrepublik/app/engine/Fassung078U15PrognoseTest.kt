package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.PrognoseAuswahl.Grund
import de.troetenrepublik.app.engine.PrognoseAuswahl.Merkmale
import de.troetenrepublik.app.market.Prognose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.78: Auswahl „Wichtigste“ der Prognosemaerkte und der grossen Wetten. Titel nach dem Muster der Boersen,
// Zahlen erfunden; dazu die gekuerzten echten Antworten aus prognose/.
class Fassung078U15PrognoseTest {
    private val jetzt = 1_800_000_000_000L
    private val stunde = 3_600_000L

    private fun text(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("prognose/$name")!!.readBytes().toString(Charsets.UTF_8)

    private fun grund(titel: String, vararg worte: String, chance: Double = 0.4, volumen: Double? = 1_000_000.0, rest: Double? = 500.0) =
        PrognoseAuswahl.grund(Merkmale(titel, worte.toSet(), listOf(chance), volumen, rest))

    private fun markt(id: String, chance: Double) = Prognose.Markt(
        Prognose.Quelle.POLYMARKET, id, id, id, listOf(Prognose.Seite("Yes", chance, chance, chance), Prognose.Seite("No", 1 - chance, 1 - chance, 1 - chance)),
        null, 0.0, 0L, true,
    )

    private fun ereignis(
        id: String, titel: String, worte: List<String>, volumen: Double = 1_000_000.0, volumen24h: Double = 10_000.0,
        chancen: List<Double> = listOf(0.4), endeInStunden: Int = 500, quelle: Prognose.Quelle = Prognose.Quelle.POLYMARKET,
    ) = Prognose.Ereignis(
        quelle, id, titel, "", worte.firstOrNull().orEmpty(), "", "", volumen, volumen24h, jetzt + endeInStunden * stunde,
        chancen.mapIndexed { i, c -> markt("$id-$i", c) }, "", schlagworte = worte,
    )

    private fun wette(titel: String, thema: String, preis: Double = 0.4, betrag: Double = 20_000.0, slug: String = "ereignis") =
        Prognose.GrosseWette(titel, "Yes", true, betrag, preis, jetzt, slug, "", thema)

    @Test fun sportFaelltRaus() {
        assertEquals(Grund.SPORT, grund("Fed decision in December?", "sports"))
        assertEquals(Grund.SPORT, grund("Team Liquid wins?", "esports"))
        // Sportwetten unter anderem oder ohne Schlagwort: Liga, Turnier, Paarung, Spielergebnis
        assertEquals(Grund.SPORT, grund("NBA Champion 2027"))
        assertEquals(Grund.SPORT, grund("Will Arsenal win the Premier League?", "world"))
        assertEquals(Grund.SPORT, grund("Lakers vs. Celtics"))
        assertEquals(Grund.SPORT, grund("Sabalenka vs Li"))
        assertEquals(Grund.SPORT, grund("Chiefs @ Bills"))
        assertEquals(Grund.SPORT, grund("Spread: Lakers (-3.5)"))
        assertEquals(Grund.SPORT, grund("Bayern Munich: Both Teams to Score", "other"))
        assertEquals(Grund.SPORT, grund("T1 to win Map 2?"))
        assertEquals(Grund.SPORT, grund("LoL: T1 - Gen.G", "other"))
        assertEquals(Grund.SPORT, grund("Who wins? nba lal bos 2026 11 12"))
        // „vs.“ in einem Anlegerthema ist keine Spielpaarung
        assertNull(grund("Trump vs. Newsom: who wins the 2028 election?", "politics"))
        assertNull(grund("US opens embassy in Damascus?", "geopolitics"))
        assertNull(grund("10Y-2Y yield spread above 1% in 2027?", "economy"))
    }

    @Test fun unwichtigesFaelltRaus() {
        assertEquals(Grund.UNTERHALTUNG, grund("Best Picture winner", "culture"))
        assertEquals(Grund.UNTERHALTUNG, grund("Top grossing movie 2026", "entertainment"))
        assertEquals(Grund.UNTERHALTUNG, grund("Who will win Album of the Year at the Grammys?"))
        assertEquals(Grund.UNTERHALTUNG, grund("Will the two stars get engaged in 2026?", "other"))
        assertEquals(Grund.WETTER, grund("Highest temperature in NYC on November 12?", "weather"))
        assertEquals(Grund.WETTER, grund("Will it rain in Seattle tomorrow?", "climate and weather"))
        assertEquals(Grund.ZAEHLER, grund("# of posts by the president this week?", "politics"))
        assertEquals(Grund.ZAEHLER, grund("How many tweets from November 7 to 14?", "tech"))
        assertEquals(Grund.ZAEHLER, grund("What will Powell say during the press conference?", "mentions"))
        assertEquals(Grund.ZAEHLER, grund("Will the CEO mention tariffs on the earnings call?", "earnings"))
        assertEquals(Grund.KURZFRIST, grund("Bitcoin Up or Down - November 12, 3:00PM-3:15PM ET", "crypto"))
        assertEquals(Grund.KURZFRIST, grund("Ethereum above 4,000 on November 12, 5PM ET?", "crypto"))
        assertEquals(Grund.KURZFRIST, grund("Bitcoin above 110,000 on November 13?", "crypto", rest = 20.0))
        assertEquals(Grund.KURZFRIST, grund("S&P 500 Up or Down on November 12?", "finance"))
        assertEquals(Grund.KURZFRIST, grund("Price check btc updown 15m 1762960500"))
    }

    @Test fun schwellen() {
        assertEquals(50_000.0, PrognoseAuswahl.MIN_VOLUMEN, 0.0)
        assertEquals(0.02, PrognoseAuswahl.ENTSCHIEDEN_RAND, 0.0)
        assertEquals(48.0, PrognoseAuswahl.KRYPTO_MINDESTFRIST_STUNDEN, 0.0)
        assertEquals(Grund.VOLUMEN, grund("Fed decision in December?", "economy", volumen = 49_999.0))
        assertNull(grund("Fed decision in December?", "economy", volumen = 50_000.0))
        // Ohne Angabe der Quelle (grosse Wetten) entscheidet der Umsatz nicht
        assertNull(grund("Fed decision in December?", "economy", volumen = null))
        assertEquals(Grund.ENTSCHIEDEN, grund("US recession in 2026?", "economy", chance = 0.02))
        assertEquals(Grund.ENTSCHIEDEN, grund("US recession in 2026?", "economy", chance = 0.985))
        assertNull(grund("US recession in 2026?", "economy", chance = 0.03))
        assertNull(grund("US recession in 2026?", "economy", chance = 0.97))
        // Mehrere Ausgaenge: entschieden erst, wenn keiner mehr offen ist
        assertEquals(Grund.ENTSCHIEDEN, PrognoseAuswahl.grund(Merkmale("Fed decision", setOf("fed"), listOf(0.99, 0.005, 0.005), 1e6, 500.0)))
        assertNull(PrognoseAuswahl.grund(Merkmale("Fed decision", setOf("fed"), listOf(0.99, 0.6, 0.01), 1e6, 500.0)))
        // Krypto: ab 48 Stunden Restlaufzeit bleibt der Markt
        assertEquals(Grund.KURZFRIST, grund("Bitcoin above 150,000?", "crypto", rest = 47.9))
        assertNull(grund("Bitcoin above 150,000?", "crypto", rest = 48.0))
        assertNull(grund("Fed decision in December?", "economy", rest = 5.0))
    }

    @Test fun wasFuerAnlegerZaehltBleibt() {
        listOf(
            "Fed decision in December?" to "fed", "ECB rate cut in January?" to "economy", "CPI core in October" to "economics",
            "US recession in 2026?" to "economy", "Presidential Election Winner 2028" to "elections", "Who will be the next Fed chair?" to "politics",
            "Russia x Ukraine ceasefire in 2026?" to "geopolitics", "Will China invade Taiwan in 2026?" to "geopolitics",
            "Nvidia quarterly earnings beat?" to "earnings", "Largest company by market cap on December 31?" to "finance",
            "OpenAI IPO in 2026?" to "tech", "Which company has the best AI model at the end of the year?" to "ai",
            "Will the merger close before July?" to "companies", "Crude oil above \$90 at the end of 2026?" to "finance",
            "Gold above \$5,000 in 2026?" to "finance", "Bitcoin above 150,000 by December 31?" to "crypto",
            "Spot Solana ETF approved in 2026?" to "crypto", "New tariffs on the EU before March?" to "tariffs",
            "Atlantic hurricane season: major hurricanes" to "climate and weather", "Will the stablecoin bill become law?" to "politics",
        ).forEach { (titel, wort) -> assertNull(titel, grund(titel, wort)) }
    }

    @Test fun ereignisseGefiltertUndNachUmsatz() {
        val alle = listOf(
            ereignis("fed", "Fed decision in December?", listOf("economy", "fed"), volumen24h = 500.0),
            ereignis("wahl", "Presidential Election Winner 2028", listOf("politics"), volumen24h = 9_000.0),
            // dasselbe Ereignis aus einer zweiten Kategorie
            ereignis("wahl", "Presidential Election Winner 2028", listOf("elections"), volumen24h = 9_000.0),
            ereignis("ki", "Best AI model at the end of the year?", listOf("tech"), volumen = 80_000.0, volumen24h = 500.0),
            ereignis("nba-lal-bos-2026-11-12", "Lakers at Celtics", listOf("world")),
            ereignis("klein", "Will the merger close before July?", listOf("finance"), volumen = 12_000.0, volumen24h = 99_000.0),
            ereignis("durch", "Fed decision in October?", listOf("fed"), chancen = listOf(0.995, 0.003, 0.002)),
            ereignis("btc-heute", "Bitcoin above 110,000 on November 13?", listOf("crypto"), endeInStunden = 12),
            ereignis("btc-jahr", "Bitcoin above 150,000 by December 31?", listOf("crypto"), volumen24h = 20_000.0, endeInStunden = 900),
        )
        assertEquals(listOf("btc-jahr", "wahl", "fed", "ki"), PrognoseAuswahl.ereignisse(alle, jetzt).map { it.id })
        // gleicher Tagesumsatz: der groessere Gesamtumsatz zuerst
        assertEquals(1_000_000.0, PrognoseAuswahl.ereignisse(alle, jetzt)[2].volumen, 0.0)
        // Kalshi: die Kennung zaehlt nicht zum Text
        assertTrue(PrognoseAuswahl.wichtig(ereignis("KXFED-T20", "Fed funds rate", listOf("economics"), quelle = Prognose.Quelle.KALSHI), jetzt))
        assertFalse(PrognoseAuswahl.wichtig(ereignis("KXGAME", "Pro football champion", listOf("sports"), quelle = Prognose.Quelle.KALSHI), jetzt))
        assertFalse(PrognoseAuswahl.wichtig(ereignis("KXTOP", "Top song on November 20", listOf("entertainment"), quelle = Prognose.Quelle.KALSHI), jetzt))
    }

    @Test fun echteAntworten() {
        val poly = Prognose.polyListeLesen(text("poly_liste.json"))
        val fed = poly.first { it.titel.startsWith("Fed Decision") }
        assertTrue(fed.schlagworte.containsAll(listOf("fed", "economy", "fed rates")))
        assertTrue(PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(fed, jetzt)).let { it == null || it == Grund.ENTSCHIEDEN })
        val tennis = poly.first { it.titel.contains("Tennis") }
        assertTrue("sports" in tennis.schlagworte)
        assertEquals(Grund.SPORT, PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(tennis, jetzt)))
        val kalshi = Prognose.kalshiListeLesen(text("kalshi_trends.json")).ereignisse
        assertTrue(kalshi.isNotEmpty())
        kalshi.forEach { assertEquals(listOfNotNull(it.kategorie.lowercase().takeIf { k -> k.isNotBlank() }), it.schlagworte) }
        assertTrue(kalshi.filter { it.kategorie == "Economics" }.none { PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(it, jetzt)) in setOf(Grund.SPORT, Grund.UNTERHALTUNG, Grund.WETTER, Grund.ZAEHLER) })
    }

    @Test fun grosseWetten() {
        val alle = listOf(
            wette("Fed decision in December?", "fed", betrag = 90_000.0),
            wette("Lakers vs. Celtics", "sports", betrag = 80_000.0),
            wette("Spread: Chiefs (-2.5)", "", betrag = 70_000.0),
            wette("Who wins?", "", betrag = 65_000.0, slug = "epl-ars-che-2026-11-12"),
            wette("Presidential Election Winner 2028", "elections", betrag = 60_000.0),
            wette("Best Picture winner", "culture", betrag = 50_000.0),
            wette("Russia x Ukraine ceasefire in 2026?", "ukraine", preis = 0.995, betrag = 40_000.0),
            wette("Bitcoin Up or Down - November 12, 3PM ET", "crypto", betrag = 30_000.0),
            wette("What will the president say on Friday?", "mentions", betrag = 25_000.0),
            wette("Highest temperature in NYC today?", "weather", betrag = 22_000.0),
            wette("Bitcoin above 150,000 by December 31?", "crypto", betrag = 20_000.0),
            wette("OpenAI IPO in 2026?", "", betrag = 15_000.0),
        )
        // dieselbe Regel, Reihenfolge nach Einsatz bleibt
        assertEquals(
            listOf("Fed decision in December?", "Presidential Election Winner 2028", "Bitcoin above 150,000 by December 31?", "OpenAI IPO in 2026?"),
            PrognoseAuswahl.grosse(alle).map { it.titel },
        )
        assertEquals(Grund.ENTSCHIEDEN, PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(alle[6])))
    }

    @Test fun sammelKategorien() {
        assertEquals(listOf("economy", "finance", "geopolitics", "politics", "world", "elections", "tech", "crypto"), PrognoseAuswahl.sammelKategorien(Prognose.POLY_KATEGORIEN))
        assertEquals(
            listOf("Economics", "Financials", "Politics", "Elections", "Science and Technology", "Crypto", "Companies", "Climate and Weather", "Health"),
            PrognoseAuswahl.sammelKategorien(Prognose.KALSHI_KATEGORIEN),
        )
        // Die Kategorien der Boersen bleiben, wie sie waren
        assertEquals(10, Prognose.POLY_KATEGORIEN.size)
        assertEquals(12, Prognose.KALSHI_KATEGORIEN.size)
        assertEquals("economy", Prognose.POLY_KATEGORIEN.first().schluessel)
    }
}
