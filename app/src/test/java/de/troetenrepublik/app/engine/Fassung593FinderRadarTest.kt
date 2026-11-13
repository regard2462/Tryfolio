package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.Aktienfinder.Daten
import de.troetenrepublik.app.engine.Radar.Art
import de.troetenrepublik.app.engine.Radar.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: Radar im Aktienfinder. Kennzeichen „Im Gespraech“, Filtern der Treffer und der Beweis, dass
// Aufmerksamkeit die Punkte des Aktienfinders nicht aendert.
class Fassung593FinderRadarTest {

    private val heute = 20_000L

    private fun treffer(symbol: String, vararg signale: Signal, bekannt: Boolean = false) =
        Radar.Treffer(symbol, signale.toList(), 50.0, heute, signale.size, bekannt)

    @Test fun imGespraechBeiAufmerksamkeit() {
        assertTrue(Radar.imGespraech(treffer("A", Signal("A", Art.ERWAEHNUNGEN, heute, 0.5))))
        assertTrue(Radar.imGespraech(treffer("B", Signal("B", Art.WIKIPEDIA, heute, 0.5))))
        // Breit bekannte Werte zaehlen auch, Insider-Hinweise allein nicht
        assertTrue(Radar.imGespraech(treffer("C", bekannt = true)))
        assertFalse(Radar.imGespraech(treffer("D", Signal("D", Art.INSIDER_KAUF, heute, 0.5))))
        assertFalse(Radar.imGespraech(treffer("E", Signal("E", Art.KONGRESS, heute, 0.5))))
    }

    @Test fun filternOhneEinschraenkungLaesstAlles() {
        val l = listOf(
            treffer("A", Signal("A", Art.WIKIPEDIA, heute, 0.5)),
            treffer("B", Signal("B", Art.ERWAEHNUNGEN, heute, 0.5)),
            treffer("C", Signal("C", Art.INSIDER_KAUF, heute, 0.5)),
        )
        assertEquals(3, Radar.filtern(l, null, null).size)
        l.forEachIndexed { i, t -> assertSame(t, Radar.filtern(l, null, null)[i]) }
    }

    @Test fun filternNachWikipedia() {
        val wiki = treffer("A", Signal("A", Art.WIKIPEDIA, heute, 0.5))
        val beide = treffer("AB", Signal("AB", Art.ERWAEHNUNGEN, heute, 0.5), Signal("AB", Art.WIKIPEDIA, heute, 0.5))
        val l = listOf(wiki, beide, treffer("C", Signal("C", Art.ERWAEHNUNGEN, heute, 0.5)))
        assertEquals(listOf("A", "AB"), Radar.filtern(l, setOf(Art.WIKIPEDIA), null).map { it.symbol })
    }

    @Test fun filternNachSymbolenHaeltDieReihenfolge() {
        val a = treffer("A", Signal("A", Art.ERWAEHNUNGEN, heute, 0.5))
        val b = treffer("B", Signal("B", Art.ERWAEHNUNGEN, heute, 0.5))
        assertEquals(listOf("A"), Radar.filtern(listOf(b, a), null, setOf("A")).map { it.symbol })
        assertEquals(listOf("B", "A"), Radar.filtern(listOf(b, a), null, setOf("A", "B")).map { it.symbol })
        // Arten und Symbole zusammen
        assertEquals(listOf("B"), Radar.filtern(listOf(b, a), setOf(Art.ERWAEHNUNGEN), setOf("B")).map { it.symbol })
    }

    // Erwaehnungen zaehlen nur im Bereich Stimmung; alle anderen Bereiche und ihre Kennzahlen bleiben gleich.
    @Test fun erwaehnungenAendernNurDieStimmung() {
        fun daten(e: Int?, vorher: Int?) = listOf(
            Daten("S0", "Firma 0", land = "US", branche = "SOFTWARE", kgv = 10.0, kgvErwartet = 10.0, erwaehnungen = e, erwaehnungenVorher = vorher),
            Daten("S1", "Firma 1", land = "US", branche = "SOFTWARE", kgv = 20.0, kgvErwartet = 20.0, erwaehnungen = e, erwaehnungenVorher = vorher?.let { it * 4 }),
        )
        val ohne = Aktienfinder.bewerten(daten(null, null)).associateBy { it.symbol }
        val mit = Aktienfinder.bewerten(daten(40, 10)).associateBy { it.symbol }
        listOf("S0", "S1").forEach { s ->
            Bereich.entries.filter { it != Bereich.STIMMUNG }.forEach { b ->
                assertEquals(ohne.getValue(s).bereiche[b], mit.getValue(s).bereiche[b])
            }
            assertEquals(null, ohne.getValue(s).bereiche[Bereich.STIMMUNG])
            assertNotNull(mit.getValue(s).bereiche[Bereich.STIMMUNG])
            assertEquals(ohne.getValue(s).gruende.map { it.kennzahl }.filter { it != Aktienfinder.Kennzahl.ERWAEHNUNGEN },
                mit.getValue(s).gruende.map { it.kennzahl }.filter { it != Aktienfinder.Kennzahl.ERWAEHNUNGEN })
        }
    }
}
