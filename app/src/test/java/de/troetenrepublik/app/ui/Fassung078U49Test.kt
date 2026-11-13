package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.HebelSimulator
import de.troetenrepublik.app.i18n.HebelGrenzeTexte
import de.troetenrepublik.app.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Hebel-Simulator: Regler von -3 bis 5 in Schritten von 0,25 statt acht Stufen.
class Fassung078U49Test {

    // Alle Positionen der Spur und ihre Hebel
    private val hebelAllePositionen = (0..HEBEL_POS_SCHRITTE).map { posHebel(it * HEBEL_SCHRITT) }

    @Test
    fun spurHatDasRasterOhneNull() {
        assertEquals(HEBEL_POS_SCHRITTE + 1, hebelAllePositionen.size)
        assertEquals(-3.0, hebelAllePositionen.first(), 0.0)
        assertEquals(5.0, hebelAllePositionen.last(), 0.0)
        assertTrue(hebelAllePositionen.none { it == 0.0 })
        // Zwischen -1 und 1 liegt nichts, sonst steigt die Spur in Schritten von 0,25
        assertTrue(hebelAllePositionen.none { it > -1.0 && it < 1.0 })
        assertEquals(hebelAllePositionen.size, hebelAllePositionen.toSet().size)
        hebelAllePositionen.zipWithNext().forEach { (a, b) ->
            assertEquals(if (a == -1.0) 2.0 else HEBEL_SCHRITT, b - a, 1e-12)
        }
        assertTrue(hebelAllePositionen.contains(2.25))
        assertTrue(hebelAllePositionen.containsAll(HebelSimulator.HEBEL))
    }

    @Test
    fun positionUndHebelSindUmkehrbar() {
        hebelAllePositionen.forEachIndexed { i, h ->
            assertEquals(i * HEBEL_SCHRITT, hebelPos(h), 1e-12)
            assertEquals(h, posHebel(hebelPos(h)), 1e-12)
        }
        assertEquals(HebelSimulator.HEBEL_STANDARD, posHebel(hebelPos(HebelSimulator.HEBEL_STANDARD)), 0.0)
    }

    @Test
    fun nullIstNichtWaehlbar() {
        assertEquals(1.0, hebelEinrasten(0.0), 0.0)
        assertEquals(1.0, hebelEinrasten(0.4), 0.0)
        assertEquals(1.0, hebelEinrasten(0.9), 0.0)
        assertEquals(-1.0, hebelEinrasten(-0.1), 0.0)
        assertEquals(-1.0, hebelEinrasten(-0.9), 0.0)
        assertEquals(2.25, hebelEinrasten(2.3), 0.0)
        assertEquals(2.5, hebelEinrasten(2.4), 0.0)
        assertEquals(5.0, hebelEinrasten(9.0), 0.0)
        assertEquals(-3.0, hebelEinrasten(-7.0), 0.0)
        assertEquals(2.0, hebelEinrasten(Double.NaN), 0.0)
    }

    @Test
    fun hebelwerteSindLesbar() {
        val alt = I18n.lang
        try {
            I18n.lang = Lang.DE
            assertEquals("2", hebelZahl(2.0))
            assertEquals("2,25", hebelZahl(2.25))
            assertEquals("2,5", hebelZahl(2.5))
            assertEquals("−0,75", hebelZahl(-0.75))
            assertEquals("−3", hebelZahl(-3.0))
            assertEquals("Aus 1.000 € mit Hebel 2,25", HebelGrenzeTexte.text(true, "h.kern.titel", listOf("1.000 €", hebelZahl(2.25))))
            assertEquals("2,25×", HebelGrenzeTexte.text(true, "h.mal", listOf(hebelZahl(2.25))))
            I18n.lang = Lang.EN
            assertEquals("2.25", hebelZahl(2.25))
            assertEquals("From 1,000 € with leverage 2.5", HebelGrenzeTexte.text(false, "h.kern.titel", listOf("1,000 €", hebelZahl(2.5))))
        } finally {
            I18n.lang = alt
        }
    }

    @Test
    fun rechnungBeiKommahebelLiegtZwischenDenNachbarn() {
        // Ein Jahr Tageskurse mit kleinem Auf und Ab bei steigendem Trend; bei so geringer Schwankung steigt die Rendite
        // im Bereich 2 bis 2,5 mit dem Hebel (bei starker Schwankung liegt der Gipfel der Kurve schon darunter)
        val tag = 86_400_000L
        val reihe = (0..400).map { i -> i * tag to 100.0 * (1.0 + 0.0006 * i) * (1.0 + 0.002 * if (i % 2 == 0) 1 else -1) }
        val a = HebelSimulator.Annahmen(aufschlag = 0.5, kosten = 0.75, zinsFest = 3.0)
        val zwei = HebelSimulator.rechnen(reihe, 2.0, a)!!
        val mitte = HebelSimulator.rechnen(reihe, 2.25, a)!!
        val zweiFuenf = HebelSimulator.rechnen(reihe, 2.5, a)!!
        assertEquals(2.25, mitte.hebel, 0.0)
        assertTrue(mitte.renditeMit > zwei.renditeMit && mitte.renditeMit < zweiFuenf.renditeMit)
        assertTrue(mitte.endMit > zwei.endMit && mitte.endMit < zweiFuenf.endMit)
        assertNotEquals(zwei.rueckgangMit, mitte.rueckgangMit, 0.0)
        assertTrue(mitte.rueckgangMit > zwei.rueckgangMit && mitte.rueckgangMit < zweiFuenf.rueckgangMit)
        // Negative Kommahebel rechnen ebenfalls: Ein Short auf einen steigenden Kurs verliert
        assertTrue(HebelSimulator.rechnen(reihe, -1.5, a)!!.renditeMit < 0)
    }

    @Test
    fun kurveLaeuftLueckenlosUeberNull() {
        val tag = 86_400_000L
        val reihe = (0..400).map { i -> i * tag to 100.0 * (1.0 + 0.0006 * i) * (1.0 + 0.002 * if (i % 2 == 0) 1 else -1) }
        val a = HebelSimulator.Annahmen(aufschlag = 0.5, kosten = 0.75, zinsFest = 3.0)
        val k = HebelSimulator.kurve(reihe, a)
        assertEquals(33, k.size)
        assertEquals(-3.0, k.first().first, 0.0)
        assertEquals(5.0, k.last().first, 0.0)
        k.zipWithNext().forEach { (p, q) -> assertEquals(HEBEL_SCHRITT, q.first - p.first, 1e-12) }
        listOf(-0.5, 0.0, 0.5).forEach { h ->
            val p = k.first { it.first == h }
            assertEquals(HebelSimulator.rechnen(reihe, h, a)!!.renditeMit, p.second, 1e-12)
        }
        // Hebel 0 bringt nur den Zins abzueglich Kosten, das ist weniger als Hebel 0,5 bei steigendem Kurs
        assertTrue(k.first { it.first == 0.5 }.second > k.first { it.first == 0.0 }.second)
    }
}
