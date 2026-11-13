package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Rezessionsbaender: Spannen aus der NBER-Spalte der NY-Fed-Datei, Zuschnitt auf einen Chart, Schwelle zehn Jahre,
// Leser der Zeilen aus allmonth.xls.
class Fassung078H2RezessionsbaenderTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private fun ms(s: String) = LocalDate.parse(s).toEpochDay() * 86_400_000L

    // Gekuerzter Ausschnitt der Datei in derselben Form (Monate 1959-01 bis 1961-06)
    private val ausschnitt = """{"stand":"1961-06","von":"1959-01","bisNber":"1961-06",
        |"p":[null,null,null,null,null,null,null,null,null,null,null,null,10.5,9.8,12.0,15.5,20.1,27.8,25.0,22.0,18.0,15.0,12.0,10.0,9.0,8.0,7.5,7.0,6.5,6.0],
        |"nber":[["1960-05","1961-02"]]}""".trimMargin()

    @Test fun ausschnittLesen() {
        val r = RezessionNyFed.lesen(ausschnitt)
        assertNotNull(r)
        r!!
        assertEquals(RezessionNyFed.monat(1959, 1), r.von)
        assertEquals(30, r.p.size)
        assertNull(r.wert(RezessionNyFed.monat(1959, 12)))
        assertEquals(10.5, r.wert(RezessionNyFed.monat(1960, 1))!!, 1e-9)
        assertEquals(listOf(RezessionNyFed.monat(1960, 5) to RezessionNyFed.monat(1961, 2)), r.nber)
        // Spanne vom 1. Mai 1960 bis vor den 1. Maerz 1961
        val s = Rezessionen.spannen(r).single()
        assertEquals(ms("1960-05-01"), s.von)
        assertEquals(ms("1961-03-01"), s.bis)
        // Kaputtes oder leeres bleibt draussen
        assertNull(RezessionNyFed.lesen("{}"))
        assertNull(RezessionNyFed.lesen("""{"stand":"1961-06","von":"1959-01","bisNber":"1961-06","p":[null,null],"nber":[]}"""))
    }

    @Test fun mitgelieferteDateiNenntAlleRezessionenSeit1960() {
        val r = RezessionNyFed.lesen(asset(RezessionNyFed.DATEI).readText())
        assertNotNull(r)
        r!!
        assertEquals(RezessionNyFed.monat(1959, 1), r.von)
        val texte = r.nber.map { (a, b) -> "%d-%02d..%d-%02d".format(RezessionNyFed.jahr(a), RezessionNyFed.monatImJahr(a), RezessionNyFed.jahr(b), RezessionNyFed.monatImJahr(b)) }
        // NBER: Beginn im Monat nach dem Hoch, Ende im Monat des Tiefs
        assertEquals(
            listOf(
                "1960-05..1961-02", "1970-01..1970-11", "1973-12..1975-03", "1980-02..1980-07", "1981-08..1982-11",
                "1990-08..1991-03", "2001-04..2001-11", "2008-01..2009-06", "2020-03..2020-04",
            ),
            texte,
        )
        // Die Wahrscheinlichkeit reicht zwoelf Monate ueber die letzte Zinskurve hinaus
        assertEquals(r.stand + RezessionNyFed.VORLAUF, r.bis)
    }

    @Test fun zuschnittUndZehnJahre() {
        val spannen = listOf(Rezessionen.Spanne(ms("2008-01-01"), ms("2009-07-01")), Rezessionen.Spanne(ms("2020-03-01"), ms("2020-05-01")))
        // Ein Chart ab 2009-01: die erste Spanne beginnt am Rand
        val im = Rezessionen.imBereich(ms("2009-01-01"), ms("2026-11-01"), spannen)
        assertEquals(2, im.size)
        assertEquals(ms("2009-01-01"), im[0].von)
        assertEquals(ms("2009-07-01"), im[0].bis)
        // Ausserhalb nichts
        assertTrue(Rezessionen.imBereich(ms("2010-01-01"), ms("2019-12-31"), spannen).isEmpty())
        // Zehn Jahre (mit Spiel fuer einen Abruf „10y“, der an einem Handelstag beginnt), neun nicht
        assertTrue(Rezessionen.lang(ms("2016-11-14"), ms("2026-11-12")))
        assertFalse(Rezessionen.lang(ms("2017-11-12"), ms("2026-11-12")))
    }

    @Test fun zeilenAusAllmonthXls() {
        // Wie XlsLeser.tabelle sie liefert: Monatsende als Tag seit 1970, dann 10 J., 3 M., 3 M. Anleihe, Spread, Rec_prob, NBER_Rec
        fun tag(s: String) = LocalDate.parse(s).toEpochDay()
        val nan = Double.NaN
        val zeilen = ArrayList<Pair<Long, DoubleArray>>()
        var d = LocalDate.of(1959, 1, 31)
        for (i in 0 until 30) {
            val nber = if (i in 16..25) 1.0 else 0.0
            val p = if (i < 12) nan else 0.1 + i / 1000.0
            zeilen += tag(d.toString()) to doubleArrayOf(4.0, 3.0, 3.1, 0.9, p, nber)
            d = d.plusMonths(1).let { it.withDayOfMonth(it.lengthOfMonth()) }
        }
        // Die letzten Monate nur mit Prognose: Zinskurve und NBER fehlen
        for (k in 0 until 3) zeilen[29 - k] = zeilen[29 - k].first to doubleArrayOf(nan, nan, nan, nan, 0.2, nan)
        val r = RezessionNyFed.ausTabelle(zeilen)
        assertNotNull(r)
        r!!
        assertEquals(RezessionNyFed.monat(1959, 1), r.von)
        assertNull(r.wert(RezessionNyFed.monat(1959, 1)))
        assertEquals(11.2, r.wert(RezessionNyFed.monat(1960, 1))!!, 1e-9)
        assertEquals(RezessionNyFed.monat(1961, 3), r.stand)
        assertEquals(RezessionNyFed.monat(1961, 3), r.bisNber)
        assertEquals(listOf(RezessionNyFed.monat(1960, 5) to RezessionNyFed.monat(1961, 2)), r.nber)
        // Eine Luecke in den Monaten macht die Tabelle unbrauchbar
        assertNull(RezessionNyFed.ausTabelle(zeilen.filterIndexed { i, _ -> i != 10 }))
        // Neuere Reihe gewinnt
        val alt = RezessionNyFed.lesen(ausschnitt)!!
        assertEquals(alt.stand, RezessionNyFed.neuere(alt, r)!!.stand)
        assertEquals(alt.stand, RezessionNyFed.neuere(r, alt)!!.stand)
        assertEquals(r.stand, RezessionNyFed.neuere(null, r)!!.stand)
    }
}
