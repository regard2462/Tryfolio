package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.LangeReihen.Reihe
import de.troetenrepublik.app.engine.WaehrungReihen.Waehrung
import de.troetenrepublik.app.i18n.WaehrungTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Wechselkurse der langen Reihen: Datei gegen Werte der Quelle, Euro aus D-Mark und Euro, Spanne, Texte.
class Fassung109WaehrungTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val datei by lazy { LangeReihen.lesen(asset(LangeReihen.DATEI).readText(Charsets.UTF_8))!! }
    private fun m(jahr: Int, monat: Int) = LangeReihen.monat(jahr, monat)

    private val kopf = """"stand":"2026-11-12","quellen":{"kurse":{"name":"N","url":"https://x","reihe":"R","lizenz":"L"}}"""
    private fun klein(r: String) = LangeReihen.lesen("""{$kopf,"r":{$r}}""")!!

    @Test fun dateiTraegtDieKurseDerQuelle() {
        fun wert(r: Reihe, jahr: Int, monat: Int) = datei.reihen.getValue(r).wert(m(jahr, monat))
        // H.10 der Federal Reserve, Monatsmittel
        assertEquals(358.02, wert(Reihe.KURS_JPY, 1971, 1)!!, 0.0)
        assertEquals(2.4058, wert(Reihe.KURS_GBP, 1971, 1)!!, 0.0)
        assertEquals(1.1591, wert(Reihe.KURS_EUR, 1999, 1)!!, 0.0)
        assertEquals(3.637, wert(Reihe.KURS_DEM, 1971, 1)!!, 0.0)
        assertEquals(1.6698, wert(Reihe.KURS_DEM, 1998, 12)!!, 0.0)
        assertNull(wert(Reihe.KURS_DEM, 1999, 1))
        assertNull(wert(Reihe.KURS_EUR, 1998, 12))
        for (r in listOf(Reihe.KURS_JPY, Reihe.KURS_CHF, Reihe.KURS_CAD, Reihe.KURS_GBP)) {
            assertTrue(r.name, datei.reihen.getValue(r).werte.none { it == null })
            assertEquals(m(1971, 1), datei.reihen.getValue(r).ab)
            assertTrue(r.name, datei.reihen.getValue(r).bis >= m(2026, 9))
        }
        assertEquals("public domain", datei.quelle(Reihe.KURS_JPY)!!.lizenz)
        assertTrue(datei.quelle(Reihe.KURS_EUR)!!.url.startsWith("https://www.federalreserve.gov/"))
    }

    @Test fun dollarInEinerWaehrung() {
        // Quotierung je US-Dollar bleibt, Dollar je Pfund wird umgekehrt
        assertEquals(358.02, WaehrungReihen.dollarIn(datei, Waehrung.JPY)!!.wert(m(1971, 1))!!, 0.0)
        assertEquals(1.0 / 2.4058, WaehrungReihen.dollarIn(datei, Waehrung.GBP)!!.wert(m(1971, 1))!!, 1e-12)
        assertEquals(Waehrung.entries, WaehrungReihen.verfuegbar(datei))
    }

    @Test fun euroAusMarkUndEuro() {
        val e = WaehrungReihen.dollarIn(datei, Waehrung.EUR)!!
        assertEquals(m(1971, 1), e.ab)
        assertEquals(3.637 / WaehrungReihen.MARK_JE_EURO, e.wert(m(1971, 1))!!, 1e-12)
        assertEquals(1.6698 / WaehrungReihen.MARK_JE_EURO, e.wert(m(1998, 12))!!, 1e-12)
        assertEquals(1.0 / 1.1591, e.wert(m(1999, 1))!!, 1e-12)
        assertTrue(e.werte.none { it == null })
        // Der Sprung am Uebergang bleibt klein (kein Bruch von Mark und Euro)
        val a = e.wert(m(1998, 12))!!; val b = e.wert(m(1999, 1))!!
        assertTrue(kotlin.math.abs(b / a - 1.0) < 0.06)
    }

    @Test fun euroNurEinTeilUndLuecken() {
        val nurEuro = klein(""""fx_eur":{"ab":"1999-01","w":[1.25,null,0.5]}""")
        val e = WaehrungReihen.dollarIn(nurEuro, Waehrung.EUR)!!
        assertEquals(listOf(0.8, null, 2.0), e.werte)
        val nurMark = klein(""""fx_dem":{"ab":"1998-11","w":[2.0,1.95583]}""")
        val d = WaehrungReihen.dollarIn(nurMark, Waehrung.EUR)!!
        assertEquals(2.0 / 1.95583, d.werte[0]!!, 1e-12)
        assertEquals(1.0, d.werte[1]!!, 1e-12)
        assertNull(WaehrungReihen.dollarIn(nurMark, Waehrung.JPY))
        assertEquals(listOf(Waehrung.EUR), WaehrungReihen.verfuegbar(nurMark))
        assertTrue(WaehrungReihen.verfuegbar(klein(""""fx_jpy":{"ab":"1999-01","w":[null,null]}""")).isEmpty())
        // Null und negative Werte ergeben Luecken statt Unendlich
        assertEquals(listOf(null, 0.5), WaehrungReihen.dollarIn(klein(""""fx_gbp":{"ab":"1999-01","w":[0,2]}"""), Waehrung.GBP)!!.werte)
    }

    @Test fun spanneMitHochTiefUndAenderung() {
        val r = LangeReihen.Monatsreihe(m(2000, 1), listOf(100.0, 120.0, null, 60.0, 90.0))
        val s = WaehrungReihen.spanne(r)!!
        assertEquals(m(2000, 1), s.erster.monat)
        assertEquals(m(2000, 5), s.letzter.monat)
        assertEquals(120.0, s.hoch.wert, 0.0)
        assertEquals(m(2000, 2), s.hoch.monat)
        assertEquals(60.0, s.tief.wert, 0.0)
        assertEquals(m(2000, 4), s.tief.monat)
        assertEquals(-10.0, s.aenderung, 1e-12)
        assertNull(WaehrungReihen.spanne(LangeReihen.Monatsreihe(m(2000, 1), listOf(null, 1.0))))
        assertEquals(2000, WaehrungReihen.jahr(m(2000, 12)))
        assertEquals(12, WaehrungReihen.monatImJahr(m(2000, 12)))
        assertEquals(1, WaehrungReihen.monatImJahr(m(2001, 1)))
    }

    @Test fun alteDateiOhneKurseBleibtLesbar() {
        val alt = LangeReihen.lesen("""{"stand":"2026-11-12","quellen":{"gold":{"name":"N","url":"https://x","reihe":"R","lizenz":"L"}},"r":{"gold":{"ab":"1960-01","w":[35,36]}}}""")
        assertNotNull(alt)
        assertTrue(WaehrungReihen.verfuegbar(alt!!).isEmpty())
        assertNull(WaehrungReihen.dollarIn(alt, Waehrung.EUR))
    }

    @Test fun texteInBeidenSprachen() {
        assertEquals(WaehrungTexte.schluessel(true), WaehrungTexte.schluessel(false))
        for (k in WaehrungTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = WaehrungTexte.text(d, k, listOf("1", "2", "3", "4"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{') || t.contains('—') || t.contains('…') || t.contains('?'))
        }
        for (w in Waehrung.entries) assertTrue(w.name in WaehrungTexte.schluessel(true))
        assertEquals("US-Dollar in Euro: 0,89, seit 1971 -22 %", WaehrungTexte.text(true, "kurz", listOf("Euro", "0,89", "1971", "-22 %")))
    }
}
