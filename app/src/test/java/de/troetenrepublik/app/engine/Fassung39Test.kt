package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Aktualisierung
import de.troetenrepublik.app.market.Handelstage
import de.troetenrepublik.app.market.Kongress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Pruefung auf neue Fassungen: Antwort von GitHub, Vergleich, wann gefragt und wann gemeldet wird. Dazu der
// Kalender der gemeldeten Geschaefte einer Person. Alle Geschaefte sind erfunden.
class Fassung39Test {

    private fun antwort(tag: String, draft: Boolean = false, pre: Boolean = false) = """
        {"url":"https://api.github.com/repositories/1/releases/1","tag_name":"$tag","name":"Tryfolio",
         "html_url":"https://github.com/beispiel/Troetenrepublik/releases/tag/$tag","draft":$draft,"prerelease":$pre,
         "published_at":"2026-10-17T15:22:03Z","assets":[{"name":"troetenrepublik-debug.apk","size":1}]}
    """.trimIndent()

    @Test
    fun antwortLesen() {
        val n = Aktualisierung.lesen(antwort("v3.10"))
        assertNotNull(n)
        assertEquals("3.10", n!!.fassung)
        assertEquals("https://github.com/beispiel/Troetenrepublik/releases/tag/v3.10", n.link)
        assertNull(Aktualisierung.lesen(antwort("v4.0", draft = true)))
        assertNull(Aktualisierung.lesen(antwort("v4.0", pre = true)))
        assertNull(Aktualisierung.lesen(antwort("neu")))
        assertNull(Aktualisierung.lesen("""{"message":"Not Found","documentation_url":"https://docs.github.com"}"""))
        assertNull(Aktualisierung.lesen("kein json"))
        // Fremde Links landen nicht in der Mitteilung
        assertNull(Aktualisierung.lesen(antwort("v3.10").replace("https://github.com/", "https://example.org/")))
    }

    @Test
    fun fassungenVergleichen() {
        assertTrue(Aktualisierung.neuer("3.10", "3.9"))
        assertTrue(Aktualisierung.neuer("3.9.1", "3.9"))
        assertTrue(Aktualisierung.neuer("4.0", "3.9"))
        assertFalse(Aktualisierung.neuer("3.9", "3.9"))
        assertFalse(Aktualisierung.neuer("3.9.0", "3.9"))
        assertFalse(Aktualisierung.neuer("3.8", "3.9"))
        assertFalse(Aktualisierung.neuer("", "3.9"))
        assertFalse(Aktualisierung.neuer("3.x", "3.9"))
        assertTrue(Aktualisierung.neuer("3.10-beta", "3.9"))
    }

    @Test
    fun einmalAmTag() {
        val h = 3_600_000L
        val t = 1_800_000_000_000L
        assertTrue(Aktualisierung.faellig(t, 0L, false))
        assertFalse(Aktualisierung.faellig(t, t - 23 * h, true))
        assertTrue(Aktualisierung.faellig(t, t - 24 * h, true))
        // ohne Netz nach einer Stunde wieder
        assertFalse(Aktualisierung.faellig(t, t - h / 2, false))
        assertTrue(Aktualisierung.faellig(t, t - h, false))
        // Uhr zurueckgestellt
        assertTrue(Aktualisierung.faellig(t, t + 5 * h, true))
    }

    @Test
    fun jedeFassungEinmalMelden() {
        val neu = Aktualisierung.Neu("3.10", "https://github.com/x/y/releases/tag/v3.10")
        val erst = Aktualisierung.auswerten(neu, "3.9", "", hinweis = true)
        assertEquals("3.10", erst.fassung)
        assertTrue(erst.melden)
        assertFalse(Aktualisierung.auswerten(neu, "3.9", "3.10", hinweis = true).melden)
        // Ausgeschaltet: im Profil sichtbar, aber keine Mitteilung
        val aus = Aktualisierung.auswerten(neu, "3.9", "", hinweis = false)
        assertEquals("3.10", aus.fassung)
        assertFalse(aus.melden)
        // Gleiche oder aeltere Fassung und keine Antwort ergeben nichts
        assertEquals("", Aktualisierung.auswerten(Aktualisierung.Neu("3.9", neu.link), "3.9", "", true).fassung)
        assertEquals("", Aktualisierung.auswerten(null, "3.9", "", true).fassung)
    }

    @Test
    fun fassungPasstZumBau() {
        val wurzel = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").isFile }
        val app = File(wurzel, "app/build.gradle.kts").readText()
        val desktop = File(wurzel, "desktop/build.gradle.kts").readText()
        assertTrue(app.contains("versionName = \"${Aktualisierung.FASSUNG}\""))
        assertTrue(desktop.contains("troeteVersion = \"${Aktualisierung.FASSUNG}.0\""))
    }

    private fun g(tag: String, art: Kongress.Art, von: Long, bis: Long?, kuerzel: String? = "AAA") =
        Kongress.Geschaeft("", "Wert $kuerzel", kuerzel, "ST", art, java.time.LocalDate.parse(tag).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), 0L, von, bis)

    @Test
    fun geschaeftsKalender() {
        val liste = listOf(
            g("2026-07-01", Kongress.Art.VERKAUF, 1_001, 15_000),
            g("2026-07-01", Kongress.Art.KAUF, 15_001, 50_000, "BBB"),
            g("2026-07-01", Kongress.Art.KAUF, 1_001, 15_000, "CCC"),
            g("2026-07-31", Kongress.Art.TEILVERKAUF, 50_001, 100_000),
            g("2026-07-31", Kongress.Art.TAUSCH, 1_001, 15_000),
            g("2025-03-14", Kongress.Art.KAUF, 5_000_001, null),
        )
        val tage = Handelstage.tage(liste)
        assertEquals(3, tage.size)
        val erster = tage.getValue(java.time.LocalDate.parse("2026-07-01"))
        // Kaeufe vor Verkaeufen, die groessten zuerst
        assertEquals(listOf("BBB", "CCC", "AAA"), erster.geschaefte.map { it.kuerzel })
        assertEquals(2, erster.kaeufe)
        assertEquals(1, erster.verkaeufe)
        assertEquals(32_500.5 + 8_000.5, erster.kaufSumme, 1e-6)
        assertTrue(erster.netto > 0)
        val letzter = tage.getValue(java.time.LocalDate.parse("2026-07-31"))
        assertTrue(letzter.netto < 0)
        // Tausch zaehlt als Geschaeft, aber weder als Kauf noch als Verkauf
        assertEquals(2, letzter.geschaefte.size)
        assertEquals(1, letzter.verkaeufe)
        val juli = Handelstage.monat(tage, java.time.YearMonth.of(2026, 7))
        assertEquals(5, juli.geschaefte)
        assertEquals(2, juli.tage)
        assertEquals(2, juli.kaeufe)
        assertEquals(2, juli.verkaeufe)
        // Ueber 5 Mio. zaehlt mit der Untergrenze
        assertEquals(5_000_001.0, Handelstage.monat(tage, java.time.YearMonth.of(2025, 3)).kaufSumme, 1e-6)
        assertEquals(java.time.YearMonth.of(2025, 3)..java.time.YearMonth.of(2026, 7), Handelstage.spanne(tage, java.time.YearMonth.of(2026, 10)))
        assertEquals(java.time.YearMonth.of(2026, 10)..java.time.YearMonth.of(2026, 10), Handelstage.spanne(emptyMap(), java.time.YearMonth.of(2026, 10)))
    }
}
