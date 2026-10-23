package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.TruthNews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Stochastik, Depotfinder, Optionsanalyse und die Markierung der Trump-Beitraege.
class Fassung44Test {

    @Test
    fun stochastikAmHochUndTief() {
        // Steigt stetig: Schluss am Hoch der Spanne, %K bei 100
        val schluss = (0 until 40).map { 100.0 + it }
        val s = Indikatoren.stochastik(schluss.map { it + 0.0 }, schluss.map { it - 5 }, schluss)
        assertNull(s.k[12])
        assertEquals(100.0, s.k.last()!!, 1e-9)
        assertEquals(100.0, s.d.last()!!, 1e-9)
        // Faellt stetig: Schluss am Tief, %K bei 0, Deutung ueberverkauft
        val fallend = (0 until 60).map { 200.0 - it }
        val f = Indikatoren.stochastik(fallend.map { it + 5 }, fallend, fallend)
        assertEquals(0.0, f.k.last()!!, 1e-9)
        val d = Indikatoren.deuten(fallend.indices.map { 20_000L + it }, fallend, fallend.map { it + 5 }, fallend)
        val st = d.firstOrNull { it.art == "stoch" }
        assertNotNull(st)
        assertEquals("ueberverkauft", st!!.lage)
    }

    private fun kandidat(s: String, kgv: Double?, eps: Double?, epsF: Double?, buch: Double?, div: Double?, steigung: Double) =
        Depotfinder.Kandidat(s, s, kgv, kgv?.let { it / 5 }, eps, epsF, buch, div, (0 until 252).map { 100.0 * (1 + steigung * it) + if (it % 2 == 0) 1.0 else -1.0 })

    @Test
    fun depotfinderOrdnetNachFaktoren() {
        val liste = listOf(
            kandidat("BILLIG", 8.0, 5.0, 5.5, 40.0, 0.04, 0.0005),
            kandidat("MITTEL", 20.0, 3.0, 3.6, 20.0, 0.02, 0.001),
            kandidat("TEUER", 60.0, 1.0, 1.5, 5.0, null, 0.003),
        )
        val r = Depotfinder.raenge(liste)
        assertEquals(1.0, r.getValue("BILLIG").getValue(Depotfinder.Faktor.VALUE), 1e-9)
        assertEquals(0.0, r.getValue("TEUER").getValue(Depotfinder.Faktor.VALUE), 1e-9)
        assertEquals(1.0, r.getValue("TEUER").getValue(Depotfinder.Faktor.MOMENTUM), 1e-9)
        // fehlende Dividende zaehlt als Mitte
        assertEquals(0.5, r.getValue("TEUER").getValue(Depotfinder.Faktor.DIVIDENDE), 1e-9)
        val value = Depotfinder.finden(liste, mapOf(Depotfinder.Faktor.VALUE to 1.0), 2)
        assertEquals(listOf("BILLIG", "MITTEL"), value.treffer.map { it.symbol })
        assertNotNull(value.depotVolatilitaet)
        val momentum = Depotfinder.finden(liste, mapOf(Depotfinder.Faktor.MOMENTUM to 1.0), 1)
        assertEquals("TEUER", momentum.treffer.single().symbol)
        // Mindestscore filtert
        assertTrue(Depotfinder.finden(liste, mapOf(Depotfinder.Faktor.VALUE to 1.0), 3, 60.0).treffer.all { it.score >= 60 })
    }

    @Test
    fun optionsanalyse() {
        val jetzt = 1_800_000_000L
        val verfall = jetzt + 10 * 86_400
        fun k(call: Boolean, strike: Double, preis: Double, umsatz: Long, offen: Long, letzter: Double = preis) =
            Optionsanalyse.Kontrakt(call, strike, verfall, letzter, preis - 0.05, preis + 0.05, umsatz, offen, 0.3)
        val kette = listOf(
            k(true, 90.0, 10.5, 100, 1000), k(true, 100.0, 3.0, 300, 2000), k(true, 110.0, 0.5, 50, 4000),
            k(false, 90.0, 0.4, 80, 3000), k(false, 100.0, 2.8, 150, 1500), k(false, 110.0, 10.2, 20, 500),
            // grosser neuer Call, zum Brief gekauft
            Optionsanalyse.Kontrakt(true, 105.0, verfall, 1.55, 1.45, 1.55, 6000, 300, 0.32),
        )
        val e = Optionsanalyse.auswerten(100.0, kette, jetzt)
        assertEquals(250.0 / 6450.0, e.pcrUmsatz!!, 1e-9)
        assertEquals(5.8, e.bewegung!!, 1e-9)
        assertEquals(verfall, e.bewegungVerfall)
        assertEquals(0.3, e.ivAmGeld!!, 1e-9)
        assertNotNull(e.maxPain)
        val a = e.auffaellig.single()
        assertEquals(105.0, a.kontrakt.strike, 1e-9)
        assertEquals(Optionsanalyse.Seite.KAUF, a.seite)
        // Max Pain: viele offene Calls bei 90 und Puts bei 110 ziehen zur Mitte
        val schmerz = listOf(k(true, 90.0, 1.0, 0, 1000), k(true, 100.0, 1.0, 0, 10), k(false, 100.0, 1.0, 0, 10), k(false, 110.0, 1.0, 0, 1000))
        assertEquals(100.0, Optionsanalyse.maxPain(schmerz)!!, 1e-9)
    }

    @Test
    fun trumpNurWichtigesMarkiert() {
        assertTrue(TruthNews.themen("We will put tariffs of 50% on all steel coming from China.", emptyList()).isNotEmpty())
        assertTrue(TruthNews.themen("Happy Birthday to a great American patriot!", emptyList()).isEmpty())
    }
}
