package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Empfindlichkeit.Faktor
import de.troetenrepublik.app.engine.Empfindlichkeit.Urteil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.exp

// Kursempfindlichkeit: kuenstliche Reihen bekannter Abhaengigkeit und die Reihen dreier echter Werte (Yahoo, Wochen bis
// 11/2026; Referenzwerte mit numpy nach derselben Vorschrift gerechnet).
class EmpfindlichkeitTest {
    private val tagMs = 86_400_000L

    // Freitag 8.1.2016 = Tag 16808; je Woche ein Freitag als Wochenschluss
    private val ersterFreitag = 16_808L

    private fun reihe(renditen: DoubleArray, freitagStunde: Int = 12, start: Double = 100.0): List<Candle> {
        var kurs = start
        val aus = ArrayList<Candle>()
        for (i in 0..renditen.size) {
            if (i > 0) kurs *= exp(renditen[i - 1])
            val freitag = (ersterFreitag + 7L * i) * tagMs + freitagStunde * 3_600_000L
            // Mittwoch mit anderem Kurs: zaehlt nicht als Wochenschluss
            aus += Candle(freitag - 2 * tagMs, kurs * 1.3, kurs * 1.3, kurs * 1.3, kurs * 1.3)
            aus += Candle(freitag, kurs, kurs, kurs, kurs)
        }
        return aus
    }

    private fun zufall(n: Int, seed: Long, sigma: Double = 0.02): DoubleArray = Random(seed).let { r -> DoubleArray(n) { r.nextGaussian() * sigma } }

    private val seit = 0L

    @Test(timeout = 20_000)
    fun bekannteAbhaengigkeitWirdGefunden() {
        val n = 400
        val m = zufall(n, 1)
        val rauschen = zufall(n, 2, 0.01)
        val y = DoubleArray(n) { 1.5 * m[it] + rauschen[it] }
        val e = Empfindlichkeit.rechnen(reihe(y), reihe(m), emptyMap(), seit)!!
        val k = e.markt!!
        assertEquals(1.5, k.wert, 0.12)
        assertEquals(Urteil.BELEGT, k.urteil)
        assertEquals(n, e.wochen)
        assertTrue(e.r2 > 0.8)
        assertEquals(e.r2, e.r2Markt, 1e-9)
        assertEquals(Empfindlichkeit.Aussage.LAEUFT_MIT, Empfindlichkeit.aussage(k))
    }

    @Test(timeout = 20_000)
    fun gegenlaeufigeGroesse() {
        val n = 400
        val m = zufall(n, 3)
        val oel = zufall(n, 4)
        val r = zufall(n, 5, 0.005)
        val y = DoubleArray(n) { 0.8 * m[it] - 0.6 * oel[it] + r[it] }
        val e = Empfindlichkeit.rechnen(reihe(y), reihe(m), mapOf(Faktor.OEL to reihe(oel), Faktor.GOLD to reihe(zufall(n, 6))), seit)!!
        val k = e.koeffizienten.first { it.faktor == Faktor.OEL }
        assertEquals(-0.6, k.wert, 0.1)
        assertEquals(Urteil.BELEGT, k.urteil)
        assertEquals(Empfindlichkeit.Aussage.LAEUFT_GEGEN, Empfindlichkeit.aussage(k))
        // Gold hat keinen Anteil: bleibt ausserhalb des Modells
        assertTrue(Faktor.GOLD in e.ohneBeleg)
        assertTrue(e.r2 > e.r2Markt)
    }

    @Test(timeout = 20_000)
    fun unabhaengigeReiheHatKeinenBeleg() {
        val n = 400
        val e = Empfindlichkeit.rechnen(
            reihe(zufall(n, 10)), reihe(zufall(n, 11)),
            mapOf(Faktor.OEL to reihe(zufall(n, 12)), Faktor.DOLLAR to reihe(zufall(n, 13))), seit,
        )!!
        assertTrue(e.r2 < 0.1)
        assertTrue(Empfindlichkeit.belegt(e).isEmpty())
        assertTrue(e.markt!!.urteil != Urteil.BELEGT)
    }

    @Test(timeout = 20_000)
    fun wechselndesVorzeichenWirdGemeldet() {
        val n = 400
        val m = zufall(n, 20)
        val r = zufall(n, 21, 0.004)
        val y = DoubleArray(n) { (if (it < n / 2) 1.0 else -1.0) * m[it] + r[it] }
        val e = Empfindlichkeit.rechnen(reihe(y), reihe(m), emptyMap(), seit)!!
        val k = e.markt!!
        assertEquals(Urteil.WECHSELT, k.urteil)
        assertEquals(Empfindlichkeit.Aussage.WECHSELT, Empfindlichkeit.aussage(k))
        assertTrue(k.haelften.first > 0.8 && k.haelften.second < -0.8)
    }

    @Test(timeout = 20_000)
    fun zinsenInPunktenNichtInProzent() {
        val n = 400
        val m = zufall(n, 30)
        val z = zufall(n, 31, 0.1)
        val r = zufall(n, 32, 0.004)
        val y = DoubleArray(n) { 0.3 * m[it] + 0.05 * z[it] + r[it] }
        // Zinsreihe als Stand in Prozent: die Aenderung in Punkten zaehlt, nicht die Log-Rendite
        var stand = 4.0
        val zk = ArrayList<Candle>()
        for (i in 0..n) {
            if (i > 0) stand += z[i - 1]
            val f = (ersterFreitag + 7L * i) * tagMs + 12 * 3_600_000L
            zk += Candle(f, stand + 20, stand + 20, stand + 20, stand + 20)
        }
        val e = Empfindlichkeit.rechnen(reihe(y), reihe(m), mapOf(Faktor.ZINS10J to zk), seit)!!
        val k = e.koeffizienten.first { it.faktor == Faktor.ZINS10J }
        assertEquals(0.05, k.wert, 0.015)
        assertEquals(Urteil.BELEGT, k.urteil)
    }

    @Test(timeout = 20_000)
    fun verschiedeneSchlusszeitenGehoerenZurSelbenWoche() {
        // Xetra schliesst Freitag 15:30 UTC, New York 21:00 UTC (mit dem Versatz der App schon nach Mitternacht)
        val n = 300
        val m = zufall(n, 40)
        val e = Empfindlichkeit.rechnen(reihe(m.copyOf(), freitagStunde = 15), reihe(m, freitagStunde = 21), emptyMap(), seit)!!
        assertEquals(1.0, e.markt!!.wert, 1e-6)
        assertEquals(1.0, e.r2, 1e-6)
        assertEquals(n, e.wochen)
    }

    @Test(timeout = 20_000)
    fun zuKurzeReiheGibtNichts() {
        val n = 60
        assertNull(Empfindlichkeit.rechnen(reihe(zufall(n, 1)), reihe(zufall(n, 2)), emptyMap(), seit))
        assertNull(Empfindlichkeit.rechnen(emptyList(), emptyList(), emptyMap(), seit))
    }

    @Test(timeout = 20_000)
    fun seitSchneidetDenZeitraumAb() {
        val n = 300
        val m = zufall(n, 50)
        val e = Empfindlichkeit.rechnen(reihe(m), reihe(m), emptyMap(), (ersterFreitag + 7L * 100) * tagMs)!!
        assertTrue(e.wochen in 195..200)
    }

    // ---- echte Werte ----

    private fun fixture(): Map<String, List<Candle>> {
        val text = javaClass.getResourceAsStream("/empfindlichkeit_fixture.json")!!.readBytes().decodeToString()
        return Json.parseToJsonElement(text).jsonObject.mapValues { (_, v) ->
            v.jsonArray.map { p ->
                val c = p.jsonArray[1].jsonPrimitive.content.toDouble()
                Candle(p.jsonArray[0].jsonPrimitive.content.toLong() * tagMs + 12 * 3_600_000L, c, c, c, c)
            }
        }
    }

    private fun echt(name: String): Empfindlichkeit.Ergebnis {
        val f = fixture()
        val weitere = mapOf(
            Faktor.HALBLEITER to f.getValue("sox"), Faktor.OEL to f.getValue("oel"), Faktor.DOLLAR to f.getValue("dollar"),
            Faktor.ZINS10J to f.getValue("zins"), Faktor.KUPFER to f.getValue("kupfer"), Faktor.GOLD to f.getValue("gold"),
            Faktor.CHINA to f.getValue("china"),
        )
        return Empfindlichkeit.rechnen(f.getValue(name), f.getValue("markt"), weitere, 0L)!!
    }

    private fun k(e: Empfindlichkeit.Ergebnis, f: Faktor) = e.koeffizienten.first { it.faktor == f }

    @Test(timeout = 30_000)
    fun nvidiaLaeuftMitDemHalbleiterindex() {
        val e = echt("nvda")
        assertEquals(553, e.wochen)
        assertEquals(0.591091, e.r2, 1e-4)
        assertEquals(0.415533, e.r2Markt, 1e-4)
        assertEquals(0.548102, e.r2Haelften.first, 1e-4)
        assertEquals(0.638770, e.r2Haelften.second, 1e-4)
        assertEquals(listOf(Faktor.MARKT, Faktor.HALBLEITER), e.koeffizienten.map { it.faktor })
        val sox = k(e, Faktor.HALBLEITER)
        assertEquals(0.995516, sox.wert, 1e-4)
        assertEquals(15.3666, sox.t, 1e-2)
        assertEquals(Urteil.BELEGT, sox.urteil)
        // Der Markt wechselt zwischen den Haelften das Vorzeichen
        val markt = k(e, Faktor.MARKT)
        assertEquals(0.288084, markt.wert, 1e-4)
        assertEquals(-0.065780, markt.haelften.first, 1e-4)
        assertEquals(0.589486, markt.haelften.second, 1e-4)
        assertEquals(Urteil.WECHSELT, markt.urteil)
        assertTrue(Faktor.DOLLAR in e.ohneBeleg && Faktor.OEL in e.ohneBeleg && Faktor.ZINS10J in e.ohneBeleg)
    }

    @Test(timeout = 30_000)
    fun volkswagenLaeuftMitDemMarkt() {
        val e = echt("vow")
        assertEquals(0.263572, e.r2, 1e-4)
        assertEquals(0.218643, e.r2Markt, 1e-4)
        val markt = k(e, Faktor.MARKT)
        assertEquals(0.774944, markt.wert, 1e-4)
        assertEquals(Urteil.BELEGT, markt.urteil)
        assertEquals(0.932320, markt.haelften.first, 1e-4)
        assertEquals(0.539255, markt.haelften.second, 1e-4)
        // Dollar: gesamt auffaellig, in der ersten Haelfte aber nicht (t -1,5): kein belegter Zusammenhang
        val dollar = k(e, Faktor.DOLLAR)
        assertEquals(-0.688763, dollar.wert, 1e-4)
        assertEquals(Urteil.NICHT_BELEGT, dollar.urteil)
        // China: in der ersten Haelfte nicht belegt
        assertEquals(Urteil.NICHT_BELEGT, k(e, Faktor.CHINA).urteil)
        // Oel kommt gar nicht ins Modell
        assertTrue(Faktor.OEL in e.ohneBeleg)
    }

    @Test(timeout = 30_000)
    fun alphaminLaeuftMitGold() {
        val e = echt("afm")
        assertEquals(0.089349, e.r2, 1e-4)
        assertEquals(0.038900, e.r2Markt, 1e-4)
        val gold = k(e, Faktor.GOLD)
        assertEquals(0.799509, gold.wert, 1e-4)
        assertEquals(Urteil.BELEGT, gold.urteil)
        assertNotNull(Empfindlichkeit.belegt(e).firstOrNull { it.faktor == Faktor.GOLD })
        // Markt: nur in der zweiten Haelfte klar
        assertEquals(Urteil.NICHT_BELEGT, k(e, Faktor.MARKT).urteil)
        // Bestimmtheitsmass klein: die Anzeige darf es nicht verschweigen
        assertTrue(e.r2 < 0.1)
    }
}
