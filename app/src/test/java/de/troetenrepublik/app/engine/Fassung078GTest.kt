package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Lernseiten „Wirkt das?“, IPO-Statistik und neue Finder-Vorlagen mit CSV [0.76]; erfundene Werte, ohne Netz.
class Fassung078GTest {

    private val TAG = 86_400_000L
    private val JETZT = 1_800_000_000_000L

    private fun datei(pfad: String): String {
        val kandidaten = listOf(File("src/main/assets/$pfad"), File("app/src/main/assets/$pfad"), File("../app/src/main/assets/$pfad"))
        return kandidaten.first { it.exists() }.readText()
    }

    // ------------------------------------------------------------ Insider-Cluster und IPO (gebuendelte Dateien)

    @Test
    fun clusterDateiLesbar() {
        val c = WirktDas.clusterLesen(datei("copytrading/cluster.json"))
        assertNotNull(c)
        c!!
        assertEquals(60, c.halten)
        assertTrue(c.ausgewertet > 100)
        assertEquals(c.ausgewertet, c.jahre.sumOf { it.n })
        // Die Kurve beginnt bei null und endet bei der Rendite nach der Haltedauer
        assertEquals(0.0, c.kurve.first()[1], 1e-9)
        assertEquals(c.halten.toDouble(), c.kurve.last()[0], 1e-9)
        assertEquals(c.rendite, c.kurve.last()[1], 1e-3)
        assertEquals(c.index, c.kurve.last()[2], 1e-3)
        assertTrue(c.treffer in 0.0..1.0)
    }

    @Test
    fun clusterKaputtGibtNull() {
        assertNull(WirktDas.clusterLesen("{}"))
        assertNull(WirktDas.clusterLesen("kein json"))
    }

    @Test
    fun ipoDateiLesbarUndGesamt() {
        val i = WirktDas.ipoLesen(datei("lernen/ipo.json"))
        assertNotNull(i)
        i!!
        val g = WirktDas.ipoGesamt(i)
        assertEquals(1980, g.von)
        assertEquals(2025, g.bis)
        // Ritter nennt fuer 1980 bis 2025 9.343 Boersengaenge
        assertEquals(9343, g.n)
        // Mittel der ersten Tage rund 19 %, Anteil mit Verlust am ersten Tag rund 16,5 %
        assertEquals(19.0, g.t1, 0.6)
        assertEquals(16.5, g.neg, 0.6)
        // 1999: 476 Boersengaenge, 71,2 % am ersten Tag
        val j99 = i.jahre.first { it.j == 1999 }
        assertEquals(476, j99.n)
        assertEquals(71.2, j99.t1, 1e-9)
        assertNull(i.jahre.first { it.j == 2025 }.m3)
    }

    @Test
    fun ipoGesamtGewichtetNachZahl() {
        val i = WirktDas.Ipo("", listOf(
            WirktDas.IpoJahr(2000, 100, 10.0, 10.0, m3 = -20.0),
            WirktDas.IpoJahr(2001, 300, 20.0, 30.0, m3 = 10.0),
            WirktDas.IpoJahr(2002, 50, 0.0, 0.0),
        ))
        val g = WirktDas.ipoGesamt(i)
        assertEquals(450, g.n)
        assertEquals((1000.0 + 6000.0) / 450, g.t1, 1e-9)
        assertEquals((1000.0 + 9000.0) / 450, g.neg, 1e-9)
        assertEquals((-2000.0 + 3000.0) / 400, g.m3!!, 1e-9)
        assertEquals(0.5, g.m3Anteil!!, 1e-9)
    }

    // ------------------------------------------------------------ Stillhalter-Protokoll

    private fun kontrakt(strike: Double, geld: Double, verfallS: Long, iv: Double = 0.4) =
        Optionsanalyse.Kontrakt(false, strike, verfallS, geld, geld, geld + 0.05, 10, 100, iv)

    @Test
    fun besterPutNachJahresrendite() {
        val jetzt = JETZT
        val verfall = jetzt / 1000 + 30 * 86_400L
        // Kurs 100: der Put bei 90 bringt 1,0 (1,11 %), der bei 95 bringt 2,0 (2,1 %)
        val p = WirktDas.besterPut("AAA", 100.0, listOf(kontrakt(90.0, 1.0, verfall), kontrakt(95.0, 2.0, verfall)), jetzt)!!
        assertEquals(95.0, p.strike, 1e-9)
        assertEquals(2.0, p.praemie, 1e-9)
        assertEquals(verfall, p.verfall)
        assertEquals(2.0 / 95.0 * 365 / 30, p.jahr, 1e-9)
        assertNull(WirktDas.besterPut("AAA", 100.0, emptyList(), jetzt))
    }

    @Test
    fun nominalGegenRealisiert() {
        val aufnahme = JETZT
        val verfall = aufnahme / 1000 + 30 * 86_400L
        fun put(s: String, strike: Double, praemie: Double, schluss: Double?) = WirktDas.Put(s, aufnahme, verfall, strike, praemie, strike * 1.05, praemie / strike * 365 / 30, schluss)
        // A verfaellt wertlos, B wird bei 80 ausgeuebt (Verlust 20 minus 2 Praemie), C offen
        val liste = listOf(put("A", 100.0, 2.0, 110.0), put("B", 100.0, 2.0, 80.0), put("C", 100.0, 2.0, null))
        assertEquals(0.02, liste[0].realisiert!!, 1e-9)
        assertEquals(-0.18, liste[1].realisiert!!, 1e-9)
        assertNull(liste[2].realisiert)
        val b = WirktDas.bilanz(liste)
        assertEquals(3, b.gesamt)
        assertEquals(1, b.offen)
        assertEquals(2, b.fertig)
        assertEquals(0.02, b.nominal!!, 1e-9)
        assertEquals((0.02 - 0.18) / 2, b.realisiert!!, 1e-9)
        assertEquals(0.5, b.verlustAnteil!!, 1e-9)
        assertEquals(-0.18, b.schlechtester!!, 1e-9)
        // aufs Jahr: 30 Tage
        assertEquals(0.02 * 365 / 30, b.nominalJahr!!, 1e-9)
        assertEquals((0.02 - 0.18) / 2 * 365 / 30, b.realisiertJahr!!, 1e-9)
        assertNull(WirktDas.bilanz(listOf(liste[2])).realisiertJahr)
    }

    @Test
    fun faelligErstNachDemVerfallstag() {
        val verfall = JETZT / 1000
        val p = WirktDas.Put("A", JETZT - 30 * TAG, verfall, 100.0, 2.0, 105.0, 0.3)
        assertTrue(!WirktDas.faellig(p, JETZT + 10 * 3_600_000L))
        assertTrue(WirktDas.faellig(p, JETZT + 40 * 3_600_000L))
        assertTrue(!WirktDas.faellig(p.copy(schluss = 90.0), JETZT + 40 * 3_600_000L))
    }

    @Test
    fun auswahlOhneDoppelteUndBegrenzt() {
        val v = JETZT / 1000 + 30 * 86_400L
        fun p(s: String, jahr: Double, strike: Double = 100.0) = WirktDas.Put(s, JETZT, v, strike, 1.0, 105.0, jahr)
        val vorhanden = listOf(p("A", 0.5))
        val kandidaten = listOf(p("A", 0.9), p("B", 0.8), p("C", 0.7), p("D", 0.6), p("E", 0.5), p("F", 0.4), p("G", 0.3), p("A", 0.2, strike = 95.0))
        val neu = WirktDas.auswaehlen(kandidaten, vorhanden)
        assertEquals(listOf("B", "C", "D", "E", "F"), neu.map { it.s })
        // anderer Basispreis derselben Aktie ist ein neuer Eintrag
        assertTrue(WirktDas.auswaehlen(listOf(p("A", 0.2, strike = 95.0)), vorhanden).isNotEmpty())
    }

    @Test
    fun protokollRundlauf() {
        val v = JETZT / 1000 + 30 * 86_400L
        val l = listOf(WirktDas.Put("A", JETZT, v, 100.0, 1.0, 105.0, 0.3), WirktDas.Put("B", JETZT - TAG, v, 50.0, 0.5, 52.0, 0.2, schluss = 51.0))
        val text = WirktDas.protokollSchreiben(l)
        val zurueck = WirktDas.protokollLesen(text)!!
        assertEquals(l.sortedBy { it.am }, zurueck)
        assertNull(WirktDas.protokollLesen("kaputt"))
        assertTrue(WirktDas.neuAufnehmen(emptyList(), JETZT))
        assertTrue(!WirktDas.neuAufnehmen(l, JETZT + 3_600_000L))
        assertTrue(WirktDas.neuAufnehmen(l, JETZT + 21 * 3_600_000L))
    }

    // ------------------------------------------------------------ Kongress kopieren

    @Test
    fun kongressAbstandInPunkten() {
        fun s(a: Double?, b: Double?) = KongressAuswertung.Strategie(emptyList(), emptyList(), emptyList(), a, b, null, 0.0)
        assertEquals(-3.1, WirktDas.kongressAbstand(s(0.10, 0.131))!!, 1e-9)
        assertNull(WirktDas.kongressAbstand(s(null, 0.1)))
        assertNull(WirktDas.kongressAbstand(s(0.1, null)))
    }

    // ------------------------------------------------------------ Finder-Vorlagen

    @Test
    fun neueVorlagenVollstaendig() {
        val ids = listOf("NETTOKASSE", "RUECKKAEUFE", "FCF_RENDITE", "KGV_ROE", "SOLIDE_DIVIDENDE", "RSI_UEBERKAUFT", "RSI_UEBERVERKAUFT", "MEISTGESHORTET")
        ids.forEach { id ->
            val v = FinderVorlagen.vorlage(id)
            assertNotNull(id, v)
            assertTrue(id, v!!.grenzen.isNotEmpty())
            assertTrue(id, de.troetenrepublik.app.i18n.De.afVorlage(id) != id)
            assertTrue(id, de.troetenrepublik.app.i18n.En.afVorlage(id) != id)
            assertTrue(id, de.troetenrepublik.app.i18n.De.afVorlageIdee(id).isNotBlank())
            assertTrue(id, de.troetenrepublik.app.i18n.En.afVorlageIdee(id).isNotBlank())
            v.grenzen.forEach { g ->
                assertTrue(g.feld.name, de.troetenrepublik.app.i18n.De.afFeld(g.feld.name).isNotBlank())
                assertTrue(g.feld.name, de.troetenrepublik.app.i18n.En.afFeld(g.feld.name).isNotBlank())
            }
        }
    }

    @Test
    fun neueFelderHabenBezeichnung() {
        listOf(Feld.NETTOKASSE, Feld.FCF_RENDITE, Feld.RUECKKAUF_RENDITE).forEach { f ->
            assertTrue(f.name, de.troetenrepublik.app.i18n.De.afFeld(f.name) != f.name)
            assertTrue(f.name, de.troetenrepublik.app.i18n.En.afFeld(f.name) != f.name)
        }
    }

    @Test
    fun abgeleiteteFelder() {
        val d = Aktienfinder.Daten(
            "X", "X", "US", boersenwert = 2e9, kasse = 500e6, schulden = 200e6, freierCashflow = 160e6, mitDetails = true,
            werte = mapOf(Feld.RUECKKAUF_BETRAG to 60e6),
        )
        assertEquals(15.0, FinderVorlagen.wert(d, Feld.NETTOKASSE)!!, 1e-9)
        assertEquals(8.0, FinderVorlagen.wert(d, Feld.FCF_RENDITE)!!, 1e-9)
        assertEquals(3.0, FinderVorlagen.wert(d, Feld.RUECKKAUF_RENDITE)!!, 1e-9)
        val netto = FinderVorlagen.vorlage("NETTOKASSE")!!
        val fcf = FinderVorlagen.vorlage("FCF_RENDITE")!!
        val rueck = FinderVorlagen.vorlage("RUECKKAEUFE")!!
        assertEquals(FinderVorlagen.Pruefung.JA, FinderVorlagen.pruefen(d, netto.grenzen))
        assertEquals(FinderVorlagen.Pruefung.JA, FinderVorlagen.pruefen(d, fcf.grenzen))
        assertEquals(FinderVorlagen.Pruefung.JA, FinderVorlagen.pruefen(d, rueck.grenzen))
        // Ohne Rueckkauf trotz geladenem Profil: nicht erfuellt; Schulden ueber der Kasse: keine Nettokasse
        assertEquals(FinderVorlagen.Pruefung.NEIN, FinderVorlagen.pruefen(d.copy(werte = emptyMap()), rueck.grenzen))
        assertEquals(FinderVorlagen.Pruefung.NEIN, FinderVorlagen.pruefen(d.copy(schulden = 900e6), netto.grenzen))
        // Ohne Boersenwert keine Rendite
        assertNull(FinderVorlagen.wert(d.copy(boersenwert = null), Feld.FCF_RENDITE))
    }

    @Test
    fun rueckkaufAusZeitreihe() {
        fun antwort(laufend: String?, jahr: String?): String {
            fun teil(typ: String, d: String, v: Double) =
                """{"meta":{"symbol":["A"],"type":["$typ"]},"$typ":[{"asOfDate":"$d","currencyCode":"USD","reportedValue":{"raw":$v}}]}"""
            val l = listOfNotNull(laufend?.let { teil("trailingRepurchaseOfCapitalStock", "2026-06-30", it.toDouble()) }, jahr?.let { teil("annualRepurchaseOfCapitalStock", "2025-09-30", it.toDouble()) })
            return """{"timeseries":{"result":[${l.joinToString(",")}]}}"""
        }
        // Auszahlungen stehen negativ; der Wert der letzten zwoelf Monate geht vor
        assertEquals(95.0, FinderVorlagen.rueckkaufLesen(antwort("-95", "-90"))!!, 1e-9)
        assertEquals(90.0, FinderVorlagen.rueckkaufLesen(antwort(null, "-90"))!!, 1e-9)
        // Ausgabe neuer Aktien (positiv) ist kein Rueckkauf
        assertNull(FinderVorlagen.rueckkaufLesen(antwort("5", null)))
        assertNull(FinderVorlagen.rueckkaufLesen("""{"timeseries":{"result":[]}}"""))
        assertNull(FinderVorlagen.rueckkaufLesen("kaputt"))
    }

    @Test
    fun csvMitFeldernUndAnfuehrungszeichen() {
        val d1 = Aktienfinder.Daten("AAA", "Alpha, \"Inc\"", "US", "Software", kgv = 12.5, boersenwert = 2.5e9, mitDetails = true)
        val d2 = Aktienfinder.Daten("BBB", "Beta", "DE", "Auto", boersenwert = 5e8, mitDetails = true)
        fun e(d: Aktienfinder.Daten, g: Double) = Aktienfinder.Ergebnis(d, g, emptyMap(), Aktienfinder.Einordnung.FAIR, emptyList(), 1.0, emptyMap())
        val grenzen = listOf(FinderVorlagen.Grenze(Feld.BOERSENWERT, 1e9), FinderVorlagen.Grenze(Feld.RSI, max = 30.0))
        val felder = FinderExport.felder(grenzen)
        // Grundfelder zuerst, Felder der Grenzen dahinter, keine doppelt
        assertEquals(FinderExport.GRUNDFELDER + Feld.RSI, felder)
        val csv = FinderExport.csv(listOf(e(d1, 61.234), e(d2, 40.0)), listOf(Feld.BOERSENWERT, Feld.KGV), listOf("Symbol", "Name", "Land", "Branche", "Punkte")) { it.name }
        val zeilen = csv.trimEnd().split("\r\n")
        assertEquals("Symbol,Name,Land,Branche,Punkte,BOERSENWERT,KGV", zeilen[0])
        assertEquals("AAA,\"Alpha, \"\"Inc\"\"\",US,Software,61.23,2500000000,12.5", zeilen[1])
        assertEquals("BBB,Beta,DE,Auto,40,500000000,", zeilen[2])
        // 0.77: deutsch mit Semikolon und Dezimalkomma, Branche mit Anzeigenamen
        val de = FinderExport.csv(listOf(e(d1, 61.234)), listOf(Feld.BOERSENWERT, Feld.KGV), listOf("Symbol", "Name", "Land", "Branche", "Punkte"), deutsch = true, branche = { "Programme" }) { it.name }
        assertEquals("Symbol;Name;Land;Branche;Punkte;BOERSENWERT;KGV\r\nAAA;\"Alpha, \"\"Inc\"\"\";US;Programme;61,23;2500000000;12,5\r\n", de)
    }

    @Test
    fun csvNurSichereTreffer() {
        val sicher = Aktienfinder.Daten("S", "S", "US", boersenwert = 3e9, mitDetails = true)
        val offen = Aktienfinder.Daten("O", "O", "US", boersenwert = 3e9, mitDetails = false)
        val zuKlein = Aktienfinder.Daten("K", "K", "US", boersenwert = 1e8, mitDetails = true)
        fun e(d: Aktienfinder.Daten) = Aktienfinder.Ergebnis(d, 50.0, emptyMap(), Aktienfinder.Einordnung.FAIR, emptyList(), 1.0, emptyMap())
        val grenzen = listOf(FinderVorlagen.Grenze(Feld.BOERSENWERT, 1e9), FinderVorlagen.Grenze(Feld.ROE, 10.0))
        // O wartet noch auf das Profil (ROE fehlt), S hat es geladen, aber ohne ROE: NEIN
        val l = listOf(e(sicher), e(offen), e(zuKlein))
        assertEquals(emptyList<String>(), FinderExport.treffer(l, grenzen, JETZT).map { it.symbol })
        val mit = Aktienfinder.Daten("S", "S", "US", boersenwert = 3e9, mitDetails = true, eigenkapitalRendite = 15.0)
        assertEquals(listOf("S"), FinderExport.treffer(listOf(e(mit), e(offen), e(zuKlein)), grenzen, JETZT).map { it.symbol })
        // Ohne Grenzen alle
        assertEquals(3, FinderExport.treffer(l, emptyList(), JETZT).size)
    }
}
