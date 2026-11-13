package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.EtfTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Streuung in der ETF-Durchschau [0.78]: Kennzahlen, Schwellen, Laender, Landrisiko, Waehrungen; erfundene Werte, ohne Netz.
class Fassung078U14EtfTest {

    private fun pos(vararg a: Pair<String, Double>) = a.map { Durchschau.Position(it.first, it.first, it.second) }

    @Test
    fun schwellenDerEinordnung() {
        assertEquals(EtfStreuung.Stufe.BREIT, EtfStreuung.stufe(0.299))
        assertEquals(EtfStreuung.Stufe.KONZENTRIERT, EtfStreuung.stufe(0.30))
        assertEquals(EtfStreuung.Stufe.KONZENTRIERT, EtfStreuung.stufe(0.499))
        assertEquals(EtfStreuung.Stufe.STARK, EtfStreuung.stufe(0.50))
    }

    @Test
    fun kennzahlenMitRest() {
        // Zehn Positionen mit zusammen 43 %: der Rest ist bekannt, Zahl und wirksame Zahl der Positionen nicht
        val p = pos("A" to 0.08, "B" to 0.07, "C" to 0.06, "D" to 0.05, "E" to 0.04, "F" to 0.04, "G" to 0.03, "H" to 0.03, "I" to 0.02, "J" to 0.01)
        val k = EtfStreuung.kennzahlen(p.shuffled(java.util.Random(1)))!!
        assertEquals(10, k.genannt)
        assertEquals(0.43, k.top, 1e-9)
        assertEquals(0.57, k.rest, 1e-9)
        assertEquals("A", k.groesste.symbol)
        assertNull(k.anzahl)
        assertNull(k.wirksam)
        assertEquals(EtfStreuung.Stufe.KONZENTRIERT, k.stufe)
        val leiste = EtfStreuung.leiste(k, p)
        assertEquals(11, leiste.size)
        assertEquals(1.0, leiste.sum(), 1e-9)
        assertEquals(0.08, leiste.first(), 1e-9)
        assertEquals(0.57, leiste.last(), 1e-9)
    }

    @Test
    fun kennzahlenVollstaendig() {
        // Vier gleich grosse Positionen machen den ganzen Fonds aus: wirksame Zahl 4, kein Rest
        val k = EtfStreuung.kennzahlen(pos("A" to 0.25, "B" to 0.25, "C" to 0.25, "D" to 0.25))!!
        assertEquals(4, k.anzahl)
        assertEquals(4.0, k.wirksam!!, 1e-9)
        assertEquals(0.0, k.rest, 1e-9)
        assertEquals(EtfStreuung.Stufe.STARK, k.stufe)
        assertEquals(4, EtfStreuung.leiste(k, pos("A" to 0.25, "B" to 0.25, "C" to 0.25, "D" to 0.25)).size)
        // Ungleiche Gewichte: 1 / (0,7² + 0,3²)
        assertEquals(1.0 / 0.58, EtfStreuung.wirksameZahl(listOf(0.7, 0.3))!!, 1e-9)
        assertNull(EtfStreuung.wirksameZahl(emptyList()))
    }

    @Test
    fun kennzahlenOhneGrundlage() {
        assertNull(EtfStreuung.kennzahlen(emptyList()))
        assertNull(EtfStreuung.kennzahlen(pos("A" to 0.0)))
        // Summe ueber 100 % (gehebelt oder fehlerhaft): keine Aussage
        assertNull(EtfStreuung.kennzahlen(pos("A" to 0.8, "B" to 0.7)))
    }

    @Test
    fun listenlaenge() {
        assertEquals(6, EtfStreuung.zuerst(6))
        assertEquals(5, EtfStreuung.zuerst(7))
        assertEquals(5, EtfStreuung.zuerst(10))
        assertEquals(3, EtfStreuung.zuerst(3))
    }

    @Test
    fun laenderAusIndex() {
        val l = EtfStreuung.laenderAusIndex(mapOf("JP" to 6.0, "US" to 72.0, "GB" to 2.0, "XX" to 0.0))!!
        assertTrue(l.ausIndex)
        assertEquals(listOf("US", "JP", "GB"), l.liste.map { it.a2 })
        assertEquals(0.9, l.liste.first().anteil, 1e-9)
        assertEquals(1.0, l.liste.sumOf { it.anteil }, 1e-9)
        assertNull(EtfStreuung.laenderAusIndex(null))
        assertNull(EtfStreuung.laenderAusIndex(emptyMap()))
    }

    @Test
    fun laenderAusPositionen() {
        assertEquals("US", EtfStreuung.sitz("AAPL", "US"))
        // Ohne Katalog nur mit Boersenendung; fremde Firmen an US-Boersen bleiben ohne Land
        assertEquals("NL", EtfStreuung.sitz("ASML.AS", null))
        assertNull(EtfStreuung.sitz("TSM", null))
        assertNull(EtfStreuung.sitz("TSM", ""))
        val katalog = mapOf("AAPL" to "US", "MSFT" to "US")
        val l = EtfStreuung.laenderAusPositionen(pos("AAPL" to 0.05, "MSFT" to 0.04, "ASML.AS" to 0.03, "TSM" to 0.02)) { s -> EtfStreuung.sitz(s, katalog[s]) }!!
        assertFalse(l.ausIndex)
        assertEquals(listOf("US", "NL"), l.liste.map { it.a2 })
        assertEquals(0.09, l.liste.first().anteil, 1e-9)
        assertEquals(0.12, l.abgedeckt, 1e-9)
        assertNull(EtfStreuung.laenderAusPositionen(pos("TSM" to 0.02)) { s -> EtfStreuung.sitz(s, null) })
    }

    @Test
    fun landrisiko() {
        val l = EtfStreuung.laenderAusIndex(mapOf("CN" to 30.0, "IN" to 20.0, "TW" to 20.0, "KR" to 30.0))!!
        val punkte = mapOf("CN" to 35.0, "IN" to 49.9, "KR" to 75.0)
        val r = EtfStreuung.risiko(l, punkte::get)!!
        // Hoch ist unter 50 Punkten (wie Kernaussagen.stufe); Taiwan ohne Punkte zaehlt nicht als bewertet
        assertEquals(Kernaussagen.Stufe.HOCH, Kernaussagen.stufe(EtfStreuung.RISIKO_UNTER - 0.1))
        assertEquals(Kernaussagen.Stufe.MITTEL, Kernaussagen.stufe(EtfStreuung.RISIKO_UNTER))
        assertEquals(listOf("CN", "IN"), r.laender.map { it.a2 })
        assertEquals(0.5, r.anteil, 1e-9)
        assertEquals(0.8, r.bewertet, 1e-9)
        // Kein Land ueber der Schwelle: Anteil null, aber eine Aussage
        val ruhig = EtfStreuung.risiko(l) { 80.0 }!!
        assertEquals(0.0, ruhig.anteil, 1e-9)
        assertTrue(ruhig.laender.isEmpty())
        // Ohne Punkte keine Aussage
        assertNull(EtfStreuung.risiko(l) { null })
    }

    @Test
    fun waehrungenAusLaendern() {
        val l = EtfStreuung.laenderAusIndex(mapOf("US" to 60.0, "DE" to 15.0, "FR" to 10.0, "JP" to 10.0, "XX" to 5.0))!!
        val w = EtfStreuung.waehrungen(l, DepotStreuung::waehrungVon)
        assertEquals(listOf("USD", "EUR", "JPY"), w.map { it.first })
        assertEquals(0.25, w[1].second, 1e-9)
    }

    @Test
    fun indexDateiTraegtDieRechnung() {
        // Die gebuendelte Laenderverteilung liefert fuer einen Welt-Index Laender, Waehrungen und eine Summe von 1
        val kandidaten = listOf("src/main/assets/", "app/src/main/assets/", "../app/src/main/assets/").map { java.io.File(it + "globus/etf-laender.json") }
        val daten = EtfLaender.lesen(kandidaten.first { it.exists() }.readText())
        val l = EtfStreuung.laenderAusIndex(daten.verteilung("MSCIWORLD"))
        assertNotNull(l)
        assertEquals("US", l!!.liste.first().a2)
        assertEquals(1.0, l.liste.sumOf { it.anteil }, 1e-9)
        assertEquals("USD", EtfStreuung.waehrungen(l, DepotStreuung::waehrungVon).first().first)
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(EtfTexte.schluessel(true), EtfTexte.schluessel(false))
        for (deutsch in listOf(true, false)) for (key in EtfTexte.schluessel(deutsch)) {
            val t = EtfTexte.text(deutsch, key, listOf("1", "2"))
            assertTrue(key, t.isNotBlank())
            assertFalse(key, t.contains("{"))
            assertFalse(key, t.contains("–") || t.contains("—") || t.contains("…"))
        }
        for (s in EtfStreuung.Stufe.entries) assertTrue(EtfTexte.text(true, "stufe." + s.name).isNotBlank())
    }
}
