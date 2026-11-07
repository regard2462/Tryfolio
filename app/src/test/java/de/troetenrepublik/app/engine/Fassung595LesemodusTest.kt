package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import org.junit.Assert.*
import org.junit.Test

// Knopf des Lesemodus in der Nachrichtenansicht (5.95): welcher Zustand welchen Knopf zeigt; welche Adresse eine
// Zwischenseite von Google ist.
class Fassung595LesemodusTest {

    @Test
    fun ohneWebAnsichtKeinKnopf() {
        assertEquals(Lesemodus.Knopf.KEINER,
            Lesemodus.knopf(webAnsicht = false, adresse = true, original = true, geladen = true, text = false, automatisch = true, laedt = true, liest = false, gelesen = false))
    }

    @Test
    fun ohneAdresseKeinKnopf() {
        assertEquals(Lesemodus.Knopf.KEINER,
            Lesemodus.knopf(webAnsicht = true, adresse = false, original = true, geladen = true, text = true, automatisch = true, laedt = false, liest = false, gelesen = false))
    }

    @Test
    fun originalseiteLaedtBeiOhneLesemodusKeinKnopf() {
        // ohneLesemodus: weder automatisch gelesen noch loading wahr
        assertEquals(Lesemodus.Knopf.KEINER,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = false, text = false, automatisch = false, laedt = false, liest = false, gelesen = false))
    }

    @Test
    fun nachDemLadenBeiOhneLesemodusNurText() {
        assertEquals(Lesemodus.Knopf.NUR_TEXT,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = true, text = false, automatisch = false, laedt = false, liest = false, gelesen = false))
    }

    @Test
    fun automatischLesenVorDemLadeendeLaedt() {
        assertEquals(Lesemodus.Knopf.LAEDT,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = false, text = false, automatisch = true, laedt = true, liest = false, gelesen = false))
    }

    @Test
    fun textVorDemLadeendeNurText() {
        assertEquals(Lesemodus.Knopf.NUR_TEXT,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = false, text = true, automatisch = true, laedt = true, liest = false, gelesen = false))
    }

    @Test
    fun ausleseAufTippLaedt() {
        assertEquals(Lesemodus.Knopf.LAEDT,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = true, text = false, automatisch = false, laedt = false, liest = true, gelesen = false))
    }

    @Test
    fun textansichtNachLeererAusleseOriginal() {
        assertEquals(Lesemodus.Knopf.ORIGINAL,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = false, geladen = true, text = false, automatisch = true, laedt = false, liest = false, gelesen = true))
    }

    @Test
    fun textansichtWaehrendAutomatischemLadenKeinKnopf() {
        assertEquals(Lesemodus.Knopf.KEINER,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = false, geladen = false, text = false, automatisch = true, laedt = true, liest = false, gelesen = false))
    }

    @Test
    fun ausSeiteLesenNurOhneTextUndGeladen() {
        assertTrue(Lesemodus.ausSeiteLesen(text = false, geladen = true))
        assertFalse(Lesemodus.ausSeiteLesen(text = true, geladen = true))
        assertFalse(Lesemodus.ausSeiteLesen(text = false, geladen = false))
        assertFalse(Lesemodus.ausSeiteLesen(text = true, geladen = false))
    }

    @Test
    fun handelsblattOhneLesemodus() {
        // Rss.ohneLesemodus: nie automatisch lesen, nur auf Tipp aus der geladenen Seite
        assertTrue(Rss.ohneLesemodus("https://www.handelsblatt.com/x.html", ""))
        val automatisch = !Rss.ohneLesemodus("https://www.handelsblatt.com/x.html", "")
        assertFalse(automatisch)
        assertEquals(Lesemodus.Knopf.KEINER,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = false, text = false, automatisch = automatisch, laedt = false, liest = false, gelesen = false))
        assertEquals(Lesemodus.Knopf.NUR_TEXT,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = true, text = false, automatisch = automatisch, laedt = false, liest = false, gelesen = false))
    }

    @Test
    fun googleZwischenseiteErkannt() {
        assertTrue(Lesemodus.zwischenseite("https://news.google.com/rss/articles/CBMi0KGKh?oc=5"))
        assertTrue(Lesemodus.zwischenseite("https://consent.google.com/?continue=https%3A%2F%2Fwww.zeit.de"))
        // Gross- und Kleinschreibung und Port aendern den Host nicht
        assertTrue(Lesemodus.zwischenseite("https://NEWS.GOOGLE.COM:443/x"))
    }

    @Test
    fun keineGoogleZwischenseite() {
        assertFalse(Lesemodus.zwischenseite("https://www.handelsblatt.com/politik/x.html"))
        assertFalse(Lesemodus.zwischenseite("https://google.com/search?q=Zeitung"))
        assertFalse(Lesemodus.zwischenseite("https://news.google.com.example.org/artikel"))
        assertFalse(Lesemodus.zwischenseite(null))
        assertFalse(Lesemodus.zwischenseite(""))
        // Ohne Schema gibt es keinen Host
        assertFalse(Lesemodus.zwischenseite("news.google.com/rss/articles/CBMi?oc=5"))
    }
}
