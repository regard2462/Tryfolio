package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Bewertungsblick.Ampel
import de.troetenrepublik.app.engine.Levermann.Groesse
import de.troetenrepublik.app.engine.Levermann.Kriterium
import de.troetenrepublik.app.engine.LynchTyp.Typ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Levermann-Punkte, Typ nach Lynch, faires KGV und Ertragswert ohne Wachstum.
class Fassung078Z4BewertungTest {

    private fun kerze(tag: LocalDate, schluss: Double) =
        Candle(tag.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 43_200_000L, schluss, schluss, schluss, schluss, 1000.0)

    private fun punkte(e: Levermann.Ergebnis, k: Kriterium): Int? = e.zeilen.first { it.kriterium == k }.punkte

    // ------------------------------------------------------------ Levermann

    private val stark = Levermann.Eingabe(
        ekRendite = 25.0, ebitMarge = 15.0, ekQuote = 30.0, kgv = 10.0, kgvSchnitt = 11.0, note = 4.2, analysten = 20,
        zahlen = 2.0, revision = 6.0, kurs6 = 10.0, kurs12 = 10.0, umkehr = listOf(-1.0, -2.0, -0.5), gewinnwachstum = 10.0,
        boersenwert = 20e9,
    )

    @Test
    fun grosserWertMitGutenZahlen() {
        val e = Levermann.berechne(stark)
        assertEquals(13, e.moeglich)
        assertEquals(13, e.bewertet)
        // Halbjahr und Jahr beide gut: das Momentum gibt keinen Punkt, alle anderen einen
        assertEquals(0, punkte(e, Kriterium.MOMENTUM))
        assertEquals(12, e.punkte)
        assertEquals(Groesse.GROSS, e.groesse)
        assertEquals(4, e.kaufAb)
        assertEquals(true, e.kauf)
        assertEquals(Ampel.GRUEN, Levermann.ampel(e))
    }

    @Test
    fun schwellenJeKriterium() {
        fun p(e: Levermann.Eingabe, k: Kriterium) = punkte(Levermann.berechne(e), k)
        assertEquals(0, p(stark.copy(ekRendite = 20.0), Kriterium.EK_RENDITE))
        assertEquals(-1, p(stark.copy(ekRendite = 9.9), Kriterium.EK_RENDITE))
        assertEquals(0, p(stark.copy(ebitMarge = 12.0), Kriterium.EBIT_MARGE))
        assertEquals(-1, p(stark.copy(ebitMarge = 5.0), Kriterium.EBIT_MARGE))
        assertEquals(0, p(stark.copy(ekQuote = 20.0), Kriterium.EK_QUOTE))
        assertEquals(-1, p(stark.copy(ekQuote = 14.0), Kriterium.EK_QUOTE))
        assertEquals(0, p(stark.copy(kgv = 14.0), Kriterium.KGV))
        assertEquals(-1, p(stark.copy(kgv = 16.5), Kriterium.KGV))
        // Verlust
        assertEquals(-1, p(stark.copy(kgv = -1.0), Kriterium.KGV))
        assertEquals(-1, p(stark.copy(kgvSchnitt = 30.0), Kriterium.KGV_SCHNITT))
        assertEquals(0, p(stark.copy(zahlen = 0.5), Kriterium.ZAHLEN))
        assertEquals(-1, p(stark.copy(zahlen = -1.5), Kriterium.ZAHLEN))
        assertEquals(0, p(stark.copy(revision = 5.0), Kriterium.REVISIONEN))
        assertEquals(-1, p(stark.copy(revision = -7.0), Kriterium.REVISIONEN))
        assertEquals(-1, p(stark.copy(kurs6 = -6.0), Kriterium.KURS6))
        assertEquals(0, p(stark.copy(kurs12 = 3.0), Kriterium.KURS12))
        assertEquals(-1, p(stark.copy(gewinnwachstum = -5.1), Kriterium.GEWINNWACHSTUM))
    }

    @Test
    fun analystenAlsGegenzeichen() {
        // Grosser Wert: Kaufempfehlungen kosten den Punkt, Verkaufsempfehlungen geben ihn
        assertEquals(-1, Levermann.analystenPunkte(1.8, 30, Groesse.GROSS))
        assertEquals(0, Levermann.analystenPunkte(3.0, 30, Groesse.GROSS))
        assertEquals(1, Levermann.analystenPunkte(4.0, 30, Groesse.GROSS))
        // Kleiner Wert mit wenigen Analysten: ohne Umkehrung; mit vielen wie bei grossen
        assertEquals(1, Levermann.analystenPunkte(1.8, 5, Groesse.MITTEL_KLEIN))
        assertEquals(-1, Levermann.analystenPunkte(4.5, 3, Groesse.MITTEL_KLEIN))
        assertEquals(-1, Levermann.analystenPunkte(1.8, 6, Groesse.MITTEL_KLEIN))
        // Ohne Analysten, ohne Groesse oder mit Note ausserhalb der Skala keine Angabe
        assertNull(Levermann.analystenPunkte(2.0, 0, Groesse.GROSS))
        assertNull(Levermann.analystenPunkte(2.0, 10, null))
        assertNull(Levermann.analystenPunkte(0.0, 10, Groesse.GROSS))
    }

    @Test
    fun momentumUndUmkehr() {
        assertEquals(1, Levermann.momentumPunkte(1, 0))
        assertEquals(1, Levermann.momentumPunkte(1, -1))
        assertEquals(0, Levermann.momentumPunkte(1, 1))
        assertEquals(-1, Levermann.momentumPunkte(-1, 0))
        assertEquals(-1, Levermann.momentumPunkte(-1, 1))
        assertEquals(0, Levermann.momentumPunkte(0, 1))
        assertNull(Levermann.momentumPunkte(null, 1))
        assertEquals(1, Levermann.umkehrPunkte(listOf(-1.0, -0.1, -3.0), Groesse.GROSS))
        assertEquals(-1, Levermann.umkehrPunkte(listOf(1.0, 0.1, 3.0), Groesse.GROSS))
        assertEquals(0, Levermann.umkehrPunkte(listOf(1.0, -0.1, 3.0), Groesse.GROSS))
        assertNull(Levermann.umkehrPunkte(null, Groesse.GROSS))
        // Mittlere und kleine Werte: zaehlt als 0, auch ohne Monatswerte
        assertEquals(0, Levermann.umkehrPunkte(null, Groesse.MITTEL_KLEIN))
        assertNull(Levermann.umkehrPunkte(listOf(1.0, 1.0, 1.0), null))
    }

    @Test
    fun kleinerWertBrauchtSiebenPunkte() {
        val e = Levermann.berechne(stark.copy(boersenwert = 800e6, note = 3.0, kurs12 = 0.0, umkehr = null))
        assertEquals(Groesse.MITTEL_KLEIN, e.groesse)
        assertEquals(7, e.kaufAb)
        assertEquals(0, punkte(e, Kriterium.UMKEHR))
        assertEquals(1, punkte(e, Kriterium.MOMENTUM))
        // 5 aus den Kennzahlen, Zahlen, Revision, Halbjahr, Momentum, Wachstum
        assertEquals(10, e.punkte)
        assertEquals(true, e.kauf)
        val schwach = Levermann.berechne(stark.copy(boersenwert = 800e6, note = 3.0, kgv = 20.0, kgvSchnitt = 25.0, kurs6 = 0.0, zahlen = 0.0, revision = 0.0))
        assertEquals(3, schwach.punkte)
        assertEquals(false, schwach.kauf)
        assertEquals(Ampel.GELB, Levermann.ampel(schwach))
    }

    @Test
    fun finanzwertOhneEbitMarge() {
        val e = Levermann.berechne(stark.copy(finanzwert = true, ekQuote = 8.0))
        assertEquals(12, e.moeglich)
        assertTrue(e.zeilen.none { it.kriterium == Kriterium.EBIT_MARGE })
        assertEquals(0, punkte(e, Kriterium.EK_QUOTE))
        assertEquals(1, punkte(Levermann.berechne(stark.copy(finanzwert = true, ekQuote = 11.0)), Kriterium.EK_QUOTE))
        assertEquals(-1, punkte(Levermann.berechne(stark.copy(finanzwert = true, ekQuote = 4.0)), Kriterium.EK_QUOTE))
    }

    @Test
    fun fehlendeAngabenZaehlenNicht() {
        val leer = Levermann.berechne(Levermann.Eingabe())
        assertEquals(0, leer.bewertet)
        assertEquals(0, leer.punkte)
        assertNull(leer.kaufAb)
        assertNull(leer.kauf)
        assertNull(Levermann.ampel(leer))
        val teil = Levermann.berechne(Levermann.Eingabe(ekRendite = 5.0, kgv = 30.0, boersenwert = 10e9))
        // Eigenkapitalrendite und KGV; die Umkehr fehlt (grosser Wert ohne Monatswerte)
        assertEquals(2, teil.bewertet)
        assertEquals(-2, teil.punkte)
        assertNull(punkte(teil, Kriterium.UMKEHR))
        assertEquals(Ampel.ROT, Levermann.ampel(teil))
    }

    @Test
    fun kursGegenSechsUndZwoelfMonate() {
        val ende = LocalDate.of(2026, 11, 12)
        // Ein Jahr lang 100, im letzten Halbjahr 120, am Ende 132
        val kerzen = (400 downTo 0L).map { ende.minusDays(it) }.filter { it.dayOfWeek.value <= 5 }.map { d ->
            kerze(d, if (d == ende) 132.0 else if (d.isAfter(ende.minusMonths(7))) 120.0 else 100.0)
        }
        assertEquals(10.0, Levermann.kursGegen(kerzen, 6)!!, 1e-9)
        assertEquals(32.0, Levermann.kursGegen(kerzen, 12)!!, 1e-9)
        // Die Reihe reicht nicht 24 Monate zurueck
        assertNull(Levermann.kursGegen(kerzen, 24))
        assertNull(Levermann.kursGegen(emptyList(), 6))
    }

    @Test
    fun dreiMonateGegenDenIndex() {
        // Monatsenden Juli bis Oktober, der laufende Monat ist November
        val tage = listOf(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 12))
        val aktie = tage.zip(listOf(100.0, 101.0, 99.0, 99.0, 105.0)).map { kerze(it.first, it.second) }
        val index = tage.zip(listOf(1000.0, 1020.0, 1020.0, 1030.2, 1000.0)).map { kerze(it.first, it.second) }
        val m = Levermann.umkehrMonate(aktie, index)!!
        assertEquals(3, m.size)
        assertEquals(1.0 - 2.0, m[0], 1e-9)
        assertEquals((99.0 / 101.0 - 1.0) * 100.0, m[1], 1e-9)
        assertEquals(-1.0, m[2], 1e-9)
        assertEquals(1, Levermann.umkehrPunkte(m, Groesse.GROSS))
        // Fehlt dem Index ein Monatsende, gibt es keine Angabe
        assertNull(Levermann.umkehrMonate(aktie, index.drop(1)))
    }

    @Test
    fun reaktionAufZahlenGegenIndex() {
        val tag = LocalDate.of(2026, 10, 29)
        val index = listOf(kerze(tag.minusDays(1), 1000.0), kerze(tag, 1010.0), kerze(tag.plusDays(1), 1005.0))
        // Aktie +4 %, Index +1 %
        assertEquals(3.0, Levermann.zahlenGegenIndex(4.0, tag.toEpochDay(), index, ZoneOffset.UTC)!!, 1e-9)
        // Kein Indexkurs in der Naehe des Tages
        assertNull(Levermann.zahlenGegenIndex(4.0, tag.plusDays(10).toEpochDay(), index, ZoneOffset.UTC))
        assertNull(Levermann.zahlenGegenIndex(4.0, tag.toEpochDay(), emptyList(), ZoneOffset.UTC))
    }

    @Test
    fun revisionDerSchaetzung() {
        assertEquals(10.0, Levermann.revision(1.10, 1.00)!!, 1e-9)
        // Verlust wird kleiner: bezogen auf den Betrag eine Verbesserung
        assertEquals(50.0, Levermann.revision(-0.10, -0.20)!!, 1e-9)
        assertNull(Levermann.revision(1.0, 0.0))
        assertNull(Levermann.revision(null, 1.0))
    }

    // ------------------------------------------------------------ Lynch

    private fun lynch(
        gewinne: List<Double>, umsaetze: List<Double> = emptyList(), dividende: Double = 0.0, kbv: Double = 3.0, zyklisch: Boolean = false,
        verschuldung: Double? = null,
    ) = LynchTyp.berechne(LynchTyp.Eingabe(gewinne, umsaetze, dividende, kbv, zyklisch, verschuldung))

    @Test
    fun sechsTypen() {
        // Verlust im letzten Jahr und Gewinn nach Verlust gehen vor Branche und Buchwert
        assertEquals(Typ.TURNAROUND, lynch(listOf(10.0, 12.0, -3.0), zyklisch = true)!!.typ)
        assertEquals(LynchTyp.Grund.VERLUST_VORJAHR, lynch(listOf(10.0, -4.0, 2.0), kbv = 0.5)!!.grund)
        assertEquals(Typ.ZYKLIKER, lynch(listOf(10.0, 30.0, 12.0), kbv = 0.8, zyklisch = true)!!.typ)
        assertEquals(Typ.SUBSTANZ, lynch(listOf(10.0, 10.5, 11.0), kbv = 0.8)!!.typ)
        // 100 auf 175 in drei Jahren: gut 20 % pro Jahr
        assertEquals(Typ.SCHNELL, lynch(listOf(100.0, 120.0, 144.0, 175.0), listOf(1000.0, 1150.0, 1320.0, 1520.0))!!.typ)
        // Gewinn waechst schnell, der Umsatz kaum
        assertEquals(Typ.STETIG, lynch(listOf(100.0, 120.0, 144.0, 175.0), listOf(1000.0, 1020.0, 1040.0, 1061.0))!!.typ)
        assertEquals(Typ.STETIG, lynch(listOf(100.0, 112.0, 125.0, 140.0))!!.typ)
        // 7 % Wachstum: mit Dividende langsam, ohne stetig
        assertEquals(Typ.LANGSAM, lynch(listOf(100.0, 107.0, 114.5), dividende = 3.5)!!.typ)
        assertEquals(Typ.STETIG, lynch(listOf(100.0, 107.0, 114.5), dividende = 0.5)!!.typ)
        assertEquals(Typ.LANGSAM, lynch(listOf(100.0, 102.0, 104.0))!!.typ)
        assertEquals(Typ.LANGSAM, lynch(listOf(100.0, 90.0, 80.0))!!.typ)
    }

    @Test
    fun ohneGewinnreiheZaehltDerUmsatz() {
        val e = lynch(listOf(-5.0, 2.0, 8.0), listOf(100.0, 130.0, 169.0))!!
        assertEquals(Typ.SCHNELL, e.typ)
        assertEquals(LynchTyp.Grund.UMSATZWACHSTUM, e.grund)
        assertEquals(30.0, e.umsatzwachstum!!, 1e-9)
        // Weder Wachstum noch ein anderes Merkmal: kein Typ
        assertNull(lynch(listOf(5.0)))
        assertNull(lynch(emptyList()))
    }

    @Test
    fun prueflisteFuerSchnelleWachser() {
        val solide = lynch(listOf(100.0, 125.0, 156.25), verschuldung = 40.0)!!
        assertEquals(Typ.SCHNELL, solide.typ)
        assertEquals(true, solide.pruefliste!!.schuldenOk)
        assertFalse(solide.pruefliste!!.wachstumZuHoch)
        val heiss = lynch(listOf(100.0, 160.0, 256.0), verschuldung = 120.0)!!
        assertEquals(false, heiss.pruefliste!!.schuldenOk)
        assertTrue(heiss.pruefliste!!.wachstumZuHoch)
        // Grenze: genau 80 % gilt noch, ohne Angabe bleibt die Pruefung offen
        assertEquals(true, lynch(listOf(100.0, 125.0, 156.25), verschuldung = 80.0)!!.pruefliste!!.schuldenOk)
        assertNull(lynch(listOf(100.0, 125.0, 156.25))!!.pruefliste!!.schuldenOk)
        // Andere Typen haben keine Pruefliste
        assertNull(lynch(listOf(100.0, 112.0, 125.0), verschuldung = 200.0)!!.pruefliste)
    }

    // ------------------------------------------------------------ Faires KGV

    @Test
    fun stabilitaetUndMarktstellungInStufen() {
        assertEquals(2.0, FairesKgv.stabilitaet(listOf(100.0, 105.0, 110.0, 108.0))!!.first, 1e-9)
        // Mittel 100, Streuung rund 20
        assertEquals(1.5, FairesKgv.stabilitaet(listOf(75.0, 100.0, 125.0))!!.first, 1e-9)
        assertEquals(1.0, FairesKgv.stabilitaet(listOf(50.0, 100.0, 150.0))!!.first, 1e-9)
        assertEquals(0.5, FairesKgv.stabilitaet(listOf(10.0, 100.0, 300.0))!!.first, 1e-9)
        // Ein Verlustjahr: kleinster Aufschlag, keine Schwankung
        assertEquals(0.5 to null, FairesKgv.stabilitaet(listOf(100.0, -20.0, 110.0)))
        assertNull(FairesKgv.stabilitaet(listOf(100.0, 110.0)))
        assertEquals(listOf(0.0, 0.0, 0.75, 1.5, 2.25, 2.25, 3.0), listOf(-2.0, 3.0, 5.0, 7.0, 7.1, 12.0, 12.1).map { FairesKgv.marktAusMarge(it) })
        assertEquals(1.5, FairesKgv.marktAusPunkten(50.0), 1e-9)
        assertEquals(3.0, FairesKgv.marktAusPunkten(140.0), 1e-9)
        assertEquals(listOf(0.7, 1.0, 1.25, 1.4), listOf(3.0, 12.0, 15.0, 30.0).map { FairesKgv.faktor(it) })
    }

    @Test
    fun fairesKgvAusDenDreiTeilen() {
        val gewinne = listOf(100.0, 105.0, 110.0, 108.0)
        // Ohne Marktmacht zaehlt die Umsatzrendite: (8 + 2 + 2,25) × 1,4
        val ausMarge = FairesKgv.berechne(gewinne, null, 9.0, 18.0)!!
        assertEquals(17.15, ausMarge.fair, 1e-9)
        assertFalse(ausMarge.ausMarktmacht)
        // Mit Marktmacht 80 von 100: (8 + 2 + 2,4) × 1,0
        val ausMacht = FairesKgv.berechne(gewinne, 80.0, 9.0, 12.0)!!
        assertEquals(12.4, ausMacht.fair, 1e-9)
        assertTrue(ausMacht.ausMarktmacht)
        // Fehlt Rendite, Marktstellung oder die Gewinnreihe, gibt es kein Ergebnis
        assertNull(FairesKgv.berechne(gewinne, 80.0, 9.0, null))
        assertNull(FairesKgv.berechne(gewinne, null, null, 12.0))
        assertNull(FairesKgv.berechne(listOf(100.0), 80.0, 9.0, 12.0))
        assertEquals(25.0, FairesKgv.abstand(15.5, 12.4)!!, 1e-9)
        assertNull(FairesKgv.abstand(null, 12.4))
        assertEquals(listOf(Ampel.GRUEN, Ampel.GELB, Ampel.ROT, null), listOf(-10.0, 25.0, 26.0, null).map { FairesKgv.ampel(it) })
    }

    // ------------------------------------------------------------ Ertragswert

    private fun jahr(ende: String, ebit: Double?, steuer: Double? = 0.25, umsatz: Double? = 1000.0) =
        Qualitaet.Jahr(ende = ende, umsatz = umsatz, ebit = ebit, steuersatz = steuer, bilanzsumme = 2000.0, verbindlichkeiten = 1400.0)

    @Test
    fun ertragswertJeAktie() {
        val jahre = listOf(jahr("2023-12-31", 100.0), jahr("2024-12-31", 120.0), jahr("2025-12-31", 80.0))
        // Marge 10 % × Umsatz 1.000 × 0,75 = 75; durch 8 % = 937,5; plus Kasse 100, minus Schulden 37,5 = 1.000; 100 Aktien
        val e = Ertragswert.berechne(jahre, 100.0, 37.5, 100.0)!!
        assertEquals(75.0, e.gewinn, 1e-9)
        assertEquals(10.0, e.marge, 1e-9)
        assertEquals(10.0, e.jeAktie, 1e-9)
        assertEquals(3, e.jahre)
        // Ohne gemeldete Schulden zaehlen keine
        assertEquals(10.375, Ertragswert.berechne(jahre, 100.0, null, 100.0)!!.jeAktie, 1e-9)
        assertEquals(6.0, Ertragswert.buchwertJeAktie(jahre, 100.0)!!, 1e-9)
        assertEquals(20.0, Ertragswert.abstand(12.0, 10.0)!!, 1e-9)
        assertNull(Ertragswert.abstand(12.0, -1.0))
        assertEquals(listOf(Ampel.GRUEN, Ampel.GELB, Ampel.ROT), listOf(Ertragswert.ampel(9.0, 10.0), Ertragswert.ampel(12.0, 10.0), Ertragswert.ampel(12.0, -1.0)))
    }

    @Test
    fun ertragswertBrauchtAlleAngaben() {
        val jahre = listOf(jahr("2023-12-31", 100.0), jahr("2024-12-31", 120.0), jahr("2025-12-31", 80.0))
        assertNull(Ertragswert.berechne(jahre, null, 0.0, 100.0))
        assertNull(Ertragswert.berechne(jahre, 100.0, 0.0, null))
        assertNull(Ertragswert.berechne(jahre.take(2), 100.0, 0.0, 100.0))
        // Kein Jahr nennt einen Steuersatz
        assertNull(Ertragswert.berechne(jahre.map { it.copy(steuersatz = null) }, 100.0, 0.0, 100.0))
        // Nur die letzten fuenf Jahre zaehlen
        val lang = (2018..2025).map { jahr("$it-12-31", if (it < 2021) 500.0 else 100.0) }
        assertEquals(5, Ertragswert.berechne(lang, 0.0, null, 100.0)!!.jahre)
        assertEquals(10.0, Ertragswert.berechne(lang, 0.0, null, 100.0)!!.marge, 1e-9)
        // Verluste geben einen negativen Wert, die Anzeige nennt ihn nicht positiv
        assertTrue(Ertragswert.berechne(jahre.map { it.copy(ebit = -50.0) }, 10.0, null, 100.0)!!.jeAktie < 0)
    }
}
