package de.troetenrepublik.app.market

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Form-AP-Index aus selbst formulierten CSV-Zeilen (erfundene Firmen und Personen, Spalten wie im Bestand des PCAOB).
class FirmaPcaobTest {
    private val kopf = "Form Filing ID,Latest Form AP Filing,Firm ID,Firm Name,Audit Report Type,Issuer Name,Issuer CIK,\"Issuer Ticker \n(Not Available)\",Fiscal Period End Date,Engagement Partner Last Name,Engagement Partner First Name,Engagement Partner Middle Name,Engagement Partner ID,Audit Not Divided Percent Information"

    private fun zeile(neu: String, firma: String, art: String, cik: String, ende: String, nach: String, vor: String, pid: String, andere: String = "") =
        "1,$neu,5,\"$firma\",\"$art\",\"Beispiel AG\",$cik,,$ende 12:00:00 AM,$nach,$vor,,$pid,\"${andere.replace("\"", "\"\"")}\""

    private val emittent = "Issuer, other than Employee Benefit Plan or Investment Company"
    private val andere = """[{"FirmID":"7","FirmName":"Muster Ltd, London","Percentage":"","Range":"20% to less than 30%"},{"FirmID":"8","FirmName":"Klein","Percentage":"6","Range":""},{"FirmID":"9","FirmName":"Winzig","Percentage":"","Range":"Less than 5%"}]"""
    private val csv = listOf(
        kopf,
        zeile("1", "Muster LLP", emittent, "0000123456", "12/31/2022", "Meier", "Anna", "P1"),
        zeile("1", "Muster LLP", emittent, "123456", "12/31/2023", "Meier", "Anna", "P1", andere),
        zeile("1", "Muster LLP", emittent, "123456", "12/31/2024", "Meier", "Anna", "P1"),
        zeile("0", "Muster LLP", emittent, "123456", "12/31/2024", "Alt", "Ottmar", "P0"),
        zeile("1", "Muster LLP", "Investment Company", "999", "12/31/2024", "Fonds", "Franz", "P9"),
    ).joinToString("\r\n") + "\r\n"

    private fun index(text: String, stand: String = "2026-11-13") = Pcaob.aus(Utf8Strom(ByteArrayInputStream(text.toByteArray())), stand)

    @Test fun csvMitUmbruchImKopfUndAnfuehrungszeichen() {
        val l = Pcaob.CsvLeser(Utf8Strom(ByteArrayInputStream("a,\"b\nc\",\"d \"\"e\"\"\"\n1,2,3\n".toByteArray())))
        assertEquals(listOf("a", "b\nc", "d \"e\""), l.satz())
        assertEquals(listOf("1", "2", "3"), l.satz())
        assertNull(l.satz())
    }

    @Test fun datum() {
        assertEquals("2017-07-20", Pcaob.datum("7/20/2017 12:00:00 AM"))
        assertEquals("", Pcaob.datum("kein Datum"))
    }

    @Test fun andereMitAnteilAb5Prozent() {
        val a = Pcaob.andere(andere)
        assertEquals(listOf(Pcaob.Anteil("Muster Ltd  London", 20), Pcaob.Anteil("Klein", 6)), a)
        assertTrue(Pcaob.andere("").isEmpty())
        assertTrue(Pcaob.andere("kein json").isEmpty())
    }

    @Test fun indexNurGueltigeMeldungenVonEmittenten() {
        val i = index(csv)
        val e = i.fuer("0000123456")!!
        assertEquals(listOf("2024-12-31", "2023-12-31", "2022-12-31"), e.eintraege.map { it.ende })
        assertEquals("Anna Meier", e.aktuell.partner)
        assertEquals("Muster LLP", e.aktuell.firma)
        assertEquals(2022, e.partnerSeit)
        assertNull(i.fuer("999"))
        assertEquals(2, e.eintraege[1].andere.size)
    }

    @Test fun partnerwechselBeendetDieReihe() {
        val c = listOf(
            kopf,
            zeile("1", "Muster LLP", emittent, "5", "12/31/2022", "A", "A", "P1"),
            zeile("1", "Muster LLP", emittent, "5", "12/31/2023", "B", "B", "P2"),
            zeile("1", "Muster LLP", emittent, "5", "12/31/2024", "B", "B", "P2"),
        ).joinToString("\n")
        assertEquals(2023, index(c, "x").fuer("5")!!.partnerSeit)
    }

    @Test fun nurDieLetztenVierJahre() {
        val c = (2018..2024).map { zeile("1", "M", emittent, "7", "12/31/$it", "N", "V", "P1") }
        val e = index((listOf(kopf) + c).joinToString("\n"), "x").fuer("7")!!
        assertEquals(4, e.eintraege.size)
        assertEquals("2021-12-31", e.eintraege.last().ende)
    }

    @Test fun ablageHinUndZurueck() {
        val i = index(csv)
        val j = Pcaob.lesen(Pcaob.schreiben(i))
        assertNotNull(j)
        assertEquals("2026-11-13", j!!.stand)
        assertEquals(i.fuer("123456")!!.eintraege, j.fuer("123456")!!.eintraege)
        assertNull(Pcaob.lesen("v0\tx"))
        assertFalse(j.werte.isEmpty())
    }
}
