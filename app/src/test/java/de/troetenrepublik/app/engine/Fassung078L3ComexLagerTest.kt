package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.ComexLagerAbruf
import de.troetenrepublik.app.market.LagerDatei
import de.troetenrepublik.app.market.LmeLagerAbruf
import de.troetenrepublik.app.market.MarketApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Fassung078L3ComexLagerTest {

    // ------------------------------------------------------------ Hilfen: BIFF8-Strom und OLE2-Verbunddatei

    private class Formel(val v: Double)

    private fun ByteArrayOutputStream.u8(v: Int) = write(v and 0xFF)
    private fun ByteArrayOutputStream.u16(v: Int) { u8(v); u8(v shr 8) }
    private fun ByteArrayOutputStream.u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
    private fun ByteArrayOutputStream.f64(v: Double) { val b = v.toBits(); u32(b and 0xFFFFFFFFL); u32((b ushr 32) and 0xFFFFFFFFL) }

    private fun satz(art: Int, daten: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.u16(art); o.u16(daten.size); o.write(daten)
        return o.toByteArray()
    }

    private fun satz(art: Int, bau: ByteArrayOutputStream.() -> Unit): ByteArray = satz(art, ByteArrayOutputStream().also(bau).toByteArray())

    // Zahl als RK-Wert, wenn sie als 30-Bit-Ganzzahl oder mit zwei Nachkommastellen darstellbar ist
    private fun rkWert(v: Double): Long? {
        if (v == Math.rint(v) && Math.abs(v) < 500_000_000) return ((v.toLong() shl 2) and 0xFFFFFFFFL) or 2L
        val c = v * 100
        if (Math.abs(c - Math.rint(c)) < 1e-9 && Math.abs(c) < 500_000_000 && Math.rint(c) / 100.0 == v) return ((Math.rint(c).toLong() shl 2) and 0xFFFFFFFFL) or 3L
        return null
    }

    // Blatt: Zeilen mit Text (LABELSST), Zahlen (RK, MULRK bei zwei und mehr RK nebeneinander, sonst NUMBER) und Formeln
    private fun blattSaetze(zeilen: List<List<Any?>>, vorrat: List<String>): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(satz(0x0809) { u16(0x0600); u16(0x0010); u16(0); u16(0); u32(0); u32(6) })
        for ((r, z) in zeilen.withIndex()) {
            var c = 0
            while (c < z.size) {
                val w = z[c]
                val spalte = c
                when {
                    w == null -> c++
                    w is String -> { o.write(satz(0x00FD) { u16(r); u16(spalte); u16(15); u32(vorrat.indexOf(w).toLong()) }); c++ }
                    w is Formel -> { o.write(satz(0x0006) { u16(r); u16(spalte); u16(15); f64(w.v); u16(0); u32(0) }); c++ }
                    w is Double -> {
                        var e = c
                        while (e < z.size && z[e] is Double && rkWert(z[e] as Double) != null) e++
                        if (e - c >= 2) {
                            val bis = e
                            o.write(satz(0x00BD) { u16(r); u16(spalte); for (k in spalte until bis) { u16(15); u32(rkWert(z[k] as Double)!!) }; u16(bis - 1) })
                            c = e
                        } else {
                            val rk = rkWert(w)
                            if (rk != null) o.write(satz(0x027E) { u16(r); u16(spalte); u16(15); u32(rk) })
                            else o.write(satz(0x0203) { u16(r); u16(spalte); u16(15); f64(w) })
                            c++
                        }
                    }
                    else -> c++
                }
            }
        }
        o.write(satz(0x000A, ByteArray(0)))
        return o.toByteArray()
    }

    // Textvorrat als SST mit CONTINUE: der Schnitt liegt mitten in der Zeichenkette Nummer schnittIndex (nach schnittZeichen
    // Zeichen), diese Zeichenkette steht in Unicode (zwei Bytes je Zeichen), die uebrigen in einem Byte je Zeichen.
    private fun vorratSaetze(texte: List<String>, schnittIndex: Int, schnittZeichen: Int): ByteArray {
        val a = ByteArrayOutputStream()
        a.u32(texte.size.toLong()); a.u32(texte.size.toLong())
        var b: ByteArrayOutputStream? = null
        for ((i, t) in texte.withIndex()) {
            if (i == schnittIndex) {
                a.u16(t.length); a.u8(1)
                for (k in 0 until schnittZeichen) a.u16(t[k].code)
                val n = ByteArrayOutputStream()
                n.u8(1)
                for (k in schnittZeichen until t.length) n.u16(t[k].code)
                b = n
            } else {
                val ziel = b ?: a
                ziel.u16(t.length); ziel.u8(0)
                for (ch in t) ziel.u8(ch.code)
            }
        }
        return satz(0x00FC, a.toByteArray()) + satz(0x003C, b!!.toByteArray())
    }

    private fun strom(zeilen: List<List<Any?>>): ByteArray {
        val texte = zeilen.flatMap { it }.filterIsInstance<String>().distinct()
        val schnittIndex = texte.indexOfFirst { it.length > 8 }
        val vorrat = vorratSaetze(texte, schnittIndex, 4)
        val bof = satz(0x0809) { u16(0x0600); u16(0x0005); u16(0); u16(0); u32(0); u32(6) }
        val eof = satz(0x000A, ByteArray(0))
        val blattName = ByteArrayOutputStream().also { it.u8(11); it.u8(0); for (ch in "Meldung_001") it.u8(ch.code) }.toByteArray()
        val laenge = bof.size + (4 + 6 + blattName.size) + vorrat.size + eof.size
        val bs = satz(0x0085) { u32(laenge.toLong()); u8(0); u8(0); write(blattName) }
        val o = ByteArrayOutputStream()
        o.write(bof); o.write(bs); o.write(vorrat); o.write(eof)
        check(o.size() == laenge) { "Blattversatz " + o.size() + " statt " + laenge }
        o.write(blattSaetze(zeilen, texte))
        return o.toByteArray()
    }

    private fun verbunddatei(strom: ByteArray, name: String = "Workbook"): ByteArray {
        val ende = 0xFFFFFFFEL
        val frei = 0xFFFFFFFFL
        val fatSek = 0xFFFFFFFDL
        val klein = strom.size < 4096
        val fat = ArrayList<Long>()
        val daten = ByteArrayOutputStream()
        val verz = ByteArrayOutputStream()
        fun eintrag(n: String, art: Int, start: Long, groesse: Long) {
            val e = ByteArrayOutputStream()
            val nb = ByteArrayOutputStream()
            for (ch in n) nb.u16(ch.code)
            e.write(nb.toByteArray().copyOf(64))
            e.u16(if (n.isEmpty()) 0 else n.length * 2 + 2)
            e.u8(art); e.u8(1)
            e.u32(frei); e.u32(frei); e.u32(frei)
            e.write(ByteArray(16 + 4 + 8 + 8))
            e.u32(start); e.u32(groesse); e.u32(0)
            verz.write(e.toByteArray())
        }
        var miniFatStart = ende
        var miniZahl = 0L
        if (klein) {
            // Mini-Stream: Sektor 2 ist die Mini-FAT, ab Sektor 3 liegt der Ministrom der Wurzel
            val m = (strom.size + 63) / 64
            val miniFat = ByteArrayOutputStream()
            for (i in 0 until 128) miniFat.u32(if (i < m - 1) (i + 1).toLong() else if (i == m - 1) ende else frei)
            val mini = strom.copyOf(((m * 64 + 511) / 512) * 512)
            val miniSek = mini.size / 512
            fat += fatSek; fat += ende; fat += ende
            for (i in 0 until miniSek) fat += if (i < miniSek - 1) (3 + i + 1).toLong() else ende
            daten.write(miniFat.toByteArray()); daten.write(mini)
            eintrag("Root Entry", 5, 3, (m * 64).toLong())
            eintrag(name, 2, 0, strom.size.toLong())
            miniFatStart = 2; miniZahl = 1
        } else {
            val k = (strom.size + 511) / 512
            fat += fatSek; fat += ende
            for (i in 0 until k) fat += if (i < k - 1) (2 + i + 1).toLong() else ende
            daten.write(strom.copyOf(k * 512))
            eintrag("Root Entry", 5, ende, 0)
            eintrag(name, 2, 2, strom.size.toLong())
        }
        eintrag("", 0, 0, 0); eintrag("", 0, 0, 0)
        check(fat.size <= 128)
        val fatBytes = ByteArrayOutputStream()
        for (i in 0 until 128) fatBytes.u32(fat.getOrElse(i) { frei })
        val kopf = ByteArrayOutputStream()
        for (b in intArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) kopf.u8(b)
        kopf.write(ByteArray(16)); kopf.u16(0x3E); kopf.u16(3); kopf.u16(0xFFFE); kopf.u16(9); kopf.u16(6)
        kopf.write(ByteArray(6)); kopf.u32(0); kopf.u32(1); kopf.u32(1); kopf.u32(0); kopf.u32(4096)
        kopf.u32(miniFatStart); kopf.u32(miniZahl); kopf.u32(ende); kopf.u32(0)
        kopf.u32(0)
        for (i in 1 until 109) kopf.u32(frei)
        check(kopf.size() == 512)
        val o = ByteArrayOutputStream()
        o.write(kopf.toByteArray()); o.write(fatBytes.toByteArray()); o.write(verz.toByteArray()); o.write(daten.toByteArray())
        return o.toByteArray()
    }

    private val TAG = LocalDate.of(2026, 11, 11).toEpochDay()

    private fun abschnitt(
        metall: String, einheit: String, datum: String, kopf: String, gesamtName: String, registered: Double, eligible: Double, pledged: Double?,
        netto: Double, mitLager: Boolean,
    ): List<List<Any?>> {
        val z = ArrayList<List<Any?>>()
        z += listOf(metall, null, null, null, null, null, "Report Date: $datum", null, null)
        z += listOf(einheit, null, null, null, null, null, "Activity Date: 11/10/2026", null, null)
        z += emptyList<Any?>()
        z += listOf(kopf, null, "PREV TOTAL", "RECEIVED", "WITHDRAWN", "NET CHANGE", "ADJUSTMENT", "TOTAL TODAY", null)
        if (mitLager) {
            z += listOf("ASAHI DEPOSITORY LLC")
            z += listOf("  Registered", null, 10.0, 0.0, 0.0, 0.0, 0.0, 10.0, null)
            z += listOf("  Total", null, 10.0, 0.0, 0.0, 0.0, 0.0, 10.0, null)
        }
        z += listOf("Total", null, 3.0, 0.0, 0.0, 0.0, 0.0, 3.0, null)
        z += listOf(if (mitLager) "TOTAL REGISTERED" else "Total Registered (warranted)", null, registered, 0.0, 0.0, 0.0, 0.0, registered, null)
        if (pledged != null) z += listOf("TOTAL PLEDGED", null, pledged, null, null, null, null, pledged, null)
        z += listOf(if (mitLager) "TOTAL ELIGIBLE" else "Total Eligible (non-warranted)", null, eligible - netto, 0.0, -netto, netto, 0.0, eligible, null)
        z += listOf(gesamtName, null, registered + eligible - netto, 0.0, -netto, netto, 0.0, Formel(registered + eligible), null)
        z += emptyList<Any?>()
        return z
    }

    // Eine Datei mit Gold, Kupfer und einem unbekannten Metall (wird uebersprungen)
    private fun tabelle(datum: String, gold: Double, kupfer: Double): List<List<Any?>> {
        val z = ArrayList<List<Any?>>()
        z += emptyList<Any?>()
        z += listOf("COMMODITY EXCHANGE, INC.")
        z += listOf("METAL DEPOSITORY STATISTICS")
        z.addAll(abschnitt("GOLD", "Troy Ounce", datum, "DEPOSITORY", "COMBINED TOTAL", gold, 8754373.909, 1676023.357, -65427.285, true))
        z.addAll(abschnitt("NICKEL", "Metric Tons", datum, "DELIVERY POINT", "TOTAL NICKEL", 5.0, 6.0, null, 0.0, false))
        z.addAll(abschnitt("COPPER - HIGH GRADE", "Short Tons", datum, "DELIVERY POINT", "TOTAL COPPER", kupfer, 328246.0, null, -256.0, false))
        z += listOf("This report is produced for information purposes only.")
        return z
    }

    private fun fuellzeilen(n: Int): List<List<Any?>> = (0 until n).map { listOf("Hinweis Nummer $it zu den gemeldeten Lagerhaeusern und Bestaenden", null, it.toDouble() + 0.25) }

    private fun xls(datum: String, gold: Double, kupfer: Double): ByteArray = verbunddatei(strom(tabelle(datum, gold, kupfer)))

    // ------------------------------------------------------------ Tests

    @Test fun leserLiestGoldUndKupferAusKleinerDatei() {
        val strom = strom(tabelle("11/11/2026", 14241501.372, 478924.0))
        assertTrue("Strom muss im Mini-Stream liegen", strom.size < 4096)
        val e = ComexLager.lesen(verbunddatei(strom))
        assertEquals(listOf("GOLD", "KUPFER"), e.map { it.metall })
        val g = e[0]
        assertEquals(ComexLager.OZ, g.einheit)
        assertEquals(TAG, g.tag)
        assertEquals(14241501.372, g.registered!!, 1e-6)
        assertEquals(8754373.909, g.eligible!!, 1e-6)
        assertEquals(1676023.357, g.pledged!!, 1e-6)
        assertEquals(14241501.372 + 8754373.909, g.gesamt, 1e-6)
        assertEquals(-65427.285, g.aenderung!!, 1e-6)
        val k = e[1]
        assertEquals(ComexLager.SHT, k.einheit)
        assertNull(k.pledged)
        assertEquals(478924.0, k.registered!!, 0.0)
        assertEquals(328246.0, k.eligible!!, 0.0)
        assertEquals(478924.0 + 328246.0, k.gesamt, 0.0)
        assertEquals(-256.0, k.aenderung!!, 0.0)
    }

    @Test fun grosseDateiGibtDasselbeErgebnis() {
        val kleinStrom = strom(tabelle("11/11/2026", 14241501.372, 478924.0))
        val gross = strom(tabelle("11/11/2026", 14241501.372, 478924.0) + fuellzeilen(120))
        assertTrue("Strom muss ueber 4096 Bytes liegen", gross.size > 4096)
        assertEquals(ComexLager.lesen(verbunddatei(kleinStrom)), ComexLager.lesen(verbunddatei(gross)))
        // Aeltere Dateien nennen den Strom Book
        assertEquals(2, ComexLager.lesen(verbunddatei(gross, "Book")).size)
    }

    @Test fun tabelleHatTextUndZahlen() {
        val t = XlsTabelle.lesen(xls("11/11/2026", 1.0, 2.0))
        assertEquals("GOLD", t[3][0])
        assertEquals("Report Date: 11/11/2026", t[3][6])
        assertEquals("  Registered", t[8][0])
        assertEquals(10.0, t[8][2])
        assertNull(t[3][1])
    }

    @Test fun zweiTageSpeichernUndUeberschreiben() {
        val ordner = Files.createTempDirectory("comex").toFile()
        try {
            val a = ComexLagerAbruf(ordner)
            assertEquals(2, a.einlesen(xls("11/10/2026", 10_000_000.0, 400_000.0)))
            assertEquals(2, a.einlesen(xls("11/11/2026", 14_241_501.372, 478_924.0)))
            assertEquals(setOf("GOLD.txt", "KUPFER.txt"), ordner.list()!!.toSet())
            val tage = ComexLagerAbruf(ordner).laden()
            assertEquals(2, tage.getValue("GOLD").size)
            val stand = ComexLager.auswerten(tage)!!
            assertEquals(listOf("GOLD", "KUPFER"), stand.metalle.map { it.schluessel })
            val gold = stand.metalle[0]
            assertEquals(TAG, stand.tag)
            assertEquals(2, gold.verlauf.size)
            val jetzt = 14_241_501.372 + 8754373.909
            val vorher = 10_000_000.0 + 8754373.909
            assertEquals((jetzt / vorher - 1) * 100, gold.aenderung!!, 1e-9)

            // Derselbe Tag noch einmal mit anderem Wert: ueberschreibt, es bleiben zwei Tage
            assertEquals(2, a.einlesen(xls("11/11/2026", 1_000.0, 478_924.0)))
            val neu = ComexLager.auswerten(a.laden())!!.metalle[0]
            assertEquals(2, neu.verlauf.size)
            assertEquals(1_000.0 + 8754373.909, neu.letzter.gesamt, 1e-6)
            assertEquals(2, File(ordner, "GOLD.txt").readLines().size)
        } finally {
            ordner.deleteRecursively()
        }
    }

    @Test fun ohneVergleichstagKeineAenderung() {
        val ordner = Files.createTempDirectory("comex").toFile()
        try {
            val a = ComexLagerAbruf(ordner)
            a.einlesen(xls("11/11/2026", 1.0, 2.0))
            val m = ComexLager.auswerten(a.laden())!!.metalle
            assertTrue(m.all { it.aenderung == null && it.verlauf.size == 1 })
        } finally {
            ordner.deleteRecursively()
        }
    }

    @Test fun hoechstens400TageBleiben() {
        var l = emptyList<ComexLager.Eintrag>()
        for (i in 0 until 405) l = ComexLager.zusammenfuehren(l, ComexLager.Eintrag("GOLD", ComexLager.OZ, 20_000L + i, 1.0, 2.0, null, 3.0 + i, null))
        assertEquals(400, l.size)
        assertEquals(20_005L, l.first().tag)
        assertEquals(20_404L, l.last().tag)
        assertEquals(l, ComexLager.laden("GOLD", ComexLager.speichern(l)))
    }

    @Test fun keineXlsErgibtNichts() {
        val ordner = Files.createTempDirectory("comex").toFile()
        try {
            val a = ComexLagerAbruf(ordner)
            val gut = xls("11/11/2026", 1.0, 2.0)
            assertEquals(0, a.einlesen("a;b".toByteArray()))
            assertEquals(0, a.einlesen(ByteArray(0)))
            assertEquals(0, a.einlesen(gut.copyOf(600)))
            assertEquals(0, a.einlesen(ByteArray(5000)))
            assertTrue(XlsTabelle.lesen(ByteArray(3_000_000)).isEmpty())
            // Beschaedigte Dateien werfen nie eine Ausnahme
            for (i in 512 until gut.size step 37) {
                val kaputt = gut.copyOf()
                kaputt[i] = (kaputt[i] + 91).toByte()
                kaputt[(i * 7) % kaputt.size] = 0x7F
                ComexLager.lesen(kaputt)
            }
            // Strom ohne Metallname
            assertTrue(ComexLager.lesen(verbunddatei(strom(listOf(listOf("Report Date: 11/11/2026", null, "x"))))).isEmpty())
            assertTrue(ordner.list().isNullOrEmpty())
        } finally {
            ordner.deleteRecursively()
        }
    }

    private fun xlsxLme(): ByteArray {
        val blatt = "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>BusinessDate</t></is></c><c r=\"B1\" t=\"inlineStr\"><is><t>CA</t></is></c></row>" +
            "<row r=\"2\"><c r=\"A2\" t=\"n\"><v>" + (LocalDate.of(2026, 10, 30).toEpochDay() + 25569) + "</v></c><c r=\"B2\" t=\"n\"><v>1300</v></c></row></sheetData></worksheet>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(blatt.toByteArray()); z.closeEntry() }
        return out.toByteArray()
    }

    @Test fun eineAuswahlErkenntBeideArtenAmInhalt() {
        val lme = Files.createTempDirectory("lme").toFile()
        val comex = Files.createTempDirectory("comex").toFile()
        try {
            val l = LmeLagerAbruf(MarketApi(), lme)
            val c = ComexLagerAbruf(comex)
            val r1 = LagerDatei.einlesen(xls("11/11/2026", 1.0, 2.0), l, c)
            assertEquals(LagerDatei.Art.COMEX, r1.art)
            assertEquals(2, r1.anzahl)
            val r2 = LagerDatei.einlesen(xlsxLme(), l, c)
            assertEquals(LagerDatei.Art.LME, r2.art)
            assertEquals(1, r2.anzahl)
            assertEquals(LagerDatei.Art.KEINE, LagerDatei.einlesen("kein Excel".toByteArray(), l, c).art)
            assertEquals(LagerDatei.Art.KEINE, LagerDatei.einlesen(ByteArray(0), l, c).art)
        } finally {
            lme.deleteRecursively()
            comex.deleteRecursively()
        }
    }

    @Test fun keineDateiadresseDerCmeImQuelltext() {
        val wurzel = listOf("src/main/java", "app/src/main/java", "../app/src/main/java").map { File(it) }.first { it.isDirectory }
        val verboten = "delivery" + "_reports"
        val treffer = wurzel.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { verboten in it.readText() }.map { it.name }.toList()
        assertFalse("Dateiadresse gefunden: $treffer", treffer.isNotEmpty())
        assertNotNull(ComexLager.BERICHT)
    }
}
