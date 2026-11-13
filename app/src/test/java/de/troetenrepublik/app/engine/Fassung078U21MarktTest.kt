package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.MarktTexte
import de.troetenrepublik.app.ui.PortfolioTool
import de.troetenrepublik.app.ui.WERKZEUG_GRUPPEN
import de.troetenrepublik.app.ui.werkzeugFormen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Werkzeug „Markt schlagen“: hinterlegte Zahlen mit Quelle, Rechnungen, Texte in beiden Sprachen, Platz in der Liste.
class Fassung078U21MarktTest {
    private val m = MarktSchlagen

    @Test
    fun jedeZahlHatQuelleUndAdresse() {
        assertEquals(24, m.ZAHLEN.size)
        assertEquals(m.ZAHLEN.size, m.ZAHLEN.map { it.schluessel }.toSet().size)
        m.ZAHLEN.forEach { z ->
            assertTrue(z.schluessel, z.wert.isFinite() && z.wert > 0)
            assertTrue(z.schluessel, z.quelle in m.QUELLEN)
        }
        m.QUELLEN.forEach { q ->
            assertTrue(q.name, q.herausgeber.isNotBlank() && q.titel.isNotBlank() && q.stand.isNotBlank())
            assertTrue(q.name, q.jahr in 2000..2026)
            assertTrue(q.name, q.url.startsWith("https://") && !q.url.contains(' '))
            assertEquals(q.name, "${q.herausgeber}: ${q.titel} (${q.jahr})", q.name)
            // Keine Quelle ohne Zahl, ausser sie liefert einen Datensatz
            assertTrue(q.name, m.ZAHLEN.any { it.quelle == q } || q in m.DATENQUELLEN || q in m.AUSSAGEQUELLEN)
        }
        assertEquals(m.QUELLEN.size, m.QUELLEN.map { it.name }.toSet().size)
        assertEquals(m.QUELLEN.size, m.QUELLEN.map { it.url }.toSet().size)
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(m.STAND))
    }

    @Test
    fun jederBausteinHatQuellen() {
        assertEquals(9, MarktSchlagen.Baustein.entries.size)
        MarktSchlagen.Baustein.entries.forEach { b ->
            val q = m.quellen(b)
            assertTrue(b.name, q.isNotEmpty() && q.all { it in m.QUELLEN })
        }
        assertEquals(m.QUELLEN.toSet(), MarktSchlagen.Baustein.entries.flatMap { m.quellen(it) }.toSet())
    }

    @Test
    fun hinterlegteWerte() {
        assertEquals(7.6, m.FONDS_AKTIV.wert, 0.0)
        assertEquals(9.3, m.FONDS_ETF.wert, 0.0)
        assertEquals(9.3, m.FONDS_PASSIV.wert, 0.0)
        assertEquals(1.39, m.KOSTEN_AKTIV.wert, 0.0)
        assertEquals(0.26, m.KOSTEN_ETF.wert, 0.0)
        assertEquals(4.0, m.WENIGE_USA.wert, 0.0)
        assertEquals(55.2, m.SCHATZ_USA.wert, 0.0)
        assertEquals(57.4, m.SCHATZ_WELT.wert, 0.0)
        assertEquals(17.9, m.HAUSHALTE_MARKT.wert, 0.0)
        assertEquals(16.4, m.HAUSHALTE_SCHNITT.wert, 0.0)
        assertEquals(11.4, m.HAUSHALTE_VIEL.wert, 0.0)
        assertEquals(97.0, m.BRASILIEN_VERLUST.wert, 0.0)
        assertEquals(74.0, m.CFD_VON.wert, 0.0)
        assertEquals(89.0, m.CFD_BIS.wert, 0.0)
        assertTrue(m.CFD_VON.wert < m.CFD_BIS.wert)
        assertTrue(m.ANLEIHEN_AKTIV.wert > m.ANLEIHEN_PASSIV.wert)
    }

    @Test
    fun abstaende() {
        assertEquals(1.7, m.fondsAbstand(), 1e-9)
        assertEquals(6.5, m.haushalteAbstand(), 1e-9)
    }

    @Test
    fun kostenRechnung() {
        val ohne = m.kostenVerlauf(0.0)
        assertEquals(m.RECHNUNG_JAHRE + 1, ohne.size)
        assertEquals(m.RECHNUNG_START, ohne.first(), 0.0)
        assertEquals(10_000 * Math.pow(1.06, 30.0), ohne.last(), 1e-6)
        // Ein Jahr: Zuwachs, danach Kosten auf den Wert des Jahresendes
        assertEquals(1000 * 1.10 * 0.99, m.kostenVerlauf(1.0, start = 1000.0, bruttoProzent = 10.0, jahre = 1).last(), 1e-9)
        assertEquals(listOf(500.0), m.kostenVerlauf(1.0, start = 500.0, jahre = 0))
        val etf = m.kostenVerlauf(m.KOSTEN_ETF.wert)
        val aktiv = m.kostenVerlauf(m.KOSTEN_AKTIV.wert)
        assertEquals(10_000 * Math.pow(1.06 * 0.9974, 30.0), etf.last(), 1e-6)
        assertEquals(10_000 * Math.pow(1.06 * 0.9861, 30.0), aktiv.last(), 1e-6)
        // Jedes Jahr waechst der Abstand
        for (i in 1 until etf.size) assertTrue(etf[i] - aktiv[i] > etf[i - 1] - aktiv[i - 1])
        assertEquals(etf.last() - aktiv.last(), m.kostenLuecke(), 1e-9)
        assertEquals(m.kostenEndwert(m.KOSTEN_ETF.wert), etf.last(), 0.0)
        assertTrue(m.kostenLuecke() > 15_000 && m.kostenLuecke() < 16_000)
    }

    @Test
    fun punkteDesRasters() {
        assertEquals(4, m.punkte(m.WENIGE_USA.wert))
        assertEquals(1, m.punkte(m.TAIWAN_GRENZE.wert))
        assertEquals(97, m.punkte(m.BRASILIEN_VERLUST.wert))
        assertEquals(74, m.punkte(m.CFD_VON.wert))
        assertEquals(89, m.punkte(m.CFD_BIS.wert))
        assertEquals(0, m.punkte(-3.0))
        assertEquals(100, m.punkte(140.0))
        assertEquals(57, m.punkte(57.4))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(MarktTexte.schluessel(true), MarktTexte.schluessel(false))
        for (k in MarktTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = MarktTexte.text(d, k, listOf("1", "2", "3", "4"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{'))
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('…') || t.contains('?'))
        }
        for (b in MarktSchlagen.Baustein.entries) for (teil in listOf(".titel", ".satz")) {
            assertTrue(b.name + teil, b.name + teil in MarktTexte.schluessel(true))
        }
        assertEquals("1,7 Prozentpunkte", MarktTexte.text(true, "punkte", listOf("1,7")))
        assertEquals("74 to 89%", MarktTexte.text(false, "spanne", listOf("74", "89%")))
        assertEquals("Kosten über 30 Jahre", MarktTexte.text(true, "KOSTEN.titel", listOf("30")))
    }

    @Test
    fun werkzeugInDerListe() {
        val name = PortfolioTool.MARKT.name
        assertEquals(1, WERKZEUG_GRUPPEN.count { name in it.second })
        assertTrue(werkzeugFormen(name).isNotEmpty())
        assertEquals("Markt schlagen", De.ptTitle(name))
        assertEquals("Beating the market", En.ptTitle(name))
        for (s in listOf(De, En)) {
            assertTrue(s.ptSubtitle(name).isNotBlank())
            assertFalse(s.ptSubtitle(name).contains('?'))
        }
    }
}
