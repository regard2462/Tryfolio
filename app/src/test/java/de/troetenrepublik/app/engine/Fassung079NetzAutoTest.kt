package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Gleif
import de.troetenrepublik.app.market.NetzAuto
import de.troetenrepublik.app.market.SecBericht
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Automatische Kanten je Firma (gemeldet): Grosskunden der SEC, GLEIF, Wikidata, Anlage 21, Halter aus dem Dienst fuer
// Eigentuemer und Rechte, Wettbewerber im Jahresbericht, Board-Mitglieder aus Form 4, Zuschuesse bei USAspending, Zwischenspeicher.
class Fassung079NetzAutoTest {

    private val stand = "2026-11-13"
    private val bericht = "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/0001045810-26-000021-index.htm"
    private val nvda = Netz.Knoten("NVDA", Netz.Typ.FIRMA, "Nvidia", symbole = listOf("NVDA"), cik = "1045810")
    private val msft = Netz.Knoten("MSFT", Netz.Typ.FIRMA, "Microsoft", symbole = listOf("MSFT"), cik = "789019")
    private fun graph(vararg k: Netz.Knoten) = Netz.Graph(k.associateBy { it.id }, emptyList())

    // ------------------------------------------------------------ SEC: Grosskunden

    @Test fun kundenAlsKantenMitAnonymenSammelknoten() {
        val b = SecBericht.Auswertung(
            jahr = 2026, stichtag = "2026-01-25",
            kunden = listOf(
                SecBericht.Posten("Kunde A", 0.22, k = 1, nr = "A"), SecBericht.Posten("Kunde B", 0.14, k = 1, nr = "B"),
                SecBericht.Posten("Microsoft Corporation", 0.10), SecBericht.Posten("3 Kunden", 0.30, k = 2, nr = "3"), SecBericht.Posten("Null", 0.0),
            ),
        )
        val z = NetzAuto.kunden(nvda, b, graph(nvda, msft), bericht, stand)
        assertEquals(4, z.kanten.size)
        assertEquals(setOf("kunde:NVDA:A", "kunde:NVDA:B", "kunden:NVDA:3"), z.knoten.map { it.id }.toSet())
        val a = z.knoten.first { it.id == "kunde:NVDA:A" }
        assertEquals(Netz.Typ.SAMMEL, a.typ)
        assertEquals("Kunde A (Nvidia)", a.name)
        assertEquals("Customer A (Nvidia)", a.nameEn)
        val ka = z.kanten.first { it.nach == "kunde:NVDA:A" }
        assertEquals(Netz.Art.LIEFERT, ka.art)
        assertEquals(22.0, ka.umfang!!.wert, 1e-9)
        assertEquals(Netz.Bezug.UMSATZ, ka.umfang!!.bezug)
        assertEquals(Netz.Herkunft.GEMELDET, ka.herkunft)
        assertEquals(Netz.Guete.A, ka.guete)
        // Link auf die Einreichung, Stand ist das Datum des Berichts
        assertEquals(bericht, ka.quelle.url)
        assertTrue(Netz.einzeldokument(ka.quelle.url))
        assertEquals("2026-01-25", ka.stand)
        // Genannte Firma wird dem bekannten Knoten zugeordnet, ohne neuen Knoten
        assertTrue(z.kanten.any { it.nach == "MSFT" })
        assertFalse(z.knoten.any { it.id == "MSFT" })
    }

    @Test fun einreichungsseiteIstDasFilingNichtDieFirmenuebersicht() {
        val u = NetzAuto.einreichungsseite("0001045810", "0001045810-26-000021")
        assertEquals("https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/0001045810-26-000021-index.htm", u)
        assertTrue(Netz.einzeldokument(u))
    }

    // ------------------------------------------------------------ Abgleich der Knoten

    @Test fun abgleichZuerstUeberKennungenDannStrengUeberDenNamen() {
        val sp = Netz.Knoten("SPGI", Netz.Typ.FIRMA, "S&P Global", symbole = listOf("SPGI"), cik = "64040", lei = "LEI-SP")
        val att = Netz.Knoten("T", Netz.Typ.FIRMA, "AT&T", symbole = listOf("T"), cik = "732717")
        val amzn = Netz.Knoten("AMZN", Netz.Typ.FIRMA, "Amazon", symbole = listOf("AMZN"), cik = "1018724")
        val person = Netz.Knoten("ERIKA", Netz.Typ.PERSON, "Erika Beispiel", wikidata = "Q42")
        val g = graph(sp, att, amzn, person)
        // Kennungen vor dem Namen, Nullen vorn zaehlen nicht
        assertEquals("AMZN", g.knotenFuerKennung(cik = "0001018724")?.id)
        assertEquals("SPGI", g.knotenFuerKennung(lei = "LEI-SP")?.id)
        assertEquals("ERIKA", g.knotenFuerKennung(wikidata = "Q42")?.id)
        assertEquals("T", g.knotenFuerKennung(symbol = "T")?.id)
        assertNull(g.knotenFuerKennung(cik = "1"))
        // Name: voll und normiert, nie ein allgemeines Wort allein
        assertEquals("SPGI", g.knotenFuerName("S&P Global Inc.")?.id)
        assertNull(g.knotenFuerName("Global Holdings LLC"))
        assertNull(g.knotenFuerName("Global"))
        assertEquals("T", g.knotenFuerName("AT&T Inc")?.id)
        assertNull(g.knotenFuerName("AT"))
        assertEquals("", Netz.nameVoll("Holdings"))
        assertEquals("meta platforms", Netz.nameVoll("Meta Platforms, Inc."))
        assertEquals("", Netz.nameVoll("Meta"))
        // Personen werden nie ueber den Namen zusammengefuehrt
        assertNull(g.knotenFuerName("Erika Beispiel"))
    }

    @Test fun einzeldokumentLehntSammelUndStartseitenAb() {
        assertTrue(Netz.einzeldokument("https://www.sec.gov/Archives/edgar/data/1045810/000104581026000075/nvda-20260726.htm"))
        assertTrue(Netz.einzeldokument("https://nvidianews.nvidia.com/news/openai-and-nvidia-announce-strategic-partnership-to-deploy-10gw-of-nvidia-systems"))
        assertTrue(Netz.einzeldokument("https://www.wikidata.org/wiki/Q95"))
        assertTrue(Netz.einzeldokument("https://search.gleif.org/#/record/5493001KJTIIGC8Y1R12"))
        assertFalse(Netz.einzeldokument("https://www.sec.gov/edgar/browse/?CIK=1045810"))
        assertFalse(Netz.einzeldokument("https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=1045810"))
        assertFalse(Netz.einzeldokument("https://www.catl.com/en/"))
        assertFalse(Netz.einzeldokument("https://openai.com"))
        assertFalse(Netz.einzeldokument("https://www.nist.gov/"))
        assertFalse(Netz.einzeldokument("https://www.usaspending.gov/search"))
        assertFalse(Netz.einzeldokument("http://www.example.org/doc/1"))
        assertFalse(Netz.einzeldokument("https://ir.example.com/investors"))
    }

    // ------------------------------------------------------------ GLEIF

    @Test fun gleifListeUndKanten() {
        val liste = """{"data":[
            {"type":"lei-records","attributes":{"lei":"AAA111","entity":{"legalName":{"name":"Nvidia Singapore Pte. Ltd."},"legalAddress":{"country":"SG"}}}},
            {"type":"lei-records","attributes":{"lei":"BBB222","entity":{"legalName":{"name":"Nvidia Corporation"},"legalAddress":{"country":"US"}}}},
            {"type":"lei-records","attributes":{"lei":"CCC333","entity":{"legalName":{"name":"Mellanox Technologies, Ltd."},"legalAddress":{"country":"IL"}}}},
            {"type":"lei-records","attributes":{"entity":{}}}]}"""
        val k = NetzAuto.gleifListe(liste)
        assertEquals(3, k.size)
        val mutter = Gleif.Mutter("PARENT1", "Beispiel Holding AG", "DE")
        val z = NetzAuto.gleif(nvda, "NVDA-LEI", mutter, k, graph(nvda), stand)
        // Die Tochter mit dem Namen der Firma selbst fehlt
        assertEquals(setOf("lei:AAA111", "lei:CCC333", "lei:PARENT1"), z.knoten.map { it.id }.toSet())
        val m = z.kanten.first { it.nach == "NVDA" }
        assertEquals(Netz.Art.TOCHTER, m.art)
        assertEquals("lei:PARENT1", m.von)
        assertEquals(2, z.kanten.count { it.von == "NVDA" })
        assertTrue(z.kanten.all { it.guete == Netz.Guete.A && it.herkunft == Netz.Herkunft.GEMELDET })
        assertTrue(z.kanten.all { Netz.einzeldokument(it.quelle.url) })
        // Mutter mit gleichem Namen: keine Kante
        val gleich = NetzAuto.gleif(nvda, null, Gleif.Mutter("X", "Nvidia Corp", "US"), emptyList(), graph(nvda), stand)
        assertTrue(gleich.leer)
        assertEquals(emptyList<NetzAuto.GleifSatz>(), NetzAuto.gleifListe("kein json"))
        // Bekannter Knoten mit LEI wird wiederverwendet, auch bei anderem Namen
        val bekannt = Netz.Knoten("BEISPIEL", Netz.Typ.FIRMA, "Beispiel", lei = "PARENT1")
        val z2 = NetzAuto.gleif(nvda, null, mutter, emptyList(), graph(nvda, bekannt), stand)
        assertEquals("BEISPIEL", z2.kanten.single().von)
        assertTrue(z2.knoten.isEmpty())
    }

    // ------------------------------------------------------------ Wikidata

    private val wd = """{"entities":{"Q1":{"claims":{
        "P127":[{"mainsnak":{"datavalue":{"value":{"id":"Q10"}}},"qualifiers":{"P1107":[{"datavalue":{"value":{"amount":"+0.25"}}}]}}],
        "P749":[{"mainsnak":{"datavalue":{"value":{"id":"Q11"}}}}],
        "P355":[{"mainsnak":{"datavalue":{"value":{"id":"Q12"}}}},{"mainsnak":{"datavalue":{"value":{"id":"Q13"}}},"qualifiers":{"P582":[{"datavalue":{}}]}}],
        "P1830":[{"mainsnak":{"datavalue":{"value":{"id":"Q14"}}},"qualifiers":{"P1107":[{"datavalue":{"value":{"amount":"+5"}}}]}}],
        "P3320":[{"mainsnak":{"datavalue":{"value":{"id":"Q15"}}}},{"mainsnak":{"datavalue":{"value":{"id":"Q16"}}},"qualifiers":{"P582":[{"datavalue":{}}]}}]
    }}}}"""

    @Test fun wikidataLesenUndKanten() {
        val a = NetzAuto.wikidataLesen(wd, "Q1")!!
        assertEquals(listOf("Q10"), a.eigentuemer.map { it.ziel })
        assertEquals(0.25, a.eigentuemer[0].anteil!!, 1e-9)
        assertEquals(listOf("Q11"), a.mutter.map { it.ziel })
        // Beendete Aussagen (P582) fallen weg
        assertEquals(listOf("Q12"), a.toechter.map { it.ziel })
        assertNull(a.besitzt[0].anteil)
        assertEquals(listOf("Q15"), a.mandate.map { it.ziel })
        assertNull(NetzAuto.wikidataLesen("{}", "Q1"))
        val namen = mapOf("Q10" to "Eigner Fonds", "Q11" to "Mutter AG", "Q12" to "Tochter GmbH", "Q14" to "Beteiligung Inc", "Q15" to "Erika Beispiel")
        val z = NetzAuto.wikidata(nvda, "Q1", a, namen, graph(nvda), stand)
        // Eigentuemer (P127) kommen aus dem Dienst fuer Eigentuemer und Rechte, nicht von hier
        assertFalse(z.kanten.any { it.art == Netz.Art.HAELT_ANTEIL && it.nach == "NVDA" })
        assertTrue(z.kanten.any { it.art == Netz.Art.TOCHTER && it.nach == "NVDA" })
        assertTrue(z.kanten.any { it.art == Netz.Art.TOCHTER && it.von == "NVDA" })
        assertTrue(z.kanten.any { it.art == Netz.Art.HAELT_ANTEIL && it.von == "NVDA" && it.umfang == null })
        val mandat = z.kanten.first { it.art == Netz.Art.MANDAT }
        assertEquals(Netz.Typ.PERSON, z.knoten.first { it.id == mandat.von }.typ)
        assertTrue(z.kanten.all { it.guete == Netz.Guete.C && !it.quelle.primaer && Netz.einzeldokument(it.quelle.url) })
        // Eine Person mit anderer Wikidata-Kennung und gleichem Namen ist eine andere Person
        val andere = Netz.Knoten("ERIKA", Netz.Typ.PERSON, "Erika Beispiel", wikidata = "Q99")
        val z2 = NetzAuto.wikidata(nvda, "Q1", a, namen, graph(nvda, andere), stand)
        assertEquals("wd:Q15", z2.kanten.first { it.art == Netz.Art.MANDAT }.von)
        // Ohne Beschriftung kein Knoten, keine Kante
        assertTrue(NetzAuto.wikidata(nvda, "Q1", a, emptyMap(), graph(nvda), stand).leer)
    }

    // ------------------------------------------------------------ Anlage 21

    @Test fun anlage21NamenUndDatei() {
        val html = """<html><body><table><tr><td>Subsidiaries of the Registrant</td><td>Jurisdiction</td></tr>
            <tr><td>NVIDIA Singapore Pte. Ltd.</td><td>Singapore</td></tr>
            <tr><td>Mellanox Technologies, Ltd.</td><td>Israel</td></tr>
            <tr><td><font>Nvidia GmbH</font></td><td>Germany</td></tr>
            <tr><td>Mellanox Technologies, Ltd.</td><td>Israel</td></tr>
            <tr><td>Exhibit 21.1</td><td>Delaware</td></tr></table></body></html>"""
        val n = NetzAuto.exhibit21Namen(html)
        assertEquals(listOf("NVIDIA Singapore Pte. Ltd.", "Mellanox Technologies, Ltd.", "Nvidia GmbH"), n)
        val index = """{"directory":{"item":[{"name":"nvda-20260125.htm"},{"name":"nvda-20260125xex211.htm"},{"name":"nvda-20260125xex231.htm"},{"name":"R1.htm"}]}}"""
        assertEquals("nvda-20260125xex211.htm", NetzAuto.exhibit21Datei(index))
        assertNull(NetzAuto.exhibit21Datei("""{"directory":{"item":[{"name":"x.htm"}]}}"""))
        val z = NetzAuto.exhibit21(nvda, n, "https://www.sec.gov/Archives/edgar/data/1045810/x/nvda-ex21.htm", graph(nvda), stand)
        assertEquals(3, z.kanten.size)
        assertTrue(z.kanten.all { it.art == Netz.Art.TOCHTER && it.von == "NVDA" && it.guete == Netz.Guete.B })
        // Die Kennung enthaelt die Mutter: gleichnamige Toechter zweier Konzerne verschmelzen nicht
        val z2 = NetzAuto.exhibit21(msft, listOf("Mellanox Technologies, Ltd."), "https://www.sec.gov/Archives/edgar/data/789019/x/msft-ex21.htm", graph(nvda, msft), stand)
        assertTrue(z2.knoten.single().id.startsWith("ex21:MSFT:"))
        // Namen der Firma selbst und Namen an boersennotierte Knoten: nie ueber den Namen geheftet
        val mit = graph(nvda, Netz.Knoten("MLNX", Netz.Typ.FIRMA, "Nvidia GmbH", symbole = listOf("MLNX")))
        assertTrue(NetzAuto.exhibit21(nvda, listOf("Nvidia GmbH"), "https://www.sec.gov/x/a", mit, stand).kanten.single().nach.startsWith("ex21:NVDA:"))
        assertEquals(0, NetzAuto.exhibit21(nvda, listOf("Nvidia Corporation"), "https://www.sec.gov/x/a", graph(nvda), stand).kanten.size)
    }

    // ------------------------------------------------------------ Halter aus dem Dienst fuer Eigentuemer und Rechte

    @Test fun halterMitBelegUndRichtigerBeschriftung() {
        val e = Struktur.Ergebnis(
            firma = "Nvidia",
            halter = listOf(
                Struktur.Halter("Vanguard Group", Struktur.Art.FONDS, klasse = 8.9, beleg = 0),
                Struktur.Halter("Jensen Huang", Struktur.Art.PERSON, kapital = 3.5, stimmen = 3.5, beleg = 1),
                Struktur.Halter("Ohne Link", Struktur.Art.FONDS, kapital = 6.0, beleg = 2),
                Struktur.Halter("", Struktur.Art.STREUBESITZ, kapital = 70.0),
                Struktur.Halter("Institutionen", Struktur.Art.INSTITUTIONEN, kapital = 60.0, beleg = 0),
            ),
            belege = listOf(
                Struktur.Beleg("SEC, Schedule 13G", "https://www.sec.gov/Archives/edgar/data/102909/000010290926000001/primary_doc.xml", "2026-02-10"),
                Struktur.Beleg("SEC, Proxy Statement", "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000010/nvda-20260101.htm", "2026-04-20"),
                Struktur.Beleg("Sammelseite", "https://www.sec.gov/edgar/browse/?CIK=1045810", ""),
            ),
        )
        val z = NetzAuto.halter(nvda, e, graph(nvda), stand)
        // Streubesitz, Institutionen gesammelt und Halter ohne Link auf ein einzelnes Dokument fallen weg
        val v = z.kanten.single { it.von.contains("vanguard") }
        assertEquals(Netz.Bezug.KLASSE, v.umfang!!.bezug)
        assertEquals(8.9, v.umfang!!.wert, 1e-9)
        assertEquals("2026-02-10", v.stand)
        assertEquals(Netz.Guete.B, v.guete)
        val h = z.kanten.filter { it.von.contains("jensen") }
        assertEquals(setOf(Netz.Bezug.KAPITAL, Netz.Bezug.STIMMEN), h.map { it.umfang!!.bezug }.toSet())
        assertEquals(Netz.Typ.PERSON, z.knoten.first { it.id == h[0].von }.typ)
        assertFalse(z.kanten.any { it.von.contains("ohne") })
        assertEquals(3, z.kanten.size)
        // Person mit gleichem Namen wie ein Knoten des Netzes: nicht zusammengefuehrt
        val bekannt = Netz.Knoten("JH", Netz.Typ.PERSON, "Jensen Huang")
        assertTrue(NetzAuto.halter(nvda, e, graph(nvda, bekannt), stand).kanten.none { it.von == "JH" })
    }

    // ------------------------------------------------------------ Wettbewerber

    @Test fun wettbewerberNurEindeutigeTreffer() {
        val klar = "Item 1. Business Overview Our products. Competition Our markets are intensely competitive. We compete with Intel, Advanced Micro Devices, " +
            "Broadcom and Qualcomm. Google and Azure also compete in some areas. Target and Square also compete in some areas. Employees We have many."
        val abschnitt = NetzAuto.wettbewerbAbschnitt(klar)
        assertTrue(abschnitt.contains("Advanced Micro Devices"))
        assertEquals("", NetzAuto.wettbewerbAbschnitt("Nichts zum Thema hier."))
        val g = graph(
            nvda, Netz.Knoten("INTC", Netz.Typ.FIRMA, "Intel", symbole = listOf("INTC")),
            Netz.Knoten("GOOGL", Netz.Typ.FIRMA, "Alphabet", symbole = listOf("GOOGL")), msft,
        )
        val katalog = listOf(
            "Advanced Micro Devices, Inc." to "AMD", "Broadcom Inc." to "AVGO", "Qualcomm Incorporated" to "QCOM",
            "Qualcomm Atheros" to "QCOM2", "Target Corporation" to "TGT", "Square Inc" to "SQ", "Nvidia Corporation" to "NVDA",
        )
        val z = NetzAuto.wettbewerber(nvda, abschnitt, g, katalog, "https://www.sec.gov/Archives/edgar/data/1045810/x/nvda.htm", stand)
        val ids = z.kanten.flatMap { listOf(it.von, it.nach) }.toSet() - "NVDA"
        assertTrue(ids.toString(), ids.containsAll(setOf("INTC", "AMD", "AVGO", "QCOM", "GOOGL", "MSFT")))
        // Gewoehnliche Woerter wie Target und Square fallen weg, die Firma selbst auch
        assertFalse("TGT" in ids || "SQ" in ids || "NVDA" in ids)
        assertTrue(z.kanten.all { it.art == Netz.Art.KONKURRIERT && it.von < it.nach && it.guete == Netz.Guete.C })
        // Zwei Firmen mit demselben Namen: mehrdeutig, keine Kante
        val doppelt = NetzAuto.wettbewerber(nvda, "Competition we compete with Acme Labs.", graph(nvda), listOf("Acme Labs Inc" to "ACM1", "Acme Labs Ltd" to "ACM2"), "https://www.sec.gov/x/a", stand)
        assertTrue(doppelt.leer)
        assertTrue(NetzAuto.wettbewerber(nvda, "", g, katalog, "https://www.sec.gov/x/a", stand).leer)
    }

    // ------------------------------------------------------------ Board-Mitglieder (Form 4)

    @Test fun direktorenAusForm4() {
        val suche = """{"hits":{"hits":[
            {"_id":"0001-26-000011:xslF345X05/wf-form4_1.xml","_source":{"ciks":["0001000001","0001045810"],"adsh":"0001-26-000011","file_date":"2026-05-01"}},
            {"_id":"0001-26-000012:wf-form4_2.xml","_source":{"ciks":["0001000002","0001045810"],"adsh":"0001-26-000012","file_date":"2026-04-01"}},
            {"_id":"0001-26-000013:doc.htm","_source":{"ciks":["0001000003","0001045810"],"adsh":"0001-26-000013","file_date":"2026-04-01"}}]}}"""
        val t = NetzAuto.form4Treffer(suche, "1045810")
        assertEquals(2, t.size)
        assertEquals(1, NetzAuto.form4Treffer(suche, "1045810", 1).size)
        assertEquals("wf-form4_1.xml", t[0].datei)
        assertEquals("https://www.sec.gov/Archives/edgar/data/1045810/000126000011/wf-form4_1.xml", NetzAuto.form4Dokument("1045810", t[0]))
        assertTrue(NetzAuto.form4Adresse("1045810", "2025-11-13", "2026-11-13").contains("forms=4"))
        val xml = "<ownershipDocument><reportingOwner><reportingOwnerId><rptOwnerName>BEISPIEL ERIKA M</rptOwnerName></reportingOwnerId>" +
            "<reportingOwnerRelationship><isDirector>1</isDirector><isOfficer>0</isOfficer></reportingOwnerRelationship></reportingOwner></ownershipDocument>"
        val d = NetzAuto.direktor(xml)!!
        assertEquals("Erika M Beispiel", d.name)
        assertEquals("Director", d.rolle)
        val mitTitel = xml.replace("<isOfficer>0</isOfficer>", "<isOfficer>1</isOfficer><officerTitle>Chair &amp; CEO</officerTitle>")
        assertEquals("Director, Chair & CEO", NetzAuto.direktor(mitTitel)!!.rolle)
        assertNull(NetzAuto.direktor(xml.replace("<isDirector>1</isDirector>", "<isDirector>0</isDirector>")))
        val z = NetzAuto.direktoren(nvda, listOf(Triple(t[0], d, "https://www.sec.gov/Archives/edgar/data/1045810/000126000011/wf-form4_1.xml")), graph(nvda), stand)
        val k = z.kanten.single()
        assertEquals(Netz.Art.MANDAT, k.art)
        assertEquals("person:1000001", k.von)
        assertEquals("Director", k.rolle)
        assertEquals(Netz.Typ.PERSON, z.knoten.single().typ)
        // Personen nur ueber die CIK des Meldenden: gleicher Name, andere CIK bleibt eine andere Person
        val gleichnamig = Netz.Knoten("ERIKA", Netz.Typ.PERSON, "Erika M Beispiel", cik = "999")
        val z2 = NetzAuto.direktoren(nvda, listOf(Triple(t[0], d, "https://www.sec.gov/x/a.xml")), graph(nvda, gleichnamig), stand)
        assertEquals("person:1000001", z2.kanten.single().von)
        // Dieselbe CIK im Netz: wiederverwendet
        val gleicheCik = Netz.Knoten("ERIKA", Netz.Typ.PERSON, "E. Beispiel", cik = "1000001")
        val z3 = NetzAuto.direktoren(nvda, listOf(Triple(t[0], d, "https://www.sec.gov/x/a.xml")), graph(nvda, gleicheCik), stand)
        assertEquals("ERIKA", z3.kanten.single().von)
        assertTrue(z3.knoten.isEmpty())
    }

    // ------------------------------------------------------------ USAspending (Anfrage und Leser in Behoerden)

    @Test fun zuschuesseUndKrediteJeVergabe() {
        val grants = """{"results":[
            {"Award ID":"G1","Recipient Name":"INTEL CORPORATION","Award Amount":3000000000,"Awarding Agency":"Department of Commerce","Start Date":"2024-11-26","Description":"CHIPS incentives","generated_internal_id":"ASST_NON_1"},
            {"Award ID":"G2","Recipient Name":"INTEL FEDERAL LLC","Award Amount":900000000,"Awarding Agency":"Department of Defense","Start Date":"2024-01-01","Description":"x","generated_internal_id":"ASST_NON_2"},
            {"Award ID":"G3","Recipient Name":"INTEL CORPORATION","Award Amount":200000,"Awarding Agency":"Department of Energy","Start Date":"2023-01-01","Description":"klein","generated_internal_id":"ASST_NON_3"}]}"""
        val loans = """{"results":[{"Award ID":"L1","Recipient Name":"Intel Corp.","Award Amount":1500000000,"Awarding Agency":"Department of Energy","Start Date":"2025-02-03","Description":"","generated_internal_id":"ASST_NON_4"}]}"""
        val intel = Netz.Knoten("INTC", Netz.Typ.FIRMA, "Intel", symbole = listOf("INTC"))
        val z = NetzAuto.zuschuesse(intel, listOf(grants, loans, "kaputt"), graph(intel), stand)
        assertEquals(2, z.kanten.size)
        val c = z.kanten.first { it.von == "usa:department-of-commerce" }
        assertEquals(3e9, c.umfang!!.wert, 1.0)
        assertEquals(Netz.Art.STAAT_FINANZIERT, c.art)
        assertEquals(Netz.Status.ZUGESAGT, c.status)
        assertEquals("CHIPS incentives", c.programm)
        assertEquals("2024-11-26", c.stand)
        assertEquals("https://www.usaspending.gov/award/ASST_NON_1", c.quelle.url)
        assertTrue(Netz.einzeldokument(c.quelle.url))
        assertEquals(Netz.Typ.STAAT, z.knoten.first { it.id == c.von }.typ)
        val kredit = z.kanten.first { it.umfang!!.wert == 1.5e9 }
        assertEquals("Grants and loans", kredit.programmEn)
        // Kleine Betraege (200.000 USD) und fremde Empfaenger fehlen
        assertFalse(z.kanten.any { it.von.contains("defense") })
        val body = de.troetenrepublik.app.market.Behoerden.zuschussListeKoerper("Intel Corporation", listOf("02", "03"), "2015-10-01", stand)
        assertTrue(body.contains("\"award_type_codes\":[\"02\",\"03\"]") && body.contains("\"Intel Corporation\"") && body.contains("generated_internal_id"))
    }

    // ------------------------------------------------------------ Zwischenspeicher

    @Test fun zusatzUeberstehtSpeichernUndLesen() {
        val b = SecBericht.Auswertung(jahr = 2026, stichtag = "2026-01-25", kunden = listOf(SecBericht.Posten("Kunde A", 0.22, k = 1, nr = "A")))
        val z = NetzAuto.kunden(nvda, b, graph(nvda), bericht, stand) +
            NetzAuto.direktoren(nvda, listOf(Triple(NetzAuto.Form4Treffer("1000001", "0001-26-000011", "a.xml", "2026-05-01"), NetzAuto.Direktor("Erika Beispiel", "Director, CEO"), "https://www.sec.gov/Archives/edgar/data/1/2/a.xml")), graph(nvda), stand)
        val text = NetzAuto.schreiben(z, listOf(nvda))
        val r = NetzAuto.lesen(text, graph(nvda))!!
        assertEquals(z.kanten.map { it.id }.toSet(), r.kanten.map { it.id }.toSet())
        assertEquals(z.knoten.map { it.id }.toSet(), r.knoten.map { it.id }.toSet())
        assertEquals("Customer A (Nvidia)", r.knoten.first { it.id == "kunde:NVDA:A" }.nameEn)
        assertEquals("Director, CEO", r.kanten.first { it.art == Netz.Art.MANDAT }.rolle)
        assertTrue(r.kanten.all { it.herkunft == Netz.Herkunft.GEMELDET })
        assertNull(NetzAuto.lesen("", graph(nvda)))
        assertNull(NetzAuto.lesen("kaputt", graph(nvda)))
    }

    @Test fun gemeldeteKantenUeberschreibenDasBuendelNicht() {
        val basis = NetzTestHilfe.buendel.graph
        val doppelt = Netz.Kante(
            "wd:x", "NVDA", "OPENAI", Netz.Art.HAELT_ANTEIL, null, null, null, Netz.Status.DEFINITIV, Netz.Guete.C,
            Netz.Quelle("Wikidata", "https://www.wikidata.org/wiki/Q1", false), emptyList(), stand, null, null, null, false, null, false, Netz.Herkunft.GEMELDET,
        )
        val neu = basis.mit(emptyList(), listOf(doppelt))
        assertEquals(basis.kanten.size, neu.kanten.size)
    }

    @Test
    fun boardMitgliedAusWikidataNichtDoppeltNebenForm4() {
        val g = graph(nvda)
        val f4 = NetzAuto.direktoren(nvda, listOf(Triple(NetzAuto.Form4Treffer("1000001", "0001-26-000011", "a.xml", "2026-05-01"), NetzAuto.Direktor("Timothy D Cook", "Director"), "https://www.sec.gov/x/a.xml")), g, stand)
        val angaben = NetzAuto.WdAngaben(emptyList(), emptyList(), emptyList(), emptyList(), listOf(NetzAuto.WdVerweis("Q1", null), NetzAuto.WdVerweis("Q2", null)))
        val wd = NetzAuto.wikidata(nvda, "Q182477", angaben, mapOf("Q1" to "Tim Cook", "Q2" to "Rebecca Cook"), g, stand)
        val ohne = NetzAuto.ohneDoppelteMandate(nvda, f4, wd, g)
        val namen = ohne.knoten.map { it.name }
        assertFalse(namen.toString(), "Tim Cook" in namen)
        assertTrue(namen.toString(), "Rebecca Cook" in namen)
        assertEquals(1, ohne.kanten.size)
    }

    @Test
    fun gleifNamenLesbar() {
        val z = NetzAuto.gleif(nvda, "LEI0", null, listOf(NetzAuto.GleifSatz("LEI1", "APPLE OPERATIONS INTERNATIONAL LIMITED", "IE")), graph(nvda), stand)
        assertEquals("Apple Operations International Limited", z.knoten.single().name)
        assertEquals("PV 1790 Sp. z o.o.", NetzAuto.gleifName("PV 1790 SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ", "RWE"))
        assertEquals("RWE Investco MGMT II, LLC", NetzAuto.gleifName("RWE INVESTCO MGMT II, LLC", "RWE"))
        assertEquals("Beispiel Holding GmbH", NetzAuto.gleifName("Beispiel Holding Gesellschaft mit beschränkter Haftung", "Muster AG"))
    }
}
