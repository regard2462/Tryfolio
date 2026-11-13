package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.76 (q76f): Long/Short-Verteilung aus dem Optionsumsatz der Euronext fuer Aktien ohne US-Optionen,
// etwa Umicore. Feste Beispieldaten im Aufbau der Euronext-Antworten.
class Fassung076Q76fTest {

    private fun tabelle(art: String, zeilen: List<Triple<String, String, String>>, kopf: String = "Day Vol"): String {
        val th = listOf("", "Strike.", "Bid", "Ask", "Last", "Vol", "Time", kopf, "O.I.", "Settl.").joinToString("") { "<th>$it</th>" }
        val tr = zeilen.joinToString("") { (strike, tag, oi) ->
            "<tr><td></td><td>$strike</td><td>1.0</td><td>1.2</td><td>-</td><td>-</td><td>15:50</td><td>$tag</td><td>$oi</td><td>1.1</td></tr>"
        }
        return "<h2>${art.replaceFirstChar { it.uppercase() }}</h2><div><table class=\"${art}_table table table__group\"><thead><tr>$th</tr></thead><tbody>$tr</tbody></table></div>"
    }

    private val einfach = "<table id=\"prices_tables_0\" class=\"delayed\"><tr><th>Strike</th></tr><tr><td>7</td></tr></table>"

    @Test fun klasseUmicoreAusDerIsinSuche() {
        val antwort = """[{"mic":"XBRU","link":"\/en\/product\/equities\/BE0974320526-XBRU","name":"UMICORE"},
            {"mic":"DBRU","link":"\/en\/product\/stock-options\/UMC-DBRU","name":"Umicore"}]"""
        assertEquals(EuronextOptionen.Klasse("UMC", "DBRU"), EuronextOptionen.klasse(antwort))
    }

    @Test fun klasseNimmtNormaleSerieVorWochenUndMini() {
        val antwort = """[{"link":"/en/product/stock-options/AS9-DAMS","name":"ASML Holding NV - European Stock Option"},
            {"link":"/en/product/stock-options/1ASM-DAMS","name":"ASML Holding NV - Mini Weekly Option (10) 1st Friday"},
            {"link":"/en/product/stock-options/ASL-DAMS","name":"ASML Holding NV"}]"""
        assertEquals(EuronextOptionen.Klasse("ASL", "DAMS"), EuronextOptionen.klasse(antwort))
    }

    @Test fun klasseFallbackAufErsteSonderserie() {
        val antwort = """[{"link":"/en/product/stock-options/AI4-DPAR","name":"Air Liquide - European Stock Option (100)"}]"""
        assertEquals(EuronextOptionen.Klasse("AI4", "DPAR"), EuronextOptionen.klasse(antwort))
    }

    @Test fun keineAktienoptionenOderUnlesbar() {
        assertNull(EuronextOptionen.klasse("""[{"link":"/en/product/equities/DE0007164600-BGEM","name":"SAP"}]"""))
        assertNull(EuronextOptionen.klasse("[]"))
        assertNull(EuronextOptionen.klasse("<html>gesperrt</html>"))
    }

    @Test fun faelligkeitenLaufenUeberDenJahreswechsel() {
        val f = EuronextOptionen.formular(2026, 11, 6)
        assertEquals(listOf("01-11-2026", "01-12-2026", "01-01-2027", "01-02-2027", "01-03-2027", "01-04-2027"), f.filter { it.first == "md[]" }.map { it.second })
        assertEquals("extended", f.last().second)
    }

    @Test fun summenZaehltCallsUndPutsGetrennt() {
        val html = einfach +
            tabelle("calls", listOf(Triple("7.00", "-", "-"), Triple("8.00", "12", "30"), Triple("9.00", "1,015", "4"))) +
            tabelle("puts", listOf(Triple("7.00", "3", "0.00"), Triple("8.00", "-", "0.31"))) +
            tabelle("calls", listOf(Triple("7.00", "5", "-"))) +
            tabelle("puts", listOf(Triple("7.00", "20", "0.12")))
        // Offene Kontrakte der Puts (Abrechnungskurse in der Quelle) zaehlen nicht
        assertEquals(1032L to 23L, EuronextOptionen.summen(html))
    }

    @Test fun summenOhneTabelleOderOhneSpalteIstNull() {
        assertNull(EuronextOptionen.summen(einfach))
        assertNull(EuronextOptionen.summen(tabelle("calls", listOf(Triple("7.00", "4", "1")), kopf = "Volumen")))
        assertNull(EuronextOptionen.summen(""))
    }

    @Test fun longAnteilAusDenSummen() {
        val s = EuronextOptionen.summen(
            tabelle("calls", listOf(Triple("7.00", "27", "-"))) + tabelle("puts", listOf(Triple("7.00", "16", "-"))),
        )!!
        assertTrue(s.first + s.second >= EuronextOptionen.MINDESTUMSATZ)
        val v = MarketApi.OptionVolumes(s.first, s.second, 0L, MarketApi.Herkunft.EURONEXT)
        assertEquals(27 * 100.0 / 43, v.longPct, 1e-9)
        assertEquals(MarketApi.Herkunft.EURONEXT, v.herkunft)
    }

    @Test fun zuWenigUmsatzLiegtUnterDerSchwelle() {
        val s = EuronextOptionen.summen(
            tabelle("calls", listOf(Triple("7.00", "3", "-"))) + tabelle("puts", listOf(Triple("7.00", "2", "-"))),
        )!!
        assertTrue(s.first + s.second < EuronextOptionen.MINDESTUMSATZ)
    }

    private val hkex = """"STOCK OPTIONS DAILY MARKET REPORT AS AT 10 NOV 2026"


,,,"SUMMARY TRADING VOLUME",,,"SUMMARY OPEN INTEREST"
"HKATS CODE","UNDERLYING STOCK",,"TOTAL","CALLS","PUTS","TOTAL","CALLS","PUTS","IV%*"
A50,X ISHARES A50,(02823),20,12,8,7330,3229,4101,20
ALB,BABA,(09988),55686,25347,30339,1914262,886855,1027407,51
TCH,TENCENT HOLDINGS LTD,(00700),106948,70399,36549,2163494,1208421,955073,31

"CLASS TCH - TENCENT HOLDINGS LTD",,,"CLOSING PRICE HK${'$'} 417.800"
C,TCH,30MAR27,500.00,13104,9.62,31,10.21,9.62,34095,-0.41,-4.09"""

    @Test fun hkexCodeAusYahooKuerzel() {
        assertEquals("00700", HkexOptionen.code("0700.HK"))
        assertEquals("09988", HkexOptionen.code("9988.HK"))
        assertEquals("00005", HkexOptionen.code("0005.HK"))
        assertEquals("01810", HkexOptionen.code("1810.hk"))
        assertNull(HkexOptionen.code("7203.T"))
        assertNull(HkexOptionen.code("AAPL"))
        assertNull(HkexOptionen.code("1234567.HK"))
    }

    @Test fun hkexAdresseMitZweistelligemJahr() {
        assertEquals("https://www.hkex.com.hk/eng/stat/dmstat/dayrpt/dqe261110.csv", HkexOptionen.url(2026, 11, 10))
        assertEquals("https://www.hkex.com.hk/eng/stat/dmstat/dayrpt/dqe270105.csv", HkexOptionen.url(2027, 1, 5))
    }

    @Test fun hkexSummeAusDemBerichtskopf() {
        assertEquals(70399L to 36549L, HkexOptionen.lesen(hkex, "00700"))
        assertEquals(25347L to 30339L, HkexOptionen.lesen(hkex, "09988"))
        val v = MarketApi.OptionVolumes(70399, 36549, 0L, MarketApi.Herkunft.HKEX)
        assertEquals(70399 * 100.0 / 106948, v.longPct, 1e-9)
    }

    @Test fun hkexBasiswertOhneOptionenOderFremderText() {
        assertNull(HkexOptionen.lesen(hkex, "00941"))
        assertNull(HkexOptionen.lesen("<html>404</html>", "00700"))
    }

    // Der Zwischenspeicher kennt ohne Abruf nichts: Werte mit nicht-US-ISIN warten, Indizes sind sofort bekannt.
    @Test fun zwischenspeicherOhneEintrag() {
        val repo = MarketRepository(MarketApi(), CoroutineScope(Job()))
        assertNull(repo.optionVolumesCached("UMI.BR", "BE0974320526"))
        assertEquals(Pair(true, null), repo.optionVolumesCached("^GDAXI", "BE0974320526"))
        assertNotNull(EuronextOptionen.suchUrl("BE0974320526"))
        assertTrue(EuronextOptionen.suchUrl("BE0974320526").endsWith("q=BE0974320526"))
    }
}
