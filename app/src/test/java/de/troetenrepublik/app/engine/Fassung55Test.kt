package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Bafin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.5: Seiten der BaFin-Datenbanken lesen und auswerten (Ausschnitte im Aufbau der echten Seiten).
class Fassung55Test {
    private val suche = """
        <a href="ergebnisListe.do?cmd=loadEmittentenAction&amp;meldepflichtigerId=35042">2. vLB GmbH &amp; Co. KG</a>
        <a href="ergebnisListe.do?cmd=loadEmittentenAction&amp;meldepflichtigerId=35043">Utz</a>
        <a href="sucheForm.do?zeitraum=2&amp;d-5010980-p=2">2</a> <a href="sucheForm.do?zeitraum=2&amp;d-5010980-p=7">7</a>
    """

    private val person = """
        <table><tr><th>Emittent</th></tr>
        <tr class="odd">
        <td>
        			<a href="transaktionListe.do?cmd=loadTransaktionenAction&amp;emittentBafinId=40006808&amp;meldungId=35034&KeepThis=true" class="thickbox">EnviTec Biogas AG</a>
        		</td>
        <td>40006808</td>
        <td>DE000A0MVLS8</td>
        <td>2. vLB GmbH & Co. KG</td>
        <td>in enger Beziehung</td>
        <td>Aktie</td>
        <td>Kauf</td>
        <td>20.10.2026</td>
        <td>Tradegate</td>
        <td>22.10.2026 09:41:38</td></tr></table>
    """

    private val detail = """
        <tr height="10px"><td>Preis:</td>
        <td>17,11 EUR</td></tr>
        <tr height="10px"><td>Aggregiertes Volumen:</td>
        <td>174.549,99 EUR</td></tr>
    """

    @Test fun suchseiteLiefertKennungenUndSeiten() {
        assertEquals(listOf("35042", "35043"), Bafin.personen(suche))
        assertEquals(7, Bafin.letzteSeite(suche))
    }

    @Test fun meldungUndEinzelheiten() {
        val (emittent, g) = Bafin.meldungen(person, "35042").single()
        assertEquals("40006808", emittent)
        assertEquals("35034", g.id)
        assertEquals("35042", g.pid)
        assertEquals("DE000A0MVLS8", g.isin)
        assertEquals("EnviTec Biogas AG", g.e)
        assertEquals("2. vLB GmbH & Co. KG", g.p)
        assertTrue(g.kauf)
        assertEquals("2026-10-20", g.d)
        assertEquals("2026-10-22", g.v)
        val voll = Bafin.einzelheiten(detail, g)
        assertEquals(17.11, voll.preis!!, 1e-9)
        assertEquals(174549.99, voll.vol!!, 1e-6)
        assertEquals("EUR", voll.w)
        assertNull(Bafin.einzelheiten("<p>keine Angaben</p>", g).vol)
    }

    private fun g(id: String, isin: String, art: String, d: String, vol: Double, k: String? = null) =
        Bafin.Geschaeft(id = id, isin = isin, e = "Firma $isin", p = "P", i = "Aktie", art = art, d = d, vol = vol, w = "EUR", k = k)

    @Test fun vereinenBehaeltBekanntesUndSchneidetAb() {
        val alt = listOf(g("1", "DE1", "Kauf", "2026-10-01", 100.0, k = "AAA.DE"), g("2", "DE1", "Kauf", "2025-01-01", 5.0))
        val neu = listOf(g("1", "DE1", "Kauf", "2026-10-01", 100.0).copy(k = null), g("3", "DE2", "Verkauf", "2026-10-05", 50.0))
        val v = Bafin.vereinen(alt, neu, "2025-10-22")
        assertEquals(listOf("3", "1"), v.map { it.id })
        assertEquals("AAA.DE", v.last().k)
    }

    @Test fun firmenNachNettokaeufen() {
        val liste = listOf(
            g("1", "DE1", "Kauf", "2026-10-01", 100.0), g("2", "DE1", "Verkauf", "2026-10-02", 30.0),
            g("3", "DE2", "Verkauf", "2026-10-03", 500.0), g("4", "DE3", "Kauf", "2026-01-01", 900.0),
        )
        val f = Bafin.firmen(liste, "2026-07-24")
        assertEquals(listOf("DE1", "DE2"), f.map { it.isin })
        assertEquals(70.0, f[0].netto, 1e-9)
        assertEquals(1, f[0].anzahlKaeufe)
        assertEquals(-500.0, f[1].netto, 1e-9)
    }

    @Test fun grossaktionaereNachName() {
        val liste = listOf(
            Bafin.Anteil(e = "SAP SE", m = "BlackRock, Inc.", s = 5.2, summe = 6.1),
            Bafin.Anteil(e = "SAP SE", m = "Hasso Plattner", s = 6.9),
            Bafin.Anteil(e = "SAP Fiori AG", m = "X", s = 3.0),
        )
        assertEquals(listOf("Hasso Plattner", "BlackRock, Inc."), Bafin.anteileAn("SAP", liste).map { it.m })
        assertTrue(Bafin.anteileAn("Apple", liste).isEmpty())
    }

    @Test fun zahlenUndDaten() {
        assertEquals(1234.5 to "USD", Bafin.zahl("1.234,50 USD"))
        assertEquals("2026-01-31", Bafin.datum("31.01.2026 08:00:00"))
    }
}
