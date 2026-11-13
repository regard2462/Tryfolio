package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Lagebild.Thema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Lagebild: Aussagen aus vorhandenen Daten, Aenderungen seit dem letzten Oeffnen.
class LagebildTest {
    private val jetzt = LocalDate.of(2026, 11, 10).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val tag = jetzt / 86_400_000L

    private fun imf(wachstum: Pair<Double, Double>, inflation: Pair<Double, Double>) = Imf.Datensatz(
        abgerufen = jetzt,
        werte = mapOf(
            Imf.WACHSTUM to mapOf(Imf.WELT to mapOf(2025 to wachstum.first, 2026 to wachstum.second)),
            Imf.INFLATION to mapOf(Imf.WELT to mapOf(2025 to inflation.first, 2026 to inflation.second)),
        ),
    )

    private fun lage(abw: Double, vn: Double = 100.0) = Engpaesse.Lage(vn * (1 + abw / 100), 0.0, 0.0, vn, null, null)

    private fun aussage(a: List<Lagebild.Aussage>, t: Thema) = a.firstOrNull { it.thema == t }
    private fun kz(a: List<Lagebild.Aussage>, t: Thema, k: String) = aussage(a, t)!!.kennzahlen.first { it.schluessel == k }

    @Test(timeout = 20_000)
    fun ohneDatenKeineAussagen() {
        assertTrue(Lagebild.aussagen(Lagebild.Eingabe(jetzt)).isEmpty())
    }

    @Test(timeout = 20_000)
    fun konjunkturMitVorjahr() {
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, imf = imf(3.3 to 3.2, 5.8 to 4.1), jahr = 2026))
        assertEquals(1, a.size)
        val w = kz(a, Thema.KONJUNKTUR, "WACHSTUM")
        assertEquals(3.2, w.wert, 1e-9)
        assertEquals(3.3, w.vorher!!, 1e-9)
        assertEquals("2026", w.zusatz)
        assertEquals(4.1, kz(a, Thema.KONJUNKTUR, "INFLATION").wert, 1e-9)
        assertEquals("globus", aussage(a, Thema.KONJUNKTUR)!!.ziel)
        assertEquals(2, aussage(a, Thema.KONJUNKTUR)!!.signale)
        // Fehlt das laufende Jahr, gilt das letzte mit Zahl
        val alt = Imf.Datensatz(jetzt, mapOf(Imf.WACHSTUM to mapOf(Imf.WELT to mapOf(2024 to 3.0))))
        assertEquals(3.0, kz(Lagebild.aussagen(Lagebild.Eingabe(jetzt, imf = alt, jahr = 2026)), Thema.KONJUNKTUR, "WACHSTUM").wert, 1e-9)
    }

    @Test(timeout = 20_000)
    fun zinsenMitSchritten() {
        val pfad = listOf(Zinserwartung.Monat(2026, 11, 4.0), Zinserwartung.Monat(2027, 11, 3.5))
        val z = Zinserwartung.erwartung(pfad)!!
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, zins = z))
        assertEquals(4.0, kz(a, Thema.ZINSEN, "ZINS_HEUTE").wert, 1e-9)
        assertEquals(-2.0, kz(a, Thema.ZINSEN, "ZINS_SCHRITTE").wert, 1e-9)
        // Zwei Schritte oder mehr gelten als auffaellig
        assertTrue(kz(a, Thema.ZINSEN, "ZINS_SCHRITTE").auffaellig)
        assertEquals("maerkte/ausblick", aussage(a, Thema.ZINSEN)!!.ziel)
    }

    @Test(timeout = 20_000)
    fun geopolitikAusKriegenUndSpannungen() {
        val k = Konflikte.Stand(
            2025, "x",
            mapOf(
                "UKR" to listOf(Konflikte.Konflikt("Government of Russia", "Government of Ukraine", 2), Konflikte.Konflikt("A", "B", 1)),
                "RUS" to listOf(Konflikte.Konflikt("Government of Russia", "Government of Ukraine", 2)),
            ),
        )
        val sp = mapOf(
            "TWN" to Spannungen.Wert(jetzt, "TWN", 80, 2.0, tag.toInt()),
            "UKR" to Spannungen.Wert(jetzt, "UKR", 300, 1.0, tag.toInt()),
        )
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, konflikte = k, spannungen = sp))
        // Derselbe Krieg in zwei Laendern zaehlt einmal
        assertEquals(1.0, kz(a, Thema.GEOPOLITIK, "KRIEGE").wert, 0.0)
        assertEquals("2025", kz(a, Thema.GEOPOLITIK, "KRIEGE").zusatz)
        val s = kz(a, Thema.GEOPOLITIK, "SPANNUNG")
        assertEquals(1.0, s.wert, 0.0)
        assertEquals("2", s.zusatz)
        assertTrue(s.auffaellig)
        assertEquals(tag, s.stand)
    }

    @Test(timeout = 20_000)
    fun rohstoffeAusEngpaessen() {
        val u = Engpaesse.Uebersicht(
            jetzt, tag.toInt(),
            mapOf("chokepoint6" to lage(-30.0), "chokepoint1" to lage(-25.0), "chokepoint2" to lage(5.0), "chokepoint3" to lage(-60.0, vn = 4.0)),
        )
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, engpaesse = u))
        val g = kz(a, Thema.ROHSTOFFE, "ENGPASS_GESTOERT")
        // Zwei Engpaesse mit Rueckgang ueber 20 %; der Bosporus hat zu wenig Verkehr fuer einen Vergleich
        assertEquals(2.0, g.wert, 0.0)
        assertEquals("4", g.zusatz)
        assertTrue(g.auffaellig)
        val s = kz(a, Thema.ROHSTOFFE, "ENGPASS_SCHLECHTESTER")
        assertEquals("chokepoint6", s.zusatz)
        assertEquals(-30.0, s.wert, 1e-9)
    }

    @Test(timeout = 20_000)
    fun klimaAusEnsoUndKatastrophen() {
        val index = Enso.Index(jetzt, listOf(Enso.Punkt("ASO", 2026, 0.4), Enso.Punkt("SON", 2026, 0.9)))
        val heute = LocalDate.ofEpochDay(tag)
        val kat = Katastrophen.Stand(
            jetzt,
            listOf(
                Katastrophen.Ereignis("TC", true, heute.minusDays(3).toString(), heute.toString(), listOf("PHL")),
                Katastrophen.Ereignis("EQ", true, heute.minusDays(3).toString(), heute.toString(), listOf("JPN")),
                Katastrophen.Ereignis("DR", false, heute.minusDays(90).toString(), heute.minusDays(60).toString(), listOf("BRA")),
                Katastrophen.Ereignis("FL", false, heute.minusDays(5).toString(), heute.toString(), listOf("IND")),
            ),
        )
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, ensoStatus = Enso.Status(jetzt, "EL_NINO_ADVISORY"), ensoIndex = index, katastrophen = kat))
        assertTrue(kz(a, Thema.KLIMA, "ENSO_ZUSTAND").auffaellig)
        assertEquals("EL_NINO_ADVISORY", kz(a, Thema.KLIMA, "ENSO_ZUSTAND").zusatz)
        assertEquals(0.9, kz(a, Thema.KLIMA, "ENSO_INDEX").wert, 1e-9)
        assertEquals(0.4, kz(a, Thema.KLIMA, "ENSO_INDEX").vorher!!, 1e-9)
        // Nur Wetter (Sturm, Flut), nur die laufenden der letzten 30 Tage; Erdbeben zaehlen nicht
        val k = kz(a, Thema.KLIMA, "KATASTROPHEN")
        assertEquals(2.0, k.wert, 0.0)
        assertEquals("1", k.zusatz)
        // Unbekannter Zustand: keine ENSO-Zeile
        val u = Lagebild.aussagen(Lagebild.Eingabe(jetzt, ensoStatus = Enso.Status(jetzt, "UNBEKANNT")))
        assertNull(aussage(u, Thema.KLIMA))
    }

    @Test(timeout = 20_000)
    fun technikAusDurchbruchmeldungen() {
        val m = listOf(
            Durchbrueche.Meldung("ki", "a", "u", jetzt - 86_400_000L, "q"),
            Durchbrueche.Meldung("ki", "b", "u", jetzt - 2 * 86_400_000L, "q"),
            Durchbrueche.Meldung("krebs", "c", "u", jetzt - 3 * 86_400_000L, "q"),
            Durchbrueche.Meldung("fusion", "d", "u", jetzt - 20 * 86_400_000L, "q"),
        )
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, durchbrueche = m))
        assertEquals(3.0, kz(a, Thema.TECHNIK, "DURCHBRUCH_NEU").wert, 0.0)
        assertEquals(2.0, kz(a, Thema.TECHNIK, "DURCHBRUCH_THEMEN").wert, 0.0)
        assertEquals("maerkte/trends", aussage(a, Thema.TECHNIK)!!.ziel)
    }

    @Test(timeout = 20_000)
    fun aenderungenGegenDenGemerktenStand() {
        val alt = Lagebild.aussagen(Lagebild.Eingabe(jetzt, imf = imf(3.3 to 3.3, 5.8 to 4.1), jahr = 2026))
        val gemerkt = Lagebild.Gemerkt(jetzt - 86_400_000L, Lagebild.merken(alt))
        assertEquals(setOf("KONJUNKTUR.WACHSTUM", "KONJUNKTUR.INFLATION"), Lagebild.merken(alt).keys)
        // Unveraendert: keine Aenderung
        assertTrue(Lagebild.aenderungen(gemerkt, alt).isEmpty())
        // Wachstum 3,3 -> 3,0 (ueber der Schwelle von 0,1), Inflation 4,1 -> 4,1
        val neu = Lagebild.aussagen(Lagebild.Eingabe(jetzt, imf = imf(3.3 to 3.0, 5.8 to 4.1), jahr = 2026))
        val a = Lagebild.aenderungen(gemerkt, neu)
        assertEquals(1, a.size)
        assertEquals("WACHSTUM", a[0].schluessel)
        assertEquals(3.3, a[0].von, 1e-9)
        assertEquals(3.0, a[0].nach, 1e-9)
        // Kleiner als die Schwelle zaehlt nicht
        val klein = Lagebild.aussagen(Lagebild.Eingabe(jetzt, imf = imf(3.3 to 3.25, 5.8 to 4.1), jahr = 2026))
        assertTrue(Lagebild.aenderungen(gemerkt, klein).isEmpty())
        // Erstes Oeffnen oder leerer Stand: nichts
        assertTrue(Lagebild.aenderungen(null, neu).isEmpty())
        assertTrue(Lagebild.aenderungen(Lagebild.Gemerkt(), neu).isEmpty())
    }

    @Test(timeout = 20_000)
    fun engpassWechselWirdNichtAlsAenderungGemeldet() {
        fun u(id: String, abw: Double) = Engpaesse.Uebersicht(jetzt, tag.toInt(), mapOf(id to lage(abw)))
        val alt = Lagebild.aussagen(Lagebild.Eingabe(jetzt, engpaesse = u("chokepoint6", -30.0)))
        val neu = Lagebild.aussagen(Lagebild.Eingabe(jetzt, engpaesse = u("chokepoint1", -50.0)))
        val a = Lagebild.aenderungen(Lagebild.Gemerkt(1L, Lagebild.merken(alt)), neu)
        assertTrue(a.none { it.schluessel == "ENGPASS_SCHLECHTESTER" })
    }

    @Test(timeout = 20_000)
    fun hoechstensFuenfAenderungen() {
        val reich = Lagebild.Eingabe(
            jetzt, imf = imf(3.3 to 3.3, 5.8 to 4.1), jahr = 2026,
            engpaesse = Engpaesse.Uebersicht(jetzt, tag.toInt(), mapOf("chokepoint6" to lage(-30.0))),
            spannungen = mapOf("TWN" to Spannungen.Wert(jetzt, "TWN", 80, 2.0, tag.toInt())),
            durchbrueche = listOf(Durchbrueche.Meldung("ki", "a", "u", jetzt, "q")),
            ensoIndex = Enso.Index(jetzt, listOf(Enso.Punkt("ASO", 2026, 0.4))),
        )
        val alt = Lagebild.Gemerkt(1L, Lagebild.merken(Lagebild.aussagen(reich)).mapValues { it.value + 5.0 })
        val a = Lagebild.aenderungen(alt, Lagebild.aussagen(reich))
        assertEquals(Lagebild.MAX_AENDERUNGEN, a.size)
    }

    @Test(timeout = 20_000)
    fun gemerktenStandSchreibenUndLesen() {
        val g = Lagebild.Gemerkt(123L, mapOf("KONJUNKTUR.WACHSTUM" to 3.2))
        val l = Lagebild.lesen(Lagebild.schreiben(g))!!
        assertEquals(123L, l.zeit)
        assertEquals(3.2, l.werte["KONJUNKTUR.WACHSTUM"]!!, 1e-9)
        assertNull(Lagebild.lesen("kein json"))
        assertNotNull(Lagebild.lesen("{}"))
        assertFalse(Lagebild.lesen("{}")!!.werte.isNotEmpty())
    }
}
