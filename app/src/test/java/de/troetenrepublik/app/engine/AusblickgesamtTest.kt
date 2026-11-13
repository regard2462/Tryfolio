package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Ausblickgesamt.Beitrag
import de.troetenrepublik.app.engine.Ausblickgesamt.Horizont
import de.troetenrepublik.app.engine.Ausblickgesamt.Markt
import de.troetenrepublik.app.engine.Ausblickgesamt.Urteil
import de.troetenrepublik.app.engine.Tendenz.Richtung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// [Ausblick 0.70] Gesamtrechnung im Reiter Ausblick.
class AusblickgesamtTest {
    private fun b(k: String, r: Richtung, ziel: String = Tendenz.AKTIEN, alter: Long = 0) = Beitrag(k, k, ziel, r, alter)

    // Sechs Aktien-Signale aus verschiedenen Gruppen
    private fun aktien(vararg r: Richtung): List<Beitrag> {
        val keys = listOf("zins", "rezession", Fruehindikatoren.ERSTANTRAEGE, Ersatzquellen.LIEFERKETTEN, "hormus", "inflation")
        return r.mapIndexed { i, x -> b(keys[i], x, if (keys[i] == "inflation") Tendenz.AKTIEN else Tendenz.AKTIEN) }
    }

    @Test
    fun eindeutigPositivZeigtAufwaerts() {
        val e = Ausblickgesamt.berechnen(aktien(Richtung.BULLISH, Richtung.BULLISH, Richtung.BULLISH, Richtung.BULLISH, Richtung.NEUTRAL, Richtung.BULLISH), Markt.AKTIEN)
        assertEquals(Urteil.AUFWAERTS, e.urteil)
        assertEquals(Ausblickgesamt.Staerke.DEUTLICH, e.staerke)
        assertEquals(5, e.positiv)
        assertEquals(0, e.negativ)
        assertTrue(e.punktzahl > 0.6)
    }

    @Test
    fun eindeutigNegativZeigtAbwaerts() {
        val e = Ausblickgesamt.berechnen(aktien(Richtung.BEARISH, Richtung.BEARISH, Richtung.BEARISH, Richtung.NEUTRAL, Richtung.BEARISH, Richtung.BEARISH), Markt.AKTIEN)
        assertEquals(Urteil.ABWAERTS, e.urteil)
        assertTrue(e.punktzahl < -0.6)
        assertEquals(5, e.negativ)
        assertEquals("zins", e.treiberNegativ.first().schluessel)
    }

    @Test
    fun gemischteLageIstUneinheitlich() {
        val e = Ausblickgesamt.berechnen(aktien(Richtung.BULLISH, Richtung.BEARISH, Richtung.BULLISH, Richtung.BEARISH, Richtung.NEUTRAL, Richtung.NEUTRAL), Markt.AKTIEN)
        assertEquals(Urteil.UNEINHEITLICH, e.urteil)
        assertEquals(2, e.positiv)
        assertEquals(2, e.negativ)
    }

    @Test
    fun ueberwiegendNeutralIstSeitwaerts() {
        val e = Ausblickgesamt.berechnen(aktien(Richtung.NEUTRAL, Richtung.NEUTRAL, Richtung.NEUTRAL, Richtung.NEUTRAL, Richtung.NEUTRAL, Richtung.BULLISH), Markt.AKTIEN)
        assertEquals(Urteil.SEITWAERTS, e.urteil)
    }

    @Test
    fun wenigeDatenGebenKeineAussage() {
        val e = Ausblickgesamt.berechnen(aktien(Richtung.BULLISH, Richtung.BULLISH), Markt.AKTIEN)
        assertEquals(Urteil.ZU_WENIG, e.urteil)
        // Ein einzelnes Signal fuer einen Nebenmarkt reicht ebenfalls nicht
        val g = Ausblickgesamt.berechnen(listOf(b(Fruehindikatoren.DUERRE, Richtung.BULLISH, Tendenz.GETREIDE)), Markt.GETREIDE)
        assertEquals(Urteil.ZU_WENIG, g.urteil)
        assertEquals(Urteil.ZU_WENIG, Ausblickgesamt.berechnen(emptyList(), Markt.AKTIEN).urteil)
    }

    @Test
    fun doppeltZaehlendeSignaleWerdenZusammengefasst() {
        // Futures, Zinswette und Jahreswette sind eine Grundlage, Bahnzahlen aus zwei Quellen ebenso
        val l = listOf(
            b("zins", Richtung.BULLISH), b("fed", Richtung.BULLISH), b("fed_jahr", Richtung.BULLISH),
        ) + (1..9).map { b("aar", Richtung.BEARISH) }
        val u = Ausblickgesamt.einheiten(l)
        assertEquals(2, u.size)
        assertEquals(3, u.first { it.schluessel == "zins" }.mitglieder)
        assertEquals(1.2, u.first { it.schluessel == "zins" }.gewicht, 1e-9)
        assertEquals(0.5, u.first { it.schluessel == "aar" }.gewicht, 1e-9)
    }

    @Test
    fun aeltereDatenZaehlenWeniger() {
        assertEquals(1.0, Ausblickgesamt.frische(10, 7), 1e-9)
        assertEquals(0.4, Ausblickgesamt.frische(60, 7), 1e-9)
        assertTrue(Ausblickgesamt.frische(20, 7) in 0.4..1.0)
        assertEquals(1.0, Ausblickgesamt.frische(50, 45), 1e-9)
        assertTrue(Ausblickgesamt.gewicht(b("zins", Richtung.BULLISH, alter = 40)) < Ausblickgesamt.gewicht(b("zins", Richtung.BULLISH)))
    }

    @Test
    fun zeithorizontFiltertSignale() {
        val l = aktien(Richtung.BULLISH, Richtung.BULLISH, Richtung.BULLISH, Richtung.BULLISH, Richtung.BULLISH, Richtung.BULLISH)
        val kurz = Ausblickgesamt.einheiten(l).filter { Horizont.KURZ in it.horizonte }.map { it.schluessel }
        // Rezession und Hormus wirken nur ueber Monate
        assertTrue("rezession" !in kurz && "hormus" !in kurz && "zins" in kurz)
    }

    @Test
    fun maerkteBleibenGetrennt() {
        val l = aktien(Richtung.BEARISH, Richtung.BEARISH, Richtung.BEARISH, Richtung.BEARISH, Richtung.BEARISH, Richtung.BEARISH) +
            listOf(b(Fruehindikatoren.ROHOEL, Richtung.BULLISH, Tendenz.OEL), b("hormus", Richtung.BULLISH, Tendenz.OEL))
        assertEquals(Urteil.AUFWAERTS, Ausblickgesamt.berechnen(l, Markt.OEL).urteil)
        assertEquals(Urteil.ABWAERTS, Ausblickgesamt.berechnen(l, Markt.AKTIEN).urteil)
        // Wahlen ohne Marktrichtung fallen weg
        assertEquals(0, Ausblickgesamt.einheiten(listOf(b("wahlen", Richtung.BULLISH, "sonstiges"))).size)
    }

    @Test
    fun richtungUndGewichtsanteileStimmenUeberein() {
        // Zwei schwere Signale (Zins 1,2, Rezession 1,2) positiv, drei leichte negativ: mehr Gewicht dafuer als dagegen
        val l = listOf(
            b("zins", Richtung.BULLISH), b("rezession", Richtung.BULLISH),
            b(Ersatzquellen.LIEFERKETTEN, Richtung.BEARISH), b("inflation", Richtung.BEARISH), b("hormus", Richtung.BEARISH),
        )
        val e = Ausblickgesamt.berechnen(l, Markt.AKTIEN, Horizont.MITTEL)
        assertTrue(e.prozentPositiv > e.prozentNegativ)
        assertEquals(100, e.prozentPositiv + e.prozentNeutral + e.prozentNegativ)
        assertTrue(e.urteil != Urteil.ABWAERTS)
    }

    @Test
    fun uneinheitlichSchonBeiPunktzahlUnterGrenze() {
        // Punktzahl 0,25 mit beiden Seiten ueber einem Viertel des Gewichts: uneinheitlich statt "eher aufwaerts"
        val e = Ausblickgesamt.auswerten(
            listOf(
                einheit("a", 0.9, 1.0), einheit("b", 0.9, 1.0), einheit("c", -0.4, 1.0), einheit("d", -0.4, 1.0), einheit("e", 0.3, 0.2),
            ),
            Markt.AKTIEN,
        )
        assertTrue(e.punktzahl in 0.2..0.35)
        assertEquals(Urteil.UNEINHEITLICH, e.urteil)
    }

    @Test
    fun richtungGegenDasGewichtIstUneinheitlich() {
        // Positive wiegen weniger als Negative, die Punktzahl bleibt knapp positiv (Werte +1 gegen -0,3): keine Richtung nach oben
        val e = Ausblickgesamt.auswerten(
            listOf(einheit("a", 1.0, 1.0), einheit("b", -0.3, 1.0), einheit("c", -0.3, 1.0), einheit("d", 0.0, 0.2)),
            Markt.AKTIEN,
        )
        assertTrue(e.gewichtNegativ > e.gewichtPositiv)
        assertTrue(e.urteil != Urteil.AUFWAERTS)
    }

    private fun einheit(k: String, wert: Double, gewicht: Double) =
        Ausblickgesamt.Einheit(k, k, Markt.AKTIEN, wert, gewicht, setOf(Horizont.KURZ, Horizont.MITTEL), "kern", 1)
}
