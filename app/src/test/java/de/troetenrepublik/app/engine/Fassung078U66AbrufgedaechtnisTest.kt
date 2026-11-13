package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.CompanyInfo
import de.troetenrepublik.app.engine.Abrufgedaechtnis.MINUTE
import de.troetenrepublik.app.engine.Abrufgedaechtnis.Speicher
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NasdaqAbruf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Sperre nach abgelehnten Abrufen: Staffelung, Retry-After, Ablehnung ueberschreibt nichts, Wiederherstellen nach Neustart
class Fassung078U66AbrufgedaechtnisTest {

    private val stunde = 60 * MINUTE

    @Test
    fun staffelung5Min30Min2Std12Std() {
        val s = Speicher()
        var jetzt = 1_000_000L
        val erwartet = listOf(5 * MINUTE, 30 * MINUTE, 2 * stunde, 12 * stunde, 12 * stunde)
        for (ms in erwartet) {
            val sp = s.ablehnung("q:wikipedia", 429, null, jetzt)
            assertEquals(jetzt + ms, sp.bis)
            assertTrue(s.gesperrt("q:wikipedia", jetzt + ms - 1))
            assertFalse(s.gesperrt("q:wikipedia", jetzt + ms))
            jetzt += ms
        }
    }

    @Test
    fun waehrendDerSperreSteigtDieStufeNicht() {
        val s = Speicher()
        val erste = s.ablehnung("k", 429, null, 0L)
        val zweite = s.ablehnung("k", 429, null, 60_000L)
        assertEquals(erste, zweite)
        assertEquals(1, s.sperre("k")!!.stufe)
    }

    @Test
    fun retryAfterWirdUebernommen() {
        assertEquals(120_000L, Abrufgedaechtnis.retryAfterMs("120"))
        assertEquals(120_000L, Abrufgedaechtnis.retryAfterMs(" 120 "))
        assertEquals(1_000L, Abrufgedaechtnis.retryAfterMs("0"))
        assertEquals(12 * stunde, Abrufgedaechtnis.retryAfterMs("99999999"))
        assertNull(Abrufgedaechtnis.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(Abrufgedaechtnis.retryAfterMs(null))
        assertNull(Abrufgedaechtnis.retryAfterMs("-5"))
        val s = Speicher()
        assertEquals(10_000L + 90_000L, s.ablehnung("k", 429, "90", 10_000L).bis)
        // Ein Datum gilt als nicht genannt: dann die Staffel
        assertEquals(5 * MINUTE, Speicher().ablehnung("k", 429, "Wed, 21 Oct 2026 07:28:00 GMT", 0L).bis)
    }

    @Test
    fun mindestwertDerQuelleGiltFuerDieStaffel() {
        assertEquals(10 * MINUTE, Speicher().ablehnung("k", 429, null, 0L, mindestMs = 10 * MINUTE).bis)
        assertEquals(5 * MINUTE, Speicher().ablehnung("k", 429, null, 0L, mindestMs = 60_000L).bis)
    }

    @Test
    fun forbiddenSteigtNurBisZurZweitenStufe() {
        val s = Speicher()
        var jetzt = 0L
        var letzteWartezeit = 0L
        repeat(5) {
            val sp = s.ablehnung("q:sec", 403, null, jetzt)
            letzteWartezeit = sp.bis - jetzt
            jetzt = sp.bis
        }
        assertEquals(2, s.sperre("q:sec")!!.stufe)
        assertEquals(30 * MINUTE, letzteWartezeit)
    }

    @Test
    fun erfolgHebtDieSperreAufUndDieStaffelBeginntNeu() {
        val s = Speicher()
        s.ablehnung("k", 429, null, 0L)
        assertTrue(s.erfolg("k"))
        assertFalse(s.erfolg("k"))
        assertNull(s.sperre("k"))
        assertEquals(5 * MINUTE, s.ablehnung("k", 429, null, 10 * MINUTE).bis - 10 * MINUTE)
    }

    @Test
    fun nachEinemTagBeginntDieStaffelVonVorn() {
        val s = Speicher()
        val erste = s.ablehnung("k", 429, null, 0L)
        s.ablehnung("k", 429, null, erste.bis)
        assertEquals(2, s.sperre("k")!!.stufe)
        val spaeter = s.sperre("k")!!.bis + Abrufgedaechtnis.VERGESSEN_MS + 1
        assertEquals(1, s.ablehnung("k", 429, null, spaeter).stufe)
    }

    @Test
    fun neustartStelltSperrenAusDemTextWiederHer() {
        val a = Speicher()
        val jetzt = 5_000_000L
        val s1 = a.ablehnung("q:wikipedia", 429, "600", jetzt)
        val s2 = a.ablehnung("firma:NVDA", 0, null, jetzt)
        val text = a.text(jetzt)
        val b = Speicher()
        b.laden(text, jetzt + 1_000L)
        assertEquals(s1, b.sperre("q:wikipedia"))
        assertEquals(s2, b.sperre("firma:NVDA"))
        assertTrue(b.gesperrt("q:wikipedia", jetzt + 599_000L))
        assertFalse(b.gesperrt("q:wikipedia", jetzt + 600_000L))
        // Beschaedigte Zeilen fallen allein weg
        val c = Speicher()
        c.laden("kaputt\nk\tabc\t1\n\t5\t1\nok\t${jetzt + 1000}\t3\nzuwenig\t5", jetzt)
        assertEquals(1, c.anzahl())
        assertEquals(3, c.sperre("ok")!!.stufe)
        // Lange abgelaufene Eintraege werden nicht geladen
        val d = Speicher()
        d.laden("alt\t0\t2", Abrufgedaechtnis.VERGESSEN_MS + 1)
        assertEquals(0, d.anzahl())
        // Tabulator und Zeilenumbruch im Schluessel zerstoeren die Ablage nicht
        val e = Speicher()
        e.ablehnung("a\tb\nc", 429, null, jetzt)
        val f = Speicher()
        f.laden(e.text(jetzt), jetzt)
        assertEquals(1, f.anzahl())
    }

    @Test
    fun groesseIstBegrenztAelteresFaellt() {
        val s = Speicher(maxEintraege = 3)
        for (i in 1..5) s.ablehnung("k$i", 429, null, i * 1_000L)
        assertEquals(3, s.anzahl())
        assertNull(s.sperre("k1"))
        assertNull(s.sperre("k2"))
        assertNotNull(s.sperre("k5"))
    }

    @Test
    fun fehlerseiteStattDatenWirdErkannt() {
        assertTrue(Abrufgedaechtnis.htmlStattDaten("<!DOCTYPE html><html><body>Too many requests</body></html>"))
        assertTrue(Abrufgedaechtnis.htmlStattDaten("﻿  \n<html lang=\"en\">"))
        assertFalse(Abrufgedaechtnis.htmlStattDaten("{\"query\":{\"search\":[]}}"))
        assertFalse(Abrufgedaechtnis.htmlStattDaten("<?xml version=\"1.0\"?><rss></rss>"))
        assertFalse(Abrufgedaechtnis.htmlStattDaten(null))
        assertTrue(Abrufgedaechtnis.abgelehnt(429))
        assertTrue(Abrufgedaechtnis.abgelehnt(403))
        assertTrue(Abrufgedaechtnis.abgelehnt(503))
        assertFalse(Abrufgedaechtnis.abgelehnt(404))
        assertFalse(Abrufgedaechtnis.abgelehnt(200))
        assertFalse(Abrufgedaechtnis.abgelehnt(0))
    }

    @Test
    fun ablehnungUeberschreibtKeineBeschreibung() {
        val alt = CompanyInfo(
            "NVDA", "US67066G1040", "logo", "https://www.nvidia.com", "Beschreibung de", "Description en", fetchedAt = 1L,
            missing = false, articleDe = "https://de.wikipedia.org/wiki/Nvidia", articleEn = "https://en.wikipedia.org/wiki/Nvidia",
        )
        // Suche fand mit Antworten nichts: der fruehere Treffer bleibt, nur die Abrufzeit rueckt vor
        val keinFund = Abrufgedaechtnis.firmaBehalten(CompanyInfo("NVDA", fetchedAt = 99L, missing = true), alt)
        assertEquals(alt.copy(fetchedAt = 99L), keinFund)
        assertFalse(keinFund.missing)
        // Zusammenfassung kam nicht an: Beschreibungen bleiben, neue Felder kommen dazu
        val teilweise = Abrufgedaechtnis.firmaBehalten(CompanyInfo("NVDA", "", "", "", "", "", fetchedAt = 50L, articleDe = "https://de.wikipedia.org/wiki/Neu"), alt)
        assertEquals("Beschreibung de", teilweise.descriptionDe)
        assertEquals("Description en", teilweise.descriptionEn)
        assertEquals("logo", teilweise.logoUrl)
        assertEquals("https://de.wikipedia.org/wiki/Neu", teilweise.articleDe)
        assertEquals(50L, teilweise.fetchedAt)
        // Ohne fruehere Angaben gilt der neue Stand, auch ohne Fund
        val leer = CompanyInfo("X", fetchedAt = 5L, missing = true)
        assertEquals(leer, Abrufgedaechtnis.firmaBehalten(leer, null))
        assertEquals(leer, Abrufgedaechtnis.firmaBehalten(leer, CompanyInfo("X", fetchedAt = 1L, missing = true)))
        // Neuer Treffer ersetzt einen frueheren Nichtfund
        val treffer = CompanyInfo("X", descriptionEn = "Hit", fetchedAt = 7L)
        assertEquals(treffer, Abrufgedaechtnis.firmaBehalten(treffer, CompanyInfo("X", fetchedAt = 1L, missing = true)))
    }

    // Ablage mit steuerbarer Uhr, wie sie die App ueber einen Neustart hinweg nutzt
    private class Ablage(val speicher: Speicher, val zeit: () -> Long) : Abrufgedaechtnis.Dienst {
        override fun gesperrtBis(schluessel: String) = speicher.bis(schluessel, zeit())
        override fun ablehnung(schluessel: String, code: Int, retryAfter: String?, mindestMs: Long) =
            speicher.ablehnung(schluessel, code, retryAfter, zeit(), mindestMs).bis
        override fun erfolg(schluessel: String) { speicher.erfolg(schluessel) }
    }

    @Test
    fun nasdaqPauseUeberlebtEinenNeustart() = runBlocking {
        var zeit = 10_000_000L
        var abrufe = 0
        var antwort = MarketApi.Antwort(429, null, mapOf("retry-after" to "3600"))
        val ablage1 = Ablage(Speicher()) { zeit }
        val a = NasdaqAbruf(netz = { _ -> abrufe += 1; antwort }, uhr = { zeit }, abstandMs = 0, sperre = ablage1)
        val lesen: (String) -> List<Int>? = { listOf(it.length) }
        assertNull(a.holen("adr", { 60_000L }, lesen))
        assertEquals(1, abrufe)
        assertTrue(a.pausiert())
        // Neustart: neue Instanz, Sperren aus dem abgelegten Text
        val text = ablage1.speicher.text(zeit)
        val geladen = Speicher().also { it.laden(text, zeit) }
        val b = NasdaqAbruf(netz = { _ -> abrufe += 1; antwort }, uhr = { zeit }, abstandMs = 0, sperre = Ablage(geladen) { zeit })
        assertTrue(b.pausiert())
        assertNull(b.holen("adr", { 60_000L }, lesen))
        assertEquals(1, abrufe)
        // Retry-After (eine Stunde) bestimmt das Ende
        zeit += 3_600_000L - 1
        assertTrue(b.pausiert())
        zeit += 1
        assertFalse(b.pausiert())
        antwort = MarketApi.Antwort(200, "abc")
        assertEquals(listOf(3), b.holen("adr", { 60_000L }, lesen))
        assertEquals(2, abrufe)
        // Erfolg hebt die gemerkte Sperre auf
        assertNull(geladen.sperre("nasdaq"))
    }

    @Test
    fun nasdaqFehlerseiteStattDatenWirdAlsAblehnungGemerkt() = runBlocking {
        var zeit = 0L
        val speicher = Speicher()
        val a = NasdaqAbruf(netz = { _ -> MarketApi.Antwort(200, "<html><body>Access denied</body></html>") }, uhr = { zeit }, abstandMs = 0, sperre = Ablage(speicher) { zeit })
        assertNull(a.holen("adr", { 60_000L }) { _: String -> null as List<Int>? })
        assertNotNull(speicher.sperre("nasdaq"))
        assertTrue(speicher.gesperrt("nasdaq", 1_000L))
    }

    @Test
    fun nasdaqOhneDatenOhneAblehnungPausiertNurInDerSitzung() = runBlocking {
        var zeit = 0L
        val speicher = Speicher()
        val a = NasdaqAbruf(netz = { _ -> MarketApi.Antwort(404, null) }, uhr = { zeit }, abstandMs = 0, sperre = Ablage(speicher) { zeit })
        assertNull(a.holen("adr", { 60_000L }) { _: String -> null as List<Int>? })
        assertTrue(a.pausiert())
        assertNull(speicher.sperre("nasdaq"))
    }
}
