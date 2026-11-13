package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.RohstoffLageTexte
import de.troetenrepublik.app.ui.quellenLogoUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regelpruefung 0.79, Teil 4: Quellenlogos der Nachrichten und neue Texte
class Fassung079R4Test {

    @Test
    fun quellenLogoVonDerEigenenDomain() {
        assertEquals(
            listOf("https://example.com/apple-touch-icon.png", "https://example.com/favicon.ico"),
            quellenLogoUrls("www.example.com"),
        )
        val sub = quellenLogoUrls("feeds.example.com")
        assertEquals("https://feeds.example.com/apple-touch-icon.png", sub.first())
        assertTrue(sub.contains("https://example.com/apple-touch-icon.png"))
        assertTrue(quellenLogoUrls("").isEmpty())
        assertTrue(quellenLogoUrls("Reuters Staff").isEmpty())
    }

    @Test
    fun optionenErklaerungenInBeidenSprachen() {
        for (k in listOf("wand", "gex", "nullpunkt", "ivrv", "skew", "ivrang", "ivperz")) {
            val de = De.opErklaerung(k)
            val en = En.opErklaerung(k)
            assertTrue(k, de.isNotBlank() && en.isNotBlank() && de != En.opErklaerung("unbekannt"))
            assertFalse(k, de.contains('—') || en.contains('—'))
            assertTrue(k, de != en)
        }
        assertEquals("Offene Kontrakte je Basispreis", De.opEbenen)
        assertEquals("Open interest by strike", En.opEbenen)
    }

    @Test
    fun oelVorgabeNenntEinheitUndRichtung() {
        assertEquals("40 Tsd. darüber", RohstoffLageTexte.text(true, "ow.ueber", listOf("40")))
        assertEquals("Vorgabe 2,15 Mio. Barrel/Tag, Förderung 40 Tsd. darüber", RohstoffLageTexte.text(true, "ow.vorgabe", listOf("2,15", "40 Tsd. darüber")))
        assertEquals("Target 2.15 mb/d, output on target", RohstoffLageTexte.text(false, "ow.vorgabe", listOf("2.15", RohstoffLageTexte.text(false, "ow.gleichVorgabe", emptyList()))))
    }

    @Test
    fun mitteilungenUndLevermannBenennenDenInhalt() {
        assertEquals("Weltereignisse im Depot", De.mtWelt)
        assertEquals("World events in your portfolio", En.mtWelt)
        assertEquals("Kursänderung 6 Monate", De.levName("KURS6"))
        assertEquals("Price change 12 months", En.levName("KURS12"))
        assertFalse(De.ersSenatIntro("01.01.2026").contains("Congress Trading Monitor"))
    }
}
