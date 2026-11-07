package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.Abrufkontingent
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NachrichtenApis
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Uebersetzer
import de.troetenrepublik.app.ui.PmTexte
import de.troetenrepublik.app.ui.pmText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

// Abrufkontingente, Nachrichten mit eigenem Schluessel (Guardian, Finnhub, Alpha Vantage), DeepL, Verlauf der Wetten,
// Woerterbuch der Prediction Markets, Sprache nach System. Die Testdaten bilden nur den Aufbau der Antworten nach,
// keine Inhalte der Anbieter.
class Fassung28Test {

    // Kontingent

    @Test
    fun kontingentGleitendesFensterUndAbstand() {
        var jetzt = 1_000_000_000L
        val k = Abrufkontingent(null) { jetzt }
        // Alpha Vantage: 22 je 24 Stunden, 4 je Minute, 15 Sekunden Abstand
        repeat(5) { assertTrue(k.erlaubt(Abrufkontingent.ALPHAVANTAGE)); k.buchen(Abrufkontingent.ALPHAVANTAGE); jetzt += 15_000 }
        jetzt -= 15_000
        assertFalse("Mindestabstand", k.erlaubt(Abrufkontingent.ALPHAVANTAGE))
        assertEquals(15_000L, k.warten(Abrufkontingent.ALPHAVANTAGE))
        repeat(17) { jetzt += 3_600_000L; k.buchen(Abrufkontingent.ALPHAVANTAGE) }
        jetzt += 60_000
        assertEquals(22L to 22L, k.stand(Abrufkontingent.ALPHAVANTAGE))
        assertFalse("Tageslimit", k.erlaubt(Abrufkontingent.ALPHAVANTAGE))
        // die ersten Abrufe fallen nach 24 Stunden aus dem Fenster
        jetzt += 7 * 3_600_000L
        assertTrue(k.erlaubt(Abrufkontingent.ALPHAVANTAGE))
    }

    @Test
    fun kontingentZeichenUndDatei() {
        var jetzt = 2_000_000_000L
        val datei = File.createTempFile("abrufe", ".json").apply { deleteOnExit() }
        val k = Abrufkontingent(datei) { jetzt }
        assertTrue(k.erlaubt(Abrufkontingent.DEEPL, 400_000))
        k.buchen(Abrufkontingent.DEEPL, 400_000)
        assertFalse(k.erlaubt(Abrufkontingent.DEEPL, 60_000))
        assertTrue(k.erlaubt(Abrufkontingent.DEEPL, 50_000))
        // nach einem Neustart gilt derselbe Stand
        val neu = Abrufkontingent(datei) { jetzt }
        assertEquals(400_000L, neu.stand(Abrufkontingent.DEEPL).first)
        neu.erschoepft(Abrufkontingent.GUARDIAN)
        assertFalse(neu.erlaubt(Abrufkontingent.GUARDIAN))
        jetzt += 32 * 86_400_000L
        assertTrue(neu.erlaubt(Abrufkontingent.DEEPL, 400_000))
    }

    // Leser der Dienste

    @Test
    fun guardianListeArtikelUndFehler() {
        val liste = """{"response":{"status":"ok","results":[
            {"id":"business/2026/oct/14/beispiel","webTitle":"Beispielueberschrift","webUrl":"https://www.theguardian.com/business/2026/oct/14/beispiel",
             "webPublicationDate":"2026-10-14T20:00:00Z","fields":{"trailText":"<p>Anriss</p>","byline":"Autorin A"}}]}}"""
        val e = NachrichtenApis.guardianLesen(liste, "The Guardian", Rss.Kategorie.MAERKTE, "guardian-maerkte")
        assertNull(e.meldung)
        val x = e.eintraege.single()
        assertEquals("Autorin A", x.autor)
        assertEquals("Anriss", x.zusammenfassung)
        assertEquals("theguardian.com", x.herausgeber)
        assertEquals(java.time.Instant.parse("2026-10-14T20:00:00Z").toEpochMilli(), x.zeit)
        assertTrue(NachrichtenApis.istGuardian(x.link))
        assertEquals(
            "https://content.guardianapis.com/business/2026/oct/14/beispiel?show-fields=body,byline,headline&api-key=k1",
            NachrichtenApis.guardianArtikel(x.link, "k1"),
        )
        val artikel = NachrichtenApis.guardianArtikelLesen("""{"response":{"status":"ok","content":{"webTitle":"T","fields":{"headline":"H","byline":"B","body":"<p>Erster &amp; Absatz</p><figure>x</figure><p>Zweiter</p>"}}}}""")!!
        assertEquals(listOf("Erster & Absatz", "Zweiter"), artikel.absaetze)
        assertEquals("B", artikel.autor)
        assertEquals("Invalid key", NachrichtenApis.guardianLesen("""{"message":"Invalid key"}""", "G", Rss.Kategorie.MAERKTE, "g").meldung)
        assertFalse(NachrichtenApis.istGuardian("https://www.cnbc.com/x"))
    }

    @Test
    fun finnhubUndAlphaVantage() {
        val f = """[{"category":"top news","datetime":1791990000,"headline":"Kopfzeile","id":1,"image":"","related":"","source":"Reuters",
            "summary":"Kurz","url":"https://finnhub.io/api/news?id=abc"},
            {"category":"top news","datetime":1791990100,"headline":"Zweite","id":2,"source":"CNBC","summary":"","url":"https://www.cnbc.com/2026/10/14/x.html"}]"""
        val e = NachrichtenApis.finnhubLesen(f, "Finnhub", Rss.Kategorie.MAERKTE, "finnhub-maerkte").eintraege
        assertEquals(2, e.size)
        assertEquals("reuters.com", e[0].herausgeber)
        assertEquals(1_791_990_000_000L, e[0].zeit)
        assertEquals("cnbc.com", e[1].herausgeber)
        assertEquals("Invalid API key.", NachrichtenApis.finnhubLesen("""{"error":"Invalid API key."}""", "F", Rss.Kategorie.MAERKTE, "f").meldung)

        val av = """{"items":"1","feed":[{"title":"Titel","url":"https://www.benzinga.com/x","time_published":"20261014T205308",
            "summary":"Zusammenfassung","source":"Benzinga","source_domain":"www.benzinga.com","topics":[]}]}"""
        val a = NachrichtenApis.alphaVantageLesen(av, "Alpha Vantage", Rss.Kategorie.MAERKTE, "alphavantage").eintraege.single()
        assertEquals("benzinga.com", a.herausgeber)
        assertEquals(java.time.Instant.parse("2026-10-14T20:53:08Z").toEpochMilli(), a.zeit)
        val info = NachrichtenApis.alphaVantageLesen("""{"Information":"premium endpoint"}""", "A", Rss.Kategorie.MAERKTE, "a")
        assertTrue(info.eintraege.isEmpty())
        assertEquals("premium endpoint", info.meldung)
        assertTrue(NachrichtenApis.alphaVantageListe("k").contains("function=NEWS_SENTIMENT"))
    }

    @Test
    fun quellenMitSchluessel() {
        assertTrue(Rss.mitSchluessel("", "", "").isEmpty())
        val q = Rss.mitSchluessel("g", "f", "a")
        assertEquals(2, q.count { it.anbieter == Rss.Anbieter.GUARDIAN })
        assertEquals(4, q.count { it.anbieter == Rss.Anbieter.FINNHUB })
        assertEquals(1, q.count { it.anbieter == Rss.Anbieter.ALPHAVANTAGE })
        // mit den Abstaenden passen die Abrufe eines ganzen Tages in die Kontingente
        val tag = 86_400_000L
        assertTrue(q.filter { it.art == Rss.Art.GUARDIAN }.sumOf { tag / it.abstandMs } <= 450)
        assertTrue(q.filter { it.art == Rss.Art.ALPHAVANTAGE }.sumOf { tag / it.abstandMs } <= 24)
    }

    @Test
    fun gemerkteGuardianArtikelVerfallen() {
        val jetzt = 1_800_000_000_000L
        val g = Rss.Eintrag("G", "https://www.theguardian.com/a", jetzt, gemerktAm = jetzt - 25 * 3_600_000L)
        val g2 = Rss.Eintrag("G2", "https://www.theguardian.com/b", jetzt, gemerktAm = jetzt - 3_600_000L)
        val c = Rss.Eintrag("C", "https://www.cnbc.com/c", jetzt, gemerktAm = jetzt - 30 * 86_400_000L)
        assertEquals(listOf("G2", "C"), Rss.gemerktGueltig(listOf(g, g2, c), jetzt).map { it.titel })
        // Merkzeit bleibt beim Speichern erhalten
        val zurueck = Rss.gemerktLesen(Rss.gemerktText(listOf(g2)))
        assertEquals(g2.gemerktAm, zurueck.single().gemerktAm)
    }

    // DeepL

    @Test
    fun uebersetzerMerktUndZaehlt() = runBlocking {
        val k = Abrufkontingent(null)
        val gesendet = ArrayList<List<String>>()
        val u = Uebersetzer({ url, kopf, formular ->
            assertEquals("https://api-free.deepl.com/v2/translate", url)
            assertEquals("DeepL-Auth-Key abc:fx", kopf["Authorization"])
            val texte = formular.filter { it.first == "text" }.map { it.second }
            gesendet += texte
            MarketApi.Antwort(200, """{"translations":[""" + texte.joinToString(",") { """{"text":"DE $it"}""" } + "]}")
        }, k, null)
        val erst = u.deutsch(listOf("Who will win?", "Who will win?", "Fed decision"), "abc:fx")
        assertEquals(mapOf("Who will win?" to "DE Who will win?", "Fed decision" to "DE Fed decision"), erst)
        assertEquals(1, gesendet.size)
        assertEquals(("Who will win?".length + "Fed decision".length).toLong(), k.stand(Abrufkontingent.DEEPL).first)
        // bekannte Texte kosten nichts
        assertTrue(u.deutsch(listOf("Fed decision"), "abc:fx").isEmpty())
        assertEquals(1, gesendet.size)
        // Limit des Anbieters: Kontingent erschoepft
        val voll = Uebersetzer({ _, _, _ -> MarketApi.Antwort(456, "{}") }, k, null)
        assertTrue(voll.deutsch(listOf("neu"), "abc:fx").isEmpty())
        assertFalse(k.erlaubt(Abrufkontingent.DEEPL, 10))
    }

    @Test
    fun woerterbuchDerMarkte() {
        val vorher = I18n.lang
        try {
            I18n.lang = Lang.DE
            PmTexte.karte.clear()
            assertEquals("Unentschieden", pmText("Tie"))
            assertEquals("Unentschieden (A vs. B)", pmText("Draw (A vs. B)"))
            assertEquals("Ajax vs. PSV – weitere Märkte", pmText("Ajax vs. PSV - More Markets"))
            assertEquals("Andere", pmText("Other"))
            PmTexte.karte["Fed decision"] = "Fed-Entscheidung"
            assertEquals("Fed-Entscheidung", pmText("Fed decision"))
            I18n.lang = Lang.EN
            assertEquals("Tie", pmText("Tie"))
            assertEquals("Fed decision", pmText("Fed decision"))
        } finally {
            I18n.lang = vorher
            PmTexte.karte.clear()
        }
        assertEquals("https://assets.kalshi.com/series-images-webp/KXFEDDECISION.webp", Prognose.kalshiBild("KXFEDDECISION"))
    }

    @Test
    fun spracheNachSystem() {
        val vorher = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(Lang.DE, Lang.system())
            Locale.setDefault(Locale("de", "AT"))
            assertEquals(Lang.DE, Lang.system())
            Locale.setDefault(Locale.FRANCE)
            assertEquals(Lang.EN, Lang.system())
            assertEquals(Lang.EN, de.troetenrepublik.app.data.Settings(languageAuto = true, language = Lang.DE).sprache)
            assertEquals(Lang.DE, de.troetenrepublik.app.data.Settings(languageAuto = false, language = Lang.DE).sprache)
        } finally {
            Locale.setDefault(vorher)
        }
    }

    // Verlauf der Wetten

    @Test
    fun wettverlaufAusBuchungenUndKursen() {
        val t0 = 1_800_000_000_000L
        val tx = listOf(
            Transaction("1", t0, TxType.BET, "Ereignis", amount = -10.0, qty = 20.0, price = 0.5, key = "pm:w1", tag = "BET_BUY"),
            Transaction("2", t0 + 2_000, TxType.BET, "Ereignis", amount = 14.0, qty = 20.0, price = 0.7, key = "pm:w1", tag = "BET_SELL"),
        )
        val kurse = mapOf("w1" to listOf(t0 to 0.5, t0 + 1_000 to 0.6, t0 + 3_000 to 0.9))
        val v = Wetten.verlauf(tx, kurse, emptyMap(), jetzt = t0 + 4_000)
        // Kauf: -10 Geld + 20 x 0,5 = 0; bei 0,6: +2; nach Verkauf fuer 14: +4 und danach fest
        assertEquals(listOf(t0, t0 + 1_000, t0 + 2_000, t0 + 3_000, t0 + 4_000), v.map { it.first })
        assertEquals(listOf(0.0, 2.0, 4.0, 4.0, 4.0), v.map { Math.round(it.second * 1000) / 1000.0 })
        // offene Wette: aktueller Kurs am Ende
        val offen = Wetten.verlauf(tx.take(1), kurse, mapOf("w1" to 0.8), jetzt = t0 + 4_000)
        assertEquals(6.0, offen.last().second, 1e-9)
        assertEquals(2, Wetten.buchungen(tx).size)
        assertTrue(Wetten.buchungen(tx).first().kauf)
    }
}
