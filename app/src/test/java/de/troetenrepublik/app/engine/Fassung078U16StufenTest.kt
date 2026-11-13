package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.AnalystenStufen.Kreis
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.StufenTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.78: Werkzeug „Neue Analystenstufen“ mit Gruppen je Unternehmen, Zeitraum, Eingrenzung und Auswahl der Werte.
class Fassung078U16StufenTest {

    private val tag = 86_400_000L
    private val jetzt = 400 * tag

    private fun u(haus: String, vorTagen: Int, aktion: String, nach: String, von: String, ziel: Double = 0.0, vorher: Double = 0.0) =
        Analystenbilanz.Urteil(haus, jetzt - vorTagen * tag, nach, von, aktion, ziel, vorher)

    private val urteile = mapOf(
        "AAA" to listOf(
            u("X", 2, "down", "Hold", "Buy", 185.0, 315.0), u("Y", 20, "up", "Buy", "Hold", 200.0), u("X", 50, "up", "Buy", "Hold", 300.0),
            u("Z", 5, "main", "Buy", "Buy", 999.0), u("W", 200, "down", "Sell", "Hold"),
        ),
        "BBB" to listOf(u("X", 1, "up", "Buy", "Hold"), u("Y", 400, "down", "Hold", "Buy")),
        "CCC" to listOf(u("X", 100, "init", "Buy", "")),
        "DDD" to emptyList(),
    )

    @Test fun zeitraeumeUndVorgabe() {
        assertEquals(listOf(7, 30, 90, 365), AnalystenStufen.ZEITRAEUME)
        assertTrue(AnalystenStufen.ZEITRAUM_VORGABE in AnalystenStufen.ZEITRAEUME)
        assertEquals(30, AnalystenStufen.ZEITRAUM_VORGABE)
    }

    @Test fun buendeltJeUnternehmenNeuesteZuerst() {
        val g = AnalystenStufen.buendeln(urteile, jetzt, 30)
        // BBB hat die juengste Stufe, danach AAA; CCC und DDD haben im Zeitraum nichts
        assertEquals(listOf("BBB", "AAA"), g.map { it.symbol })
        val a = g[1]
        assertEquals(listOf("X", "Y"), a.eintraege.map { it.haus })
        assertEquals(1, a.hoch)
        assertEquals(1, a.runter)
        assertEquals(jetzt - 2 * tag, a.juengste)
        // Bestaetigungen zaehlen nicht, auch nicht beim Kursziel
        assertEquals(192.5, a.zielMittel, 1e-9)
        assertEquals(2, a.zielZahl)
    }

    @Test fun zeitraumFiltert() {
        assertEquals(listOf("BBB", "AAA"), AnalystenStufen.buendeln(urteile, jetzt, 7).map { it.symbol })
        assertEquals(1, AnalystenStufen.buendeln(urteile, jetzt, 7)[1].eintraege.size)
        val jahr = AnalystenStufen.buendeln(urteile, jetzt, 365)
        assertEquals(listOf("BBB", "AAA", "CCC"), jahr.map { it.symbol })
        assertEquals(listOf(2, 20, 50, 200), jahr[1].eintraege.map { ((jetzt - it.zeit) / tag).toInt() })
        // Stufe vor 400 Tagen liegt ausserhalb
        assertEquals(1, jahr[0].eintraege.size)
        // Juengstes Ziel je Haus: X 185 (nicht 300), Y 200
        assertEquals(192.5, jahr[1].zielMittel, 1e-9)
    }

    @Test fun zaehlerFolgtZeitraumUndEingrenzung() {
        assertEquals(2 to 1, AnalystenStufen.zaehler(AnalystenStufen.buendeln(urteile, jetzt, 30)))
        assertEquals(1 to 1, AnalystenStufen.zaehler(AnalystenStufen.buendeln(urteile, jetzt, 7)))
        assertEquals(4 to 2, AnalystenStufen.zaehler(AnalystenStufen.buendeln(urteile, jetzt, 365)))
        val nurDepot = AnalystenStufen.erlaubt(Kreis.DEPOT, listOf("AAA"), listOf("BBB"))
        assertEquals(1 to 1, AnalystenStufen.zaehler(AnalystenStufen.buendeln(urteile, jetzt, 30, nurDepot)))
        val nurWatch = AnalystenStufen.erlaubt(Kreis.WATCHLIST, listOf("AAA"), listOf("BBB"))
        assertEquals(listOf("BBB"), AnalystenStufen.buendeln(urteile, jetzt, 30, nurWatch).map { it.symbol })
        assertNull(AnalystenStufen.erlaubt(Kreis.ALLE, listOf("AAA"), listOf("BBB")))
        assertEquals(0 to 0, AnalystenStufen.zaehler(emptyList()))
    }

    @Test fun gesuchteOhneStufeStehenVorn() {
        val g = AnalystenStufen.buendeln(urteile, jetzt, 30, immer = listOf("DDD", "AAA", "ZZZ"))
        // DDD ist geladen und leer, AAA hat Stufen und bleibt einsortiert, ZZZ ist noch nicht geladen
        assertEquals(listOf("DDD", "BBB", "AAA"), g.map { it.symbol })
        assertTrue(g[0].eintraege.isEmpty())
        assertEquals(0.0, g[0].zielMittel, 0.0)
        // Mit Eingrenzung entfaellt der gesuchte Wert ausserhalb
        assertEquals(listOf("AAA"), AnalystenStufen.buendeln(urteile, jetzt, 30, setOf("AAA"), listOf("DDD")).map { it.symbol })
    }

    @Test fun gleicheZeitNachSymbol() {
        val m = mapOf("B" to listOf(u("X", 3, "up", "Buy", "Hold")), "A" to listOf(u("X", 3, "down", "Hold", "Buy")))
        assertEquals(listOf("A", "B"), AnalystenStufen.buendeln(m, jetzt, 30).map { it.symbol })
    }

    @Test fun auswahlMitObergrenze() {
        val grosse = (1..200).map { "G$it" }
        val a = AnalystenStufen.auswahl(listOf("S1"), listOf("D1", "D2"), listOf("D2", "W1", "G1"), grosse)
        assertEquals(AnalystenStufen.OBERGRENZE, a.size)
        assertEquals(listOf("S1", "D1", "D2", "W1", "G1", "G2"), a.take(6))
        assertEquals(a.size, a.distinct().size)
        // Depot und Watchlist zusammen hoechstens EIGENE_MAX, gesuchte hoechstens GEZIELT_MAX
        val viele = AnalystenStufen.auswahl((1..50).map { "S$it" }, (1..100).map { "D$it" }, (1..100).map { "W$it" }, grosse)
        assertEquals(AnalystenStufen.OBERGRENZE, viele.size)
        assertEquals(AnalystenStufen.GEZIELT_MAX, viele.count { it.startsWith("S") })
        assertEquals(AnalystenStufen.EIGENE_MAX, viele.count { it.startsWith("D") || it.startsWith("W") })
        assertEquals(AnalystenStufen.OBERGRENZE - AnalystenStufen.GEZIELT_MAX - AnalystenStufen.EIGENE_MAX, viele.count { it.startsWith("G") })
        assertEquals(listOf("D1"), AnalystenStufen.auswahl(emptyList(), listOf("D1"), emptyList(), emptyList()))
        assertEquals(3, AnalystenStufen.auswahl(emptyList(), listOf("D1"), emptyList(), grosse, 3).size)
        assertTrue(AnalystenStufen.auswahl(emptyList(), listOf("D1"), emptyList(), grosse, 0).isEmpty())
    }

    @Test fun bisherigeAufruferUnveraendert() {
        // Nachrichtenseite: weiter sieben Tage ohne Angabe
        assertEquals(7, AnalystenStufen.TAGE)
        val e = AnalystenStufen.ordnen(AnalystenStufen.neue("AAA", urteile.getValue("AAA"), jetzt))
        assertEquals(listOf("X"), e.map { it.haus })
    }

    @Test fun texteInBeidenSprachen() {
        assertEquals(StufenTexte.schluessel(true), StufenTexte.schluessel(false))
        for (k in StufenTexte.schluessel(true)) {
            for (t in listOf(De.su(k, listOf("1", "2")), En.su(k, listOf("1", "2")))) {
                assertTrue(k, t.isNotBlank())
                assertFalse(k, t.contains("{"))
                assertFalse(k, t.contains("–") || t.contains("—") || t.contains("?"))
            }
        }
        assertEquals("30 Tage", De.su("tage", listOf("30")))
        assertEquals("Lädt 3 von 120", De.su("laedt", listOf("3", "120")))
        assertEquals("Loading 3 of 120", En.su("laedt", listOf("3", "120")))
    }
}
