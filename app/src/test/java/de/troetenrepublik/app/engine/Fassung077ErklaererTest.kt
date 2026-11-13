package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Erklaerer.Einstellung
import de.troetenrepublik.app.engine.Erklaerer.Ergebnis
import de.troetenrepublik.app.engine.Erklaerer.Fehlerart
import de.troetenrepublik.app.engine.Erklaerer.Nachricht
import de.troetenrepublik.app.engine.Erklaerer.Roh
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Erklaer-Assistent: Aufbau der Anfragen je Anbieter, Auswertung der Antworten und Fehler, der Schluessel in keiner Meldung.
// Gesendet wird nie, die Antworten kommen von einem Ersatz fuer den Sendeweg.
class Fassung077ErklaererTest {
    private val schluessel = "sk-test-0123456789abcdef"
    private val anthropic = Einstellung(Erklaerer.ANTHROPIC, "", "", schluessel)
    private val openai = Einstellung(Erklaerer.OPENAI, "https://llm.example.org/", "modell-x", schluessel)
    private val nachrichten = listOf(Nachricht("user", "Erkläre mir diese Angaben."))

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test fun anthropicAnfrage() {
        val a = Erklaerer.anfrage(anthropic, Erklaerer.systemAnweisung(true), nachrichten)!!
        assertEquals("https://api.anthropic.com/v1/messages", a.url)
        assertEquals(schluessel, a.kopf["x-api-key"])
        assertEquals("2023-06-01", a.kopf["anthropic-version"])
        assertEquals("application/json", a.kopf["content-type"])
        assertFalse(a.kopf.containsKey("Authorization"))
        val j = obj(a.json)
        assertEquals("claude-sonnet-5-5", j["model"]!!.jsonPrimitive.content)
        assertEquals(1024, j["max_tokens"]!!.jsonPrimitive.int)
        assertTrue(j["system"]!!.jsonPrimitive.content.contains("keine Kauf- oder Verkaufsempfehlung"))
        val m = (j["messages"] as JsonArray)[0] as JsonObject
        assertEquals("user", m["role"]!!.jsonPrimitive.content)
        assertFalse(a.json.contains(schluessel))
        assertFalse(a.toString().contains(schluessel))
    }

    @Test fun openaiAnfrage() {
        val a = Erklaerer.anfrage(openai, "System", nachrichten)!!
        assertEquals("https://llm.example.org/v1/chat/completions", a.url)
        assertEquals("Bearer $schluessel", a.kopf["Authorization"])
        assertFalse(a.kopf.containsKey("x-api-key"))
        val msgs = obj(a.json)["messages"] as JsonArray
        assertEquals("system", (msgs[0] as JsonObject)["role"]!!.jsonPrimitive.content)
        assertEquals("user", (msgs[1] as JsonObject)["role"]!!.jsonPrimitive.content)
        assertEquals("modell-x", obj(a.json)["model"]!!.jsonPrimitive.content)
    }

    @Test fun adresseUndWahl() {
        assertEquals("https://api.openai.com", Erklaerer.basisadresse("https://api.openai.com/v1/"))
        assertEquals("http://localhost:11434", Erklaerer.basisadresse("http://localhost:11434"))
        assertEquals("http://[::1]:8080", Erklaerer.basisadresse("http://[::1]:8080"))
        assertNull(Erklaerer.basisadresse("http://example.org"))
        assertNull(Erklaerer.basisadresse("https://nutzer:pw@example.org"))
        assertNull(Erklaerer.basisadresse("https://example.org/?k=1"))
        assertNull(Erklaerer.basisadresse("ftp://example.org"))
        assertNull(Erklaerer.anfrage(openai.copy(adresse = ""), "s", nachrichten))
        assertNull(Erklaerer.anfrage(anthropic.copy(schluessel = " "), "s", nachrichten))
        assertFalse(anthropic.copy(schluessel = "").bereit)
        assertTrue(anthropic.bereit)
    }

    @Test fun antwortenUndFehler() {
        val ok = """{"id":"x","type":"message","content":[{"type":"thinking","text":"nein"},{"type":"text","text":"Das KGV "},{"type":"text","text":"ist 31."}]}"""
        assertEquals(Ergebnis.Text("Das KGV ist 31."), Erklaerer.auswerten(anthropic, Roh(200, ok)))
        val oa = """{"choices":[{"message":{"role":"assistant","content":" Marge ist hoch. "}}]}"""
        assertEquals(Ergebnis.Text("Marge ist hoch."), Erklaerer.auswerten(openai, Roh(200, oa)))
        assertEquals(Ergebnis.Fehler(Fehlerart.LEER), Erklaerer.auswerten(anthropic, Roh(200, """{"content":[]}""")))
        fun fehler(code: Int) = (Erklaerer.auswerten(anthropic, Roh(code, """{"type":"error","error":{"type":"x","message":"fehler"}}""")) as Ergebnis.Fehler).art
        assertEquals(Fehlerart.SCHLUESSEL, fehler(401))
        assertEquals(Fehlerart.LIMIT, fehler(429))
        assertEquals(Fehlerart.UEBERLASTET, fehler(529))
        assertEquals(Fehlerart.ANBIETER, fehler(400))
        assertEquals(Fehlerart.NETZ, (Erklaerer.auswerten(anthropic, Roh(0, null)) as Ergebnis.Fehler).art)
    }

    @Test fun schluesselNieInMeldung() {
        val roh = Roh(400, """{"error":{"message":"bad key $schluessel and ${schluessel.take(10)}xyz here"}}""")
        val f = Erklaerer.auswerten(anthropic, roh) as Ergebnis.Fehler
        assertFalse(f.meldung.contains(schluessel))
        assertFalse(f.meldung.contains(schluessel.take(8)))
        assertTrue(f.meldung.contains("here"))
    }

    @Test fun rundeUeberErsatzSendeweg() = runBlocking {
        var gesehen: Erklaerer.Anfrage? = null
        val r = Erklaerer.fragen({ a -> gesehen = a; Roh(200, """{"content":[{"type":"text","text":"Antwort"}]}""") }, anthropic, true, nachrichten)
        assertEquals(Ergebnis.Text("Antwort"), r)
        assertNotNull(gesehen)
        val e = Erklaerer.fragen({ throw java.io.IOException("kaputt $schluessel") }, anthropic, false, nachrichten) as Ergebnis.Fehler
        assertEquals(Fehlerart.NETZ, e.art)
        assertTrue(Erklaerer.systemAnweisung(false).endsWith("Antworte auf Englisch."))
    }

    @Test fun gespraechBleibtBegrenzt() {
        val lang = listOf(Nachricht("user", "kontext")) + (1..30).map { Nachricht(if (it % 2 == 1) "assistant" else "user", "n$it") }
        val b = Erklaerer.begrenzen(lang)
        assertTrue(b.size <= Erklaerer.MAX_NACHRICHTEN)
        assertEquals("kontext", b.first().text)
        assertEquals("user", b.first().rolle)
        assertEquals("assistant", b[1].rolle)
    }

    @Test fun kontextOhneDepot() {
        val p = MarketApi.Profile(sector = "Technology", trailingPe = 31.2, dividendYield = 0.5, marketCap = 3.0e12, grossMargin = 45.0)
        val k = Erklaerer.kontextWertpapier("Apple", "AAPL", "stock", 190.5, "USD", 1.25, p)
        assertTrue(k.contains("P/E trailing: 31.2"))
        assertTrue(k.contains("Market cap: 3.0tn"))
        assertTrue(k.contains("change today +1.25 %"))
        assertFalse(k.contains("Beta"))
        val f = Erklaerer.kontextFinanzen("Apple", p.copy(yearly = listOf(MarketApi.FinPeriod("2025", 0L, 4.0e11, 1.0e11))), false)
        assertTrue(f.contains("2025: revenue 400.0bn, net income 100.0bn"))
        val m = Zahlenmitteilung.Mitteilung(
            listOf(Zahlenmitteilung.Kennzahl(Zahlenmitteilung.Art.entries.first(), "Revenue", "\$10 million", 10.0)), emptyList(), listOf("Absatz eins"),
        )
        val t = Erklaerer.kontextMitteilung("AAPL", "2026-10-30", m, null)
        assertTrue(t.contains("Revenue: \$10 million") && t.contains("Absatz eins"))
        assertTrue(Erklaerer.ersteNachricht(true, "x".repeat(20_000)).length < 9_100)
    }
}
