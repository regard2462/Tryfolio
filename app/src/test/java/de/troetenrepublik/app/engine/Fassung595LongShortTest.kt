package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Firmenname
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.Zweitnotiz
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Namen wie sie die Yahoo-Suche am 03.11.2026 lieferte
class Fassung595LongShortTest {

    @Test
    fun suchbegriffe() {
        assertEquals(listOf("Novo Nordisk"), Zweitnotiz.usSuchbegriffe("Novo Nordisk (B)"))
        assertEquals(listOf("Novo Nordisk"), Zweitnotiz.usSuchbegriffe("Novo Nordisk A/S"))
        assertEquals(listOf("Novo Nordisk"), Zweitnotiz.usSuchbegriffe("Novo Nordisk B A/S"))
        assertEquals(listOf("Novo-Nordisk"), Zweitnotiz.usSuchbegriffe("Novo-Nordisk AS               N"))
        assertEquals(
            listOf("Telefonaktiebolaget LM Ericsson"),
            Zweitnotiz.usSuchbegriffe("Telefonaktiebolaget LM Ericsson (publ)"),
        )
        assertEquals(
            listOf("Ericsson, Telefonab. L M", "Ericsson"),
            Zweitnotiz.usSuchbegriffe("Ericsson, Telefonab. L M ser. B"),
        )
        assertEquals(listOf("Volvo"), Zweitnotiz.usSuchbegriffe("AB Volvo (publ)"))
        assertEquals(listOf("SAP"), Zweitnotiz.usSuchbegriffe("SAP SE                        I"))
        assertEquals(listOf("BAYERISCHE MOTOREN WERKE"), Zweitnotiz.usSuchbegriffe("BAYERISCHE MOTOREN WERKE AG   S"))
        assertEquals(listOf("SHELL"), Zweitnotiz.usSuchbegriffe("SHELL PLC ORD EUR0.07"))
        assertEquals(listOf("BP"), Zweitnotiz.usSuchbegriffe("BP p.l.c."))
        assertEquals(listOf("Siemens"), Zweitnotiz.usSuchbegriffe("Siemens Aktiengesellschaft"))
        assertEquals(listOf("UBS GROUP"), Zweitnotiz.usSuchbegriffe("UBS GROUP N"))
        assertEquals(listOf("Equinor"), Zweitnotiz.usSuchbegriffe("Equinor ASA"))
        assertEquals(listOf("Nokia"), Zweitnotiz.usSuchbegriffe("Nokia Oyj"))
    }

    @Test
    fun bereinigt() {
        assertEquals("Seven & i", Firmenname.bereinigt("Seven & i Holdings Co., Ltd."))
        assertEquals("Phillips 66", Firmenname.bereinigt("Phillips 66"))
        assertEquals("BP", Firmenname.bereinigt("BP PLC \$0.25"))
        assertEquals("UNILEVER", Firmenname.bereinigt("UNILEVER PLC ORD 3.5P"))
    }

    @Test
    fun gleicheFirmen() {
        assertTrue(Gleichwerte.namenGleich("Novo-Nordisk AS               N", "Novo Nordisk A/S"))
        assertTrue(Gleichwerte.namenGleich("SHELL PLC ORD EUR0.07", "Shell plc"))
        assertFalse(Gleichwerte.namenGleich("Puma Biotechnology, Inc.", "PUMA SE"))
        assertFalse(Gleichwerte.namenGleich("Mitsubishi UFJ Financial Group", "Mitsubishi Corporation"))
    }

    // Die US-Treffer der Yahoo-Suche (Symbol, Boerse, Name)
    private val usTreffer = listOf(
        Zweitnotiz.Kandidat("NVO", "NYQ", 100.0, "Novo Nordisk A/S"),
        Zweitnotiz.Kandidat("NONOF", "PNK", 100.0, "Novo-Nordisk A/S"),
        Zweitnotiz.Kandidat("ERIC", "NMS", 10.0, "Ericsson"),
        Zweitnotiz.Kandidat("ERIXF", "PNK", 10.0, "Ericsson (L.M.) Telephone Co."),
        Zweitnotiz.Kandidat("VLVLY", "PNK", 20.0, "Volvo AB"),
        Zweitnotiz.Kandidat("ATLC", "NMS", 30.0, "Atlanticus Holdings Corporation"),
        Zweitnotiz.Kandidat("ATLKY", "PNK", 30.0, "Atlas Copco AB"),
        Zweitnotiz.Kandidat("HL", "NYQ", 5.0, "Hecla Mining Company"),
        Zweitnotiz.Kandidat("HMC", "NYQ", 30.0, "Honda Motor Company, Ltd."),
        Zweitnotiz.Kandidat("CABGY", "PNK", 15.0, "Carlsberg AS"),
        Zweitnotiz.Kandidat("AMKBY", "PNK", 15.0, "A.P. Moeller-Maersk A/S"),
        Zweitnotiz.Kandidat("EQNR", "NYQ", 25.0, "Equinor ASA"),
        Zweitnotiz.Kandidat("NOK", "NYQ", 4.0, "Nokia Corporation Sponsored"),
        Zweitnotiz.Kandidat("NKRKY", "PNK", 40.0, "Nokian Tyres Plc."),
        Zweitnotiz.Kandidat("RHHBY", "OQX", 35.0, "Roche Holding Ltd"),
        Zweitnotiz.Kandidat("RCTFF", "PNK", 1.0, "Rochester Resources Ltd."),
        Zweitnotiz.Kandidat("NSLYF", "PNK", 90.0, "Nestle Malaysia BHD"),
        Zweitnotiz.Kandidat("UL", "NYQ", 60.0, "Unilever PLC"),
        Zweitnotiz.Kandidat("UNLRY", "PNK", 60.0, "PT Unilever Indonesia Tbk"),
        Zweitnotiz.Kandidat("SHEL", "NYQ", 70.0, "Shell PLC"),
        Zweitnotiz.Kandidat("BP", "NYQ", 35.0, "BP p.l.c."),
        Zweitnotiz.Kandidat("BPOP", "NMS", 80.0, "Popular, Inc."),
        Zweitnotiz.Kandidat("BPAQF", "PNK", 35.0, "BP Plc"),
        Zweitnotiz.Kandidat("ASML", "NMS", 700.0, "ASML Holding N.V. - New York Re"),
        Zweitnotiz.Kandidat("PHG", "NYQ", 25.0, "Koninklijke Philips N.V. NY Reg"),
        Zweitnotiz.Kandidat("TM", "NYQ", 180.0, "Toyota Motor Corporation"),
        Zweitnotiz.Kandidat("TYHOY", "PNK", 150.0, "Toyota Tsusho Corp."),
        Zweitnotiz.Kandidat("SONY", "NYQ", 90.0, "Sony Group Corporation"),
        Zweitnotiz.Kandidat("SFGYY", "PNK", 12.0, "Sony Financial Group Inc."),
        Zweitnotiz.Kandidat("SAP", "NYQ", 200.0, "SAP SE"),
        Zweitnotiz.Kandidat("SIEGY", "PNK", 100.0, "Siemens AG"),
        Zweitnotiz.Kandidat("IFNNY", "OQX", 35.0, "Infineon Technologies AG"),
        Zweitnotiz.Kandidat("BMWKY", "PNK", 85.0, "Bayerische Motoren Werke AG"),
        Zweitnotiz.Kandidat("TTE", "NYQ", 60.0, "TotalEnergies SE"),
        Zweitnotiz.Kandidat("SNY", "NMS", 45.0, "Sanofi"),
        Zweitnotiz.Kandidat("AZN", "NYQ", 65.0, "AstraZeneca PLC"),
        Zweitnotiz.Kandidat("NVS", "NYQ", 100.0, "Novartis AG"),
        Zweitnotiz.Kandidat("UBS", "NYQ", 30.0, "UBS Group AG Registered"),
        Zweitnotiz.Kandidat("UBSI", "NMS", 40.0, "United Bankshares, Inc."),
    )

    // Wie optionsKuerzel: Suchbegriffe der Reihe nach, der erste Treffer zaehlt
    private fun us(name: String): String? {
        for (begriff in Zweitnotiz.usSuchbegriffe(name)) {
            Zweitnotiz.usNotierung(begriff, usTreffer)?.let { return it }
        }
        return null
    }

    @Test
    fun usNotierungMitGattungUndRechtsform() {
        // Novo Nordisk: Heimatname, deutsche Kurznamen, Katalogname
        assertEquals("NVO", us("Novo Nordisk A/S"))
        assertEquals("NVO", us("Novo Nordisk B A/S"))
        assertEquals("NVO", us("Novo-Nordisk AS               N"))
        assertEquals("NVO", us("NOVO-NORDISK AS               I"))
        assertEquals("NVO", us("Novo-Nordisk AS (ADRs)"))
        assertEquals("NVO", us("Novo Nordisk (B)"))
        // Ericsson
        assertEquals("ERIC", us("Telefonaktiebolaget LM Ericsson (publ)"))
        assertEquals("ERIC", us("Ericsson, Telefonab. L M ser. B"))
        assertEquals("ERIC", us("Ericsson (B)"))
        // Equinor
        assertEquals("EQNR", us("Equinor ASA"))
        assertEquals("EQNR", us("EQUINOR"))
        // Nokia
        assertEquals("NOK", us("Nokia Oyj"))
        assertEquals("NOK", us("Nokia Corporation"))
        assertEquals("NOK", us("Nokia"))
        // Unilever
        assertEquals("UL", us("Unilever PLC"))
        assertEquals("UL", us("UNILEVER PLC ORD 3.5P"))
        assertEquals("UL", us("Unilever"))
        // Shell
        assertEquals("SHEL", us("Shell plc"))
        assertEquals("SHEL", us("SHELL PLC ORD EUR0.07"))
        assertEquals("SHEL", us("Shell"))
        // BP
        assertEquals("BP", us("BP p.l.c."))
        assertEquals("BP", us("BP PLC \$0.25"))
        assertEquals("BP", us("BP"))
        // ASML
        assertEquals("ASML", us("ASML Holding N.V."))
        assertEquals("ASML", us("ASML HOLDING"))
        assertEquals("ASML", us("ASML"))
        // Philips
        assertEquals("PHG", us("Koninklijke Philips N.V."))
        assertEquals("PHG", us("Philips"))
        // Toyota
        assertEquals("TM", us("Toyota Motor Corporation"))
        assertEquals("TM", us("Toyota"))
        // Sony
        assertEquals("SONY", us("Sony Group Corporation"))
        assertEquals("SONY", us("Sony"))
        // SAP
        assertEquals("SAP", us("SAP SE"))
        assertEquals("SAP", us("SAP SE                        I"))
        assertEquals("SAP", us("SAP"))
        // TotalEnergies
        assertEquals("TTE", us("TotalEnergies SE"))
        assertEquals("TTE", us("TotalEnergies"))
        // Sanofi
        assertEquals("SNY", us("Sanofi"))
        // AstraZeneca
        assertEquals("AZN", us("AstraZeneca PLC"))
        assertEquals("AZN", us("ASTRAZENECA PLC ORD SHS \$0.25"))
        // Novartis
        assertEquals("NVS", us("Novartis AG"))
        assertEquals("NVS", us("NOVARTIS N"))
        assertEquals("NVS", us("Novartis"))
        // UBS
        assertEquals("UBS", us("UBS Group AG"))
        assertEquals("UBS", us("UBS GROUP N"))
        assertEquals("UBS", us("UBS"))
        // Nur OTC-Treffer oder fremde Firmen: keine US-Notierung
        assertNull(us("AB Volvo (publ)"))
        assertNull(us("Volvo, AB ser. B"))
        assertNull(us("Volvo (B)"))
        assertNull(us("Atlas Copco AB (publ)"))
        assertNull(us("Atlas Copco AB ser. A"))
        assertNull(us("Atlas Copco (A)"))
        assertNull(us("H & M Hennes & Mauritz AB (publ)"))
        assertNull(us("Hennes & Mauritz AB, H & M ser."))
        assertNull(us("H&M (B)"))
        assertNull(us("Carlsberg A/S"))
        assertNull(us("Carlsberg B A/S"))
        assertNull(us("Carlsberg (B)"))
        assertNull(us("A.P. Møller - Mærsk A/S"))
        assertNull(us("A.P. Møller - Mærsk B A/S"))
        assertNull(us("A.P. Møller - Mærsk (B)"))
        assertNull(us("Roche Holding AG"))
        assertNull(us("ROCHE PS"))
        assertNull(us("Roche"))
        assertNull(us("Nestlé S.A."))
        assertNull(us("NESTLE N"))
        assertNull(us("Nestlé"))
        assertNull(us("Siemens Aktiengesellschaft"))
        assertNull(us("SIEMENS AG                    N"))
        assertNull(us("Siemens"))
        assertNull(us("Infineon Technologies AG"))
        assertNull(us("INFINEON TECHNOLOGIES AG      N"))
        assertNull(us("Infineon"))
        assertNull(us("Bayerische Motoren Werke Aktiengesellschaft"))
        assertNull(us("BAYERISCHE MOTOREN WERKE AG   S"))
        assertNull(us("BMW"))
    }

    @Test
    fun zweitesFormat() {
        assertEquals("SAP", Zweitnotiz.usNotierung("SAP", listOf(Zweitnotiz.Kandidat("SAP", "NYSE", 200.0, "SAP SE"))))
        assertEquals("SNY", Zweitnotiz.usNotierung("Sanofi", listOf(Zweitnotiz.Kandidat("SNY", "NAS", 45.0, "Sanofi SA"))))
        assertEquals(
            "UBS",
            Zweitnotiz.usNotierung("UBS Group AG", listOf(Zweitnotiz.Kandidat("UBS", "NYSE", 30.0, "UBS Group AG"))),
        )
        assertNull(
            Zweitnotiz.usNotierung(
                "UBS",
                listOf(Zweitnotiz.Kandidat("DHY", "AMEX", 5.0, "Credit Suisse High Yield Credit Fund")),
            ),
        )
        assertNull(Zweitnotiz.usNotierung("UBS", listOf(Zweitnotiz.Kandidat("SPPJY", "OTCM", 20.0, "Sappi Ltd"))))
    }

    @Test
    fun getrenntBleiben() {
        assertNull(Zweitnotiz.usNotierung("PUMA", listOf(Zweitnotiz.Kandidat("PBYI", "NMS", 5.0, "Puma Biotechnology, Inc."))))
        assertNull(
            Zweitnotiz.usNotierung(
                "Mitsubishi Corporation",
                listOf(Zweitnotiz.Kandidat("MUFG", "NYQ", 12.0, "Mitsubishi UFJ Financial Group,")),
            ),
        )
        assertNull(Zweitnotiz.usNotierung("Orion", listOf(Zweitnotiz.Kandidat("ORN", "NYQ", 12.0, "Orion Group Holdings, Inc."))))
        assertNull(
            Zweitnotiz.usNotierung(
                "Orion",
                listOf(Zweitnotiz.Kandidat("ORN", "NYQ", 12.0, "Orion Group Holdings, Inc. Class A")),
            ),
        )
        assertNull(
            Zweitnotiz.usNotierung(
                "Orion",
                listOf(Zweitnotiz.Kandidat("ORN", "NYQ", 12.0, "Orion Group Holdings, Inc. Registered")),
            ),
        )
        assertNull(
            Zweitnotiz.usNotierung("Santander", listOf(Zweitnotiz.Kandidat("BSBR", "NYQ", 12.0, "Banco Santander Brasil SA"))),
        )
        assertNull(Zweitnotiz.usNotierung("Siemens", listOf(Zweitnotiz.Kandidat("SIEB", "NMS", 3.0, "Siebert Financial Corp."))))
        assertNull(
            Zweitnotiz.usNotierung(
                "Atlas Copco",
                listOf(Zweitnotiz.Kandidat("ATLC", "NMS", 30.0, "Atlanticus Holdings Corporation")),
            ),
        )
        assertNull(
            Zweitnotiz.usNotierung(
                "H&M",
                listOf(
                    Zweitnotiz.Kandidat("HL", "NYQ", 5.0, "Hecla Mining Company"),
                    Zweitnotiz.Kandidat("HMC", "NYQ", 30.0, "Honda Motor Company, Ltd."),
                ),
            ),
        )
        assertNull(Zweitnotiz.usNotierung("Nokia", listOf(Zweitnotiz.Kandidat("NKRKY", "NYQ", 40.0, "Nokian Tyres Plc."))))
        assertNull(Zweitnotiz.usNotierung("Sony", listOf(Zweitnotiz.Kandidat("SFGYY", "NYQ", 12.0, "Sony Financial Group Inc."))))
        assertNull(Zweitnotiz.usNotierung("Toyota", listOf(Zweitnotiz.Kandidat("TYHOY", "NYQ", 150.0, "Toyota Tsusho Corp."))))
        assertNull(
            Zweitnotiz.usNotierung("Unilever", listOf(Zweitnotiz.Kandidat("UNLRY", "NYQ", 60.0, "PT Unilever Indonesia Tbk"))),
        )
    }

    @Test
    fun usSuche() {
        runBlocking {
            // Der zweite Begriff wird gefragt, wenn der erste keine Treffer liefert
            val erste = mapOf(
                "Ericsson, Telefonab. L M" to emptyList<Zweitnotiz.Kandidat>(),
                "Ericsson" to listOf(Zweitnotiz.Kandidat("ERIC", "NMS", 10.0, "Ericsson")),
            )
            assertEquals("ERIC", Zweitnotiz.usSuchen("Ericsson, Telefonab. L M ser. B") { erste[it] })
            // Eine Suche ohne Antwort (Netzfehler) zaehlt nicht als durchsucht
            val stoerung = mapOf(
                "Ericsson, Telefonab. L M" to listOf(Zweitnotiz.Kandidat("ERIXF", "PNK", 10.0, "Ericsson (L.M.) Telephone Co.")),
                "Ericsson" to null,
            )
            assertNull(Zweitnotiz.usSuchen("Ericsson, Telefonab. L M ser. B") { stoerung[it] })
            // Alle Begriffe beantwortet, keiner passt (nur OTC-Treffer)
            val otc = mapOf("Volvo" to listOf(Zweitnotiz.Kandidat("VLVLY", "PNK", 20.0, "Volvo AB")))
            assertEquals("", Zweitnotiz.usSuchen("Volvo (B)") { otc[it] })
            assertNull(Zweitnotiz.usSuchen("Volvo (B)") { null })
        }
    }

    // Sperrt Gleichwerte wie Orion Group Holdings, Inc. gegenueber Orion Oyj; gleiche Wortzahl bleibt gleich
    @Test
    fun incSperreBeiNamen() {
        assertFalse(Gleichwerte.namenGleich("Orion Group Holdings, Inc.", "Orion Oyj"))
        assertTrue(Gleichwerte.incSperre("Orion Group Holdings, Inc.", "Orion Oyj"))
        assertFalse(Gleichwerte.incSperre("Alphabet Inc.", "Alphabet (A)"))
        assertFalse(Gleichwerte.incSperre("Nomura Holdings, Inc.", "Nomura Holdings"))
        assertTrue(Gleichwerte.namenGleich("Grizzly Discoveries Inc.", "GRIZZLY DISCOVER.INC."))
        // Namen im Nasdaq-Stil mit Gattungszusatz bleiben dieselbe Firma
        assertTrue(Gleichwerte.namenGleich("Apple Inc.", "Apple Inc. Common Stock"))
        assertFalse(Gleichwerte.incSperre("Marathon Digital Holdings, Inc.", "Marathon Digital Holdings, Inc. Common Stock"))
        assertEquals("Apple", Firmenname.bereinigt("Apple Inc. Common Stock"))
    }
}
