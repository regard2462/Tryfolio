package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.PdfText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Meldungen des US-Repraesentantenhauses lesen. Die Testdateien sind echte, oeffentliche Transaktionsmeldungen
// aus dem Jahr 2026 (verschluesselte PDFs mit Textlayer) und ein Ausschnitt des Jahresverzeichnisses.
class KongressTest {

    private fun datei(name: String) = javaClass.classLoader!!.getResourceAsStream("kongress/$name")!!.readBytes()
    private fun tag(s: String) = LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun geschaefte(doc: String) = Kongress.geschaefte(PdfText.zeilen(datei("$doc.pdf")))

    @Test
    fun readsEncryptedReportText() {
        val z = PdfText.zeilen(datei("20034836.pdf"))
        assertTrue(z.contains("Hon. Nancy Pelosi"))
        assertTrue(z.any { it.startsWith("Filing ID #20034836") })
        // Eingescannte Papiermeldung: kein Text, keine Geschaefte
        assertTrue(PdfText.zeilen(datei("9115809.pdf")).isEmpty())
        assertTrue(geschaefte("9115809").isEmpty())
        // Kaputte Daten werfen nicht
        assertTrue(PdfText.zeilen(ByteArray(100) { it.toByte() }).isEmpty())
    }

    @Test
    fun parsesOptionsSpouseAndRanges() {
        val g = geschaefte("20034836")
        assertEquals(2, g.size)
        val intel = g[0]
        assertEquals("INTC", intel.kuerzel)
        assertEquals("Intel Corporation - Common Stock", intel.wert)
        assertEquals("OP", intel.typ)
        assertEquals("SP", intel.inhaber)
        assertEquals(Kongress.Art.KAUF, intel.art)
        assertEquals(tag("2026-05-29"), intel.datum)
        assertEquals(1_000_001L, intel.von)
        assertEquals(5_000_000L, intel.bis)
        assertEquals("UBER", g[1].kuerzel)
        assertEquals(500_001L, g[1].von)
    }

    @Test
    fun parsesSalesJointAccountsAndLongReports() {
        val hern = geschaefte("20033829").single()
        assertEquals("UNH", hern.kuerzel)
        assertEquals("JT", hern.inhaber)
        assertEquals(Kongress.Art.VERKAUF, hern.art)
        assertEquals(tag("2025-12-23"), hern.datum)
        assertEquals(tag("2025-12-24"), hern.gemeldet)
        // Drei Seiten, Aktien, Optionen, Teilverkaeufe und ein Tausch mit Centbetrag
        val pelosi = geschaefte("20033725")
        assertEquals(18, pelosi.size)
        assertTrue(pelosi.any { it.kuerzel == "GOOGL" && it.typ == "OP" && it.art == Kongress.Art.KAUF })
        assertTrue(pelosi.any { it.kuerzel == "AAPL" && it.verkauf && it.von == 5_000_001L })
        assertEquals(15L, pelosi.first { it.kuerzel == "VSNT" }.von)
        // Zeile ueber einen Seitenumbruch geteilt: Art, Daten und Betragsanfang vor dem Seitenkopf, Kuerzel danach
        val tempus = pelosi.first { it.kuerzel == "TEM" }
        assertEquals("Tempus AI, Inc. - Class A Common Stock", tempus.wert)
        assertEquals(Kongress.Art.KAUF, tempus.art)
        assertEquals(50_001L, tempus.von)
        assertEquals(100_000L, tempus.bis)
        // Unterkonten ohne Inhaberkennung (Trust); Schnipsel der Unterkonto-Zeile gehoeren nicht zum Namen
        val taylor = geschaefte("20033845")
        assertEquals(9, taylor.size)
        assertTrue(taylor.all { it.inhaber.isEmpty() && !it.wert.contains("Taylor") })
        assertEquals("International Business Machines Corporation Common Stock", taylor.first { it.kuerzel == "IBM" }.wert)
        // Hinterlegungsscheine und lange Namen ueber drei Zeilen
        val gott = geschaefte("20033756")
        assertTrue(gott.any { it.kuerzel == "SE" && it.wert.startsWith("Sea Limited American Depositary Shares") })
        assertTrue(geschaefte("20033830").all { it.art == Kongress.Art.TAUSCH })
    }

    @Test
    fun readsShortNameLinesAndDatesInBondNames() {
        // Zeilen wie aus PdfText: Zellen als ganze Zeilen, Zusatzangaben im Blocksatz als Schnipsel
        val kopf = listOf("Filing ID #20099999", "ID", "Owner", "Asset", "Cap", ".", "G", "ai", "n", "s >", "$", "2", "0", "0", "?")
        val schnipsel = listOf("F", "S", ":", "N", "ew", "S", "O", ":", "Ex", "a", "m", "p", "le", "T", "ru", "st")
        // Name mit kurzer letzter Zeile („Bonds“), ohne Inhaberkennung; davor Schnipsel des vorigen Geschaefts
        val kurz = Kongress.geschaefte(
            kopf +
                listOf("Lakeview Water District Revenue", "Bonds", "[GS]", "S", "09/29/2026", "10/09/2026", "$100,001 -", "$250,000") +
                schnipsel +
                listOf("Hillcrest School District GO", "Bonds", "[GS]", "P", "09/29/2026", "10/09/2026", "$250,001 -", "$500,000") +
                schnipsel,
        )
        assertEquals(2, kurz.size)
        assertEquals("Lakeview Water District Revenue Bonds", kurz[0].wert)
        assertEquals(Kongress.Art.VERKAUF, kurz[0].art)
        assertEquals(100_001L, kurz[0].von)
        assertEquals("Hillcrest School District GO Bonds", kurz[1].wert)
        assertTrue(kurz.all { it.inhaber.isEmpty() && it.kuerzel == null && it.typ == "GS" })
        // Anleihe mit Faelligkeitsdatum im Namen: Name und Inhaber bleiben erhalten, die Spaltendaten nicht
        val anleihe = Kongress.geschaefte(
            kopf +
                listOf("JT", "Ridgeway County School Finance", "AUTH 5.000% MAT 09/01/2028", "[GS]", "P", "09/21/2026", "10/02/2026", "$50,001 -", "$100,000") +
                schnipsel +
                listOf("JT", "Example Industries Inc. Common", "Stock (EXIN)", "[ST]", "S (partial)", "09/17/2026", "10/02/2026", "$15,001 -", "$50,000"),
        )
        assertEquals(2, anleihe.size)
        val a = anleihe[0]
        assertEquals("JT", a.inhaber)
        assertEquals("Ridgeway County School Finance AUTH 5.000% MAT 09/01/2028", a.wert)
        assertNull(a.kuerzel)
        assertEquals(Kongress.Art.KAUF, a.art)
        assertEquals(tag("2026-09-21"), a.datum)
        assertEquals(tag("2026-10-02"), a.gemeldet)
        assertEquals(50_001L, a.von)
        assertEquals(100_000L, a.bis)
        assertEquals("EXIN", anleihe[1].kuerzel)
        assertEquals("Example Industries Inc. Common Stock", anleihe[1].wert)
        assertEquals(Kongress.Art.TEILVERKAUF, anleihe[1].art)
    }

    @Test
    fun aggregatesPositionsAndWeights() {
        val posten = Kongress.posten(geschaefte("20033725"))
        // Optionen zaehlen zum Basiswert, der Tausch und Werte ohne Kuerzel fallen weg
        assertTrue(posten.none { it.kuerzel == "VSNT" })
        val nvda = posten.first { it.kuerzel == "NVDA" }
        assertTrue(nvda.optionen)
        assertEquals((100_001 + 250_000) / 2.0 + (250_001 + 500_000) / 2.0, nvda.kaeufe, 0.5)
        assertEquals((1_000_001 + 5_000_000) / 2.0, nvda.verkaeufe, 0.5)
        val w = Kongress.gewichte(posten, 5)
        assertTrue(w.size <= 5)
        assertEquals(1.0, w.sumOf { it.second }, 1e-9)
        assertTrue(w.all { (k, _) -> posten.first { it.kuerzel == k }.netto > 0 })
        assertTrue(Kongress.gewichte(emptyList(), 5).isEmpty())
    }

    @Test
    fun readsTheYearlyIndex() {
        val xml = String(datei("2026FD-auszug.xml"), Charsets.UTF_8)
        val m = Kongress.verzeichnis(xml)
        assertTrue(m.isNotEmpty())
        assertTrue(m.zipWithNext().all { (a, b) -> a.datum >= b.datum })
        val pelosi = m.filter { it.nachname == "Pelosi" }
        assertEquals(4, pelosi.size)
        assertTrue(pelosi.all { it.elektronisch && it.wahlkreis == "CA11" && it.name == "Nancy Pelosi" })
        assertEquals(tag("2026-10-02"), pelosi.first().datum)
        assertFalse(m.first { it.nachname == "Harshbarger" }.elektronisch)
        val personen = Kongress.personen(m)
        assertEquals(personen.map { it.schluessel }.toSet().size, personen.size)
        assertEquals("https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/2026/20035553.pdf", Kongress.meldungAdresse(2026, "20035553"))
        assertNull(Kongress.xmlAusZip(ByteArray(10)))
    }
}
