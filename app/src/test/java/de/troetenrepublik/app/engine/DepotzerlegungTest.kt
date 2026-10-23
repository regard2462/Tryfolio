package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Depotverlauf: Ergebnis je Tag und Position ohne die Geldfluesse von Kauf und Verkauf.
class DepotzerlegungTest {

    private val tag = Depotzerlegung.TAG
    // Montag, 5. Oktober 2026, 00:00 UTC.
    private val montag = java.time.LocalDate.of(2026, 10, 5).toEpochDay() * tag

    private fun kauf(id: String, k: String, ts: Long, qty: Double, preis: Double, sym: String = k, d: Derivative? = null) =
        Transaction(id, ts, TxType.BUY, k, qty = qty, price = preis, symbol = sym, key = k, tag = "BUY", derivative = d)

    private fun verkauf(id: String, k: String, ts: Long, qty: Double, preis: Double, sym: String = k, d: Derivative? = null) =
        Transaction(id, ts, TxType.SELL, k, qty = qty, price = preis, symbol = sym, key = k, tag = "SELL", derivative = d)

    @Test fun kaufUndVerkaufVerfaelschenDasErgebnisNicht() {
        // 10 Stueck zu 100 am Montag, Schluss 110 am Dienstag, Verkauf zu 120 am Mittwoch: 200 Gewinn
        val acc = Account(transactions = listOf(kauf("a", "AAA", montag + 9 * 3_600_000L, 10.0, 100.0), verkauf("b", "AAA", montag + 2 * tag + 10 * 3_600_000L, 10.0, 120.0)))
        val schluss = mapOf(montag to 100.0, montag + tag to 110.0)
        val tage = Depotzerlegung.tage(montag, montag + 2 * tag, listOf("AAA"), Depotzerlegung.zufluesse(acc)) { k, t ->
            val stueck = Depotzerlegung.bestand(acc, t + tag - 1)[k] ?: return@tage 0.0
            stueck * (schluss[t] ?: return@tage null)
        }
        assertEquals(3, tage.size)
        assertEquals(0.0, tage[0].je["AAA"] ?: 0.0, 1e-9)
        assertEquals(100.0, tage[1].je["AAA"]!!, 1e-9)
        assertEquals(100.0, tage[2].je["AAA"]!!, 1e-9)
        assertEquals(1_000.0, tage[1].wertVorher, 1e-9)
        assertEquals(200.0, Depotzerlegung.summe(tage).single().second, 1e-9)
    }

    @Test fun ursachenEinesAnstiegs() {
        val a = Depotzerlegung.Abschnitt(montag, montag + tag - 1, mapOf("KO-NVDA" to 1_240.0, "NVDA" to 310.0, "SAP" to -50.0), 20_000.0)
        assertEquals(listOf("KO-NVDA", "NVDA", "SAP"), Depotzerlegung.ursachen(a).map { it.first })
        assertEquals(7.5, a.prozent!!, 1e-9)
        val b = Depotzerlegung.Abschnitt(montag, montag + tag - 1, mapOf("KO-NVDA" to -900.0, "SAP" to 40.0), 0.0)
        assertEquals("KO-NVDA", Depotzerlegung.ursachen(b).first().first)
        assertNull("ohne Bestand am Vortag kein Prozentwert", b.prozent)
    }

    @Test fun groessteAnstiegeUndRueckgaenge() {
        val tage = listOf(50.0, -400.0, 300.0, -20.0, 900.0).mapIndexed { i, v ->
            Depotzerlegung.Tag(montag + i * tag, mapOf("A" to v), 10_000.0)
        }
        val abschnitte = Depotzerlegung.abschnitte(tage, Depotzerlegung.Raster.TAG)
        assertEquals(listOf(900.0, 300.0), Depotzerlegung.groesste(abschnitte, 2, anstieg = true).map { it.summe })
        assertEquals(listOf(-400.0, -20.0), Depotzerlegung.groesste(abschnitte, 3, anstieg = false).map { it.summe })
    }

    @Test fun wochenUndMonate() {
        // Montag 5.10. bis Sonntag 18.10.2026: zwei Kalenderwochen; dazu der 1.11. in einem neuen Monat
        val tage = (0 until 14).map { Depotzerlegung.Tag(montag + it * tag, mapOf("A" to 1.0), 100.0) } +
            Depotzerlegung.Tag(java.time.LocalDate.of(2026, 11, 1).toEpochDay() * tag, mapOf("A" to 5.0), 100.0)
        val wochen = Depotzerlegung.abschnitte(tage, Depotzerlegung.Raster.WOCHE)
        assertEquals(listOf(7.0, 7.0, 5.0), wochen.map { it.summe })
        assertEquals(montag, wochen[0].beginn)
        val monate = Depotzerlegung.abschnitte(tage, Depotzerlegung.Raster.MONAT)
        assertEquals(listOf(14.0, 5.0), monate.map { it.summe })
    }

    @Test fun bestandUndZufluesseMitKnockout() {
        val ko = Transaction("k", montag + 3 * tag, TxType.KNOCKOUT, "Turbo", qty = 100.0, price = 0.001, symbol = "NVDA", key = "KO1", tag = "KO_SETTLE")
        val acc = Account(
            positions = listOf(Position("SAP", "SAP", "SAP", 5.0, 200.0, montag)),
            transactions = listOf(kauf("a", "KO1", montag, 100.0, 2.0, sym = "NVDA"), ko, kauf("b", "SAP", montag + tag, 5.0, 200.0)),
        )
        assertEquals(mapOf("KO1" to 100.0), Depotzerlegung.bestand(acc, montag + 1))
        assertEquals(mapOf("KO1" to 100.0, "SAP" to 5.0), Depotzerlegung.bestand(acc, montag + 2 * tag))
        assertEquals(mapOf("SAP" to 5.0), Depotzerlegung.bestand(acc, montag + 4 * tag))
        val z = Depotzerlegung.zufluesse(acc)
        assertEquals(200.0, z["KO1" to montag]!!, 1e-9)
        assertEquals(-0.1, z["KO1" to montag + 3 * tag]!!, 1e-9)
        assertEquals(2.0, Depotzerlegung.preisBis(Depotzerlegung.handelspreise(acc)["KO1"], montag + tag)!!, 1e-9)
    }

    @Test fun verkaufteDerivateBehaltenIhrProdukt() {
        val d = Derivative("ko-nvda", "NVDA", "NVIDIA", DerivativeKind.KO_LONG, strike = 150.0, barrier = 150.0, ratio = 10.0, ccy = "USD")
        val jetzt = montag + 5 * tag
        var acc = Trading.deposit(Account(), 10_000.0, montag)
        acc = Trading.buy(acc, d.id, "Turbo Long NVIDIA", "NVDA", 100.0, 3.0, d, now = montag + 3_600_000L).account
        assertEquals(d, acc.transactions.last { it.type == TxType.BUY }.derivative)
        acc = Trading.sell(acc, d.id, 100.0, 4.0, now = jetzt).account
        assertEquals(d, acc.transactions.last { it.type == TxType.SELL }.derivative)
        assertTrue("verkauft", acc.positions.none { it.key == d.id })
        // Fuer die Zeit, in der es im Depot lag, ist das Produkt wieder da
        val damals = Trading.holdingsAt(acc, montag + tag)[d.id]
        assertNotNull(damals)
        assertEquals(d, damals!!.derivative)
        assertEquals(100.0, damals.qty, 1e-9)
        assertEquals(d, Trading.produkte(acc)[d.id])
    }

    @Test fun alteVerkaeufeOhneProduktBleibenAussen() {
        val acc = Account(transactions = listOf(
            kauf("a", "alt", montag, 10.0, 5.0, sym = "NVDA"),
            verkauf("b", "alt", montag + 2 * tag, 10.0, 7.0, sym = "NVDA"),
        ))
        assertNull(Trading.holdingsAt(acc, montag + tag)["alt"])
        // Die Zerlegung bewertet sie zum Handelspreis: das Ergebnis faellt am Verkaufstag an
        val preise = Depotzerlegung.handelspreise(acc)
        val tage = Depotzerlegung.tage(montag, montag + 2 * tag, listOf("alt"), Depotzerlegung.zufluesse(acc)) { k, t ->
            val stueck = Depotzerlegung.bestand(acc, t + tag - 1)[k] ?: return@tage 0.0
            Depotzerlegung.preisBis(preise[k], t + tag - 1)?.let { stueck * it }
        }
        assertEquals(listOf(0.0, 0.0, 20.0), tage.map { it.je["alt"] ?: 0.0 })
    }

    @Test fun depotkurveBehaeltVerkaufteHebelOhneProdukt() {
        // Wie die Depotkurve sie bewertet: Turbo ohne gespeichertes Produkt zu 2 gekauft und zu 5 verkauft,
        // daneben eine Aktie, die bei 100 bleibt. Der Turbo muss mit seinen 300 Gewinn in der Kurve stehen.
        val acc = Account(
            positions = listOf(Position("AAA", "AAA", "AAA", 10.0, 100.0, montag)),
            transactions = listOf(
                kauf("a", "AAA", montag, 10.0, 100.0),
                kauf("b", "KO-ALT", montag + 3_600_000L, 100.0, 2.0, sym = "NVDA"),
                verkauf("c", "KO-ALT", montag + 2 * tag + 3_600_000L, 100.0, 5.0, sym = "NVDA"),
            ),
        )
        val preise = Depotzerlegung.handelspreise(acc)
        val kurve = (0..3).map { montag + it * tag + 12 * 3_600_000L }.map { t ->
            val stueck = Depotzerlegung.bestand(acc, t)
            val wert = (stueck["AAA"] ?: 0.0) * 100.0 + (stueck["KO-ALT"] ?: 0.0) * (Depotzerlegung.preisBis(preise["KO-ALT"], t) ?: 0.0)
            Candle(t, wert, wert, wert, wert)
        }
        val fluesse = acc.transactions.filter { Depotzerlegung.imDepot(it.type) }.map { it.ts to Depotzerlegung.fluss(it) }
        val neutral = Trading.flowNeutral(kurve, fluesse)
        assertEquals(300.0, neutral.last().close - neutral.first().close, 1e-9)
        // Der Verkauf laesst die Kurve nicht fallen: das Ergebnis steht am Verkaufstag, davor bleibt sie flach
        assertEquals(neutral[0].close, neutral[1].close, 1e-9)
        assertEquals(300.0, neutral[2].close - neutral[1].close, 1e-9)
        assertEquals(-500.0, Depotzerlegung.fluss(acc.transactions[2]), 1e-9)
    }
}
