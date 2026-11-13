package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.KursScan.Muster
import de.troetenrepublik.app.i18n.KursmusterTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// Scan nach Kursmustern im Aktienfinder (0.78), alle Reihen konstruiert.
class Fassung078U32ScannerTest {

    private fun flach(n: Int, v: Double = 100.0) = List(n) { v }

    private fun treffer(m: Muster, c: List<Double>) = KursScan.treffer(m, KursScan.Reihe(c), c.size - 1)

    @Test
    fun neuesHochNurUeberAllenZwanzigSchluessen() {
        val basis = flach(40) + List(19) { 100.0 + it % 3 }
        val hoch = basis + 103.0
        assertEquals((103.0 / 102.0 - 1) * 100, treffer(Muster.HOCH20, hoch)!!, 1e-9)
        // Gleichstand mit dem alten Hoch ist kein neues Hoch
        assertNull(treffer(Muster.HOCH20, basis + 102.0))
        // Ein hoeherer Schluss vor mehr als 20 Tagen stoert nicht
        assertNotNull(treffer(Muster.HOCH20, flach(20) + 110.0 + flach(20) + 101.0))
        assertNull(treffer(Muster.HOCH20, flach(10) + 110.0 + flach(19) + 101.0))
        // Zu kurze Reihe
        assertNull(KursScan.treffer(Muster.HOCH20, KursScan.Reihe(flach(15) + 120.0), 15))
    }

    @Test
    fun flaggeBrauchtAnstiegUndEngeSpanne() {
        fun reihe(ziel: Double, ruhe: List<Double>) = flach(45) + List(10) { 100.0 + (ziel - 100.0) * (it + 1) / 10 } + ruhe
        val eng = listOf(112.5, 113.0, 112.0, 113.5, 113.0)
        val c = reihe(112.0, eng)
        assertEquals(60, c.size)
        assertEquals(12.0, treffer(Muster.FLAGGE, c)!!, 1e-9)
        assertEquals((113.5 / 112.0 - 1) * 100, KursScan.ruheSpanne(c, c.size - 1)!!, 1e-9)
        // 9 % Anstieg reichen nicht
        assertNull(treffer(Muster.FLAGGE, reihe(109.0, listOf(109.5, 110.0, 109.0, 110.5, 110.0))))
        // Spanne ueber 5 %
        assertNull(treffer(Muster.FLAGGE, reihe(112.0, listOf(112.0, 118.0, 112.0, 113.0, 113.0))))
        // Die Ruhe darf unter dem Hoch des Anstiegs liegen, solange die Spanne passt
        assertNotNull(treffer(Muster.FLAGGE, reihe(112.0, listOf(100.0, 104.0, 102.0, 103.0, 104.0))))
        // Ohne Anstieg davor
        assertNull(treffer(Muster.FLAGGE, flach(60)))
    }

    @Test
    fun zurueckUeberDenDurchschnitt() {
        val c = flach(40) + flach(19, 95.0) + 105.0
        // Gestern 95 unter dem Durchschnitt 95,25, heute 105 ueber 95,5
        assertEquals((105.0 / 95.5 - 1) * 100, treffer(Muster.UEBER_SCHNITT20, c)!!, 1e-9)
        // Schon gestern darueber: kein Wechsel
        assertNull(treffer(Muster.UEBER_SCHNITT20, c + 106.0))
        // Heute weiter darunter
        assertNull(treffer(Muster.UEBER_SCHNITT20, flach(40) + flach(20, 95.0)))
    }

    @Test
    fun rsiMuster() {
        val faellt = List(60) { 100.0 * 0.99.pow(it) }
        val rsi = treffer(Muster.RSI_UEBERVERKAUFT, faellt)!!
        assertTrue(rsi < 30.0)
        // Ohne hoeheren Schluss keine Umkehr
        assertNull(treffer(Muster.RSI_UMKEHR, faellt))
        val dreht = faellt + (faellt.last() * 1.005)
        assertNotNull(treffer(Muster.RSI_UMKEHR, dreht))
        assertEquals(Indikatoren.rsi(dreht).last()!!, treffer(Muster.RSI_UMKEHR, dreht)!!, 1e-9)
        // Steigende Reihe: weder ueberverkauft noch Umkehr
        val steigt = List(60) { 100.0 * 1.01.pow(it) }
        assertNull(treffer(Muster.RSI_UEBERVERKAUFT, steigt))
        assertNull(treffer(Muster.RSI_UMKEHR, steigt))
    }

    @Test
    fun heuteUndTagesaenderung() {
        val c = flach(40) + List(19) { 100.0 + it % 3 } + 103.0
        assertTrue(KursScan.heute(c).any { it.muster == Muster.HOCH20 })
        // Unter 60 Schluessen und bei fehlerhaften Kursen keine Aussage
        assertTrue(KursScan.heute(c.takeLast(59)).isEmpty())
        assertTrue(KursScan.heute(c.dropLast(1) + 0.0).isEmpty())
        assertTrue(KursScan.heute(flach(59) + Double.NaN).isEmpty())
        assertEquals(3.0, KursScan.tagesAenderung(listOf(100.0, 103.0))!!, 1e-9)
        assertNull(KursScan.tagesAenderung(listOf(100.0)))
        val t = KursScan.trefferVon("AAA", "Alpha AG", c, 12.5)
        assertEquals("Alpha AG", t.first().name)
        assertEquals(12.5, t.first().kurs!!, 0.0)
        assertEquals(3.0, t.first().tag!!, 1e-9)
    }

    @Test
    fun rueckblickZaehltDenErstenTagGegenAlleWerte() {
        val tage = List(130) { it * 86_400_000L }
        // A steigt ab Tag 70 fuenfundzwanzig Tage um je 1 %, B bleibt stehen
        val a = List(130) { i -> 100.0 * 1.01.pow((i - 69).coerceIn(0, 25)) }
        val b = flach(130)
        val r = KursScan.rueckblick(listOf(tage to a, tage to b))
        val hoch = r.getValue(Muster.HOCH20)
        val danach = (1.01.pow(20) - 1) * 100
        // Die Folge neuer Hochs zaehlt einmal, am ersten Tag
        assertEquals(1, hoch.faelle)
        assertEquals(danach, hoch.mittel, 1e-9)
        assertEquals(danach / 2, hoch.markt, 1e-9)
        assertEquals(danach / 2, hoch.mehr, 1e-9)
        // Zu wenige Faelle: keine Zeile
        val stand = KursScan.Stand("X", 0L, 0.0, 0.0, bilanz = r.mapKeys { it.key.name })
        assertNull(stand.bilanzFuer(Muster.HOCH20))
        assertNotNull(stand.copy(bilanz = mapOf("HOCH20" to KursScan.Bilanz(30, 1.0, 0.5))).bilanzFuer(Muster.HOCH20))
        // Zu kurze Reihen
        assertTrue(KursScan.rueckblick(listOf(tage.take(70) to a.take(70))).isEmpty())
    }

    @Test
    fun auswahlFilterUndAbrufe() {
        fun k(s: String, kurs: Double?, vol: Double?, wert: Double?) = KursScan.Kandidat(s, kurs, vol, wert)
        val markt = listOf(
            k("KLEIN", 20.0, 900_000.0, 1e9), k("GROSS", 50.0, 2_000_000.0, 9e9), k("BILLIG", 2.0, 5_000_000.0, 5e9),
            k("DUENN", 80.0, 10_000.0, 7e9), k("OHNE", null, null, 8e9), k("MEINS", 1.0, 1.0, 1.0),
        )
        assertEquals(listOf("MEINS", "ETF", "GROSS", "KLEIN"), KursScan.auswahl(markt, listOf("MEINS", "ETF", "MEINS"), 5.0, 200_000.0))
        // Ohne Grenzen kommen alle, die groessten zuerst
        assertEquals(listOf("GROSS", "OHNE", "DUENN", "BILLIG", "KLEIN", "MEINS"), KursScan.auswahl(markt, emptyList(), 0.0, 0.0))
        assertEquals(listOf("GROSS", "OHNE"), KursScan.auswahl(markt, emptyList(), 0.0, 0.0, hoechstens = 2))
        assertEquals(listOf("A", "GROSS"), KursScan.auswahl(markt, listOf("A", "B"), 5.0, 200_000.0, hoechstens = 1, eigeneHoechstens = 1))
        // Unbekannter Kurs faellt nur durch, wenn eine Grenze gilt
        assertFalse(KursScan.liquide(k("X", null, 1e6, null), 5.0, 0.0))
        assertTrue(KursScan.liquide(k("X", null, null, null), 0.0, 0.0))
        // 20 Werte je Abruf, gemerkte Reihen kosten nichts
        assertEquals(20, KursScan.abrufe(400))
        assertEquals(21, KursScan.abrufe(401))
        assertEquals(25, KursScan.abrufe(KursScan.HOECHSTENS + KursScan.EIGENE_HOECHSTENS))
        assertEquals(0, KursScan.abrufe(400, gemerkt = 400))
        assertEquals(1, KursScan.abrufe(400, gemerkt = 399))
        assertEquals(5.0, KursScan.MIN_KURS, 0.0)
        assertTrue(KursScan.MIN_KURS in KursScan.KURS_STUFEN && KursScan.MIN_VOLUMEN in KursScan.VOLUMEN_STUFEN)
    }

    @Test
    fun ergebnisSpeichernUndOrdnen() {
        val t = listOf(
            KursScan.Treffer("A", "Alpha", "RSI_UEBERVERKAUFT", 25.0), KursScan.Treffer("B", "Beta", "RSI_UEBERVERKAUFT", 12.0),
            KursScan.Treffer("C", "Gamma", "HOCH20", 0.5, kurs = 10.0, tag = 1.2), KursScan.Treffer("D", "Delta", "HOCH20", 2.5),
        )
        val jetzt = 100L * 86_400_000L
        val stand = KursScan.Stand("DAX", jetzt, 5.0, 200_000.0, geprueft = 40, ohneKurse = 1, treffer = t, bilanz = mapOf("HOCH20" to KursScan.Bilanz(44, 1.5, 0.9)))
        assertEquals(listOf("B", "A"), KursScan.sortiert(Muster.RSI_UEBERVERKAUFT, stand.fuer(Muster.RSI_UEBERVERKAUFT)).map { it.symbol })
        assertEquals(listOf("D", "C"), KursScan.sortiert(Muster.HOCH20, stand.fuer(Muster.HOCH20)).map { it.symbol })
        val alt = stand.copy(universum = "SMI", zeit = jetzt - 8 * 86_400_000L)
        val gelesen = KursScan.lesen(KursScan.schreiben(mapOf("DAX" to stand, "SMI" to alt), jetzt))
        // Aelter als sieben Tage faellt weg
        assertEquals(setOf("DAX"), gelesen.keys)
        assertEquals(stand, gelesen["DAX"])
        assertTrue(KursScan.lesen("kaputt").isEmpty())
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(KursmusterTexte.schluessel(true), KursmusterTexte.schluessel(false))
        for (k in KursmusterTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = KursmusterTexte.text(d, k, listOf("1", "2", "3"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{'))
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('…') || t.contains('?'))
        }
        for (m in Muster.entries) for (art in listOf("muster.", "regel.", "zahl.")) assertTrue(m.name, art + m.name in KursmusterTexte.schluessel(true))
        assertEquals("Lädt 40 von 400", KursmusterTexte.text(true, "laedt", listOf("40", "400")))
        assertEquals("Loading 40 of 400", KursmusterTexte.text(false, "laedt", listOf("40", "400")))
    }
}
