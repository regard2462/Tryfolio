package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.HeatmapGruppenName.Form
import de.troetenrepublik.app.engine.HeatmapGruppenName.Kandidat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung077BTest {
    private val kopf = 16f
    private val rand = 8f
    private fun k(f: Form, breite: Float, zeilen: Int = 1) = Kandidat(f, 10f, breite, 12f * zeilen, zeilen, f)

    // voll 120 breit, kurz 60 breit, zweizeilig 70 breit
    private fun folge() = sequenceOf(k(Form.VOLL, 120f), k(Form.KURZ, 60f), k(Form.ZWEIZEILIG, 70f, 2))

    @Test fun vollWennEsPasst() {
        assertEquals(Form.VOLL, HeatmapGruppenName.waehlen(folge(), 140f, 60f, rand, kopf)?.form)
    }

    @Test fun kurzWennVollZuBreitIst() {
        assertEquals(Form.KURZ, HeatmapGruppenName.waehlen(folge(), 80f, 60f, rand, kopf)?.form)
    }

    @Test fun zweizeiligNurBeiHoherGruppe() {
        val flach = sequenceOf(k(Form.VOLL, 120f), k(Form.ZWEIZEILIG, 70f, 2))
        assertEquals(Form.ZWEIZEILIG, HeatmapGruppenName.waehlen(flach, 80f, 90f, rand, kopf)?.form)
        assertNull(HeatmapGruppenName.waehlen(flach, 80f, 60f, rand, kopf))
    }

    @Test fun erstAbZoomWennNichtsPasst() {
        assertNull(HeatmapGruppenName.waehlen(folge(), 40f, 200f, rand, kopf))
        // dieselbe Gruppe, doppelt so gross gezoomt: jetzt die kurze Form
        assertEquals(Form.KURZ, HeatmapGruppenName.waehlen(folge(), 40f * 2f, 200f * 2f, rand, kopf)?.form)
        // vierfach: der volle Name
        assertEquals(Form.VOLL, HeatmapGruppenName.waehlen(folge(), 40f * 4f, 200f * 4f, rand, kopf)?.form)
    }

    @Test fun zuNiedrigOderZuSchmalGibtNichts() {
        assertNull(HeatmapGruppenName.waehlen(folge(), 300f, 40f, rand, kopf))
        assertNull(HeatmapGruppenName.waehlen(folge(), 30f, 300f, rand, kopf))
    }

    @Test fun kurzNurWennVerschieden() {
        assertTrue(HeatmapGruppenName.kurzNoetig("Banken & Finanzen", "Banken"))
        assertFalse(HeatmapGruppenName.kurzNoetig("Fahrzeuge", "Fahrzeuge"))
        assertFalse(HeatmapGruppenName.kurzNoetig("Fahrzeuge", ""))
    }
}
