package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Fda
import de.troetenrepublik.app.market.Leerpositionen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.9: Leerverkaufspositionen aus Schweden und Grossbritannien, Zulassungen und Rueckrufe der FDA.
class Fassung59Test {
    private val heute = java.time.LocalDate.of(2026, 10, 23)

    @Test fun fcaSummenUndVerlauf() {
        val aktuell = "Name of Company,International Securities Identification Number (ISIN),Aggregated net short position (%),Position date (of latest position date notified)\n" +
            "\"BOKU, INC.\",US09853C1009,0.6,15/10/2026\nBeispiel PLC,GB0000000001,1.1,02/10/2026\n"
        val historisch = "Name of Company,International Securities Identification Number (ISIN),Aggregated net short position (%),Position date,Date the aggregated net short position became historical\n" +
            "Beispiel PLC,GB0000000001,0.8,01/09/2026,02/10/2026\nAlt PLC,GB0000000002,0.4,01/08/2026,12/10/2026\n"
        val t = Leerpositionen.fcaAuswerten(aktuell, historisch, heute)
        assertEquals("BOKU, INC.", t["US09853C1009"]!!.e)
        val b = t["GB0000000001"]!!
        assertEquals(1.1, b.summe, 1e-9)
        assertTrue(b.positionen.isEmpty())
        // 2026-09-25: noch der fruehere Wert 0,8; ab 2026-10-02 der aktuelle
        assertEquals(0.8, b.verlauf.single { it.t == "2026-09-25" }.s, 1e-9)
        assertEquals(1.1, b.verlauf.single { it.t == "2026-10-09" }.s, 1e-9)
        // Nicht mehr aktuell: gesamt 0, Verlauf bis zum Ende der Meldung
        val alt = t["GB0000000002"]!!
        assertEquals(0.0, alt.summe, 1e-9)
        assertEquals(0.4, alt.verlauf.single { it.t == "2026-10-09" }.s, 1e-9)
        assertEquals(0.0, alt.verlauf.last().s, 1e-9)
    }

    private fun ods(zeilen: List<List<String>>): ByteArray {
        val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content><office:body><office:spreadsheet><table:table table:name=\"Blad1\">")
        for (z in zeilen) {
            xml.append("<table:table-row table:style-name=\"ro1\">")
            for (c in z) {
                val zahl = c.toDoubleOrNull()
                if (zahl != null) xml.append("<table:table-cell office:value-type=\"float\" office:value=\"$c\"><text:p>${c.replace('.', ',')}</text:p></table:table-cell>")
                else xml.append("<table:table-cell office:value-type=\"string\"><text:p>${c.replace("&", "&amp;")}</text:p></table:table-cell>")
            }
            xml.append("<table:table-cell table:number-columns-repeated=\"3\"/></table:table-row>")
        }
        xml.append("<table:table-row table:number-rows-repeated=\"100\"><table:table-cell table:number-columns-repeated=\"6\"/></table:table-row>")
        xml.append("</table:table></office:spreadsheet></office:body></office:document-content>")
        val aus = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(aus).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("mimetype")); zip.write("application/vnd.oasis.opendocument.spreadsheet".toByteArray()); zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("content.xml")); zip.write(xml.toString().toByteArray()); zip.closeEntry()
        }
        return aus.toByteArray()
    }

    @Test fun fiAktuelleDateiUndFortschreiben() {
        val datei = ods(listOf(
            listOf("Aktuella positioner"), listOf(), listOf(),
            listOf("Innehavare av positionen (Position holder)", "Namn på emittent (Name of the issuer)", "ISIN", "Position i procent(Position in per cent)", "Datum för positionen (Position date)", "Kommentar(Comment)"),
            listOf("Fonds A", "Beispiel AB", "SE0000000001", "1.08", "2026-10-22"),
            listOf("Fonds B & Co", "Beispiel AB", "SE0000000001", "0.6", "2026-10-01T00:00:00"),
            listOf("Fonds C", "Andere AB", "SE0000000002", "<0,5", "2026-10-21"),
        ))
        val neu = Leerpositionen.fiAktuell(datei)
        assertEquals(setOf("SE0000000001"), neu.keys)
        val (name, pos) = neu["SE0000000001"]!!
        assertEquals("Beispiel AB", name)
        assertEquals(listOf("Fonds A", "Fonds B & Co"), pos.map { it.h })
        assertEquals("2026-10-01", pos[1].d)
        val alt = mapOf(
            "SE0000000001" to Leerpositionen.Titel("Beispiel AB", verlauf = listOf(Leerpositionen.Punkt("2025-01-01", 3.0), Leerpositionen.Punkt("2026-10-16", 1.5))),
            "SE0000000003" to Leerpositionen.Titel("Weg AB", listOf(Leerpositionen.Position("X", 0.7, "2026-01-01")), listOf(Leerpositionen.Punkt("2026-10-16", 0.7))),
        )
        val f = Leerpositionen.fortschreiben(alt, neu, heute)
        val b = f["SE0000000001"]!!
        assertEquals(listOf("2026-10-16", "2026-10-23"), b.verlauf.map { it.t })
        assertEquals(1.68, b.verlauf.last().s, 1e-9)
        // Position geschlossen: Aktie bleibt mit dem Verlauf, ohne offene Positionen
        val w = f["SE0000000003"]!!
        assertTrue(w.positionen.isEmpty())
        assertEquals(0.0, w.verlauf.last().s, 1e-9)
    }

    @Test fun landFuerIsin() {
        assertEquals("GB", Leerpositionen.landFuerIsin("JE00B4T3BW64"))
        assertEquals("SE", Leerpositionen.landFuerIsin("SE0000163628"))
        assertNull(Leerpositionen.landFuerIsin("DE0007164600"))
    }

    @Test fun fdaNamenUndZuordnung() {
        assertEquals("PFIZER", Fda.normal("Pfizer Inc."))
        assertEquals("NOVO NORDISK", Fda.normal("Novo Nordisk A/S"))
        assertEquals("JOHNSON AND JOHNSON", Fda.normal("Johnson & Johnson"))
        val pfizer = Fda.namen("Pfizer Inc.")
        assertTrue("HOSPIRA" in pfizer)
        assertTrue(Fda.passt("PFIZER INC", pfizer))
        assertTrue(Fda.passt("PFIZER PHARMS", pfizer))
        assertTrue(Fda.passt("Hospira Inc., A Pfizer Company", pfizer))
        assertTrue(Fda.passt("King Legacy, a wholly owned subsidiary of Pfizer", pfizer))
        assertFalse(Fda.passt("Elite Laboratories Inc", Fda.namen("Eli Lilly and Company")))
        assertTrue(Fda.passt("ELI LILLY AND CO", Fda.namen("Eli Lilly and Company")))
        assertTrue(Fda.passt("LILLY", Fda.namen("Eli Lilly and Company")))
        // Merck KGaA ist nicht Merck & Co.
        assertFalse(Fda.passt("MERCK SHARP DOHME", Fda.namen("Merck KGaA")))
        assertTrue(Fda.passt("EMD Serono Inc", Fda.namen("Merck KGaA")))
        assertTrue(Fda.betroffen("Drug Manufacturers - General", "Healthcare"))
        assertFalse(Fda.betroffen("Semiconductors", "Technology"))
    }

    @Test fun fdaZulassungenUndRueckrufeLesen() {
        val zul = """{"results":[{"application_number":"NDA000001","sponsor_name":"PFIZER INC",
            "products":[{"brand_name":"BEISPIELA","active_ingredients":[{"name":"WIRKSTOFF"}]}],
            "submissions":[
              {"submission_type":"ORIG","submission_status":"AP","submission_status_date":"20260904","submission_class_code":"TYPE 1","review_priority":"PRIORITY"},
              {"submission_type":"SUPPL","submission_status":"AP","submission_status_date":"20260910","submission_class_code":"EFFICACY"},
              {"submission_type":"SUPPL","submission_status":"AP","submission_status_date":"20260911","submission_class_code":"LABELING"},
              {"submission_type":"ORIG","submission_status":"AP","submission_status_date":"20200101","submission_class_code":"TYPE 1"}]},
            {"application_number":"NDA000002","sponsor_name":"PFIZERISH LABS","submissions":[{"submission_type":"ORIG","submission_status":"AP","submission_status_date":"20260904"}]}]}"""
        val z = Fda.zulassungenLesen(zul, Fda.namen("Pfizer Inc."), "2025-10-23", "2026-10-23")
        assertEquals(listOf(Fda.Art.ANWENDUNG, Fda.Art.NEU), z.map { it.art })
        assertTrue(z.last().vorrang)
        assertEquals("2026-09-04", z.last().tag)
        val rr = """{"results":[{"recall_number":"D-1","classification":"Class I","recalling_firm":"Pfizer Inc.","product_description":"Mittel  A","reason_for_recall":"Grund","report_date":"20261014","status":"Ongoing"},
            {"recall_number":"D-2","classification":"Class II","recalling_firm":"Andere Firma","report_date":"20261014"}]}"""
        val r = Fda.rueckrufeLesen(rr, Fda.namen("Pfizer Inc."), geraet = false)
        assertEquals(1, r.size)
        assertEquals(1, r[0].klasse)
        assertEquals("Mittel A", r[0].produkt)
        assertTrue(r[0].laufend)
        assertTrue(Fda.rueckrufeLesen("{\"error\":{}}", Fda.namen("Pfizer"), true).isEmpty())
    }

    @Test fun marktphase() {
        val tag = 86_400_000L
        // 300 Tage Anstieg auf 200, Absturz auf 150 (minus 25 %), Erholung auf 185 (plus 23 % vom Tief)
        val kurse = (0 until 300).map { it * tag to 100.0 + it / 3.0 } +
            (0 until 100).map { (300 + it) * tag to 200.0 - it * 0.5 } +
            (0 until 70).map { (400 + it) * tag to 150.0 + it * 0.5 }
        val p = Marktphase.phase("x", kurse)!!
        assertEquals(Marktphase.Art.BULLE, p.art)
        assertEquals(400 * tag, p.seit)
        assertTrue(p.vomHoch < -5 && p.vomHoch > -10)
        assertEquals(false, p.korrektur)
        assertEquals(false, p.golden)
        val baer = Marktphase.phase("x", kurse.take(400))!!
        assertEquals(Marktphase.Art.BAER, baer.art)
        assertEquals(300 * tag, baer.seit)
        val korr = Marktphase.phase("x", kurse.take(345))!!
        assertEquals(Marktphase.Art.BULLE, korr.art)
        assertTrue(korr.korrektur)
        assertNull(Marktphase.phase("x", kurse.take(10)))
    }

    @Test fun bullBearLeerverkaufUndFda() {
        val p = BullBear.auswerten(BullBear.Fakten(leerverkauf = 10.0, fdaZulassungen = 2, fdaRueckrufe = 1), emptyList(), 0L)
        assertTrue(p.any { it.kennzahl == BullBear.Kennzahl.LEERVERKAUF_HOCH })
        assertTrue(p.any { it.kennzahl == BullBear.Kennzahl.FDA_ZULASSUNG })
        assertTrue(p.any { it.kennzahl == BullBear.Kennzahl.FDA_RUECKRUF })
        assertTrue(BullBear.auswerten(BullBear.Fakten(leerverkauf = 1.0, fdaZulassungen = 0, fdaRueckrufe = 0), emptyList(), 0L).isEmpty())
    }

    @Test fun ogeNeueMeldungen() {
        val json = """{"draw":1,"recordsTotal":2,"data":[
            {"type":"278 Transaction (<a href='https://extapps2.oge.gov/201/Presiden.nsf/PAS+Index/0BDCCCE7DE13AEFA85258E8A002DE2B5/${'$'}FILE/Donald-J-Trump.09.17.2026-278T.pdf'>View Document</a>)","name":"Trump, Donald J.","docDate":"2026-10-08T04:21:11"},
            {"type":"278 Transaction (<a href='https://extapps2.oge.gov/201/Presiden.nsf/PAS+Index/E590116FC9631E9885258E7A002DE209/${'$'}FILE/Donald-J-Trump-09.8.2026-278T.pdf'>View Document</a>)","name":"Trump, Donald J.","docDate":"2026-09-22T04:21:09"},
            {"type":"278 Transaction (<a href='https://x/PAS+Index/ABCDEF12/${'$'}FILE/a.pdf'>View</a>)","name":"Trump, Lara","docDate":"2026-10-09T00:00:00"}]}"""
        val l = de.troetenrepublik.app.market.Politik.ogeMeldungen(json, "Trump", "Donald")
        assertEquals(2, l.size)
        assertEquals("2026-10-08", l.first().tag)
        val p = de.troetenrepublik.app.market.Politik.RPerson("trump", "Donald J. Trump", listOf(
            de.troetenrepublik.app.market.Politik.RMeldung(datei = "https://extapps2.oge.gov/201/Presiden.nsf/PAS+Index/E590116F", unterschrieben = "2026-09-08")))
        val neu = de.troetenrepublik.app.market.Politik.ogeNeu(l, p)
        assertEquals(1, neu.size)
        assertEquals("0BDCCCE7", de.troetenrepublik.app.market.Politik.ogeKennung(neu.single().pdf))
    }
}
