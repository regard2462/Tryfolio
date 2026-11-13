package de.troetenrepublik.app.ui

import androidx.compose.ui.text.AnnotatedString
import de.troetenrepublik.app.engine.Achsen
import de.troetenrepublik.app.engine.RisikoDiagramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Nachbesserung: Achsen auf dem Datenbereich, Beschriftungen ohne Ueberlappung, Startwert des Hebel-Simulators,
// Tausendertrenner im Eingabefeld, Ganz-Euro-Betraege mit Vorzeichen.
class Fassung078U45Test {

    @Test
    fun achseZoomtAufDenDatenbereich() {
        val b = Achsen.bereich(0.18, 0.52, 4, 0.01)
        assertTrue(b.von > 0.0 && b.von <= 0.18)
        assertTrue(b.bis >= 0.52 && b.bis < 0.7)
        assertTrue(b.ticks.size in 3..8)
        assertEquals(b.von, b.ticks.first(), 1e-9)
        assertEquals(b.bis, b.ticks.last(), 1e-9)
    }

    @Test
    fun nullKommtNurBeiWertenNaheNullOderNegativ() {
        // Kleinster Wert nahe null: die Achse beginnt bei 0
        assertEquals(0.0, Achsen.bereich(0.03, 0.40, 4, 0.01).von, 1e-9)
        // Negative Werte: Null liegt im Bereich
        val neg = Achsen.bereich(-0.10, 0.30, 4, 0.01)
        assertTrue(neg.von < 0.0 && neg.bis > 0.0)
        assertTrue(neg.ticks.any { kotlin.math.abs(it) < 1e-12 })
        // Alles negativ und weit von null: keine Null
        assertTrue(Achsen.bereich(-0.40, -0.20, 4, 0.01).bis < 0.0)
    }

    @Test
    fun achseMitEinemWertBleibtEndlich() {
        val b = Achsen.bereich(0.2, 0.2, 4, 0.01)
        assertTrue(b.bis > b.von)
        assertTrue(b.ticks.size >= 2)
    }

    @Test
    fun beschriftungenOhneUeberlappungMitNullVorrang() {
        // Drei Striche: oben, Null, unten nur 6 px unter der Null; Hoehe 12 px
        val pos = listOf(10f, 100f, 106f)
        val gewaehlt = Achsen.ohneUeberlappung(pos, listOf(1, 0, 2), 12f)
        assertTrue(1 in gewaehlt && 0 in gewaehlt)
        assertFalse(2 in gewaehlt)
        // Genug Abstand: alle bleiben
        assertEquals(setOf(0, 1, 2), Achsen.ohneUeberlappung(listOf(0f, 50f, 100f), listOf(0, 1, 2), 12f))
    }

    @Test
    fun entfernteBeschriftungFindetPlatzWoDirektKeinerIst() {
        // Ein Punkt in einer Ecke, rundherum belegt bis auf weit entfernt rechts
        val marken = listOf(RisikoDiagramme.Marke(50f, 50f, 5f, 40f to 10f))
        val flaeche = RisikoDiagramme.Rechteck(0f, 0f, 200f, 100f)
        val sperre = listOf(RisikoDiagramme.Rechteck(55f, 40f, 100f, 60f), RisikoDiagramme.Rechteck(0f, 0f, 50f, 100f))
        val nah = RisikoDiagramme.beschriftungen(marken, listOf(0), flaeche, sperre)
        val fern = RisikoDiagramme.beschriftungen(marken, listOf(0), flaeche, sperre, fern = listOf(30f, 60f))
        assertTrue(nah.isEmpty())
        assertEquals(1, fern.size)
    }

    @Test
    fun hebelStartIstGroessstePositionDanachIndex() {
        val k = hebelStartKandidaten(
            listOf(Triple("AAPL", "Apple", 100.0), Triple("SAP.DE", "SAP", 300.0), Triple("AAPL", "Apple", 250.0), Triple("X", "X", 0.0)),
        )
        assertEquals("AAPL", k.first().first)
        assertEquals(listOf("AAPL", "SAP.DE"), k.map { it.first }.take(2))
        assertEquals(HEBEL_START_INDEX, k.last())
        assertFalse(k.any { it.first == "X" })
        assertEquals(listOf(HEBEL_START_INDEX), hebelStartKandidaten(emptyList()))
    }

    @Test
    fun tausenderGruppierung() {
        assertEquals("10.000", tausenderGruppiert("10000", '.'))
        assertEquals("1.234.567", tausenderGruppiert("1234567", '.'))
        assertEquals("999", tausenderGruppiert("999", '.'))
        assertEquals("", tausenderGruppiert("", '.'))
        assertEquals("100,000", tausenderGruppiert("100000", ','))
    }

    @Test
    fun tausenderCursorStellen() {
        val t = TausenderTransformation('.').filter(AnnotatedString("10000"))
        assertEquals("10.000", t.text.text)
        assertEquals(0, t.offsetMapping.originalToTransformed(0))
        assertEquals(2, t.offsetMapping.originalToTransformed(2))
        assertEquals(4, t.offsetMapping.originalToTransformed(3))
        assertEquals(6, t.offsetMapping.originalToTransformed(5))
        assertEquals(5, t.offsetMapping.transformedToOriginal(6))
        assertEquals(2, t.offsetMapping.transformedToOriginal(3))
        assertEquals(0, t.offsetMapping.transformedToOriginal(0))
    }

    @Test
    fun ganzeEuroMitVorzeichen() {
        val plus = Fmt.signedEuro(25_419.4, 0)
        assertTrue("plus=[$plus]", plus.startsWith("+"))
        assertTrue("plus=[$plus]", plus.contains("25.419") || plus.contains("25,419"))
        assertEquals("plus=[$plus]", "25419", plus.filter { it.isDigit() })
        val minus = Fmt.signedEuro(-30_418.6, 0)
        assertTrue("minus=[$minus]", minus.contains("30.419") || minus.contains("30,419"))
        assertEquals("minus=[$minus]", "30419", minus.filter { it.isDigit() })
        assertFalse("null=[${Fmt.signedEuro(0.3, 0)}]", Fmt.signedEuro(0.3, 0).contains("+"))
    }
}
