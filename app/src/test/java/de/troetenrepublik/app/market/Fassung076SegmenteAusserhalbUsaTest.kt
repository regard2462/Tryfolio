package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.SecBericht.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

// Zuordnung von Boersenkuerzeln und Namen ausserhalb der USA zur SEC-Firma (Jahresberichte 20-F und 40-F), mit festen Daten.
class Fassung076SegmenteAusserhalbUsaTest {
    private val liste = """{"0":{"cik_str":937966,"ticker":"ASML","title":"ASML HOLDING NV"},
        "1":{"cik_str":1000184,"ticker":"SAP","title":"SAP SE"},
        "2":{"cik_str":1002047,"ticker":"NVO","title":"NOVO NORDISK A/S"},
        "3":{"cik_str":1306965,"ticker":"SHEL","title":"Shell plc"},
        "4":{"cik_str":1,"ticker":"TM","title":"TOYOTA MOTOR CORP/ADR"},
        "5":{"cik_str":1165565,"ticker":"SONY","title":"SONY GROUP CORP"},
        "6":{"cik_str":1046179,"ticker":"TSM","title":"TAIWAN SEMICONDUCTOR MANUFACTURING CO LTD"},
        "7":{"cik_str":1067491,"ticker":"INFY","title":"INFOSYS LTD"},
        "8":{"cik_str":1144967,"ticker":"HDB","title":"HDFC BANK LTD"},
        "9":{"cik_str":313807,"ticker":"BP","title":"BP PLC"},
        "10":{"cik_str":885725,"ticker":"ERIC","title":"ERICSSON LM TELEPHONE CO"},
        "11":{"cik_str":313216,"ticker":"PHG","title":"KONINKLIJKE PHILIPS N V"},
        "12":{"cik_str":1114446,"ticker":"UBS","title":"UBS Group AG"},
        "13":{"cik_str":10,"ticker":"ALV","title":"AUTOLIV INC"},
        "14":{"cik_str":1577552,"ticker":"AI","title":"C3.ai, Inc."},
        "15":{"cik_str":99,"ticker":"SIEGY","title":"SIEMENS AKTIENGESELLSCHAFT"}}"""

    private fun cik(symbol: String, name: String) = SecBericht.cikAusListe(liste, symbol, name)

    @Test(timeout = 20_000)
    fun europaischeKuerzelMitVollemNamen() {
        assertEquals("937966", cik("ASML.AS", "ASML Holding N.V."))
        assertEquals("1000184", cik("SAP.DE", "SAP SE"))
        assertEquals("1002047", cik("NOVO-B.CO", "Novo Nordisk A/S"))
        assertEquals("1306965", cik("SHEL.L", "Shell plc"))
        assertEquals("313807", cik("BP.L", "BP p.l.c."))
        assertEquals("885725", cik("ERIC-B.ST", "Telefonaktiebolaget LM Ericsson (publ)"))
        assertEquals("313216", cik("PHIA.AS", "Koninklijke Philips N.V."))
        assertEquals("1114446", cik("UBSG.SW", "UBS Group AG"))
    }

    @Test(timeout = 20_000)
    fun asiatischeKuerzelMitVollemNamen() {
        assertEquals("1", cik("7203.T", "Toyota Motor Corporation"))
        assertEquals("1165565", cik("6758.T", "Sony Group Corporation"))
        assertEquals("1046179", cik("2330.TW", "Taiwan Semiconductor Manufacturing Company Limited"))
        assertEquals("1067491", cik("INFY.NS", "Infosys Limited"))
        assertEquals("1144967", cik("HDFCBANK.NS", "HDFC Bank Limited"))
    }

    @Test(timeout = 20_000)
    fun gleichesKuerzelEinerAnderenFirmaWirdAbgelehnt() {
        // ALV gehoert bei der SEC Autoliv, AI der C3.ai: Namen ohne gemeinsame Worte
        assertNull(cik("ALV.DE", "Allianz SE"))
        assertNull(cik("AI.PA", "L'Air Liquide S.A."))
        // Firma ohne SEC-Bericht
        assertNull(cik("ROG.SW", "Roche Holding AG"))
        assertNull(cik("MC.PA", "LVMH Moet Hennessy Louis Vuitton SE"))
    }

    @Test(timeout = 20_000)
    fun nameKernEntferntRechtsformenUndPunkte() {
        assertEquals("asml", SecBericht.nameKern("ASML Holding N.V."))
        assertEquals("novo nordisk", SecBericht.nameKern("Novo Nordisk A/S"))
        assertEquals("novo nordisk", SecBericht.nameKern("NOVO NORDISK A/S"))
        assertEquals("toyota motor", SecBericht.nameKern("TOYOTA MOTOR CORP/ADR"))
        assertEquals("lm ericsson", SecBericht.nameKern("Telefonaktiebolaget LM Ericsson (publ)"))
    }

    // ------------------------------------------------------------ Tochter ueber die Konzernmutter

    private class Netz : FirmenAbruf {
        val texte = HashMap<String, String>()
        val abrufe = ArrayList<String>()
        override suspend fun sec(url: String): String? { abrufe += url; return texte[url] }
        override suspend fun secStrom(url: String, lesen: (InputStream) -> Xbrl.Gelesen): Xbrl.Gelesen? { abrufe += url; return null }
        override suspend fun gleif(url: String): String? { abrufe += url; return texte[url] }
    }

    private class Platte : FirmenSpeicher {
        val dateien = HashMap<String, String>()
        override fun lesen(name: String): String? = dateien[name]
        override fun schreiben(name: String, text: String) { dateien[name] = text }
    }

    private val eigen = """{"data":[{"type":"lei-records","id":"529900QBVWXMWANH7H45","attributes":{"lei":"529900QBVWXMWANH7H45","entity":{"legalName":{"name":"Siemens Healthineers AG","language":"de"},"legalAddress":{"country":"DE"}}},
        "relationships":{"direct-parent":{"links":{"lei-record":"https://api.gleif.org/api/v1/lei-records/529900QBVWXMWANH7H45/direct-parent"}},
        "ultimate-parent":{"links":{"lei-record":"https://api.gleif.org/api/v1/lei-records/529900QBVWXMWANH7H45/ultimate-parent"}}}}]}"""
    private val mutter = """{"data":{"type":"lei-records","id":"W38RGI023J3WT1HWRP32","attributes":{"lei":"W38RGI023J3WT1HWRP32","entity":{"legalName":{"name":"Siemens Aktiengesellschaft","language":"de"},"legalAddress":{"country":"DE"}}}}}"""

    private fun dienst(netz: Netz, platte: Platte = Platte()) = Firmenberichte(netz, platte, { 1_000_000_000_000L }, {})

    @Test(timeout = 20_000)
    fun toechterNutzenDenBerichtDerKonzernmutter() = runBlocking {
        val netz = Netz()
        netz.texte[SecFormen.KUERZEL] = liste
        netz.texte[Gleif.leiSuche("DE000SHL1006")] = eigen
        netz.texte["https://api.gleif.org/api/v1/lei-records/529900QBVWXMWANH7H45/ultimate-parent"] = mutter
        netz.texte[SecBericht.submissionsAdresse("99")] = """{"filings":{"recent":{"form":["6-K"],"accessionNumber":["x"],"filingDate":["2026-01-01"],"reportDate":[""],"isXBRL":[0]}}}"""
        val e = dienst(netz).laden("SHL.DE", "Siemens Healthineers AG", true, "DE000SHL1006")
        // Die Firma wurde ueber die Mutter gefunden: ihre Einreichungen wurden abgefragt
        assertEquals(Status.KEIN_BERICHT, e.status)
        assertTrue(netz.abrufe.contains(SecBericht.submissionsAdresse("99")))
    }

    @Test(timeout = 20_000)
    fun ohneIsinBleibtEsBeiKeinSec() = runBlocking {
        val netz = Netz()
        netz.texte[SecFormen.KUERZEL] = liste
        val e = dienst(netz).laden("SHL.DE", "Siemens Healthineers AG", true)
        assertEquals(Status.KEIN_SEC, e.status)
        assertTrue(netz.abrufe.none { it.contains("gleif") })
    }
}
