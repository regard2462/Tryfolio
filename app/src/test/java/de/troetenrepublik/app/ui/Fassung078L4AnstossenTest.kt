package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Fassung078L4AnstossenTest {

    private val datum = { ms: Long -> LocalDate.ofEpochDay(ms / 86_400_000L).toString() }

    @Before fun deutsch() { I18n.lang = Lang.DE }

    @Test fun eintraegeJeGruppe() {
        assertEquals(5, Anstossen.eintraege(Anstossen.Gruppe.LADEN).size)
        assertEquals(5, Anstossen.eintraege(Anstossen.Gruppe.DATEIEN).size)
        assertEquals(1, Anstossen.eintraege(Anstossen.Gruppe.BROWSER).size)
        assertEquals(Anstossen.Eintrag.entries.size, Anstossen.Gruppe.entries.sumOf { Anstossen.eintraege(it).size })
    }

    @Test fun texteJeZustandDeutsch() {
        assertEquals("noch nicht geladen", Anstossen.text(Anstossen.Stand.Nie, datum))
        assertEquals("läuft 40 %", Anstossen.text(Anstossen.Stand.Laeuft(40), datum))
        assertEquals("läuft", Anstossen.text(Anstossen.Stand.Laeuft(null), datum))
        assertEquals("geladen am 2026-11-11", Anstossen.text(Anstossen.Stand.Am(LocalDate.of(2026, 11, 11).toEpochDay() * 86_400_000L), datum))
        assertEquals("geladen bis 2026-11-11", Anstossen.text(Anstossen.Stand.Bis(LocalDate.of(2026, 11, 11).toEpochDay() * 86_400_000L), datum))
        assertEquals("3 Monate eingelesen", Anstossen.text(Anstossen.Stand.Monate(3), datum))
        assertEquals("1 Monat eingelesen", Anstossen.text(Anstossen.Stand.Monate(1), datum))
        assertEquals("1.234 offen", Anstossen.text(Anstossen.Stand.Offen(1234), datum))
        assertEquals("alle gelesen", Anstossen.text(Anstossen.Stand.Alle, datum))
        assertEquals("noch keine Datei eingelesen", Anstossen.text(Anstossen.Stand.KeineDatei, datum))
        assertEquals("heute erstellt", Anstossen.text(Anstossen.Stand.Heute, datum))
        assertEquals("heute noch nicht erstellt", Anstossen.text(Anstossen.Stand.HeuteNie, datum))
        assertEquals("", Anstossen.text(Anstossen.Stand.Keiner, datum))
    }

    @Test fun texteEnglisch() {
        I18n.lang = Lang.EN
        assertEquals("not loaded yet", Anstossen.text(Anstossen.Stand.Nie, datum))
        assertEquals("running 5 %", Anstossen.text(Anstossen.Stand.Laeuft(5), datum))
        assertEquals("3 months read", Anstossen.text(Anstossen.Stand.Monate(3), datum))
    }

    @Test fun meldungen() {
        assertEquals(Anstossen.Stand.Nie, Anstossen.meldungen(false, 0, 0, null))
        assertEquals(Anstossen.Stand.Alle, Anstossen.meldungen(false, 0, 0, 0))
        assertEquals(Anstossen.Stand.Offen(12), Anstossen.meldungen(false, 0, 0, 12))
        assertEquals(Anstossen.Stand.Laeuft(40), Anstossen.meldungen(true, 40, 100, 12))
        assertEquals(Anstossen.Stand.Laeuft(null), Anstossen.meldungen(true, 0, 0, null))
        assertEquals(Anstossen.Stand.Laeuft(100), Anstossen.meldungen(true, 130, 100, null))
    }

    @Test fun geladen() {
        assertEquals(Anstossen.Stand.Nie, Anstossen.geladen(null, null))
        assertEquals(Anstossen.Stand.Nie, Anstossen.geladen(null, 0L))
        assertEquals(Anstossen.Stand.Am(5L), Anstossen.geladen(null, 5L))
        assertEquals(Anstossen.Stand.Laeuft(40), Anstossen.geladen(0.4f, 5L))
        assertEquals(Anstossen.Stand.Laeuft(null), Anstossen.geladen(2f, 5L))
    }

    @Test fun lager() {
        val tag = LocalDate.of(2026, 9, 30).toEpochDay()
        val tage = listOf(tag, tag + 1, tag + 2, tag + 40, tag + 75)
        assertEquals(4, Anstossen.monate(tage))
        assertEquals(Anstossen.Stand.Monate(4), Anstossen.lme(tage))
        assertEquals(Anstossen.Stand.KeineDatei, Anstossen.lme(emptyList()))
        assertEquals(Anstossen.Stand.KeineDatei, Anstossen.comex(null))
        assertEquals(Anstossen.Stand.Bis(tag * 86_400_000L), Anstossen.comex(tag))
    }

    @Test fun rueckblick() {
        assertEquals(Anstossen.Stand.Laeuft(null), Anstossen.rueckblick(true, false))
        assertEquals(Anstossen.Stand.Heute, Anstossen.rueckblick(false, true))
        assertTrue(Anstossen.rueckblick(false, false) == Anstossen.Stand.HeuteNie)
    }
}
