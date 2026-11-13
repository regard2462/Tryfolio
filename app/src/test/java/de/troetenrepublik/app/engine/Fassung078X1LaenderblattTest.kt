package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Laenderblatt 0.78: Wirtschaftsteil aus Wikipedia (eigener Artikel, sonst Abschnitt des Laenderartikels), Massstab der
// Anteilsbalken, Verteilung der Felder, Beschriftung der Kacheln und gemeinsame Jahreszahl.
class Fassung078X1LaenderblattTest {
    private val titel = """{"stand":"x","t":{
        "BW":["Wirtschaft Botswanas","Economy of Botswana","Botswana","Botswana"],
        "AD":["","Economy of Andorra","Andorra","Andorra"],
        "XX":["","","","Somewhere"]}}"""

    // Gekuerzte Antworten der Wikimedia-Schnittstelle: Zusammenfassung nach Weiterleitung auf den Laenderartikel
    private val zusammenfassungLand = """{"type":"standard","title":"Botswana","titles":{"canonical":"Botswana","normalized":"Botswana"},
        "extract":"Botswana, nach amtlicher deutscher Schreibweise Republik Botsuana, ist ein Binnenstaat im südlichen Afrika.",
        "content_urls":{"desktop":{"page":"https://de.wikipedia.org/wiki/Botswana"}}}"""
    private val zusammenfassungWirtschaft = """{"type":"standard","title":"Economy of Botswana",
        "extract":"The economy of Botswana is currently one of the world's fastest growing economies.",
        "content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Economy_of_Botswana"}}}"""

    // Ganzer Text des Laenderartikels mit Weiterleitung auf den Abschnitt
    private val seite = """{"batchcomplete":true,"query":{
        "normalized":[{"fromencoded":false,"from":"Wirtschaft_Botswanas","to":"Wirtschaft Botswanas"}],
        "redirects":[{"from":"Wirtschaft Botswanas","to":"Botswana","tofragment":"Wirtschaft"}],
        "pages":[{"pageid":1,"ns":0,"title":"Botswana","extract":"Botswana ist ein Binnenstaat im südlichen Afrika.\n\n\n== Geographie ==\nDas Land liegt im Kalahari-Becken.\n\n\n=== Klima ===\nEs ist trocken.\n\n\n== Wirtschaft ==\n\n\n=== Allgemeines ===\nDas Bruttoinlandsprodukt (BIP) Botswanas betrug im Jahr 2023 19,4 Milliarden US-Dollar.\nIm Global Competitiveness Index belegt Botswana Platz 63 von 137 Ländern.\n\n\n=== Bodenschätze ===\n\nDas Einkommen beruht auf Diamanten.\n\n\n== Infrastruktur ==\nStraßen und Bahn."}]}}"""

    @Test
    fun eigenerArtikelNurInDerSpracheDerApp() {
        val de = LaenderDaten.wikiTitelEigen(titel, "BW", true)!!
        assertEquals(LaenderDaten.Artikel("de", "Wirtschaft Botswanas"), de.wirtschaft)
        assertEquals(LaenderDaten.Artikel("de", "Botswana"), de.land)
        assertEquals(LaenderDaten.Artikel("en", "Economy of Botswana"), LaenderDaten.wikiTitelEigen(titel, "BW", false)!!.wirtschaft)
        // Ohne deutschen Wirtschaftsartikel kein englischer Ersatz: dann zaehlt der Abschnitt des deutschen Laenderartikels
        val ad = LaenderDaten.wikiTitelEigen(titel, "AD", true)!!
        assertNull(ad.wirtschaft)
        assertEquals(LaenderDaten.Artikel("de", "Andorra"), ad.land)
        // Fehlt der Laenderartikel in der eigenen Sprache, der in der anderen
        assertEquals(LaenderDaten.Artikel("en", "Somewhere"), LaenderDaten.wikiTitelEigen(titel, "XX", true)!!.land)
        assertNull(LaenderDaten.wikiTitelEigen(titel, "ZZ", true))
    }

    @Test
    fun weiterleitungAufDenLaenderartikelZaehltNicht() {
        // "Wirtschaft Botswanas" leitet auf "Botswana" weiter: die Einleitung des Landes ist kein Wirtschaftstext
        assertEquals("Botswana", LaenderDaten.auszugTitel(zusammenfassungLand))
        assertFalse(LaenderDaten.istWirtschaftsartikel("Botswana", "Wirtschaft Botswanas", "Botswana"))
        assertTrue(LaenderDaten.istWirtschaftsartikel(LaenderDaten.auszugTitel(zusammenfassungWirtschaft), "Economy of Botswana", "Botswana"))
        // Unterstriche und Schreibung des angefragten Titels
        assertTrue(LaenderDaten.istWirtschaftsartikel("Wirtschaft Islands", "Wirtschaft_Islands", "Island"))
        // Weiterleitung auf einen anders benannten Wirtschaftsartikel bleibt gueltig
        assertTrue(LaenderDaten.istWirtschaftsartikel("Economy of the Republic of Ireland", "Economy of Ireland", "Republic of Ireland"))
        // Weiterleitung auf einen anders benannten Laenderartikel zaehlt nicht
        assertFalse(LaenderDaten.istWirtschaftsartikel("Timor-Leste", "Wirtschaft Osttimors", "Osttimor"))
        assertFalse(LaenderDaten.istWirtschaftsartikel(null, "Wirtschaft Botswanas", "Botswana"))
        assertNull(LaenderDaten.auszugTitel("""{"type":"disambiguation","title":"Wirtschaft"}"""))
    }

    @Test
    fun abschnittWirtschaftDesLaenderartikels() {
        val s = LaenderDaten.seite(seite)!!
        assertEquals("Botswana", s.titel)
        assertEquals("Wirtschaft", s.abschnitt)
        val a = LaenderDaten.abschnitt(s.text, listOfNotNull(s.abschnitt) + LaenderDaten.wirtschaftsNamen("de"))!!
        assertEquals("Wirtschaft", a.kopf)
        assertTrue(a.text.startsWith("Das Bruttoinlandsprodukt (BIP) Botswanas betrug im Jahr 2023 19,4 Milliarden US-Dollar."))
        // Zwischenueberschriften stehen nicht im Text, der naechste Hauptabschnitt gehoert nicht dazu
        assertFalse("==" in a.text)
        assertFalse("Allgemeines" in a.text)
        assertTrue("Diamanten" in a.text)
        assertFalse("Straßen" in a.text)
        assertFalse("Binnenstaat" in a.text)
        assertEquals("https://de.wikipedia.org/wiki/Botswana#Wirtschaft", LaenderDaten.abschnittAdresse("de", "Botswana", a.kopf))
    }

    @Test
    fun abschnittMitZusatzImTitelUndOhneAbschnitt() {
        val text = "Einleitung.\n\n== History ==\nOld.\n\n== Economy and infrastructure ==\nMining dominates.\n\n== Culture ==\nMusic."
        val a = LaenderDaten.abschnitt(text, LaenderDaten.wirtschaftsNamen("en"))!!
        assertEquals("Economy and infrastructure", a.kopf)
        assertEquals("Mining dominates.", a.text)
        assertEquals("https://en.wikipedia.org/wiki/Some_Land#Economy_and_infrastructure", LaenderDaten.abschnittAdresse("en", "Some Land", a.kopf))
        // Die genaue Ueberschrift geht einer mit Zusatz vor
        val beide = "== Economy and trade ==\nA.\n\n== Economy ==\nB."
        assertEquals("B.", LaenderDaten.abschnitt(beide, listOf("Economy"))!!.text)
        // Ohne Abschnitt oder mit leerem Abschnitt kein Text: dann bleibt nur der Verweis auf den Laenderartikel
        assertNull(LaenderDaten.abschnitt("Einleitung.\n\n== Geschichte ==\nAlt.", LaenderDaten.wirtschaftsNamen("de")))
        assertNull(LaenderDaten.abschnitt("== Wirtschaft ==\n\n== Kultur ==\nMusik.", LaenderDaten.wirtschaftsNamen("de")))
        // Eine Unterueberschrift "Wirtschaft" in einem anderen Abschnitt zaehlt nicht
        assertNull(LaenderDaten.abschnitt("== Geschichte ==\n=== Wirtschaft ===\nFrueher.", LaenderDaten.wirtschaftsNamen("de")))
        assertNull(LaenderDaten.seite("""{"query":{"pages":[{"title":"X","missing":true}]}}"""))
        assertNull(LaenderDaten.seite("kaputt"))
        assertNotNull(LaenderDaten.textAdresse("de", "Vereinigte Staaten").takeIf { it.endsWith("titles=Vereinigte_Staaten") && "explaintext=1" in it })
    }

    @Test
    fun anteilsbalkenImMassstabNullBisHundert() {
        // 63,9 % fuellt knapp zwei Drittel, auch als groesster Eintrag der Liste
        assertEquals(0.639f, Blattaufbau.balkenAnteil(63.9), 1e-6f)
        assertEquals(0.269f, Blattaufbau.balkenAnteil(26.9), 1e-6f)
        assertEquals(1f, Blattaufbau.balkenAnteil(100.0), 0f)
        assertEquals(1f, Blattaufbau.balkenAnteil(140.0), 0f)
        assertEquals(0f, Blattaufbau.balkenAnteil(-3.0), 0f)
        assertEquals(0f, Blattaufbau.balkenAnteil(Double.NaN), 0f)
    }

    @Test
    fun felderGleichmaessigAufZweiSpalten() {
        // Neun schmale Felder: vier Paare und ein letztes Feld, nicht sieben links und zwei rechts
        val neun = Blattaufbau.feldZeilen(List(9) { false })
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5), listOf(6, 7), listOf(8)), neun)
        // Ein breites Feld steht allein; folgt es auf ein offenes schmales Feld, wartet es auf dessen Nachbarn
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3, 4)), Blattaufbau.feldZeilen(listOf(false, false, true, false, false)))
        assertEquals(listOf(listOf(0, 2), listOf(1), listOf(3)), Blattaufbau.feldZeilen(listOf(false, true, false, false)))
        // Japan: Hauptstadt, Waehrung, Regierungsform (breit), Flaeche, Staatsoberhaupt, Regierungschef, Zeitzone, Amtssprache, Mitgliedschaften
        val japan = Blattaufbau.feldZeilen(listOf(false, false, true, false, false, false, false, false, false))
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3, 4), listOf(5, 6), listOf(7, 8)), japan)
        // Fehlende Felder: jedes Feld kommt genau einmal vor, hoechstens eine Zeile mit einem einzelnen schmalen Feld
        for (n in 0..9) for (maske in 0 until (1 shl n)) {
            val breit = List(n) { (maske shr it) and 1 == 1 }
            val z = Blattaufbau.feldZeilen(breit)
            assertEquals((0 until n).toList(), z.flatten().sorted())
            assertTrue(z.all { it.size == 2 && it.none { i -> breit[i] } || it.size == 1 })
            assertTrue(z.count { it.size == 1 && !breit[it[0]] } <= 1)
        }
        assertTrue(Blattaufbau.feldZeilen(emptyList()).isEmpty())
    }

    @Test
    fun kachelZeigtNurGanzeNamen() {
        // Breiten: ganzer Name, Kuerzel. Passt der Name, steht er; sonst das Kuerzel; sonst nichts
        assertEquals(0, Blattaufbau.kachelName(listOf(80, 40), 100))
        assertEquals(1, Blattaufbau.kachelName(listOf(120, 40), 100))
        assertEquals(-1, Blattaufbau.kachelName(listOf(120, 110), 100))
        // Ohne Kuerzel (Breite 0) bleibt die Kachel leer, statt den Namen anzuschneiden
        assertEquals(-1, Blattaufbau.kachelName(listOf(120, 0), 100))
        assertEquals(0, Blattaufbau.kachelName(listOf(100, 0), 100))
    }

    @Test
    fun jahrEinmalStattInJederZeile() {
        assertEquals(2024, Blattaufbau.gemeinsamesJahr(listOf(2024, 2024, 2024, 2024)))
        // Unterschiedliche Jahre oder Zeilen ohne Jahr: das Jahr bleibt an der Zeile
        assertNull(Blattaufbau.gemeinsamesJahr(listOf(2024, 2023, 2024)))
        assertNull(Blattaufbau.gemeinsamesJahr(listOf(2024, null, 2024)))
        assertNull(Blattaufbau.gemeinsamesJahr(listOf(null, null)))
        assertNull(Blattaufbau.gemeinsamesJahr(emptyList()))
    }
}
