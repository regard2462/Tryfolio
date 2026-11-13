package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.floor

// Jahrestabelle steuerwerte.json fuer App und Finanzwissen: Tarif als Liste von Zonen, Soli-Freigrenzen, Grundfreibetrag,
// fehlende Felder aus dem Jahr davor (geschaetzt), nie Basiszins 0, aeltere Fassungen lesen die Datei weiter.
class SteuerwerteJahrestabelleTest {

    @After fun zurueck() { Steuerwerte.setzen(null) }

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)

    private val voll2026 = """"2026":{"abgeltung":25.0,"soli":5.5,"pauschbetrag":1000,"pauschbetragPaar":2000,"basiszins":3.20,"grundfreibetrag":12348,"soliFreigrenze":20350,"soliFreigrenzePaar":40700,"tarif":[{"bis":12348},{"bis":17799,"ab":12348,"a":914.51,"b":1400},{"bis":69878,"ab":17799,"a":173.10,"b":2397,"c":1034.87},{"bis":277825,"satz":0.42,"abzug":11135.63},{"satz":0.45,"abzug":19470.38}]}"""

    // Der bisherige Aufbau mit festen Zonen 2 bis 5 (Fassung 0.74), als Vergleich
    private fun alt2026(zve: Double): Double {
        val x = floor(zve)
        if (x <= 12348) return 0.0
        if (x <= 17799) { val y = (x - 12348) / 10000; return floor((914.51 * y + 1400) * y) }
        if (x <= 69878) { val z = (x - 17799) / 10000; return floor((173.10 * z + 2397) * z + 1034.87) }
        if (x <= 277825) return floor(0.42 * x - 11135.63)
        return floor(0.45 * x - 19470.38)
    }

    @Test fun dateiPasstZumEingebautenUndTraegtDenTarif() {
        val d = Steuerwerte.lesen(asset(Steuerwerte.DATEI))
        assertNotNull(d)
        assertEquals(Steuerwerte.EINGEBAUT.jahre, d!!.jahre)
        assertEquals(5, d.jahre.getValue("2026").tarif!!.size)
        // Kein Jahr nach 2026: Tarif und Basiszins 2027 sind nicht beschlossen
        assertEquals(2026, Steuerwerte.letztesJahr(d))
    }

    @Test fun tarif2026GibtDieTabellenwerte() {
        val t = Steuerwerte.fuer(2026).tarif!!
        assertEquals(0.0, Steuerwerte.einkommensteuer(12_348.0, t), 0.0)
        assertEquals(8_835.0, Steuerwerte.einkommensteuer(45_000.0, t), 0.0)
        var x = 0.0
        while (x <= 400_000.0) {
            assertEquals("zvE $x", alt2026(x), Steuerwerte.einkommensteuer(x, t), 0.0)
            x += 13.0
        }
        listOf(12_349.0, 17_799.0, 17_800.0, 69_878.0, 69_879.0, 277_825.0, 277_826.0, 1_000_000.0).forEach {
            assertEquals("zvE $it", alt2026(it), Steuerwerte.einkommensteuer(it, t), 0.0)
        }
    }

    @Test fun jahrOhneBasiszinsNimmtDenDesVorjahrsUndIstGeschaetzt() {
        val d = Steuerwerte.lesen("""{"v":1,"stand":"2027-01-02","jahre":{$voll2026,"2027":{"abgeltung":25.0,"soli":5.5,"pauschbetrag":1000,"pauschbetragPaar":2000}}}""")!!
        Steuerwerte.setzen(d)
        assertEquals(3.20, Steuerwerte.fuer(2027).basiszins!!, 0.0)
        assertTrue(Steuerwerte.geschaetzt(2027) { it.basiszins })
        assertFalse(Steuerwerte.geschaetzt(2027) { it.pauschbetragFeld })
        assertFalse(Steuerwerte.geschaetzt(2026) { it.basiszins })
        // Das Jahr hat einen Eintrag, ist als Ganzes also nicht geschaetzt
        assertFalse(Steuerwerte.geschaetzt(2027))
        assertEquals(2026, Steuerwerte.feldJahr(2027) { it.tarif })
        assertEquals(20_350.0, Steuerwerte.fuer(2027).soliFreigrenze!!, 0.0)
    }

    @Test fun ohneJedenBasiszinsBleibtErLeer() {
        val d = Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"pauschbetrag":1000}}}""")!!
        assertNull(Steuerwerte.fuer(2026, d).basiszins)
        assertNull(Steuerwerte.feldJahr(2026, d) { it.basiszins })
        assertTrue(Steuerwerte.geschaetzt(2026, d) { it.basiszins })
    }

    @Test fun tarifMitSechsZonenBisSiebenundvierzigProzent() {
        // Erfundene Werte, nur fuer den Aufbau: eine weitere Zone ueber 280.000 Euro mit 47 Prozent
        val d = Steuerwerte.lesen("""{"v":1,"jahre":{$voll2026,"2028":{"tarif":[{"bis":13000},{"bis":18000,"ab":13000,"a":900,"b":1400},{"bis":70000,"ab":18000,"a":170,"b":2400,"c":1000},{"bis":200000,"satz":0.42,"abzug":11000},{"bis":280000,"satz":0.45,"abzug":17000},{"satz":0.47,"abzug":22600}]}}}""")
        assertNotNull(d)
        Steuerwerte.setzen(d)
        val t = Steuerwerte.fuer(2028).tarif!!
        assertEquals(6, t.size)
        assertEquals(floor(0.47 * 300_000 - 22_600), Steuerwerte.einkommensteuer(300_000.0, t), 0.0)
        assertEquals(floor(0.45 * 250_000 - 17_000), Steuerwerte.einkommensteuer(250_000.0, t), 0.0)
        // Uebrige Felder 2028 aus 2026, geschaetzt
        assertEquals(1_000.0, Steuerwerte.fuer(2028).pauschbetrag, 0.0)
        assertTrue(Steuerwerte.geschaetzt(2028) { it.pauschbetragFeld })
        assertEquals(3.20, Steuerwerte.fuer(2028).basiszins!!, 0.0)
        // 2027 hat keinen Eintrag: Tarif 2026
        assertEquals(5, Steuerwerte.fuer(2027).tarif!!.size)
    }

    @Test fun kaputterTarifMachtDieDateiUngueltig() {
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"tarif":[]}}}"""))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"tarif":[{"bis":20000},{"bis":10000},{"satz":0.45}]}}}"""))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"tarif":[{"bis":20000},{"bis":30000}]}}}"""))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"tarif":[{"bis":20000},{"satz":1.5}]}}}"""))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"basiszins":99.0}}}"""))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"soliFreigrenze":-1}}}"""))
    }

    // So las Fassung 0.73/0.74 die Datei
    @Serializable private data class AltJahr(val abgeltung: Double = 25.0, val soli: Double = 5.5, val pauschbetrag: Double = 1_000.0, val pauschbetragPaar: Double = 2_000.0, val basiszins: Double = 0.0)
    @Serializable private data class AltDatei(val v: Int = 1, val stand: String = "", val jahre: Map<String, AltJahr> = emptyMap())

    @Test fun aeltereFassungenLesenDieNeueDatei() {
        val alt = Json { ignoreUnknownKeys = true }.decodeFromString(AltDatei.serializer(), asset(Steuerwerte.DATEI))
        assertEquals(setOf("2023", "2024", "2025", "2026"), alt.jahre.keys)
        alt.jahre.values.forEach { j ->
            assertEquals(25.0, j.abgeltung, 0.0); assertEquals(5.5, j.soli, 0.0)
            assertEquals(1_000.0, j.pauschbetrag, 0.0); assertEquals(2_000.0, j.pauschbetragPaar, 0.0)
            assertTrue(j.basiszins > 0)
        }
        // Und die neue Fassung liest eine alte Datei ohne die neuen Felder
        val d = Steuerwerte.lesen("""{"v":1,"stand":"2026-11-09","jahre":{"2026":{"abgeltung":25.0,"soli":5.5,"pauschbetrag":1000,"pauschbetragPaar":2000,"basiszins":3.20}}}""")!!
        assertNull(Steuerwerte.fuer(2026, d).tarif)
        assertEquals(0.26375, Steuerwerte.fuer(2026, d).satz, 1e-12)
    }
}
