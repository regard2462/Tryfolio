package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Investorenbriefe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Gekuerzte Texte tragen kein Auslassungszeichen; ob sie gekuerzt sind, steht in einem Kennzeichen (Blende in der Oberflaeche).
class Fassung068BlendeTest {

    @Test
    fun anrissOhneZeichenMitKennzeichen() {
        val lang = Anriss.lesen("<p>" + "wort ".repeat(120) + "</p>", "x")
        assertTrue(lang.gekuerzt)
        assertFalse(lang.text.contains("…"))
        assertTrue(lang.text.length <= 400)
        // Am Satzende gekuerzt oder ganz: kein Kennzeichen
        val satz = Anriss.lesen("<p>" + "Ein langer Satz über Geldpolitik und Zinsen. ".repeat(20) + "</p>", "x")
        assertFalse(satz.gekuerzt)
        assertTrue(satz.text.endsWith("."))
        val kurz = Anriss.lesen("<p>Ein kurzer Absatz über Geldpolitik, der genug Zeichen hat, um als Anriss zu gelten.</p>", "x")
        assertFalse(kurz.gekuerzt)
        assertEquals(Anriss.ausHtml("<p>" + "wort ".repeat(120) + "</p>", "x"), lang.text)
    }

    @Test
    fun auszugOhneZeichenMitKennzeichen() {
        val lang = (1..90).joinToString(" ") { if (it == 45) "Microsoft" else "word$it" } + "."
        val stelle = lang.indexOf("Microsoft").let { it until it + 9 }
        val vorn = Investorenbriefe.auszugStueck(listOf(lang), 0, stelle)
        assertFalse(vorn.text.contains("…"))
        assertTrue(vorn.gekuerzt)
        assertTrue(vorn.text.contains("Microsoft"))
        // Die Textstelle im Brief ist mit dem Auszug weiter auffindbar
        val absatz = (1..300).joinToString(" ") { if (it == 250) "Gamma" else "wort$it" }
        val u = Investorenbriefe.umfeldStueck("Kopf.\n$absatz\nFuss.", absatz.substring(absatz.indexOf("Gamma"), absatz.indexOf("Gamma") + 20))
        assertNotNull(u)
        assertFalse(u!!.text.contains("…"))
        assertTrue(u.text.contains("Gamma wort251"))
        assertTrue(Investorenbriefe.woerterZahl(u.text) <= 250)
        // Kurzer Absatz: ganz, ohne Kennzeichen
        val ganz = Investorenbriefe.umfeldStueck("Eins.\nDer Absatz nennt Delta und bleibt kurz.\nDrei.", "Der Absatz nennt Delta")
        assertNotNull(ganz)
        assertFalse(ganz!!.gekuerzt)
    }
}
