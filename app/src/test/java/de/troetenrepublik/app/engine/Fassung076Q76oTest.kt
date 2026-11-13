package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.prozentText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// [0.76 q76o] Foerdermengen aller Laender aus dem USGS Minerals Yearbook 2024, Kapazitaet der Bergwerke je Unternehmen, kleine Anteile
class Fassung076Q76oTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE), anlagen = datei(WeltDaten.ANLAGEN)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun roh(id: String) = index.rohstoffe.getValue(id)
    private fun menge(id: String, land: String) = roh(id).mengen!!.laender.first { it.land == land }.menge

    @Test(timeout = 20_000)
    fun weltmengeIstDieSummeDerLaender() {
        val mitMengen = index.rohstoffe.values.filter { it.mengen != null }
        assertEquals(27, mitMengen.size)
        for (r in mitMengen) {
            val m = r.mengen!!
            val a = Rohstoffmengen.aufschluesseln(r)!!
            assertEquals(r.id, m.laender.sumOf { it.menge } + m.rest, a.welt, 1e-6)
            assertEquals(r.id, 100.0, a.zeilen.sumOf { it.anteil }, 1e-9)
            // Wo das Jahrbuch die Laender fuehrt, bleibt kein Rest
            if (m.mengenQuelle == Rohstoffmengen.QUELLE_MYB) {
                assertEquals(r.id, 0.0, m.rest, 0.0)
                assertTrue(r.id, a.zeilen.none { it.land == null })
            }
        }
    }

    @Test(timeout = 20_000)
    fun laenderzahlUndQuelleJeRohstoff() {
        // Kupfer: 53 statt 14 Laender, keine uebrigen Laender mehr; Lithium und Silizium bleiben USGS MCS 2026
        assertTrue(roh("KUPFER").mengen!!.laender.size >= 50)
        assertEquals(Rohstoffmengen.QUELLE_MYB, roh("KUPFER").mengen!!.mengenQuelle)
        assertEquals(Rohstoffmengen.QUELLE_MCS, roh("LITHIUM").mengen!!.mengenQuelle)
        assertEquals(Rohstoffmengen.QUELLE_MCS, roh("SILIZIUM").mengen!!.mengenQuelle)
        assertTrue(roh("GOLD").mengen!!.laender.size >= 90)
        // Laender ohne Zeile im Jahrbuch (Vereinigte Staaten) bleiben mit dem bisherigen Wert
        assertTrue(menge("KUPFER", "US") > 1_000_000.0)
        assertEquals(setOf(Rohstoffmengen.QUELLE_MYB, Rohstoffmengen.QUELLE_MCS), Rohstoffmengen.mengenQuellen(listOf(roh("KUPFER"), roh("LITHIUM"))))
    }

    @Test(timeout = 20_000)
    fun umrechnungAufDieBezugsgroesseDesPreises() {
        // Phosphat: P2O5 in Gestein mit 70 % BPL (32,03 % P2O5): China 34,1 Mio. t P2O5
        assertEquals(34_100_000.0 / 0.3203, menge("PHOSPHAT", "CN"), 34_100_000.0 / 0.3203 * 0.001)
        // Eisenerz: Fe-Inhalt durch 0,62, Australien 591.649.946 t Fe
        assertEquals(591_649_946.0 / 0.62, menge("EISENERZ", "AU"), 1.0)
        // Gallium und Germanium in kg, Germanium neu mit China 162 t
        assertEquals("kg", roh("GALLIUM").mengen!!.einheit)
        assertEquals("kg", roh("GERMANIUM").mengen!!.einheit)
        assertEquals(162_000.0, menge("GERMANIUM", "CN"), 0.5)
        // Gold in kg: Ghana Kleinbergbau 59.090 plus Grossbetriebe 90.695
        assertEquals(149_785.0, menge("GOLD", "GH"), 0.5)
        // Silber aus Tonnen und Kilogramm, Helium in m3
        assertEquals("m3", roh("HELIUM").mengen!!.einheit)
        assertEquals(64_000_000.0, menge("HELIUM", "QA"), 0.5)
        // Kupfer: Konzentrat und Loesungsgewinnung zusammen, Erz nicht doppelt (Russland)
        assertEquals(1_020_800.0, menge("KUPFER", "RU"), 0.5)
    }

    @Test(timeout = 20_000)
    fun anteileDerListeStimmenMitDenMengen() {
        for (r in index.rohstoffe.values.filter { it.mengen != null }) {
            val a = Rohstoffmengen.aufschluesseln(r)!!
            for (x in r.laender.filter { it.prozent > 0 }) {
                val z = a.zeilen.first { it.land == x.land }
                assertTrue(r.id + " " + x.land, Math.abs(z.anteil - x.prozent) <= 1.0)
            }
            assertTrue(r.id, r.laender.sumOf { it.prozent } <= 100)
        }
        assertEquals("CD", roh("KOBALT").laender.first().land)
    }

    @Test(timeout = 20_000)
    fun kapazitaetJeUnternehmenUndLand() {
        val bhp = index.anlagen.getValue("BHP").getValue("KUPFER")
        // Gleiche Firma unter mehreren Kuerzeln: dieselbe Kapazitaet
        assertEquals(bhp.menge, index.anlagen.getValue("BHP.AX").getValue("KUPFER").menge, 0.0)
        assertEquals(2024, bhp.jahr)
        assertEquals(bhp.menge, bhp.laender.sumOf { it.menge }, 1e-6)
        // Eigentumsanteil: BHP hat 100 % an Olympic Dam (235.000 t) und die Eigentumsanteile an Antamina, nie die vollen Werte der Gemeinschaftsbergwerke
        assertTrue(bhp.laender.first { it.land == "AU" }.menge >= 235_000.0)
        assertTrue(bhp.laender.first { it.land == "PE" }.menge < 450_000.0)
        val k = Rohstoffmengen.kapazitaet(roh("KUPFER"), bhp)!!
        assertEquals("t", k.einheit)
        assertEquals(bhp.menge, k.gesamt, 1e-6)
        assertEquals(100.0, k.zeilen.sumOf { it.anteil }, 1e-9)
        assertTrue(k.zeilen.zipWithNext().all { (a, b) -> a.menge >= b.menge })
        // Zeile der Karte bekommt die Kapazitaet des Unternehmens
        val zeilen = Abhaengigkeiten.karte(index, Abhaengigkeiten.Eingabe(Wertbild("BHP", "BERGBAU", "AU")), heute)
        val kupfer = zeilen.first { it.typ == Abhaengigkeiten.Zeilentyp.ROHSTOFF && it.schluessel == "KUPFER" }
        assertNotNull(kupfer.anlage)
        assertEquals(bhp.menge, kupfer.anlage!!.menge, 0.0)
    }

    @Test(timeout = 20_000)
    fun kapazitaetOhneEinheitOderOhneAngabeIstLeer() {
        assertNull(Rohstoffmengen.kapazitaet(roh("KUPFER"), null))
        val ohneMengen = Einflussverzeichnis.Rohstoff("X", "X", "X", "x", emptyList(), false, false, null)
        assertNull(Rohstoffmengen.kapazitaet(ohneMengen, Einflussverzeichnis.Anlage(10.0, listOf(Einflussverzeichnis.LandMenge("CL", 10.0)), 2024, 1, 1)))
        assertNull(Rohstoffmengen.kapazitaet(roh("KUPFER"), Einflussverzeichnis.Anlage(0.0, emptyList(), 2024, 0, 3)))
    }

    @Test(timeout = 20_000)
    fun luckenInDerKapazitaetWerdenGenannt() {
        val a = Einflussverzeichnis.Anlage(300.0, listOf(Einflussverzeichnis.LandMenge("CL", 100.0), Einflussverzeichnis.LandMenge("PE", 200.0)), 2024, 2, 3)
        val k = Rohstoffmengen.kapazitaet(roh("KUPFER"), a)!!
        assertTrue(k.luecken)
        assertEquals("PE", k.zeilen.first().land)
        assertEquals(200.0 / 300.0 * 100.0, k.zeilen.first().anteil, 1e-9)
        val voll = Rohstoffmengen.kapazitaet(roh("KUPFER"), Einflussverzeichnis.Anlage(300.0, a.laender, 2024, 3, 3))!!
        assertFalse(voll.luecken)
    }

    @Test(timeout = 20_000)
    fun firmenzuordnungNurBeiEindeutigemNamen() {
        // Eindeutig: Name des Betreibers oder Eigentuemers ist der Name im Katalog (Rechtsform ausgenommen)
        for (s in listOf("BHP", "GLEN.L", "RIO.L", "AAL.L", "NEM", "FCX", "ANTO.L", "5713.T", "B")) assertTrue(s, index.anlagen.containsKey(s))
        assertTrue(index.anlagen.containsKey("BHP.AX"))
        // Mehrdeutig oder nicht dieselbe Firma: Handelshaus-Gruppen, Banken und Versicherer als Kapitaleigner, gleichnamige Aktie
        // einer anderen Firma (Evolution Mining nicht Evolution AB), Kazatomprom (Kapazitaet nur als Gesamtzahl aller Betriebe)
        for (s in listOf("BLK", "MANTA.HE", "EVO.ST", "KAP.L", "KAP.IL", "KAZ.IL")) assertFalse(s, index.anlagen.containsKey(s))
        // Nur Rohstoffe des Index
        assertTrue(index.anlagen.values.all { je -> je.keys.all { it in index.rohstoffe } })
    }

    @Test(timeout = 20_000)
    fun kleineAnteileStehenAlsKleinerAlsEinHundertstel() {
        assertTrue(Rohstoffmengen.anteilKlein(0.004))
        assertTrue(Rohstoffmengen.anteilKlein(0.0099))
        assertFalse(Rohstoffmengen.anteilKlein(0.01))
        assertFalse(Rohstoffmengen.anteilKlein(0.0))
        assertEquals("< " + Fmt.pct(0.01, 2), prozentText(0.004))
        assertEquals(Fmt.pct(0.5, 2), prozentText(0.5))
        assertEquals(Fmt.pct(12.34, 1), prozentText(12.34))
        // In den echten Tabellen steht nirgends 0,00 %
        var klein = 0
        for (r in index.rohstoffe.values.filter { it.mengen != null }) {
            for (z in Rohstoffmengen.aufschluesseln(r)!!.zeilen) {
                val t = prozentText(z.anteil)
                assertFalse(r.id + " " + t, t.startsWith("0,00") || t.startsWith("0.00"))
                if (Rohstoffmengen.anteilKlein(z.anteil)) { klein++; assertTrue(t.startsWith("< ")) }
            }
        }
        assertTrue(klein > 0)
    }

    @Test(timeout = 20_000)
    fun alterDatenstandOhneQuelleUndAnlagenLaeuftWeiter() {
        val alt = """{"v":2,"stand":"2026-11-10","jahr":2024,"quelle":"alt","rohstoffe":[{"id":"KUPFER","de":"Kupfer","en":"Copper","u":"copper","l":[{"c":"CL","p":23}],"k":1,"s":1,"kurs":"HG=F","me":"t","mj":2024,"lm":[{"c":"CL","m":5000},{"c":"PE","m":2500}],"ru":500}],"branchen":{"BAU":["KUPFER"]}}"""
        val i = Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), alt)
        val k = i.rohstoffe.getValue("KUPFER")
        assertNull(k.mengen!!.mengenQuelle)
        assertEquals(setOf(Rohstoffmengen.QUELLE_MCS), Rohstoffmengen.mengenQuellen(listOf(k)))
        assertTrue(i.anlagen.isEmpty())
        // Das Uebrige bleibt als Zeile erhalten
        assertNull(Rohstoffmengen.aufschluesseln(k)!!.zeilen.last().land)
        // Kaputte oder fehlende Anlagendatei: keine Kapazitaeten, kein Absturz
        assertTrue(Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), alt, anlagen = "{kein json").anlagen.isEmpty())
        val zeilen = Abhaengigkeiten.karte(i, Abhaengigkeiten.Eingabe(Wertbild("BHP", "BERGBAU", "AU")), heute)
        assertTrue(zeilen.filter { it.typ == Abhaengigkeiten.Zeilentyp.ROHSTOFF }.all { it.anlage == null })
    }
}
