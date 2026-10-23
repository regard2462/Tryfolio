package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.market.Briefquellen
import de.troetenrepublik.app.market.Personenbilder
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.TruthNews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Indikatoren mit Deutung, Briefe, Trump in den News, Fotos, Depot-Check fuer Personen, Optionsschein zu Optionen.
class Fassung42Test {

    private fun ms(iso: String) = LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun indikatorenUndDeutung() {
        // Gleichmaessig steigend: ueber beiden Durchschnitten, RSI ueberkauft, MACD ueber der Signallinie
        val werte = (0 until 260).map { 100.0 + it * 0.5 + it * it * 0.01 }
        val tage = werte.indices.map { 20_000L + it }
        val d = Indikatoren.deuten(tage, werte).associateBy { it.art }
        assertEquals("ueber", d.getValue("sma50").lage)
        assertEquals("ueber", d.getValue("sma200").lage)
        assertEquals("ueberkauft", d.getValue("rsi").lage)
        assertEquals("steigt", d.getValue("macd").lage)
        assertNotNull(d["bb"])
        // EMA beginnt mit dem einfachen Durchschnitt. Bollinger um eine konstante Reihe hat keine Breite
        assertEquals(2.0, Indikatoren.ema(listOf(1.0, 2.0, 3.0), 3)[2]!!, 1e-9)
        val b = Indikatoren.bollinger(List(30) { 5.0 })
        assertEquals(5.0, b.oben.last()!!, 1e-9)
        assertEquals(5.0, b.unten.last()!!, 1e-9)
        // Golden Cross: erst fallend, dann stark steigend
        val kreuz = (0 until 250).map { 200.0 - it * 0.3 } + (0 until 70).map { 125.0 + it * 2.0 }
        val dk = Indikatoren.deuten(kreuz.indices.map { 20_000L + it }, kreuz).first { it.art == "sma200" }
        assertTrue("Golden Cross erkannt", dk.kreuzTag != null && dk.kreuzAuf)
        assertTrue(Indikatoren.deuten(listOf(1L, 2L), listOf(1.0, 2.0)).isEmpty())
    }

    @Test
    fun briefeVonUebersichtsseiten() {
        val q = Rss.eingebaut(deutsch = true).first { it.schluessel == "brief-berkshire" }
        val html = """<a href="2023ltr.pdf">2023</a> <a href="https://www.berkshirehathaway.com/letters/2025ltr.pdf">2025</a> <a href="2024ltr.pdf">2024</a>"""
        val e = Briefquellen.lesen(html, q, deutsch = true).single()
        assertEquals("https://www.berkshirehathaway.com/letters/2025ltr.pdf", e.link)
        assertEquals("Brief an die Aktionäre 2025", e.titel)
        assertTrue(Rss.Kategorie.BRIEFE in e.kategorien)
        val oak = Rss.eingebaut(deutsch = false).first { it.schluessel == "brief-oaktree" }
        val memos = """<time class="embedded-date d-block" datetime=">2026-09-22T07:00:00.0000000Z" pubdate title="x">Sep 22, 2026</time> <a class="oc-title-link" href="/insights/memo/shall-we">Shall We Repeal the Laws of Economics</a>"""
        val m = Briefquellen.lesen(memos, oak, deutsch = false).single()
        assertEquals("https://www.oaktreecapital.com/insights/memo/shall-we", m.link)
        assertEquals("Memo: Shall We Repeal the Laws of Economics", m.titel)
        assertEquals(ms("2026-09-22") + 12 * 3_600_000L, m.zeit)
        assertEquals("A Catalyst for the AI Bubble Break", Briefquellen.titelAusTeil("a-catalyst-for-the-ai-bubble-break"))
        assertEquals("36th Annual Graham & Dodd", Briefquellen.dekodieren("36th%20Annual%20Graham%20%26%20Dodd"))
    }

    @Test
    fun trumpInDenNews() {
        assertEquals(listOf("zoelle"), TruthNews.themen("We will put TARIFFS on everything!", emptyList()))
        assertTrue("geopolitik" in TruthNews.themen("Iran launched missiles at Israel tonight.", emptyList()))
        assertTrue("firma:Nvidia" in TruthNews.themen("Nvidia will build here, no tariffs for them!", listOf("Nvidia")))
        assertTrue(TruthNews.themen("Happy Birthday to a great friend!", listOf("Nvidia")).isEmpty())
        // Nur ein schwaches Thema oder eine Wahlempfehlung mit den ueblichen Punkten: nicht wichtig
        assertTrue(TruthNews.themen("The Radical Left attacks me every day.", emptyList()).isEmpty())
        assertTrue(TruthNews.themen("He has my Complete and Total Endorsement: Strong Military, Lower Taxes, Growing Economy!", emptyList()).isEmpty())
        assertEquals("This is the first sentence of the post.", TruthNews.ueberschrift("This is the first sentence of the post. And more text."))
        // Unter Alle nur wichtige Beitraege, keine Briefe und Blogs. Im Reiter Trump alle
        val q = Rss.eingebaut(deutsch = true)
        val wichtig = Rss.Eintrag("Zoelle", "https://a/1", 1L, kategorien = setOf(Rss.Kategorie.TRUMP), quellen = setOf("truth"), themen = listOf("zoelle"))
        val privat = Rss.Eintrag("Geburtstag", "https://a/2", 1L, kategorien = setOf(Rss.Kategorie.TRUMP), quellen = setOf("truth"))
        val brief = Rss.Eintrag("Brief", "https://a/3", 0L, kategorien = setOf(Rss.Kategorie.BRIEFE), quellen = setOf("brief-berkshire"))
        val alle = listOf(wichtig, privat, brief)
        assertEquals(listOf(wichtig), Rss.filter(alle, Rss.Anbieter.ALLE, q))
        assertEquals(listOf(wichtig, privat), Rss.filter(alle, Rss.Anbieter.TRUMP, q))
        assertEquals(listOf(brief), Rss.filter(alle, Rss.Anbieter.BRIEFE, q))
        assertTrue(Rss.hierLesbar(q.first { it.schluessel == "marketwatch" }))
    }

    @Test
    fun fotosMitZusatzUndDoppelnamen() {
        assertEquals("McConnell", Personenbilder.ohneZusatz("McConnell, Jr."))
        assertEquals("Justice", Personenbilder.ohneZusatz("Justice, II"))
        assertEquals("Hagerty", Personenbilder.ohneZusatz("Hagerty, IV"))
        val b = Personenbilder(
            kongressFoto = "https://bilder/{id}.jpg",
            haus = mapOf("MD06|mcclaindelaney" to "M001232"),
            senat = mapOf("husted|jon" to "H001104"),
        )
        val f = b.haus("Delaney", "MD06", "April McClain")
        assertEquals("https://bilder/M001232.jpg", f?.url)
        assertEquals("https://bioguide.congress.gov/photo/M001232.jpg", f?.ersatz)
        assertEquals("https://bilder/H001104.jpg", b.senator("Husted", "Jon A")?.url)
    }

    @Test
    fun depotCheckFuerPersonen() {
        val werte = listOf(
            DepotCheck.Wert("Apple", 50.0, DepotCheck.Art.AKTIE, "USD", "Technik", 10.0),
            DepotCheck.Wert("Apple (Optionen)", 30.0, DepotCheck.Art.DERIVAT, "USD", null, 0.0),
            DepotCheck.Wert("Microsoft", 30.0, DepotCheck.Art.AKTIE, "USD", "Technik", 5.0),
        )
        val b = DepotCheck.pruefen(werte, 0.0, 0.0, 0.0, 0, 0, heim = "USD", mitKonto = false).associateBy { it.regel }
        assertEquals(DepotCheck.Stufe.GUT, b.getValue(DepotCheck.Regel.WAEHRUNG).stufe)
        assertEquals(DepotCheck.Stufe.KRITISCH, b.getValue(DepotCheck.Regel.HEBEL).stufe)
        assertFalse(DepotCheck.Regel.RESERVE in b)
        assertFalse(DepotCheck.Regel.SPARPLAN in b)
        // Eigenes Depot: Euro als Heimwaehrung, mit Reserve
        val eigen = DepotCheck.pruefen(werte, 10.0, 0.0, 0.0, 0, 0).associateBy { it.regel }
        assertEquals(DepotCheck.Stufe.ACHTUNG, eigen.getValue(DepotCheck.Regel.WAEHRUNG).stufe)
        assertTrue(DepotCheck.Regel.RESERVE in eigen)
    }

    @Test
    fun optionsscheinZuEinerGemeldetenOption() {
        val d = Derivatives.warrantFuer("BE", "Bloom Energy", 80.0, "USD", 0.6, call = true, strike = 100.0, verfallTag = ms("2027-06-17"))
        assertEquals(DerivativeKind.CALL, d.kind)
        assertEquals(100.0, d.strike, 1e-9)
        assertEquals("BE", d.underlying)
        assertEquals(LocalDate.parse("2027-06-17"), java.time.Instant.ofEpochMilli(d.expiry).atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDate())
        // Gleiche Option, gleiche Kennung, damit Kauf und spaeterer Verkauf dasselbe Produkt treffen
        assertEquals(d.id, Derivatives.warrantFuer("BE", "Bloom Energy", 85.0, "USD", 0.5, call = true, strike = 100.0, verfallTag = ms("2027-06-17")).id)
    }
}
