package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.Quartalszahlen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Kalender der Nasdaq (5.95), kleine echte Antworten vom 03.11.2026, gekuerzt
class Fassung595NasdaqTest {

    // In Raw-Strings loest Kotlin das Dollarzeichen Vorlagen aus, darum steht in den Antworten §; replace macht
    // daraus wieder das Dollarzeichen
    private val ipoMonat = """
        {"data":{"priced":{"asOf":null,"headers":null,"rows":[
        {"dealID":"1320589-119617","proposedTickerSymbol":"BBTX","companyName":"Bambusa Therapeutics, Inc.","proposedExchange":"NASDAQ Global Select","proposedSharePrice":"16.00","sharesOffered":"10,555,555","pricedDate":"10/30/2026","dollarValueOfSharesOffered":"§168,888,880","dealStatus":"Priced"},
        {"dealID":"1405733-119610","proposedTickerSymbol":"ASPE","companyName":"Aspen Neuroscience, Inc.","proposedExchange":"NASDAQ Global Select","proposedSharePrice":"15.00","sharesOffered":"9,375,000","pricedDate":"10/30/2026","dollarValueOfSharesOffered":"§140,625,000","dealStatus":"Priced"}]},
        "upcoming":{"upcomingTable":{"asOf":null,"headers":null,"rows":null},"lastUpdatedTime":"LAST UPDATED: 11/03/2026 - Source: EDGAR Online"},
        "filed":{"asOf":null,"headers":null,"rows":[
        {"dealID":"1408395-119777","proposedTickerSymbol":"UCSBU","companyName":"UCSB Acquisition Corp.","filedDate":"10/30/2026","dollarValueOfSharesOffered":"§69,000,000"},
        {"dealID":"1408177-119763","proposedTickerSymbol":"KATX","companyName":"Onyx Capital Tequila Partners IV, LLC","filedDate":"10/29/2026","dollarValueOfSharesOffered":"§26,833,338"}]},
        "withdrawn":{"asOf":null,"headers":null,"rows":[
        {"dealID":"1348215-116707","proposedTickerSymbol":null,"companyName":"EvoNexus Group LTD","proposedExchange":null,"sharesOffered":"6,250,000","filedDate":"1/16/2026","dollarValueOfSharesOffered":"§28,750,000","withdrawDate":"10/26/2026"}]},
        "month":10,"year":2026,"totalResults":7},"message":null,"status":{"rCode":200,"bCodeMessage":[{"code":1002,"errorMessage":"Upcoming:No record found."}],"developerMessage":null}}
    """.trimIndent().replace('§', '$')

    // selbst gebaute Zeile im Aufbau der Nasdaq-Seite: die echte Antwort enthielt keine upcoming-Zeilen
    private val ipoUpcoming = """
        {"data":{"priced":{"rows":null},
        "upcoming":{"upcomingTable":{"rows":[
        {"dealID":"1412345-119900","proposedTickerSymbol":"NOVA","companyName":"Nova Ray Inc.","proposedExchange":"NASDAQ Capital Market","proposedSharePrice":"14.00-16.00","sharesOffered":"8,000,000","expectedPriceDate":"11/12/2026","dollarValueOfSharesOffered":"§120,000,000"}]}},
        "filed":{"rows":null},"withdrawn":{"rows":null},"month":11,"year":2026},"message":null,"status":{"rCode":200}}
    """.trimIndent().replace('§', '$')

    private val zahlenKommend = """
        {"data":{"asOf":"Wed, Nov 4, 2026","headers":null,"rows":[
        {"lastYearRptDt":"11/05/2025","lastYearEPS":"§4.85","time":"time-not-supplied","symbol":"TM","name":"Toyota Motor Corp Ltd Ord","marketCap":"§255,087,084,000","fiscalQuarterEnding":"Sep/2026","epsForecast":"","noOfEsts":"N/A"},
        {"lastYearRptDt":"11/05/2025","lastYearEPS":"§0.15","time":"time-after-hours","symbol":"ARM","name":"Arm Holdings plc","marketCap":"§219,163,460,000","fiscalQuarterEnding":"Sep/2026","epsForecast":"§0.22","noOfEsts":"7"},
        {"lastYearRptDt":"11/05/2025","lastYearEPS":"§3.22","time":"time-pre-market","symbol":"MCD","name":"McDonald's Corporation","marketCap":"§182,656,434,000","fiscalQuarterEnding":"Sep/2026","epsForecast":"§3.36","noOfEsts":"11"}]},
        "message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent().replace('§', '$')

    private val zahlenVergangen = """
        {"data":{"asOf":"Thu, Oct 29, 2026","headers":null,"rows":[
        {"eps":"§1.89","surprise":"-5.97","time":"time-not-supplied","symbol":"AMZN","name":"Amazon.com, Inc.","marketCap":"§2,592,490,390,000","fiscalQuarterEnding":"Sep/2026","epsForecast":"§2.01","noOfEsts":"14"},
        {"eps":"§7.48","surprise":"39.03","time":"time-not-supplied","symbol":"LLY","name":"Eli Lilly and Company","marketCap":"§1,333,543,700,000","fiscalQuarterEnding":"Sep/2026","epsForecast":"§5.38","noOfEsts":"3"},
        {"eps":"N/A","surprise":"N/A","time":"time-pre-market","symbol":"HLN","name":"Haleon plc","marketCap":"§37,637,640,000","fiscalQuarterEnding":"Sep/2026","epsForecast":"","noOfEsts":"N/A"}]},
        "message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent().replace('§', '$')

    private val wochenende = """
        {"data":{"asOf":"Sat, Nov 7, 2026","headers":null,"rows":null},"message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent()

    private val dividenden = """
        {"data":{"calendar":{"asOf":"Wed, Nov 4, 2026","headers":null,"rows":[
        {"companyName":"Heritage Financial Corporation Common Stock","symbol":"HFWA","dividend_Ex_Date":"11/04/2026","payment_Date":"11/18/2026","record_Date":"11/04/2026","dividend_Rate":0.25,"indicated_Annual_Dividend":1.0,"announcement_Date":"10/21/2026"},
        {"companyName":"Kearny Financial Corp Common Stock","symbol":"KRNY","dividend_Ex_Date":"11/04/2026","payment_Date":"11/18/2026","record_Date":"11/04/2026","dividend_Rate":0.11,"indicated_Annual_Dividend":0.44,"announcement_Date":"10/21/2026"}]},
        "timeframe":{"minDate":"1988-10-03T00:00:00","maxDate":"2104-06-02T00:00:00"}},"message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent()

    private val splits = """
        {"data":{"asOf":"Tue, Nov 3, 2026","headers":null,"rows":[
        {"symbol":"SNDK","name":"Sandisk Corporation","ratio":"8:1","executionDate":"11/20/2026"},
        {"symbol":"EMBJ","name":"Embraer S.A.","ratio":"4 : 1","executionDate":"11/16/2026"},
        {"symbol":"MMT","name":"Aberdeen Multi-Market Income Fund","ratio":"1 : 4","executionDate":"11/09/2026"},
        {"symbol":"TOPS","name":"TOP Ships Inc.","ratio":"1 : 10","executionDate":"11/06/2026"}]},
        "message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent()

    // selbst gebaute Zeile: Dezimalverhaeltnis
    private val splitsDezimal = """
        {"data":{"asOf":"Tue, Nov 3, 2026","headers":null,"rows":[
        {"symbol":"TEST","name":"Test Corp","ratio":"1.5 : 1","executionDate":"11/25/2026"}]},
        "message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent()

    private val wirtschaft = """
        {"data":{"asOf":"Tue, Nov 3, 2026","headers":null,"rows":[
        {"gmt":"02:30","country":"Switzerland","eventName":"CPI","actual":"1.5%","consensus":"1.3%","previous":"1.0%","description":"The Consumer Price Index (CPI) measures the change in the pr"},
        {"gmt":"02:45","country":"France","eventName":"French Government Budget Balance","actual":"-159.2B","consensus":" ","previous":"-159.6B","description":"The French Government Budget Balance measures the difference"},
        {"gmt":"10:00","country":"United States","eventName":"JOLTS Job Openings","actual":"&nbsp;","consensus":"7.150M","previous":"7.079M","description":"A survey done by the US Bureau of Labor Statistics to help m"},
        {"gmt":"All Day","country":"Japan","eventName":"Japan - Culture Day","actual":" ","consensus":" ","previous":" ","description":""}]},
        "message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}
    """.trimIndent()

    @Test
    fun ipoMonatLesen() {
        val liste = NasdaqKalender.ipoLesen(ipoMonat)
        assertNotNull(liste)
        assertEquals(5, liste!!.size)
        val bbtx = liste.first { it.symbol == "BBTX" }
        assertEquals(IpoKalender.Status.PREIS, bbtx.status)
        assertEquals("bambusa therapeutics inc", bbtx.deal)
        assertEquals(LocalDate.of(2026, 10, 30).toEpochDay(), bbtx.tag)
        assertEquals("NASDAQ Global Select", bbtx.boerse)
        assertEquals("16.00", bbtx.preis)
        assertEquals(10_555_555L, bbtx.aktien)
        assertEquals(168_888_880.0, bbtx.volumen!!, 1e-6)
        val ucsbu = liste.first { it.symbol == "UCSBU" }
        assertEquals(IpoKalender.Status.EINGEREICHT, ucsbu.status)
        assertEquals(LocalDate.of(2026, 10, 30).toEpochDay(), ucsbu.tag)
        assertEquals(69_000_000.0, ucsbu.volumen!!, 1e-6)
        val evo = liste.first { it.name == "EvoNexus Group LTD" }
        assertEquals(IpoKalender.Status.ZURUECK, evo.status)
        assertNull(evo.symbol)
        assertNull(evo.boerse)
        assertEquals(LocalDate.of(2026, 10, 26).toEpochDay(), evo.tag)
        assertEquals(6_250_000L, evo.aktien)
        assertEquals(28_750_000.0, evo.volumen!!, 1e-6)
    }

    @Test
    fun ipoUpcomingLesen() {
        val liste = NasdaqKalender.ipoLesen(ipoUpcoming)
        assertNotNull(liste)
        assertEquals(1, liste!!.size)
        val a = liste[0]
        assertEquals(IpoKalender.Status.ANGEKUENDIGT, a.status)
        assertEquals("NOVA", a.symbol)
        assertEquals(LocalDate.of(2026, 11, 12).toEpochDay(), a.tag)
        assertEquals("14.00-16.00", a.preis)
        assertEquals(15.0, a.preisZahl!!, 1e-9)
        assertEquals(8_000_000L, a.aktien)
        assertEquals(120_000_000.0, a.volumen!!, 1e-6)
    }

    @Test
    fun wochenendeLeer() {
        val tag = LocalDate.of(2026, 11, 7)
        assertEquals(emptyList<Quartalszahlen.Termin>(), NasdaqKalender.zahlenLesen(wochenende, tag))
        assertEquals(emptyList<NasdaqKalender.Split>(), NasdaqKalender.splitsLesen(wochenende))
        assertEquals(emptyList<NasdaqKalender.Dividende>(), NasdaqKalender.dividendenLesen(wochenende))
        assertEquals(emptyList<NasdaqKalender.Wirtschaftstermin>(), NasdaqKalender.wirtschaftLesen(wochenende, tag))
    }

    @Test
    fun kaputteAntwortNull() {
        assertNull(NasdaqKalender.ipoLesen("nicht json"))
        assertNull(NasdaqKalender.ipoLesen("[]"))
        org.junit.Assert.assertEquals(emptyList<de.troetenrepublik.app.market.Quartalszahlen.Termin>(), NasdaqKalender.zahlenLesen("{\"data\":null}", LocalDate.of(2026, 11, 4)))
        org.junit.Assert.assertEquals(emptyList<NasdaqKalender.Dividende>(), NasdaqKalender.dividendenLesen("{\"data\":null}"))
        assertNull(NasdaqKalender.splitsLesen("Forbidden"))
        assertNull(NasdaqKalender.wirtschaftLesen("{\"message\":\"fehler\"}", LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun ipoZusammenAusZweiQuellen() {
        assertNull(NasdaqKalender.ipoZusammen(null, null))
        val finnhub = listOf(
            IpoKalender.Angebot(
                deal = "aspen neuroscience inc", symbol = "ASPE", name = "Aspen Neuroscience, Inc.", boerse = null,
                preis = null, aktien = 9_375_000L, volumen = 140_625_000.0, tag = LocalDate.of(2026, 10, 30).toEpochDay(),
                status = IpoKalender.Status.PREIS,
            ),
        )
        assertEquals(finnhub, NasdaqKalender.ipoZusammen(null, finnhub))
        val nasdaq = listOf(
            IpoKalender.Angebot(
                deal = "aspen neuroscience inc", symbol = "ASPE", name = "Aspen Neuroscience, Inc.",
                boerse = "NASDAQ Global Select", preis = "15.00", aktien = null, volumen = null,
                tag = LocalDate.of(2026, 10, 30).toEpochDay(), status = IpoKalender.Status.PREIS,
            ),
        )
        val gemischt = NasdaqKalender.ipoZusammen(nasdaq, finnhub)!!
        assertEquals(1, gemischt.size)
        assertEquals("ASPE", gemischt[0].symbol)
        assertEquals("NASDAQ Global Select", gemischt[0].boerse)
        assertEquals("15.00", gemischt[0].preis)
        assertEquals(9_375_000L, gemischt[0].aktien)
        assertEquals(140_625_000.0, gemischt[0].volumen!!, 1e-6)
    }

    @Test
    fun zahlenLesenKommendUndVergangen() {
        val tag = LocalDate.of(2026, 11, 4)
        val liste = NasdaqKalender.zahlenLesen(zahlenKommend, tag)!!
        assertEquals(3, liste.size)
        val tm = liste[0]
        assertEquals("TM", tm.symbol)
        assertEquals("Toyota Motor Corp Ltd Ord", tm.name)
        assertEquals(tag.toEpochDay(), tm.tag)
        assertEquals(Quartalszahlen.Zeit.OFFEN, tm.zeit)
        assertEquals(255_087_084_000.0, tm.wert!!, 1e-6)
        assertNull(tm.epsErwartet)
        assertNull(tm.schaetzungen)
        assertEquals(4.85, tm.epsVorjahr!!, 1e-9)
        assertNull(tm.eps)
        assertNull(tm.abweichung)
        assertEquals("Sep/2026", tm.quartal)
        val arm = liste[1]
        assertEquals(Quartalszahlen.Zeit.NACH, arm.zeit)
        assertEquals("Arm Holdings", arm.name)
        assertEquals(0.22, arm.epsErwartet!!, 1e-9)
        assertEquals(7, arm.schaetzungen)
        val mcd = liste[2]
        assertEquals(Quartalszahlen.Zeit.VOR, mcd.zeit)
        assertEquals(3.36, mcd.epsErwartet!!, 1e-9)
        assertEquals(11, mcd.schaetzungen)
        // vergangener Tag mit Ist-Wert und Abweichung in Prozent
        val vergangen = NasdaqKalender.zahlenLesen(zahlenVergangen, LocalDate.of(2026, 10, 29))!!
        val amzn = vergangen.first { it.symbol == "AMZN" }
        assertEquals(1.89, amzn.eps!!, 1e-9)
        assertEquals(-5.97, amzn.abweichung!!, 1e-9)
        assertEquals(2.01, amzn.epsErwartet!!, 1e-9)
        assertEquals("Sep/2026", amzn.quartal)
        val hln = vergangen.first { it.symbol == "HLN" }
        assertEquals(Quartalszahlen.Zeit.VOR, hln.zeit)
        assertNull(hln.eps)
        assertNull(hln.abweichung)
    }

    @Test
    fun zahlenMischenZweiQuellen() {
        assertNull(NasdaqKalender.zahlenMischen(null, null))
        val andere = listOf(
            Quartalszahlen.Termin("TM", "Toyota Motor", LocalDate.of(2026, 11, 4).toEpochDay(), Quartalszahlen.Zeit.VOR, umsatz = 7.5e10),
            Quartalszahlen.Termin("SIE", "Siemens", LocalDate.of(2026, 11, 4).toEpochDay(), Quartalszahlen.Zeit.OFFEN, wert = 1.2e11, eigen = true),
        )
        assertEquals(andere, NasdaqKalender.zahlenMischen(null, andere))
        val nasdaq = NasdaqKalender.zahlenLesen(zahlenKommend, LocalDate.of(2026, 11, 4))!!
        val gemischt = NasdaqKalender.zahlenMischen(nasdaq, andere)!!
        assertEquals(4, gemischt.size)
        val tm = gemischt.first { it.symbol == "TM" }
        assertEquals(Quartalszahlen.Zeit.VOR, tm.zeit)
        assertEquals(7.5e10, tm.umsatz!!, 1e-6)
        assertEquals(255_087_084_000.0, tm.wert!!, 1e-6)
        // nur der andere kennt SIE: bleibt mit eigenem Wert hinten
        assertEquals("SIE", gemischt[3].symbol)
        assertEquals(1.2e11, gemischt[3].wert!!, 1e-6)
    }

    @Test
    fun wirtschaftLesenUndWichtige() {
        val tag = LocalDate.of(2026, 11, 3)
        val liste = NasdaqKalender.wirtschaftLesen(wirtschaft, tag)!!
        assertEquals(4, liste.size)
        // Sortierung: ganztaegig zuerst, dann Uhrzeit, Land, Name
        assertNull(liste[0].minute)
        assertEquals("Japan", liste[0].land)
        assertNull(liste[0].ist)
        assertEquals(150, liste[1].minute)
        assertEquals("Switzerland", liste[1].land)
        assertEquals("CPI", liste[1].name)
        assertEquals("1.5%", liste[1].ist)
        assertEquals("1.3%", liste[1].erwartet)
        assertEquals("1.0%", liste[1].vorher)
        assertEquals(165, liste[2].minute)
        assertEquals("France", liste[2].land)
        assertEquals("-159.2B", liste[2].ist)
        assertNull(liste[2].erwartet)
        assertEquals(600, liste[3].minute)
        assertEquals("United States", liste[3].land)
        assertNull(liste[3].ist)
        assertEquals("7.150M", liste[3].erwartet)
        assertEquals(tag.toEpochDay(), liste[0].tag)
        assertEquals(
            LocalDateTime.of(2026, 11, 3, 2, 30).atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli(),
            NasdaqKalender.zeitpunkt(liste[1]),
        )
        assertNull(NasdaqKalender.zeitpunkt(liste[0]))
        // wichtige Laender ohne Feiertage: Frankreich und Schweiz bleiben, der japanische Feiertag faellt weg
        val wichtige = NasdaqKalender.wirtschaftWichtige(liste)
        assertEquals(3, wichtige.size)
        assertTrue(wichtige.none { it.land == "Japan" })
        assertTrue(wichtige.any { it.land == "France" })
        assertTrue(wichtige.any { it.land == "Switzerland" })
    }

    @Test
    fun entitaetenWertTextUndLaender() {
        assertEquals("R&D", NasdaqKalender.entitaeten("R&amp;D"))
        assertNull(NasdaqKalender.entitaeten("&nbsp;"))
        assertEquals("350.536,0", NasdaqKalender.wertText("350,536.0", true))
        assertEquals("-159,2B", NasdaqKalender.wertText("-159.2B", true))
        assertEquals("1,5%", NasdaqKalender.wertText("1.5%", true))
        assertEquals("1.5%", NasdaqKalender.wertText("1.5%", false))
        assertEquals("350,536.0", NasdaqKalender.wertText("350,536.0", false))
        assertEquals("EU", NasdaqKalender.landCode("Euro Zone"))
        assertNull(NasdaqKalender.landCode("Niemandsland"))
    }

    @Test
    fun adressen() {
        assertTrue(NasdaqKalender.wirtschaftAdresse(LocalDate.of(2026, 11, 3)).endsWith("date=2026-11-04"))
        assertTrue(NasdaqKalender.ipoAdresse(LocalDate.of(2026, 10, 5)).endsWith("date=2026-10"))
    }

    @Test
    fun splitsLesen() {
        val liste = NasdaqKalender.splitsLesen(splits)!!
        assertEquals(4, liste.size)
        val sndk = liste[0]
        assertEquals("SNDK", sndk.symbol)
        assertEquals(LocalDate.of(2026, 11, 20).toEpochDay(), sndk.tag)
        assertEquals(8, sndk.neu)
        assertEquals(1, sndk.alt)
        assertEquals("8:1", sndk.verhaeltnis)
        assertFalse(sndk.zusammenlegung)
        assertEquals("4:1", liste[1].verhaeltnis)
        assertFalse(liste[1].zusammenlegung)
        assertTrue(liste.first { it.symbol == "MMT" }.zusammenlegung)
        val tops = liste.first { it.symbol == "TOPS" }
        assertEquals(1, tops.neu)
        assertEquals(10, tops.alt)
        assertTrue(tops.zusammenlegung)
        // Dezimalverhaeltnis als kleinste ganze Zahlen
        val dezimal = NasdaqKalender.splitsLesen(splitsDezimal)!!
        assertEquals(3, dezimal[0].neu)
        assertEquals(2, dezimal[0].alt)
        assertEquals("3:2", dezimal[0].verhaeltnis)
    }

    @Test
    fun dividendenLesen() {
        val liste = NasdaqKalender.dividendenLesen(dividenden)!!
        assertEquals(2, liste.size)
        val hfwa = liste[0]
        assertEquals("HFWA", hfwa.symbol)
        assertEquals("Heritage Financial", hfwa.name)
        assertEquals(LocalDate.of(2026, 11, 4).toEpochDay(), hfwa.exTag)
        assertEquals(LocalDate.of(2026, 11, 18).toEpochDay(), hfwa.zahltag)
        assertEquals(LocalDate.of(2026, 11, 4).toEpochDay(), hfwa.stichtag)
        assertEquals(0.25, hfwa.betrag!!, 1e-9)
        assertEquals(1.0, hfwa.jeJahr!!, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 21).toEpochDay(), hfwa.angekuendigt)
        val krny = liste[1]
        assertEquals("Kearny Financial", krny.name)
        assertEquals(0.11, krny.betrag!!, 1e-9)
        assertEquals(0.44, krny.jeJahr!!, 1e-9)
        // eigene zuerst, unabhaengig vom Namen; Symbols.find liefert im Test nichts Gesichertes
        val eigene = hfwa.copy(eigen = true)
        val geordnet = NasdaqKalender.dividendenOrdnen(listOf(krny, eigene))
        assertEquals(2, geordnet.size)
        assertTrue(geordnet[0].eigen)
        assertTrue(NasdaqKalender.dividendenBekannte(listOf(krny, eigene)).any { it.eigen })
    }
}
