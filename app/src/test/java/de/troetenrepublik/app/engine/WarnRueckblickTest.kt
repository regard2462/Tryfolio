package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.WarnRueckblick.Reihe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin

// [0.73] Rueckblick „Haette man es sehen koennen?“: Ereignisse, Auswertung, Gegenrechnung und vor allem: kein Blick in die
// Zukunft. Alles mit erfundenen Reihen, ohne Netz.
class WarnRueckblickTest {

    private val T0 = 10_000L

    // Taegliche erfundene Reihe: Messtage T0 bis T0+n, Wert aus f
    private fun taeglich(n: Int, verzug: Int = 0, maxAlter: Int = 10, f: (Int) -> Double) =
        Reihe(LongArray(n) { T0 + it }, DoubleArray(n) { f(it) }, verzug, maxAlter)

    // Monatsreihe (alle 30 Tage)
    private fun monatlich(n: Int, verzug: Int, maxAlter: Int, f: (Int) -> Double) =
        Reihe(LongArray(n) { T0 + it * 30L }, DoubleArray(n) { f(it) }, verzug, maxAlter)

    // Quartalsreihe (alle 91 Tage) und Wochenreihe
    private fun quartal(n: Int, verzug: Int, maxAlter: Int, f: (Int) -> Double) =
        Reihe(LongArray(n) { T0 + it * 91L }, DoubleArray(n) { f(it) }, verzug, maxAlter)

    private fun woche(n: Int, verzug: Int, maxAlter: Int, f: (Int) -> Double) =
        Reihe(LongArray(n) { T0 + it * 7L }, DoubleArray(n) { f(it) }, verzug, maxAlter)

    // Alle Eingangsreihen mit Schwankungen, die manche Zeichen ausloesen
    private fun alle(n: Int, garbageAb: Long? = null): Map<String, Reihe> {
        fun g(r: Reihe, ab: Long?): Reihe = if (ab == null) r else Reihe(
            r.tage, DoubleArray(r.werte.size) { i -> if (r.tage[i] + r.verzug > ab) 1e9 * (1 + i % 3) - 5e8 else r.werte[i] }, r.verzug, r.maxAlter,
        )
        val sp = taeglich(n) { 1000 * (1 + 0.0008 * it + 0.25 * sin(it / 150.0)) }
        return mapOf(
            WarnRueckblick.R_KURVE to taeglich(n) { 1.5 * sin(it / 220.0) },
            WarnRueckblick.R_KURVE_2J to taeglich(n) { 1.2 * sin(it / 200.0 + 1) },
            WarnRueckblick.R_ARBEIT to monatlich(n / 30 + 1, WarnRueckblick.VERZUG_ARBEIT, 100) { 5 + 1.5 * sin(it / 9.0) + it * 0.01 },
            WarnRueckblick.R_VIX to taeglich(n) { 22 + 12 * sin(it / 60.0) },
            WarnRueckblick.R_VIX3M to taeglich(n) { 22 + 8 * sin(it / 70.0) },
            WarnRueckblick.R_INDEX to sp,
            WarnRueckblick.R_SP to sp,
            WarnRueckblick.R_NFCI to taeglich(n, WarnRueckblick.VERZUG_NFCI, 21) { 1.2 * sin(it / 100.0) },
            WarnRueckblick.R_CAPE to monatlich(n / 30 + 1, WarnRueckblick.VERZUG_CAPE, 70) { 24 + 10 * sin(it / 11.0) },
            WarnRueckblick.R_GEWINN to monatlich(n / 30 + 1, WarnRueckblick.VERZUG_GEWINN, 200) { 55 + 5 * sin(it / 7.0) },
            WarnRueckblick.R_ZINS10 to taeglich(n) { 3 + 2 * sin(it / 300.0) },
            WarnRueckblick.R_CLI to monatlich(n / 30 + 1, WarnRueckblick.VERZUG_CLI, 100) { 100 + 2 * sin(it / 8.0) },
            WarnRueckblick.R_OEL to taeglich(n, WarnRueckblick.VERZUG_OEL, 10) { 60 + 40 * sin(it / 250.0) },
            WarnRueckblick.R_ANLEIHEN to taeglich(n) { 100 + 0.01 * it + 3 * sin(it / 90.0) },
            WarnRueckblick.R_KREDIT to monatlich(n / 30 + 1, WarnRueckblick.VERZUG_KREDIT, 45) { 1.9 + 1.4 * sin(it / 6.0) },
            WarnRueckblick.R_MARGIN to quartal(n / 91 + 1, WarnRueckblick.VERZUG_Z1, 120) { 300 * (1 + 0.5 * sin(it / 2.5)) },
            WarnRueckblick.R_AKTIEN_WERT to quartal(n / 91 + 1, WarnRueckblick.VERZUG_Z1, 120) { 2e7 * (1 + 0.3 * sin(it / 4.0)) },
            WarnRueckblick.R_BIP to quartal(n / 91 + 1, WarnRueckblick.VERZUG_BIP, 120) { 1.6e7 * (1 + 0.01 * it) },
            WarnRueckblick.R_ANTRAEGE to woche(n / 7 + 1, WarnRueckblick.VERZUG_ANTRAEGE, 14) { 220_000 + 50_000 * sin(it / 15.0) },
        ).mapValues { (_, r) -> g(r, garbageAb) }
    }

    // ------------------------------------------------------------ kein Vorausschau-Fehler

    @Test(timeout = 20_000)
    fun reiheZeigtWertErstNachVerzug() {
        val r = Reihe(longArrayOf(100, 130), doubleArrayOf(1.0, 2.0), verzug = 38, maxAlter = 100)
        assertEquals(0, r.anzahl(137))
        assertEquals(1, r.anzahl(138))
        assertEquals(1, r.anzahl(167))
        assertEquals(2, r.anzahl(168))
        assertNull(r.aktuell(137))
        assertNull(r.aktuell(168 + 101))
    }

    @Test(timeout = 20_000)
    fun verzugsAnnahmenDerReihen() {
        // Monatswerte tragen den ersten Tag; bekannt erst nach Monatsende plus Veroeffentlichung
        assertTrue(WarnRueckblick.VERZUG_ARBEIT >= 35)
        assertTrue(WarnRueckblick.VERZUG_CAPE >= 60)
        assertTrue(WarnRueckblick.VERZUG_CLI >= 60)
        assertTrue(WarnRueckblick.VERZUG_GEWINN >= 90)
        assertTrue(WarnRueckblick.VERZUG_NFCI >= 3)
        // Jedes Zeichen nennt den Verzug seiner Reihe
        val z = WarnRueckblick.zeichenListe().associateBy { it.id }
        assertEquals(WarnRueckblick.VERZUG_ARBEIT, z.getValue(WarnRueckblick.SAHM).verzug)
        assertEquals(WarnRueckblick.VERZUG_CAPE, z.getValue(WarnRueckblick.CAPE).verzug)
        assertEquals(WarnRueckblick.VERZUG_CLI, z.getValue(WarnRueckblick.FRUEHINDIKATOR).verzug)
    }

    // Jedes Zeichen: Werte, die an einem Tag noch nicht veroeffentlicht waren, aendern seinen Stand an diesem Tag nicht.
    @Test(timeout = 20_000)
    fun keinZeichenSiehtDieZukunft() {
        val n = 3000
        val normal = WarnRueckblick.Quellen(alle(n))
        for (stichtag in listOf(500L, 900L, 1500L, 2200L, 2800L)) {
            val t = T0 + stichtag
            val verfaelscht = WarnRueckblick.Quellen(alle(n, garbageAb = t))
            for (z in WarnRueckblick.zeichenListe()) {
                assertEquals("${z.id} am Tag $stichtag", z.regel(normal, t), z.regel(verfaelscht, t))
            }
        }
    }

    // Auch das Abschneiden aller Reihen am Stichtag aendert nichts
    @Test(timeout = 20_000)
    fun abgeschnittenGleichVollstaendig() {
        val n = 2600
        val voll = alle(n)
        val t = T0 + 1800
        val abgeschnitten = voll.mapValues { (_, r) ->
            val k = r.anzahl(t)
            Reihe(r.tage.copyOf(k), r.werte.copyOf(k), r.verzug, r.maxAlter)
        }
        for (z in WarnRueckblick.zeichenListe()) {
            assertEquals(z.id, z.regel(WarnRueckblick.Quellen(voll), t), z.regel(WarnRueckblick.Quellen(abgeschnitten), t))
        }
    }

    // Die Zeichen liefern an mehreren Tagen mehr als nur einen Wert (die Regeln greifen)
    @Test(timeout = 20_000)
    fun zeichenSindNichtImmerGleich() {
        val q = WarnRueckblick.Quellen(alle(3000))
        val tage = LongArray(2500) { T0 + 400 + it }
        for (z in WarnRueckblick.zeichenListe()) {
            if (z.id == WarnRueckblick.STIMMUNG_SICHER) continue
            val werte = WarnRueckblick.zustand(z, q, tage).toSet()
            assertTrue("${z.id}: $werte", werte.size >= 2)
        }
    }

    // ------------------------------------------------------------ Regeln der App

    @Test(timeout = 20_000)
    fun zinskurveGleichDerAppRegel() {
        val r = taeglich(2000) { 1.5 * sin(it / 220.0) }
        val z = WarnRueckblick.zeichenListe().first { it.id == WarnRueckblick.ZINSKURVE }
        val q = WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_KURVE to r))
        for (i in listOf(600, 900, 1200, 1500, 1900)) {
            val voll = (0..i).map { (T0 + it) * 86_400_000L to r.werte[it] }
            val erwartet = when (Fruehwarnung.zinskurve(voll)!!.ampel) { Fruehwarnung.Ampel.GRUEN -> 1; Fruehwarnung.Ampel.GELB -> 2; else -> 3 }
            assertEquals("Tag $i", erwartet, z.regel(q, T0 + i))
        }
    }

    @Test(timeout = 20_000)
    fun trendUndStimmungGleichDerAppRegel() {
        val sp = taeglich(900) { 1000 * (1 + 0.0008 * it + 0.25 * sin(it / 150.0)) }
        val vix = taeglich(900) { 22 + 12 * sin(it / 60.0) }
        val anl = taeglich(900) { 100 + 0.01 * it + 3 * sin(it / 90.0) }
        val q = WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_INDEX to sp, WarnRueckblick.R_SP to sp, WarnRueckblick.R_VIX to vix, WarnRueckblick.R_ANLEIHEN to anl))
        val liste = WarnRueckblick.zeichenListe().associateBy { it.id }
        for (i in listOf(300, 500, 700, 899)) {
            val paare = { r: Reihe -> (0..i).map { (T0 + it) * 86_400_000L to r.werte[it] } }
            val trend = Fruehwarnung.trend("t", paare(sp))!!.ampel.ordinal + 1
            assertEquals(trend, liste.getValue(WarnRueckblick.TREND).regel(q, T0 + i))
            val vixWert = Stimmungsbarometer.vixBarometer(paare(vix))!!.wert
            assertEquals(Blasen.stimmung(vixWert)!!.ampel.ordinal + 1, liste.getValue(WarnRueckblick.STIMMUNG_VIX).regel(q, T0 + i))
            val sicher = Stimmungsbarometer.sicherBarometer(paare(sp), paare(anl))!!.wert
            assertEquals(Blasen.stimmung(sicher)!!.ampel.ordinal + 1, liste.getValue(WarnRueckblick.STIMMUNG_SICHER).regel(q, T0 + i))
        }
    }

    // ------------------------------------------------------------ Ereignisse

    @Test(timeout = 20_000)
    fun einbruchUndRueckgangWerdenGetrennt() {
        // Anstieg bis 100, Fall auf 75 (25 %), Anstieg auf 110, Fall auf 96,8 (12 %), Anstieg auf 120
        val k = ArrayList<Double>()
        for (i in 0..100) k += 50 + i * 0.5
        for (i in 1..50) k += 100 - i * 0.5
        for (i in 1..100) k += 75 + i * 0.35
        for (i in 1..50) k += 110 - i * 0.264
        for (i in 1..100) k += 96.8 + i * 0.232
        val tage = LongArray(k.size) { 20_000L + it }
        val e = WarnRueckblick.ereignisse(tage, k.toDoubleArray())
        assertEquals(2, e.size)
        assertEquals(20, e[0].stufe)
        assertEquals(20_100, e[0].hoch)
        assertEquals(20_150, e[0].tief)
        assertEquals(25.0, e[0].tiefe, 0.01)
        assertNotNull(e[0].erholt)
        assertEquals(10, e[1].stufe)
        assertEquals(12.0, e[1].tiefe, 0.1)
    }

    @Test(timeout = 20_000)
    fun laufenderEinbruchHatKeineErholung() {
        val k = doubleArrayOf(100.0, 110.0, 90.0, 80.0, 85.0)
        val e = WarnRueckblick.ereignisse(LongArray(5) { it.toLong() }, k)
        assertEquals(1, e.size)
        assertNull(e[0].erholt)
        assertEquals(27.27, e[0].tiefe, 0.01)
    }

    // ------------------------------------------------------------ Auswertung

    @Test(timeout = 20_000)
    fun wilsonWieImKonzept() {
        assertEquals(12 to 65, WarnRueckblick.wilson(3, 9))
        assertEquals(0 to 0, WarnRueckblick.wilson(0, 0))
    }

    private val tage = LongArray(2400) { 30_000L + it }

    // Kurs mit einem Einbruch ab Tag 1500 und Hoch bei Tag 1500
    private val kurs = DoubleArray(2400) { i -> if (i <= 1500) 100.0 + i * 0.1 else (250.0 - (i - 1500) * 0.15).coerceAtLeast(150.0) }

    @Test(timeout = 20_000)
    fun trefferFehlalarmUndVorlauf() {
        val ev = WarnRueckblick.ereignisse(tage, kurs)
        val hoch = tage[1500].toInt()
        assertTrue(ev.any { it.stufe == 20 && it.hoch == hoch })
        val zu = IntArray(2400) { i -> if (i in 1300..1400 || i in 100..150) 3 else 1 }
        val b = WarnRueckblick.bilanz("x", tage, zu, 3, ev)
        assertEquals(1, b.auswertbar)
        assertEquals(1, b.treffer)
        assertEquals(2, b.phasen)
        assertEquals(1, b.phasenTreffer)
        assertEquals(1, b.fehlalarme)
        // Erste Warnung 200 Tage vor dem Hoch: 200 / 30,4375 Monate
        assertEquals(200 / 30.4375, b.vorlaufMittel!!, 0.01)
        assertEquals(b.vorlaufMittel, b.vorlaufMin)
        assertEquals(152.0 / 2400, b.zeitAnteil, 1e-9)
    }

    @Test(timeout = 20_000)
    fun phasenOhneEinbruchSindNurOffen() {
        // Warnung in den letzten 100 Tagen: weder Treffer noch Fehlalarm, solange kein Jahr vergangen ist
        val zu = IntArray(2400) { i -> if (i >= 2300) 3 else 1 }
        val b = WarnRueckblick.bilanz("x", tage, zu, 3, emptyList())
        assertEquals(1, b.phasen)
        assertEquals(0, b.fehlalarme)
        assertEquals(1, b.offen)
    }

    @Test(timeout = 20_000)
    fun standMatrixZeigtWarnungRuheUndKeineDaten() {
        val hoch = tage[1500].toInt()
        // Daten ab Tag 1200, Warnung ab Tag 1400
        val zu = IntArray(2400) { i -> if (i < 1200) 0 else if (i >= 1400) 3 else 1 }
        // 12 Monate vor dem Hoch: Tag ~1135 (keine Daten); 6: ~1317 (ruhig); 3: ~1408 (Warnung); 1: ~1469 (Warnung)
        assertEquals("-rww", WarnRueckblick.stand(tage, zu, 3, hoch))
    }

    // ------------------------------------------------------------ Gegenrechnung

    @Test(timeout = 20_000)
    fun laufOhneWarnungGleichHalten() {
        val k = DoubleArray(1000) { 100 * (1 + 0.0003 * it) }
        val t = LongArray(1000) { it.toLong() }
        val z = DoubleArray(1000) { 2.0 }
        val halten = WarnRueckblick.lauf(t, k, z, null, null, 3, 0)
        val folgen = WarnRueckblick.lauf(t, k, z, null, IntArray(1000) { 1 }, 3, 0)
        assertEquals(halten.ende, folgen.ende, 1e-9)
        assertEquals(0, folgen.wechsel)
    }

    @Test(timeout = 20_000)
    fun dauerWarnungBringtGeldmarktzinsMitKosten() {
        val k = DoubleArray(366) { 100.0 }
        val t = LongArray(366) { it.toLong() }
        val z = DoubleArray(366) { 3.65 }
        val l = WarnRueckblick.lauf(t, k, z, null, IntArray(366) { 3 }, 3, 0)
        // Ein Wechsel am ersten Tag, danach 365 Tage zu 0,01 % je Tag
        assertEquals(1, l.wechsel)
        assertEquals(100 * (1 - WarnRueckblick.KOSTEN_WECHSEL) * Math.pow(1.0001, 365.0), l.ende, 0.001)
    }

    @Test(timeout = 20_000)
    fun warnungWirktErstAmFolgetag() {
        // Warnung nur an Tag 5; der Kurs faellt an Tag 5 um 10 %: wer erst nach dem Schluss reagiert, erleidet den Verlust.
        val k = DoubleArray(10) { if (it < 5) 100.0 else 90.0 }
        val t = LongArray(10) { it.toLong() }
        val l = WarnRueckblick.lauf(t, k, DoubleArray(10), null, IntArray(10) { if (it == 5) 3 else 1 }, 3, 0)
        assertEquals(2, l.wechsel)
        assertEquals(100 * 0.9 * (1 - WarnRueckblick.KOSTEN_WECHSEL) * (1 - WarnRueckblick.KOSTEN_WECHSEL), l.ende, 0.01)
    }

    // ------------------------------------------------------------ Ganze Messung und Datei

    @Test(timeout = 20_000)
    fun gesamtmessungSchreibtUndLiestDieDatei() {
        val n = 3000
        val q = alle(n)
        val sp = q.getValue(WarnRueckblick.R_INDEX)
        val e = WarnRueckblick.IndexEingabe(
            "sp500", sp.tage, sp.werte, DoubleArray(n) { 2.0 }, null, q, "trend_sp500",
        )
        val ix = WarnRueckblick.indexErgebnis(e)
        assertTrue(ix.ereignisse.isNotEmpty())
        assertTrue(ix.zeichen.any { it.id == "zusammen_3" })
        val d = WarnRueckblick.Datei("2026-11-10", "2026-11-06", 0.001, emptyList(), emptyList(), listOf(ix))
        val zurueck = WarnRueckblick.lesen(WarnRueckblick.schreiben(d))
        assertNotNull(zurueck)
        assertEquals(ix.ereignisse.size, zurueck!!.indizes[0].ereignisse.size)
        assertEquals(ix.zeichen.size, zurueck.indizes[0].zeichen.size)
        assertNull(WarnRueckblick.lesen("kaputt"))
    }

    // Die mitgelieferte Datei (falls gebaut): Einbrueche des S&P 500 und die Probe der Zinskurve
    @Test(timeout = 20_000)
    fun mitgelieferteDateiIstPlausibel() {
        val f = File("src/main/assets/indikatoren/warnzeichen_rueckblick.json")
        if (!f.exists()) return
        val d = WarnRueckblick.lesen(f.readText())
        assertNotNull(d)
        val sp = d!!.indizes.first { it.id == "sp500" }
        assertTrue(sp.ereignisse.count { it.stufe == 20 } >= 9)
        val kurve = sp.zeichen.first { it.id == WarnRueckblick.ZINSKURVE }
        assertTrue(kurve.auswertbar >= 8)
        assertTrue(kurve.treffer in 0..kurve.auswertbar)
        assertTrue(kurve.fehlalarme >= 0)
        // Warnphasen liegen geordnet vor
        for (z in sp.zeichen) for (i in 0 until z.phasenListe.size - 1) assertTrue(z.phasenListe[i] <= z.phasenListe[i + 1])
        // Kein Zeichen beginnt vor dem ersten Kurs
        for (z in sp.zeichen) assertTrue(z.id, z.ab == 0 || z.ab >= sp.von)
    }

    // ------------------------------------------------------------ Zeichen, die die App ueber FRED laedt

    @Test(timeout = 20_000)
    fun hochzinsMitUmgerechnetemKreditaufschlag() {
        val z = WarnRueckblick.zeichenListe().first { it.id == WarnRueckblick.HOCHZINS }
        assertTrue(z.ersatz)
        // ein Jahr 1,6 (umgerechnet 4,0: gruen), dann 2,0 (5,0 und 1 Punkt ueber dem Tief: gelb), dann 2,6 (6,5: rot)
        val r = monatlich(16, WarnRueckblick.VERZUG_KREDIT, 45) { if (it < 13) 1.6 else if (it < 14) 2.0 else 2.6 }
        val q = WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_KREDIT to r))
        val bekannt = { i: Int -> T0 + i * 30L + WarnRueckblick.VERZUG_KREDIT }
        assertEquals(1, z.regel(q, bekannt(12)))
        assertEquals(2, z.regel(q, bekannt(13)))
        assertEquals(3, z.regel(q, bekannt(14)))
        // vor der Veroeffentlichung gilt noch der alte Stand
        assertEquals(1, z.regel(q, bekannt(13) - 1))
    }

    @Test(timeout = 20_000)
    fun marginWachstumGegenVorjahr() {
        val z = WarnRueckblick.zeichenListe().first { it.id == WarnRueckblick.MARGIN }
        // vier Quartale 100, dann 125 (+25 %: gelb), dann 150 (+50 %: rot)
        val r = Reihe(longArrayOf(T0, T0 + 91, T0 + 182, T0 + 273, T0 + 365, T0 + 456), doubleArrayOf(100.0, 100.0, 100.0, 100.0, 125.0, 150.0), WarnRueckblick.VERZUG_Z1, 120)
        val q = WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_MARGIN to r))
        assertEquals(0, z.regel(q, T0 + 273 + WarnRueckblick.VERZUG_Z1))
        assertEquals(2, z.regel(q, T0 + 365 + WarnRueckblick.VERZUG_Z1))
        assertEquals(3, z.regel(q, T0 + 456 + WarnRueckblick.VERZUG_Z1))
    }

    @Test(timeout = 20_000)
    fun buffettMitDemIndexFortgeschrieben() {
        val z = WarnRueckblick.zeichenListe().first { it.id == WarnRueckblick.BUFFETT }
        // Boersenwert 20 Billionen, BIP 16 Billionen (Millionen Dollar): 125 % gelb; verdoppelt sich der Index danach, 250 % rot
        val aktien = Reihe(longArrayOf(T0 + 100), doubleArrayOf(2e7), WarnRueckblick.VERZUG_Z1, 120)
        val bip = Reihe(longArrayOf(T0 + 100), doubleArrayOf(1.6e7), WarnRueckblick.VERZUG_BIP, 120)
        val sp = taeglich(300) { if (it < 250) 1000.0 else 2000.0 }
        val q = WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_AKTIEN_WERT to aktien, WarnRueckblick.R_BIP to bip, WarnRueckblick.R_SP to sp))
        assertEquals(0, z.regel(q, T0 + 100 + WarnRueckblick.VERZUG_Z1 - 1))
        assertEquals(2, z.regel(q, T0 + 100 + WarnRueckblick.VERZUG_Z1))
        assertEquals(3, z.regel(q, T0 + 250))
        // ein BIP aelter als vier Monate (nach Veroeffentlichung) gilt nicht mehr
        assertEquals(0, z.regel(q, T0 + 260))
    }

    @Test(timeout = 20_000)
    fun erstantraegeGleichDerAppRegel() {
        val z = WarnRueckblick.zeichenListe().first { it.id == WarnRueckblick.ERSTANTRAEGE }
        val r = woche(70, WarnRueckblick.VERZUG_ANTRAEGE, 14) { if (it < 60) 200_000.0 else 250_000.0 }
        val q = WarnRueckblick.Quellen(mapOf(WarnRueckblick.R_ANTRAEGE to r))
        for (i in listOf(58, 60, 61, 63, 69)) {
            val bis = (0..i).map { (T0 + it * 7L) * 86_400_000L to r.werte[it] }
            val erwartet = Fruehindikatoren.erstantraege(bis)!!.ampel.ordinal + 1
            assertEquals("Woche $i", erwartet, z.regel(q, T0 + i * 7L + WarnRueckblick.VERZUG_ANTRAEGE))
        }
        assertEquals(3, z.regel(q, T0 + 69 * 7L + WarnRueckblick.VERZUG_ANTRAEGE))
    }

    @Test(timeout = 20_000)
    fun neueZeichenZaehlenOhneDoppelung() {
        assertTrue(WarnRueckblick.HOCHZINS in WarnRueckblick.ZAEHLT)
        assertTrue(WarnRueckblick.MARGIN in WarnRueckblick.ZAEHLT)
        assertTrue(WarnRueckblick.BUFFETT !in WarnRueckblick.ZAEHLT)
        assertTrue(WarnRueckblick.ERSTANTRAEGE !in WarnRueckblick.ZAEHLT)
    }
}
