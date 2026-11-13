package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.IpoKalender.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Uebersicht der Boersengaenge (0.77), Namen und Betraege erfunden.
class Fassung077IpoTest {
    private val heute = LocalDate.of(2026, 11, 11).toEpochDay()

    private fun a(
        name: String, status: Status, volumen: Double? = null, preis: String? = null,
        tag: Long? = null, symbol: String? = null,
    ) = IpoKalender.Angebot(name.lowercase(), symbol, name, "NASDAQ", preis, null, volumen, tag, status)

    @Test
    fun suchtextOhneRechtsform() {
        assertEquals("\"Acme\" IPO", IpoUebersicht.suchtext("Acme, PBC"))
        assertEquals("\"Bambusa Therapeutics\" IPO", IpoUebersicht.suchtext("Bambusa Therapeutics, Inc."))
        assertEquals("\"Nordlicht\" IPO", IpoUebersicht.suchtext("Nordlicht Holdings Ltd"))
        assertEquals("Acme", IpoUebersicht.firmenKern("Acme PBC"))
    }

    @Test
    fun namensabgleichSchlagzeile() {
        assertTrue(IpoUebersicht.nameTrifft("Acme sets IPO price range at \$70 to \$78", "Acme, PBC"))
        assertTrue(IpoUebersicht.nameTrifft("Börsengang: ACME peilt Bewertung an", "Acme, PBC"))
        assertFalse(IpoUebersicht.nameTrifft("Acmeville opens new plant", "Acme, PBC"))
        assertFalse(IpoUebersicht.nameTrifft("Tech IPOs return to the market", "Acme, PBC"))
        // zu kurzer Kern ergibt keinen Treffer
        assertFalse(IpoUebersicht.nameTrifft("AB reports", "AB Inc."))
    }

    @Test
    fun juengsteSchlagzeileNurMitFirmenname() {
        val m = listOf(
            de.troetenrepublik.app.engine.LaenderZusatz.Meldung("Acme prices IPO", "https://x.test/1", 100L, "CNBC"),
            de.troetenrepublik.app.engine.LaenderZusatz.Meldung("Acme sets terms", "https://x.test/2", 300L, "UPI"),
            de.troetenrepublik.app.engine.LaenderZusatz.Meldung("Markets rally on IPO news", "https://x.test/3", 900L, "Yahoo"),
            de.troetenrepublik.app.engine.LaenderZusatz.Meldung("Acme without link", "", 800L, "Yahoo"),
        )
        assertEquals("Acme sets terms", IpoUebersicht.juengste(m, "Acme, PBC")!!.titel)
        assertNull(IpoUebersicht.juengste(m, "Zeta Corp"))
        assertNull(IpoUebersicht.juengste(emptyList(), "Acme, PBC"))
    }

    @Test
    fun platzhalterBetrag() {
        assertTrue(IpoUebersicht.platzhalter(a("Acme, PBC", Status.EINGEREICHT, 1_000_000_000.0)))
        assertFalse(IpoUebersicht.platzhalter(a("Acme, PBC", Status.EINGEREICHT, null)))
        assertFalse(IpoUebersicht.platzhalter(a("Acme, PBC", Status.EINGEREICHT, 1_000_000_000.0, preis = "14.00-16.00")))
        assertFalse(IpoUebersicht.platzhalter(a("Acme, PBC", Status.ANGEKUENDIGT, 500_000_000.0, preis = "70.00-78.00")))
    }

    @Test
    fun grosseBoersengaengeOhneMantelUndKleine() {
        val liste = listOf(
            a("Acme, PBC", Status.EINGEREICHT, 1_000_000_000.0),
            a("Beta Robotics Inc.", Status.ANGEKUENDIGT, 600_000_000.0, "20.00-22.00", heute + 3),
            a("Gamma Acquisition Corp.", Status.EINGEREICHT, 2_000_000_000.0),
            a("Delta Bio Inc.", Status.ANGEKUENDIGT, 80_000_000.0, "10.00", heute + 2),
            a("Epsilon Energy Inc.", Status.PREIS, 900_000_000.0, "30.00", heute - 2),
            a("Zeta Labs Inc.", Status.EINGEREICHT, 300_000_000.0),
            a("Eta Mining Inc.", Status.ZURUECK, 700_000_000.0),
        )
        assertEquals(listOf("Acme, PBC", "Beta Robotics Inc.", "Zeta Labs Inc."), IpoUebersicht.grosse(liste).map { it.name })
        assertEquals(listOf("Acme, PBC"), IpoUebersicht.grosse(liste, 1).map { it.name })
    }

    @Test
    fun listeGrossZuerstMantelZuletzt() {
        val l = IpoUebersicht.grossZuerst(
            listOf(
                a("Gamma Acquisition Corp.", Status.EINGEREICHT, 2_000_000_000.0),
                a("Zeta Labs Inc.", Status.EINGEREICHT, 300_000_000.0),
                a("Acme, PBC", Status.EINGEREICHT, 1_000_000_000.0),
            ),
        )
        assertEquals(listOf("Acme, PBC", "Zeta Labs Inc.", "Gamma Acquisition Corp."), l.map { it.name })
    }

    @Test
    fun kopfZahlen() {
        val k = IpoUebersicht.kopf(
            listOf(
                a("A", Status.ANGEKUENDIGT, tag = heute + 1),
                a("B", Status.ANGEKUENDIGT, tag = heute + 5),
                a("C", Status.EINGEREICHT, tag = heute - 3),
                a("D", Status.PREIS, tag = heute - 5),
                a("E", Status.PREIS, tag = heute - 30),
                a("F", Status.PREIS, tag = heute - 31),
                a("G", Status.PREIS, tag = null),
                a("H", Status.ZURUECK, tag = heute - 2),
            ),
            heute,
        )
        assertEquals(IpoUebersicht.Kopf(2, 1, 2), k)
    }

    @Test
    fun volumenSummeOhnePlatzhalter() {
        val s = IpoUebersicht.volumenSumme(
            listOf(
                a("A", Status.EINGEREICHT, 1_000_000_000.0),
                a("B", Status.ANGEKUENDIGT, 100.0, "5.00"),
                a("C", Status.PREIS, 50.0, "5.00"),
                a("D", Status.PREIS, null),
            ),
        )
        assertEquals(150.0, s, 1e-9)
    }

    @Test
    fun abrufMonateMitFolgemonat() {
        val m = IpoUebersicht.abrufMonate(LocalDate.of(2026, 11, 11))
        assertEquals(listOf(12, 11, 10, 9), m.map { it.monthValue })
        assertEquals(2026, m.first().year)
        val jahr = IpoUebersicht.abrufMonate(LocalDate.of(2026, 12, 31))
        assertEquals(LocalDate.of(2027, 1, 31), jahr.first())
        assertEquals(listOf(1, 12, 11, 10), jahr.map { it.monthValue })
    }
    // Bericht: Schlagzeilen zu Termin und Preis gehen vor, wenn sie nicht viel aelter sind als die juengste
    @Test
    fun berichtBevorzugtTerminUndPreis() {
        val tag = 86_400_000L
        fun m(titel: String, zeit: Long) = LaenderZusatz.Meldung(titel, "https://beispiel.test/" + zeit, zeit, "Quelle")
        val allgemein = m("Acme erwaegt hoehere Bewertung beim IPO", 20 * tag)
        val termin = m("Acme: Preisspanne steht, Erstnotiz am Donnerstag", 18 * tag)
        val alt = m("Acme nennt Preisspanne", 2 * tag)
        assertEquals(termin, IpoUebersicht.juengste(listOf(allgemein, termin), "Acme, Inc."))
        // zu alt: die juengste gewinnt
        assertEquals(allgemein, IpoUebersicht.juengste(listOf(allgemein, alt), "Acme, Inc."))
        assertEquals(allgemein, IpoUebersicht.juengste(listOf(allgemein), "Acme, Inc."))
        assertTrue(IpoUebersicht.nenntTermin("Acme sets price range for IPO"))
        assertFalse(IpoUebersicht.nenntTermin("Acme waechst schneller"))
    }
    // Mantelgesellschaften: auch eingereicht ohne Preis erkannt, damit sie nicht unter den grossen Angeboten stehen
    @Test
    fun mantelgesellschaftOhnePreis() {
        assertTrue(a("Nordkap Capital Corp XIV", Status.EINGEREICHT, 345e6, symbol = "NKXIU").spac)
        assertTrue(a("Seeadler Acquisition II", Status.EINGEREICHT, 172e6, symbol = "SEAA").spac)
        assertTrue(a("Lower Fjord Acquisitions", Status.EINGEREICHT, 230e6).spac)
        // vierstelliges Kuerzel auf U ohne Preis ist keine Mantelgesellschaft
        assertFalse(a("Bambusa Search Inc", Status.EINGEREICHT, 500e6, symbol = "BAMU").spac)
        assertTrue(a("Bambusa Units", Status.ANGEKUENDIGT, 100e6, preis = "10.00", symbol = "BAMU").spac)
        assertFalse(a("Acme, PBC", Status.EINGEREICHT, 1e9, symbol = "ACM").spac)
        val gross = IpoUebersicht.grosse(listOf(a("Nordkap Capital Corp XIV", Status.EINGEREICHT, 345e6, symbol = "NKXIU"), a("Acme, PBC", Status.EINGEREICHT, 1e9, symbol = "ACM")))
        assertEquals(listOf("Acme, PBC"), gross.map { it.name })
    }
}
