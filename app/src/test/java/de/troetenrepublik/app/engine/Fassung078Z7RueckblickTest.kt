package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.engine.WarnRueckblick.Reihe
import de.troetenrepublik.app.i18n.MarktzeichenTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Rueckblick fuer Bodenzeichen und Blasen-Detektor: grosse Boeden aus der Tagesreihe, Werte der Zeichen an Tief und
// Hoch, Phasen und Fehlalarme, Einordnung mit den Schwellen aus Boden und Blasen, die mitgelieferte Datei.
class Fassung078Z7RueckblickTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    // Stuetzpunkte (Platz, Kurs), dazwischen gleichmaessig
    private fun kurse(vararg punkte: Pair<Int, Double>): DoubleArray = DoubleArray(punkte.last().first + 1) { i ->
        val b = punkte.indexOfFirst { it.first >= i }
        if (b == 0) punkte[0].second else {
            val (i0, k0) = punkte[b - 1]
            val (i1, k1) = punkte[b]
            k0 + (k1 - k0) * (i - i0) / (i1 - i0)
        }
    }

    @Test fun grosserBodenBrauchtRueckgangUndErholung() {
        // 100 -> 200, -30 % auf 140, +25 % auf 175 (zu wenig), -50 % vom Hoch auf 100, +60 % auf 160
        val k = kurse(0 to 100.0, 100 to 200.0, 200 to 140.0, 300 to 175.0, 400 to 100.0, 500 to 160.0, 600 to 160.0)
        val w = ZeichenRueckblick.wenden(k)
        assertEquals(1, w.boeden.size)
        assertEquals(100, w.boeden[0].hoch)
        assertEquals(400, w.boeden[0].tief)
        assertNull(w.offenAb)
        // Rueckgang unter 20 %: kein Boden
        assertTrue(ZeichenRueckblick.wenden(kurse(0 to 100.0, 100 to 81.0, 200 to 200.0)).boeden.isEmpty())
        // Tief ohne Erholung um die Haelfte: offen ab dem Hoch
        val offen = ZeichenRueckblick.wenden(kurse(0 to 100.0, 50 to 200.0, 100 to 150.0, 150 to 180.0))
        assertTrue(offen.boeden.isEmpty())
        assertEquals(50, offen.offenAb)
        assertEquals(20.0, ZeichenRueckblick.RUECKGANG_AB, 0.0)
        assertEquals(50.0, ZeichenRueckblick.ERHOLUNG_AB, 0.0)
    }

    @Test fun zwoelfMonateDanach() {
        val tage = LongArray(800) { 1000L + it }
        val k = kurse(0 to 100.0, 365 to 150.0, 799 to 150.0)
        assertEquals(50.0, ZeichenRueckblick.danach(tage, k, 0)!!, 1e-9)
        // Reihe endet vor Ablauf des Jahres
        assertNull(ZeichenRueckblick.danach(tage, k, 500))
    }

    @Test fun phasenAuchVor1970() {
        // Tage vor 1970 sind negativ
        val tage = LongArray(1000) { -20_000L + it }
        val erfuellt = BooleanArray(1000) { it in 10..20 || it in 100..110 || it in 600..650 }
        val p = ZeichenRueckblick.phasen(tage, erfuellt)
        // Pause von 80 Tagen zaehlt mit, die von 490 Tagen trennt
        assertEquals(2, p.size)
        assertEquals(-19_990L, p[0][0]); assertEquals(-19_890L, p[0][1])
        assertEquals(-19_400L, p[1][0]); assertEquals(-19_350L, p[1][1])
    }

    @Test fun fehlalarmeUndOffenePhasen() {
        val tage = LongArray(3000) { -5000L + it }
        // Phasen: 100..150 (Boden bei 200 in der Naehe), 1000..1050 (kein Boden), 2900..2950 (Ende der Reihe: offen)
        val zustand = IntArray(3000) { i -> if (i < 50) 0 else if (i in 100..150 || i in 1000..1050 || i in 2900..2950) 2 else 1 }
        val a = ZeichenRueckblick.alarm("x", tage, zustand, listOf(tage[200], tage[2000], tage[20]), null)!!
        assertEquals(3, a.phasen)
        assertEquals(1, a.mitBoden)
        assertEquals(1, a.fehlalarme)
        assertEquals(1, a.offen)
        // Boeden mit Daten: Platz 200 und 2000 (Platz 20 liegt vor dem ersten Wert), erfuellt an keinem
        assertEquals(2, a.boeden)
        assertEquals(0, a.erfuellt)
        assertEquals(tage[50].toInt(), a.ab)
        assertEquals(153.0 / 2950, a.zeit, 0.001)
        // Phase im laufenden Rueckgang ohne bestaetigtes Tief bleibt offen
        val b = ZeichenRueckblick.alarm("x", tage, IntArray(3000) { i -> if (i in 1000..1050) 2 else 1 }, emptyList(), tage[900])!!
        assertEquals(0, b.fehlalarme)
        assertEquals(1, b.offen)
        assertNull(ZeichenRueckblick.alarm("x", tage, IntArray(3000), emptyList(), null))
    }

    @Test fun einordnungMitDenSchwellenDerApp() {
        fun w(id: String, v: Double, z: Double? = null) = ZeichenRueckblick.Wert(id, v, z)
        // Bodenzeichen: dieselben Grenzen wie Boden.kt
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(w(Boden.KGV, Boden.KGV_ERFUELLT - 0.01)))
        assertEquals(Boden.Lage.NAHE, ZeichenRueckblick.lage(w(Boden.KGV, Boden.KGV_ERFUELLT)))
        assertEquals(Boden.Lage.FERN, ZeichenRueckblick.lage(w(Boden.KGV, Boden.KGV_NAHE)))
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(w(Boden.PRAEMIE, Boden.PRAEMIE_ERFUELLT)))
        assertEquals(Boden.Lage.NAHE, ZeichenRueckblick.lage(w(Boden.PRAEMIE, Boden.PRAEMIE_NAHE)))
        assertEquals(Boden.Lage.FERN, ZeichenRueckblick.lage(w(Boden.PRAEMIE, Boden.PRAEMIE_NAHE - 0.01)))
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(w(Boden.RUECKGANG, Boden.JAHR_ERFUELLT)))
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(w(Boden.RUECKGANG, -5.0, Boden.ZWEI_JAHRE_ERFUELLT)))
        assertEquals(Boden.Lage.NAHE, ZeichenRueckblick.lage(w(Boden.RUECKGANG, Boden.RUECKGANG_NAHE)))
        assertEquals(Boden.Lage.FERN, ZeichenRueckblick.lage(w(Boden.RUECKGANG, -19.9, -19.9)))
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(w(Boden.SCHWANKUNG, Boden.VIX_ERFUELLT)))
        assertEquals(Boden.Lage.NAHE, ZeichenRueckblick.lage(w(Boden.SCHWANKUNG, Boden.VIX_NAHE)))
        assertNull(ZeichenRueckblick.lage(w("unbekannt", 1.0)))
        for (k in Boden.ALLE) assertNotNull(k, ZeichenRueckblick.lage(w(k, 1.0)))
        // Messgroessen des Detektors: Ampel aus Blasen.SCHWELLEN, fuer jede Messgroesse mit Reihe
        for (k in ZeichenRueckblick.BLASEN_MIT_REIHE) {
            val s = Blasen.SCHWELLEN.getValue(k)
            val rot = if (s.richtung == Blasen.Richtung.FAELLT) s.rot - 0.01 else s.rot + 0.01
            val gruen = if (s.richtung == Blasen.Richtung.FAELLT) s.gelb + 0.01 else s.gelb - 0.01
            assertEquals(k, Ampel.ROT, ZeichenRueckblick.ampel(w(k, rot)))
            assertEquals(k, Blasen.ampel(k, rot), ZeichenRueckblick.ampel(w(k, rot)))
            assertEquals(k, Ampel.GRUEN, ZeichenRueckblick.ampel(w(k, gruen)))
            // Rot im Jahr davor zaehlt, und nur dann stammt die Ampel von dort
            assertEquals(k, Ampel.ROT, ZeichenRueckblick.ampel(w(k, gruen, rot)))
            assertTrue(k, ZeichenRueckblick.nurDavor(w(k, gruen, rot)))
            assertFalse(k, ZeichenRueckblick.nurDavor(w(k, rot, rot)))
            assertFalse(k, ZeichenRueckblick.nurDavor(w(k, rot)))
        }
        // Jede Messgroesse des Detektors ist entweder mit oder ohne Reihe gefuehrt
        assertEquals((Blasen.DAFUER + Blasen.DAGEGEN).toSet(), (ZeichenRueckblick.BLASEN_MIT_REIHE + ZeichenRueckblick.BLASEN_OHNE_REIHE).toSet())
        assertTrue(ZeichenRueckblick.BLASEN_MIT_REIHE.intersect(ZeichenRueckblick.BLASEN_OHNE_REIHE.toSet()).isEmpty())
        val hoch = ZeichenRueckblick.Hoch(0, 1, 30.0, listOf(w(Blasen.CAPE, 31.0), w(Blasen.KGV, 15.0), w(Blasen.TREND, 2.0, 16.0)))
        assertEquals(2 to 3, ZeichenRueckblick.zaehlung(hoch))
        assertEquals(ZeichenRueckblick.BLASEN_MIT_REIHE - setOf(Blasen.CAPE, Blasen.KGV, Blasen.TREND), ZeichenRueckblick.ohneDaten(hoch))
        val tief = ZeichenRueckblick.Tief(0, 1, 30.0, null, listOf(w(Boden.KGV, 8.0), w(Boden.PRAEMIE, 1.0), w(Boden.RUECKGANG, -35.0)))
        assertEquals(2 to 3, ZeichenRueckblick.zaehlung(tief))
        assertEquals(listOf(Boden.SCHWANKUNG), ZeichenRueckblick.ohneDaten(tief))
    }

    @Test fun rechnungAusReihen() {
        // 100 -> 200 in 300 Tagen, -50 % in 300 Tagen, +60 % in 300 Tagen, dann gleich; Gewinn 10, Zins 4 %, kein VIX
        val n = 1500
        val tage = LongArray(n) { 10_957L + it }
        val k = kurse(0 to 100.0, 300 to 200.0, 600 to 100.0, 900 to 160.0, n - 1 to 160.0)
        val monate = LongArray(60) { 10_900L + it * 30 }
        val q = WarnRueckblick.Quellen(
            mapOf(
                WarnRueckblick.R_SP to Reihe(tage, k),
                WarnRueckblick.R_GEWINN to Reihe(monate, DoubleArray(60) { 10.0 }, 0, 200),
                WarnRueckblick.R_ZINS10 to Reihe(tage, DoubleArray(n) { 4.0 }),
            ),
        )
        val d = ZeichenRueckblick.rechnen(tage, k, q, "2004-02-08")
        assertEquals(1, d.boeden.size)
        val b = d.boeden[0]
        assertEquals(tage[300].toInt(), b.hoch)
        assertEquals(tage[600].toInt(), b.tief)
        assertEquals(50.0, b.rueckgang, 1e-9)
        assertEquals(60.0, b.danach!!, 1e-9)
        // KGV 100 / 10 = 10: nahe (erfuellt erst unter 10); Renditeabstand 10 - 4 = 6: erfuellt; Rueckgang gegen den Kurs
        // 365 Tage zuvor (178,33): -43,93 %, erfuellt; der VIX fehlt
        assertEquals(listOf(Boden.KGV, Boden.PRAEMIE, Boden.RUECKGANG), b.werte.map { it.id })
        assertEquals(10.0, b.werte[0].wert, 1e-9)
        assertEquals(6.0, b.werte[1].wert, 1e-9)
        assertEquals(-43.93, b.werte[2].wert, 1e-9)
        assertEquals(Boden.Lage.NAHE, ZeichenRueckblick.lage(b.werte[0]))
        assertEquals(2 to 3, ZeichenRueckblick.zaehlung(b))
        assertEquals(listOf(Boden.SCHWANKUNG), ZeichenRueckblick.ohneDaten(b))
        // Hoch vor dem Einbruch: KGV 20 (gelb), Aktien gegen Anleihen 5 - 4 = 1 (gelb), Abstand zur 200-Tage-Linie
        assertEquals(1, d.blasen.size)
        val h = d.blasen[0]
        assertEquals(tage[300].toInt(), h.hoch)
        assertEquals(tage[600].toInt(), h.tief)
        assertEquals(20.0, h.werte.first { it.id == Blasen.KGV }.wert, 1e-9)
        assertEquals(1.0, h.werte.first { it.id == Blasen.PRAEMIE }.wert, 1e-9)
        assertEquals(Ampel.GELB, ZeichenRueckblick.ampel(h.werte.first { it.id == Blasen.KGV }))
        // Mittel der 200 Schlusskurse bis zum Hoch: 200 - 99,5 / 3
        assertEquals(WarnRueckblick.runden((200 / (200 - 99.5 / 3) - 1) * 100, 2), h.werte.first { it.id == Blasen.TREND }.wert, 1e-9)
        assertTrue(Blasen.CAPE !in h.werte.map { it.id })
        // Die Zaehlung traegt die Schwellen, mit denen sie gerechnet wurde; nur damit gilt sie
        assertEquals(ZeichenRueckblick.bodenSchwellen(), d.schwellen)
        assertTrue(ZeichenRueckblick.alarme(d).isNotEmpty())
        assertTrue(ZeichenRueckblick.alarme(d.copy(schwellen = d.schwellen.map { it + 1 })).isEmpty())
        val ruecken = ZeichenRueckblick.alarme(d).first { it.id == Boden.RUECKGANG }
        assertEquals(1, ruecken.phasen)
        assertEquals(1, ruecken.mitBoden)
        assertEquals(0, ruecken.fehlalarme)
        assertEquals(1 to 1, ruecken.erfuellt to ruecken.boeden)
        // Lesen und Schreiben
        assertEquals(d, ZeichenRueckblick.lesen(ZeichenRueckblick.schreiben(d)))
    }

    @Test fun capeAlsMonatswert() {
        // Monatswerte 30 und 33 am ersten Tag ihres Monats: der Wert gilt im ganzen Monat, der Kurs des Tages aendert ihn nicht
        val tage = LongArray(80) { 20_000L + it }
        val q = WarnRueckblick.Quellen(
            mapOf(
                WarnRueckblick.R_SP to Reihe(tage, DoubleArray(80) { 110.0 }),
                WarnRueckblick.R_CAPE to Reihe(longArrayOf(20_000L, 20_031L), doubleArrayOf(30.0, 33.0), WarnRueckblick.VERZUG_CAPE, 70),
            ),
        )
        assertNull(ZeichenRueckblick.cape(q, 19_999L))
        assertEquals(30.0, ZeichenRueckblick.cape(q, 20_005L)!!, 1e-9)
        assertEquals(30.0, ZeichenRueckblick.cape(q, 20_030L)!!, 1e-9)
        assertEquals(33.0, ZeichenRueckblick.cape(q, 20_031L)!!, 1e-9)
        assertEquals(33.0, ZeichenRueckblick.blasenWerte(q, 20_050L).getValue(Blasen.CAPE), 1e-9)
    }

    @Test fun mitgelieferteDatei() {
        val f = asset(ZeichenRueckblick.DATEI)
        assertTrue(f.length() < 300_000)
        val d = ZeichenRueckblick.lesen(f.readText(Charsets.UTF_8))
        assertNotNull(d)
        d!!
        fun jahr(t: Int) = LocalDate.ofEpochDay(t.toLong()).year
        // Schwellen der Zaehlung sind die aus Boden.kt
        assertEquals(ZeichenRueckblick.bodenSchwellen(), d.schwellen)
        assertEquals(Boden.ALLE + ZeichenRueckblick.VIELE, ZeichenRueckblick.alarme(d).map { it.id })
        for (a in ZeichenRueckblick.alarme(d)) {
            assertEquals(a.id, a.phasen, a.mitBoden + a.fehlalarme + a.offen)
            assertTrue(a.id, a.erfuellt <= a.boeden && a.boeden <= d.boeden.size && a.zeit in 0.0..1.0)
        }
        // Die grossen Boeden stammen aus der Reihe
        val tiefs = d.boeden.map { jahr(it.tief) }
        for (j in listOf(1932, 1942, 1974, 1982, 1987, 2002, 2009, 2020, 2022)) assertTrue("Boden $j", j in tiefs)
        assertEquals(d.boeden.sortedBy { it.tief }, d.boeden)
        for (b in d.boeden) {
            assertTrue(b.rueckgang >= ZeichenRueckblick.RUECKGANG_AB && b.hoch < b.tief)
            assertTrue((b.danach ?: 1.0) > 0)
            assertTrue(b.werte.all { ZeichenRueckblick.lage(it) != null && it.id in Boden.ALLE })
            // VIX erst seit 1990
            assertEquals(jahr(b.tief) >= 1990, b.werte.any { it.id == Boden.SCHWANKUNG })
            assertTrue(b.werte.any { it.id == Boden.RUECKGANG })
        }
        // Bekannte Staende: 1974 und 1982 einstelliges KGV, 2009 hoher VIX und Einbruch
        fun boden(j: Int) = d.boeden.first { jahr(it.tief) == j }
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(boden(1974).werte.first { it.id == Boden.KGV }))
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(boden(1982).werte.first { it.id == Boden.KGV }))
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(boden(2009).werte.first { it.id == Boden.SCHWANKUNG }))
        assertEquals(Boden.Lage.ERFUELLT, ZeichenRueckblick.lage(boden(2009).werte.first { it.id == Boden.RUECKGANG }))
        assertEquals(LocalDate.of(2009, 3, 9), LocalDate.ofEpochDay(boden(2009).tief.toLong()))
        // Hochs vor Einbruechen
        val hochs = d.blasen.map { jahr(it.hoch) }
        for (j in listOf(1929, 1973, 1987, 2000, 2007, 2022)) assertTrue("Hoch $j", j in hochs)
        for (h in d.blasen) {
            assertTrue(h.rueckgang >= WarnRueckblick.EINBRUCH && h.hoch < h.tief)
            assertTrue(h.werte.all { it.id in ZeichenRueckblick.BLASEN_MIT_REIHE })
            assertTrue(h.werte.any { it.id == Blasen.TREND })
            val (rot, mit) = ZeichenRueckblick.zaehlung(h)
            assertTrue(rot <= mit && mit == h.werte.size)
        }
        fun hoch(j: Int) = d.blasen.first { jahr(it.hoch) == j }
        assertEquals(Ampel.ROT, ZeichenRueckblick.ampel(hoch(2000).werte.first { it.id == Blasen.CAPE }))
        assertEquals(Ampel.ROT, ZeichenRueckblick.ampel(hoch(1929).werte.first { it.id == Blasen.CAPE }))
        // 1929 gab es weder Finanzierungsrechnung noch die ETFs
        assertTrue(ZeichenRueckblick.ohneDaten(hoch(1929)).containsAll(listOf(Blasen.BUFFETT, Blasen.MARGIN, Blasen.BREITE, Blasen.IPO)))
        assertEquals(d, ZeichenRueckblick.lesen(ZeichenRueckblick.schreiben(d)))
        assertNull(ZeichenRueckblick.lesen("{}"))
        // Eintraege der gepflegten Datei
        assertTrue(Datenbuendel.SCHEMA.containsKey(ZeichenRueckblick.DATEI))
        assertTrue(ZeichenRueckblick.DATEI in Alter.dateien())
        assertTrue(de.troetenrepublik.app.market.Daten.PRUEFER.containsKey(ZeichenRueckblick.DATEI))
        assertTrue(asset("../../../../tools/daten/dateien.json").readText().contains("\"" + ZeichenRueckblick.DATEI + "\""))
    }

    @Test fun texteInBeidenSprachen() {
        val schluessel = listOf(
            "zr.von", "zr.keine", "zr.b.kern", "zr.b.satz", "zr.b.fall", "zr.b.danach", "zr.b.phasen", "zr.b.fehl", "zr.b.treffer", "zr.b.liste",
            "zr.b.info", "zr.h.kern", "zr.h.satz", "zr.h.fall", "zr.h.davor", "zr.h.ohne", "zr.h.info",
            "zr.name.1929", "zr.name.1973", "zr.name.1987", "zr.name.2000", "zr.name.2007", "zr.name.2020", "zr.name.2022",
        )
        for (k in schluessel) for (deutsch in listOf(true, false)) {
            val t = MarktzeichenTexte.text(deutsch, k, listOf("A", "B", "C", "D"))
            assertTrue("$k $deutsch", t.isNotBlank())
            assertFalse("$k Platzhalter", t.contains("{"))
            assertFalse("$k Strich", t.contains("–") || t.contains("—"))
            assertFalse(k, t.contains("Burggraben", ignoreCase = true) || t.contains("moat", ignoreCase = true))
        }
        assertEquals("Letzter Boden: Oktober 2022", MarktzeichenTexte.text(true, "zr.b.kern", listOf("Oktober 2022")))
        assertEquals("2 von 4 Zeichen erfüllt, 16 Böden seit 1932", MarktzeichenTexte.text(true, "zr.b.satz", listOf("2", "4", "16", "1932")))
        // Hochs ohne eigenen Namen: leer
        assertEquals("", MarktzeichenTexte.text(true, "zr.name.1961", emptyList()))
    }
}
