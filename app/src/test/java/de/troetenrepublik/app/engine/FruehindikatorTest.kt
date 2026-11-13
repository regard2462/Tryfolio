package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import de.troetenrepublik.app.engine.Fruehindikator.Phase as P
import java.time.YearMonth

// Globus Trend: Phase nach OECD, Lesen der DBnomics-, FRED- und Eurostat-Antworten (gekuerzt), Zusammenfuehren, Anteil im Abschwung.
class FruehindikatorTest {

    @Test
    fun phasenNachNiveauUndRichtung() {
        assertEquals(P.AUFSCHWUNG, Fruehindikator.phase(101.0, 100.8))
        assertEquals(P.ABSCHWAECHUNG, Fruehindikator.phase(100.2, 100.5))
        assertEquals(P.ABSCHWUNG, Fruehindikator.phase(99.5, 99.8))
        assertEquals(P.ERHOLUNG, Fruehindikator.phase(99.5, 99.2))
        assertNull(Fruehindikator.phase(listOf(Fruehindikator.Punkt("2026-09", 100.0))))
    }

    private val dbnomics = """{"_meta":{"args":{}},"errors":[],"series":{"docs":[
      {"dimensions":{"REF_AREA":"DEU","FREQ":"M","MEASURE":"LI"},"period":["2026-02","2026-03","2026-04","2026-05"],"value":[100.2,100.5,"NA",100.8381]},
      {"dimensions":{"REF_AREA":"CHN","FREQ":"M","MEASURE":"LI"},"period":["2026-03","2026-04","2026-05"],"value":[99.1,98.99,98.9058]},
      {"dimensions":{"REF_AREA":"G7","FREQ":"M","MEASURE":"LI"},"period":["2026-05"],"value":[100.8455]}],
      "limit":1000,"num_found":3,"offset":0}}"""

    @Test
    fun dbnomicsLesen() {
        val r = Fruehindikator.lesenDbnomics(dbnomics, setOf("DEU", "CHN", "USA"))!!
        assertEquals(setOf("DEU", "CHN"), r.keys)
        // "NA" fehlt, Reihe aufsteigend
        assertEquals(listOf("2026-02", "2026-03", "2026-05"), r["DEU"]!!.map { it.monat })
        assertEquals(100.8381, r["DEU"]!!.last().wert, 1e-9)
        assertEquals(P.ABSCHWUNG, Fruehindikator.phase(r["CHN"]))
        assertNull(Fruehindikator.lesenDbnomics("{kaputt", setOf("DEU")))
    }

    private val fred = "observation_date,DEULOLITOAASTSAM,CHNLOLITOAASTSAM,XYZ\n" +
        "1956-01-01,,,\n2026-07-01,100.7947598581980,97.8840220942698,1\n2026-08-01,100.9033623489300,97.6355004075483,1\n" +
        "2026-09-01,101.0065743822700,97.4103470143474,1\n"

    @Test
    fun fredLesen() {
        val r = Fruehindikator.lesenFred(fred)!!
        assertEquals(setOf("DEU", "CHN"), r.keys)
        assertEquals(3, r["DEU"]!!.size)
        assertEquals("2026-09", r["CHN"]!!.last().monat)
        assertEquals(P.AUFSCHWUNG, Fruehindikator.phase(r["DEU"]))
        assertEquals(P.ABSCHWUNG, Fruehindikator.phase(r["CHN"]))
    }

    @Test
    fun zusammenNimmtDenNeuerenMonat() {
        val db = Fruehindikator.lesenDbnomics(dbnomics, setOf("DEU", "CHN"))!!
        val fr = Fruehindikator.lesenFred(fred)!!
        val z = Fruehindikator.zusammen(db, fr)
        assertEquals("2026-09", z["DEU"]!!.last().monat)
        assertEquals(YearMonth.of(2026, 9), Fruehindikator.neuesterMonat(z))
        assertFalse(Fruehindikator.frisch(db, YearMonth.of(2026, 11)))
        assertTrue(Fruehindikator.frisch(z, YearMonth.of(2026, 11)))
        // Land nur in einer Quelle bleibt erhalten
        assertNotNull(Fruehindikator.zusammen(mapOf("JPN" to listOf(Fruehindikator.Punkt("2026-05", 100.0))), fr)["JPN"])
    }

    @Test
    fun anteilImAbschwungZaehltNurAktuelles() {
        val d = Fruehindikator.Datensatz(0, mapOf(
            "CHN" to listOf(Fruehindikator.Punkt("2026-08", 97.6), Fruehindikator.Punkt("2026-09", 97.4)),
            "DEU" to listOf(Fruehindikator.Punkt("2026-08", 100.9), Fruehindikator.Punkt("2026-09", 101.0)),
            "RUS" to listOf(Fruehindikator.Punkt("2021-12", 101.0), Fruehindikator.Punkt("2022-01", 103.0)),
        ))
        val a = Fruehindikator.anteilAbschwung(d, YearMonth.of(2026, 10))
        assertEquals(1, a.abschwung)
        assertEquals(2, a.mitDaten)
    }

    // Gekuerzt aus ei_bssi_m_r2 (JSON-stat 2.0): Dimensionen freq, indic, s_adj, geo, time
    private val esi = """{"version":"2.0","class":"dataset","id":["freq","indic","s_adj","geo","time"],"size":[1,1,1,3,3],
      "value":{"0":95.7,"1":97.2,"2":98.1,"3":91.7,"4":92.9,"5":94.2,"6":107.7,"7":107.1,"8":106.8},
      "dimension":{"geo":{"category":{"index":{"EA20":0,"DE":1,"EL":2}}},"time":{"category":{"index":{"2026-08":0,"2026-09":1,"2026-10":2}}}}}"""

    @Test
    fun esiLesen() {
        val r = Fruehindikator.lesenEsi(esi)!!
        assertEquals(setOf("DE", "GR"), r.keys)
        assertEquals(listOf(91.7, 92.9, 94.2), r["DE"]!!.map { it.wert })
        assertEquals("2026-10", r["GR"]!!.last().monat)
        assertEquals(106.8, r["GR"]!!.last().wert, 1e-9)
        assertNull(Fruehindikator.lesenEsi("[]"))
    }

    @Test
    fun speichernUndLesen() {
        val d = Fruehindikator.Datensatz(5L, mapOf("DEU" to listOf(Fruehindikator.Punkt("2026-09", 101.0))))
        assertEquals(d, Fruehindikator.lesenGespeichert(Fruehindikator.schreiben(d)))
        assertTrue(Fruehindikator.veraltet(d, 5L + Fruehindikator.GUELTIG_MS))
        assertFalse(Fruehindikator.veraltet(d, 6L))
    }
}
