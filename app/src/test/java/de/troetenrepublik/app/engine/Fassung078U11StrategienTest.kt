package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.FinderVorlagen.Pruefung
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.StrategieTexte
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

// 0.78: Finder-Vorlagen fuer Setups, Glocke auf eine Regel des Strategietests, Stoppabstand aus der ATR, Auswertung der
// eigenen Geschaefte und Stopp-Verkauf mit verknuepftem Ziel.
class Fassung078U11StrategienTest {
    private val zone = java.time.ZoneId.of("Europe/Berlin")
    private var zoneVorher: TimeZone? = null
    private var zeitenVorher: MarketHours.Zeiten? = null
    private val tagMs = 86_400_000L

    @Before fun vor() {
        zoneVorher = TimeZone.getDefault()
        zeitenVorher = MarketHours.zeiten
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        MarketHours.zeiten = MarketHours.DEUTSCHLAND
    }

    @After fun nach() {
        TimeZone.setDefault(zoneVorher)
        zeitenVorher?.let { MarketHours.zeiten = it }
    }

    // ------------------------------------------------------------ Finder-Vorlagen

    private val jetzt = 1_800_000_000_000L

    private fun aktie(
        symbol: String, jahr: Double? = null, linie200: Double? = null, boersenwert: Double? = 1e9, mitKerzen: Boolean = true,
        werte: Map<Feld, Double> = emptyMap(),
    ) = Aktienfinder.Daten(symbol, symbol, "US", jahr = jahr, linie200 = linie200, boersenwert = boersenwert, mitKerzen = mitKerzen, werte = werte)

    private fun v(id: String) = FinderVorlagen.vorlage(id)!!

    @Test fun ruecksetzerImAufwaertstrend() {
        val g = v("RUECKSETZER").grenzen
        val trend = mapOf(Feld.VOLUMEN_SCHNITT to 1e6, Feld.SMA50_200 to 3.0)
        assertEquals(Pruefung.JA, FinderVorlagen.pruefen(aktie("A", linie200 = 5.0, werte = trend + (Feld.RSI to 30.0)), g, jetzt))
        // RSI ueber 35, Kurs unter der 200-Tage-Linie, 50- unter 200-Tage-Linie, zu wenig Umsatz
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(aktie("B", linie200 = 5.0, werte = trend + (Feld.RSI to 40.0)), g, jetzt))
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(aktie("C", linie200 = -1.0, werte = trend + (Feld.RSI to 30.0)), g, jetzt))
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(aktie("D", linie200 = 5.0, werte = mapOf(Feld.VOLUMEN_SCHNITT to 1e6, Feld.SMA50_200 to -2.0, Feld.RSI to 30.0)), g, jetzt))
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(aktie("E", linie200 = 5.0, werte = mapOf(Feld.VOLUMEN_SCHNITT to 1e5, Feld.SMA50_200 to 3.0, Feld.RSI to 30.0)), g, jetzt))
        // Ohne geladene Kerzen ist der RSI noch offen
        assertEquals(Pruefung.OFFEN, FinderVorlagen.pruefen(aktie("F", linie200 = 5.0, mitKerzen = false, werte = trend), g, jetzt))
        assertEquals(Feld.RSI, v("RUECKSETZER").sortierung)
        assertTrue(v("RUECKSETZER").aufsteigend)
    }

    @Test fun nahAmHoch() {
        val g = v("NAH_AM_HOCH").grenzen
        fun w(abstand: Double) = mapOf(Feld.VOLUMEN_SCHNITT to 1e6, Feld.HOCH52 to abstand)
        assertEquals(Pruefung.JA, FinderVorlagen.pruefen(aktie("A", werte = w(-3.0)), g, jetzt))
        assertEquals(Pruefung.JA, FinderVorlagen.pruefen(aktie("B", werte = w(-5.0)), g, jetzt))
        assertEquals(Pruefung.JA, FinderVorlagen.pruefen(aktie("C", werte = w(0.0)), g, jetzt))
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(aktie("D", werte = w(-8.0)), g, jetzt))
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(aktie("E", boersenwert = 1e8, werte = w(-1.0)), g, jetzt))
        assertEquals(Feld.JAHR, v("NAH_AM_HOCH").sortierung)
        assertFalse(v("NAH_AM_HOCH").aufsteigend)
    }

    @Test fun staerksteZwoelfMonateIstDasObersteZehntel() {
        val vorlage = v("STAERKSTE_12_MONATE")
        val liste = (1..20).map { aktie("S$it", jahr = it * 5.0, werte = mapOf(Feld.VOLUMEN_SCHNITT to 1e6)) } +
            // Hohe Jahresrendite, aber zu wenig Umsatz
            aktie("DUENN", jahr = 500.0, werte = mapOf(Feld.VOLUMEN_SCHNITT to 1e4))
        val p = FinderRaster.pruefen(liste, vorlage.grenzen, vorlage, jetzt)
        // 21 Werte, ein Zehntel sind drei: DUENN, S20 und S19 liegen darin, DUENN scheitert am Umsatz
        assertEquals(setOf("S19", "S20"), p.filterValues { it == Pruefung.JA }.keys)
        assertEquals(Pruefung.NEIN, p["DUENN"])
        assertEquals(Pruefung.NEIN, p["S18"])
    }

    @Test fun neueVorlagenMitTextenUndEigenerNummer() {
        val ids = listOf("RUECKSETZER", "NAH_AM_HOCH", "STAERKSTE_12_MONATE")
        assertEquals(ids, FinderVorlagen.VORLAGEN.filter { it.nummer >= 41 }.map { it.id })
        for (id in ids) for (s in listOf(De, En)) {
            assertTrue(id, s.afVorlage(id) != id)
            assertTrue(id, s.afVorlageIdee(id).isNotBlank())
            assertFalse(id, v(id).unvollstaendig)
            v(id).grenzen.forEach { assertTrue(it.feld.name, s.afFeld(it.feld.name).isNotBlank()) }
            listOf(s.afVorlage(id), s.afVorlageIdee(id)).forEach { t -> assertFalse(t, t.contains("—") || t.contains("–") || t.contains("…")) }
        }
        // Der Ruecksetzer ist nicht die schwache Woche: er verlangt den RSI
        assertTrue(v("RUECKSETZER").grenzen.any { it.feld == Feld.RSI })
        assertTrue(v("WOCHENTIEF").grenzen.none { it.feld == Feld.RSI })
    }

    // ------------------------------------------------------------ Glocke auf eine Regel

    private val heute = 20_500L

    private fun alarm(art: String, zustand: Int = 0, merk: String = "", gemeldet: Long = 0L) =
        PriceAlert("a1", "AAPL", "Apple", 0.0, above = true, createdAt = 1L, art = art, zustand = zustand, merk = merk, gemeldetAm = gemeldet)

    private val sma = StrategieLabor.Regel(StrategieLabor.Art.SMA, 200)
    private val smaArt = RegelGlocke.art(sma)

    // Kerzen mit festem Schlusskurs je Tag, ab Tag 20.000
    private fun reihe(schluss: List<Double>) = schluss.mapIndexed { i, c -> Candle((20_000L + i) * tagMs, c, c, c, c) }

    @Test fun artTraegtDieRegel() {
        val regeln = StrategieLabor.REGELN
        // Jede Regel der oeffentlichen Liste bekommt ihre eigene Art und findet zurueck
        assertEquals(regeln.size, regeln.map { RegelGlocke.art(it) }.toSet().size)
        for (r in regeln) {
            val art = RegelGlocke.art(r)
            assertTrue(RegelGlocke.istArt(art))
            assertEquals(r, RegelGlocke.regel(art))
            assertEquals(RegelGlocke.Teile(r.art.name, r.a, r.b, r.n), RegelGlocke.teile(art))
            assertEquals(!StrategieLabor.ohneSignal(r), RegelGlocke.moeglich(r))
        }
        assertEquals("REGEL:SMA:200:0:0", smaArt)
        assertFalse(RegelGlocke.istArt(Alarmarten.SCHNITT))
        assertNull(RegelGlocke.teile("REGEL:SMA:x:0:0"))
        assertNull(RegelGlocke.regel("REGEL:GIBTESNICHT:1:2:3"))
    }

    @Test fun lageKommtAusDemStrategietest() {
        // 320 Tage steigend: Kurs ueber der 200-Tage-Linie
        val steigend = reihe((0 until 320).map { 100.0 + it })
        val oben = RegelGlocke.lage(steigend, sma)!!
        assertTrue(oben.investiert)
        assertEquals(20_319L, oben.tag)
        // Danach ein Absturz unter die Linie
        val gefallen = reihe((0 until 320).map { 100.0 + it } + 50.0)
        val unten = RegelGlocke.lage(gefallen, sma)!!
        assertFalse(unten.investiert)
        assertEquals(20_320L, unten.tag)
        // Dieselbe Rechnung wie im Test, fuer jede Regel der Liste
        for (r in StrategieLabor.REGELN) {
            assertEquals(r.toString(), StrategieLabor.ziel(StrategieLabor.Daten(gefallen), r).last(), RegelGlocke.lage(gefallen, r)!!.investiert)
        }
        // Zu kurze Reihe
        assertNull(RegelGlocke.lage(steigend.take(RegelGlocke.MIN_KERZEN - 1), sma))
    }

    @Test fun wechselMeldetEinmal() {
        // Erste Pruefung merkt nur die Lage
        val erst = RegelGlocke.pruefen(alarm(smaArt), RegelGlocke.Lage(true, 100), heute)
        assertFalse(erst.gemeldet)
        assertEquals(1, erst.neu.zustand)
        assertEquals("100", erst.neu.merk)
        assertEquals(heute, erst.neu.gemeldetAm)
        // Gleiche Lage am naechsten Schluss: nichts
        val gleich = RegelGlocke.pruefen(erst.neu, RegelGlocke.Lage(true, 101), heute + 1)
        assertFalse(gleich.gemeldet)
        assertEquals("101", gleich.neu.merk)
        // Wechsel auf nicht investiert: Verkaufssignal
        val raus = RegelGlocke.pruefen(gleich.neu, RegelGlocke.Lage(false, 102), heute + 2)
        assertTrue(raus.gemeldet)
        assertEquals(RegelGlocke.TAG_VERKAUF, raus.tag)
        assertEquals(smaArt, raus.text)
        assertEquals(-1, raus.neu.zustand)
        // Derselbe Schluss noch einmal geprueft: keine zweite Meldung
        val nochmal = RegelGlocke.pruefen(raus.neu, RegelGlocke.Lage(false, 102), heute + 2)
        assertFalse(nochmal.gemeldet)
        // Ein aelterer Schluss mit anderer Lage (verspaetete Daten) meldet auch nicht
        assertFalse(RegelGlocke.pruefen(raus.neu, RegelGlocke.Lage(true, 101), heute + 2).gemeldet)
        // Wechsel zurueck: Kaufsignal
        val rein = RegelGlocke.pruefen(raus.neu, RegelGlocke.Lage(true, 103), heute + 3)
        assertTrue(rein.gemeldet)
        assertEquals(RegelGlocke.TAG_KAUF, rein.tag)
        // Ohne Daten bleibt alles, wie es ist
        val ohne = RegelGlocke.pruefen(rein.neu, null, heute + 4)
        assertFalse(ohne.gemeldet)
        assertEquals(rein.neu, ohne.neu)
    }

    @Test fun glockeLaeuftImGlockenSystemMit() {
        val a = alarm(smaArt, zustand = 1, merk = "100", gemeldet = heute - 1)
        val daten = Alarmarten.Daten(100.0, 1000.0, null, null, null, emptyList(), heute, "US", true, regelLage = RegelGlocke.Lage(false, 101))
        val e = Alarmarten.pruefen(a, daten)
        assertTrue(e.gemeldet)
        // Buchung mit Text in beiden Sprachen
        val konto = Account(cash = 0.0, alerts = listOf(a))
        val (neu, gemeldet) = Trading.artenMelden(konto, mapOf(a.id to e), mapOf("AAPL" to Quote("AAPL", price = 123.0)), 5L)
        assertEquals(1, gemeldet.size)
        assertEquals(-1, neu.alerts.single().zustand)
        val tx = neu.transactions.single()
        assertEquals(RegelGlocke.TAG_VERKAUF, tx.tag)
        assertTrue(tx.tag in Alarmarten.TEXT_TAGS)
        assertEquals("Verkaufssignal: 200-Tage-Linie · Kurs 123", De.alertNeuText(tx.tag, tx.subtitle, "123"))
        assertEquals("Buy signal: 200-day line · price 123", En.alertNeuText(RegelGlocke.TAG_KAUF, tx.subtitle, "123"))
        // Ohne geladene Lage (kein Abruf in dieser Runde) meldet nichts
        assertFalse(Alarmarten.pruefen(a, daten.copy(regelLage = null)).gemeldet)

        // In der Glocke des Werts: Gruppe Signale, sichtbar nur wenn gesetzt, abschaltbar
        assertEquals(Wertglocke.Gruppe.SIGNALE, Wertglocke.gruppeVon(smaArt))
        val moeglich = Wertglocke.moeglich(AssetType.ETF, us = false, dividende = null)
        assertTrue(moeglich.none { smaArt in it.arten })
        val sichtbar = Wertglocke.sichtbar(moeglich, setOf(smaArt))
        assertEquals(listOf(smaArt), sichtbar.single { it.gruppe == Wertglocke.Gruppe.SIGNALE }.arten)
        assertEquals("Regel 200-Tage-Linie", De.glockeArt(smaArt))
        assertEquals("Rule 200-day line", En.glockeArtKurz(smaArt))
        assertEquals("Regel 200-Tage-Linie", Wertglocke.zusammenfassung(listOf(a), { De.glockeArtKurz(it) }, { "" }, { "" }))
        val an = Wertglocke.umlegen(emptyList(), "AAPL", listOf(smaArt), true) { art -> alarm(art) }
        assertEquals(smaArt, an.single().art)
        // Noch einmal einschalten legt keine zweite an, abschalten entfernt sie
        assertEquals(1, Wertglocke.umlegen(an, "AAPL", listOf(smaArt), true) { art -> alarm(art) }.size)
        assertTrue(Wertglocke.umlegen(an, "AAPL", listOf(smaArt), false) { art -> alarm(art) }.isEmpty())
    }

    // ------------------------------------------------------------ ATR

    @Test fun atrVonHandGerechnet() {
        // Spannen 2, 2, 4: erster Schnitt 8/3; danach Spanne 2: (8/3 * 2 + 2) / 3 = 22/9. Letzter Schluss 13
        val k = listOf(
            Candle(1 * tagMs, 10.0, 11.0, 9.0, 10.0),
            Candle(2 * tagMs, 10.0, 12.0, 10.0, 11.0),
            Candle(3 * tagMs, 11.0, 15.0, 11.0, 14.0),
            Candle(4 * tagMs, 14.0, 14.0, 12.0, 13.0),
        )
        assertEquals(22.0 / 9.0 / 13.0 * 100.0, AtrAbstand.prozent(k, 3)!!, 1e-9)
        // Zu wenige Kerzen fuer 20 Tage
        assertNull(AtrAbstand.prozent(k))
        // 25 Tage mit Schluss 100 und Spanne 2: ATR 2 %, zwei ATR 4 %, drei ATR 6 %
        val gleich = (1..25).map { Candle(it * tagMs, 100.0, 101.0, 99.0, 100.0) }
        val atr = AtrAbstand.prozent(gleich)!!
        assertEquals(2.0, atr, 1e-9)
        assertEquals(4.0, AtrAbstand.abstand(atr, 2)!!, 1e-9)
        assertEquals(6.0, AtrAbstand.abstand(atr, 3)!!, 1e-9)
        // Positionsgroesse mit diesem Abstand: 1 % von 10.000 riskiert, 4 je Stueck, 25 Stueck, Stopp bei 96
        val p = Analysis.positionSize(10_000.0, 100.0, 1.0, AtrAbstand.abstand(atr, 2)!!, true)!!
        assertEquals(96.0, p.stopPrice, 1e-9)
        assertEquals(25.0, p.units, 1e-9)
        // Trailing-Order nimmt hoechstens 50 %
        assertNull(AtrAbstand.abstand(30.0, 2, AtrAbstand.MAX_PROZENT))
        assertEquals(50.0, AtrAbstand.abstand(25.0, 2, AtrAbstand.MAX_PROZENT)!!, 1e-9)
        assertNull(AtrAbstand.abstand(0.0, 2))
        assertNull(AtrAbstand.abstand(60.0, 2))
        // Eingabe fuer das Ziffernfeld
        assertEquals("4", AtrAbstand.eingabe(4.0))
        assertEquals("4,5", AtrAbstand.eingabe(4.5))
        assertEquals("12,34", AtrAbstand.eingabe(12.344))
        assertEquals("0,05", AtrAbstand.eingabe(0.05))
        // Eine Trailing-Order mit dem vorbelegten Abstand ist gueltig
        val o = Order("o", "T", "T", "T", Side.SELL, OrderType.TRAILING_STOP, 1.0, trailPct = AtrAbstand.abstand(atr, 3)!!)
        assertNull(Trading.orderError(Trading.trailingStop(o, 100.0)))
    }

    // ------------------------------------------------------------ Auswertung der Geschaefte

    private fun kauf(key: String, tag: Int) = Transaction("k-$key", tag * tagMs, TxType.BUY, key, qty = 10.0, price = 100.0, symbol = key, key = key, tag = "BUY")
    private fun verkauf(key: String, tag: Int, ergebnis: Double, via: String = "", typ: TxType = TxType.SELL) =
        Transaction("v-$key", tag * tagMs, typ, key, qty = 10.0, price = 100.0 + ergebnis / 10, symbol = key, key = key, realized = ergebnis, via = via, tag = "SELL")
    private fun erledigt(key: String, tag: Int, art: OrderType) =
        Order("o-$key", key, key, key, Side.SELL, art, 10.0, 90.0, status = OrderStatus.FILLED, executedAt = tag * tagMs)

    @Test fun geschaefteNachHaltedauerUndAusloeser() {
        val txs = listOf("A", "B", "C", "D", "E", "F").map { kauf(it, 0) } + listOf(
            verkauf("A", 3, 50.0),
            verkauf("B", 20, -30.0, via = "ORDER"),
            verkauf("C", 60, -20.0, via = "ORDER"),
            verkauf("D", 61, -10.0, via = "ORDER"),
            verkauf("E", 62, 5.0),
            verkauf("F", 63, -100.0, typ = TxType.KNOCKOUT),
            // Ohne Kauf in den Buchungen und ohne Order in der Liste
            verkauf("G", 64, 7.0, via = "ORDER"),
        )
        val orders = listOf(erledigt("B", 20, OrderType.STOP), erledigt("C", 60, OrderType.LIMIT), erledigt("D", 61, OrderType.TRAILING_STOP))
        val a = Geschaeftsauswertung.auswerten(txs, orders)!!
        assertEquals(7, a.geschaefte.size)
        // Haltedauer je Geschaeft
        assertEquals(listOf(3.0, 20.0, 60.0, 61.0, 62.0, 63.0, null), a.geschaefte.map { it.tage })
        // Klassen: unter 1 Woche, bis 1 Monat, laenger
        assertEquals(Geschaeftsauswertung.Gruppe(1, 1, 50.0), a.dauer[Geschaeftsauswertung.Dauer.WOCHE])
        assertEquals(Geschaeftsauswertung.Gruppe(1, 0, -30.0), a.dauer[Geschaeftsauswertung.Dauer.MONAT])
        val lang = a.dauer.getValue(Geschaeftsauswertung.Dauer.LAENGER)
        assertEquals(4, lang.anzahl)
        assertEquals(1, lang.gewinne)
        assertEquals(-125.0, lang.summe, 1e-9)
        assertEquals(0.25, lang.trefferquote, 1e-9)
        // Ausloeser
        // Ausloeser in fester Reihenfolge
        assertEquals(Geschaeftsauswertung.Gruppe(2, 2, 55.0), a.ausloeser[Geschaeftsauswertung.Ausloeser.HAND])
        assertEquals(Geschaeftsauswertung.Gruppe(1, 0, -30.0), a.ausloeser[Geschaeftsauswertung.Ausloeser.STOPP])
        assertEquals(Geschaeftsauswertung.Gruppe(1, 0, -20.0), a.ausloeser[Geschaeftsauswertung.Ausloeser.LIMIT])
        assertEquals(Geschaeftsauswertung.Gruppe(1, 0, -10.0), a.ausloeser[Geschaeftsauswertung.Ausloeser.TRAILING])
        assertEquals(Geschaeftsauswertung.Gruppe(1, 1, 7.0), a.ausloeser[Geschaeftsauswertung.Ausloeser.ORDER])
        assertEquals(Geschaeftsauswertung.Gruppe(1, 0, -100.0), a.ausloeser[Geschaeftsauswertung.Ausloeser.ENDE])
        assertEquals(Geschaeftsauswertung.Ausloeser.entries.toList(), a.ausloeser.keys.toList())
        // Laengste Verlustserie: B, C, D
        assertEquals(3, a.verlustserie)
        assertEquals((3 + 20 + 60 + 61 + 62 + 63) / 6.0, a.mittelTage!!, 1e-9)
        // Gleiche Zaehlung wie die Verkaufsbilanz
        assertEquals(Verkaufsbilanz.auswerten(txs)!!.anzahl, a.geschaefte.size)
        // Unter fuenf Geschaeften keine Auswertung
        assertNull(Geschaeftsauswertung.auswerten(txs.filter { it.key in setOf("A", "B") }, orders))
    }

    @Test fun klassenGrenzenUndSerie() {

        assertEquals(Geschaeftsauswertung.Dauer.WOCHE, Geschaeftsauswertung.dauerKlasse(0.0))
        assertEquals(Geschaeftsauswertung.Dauer.WOCHE, Geschaeftsauswertung.dauerKlasse(6.99))
        assertEquals(Geschaeftsauswertung.Dauer.MONAT, Geschaeftsauswertung.dauerKlasse(7.0))
        assertEquals(Geschaeftsauswertung.Dauer.MONAT, Geschaeftsauswertung.dauerKlasse(31.0))
        assertEquals(Geschaeftsauswertung.Dauer.LAENGER, Geschaeftsauswertung.dauerKlasse(31.01))
        assertEquals(0, Geschaeftsauswertung.verlustserie(listOf(5.0, 1.0)))
        assertEquals(2, Geschaeftsauswertung.verlustserie(listOf(-1.0, 5.0, -2.0, -3.0, 4.0, -1.0)))
        // Ein Ergebnis von null unterbricht die Serie nicht und zaehlt nicht mit
        assertEquals(3, Geschaeftsauswertung.verlustserie(listOf(-1.0, -2.0, 0.0, -3.0)))
        assertEquals(0, Geschaeftsauswertung.verlustserie(emptyList()))
    }

    // ------------------------------------------------------------ Stopp mit Ziel

    private fun zeit(tag: String, stunde: Int, minute: Int = 0): Long =
        LocalDate.parse(tag).atTime(stunde, minute).atZone(zone).toInstant().toEpochMilli()

    private val aufgabe = zeit("2026-03-02", 10)
    private val seit = zeit("2026-03-02", 10, 1)
    private val spaeter = zeit("2026-03-04", 12)

    private fun order(art: OrderType, marke: Double, id: String, stueck: Double = 10.0) =
        Order(id, "TEST", "Test", "TEST", Side.SELL, art, stueck, marke, createdAt = aufgabe, validity = Validity.GTC)

    private val stopp = order(OrderType.STOP, 90.0, "s")
    private val ziel = order(OrderType.LIMIT, 110.0, "z")
    private val bestand = Position("TEST", "Test", "TEST", 10.0, 100.0, aufgabe - tagMs)

    private fun paarKonto() = OrderPaar.anlegen(Account(cash = 1_000.0, positions = listOf(bestand), letzterLauf = seit), stopp, ziel)
    private fun Account.o(id: String) = orders.single { it.id == id }
    private fun kurs(preis: Double, wann: Long) = mapOf("TEST" to Quote("TEST", price = preis, prevClose = 100.0, updatedAt = wann))

    @Test fun paarWirdVerknuepftAngelegt() {
        val a = paarKonto()
        assertEquals(listOf("s", "z"), a.orders.map { it.id })
        assertEquals("z", a.o("s").paar)
        assertEquals("s", a.o("z").paar)
        assertEquals("z", OrderPaar.partner(a.orders, a.o("s"))!!.id)
        assertTrue(a.orders.all { it.status == OrderStatus.OPEN && it.expiresAt > 0 })
        // Ziel unter dem Kurs oder unter dem Stopp, andere Stueckzahl, falsche Art: kein Paar
        assertNull(OrderPaar.fehler(stopp, ziel, 100.0))
        assertEquals(Err.INVALID_LIMIT, OrderPaar.fehler(stopp, ziel, 120.0))
        assertEquals(Err.INVALID_STOP, OrderPaar.fehler(stopp, ziel, 80.0))
        assertEquals(Err.INVALID_LIMIT, OrderPaar.fehler(stopp, order(OrderType.LIMIT, 85.0, "z")))
        assertEquals(Err.INVALID_QTY, OrderPaar.fehler(stopp, order(OrderType.LIMIT, 110.0, "z", stueck = 5.0)))
        assertEquals(Err.INVALID_LIMIT, OrderPaar.fehler(ziel, stopp))
        val leer = Account(cash = 1_000.0)
        assertEquals(leer, OrderPaar.anlegen(leer, stopp, order(OrderType.LIMIT, 85.0, "z")))
    }

    @Test fun zielStreichtDenStoppImLaufendenTakt() {
        val a = Trading.runOrders(paarKonto(), kurs(111.0, aufgabe + 60_000L), aufgabe + 60_000L, respectHours = false)
        assertEquals(OrderStatus.FILLED, a.o("z").status)
        assertEquals(OrderStatus.CANCELLED, a.o("s").status)
        assertEquals(OrderPaar.GRUND, a.o("s").reason)
        assertTrue(a.positions.isEmpty())
        assertEquals(1, a.transactions.count { it.type == TxType.SELL })
        // Ein spaeterer Kurs unter dem Stopp loest nichts mehr aus
        val b = Trading.runOrders(a, kurs(80.0, aufgabe + 120_000L), aufgabe + 120_000L, respectHours = false)
        assertEquals(a.orders, b.orders)
    }

    @Test fun stoppStreichtDasZielImLaufendenTakt() {
        val a = Trading.runOrders(paarKonto(), kurs(89.0, aufgabe + 60_000L), aufgabe + 60_000L, respectHours = false)
        assertEquals(OrderStatus.FILLED, a.o("s").status)
        assertEquals(OrderStatus.CANCELLED, a.o("z").status)
        assertEquals(OrderPaar.GRUND, a.o("z").reason)
        assertEquals("Verknüpfte Order ausgeführt", De.reason(a.o("z").reason))
        assertEquals("Linked order executed", En.reason(a.o("z").reason))
        assertTrue(a.positions.isEmpty())
        // Zwischen Stopp und Ziel bleibt das Paar offen
        val offen = Trading.runOrders(paarKonto(), kurs(100.0, aufgabe + 60_000L), aufgabe + 60_000L, respectHours = false)
        assertTrue(offen.orders.all { it.status == OrderStatus.OPEN })
    }

    private fun nachholen(a: Account, vararg k: Candle) = Nachholen.ausfuehren(a, seit, spaeter, mapOf("TEST" to Nachholen.Reihe(k.toList())))

    @Test fun nachholenStoppVorZielInDerselbenKerze() {
        val t = zeit("2026-03-02", 14, 5)
        // Die Kerze reicht von 85 bis 115: beide Marken liegen darin, der Stopp zaehlt
        val a = nachholen(paarKonto(), Candle(t, 100.0, 115.0, 85.0, 100.0))
        assertEquals(OrderStatus.FILLED, a.o("s").status)
        assertEquals(t, a.o("s").executedAt)
        assertEquals(OrderStatus.CANCELLED, a.o("z").status)
        assertEquals(OrderPaar.GRUND, a.o("z").reason)
        assertEquals(1, a.transactions.count { it.type == TxType.SELL })
        assertTrue(a.positions.isEmpty())
        // Auch wenn das Ziel in der Liste vor dem Stopp steht
        val gedreht = paarKonto().let { it.copy(orders = it.orders.reversed()) }
        val b = nachholen(gedreht, Candle(t, 100.0, 115.0, 85.0, 100.0))
        assertEquals(OrderStatus.FILLED, b.o("s").status)
        assertEquals(OrderStatus.CANCELLED, b.o("z").status)
    }

    @Test fun nachholenZielStreichtStoppUndUmgekehrt() {
        val t1 = zeit("2026-03-02", 14, 5)
        val t2 = zeit("2026-03-02", 14, 10)
        // Erst das Ziel erreicht, danach faellt der Kurs unter den Stopp: nur das Ziel wird ausgefuehrt
        val a = nachholen(paarKonto(), Candle(t1, 100.0, 112.0, 99.0, 111.0), Candle(t2, 111.0, 111.0, 80.0, 82.0))
        assertEquals(OrderStatus.FILLED, a.o("z").status)
        assertEquals(110.0, a.o("z").executedPrice, 1.0)
        assertEquals(OrderStatus.CANCELLED, a.o("s").status)
        assertEquals(t1, a.o("s").executedAt)
        assertEquals(1, a.transactions.count { it.type == TxType.SELL })
        // Erst der Stopp, danach steigt der Kurs ueber das Ziel
        val b = nachholen(paarKonto(), Candle(t1, 100.0, 101.0, 88.0, 89.0), Candle(t2, 89.0, 120.0, 89.0, 118.0))
        assertEquals(OrderStatus.FILLED, b.o("s").status)
        assertEquals(OrderStatus.CANCELLED, b.o("z").status)
        assertEquals(1, b.transactions.count { it.type == TxType.SELL })
        // Keine Marke erreicht: beide bleiben offen
        val c = nachholen(paarKonto(), Candle(t1, 100.0, 105.0, 95.0, 100.0))
        assertTrue(c.orders.all { it.status == OrderStatus.OPEN })
    }

    @Test fun einzelneOrdersBleibenEinzeln() {
        // Stopp und Limit ohne Verknuepfung: die Ausfuehrung der einen laesst die andere stehen
        val konto = Trading.placeOrder(Trading.placeOrder(Account(cash = 1_000.0, positions = listOf(bestand.copy(qty = 20.0)), letzterLauf = seit), stopp), ziel)
        assertNull(OrderPaar.partner(konto.orders, konto.o("s")))
        val a = Trading.runOrders(konto, kurs(111.0, aufgabe + 60_000L), aufgabe + 60_000L, respectHours = false)
        assertEquals(OrderStatus.FILLED, a.o("z").status)
        assertEquals(OrderStatus.OPEN, a.o("s").status)
        // Storniert der Nutzer eine Order des Paars, bleibt die andere als einzelne Order
        val storniert = Trading.cancelOrder(paarKonto(), "z", aufgabe + 1)
        assertNull(OrderPaar.partner(storniert.orders, storniert.o("s")))
        val b = Trading.runOrders(storniert, kurs(89.0, aufgabe + 60_000L), aufgabe + 60_000L, respectHours = false)
        assertEquals(OrderStatus.FILLED, b.o("s").status)
        assertEquals(OrderStatus.CANCELLED, b.o("z").status)
        assertEquals("", b.o("z").reason)
    }

    @Test fun geaenderteStueckzahlGiltFuerBeide() {
        val r = Trading.orderAendern(paarKonto(), "s", 4.0, 88.0, 0.0, Validity.GTC, aufgabe + 1)
        assertTrue(r.ok)
        assertEquals(4.0, r.account.o("s").qty, 1e-9)
        assertEquals(88.0, r.account.o("s").limitPrice, 1e-9)
        assertEquals(4.0, r.account.o("z").qty, 1e-9)
        assertEquals(110.0, r.account.o("z").limitPrice, 1e-9)
    }

    @Test fun altesSpeicherformatLaedtWeiter() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        // Order aus einer aelteren Fassung, ohne das Feld paar
        val alt = """{"id":"o1","key":"TEST","name":"Test","symbol":"TEST","side":"SELL","type":"STOP","qty":10.0,"limitPrice":90.0,"status":"OPEN","createdAt":5,"validity":"GTC","expiresAt":9}"""
        val o = json.decodeFromString(Order.serializer(), alt)
        assertEquals("", o.paar)
        assertEquals(OrderType.STOP, o.type)
        assertNull(OrderPaar.partner(listOf(o), o))
        // Ein Paar uebersteht Speichern und Laden
        val konto = paarKonto()
        val wieder = json.decodeFromString(Account.serializer(), json.encodeToString(Account.serializer(), konto))
        assertEquals(konto.orders, wieder.orders)
        assertNotNull(OrderPaar.partner(wieder.orders, wieder.o("s")))
    }

    // ------------------------------------------------------------ Texte

    @Test fun texteInBeidenSprachen() {
        assertEquals(StrategieTexte.schluessel(true), StrategieTexte.schluessel(false))
        for (k in StrategieTexte.schluessel(true)) for (s in listOf(De, En)) {
            val t = s.st(k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains("—") || t.contains("–") || t.contains("…") || t.contains("{"))
        }
        assertEquals("Stopp bei 2 ATR", De.st("atr.stopp", listOf("2")))
        assertEquals("", De.st("gibt.es.nicht"))
    }
}
