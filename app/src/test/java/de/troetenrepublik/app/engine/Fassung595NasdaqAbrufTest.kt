package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NasdaqAbruf
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.ohneAbbruch
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Abruf und Pause der Nasdaq-Kalender mit steuerbarer Uhr und gezaehlten Netzabrufen
class Fassung595NasdaqAbrufTest {

    @Test
    fun abbruchWaehrendAbrufSetztKeinePause() = runBlocking {
        var zeit = 0L
        var abrufe = 0
        var stoeren = true
        val gestartet = CompletableDeferred<Unit>()
        val a = NasdaqAbruf(
            netz = { _ ->
                abrufe += 1
                if (stoeren) {
                    gestartet.complete(Unit)
                    awaitCancellation()
                }
                MarketApi.Antwort(200, "abc")
            },
            uhr = { zeit },
            abstandMs = 0,
        )
        val job = launch { a.holen("adr", { _ -> 60_000L }, { listOf(it.length) }) }
        gestartet.await()
        job.cancel()
        job.join()
        // kein Fehlschlag, nichts gespeichert
        assertFalse(a.pausiert())
        stoeren = false
        assertEquals(listOf(3), a.holen("adr", { _ -> 60_000L }, { listOf(it.length) }))
        assertEquals(2, abrufe)
    }

    @Test
    fun ohneAbbruchReichtAbbruchWeiter() {
        try {
            ohneAbbruch { throw CancellationException("stopp") }
            fail("Abbruch wurde geschluckt")
        } catch (_: CancellationException) {
        }
        assertEquals("fehler", ohneAbbruch { throw RuntimeException("fehler") }.exceptionOrNull()?.message)
        assertEquals("ok", ohneAbbruch { "ok" }.getOrNull())
    }

    @Test
    fun pauseNachFehlschlag() = runBlocking {
        var zeit = 0L
        var abrufe = 0
        var antwort = MarketApi.Antwort(200, "abc")
        val a = NasdaqAbruf(netz = { _ -> abrufe += 1; antwort }, uhr = { zeit }, abstandMs = 0)
        val lesen: (String) -> List<Int>? = { listOf(it.length) }
        // Erfolg wird gespeichert
        assertEquals(listOf(3), a.holen("adr", { _ -> 60_000L }, lesen))
        assertEquals(1, abrufe)
        // 503: Pause, danach kein Netzabruf und der alte Stand
        antwort = MarketApi.Antwort(503, null)
        zeit += 61_000L
        // der Fehlschlag liefert den alten Stand
        assertEquals(listOf(3), a.holen("adr", { _ -> 60_000L }, lesen))
        assertTrue(a.pausiert())
        assertEquals(listOf(3), a.holen("adr", { _ -> 60_000L }, lesen))
        assertEquals(2, abrufe)
        // Pause je Fehlschlag verdoppelt, hoechstens vier Stunden
        var pause = 15L * 60_000L
        while (true) {
            zeit += pause - 1
            assertTrue(a.pausiert())
            zeit += 1
            assertFalse(a.pausiert())
            assertEquals(listOf(3), a.holen("adr", { _ -> 60_000L }, lesen))
            if (pause == 4L * 3_600_000L) break
            pause = minOf(pause * 2, 4L * 3_600_000L)
        }
        assertEquals(7, abrufe)
    }

    @Test
    fun okOhneDatenWirdLeereListe() = runBlocking {
        var zeit = 0L
        var abrufe = 0
        val a = NasdaqAbruf(netz = { _ -> abrufe += 1; MarketApi.Antwort(200, "{\"data\":null}") }, uhr = { zeit }, abstandMs = 0)
        assertEquals(emptyList<NasdaqKalender.Split>(), a.holen("adr", { _ -> 60_000L }, NasdaqKalender::splitsLesen))
        assertFalse(a.pausiert())
        // als Erfolg gespeichert: innerhalb der Haltbarkeit kein zweiter Abruf
        assertEquals(emptyList<NasdaqKalender.Split>(), a.holen("adr", { _ -> 60_000L }, NasdaqKalender::splitsLesen))
        assertEquals(1, abrufe)
    }

    @Test
    fun zwischenspeicherHaeltHaltbarkeit() = runBlocking {
        var zeit = 0L
        var abrufe = 0
        val a = NasdaqAbruf(netz = { _ -> abrufe += 1; MarketApi.Antwort(200, "abc") }, uhr = { zeit }, abstandMs = 0)
        val haltbar: (Long) -> Long = { _ -> 60_000L }
        assertEquals(listOf(3), a.holen("adr", haltbar, { listOf(it.length) }))
        zeit += 30_000L
        // innerhalb der Haltbarkeit kein Abruf
        assertEquals(listOf(3), a.holen("adr", haltbar, { listOf(it.length) }))
        assertEquals(1, abrufe)
        zeit += 31_000L
        // nach Ablauf wieder Abruf
        assertEquals(listOf(3), a.holen("adr", haltbar, { listOf(it.length) }))
        assertEquals(2, abrufe)
    }

    @Test
    fun haltbarkeitJeAbrufzeitpunkt() {
        val tag = LocalDate.of(2026, 11, 3)
        val ny = ZoneId.of("America/New_York")
        val amTag = tag.atTime(10, 0).atZone(ny).toInstant().toEpochMilli()
        val amFolgetag = tag.plusDays(1).atTime(0, 30).atZone(ny).toInstant().toEpochMilli()
        assertEquals(6 * 3_600_000L, NasdaqKalender.haltbarMs(tag, amTag))
        assertEquals(24 * 3_600_000L, NasdaqKalender.haltbarMs(tag, amFolgetag))
    }

    @Test
    fun ipoZusammenGleichtNachKuerzel() {
        val nasdaq = listOf(
            IpoKalender.Angebot(
                deal = "foo holdings limited", symbol = "FOO", name = "Foo Holdings Limited", boerse = null,
                preis = null, aktien = null, volumen = null, tag = null, status = IpoKalender.Status.ANGEKUENDIGT,
            ),
        )
        val finnhub = listOf(
            IpoKalender.Angebot(
                deal = "foo holdings ltd", symbol = "FOO", name = "Foo Holdings Ltd", boerse = null,
                preis = null, aktien = null, volumen = null, tag = null, status = IpoKalender.Status.ANGEKUENDIGT,
            ),
        )
        val eins = NasdaqKalender.ipoZusammen(nasdaq, finnhub)
        assertEquals(1, eins?.size)
        assertEquals("FOO", eins?.first()?.symbol)
        assertEquals("foo holdings limited", eins?.first()?.deal)
    }

    @Test
    fun splitsLesenOhneDoppelte() {
        val text = "{\"data\":{\"rows\":[" +
            "{\"symbol\":\"FOO\",\"name\":\"Foo Inc\",\"ratio\":\"8:1\",\"executionDate\":\"11/03/2026\"}," +
            "{\"symbol\":\"FOO\",\"name\":\"Foo Inc\",\"ratio\":\"8:1\",\"executionDate\":\"11/03/2026\"}," +
            "{\"symbol\":\"BAR\",\"name\":\"Bar Inc\",\"ratio\":\"2:1\",\"executionDate\":\"11/04/2026\"}" +
            "]}}"
        val l = NasdaqKalender.splitsLesen(text)
        assertEquals(2, l?.size)
        assertEquals(setOf("FOO", "BAR"), l?.map { it.symbol }?.toSet())
    }

    @Test
    fun fehlerMitRcodeIstFehlschlag() = runBlocking {
        val fehlerText = """{"data":null,"status":{"rCode":400}}"""
        val okText = """{"data":null,"status":{"rCode":200}}"""
        val ohneStatus = """{"data":null}"""
        // Fehlermeldung der API (HTTP 200, rCode 400) ist ein Fehlschlag
        org.junit.Assert.assertNull(NasdaqKalender.splitsLesen(fehlerText))
        org.junit.Assert.assertNull(NasdaqKalender.zahlenLesen(fehlerText, LocalDate.of(2026, 11, 4)))
        org.junit.Assert.assertNull(NasdaqKalender.dividendenLesen(fehlerText))
        // rCode 200 oder fehlender Status: Antwort ohne Daten, also leere Liste
        assertEquals(0, NasdaqKalender.splitsLesen(okText).orEmpty().size)
        assertEquals(0, NasdaqKalender.zahlenLesen(okText, LocalDate.of(2026, 11, 4)).orEmpty().size)
        assertEquals(0, NasdaqKalender.dividendenLesen(okText).orEmpty().size)
        assertEquals(0, NasdaqKalender.splitsLesen(ohneStatus).orEmpty().size)
        assertEquals(0, NasdaqKalender.zahlenLesen(ohneStatus, LocalDate.of(2026, 11, 4)).orEmpty().size)
        assertEquals(0, NasdaqKalender.dividendenLesen(ohneStatus).orEmpty().size)
        // so eine Antwort zaehlt auch im Abruf als Fehlschlag und setzt die Pause
        var zeit = 0L
        var abrufe = 0
        val a = NasdaqAbruf(
            netz = { _ ->
                abrufe += 1
                MarketApi.Antwort(200, fehlerText)
            },
            uhr = { zeit },
            abstandMs = 0,
        )
        org.junit.Assert.assertNull(a.holen("adr", { _ -> 60_000L }, { NasdaqKalender.splitsLesen(it) }))
        assertTrue(a.pausiert())
        // in der Pause kein weiterer Abruf
        org.junit.Assert.assertNull(a.holen("adr", { _ -> 60_000L }, { NasdaqKalender.splitsLesen(it) }))
        assertEquals(1, abrufe)
    }

    @Test
    fun trefferWartetNichtAufLaufendenAbruf() = runBlocking {
        var zeit = 0L
        val gestartet = CompletableDeferred<Unit>()
        val weiter = CompletableDeferred<Unit>()
        val a = NasdaqAbruf(
            netz = { adresse ->
                if (adresse == "b") {
                    gestartet.complete(Unit)
                    weiter.await()
                }
                MarketApi.Antwort(200, if (adresse == "b") "bbbb" else "aa")
            },
            uhr = { zeit },
            abstandMs = 0,
        )
        // Adresse a abrufen und damit speichern
        assertEquals(listOf(2), a.holen("a", { _ -> 60_000L }, { listOf(it.length) }))
        val job = launch { a.holen("b", { _ -> 60_000L }, { listOf(it.length) }) }
        gestartet.await()
        // gespeicherter Treffer kommt sofort zurueck, obwohl b noch im Netz haengt
        kotlinx.coroutines.withTimeout(1000) {
            assertEquals(listOf(2), a.holen("a", { _ -> 60_000L }, { listOf(it.length) }))
        }
        weiter.complete(Unit)
        job.join()
        assertEquals(listOf(4), a.holen("b", { _ -> 60_000L }, { listOf(it.length) }))
    }
}
