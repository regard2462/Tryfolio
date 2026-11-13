package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// Unterwasser, Renditeverteilung, rollierende Kennzahlen, Rendite-Risiko-Punkte und Beschriftungsplatz,
// gegen von Hand nachgerechnete Reihen.
class Fassung073RisikoDiagrammeTest {

    private val tag = 86_400_000L
    private val t0 = 1_700_000_000_000L

    private fun reihe(vararg werte: Double) = werte.mapIndexed { i, v -> (t0 + i * tag) to v }

    // Unterwasser

    @Test
    fun unterwasserZeigtAbstandZumBisherigenHoch() {
        // Hoch 100, 110; dann 99 = -10 % unter 110; neues Hoch 121; 60,5 = -50 %. Der Wert 0 faellt weg.
        val r = reihe(100.0, 110.0, 99.0, 121.0, 121.0, 60.5, 0.0)
        val dd = RisikoDiagramme.unterwasser(r).map { it.second }
        assertEquals(6, dd.size)
        val soll = listOf(0.0, 0.0, -10.0, 0.0, 0.0, -50.0)
        soll.forEachIndexed { i, v -> assertEquals(v, dd[i], 1e-9) }
    }

    @Test
    fun unterwasserSortiertUndLaesstLeereReiheLeer() {
        val r = reihe(100.0, 90.0, 95.0).reversed()
        assertEquals(listOf(0.0, -10.0, -5.0), RisikoDiagramme.unterwasser(r).map { Math.round(it.second * 1e6) / 1e6 })
        assertTrue(RisikoDiagramme.unterwasser(emptyList()).isEmpty())
    }

    @Test
    fun unterwasserEndetImTiefstenPunktWieDerGroessteRueckgang() {
        val r = reihe(100.0, 120.0, 84.0, 90.0)
        // 84 / 120 - 1 = -30 %
        assertEquals(-30.0, RisikoDiagramme.unterwasser(r).minOf { it.second }, 1e-9)
    }

    // Klassenbreite und Histogramm

    @Test
    fun schoeneBreiteRundetAufEineStufe() {
        // 0,06 / 24 = 0,0025 liegt schon auf einer Stufe
        assertEquals(0.0025, RisikoDiagramme.schoeneBreite(0.06), 1e-12)
        // 0,5 / 24 = 0,0208 -> 0,025
        assertEquals(0.025, RisikoDiagramme.schoeneBreite(0.5), 1e-12)
        // 1,2 / 24 = 0,05
        assertEquals(0.05, RisikoDiagramme.schoeneBreite(1.2), 1e-12)
        // 0,3 / 24 = 0,0125 -> 0,02
        assertEquals(0.02, RisikoDiagramme.schoeneBreite(0.3), 1e-12)
        assertEquals(0.0, RisikoDiagramme.schoeneBreite(0.0), 0.0)
    }

    @Test
    fun histogrammZaehltKlassenUndRaender() {
        val werte = listOf(-0.025, -0.01, 0.0, 0.005, 0.0099, 0.01, 0.02, 0.06)
        val h = RisikoDiagramme.histogramm(werte, breite = 0.01, minimum = 1)!!
        // 1. Prozentpunkt -0,02395 -> Rand -0,03; 99. Prozentpunkt 0,0572 -> Rand 0,06; neun Klassen
        assertEquals(-0.03, h.untergrenze, 1e-12)
        assertEquals(0.06, h.obergrenze, 1e-12)
        assertEquals(9, h.anzahl.size)
        // [-3,-2) [-2,-1) [-1,0) [0,1) [1,2) [2,3) [3,4) [4,5) [5,6) in Prozent; 0,06 liegt auf dem oberen Rand
        // und zaehlt als Ueberlauf in der letzten Klasse, -0,01 auf dem Rand gehoert in die obere Klasse [-1, 0)
        assertEquals(listOf(1, 0, 1, 3, 1, 1, 0, 0, 1), h.anzahl)
        assertEquals(8, h.n)
        assertEquals(0, h.unterlauf)
        assertEquals(1, h.ueberlauf)
    }

    @Test
    fun histogrammSchliesstVarUndEsEin() {
        val werte = listOf(-0.025, -0.01, 0.0, 0.005, 0.0099, 0.01, 0.02, 0.06)
        val h = RisikoDiagramme.histogramm(werte, breite = 0.01, minimum = 1, einschliessen = listOf(-0.045))!!
        assertEquals(-0.05, h.untergrenze, 1e-12)
        assertEquals(11, h.anzahl.size)
        assertEquals(8, h.n)
    }

    @Test
    fun histogrammAusreisserLandenInDenAeusserenKlassen() {
        // 98 Werte um null, ein Ausreisser nach unten und einer nach oben: der Bereich folgt dem 1. und 99. Prozentpunkt
        val mitte = (0 until 98).map { (it - 49) * 0.0002 }
        val h = RisikoDiagramme.histogramm(mitte + listOf(-0.30, 0.30))!!
        assertEquals(100, h.n)
        assertTrue(h.unterlauf >= 1)
        assertTrue(h.ueberlauf >= 1)
        assertTrue(h.untergrenze > -0.30)
        assertTrue(h.obergrenze < 0.30)
        // die Null liegt auf einem Klassenrand
        val k = h.untergrenze / h.breite
        assertEquals(Math.rint(k), k, 1e-6)
    }

    @Test
    fun histogrammBrauchtGenugRenditen() {
        assertNull(RisikoDiagramme.histogramm(List(99) { it * 0.001 }))
        assertNotNull(RisikoDiagramme.histogramm(List(100) { it * 0.001 }))
        assertNull(RisikoDiagramme.histogramm(List(120) { 0.0 }))
    }

    // Rollierende Rendite

    @Test
    fun rollierendeRenditeRechnetGegenDenTagVorEinemJahr() {
        val r = (0 until 400).map { (t0 + it * tag) to 100.0 * 1.001.pow(it) }
        val roll = RisikoDiagramme.rollierendeRendite(r)
        // erst ab dem 366. Punkt (Tag 365) gibt es einen Wert 365 Tage davor
        assertEquals(35, roll.size)
        assertEquals(t0 + 365 * tag, roll.first().first)
        assertEquals(1.001.pow(365) - 1.0, roll.first().second, 1e-9)
        assertEquals(1.001.pow(365) - 1.0, roll.last().second, 1e-9)
    }

    @Test
    fun rollierendeRenditeBeiZuKurzerReiheLeer() {
        val r = (0 until 365).map { (t0 + it * tag) to 100.0 + it }
        assertTrue(RisikoDiagramme.rollierendeRendite(r).isEmpty())
        assertTrue(RisikoDiagramme.rollierendeRendite(emptyList()).isEmpty())
    }

    @Test
    fun rollierendeRenditeUeberbrueckteLueckenNurBisZurToleranz() {
        // Tage 0 bis 99 und 200 bis 599: Fuer Tag i liegt der Bezugstag bei i - 365
        val tage = (0..99) + (200..599)
        val roll = RisikoDiagramme.rollierendeRendite(tage.map { (t0 + it * tag) to 100.0 + it })
        val tageMitWert = roll.map { ((it.first - t0) / tag).toInt() }
        // bis Tag 471 reicht der letzte Punkt vor dem Bezugstag (Tag 99) noch 7 Tage heran, danach fehlt der Wert
        assertEquals((365..471).toList() + (565..599).toList(), tageMitWert)
        // Tag 470: Bezugstag 105, nahester Punkt davor Tag 99
        val p470 = roll.first { (it.first - t0) / tag == 470L }.second
        assertEquals((100.0 + 470) / (100.0 + 99) - 1.0, p470, 1e-12)
    }

    // Rollierende Schwankung

    @Test
    fun rollierendeSchwankungEntsprichtDerHandrechnung() {
        // Renditen +10 %, -10 %, +10 %: ln 1,1 = a, ln 0,9 = b. Mittel (2a + b) / 3, Abweichungen (a - b) / 3 und
        // -2 (a - b) / 3, Varianz mit n - 1 = (a - b)^2 / 3, also Schwankung |a - b| / Wurzel 3 = ln(11 / 9) / Wurzel 3
        val r = reihe(100.0, 110.0, 99.0, 108.9)
        val soll = ln(11.0 / 9.0) / sqrt(3.0)
        val roll = RisikoDiagramme.rollierendeSchwankung(r, fenster = 3, proJahr = 1.0)
        assertEquals(1, roll.size)
        assertEquals(soll, roll[0].second, 1e-12)
        assertEquals(t0 + 3 * tag, roll[0].first)
        // auf ein Jahr mit 252 Tagen hochgerechnet
        assertEquals(soll * sqrt(252.0), RisikoDiagramme.rollierendeSchwankung(r, fenster = 3).first().second, 1e-12)
    }

    @Test
    fun rollierendeSchwankungGleitetDasFensterWeiter() {
        val r = reihe(100.0, 110.0, 99.0, 108.9, 98.01)
        val roll = RisikoDiagramme.rollierendeSchwankung(r, fenster = 3, proJahr = 1.0)
        // zweites Fenster: -10 %, +10 %, -10 % hat dieselbe Streuung
        assertEquals(2, roll.size)
        assertEquals(roll[0].second, roll[1].second, 1e-12)
    }

    @Test
    fun rollierendeSchwankungMitLueckeOderZuKurzerReiheLeer() {
        // Tage 0, 1, 2, dann 20 Tage Pause: kein Fenster mit drei Renditen ohne Luecke
        val mitLuecke = listOf(0, 1, 2, 22, 23, 24).map { (t0 + it * tag) to 100.0 + it }
        assertTrue(RisikoDiagramme.rollierendeSchwankung(mitLuecke, fenster = 3).isEmpty())
        // eine Luecke von zehn Tagen ist noch erlaubt
        val zehn = listOf(0, 1, 2, 12, 13).map { (t0 + it * tag) to 100.0 + it }
        assertEquals(2, RisikoDiagramme.rollierendeSchwankung(zehn, fenster = 3).size)
        // zu kurz: drei Punkte ergeben nur zwei Renditen
        assertTrue(RisikoDiagramme.rollierendeSchwankung(reihe(100.0, 101.0, 102.0), fenster = 3).isEmpty())
        // 60 Tage Fenster, 60 Punkte: nur 59 Renditen
        assertTrue(RisikoDiagramme.rollierendeSchwankung((0 until 60).map { (t0 + it * tag) to 100.0 + it }).isEmpty())
        assertEquals(1, RisikoDiagramme.rollierendeSchwankung((0 until 61).map { (t0 + it * tag) to 100.0 + it }).size)
    }

    @Test
    fun rollierendeSchwankungUeberspringtUngueltigeWerte() {
        // Ein Wert 0 unterbricht die Renditen: danach braucht es wieder drei gueltige
        val r = reihe(100.0, 110.0, 99.0, 0.0, 100.0, 110.0, 99.0, 108.9)
        val roll = RisikoDiagramme.rollierendeSchwankung(r, fenster = 3, proJahr = 1.0)
        // gueltige Renditen: k = 1, 2 | 3 und 4 ungueltig | 5, 6, 7 gueltig -> ein Fenster am Punkt 7
        assertEquals(1, roll.size)
        assertEquals(t0 + 7 * tag, roll[0].first)
    }

    // Jahresrendite und Rendite-Risiko

    @Test
    fun jahresrenditeRechnetAufEinJahrHoch() {
        val zweiJahre = listOf(t0 to 100.0, t0 + (2 * 365.25 * tag).toLong() to 200.0)
        assertEquals(sqrt(2.0) - 1.0, RisikoDiagramme.jahresrendite(zweiJahre)!!, 1e-9)
        assertNull(RisikoDiagramme.jahresrendite(zweiJahre, mindestJahre = 3.0))
        val halbesJahr = listOf(t0 to 100.0, t0 + (0.5 * 365.25 * tag).toLong() to 110.0)
        assertNull(RisikoDiagramme.jahresrendite(halbesJahr))
        assertEquals(1.1.pow(2) - 1.0, RisikoDiagramme.jahresrendite(halbesJahr, 0.5)!!, 1e-9)
        assertNull(RisikoDiagramme.jahresrendite(emptyList()))
    }

    private fun zickzack(tage: Int, a: Double, wachstum: Double) =
        (0..tage).map { (t0 + it * tag) to 100.0 * wachstum.pow(it) * exp(a * (it % 2)) }

    @Test
    fun renditeRisikoAnnualisiertSchwankungUndRendite() {
        // 730 Renditen, abwechselnd +a und -a (plus 0,02 % Trend): Mittel der Abweichung 0, Varianz mit n - 1 = a^2 * 730 / 729,
        // Renditen pro Jahr 730 / (730 / 365,25) = 365,25. Rendite: gerade Endpunkte, also 1,0002 hoch 365,25 minus 1.
        val r = zickzack(730, 0.01, 1.0002)
        val rr = RisikoDiagramme.renditeRisiko(r, t0)!!
        assertEquals(0.01 * sqrt(730.0 / 729.0 * 365.25), rr.schwankung, 1e-9)
        assertEquals(1.0002.pow(365.25) - 1.0, rr.rendite, 1e-9)
    }

    @Test
    fun renditeRisikoBrauchtDenZeitraumAlsKurse() {
        val r = zickzack(730, 0.01, 1.0002)
        // Beginn 400 Tage vor dem ersten Kurs: Der Wert reicht nicht in den Zeitraum zurueck
        assertNull(RisikoDiagramme.renditeRisiko(r, t0 - 400 * tag))
        // 50 Tage Luft sind unter einem Zehntel des Zeitraums
        assertNotNull(RisikoDiagramme.renditeRisiko(r, t0 - 50 * tag))
        // zu wenige Punkte oder unter einem halben Jahr
        assertNull(RisikoDiagramme.renditeRisiko(zickzack(50, 0.01, 1.0), t0))
        assertNull(RisikoDiagramme.renditeRisiko(zickzack(150, 0.01, 1.0), t0))
    }

    // Beschriftungen

    private val flaeche = RisikoDiagramme.Rechteck(0f, 0f, 300f, 200f)

    @Test
    fun beschriftungenWeichenEinanderAus() {
        val m = listOf(
            RisikoDiagramme.Marke(100f, 100f, 5f, 30f to 10f),
            RisikoDiagramme.Marke(100f, 100f, 5f, 30f to 10f),
        )
        val p = RisikoDiagramme.beschriftungen(m, listOf(0, 1), flaeche)
        // der erste rechts, der zweite links
        assertEquals(108f to 95f, p[0])
        assertEquals(62f to 95f, p[1])
        // Reihenfolge der Prioritaet entscheidet, wer rechts steht
        val q = RisikoDiagramme.beschriftungen(m, listOf(1, 0), flaeche)
        assertEquals(108f to 95f, q[1])
        assertEquals(62f to 95f, q[0])
    }

    @Test
    fun beschriftungenBleibenInDerFlaeche() {
        val m = listOf(RisikoDiagramme.Marke(290f, 100f, 5f, 30f to 10f))
        // rechts waere ausserhalb: links
        assertEquals(252f to 95f, RisikoDiagramme.beschriftungen(m, listOf(0), flaeche)[0])
        // gar kein Platz: keine Beschriftung
        val eng = RisikoDiagramme.Rechteck(0f, 0f, 20f, 10f)
        assertTrue(RisikoDiagramme.beschriftungen(listOf(RisikoDiagramme.Marke(10f, 5f, 3f, 30f to 10f)), listOf(0), eng).isEmpty())
    }

    @Test
    fun beschriftungenMeidenAuchPunkteOhneText() {
        val m = listOf(
            RisikoDiagramme.Marke(100f, 100f, 5f, 30f to 10f),
            // ein Punkt ohne Beschriftung steht dort, wo rechts die Beschriftung laege
            RisikoDiagramme.Marke(125f, 100f, 6f, null),
        )
        val p = RisikoDiagramme.beschriftungen(m, listOf(0, 1), flaeche)
        assertEquals(62f to 95f, p[0])
        assertTrue(1 !in p)
    }

    @Test
    fun beschriftungenBeachtenHoechstzahlUndGesperrteFlaechen() {
        val m = listOf(
            RisikoDiagramme.Marke(50f, 50f, 5f, 30f to 10f),
            RisikoDiagramme.Marke(150f, 150f, 5f, 30f to 10f),
        )
        val eins = RisikoDiagramme.beschriftungen(m, listOf(1, 0), flaeche, maxAnzahl = 1)
        assertEquals(setOf(1), eins.keys)
        // gesperrt: rechts neben dem ersten Punkt
        val gesperrt = RisikoDiagramme.beschriftungen(m, listOf(0), flaeche, gesperrt = listOf(RisikoDiagramme.Rechteck(55f, 40f, 100f, 60f)))
        assertEquals((50f - 5f - 3f - 30f) to 45f, gesperrt[0])
    }

    @Test
    fun beschriftungenUeberdeckenSichNie() {
        // Viele dicht liegende Punkte: Was beschriftet wird, ueberlappt weder andere Beschriftungen noch Punkte
        val m = (0 until 30).map { i -> RisikoDiagramme.Marke(40f + (i % 6) * 9f, 40f + (i / 6) * 9f, 4f + (i % 3), 28f to 9f) }
        val p = RisikoDiagramme.beschriftungen(m, m.indices.toList(), flaeche, maxAnzahl = 8)
        assertTrue(p.size <= 8)
        val rechtecke = p.map { (i, o) -> RisikoDiagramme.Rechteck(o.first, o.second, o.first + 28f, o.second + 9f) }
        for (a in rechtecke.indices) {
            for (b in a + 1 until rechtecke.size) assertTrue(!rechtecke[a].schneidet(rechtecke[b]))
            for (mk in m) assertTrue(!rechtecke[a].schneidetKreis(mk.x, mk.y, mk.radius))
        }
    }

    @Test
    fun naechsterPunktInReichweite() {
        val m = listOf(RisikoDiagramme.Marke(10f, 10f, 4f, null), RisikoDiagramme.Marke(30f, 10f, 12f, null))
        assertEquals(0, RisikoDiagramme.naechster(m, 12f, 12f, 8f))
        // 40 liegt im Radius des zweiten Punkts, auch wenn die Reichweite kleiner ist
        assertEquals(1, RisikoDiagramme.naechster(m, 40f, 10f, 5f))
        assertNull(RisikoDiagramme.naechster(m, 100f, 100f, 20f))
    }
}
