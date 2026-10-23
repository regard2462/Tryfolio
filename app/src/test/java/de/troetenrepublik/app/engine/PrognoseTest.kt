package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Wette
import de.troetenrepublik.app.market.Prognose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Echte, gekuerzte Antworten von Polymarket und Kalshi, dazu Uebungswetten und wichtige News.
class PrognoseTest {

    private fun text(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("prognose/$name")!!.readBytes().toString(Charsets.UTF_8)

    private fun pruefeMarkt(m: Prognose.Markt) {
        assertEquals(2, m.seiten.size)
        m.seiten.forEach { s ->
            assertTrue("Kurs ${s.preis}", s.preis in 0.0..1.0)
            assertTrue("Brief ${s.kauf}", s.kauf in 0.0..1.0)
            assertTrue("Geld ${s.verkauf}", s.verkauf in 0.0..1.0)
        }
        assertTrue("Brief nicht unter Geld", m.seiten[0].kauf + 1e-9 >= m.seiten[0].verkauf)
    }

    @Test
    fun polymarketListeUndEreignis() {
        val liste = Prognose.polyListeLesen(text("poly_liste.json"))
        assertTrue(liste.isNotEmpty())
        val e = liste.first()
        assertEquals(Prognose.Quelle.POLYMARKET, e.quelle)
        assertTrue(e.titel.isNotBlank())
        assertTrue(e.link.startsWith("https://polymarket.com/event/"))
        assertTrue(e.maerkte.isNotEmpty())
        // nach Wahrscheinlichkeit absteigend
        assertEquals(e.maerkte.sortedByDescending { it.chance }.map { it.id }, e.maerkte.map { it.id })
        e.maerkte.forEach { pruefeMarkt(it); assertTrue(it.seiten[0].token.isNotBlank()) }
    }

    @Test
    fun polymarketMarktUndVerlauf() {
        val m = Prognose.polyMarktText(text("poly_markt.json"))
        assertNotNull(m)
        pruefeMarkt(m!!)
        val v = Prognose.polyVerlaufLesen(text("poly_verlauf.json"))
        assertTrue(v.size >= 5)
        assertTrue(v.all { it.second in 0.0..1.0 && it.first > 1_700_000_000_000L })
        assertEquals(v.sortedBy { it.first }, v)
    }

    @Test
    fun kalshiTrendsEreignisMarktUndKerzen() {
        val s = Prognose.kalshiListeLesen(text("kalshi_trends.json"))
        assertTrue(s.ereignisse.isNotEmpty())
        assertNotNull(s.weiter)
        s.ereignisse.forEach { e ->
            assertEquals(Prognose.Quelle.KALSHI, e.quelle)
            assertTrue(e.serie.isNotBlank())
            e.maerkte.forEach { pruefeMarkt(it) }
        }
        val e = Prognose.kalshiEreignisText(text("kalshi_ereignis.json"))
        assertNotNull(e)
        assertEquals("KXFEDDECISION-26OCT", e!!.id)
        assertEquals("KXFEDDECISION", e.serie)
        assertTrue(e.maerkte.isNotEmpty())
        assertTrue(e.beschreibung.isNotBlank())
        e.maerkte.forEach { pruefeMarkt(it); assertTrue(it.offen) }
        val m = Prognose.kalshiMarktText(text("kalshi_markt.json"))
        assertNotNull(m)
        assertEquals("KXFEDDECISION", m!!.serie)
        val k = Prognose.kalshiVerlaufLesen(text("kalshi_kerzen.json"))
        assertTrue(k.isNotEmpty())
        assertTrue(k.all { it.second in 0.0..1.0 })
    }

    @Test
    fun aufloesungWirdErkannt() {
        // Polymarket: geschlossen mit Kursen 1/0 oder 0/1
        val zu = """{"id":"1","question":"Q?","outcomes":"[\"Yes\", \"No\"]","outcomePrices":"[\"0\", \"1\"]","clobTokenIds":"[\"a\", \"b\"]","closed":true,"active":true}"""
        val m = Prognose.polyMarktText(zu)!!
        assertFalse(m.offen)
        assertEquals(0.0, m.auszahlung!!, 0.0)
        assertEquals(1, m.gewinner)
        // Kalshi: Ergebnis „yes“
        val k = """{"market":{"ticker":"KX-1-A","title":"T","yes_sub_title":"A","status":"finalized","result":"yes","last_price_dollars":"0.99"}}"""
        val km = Prognose.kalshiMarktText(k)!!
        assertFalse(km.offen)
        assertEquals(1.0, km.auszahlung!!, 0.0)
    }

    private fun vorlage(seite: Int = 0) = Wette(
        id = "w1", quelle = "KALSHI", markt = "KX-1-A", ereignis = "KX-1", titel = "Ereignis", frage = "A", seite = seite,
        seitenName = if (seite == 0) "Yes" else "No", anteile = 0.0, einstand = 0.0, kurs = 0.0, kursAm = 0L, eroeffnet = 0L,
    )

    @Test
    fun wettenKaufenVerkaufenAufloesen() {
        val start = Account(cash = 100.0, deposited = 100.0)
        val k1 = Wetten.kaufen(start, vorlage(), 10.0, 0.25, 1L, "t1")
        assertTrue(k1.ok)
        assertEquals(90.0, k1.account.cash, 1e-9)
        assertEquals(40.0, k1.account.wetten.single().anteile, 1e-9)
        assertEquals(TxType.BET, k1.account.transactions.last().type)
        // zweiter Kauf derselben Seite: eine Wette mit gemitteltem Einstand
        val k2 = Wetten.kaufen(k1.account, vorlage().copy(id = "w2"), 10.0, 0.5, 2L, "t2")
        val w = k2.account.wetten.single()
        assertEquals(60.0, w.anteile, 1e-9)
        assertEquals(20.0 / 60.0, w.einstand, 1e-9)
        // Verkauf zum Geldkurs
        val v = Wetten.verkaufen(k2.account, w.id, 0.4, 3L, "t3")
        assertTrue(v.ok)
        assertEquals(80.0 + 24.0, v.account.cash, 1e-9)
        assertEquals(Wette.VERKAUFT, v.account.wetten.single().status)
        assertEquals(4.0, v.account.transactions.last().realized, 1e-9)
        // Aufloesen: gewonnen zahlt 1 € je Anteil, verloren nichts
        val g = Wetten.aufloesen(k1.account, k1.account.wetten.single().id, 1.0, 4L, "t4")
        assertEquals(130.0, g.account.cash, 1e-9)
        assertEquals(Wette.GEWONNEN, g.account.wetten.single().status)
        val l = Wetten.aufloesen(k1.account, k1.account.wetten.single().id, 0.0, 4L, "t5")
        assertEquals(90.0, l.account.cash, 1e-9)
        assertEquals(Wette.VERLOREN, l.account.wetten.single().status)
        // schon abgeschlossen
        assertEquals(Wetten.Fehler.GESCHLOSSEN, Wetten.verkaufen(g.account, k1.account.wetten.single().id, 0.5, 5L, "t6").fehler)
    }

    @Test
    fun wettenPruefenEinsatzKursUndGuthaben() {
        val start = Account(cash = 20.0)
        assertEquals(Wetten.Fehler.BETRAG, Wetten.kaufen(start, vorlage(), 0.5, 0.3, 1L, "t").fehler)
        assertEquals(Wetten.Fehler.GUTHABEN, Wetten.kaufen(start, vorlage(), 25.0, 0.3, 1L, "t").fehler)
        assertEquals(Wetten.Fehler.KURS, Wetten.kaufen(start, vorlage(), 5.0, 1.0, 1L, "t").fehler)
        assertEquals(Wetten.Fehler.KURS, Wetten.kaufen(start, vorlage(), 5.0, 0.0, 1L, "t").fehler)
    }

    @Test
    fun uebersichtZaehltAbgeschlosseneWettenMit() {
        val start = Account(cash = 100.0, deposited = 100.0)
        // w1 verkauft mit +4 €, w2 verloren mit -10 €, w3 offen: 20 Anteile zu 0,5 (10 €), jetzt 0,6
        val a = Wetten.kaufen(start, vorlage(), 10.0, 0.25, 1L, "t1").account
        val b = Wetten.verkaufen(a, "w1", 0.35, 2L, "t2").account
        val c = Wetten.kaufen(b, vorlage().copy(id = "w2", markt = "KX-1-B"), 10.0, 0.5, 3L, "t3").account
        val d = Wetten.aufloesen(c, "w2", 0.0, 4L, "t4").account
        val e = Wetten.kaufen(d, vorlage().copy(id = "w3", markt = "KX-1-C"), 10.0, 0.5, 5L, "t5").account
        val u = Wetten.uebersicht(e.wetten, mapOf("w3" to 0.6))
        assertEquals(1, u.offen)
        assertEquals(10.0, u.einsatz, 1e-9)
        assertEquals(12.0, u.wert, 1e-9)
        assertEquals(2.0, u.ergebnisOffen, 1e-9)
        assertEquals(2, u.abgeschlossen)
        assertEquals(-6.0, u.ergebnisAbgeschlossen, 1e-9)
        assertEquals(-4.0, u.gesamt, 1e-9)
        // dasselbe wie der letzte Punkt des Verlaufs
        val v = Wetten.verlauf(e.transactions, emptyMap(), mapOf("w3" to 0.6), 10L)
        assertEquals(u.gesamt, v.last().second, 1e-9)
        // ohne aktuellen Kurs gilt der zuletzt bekannte
        assertEquals(0.0, Wetten.uebersicht(e.wetten, emptyMap()).ergebnisOffen, 1e-9)
    }
}
