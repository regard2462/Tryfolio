package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.i18n.DepotTexte
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Portfolioverlauf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// 0.78, Depot, Analytics und Depot-Werkzeuge: eine Zaehlregel fuer Verkaeufe, erwartete Dividenden mit Zahltag und
// Steuer, Fremdwaehrung mit Durchschau, Fondskosten, Sparplan-Rueckblick, Dollar-Umrechnung, Jahreslaenge, Indexliste.
class Fassung078U4DepotTest {

    private val tag = 86_400_000L

    private fun tx(typ: TxType, ergebnis: Double, id: String = "t$typ$ergebnis") =
        Transaction(id = id, ts = 1_000L, type = typ, title = "X", qty = 1.0, price = 100.0, symbol = "X", realized = ergebnis)

    // ------------------------------------------------------------ Verkaeufe

    @Test
    fun verkaufsbilanzZaehltKnockoutUndVerfall() {
        val txs = listOf(
            tx(TxType.SELL, 50.0), tx(TxType.SELL, -20.0), tx(TxType.KNOCKOUT, -100.0), tx(TxType.EXPIRY, -30.0),
            tx(TxType.SELL, 10.0), tx(TxType.BUY, 0.0), tx(TxType.DIVIDEND, 0.0),
        )
        val v = Verkaufsbilanz.auswerten(txs)!!
        assertEquals(5, v.anzahl)
        assertEquals(2, v.gewinne)
        assertEquals(0.4, v.trefferquote, 1e-9)
        assertEquals(60.0 / 150.0, v.gewinnfaktor!!, 1e-9)
    }

    @Test
    fun verkaufsbilanzBrauchtFuenfVerkaeufeMitErgebnis() {
        assertEquals(5, Verkaufsbilanz.MIN_VERKAEUFE)
        val vier = (1..4).map { tx(TxType.SELL, it.toDouble(), "a$it") }
        assertNull(Verkaufsbilanz.auswerten(vier))
        // Ein Ergebnis unter einem halben Cent ist weder Gewinn noch Verlust und zaehlt nicht mit
        assertNull(Verkaufsbilanz.auswerten(vier + tx(TxType.SELL, 0.004)))
        assertNotNull(Verkaufsbilanz.auswerten(vier + tx(TxType.SELL, -1.0)))
        assertTrue(Verkaufsbilanz.gewinn(0.01))
        assertTrue(!Verkaufsbilanz.gewinn(0.004) && !Verkaufsbilanz.verlust(-0.004))
    }

    @Test
    fun anlegerprofilUndKalenderNutzenDieselbeRegel() {
        val txs = listOf(
            tx(TxType.SELL, 50.0, "1"), tx(TxType.SELL, -20.0, "2"), tx(TxType.KNOCKOUT, -100.0, "3"), tx(TxType.EXPIRY, -30.0, "4"), tx(TxType.SELL, 10.0, "5"),
        )
        val v = Verkaufsbilanz.auswerten(txs)!!
        val treffer = Anlegerprofil.auswerten(txs).first { it.merkmal == Anlegerprofil.Merkmal.TREFFER }
        assertEquals(v.trefferquote, treffer.wert, 1e-9)
        assertEquals(v.anzahl, treffer.zahl)
        val zone = ZoneOffset.UTC
        val tage = TradeCalendar.tage(txs, emptyList(), zone)
        val monat = TradeCalendar.monat(tage, java.time.YearMonth.of(1970, 1))
        assertEquals(v.anzahl, monat.trades)
        assertEquals(v.gewinne, monat.gewinne)
        assertEquals(v.anzahl - v.gewinne, monat.verluste)
        assertEquals(Verkaufsbilanz.MIN_VERKAEUFE, Anlegerprofil.MIN_VERKAEUFE)
    }

    // ------------------------------------------------------------ Dividenden

    @Test
    fun zahltagNimmtAngekuendigtenTerminSonstSchaetzung() {
        val now = 100 * tag
        val ex = 120 * tag
        // Ohne Angabe: drei Tage nach dem Ex-Tag
        assertEquals(ex + 3 * tag, PortfolioTools.zahltag(ex, 0L, 0L, now))
        // Angekuendigt, in der Zukunft und passend zum erwarteten Ex-Tag
        assertEquals(140 * tag, PortfolioTools.zahltag(ex, 121 * tag, 140 * tag, now))
        // Angekuendigter Zahltag liegt schon zurueck: Schaetzung
        assertEquals(ex + 3 * tag, PortfolioTools.zahltag(ex, 80 * tag, 90 * tag, now))
        // Angekuendigter Ex-Tag gehoert zu einer anderen Zahlung
        assertEquals(ex + 3 * tag, PortfolioTools.zahltag(ex, 220 * tag, 230 * tag, now))
        // Zahltag ohne Ex-Tag, nahe am erwarteten
        assertEquals(130 * tag, PortfolioTools.zahltag(ex, 0L, 130 * tag, now))
    }

    @Test
    fun erwarteteDividendenTragenDenZahltag() {
        val now = 400 * tag
        val q = PortfolioTools.DividendSource("A", "A", 10.0, listOf(60 * tag to 1.0, 240 * tag to 1.0), exAngekuendigt = 426 * tag, zahlungAngekuendigt = 440 * tag)
        val e = PortfolioTools.expectedDividends(listOf(q), now)
        assertEquals(2, e.size)
        // Die naechste Zahlung hat den angekuendigten Zahltag, die uebernaechste die Schaetzung
        assertEquals(440 * tag, e[0].zahltag)
        assertEquals(e[1].date + PortfolioTools.ZAHLTAG_ABSTAND, e[1].zahltag)
    }

    @Test
    fun nettoVerbrauchtTopfUndFreibetragInZahltagFolge() {
        val e = listOf(
            PortfolioTools.ExpectedDividend("B", "B", 20 * tag, 100.0, 23 * tag),
            PortfolioTools.ExpectedDividend("A", "A", 10 * tag, 100.0, 13 * tag),
        )
        val z = Dividendenerwartung.zahlungen(e, freibetragRest = 50.0, verlusttopf = 30.0, teilfrei = { 0.0 })
        // Reihenfolge nach Zahltag: A zuerst. 100 - 30 Topf - 50 Freibetrag = 20 pflichtig
        assertEquals("A", z[0].key)
        assertEquals(100.0 - 20.0 * Tax.RATE, z[0].netto, 1e-9)
        assertEquals(100.0 - 100.0 * Tax.RATE, z[1].netto, 1e-9)
        // Summe der Steuer wie auf die Jahressumme gerechnet
        assertEquals((200.0 - 80.0) * Tax.RATE, z.sumOf { it.brutto - it.netto }, 1e-9)
        // Fremdes Depot: brutto
        assertEquals(100.0, Dividendenerwartung.zahlungen(e, null)[0].netto, 1e-9)
        // Teilfreistellung eines Fonds
        val f = Dividendenerwartung.zahlungen(e.take(1), 0.0, teilfrei = { 0.3 })
        assertEquals(100.0 - 70.0 * Tax.RATE, f[0].netto, 1e-9)
    }

    @Test
    fun nettoMitSteuerstandDesKontos() {
        val now = LocalDate.of(2026, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val e = listOf(PortfolioTools.ExpectedDividend("AKTIE", "Aktie", now + 10 * tag, 1_000.0))
        val steuer = TaxState(year = 2026, allowanceUsed = 400.0)
        val z = Dividendenerwartung.fuerKonto(e, steuer, 1_000.0, now)
        assertEquals(1_000.0 - 400.0 * Tax.RATE, z[0].netto, 1e-6)
        // Der Kalender summiert erwartete Dividenden nach Steuern
        val d = TradeCalendar.Dividende("AKTIE", "Aktie", "AKTIE", z[0].zahltag, z[0].brutto, z[0].brutto - z[0].netto, 1.0, 1_000.0, erwartet = true)
        val t = TradeCalendar.tage(emptyList(), listOf(d), ZoneOffset.UTC).values.single()
        assertEquals(z[0].netto, t.dividendenErwartet, 1e-9)
    }

    // ------------------------------------------------------------ Fremdwaehrung und Fondskosten

    private fun wert(symbol: String, w: Double, art: DepotCheck.Art, waehrung: String, ter: Double? = null) =
        DepotCheck.Wert(symbol, w, art, waehrung, null, 0.0, ter = ter, symbol = symbol)

    private val welt = mapOf("US" to 0.70, "JP" to 0.06, "GB" to 0.04, "FR" to 0.03, "DE" to 0.03, "CH" to 0.03, "CA" to 0.03)

    @Test
    fun fremdwaehrungMitDurchschauWieDieSeiteStreuung() {
        val etf: (String) -> Map<String, Double>? = { if (it == "WELT.DE") welt else if (it == "SP.DE") mapOf("US" to 1.0) else null }
        // Welt-ETF in Euro: ohne Durchschau 0 %, mit Durchschau der Anteil ausserhalb des Euro
        val nurWelt = listOf(wert("WELT.DE", 1_000.0, DepotCheck.Art.ETF, "EUR"))
        assertEquals(0.0, DepotCheck.fremdwaehrung(nurWelt, "EUR"), 1e-9)
        assertEquals(0.86, DepotCheck.fremdwaehrung(nurWelt, "EUR", etf), 1e-9)
        // Dieselbe Zahl wie aus DepotStreuung.waehrungen
        val w = DepotStreuung.waehrungen(listOf(Triple("WELT.DE", 1_000.0, "EUR")), etf)
        assertEquals(1.0 - (w["EUR"] ?: 0.0) / 1_000.0, DepotCheck.fremdwaehrung(nurWelt, "EUR", etf), 1e-9)
        // Faustregel: Welt-ETF gut, S&P-500-ETF in Euro einseitig
        fun stufe(werte: List<DepotCheck.Wert>) =
            DepotCheck.pruefen(werte, 0.0, 0.0, 0.0, 0, 0, etf = etf).first { it.regel == DepotCheck.Regel.WAEHRUNG }.stufe
        assertEquals(DepotCheck.Stufe.GUT, stufe(nurWelt))
        assertEquals(DepotCheck.Stufe.ACHTUNG, stufe(listOf(wert("SP.DE", 1_000.0, DepotCheck.Art.ETF, "EUR"))))
        // Drei Viertel US-Aktien, ein Viertel Euro: unter der Schwelle
        val gemischt = listOf(wert("AAPL", 750.0, DepotCheck.Art.AKTIE, "USD"), wert("SAP.DE", 250.0, DepotCheck.Art.AKTIE, "EUR"))
        assertEquals(0.75, DepotCheck.fremdwaehrung(gemischt, "EUR", etf), 1e-9)
        assertEquals(DepotCheck.Stufe.GUT, stufe(gemischt))
        assertEquals(0.95, DepotCheck.FREMD_ACHTUNG, 1e-12)
    }

    @Test
    fun fremdwaehrungOhneHebelprodukteUndMitPence() {
        val werte = listOf(
            wert("SHEL.L", 500.0, DepotCheck.Art.AKTIE, "GBp"), wert("SAP.DE", 300.0, DepotCheck.Art.AKTIE, "EUR"),
            wert("KO1", 200.0, DepotCheck.Art.DERIVAT, "USD"),
        )
        assertEquals(0.5, DepotCheck.fremdwaehrung(werte, "EUR"), 1e-9)
        // Heimat Pfund: Pence zaehlen als Pfund
        assertEquals(0.3, DepotCheck.fremdwaehrung(werte, "GBP"), 1e-9)
    }

    @Test
    fun fondskostenEineRechnungFuerRegelUndStreuung() {
        val f = DepotCheck.fondskosten(listOf(10_000.0 to 0.2, 5_000.0 to 0.5))!!
        assertEquals(45.0, f.kosten, 1e-9)
        assertEquals(0.3, f.ter, 1e-9)
        assertNull(DepotCheck.fondskosten(emptyList()))
        val werte = listOf(wert("A.DE", 10_000.0, DepotCheck.Art.ETF, "EUR", 0.2), wert("B.DE", 5_000.0, DepotCheck.Art.ETF, "EUR", 0.5))
        val b = DepotCheck.pruefen(werte, 0.0, 0.0, 0.0, 0, 0).first { it.regel == DepotCheck.Regel.FONDSKOSTEN }
        assertEquals(f.ter / 100, b.anteil, 1e-12)
        assertEquals(45, b.anzahl)
    }

    // ------------------------------------------------------------ Regionen

    @Test
    fun regionenMitDurchschauDerFonds() {
        val a = DepotStreuung.anteile(
            listOf(Triple("WELT.DE", 600.0, true), Triple("SAP.DE", 300.0, false), Triple("BTC-EUR", 100.0, false)),
            { if (it == "SAP.DE") "DE" else null }, { if (it == "WELT.DE") mapOf("US" to 0.7, "JP" to 0.1, "DE" to 0.2) else null },
        )
        val b = DepotStreuung.bilanz(a, emptyMap(), "DE")!!
        // 900 von 1000 haben ein Land; Anteile am zugeordneten Teil
        assertEquals(0.9, b.zugeordnet, 1e-9)
        assertEquals(420.0 / 900.0, b.regionen.getValue(DepotStreuung.Region.NORDAMERIKA), 1e-9)
        assertEquals(420.0 / 900.0, b.regionen.getValue(DepotStreuung.Region.EUROPA), 1e-9)
        assertEquals(60.0 / 900.0, b.regionen.getValue(DepotStreuung.Region.ASIEN_PAZIFIK), 1e-9)
    }

    // ------------------------------------------------------------ Sparplan-Rueckblick

    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun kerzen(): List<Candle> {
        val aus = ArrayList<Candle>()
        var d = LocalDate.of(2023, 1, 2)
        var kurs = 100.0
        while (d.isBefore(LocalDate.of(2025, 7, 1))) {
            if (d.dayOfWeek.value <= 5) {
                aus += Candle(d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), kurs, kurs, kurs, kurs, 0.0)
                kurs *= 1.0004
            }
            d = d.plusDays(1)
        }
        return aus
    }

    @Test
    fun sparplanRueckblickRechnetWieDerVergleich() {
        val daily = kerzen()
        val divs = listOf(MarketApi.DividendEvent(ms(2023, 6, 15), 1.0), MarketApi.DividendEvent(ms(2024, 6, 14), 1.2))
        val now = ms(2025, 7, 1)
        val von = ms(2023, 1, 1)
        val e = Analysis.sparplanMitErtraegen(daily, divs, 200.0, von, null, now, ZoneOffset.UTC)!!
        // Dieselbe Rechnung wie im Vergleich je Wertpapier: Gesamtrendite-Reihe, dann Analysis.savingsPlan
        val ang = Analysis.thesaurierend(daily, divs)
        val p = Analysis.savingsPlan(ang, 200.0, von, null, now, ZoneOffset.UTC)!!
        assertEquals(p.value, e.plan.value, 1e-9)
        assertEquals(p.invested, e.plan.invested, 1e-9)
        assertEquals(p.executions, e.plan.executions)
        assertEquals(30, e.plan.executions)
        // Kauf am ersten Handelstag: der 2. Januar 2023
        assertEquals(ms(2023, 1, 2), e.beginn)
        // Ausschuettungen wieder angelegt: mehr als ohne
        val ohne = Analysis.sparplanMitErtraegen(daily, emptyList(), 200.0, von, null, now, ZoneOffset.UTC)!!
        assertTrue(e.plan.value > ohne.plan.value)
        // Einmalanlage: der ganze Betrag am ersten Kauftag
        assertEquals(e.plan.invested / ang.first().close * ang.last().close, e.einmal, 1e-6)
        assertNotNull(e.plan.perYear)
    }

    @Test
    fun sparplanRueckblickMitLivekursUndSpaeteremBeginn() {
        val daily = kerzen()
        val divs = listOf(MarketApi.DividendEvent(ms(2023, 6, 15), 1.0))
        val now = ms(2025, 7, 1)
        val ang = Analysis.thesaurierend(daily, divs)
        val faktor = ang.last().close / daily.last().close
        val e = Analysis.sparplanMitErtraegen(daily, divs, 100.0, ms(2015, 1, 1), 200.0, now, ZoneOffset.UTC)!!
        // Der Livekurs bekommt den Faktor der letzten Kerze
        assertEquals(e.plan.units * 200.0 * faktor, e.plan.value, 1e-6)
        // Die Reihe beginnt spaeter als gewaehlt: Beginn ist der erste Kurs
        assertEquals(daily.first().t, e.beginn)
        assertNull(Analysis.sparplanMitErtraegen(emptyList(), divs, 100.0, 0L, null, now, ZoneOffset.UTC))
        assertNull(Analysis.sparplanMitErtraegen(daily, divs, 100.0, ms(2030, 1, 1), null, now, ZoneOffset.UTC))
    }

    // ------------------------------------------------------------ Was waere, wenn: Dollar

    @Test
    fun dollarUmrechnungWieDerWaehrungsschock() {
        val posten = listOf(
            Szenarien.Posten("AAPL", "Apple", 600.0, waehrung = mapOf("USD" to 1.0)),
            Szenarien.Posten("WELT.DE", "Welt", 200.0, waehrung = mapOf("USD" to 0.7, "EUR" to 0.3)),
            Szenarien.Posten("SAP.DE", "SAP", 100.0, waehrung = mapOf("EUR" to 1.0)),
            Szenarien.Posten("KO", "Schein", 100.0, messbar = false, waehrung = mapOf("USD" to 1.0)),
        )
        // Dollar -10 %: 60 % + 14 % Dollar-Anteil verlieren 10 %
        assertEquals(-0.074, Szenarien.dollarUmrechnung(posten, "EUR", -10.0)!!, 1e-9)
        // Dieselbe Bewegung wie der Waehrungsschock, wenn der Euro entsprechend steigt (nur Dollar-Anteile)
        val schock = 1.0 / 0.9 - 1.0
        assertEquals(Waehrungsschock.umrechnung(schock), -0.1, 1e-9)
        // Heimat Dollar: die anderen Waehrungen gewinnen bei fallendem Dollar
        val usd = Szenarien.dollarUmrechnung(posten, "USD", -10.0)!!
        assertEquals((0.06 + 0.1) * (1.0 / 0.9 - 1.0), usd, 1e-9)
        // Ohne Dollar-Anteil keine Zahl
        assertNull(Szenarien.dollarUmrechnung(listOf(Szenarien.Posten("SAP.DE", "SAP", 100.0, waehrung = mapOf("EUR" to 1.0))), "EUR", -10.0))
    }

    // ------------------------------------------------------------ Jahreslaenge, Indexliste, Ziele, Texte

    @Test
    fun jahrHatUeberall36525Tage() {
        assertEquals(365.25, PortfolioTools.YEAR_DAYS, 0.0)
        assertEquals((365.25 * 86_400_000.0).toLong(), PortfolioTools.JAHR_MS)
    }

    @Test
    fun eineIndexlisteFuerKurveUndIndexvergleich() {
        val m = Portfolioverlauf.MASSSTAEBE
        assertEquals(listOf("EUNL.DE", "VWCE.DE", "IUSQ.DE", "SXR8.DE", "SXRV.DE", "EXS1.DE", "XSX6.DE", "EUNM.DE"), m.map { it.symbol })
        assertEquals(
            listOf("MSCI_WORLD", "FTSE_ALL_WORLD", "MSCI_ACWI", "SP500", "NASDAQ100", "DAX", "STOXX600", "EM"), m.map { it.schluessel },
        )
        assertEquals(m.size, m.map { it.id }.toSet().size)
        // Die bisherigen Kennungen der Kurve bleiben
        assertTrue(m.map { it.id }.containsAll(listOf("msci", "acwi", "sp500", "nasdaq", "dax")))
    }

    @Test
    fun zielPortfolioPerformanceIstErlaubt() {
        assertEquals("analytics/PERFORMANCE", WidgetZiel.pruefen(WidgetZiel.PERFORMANCE))
    }

    @Test
    fun depotTexteInBeidenSprachen() {
        assertEquals(DepotTexte.schluessel(true), DepotTexte.schluessel(false))
        for (k in DepotTexte.schluessel(true)) {
            assertTrue(k, DepotTexte.text(true, k, listOf("1")).isNotBlank())
            assertTrue(k, DepotTexte.text(false, k, listOf("1")).isNotBlank())
        }
        assertEquals("vor Steuern 12 €", DepotTexte.text(true, "div.vorSteuern", listOf("12 €")))
        assertEquals("", DepotTexte.text(true, "gibt.es.nicht", emptyList()))
    }
}
