package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Toleranter Seitenleser fuer Selbstabrufe mit gekuerzten echten Seiten (06./07.11.2026).
class SeitenLeserTest {
    @Test
    fun tabelleMitBoersenwertSpalte() {
        val e = SeitenLeser.lesen("""<table><tr> <td class="table-sub-headers" align="center"> Code </td> <td class="table-sub-headers" align="center"> Description </td> <td class="table-sub-headers" align="center"> Listing Date </td> <td class="table-sub-headers" align="center"> No of Shares </td> <td class="table-sub-headers" align="center"> Last Quote </td> <td class="table-sub-headers" align="center"> Rate </td> <td class="table-sub-headers" align="center"> Capitalization-EUR (€) </td> </tr><tr> <td scope="row"> <strong> BOCH</strong> </td> <td align="left"> Bank of Cyprus Holdings Plc </td> <td align="center"> 19/1/2017 </td> <td align="right"> 435962305 </td> <td align="right"> 10.480(EUR) </td> <td align="right"> 1.00000 </td> <td align="right"> 4,568,884,956.40 </td> </tr><tr> <td scope="row"> <strong> DEM</strong> </td> <td align="left"> Demetra Holdings Plc </td> <td align="center"> 27/04/2000 </td> <td align="right"> 200000000 </td> <td align="right"> 1.310(EUR) </td> <td align="right"> 1.00000 </td> <td align="right"> 262,000,000.00 </td> </tr></table>""", "CY", "EUR", "SELBST")
        assertEquals(2, e.firmen.size)
        assertEquals("Bank of Cyprus Holdings", e.firmen[0].name)
        assertEquals(4568884956.40, e.firmen[0].wert, 1.0)
        assertEquals("EUR", e.firmen[0].waehrung)
        assertEquals("SELBST:BOCH", e.firmen[0].symbol)
        assertEquals(262000000.0, e.firmen[1].wert, 1.0)
    }

    @Test
    fun tabelleMitIssuerUndKapitalisierung() {
        val e = SeitenLeser.lesen("""<table class="market_securities_table" id="equity_table"> <thead> <tr class="ms_banner ms_banner--main"> <th colspan="9"> <div class="ms_banner_inner"> <span class="ms_banner_title" style="text-transform: uppercase;" data-text="Main market">Main market</span> </div> </th> </tr> <tr class="ms_cols"> <th>ISIN code</th> <th>Issuer name</th> <th>Listing Date</th> <th>Last Traded Price</th> <th>Last Trade Date</th> <th class="stock_market_mayker">Underwriter</th> <th>Quantity</th> <th data-threferencename="Reference price" data-thnominalname="Nominal value" class="ms_ref shown_nominal_title"> <div class="ms_ref_inner"> <span class="ms_ref_name">Reference price</span> <div class="th_icon" data-tippy-content="(Reference price) - Weighted Average Price"></div> </div> </th> <th class="hidden_nominal_title">Nominal value</th> <th>Capitalization</th> </tr> </thead><tbody><tr class="issuer main"> <td>AZ1012006628</td> <td> <div class="issuer_holder"> <a href="https://www.bfb.az/en/issuer/pasha-bank-open-joint-stock-company">PASHA BANK</a> </div> </td> <td>19.06.2026</td> <td>52.0000</td> <td>06.11.2026</td> <td class="stock_market_mayker">PAŞA Kapital</td> <td>18 658 516</td> <td class="reference_price shown_reference_td">52.19</td> <td class="nominal hidden_nominal_td">20</td> <td>973 787 950.04</td> </tr><tr class="issuer main"> <td>AZ1004004581</td> <td> <div class="issuer_holder"> <a href="https://www.bfb.az/en/issuer/azerbaycan-beynelxalq-banki-open-joint-stock-company">ABB</a> </div> </td> <td>11.06.2024</td> <td>0.3500</td> <td>06.11.2026</td> <td class="stock_market_mayker">ABB İnvest</td> <td>4 745 449 602</td> <td class="reference_price shown_reference_td">0.34</td> <td class="nominal hidden_nominal_td">0.27</td> <td>1 613 452 864.68</td> </tr></tbody></table>""", "AZ", "AZN", "SELBST")
        assertEquals(2, e.firmen.size)
        assertTrue(e.firmen.all { it.wert > 1e6 && it.waehrung == "AZN" && it.land == "AZ" })
        assertEquals("PASHA BANK", e.firmen[0].name)
        assertEquals(973787950.04, e.firmen[0].wert, 1.0)
    }

    @Test
    fun gesamtwertAusSeitentext() {
        val r = SeitenLeser.lesen("""<table><tr><td>Equity turnover</td><td>2,667,000</td></tr></table><div>xt text-success">(0.11) Market capitalization (FRW) 6,849,561,071,247 <div cla</div>""", "RW", "RWF", "SELBST")
        assertTrue(r.firmen.isEmpty())
        assertEquals(6849561071247.0, r.gesamt!!, 1.0)
        assertEquals("RWF", r.waehrung)
        val g = SeitenLeser.lesen("""<div>Total Stock Market Capitalization 478,389,254.30 ₾ 31/10/2026 HISTORY --> Links News About</div>""", "GE", "GEL", "SELBST")
        assertEquals(478389254.30, g.gesamt!!, 1.0)
        assertEquals("GEL", g.waehrung)
    }

    @Test
    fun seitenOhneWerteLiefernNichts() {
        // Zeitreihe ohne Firmennamen (Ghana) und Tabelle mit gehandeltem Betrag statt Boersenwert (Tunis)
        val gh = SeitenLeser.lesen("""<table><tr> <th class=" wdtheader sort numdata integer " style="">wdt_ID</th> <th data-class="expand" class=" wdtheader sort " style="">Day</th> <th class=" wdtheader sort " style="">Date</th> <th class=" wdtheader sort numdata float " style="">Volume</th> <th class=" wdtheader sort numdata float " style="">GSE Composite Index(GSE-CI)</th> <th class=" wdtheader sort numdata float " style="">Market Capitalization GH¢ million</th> <th class=" wdtheader sort numdata float " style="">Financial Stock Index</th> </tr><tr id="table_47_row_0"> <td style="">1</td> <td style="">Wed</td> <td style="">30/08/2023</td> <td style="">129,663.00</td> <td style="">3,099.49</td> <td style="">73,410.00</td> <td style="">1,745.04</td> </tr><tr id="table_47_row_1"> <td style="">2</td> <td style="">Tue</td> <td style="">29/08/2023</td> <td style="">111,315.00</td> <td style="">3,074.26</td> <td style="">73,135.60</td> <td style="">1,697.42</td> </tr></table>""", "GH", "GHS", "SELBST")
        assertTrue(gh.firmen.isEmpty())
        val tn = SeitenLeser.lesen("""<table><tr><th class="l hp-kpi-col-0">VALEUR</th><th class="r hp-kpi-col-1">DERNIER</th><th class="c hp-kpi-col-2"></th><th class="r hp-kpi-col-3">VARIATION</th><th class="r hp-kpi-col-4">CAPITAUX</th></tr></table>""", "TN", "TND", "SELBST")
        assertTrue(tn.firmen.isEmpty())
        assertNull(tn.gesamt)
        assertTrue(SeitenLeser.lesen("<html></html>", "XX", "USD", "S").firmen.isEmpty())
    }

    @Test
    fun zahlenFormate() {
        assertEquals(478389254.30, SeitenLeser.zahl("478,389,254.30")!!, 0.001)
        assertEquals(5410.01, SeitenLeser.zahl("5 410,01")!!, 0.001)
        assertEquals(27240.0, SeitenLeser.zahl("27,240")!!, 0.001)
        assertEquals(6.81, SeitenLeser.zahl("6.81")!!, 0.001)
        assertEquals(1234567.0, SeitenLeser.zahl("1.234.567")!!, 0.001)
        assertNull(SeitenLeser.zahl("-"))
    }

    @Test
    fun speicherRundlauf() {
        val e = SeitenLeser.Ergebnis(listOf(Boersenfeeds.Notierung("A", "SELBST:A", 5.0e9, "EUR", "SK")), 7.0e9, "EUR")
        val (zeit, l) = SeitenLeser.laden(SeitenLeser.speichern(123L, e))!!
        assertEquals(123L, zeit)
        assertEquals(1, l.firmen.size)
        assertEquals(7.0e9, l.gesamt!!, 1.0)
        assertNotNull(SeitenLeser.laden("1;0;EUR\n"))
        assertNull(SeitenLeser.laden("kaputt"))
    }
}
