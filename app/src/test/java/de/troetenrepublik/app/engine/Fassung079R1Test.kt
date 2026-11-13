package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

// Depot-Check (0.79): betroffene Positionen kommen einzeln, der Satz zur Regel enthaelt keinen Namen mehr.
class Fassung079R1Test {
    private fun wert(name: String, w: Double, art: DepotCheck.Art, verlust: Double = 0.0, ter: Double? = null, vorher: Double? = null) =
        DepotCheck.Wert(name, w, art, "EUR", null, verlust, ter, vorher)

    @Test
    fun tiefeVerlusteNennenJedeBetroffenePosition() {
        val b = DepotCheck.pruefen(
            listOf(wert("A, Inc.", 100.0, DepotCheck.Art.AKTIE, -60.0), wert("B", 100.0, DepotCheck.Art.AKTIE, -80.0), wert("C", 100.0, DepotCheck.Art.AKTIE, 5.0)),
            0.0, 0.0, 0.0, 0, 0,
        ).first { it.regel == DepotCheck.Regel.VERLUSTE }
        assertEquals(listOf("B", "A, Inc."), b.namenListe)
        assertEquals(2, b.anzahl)
    }

    @Test
    fun teurerGewordeneFondsStehenEinzeln() {
        val b = DepotCheck.pruefen(
            listOf(wert("Fonds eins", 100.0, DepotCheck.Art.ETF, ter = 0.3, vorher = 0.2), wert("Fonds zwei", 100.0, DepotCheck.Art.ETF, ter = 0.2, vorher = 0.2)),
            0.0, 0.0, 0.0, 0, 0,
        ).first { it.regel == DepotCheck.Regel.TER_GESTIEGEN }
        assertEquals(listOf("Fonds eins"), b.namenListe)
    }
}
