package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Eigene Kategorien mit Zielgewichten (0.76): Ziele je Kategorie, Orders, Nur zukaufen, Ziele ohne 100 %,
// Positionen ohne Kategorie, Speichern und Laden.
class Fassung076YTest {

    private val werte = listOf("a" to 500.0, "b" to 300.0, "c" to 200.0)

    private fun stand(vararg ziele: Double, zuordnung: Map<String, String>): KategorienStand =
        KategorienStand(ziele.mapIndexed { i, z -> Kategorie("k${i + 1}", "K${i + 1}", z) }, zuordnung, vorgeschlagen = true)

    @Test(timeout = 5000)
    fun zieleJeKategorieUndOrders() {
        // k1 = a und b (Wert 800), k2 = c (200); Ziel 50 zu 50
        val s = stand(50.0, 50.0, zuordnung = mapOf("a" to "k1", "b" to "k1", "c" to "k2"))
        val p = Kategorien.plan(s, werte, 0.0, true)
        val k1 = p.zeilen.first { it.id == "k1" }
        assertEquals(0.8, k1.ist, 1e-9)
        assertEquals(0.5, k1.ziel, 1e-9)
        assertEquals(300.0, k1.abweichungEuro, 1e-9)
        assertEquals(0.3, k1.abweichung, 1e-9)
        // innerhalb k1 im Verhaeltnis 5 zu 3: Ziel 500 gesamt
        val o = p.orders.associateBy { it.key }
        assertEquals(500.0 * 5 / 8 - 500.0, o.getValue("a").amount, 1e-9)
        assertEquals(500.0 * 3 / 8 - 300.0, o.getValue("b").amount, 1e-9)
        assertEquals(300.0, o.getValue("c").amount, 1e-9)
        assertEquals(0.0, p.orders.sumOf { it.amount }, 1e-9)
    }

    @Test(timeout = 5000)
    fun nurZukaufenVerteiltNeuesGeldDortWoDasZielFehlt() {
        val s = stand(50.0, 50.0, zuordnung = mapOf("a" to "k1", "b" to "k1", "c" to "k2"))
        // Depot 1000 plus 1000 Neugeld: Ziel je Kategorie 1000, k1 braucht 200 (a 125, b 75), k2 braucht 800
        val p = Kategorien.plan(s, werte, 1000.0, false)
        val o = p.orders.associateBy { it.key }
        assertEquals(125.0, o.getValue("a").amount, 1e-9)
        assertEquals(75.0, o.getValue("b").amount, 1e-9)
        assertEquals(800.0, o.getValue("c").amount, 1e-9)
        assertTrue(p.orders.none { it.amount < 0 })
        // Reicht das Geld nicht, wird anteilig gekauft
        val knapp = Kategorien.plan(s, werte, 400.0, false)
        // Ziel je Kategorie 700: k1 hat 800, k2 braucht 500, Geld 400
        assertEquals(400.0, knapp.orders.sumOf { it.amount }, 1e-9)
        assertEquals(400.0, knapp.orders.first { it.key == "c" }.amount, 1e-9)
    }

    @Test(timeout = 5000)
    fun nurZukaufenVerteiltInnerhalbDerKategorieNachWert() {
        val s = stand(100.0, zuordnung = mapOf("a" to "k1", "b" to "k1", "c" to "k1"))
        val p = Kategorien.plan(s, werte, 100.0, false)
        assertEquals(50.0, p.orders.first { it.key == "a" }.amount, 1e-9)
        assertEquals(30.0, p.orders.first { it.key == "b" }.amount, 1e-9)
        assertEquals(20.0, p.orders.first { it.key == "c" }.amount, 1e-9)
    }

    @Test(timeout = 5000)
    fun zieleOhneHundertProzentRechnenImVerhaeltnis() {
        val a = Kategorien.plan(stand(70.0, 30.0, zuordnung = mapOf("a" to "k1", "b" to "k1", "c" to "k2")), werte, 0.0, true)
        val b = Kategorien.plan(stand(7.0, 3.0, zuordnung = mapOf("a" to "k1", "b" to "k1", "c" to "k2")), werte, 0.0, true)
        a.orders.zip(b.orders).forEach { (x, y) -> assertEquals(x.amount, y.amount, 1e-9) }
        assertEquals(0.7, a.zeilen.first { it.id == "k1" }.ziel, 1e-9)
        assertEquals(10.0, Kategorien.summeZiele(stand(7.0, 3.0, zuordnung = emptyMap())), 1e-9)
        assertEquals(60.0, Kategorien.summeZiele(stand(40.0, 20.0, zuordnung = emptyMap())), 1e-9)
    }

    @Test(timeout = 5000)
    fun positionOhneKategorieBleibtUnveraendert() {
        val s = stand(60.0, 40.0, zuordnung = mapOf("a" to "k1", "b" to "k2"))
        val p = Kategorien.plan(s, werte, 0.0, true, "Ohne")
        // c (200) bleibt, a und b teilen sich 800 im Verhaeltnis 60 zu 40
        assertEquals(0.0, p.orders.first { it.key == "c" }.amount, 1e-9)
        assertEquals(480.0 - 500.0, p.orders.first { it.key == "a" }.amount, 1e-9)
        assertEquals(320.0 - 300.0, p.orders.first { it.key == "b" }.amount, 1e-9)
        val ohne = p.zeilen.first { it.id == null }
        assertEquals("Ohne", ohne.name)
        assertEquals(0.2, ohne.ist, 1e-9)
        assertEquals(0.0, ohne.abweichungEuro, 1e-9)
        // Zuordnung zu einer geloeschten Kategorie zaehlt als ohne Kategorie
        val geloescht = Kategorien.loeschen(s, "k2")
        assertNull(geloescht.zuordnung["b"])
        assertEquals(0.0, Kategorien.plan(geloescht, werte, 0.0, true).orders.first { it.key == "b" }.amount, 1e-9)
    }

    @Test(timeout = 5000)
    fun leereKategorieZaehltNichtMit() {
        val s = stand(50.0, 50.0, zuordnung = mapOf("a" to "k1", "b" to "k1", "c" to "k1"))
        val p = Kategorien.plan(s, werte, 0.0, true)
        assertEquals(1, p.zeilen.size)
        assertTrue(p.orders.all { kotlin.math.abs(it.amount) < 1e-9 })
    }

    @Test(timeout = 5000)
    fun startvorschlagNachAnlageklasse() {
        val v = listOf(Kategorien.Wert("a", 500.0, "Aktien"), Kategorien.Wert("b", 300.0, "Aktien"), Kategorien.Wert("c", 200.0, "ETFs"))
        val s = Kategorien.vorschlag(v)
        assertEquals(listOf("Aktien", "ETFs"), s.kategorien.map { it.name })
        assertEquals(80.0, s.kategorien[0].ziel, 1e-9)
        assertEquals(100.0, Kategorien.summeZiele(s), 1e-9)
        assertEquals(s.zuordnung["a"], s.zuordnung["b"])
        assertTrue(s.vorgeschlagen)
        // Heutige Gewichte: der Plan verlangt nichts
        assertTrue(Kategorien.plan(s, werte, 0.0, true).orders.all { kotlin.math.abs(it.amount) < 1e-6 })
        // Drei gleiche Klassen: die Summe bleibt 100,0
        val drei = Kategorien.vorschlag(listOf(Kategorien.Wert("a", 1.0, "X"), Kategorien.Wert("b", 1.0, "Y"), Kategorien.Wert("c", 1.0, "Z")))
        assertEquals(100.0, Kategorien.summeZiele(drei), 1e-9)
    }

    @Test(timeout = 5000)
    fun bearbeitenAnlegenUmbenennenZielLoeschenZuordnen() {
        var s = KategorienStand()
        s = Kategorien.neu(s, " Gold ", 10.0)
        s = Kategorien.neu(s, "Welt", 90.0)
        assertEquals(listOf("Gold", "Welt"), s.kategorien.map { it.name })
        s = Kategorien.umbenennen(s, "k1", "Edelmetall")
        s = Kategorien.zielSetzen(s, "k1", 15.0)
        s = Kategorien.zuordnen(s, "a", "k1")
        assertEquals("Edelmetall", s.kategorien[0].name)
        assertEquals(15.0, s.kategorien[0].ziel, 0.0)
        assertEquals("k1", s.zuordnung["a"])
        s = Kategorien.zuordnen(s, "a", null)
        assertNull(s.zuordnung["a"])
        s = Kategorien.zuordnen(s, "a", "k9")
        assertNull(s.zuordnung["a"])
        s = Kategorien.zuordnen(s, "a", "k2")
        s = Kategorien.loeschen(s, "k2")
        assertEquals(1, s.kategorien.size)
        assertNull(s.zuordnung["a"])
        // neue Nummer nach dem Loeschen kollidiert nicht
        assertEquals("k2", Kategorien.neu(s, "Neu", 0.0).kategorien.last().id)
        assertEquals(0.0, Kategorien.zielSetzen(s, "k1", -5.0).kategorien[0].ziel, 0.0)
    }

    @Test(timeout = 5000)
    fun speichernUndLaden() {
        var s = Kategorien.neu(KategorienStand(), "Gold", 10.0)
        s = Kategorien.zuordnen(s, "a", "k1")
        s = Kategorien.zieleSpeichern(s, mapOf("a" to 60.0, "b" to 40.0))
        val text = Kategorien.schreiben(s)
        assertEquals(s, Kategorien.lesen(text))
        assertEquals(KategorienStand(), Kategorien.lesen(""))
        assertEquals(KategorienStand(), Kategorien.lesen("kein json"))
        assertEquals("", Kategorien.schreiben(KategorienStand()))
        // Regler starten mit den gespeicherten Zielen, sonst mit dem heutigen Anteil
        val start = Kategorien.startGewichte(s, listOf("a" to 500.0, "b" to 300.0, "c" to 200.0))
        assertEquals(60.0, start.getValue("a"), 0.0)
        assertEquals(40.0, start.getValue("b"), 0.0)
        assertEquals(20.0, start.getValue("c"), 1e-9)
    }

    @Test(timeout = 5000)
    fun sicherungNimmtKategorienMit() {
        val s = Kategorien.schreiben(Kategorien.zieleSpeichern(Kategorien.neu(KategorienStand(), "Gold", 10.0), mapOf("a" to 25.0)))
        val text = Backup.export(Account(), Settings(kategorien = s), now = 1L)
        val ok = Backup.parse(text) as Backup.Outcome.Ok
        assertEquals(s, ok.payload.settings.kategorien)
        // Aeltere Dateien ohne das Feld lesen sich mit Vorgabe
        val alt = text.replace(Regex("\"kategorien\": \"(?:[^\"\\\\]|\\\\.)*\""), "\"fremd\": 0")
        assertEquals("", (Backup.parse(alt) as Backup.Outcome.Ok).payload.settings.kategorien)
    }
}
