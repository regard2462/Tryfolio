package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.NetzAuto
import de.troetenrepublik.app.i18n.NetzTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Zulieferer rueckwaerts: Datei netz/zulieferer.json (Parser), strenger Abgleich des Kundennamens, Kanten samt Link auf die
// Einreichung, Rueckwaerts und Vorwaerts, Mehrdeutiges faellt weg, Zwischenspeicher (Schreiben und Lesen), eingebaute Datei.
class Fassung094U108ZuliefererTest {

    private val text = """
        {"schema":1,"stand":"2026-11-14","zeilen":[
        ["0001000001","SUPPLIER ONE CORP /DE/","SUP1","Apple Inc",22.0,"2025-09-27","0001000001-25-000010","10-K",2025],
        ["1000001","SUPPLIER ONE CORP /DE/","SUP1","Apple Inc",19.5,"2024-09-28","0001000001-24-000008","10-K",2024],
        ["1000002","CHIPMAKER HOLDINGS, INC.","CHIP","Apple Inc",9.5,"2025-12-31","0001000002-26-000003","10-K",2025],
        ["1000003","NO SYMBOL LTD","","Apple Inc",12.0,"2025-12-31","0001000003-26-000004","20-F",2025],
        ["1000004","FOUNDRY HOLDING PLC","","Samsung Electronics Co Ltd",31.5,"2025-12-31","0001000004-26-000005","10-K",2025],
        ["1000002","CHIPMAKER HOLDINGS, INC.","CHIP","Samsung Electronics Co Ltd",11.0,"2025-12-31","0001000002-26-000003","10-K",2025],
        ["1000005","BOARD SUPPLIER INC","BRD","Delta Corp",15.0,"2025-12-31","0001000005-26-000006","10-K",2025],
        ["1000006","BAD ROW","X","Apple Inc",0.0,"2025-12-31","0001000006-26-000001","10-K",2025],
        ["abc","BAD CIK","X","Apple Inc",5.0,"2025-12-31","0001000006-26-000001","10-K",2025],
        ["1000007","BAD FILING","X","Apple Inc",5.0,"2025-12-31","kein-adsh","10-K",2025],
        ["1000008","BAD DATE","X","Apple Inc",5.0,"31.12.2025","0001000008-26-000001","10-K",2025],
        "kein Array",
        ["1000009","SHORT"]
        ]}
    """.trimIndent()

    private val daten = NetzZulieferer.lesen(text)!!
    private val aapl = Netz.Knoten("AAPL", Netz.Typ.FIRMA, "Apple", symbole = listOf("AAPL"))
    private val sup1 = Netz.Knoten("SUP1", Netz.Typ.FIRMA, "Supplier One", symbole = listOf("SUP1"), cik = "1000001")
    private val katalog = listOf(
        "Apple Inc." to "AAPL", "Supplier One Corp" to "SUP1", "Chipmaker Holdings Inc" to "CHIP",
        "Delta Corp" to "DLT1", "Delta Ltd" to "DLT2", "Samsung Electronics Co., Ltd." to "005930.KS",
    )
    private fun graph(vararg k: Netz.Knoten) = Netz.Graph(k.associateBy { it.id }, emptyList())

    // ------------------------------------------------------------ Parser

    @Test fun parserNimmtNurGanzeZeilen() {
        assertEquals("2026-11-14", daten.stand)
        // Gueltig: 7 Zeilen; ohne Anteil, mit falscher CIK, falscher Einreichung, falschem Datum, kein Array und zu kurz fallen weg
        assertEquals(7, daten.zeilen.size)
        val z = daten.zeilen.first()
        // Nullen vorn zaehlen nicht
        assertEquals("1000001", z.cik)
        assertEquals("SUPPLIER ONE CORP /DE/", z.name)
        assertEquals("SUP1", z.symbol)
        assertEquals("Apple Inc", z.kunde)
        assertEquals(22.0, z.prozent, 1e-9)
        assertEquals("2025-09-27", z.stichtag)
        assertEquals(2025, z.jahr)
        assertEquals("10-K", z.form)
    }

    @Test fun parserLehntKaputtesAb() {
        assertNull(NetzZulieferer.lesen("kein json"))
        assertNull(NetzZulieferer.lesen("""{"schema":1,"zeilen":[]}"""))
        assertNull(NetzZulieferer.lesen("""{"schema":1}"""))
        assertNull(NetzZulieferer.lesen("[]"))
    }

    // ------------------------------------------------------------ Abgleich der Namen

    @Test fun schluesselIstDerVolleNormierteNameOhneLeerzeichen() {
        assertEquals("apple", NetzZulieferer.schluessel("Apple Inc"))
        assertEquals("apple", NetzZulieferer.schluessel("Apple Inc."))
        assertEquals("homedepot", NetzZulieferer.schluessel("Home Depot Inc"))
        // Nur ein allgemeines Wort: kein Schluessel
        assertEquals("", NetzZulieferer.schluessel("Holdings Inc"))
        // Namensgruppen
        assertEquals(NetzZulieferer.schluessel("Alphabet Inc"), NetzZulieferer.schluessel("Google LLC"))
        assertEquals(NetzZulieferer.schluessel("Walmart Inc."), NetzZulieferer.schluessel("Wal Mart Stores Inc"))
        assertEquals(NetzZulieferer.schluessel("Hon Hai Precision Industry Co Ltd"), NetzZulieferer.schluessel("Foxconn Technology Group"))
        assertEquals(NetzZulieferer.schluessel("AT&T Inc"), NetzZulieferer.schluessel("ATT Inc"))
        // Teilwoerter gleichen nie ab
        assertTrue(NetzZulieferer.schluessel("Apple Hospitality REIT") != NetzZulieferer.schluessel("Apple Inc"))
        assertTrue(NetzZulieferer.schluessel("Samsung") == NetzZulieferer.schluessel("Samsung Electronics Co Ltd"))
    }

    @Test fun schluesselDerFirmaNenntVollenNamenUndNamenOhneZusaetze() {
        val k = NetzZulieferer.schluesselVon("Delta Holdings")
        assertTrue("deltaholdings" in k)
        assertTrue("delta" in k)
        assertEquals(setOf("apple"), NetzZulieferer.schluesselVon("Apple Inc."))
    }

    // ------------------------------------------------------------ Kanten rueckwaerts

    @Test fun zuliefererNennenDieFirmaAlsKunden() {
        val z = NetzAuto.zulieferer(aapl, daten, katalog, graph(aapl, sup1))
        // Je Zulieferer die juengste Meldung: SUP1 (22 %), Chipmaker (9,5 %), ein Zulieferer ohne Kuerzel (12 %)
        assertEquals(3, z.kanten.size)
        val k1 = z.kanten.first { it.von == "SUP1" }
        assertEquals("AAPL", k1.nach)
        assertEquals(Netz.Art.LIEFERT, k1.art)
        assertEquals(Netz.Guete.B, k1.guete)
        assertEquals(Netz.Herkunft.GEMELDET, k1.herkunft)
        assertEquals(22.0, k1.umfang!!.wert, 1e-9)
        assertEquals(Netz.Einheit.PCT, k1.umfang!!.einheit)
        assertEquals(Netz.Bezug.UMSATZ, k1.umfang!!.bezug)
        assertEquals("2025-09-27", k1.stand)
        assertTrue(k1.quelle.primaer)
        assertEquals("SEC, Jahresbericht 2025", k1.quelle.name)
        assertEquals("https://www.sec.gov/Archives/edgar/data/1000001/000100000125000010/0001000001-25-000010-index.htm", k1.quelle.url)
        assertTrue(Netz.einzeldokument(k1.quelle.url))
        assertEquals("Anteil am Umsatz von Supplier One laut dessen Jahresbericht 2025.", k1.hinweis)
        assertEquals("Share of Supplier One's revenue per its annual report 2025.", k1.hinweisEn)
        // Der bekannte Zulieferer ist schon im Netz, die beiden anderen kommen als Knoten dazu
        assertEquals(setOf("CHIP", "cik:1000003"), z.knoten.map { it.id }.toSet())
        val chip = z.knoten.first { it.id == "CHIP" }
        assertEquals("Chipmaker Holdings Inc", chip.name)
        assertEquals(listOf("CHIP"), chip.symbole)
        assertEquals("1000002", chip.cik)
        val ohne = z.knoten.first { it.id == "cik:1000003" }
        assertEquals("No Symbol", ohne.name)
        assertTrue(ohne.symbole.isEmpty())
        // Reihenfolge der Kanten: groesster Anteil zuerst
        assertEquals(listOf(22.0, 12.0, 9.5), z.kanten.map { it.umfang!!.wert })
    }

    @Test fun nichtUSKundeAlsKnoten() {
        val samsung = Netz.Knoten("005930.KS", Netz.Typ.FIRMA, "Samsung Electronics Co., Ltd.", symbole = listOf("005930.KS"))
        val z = NetzAuto.zulieferer(samsung, daten, katalog, graph(samsung))
        assertEquals(setOf(31.5, 11.0), z.kanten.map { it.umfang!!.wert }.toSet())
        assertTrue(z.kanten.all { it.nach == "005930.KS" && it.art == Netz.Art.LIEFERT })
        assertEquals("Foundry", z.knoten.first { it.id == "cik:1000004" }.name)
    }

    @Test fun mehrdeutigerNameBleibtWeg() {
        // Zwei Katalogfirmen heissen "Delta" (Corp und Ltd): der Kunde "Delta Corp" ist nicht eindeutig
        val delta = Netz.Knoten("DLT1", Netz.Typ.FIRMA, "Delta Corp", symbole = listOf("DLT1"))
        assertTrue(NetzAuto.zulieferer(delta, daten, katalog, graph(delta)).leer)
        // Ohne die zweite Firma im Katalog ist er eindeutig
        val eindeutig = katalog.filter { it.second != "DLT2" }
        assertEquals(1, NetzAuto.zulieferer(delta, daten, eindeutig, graph(delta)).kanten.size)
    }

    @Test fun gleicheFirmaMitZweiKuerzelnIstNichtMehrdeutig() {
        val a = Netz.Knoten("DLT1", Netz.Typ.FIRMA, "Delta Corp Class A", symbole = listOf("DLT1"))
        val kat = listOf("Delta Corp Class A" to "DLT1", "Delta Corp Class C" to "DLT3")
        assertEquals(1, NetzAuto.zulieferer(a, daten, kat, graph(a)).kanten.size)
    }

    @Test fun schreibweiseAusDemKatalogUnterAnderemKuerzel() {
        val zeile = """{"stand":"2026-11-01","zeilen":[["932787","STMICROELECTRONICS N.V.","STM","Apple",17.7,"2025-12-31","0000932787-26-000009","20-F","2025"]]}"""
        val d = NetzZulieferer.lesen(zeile)!!
        val kat = listOf("Apple Inc." to "AAPL", "STMicroelectronics" to "STMPA.PA")
        val z = NetzAuto.zulieferer(aapl, d, kat, graph(aapl))
        assertEquals("STMicroelectronics", z.knoten.single().name)
    }

    @Test fun firmaOhneMeldungenBleibtLeer() {
        val x = Netz.Knoten("ZZZ", Netz.Typ.FIRMA, "Zeta Zero", symbole = listOf("ZZZ"))
        assertTrue(NetzAuto.zulieferer(x, daten, katalog, graph(x)).leer)
        // Personen und Staaten bekommen nie Zulieferer
        val p = Netz.Knoten("P", Netz.Typ.PERSON, "Apple Inc")
        assertTrue(NetzAuto.zulieferer(p, daten, katalog, graph(p)).leer)
    }

    @Test fun derZuliefererSelbstBekommtSeineKundenVorwaerts() {
        val z = NetzAuto.zulieferer(sup1, daten, katalog, graph(sup1))
        // Apple (Meldung 2025 vor 2024): eine Kante von SUP1 zu einem neuen Knoten, Anteil 22 %
        assertEquals(1, z.kanten.size)
        val k = z.kanten.single()
        assertEquals("SUP1", k.von)
        assertEquals("name:apple", k.nach)
        assertEquals(22.0, k.umfang!!.wert, 1e-9)
        assertEquals("sec:SUP1:name:apple", k.id)
        // Mit Apple im Netz zeigt die Kante auf den bekannten Knoten (gleiche Kennung wie die Kante aus dem eigenen Bericht)
        val z2 = NetzAuto.zulieferer(sup1, daten, katalog, graph(sup1, aapl))
        assertEquals("AAPL", z2.kanten.single().nach)
        assertTrue(z2.knoten.isEmpty())
    }

    @Test fun kanteMitGraphVereinigt() {
        val z = NetzAuto.zulieferer(aapl, daten, katalog, graph(aapl, sup1))
        val g = graph(aapl, sup1).mit(z.knoten, z.kanten)
        assertEquals(3, g.zulieferer("AAPL").size)
        assertEquals(1, g.abnehmer("SUP1").size)
        // Ein zweites Mal hinzugefuegt: keine Doppelten
        val g2 = g.mit(z.knoten, z.kanten)
        assertEquals(3, g2.zulieferer("AAPL").size)
    }

    // ------------------------------------------------------------ Zwischenspeicher: Schreiben und Rueckgelesen durch NetzAuto.lesen

    @Test fun kantenUeberstehenDasRuecklesen() {
        val g = graph(aapl, sup1)
        val z = NetzAuto.zulieferer(aapl, daten, katalog, g)
        val gespeichert = NetzAuto.schreiben(z, listOf(aapl, sup1))
        val zurueck = NetzAuto.lesen(gespeichert, g)!!
        assertEquals(z.kanten.size, zurueck.kanten.size)
        assertEquals(z.knoten.map { it.id }.toSet(), zurueck.knoten.map { it.id }.toSet())
        for (k in z.kanten) {
            val r = zurueck.kanten.first { it.id == k.id }
            assertEquals(k.von, r.von); assertEquals(k.nach, r.nach)
            assertEquals(Netz.Art.LIEFERT, r.art)
            assertEquals(Netz.Guete.B, r.guete)
            assertEquals(k.umfang!!.wert, r.umfang!!.wert, 1e-9)
            assertEquals(Netz.Bezug.UMSATZ, r.umfang!!.bezug)
            assertEquals(k.stand, r.stand)
            assertEquals(k.quelle.url, r.quelle.url)
            assertEquals(k.quelle.name, r.quelle.name)
            assertTrue(r.quelle.primaer)
            assertEquals(k.hinweis, r.hinweis)
            assertEquals(k.hinweisEn, r.hinweisEn)
            assertEquals(Netz.Herkunft.GEMELDET, r.herkunft)
        }
        // Der Zulieferer mit Kuerzel behaelt Kuerzel und CIK
        val chip = zurueck.knoten.first { it.id == "CHIP" }
        assertEquals(listOf("CHIP"), chip.symbole)
        assertEquals("1000002", chip.cik)
    }

    // ------------------------------------------------------------ Texte, Datei und Alterung

    @Test fun hinweisTextHatBeideSprachen() {
        assertTrue(NetzTexte.schluessel(true).contains("hinweis.kunde"))
        assertTrue(NetzTexte.schluessel(false).contains("hinweis.kunde"))
        assertEquals(NetzTexte.schluessel(true), NetzTexte.schluessel(false))
        assertFalse(NetzTexte.text(true, "hinweis.kunde").contains("—"))
    }

    @Test fun eingebauteDateiIstLesbarUndSauber() {
        val roh = NetzTestHilfe.asset(NetzZulieferer.DATEI).readText(Charsets.UTF_8)
        val d = NetzZulieferer.lesen(roh)
        assertNotNull(d)
        d!!
        assertTrue(roh.toByteArray().size < Datenbuendel.MAX_DATEI)
        assertTrue(d.zeilen.size >= 100)
        // Stand ist ein Datum, jede Zeile hat Anteil, Stichtag und Einreichung (der Link zeigt auf ein einzelnes Dokument)
        assertTrue(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(d.stand))
        for (z in d.zeilen) {
            assertTrue(z.prozent in 1.0..100.0)
            assertTrue(z.stichtag <= d.stand)
            assertTrue(Netz.einzeldokument(NetzAuto.einreichungsseite(z.cik, z.adsh)))
            assertTrue(z.form in setOf("10-K", "20-F", "40-F"))
            assertTrue(NetzZulieferer.schluessel(z.kunde).isNotEmpty())
        }
        // Kein Zulieferer nennt sich selbst als Kunden
        assertTrue(d.zeilen.none { NetzZulieferer.schluessel(it.kunde) == NetzZulieferer.schluessel(it.name) })
    }

    @Test fun dateiIstImBuendelEingetragen() {
        assertEquals(1, Datenbuendel.SCHEMA[NetzZulieferer.DATEI])
        assertTrue(NetzZulieferer.DATEI in Alter.dateien())
        assertEquals(Alter.NETZ, Alter.profilFuer(NetzZulieferer.DATEI))
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(NetzZulieferer.DATEI, text))
        assertFalse(de.troetenrepublik.app.market.Daten.probeLesen(NetzZulieferer.DATEI, """{"zeilen":[]}"""))
    }
}
