package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

// Prueft die Vergleichsrechnung der Fassung 5.96 mit festen Zeiten.
class Fassung596VergleichTest {

    private val tag = Vergleichsrechnung.TAG

    @Test
    fun bereinigtSortiertUndZaehltJeZeitpunktDenLetztenWert() {
        val r = listOf(
            20 * tag to 3.0,
            10 * tag to 1.0,
            10 * tag to 2.0,
            15 * tag to Double.NaN,
            5 * tag to 0.0,
        )
        assertEquals(listOf(10 * tag to 2.0, 20 * tag to 3.0), Vergleichsrechnung.bereinigt(r))
    }

    @Test
    fun statusPrueftInFesterReihenfolge() {
        assertEquals(Vergleichsrechnung.Status.KEINE_DATEN, Vergleichsrechnung.status(null))
        assertEquals(Vergleichsrechnung.Status.KEINE_DATEN, Vergleichsrechnung.status(listOf(0L to 1.0)))
        // zu wenig Punkte wiegt schwerer als zu wenig Geschaefte
        assertEquals(Vergleichsrechnung.Status.KEINE_DATEN, Vergleichsrechnung.status(listOf(0L to 1.0), geschaefte = 0))
        val kurz = listOf(0L to 1.0, 1 * tag to 1.1)
        // zu wenig Geschaefte wiegt schwerer als zu kurzer Verlauf
        assertEquals(Vergleichsrechnung.Status.ZU_WENIG_GESCHAEFTE, Vergleichsrechnung.status(kurz, geschaefte = 2))
        assertEquals(Vergleichsrechnung.Status.ZU_KURZ, Vergleichsrechnung.status(kurz))
        val lang = listOf(0L to 1.0, 40 * tag to 1.1)
        assertEquals(Vergleichsrechnung.Status.OK, Vergleichsrechnung.status(lang, geschaefte = 3))
    }

    @Test
    fun gemeinsamNimmtDenSpaetstenAnfangUndZaehltAktuellesEndeAlsJetzt() {
        val jetzt = 100 * tag
        val a = listOf(0L to 1.0, 10 * tag to 1.0, 99 * tag to 1.0)
        val b = listOf(5 * tag to 1.0, 98 * tag to 1.0)
        // beide Enden liegen nah genug an jetzt, Reihe b beginnt spaeter als gewuenscht
        assertEquals(Vergleichsrechnung.Spanne(5 * tag, jetzt), Vergleichsrechnung.gemeinsam(listOf(a, b), 2 * tag, jetzt))
    }

    @Test
    fun gemeinsamKuerztVeraltetesEnde() {
        val jetzt = 100 * tag
        val a = listOf(0L to 1.0, 99 * tag to 1.0)
        val alt = listOf(0L to 1.0, 50 * tag to 1.0)
        assertEquals(Vergleichsrechnung.Spanne(0L, 50 * tag), Vergleichsrechnung.gemeinsam(listOf(a, alt), 0L, jetzt))
    }

    @Test
    fun gemeinsamIstNullBeiLeerUndZuKurz() {
        val jetzt = 100 * tag
        assertNull(Vergleichsrechnung.gemeinsam(emptyList(), 0L, jetzt))
        assertNull(Vergleichsrechnung.gemeinsam(listOf(emptyList()), 0L, jetzt))
        val a = listOf(0L to 1.0, 99 * tag to 1.1)
        val spaet = listOf(0L to 1.0, 50 * tag to 1.0)
        // der spaete gewuenschte Anfang laesst nur einen Tag uebrig
        assertNull(Vergleichsrechnung.gemeinsam(listOf(a, spaet), 49 * tag, jetzt))
    }

    @Test
    fun begrenzerIstNullWennAllesFruehGenugBeginnt() {
        val reihen = listOf(
            listOf(0L to 1.0, 60 * tag to 1.0),
            listOf(2 * tag to 1.0, 60 * tag to 1.0),
        )
        assertNull(Vergleichsrechnung.begrenzer(reihen, 0L))
    }

    @Test
    fun begrenzerNenntDieReiheMitDemSpaetstenAnfang() {
        val reihen = listOf(
            listOf(0L to 1.0, 60 * tag to 1.0),
            listOf(4 * tag to 1.0, 60 * tag to 1.0),
            listOf(2 * tag to 1.0, 60 * tag to 1.0),
        )
        assertEquals(1, Vergleichsrechnung.begrenzer(reihen, 0L))
        // genau drei Tage nach dem Wunsch gilt noch als frueh genug
        assertNull(Vergleichsrechnung.begrenzer(reihen, 1 * tag))
    }

    @Test
    fun schneidenHoltDenStartwertVomPunktDavor() {
        val r = listOf(0L to 100.0, 10 * tag to 110.0, 20 * tag to 120.0, 30 * tag to 130.0, 40 * tag to 140.0)
        // zwischen zwei Punkten: der letzte Punkt davor liefert den Wert am Tag von
        assertEquals(
            listOf(5 * tag to 100.0, 10 * tag to 110.0, 20 * tag to 120.0),
            Vergleichsrechnung.schneiden(r, 5 * tag, 25 * tag),
        )
        // Punkt bei von bleibt, bis zaehlt mit
        assertEquals(
            listOf(10 * tag to 110.0, 20 * tag to 120.0, 30 * tag to 130.0),
            Vergleichsrechnung.schneiden(r, 10 * tag, 30 * tag),
        )
    }

    @Test
    fun einmalAufLinearerVerdopplung() {
        val tage = 100
        val r = (0 until tage).map { it.toLong() * tag to 100.0 + 100.0 * it / (tage - 1) }
        val e = Vergleichsrechnung.einmal(r, 1_000.0, Vergleichsrechnung.Spanne(0L, (tage - 1).toLong() * tag))!!
        assertEquals(0L, e.start.t)
        assertEquals(2_000.0, e.value, 1e-6)
        assertEquals(1_000.0, e.gain, 1e-6)
        assertEquals(100.0, e.pct, 1e-6)
    }

    @Test
    fun sparplanAufKonstanterTagesreihe() {
        val t0 = 1_672_531_200_000L // 1. Januar 2023, Mitternacht UTC
        val r = (0 until 181).map { t0 + it.toLong() * tag to 100.0 } // bis 30. Juni 2023
        val p = Vergleichsrechnung.sparplan(r, 100.0, Vergleichsrechnung.Spanne(t0, t0 + 180 * tag), ZoneOffset.UTC)!!
        assertEquals(6, p.executions)
        assertEquals(600.0, p.invested, 1e-9)
        assertEquals(600.0, p.value, 1e-9)
        assertEquals(0.0, p.gain, 1e-9)
    }

    @Test
    fun sparplanAufWochenreiheKauftJeMonatEinmal() {
        val t0 = 1_672_531_200_000L
        val r = (0..25).map { t0 + it * 7L * tag to 100.0 }
        val p = Vergleichsrechnung.sparplan(r, 50.0, Vergleichsrechnung.Spanne(t0, t0 + 175 * tag), ZoneOffset.UTC)!!
        assertEquals(6, p.executions)
        assertEquals(300.0, p.invested, 1e-9)
        assertEquals(0.0, p.gain, 1e-9)
    }

    @Test
    fun laborSchluesselUndRegelHinUndZurueck() {
        StrategieLabor.REGELN.forEach { r ->
            assertEquals(r, Vergleichsrechnung.laborRegel(Vergleichsrechnung.laborSchluessel(r)))
        }
        assertEquals("SMA-200-0-0", Vergleichsrechnung.laborSchluessel(StrategieLabor.Regel(StrategieLabor.Art.SMA, 200)))
        assertNull(Vergleichsrechnung.laborRegel("XXX-1-0-0"))
        assertNull(Vergleichsrechnung.laborRegel("SMA-200-0"))
        assertNull(Vergleichsrechnung.laborRegel("SMA-200-x-0"))
    }

    @Test
    fun laborReiheFolgtMitHaltenDemKurs() {
        val kurse = listOf(100.0, 100.0, 120.0, 90.0, 150.0, 130.0, 180.0, 110.0, 160.0, 200.0)
        val kerzen = kurse.mapIndexed { i, k -> Candle(i.toLong() * tag, k, k, k, k) }
        val regel = StrategieLabor.Regel(StrategieLabor.Art.HALTEN)
        val reihe = Vergleichsrechnung.laborReihe(kerzen, regel, StrategieLabor.Kosten(0.0, 0.0))!!
        assertEquals(kurse.size, reihe.size)
        // Kauf zur Eroeffnung des ersten Tags, open = close: die Reihe beginnt beim Startkapital
        assertEquals(StrategieLabor.START, reihe.first().second, 1e-9)
        assertEquals(kurse.last() / kurse.first(), reihe.last().second / reihe.first().second, 1e-9)
        assertNull(Vergleichsrechnung.laborReihe(kerzen.take(1), regel, StrategieLabor.Kosten(0.0, 0.0)))
        assertNull(Vergleichsrechnung.laborReihe(emptyList(), regel, StrategieLabor.Kosten(0.0, 0.0)))
    }
}
