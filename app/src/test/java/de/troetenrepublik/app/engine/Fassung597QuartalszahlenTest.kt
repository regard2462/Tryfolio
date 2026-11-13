package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Quartalsblick
import de.troetenrepublik.app.market.Quartalszahlen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// Quartalszahlen (5.97): Uhrzeit eines Termins in der Ortszeit des Nutzers, Erwartungen aus Yahoo und die
// Uebersicht einer Aktie, kleine selbst gebaute Beispiele
class Fassung597QuartalszahlenTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val usPlatz = Quartalszahlen.platz("AAPL")!!

    @Test
    fun usTerminVorBoersenbeginn() {
        val tag = LocalDate.of(2026, 11, 3)
        val u = Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.VOR, null, usPlatz)
        assertEquals(Zahlenzeit.Art.VOR, u.art)
        assertNull(u.von)
        val o = Zahlenzeit.ort(u.bis!!, tag, berlin)
        assertEquals(15, o.stunde)
        assertEquals(30, o.minute)
        assertEquals(0, o.tagVersatz)
    }

    @Test
    fun usTerminNachBoersenschluss() {
        val tag = LocalDate.of(2026, 11, 3)
        val u = Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.NACH, null, usPlatz)
        val o = Zahlenzeit.ort(u.von!!, tag, berlin)
        assertEquals(22, o.stunde)
        assertEquals(0, o.minute)
        assertEquals(0, o.tagVersatz)
    }

    @Test
    fun usTerminWaehrendDesHandels() {
        val tag = LocalDate.of(2026, 11, 3)
        val u = Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.HANDEL, null, usPlatz)
        assertEquals(Zahlenzeit.Art.HANDEL, u.art)
        val von = Zahlenzeit.ort(u.von!!, tag, berlin)
        val bis = Zahlenzeit.ort(u.bis!!, tag, berlin)
        assertEquals(15, von.stunde)
        assertEquals(30, von.minute)
        assertEquals(22, bis.stunde)
        assertEquals(0, bis.minute)
    }

    @Test
    fun herbstZeitumstellung() {
        // Europa hat Winterzeit, die USA noch Sommerzeit
        val tag = LocalDate.of(2026, 10, 27)
        val vor = Zahlenzeit.ort(Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.VOR, null, usPlatz).bis!!, tag, berlin)
        assertEquals(14, vor.stunde)
        assertEquals(30, vor.minute)
        val nach = Zahlenzeit.ort(Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.NACH, null, usPlatz).von!!, tag, berlin)
        assertEquals(21, nach.stunde)
        assertEquals(0, nach.minute)
    }

    @Test
    fun fruehjahrZeitumstellung() {
        // die USA haben Sommerzeit, Europa erst ab dem 28.03.
        val mitten = LocalDate.of(2027, 3, 16)
        val a = Zahlenzeit.ort(Zahlenzeit.berechnen(mitten, Quartalszahlen.Zeit.VOR, null, usPlatz).bis!!, mitten, berlin)
        assertEquals(14, a.stunde)
        assertEquals(30, a.minute)
        val ende = LocalDate.of(2027, 3, 30)
        val b = Zahlenzeit.ort(Zahlenzeit.berechnen(ende, Quartalszahlen.Zeit.VOR, null, usPlatz).bis!!, ende, berlin)
        assertEquals(15, b.stunde)
        assertEquals(30, b.minute)
    }

    @Test
    fun nutzerInLosAngeles() {
        val tag = LocalDate.of(2026, 11, 3)
        val o = Zahlenzeit.ort(Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.VOR, null, usPlatz).bis!!, tag, ZoneId.of("America/Los_Angeles"))
        assertEquals(6, o.stunde)
        assertEquals(30, o.minute)
        assertEquals(0, o.tagVersatz)
    }

    @Test
    fun nutzerInTokio() {
        // 16:00 New York am 05.11. ist 06:00 Tokio am 06.11.
        val tag = LocalDate.of(2026, 11, 5)
        val o = Zahlenzeit.ort(Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.NACH, null, usPlatz).von!!, tag, ZoneId.of("Asia/Tokyo"))
        assertEquals(6, o.stunde)
        assertEquals(0, o.minute)
        assertEquals(1, o.tagVersatz)
    }

    @Test
    fun xetraWert() {
        val tag = LocalDate.of(2026, 11, 3)
        val platz = Quartalszahlen.platz("SAP.DE")!!
        val bis = Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.VOR, null, platz).bis!!
        val inBerlin = Zahlenzeit.ort(bis, tag, berlin)
        assertEquals(9, inBerlin.stunde)
        assertEquals(0, inBerlin.minute)
        val inNewYork = Zahlenzeit.ort(bis, tag, ZoneId.of("America/New_York"))
        assertEquals(3, inNewYork.stunde)
        assertEquals(0, inNewYork.minute)
    }

    @Test
    fun genauerZeitpunkt() {
        val tag = LocalDate.of(2026, 11, 5)
        val ms = ZonedDateTime.of(tag, LocalTime.of(7, 0), berlin).toInstant().toEpochMilli()
        val u = Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.OFFEN, ms, usPlatz)
        assertEquals(Zahlenzeit.Art.GENAU, u.art)
        assertEquals(ms, u.von)
        assertNull(u.bis)
        assertEquals("07:00", Zahlenzeit.hhmm(Zahlenzeit.ort(ms, tag, berlin)))
        assertEquals("01:00", Zahlenzeit.hhmm(Zahlenzeit.ort(ms, tag, ZoneId.of("America/New_York"))))
    }

    @Test
    fun offenUndOhnePlatz() {
        val tag = LocalDate.of(2026, 11, 3)
        val offen = Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.OFFEN, null, usPlatz)
        assertEquals(Zahlenzeit.Art.OFFEN, offen.art)
        assertNull(offen.von)
        assertNull(offen.bis)
        val ohne = Zahlenzeit.berechnen(tag, Quartalszahlen.Zeit.VOR, null, null)
        assertEquals(Zahlenzeit.Art.VOR, ohne.art)
        assertNull(ohne.von)
        assertNull(ohne.bis)
    }

    @Test
    fun gemeinsameUhrzeit() {
        val a = Zahlenzeit.Uhrzeit(Zahlenzeit.Art.VOR, bis = 100L)
        assertEquals(a, Zahlenzeit.gemeinsam(listOf(a, Zahlenzeit.Uhrzeit(Zahlenzeit.Art.VOR, null, 100L))))
        assertNull(Zahlenzeit.gemeinsam(listOf(a, Zahlenzeit.Uhrzeit(Zahlenzeit.Art.NACH, von = 100L))))
        assertNull(Zahlenzeit.gemeinsam(emptyList()))
    }

    @Test
    fun uhrzeitText() {
        assertEquals("07:05", Zahlenzeit.hhmm(Zahlenzeit.Ort(7, 5, 0)))
    }

    // selbst gebaute Antwort im Aufbau von Yahoo quoteSummary: Zahlen als raw/fmt, ein leeres Objekt fuer high
    private val yahoo = """
        {"quoteSummary":{"result":[{
        "earningsTrend":{"trend":[
        {"period":"0q","endDate":"2026-12-31",
        "earningsEstimate":{"avg":{"raw":2.5,"fmt":"2.50"},"low":{"raw":2.0,"fmt":"2.00"},"high":{"raw":3.0,"fmt":"3.00"},"numberOfAnalysts":{"raw":10,"fmt":"10"},"yearAgoEps":{"raw":2.2,"fmt":"2.20"},"growth":{"raw":0.136,"fmt":"13.60%"},"earningsCurrency":"USD"},
        "revenueEstimate":{"avg":{"raw":12500000000,"fmt":"12.5B"},"low":{"raw":12000000000,"fmt":"12B"},"high":{},"numberOfAnalysts":{"raw":9,"fmt":"9"},"yearAgoRevenue":{"raw":11950000000,"fmt":"11.95B"},"growth":{"raw":0.046,"fmt":"4.60%"},"revenueCurrency":"USD"},
        "epsTrend":{"current":{"raw":2.5,"fmt":"2.50"},"7daysAgo":{"raw":2.48,"fmt":"2.48"},"30daysAgo":{"raw":2.4,"fmt":"2.40"},"90daysAgo":{"raw":2.3,"fmt":"2.30"}},
        "epsRevisions":{"upLast30days":{"raw":6,"fmt":"6"},"downLast30days":{"raw":2,"fmt":"2"}}},
        {"period":"+1q","endDate":"2027-03-31"}]},
        "earningsHistory":{"history":[
        {"quarter":{"raw":1764460800,"fmt":"2025-11-30"},"epsActual":{"raw":2.1,"fmt":"2.10"},"epsEstimate":{"raw":2.0,"fmt":"2.00"},"surprisePercent":{"raw":0.05,"fmt":"5.00%"}},
        {"quarter":{"raw":1761916800,"fmt":"2025-10-31"},"epsActual":{"raw":2.0,"fmt":"2.00"},"epsEstimate":{"raw":2.05,"fmt":"2.05"},"surprisePercent":{"raw":-0.0244,"fmt":"-2.44%"}},
        {"quarter":{"raw":1758931200,"fmt":"2025-09-30"},"epsActual":{"raw":1.9,"fmt":"1.90"},"epsEstimate":{"raw":2.0,"fmt":"2.00"},"surprisePercent":{"raw":-0.05,"fmt":"-5.00%"}}]},
        "calendarEvents":{"earnings":{
        "earningsDate":[{"raw":1797004800,"fmt":"2026-12-08"}],
        "earningsCallDate":[{"raw":1797022800,"fmt":"2026-12-08"}],
        "isEarningsDateEstimate":false,
        "earningsAverage":{"raw":2.5,"fmt":"2.50"},"earningsLow":{"raw":2.0,"fmt":"2.00"},"earningsHigh":{"raw":3.0,"fmt":"3.00"},
        "revenueAverage":{"raw":12500000000,"fmt":"12.5B"},"revenueLow":{"raw":12000000000,"fmt":"12B"},"revenueHigh":{"raw":13000000000,"fmt":"13B"}}}
        }]}}
    """.trimIndent()

    @Test
    fun yahooErwartungenLesen() {
        val d = Quartalsblick.lesenText(yahoo)!!
        assertEquals("2026-12-31", d.quartalsende)
        assertEquals(2.5, d.gewinn!!.mittel!!, 1e-9)
        assertEquals(10, d.gewinn!!.analysten!!)
        assertEquals(13.6, d.gewinn!!.wachstum!!, 1e-6)
        assertNull(d.umsatz!!.hoch)
        assertEquals(listOf("2025-09-30", "2025-10-31", "2025-11-30"), d.verlauf.map { it.ende })
        assertEquals(-5.0, d.verlauf[0].abweichung!!, 1e-9)
        // Seit 0.78 aus gemeldet und erwartet gerechnet, die Angabe der Quelle nur als Ersatz
        assertEquals(-2.44, d.verlauf[1].abweichung!!, 0.01)
        assertEquals(1797004800000L, d.termin!!)
        assertFalse(d.geschaetzt!!)
        assertEquals(1797022800000L, d.konferenz!!)
        assertEquals(6, d.hoch30!!)
        assertEquals(2, d.runter30!!)
    }

    @Test
    fun yahooMuellLesen() {
        assertNull(Quartalsblick.lesenText("<html>"))
        assertNull(Quartalsblick.lesenText("""{"quoteSummary":{"result":[{}]}}"""))
    }

    private fun termin(
        eps: Double? = null,
        epsErwartet: Double? = null,
        epsVorjahr: Double? = null,
        abweichung: Double? = null,
        schaetzungen: Int? = null,
    ): Quartalszahlen.Termin = Quartalszahlen.Termin(
        "TEST", "Test", LocalDate.of(2026, 12, 8).toEpochDay(), Quartalszahlen.Zeit.VOR,
        epsErwartet = epsErwartet, schaetzungen = schaetzungen, epsVorjahr = epsVorjahr, eps = eps, abweichung = abweichung,
    )

    @Test
    fun kalenderErwartungOhneYahoo() {
        val u = Quartalsueberblick.erstellen(termin(epsErwartet = 3.36, epsVorjahr = 3.22, schaetzungen = 11), null)
        assertEquals(1, u.gewinn.size)
        assertEquals(Quartalsueberblick.Quelle.KALENDER, u.gewinn[0].quelle)
        assertEquals(3.36, u.gewinn[0].mittel!!, 1e-9)
        assertEquals(11, u.gewinn[0].analysten!!)
        val p = u.punkte.first { it.art == Quartalsueberblick.PunktArt.GEWINN_WACHSTUM }
        assertEquals(4.35, p.p!!, 0.01)
    }

    @Test
    fun gewinnAusVerlust() {
        val u = Quartalsueberblick.erstellen(termin(epsErwartet = 0.20, epsVorjahr = -0.50), null)
        assertTrue(u.punkte.any { it.art == Quartalsueberblick.PunktArt.GEWINN_AUS_VERLUST })
    }

    @Test
    fun verlustErwartet() {
        val u = Quartalsueberblick.erstellen(termin(epsErwartet = -0.10, epsVorjahr = -0.50), null)
        assertTrue(u.punkte.any { it.art == Quartalsueberblick.PunktArt.VERLUST_ERWARTET })
    }

    @Test
    fun gemeldetUnterErwartung() {
        val u = Quartalsueberblick.erstellen(termin(eps = 1.89, epsErwartet = 2.01, abweichung = -5.97), null)
        assertEquals(Quartalsueberblick.PunktArt.GEMELDET_UNTER, u.punkte[0].art)
        assertEquals(-5.97, u.punkte[0].p!!, 0.01)
    }

    @Test
    fun gemeldetWieErwartet() {
        val u = Quartalsueberblick.erstellen(termin(eps = 2.02, epsErwartet = 2.01), null)
        assertEquals(Quartalsueberblick.PunktArt.GEMELDET_WIE_ERWARTET, u.punkte[0].art)
    }

    private fun yahooDaten(tief: Double, hoch: Double): Quartalsblick.Daten =
        Quartalsblick.Daten(
            quartalsende = "2026-10-31",
            gewinn = Quartalsblick.Spanne(2.5, tief, hoch, 10, 2.2, 13.6),
            umsatz = null,
            gewinnWaehrung = "USD",
            umsatzWaehrung = null,
            gewinnJetzt = 2.5,
            gewinnVor7 = 2.48,
            gewinnVor30 = 2.4,
            gewinnVor90 = 2.3,
            hoch30 = 6,
            runter30 = 2,
            verlauf = listOf(
                Quartalsblick.Quartal("2025-03-31", 1.8, 1.7, 5.9),
                Quartalsblick.Quartal("2025-06-30", 2.3, 2.0, 15.0),
                Quartalsblick.Quartal("2025-09-30", 2.1, 2.0, 5.0),
                Quartalsblick.Quartal("2025-10-31", 2.2, 2.0, 10.0),
                Quartalsblick.Quartal("2025-11-30", 2.05, 2.0, 2.5),
                Quartalsblick.Quartal("2025-12-31", 1.9, 2.0, -5.0),
            ),
            termin = null,
            terminBis = null,
            geschaetzt = null,
            konferenz = null,
            umsatzTief = null,
            umsatzHoch = null,
            umsatzMittel = null,
            gewinnTief = null,
            gewinnHoch = null,
            gewinnMittel = null,
        )

    @Test
    fun yahooSpanneRevisionTreffer() {
        val u = Quartalsueberblick.erstellen(termin(), yahooDaten(2.0, 3.0))
        assertTrue(u.punkte.any { it.art == Quartalsueberblick.PunktArt.SPANNE_WEIT })
        val eng = Quartalsueberblick.erstellen(termin(), yahooDaten(2.45, 2.55))
        assertFalse(eng.punkte.any { it.art == Quartalsueberblick.PunktArt.SPANNE_WEIT })
        assertTrue(eng.punkte.any { it.art == Quartalsueberblick.PunktArt.SPANNE_ENG })
        assertTrue(u.punkte.any { it.art == Quartalsueberblick.PunktArt.SCHAETZUNG_HOCH })
        val treffer = u.punkte.first { it.art == Quartalsueberblick.PunktArt.TREFFER }
        assertEquals(3, treffer.n!!)
        assertEquals(4, treffer.m!!)
        assertEquals(4, u.verlauf.size)
    }

    @Test
    fun yahooNurZumPassendenTermin() {
        // Termin am 08.12.2026; Yahoo nennt schon den naechsten Termin im Maerz: Erwartungen gehoeren nicht dazu
        val spaeter = yahooDaten(2.0, 3.0).copy(termin = java.time.ZonedDateTime.of(2027, 3, 9, 21, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli())
        assertFalse(Quartalsueberblick.passt(termin(), spaeter))
        val u = Quartalsueberblick.erstellen(termin(), spaeter)
        assertTrue(u.gewinn.isEmpty())
        assertNull(u.revision)
        assertEquals(4, u.verlauf.size)
        // Yahoo-Termin am selben Tag passt, ebenso ohne Termin ein Quartalsende kurz davor
        val gleich = yahooDaten(2.0, 3.0).copy(termin = java.time.ZonedDateTime.of(2026, 12, 8, 21, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli())
        assertTrue(Quartalsueberblick.passt(termin(), gleich))
        assertTrue(Quartalsueberblick.passt(termin(), yahooDaten(2.0, 3.0)))
        assertFalse(Quartalsueberblick.passt(termin(), yahooDaten(2.0, 3.0).copy(quartalsende = "2026-12-31")))
    }

    private val katalog = listOf(
        Catalog.Stock("SAP.DE", "SAP", "DE"),
        Catalog.Stock("AAPL", "Apple", "US"),
        Catalog.Stock("TSM", "Taiwan Semiconductor", "TW"),
        Catalog.Stock("NOVO-B.CO", "Novo Nordisk (B)", "DK"),
        Catalog.Stock("ASML.AS", "ASML", "NL"),
    )

    @Test
    fun notierungFinden() {
        assertEquals("SAP.DE", Quartalsueberblick.notierung("SAP", "SAP", emptySet(), emptySet(), katalog))
        assertEquals("AAPL", Quartalsueberblick.notierung("AAPL", "Apple", emptySet(), emptySet(), katalog))
        assertEquals("TSM", Quartalsueberblick.notierung("TSM", "Taiwan Semiconductor", emptySet(), emptySet(), katalog))
        assertEquals("NOVO-B.CO", Quartalsueberblick.notierung("NVO", "Novo Nordisk", emptySet(), emptySet(), katalog))
        assertEquals("SAP", Quartalsueberblick.notierung("SAP", "SAP", setOf("SAP"), emptySet(), katalog))
        assertEquals("SAP.F", Quartalsueberblick.notierung("SAP", "SAP", setOf("SAP.F"), setOf("SAP", "SAP.F", "SAP.DE"), katalog))
        assertEquals("ZZZZ", Quartalsueberblick.notierung("ZZZZ", "Zeta Widgets", emptySet(), emptySet(), katalog))
    }
}
