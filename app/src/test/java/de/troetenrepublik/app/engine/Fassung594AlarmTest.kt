package de.troetenrepublik.app.engine

import de.troetenrepublik.app.FinderAlarm
import de.troetenrepublik.app.engine.Aktienfinder.Daten
import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.FinderVorlagen.Grenze
import de.troetenrepublik.app.finderAlarmAbgleich
import de.troetenrepublik.app.finderAlarmGrenzen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.94: Finder-Alarme melden nur Werte, die neu alle Grenzen erfuellen.
class Fassung594AlarmTest {

    private val v = FinderVorlagen.Vorlage("TEST", 1, listOf(Grenze(Feld.KGV, max = 15.0), Grenze(Feld.KBV, max = 3.0)))

    private fun d(symbol: String, kgv: Double?, kbv: Double?, land: String = "US") = Daten(symbol, "Firma $symbol", land = land, kgv = kgv, kbv = kbv)

    @Test fun ersterLaufMerktNur() {
        val (bekannt, neu) = finderAlarmAbgleich(listOf(d("A", 10.0, 2.0), d("B", 20.0, 2.0)), v, FinderAlarm("US", "TEST", listOf(0, 1)), 0L)
        assertEquals(listOf("A"), bekannt)
        assertTrue(neu.isEmpty())
    }

    @Test fun nurNeueTrefferWerdenGemeldet() {
        val a = FinderAlarm("US", "TEST", listOf(0, 1), bekannt = listOf("A"))
        val (bekannt, neu) = finderAlarmAbgleich(listOf(d("A", 10.0, 2.0), d("B", 12.0, 1.0), d("C", 30.0, 1.0)), v, a, 0L)
        assertEquals(listOf("B"), neu)
        assertEquals(setOf("A", "B"), bekannt.toSet())
    }

    @Test fun herausgefalleneWerteKoennenWiederkommen() {
        val a = FinderAlarm("US", "TEST", listOf(0, 1), bekannt = listOf("A"))
        val (bekannt, _) = finderAlarmAbgleich(listOf(d("A", 25.0, 2.0)), v, a, 0L)
        assertTrue(bekannt.isEmpty())
        val (_, neu) = finderAlarmAbgleich(listOf(d("A", 10.0, 2.0)), v, a.copy(bekannt = bekannt), 0L)
        assertEquals(listOf("A"), neu)
    }

    @Test fun nurEingeschalteteGrenzenZaehlen() {
        // Nur die KGV-Grenze ist an: KBV 5 stoert nicht
        val a = FinderAlarm("US", "TEST", listOf(0), bekannt = emptyList())
        assertEquals(1, finderAlarmGrenzen(v, a).size)
        assertEquals(listOf("A"), finderAlarmAbgleich(listOf(d("A", 10.0, 5.0)), v, a, 0L).second)
        // Ungueltige Nummern: alle Grenzen der Vorlage
        assertEquals(2, finderAlarmGrenzen(v, a.copy(grenzen = listOf(9))).size)
    }

    @Test fun nurUsVorlageIgnoriertAndere() {
        val us = v.copy(nurUs = true)
        val a = FinderAlarm("WELT", "TEST", listOf(0, 1), bekannt = emptyList())
        assertEquals(listOf("A"), finderAlarmAbgleich(listOf(d("A", 10.0, 2.0), d("B", 10.0, 2.0, land = "DE")), us, a, 0L).second)
    }
}
