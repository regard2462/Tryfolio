package de.troetenrepublik.app.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.SettingsStore
import de.troetenrepublik.app.data.TresorLesen
import de.troetenrepublik.app.data.TresorSpeicher
import de.troetenrepublik.app.engine.LogoDarstellung.Art
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.79: Logos farbig oder schwarz-weiss, rund oder eckig, Trennlinien an oder aus (Logik und Einstellungen).
class Fassung079LogoFarbeTest {

    private class LeererTresor : TresorSpeicher {
        override var verfuegbar = true
        override fun lesen(name: String): TresorLesen = TresorLesen.Leer
        override fun schreiben(name: String, wert: String?) {}
    }

    private class SpeicherStore : DataStore<Preferences> {
        private val stand = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = stand
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(stand.value).also { stand.value = it }
    }

    @Test fun vorgabenSindFarbigRundMitLinien() {
        val s = Settings()
        assertTrue(s.logosFarbig); assertTrue(s.logoRund); assertTrue(s.trennlinien)
    }

    @Test fun einstellungenWerdenGespeichertUndGeladen() = runBlocking {
        val ds = SpeicherStore()
        val store = SettingsStore(ds, LeererTresor())
        assertTrue(store.flow.first().logosFarbig)
        store.update { it.copy(logosFarbig = false, logoRund = false, trennlinien = false) }
        val neu = SettingsStore(ds, LeererTresor()).flow.first()
        assertFalse(neu.logosFarbig); assertFalse(neu.logoRund); assertFalse(neu.trennlinien)
        store.update { it.copy(logosFarbig = true) }
        val wieder = SettingsStore(ds, LeererTresor()).flow.first()
        assertTrue(wieder.logosFarbig); assertFalse(wieder.logoRund); assertFalse(wieder.trennlinien)
    }

    @Test fun artJeKombination() {
        // Farbig bleibt immer unveraendert
        for (fl in listOf(true, false)) for (fr in listOf(true, false)) {
            if (fl && fr) continue
            assertEquals(Art.ORIGINAL, LogoDarstellung.art(true, fl, fr))
        }
        // Schwarz-weiss: eigene Flaeche in Graustufen, freistehendes Zeichen einfarbig, unbekannt vorerst Graustufen
        assertEquals(Art.GRAUSTUFEN, LogoDarstellung.art(false, eigeneFlaeche = true, frei = false))
        assertEquals(Art.EINFARBIG, LogoDarstellung.art(false, eigeneFlaeche = false, frei = true))
        assertEquals(Art.GRAUSTUFEN, LogoDarstellung.art(false, eigeneFlaeche = false, frei = false))
    }

    @Test fun flaecheHinterDunklemZeichenNurFarbigImDunklenModus() {
        assertTrue(LogoDarstellung.flaecheHinterDunklemZeichen(Art.ORIGINAL, dunkel = true))
        assertFalse(LogoDarstellung.flaecheHinterDunklemZeichen(Art.ORIGINAL, dunkel = false))
        assertFalse(LogoDarstellung.flaecheHinterDunklemZeichen(Art.EINFARBIG, dunkel = true))
        assertFalse(LogoDarstellung.flaecheHinterDunklemZeichen(Art.GRAUSTUFEN, dunkel = true))
    }

    @Test fun einfarbigFolgtDemErscheinungsbild() {
        // Textfarbe: schwarz im hellen, weiss im dunklen, braun im Sepia-Design (das Theme nimmt onSurface)
        assertEquals(0xFF000000.toInt(), Erscheinung.HELL_TEXT)
        assertTrue(Erscheinung.dunkel(de.troetenrepublik.app.data.Appearance.DARK, false))
        assertFalse(Erscheinung.dunkel(de.troetenrepublik.app.data.Appearance.SEPIA, true))
        assertTrue(Erscheinung.sepia(de.troetenrepublik.app.data.Appearance.SEPIA))
        assertTrue(Erscheinung.SEPIA_TEXT != Erscheinung.HELL_TEXT)
    }

    @Test fun formRundOderEckig() {
        assertEquals(0.5f, LogoDarstellung.eckenAnteil(true), 0f)
        assertEquals(0.22f, LogoDarstellung.eckenAnteil(false), 0f)
        // Im Kreis sitzt das Zeichen kleiner als im Quadrat, auf Flaeche ebenfalls
        assertTrue(LogoDarstellung.zeichenAnteil(true, false) < LogoDarstellung.zeichenAnteil(false, false))
        assertTrue(LogoDarstellung.zeichenAnteil(true, true) < LogoDarstellung.zeichenAnteil(false, true))
        assertEquals(0.72f, LogoDarstellung.zeichenAnteil(true, false), 0f)
        assertEquals(1.14f, LogoDarstellung.FLAECHE_VERGROESSERUNG, 0f)
    }

    @Test fun trennlinienSichtbarkeit() {
        assertTrue(LogoDarstellung.linieSichtbar(true))
        assertFalse(LogoDarstellung.linieSichtbar(false))
    }

    @Test fun textePassenInBeideSprachen() {
        for (s in listOf(De, En)) {
            listOf(s.logos, s.logosFarbig, s.logosSchwarzWeiss, s.logoForm, s.logoRund, s.logoEckig, s.trennlinien, s.trennAn, s.trennAus).forEach {
                assertTrue(it.isNotBlank())
                listOf("—", "…").forEach { z -> assertFalse(it.contains(z)) }
            }
        }
        assertEquals("Schwarz-weiß", De.logosSchwarzWeiss)
        assertEquals("Black and white", En.logosSchwarzWeiss)
        assertEquals("Logo shape", En.logoForm)
        assertEquals("Dividers", En.trennlinien)
    }
}
