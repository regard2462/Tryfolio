package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.LmeLagerAbruf
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Fassung078L2LmeEinlesenTest {

    private fun text(ref: String, t: String) = "<c r=\"$ref\" t=\"inlineStr\"><is><t>$t</t></is></c>"
    private fun zahl(ref: String, v: Long) = "<c r=\"$ref\" t=\"n\"><v>$v</v></c>"

    private fun excel(d: LocalDate) = d.toEpochDay() + 25_569L

    // Kopfzeile und je Datum eine Zeile mit Kupfer und Zinn
    private fun xlsx(tage: List<Pair<LocalDate, Long>>): ByteArray {
        val zeilen = ArrayList<String>()
        zeilen += text("A1", "BusinessDate") + text("B1", "CA") + text("C1", "SN")
        for ((i, t) in tage.withIndex()) zeilen += zahl("A${i + 2}", excel(t.first)) + zahl("B${i + 2}", t.second) + zahl("C${i + 2}", 100)
        val blatt = "<worksheet><sheetData>" + zeilen.mapIndexed { i, z -> "<row r=\"${i + 1}\">$z</row>" }.joinToString("") + "</sheetData></worksheet>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(blatt.toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    private fun abruf(ordner: File) = LmeLagerAbruf(MarketApi(), ordner)

    @Test fun zweiMonateEinlesenUndLaden(): Unit = runBlocking {
        val ordner = Files.createTempDirectory("lme").toFile()
        val a = abruf(ordner)
        val daten = xlsx(
            listOf(
                LocalDate.of(2026, 9, 29) to 1_000L, LocalDate.of(2026, 9, 30) to 1_100L,
                LocalDate.of(2026, 10, 1) to 1_200L, LocalDate.of(2026, 10, 30) to 1_300L,
            ),
        )
        assertEquals(4, a.einlesen(daten))
        // Neue Instanz liest von der Platte
        val e = abruf(ordner).laden(LocalDate.of(2026, 11, 12))
        assertEquals(4, e.tage.size)
        assertEquals(setOf(9, 10), e.tage.map { LocalDate.ofEpochDay(it.tag).monthValue }.toSet())
        val stand = LmeLager.auswerten(e.tage)!!
        assertEquals(1_300.0, stand.metalle.first().tonnen, 1e-9)
        assertEquals(setOf("stocks-2026-09.txt", "stocks-2026-10.txt"), ordner.list()!!.toSet())

        // Neue Tage kommen zum Monat hinzu, die uebrigen Tage bleiben
        assertEquals(1, a.einlesen(xlsx(listOf(LocalDate.of(2026, 10, 15) to 5L))))
        val neu = a.laden(LocalDate.of(2026, 11, 12))
        assertEquals(5, neu.tage.size)
        assertTrue(neu.tage.any { it.bestand["CA"] == 5.0 })
        // Gleicher Tag wird ersetzt
        assertEquals(1, a.einlesen(xlsx(listOf(LocalDate.of(2026, 10, 15) to 6L))))
        val ersetzt = a.laden(LocalDate.of(2026, 11, 12))
        assertEquals(5, ersetzt.tage.size)
        assertEquals(6.0, ersetzt.tage.first { it.tag == LocalDate.of(2026, 10, 15).toEpochDay() }.bestand.getValue("CA"), 0.0)
        ordner.deleteRecursively()
    }

    // Eine Datei mit nur einem Tag des Vormonats loescht den Vormonat nicht
    @Test fun nachbartagLoeschtMonatNicht() = runBlocking<Unit> {
        val ordner = Files.createTempDirectory("lme").toFile()
        val a = abruf(ordner)
        a.einlesen(xlsx((1..20).map { LocalDate.of(2026, 9, it) to 100L + it }))
        a.einlesen(xlsx(listOf(LocalDate.of(2026, 9, 30) to 900L, LocalDate.of(2026, 10, 1) to 910L)))
        val e = a.laden(LocalDate.of(2026, 11, 12))
        assertEquals(22, e.tage.size)
        assertEquals(21, e.tage.count { LocalDate.ofEpochDay(it.tag).monthValue == 9 })
        ordner.deleteRecursively()
    }

    // Der laufende Monat erscheint im Verlauf
    @Test fun laufenderMonatWirdGeladen() = runBlocking<Unit> {
        val ordner = Files.createTempDirectory("lme").toFile()
        val a = abruf(ordner)
        a.einlesen(xlsx(listOf(LocalDate.of(2026, 10, 30) to 1L, LocalDate.of(2026, 11, 2) to 2L, LocalDate.of(2026, 11, 10) to 3L)))
        val e = a.laden(LocalDate.of(2026, 11, 12))
        assertEquals(3, e.tage.size)
        assertEquals(LocalDate.of(2026, 11, 10).toEpochDay(), LmeLager.auswerten(e.tage)!!.tag)
        ordner.deleteRecursively()
    }

    @Test fun keineXlsxErgibtNull() {
        val ordner = Files.createTempDirectory("lme").toFile()
        val a = abruf(ordner)
        assertEquals(0, a.einlesen("a;b\n1;2".toByteArray()))
        assertEquals(0, a.einlesen(ByteArray(0)))
        assertEquals(0, a.einlesen(xlsx(emptyList())))
        assertTrue(ordner.list().isNullOrEmpty())
        ordner.deleteRecursively()
    }

    @Test fun keineMonatsadresseImQuelltext() {
        val wurzel = listOf("src/main/java", "app/src/main/java", "../app/src/main/java").map { File(it) }.first { it.isDirectory }
        val treffer = wurzel.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { "stocks-summary/stocks-" in it.readText() }.map { it.name }.toList()
        assertFalse("Monatsadresse gefunden: $treffer", treffer.isNotEmpty())
    }
}
