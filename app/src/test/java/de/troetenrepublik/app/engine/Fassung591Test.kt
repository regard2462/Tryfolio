package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.WatchMeta
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.91: Aktiensplits im Uebungsdepot, Sharpe ueber dem Zins, Insider-Gewicht nach Umfang, Waage im
// Bull- und Bear-Case, Rohstoff-Zuordnung.
class Fassung591Test {
    private val tag = 86_400_000L
    private val vorher = 100 * tag
    private val split = Splits.Split(200 * tag, 10.0, 1.0)

    private fun konto(): Account = Account(
        cash = 1000.0,
        positions = listOf(
            Position("NVDA", "NVIDIA", "NVDA", 2.0, 1000.0, vorher),
            Position("ko1", "Turbo", "NVDA", 100.0, 1.0, vorher,
                Derivative("ko1", "NVDA", "NVIDIA", DerivativeKind.KO_LONG, strike = 800.0, barrier = 820.0, ratio = 100.0, createdAt = vorher)),
        ),
        orders = listOf(Order("o1", "NVDA", "NVIDIA", "NVDA", Side.SELL, OrderType.LIMIT, 2.0, limitPrice = 1200.0, createdAt = vorher)),
        alerts = listOf(PriceAlert("a1", "NVDA", "NVIDIA", 900.0, false, createdAt = vorher, referencePrice = 1000.0)),
        watchMeta = mapOf("NVDA" to WatchMeta(vorher, 950.0)),
        transactions = listOf(Transaction("t1", vorher, TxType.BUY, "NVIDIA", qty = 2.0, price = 1000.0, symbol = "NVDA", key = "NVDA")),
    )

    @Test fun splitPasstAllesAn() {
        val a = Splits.anwenden(konto(), "NVDA", "NVIDIA", split, "s1")
        val p = a.positions.first { it.key == "NVDA" }
        assertEquals(20.0, p.qty, 1e-9)
        assertEquals(100.0, p.avgPrice, 1e-9)
        assertEquals(2000.0, p.invested, 1e-9)
        val d = a.positions.first { it.key == "ko1" }.derivative!!
        assertEquals(80.0, d.strike, 1e-9)
        assertEquals(82.0, d.barrier, 1e-9)
        assertEquals(10.0, d.ratio, 1e-9)
        val o = a.orders.single()
        assertEquals(20.0, o.qty, 1e-9)
        assertEquals(120.0, o.limitPrice, 1e-9)
        assertEquals(90.0, a.alerts.single().target, 1e-9)
        assertEquals(95.0, a.watchMeta["NVDA"]!!.price, 1e-9)
        val tx = a.transactions.last()
        assertEquals(TxType.SPLIT, tx.type)
        assertEquals("10:1", tx.subtitle)
        // Ein zweiter Lauf aendert nichts
        assertSame(a, Splits.anwenden(a, "NVDA", "NVIDIA", split, "s2"))
    }

    @Test fun splitNachKaufTrifftNichts() {
        val spaet = Splits.Split(50 * tag, 4.0, 1.0)
        val k = konto().copy(alerts = emptyList(), watchMeta = emptyMap(), orders = emptyList())
        assertSame(k, Splits.anwenden(k, "NVDA", "NVIDIA", spaet, "s1"))
    }

    @Test fun splitMitZukaufDanachUndUebernahme() {
        // 2 alte Stueck vor dem Split, 5 neue danach: nur die alten werden verzehnfacht
        val k = konto().let { it.copy(
            positions = listOf(Position("NVDA", "NVIDIA", "NVDA", 7.0, (2000.0 + 500.0) / 7, vorher)),
            transactions = it.transactions + Transaction("t2", 201 * tag, TxType.BUY, "NVIDIA", qty = 5.0, price = 100.0, symbol = "NVDA", key = "NVDA"),
        ) }
        val p = Splits.anwenden(k, "NVDA", "NVIDIA", split, "s1").positions.single()
        assertEquals(25.0, p.qty, 1e-9)
        assertEquals(100.0, p.avgPrice, 1e-9)
        // Uebernommen nach dem Split: der Broker hat ihn schon gebucht
        val imp = konto().copy(positions = listOf(Position("NVDA", "NVIDIA", "NVDA", 20.0, 100.0, vorher, importedAt = 250 * tag)))
        assertEquals(20.0, Splits.anwenden(imp, "NVDA", "NVIDIA", split, "s1").positions.single().qty, 1e-9)
    }

    @Test fun bestandVorDemSplitInHeutigenEinheiten() {
        val a = Splits.anwenden(konto(), "NVDA", "NVIDIA", split, "s1")
        // Vor dem Kauf nichts, zwischen Kauf und Split 20 Stueck (Kurse sind rueckwirkend umgerechnet)
        assertNull(Trading.holdingsAt(a, vorher - tag)["NVDA"])
        assertEquals(20.0, Trading.holdingsAt(a, 150 * tag)["NVDA"]!!.qty, 1e-9)
        assertEquals(20.0, Trading.holdingsAt(a, 250 * tag)["NVDA"]!!.qty, 1e-9)
    }

    @Test fun sharpeZiehtZinsAb() {
        // Ein Jahr mit stetigem Anstieg und kleiner Schwankung: ein Zins in Hoehe der mittleren Rendite drueckt Sharpe auf 0
        val reihe = (0..365).map { i -> i * tag to 100.0 * Math.pow(1.10, i / 365.0) * (1 + if (i % 2 == 0) 0.001 else -0.001) }
        val k = Risiko.kennzahlen(reihe)!!
        val ohne = k.sharpe!!
        val mittel = (1 until reihe.size).map { reihe[it].second / reihe[it - 1].second - 1 }.average() * k.proJahr
        val mit = Risiko.kennzahlen(reihe, zins = mittel)!!.sharpe!!
        assertTrue(ohne > 1.0)
        assertEquals(0.0, mit, 1e-9)
    }

    @Test fun insiderUmfang() {
        assertEquals(1.0, InsiderTrend.umfang(true, null), 1e-9)
        assertEquals(1.5, InsiderTrend.umfang(true, 0.5), 1e-9)
        assertEquals(0.5, InsiderTrend.umfang(true, 0.01), 1e-9)
        assertEquals(1.5, InsiderTrend.umfang(false, 0.8), 1e-9)
        assertEquals(0.5, InsiderTrend.umfang(false, 0.05), 1e-9)
        // Bestand danach 300, 100 verkauft: ein Viertel des Bestands davor
        val g = SecInsider.Geschaeft("a", "X", "X Inc", p = "A", r = "S", kauf = false, d = "2026-10-01", n = 100.0, preis = 10.0, wert = 1000.0, nach = 300.0)
        assertEquals(0.25, g.anteil!!, 1e-9)
        assertEquals(400.0, g.vor!!, 1e-9)
        // CEO verkauft 3 % seines Bestands, ein Director kauft seinen ersten Posten: die Waage kippt zum Kauf
        val t = InsiderTrend.auswerten(listOf(
            InsiderTrend.Geschaeft("ceo", InsiderTrend.Rolle.SPITZE, false, 1e6, "2026-10-01", anteil = 0.03),
            InsiderTrend.Geschaeft("dir", InsiderTrend.Rolle.AUFSICHT, true, 1e5, "2026-10-02", anteil = 1.0),
        ), "2026-09-01")!!
        assertTrue(t.wert > 0)
        val b = SecInsider.bestaende(listOf(g, g.copy(a = "b", d = "2026-10-05", nach = 250.0, kauf = false, n = 50.0)))
        assertEquals(250.0, b.single().stueck, 1e-9)
    }

    @Test fun bullBearWaage() {
        val f = BullBear.Fakten(kurs = 100.0, gewinnMarge = 0.25, eigenkapitalRendite = 0.3, zumHoch = 0.97, dividendenRendite = 0.035, ausschuettung = 0.5)
        val p = BullBear.auswerten(f, listOf(BullBear.Schlagzeile("Company faces SEC investigation", 0L, "1")), 0L)
        val k = p.mapNotNull { it.kennzahl }.toSet()
        assertTrue(BullBear.Kennzahl.NAHE_HOCH in k)
        assertTrue(BullBear.Kennzahl.RENDITE_HOCH in k)
        assertTrue(BullBear.Kennzahl.DIVIDENDE_SOLIDE in k)
        val w = BullBear.waage(p)!!
        // Dafuer: Marge 1 + Rendite 1 + Hoch 1 + Dividende 1 = 4, dagegen: Ermittlung 2
        assertEquals(4.0, w.dafuer, 1e-9)
        assertEquals(2.0, w.dagegen, 1e-9)
        assertEquals(BullBear.Stufe.EHER_DAFUER, w.stufe)
        assertNull(BullBear.waage(emptyList()))
        val tief = BullBear.auswerten(BullBear.Fakten(zumHoch = 0.5), emptyList(), 0L)
        assertTrue(tief.any { it.kennzahl == BullBear.Kennzahl.WEIT_UNTER_HOCH })
    }

    @Test fun rohstoffZuordnung() {
        assertEquals("GOLD", Rohstoffe.schluessel("GC=F"))
        assertEquals("GOLD", Rohstoffe.schluessel("4GLD.DE"))
        assertEquals("KORB", Rohstoffe.schluessel("sxrs.de"))
        assertNull(Rohstoffe.schluessel("AAPL"))
        assertTrue(Rohstoffe.istTermin("CL=F"))
        // Fuer jeden Schluessel gibt es Texte in beiden Sprachen
        val schluessel = listOf("GC=F", "SI=F", "PL=F", "PA=F", "CL=F", "NG=F", "HG=F", "ZW=F", "ZC=F", "KC=F", "CC=F", "SB=F", "SXRS.DE", "IS0E.DE")
            .map { Rohstoffe.schluessel(it)!! }
        for (k in schluessel) {
            assertEquals(4, de.troetenrepublik.app.i18n.De.rohstoffAbschnitte(k, false).size)
            assertEquals(5, de.troetenrepublik.app.i18n.En.rohstoffAbschnitte(k, true).size)
        }
    }

    @Test fun etfDurchschau() {
        // 60 % World-ETF mit 5 % NVIDIA, 30 % S&P-ETF mit 7 % NVIDIA, 10 % NVIDIA direkt
        val world = Durchschau.Bestand(listOf(Durchschau.Position("NVDA", "NVIDIA Corp", 0.05), Durchschau.Position("SAP", "SAP SE", 0.01)), emptyList(), 1.0, 0.0, 0.0)
        val sp = Durchschau.Bestand(listOf(Durchschau.Position("NVDA", "NVIDIA Corp", 0.07)), emptyList(), 1.0, 0.0, 0.0)
        val w = Durchschau.wirkung(mapOf("EUNL.DE" to 0.6, "SXR8.DE" to 0.3, "NVDA" to 0.1), mapOf("EUNL.DE" to world, "SXR8.DE" to sp))
        val n = w.first()
        assertEquals("NVDA", n.symbol)
        assertEquals(0.1, n.direkt, 1e-9)
        assertEquals(0.6 * 0.05 + 0.3 * 0.07, n.ueberEtfs, 1e-9)
        assertEquals(2, n.etfs.size)
        assertEquals("SAP", Durchschau.gleich("SAP.DE"))
        assertEquals("Technology", Durchschau.sektor("technology"))
    }

    @Test fun wetteVerkaufZumGeldkurs() {
        val w = de.troetenrepublik.app.data.Wette(
            id = "w1", quelle = "POLYMARKET", markt = "m", ereignis = "e", titel = "T", frage = "", seite = 0, seitenName = "Yes",
            anteile = 100.0, einstand = 0.5, kurs = 0.01, kursAm = 0L, eroeffnet = 0L, ende = 0L, token = "", serie = "", bild = "",
        )
        val a = Account(cash = 0.0, wetten = listOf(w))
        val r = Wetten.verkaufen(a, "w1", 0.009, 1L, "t")
        assertEquals(0.9, r.account.cash, 1e-9)
    }
}
