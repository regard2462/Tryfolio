package de.troetenrepublik.app.engine

import org.junit.Assert.*
import org.junit.Test

// Erklaerung der Trends je sichtbarer Quelle und Hacker News nur bei Technikwerten (5.97)
class Fassung597TrendsTest {
    @Test
    fun nurWikipediaOhneReddit() {
        // Novo Nordisk: keine Reddit-Zeile, also kein Reddit-Satz
        assertEquals(listOf(TrendsErklaerung.Teil.WIKIPEDIA), TrendsErklaerung.teile(TrendsErklaerung.Sichtbar(wikipedia = true)))
    }

    @Test
    fun redditMitVerlauf() {
        assertEquals(
            listOf(TrendsErklaerung.Teil.REDDIT, TrendsErklaerung.Teil.VERLAUF),
            TrendsErklaerung.teile(TrendsErklaerung.Sichtbar(redditApewisdom = true)),
        )
        assertEquals(
            listOf(TrendsErklaerung.Teil.REDDIT_DEUTSCH, TrendsErklaerung.Teil.VERLAUF),
            TrendsErklaerung.teile(TrendsErklaerung.Sichtbar(redditDeutsch = true)),
        )
        assertEquals(
            listOf(TrendsErklaerung.Teil.REDDIT, TrendsErklaerung.Teil.REDDIT_DEUTSCH, TrendsErklaerung.Teil.VERLAUF),
            TrendsErklaerung.teile(TrendsErklaerung.Sichtbar(redditApewisdom = true, redditDeutsch = true)),
        )
    }

    @Test
    fun namenSatzNurMitNetzquellen() {
        assertEquals(
            listOf(TrendsErklaerung.Teil.MASTODON, TrendsErklaerung.Teil.NAMEN),
            TrendsErklaerung.teile(TrendsErklaerung.Sichtbar(mastodon = true)),
        )
        assertFalse(TrendsErklaerung.teile(TrendsErklaerung.Sichtbar(wikipedia = true, yahoo = true)).contains(TrendsErklaerung.Teil.NAMEN))
    }

    @Test
    fun alleInZeilenreihenfolge() {
        assertEquals(
            listOf(
                TrendsErklaerung.Teil.REDDIT,
                TrendsErklaerung.Teil.REDDIT_DEUTSCH,
                TrendsErklaerung.Teil.VERLAUF,
                TrendsErklaerung.Teil.BEITRAEGE,
                TrendsErklaerung.Teil.YAHOO,
                TrendsErklaerung.Teil.WIKIPEDIA,
                TrendsErklaerung.Teil.HACKER_NEWS,
                TrendsErklaerung.Teil.MASTODON,
                TrendsErklaerung.Teil.BLUESKY,
                TrendsErklaerung.Teil.NAMEN,
            ),
            TrendsErklaerung.teile(
                TrendsErklaerung.Sichtbar(
                    redditApewisdom = true, redditDeutsch = true, redditBeitraege = true,
                    yahoo = true, wikipedia = true, hackerNews = true, mastodon = true, bluesky = true,
                ),
            ),
        )
    }

    @Test
    fun nichtsSichtbarLeer() {
        assertTrue(TrendsErklaerung.teile(TrendsErklaerung.Sichtbar()).isEmpty())
    }

    @Test
    fun textOhneLeereTeile() {
        assertEquals(
            "REDDIT WIKIPEDIA",
            TrendsErklaerung.text(listOf(TrendsErklaerung.Teil.REDDIT, TrendsErklaerung.Teil.VERLAUF, TrendsErklaerung.Teil.WIKIPEDIA)) {
                if (it == TrendsErklaerung.Teil.VERLAUF) "" else it.name
            },
        )
    }

    @Test
    fun hackerNewsNurBeiTechnik() {
        assertFalse(TrendsErklaerung.hackerNewsZeigen("Healthcare", 10, 5.0))
        // 0 Beitraege, vorher 0,3 pro Woche: zu wenig fuer eine Zeile
        assertFalse(TrendsErklaerung.hackerNewsZeigen("Technology", 0, 0.3))
        assertTrue(TrendsErklaerung.hackerNewsZeigen("Technology", 2, 1.0))
        assertTrue(TrendsErklaerung.hackerNewsZeigen("Communication Services", 3, 0.0))
        assertFalse(TrendsErklaerung.hackerNewsZeigen(null, 10, 5.0))
        assertFalse(TrendsErklaerung.hackerNewsZeigen("", 10, 5.0))
    }

    @Test
    fun hackerNewsBeiIpo() {
        assertTrue(TrendsErklaerung.hackerNewsBeiIpo(IpoBewertung.Branche.KI))
        assertTrue(TrendsErklaerung.hackerNewsBeiIpo(IpoBewertung.Branche.SOFTWARE))
        assertFalse(TrendsErklaerung.hackerNewsBeiIpo(IpoBewertung.Branche.BIOTECH))
        assertFalse(TrendsErklaerung.hackerNewsBeiIpo(null))
    }
}
