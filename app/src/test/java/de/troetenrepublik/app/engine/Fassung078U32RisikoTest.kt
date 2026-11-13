package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.DepotRegeln
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.Risikoregeln.Grenze
import de.troetenrepublik.app.engine.Risikoregeln.Regel
import de.troetenrepublik.app.i18n.RisikoTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

// Risikoregeln, Positionsgroesse mit Grenzen und Chance zu Risiko (0.78), alle Zahlen erfunden.
class Fassung078U32RisikoTest {

    private val tagMs = 86_400_000L
    private val jetzt = 20_000L * tagMs + 15 * 3_600_000L

    private fun b(type: TxType, ts: Long = jetzt - 3_600_000L, realized: Double = 0.0, via: String = "", tag: String = "") =
        Transaction(id = "t$ts$type$via$tag$realized", ts = ts, type = type, title = "", realized = realized, via = via, tag = tag)

    @Test
    fun chanceZuRisikoUndTrefferquote() {
        assertEquals(2.0, Risikoregeln.chanceRisiko(100.0, 95.0, 110.0)!!, 1e-9)
        assertEquals(0.5, Risikoregeln.chanceRisiko(100.0, 90.0, 105.0)!!, 1e-9)
        // Stopp ueber dem Kurs, Ziel unter dem Kurs oder fehlende Werte
        assertNull(Risikoregeln.chanceRisiko(100.0, 101.0, 110.0))
        assertNull(Risikoregeln.chanceRisiko(100.0, 95.0, 99.0))
        assertNull(Risikoregeln.chanceRisiko(100.0, 0.0, 110.0))
        assertNull(Risikoregeln.chanceRisiko(100.0, 95.0, 0.0))
        assertEquals(1.0 / 3.0, Risikoregeln.trefferquote(2.0)!!, 1e-12)
        assertEquals(0.5, Risikoregeln.trefferquote(1.0)!!, 1e-12)
        assertEquals(0.25, Risikoregeln.trefferquote(3.0)!!, 1e-12)
        assertNull(Risikoregeln.trefferquote(0.0))
        assertEquals(110.0, Risikoregeln.ziel(100.0, 95.0, 2.0)!!, 1e-9)
        assertEquals(2.0, Risikoregeln.chanceRisiko(100.0, 95.0, Risikoregeln.ziel(100.0, 95.0, 2.0)!!)!!, 1e-9)
        assertNull(Risikoregeln.ziel(100.0, 100.0, 2.0))
    }

    @Test
    fun tagesStandAusDenBuchungen() {
        val buchungen = listOf(
            b(TxType.BUY), b(TxType.BUY, via = "ORDER"), b(TxType.BUY, via = "PLAN"), b(TxType.BUY, via = "IMPORT_TR"),
            b(TxType.BUY, tag = "BUY_CLOSE", realized = -10.0), b(TxType.SELL, tag = "SELL_OPEN"),
            b(TxType.SELL, realized = -50.0), b(TxType.SELL, realized = 20.0), b(TxType.KNOCKOUT, realized = -5.0),
            // Gestern und morgen zaehlen nicht
            b(TxType.SELL, ts = jetzt - tagMs, realized = -500.0), b(TxType.BUY, ts = jetzt - tagMs), b(TxType.BUY, ts = jetzt + tagMs),
        )
        val t = Risikoregeln.tagesStand(buchungen, jetzt, ZoneOffset.UTC)
        // Kauf von Hand, Kauf per Order und die eroeffnete Stillhalterposition
        assertEquals(3, t.geschaefte)
        assertEquals(45.0, t.verlust, 1e-9)
        // Ein Tag im Plus hat keinen Verlust
        assertEquals(0.0, Risikoregeln.tagesStand(listOf(b(TxType.SELL, realized = -50.0), b(TxType.SELL, realized = 80.0)), jetzt, ZoneOffset.UTC).verlust, 0.0)
        assertEquals(Risikoregeln.Tag(), Risikoregeln.tagesStand(emptyList(), jetzt, ZoneOffset.UTC))
    }

    @Test
    fun regelnWarnenNurWennGesetzt() {
        val ohne = Risikoregeln.Tag()
        // Standard: alles aus
        assertFalse(DepotRegeln().risikoAktiv)
        assertTrue(Risikoregeln.kaufPruefen(DepotRegeln(), 10_000.0, 100.0, 100.0, 50.0, 101.0, Risikoregeln.Tag(5_000.0, 50)).isEmpty())
        // Die alten Depotregeln allein schalten die Risikoregeln nicht ein
        assertFalse(DepotRegeln(maxPosition = 20.0, keineHebel = true, minBargeld = 10.0).risikoAktiv)

        // Hoechstverlust je Geschaeft: 20 Stueck mal 5 Euro sind 1 % von 10.000
        val r1 = DepotRegeln(maxVerlustGeschaeft = 1.0)
        assertTrue(r1.risikoAktiv)
        assertTrue(Risikoregeln.kaufPruefen(r1, 10_000.0, 100.0, 20.0, 95.0, 0.0, ohne).isEmpty())
        val zuViel = Risikoregeln.kaufPruefen(r1, 10_000.0, 100.0, 30.0, 95.0, 0.0, ohne).single()
        assertEquals(Regel.VERLUST_GESCHAEFT, zuViel.regel)
        assertEquals(1.5, zuViel.ist, 1e-9)
        assertEquals(1.0, zuViel.grenze, 0.0)
        // Ohne Stopp laesst sich der Verlust nicht pruefen
        assertTrue(Risikoregeln.kaufPruefen(r1, 10_000.0, 100.0, 90.0, 0.0, 0.0, ohne).isEmpty())

        // Hoechstverlust je Tag: erreicht ab 2 % realisiertem Verlust, auch ohne Stopp
        val r2 = DepotRegeln(maxVerlustTag = 2.0)
        assertTrue(Risikoregeln.kaufPruefen(r2, 10_000.0, 100.0, 1.0, 0.0, 0.0, Risikoregeln.Tag(verlust = 150.0)).isEmpty())
        assertEquals(Regel.VERLUST_TAG, Risikoregeln.kaufPruefen(r2, 10_000.0, 100.0, 1.0, 0.0, 0.0, Risikoregeln.Tag(verlust = 200.0)).single().regel)

        // Neue Geschaefte je Tag: das dritte bei Grenze 2
        val r3 = DepotRegeln(maxGeschaefteTag = 2)
        assertTrue(Risikoregeln.kaufPruefen(r3, 10_000.0, 100.0, 1.0, 0.0, 0.0, Risikoregeln.Tag(geschaefte = 1)).isEmpty())
        val drittes = Risikoregeln.kaufPruefen(r3, 10_000.0, 100.0, 1.0, 0.0, 0.0, Risikoregeln.Tag(geschaefte = 2)).single()
        assertEquals(Regel.GESCHAEFTE_TAG, drittes.regel)
        assertEquals(3.0, drittes.ist, 0.0)

        // Chance zu Risiko: nur mit Stopp und Ziel
        val r4 = DepotRegeln(minChanceRisiko = 2.0)
        assertTrue(Risikoregeln.kaufPruefen(r4, 10_000.0, 100.0, 10.0, 95.0, 110.0, ohne).isEmpty())
        assertTrue(Risikoregeln.kaufPruefen(r4, 10_000.0, 100.0, 10.0, 95.0, 0.0, ohne).isEmpty())
        val knapp = Risikoregeln.kaufPruefen(r4, 10_000.0, 100.0, 10.0, 95.0, 107.5, ohne).single()
        assertEquals(Regel.CHANCE_RISIKO, knapp.regel)
        assertEquals(1.5, knapp.ist, 1e-9)

        // Mehrere Regeln zugleich
        val alle = DepotRegeln(maxVerlustGeschaeft = 1.0, maxVerlustTag = 2.0, maxGeschaefteTag = 2, minChanceRisiko = 2.0)
        assertEquals(
            listOf(Regel.VERLUST_GESCHAEFT, Regel.VERLUST_TAG, Regel.GESCHAEFTE_TAG, Regel.CHANCE_RISIKO),
            Risikoregeln.kaufPruefen(alle, 10_000.0, 100.0, 30.0, 95.0, 105.0, Risikoregeln.Tag(300.0, 4)).map { it.regel },
        )
    }

    @Test
    fun positionsgroesseNimmtDieEngsteGrenze() {
        // Nur das Risiko begrenzt: wie die bisherige Rechnung
        val frei = Risikoregeln.groesse(10_000.0, 50.0, 1.0, 8.0, 0.0, 0.0, 10_000.0, 0.0, ganze = true)!!
        val alt = Analysis.positionSize(10_000.0, 50.0, 1.0, 8.0, true)!!
        assertEquals(alt.units, frei.stueck, 0.0)
        assertEquals(25.0, frei.stueck, 0.0)
        assertEquals(Grenze.RISIKO, frei.grenze)
        assertEquals(alt.stopPrice, frei.stoppKurs, 1e-9)
        assertEquals(alt.maxLoss, frei.hoechstverlust, 1e-9)
        assertFalse(Grenze.ANTEIL in frei.jeGrenze)

        // Hoechstens 10 % des Depots: 1.000 Euro, davon 400 schon im Depot, bleiben 12 Stueck
        val anteil = Risikoregeln.groesse(10_000.0, 50.0, 1.0, 8.0, 10.0, 400.0, 10_000.0, 0.0, ganze = true)!!
        assertEquals(12.0, anteil.stueck, 0.0)
        assertEquals(Grenze.ANTEIL, anteil.grenze)
        assertEquals(6.0, anteil.anteil, 1e-9)

        // Guthaben 800, Puffer 5 % des Depots und 1 Euro Gebuehr: 299 Euro, 5 Stueck
        val bar = Risikoregeln.groesse(10_000.0, 50.0, 1.0, 8.0, 10.0, 0.0, 800.0, 5.0, ganze = true)!!
        assertEquals(5.0, bar.stueck, 0.0)
        assertEquals(Grenze.BARGELD, bar.grenze)
        assertEquals(250.0, bar.kapital, 1e-9)
        assertEquals(setOf(Grenze.RISIKO, Grenze.ANTEIL, Grenze.BARGELD), bar.jeGrenze.keys)

        // Bruchstuecke auf vier Stellen, kein Guthaben ergibt 0 Stueck
        assertEquals(5.98, Risikoregeln.groesse(10_000.0, 50.0, 1.0, 8.0, 0.0, 0.0, 300.0, 0.0, ganze = false)!!.stueck, 1e-9)
        val leer = Risikoregeln.groesse(10_000.0, 50.0, 1.0, 8.0, 0.0, 0.0, 0.0, 0.0, ganze = true)!!
        assertTrue(leer.einStueckZuViel)
        assertEquals(Grenze.BARGELD, leer.grenze)
        // Anteil schon ueberschritten
        assertEquals(0.0, Risikoregeln.groesse(10_000.0, 50.0, 1.0, 8.0, 10.0, 1_500.0, 10_000.0, 0.0, ganze = true)!!.stueck, 0.0)
        assertNull(Risikoregeln.groesse(0.0, 50.0, 1.0, 8.0, 0.0, 0.0, 100.0, 0.0, ganze = true))
        assertNull(Risikoregeln.groesse(10_000.0, 50.0, 1.0, 100.0, 0.0, 0.0, 100.0, 0.0, ganze = true))

        // Die Stueckzahl aus der Positionsgroesse verletzt die gleich hohe Regel nicht
        val regel = DepotRegeln(maxVerlustGeschaeft = 1.0)
        assertTrue(Risikoregeln.kaufPruefen(regel, 10_000.0, 50.0, frei.stueck, frei.stoppKurs, 0.0, Risikoregeln.Tag()).isEmpty())
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(RisikoTexte.schluessel(true), RisikoTexte.schluessel(false))
        for (k in RisikoTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = RisikoTexte.text(d, k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{'))
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('…') || t.contains('?'))
        }
        for (r in Regel.entries) for (art in listOf("regel.", "verstoss.")) assertTrue(r.name, art + r.name in RisikoTexte.schluessel(true))
        for (g in Grenze.entries) assertTrue(g.name, "grenze." + g.name in RisikoTexte.schluessel(true))
        assertEquals("Chance zu Risiko 2,0 · Trefferquote ab 33 %", RisikoTexte.text(true, "crvZeile", listOf("2,0", "33 %")))
    }
}
