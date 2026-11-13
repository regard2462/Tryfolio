package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.ui.blkGlossarLesen
import de.troetenrepublik.app.ui.blkSpuren
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Fassung 0.78 (L6): Karte je Blasenzeichen. Ampel und Skala lesen dieselben Schwellen (Blasen.SCHWELLEN), die
// Vergleichspunkte kommen aus Blasen.VERGLEICHE oder aus der Reihe, jedes Zeichen hat deutschen und englischen Text.
class Fassung078L6BlasenKarteTest {

    private val alle = Blasen.DAFUER + Blasen.DAGEGEN
    private val tagMs = 86_400_000L
    private fun ms(s: String) = LocalDate.parse(s).toEpochDay() * tagMs

    // Ampel einer Messgroesse ueber ihre eigene Rechenfunktion, soweit sie den Wert direkt nimmt.
    private fun ueberFunktion(k: String, v: Double): Ampel? = when (k) {
        Blasen.CAPE -> Blasen.cape(Ersatzquellen.Cape(v, 2026, 10, 50.0, 17.0))?.ampel
        Blasen.MARGIN -> Blasen.margin(Ersatzquellen.Margin(0L, 500.0, v))?.ampel
        Blasen.KGV -> Blasen.kgv(v)?.ampel
        Blasen.BUFFETT -> Blasen.buffett(v * 300.0, 30e12)?.ampel
        Blasen.TREND -> Blasen.trend(v)?.ampel
        Blasen.IPO -> Blasen.ipo(v)?.ampel
        Blasen.STIMMUNG -> Blasen.stimmung(v)?.ampel
        Blasen.GEWINN -> Blasen.gewinn(v)?.ampel
        Blasen.PRAEMIE -> Blasen.praemie(20.0, 5.0 - v)?.ampel
        Blasen.BREITE -> Blasen.breite(v, 0.0)?.ampel
        else -> null
    }

    @Test fun schwellenWieVorher() {
        // Die Grenzen, die vor der Umstellung in den Rechenfunktionen standen
        assertEquals(Ampel.GRUEN, Blasen.kgv(19.99)!!.ampel)
        assertEquals(Ampel.GELB, Blasen.kgv(20.0)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.kgv(25.0)!!.ampel)
        assertEquals(Ampel.GELB, Blasen.cape(Ersatzquellen.Cape(22.0, 2026, 10, 0.0, 17.0))!!.ampel)
        assertEquals(Ampel.ROT, Blasen.cape(Ersatzquellen.Cape(30.0, 2026, 10, 0.0, 17.0))!!.ampel)
        assertEquals(Ampel.GELB, Blasen.stimmung(75.0)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.stimmung(75.01)!!.ampel)
        assertEquals(Ampel.GELB, Blasen.stimmung(60.0)!!.ampel)
        assertEquals(Ampel.GRUEN, Blasen.gewinn(10.0)!!.ampel)
        assertEquals(Ampel.GELB, Blasen.gewinn(9.99)!!.ampel)
        assertEquals(Ampel.GELB, Blasen.gewinn(3.0)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.gewinn(2.99)!!.ampel)
        assertEquals(Ampel.GRUEN, Blasen.praemie(20.0, 3.0)!!.ampel) // 5 - 3 = 2
        assertEquals(Ampel.GELB, Blasen.praemie(20.0, 5.0)!!.ampel) // 0
        assertEquals(Ampel.ROT, Blasen.praemie(20.0, 5.01)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.buffett(45_000.0, 30e12)!!.ampel) // 150 %
        assertEquals(Ampel.GELB, Blasen.buffett(33_000.0, 30e12)!!.ampel) // 110 %
        assertEquals(Ampel.GRUEN, Blasen.buffett(32_000.0, 30e12)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.erwaehnung(List(59) { 10 } + List(10) { 100 })!!.ampel)
        val werte = List(40) { 1.0 } + List(10) { 2.0 }
        assertEquals(Ampel.GELB, Blasen.konzentration(werte)!!.ampel) // 20 von 60 = 33 %
        assertEquals(Ampel.ROT, Blasen.ampel(Blasen.KONZENTRATION, 35.0))
        assertEquals(Ampel.GELB, Blasen.ampel(Blasen.KONZENTRATION, 27.0))
    }

    @Test fun ampelUndSkalaMitDenselbenSchwellen() {
        for (k in alle) {
            val s = Blasen.SCHWELLEN[k]
            assertNotNull("Schwellen fehlen: $k", s)
            val (lo, hi) = Blasen.skala(k, (s!!.von + s.bis) / 2)
            val zonen = Blasen.zonen(k, lo, hi)
            assertEquals(3, zonen.size)
            // Die Zonen grenzen an den Schwellen aneinander
            val grenzen = zonen.zipWithNext().map { (a, b) -> assertEquals(a.bis, b.von, 1e-12); a.bis }.toSet()
            assertEquals(setOf(s.gelb, s.rot), grenzen)
            // Fuer Werte quer ueber die Skala (nicht genau auf einer Grenze) zeigt die Zone dieselbe Farbe wie die Ampel
            var v = lo + 0.0137
            while (v < hi) {
                if (abs(v - s.gelb) > 1e-6 && abs(v - s.rot) > 1e-6) {
                    val zone = zonen.first { v >= it.von && v < it.bis || (it === zonen.last() && v >= it.von) }
                    assertEquals("$k bei $v", Blasen.ampel(k, v), zone.ampel)
                    ueberFunktion(k, v)?.let { assertEquals("$k Funktion bei $v", zone.ampel, it) }
                }
                v += (hi - lo) / 97
            }
        }
    }

    @Test fun skalaUmfasstWertUndPunkte() {
        val (lo, hi) = Blasen.skala(Blasen.CAPE, 61.0, listOf(2.0))
        assertTrue(lo < 2.0 && hi > 61.0)
        val (lo2, hi2) = Blasen.skala(Blasen.STIMMUNG, 50.0)
        assertEquals(0.0, lo2, 1e-9)
        assertEquals(100.0, hi2, 1e-9)
        // Ohne Schwellen (kuenftiges Zeichen): ein Bereich um den Wert, keine Zonen
        val (lo3, hi3) = Blasen.skala("neu", 5.0)
        assertTrue(lo3 < 5.0 && hi3 > 5.0)
        assertTrue(Blasen.zonen("neu", lo3, hi3).isEmpty())
        assertEquals(Ampel.GRUEN, Blasen.ampel("neu", 1e9))
    }

    @Test fun textJedesZeichensDeutschUndEnglisch() {
        for (k in alle) for (s in listOf(De, En)) {
            assertTrue("Kern $k", s.blkKern(k).isNotBlank())
            assertTrue("Warum $k", s.blkWarum(k).isNotBlank())
            assertTrue("Schwaeche $k", s.blkSchwaeche(k).isNotBlank())
            assertFalse("Name $k", (s.ersBlName(k) ?: s.blName(k)) == k)
            for (t in listOf(s.blkKern(k), s.blkWarum(k), s.blkSchwaeche(k))) {
                assertFalse("Gedankenstrich in $t", t.contains(" – ") || t.contains(" — "))
                assertFalse("Frage in $t", t.contains("?"))
            }
        }
        // Kuenftige Zeichen ohne Text: leer, kein Absturz
        assertEquals("", De.blkKern("neu"))
        assertEquals("", En.blkSchwaeche("neu"))
        assertNull(De.blkBegriff("neu"))
        assertEquals("Gelb ab 22, Rot ab 30", De.blkSchwellen("22", "30", faellt = false, rotUeber = false))
        assertEquals("Gelb ab 60, Rot über 75", De.blkSchwellen("60", "75", faellt = false, rotUeber = true))
        assertEquals("Yellow below 2 pts, red below 0 pts", En.blkSchwellen("2 pts", "0 pts", faellt = true, rotUeber = false))
    }

    @Test fun begriffeStehenImGlossar() {
        val assets = listOf("src/main/assets", "../app/src/main/assets").map { File(it) }.first { File(it, "finanzwissen").isDirectory }
        val de = blkGlossarLesen(File(assets, "finanzwissen/data/glossar.json").readText())
        val en = blkGlossarLesen(File(assets, "finanzwissen/data/en/glossar.json").readText())
        assertTrue(de.size > 500 && en.size > 500)
        for (k in alle) {
            De.blkBegriff(k)?.let { assertTrue("Glossar de: $it", de[it]?.isNotBlank() == true) }
            En.blkBegriff(k)?.let { assertTrue("Glossar en: $it", en[it]?.isNotBlank() == true) }
            assertEquals("Begriff in beiden Sprachen oder in keiner: $k", De.blkBegriff(k) == null, En.blkBegriff(k) == null)
        }
        // Verweise im Text werden zu einfachem Text
        val m = blkGlossarLesen("""[{"begriff":"A","text":"Siehe [[KGV]] und [[Shiller-CAPE|CAPE]], **fett**."}]""")
        assertEquals("Siehe KGV und CAPE, fett.", m["A"])
        assertTrue(blkGlossarLesen("kein json").isEmpty())
    }

    @Test fun vergleichspunkteOhneUndMitReihe() {
        // Ohne Reihe: die ueberlieferten Werte, Punkte ohne Wert fehlen
        val ohne = Blasen.vergleiche(Blasen.CAPE)
        assertEquals(listOf(Blasen.SCHNITT, "1929", "1999"), ohne.map { it.marke })
        assertEquals(44.2, ohne.last().wert, 1e-9)
        assertTrue(ohne.none { it.ausReihe })
        assertTrue(Blasen.vergleiche(Blasen.MARGIN).isEmpty())
        assertTrue(Blasen.vergleiche(Blasen.STIMMUNG).isEmpty())
        assertEquals(listOf(Blasen.SCHNITT, "2000"), Blasen.vergleiche(Blasen.BUFFETT).map { it.marke })

        // Mit Reihe: Monate 1925 bis 2026, Spitzen im September 1929, Dezember 1999 und November 2021
        val reihe = ArrayList<Pair<Long, Double>>()
        for (j in 1925..2026) for (m in 1..12) {
            val d = LocalDate.of(j, m, 1)
            val w = when (d.toString()) { "1929-09-01" -> 32.56; "1999-12-01" -> 44.2; "2021-11-01" -> 38.58; else -> 15.0 + (j % 7) }
            reihe += d.toEpochDay() * tagMs to w
        }
        val mit = Blasen.vergleiche(Blasen.CAPE, reihe)
        assertEquals(listOf(Blasen.SCHNITT, "1929", "1999", "2021"), mit.map { it.marke })
        assertTrue(mit.all { it.ausReihe })
        assertEquals(32.56, mit[1].wert, 1e-9)
        assertEquals(38.58, mit[3].wert, 1e-9)
        assertEquals(reihe.sumOf { it.second } / reihe.size, mit[0].wert, 1e-9)

        // Margin: Wachstum je Quartal aus der Reihe der Fed, Hoch im Jahr 2000 aus dem Verlauf
        val q = listOf(
            "1998-01-01" to 140_000.0, "1998-04-01" to 150_000.0, "1998-07-01" to 145_000.0, "1998-10-01" to 150_000.0,
            "1999-01-01" to 175_000.0, "1999-04-01" to 190_000.0, "1999-07-01" to 200_000.0, "1999-10-01" to 230_000.0,
            "2000-01-01" to 280_000.0, "2000-04-01" to 260_000.0, "2000-07-01" to 250_000.0, "2000-10-01" to 210_000.0,
        ).map { LocalDate.parse(it.first).toEpochDay() to it.second }
        val mg = Ersatzquellen.margin(q)!!
        assertEquals((210_000.0 / 230_000 - 1) * 100, mg.wachstum, 1e-9)
        assertEquals(8, mg.verlauf.size)
        assertEquals(LocalDate.parse("1999-01-01").toEpochDay(), mg.verlauf.first().first)
        val punkte = Blasen.vergleiche(Blasen.MARGIN, mg.verlauf.map { it.first * tagMs to it.second })
        assertEquals("2000", punkte.single().marke)
        assertEquals((280_000.0 / 175_000 - 1) * 100, punkte.single().wert, 1e-9)
    }

    @Test fun capeReiheAusShillerDaten() {
        val zeilen = HashMap<Int, Map<Int, Double>>()
        var z = 8
        for (j in 2000..2026) for (m in 1..12) {
            if (j == 2026 && m > 10) break
            zeilen[z++] = mapOf(0 to j + m / 100.0, 12 to 20.0 + z * 0.01)
        }
        val c = Ersatzquellen.capeAusZahlen(listOf(zeilen))!!
        assertEquals(zeilen.size, c.reihe.size)
        assertEquals(LocalDate.of(2000, 1, 1).toEpochDay(), c.reihe.first().first)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), c.reihe.last().first)
        assertEquals(c.wert, c.reihe.last().second, 1e-12)
        assertTrue(c.reihe.zipWithNext().all { (a, b) -> b.first > a.first })
    }

    @Test fun staerksteUnveraendert() {
        // Reihenfolge der roten nach Abstand zur Schwelle wie vor der Umstellung
        val l = listOf(
            Blasen.Indikator(Blasen.CAPE, 39.0, Ampel.ROT), Blasen.Indikator(Blasen.KGV, 26.0, Ampel.ROT),
            Blasen.Indikator(Blasen.GEWINN, 1.0, Ampel.ROT), Blasen.Indikator(Blasen.PRAEMIE, -1.0, Ampel.ROT),
            Blasen.Indikator(Blasen.TREND, 9.0, Ampel.GELB),
        )
        // CAPE 1,3; GEWINN 1,2; PRAEMIE 1,5; KGV 1,04
        assertEquals(listOf(Blasen.PRAEMIE, Blasen.CAPE, Blasen.GEWINN, Blasen.KGV, Blasen.TREND), Blasen.staerkste(l, 5).map { it.schluessel })
    }

    @Test fun beschriftungenOhneUeberlappung() {
        // Zwei Schwellen nah beieinander, ein Punkt dazwischen: der zweite und dritte rutschen in eigene Zeilen
        val spuren = blkSpuren(listOf(0f to 30f, 32f to 60f, 20f to 40f, 100f to 120f), 3, 6f)
        assertEquals(listOf(0, 1, 2, 0), spuren)
        // Kein Platz mehr: null (steht nur in der Liste)
        assertNull(blkSpuren(listOf(0f to 50f, 0f to 50f, 0f to 50f), 2, 0f)[2])
    }

    private fun abs(x: Double) = kotlin.math.abs(x)
}
