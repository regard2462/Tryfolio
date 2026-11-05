package de.troetenrepublik.app.market

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pflichtangaben der Quellen (5.94): BLS-Satz und Tageskontingent, Quellenformat der New York Fed, Feed-Adressen von SNB und Bundesbank.
class Fassung594PflichtTest {

    @Test
    fun blsKontingent() {
        var t = 1_000_000L
        val k = Abrufkontingent(null) { t }
        repeat(20) { assertTrue(k.erlaubt(Abrufkontingent.BLS)); k.buchen(Abrufkontingent.BLS); t += 60_000L }
        assertFalse(k.erlaubt(Abrufkontingent.BLS))
        t += 86_400_000L
        assertTrue(k.erlaubt(Abrufkontingent.BLS))
    }

    @Test
    fun texte() {
        val satz = "BLS.gov cannot vouch for the data or analyses derived from these data after the data have been retrieved from BLS.gov."
        assertTrue(De.blsHinweis("03.11.2026").contains("03.11.2026") && De.blsHinweis("03.11.2026").endsWith(satz))
        assertTrue(En.blsHinweis("3 Nov 2026").endsWith(satz))
        assertEquals("Quelle: © 2026 Federal Reserve Bank of New York. Content from the New York Fed subject to the Terms of Use at newyorkfed.org.", De.ersQuelle("lieferketten", 2026))
    }

    @Test
    fun feeds() {
        val de = Rss.eingebaut(true); val en = Rss.eingebaut(false)
        assertEquals("https://www.snb.ch/public/rss/de/news", de.first { it.schluessel == "snb" }.adresse)
        assertEquals("https://www.snb.ch/public/rss/en/news", en.first { it.schluessel == "snb" }.adresse)
        assertTrue(de.first { it.schluessel == "bbk" }.adresse.contains("/de/633286/"))
        assertTrue(en.first { it.schluessel == "bbk" }.adresse.contains("/en/633306/"))
    }
}
