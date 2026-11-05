package de.troetenrepublik.app.engine

import de.troetenrepublik.app.ui.tendenz
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sin

// Fassung 5.92: Tendenz unter „Im Preis?“. Markt hoeher als ueblich heisst eher short, tiefer eher long; bei den
// Zinsen umgekehrt fuer Anleihen; datierte Signale zeigen ihre Richtung nur, solange erst ein Teil gelaufen ist.
class Fassung592TendenzTest {
    private val schaetzung = Eingepreist.schaetzen(
        (0 until 600).map { i ->
            val x = 10 * sin(i / 9.0)
            Eingepreist.Punkt(LocalDate.of(2010, 1, 1).toEpochDay() + 7L * i, x, -x + 2 * (sin(i * 12.9898 + 78.233) * 43758.5453 % 1.0))
        },
        saison = false, vorzeichen = -1,
    )!!
    private val heute = LocalDate.of(2026, 10, 1).toEpochDay()

    @Test fun marktZuHochIstShort() {
        val e = Eingepreist.einordnen(schaetzung, 0.0, 50.0, heute)
        assertEquals(Eingepreist.Urteil.HOEHER, e.urteil)
        assertEquals(-1, tendenz(e))
        assertEquals(1, tendenz(e, Fruehindikatoren.Mass.ZINS))
    }

    @Test fun marktZuTiefIstLong() {
        val e = Eingepreist.einordnen(schaetzung, 0.0, -50.0, heute)
        assertEquals(1, tendenz(e))
        assertEquals(-1, tendenz(e, Fruehindikatoren.Mass.ZINS))
    }

    @Test fun passendOhneRichtung() {
        assertEquals(0, tendenz(Eingepreist.einordnen(schaetzung, 0.0, 0.0, heute)))
    }

    @Test fun datierteSignale() {
        fun g(lauf: Eingepreist.Lauf, r: Int) = Eingepreist.Gelaufen(0.0, 10, null, 0, lauf, null, r)
        assertEquals(1, tendenz(g(Eingepreist.Lauf.WENIG, 1)))
        assertEquals(-1, tendenz(g(Eingepreist.Lauf.TEIL, -1)))
        assertEquals(0, tendenz(g(Eingepreist.Lauf.GROSSTEIL, 1)))
        assertEquals(0, tendenz(g(Eingepreist.Lauf.GEGEN, 1)))
        assertEquals(0, tendenz(g(Eingepreist.Lauf.OHNE_VORSPRUNG, -1)))
    }
}
