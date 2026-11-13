package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Abhaengigkeiten.Abschnitt
import de.troetenrepublik.app.engine.Abhaengigkeiten.Eingabe
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeilentyp
import de.troetenrepublik.app.engine.Einflussverzeichnis.Art
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Karte "Abhaengigkeiten und Risiken": Zeilen, Reihenfolge, Luecken und Alterung.
class AbhaengigkeitenTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private val nvidia = Wertbild("NVDA", "HALBLEITER", "US", "US")
    private val vw = Wertbild("VOW3.DE", "FAHRZEUGE", "DE", "DE")
    private val alphamin = Wertbild("AFM.V", "BERGBAU", "CD", "MU")

    private fun lage(abw: Double, vorjahr: Double = 100.0) = Engpaesse.Lage(vorjahr * (1 + abw / 100), 0.0, 0.0, vorjahr, null, null)

    private fun emp(vararg k: Empfindlichkeit.Koeffizient, ohne: List<Empfindlichkeit.Faktor> = emptyList()) =
        Empfindlichkeit.Ergebnis(553, 16800, 17400, 0.28, 0.22, 0.34 to 0.19, k.toList(), ohne)

    private fun koeff(f: Empfindlichkeit.Faktor, wert: Double, u: Empfindlichkeit.Urteil) =
        Empfindlichkeit.Koeffizient(f, wert, 4.0, wert to wert, 3.0 to 3.0, u)

    @Test(timeout = 20_000)
    fun rohstoffeStehenNachKonzentrationMitHerkunft() {
        // Unbekannte Firma einer groben Branche: Rueckfall auf die Liste der Branche, so gekennzeichnet
        val z = Abhaengigkeiten.karte(index, Eingabe(Wertbild("UNBEKANNT", "COMPUTER", "US", "US"), usWert = true, landPunkte = 62.0, gesamtbild = 0.3, gesamtbildJahr = 2026), heute)
        val roh = z.filter { it.typ == Zeilentyp.ROHSTOFF }
        assertEquals(Einflussverzeichnis.rohstoffeFuerBranche(index, "COMPUTER").map { it.id }.toSet(), roh.map { it.schluessel }.toSet())
        // Staerkste Konzentration zuerst, ohne veroeffentlichte Anteile am Ende
        assertEquals(roh.map { it.zahl ?: -1.0 }.sortedDescending(), roh.map { it.zahl ?: -1.0 })
        assertTrue(roh.all { it.art == Art.ABGELEITET && it.stand == index.rohstoffStand })
        assertTrue(roh.all { it.rohstoff?.quelle == Rohstoffprofil.Quelle.BRANCHE })
        // Kritisch laut EU-Liste markiert
        assertEquals(1, roh.first { it.schluessel == "KUPFER" }.ganz)
        // Foerderlaender mit Anteil, die groessten zuerst
        assertEquals("CN:69", roh.first { it.schluessel == "SELTENE_ERDEN" }.liste.first())
        // Kupfer fuehrt zur Rohstoffseite
        assertEquals("instrument/HG=F", roh.first { it.schluessel == "KUPFER" }.ziel)
    }

    @Test(timeout = 20_000)
    fun fablessFirmaZeigtNurDenWaferUeberDenAuftragsfertiger() {
        val z = Abhaengigkeiten.karte(index, Eingabe(nvidia, usWert = true, landPunkte = 62.0, gesamtbild = 0.3, gesamtbildJahr = 2026), heute)
        val roh = z.filter { it.typ == Zeilentyp.ROHSTOFF }
        assertEquals(listOf("SILIZIUM"), roh.map { it.schluessel })
        assertEquals(Rohstoffprofil.Weg.FERTIGER, roh.single().rohstoff?.weg)
        assertEquals("WAFER", roh.single().rohstoff?.verwendung)
        assertEquals(Rohstoffprofil.Quelle.FIRMA, roh.single().rohstoff?.quelle)
        // SEC-Angaben kommen dazu und sind als gemeldet gekennzeichnet
        val mit = Abhaengigkeiten.karte(index, Eingabe(nvidia, usWert = true, sec = Rohstoffprofil.SecAngaben("2026-05-28")), heute)
        val gemeldet = mit.filter { it.typ == Zeilentyp.ROHSTOFF && it.art == Art.GEMELDET }.map { it.schluessel }.toSet()
        assertEquals(setOf("ZINN", "TANTAL", "WOLFRAM", "GOLD"), gemeldet)
        // Die Aussage zur Konzentration nennt das Unternehmen, nicht die Branche
        assertTrue(Kernaussagen.ableiten(index, z, null).any { it.typ == Kernaussagen.Typ.ROHSTOFFE && it.firma })
    }

    @Test(timeout = 20_000)
    fun herkunftUndLandzeilen() {
        val z = Abhaengigkeiten.karte(index, Eingabe(alphamin, taetigkeit = "CD", landPunkte = 31.0, katastrophen = 2, katastrophenRot = 1), heute)
        val h = z.filter { it.abschnitt == Abschnitt.HERKUNFT }
        // Sitz Mauritius gemeldet, Taetigkeit Kongo abgeleitet, Land mit Landrisiko und Katastrophen
        assertEquals("MU", h.first { it.typ == Zeilentyp.SITZ }.schluessel)
        assertEquals(Art.GEMELDET, h.first { it.typ == Zeilentyp.SITZ }.art)
        assertEquals("CD", h.first { it.typ == Zeilentyp.TAETIGKEIT }.schluessel)
        assertEquals("land/CD", h.first { it.typ == Zeilentyp.LANDRISIKO }.ziel)
        assertEquals(1.0, h.first { it.typ == Zeilentyp.KATASTROPHEN }.zahl!!, 0.0)
        // Taetigkeit gleich Sitz entfaellt
        val gleich = Abhaengigkeiten.karte(index, Eingabe(vw, taetigkeit = "DE"), heute)
        assertTrue(gleich.none { it.typ == Zeilentyp.TAETIGKEIT })
        // Ohne Land keine Landzeilen
        val ohne = Abhaengigkeiten.karte(index, Eingabe(Wertbild("XYZ", "BERGBAU", null), landPunkte = 50.0), heute)
        assertTrue(ohne.none { it.typ == Zeilentyp.LANDRISIKO || it.typ == Zeilentyp.SITZ })
    }

    @Test(timeout = 20_000)
    fun auffaelligeStaendeStehenVorn() {
        val e = Eingabe(
            nvidia, engpaesse = mapOf("chokepoint11" to lage(-35.0)),
            spannungen = mapOf("TWN" to Spannungen.Wert(0L, "TWN", 120, 2.4, 20_000)),
        )
        val g = Abhaengigkeiten.karte(index, e, heute).filter { it.typ == Zeilentyp.GROESSE && it.abschnitt == Abschnitt.EINFLUESSE }
        // Die beiden auffaelligen zuerst, danach die uebrigen nach Typ
        assertEquals(setOf("engpass:chokepoint11", "spannung:TWN"), g.take(2).map { it.schluessel }.toSet())
        assertEquals(-35.0, g.first { it.schluessel == "engpass:chokepoint11" }.zahl!!, 1e-6)
        assertEquals(2.4, g.first { it.schluessel == "spannung:TWN" }.zahl!!, 1e-9)
        assertEquals("globus/engpaesse", g.first { it.schluessel == "engpass:chokepoint11" }.ziel)
        // Programme stehen unter Politik und fuehren zum Wirkungsnetz
        val p = Abhaengigkeiten.karte(index, e, heute).filter { it.abschnitt == Abschnitt.POLITIK && it.typ == Zeilentyp.GROESSE }
        assertTrue(p.any { it.schluessel == "programm:chips-us" && it.ziel == "wirkung/programm:chips-us" })
        // Ein Engpass mit wenig Verkehr im Vorjahr ist nicht auffaellig (zu wenig Schiffe fuer einen Vergleich)
        val wenig = Abhaengigkeiten.karte(index, Eingabe(nvidia, engpaesse = mapOf("chokepoint11" to lage(-50.0, vorjahr = 4.0))), heute)
            .filter { it.typ == Zeilentyp.GROESSE && it.abschnitt == Abschnitt.EINFLUESSE }
        assertEquals("china", wenig.first().schluessel)
        assertTrue(wenig.take(2).none { it.schluessel == "engpass:chokepoint11" })
    }

    @Test(timeout = 20_000)
    fun kursempfindlichkeitUndLuecken() {
        val e = Eingabe(
            vw, usWert = false,
            empfindlichkeit = emp(
                koeff(Empfindlichkeit.Faktor.MARKT, 0.77, Empfindlichkeit.Urteil.BELEGT),
                koeff(Empfindlichkeit.Faktor.DOLLAR, -0.69, Empfindlichkeit.Urteil.NICHT_BELEGT),
                koeff(Empfindlichkeit.Faktor.ZINS10J, 0.07, Empfindlichkeit.Urteil.BELEGT),
                ohne = listOf(Empfindlichkeit.Faktor.OEL, Empfindlichkeit.Faktor.GOLD),
            ),
        )
        val z = Abhaengigkeiten.karte(index, e, heute).filter { it.abschnitt == Abschnitt.EMPFINDLICHKEIT }
        assertEquals(Zeilentyp.EMPF_MARKT, z.first().typ)
        assertEquals(Empfindlichkeit.Aussage.LAEUFT_MIT, z.first().aussage)
        assertEquals(0.28, z.first().zahl2!!, 1e-9)
        assertEquals(11, z.first().ganz)
        assertTrue(z.all { it.art == Art.GEMESSEN })
        // Belegte stehen einzeln, alle ohne Beleg in einer Zeile (die nicht belegte Steigung zuerst, dann die uebrigen)
        val faktoren = z.filter { it.typ == Zeilentyp.EMPF_FAKTOR }
        assertEquals(listOf("ZINS10J"), faktoren.map { it.schluessel })
        assertEquals(listOf("DOLLAR", "OEL", "GOLD"), z.single { it.typ == Zeilentyp.EMPF_OHNE }.liste)
        assertEquals(0.28, z.single { it.typ == Zeilentyp.EMPF_OHNE }.zahl2!!, 1e-9)
        // Zu kurze Kurshistorie ist eine Zeile, kein Schweigen
        val kurz = Abhaengigkeiten.karte(index, Eingabe(vw, empfindlichkeitZuKurz = true), heute)
        assertNotNull(kurz.firstOrNull { it.typ == Zeilentyp.EMPF_ZU_KURZ })
        // Ohne abgelaufene Angaben gibt es keine Luecken-Zeile (Lieferer ohne freie Quelle stehen nicht mehr da)
        assertTrue(Abhaengigkeiten.karte(index, Eingabe(vw), heute).none { it.abschnitt == Abschnitt.LUECKEN })
    }

    @Test(timeout = 20_000)
    fun konzernmutterStehtNachDemSitzMitStand() {
        val m = Abhaengigkeiten.Konzernmutter("Siemens Aktiengesellschaft", "DE", 20_000L)
        val z = Abhaengigkeiten.karte(index, Eingabe(vw, taetigkeit = null).copy(konzernmutter = m), heute)
        val zeile = z.first { it.typ == Zeilentyp.MUTTER }
        assertEquals(Abschnitt.HERKUNFT, zeile.abschnitt)
        assertEquals("DE", zeile.schluessel)
        assertEquals(listOf("Siemens Aktiengesellschaft"), zeile.liste)
        assertEquals(Art.GEMELDET, zeile.art)
        assertEquals(20_000L, zeile.stand)
        assertTrue(Abhaengigkeiten.karte(index, Eingabe(vw), heute).none { it.typ == Zeilentyp.MUTTER })
    }

    @Test(timeout = 20_000)
    fun alteAngabenWerdenGrauUndDannAusgeblendet() {
        val grau = heute + 19 * 30
        val z18 = Abhaengigkeiten.karte(index, Eingabe(nvidia), grau)
        assertTrue(z18.filter { it.typ == Zeilentyp.ROHSTOFF }.all { it.grau })
        val weg = heute + 31 * 30
        val z30 = Abhaengigkeiten.karte(index, Eingabe(nvidia), weg)
        assertTrue(z30.none { it.typ == Zeilentyp.ROHSTOFF })
        // Frische Angaben sind nicht grau
        assertTrue(Abhaengigkeiten.karte(index, Eingabe(nvidia), heute).filter { it.typ == Zeilentyp.ROHSTOFF }.none { it.grau })
    }

    @Test(timeout = 20_000)
    fun rohstoffKarteMitFoerderlaendern() {
        val z = Abhaengigkeiten.rohstoffKarte(index, Eingabe(Wertbild("HG=F", null, null)), heute)
        val r = z.first()
        assertEquals(Zeilentyp.ROHSTOFF, r.typ)
        assertEquals("KUPFER", r.schluessel)
        assertEquals(5, r.liste.size)
        // Ein Kontrakt ohne Rohstoffdaten (Oel) zeigt nur die Einfluesse
        val oel = Abhaengigkeiten.rohstoffKarte(index, Eingabe(Wertbild("BZ=F", null, null)), heute)
        assertTrue(oel.none { it.typ == Zeilentyp.ROHSTOFF })
        assertTrue(oel.any { it.schluessel == "oel" })
        assertTrue(oel.any { it.schluessel == "engpass:chokepoint6" })
    }

    @Test(timeout = 20_000)
    fun etfKarteZaehltGroessenUeberDiePositionen() {
        val pos = listOf(nvidia to 0.10, Wertbild("TSM", "HALBLEITER", "TW", "TW") to 0.05, Wertbild("XOM", "OEL_GAS", "US", "US") to 0.04)
        val z = Abhaengigkeiten.etfKarte(index, pos, 0.19, heute)
        val g = z.filter { it.typ == Zeilentyp.GROESSE }
        // Nur Groessen, die mindestens zwei Positionen treffen; die mit dem groessten Anteil zuerst
        assertTrue(g.all { (it.ganz ?: 0) >= 2 })
        val halb = g.first { it.schluessel == "halbleiter" }
        assertEquals(0.15, halb.zahl2!!, 1e-9)
        assertEquals(2, halb.ganz)
        assertTrue(g.zipWithNext().all { (a, b) -> a.zahl2!! >= b.zahl2!! })
        // Die Luecke nennt den erfassten Anteil
        val l = z.first { it.typ == Zeilentyp.LUECKE_SEGMENTE }
        assertEquals(2, l.ganz)
        assertEquals(0.19, l.zahl!!, 1e-9)
        // Ohne Positionen bleibt nur die Luecke
        assertEquals(1, Abhaengigkeiten.etfKarte(index, emptyList(), 0.0, heute).size)
    }
}
