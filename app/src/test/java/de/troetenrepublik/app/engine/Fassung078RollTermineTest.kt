package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

// Rollen (0.78): fehlen der Kette die Termine der neuen Laufzeit, werden genau die gebrauchten nachgeladen.
class Fassung078RollTermineTest {
    private val tag = 86_400L
    private val jetzt = 20_000 * tag

    @Test fun ersterUndLetzterImFensterUndCallTermin() {
        // Termine in 1, 4, 15, 36, 50, 64, 85 und 120 Tagen; geladen sind nur die ersten drei, der Put verfaellt in 15 Tagen
        val termine = listOf(1, 4, 15, 36, 50, 64, 85, 120).map { jetzt + it * tag }
        val vorhanden = termine.take(3).toSet()
        val f = RollHelfer.fehlendeTermine(termine, vorhanden, (jetzt + 15 * tag) / tag, jetzt)
        // 50 und 85 Tage fuer den neuen Put, 50 Tage liegt auch am naechsten an der Laufzeit des Calls
        assertEquals(listOf(jetzt + 50 * tag, jetzt + 85 * tag), f)
    }

    @Test fun optionenVerkaufenLaedtDenPassendenTermin() {
        // Geladen sind Termine bis 13 Tage; fuer 30 Tage Laufzeit passt der Termin in 36 Tagen
        val termine = listOf(1, 4, 6, 8, 11, 13, 36, 64).map { jetzt + it * tag }
        val vorhanden = termine.take(6).toSet()
        assertEquals(jetzt + 36 * tag, StillhalterListe.fehlenderTermin(termine, vorhanden, 30, jetzt))
        assertEquals(jetzt + 64 * tag, StillhalterListe.fehlenderTermin(termine, vorhanden, 60, jetzt))
        assertEquals(null, StillhalterListe.fehlenderTermin(termine, termine.toSet(), 30, jetzt))
        // Kein Termin in Reichweite der Laufzeit
        assertEquals(null, StillhalterListe.fehlenderTermin(termine.take(6), vorhanden, 45, jetzt))
    }

    @Test fun nichtsWennSchonGeladenOderKeinTermin() {
        val termine = listOf(15, 50, 85).map { jetzt + it * tag }
        assertEquals(emptyList<Long>(), RollHelfer.fehlendeTermine(termine, termine.toSet(), (jetzt + 15 * tag) / tag, jetzt))
        assertEquals(emptyList<Long>(), RollHelfer.fehlendeTermine(emptyList(), emptySet(), (jetzt + 15 * tag) / tag, jetzt))
        // Nur ein Termin fuer den Call (30 Tage), keiner im Fenster des neuen Puts
        assertEquals(listOf(jetzt + 30 * tag), RollHelfer.fehlendeTermine(listOf(jetzt + 30 * tag), emptySet(), (jetzt + 15 * tag) / tag, jetzt))
    }
}
