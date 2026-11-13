package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Diversifizieren.Wert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Depotverlauf nach Quartal und Jahr, Zielgewichte fuers Diversifizieren.
class Fassung076RTest {

    private val tag = Depotzerlegung.TAG

    private fun summe(m: Map<String, Double>) = m.values.sum()

    @Test fun quartaleUndJahre() {
        // 15.3.2025 (Q1), 15.5.2025 und 20.6.2025 (Q2), 5.1.2026 (Q1 2026)
        val daten = listOf(
            java.time.LocalDate.of(2025, 3, 15) to 1.0, java.time.LocalDate.of(2025, 5, 15) to 2.0,
            java.time.LocalDate.of(2025, 6, 20) to 3.0, java.time.LocalDate.of(2026, 1, 5) to 4.0,
        )
        val tage = daten.map { (d, v) -> Depotzerlegung.Tag(d.toEpochDay() * tag, mapOf("A" to v), 100.0) }
        val q = Depotzerlegung.abschnitte(tage, Depotzerlegung.Raster.QUARTAL)
        assertEquals(listOf(1.0, 5.0, 4.0), q.map { it.summe })
        val j = Depotzerlegung.abschnitte(tage, Depotzerlegung.Raster.JAHR)
        assertEquals(listOf(6.0, 4.0), j.map { it.summe })
        assertEquals(java.time.LocalDate.of(2025, 3, 15).toEpochDay() * tag, j[0].beginn)
        // Quartalsgrenze: 31.12. und 1.1. liegen in verschiedenen Quartalen und Jahren
        val grenze = listOf(java.time.LocalDate.of(2025, 12, 31), java.time.LocalDate.of(2026, 1, 1))
            .map { Depotzerlegung.Tag(it.toEpochDay() * tag, mapOf("A" to 1.0), 100.0) }
        assertEquals(2, Depotzerlegung.abschnitte(grenze, Depotzerlegung.Raster.QUARTAL).size)
        assertEquals(2, Depotzerlegung.abschnitte(grenze, Depotzerlegung.Raster.JAHR).size)
    }

    @Test fun gleichVerteilt() {
        val g = Diversifizieren.gleich(listOf(Wert("a", 10.0), Wert("b", 90.0), Wert("c", 0.0)))
        assertEquals(setOf("a", "b"), g.keys)
        assertEquals(0.5, g.getValue("a"), 1e-12)
        assertEquals(1.0, summe(g), 1e-12)
    }

    @Test fun brancheGleichVielImHeutigenVerhaeltnis() {
        val w = listOf(
            Wert("a", 30.0, branche = "Tech"), Wert("b", 10.0, branche = "Tech"),
            Wert("c", 50.0, branche = "Bank"), Wert("d", 10.0, branche = null),
        )
        val g = Diversifizieren.branchen(w)
        assertEquals(1.0, summe(g), 1e-12)
        // drei Gruppen mit je einem Drittel
        assertEquals(1.0 / 3 * 0.75, g.getValue("a"), 1e-12)
        assertEquals(1.0 / 3 * 0.25, g.getValue("b"), 1e-12)
        assertEquals(1.0 / 3, g.getValue("c"), 1e-12)
        assertEquals(1.0 / 3, g.getValue("d"), 1e-12)
    }

    @Test fun hoechstensKapptUndVerteiltDenRest() {
        val w = listOf(Wert("a", 60.0), Wert("b", 25.0), Wert("c", 10.0), Wert("d", 5.0))
        val g = Diversifizieren.hoechstens(w, 0.40)
        assertEquals(1.0, summe(g), 1e-9)
        assertEquals(0.40, g.getValue("a"), 1e-9)
        // b, c, d teilen 60 % im Verhaeltnis 25 : 10 : 5, dabei darf b nicht ueber 40 % steigen
        assertEquals(0.60 * 25 / 40, g.getValue("b"), 1e-9)
        assertTrue(g.values.all { it <= 0.40 + 1e-9 })
    }

    @Test fun hoechstensKettenreaktion() {
        // Nach dem Kappen von a rutscht b ueber die Grenze und wird ebenfalls gekappt
        val w = listOf(Wert("a", 70.0), Wert("b", 20.0), Wert("c", 5.0), Wert("d", 5.0))
        val g = Diversifizieren.hoechstens(w, 0.30)
        assertEquals(1.0, summe(g), 1e-9)
        assertTrue(g.values.all { it <= 0.30 + 1e-9 })
        assertEquals(0.30, g.getValue("a"), 1e-9)
        assertEquals(0.30, g.getValue("b"), 1e-9)
        assertEquals(0.20, g.getValue("c"), 1e-9)
    }

    @Test fun hoechstensZuWenigPositionenGleich() {
        val g = Diversifizieren.hoechstens(listOf(Wert("a", 80.0), Wert("b", 20.0)), 0.20)
        assertEquals(0.5, g.getValue("a"), 1e-12)
        assertEquals(1.0, summe(g), 1e-12)
        // Grenze ueber dem Maximum aendert nichts
        val h = Diversifizieren.hoechstens(listOf(Wert("a", 80.0), Wert("b", 20.0)), 0.90)
        assertEquals(0.8, h.getValue("a"), 1e-12)
    }

    @Test fun laenderNachBip() {
        // USA 60 %, Deutschland 20 %, Japan 20 % des Depots; BIP-Anteile 50 : 30 : 20, Frankreich ohne Position
        val bip = mapOf("US" to 0.25, "DE" to 0.05, "JP" to 0.04, "FR" to 0.04)
        val w = listOf(
            Wert("u1", 40.0, mapOf("US" to 1.0)), Wert("u2", 20.0, mapOf("US" to 1.0)),
            Wert("d", 20.0, mapOf("DE" to 1.0)), Wert("j", 20.0, mapOf("JP" to 1.0)),
        )
        val g = Diversifizieren.laenderBip(w, bip)!!
        assertEquals(1.0, summe(g), 1e-9)
        val ziel = 0.25 + 0.05 + 0.04
        assertEquals((0.25 / ziel), g.getValue("u1") + g.getValue("u2"), 1e-6)
        assertEquals(0.05 / ziel, g.getValue("d"), 1e-6)
        assertEquals(0.04 / ziel, g.getValue("j"), 1e-6)
        // innerhalb eines Landes im heutigen Verhaeltnis
        assertEquals(2.0, g.getValue("u1") / g.getValue("u2"), 1e-6)
    }

    @Test fun laenderMitFondsUndFesterPosition() {
        val bip = mapOf("US" to 0.5, "DE" to 0.3, "JP" to 0.2)
        val w = listOf(
            Wert("us", 50.0, mapOf("US" to 1.0)),
            Wert("welt", 30.0, mapOf("US" to 0.6, "DE" to 0.2, "JP" to 0.2)),
            Wert("de", 10.0, mapOf("DE" to 1.0)),
            Wert("krypto", 10.0),
        )
        val g = Diversifizieren.laenderBip(w, bip)!!
        assertEquals(1.0, summe(g), 1e-9)
        // Position ohne Land behaelt ihren Anteil
        assertEquals(0.10, g.getValue("krypto"), 1e-12)
        val vorher = Diversifizieren.laenderAbstand(w.filter { it.key != "krypto" }, mapOf("us" to 0.5, "welt" to 0.3, "de" to 0.1), bip)
        val nachher = Diversifizieren.laenderAbstand(w, g, bip)
        assertTrue("Abstand sinkt: $vorher -> $nachher", nachher < vorher)
        assertTrue(g.values.all { it >= 0.0 })
    }

    @Test fun laenderOhneZuordnungGibtNull() {
        assertEquals(null, Diversifizieren.laenderBip(listOf(Wert("a", 1.0), Wert("b", 2.0)), mapOf("US" to 1.0)))
        assertEquals(null, Diversifizieren.laenderBip(listOf(Wert("a", 1.0, mapOf("XX" to 1.0))), mapOf("US" to 1.0)))
    }
}
