package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Historie078.Art
import de.troetenrepublik.app.engine.Historie078.Punkt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Fassung 0.78, Paket q78e: Spanne gegen die eigene Geschichte, TTM aus XBRL, Leerverkauf, Fail-to-Deliver, Reg SHO, CSV.
// Alle Zahlen sind erfunden.
class Fassung078ETest {

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay()

    @Test fun perzentilInterpoliert() {
        val l = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(3.0, Historie078.perzentil(l, 0.5), 1e-9)
        assertEquals(1.4, Historie078.perzentil(l, 0.1), 1e-9)
        assertEquals(4.6, Historie078.perzentil(l, 0.9), 1e-9)
    }

    @Test fun spanneBrauchtSechsPunkteUndMedian() {
        val reihe = (1..10).map { Punkt(it.toLong(), it * 2.0) }
        assertNull(Historie078.spanne(reihe.take(5), 10.0))
        assertNull(Historie078.spanne(reihe, null))
        val s = Historie078.spanne(reihe, 22.0)!!
        assertEquals(11.0, s.median, 1e-9)
        assertEquals(2.0, s.min, 1e-9)
        assertEquals(20.0, s.max, 1e-9)
        assertEquals(100.0, s.abstand, 1e-9)
        assertEquals(10, s.n)
        assertEquals(100.0, Historie078.anteilDarunter(reihe, 22.0), 1e-9)
    }

    @Test fun vielfacheAusBasis() {
        val b = Historie078.Basis(0, eps = 5.0, umsatz = 1000.0, cashflow = 200.0, ebitda = 300.0, aktien = 100.0, schulden = 400.0, kasse = 100.0)
        assertEquals(20.0, Historie078.vielfach(Art.KGV, b, 100.0)!!, 1e-9)
        assertEquals(10.0, Historie078.vielfach(Art.KUV, b, 100.0)!!, 1e-9)
        assertEquals(50.0, Historie078.vielfach(Art.KCV, b, 100.0)!!, 1e-9)
        assertEquals((10000.0 + 400 - 100) / 300, Historie078.vielfach(Art.EV_EBITDA, b, 100.0)!!, 1e-9)
        // Verlust: kein KGV
        assertNull(Historie078.vielfach(Art.KGV, Historie078.Basis(0, -1.0, null, null, null, null, null, null), 100.0))
        // ohne Aktienzahl kein KUV
        assertNull(Historie078.vielfach(Art.KUV, Historie078.Basis(0, 1.0, 1000.0, null, null, null, null, null), 100.0))
    }

    @Test fun reiheNimmtKursZumStichtag() {
        val t1 = tag("2024-03-31")
        val kerzen = listOf(Candle(tag("2024-03-28") * 86_400_000L, 0.0, 0.0, 0.0, 50.0), Candle(tag("2024-06-28") * 86_400_000L, 0.0, 0.0, 0.0, 60.0))
        val basen = listOf(Historie078.Basis(t1, 5.0, null, null, null, null, null, null), Historie078.Basis(tag("2020-01-01"), 5.0, null, null, null, null, null, null))
        val r = Historie078.reihe(Art.KGV, basen, kerzen, 1.0)
        assertEquals(1, r.size)
        assertEquals(10.0, r[0].wert, 1e-9)
        // Pence gegen Pfund
        assertEquals(1.0, Historie078.waehrungsFaktor("USD", "USD")!!, 1e-9)
        assertEquals(0.01, Historie078.waehrungsFaktor("GBp", "GBP")!!, 1e-9)
        assertNull(Historie078.waehrungsFaktor("USD", "EUR"))
    }

    @Test fun yahooAntwortWirdZuBasen() {
        val text = """{"timeseries":{"result":[
            {"meta":{"type":["trailingDilutedEPS"]},"trailingDilutedEPS":[{"asOfDate":"2025-06-30","reportedValue":{"raw":6.0}},{"asOfDate":"2025-09-30","reportedValue":{"raw":7.0}}]},
            {"meta":{"type":["annualDilutedEPS"]},"annualDilutedEPS":[{"asOfDate":"2024-09-30","reportedValue":{"raw":5.0}},{"asOfDate":"2025-09-30","reportedValue":{"raw":7.5}}]},
            {"meta":{"type":["annualOrdinarySharesNumber"]},"annualOrdinarySharesNumber":[{"asOfDate":"2024-09-30","reportedValue":{"raw":1000.0}}]}
        ]}}"""
        val r = Historie078.yahooReihen(text)
        assertEquals(2, r["annualDilutedEPS"]!!.size)
        val basen = Historie078.basenYahoo(r)
        assertEquals(3, basen.size)
        // der TTM-Typ hat Vorrang vor dem Jahreswert
        assertEquals(7.0, basen.last().eps!!, 1e-9)
        assertEquals(1000.0, basen.first().aktien!!, 1e-9)
        // Bestand zu alt: keine Aktienzahl
        assertNull(basen.last().aktien)
        assertTrue(Historie078.yahooReihen("kaputt").isEmpty())
    }

    private fun fakt(s: String?, e: String, w: Double, f: String = e) = Historie078.Fakt(s?.let { tag(it) }, tag(e), w, tag(f))

    @Test fun ttmAusJahrUndZeitraumSeitJahresbeginn() {
        val fakten = listOf(
            fakt("2023-01-01", "2023-12-31", 400.0),
            fakt("2024-01-01", "2024-12-31", 480.0),
            fakt("2023-01-01", "2023-06-30", 180.0),
            fakt("2024-01-01", "2024-06-30", 220.0),
        )
        val r = Historie078.ttm(fakten)
        assertEquals(400.0, r.first { it.tag == tag("2023-12-31") }.wert, 1e-9)
        assertEquals(480.0, r.first { it.tag == tag("2024-12-31") }.wert, 1e-9)
        // 400 + 220 - 180
        assertEquals(440.0, r.first { it.tag == tag("2024-06-30") }.wert, 1e-9)
        // das erste Halbjahr hat kein Jahr davor
        assertTrue(r.none { it.tag == tag("2023-06-30") })
    }

    @Test fun faktenLesenNimmtJuengstenBerichtUndEinheit() {
        val text = """{"units":{"USD":[
            {"start":"2023-01-01","end":"2023-12-31","val":100,"filed":"2024-02-01"},
            {"start":"2023-01-01","end":"2023-12-31","val":110,"filed":"2025-02-01"},
            {"end":"2023-12-31","val":5,"filed":"2024-02-01"}],"EUR":[{"start":"2023-01-01","end":"2023-12-31","val":1,"filed":"2024-02-01"}]}}"""
        val f = Historie078.faktenLesen(text, "USD")
        assertEquals(2, f.size)
        assertEquals(110.0, f.first { it.start != null }.wert, 1e-9)
        assertEquals(1, Historie078.faktenLesen(text, "EUR").size)
        assertEquals(1, Historie078.bestaende(f).size)
        assertTrue(Historie078.faktenLesen("{}", "USD").isEmpty())
    }

    @Test fun splitsRechnenGewinnJeAktieUndAktienzahl() {
        val splits = listOf(Splits.Split(tag("2024-06-10") * 86_400_000L, 10.0, 1.0))
        val f = listOf(fakt("2023-01-01", "2023-12-31", 20.0, "2024-02-01"), fakt("2024-07-01", "2024-09-30", 3.0, "2024-11-01"))
        val eps = Historie078.splitAngepasst(f, splits, true)
        assertEquals(2.0, eps[0].wert, 1e-9)
        assertEquals(3.0, eps[1].wert, 1e-9)
        val aktien = Historie078.splitAngepasst(f, splits, false)
        assertEquals(200.0, aktien[0].wert, 1e-9)
    }

    @Test fun leerverkaufNasdaqLesen() {
        val text = """{"data":{"symbol":"aapl","shortInterestTable":{"rows":[
            {"settlementDate":"10/30/2026","interest":"144,791,472","avgDailyShareVolume":"43,085,075","daysToCover":3.36},
            {"settlementDate":"10/15/2026","interest":"131,591,865","avgDailyShareVolume":"35,498,663","daysToCover":"3.7"}]}},"status":{"rCode":200}}"""
        val l = Historie078.leerLesen(text)!!
        assertEquals(2, l.size)
        assertEquals(tag("2026-10-15"), l.first().tag)
        assertEquals(131_591_865.0, l.first().aktien, 1e-9)
        assertEquals(3.7, l.first().deckung!!, 1e-9)
        // nicht an der Nasdaq notiert: Daten null, aber gueltige Antwort
        assertEquals(0, Historie078.leerLesen("""{"data":null,"message":"x","status":{"rCode":200}}""")!!.size)
        // Fehler
        assertNull(Historie078.leerLesen("""{"data":null,"status":{"rCode":400}}"""))
        assertNull(Historie078.leerLesen("kaputt"))
        assertEquals(1.0e8, Historie078.streubesitz(5_000_000.0, 5.0)!!, 1e-3)
        assertNull(Historie078.streubesitz(0.0, 5.0))
        assertTrue(Historie078.nasdaqBoerse("NMS"))
        assertTrue(!Historie078.nasdaqBoerse("NYQ"))
    }

    @Test fun ftdAdressenUndZeilen() {
        val a = Historie078.ftdAdressen(LocalDate.of(2026, 11, 11), 4)
        assertEquals(Historie078.FTD_BASIS + "cnsfails202610b.zip", a[0])
        assertEquals(Historie078.FTD_BASIS + "cnsfails202610a.zip", a[1])
        assertEquals(Historie078.FTD_BASIS + "cnsfails202609b.zip", a[2])
        // Jahreswechsel
        val b = Historie078.ftdAdressen(LocalDate.of(2026, 1, 20), 3)
        assertEquals(Historie078.FTD_BASIS + "cnsfails202601a.zip", b[0])
        assertEquals(Historie078.FTD_BASIS + "cnsfails202512b.zip", b[1])
        val text = "SETTLEMENT DATE|CUSIP|SYMBOL|QUANTITY (FAILS)|DESCRIPTION|PRICE\r\n" +
            "20261001|037833100|AAPL|1200|APPLE INC|230.1\r\n20261002|037833100|AAPL|900|APPLE INC|231.0\r\n20261002|594918104|MSFT|50|MICROSOFT|400\r\n"
        val z = Historie078.ftdLesen(text, Historie078.ftdKuerzel("AAPL"))
        assertEquals(2, z.size)
        assertEquals(900.0, z.last().menge, 1e-9)
        assertEquals(tag("2026-10-02"), z.last().tag)
        assertTrue("BRK.B" in Historie078.ftdKuerzel("BRK-B"))
    }

    @Test fun regShoListe() {
        val text = "Symbol|Security Name|Market Category|Reg SHO Threshold Flag|Rule 3210|Filler\nAAPD|X|G|Y|N|\nZNB|Y|S|Y|N|\nQQQ|Z|G|N|N|\n20261109230005"
        val s = Historie078.regShoLesen(text)!!
        assertEquals(setOf("AAPD", "ZNB"), s)
        assertNull(Historie078.regShoLesen("<html>"))
        assertEquals("https://www.nasdaqtrader.com/dynamic/symdir/regsho/nasdaqth20261109.txt", Historie078.regShoAdresse(LocalDate.of(2026, 11, 9)))
        // Wochenende wird uebersprungen: Montag 9.11. dann Freitag 6.11.
        val tage = Historie078.regShoTage(LocalDate.of(2026, 11, 9), 2)
        assertEquals(LocalDate.of(2026, 11, 6), tage[1])
    }

    @Test fun csvDeutschUndEnglisch() {
        val k = listOf(Candle(tag("2024-03-28") * 86_400_000L, 10.5, 11.0, 10.0, 10.75, 1234.0))
        assertEquals("Datum;Eroeffnung;Hoch;Tief;Schluss;Volumen\r\n2024-03-28;10,5;11;10;10,75;1234\r\n", Historie078.ohlcvCsv(k, true))
        assertEquals("Date,Open,High,Low,Close,Volume\r\n2024-03-28,10.5,11,10,10.75,1234\r\n", Historie078.ohlcvCsv(k, false))
        val f = Historie078.finanzenCsv(listOf(Historie078.FinZeile("Y", "2024-12-31", 100.0, 40.0, 20.0, 15.5)), "USD", false)
        assertEquals(
            "Type,Period,Currency,Revenue,Gross profit,Operating income,Net income,Total assets,Total liabilities,Equity,Long-term debt,Operating cash flow,Capital expenditure,Free cash flow\r\n" +
                "Y,2024-12-31,USD,100,40,20,15.5,,,,,,,\r\n",
            f,
        )
        // Jahre aus den Abschluessen mit Bilanz und Cashflow, Unbekanntes leer, Quartale aus dem Profil
        val z = Historie078.finZeilen(
            listOf(Qualitaet.Jahr("2025-09-30", "USD", nettogewinn = 112.0, bilanzsumme = 360.0, cashflow = 111.0, umsatz = 416.0, verbindlichkeiten = 286.0, capex = -13.0)),
            listOf(Historie078.FinZeile("Y", "2025", 416.0, null, null, 112.0)),
            listOf(Historie078.FinZeile("Q", "Q3 2026", 114.0, null, null, 30.0)),
        )
        assertEquals(listOf("Y", "Q"), z.map { it.art })
        assertEquals(
            "Art;Periode;Waehrung;Umsatz;Bruttogewinn;Betriebsergebnis;Gewinn;Bilanzsumme;Verbindlichkeiten;Eigenkapital;Langfristige Schulden;Operativer Cashflow;Investitionen;Freier Cashflow\r\n" +
                "Y;2025-09-30;USD;416;;;112;360;286;74;;111;-13;98\r\n" + "Q;Q3 2026;EUR;114;;;30;;;;;;;\r\n",
            Historie078.finanzenCsv(z, "EUR", true),
        )
        // Ohne Abschluesse bleiben die Jahre des Profils
        assertEquals("2025", Historie078.finZeilen(emptyList(), listOf(Historie078.FinZeile("Y", "2025", 416.0, null, null, 112.0)), emptyList()).single().ende)
    }
}
