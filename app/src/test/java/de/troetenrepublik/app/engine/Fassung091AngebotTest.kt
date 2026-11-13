package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.IpoTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Angebot laut Prospekt (Abschnitt „The Offering“): angebotene Aktien, Aufteilung, Mehrzuteilung, Aktien danach,
// Gattungen mit Stimmen und die Freigabe mit dem Ende der Sperrfrist. Die Ausschnitte sind selbst im Stil solcher
// Prospekte formuliert, Firmen und Zahlen sind erfunden.
class Fassung091AngebotTest {

    private fun zeile(label: String, wert: String) = "<tr><td><p>$label</p></td><td>&#160;</td><td><p>$wert</p></td></tr>"

    private fun seite(vararg zeilen: String, kopf: String = "<p><b>The Offering</b></p>") =
        "<html><body><p>Table of Contents</p>$kopf<table>" + zeilen.joinToString("") + "</table><p>Risk Factors</p></body></html>"

    @Test
    fun eineGattungMitAufteilungUndMehrzuteilung() {
        val html = seite(
            zeile("Issuer", "Acme Robotics, Inc."),
            zeile("Common stock offered by us", "10,000,000 shares"),
            zeile("Common stock offered by the selling stockholders", "5,000,000 shares"),
            zeile(
                "Option to purchase additional shares",
                "We have granted the underwriters a 30-day option to purchase up to 2,250,000 additional shares of common stock from us.",
            ),
            zeile("Common stock outstanding before this offering", "80,000,000 shares"),
            zeile("Common stock to be outstanding after this offering", "90,000,000 shares"),
            zeile("Use of proceeds", "We estimate net proceeds of approximately \$ 150.0 million."),
        )
        val a = IpoProspekt.angebot(html)!!
        assertEquals(15_000_000L, a.angeboten)
        assertEquals(10_000_000L, a.neu)
        assertEquals(5_000_000L, a.verkauf)
        assertEquals(2_250_000L, a.mehrzuteilung)
        assertEquals(90_000_000L, a.danach)
        assertEquals(100.0 / 6.0, a.frei!!, 1e-9)
        assertTrue(a.gattungen.isEmpty())
        assertNull(a.stimmenAnteil)
        // Im Gesamtergebnis
        val aus = IpoProspekt.lesen(html)
        assertEquals(a, aus.angebot)
        assertFalse(aus.leer)
    }

    @Test
    fun nurNeueAktienOhneZeileZuAltaktionaeren() {
        val a = IpoProspekt.angebot(
            seite(
                zeile("Common stock offered by us", "8,000,000 shares"),
                zeile("Common stock to be outstanding after this offering", "40,000,000 shares"),
            ),
        )!!
        assertEquals(8_000_000L, a.angeboten)
        assertEquals(8_000_000L, a.neu)
        assertEquals(0L, a.verkauf)
        assertNull(a.mehrzuteilung)
        assertEquals(20.0, a.frei!!, 1e-9)
    }

    @Test
    fun nurVerkaufDurchAltaktionaere() {
        val a = IpoProspekt.angebot(
            seite(
                zeile("Common stock offered by the selling stockholders", "12,000,000 shares"),
                zeile("Total shares offered", "12,000,000 shares"),
                zeile("Common stock to be outstanding after this offering", "96,000,000 shares"),
            ),
        )!!
        assertEquals(12_000_000L, a.angeboten)
        assertEquals(0L, a.neu)
        assertEquals(12_000_000L, a.verkauf)
        assertEquals(12.5, a.frei!!, 1e-9)
    }

    @Test
    fun zweiGattungenMitStimmen() {
        val a = IpoProspekt.angebot(
            seite(
                zeile("Class A common stock offered by us", "20,000,000 shares"),
                zeile("Class A common stock to be outstanding after this offering", "50,000,000 shares"),
                zeile("Class B common stock to be outstanding after this offering", "150,000,000 shares"),
                zeile(
                    "Voting rights",
                    "Holders of Class A common stock are entitled to one vote per share and holders of Class B common stock are entitled to ten votes per share.",
                ),
            ),
        )!!
        assertEquals(200_000_000L, a.danach)
        assertEquals(10.0, a.frei!!, 1e-9)
        assertEquals(listOf("A", "B"), a.gattungen.map { it.name })
        assertEquals(listOf(50_000_000L, 150_000_000L), a.gattungen.map { it.aktien })
        assertEquals(listOf<Int?>(1, 10), a.gattungen.map { it.stimmen })
        // 20 Mio. Stimmen von 50 Mio. + 1.500 Mio.
        assertEquals(20.0 / 1550.0 * 100.0, a.stimmenAnteil!!, 1e-9)
    }

    @Test
    fun gattungenInEinerZeile() {
        val a = IpoProspekt.angebot(
            seite(
                zeile("Common stock offered by us", "10,000,000 shares of Class A common stock"),
                zeile(
                    "Common stock to be outstanding after this offering",
                    "40,000,000 shares of Class A common stock and 60,000,000 shares of Class B common stock",
                ),
            ),
        )!!
        assertEquals(100_000_000L, a.danach)
        assertEquals(2, a.gattungen.size)
        assertEquals(10.0, a.frei!!, 1e-9)
        // Ohne Stimmen je Aktie keine Angabe zu den Stimmen
        assertTrue(a.gattungen.all { it.stimmen == null })
        assertNull(a.stimmenAnteil)
    }

    @Test
    fun millionenSchreibweise() {
        val a = IpoProspekt.angebot(
            seite(
                zeile("Ordinary shares offered by us", "5.0 million shares"),
                zeile("Ordinary shares to be outstanding after this offering", "40.0 million shares"),
                zeile("Net proceeds", "approximately \$ 62.5 million"),
            ),
        )!!
        assertEquals(5_000_000L, a.angeboten)
        assertEquals(40_000_000L, a.danach)
        assertEquals(12.5, a.frei!!, 1e-9)
    }

    @Test
    fun depositaryZeilenZaehlenNicht() {
        // Angebot in ADSs: Verhaeltnis zu den Aktien ist unbekannt
        val html = seite(
            zeile("ADSs offered by us", "10,000,000 ADSs"),
            zeile("Ordinary shares to be outstanding after this offering", "120,000,000 ordinary shares"),
        )
        val a = IpoProspekt.angebot(html)!!
        assertNull(a.angeboten)
        assertNull(a.frei)
        assertEquals(120_000_000L, a.danach)
        assertNull(IpoProspekt.angebot(seite(zeile("ADSs offered by us", "10,000,000 ADSs"))))
    }

    @Test
    fun uneinigeSummeLaesstDieAufteilungWeg() {
        val a = IpoProspekt.angebot(
            seite(
                zeile("Common stock offered by us", "10,000,000 shares"),
                zeile("Common stock offered by the selling stockholders", "5,000,000 shares"),
                zeile("Total shares offered", "16,000,000 shares"),
                zeile("Common stock to be outstanding after this offering", "64,000,000 shares"),
            ),
        )!!
        assertEquals(16_000_000L, a.angeboten)
        assertNull(a.neu)
        assertNull(a.verkauf)
        assertEquals(25.0, a.frei!!, 1e-9)
    }

    @Test
    fun ohneUeberschriftUndAlsTextzeilen() {
        // Tabelle ohne Ueberschrift: gefunden ueber „offered by us“
        val ohne = "<html><body><table>" +
            zeile("Common stock offered by us", "6,000,000 shares") +
            zeile("Common stock to be outstanding after this offering", "30,000,000 shares") +
            "</table></body></html>"
        assertEquals(20.0, IpoProspekt.angebot(ohne)!!.frei!!, 1e-9)
        // Absaetze statt Tabelle
        val text = "<html><body><p>The Offering</p><p>Common stock offered by us 10,000,000 shares</p>" +
            "<p>Common stock to be outstanding after this offering 60,000,000 shares</p><p>Risk Factors</p></body></html>"
        val a = IpoProspekt.angebot(text)!!
        assertEquals(10_000_000L, a.angeboten)
        assertEquals(60_000_000L, a.danach)
        // Inhaltsverzeichnis mit derselben Ueberschrift davor stoert nicht
        val mitInhalt = "<html><body><table><tr><td><a href=\"#o\">The Offering</a></td><td>12</td></tr></table>" +
            seite(zeile("Common stock offered by us", "1,000,000 shares"), zeile("Common stock to be outstanding after this offering", "5,000,000 shares")).substringAfter("<body>")
        assertEquals(20.0, IpoProspekt.angebot(mitInhalt)!!.frei!!, 1e-9)
    }

    @Test
    fun fehlendesBleibtLeer() {
        assertNull(IpoProspekt.angebot(""))
        assertNull(IpoProspekt.angebot(seite(zeile("Issuer", "Acme"), zeile("Use of proceeds", "General corporate purposes"))))
        assertTrue(IpoProspekt.lesen("").leer)
        // Aktien vor dem Angebot zaehlen nicht als Aktien danach
        val nurVorher = IpoProspekt.angebot(seite(zeile("Common stock outstanding before this offering", "80,000,000 shares")))
        assertNull(nurVorher)
        // Mehr Aktien angeboten als danach da sind: kein Anteil
        val unsinn = IpoProspekt.angebot(seite(zeile("Common stock offered by us", "50,000,000 shares"), zeile("Common stock to be outstanding after this offering", "10,000,000 shares")))!!
        assertNull(unsinn.frei)
    }

    @Test
    fun freigabeMitDemEndeDerSperrfrist() {
        val a = IpoProspekt.Angebot(15_000_000L, 10_000_000L, 5_000_000L, null, 90_000_000L)
        // Bezug auf Aktien vor dem Angebot: 80 Mio. mal 90 %
        assertEquals(72_000_000L, IpoProspekt.freigabe(a, IpoProspekt.Gesperrt(90.0, nachAngebot = false)))
        // Bezug auf alle Aktien danach: 90 Mio. mal 80 %
        assertEquals(72_000_000L, IpoProspekt.freigabe(a, IpoProspekt.Gesperrt(80.0, nachAngebot = true)))
        // Mehr gesperrt als Altaktien da sind
        assertNull(IpoProspekt.freigabe(a, IpoProspekt.Gesperrt(95.0, nachAngebot = true)))
        assertNull(IpoProspekt.freigabe(a, null))
        assertNull(IpoProspekt.freigabe(null, IpoProspekt.Gesperrt(90.0, nachAngebot = true)))
        // Ohne die neuen Aktien keine Basis fuer den Bezug auf die Aktien vorher
        assertNull(IpoProspekt.freigabe(IpoProspekt.Angebot(15_000_000L, null, null, null, 90_000_000L), IpoProspekt.Gesperrt(90.0, nachAngebot = false)))
    }

    @Test
    fun texteInBeidenSprachen() {
        val schluessel = IpoTexte.schluessel(true).filter { it.startsWith("angebot.") || it.startsWith("freigabe") }
        assertNotNull(schluessel.firstOrNull())
        for (k in schluessel) assertTrue(k, k in IpoTexte.schluessel(false))
        assertEquals("Klasse B, 10 Stimmen je Aktie", IpoTexte.text(true, "angebot.klasseMit", listOf("B", IpoTexte.text(true, "angebot.stimmen", listOf("10")))))
        assertEquals("Class A, 1 vote per share", IpoTexte.text(false, "angebot.klasseMit", listOf("A", IpoTexte.text(false, "angebot.stimme1"))))
    }
}
