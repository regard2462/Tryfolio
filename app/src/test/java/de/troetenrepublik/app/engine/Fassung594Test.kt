package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.GleicheFirma.Eintrag
import de.troetenrepublik.app.market.Zweitnotiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung594GleicheFirmaTest {
    @Test
    fun zweiNotierungenEinerFirma() {
        // Heimat und US-Notierung mit bekanntem Land
        assertTrue(GleicheFirma.gleich(Eintrag("000660.KS", "SK Hynix", "KR", 610.0), Eintrag("SKHY", "SK hynix Inc. - American Depositary Shares", "KR", 150.0)))
        // ADR ohne Katalogland: gleicher Name und fast gleicher Euro-Kurs
        assertTrue(GleicheFirma.gleich(Eintrag("NOVO-B.CO", "Novo Nordisk (B)", "DK", 41.2), Eintrag("NVO", "Novo Nordisk A/S", null, 41.9)))
        // Gattungen
        assertTrue(GleicheFirma.gleich(Eintrag("GOOGL", "Alphabet (A)", "US", 250.0), Eintrag("GOOG", "Alphabet Inc. Class C", "US", 251.0)))
    }

    @Test
    fun gleichnamigeAndereBleiben() {
        // Anderer Name
        assertFalse(GleicheFirma.gleich(Eintrag("NOVO-B.CO", "Novo Nordisk (B)", "DK", 41.0), Eintrag("NSIS-B.CO", "Novonesis (B)", "DK", 41.0)))
        // Gleicher Name, verschiedene Laender
        assertFalse(GleicheFirma.gleich(Eintrag("CON.DE", "Continental", "DE", 70.0), Eintrag("CTL", "Continental", "US", 70.0)))
        // Gleicher Name, Land unbekannt, Kurs weit auseinander
        assertFalse(GleicheFirma.gleich(Eintrag("ABC.L", "Atlas", null, 12.0), Eintrag("ATL", "Atlas", null, 30.0)))
        // Ohne Kurs und ohne Land keine Vermutung
        assertFalse(GleicheFirma.gleich(Eintrag("X1", "Atlas", null, null), Eintrag("X2", "Atlas", null, null)))
    }

    @Test
    fun gruppenUndHeimat() {
        val l = listOf(
            Eintrag("SKHY", "SK hynix", "KR", 150.0),
            Eintrag("AAPL", "Apple", "US", 230.0),
            Eintrag("000660.KS", "SK Hynix", "KR", 610.0),
        )
        assertEquals(listOf(listOf(0, 2), listOf(1)), GleicheFirma.gruppen(l))
        assertFalse(GleicheFirma.heimat(l[0]))
        assertTrue(GleicheFirma.heimat(l[2]))
    }
}

class Fassung594LeerUndUsTest {
    @Test
    fun leerStufen() {
        assertEquals(0, Bewertungsblick.leerStufe(2.0, true))
        assertEquals(2, Bewertungsblick.leerStufe(12.0, true))
        assertEquals(3, Bewertungsblick.leerStufe(25.0, true))
        assertEquals(1, Bewertungsblick.leerStufe(1.5, false))
        assertEquals(3, Bewertungsblick.leerStufe(6.0, false))
    }

    @Test
    fun usNotierungNachName() {
        val k = listOf(
            Zweitnotiz.Kandidat("SAP.DE", "GER", 230.0, "SAP SE"),
            Zweitnotiz.Kandidat("SAPGF", "PNK", 230.0, "SAP SE"),
            Zweitnotiz.Kandidat("SAP", "NYQ", 270.0, "SAP SE"),
        )
        assertEquals("SAP", Zweitnotiz.usNotierung("SAP", k))
        // Fremde Firma mit aehnlichem Anfang zaehlt nicht
        assertNull(Zweitnotiz.usNotierung("Siemens", listOf(Zweitnotiz.Kandidat("SIEB", "NMS", 3.0, "Siebert Financial Corp."))))
    }
}

class Fassung594ErsatzPreisTest {
    @Test
    fun monatsVorjahr() {
        val t = { j: Int, m: Int -> java.time.LocalDate.of(j, m, 1).toEpochDay() }
        val r = Eingepreist.monatsVorjahr(listOf(t(2024, 1) to 100.0, t(2024, 2) to 50.0, t(2025, 1) to 110.0, t(2025, 3) to 70.0))
        assertEquals(1, r.size)
        assertEquals(10.0, r[0].second, 1e-9)
    }

    @Test
    fun spaeteMonatsdatenMitAbstand() {
        // Signal zwei Monate alt, Markt aktuell: mit dem Abstand der Ersatzquellen gibt es eine Schaetzung
        val x = (0 until 200).map { it * 30L to (it % 7).toDouble() }
        val y = (0 until 1206).map { it * 5L to ((it * 5L / 30) % 7).toDouble() * 2 + (it % 3) * 0.1 }
        assertNull(Fruehindikatoren.einordnungAusReihen(x, y, 1, saison = false))
        assertTrue(Fruehindikatoren.einordnungAusReihen(x, y, 1, saison = false, abstand = Fruehindikatoren.ERSATZ_ABSTAND) != null)
    }
}

class Fassung594WsbTest {
    private fun w(t: String, p: Double, k: Int) = Stimmungsbarometer.WsbWert(t, p > 0, p, k)

    @Test
    fun bereinigt() {
        val namen = mapOf("GOOG" to "Alphabet Inc. Class C", "GOOGL" to "Alphabet (A)", "NVDA" to "NVIDIA", "FOX" to "Fox Corp (B)")
        val r = Stimmungsbarometer.wsbBereinigt(
            listOf(w("AI", -0.03, 126), w("NVDA", 0.27, 13), w("GOOG", 0.148, 7), w("GOOGL", -0.001, 10), w("FOXA", 0.2, 30)),
        ) { namen[it] }
        // Wort heraus, Alphabet einmal mit gewichtetem Score, FOXA ohne Katalognamen bleibt allein
        assertEquals(listOf("FOXA", "GOOGL", "NVDA"), r.map { it.ticker })
        val g = r.first { it.ticker == "GOOGL" }
        assertEquals(17, g.kommentare)
        assertEquals((7 * 0.148 - 10 * 0.001) / 17, g.punkte, 1e-9)
        assertFalse(Stimmungsbarometer.wsbKlar(g))
        // Unter 10 Kommentaren nie klar
        assertFalse(Stimmungsbarometer.wsbSteigendKlar(w("X", 0.5, 9)))
    }

    @Test
    fun nullIstNeutral() {
        val r = Stimmungsbarometer.wsbLesen("""[{"ticker":"PLTR","sentiment":"Bearish","sentiment_score":0.0,"no_of_comments":40}]""")
        assertFalse(r.single().steigend)
        assertFalse(Stimmungsbarometer.wsbFallendKlar(r.single()))
    }
}

class Fassung594FeedTest {
    @Test
    fun relativeLinks() {
        val xml = "<rss><channel><item><title>Sind Sie ein Leistungsträger?</title><link>/de/diw_01.c.1023905.de/publikationen/a.html</link>" +
            "<pubDate>Mon, 02 Nov 2026 10:00:00 +0200</pubDate></item></channel></rss>"
        val e = de.troetenrepublik.app.market.Rss.lesen(xml, "DIW", de.troetenrepublik.app.market.Rss.Kategorie.entries.first(), basis = "https://www.diw.de/de/rss_publications.xml")
        assertEquals("https://www.diw.de/de/diw_01.c.1023905.de/publikationen/a.html", e.single().link)
        // Absolute Links bleiben
        assertEquals("https://x.org/a", de.troetenrepublik.app.market.Rss.absolut("https://x.org/a", "https://www.diw.de/feed.xml"))
    }
}

class Fassung594BankTest {
    @Test
    fun kbvGegenEigenkapitalrendite() {
        // ROE 14 %: angemessen (14 − 2) / (10 − 2) = 1,5
        val b = Bewertungsblick.bankBewertung(1.2, 14.0)!!
        assertEquals(1.5, b.angemessen!!, 1e-9)
        assertEquals(Bewertungsblick.Ampel.GRUEN, b.ampel)
        assertEquals(Bewertungsblick.Ampel.GELB, Bewertungsblick.bankBewertung(1.8, 14.0)!!.ampel)
        assertEquals(Bewertungsblick.Ampel.ROT, Bewertungsblick.bankBewertung(2.5, 14.0)!!.ampel)
        // Rendite unter dem Wachstum: kein angemessenes KBV
        assertNull(Bewertungsblick.bankBewertung(0.5, 1.0)!!.angemessen)
        assertTrue(Bewertungsblick.bilanzgeschaeft("Banks - Regional"))
        assertTrue(Bewertungsblick.bilanzgeschaeft("Insurance - Life"))
        assertFalse(Bewertungsblick.bilanzgeschaeft("Asset Management"))
    }

    @Test
    fun bankBilanz() {
        assertEquals(Bewertungsblick.Ampel.GRUEN, Bewertungsblick.bankBilanz(1.2, 9.0)!!.ampel)
        assertEquals(Bewertungsblick.Ampel.ROT, Bewertungsblick.bankBilanz(1.2, 4.0)!!.ampel)
        assertEquals(Bewertungsblick.Ampel.GELB, Bewertungsblick.bankBilanz(0.7, null)!!.ampel)
        assertEquals(6.0, Bewertungsblick.ekQuote(100.0, 94.0)!!, 1e-9)
        assertNull(Bewertungsblick.bankBilanz(null, null))
    }
}
