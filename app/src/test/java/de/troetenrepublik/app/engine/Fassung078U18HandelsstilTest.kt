package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.StrategieLabor.Art
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.78: Handelsstile im Strategietest. Jede Regel gehoert zu einem Stil, das Ergebnis wird nach der mittleren
// Haltedauer eingeordnet, Daytrading und Scalping haben keine Regel.
class Fassung078U18HandelsstilTest {

    private fun ergebnis(vararg tage: Int) = StrategieLabor.Ergebnis(
        endwert = 10_000.0, renditeJahr = 0.0, maxRueckgang = 0.0, trades = tage.size, treffer = null, imMarkt = 0.5,
        verlauf = emptyList(), renditen = emptyList(), sharpe = 0.0, jahre = 5.0,
        geschaefte = tage.mapIndexed { i, t -> StrategieLabor.Geschaeft(i.toLong(), i + 1L, 0.01, t) },
    )

    @Test fun jedeArtHatEinenTestbarenStil() {
        for (a in Art.entries) assertTrue(a.name, Handelsstil.von(a).testbar)
        assertEquals(Handelsstil.BUY_AND_HOLD, Handelsstil.von(Art.HALTEN))
        for (a in listOf(Art.SMA, Art.KREUZ, Art.MACD, Art.DONCHIAN, Art.MOMENTUM)) assertEquals(a.name, Handelsstil.POSITION, Handelsstil.von(a))
        for (a in listOf(Art.RSI, Art.BOLLINGER, Art.RHYTHMUS, Art.RSI_KURZ, Art.MONATSWECHSEL)) assertEquals(a.name, Handelsstil.SWING, Handelsstil.von(a))
        assertFalse(Handelsstil.DAYTRADING.testbar)
        assertFalse(Handelsstil.SCALPING.testbar)
    }

    @Test fun grenzenDerHaltedauer() {
        assertNull(Handelsstil.nachHaltedauer(Double.NaN))
        assertNull(Handelsstil.nachHaltedauer(-1.0))
        assertEquals(Handelsstil.DAYTRADING, Handelsstil.nachHaltedauer(0.5))
        assertEquals(Handelsstil.SWING, Handelsstil.nachHaltedauer(1.0))
        assertEquals(Handelsstil.SWING, Handelsstil.nachHaltedauer(30.0))
        assertEquals(Handelsstil.POSITION, Handelsstil.nachHaltedauer(30.5))
        assertEquals(Handelsstil.POSITION, Handelsstil.nachHaltedauer(252.0))
        assertEquals(Handelsstil.BUY_AND_HOLD, Handelsstil.nachHaltedauer(253.0))
        // Kein Ergebnis landet bei Scalping
        assertTrue((0..2000).none { Handelsstil.nachHaltedauer(it / 4.0) == Handelsstil.SCALPING })
    }

    @Test fun stilAusDemErgebnis() {
        // Mittel 4 Handelstage
        assertEquals(Handelsstil.SWING, Handelsstil.von(Art.SMA, ergebnis(3, 4, 5)))
        // Mittel 90 Handelstage: die gemessene Dauer zaehlt, nicht der Stil der Regel
        assertEquals(Handelsstil.POSITION, Handelsstil.von(Art.RSI, ergebnis(60, 120)))
        assertEquals(Handelsstil.BUY_AND_HOLD, Handelsstil.von(Art.KREUZ, ergebnis(400, 600)))
        // Ohne Geschaefte gilt der Stil der Regel
        assertEquals(Handelsstil.POSITION, Handelsstil.von(Art.MACD, ergebnis()))
        assertEquals(Handelsstil.SWING, Handelsstil.von(Art.BOLLINGER, ergebnis()))
        // Kaufen und Halten bleibt Buy and Hold, auch in einem kurzen Zeitraum
        assertEquals(Handelsstil.BUY_AND_HOLD, Handelsstil.von(Art.HALTEN, ergebnis(100)))
    }

    @Test fun gruppenBehaltenDieReihenfolge() {
        val g = Handelsstil.gruppen(StrategieLabor.REGELN) { it.art }
        assertEquals(listOf(Handelsstil.BUY_AND_HOLD, Handelsstil.POSITION, Handelsstil.SWING), g.map { it.first })
        assertEquals(StrategieLabor.REGELN.size, g.sumOf { it.second.size })
        assertEquals(StrategieLabor.REGELN.toSet(), g.flatMap { it.second }.toSet())
        for ((stil, regeln) in g) {
            assertTrue(regeln.all { Handelsstil.von(it.art) == stil })
            assertEquals(regeln, StrategieLabor.REGELN.filter { it in regeln })
        }
    }

    @Test fun texteInBeidenSprachen() {
        for (s in listOf(De, En)) {
            val namen = Handelsstil.entries.map { s.st("hs." + it.name) }
            val dauern = Handelsstil.entries.map { s.st("hs.dauer." + it.name) }
            assertTrue(namen.all { it.isNotBlank() } && dauern.all { it.isNotBlank() })
            assertEquals(namen.size, namen.toSet().size)
            assertEquals(dauern.size, dauern.toSet().size)
            assertTrue(s.st("hs.zeile").isNotBlank())
            val e = s.st("hs.erklaerung")
            // Ein Stichpunkt je Stil, jeder mit dem Namen des Stils
            assertEquals(Handelsstil.entries.size, e.split('\n').count { it.startsWith("• ") })
            for (n in namen) assertTrue(n, e.contains("• $n: "))
            for (t in namen + dauern + e + s.st("hs.zeile")) {
                assertFalse(t, t.contains("—") || t.contains("–") || t.contains("…") || t.contains("?") || t.contains("{"))
            }
            assertFalse(e.contains("bullisch") || e.contains("bärisch") || e.contains("bullish") || e.contains("bearish"))
        }
        assertEquals("Swing Trading · Tage bis Wochen", De.st("hs.gruppe", listOf(De.st("hs.SWING"), De.st("hs.dauer.SWING"))))
        assertEquals("Swing trading · Days to weeks", En.st("hs.gruppe", listOf(En.st("hs.SWING"), En.st("hs.dauer.SWING"))))
        // Die Erklaerung nennt die Grenzen, mit denen gerechnet wird
        assertTrue(De.st("hs.erklaerung").contains("bis " + Handelsstil.SWING_BIS.toInt() + " Handelstage"))
        assertTrue(De.st("hs.erklaerung").contains("bis " + Handelsstil.POSITION_BIS.toInt() + " "))
        assertTrue(En.st("hs.erklaerung").contains("up to " + Handelsstil.SWING_BIS.toInt() + " trading days"))
        assertTrue(En.st("hs.erklaerung").contains("up to " + Handelsstil.POSITION_BIS.toInt() + " "))
    }
}
