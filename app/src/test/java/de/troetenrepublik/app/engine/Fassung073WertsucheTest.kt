package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Instrument
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.engine.Wertsuche.Art
import de.troetenrepublik.app.engine.Wertsuche.Herkunft
import org.junit.Assert.*
import org.junit.Test

// Wertpapiersuche (0.73): ISIN und WKN erkennen, Pruefziffer, Rangfolge der Vorschlaege, Plaetze, veraltete Antworten
class Fassung073WertsucheTest {

    // Auszuege echter Antworten der Yahoo-Suche (q=IE00BK5BQT80 und q=SAP SE) und von OpenFIGI (ID_ISIN DE0007164600)
    private val yahooIsin = """{"quotes": [{"exchange": "LSE", "shortname": "VANGUARD FUNDS PLC VANGUARD FTS", "quoteType": "ETF", "symbol": "VWRA.L", "exchDisp": "London", "longname": "Vanguard FTSE All-World UCITS ETF USD Accumulation"}, {"exchange": "STU", "shortname": "Vanguard FTSE All-World UCITS E", "quoteType": "MUTUALFUND", "symbol": "IE00BK5BQT80.SG", "exchDisp": "Stuttgart"}]}"""
    private val yahooSap = """{"quotes": [{"shortname": "SAP  SE", "quoteType": "EQUITY", "symbol": "SAP", "exchDisp": "NYSE", "longname": "SAP SE"}, {"shortname": "SAP SE                        I", "quoteType": "EQUITY", "symbol": "SAP.DE", "exchDisp": "XETRA", "longname": "SAP SE"}, {"shortname": "SAP SE                        I", "quoteType": "EQUITY", "symbol": "SAP.F", "exchDisp": "Frankfurt", "longname": "SAP SE"}, {"shortname": "SAP SE", "quoteType": "EQUITY", "symbol": "SAPGF", "exchDisp": "OTC Markets", "longname": "SAP SE"}, {"shortname": "SAP SE", "quoteType": "OPTION", "symbol": "SAP261204C00195000", "exchDisp": "OPR"}, {"shortname": "SAP SE", "quoteType": "ETF", "symbol": "SAPH", "exchDisp": "NYSEArca", "longname": "SAP SE ADRhedged"}]}"""
    private val figiSap = """[{"data":[{"figi":"BBG000BDTGP4","name":"SAP SE","ticker":"SAP","exchCode":"SW","compositeFIGI":"BBG000BDTGP4","securityType":"Common Stock","marketSector":"Equity"},{"figi":"BBG000BG7DY8","name":"SAP SE","ticker":"SAP","exchCode":"GR","compositeFIGI":"BBG000BG7DY8","securityType":"Common Stock","marketSector":"Equity"},{"figi":"BBG000BG7FG3","name":"SAP SE","ticker":"SAP","exchCode":"GF","securityType":"Common Stock","marketSector":"Equity"},{"figi":"BBG000X","name":"SAP SE","ticker":"SAPGF","exchCode":"US","securityType":"Common Stock","marketSector":"Equity"},{"figi":"BBG000Y","name":"SAP SE","ticker":"SAPNOK","exchCode":"EO","securityType":"Common Stock","marketSector":"Equity"}]}]"""

    // ------------------------------------------------------------ Erkennung

    @Test
    fun pruefzifferEchterIsin() {
        listOf(
            "DE0007164600", "US0378331005", "US67066G1040", "GB0002634946", "CH0012005267", "JP3633400001",
            "US86800U3023", "IE00B4L5Y983", "KYG875721634", "DE000A0S9GB0", "DE000BASF111",
        ).forEach { assertTrue(it, Wertsuche.pruefzifferOk(it)) }
        listOf("DE0007164601", "US0378331006", "DE000A0S9GB1", "DE000716460", "DE00071646000", "1E0007164600", "DE000716460A")
            .forEach { assertFalse(it, Wertsuche.pruefzifferOk(it)) }
    }

    @Test
    fun vollstaendigeIsinMitLeerzeichenKleinbuchstabenUndUmbruch() {
        for (roh in listOf("DE0007164600", "de0007164600", " DE0007164600 ", "DE0007164600\n", "DE00 0716 4600", "DE 0007164600\r\n", "de-0007164600")) {
            val e = Wertsuche.auswerten(roh)
            assertEquals(roh, Art.ISIN, e.art)
            assertEquals("DE0007164600", e.kennung)
        }
        assertEquals("DE 0007164600", Wertsuche.auswerten("DE   0007164600\n").begriff)
    }

    @Test
    fun falschePruefzifferTeilEingabeUndWkn() {
        assertEquals(Art.ISIN_PRUEFZIFFER, Wertsuche.auswerten("DE0007164601").art)
        assertEquals(Art.ISIN_PRUEFZIFFER, Wertsuche.auswerten("US86800U1044").art)
        assertEquals(Art.ISIN_TEIL, Wertsuche.auswerten("DE000716").art)
        assertEquals(Art.ISIN_TEIL, Wertsuche.auswerten("de0007164").art)
        assertEquals(Art.ISIN_TEIL, Wertsuche.auswerten("US86800U10").art)
        assertEquals(Art.WKN, Wertsuche.auswerten("716460").art)
        assertEquals(Art.WKN, Wertsuche.auswerten("a1ewww").art)
        assertEquals("A1EWWW", Wertsuche.auswerten("a1ewww").kennung)
    }

    @Test
    fun namenUndKuerzelSindKeineKennung() {
        listOf("sm", "SMCI", "SAP.DE", "Apple", "Deutsche Telekom", "Münchener Rück", "BTC-EUR", "Microsoft", "iShares Core", "Ab 12", "", "  ", "VOW3.DE", "Berkshire Hathaway")
            .forEach { assertEquals(it, Art.TEXT, Wertsuche.auswerten(it).art) }
    }

    // ------------------------------------------------------------ Falten und Stufen

    @Test
    fun umlauteUndEszettGegenAeOeUeSs() {
        assertEquals("muenchener rueck", Wertsuche.faltenAe("Münchener Rück"))
        assertEquals("strasse", Wertsuche.faltenAe("Straße"))
        assertEquals("munchener ruck", Wertsuche.faltenOhne("Münchener Rück"))
        for (frage in listOf("Münchener", "Muenchener", "munchener", "MÜNCHENER RÜCK", "Muenchener Rueck")) {
            assertNotNull(frage, Wertsuche.stufe(frage, "MUV2.DE", "Münchener Rück"))
        }
        for (frage in listOf("Münchener", "Muenchener", "MÜNCHENER RÜCK", "Muenchener Rueck")) {
            assertNotNull(frage, Wertsuche.stufe(frage, "MUV2.DE", "MUENCHENER RUECKVERS.-GES. AG N"))
        }
        assertNotNull(Wertsuche.stufe("Boerse", "DB1.DE", "Deutsche Börse"))
        assertNotNull(Wertsuche.stufe("Börse", "DB1.DE", "Deutsche Boerse AG"))
        assertNotNull(Wertsuche.stufe("Strasse", "X", "Große Straße"))
    }

    @Test
    fun netzbegriffFaltetUmlauteUndKuerzelVariante() {
        assertEquals("deutsche boerse", Wertsuche.netzBegriff("Deutsche Börse"))
        assertEquals("muenchener rueck", Wertsuche.netzBegriff("  Münchener Rück "))
        assertEquals("sap.de", Wertsuche.netzBegriff("sap.de"))
        assertEquals("super micro", Wertsuche.netzBegriff("super micro"))
        assertEquals("bitcoin", Wertsuche.netzBegriff("Bitcoin"))
        assertEquals("SAP.DE", Wertsuche.kuerzelVariante("sap de"))
        assertEquals("SAP.DE", Wertsuche.kuerzelVariante("SAP DE "))
        assertEquals("SAP.F", Wertsuche.kuerzelVariante("sap f"))
        assertNull(Wertsuche.kuerzelVariante("sap.de"))
        assertNull(Wertsuche.kuerzelVariante("super micro"))
        assertNull(Wertsuche.kuerzelVariante("sap"))
    }

    @Test
    fun stufenPraefixWortanfangEnthalten() {
        assertEquals(0, Wertsuche.stufe("sm", "SMCI", "Super Micro Computer"))
        assertEquals(0, Wertsuche.stufe("sup", "SMCI", "Super Micro Computer"))
        assertEquals(1, Wertsuche.stufe("micro", "SMCI", "Super Micro Computer"))
        assertEquals(2, Wertsuche.stufe("icro", "SMCI", "Super Micro Computer"))
        assertNull(Wertsuche.stufe("icro", "SMCI", "Super Micro Computer", mitEnthalten = false))
        assertNull(Wertsuche.stufe("xyz", "SMCI", "Super Micro Computer"))
        assertEquals(0, Wertsuche.stufe("sap.de", "SAP.DE", "SAP"))
        assertEquals(0, Wertsuche.stufe("sap de", "SAP.DE", "SAP"))
        assertEquals(0, Wertsuche.stufe("super mi", "SMCI", "Super Micro Computer"))
        // Ein Zeichen enthaelt keine Zufallstreffer mitten im Wort
        assertNull(Wertsuche.stufe("m", "SAP.DE", "SAP"))
    }

    // ------------------------------------------------------------ Rangfolge

    private val katalogListe = listOf(
        Instrument("SMR", "NuScale Power", AssetType.STOCK, "USD"),
        Instrument("SMCI", "Super Micro Computer", AssetType.STOCK, "USD"),
        Instrument("SN.L", "Smith & Nephew", AssetType.STOCK, "GBP"),
        Instrument("S92.DE", "SMA Solar", AssetType.STOCK, "EUR"),
        Instrument("MSMX", "Alpha Smart", AssetType.STOCK, "USD"),
        Instrument("SAP.DE", "SAP", AssetType.STOCK, "EUR", "DE0007164600"),
        Instrument("EUNL.DE", "iShares Core MSCI World", AssetType.ETF, "EUR", "IE00B4L5Y983"),
    )

    @Test
    fun verlaufErscheintSchonBeimErstenZeichenVorDemKatalog() {
        val verlauf = listOf(
            Suchverlauf.Eintrag("SMCI", "Super Micro Computer", 3, 1000),
            Suchverlauf.Eintrag("AAPL", "Apple", 1, 2000),
        )
        val eigene = Wertsuche.eigene("s", verlauf, emptyList(), emptyList())
        assertEquals(listOf("SMCI"), eigene.map { it.symbol })
        assertEquals(Herkunft.VERLAUF, eigene[0].herkunft)
        val eigene2 = Wertsuche.eigene("sm", verlauf, emptyList(), emptyList())
        assertEquals(listOf("SMCI"), eigene2.map { it.symbol })
        assertTrue(Wertsuche.eigene("", verlauf, emptyList(), emptyList()).isEmpty())
        assertTrue(Wertsuche.eigene("  ", verlauf, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun eigeneNachStufeDannHerkunftUndAktualitaet() {
        val verlauf = listOf(
            Suchverlauf.Eintrag("SMR", "NuScale Power", 1, 100),
            Suchverlauf.Eintrag("SMCI", "Super Micro Computer", 1, 300),
            Suchverlauf.Eintrag("SM", "SM Energy", 1, 200),
            Suchverlauf.Eintrag("ASML.AS", "ASML", 1, 900),
            Suchverlauf.Eintrag("XYZ", "Der Smarte Zweite", 1, 800),
        )
        val depot = listOf("SMTC" to "Semtech")
        val watch = listOf("SMH" to "VanEck Semiconductor ETF", "ZZZ" to "Super Micro Daneben")
        val e = Wertsuche.eigene("sm", verlauf, depot, watch)
        // Praefix vor Wortanfang; im Praefix Verlauf (juengster zuerst), Depot, Watchlist
        assertEquals(listOf("SMCI", "SM", "SMR", "SMTC", "SMH", "XYZ"), e.map { it.symbol })
        assertEquals(listOf(0, 0, 0, 0, 0, 1), e.map { it.stufe })
    }

    @Test
    fun eigeneWerteOhneDoppelteUndBegrenzt() {
        val verlauf = listOf(Suchverlauf.Eintrag("SMCI", "Super Micro Computer", 1, 5))
        val e = Wertsuche.eigene("smci", verlauf, listOf("SMCI" to "Super Micro Computer"), listOf("SMCI" to "Super Micro Computer"))
        assertEquals(1, e.size)
        assertEquals(Herkunft.VERLAUF, e[0].herkunft)
        val viele = (1..30).map { Suchverlauf.Eintrag("SM$it", "Wert $it", 1, it.toLong()) }
        val begrenzt = Wertsuche.eigene("sm", viele, emptyList(), emptyList(), max = 8)
        assertEquals(8, begrenzt.size)
        assertEquals("SM30", begrenzt[0].symbol)
    }

    @Test
    fun katalogPraefixVorWortanfangVorEnthalten() {
        val e = Wertsuche.auswerten("sm")
        val k = Wertsuche.katalog(e, katalogListe).map { it.symbol }
        // Praefix von Kuerzel oder Name (SMR, SMCI, S92.DE ueber „SMA Solar“, SN.L ueber „Smith“), dann Wortanfang (MSMX)
        assertEquals(setOf("SMR", "SMCI", "S92.DE", "SN.L"), k.take(4).toSet())
        assertEquals("MSMX", k[4])
        assertEquals(5, k.size)
        // Kuerzel-Praefix vor Namens-Praefix
        assertTrue(k.indexOf("SMCI") < k.indexOf("S92.DE"))
        assertTrue(k.indexOf("SMR") < k.indexOf("SN.L"))
    }

    @Test
    fun katalogGenauesKuerzelStehtVorangeStellt() {
        val liste = listOf(Instrument("SAPX", "Alpha", AssetType.STOCK), Instrument("SAP.DE", "SAP", AssetType.STOCK), Instrument("SAP", "SAP ADR", AssetType.STOCK))
        assertEquals("SAP", Wertsuche.katalog(Wertsuche.auswerten("sap"), liste).first().symbol)
        assertEquals("SAP.DE", Wertsuche.katalog(Wertsuche.auswerten("SAP.DE"), liste).first().symbol)
        assertEquals("SAP.DE", Wertsuche.katalog(Wertsuche.auswerten("sap de"), liste).first().symbol)
    }

    @Test
    fun katalogFindetIsinGanzUndTeilweise() {
        for (roh in listOf("DE0007164600", "de00 0716 4600", "DE000716", "DE0007164")) {
            val k = Wertsuche.katalog(Wertsuche.auswerten(roh), katalogListe)
            assertEquals(roh, listOf("SAP.DE"), k.map { it.symbol })
        }
        assertEquals(listOf("EUNL.DE"), Wertsuche.katalog(Wertsuche.auswerten("IE00B4L5Y983"), katalogListe).map { it.symbol })
        assertTrue(Wertsuche.katalog(Wertsuche.auswerten("US0378331005"), katalogListe).isEmpty())
    }

    @Test
    fun gesamtreihenfolgeVerlaufVorKatalogVorNetz() {
        val verlauf = listOf(Suchverlauf.Eintrag("SMCI", "Super Micro Computer", 1, 5))
        val eigene = Wertsuche.eigene("sm", verlauf, emptyList(), emptyList())
        val katalog = Wertsuche.katalog(Wertsuche.auswerten("sm"), katalogListe)
            .map { Wertsuche.Vorschlag(it.symbol, it.name, Herkunft.KATALOG, 0) }
        val netz = MarketApi.suchtreffer(yahooSap).map { Wertsuche.Vorschlag(it.symbol, it.name, Herkunft.NETZ, 0) } +
            Wertsuche.Vorschlag("SMR", "NuScale Power", Herkunft.NETZ, 0)
        val alle = Wertsuche.mischen(eigene, katalog, netz)
        assertEquals("SMCI", alle[0].symbol)
        assertEquals(Herkunft.VERLAUF, alle[0].herkunft)
        assertEquals(1, alle.count { it.symbol == "SMCI" })
        assertEquals(1, alle.count { it.symbol == "SMR" })
        assertEquals(Herkunft.KATALOG, alle.first { it.symbol == "SMR" }.herkunft)
        val ersteNetz = alle.indexOfFirst { it.herkunft == Herkunft.NETZ }
        assertTrue(alle.drop(ersteNetz).none { it.herkunft != Herkunft.NETZ })
        assertTrue(alle.take(ersteNetz).all { it.herkunft != Herkunft.NETZ })
    }

    // ------------------------------------------------------------ Plaetze

    @Test
    fun yahooAntwortenWerdenGelesen() {
        val sap = MarketApi.suchtreffer(yahooSap)
        // Option fällt heraus
        assertEquals(listOf("SAP", "SAP.DE", "SAP.F", "SAPGF", "SAPH"), sap.map { it.symbol })
        assertEquals("SAP SE", sap[1].name)
        assertEquals("XETRA", sap[1].exchange)
        val isin = MarketApi.suchtreffer(yahooIsin)
        assertEquals(listOf("VWRA.L", "IE00BK5BQT80.SG"), isin.map { it.symbol })
        assertTrue(MarketApi.suchtreffer("kein json").isEmpty())
        assertTrue(MarketApi.suchtreffer("""{"finance":{"error":"x"}}""").isEmpty())
    }

    @Test
    fun plaetzeHeimatboerseDannHauptboerseDannUsDannRest() {
        val treffer = MarketApi.suchtreffer(yahooSap)
        // Deutsche Nutzer: Xetra vorn, dann Hauptboerse des Landes (hier ebenfalls .DE), dann US, Rest in Quellenfolge
        assertEquals(listOf("SAP.DE", "SAP", "SAPGF", "SAPH", "SAP.F"), Wertsuche.plaetzeOrdnen(treffer, "DE0007164600", "").map { it.symbol })
        // Nutzer in der Schweiz: SIX gibt es hier nicht; Hauptboerse des ISIN-Landes (Xetra) bleibt vorn
        assertEquals("SAP.DE", Wertsuche.plaetzeOrdnen(treffer, "DE0007164600", "CH").first().symbol)
        // US-Nutzer mit deutscher ISIN: US-Notierung vor Xetra, wenn die Heimat gilt
        assertEquals("SAP", Wertsuche.plaetzeOrdnen(treffer, "DE0007164600", "US").first().symbol)
        // Fonds mit der ISIN als Kuerzel ganz hinten
        val fonds = MarketApi.suchtreffer(yahooIsin).reversed()
        assertEquals(listOf("VWRA.L", "IE00BK5BQT80.SG"), Wertsuche.plaetzeOrdnen(fonds, "IE00BK5BQT80", "").map { it.symbol })
    }

    @Test
    fun zusatzUndHeimat() {
        assertEquals(".DE", Wertsuche.zusatz("SAP.DE"))
        assertEquals(".L", Wertsuche.zusatz("BT-A.L"))
        assertEquals("", Wertsuche.zusatz("AAPL"))
        assertEquals("", Wertsuche.zusatz("BRK-B"))
        assertEquals("?", Wertsuche.zusatz("^GDAXI"))
        assertEquals(".DE", Wertsuche.heimatZusatz(""))
        assertEquals(".SW", Wertsuche.heimatZusatz("ch"))
        assertEquals("", Wertsuche.heimatZusatz("US"))
        assertNull(Wertsuche.heimatZusatz("ZZ"))
    }

    // ------------------------------------------------------------ OpenFIGI

    @Test
    fun figiAntwortUndKandidaten() {
        val e = WertsucheFigi.lesen(figiSap)
        assertEquals(5, e.size)
        assertEquals("SAP", e[1].ticker)
        assertEquals("GR", e[1].boerse)
        // Heimat des Nutzers (Deutschland) zuerst, dann US, dann Schweiz und Frankfurt; EO (Fremdwaehrung) fehlt
        assertEquals(listOf("SAP.DE", "SAPGF", "SAP.SW", "SAP.F"), WertsucheFigi.kandidaten(e, "DE0007164600", ""))
        assertEquals("SAPGF", WertsucheFigi.kandidaten(e, "DE0007164600", "US").first())
        assertEquals(2, WertsucheFigi.kandidaten(e, "DE0007164600", "", max = 2).size)
    }

    @Test
    fun figiOhneTrefferWarnungFehlerUndMuell() {
        assertTrue(WertsucheFigi.lesen("""[{"warning":"No identifier found."}]""").isEmpty())
        assertTrue(WertsucheFigi.lesen("""{"error":"Too many requests"}""").isEmpty())
        assertTrue(WertsucheFigi.lesen("").isEmpty())
        assertTrue(WertsucheFigi.lesen("""[{"data":[{"name":"X","ticker":"A"}]}]""").isEmpty())
    }

    @Test
    fun figiKuerzelFuerHongkongKanadaUndKlassenaktien() {
        assertEquals("0700.HK", WertsucheFigi.yahooKuerzel(WertsucheFigi.Eintrag("700", "HK", "Tencent", "Common Stock")))
        assertEquals("BRK-B", WertsucheFigi.yahooKuerzel(WertsucheFigi.Eintrag("BRK/B", "US", "Berkshire", "Common Stock")))
        assertEquals("BT-A.L", WertsucheFigi.yahooKuerzel(WertsucheFigi.Eintrag("BT/A", "LN", "BT", "Common Stock")))
        assertEquals("7203.T", WertsucheFigi.yahooKuerzel(WertsucheFigi.Eintrag("7203", "JT", "Toyota", "Common Stock")))
        assertNull(WertsucheFigi.yahooKuerzel(WertsucheFigi.Eintrag("SAPNOK", "EO", "SAP", "Common Stock")))
    }

    @Test
    fun figiAnfrageEnthaeltNurDieKennung() {
        assertEquals("""[{"idType":"ID_ISIN","idValue":"DE0007164600"}]""", WertsucheFigi.anfrage(false, "DE0007164600"))
        assertEquals("""[{"idType":"ID_WERTPAPIER","idValue":"716460"}]""", WertsucheFigi.anfrage(true, "716460"))
        assertEquals("""[{"idType":"ID_ISIN","idValue":"DE0007164600"}]""", WertsucheFigi.anfrage(false, "DE00\"}] 07164600"))
    }

    // ------------------------------------------------------------ Entprellung

    @Test
    fun verspaeteteAntwortUeberschreibtNeuereNicht() {
        val folge = Wertsuche.Abruffolge()
        var anzeige = ""
        val a = folge.neu()
        val b = folge.neu()
        // Die Antwort auf „s“ (a) trifft nach der auf „sm“ (b) ein
        if (folge.aktuell(b)) anzeige = "sm"
        if (folge.aktuell(a)) anzeige = "s"
        assertEquals("sm", anzeige)
        assertFalse(folge.aktuell(a))
        assertTrue(folge.aktuell(b))
        val c = folge.neu()
        assertFalse(folge.aktuell(b))
        assertTrue(folge.aktuell(c))
    }
}
