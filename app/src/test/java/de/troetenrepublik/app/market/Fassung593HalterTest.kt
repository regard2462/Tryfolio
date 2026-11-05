package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.Bewertungsblick.Ampel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Fassung 5.93: groesste institutionelle Halter aus der quoteSummary und die Abweichung von Zahlen zur Erwartung.
// Die Beispiele folgen dem Aufbau der Yahoo-Antworten, Namen und Werte sind erfunden.
class Fassung593HalterTest {

    // Drei Eintraege, unsortiert, einer ohne organization; dazu die Aufteilung auf Insider und Institutionen.
    private val beispiel = """
        {"quoteSummary":{"result":[{"institutionOwnership":{"ownershipList":[
          {"organization":"Fond Eins","pctHeld":{"raw":0.0912,"fmt":"9.12%"},"position":{"raw":1000000},
           "value":{"raw":200000000},"reportDate":{"raw":1735689600},"pctChange":{"raw":0.0123}},
          {"organization":"Fond Zwei","pctHeld":{"raw":0.0512},"position":{"raw":2500000},
           "value":{"raw":510000000},"reportDate":{"raw":1738368000}},
          {"pctHeld":{"raw":0.0333},"position":{"raw":9999999},"value":{"raw":777000000}}
        ]},"majorHoldersBreakdown":{"insidersPercentHeld":{"raw":0.0123},
          "institutionsPercentHeld":{"raw":0.6521},"institutionsCount":{"raw":4312}}}]}}
    """.trimIndent()

    @Test fun liestZweiHalterUndUebersicht() {
        val d = Halter.parse(beispiel)!!
        assertEquals(2, d.halter.size)
        assertEquals("Fond Zwei", d.halter[0].name)
        assertEquals("Fond Eins", d.halter[1].name)
        assertEquals(9.12, d.halter[1].anteil, 1e-9)
        assertEquals(1.23, d.halter[1].aenderung!!, 1e-9)
        assertNull(d.halter[0].aenderung)
        assertEquals(1735689600L * 1000L, d.halter[1].stichtag)
        assertEquals(1738368000L * 1000L, d.stand)
        assertEquals(1.23, d.uebersicht!!.insider, 1e-9)
        assertEquals(65.21, d.uebersicht.institutionen, 1e-9)
        assertEquals(4312, d.uebersicht.anzahl)
    }

    @Test fun mehrAlsZehnWerdenGekuerzt() {
        val json = buildString {
            append("""{"quoteSummary":{"result":[{"institutionOwnership":{"ownershipList":[""")
            for (i in 1..12) {
                if (i > 1) append(",")
                append("""{"organization":"Institut $i","pctHeld":{"raw":0.01},"position":{"raw":${i * 100000}},"value":{"raw":${i * 1000000}},"reportDate":{"raw":1735689600}}""")
            }
            append("""]},"majorHoldersBreakdown":{"insidersPercentHeld":{"raw":0.01},"institutionsPercentHeld":{"raw":0.7},"institutionsCount":{"raw":10}}}]}}""")
        }
        val d = Halter.parse(json)!!
        assertEquals(10, d.halter.size)
        assertEquals("Institut 12", d.halter[0].name)
        assertEquals("Institut 3", d.halter[9].name)
    }

    @Test fun ohneDatenUndKaputtesJson() {
        assertNull(Halter.parse("""{"quoteSummary":{"result":[{}]}}"""))
        assertNull(Halter.parse("{"))
    }

    @Test fun zahlAlsTextWirdAkzeptiert() {
        val json = """
            {"quoteSummary":{"result":[{"institutionOwnership":{"ownershipList":[
              {"organization":"Fond Text","pctHeld":"0.05","position":"1000","value":"2000","reportDate":"1735689600"}
            ]}}]}}
        """.trimIndent()
        val d = Halter.parse(json)!!
        assertEquals(1, d.halter.size)
        assertEquals(5.0, d.halter[0].anteil, 1e-9)
        assertEquals(1000.0, d.halter[0].stueck, 1e-9)
    }

    @Test fun ueberraschung() {
        val gut = Bewertungsblick.ueberraschung(1.1, 1.0)!!
        assertEquals(Ampel.GRUEN, gut.ampel)
        assertEquals(10.0, gut.abweichung, 1e-9)
        val schlecht = Bewertungsblick.ueberraschung(0.9, 1.0)!!
        assertEquals(Ampel.ROT, schlecht.ampel)
        assertEquals(-10.0, schlecht.abweichung, 1e-9)
        assertNull(Bewertungsblick.ueberraschung(1.0, 0.0))
    }

    // Quartalsbeschriftung der Gewinnueberraschungen: Quartalsende wird zu Qn JJ, sonst gekuerzt (stuerzte in 5.93 ab).
    @Test fun quartalsbeschriftung() {
        assertEquals("Q3 25", de.troetenrepublik.app.ui.quartalKurz("2025-09-30"))
        assertEquals("Q4 24", de.troetenrepublik.app.ui.quartalKurz("bis 2024-12-31"))
        assertEquals("4Q2024", de.troetenrepublik.app.ui.quartalKurz("4Q2024"))
        assertEquals("2025-13", de.troetenrepublik.app.ui.quartalKurz("2025-13-01"))
    }
}
