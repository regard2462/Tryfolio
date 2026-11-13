package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.FinderRueckblick.Abschnitt
import de.troetenrepublik.app.engine.FinderRueckblick.Aenderung
import de.troetenrepublik.app.engine.FinderRueckblickMessung.Stichtag
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Fassung 0.78 H1: Finder-Rueckblick mit den damaligen Mitgliedern des S&P 500 (fja05680/sp500, MIT). Lesen der
// Mitgliederliste, Mitglieder zum Stichtag, Umbenennungen (Kurse unter dem heutigen Kuerzel), Pruefung der Kursreihe,
// Abdeckung je Stichtag in der Datei und der Text dazu.
class Fassung078H1MitgliederTest {

    private fun t(d: String) = LocalDate.parse(d).toEpochDay()

    // Ausschnitt aus sp500_ticker_start_end.csv (Stand 07.09.2026) und die Spalte "Date added" aus sp500.csv.
    private val csv = """
        ticker,start_date,end_date
        AAPL,1996-01-02,
        ALXN,2012-05-25,2021-07-21
        BRK.B,2010-02-16,
        CA,1996-01-02,2018-11-06
        COG,2008-06-23,2021-10-04
        CTRA,2021-10-04,2026-05-07
        EP,1999-10-26,2012-05-25
        FB,2013-12-23,2022-06-09
        FI,2023-06-07,2025-11-11
        FISV,2001-04-02,2023-06-07
        FISV,2025-11-11,
        KMI,2000-12-12,2007-05-31
        KMI,2012-05-25,
        LIN,2018-11-06,
        META,2022-06-09,
        PX,1996-01-02,2018-10-31
    """.trimIndent()
    private val aufgenommen = mapOf(
        "AAPL" to "1982-11-30", "BRK.B" to "2010-02-16", "FISV" to "2001-04-02", "KMI" to "2012-05-25", "LIN" to "1992-07-01", "META" to "2013-12-23",
    )

    // Dasselbe im Aufbau von indizes/sp500_mitglieder.json (wie tools/indizes/sp500_mitglieder.py es schreibt).
    private val datei: String = run {
        val a = csv.lines().drop(1).joinToString(",") { z ->
            val p = z.split(',')
            """["${p[0]}",${t(p[1])}""" + (if (p[2].isNotBlank()) ",${t(p[2])}" else "") + "]"
        }
        val s = aufgenommen.entries.joinToString(",") { (k, v) -> """"$k":${t(v)}""" }
        """{"stand":"2026-08-18","quelle":"https://github.com/fja05680/sp500","lizenz":["MIT License"],"tage":"seit 1970-01-01","a":[$a],"seit":{$s}}"""
    }

    // Aenderungen laut Wikipedia um diese Tage (Ausschnitt der Tabelle "changes").
    private val wiki = listOf(
        Aenderung(t("2026-05-07"), "VEEV", "CTRA"),
        Aenderung(t("2021-07-21"), "MRNA", "ALXN"),
        Aenderung(t("2018-11-06"), "KEYS", "CA"),
        Aenderung(t("2012-05-21"), "ALXN", "MMI"),
        Aenderung(t("2012-05-17"), "KMI", "EP"),
        Aenderung(t("2008-06-23"), "COG", "BC"),
    )

    private val liste = FinderRueckblick.mitgliederLesen(datei)!!

    private fun abschnitt(k: String, ab: String) = liste.abschnitte.single { it.kuerzel == k && it.ab == t(ab) }

    @Test fun listeLesen() {
        assertEquals("2026-08-18", liste.stand)
        assertEquals(16, liste.abschnitte.size)
        assertEquals(t("1996-01-02"), liste.beginn)
        assertEquals(Abschnitt("FB", t("2013-12-23"), t("2022-06-09")), abschnitt("FB", "2013-12-23"))
        assertNull(abschnitt("META", "2022-06-09").bis)
        assertEquals(t("1992-07-01"), liste.seit["LIN"])
        // Austrittstag zaehlt nicht mehr: FB am 08.06.2022 im Index, am 09.06.2022 META
        assertEquals(listOf("FB"), liste.zum(t("2022-06-08")).map { it.kuerzel }.filter { it == "FB" || it == "META" })
        assertEquals(listOf("META"), liste.zum(t("2022-06-09")).map { it.kuerzel }.filter { it == "FB" || it == "META" })
        assertNull(FinderRueckblick.mitgliederLesen("""{"a":[]}"""))
        assertNull(FinderRueckblick.mitgliederLesen("kein JSON"))
    }

    @Test fun umbenennungenAufgeloest() {
        val y = FinderRueckblick.yahooJeAbschnitt(liste, wiki)
        // (a) Heutige Mitglieder: Kette bis zum Aufnahmetag
        assertEquals("META", y[abschnitt("FB", "2013-12-23")])
        assertEquals("FISV", y[abschnitt("FI", "2023-06-07")])
        assertEquals("FISV", y[abschnitt("FISV", "2001-04-02")])
        // (b) Umbenannt und spaeter ausgeschieden: COG wurde CTRA (an dem Tag endet nur COG, beginnt nur CTRA)
        assertEquals("CTRA", y[abschnitt("COG", "2008-06-23")])
        // Ersetzt, nicht umbenannt: CA ging laut Wikipedia am Tag der Aufnahme von LIN; EP endet am Tag, an dem
        // KMI und ALXN beginnen
        assertEquals("CA", y[abschnitt("CA", "1996-01-02")])
        assertEquals("EP", y[abschnitt("EP", "1999-10-26")])
        assertEquals("PX", y[abschnitt("PX", "1996-01-02")])
        assertEquals("BRK-B", y[abschnitt("BRK.B", "2010-02-16")])
        assertEquals("KMI", y[abschnitt("KMI", "2000-12-12")])
        // Ohne die Gegenprobe wuerde CA faelschlich LIN zugeordnet
        assertEquals("LIN", FinderRueckblick.yahooJeAbschnitt(liste)[abschnitt("CA", "1996-01-02")])
    }

    // Taegliche Kurse von a bis b.
    private fun kurse(von: String, bis: String): List<Candle> =
        (t(von)..t(bis)).map { d -> Candle(d * FinderRueckblick.TAG_MS, 10.0, 10.0, 10.0, 10.0) }

    @Test fun kursreiheMussZurFirmaVonDamalsPassen() {
        val stichtag = t("2012-01-31")
        // LIN-Kurse beginnen 2018: fuer den Abschnitt von CA ab 2012 gehoeren sie zu einer anderen Firma
        assertFalse(FinderRueckblick.kurseFuer(kurse("2018-10-31", "2025-10-31"), stichtag))
        assertTrue(FinderRueckblick.kurseFuer(kurse("2008-01-02", "2025-10-31"), stichtag))
        // Beginn bis 31 Tage nach dem ersten Stichtag gilt noch (Aufnahme kurz nach dem Boersengang)
        assertTrue(FinderRueckblick.kurseFuer(kurse("2012-02-20", "2025-10-31"), stichtag))
        assertFalse(FinderRueckblick.kurseFuer(kurse("2012-01-02", "2012-02-01"), stichtag))
    }

    @Test fun mitgliederZumStichtagMitAusgeschiedenen() {
        val y = FinderRueckblick.yahooJeAbschnitt(liste, wiki)
        // Ende Januar 2020 laut Liste: AAPL, ALXN, BRK.B, COG, FB, FISV, KMI, LIN; ALXN ohne passende Kurse
        val (soll, messbar) = FinderRueckblick.zumStichtag(liste, y, { it.kuerzel != "ALXN" }, t("2020-01-31"))
        assertEquals(8, soll)
        assertEquals(setOf("AAPL", "BRK-B", "CTRA", "META", "FISV", "KMI", "LIN"), messbar)
        // Anfang 2012: EP noch Mitglied (heute ohne Kurse bei Yahoo), KMI noch nicht, COG unter CTRA
        val (soll2012, messbar2012) = FinderRueckblick.zumStichtag(liste, y, { true }, t("2012-01-31"))
        assertEquals(7, soll2012)
        assertEquals(setOf("AAPL", "BRK-B", "CA", "CTRA", "EP", "FISV", "PX"), messbar2012)
    }

    private fun stichtag(tag: String, soll: Int, gemessen: Int): Stichtag {
        val werte = (0 until gemessen).map { i ->
            FinderRueckblickMessung.Wert("S$i", mapOf(Aktienfinder.Bereich.TREND to i.toDouble()), listOf(1, 3, 6, 12).associateWith { 0.01 * i })
        }
        val m = listOf(1, 3, 6, 12).associateWith { 0.0 }
        return Stichtag(t(tag), werte, m, m, soll)
    }

    @Test fun abdeckungJeStichtagInDerDatei() {
        val st = listOf(stichtag("2012-01-31", 500, 350), stichtag("2012-02-29", 500, 400), stichtag("2025-10-31", 503, 503))
        val d = FinderRueckblickMessung.zusammenfassen(st, mapOf(Aktienfinder.Bereich.TREND to 1.0), "2026-11-12", false, mitgliederStand = "2026-08-18")
        assertEquals(listOf("2012-01-31", "2012-02-29", "2025-10-31"), d.abdeckung.map { it.tag })
        assertEquals(350, d.abdeckung[0].gemessen)
        assertEquals(0.7, d.abdeckung[0].anteil, 1e-12)
        // Schnitt ueber die Stichtage: (0,7 + 0,8 + 1,0) / 3
        assertEquals(2.5 / 3, d.abdeckungSchnitt()!!, 1e-12)
        assertEquals(1.0 - 2.5 / 3, d.fehlend, 1e-12)
        assertEquals(listOf(2012, 2025), d.abdeckungJeJahr().map { it.first })
        assertEquals(0.75, d.abdeckungJeJahr()[0].second, 1e-12)
        assertEquals(1.0, d.abdeckungJeJahr()[1].second, 1e-12)
        assertEquals("2026-08-18", d.mitgliederStand)
        assertEquals(d, FinderRueckblickMessung.lesen(FinderRueckblickMessung.schreiben(d)))
        // Aeltere Datei ohne Abdeckung: kein Schnitt, die Anzeige bleibt beim alten Hinweis
        val alt = FinderRueckblickMessung.lesen("""{"stand":"2026-11-12","stichtage":166,"werte":429,"fehlend":0.144}""")!!
        assertNull(alt.abdeckungSchnitt())
        assertTrue(alt.abdeckungJeJahr().isEmpty())
    }

    @Test fun textNenntAbdeckungUndStreichtSchoenenNurBeiVollerAbdeckung() {
        val teil = De.afRueckblickAbdeckung("86 %", listOf("2012" to "71 %", "2025" to "98 %"), false)
        assertEquals("• Gemessen je Stichtag: im Schnitt 86 % der damaligen Mitglieder (2012: 71 %, 2025: 98 %). Zu den übrigen fehlen Kurse, das schönt das Ergebnis.", teil)
        val voll = De.afRueckblickAbdeckung("100 %", emptyList(), true)
        assertEquals("• Gemessen je Stichtag: im Schnitt 100 % der damaligen Mitglieder.", voll)
        assertTrue(En.afRueckblickAbdeckung("86%", listOf("2012" to "71%"), false).endsWith("which flatters the result."))
        // In den Grenzen ersetzt die Abdeckung den pauschalen Satz, Dividenden bleiben
        val g = De.afRueckblickGrenzen(null, false, 0.01, 0.01, null, teil)
        assertTrue(g.contains(teil) && g.endsWith("• Kurse ohne Dividenden."))
        assertFalse(g.contains("Fehlende Firmen"))
        assertTrue(De.afRueckblickGrenzen(null, false, 0.01, 0.01, null, null).contains("Fehlende Firmen schönen das Ergebnis."))
        assertFalse(En.afRueckblickGrenzen(null, false, 0.01, 0.01, null, "x").contains("Missing firms"))
    }

    @Test fun mitgelieferteListe() {
        val f = listOf("src/main/assets/indizes/sp500_mitglieder.json", "../app/src/main/assets/indizes/sp500_mitglieder.json").map { File(it) }.first { it.exists() }
        val text = f.readText(Charsets.UTF_8)
        // MIT: Urheberzeile und Erlaubnistext liegen der Datei bei
        assertTrue(text.contains("Copyright (c) 2019-2020 Farrell J. Aultman"))
        assertTrue(text.contains("The above copyright notice and this permission notice shall be included"))
        assertTrue("Datei zu gross: ${f.length()}", f.length() < 300_000)
        val m = FinderRueckblick.mitgliederLesen(text)!!
        assertEquals(t("1996-01-02"), m.beginn)
        // Je Stichtag rund 500 Mitglieder
        for (d in listOf("2001-06-29", "2012-01-31", "2020-03-31", "2025-10-31")) {
            assertTrue(d, m.zum(t(d)).size in 490..510)
        }
        val y = FinderRueckblick.yahooJeAbschnitt(m)
        assertEquals("META", y[m.abschnitte.single { it.kuerzel == "FB" }])
        assertNotNull(m.seit["AAPL"])
    }
}
