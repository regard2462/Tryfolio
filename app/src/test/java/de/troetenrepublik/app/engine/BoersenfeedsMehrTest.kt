package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Leser der Boersenseiten aus Zagreb, Ljubljana, Vietnam, Zypern, Trinidad, Baku, Kasachstan und Lima mit gekuerzten echten Antworten (06./07.11.2026).
class BoersenfeedsMehrTest {
    private val bm = BoersenfeedsMehr

    @Test
    fun zagrebStammaktienMalKurs() {
        val aktien = bm.zseAktien("""{"securities":[{"symbol":"HT","isin":"HRHT00RA0005","name":"HT d.d.","listed_quantity":76550000,"segment_delisting_date":null,"security_class":"EQTY","security_type":"ORD-SHARE"},{"symbol":"ZABA","isin":"HRZABARA0009","name":"Zagrebačka banka d.d.","listed_quantity":320241955,"segment_delisting_date":null,"security_class":"EQTY","security_type":"ORD-SHARE"},{"symbol":"CROS2","isin":"HRCROSPA0004","name":"CROATIA osiguranje d.d.","listed_quantity":8750,"segment_delisting_date":null,"security_class":"EQTY","security_type":"PREF-SHARE"},{"symbol":"BSQR","isin":"HRMRULRA0009","name":"BOSQAR d.d.","listed_quantity":1178437,"segment_delisting_date":"2025-09-05","security_class":"EQTY","security_type":"ORD-SHARE"}]}""")
        // Vorzugsaktie und ausgelistete Aktie fallen weg
        assertEquals(listOf("HT", "ZABA"), aktien.map { it.code })
        assertEquals("Zagrebačka banka", aktien[1].name)
        assertEquals("HT", aktien[0].name)
        val preise = bm.zsePreise("""{"securities":[{"symbol":"HT","isin":"HRHT00RA0005","close_price":"38.8000","security_class":"EQTY","security_type":"ORD-SHARE"},{"symbol":"ZABA","isin":"HRZABARA0009","close_price":"23.3000","security_class":"EQTY","security_type":"ORD-SHARE"}]}""")
        assertEquals(23.3, preise.getValue("ZABA"), 0.0001)
        val l = bm.malAnzahl(aktien, preise, "ZSE", "EUR", "HR")
        assertEquals(2, l.size)
        assertEquals(320241955.0 * 23.3, l.first { it.symbol == "ZSE:ZABA" }.wert, 1.0)
        assertEquals(76550000.0 * 38.8, l.first { it.symbol == "ZSE:HT" }.wert, 1.0)
        assertEquals("EUR", l[0].waehrung)
        assertEquals("HR", l[0].land)
        assertEquals("BANKEN", l.first { it.symbol == "ZSE:ZABA" }.branche)
        assertTrue(bm.zseAktien("kaputt").isEmpty())
        assertTrue(bm.zsePreise("{\"securities\":[]}").isEmpty())
    }

    @Test
    fun ljubljanaGleicheLogik() {
        val aktien = bm.zseAktien("""{"securities":[{"symbol":"KRKG","isin":"SI0031102120","name":"KRKA","listed_quantity":32793448,"segment_delisting_date":null,"security_class":"EQTY","security_type":"ORD-SHARE"},{"symbol":"NLBR","isin":"SI0021117344","name":"NLB","listed_quantity":20000000,"segment_delisting_date":null,"security_class":"EQTY","security_type":"ORD-SHARE"}]}""")
        val preise = bm.zsePreise("""{"securities":[{"symbol":"KRKG","isin":"SI0031102120","close_price":"249.0000","security_class":"EQTY","security_type":"ORD-SHARE"},{"symbol":"NLBR","isin":"SI0021117344","close_price":"223.0000","security_class":"EQTY","security_type":"ORD-SHARE"}]}""")
        val l = bm.malAnzahl(aktien, preise, "LJSE", "EUR", "SI")
        assertEquals(2, l.size)
        assertEquals(8165568552.0, l.first { it.symbol == "LJSE:KRKG" }.wert, 1.0)
        assertEquals("KRKA", l.first { it.symbol == "LJSE:KRKG" }.name)
    }

    @Test
    fun hoseAktienzahlUndKursInTausendDong() {
        val aktien = bm.hoseAktien("""{"data":{"list":[{"code":"AAA","name":"An Phat Bioplastics Joint Stock Company","securitiesType":1,"isin":"VN000000AAA4","outStanding":"393,742,730"},{"code":"AAM","name":"Mekong Fisheries Joint Stock Company","securitiesType":1,"isin":"VN000000AAM9","outStanding":"10,451,182"}]}}""")
        assertEquals(2, aktien.size)
        assertEquals("AAA", aktien[0].code)
        assertEquals(393742730.0, aktien[0].anzahl, 0.5)
        assertEquals("An Phat Bioplastics", aktien[0].name)
        assertEquals(2, bm.hoseSeitenGroesse("""{"data":{"list":[{"code":"AAA","name":"An Phat Bioplastics Joint Stock Company","securitiesType":1,"isin":"VN000000AAA4","outStanding":"393,742,730"},{"code":"AAM","name":"Mekong Fisheries Joint Stock Company","securitiesType":1,"isin":"VN000000AAM9","outStanding":"10,451,182"}]}}"""))
        val preise = bm.hoseKurse("""{"data":[{"securitySymbol":"AAA","priorClosePrice":"7.17","closePrice":"7.08"},{"securitySymbol":"VIC","priorClosePrice":"234.00","closePrice":"238.00"},{"securitySymbol":"ZZZ","priorClosePrice":"10.50","closePrice":""}]}""")
        assertEquals(7080.0 , preise.getValue("AAA"), 0.5)
        // ohne Schlusskurs gilt der Vortag
        assertEquals(10500.0, preise.getValue("ZZZ"), 0.5)
        val l = bm.malAnzahl(aktien, preise, "HOSE", "VND", "VN")
        assertEquals(1, l.size)
        assertEquals(2787698528400.0, l[0].wert, 1.0)
        assertEquals("VND", l[0].waehrung)
    }

    @Test
    fun hanoiAktienzahlUndKurs() {
        val aktien = bm.hnxAktien("""{"Content":"<table><tr>\r\n                    <td class=\"tdCenterAlign STT\">\r\n                        1\r\n                    </td>\r\n                    <td class=\"tdCenterAlign STOCK_CODE\">\r\n                        <a href=\"/cophieu-etfs/chi-tiet-chung-khoan-ny-clm.html\">CLM</a>\r\n                    </td>\r\n                    <td class=\"tdLeftAlign NAME\">\r\n                        <a href=\"/cophieu-etfs/chi-tiet-chung-khoan-ny-clm.html\">C&#244;ng ty cổ phần Xuất nhập khẩu Than - Vinacomin</a>\r\n                    </td>\r\n                     <td class=\"tdLeftAlign CATEGORIES_NAME\">\r\n                        Khai kho&#225;ng v&#224; Dầu kh&#237; \r\n                    </td>\r\n                    <td class=\"tdCenterAlign START_TRADING_DATE\">\r\n                        15/04/2016\r\n                    </td>\r\n                    <td class=\"tdRightAlign TOTAL_LISTING_QTTY\">\r\n                        14.464.925\r\n                    </td>\r\n                    <td class=\"tdRightAlign TOTAL_LISTING_VALUE\">\r\n                        14.464.925\r\n                    </td>\r\n                </tr><tr>\r\n                    <td class=\"tdCenterAlign STT\">\r\n                        2\r\n                    </td>\r\n                    <td class=\"tdCenterAlign STOCK_CODE\">\r\n                        <a href=\"/cophieu-etfs/chi-tiet-chung-khoan-ny-pbp.html\">PBP</a>\r\n                    </td>\r\n                    <td class=\"tdLeftAlign NAME\">\r\n                        <a href=\"/cophieu-etfs/chi-tiet-chung-khoan-ny-pbp.html\">CTCP Bao b&#236; Dầu kh&#237; Việt Nam</a>\r\n                    </td>\r\n                     <td class=\"tdLeftAlign CATEGORIES_NAME\">\r\n                        C&#244;ng nghiệp \r\n                    </td>\r\n                    <td class=\"tdCenterAlign START_TRADING_DATE\">\r\n                        27/01/2015\r\n                    </td>\r\n                    <td class=\"tdRightAlign TOTAL_LISTING_QTTY\">\r\n                        4.799.516\r\n                    </td>\r\n                    <td class=\"tdRightAlign TOTAL_LISTING_VALUE\">\r\n                        5.207.378\r\n                    </td>\r\n                </tr></table>","Total":""}""")
        assertEquals(listOf("CLM", "PBP"), aktien.map { it.code })
        val kurse = bm.hnxKurse("""{"success":1,"obj":["|CLM|CLM_stock_name*CLM|CLM_listing_status*N**N|CLM_status_ttthq*00**00|CLM_ref_price*63.1*4|CLM_ceil_price*69.4*1|CLM_floor_price*56.8*6|CLM_b_qtty_3*0.7*5|CLM_b_price_3*64.4*5|CLM_b_qtty_2*1.0*5|CLM_b_price_2*64.5*5|CLM_b_qtty_1*0.1*5|CLM_b_price_1*64.6*5|CLM_pct_change*1.5*5|CLM_last_price*64.6*5|CLM_last_qtty*0.1*5|CLM_traded_quantity*4.5*|CLM_total_value_traded_qtt*307,810*|CLM_s_qtty_3**2|CLM_s_price_3**2|CLM_s_qtty_2**2|CLM_s_price_2**2|CLM_s_qtty_1*0.1*1|CLM_s_price_1*69.4*1|CLM_open_price*69.1*5|CLM_mid_px*68.4*5|CLM_max_price*69.4*1|CLM_min_price*64.0*5|CLM_foreign_buy**|CLM_foreign_sell**|CLM_iNav**|CLM_close_price*64.6*5|CLM_index_code**|CLM_admin_status*BT**NRM|CLM_tradingmethod_status*BT**NRM|CLM_tradingsantion_status*BT**NRM|CLM_status*N**N|CLM_yield**|Bình thường|Normal|N/A|N/A|Công ty cổ phần Xuất nhập khẩu Than - Vinacomin|Vinacomin - Coal Import Export Joint Stock Company|NY|LIS"]}""")
        assertEquals(1, kurse.size)
        assertEquals(64599.99999999999, kurse[0].preis, 0.5)
        assertEquals("Vinacomin - Coal Import Export Joint Stock Company".let { bm.reinigen(it) }, kurse[0].name)
        val l = bm.hnxMalAnzahl(aktien, kurse, "HNX", "VN")
        assertEquals(1, l.size)
        assertEquals("HNX:CLM", l[0].symbol)
        assertEquals(64599.99999999999 * aktien.first { it.code == "CLM" }.anzahl, l[0].wert, 1.0)
    }

    @Test
    fun zypernOhneFondsUndZweitnotierung() {
        val l = bm.zypern("""<h3>MAIN MARKET</h3><table><tr>
 <td scope="row">
 <strong>
 BOCH</strong>
 </td>
 <td align="left">
 Bank of Cyprus Holdings Plc
 </td>
 <td align="center">
 19/1/2017
 </td>
 <td align="right">
 435962305
 </td>
 <td align="right">
 10.480(EUR)
 </td>
 <td align="right">
 1.00000
 </td>
 <td align="right">
 4,568,884,956.40
 </td>
 </tr><tr>
 <td scope="row">
 <strong>
 EUROBCY</strong>
 </td>
 <td align="left">
 EUROBANK S.A. (CR with VR_C)
 </td>
 <td align="center">
 19/12/2025
 </td>
 <td align="right">
 3609969343
 </td>
 <td align="right">
 4.694(EUR)
 </td>
 <td align="right">
 1.00000
 </td>
 <td align="right">
 16,945,196,096.04
 </td>
 </tr></table><h3>ALTERNATIVE MARKET</h3><table><tr>
 <td scope="row">
 <strong>
 ACT</strong>
 </td>
 <td align="left">
 ACTIBOND GROWTH FUND PUBLIC COMPANY LTD
 </td>
 <td align="center">
 11/5/2001
 </td>
 <td align="right">
 58429542
 </td>
 <td align="right">
 0.011(EUR)
 </td>
 <td align="right">
 1.00000
 </td>
 <td align="right">
 642,724.96
 </td>
 </tr><tr>
 <td scope="row">
 <strong>
 CCC</strong>
 </td>
 <td align="left">
 THE CYPRUS CEMENT PUBLIC COMPANY LTD
 </td>
 <td align="center">
 29/03/1996
 </td>
 <td align="right">
 137161879
 </td>
 <td align="right">
 1.340(EUR)
 </td>
 <td align="right">
 1.00000
 </td>
 <td align="right">
 183,796,917.86
 </td>
 </tr></table><h3>TREASURY BILLS</h3><table><tr>
 <td scope="row">
 <strong>
 TB13H26</strong>
 </td>
 <td align="left">
 13 weeks Treasury Bills 8th Edition 2026 (28/08/2026 – 27/11/2026)
 </td>
 <td align="center">
 28/08/2026
 </td>
 <td align="right">
 50000
 </td>
 <td align="right">
 1,000.000(EUR)
 </td>
 <td align="right">
 1.00000
 </td>
 <td align="right">
 50,000,000.00
 </td>
 </tr></table>""")
        // Eurobank (CR with VR), Actibond-Fonds und die Staatsanleihe fallen weg
        assertEquals(listOf("CSE:BOCH", "CSE:CCC"), l.map { it.symbol })
        assertEquals(4568884956.40, l[0].wert, 1.0)
        assertEquals("Bank of Cyprus Holdings", l[0].name)
        assertEquals("EUR", l[0].waehrung)
        assertEquals("CY", l[0].land)
        assertEquals("CYPRUS CEMENT", l[1].name)
    }

    @Test
    fun trinidadOhneSummenUndFonds() {
        val l = bm.trinidad("""<table class="ordinary_stocks"><tr><th>Security</th><th>Issued Share Capital</th><th>Capitalisation Value</th></tr><tr>
 <td><a href="https://www.stockex.co.tt/manage-stock/cibc/">CIBC </a></td>
 <td class="right">1,577,094,570</td>
 <td class="right">13,011,030,203</td>
 <td class="right">2</td>
 <td class="right">150</td>
 <td class="right">1,235.00</td>
 <td class="right"></td>
 <td class="right"></td>
 <td>&nbsp;</td>
 </tr><tr>
 <td><a href="https://www.stockex.co.tt/manage-stock/rfhl/">RFHL </a></td>
 <td class="right">163,923,916</td>
 <td class="right">18,644,706,206</td>
 <td class="right">12</td>
 <td class="right">10,617</td>
 <td class="right">1,207,540.70</td>
 <td class="right"></td>
 <td class="right"></td>
 <td>&nbsp;</td>
 </tr><tr>
 <th style="color:black;padding-bottom:5px; text-transform:uppercase;"><strong>BANKING Sector Totals</strong> </th>
 <th class="right">4,714,041,310</th>
 <th class="right">57,902,947,897</th>
 <th class="right">24</th>
 <th class="right">15,455</th>
 <th class="right">1,384,731.20</th>
 <th class="right">894.86</th>
 <th class="right">5.56</th>
 <th class="right">0.63</th>
 </tr></table><table><tr><th>Security</th><th>Issued Share Capital</th><th>Capitalisation Value</th></tr><tr>
 <td><a href="https://www.stockex.co.tt/manage-stock/calyp/">CALYP (S) </a></td>
 <td class="right">20,200,000</td>
 <td class="right">505,000,000</td>
 <td class="right">&#8211;</td>
 <td class="right">&#8211;</td>
 <td class="right">&#8211;</td>
 <td class="right">&nbsp;</td>
 <td class="right">&nbsp;</td>
 <td class="right">&nbsp;</td>
 </tr><tr>
 <th style="color:black; padding-bottom:1rem;">Mutual Funds Totals</th>
 <th class="right">130,797,571</th>
 <th class="right">633,055,239</th>
 <th class="right">&#8211;</th>
 <th class="right">&#8211;</th>
 <th class="right">&#8211;</th>
 <th class="right">&nbsp;</th>
 <th class="right">&nbsp;</th>
 <th class="right">&nbsp;</th>
 </tr></table>""")
        assertEquals(listOf("TTSE:CIBC", "TTSE:RFHL"), l.map { it.symbol })
        assertEquals(13011030203.0, l[0].wert, 1.0)
        assertEquals(18644706206.0, l[1].wert, 1.0)
        assertEquals("Republic Financial Holdings", l[1].name)
        assertEquals("TTD", l[0].waehrung)
        assertTrue(bm.trinidad("kaputt").isEmpty())
    }

    @Test
    fun bakuKapitalisierung() {
        val l = bm.baku("""<table><tbody><tr class="issuer main">
 <td>AZ1012006628</td>
 <td>
 <div class="issuer_holder">
 <a href="https://www.bfb.az/en/issuer/pasha-bank-open-joint-stock-company">PASHA BANK</a>
 </div>
 </td>
 <td>19.06.2026</td>
 
 <td>52.0000</td>
 <td>06.11.2026</td>
 <td class="stock_market_mayker">PAŞA Kapital</td>
 <td>18 658 516</td>
 <td class="reference_price shown_reference_td">52.19</td>
 <td class="nominal hidden_nominal_td">20</td>
 <td>973 787 950.04</td>
 
 </tr><tr class="issuer main">
 <td>AZ1004004581</td>
 <td>
 <div class="issuer_holder">
 <a href="https://www.bfb.az/en/issuer/azerbaycan-beynelxalq-banki-open-joint-stock-company">ABB</a>
 </div>
 </td>
 <td>11.06.2024</td>
 
 <td>0.3500</td>
 <td>06.11.2026</td>
 <td class="stock_market_mayker">ABB İnvest</td>
 <td>4 745 449 602</td>
 <td class="reference_price shown_reference_td">0.34</td>
 <td class="nominal hidden_nominal_td">0.27</td>
 <td>1 613 452 864.68</td>
 
 </tr><tr class="issuer standard">
 <td>AZ1004002882</td>
 <td>
 <div class="issuer_holder">
 <a href="https://www.bfb.az/en/issuer/bank-respublika-open-joint-stock-company">BANK RESPUBLIKA</a>
 </div>
 </td>
 <td>27.10.2026</td>
 
 <td></td>
 <td></td>
 <td class="stock_market_mayker"></td>
 <td>30 188 200</td>
 <td class="reference_price shown_reference_td"></td>
 <td class="nominal hidden_nominal_td">7.96</td>
 <td>240 298 072.00</td>
 
 </tr></tbody></table>""")
        assertTrue(l.size >= 2)
        assertEquals("AZN", l[0].waehrung)
        assertEquals("AZ", l[0].land)
        assertTrue(l.all { it.wert > 0 && it.symbol.startsWith("BFB:AZ") })
    }

    @Test
    fun kaseKursMalAktienzahl() {
        val l = bm.kase("""<h2>Representative list of KASE Index</h2><table><tbody><tr class="kase-table__tr"><td _ngcontent-ng-c3338555458="" kasestickycolumn="" class="kase-table__cell--sticky-left index-col" style="left: 0px;">1</td><td _ngcontent-ng-c3338555458="" kasestickycolumn="60" class="kase-table__cell--sticky-left" style="left: 60px;"><app-ticker-link _ngcontent-ng-c3338555458="" ngh="24"><a kase-ticker-chip="" class="kase-ticker-chip kase-ticker-chip--outlined kase-ticker-chip--medium" _nghost-ng-c3604033414="" href="/en/investors/shares/AIRA" ngh="0"><span _ngcontent-ng-c3604033414="" class="kase-ticker-chip__label">AIRA</span></a></app-ticker-link></td><td _ngcontent-ng-c3338555458=""><app-table-link _ngcontent-ng-c3338555458="" ngh="0"><app-link-button theme="green" type="routerLink" styling="none" _nghost-ng-c1954240678="" ngh="25"><!----><!----><a _ngcontent-ng-c1954240678="" class="green none normal" href="/en/listing/issuers/AIRA"><span _ngcontent-ng-c1954240678="">Air Astana</span><!----></a><!----></app-link-button></app-table-link></td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end">677,00</td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"><app-index-trend _ngcontent-ng-c3338555458="" _nghost-ng-c1335396825="" ngh="16"><div _ngcontent-ng-c1335396825="" class="up"><!----><span _ngcontent-ng-c1335396825=""><!----> +0,45 <!----><!----></span><!----></div><!----><!----></app-index-trend></td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"> 677,00 </td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"> 10,30 </td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end">354 951 593</td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end">59,0</td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"> 1,0000000 </td><!----></tr><tr class="kase-table__tr"><td _ngcontent-ng-c3338555458="" kasestickycolumn="" class="kase-table__cell--sticky-left index-col" style="left: 0px;">7</td><td _ngcontent-ng-c3338555458="" kasestickycolumn="60" class="kase-table__cell--sticky-left" style="left: 60px;"><app-ticker-link _ngcontent-ng-c3338555458="" ngh="24"><a kase-ticker-chip="" class="kase-ticker-chip kase-ticker-chip--outlined kase-ticker-chip--medium" _nghost-ng-c3604033414="" href="/en/investors/shares/KSPI" ngh="0"><span _ngcontent-ng-c3604033414="" class="kase-ticker-chip__label">KSPI</span></a></app-ticker-link></td><td _ngcontent-ng-c3338555458=""><app-table-link _ngcontent-ng-c3338555458="" ngh="0"><app-link-button theme="green" type="routerLink" styling="none" _nghost-ng-c1954240678="" ngh="25"><!----><!----><a _ngcontent-ng-c1954240678="" class="green none normal" href="/en/listing/issuers/KSPI"><span _ngcontent-ng-c1954240678="">Kaspi.kz</span><!----></a><!----></app-link-button></app-table-link></td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end">41 610,00</td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"><app-index-trend _ngcontent-ng-c3338555458="" _nghost-ng-c1335396825="" ngh="16"><div _ngcontent-ng-c1335396825="" class="down"><!----><span _ngcontent-ng-c1335396825=""><!----> -2,55 <!----><!----></span><!----></div><!----><!----></app-index-trend></td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"> 41 610,00 </td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"> 12,90 </td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end">190 027 266</td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end">39,0</td><td _ngcontent-ng-c3338555458="" kasecellalign="end" class="kase-table__cell--end"> 0,0576089 </td><!----></tr></tbody></table>""")
        assertEquals(listOf("KASE:AIRA", "KASE:KSPI"), l.map { it.symbol })
        assertEquals(677.0 * 354951593.0, l[0].wert, 1000.0)
        assertEquals(41610.0 * 190027266.0, l[1].wert, 1000.0)
        assertEquals("KZT", l[0].waehrung)
        assertEquals("Air Astana", l[0].name)
    }

    @Test
    fun limaKurseEmittentUndWert() {
        val kurse = bm.peruKurse("""{"content":[{"companyCode":"XXX","companyName":"Apple Inc.","shortName":"Apple Inc.","nemonico":"AAPL","last":343.5,"previous":342.78,"currency":"US${'$'}","segment":"RV3","negotiatedNationalAmount":85197.0},{"companyCode":"21802","companyName":"UNION DE CERVECERIAS PERUANAS BACKUS Y JOHNSTON S.A.A.","shortName":"UCP Backus & Johnston","nemonico":"BACKUAC1","previous":275.0,"currency":"S/","segment":"RV1","negotiatedNationalAmount":3060.0},{"companyCode":"21802","companyName":"UNION DE CERVECERIAS PERUANAS BACKUS Y JOHNSTON S.A.A.","shortName":"UCP Backus & Johnston","nemonico":"BACKUSI1","last":28.49,"previous":28.99,"currency":"S/","segment":"RV1","negotiatedNationalAmount":127711.0},{"companyCode":"64650","companyName":"SOCIEDAD MINERA CERRO VERDE S.A.A.","shortName":"Cerro Verde","nemonico":"CVERDEC1","last":63.5,"previous":65.62,"currency":"US${'$'}","segment":"RV1","negotiatedNationalAmount":2984165.0},{"companyCode":"70500","companyName":"INTEGRATEL PERÚ S.A.A.","shortName":"Integratel Perú","nemonico":"INTPEBC1","previous":0.13,"currency":"S/","segment":"RV1"}]}""")
        // auslaendisches Papier (AAPL, Firma XXX) fehlt; Integratel hat nur den Vortagskurs
        assertTrue(kurse.none { it.nemonico == "AAPL" })
        val cv = kurse.first { it.nemonico == "CVERDEC1" }
        assertEquals("USD", cv.waehrung)
        assertEquals(63.5, cv.preis, 0.0001)
        val e = bm.peruEmittent("""{"companyCode":"70500","companyName":"INTEGRATEL PERÚ S.A.A.","listValue":[{"listStock":[{"nemonico":"INTPEBC1","isin":"PEP705001209","quantity":"16501333878"}]},{"listStock":[]},{"listStock":[{"nemonico":"INTPECC1","isin":"PEP705001308","quantity":"190346"}]}]}""")
        assertNotNull(e)
        assertEquals("70500", e!!.firma)
        assertEquals("INTEGRATEL PERÚ", e.name)
        assertEquals(16501333878.0, e.aktien.getValue("INTPEBC1"), 0.5)
        val l = bm.peruWerte(kurse, mapOf(e.firma to e))
        assertEquals(1, l.size)
        assertEquals("BVL:70500", l[0].symbol)
        assertEquals("PEN", l[0].waehrung)
        assertEquals(16501333878.0 * 0.13 + 190346.0 * 0, l[0].wert, 10.0)
    }

    @Test
    fun speicherRundlauf() {
        val a = listOf(BoersenfeedsMehr.Aktie("HT", "HT", 76550000.0), BoersenfeedsMehr.Aktie("ZABA", "Zagrebačka banka", 320241955.0))
        assertEquals(a, bm.aktienLaden(bm.aktienSpeichern(a)))
        val e = mapOf("70500" to BoersenfeedsMehr.PeruEmittent("70500", "Integratel Perú", mapOf("INTPEBC1" to 16501333878.0)))
        assertEquals(e, bm.emittentenLaden(bm.emittentenSpeichern(e)))
        assertFalse(bm.aktienLaden("").isNotEmpty())
    }

    @Test
    fun zuordnungUeberKennung() {
        val n = Boersenfeeds.Notierung("HT", "ZSE:HT", 1.0, "EUR", "HR")
        assertTrue(Boersenfeeds.passt("ZSE:HT", "Hrvatski Telekom", n))
        assertFalse(Boersenfeeds.passt("", "Hrvatski Telekom", n))
        assertTrue(Boersenfeeds.passt("", "HT", n))
    }
}
