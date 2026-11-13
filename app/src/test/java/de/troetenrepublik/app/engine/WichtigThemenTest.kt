package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.WichtigThemen.Kat
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Wichtig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Themen im Reiter „Wichtigste“: Zuordnung zu Kategorien, Wechsel der Kategorien, Buendelung. Alle Titel sind erfunden.
class WichtigThemenTest {

    private val t = 1_790_000_000_000L
    private fun e(titel: String, her: String = "cnbc.com", zeit: Long = t, link: String = titel, kategorien: Set<Rss.Kategorie> = emptySet()) =
        Rss.Eintrag(titel, link, zeit, herausgeber = her, quelle = her, kategorien = kategorien)

    private fun kat(titel: String, kategorien: Set<Rss.Kategorie> = emptySet()): Kat {
        val x = e(titel, kategorien = kategorien)
        return WichtigThemen.kategorie(x, Wichtig.bewerten(x).gruende)
    }

    @Test
    fun kategorienNachStichwoertern() {
        assertEquals(Kat.ZENTRALBANKEN, kat("Fed cuts rates by half a point in emergency move"))
        assertEquals(Kat.ZENTRALBANKEN, kat("EZB senkt den Leitzins auf 1,5 Prozent"))
        assertEquals(Kat.KONJUNKTUR, kat("Consumer prices rose 4.1% annually in June"))
        assertEquals(Kat.POLITIK, kat("US imposes new tariffs on chip imports"))
        assertEquals(Kat.POLITIK, kat("Oil prices surge 9% after attack on tanker near Hormuz"))
        assertEquals(Kat.POLITIK, kat("Trump says we don't need a deal with Iran"))
        assertEquals(Kat.KRYPTO, kat("Bitcoin plunges 12% in sell-off, liquidations top \$1 billion"))
        assertEquals(Kat.UNTERNEHMEN, kat("Samsung meldet Rekordgewinn im dritten Quartal"))
        assertEquals(Kat.ROHSTOFFE, kat("Gold hits a record as the Fed signals patience"))
        assertEquals(Kat.MAERKTE, kat("Dax steigt erstmals über 27 000 Punkte"))
        // Ohne Stichwort gilt der Feed, sonst die Märkte
        assertEquals(Kat.KRYPTO, kat("Wallet makers face an uphill path", setOf(Rss.Kategorie.KRYPTO)))
        assertEquals(Kat.MAERKTE, kat("Quiet session for regional lenders"))
    }

    private fun thema(id: String, kat: Kat, punkte: Int, zeit: Long = t) = WichtigThemen.Thema(e(id, link = id, zeit = zeit), emptyList(), kat, punkte)

    @Test
    fun kategorienImWechselNachWichtigkeit() {
        val themen = listOf(
            thema("m1", Kat.MAERKTE, 8), thema("m2", Kat.MAERKTE, 7, t + 1), thema("m3", Kat.MAERKTE, 7),
            thema("p1", Kat.POLITIK, 9), thema("p2", Kat.POLITIK, 6),
            thema("k1", Kat.KRYPTO, 5),
        )
        val reihe = WichtigThemen.abwechselnd(themen).map { it.kopf.id }
        // Runde 1: je Kategorie das wichtigste, nach Punkten; Runde 2: das zweitwichtigste; Runde 3: der Rest
        assertEquals(listOf("p1", "m1", "k1", "m2", "p2", "m3"), reihe)
    }

    @Test
    fun geschichteWirdEinThemaMitWeiteren() {
        val tanker = listOf("reuters.com", "bloomberg.com", "cnn.com").mapIndexed { i, h ->
            e("Oil prices surge 9% after attack on tanker near Hormuz", h, zeit = t + i * 600_000L, link = "tanker$i")
        }
        val einzeln = listOf(
            e("Fed cuts rates by half a point in emergency move", link = "fed"),
            e("Bitcoin plunges 12% in sell-off, liquidations top \$1 billion", link = "btc"),
            e("Samsung meldet Rekordgewinn im dritten Quartal", "tagesschau.de", link = "samsung"),
        )
        val ruhig = (1..30).map { e("Quiet trading in regional markets number $it", "reuters.com", link = "r$it") }
        val themen = WichtigThemen.themen(tanker + einzeln + ruhig)
        assertEquals(4, themen.size)
        val politik = themen.single { it.kat == Kat.POLITIK }
        assertTrue(politik.kopf.id.startsWith("tanker"))
        assertEquals(2, politik.weitere.size)
        // Jede Kategorie kommt einmal vor, die Runde ist nach Punkten geordnet
        assertEquals(setOf(Kat.POLITIK, Kat.ZENTRALBANKEN, Kat.KRYPTO, Kat.UNTERNEHMEN), themen.map { it.kat }.toSet())
        assertEquals(themen.map { it.punkte }, themen.map { it.punkte }.sortedDescending())
        // Dieselbe Auswahl wie die Markierung der Nachrichtenseite
        assertEquals(Wichtig.wichtige(tanker + einzeln + ruhig), themen.map { it.kopf.id }.toSet())
    }
}
