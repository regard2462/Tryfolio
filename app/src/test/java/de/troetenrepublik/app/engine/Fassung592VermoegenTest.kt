package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.RegierungOge
import de.troetenrepublik.app.ui.punkteZeile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: Vermoegen der weiteren Regierungsmitglieder aus ihren Vermoegensmeldungen (OGE 278e). Kurze
// Textausschnitte im Aufbau der PDFs aus Integrity.gov (so, wie PdfText sie liefert), Namen erfunden, ohne Netz.
class Fassung592VermoegenTest {

    // Jahresmeldung im Aufbau des Weissen Hauses: Kopf in einer Zeile, Ueberschriften vor den Tabellen,
    // Wert und Einkuenfte jeweils als Spanne, Verbindlichkeiten mit vermieteter Wohnung und Kreditkarte.
    private val jahresmeldung = """
        Muster, Max - Page 1
        OGE Form 278e (Updated 08/2024) (Expires 08/31/2027)
        Report Type: Annual Report
        Year (Annual Report only): 2026
        Filer's Information
        Muster, Max
        Deputy Director, Trump-Vance (2025) - White House
        Report Year: 2026
        /s/ Muster, Max [electronically signed on 06/26/2026 by Muster, Max in Integrity.gov]
        /s/ Pruefer, Paula, Certifying Official [electronically signed on 08/21/2026 by Pruefer, Paula in Integrity.gov]
        Muster, Max - Page 3
        1. Filer's Positions Held Outside United States Government
        2. Filer's Employment Assets & Income and Retirement Accounts
        None
        6. Other Assets and Income
        # ORGANIZATION NAME
        CITY, STATE ORGANIZATION
        TYPE
        1 Muster Beratung LLC
        See Endnote Sarasota, Florida Corporation President 2/2023
        Present
        Muster, Max - Page 4
        7. Transactions
        None
        8. Liabilities
        Endnotes
        # DESCRIPTION
        EIF
        VALUE
        INCOME TYPE INCOME
        AMOUNT
        1 Residential real estate, Cleveland, OH
        N/A
        ${'$'}250,001 -
        ${'$'}500,000
        Rent or Royalties ${'$'}15,001 -
        ${'$'}50,000
        2 U.S. bank (cash)
        N/A
        ${'$'}1,001 - ${'$'}15,000
        None (or less
        than ${'$'}201)
        # CREDITOR NAME
        TYPE
        AMOUNT YEAR
        INCURRED
        RATE
        TERM
        1 Beispiel Home Loan
        Mortgage
        (investment/rent
        al property)
        ${'$'}250,001 -
        ${'$'}500,000
        2019
        2.25
        30 years
        2 Beispielbank
        Credit Card ${'$'}10,001 -
        ${'$'}15,000
        2025
        No APR until
        January 2027
        Muster, Max - Page 5
        PART
        #
        ENDNOTE
        1.
        1
        Entity will remain dormant during my appointment, ${'$'}5,000,001 - ${'$'}25,000,000.
        Summary of Contents
        2. Filer's Employment Assets & Income and Retirement Accounts
        # DESCRIPTION EIF VALUE INCOME TYPE INCOME AMOUNT
        1 Beispiel N/A ${'$'}1,000,001 - ${'$'}5,000,000 None
    """.trimIndent().lines()

    // Antrittsmeldung im Aufbau der OGE: jede Zelle eine Zeile, Konten mit Unterposten (1, 1.1, 1.2), Gehalt ohne
    // Wert, Wert nicht bestimmbar, offene Spanne, „See Endnote“ vor EIF und eine Tabelle ueber zwei Seiten.
    private val antritt = """
        Beispiel, Berta Anna - Page 1
        OGE Form 278e (Updated 08/2024) (Expires 08/31/2027)
        Report Type: Nominee Report
        Year (Annual Report only):
        /s/ Beispiel, Berta Anna [electronically signed on 01/16/2025 by Beispiel, Berta Anna in Integrity.gov]
        Beispiel, Berta Anna - Page 7
        2. Filer's Employment Assets & Income and Retirement Accounts
        #
        DESCRIPTION
        EIF
        VALUE
        INCOME TYPE
        INCOME
        AMOUNT
        1
        Beispiel Capital, LLC (Venture capital
        firm)
        N/A
        Salary/Bonus
        ${'$'}2,537,996
        2
        Beispiel Capital, LLC, anticipated severance
        N/A
        ${'$'}250,001 -
        ${'$'}500,000
        None (or less
        than ${'$'}201)
        3
        Beispiel Fund LLC
        See Endnote
        No
        ${'$'}250,001 -
        ${'$'}500,000
        ${'$'}5,001 - ${'$'}15,000
        4
        401K with current employer
        No
        4.1
        Vanguard 500 Index Fund Admiral Shares
        Yes
        ${'$'}500,001 -
        ${'$'}1,000,000
        None (or less
        than ${'$'}201)
        Beispiel, Berta Anna - Page 8
        #
        DESCRIPTION
        EIF
        VALUE
        INCOME TYPE
        INCOME
        AMOUNT
        4.2
        Mid Cap Index Fund
        Yes
        ${'$'}100,001 -
        ${'$'}250,000
        None (or less
        than ${'$'}201)
        5
        Beispiel Growth Fund IV, Carried Interest - value not readily
        ascertainable
        N/A
        Carried Interest
        ${'$'}15,001 - ${'$'}50,000
        6
        Beispiel Opportunity Fund
        Yes
        Over
        ${'$'}50,000,000
        None (or less
        than ${'$'}201)
        7
        State pension, eligible for ${'$'}1,038/mo at age 55
        N/A
        None (or less
        than ${'$'}201)
        8. Liabilities
        #
        CREDITOR NAME
        TYPE
        AMOUNT
        YEAR
        INCURRED
        RATE
        TERM
        1
        Beispielbank
        Mortgage on Personal Residence
        ${'$'}1,000,001 - ${'$'}5,000,000
        2021
        3.375%
        30 years
        2
        Beispiel Trust
        Personal Loan
        ${'$'}250,001 - ${'$'}500,000
        2024
        3
        3 years
        3
        Beispiel Margin
        Margin loan (paid off)
        ${'$'}15,001 - ${'$'}50,000
        2025
        9. Gifts and Travel Reimbursements
        #
        SOURCE NAME
        CITY, STATE
        BRIEF DESCRIPTION
        VALUE
        1
        Beispielverein
        Washington, DC
        Reise
        ${'$'}1,250
        Privacy Act Statement
    """.trimIndent().lines()

    @Test fun jahresmeldungWeissesHaus() {
        val b = RegierungOge.vermoegenLesen(jahresmeldung)!!
        assertEquals("J", b.art)
        assertEquals(2026, b.jahr)
        assertEquals("2026-06-26", b.unterschrieben)
        // Wohnung und Konto; die Einkuenfte der Wohnung zaehlen nicht, die Endnote und die Erklaerungen auch nicht
        assertEquals(2, b.z)
        assertEquals(250_001L + 1_001L, b.av)
        assertEquals(500_000L + 15_000L, b.ab)
        assertFalse(b.ao)
        // Kredit auf die vermietete Wohnung und die Kreditkarte
        assertEquals(2, b.n)
        assertEquals(250_001L + 10_001L, b.sv)
        assertEquals(500_000L + 15_000L, b.sb)
    }

    @Test fun antrittsmeldungOge() {
        val b = RegierungOge.vermoegenLesen(antritt)!!
        assertEquals("A", b.art)
        // Ohne Berichtsjahr: Jahr der Unterschrift
        assertEquals(2025, b.jahr)
        assertEquals("2025-01-16", b.unterschrieben)
        // Abfindung, Fonds hinter „See Endnote“, zwei Unterposten (ueber die Seite hinweg) und die offene Spanne;
        // Gehalt, nicht bestimmbarer Wert und Rente ohne Spanne fehlen
        assertEquals(5, b.z)
        assertEquals(250_001L + 250_001L + 500_001L + 100_001L + 50_000_001L, b.av)
        assertEquals(500_000L + 500_000L + 1_000_000L + 250_000L + 50_000_001L, b.ab)
        assertTrue(b.ao)
        // Nur das Darlehen: Hypothek auf das eigene Haus und abbezahlter Kredit zaehlen nicht, Geschenke sind keine Schulden
        assertEquals(1, b.n)
        assertEquals(250_001L, b.sv)
        assertEquals(500_000L, b.sb)
    }

    @Test fun keineVermoegensmeldung() {
        assertNull(RegierungOge.vermoegenLesen(listOf("Periodic Transaction Report", "OGE Form 278-T")))
        assertNull(RegierungOge.vermoegenLesen(emptyList()))
        // 278e ohne Tabellen (etwa nur „None“): gelesen, aber ohne Werte
        val leer = RegierungOge.vermoegenLesen(listOf("OGE Form 278e", "Report Type: Termination Report", "6. Other Assets and Income", "None"))!!
        assertEquals("E", leer.art)
        assertEquals(0, leer.z)
    }

    @Test fun listeDerOge() {
        val text = """{"recordsFiltered":4,"data":[
            {"type":"<a href='https:\/\/extapps2.oge.gov\/201\/Presiden.nsf\/PAS+Index\/AA\/${'$'}FILE\/Max-Muster-2026-278ANNU.pdf'>Annual (2026)<\/a> (Amended 07\/20\/2026)","name":"Muster, Max A","agency":"Department of Examples","title":"Secretary","docDate":"2026-06-18T04:00:00"},
            {"type":"<a href='https:\/\/extapps2.oge.gov\/201\/Presiden.nsf\/PAS+Index\/BB\/${'$'}FILE\/Muster%2C%20Max%20%20final278.pdf'>Nominee 278<\/a>","name":"Muster, Max A","agency":"Department of Examples","title":"Secretary","docDate":"2025-02-07T04:00:00"},
            {"type":"Annual Term (<a href='https:\/\/extapps2.oge.gov\/201\/Presiden.nsf\/201%20Request?OpenForm&Filer=Alt'>Request this Document<\/a>)","name":"Alt, Anna","agency":"X","title":"Y","docDate":"2026-05-02T04:00:00"},
            {"type":"<a href='https:\/\/extapps2.oge.gov\/x\/Alt-2024-278TERM.pdf'>Termination<\/a>","name":"Alt, Anna","agency":"X","title":"Y","docDate":"2025-01-02T04:00:00"},
            {"type":"<a href='https:\/\/extapps2.oge.gov\/x\/Trump-2026.pdf'>Annual (2026)<\/a>","name":"Trump, Donald J","agency":"White House Office","title":"President","docDate":"2026-07-01T04:00:00"}
        ]}"""
        val (gesamt, liste) = RegierungOge.ogeVermoegenLesen(text)
        assertEquals(4, gesamt)
        // Nur verlinkte Meldungen seit Amtsbeginn, ohne den Praesidenten
        assertEquals(listOf("J", "A"), liste.map { it.art })
        assertTrue(liste.all { RegierungOge.kennung(it.name) == "muster-max" })
        assertTrue(liste[0].pdf.endsWith("Max-Muster-2026-278ANNU.pdf"))
        assertEquals("2026-06-18", liste[0].tag)
        assertTrue(RegierungOge.ogeSuche("Annual (2026)</a>", 100).endsWith("start=100&length=100&search%5Bvalue%5D=Annual%20%282026%29%3C%2Fa%3E"))
        assertEquals(listOf("Annual (2025)</a>", "Annual (2026)</a>", "Annual (2027)</a>", "Nominee 278</a>", "Termination</a>"), RegierungOge.ogeVermoegenSuchen(2026))
    }

    @Test fun listeDesWeissenHauses() {
        val u = "https://www.whitehouse.gov/wp-content/uploads"
        val html = listOf(
            "$u/2026/09/Muster-Max-2026-Annual.pdf", "$u/2026/09/Beispiel-Berta-2026-Annual-1.pdf", "$u/2026/09/Probe-Paul-Termination.pdf",
            "$u/2026/03/Probe-Paul-Termination-Report.pdf", "$u/2025/06/Blair-Michael-James.pdf", "$u/2025/06/Muster-Max-Periodic-Transaction-Report-05.08.25.pdf",
            "$u/2025/03/Memo-Max-Muster-3.5.2025-1.pdf", "$u/2026/06/Vice-President-JD-Vance-2025-Annual-Report.pdf", "$u/2025/03/20250218102929192.pdf",
            "$u/2026/04/Probe-Paul-Limited-Waiver-Pursuant-to-18-U.S.C.-208b1.pdf",
        ).joinToString("\n") { "<a href=\"$it\">x</a>" }
        val l = RegierungOge.weissesHausVermoegen(html)
        assertEquals(
            listOf("Muster, Max" to "J", "Beispiel, Berta" to "J", "Probe, Paul" to "E", "Probe, Paul" to "E", "Blair, Michael James" to "A"),
            l.map { it.name to it.art },
        )
        assertEquals("2026-09-01", l[0].tag)
        assertTrue(l.all { it.quelle == "WH" })
        assertEquals("blair-michael", RegierungOge.kennung(l.last().name))
    }

    @Test fun neuesteMeldungJePerson() {
        val p = RegierungOge.Person("muster-max", "Max Muster", meldungen = listOf(RegierungOge.Meldung("t.pdf", "2026-01-01")))
        var d = RegierungOge.Datei(1L, listOf(p, RegierungOge.Person("fremd-fritz", "Fritz Fremd")))
        d = RegierungOge.vermoegenEinarbeiten(
            d,
            listOf(
                RegierungOge.VEintrag("Muster, Max A", "2025-02-07", "n.pdf", "A", "OGE"),
                RegierungOge.VEintrag("Muster, Max A", "2026-06-18", "j.pdf", "J", "OGE"),
                // Ohne 278-T keine eigene Person
                RegierungOge.VEintrag("Neu, Nora", "2026-06-18", "x.pdf", "J", "OGE"),
            ),
        )
        assertEquals(2, d.personen.size)
        val max = d.personen[0]
        assertEquals(listOf("j.pdf", "n.pdf"), max.vermoegen.map { it.pdf })
        assertTrue(d.personen[1].vermoegen.isEmpty())
        // Erst die neueste; ist sie leer (gescannt), die naechstaeltere
        assertEquals("j.pdf", RegierungOge.vermoegenOffen(max)!!.pdf)
        assertNull(RegierungOge.vermoegenAktuell(max))
        d = RegierungOge.vermoegenEintragen(d, "muster-max", "j.pdf", null)
        assertEquals("n.pdf", RegierungOge.vermoegenOffen(d.personen[0])!!.pdf)
        val b = RegierungOge.vermoegenLesen(antritt)!!
        d = RegierungOge.vermoegenEintragen(d, "muster-max", "n.pdf", b)
        val aktuell = RegierungOge.vermoegenAktuell(d.personen[0])
        assertNotNull(aktuell)
        assertEquals("A", aktuell!!.art)
        assertEquals("2025-01-16", aktuell.stand)
        assertNull(RegierungOge.vermoegenOffen(d.personen[0]))
        // Eine neue Jahresmeldung wird wieder zuerst gelesen, die alten Werte bleiben bis dahin sichtbar
        d = RegierungOge.vermoegenEinarbeiten(d, listOf(RegierungOge.VEintrag("Muster, Max", "2027-06-01", "j2.pdf", "J", "OGE")))
        assertEquals("j2.pdf", RegierungOge.vermoegenOffen(d.personen[0])!!.pdf)
        assertEquals("n.pdf", RegierungOge.vermoegenAktuell(d.personen[0])!!.pdf)
        // Netto-Spanne fuer die Anzeige: Vermoegen minus Verbindlichkeiten
        val e = RegierungOge.alsSpanne(d.personen[0], aktuell)
        assertEquals(b.av - b.sb, e.nettoVon)
        // Gespeichert und wieder gelesen
        val zurueck = RegierungOge.lesen(RegierungOge.schreiben(d.copy(vAbgerufen = 5L)))!!
        assertEquals(5L, zurueck.vAbgerufen)
        assertEquals(d.personen, zurueck.personen)
        // Neue 278-T behalten die Vermoegensmeldungen
        val neu = RegierungOge.einarbeiten(zurueck, listOf(RegierungOge.Eintrag("Muster, Max", "X", "Secretary", "2026-09-01", "t2.pdf", "OGE")), 9L)
        assertEquals(3, neu.personen.first { it.id == "muster-max" }.vermoegen.size)
        assertEquals(5L, neu.vAbgerufen)
    }

    @Test fun punkteBrechenNurDazwischen() {
        val z = punkteZeile(listOf("Vermögen über 1,9 Mio. €", "12 Geschäfte", " ", "zuletzt 26.06.2026"))
        assertEquals(2, z.count { it == ' ' })
        assertEquals(listOf("Vermögen über 1,9 Mio. € ·", "12 Geschäfte ·", "zuletzt 26.06.2026"), z.split(' '))
    }
}
