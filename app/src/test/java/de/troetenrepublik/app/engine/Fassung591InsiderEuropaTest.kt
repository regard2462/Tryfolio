package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.InsiderEuropa
import de.troetenrepublik.app.market.InsiderWelt
import de.troetenrepublik.app.market.MarFormular
import de.troetenrepublik.app.market.PdfText
import de.troetenrepublik.app.market.RegisterImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.91: Insidergeschaefte aus Nordeuropa, dem Baltikum, Tschechien, Luxemburg, Italien, Zypern und Ungarn
// (gemeinsamer Leser fuer das Formular nach Art. 19 MAR und die Antworten der Quellen). Die Beispiele folgen dem
// Aufbau echter Meldungen; Namen und Firmen sind erfunden.
class Fassung591InsiderEuropaTest {
    @Test fun boerseUndNamen() {
        assertEquals("DK" to "NOVO-B", InsiderWelt.boerse("NOVO-B.CO"))
        assertEquals("CZ" to "KOFOL", InsiderWelt.boerse("KOFOL.PR"))
        assertEquals("IT" to "ENI", InsiderWelt.boerse("ENI.MI"))
        assertEquals("HU" to "OTP", InsiderWelt.boerse("OTP.BD"))
        // Luxemburgische ISIN an einer anderen Boerse; Indien bleibt Indien
        assertEquals("LU" to "MT", InsiderWelt.boerse("MT.AS", "LU1598757687"))
        assertEquals("IN" to "INFY", InsiderWelt.boerse("INFY.NS", "INE009A01021"))
        assertNull(InsiderWelt.boerse("SAP.DE", "DE0007164600"))
        assertTrue(InsiderEuropa.gleicheFirma("Elisa Oyj", "Elisa"))
        assertTrue(InsiderEuropa.gleicheFirma("ENI", "Eni S.p.A."))
        assertTrue(InsiderEuropa.gleicheFirma("AS Tallink Grupp", "Tallink Grupp"))
        assertTrue(!InsiderEuropa.gleicheFirma("Danske Bank A/S", "Jyske Bank A/S"))
        assertTrue(MarFormular.isinGueltig("DK0010274414"))
        assertTrue(!MarFormular.isinGueltig("DK0010274415"))
        assertEquals("IT0003132476", MarFormular.isinIn("Identification codeIT0003132476"))
        assertEquals("XS3503273719", MarFormular.isinIn("ISIN: XS 3503273719"))
        assertEquals("2026-10-23", MarFormular.datum("202 6 -1 0 -23"))
        assertEquals("2026-10-20", MarFormular.datum("2026.10.20."))
    }

    // Kurzform der Nasdaq-Mitteilungen: Person, Fuehrungsperson dahinter, Einzelgeschaefte und Summe
    @Test fun kurzform() {
        val z = listOf(
            "Beispiel Oyj - Managers' Transactions", "____________________________________________",
            "Person subject to the notification requirement", "Name: Beispiel Holding Oy", "Position: Closely associated person", "(X) Legal person",
            "Person Discharging Managerial Responsibilities in Issuer", "Name: Muster, Max", "Position: Member of the Board",
            "Issuer: Beispiel Oyj", "LEI: 743700TU2S3DXWGU7H32", "Notification type: INITIAL NOTIFICATION", "____________________________________________",
            "Transaction date: 2026-10-23", "Venue: NASDAQ HELSINKI LTD (XHEL)", "Instrument type: SHARE", "ISIN: FI0009007884",
            "Nature of the transaction: ACQUISITION", "Transaction details", "(1): Volume: 1,000; Unit price: 30.50 EUR", "(2): Volume: 500; Unit price: 30.20 EUR",
            "Aggregated transactions", "(2): Volume: 1,500; Volume weighted average price: 30.40 EUR",
        )
        assertTrue(MarFormular.istFormular(z))
        val g = MarFormular.lesen(z, MarFormular.Meldung("NQ-1", "x", "2026-10-24")).single()
        assertEquals("Beispiel Holding Oy", g.p)
        assertEquals("Max Muster", g.fuer)
        assertEquals("N", g.r)
        assertEquals("K", g.art)
        assertEquals("Aktie", g.i)
        assertEquals(1500.0, g.n, 1e-9)
        assertEquals(30.40, g.preis, 1e-9)
        assertEquals("EUR", g.w)
        assertEquals("2026-10-23", g.d)
        // Aktienbasierte Verguetung ist Sonstiges
        val plan = listOf("Name: Erika Beispiel", "Position: Chief Financial Officer", "Issuer: Beispiel Oyj", "Transaction date: 2026-10-26",
            "Outside a trading venue", "Instrument type: SHARE", "ISIN: FI0009007884", "Nature of transaction: RECEIPT OF A SHARE-BASED INCENTIVE",
            "(1): Volume: 1900 Unit price: 0 EUR")
        MarFormular.lesen(plan, MarFormular.Meldung("NQ-2", publiziert = "2026-10-26")).single().let { assertEquals("S", it.art); assertEquals("S", it.r); assertEquals(1900.0, it.n, 1e-9) }
        // Abschnitte mit eigener Person und Durchschnittspreis mit Dezimalkomma (Baltikum)
        val baltisch = listOf("Issuer: Beispiel AS", "Instrument type: Share", "ISIN: EE3100004466", "-------------------------------------",
            "Person discharging managerial responsibilities/person closely associated with them", "Name: Beispiel Invest OÜ",
            "Position: Legal entity related to Max Muster, board member of Beispiel AS", "Nature of the transaction: Acquisition", "Volume: 420", "Average price: 7,3",
            "Transaction date: 2026-10-04", "Venue: First North Estonia, XTAL", "-------------------------------------",
            "Person discharging managerial responsibilities/person closely associated with them", "Name: Beispiel Invest OÜ",
            "Position: Legal entity related to Max Muster, board member of Beispiel AS", "Nature of the transaction: Acquisition", "Volume: 1927", "Average price: 7,485",
            "Transaction date: 2026-10-08", "Venue: First North Estonia, XTAL")
        val b = MarFormular.lesen(baltisch, MarFormular.Meldung("NQ-3", publiziert = "2026-10-09"))
        assertEquals(2, b.size)
        assertEquals(7.485, b[1].preis, 1e-9)
        assertEquals("Max Muster", b[0].fuer)
        assertEquals("EE3100004466", b[0].isin)
    }

    // Vorlage als PDF nach Lage (Spalten durch Tabulatoren): Verkauf in ein Rueckkaufprogramm bleibt ein Verkauf
    @Test fun vorlageDaenemark() {
        val z = listOf(
            "1.\tDetails of the person discharging managerial responsibilities/person closely associated",
            "a)\tName\tBeispiel Invest A/S", "2.\tReason for the notification",
            "a)\tPosition/status\tBeispiel Invest A/S is closely associated to Max Muster,", "\tVice Chairman of the Board of Directors in Beispiel Bank A/S.",
            "b)\tInitial notification/Amend-Initial notification", "ment",
            "3.\tDetails of the issuer, emission allowance market participant, auction platform, auctioneer or auction", "monitor",
            "a)\tName\tBeispiel Bank A/S", "b)\tLEI\tMAES062Z21O4RZ2U7M96",
            "4.\tDetails of the transaction(s): section to be repeated for (i) each type of instrument; (ii) each type of",
            "a)\tDescription of the financial Shares", "instrument, type of", "instrument", "Identification code\tDK0010274414",
            "b)\tNature of the transaction Sale of shares. Beispiel Invest A/S continuously sells shares pro rata", "\tin connection with the announced share buy-back program.",
            "c)\tPrice(s) and volume(s)\tPrices(average)\tVolumes", "\tDKK 339.23\t5,577",
            "d)\tAggregated information", "- Aggregated volume\t- Total number ofshares:5,577", "- Price\t- Total price:DKK 1,891,898.4",
            "e)\tDate of the transaction\t202 6 -1 0 -23", "f)\tPlace of the transaction Refer to attached appendix",
        )
        val g = MarFormular.lesen(z, MarFormular.Meldung("NQ-4", publiziert = "2026-10-26")).single()
        assertEquals("Beispiel Invest A/S", g.p)
        assertEquals("Max Muster", g.fuer)
        assertEquals("N", g.r)
        assertEquals("Beispiel Bank A/S", g.e)
        assertEquals("DK0010274414", g.isin)
        assertEquals("V", g.art)
        assertEquals(5577.0, g.n, 1e-9)
        assertEquals(339.23, g.preis, 1e-9)
        assertEquals("DKK", g.w)
        assertEquals("2026-10-23", g.d)
    }

    // Zweisprachige Vorlage aus Italien: die Frage nach Optionsprogrammen mit „NO“ macht keinen Plan daraus
    @Test fun vorlageItalien() {
        val z = listOf(
            "1\tDati relativi alla persona che esercita funzioni di amministrazione, di controllo o di direzione/alla persona strettamente associata/ Details",
            "of the person discharging managerial responsibilities/person closely associated", "a)\tNome/First Name\tERIKA BEISPIEL",
            "2\tMotivo della notifica / Reason for the notification",
            "a)\tPosizione - Qualifica/Position - Status(1) - PERSONA CHE ESERCITA FUNZIONI DI DIREZIONE/PERSON PERFORMING MANAGEMENT FUNCTIONS",
            "b)\tNotifica iniziale/Modifica Initial\tNuova notifica/New notification",
            "3\tDati relativi all'emittente, al partecipante al mercato delle quote di emissioni / Details of the issuer", "a)\tNome/Name\tBEISPIEL",
            "c)\tLEI(3)\tBUCRF72VH5RBN7X3VL35",
            "4\tDati relativi all'operazione / Details of the transaction(s): section to be repeated for (i) each type of", "Operazione/Operation - 1",
            "a)\tDescrizione dello strumento finanziario, tipo diAZIONI ORDINARIE/ORDINARY SHARES", "Codice di identificazione/ Identification codeIT0003132476",
            "b)\tNatura dell'operazione/ Nature of the\tCESSIONE/DISPOSAL", "transaction(5)", "A norma dell'articolo 19, paragrafo 6, lettera e),",
            "indicated whether the transaction is linked to the", "exercise of a share option programme\tNO",
            "c)\tPrezzo/i e volume/i – Price(s) and Volume(s)(6)Prezzo/i - Price(s)\tVolume/i - Volume(s)", "\t27,1500 EUR\t2631",
            "d)\tInformazioni aggregate/Aggregated informationPrezzo/i - Price(s)\tVolume/i - Volume(s)", "— Volume aggregato/Aggregated volume —\t27,1500 EUR\t2631",
            "e)\tData dell'operazione/Date of the transaction(8)2026-10-26 - Dalle/From 08:48:00 alle/to 08:48:00",
            "f)\tLuogo dell'operazione/Place of the\tMTAA - ELECTRONIC SHARE MARKET",
        )
        val g = MarFormular.lesen(z, MarFormular.Meldung("1INFO-1", publiziert = "2026-10-26")).single()
        assertEquals("ERIKA BEISPIEL", g.p)
        assertEquals("IT0003132476", g.isin)
        assertEquals("V", g.art)
        assertEquals("F", g.r)
        assertEquals(2631.0, g.n, 1e-9)
        assertEquals(27.15, g.preis, 1e-9)
        assertEquals("2026-10-26", g.d)
    }

    // Ungarische Vorlage: Nahestehende Person, Kauf von Aktien mit Leerzeichen als Tausendertrennung
    @Test fun vorlageUngarn() {
        val z = listOf(
            "1 A vezetői feladatokat ellátó személyekre/a velük szoros kapcsolatban álló személyekre", "vonatkozó adatok",
            "a) Név\tBeispiel Erika", "2 Az értesítés indoka", "a) Pozíció/státus\tszoros kapcsolatban áll", "\tMuster Max,", "\taz Beispiel Nyrt. Igazgatóságának tagjával",
            "b) Első értesítés/Módosítás\tElső értesítés", "3 A kibocsátóra, a kibocsátási egységek piacának szereplőjére", "a) Név\tBeispiel Nyrt.",
            "b) LEI\t529900W3MOO00A18X956", "Az ügylet(ek) részletei: ezt a szakaszt meg kell ismételni",
            "A pénzügyi eszköz leírása, az eszköz típusa\tBeispiel törzsrészvény", "a)", "Azonosító kód\tISIN: HU0000061726",
            "b) Az ügylet jellege\tVételi ügylet", "\tÁr(ak)\tVolumen(ek)", "c) Ár(ak) és volumen(ek)", "\t10 500 HUF\t200 darab",
            "d) Összesített információ", "- Összesített volumen\t200 darab", "- Ár\t10 500 HUF",
            "e) Az ügylet dátuma\t202 6-10-20;13:07 (UTC)", "f) Az ügylet helye\tXBUD",
        )
        val g = MarFormular.lesen(z, MarFormular.Meldung("MNB-1", publiziert = "2026-10-21")).single()
        assertEquals("Beispiel Erika", g.p)
        assertEquals("N", g.r)
        assertEquals("HU0000061726", g.isin)
        assertEquals("K", g.art)
        assertEquals(200.0, g.n, 1e-9)
        assertEquals(10500.0, g.preis, 1e-9)
        assertEquals("HUF", g.w)
    }

    // Antworten der Quellen (gekuerzt)
    @Test fun nasdaqLuxseEinsInfoCse() {
        val nq = """{"results":{"item":[{"disclosureId":1472546,"headline":"Beispiel Bank A/S, transactions by persons discharging managerial responsibilities",
            "language":"en","cnsCategory":"Managers' Transactions","messageUrl":"https://view.news.eu.nasdaq.com/view?id=b1&lang=en&src=listed",
            "published":"2026-10-26 12:24:38","market":"Main Market, Copenhagen","attachment":[{"mimetype":"application/pdf","fileName":"a.pdf",
            "attachmentUrl":"https://attachment.news.eu.nasdaq.com/a1"}],"company":"Beispiel Bank A/S"}]},"count":1}"""
        val m = InsiderEuropa.nasdaqLesen(nq).single()
        assertEquals("1472546", m.id)
        assertEquals("2026-10-26", m.tag)
        assertEquals(listOf("https://attachment.news.eu.nasdaq.com/a1"), m.pdfs)
        val lux = """{"data":{"oamSubmissionsSearch":{"totalHits":1,"submissions":[{"submissionId":271965,"publicationDate":"2026-10-21T10:11:10.963Z","issuerName":"Beispiel S.A."}]}}}"""
        assertEquals(Triple("271965", "2026-10-21", "Beispiel S.A."), InsiderEuropa.luxseSucheLesen(lux).single())
        assertEquals(listOf("RA+u6f/x"), InsiderEuropa.luxseDetailLesen("""{"data":{"oamSubmissionDetail":{"documents":[{"fileName":"Managers’ transactions - EN","url":"RA+u6f/x","category":"DINI","obsolete":false}]}}}"""))
        assertTrue(InsiderEuropa.luxsePdf("RA+u6f/x").endsWith("RA%2Bu6f%2Fx"))
        assertEquals("117" to "BEISPIEL", InsiderEuropa.einsInfoFirmenLesen("""[{"ndg":117,"descrizione":"BEISPIEL","id":0,"idRow":0}]""").single())
        val ei = InsiderEuropa.einsInfoLesen("""{"draw":1,"recordsFiltered":1,"data":[{"ndg":117,"dataDiffusione":1793026050,"mittente":"BEISPIEL","oggetto":"Internal dealing",
            "categoria":"MANTRA","pdf":"117_173032_2026_oneinfo","idRow":95318}]}""").single()
        assertEquals("95318", ei.id)
        assertTrue(InsiderEuropa.einsInfoPdf(ei).contains("year=${ei.jahr}&file=117_173032_2026_oneinfo.pdf"))
        assertTrue(InsiderEuropa.einsInfoFelder("117").any { it.first == "columns[0][data]" })
        val cse = InsiderEuropa.cseListeLesen("""{"content":[{"id":225482,"publicationTimestamp":1790000000000,"infoLanguagesNameEn":"Greek","translationId":225483,"listedCompanyEnglish":"BEISPIEL PLC"},
            {"id":225483,"publicationTimestamp":1790000000000,"infoLanguagesNameEn":"English","translationId":0,"listedCompanyEnglish":"BEISPIEL PLC"},
            {"id":222876,"publicationTimestamp":1780000000000,"infoLanguagesNameEn":"English","translationId":0,"listedCompanyEnglish":"BEISPIEL PLC"}]}""")
        assertEquals(listOf("225482", "225483", "222876"), cse.map { it.id })
        assertTrue(cse[1].englisch)
        val pdf = java.util.Base64.getEncoder().encodeToString("%PDF-1.4".toByteArray())
        assertEquals("%PDF-1.4", InsiderEuropa.cseDateienLesen("""[{"fileId":1,"fileName":"a.pdf","fileType":"application/pdf","file":"$pdf"}]""").single().decodeToString())
    }

    @Test fun cnbUndMnb() {
        val liste = """<table><tr class="c28"><td><p><span class="c1">Beispiel a.s. / 12345678</span></p></td><td>3157005DO9L5OWHBQ359</td><td>Erika Beispiel</td>
            <td>13.10.2026</td><td>Pozbytí (zcizení)</td><td>CZ0009000121</td><td>1050</td>
            <td><a href="https://oam.cnb.cz/xmlpserver/OAM_CNB_CZ/R2_FXX.xdo?_xpf=&amp;_xpt=1&amp;par_obf_id=S21347892&amp;par_lang=cs">Otevřít</a></td></tr></table>"""
        val z = InsiderEuropa.cnbListeLesen(liste).single()
        assertEquals("S21347892", z.id)
        assertEquals("Beispiel a.s.", z.emittent)
        assertEquals("2026-10-13", z.tag)
        assertEquals(1050.0, z.n, 1e-9)
        fun zeile(vararg z: String) = "<tr>" + z.joinToString("") { "<td>$it</td>" } + "</tr>"
        val detail = "<table>" + zeile("Obchodní firma nebo název", "Beispiel a.s.") + zeile("Jméno, příjmení", "Erika Beispiel") + zeile("Funkce/postavení", "člen představenstva") +
            zeile("Datum přijetí", "16.10.2026") + zeile("Popis finančního nástroje", "akcie") + zeile("Identifikační kód", "CZ0009000121") +
            zeile("Povaha transakce", "Pozbytí (zcizení)") + zeile("Další informace o povaze transakce", "") + zeile("Transakce", "Objem", "Jednotková cena", "Měna") +
            zeile("1", "600", "508.22", "CZK") + zeile("2", "450", "508.22", "CZK") + zeile("Souhrnný objem/Průměrná jednotková cena", "1050", "508.22") +
            zeile("Datum uskutečnění transakce", "13.10.2026") + zeile("Místo transakce", "XPRA") + "</table>"
        val g = InsiderEuropa.cnbDetailLesen(detail, "S21347892", "x", "KOFOL").single()
        assertEquals("Erika Beispiel", g.p)
        assertEquals("S", g.r)
        assertEquals("V", g.art)
        assertEquals(1050.0, g.n, 1e-9)
        assertEquals(508.22, g.preis, 1e-9)
        assertEquals("CZK", g.w)
        assertEquals("2026-10-13", g.d)
        assertEquals("2026-10-16", g.v)
        assertEquals("CNB-S21347892", g.id)
        val mnb = """<table class="datagrid"><tr>
            <td class="center">2026.10.21. 16:21:55 </td><td class="icon-status-ok"></td><td>Beispiel Nyrt.</td><td class="break">Vezetői feladatokat ellátó személy tranzakciója</td>
            <td>rendkívüli tájékoztatás</td><td>Vezetői feladatokat ellátó személyek ügyletei</td>
            <td><a class="clickable" href="/kozzetetelek?viewid=K524482/2026">K524482/2026</a></td><td></td></tr></table>"""
        assertEquals(Triple("K524482/2026", "2026-10-21", "Beispiel Nyrt."), InsiderEuropa.mnbListeLesen(mnb).single())
        assertEquals("https://kozzetetelek.mnb.hu/kozzetetelek?viewid=K524482/2026", InsiderEuropa.MnbMeldung("K524482/2026", "2026-10-21", "Beispiel Nyrt.").link)
    }

    // Kleine PDF mit zwei Spalten je Zeile (Bezeichnung links, Wert rechts), eine Seite je Formular.
    private fun pdf(seiten: List<List<Pair<String, String>>>): ByteArray {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        val obj = mutableListOf("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [${seiten.indices.joinToString(" ") { "${4 + it * 2} 0 R" }}] /Count ${seiten.size} >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
        for ((k, zeilen) in seiten.withIndex()) {
            val inhalt = buildString {
                zeilen.forEachIndexed { i, (a, b) ->
                    val y = 780 - i * 14
                    if (a.isNotEmpty()) append("BT /F1 9 Tf 1 0 0 1 40 $y Tm (${esc(a)}) Tj ET\n")
                    if (b.isNotEmpty()) append("BT /F1 9 Tf 1 0 0 1 300 $y Tm (${esc(b)}) Tj ET\n")
                }
            }
            obj += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents ${5 + k * 2} 0 R >>"
            obj += "<< /Length ${inhalt.toByteArray(Charsets.ISO_8859_1).size} >>\nstream\n${inhalt}endstream"
        }
        val s = StringBuilder("%PDF-1.4\n")
        obj.forEachIndexed { i, o -> s.append("${i + 1} 0 obj\n$o\nendobj\n") }
        s.append("trailer\n<< /Root 1 0 R >>\n%%EOF\n")
        return s.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun formular(name: String, funktion: String, art: String, preis: String, menge: String, tag: String) = listOf(
        "1. Angaben zu den Personen, die Führungsaufgaben wahrnehmen" to "", "a) Name" to name, "2. Grund der Meldung" to "", "a) Position / Status" to funktion,
        "b) Erstmeldung / Berichtigung" to "Erstmeldung", "3. Angaben zum Emittenten" to "", "a) Name" to "Beispiel S.A.", "b) LEI" to "259400BEISPIEL000001",
        "4. Angaben zum Geschäft" to "", "a) Beschreibung des Finanzinstruments" to "Aktie", "Kennung" to "PLPKN0000018", "b) Art des Geschäfts" to art,
        "c) Preis(e) und Volumen" to preis, "" to menge, "d) Aggregierte Informationen" to "", "Aggregiertes Volumen" to menge, "Preis" to preis,
        "e) Datum des Geschäfts" to tag, "f) Ort des Geschäfts" to "XWAR",
    )

    // Selbst heruntergeladene PDF mit zwei Formularen (deutsche Beschriftung, polnische Aktie)
    @Test fun importMarPdf() {
        val datei = pdf(listOf(
            formular("Erika Beispiel", "Mitglied des Vorstands", "Kauf", "52,10 PLN", "1.000", "2026-10-20"),
            formular("Max Muster", "Mitglied des Aufsichtsrats", "Verkauf", "60,00 PLN", "500", "2026-10-21"),
        ))
        val zeilen = PdfText.zeilenNachLage(datei)
        assertTrue(zeilen.any { it == "a) Name\tErika Beispiel" })
        val e = RegisterImport.erkennen(datei, java.time.LocalDate.parse("2026-10-26")) as RegisterImport.Ergebnis.Insider
        assertEquals("PL", e.land)
        assertEquals("", e.stand)
        assertEquals(2, e.geschaefte.size)
        val (a, b) = e.geschaefte
        assertEquals("Erika Beispiel", a.p)
        assertEquals("S", a.r)
        assertEquals("K", a.art)
        assertEquals(1000.0, a.n, 1e-9)
        assertEquals(52.10, a.preis, 1e-9)
        assertEquals("PLN", a.w)
        assertEquals("Beispiel S.A.", a.e)
        assertEquals("A", b.r)
        assertEquals("V", b.art)
        assertEquals(500.0, b.n, 1e-9)
        assertEquals("2026-10-21", b.d)
        // Andere PDFs bleiben unbekannt
        assertTrue(RegisterImport.erkennen(pdf(listOf(listOf("Jahresbericht" to "2026"))), java.time.LocalDate.parse("2026-10-26")) is RegisterImport.Ergebnis.Unbekannt)
    }
}
