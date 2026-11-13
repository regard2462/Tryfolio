package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Abhaengigkeiten.Eingabe
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeilentyp
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Kernaussagen.Typ
import de.troetenrepublik.app.market.SecBericht
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Paket q76d: Kernaussagen der Karte "Abhaengigkeiten und Risiken", Messung und Auffaelligkeit je Einfluss, Bausteine des Landrisikos.
class Fassung076DTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    // Erneuerbare Versorger: Silizium (China 79 %), Seltene Erden (China 70 %), Silber, Kupfer (Profil, nicht Branche)
    private val umicore = Wertbild("ERNEUERBAR.TEST", "VERSORGER", "BE", "BE", industrie = "Utilities - Renewable")
    private val nvidia = Wertbild("NVDA", "HALBLEITER", "US", "US")
    private val exxon = Wertbild("XOM", "OEL_GAS", "US", "US")

    private fun koeff(f: Empfindlichkeit.Faktor, wert: Double, u: Empfindlichkeit.Urteil) =
        Empfindlichkeit.Koeffizient(f, wert, 4.0, wert to wert, 3.0 to 3.0, u)

    private fun emp(vararg k: Empfindlichkeit.Koeffizient) =
        Empfindlichkeit.Ergebnis(553, 16800, 17400, 0.28, 0.22, 0.34 to 0.19, k.toList(), emptyList())

    private fun aussagen(e: Eingabe, g: SecBericht.Auswertung? = null): List<Kernaussagen.Aussage> =
        Kernaussagen.ableiten(index, Abhaengigkeiten.karte(index, e, heute), g)

    private fun posten(n: String, w: Double, k: Int = 0, nr: String = "") = SecBericht.Posten(n, w, "", k, nr)

    private fun gliederung(
        segmente: List<SecBericht.Posten> = emptyList(), regionen: List<SecBericht.Posten> = emptyList(),
        kunden: List<SecBericht.Posten> = emptyList(), sStatus: Int = 0, rStatus: Int = 0,
    ) = SecBericht.Auswertung(jahr = 2025, segmente = segmente, regionen = regionen, kunden = kunden, segmenteStatus = sStatus, regionenStatus = rStatus)

    @Test(timeout = 20_000)
    fun ohneFirmenangabenStehenMessungRohstoffeUndLand() {
        val e = Eingabe(
            umicore, landPunkte = 64.0,
            empfindlichkeit = emp(
                koeff(Empfindlichkeit.Faktor.MARKT, 0.9, Empfindlichkeit.Urteil.BELEGT),
                koeff(Empfindlichkeit.Faktor.KUPFER, 0.4, Empfindlichkeit.Urteil.BELEGT),
                koeff(Empfindlichkeit.Faktor.CHINA, 0.2, Empfindlichkeit.Urteil.NICHT_BELEGT),
            ),
        )
        val a = aussagen(e)
        assertEquals(listOf(Typ.KURS, Typ.ROHSTOFFE, Typ.LAND), a.take(3).map { it.typ })
        // Kurs: nur belegte Groessen ausser dem Markt
        assertEquals(listOf("KUPFER"), a[0].liste.map { it.schluessel })
        assertEquals(0.4, a[0].liste.single().zahl!!, 1e-9)
        // Rohstoffe aus China, der groesste Anteil zuerst
        assertEquals("CN", a[1].land)
        assertEquals(listOf("SILIZIUM", "SELTENE_ERDEN"), a[1].liste.take(2).map { it.schluessel })
        assertEquals(79.0, a[1].liste.first().zahl!!, 0.0)
        // Land: Sitz und Stufe
        assertEquals("BE", a[2].sitz)
        assertEquals(Kernaussagen.Stufe.MITTEL, a[2].stufe)
        assertTrue(a.size in 2..Kernaussagen.MAX_AUSSAGEN)
    }

    @Test(timeout = 20_000)
    fun eigeneAngabenDerFirmaStehenVorDenAbleitungen() {
        val g = gliederung(
            segmente = listOf(posten("Devices", 0.38), posten("Cloud", 0.62)),
            regionen = listOf(posten("Asia", 0.58), posten("Europe", 0.42)),
            kunden = listOf(posten("Kunde A", 0.24, k = 1, nr = "A")),
        )
        val e = Eingabe(
            umicore, landPunkte = 80.0,
            empfindlichkeit = emp(koeff(Empfindlichkeit.Faktor.OEL, -0.3, Empfindlichkeit.Urteil.BELEGT)),
        )
        val a = aussagen(e, g)
        // Hoechstens vier, Segment zuerst, dann die Messung, dann die Region
        assertEquals(Kernaussagen.MAX_AUSSAGEN, a.size)
        assertEquals(listOf(Typ.SEGMENT, Typ.KURS, Typ.REGION, Typ.ROHSTOFFE), a.map { it.typ })
        // Der groesste Posten zaehlt, nicht der erste in der Liste
        assertEquals("Cloud", a[0].posten!!.n)
        assertEquals(0.62, a[0].zahl, 1e-9)
        // Gegen den Zusammenhang: negative Steigung bleibt negativ
        assertEquals(-0.3, a[1].liste.single().zahl!!, 1e-9)
    }

    @Test(timeout = 20_000)
    fun nichtBelegtesEntfaellt() {
        // Region unter der Schwelle, nicht eindeutig gemeldete Segmente und kleiner Kunde: keine Aussage
        val g = gliederung(
            segmente = listOf(posten("Cloud", 0.62)), sStatus = 1,
            regionen = listOf(posten("Asia", 0.45), posten("Europe", 0.30)),
            kunden = listOf(posten("Kunde A", 0.12, k = 1, nr = "A")),
        )
        val a = aussagen(Eingabe(Wertbild("XYZ", null, null)), g)
        assertTrue(a.isEmpty())
        // Kein Land, kein Landrisiko: keine Landaussage; keine belegte Messung: keine Kursaussage
        val ohne = aussagen(Eingabe(umicore, empfindlichkeit = emp(koeff(Empfindlichkeit.Faktor.GOLD, 0.1, Empfindlichkeit.Urteil.NICHT_BELEGT))))
        assertTrue(ohne.none { it.typ == Typ.LAND || it.typ == Typ.KURS })
        // Ein Grosskunde ab 20 Prozent kommt dazu, eine Kundengruppe nicht
        val kunde = aussagen(Eingabe(Wertbild("XYZ", null, null)), gliederung(kunden = listOf(posten("Kunde A", 0.31, k = 1, nr = "A"))))
        assertEquals(listOf(Typ.KUNDE), kunde.map { it.typ })
        val gruppe = aussagen(Eingabe(Wertbild("XYZ", null, null)), gliederung(kunden = listOf(posten("3 Kunden", 0.5, k = 2, nr = "3"))))
        assertTrue(gruppe.isEmpty())
    }

    @Test(timeout = 20_000)
    fun landrisikoInStufen() {
        assertEquals(Kernaussagen.Stufe.NIEDRIG, Kernaussagen.stufe(70.0))
        assertEquals(Kernaussagen.Stufe.MITTEL, Kernaussagen.stufe(69.9))
        assertEquals(Kernaussagen.Stufe.MITTEL, Kernaussagen.stufe(50.0))
        assertEquals(Kernaussagen.Stufe.HOCH, Kernaussagen.stufe(49.9))
        // Sitz fehlt: die Aussage nennt nur das Land
        val a = aussagen(Eingabe(Wertbild("XYZ", "BERGBAU", "CD"), landPunkte = 31.0))
        val land = a.single { it.typ == Typ.LAND }
        assertEquals("", land.sitz)
        assertEquals("CD", land.land)
        assertEquals(Kernaussagen.Stufe.HOCH, land.stufe)
    }

    @Test(timeout = 20_000)
    fun auffaelligeStaendeWerdenAussageUndEinflussUebrigeEbenso() {
        val auffaellig = Eingabe(
            nvidia, engpaesse = mapOf("chokepoint11" to Engpaesse.Lage(65.0, 0.0, 0.0, 100.0, null, null)),
            spannungen = mapOf("TWN" to Spannungen.Wert(0L, "TWN", 120, 2.4, 20_000)),
        )
        val z = Abhaengigkeiten.karte(index, auffaellig, heute)
        assertTrue(z.filter { it.typ == Zeilentyp.GROESSE && it.auffaellig }.map { it.schluessel }.containsAll(listOf("engpass:chokepoint11", "spannung:TWN")))
        val a = Kernaussagen.ableiten(index, z, null).single { it.typ == Typ.AUFFAELLIG }
        assertTrue(a.liste.size in 1..2)
        assertTrue(a.liste.all { p -> z.first { it.schluessel == p.schluessel }.auffaellig })
        // Ohne auffaellige Staende nennt die Aussage die ersten Groessen der Branche
        val ruhig = Kernaussagen.ableiten(index, Abhaengigkeiten.karte(index, Eingabe(nvidia), heute), null)
        assertNull(ruhig.firstOrNull { it.typ == Typ.AUFFAELLIG })
        assertNotNull(ruhig.firstOrNull { it.typ == Typ.EINFLUESSE })
    }

    @Test(timeout = 20_000)
    fun einflussTraegtDieMessungDerselbenGroesse() {
        val e = Eingabe(
            exxon,
            empfindlichkeit = emp(
                koeff(Empfindlichkeit.Faktor.MARKT, 0.8, Empfindlichkeit.Urteil.BELEGT),
                koeff(Empfindlichkeit.Faktor.OEL, 0.5, Empfindlichkeit.Urteil.BELEGT),
            ),
        )
        val oel = Abhaengigkeiten.karte(index, e, heute).first { it.schluessel == "oel" }
        val m = oel.messung
        assertNotNull(m)
        assertEquals(Empfindlichkeit.Faktor.OEL, m!!.faktor)
        assertEquals(Empfindlichkeit.Aussage.LAEUFT_MIT, m.aussage)
        assertEquals(0.5, m.wert, 1e-9)
        // Ohne Messung keine Angabe
        assertNull(Abhaengigkeiten.karte(index, Eingabe(exxon), heute).first { it.schluessel == "oel" }.messung)
    }

    @Test(timeout = 20_000)
    fun bausteineErgebenDieLandrisikoPunkte() {
        val faelle = listOf(
            LandRisiko.Fakten(wgi = 78.0),
            LandRisiko.Fakten(wgi = 40.0, stabilitaet = 10.0, sanktionenEu = 1, sanktionenUs = 1, openSanctions = 60, konflikt = 2, reise = 4, strukturen = 5),
            LandRisiko.Fakten(wgi = 55.0, stabilitaet = 30.0, konflikt = 1, reise = 3, strukturen = 2, openSanctions = 7),
            LandRisiko.Fakten(sanktionenEu = 1),
        )
        for (f in faelle) {
            val b = LandRisikoBausteine.zerlegen(f)
            assertEquals("BASIS", b.first().key)
            val summe = b.sumOf { it.wert }
            assertEquals(LandRisiko.punkte(f)!!, summe.coerceIn(0.0, 100.0), 1e-9)
            // Abzuege sind negativ, nur die Basis nicht
            assertTrue(b.drop(1).all { it.wert < 0 })
        }
        // Ohne Angaben keine Bausteine
        assertTrue(LandRisikoBausteine.zerlegen(LandRisiko.Fakten()).isEmpty())
    }
}
