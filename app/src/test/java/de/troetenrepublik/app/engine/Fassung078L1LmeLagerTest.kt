package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Fassung078L1LmeLagerTest {

    private fun text(ref: String, t: String) = "<c r=\"$ref\" s=\"4\" t=\"inlineStr\"><is><t>$t</t></is></c>"
    private fun zahl(ref: String, v: Long) = "<c r=\"$ref\" s=\"8\" t=\"n\"><v>$v</v></c>"
    private fun leer(ref: String) = "<c r=\"$ref\" s=\"3\" t=\"n\"></c>"

    private fun blatt(zeilen: List<String>) =
        "<worksheet><sheetData>" + zeilen.mapIndexed { i, z -> "<row r=\"${i + 1}\">$z</row>" }.joinToString("") + "</sheetData></worksheet>"

    private fun xlsx(sheet: String, name: String = "xl/worksheets/sheet1.xml"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write("<Types/>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry(name)); z.write(sheet.toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    // Kopf in den Spalten C bis L (nicht am Rand), Leerzeilen davor und danach, Titelzeile, ein Tag ohne Zinn, ein Tag ohne Datum
    private fun beispiel(): ByteArray {
        val kopf = listOf("BusinessDate", "AA", "AH", "CA", "CO", "NA", "NI", "PB", "SN", "ZS", "Total")
        val spalten = ('C'..'M').toList()
        val kopfZeile = spalten.indices.joinToString("") { text("${spalten[it]}4", kopf[it]) }
        fun tag(n: Int, excel: Long, w: Map<String, Long>, zeile: Int): String {
            var z = leer("A$zeile")
            for ((i, k) in kopf.withIndex()) {
                z += when {
                    i == 0 -> zahl("${spalten[i]}$zeile", excel)
                    k in w -> zahl("${spalten[i]}$zeile", w.getValue(k))
                    else -> leer("${spalten[i]}$zeile")
                }
            }
            return z
        }
        val zeilen = listOf(
            leer("A1"), text("C2", "LME Monthly Stock Report"), "", kopfZeile, "",
            // Excel-Tageszahl 46295 ist der 30.09.2026, 46296 der 01.10.2026
            tag(1, 46295, mapOf("CA" to 100_000, "AH" to 500_000, "SN" to 2_000, "ZS" to 0, "Total" to 602_000), 6),
            tag(2, 46296, mapOf("CA" to 110_000, "AH" to 495_000, "ZS" to 40_000, "Total" to 645_000), 7),
            text("C8", "Notiz ohne Zahl"),
            "", leer("A10"),
        )
        return xlsx(blatt(zeilen))
    }

    @Test fun tageZeilenMitLuecken() {
        val tage = LmeLager.lesen(beispiel())
        assertEquals(2, tage.size)
        assertEquals(46295L - 25569L, tage[0].tag)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), tage[0].tag)
        assertEquals(100_000.0, tage[0].bestand.getValue("CA"), 0.0)
        assertEquals(2_000.0, tage[0].bestand.getValue("SN"), 0.0)
        // Total ist kein Metall; fehlende Spalten fehlen
        assertTrue("Total" !in tage[0].bestand)
        assertNull(tage[1].bestand["SN"])
        assertEquals(40_000.0, tage[1].bestand.getValue("ZS"), 0.0)
    }

    @Test fun keineLmeDatei() {
        assertTrue(LmeLager.lesen("kein zip".toByteArray()).isEmpty())
        assertTrue(LmeLager.lesen(xlsx(blatt(listOf("")), "xl/worksheets/sheet2.xml")).isEmpty())
        assertTrue(LmeLager.lesen(xlsx(blatt(listOf(text("A1", "Etwas anderes"), zahl("A2", 46295))))).isEmpty())
        assertTrue(LmeLager.blattLesen("").isEmpty())
    }

    @Test fun kopfNichtAnFesterStelle() {
        val kopf = text("F9", "BusinessDate") + text("G9", "NI") + text("H9", "CA")
        val z = zahl("F10", 46296) + zahl("G10", 5) + zahl("H10", 7)
        val t = LmeLager.blattLesen(blatt(List(8) { "" } + listOf(kopf, z)))
        assertEquals(1, t.size)
        assertEquals(mapOf("NI" to 5.0, "CA" to 7.0), t[0].bestand)
    }

    @Test fun monate() {
        val m = LmeLager.monate(LocalDate.of(2026, 11, 12))
        assertEquals(12, m.size)
        assertEquals(LmeLager.Monat(2026, 10), m.first())
        assertEquals(LmeLager.Monat(2025, 11), m.last())
        assertEquals(LmeLager.Monat(2025, 12), LmeLager.monate(LocalDate.of(2026, 1, 3)).first())
    }

    @Test fun speichernUndLaden() {
        val tage = LmeLager.lesen(beispiel())
        assertEquals(tage, LmeLager.laden(LmeLager.speichern(tage)))
        assertTrue(LmeLager.laden("Muell\n\n;;;").isEmpty())
    }

    private fun tag(y: Int, m: Int, d: Int, vararg w: Pair<String, Double>) = LmeLager.Tag(LocalDate.of(y, m, d).toEpochDay(), w.toMap())

    @Test fun auswertungVeraenderungUndReihenfolge() {
        val s = LmeLager.auswerten(
            listOf(
                tag(2026, 10, 30, "SN" to 2_000.0, "CA" to 110_000.0, "CO" to 100.0, "AH" to 495_000.0, "ZS" to 40_000.0),
                tag(2026, 9, 30, "SN" to 2_000.0, "CA" to 100_000.0, "AH" to 500_000.0, "CO" to 0.0, "ZS" to 50_000.0),
                tag(2026, 9, 29, "CA" to 1.0),
                tag(2026, 8, 31, "CA" to 90_000.0),
            ),
        )!!
        assertEquals(LocalDate.of(2026, 10, 30).toEpochDay(), s.tag)
        assertEquals(listOf("CA", "AH", "ZS", "SN", "CO"), s.metalle.map { it.kuerzel })
        val ca = s.metalle[0]
        assertEquals(10.0, ca.aenderung!!, 1e-9)
        assertEquals(4, ca.verlauf.size)
        assertEquals(-1.0, s.metalle[1].aenderung!!, 1e-9)
        assertEquals(-20.0, s.metalle[2].aenderung!!, 1e-9)
        assertEquals(0.0, s.metalle[3].aenderung!!, 1e-9)
        // Vormonatsbestand null: keine Veraenderung
        assertNull(s.metalle[4].aenderung)
    }

    @Test fun auswertungOhneVormonat() {
        val s = LmeLager.auswerten(listOf(tag(2026, 10, 30, "CA" to 5.0), tag(2026, 8, 31, "CA" to 4.0)))!!
        assertNull(s.metalle[0].aenderung)
        assertNotNull(s)
        assertNull(LmeLager.auswerten(emptyList()))
    }
}
