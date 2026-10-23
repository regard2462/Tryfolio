package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Portfolioverlauf.Handel
import de.troetenrepublik.app.market.Portfolioverlauf.Waehrung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// Portfolioverlaeufe im Copytrading: zeitgewichteter Verlauf aus Geschaeften, Waehrung, Zeitraum, Kennzahlen.
class PortfolioverlaufTest {

    private val d0 = 20_000L
    private fun ms(d: Long) = Portfolioverlauf.ms(d)
    private fun kerzen(vararg p: Pair<Long, Double>) = p.map { (d, c) -> Candle(ms(d), c, c, c, c) }

    @Test
    fun kongressZeitgewichtet() {
        val kurse = mapOf(
            "A" to kerzen(d0 to 10.0, d0 + 1 to 11.0, d0 + 2 to 12.0, d0 + 3 to 12.0),
            "B" to kerzen(d0 to 20.0, d0 + 1 to 20.0, d0 + 2 to 10.0, d0 + 3 to 10.0),
            "C" to kerzen(d0 to 5.0, d0 + 3 to 5.0),
        )
        val handel = listOf(
            Handel(ms(d0), "A", "A Inc", kauf = true, betrag = 100.0, ganz = false, option = false),
            Handel(ms(d0 + 1), "B", "B Inc", kauf = true, betrag = 200.0, ganz = false, option = false),
            Handel(ms(d0 + 2), "A", "A Inc", kauf = false, betrag = 15_000.0, ganz = true, option = false),
            Handel(ms(d0 + 2), "C", "C Inc", kauf = false, betrag = 50.0, ganz = true, option = false),
            Handel(ms(d0 + 1), "D", "ohne Kurs", kauf = true, betrag = 50.0, ganz = false, option = true),
        )
        val v = Portfolioverlauf.kongress(handel, kurse, ms(d0 + 3))
        assertNotNull(v)
        v!!
        assertEquals(4, v.reihe.size)
        assertEquals(100.0, v.reihe[0].second, 1e-9)
        assertEquals(110.0, v.reihe[1].second, 1e-9)
        // Tag 2: 10 A zu 12 und 10 B zu 10 = 220 gegen 310 nach dem Kauf von B
        assertEquals(110.0 * 220.0 / 310.0, v.reihe[2].second, 1e-9)
        // nach dem Verkauf von A bleibt nur B, das sich nicht bewegt
        assertEquals(v.reihe[2].second, v.reihe[3].second, 1e-9)
        assertEquals(1, v.ohneKurs)
        assertEquals(1, v.ohneBestand)
        assertEquals(listOf("B"), v.bestand.map { it.first })
        assertEquals(4, v.handel.size)
    }

    @Test
    fun kongressOhneKaeufe() {
        val kurse = mapOf("A" to kerzen(d0 to 10.0, d0 + 1 to 11.0))
        val v = Portfolioverlauf.kongress(listOf(Handel(ms(d0), "A", "A", false, 100.0, true, false)), kurse, ms(d0 + 1))
        assertTrue(v!!.reihe.isEmpty())
    }

    @Test
    fun waehrungMitTageskurs() {
        val usd = listOf(ms(d0) to 100.0, ms(d0 + 1) to 110.0)
        val fx = kerzen(d0 to 1.0, d0 + 1 to 1.1)
        val eur = Portfolioverlauf.umrechnen(usd, Waehrung.USD, Waehrung.EUR, fx)
        assertEquals(100.0, eur[0].second, 1e-9)
        assertEquals(100.0, eur[1].second, 1e-9)
        val zurueck = Portfolioverlauf.umrechnen(eur, Waehrung.EUR, Waehrung.USD, fx)
        assertEquals(110.0, zurueck[1].second, 1e-9)
        // ohne Kurse unveraendert
        assertEquals(usd, Portfolioverlauf.umrechnen(usd, Waehrung.USD, Waehrung.EUR, emptyList()))
    }

    @Test
    fun tagespunkteAusTageskursen() {
        val k = kerzen(*(0L until 30L).map { (d0 + it) to (100.0 + it) }.toTypedArray())
        val r = Portfolioverlauf.ausKursen(k)
        // jeder Handelstag ein Punkt
        assertEquals((0L until 30L).map { ms(d0 + it) }, r.map { it.first })
        assertEquals(129.0, r.last().second, 1e-9)
    }

    @Test
    fun fensterBeginntBeiHundert() {
        val r = listOf(ms(d0) to 50.0, ms(d0 + 10) to 100.0, ms(d0 + 20) to 150.0)
        val f = Portfolioverlauf.fenster(r, ms(d0 + 5))
        assertEquals(listOf(ms(d0 + 5) to 100.0, ms(d0 + 10) to 200.0, ms(d0 + 20) to 300.0), f)
        // Zeitraum vor der Reihe: ab ihrem Anfang
        assertEquals(100.0, Portfolioverlauf.fenster(r, ms(d0 - 100)).first().second, 1e-9)
    }

    @Test
    fun kennzahlenAusReihe() {
        val r = listOf(ms(d0) to 100.0, ms(d0 + 200) to 120.0, ms(d0 + 400) to 90.0, ms(d0 + 730) to 135.0)
        val k = Portfolioverlauf.kennzahlen(r)!!
        assertEquals(0.35, k.gesamt, 1e-9)
        assertEquals(-0.25, k.maxVerlust, 1e-9)
        assertEquals(1.35.pow(365.25 / 730) - 1, k.proJahr!!, 1e-9)
        assertNull(k.schwankung)
        // unter einem Jahr keine Rendite pro Jahr
        assertNull(Portfolioverlauf.kennzahlen(listOf(ms(d0) to 100.0, ms(d0 + 100) to 110.0))!!.proJahr)
    }

    @Test
    fun fortschreibenMitGroesstenPositionen() {
        val reihe = listOf(ms(d0) to 100.0, ms(d0 + 30) to 110.0)
        val kurse = mapOf(
            "A" to kerzen(d0 to 10.0, d0 + 30 to 10.0, d0 + 37 to 20.0),
            "B" to kerzen(d0 to 10.0, d0 + 30 to 10.0, d0 + 37 to 10.0),
        )
        val f = Portfolioverlauf.fortschreiben(reihe, ms(d0), listOf("A" to 0.5, "B" to 0.5), kurse)
        assertEquals(3, f.size)
        assertEquals(165.0, f.last().second, 1e-9)
        // ohne Kurse bleibt die Reihe
        assertEquals(reihe, Portfolioverlauf.fortschreiben(reihe, ms(d0), listOf("X" to 1.0), emptyMap()))
    }

    // Mitgelieferte Verlaufsdatei: alle Investoren, lueckenlose Quartale, Reihen passend zu den Stichtagen.
    @Test
    fun mitgelieferteVerlaeufe() {
        fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { java.io.File(it) }.first { it.exists() }.readText()
        val d = Portfolioverlauf.lesen(asset("copytrading/verlauf.json"))
        val investoren = de.troetenrepublik.app.market.Investoren.lesen(asset("copytrading/investoren.json"))
        // Ohne Verlauf: Investoren, deren Meldungen in einem Quartal weniger als die Haelfte mit Kursen erfassen
        assertEquals(investoren.map { it.id }.toSet() - setOf("watsa", "soros"), d.investoren.keys)
        for (inv in investoren.filter { it.id in d.investoren }) {
            val v = d.investoren.getValue(inv.id)
            assertTrue(inv.id, v.quartale.size >= 16)
            // Quartale im Abstand von drei Monaten, das letzte wie in investoren.json
            v.quartale.zipWithNext().forEach { (a, b) -> assertTrue(inv.id, (b.stichtag - a.stichtag) / 86_400_000L in 89L..93L) }
            assertEquals(inv.id, Portfolioverlauf.iso(inv.aktuell!!.stichtag), v.quartale.last().stichtag)
            assertTrue(inv.id, v.quartale.all { it.abgedeckt in 0.5..1.0 })
            assertTrue(inv.id, v.quartale.drop(1).all { q -> q.bewegungen.all { it.art in setOf("neu", "mehr", "weniger", "raus") } })
            // Wochenreihe ab dem ersten Stichtag bei 100, aufsteigend, in beiden Waehrungen gleich lang
            assertEquals(v.usd.size, v.eur.size)
            assertTrue(inv.id, v.usd.size >= 200)
            assertEquals(v.quartale.first().stichtag, v.usd.first().first)
            assertEquals(100.0, v.usd.first().second, 1e-6)
            assertTrue(inv.id, v.usd.zipWithNext().all { (a, b) -> b.first > a.first } && v.usd.all { it.second > 0 })
            // keine Spruenge ueber 40 % von Woche zu Woche (Hinweis auf falsche Kurse oder Splits)
            assertTrue(inv.id, v.usd.zipWithNext().all { (a, b) -> b.second / a.second in 0.6..1.4 })
        }
    }

    @Test
    fun dateiLesenUndMarken() {
        val text = """{"stand":"2026-10-14","quelle":"x","investoren":{"buffett":{"quartale":[
            {"stichtag":"2026-03-31","eingereicht":"2026-05-15","wertUsd":1.0e11,"positionen":40,"abgedeckt":0.99,"bewegungen":[]},
            {"stichtag":"2026-06-30","eingereicht":"2026-08-14","wertUsd":1.1e11,"positionen":41,"abgedeckt":0.98,
             "bewegungen":[["AAPL","Apple Inc.","mehr",3.2,1.5e9],["HPQ","HP Inc.","raus",-100.0,-8.0e8],["OXY","Occidental","neu",100.0,2.0e8]]}],
            "woche":{"t":["2026-03-31","2026-04-03","2026-10-09"],"usd":[100.0,101.5,108.2],"eur":[100.0,100.9,104.0]}}}}"""
        val d = Portfolioverlauf.lesen(text)
        assertEquals(Portfolioverlauf.iso("2026-10-14"), d.stand)
        val inv = d.investoren.getValue("buffett")
        assertEquals(2, inv.quartale.size)
        assertEquals(3, inv.usd.size)
        assertEquals(104.0, inv.reihe(Waehrung.EUR).last().second, 1e-9)
        val marken = Portfolioverlauf.quartalsMarken(inv) { _, b, kauf -> (if (kauf) "K:" else "V:") + b.joinToString(",") { it.kuerzel } }
        assertEquals(listOf("K:AAPL,OXY", "V:HPQ"), marken.map { it.second })
        assertTrue(marken.all { it.first == Portfolioverlauf.iso("2026-06-30") })
        // kaputte Datei
        assertTrue(Portfolioverlauf.lesen("{").investoren.isEmpty())
    }
}
