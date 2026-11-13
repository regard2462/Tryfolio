package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.AusblickStaerke.Staerke
import de.troetenrepublik.app.engine.Tendenz.Richtung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 0.77 F: Staerke je Signal im Ausblick aus den Rueckblicken der App, Zustand und Wert in den Beitraegen.
class Fassung077FTest {

    private fun zeitraum(wochen: Int, unabhaengig: Int, treffer: Double, grund: Double, belegt: Boolean = false) =
        TendenzRueckblick.Zeitraum(wochen, unabhaengig, unabhaengig, treffer, grund, 0.0, 0.0, 0.0, belegt = belegt)

    private fun signal(status: String = TendenzRueckblick.GEPRUEFT, vararg z: TendenzRueckblick.Zeitraum) =
        TendenzRueckblick.Signal(status, gruppen = mapOf("alle" to z.toList()))

    @Test fun stufenLeiter() {
        assertEquals(0, AusblickStaerke.stufe(-0.2))
        assertEquals(0, AusblickStaerke.stufe(0.0))
        assertEquals(1, AusblickStaerke.stufe(0.03))
        assertEquals(2, AusblickStaerke.stufe(0.05))
        assertEquals(2, AusblickStaerke.stufe(0.14))
        assertEquals(3, AusblickStaerke.stufe(0.15))
        assertEquals(4, AusblickStaerke.stufe(0.30))
        assertEquals(0, AusblickStaerke.stufe(Double.NaN))
    }

    @Test fun tendenzMitteltVorsprungUeberZeitraeumeMitGenugFaellen() {
        // 52 Wochen hat zu wenige Faelle und zaehlt nicht; Mittel aus +12 und 0 Punkten
        val s = signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 40, 0.62, 0.50), zeitraum(13, 25, 0.50, 0.50), zeitraum(52, 5, 0.95, 0.5))
        val r = AusblickStaerke.ausTendenz(listOf(s))!!
        assertTrue(r.gemessen)
        assertEquals(2, r.stufe)
    }

    @Test fun tendenzOhneBelegHoechstensStufeZwei() {
        val s = signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 40, 0.90, 0.50))
        assertEquals(2, AusblickStaerke.ausTendenz(listOf(s))!!.stufe)
        val belegt = signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 40, 0.90, 0.50, belegt = true))
        assertEquals(4, AusblickStaerke.ausTendenz(listOf(belegt))!!.stufe)
    }

    @Test fun tendenzUnterGrundquoteIstStufeNull() {
        val s = signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 60, 0.45, 0.50), zeitraum(13, 30, 0.42, 0.50))
        val r = AusblickStaerke.ausTendenz(listOf(s))!!
        assertEquals(Staerke(0, true), r)
    }

    @Test fun tendenzOhnePruefungOderFaelleLiefertNull() {
        assertNull(AusblickStaerke.ausTendenz(listOf(null)))
        assertNull(AusblickStaerke.ausTendenz(listOf(TendenzRueckblick.Signal(TendenzRueckblick.ZU_KURZ))))
        assertNull(AusblickStaerke.ausTendenz(listOf(signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 19, 0.9, 0.5)))))
    }

    private fun warn(auswertbar: Int, treffer: Int, zeit: Double) =
        WarnRueckblick.Bilanz("x", 0, auswertbar = auswertbar, treffer = treffer, zeitAnteil = zeit)

    @Test fun warnungVorsprungGegenZeitanteil() {
        // 6 von 11 gegen 6 % der Tage mit Warnung: 54 - 6 = 48 Punkte
        assertEquals(Staerke(4, true), AusblickStaerke.ausWarnung(warn(11, 6, 0.06)))
        // 3 von 12 gegen 11 %: 14 Punkte
        assertEquals(Staerke(2, true), AusblickStaerke.ausWarnung(warn(12, 3, 0.11)))
        // 1 von 11 gegen 6 %: 3 Punkte
        assertEquals(Staerke(1, true), AusblickStaerke.ausWarnung(warn(11, 1, 0.06)))
        // warnt fast immer: kein Vorsprung
        assertEquals(Staerke(0, true), AusblickStaerke.ausWarnung(warn(11, 6, 0.6)))
    }

    @Test fun warnungMitWenigenEinbruechenGedeckeltOderUngemessen() {
        assertEquals(Staerke(2, true), AusblickStaerke.ausWarnung(warn(7, 7, 0.1)))
        assertNull(AusblickStaerke.ausWarnung(warn(4, 4, 0.1)))
        assertNull(AusblickStaerke.ausWarnung(null))
    }

    @Test fun gesetztAusQuellengewicht() {
        assertEquals(Staerke(4, false), AusblickStaerke.gesetzt(1.2))
        assertEquals(Staerke(3, false), AusblickStaerke.gesetzt(1.0))
        assertEquals(Staerke(2, false), AusblickStaerke.gesetzt(0.5))
        assertEquals(Staerke(1, false), AusblickStaerke.gesetzt(0.0))
        assertFalse(AusblickStaerke.gesetzt(0.8).gemessen)
    }

    @Test fun signalOhneRueckblickIstGesetzt() {
        val r = AusblickStaerke.fuerSignal("rezession", null, null)
        assertEquals(AusblickStaerke.gesetzt(Ausblickgesamt.quelle("rezession").basis), r)
        assertFalse(AusblickStaerke.fuerSignal(Fruehindikatoren.ROHOEL, null, null).gemessen)
        assertFalse(AusblickStaerke.fuerBlase(Blasen.CAPE, null).gemessen)
    }

    @Test fun duerreNimmtAlleGetreide() {
        val d = TendenzRueckblick.Datei(
            signale = mapOf(
                "duerre:ZS=F" to signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 30, 0.62, 0.50)),
                "duerre:ZW=F" to signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 30, 0.50, 0.50)),
                "rohoel" to signal(TendenzRueckblick.GEPRUEFT, zeitraum(4, 30, 0.90, 0.50)),
            ),
        )
        // Mittel +6 Punkte aus den zwei Getreiden, Rohoel zaehlt nicht mit
        assertEquals(Staerke(2, true), AusblickStaerke.fuerSignal(Fruehindikatoren.DUERRE, d, null))
    }

    @Test fun mitgelieferteDateienLiefernFuerAlleSignaleEineStufe() {
        val t = File("src/main/assets/indikatoren/tendenz_rueckblick.json").takeIf { it.exists() }?.let { TendenzRueckblick.lesen(it.readText()) }
        val w = File("src/main/assets/indikatoren/warnzeichen_rueckblick.json").takeIf { it.exists() }?.let { WarnRueckblick.lesen(it.readText()) }
        if (t == null || w == null) return
        for (k in Fruehindikatoren.AUSBLICK + listOf("zins", "fed", "rezession", "wci", "aar", "wsts") + Ersatzquellen.SIGNALE) {
            val s = AusblickStaerke.fuerSignal(k, t, w)
            assertTrue(k, s.stufe in 0..AusblickStaerke.STUFEN)
        }
        for (k in Blasen.DAFUER + Blasen.DAGEGEN) assertTrue(k, AusblickStaerke.fuerBlase(k, w).stufe in 0..AusblickStaerke.STUFEN)
        // Erstantraege und Marken wie CAPE haben einen Rueckblick und sind gemessen, Polymarket nicht
        assertTrue(AusblickStaerke.fuerSignal(Fruehindikatoren.ERSTANTRAEGE, t, w).gemessen)
        assertTrue(AusblickStaerke.fuerBlase(Blasen.CAPE, w).gemessen)
        assertTrue(AusblickStaerke.fuerBlase(Blasen.TREND, w).gemessen)
        assertFalse(AusblickStaerke.fuerSignal("rezession", t, w).gemessen)
        // Rohoel lag im Rueckblick nicht ueber der Grundquote
        assertEquals(Staerke(0, true), AusblickStaerke.fuerSignal(Fruehindikatoren.ROHOEL, t, w))
    }

    @Test fun beitragTraegtZustandUndWertBisInDieEinheit() {
        val b = listOf(
            Ausblickgesamt.Beitrag("rezession", "Rezession in den USA", Tendenz.AKTIEN, Richtung.BULLISH, 0, "US-Rezession unwahrscheinlich", "1 %"),
            Ausblickgesamt.Beitrag("erstantraege", "Erstanträge", Tendenz.AKTIEN, Richtung.BULLISH, 0, "Erstanträge niedrig", "213000"),
        )
        val e = Ausblickgesamt.einheiten(b)
        val r = e.first { it.schluessel == "rezession" }
        assertEquals("US-Rezession unwahrscheinlich", r.zustand)
        assertEquals("1 %", r.wertText)
        assertNotNull(e.first { it.schluessel == "erstantraege" }.zustand.takeIf { it.isNotEmpty() })
    }

    @Test fun gruppeNimmtZustandDesSchwerstenMitglieds() {
        val b = listOf(
            Ausblickgesamt.Beitrag("zins", "Zins", Tendenz.AKTIEN, Richtung.BULLISH, 0, "Fed-Senkungen erwartet", "4,10 %"),
            Ausblickgesamt.Beitrag("fed", "Fed", Tendenz.AKTIEN, Richtung.BULLISH, 0, "Fed-Senkung wahrscheinlich", "70 %"),
        )
        val e = Ausblickgesamt.einheiten(b).single()
        assertEquals("zins", e.schluessel)
        assertEquals("Fed-Senkungen erwartet", e.zustand)
    }
}
