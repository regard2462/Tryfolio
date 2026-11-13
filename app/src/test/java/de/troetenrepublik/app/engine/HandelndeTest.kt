package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.Kongress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Wer einen Wert handelt: Richtung je Person, Investoren nach dem Quartalsvergleich, Tendenz.
class HandelndeTest {

    private val tag = 86_400_000L
    private val jetzt = 1_790_000_000_000L

    private fun g(k: String, art: Kongress.Art, vorTagen: Int, von: Long, bis: Long, typ: String = "ST") =
        Kongress.Geschaeft("", "Beispiel AG", k, typ, art, jetzt - vorTagen * tag, jetzt, von, bis)

    @Test fun richtungNachSummeUndNurPassendeKuerzel() {
        val personen = listOf(
            Handelnde.Person(Handelnde.Gruppe.HAUS, "a", "Person A", listOf(
                g("XYZ", Kongress.Art.KAUF, 10, 1_001, 15_000),
                g("XYZ", Kongress.Art.VERKAUF, 20, 1_001, 15_000),
                g("XYZ", Kongress.Art.KAUF, 30, 50_001, 100_000),
                g("ABC", Kongress.Art.VERKAUF, 5, 1_000_001, 5_000_000),
            )),
            Handelnde.Person(Handelnde.Gruppe.SENAT, "b", "Person B", listOf(
                g("XYZ", Kongress.Art.TEILVERKAUF, 40, 15_001, 50_000),
                // Optionen und alte Geschaefte zaehlen nicht
                g("XYZ", Kongress.Art.KAUF, 41, 1_000_001, 5_000_000, typ = "OP"),
                g("XYZ", Kongress.Art.KAUF, 400, 1_000_001, 5_000_000),
            )),
            Handelnde.Person(Handelnde.Gruppe.HAUS, "c", "Person C", listOf(g("ABC", Kongress.Art.KAUF, 3, 1_001, 15_000))),
        )
        val e = Handelnde.geschaefte(personen, { it == "XYZ" }, jetzt)
        assertEquals(listOf("a", "b"), e.map { it.schluessel })
        assertEquals(Handelnde.Richtung.KAUF, e[0].richtung)
        assertEquals(2, e[0].kaeufe)
        assertEquals(1, e[0].verkaeufe)
        assertEquals(Handelnde.Richtung.VERKAUF, e[1].richtung)
        assertEquals(1, e[1].verkaeufe)
    }

    @Test fun investorenNachQuartalsvergleich() {
        fun pos(k: String, stueck: Double) = Investoren.Position("Beispiel", k, "C$k", stueck * 10, stueck)
        fun inv(id: String, jetzt: List<Investoren.Position>, vorher: List<Investoren.Position>) = Investoren.Investor(
            id, id, "Fonds $id", "1", "", "",
            listOf(Investoren.Quartal("2026-06-30", "2026-08-14", 0.0, jetzt + pos("ZZZ", 100.0)), Investoren.Quartal("2026-03-31", "2026-05-15", 0.0, vorher + pos("ZZZ", 100.0))),
        )
        val liste = listOf(
            inv("neu", listOf(pos("XYZ", 10.0)), emptyList()),
            inv("mehr", listOf(pos("XYZ", 20.0)), listOf(pos("XYZ", 10.0))),
            inv("weg", emptyList(), listOf(pos("XYZ", 10.0))),
            inv("gleich", listOf(pos("XYZ", 10.0)), listOf(pos("XYZ", 10.0))),
            inv("ohne", emptyList(), emptyList()),
        )
        val e = Handelnde.investoren(liste) { it == "XYZ" }.associateBy { it.schluessel }
        assertEquals(setOf("neu", "mehr", "weg", "gleich"), e.keys)
        assertEquals(Investoren.Wandel.NEU, e["neu"]!!.wandel)
        assertEquals(Investoren.Wandel.ERHOEHT, e["mehr"]!!.wandel)
        assertEquals(Investoren.Wandel.VERKAUFT, e["weg"]!!.wandel)
        assertEquals(Handelnde.Richtung.HALTEN, e["gleich"]!!.richtung)
        val b = Handelnde.bild(e.values.toList())
        assertEquals(2, b.kaeufer)
        assertEquals(1, b.verkaeufer)
        assertEquals(Handelnde.Richtung.KAUF, b.tendenz)
    }

    @Test fun ohneKaeuferUndVerkaeuferKeineTendenz() {
        assertNull(Handelnde.Bild(0, 0, 3, 0).tendenz)
        assertEquals(Handelnde.Richtung.GEMISCHT, Handelnde.Bild(3, 3, 0, 0).tendenz)
        assertEquals(Handelnde.Richtung.VERKAUF, Handelnde.Bild(1, 2, 0, 0).tendenz)
    }
}
