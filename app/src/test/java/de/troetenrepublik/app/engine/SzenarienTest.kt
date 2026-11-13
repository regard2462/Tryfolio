package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Empfindlichkeit.Faktor
import de.troetenrepublik.app.engine.Szenarien.Messung
import de.troetenrepublik.app.engine.Szenarien.Posten
import de.troetenrepublik.app.engine.Szenarien.Steigung
import de.troetenrepublik.app.engine.Szenarien.Szenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

// Rechenbeispiele "Was waere, wenn": feste Reihen und feste Steigungen, kein Netz.
class SzenarienTest {
    private val tagMs = 86_400_000L
    private val ersterFreitag = 16_808L

    private fun reihe(renditen: DoubleArray): List<Candle> {
        var kurs = 100.0
        val aus = ArrayList<Candle>()
        for (i in 0..renditen.size) {
            if (i > 0) kurs *= exp(renditen[i - 1])
            val freitag = (ersterFreitag + 7L * i) * tagMs + 12 * 3_600_000L
            aus += Candle(freitag, kurs, kurs, kurs, kurs)
        }
        return aus
    }

    private fun zufall(n: Int, seed: Long, sigma: Double = 0.02): DoubleArray = Random(seed).let { r -> DoubleArray(n) { r.nextGaussian() * sigma } }

    private fun messung(faktor: Faktor, gesamt: Double, h1: Double, h2: Double, se: Double = 0.0) = Messung(mapOf(faktor to Steigung(gesamt, se, h1 to h2)))

    private fun posten(symbol: String, wert: Double = 100.0, messbar: Boolean = true, waehrung: Map<String, Double> = mapOf("EUR" to 1.0)) =
        Posten(symbol, symbol, wert, messbar, waehrung)

    // Wochenrenditen mit bekannter Abhaengigkeit: y = 0.8 * Markt + b * Groesse + Rauschen
    private fun abhaengig(n: Int, b: Double, seed: Long, bHalb2: Double = b): Triple<List<Candle>, List<Candle>, List<Candle>> {
        val m = zufall(n, seed)
        val g = zufall(n, seed + 1)
        val r = zufall(n, seed + 2, 0.004)
        val y = DoubleArray(n) { 0.8 * m[it] + (if (it < n / 2) b else bHalb2) * g[it] + r[it] }
        return Triple(reihe(y), reihe(m), reihe(g))
    }

    @Test(timeout = 20_000)
    fun vierwochenBloeckeTreffenDieBekannteSteigung() {
        val (w, m, g) = abhaengig(480, -0.6, 51)
        val me = Szenarien.messen(w, m, mapOf(Faktor.OEL to g))
        val st = me.steigungen[Faktor.OEL]!!
        assertEquals(-0.6, st.gesamt, 0.1)
        assertEquals(-0.6, st.haelften.first, 0.2)
        assertEquals(-0.6, st.haelften.second, 0.2)
        assertTrue(st.se > 0.0)
    }

    @Test(timeout = 20_000)
    fun zuKurzeReiheOhneMessung() {
        val (w, m, g) = abhaengig(120, -0.6, 52)
        assertTrue(Szenarien.messen(w, m, mapOf(Faktor.OEL to g)).steigungen.isEmpty())
        // fehlende Kerzen der Groesse: keine Messung dieser Groesse
        val (w2, m2, _) = abhaengig(480, -0.6, 53)
        assertTrue(Szenarien.messen(w2, m2, mapOf(Faktor.OEL to emptyList())).steigungen.isEmpty())
    }

    @Test(timeout = 20_000)
    fun wechselndesVorzeichenDerHaelftenIstNichtBelegt() {
        val (w, m, g) = abhaengig(480, 0.9, 54, bHalb2 = -0.9)
        assertNull(Szenarien.messen(w, m, mapOf(Faktor.OEL to g)).steigungen[Faktor.OEL])
    }

    @Test(timeout = 20_000)
    fun zinsenInPunktenWirkenProPunkt() {
        val n = 480
        val m = zufall(n, 61)
        val z = zufall(n, 62, 0.1)
        val r = zufall(n, 63, 0.004)
        val y = DoubleArray(n) { 0.8 * m[it] - 0.05 * z[it] + r[it] }
        // Zinsreihe als Stand in Punkten: Summe der Aenderungen
        var stand = 4.0
        val zinsen = ArrayList<Candle>()
        for (i in 0..n) {
            if (i > 0) stand += z[i - 1]
            val t = (ersterFreitag + 7L * i) * tagMs + 12 * 3_600_000L
            zinsen += Candle(t, stand + 20.0, stand + 20.0, stand + 20.0, stand + 20.0)
        }
        val me = Szenarien.messen(reihe(y), reihe(m), mapOf(Faktor.ZINS10J to zinsen))
        val st = me.steigungen[Faktor.ZINS10J]!!
        assertEquals(-0.05, st.gesamt, 0.02)
        assertEquals(1.0, Szenarien.eingang(Szenario.ZINS, 1.0), 0.0)
    }

    @Test(timeout = 20_000)
    fun spanneMitZweiStandardfehlernUndKappungAnDerNull() {
        val st = Steigung(0.4, 0.1, 0.3 to 0.5)
        assertEquals(0.3 - 0.2, st.grenzen.first, 1e-12)
        assertEquals(0.5 + 0.2, st.grenzen.second, 1e-12)
        // positive Steigung: nie unter null
        val nah = Steigung(0.2, 0.3, 0.1 to 0.3)
        assertEquals(0.0, nah.grenzen.first, 0.0)
        assertEquals(0.3 + 0.6, nah.grenzen.second, 1e-12)
        // negative Steigung: nie ueber null
        val neg = Steigung(-0.2, 0.3, -0.1 to -0.3)
        assertEquals(-0.3 - 0.6, neg.grenzen.first, 1e-12)
        assertEquals(0.0, neg.grenzen.second, 0.0)
        // Wirkung im Depot: Gold +30 %, Spanne 0 % bis ...
        val z = Szenarien.rechnen(Szenario.GOLD, 30.0, listOf(posten("A")), mapOf("A" to Messung(mapOf(Faktor.GOLD to nah))))
        assertEquals(0.0, z.von, 0.0)
        assertEquals(exp(0.9 * ln(1.3)) - 1.0, z.bis, 1e-12)
        // Gold faellt: dieselbe Steigung wirkt ins Minus, die Grenzen vertauschen sich
        val f = Szenarien.rechnen(Szenario.GOLD, -30.0, listOf(posten("A")), mapOf("A" to Messung(mapOf(Faktor.GOLD to nah))))
        assertEquals(exp(0.9 * ln(0.7)) - 1.0, f.von, 1e-12)
        assertEquals(0.0, f.bis, 0.0)
    }

    @Test(timeout = 20_000)
    fun positionOhneBelegZaehltNull() {
        val n = 400
        val me = Szenarien.messen(reihe(zufall(n, 31)), reihe(zufall(n, 32)), mapOf(Faktor.OEL to reihe(zufall(n, 33))))
        assertNull(me.steigungen[Faktor.OEL])
        val z = Szenarien.rechnen(Szenario.OEL, 30.0, listOf(posten("A"), posten("B")), mapOf("A" to me, "B" to messung(Faktor.OEL, 0.5, 0.4, 0.6)))
        assertEquals(0.5, z.belegt, 1e-9)
        assertEquals(1, z.beitraege.size)
        assertEquals("B", z.beitraege[0].posten.symbol)
        val nurA = Szenarien.rechnen(Szenario.OEL, 30.0, listOf(posten("A")), mapOf("A" to me))
        assertEquals(0.0, nurA.von, 0.0)
        assertEquals(0.0, nurA.bis, 0.0)
        assertEquals(0.0, nurA.belegt, 0.0)
        assertTrue(nurA.beitraege.isEmpty())
    }

    @Test(timeout = 20_000)
    fun positionOhneKerzenHatKeineMessung() {
        val z = Szenarien.rechnen(Szenario.GOLD, 30.0, listOf(posten("X")), emptyMap())
        assertEquals(0.0, z.von, 0.0)
        assertEquals(0.0, z.bis, 0.0)
        assertEquals(0.0, z.belegt, 0.0)
    }

    @Test(timeout = 20_000)
    fun gewichteUndVorzeichenImDepot() {
        val ps = listOf(posten("A", 300.0), posten("B", 100.0))
        val me = mapOf("A" to messung(Faktor.GOLD, 1.5, 1.4, 1.6), "B" to messung(Faktor.GOLD, -0.2, -0.1, -0.3))
        val z = Szenarien.rechnen(Szenario.GOLD, 30.0, ps, me)
        val x = ln(1.3)
        val aLo = exp(1.4 * x) - 1.0
        val aHi = exp(1.6 * x) - 1.0
        val bLo = exp(-0.3 * x) - 1.0
        val bHi = exp(-0.1 * x) - 1.0
        assertEquals(0.75 * aLo + 0.25 * bLo, z.von, 1e-12)
        assertEquals(0.75 * aHi + 0.25 * bHi, z.bis, 1e-12)
        assertEquals(1.0, z.belegt, 1e-12)
        // groesster Beitrag zuerst
        assertEquals("A", z.beitraege[0].posten.symbol)
        // Gold faellt: das Vorzeichen dreht sich
        val faellt = Szenarien.rechnen(Szenario.GOLD, -30.0, ps, me)
        assertTrue(faellt.bis < 0.0 || faellt.von < 0.0)
        assertTrue(faellt.von < z.von)
    }

    @Test(timeout = 20_000)
    fun zinsenUndDollarSindNichtBerechenbar() {
        assertTrue(!Szenario.ZINS.berechenbar)
        assertTrue(!Szenario.DOLLAR.berechenbar)
        val me = mapOf("A" to messung(Faktor.ZINS10J, -0.05, -0.04, -0.06))
        val z = Szenarien.rechnen(Szenario.ZINS, 1.0, listOf(posten("A")), me)
        assertEquals(0.0, z.von, 0.0)
        assertEquals(0.0, z.bis, 0.0)
        assertEquals(0.0, z.belegt, 0.0)
        assertTrue(z.beitraege.isEmpty())
        // betroffen: Position mit bisherigem Zusammenhang und Richtung
        val b = Szenarien.betroffene(Szenario.ZINS, listOf(posten("A", 300.0), posten("B", 100.0)), me, "EUR")!!
        assertEquals(listOf("A"), b.betroffen.map { it.symbol })
        assertEquals(-1, b.betroffen[0].richtung)
        assertEquals(0.75, b.anteil, 1e-12)
        // berechenbare Szenarien haben keine Liste ohne Zahl
        assertNull(Szenarien.betroffene(Szenario.OEL, listOf(posten("A")), me, "EUR"))
    }

    @Test(timeout = 20_000)
    fun dollarZeigtPositionenMitDollarAnteil() {
        val usd = posten("US", 100.0, waehrung = mapOf("USD" to 1.0))
        val halb = posten("MIX", 100.0, waehrung = mapOf("USD" to 0.5, "EUR" to 0.5))
        val eur = posten("DE", 200.0)
        val b = Szenarien.betroffene(Szenario.DOLLAR, listOf(usd, halb, eur), emptyMap(), "EUR")!!
        assertEquals(listOf("US", "MIX"), b.betroffen.map { it.symbol })
        assertEquals(1.0, b.betroffen[0].dollar, 1e-12)
        assertEquals(0.5, b.betroffen[1].dollar, 1e-12)
        assertEquals(0.5, b.anteil, 1e-12)
        // Dollar als Heimatwaehrung: der Anteil in anderen Waehrungen zaehlt
        assertEquals(0.0, Szenarien.dollarAnteil(usd.waehrung, "USD"), 0.0)
        assertEquals(1.0, Szenarien.dollarAnteil(eur.waehrung, "USD"), 1e-12)
        assertEquals(0.5, Szenarien.dollarAnteil(halb.waehrung, "EUR"), 1e-12)
        // Anteile werden normiert
        assertEquals(1.0, Szenarien.dollarAnteil(mapOf("USD" to 2.0), "EUR"), 1e-12)
    }

    @Test(timeout = 20_000)
    fun depotNurAusBargeldOderLeer() {
        val leer = Szenarien.rechnen(Szenario.OEL, 30.0, emptyList(), emptyMap())
        assertEquals(0.0, leer.von, 0.0)
        assertEquals(0.0, leer.belegt, 0.0)
        assertTrue(leer.beitraege.isEmpty())
        assertNull(Szenarien.betroffene(Szenario.DOLLAR, emptyList(), emptyMap(), "EUR"))
        // Bargeld und Derivate sind keine messbaren Posten
        val nurBar = Szenarien.rechnen(Szenario.OEL, 30.0, listOf(posten("BAR", 500.0, messbar = false)), mapOf("BAR" to messung(Faktor.OEL, 1.0, 1.0, 1.0)))
        assertEquals(0.0, nurBar.von, 0.0)
        assertEquals(0.0, nurBar.bis, 0.0)
        assertEquals(0.0, nurBar.belegt, 0.0)
        // Nicht messbare Posten bleiben im Nenner: der belegte Anteil bezieht sich auf alle Posten
        val mit = Szenarien.rechnen(Szenario.OEL, 30.0, listOf(posten("A", 100.0), posten("D", 100.0, messbar = false)), mapOf("A" to messung(Faktor.OEL, 0.5, 0.5, 0.5)))
        assertEquals(0.5, mit.belegt, 1e-12)
        assertEquals(0.5 * (exp(0.5 * ln(1.3)) - 1.0), mit.von, 1e-12)
    }

    @Test(timeout = 20_000)
    fun starkerFallBleibtEndlich() {
        val z = Szenarien.rechnen(Szenario.OEL, -100.0, listOf(posten("A")), mapOf("A" to messung(Faktor.OEL, 0.5, 0.4, 0.6)))
        assertTrue(z.von.isFinite() && z.bis.isFinite())
        assertTrue(z.von >= -1.0)
    }

    @Test(timeout = 20_000)
    fun anzeigeReihenfolge() {
        assertEquals(-0.02 to -0.05, Szenarien.anzeigeReihenfolge(-0.05, -0.02))
        assertEquals(0.02 to 0.05, Szenarien.anzeigeReihenfolge(0.02, 0.05))
        assertEquals(-0.01 to 0.03, Szenarien.anzeigeReihenfolge(-0.01, 0.03))
    }

    private val heute = 20_400L

    @Test(timeout = 20_000)
    fun ereignisseZeigenBetroffeneOhneZahl() {
        val i = Einflussverzeichnis.aufbauen(null, null)
        val bestand = listOf(
            Bestand(Wertbild("NVDA", null, "US"), "Nvidia", 0.3),
            Bestand(Wertbild("XOM", null, "US"), "Exxon", 0.2),
            Bestand(Wertbild("JPM", null, "US"), "Bank", 0.5),
        )
        val tw = Szenarien.ereignis(Szenarien.Ereignis.TAIWAN, i, bestand, heute)
        assertEquals(listOf("NVDA"), tw.betroffen.map { it.symbol })
        assertEquals(0.3, tw.anteil, 1e-12)
        assertEquals(Einflussverzeichnis.Art.ABGELEITET, tw.betroffen[0].art)
        val ho = Szenarien.ereignis(Szenarien.Ereignis.HORMUS, i, bestand, heute)
        assertEquals(listOf("XOM"), ho.betroffen.map { it.symbol })
        val leer = Szenarien.ereignis(Szenarien.Ereignis.TAIWAN, i, emptyList(), heute)
        assertTrue(leer.betroffen.isEmpty())
        assertEquals(0.0, leer.anteil, 0.0)
    }

    @Test(timeout = 20_000)
    fun embargoTrifftBranchenMitKonzentriertenRohstoffen() {
        val roh = """{"v":1,"stand":"2026-11-10","jahr":2024,"quelle":"USGS","rohstoffe":[
            {"id":"GALLIUM","de":"Gallium","en":"Gallium","l":[{"c":"CN","p":99}],"k":1,"s":1},
            {"id":"KUPFER","de":"Kupfer","en":"Copper","l":[{"c":"CL","p":23},{"c":"CD","p":14},{"c":"PE","p":11}],"k":1,"s":1}],
            "branchen":{"ELEKTRO":["GALLIUM","KUPFER"],"BAU":["KUPFER"]}}"""
        val i = Einflussverzeichnis.aufbauen(null, roh)
        val bestand = listOf(
            Bestand(Wertbild("CHIP", "ELEKTRO", "US"), "Chip", 0.4),
            Bestand(Wertbild("BAU1", "BAU", "DE"), "Bau", 0.4),
            Bestand(Wertbild("OHNE", null, "US"), "Ohne", 0.2),
        )
        val z = Szenarien.ereignis(Szenarien.Ereignis.EMBARGO, i, bestand, heute)
        assertEquals(listOf("CHIP"), z.betroffen.map { it.symbol })
        assertEquals(listOf("GALLIUM"), z.betroffen[0].rohstoffe.map { it.id })
        assertEquals(0.4, z.anteil, 1e-12)
    }

    @Test(timeout = 20_000)
    fun szenarioStufenEnthaltenDieVorgabe() {
        for (s in Szenario.entries) assertTrue(s.name, s.standard in s.stufen)
        assertEquals(listOf(-30.0, 30.0, 60.0), Szenario.OEL.stufen)
        assertEquals(listOf(-1.0, 1.0, 2.0), Szenario.ZINS.stufen)
        assertEquals(listOf(-20.0, -10.0, 10.0), Szenario.DOLLAR.stufen)
    }

    @Test(timeout = 20_000)
    fun wechselkursNimmtDenScheinzusammenhangHeraus() {
        // Der Wert haengt nur am Wechselkurs, Gold laeuft zur Haelfte mit dem Wechselkurs: ohne ihn wirkt Gold belegt
        val n = 2000
        val m = zufall(n, 71)
        val fx = zufall(n, 72)
        val eigen = zufall(n, 73)
        val r = zufall(n, 74, 0.004)
        val gold = DoubleArray(n) { 0.7 * fx[it] + 0.7 * eigen[it] }
        val y = DoubleArray(n) { 0.5 * m[it] + 0.9 * fx[it] + r[it] }
        val ohne = Szenarien.messen(reihe(y), reihe(m), mapOf(Faktor.GOLD to reihe(gold)))
        assertNotNull(ohne.steigungen[Faktor.GOLD])
        val mit = Szenarien.messen(reihe(y), reihe(m), mapOf(Faktor.GOLD to reihe(gold)), reihe(fx))
        assertNull(mit.steigungen[Faktor.GOLD])
    }

    @Test(timeout = 20_000)
    fun wechselkursLaesstEchtenZusammenhangStehen() {
        val n = 2000
        val m = zufall(n, 81)
        val fx = zufall(n, 82)
        val gold = zufall(n, 83)
        val r = zufall(n, 84, 0.004)
        val y = DoubleArray(n) { 0.5 * m[it] + 0.3 * fx[it] + 1.2 * gold[it] + r[it] }
        val mit = Szenarien.messen(reihe(y), reihe(m), mapOf(Faktor.GOLD to reihe(gold)), reihe(fx))
        assertEquals(1.2, mit.steigungen[Faktor.GOLD]!!.gesamt, 0.1)
    }

    // Eigener Wert (Regler): die Rechnung gilt fuer jeden Wert in den Grenzen, nicht nur fuer die Vorgaben
    @Test(timeout = 20_000)
    fun beliebigeWerteInDenGrenzen() {
        val st = Steigung(0.4, 0.1, 0.3 to 0.5)
        val m = mapOf("A" to Messung(mapOf(Faktor.OEL to st)))
        for (v in listOf(-60.0, -45.0, -10.0, 0.0, 15.0, 45.0, 100.0)) {
            val z = Szenarien.rechnen(Szenario.OEL, v, listOf(posten("A")), m)
            val x = ln(1.0 + v / 100.0)
            val (lo, hi) = st.grenzen
            assertEquals(minOf(exp(lo * x), exp(hi * x)) - 1.0, z.von, 1e-12)
            assertEquals(maxOf(exp(lo * x), exp(hi * x)) - 1.0, z.bis, 1e-12)
            assertEquals(v, z.schock, 0.0)
        }
        // je weiter der Wert nach oben geht, desto groesser die Wirkung bei positiver Steigung
        val a = Szenarien.rechnen(Szenario.OEL, 20.0, listOf(posten("A")), m).bis
        val b = Szenarien.rechnen(Szenario.OEL, 80.0, listOf(posten("A")), m).bis
        assertTrue(b > a && a > 0.0)
    }

    @Test(timeout = 20_000)
    fun grenzenDerEigenenWerte() {
        assertEquals(-60.0, Szenarien.begrenzen(Szenario.OEL, -90.0), 0.0)
        assertEquals(100.0, Szenarien.begrenzen(Szenario.KUPFER, 250.0), 0.0)
        assertEquals(-3.0, Szenarien.begrenzen(Szenario.ZINS, -7.0), 0.0)
        assertEquals(3.0, Szenarien.begrenzen(Szenario.ZINS, 3.0), 0.0)
        assertEquals(-30.0, Szenarien.begrenzen(Szenario.DOLLAR, -50.0), 0.0)
        assertEquals(30.0, Szenarien.begrenzen(Szenario.DOLLAR, 99.0), 0.0)
        assertEquals(30.0, Szenarien.begrenzen(Szenario.GOLD, Double.NaN), 0.0)
        // alle Vorgaben liegen innerhalb der Grenzen
        Szenario.entries.forEach { s -> s.stufen.forEach { assertTrue(it >= s.min && it <= s.max) } }
    }

    @Test(timeout = 20_000)
    fun dollarUmrechnungFuerBeliebigenWert() {
        val p = listOf(posten("A", 100.0, waehrung = mapOf("USD" to 1.0)))
        val a = Szenarien.dollarUmrechnung(p, "EUR", -25.0)!!
        val b = Szenarien.dollarUmrechnung(p, "EUR", 25.0)!!
        assertEquals(-0.25, a, 1e-12)
        assertEquals(0.25, b, 1e-12)
    }
}
