package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.engine.EigenerFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 0.78: Sprache je Nachrichtenquelle und der Schalter „Nur englische Quellen“.
class Fassung078U8SpracheTest {

    private fun alle(deutsch: Boolean) = Rss.eingebaut(deutsch) + Rss.mitSchluessel("g", "f", "a")

    private fun host(q: Rss.Quelle) = java.net.URI(q.adresse).host.orEmpty().lowercase()

    // Deutschsprachige Herausgeber, deren Adresse nicht auf .de oder .at endet.
    private val deutschOhneLandesendung = setOf("handelsblatt.com")

    private val deutscheSchluessel = setOf(
        "tagesschau-boerse", "tagesschau-wirtschaft", "ntv", "handelsblatt", "sz", "fn-adhoc", "derstandard", "aktionaer",
        "destatis", "bafin", "bafin-massnahmen", "blog-ifo", "blog-iw", "blog-diw", "blog-makronom", "blog-kommer",
    )

    private val neutraleSchluessel = setOf(
        "yahoo-maerkte", "yahoo-unternehmen", "yahoo-rohstoffe", "yahoo-krypto", "google-wirtschaft", "reuters", "bbk", "snb", "eu-kommission",
    )

    @Test
    fun jedeQuelleHatEineBekannteSpracheInBeidenFassungenGleich() {
        val de = alle(true)
        val en = alle(false)
        assertEquals(de.map { it.schluessel }, en.map { it.schluessel })
        for ((a, b) in de.zip(en)) {
            assertTrue(a.schluessel + " " + a.sprache, a.sprache in setOf("", "de", "en"))
            assertEquals(a.schluessel, a.sprache, b.sprache)
        }
    }

    // Ohne feste Sprache sind genau die Quellen, deren Adresse der Sprache der App folgt.
    @Test
    fun neutralSindGenauDieQuellenMitAdresseJeSprache() {
        val en = alle(false).associateBy { it.schluessel }
        for (q in alle(true)) {
            val folgt = q.adresse != en.getValue(q.schluessel).adresse
            assertEquals(q.schluessel, folgt, q.sprache.isEmpty())
        }
        assertEquals(neutraleSchluessel, alle(true).filter { it.sprache.isEmpty() }.map { it.schluessel }.toSet())
    }

    @Test
    fun spracheStimmtMitAdresseUndHerausgeberUeberein() {
        for (q in alle(false)) {
            val h = host(q)
            val deutscheAdresse = h.endsWith(".de") || h.endsWith(".at") || deutschOhneLandesendung.any { h == it || h.endsWith(".$it") }
            when (q.sprache) {
                "de" -> assertTrue(q.schluessel + " " + h, deutscheAdresse)
                "en" -> {
                    assertFalse(q.schluessel + " " + h, deutscheAdresse)
                    assertFalse(q.schluessel, q.adresse.contains("/de/", ignoreCase = true) || q.adresse.contains("deutsch", ignoreCase = true))
                }
            }
        }
        assertEquals(deutscheSchluessel, alle(false).filter { it.sprache == "de" }.map { it.schluessel }.toSet())
    }

    @Test
    fun filterEntferntGenauDieNichtEnglischen() {
        val quellen = alle(false)
        val bleibt = Rss.nurEnglisch(quellen, true)
        assertEquals(deutscheSchluessel, (quellen - bleibt.toSet()).map { it.schluessel }.toSet())
        assertTrue(bleibt.all { it.sprache.isEmpty() || it.sprache == "en" })
        assertEquals(deutscheSchluessel, Rss.NICHT_ENGLISCH)
        // Aus: nichts faellt weg
        assertEquals(quellen, Rss.nurEnglisch(quellen, false))
        // Andere Sprachen zaehlen als nicht englisch
        val fr = Rss.Quelle("x", "X", "https://example.org/rss", Rss.Kategorie.MAERKTE, Rss.Anbieter.PRESSE, sprache = "fr")
        assertTrue(Rss.nurEnglisch(listOf(fr), true).isEmpty())
    }

    @Test
    fun reiterOhneVerbleibendeQuelleEntfallen() {
        val bleibt = Rss.nurEnglisch(alle(false), true)
        assertTrue(bleibt.none { it.anbieter == Rss.Anbieter.TAGESSCHAU })
        for (a in listOf(Rss.Anbieter.YAHOO, Rss.Anbieter.GOOGLE, Rss.Anbieter.PRESSE, Rss.Anbieter.CNBC, Rss.Anbieter.NOTENBANKEN, Rss.Anbieter.BEHOERDEN, Rss.Anbieter.BRIEFE, Rss.Anbieter.TRUMP)) {
            assertTrue(a.name, bleibt.any { it.anbieter == a })
        }
    }

    @Test
    fun eigeneFeedsUndZugaengeBleiben() {
        val ntv = "https://www.n-tv.de/wirtschaft/rss"
        val eigen = Rss.eigene("Mein Feed", "https://beispiel.de/feed.xml")
        assertEquals("", eigen.sprache)
        val basis = Rss.nurEnglisch(alle(false), true) + eigen
        // Die Adresse einer ausgeblendeten festen Quelle ist als eigener Zugang eine eigene Quelle
        val zugaenge = EigenerFeed.quellen(listOf(EigenerFeed.Feed("k", "F", zugaenge = listOf(EigenerFeed.Zugang("n-tv", ntv)))), basis)
        assertEquals(1, zugaenge.size)
        assertEquals("", zugaenge[0].sprache)
        assertEquals(listOf(eigen) + zugaenge, Rss.nurEnglisch(listOf(eigen) + zugaenge, true))
        // Ihre Schlagzeilen bleiben auch im gespeicherten Stand
        val e = Rss.Eintrag("Titel", "https://www.n-tv.de/a", 1L, quellen = setOf(zugaenge[0].schluessel))
        assertEquals(listOf(e), Rss.ohneNichtEnglische(listOf(e)))
    }

    @Test
    fun vorhandeneSchlagzeilenAusgeblendeterQuellenFallenWeg() {
        fun e(titel: String, vararg quellen: String) = Rss.Eintrag(titel, "https://example.org/$titel", 1L, quellen = quellen.toSet())
        val liste = listOf(e("a", "ntv"), e("b", "bbc"), e("c", "ntv", "bbc"), e("d"), e("e", "tagesschau-boerse", "sz"), e("f", "yahoo-maerkte"))
        assertEquals(listOf("b", "c", "d", "f"), Rss.ohneNichtEnglische(liste).map { it.titel })
    }

    @Test
    fun vorgabeUndWirkungNurAufEnglisch() {
        assertTrue(Settings().newsNurEnglisch)
        val en = Settings(languageAuto = false, language = Lang.EN)
        val de = Settings(languageAuto = false, language = Lang.DE)
        assertTrue(en.nurEnglischeQuellen)
        assertFalse(en.copy(newsNurEnglisch = false).nurEnglischeQuellen)
        assertFalse(de.nurEnglischeQuellen)
        // Auf Deutsch bleibt die Liste, wie sie ist
        val quellen = alle(true)
        assertEquals(quellen, Rss.nurEnglisch(quellen, de.nurEnglischeQuellen))
        assertTrue(Rss.nurEnglisch(alle(false), en.nurEnglischeQuellen).none { it.sprache == "de" })
    }
}
