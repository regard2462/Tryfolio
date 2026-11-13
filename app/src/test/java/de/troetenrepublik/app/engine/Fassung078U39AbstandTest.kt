package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.AbstandTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Abstand von US-Indexfonds zu ihrem Index (Form N-CEN der SEC): Leser, Kernzahlen und Verteilung an einem Ausschnitt
// der Buendeldatei, dazu die Pflege der ganzen Datei.
class Fassung078U39AbstandTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val ausschnitt by lazy {
        IndexAbstand.lesen(javaClass.classLoader!!.getResourceAsStream("abstand/etf-abstand-ausschnitt.json")!!.readBytes().decodeToString())!!
    }

    @Test fun liestFondsUndJahre() {
        val d = ausschnitt
        assertEquals("2026-11-12", d.stand)
        val spy = IndexAbstand.fuer(d, "spy")!!
        assertEquals(12, spy.monat)
        assertEquals(2018, spy.jahre.first().jahr)
        assertEquals(-0.09, spy.jahre.first().nach, 1e-9)
        assertEquals(0.0, spy.jahre.first().vor!!, 1e-9)
        assertEquals(2025, spy.jahre.last().jahr)
        // Geschaeftsjahr endet im Maerz: das Jahr ist das Kalenderjahr des Endes
        val ivv = IndexAbstand.fuer(d, "IVV")!!
        assertEquals(3, ivv.monat)
        assertEquals(2019, ivv.jahre.first().jahr)
        assertEquals(2026, ivv.jahre.last().jahr)
    }

    @Test fun nurKuerzelOhneBoersenzusatz() {
        val d = ausschnitt
        assertNotNull(IndexAbstand.fuer(d, "VOO"))
        assertNull(IndexAbstand.fuer(d, "VOO.L"))
        assertNull(IndexAbstand.fuer(d, "^SPY"))
        assertNull(IndexAbstand.fuer(d, "XXXX"))
        assertNull(IndexAbstand.fuer(d, ""))
    }

    @Test fun kernzahlen() {
        val voo = IndexAbstand.fuer(ausschnitt, "VOO")!!
        val k = IndexAbstand.kern(voo)
        assertEquals(8, k.jahre)
        assertEquals(2018, k.von)
        assertEquals(2025, k.bis)
        // Mittel nach Kosten: (-14 -3 -15 -17 -12 -18 -18 -17) / 8 Hundertstel
        assertEquals(-0.1425, k.mittel, 1e-9)
        assertEquals(0.0025, k.mittelVor!!, 1e-9)
        assertEquals(0.145, k.kosten!!, 1e-9)
    }

    @Test fun luecke_und_fehlendeWerte() {
        val text = """{"stand":"2026-11-12","grenzen":[-50,0,50],"verteilung":[{"j":2025,"n":4,"m":-10,"mv":null,"k":[1,2,1,0]}],
            "fonds":[{"t":["AAA"],"n":"Fonds A","m":6,"ab":2020,"v":[null,null,5],"a":[-20,null,-10]}]}"""
        val d = IndexAbstand.lesen(text)!!
        val f = IndexAbstand.fuer(d, "AAA")!!
        assertEquals(listOf(2020, 2022), f.jahre.map { it.jahr })
        assertNull(f.jahre[0].vor)
        val k = IndexAbstand.kern(f)
        assertEquals(-0.15, k.mittel, 1e-9)
        assertEquals(0.05, k.mittelVor!!, 1e-9)
        assertEquals(0.15, k.kosten!!, 1e-9)
        assertNull(d.verteilung[0].medianVor)
        assertEquals(0.75, IndexAbstand.anteilDahinter(d, d.verteilung[0]), 1e-9)
    }

    @Test fun unlesbaresWirdAbgelehnt() {
        assertNull(IndexAbstand.lesen(""))
        assertNull(IndexAbstand.lesen("{}"))
        // Klassenzahl passt nicht zu den Grenzen: keine Verteilung, also keine Datei
        assertNull(IndexAbstand.lesen("""{"stand":"2026-11-12","grenzen":[-50,0],"verteilung":[{"j":2025,"n":4,"m":0,"k":[2,2]}],"fonds":[{"t":["A"],"n":"A","ab":2020,"v":[1],"a":[1]}]}"""))
        // Grenzen nicht aufsteigend
        assertNull(IndexAbstand.lesen("""{"stand":"2026-11-12","grenzen":[0,-50],"verteilung":[{"j":2025,"n":4,"m":0,"k":[2,1,1]}],"fonds":[{"t":["A"],"n":"A","ab":2020,"v":[1],"a":[1]}]}"""))
    }

    @Test fun verteilung() {
        val d = ausschnitt
        // 2026 ist unvollstaendig (757 Fonds), das volle Jahr ist 2025
        val v = IndexAbstand.vollesJahr(d)
        assertEquals(2025, v.jahr)
        assertEquals(2144, v.anzahl)
        assertEquals(-0.18, v.median, 1e-9)
        // Klassen bis zur Grenze 0: 47 + 92 + 375 + 403 + 394 + 402 von 2144
        assertEquals(1713.0 / 2144.0, IndexAbstand.anteilDahinter(d, v), 1e-9)
        assertEquals(0, IndexAbstand.klasse(d, -3.0))
        assertEquals(5, IndexAbstand.klasse(d, -0.05))
        assertEquals(6, IndexAbstand.klasse(d, 0.0))
        assertEquals(10, IndexAbstand.klasse(d, 4.0))
    }

    @Test fun ganzeDateiIstGepflegt() {
        val n = IndexAbstand.DATEI
        val text = asset(n).readText(Charsets.UTF_8)
        val d = IndexAbstand.lesen(text)!!
        assertTrue(n in Alter.dateien())
        assertEquals(1, Datenbuendel.SCHEMA[n])
        assertTrue(asset(n).length() < 400_000)
        assertTrue(d.fonds.size > 2000)
        assertTrue(d.verteilung.size >= 8)
        // Vor Kosten liegt der Median der Indexfonds nahe null, nach Kosten darunter
        val v = IndexAbstand.vollesJahr(d)
        assertTrue(kotlin.math.abs(v.medianVor!!) < 0.05)
        assertTrue(v.median < 0 && v.median > -0.5)
        assertTrue(IndexAbstand.anteilDahinter(d, v) in 0.6..0.95)
        val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
        assertEquals(text, im.readText(Charsets.UTF_8))
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, text))
    }

    @Test fun texteInBeidenSprachen() {
        assertEquals(AbstandTexte.schluessel(true), AbstandTexte.schluessel(false))
        for (de in listOf(true, false)) for (k in AbstandTexte.schluessel(de)) {
            val t = AbstandTexte.text(de, k, listOf("a", "b", "c", "d"))
            assertTrue(k, t.isNotBlank() && !t.contains("{") && !t.contains("–") && !t.contains("—"))
        }
    }

    @Test fun nennungInDerQuellenliste() {
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("etf-abstand.json: U.S. Securities and Exchange Commission, Form N-CEN Data Sets"))
    }
}
