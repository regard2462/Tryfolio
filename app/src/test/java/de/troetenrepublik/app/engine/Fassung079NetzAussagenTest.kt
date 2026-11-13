package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.NetzAussagen.Typ
import de.troetenrepublik.app.engine.NetzTestHilfe.HEUTE
import de.troetenrepublik.app.engine.NetzTestHilfe.gw
import de.troetenrepublik.app.engine.NetzTestHilfe.kante
import de.troetenrepublik.app.engine.NetzTestHilfe.netz
import de.troetenrepublik.app.engine.NetzTestHilfe.pct
import de.troetenrepublik.app.engine.NetzTestHilfe.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// "Was das Netz zeigt": je Regel ein kleiner Graph, der sie ausloest, und einer, der sie nicht ausloest.
class Fassung079NetzAussagenTest {

    private fun ableiten(g: Netz.Graph, fokus: String? = null) = NetzAussagen.ableiten(g, g.knoten.keys, HEUTE, fokus)

    private fun typen(g: Netz.Graph, fokus: String? = null) = ableiten(g, fokus).map { it.typ }

    // 1. Kreislauf
    @Test fun kreislaufNenntBetraegeBeiderRichtungen() {
        val g = netz(
            kante("N", Netz.Art.HAELT_ANTEIL, "O", usd(30.0)),
            kante("O", Netz.Art.KAUFT, "N", usd(50.0)),
        )
        val a = ableiten(g, "N").single { it.typ == Typ.KREISLAUF }
        assertEquals(listOf("N", "O"), a.knoten)
        assertEquals(30e9, a.zahl, 1.0)
        assertEquals(50e9, a.zahl2, 1.0)
        assertFalse(a.gemischt)
        assertFalse(a.bedingt)
        val ohne = netz(kante("N", Netz.Art.HAELT_ANTEIL, "O"), kante("N", Netz.Art.HAELT_ANTEIL, "P"))
        assertFalse(Typ.KREISLAUF in typen(ohne))
    }

    @Test fun kreislaufInGwUndGemischteGuete() {
        val g = netz(kante("N", Netz.Art.FINANZIERT, "O", guete = Netz.Guete.B), kante("O", Netz.Art.KAUFT, "N", gw(10.0)))
        val a = ableiten(g).single { it.typ == Typ.KREISLAUF }
        assertEquals(0.0, a.zahl2, 0.0)
        assertEquals(10.0, a.zahl3, 1e-9)
        assertTrue(a.gemischt)
    }

    @Test fun angekuendigterKreislaufIstGekennzeichnetUndDieAussageZuBedingtemBleibt() {
        val g = netz(
            kante("N", Netz.Art.HAELT_ANTEIL, "O", usd(30.0)),
            kante("N", Netz.Art.FINANZIERT, "O", usd(100.0, true), Netz.Status.ABSICHT, bedingt = true),
            kante("O", Netz.Art.KAUFT, "N", gw(10.0), Netz.Status.ABSICHT),
            kante("N", Netz.Art.GARANTIE, "S", usd(105.0, true), bedingt = true),
            kante("O", Netz.Art.KAUFT, "A", usd(1.0)), kante("O", Netz.Art.KAUFT, "B", usd(1.0)), kante("O", Netz.Art.KAUFT, "C", usd(1.0)),
            kante("D", Netz.Art.KAUFT, "O", usd(1.0)), kante("E", Netz.Art.KAUFT, "O", usd(1.0)),
        )
        val a = ableiten(g, "N")
        val kreis = a.single { it.typ == Typ.KREISLAUF }
        assertTrue(kreis.bedingt)
        assertEquals(10.0, kreis.zahl3, 1e-9)
        // Bedingte Betraege stecken im Netz: die Aussage dazu steht immer da
        assertTrue(a.any { it.typ == Typ.BEDINGT })
        // Fester Kreislauf mit zusaetzlich bedingten Betraegen nennt den bedingten Teil
        val fest = netz(
            kante("N", Netz.Art.HAELT_ANTEIL, "O", usd(30.0)), kante("N", Netz.Art.FINANZIERT, "O", usd(100.0, true), Netz.Status.ABSICHT),
            kante("O", Netz.Art.KAUFT, "N", usd(5.0)),
        )
        val f = ableiten(fest, "N").single { it.typ == Typ.KREISLAUF }
        assertFalse(f.bedingt)
        assertEquals(100e9, f.zahl4, 1.0)
    }

    // 2. Kundenkonzentration
    @Test fun konzentrationEinKundeUeberZehnProzent() {
        val g = netz(kante("L", Netz.Art.LIEFERT, "K1", pct(12.0, Netz.Bezug.UMSATZ)), kante("L", Netz.Art.LIEFERT, "K2", pct(3.0, Netz.Bezug.UMSATZ)))
        val a = ableiten(g).single { it.typ == Typ.KONZENTRATION }
        assertEquals(listOf("L", "K1", "K2"), a.knoten)
        assertEquals(12.0, a.zahl, 1e-9)
        assertEquals(15.0, a.zahl2, 1e-9)
    }

    @Test fun konzentrationNenntDieGroesstenDreiUndGrenzen() {
        val k = listOf(12.0, 9.0, 8.0, 7.0).mapIndexed { i, p -> kante("L", Netz.Art.LIEFERT, "K$i", pct(p, Netz.Bezug.UMSATZ)) }
        val a = ableiten(netz(*k.toTypedArray())).single { it.typ == Typ.KONZENTRATION }
        assertEquals(12.0, a.zahl, 1e-9)
        assertEquals(29.0, a.zahl2, 1e-9)
        assertEquals(4, a.n)
        // Vier Kunden mit je 7 %: weder einer ueber 10 % noch drei ueber 30 %
        val klein = (1..4).map { kante("L", Netz.Art.LIEFERT, "K$it", pct(7.0, Netz.Bezug.UMSATZ)) }
        assertFalse(Typ.KONZENTRATION in typen(netz(*klein.toTypedArray())))
        // Genau 10 % und genau 30 % reichen nicht
        val grenze = (1..4).map { kante("L", Netz.Art.LIEFERT, "K$it", pct(10.0, Netz.Bezug.UMSATZ)) }
        assertFalse(Typ.KONZENTRATION in typen(netz(*grenze.toTypedArray())))
        // Eine Gruppe von Kunden ("3 Kunden") ist kein einzelner Abnehmer
        val gruppe = listOf(kante("L", Netz.Art.LIEFERT, "kunden:L:3", pct(45.0, Netz.Bezug.UMSATZ)))
        assertFalse(Typ.KONZENTRATION in typen(netz(*gruppe.toTypedArray())))
        // Anteile am Kapital zaehlen nicht
        val kapital = listOf(kante("L", Netz.Art.LIEFERT, "K1", pct(50.0, Netz.Bezug.KAPITAL)))
        assertFalse(Typ.KONZENTRATION in typen(netz(*kapital.toTypedArray())))
    }

    @Test fun konzentrationBeiCloudKundeAlsAbnehmer() {
        // Microsoft kauft Cloud bei CoreWeave mit 67 % von deren Umsatz: Lieferant ist CoreWeave
        val g = netz(kante("M", Netz.Art.RECHENZENTRUM, "C", pct(67.0, Netz.Bezug.UMSATZ)))
        val a = ableiten(g).single { it.typ == Typ.KONZENTRATION }
        assertEquals("C", a.knoten[0])
        assertEquals("M", a.knoten[1])
    }

    // 3. Lieferantenabhaengigkeit
    @Test fun einzigerLieferantFuerSchluesselteil() {
        val g = netz(
            kante("T", Netz.Art.LIEFERT, "N", teil = "spitzenchips", schluessel = true),
            kante("T", Netz.Art.LIEFERT, "A", teil = "spitzenchips", schluessel = true),
        )
        val a = ableiten(g).single { it.typ == Typ.LIEFERANT }
        assertEquals("T", a.knoten[0])
        assertEquals(setOf("N", "A"), a.knoten.drop(1).toSet())
        assertEquals("spitzenchips", a.teil)
        assertEquals(2, a.n)
    }

    @Test fun zweiterLieferantOderKeinSchluesselteilKeineAussage() {
        val zwei = netz(
            kante("T", Netz.Art.LIEFERT, "N", teil = "hbm", schluessel = true),
            kante("S", Netz.Art.LIEFERT, "N", teil = "hbm", schluessel = true),
        )
        assertFalse(Typ.LIEFERANT in typen(zwei))
        val kein = netz(kante("T", Netz.Art.LIEFERT, "N", teil = "montage", schluessel = false))
        assertFalse(Typ.LIEFERANT in typen(kein))
        // Anderes Teil beim selben Abnehmer ist kein Zweitlieferant
        val anders = netz(
            kante("T", Netz.Art.LIEFERT, "N", teil = "spitzenchips", schluessel = true),
            kante("S", Netz.Art.LIEFERT, "N", teil = "hbm", schluessel = false),
        )
        assertTrue(Typ.LIEFERANT in typen(anders))
    }

    // 4. Gegenseitige Abhaengigkeit
    @Test fun gegenseitigKundeUndLieferant() {
        // N liefert an C (C zahlt N) und N kauft Cloud bei C (N zahlt C)
        val g = netz(kante("N", Netz.Art.LIEFERT, "C"), kante("N", Netz.Art.RECHENZENTRUM, "C"))
        val a = ableiten(g).single { it.typ == Typ.GEGENSEITIG }
        assertEquals(0.0, a.zahl, 0.0)
        assertEquals(setOf("N", "C"), a.knoten.toSet())
    }

    @Test fun gegenseitigAnteile() {
        val g = netz(kante("A", Netz.Art.HAELT_ANTEIL, "B"), kante("B", Netz.Art.HAELT_ANTEIL, "A"))
        val a = ableiten(g).single { it.typ == Typ.GEGENSEITIG }
        assertEquals(1.0, a.zahl, 0.0)
        // Einseitig: keine Aussage
        assertFalse(Typ.GEGENSEITIG in typen(netz(kante("A", Netz.Art.HAELT_ANTEIL, "B"), kante("A", Netz.Art.KAUFT, "B"))))
        // Beteiligung und Kauf in Gegenrichtung zaehlen nicht als gegenseitig
        assertFalse(Typ.GEGENSEITIG in typen(netz(kante("A", Netz.Art.HAELT_ANTEIL, "B"), kante("B", Netz.Art.KAUFT, "A"))))
    }

    // 5. Bedingte Zusagen
    @Test fun bedingteBetraegeGegenFesteVertraege() {
        val g = netz(
            kante("A", Netz.Art.FINANZIERT, "B", usd(100.0, true), Netz.Status.ABSICHT),
            kante("C", Netz.Art.HAELT_ANTEIL, "B", usd(10.0), bedingt = true),
            kante("D", Netz.Art.RECHENZENTRUM, "B", usd(50.0)),
            kante("E", Netz.Art.OPTIONSSCHEIN, "B", Netz.Umfang(160e6, Netz.Einheit.AKTIEN)),
        )
        val a = ableiten(g).single { it.typ == Typ.BEDINGT }
        // Gezaehlt, nicht summiert: zwei von drei Zusagen sind Absicht oder bedingt
        assertEquals(2.0, a.zahl, 0.0)
        assertEquals(3.0, a.zahl2, 0.0)
        assertEquals(1, a.n)
    }

    @Test fun bedingtNurMitBedingtemUndKeineStaatsgelderOderTochter() {
        val fest = netz(kante("A", Netz.Art.KAUFT, "B", usd(10.0)), kante("C", Netz.Art.KAUFT, "B", usd(20.0)))
        assertFalse(Typ.BEDINGT in typen(fest))
        val nurOptionen = netz(kante("A", Netz.Art.OPTIONSSCHEIN, "B"), kante("C", Netz.Art.OPTIONSSCHEIN, "D"))
        val a = ableiten(nurOptionen).single { it.typ == Typ.BEDINGT }
        assertEquals(2, a.n)
        val staat = netz(kante("S", Netz.Art.STAAT_FINANZIERT, "B", usd(10.0), Netz.Status.ZUGESAGT), kante("S", Netz.Art.STAAT_FINANZIERT, "C", usd(5.0)))
        assertFalse(Typ.BEDINGT in typen(staat))
    }

    // 6. Klumpen
    @Test fun klumpenAbFuenfVerbundenenFirmen() {
        val k = (1..5).map { kante("H", Netz.Art.RECHENZENTRUM, "F$it") }
        val a = ableiten(netz(*k.toTypedArray())).single { it.typ == Typ.KLUMPEN }
        assertEquals("H", a.knoten[0])
        assertEquals(5, a.n)
        // Die Aktie im Fokus selbst ist nie der Klumpen
        assertFalse(Typ.KLUMPEN in typen(netz(*k.toTypedArray()), "H"))
        val vier = (1..4).map { kante("H", Netz.Art.RECHENZENTRUM, "F$it") }
        assertFalse(Typ.KLUMPEN in typen(netz(*vier.toTypedArray())))
        // Toechter und Wettbewerb zaehlen nicht
        val tochter = (1..6).map { kante("H", Netz.Art.TOCHTER, "F$it") }
        assertFalse(Typ.KLUMPEN in typen(netz(*tochter.toTypedArray())))
    }

    // 7. Kontrolle
    @Test fun kontrolleNurBeiMehrheitDerStimmen() {
        val g = netz(
            kante("Z", Netz.Art.HAELT_ANTEIL, "M", pct(60.8, Netz.Bezug.STIMMEN)),
            kante("P", Netz.Art.HAELT_ANTEIL, "G", pct(27.4, Netz.Bezug.STIMMEN)),
            kante("Q", Netz.Art.HAELT_ANTEIL, "H", pct(50.0, Netz.Bezug.STIMMEN)),
            kante("K", Netz.Art.HAELT_ANTEIL, "Y", pct(80.0, Netz.Bezug.KAPITAL)),
        )
        // Mehr als die Haelfte der Stimmen; die Sperrminoritaet ist deutsches Aktienrecht und keine allgemeine Aussage
        val a = ableiten(g).filter { it.typ == Typ.KONTROLLE }
        assertEquals(1, a.size)
        assertEquals(listOf("Z", "M"), a[0].knoten)
        assertEquals(60.8, a[0].zahl, 1e-9)
    }

    // 8. Wettbewerb
    @Test fun wettbewerberDavonKundeOderLieferant() {
        val g = netz(
            kante("A", Netz.Art.KONKURRIERT, "B"), kante("A", Netz.Art.KONKURRIERT, "C"), kante("A", Netz.Art.KONKURRIERT, "D"),
            kante("A", Netz.Art.LIEFERT, "B"),
        )
        val a = ableiten(g, "A").single { it.typ == Typ.WETTBEWERB }
        assertEquals(3.0, a.zahl, 0.0)
        assertEquals(1.0, a.zahl2, 0.0)
        assertEquals(setOf("B", "C", "D"), a.knoten.toSet())
        assertFalse(Typ.WETTBEWERB in typen(netz(kante("A", Netz.Art.KAUFT, "B"))))
    }

    // 9. Ueberkreuzmandate
    @Test fun ueberkreuzmandate() {
        val g = netz(
            kante("P", Netz.Art.MANDAT, "F1", rolle = "Director"), kante("P", Netz.Art.MANDAT, "F2", rolle = "CEO"), kante("Q", Netz.Art.MANDAT, "F3"),
            typen = mapOf("P" to Netz.Typ.PERSON, "Q" to Netz.Typ.PERSON),
        )
        val a = ableiten(g).single { it.typ == Typ.MANDATE }
        assertEquals(setOf("F1", "F2"), a.knoten.toSet())
        assertEquals(1.0, a.zahl, 0.0)
        assertEquals(2, a.n)
        // Beendetes Mandat zaehlt nicht
        val b = netz(
            kante("P", Netz.Art.MANDAT, "F1"), kante("P", Netz.Art.MANDAT, "F2", status = Netz.Status.BEENDET),
            typen = mapOf("P" to Netz.Typ.PERSON),
        )
        assertFalse(Typ.MANDATE in typen(b))
    }

    // 10. Staatsgeld
    @Test fun staatsgeldUndDavonAusgezahlt() {
        val g = netz(
            kante("S", Netz.Art.STAAT_FINANZIERT, "A", usd(8.0), Netz.Status.ZUGESAGT),
            kante("S", Netz.Art.STAAT_FINANZIERT, "B", usd(2.0), Netz.Status.AUSGEZAHLT),
            kante("E", Netz.Art.STAAT_FINANZIERT, "C", Netz.Umfang(5e9, Netz.Einheit.EUR), Netz.Status.ZUGESAGT),
            kante("E", Netz.Art.STAAT_FINANZIERT, "D", usd(9.0), Netz.Status.ZURUECKGEZOGEN),
        )
        val a = ableiten(g).single { it.typ == Typ.STAATSGELD }
        assertEquals(10e9, a.zahl, 1.0)
        assertEquals(2e9, a.zahl2, 1.0)
        assertEquals(5e9, a.zahl3, 1.0)
        assertEquals(0.0, a.zahl4, 0.0)
        assertFalse(a.knoten.contains("D"))
        assertFalse(Typ.STAATSGELD in typen(netz(kante("A", Netz.Art.KAUFT, "B", usd(1.0)))))
    }

    // Auswahl und Ausschnitt
    @Test fun hoechstensFuenfInFesterReihenfolge() {
        val g = NetzTestHilfe.buendel.graph
        val a = NetzAussagen.ableiten(g, g.knoten.keys, HEUTE, "NVDA")
        assertTrue(a.size in 2..5)
        assertEquals(a.map { it.typ.ordinal }, a.map { it.typ.ordinal }.sorted())
        assertTrue(a.any { it.typ == Typ.KREISLAUF })
    }

    @Test fun aussagenFolgenDemSichtbarenAusschnitt() {
        val g = NetzTestHilfe.buendel.graph
        val klein = NetzAussagen.ableiten(g, setOf("NVDA", "OPENAI"), HEUTE, "NVDA")
        assertTrue(klein.any { it.typ == Typ.KREISLAUF && it.knoten == listOf("NVDA", "OPENAI") })
        val nur = NetzAussagen.ableiten(g, setOf("NVDA"), HEUTE, "NVDA")
        assertTrue(nur.isEmpty())
        val ki = NetzAussagen.ableiten(g, setOf("NVDA", "NVDA_A", "NVDA_B"), HEUTE, "NVDA")
        assertTrue(ki.any { it.typ == Typ.KONZENTRATION && it.zahl2 == 36.0 })
        val meta = NetzAussagen.ableiten(g, setOf("ZUCKERBERG", "META"), HEUTE, "META")
        assertTrue(meta.any { it.typ == Typ.KONTROLLE && it.zahl2 == 1.0 })
    }

    @Test fun beendeteKantenFliessenNichtEin() {
        val g = netz(
            kante("N", Netz.Art.HAELT_ANTEIL, "O", usd(30.0)),
            kante("O", Netz.Art.KAUFT, "N", usd(50.0), Netz.Status.BEENDET),
        )
        assertTrue(ableiten(g).isEmpty())
    }
}
