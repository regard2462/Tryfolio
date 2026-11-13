package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.RasterTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Raster Jahr mal Monat: Monatsrenditen aus Kursreihen, Jahresrendite, Luecken, Zeitzone, fertige Monatswerte, Texte.
class Fassung109MonatsrasterTest {

    private fun t(j: Int, m: Int, d: Int) = LocalDate.of(j, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun monatsrenditenUndJahr() {
        val r = Monatsraster.aus(
            listOf(t(2023, 12, 29) to 100.0, t(2024, 1, 15) to 50.0, t(2024, 1, 31) to 110.0, t(2024, 2, 29) to 99.0, t(2024, 3, 28) to 108.9),
        )!!
        // Dezember 2023 steht allein: ohne Bezugskurs, kein Jahr 2023 im Raster
        assertEquals(listOf(2024), r.zeilen.map { it.jahr })
        val z = r.zeilen[0]
        assertEquals(0.1, z.monate[0]!!, 1e-12)
        assertEquals(-0.1, z.monate[1]!!, 1e-12)
        assertEquals(0.1, z.monate[2]!!, 1e-12)
        assertNull(z.monate[3])
        assertEquals(12, z.monate.size)
        assertEquals(1.1 * 0.9 * 1.1 - 1.0, z.rendite!!, 1e-12)
    }

    @Test fun ersterMonatNurMitAnfangstagen() {
        val frueh = Monatsraster.aus(listOf(t(2024, 1, 3) to 100.0, t(2024, 1, 31) to 105.0))!!
        assertEquals(0.05, frueh.zeilen[0].monate[0]!!, 1e-12)
        val spaet = Monatsraster.aus(listOf(t(2024, 1, 10) to 100.0, t(2024, 1, 31) to 105.0, t(2024, 2, 29) to 115.5))
        assertNull(spaet!!.zeilen[0].monate[0])
        assertEquals(0.1, spaet.zeilen[0].monate[1]!!, 1e-12)
        // Ohne Januar keine Jahresrendite
        assertNull(spaet.zeilen[0].rendite)
    }

    @Test fun jahreNeuestesZuerstUndJahreswechsel() {
        val r = Monatsraster.aus(
            listOf(t(2023, 11, 30) to 100.0, t(2023, 12, 29) to 110.0, t(2024, 1, 31) to 121.0, t(2024, 12, 31) to 133.1),
        )!!
        assertEquals(listOf(2024, 2023), r.zeilen.map { it.jahr })
        assertEquals(0.1, r.zeilen[1].monate[11]!!, 1e-12)
        assertEquals(0.1, r.zeilen[0].monate[0]!!, 1e-12)
        // Februar bis November 2024 fehlen, Dezember hat keinen Vormonat: leer, Jahresrendite nur aus dem Januar
        assertNull(r.zeilen[0].monate[11])
    }

    @Test fun lueckeMachtDieJahresrenditeLeer() {
        val r = Monatsraster.aus(
            listOf(t(2024, 1, 2) to 100.0, t(2024, 1, 31) to 110.0, t(2024, 3, 28) to 121.0),
        )!!
        assertEquals(0.1, r.zeilen[0].monate[0]!!, 1e-12)
        assertNull(r.zeilen[0].monate[2])
        assertNull(r.zeilen[0].rendite)
    }

    @Test fun zeitzoneVerschiebtDenMonat() {
        // 31.01. 23:30 UTC ist in Zuerich schon der 1.2.
        val reihe = listOf(
            t(2023, 12, 29) + 0L to 100.0, t(2024, 1, 31) + 23L * 3_600_000L + 30L * 60_000L to 110.0,
            t(2024, 2, 29) to 99.0, t(2024, 3, 28) to 108.9,
        )
        val utc = Monatsraster.aus(reihe, ZoneOffset.UTC)!!
        assertEquals(0.1, utc.zeilen[0].monate[0]!!, 1e-12)
        val zh = Monatsraster.aus(reihe, ZoneId.of("Europe/Zurich"))!!
        assertNull(zh.zeilen[0].monate[0])
    }

    @Test fun ungueltigeKurseUndZuWenig() {
        assertNull(Monatsraster.aus(emptyList()))
        assertNull(Monatsraster.aus(listOf(t(2024, 1, 2) to 100.0)))
        assertNull(Monatsraster.aus(listOf(t(2024, 1, 2) to 0.0, t(2024, 2, 1) to Double.NaN)))
        // Unsortiert
        val r = Monatsraster.aus(listOf(t(2024, 2, 29) to 110.0, t(2024, 1, 2) to 100.0, t(2024, 1, 31) to 100.0))!!
        assertEquals(0.0, r.zeilen[0].monate[0]!!, 1e-12)
        assertEquals(0.1, r.zeilen[0].monate[1]!!, 1e-12)
    }

    @Test fun fertigeMonatswerteMitJahresrendite() {
        val r = Monatsraster.ausMonaten(
            mapOf((2024 to 1) to 0.01, (2024 to 2) to 0.02, (2025 to 5) to -0.03, (2025 to 13) to 9.0),
            mapOf(2024 to 0.5),
        )!!
        assertEquals(listOf(2025, 2024), r.zeilen.map { it.jahr })
        assertEquals(0.5, r.zeilen[1].rendite!!, 0.0)
        assertNull(r.zeilen[0].rendite)
        assertEquals(-0.03, r.zeilen[0].monate[4]!!, 0.0)
        assertNull(Monatsraster.ausMonaten(emptyMap()))
        assertNotNull(Monatsraster.ausMonaten(mapOf((2024 to 1) to 0.0)))
    }

    @Test fun jahrAusMonatenVerkettet() {
        val m = List<Double?>(12) { null }.toMutableList()
        assertNull(Monatsraster.verkettet(m))
        m[0] = 0.1; m[1] = 0.1
        assertEquals(0.21, Monatsraster.verkettet(m)!!, 1e-12)
        m[3] = 0.1
        assertNull(Monatsraster.verkettet(m))
    }

    @Test fun texteInBeidenSprachen() {
        assertEquals(RasterTexte.schluessel(true), RasterTexte.schluessel(false))
        for (k in RasterTexte.schluessel(true)) for (d in listOf(true, false)) {
            val x = RasterTexte.text(d, k, listOf("2025", "+5,0 %"))
            assertTrue(k, x.isNotBlank())
            assertFalse(k, x.contains('{') || x.contains('—') || x.contains('…') || x.contains('?') || x.contains(';'))
        }
        assertEquals("2025: +5,0 %", RasterTexte.text(true, "kern", listOf("2025", "+5,0 %")))
    }
}
