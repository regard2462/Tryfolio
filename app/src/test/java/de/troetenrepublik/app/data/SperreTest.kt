package de.troetenrepublik.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// App-Sperre: Depot verschluesselt speichern, nur mit der richtigen PIN wieder lesen.
class SperreTest {
    private fun ordner() = File.createTempFile("sperre", "").also { it.delete(); it.mkdirs() }

    @Test fun verschluesseltSpeichernUndEntsperren() = runBlocking {
        val dir = ordner()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = Store(dir, scope)
        store.replace(Account(cash = 1234.5, watchlist = listOf("SAP.DE", "AAPL")))
        delay(200)
        val k = de.troetenrepublik.app.plattform.Krypto.zufall(32)
        store.sperre.setzen("482913", Sperre.PIN, k)
        store.schuetzen(k)
        // Auf der Platte steht nichts mehr im Klartext
        val roh = File(dir, "account.json").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(roh.contains("SAP.DE"))
        assertFalse(roh.contains("1234.5"))
        scope.cancel()

        // Neuer Start: gesperrt, leeres Konto, nichts wird geschrieben
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val neu = Store(dir, scope2)
        assertTrue(neu.gesperrt.value)
        assertTrue(neu.sperre.aktiv)
        assertEquals(Sperre.PIN, neu.sperre.art)
        assertTrue(neu.account.value.watchlist.isEmpty())
        neu.update { it.copy(cash = 1.0) }
        delay(100)
        assertEquals(roh, File(dir, "account.json").readBytes().toString(Charsets.ISO_8859_1))

        assertNull(neu.sperre.oeffnen("000000"))
        val dek = neu.sperre.oeffnen("482913")
        assertNotNull(dek)
        assertTrue(neu.entsperren(dek!!))
        assertFalse(neu.gesperrt.value)
        assertEquals(1234.5, neu.account.value.cash, 1e-9)
        assertEquals(listOf("SAP.DE", "AAPL"), neu.account.value.watchlist)

        // PIN aendern: derselbe Datenschluessel, neu verpackt
        neu.sperre.setzen("Langes Passwort 1", Sperre.PASSWORT, dek)
        assertNull(neu.sperre.oeffnen("482913"))
        assertTrue(neu.sperre.oeffnen("Langes Passwort 1")!!.contentEquals(dek))

        // Ausschalten: erst Klartext schreiben, dann die Sperre loeschen
        neu.freigeben()
        neu.sperre.entfernen()
        assertTrue(File(dir, "account.json").readText().contains("SAP.DE"))
        assertFalse(File(dir, "sperre.json").exists())
        scope2.cancel()
        dir.deleteRecursively()
        Unit
    }

    @Test fun vergessenePinBeginntNeu() = runBlocking {
        val dir = ordner()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = Store(dir, scope)
        store.replace(Account(cash = 99.0))
        val k = de.troetenrepublik.app.plattform.Krypto.zufall(32)
        store.sperre.setzen("1111", Sperre.PIN, k)
        store.schuetzen(k)
        val neu = Store(dir, scope)
        assertTrue(neu.gesperrt.value)
        neu.verwerfen()
        neu.sperre.entfernen()
        assertFalse(neu.gesperrt.value)
        assertFalse(neu.sperre.aktiv)
        assertTrue(File(dir, "account.json").readText().startsWith("{"))
        scope.cancel()
        dir.deleteRecursively()
        Unit
    }
}
