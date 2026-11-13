package de.troetenrepublik.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ersatz-Tresor mit Schaltern fuer Lese- und Schreibfehler und Zaehlern fuer die Zugriffe.
private class FakeTresor : TresorSpeicher {
    override var verfuegbar = true
    val daten = mutableMapOf<String, String>()
    var lesenFehlt = false
    var schreibenFehlt = false
    val geschrieben = mutableListOf<Pair<String, String?>>()

    override fun lesen(name: String): TresorLesen = when {
        lesenFehlt -> TresorLesen.Fehler
        else -> daten[name]?.let { TresorLesen.Wert(it) } ?: TresorLesen.Leer
    }

    override fun schreiben(name: String, wert: String?) {
        if (schreibenFehlt) throw IllegalStateException("write failed")
        geschrieben += name to wert
        if (wert == null) daten.remove(name) else daten[name] = wert
    }
}

// DataStore im Speicher, damit die Tests nicht von Dateisperren des Betriebssystems abhaengen.
private class SpeicherStore : DataStore<Preferences> {
    private val stand = MutableStateFlow<Preferences>(emptyPreferences())
    private val sperre = Mutex()
    override val data: Flow<Preferences> = stand

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        sperre.withLock { transform(stand.value).also { stand.value = it } }
}

class TresorUebernahmeTest {

    private val guardianDatei = stringPreferencesKey("key_guardian")
    private val finnhubDatei = stringPreferencesKey("key_finnhub")
    private val steuerDatei = stringPreferencesKey("tax_id")

    private fun datastore(): DataStore<Preferences> = SpeicherStore()

    private suspend fun alterKlartext(ds: DataStore<Preferences>) = ds.edit {
        it[guardianDatei] = "g-alt"
        it[finnhubDatei] = "f-alt"
        it[steuerDatei] = "12345678901"
    }

    @Test fun uebernahmeLoeschtKlartextUndBehaeltWerte() = runBlocking {
        val ds = datastore(); val t = FakeTresor()
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        s.update { it }
        val p = ds.data.first()
        assertNull(p[guardianDatei]); assertNull(p[finnhubDatei]); assertNull(p[steuerDatei])
        assertEquals("g-alt", t.daten["schluessel_guardian"])
        assertEquals("12345678901", t.daten["schluessel_steuer_id"])
        val e = s.flow.first()
        assertEquals("g-alt", e.keyGuardian); assertEquals("f-alt", e.keyFinnhub); assertEquals("12345678901", e.taxId)
        // Zweite Fassung des Stores liest nur noch aus dem Tresor
        assertEquals("g-alt", SettingsStore(ds, t).flow.first().keyGuardian)
    }

    @Test fun uebernahmeIstWiederholbar() = runBlocking {
        val ds = datastore(); val t = FakeTresor()
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        repeat(3) { s.update { it } }
        assertEquals(3, t.geschrieben.size)
    }

    @Test fun schreibfehlerLaesstKlartextStehenUndWertBleibtNutzbar() = runBlocking {
        val ds = datastore(); val t = FakeTresor().apply { schreibenFehlt = true }
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        s.update { it }
        val p = ds.data.first()
        assertEquals("g-alt", p[guardianDatei]); assertEquals("12345678901", p[steuerDatei])
        assertEquals("g-alt", s.flow.first().keyGuardian)
        // Spaeter geht es: die Uebernahme holt es nach
        t.schreibenFehlt = false
        s.update { it }
        assertNull(ds.data.first()[guardianDatei])
        assertEquals("g-alt", t.daten["schluessel_guardian"])
    }

    @Test fun lesefehlerGefolgtVonUpdateLaesstTresorUnberuehrt() = runBlocking {
        val ds = datastore(); val t = FakeTresor()
        alterKlartext(ds)
        SettingsStore(ds, t).update { it }
        t.geschrieben.clear()
        t.lesenFehlt = true
        val s = SettingsStore(ds, t)
        assertEquals("", s.flow.first().keyGuardian)
        s.update { it.copy(ownerName = "Alex") }
        s.update { it.copy(refreshSeconds = 30) }
        assertTrue(t.geschrieben.isEmpty())
        assertEquals("g-alt", t.daten["schluessel_guardian"])
        assertEquals("f-alt", t.daten["schluessel_finnhub"])
        // Beim naechsten Lesen ohne Fehler ist alles da
        t.lesenFehlt = false
        s.tresorNeuLesen()
        assertEquals("g-alt", s.flow.first().keyGuardian)
        assertEquals("Alex", s.flow.first().ownerName)
    }

    @Test fun lesefehlerMitKlartextInDateiNutztKlartext() = runBlocking {
        val ds = datastore(); val t = FakeTresor().apply { lesenFehlt = true }
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        s.update { it.copy(ownerName = "Alex") }
        assertEquals("g-alt", s.flow.first().keyGuardian)
        assertEquals("g-alt", ds.data.first()[guardianDatei])
        assertTrue(t.geschrieben.isEmpty())
    }

    @Test fun ausdruecklichesLeerenLoeschtDenEintrag() = runBlocking {
        val ds = datastore(); val t = FakeTresor()
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        s.update { it }
        s.update { it.copy(keyGuardian = "") }
        assertNull(t.daten["schluessel_guardian"])
        assertEquals("f-alt", t.daten["schluessel_finnhub"])
        assertEquals("", s.flow.first().keyGuardian)
    }

    @Test fun aendernSchreibtNurDiesesFeld() = runBlocking {
        val ds = datastore(); val t = FakeTresor()
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        s.update { it }
        t.geschrieben.clear()
        s.update { it.copy(keyFinnhub = " f-neu ") }
        assertEquals(listOf("schluessel_finnhub" to "f-neu"), t.geschrieben)
        assertEquals("f-neu", s.flow.first().keyFinnhub)
        assertNull(ds.data.first()[finnhubDatei])
    }

    @Test fun unveraendertesSpeichernSchreibtNichts() = runBlocking {
        val ds = datastore(); val t = FakeTresor()
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        s.update { it }
        t.geschrieben.clear()
        s.update { it.copy(ownerName = "Alex") }
        s.update { it }
        assertTrue(t.geschrieben.isEmpty())
    }

    @Test fun ohneTresorBleibtAllesImKlartext() = runBlocking {
        val ds = datastore(); val t = FakeTresor().apply { verfuegbar = false }
        alterKlartext(ds)
        val s = SettingsStore(ds, t)
        s.update { it.copy(keyDeepl = "d") }
        val p = ds.data.first()
        assertEquals("g-alt", p[guardianDatei])
        assertEquals("d", p[stringPreferencesKey("key_deepl")])
        assertTrue(t.geschrieben.isEmpty())
    }
}
