package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Mitteilung "Betrifft dein Depot": sparsam und nur bei genannten Werten im Depot.
class WeltMeldungTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private val nvidia = Bestand(Wertbild("NVDA", "HALBLEITER", "US", "US"), "NVIDIA", 0.20)
    private val tsm = Bestand(Wertbild("TSM", "HALBLEITER", "TW", "TW"), "TSMC", 0.10)
    private val vw = Bestand(Wertbild("VOW3.DE", "FAHRZEUGE", "DE", "DE"), "Volkswagen", 0.30)
    private val intel = Bestand(Wertbild("INTC", "HALBLEITER", "US", "US"), "Intel", 0.05)

    private fun lage(abw: Double, vn: Double = 100.0) = Engpaesse.Lage(vn * (1 + abw / 100), 0.0, 0.0, vn, null, null)
    private fun uebersicht(vararg l: Pair<String, Engpaesse.Lage>) = Engpaesse.Uebersicht(0L, 20_000, l.toMap())

    @Test(timeout = 20_000)
    fun ereignisseNachStaerkeGeordnet() {
        val u = uebersicht("chokepoint11" to lage(-35.0), "chokepoint6" to lage(-60.0), "chokepoint1" to lage(-10.0), "chokepoint3" to lage(-80.0, vn = 3.0))
        val sp = mapOf("TWN" to Spannungen.Wert(0L, "TWN", 100, 3.0, 20_000), "UKR" to Spannungen.Wert(0L, "UKR", 100, 1.4, 20_000))
        val e = WeltMeldung.ereignisse(u, sp)
        // Rueckgang unter 20 % zaehlt nicht, ebenso wenig zu wenig Verkehr im Vorjahr oder ein Faktor unter 2
        assertEquals(listOf("engpass:chokepoint6", "engpass:chokepoint11", "spannung:TWN"), e.map { it.groesse })
        assertTrue(e.zipWithNext().all { (a, b) -> a.staerke >= b.staerke })
        assertTrue(WeltMeldung.ereignisse(null, emptyMap()).isEmpty())
    }

    @Test(timeout = 20_000)
    fun meldetNurGenannteWerteImDepot() {
        val e = WeltMeldung.ereignisse(uebersicht("chokepoint11" to lage(-35.0)), emptyMap())
        val m = WeltMeldung.waehlen(index, e, listOf(nvidia, tsm, vw), WeltMeldung.Gemerkt(), heute)!!
        assertEquals("engpass:chokepoint11", m.ereignis.groesse)
        // NVIDIA und TSMC sind in der Liste der Route genannt, Volkswagen nur ueber die Branche
        assertEquals(listOf("NVIDIA", "TSMC"), m.namen)
        assertEquals(0, m.rest)
        // Nur Volkswagen im Depot: betroffen, aber nicht genannt, also keine Mitteilung
        assertNull(WeltMeldung.waehlen(index, e, listOf(vw), WeltMeldung.Gemerkt(), heute))
        // Werte nur auf der Watchlist (Anteil 0) zaehlen nicht
        assertNull(WeltMeldung.waehlen(index, e, listOf(Bestand(nvidia.wert, "NVIDIA", 0.0)), WeltMeldung.Gemerkt(), heute))
    }

    @Test(timeout = 20_000)
    fun mehrAlsDreiNamenWerdenGezaehlt() {
        val e = WeltMeldung.ereignisse(uebersicht("chokepoint11" to lage(-35.0)), emptyMap())
        val depot = listOf(
            nvidia, tsm, Bestand(Wertbild("VVSM.DE", "HALBLEITER", "DE", "DE"), "VanEck", 0.04),
            Bestand(Wertbild("SEC0.DE", "HALBLEITER", "DE", "DE"), "iShares", 0.03),
        )
        val m = WeltMeldung.waehlen(index, e, depot, WeltMeldung.Gemerkt(), heute)!!
        assertEquals(3, m.namen.size)
        assertEquals(1, m.rest)
        assertEquals(listOf("NVIDIA", "TSMC", "VanEck"), m.namen)
    }

    @Test(timeout = 20_000)
    fun hoechstensEineAmTagUndJedesEreignisEinmalProMonat() {
        val e = WeltMeldung.ereignisse(
            uebersicht("chokepoint11" to lage(-35.0), "chokepoint5" to lage(-30.0)), emptyMap(),
        )
        val depot = listOf(nvidia, tsm, Bestand(Wertbild("MAERSK-B.CO", "VERKEHR", "DK", "DK"), "Maersk", 0.1))
        val m = WeltMeldung.waehlen(index, e, depot, WeltMeldung.Gemerkt(), heute)!!
        val gemerkt = WeltMeldung.merken(WeltMeldung.Gemerkt(), m, heute)
        // Am selben Tag nichts mehr
        assertNull(WeltMeldung.waehlen(index, e, depot, gemerkt, heute))
        // Am naechsten Tag das andere Ereignis, nicht dasselbe wieder
        val zweite = WeltMeldung.waehlen(index, e, depot, gemerkt, heute + 1)
        assertNotNull(zweite)
        assertTrue(zweite!!.ereignis.groesse != m.ereignis.groesse)
        // Nach 30 Tagen darf dasselbe Ereignis wieder kommen
        val spaeter = WeltMeldung.waehlen(index, e, depot, gemerkt, heute + 31)!!
        assertEquals(m.ereignis.groesse, spaeter.ereignis.groesse)
    }

    @Test(timeout = 20_000)
    fun alteEintraegeFallenAusDemGemerktenStand() {
        val e = WeltMeldung.ereignisse(uebersicht("chokepoint11" to lage(-35.0)), emptyMap())
        val m = WeltMeldung.waehlen(index, e, listOf(nvidia), WeltMeldung.Gemerkt(), heute)!!
        val alt = WeltMeldung.Gemerkt(heute - 60, mapOf("engpass:chokepoint1" to heute - 60))
        val neu = WeltMeldung.merken(alt, m, heute)
        assertEquals(heute, neu.tag)
        assertEquals(setOf("engpass:chokepoint11"), neu.ids.keys)
        val l = WeltMeldung.lesen(WeltMeldung.schreiben(neu))!!
        assertEquals(neu.ids, l.ids)
        assertNull(WeltMeldung.lesen("kein json"))
    }

    @Test(timeout = 20_000)
    fun spannungsgebietMitGenanntemWert() {
        val sp = mapOf("TWN" to Spannungen.Wert(0L, "TWN", 100, 2.5, 20_000))
        val e = WeltMeldung.ereignisse(null, sp)
        val m = WeltMeldung.waehlen(index, e, listOf(intel, tsm, vw), WeltMeldung.Gemerkt(), heute)
        // Die Liste des Gebiets Taiwan nennt TSMC, Intel nur ueber die Branche
        assertNotNull(m)
        assertEquals(listOf("TSMC"), m!!.namen)
        assertEquals(Einflussverzeichnis.Typ.SPANNUNG, m.ereignis.typ)
    }

    @Test(timeout = 20_000)
    fun artIstAbschaltbar() {
        val s = Settings()
        assertTrue(Mitteilungsarten.an(s, Mitteilungsarten.Art.WELT))
        val aus = Mitteilungsarten.setzen(s, Mitteilungsarten.Art.WELT, false)
        assertFalse(Mitteilungsarten.an(aus, Mitteilungsarten.Art.WELT))
        assertEquals(setOf("welt"), Mitteilungsarten.gesperrteKanaele(aus))
        assertTrue(Mitteilungsarten.an(Mitteilungsarten.setzen(aus, Mitteilungsarten.Art.WELT, true), Mitteilungsarten.Art.WELT))
        assertTrue(Mitteilungsarten.Art.WELT in Mitteilungsarten.arten(false))
    }
}
