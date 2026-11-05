package de.troetenrepublik.app.engine

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.troetenrepublik.app.data.MITTEILUNG_ALLE
import de.troetenrepublik.app.data.MITTEILUNG_AUS
import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Fassung 5.93: Vorgabe der News-Mitteilungen ist „wichtige“. Ein „aus“ ohne eigene Wahl (DEFAULTS_48)
// gilt einmalig als „wichtige“; nach dem ersten Speichern gilt jede Wahl, auch „aus“.
class Fassung593MitteilungTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private fun store(name: String): SettingsStore =
        SettingsStore(PreferenceDataStoreFactory.create { File(ordner.root, name) })

    @Test fun frischeInstallationNimmtWichtige() = runBlocking {
        assertEquals(MITTEILUNG_WICHTIGE, store("a.preferences_pb").flow.first().newsMitteilung)
    }

    @Test fun frueherErzwungenesAusGiltAlsWichtige() = runBlocking {
        val ds = PreferenceDataStoreFactory.create { File(ordner.root, "b.preferences_pb") }
        ds.edit {
            it[booleanPreferencesKey("defaults_48")] = true
            it[stringPreferencesKey("news_mitteilung")] = "aus"
        }
        assertEquals(MITTEILUNG_WICHTIGE, SettingsStore(ds).flow.first().newsMitteilung)
    }

    @Test fun gewaehltesAusBleibtAus() = runBlocking {
        val ds = PreferenceDataStoreFactory.create { File(ordner.root, "c.preferences_pb") }
        ds.edit {
            it[booleanPreferencesKey("defaults_48")] = true
            it[booleanPreferencesKey("defaults_593")] = true
            it[stringPreferencesKey("news_mitteilung")] = "aus"
        }
        assertEquals(MITTEILUNG_AUS, SettingsStore(ds).flow.first().newsMitteilung)
    }

    @Test fun alleBleibtImmerAlle() = runBlocking {
        val ds = PreferenceDataStoreFactory.create { File(ordner.root, "d.preferences_pb") }
        ds.edit {
            it[booleanPreferencesKey("defaults_48")] = true
            it[stringPreferencesKey("news_mitteilung")] = "alle"
        }
        assertEquals(MITTEILUNG_ALLE, SettingsStore(ds).flow.first().newsMitteilung)
    }

    @Test fun ersteEigeneWahlGiltDanach() = runBlocking {
        val s = store("e.preferences_pb")
        s.update { it.copy(newsMitteilung = MITTEILUNG_AUS) }
        assertEquals(MITTEILUNG_AUS, s.flow.first().newsMitteilung)
    }
}
