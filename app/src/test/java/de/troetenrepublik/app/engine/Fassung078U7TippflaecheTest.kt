package de.troetenrepublik.app.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Fassung078U7TippflaecheTest {
    private fun f(breite: Float, hoehe: Float, links: Float, rechts: Float) =
        Tippmass.textFlaeche(breite, hoehe, links, rechts, rand = 10f, minHoehe = 28f, maxHoehe = 36f, einzeiligBis = 56f)

    @Test fun einzeiligMitGrosserTippflaecheBleibtKnappUmDieSchrift() {
        // 48 hoch, Schrift 4 vom Rand: Feld 36 hoch in der Mitte, seitlich 6 ueber den Knopf hinaus
        assertArrayEquals(floatArrayOf(-6f, 6f, 106f, 42f), f(100f, 48f, 4f, 4f), 0.001f)
    }

    @Test fun breiterInnenabstandWirdEingezogen() {
        // Schrift 20 vom Rand: Feld beginnt 10 vor der Schrift
        assertArrayEquals(floatArrayOf(10f, 4f, 190f, 40f), f(200f, 44f, 20f, 20f), 0.001f)
        // links 12, rechts buendig
        assertArrayEquals(floatArrayOf(2f, -1f, 90f, 27f), f(80f, 26f, 12f, 0f), 0.001f)
    }

    @Test fun niedrigeSchriftBekommtMindesthoehe() {
        assertArrayEquals(floatArrayOf(-10f, -5f, 70f, 23f), f(60f, 18f, 0f, 0f), 0.001f)
    }

    @Test fun mehrzeiligBelegtDieGanzeHoehe() {
        assertArrayEquals(floatArrayOf(-10f, 0f, 310f, 90f), f(300f, 90f, 0f, 0f), 0.001f)
    }

    @Test fun zuSchmalFaelltAufDieGrenzenZurueck() {
        assertArrayEquals(floatArrayOf(0f, 0f, 30f, 28f), f(30f, 28f, 20f, 20f), 0.001f)
    }

    @Test fun kreisBleibtImKnopf() {
        assertEquals(20f, Tippmass.kreisRadius(48f, 48f, 20f), 0.001f)
        assertEquals(15f, Tippmass.kreisRadius(30f, 36f, 20f), 0.001f)
    }

    @Test fun drueckenGehtVor() {
        assertEquals(0.14f, Tippmass.deckkraft(true, true, true, 0.14f, 0.06f, 0.05f), 0f)
        assertEquals(0.06f, Tippmass.deckkraft(false, true, true, 0.14f, 0.06f, 0.05f), 0f)
        assertEquals(0.05f, Tippmass.deckkraft(false, false, true, 0.14f, 0.06f, 0.05f), 0f)
        assertEquals(0f, Tippmass.deckkraft(false, false, false, 0.14f, 0.06f, 0.05f), 0f)
    }
}
