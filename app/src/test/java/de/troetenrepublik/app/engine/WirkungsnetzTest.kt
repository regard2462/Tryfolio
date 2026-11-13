package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Art
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Wirkungsnetz: Eintraege einer Groesse, gemessene Richtung, Reihenfolge der Uebersicht.
class WirkungsnetzTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private val nvidia = Bestand(Wertbild("NVDA", "HALBLEITER", "US", "US"), "NVIDIA", 0.20)
    private val vw = Bestand(Wertbild("VOW3.DE", "FAHRZEUGE", "DE", "DE"), "Volkswagen", 0.30)
    private val bank = Bestand(Wertbild("DBK.DE", "BANKEN", "DE", "DE"), "Deutsche Bank", 0.10)
    private val watch = Bestand(Wertbild("TSM", "HALBLEITER", "TW", "TW"), "TSMC", 0.0)

    private fun ergebnis(f: Empfindlichkeit.Faktor, wert: Double, u: Empfindlichkeit.Urteil) = Empfindlichkeit.Ergebnis(
        553, 16800, 17400, 0.5, 0.4, 0.5 to 0.5, listOf(Empfindlichkeit.Koeffizient(f, wert, 5.0, wert to wert, 4.0 to 4.0, u)), emptyList(),
    )

    @Test(timeout = 20_000)
    fun engpassTrifftDepotUndWatchlist() {
        val e = Wirkungsnetz.eintrag(index, "engpass:chokepoint11", listOf(nvidia, vw, bank), listOf(watch), emptyMap(), heute)!!
        assertEquals(listOf("VOW3.DE", "NVDA"), e.depot.map { it.bestand.wert.symbol })
        assertEquals(listOf("TSM"), e.watchlist.map { it.bestand.wert.symbol })
        assertEquals(0.50, e.depotAnteil, 1e-9)
        assertEquals(3, e.treffer)
        // Ohne belegte Richtung nur "betroffen"
        assertTrue(e.depot.all { it.richtung == 0 && it.gemessen == null })
        assertTrue("TW" in e.laender && "CN" in e.laender)
        assertTrue("HALBLEITER" in e.branchen)
        assertTrue(e.belege.isNotEmpty())
        // Genannter Wert: abgeleitet ueber die Liste der Route; Fahrzeuge nur ueber die Branche
        assertEquals(Einflussverzeichnis.ZielArt.WERT, e.depot.first { it.bestand.wert.symbol == "NVDA" }.kante.ziel.art)
        assertEquals(Einflussverzeichnis.ZielArt.BRANCHE, e.depot.first { it.bestand.wert.symbol == "VOW3.DE" }.kante.ziel.art)
    }

    @Test(timeout = 20_000)
    fun richtungNurWoBelegtOderGemessen() {
        // Oel -> Oel- und Gasfoerderer steigen: Richtung steht in der Datei (+1), Fluglinien fallen (-1)
        val xom = Bestand(Wertbild("XOM", "OEL_GAS", "US", "US"), "Exxon", 0.1)
        val dal = Bestand(Wertbild("DAL", "LUFTFAHRT", "US", "US"), "Delta", 0.1)
        val e = Wirkungsnetz.eintrag(index, "oel", listOf(xom, dal, vw), emptyList(), emptyMap(), heute)!!
        assertEquals(1, e.depot.first { it.bestand.wert.symbol == "XOM" }.richtung)
        assertEquals(-1, e.depot.first { it.bestand.wert.symbol == "DAL" }.richtung)
        assertEquals(0, e.depot.first { it.bestand.wert.symbol == "VOW3.DE" }.richtung)
        // Eine belegte Messung gegen Oel setzt die Richtung und die Art "gemessen"
        val messungen = mapOf("VOW3.DE" to ergebnis(Empfindlichkeit.Faktor.OEL, -0.3, Empfindlichkeit.Urteil.BELEGT))
        val m = Wirkungsnetz.eintrag(index, "oel", listOf(xom, dal, vw), emptyList(), messungen, heute)!!.depot.first { it.bestand.wert.symbol == "VOW3.DE" }
        assertEquals(-1, m.gemessen)
        assertEquals(-1, m.richtung)
        assertEquals(Art.GEMESSEN, m.art)
    }

    @Test(timeout = 20_000)
    fun nurBelegteMessungZaehlt() {
        assertEquals(1, Wirkungsnetz.gemessen(ergebnis(Empfindlichkeit.Faktor.HALBLEITER, 1.0, Empfindlichkeit.Urteil.BELEGT), "halbleiter"))
        assertNull(Wirkungsnetz.gemessen(ergebnis(Empfindlichkeit.Faktor.HALBLEITER, 1.0, Empfindlichkeit.Urteil.WECHSELT), "halbleiter"))
        assertNull(Wirkungsnetz.gemessen(ergebnis(Empfindlichkeit.Faktor.HALBLEITER, 1.0, Empfindlichkeit.Urteil.NICHT_BELEGT), "halbleiter"))
        // Anderer Faktor gemessen: kein Beleg fuer diese Groesse
        assertNull(Wirkungsnetz.gemessen(ergebnis(Empfindlichkeit.Faktor.GOLD, 1.0, Empfindlichkeit.Urteil.BELEGT), "halbleiter"))
        assertNull(Wirkungsnetz.gemessen(null, "oel"))
        // Groessen ohne Stellvertreter (Programm, Engpass) haben keine Messung
        assertNull(Wirkungsnetz.faktorFuer("programm:chips-us"))
        assertEquals(Empfindlichkeit.Faktor.KUPFER, Wirkungsnetz.faktorFuer("rohstoff:KUPFER"))
    }

    @Test(timeout = 20_000)
    fun landAlsGroesse() {
        val e = Wirkungsnetz.eintrag(index, "land:DE", listOf(nvidia, vw, bank), listOf(watch), emptyMap(), heute)!!
        assertEquals(listOf("VOW3.DE", "DBK.DE"), e.depot.map { it.bestand.wert.symbol })
        assertTrue(e.watchlist.isEmpty())
        assertEquals(Einflussverzeichnis.Typ.LAND, e.groesse.typ)
        assertEquals(listOf("DE"), e.laender)
    }

    @Test(timeout = 20_000)
    fun unbekannteOderAbgelaufeneGroessen() {
        assertNull(Wirkungsnetz.eintrag(index, "gibt-es-nicht", listOf(nvidia), emptyList(), emptyMap(), heute))
        val spaet = LocalDate.of(2030, 1, 1).toEpochDay()
        assertNull(Wirkungsnetz.eintrag(index, "programm:chips-us", listOf(nvidia), emptyList(), emptyMap(), spaet))
        assertNotNull(Wirkungsnetz.eintrag(index, "programm:chips-us", listOf(nvidia), emptyList(), emptyMap(), heute))
    }

    @Test(timeout = 20_000)
    fun uebersichtOrdnetNachDepotanteil() {
        val u = Wirkungsnetz.uebersicht(index, listOf(nvidia, vw, bank), listOf(watch), emptyMap(), heute)
        val t = Wirkungsnetz.mitTreffern(u)
        assertTrue(t.isNotEmpty())
        // Absteigend nach Depotanteil
        assertTrue(t.zipWithNext().all { (a, b) -> a.depotAnteil >= b.depotAnteil })
        // Groessen ohne Treffer stehen hinten und haben keinen
        val ohne = u.filter { it.treffer == 0 }
        assertTrue(ohne.isNotEmpty())
        assertEquals(t.size, u.indexOfFirst { it.treffer == 0 })
        // Die Bank wird von Zinsen getroffen, nicht von Taiwan
        val zins = u.first { it.groesse.id == "zins" }
        assertEquals(listOf("DBK.DE"), zins.depot.map { it.bestand.wert.symbol })
        assertTrue(u.first { it.groesse.id == "engpass:chokepoint11" }.depot.none { it.bestand.wert.symbol == "DBK.DE" })
        // Rohstoffgroessen ohne Treffer erscheinen nicht
        assertTrue(u.none { it.groesse.typ == Einflussverzeichnis.Typ.ROHSTOFF && it.treffer == 0 })
    }
}
