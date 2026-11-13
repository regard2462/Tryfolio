package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.LangeReihen.Anlage
import de.troetenrepublik.app.engine.LangeReihen.Jahre
import de.troetenrepublik.app.i18n.LangeSichtTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Lange Sicht im Vergleich: Auswahl, gemeinsamer Zeitraum, Haltedauern, Skala und Ausweichen der Beschriftungen.
class Fassung078U47Test {
    private val namen = LangeReihen.VORGABEN.map { it.name }

    private val anlagen = mapOf(
        Anlage.AKTIEN_USA to Jahre(1900, List(40) { 0.04 + (it % 5) * 0.03 - 0.05 }),
        Anlage.ANLEIHEN to Jahre(1920, List(20) { 0.02 }),
    )

    private fun mix(aktien: Double, anleihen: Double) =
        LangeReihen.mischung(anlagen, mapOf(Anlage.AKTIEN_USA to aktien, Anlage.ANLEIHEN to anleihen))

    // ------------------------------------------------------------ Auswahl

    @Test fun vorgabeUndLesen() {
        assertEquals(listOf("A100", "A60"), LangeVergleich.lesen("", namen))
        assertEquals(listOf("A100", "A60"), LangeVergleich.lesen("unbekannt", namen))
        assertEquals(listOf("A40", "A80"), LangeVergleich.lesen("A40, A80", namen))
        assertEquals(listOf("A40"), LangeVergleich.lesen("A40,A40", namen))
        // nie mehr als MAX, die zuletzt genannten bleiben
        assertEquals(listOf("A80", "A60", "A40"), LangeVergleich.lesen("A100,A80,A60,A40", namen))
        // ohne die Vorgabenamen (zum Beispiel ohne Gold) greift die erste gueltige
        assertEquals(listOf("A40"), LangeVergleich.standard(listOf("A40", "A80")))
        assertEquals(listOf("A100"), LangeVergleich.standard(listOf("A100", "A80")))
        assertEquals(emptyList<String>(), LangeVergleich.standard(emptyList()))
    }

    @Test fun umschalten() {
        val a = listOf("A100", "A60")
        assertEquals(listOf("A100", "A60", "A40"), LangeVergleich.umschalten(a, "A40"))
        assertEquals(listOf("A60"), LangeVergleich.umschalten(a, "A100"))
        // die letzte Wahl bleibt
        assertEquals(listOf("A60"), LangeVergleich.umschalten(listOf("A60"), "A60"))
        // vierter Tipp ersetzt die aelteste Wahl
        assertEquals(listOf("A60", "A40", "A80"), LangeVergleich.umschalten(listOf("A100", "A60", "A40"), "A80"))
        // schreiben und lesen sind umkehrbar
        val l = listOf("A80", "GOLD10", "VIER")
        assertEquals(l, LangeVergleich.lesen(LangeVergleich.schreiben(l), namen))
    }

    // ------------------------------------------------------------ Gemeinsamer Zeitraum

    @Test fun zuschneiden() {
        val a100 = mix(1.0, 0.0)!!
        val a60 = mix(0.6, 0.4)!!
        assertEquals(1900, a100.von)
        assertEquals(1939, a100.bis)
        assertEquals(1920, a60.von)
        val z = LangeVergleich.zuschneiden(listOf(a100, a60))!!
        assertEquals(listOf(1920, 1920), z.map { it.von })
        assertEquals(listOf(1939, 1939), z.map { it.bis })
        assertEquals(listOf(21, 21), z.map { it.verlauf.size })
        assertEquals(1.0, z[0].verlauf[0], 0.0)
        // der Zuschnitt rechnet ab dem gemeinsamen Start neu
        var v = 1.0
        for (j in 1920..1939) v *= 1.0 + a100.renditen[j - 1900]
        assertEquals(v, z[0].verlauf.last(), 1e-9)
        // die schmalere Mischung bleibt dieselbe
        assertTrue(z[1] === a60)
        // eine einzelne Mischung bleibt unveraendert
        assertTrue(LangeVergleich.zuschneiden(listOf(a100))!![0] === a100)
        assertNull(LangeVergleich.zuschneiden(emptyList()))
    }

    @Test fun zuschnittAusserhalbUndOhneGemeinsames() {
        val a100 = mix(1.0, 0.0)!!
        assertNull(LangeReihen.zuschnitt(a100, 1890, 1900))
        assertNull(LangeReihen.zuschnitt(a100, 1930, 1920))
        assertNull(LangeReihen.zuschnitt(a100, 1930, 1950))
        val frueh = LangeReihen.ausRenditen(1900, DoubleArray(5) { 0.03 })!!
        val spaet = LangeReihen.ausRenditen(1950, DoubleArray(5) { 0.03 })!!
        assertNull(LangeVergleich.zuschneiden(listOf(frueh, spaet)))
        assertEquals(1904, frueh.bis)
        assertNull(LangeReihen.ausRenditen(1900, DoubleArray(0)))
    }

    // ------------------------------------------------------------ Haltedauern und Tabellenwerte

    @Test fun haltedauern() {
        val z = LangeVergleich.zuschneiden(listOf(mix(1.0, 0.0)!!, mix(0.6, 0.4)!!))!!
        // 20 Jahre Zuschnitt: 30 Jahre fehlen bei beiden
        assertEquals(listOf(1, 5, 10, 20), LangeVergleich.gemeinsameDauern(z))
        assertEquals(10, LangeVergleich.waehleDauer(listOf(1, 5, 10, 20), 10))
        assertEquals(10, LangeVergleich.waehleDauer(listOf(1, 5, 10, 20), 30))
        assertEquals(5, LangeVergleich.waehleDauer(listOf(1, 5), 30))
        assertNull(LangeVergleich.waehleDauer(emptyList(), 10))
        val h = LangeVergleich.halte(z[0], 10)!!
        assertEquals(11, h.faelle)
        assertTrue(h.verlust in 0.0..1.0)
        assertNull(LangeVergleich.halte(z[0], 30))
        // gemeinsame Dauern ohne gemeinsame Zeitraeume gibt es auch nicht fuer eine kuerzere Mischung allein
        val kurz = LangeReihen.ausRenditen(1900, DoubleArray(6) { 0.01 })!!
        assertEquals(listOf(1, 5), LangeVergleich.gemeinsameDauern(listOf(kurz, z[0])))
    }

    @Test fun renditeUndRueckgangNachZuschnitt() {
        val a100 = mix(1.0, 0.0)!!
        val z = LangeVergleich.zuschneiden(listOf(a100, mix(0.6, 0.4)!!))!!
        val erwartet = (Math.pow(z[0].verlauf.last(), 1.0 / 20) - 1.0) * 100.0
        assertEquals(erwartet, z[0].rendite, 1e-9)
        val r = z[0].rueckgang
        if (r != null) {
            assertTrue(r.hoch >= z[0].von - 1 && r.tief <= z[0].bis)
            val e = r.erholt
            if (e != null) assertEquals(e - r.hoch, r.jahreBisErholt!!)
        }
    }

    // ------------------------------------------------------------ Skala und Ausweichen

    @Test fun logSkala() {
        val s = LangeVergleich.logSkala(listOf(doubleArrayOf(1.0, 10.0), doubleArrayOf(1.0, 100.0)))!!
        assertEquals(0.0, s.n(1.0), 1e-12)
        assertEquals(1.0, s.n(100.0), 1e-12)
        assertEquals(0.5, s.n(10.0), 1e-12)
        assertNull(LangeVergleich.logSkala(listOf(doubleArrayOf(1.0, 1.0))))
        assertNull(LangeVergleich.logSkala(emptyList()))
        val rand = 0.1f
        assertEquals(0.1f, LangeVergleich.yBruch(1.0, rand), 1e-6f)
        assertEquals(0.9f, LangeVergleich.yBruch(0.0, rand), 1e-6f)
    }

    private fun pruefeAusweichen(mitten: List<Float>, hoehen: List<Float>, gesamt: Float) {
        val oben = LangeVergleich.ausweichen(mitten, hoehen, gesamt)
        assertEquals(mitten.size, oben.size)
        for (i in oben.indices) {
            assertTrue("innerhalb oben", oben[i] >= 0f)
            assertTrue("innerhalb unten", oben[i] + hoehen[i] <= gesamt + 1e-3f)
            for (j in oben.indices) if (i != j && mitten[i] < mitten[j]) {
                assertTrue("keine Ueberlappung", oben[i] + hoehen[i] <= oben[j] + 1e-3f)
            }
        }
    }

    @Test fun ausweichen() {
        // weit auseinander: jede Beschriftung steht mittig auf ihrer Linie
        val frei = LangeVergleich.ausweichen(listOf(20f, 70f, 120f), listOf(20f, 20f, 20f), 140f)
        assertEquals(listOf(10f, 60f, 110f), frei)
        // dicht beieinander, in beliebiger Reihenfolge
        pruefeAusweichen(listOf(60f, 62f, 64f), listOf(30f, 30f, 30f), 140f)
        pruefeAusweichen(listOf(64f, 60f, 62f), listOf(30f, 36f, 30f), 140f)
        // am oberen und unteren Rand
        pruefeAusweichen(listOf(2f, 3f, 4f), listOf(30f, 30f, 30f), 140f)
        pruefeAusweichen(listOf(138f, 139f, 140f), listOf(30f, 30f, 30f), 140f)
        // gleiche Lage
        pruefeAusweichen(listOf(70f, 70f), listOf(30f, 30f), 140f)
        // die Rangfolge der Linien bleibt: wer hoeher liegt, steht hoeher
        val o = LangeVergleich.ausweichen(listOf(100f, 95f, 98f), listOf(30f, 30f, 30f), 140f)
        assertTrue(o[1] < o[2] && o[2] < o[0])
        assertEquals(emptyList<Float>(), LangeVergleich.ausweichen(emptyList(), emptyList(), 140f))
    }

    // ------------------------------------------------------------ Texte

    @Test fun texte() {
        assertEquals(LangeSichtTexte.schluessel(true), LangeSichtTexte.schluessel(false))
        for (k in listOf("leer.mehr", "t.erholt", "t.offen", "t.verlust", "t.zeitraum", "halte.kurz.mehr")) {
            assertTrue(k, k in LangeSichtTexte.schluessel(true))
            for (d in listOf(true, false)) {
                val t = LangeSichtTexte.text(d, k, listOf("1", "2", "3", "4"))
                assertTrue(k, t.isNotBlank() && !t.contains('{') && !t.contains('–') && !t.contains('—') && !t.contains('…'))
            }
        }
        assertEquals("10 Jahre: realer Verlust, 60/40 4 %", LangeSichtTexte.text(true, "halte.kurz.mehr", listOf("10 Jahre", "60/40 4 %")))
        assertEquals("1915 to 1920", LangeSichtTexte.text(false, "t.zeitraum", listOf("1915", "1920")))
    }
}
