package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Faktorzerlegung.Faktor
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.FaktorTexte
import de.troetenrepublik.app.ui.PortfolioTool
import de.troetenrepublik.app.ui.WERKZEUG_GRUPPEN
import de.troetenrepublik.app.ui.werkzeugFormen
import de.troetenrepublik.app.market.FaktorAbruf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sqrt
import kotlin.random.Random

// Fassung 0.78 U40: Faktorzerlegung des Depots. Lesen der Faktordateien, Monatsrenditen, Regression mit Standardfehlern,
// Zerlegung ueber den ganzen Zeitraum und rollend.
class Fassung078U40FaktorTest {

    private fun m(j: Int, mo: Int) = Faktorzerlegung.monat(j, mo)
    private fun t(d: String) = LocalDate.parse(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    // Aufbau wie die Monatsdateien: Vorspann, Kopfzeile mit fuehrendem Komma, Monate in Prozent, danach die Jahrestabelle
    private val fuenf = """
        Diese Datei wurde zu Testzwecken erzeugt.
        Der Zins steht in der letzten Spalte, alle Werte in Prozent.

        ,Mkt-RF,SMB,HML,RMW,CMA,RF
        202001,    1.50,   -0.40,    0.80,    0.20,   -0.10,    0.12
        202002,   -2.25,    0.60,   -1.00,    0.35,    0.05,    0.11
        202003,   -99.99,   0.10,    0.20,   -0.30,    0.15,    0.10

         Annual Factors: January-December
        ,Mkt-RF,SMB,HML,RMW,CMA,RF
          2020,   12.00,    3.00,   -4.00,    1.00,    0.50,    1.30
    """.trimIndent()

    private val mom = """
        Momentum, gebildet aus sechs Portfolios nach Groesse und frueherer Rendite.

        ,Mom
        202001,   0.90
        202002,  -1.10

        Annual Factors:
        January-December
        ,Mom
          2020,   5.00
    """.trimIndent()

    @Test
    fun frenchDateienLesen() {
        val f = Faktorzerlegung.frenchLesen(fuenf)
        assertEquals(setOf("Mkt-RF", "SMB", "HML", "RMW", "CMA", "RF"), f.keys)
        assertEquals(0.015, f.getValue("Mkt-RF").getValue(m(2020, 1)), 1e-12)
        assertEquals(-0.0225, f.getValue("Mkt-RF").getValue(m(2020, 2)), 1e-12)
        // Fehlwert faellt weg, die Jahrestabelle zaehlt nicht
        assertFalse(m(2020, 3) in f.getValue("Mkt-RF"))
        assertEquals(3, f.getValue("SMB").size)
        assertEquals(0.0011, f.getValue("RF").getValue(m(2020, 2)), 1e-12)
        val d = Faktorzerlegung.ausFrench(fuenf, mom)!!
        assertEquals(0.0035, d.faktoren.getValue(Faktor.QUALITAET).getValue(m(2020, 2)), 1e-12)
        assertEquals(-0.011, d.faktoren.getValue(Faktor.MOMENTUM).getValue(m(2020, 2)), 1e-12)
        assertEquals(m(2020, 2), d.bis)
        assertNull(Faktorzerlegung.ausFrench(fuenf, "ohne Tabelle"))
        assertNull(Faktorzerlegung.ausFrench("", mom))
    }

    @Test
    fun weltweiteDateienLesen() {
        val themen = """
            location,name,freq,weighting,n_countries,date,ret
            world,size,monthly,vw_cap,40,2020-01-31,0.004
            world,value,monthly,vw_cap,40,2020-01-31,-0.012
            world,quality,monthly,vw_cap,40,2020-01-31,0.003
            world,momentum,monthly,vw_cap,40,2020-01-31,0.02
            world,low_risk,monthly,vw_cap,40,2020-01-31,0.01
            world,size,monthly,vw_cap,40,2020-02-29,kaputt
        """.trimIndent()
        val markt = """
            location,name,freq,weighting,direction,n_countries,date,ret
            world,mkt,monthly,vw_cap,na,80,2020-02-29,-0.08
            world,mkt,monthly,vw_cap,na,80,2020-01-31,-0.01
        """.trimIndent()
        val t = Faktorzerlegung.jkpLesen(themen)
        assertEquals(0.004, t.getValue("size").getValue(m(2020, 1)), 1e-12)
        assertEquals(1, t.getValue("size").size)
        val d = Faktorzerlegung.ausJkp(markt, themen, mapOf(m(2020, 1) to 0.001))!!
        assertEquals(-0.08, d.faktoren.getValue(Faktor.MARKT).getValue(m(2020, 2)), 1e-12)
        assertEquals(-0.012, d.faktoren.getValue(Faktor.WERT).getValue(m(2020, 1)), 1e-12)
        // Der gemeinsame Stand endet beim kuerzesten Faktor
        assertEquals(m(2020, 1), d.bis)
        assertNull(Faktorzerlegung.ausJkp(markt, themen, emptyMap()))
        assertNull(Faktorzerlegung.ausJkp("location,name,date,ret", themen, mapOf(1 to 0.0)))
        assertTrue(Faktorzerlegung.jkpLesen("a,b,c\n1,2,3").isEmpty())
    }

    @Test
    fun adresseUndArchiv() {
        assertEquals(
            "https://jkpfactors-data.s3.amazonaws.com/public/%5Bworld%5D_%5Bmkt%5D_%5Bmonthly%5D_%5Bvw_cap%5D.zip",
            FaktorAbruf.jkpAdresse(FaktorAbruf.WELT, "mkt"),
        )
        val aus = ByteArrayOutputStream()
        ZipOutputStream(aus).use { z ->
            z.putNextEntry(ZipEntry("liesmich.txt")); z.write("nichts".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("daten.CSV")); z.write(mom.toByteArray()); z.closeEntry()
        }
        assertEquals(mom, FaktorAbruf.csvAusZip(aus.toByteArray()))
        assertNull(FaktorAbruf.csvAusZip("kein Archiv".toByteArray()))
    }

    @Test
    fun monatsrenditenMitWechselkurs() {
        val kurse = listOf(
            t("2024-01-30") to 100.0, t("2024-01-31") to 100.0, t("2024-02-15") to 104.0, t("2024-02-29") to 110.0,
            t("2024-03-28") to 99.0, t("2024-04-10") to 120.0,
        )
        val r = Faktorzerlegung.monatsrenditen(kurse, null, t("2024-04-11"))
        // April laeuft noch und zaehlt nicht
        assertEquals(setOf(m(2024, 2), m(2024, 3)), r.keys)
        assertEquals(0.10, r.getValue(m(2024, 2)), 1e-12)
        assertEquals(-0.10, r.getValue(m(2024, 3)), 1e-12)
        // Wechselkurs steigt im Februar um 5 %
        val fx = listOf(t("2024-01-31") to 1.00, t("2024-02-29") to 1.05, t("2024-03-28") to 1.05)
        val u = Faktorzerlegung.monatsrenditen(kurse, fx, t("2024-04-11"))
        assertEquals(1.10 * 1.05 - 1, u.getValue(m(2024, 2)), 1e-12)
        assertEquals(-0.10, u.getValue(m(2024, 3)), 1e-12)
        // Ohne Wechselkurs bis zum Tag zaehlt der Kurs nicht
        val spaet = Faktorzerlegung.monatsrenditen(kurse, listOf(t("2024-02-29") to 1.0, t("2024-03-28") to 1.0), t("2024-04-11"))
        assertEquals(setOf(m(2024, 3)), spaet.keys)
        // Luecke von einem Monat: keine Rendite ueber die Luecke
        val luecke = Faktorzerlegung.monatsrenditen(listOf(t("2024-01-31") to 1.0, t("2024-03-28") to 2.0, t("2024-04-30") to 3.0))
        assertEquals(setOf(m(2024, 4)), luecke.keys)
    }

    @Test
    fun depotAusBestand() {
        val a = Faktorzerlegung.Bestand(60.0, mapOf(1 to 0.10, 2 to 0.00, 3 to 0.05))
        val b = Faktorzerlegung.Bestand(30.0, mapOf(2 to 0.03, 3 to -0.01))
        val c = Faktorzerlegung.Bestand(10.0, emptyMap())
        val d = Faktorzerlegung.depotMonate(listOf(a, b, c))
        // Monat 1: nur 60 % abgedeckt, faellt weg
        assertEquals(setOf(2, 3), d.keys)
        assertEquals(0.01, d.getValue(2).first, 1e-12)
        assertEquals(0.9, d.getValue(2).second, 1e-12)
        assertEquals((60 * 0.05 - 30 * 0.01) / 90, d.getValue(3).first, 1e-12)
        assertTrue(Faktorzerlegung.depotMonate(emptyList()).isEmpty())
    }

    @Test
    fun regressionTrifftBekannteWerte() {
        // Ohne Rauschen: Koeffizienten genau, R2 gleich 1
        val r = Random(40)
        val x1 = DoubleArray(30) { r.nextDouble(-0.1, 0.1) }
        val x2 = DoubleArray(30) { r.nextDouble(-0.05, 0.05) }
        val y = DoubleArray(30) { 0.002 + 1.1 * x1[it] - 0.3 * x2[it] }
        val g = Faktorzerlegung.regression(y, listOf(x1, x2))!!
        assertEquals(0.002, g.koeffizienten[0], 1e-10)
        assertEquals(1.1, g.koeffizienten[1], 1e-10)
        assertEquals(-0.3, g.koeffizienten[2], 1e-10)
        assertEquals(1.0, g.r2, 1e-10)
        assertEquals(27, g.freiheitsgrade)
        // Eine Variable: Steigung, Standardfehler und R2 nach der Lehrbuchformel
        val x = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val z = doubleArrayOf(2.1, 3.9, 6.2, 7.8, 10.1, 11.9)
        val e = Faktorzerlegung.regression(z, listOf(x))!!
        val mx = x.average()
        val mz = z.average()
        val sxx = x.sumOf { (it - mx) * (it - mx) }
        val b = x.indices.sumOf { (x[it] - mx) * (z[it] - mz) } / sxx
        val a = mz - b * mx
        val rest = x.indices.sumOf { val f = z[it] - a - b * x[it]; f * f }
        assertEquals(b, e.koeffizienten[1], 1e-10)
        assertEquals(a, e.koeffizienten[0], 1e-10)
        assertEquals(sqrt(rest / 4 / sxx), e.fehler[1], 1e-10)
        assertEquals(sqrt(rest / 4 * (1.0 / 6 + mx * mx / sxx)), e.fehler[0], 1e-10)
        assertEquals(1 - rest / z.sumOf { (it - mz) * (it - mz) }, e.r2, 1e-10)
        // Abhaengige Spalten, zu wenige Beobachtungen, keine Streuung
        assertNull(Faktorzerlegung.regression(z, listOf(x, DoubleArray(6) { 2 * x[it] })))
        assertNull(Faktorzerlegung.regression(doubleArrayOf(1.0, 2.0), listOf(doubleArrayOf(1.0, 2.0))))
        assertNull(Faktorzerlegung.regression(DoubleArray(6) { 1.0 }, listOf(x)))
    }

    @Test
    fun tWerte() {
        assertEquals(2.571, Faktorzerlegung.tWert95(5), 0.01)
        assertEquals(2.179, Faktorzerlegung.tWert95(12), 0.005)
        assertEquals(2.000, Faktorzerlegung.tWert95(60), 0.002)
        assertEquals(1.960, Faktorzerlegung.tWert95(100_000), 0.001)
    }

    // Kuenstliche Faktoren und ein Depot mit bekannten Ladungen und Rauschen
    private fun kuenstlich(monate: Int, alpha: Double, rauschen: Double, seed: Int = 7): Pair<Map<Int, Double>, Faktorzerlegung.Faktordaten> {
        val r = Random(seed)
        fun normal() = (1..12).sumOf { r.nextDouble() } - 6.0
        val start = m(2015, 1)
        val soll = mapOf(Faktor.MARKT to 1.2, Faktor.GROESSE to 0.4, Faktor.WERT to -0.5, Faktor.QUALITAET to 0.0, Faktor.MOMENTUM to 0.3)
        val faktoren = Faktor.entries.associateWith { f -> (0 until monate).associate { start + it to normal() * (if (f == Faktor.MARKT) 0.045 else 0.025) + 0.003 } }
        val zins = (0 until monate).associate { start + it to 0.002 }
        val depot = (0 until monate).associate { i ->
            val k = start + i
            k to zins.getValue(k) + alpha + Faktor.entries.sumOf { soll.getValue(it) * faktoren.getValue(it).getValue(k) } + normal() * rauschen
        }
        return depot to Faktorzerlegung.Faktordaten(faktoren, zins)
    }

    @Test
    fun zerlegungFindetLadungen() {
        val (depot, daten) = kuenstlich(120, 0.001, 0.004)
        val e = Faktorzerlegung.zerlegen(depot, daten)!!
        assertEquals(120, e.monate)
        assertEquals(m(2015, 1), e.von)
        assertEquals(m(2024, 12), e.bis)
        val soll = mapOf(Faktor.MARKT to 1.2, Faktor.GROESSE to 0.4, Faktor.WERT to -0.5, Faktor.QUALITAET to 0.0, Faktor.MOMENTUM to 0.3)
        for (l in e.ladungen) {
            val s = soll.getValue(l.faktor)
            assertTrue("${l.faktor}: $l", s > l.von - 0.03 && s < l.bis + 0.03)
            assertEquals(s, l.wert, 0.06)
            assertTrue(l.fehler > 0 && l.von < l.wert && l.wert < l.bis)
        }
        assertTrue(e.ladung(Faktor.MARKT)!!.belegt)
        assertTrue(e.ladung(Faktor.WERT)!!.belegt)
        assertEquals(0.0, e.ladung(Faktor.QUALITAET)!!.wert, 0.06)
        assertTrue(e.r2 > 0.95)
        // Alpha 1,2 % im Jahr, die Spanne umschliesst den Schaetzwert
        assertEquals(0.012, e.alphaJahr, 0.02)
        assertTrue(e.alphaVon < e.alphaJahr && e.alphaJahr < e.alphaBis)
        // Zins, Beitraege und Rest ergeben die mittlere Depotrendite
        assertEquals(e.renditeJahr, e.zinsJahr + e.ladungen.sumOf { it.beitragJahr } + e.alphaJahr, 1e-10)
        assertEquals(0.024, e.zinsJahr, 1e-12)
        // Nur die letzten 36 Monate
        val kurz = Faktorzerlegung.zerlegen(depot, daten, 36)!!
        assertEquals(36, kurz.monate)
        assertEquals(m(2022, 1), kurz.von)
        assertTrue(kurz.ladung(Faktor.MARKT)!!.fehler > e.ladung(Faktor.MARKT)!!.fehler)
    }

    @Test
    fun zuWenigeMonateUndFehlendeFaktoren() {
        val (depot, daten) = kuenstlich(17, 0.0, 0.004)
        assertNull(Faktorzerlegung.zerlegen(depot, daten))
        val (lang, d2) = kuenstlich(40, 0.0, 0.004)
        assertNotNull(Faktorzerlegung.zerlegen(lang, d2))
        // Monate ohne Zins oder ohne einen Faktor zaehlen nicht
        val ohne = Faktorzerlegung.Faktordaten(d2.faktoren, d2.zins.filterKeys { it < m(2015, 1) + 10 })
        assertEquals(10, Faktorzerlegung.gemeinsameMonate(lang, ohne).size)
        assertNull(Faktorzerlegung.zerlegen(lang, ohne))
        val luecke = Faktorzerlegung.Faktordaten(d2.faktoren + (Faktor.WERT to emptyMap()), d2.zins)
        assertFalse(luecke.vollstaendig())
        assertTrue(Faktorzerlegung.gemeinsameMonate(lang, luecke).isEmpty())
    }

    @Test
    fun rollendeLadungen() {
        val (depot, daten) = kuenstlich(60, 0.0, 0.004)
        val r = Faktorzerlegung.rollend(depot, daten)
        assertEquals(60 - Faktorzerlegung.FENSTER + 1, r.size)
        assertEquals(m(2015, 1) + Faktorzerlegung.FENSTER - 1, r.first().first)
        assertEquals(m(2019, 12), r.last().first)
        assertTrue(r.all { it.second.monate == Faktorzerlegung.FENSTER })
        assertTrue(r.all { kotlin.math.abs(it.second.ladung(Faktor.MARKT)!!.wert - 1.2) < 0.25 })
        // Unter 36 Monaten kein Verlauf
        val (kurz, d2) = kuenstlich(30, 0.0, 0.004)
        assertTrue(Faktorzerlegung.rollend(kurz, d2).isEmpty())
    }

    @Test
    fun monatsschluessel() {
        assertEquals(2024, Faktorzerlegung.jahrVon(m(2024, 12)))
        assertEquals(12, Faktorzerlegung.monatVon(m(2024, 12)))
        assertEquals(1, Faktorzerlegung.monatVon(m(2025, 1)))
        assertEquals(m(2024, 2), Faktorzerlegung.monat(t("2024-02-29")))
    }

    @Test
    fun werkzeugInDerListe() {
        val name = PortfolioTool.FAKTOREN.name
        assertEquals(1, WERKZEUG_GRUPPEN.count { name in it.second })
        assertTrue(werkzeugFormen(name).isNotEmpty())
        assertEquals("Faktoren", De.ptTitle(name))
        assertEquals("Factors", En.ptTitle(name))
        for (s in listOf(De, En)) {
            assertTrue(s.ptSubtitle(name).isNotBlank())
            assertFalse(s.ptSubtitle(name).contains('?'))
        }
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(FaktorTexte.schluessel(true), FaktorTexte.schluessel(false))
        for (k in FaktorTexte.schluessel(true)) for (d in listOf(true, false)) {
            val text = FaktorTexte.text(d, k, listOf("1", "2", "3"))
            assertTrue(k, text.isNotBlank())
            assertFalse(k, text.contains("{"))
            listOf("—", "–", "…", "bullisch", "bärisch").forEach { assertFalse("$k: $it", text.contains(it)) }
        }
        for (f in Faktor.entries) {
            assertTrue("f.${f.name}" in FaktorTexte.schluessel(true))
            assertTrue("f.${f.name}.links" in FaktorTexte.schluessel(true))
        }
    }
}
