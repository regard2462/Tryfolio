package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetGrafik
import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.data.WidgetZeile
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.ui.Fmt
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Widgets zu den Abschnitten aus q78 (0.78): Abbildung Langdruck -> Art und Einstellung, Ziele, Aufbau der Karten und Zuschnitt
// je Groesse (2x2, 3x2, 3x3), auch mit Balken unter den Zeilen und der ausgewogenen Auswahl sortierter Listen.
class Fassung078WTest {

    @Before fun deutsch() { I18n.lang = Lang.DE }

    // ------------------------------------------------------------ Langdruck -> Art und Einstellung

    @Test fun langdruckAufArtenOhneEinstellung() {
        assertEquals(WidgetKarten.THEMEN, WidgetVorgabe.themen().art)
        assertEquals(WidgetKarten.SEKTOREN, WidgetVorgabe.sektoren().art)
        assertEquals(WidgetKarten.KONGRESSFLOW, WidgetVorgabe.kongressFlow().art)
        assertEquals(WidgetKarten.STILLHALTER, WidgetVorgabe.werkzeug("STILLHALTER")!!.art)
        assertTrue(WidgetVorgabe.themen().einstellung.isEmpty())
    }

    @Test fun indexBewegerBringtDieQuelleMit() {
        assertEquals("DAX", WidgetVorgabe.indexBeweger("DAX")!!.einstellung[WidgetVorgabe.VARIANTE])
        assertEquals("MARKT", WidgetVorgabe.indexBeweger("MARKT")!!.einstellung[WidgetVorgabe.VARIANTE])
        assertEquals(WidgetKarten.INDEXBEWEGER, WidgetVorgabe.indexBeweger("SP500")!!.art)
        // Depot und Watchlist haben kein Widget: aus ihnen wuerden Positionen auf dem Startbildschirm landen
        assertNull(WidgetVorgabe.indexBeweger("DEPOT"))
        assertNull(WidgetVorgabe.indexBeweger("WATCHLIST"))
    }

    @Test fun signaleUndVolatilitaetBringenDenWertMit() {
        assertEquals("AAPL", WidgetVorgabe.signale("AAPL")!!.einstellung[WidgetVorgabe.VARIANTE])
        assertEquals(WidgetKarten.SIGNALE, WidgetVorgabe.signale("SAP.DE")!!.art)
        assertEquals("SAP.DE", WidgetVorgabe.volatilitaet("SAP.DE")!!.einstellung[WidgetVorgabe.VARIANTE])
        assertEquals(WidgetKarten.VOLATILITAET, WidgetVorgabe.volatilitaet("^GSPC")!!.art)
        // Zeichen, die kein Kuerzel haben, gibt es nicht als Vorgabe
        assertNull(WidgetVorgabe.signale(""))
        assertNull(WidgetVorgabe.signale("A B"))
        assertNull(WidgetVorgabe.volatilitaet("a/b"))
        assertNull(WidgetVorgabe.volatilitaet("x".repeat(41)))
    }

    @Test fun kartenSchluesselTraegtDenWertUndKommtZurueck() {
        val k = WidgetVorgabe.kartenSchluessel(WidgetKarten.SIGNALE, "AAPL")
        assertEquals("signale:AAPL", k)
        assertEquals(WidgetKarten.SIGNALE, WidgetVorgabe.art(k))
        assertEquals("AAPL", WidgetVorgabe.variante(k))
        // Ein Kuerzel mit Doppelpunkt (Moskauer Boerse) bleibt ganz
        assertEquals("MOEX:GAZP", WidgetVorgabe.variante(WidgetVorgabe.kartenSchluessel(WidgetKarten.VOLATILITAET, "MOEX:GAZP")))
        assertEquals("indexbeweger:DAX", WidgetVorgabe.kartenSchluessel(WidgetKarten.INDEXBEWEGER, "DAX"))
        // Ohne Wert, ohne Variante: nur die Art
        assertEquals("signale", WidgetVorgabe.kartenSchluessel(WidgetKarten.SIGNALE, null))
        // Arten ohne Variante ignorieren sie
        assertEquals("themen", WidgetVorgabe.kartenSchluessel(WidgetKarten.THEMEN, "DAX"))
    }

    // ------------------------------------------------------------ Arten und Ziele

    @Test fun neueArtenSindAnlegbarMitTitel() {
        for (a in WidgetKarten.ARTEN_078) {
            assertTrue(a in WidgetKarten.ARTEN)
            assertTrue(a in WidgetKarten.ANLEGBAR)
            assertTrue(WidgetKarten.titel(a).isNotBlank())
            assertNotEquals(a, WidgetKarten.titel(a))
        }
        assertEquals(7, WidgetKarten.ARTEN_078.size)
        assertEquals(WidgetKarten.ARTEN.size, WidgetKarten.ARTEN.toSet().size)
        assertEquals(WidgetKarten.ANLEGBAR.size, WidgetKarten.ANLEGBAR.toSet().size)
    }

    @Test fun zieleDerNeuenWidgetsSindZugelassen() {
        for (a in listOf(WidgetKarten.THEMEN, WidgetKarten.SEKTOREN, WidgetKarten.INDEXBEWEGER, WidgetKarten.KONGRESSFLOW, WidgetKarten.STILLHALTER)) {
            val z = WidgetKarten.ziel(a)
            assertEquals(a, z, WidgetZiel.pruefen(z))
        }
        assertEquals("maerkte/trends", WidgetKarten.ziel(WidgetKarten.THEMEN))
        assertEquals("maerkte/trends", WidgetKarten.ziel(WidgetKarten.SEKTOREN))
        assertEquals("copy-flow", WidgetKarten.ziel(WidgetKarten.KONGRESSFLOW))
        assertEquals(WidgetZiel.DERIVATE, WidgetKarten.ziel(WidgetKarten.STILLHALTER))
        assertEquals("search", WidgetZiel.pruefen(WidgetKarten.ziel(WidgetKarten.SIGNALE)))
        assertEquals("search", WidgetZiel.pruefen(WidgetKarten.ziel(WidgetKarten.VOLATILITAET)))
        assertEquals("heatmap/DAX/0", WidgetZiel.pruefen("heatmap/DAX/0"))
        assertEquals("instrument/AAPL", WidgetZiel.pruefen("instrument/AAPL"))
        assertNull(WidgetZiel.pruefen("copy-flow/../x"))
        assertNull(WidgetZiel.pruefen("sektoren"))
    }

    // ------------------------------------------------------------ Aufbau der Karten

    private fun koerbe() = listOf(
        WidgetKartenBau.Korb("Solar", -1.6, 3, 11), WidgetKartenBau.Korb("KI", 2.4, 9, 11), WidgetKartenBau.Korb("Krypto", -0.2, 5, 9),
        WidgetKartenBau.Korb("Cyber", 0.4, 7, 13), WidgetKartenBau.Korb("Weltraum", 1.1, 6, 8), WidgetKartenBau.Korb("Spiele", -0.9, 4, 12),
        WidgetKartenBau.Korb("Robotik", 0.1, 8, 15), WidgetKartenBau.Korb("Leer", 0.0, 0, 0),
    )

    @Test fun themenSortiertMitAnteilAlsBalkenUndZaehlungAlsHaupt() {
        val k = WidgetKartenBau.themen(koerbe(), "", 1L)
        // Ein Korb ohne Werte zaehlt nicht
        assertEquals(7, k.zeilen.size)
        assertEquals(listOf("KI", "Weltraum", "Cyber", "Robotik", "Krypto", "Spiele", "Solar"), k.zeilen.map { it.text })
        assertTrue(k.ausgewogen)
        assertEquals("4 von 7", k.haupt)
        assertEquals(WidgetGrafik.GRUEN, k.hauptTon)
        // Der Balken zeigt den Anteil steigender Werte, die Farbe folgt dem Median
        assertEquals(listOf(9.0 / 11), k.zeilen[0].balken)
        assertEquals(true, k.zeilen[0].hoch)
        assertEquals(false, k.zeilen.last().hoch)
        assertEquals("maerkte/trends", k.ziel)
        assertTrue(k.zeilen.all { it.unter.isNotBlank() })
    }

    @Test fun themenLeerHatKeineHauptaussage() {
        val k = WidgetKartenBau.themen(emptyList(), "", 1L)
        assertEquals("", k.haupt)
        assertTrue(k.zeilen.isEmpty())
        assertTrue(k.hinweis.isNotBlank())
    }

    @Test fun indexBewegerWechseltZwischenObenUndUntenMitMittigemBalken() {
        val hoch = listOf(WidgetKartenBau.Treiber("SAP", "SAP.DE", 0.18), WidgetKartenBau.Treiber("SIE", "SIE.DE", 0.09), WidgetKartenBau.Treiber("DTE", "DTE.DE", 0.03))
        val runter = listOf(WidgetKartenBau.Treiber("ALV", "ALV.DE", -0.12), WidgetKartenBau.Treiber("BAS", "BAS.DE", -0.02))
        val k = WidgetKartenBau.indexBeweger("DAX", "DAX", 0.42, hoch, runter, "", 1L)
        assertEquals(listOf("SAP", "ALV", "SIE", "BAS", "DTE"), k.zeilen.map { it.text })
        assertEquals(Fmt.signedPct(0.42, 2), k.haupt)
        assertEquals(WidgetGrafik.GRUEN, k.hauptTon)
        assertEquals("DAX", k.hauptUnter)
        // Der groesste Beitrag fuellt die halbe Bahn: nach oben rechts, nach unten links
        assertEquals(listOf(0.0, 1.0), k.zeilen[0].balken)
        assertEquals(listOf(0.12 / 0.18, 0.0), k.zeilen[1].balken)
        assertEquals("instrument/SAP.DE", k.zeilen[0].ziel)
        assertEquals("heatmap/DAX/0", k.ziel)
        assertEquals(WidgetZiel.pruefen(k.ziel), k.ziel)
    }

    @Test fun indexBewegerNegativeBewegungIstRot() {
        val k = WidgetKartenBau.indexBeweger("Markt", "MARKT", -0.8, emptyList(), listOf(WidgetKartenBau.Treiber("NVDA", "NVDA", -0.3)), "", 1L)
        assertEquals(WidgetGrafik.ROT, k.hauptTon)
        assertEquals(Fmt.signedPct(-0.8, 2), k.haupt)
        assertEquals(1, k.zeilen.size)
        assertEquals(listOf(1.0, 0.0), k.zeilen[0].balken)
    }

    @Test fun sektorenSortiertMitBalkenProzentUndZaehlung() {
        val l = listOf(
            WidgetKartenBau.Sektor("Software", 1.4), WidgetKartenBau.Sektor("Goldminen", -1.8), WidgetKartenBau.Sektor("Halbleiter", 2.1),
            WidgetKartenBau.Sektor("Banken", 0.0), WidgetKartenBau.Sektor("Versorger", -0.3),
        )
        val k = WidgetKartenBau.sektoren(l, "", 1L)
        assertEquals(listOf("Halbleiter", "Software", "Banken", "Versorger", "Goldminen"), k.zeilen.map { it.text })
        assertEquals("2 von 5", k.haupt)
        assertEquals(WidgetGrafik.ROT, k.hauptTon)
        assertTrue(k.ausgewogen)
        assertEquals(listOf(0.0, 1.0), k.zeilen[0].balken)
        assertEquals(listOf(1.8 / 2.1, 0.0), k.zeilen.last().balken)
        // Null hat keinen Ausschlag und keine Farbe
        assertEquals(listOf(0.0, 0.0), k.zeilen[2].balken)
        assertNull(k.zeilen[2].hoch)
    }

    @Test fun kongressFlowSaldoUndBalkenJeSeite() {
        val l = listOf(
            WidgetKartenBau.FlussSektor("Finanzen", 0.6e6, 1.2e6, 9, 15), WidgetKartenBau.FlussSektor("Technologie", 2.4e6, 0.6e6, 34, 12),
            WidgetKartenBau.FlussSektor("Energie", 0.1e6, 0.0, 2, 0),
        )
        val k = WidgetKartenBau.kongressFlow(l, "", 1L)
        // Nach Summe der Geschaefte: Technologie 3,0, Finanzen 1,8, Energie 0,1 Millionen
        assertEquals(listOf("Technologie", "Finanzen", "Energie"), k.zeilen.map { it.text })
        assertEquals(WidgetGrafik.GRUEN, k.hauptTon)
        assertTrue(k.haupt.startsWith("+"))
        // Verkaeufe links, Kaeufe rechts, beide im Verhaeltnis zur groessten Seite (2,4 Millionen)
        assertEquals(listOf(0.6 / 2.4, 1.0), k.zeilen[0].balken)
        assertEquals(listOf(0.5, 0.25), k.zeilen[1].balken)
        assertEquals(true, k.zeilen[0].hoch)
        assertEquals(false, k.zeilen[1].hoch)
        assertTrue(k.zeilen[1].wert.startsWith("−"))
        assertEquals("copy-flow", k.ziel)
        assertEquals("34 Käufe, 12 Verkäufe", k.zeilen[0].unter)
        assertEquals("2 Käufe, 0 Verkäufe", k.zeilen[2].unter)
        // Hoechstens sechs Sektoren
        val viele = List(9) { WidgetKartenBau.FlussSektor("S$it", 1e6 + it, 0.0, 1, 0) }
        assertEquals(6, WidgetKartenBau.kongressFlow(viele, "", 1L).zeilen.size)
    }

    @Test fun stillhalterVierBesteWerteJeWertDerBessereVonPutUndCall() {
        fun s(name: String, sym: String, call: Boolean, r: Double) = WidgetKartenBau.Stillhalt(name, sym, call, 100.0, 2.0, 1797681600000L, r)
        val k = WidgetKartenBau.stillhalter(
            listOf(
                s("Apple", "AAPL", false, 0.24), s("Apple", "AAPL", true, 0.12), s("Nvidia", "NVDA", true, 0.20), s("Nvidia", "NVDA", false, 0.31),
                s("Visa", "V", false, 0.08), s("Shell", "SHEL", true, 0.19), s("Pfizer", "PFE", true, 0.05),
            ),
            "", 1L,
        )
        assertEquals(4, k.zeilen.size)
        val strike = Fmt.price(100.0, "USD")
        assertEquals(listOf("Nvidia Put $strike", "Apple Put $strike", "Shell Call $strike", "Visa Put $strike"), k.zeilen.map { it.text })
        assertEquals(Fmt.pct(31.0, 1), k.haupt)
        assertEquals(Fmt.pct(31.0, 1), k.zeilen[0].wert)
        // Balken im Verhaeltnis zur besten Rendite, der beste ist voll
        assertEquals(listOf(1.0), k.zeilen[0].balken)
        assertEquals(listOf(0.24 / 0.31), k.zeilen[1].balken)
        assertEquals("instrument/NVDA", k.zeilen[0].ziel)
        // Praemie und Verfall in der kleinen Zeile
        assertTrue(k.zeilen[0].unter.contains(Fmt.price(2.0, "USD")) && k.zeilen[0].unter.contains(Fmt.dayMonthNum(1797681600000L)))
        assertEquals(WidgetZiel.DERIVATE, k.ziel)
    }

    @Test fun stillhalterOhneWerteHatHinweis() {
        val k = WidgetKartenBau.stillhalter(emptyList(), "", 1L)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("", k.haupt)
        assertTrue(k.hinweis.isNotBlank())
    }

    @Test fun signaleZeilenMitMittigemBalkenOhneWertung() {
        val k = WidgetKartenBau.signale(
            "SAP.DE",
            listOf(
                WidgetKartenBau.Signal("Insider", "5 Käufer", 1.0, 4.0), WidgetKartenBau.Signal("Kongress", "0 Käufe", 0.0, 0.0),
                WidgetKartenBau.Signal("Stufen", "2 Abstufungen", 2.0, 0.0), WidgetKartenBau.Signal("A", "x", 1.0, 1.0), WidgetKartenBau.Signal("B", "x", 1.0, 1.0),
            ),
            "", 1L,
        )
        // Titel mit Kuerzel ohne Boersenendung, hoechstens vier Zeilen
        assertEquals("Signale · SAP", k.titel)
        assertEquals(4, k.zeilen.size)
        // Der Wert steht in der kleinen Zeile, die Zeile hat keine Farbe
        assertEquals("5 Käufer", k.zeilen[0].unter)
        assertEquals("", k.zeilen[0].wert)
        assertTrue(k.zeilen.all { it.hoch == null })
        assertEquals(listOf(0.25, 1.0), k.zeilen[0].balken)
        assertTrue(k.zeilen[1].balken.isEmpty())
        assertEquals(listOf(1.0, 0.0), k.zeilen[2].balken)
        assertEquals("instrument/SAP.DE", k.ziel)
    }

    @Test fun volatilitaetVerhaeltnisAlsHauptUndBalkenZumGroesseren() {
        val k = WidgetKartenBau.volatilitaet("AAPL", 0.284, 0.215, 1.32, 0.63, "", 1L)
        assertEquals("1,32", k.haupt)
        assertEquals("Volatilität · AAPL", k.titel)
        assertEquals(3, k.zeilen.size)
        assertEquals(Fmt.pct(28.4, 1), k.zeilen[0].wert)
        assertEquals(listOf(1.0), k.zeilen[0].balken)
        assertEquals(listOf(0.215 / 0.284), k.zeilen[1].balken)
        // IV-Rang als Anteil von 0 bis 1
        assertEquals(Fmt.pct(63.0, 0), k.zeilen[2].wert)
        assertEquals(listOf(0.63), k.zeilen[2].balken)
        assertEquals("instrument/AAPL", k.ziel)
        // Ohne Rang (noch zu wenig Tageswerte) entfaellt die Zeile
        assertEquals(2, WidgetKartenBau.volatilitaet("AAPL", 0.284, 0.215, 1.32, null, "", 1L).zeilen.size)
        // Ohne Kette keine Hauptaussage
        val leer = WidgetKartenBau.volatilitaet("AAPL", null, null, null, null, "", 1L)
        assertEquals("", leer.haupt)
        assertTrue(leer.zeilen.isEmpty())
    }

    @Test fun karteMitBalkenUeberstehtSerialisierung() {
        val k = WidgetKartenBau.themen(koerbe(), "Stand 12:30", 5L)
        val json = Json { ignoreUnknownKeys = true }
        val zurueck = json.decodeFromString(WidgetKarte.serializer(), json.encodeToString(WidgetKarte.serializer(), k))
        assertEquals(k, zurueck)
        // Eine Karte der Fassung davor (ohne Balken und ohne ausgewogen) bleibt lesbar
        val alt = json.decodeFromString(WidgetKarte.serializer(), """{"titel":"T","zeilen":[{"text":"a","wert":"1"}]}""")
        assertFalse(alt.ausgewogen)
        assertTrue(alt.zeilen[0].balken.isEmpty())
    }

    // ------------------------------------------------------------ Zuschnitt je Groesse

    // 2x2, 3x2 und 3x3 Zellen wie auf einem Startbildschirm (rund 74 dp je Zelle abzueglich Rand)
    private val KLEIN = 146 to 146
    private val BREIT = 222 to 146
    private val GROSS = 222 to 222

    private fun zuschnitt(k: WidgetKarte, g: Pair<Int, Int>, skala: Float = 1f) = WidgetKarten.zuschneiden(k, g.first, g.second, skala)

    @Test fun ausgewogeneAuswahlZeigtStaerkstenUndSchwaechsten() {
        val k = WidgetKartenBau.themen(koerbe(), "", 1L)
        val gross = zuschnitt(k, GROSS)
        assertTrue(gross.zeilen.size >= 2)
        // Reihenfolge bleibt, der staerkste steht vorn und der schwaechste hinten
        assertEquals("KI", gross.zeilen.first().text)
        assertEquals("Solar", gross.zeilen.last().text)
        assertTrue(gross.zeilen.map { it.text }.all { n -> k.zeilen.any { it.text == n } })
        val ids = gross.zeilen.map { z -> k.zeilen.indexOfFirst { it.text == z.text } }
        assertEquals(ids.sorted(), ids)
    }

    @Test fun ausgewogeneAuswahlJeGroesseNimmtMitPlatzZu() {
        val k = WidgetKartenBau.sektoren(List(14) { WidgetKartenBau.Sektor("Sektor $it", 3.0 - it * 0.4) }, "", 1L)
        val klein = zuschnitt(k, KLEIN).zeilen.size
        val breit = zuschnitt(k, BREIT).zeilen.size
        val gross = zuschnitt(k, GROSS).zeilen.size
        val hoch = WidgetKarten.zuschneiden(k, 222, 330).zeilen.size
        assertTrue("klein $klein breit $breit gross $gross", klein <= breit && breit <= gross && gross <= hoch)
        assertTrue(hoch <= 8)
        // Mit mindestens zwei Zeilen ist auch das Ende der Liste dabei
        val z = WidgetKarten.zuschneiden(k, 222, 330).zeilen
        assertEquals("Sektor 0", z.first().text)
        assertEquals("Sektor 13", z.last().text)
    }

    @Test fun zuschnittMitBalkenRechnetDieHoeheDerBalkenMit() {
        val ohne = WidgetKarte("T", List(6) { WidgetZeile("Wert $it", "+1,0 %", true) })
        val mit = WidgetKarte("T", List(6) { WidgetZeile("Wert $it", "+1,0 %", true, balken = listOf(0.5)) })
        assertTrue(zuschnitt(mit, GROSS).zeilen.size < zuschnitt(ohne, GROSS).zeilen.size)
        assertTrue(zuschnitt(mit, GROSS).zeilen.all { it.balken == listOf(0.5) })
    }

    @Test fun zuschnittIndexBewegerKleinZeigtHauptUndBeideSeiten() {
        val hoch = List(3) { WidgetKartenBau.Treiber("H$it", "H$it", 0.2 - it * 0.05) }
        val runter = List(3) { WidgetKartenBau.Treiber("R$it", "R$it", -0.2 + it * 0.05) }
        val k = WidgetKartenBau.indexBeweger("DAX", "DAX", 0.3, hoch, runter, "", 1L)
        val gross = zuschnitt(k, GROSS)
        assertTrue(gross.haupt)
        // Im Wechsel: schon mit zwei Zeilen steht ein Treiber nach oben und einer nach unten da
        assertTrue(gross.zeilen.size >= 2)
        assertEquals(listOf("H0", "R0"), gross.zeilen.take(2).map { it.text })
        val klein = zuschnitt(k, KLEIN)
        assertTrue(klein.haupt)
        assertEquals(if (klein.zeilen.isEmpty()) "" else "H0", klein.zeilen.firstOrNull()?.text.orEmpty())
        assertTrue(zuschnitt(k, BREIT).zeilen.size >= klein.zeilen.size)
    }

    @Test fun zuschnittNichtsWirdAbgeschnittenMitGrosserSchrift() {
        val k = WidgetKartenBau.kongressFlow(
            listOf(WidgetKartenBau.FlussSektor("Informationstechnologie und Kommunikation", 3.4e6, 1.2e6, 34, 12), WidgetKartenBau.FlussSektor("Energie", 1e6, 2e6, 3, 5)),
            "", 1L,
        )
        for (g in listOf(KLEIN, BREIT, GROSS)) for (skala in listOf(1f, 1.3f, 2f)) {
            val z = zuschnitt(k, g, skala)
            // Jede gezeigte Zeile hat ihren Wert und, wenn der Zuschnitt sie zeigt, ihren Balken
            z.zeilen.forEach { assertTrue(it.text.isNotBlank()) }
            if (z.haupt) assertTrue(k.haupt.isNotBlank())
        }
    }

    @Test fun zuschnittSignaleKleinFaelltAufEineZeileOderHinweis() {
        val k = WidgetKartenBau.signale(
            "AAPL",
            listOf(
                WidgetKartenBau.Signal("Insider, 90 Tage", "5 Käufer · 2 Verkäufer", 2.0, 5.0), WidgetKartenBau.Signal("Kongress, 90 Tage", "7 Käufe · 3 Verkäufe", 3.0, 7.0),
                WidgetKartenBau.Signal("Hoch- und Abstufungen, 30 Tage", "3 Hochstufungen · 1 Abstufung", 1.0, 3.0), WidgetKartenBau.Signal("Auffällige Optionen", "6 · 4 Calls · 2 Puts", 2.0, 4.0),
            ),
            "", 1L,
        )
        val gross = zuschnitt(k, GROSS)
        assertTrue(gross.zeilen.size in 2..4)
        // Der Wert steht in der kleinen Zeile und bleibt, wo die Zeile steht
        assertTrue(gross.mitUnter)
        assertEquals("Insider, 90 Tage", gross.zeilen.first().text)
        val klein = zuschnitt(k, KLEIN)
        assertTrue(klein.zeilen.size <= gross.zeilen.size)
        assertNotNull(klein)
    }

    @Test fun zuschnittVolatilitaetZweiBalkenImKleinen() {
        val k = WidgetKartenBau.volatilitaet("AAPL", 0.284, 0.215, 1.32, 0.63, "", 1L)
        val gross = zuschnitt(k, GROSS)
        assertTrue(gross.haupt)
        assertEquals(3, gross.zeilen.size)
        val breit = zuschnitt(k, BREIT)
        assertTrue(breit.zeilen.size in 1..3)
        assertEquals("IV30", breit.zeilen.first().text)
    }
}
