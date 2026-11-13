package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.market.IndexSelbst
import de.troetenrepublik.app.market.IndexStand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 0.78: Feiertage und verkuerzte Handelstage der US-Boersen gegen den Kalender der NYSE fuer 2026 bis 2028 (Stand
// 12.11.2026), Wirkung auf Handelsfenster und Tagesorders, Selbstabruf ohne die entfernte Quelle.
class Fassung078U29QuellenTest {
    private val ny = ZoneId.of("America/New_York")
    private var zeitenVorher: MarketHours.Zeiten? = null

    @Before fun vorher() {
        zeitenVorher = MarketHours.zeiten
        MarketHours.zeiten = MarketHours.zeitenFuer("US", MarketHours.LAND, 0, 0)
    }

    @After fun nachher() {
        zeitenVorher?.let { MarketHours.zeiten = it }
    }

    private fun d(s: String) = LocalDate.parse(s)
    private fun um(tag: String, h: Int, m: Int = 0) = d(tag).atTime(h, m).atZone(ny).toInstant().toEpochMilli()

    // Handelsfreie Tage laut Kalender der Boerse
    private val feiertage = setOf(
        "2026-01-01", "2026-01-19", "2026-02-16", "2026-04-03", "2026-05-25", "2026-06-19", "2026-07-03", "2026-09-07", "2026-11-26", "2026-12-25",
        "2027-01-01", "2027-01-18", "2027-02-15", "2027-03-26", "2027-05-31", "2027-06-18", "2027-07-05", "2027-09-06", "2027-11-25", "2027-12-24",
        "2028-01-17", "2028-02-21", "2028-04-14", "2028-05-29", "2028-06-19", "2028-07-04", "2028-09-04", "2028-11-23", "2028-12-25",
    ).map { d(it) }.toSet()

    // Tage mit Schluss um 13:00 Uhr laut Kalender der Boerse
    private val verkuerzt = setOf("2026-11-27", "2026-12-24", "2027-11-26", "2028-07-03", "2028-11-24").map { d(it) }.toSet()

    private fun werktage(): List<LocalDate> =
        generateSequence(d("2026-01-01")) { it.plusDays(1) }.takeWhile { it.year <= 2028 }.filter { it.dayOfWeek.value <= 5 }.toList()

    @Test fun feiertageWieImKalenderDerBoerse() {
        for (tag in werktage()) {
            assertEquals(tag.toString(), tag in feiertage, Boersenzeiten.feiertagUs(tag))
            assertEquals(tag.toString(), tag in feiertage, WeitereQuellen.usFeiertag(tag))
        }
    }

    @Test fun verkuerzteTageWieImKalenderDerBoerse() {
        var tag = d("2026-01-01")
        while (tag.year <= 2028) {
            assertEquals(tag.toString(), tag in verkuerzt, Boersenzeiten.verkuerztUs(tag))
            tag = tag.plusDays(1)
        }
        // Nur die USA haben eine Regel
        assertTrue(Boersenzeiten.verkuerzt("US", d("2026-11-27")))
        assertFalse(Boersenzeiten.verkuerzt("DE", d("2026-12-30")))
    }

    @Test fun globusZeigtSchlussUmDreizehnUhr() {
        val kurz = Boersenzeiten.status("US", d("2026-11-27").atTime(12, 0).atZone(ny).toInstant())!!
        assertTrue(kurz.offen)
        assertEquals(LocalTime.of(13, 0), kurz.wechsel.toLocalTime())
        val danach = Boersenzeiten.status("US", d("2026-11-27").atTime(13, 0).atZone(ny).toInstant())!!
        assertFalse(danach.offen)
        assertEquals(d("2026-11-30"), danach.wechsel.toLocalDate())
        val normal = Boersenzeiten.status("US", d("2026-11-25").atTime(12, 0).atZone(ny).toInstant())!!
        assertEquals(LocalTime.of(16, 0), normal.wechsel.toLocalTime())
        assertEquals("9:30 bis 13:00", Boersenzeiten.zeiten(Boersenzeiten.NACH_LAND["US"]!!, "und", "bis", d("2026-12-24")))
        assertEquals("9:30 bis 16:00", Boersenzeiten.zeiten(Boersenzeiten.NACH_LAND["US"]!!, "und", "bis", d("2026-12-23")))
    }

    @Test fun handelsfensterEndetFrueher() {
        val w = MarketHours.STOCKS
        assertTrue(MarketHours.isOpen(w, um("2026-11-27", 12, 59)))
        assertFalse(MarketHours.isOpen(w, um("2026-11-27", 13, 0)))
        assertFalse(MarketHours.isOpen(w, um("2026-11-27", 15, 0)))
        assertTrue(MarketHours.isOpen(w, um("2026-11-25", 15, 0)))
        assertFalse(MarketHours.isOpen(AssetType.STOCK, false, um("2028-07-03", 14, 0)))
        // Krypto bleibt offen
        assertTrue(MarketHours.isOpen(AssetType.CRYPTO, false, um("2028-07-03", 14, 0)))
        // 2027 liegt der 24.12. auf dem Feiertag selbst, 2026 ist der 3.7. Feiertag
        assertFalse(MarketHours.isOpen(w, um("2027-12-24", 10, 0)))
        assertFalse(MarketHours.isOpen(w, um("2026-07-03", 10, 0)))
        // Depotfenster des Tages
        assertEquals(um("2026-11-27", 9, 30) to um("2026-11-27", 13, 0), MarketHours.depotFenster(um("2026-11-27", 11, 0)))
        assertEquals(LocalTime.of(16, 0), MarketHours.schlussAm(w, d("2026-11-25")))
    }

    @Test fun statusNenntDenFruehenSchluss() {
        val offen = MarketHours.status(AssetType.STOCK, false, um("2026-12-24", 10, 0))
        assertTrue(offen.open)
        assertEquals(LocalTime.of(13, 0), offen.kurzSchluss)
        assertNull(MarketHours.status(AssetType.STOCK, false, um("2026-12-23", 10, 0)).kurzSchluss)
        val zu = MarketHours.status(AssetType.STOCK, false, um("2026-12-24", 13, 30))
        assertFalse(zu.open)
        assertNull(zu.kurzSchluss)
        // Der 25.12. ist Feiertag, danach Wochenende
        assertEquals(um("2026-12-28", 9, 30), zu.nextOpen)
    }

    @Test fun tagesorderEndetMitDemVerkuerztenTag() {
        val w = MarketHours.STOCKS
        assertEquals(um("2026-11-27", 13, 0), MarketHours.schlussDesHandelstags(w, um("2026-11-27", 10, 0)))
        // Am Feiertag aufgegeben: gilt bis zum fruehen Schluss am Freitag
        assertEquals(um("2026-11-27", 13, 0), MarketHours.schlussDesHandelstags(w, um("2026-11-26", 10, 0)))
        // Nach dem fruehen Schluss aufgegeben: naechster Handelstag mit ueblichem Schluss
        assertEquals(um("2026-11-30", 16, 0), MarketHours.schlussDesHandelstags(w, um("2026-11-27", 14, 0)))
    }

    @Test fun andereEinstellungenBleibenOhneFruehenSchluss() {
        MarketHours.zeiten = MarketHours.DEUTSCHLAND
        val berlin = ZoneId.of("Europe/Berlin")
        assertTrue(MarketHours.isOpen(MarketHours.STOCKS, d("2026-11-27").atTime(21, 0).atZone(berlin).toInstant().toEpochMilli()))
        MarketHours.zeiten = MarketHours.zeitenFuer("US", MarketHours.EIGENE, 9 * 60, 20 * 60)
        assertTrue(MarketHours.isOpen(MarketHours.STOCKS, um("2026-11-27", 15, 0)))
    }

    @Test fun selbstabrufOhneEntfernteQuelle() {
        // Merval laeuft ueber Stooq, der Laender-ETF ARGT bleibt der Rueckfall
        assertNotNull(IndexSelbst.fuer("AR"))
        assertTrue(IndexSelbst.QUELLEN.all { it.adresse.startsWith("https://") })
        // Ein gemerkter Wert einer Quelle, die es nicht mehr gibt, faellt beim Lesen weg; bekannte bleiben
        val heute = d("2026-11-12")
        val text = IndexStand.schreiben(
            mapOf(
                "AR" to mapOf(
                    "selbst:entfernt" to IndexStand.Wert("AR", "selbst:entfernt", 1.0, 100.0, heute, 1L),
                    "yahoo-etf" to IndexStand.Wert("AR", "yahoo-etf", 0.5, 80.0, heute, 2L),
                ),
                "TN" to mapOf("selbst:ilboursa" to IndexStand.Wert("TN", "selbst:ilboursa", 0.2, 18000.0, heute, 3L)),
            ),
        )
        val gelesen = IndexStand.lesen(text)
        assertEquals(setOf("yahoo-etf"), gelesen["AR"]!!.keys)
        assertEquals(setOf("selbst:ilboursa"), gelesen["TN"]!!.keys)
        assertEquals("yahoo-etf", IndexStand.zusammenfuehren(gelesen, heute)["AR"]!!.quelle)
    }
}
