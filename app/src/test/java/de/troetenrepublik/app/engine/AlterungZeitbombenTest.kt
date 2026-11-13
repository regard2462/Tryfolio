package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.market.Behoerden
import de.troetenrepublik.app.market.RegierungOge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Zeitbomben im Code: Regierung und Praesident aus Daten, Steuerwerte je Jahr, Ordnungszahlen. Nach einem, drei und fuenf
// Jahren fuer alle Teile.
class AlterungZeitbombenTest {

    @After fun zurueck() {
        RegierungOge.amtSetzen(null)
        Steuerwerte.setzen(null)
        Uhr.vorstellenTage(0)
    }

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)
    private fun tag(s: String): Long = LocalDate.parse(s).toEpochDay() * 86_400_000L + 3_600_000L

    private fun ogeSeite(vararg eintraege: Pair<String, String>): String =
        """{"recordsFiltered":"${eintraege.size}","data":[""" + eintraege.joinToString(",") { (name, tag) ->
            """{"type":"<a href='https://x.gov/${name.take(3)}.pdf'>Transaction</a>","name":"$name","docDate":"${tag}T00:00:00","agency":"A","title":"T"}"""
        } + "]}"

    // ------------------------------------------------------------ Regierung aus Daten

    @Test fun amtDateiPasstZumEingebauten() {
        val a = RegierungOge.amtLesen(asset(RegierungOge.AMT_DATEI))
        assertNotNull(a)
        assertEquals(RegierungOge.Amt(), a)
    }

    @Test fun amtLesenPrueftDieForm() {
        assertNull(RegierungOge.amtLesen("kein json"))
        assertNull(RegierungOge.amtLesen("""{"seit":"gestern"}"""))
        assertNull(RegierungOge.amtLesen("""{"seit":"2029-01-20","nachname":"Muster(.*)"}"""))
        assertNull(RegierungOge.amtLesen("""{"seit":"2029-01-20","nachname":""}"""))
        assertNotNull(RegierungOge.amtLesen("""{"seit":"2029-01-20","bis":"","nachname":"Muster","vorname":"Max","praesidentId":"muster","vizeId":"beispiel","vize":"Beispiel","neuesFeld":1}"""))
    }

    @Test fun ogeFilterAusDenDaten() {
        val seite = ogeSeite("Trump, Donald J" to "2027-03-02", "Kupor, Scott A" to "2027-03-03", "Neu, Nora" to "2029-03-04", "Alt, Alex" to "2024-12-01")
        // Heutige Amtszeit: der Praesident und alles vor dem Amtsbeginn fehlen
        val jetzt = RegierungOge.ogeListeLesen(seite).second.map { it.name }
        assertEquals(listOf("Kupor, Scott A", "Neu, Nora"), jetzt)
        // Amtswechsel Januar 2029: neuer Praesident, neuer Beginn, kein fester Name
        RegierungOge.amtSetzen(RegierungOge.Amt(seit = "2029-01-20", bis = "2033-01-20", praesidentId = "neu", nachname = "Neu", vorname = "Nora", vizeId = "v", vize = "Vize"))
        val danach = RegierungOge.ogeListeLesen(seite).second.map { it.name }
        assertEquals(emptyList<String>(), danach)
        val seite2 = ogeSeite("Trump, Donald J" to "2029-03-02", "Neu, Nora" to "2029-03-03", "Kupor, Scott A" to "2029-03-04")
        assertEquals(listOf("Trump, Donald J", "Kupor, Scott A"), RegierungOge.ogeListeLesen(seite2).second.map { it.name })
    }

    @Test fun vermoegenJahreAbAmtsbeginn() {
        assertEquals("Annual (2025)</a>", RegierungOge.ogeVermoegenSuchen(2026).first())
        assertEquals(listOf("Annual (2025)</a>", "Annual (2026)</a>", "Annual (2027)</a>", "Nominee 278</a>", "Termination</a>"), RegierungOge.ogeVermoegenSuchen(2026))
        RegierungOge.amtSetzen(RegierungOge.Amt(seit = "2029-01-20"))
        assertEquals(listOf("Annual (2029)</a>", "Annual (2030)</a>", "Annual (2031)</a>", "Annual (2032)</a>", "Nominee 278</a>", "Termination</a>"), RegierungOge.ogeVermoegenSuchen(2031))
        // Jahr vor dem Amtsbeginn: keine Rueckwaerts-Schleife
        assertEquals(listOf("Annual (2029)</a>", "Annual (2030)</a>", "Nominee 278</a>", "Termination</a>"), RegierungOge.ogeVermoegenSuchen(2027))
    }

    @Test fun weissesHausOhnePraesidentAusDenDaten() {
        val html = """<a href="/x/Borg-Jared-Periodic-Transaction-Report-05.08.25.pdf">a</a>
            <a href="/x/Trump-Donald-Periodic-Transaction-Report-05.08.25.pdf">b</a>
            <a href="/x/Neu-Nora-Periodic-Transaction-Report-05.08.29.pdf">c</a>"""
        assertEquals(listOf("Borg, Jared", "Neu, Nora"), RegierungOge.weissesHausLesen(html).map { it.name })
        RegierungOge.amtSetzen(RegierungOge.Amt(seit = "2029-01-20", nachname = "Neu", vorname = "Nora", vize = "Vize"))
        assertEquals(listOf("Borg, Jared", "Trump, Donald"), RegierungOge.weissesHausLesen(html).map { it.name })
    }

    @Test fun amtVorbeiNachDemEnde() {
        val a = RegierungOge.Amt()
        assertFalse(a.vorbei("2028-12-31"))
        assertTrue(a.vorbei("2029-01-20"))
        assertFalse(RegierungOge.Amt(bis = "").vorbei("2040-01-01"))
    }

    // ------------------------------------------------------------ Ordnungszahlen

    @Test fun kongressLinksNachEinemDreiUndFuenfJahren() {
        listOf(1, 3, 5).forEach { j ->
            Uhr.vorstellenTage(365L * j)
            val jahr = Uhr.heute().year
            val url = Behoerden.gesetzAdresse("H.R. 1", jahr)!!
            assertTrue(url, url.contains("-congress/house-bill/1"))
            assertEquals(Behoerden.ordinal(Behoerden.kongressNummer(jahr)) + "-congress", url.substringAfter("/bill/").substringBefore("/"))
        }
        assertEquals(119, Behoerden.kongressNummer(2026))
        assertEquals(121, Behoerden.kongressNummer(2029))
        assertEquals(124, Behoerden.kongressNummer(2035))
    }

    // ------------------------------------------------------------ Steuerwerte

    @Test fun steuerwerteDateiPasstZumEingebauten() {
        val d = Steuerwerte.lesen(asset(Steuerwerte.DATEI))
        assertNotNull(d)
        assertEquals(Steuerwerte.EINGEBAUT.jahre, d!!.jahre)
    }

    @Test fun werteFuerJahreOhneEintrag() {
        assertEquals(0.26375, Steuerwerte.fuer(2026).satz, 1e-12)
        assertFalse(Steuerwerte.geschaetzt(2026))
        assertTrue(Steuerwerte.geschaetzt(2029))
        assertEquals(Steuerwerte.fuer(2026), Steuerwerte.fuer(2029))
        assertEquals(2.55, Steuerwerte.fuer(2023).basiszins!!, 0.0)
        // Vor dem aeltesten Eintrag gilt der aelteste
        assertEquals(Steuerwerte.fuer(2023), Steuerwerte.fuer(2010))
        assertEquals(2026, Steuerwerte.letztesJahr())
    }

    @Test fun steuerwerteAusDerFerndatei() {
        val neu = Steuerwerte.lesen("""{"v":1,"stand":"2028-01-01","jahre":{"2026":{"pauschbetrag":1000,"pauschbetragPaar":2000},"2028":{"pauschbetrag":1500,"pauschbetragPaar":3000,"abgeltung":26.0,"soli":0.0,"basiszins":2.0},"neuesFeld":1}}""")
        assertNull(neu)
        val d = Steuerwerte.lesen("""{"v":1,"stand":"2028-01-01","jahre":{"2026":{"pauschbetrag":1000,"pauschbetragPaar":2000},"2028":{"pauschbetrag":1500,"pauschbetragPaar":3000,"abgeltung":26.0,"soli":0.0,"basiszins":2.0,"neu":1}}}""")!!
        Steuerwerte.setzen(d)
        assertEquals(0.26, Steuerwerte.fuer(2028).satz, 1e-12)
        // 2027 hat keinen Eintrag: gilt 2026; 2029 gilt 2028
        assertEquals(1000.0, Steuerwerte.fuer(2027).pauschbetrag, 0.0)
        assertEquals(1500.0, Steuerwerte.fuer(2029).pauschbetrag, 0.0)
        // Die Steuer im Jahr 2028: 5.000 Ertrag, Pauschbetrag 1.500 (Auftrag 3.000, gedeckelt auf 3.000 Paar, genutzt bis 3.000)
        val r = Tax.apply(TaxState(2028), 5_000.0, Tax.Kind.GENERAL, 3_000.0, tag("2028-06-01"))
        assertEquals(Tax.steuer(2_000.0, Steuerwerte.fuer(2028)), r.tax, 1e-9)
        assertEquals(520.0, r.tax, 1e-9)
    }

    @Test fun kaputteSteuerdateiIstNull() {
        assertNull(Steuerwerte.lesen("kein json"))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{}}"""))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"zwanzig":{}}}"""))
        assertNull(Steuerwerte.lesen("""{"v":1,"jahre":{"2026":{"abgeltung":250.0}}}"""))
    }

    @Test fun steuerRechnetNachEinemDreiUndFuenfJahren() {
        listOf(1, 3, 5).forEach { j ->
            Uhr.vorstellenTage(365L * j)
            val jahr = Uhr.heute().year
            val r = Tax.apply(TaxState(jahr), 2_000.0, Tax.Kind.STOCK, 1_000.0, Uhr.jetzt())
            // Mit den Werten des juengsten bekannten Jahres: 1.000 steuerfrei, 1.000 zu 26,375 %
            assertEquals(263.75, r.tax, 1e-9)
            assertTrue(Steuerwerte.geschaetzt(jahr))
            assertEquals(0.26375, Tax.RATE, 1e-12)
        }
    }
}
