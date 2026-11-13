package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.ui.tabelleAlsSpalten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// [0.76 q76n] Rohstoffe: Weltmenge, Wert und alle Laender mit Menge, Anteil und Wert
class Fassung076Q76nTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun roh(id: String) = index.rohstoffe.getValue(id)

    // Mit Mengen, ohne Preis, ohne Mengen
    private fun rohstoff(laender: List<Pair<String, Double>>, rest: Double, preis: Double? = null) = Einflussverzeichnis.Rohstoff(
        "X", "X", "X", "x", emptyList(), false, false, null,
        mengen = Einflussverzeichnis.Mengen("t", 2024, laender.map { Einflussverzeichnis.LandMenge(it.first, it.second) }, rest, preis, 2024, "usgs"),
    )

    @Test(timeout = 20_000)
    fun summeDerLaenderUndRestIstDieWeltmenge() {
        val r = rohstoff(listOf("CL" to 5000.0, "PE" to 2700.0, "CN" to 1800.0), 500.0)
        val a = Rohstoffmengen.aufschluesseln(r)!!
        assertEquals(10000.0, a.welt, 1e-9)
        assertEquals(10000.0, a.zeilen.sumOf { it.menge }, 1e-9)
        assertEquals(100.0, a.zeilen.sumOf { it.anteil }, 1e-9)
        assertNull(a.zeilen.last().land)
        assertEquals(5.0, a.zeilen.last().anteil, 1e-9)
    }

    @Test(timeout = 20_000)
    fun wertIstMengeMalPreisInJederZeileUndInSumme() {
        val r = rohstoff(listOf("CL" to 5000.0, "PE" to 2500.0), 250.0, preis = 9000.0)
        val a = Rohstoffmengen.aufschluesseln(r)!!
        assertEquals(5000.0 * 9000.0, a.zeilen[0].wert!!, 1e-6)
        assertEquals(250.0 * 9000.0, a.zeilen.last().wert!!, 1e-6)
        assertEquals(7750.0 * 9000.0, a.weltWert!!, 1e-6)
        assertEquals(a.weltWert!!, a.zeilen.sumOf { it.wert!! }, 1e-6)
        assertEquals(2024, a.preisJahr)
    }

    @Test(timeout = 20_000)
    fun ohnePreisKeinWert() {
        val a = Rohstoffmengen.aufschluesseln(rohstoff(listOf("CN" to 100.0), 0.0))!!
        assertNull(a.weltWert)
        assertTrue(a.zeilen.all { it.wert == null })
        assertNull(Rohstoffmengen.wert(5.0, null))
    }

    @Test(timeout = 20_000)
    fun sortiertNachMengeRestZuletzt() {
        val a = Rohstoffmengen.aufschluesseln(rohstoff(listOf("A" to 10.0, "B" to 300.0, "C" to 20.0), 5000.0))!!
        assertEquals(listOf("B", "C", "A", null), a.zeilen.map { it.land })
        // Auch der groesste Rest steht am Ende
        assertEquals(5000.0, a.zeilen.last().menge, 1e-9)
    }

    @Test(timeout = 20_000)
    fun einheitenBleibenErhalten() {
        for ((id, einheit) in listOf("GOLD" to "kg", "SILBER" to "kg", "PLATIN" to "kg", "GALLIUM" to "kg", "HELIUM" to "m3", "KUPFER" to "t", "LITHIUM" to "t")) {
            assertEquals(id, einheit, Rohstoffmengen.aufschluesseln(roh(id))!!.einheit)
        }
    }

    @Test(timeout = 20_000)
    fun dateiHatFuerAlleRohstoffeMitMengenStimmigeSummen() {
        val mit = index.rohstoffe.values.filter { it.mengen != null }
        assertTrue(mit.size >= 20)
        for (r in mit) {
            val a = Rohstoffmengen.aufschluesseln(r)!!
            assertEquals(r.id, a.welt, a.zeilen.sumOf { it.menge }, 1e-6)
            assertEquals(r.id, 100.0, a.zeilen.sumOf { it.anteil }, 1e-6)
            // nach Menge absteigend, der Rest nur am Ende
            val laender = a.zeilen.filter { it.land != null }
            assertEquals(r.id, laender.sortedByDescending { it.menge }.map { it.menge }, laender.map { it.menge })
            assertTrue(r.id, a.zeilen.dropLast(1).all { it.land != null })
            assertEquals(r.id, 2024, a.jahr)
            // Anteile der Zeilen und die gerundeten Anteile der Liste stimmen auf ein Prozent
            val erstes = r.laender.firstOrNull { it.prozent > 0 }
            if (erstes != null) assertEquals(r.id, erstes.land, laender.first().land)
        }
    }

    @Test(timeout = 20_000)
    fun alleLaenderStehenInDerTabelleNichtNurDieErstenFuenf() {
        val gold = Rohstoffmengen.aufschluesseln(roh("GOLD"))!!
        assertTrue(gold.zeilen.count { it.land != null } > 5)
        val phosphat = Rohstoffmengen.aufschluesseln(roh("PHOSPHAT"))!!
        assertTrue(phosphat.zeilen.count { it.land != null } >= 20)
        assertTrue(roh("PHOSPHAT").laender.size <= 5)
    }

    @Test(timeout = 20_000)
    fun preiseEntsprechenDenQuellen() {
        // Kupfer: Pink Sheet 2024, 9.142 Dollar je Tonne; Gold je Kilogramm aus 2.388 Dollar je Feinunze
        assertEquals(9142.0, roh("KUPFER").mengen!!.preis!!, 0.5)
        assertEquals(2388.0 * 32.1507466, roh("GOLD").mengen!!.preis!!, 5.0)
        assertEquals("wb", roh("KUPFER").mengen!!.preisQuelle)
        // Kupfer: 23 Millionen Tonnen Welt, Chile gut ein Viertel nach der Tabelle
        val k = Rohstoffmengen.aufschluesseln(roh("KUPFER"))!!
        assertEquals(23.0e6, k.welt, 0.5e6)
        assertEquals("CL", k.zeilen.first().land)
        assertTrue(k.zeilen.first().anteil in 22.0..26.0)
        assertEquals(k.welt * 9142.0, k.weltWert!!, k.welt * 1.0)
    }

    @Test(timeout = 20_000)
    fun rohstoffeOhneVerlaesslichenPreisHabenMengeAberKeinenWert() {
        for (id in listOf("SELTENE_ERDEN", "GRAPHIT", "HELIUM", "FLUSSSPAT", "GERMANIUM")) {
            val a = Rohstoffmengen.aufschluesseln(roh(id))!!
            assertNull(id, a.weltWert)
        }
    }

    @Test(timeout = 20_000)
    fun uranHatLaenderUndFirmenmengen() {
        val u = Rohstoffmengen.aufschluesseln(roh("URAN"))!!
        assertEquals(60356.0, u.welt, 1.0)
        assertEquals("KZ", u.zeilen.first().land)
        assertNotNull(index.firmenMengen["CCJ"]?.get("URAN"))
        assertEquals(10193.0, index.firmenMengen.getValue("CCJ").getValue("URAN").menge, 0.5)
        assertEquals(2024, index.firmenMengen.getValue("KAP.L").getValue("URAN").jahr)
        // Die Karte reicht die Menge an die Zeile weiter
        val zeilen = Abhaengigkeiten.karte(index, Abhaengigkeiten.Eingabe(Wertbild("CCJ", "BERGBAU", "CA")), heute)
        val uran = zeilen.first { it.typ == Abhaengigkeiten.Zeilentyp.ROHSTOFF && it.schluessel == "URAN" }
        assertEquals(10193.0, uran.firmaMenge!!.menge, 0.5)
        val andere = Abhaengigkeiten.karte(index, Abhaengigkeiten.Eingabe(Wertbild("UEC", "BERGBAU", "US", industrie = "Uranium")), heute)
        assertNull(andere.first { it.typ == Abhaengigkeiten.Zeilentyp.ROHSTOFF && it.schluessel == "URAN" }.firmaMenge)
    }

    @Test(timeout = 20_000)
    fun alterDatenstandOhneNeueFelderLaeuftWeiter() {
        val alt = """{"v":1,"stand":"2026-11-10","jahr":2024,"quelle":"alt","rohstoffe":[{"id":"KUPFER","de":"Kupfer","en":"Copper","u":"copper","l":[{"c":"CL","p":23},{"c":"CD","p":14}],"k":1,"s":1,"kurs":"HG=F"}],"branchen":{"BAU":["KUPFER"]}}"""
        val i = Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), alt)
        val k = i.rohstoffe.getValue("KUPFER")
        assertNull(k.mengen)
        assertNull(Rohstoffmengen.aufschluesseln(k))
        assertEquals(2, k.laender.size)
        assertEquals(37, k.konzentration)
        assertTrue(i.firmenMengen.isEmpty())
    }

    @Test(timeout = 20_000)
    fun konzentrationUndEmbargoBleibenAusDenAnteilen() {
        // Die Anteile der Liste stammen aus denselben Mengen: Gallium fast ganz China, Kobalt gut drei Viertel Kongo
        assertTrue(roh("GALLIUM").laender.first { it.land == "CN" }.prozent >= 98)
        assertEquals("CD", roh("KOBALT").laender.first().land)
        assertTrue(roh("KOBALT").laender.first().prozent in 72..74)
        assertTrue(roh("URAN").embargo)
    }

    @Test(timeout = 20_000)
    fun tabelleWechseltDenAufbauBeiZuWenigPlatzFuerDenNamen() {
        // Zahlenspalten 190, Name braucht mindestens 110
        assertTrue(tabelleAlsSpalten(320, 190, 110))
        assertFalse(tabelleAlsSpalten(290, 190, 110))
        assertTrue(tabelleAlsSpalten(300, 190, 110))
    }
}
