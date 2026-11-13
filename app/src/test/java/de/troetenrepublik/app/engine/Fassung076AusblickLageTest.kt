package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.76, Paket q76b: Zaehlung im Blasen-Detektor, auffaellige Engpaesse, Einzelwerte und Zwischenspeicher der Lage.
// Alle Zahlen sind erfunden.
class Fassung076AusblickLageTest {

    // ---- Blasen-Zaehlung ----

    private fun ind(k: String, a: Ampel) = Blasen.Indikator(k, 1.0, a)

    private fun zwoelf(): List<Blasen.Indikator> = listOf(
        ind(Blasen.PRAEMIE, Ampel.ROT), ind(Blasen.CAPE, Ampel.GRUEN), ind(Blasen.KGV, Ampel.ROT), ind(Blasen.BUFFETT, Ampel.GELB),
        ind(Blasen.MARGIN, Ampel.ROT), ind(Blasen.TREND, Ampel.GRUEN), ind(Blasen.KONZENTRATION, Ampel.ROT), ind(Blasen.IPO, Ampel.GELB),
        ind(Blasen.STIMMUNG, Ampel.GRUEN), ind(Blasen.ERWAEHNUNG, Ampel.ROT), ind(Blasen.GEWINN, Ampel.GELB), ind(Blasen.BREITE, Ampel.GRUEN),
    )

    @Test fun zaehlungTeiltNachFarbeUndGruppe() {
        val z = Blasen.zaehlung(zwoelf())
        assertEquals(5, z.rot)
        assertEquals(3, z.gelb)
        assertEquals(4, z.gruen)
        assertEquals(12, z.gesamt)
        // PRAEMIE gehoert zu DAGEGEN, die anderen vier roten zu DAFUER
        assertEquals(4, z.dafuerRot)
        assertEquals(9, z.dafuerGesamt)
        assertEquals(1, z.dagegenRot)
        assertEquals(3, z.dagegenGesamt)
    }

    @Test fun zaehlungIgnoriertUnbekannteSchluessel() {
        val z = Blasen.zaehlung(zwoelf() + ind("unbekannt", Ampel.ROT))
        assertEquals(5, z.rot)
        assertEquals(12, z.gesamt)
    }

    @Test fun uebersichtRotZuerstDannInDerReihenfolgeDerAnzeige() {
        val u = Blasen.uebersicht(zwoelf())
        assertEquals(12, u.size)
        assertEquals(listOf(Blasen.KGV, Blasen.MARGIN, Blasen.KONZENTRATION, Blasen.ERWAEHNUNG, Blasen.PRAEMIE), u.take(5).map { it.schluessel })
        assertTrue(u.take(5).all { it.ampel == Ampel.ROT })
        assertTrue(u.subList(5, 8).all { it.ampel == Ampel.GELB })
        assertTrue(u.drop(8).all { it.ampel == Ampel.GRUEN })
    }

    // ---- auffaellige Engpaesse ----

    // n heute, Vorjahr 100: abw = n - 100 Prozent
    private fun lage(abw: Double, vorjahr: Double = 100.0) = Engpaesse.Lage(vorjahr * (1 + abw / 100), 0.0, 0.0, vorjahr, 0.0, 0.0)

    private fun uebersicht(vararg x: Pair<String, Engpaesse.Lage>) = Engpaesse.Uebersicht(0L, 20_000, x.toMap())

    @Test fun auffaelligeNachBetragDerAbweichung() {
        val u = uebersicht(
            "chokepoint6" to lage(-50.0), "chokepoint4" to lage(30.0), "chokepoint1" to lage(-12.0),
            "chokepoint2" to lage(5.0), "chokepoint5" to lage(-2.0), "chokepoint11" to lage(-1.0),
        )
        val ids = Engpaesse.auffaellige(u).map { it.first.id }
        assertEquals(listOf("chokepoint6", "chokepoint4", "chokepoint1"), ids)
    }

    @Test fun wenigeAuffaelligeWerdenAufDreiAufgefuellt() {
        val u = uebersicht("chokepoint6" to lage(-40.0), "chokepoint4" to lage(4.0), "chokepoint1" to lage(-6.0), "chokepoint2" to lage(1.0))
        assertEquals(listOf("chokepoint6", "chokepoint1", "chokepoint4"), Engpaesse.auffaellige(u).map { it.first.id })
    }

    @Test fun hoechstensFuenfBeiVielenAuffaelligen() {
        val u = uebersicht(
            "chokepoint6" to lage(-60.0), "chokepoint4" to lage(-50.0), "chokepoint1" to lage(-40.0), "chokepoint2" to lage(-30.0),
            "chokepoint5" to lage(-25.0), "chokepoint11" to lage(-20.0), "chokepoint3" to lage(-15.0), "chokepoint8" to lage(-11.0),
        )
        val ids = Engpaesse.auffaellige(u).map { it.first.id }
        assertEquals(listOf("chokepoint6", "chokepoint4", "chokepoint1", "chokepoint2", "chokepoint5"), ids)
    }

    @Test fun engpaesseMitWenigVerkehrOderOhneVorjahrFehlen() {
        val ohne = Engpaesse.Lage(10.0, 0.0, 0.0, null, null, null)
        val u = uebersicht("chokepoint6" to lage(-90.0, vorjahr = 5.0), "chokepoint4" to ohne, "chokepoint1" to lage(-15.0))
        assertEquals(listOf("chokepoint1"), Engpaesse.auffaellige(u).map { it.first.id })
        assertTrue(Engpaesse.auffaellige(uebersicht()).isEmpty())
    }

    // ---- Einzelwerte ----

    private fun imf(): Imf.Datensatz {
        val wachstum = mapOf(Imf.WELT to mapOf(2026 to 3.1), "USA" to mapOf(2026 to 2.0), "CHN" to mapOf(2026 to 4.5), "DEU" to mapOf(2026 to -0.2),
            "JPN" to mapOf(2026 to 0.8), "IND" to mapOf(2026 to 6.5), "GBR" to mapOf(2026 to 1.1), "MLT" to mapOf(2026 to 4.0))
        val bip = mapOf("USA" to mapOf(2026 to 30_000.0), "CHN" to mapOf(2026 to 19_000.0), "DEU" to mapOf(2026 to 4_900.0),
            "JPN" to mapOf(2026 to 4_300.0), "IND" to mapOf(2026 to 4_200.0), "GBR" to mapOf(2026 to 3_700.0), "MLT" to mapOf(2026 to 20.0))
        return Imf.Datensatz(abgerufen = 1L, werte = mapOf(Imf.WACHSTUM to wachstum, Imf.BIP to bip))
    }

    @Test fun groessteWirtschaftenNachBip() {
        val p = Lagebild.groessteWirtschaften(imf(), Imf.WACHSTUM, 2026)
        assertEquals(listOf("USA", "CHN", "DEU", "JPN", "IND"), p.map { it.name })
        assertEquals(-0.2, p[2].wert, 1e-9)
    }

    @Test fun groessteWirtschaftenOhneWertFehlen() {
        val p = Lagebild.groessteWirtschaften(imf(), Imf.WACHSTUM, 2027)
        assertTrue(p.isEmpty())
    }

    @Test fun kriegsLaenderZaehltNurSchwereKonflikteEinmal() {
        val k = Konflikte.Stand(
            2025, "x", mapOf(
                "MMR" to listOf(Konflikte.Konflikt("A", "B", 2), Konflikte.Konflikt("A", "B", 2), Konflikte.Konflikt("A", "C", 2)),
                "SDN" to listOf(Konflikte.Konflikt("D", "E", 2)),
                "COL" to listOf(Konflikte.Konflikt("F", "G", 1)),
            ),
        )
        val p = Lagebild.kriegsLaender(k)
        assertEquals(listOf("MMR", "SDN"), p.map { it.name })
        assertEquals(2.0, p[0].wert, 1e-9)
    }

    @Test fun spannungsGebieteNachFaktor() {
        fun w(id: String, f: Double) = Spannungen.Wert(0L, id, 100, f, 20_000)
        val p = Lagebild.spannungsGebiete(mapOf("UKR" to w("UKR", 1.2), "TWN" to w("TWN", 3.4), "NAO" to w("NAO", 2.0), "KOR" to w("KOR", Double.NaN)), 2)
        assertEquals(listOf("TWN", "NAO"), p.map { it.name })
    }

    @Test fun katastrophenLaenderRotZuerst() {
        fun e(rot: Boolean, vararg l: String) = Katastrophen.Ereignis("FL", rot, "2026-10-01", "2026-10-05", l.toList())
        val p = Lagebild.katastrophenLaender(listOf(e(false, "USA", "MEX"), e(false, "USA"), e(true, "PHL"), e(false, "USA")))
        assertEquals(listOf("PHL", "USA", "MEX"), p.map { it.name })
        assertEquals("1", p[0].zusatz)
        assertEquals(3.0, p[1].wert, 1e-9)
    }

    @Test fun aussagenTragenDieEinzelwerte() {
        fun w(id: String, f: Double) = Spannungen.Wert(0L, id, 100, f, 20_000)
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt = 1_000_000_000_000L, spannungen = mapOf("UKR" to w("UKR", 1.8), "TWN" to w("TWN", 2.6))))
        val k = a.first { it.thema == Lagebild.Thema.GEOPOLITIK }.kennzahlen.first { it.schluessel == "SPANNUNG" }
        assertEquals(listOf("TWN", "UKR"), k.posten.map { it.name })
        assertEquals(2.0, k.wert, 1e-9)
    }

    // ---- Zwischenspeicher der Lage ----

    private fun kz(s: String, v: Double, posten: List<Lagebild.Posten> = emptyList()) = Lagebild.Kennzahl(s, Lagebild.Einheit.ANZAHL, v, posten = posten)

    @Test fun zusammenfuehrenBehaeltWasFehlt() {
        val alt = listOf(
            Lagebild.Aussage(Lagebild.Thema.KLIMA, listOf(kz("ENSO_ZUSTAND", 1.0), kz("KATASTROPHEN", 4.0)), "globus"),
            Lagebild.Aussage(Lagebild.Thema.TECHNIK, listOf(kz("DURCHBRUCH_NEU", 9.0)), "maerkte/trends"),
        )
        val neu = listOf(Lagebild.Aussage(Lagebild.Thema.KLIMA, listOf(kz("KATASTROPHEN", 6.0)), "globus"))
        val m = Lagebild.zusammenfuehren(alt, neu)
        assertEquals(listOf(Lagebild.Thema.KLIMA, Lagebild.Thema.TECHNIK), m.map { it.thema })
        val klima = m[0].kennzahlen
        assertEquals(listOf("ENSO_ZUSTAND", "KATASTROPHEN"), klima.map { it.schluessel })
        assertEquals(1.0, klima[0].wert, 1e-9)
        assertEquals(6.0, klima[1].wert, 1e-9)
        assertEquals(9.0, m[1].kennzahlen[0].wert, 1e-9)
    }

    @Test fun zusammenfuehrenOhneAltenStandGiltDasNeue() {
        val neu = listOf(Lagebild.Aussage(Lagebild.Thema.ZINSEN, listOf(kz("ZINS_HEUTE", 4.0)), "maerkte/ausblick"))
        assertEquals(neu, Lagebild.zusammenfuehren(null, neu))
        assertEquals(neu, Lagebild.zusammenfuehren(emptyList(), neu))
    }

    @Test fun zusammenfuehrenMitLeeremAbrufLaesstAllesStehen() {
        val alt = listOf(Lagebild.Aussage(Lagebild.Thema.ZINSEN, listOf(kz("ZINS_HEUTE", 4.0)), "maerkte/ausblick"))
        val m = Lagebild.zusammenfuehren(alt, emptyList())
        assertEquals(1, m.size)
        assertEquals(4.0, m[0].kennzahlen[0].wert, 1e-9)
    }

    @Test fun standUeberstehtSpeichernUndLesen() {
        val s = Lagebild.Stand(
            123L, listOf(Lagebild.Aussage(Lagebild.Thema.GEOPOLITIK, listOf(kz("SPANNUNG", 2.0, listOf(Lagebild.Posten("TWN", 2.6, "x")))), "globus/engpaesse")),
        )
        val z = Lagebild.lesenStand(Lagebild.schreibenStand(s))
        assertNotNull(z)
        assertEquals(123L, z!!.zeit)
        val k = z.aussagen[0].kennzahlen[0]
        assertEquals("SPANNUNG", k.schluessel)
        assertEquals("TWN", k.posten[0].name)
        assertEquals(2.6, k.posten[0].wert, 1e-9)
        assertEquals("x", k.posten[0].zusatz)
    }

    @Test fun kaputterOderLeererStandGiltAlsKeiner() {
        assertNull(Lagebild.lesenStand("{kaputt"))
        assertNull(Lagebild.lesenStand(""))
        assertNull(Lagebild.lesenStand(Lagebild.schreibenStand(Lagebild.Stand(5L, emptyList()))))
    }
}
