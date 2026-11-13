package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.NachrichtArt.Art
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Wichtig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class Fassung077NachrichtenListeTest {
    private val heute = LocalDate.of(2026, 11, 11)
    private val jetzt = heute.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun vor(tage: Int) = jetzt - tage * 86_400_000L

    @Test
    fun archivAdressenDeckenDreissigTageInVierAbschnitten() {
        val a = NachrichtenListe.archivAdressen(Art.ANALYST, false, heute)
        assertEquals(4, a.size)
        assertTrue(a[0].contains("when%3A7d"))
        assertTrue(a[1].contains("after%3A2026-10-28") && a[1].contains("before%3A2026-11-04"))
        assertTrue(a[3].contains("after%3A2026-10-12") && a[3].contains("before%3A2026-10-20"))
        assertTrue(a.all { it.startsWith("https://news.google.com/rss/search?q=") && it.contains("ceid=US:en") })
        assertTrue(NachrichtenListe.archivAdressen(Art.ANALYST, true, heute).all { it.contains("ceid=DE:de") })
    }

    @Test
    fun artenOhneNachladenHabenKeineAdressen() {
        assertTrue(NachrichtenListe.archivAdressen(Art.SEKTOR, true, heute).isEmpty())
        assertEquals(setOf(Art.ZAHLEN, Art.ANALYST, Art.UEBERNAHME, Art.KAPITAL), NachrichtenListe.ARCHIV_ARTEN)
        for (art in NachrichtenListe.ARCHIV_ARTEN) for (d in listOf(true, false)) assertEquals(4, NachrichtenListe.archivAdressen(art, d, heute).size)
    }

    @Test
    fun archivEintraegeBehaltenNurArtUndDreissigTage() {
        val m = listOf(
            LaenderZusatz.Meldung("Morgan Stanley upgrades Nvidia to overweight", "https://a/1", vor(3), "Reuters"),
            LaenderZusatz.Meldung("Morgan Stanley upgrades Nvidia to overweight", "https://a/1", vor(3), "Reuters"),
            LaenderZusatz.Meldung("Analyst hebt Kursziel für SAP", "https://a/2", vor(20), "Handelsblatt"),
            LaenderZusatz.Meldung("Firma kauft Konkurrenten, Übernahme perfekt", "https://a/3", vor(5), "dpa"),
            LaenderZusatz.Meldung("Upgrades Tesla stock after rally", "https://a/4", vor(45), "CNBC"),
            LaenderZusatz.Meldung("Downgrade for Intel", "https://a/5", 0L, "CNBC"),
        )
        val e = NachrichtenListe.archivEintraege(m, Art.ANALYST, jetzt)
        assertEquals(listOf("https://a/1", "https://a/2"), e.map { it.link })
        assertEquals("Reuters", e[0].herausgeber)
        assertEquals(listOf("https://a/3"), NachrichtenListe.archivEintraege(m, Art.UEBERNAHME, jetzt).map { it.link })
    }

    @Test
    fun depotListeNenntDepotwerteUndMischtJeWertDazu() {
        fun r(t: String, l: String, z: Long) = Rss.Eintrag(titel = t, link = l, zeit = z)
        val begriffe = Wichtig.depotBegriffe(listOf("Apple Inc."), listOf("AAPL"))
        val alle = listOf(
            r("Apple unveils new chip", "https://x/1", vor(1)),
            r("Oil prices fall again", "https://x/2", vor(1)),
        )
        val jeWert = listOf(r("Apple supplier raises output", "https://x/3", vor(0)), r("Apple unveils new chip", "https://x/1", vor(1)))
        val l = NachrichtenListe.depotListe(alle, jeWert, begriffe, jetzt)
        assertEquals(listOf("https://x/3", "https://x/1"), l.map { it.link })
        assertTrue(NachrichtenListe.imDepot("Apple unveils new chip", begriffe))
        assertFalse(NachrichtenListe.imDepot("Oil prices fall again", begriffe))
        assertTrue(NachrichtenListe.depotListe(alle, emptyList(), emptyList(), jetzt).isEmpty())
    }

    @Test
    fun depotReiterStehtNebenWichtigste() {
        assertEquals(Rss.Anbieter.WICHTIG.ordinal + 1, Rss.Anbieter.DEPOT.ordinal)
    }
}
