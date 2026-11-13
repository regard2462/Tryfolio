package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.NetzTestHilfe.HEUTE
import de.troetenrepublik.app.engine.NetzTestHilfe.gw
import de.troetenrepublik.app.engine.NetzTestHilfe.kante
import de.troetenrepublik.app.engine.NetzTestHilfe.netz
import de.troetenrepublik.app.engine.NetzTestHilfe.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Kreislaeufe (Laenge 2 bis 4, zurueck zur Ausgangsfirma, mit Kapital- und Geschaeftskante) und der Ausschnitt des Netzes.
class Fassung079NetzKreislaufTest {

    @Test fun nvidiaOpenAiNvidia() {
        val g = netz(
            kante("N", Netz.Art.HAELT_ANTEIL, "O", usd(30.0)),
            kante("O", Netz.Art.KAUFT, "N", NetzTestHilfe.gw(10.0)),
        )
        val k = NetzKreislauf.suche(g, "N", HEUTE)
        assertEquals(1, k.size)
        assertEquals(listOf("N", "O"), k[0].knoten)
        assertEquals(30e9, k[0].kapitalUsd, 1.0)
        assertEquals(10.0, k[0].handelGw, 1e-9)
        // Von der anderen Seite derselbe Kreis, ueber alle Knoten einmal
        assertEquals(1, NetzKreislauf.alle(g, setOf("N", "O"), HEUTE).size)
    }

    @Test fun liefertRichtungZaehlt() {
        // Lieferung: der Abnehmer zahlt dem Lieferanten. N beteiligt sich an O, N liefert an O: Geld fliesst zu O und zurueck
        val g = netz(kante("N", Netz.Art.HAELT_ANTEIL, "O"), kante("N", Netz.Art.LIEFERT, "O"))
        assertEquals(1, NetzKreislauf.suche(g, "N", HEUTE).size)
        // Umgekehrt: O liefert an N, N beteiligt sich an O: das Geld fliesst beide Male von N zu O, kein Kreis
        val h = netz(kante("N", Netz.Art.HAELT_ANTEIL, "O"), kante("O", Netz.Art.LIEFERT, "N"))
        assertEquals(0, NetzKreislauf.suche(h, "N", HEUTE).size)
    }

    @Test fun dreierKreisUndGrenzeBeiVier() {
        val drei = netz(kante("A", Netz.Art.FINANZIERT, "B"), kante("B", Netz.Art.KAUFT, "C"), kante("C", Netz.Art.KAUFT, "A"))
        assertEquals(listOf("A", "B", "C"), NetzKreislauf.suche(drei, "A", HEUTE).single().knoten)
        val fuenf = netz(
            kante("A", Netz.Art.FINANZIERT, "B"), kante("B", Netz.Art.KAUFT, "C"), kante("C", Netz.Art.KAUFT, "D"),
            kante("D", Netz.Art.KAUFT, "E"), kante("E", Netz.Art.KAUFT, "A"),
        )
        assertEquals(0, NetzKreislauf.suche(fuenf, "A", HEUTE).size)
        assertEquals(1, NetzKreislauf.suche(fuenf, "A", HEUTE, maxLaenge = 5).size)
    }

    @Test fun ohneKapitalOderOhneGeschaeftKeinKreislauf() {
        val nurGeschaeft = netz(kante("A", Netz.Art.KAUFT, "B"), kante("B", Netz.Art.KAUFT, "A"))
        assertEquals(0, NetzKreislauf.suche(nurGeschaeft, "A", HEUTE).size)
        val nurKapital = netz(kante("A", Netz.Art.HAELT_ANTEIL, "B"), kante("B", Netz.Art.HAELT_ANTEIL, "A"))
        assertEquals(0, NetzKreislauf.suche(nurKapital, "A", HEUTE).size)
    }

    @Test fun beendeteTochterWettbewerbMandatUndStaatsgeldZaehlenNicht() {
        val beendet = netz(kante("A", Netz.Art.HAELT_ANTEIL, "B"), kante("B", Netz.Art.KAUFT, "A", status = Netz.Status.BEENDET))
        assertEquals(0, NetzKreislauf.suche(beendet, "A", HEUTE).size)
        // Ein ueberfaelliger Pflegetermin ist kein Vertragsende: der Kreislauf bleibt
        val ueberfaellig = netz(kante("A", Netz.Art.HAELT_ANTEIL, "B"), kante("B", Netz.Art.KAUFT, "A", ablauf = "2026-01-01"))
        assertEquals(1, NetzKreislauf.suche(ueberfaellig, "A", HEUTE).size)
        val sonst = netz(
            kante("A", Netz.Art.TOCHTER, "B"), kante("B", Netz.Art.KONKURRIERT, "A"), kante("B", Netz.Art.MANDAT, "A"),
            kante("B", Netz.Art.STAAT_FINANZIERT, "A"), kante("A", Netz.Art.KAUFT, "B"),
        )
        assertEquals(0, NetzKreislauf.suche(sonst, "A", HEUTE).size)
    }

    @Test fun parallelKantenZaehlenEinmalMitGroesstemFestenBetrag() {
        val g = netz(
            kante("N", Netz.Art.FINANZIERT, "O", usd(100.0, true), Netz.Status.ABSICHT, bedingt = true),
            kante("N", Netz.Art.HAELT_ANTEIL, "O", usd(30.0)),
            kante("O", Netz.Art.KAUFT, "N", usd(5.0)),
        )
        val k = NetzKreislauf.suche(g, "N", HEUTE).single()
        assertEquals(2, k.schritte.first { it.first().von == "N" }.size)
        assertEquals(30e9, k.kapitalUsd, 1.0)
        assertEquals(100e9, k.kapitalBedingtUsd, 1.0)
        assertEquals(5e9, k.handelUsd, 1.0)
        assertFalse(k.angekuendigt)
    }

    @Test fun nurAbsichtserklaerungenSindEinAngekuendigterKreislauf() {
        val g = netz(
            kante("N", Netz.Art.FINANZIERT, "O", usd(100.0, true), Netz.Status.ABSICHT, bedingt = true),
            kante("O", Netz.Art.KAUFT, "N", gw(10.0), Netz.Status.ABSICHT),
        )
        val k = NetzKreislauf.suche(g, "N", HEUTE).single()
        assertTrue(k.angekuendigt)
        assertEquals(0.0, k.kapitalUsd, 0.0)
        assertEquals(100e9, k.kapitalBedingtUsd, 1.0)
        assertEquals(10.0, k.handelGw, 1e-9)
        // Feste Beteiligung, aber die Gegenrichtung nur angekuendigt: ebenfalls angekuendigt
        val h = netz(kante("N", Netz.Art.HAELT_ANTEIL, "O", usd(30.0)), kante("O", Netz.Art.KAUFT, "N", gw(10.0), Netz.Status.ABSICHT))
        assertTrue(NetzKreislauf.suche(h, "N", HEUTE).single().angekuendigt)
        assertEquals(30e9, NetzKreislauf.suche(h, "N", HEUTE).single().kapitalUsd, 1.0)
    }

    @Test fun imBuendelFuehrenNvidiaOpenAiUndAmazonAnthropicInKreise() {
        val g = NetzTestHilfe.buendel.graph
        val nv = NetzKreislauf.suche(g, "NVDA", HEUTE)
        assertTrue(nv.any { it.knoten == listOf("NVDA", "OPENAI") })
        assertTrue(nv.any { it.knoten == listOf("NVDA", "ANTHROPIC") })
        val amzn = NetzKreislauf.suche(g, "AMZN", HEUTE)
        assertTrue(amzn.any { it.knoten == listOf("AMZN", "ANTHROPIC") })
        assertTrue(NetzKreislauf.alle(g, g.knoten.keys, HEUTE).size >= 5)
    }

    // ------------------------------------------------------------ Ausschnitt

    private val stern = netz(
        kante("M", Netz.Art.LIEFERT, "A", usd(1.0)), kante("M", Netz.Art.LIEFERT, "B", usd(5.0)), kante("M", Netz.Art.KONKURRIERT, "C"),
        kante("A", Netz.Art.LIEFERT, "X"), kante("A", Netz.Art.LIEFERT, "Y"), kante("B", Netz.Art.HAELT_ANTEIL, "Z"),
    )

    @Test fun startMitNachbarnWichtigsteZuerst() {
        val a = NetzAusschnitt.start(stern, "M", NetzAusschnitt.ALLE, HEUTE)
        assertEquals(setOf("M", "A", "B", "C"), a.sichtbar)
        assertEquals(listOf("B", "A", "C"), NetzAusschnitt.nachbarn(stern, "M", NetzAusschnitt.ALLE, HEUTE))
        val klein = NetzAusschnitt.start(stern, "M", NetzAusschnitt.ALLE, HEUTE, maxNeu = 2)
        assertEquals(setOf("M", "B", "A"), klein.sichtbar)
        assertEquals(listOf("C"), klein.rest(stern, "M", NetzAusschnitt.ALLE, HEUTE))
    }

    @Test fun erweiternUndZuruecknehmen() {
        val a = NetzAusschnitt.start(stern, "M", NetzAusschnitt.ALLE, HEUTE)
        val b = a.erweitern(stern, "A", NetzAusschnitt.ALLE, HEUTE)
        assertEquals(setOf("M", "A", "B", "C", "X", "Y"), b.sichtbar)
        assertEquals(1, b.schritte.size)
        val c = b.erweitern(stern, "B", NetzAusschnitt.ALLE, HEUTE)
        assertEquals(setOf("M", "A", "B", "C", "X", "Y", "Z"), c.sichtbar)
        // Nichts Neues: derselbe Ausschnitt
        assertTrue(c.erweitern(stern, "B", NetzAusschnitt.ALLE, HEUTE) === c)
        // Unsichtbarer Knoten erweitert nicht
        assertTrue(a.erweitern(stern, "X", NetzAusschnitt.ALLE, HEUTE) === a)
        // Zurueck nimmt die letzte Erweiterung zurueck, dann die davor, dann ist Schluss
        val z1 = c.zurueck()!!
        assertEquals(b.sichtbar, z1.sichtbar)
        val z2 = z1.zurueck()!!
        assertEquals(a.sichtbar, z2.sichtbar)
        assertEquals(null, z2.zurueck())
    }

    @Test fun maxNeuBegrenztUndRestWirdNachgeladen() {
        val kanten = (1..20).map { kante("M", Netz.Art.LIEFERT, "N$it", usd(it.toDouble())) }
        val g = netz(*kanten.toTypedArray())
        val a = NetzAusschnitt.start(g, "M", NetzAusschnitt.ALLE, HEUTE, maxNeu = 5)
        assertEquals(6, a.sichtbar.size)
        val b = a.erweitern(g, "M", NetzAusschnitt.ALLE, HEUTE, maxNeu = 5)
        assertEquals(11, b.sichtbar.size)
        assertEquals(10, b.rest(g, "M", NetzAusschnitt.ALLE, HEUTE).size)
        // Die wichtigsten (groesster Betrag) kamen zuerst
        assertTrue("N20" in a.sichtbar && "N16" in a.sichtbar && "N15" !in a.sichtbar)
    }

    @Test fun filterBeschraenktNachbarn() {
        val nur = setOf(Netz.Gruppe.BETEILIGUNG)
        assertEquals(listOf("Z"), NetzAusschnitt.nachbarn(stern, "B", nur, HEUTE))
        assertEquals(emptyList<String>(), NetzAusschnitt.nachbarn(stern, "M", nur, HEUTE))
        assertEquals(listOf("C"), NetzAusschnitt.nachbarn(stern, "M", setOf(Netz.Gruppe.WETTBEWERB), HEUTE))
    }

    @Test fun ausListeStartetMitMehrerenKnoten() {
        val a = NetzAusschnitt.ausListe("M", listOf("A", "X"))
        assertEquals(setOf("M", "A", "X"), a.sichtbar)
        assertEquals("M", a.start)
    }

    @Test fun beendeteKantenFuehrenNichtZuNachbarn() {
        val g = netz(kante("M", Netz.Art.LIEFERT, "A"), kante("M", Netz.Art.LIEFERT, "B", status = Netz.Status.BEENDET))
        assertEquals(listOf("A"), NetzAusschnitt.nachbarn(g, "M", NetzAusschnitt.ALLE, HEUTE))
    }

    @Test fun zulieferAbnehmerRollen() {
        val g = netz(
            kante("S", Netz.Art.LIEFERT, "F"), kante("F", Netz.Art.KAUFT, "S2"), kante("F", Netz.Art.RECHENZENTRUM, "C"),
            kante("F", Netz.Art.LIEFERT, "K"), kante("K2", Netz.Art.KAUFT, "F"), kante("F", Netz.Art.LIZENZ, "L"), kante("F", Netz.Art.HAELT_ANTEIL, "H"),
        )
        assertEquals(setOf("S", "S2", "C"), g.zulieferer("F").map { it.andere("F") }.toSet())
        assertEquals(setOf("K", "K2"), g.abnehmer("F").map { it.andere("F") }.toSet())
        assertEquals(setOf("L", "H"), g.kapital("F").map { it.andere("F") }.toSet())
    }
}
