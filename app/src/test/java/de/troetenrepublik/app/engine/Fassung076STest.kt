package de.troetenrepublik.app.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.SettingsStore
import de.troetenrepublik.app.data.ValuedPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// DataStore im Speicher; ein zweiter SettingsStore auf demselben Speicher entspricht dem Neustart.
private class SpeicherStore : DataStore<Preferences> {
    private val stand = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = stand
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(stand.value).also { stand.value = it }
}

class Fassung076STest {

    private val beispiel = listOf(
        Testdepot.Eintrag("AAPL", "Apple", 1500.0),
        Testdepot.Eintrag("SAP.DE", "SAP", 250.5),
    )

    @Test fun speichernUndLadenRundum() {
        assertEquals(beispiel, Testdepot.laden(Testdepot.speichern(beispiel)))
        assertTrue(Testdepot.laden("").isEmpty())
        assertTrue(Testdepot.laden("kaputt\nX\t-5\tX\n\t3\t").isEmpty())
    }

    @Test fun ueberlebtNeustartInDenEinstellungen() = runBlocking {
        val ds = SpeicherStore()
        SettingsStore(ds).update { it.copy(crashTestdepot = Testdepot.speichern(beispiel)) }
        assertEquals(beispiel, Testdepot.laden(SettingsStore(ds).flow.first().crashTestdepot))
        assertEquals("", SettingsStore(SpeicherStore()).flow.first().crashTestdepot)
    }

    @Test fun bewertungLiefertBetragAlsWert() {
        val v = Testdepot.bewertung(beispiel)
        assertEquals(2, v.positions.size)
        assertEquals(1750.5, v.securities, 1e-9)
        assertEquals(1500.0, v.positions[0].value, 1e-9)
        assertTrue(v.positions.all { it.position.derivative == null })
    }

    @Test fun ausDepotFasstZusammenUndLaesstDerivateWeg() {
        fun vp(sym: String, qty: Double, preis: Double) = ValuedPosition(Position(sym, sym, sym, qty, preis, 0L), preis, preis)
        val e = Testdepot.ausDepot(listOf(vp("AAPL", 2.0, 100.0), vp("AAPL", 1.0, 100.0), vp("SAP.DE", 10.0, 120.0)))
        assertEquals(listOf("SAP.DE", "AAPL"), e.map { it.symbol })
        assertEquals(300.0, e.first { it.symbol == "AAPL" }.betrag, 1e-9)
    }

    @Test fun krisenrechnungLaeuftMitTestdepot() {
        // Rechenweg des Crash-Tests: Betrag mal (1 + Bewegung) je Wert
        val v = Testdepot.bewertung(beispiel)
        val nachher = v.positions.sumOf { it.value * (1.0 - 0.3) }
        assertEquals(1750.5 * 0.7, nachher, 1e-9)
        assertEquals(-0.3, nachher / v.securities - 1.0, 1e-9)
    }
}
