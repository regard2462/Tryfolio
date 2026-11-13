package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Paket q78b (0.76): Bewegung zu den Zahlen, Dividendenserie, weitere Alarmarten.
class Fassung078BTest {
    private val basis = LocalDate.of(2025, 1, 1).toEpochDay()

    // Kerze an Tag basis+i, 17 Uhr UTC (mittags in New York)
    private fun kerze(i: Int, close: Double, volumen: Double) =
        Candle((basis + i) * 86_400_000L + 17 * 3_600_000L, close, close, close, close, volumen)

    private fun ruhig(n: Int) = (0 until n).map { kerze(it, 100.0, 1000.0) }.toMutableList()

    // ------------------------------------------------------------ Reaktion

    @Test fun nachBoersenschlussReagiertDerFolgetag() {
        val k = ruhig(40)
        k[20] = kerze(20, 100.0, 1500.0)
        k[21] = kerze(21, 110.0, 5000.0)
        for (i in 22 until 40) k[i] = kerze(i, 110.0, 1000.0)
        val r = Zahlenreaktion.reaktion(k, basis + 20)!!
        assertEquals(basis + 21, r.tag)
        assertEquals(10.0, r.schritt, 1e-9)
    }

    @Test fun vorBoersenbeginnReagiertDerTagSelbst() {
        val k = ruhig(40)
        k[20] = kerze(20, 95.0, 5000.0)
        k[21] = kerze(21, 95.0, 1500.0)
        for (i in 22 until 40) k[i] = kerze(i, 95.0, 1000.0)
        val r = Zahlenreaktion.reaktion(k, basis + 20)!!
        assertEquals(basis + 20, r.tag)
        assertEquals(-5.0, r.schritt, 1e-9)
    }

    @Test fun ohneKerzeNaehAmTerminKeineReaktion() {
        assertNull(Zahlenreaktion.reaktion(ruhig(40), basis + 200))
        assertNull(Zahlenreaktion.reaktion(ruhig(1), basis))
    }

    @Test fun sprungBrauchtDoppeltenUmsatz() {
        val k = ruhig(100)
        k[70] = kerze(70, 104.0, 1800.0)
        assertNull(Zahlenreaktion.sprung(k, basis + 70))
        k[70] = kerze(70, 104.0, 2500.0)
        val r = Zahlenreaktion.sprung(k, basis + 65)!!
        assertEquals(basis + 70, r.tag)
        assertEquals(4.0, r.schritt, 1e-9)
        assertTrue(!r.genau)
    }

    @Test fun achtTermineWerdenRueckwaertsImQuartalsabstandGefunden() {
        val k = ruhig(800)
        val spruenge = listOf(750, 659, 568, 477, 386, 295, 204, 113)
        var kurs = 100.0
        val liste = (0 until 800).map { i ->
            if (i in spruenge) kurs *= 1.05
            kerze(i, kurs, if (i in spruenge) 6000.0 else 1000.0)
        }
        val exakt = spruenge.take(4).map { basis + it }
        val r = Zahlenreaktion.reaktionen(liste, exakt)
        assertEquals(8, r.size)
        assertTrue(r.all { Math.abs(it.schritt - 5.0) < 1e-6 })
        assertEquals(basis + 750, r.first().tag)
        assertEquals(4, r.count { it.genau })
        assertEquals(k.size, 800)
    }

    @Test fun ueblichIstMedianDerBetraegeAbDreiTerminen() {
        fun r(tag: Long, s: Double) = Zahlenreaktion.Reaktion(tag, s, true)
        assertNull(Zahlenreaktion.ueblich(listOf(r(1, 3.0), r(100, -5.0))))
        val l = listOf(r(400, 2.0), r(300, -4.0), r(200, 10.0), r(100, -6.0))
        assertEquals(5.0, Zahlenreaktion.ueblich(l)!!, 1e-9)
        // der eigene Termin zaehlt nicht mit
        assertEquals(6.0, Zahlenreaktion.ueblich(l, ohneTag = 400)!!, 1e-9)
    }

    @Test fun tatsaechlichAmTerminOderDanach() {
        val l = listOf(Zahlenreaktion.Reaktion(1001, -7.5, true), Zahlenreaktion.Reaktion(900, 3.0, true))
        assertEquals(-7.5, Zahlenreaktion.tatsaechlich(l, 1000)!!, 1e-9)
        assertNull(Zahlenreaktion.tatsaechlich(l, 1010))
    }

    // ------------------------------------------------------------ erwartete Bewegung

    private val tag = 86_400L
    private val verfall1 = (basis + 10) * tag
    private val verfall2 = (basis + 17) * tag

    private fun kette(mitPreise: Boolean) = listOf(
        Kontrakt(true, 100.0, verfall1, 0.0, if (mitPreise) 2.9 else 0.0, if (mitPreise) 3.1 else 0.0, 0, 0, 0.5),
        Kontrakt(false, 100.0, verfall1, 0.0, if (mitPreise) 2.4 else 0.0, if (mitPreise) 2.6 else 0.0, 0, 0, 0.5),
        Kontrakt(true, 105.0, verfall1, 1.0, 1.0, 1.0, 0, 0, 0.5),
        Kontrakt(true, 100.0, verfall2, 0.0, 5.0, 5.0, 0, 0, 0.4),
        Kontrakt(false, 100.0, verfall2, 0.0, 4.0, 4.0, 0, 0, 0.4),
    )

    @Test fun straddleDesErstenVerfallsNachDemTermin() {
        val e = Zahlenreaktion.erwartet(100.5, kette(true), basis + 9, (basis + 5) * tag)!!
        assertEquals(Zahlenreaktion.Art.STRADDLE, e.art)
        assertEquals(5.5 / 100.5 * 100.0, e.prozent, 1e-9)
        assertEquals(verfall1, e.verfall)
    }

    @Test fun nachBoersenschlussZaehltDerVerfallAmTermintagNicht() {
        val e = Zahlenreaktion.erwartet(100.0, kette(true), basis + 11, (basis + 5) * tag)!!
        assertEquals(verfall2, e.verfall)
        assertEquals(9.0, e.prozent, 1e-9)
    }

    @Test fun ohnePreiseAusDerImpliziertenVolatilitaet() {
        val e = Zahlenreaktion.erwartet(100.0, kette(false), basis + 9, (basis + 5) * tag)!!
        assertEquals(Zahlenreaktion.Art.IV, e.art)
        assertEquals(0.8 * 0.5 * Math.sqrt(5.0 / 365.0) * 100.0, e.prozent, 1e-9)
    }

    @Test fun keinVerfallNachDemTerminKeineErwartung() {
        assertNull(Zahlenreaktion.erwartet(100.0, kette(true), basis + 400, (basis + 5) * tag))
        assertNull(Zahlenreaktion.erwartet(0.0, kette(true), basis + 9, 0))
    }

    @Test fun ueberraschungInProzent() {
        assertEquals(10.0, Zahlenreaktion.ueberraschung(1.1, 1.0)!!, 1e-9)
        assertEquals(50.0, Zahlenreaktion.ueberraschung(-0.5, -1.0)!!, 1e-9)
        assertNull(Zahlenreaktion.ueberraschung(1.0, 0.0))
        assertNull(Zahlenreaktion.ueberraschung(null, 1.0))
    }

    // ------------------------------------------------------------ Nasdaq-Tabelle

    @Test fun ueberraschungstabelleDerNasdaq() {
        val text = """{"data":{"symbol":"aapl","earningsSurpriseTable":{"rows":[
            {"fiscalQtrEnd":"Jun 2026","dateReported":"7/30/2026","eps":1.91,"consensusForecast":"1.88","percentageSurprise":"1.6"},
            {"fiscalQtrEnd":"Sep 2026","dateReported":"11/2/2026","eps":2.05,"consensusForecast":"1.98","percentageSurprise":"3.54"}]}},
            "status":{"rCode":200}}"""
        val l = NasdaqKalender.ueberraschungLesen(text)!!
        assertEquals(2, l.size)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), l[0].tag)
        assertEquals(1.98, l[0].erwartet!!, 1e-9)
        assertEquals(3.54, l[0].prozent!!, 1e-9)
        assertEquals(0, NasdaqKalender.ueberraschungLesen("""{"data":{"symbol":"x"},"status":{"rCode":200}}""")!!.size)
    }

    // ------------------------------------------------------------ Dividende

    private fun ex(jahr: Int, betrag: Double) =
        MarketApi.DividendEvent(LocalDate.of(jahr, 3, 10).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), betrag)

    @Test fun jahreInFolgeZaehltSteigendeJahressummen() {
        val voll = listOf(2019 to 1.0, 2020 to 1.1, 2021 to 1.2, 2022 to 1.2, 2023 to 1.3, 2024 to 1.4, 2025 to 1.5)
        assertEquals(3, DividendenUeberblick.jahreInFolge(voll))
        assertEquals(0, DividendenUeberblick.jahreInFolge(listOf(2024 to 1.5, 2025 to 1.4)))
        // eine Luecke im Kalender beendet die Folge
        assertEquals(1, DividendenUeberblick.jahreInFolge(listOf(2021 to 1.0, 2023 to 1.1, 2024 to 1.2)))
        assertEquals(0, DividendenUeberblick.jahreInFolge(emptyList()))
    }

    @Test fun fuenfJahresWachstumBrauchtSechsVolleJahre() {
        val voll = (2020..2025).mapIndexed { i, j -> j to 1.0 * Math.pow(1.1, i.toDouble()) }
        assertEquals(10.0, DividendenUeberblick.wachstumFuenf(voll)!!, 1e-6)
        assertNull(DividendenUeberblick.wachstumFuenf(voll.takeLast(5)))
        assertNull(DividendenUeberblick.wachstumFuenf(voll.filter { it.first != 2022 } + (2019 to 1.0)))
    }

    @Test fun letzterKauftagUeberspringtWochenendeUndFeiertag() {
        // Montag 30.11.2026: davor Freitag 27.11.
        assertEquals(LocalDate.of(2026, 11, 27).toEpochDay(), Alarmarten.letzterKauftag(LocalDate.of(2026, 11, 30).toEpochDay(), "US"))
        // Freitag 27.11.2026: Donnerstag ist Thanksgiving, also Mittwoch
        assertEquals(LocalDate.of(2026, 11, 25).toEpochDay(), Alarmarten.letzterKauftag(LocalDate.of(2026, 11, 27).toEpochDay(), "US"))
        assertEquals("US", Alarmarten.land("AAPL"))
        assertEquals("DE", Alarmarten.land("SAP.DE"))
    }

    @Test fun werteLiefernSerieUndKauftag() {
        val jetzt = LocalDate.of(2026, 6, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val verlauf = (2019..2025).map { ex(it, 1.0 + (it - 2019) * 0.1) }
        val exMs = LocalDate.of(2026, 6, 15).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val w = DividendenUeberblick.werte(verlauf, 2.0, 40.0, exMs, 0L, 50.0, now = jetzt, zone = java.time.ZoneOffset.UTC)!!
        assertEquals(6, w.jahreErhoeht)
        assertNotNull(w.wachstum5)
        assertEquals(LocalDate.of(2026, 6, 12).toEpochDay(), w.kauftag!! / 86_400_000L)
    }

    @Test fun erhoehungGegenDieLetzteZahlung() {
        val h = listOf(ex(2024, 0.50), ex(2025, 0.52))
        val exMs = LocalDate.of(2026, 3, 10).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals((0.55 / 0.52 - 1) * 100, DividendenUeberblick.erhoehung(0.55, exMs, h)!!, 1e-9)
        assertNull(DividendenUeberblick.erhoehung(0.52, exMs, h))
        assertNull(DividendenUeberblick.erhoehung(0.60, exMs, emptyList()))
    }

    // ------------------------------------------------------------ Alarmarten

    private val heute = 20_000L
    private fun alarm(art: String, zustand: Int = 0, gemeldet: Long = 0L, merk: String = "") =
        PriceAlert("a1", "AAPL", "Apple", 0.0, above = true, createdAt = (heute - 3) * 86_400_000L, art = art, zustand = zustand, gemeldetAm = gemeldet, merk = merk)

    private fun daten(
        kurs: Double = 100.0, volumen: Double = 1000.0, schnitt: Double? = 100.0, normal: Double? = 1000.0,
        exTag: Long? = null, insider: List<SecInsider.Geschaeft> = emptyList(), aktuell: Boolean = true,
    ) = Alarmarten.Daten(kurs, volumen, schnitt, normal, exTag, insider, heute, "US", aktuell)

    @Test fun schnittUndNormalvolumen() {
        assertNull(Alarmarten.schnitt(List(49) { 1.0 }))
        assertEquals(2.0, Alarmarten.schnitt(List(10) { 9.0 } + List(50) { 2.0 })!!, 1e-9)
        assertNull(Alarmarten.normalVolumen(List(5) { kerze(it, 1.0, 10.0) }))
        assertEquals(10.0, Alarmarten.normalVolumen(List(60) { kerze(it, 1.0, 10.0) })!!, 1e-9)
    }

    @Test fun schnittKreuzenMeldetBeimSeitenwechselEinmalJeTag() {
        // erste Pruefung merkt nur die Seite
        val erst = Alarmarten.pruefen(alarm(Alarmarten.SCHNITT), daten(kurs = 101.0))
        assertTrue(!erst.gemeldet)
        assertEquals(1, erst.neu.zustand)
        // gleiche Seite: nichts
        assertTrue(!Alarmarten.pruefen(erst.neu, daten(kurs = 103.0)).gemeldet)
        // Wechsel nach unten
        val unten = Alarmarten.pruefen(erst.neu, daten(kurs = 99.0))
        assertTrue(unten.gemeldet)
        assertEquals("ALERT_SMA_DOWN", unten.tag)
        assertEquals(100.0, unten.extra, 1e-9)
        assertEquals(-1, unten.neu.zustand)
        assertEquals(heute, unten.neu.gemeldetAm)
        // Wechsel am selben Tag wieder nach oben: Seite merken, nicht noch einmal melden
        val oben = Alarmarten.pruefen(unten.neu, daten(kurs = 102.0))
        assertTrue(!oben.gemeldet)
        assertEquals(1, oben.neu.zustand)
        // ohne aktuellen Kurs oder Schnitt nichts
        assertTrue(!Alarmarten.pruefen(erst.neu, daten(kurs = 99.0, aktuell = false)).gemeldet)
        assertTrue(!Alarmarten.pruefen(erst.neu, daten(kurs = 99.0, schnitt = null)).gemeldet)
    }

    @Test fun volumenMeldetAbDemDoppeltenEinmalJeTag() {
        assertTrue(!Alarmarten.pruefen(alarm(Alarmarten.VOLUMEN), daten(volumen = 1999.0)).gemeldet)
        val e = Alarmarten.pruefen(alarm(Alarmarten.VOLUMEN), daten(volumen = 2500.0))
        assertTrue(e.gemeldet)
        assertEquals(2.5, e.extra, 1e-9)
        assertEquals("ALERT_VOLUME", e.tag)
        assertTrue(!Alarmarten.pruefen(e.neu, daten(volumen = 3000.0)).gemeldet)
        assertTrue(Alarmarten.pruefen(e.neu, daten(volumen = 3000.0).copy(heute = heute + 1)).gemeldet)
    }

    @Test fun exDividendeErinnertEinenTagVorDemLetztenKauftagEinmal() {
        // Ex-Tag Montag 30.11.2026, letzter Kauftag Freitag 27.11.
        val ex = LocalDate.of(2026, 11, 30).toEpochDay()
        val kauftag = LocalDate.of(2026, 11, 27).toEpochDay()
        val a = alarm(Alarmarten.EXDIV)
        assertTrue(!Alarmarten.pruefen(a, daten(exTag = ex).copy(heute = kauftag - 2)).gemeldet)
        val e = Alarmarten.pruefen(a, daten(exTag = ex).copy(heute = kauftag - 1))
        assertTrue(e.gemeldet)
        assertEquals(kauftag.toDouble(), e.extra, 1e-9)
        assertEquals(ex.toString(), e.neu.merk)
        // fuer denselben Ex-Tag nur einmal
        assertTrue(!Alarmarten.pruefen(e.neu, daten(exTag = ex).copy(heute = kauftag)).gemeldet)
        // nach dem Ex-Tag nichts mehr, ohne Ex-Tag nichts
        assertTrue(!Alarmarten.pruefen(a, daten(exTag = ex).copy(heute = ex)).gemeldet)
        assertTrue(!Alarmarten.pruefen(a, daten(exTag = null)).gemeldet)
    }

    private fun geschaeft(a: String, f: String, kauf: Boolean = true, wert: Double = 250_000.0, k: String = "AAPL") =
        SecInsider.Geschaeft(a = a, k = k, e = "Apple", p = "Jane Doe", kauf = kauf, d = f, f = f, wert = wert)

    @Test fun insiderKaufMeldetNeueMeldungenSeitDemAnlegen() {
        val seit = LocalDate.ofEpochDay(heute - 3).toString()
        val alt = geschaeft("0001", LocalDate.ofEpochDay(heute - 10).toString())
        val verkauf = geschaeft("0002", LocalDate.ofEpochDay(heute - 1).toString(), kauf = false)
        val fremd = geschaeft("0003", LocalDate.ofEpochDay(heute - 1).toString(), k = "MSFT")
        val neu1 = geschaeft("0004", LocalDate.ofEpochDay(heute - 2).toString(), wert = 90_000.0)
        val neu2 = geschaeft("0005", LocalDate.ofEpochDay(heute - 1).toString(), wert = 400_000.0)
        assertTrue(seit < neu1.f)
        assertTrue(!Alarmarten.pruefen(alarm(Alarmarten.INSIDER), daten(insider = listOf(alt, verkauf, fremd))).gemeldet)
        val e = Alarmarten.pruefen(alarm(Alarmarten.INSIDER), daten(insider = listOf(alt, verkauf, fremd, neu1, neu2)))
        assertTrue(e.gemeldet)
        assertEquals(400_000.0, e.extra, 1e-9)
        assertEquals("ALERT_INSIDER", e.tag)
        // die ältere neue Meldung kommt danach nicht mehr
        assertTrue(!Alarmarten.pruefen(e.neu, daten(insider = listOf(neu1, neu2))).gemeldet)
        // eine noch neuere meldet wieder
        val neu3 = geschaeft("0006", LocalDate.ofEpochDay(heute).toString())
        assertTrue(Alarmarten.pruefen(e.neu, daten(insider = listOf(neu1, neu2, neu3))).gemeldet)
    }

    @Test fun kursmarkenPruefungLaesstWeitereArtenUnberuehrt() {
        val quote = mapOf("AAPL" to Quote("AAPL", "Apple", price = 100.0, prevClose = 100.0, updatedAt = 1L, marketTime = 1L))
        val acc = Account(alerts = listOf(alarm(Alarmarten.SCHNITT)))
        val (nach, treffer) = Trading.checkAlerts(acc, quote, 1L)
        assertTrue(treffer.isEmpty())
        assertEquals(acc, nach)
    }

    @Test fun artenMeldenBuchtEreignisUndBehaeltDenAlarm() {
        val jetzt = 1_800_000_000_000L
        val a = alarm(Alarmarten.VOLUMEN)
        val e = Alarmarten.pruefen(a, daten(volumen = 3000.0))
        val kurse = mapOf("AAPL" to Quote("AAPL", "Apple", price = 123.0))
        val (nach, gemeldet) = Trading.artenMelden(Account(alerts = listOf(a)), mapOf(a.id to e), kurse, jetzt)
        assertEquals(1, gemeldet.size)
        assertEquals(1, nach.alerts.size)
        assertEquals(heute, nach.alerts[0].gemeldetAm)
        val tx = nach.transactions.last()
        assertEquals("ALERT_VOLUME", tx.tag)
        assertEquals(123.0, tx.price, 1e-9)
        assertEquals(3.0, tx.extra, 1e-9)
        // nichts zu melden: Konto bleibt gleich
        val still = Alarmarten.pruefen(a, daten(volumen = 10.0))
        val (gleich, keine) = Trading.artenMelden(Account(alerts = listOf(a)), mapOf(a.id to still), kurse, jetzt)
        assertTrue(keine.isEmpty())
        assertEquals(Account(alerts = listOf(a)), gleich)
    }
}
