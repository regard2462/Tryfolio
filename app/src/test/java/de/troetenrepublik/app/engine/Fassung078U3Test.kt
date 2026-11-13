package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.engine.Bewertungsblick.Ampel
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 0.78: Angleichungen der Wertpapierseite (Geschaeftszahlen, Optionen, Bewertung, Indikatoren).
class Fassung078U3Test {
    private val jetzt = 1_800_000_000L
    private val tag = 86_400L

    // ------------------------------------------------------------ Gewinnueberraschung

    @Test fun abweichungBeziehtSichAufDenBetragDerErwartung() {
        assertEquals(10.0, Gewinnueberraschung.abweichung(1.10, 1.00)!!, 1e-9)
        assertEquals(50.0, Gewinnueberraschung.abweichung(-0.05, -0.10)!!, 1e-9)
        assertNull(Gewinnueberraschung.abweichung(1.0, 0.0))
        assertNull(Gewinnueberraschung.abweichung(null, 1.0))
        assertNull(Gewinnueberraschung.abweichung(1.0, null))
    }

    @Test fun uebertroffenErstJenseitsDerSchwelle() {
        assertEquals(Gewinnueberraschung.Urteil.WIE_ERWARTET, Gewinnueberraschung.urteil(2.0))
        assertEquals(Gewinnueberraschung.Urteil.UEBERTROFFEN, Gewinnueberraschung.urteil(2.1))
        assertEquals(Gewinnueberraschung.Urteil.WIE_ERWARTET, Gewinnueberraschung.urteil(-2.0))
        assertEquals(Gewinnueberraschung.Urteil.VERFEHLT, Gewinnueberraschung.urteil(-2.1))
        assertEquals(Gewinnueberraschung.Urteil.WIE_ERWARTET, Gewinnueberraschung.urteil(1.01, 1.00))
    }

    @Test fun alleStellenRechnenGleich() {
        val a = Gewinnueberraschung.abweichung(1.23, 1.10)!!
        assertEquals(a, Bewertungsblick.ueberraschung(1.23, 1.10)!!.abweichung, 1e-9)
        assertEquals(a, Zahlenreaktion.ueberraschung(1.23, 1.10)!!, 1e-9)
    }

    @Test fun bilanzZaehltNurDieLetztenVier() {
        // aelteste zuerst: das erste Paar faellt heraus, ein Cent ueber der Erwartung zaehlt nicht
        val b = Gewinnueberraschung.bilanz(listOf(0.5 to 1.0, 1.2 to 1.0, 1.01 to 1.0, 0.9 to 1.0, 1.5 to 1.0))
        assertEquals(2, b.uebertroffen)
        assertEquals(1, b.verfehlt)
        assertEquals(4, b.gezaehlt)
    }

    @Test fun fehlendeWerteBleibenNull() {
        val q = listOf(
            Gewinnueberraschung.Quartal("2026-03-31", 1.0, null), Gewinnueberraschung.Quartal("2026-06-30", 1.1, 1.0),
        )
        assertNull(q[0].abweichung)
        assertEquals(listOf("2026-06-30"), Gewinnueberraschung.vollstaendig(q).map { it.ende })
    }

    // ------------------------------------------------------------ Zahlentermine

    @Test fun vergangenerTerminStehtNichtUnterAnstehend() {
        val ms = 86_400_000L
        val now = 2_000_000_000_000L
        assertTrue(Zahlentermine.anstehend(now + ms, now))
        assertTrue(Zahlentermine.anstehend(now - ms / 2, now))
        assertFalse(Zahlentermine.anstehend(now - 2 * ms, now))
    }

    @Test fun veroeffentlichungstagZumQuartalsende() {
        assertEquals(20_030L, Zahlentermine.veroeffentlicht(20_000L, listOf(19_990L, 20_030L, 20_121L, 20_100L)))
        assertNull(Zahlentermine.veroeffentlicht(20_000L, listOf(20_000L, 20_121L)))
    }

    // ------------------------------------------------------------ Optionsumsatz

    private fun k(call: Boolean, strike: Double, umsatz: Long, offen: Long, mitte: Double = 2.0, tage: Int = 20, handel: Long = 0L) =
        Kontrakt(call, strike, jetzt + tage * tag, mitte, mitte - 0.05, mitte + 0.05, umsatz, offen, 0.3, handel)

    @Test fun umsatzZaehltNurDenJuengstenHandelstag() {
        val heute = jetzt - 3_600
        val kette = listOf(
            k(true, 100.0, 300, 10, handel = heute), k(false, 95.0, 100, 10, handel = heute - 1_800),
            k(false, 90.0, 5_000, 10, handel = heute - 3 * tag), // Umsatz eines frueheren Tages
        )
        val u = Optionsanalyse.umsatz(kette, jetzt)
        assertEquals(300L, u.calls)
        assertEquals(100L, u.puts)
        assertEquals(1.0 / 3, u.putCall!!, 1e-9)
    }

    @Test fun balkenUndPutCallAusEinerRechnung() {
        val heute = jetzt - 3_600
        val kette = listOf(k(true, 100.0, 600, 10, handel = heute), k(false, 95.0, 200, 10, handel = heute))
        val u = Optionsanalyse.umsatz(kette, jetzt)
        val posten = kette.map { Triple(it.call, it.umsatz, it.handel * 1000L) }
        val balken = Optionsanalyse.tagesUmsatz(posten)
        assertEquals(u.calls, balken.calls)
        assertEquals(u.puts, balken.puts)
        // 75 % Calls im Balken, Put/Call 0,33
        assertEquals(0.75, balken.calls.toDouble() / balken.gesamt, 1e-9)
    }

    @Test fun ohneCallsKeinVerhaeltnis() {
        assertNull(Optionsanalyse.tagesUmsatz(listOf(Triple(false, 10L, 0L))).putCall)
    }

    // ------------------------------------------------------------ Auffaellige Optionen

    @Test fun grundregelDerAuffaelligenOptionen() {
        // Umsatz gleich den offenen Kontrakten, Praemie genau 50.000 $, Verfall in 60 Tagen
        assertTrue(Optionsanalyse.auffaellig(k(true, 100.0, 250, 250, tage = 60), jetzt))
        assertFalse(Optionsanalyse.auffaellig(k(true, 100.0, 249, 250, tage = 60), jetzt))
        assertFalse(Optionsanalyse.auffaellig(k(true, 100.0, 250, 250, tage = 61), jetzt))
        assertFalse(Optionsanalyse.auffaellig(k(true, 100.0, 250, 250, mitte = 1.9, tage = 30), jetzt))
        assertEquals(50_000.0, Optionsanalyse.praemie(k(true, 100.0, 250, 250)), 1e-6)
    }

    @Test fun werkzeugFolgtDerGrundregel() {
        val kette = listOf(
            k(true, 105.0, 3_000, 500), k(true, 100.0, 5_000, 4_000),
            k(false, 90.0, 400, 100), // unter dem gewaehlten Mindestvolumen
            k(false, 95.0, 2_000, 4_000), // weniger Umsatz als offene Kontrakte
        )
        val w = AuffaelligeKontrakte.auswerten(100.0, kette, 500, AuffaelligeKontrakte.Art.ALLE, jetzt)!!
        assertEquals(setOf(105.0, 100.0), w.zeilen.map { it.kontrakt.strike }.toSet())
        assertTrue(w.zeilen.all { Optionsanalyse.auffaellig(it.kontrakt, jetzt) })
    }

    // ------------------------------------------------------------ Stillhalter und Rechner

    @Test fun stillhalterZeileOeffnetDenRechnerMitDemKontrakt() {
        val kette = listOf(k(true, 110.0, 10, 10), k(false, 90.0, 10, 10))
        val call = OptionsrechnerPlan.Start(true, 110.0, jetzt + 20 * tag)
        val put = OptionsrechnerPlan.Start(false, 90.0, jetzt + 20 * tag)
        assertTrue(OptionsrechnerPlan.startPasst(call, kette))
        assertFalse(OptionsrechnerPlan.startPasst(OptionsrechnerPlan.Start(true, 111.0, jetzt + 20 * tag), kette))
        assertFalse(OptionsrechnerPlan.startPasst(null, kette))
        assertEquals(Optionsrechner.Vorlage.COVERED_CALL, OptionsrechnerPlan.startVorlage(call))
        assertEquals(Optionsrechner.Vorlage.CASH_SECURED_PUT, OptionsrechnerPlan.startVorlage(put))
        assertEquals(2, OptionsrechnerPlan.startBeine(call).size)
        assertEquals(90.0, OptionsrechnerPlan.startBeine(put).single().strike, 1e-9)
    }

    // ------------------------------------------------------------ Scheinseite

    private fun schein(kind: DerivativeKind, strike: Double, ivKontrakt: Boolean = false) = Derivative(
        id = "T1", underlying = "AAA", underlyingName = "AAA", kind = kind, strike = strike, ratio = 10.0, expiry = jetzt * 1000, ivKontrakt = ivKontrakt,
    )

    @Test fun gewinnschwelleAusDemEinstand() {
        // 0,50 EUR je Schein, zehn Scheine je Aktie, 1 USD = 0,80 EUR: 6,25 USD je Aktie
        assertEquals(106.25, Derivatives.gewinnschwelle(schein(DerivativeKind.CALL, 100.0), 0.50, 0.80), 1e-9)
        assertEquals(93.75, Derivatives.gewinnschwelle(schein(DerivativeKind.PUT, 100.0), 0.50, 0.80), 1e-9)
    }

    @Test fun hausscheinZeigtModellVolatilitaet() {
        assertTrue(Derivatives.modellVol(schein(DerivativeKind.CALL, 100.0)))
        assertFalse(Derivatives.modellVol(schein(DerivativeKind.CALL, 100.0, ivKontrakt = true)))
    }

    // ------------------------------------------------------------ Gesamtrichtung und Bilanz

    @Test fun trendZaehltAlsEigeneFamilie() {
        assertNull(Gesamtrichtung.trend(List(10) { 1.0 }, List(10) { 1.0 }, List(10) { 1.0 }))
        // Treppe nach oben: hoehere Hochs und hoehere Tiefs
        val schluss = ArrayList<Double>()
        var v = 100.0
        repeat(12) { repeat(8) { v += 2.0; schluss += v }; repeat(4) { v -= 1.5; schluss += v } }
        val d = Gesamtrichtung.trend(schluss.map { it + 0.5 }, schluss.map { it - 0.5 }, schluss)
        val zustand = Markttechnik.lage(schluss.map { it + 0.5 }, schluss.map { it - 0.5 }, schluss).zustand
        if (zustand == Markttechnik.Zustand.KEINER) { assertNull(d); return }
        assertEquals(Gesamtrichtung.TREND, d!!.art)
        assertEquals(Gesamtrichtung.TREND, Gesamtrichtung.familie(d.art))
        assertEquals(if (zustand == Markttechnik.Zustand.AUF) 1 else if (zustand == Markttechnik.Zustand.AB) -1 else 0, d.stufe)
        // Ohne erkennbaren Trend kein Signal: eine gerade Linie hat keine Hoch- und Tiefpunkte
        val linie = List(60) { 100.0 + it }
        if (Markttechnik.lage(linie, linie, linie).zustand == Markttechnik.Zustand.KEINER) assertNull(Gesamtrichtung.trend(linie, linie, linie))
    }

    @Test fun kritischeStabilitaetDeckeltDieBilanzAmpel() {
        val rot = Stabilitaet.Zeile(Stabilitaet.Kennzahl.GEARING, Stabilitaet.Lage.WERT, 250.0, Ampel.ROT)
        val gelb = Stabilitaet.Zeile(Stabilitaet.Kennzahl.TILGUNG, Stabilitaet.Lage.WERT, 4.0, Ampel.GELB)
        assertEquals(Ampel.GELB, Stabilitaet.seitenAmpel(Ampel.GRUEN, listOf(gelb, rot)))
        assertEquals(Ampel.GRUEN, Stabilitaet.seitenAmpel(Ampel.GRUEN, listOf(gelb)))
        assertEquals(Ampel.ROT, Stabilitaet.seitenAmpel(Ampel.ROT, listOf(rot)))
        assertEquals(rot, Stabilitaet.kritisch(listOf(gelb, rot)))
        assertNull(Stabilitaet.kritisch(listOf(gelb)))
    }

    // ------------------------------------------------------------ Bewertung

    @Test fun einKgvProfilVorKursabruf() {
        assertEquals(18.0, Bewertungsblick.kgv(18.0, 21.0)!!, 1e-9)
        assertEquals(21.0, Bewertungsblick.kgv(0.0, 21.0)!!, 1e-9)
        assertEquals(21.0, Bewertungsblick.kgv(null, 21.0)!!, 1e-9)
        assertNull(Bewertungsblick.kgv(-3.0, 0.0))
    }

    @Test fun selbstRechnenStartetWieDieBewertungszeile() {
        assertEquals(15.0, Bewertungsblick.zielKgv(null), 1e-9)
        assertEquals(23.0, Bewertungsblick.zielKgv(22.6), 1e-9)
        assertEquals(35.0, Bewertungsblick.zielKgv(80.0), 1e-9)
        assertEquals(8.0, Bewertungsblick.zielKgv(3.0), 1e-9)
        // Gleiche Vorgaben, gleicher fairer Wert: Kurs 120, KGV 20, 12 % Wachstum, Ziel-KGV 23
        val zeile = Bewertungsblick.fairerWert(120.0, 20.0, 12.0, 23.0)!!
        val selbst = Analysis.fairValue(120.0 / 20.0, 120.0, 12.0, Bewertungsblick.JAHRE, 23.0, Bewertungsblick.RENDITE)!!
        assertEquals(zeile, selbst.fair, 1e-6)
    }
}
