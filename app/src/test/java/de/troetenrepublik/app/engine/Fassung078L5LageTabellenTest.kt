package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Lagebild.Thema
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.ui.LageOffenText
import de.troetenrepublik.app.ui.LageTipp
import de.troetenrepublik.app.ui.LageWege
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

// Lage 0.78: Tabellen zum Aufklappen unter den Karten, gepflegte Erklaerungen, Wege (Blatt ueber der Lage statt Globus).
class Fassung078L5LageTabellenTest {
    private val jetzt = LocalDate.of(2026, 11, 12).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val tag = jetzt / 86_400_000L

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)

    private fun tabelle(a: List<Lagebild.Aussage>, t: Thema, s: String) = a.first { it.thema == t }.tabellen.first { it.schluessel == s }

    // Ausschnitt aus globus/konflikte.json (UCDP 26.1): derselbe Krieg unter zwei Laendern, ein kleinerer Konflikt
    private val konflikte = Konflikte.lesen(
        """{"jahr": 2025, "v": "26.1", "k": {
        "UKR": [["Government of Russia (Soviet Union)", "Government of Ukraine", 2]],
        "RUS": [["Government of Russia (Soviet Union)", "Government of Ukraine", 2]],
        "PHL": [["Government of Philippines", "CPP", 1]],
        "THA": [["Government of Cambodia (Kampuchea)", "Government of Thailand", 1], ["Government of Thailand", "Patani insurgents", 1]],
        "KHM": [["Government of Cambodia (Kampuchea)", "Government of Thailand", 1]]}}""",
    )!!

    @Test(timeout = 20_000)
    fun laenderTabellenMitVorjahrSortiert() {
        val d = Imf.Datensatz(
            abgerufen = jetzt,
            werte = mapOf(
                Imf.WACHSTUM to mapOf(
                    Imf.WELT to mapOf(2025 to 3.3, 2026 to 3.1), "DEU" to mapOf(2025 to 0.2, 2026 to 0.9),
                    "ARG" to mapOf(2025 to -1.3, 2026 to 4.5), "RUS" to mapOf(2026 to Double.NaN), "IND" to mapOf(2026 to 6.4),
                ),
                Imf.INFLATION to mapOf(Imf.WELT to mapOf(2025 to 5.8, 2026 to 4.3), "ARG" to mapOf(2025 to 219.9, 2026 to 41.3), "DEU" to mapOf(2026 to 2.1)),
            ),
        )
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, imf = d, jahr = 2026))
        val w = tabelle(a, Thema.KONJUNKTUR, "WACHSTUM_LAND")
        // Alle Laender mit Wert (nicht nur die groessten), hoechster zuerst; ohne Zahl (NaN) fehlt das Land; Welt steht nicht drin
        assertEquals(listOf("IND", "ARG", "DEU"), w.zeilen.map { it.id })
        assertEquals(2026, w.jahr)
        assertEquals(-1.3, w.zeilen[1].vorher!!, 1e-9)
        assertNull(w.zeilen[0].vorher)
        assertEquals(listOf("ARG", "DEU"), tabelle(a, Thema.KONJUNKTUR, "INFLATION_LAND").zeilen.map { it.id })
        // Der Stand mit Tabellen laesst sich schreiben und lesen
        val s = Lagebild.lesenStand(Lagebild.schreibenStand(Lagebild.Stand(jetzt, a)))!!
        assertEquals(3, s.aussagen.single().tabellen.first().zeilen.size)
    }

    @Test(timeout = 20_000)
    fun kriegeUndKonflikteJederEinmal() {
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, konflikte = konflikte))
        val kriege = tabelle(a, Thema.GEOPOLITIK, "KRIEGE")
        assertEquals(1, kriege.zeilen.size)
        assertEquals("Government of Russia (Soviet Union)|Government of Ukraine", kriege.zeilen[0].id)
        assertEquals(listOf("RUS", "UKR"), kriege.zeilen[0].teile)
        assertEquals(2025, kriege.jahr)
        val weitere = tabelle(a, Thema.GEOPOLITIK, "KONFLIKTE")
        // Der Grenzkonflikt unter zwei Laendern zaehlt einmal und steht zuerst; kein Krieg unter den weiteren Konflikten
        assertEquals(3, weitere.zeilen.size)
        assertEquals("Government of Cambodia (Kampuchea)|Government of Thailand", weitere.zeilen[0].id)
        assertTrue(weitere.zeilen.none { it.id.contains("Ukraine") })
        // Die Zahl der Kriege auf der Karte passt zur Tabelle
        assertEquals(kriege.zeilen.size.toDouble(), a.first().kennzahlen.first { it.schluessel == "KRIEGE" }.wert, 0.0)
    }

    @Test(timeout = 20_000)
    fun spannungEngpassKatastropheDurchbruch() {
        val sp = mapOf(
            "TWN" to Spannungen.Wert(jetzt, "TWN", 80, 2.0, tag.toInt()), "UKR" to Spannungen.Wert(jetzt, "UKR", 300, 1.1, tag.toInt()),
            "KOR" to Spannungen.Wert(jetzt, "KOR", 10, Double.NaN, tag.toInt()),
        )
        fun lage(abw: Double?, vn: Double = 100.0) = Engpaesse.Lage(if (abw == null) 50.0 else vn * (1 + abw / 100), 0.0, 0.0, if (abw == null) null else vn, null, null)
        val u = Engpaesse.Uebersicht(jetzt, tag.toInt(), mapOf("chokepoint6" to lage(-30.0), "chokepoint1" to lage(5.0), "chokepoint26" to lage(null)))
        val heute = LocalDate.ofEpochDay(tag)
        val kat = Katastrophen.Stand(
            jetzt,
            listOf(
                Katastrophen.Ereignis("FL", false, heute.minusDays(3).toString(), heute.toString(), listOf("PHL")),
                Katastrophen.Ereignis("TC", true, heute.minusDays(2).toString(), heute.toString(), listOf("PHL", "VNM")),
                Katastrophen.Ereignis("EQ", true, heute.minusDays(2).toString(), heute.toString(), listOf("JPN")),
            ),
        )
        val m = listOf(
            Durchbrueche.Meldung("ki", "a", "u", jetzt - 86_400_000L, "q1"), Durchbrueche.Meldung("ki", "b", "u", jetzt - 2 * 86_400_000L, "q2"),
            Durchbrueche.Meldung("fusion", "c", "u", jetzt - 20 * 86_400_000L, "q3"),
        )
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, spannungen = sp, engpaesse = u, katastrophen = kat, durchbrueche = m))
        // Spannungsgebiete: staerkster Anstieg zuerst, ohne Zahl kein Eintrag, Stand des juengsten Tages
        val s = tabelle(a, Thema.GEOPOLITIK, "SPANNUNG")
        assertEquals(listOf("TWN", "UKR"), s.zeilen.map { it.id })
        assertEquals(tag, s.stand)
        // Engpaesse: alle, groesster Rueckgang zuerst, ohne Vorjahreswert am Ende (mit Durchfahrten pro Tag)
        val e = tabelle(a, Thema.ROHSTOFFE, "ENGPAESSE")
        assertEquals(listOf("chokepoint6", "chokepoint1", "chokepoint26"), e.zeilen.map { it.id })
        assertNull(e.zeilen[2].wert)
        assertEquals(50, e.zeilen[2].zahl)
        // Katastrophen je Land: Rot zuerst, Arten in fester Reihenfolge, Erdbeben zaehlen nicht
        val k = tabelle(a, Thema.KLIMA, "KATASTROPHEN_LAND")
        assertEquals(listOf("PHL", "VNM"), k.zeilen.map { it.id })
        assertEquals(listOf("TC", "FL"), k.zeilen[0].teile)
        assertEquals(2.0, k.zeilen[0].wert!!, 0.0)
        assertEquals(1, k.zeilen[0].zahl)
        // Durchbrueche je Thema: neue der letzten sieben Tage, juengste Schlagzeile mit Quelle
        val d = tabelle(a, Thema.TECHNIK, "DURCHBRUCH_THEMA")
        assertEquals(listOf("ki", "fusion"), d.zeilen.map { it.id })
        assertEquals(2.0, d.zeilen[0].wert!!, 0.0)
        assertEquals(listOf("a", "q1"), d.zeilen[0].teile)
        assertEquals(0.0, d.zeilen[1].wert!!, 0.0)
        // Ohne NaN im Stand: Schreiben gelingt
        assertNotNull(Lagebild.lesenStand(Lagebild.schreibenStand(Lagebild.Stand(jetzt, a))))
    }

    @Test(timeout = 20_000)
    fun zinspfadUndZusammenfuehren() {
        val pfad = listOf(Zinserwartung.Monat(2026, 11, 3.88), Zinserwartung.Monat(2026, 12, 3.70), Zinserwartung.Monat(2027, 11, 3.2))
        val a = Lagebild.aussagen(Lagebild.Eingabe(jetzt, zins = Zinserwartung.erwartung(pfad)))
        // Den Satz je Monat zeigt die Zinskarte im Ausblick, die Lage fuehrt keine eigene Monatstabelle
        assertTrue(a.first { it.thema == Thema.ZINSEN }.tabellen.isEmpty())
        // Ein neuer Abruf ohne Tabelle laesst die alte stehen; eine neue Tabelle ersetzt die alte
        val alt = Lagebild.aussagen(Lagebild.Eingabe(jetzt, konflikte = konflikte))
        val ohne = listOf(Lagebild.Aussage(Thema.GEOPOLITIK, alt.first().kennzahlen, "globus/engpaesse"))
        assertEquals(2, Lagebild.zusammenfuehren(alt, ohne).first().tabellen.size)
        val neuK = Konflikte.Stand(2026, "x", mapOf("SDN" to listOf(Konflikte.Konflikt("Government of Sudan", "RSF, SFA, SPLM/A-North", 2))))
        val neu = Lagebild.aussagen(Lagebild.Eingabe(jetzt, konflikte = neuK))
        val z = Lagebild.zusammenfuehren(alt, neu).first().tabellen.first { it.schluessel == "KRIEGE" }
        assertEquals(2026, z.jahr)
    }

    // ------------------------------------------------------------ Wege: alles bleibt in der Lage, ausser dem Weg am Ende

    @Test(timeout = 20_000)
    fun tippsBleibenInDerLage() {
        fun z(id: String, vararg teile: String) = Lagebild.Zeile(id, teile = teile.toList())
        assertEquals(LageTipp.Land("DEU"), LageWege.zeile("WACHSTUM_LAND", z("DEU")))
        assertEquals(LageTipp.Land("ARG"), LageWege.zeile("INFLATION_LAND", z("ARG")))
        assertEquals(LageTipp.Land("PHL"), LageWege.zeile("KATASTROPHEN_LAND", z("PHL")))
        assertEquals(LageTipp.Land("SOM"), LageWege.zeile("KRIEGE", z("Government of Somalia|Al-Shabaab", "SOM")))
        // Mehrere Laender: jeder Name ist ein eigener Tipp, die Zeile selbst oeffnet nichts
        assertNull(LageWege.zeile("KRIEGE", z("A|B", "RUS", "UKR")))
        assertEquals(LageTipp.Spannung("TWN"), LageWege.zeile("SPANNUNG", z("TWN")))
        assertEquals(LageTipp.Engpass("chokepoint6"), LageWege.zeile("ENGPAESSE", z("chokepoint6")))
        assertEquals(LageTipp.Durchbruch("ki"), LageWege.zeile("DURCHBRUCH_THEMA", z("ki")))
        assertNull(LageWege.zeile("ZINS_PFAD", z("2026-12")))
        for (s in listOf("WACHSTUM_LAND", "INFLATION_LAND", "KATASTROPHEN_LAND", "KRIEGE", "KONFLIKTE", "SPANNUNG", "ENGPAESSE", "DURCHBRUCH_THEMA", "ZINS_PFAD")) {
            LageWege.zeile(s, z("X", "X"))?.let { assertTrue(s, LageWege.bleibtInDerLage(it)) }
        }
        assertTrue(LageWege.bleibtInDerLage(LageTipp.Abschnitt("KONJUNKTUR.WACHSTUM_LAND")))
        // Nur der Weg am Ende der Tabelle wechselt die Seite, und zwar genau zur Ebene oder zum Abschnitt
        assertEquals(LageTipp.Seite("globus/wachstum"), LageWege.weg(Thema.KONJUNKTUR))
        assertEquals(LageTipp.Seite("maerkte/ausblick/zins"), LageWege.weg(Thema.ZINSEN))
        assertEquals(LageTipp.Seite("globus/engpaesse/weltlage"), LageWege.weg(Thema.GEOPOLITIK))
        assertEquals(LageTipp.Seite("globus/engpaesse"), LageWege.weg(Thema.ROHSTOFFE))
        assertEquals(LageTipp.Seite("globus/engpaesse/weltlage"), LageWege.weg(Thema.KLIMA))
        assertEquals(LageTipp.Seite("maerkte/trends/durchbrueche"), LageWege.weg(Thema.TECHNIK))
        for (t in Thema.entries) assertFalse(LageWege.bleibtInDerLage(LageWege.weg(t)))
    }

    @Test(timeout = 20_000)
    fun offeneAbschnitteBleibenGemerkt() {
        var t = ""
        t = LageOffenText.wechseln(t, "KONJUNKTUR.WACHSTUM_LAND")
        t = LageOffenText.wechseln(t, "KLIMA.ENSO")
        assertTrue(LageOffenText.enthaelt(t, "KONJUNKTUR.WACHSTUM_LAND"))
        assertTrue(LageOffenText.enthaelt(t, "KLIMA.ENSO"))
        assertFalse(LageOffenText.enthaelt(t, "KONJUNKTUR.WACHSTUM"))
        // Der Text ist der gemerkte Zustand: unveraendert nach dem Schliessen eines Blatts
        val gemerkt = t
        assertEquals(gemerkt, LageOffenText.oeffnen(t, "KLIMA.ENSO"))
        t = LageOffenText.wechseln(t, "KONJUNKTUR.WACHSTUM_LAND")
        assertEquals("KLIMA.ENSO", t)
        assertEquals("", LageOffenText.wechseln(t, "KLIMA.ENSO"))
        assertEquals("KONJUNKTUR.WACHSTUM_LAND", LageWege.abschnitt(Thema.KONJUNKTUR, "WACHSTUM_LAND"))
    }

    // ------------------------------------------------------------ Erklaerungen

    private val strich = Regex("[–—]| - ")

    @Test(timeout = 20_000)
    fun zuJedemKonfliktGebietUndEngpassEinSatz() {
        val e = LageErklaerungen.lesen(asset(LageErklaerungen.DATEI))!!
        assertNotNull(Alter.tag(e.stand))
        val k = Konflikte.lesen(asset(Konflikte.DATEI))!!
        val alle = k.nachLand.values.flatten().map { Lagebild.konfliktSchluessel(it) }.toSet()
        assertTrue(alle.size >= 10)
        val fehlen = alle.filter { id -> e.konflikte[id]?.let { it.satz(true) == null || it.satz(false) == null || it.titel(true) == null || it.titel(false) == null } ?: true }
        assertTrue("Ohne Erklaerung: $fehlen", fehlen.isEmpty())
        for (g in Spannungen.GEBIETE) assertTrue(g.id, e.gebiete[g.id]?.let { it.satz(true) != null && it.satz(false) != null } == true)
        for (s in Engpaesse.STELLEN) assertTrue(s.id, e.engpaesse[s.id]?.let { it.satz(true) != null && it.satz(false) != null } == true)
        // Keine Eintraege fuer Unbekanntes (Tippfehler im Schluessel)
        assertTrue((e.konflikte.keys - alle).isEmpty())
        assertTrue((e.gebiete.keys - Spannungen.GEBIETE.map { it.id }.toSet()).isEmpty())
        assertTrue((e.engpaesse.keys - Engpaesse.STELLEN.map { it.id }.toSet()).isEmpty())
        // Keine Gedankenstriche, keine Fragen, hoechstens zwei Saetze
        val texte = (e.konflikte.values + e.gebiete.values + e.engpaesse.values).flatMap { listOf(it.de, it.en, it.tde, it.ten) }.filter { it.isNotEmpty() }
        for (x in texte) {
            assertFalse(x, strich.containsMatchIn(x))
            assertFalse(x, x.contains('?'))
            assertTrue(x, Regex("[.;] [A-ZÄÖÜ]").findAll(x).count() <= 1)
        }
    }

    @Test(timeout = 20_000)
    fun erklaerungenDerTabellenDeutschUndEnglisch() {
        val schluessel = listOf("WACHSTUM_LAND", "INFLATION_LAND", "ZINS_PFAD", "KRIEGE", "KONFLIKTE", "SPANNUNG", "ENGPAESSE", "ENSO", "KATASTROPHEN_LAND", "DURCHBRUCH_THEMA", "BESCHLEUNIGT")
        for (s in schluessel) for (x in listOf(De, En)) {
            val text = x.lbErk(s, "5 %")
            assertTrue(s, text.isNotBlank())
            assertFalse(text, strich.containsMatchIn(text))
            assertFalse(text, text.contains('?'))
            assertTrue(x.lbTab(s) != s)
        }
        for (t in Thema.entries) for (x in listOf(De, En)) assertTrue(x.lbWeg(t.name).isNotBlank())
        assertEquals("Im Globus ansehen", De.lbWeg("KONJUNKTUR"))
        assertEquals("Zu den Durchbrüchen", De.lbWeg("TECHNIK"))
        assertEquals("Zum Ausblick", De.lbWeg("ZINSEN"))
    }
}
