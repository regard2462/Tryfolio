package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.EuInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.8: Insidergeschaefte aus Schweden (Finansinspektionen) und Positionierung an den Terminmaerkten (CFTC).
class Fassung58Test {
    private val kopf = "Publiceringsdatum;Emittent;LEI-kod;Anmälningsskyldig;Befattning;Närstående;Korrigering;Beskrivning av korrigering;" +
        "Är förstagångsrapportering;Är kopplad till aktieprogram;Karaktär;Instrumenttyp;Instrumentnamn;ISIN;Transaktionsdatum;" +
        "Volym;Volymsenhet;Pris;Valuta;Handelsplats;Status;Person i ledande ställning"

    private fun zeile(person: String, befattning: String, nah: String, art: String, typ: String, tag: String, volym: String, pris: String, status: String = "Aktuell", fuer: String = "") =
        "2026-10-20 08:00:00;Beispiel AB;LEI;$person;$befattning;$nah;;;;;$art;$typ;Beispiel AB;SE0000000001;$tag 00:00:00;$volym;Antal;$pris;SEK;NASDAQ STOCKHOLM AB;$status;$fuer"

    @Test fun schwedenExportLesen() {
        val text = listOf(
            kopf,
            zeile("Anna Svensson", "Verkställande direktör (VD)", "", "Förvärv", "Aktie", "2026-10-19", "1 000", "50,5"),
            zeile("Anna Svensson", "Verkställande direktör (VD)", "", "Förvärv", "Aktie", "2026-10-19", "500", "52"),
            zeile("Erik Berg", "Styrelseledamot", "", "Avyttring", "Aktie", "2026-10-18", "2000", "49"),
            zeile("Erik Berg", "Styrelseledamot", "", "Avyttring", "Aktie", "2026-10-17", "2000", "49", status = "Historisk"),
            zeile("Karin Berg", "Styrelseledamot", "Ja", "Tilldelning", "Aktie", "2026-10-16", "300", "0", fuer = "Erik Berg"),
        ).joinToString("\r\n")
        val liste = EuInsider.schwedenLesen(text)
        assertEquals(3, liste.size)
        val anna = liste.single { it.p == "Anna Svensson" }
        assertEquals("S", anna.r)
        assertEquals("K", anna.art)
        assertEquals(1500.0, anna.n, 1e-9)
        assertEquals(50_500.0 + 26_000.0, anna.wert, 1e-6)
        assertEquals(51.0, anna.preis, 1e-9)
        assertEquals("A", liste.single { it.p == "Erik Berg" }.r)
        val karin = liste.single { it.p == "Karin Berg" }
        assertEquals("N", karin.r)
        assertEquals("S", karin.art)
        assertEquals("Erik Berg", karin.fuer)
    }

    @Test fun schwedenUtf16OhneBom() {
        val roh = (kopf + "\r\n").toByteArray(Charsets.UTF_16LE)
        assertEquals(kopf + "\r\n", EuInsider.text(roh))
        assertEquals("abc", EuInsider.text(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "abc".toByteArray(Charsets.UTF_16LE)))
    }

    @Test fun schwedenTrendNurAktienkaeufeUndVerkaeufe() {
        fun g(p: String, r: String, art: String, i: String = "Aktie") = EuInsider.Geschaeft(e = "X", isin = "SE1", p = p, r = r, i = i, art = art, d = "2026-10-10", n = 1000.0, wert = 100_000.0)
        val t = EuInsider.trend(listOf(g("A", "S", "K"), g("B", "F", "K"), g("C", "A", "V"), g("D", "S", "V", i = "Köpoption"), g("E", "S", "S")), "2026-07-01")
        assertNotNull(t)
        assertEquals(2, t!!.kaeufer)
        assertEquals(1, t.verkaeufer)
        // (3 + 2 - 1,5) / 6,5
        assertEquals(54, t.wert)
        assertNull(EuInsider.trend(listOf(g("E", "S", "S")), "2026-07-01"))
    }

    @Test fun schwedenVereinenOhneDoppelte() {
        val a = EuInsider.Geschaeft(e = "X", isin = "SE1", p = "A", art = "K", d = "2026-10-01", wert = 1.0)
        val b = a.copy(wert = 2.0)
        val alt = a.copy(d = "2024-01-01")
        val v = EuInsider.vereinen(listOf(a, alt), listOf(b), "2025-01-01")
        assertEquals(1, v.size)
        assertEquals(2.0, v.single().wert, 0.0)
    }

    @Test fun cotLesenUndIndex() {
        val woche = (0 until 20).joinToString(",", "[", "]") { i ->
            val tag = java.time.LocalDate.of(2026, 1, 6).plusWeeks(i.toLong())
            """{"report_date_as_yyyy_mm_dd":"${tag}T00:00:00.000","open_interest_all":"1000","noncomm_positions_long_all":"${400 + i * 10}","noncomm_positions_short_all":"300"}"""
        }
        val wochen = Positionierung.lesen(woche)
        assertEquals(20, wochen.size)
        assertTrue(wochen.zipWithNext().all { it.first.tag < it.second.tag })
        assertEquals(10.0, wochen.first().anteil, 1e-9)
        val l = Positionierung.lage("gold", wochen)!!
        assertEquals(29.0, l.anteil, 1e-9)
        assertEquals(28.0, l.vorwoche, 1e-9)
        assertEquals(100, l.index)
        assertEquals(20, l.verlauf.size)
        assertNull(Positionierung.lage("gold", wochen.take(5)))
        assertTrue(Positionierung.lesen("kein json").isEmpty())
        assertTrue(Positionierung.MAERKTE.all { it.second.isNotBlank() })
        assertTrue(Positionierung.adresse("088691").startsWith("https://publicreporting.cftc.gov/"))
    }
    @Test fun amfLeerpositionen() {
        val kopf = "\"Detenteur de la position courte nette\";\"Legal Entity Identifier detenteur\";\"Emetteur / issuer\";\"Ratio\";\"code ISIN\";" +
            "\"Date de debut position\";\"Date de debut de publication position\";\"Date de fin de publication position\""
        fun z(h: String, r: String, beginn: String, ende: String = "") = "\"$h\";\"LEI\";\"BEISPIEL SA\";\"$r\";\"FR0000000001\";\"$beginn\";\"$beginn\";\"$ende\""
        val heute = java.time.LocalDate.of(2026, 10, 23)
        val text = listOf(
            kopf,
            z("FONDS A", "0.6", "2026-09-01"), z("FONDS A", "0.9", "2026-10-20"),
            z("FONDS B", "0.7", "2026-08-01", "2026-10-10"), z("FONDS B", "0.55", "2026-07-01", "2026-10-10"),
            z("FONDS C", "1.2", "2020-01-01", "2021-01-01"),
        ).joinToString("\n")
        val t = de.troetenrepublik.app.market.Leerpositionen.auswerten(text, heute)["FR0000000001"]!!
        assertEquals(listOf("FONDS A"), t.positionen.map { it.h })
        assertEquals(0.9, t.summe, 1e-9)
        assertEquals(53, t.verlauf.size)
        assertEquals("2026-10-23", t.verlauf.last().t)
        assertEquals(0.9, t.verlauf.last().s, 1e-9)
        // 2026-10-09: A mit 0,6 und B mit 0,7
        assertEquals(1.3, t.verlauf.single { it.t == "2026-10-09" }.s, 1e-9)
        assertTrue(de.troetenrepublik.app.market.Leerpositionen.auswerten("x;y", heute).isEmpty())
    }
}
