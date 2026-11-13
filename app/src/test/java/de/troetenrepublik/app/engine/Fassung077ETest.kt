package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

// Zeitraeume im Rueckblick: Angebot nach Geschichte, Beginn und Ende.
class Fassung077ETest {
    private val tag = Zeitraum.TAG

    @Test fun kurzeGeschichteBietetNurKurzeZeitraeumeUndMax() {
        // rund zehn Monate, wie ein Wert, der erst seit Januar gehandelt wird
        assertEquals(listOf(30, 91, 182, 0), RueckblickZeitraum.angebot(290))
    }

    @Test fun langeGeschichteBietetAlles() {
        assertEquals(listOf(30, 91, 182, 365, 1096, 1826, 0), RueckblickZeitraum.angebot(3650))
    }

    @Test fun maxStehtImmerDa() {
        assertEquals(listOf(0), RueckblickZeitraum.angebot(10))
        assertEquals(listOf(0), RueckblickZeitraum.angebot(0))
    }

    @Test fun beginnFestUndMax() {
        val jetzt = 1_000L * tag
        assertEquals(jetzt - 91 * tag, RueckblickZeitraum.beginn(0L, jetzt, 91, null))
        assertEquals(10 * tag, RueckblickZeitraum.beginn(10 * tag, jetzt, 0, null))
        assertEquals(10 * tag, RueckblickZeitraum.beginn(10 * tag, jetzt, 3650, null))
    }

    @Test fun beginnEigenNieVorErstemKurs() {
        val jetzt = 1_000L * tag
        assertEquals(500 * tag, RueckblickZeitraum.beginn(100 * tag, jetzt, 91, 500))
        assertEquals(100 * tag, RueckblickZeitraum.beginn(100 * tag, jetzt, 91, 50))
    }

    @Test fun endeEigenSchlussDesTagesHoechstensJetzt() {
        val jetzt = 1_000L * tag + 5 * 3_600_000L
        assertEquals(600 * tag + tag - 1, RueckblickZeitraum.ende(jetzt, 600))
        assertEquals(jetzt, RueckblickZeitraum.ende(jetzt, 1_000))
        assertEquals(jetzt, RueckblickZeitraum.ende(jetzt, null))
    }
}
