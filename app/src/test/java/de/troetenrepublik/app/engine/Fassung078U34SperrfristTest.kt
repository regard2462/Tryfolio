package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Sperrfristen, die bald enden (0.78): Auswahl der Boersengaenge, Enddatum aus der Frist des Prospekts, Speicher und
// der gesperrte Anteil aus dem Abschnitt „Shares Eligible for Future Sale“. Die Ausschnitte unter resources/ipo sind
// nach dem ueblichen Wortlaut solcher Prospekte geschrieben, Firmen und Zahlen sind erfunden.
class Fassung078U34SperrfristTest {

    private fun prospekt(name: String): String = Rss.text(javaClass.classLoader!!.getResourceAsStream("ipo/$name.html")!!.readBytes())

    private fun angebot(name: String, symbol: String?, tag: Long?, volumen: Double? = 100e6, status: IpoKalender.Status = IpoKalender.Status.PREIS) =
        IpoKalender.Angebot(name.lowercase(), symbol, name, "NASDAQ", "20.00", 5_000_000, volumen, tag, status)

    private val heute = 20_000L

    // ------------------------------------------------------------ Gesperrter Anteil

    @Test
    fun anteilDerHalter() {
        val g = IpoProspekt.gesperrt(prospekt("sperre-anteil"))!!
        assertEquals(97.5, g.anteil, 1e-9)
        assertEquals(false, g.nachAngebot)
        // Die Frist steht im selben Ausschnitt
        assertEquals(180, IpoProspekt.sperrfrist(prospekt("sperre-anteil")))
    }

    @Test
    fun stueckzahlGegenAlleAktien() {
        val g = IpoProspekt.gesperrt(prospekt("sperre-stueck"))!!
        assertEquals(80.0, g.anteil, 1e-9)
        assertEquals(true, g.nachAngebot)
    }

    @Test
    fun widerspruchOderOhneAngabeErgibtNichts() {
        assertNull(IpoProspekt.gesperrt(prospekt("sperre-uneinig")))
        // „substantially all“ ist keine Zahl
        assertNull(IpoProspekt.gesperrt(prospekt("prospekt-vorstand-zuerst")))
        assertNull(IpoProspekt.gesperrt("<html><body><p>Shares Eligible for Future Sale</p></body></html>"))
        // Ausserhalb des Abschnitts zaehlt nichts
        assertNull(IpoProspekt.gesperrt("<p>Holders of approximately 97% of our outstanding shares have signed lock-up agreements.</p>"))
        // Die Gruppe „more than 5%“ ist kein gesperrter Anteil
        assertNull(IpoProspekt.gesperrt("<h2>Shares Eligible for Future Sale</h2><p>Our officers and holders of more than 5% of our common stock have signed lock-up agreements.</p>"))
        // Frei handelbare Aktien des Angebots sind nicht die gesperrten
        assertNull(
            IpoProspekt.gesperrt(
                "<h2>Shares Eligible for Future Sale</h2><p>We will have 50,000,000 shares of common stock outstanding. All of the 18,000,000 shares " +
                    "sold in this offering will be freely tradable, except for shares subject to lock-up agreements.</p>",
            ),
        )
    }

    @Test
    fun auszugTraegtDenAnteil() {
        val a = IpoProspekt.lesen(prospekt("sperre-anteil"))
        assertEquals(180, a.sperrfristTage)
        assertEquals(97.5, a.gesperrt!!.anteil, 1e-9)
        val g = IpoSperrfristen.ausAuszug(a, 5L)
        assertEquals(180, g.tage)
        assertEquals(97.5, g.anteil!!, 1e-9)
        // Ohne Frist kein Anteil und kein Eintrag
        val ohne = IpoSperrfristen.ausAuszug(IpoProspekt.Auszug(null, emptyList(), null, IpoProspekt.Gesperrt(90.0, true)), 5L)
        assertNull(ohne.tage)
        assertNull(ohne.anteil)
        assertNull(IpoSperrfristen.ausAuszug(null, 5L).tage)
    }

    // ------------------------------------------------------------ Liste

    @Test
    fun kandidatenOhneMantelgesellschaftenGroessteZuerst() {
        val liste = listOf(
            angebot("Klein Corp", "KLN", heute - 100, 50e6),
            angebot("Gross Corp", "GRS", heute - 150, 900e6),
            angebot("Alpha Acquisition Corp", "ALPU", heute - 100, 2e9),
            angebot("Ohne Kuerzel Inc", null, heute - 100),
            angebot("Zu Alt Inc", "ALT", heute - IpoSperrfristen.ZURUECK_TAGE - 1),
            angebot("Morgen Inc", "MRG", heute + 1),
            angebot("Eingereicht Inc", "EIN", heute - 10, status = IpoKalender.Status.EINGEREICHT),
            angebot("Gross Corp", "GRS", heute - 150, 900e6),
        )
        assertEquals(listOf("GRS", "KLN"), IpoSperrfristen.kandidaten(liste, heute).map { it.symbol })
    }

    @Test
    fun nurFristenDieInNeunzigTagenEnden() {
        val a = angebot("Bald Corp", "BLD", heute - 170)
        val b = angebot("Heute Corp", "HTE", heute - 180)
        val c = angebot("Vorbei Corp", "VRB", heute - 181)
        val d = angebot("Spaet Corp", "SPT", heute - 89)
        val e = angebot("Kurz Corp", "KRZ", heute - 60)
        val f = angebot("Ohne Frist Corp", "OHN", heute - 170)
        val g = angebot("Ungelesen Corp", "UNG", heute - 170)
        val gelesen = mapOf(
            IpoSperrfristen.schluessel(a) to IpoSperrfristen.Gelesen(180, 95.0, false, 1L),
            IpoSperrfristen.schluessel(b) to IpoSperrfristen.Gelesen(180, null, false, 1L),
            IpoSperrfristen.schluessel(c) to IpoSperrfristen.Gelesen(180, null, false, 1L),
            IpoSperrfristen.schluessel(d) to IpoSperrfristen.Gelesen(180, null, false, 1L),
            IpoSperrfristen.schluessel(e) to IpoSperrfristen.Gelesen(90, 80.0, true, 1L),
            IpoSperrfristen.schluessel(f) to IpoSperrfristen.Gelesen(null, null, false, 1L),
        )
        val liste = IpoSperrfristen.eintraege(listOf(a, b, c, d, e, f, g), gelesen, heute)
        // Ende heute, in 10 und in 30 Tagen; vorbei, in 91 Tagen, ohne Frist und ungelesen fehlen
        assertEquals(listOf("HTE", "BLD", "KRZ"), liste.map { it.angebot.symbol })
        assertEquals(listOf(0L, 10L, 30L), liste.map { it.inTagen })
        assertEquals(heute + 10, liste[1].ende)
        assertEquals(95.0, liste[1].anteil!!, 1e-9)
        assertTrue(liste[2].nachAngebot)
        assertEquals(90, liste[2].tage)
    }

    @Test
    fun offenNurUngeleseneUndAlteFehlversuche() {
        val tagMs = 86_400_000L
        val jetzt = 100 * tagMs
        val a = angebot("A Corp", "AAA", heute - 10)
        val b = angebot("B Corp", "BBB", heute - 10)
        val c = angebot("C Corp", "CCC", heute - 10)
        val d = angebot("D Corp", "DDD", heute - 10)
        val gelesen = mapOf(
            IpoSperrfristen.schluessel(a) to IpoSperrfristen.Gelesen(180, null, false, 1L),
            IpoSperrfristen.schluessel(b) to IpoSperrfristen.Gelesen(null, null, false, jetzt - tagMs),
            IpoSperrfristen.schluessel(c) to IpoSperrfristen.Gelesen(null, null, false, jetzt - IpoSperrfristen.ERNEUT_TAGE * tagMs),
        )
        assertEquals(listOf("CCC", "DDD"), IpoSperrfristen.offen(listOf(a, b, c, d), gelesen, jetzt).map { it.symbol })
        assertEquals(listOf("CCC"), IpoSperrfristen.offen(listOf(a, b, c, d), gelesen, jetzt, max = 1).map { it.symbol })
    }

    @Test
    fun speicherHinUndZurueck() {
        val gelesen = mapOf(
            "AAA:19990" to IpoSperrfristen.Gelesen(180, 97.5, false, 123L),
            "BBB:19900" to IpoSperrfristen.Gelesen(90, null, true, 456L),
            "CCC:19800" to IpoSperrfristen.Gelesen(null, null, false, 789L),
        )
        assertEquals(gelesen, IpoSperrfristen.lesen(IpoSperrfristen.schreiben(gelesen)))
        assertTrue(IpoSperrfristen.lesen("").isEmpty())
        assertTrue(IpoSperrfristen.lesen("kaputt\nAAA:1\t180").isEmpty())
        // Unglaubwuerdige Werte fallen beim Lesen weg
        assertNull(IpoSperrfristen.lesen("AAA:1\t9999\t250\t0\t5").getValue("AAA:1").tage)
        assertNull(IpoSperrfristen.lesen("AAA:1\t180\t250\t0\t5").getValue("AAA:1").anteil)
    }
}
