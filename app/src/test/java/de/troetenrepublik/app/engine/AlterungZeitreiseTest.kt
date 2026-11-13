package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Aktualisierung
import de.troetenrepublik.app.market.Daten
import de.troetenrepublik.app.market.Quellen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

// Zeitreise mit den echten gebuendelten Dateien: Uhr um ein, drei und fuenf Jahre vorgestellt. Nichts stuerzt ab, keine Liste
// laeuft still leer, jeder gepflegte Inhalt zeigt seinen Stand oder verschwindet, und die App meldet ihr Alter.
class AlterungZeitreiseTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    @Before fun vorbereiten() {
        Daten.assetLeser = { asset(it).readBytes() }
        Daten.fuerTest(null)
    }

    @After fun zurueck() {
        Daten.assetLeser = { error("kein Asset") }
        Daten.fuerTest(null)
        Uhr.vorstellenTage(0)
        Quellen.fuerTest(null)
    }

    private val jahre = listOf(1, 3, 5)

    @Test fun appMeldetIhrAlter() {
        for (j in jahre) {
            Uhr.vorstellenTage(365L * j)
            val stufe = Aktualisierung.alterStufe(Aktualisierung.alterMonate(Uhr.jetzt()))
            assertEquals("Nach $j Jahren", if (j == 1) 12 else 24, stufe)
        }
    }

    @Test fun strukturenBleibenMitStandOderKennzeichnung() {
        val frisch = Daten.strukturen()!!
        assertTrue(frisch.l.size > 150)
        for (j in jahre) {
            Uhr.vorstellenTage(365L * j)
            val s = Daten.strukturen()!!
            // Dieselben Laender mit denselben Problemen, nur gekennzeichnet
            assertEquals(frisch.l.keys, s.l.keys)
            assertEquals(frisch.l.values.sumOf { it.size }, s.l.values.sumOf { it.size })
            val text = s.l.values.first().first().d.de
            assertTrue("Nach $j Jahren: $text", text.contains("Stand") || text.startsWith("Veraltet"))
        }
    }

    @Test fun laenderdatenTragenIhrenStand() {
        val roh = LaenderDaten.lesen(Daten.text("globus/laenderdaten.json"), true)!!
        assertTrue(roh.laender.size > 150)
        for (j in jahre) {
            Uhr.vorstellenTage(365L * j)
            val d = LaenderDaten.gealtert(roh, true, Uhr.jetzt())
            assertEquals(roh.laender.size, d.laender.size)
            val de = d.laender.getValue("DE")
            assertTrue(de.oberhaupt.orEmpty(), de.oberhaupt.orEmpty().contains("(Stand "))
            assertNotNull(de.hauptstadt)
        }
    }

    @Test fun durchbruecheBehaltenRueckblickUndLassenLagebilderAuslaufen() {
        val inh = Durchbrueche.lesenInhalt(Daten.text(Durchbrueche.INHALT_DATEI))!!
        val lagen = inh.themen.sumOf { it.w.size }
        val rueck = inh.themen.sumOf { it.r.size }
        assertTrue(lagen > 0 && rueck > 0)
        for (j in jahre) {
            Uhr.vorstellenTage(365L * j)
            val jetzt = Uhr.jetzt()
            val blocks = Durchbrueche.ansicht(inh, emptyList(), jetzt)
            // Der Rueckblick haelt die Themen am Leben, nichts ist leer ohne Erklaerung
            assertTrue(blocks.isNotEmpty())
            val l = blocks.sumOf { it.inhalt?.w?.size ?: 0 }
            if (j >= 3) assertEquals("Lagebilder nach $j Jahren", 0, l) else assertEquals(lagen, l)
            assertTrue(blocks.sumOf { it.inhalt?.r?.size ?: 0 } == rueck)
            // Erwartetes endet ueber den Ablaufmonat
            assertTrue(blocks.sumOf { it.erwartet(jetzt).size } <= inh.themen.sumOf { it.e.size })
        }
    }

    @Test fun jederGepflegteInhaltHatNachDerSchwelleStandOderIstWeg() {
        val m = Datenbuendel.manifestLesen(Daten.text("daten/manifest.json"))!!
        for (j in jahre) {
            Uhr.vorstellenTage(365L * j)
            for ((name, d) in m.dateien) {
                assertEquals(d.stand, Daten.stand(name))
                val z = Daten.alter(name)
                val p = Alter.profilFuer(name)
                val monate = Alter.monate(d.stand, Uhr.jetzt())
                if (monate >= p.warnenMonate) assertFalse("$name nach $j Jahren", z == Alter.Zustand.FRISCH)
            }
        }
    }

    @Test fun regierungUndKongressNachJahren() {
        for (j in jahre) {
            Uhr.vorstellenTage(365L * j)
            val heute = Uhr.heute().toString()
            // Amt: nach dem Ende der Amtszeit (20.01.2029) gilt es als vorbei, nach drei Jahren immer
            if (j >= 3) assertTrue(de.troetenrepublik.app.market.RegierungOge.Amt().vorbei(heute))
            assertTrue(de.troetenrepublik.app.market.RegierungOge.ogeVermoegenSuchen(Uhr.heute().year).size >= 3)
            assertTrue(de.troetenrepublik.app.market.Behoerden.gesetzAdresse("S. 1", Uhr.heute().year)!!.contains("-congress/"))
        }
    }
}
