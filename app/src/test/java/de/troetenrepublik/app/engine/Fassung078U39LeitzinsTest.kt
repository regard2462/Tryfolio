package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.LeitzinsTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Leitzins-Historie (BIS, Central bank policy rates): Leser der Buendeldatei, Eurolaender, Nachtragen aus Tageswerten,
// Kernzeile und Verlauf an Ausschnitten der echten Daten (Stand 12.11.2026).
class Fassung078U39LeitzinsTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun quelle(name: String): String = javaClass.classLoader!!.getResourceAsStream("leitzins/$name")!!.readBytes().decodeToString()

    private val d by lazy { Leitzins.lesen(quelle("leitzins-ausschnitt.json"))!! }
    private val tage by lazy { quelle("bis_tage_ausschnitt.csv") }

    private fun m(jahr: Int, monat: Int) = jahr * 12 + monat - 1

    @Test fun liestMonatsreiheUndSchritte() {
        val gb = d.laender.getValue("GB")
        assertEquals(m(1946, 1), gb.ab)
        assertEquals(970, gb.werte.size)
        assertEquals(m(2026, 10), gb.bis)
        assertEquals(3.75, gb.werte.last()!!, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 9), gb.letzterTag)
        val s = gb.schritte.last()
        assertEquals(LocalDate.of(2026, 11, 5), s.tag)
        assertEquals(0.25, s.aenderung, 1e-9)
        assertEquals(4.0, s.nachher, 1e-9)
        assertTrue(gb.schritte.size <= Leitzins.SCHRITTE)
        assertEquals(gb.schritte.sortedBy { it.tag }.map { it.tag }, gb.schritte.map { it.tag })
        assertEquals(m(1999, 1), Leitzins.monat("1999-01"))
        assertNull(Leitzins.monat("1999-13"))
        assertNull(Leitzins.monat("1999"))
    }

    @Test fun kernzeile() {
        // Schritt nach dem Ende der Monatsreihe: der Satz danach gilt, mit dem Tag des Schritts
        val gb = Leitzins.kern(d.laender.getValue("GB"))!!
        assertEquals(4.0, gb.satz, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 5), gb.seit)
        assertFalse(gb.nurMonat)
        // Ohne Zinsschritte: Monat, seit dem die Reihe den Satz zeigt
        val r = Leitzins.Reihe(m(2020, 1), listOf(1.0, 1.0, 2.0, 2.0, 2.0), emptyList(), null)
        val k = Leitzins.kern(r)!!
        assertEquals(2.0, k.satz, 1e-9)
        assertEquals(LocalDate.of(2020, 3, 1), k.seit)
        assertTrue(k.nurMonat)
        assertNull(Leitzins.kern(Leitzins.Reihe(m(2020, 1), listOf(null, null), emptyList(), null)))
    }

    @Test fun eurolaender() {
        assertEquals("XM", Leitzins.gebiet("DE"))
        assertEquals("GB", Leitzins.gebiet("GB"))
        val xm = d.laender.getValue("XM")
        // Deutschland: Bundesbank bis Ende 1998, ab Januar 1999 die EZB
        val de = Leitzins.fuer(d, "DE")!!
        assertEquals(m(1948, 7), de.ab)
        assertEquals(xm.bis, de.bis)
        assertEquals(3.0, de.werte[m(1998, 12) - de.ab]!!, 1e-9)
        assertEquals(xm.werte.first()!!, de.werte[m(1999, 1) - de.ab]!!, 1e-9)
        assertEquals(xm.schritte.map { it.tag }, de.schritte.map { it.tag })
        // Griechenland: eigene Reihe bis Ende 2000, Beitritt 2001
        val gr = Leitzins.fuer(d, "GR")!!
        assertEquals(3.75, gr.werte[m(2000, 12) - gr.ab]!!, 1e-9)
        assertEquals(xm.werte[m(2001, 1) - xm.ab]!!, gr.werte[m(2001, 1) - gr.ab]!!, 1e-9)
        // Kroatien: Beitritt 2023; die eigene Reihe endet 2022
        val hr = Leitzins.fuer(d, "HR")!!
        assertEquals(xm.werte[m(2023, 1) - xm.ab]!!, hr.werte[m(2023, 1) - hr.ab]!!, 1e-9)
        assertEquals(xm.bis, hr.bis)
        // Euroland ohne eigene Reihe in der Datei: Euroraum ab dem Beitritt
        val ee = Leitzins.fuer(d, "EE")!!
        assertEquals(m(2011, 1), ee.ab)
        assertTrue(ee.schritte.all { it.tag >= LocalDate.of(2011, 1, 1) })
        assertEquals(m(1999, 1), Leitzins.fuer(d, "FI")!!.ab)
        // Land ohne Reihe
        assertNull(Leitzins.fuer(d, "NG"))
    }

    @Test fun tageswerteLesen() {
        val gb = Leitzins.tageLesen(tage, "GB")
        assertEquals(LocalDate.of(2026, 10, 26), gb.first().first)
        assertEquals(4.0, gb.last().second, 1e-9)
        // NaN faellt weg: die Philippinen enden am letzten Tag mit Zahl
        val ph = Leitzins.tageLesen(tage, "PH")
        assertEquals(LocalDate.of(2026, 11, 6), ph.last().first)
        assertTrue(Leitzins.tageLesen(tage, "US").isEmpty())
        assertTrue(Leitzins.tageLesen("", "GB").isEmpty())
        assertTrue(Leitzins.tageLesen("A,B\n1,2", "GB").isEmpty())
        val kurz = Leitzins.tageKuerzen(tage, "GB")!!
        assertEquals(gb.map { it.first to it.second }, Leitzins.tageLesen(kurz, "GB"))
        assertNull(Leitzins.tageKuerzen(tage, "US"))
    }

    @Test fun adresseAbDemMonatNachDemEnde() {
        val gb = d.laender.getValue("GB")
        assertEquals(
            "https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D.GB?startPeriod=2026-11-01&detail=dataonly&format=csv",
            Leitzins.adresse("GB", gb),
        )
        val dez = Leitzins.Reihe(m(2026, 12), listOf(1.0), emptyList(), null)
        assertTrue(Leitzins.adresse("XM", dez).contains("D.XM?startPeriod=2027-01-01"))
    }

    @Test fun nachtragenAusTageswerten() {
        // Stand der Datei vor dem Schritt vom 5. November: der Schritt kommt aus den Tageswerten
        val alt = Leitzins.Reihe(m(2026, 8), listOf(3.75, 3.75, 3.75), emptyList(), LocalDate.of(2026, 11, 3))
        val neu = Leitzins.ergaenzen(alt, Leitzins.tageLesen(tage, "GB"))
        assertEquals(m(2026, 11), neu.bis)
        assertEquals(listOf(3.75, 3.75, 3.75, 4.0), neu.werte)
        assertEquals(1, neu.schritte.size)
        assertEquals(LocalDate.of(2026, 11, 5), neu.schritte[0].tag)
        assertEquals(3.75, neu.schritte[0].vorher, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 9), neu.letzterTag)
        assertEquals(4.0, Leitzins.kern(neu)!!.satz, 1e-9)
        // Die Datei kennt den Schritt schon: nichts doppelt
        val gb = d.laender.getValue("GB")
        val gleich = Leitzins.ergaenzen(gb, Leitzins.tageLesen(tage, "GB"))
        assertEquals(gb.schritte.map { it.tag }, gleich.schritte.map { it.tag })
        assertEquals(m(2026, 11), gleich.bis)
        assertEquals(4.0, gleich.werte.last()!!, 1e-9)
        // Ohne Tageswerte bleibt die Reihe
        assertTrue(Leitzins.ergaenzen(gb, emptyList()) === gb)
        // Luecke ueber einen Monat ohne Wert: der letzte Satz gilt fort; hoechstens SCHRITTE Zinsschritte
        val lang = (1..20).map { LocalDate.of(2027, 1, it) to 4.0 + it * 0.25 }
        val viele = Leitzins.ergaenzen(alt, lang)
        assertEquals(Leitzins.SCHRITTE, viele.schritte.size)
        assertEquals(m(2027, 1), viele.bis)
        assertEquals(3.75, viele.werte[m(2026, 12) - viele.ab]!!, 1e-9)
        assertEquals(9.0, viele.werte.last()!!, 1e-9)
    }

    @Test fun verlaufAlsTreppe() {
        val r = Leitzins.Reihe(m(2020, 1), listOf(1.0, 1.0, null, 2.0, 2.0), emptyList(), null)
        val t = Leitzins.treppe(r)
        // Start, Wechsel mit zwei Punkten am selben Ort, Ende nach dem letzten Monat
        assertEquals(listOf(2020.0 to 1.0, 2020.25 to 1.0, 2020.25 to 2.0, (m(2020, 1) + 5) / 12.0 to 2.0), t)
        assertEquals(m(2020, 1), Leitzins.verlaufAb(r, 30))
        val xm = d.laender.getValue("XM")
        assertEquals(xm.bis + 1 - 120, Leitzins.verlaufAb(xm, 10))
        val (tief, hoch) = Leitzins.spanne(xm, m(1999, 1))!!
        assertEquals(0.0, tief, 1e-9)
        assertEquals(4.75, hoch, 1e-9)
        assertNull(Leitzins.spanne(Leitzins.Reihe(0, listOf(null), emptyList(), null), 0))
    }

    @Test fun unlesbaresWirdAbgelehnt() {
        assertNull(Leitzins.lesen(""))
        assertNull(Leitzins.lesen("{}"))
        assertNull(Leitzins.lesen("""{"stand":"2026-11-12","l":{}}"""))
        // Ungerade Laufliste und Lauf ohne Wert fallen weg, das Land daneben bleibt
        val t = """{"stand":"2026-11-12","l":{"AA":{"ab":"2020-01","w":[1000,2,2000]},"BB":{"ab":"2020-01","w":[null,3]},"CC":{"ab":"2020-01","w":[1500,2,null,1,1750,1],"s":[["2020-04-02",1500,1750],["x",1,2]]}}}"""
        val x = Leitzins.lesen(t)!!
        assertEquals(setOf("CC"), x.laender.keys)
        val cc = x.laender.getValue("CC")
        assertEquals(listOf(1.5, 1.5, null, 1.75), cc.werte)
        assertEquals(1, cc.schritte.size)
        assertNull(cc.letzterTag)
    }

    @Test fun ganzeDateiIstGepflegt() {
        val n = Leitzins.DATEI
        val text = asset(n).readText(Charsets.UTF_8)
        val g = Leitzins.lesen(text)!!
        assertTrue(n in Alter.dateien())
        assertEquals(1, Datenbuendel.SCHEMA[n])
        assertTrue(asset(n).length() < 120_000)
        assertTrue(g.laender.size >= 45)
        for (a2 in listOf("US", "XM", "GB", "JP", "CH", "CN", "IN", "BR")) {
            val r = g.laender.getValue(a2)
            assertNotNull(a2, Leitzins.kern(r))
            assertTrue(a2, r.schritte.isNotEmpty() && r.letzterTag != null)
        }
        val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
        assertEquals(text, im.readText(Charsets.UTF_8))
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, text))
    }

    @Test fun texteUndNennung() {
        assertEquals(LeitzinsTexte.schluessel(true), LeitzinsTexte.schluessel(false))
        for (de in listOf(true, false)) for (k in LeitzinsTexte.schluessel(de)) {
            val t = LeitzinsTexte.text(de, k, listOf("a", "b"))
            assertTrue(k, t.isNotBlank() && !t.contains("{") && !t.contains("–") && !t.contains("—"))
        }
        // Die BIS verlangt die Nennung als Quelle
        assertTrue(LeitzinsTexte.text(true, "info", listOf("x")).contains("Bank für Internationalen Zahlungsausgleich (BIS)"))
        assertTrue(LeitzinsTexte.text(false, "info", listOf("x")).contains("Bank for International Settlements (BIS)"))
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("globus/leitzins.json: Bank for International Settlements (BIS), Central bank policy rates"))
    }
}
