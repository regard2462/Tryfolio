package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Halter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.96: Fonds als zweite Halterliste, doppelt gemeldete Namen zusammengefuehrt, Eintraege mit nur einem
// Anteil bleiben, ohne jede Angabe faellt weg. Dazu Anzeigeanteile unter der kleinsten Stelle, Veraenderungen um
// null und die Art der Namensliste. Die Beispiele folgen dem Aufbau der Yahoo-Antworten, Namen sind erfunden.
class Fassung596HalterTest {

    // Ein 13F-Halter wie bei einer Kanada-Aktie, drei Fonds (einer doppelt mit aelterem Datum, einer nur mit
    // Anteil), ein Fonds ohne jede Angabe, dazu die Aufteilung auf Insider und Institutionen.
    private val beispiel = """
        {"quoteSummary":{"result":[
          {"institutionOwnership":{"ownershipList":[
            {"organization":"Grandview Custody Ltd","pctHeld":{"raw":0.000123},"position":{"raw":160000},
             "value":{"raw":140700},"reportDate":{"raw":1735689600},"pctChange":{"raw":0}}
          ]},"fundOwnership":{"ownershipList":[
            {"organization":"Nordlicht Fonds SA","pctHeld":{"raw":0.021},"position":{"raw":900000},
             "value":{"raw":3100000},"reportDate":{"raw":1738368000},"pctChange":{"raw":0.031}},
            {"organization":" nordlicht fonds sa ","pctHeld":{"raw":0.019},"position":{"raw":800000},
             "value":{"raw":2900000},"reportDate":{"raw":1735689600}},
            {"organization":"Seeblick Treuhand","pctHeld":{"raw":0.004}},
            {"organization":"Leergut Verwaltung"}
          ]},"majorHoldersBreakdown":{"insidersPercentHeld":{"raw":0.57},"institutionsPercentHeld":{"raw":0.083},
            "institutionsCount":{"raw":26}}}]}}
    """.trimIndent()

    @Test fun halterFondsUndZusammenfuehrung() {
        val d = Halter.parse(beispiel)!!
        assertEquals(1, d.halter.size)
        assertEquals("Grandview Custody Ltd", d.halter[0].name)
        assertEquals(0.0123, d.halter[0].anteil, 1e-9)
        assertEquals(160000.0, d.halter[0].stueck, 1e-9)
        assertEquals(140700.0, d.halter[0].wert, 1e-9)
        assertEquals(0.0, d.halter[0].aenderung!!, 1e-9)
        // Der doppelte Fonds zaehlt einmal, mit der neuesten Meldung; nur mit Anteil bleibt, ohne Angaben faellt weg.
        assertEquals(2, d.fonds.size)
        assertEquals("Nordlicht Fonds SA", d.fonds[0].name)
        assertEquals(900000.0, d.fonds[0].stueck, 1e-9)
        assertEquals(1738368000L * 1000L, d.fonds[0].stichtag)
        assertEquals(3.1, d.fonds[0].aenderung!!, 1e-9)
        assertEquals("Seeblick Treuhand", d.fonds[1].name)
        assertEquals(0.4, d.fonds[1].anteil, 1e-9)
        assertEquals(0.0, d.fonds[1].stueck, 1e-9)
        assertNull(d.fonds[1].aenderung)
        assertEquals(57.0, d.uebersicht!!.insider, 1e-9)
        assertEquals(8.3, d.uebersicht.institutionen, 1e-9)
        assertEquals(26, d.uebersicht.anzahl)
        assertEquals(1738368000L * 1000L, d.stand)
    }

    @Test fun anteilAnzeige() {
        val gross = HalterAnzeige.anteil(0.0123, 2)
        assertFalse(gross.unter)
        assertEquals(0.0123, gross.wert, 1e-12)
        val klein = HalterAnzeige.anteil(0.004, 2)
        assertTrue(klein.unter)
        assertEquals(0.01, klein.wert, 1e-12)
        val nullwert = HalterAnzeige.anteil(0.0, 2)
        assertFalse(nullwert.unter)
        assertEquals(0.0, nullwert.wert, 1e-12)
        val fastNull = HalterAnzeige.anteil(0.04, 1)
        assertTrue(fastNull.unter)
        assertEquals(0.1, fastNull.wert, 1e-12)
        // Genau an der Rundungsgrenze rundet der Wert schon auf die kleinste Stelle auf
        assertFalse(HalterAnzeige.anteil(0.005, 2).unter)
        assertFalse(HalterAnzeige.anteil(0.05, 1).unter)
    }

    @Test fun aenderungAnzeige() {
        assertNull(HalterAnzeige.aenderung(null))
        assertNull(HalterAnzeige.aenderung(Double.NaN))
        assertEquals(0.0, HalterAnzeige.aenderung(0.0)!!, 1e-12)
        assertEquals(0.0, HalterAnzeige.aenderung(0.03)!!, 1e-12)
        assertEquals(0.0, HalterAnzeige.aenderung(-0.04)!!, 1e-12)
        assertEquals(1.2, HalterAnzeige.aenderung(1.2)!!, 1e-12)
        assertEquals(-0.06, HalterAnzeige.aenderung(-0.06)!!, 1e-12)
    }

    @Test fun listenArt() {
        assertEquals(HalterAnzeige.Liste.KEINE, HalterAnzeige.liste(0))
        assertEquals(HalterAnzeige.Liste.TEIL, HalterAnzeige.liste(1))
        assertEquals(HalterAnzeige.Liste.TEIL, HalterAnzeige.liste(9))
        assertEquals(HalterAnzeige.Liste.ZEHN, HalterAnzeige.liste(10))
    }
}
