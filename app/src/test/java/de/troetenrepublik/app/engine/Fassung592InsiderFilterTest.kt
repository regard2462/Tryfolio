package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Test

// Fassung 5.92: Filter fuer die Liste der US-Insidergeschaefte. Firmen und Personen sind erfunden.
class Fassung592InsiderFilterTest {
    private fun g(k: String, r: String, kauf: Boolean, d: String, wert: Double, plan: Boolean = false) =
        SecInsider.Geschaeft(a = "$k-$d-$wert", k = k, e = k, p = "Person", r = r, kauf = kauf, d = d, wert = wert, plan = plan)

    private val liste = listOf(
        g("AAA", "S", true, "2026-10-22", 2_000_000.0),
        g("AAA", "A", true, "2026-10-01", 150_000.0),
        g("BBB", "F", false, "2026-09-01", 50_000.0, plan = true),
        g("CCC", "N", false, "2026-05-01", 20_000_000.0),
        g("DDD", "S", true, "2026-10-25", 80_000.0),
    )
    private val branche = mapOf("AAA" to "SOFTWARE", "BBB" to "BANKEN", "DDD" to "SOFTWARE")
    private val land = mapOf("DDD" to "NL")

    private fun f(a: InsiderFilter.Auswahl, cluster: Set<String> = emptySet()) =
        InsiderFilter.filtern(liste, a, "2026-10-28", { branche[it] }, { land[it] }, cluster).map { it.k + it.wert.toLong() }

    @Test fun richtungRolleGroesse() {
        assertEquals(5, f(InsiderFilter.Auswahl()).size)
        assertEquals(listOf("AAA2000000", "AAA150000", "DDD80000"), f(InsiderFilter.Auswahl(richtung = InsiderFilter.Richtung.KAEUFE)))
        assertEquals(listOf("AAA2000000", "DDD80000"), f(InsiderFilter.Auswahl(rollen = setOf(InsiderTrend.Rolle.SPITZE))))
        assertEquals(listOf("AAA2000000", "CCC20000000"), f(InsiderFilter.Auswahl(mindestens = 1_000_000.0)))
    }

    @Test fun zeitraumBrancheLandCluster() {
        assertEquals(listOf("AAA2000000", "DDD80000"), f(InsiderFilter.Auswahl(tage = 7)))
        assertEquals(listOf("AAA2000000", "AAA150000", "BBB50000", "DDD80000"), f(InsiderFilter.Auswahl(tage = 90)))
        assertEquals(listOf("AAA2000000", "AAA150000", "DDD80000"), f(InsiderFilter.Auswahl(branche = "SOFTWARE")))
        // Ohne Eintrag im Katalog gilt ein US-Kuerzel als US
        assertEquals(listOf("DDD80000"), f(InsiderFilter.Auswahl(land = "NL")))
        assertEquals(4, f(InsiderFilter.Auswahl(land = "US")).size)
        assertEquals(listOf("AAA2000000", "AAA150000"), f(InsiderFilter.Auswahl(nurCluster = true), setOf("AAA")))
        assertEquals(4, f(InsiderFilter.Auswahl(ohnePlan = true)).size)
    }

    @Test fun aktivUndHaeufigste() {
        assertEquals(0, InsiderFilter.Auswahl(richtung = InsiderFilter.Richtung.KAEUFE).aktiv)
        assertEquals(3, InsiderFilter.Auswahl(mindestens = 1.0, tage = 7, nurCluster = true).aktiv)
        assertEquals(listOf("SOFTWARE", "BANKEN"), InsiderFilter.haeufigste(liste) { branche[it] })
    }
}
