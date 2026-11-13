package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Landesdaten, Wikipedia-Titel, Auszug der REST-Zusammenfassung und Kuerzen am Satzende.
class LaenderDatenTest {
    private val daten = """{"stand":"2026-11-06","l":{
        "DE":{"h":"Berlin","w":"Euro","s":"Frank-Walter Steinmeier","r":"Friedrich Merz","f":"PARL_REP+B","m":"EU,EURO,NATO,G7,G20,OECD,XYZ"},
        "JP":{"h":["Tokyo","Tokio"],"w":["Yen","Yen"],"s":"Naruhito","r":"Sanae Takaichi","f":"VERF_MON","m":"G7,G20"},
        "US":{"s":"Donald Trump","r":"Donald Trump","f":"PRAES+B"}}}"""

    @Test
    fun landesdatenWerdenInDerSpracheGelesen() {
        val de = LaenderDaten.lesen(daten, true)!!
        assertEquals("2026-11-06", de.stand)
        assertEquals("Tokio", de.laender["JP"]!!.hauptstadt)
        assertEquals("Tokyo", LaenderDaten.lesen(daten, false)!!.laender["JP"]!!.hauptstadt)
        val d = de.laender["DE"]!!
        assertEquals("PARL_REP", d.form)
        assertTrue(d.bundesstaat)
        // unbekannte Mitgliedschaften fallen weg
        assertEquals(listOf("EU", "EURO", "NATO", "G7", "G20", "OECD"), d.mitglieder)
        assertFalse(de.laender["JP"]!!.bundesstaat)
        assertNull(de.laender["US"]!!.hauptstadt)
        assertEquals(de.laender["US"]!!.oberhaupt, de.laender["US"]!!.regierungschef)
        assertNull(LaenderDaten.lesen("kaputt", true))
    }

    @Test
    fun wikiTitelMitRueckfallAufEnglisch() {
        val t = """{"stand":"x","t":{"DE":["Wirtschaft Deutschlands","Economy of Germany","Deutschland","Germany"],"HK":["","Economy of Hong Kong","Hongkong","Hong Kong"]}}"""
        val de = LaenderDaten.wikiTitel(t, "DE", true)!!
        assertEquals(LaenderDaten.Artikel("de", "Wirtschaft Deutschlands"), de.wirtschaft)
        assertEquals(LaenderDaten.Artikel("en", "Economy of Germany"), LaenderDaten.wikiTitel(t, "DE", false)!!.wirtschaft)
        assertEquals(LaenderDaten.Artikel("en", "Economy of Hong Kong"), LaenderDaten.wikiTitel(t, "HK", true)!!.wirtschaft)
        assertEquals(LaenderDaten.Artikel("de", "Hongkong"), LaenderDaten.wikiTitel(t, "HK", true)!!.land)
        assertNull(LaenderDaten.wikiTitel(t, "ZZ", true))
    }

    @Test
    fun auszugAusDerRestZusammenfassung() {
        val antwort = """{"type":"standard","title":"Wirtschaft Deutschlands","extract":"Die Wirtschaft Deutschlands ist die größte Volkswirtschaft Europas.","content_urls":{"desktop":{"page":"https://de.wikipedia.org/wiki/Wirtschaft_Deutschlands"}}}"""
        val a = LaenderDaten.auszug(antwort)!!
        assertEquals("Die Wirtschaft Deutschlands ist die größte Volkswirtschaft Europas.", a.text)
        assertEquals("https://de.wikipedia.org/wiki/Wirtschaft_Deutschlands", a.adresse)
        assertNull(LaenderDaten.auszug("""{"title":"x"}"""))
        assertEquals("https://de.wikipedia.org/api/rest_v1/page/summary/Wirtschaft_Deutschlands", LaenderDaten.auszugAdresse("de", "Wirtschaft Deutschlands"))
    }

    @Test
    fun kuerzenAmSatzende() {
        val kurz = "Ein kurzer Text."
        assertEquals(kurz, LaenderDaten.kuerzen(kurz, 600))
        val text = "Erster Satz mit 5 Mrd. Euro Umsatz. Zweiter Satz folgt hier. Dritter Satz ist deutlich zu lang für die Grenze."
        val g = LaenderDaten.kuerzen(text, 60)
        assertEquals("Erster Satz mit 5 Mrd. Euro Umsatz. Zweiter Satz folgt hier.", g)
        assertTrue(g.length <= 60)
        assertTrue(g.endsWith("."))
        // erster Satz schon zu lang: am Wortende kuerzen
        val lang = "Ein einziger sehr langer Satz ohne Ende der immer weiter geht und geht"
        val k = LaenderDaten.kuerzen(lang, 30)
        assertTrue(k.length <= 31)
        assertNotNull(k)
        assertFalse(k.contains("  "))
    }
}
