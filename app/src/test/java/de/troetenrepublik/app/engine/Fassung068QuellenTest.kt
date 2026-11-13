package de.troetenrepublik.app.engine

import org.junit.Assert.*
import org.junit.Test

class Fassung068QuellenTest {

    // https und http mit Rechnername gelten, Leerraum am Rand wird entfernt.
    @Test fun adresseNimmtWebLinks() {
        assertEquals("https://www.destatis.de/DE", WissenQuellen.adresse("https://www.destatis.de/DE"))
        assertEquals("http://example.org", WissenQuellen.adresse("http://example.org"))
        assertEquals("https://example.org/a", WissenQuellen.adresse("  https://example.org/a \n"))
    }

    // Fremde Schemata, fehlender Rechnername, Leerzeichen und null werden abgelehnt.
    @Test fun adresseLehntUngueltigesAb() {
        assertNull(WissenQuellen.adresse("javascript:alert(1)"))
        assertNull(WissenQuellen.adresse("ftp://x.de"))
        assertNull(WissenQuellen.adresse("https://"))
        assertNull(WissenQuellen.adresse("https://example.org/a b"))
        assertNull(WissenQuellen.adresse(null))
    }

    // Anbieter ohne www. und ohne Port, klein geschrieben.
    @Test fun anbieterLiefertRechnernamen() {
        assertEquals("destatis.de", WissenQuellen.anbieter("https://www.destatis.de/DE/Presse"))
        assertEquals("pages.stern.nyu.edu", WissenQuellen.anbieter("https://pages.stern.nyu.edu:443/~adamodar"))
    }

    // Lange Titel enden spaetestens bei 300 Zeichen und ohne angehaengtes Zeichen.
    @Test fun titelKuerztLange() {
        val lang = "wort ".repeat(200)
        val t = WissenQuellen.titel(lang)
        assertTrue(t.length <= 300)
        assertFalse(t.endsWith("…"))
        assertTrue(t.endsWith("wort"))
    }

    // Zeilenumbrueche und mehrfacher Leerraum werden zu einem Leerzeichen.
    @Test fun titelFaltetUmbrueche() {
        assertEquals("Erste Zeile zweite Zeile", WissenQuellen.titel("  Erste\nZeile \r\n  zweite   Zeile "))
        assertEquals("", WissenQuellen.titel(null))
    }
}
