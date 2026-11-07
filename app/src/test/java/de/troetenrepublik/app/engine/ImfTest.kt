package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Globus 0.69: Antwort des IWF-DataMapper, Einordnung und Weltuebersicht.
class ImfTest {

    // Gekuerzte Antwortform: Kennung, Land, Jahr; dazu eine Gruppe (EURO), die Welt, Kosovo (UVK) und ein leerer Schluessel
    private val antwort = """
        {"values":{
          "NGDP_RPCH":{"DEU":{"1999":0.9,"2019":1.1,"2025":0.2,"2026":1.3,"2027":1.1},"USA":{"2026":2.3,"2027":2.3},
            "EURO":{"2026":1.0},"WEOWORLD":{"2026":3.1,"2027":3.4,"2028":3.3},"UVK":{"2026":3.5,"2027":3.6},
            "ITA":{"2026":-0.4,"2027":0.5},"ARG":{"2026":-1.0,"2027":2.0}},
          "PCPIPCH":{"DEU":{"2026":2.7},"ARG":{"2026":30.5}},
          "LUR":{"DEU":{"2026":4.0}},
          "GGXWDG_NGDP":{"ITA":{"2026":140.0},"DEU":{"2026":64.6}},
          "BCA_NGDPD":{"DEU":{"2026":4.5}},
          "NGDPDPC":{"DEU":{"2025":60000,"2026":64000.5}},"PPPPC":{"DEU":{"2026":78000}},"GGXCNL_NGDP":{"DEU":{"2026":-3.2},"ITA":{"2026":-3.0}},
          "LP":{"DEU":{"2026":84.2}},"PPPSH":{"DEU":{"2026":2.7}},
          "NGDPD":{"DEU":{"2026":5471.3},"USA":{"2026":32642.4},"ITA":{"2026":2500.0},"ARG":{"2026":600.0}},
          "":null},
         "api":{"version":"1","output-method":"json"}}
    """.trimIndent()

    private val gueltig = setOf("DEU", "USA", "ITA", "ARG", "KOS")

    @Test
    fun antwortLesen() {
        val d = Imf.lesen(antwort, 5L, gueltig)!!
        assertEquals(5L, d.abgerufen)
        assertEquals(1.3, Imf.wert(d, Imf.WACHSTUM, "DEU", 2026)!!, 1e-9)
        // Jahre vor 2000 fallen weg, Gruppen auch, die Welt bleibt, UVK heisst KOS
        assertNull(Imf.wert(d, Imf.WACHSTUM, "DEU", 1999))
        assertEquals(1.1, Imf.wert(d, Imf.WACHSTUM, "DEU", 2019))
        assertNull(d.werte[Imf.WACHSTUM]!!["EURO"])
        assertEquals(3.4, Imf.wert(d, Imf.WACHSTUM, Imf.WELT, 2027)!!, 1e-9)
        assertEquals(3.5, Imf.wert(d, Imf.WACHSTUM, "KOS", 2026)!!, 1e-9)
        assertEquals(setOf("DEU", "USA", "ITA", "ARG", "KOS"), Imf.laenderCodes(d))
        val l = Imf.land(d, "DEU", 2026)
        assertEquals(1.3, l.wachstum!!, 1e-9)
        assertEquals(1.1, l.wachstumNaechstes!!, 1e-9)
        assertEquals(-0.2, l.ausblick!!, 1e-9)
        assertEquals(2.7, l.inflation!!, 1e-9)
        assertEquals(4.5, l.leistungsbilanz!!, 1e-9)
        assertTrue(Imf.land(d, "FRA", 2026).leer)
        // Reihen des Laenderblatts (Wirtschaft): je Kopf, Kaufkraft, Haushalt, Bevoelkerung, Welt-Anteil
        assertEquals(64000.5, l.bipKopf!!, 1e-9)
        assertEquals(78000.0, l.bipKopfKkp!!, 1e-9)
        assertEquals(-3.2, l.haushalt!!, 1e-9)
        assertEquals(84.2, l.bevoelkerung!!, 1e-9)
        assertEquals(2.7, l.weltAnteil!!, 1e-9)
        assertEquals(60000.0, Imf.wert(d, Imf.BIP_KOPF, "DEU", 2025)!!, 1e-9)
        assertNull(Imf.land(d, "USA", 2026).bipKopf)
    }

    @Test
    fun alterStandOhneNeueReihen() {
        // Gespeicherte Datei von vor den neuen Reihen: kein Feld stand, nur die alten Kennungen
        val alt = """{"abgerufen":1000,"werte":{"NGDP_RPCH":{"DEU":{"2026":1.3}}}}"""
        val d = Imf.lesenGespeichert(alt)!!
        assertEquals(1.3, Imf.land(d, "DEU", 2026).wachstum!!, 1e-9)
        assertNull(Imf.land(d, "DEU", 2026).bipKopf)
        assertTrue(Imf.veraltet(d, 2_000L))
        assertFalse(Imf.veraltet(Imf.lesen(antwort, 1_000L, gueltig), 2_000L))
    }

    @Test
    fun fremdeAntwortenSindNull() {
        assertNull(Imf.lesen("<HTML>Access Denied</HTML>", 0L, gueltig))
        assertNull(Imf.lesen("{\"values\":{}}", 0L, gueltig))
        assertNull(Imf.lesen("{\"values\":{\"NGDP_RPCH\":{\"XXX\":{\"2026\":1.0}}}}", 0L, gueltig))
    }

    @Test
    fun speichernUndAlter() {
        val d = Imf.lesen(antwort, 1_000L, gueltig)!!
        val zurueck = Imf.lesenGespeichert(Imf.schreiben(d))
        assertNotNull(zurueck)
        assertEquals(d, zurueck)
        assertFalse(Imf.veraltet(d, 1_000L + 3_600_000L))
        assertTrue(Imf.veraltet(d, 1_000L + Imf.GUELTIG_MS))
        assertTrue(Imf.veraltet(null, 0L))
        assertNull(Imf.lesenGespeichert("kaputt"))
    }

    @Test
    fun einordnung() {
        val d = Imf.lesen(antwort, 0L, gueltig)!!
        val arg = Imf.einordnen(Imf.land(d, "ARG", 2026))
        assertEquals(Imf.Art.REZESSION, arg[0].art)
        assertTrue(arg.any { it.art == Imf.Art.HOHE_INFLATION })
        val ita = Imf.einordnen(Imf.land(d, "ITA", 2026)).map { it.art }
        assertTrue(Imf.Art.REZESSION in ita && Imf.Art.HOHE_SCHULDEN in ita)
        val anziehend = Imf.einordnen(Imf.Land(2.1, 2.6, 2.0, null, null, null, null))
        assertEquals(Imf.Art.ZIEHT_AN, anziehend[0].art)
        assertEquals(2.1, anziehend[0].a, 1e-9)
        assertEquals(2.6, anziehend[0].b, 1e-9)
        assertEquals(Imf.Art.SCHWAECHT_AB, Imf.einordnen(Imf.Land(3.0, 1.5, null, null, null, null, null))[0].art)
        assertTrue(Imf.einordnen(Imf.Land(2.0, 2.1, 2.0, 5.0, 60.0, 1.0, null)).isEmpty())
    }

    @Test
    fun weltueberblick() {
        val d = Imf.lesen(antwort, 0L, gueltig)!!
        val u = Imf.ueberblick(d, 2026)
        assertEquals(3.1, u.welt!!, 1e-9)
        assertEquals(3.4, u.weltNaechstes!!, 1e-9)
        assertEquals(listOf(3.1, 3.4, 3.3), u.pfad)
        // ITA und ARG negativ von fuenf Laendern mit Wachstum (DEU, USA, ITA, ARG, KOS)
        assertEquals(5, u.laender)
        assertEquals(2, u.rezession)
        assertEquals((2500.0 + 600.0) / (5471.3 + 32642.4 + 2500.0 + 600.0), u.rezessionAnteil!!, 1e-6)
        assertEquals("ARG", u.aufsteiger[0].code)
        assertEquals(3.0, u.aufsteiger[0].wert, 1e-9)
        assertEquals("DEU", u.abschwaecher[0].code)
        assertEquals(1, u.hoheInflationAnzahl)
        assertEquals("ITA", u.schuldenSchwach[0].code)
    }
}
