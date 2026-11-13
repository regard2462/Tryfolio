package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.FinderRueckblickMessung.Stichtag
import de.troetenrepublik.app.engine.FinderRueckblickMessung.Wert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 0.77 F1: Der Rueckblick des Aktienfinders haelt die Gewichtung der Messung (alt oder aktuell) auseinander.
// Alte Dateien ohne oder mit alten Gewichten bleiben ladbar, gelten aber als fruehere Gewichtung; die Neurechnung aus
// den Punkten je Bereich mit neuen Gewichten ordnet anders als mit alten.
class Fassung077F1RueckblickGewichteTest {

    private val alt = mapOf(Bereich.BEWERTUNG to 30.0, Bereich.QUALITAET to 20.0, Bereich.ANALYSTEN to 10.0, Bereich.TREND to 20.0)

    private fun datei(gewichte: String) =
        """{"stand":"2026-11-01","von":"2012-01-31","bis":"2025-09-30","stichtage":165,"werte":430$gewichte,
            "horizonte":[{"monate":12,"stichtage":165,"top10":0.1,"top20":0.12,"msci":0.1,"spx":0.11,"universum":0.1,
            "mehr":0.02,"mehrSe":0.01,"treffer":0.6,"ic":0.01,"icSe":0.01,"spanne":0.0,"spanneSe":0.0}]}"""

    @Test fun alteDateiMitAltenGewichtenLaedtUndGiltAlsFrueher() {
        val d = FinderRueckblickMessung.lesen(datei(""","gewichte":{"BEWERTUNG":30.0,"QUALITAET":20.0,"ANALYSTEN":10.0,"TREND":20.0}"""))
        assertNotNull(d)
        assertEquals(165, d!!.stichtage)
        assertNotNull(d.horizont(12))
        assertFalse(d.passtZuGewichten(FinderRueckblick.aktuelleGewichte()))
        assertTrue(d.passtZuGewichten(mapOf("BEWERTUNG" to 30.0, "QUALITAET" to 20.0, "ANALYSTEN" to 10.0, "TREND" to 20.0)))
    }

    @Test fun dateiOhneGewichteLaedtUndGiltAlsFrueher() {
        val d = FinderRueckblickMessung.lesen(datei(""))
        assertNotNull(d)
        assertTrue(d!!.gewichte.isEmpty())
        assertFalse(d.passtZuGewichten(FinderRueckblick.aktuelleGewichte()))
    }

    @Test fun dateiMitAktuellenGewichtenGiltAlsAktuell() {
        val aktuell = FinderRueckblick.aktuelleGewichte()
        assertEquals(mapOf("BEWERTUNG" to 25.0, "QUALITAET" to 15.0, "ANALYSTEN" to 10.0, "TREND" to 15.0), aktuell)
        val d = FinderRueckblickMessung.lesen(datei(""","gewichte":{"BEWERTUNG":25.0,"QUALITAET":15.0,"ANALYSTEN":10.0,"TREND":15.0}"""))!!
        assertTrue(d.passtZuGewichten(aktuell))
        // nur das Verhaeltnis zaehlt
        assertTrue(FinderRueckblickMessung.gewichteGleich(mapOf("BEWERTUNG" to 50.0, "QUALITAET" to 30.0, "ANALYSTEN" to 20.0, "TREND" to 30.0), aktuell))
        assertFalse(FinderRueckblickMessung.gewichteGleich(mapOf("BEWERTUNG" to 25.0, "QUALITAET" to 15.0, "ANALYSTEN" to 15.0, "TREND" to 10.0), aktuell))
    }

    @Test fun gebuendelteDateiLaedtUndPasstNochNichtZurAktuellenGewichtung() {
        val f = File("src/main/assets/indikatoren/finder_rueckblick.json")
        if (!f.isFile) return
        val d = FinderRueckblickMessung.lesen(f.readText())
        assertNotNull(d)
        assertTrue(d!!.stichtage > 0)
        // Bis die Messung neu gebaut ist, stammt sie von der Gewichtung vor 0.77
        if (d.gewichte["BEWERTUNG"] == 30.0) assertFalse(d.passtZuGewichten(FinderRueckblick.aktuelleGewichte()))
    }

    // A: stark in Qualitaet und Trend, schwach bei Analysten; B umgekehrt. Die alte Gewichtung stellt A vorn, die
    // neue (Analysten mit mehr Anteil an der Summe) B.
    private val stichtag = Stichtag(
        tag = 20_000L,
        werte = listOf(
            Wert("A", mapOf(Bereich.BEWERTUNG to 50.0, Bereich.QUALITAET to 70.0, Bereich.ANALYSTEN to 10.0, Bereich.TREND to 70.0), mapOf(3 to 0.10)),
            Wert("B", mapOf(Bereich.BEWERTUNG to 50.0, Bereich.QUALITAET to 50.0, Bereich.ANALYSTEN to 80.0, Bereich.TREND to 50.0), mapOf(3 to 0.02)),
        ),
        msci = mapOf(3 to 0.03), spx = mapOf(3 to 0.04), soll = 2,
    )

    @Test fun neurechnungMitNeuenGewichtenStimmtAnFestemBeispiel() {
        val neu = FinderRueckblick.testGewichte()
        assertEquals(55.0, Aktienfinder.gesamt(stichtag.werte[0].bereiche, alt), 1e-9)
        assertEquals(53.75, Aktienfinder.gesamt(stichtag.werte[1].bereiche, alt), 1e-9)
        assertEquals(3450.0 / 65.0, Aktienfinder.gesamt(stichtag.werte[0].bereiche, neu), 1e-9)
        assertEquals(3550.0 / 65.0, Aktienfinder.gesamt(stichtag.werte[1].bereiche, neu), 1e-9)
        assertEquals(listOf("A", "B"), FinderRueckblickMessung.sortiert(stichtag, alt).map { it.symbol })
        assertEquals(listOf("B", "A"), FinderRueckblickMessung.sortiert(stichtag, neu).map { it.symbol })
        // Beste Aktie: alte Gewichtung bringt 10 %, neue 2 %; je Gewichtung eine eigene Zahl
        assertEquals(0.10, FinderRueckblickMessung.topRendite(stichtag, alt, 1, 3)!!, 1e-12)
        assertEquals(0.02, FinderRueckblickMessung.topRendite(stichtag, neu, 1, 3)!!, 1e-12)
    }

    @Test fun zusammenfassungTraegtDieGewichteDerRechnung() {
        val st = (0 until 3).map { stichtag.copy(tag = 20_000L + it * 31) }
        val d = FinderRueckblickMessung.zusammenfassen(st, FinderRueckblick.testGewichte(), "2026-11-11", false)
        assertEquals(FinderRueckblick.aktuelleGewichte(), d.gewichte)
        assertTrue(d.passtZuGewichten(FinderRueckblick.aktuelleGewichte()))
    }
}
