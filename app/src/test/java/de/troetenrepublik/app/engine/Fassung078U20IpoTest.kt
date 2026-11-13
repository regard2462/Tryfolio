package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.IpoTexte
import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Detailblatt eines Boersengangs (0.78): Kurs anderer Boersengaenge nach dem Start, Anteilseigner, Banken und
// Sperrfrist aus dem Prospekt. Die Prospekte unter resources/ipo sind gekuerzte Ausschnitte oeffentlicher Einreichungen;
// die Namen aller Personen darin sind erfunden.
class Fassung078U20IpoTest {

    private val tagMs = 86_400_000L

    private fun kerze(tag: Long, schluss: Double) = Candle(tag * tagMs + 14 * 3_600_000L, schluss, schluss, schluss, schluss)

    private fun prospekt(name: String): String = Rss.text(javaClass.classLoader!!.getResourceAsStream("ipo/$name.html")!!.readBytes())

    // ------------------------------------------------------------ Kurs nach dem Start

    @Test
    fun kursNachDerFrist() {
        val g = IpoBlatt.Gestartet(1000L, 20.0, listOf(kerze(1000, 24.0), kerze(1029, 30.0), kerze(1031, 18.0), kerze(1182, 10.0)))
        // erster Kurs am oder nach dem Tag der Frist
        assertEquals(-10.0, IpoBlatt.nachStart(g, 30, 2000L)!!, 1e-9)
        assertEquals(-50.0, IpoBlatt.nachStart(g, 182, 2000L)!!, 1e-9)
        // Frist noch nicht um
        assertNull(IpoBlatt.nachStart(g, 30, 1029L))
        assertNotNull(IpoBlatt.nachStart(g, 30, 1030L))
        // kein Kurs in der Woche nach der Frist
        assertNull(IpoBlatt.nachStart(g, 365, 2000L))
    }

    @Test
    fun kursreihenDieNichtPassenFallenWeg() {
        // Der erste Handelstag fehlt: anderes Papier unter dem Kuerzel
        assertNull(IpoBlatt.nachStart(IpoBlatt.Gestartet(1000L, 20.0, listOf(kerze(1004, 21.0), kerze(1030, 22.0))), 30, 2000L))
        // Kursreihe nach einer Zusammenlegung umgerechnet: erster Kurs beim Zehnfachen des Ausgabepreises
        assertNull(IpoBlatt.nachStart(IpoBlatt.Gestartet(1000L, 5.0, listOf(kerze(1000, 50.0), kerze(1030, 20.0))), 30, 2000L))
        assertNull(IpoBlatt.nachStart(IpoBlatt.Gestartet(1000L, 0.0, listOf(kerze(1000, 5.0), kerze(1030, 6.0))), 30, 2000L))
        assertNull(IpoBlatt.nachStart(IpoBlatt.Gestartet(1000L, 20.0, emptyList()), 30, 2000L))
    }

    @Test
    fun fristenMitFallzahl() {
        // Zwoelf Boersengaenge zu 10: nach einem Monat stehen acht unter dem Ausgabepreis
        val liste = (0 until 12).map { i ->
            val nach30 = if (i < 8) 9.0 - i * 0.5 else 11.0 + i
            IpoBlatt.Gestartet(1000L + i, 10.0, listOf(kerze(1000L + i, 10.5), kerze(1030L + i, nach30)))
        }
        val f = IpoBlatt.verlauf(liste, 1100L)
        assertEquals(listOf(30), f.map { it.tage })
        assertEquals(12, f[0].anzahl)
        assertEquals(8, f[0].unter)
        assertEquals(8.0 / 12.0, f[0].anteilUnter, 1e-9)
        // Median der zwoelf Werte: zwischen -10 % (9,0) und +190 % liegt die Mitte bei den Werten 6 und 7 der Reihe
        val werte = liste.map { IpoBlatt.nachStart(it, 30, 1100L)!! }
        assertEquals(IpoBlatt.median(werte), f[0].median, 1e-9)
        assertTrue(f[0].median < 0)
        // Zu wenige Faelle: keine Frist
        assertTrue(IpoBlatt.verlauf(liste.take(IpoBlatt.MIN_VERLAUF - 1), 1100L).isEmpty())
        // genau am Ausgabepreis zaehlt nicht als darunter
        val gleich = (0 until 10).map { IpoBlatt.Gestartet(1000L, 10.0, listOf(kerze(1000, 10.0), kerze(1030, 10.0))) }
        assertEquals(0, IpoBlatt.verlauf(gleich, 1100L)[0].unter)
    }

    @Test
    fun rueckblickUndForschung() {
        val monate = IpoUebersicht.rueckblickMonate(LocalDate.of(2026, 11, 12))
        assertEquals(IpoBlatt.RUECKBLICK_MONATE - 2, monate.size)
        assertEquals(LocalDate.of(2026, 8, 12), monate.first())
        assertEquals(LocalDate.of(2024, 11, 12), monate.last())
        // keine Ueberschneidung mit den Monaten des Kalenders
        assertTrue(monate.none { m -> IpoUebersicht.abrufMonate(LocalDate.of(2026, 11, 12)).any { it.year == m.year && it.month == m.month } })
        // Tabelle 16e, Teil A: 35,0 % und 21,1 % von 9195
        val f = IpoForschung.dreiJahre
        assertEquals(5154, f.unter)
        assertEquals(0.5605, f.anteilUnter, 0.0001)
        assertEquals(35.0, 3215 * 100.0 / IpoForschung.DREI_ANZAHL, 0.05)
        assertEquals(21.1, 1939 * 100.0 / IpoForschung.DREI_ANZAHL, 0.05)
        assertEquals(-5.8, IpoForschung.JAHR_IPO - IpoForschung.JAHR_VERGLEICH, 1e-9)
        assertTrue(IpoForschung.LINK.startsWith("https://site.warrington.ufl.edu/ritter/"))
        assertNotNull(LocalDate.parse(IpoForschung.STAND))
    }

    // ------------------------------------------------------------ Prospekt

    @Test
    fun eineGattung() {
        val a = IpoProspekt.lesen(prospekt("prospekt-eine-gattung"))
        val e = a.eigner!!
        assertFalse(e.stimmrechte)
        assertEquals(
            listOf(
                "OrbiMed Private Investments VII, LP" to 13.1, "ARCH Venture Fund X, L.P." to 12.3, "Gilead Sciences, Inc." to 8.4,
                "Frazier Life Sciences X, L.P." to 6.8, "Q Healthcare Holding LLC" to 6.8, "Abu Dhabi Investment Authority" to 6.6,
                "Medical Excellence Capital Partners, LP" to 5.5,
            ),
            e.halter.filter { it.gesellschaft }.map { it.name to it.anteil },
        )
        // Personen mit Anteil ab 1 %; der Aufsichtsrat mit den Aktien seines Fonds steht nicht noch einmal da
        assertEquals(listOf("Elliot Specimen" to 4.2, "Alex Example" to 1.9), e.halter.filter { !it.gesellschaft }.map { it.name to it.anteil })
        assertEquals(21.2, e.gruppe!!, 1e-9)
        assertEquals(e.halter.sortedByDescending { it.anteil }.map { it.anteil }, e.halter.map { it.anteil })
        assertEquals(
            listOf("Leerink Partners LLC", "Piper Sandler & Co.", "Stifel, Nicolaus & Company, Incorporated", "UBS Securities LLC", "Oppenheimer & Co. Inc."),
            a.banken.map { it.name },
        )
        assertEquals(3_281_250L, a.banken.first().aktien)
        assertEquals(9_375_000L, a.banken.sumOf { it.aktien!! })
        assertEquals(180, a.sperrfristTage)
    }

    @Test
    fun vorstandVorDenGrossaktionaeren() {
        val a = IpoProspekt.lesen(prospekt("prospekt-vorstand-zuerst"))
        val e = a.eigner!!
        assertEquals("Versant Ventures" to 18.9, e.halter.first().let { it.name to it.anteil })
        assertTrue(e.halter.first().gesellschaft)
        assertEquals(
            listOf(18.9, 12.8, 11.1, 9.7, 9.3, 5.3, 5.2, 1.3, 1.2),
            e.halter.map { it.anteil },
        )
        assertEquals("Redmile Biopharma Investments III, L.P.", e.halter[2].name)
        assertEquals(10.3, e.gruppe!!, 1e-9)
        assertEquals("Jefferies LLC", a.banken.first().name)
        assertEquals(5, a.banken.size)
        assertEquals(180, a.sperrfristTage)
    }

    @Test
    fun vorlaeufigerProspektOhneZahlenNachDemAngebot() {
        val a = IpoProspekt.lesen(prospekt("prospekt-f1-vorlaeufig"))
        val e = a.eigner!!
        assertEquals(
            listOf("Novo Holdings A/S" to 14.5, "OrbiMed Private Investments IX, LP" to 14.3, "Kai Anyone" to 10.0, "SR One" to 9.5, "Omega Funds" to 8.9),
            e.halter.take(5).map { it.name to it.anteil },
        )
        // Jeder Name einmal, Fondsvertreter im Aufsichtsrat nicht doppelt
        assertEquals(e.halter.size, e.halter.map { it.name }.distinct().size)
        assertTrue(e.halter.none { it.name == "Parker P. Attrappe" || it.name == "Taylor Namenlos" || it.name == "Quinn Entwurf" })
        assertEquals(46.6, e.gruppe!!, 1e-9)
        // Banken stehen fest, die Zuteilung noch nicht
        assertEquals(listOf("Jefferies LLC", "TD Securities (USA) LLC", "Piper Sandler & Co.", "UBS Securities LLC"), a.banken.map { it.name })
        assertTrue(a.banken.all { it.aktien == null })
        assertEquals(180, a.sperrfristTage)
    }

    @Test
    fun zweiGattungenMitAbgebendenAktionaeren() {
        val a = IpoProspekt.lesen(prospekt("prospekt-zwei-gattungen"))
        val e = a.eigner!!
        // Anteil an allen Aktien vor dem Angebot, nicht der an einer Gattung und nicht der nach dem Angebot
        assertFalse(e.stimmrechte)
        assertEquals(
            listOf("Advance Magazine Publishers Inc." to 29.4, "Tencent" to 10.7, "Blake Beispiel" to 8.5, "FMR LLC" to 8.2, "Quiet Capital and Tacit Capital" to 6.7, "Vy Capital" to 5.0),
            e.halter.filter { it.gesellschaft }.map { it.name to it.anteil },
        )
        assertEquals(listOf("Kai Modell" to 3.2, "Logan Vorlage" to 1.5), e.halter.filter { !it.gesellschaft }.map { it.name to it.anteil })
        assertEquals(44.8, e.gruppe!!, 1e-9)
        assertTrue(a.banken.size >= 4)
        assertTrue(a.banken.all { it.aktien != null && it.aktien!! > 0 })
        // Die Sperrfrist haengt an einer Bedingung („earlier of“): nicht sicher lesbar
        assertNull(a.sperrfristTage)
    }

    @Test
    fun ohneTabelleNichts() {
        val a = IpoProspekt.lesen("<html><body><p>PRINCIPAL STOCKHOLDERS</p><p>The table follows in a later amendment.</p><table><tr><td>Item</td><td>Amount</td></tr><tr><td>Fees</td><td>1,000</td></tr></table></body></html>")
        assertTrue(a.leer)
        assertTrue(IpoProspekt.lesen("").leer)
        // Tabelle ohne Prozentangaben ist keine Eignertabelle
        val ohneProzent = "<p>beneficial ownership</p><table><tr><td>Name</td><td>Shares</td></tr><tr><td>Alpha Fund LP</td><td>1,000</td></tr>" +
            "<tr><td>Beta Fund LP</td><td>2,000</td></tr><tr><td>All directors as a group (2 persons)</td><td>3,000</td></tr></table>"
        assertNull(IpoProspekt.eigner(ohneProzent))
    }

    @Test
    fun einfacheTabelleOhneGruppenzeile() {
        val html = "<p><b>PRINCIPAL SHAREHOLDERS</b></p><p>beneficial ownership before this offering</p>" +
            "<table><tr><td>Name of Beneficial Owner</td><td>Shares</td><td>Percent Before Offering</td><td>Percent After Offering</td></tr>" +
            "<tr><td>5% Shareholders:</td><td></td><td></td><td></td></tr>" +
            "<tr><td>Entities affiliated with Alpha Capital(1)</td><td>4,000,000</td><td>40.0%</td><td>30.0%</td></tr>" +
            "<tr><td>Beta Holdings Ltd.&nbsp;(2)</td><td>2,500,000</td><td>25.0</td><td>18.8</td></tr>" +
            "<tr><td>Jane Doe</td><td>50,000</td><td>*</td><td>*</td></tr>" +
            "<tr><td>John Roe (3)</td><td>&#8212;</td><td>&#8212;</td><td>&#8212;</td></tr></table>"
        val e = IpoProspekt.eigner(html)!!
        assertEquals(listOf("Alpha Capital" to 40.0, "Beta Holdings Ltd." to 25.0), e.halter.map { it.name to it.anteil })
        assertTrue(e.halter.all { it.gesellschaft })
        assertNull(e.gruppe)
    }

    @Test
    fun nurStimmrechte() {
        val html = "<p>beneficial ownership</p><table>" +
            "<tr><td></td><td colspan=\"2\">Class A</td><td colspan=\"2\">Class B</td><td>% of Voting Power Before the Offering</td><td>% of Voting Power After the Offering</td></tr>" +
            "<tr><td>Name</td><td>Shares</td><td>%</td><td>Shares</td><td>%</td><td></td><td></td></tr>" +
            "<tr><td>Gamma Ventures LLC</td><td>1,000,000</td><td>10.0%</td><td>5,000,000</td><td>50.0%</td><td>48.1%</td><td>45.0%</td></tr>" +
            "<tr><td>Delta Trust</td><td>3,000,000</td><td>30.0%</td><td>-</td><td>-</td><td>2.9%</td><td>2.7%</td></tr>" +
            "<tr><td>All executive officers and directors as a group (5 persons)</td><td>1,000,000</td><td>10.0%</td><td>5,000,000</td><td>50.0%</td><td>48.1%</td><td>45.0%</td></tr></table>"
        val e = IpoProspekt.eigner(html)!!
        assertTrue(e.stimmrechte)
        assertEquals(listOf("Gamma Ventures LLC" to 48.1, "Delta Trust" to 2.9), e.halter.map { it.name to it.anteil })
        assertEquals(48.1, e.gruppe!!, 1e-9)
    }

    @Test
    fun sperrfristNurWennEindeutig() {
        fun satz(t: String) = "<p>Our officers and directors have entered into lock-up agreements and agreed not to sell shares for $t days after the date of this prospectus.</p>"
        assertEquals(180, IpoProspekt.sperrfrist(satz("180")))
        assertEquals(90, IpoProspekt.sperrfrist(satz("90") + satz("90")))
        // zwei verschiedene Fristen
        assertNull(IpoProspekt.sperrfrist(satz("180") + satz("365")))
        // ohne Bezug zur Sperrfrist
        assertNull(IpoProspekt.sperrfrist("<p>We will file a report within 25 days after the date of this prospectus.</p>"))
        assertNull(IpoProspekt.sperrfrist("<p>The lock-up ends on the earlier of the second trading day after our earnings release and 180 days after the date of this prospectus.</p>"))
        // Leerraum als Entitaet
        assertEquals(180, IpoProspekt.sperrfrist("<p>lock-up period of 180&nbsp;days after the date of this&#160;prospectus</p>"))
    }

    @Test
    fun prospektWahlUndFirmensuche() {
        fun d(f: String, tag: String, datei: String = "p.htm") = IpoKalender.Dokument(f, tag, "https://www.sec.gov/Archives/edgar/data/1/$tag/$datei")
        // der endgueltige Prospekt des Boersengangs, nicht der eines spaeteren Angebots
        val liste = listOf(d("424B4", "2026-09-01"), d("S-1", "2026-08-20"), d("424B4", "2025-03-21"), d("S-1/A", "2025-03-15"), d("S-1", "2025-02-22"))
        assertEquals("2025-03-21", IpoProspekt.wahl(liste)!!.tag)
        // vor der Preisfestsetzung der neueste eingereichte
        assertEquals("2025-03-15", IpoProspekt.wahl(listOf(d("EFFECT", "2025-03-20"), d("S-1/A", "2025-03-15"), d("S-1", "2025-02-22")))!!.tag)
        assertNull(IpoProspekt.wahl(listOf(d("S-1", "2025-02-22", "p.pdf"), d("RW", "2025-03-01"))))
        assertNull(IpoProspekt.wahl(emptyList()))

        val ein = "<feed><company-info><cik>0002124080</cik><conformed-name>Acme Robotics, Inc.</conformed-name></company-info></feed>"
        assertEquals("2124080", IpoKalender.cikAusSuche(ein) { it == "Acme Robotics, Inc." })
        assertNull(IpoKalender.cikAusSuche(ein) { false })
        // mehrere Treffer: die Antwort nennt keine Namen
        val mehrere = "<feed><entry><company-info name=\"x\"><cik>0000000011</cik></company-info></entry><entry><company-info name=\"y\"><cik>0000000012</cik></company-info></entry></feed>"
        assertNull(IpoKalender.cikAusSuche(mehrere) { true })
        assertTrue(IpoKalender.firmenSuche("Acme & Co").contains("company=Acme+%26+Co&"))
    }

    @Test
    fun texteZuDenFristen() {
        for (d in listOf(true, false)) {
            for (t in IpoBlatt.FRISTEN + IpoForschung.dreiJahre.tage) assertTrue("verlauf.$t", IpoTexte.text(d, "verlauf.$t").isNotBlank())
        }
        assertEquals("Erster Handelstag anderer Börsengänge", IpoTexte.text(true, "erste.titel"))
        assertEquals("Median -3,2 % · 212 Börsengänge", IpoTexte.text(true, "verlauf.zusatz", listOf("-3,2 %", IpoTexte.text(true, "verlauf.anzahl", listOf("212")))))
    }
}
