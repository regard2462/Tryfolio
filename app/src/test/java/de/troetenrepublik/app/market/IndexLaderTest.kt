package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.IndexStand.Wert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// [Globus Boersen 0.73] Laden: einzeln wiederholen, Zeitlimit, begrenzte Gleichzeitigkeit, Zwischenspeicher ohne falsche Frische
class IndexLaderTest {
    private val heute = LocalDate.of(2026, 11, 9)
    // Uhr ohne echte Zeit: jeder Aufruf geht zehn Millisekunden weiter, die Pausen des Laders laufen damit ohne Warten ab
    private fun uhr(start: Long = 1_000_000L): () -> Long { val z = java.util.concurrent.atomic.AtomicLong(start); return { z.addAndGet(10L) } }

    private fun quelle(id: String, land: String, zeitMs: Long = 2_000L, p: Double = 0.5) =
        BoersenDirekt.Quelle(id, listOf(land), id, "https://x.test/$id", zeitMs = zeitMs, parse = { BoersenDirekt.Stand(p, 100.0, heute) })

    private fun lader(
        scope: CoroutineScope, quellen: List<BoersenDirekt.Quelle>, datei: StringBuilder = StringBuilder(), jetzt: () -> Long = uhr(),
        holen: suspend (BoersenDirekt.Quelle) -> String?,
    ) = IndexLader(scope, { heute }, jetzt, { datei.toString().ifEmpty { null } }, { datei.setLength(0); datei.append(it) }, holen, quellen, ersteMs = 5L, gueltigMs = 1_800_000L)

    @Test(timeout = 20_000)
    fun gescheiterteQuelleWirdEinzelnWiederholt() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val versuche = ConcurrentHashMap<String, AtomicInteger>()
        val l = lader(scope, listOf(quelle("a", "AA"), quelle("b", "BB"))) { q ->
            val n = versuche.getOrPut(q.id) { AtomicInteger() }.incrementAndGet()
            if (q.id == "b" && n < 3) null else "ok"
        }
        l.anstossen()
        l.warteJob()!!.join()
        assertEquals(1, versuche["a"]!!.get())
        assertEquals(3, versuche["b"]!!.get())
        assertEquals(setOf("AA", "BB"), l.staende.value.keys)
        assertEquals(0, l.fehlerVon("b"))
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test(timeout = 20_000)
    fun tote_boerseHaeltDieAnderenNichtAuf() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val l = lader(scope, listOf(quelle("tot", "RU", zeitMs = 50L), quelle("lebt", "UA"))) { q ->
            if (q.id == "tot") { delay(60_000L); "ok" } else "ok"
        }
        l.anstossen()
        // die lebende Boerse steht, bevor die tote ihr Zeitlimit erreicht hat
        var n = 0
        while ("UA" !in l.staende.value && n++ < 200) delay(5)
        assertNotNull(l.staende.value["UA"])
        assertNull(l.staende.value["RU"])
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test(timeout = 20_000)
    fun hoechstensVierAbrufeGleichzeitig() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val aktiv = AtomicInteger(); val maximal = AtomicInteger()
        val l = lader(scope, (1..10).map { quelle("q$it", "L$it") }) {
            val a = aktiv.incrementAndGet()
            maximal.updateAndGet { m -> maxOf(m, a) }
            delay(30)
            aktiv.decrementAndGet()
            "ok"
        }
        l.anstossen()
        l.warteJob()!!.join()
        assertEquals(10, l.staende.value.size)
        assertTrue("gleichzeitig: ${maximal.get()}", maximal.get() in 2..4)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test(timeout = 20_000)
    fun dateiStandOhneRusslandGiltNichtAlsFrisch() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        // gespeichert: nur die Ukraine, vor zwei Stunden abgerufen; Russland fehlt
        val vor2h = 1_000_000L - 2 * 3_600_000L
        val datei = StringBuilder(IndexStand.schreiben(mapOf("UA" to mapOf("direkt:pfts" to Wert("UA", "direkt:pfts", 0.1, 400.0, heute, vor2h)))))
        val geholt = ConcurrentHashMap.newKeySet<String>()
        val l = lader(scope, listOf(quelle("moex", "RU"), quelle("pfts", "UA")), datei) { q -> geholt += q.id; "ok" }
        l.anstossen()
        l.warteJob()!!.join()
        assertTrue("moex" in geholt && "pfts" in geholt)
        assertNotNull(l.staende.value["RU"])
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test(timeout = 20_000)
    fun juengerAbrufRuhtBisZurNaechstenFaelligkeit() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val datei = StringBuilder(IndexStand.schreiben(mapOf("UA" to mapOf("direkt:pfts" to Wert("UA", "direkt:pfts", 0.1, 400.0, heute, 1_000_000L - 60_000L)))))
        val geholt = ConcurrentHashMap.newKeySet<String>()
        val l = lader(scope, listOf(quelle("pfts", "UA")), datei) { q -> geholt += q.id; "ok" }
        l.anstossen()
        l.warteJob()!!.join()
        assertTrue(geholt.isEmpty())
        assertEquals(0.1, l.staende.value["UA"]!!.prozent, 1e-9)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test(timeout = 20_000)
    fun yahooUndDirektWerteWerdenZusammengefuehrt() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val datei = StringBuilder()
        val l = lader(scope, listOf(quelle("brvm", "SN", p = 0.22)), datei) { "ok" }
        l.eintragen(listOf(Wert("DE", "yahoo", 1.5, 26000.0, heute, 1L), Wert("SN", "yahoo-etf", 9.0, null, heute, 1L)))
        l.anstossen()
        l.warteJob()!!.join()
        val s = l.staende.value
        assertEquals(1.5, s["DE"]!!.prozent, 1e-9)
        // der echte Index geht vor dem ETF
        assertEquals(0.22, s["SN"]!!.prozent, 1e-9)
        assertEquals(setOf("DE", "SN"), l.prozente().keys)
        // der Zwischenspeicher haelt beide Staende von SN mit Zeitpunkt und Datum
        assertEquals(3, IndexStand.lesen(datei.toString()).values.sumOf { it.size })
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test(timeout = 20_000)
    fun mehrfachesSchweigenMeldetDasLand() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val l = lader(scope, listOf(quelle("still", "RU"), quelle("lebt", "UA"))) { q -> if (q.id == "still") null else "ok" }
        assertTrue(l.stumm.value.isEmpty())
        l.anstossen()
        l.warteJob()!!.join()
        assertEquals(setOf("RU"), l.stumm.value)
        assertNull(l.staende.value["RU"])
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test(timeout = 20_000)
    fun unsinnigeWerteWerdenVerworfen() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val l = lader(scope, listOf(quelle("x", "XX", p = 64.7))) { "ok" }
        l.anstossen()
        // fuenf Fehlversuche, dann Ruhe bis zum naechsten Anstoss
        l.warteJob()!!.join()
        assertTrue(l.staende.value.isEmpty())
        assertEquals(IndexLader.MAX_FEHLER, l.fehlerVon("x"))
        scope.coroutineContext[Job]!!.cancel()
    }
}
