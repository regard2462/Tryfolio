package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.Aktienfinder.Daten
import de.troetenrepublik.app.engine.Aktienfinder.Kennzahl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: Bilanzqualitaet im Aktienfinder. Punkte aus F-Score und Altman-Zone, Gewichtung in der Qualitaet,
// Ausnahme fuer Banken und Versicherer, Filter „nur solide Bilanz“, Reihenfolge halten und der Zwischenspeicher.
class Fassung592FinderBilanzTest {

    private val tag = 86_400_000L

    // Gewinn 1 je Buchwert 10: Eigenkapitalrendite 10 %, also 65 Punkte.
    private fun firma(symbol: String, branche: String = "BAU", f: Double? = null, zone: Qualitaet.Zone? = null, ampel: Bewertungsblick.Ampel? = null) =
        Daten(symbol, symbol, branche = branche, gewinn = 1.0, buchwert = 10.0, fScore = f, zone = zone, bilanz = ampel)

    @Test fun punkteAusFScoreUndZone() {
        assertEquals(100.0, Aktienfinder.fScorePunkte(9.0), 1e-9)
        assertEquals(0.0, Aktienfinder.fScorePunkte(0.0), 1e-9)
        assertEquals(50.0, Aktienfinder.fScorePunkte(4.5), 1e-9)
        assertEquals(85.0, Aktienfinder.zonePunkte(Qualitaet.Zone.SICHER), 1e-9)
        assertEquals(50.0, Aktienfinder.zonePunkte(Qualitaet.Zone.GRAU), 1e-9)
        assertEquals(10.0, Aktienfinder.zonePunkte(Qualitaet.Zone.GEFAEHRDET), 1e-9)
        // Gewichte in der Qualitaet: F-Score doppelt, Zone einfach, die uebrigen je einfach
        assertEquals(2.0, Kennzahl.PIOTROSKI.gewicht, 1e-9)
        assertEquals(1.0, Kennzahl.ALTMAN.gewicht, 1e-9)
        assertEquals(1.0, Kennzahl.RENDITE.gewicht, 1e-9)
        assertEquals(Bereich.QUALITAET, Kennzahl.PIOTROSKI.bereich)
    }

    @Test fun bilanzInDerQualitaet() {
        val e = Aktienfinder.bewerten(
            listOf(firma("STARK", f = 9.0, zone = Qualitaet.Zone.SICHER), firma("SCHWACH", f = 2.0, zone = Qualitaet.Zone.GEFAEHRDET), firma("OFFEN")),
        ).associateBy { it.symbol }
        // (65 + 2 * 100 + 85) / 4
        assertEquals(87.5, e.getValue("STARK").bereiche.getValue(Bereich.QUALITAET)!!, 1e-9)
        assertEquals(100.0, e.getValue("STARK").kennzahlen.getValue(Kennzahl.PIOTROSKI), 1e-9)
        assertEquals(85.0, e.getValue("STARK").kennzahlen.getValue(Kennzahl.ALTMAN), 1e-9)
        // (65 + 2 * 22,2 + 10) / 4
        assertEquals((65.0 + 2 * 200.0 / 9.0 + 10.0) / 4.0, e.getValue("SCHWACH").bereiche.getValue(Bereich.QUALITAET)!!, 1e-9)
        // Ohne Bilanz zaehlen F-Score und Zone neutral: (65 + 2 * 50 + 50) / 4
        assertEquals(53.75, e.getValue("OFFEN").bereiche.getValue(Bereich.QUALITAET)!!, 1e-9)
        assertFalse(Kennzahl.PIOTROSKI in e.getValue("OFFEN").kennzahlen)
        // Reihenfolge nach der Gesamtpunktzahl
        assertTrue(e.getValue("STARK").gesamt > e.getValue("OFFEN").gesamt)
        assertTrue(e.getValue("OFFEN").gesamt > e.getValue("SCHWACH").gesamt)
        // Grund mit Beitrag: 50 Punkte Abweichung, Qualitaet 15 von 100, F-Score 2 von 4 Teilen
        val g = e.getValue("STARK").gruende.first { it.kennzahl == Kennzahl.PIOTROSKI }
        assertTrue(g.gut)
        assertEquals(3.75, g.beitrag, 1e-9)
        assertEquals(9.0, g.wert, 1e-9)
    }

    @Test fun datenlageRechnetDieBilanzMit() {
        val e = Aktienfinder.bewerten(
            listOf(firma("MIT", f = 7.0, zone = Qualitaet.Zone.GRAU), firma("OHNE"), firma("HALB", f = 7.0)),
        ).associateBy { it.symbol }
        // Nur die Qualitaet (15 von 100) hat Daten; ohne Bilanz zaehlen 4 von 7 Teilen
        assertEquals(0.15, e.getValue("MIT").abdeckung, 1e-9)
        assertEquals(0.15 * 4.0 / 7.0, e.getValue("OHNE").abdeckung, 1e-9)
        assertEquals(0.15 * 6.0 / 7.0, e.getValue("HALB").abdeckung, 1e-9)
        // Die Ampel allein (ohne F-Score und Zone) deckt die ganze Bilanz ab
        val ampel = Aktienfinder.bewerten(listOf(firma("AMPEL", ampel = Bewertungsblick.Ampel.GRUEN))).single()
        assertEquals(0.15, ampel.abdeckung, 1e-9)
        assertEquals(90.0, ampel.kennzahlen.getValue(Kennzahl.BILANZ), 1e-9)
    }

    @Test fun ausnahmeFinanzwerte() {
        val l = listOf(
            firma("BANK", branche = "BANKEN", f = 1.0, zone = Qualitaet.Zone.GEFAEHRDET),
            firma("VERS", branche = "VERSICHERUNG", f = 9.0, zone = Qualitaet.Zone.SICHER),
            firma("BAU", f = 5.0),
        )
        val e = Aktienfinder.bewerten(l).associateBy { it.symbol }
        for (s in listOf("BANK", "VERS")) {
            assertFalse(Kennzahl.PIOTROSKI in e.getValue(s).kennzahlen)
            assertFalse(Kennzahl.ALTMAN in e.getValue(s).kennzahlen)
            // Nur Eigenkapitalrendite, ohne neutrale Bilanz, und volle Datenlage
            assertEquals(65.0, e.getValue(s).bereiche.getValue(Bereich.QUALITAET)!!, 1e-9)
            assertEquals(0.15, e.getValue(s).abdeckung, 1e-9)
        }
        assertFalse(Aktienfinder.mitBilanz("BANKEN"))
        assertFalse(Aktienfinder.mitBilanz("VERSICHERUNG"))
        assertTrue(Aktienfinder.mitBilanz("SOFTWARE"))
        // Abruf nur fuer Werte ausserhalb der Finanzbranche, ohne schon geladene
        assertEquals(listOf("BAU"), Aktienfinder.fuerBilanz(Aktienfinder.bewerten(l)) { false })
        assertTrue(Aktienfinder.fuerBilanz(Aktienfinder.bewerten(l)) { it == "BAU" }.isEmpty())
    }

    @Test fun abrufReihenfolgeDieBestenZuerst() {
        val l = Aktienfinder.bewerten(listOf(firma("A", f = 2.0), firma("B", f = 9.0), firma("C", f = 5.0)))
        assertEquals(listOf("B", "C", "A"), Aktienfinder.fuerBilanz(l) { false })
    }

    @Test fun filterSolideBilanz() {
        val l = Aktienfinder.bewerten(
            listOf(
                firma("G", ampel = Bewertungsblick.Ampel.GRUEN), firma("Y", ampel = Bewertungsblick.Ampel.GELB),
                firma("R", ampel = Bewertungsblick.Ampel.ROT), firma("N"),
            ),
        )
        val solide = Aktienfinder.auswahl(l, Aktienfinder.Filter(solideBilanz = true), Aktienfinder.Sortierung.Gesamt)
        assertEquals(listOf("G"), solide.map { it.symbol })
        assertEquals(4, Aktienfinder.auswahl(l, Aktienfinder.Filter(), Aktienfinder.Sortierung.Gesamt).size)
    }

    @Test fun reihenfolgeHalten() {
        val l = Aktienfinder.bewerten(listOf(firma("A"), firma("B"), firma("C"), firma("D"))).associateBy { it.symbol }
        fun ergebnisse(vararg s: String) = s.map { l.getValue(it) }
        assertEquals(listOf("A", "B", "C", "D"), Aktienfinder.ordnungHalten(ergebnisse("C", "A", "B", "D"), listOf("A", "B", "C")).map { it.symbol })
        // Was nicht mehr passt, faellt heraus, Neues kommt ans Ende
        assertEquals(listOf("A", "C", "D"), Aktienfinder.ordnungHalten(ergebnisse("D", "C", "A"), listOf("A", "B", "C")).map { it.symbol })
        assertEquals(listOf("B", "A"), Aktienfinder.ordnungHalten(ergebnisse("B", "A"), emptyList()).map { it.symbol })
    }

    // Drei Jahre mit steigender Rendite, sinkenden Schulden, steigender Marge und gleich vielen Aktien: F-Score 9.
    private fun jahre(): List<Qualitaet.Jahr> = listOf(
        Qualitaet.Jahr("2023-12-31", "USD", 5.0, 100.0, 8.0, 30.0, 40.0, 30.0, 10.0, 30.0, 100.0, 10.0, 20.0, 8.0, 50.0),
        Qualitaet.Jahr("2024-12-31", "USD", 8.0, 100.0, 12.0, 25.0, 45.0, 30.0, 10.0, 35.0, 110.0, 15.0, 30.0, 12.0, 50.0),
        Qualitaet.Jahr("2025-12-31", "USD", 10.0, 100.0, 15.0, 20.0, 50.0, 30.0, 10.0, 40.0, 120.0, 20.0, 40.0, 15.0, 50.0),
    )

    @Test fun werteAusJahresabschluessen() {
        val w = FinderBilanz.werte(jahre(), 200.0)!!
        assertEquals(9.0, w.fScore!!, 1e-9)
        // Z = 0,24 + 0,56 + 0,495 + 2,4 + 1,2 = 4,895
        assertEquals(Qualitaet.Zone.SICHER, w.zone)
        assertEquals(Bewertungsblick.Ampel.GRUEN, w.bilanz!!.ampel)
        assertEquals(Bewertungsblick.Grund.PROFITABEL, w.bilanz.gruende.first())
        // Ohne Boersenwert fehlt nur Altman
        val ohne = FinderBilanz.werte(jahre(), null)!!
        assertNull(ohne.zone)
        assertEquals(9.0, ohne.fScore!!, 1e-9)
        assertNull(FinderBilanz.werte(emptyList(), 200.0))
        // Ein einzelnes Jahr ohne Boersenwert: weder F-Score noch Zone
        assertNull(FinderBilanz.werte(jahre().takeLast(1), null))
    }

    @Test fun zwischenspeicherGueltigkeit() {
        val jetzt = 1_800_000_000_000L
        val mit = FinderBilanz.Eintrag(jetzt - 29 * tag, jahre())
        assertTrue(FinderBilanz.gueltig(mit, jetzt))
        assertFalse(FinderBilanz.gueltig(mit.copy(t = jetzt - 31 * tag), jetzt))
        // Ohne Jahre nur sieben Tage, dann fragt die App neu
        assertTrue(FinderBilanz.gueltig(FinderBilanz.Eintrag(jetzt - 6 * tag), jetzt))
        assertFalse(FinderBilanz.gueltig(FinderBilanz.Eintrag(jetzt - 8 * tag), jetzt))
        // Zeitpunkt in der Zukunft (verstellte Uhr) gilt nicht
        assertFalse(FinderBilanz.gueltig(mit.copy(t = jetzt + tag), jetzt))
        // Gespeichert werden die letzten vier Jahre
        val fuenf = listOf(Qualitaet.Jahr("2021-12-31"), Qualitaet.Jahr("2022-12-31")) + jahre()
        assertEquals(listOf("2022-12-31", "2023-12-31", "2024-12-31", "2025-12-31"), FinderBilanz.eintrag(fuenf, jetzt).j.map { it.ende })
    }

    @Test fun zwischenspeicherDatei() {
        val jetzt = 1_800_000_000_000L
        val text = FinderBilanz.schreiben(
            mapOf(
                "AAA" to FinderBilanz.Eintrag(jetzt - tag, jahre()),
                "LEER" to FinderBilanz.Eintrag(jetzt - tag),
                "ALT" to FinderBilanz.Eintrag(jetzt - 40 * tag, jahre()),
            ),
            jetzt,
        )
        val m = FinderBilanz.lesen(text)
        // Abgelaufene Eintraege kommen nicht in die Datei
        assertEquals(setOf("AAA", "LEER"), m.keys)
        assertEquals(jahre(), m.getValue("AAA").j)
        assertTrue(m.getValue("LEER").j.isEmpty())
        // Fehlende Angaben stehen nicht als null in der Datei
        assertFalse(text.contains("null"))
        assertTrue(FinderBilanz.lesen("kaputt").isEmpty())
    }
}
