package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Gleichwerte
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Dieselbe Aktie an verschiedenen Plaetzen: gleicher Name und passender Euro-Kurs, sonst nicht.
class GleichwerteTest {

    @After fun aufraeumen() = Gleichwerte.leeren()

    @Test fun abgekuerzteNamenDeutscherPlaetzeZaehlenAlsGleich() {
        assertTrue(Gleichwerte.namenGleich("Grizzly Discoveries Inc.", "GRIZZLY DISCOVER.INC."))
        assertTrue(Gleichwerte.namenGleich("Alphamin Resources Corp.", "ALPHAMIN RES.CORP."))
        assertTrue(Gleichwerte.namenGleich("Novo Nordisk A/S", "NOVO-NORDISK AS B DK 0,1"))
        assertFalse(Gleichwerte.namenGleich("Castellum", "Castellum Bio"))
        assertFalse(Gleichwerte.namenGleich("Apple Inc.", "Applied Materials Inc."))
    }

    @Test fun gleicheNotierungenBildenEineGruppe() {
        val treffer = listOf(
            "GZD.V" to "Grizzly Discoveries Inc.",
            "GZDIF" to "Grizzly Discoveries Inc",
            "G6H.F" to "GRIZZLY DISCOVER.INC.",
            "G6H.SG" to "Grizzly Discoveries Inc.",
        )
        val kurse = mapOf("GZD.V" to 0.031, "GZDIF" to 0.029, "G6H.F" to 0.03, "G6H.SG" to 0.034)
        val g = Gleichwerte.gruppen(treffer) { kurse[it] }
        assertEquals(listOf(setOf("GZD.V", "GZDIF", "G6H.F", "G6H.SG")), g)
    }

    @Test fun gleichnamigeFirmenMitAnderemKursBleibenGetrennt() {
        // Castellum Inc. (Cyberabwehr, New York) und Castellum AB (Immobilien, Stockholm).
        val treffer = listOf("CTM" to "Castellum, Inc.", "CAST.ST" to "Castellum AB (publ)", "CWQ.F" to "CASTELLUM AB")
        val kurse = mapOf("CTM" to 0.95, "CAST.ST" to 10.8, "CWQ.F" to 10.7)
        assertEquals(listOf(setOf("CAST.ST", "CWQ.F")), Gleichwerte.gruppen(treffer) { kurse[it] })
    }

    @Test fun ohneKursKeineGruppe() {
        val treffer = listOf("AAA" to "Beispiel Werke AG", "AAA.F" to "BEISPIEL WERKE AG")
        assertTrue(Gleichwerte.gruppen(treffer) { null }.isEmpty())
    }

    @Test fun verzoegerteNotierungNimmtDenJuengerenKurs() {
        val t = 1_790_000_000_000L
        val xetra = de.troetenrepublik.app.data.Quote("NOV.DE", price = 34.75, prevClose = 34.0, nativeCurrency = "EUR", nativePrice = 34.75, marketTime = t - 15 * 60_000L)
        val heimat = de.troetenrepublik.app.data.Quote("NOVO-B.CO", price = 35.0, prevClose = 34.2, nativeCurrency = "DKK", nativePrice = 261.2, exchange = "CPH", marketState = "REGULAR", marketTime = t)
        val q = de.troetenrepublik.app.market.Zweitnotiz.frischer(xetra, listOf(heimat))
        assertEquals(35.0, q.price, 1e-9)
        assertEquals(34.0, q.prevClose, 1e-9)
        assertEquals("CPH", q.secondaryExchange)
        // Kaum juenger oder ein Fehlkurs: der eigene Kurs bleibt.
        val knapp = heimat.copy(marketTime = xetra.marketTime + 60_000L)
        assertEquals(34.75, de.troetenrepublik.app.market.Zweitnotiz.frischer(xetra, listOf(knapp)).price, 1e-9)
        val falsch = heimat.copy(price = 60.0)
        assertEquals(34.75, de.troetenrepublik.app.market.Zweitnotiz.frischer(xetra, listOf(falsch)).price, 1e-9)
        assertTrue(de.troetenrepublik.app.market.Zweitnotiz.verzoegert("NOV.DE"))
        assertFalse(de.troetenrepublik.app.market.Zweitnotiz.verzoegert("NOVO-B.CO"))
    }

    @Test fun familienWachsenZusammen() {
        val abgelegt = ArrayList<Set<String>>()
        Gleichwerte.ablage = { abgelegt += it }
        Gleichwerte.verbinden(setOf("NOVO-B.CO", "NOV.F"), merken = false)
        Gleichwerte.verbinden(setOf("NOV.F", "NVO"))
        assertEquals(setOf("NOVO-B.CO", "NOV.F", "NVO"), Gleichwerte.familien.value["NOVO-B.CO"])
        assertEquals(1, abgelegt.size)
        Gleichwerte.verbinden(setOf("NVO", "NOVO-B.CO"))
        assertEquals(1, abgelegt.size)
        Gleichwerte.ablage = null
    }
}
