package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.i18n.MarktzeichenTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.pow

// Marktzeichen (0.78): Vierer-Indikator, Bodenzeichen, VIX-Abstand und Squeeze.
class Fassung078Z2MarktzeichenTest {

    private val tagMs = 86_400_000L
    private fun tag(j: Int, m: Int, d: Int = 1) = LocalDate.of(j, m, d).toEpochDay()

    // ------------------------------------------------------------ Vierer-Indikator

    @Test fun monatsTagWieDerKalender() {
        assertEquals(0L, Vierer.monatsTag(1970, 1))
        for (j in listOf(1999, 2000, 2015, 2024, 2026, 2100)) for (m in 1..12) assertEquals("$j-$m", tag(j, m), Vierer.monatsTag(j, m))
        assertEquals(tag(2019, 9, 18), Vierer.tagLesen("2019-09-18"))
        assertNull(Vierer.tagLesen("2019-09"))
        assertNull(Vierer.tagLesen("18.09.2019"))
    }

    // Aufbau wie die CSV des Datenportals (detail=dataonly): Schluesselspalten, dann TIME_PERIOD und OBS_VALUE
    private val zinsCsv = """
        KEY,FREQ,REF_AREA,CURRENCY,PROVIDER_FM,INSTRUMENT_FM,PROVIDER_FM_ID,DATA_TYPE_FM,TIME_PERIOD,OBS_VALUE
        FM.D.U2.EUR.4F.KR.DFR.LEV,D,U2,EUR,4F,KR,DFR,LEV,2025-06-09,2.25
        FM.D.U2.EUR.4F.KR.DFR.LEV,D,U2,EUR,4F,KR,DFR,LEV,2025-06-10,2.25
        FM.D.U2.EUR.4F.KR.DFR.LEV,D,U2,EUR,4F,KR,DFR,LEV,2025-06-11,2
        FM.D.U2.EUR.4F.KR.DFR.LEV,D,U2,EUR,4F,KR,DFR,LEV,2025-06-12,2
        FM.D.U2.EUR.4F.KR.DFR.LEV,D,U2,EUR,4F,KR,DFR,LEV,2025-06-13,
    """.trimIndent()

    @Test fun zinsStufenAusDerCsv() {
        val s = Vierer.zinsLesen(zinsCsv)
        assertEquals(listOf(tag(2025, 6, 9) to 2.25, tag(2025, 6, 11) to 2.0), s)
        assertEquals(s, Vierer.stufenLesen(Vierer.stufenText(s)))
        assertTrue(Vierer.zinsLesen("").isEmpty())
        assertTrue(Vierer.zinsLesen("A,B\n1,2").isEmpty())
    }

    @Test fun preisindexHinUndZurueck() {
        val monate = listOf(Kaufkraft.Monat("2025-12", 128.5), Kaufkraft.Monat("2026-01", 128.1))
        val i = Vierer.index(monate)
        assertEquals(128.5, i.getValue(2025 * 12 + 11), 1e-9)
        assertEquals(128.1, i.getValue(2026 * 12), 1e-9)
        assertEquals(2, i.size)
    }

    // Dollar je Euro, jeden Tag ein Kurs, faellt stetig: der Dollar steht immer hoeher als ein Jahr zuvor
    private fun fallend(von: Long, bis: Long) = (von..bis).map { it to 1.30 - (it - von) * 0.0001 }

    @Test fun vierFragenMitWertUndStand() {
        val stufen = listOf(tag(2024, 1) to 4.0, tag(2025, 6, 11) to 2.0)
        val k = 2026 * 12 + 10
        // Oktober 2026 gegen Oktober 2025 gegen Oktober 2024: 2 % nach 4 %
        val index = mapOf(k - 1 to 106.08, k - 13 to 104.0, k - 25 to 100.0)
        val kurse = fallend(tag(2025, 1), tag(2026, 11, 5))
        val f = Vierer.fragen(2026, 11, stufen, index, kurse)!!
        assertEquals(listOf(Vierer.ZINS, Vierer.INFLATION, Vierer.DOLLAR, Vierer.SAISON), f.map { it.schluessel })
        assertTrue(f.all { it.ja })
        assertEquals(2.0, f[0].wert, 1e-9); assertEquals(4.0, f[0].vergleich, 1e-9); assertEquals(tag(2025, 6, 11), f[0].stand)
        assertEquals(2.0, f[1].wert, 1e-6); assertEquals(4.0, f[1].vergleich, 1e-6); assertEquals(tag(2026, 10), f[1].stand)
        assertEquals(tag(2026, 11), f[2].stand)
        assertTrue(f[2].wert < f[2].vergleich)
        // Erhoehung statt Senkung, Sommer statt Winter
        val g = Vierer.fragen(2026, 7, listOf(tag(2024, 1) to 2.0, tag(2025, 6, 11) to 2.25), mapOf(k - 5 to 106.08, k - 17 to 104.0, k - 29 to 100.0), kurse)!!
        assertFalse(g[0].ja); assertTrue(g[1].ja); assertTrue(g[2].ja); assertFalse(g[3].ja)
        // Fehlt eine Groesse, gibt es keine Antwort
        assertNull(Vierer.fragen(2026, 11, stufen.take(1), index, kurse))
        assertNull(Vierer.fragen(2026, 11, stufen, emptyMap(), kurse))
        assertNull(Vierer.fragen(2026, 11, stufen, index, emptyList()))
        // Kurs aelter als zehn Tage zaehlt nicht
        assertNull(Vierer.fragen(2026, 11, stufen, index, fallend(tag(2025, 1), tag(2026, 10, 15))))
    }

    @Test fun signalAusDerZahlDerJa() {
        assertEquals(Vierer.Signal.INVESTIERT, Vierer.signal(4, null))
        assertEquals(Vierer.Signal.INVESTIERT, Vierer.signal(3, Vierer.Signal.NICHT_INVESTIERT))
        assertEquals(Vierer.Signal.NICHT_INVESTIERT, Vierer.signal(1, Vierer.Signal.INVESTIERT))
        assertEquals(Vierer.Signal.NICHT_INVESTIERT, Vierer.signal(0, null))
        assertEquals(Vierer.Signal.INVESTIERT, Vierer.signal(2, Vierer.Signal.INVESTIERT))
        assertEquals(Vierer.Signal.NICHT_INVESTIERT, Vierer.signal(2, Vierer.Signal.NICHT_INVESTIERT))
        assertNull(Vierer.signal(2, null))
    }

    // Inflation steigt jedes Jahr um einen Punkt (immer Nein), Dollar immer Ja, Zins bis Juni 2024 erhoeht, dann gesenkt.
    // Bis Juni 2024 also 1 Ja im Sommer und 2 im Winter, danach 2 im Sommer und 3 im Winter.
    private fun lauf(heute: Long): Vierer.Ergebnis? {
        val index = HashMap<Int, Double>()
        var w = 100.0
        for (k in 2020 * 12..2026 * 12 + 11) {
            if (k > 2020 * 12) w *= (1 + (k / 12 - 2019) / 100.0).pow(1.0 / 12)
            index[k] = w
        }
        val stufen = listOf(tag(2015, 1) to 0.0, tag(2022, 7, 27) to 4.0, tag(2024, 6, 12) to 3.75)
        return Vierer.rechnen(stufen, index, fallend(tag(2020, 1), tag(2026, 12, 31)), heute)
    }

    @Test fun letztesSignalGiltBeiZweiZuZwei() {
        val sommer = lauf(tag(2026, 8, 15))!!
        assertEquals(2026, sommer.jahr); assertEquals(8, sommer.monat)
        assertEquals(2, sommer.ja)
        assertTrue(sommer.uebernommen)
        assertEquals(Vierer.Signal.INVESTIERT, sommer.signal)
        assertEquals(2024, sommer.seitJahr); assertEquals(11, sommer.seitMonat)
        val winter = lauf(tag(2026, 1, 20))!!
        assertEquals(3, winter.ja)
        assertFalse(winter.uebernommen)
        assertEquals(Vierer.Signal.INVESTIERT, winter.signal)
        assertEquals(2024, winter.seitJahr); assertEquals(11, winter.seitMonat)
        // September 2024: Zins und Dollar Ja, das fruehere „nicht investiert“ vom August 2022 gilt weiter
        val vorher = lauf(tag(2024, 9, 10))!!
        assertEquals(2, vorher.ja)
        assertEquals(Vierer.Signal.NICHT_INVESTIERT, vorher.signal)
        assertEquals(2022, vorher.seitJahr); assertEquals(8, vorher.seitMonat)
        // Vor der ersten bekannten Zinsaenderung fehlt die Antwort
        assertNull(lauf(tag(2022, 6, 10)))
    }

    @Test fun ohneFrueheresSignalBleibtEsOffen() {
        // Nur der laufende Monat laesst sich rechnen und steht zwei zu zwei
        val k = 2026 * 12 + 6
        val e = Vierer.rechnen(
            listOf(tag(2026, 1) to 2.0, tag(2026, 6, 20) to 1.75), mapOf(k - 1 to 110.0, k - 13 to 104.0, k - 25 to 100.0),
            fallend(tag(2025, 6, 20), tag(2026, 7, 20)), tag(2026, 7, 20),
        )!!
        assertEquals(2, e.ja)
        assertNull(e.signal)
        assertEquals(0, e.seitJahr)
    }

    // ------------------------------------------------------------ Bodenzeichen

    @Test fun bodenSchwellen() {
        assertEquals(Boden.Lage.ERFUELLT, Boden.kgv(9.9)!!.lage)
        assertEquals(Boden.Lage.NAHE, Boden.kgv(13.0)!!.lage)
        assertEquals(Boden.Lage.FERN, Boden.kgv(22.0)!!.lage)
        assertNull(Boden.kgv(null)); assertNull(Boden.kgv(0.0)); assertNull(Boden.kgv(Double.NaN))
        assertEquals(Boden.Lage.ERFUELLT, Boden.praemie(5.0)!!.lage)
        assertEquals(Boden.Lage.NAHE, Boden.praemie(3.5)!!.lage)
        assertEquals(Boden.Lage.FERN, Boden.praemie(-0.5)!!.lage)
        assertEquals(Boden.Lage.ERFUELLT, Boden.schwankung(31.0)!!.lage)
        assertEquals(Boden.Lage.NAHE, Boden.schwankung(22.0)!!.lage)
        assertEquals(Boden.Lage.FERN, Boden.schwankung(14.0)!!.lage)
        assertEquals(Boden.Lage.ERFUELLT, Boden.rueckgang(-31.0, null)!!.lage)
        assertEquals(Boden.Lage.ERFUELLT, Boden.rueckgang(-10.0, -34.0)!!.lage)
        assertEquals(Boden.Lage.NAHE, Boden.rueckgang(-21.0, -5.0)!!.lage)
        assertEquals(Boden.Lage.NAHE, Boden.rueckgang(-5.0, -25.0)!!.lage)
        assertEquals(Boden.Lage.FERN, Boden.rueckgang(5.0, 20.0)!!.lage)
        assertEquals(-34.0, Boden.rueckgang(-10.0, -34.0)!!.zusatz!!, 1e-9)
        assertNull(Boden.rueckgang(null, -40.0))
    }

    @Test fun bodenVeraenderungUndGesamtbild() {
        // Drei Jahre Tageskurse: erst flach bei 100, im letzten Jahr auf 65
        val kurse = (0..1095).map { i -> i * tagMs to if (i <= 730) 100.0 else 100.0 - (i - 730) * 35.0 / 365 }
        assertEquals(-35.0, Boden.veraenderung(kurse, 365)!!, 1e-6)
        assertEquals(-35.0, Boden.veraenderung(kurse, 730)!!, 1e-6)
        // Reihe zu kurz fuer zwei Jahre
        assertNull(Boden.veraenderung(kurse.takeLast(400), 730))
        assertNull(Boden.veraenderung(emptyList(), 365))
        val alle = Boden.zeichen(9.0, 6.0, kurse, 35.0)
        assertEquals(Boden.ALLE, alle.map { it.schluessel })
        assertTrue(alle.all { it.lage == Boden.Lage.ERFUELLT })
        assertEquals(Boden.Stufe.VIELE, Boden.gesamt(alle))
        val ruhig = Boden.zeichen(24.0, 0.5, (0..800).map { it * tagMs to 100.0 + it }, 14.0)
        assertEquals(Boden.Stufe.KEINE, Boden.gesamt(ruhig))
        assertEquals(Boden.Stufe.EINZELNE, Boden.gesamt(Boden.zeichen(24.0, 0.5, kurse, 14.0)))
        assertEquals(Boden.Stufe.EINZELNE, Boden.gesamt(listOfNotNull(Boden.kgv(13.0), Boden.praemie(3.5), Boden.schwankung(12.0))))
        assertEquals(Boden.Stufe.KEINE, Boden.gesamt(listOfNotNull(Boden.kgv(13.0), Boden.praemie(1.0), Boden.schwankung(12.0))))
        // Ohne KGV fehlen zwei Zeichen, zwei reichen nicht fuer ein Gesamtbild
        val wenig = Boden.zeichen(null, null, kurse, 35.0)
        assertEquals(listOf(Boden.RUECKGANG, Boden.SCHWANKUNG), wenig.map { it.schluessel })
        assertNull(Boden.gesamt(wenig))
    }

    // ------------------------------------------------------------ VIX-Abstand

    private fun reihe(werte: List<Double>) = werte.mapIndexed { i, w -> i * tagMs to w }

    @Test fun vixAbstandAlsRuecksetzerZeichen() {
        val vixHoch = reihe(List(29) { 15.0 } + 20.0)
        // Index steigt 298 Tage und gibt an den letzten beiden nach: ueber der 200-Tage-Linie, unter dem 5-Tage-Schnitt
        val ruecksetzer = reihe(List(298) { 100.0 + it } + listOf(394.0, 390.0))
        val z = Fruehwarnung.vixAbstand(vixHoch, ruecksetzer)!!
        assertEquals(Fruehwarnung.VIX_ABSTAND, z.schluessel)
        assertEquals(Ampel.GELB, z.ampel)
        assertEquals((20.0 / ((11 * 15.0 + 20.0) / 12) - 1) * 100, z.wert, 1e-9)
        assertEquals(z.wert, z.verlauf.last().second, 1e-9)
        assertEquals(30 - 11, z.verlauf.size)
        // Index steigt weiter: kein kurzfristiger Rueckgang
        assertEquals(Ampel.GRUEN, Fruehwarnung.vixAbstand(vixHoch, reihe(List(300) { 100.0 + it }))!!.ampel)
        // Index unter der 200-Tage-Linie: kein Aufwaertstrend
        assertEquals(Ampel.GRUEN, Fruehwarnung.vixAbstand(vixHoch, reihe(List(300) { 500.0 - it }))!!.ampel)
        // VIX nur 5 % ueber seinem Schnitt
        val vixRuhig = reihe(List(29) { 15.0 } + 15.8)
        val ruhig = Fruehwarnung.vixAbstand(vixRuhig, ruecksetzer)!!
        assertEquals(Ampel.GRUEN, ruhig.ampel)
        assertTrue(ruhig.wert > 0 && ruhig.wert < 10)
        // Zu kurze Reihen
        assertNull(Fruehwarnung.vixAbstand(vixHoch.take(11), ruecksetzer))
        assertNull(Fruehwarnung.vixAbstand(vixHoch, ruecksetzer.take(199)))
        assertTrue(Fruehwarnung.VIX_ABSTAND in Fruehwarnung.ALLE)
        // Zaehlt nicht in die Gesamtstimmung
        assertNull(Gesamtstimmung.ausZeichen(z))
    }

    // ------------------------------------------------------------ Squeeze

    // 60 ruhige Kerzen (Schluss pendelt um 100, Tagesspanne 2), dann acht Kerzen mit je schritt.
    private fun ausbruch(schritt: Double): Triple<List<Double>, List<Double>, List<Double>> {
        val ruhig = List(60) { 100.0 + if (it % 2 == 0) 0.05 else -0.05 }
        val lauf = List(8) { 100.0 + (it + 1) * schritt }
        val c = ruhig + lauf
        return Triple(c.map { it + 1 }, c.map { it - 1 }, c)
    }

    @Test fun squeezeEngeUndAustritt() {
        val (h, l, c) = ausbruch(3.0)
        val r = Squeeze.rechnen(h, l, c)
        assertEquals(c.size, r.eng.size); assertEquals(c.size, r.momentum.size)
        assertNull(r.eng[5])
        // Ruhige Phase: Baender im Kanal
        assertTrue((25..59).all { r.eng[it] == true })
        val ruhe = Squeeze.deutung(Squeeze.rechnen(h.take(60), l.take(60), c.take(60)))!!
        assertEquals("eng", ruhe.lage); assertEquals(0, ruhe.stufe)
        assertTrue(ruhe.wert >= 20)
        // Ausbruch nach oben: ein Signal kurz nach dem Beginn, Richtung aufwaerts
        val s = Squeeze.signale(r)
        assertEquals(1, s.size)
        assertTrue(s[0].index in 60..63)
        assertTrue(s[0].auf)
        val d = Squeeze.deutung(r)!!
        assertEquals(Squeeze.ART, d.art); assertEquals(1, d.stufe); assertEquals("auf", d.lage)
        assertEquals((c.size - 1 - s[0].index).toDouble(), d.wert, 1e-9)
        assertEquals(Indikatoren.Tendenz.POSITIV, Indikatoren.tendenz(d))
        // Punkte auf der Nulllinie nur in der engen Phase
        val p = Squeeze.punkte(r)
        assertEquals(0.0, p[40]!!, 0.0); assertNull(p[c.size - 1])
    }

    @Test fun squeezeAustrittNachUnten() {
        val (h, l, c) = ausbruch(-3.0)
        val r = Squeeze.rechnen(h, l, c)
        val s = Squeeze.signale(r)
        assertEquals(1, s.size)
        assertFalse(s[0].auf)
        val d = Squeeze.deutung(r)!!
        assertEquals(-1, d.stufe); assertEquals("ab", d.lage)
    }

    @Test fun squeezeOhneEngePhaseUndZuKurz() {
        // Stetiger Anstieg: Baender weiter als der Kanal, kein Signal, Deutung offen
        val c = List(300) { 100.0 + it }
        val r = Squeeze.rechnen(c.map { it + 1 }, c.map { it - 1 }, c)
        assertTrue(Squeeze.signale(r).isEmpty())
        val d = Squeeze.deutung(r)!!
        assertEquals("offen", d.lage); assertEquals(0, d.stufe)
        assertNull(Squeeze.deutung(Squeeze.rechnen(c.take(25).map { it + 1 }, c.take(25).map { it - 1 }, c.take(25))))
        assertNull(Squeeze.deutung(Squeeze.rechnen(emptyList(), emptyList(), emptyList())))
        // Ein Austritt, der laenger als das Fenster zurueckliegt, gibt keine Richtung mehr
        val (h, l, k) = ausbruch(3.0)
        val lang = k + List(12) { k.last() + (it + 1) * 3.0 }
        val spaet = Squeeze.deutung(Squeeze.rechnen(h + lang.takeLast(12).map { it + 1 }, l + lang.takeLast(12).map { it - 1 }, lang))!!
        assertEquals(0, spaet.stufe); assertEquals("offen", spaet.lage)
    }

    @Test fun squeezeImChartUndInDerGesamtrichtung() {
        val i = ChartIndikatoren.Indikator.von("squeeze")!!
        assertEquals(ChartIndikatoren.Ort.UNTEN, i.ort)
        for (n in listOf(0, 1, 5, 30, 300)) {
            val k = List(n) { j -> val c = 100.0 + j % 7; Candle(j * tagMs, c, c + 1, c - 1, c, 1000.0) }
            val e = ChartIndikatoren.rechnen(i, k) { it / tagMs }
            assertEquals(2, e.linien.size)
            e.linien.forEach { assertEquals(n, it.werte.size) }
            assertEquals(ChartIndikatoren.Art.BALKEN, e.linien[0].art)
            assertEquals(ChartIndikatoren.Art.PUNKTE, e.linien[1].art)
        }
        // Gesamtrichtung: der Squeeze zaehlt nur nach einem Austritt als eigenes Signal
        val s = List(300) { 100.0 + it }
        val z = Gesamtrichtung.zusatz(s, s.map { it + 1 }, s.map { it - 1 }, List(300) { 1000.0 })
        assertEquals(Gesamtrichtung.ZUSATZ.toSet() - "squeeze", z.map { it.art }.toSet())
        assertTrue("squeeze" in Gesamtrichtung.ZUSATZ)
        assertEquals("squeeze", Gesamtrichtung.familie("squeeze"))
        val (h, l, c) = ausbruch(3.0)
        val mit = Gesamtrichtung.zusatz(c, h, l, List(c.size) { 0.0 }).first { it.art == "squeeze" }
        assertEquals(1, mit.stufe)
    }

    // ------------------------------------------------------------ Texte

    @Test fun texteDeutschUndEnglisch() {
        val schluessel = listOf(
            "v.kopf", "v.signal.0", "v.signal.1", "v.signal.x", "v.wert", "v.satz.seit", "v.satz.gleich", "v.satz.offen", "v.ja", "v.nein", "v.regel",
            "b.titel", "b.info", "b.satz", "b.zwei", "fw.name", "fw.lage.0", "fw.lage.1", "fw.erklaerung", "sq.name", "sq.info",
        ) + listOf(Vierer.ZINS, Vierer.INFLATION, Vierer.DOLLAR, Vierer.SAISON).map { "v.f.$it" } +
            listOf(Vierer.ZINS, Vierer.INFLATION, Vierer.DOLLAR).map { "v.h.$it" } +
            Boden.Stufe.entries.map { "b.stufe.${it.ordinal}" } + Boden.Lage.entries.map { "b.l.${it.ordinal}" } +
            Boden.ALLE.flatMap { k -> listOf("b.n.$k", "b.e.$k") + Boden.Lage.entries.map { "b.$k.${it.ordinal}" } }
        for (k in schluessel) for (de in listOf(true, false)) {
            val t = MarktzeichenTexte.text(de, k, listOf("A", "B", "C"))
            assertTrue("$k fehlt ($de)", t.isNotBlank())
            assertFalse("$k: Platzhalter offen", Regex("""\{\d}""").containsMatchIn(t))
            assertFalse("$k: Gedankenstrich", t.contains('–') || t.contains('—'))
            assertFalse("$k: Auslassung", t.contains('…') || t.contains("..."))
        }
        assertEquals("Vierer-Indikator (DAX): investiert", MarktzeichenTexte.text(true, "v.kopf", listOf(MarktzeichenTexte.text(true, "v.signal.0", emptyList()))))
        assertEquals("", MarktzeichenTexte.text(true, "gibt.es.nicht", emptyList()))
    }
}
