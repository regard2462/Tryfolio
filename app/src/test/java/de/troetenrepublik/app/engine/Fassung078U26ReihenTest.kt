package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.LangeReihen.Anlage
import de.troetenrepublik.app.engine.LangeReihen.Klasse
import de.troetenrepublik.app.engine.LangeReihen.Monatsreihe
import de.troetenrepublik.app.engine.LangeReihen.Reihe
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.LangeSichtTexte
import de.troetenrepublik.app.ui.PortfolioTool
import de.troetenrepublik.app.ui.WERKZEUG_GRUPPEN
import de.troetenrepublik.app.ui.werkzeugFormen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

// Lange Monatsreihen und das Werkzeug „Lange Sicht“: Datei gegen einzelne Werte der Quelldateien, Rechenwege mit festen
// Beispielen, Verkettung mit Tageskursen, Mischungen in Jahresschritten, Texte und Platz in der Liste.
class Fassung078U26ReihenTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val datei by lazy { LangeReihen.lesen(asset(LangeReihen.DATEI).readText(Charsets.UTF_8))!! }
    private val jst by lazy { HistorieJst.lesen(asset(HistorieJst.DATEI).readText(Charsets.UTF_8))!! }
    private fun m(jahr: Int, monat: Int) = LangeReihen.monat(jahr, monat)

    // ------------------------------------------------------------ Datei

    @Test fun dateiTraegtDieWerteDerQuellen() {
        fun wert(r: Reihe, jahr: Int, monat: Int) = datei.reihen.getValue(r).wert(m(jahr, monat))
        // In den Quelldateien nachgelesen (BLS-Tabelle CUUR0000SA0, H.15 des Federal Reserve Board, Preistabelle der Weltbank)
        assertEquals(9.8, wert(Reihe.PREISE, 1913, 1)!!, 0.0)
        assertEquals(29.3, wert(Reihe.PREISE, 1960, 1)!!, 0.0)
        assertEquals(210.228, wert(Reihe.PREISE, 2008, 12)!!, 0.0)
        assertEquals(336.94, wert(Reihe.PREISE, 2026, 9)!!, 0.0)
        assertEquals(0.72, wert(Reihe.SCHATZWECHSEL, 1934, 1)!!, 0.0)
        assertEquals(12.0, wert(Reihe.SCHATZWECHSEL, 1980, 1)!!, 0.0)
        assertEquals(0.03, wert(Reihe.SCHATZWECHSEL, 2008, 12)!!, 0.0)
        assertEquals(2.62, wert(Reihe.RENDITE_5, 1953, 4)!!, 0.0)
        assertEquals(15.93, wert(Reihe.RENDITE_5, 1981, 9)!!, 0.0)
        assertEquals(2.83, wert(Reihe.RENDITE_10, 1953, 4)!!, 0.0)
        assertEquals(15.32, wert(Reihe.RENDITE_10, 1981, 9)!!, 0.0)
        assertEquals(2.42, wert(Reihe.RENDITE_10, 2008, 12)!!, 0.0)
        assertEquals(35.0, wert(Reihe.GOLD, 1960, 1)!!, 0.0)
        assertEquals(675.0, wert(Reihe.GOLD, 1980, 1)!!, 0.0)
        assertEquals(4319.0, wert(Reihe.GOLD, 2026, 9)!!, 0.0)
        // Vor dem Beginn der Reihe und im Monat ohne Veroeffentlichung steht nichts
        assertNull(wert(Reihe.GOLD, 1959, 12))
        assertNull(wert(Reihe.RENDITE_10, 1953, 3))
        assertNull(wert(Reihe.PREISE, 2025, 10))
        assertNotNull(wert(Reihe.PREISE, 2025, 11))
    }

    @Test fun reihenBeginnenWieDieQuellenUndHabenNurEineLuecke() {
        assertEquals(m(1913, 1), datei.reihen.getValue(Reihe.PREISE).ab)
        assertEquals(m(1934, 1), datei.reihen.getValue(Reihe.SCHATZWECHSEL).ab)
        assertEquals(m(1953, 4), datei.reihen.getValue(Reihe.RENDITE_5).ab)
        assertEquals(m(1953, 4), datei.reihen.getValue(Reihe.RENDITE_10).ab)
        assertEquals(m(1960, 1), datei.reihen.getValue(Reihe.GOLD).ab)
        assertEquals(1, datei.reihen.getValue(Reihe.PREISE).werte.count { it == null })
        for (r in listOf(Reihe.SCHATZWECHSEL, Reihe.RENDITE_5, Reihe.RENDITE_10, Reihe.GOLD)) {
            assertTrue(r.name, datei.reihen.getValue(r).werte.none { it == null })
            assertTrue(r.name, datei.reihen.getValue(r).bis >= m(2026, 9))
        }
        assertTrue(asset(LangeReihen.DATEI).length() < 60_000)
        // Gepflegt wie die uebrigen Dateien: Profil, Schema, Kopie im Datenordner, Pruefung beim Laden
        assertTrue(LangeReihen.DATEI in Alter.dateien())
        assertEquals(1, Datenbuendel.SCHEMA[LangeReihen.DATEI])
        val im = listOf(File("../daten/${LangeReihen.DATEI}"), File("daten/${LangeReihen.DATEI}")).first { it.exists() }
        assertEquals(asset(LangeReihen.DATEI).readText(Charsets.UTF_8), im.readText(Charsets.UTF_8))
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(LangeReihen.DATEI, asset(LangeReihen.DATEI).readText(Charsets.UTF_8)))
    }

    @Test fun quellenUndLizenzenStehenInDerDateiUndInDerQuellenliste() {
        assertEquals("2026-11-12", datei.stand)
        assertEquals("U.S. Bureau of Labor Statistics", datei.quelle(Reihe.PREISE)!!.name)
        assertEquals("public domain", datei.quelle(Reihe.PREISE)!!.lizenz)
        assertEquals("Board of Governors of the Federal Reserve System", datei.quelle(Reihe.RENDITE_10)!!.name)
        assertEquals("public domain", datei.quelle(Reihe.SCHATZWECHSEL)!!.lizenz)
        assertEquals("World Bank", datei.quelle(Reihe.GOLD)!!.name)
        assertTrue(datei.quelle(Reihe.GOLD)!!.lizenz.startsWith("CC BY 4.0"))
        for (r in Reihe.entries) assertTrue(datei.quelle(r)!!.url.startsWith("https://"))
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)
        assertTrue(notice.contains("indikatoren/lange_reihen.json"))
        assertTrue(notice.contains("Bureau of Labor") && notice.contains("Board of Governors") && notice.contains("The Pink Sheet (CC BY 4.0"))
        assertTrue(notice.contains("model series"))
    }

    @Test fun kaputteDateiWirdAbgelehnt() {
        assertNull(LangeReihen.lesen(""))
        assertNull(LangeReihen.lesen("{}"))
        assertNull(LangeReihen.lesen("""{"stand":"2026-11-12","quellen":{},"r":{"gold":{"ab":"1960-01","w":[35]}}}"""))
        val gut = LangeReihen.lesen(
            """{"stand":"2026-11-12","quellen":{"gold":{"name":"N","url":"https://x","reihe":"R","lizenz":"L"}},"r":{"gold":{"ab":"1960-01","w":[35,null,36.5]}}}""",
        )!!
        assertEquals(listOf(35.0, null, 36.5), gut.reihen.getValue(Reihe.GOLD).werte)
    }

    // ------------------------------------------------------------ Rechenwege

    @Test fun schatzwechselErtragAusDemDiskontsatz() {
        // 3,6 %: Kurs 1 - 0,036 * 91/360 = 0,9909; je Monat die dritte Wurzel aus 1 / 0,9909
        assertEquals(0.0030518667979535508, LangeReihen.wechselMonat(3.6), 1e-12)
        assertEquals(0.0, LangeReihen.wechselMonat(0.0), 0.0)
        assertEquals((1.0 / (1.0 - 0.12 * 91.0 / 360.0)).pow(1.0 / 3.0) - 1.0, LangeReihen.wechselMonat(12.0), 1e-12)
    }

    @Test fun anleiheKursUndMonatsertrag() {
        // Kupon gleich Rendite: Kurs 1, bei jeder Laufzeit
        assertEquals(1.0, LangeReihen.anleiheKurs(0.04, 0.04, 10.0), 1e-12)
        assertEquals(1.0, LangeReihen.anleiheKurs(0.04, 0.04, 9.0 + 11.0 / 12.0), 1e-12)
        // Rendite null: alle Zahlungen ohne Abzinsung
        assertEquals(1.4, LangeReihen.anleiheKurs(0.04, 0.0, 10.0), 1e-12)
        // Bleibt die Rendite, bleibt nur der Kupon eines Monats
        assertEquals(0.04 / 12.0, LangeReihen.anleiheMonat(4.0, 4.0, 10), 1e-12)
        // Von 4 % auf 5 % bei 10 Jahren (unabhaengig gerechnet)
        assertEquals(-0.07410913693906385, LangeReihen.anleiheMonat(4.0, 5.0, 10), 1e-12)
        // Steigende Rendite kostet, fallende bringt; die lange Laufzeit reagiert staerker
        assertTrue(LangeReihen.anleiheMonat(4.0, 3.0, 10) > LangeReihen.anleiheMonat(4.0, 3.0, 5))
        assertTrue(LangeReihen.anleiheMonat(4.0, 5.0, 10) < LangeReihen.anleiheMonat(4.0, 5.0, 5))
        assertTrue(LangeReihen.anleiheMonat(4.0, 3.0, 5) > 0.04 / 12.0)
        // Negative Rendite bleibt rechenbar
        assertTrue(LangeReihen.anleiheMonat(-0.5, -0.5, 10) < 0.0)
    }

    @Test fun verlaeufeAusDerDatei() {
        fun jahr(k: Klasse, j: Int) = LangeReihen.jahresrenditen(LangeReihen.verlauf(datei, k)!!).getValue(j)
        // Unabhaengig aus der Datei gerechnet (Python)
        assertEquals(0.36160605500907206, jahr(Klasse.ANLEIHEN_10, 1982), 1e-9)
        assertEquals(-0.14923053630448202, jahr(Klasse.ANLEIHEN_10, 2022), 1e-9)
        assertEquals(-0.07379344482829286, jahr(Klasse.ANLEIHEN_10, 1994), 1e-9)
        assertEquals(0.2875014698248606, jahr(Klasse.ANLEIHEN_5, 1982), 1e-9)
        assertEquals(-0.08523822751442256, jahr(Klasse.ANLEIHEN_5, 2022), 1e-9)
        assertEquals(0.15536834847060277, jahr(Klasse.GELDMARKT, 1981), 1e-9)
        assertEquals(0.0015181662733370072, jahr(Klasse.GELDMARKT, 2009), 1e-9)
        assertEquals(1858.0 / 1479.0 - 1.0, jahr(Klasse.GOLD, 2020), 1e-12)
        // Beginn: 1 im ersten Monat; der Geldmarkt beginnt einen Monat vor dem ersten Zins
        val a = LangeReihen.verlauf(datei, Klasse.ANLEIHEN_10)!!
        assertEquals(m(1953, 4), a.ab)
        assertEquals(1.0, a.werte.first()!!, 0.0)
        assertEquals(1.0 + LangeReihen.anleiheMonat(2.83, 3.05, 10), a.wert(m(1953, 5))!!, 1e-12)
        val g = LangeReihen.verlauf(datei, Klasse.GELDMARKT)!!
        assertEquals(m(1933, 12), g.ab)
        assertEquals(1.0 + LangeReihen.wechselMonat(0.72), g.wert(m(1934, 1))!!, 1e-12)
        assertTrue(Klasse.ANLEIHEN_5.modell && Klasse.ANLEIHEN_10.modell)
        assertFalse(Klasse.GOLD.modell || Klasse.GELDMARKT.modell)
    }

    @Test fun realUndNominal() {
        val preise = Monatsreihe(m(2000, 1), listOf(100.0, 110.0, null, 121.0))
        val r = Monatsreihe(m(2000, 1), listOf(50.0, 55.0, 60.0, 60.5))
        val real = LangeReihen.real(r, preise)
        // Bezogen auf den letzten Monat mit Preisindex
        assertEquals(listOf(60.5, 60.5, null, 60.5), real.werte.map { it?.let { x -> Math.round(x * 1e9) / 1e9 } })
        val zurueck = LangeReihen.nominal(real, preise)
        assertEquals(50.0, zurueck.werte[0]!!, 1e-9)
        assertEquals(55.0, zurueck.werte[1]!!, 1e-9)
        assertNull(zurueck.werte[2])
        // Eigener Bezugsmonat
        assertEquals(50.0, LangeReihen.real(r, preise, m(2000, 1)).werte[0]!!, 1e-12)
        // Gold real aus der Datei gegen die Quellwerte: Dezember 1974 184 nach 107, Preisindex 51,9 nach 46,2
        val gold = LangeReihen.jahresrenditen(LangeReihen.real(datei.reihen.getValue(Reihe.GOLD), datei.reihen.getValue(Reihe.PREISE)))
        assertEquals((184.0 / 107.0) / (51.9 / 46.2) - 1.0, gold.getValue(1974), 1e-12)
        assertEquals(-0.30034406390888235, gold.getValue(1981), 1e-9)
        assertEquals(1961, gold.keys.first())
    }

    @Test fun jahresrenditenBrauchenBeideDezember() {
        val w = ArrayList<Double?>()
        repeat(12) { w += 100.0 }
        w += 110.0
        repeat(12) { w += null }
        repeat(11) { w += 1.0 }
        w += 121.0
        val r = Monatsreihe(m(2000, 12), w)
        val j = LangeReihen.jahresrenditen(r)
        assertEquals(setOf(2001), j.keys)
        assertEquals(0.10, j.getValue(2001), 1e-12)
    }

    // ------------------------------------------------------------ Tage und Monate

    @Test fun monatUndLetzterTag() {
        assertEquals(m(1970, 1), LangeReihen.monatVon(0))
        assertEquals(m(1969, 12), LangeReihen.monatVon(-1))
        assertEquals(m(2004, 11), LangeReihen.monatVon(12740))
        assertEquals(m(2004, 11), LangeReihen.monatVon(12752))
        assertEquals(m(2004, 12), LangeReihen.monatVon(12753))
        assertEquals(m(1960, 2), LangeReihen.monatVon(-3594))
        assertEquals(-3594L, LangeReihen.letzterTag(m(1960, 2)))
        assertEquals(12752L, LangeReihen.letzterTag(m(2004, 11)))
        assertEquals(30L, LangeReihen.letzterTag(m(1970, 1)))
        assertEquals(-1L, LangeReihen.letzterTag(m(1969, 12)))
        // Jeder Tag ueber 200 Jahre liegt im Monat, dessen letzter Tag nicht vor ihm liegt
        var tag = LangeReihen.letzterTag(m(1869, 12)) + 1
        for (monat in m(1870, 1)..m(2070, 12)) {
            val ende = LangeReihen.letzterTag(monat)
            assertTrue((ende - tag) in 27L..30L)
            assertEquals(monat, LangeReihen.monatVon(tag))
            assertEquals(monat, LangeReihen.monatVon(ende))
            tag = ende + 1
        }
        assertEquals(1999, LangeReihen.jahr(m(1999, 12)))
        assertEquals(2000, LangeReihen.jahr(m(2000, 1)))
    }

    // ------------------------------------------------------------ Verkettung

    @Test fun verkettungLegtAmErstenGemeinsamenMonatsendeAn() {
        // Lange Reihe Januar bis November 2004: 100 bis 110; echte Kurse ab 18.11.2004
        val lang = Monatsreihe(m(2004, 1), (0..10).map { 100.0 + it })
        val tage = longArrayOf(12740, 12752, 12753, 12754)
        val kurse = doubleArrayOf(44.0, 45.0, 46.0, 47.0)
        val v = LangeReihen.verketten(lang, tage, kurse)!!
        assertEquals(45.0 / 110.0, v.faktor, 1e-12)
        assertEquals(12752L, v.echtAb)
        // Zehn Monate Vorlauf, dann die echten Kurse ab dem Anlegetag; der Kurs vom 18.11. entfaellt
        assertEquals(13, v.tage.size)
        assertEquals(10, v.vorlauf)
        assertEquals(12448L, v.tage[0])
        assertEquals(100.0 * 45.0 / 110.0, v.werte[0], 1e-12)
        assertEquals(LangeReihen.letzterTag(m(2004, 10)), v.tage[9])
        assertEquals(109.0 * 45.0 / 110.0, v.werte[9], 1e-12)
        assertEquals(listOf(45.0, 46.0, 47.0), v.werte.toList().subList(10, 13))
        assertFalse(v.echt(9))
        assertTrue(v.echt(10))
        assertTrue(v.tage.toList().zipWithNext().all { (a, b) -> a < b })
        // Renditen der langen Reihe bleiben erhalten
        assertEquals(101.0 / 100.0, v.werte[1] / v.werte[0], 1e-12)
    }

    @Test fun verkettungOhneLueckeUndOhneErfundeneMonate() {
        // Luecke im Maerz: nur die Monate nach der Luecke kommen in den Vorlauf
        val lang = Monatsreihe(m(2004, 1), listOf(1.0, 2.0, null, 4.0, 5.0, 6.0))
        val tage = longArrayOf(LangeReihen.letzterTag(m(2004, 6)), LangeReihen.letzterTag(m(2004, 6)) + 1)
        val v = LangeReihen.verketten(lang, tage, doubleArrayOf(12.0, 13.0))!!
        assertEquals(2, v.vorlauf)
        assertEquals(listOf(8.0, 10.0, 12.0, 13.0), v.werte.toList())
        // Erster Monat der Kurse ohne Wert der langen Reihe: der naechste gemeinsame Monat zaehlt
        val spaeter = LangeReihen.verketten(
            Monatsreihe(m(2004, 1), listOf(1.0, null, 3.0)), longArrayOf(LangeReihen.letzterTag(m(2004, 2)), LangeReihen.letzterTag(m(2004, 3))), doubleArrayOf(7.0, 9.0),
        )!!
        assertEquals(3.0, spaeter.faktor, 1e-12)
        assertEquals(0, spaeter.vorlauf)
        assertEquals(listOf(9.0), spaeter.werte.toList())
        // Kein gemeinsamer Monat, leere oder ungleich lange Eingaben
        assertNull(LangeReihen.verketten(lang, longArrayOf(LangeReihen.letzterTag(m(2010, 1))), doubleArrayOf(1.0)))
        assertNull(LangeReihen.verketten(lang, longArrayOf(), doubleArrayOf()))
        assertNull(LangeReihen.verketten(lang, longArrayOf(1, 2), doubleArrayOf(1.0)))
    }

    @Test fun verkettungMitDerGoldreihe() {
        // Echte Kurse ab November 2004 (erfundene Werte): Vorlauf reicht bis Januar 1960
        val gold = LangeReihen.verlauf(datei, Klasse.GOLD)!!
        val v = LangeReihen.verketten(gold, longArrayOf(12740, 12752, 12753), doubleArrayOf(44.0, 45.0, 44.5))!!
        assertEquals(m(2004, 11) - m(1960, 1), v.vorlauf)
        assertEquals(LangeReihen.letzterTag(m(1960, 1)), v.tage[0])
        assertEquals(35.0 * 45.0 / gold.wert(m(2004, 11))!!, v.werte[0], 1e-12)
    }

    // ------------------------------------------------------------ Mischungen

    private fun jahre(von: Int, vararg r: Double?) = LangeReihen.Jahre(von, r.toList())

    @Test fun mischungMitFestenAnteilen() {
        val anlagen = mapOf(
            Anlage.AKTIEN_USA to jahre(2000, 0.10, -0.50, 1.00, 0.20),
            Anlage.ANLEIHEN to jahre(2001, 0.00, 0.10, 0.00, 0.05),
        )
        // Nur Aktien: 1 -> 1,1 -> 0,55 -> 1,1 -> 1,32
        val a = LangeReihen.mischung(anlagen, mapOf(Anlage.AKTIEN_USA to 1.0))!!
        assertEquals(2000, a.von)
        assertEquals(2003, a.bis)
        assertEquals(listOf(1.0, 1.1, 0.55, 1.1, 1.32), a.verlauf.map { Math.round(it * 1e9) / 1e9 })
        assertEquals((1.32.pow(0.25) - 1.0) * 100.0, a.rendite, 1e-9)
        val r = a.rueckgang!!
        assertEquals(-50.0, r.tiefe, 1e-9)
        assertEquals(2000, r.hoch)
        assertEquals(2001, r.tief)
        assertEquals(2002, r.erholt)
        assertEquals(2, r.jahreBisErholt)
        // Halb und halb, nur die gemeinsamen Jahre 2001 bis 2003; Anteile werden auf Summe 1 gebracht
        val h = LangeReihen.mischung(anlagen, mapOf(Anlage.AKTIEN_USA to 2.0, Anlage.ANLEIHEN to 2.0, Anlage.GOLD to 0.0))!!
        assertEquals(2001, h.von)
        assertEquals(2003, h.bis)
        assertEquals(-0.25, h.renditen[0], 1e-12)
        assertEquals(0.55, h.renditen[1], 1e-12)
        assertEquals(0.10, h.renditen[2], 1e-12)
        // Fehlende Anlage, keine Anteile
        assertNull(LangeReihen.mischung(anlagen, mapOf(Anlage.GOLD to 1.0)))
        assertNull(LangeReihen.mischung(anlagen, emptyMap()))
    }

    @Test fun mischungNimmtDieLetzteFolgeOhneLuecke() {
        val anlagen = mapOf(Anlage.AKTIEN_USA to jahre(1990, 0.5, null, 0.1, 0.2, null))
        val e = LangeReihen.mischung(anlagen, mapOf(Anlage.AKTIEN_USA to 1.0))!!
        assertEquals(1992, e.von)
        assertEquals(1993, e.bis)
        assertNull(e.rueckgang)
    }

    @Test fun rueckgangOhneErholung() {
        val r = LangeReihen.rueckgang(doubleArrayOf(1.0, 2.0, 1.5, 1.0, 1.9), 1950)!!
        assertEquals(-50.0, r.tiefe, 1e-9)
        assertEquals(1950, r.hoch)
        assertEquals(1952, r.tief)
        assertNull(r.erholt)
        assertNull(r.jahreBisErholt)
        assertNull(LangeReihen.rueckgang(doubleArrayOf(1.0, 1.0, 2.0), 1950))
    }

    @Test fun haltedauerVerteilung() {
        // Jahresrenditen +100 %, -50 %, +100 %, -50 %, +300 %
        val verlauf = doubleArrayOf(1.0, 2.0, 1.0, 2.0, 1.0, 4.0)
        val eins = LangeReihen.haltedauer(verlauf, 1)!!
        assertEquals(5, eins.faelle)
        assertEquals(-50.0, eins.min, 1e-9)
        assertEquals(-50.0, eins.q1, 1e-9)
        assertEquals(100.0, eins.median, 1e-9)
        assertEquals(100.0, eins.q3, 1e-9)
        assertEquals(300.0, eins.max, 1e-9)
        assertEquals(0.4, eins.verlust, 1e-12)
        // Zwei Jahre: 0 %, 0 %, 0 %, +100 % gesamt
        val zwei = LangeReihen.haltedauer(verlauf, 2)!!
        assertEquals(4, zwei.faelle)
        assertEquals(0.0, zwei.min, 1e-9)
        assertEquals(0.0, zwei.median, 1e-9)
        assertEquals((2.0.pow(0.5) - 1.0) * 100.0, zwei.max, 1e-9)
        assertEquals((2.0.pow(0.5) - 1.0) * 100.0 * 0.25, zwei.q3, 1e-9)
        assertEquals(0.0, zwei.verlust, 0.0)
        assertEquals(1, LangeReihen.haltedauer(verlauf, 5)!!.faelle)
        assertNull(LangeReihen.haltedauer(verlauf, 6))
        assertNull(LangeReihen.haltedauer(verlauf, 0))
    }

    @Test fun anlagenAusDenDateien() {
        val a = LangeReihen.anlagen(jst, datei)
        assertEquals(Anlage.entries.toSet() - Anlage.AKTIEN_KLEIN_WERT, a.keys)
        // Nur Aktien USA: dieselbe Rendite wie die Laenderhistorie ueber dieselben Jahre
        val usa = LangeReihen.mischung(a, mapOf(Anlage.AKTIEN_USA to 1.0))!!
        assertEquals(2020, usa.bis)
        assertTrue(usa.von <= 1872)
        val vergleich = HistorieJst.spanne(jst.laender.getValue("USA"), HistorieJst.Art.AKTIEN, usa.von, usa.bis)!!
        assertEquals(vergleich.rendite * 100.0, usa.rendite, 1e-9)
        assertEquals(usa.bis - usa.von + 1, vergleich.jahre)
        assertEquals(LangeReihen.HALTEDAUERN, usa.haltedauern.map { it.jahre })
        assertEquals(usa.renditen.size - 29, usa.haltedauern.last().faelle)
        val r = usa.rueckgang!!
        assertTrue(r.tiefe < -30.0 && r.hoch < r.tief && r.erholt!! > r.tief)
        // Mit Gold beginnt die Rechnung 1961 (Dezember 1960 ist der erste Dezember der Goldreihe)
        val gold = LangeReihen.mischung(a, LangeReihen.VORGABEN.first { it.name == "VIER" }.anteile(Anlage.AKTIEN_USA))!!
        assertEquals(1961, gold.von)
        assertEquals(2020, gold.bis)
        assertEquals(31, gold.haltedauern.first { it.jahre == 30 }.faelle)
        // Welt ohne USA: Mittel der uebrigen Laender, in den ersten Jahren zu wenige
        val welt = a.getValue(Anlage.AKTIEN_WELT)
        val uebrige = jst.laender.values.filter { it.a3 != "USA" && it.reihen[HistorieJst.Art.AKTIEN] != null }
        val werte2000 = uebrige.mapNotNull { it.reihen.getValue(HistorieJst.Art.AKTIEN).getOrNull(2000 - it.von) }
        assertTrue(werte2000.size >= LangeReihen.MIN_LAENDER)
        assertEquals(werte2000.average(), welt.wert(2000)!!, 1e-12)
        // Ohne die Monatsdatei fehlt nur Gold
        assertEquals(Anlage.entries.toSet() - Anlage.GOLD - Anlage.ROHSTOFFE - Anlage.AKTIEN_KLEIN_WERT, LangeReihen.anlagen(jst, null).keys)
    }

    @Test fun vorgabenSindVollstaendig() {
        assertEquals(8, LangeReihen.VORGABEN.size)
        for (v in LangeReihen.VORGABEN) {
            assertEquals(v.name, 100.0, v.prozent.values.sum(), 1e-9)
            assertEquals(1.0, v.anteile(Anlage.AKTIEN_WELT).values.sum(), 1e-12)
            // Small-Cap-Value kommt per Abruf, ohne ihn rechnet nur der Rest
            if (LangeReihen.Teil.KLEIN_WERT !in v.prozent) assertNotNull(v.name, LangeReihen.mischung(LangeReihen.anlagen(jst, datei), v.anteile(Anlage.AKTIEN_WELT)))
        }
    }

    // ------------------------------------------------------------ Texte und Liste

    @Test fun texteInBeidenSprachen() {
        assertEquals(LangeSichtTexte.schluessel(true), LangeSichtTexte.schluessel(false))
        for (k in LangeSichtTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = LangeSichtTexte.text(d, k, listOf("1", "2", "3", "4"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{') || t.contains('?') || t.contains('–') || t.contains('—') || t.contains('…'))
        }
        for (a in Anlage.entries) assertTrue(a.name, "n." + a.name in LangeSichtTexte.schluessel(true))
        assertEquals("real pro Jahr, 1871 bis 2020", LangeSichtTexte.text(true, "kern", listOf("1871", "2020")))
        assertEquals("1929 to 1932, regained 1936 after 7 years", LangeSichtTexte.text(false, "rueckgang.erholt", listOf("1929", "1932", "1936", "7 years")))
        assertTrue(LangeSichtTexte.text(true, "hinweis", listOf("15")).startsWith("Rückrechnung, historisch und hypothetisch."))
        assertTrue(LangeSichtTexte.text(false, "hinweis", listOf("15")).startsWith("Back-calculation, historical and hypothetical."))
    }

    @Test fun werkzeugInDerListe() {
        val name = PortfolioTool.LANGSICHT.name
        assertEquals(1, WERKZEUG_GRUPPEN.count { name in it.second })
        assertTrue(werkzeugFormen(name).isNotEmpty())
        assertEquals("Börsengeschichte", De.ptTitle(name))
        assertEquals("Market history", En.ptTitle(name))
        for (s in listOf(De, En)) {
            assertTrue(s.ptSubtitle(name).isNotBlank())
            assertFalse(s.ptSubtitle(name).contains('?'))
        }
    }

    // ------------------------------------------------------------ All Weather und Golden Butterfly

    @Test fun allWeatherUndGoldenButterflyGewichte() {
        val aw = LangeReihen.VORGABEN.first { it.name == "ALLWEATHER" }
        val gb = LangeReihen.VORGABEN.first { it.name == "GOLDEN" }
        for (v in listOf(aw, gb)) assertEquals(v.name, 1.0, v.anteile(Anlage.AKTIEN_USA).values.sum(), 1e-12)
        // 30 Aktien, 40 lange und 15 mittlere Anleihen (je zur Haelfte lang und Geldmarkt), je 7,5 Gold und Rohstoffe
        val a = aw.anteile(Anlage.AKTIEN_USA)
        assertEquals(0.30, a.getValue(Anlage.AKTIEN_USA), 1e-12)
        assertEquals(0.40 + 0.075, a.getValue(Anlage.ANLEIHEN), 1e-12)
        assertEquals(0.075, a.getValue(Anlage.GELDMARKT), 1e-12)
        assertEquals(0.075, a.getValue(Anlage.GOLD), 1e-12)
        assertEquals(0.075, a.getValue(Anlage.ROHSTOFFE), 1e-12)
        assertEquals(listOf("ROH", "MITTEL"), aw.naeherungen)
        // Golden Butterfly: fuenf gleiche Teile, kurze Anleihen als Geldmarkt
        val g = gb.anteile(Anlage.AKTIEN_USA)
        assertEquals(setOf(Anlage.AKTIEN_USA, Anlage.AKTIEN_KLEIN_WERT, Anlage.ANLEIHEN, Anlage.GELDMARKT, Anlage.GOLD), g.keys)
        assertTrue(g.values.all { Math.abs(it - 0.2) < 1e-12 })
        assertEquals(listOf("KURZ", "KLEIN"), gb.naeherungen)
    }

    @Test fun rohstoffeSindPreisPlusZins() {
        val ab = m(2000, 1)
        val d = LangeReihen.Datei(
            "2026-01-01",
            mapOf(Reihe.ROHSTOFFE to Monatsreihe(ab, listOf(100.0, 110.0, 110.0)), Reihe.SCHATZWECHSEL to Monatsreihe(ab, listOf(2.0, 2.0, 2.0))),
            emptyMap(),
        )
        val r = LangeReihen.rohstoffeMitZins(d)!!
        val z = LangeReihen.wechselMonat(2.0)
        assertEquals(ab, r.ab)
        assertEquals(1.0, r.werte[0]!!, 1e-12)
        assertEquals(1.0 + 0.10 + z, r.werte[1]!!, 1e-12)
        // Unveraenderter Preis: nur der Zins
        assertEquals((1.0 + 0.10 + z) * (1.0 + z), r.werte[2]!!, 1e-12)
        // Ohne Zinsreihe keine Naeherung
        assertNull(LangeReihen.rohstoffeMitZins(LangeReihen.Datei("2026-01-01", mapOf(Reihe.ROHSTOFFE to Monatsreihe(ab, listOf(1.0, 2.0))), emptyMap())))
    }

    @Test fun kleinWertAusDerFrenchDatei() {
        val csv = """
            The portfolios include utilities.

              Average Value Weighted Returns -- Monthly
            ,SMALL LoBM,ME1 BM2,SMALL HiBM,BIG LoBM,ME2 BM2,BIG HiBM
            192607,  1.0,  2.0,  3.0,  4.0,  5.0,  6.0

              Average Equal Weighted Returns -- Monthly
            ,SMALL LoBM,ME1 BM2,SMALL HiBM,BIG LoBM,ME2 BM2,BIG HiBM
            192607,  9.0,  9.0,  9.0,  9.0,  9.0,  9.0

              Average Value Weighted Returns -- Annual
            ,SMALL LoBM,ME1 BM2,SMALL HiBM,BIG LoBM,ME2 BM2,BIG HiBM
              1927,  30.9145,  26.1386,  34.9750,  44.3751,  23.2026,  33.5957
              1928,  39.5399,  40.8589,  41.4624,  45.9858,  31.1411,  33.5404
              1929, -45.1676, -32.4379, -99.99,  -19.0256,   0.0345,  -3.5265

              Average Equal Weighted Returns -- Annual
            ,SMALL LoBM,ME1 BM2,SMALL HiBM,BIG LoBM,ME2 BM2,BIG HiBM
              1927,  25.0,  27.0,  31.0,  38.0,  26.0,  41.0
        """.trimIndent()
        val j = LangeReihen.kleinWertLesen(csv)
        assertEquals(listOf(1927, 1928), j.keys.toList())
        assertEquals(0.349750, j.getValue(1927), 1e-12)
        assertEquals(0.414624, j.getValue(1928), 1e-12)
        // Real: durch die Preisaenderung Dezember gegen Dezember
        val preise = Monatsreihe(m(1926, 12), List(12) { 10.0 } + 11.0)
        val real = LangeReihen.kleinWertReal(mapOf(1927 to 0.21), preise)
        assertEquals(1.21 / 1.1 - 1.0, real.getValue(1927), 1e-12)
        assertTrue(LangeReihen.kleinWertReal(mapOf(1927 to 0.21), Monatsreihe(m(1928, 1), listOf(1.0))).isEmpty())
    }

    @Test fun mischungenMitRohstoffenUndKleinWert() {
        // Bundle: Rohstoffe vorhanden, Small-Cap-Value nur mit Daten
        val ohne = LangeReihen.anlagen(jst, datei)
        assertTrue(Anlage.ROHSTOFFE in ohne)
        assertFalse(Anlage.AKTIEN_KLEIN_WERT in ohne)
        val aw = LangeReihen.VORGABEN.first { it.name == "ALLWEATHER" }
        val e = LangeReihen.mischung(ohne, aw.anteile(Anlage.AKTIEN_USA))!!
        // Beginn 1961 (erster Dezember der Goldreihe und Dezember 1960 der Rohstoffe), Ende mit der Laenderhistorie
        assertEquals(1961, e.von)
        assertEquals(2020, e.bis)
        // Mit geladenen Daten gibt es Small-Cap-Value ab 1927, real
        val nominal = (1927..2025).associateWith { 0.10 }
        val mit = LangeReihen.anlagen(jst, datei, nominal)
        assertTrue(Anlage.AKTIEN_KLEIN_WERT in mit)
        assertEquals(1927, mit.getValue(Anlage.AKTIEN_KLEIN_WERT).von)
        val gb = LangeReihen.mischung(mit, LangeReihen.VORGABEN.first { it.name == "GOLDEN" }.anteile(Anlage.AKTIEN_USA))!!
        assertEquals(1961, gb.von)
        assertEquals(2020, gb.bis)
        // Ohne die Daten rechnet Golden Butterfly nicht
        assertNull(LangeReihen.mischung(ohne, LangeReihen.VORGABEN.first { it.name == "GOLDEN" }.anteile(Anlage.AKTIEN_USA)))
    }
}
