package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.RedditAuswertung.Beitrag
import de.troetenrepublik.app.engine.RedditAuswertung.Kandidat
import de.troetenrepublik.app.market.Abrufkontingent
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NewsRepository
import de.troetenrepublik.app.market.RedditApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Erfundene Beitraege.
class RedditTest {

    private val sap = Kandidat("SAP.DE", "SAP")
    private val rheinmetall = Kandidat("RHM.DE", "Rheinmetall")
    private val mercedes = Kandidat("MBG.DE", "Mercedes-Benz Group")
    private val nvidia = Kandidat("NVDA", "NVIDIA Corporation")
    private val allstate = Kandidat("ALL", "Allstate")
    private val block = Kandidat("XYZ", "Block")

    private fun b(id: String, titel: String, text: String = "", stunden: Double = 1.0, punkte: Int = 10) =
        Beitrag(id, titel, text, "wallstreetbetsGER", punkte, 3, JETZT - (stunden * 3_600_000).toLong(), "https://www.reddit.com/r/x/$id")

    @Test
    fun kurznamen() {
        assertEquals("Mercedes-Benz", RedditAuswertung.kurzname("Mercedes-Benz Group"))
        assertEquals("Volkswagen", RedditAuswertung.kurzname("Volkswagen Vz."))
        assertEquals("NVIDIA", RedditAuswertung.kurzname("NVIDIA Corporation"))
        assertEquals("Münchener Rück", RedditAuswertung.kurzname("Münchener Rück AG"))
    }

    @Test
    fun werteErkennen() {
        assertTrue(RedditAuswertung.nennt("Rheinmetall geht durch die Decke", rheinmetall))
        assertTrue(RedditAuswertung.nennt("Calls auf \$RHM gekauft", rheinmetall))
        assertTrue(RedditAuswertung.nennt("Was haltet ihr von SAP?", sap))
        assertTrue(RedditAuswertung.nennt("Mercedes-Benz nach den Zahlen", mercedes))
        assertTrue(RedditAuswertung.nennt("NVDA earnings morgen", nvidia))
        assertTrue(RedditAuswertung.nennt("Nvidia ist zu teuer", nvidia))
        // Teilwoerter und gewoehnliche Woerter zaehlen nicht.
        assertFalse(RedditAuswertung.nennt("Rheinmetallaktie", rheinmetall))
        assertFalse(RedditAuswertung.nennt("Ich habe ALL in gesetzt", allstate))
        assertTrue(RedditAuswertung.nennt("\$ALL nach Zahlen", allstate))
        assertFalse(RedditAuswertung.nennt("Ein Block an Aktien", block))
        assertFalse(RedditAuswertung.nennt("Saphir ist kein Wert", sap))
    }

    @Test
    fun stimmung() {
        assertEquals(1, RedditAuswertung.richtung(b("1", "Calls auf Rheinmetall, to the moon 🚀")))
        assertEquals(-1, RedditAuswertung.richtung(b("2", "Puts auf SAP, das crasht")))
        assertEquals(0, RedditAuswertung.richtung(b("3", "Was haltet ihr von SAP?")))
        val s = RedditAuswertung.stimmung(listOf(b("1", "long rakete"), b("2", "short"), b("3", "Frage"), b("4", "Bulle")))
        assertEquals(2, s.steigend)
        assertEquals(1, s.fallend)
        assertEquals(1, s.offen)
    }

    @Test
    fun zaehlen() {
        val beitraege = listOf(
            b("1", "Rheinmetall Calls", stunden = 1.0, punkte = 50),
            b("2", "RHM und SAP", stunden = 2.0, punkte = 5),
            b("3", "Rheinmetall Rheinmetall Rheinmetall", stunden = 3.0),
            b("3", "doppelt geliefert: Rheinmetall", stunden = 3.0),
            b("4", "SAP gestern", stunden = 30.0),
            b("5", "SAP vorgestern", stunden = 47.0),
            b("6", "alt", stunden = 60.0),
        )
        val z = RedditAuswertung.zaehlen(beitraege, listOf(sap, rheinmetall, nvidia), JETZT)
        assertEquals(listOf("RHM.DE", "SAP.DE"), z.map { it.symbol })
        assertEquals(3, z[0].erwaehnungen)
        assertEquals(0, z[0].vorher)
        assertEquals(1, z[1].erwaehnungen)
        assertEquals(2, z[1].vorher)
        // Reichen die Beitraege nicht zwei Tage zurueck, gibt es keinen Vorwert.
        val kurz = RedditAuswertung.zaehlen(beitraege.take(3), listOf(sap, rheinmetall), JETZT)
        assertNull(kurz.first().vorher)
    }

    @Test
    fun anmeldungUndListe() = runBlocking {
        val anfragen = ArrayList<Triple<String, Map<String, String>, List<Pair<String, String>>?>>()
        val liste = """{"kind":"Listing","data":{"children":[
            {"kind":"t3","data":{"id":"a1","title":"Rheinmetall Calls","selftext":"Text","subreddit":"wallstreetbetsGER","score":42,"num_comments":7,"created_utc":1900000000.0,"permalink":"/r/wallstreetbetsGER/comments/a1/x/","over_18":false,"stickied":false}},
            {"kind":"t3","data":{"id":"a2","title":"Angeheftet","subreddit":"wallstreetbetsGER","score":1,"num_comments":0,"created_utc":1900000000.0,"permalink":"/r/x/","stickied":true}},
            {"kind":"t3","data":{"id":"a3","title":"NSFW","subreddit":"x","score":1,"num_comments":0,"created_utc":1900000000.0,"permalink":"/r/x/","over_18":true}}
        ]}}"""
        val api = RedditApi({ url, kopf, formular ->
            anfragen += Triple(url, kopf, formular)
            if (url == RedditApi.TOKEN) MarketApi.Antwort(200, """{"access_token":"t0k","token_type":"bearer","expires_in":86400}""")
            else MarketApi.Antwort(200, liste)
        }, Abrufkontingent(null, uhr()), { JETZT })
        val url = RedditApi.neuAdresse(RedditAuswertung.DEUTSCHE_SUBS)
        val l = api.liste(url, "meine-id", "")!!
        assertEquals(1, l.size)
        assertEquals("Rheinmetall Calls", l[0].titel)
        assertEquals(42, l[0].punkte)
        assertEquals(1_900_000_000_000L, l[0].zeit)
        assertEquals("https://www.reddit.com/r/wallstreetbetsGER/comments/a1/x/", l[0].link)
        // Ohne Secret als installierte App, ohne Geraetekennung.
        assertEquals("https://oauth.reddit.com/grants/installed_client", anfragen[0].third!!.toMap()["grant_type"])
        assertEquals("DO_NOT_TRACK_THIS_DEVICE", anfragen[0].third!!.toMap()["device_id"])
        assertTrue(anfragen[0].second["Authorization"]!!.startsWith("Basic "))
        assertEquals("bearer t0k", anfragen[1].second["Authorization"])
        // Der Zugangsschluessel wird wiederverwendet.
        api.liste(url, "meine-id", "")
        assertEquals(1, anfragen.count { it.first == RedditApi.TOKEN })
        assertNull(api.meldung)
    }

    @Test
    fun abgelehnterSchluessel() = runBlocking {
        val api = RedditApi({ _, _, _ -> MarketApi.Antwort(401, """{"message":"Unauthorized","error":401}""") }, Abrufkontingent(null) { JETZT }, { JETZT })
        assertNull(api.liste(RedditApi.neuAdresse(listOf("Aktien")), "falsch", "geheim"))
        assertEquals(NewsRepository.SCHLUESSEL, api.meldung)
        assertNull(api.liste(RedditApi.neuAdresse(listOf("Aktien")), "", ""))
    }

    @Test
    fun frageUndAdressen() {
        assertEquals("\"RHM\" OR \"Rheinmetall\"", RedditApi.frage("RHM.DE", "Rheinmetall AG"))
        assertEquals("\"SAP\"", RedditApi.frage("SAP.DE", "SAP"))
        assertEquals("\"BTC\" OR \"Bitcoin\"", RedditApi.frage("BTC-EUR", "Bitcoin"))
        val a = RedditApi.suchAdresse(listOf("Aktien", "Finanzen"), "\"SAP\"")
        assertTrue(a.startsWith("https://oauth.reddit.com/r/Aktien+Finanzen/search?q=%22SAP%22&restrict_sr=1&sort=new"))
        assertEquals(listOf("grant_type" to "client_credentials"), RedditApi.anmeldeFormular("geheim"))
    }

    // Laeuft bei jedem Blick eine Sekunde weiter, wegen des Mindestabstands zwischen Abrufen.
    private fun uhr(): () -> Long { var t = JETZT; return { t += 1_000; t } }

    private companion object {
        const val JETZT = 1_900_100_000_000L
    }
}
