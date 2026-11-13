package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.FinderRueckblickMessung.Stichtag
import de.troetenrepublik.app.i18n.FinderZufallTexte
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.pow

// Fassung 0.78 U40: Zufallsdepots als Vergleich im Rueckblick des Aktienfinders.
class Fassung078U40ZufallTest {

    // 24 Monate, 50 Werte: Wert i bringt jeden Monat 0,1 % * i (0 bis 4,9 %)
    private val monate = List(24) { List(50) { 0.001 * it } }

    @Test
    fun festerStartwertGleichesErgebnis() {
        val a = Zufallsdepots.jahresrenditen(monate, 20, 200)
        val b = Zufallsdepots.jahresrenditen(monate, 20, 200)
        assertArrayEquals(a, b, 0.0)
        assertEquals(200, a.size)
        val c = Zufallsdepots.jahresrenditen(monate, 20, 200, startwert = 79L)
        assertFalse(a.contentEquals(c))
        // Die Eingabe bleibt unveraendert
        assertEquals(0.049, monate[0][49], 1e-12)
    }

    @Test
    fun grenzenDerZufallsdepots() {
        val r = Zufallsdepots.jahresrenditen(monate, 20, 500)
        // Jede Monatsrendite liegt zwischen dem Schnitt der 20 schlechtesten (0,95 %) und der 20 besten (3,95 %)
        val unten = 1.0095.pow(12) - 1
        val oben = 1.0395.pow(12) - 1
        assertTrue(r.all { it > unten && it < oben })
        // Der Schnitt liegt beim Schnitt des Universums (2,45 % im Monat)
        assertEquals(1.0245.pow(12) - 1, r.average(), 0.01)
        // Depot so gross wie das Universum: immer genau das Universum
        val alle = Zufallsdepots.jahresrenditen(monate, 50, 10)
        assertTrue(alle.all { kotlin.math.abs(it - (1.0245.pow(12) - 1)) < 1e-9 })
        // Groesser als das Universum: nimmt alle Werte
        assertEquals(alle[0], Zufallsdepots.jahresrenditen(monate, 80, 1)[0], 1e-12)
        assertEquals(0, Zufallsdepots.jahresrenditen(emptyList(), 20).size)
        assertEquals(0, Zufallsdepots.jahresrenditen(listOf(emptyList()), 20).size)
    }

    @Test
    fun verteilungGegenAuswahl() {
        // Auswahl: die 20 besten Werte jeden Monat, besser als jedes Zufallsdepot
        val beste = List(24) { 0.0395 }
        val v = Zufallsdepots.verteilung(monate, beste, 20)!!
        assertEquals(Zufallsdepots.ANZAHL, v.anzahl)
        assertEquals(20, v.groesse)
        assertEquals(24, v.monate)
        assertEquals(1.0, v.besserAls, 0.0)
        assertEquals(1.0395.pow(12) - 1, v.auswahl, 1e-12)
        assertEquals(Zufallsdepots.STUFEN + 1, v.quantile.size)
        assertEquals(v.quantile.sorted(), v.quantile)
        assertTrue(v.quantile.first() < v.median && v.median < v.quantile.last())
        assertEquals(v.quantile[1], v.quantil(0.05), 0.0)
        assertEquals(v.quantile[19], v.quantil(0.95), 0.0)
        // Auswahl in Hoehe des Universums: etwa die Haelfte der Zufallsdepots liegt darunter
        val mitte = Zufallsdepots.verteilung(monate, List(24) { 0.0245 }, 20)!!
        assertEquals(0.5, mitte.besserAls, 0.08)
        // Die schlechtesten 20: besser als keines
        assertEquals(0.0, Zufallsdepots.verteilung(monate, List(24) { 0.0095 }, 20)!!.besserAls, 0.0)
        // Unter zwoelf Monaten und bei ungleicher Laenge keine Verteilung
        assertNull(Zufallsdepots.verteilung(monate.take(11), beste.take(11), 20))
        assertNull(Zufallsdepots.verteilung(monate, beste.take(23), 20))
    }

    @Test
    fun inDerZusammenfassungDesRueckblicks() {
        // 14 Stichtage mit 60 Werten: die Punkte im Trend steigen mit der spaeteren Rendite, die Top 20 sind die besten
        val horizonte = listOf(1, 3, 6, 12)
        val st = (0 until 14).map { m ->
            val tag = LocalDate.of(2020, 1, 31).plusMonths(m.toLong()).toEpochDay()
            val werte = (0 until 60).map { i ->
                FinderRueckblickMessung.Wert("S$i", mapOf(Aktienfinder.Bereich.TREND to i.toDouble()), horizonte.associateWith { 0.0005 * i })
            }
            Stichtag(tag, werte, horizonte.associateWith { 0.01 }, horizonte.associateWith { 0.01 }, 60)
        }
        val g = mapOf(Aktienfinder.Bereich.TREND to 1.0)
        val z = FinderRueckblickMessung.zufall(st, g)
        assertNotNull(z)
        assertEquals(14, z!!.monate)
        assertEquals(FinderRueckblickMessung.TOP, z.groesse)
        assertEquals(1.0, z.besserAls, 0.0)
        val d = FinderRueckblickMessung.zusammenfassen(st, g, "2026-11-12", false, universum = "ndx100")
        assertEquals(z, d.zufall)
        assertEquals("ndx100", d.universum)
        // Auswahl vor Kosten wie im monatlichen Umschichten
        assertEquals(d.strategie!!.top20, d.zufall!!.auswahl, 1e-12)
        assertEquals(d, FinderRueckblickMessung.lesen(FinderRueckblickMessung.schreiben(d)))
        // Aeltere Datei ohne Zufallsdepots
        val alt = FinderRueckblickMessung.lesen("""{"stand":"2026-11-12","stichtage":166}""")!!
        assertNull(alt.zufall)
        assertEquals("", alt.universum)
        // Zu wenige Stichtage
        assertNull(FinderRueckblickMessung.zufall(st.take(5), g))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(FinderZufallTexte.schluessel(true), FinderZufallTexte.schluessel(false))
        for (k in FinderZufallTexte.schluessel(true)) for (d in listOf(true, false)) {
            val text = FinderZufallTexte.text(d, k, listOf("1", "2", "3", "4", "5"))
            assertTrue(k, text.isNotBlank())
            assertFalse(k, text.contains("{"))
            listOf("—", "–", "…").forEach { assertFalse("$k: $it", text.contains(it)) }
        }
    }
}
