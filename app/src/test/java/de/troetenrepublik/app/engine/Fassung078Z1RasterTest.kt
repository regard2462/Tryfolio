package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.FinderVorlagen.Grenze
import de.troetenrepublik.app.engine.FinderVorlagen.Pruefung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Auswahlraster im Aktienfinder (0.78) mit erfundenen Werten: Rangsumme, Anteilsgrenzen, „mindestens n von m“, Spitze,
// Felder aus den Jahresabschluessen und die neun Vorlagen.
class Fassung078Z1RasterTest {

    private val jetzt = LocalDate.of(2026, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun d(
        symbol: String, branche: String = "MASCHINEN", werte: Map<Feld, Double> = emptyMap(), kgv: Double? = null, kbv: Double? = null,
        boersenwert: Double? = 2e9, dividende: Double? = null, insider: Int? = null, mitAbschluss: Boolean = true, mitDetails: Boolean = false,
        sektor: String = "", freierCashflow: Double? = null,
    ) = Aktienfinder.Daten(
        symbol, symbol, "US", branche, kgv = kgv, kbv = kbv, boersenwert = boersenwert, dividende = dividende, insider = insider,
        mitAbschluss = mitAbschluss, mitDetails = mitDetails, sektor = sektor, freierCashflow = freierCashflow, werte = werte,
    )

    private fun v(id: String) = FinderVorlagen.vorlage(id)!!

    private fun pruefen(liste: List<Aktienfinder.Daten>, id: String) = FinderRaster.pruefen(liste, v(id).grenzen, v(id), jetzt)

    // ------------------------------------------------------------ Bausteine

    @Test
    fun raengeMitGleichstand() {
        val r = FinderRaster.raenge(mapOf("A" to 30.0, "B" to 10.0, "C" to 30.0, "D" to 20.0))
        assertEquals(1.5, r.getValue("A"), 1e-9)
        assertEquals(1.5, r.getValue("C"), 1e-9)
        assertEquals(3.0, r.getValue("D"), 1e-9)
        assertEquals(4.0, r.getValue("B"), 1e-9)
        // Aufsteigend: der kleinste Wert ist der beste
        assertEquals(1.0, FinderRaster.raenge(mapOf("A" to 30.0, "B" to 10.0), oben = false).getValue("B"), 1e-9)
    }

    @Test
    fun rangsummeNurMitAllenWerten() {
        val rendite = mapOf("A" to 12.0, "B" to 8.0, "C" to 20.0, "OHNE" to 99.0)
        val kapital = mapOf("A" to 40.0, "B" to 10.0, "C" to 25.0)
        val s = FinderRaster.rangsumme(listOf(rendite to true, kapital to true))
        assertEquals(setOf("A", "B", "C"), s.keys)
        // A: Rang 2 + 1, C: 1 + 2, B: 3 + 3. OHNE zaehlt nicht mit und verschiebt keinen Rang
        assertEquals(3.0, s.getValue("A"), 1e-9)
        assertEquals(3.0, s.getValue("C"), 1e-9)
        assertEquals(6.0, s.getValue("B"), 1e-9)
        assertTrue(FinderRaster.rangsumme(emptyList()).isEmpty())
    }

    @Test
    fun schwelleDesAnteils() {
        val werte = (1..10).map { it.toDouble() }
        assertEquals(3.0, FinderRaster.schwelle(werte, 0.3)!!, 1e-9)
        assertEquals(4.0, FinderRaster.schwelle(werte, 0.4)!!, 1e-9)
        assertEquals(8.0, FinderRaster.schwelle(werte, 0.3, oben = true)!!, 1e-9)
        // Aufgerundet: 30 % von sieben Werten sind drei
        assertEquals(3.0, FinderRaster.schwelle((1..7).map { it.toDouble() }, 0.3)!!, 1e-9)
        assertNull(FinderRaster.schwelle(listOf(1.0, 2.0, 3.0, 4.0), 0.5))
    }

    @Test
    fun mindestensNVonM() {
        val ja = Pruefung.JA
        val nein = Pruefung.NEIN
        val offen = Pruefung.OFFEN
        assertEquals(ja, FinderRaster.mindestens(listOf(ja, nein, ja, nein), 2))
        assertEquals(nein, FinderRaster.mindestens(listOf(ja, nein, nein, nein), 2))
        assertEquals(offen, FinderRaster.mindestens(listOf(ja, nein, offen, nein), 2))
        assertEquals(ja, FinderRaster.mindestens(listOf(ja, ja, offen, offen), 2))
    }

    @Test
    fun spitzeHoechsteUndNiedrigste() {
        val w = mapOf("A" to 5.0, "B" to 9.0, "C" to 7.0, "D" to 9.0)
        assertEquals(setOf("B", "D"), FinderRaster.spitze(w, 2, oben = true))
        assertEquals(setOf("A", "C"), FinderRaster.spitze(w, 2, oben = false))
        // Gleichstand: der Schluessel entscheidet
        assertEquals(setOf("B"), FinderRaster.spitze(w, 1, oben = true))
    }

    // ------------------------------------------------------------ Felder aus den Jahresabschluessen

    private fun jahre() = listOf(
        Qualitaet.Jahr("2023-12-31", "USD", nettogewinn = 80.0, umsatz = 1800.0),
        Qualitaet.Jahr("2024-12-31", "USD", nettogewinn = 100.0, umsatz = 1900.0),
        Qualitaet.Jahr(
            "2025-12-31", "USD", nettogewinn = 120.0, umsatz = 2000.0, bilanzsumme = 3000.0, umlaufvermoegen = 1500.0, kurzfristSchulden = 500.0,
            verbindlichkeiten = 900.0, ebit = 200.0, cashflow = 250.0, freierCashflow = 150.0, sachanlagen = 600.0, gesamtschulden = 400.0, kasse = 300.0,
        ),
    )

    @Test
    fun bilanzWerteRechnen() {
        val w = FinderRaster.bilanzWerte(jahre(), boersenwert = 1200.0, jeEuro = 1.25, mitNeuenFeldern = true, jetzt = jetzt)
        assertEquals(12.0, w.getValue(Feld.KGV_SCHNITT3), 1e-9)
        assertEquals(1600.0, w.getValue(Feld.UMSATZ), 1e-9)
        assertEquals(6.0, w.getValue(Feld.MARGE), 1e-9)
        assertEquals(0.6, w.getValue(Feld.KUV), 1e-9)
        assertEquals(50.0, w.getValue(Feld.NETTO_UMLAUF), 1e-9)
        assertEquals(4.8, w.getValue(Feld.KCF), 1e-9)
        assertEquals(8.0, w.getValue(Feld.KFCF), 1e-9)
        // EBIT 200 durch Unternehmenswert 1200 + 400 - 300 und durch Nettoumlaufvermoegen 1000 plus Sachanlagen 600
        assertEquals(200.0 / 1300.0 * 100.0, w.getValue(Feld.GEWINNRENDITE), 1e-9)
        assertEquals(12.5, w.getValue(Feld.KAPITALRENDITE), 1e-9)
    }

    @Test
    fun bilanzWerteOhneGrundlageFehlen() {
        // Abruf ohne die neuen Felder: keine Renditen der Zauberformel, der Rest steht
        val alt = FinderRaster.bilanzWerte(jahre(), 1200.0, 1.25, mitNeuenFeldern = false, jetzt = jetzt)
        assertNull(alt[Feld.GEWINNRENDITE])
        assertNull(alt[Feld.KAPITALRENDITE])
        assertNotNull(alt[Feld.KUV])
        // Ohne Kasse und ohne Sachanlagen wird nichts geschaetzt; fehlende Schulden zaehlen als null
        val j = jahre()
        val ohne = FinderRaster.bilanzWerte(j.dropLast(1) + j.last().copy(kasse = null, sachanlagen = null), 1200.0, 1.25, true, jetzt)
        assertNull(ohne[Feld.GEWINNRENDITE])
        assertNull(ohne[Feld.KAPITALRENDITE])
        val schuldenfrei = FinderRaster.bilanzWerte(j.dropLast(1) + j.last().copy(gesamtschulden = null), 1200.0, 1.25, true, jetzt)
        assertEquals(200.0 / 900.0 * 100.0, schuldenfrei.getValue(Feld.GEWINNRENDITE), 1e-9)
        // Verlust im Schnitt, nur zwei Jahre oder eine Luecke in der Reihe: kein KGV auf den Schnitt
        assertNull(FinderRaster.bilanzWerte(j.map { it.copy(nettogewinn = -50.0) }, 1200.0, 1.25, true, jetzt)[Feld.KGV_SCHNITT3])
        assertNull(FinderRaster.bilanzWerte(j.takeLast(2), 1200.0, 1.25, true, jetzt)[Feld.KGV_SCHNITT3])
        assertNull(FinderRaster.bilanzWerte(listOf(j[0].copy(ende = "2021-12-31"), j[1], j[2]), 1200.0, 1.25, true, jetzt)[Feld.KGV_SCHNITT3])
        // Ohne Boersenwert nur, was ihn nicht braucht; ein altes letztes Jahr gibt nichts her
        val ohneWert = FinderRaster.bilanzWerte(j, null, 1.25, true, jetzt)
        assertEquals(setOf(Feld.UMSATZ, Feld.MARGE, Feld.KAPITALRENDITE), ohneWert.keys)
        assertTrue(FinderRaster.bilanzWerte(j, 1200.0, 1.25, true, jetzt + 900L * 86_400_000L).isEmpty())
        assertTrue(FinderRaster.bilanzWerte(emptyList(), 1200.0, 1.25, true, jetzt).isEmpty())
    }

    @Test
    fun abschlussLiestNeueFelder() {
        fun reihe(typ: String, wert: Double) =
            """{"meta":{"type":["$typ"]},"$typ":[{"asOfDate":"2025-12-31","currencyCode":"USD","reportedValue":{"raw":$wert}}]}"""
        val text = """{"timeseries":{"result":[""" + listOf(
            reihe("annualNetPPE", 600.0), reihe("annualTotalDebt", 400.0), reihe("annualCashAndCashEquivalents", 250.0),
            reihe("annualCashCashEquivalentsAndShortTermInvestments", 300.0),
        ).joinToString(",") + "]}}"
        val j = Qualitaet.lesen(text).single()
        assertEquals(600.0, j.sachanlagen!!, 1e-9)
        assertEquals(400.0, j.gesamtschulden!!, 1e-9)
        // Kasse samt kurzfristigen Anlagen geht vor
        assertEquals(300.0, j.kasse!!, 1e-9)
        listOf("annualNetPPE", "annualTotalDebt", "annualCashCashEquivalentsAndShortTermInvestments", "annualCashAndCashEquivalents")
            .forEach { assertTrue(it, it in Qualitaet.FELDER) }
    }

    @Test
    fun alterEintragGiltWeiterUndWirdNachgeholt() {
        val alt = FinderBilanz.Eintrag(jetzt - 86_400_000L, jahre())
        assertTrue(FinderBilanz.gueltig(alt, jetzt))
        assertFalse(FinderBilanz.aktuell(alt, jetzt))
        assertTrue(FinderBilanz.aktuell(FinderBilanz.eintrag(jahre(), jetzt), jetzt))
        // Ohne Jahre gibt es nichts nachzuholen
        assertTrue(FinderBilanz.aktuell(FinderBilanz.Eintrag(jetzt - 86_400_000L), jetzt))
        // Der Feldstand ueberlebt Schreiben und Lesen
        val zurueck = FinderBilanz.lesen(FinderBilanz.schreiben(mapOf("A" to FinderBilanz.eintrag(jahre(), jetzt), "B" to alt), jetzt))
        assertEquals(FinderBilanz.FELDSTAND, zurueck.getValue("A").f)
        assertEquals(0, zurueck.getValue("B").f)
    }

    // ------------------------------------------------------------ Vorlagen

    @Test
    fun grahamDefensiv() {
        fun w(kgv3: Double, umsatz: Double) = mapOf(Feld.KGV_SCHNITT3 to kgv3, Feld.UMSATZ to umsatz)
        val liste = listOf(
            d("JA", werte = w(12.0, 2e9), kbv = 1.4),
            // KGV und KBV je in der Grenze, das Produkt 15 mal 1,5 = 22,5 gerade noch
            d("RAND", werte = w(15.0, 2e9), kbv = 1.5),
            d("PRODUKT", werte = w(14.0, 2e9), kbv = 1.7),
            d("KLEIN", werte = w(10.0, 5e8), kbv = 1.0),
            d("TEUER", werte = w(18.0, 2e9), kbv = 1.0),
            d("OFFEN", kbv = 1.0, mitAbschluss = false),
            d("BANK", branche = "BANKEN", kbv = 0.8),
        )
        val p = pruefen(liste, "GRAHAM")
        assertEquals(Pruefung.JA, p["JA"])
        assertEquals(Pruefung.JA, p["RAND"])
        assertEquals(Pruefung.NEIN, p["PRODUKT"])
        assertEquals(Pruefung.NEIN, p["KLEIN"])
        assertEquals(Pruefung.NEIN, p["TEUER"])
        assertEquals(Pruefung.OFFEN, p["OFFEN"])
        // Fuer Banken gibt es keine Jahresabschluesse zu laden: nicht offen, sondern ausgeschieden
        assertEquals(Pruefung.NEIN, p["BANK"])
        assertEquals(16.8, FinderVorlagen.wert(liste[0], Feld.GRAHAM_PRODUKT)!!, 1e-9)
    }

    @Test
    fun netNets() {
        val p = pruefen(listOf(d("JA", werte = mapOf(Feld.NETTO_UMLAUF to 130.0)), d("NEIN", werte = mapOf(Feld.NETTO_UMLAUF to 80.0)), d("OHNE")), "NETNETS")
        assertEquals(Pruefung.JA, p["JA"])
        assertEquals(Pruefung.NEIN, p["NEIN"])
        assertEquals(Pruefung.NEIN, p["OHNE"])
    }

    @Test
    fun zauberformelOrdnetNachRangsumme() {
        fun w(rendite: Double, kapital: Double) = mapOf(Feld.GEWINNRENDITE to rendite, Feld.KAPITALRENDITE to kapital)
        val liste = listOf(
            d("A", werte = w(12.0, 40.0)), d("B", werte = w(8.0, 10.0)), d("C", werte = w(20.0, 60.0)), d("D", werte = w(15.0, 15.0)),
            d("VERLUST", werte = w(-3.0, -5.0)),
            d("BANK", branche = "BANKEN", werte = w(50.0, 90.0)), d("STROM", branche = "VERSORGER", werte = w(50.0, 90.0)),
            // Branche des Katalogs passt, der Sektor von Yahoo nennt einen Finanzwert bzw. Versorger
            d("FINANZ", branche = "HOLDINGS", sektor = "Financial Services", werte = w(50.0, 90.0)),
            d("WASSER", branche = "UMWELT", sektor = "Utilities", werte = w(50.0, 90.0)),
            d("OFFEN", mitAbschluss = false),
        )
        val z = v("ZAUBERFORMEL")
        assertEquals(2, z.rang.size)
        val f = Aktienfinder.Filter(grenzen = z.grenzen, vorlage = z.id)
        val a = Aktienfinder.auswahlGeprueft(Aktienfinder.bewerten(liste), f, Aktienfinder.Sortierung.Rang(z.rang), jetzt)
        // C: 1 + 1, A: 3 + 2, D: 2 + 3, B: 4 + 4; A vor D bei gleicher Summe nach dem Kuerzel. Ungepruefte ans Ende
        assertEquals(listOf("C", "A", "D", "B", "OFFEN"), a.liste.map { it.symbol })
        listOf("VERLUST", "BANK", "STROM", "FINANZ", "WASSER").forEach { assertEquals(it, Pruefung.NEIN, a.pruefung[it]) }
        assertEquals(Pruefung.OFFEN, a.pruefung["OFFEN"])
        // Der Export nimmt nur sicher passende Werte
        assertEquals(listOf("C", "A", "D", "B"), FinderExport.treffer(a.liste, a.pruefung).map { it.symbol })
    }

    @Test
    fun galleaZweiVonVier() {
        fun w(hoch: Double, kfcf: Double? = null, kuv: Double? = null) =
            listOfNotNull(Feld.HOCH52 to hoch, kfcf?.let { Feld.KFCF to it }, kuv?.let { Feld.KUV to it }).toMap()
        val liste = listOf(
            // KGV und KUV guenstig
            d("ZWEI", insider = 20, kgv = 9.0, kbv = 2.0, werte = w(-60.0, kfcf = 30.0, kuv = 0.7)),
            // Nur das KBV
            d("EINS", insider = 20, kgv = 25.0, kbv = 0.8, werte = w(-60.0, kfcf = 30.0, kuv = 3.0)),
            // Eins erfuellt, die Jahresabschluesse fehlen noch: offen
            d("WARTET", insider = 20, kgv = 9.0, kbv = 2.0, werte = w(-60.0), mitAbschluss = false),
            // Zwei aus dem Sammelabruf erfuellt: sicher, auch ohne Jahresabschluesse
            d("SCHON", insider = 20, kgv = 9.0, kbv = 0.8, werte = w(-60.0), mitAbschluss = false),
            d("VERKAUF", insider = -10, kgv = 9.0, kbv = 0.8, werte = w(-60.0)),
            d("KEINE", kgv = 9.0, kbv = 0.8, werte = w(-60.0)),
            d("HOCH", insider = 20, kgv = 9.0, kbv = 0.8, werte = w(-30.0)),
            d("KLEIN", insider = 20, kgv = 9.0, kbv = 0.8, werte = w(-60.0), boersenwert = 1e8),
        )
        val p = pruefen(liste, "GALLEA")
        assertEquals(Pruefung.JA, p["ZWEI"])
        assertEquals(Pruefung.NEIN, p["EINS"])
        assertEquals(Pruefung.OFFEN, p["WARTET"])
        assertEquals(Pruefung.JA, p["SCHON"])
        listOf("VERKAUF", "KEINE", "HOCH", "KLEIN").forEach { assertEquals(it, Pruefung.NEIN, p[it]) }
        // Eine abgewaehlte Wahlgrenze: zwei der uebrigen drei
        val g = v("GALLEA").grenzen.filter { !(it.wahl && it.feld == Feld.KUV) }
        assertEquals(Pruefung.NEIN, FinderRaster.pruefen(liste, g, v("GALLEA"), jetzt)["ZWEI"])
    }

    @Test
    fun anteilsgrenzenDreman() {
        // Zehn Werte mit KGV 10, KBV 1 bis 10 und Kurs-Cashflow 10 bis 1: je die niedrigsten vier
        val liste = (1..10).map { i -> d("W$i", kgv = 10.0, kbv = i.toDouble(), werte = mapOf(Feld.KCF to (11 - i).toDouble())) } +
            d("TEUER", kgv = 20.0, kbv = 0.5, werte = mapOf(Feld.KCF to 0.5))
        val nurKbv = FinderRaster.pruefen(liste, v("DREMAN").grenzen.filter { it.feld != Feld.KCF }, v("DREMAN"), jetzt)
        // TEUER zaehlt in der Schwelle mit (KBV 0,5), scheidet aber am KGV aus: elf Werte, 40 % sind fuenf, also bis KBV 4
        assertEquals(listOf("W1", "W2", "W3", "W4"), liste.filter { nurKbv[it.symbol] == Pruefung.JA }.map { it.symbol })
        // Beide Anteile zusammen schliessen sich hier aus
        assertTrue(pruefen(liste, "DREMAN").values.none { it == Pruefung.JA })
        // Zu wenige Werte fuer einen Anteil: ausgeschieden
        assertTrue(pruefen(liste.take(3), "DREMAN").values.all { it == Pruefung.NEIN })
        // Fehlen die Jahresabschluesse noch, bleiben die Werte offen, die das KBV schon bestehen
        val wartend = pruefen(liste.take(10).map { it.copy(mitAbschluss = false, werte = emptyMap()) }, "DREMAN")
        assertEquals(listOf("W1", "W2", "W3", "W4"), liste.filter { wartend[it.symbol] == Pruefung.OFFEN }.map { it.symbol })
        assertEquals(6, wartend.values.count { it == Pruefung.NEIN })
    }

    @Test
    fun oShaughnessyUndNeff() {
        val liste = (1..10).map { i -> d("W$i", werte = mapOf(Feld.KUV to i * 0.5), boersenwert = if (i == 2) 5e8 else 3e9) }
        val p = pruefen(liste, "OSHAUGHNESSY")
        // Die niedrigsten 30 % sind W1 bis W3, W2 ist zu klein
        assertEquals(listOf("W1", "W3"), liste.filter { p[it.symbol] == Pruefung.JA }.map { it.symbol })
        val neff = (1..10).map { i ->
            d("N$i", kgv = 5.0 + i, dividende = if (i == 1) 1.0 else 3.0, mitDetails = true, werte = mapOf(Feld.GEWINN_FUENF to if (i == 3) 4.0 else 9.0))
        }
        val pn = pruefen(neff, "NEFF")
        // KGV unter den niedrigsten 40 %: N1 bis N4; N1 zahlt zu wenig Dividende, N3 waechst zu langsam
        assertEquals(listOf("N2", "N4"), neff.filter { pn[it.symbol] == Pruefung.JA }.map { it.symbol })
        // Ohne Profil ist das Wachstum noch offen
        assertEquals(Pruefung.OFFEN, pruefen(neff.map { it.copy(mitDetails = false, werte = emptyMap()) }, "NEFF")["N2"])
    }

    @Test
    fun fisher() {
        fun w(kuv: Double, marge: Double, wachstum: Double) = mapOf(Feld.KUV to kuv, Feld.MARGE to marge, Feld.GEWINN_FUENF to wachstum)
        val liste = listOf(
            d("JA", werte = w(1.2, 8.0, 18.0), freierCashflow = 5e7, mitDetails = true),
            d("TEUER", werte = w(2.0, 8.0, 18.0), freierCashflow = 5e7, mitDetails = true),
            d("MARGE", werte = w(1.2, 3.0, 18.0), freierCashflow = 5e7, mitDetails = true),
            d("LANGSAM", werte = w(1.2, 8.0, 10.0), freierCashflow = 5e7, mitDetails = true),
            d("ABFLUSS", werte = w(1.2, 8.0, 18.0), freierCashflow = -5e7, mitDetails = true),
        )
        val p = pruefen(liste, "FISHER")
        assertEquals(listOf("JA"), liste.filter { p[it.symbol] == Pruefung.JA }.map { it.symbol })
    }

    @Test
    fun dividendenHunde() {
        // Zwoelf Werte: Rendite 1 bis 12 %, Kurs 120 bis 10. Die zehn hoechsten Renditen sind W3 bis W12, davon die fuenf
        // niedrigsten Kurse W8 bis W12
        val liste = (1..12).map { i -> d("W$i", dividende = i.toDouble(), werte = mapOf(Feld.KURS to (130 - i * 10).toDouble())) } +
            d("OHNE", dividende = null, kgv = 10.0, werte = mapOf(Feld.KURS to 1.0))
        val h = v("DIVIDENDEN_HUNDE")
        val a = Aktienfinder.auswahlGeprueft(
            Aktienfinder.bewerten(liste), Aktienfinder.Filter(grenzen = h.grenzen, vorlage = h.id),
            Aktienfinder.Sortierung.NachFeld(h.sortierung!!, h.aufsteigend), jetzt,
        )
        assertEquals(listOf("W12", "W11", "W10", "W9", "W8"), a.liste.map { it.symbol })
        // Nur die erste Stufe: die zehn hoechsten Renditen
        val zehn = FinderRaster.pruefen(liste, h.grenzen.take(1), h, jetzt)
        assertEquals(10, zehn.values.count { it == Pruefung.JA })
        assertEquals(Pruefung.NEIN, zehn["W2"])
        assertEquals(Pruefung.NEIN, zehn["OHNE"])
        // Ein Wert ohne geladene Kennzahlen zaehlt nicht mit, sobald die Spitze voll ist: es bleiben genau fuenf
        val mitOffen = liste + d("OFFEN", dividende = null, werte = mapOf(Feld.KURS to 2.0)).copy(mitSammel = false)
        val p = pruefen(mitOffen, "DIVIDENDEN_HUNDE")
        assertEquals(Pruefung.NEIN, p["OFFEN"])
        assertEquals(5, p.values.count { it != Pruefung.NEIN })
    }

    @Test
    fun festeGrenzenWieBisher() {
        // Ohne Vorlage und ohne neue Regeln entscheidet FinderRaster wie FinderVorlagen.pruefen
        val g = listOf(Grenze(Feld.KURS, 5.0), Grenze(Feld.LEER, max = 20.0), Grenze(Feld.RSI, max = 30.0))
        val liste = listOf(
            Aktienfinder.Daten("A", "A", "US", werte = mapOf(Feld.KURS to 10.0)),
            Aktienfinder.Daten("B", "B", "US", mitDetails = true, mitKerzen = true, werte = mapOf(Feld.KURS to 10.0, Feld.RSI to 25.0)),
            Aktienfinder.Daten("C", "C", "US", mitDetails = true, leer = 30.0, werte = mapOf(Feld.KURS to 10.0)),
            Aktienfinder.Daten("D", "D", "US", werte = mapOf(Feld.KURS to 2.0)),
        )
        val p = FinderRaster.pruefen(liste, g, null, jetzt)
        liste.forEach { assertEquals(it.symbol, FinderVorlagen.pruefen(it, g, jetzt), p[it.symbol]) }
    }

    @Test
    fun neueVorlagenMitTexten() {
        val ids = listOf("GRAHAM", "NETNETS", "ZAUBERFORMEL", "GALLEA", "NEFF", "FISHER", "DREMAN", "OSHAUGHNESSY", "DIVIDENDEN_HUNDE")
        assertEquals(ids, FinderVorlagen.VORLAGEN.filter { it.nummer in 32..40 }.map { it.id })
        for (id in ids) for (s in listOf(de.troetenrepublik.app.i18n.De, de.troetenrepublik.app.i18n.En)) {
            val vorlage = v(id)
            assertTrue(id, s.afVorlage(id) != id)
            assertTrue(id, s.afVorlageIdee(id).isNotBlank())
            if (vorlage.unvollstaendig) assertNotNull(id, s.afVorlageFehlt(id))
            vorlage.grenzen.forEach { assertTrue(it.feld.name, s.afFeld(it.feld.name).isNotBlank()) }
            // Jedes neue Feld hat eine Bezeichnung (KUV heisst deutsch wie sein Schluessel)
            Feld.entries.filter { it.ordinal >= Feld.INSIDER.ordinal && it != Feld.KUV }.forEach { assertTrue(it.name, s.afFeld(it.name) != it.name) }
            // Keine Gedankenstriche und keine Auslassungspunkte in den neuen Texten
            listOfNotNull(s.afVorlage(id), s.afVorlageIdee(id), s.afVorlageFehlt(id)).forEach { t ->
                assertFalse(t, t.contains('–') || t.contains('—') || t.contains('…'))
            }
        }
        assertEquals(2, v("GALLEA").mindestens)
        assertEquals(4, v("GALLEA").grenzen.count { it.wahl })
        // Felder der neuen Vorlagen kommen ohne Einzelabruf fuer jeden Wert aus, bis auf Wachstum und freien Cashflow
        assertEquals(emptySet<FinderVorlagen.Stufe>(), FinderVorlagen.stufen(v("DIVIDENDEN_HUNDE").grenzen))
        assertEquals(setOf(FinderVorlagen.Stufe.BILANZ), FinderVorlagen.stufen(v("ZAUBERFORMEL").grenzen))
    }
}
