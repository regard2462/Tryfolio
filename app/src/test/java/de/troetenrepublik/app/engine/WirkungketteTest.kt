package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Wirkungskette.Art
import de.troetenrepublik.app.engine.Wirkungskette.Bezug
import de.troetenrepublik.app.engine.Wirkungskette.Fall
import de.troetenrepublik.app.engine.Wirkungskette.Lage
import de.troetenrepublik.app.engine.Wirkungskette.Region
import de.troetenrepublik.app.engine.Wirkungskette.Sektor
import de.troetenrepublik.app.engine.Wirkungskette.Urteil
import de.troetenrepublik.app.engine.Wirkungskette.ZinsFolge
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.NasdaqKalender.Wirtschaftstermin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Wirkungskette: Einordnung der Nasdaq-Ereignisnamen, Ueberraschung mit Vorzeichen, Kettenwahl und Gegenprobe.
class WirkungketteTest {

    // gekuerzte Antwort der Nasdaq (economicevents, Termine des 6.11.2026, Arbeitsmarktbericht)
    private val antwort = """{"data":{"rows":[{"gmt":"08:30","country":"United States","eventName":"Average Hourly Earnings","actual":"0.4%","consensus":"0.3%","previous":"0.3%","description":""},{"gmt":"08:30","country":"United States","eventName":"Average Hourly Earnings (YoY)","actual":"3.1%","consensus":"2.9%","previous":"3.2%","description":""},{"gmt":"08:30","country":"United States","eventName":"Nonfarm Payrolls","actual":"165K","consensus":"65K","previous":"60K","description":""},{"gmt":"08:30","country":"United States","eventName":"Unemployment Rate","actual":"4.1%","consensus":"4.2%","previous":"4.2%","description":""}]},"status":{"rCode":200}}"""

    private val tag = LocalDate.of(2026, 11, 6)

    private fun t(land: String, name: String, ist: String?, erw: String?, vor: String? = null, tg: LocalDate = tag) =
        Wirtschaftstermin(tg.toEpochDay(), 8 * 60 + 30, land, name, ist, erw, vor)

    private val us = "United States"

    @Test
    fun namenWerdenEingeordnet() {
        assertEquals(Art.STELLEN, Wirkungskette.art("Nonfarm Payrolls"))
        assertEquals(Art.ARBEITSLOSENQUOTE, Wirkungskette.art("Unemployment Rate"))
        assertEquals(Art.ARBEITSLOSENQUOTE, Wirkungskette.art("German Unemployment Rate"))
        assertEquals(Art.ERSTANTRAEGE, Wirkungskette.art("Initial Jobless Claims"))
        assertEquals(Art.VERBRAUCHERPREISE, Wirkungskette.art("Core CPI"))
        assertEquals(Art.VERBRAUCHERPREISE, Wirkungskette.art("Core PCE Price Index"))
        assertEquals(Art.VERBRAUCHERPREISE_TOKIO, Wirkungskette.art("Tokyo Core CPI"))
        assertEquals(Art.ERZEUGERPREISE, Wirkungskette.art("PPI"))
        assertEquals(Art.BIP, Wirkungskette.art("GDP"))
        assertEquals(Art.BIP, Wirkungskette.art("French GDP"))
        assertEquals(Art.EINZELHANDEL, Wirkungskette.art("Retail Sales"))
        assertEquals(Art.EINKAUFSMANAGER_ISM, Wirkungskette.art("ISM Manufacturing PMI"))
        assertEquals(Art.EINKAUFSMANAGER, Wirkungskette.art("HCOB Eurozone Services PMI"))
        assertEquals(Art.EINKAUFSMANAGER, Wirkungskette.art("Manufacturing PMI"))
        assertEquals(Art.VERTRAUEN, Wirkungskette.art("CB Consumer Confidence"))
        assertEquals(Art.VERTRAUEN, Wirkungskette.art("GfK German Consumer Climate"))
        assertEquals(Art.ZEW, Wirkungskette.art("ZEW Expectations"))
        assertEquals(Art.OFFENE_STELLEN, Wirkungskette.art("JOLTS Job Openings"))
        assertEquals(Art.LOEHNE, Wirkungskette.art("Average Hourly Earnings (YoY)"))
        assertEquals(Art.AUFTRAEGE, Wirkungskette.art("Durable Goods Orders"))
        assertEquals(Art.IMMOBILIEN, Wirkungskette.art("Building Permits"))
        assertEquals(Art.ZINSENTSCHEID, Wirkungskette.art("Fed Interest Rate Decision"))
        assertEquals(Art.ZINSENTSCHEID, Wirkungskette.art("ECB Interest Rate Decision"))
        assertEquals(Art.ZINSENTSCHEID, Wirkungskette.art("BoJ Interest Rate Decision"))
        // zaehlen nicht
        assertNull(Wirkungskette.art("Bavaria CPI"))
        assertNull(Wirkungskette.art("CPI Index, n.s.a."))
        assertNull(Wirkungskette.art("Unemployment Rate n.s.a."))
        assertNull(Wirkungskette.art("HCOB Germany Construction PMI"))
        assertNull(Wirkungskette.art("BoE MPC vote hike"))
        assertNull(Wirkungskette.art("CFTC Gold speculative net positions"))
        assertNull(Wirkungskette.art("FOMC Press Conference"))
        assertNull(Wirkungskette.art("10-Year Note Auction"))
    }

    @Test
    fun werteWerdenGelesen() {
        assertEquals(165_000.0, Wirkungskette.zahl("165K")!!, 1e-6)
        assertEquals(1_701_000.0, Wirkungskette.zahl("1,701K")!!, 1e-6)
        assertEquals(7_523_000.0, Wirkungskette.zahl("7.523M")!!, 1e-6)
        assertEquals(4.1, Wirkungskette.zahl("4.1%")!!, 1e-9)
        assertEquals(-132.6e9, Wirkungskette.zahl("-132.60B")!!, 1.0)
        assertNull(Wirkungskette.zahl("&nbsp;"))
        assertNull(Wirkungskette.zahl(null))
    }

    @Test
    fun ueberraschungMitVorzeichen() {
        val liste = NasdaqKalender.wirtschaftLesen(antwort, tag)!!
        val p = liste.mapNotNull { Wirkungskette.ueberraschung(it) }.associateBy { it.name }
        assertEquals(Lage.STAERKER, p.getValue("Nonfarm Payrolls").lage)
        assertEquals(Bezug.ERWARTUNG, p.getValue("Nonfarm Payrolls").bezug)
        // niedrigere Arbeitslosenquote heisst staerker
        assertEquals(Lage.STAERKER, p.getValue("Unemployment Rate").lage)
        // Loehne hoeher: staerker im Sinne von hoeheren Preisen
        assertEquals(Lage.STAERKER, p.getValue("Average Hourly Earnings").lage)
        // hoehere Quote waere schwaecher
        val schwach = Wirkungskette.ueberraschung(t(us, "Unemployment Rate", "4.5%", "4.2%"))!!
        assertEquals(Lage.SCHWAECHER, schwach.lage)
    }

    @Test
    fun imRahmenDerErwartung() {
        // Erstantraege 194K gegen 198K: im Rahmen
        assertEquals(Lage.ERWARTET, Wirkungskette.ueberraschung(t(us, "Initial Jobless Claims", "194K", "198K", "195K"))!!.lage)
        assertEquals(Lage.ERWARTET, Wirkungskette.ueberraschung(t(us, "CPI", "0.6%", "0.6%", "0.4%"))!!.lage)
        assertEquals(Lage.ERWARTET, Wirkungskette.ueberraschung(t("Euro Zone", "ECB Interest Rate Decision", "2.65%", "2.65%", "2.65%"))!!.lage)
        // Core CPI 0,3 gegen 0,2: hoeher
        assertEquals(Lage.STAERKER, Wirkungskette.ueberraschung(t(us, "Core CPI", "0.3%", "0.2%", "0.3%"))!!.lage)
        // chinesischer Einkaufsmanagerindex 49,3 gegen 49,9: schwaecher
        assertEquals(Lage.SCHWAECHER, Wirkungskette.ueberraschung(t("China", "Manufacturing PMI", "49.3", "49.9", "50.1"))!!.lage)
    }

    @Test
    fun ohneErwartungGegenVorwert() {
        val p = Wirkungskette.ueberraschung(t("Euro Zone", "Business Climate", "0.09", null, "-0.05"))!!
        assertEquals(Bezug.VORWERT, p.bezug)
        assertEquals("-0.05", p.referenz)
        val z = Wirkungskette.ueberraschung(t("Japan", "BoJ Interest Rate Decision", "1.50%", null, "1.25%"))!!
        assertEquals(Bezug.VORWERT, z.bezug)
        assertEquals(Lage.STAERKER, z.lage)
        // ohne Istwert oder ohne jeden Vergleich keine Ueberraschung
        assertNull(Wirkungskette.ueberraschung(t(us, "Nonfarm Payrolls", null, "65K", "60K")))
        assertNull(Wirkungskette.ueberraschung(t(us, "Nonfarm Payrolls", "165K", null, null)))
        assertNull(Wirkungskette.ueberraschung(t("Brazil", "GDP", "1.0%", "0.9%")))
    }

    @Test
    fun arbeitsmarktberichtIstHeiss() {
        val liste = NasdaqKalender.wirtschaftLesen(antwort, tag)!!
        val a = Wirkungskette.aussage(tag.toEpochDay(), Region.US, liste)!!
        // Arbeitsmarkt stark und Loehne hoeher
        assertEquals(Fall.HEISS, a.fall)
        assertEquals("Nonfarm Payrolls", a.kopf!!.name)
        val k = a.kette!!
        assertEquals(ZinsFolge.ERHOEHUNG_MEHR, k.zins)
        assertTrue(Sektor.TECH in k.belastet && Sektor.IMMOBILIEN in k.belastet)
        assertTrue(Sektor.BANKEN in k.gestuetzt)
        // staerkere Waehrung: Exporteure belastet
        assertTrue(Sektor.EXPORTEURE in k.belastet)
    }

    @Test
    fun starkeSchwacheGoldilocksStagflationErwartet() {
        fun fall(vararg x: Wirtschaftstermin) = Wirkungskette.aussage(tag.toEpochDay(), Region.US, x.toList())!!.fall
        val stark = t(us, "Nonfarm Payrolls", "165K", "65K", "60K")
        val schwach = t(us, "Nonfarm Payrolls", "20K", "65K", "60K")
        val cpiNiedrig = t(us, "Core CPI", "0.1%", "0.3%", "0.3%")
        val cpiHoch = t(us, "Core CPI", "0.5%", "0.3%", "0.3%")
        assertEquals(Fall.STARK, fall(stark))
        assertEquals(Fall.SCHWACH, fall(schwach))
        assertEquals(Fall.GOLDILOCKS, fall(stark, cpiNiedrig))
        assertEquals(Fall.STAGFLATION, fall(schwach, cpiHoch))
        assertEquals(Fall.SCHWACH_ENTSPANNT, fall(schwach, cpiNiedrig))
        assertEquals(Fall.INFLATION_HOCH, fall(cpiHoch))
        assertEquals(Fall.INFLATION_NIEDRIG, fall(cpiNiedrig))
        assertEquals(Fall.ERWARTET, fall(t(us, "Initial Jobless Claims", "194K", "198K", "195K"), t(us, "CPI", "0.6%", "0.6%", "0.4%")))
        // gegenlaeufig ohne Mehrheit: Beschaeftigung stark (2), Quote schlechter (2)
        assertEquals(Fall.GEMISCHT, fall(stark, t(us, "Unemployment Rate", "4.6%", "4.2%")))
        // Zinsentscheide
        assertEquals(Fall.ZINS_ERWARTET, fall(t(us, "Fed Interest Rate Decision", "4.00%", "4.00%", "4.00%")))
        assertEquals(Fall.ZINS_LOCKERER, fall(t(us, "Fed Interest Rate Decision", "3.75%", "4.00%", "4.00%")))
        assertEquals(Fall.ZINS_STRAFFER, fall(t(us, "Fed Interest Rate Decision", "4.25%", "4.00%", "4.00%")))
    }

    @Test
    fun ketteJeRegion() {
        val schwach = Wirkungskette.kette(Fall.SCHWACH, Region.US)!!
        assertEquals(ZinsFolge.SENKUNG_MEHR, schwach.zins)
        assertTrue(Sektor.TECH in schwach.gestuetzt && Sektor.IMMOBILIEN in schwach.gestuetzt && Sektor.VERSORGER in schwach.gestuetzt)
        assertTrue(Sektor.BANKEN in schwach.belastet && Sektor.ZYKLIKER in schwach.belastet)
        // Japan: staerkerer Yen belastet Exporteure
        val jp = Wirkungskette.kette(Fall.STARK, Region.JP)!!
        assertTrue(Sektor.EXPORTEURE in jp.belastet)
        // China: Rohstoffe, Luxus
        val cn = Wirkungskette.kette(Fall.STARK, Region.CN)!!
        assertTrue(Sektor.ROHSTOFFE in cn.gestuetzt && Sektor.LUXUS in cn.gestuetzt)
        assertEquals(ZinsFolge.LOCKERUNG_WENIGER, cn.zins)
        val cnSchwach = Wirkungskette.kette(Fall.SCHWACH, Region.CN)!!
        assertTrue(Sektor.ROHSTOFFE in cnSchwach.belastet)
        // Zinsueberraschung: keine Banken
        assertTrue(Sektor.BANKEN !in Wirkungskette.kette(Fall.ZINS_STRAFFER, Region.EURO)!!.gestuetzt)
        assertNull(Wirkungskette.kette(Fall.ERWARTET, Region.US))
        assertNull(Wirkungskette.kette(Fall.GEMISCHT, Region.US))
    }

    @Test
    fun randzahlenBekommenKeineKarte() {
        val nur = mapOf(tag.toEpochDay() to listOf(t(us, "ISM Manufacturing Prices", "75.2", "77.2", "77.9")))
        assertTrue(Wirkungskette.aussagen(nur).isEmpty())
        val voll = mapOf(tag.toEpochDay() to NasdaqKalender.wirtschaftLesen(antwort, tag)!!)
        assertEquals(listOf(Region.US), Wirkungskette.aussagen(voll).map { it.region })
    }

    @Test
    fun kurzverweisNurBeiAbweichung() {
        assertNotNull(Wirkungskette.einzel(t(us, "Nonfarm Payrolls", "165K", "65K", "60K")))
        assertNull(Wirkungskette.einzel(t(us, "Initial Jobless Claims", "194K", "198K", "195K")))
        assertNull(Wirkungskette.einzel(t(us, "Nonfarm Payrolls", null, "65K", "60K")))
    }

    // ------------------------------------------------------------ Gegenprobe

    private fun kerzen(tg: LocalDate, davor: Double, danach: Double): List<Candle> {
        val ms = tg.toEpochDay() * 86_400_000L + 14 * 3_600_000L
        return listOf(
            Candle(ms - 2 * 86_400_000L, davor, davor, davor, davor),
            Candle(ms - 86_400_000L, davor, davor, davor, davor),
            Candle(ms, danach, danach, danach, danach),
        )
    }

    private fun kurse(tg: LocalDate, v: Map<String, Pair<Double, Double>>) = v.mapValues { (_, p) -> kerzen(tg, p.first, p.second) }

    @Test
    fun reaktionPasstZurKette() {
        val a = Wirkungskette.aussage(tag.toEpochDay(), Region.US, NasdaqKalender.wirtschaftLesen(antwort, tag)!!)!!
        val k = kurse(tag, mapOf(
            "^TNX" to (4.80 to 4.86), "DX-Y.NYB" to (101.0 to 101.4), "^GSPC" to (7000.0 to 6965.0), "^NDX" to (26000.0 to 25600.0),
            "^RUT" to (2800.0 to 2760.0), "XLK" to (170.0 to 167.45), "XLRE" to (45.0 to 44.6), "XLU" to (43.0 to 42.7),
            "XLF" to (50.0 to 50.15), "XLY" to (200.0 to 200.0), "XLI" to (150.0 to 150.3), "XLE" to (90.0 to 90.5),
            "XLB" to (80.0 to 80.0), "XLP" to (75.0 to 75.1), "XLV" to (140.0 to 140.0),
        ))
        val r = Wirkungskette.reaktion(a, k)
        assertEquals(Urteil.PASST, r.urteil)
        val rendite = r.zeilen.first { it.art == Wirkungskette.ZeilenArt.RENDITE }
        assertEquals(6.0, rendite.aenderung, 1e-6)
        assertEquals(true, rendite.passt)
        // Tech faellt 1,5 % bei Index minus 0,5 %: belastet, passt
        val tech = r.sektoren.first { it.schluessel == "TECH" }
        assertEquals(-1.5, tech.aenderung, 0.01)
        assertEquals(true, tech.passt)
    }

    @Test
    fun reaktionAndersAlsUeblich() {
        val a = Wirkungskette.aussage(tag.toEpochDay(), Region.US, NasdaqKalender.wirtschaftLesen(antwort, tag)!!)!!
        val k = kurse(tag, mapOf(
            "^TNX" to (4.86 to 4.78), "DX-Y.NYB" to (101.4 to 101.0), "^GSPC" to (7000.0 to 7035.0), "^NDX" to (26000.0 to 26400.0),
            "^RUT" to (2800.0 to 2840.0), "XLK" to (170.0 to 173.4), "XLRE" to (45.0 to 45.5), "XLU" to (43.0 to 43.4),
            "XLF" to (50.0 to 49.7), "XLY" to (200.0 to 199.0), "XLI" to (150.0 to 149.5),
        ))
        assertEquals(Urteil.ANDERS, Wirkungskette.reaktion(a, k).urteil)
    }

    @Test
    fun reaktionOhneKursOffen() {
        val a = Wirkungskette.aussage(tag.toEpochDay(), Region.US, NasdaqKalender.wirtschaftLesen(antwort, tag)!!)!!
        assertEquals(Urteil.OFFEN, Wirkungskette.reaktion(a, emptyMap()).urteil)
    }

    @Test
    fun yenInvertiertUndZinspfad() {
        val a = Wirkungskette.aussage(tag.toEpochDay(), Region.JP, listOf(t("Japan", "Unemployment Rate", "2.4%", "2.5%", "2.5%"), t("Japan", "Retail Sales", "4.0%", "3.3%", "2.9%")))!!
        assertEquals(Fall.STARK, a.fall)
        // Dollar je Yen faellt: der Yen wird staerker, wie die Kette sagt
        // Der Termin liegt nach Handelsschluss in Tokio am Freitag: Die Reaktion zaehlt am Montag
        assertEquals(LocalDate.of(2026, 11, 9).toEpochDay(), a.handelstag)
        val r = Wirkungskette.reaktion(a, kurse(LocalDate.ofEpochDay(a.handelstag), mapOf("JPY=X" to (158.0 to 157.0), "^N225" to (60000.0 to 60300.0))))
        val yen = r.zeilen.first { it.art == Wirkungskette.ZeilenArt.WAEHRUNG }
        assertTrue(yen.aenderung > 0)
        assertEquals(true, yen.passt)
        val zp = Wirkungskette.zinsPfad(kurse(tag, mapOf("ZQZ27.CBT" to (96.40 to 96.46))), listOf("ZQZ27.CBT"), tag.toEpochDay())!!
        assertEquals(3.60, zp.vorher, 1e-6)
        assertEquals(3.54, zp.nachher, 1e-6)
    }
}
