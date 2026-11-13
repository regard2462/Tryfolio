package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.DepotZusatzTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Fassung 0.78 U40: Tabelle der groessten Rueckgaenge, Aufwaerts- und Abwaertsanteil, Omega, Gewinn zu Schmerz.
class Fassung078U40RenditeprofilTest {

    private fun t(d: String) = LocalDate.parse(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun rueckgaengeMitBeginnTiefErholung() {
        val reihe = listOf(
            t("2024-01-01") to 100.0, t("2024-01-10") to 110.0, t("2024-01-20") to 88.0, t("2024-02-09") to 99.0, t("2024-03-01") to 110.0,
            t("2024-03-11") to 120.0, t("2024-03-21") to 114.0, t("2024-03-31") to 108.0,
        )
        val r = Renditeprofil.rueckgaenge(reihe)
        assertEquals(2, r.size)
        // Tiefster zuerst: 110 auf 88, erholt am 1. Maerz
        assertEquals(t("2024-01-10"), r[0].beginn)
        assertEquals(t("2024-01-20"), r[0].tief)
        assertEquals(t("2024-03-01"), r[0].erholung)
        assertEquals(0.2, r[0].tiefe, 1e-9)
        assertEquals(10L, r[0].tageBisTief)
        assertEquals(41L, r[0].tageBisErholung)
        assertEquals(51L, r[0].tageGesamt(reihe.last().first))
        // Laufender Rueckgang ohne Erholung
        assertEquals(t("2024-03-11"), r[1].beginn)
        assertEquals(t("2024-03-31"), r[1].tief)
        assertNull(r[1].erholung)
        assertEquals(0.1, r[1].tiefe, 1e-9)
        assertEquals(20L, r[1].tageGesamt(reihe.last().first))
    }

    @Test
    fun kleineRueckgaengeUndAnzahl() {
        val flach = listOf(t("2024-01-01") to 100.0, t("2024-01-02") to 99.5, t("2024-01-03") to 101.0)
        assertTrue(Renditeprofil.rueckgaenge(flach).isEmpty())
        assertTrue(Renditeprofil.rueckgaenge(listOf(t("2024-01-01") to 100.0)).isEmpty())
        // Sieben Rueckgaenge wachsender Tiefe, nur die fuenf tiefsten bleiben
        val viele = ArrayList<Pair<Long, Double>>()
        var tag = t("2024-01-01")
        var hoch = 100.0
        for (i in 1..7) {
            viele += tag to hoch; tag += 86_400_000L
            viele += tag to hoch * (1 - 0.02 * i); tag += 86_400_000L
            hoch += 1
        }
        viele += tag to hoch
        val r = Renditeprofil.rueckgaenge(viele)
        assertEquals(5, r.size)
        assertEquals(0.14, r.first().tiefe, 1e-9)
        assertEquals(0.06, r.last().tiefe, 1e-9)
    }

    @Test
    fun monatsPaareSchneidenAnMonatsgrenzen() {
        val depot = listOf(
            t("2024-01-15") to 1.0, t("2024-01-31") to 1.1, t("2024-02-15") to 1.0, t("2024-02-29") to 1.21, t("2024-03-10") to 1.089,
        )
        val markt = listOf(t("2024-01-12") to 50.0, t("2024-01-31") to 55.0, t("2024-02-29") to 49.5, t("2024-03-08") to 54.45)
        val p = Renditeprofil.monatsPaare(depot, markt, ZoneOffset.UTC)
        assertEquals(3, p.size)
        assertEquals(0.1, p[0].first, 1e-9); assertEquals(0.1, p[0].second, 1e-9)
        assertEquals(0.1, p[1].first, 1e-9); assertEquals(-0.1, p[1].second, 1e-9)
        assertEquals(-0.1, p[2].first, 1e-9); assertEquals(0.1, p[2].second, 1e-9)
        // Ohne Vergleichskurs vor dem Beginn fehlt der Abschnitt
        val spaet = listOf(t("2024-02-10") to 50.0, t("2024-02-29") to 55.0, t("2024-03-08") to 66.0)
        assertEquals(1, Renditeprofil.monatsPaare(depot, spaet, ZoneOffset.UTC).size)
        assertTrue(Renditeprofil.monatsPaare(depot, emptyList(), ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun anteileOmegaSchmerz() {
        // Depot macht die Haelfte jeder Indexbewegung mit
        val paare = listOf(0.02 to 0.04, 0.01 to 0.02, 0.03 to 0.06, -0.01 to -0.02, -0.02 to -0.04, -0.005 to -0.01)
        val a = Renditeprofil.anteile(paare)
        assertEquals(3, a.monateAuf); assertEquals(3, a.monateAb)
        assertEquals(0.5, a.aufwaerts!!, 0.01)
        assertEquals(0.5, a.abwaerts!!, 0.01)
        // Zu wenige fallende Monate
        assertNull(Renditeprofil.anteile(paare.take(4)).abwaerts)
        val r = paare.map { it.first }
        assertEquals(0.06 / 0.035, Renditeprofil.omega(r)!!, 1e-9)
        assertEquals(0.025 / 0.035, Renditeprofil.gewinnZuSchmerz(r)!!, 1e-9)
        assertNull(Renditeprofil.omega(r.take(3)))
        assertNull(Renditeprofil.omega(listOf(0.01, 0.02, 0.03, 0.01, 0.02, 0.03)))
        assertNull(Renditeprofil.gewinnZuSchmerz(listOf(0.01, 0.02, 0.03, 0.01, 0.02, 0.03)))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(DepotZusatzTexte.schluessel(true), DepotZusatzTexte.schluessel(false))
        val verboten = listOf("—", "–", "…", "▲", "▼", "bullisch", "bärisch", "bullish", "bearish")
        for (k in DepotZusatzTexte.schluessel(true)) for (d in listOf(true, false)) {
            val text = DepotZusatzTexte.text(d, k, listOf("1", "2", "3", "4", "5"))
            assertTrue(k, text.isNotBlank())
            assertFalse(k, text.contains("{"))
            verboten.forEach { assertFalse("$k: $it", text.contains(it)) }
        }
    }
}
