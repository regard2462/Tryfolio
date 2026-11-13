package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.LaenderZusatz.Gruppe
import de.troetenrepublik.app.engine.LaenderZusatz.Kennzahl
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Laenderblatt, Abschnitt Mehr: Lesen der gebuendelten Dateien, Feiertage, Zeilen und Texte.
class LaenderZusatzTest {
    private fun reihe(kennung: String, vararg w: Triple<String, Int, Double>) =
        Weltbank.Reihe(kennung, 0L, w.associate { it.first to Weltbank.Wert(it.second, it.third) })

    @Test
    fun handelLesen() {
        val text = """{"l":{"ARG":{"j":2024,"w":7.97e10,"g":[["agr",511],["son",142],["fzg",98],["brs",95],["che",41],["edl",34]],"p":[["BRA",171],["USA",81],["CHL",80],["CHN",75],["IND",49],["VNM",41]]},"XXX":{"j":2024,"g":[]},"QQQ":{"j":2023,"w":0,"m":1,"g":[["edl",500]],"p":[["IND",300]]}}}"""
        val h = LaenderZusatz.handelLesen(text)
        assertEquals(2, h.size)
        assertTrue(h.getValue("QQQ").spiegel)
        assertFalse(h.getValue("ARG").spiegel)
        val a = h.getValue("ARG")
        assertEquals(2024, a.jahr)
        assertEquals(5, a.gueter.size)
        assertEquals(LaenderZusatz.Warengruppe.AGRAR, a.gueter[0].first)
        assertEquals(511, a.gueter[0].second)
        assertEquals("BRA" to 171, a.partner[0])
        assertEquals(5, a.partner.size)
        assertTrue(LaenderZusatz.handelLesen("kaputt").isEmpty())
    }

    @Test
    fun steckbriefLesen() {
        val text = """{"l":{"CH":{"f":41291,"s":[["German","Deutsch"],["French","Französisch"]],"z":["Europe/Zurich"],"m":42.9},"ZA":{"f":1221037,"s":[["Tswana","Setswana"],["Zulu","isiZulu"],11],"z":["Africa/Johannesburg"]}}}"""
        val s = LaenderZusatz.steckbriefLesen(text)
        val ch = s.getValue("CH")
        assertEquals(41291.0, ch.flaeche!!, 0.0)
        assertEquals("Französisch", ch.sprachen[1].second)
        assertEquals(0, ch.weitere)
        assertEquals(42.9, ch.medianalter!!, 0.0)
        val za = s.getValue("ZA")
        assertEquals(2, za.sprachen.size)
        assertEquals(9, za.weitere)
        assertNull(za.medianalter)
    }

    @Test
    fun zeitzonen() {
        // Januar 2026: Zuerich UTC+1, Indien UTC+5:30, New York UTC-5
        val jan = java.time.Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        val v = LaenderZusatz.versaetze(listOf("Europe/Zurich", "Asia/Kolkata", "America/New_York", "Gibt/Es/Nicht", "Europe/Berlin"), jan)
        assertEquals(listOf(-18000, 3600, 19800), v)
        assertEquals("UTC−5", LaenderZusatz.utcText(-18000))
        assertEquals("UTC+1", LaenderZusatz.utcText(3600))
        assertEquals("UTC+5:30", LaenderZusatz.utcText(19800))
        assertEquals("UTC", LaenderZusatz.utcText(0))
    }

    @Test
    fun feiertage() {
        val text = """[{"date":"2026-12-08","localName":"Mariä Empfängnis","name":"Immaculate Conception","countryCode":"CH","fixed":false,"global":false,"counties":["CH-UR"],"launchYear":null,"types":["Public"]},{"date":"2026-12-25","localName":"Weihnachten","name":"Christmas Day","countryCode":"CH","fixed":false,"global":true,"counties":null,"launchYear":null,"types":["Public"]},{"date":"2026-12-26","localName":"Stephanstag","name":"St. Stephen's Day","countryCode":"CH","fixed":false,"global":true,"counties":null,"launchYear":null,"types":["Public"]},{"date":"2027-01-01","localName":"Neujahr","name":"New Year's Day","countryCode":"CH","fixed":true,"global":true,"counties":null,"launchYear":null,"types":["Public"]},{"date":"2027-01-02","localName":"Berchtoldstag","name":"Berchtold's Day","countryCode":"CH","fixed":true,"global":true,"counties":null,"launchYear":null,"types":["Public"]},{"date":"2026-11-01","localName":"Alt","name":"Past","countryCode":"CH","global":true,"types":["Public"]},{"date":"2026-12-24","localName":"Bank","name":"Bank Day","countryCode":"CH","global":true,"types":["Bank"]}]"""
        val f = LaenderZusatz.feiertage(text, LocalDate.of(2026, 11, 6))
        assertEquals(3, f.size)
        assertEquals(LocalDate.of(2026, 12, 25), f[0].datum)
        assertEquals("Christmas Day", LaenderZusatz.feiertagName(f[0], "CH", false))
        assertEquals("Weihnachten", LaenderZusatz.feiertagName(f[0], "CH", true))
        assertEquals("Christmas Day", LaenderZusatz.feiertagName(f[0], "FR", true))
        assertTrue(LaenderZusatz.feiertage("[]", LocalDate.of(2026, 11, 6)).isEmpty())
        assertTrue(LaenderZusatz.feiertage("{}", LocalDate.of(2026, 11, 6)).isEmpty())
    }

    @Test
    fun zeilenUndJahr() {
        val reihen = mapOf(
            "SP.DYN.LE00.IN" to reihe("SP.DYN.LE00.IN", Triple("CHE", 2024, 84.0), Triple("DEU", 2024, 81.2)),
            "SP.URB.TOTL.IN.ZS" to reihe("SP.URB.TOTL.IN.ZS", Triple("CHE", 2025, 74.0)),
            "SI.POV.DDAY" to reihe("SI.POV.DDAY", Triple("CHE", 2021, 0.5)),
            "SE.ADT.LITR.ZS" to reihe("SE.ADT.LITR.ZS", Triple("CHE", 2005, 99.0)),
            "SP.POP.GROW" to reihe("SP.POP.GROW", Triple("CHE", 2025, 0.8), Triple("DEU", 2022, -0.1)),
            LaenderZusatz.REISE_ANTEIL to reihe(LaenderZusatz.REISE_ANTEIL, Triple("CHE", 2025, 20.0)),
            LaenderZusatz.DIENST_EXPORT to reihe(LaenderZusatz.DIENST_EXPORT, Triple("CHE", 2025, 100.0)),
            LaenderZusatz.GESAMT_EXPORT to reihe(LaenderZusatz.GESAMT_EXPORT, Triple("CHE", 2025, 400.0)),
        )
        val z = LaenderZusatz.zeilen(Gruppe.GESELLSCHAFT, "CHE", reihen, 42.9, 2026)
        val nach = z.associateBy { it.kennzahl }
        assertEquals(84.0, nach.getValue(Kennzahl.LEBENSERWARTUNG).wert, 0.0)
        assertFalse(nach.getValue(Kennzahl.LEBENSERWARTUNG).mitJahr)
        assertTrue(nach.getValue(Kennzahl.ARMUT).mitJahr)
        assertNull(nach[Kennzahl.ALPHA])
        assertNull(nach[Kennzahl.ERWERB])
        assertFalse(nach.getValue(Kennzahl.MEDIANALTER).mitJahr)
        assertEquals(Kennzahl.LEBENSERWARTUNG, z.first().kennzahl)
        // Deutschland: Wachstum aus 2022 liegt hinter dem juengsten Jahr der Reihe (2025)
        assertTrue(LaenderZusatz.zeilen(Gruppe.GESELLSCHAFT, "DEU", reihen, null, 2026).first { it.kennzahl == Kennzahl.WACHSTUM }.mitJahr)
        val staat = LaenderZusatz.zeilen(Gruppe.STAAT, "CHE", reihen, null, 2026)
        assertEquals(5.0, staat.single { it.kennzahl == Kennzahl.TOURISMUS }.wert, 1e-9)
        // Jahre verschieden: keine Tourismusquote
        val schief = reihen + (LaenderZusatz.GESAMT_EXPORT to reihe(LaenderZusatz.GESAMT_EXPORT, Triple("CHE", 2024, 400.0)))
        assertNull(LaenderZusatz.tourismus("CHE", schief))
    }

    @Test
    fun schlagzeilen() {
        val m = listOf(
            LaenderZusatz.Meldung("Alt", "https://a.example/1", 100, "A"),
            LaenderZusatz.Meldung("Neu", "https://a.example/2", 300, "B"),
            LaenderZusatz.Meldung("neu ", "https://a.example/3", 200, "C"),
            LaenderZusatz.Meldung("Ohne Link", "", 400, "D"),
        )
        val n = LaenderZusatz.neueste(m, 5)
        assertEquals(listOf("Neu", "Alt"), n.map { it.titel })
        val s = LaenderZusatz.meldungenSchreiben(LaenderZusatz.Meldungen(5L, n))
        assertEquals(2, LaenderZusatz.meldungenLesen(s)!!.liste.size)
        assertNull(LaenderZusatz.meldungenLesen("x"))
        assertEquals("\"Deutschland\" Wirtschaft", LaenderZusatz.suchbegriff("Deutschland", true))
        assertEquals("\"Germany\" economy", LaenderZusatz.suchbegriff("Germany", false))
        assertTrue(LaenderZusatz.gueltig(0, 29 * 60_000L, LaenderZusatz.MELDUNGEN_MS))
        assertFalse(LaenderZusatz.gueltig(0, 31 * 60_000L, LaenderZusatz.MELDUNGEN_MS))
    }

    @Test
    fun textenVollstaendig() {
        for (s in listOf(De, En)) {
            for (k in Kennzahl.entries) {
                assertTrue("Label ${k.name}", s.globusMehrLabel(k).isNotBlank() && s.globusMehrLabel(k) != k.name)
                assertTrue("Info ${k.name}", s.globusMehrInfo(k).length > 60)
                assertFalse("Gedankenstrich ${k.name}", s.globusMehrInfo(k).contains('–') || s.globusMehrInfo(k).contains('—'))
            }
            for (g in LaenderZusatz.Warengruppe.entries) {
                assertTrue(s.globusMehrGruppe(g) != g.name)
                assertNotNull(s.globusMehrGruppeInfo(g, 2024))
                assertEquals(g, LaenderZusatz.Warengruppe.von(g.kuerzel))
            }
        }
        assertTrue(LaenderZusatz.KENNUNGEN.contains("SP.DYN.LE00.IN"))
        assertEquals(LaenderZusatz.KENNUNGEN.size, LaenderZusatz.KENNUNGEN.distinct().size)
    }
}
