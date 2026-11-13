package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Watchliste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung076XTest {
    private val t = 1_000L

    @Test fun alterStandWirdErsteListe() {
        val json = """{"app":"troetenrepublik","format":2,"account":{"cash":10.0,"watchlist":["SAP.DE","AAPL"]}}"""
        val a = (Backup.parse(json) as Backup.Outcome.Ok).payload.account
        assertEquals(listOf("SAP.DE", "AAPL"), a.ersteListe)
        assertEquals(1, a.watchlisten.size)
        assertEquals(listOf("SAP.DE", "AAPL"), a.watchlist)
        assertTrue(a.weitereListen.isEmpty())
        // Rundlauf mit weiteren Listen
        val b = Watchlisten.neu(a, "Tech")
        val c = (Backup.parse(Backup.export(b, de.troetenrepublik.app.data.Settings())) as Backup.Outcome.Ok).payload.account
        assertEquals(b.watchlisten, c.watchlisten)
    }

    @Test fun hinzufuegenUndEntfernenJeListe() {
        var a = Watchlisten.neu(Account(ersteListe = listOf("SAP.DE")), "Tech")
        a = Trading.watch(a, "NVDA", 100.0, t, 1)
        assertEquals(listOf("SAP.DE"), a.watchlisten[0].symbole)
        assertEquals(listOf("NVDA"), a.watchlisten[1].symbole)
        // Auf beiden Listen: Bezugspunkt bleibt vom ersten Hinzufuegen
        a = Trading.watch(a, "NVDA", 200.0, t + 1, 0)
        assertEquals(setOf(0, 1), Watchlisten.listenMit(a, "NVDA"))
        assertEquals(100.0, a.watchMeta["NVDA"]!!.price, 0.0)
        // Entfernen nur von einer Liste
        a = Trading.unwatch(a, "NVDA", 1)
        assertEquals(setOf(0), Watchlisten.listenMit(a, "NVDA"))
        assertTrue("NVDA" in a.watchlist)
        // Von allen
        a = Trading.unwatch(a, "NVDA")
        assertTrue("NVDA" !in a.watchlist)
        // Auswahl setzt genau die gewaehlten Listen
        a = Watchlisten.waehlen(a, "AAPL", 50.0, t, setOf(1))
        assertEquals(setOf(1), Watchlisten.listenMit(a, "AAPL"))
        a = Watchlisten.waehlen(a, "AAPL", 50.0, t, setOf(0))
        assertEquals(setOf(0), Watchlisten.listenMit(a, "AAPL"))
    }

    @Test fun vereinigungUndVerwaltung() {
        var a = Account(ersteListe = listOf("A", "B"))
        a = Watchlisten.neu(a, " Zwei ")
        a = Watchlisten.neu(a, "")
        assertEquals(2, a.watchlisten.size)
        assertEquals("Zwei", a.watchlisten[1].name)
        a = Trading.watch(a, "B", 1.0, t, 1)
        a = Trading.watch(a, "C", 1.0, t, 1)
        assertEquals(listOf("A", "B", "C"), a.watchlist)
        a = Watchlisten.umbenennen(a, 0, "Eins")
        assertEquals("Eins", a.watchlisten[0].name)
        // Erste loeschen: die zweite rueckt nach, letzte nicht loeschbar
        a = Watchlisten.loeschen(a, 0)
        assertEquals(listOf("B", "C"), a.ersteListe)
        assertEquals("Zwei", a.ersteListeName)
        assertEquals(a, Watchlisten.loeschen(a, 0))
        assertEquals(1, a.watchlisten.size)
    }

    @Test fun zusammenfuehrenVereinigtListen() {
        val a = Account(ersteListe = listOf("X"), weitereListen = listOf(Watchliste("Tech", listOf("N"))))
        val b = Account(ersteListe = listOf("Y"), weitereListen = listOf(Watchliste("tech", listOf("M")), Watchliste("Neu", listOf("Q"))))
        val m = Watchlisten.vereinigen(a, b)
        assertEquals(listOf("X", "Y"), m.ersteListe)
        assertEquals(listOf(Watchliste("Tech", listOf("N", "M")), Watchliste("Neu", listOf("Q"))), m.weitereListen)
        assertEquals(listOf("X", "Y", "N", "M", "Q"), m.watchlist)
    }
}
