package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Abhaengigkeiten.Abschnitt
import de.troetenrepublik.app.engine.Abhaengigkeiten.Eingabe
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeilentyp
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// [0.77 D] Einfluesse auf der Wertpapierseite: Weg, Richtung und Staerke je Zeile, nichts Vages.
class Fassung077DTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private val xom = Wertbild("XOM", "OEL_GAS", "US", "US")
    private val delta = Wertbild("DAL", "LUFTFAHRT", "US", "US")
    private val nvidia = Wertbild("NVDA", "HALBLEITER", "US", "US")
    private val frontline = Wertbild("FRO.OL", null, "NO", "NO")

    private fun lage(abw: Double) = Engpaesse.Lage(100.0 * (1 + abw / 100), 0.0, 0.0, 100.0, null, null)

    private fun koeff(f: Empfindlichkeit.Faktor, wert: Double, u: Empfindlichkeit.Urteil) =
        Empfindlichkeit.Koeffizient(f, wert, 4.0, wert to wert, 3.0 to 3.0, u)

    private fun emp(vararg k: Empfindlichkeit.Koeffizient) =
        Empfindlichkeit.Ergebnis(553, 16800, 17400, 0.28, 0.22, 0.34 to 0.19, k.toList(), emptyList())

    private fun groessen(e: Eingabe) = Abhaengigkeiten.karte(index, e, heute).filter { it.typ == Zeilentyp.GROESSE }

    @Test(timeout = 20_000)
    fun engpassNenntDenWegUeberDieBranche() {
        val z = groessen(Eingabe(xom, engpaesse = mapOf("chokepoint6" to lage(-95.0)))).first { it.schluessel == "engpass:chokepoint6" }
        // Der Listenbeleg (Handliste) ist kein Weg; die Branchenverbindung nennt ihn
        val weg = z.weg!!
        assertEquals(Einflussverzeichnis.ZielArt.BRANCHE, weg.ziel.art)
        assertTrue(weg.beleg.startsWith("Güterströme der Route (Energie)"))
        assertEquals(-95.0, z.zahl!!, 1e-6)
        // Ohne Angabe der Richtung in den Daten steht keine
        assertEquals(0, z.richtung)
    }

    @Test(timeout = 20_000)
    fun richtungKommtAusDenDatenOderAusDerMessung() {
        val oel = groessen(Eingabe(delta)).first { it.schluessel == "oel" }
        // Fluglinien: Treibstoff als Kostenblock, die Verbindung belegt die Richtung
        assertEquals(-1, oel.richtung)
        assertEquals("Treibstoff als Kostenblock der Fluglinien", oel.weg!!.beleg)
        // Am Kurs gemessen hat Vorrang vor der Verbindung
        val mit = groessen(Eingabe(delta, empfindlichkeit = emp(koeff(Empfindlichkeit.Faktor.OEL, 0.3, Empfindlichkeit.Urteil.BELEGT))))
            .first { it.schluessel == "oel" }
        assertEquals(1, mit.richtung)
        // Nicht belegte Messung aendert die Richtung der Verbindung nicht
        val nicht = groessen(Eingabe(delta, empfindlichkeit = emp(koeff(Empfindlichkeit.Faktor.OEL, 0.3, Empfindlichkeit.Urteil.NICHT_BELEGT))))
            .first { it.schluessel == "oel" }
        assertEquals(-1, nicht.richtung)
        assertEquals(0, Abhaengigkeiten.richtungVon(null, null, null))
    }

    @Test(timeout = 20_000)
    fun zeilenOhneWegRichtungOderZahlFallenWeg() {
        // Frontline steht nur in Handlisten (Engpass, Gebiet): ohne Live-Stand keine Zeile
        assertTrue(groessen(Eingabe(frontline)).none { it.schluessel.startsWith("engpass:") || it.schluessel.startsWith("spannung:") })
        // Mit Live-Stand steht die Zahl da, ohne erfundenen Weg
        val z = groessen(Eingabe(frontline, engpaesse = mapOf("chokepoint6" to lage(-30.0)))).first { it.schluessel == "engpass:chokepoint6" }
        assertNull(z.weg)
        assertEquals(-30.0, z.zahl!!, 1e-6)
    }

    @Test(timeout = 20_000)
    fun programmNenntDieBrancheAlsWeg() {
        val p = Abhaengigkeiten.karte(index, Eingabe(nvidia), heute).first { it.schluessel == "programm:chips-us" }
        assertEquals(Abschnitt.POLITIK, p.abschnitt)
        assertEquals(Einflussverzeichnis.ZielArt.BRANCHE, p.weg!!.ziel.art)
        assertEquals("HALBLEITER", p.weg!!.ziel.schluessel)
        assertEquals(setOf("US"), p.weg!!.im)
    }

    @Test(timeout = 20_000)
    fun keineStrukturZeileUndKeineLueckeZuLieferern() {
        val z = Abhaengigkeiten.karte(index, Eingabe(xom, landPunkte = 70.0, gesamtbild = 60.0, gesamtbildJahr = 2026), heute)
        assertTrue(z.none { it.abschnitt == Abschnitt.LUECKEN })
        assertTrue(Zeilentyp.entries.none { it.name == "STRUKTUR" || it.name == "LUECKE_LIEFERER" })
    }

    @Test(timeout = 20_000)
    fun belegGibtEsAuchEnglisch() {
        val k = Einflussverzeichnis.wegKante(index, delta, "oel", heute)
        assertNotNull(k)
        assertNotEquals(k!!.beleg(true), k.beleg(false))
        assertEquals("Fuel as a cost block of airlines", k.beleg(false))
        // Alle Belege der Datei, die in der Oberflaeche stehen koennen, haben eine englische Fassung (Normen und Eigennamen gleich)
        val ohne = index.kanten.filter { it.ziel.art != Einflussverzeichnis.ZielArt.LAND && !it.groesse.startsWith("rohstoff:") && !Einflussverzeichnis.istListenbeleg(it.beleg) && it.belegEn.isBlank() }.map { it.beleg }.distinct()
        assertTrue("ohne englischen Beleg: $ohne", ohne.all { it.startsWith("Verordnung") || it.contains("Act") || it.startsWith("U.S.") })
    }
}
