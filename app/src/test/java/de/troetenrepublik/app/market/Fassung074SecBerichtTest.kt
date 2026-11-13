package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.SecBericht.Posten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.time.LocalDate

// Umsatz nach Segment, Region und Grosskunde aus der XBRL-Instanz eines Jahresberichts. Die Dateien unter
// src/test/resources/sec sind Auszuege echter Berichte (nur Kontexte und Angaben zu Umsatz und Konzentration).
class Fassung074SecBerichtTest {
    private fun text(name: String): String = javaClass.classLoader!!.getResourceAsStream("sec/$name.xml")!!.readBytes().decodeToString()

    private fun auswerten(xml: String): SecBericht.Auswertung {
        val roh = Xbrl.lesen(xml.byteInputStream(), SecBericht.MAX_BYTES)
        return Xbrl.auswerten(roh)!!
    }

    private fun lesen(name: String) = auswerten(text(name))

    private fun namen(l: List<Posten>) = l.map { it.n }

    @Test(timeout = 20_000)
    fun nvidiaSegmenteUndRegionen() {
        val b = lesen("nvidia")
        assertEquals("10-K", b.form)
        assertEquals("2026-01-25", b.stichtag)
        assertEquals(2026, b.jahr)
        assertEquals(215_938_000_000.0, b.gesamt, 1.0)
        assertEquals(0, b.segmenteStatus)
        assertEquals(listOf("Compute And Networking", "Graphics"), namen(b.segmente))
        assertEquals(193_479.0 / 215_938.0, b.segmente[0].w, 1e-6)
        assertEquals(1.0, b.segmente.sumOf { it.w }, 1e-9)
        assertEquals(0, b.regionenStatus)
        assertEquals(listOf("US", "TW", "China Including Hong Kong", "Other Countries"), namen(b.regionen))
        // Laender tragen ihr Kuerzel, Sammelglieder ihren Namen
        assertEquals(3, b.regionen[0].k)
        assertEquals("US", b.regionen[0].l)
        assertEquals(0, b.regionen[2].k)
        assertEquals(149_617.0 / 215_938.0, b.regionen[0].w, 1e-6)
        // Kunden mit Anteil an Forderungen oder nur an einem Segment zaehlen nicht
        assertTrue(b.kunden.isEmpty())
    }

    @Test(timeout = 20_000)
    fun appleSegmenteOhneUeberleitung() {
        val b = lesen("apple")
        assertEquals(listOf("Americas", "Europe", "Greater China", "Rest Of Asia Pacific", "Japan"), namen(b.segmente))
        assertEquals(1.0, b.segmente.sumOf { it.w }, 1e-9)
        assertEquals(listOf("Other Countries", "US", "CN"), namen(b.regionen))
        assertEquals("CN", b.regionen[2].l)
        // Produkte (andere Achse) und der Beitrag der Zentrale ohne Umsatz tauchen nicht auf
        assertTrue(b.segmente.none { it.n.contains("Corporate") || it.n.contains("Product") })
    }

    @Test(timeout = 20_000)
    fun microsoftRegionMitNichtUsa() {
        val b = lesen("microsoft")
        assertEquals(listOf("Productivity And Business Processes", "Intelligent Cloud", "More Personal Computing"), namen(b.segmente))
        assertEquals(listOf(3, 5), b.regionen.map { it.k })
        assertEquals(170_794.0 / (170_794.0 + 161_045.0), b.regionen[0].w, 1e-6)
    }

    @Test(timeout = 20_000)
    fun exxonSummiertSegmentUndRegionUeberKreuzangaben() {
        val b = lesen("exxon")
        // Segmente stehen nur je Region; die Summe laeuft ueber die Regionen, Eliminierungen je Segment sind abgezogen
        assertEquals(listOf("Energy Products", "Upstream", "Chemical Products", "Specialty Products"), namen(b.segmente))
        val seg = 245_750.0 + 44_745.0 + 22_881.0 + 17_828.0
        assertEquals(245_750.0 / seg, b.segmente[0].w, 1e-6)
        assertEquals(listOf(5, 3), b.regionen.map { it.k })
        assertEquals(192_773.0 / (192_773.0 + 138_431.0), b.regionen[0].w, 1e-6)
        assertEquals(0, b.segmenteStatus)
        assertEquals(0, b.regionenStatus)
    }

    @Test(timeout = 20_000)
    fun sapIfrsOhneDoppelzaehlungDerOberbegriffe() {
        val b = lesen("sap")
        assertEquals("20-F", b.form)
        assertEquals(36_800_000_000.0, b.gesamt, 1.0)
        // Europa/Nahost/Afrika, Amerika und Asien/Pazifik sind Summen ihrer Teile und fehlen
        assertEquals(6, b.regionen.size)
        assertEquals(1.0, b.regionen.sumOf { it.w }, 1e-9)
        assertTrue(b.regionen.any { it.k == 4 })
        assertTrue(b.regionen.none { it.n == "Europe Middle East And Africa" || it.n == "Americas" || it.n == "Asia Pacific" })
        assertEquals("US", b.regionen[0].l)
        // Segmente tragen dort eine weitere Achse und sind nicht eindeutig lesbar: die Gruppe fehlt
        assertEquals(2, b.segmenteStatus)
        assertTrue(b.segmente.isEmpty())
    }

    @Test(timeout = 20_000)
    fun asmlKundengruppeUndKleineRegionen() {
        val b = lesen("asml")
        assertEquals(9, b.regionen.size)
        assertEquals("CN", b.regionen[0].l)
        assertEquals(1, b.kunden.size)
        val k = b.kunden[0]
        assertEquals(2, k.k)
        assertEquals("4", k.nr)
        assertEquals(0.612, k.w, 1e-9)
    }

    @Test(timeout = 20_000)
    fun supermicroKundeAusUmsatzanteil() {
        val b = lesen("supermicro")
        assertEquals(listOf("US", "Asia", "Europe", "Other Countries"), namen(b.regionen))
        assertEquals(1, b.kunden.size)
        assertEquals(1, b.kunden[0].k)
        assertEquals("1", b.kunden[0].nr)
        assertEquals(0.281, b.kunden[0].w, 1e-9)
        assertEquals(2, b.segmenteStatus)
    }

    @Test(timeout = 20_000)
    fun grosskundenNurMitBezugAufDenUmsatzUndImJuengstenJahr() {
        val b = lesen("grosskunden")
        // A (Umsatz), B (Vertragsumsatz); C bezieht sich auf Forderungen, D nur auf ein Segment, E auf das Vorjahr
        assertEquals(2, b.kunden.size)
        assertEquals(listOf(1, 1), b.kunden.map { it.k })
        assertEquals(listOf("A", "B"), b.kunden.map { it.nr })
        assertEquals(0.22, b.kunden[0].w, 1e-9)
        assertEquals(0.14, b.kunden[1].w, 1e-9)
        assertEquals(2, b.segmenteStatus)
        assertEquals(2, b.regionenStatus)
    }

    @Test(timeout = 20_000)
    fun nurDerJuengsteVolleJahreszeitraumZaehlt() {
        val vorjahr = """
            <context id="c-alt"><entity><identifier scheme="http://www.sec.gov/CIK">0001045810</identifier><segment><xbrldi:explicitMember dimension="srt:StatementGeographicalAxis">country:US</xbrldi:explicitMember></segment></entity><period><startDate>2024-01-29</startDate><endDate>2025-01-26</endDate></period></context>
            <context id="c-quartal"><entity><identifier scheme="http://www.sec.gov/CIK">0001045810</identifier><segment><xbrldi:explicitMember dimension="srt:StatementGeographicalAxis">country:TW</xbrldi:explicitMember></segment></entity><period><startDate>2025-10-27</startDate><endDate>2026-01-25</endDate></period></context>
            <us-gaap:Revenues contextRef="c-alt" unitRef="usd" decimals="-6">1</us-gaap:Revenues>
            <us-gaap:Revenues contextRef="c-quartal" unitRef="usd" decimals="-6">99000000000</us-gaap:Revenues>
        </xbrl>
        """.trimIndent()
        val xml = text("nvidia").replace("</xbrl>", vorjahr)
        val b = auswerten(xml)
        assertEquals(listOf("US", "TW", "China Including Hong Kong", "Other Countries"), namen(b.regionen))
        assertEquals(149_617.0 / 215_938.0, b.regionen[0].w, 1e-6)
    }

    private fun kopf(dei: String = "2025-12-31") = """
        <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance" xmlns:us-gaap="http://fasb.org/us-gaap/2025" xmlns:dei="http://xbrl.sec.gov/dei/2025" xmlns:xbrldi="http://xbrl.org/2006/xbrldi" xmlns:x="http://example.com/x">
        <dei:DocumentPeriodEndDate>$dei</dei:DocumentPeriodEndDate>
        <xbrli:context id="g"><xbrli:entity><xbrli:identifier scheme="s">1</xbrli:identifier></xbrli:entity><xbrli:period><xbrli:startDate>2025-01-01</xbrli:startDate><xbrli:endDate>2025-12-31</xbrli:endDate></xbrli:period></xbrli:context>
        <us-gaap:Revenues contextRef="g" unitRef="usd">1000</us-gaap:Revenues>
    """.trimIndent()

    private fun segment(id: String, glied: String, wert: String, achse: String = "us-gaap:StatementBusinessSegmentsAxis") =
        """<xbrli:context id="$id"><xbrli:entity><xbrli:identifier scheme="s">1</xbrli:identifier><xbrli:segment><xbrldi:explicitMember dimension="$achse">$glied</xbrldi:explicitMember></xbrli:segment></xbrli:entity><xbrli:period><xbrli:startDate>2025-01-01</xbrli:startDate><xbrli:endDate>2025-12-31</xbrli:endDate></xbrli:period></xbrli:context>
        <us-gaap:Revenues contextRef="$id" unitRef="usd">$wert</us-gaap:Revenues>"""

    @Test(timeout = 20_000)
    fun eliminierungenUndZentraleOhneUmsatzFallenWeg() {
        val xml = kopf() + segment("a", "x:AlphaSegmentMember", "600") + segment("b", "x:BetaMember", "400") +
            segment("e", "x:IntersegmentEliminationsMember", "-150") + segment("z", "x:CorporateAndOtherMember", "0") +
            segment("r", "x:ReconcilingItemsMember", "20") + segment("s", "x:AllOtherMember", "50") + "</xbrli:xbrl>"
        val b = auswerten(xml)
        assertEquals(listOf("Alpha", "Beta", "All Other"), namen(b.segmente))
        assertEquals(600.0 / 1050.0, b.segmente[0].w, 1e-9)
        assertEquals(0, b.segmenteStatus)
    }

    @Test(timeout = 20_000)
    fun summeAusserhalbVonNeunzigBisHundertzehnProzentIstNichtEindeutig() {
        val zuWenig = kopf() + segment("a", "x:AlphaMember", "300") + segment("b", "x:BetaMember", "200") + "</xbrli:xbrl>"
        assertEquals(1, auswerten(zuWenig).segmenteStatus)
        assertTrue(auswerten(zuWenig).segmente.isEmpty())
        val knapp = kopf() + segment("a", "x:AlphaMember", "500") + segment("b", "x:BetaMember", "420") + "</xbrli:xbrl>"
        assertEquals(0, auswerten(knapp).segmenteStatus)
        val doppelt = kopf() + segment("a", "x:AlphaMember", "900") + segment("b", "x:BetaMember", "900") + "</xbrli:xbrl>"
        assertEquals(1, auswerten(doppelt).segmenteStatus)
    }

    @Test(timeout = 20_000)
    fun fehlendeAchsenMeldenNichtGemeldetUndKeineKunden() {
        val b = auswerten(kopf() + "</xbrli:xbrl>")
        assertEquals(2, b.segmenteStatus)
        assertEquals(2, b.regionenStatus)
        assertTrue(b.kunden.isEmpty())
        assertEquals(1000.0, b.gesamt, 1e-9)
        assertEquals(2025, b.jahr)
    }

    @Test(timeout = 20_000)
    fun ohneGesamtumsatzOderStichtagKeineAuswertung() {
        val ohneUmsatz = """<xbrl><dei:DocumentPeriodEndDate>2025-12-31</dei:DocumentPeriodEndDate></xbrl>"""
        assertNull(Xbrl.auswerten(Xbrl.lesen(ohneUmsatz.byteInputStream(), 1_000_000)))
    }

    @Test(timeout = 20_000)
    fun groessenGrenzeBrichtDasLesenAb() {
        val endlos = object : InputStream() {
            override fun read(): Int = 'a'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int { java.util.Arrays.fill(b, off, off + len, 'a'.code.toByte()); return len }
        }
        var ausnahme = false
        try { Xbrl.lesen(endlos, 100_000) } catch (_: Xbrl.ZuGross) { ausnahme = true }
        assertTrue(ausnahme)
        assertTrue(Xbrl.lesenGrenze(endlos, 100_000) is Xbrl.Gelesen.Gross)
        assertTrue(Xbrl.lesenGrenze(text("nvidia").byteInputStream(), SecBericht.MAX_BYTES) is Xbrl.Gelesen.Ok)
    }

    @Test(timeout = 20_000)
    fun bezeichnungenAusGliednamen() {
        assertEquals("Productivity And Business Processes", Xbrl.woerter("ProductivityAndBusinessProcesses"))
        assertEquals("US Sales", Xbrl.woerter("USSales"))
        assertEquals("Segment2", Xbrl.woerter("Segment2"))
    }

    @Test(timeout = 20_000)
    fun tageSeit1970() {
        assertEquals(0L, Xbrl.tage("1970-01-01"))
        assertEquals(LocalDate.of(2026, 11, 10).toEpochDay(), Xbrl.tage("2026-11-10"))
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), Xbrl.tage("2024-02-29"))
        assertEquals(-1L, Xbrl.tage("kein Datum"))
    }

    // ------------------------------------------------------------ Kuerzel, Einreichungen, Verzeichnis

    private val kuerzelListe = """{"0":{"cik_str":1045810,"ticker":"NVDA","title":"NVIDIA CORP"},"1":{"cik_str":1000184,"ticker":"SAP","title":"SAP SE"},
        "2":{"cik_str":937966,"ticker":"ASML","title":"ASML HOLDING NV"},"3":{"cik_str":1067983,"ticker":"BRK-B","title":"BERKSHIRE HATHAWAY INC"},
        "4":{"cik_str":10,"ticker":"ALV","title":"AUTOLIV INC"},"5":{"cik_str":77,"ticker":"TM","title":"TOYOTA MOTOR CORP/ADR"}}"""

    @Test(timeout = 20_000)
    fun kuerzelZuCik() {
        assertEquals("1045810", SecBericht.cikAusListe(kuerzelListe, "NVDA"))
        assertEquals("1067983", SecBericht.cikAusListe(kuerzelListe, "BRK.B"))
        // Boersenzusatz: Basiskuerzel nur bei passendem Namen
        assertEquals("1000184", SecBericht.cikAusListe(kuerzelListe, "SAP.DE", "SAP"))
        assertEquals("937966", SecBericht.cikAusListe(kuerzelListe, "ASML.AS", "ASML"))
        assertNull(SecBericht.cikAusListe(kuerzelListe, "ALV.DE", "Allianz"))
        assertNull(SecBericht.cikAusListe(kuerzelListe, "ALV.DE"))
        // Ohne Kuerzel entscheidet der volle Name
        assertEquals("77", SecBericht.cikAusListe(kuerzelListe, "7203.T", "Toyota Motor"))
        assertNull(SecBericht.cikAusListe("kein json", "NVDA"))
    }

    private val einreichungen = """{"filings":{"recent":{
        "form":["8-K","10-K/A","10-K","10-Q","10-K"],
        "accessionNumber":["0001-26-000005","0001-26-000004","0001-26-000003","0001-26-000002","0001-25-000001"],
        "filingDate":["2026-08-01","2026-04-01","2026-02-25","2025-11-01","2025-02-26"],
        "reportDate":["","2025-12-31","2025-12-31","2025-09-30","2024-12-31"],
        "isXBRL":[0,1,1,1,1]}}}"""

    @Test(timeout = 20_000)
    fun juengsterJahresberichtOhneNachtraege() {
        val j = SecBericht.juengsterJahresbericht(einreichungen)!!
        assertEquals("0001-26-000003", j.acc)
        assertEquals("10-K", j.form)
        assertEquals("2026-02-25", j.eingang)
        assertNull(SecBericht.juengsterJahresbericht("""{"filings":{"recent":{"form":["8-K"],"accessionNumber":["x"],"filingDate":["2026-01-01"]}}}"""))
        assertNull(SecBericht.juengsterJahresbericht("kein json"))
        val zwanzigF = """{"filings":{"recent":{"form":["20-F"],"accessionNumber":["0002-26-000001"],"filingDate":["2026-02-26"],"reportDate":["2025-12-31"],"isXBRL":[1]}}}"""
        assertEquals("20-F", SecBericht.juengsterJahresbericht(zwanzigF)!!.form)
    }

    @Test(timeout = 20_000)
    fun instanzDateiImEinreichungsordner() {
        val inline = """{"directory":{"item":[{"name":"FilingSummary.xml","size":"52270"},{"name":"nvda-20260125_cal.xml","size":"139207"},
            {"name":"nvda-20260125_htm.xml","size":"1685733"},{"name":"nvda-20260125_lab.xml","size":"903401"}]}}"""
        val i = SecBericht.instanzDatei(inline)!!
        assertEquals("nvda-20260125_htm.xml", i.datei)
        assertEquals(1_685_733L, i.groesse)
        val alt = """{"directory":{"item":[{"name":"FilingSummary.xml","size":"1"},{"name":"abc-20190928_pre.xml","size":"900"},{"name":"abc-20190928.xml","size":"500"},{"name":"R1.xml","size":"2000"}]}}"""
        assertEquals("abc-20190928.xml", SecBericht.instanzDatei(alt)!!.datei)
        assertNull(SecBericht.instanzDatei("""{"directory":{"item":[{"name":"FilingSummary.xml","size":"1"}]}}"""))
        assertNotNull(SecBericht.archivAdresse("1045810", "0001045810-26-000021", "x.xml"))
    }

    @Test(timeout = 20_000)
    fun speicherformatRundlauf() {
        val b = lesen("asml")
        val e = SecBericht.Eintrag(1L, "937966", "0000937966-26-000001", SecBericht.Status.OK.ordinal, b, "2026-02-25")
        val text = SecBericht.schreiben(SecBericht.Datei(mapOf("ASML" to e)))
        val zurueck = SecBericht.lesen(text)!!.werte.getValue("ASML")
        assertEquals(b.regionen.size, zurueck.bericht!!.regionen.size)
        assertEquals(0.612, zurueck.bericht!!.kunden[0].w, 1e-9)
        assertEquals("4", zurueck.bericht!!.kunden[0].nr)
        assertNull(SecBericht.lesen("kein json"))
    }
}
