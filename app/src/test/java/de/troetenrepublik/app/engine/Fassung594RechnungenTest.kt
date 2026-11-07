package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.94: Korrekturen der Depotrechnungen aus der Fachpruefung.
class Fassung594RechnungenTest {

    private val tag = 86_400_000L
    private val jahr = 365L * tag
    private val t0 = 1_700_000_000_000L

    private fun tx(id: String, ts: Long, type: TxType, qty: Double = 0.0, price: Double = 0.0, extra: Double = 0.0) =
        Transaction(id = id, ts = ts, type = type, title = "X", qty = qty, price = price, symbol = "X", key = "X", extra = extra)

    @Test fun verkaufVerzerrtDieZeitgewichteteKurveNicht() {
        // 1 Stueck zu 100, 90 % im Lauf des Tages zu 98 verkauft, am Abend 0,1 Stueck zu 98
        val werte = listOf(t0 to 100.0, t0 + tag to 9.8, t0 + 2 * tag to 9.8)
        val twr = PortfolioTools.timeWeighted(werte, listOf(t0 + tag / 2 to -88.2))
        assertEquals(-2.0, twr[1].second, 1e-9)
        assertEquals(-2.0, twr[2].second, 1e-9)
    }

    @Test fun zinsfussMitVerkaufUndRueckkauf() {
        // Kauf 1.000, nach einem Jahr Verkauf fuer 2.000 und Rueckkauf fuer 2.000, nach zwei Jahren Wert 2.000
        val flows = listOf(
            PortfolioTools.Flow(t0, 1000.0, "A"),
            PortfolioTools.Flow(t0 + jahr, -2000.0, "A"),
            PortfolioTools.Flow(t0 + jahr, 2000.0, "A"),
        )
        val r = PortfolioTools.moneyWeighted(flows, 2000.0, t0 + 2 * jahr)!!
        assertEquals(Math.sqrt(2.0) - 1, r, 1e-6)
        // Unter einem Jahr: Zinsfuss fuer den Zeitraum, nicht hochgerechnet
        val halb = PortfolioTools.moneyWeighted(listOf(PortfolioTools.Flow(t0, 1000.0, "A")), 1100.0, t0 + jahr / 4, (jahr / 4).toDouble())!!
        assertEquals(0.10, halb, 1e-6)
    }

    @Test fun vergleichsanlageMeldetIhreVerkaufsbetraege() {
        val kurse = listOf(de.troetenrepublik.app.data.Candle(t0, 10.0, 10.0, 10.0, 10.0), de.troetenrepublik.app.data.Candle(t0 + jahr, 20.0, 20.0, 20.0, 20.0))
        val r = PortfolioTools.replay(listOf(PortfolioTools.Flow(t0, 100.0, "A"), PortfolioTools.Flow(t0 + jahr, -500.0, "A")), kurse, 20.0)!!
        // Nur 200 sind da, mehr kann nicht verkauft werden
        assertEquals(listOf(100.0, -200.0), r.flows.map { it.amount })
    }

    @Test fun anlegerprofilRechnetSplitsUm() {
        val txs = listOf(
            tx("b", t0, TxType.BUY, 10.0, 100.0),
            tx("s", t0 + 10 * tag, TxType.SPLIT, extra = 2.0),
            tx("v", t0 + 20 * tag, TxType.SELL, 20.0, 60.0),
        )
        val v = Anlegerprofil.verkaeufe(txs).single()
        assertEquals(20.0, v.prozent, 1e-9)
        assertEquals(200.0, v.euro, 1e-9)
    }

    @Test fun ordersProMonatBeiJungemDepot() {
        val jetzt = t0 + 400 * tag
        val txs = (0 until 40).map { i -> tx("o$i", jetzt - 60 * tag + i * tag, if (i % 2 == 0) TxType.BUY else TxType.SELL, 1.0, 10.0) }
        val h = Anlegerprofil.auswerten(txs, jetzt).first { it.merkmal == Anlegerprofil.Merkmal.HAEUFIGKEIT }
        assertTrue(h.wert > 19.0 && h.wert < 21.0)
        assertEquals(DepotCheck.Stufe.KRITISCH, h.stufe)
    }

    @Test fun einWertJeTag() {
        val r = listOf(t0 to 1.0, t0 + 3_600_000 to 2.0, t0 + tag to 3.0)
        val d = Risiko.taeglich(r)
        assertEquals(listOf(2.0, 3.0), d.map { it.second })
    }

    @Test fun alphaMitZins() {
        // Reihe = Markt: Beta 1, Alpha 0 mit und ohne Zins
        val markt = (0 until 60).map { i -> t0 + i * tag to 100.0 * (1 + 0.01 * Math.sin(i * 0.7)) }
        val v = Risiko.vergleich(markt, markt, zins = 0.03)!!
        assertEquals(1.0, v.beta, 1e-9)
        assertEquals(0.0, v.alphaProJahr, 1e-9)
    }
}
