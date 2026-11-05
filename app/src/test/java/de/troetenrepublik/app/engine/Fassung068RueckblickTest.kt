// Fassung 0.68: Uhrzeit des Tagesrueckblicks einstellbar. Naechste Ausfuehrung, Faelligkeit und vorlaeufiger Stand
// je nach Uhrzeit (abends oder morgens), dazu die Gliederung der Mitteilungsarten.
package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.MITTEILUNG_ALLE
import de.troetenrepublik.app.data.MITTEILUNG_AUS
import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class Fassung068RueckblickTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun ms(j: Int, m: Int, t: Int, h: Int, min: Int): Long =
        LocalDateTime.of(j, m, t, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun tag(j: Int, m: Int, t: Int): Long = LocalDate.of(j, m, t).toEpochDay()

    private val leer = Tagesrueckblick.Archiv(emptyList())

    @Test fun abendsNaechsteAusfuehrung() {
        val ab = Tagesrueckblick.AB_MINUTE
        assertEquals(ms(2026, 11, 4, 22, 10), Tagesrueckblick.naechsteAusfuehrung(ms(2026, 11, 4, 12, 0), ab, zone))
        assertEquals(ms(2026, 11, 5, 22, 10), Tagesrueckblick.naechsteAusfuehrung(ms(2026, 11, 4, 22, 10), ab, zone))
        assertEquals(ms(2026, 11, 9, 22, 10), Tagesrueckblick.naechsteAusfuehrung(ms(2026, 11, 6, 23, 0), ab, zone))
    }

    @Test fun morgensNaechsteAusfuehrung() {
        val ab = 7 * 60
        assertEquals(ms(2026, 11, 7, 7, 0), Tagesrueckblick.naechsteAusfuehrung(ms(2026, 11, 6, 8, 0), ab, zone))
        assertEquals(ms(2026, 11, 10, 7, 0), Tagesrueckblick.naechsteAusfuehrung(ms(2026, 11, 7, 8, 0), ab, zone))
    }

    @Test fun sommerzeitBleibtOrtszeit() {
        assertEquals(ms(2026, 3, 30, 22, 10), Tagesrueckblick.naechsteAusfuehrung(ms(2026, 3, 27, 23, 0), Tagesrueckblick.AB_MINUTE, zone))
    }

    @Test fun faelligAbends18Uhr() {
        val ab = 18 * 60
        assertEquals(tag(2026, 11, 4), Tagesrueckblick.faellig(ms(2026, 11, 4, 18, 30), leer, zone, ab))
        assertNull(Tagesrueckblick.faellig(ms(2026, 11, 4, 17, 0), leer, zone, ab))
        assertEquals(tag(2026, 11, 4), Tagesrueckblick.faellig(ms(2026, 11, 5, 7, 0), leer, zone, ab))
    }

    @Test fun faelligMorgens7Uhr() {
        val ab = 7 * 60
        assertEquals(tag(2026, 11, 4), Tagesrueckblick.faellig(ms(2026, 11, 5, 7, 30), leer, zone, ab))
        assertNull(Tagesrueckblick.faellig(ms(2026, 11, 5, 6, 30), leer, zone, ab))
        assertNull(Tagesrueckblick.faellig(ms(2026, 11, 9, 7, 30), leer, zone, ab))
    }

    @Test fun faelligMitArchiv() {
        val t = tag(2026, 11, 4)
        val jetzt = ms(2026, 11, 4, 22, 30)
        val fertig = Tagesrueckblick.Archiv(listOf(Tagesrueckblick.Rueckblick(t, 0L, vorlaeufig = false)))
        val vorl = Tagesrueckblick.Archiv(listOf(Tagesrueckblick.Rueckblick(t, 0L, vorlaeufig = true)))
        assertNull(Tagesrueckblick.faellig(jetzt, fertig, zone))
        assertEquals(t, Tagesrueckblick.faellig(jetzt, vorl, zone))
    }

    @Test fun faelligOhneAbWieBisher() {
        assertEquals(tag(2026, 11, 4), Tagesrueckblick.faellig(ms(2026, 11, 4, 22, 30), leer, zone))
        assertNull(Tagesrueckblick.faellig(ms(2026, 11, 4, 21, 0), leer, zone))
    }

    @Test fun vorlaeufigJeNachUhrzeit() {
        assertTrue(Tagesrueckblick.vorlaeufig(ms(2026, 11, 4, 20, 0), Tagesrueckblick.AB_MINUTE, zone))
        assertFalse(Tagesrueckblick.vorlaeufig(ms(2026, 11, 4, 22, 30), Tagesrueckblick.AB_MINUTE, zone))
        assertTrue(Tagesrueckblick.vorlaeufig(ms(2026, 11, 4, 23, 0), 7 * 60, zone))
    }

    @Test fun uhrzeitMitNull() {
        assertEquals("07:00", Tagesrueckblick.uhrzeit(7 * 60))
        assertEquals("22:10", Tagesrueckblick.uhrzeit(22 * 60 + 10))
    }

    @Test fun newsAnAusAnBringtStufeZurueck() {
        val s = Settings().copy(newsMitteilung = MITTEILUNG_ALLE)
        val aus = Mitteilungsarten.setzen(s, Mitteilungsarten.Art.NEWS, false)
        assertEquals(MITTEILUNG_AUS, aus.newsMitteilung)
        assertFalse(Mitteilungsarten.an(aus, Mitteilungsarten.Art.NEWS))
        assertEquals(MITTEILUNG_ALLE, Mitteilungsarten.setzen(aus, Mitteilungsarten.Art.NEWS, true).newsMitteilung)
    }

    @Test fun newsAnAusWerkszustandGibtWichtige() {
        val s = Settings().copy(newsMitteilung = MITTEILUNG_AUS, newsMitteilungZuletzt = "")
        assertEquals(MITTEILUNG_WICHTIGE, Mitteilungsarten.setzen(s, Mitteilungsarten.Art.NEWS, true).newsMitteilung)
    }

    @Test fun rueckblickMitteilungSchaltetRueckblickMit() {
        val s = Settings().copy(tagesrueckblick = false, rueckblickMitteilung = false)
        val an = Mitteilungsarten.setzen(s, Mitteilungsarten.Art.RUECKBLICK, true)
        assertTrue(an.tagesrueckblick)
        assertTrue(an.rueckblickMitteilung)
        val aus = Mitteilungsarten.setzen(an, Mitteilungsarten.Art.RUECKBLICK, false)
        assertTrue(aus.tagesrueckblick)
        assertFalse(aus.rueckblickMitteilung)
    }

    @Test fun gesperrteKanaele() {
        assertEquals(setOf("trades", "cash"), Mitteilungsarten.gesperrteKanaele(Settings().copy(mitteilungDepot = false)))
        assertEquals(emptySet<String>(), Mitteilungsarten.gesperrteKanaele(Settings()))
        assertEquals(setOf("copytrading"), Mitteilungsarten.gesperrteKanaele(Settings().copy(mitteilungCopy = false)))
    }

    @Test fun aktivZaehltUpdatesNurMitAktualisierbar() {
        val s = Settings().copy(
            mitteilungDepot = false, mitteilungAlarme = false, mitteilungCopy = false,
            tagesrueckblick = false, newsMitteilung = MITTEILUNG_AUS, updateHinweis = true,
        )
        assertEquals(0, Mitteilungsarten.aktiv(s, false))
        assertEquals(1, Mitteilungsarten.aktiv(s, true))
    }
}
