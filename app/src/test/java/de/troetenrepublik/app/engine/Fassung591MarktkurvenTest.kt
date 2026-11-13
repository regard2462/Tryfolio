package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.91: Zinskurven der USA und des Euroraums, VIX-Kurve.
class Fassung591MarktkurvenTest {
    private val tag = 86_400_000L

    private val treasury = """
        Date,"1 Mo","1.5 Month","2 Mo","3 Mo","4 Mo","6 Mo","1 Yr","2 Yr","3 Yr","5 Yr","7 Yr","10 Yr","20 Yr","30 Yr"
        10/23/2026,4.11,4.11,4.16,4.22,4.30,4.30,4.44,4.69,4.75,4.82,4.89,5.00,5.37,5.33
        09/22/2026,4.30,,4.35,4.40,4.42,4.41,4.38,4.20,4.15,4.12,4.18,4.25,4.60,4.55
        10/22/2025,4.20,N/A,4.22,4.21,4.20,4.15,4.05,4.00,4.01,4.10,4.15,4.21,4.70,4.65
    """.trimIndent()

    private val ezb = """
        KEY,FREQ,REF_AREA,CURRENCY,PROVIDER_FM,INSTRUMENT_FM,PROVIDER_FM_ID,DATA_TYPE_FM,TIME_PERIOD,OBS_VALUE
        YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_10Y,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_10Y,2026-10-22,3.41
        YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_10Y,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_10Y,2026-10-23,3.4444646497
        YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_3M,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_3M,2026-10-23,2.05
        YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_2Y,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_2Y,2026-10-23,2.3
        YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_30Y,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_30Y,2026-10-23,3.9
        YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_3M,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_3M,2026-10-22,2.04
        YC.B.U2.EUR.4F.G_N_A.SV_C_YM.SR_6M,B,U2,EUR,4F,G_N_A,SV_C_YM,SR_6M,2026-10-22,
    """.trimIndent()

    @Test fun laufzeiten() {
        assertEquals(30, Marktkurven.laufzeit("1 Mo"))
        assertEquals(46, Marktkurven.laufzeit("\"1.5 Month\""))
        assertEquals(91, Marktkurven.laufzeit("3 Mo"))
        assertEquals(3650, Marktkurven.laufzeit("10 Yr"))
        assertEquals(183, Marktkurven.laufzeit("SR_6M"))
        assertEquals(10950, Marktkurven.laufzeit("SR_30Y"))
        assertNull(Marktkurven.laufzeit("Date"))
    }

    @Test fun treasuryAlleLaufzeiten() {
        val k = Marktkurven.treasuryKurven(treasury)
        assertEquals(3, k.size)
        // aelteste zuerst, leere und N/A-Felder fehlen
        assertEquals(13, k[0].punkte.size)
        assertEquals(13, k[1].punkte.size)
        val heute = k[2]
        assertEquals(14, heute.punkte.size)
        assertEquals(4.11, heute.punkte.first().wert, 1e-9)
        assertEquals(5.33, heute.punkte.last().wert, 1e-9)
        assertEquals(5.00, heute.wert(Marktkurven.ZEHN_JAHRE)!!, 1e-9)
        val v = Marktkurven.vergleich(k)!!
        assertEquals(heute.tag, v.heute.tag)
        assertEquals(heute.tag - 31, v.monat!!.tag)
        assertEquals(heute.tag - 366, v.jahr!!.tag)
        assertEquals(Marktkurven.Form.NORMAL, Marktkurven.form(heute))
        // 22.09.2026: kurze Laufzeiten bis 4,42 % ueber zehn Jahren mit 4,25 %
        assertEquals(Marktkurven.Form.INVERS, Marktkurven.form(v.monat!!))
        assertEquals(listOf(30, 61, 91, 122, 183, 365), Marktkurven.invertiert(v.monat!!).map { it.tage })
        // 22.10.2025: 4,22 % gegen 4,21 %
        assertEquals(Marktkurven.Form.INVERS, Marktkurven.form(v.jahr!!))
        val (kurz, abstand) = Marktkurven.abstand(heute)!!
        assertEquals(91, kurz)
        assertEquals(0.78, abstand, 1e-9)
    }

    @Test fun stichtageFehlenBeiLuecke() {
        val k = listOf(
            Marktkurven.Kurve(1000, listOf(Marktkurven.Punkt(91, 1.0), Marktkurven.Punkt(3650, 2.0))),
            Marktkurven.Kurve(1100, listOf(Marktkurven.Punkt(91, 1.0), Marktkurven.Punkt(3650, 1.1))),
        )
        val v = Marktkurven.vergleich(k)!!
        assertNull(v.monat)
        assertNull(v.jahr)
        assertEquals(Marktkurven.Form.FLACH, Marktkurven.form(v.heute))
        assertNull(Marktkurven.vergleich(emptyList()))
    }

    @Test fun ezbEineAbfrage() {
        val a = Marktkurven.ezbAdresse("2025-10-01")
        assertTrue(a.startsWith("https://data-api.ecb.europa.eu/service/data/YC/B.U2.EUR.4F.G_N_A.SV_C_YM.SR_3M+SR_6M+SR_1Y+"))
        assertTrue(a.endsWith("SR_30Y?format=csvdata&detail=dataonly&startPeriod=2025-10-01"))
        val k = Marktkurven.ezbKurven(ezb)
        assertEquals(2, k.size)
        assertEquals(listOf(91, 3650), k[0].punkte.map { it.tage })
        val heute = k[1]
        assertEquals(listOf(91, 730, 3650, 10950), heute.punkte.map { it.tage })
        assertEquals(3.4444646497, heute.wert(3650)!!, 1e-9)
        assertEquals(Marktkurven.Form.NORMAL, Marktkurven.form(heute))
        assertTrue(Marktkurven.invertiert(heute).isEmpty())
        assertTrue(Marktkurven.ezbKurven("Fehler").isEmpty())
    }

    @Test fun speichernUndLesen() {
        val v = Marktkurven.vergleich(Marktkurven.treasuryKurven(treasury))!!
        val z = Marktkurven.Zinskurven(123L, v, null)
        assertEquals(z, Marktkurven.lesen(Marktkurven.schreiben(z)))
        assertNull(Marktkurven.lesen("kaputt"))
    }

    @Test fun vixKurve() {
        val jetzt = 20_400 * tag + 20 * 3_600_000L
        fun reihe(heute: Double, vorher: Double) = listOf(jetzt - 40 * tag to vorher, jetzt - 31 * tag to vorher, jetzt - tag to heute, jetzt to heute)
        val reihen = mapOf(
            "^VIX1D" to reihe(28.0, 10.0), "^VIX9D" to reihe(26.0, 12.0), "^VIX" to reihe(24.0, 14.0),
            "^VIX3M" to reihe(22.0, 16.0), "^VIX6M" to reihe(21.0, 17.0),
            // Ohne Kurs vor einem Monat: fehlt in der Kurve des Vormonats
            "^VIX1Y" to listOf(jetzt to 20.0),
        )
        val (heute, monat) = Marktkurven.vixKurven(reihen)!!
        assertEquals(listOf(1, 9, 30, 93, 183, 365), heute.punkte.map { it.tage })
        assertEquals(Marktkurven.VixForm.BACKWARDATION, Marktkurven.vixForm(heute))
        assertNotNull(monat)
        assertEquals(5, monat!!.punkte.size)
        assertEquals(14.0, monat.wert(30)!!, 1e-9)
        assertEquals(Marktkurven.VixForm.CONTANGO, Marktkurven.vixForm(monat))
        val flach = Marktkurven.Kurve(1, listOf(Marktkurven.Punkt(30, 19.5), Marktkurven.Punkt(93, 20.0)))
        assertEquals(Marktkurven.VixForm.FLACH, Marktkurven.vixForm(flach))
        assertNull(Marktkurven.vixKurven(emptyMap()))
    }
}
