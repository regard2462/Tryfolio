package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Verkaufte Optionen im Uebungsdepot (0.77): Eroeffnen, Deckung, Rueckkauf, Verfall, Spreads, Bewertung und Sicherung.
class Fassung077V1StillhalterTest {

    private val tag = 86_400_000L
    private val basis = 20_000L * tag
    private fun d(n: Int) = basis + n * tag
    private val jetzt = d(1) + 10 * 3_600_000L
    private val verfall = d(30)
    // Freibetrag, der die Praemien in diesen Beispielen steuerfrei stellt
    private val frei = 1_000.0

    private fun option(kind: DerivativeKind, strike: Double, gruppe: String, verkauft: Boolean) = Derivative(
        id = Derivatives.idOf(kind, "ACME", strike, verfall, 1.0, gruppe + if (verkauft) "V" else ""),
        underlying = "ACME", underlyingName = "Acme", kind = kind, strike = strike, ratio = 1.0, expiry = verfall,
        vol = 0.3, createdAt = jetzt, ccy = "EUR", issuer = Derivatives.HOUSE, verkauft = verkauft, gruppe = gruppe,
    )
    private fun bein(d: Derivative, stueck: Double, preis: Double) = StillhalterDepot.Bein(d.id, "Acme Option", "ACME", stueck, d, preis)
    private fun aktie(stueck: Double, preis: Double = 100.0) = StillhalterDepot.Bein("ACME", "Acme", "ACME", stueck, null, preis)
    private fun aktien(qty: Double) = Position("ACME", "Acme", "ACME", qty, 90.0, d(0))
    private fun kurs(preis: Double, t: Long) = mapOf("ACME" to Quote("ACME", price = preis, prevClose = preis, updatedAt = t))
    private fun konto(cash: Double, vararg p: Position) = Account(
        cash = cash, deposited = cash, positions = p.toList(),
        transactions = listOf(Transaction("e", d(0), TxType.DEPOSIT, "Einzahlung", amount = cash)),
    )

    // Cash-Secured Put: 1 Kontrakt Put 100 zu 3,00
    private val put = option(DerivativeKind.PUT, 100.0, "G1", true)
    private fun csp(acc: Account = konto(20_000.0), allowance: Double = frei) =
        StillhalterDepot.eroeffnen(acc, listOf(bein(put, -100.0, 3.0)), "G1", 1.0, jetzt, allowance = allowance)

    @Test
    fun cashSecuredPutBindetBasispreisMalHundert() {
        val r = csp()
        assertTrue(r.ok)
        val a = r.account
        val p = a.positions.single()
        assertEquals(-100.0, p.qty, 0.0)
        assertTrue(p.verkauft)
        assertEquals(3.0, p.avgPrice, 1e-9)
        // Praemie aufs Guthaben, Gebuehr ab
        assertEquals(20_299.0, a.cash, 1e-9)
        assertEquals(10_000.0, a.gebunden, 1e-9)
        assertEquals(10_299.0, a.frei, 1e-9)
        val tx = a.transactions.first { it.type == TxType.SELL }
        assertEquals(StillhalterDepot.TAG_OEFFNEN, tx.tag)
        assertTrue(tx.verkaufteOption)
        assertEquals(-100.0, tx.bestandsDelta, 0.0)
        // Gebundenes Guthaben steht fuer Kaeufe nicht bereit
        assertFalse(Trading.buy(a, "ACME", "Acme", "ACME", 105.0, 100.0, now = jetzt).ok)
        assertTrue(Trading.buy(a, "ACME", "Acme", "ACME", 100.0, 100.0, now = jetzt).ok)
    }

    @Test
    fun praemieIstBeiZuflussSteuerpflichtig() {
        val a = csp(allowance = 0.0).account
        val tx = a.transactions.first { it.type == TxType.SELL }
        val erwartet = Tax.apply(Account().tax, 299.0, Tax.Kind.GENERAL, 0.0, jetzt).tax
        assertTrue(erwartet > 0)
        assertEquals(erwartet, tx.tax, 1e-9)
        assertEquals(20_299.0 - erwartet, a.cash, 1e-9)
    }

    @Test
    fun coveredCallBindetVorhandeneAktienOhneKauf() {
        val call = option(DerivativeKind.CALL, 110.0, "G2", true)
        val r = StillhalterDepot.eroeffnen(konto(1_000.0, aktien(100.0)), listOf(aktie(100.0), bein(call, -100.0, 2.0)), "G2", 1.0, jetzt, allowance = frei)
        assertTrue(r.ok)
        val a = r.account
        assertEquals(100.0, a.positions.first { it.key == "ACME" }.qty, 0.0)
        assertTrue(a.transactions.none { it.type == TxType.BUY })
        assertEquals(100.0, a.gebundeneStueck("ACME"), 0.0)
        assertEquals(0.0, a.gebunden, 1e-9)
        assertEquals(1_199.0, a.cash, 1e-9)
        // Gebundene Aktien lassen sich nicht verkaufen
        val v = Trading.sell(a, "ACME", 1.0, 100.0, jetzt)
        assertFalse(v.ok)
        assertEquals(Err.GEBUNDEN, v.error)
    }

    @Test
    fun coveredCallKauftFehlendeAktienMit() {
        val call = option(DerivativeKind.CALL, 110.0, "G3", true)
        val r = StillhalterDepot.eroeffnen(konto(20_000.0, aktien(30.0)), listOf(aktie(100.0), bein(call, -100.0, 2.0)), "G3", 1.0, jetzt, allowance = frei)
        assertTrue(r.ok)
        // 30 liegen schon im Depot, 70 werden gekauft
        assertEquals(100.0, r.account.positions.first { it.key == "ACME" }.qty, 1e-9)
        assertEquals(70.0, r.account.transactions.single { it.type == TxType.BUY }.qty, 1e-9)
        assertEquals(20_000.0 + 200.0 - 1.0 - 7_000.0 - 1.0, r.account.cash, 1e-9)
        assertEquals(100.0, r.account.gebundeneStueck("ACME"), 0.0)
        assertFalse(Trading.sell(r.account, "ACME", 1.0, 100.0, jetzt).ok)
    }

    @Test
    fun ungedecktWirdAbgelehnt() {
        val acc = konto(1_000_000.0)
        val call = option(DerivativeKind.CALL, 110.0, "G4", true)
        val nackt = StillhalterDepot.eroeffnen(acc, listOf(bein(call, -100.0, 2.0)), "G4", 1.0, jetzt)
        assertEquals(Err.UNGEDECKT, nackt.error)
        assertSame(acc, nackt.account)
        // Zwei verkaufte Calls gegen einen gekauften: unbegrenzt
        val lang = option(DerivativeKind.CALL, 100.0, "G4", false)
        assertEquals(Err.UNGEDECKT, StillhalterDepot.eroeffnen(acc, listOf(bein(lang, 100.0, 5.0), bein(call, -200.0, 2.0)), "G4", 1.0, jetzt).error)
        // Leerverkauf von Aktien
        assertEquals(Err.LEERVERKAUF, StillhalterDepot.eroeffnen(acc, listOf(aktie(-100.0)), "G4", 1.0, jetzt).error)
        // Put ohne genug Guthaben fuer den Basispreis
        val knapp = konto(5_000.0)
        val r = csp(knapp)
        assertFalse(r.ok)
        assertSame(knapp, r.account)
    }

    @Test
    fun rueckkaufRealisiertUndGibtBindungFrei() {
        val a = csp(allowance = 0.0).account
        val r = StillhalterDepot.rueckkauf(a, put.id, 100.0, 1.0, jetzt + tag)
        assertTrue(r.ok)
        val b = r.account
        assertTrue(b.positions.isEmpty())
        assertTrue(b.bindungen.isEmpty())
        assertEquals(b.cash, b.frei, 1e-9)
        val tx = b.transactions.first { it.tag == StillhalterDepot.TAG_RUECKKAUF }
        assertEquals(TxType.BUY, tx.type)
        assertEquals(200.0, tx.realized, 1e-9)
        assertEquals(100.0, tx.bestandsDelta, 0.0)
        // Steuer auf die Praemie und Erstattung beim Rueckkauf ergeben die Steuer auf das Ergebnis nach Gebuehren
        val gesamt = Tax.apply(Account().tax, 198.0, Tax.Kind.GENERAL, 0.0, jetzt).tax
        assertEquals(gesamt, b.transactions.sumOf { it.tax }, 0.02)
        assertEquals(20_000.0 + 300.0 - 100.0 - 2.0 - b.transactions.sumOf { it.tax }, b.cash, 1e-9)
        // Teilrueckkauf: Bindung sinkt anteilig
        val halb = StillhalterDepot.rueckkauf(a, put.id, 40.0, 1.0, jetzt + tag, allowance = frei).account
        assertEquals(-60.0, halb.positions.single().qty, 0.0)
        assertEquals(6_000.0, halb.gebunden, 1e-9)
        assertEquals(3.0, halb.positions.single().avgPrice, 1e-9)
    }

    @Test
    fun verfallWertlosGibtBindungFrei() {
        val a = csp().account
        val nach = verfall + 60_000L
        val b = Trading.maintain(a, kurs(110.0, nach), nach, allowance = frei)
        assertTrue(b.positions.isEmpty())
        assertTrue(b.bindungen.isEmpty())
        assertEquals(a.cash, b.cash, 1e-9)
        val tx = b.transactions.single { it.type == TxType.EXPIRY }
        assertEquals("EXPIRY_SOLD_WORTHLESS", tx.tag)
        assertEquals(300.0, tx.realized, 1e-9)
        assertEquals(100.0, tx.bestandsDelta, 0.0)
    }

    @Test
    fun verfallImGeldWirdAbgerechnet() {
        val a = csp().account
        val nach = verfall + 60_000L
        val b = Trading.maintain(a, kurs(90.0, nach), nach, allowance = frei)
        assertTrue(b.positions.isEmpty())
        assertTrue(b.bindungen.isEmpty())
        val tx = b.transactions.single { it.type == TxType.EXPIRY }
        assertEquals("EXPIRY_SOLD", tx.tag)
        assertEquals(10.0, tx.price, 1e-9)
        assertEquals(-700.0, tx.realized, 1e-9)
        assertEquals(a.cash - 1_000.0 - tx.tax, b.cash, 1e-9)
    }

    @Test
    fun coveredCallImGeldVerkauftAktienZumAusgleich() {
        val call = option(DerivativeKind.CALL, 110.0, "G5", true)
        val a = StillhalterDepot.eroeffnen(konto(0.0, aktien(100.0)), listOf(aktie(100.0), bein(call, -100.0, 2.0)), "G5", 1.0, jetzt, allowance = frei).account
        val nach = verfall + 60_000L
        val b = Trading.maintain(a, kurs(120.0, nach), nach, allowance = frei)
        assertTrue(b.positions.none { it.verkauft })
        assertTrue(b.bindungen.isEmpty())
        assertTrue(b.cash >= 0.0)
        assertTrue(b.transactions.any { it.type == TxType.SELL && it.via == StillhalterDepot.VIA_AUSGLEICH })
        assertTrue(b.positions.first { it.key == "ACME" }.qty < 100.0)
    }

    @Test
    fun spreadsBindenHoechstenVerlust() {
        val acc = konto(20_000.0)
        // Bull-Put-Spread: Put 100 verkauft zu 5, Put 90 gekauft zu 2
        val kurz = option(DerivativeKind.PUT, 100.0, "G6", true)
        val lang = option(DerivativeKind.PUT, 90.0, "G6", false)
        val a = StillhalterDepot.eroeffnen(acc, listOf(bein(kurz, -100.0, 5.0), bein(lang, 100.0, 2.0)), "G6", 1.0, jetzt, allowance = frei).account
        assertEquals(1_000.0, a.gebunden, 1e-9)
        // Das freie Guthaben sinkt um den hoechsten Verlust (Breite minus Netto-Praemie) und die Gebuehren
        assertEquals(700.0 + 2.0, acc.frei - a.frei, 1e-9)

        // Bear-Call-Spread: Call 100 verkauft zu 5, Call 110 gekauft zu 2
        val c1 = option(DerivativeKind.CALL, 100.0, "G7", true)
        val c2 = option(DerivativeKind.CALL, 110.0, "G7", false)
        val bc = StillhalterDepot.eroeffnen(acc, listOf(bein(c1, -100.0, 5.0), bein(c2, 100.0, 2.0)), "G7", 1.0, jetzt, allowance = frei).account
        assertEquals(1_000.0, bc.gebunden, 1e-9)
        // Das gekaufte Bein allein zu verkaufen liesse den Call ungedeckt
        assertEquals(Err.UNGEDECKT, Trading.sell(bc, c2.id, 100.0, 2.0, jetzt).error)

        // Bull-Call-Spread mit Lastschrift: kein Verlust ueber den Einsatz hinaus
        val l1 = option(DerivativeKind.CALL, 100.0, "G8", false)
        val k1 = option(DerivativeKind.CALL, 110.0, "G8", true)
        val bull = StillhalterDepot.eroeffnen(acc, listOf(bein(l1, 100.0, 5.0), bein(k1, -100.0, 2.0)), "G8", 1.0, jetzt, allowance = frei).account
        assertEquals(0.0, bull.gebunden, 1e-9)
        assertEquals(1, bull.bindungen.size)

        // Iron Condor: die breitere Seite bindet
        val ic = listOf(
            bein(option(DerivativeKind.PUT, 90.0, "G9", false), 100.0, 1.0),
            bein(option(DerivativeKind.PUT, 95.0, "G9", true), -100.0, 2.0),
            bein(option(DerivativeKind.CALL, 105.0, "G9", true), -100.0, 2.0),
            bein(option(DerivativeKind.CALL, 115.0, "G9", false), 100.0, 1.0),
        )
        assertEquals(1_000.0, StillhalterDepot.eroeffnen(acc, ic, "G9", 1.0, jetzt, allowance = frei).account.gebunden, 1e-9)

        // Butterfly: 1 / -2 / 1
        val fly = listOf(
            bein(option(DerivativeKind.CALL, 90.0, "GA", false), 100.0, 12.0),
            bein(option(DerivativeKind.CALL, 100.0, "GA", true), -200.0, 5.0),
            bein(option(DerivativeKind.CALL, 110.0, "GA", false), 100.0, 1.0),
        )
        val f = StillhalterDepot.eroeffnen(acc, fly, "GA", 1.0, jetzt, allowance = frei)
        assertTrue(f.ok)
        assertEquals(0.0, f.account.gebunden, 1e-9)

        // Gruppe zusammen schliessen
        val zu = StillhalterDepot.gruppeSchliessen(a, "G6", { p -> if (p.verkauft) 4.0 else 1.5 }, jetzt + tag, allowance = frei)
        assertTrue(zu.ok)
        assertTrue(zu.account.positions.isEmpty())
        assertTrue(zu.account.bindungen.isEmpty())
    }

    @Test
    fun hoechsteZahlungRechnetStueckweise() {
        val o = StillhalterDepot.Option(-100.0, true, 110.0)
        assertNull(StillhalterDepot.hoechsteZahlung(listOf(o), 0.0))
        assertEquals(0.0, StillhalterDepot.hoechsteZahlung(listOf(o), 100.0)!!, 1e-9)
        assertEquals(100.0, StillhalterDepot.aktienBedarf(listOf(o)), 0.0)
        assertEquals(9_500.0, StillhalterDepot.hoechsteZahlung(listOf(StillhalterDepot.Option(-100.0, false, 95.0)), 0.0)!!, 1e-9)
    }

    @Test
    fun depotwertZaehltVerbindlichkeit() {
        val a = csp().account
        // Ohne Kurs gilt der Einstand: Wert minus 300, Gesamtvermoegen nur um die Gebuehr kleiner
        val v = Trading.value(a, emptyMap(), jetzt)
        val vp = v.positions.single()
        assertEquals(-300.0, vp.value, 1e-9)
        assertEquals(-300.0, vp.invested, 1e-9)
        assertEquals(0.0, vp.profit, 1e-9)
        assertEquals(19_999.0, v.total, 1e-9)
        assertEquals(-1.0, v.profit, 1e-9)
        // Mit Kurs: Ergebnis ist Praemie minus Rueckkaufwert, Prozent auf die Praemie
        val mit = Trading.value(a, kurs(100.0, jetzt), jetzt).positions.single()
        assertTrue(mit.value < 0)
        assertEquals(mit.value + 300.0, mit.profit, 1e-9)
        assertEquals(mit.profit / 300.0 * 100.0, mit.profitPct, 1e-9)
        // Bestand rueckwirkend negativ
        assertEquals(-100.0, Trading.holdingsAt(a, jetzt + 1)[put.id]!!.qty, 0.0)
        assertFalse(Trading.holdingsAt(a, jetzt - 1).containsKey(put.id))
        assertEquals(-100.0, Depotzerlegung.bestand(a, jetzt + 1)[put.id]!!, 0.0)
    }

    @Test
    fun performanceMitVerbindlichkeit() {
        val offen = csp(allowance = 0.0).account
        val zu = d(3) + 10 * 3_600_000L
        val a = StillhalterDepot.rueckkauf(offen, put.id, 100.0, 1.0, zu).account
        val ende = a.cash
        // Kapitalbilanz: Kursgewinn gleich realisiertem Ergebnis
        val bil = Performanceanalyse.bilanz(a.transactions, d(0) - 1, d(4), 0.0, ende)
        assertEquals(20_000.0, bil.einlagen, 1e-9)
        assertEquals(200.0, bil.realisiert, 1e-9)
        assertEquals(200.0, bil.kursgewinne, 1e-6)
        assertEquals(2.0, bil.gebuehren, 1e-9)

        // Bilanz je Wertpapier: Wert am Tagesende 1: -300, Tag 2: -200, Tag 3: 0
        val werte = mapOf(1 to -300.0, 2 to -200.0)
        val tage = Depotzerlegung.tage(d(1), d(3), listOf(put.id), Depotzerlegung.zufluesse(a)) { _, t -> werte[((t - basis) / tag).toInt()] ?: 0.0 }
        val e = Wertpapierbilanz.berechnen(tage, a.transactions, emptyMap(), emptyMap(), emptySet(), d(1), d(3))
        val z = e.zeilen.single()
        assertEquals(200.0, z.realisiert, 1e-6)
        assertEquals(0.0, z.unrealisiert, 1e-6)
        assertEquals(2.0, z.gebuehren, 1e-9)
        assertEquals(a.transactions.sumOf { it.tax }, z.steuern, 1e-9)
        assertEquals(200.0 - 2.0 - z.steuern, z.summe, 1e-6)
        assertNull(z.ttwror)
        assertNull(z.izf)
        // Waehrend die Option offen ist: Ergebnis aus Praemie und Verbindlichkeit
        val tage2 = Depotzerlegung.tage(d(1), d(2), listOf(put.id), Depotzerlegung.zufluesse(offen)) { _, t -> werte[((t - basis) / tag).toInt()] ?: 0.0 }
        val z2 = Wertpapierbilanz.berechnen(tage2, offen.transactions, emptyMap(), emptyMap(), emptySet(), d(1), d(2)).zeilen.single()
        assertEquals(100.0, z2.unrealisiert, 1e-6)
    }

    @Test
    fun umsatzExportKenntVerkaufteOptionen() {
        val a = StillhalterDepot.rueckkauf(csp(allowance = 0.0).account, put.id, 100.0, 1.0, jetzt + tag).account
        val zeilen = PortfolioPerformance.csv(a, { "" }, true).lines().filter { it.isNotBlank() }
        assertTrue(zeilen.any { it.contains("Verkauf") })
        assertTrue(zeilen.any { it.contains("Kauf") && !it.contains("Verkauf") })
        assertTrue(zeilen.any { it.contains("Steuer") })
    }

    @Test
    fun alteSicherungLaedtUnveraendert() {
        val alt = "{\"app\":\"troetenrepublik\",\"format\":2,\"account\":{\"cash\":5.0,\"positions\":[" +
            "{\"key\":\"X\",\"name\":\"X\",\"symbol\":\"X\",\"qty\":2.0,\"avgPrice\":10.0,\"openedAt\":1}," +
            "{\"key\":\"D\",\"name\":\"D\",\"symbol\":\"X\",\"qty\":3.0,\"avgPrice\":1.0,\"openedAt\":1," +
            "\"derivative\":{\"id\":\"D\",\"underlying\":\"X\",\"underlyingName\":\"X\",\"kind\":\"CALL\",\"strike\":10.0}}]}}"
        val p = (Backup.parse(alt) as Backup.Outcome.Ok).payload
        assertTrue(p.account.bindungen.isEmpty())
        assertEquals(2, p.account.positions.size)
        assertTrue(p.account.positions.none { it.verkauft })
        assertEquals("", p.account.positions[1].derivative!!.gruppe)
        assertEquals(5.0, p.account.frei, 1e-9)
        // Negative Stueckzahl ohne verkaufte Option bleibt ein Fehler
        val kaputt = alt.replace("\"qty\":2.0", "\"qty\":-2.0")
        assertTrue(Backup.parse(kaputt) is Backup.Outcome.Error)
    }

    @Test
    fun neueSicherungMitVerkaufterOptionLaedt() {
        val a = csp().account
        val text = Backup.export(a, Settings(), jetzt)
        val b = (Backup.parse(text) as Backup.Outcome.Ok).payload.account
        assertEquals(a.positions, b.positions)
        assertEquals(a.bindungen, b.bindungen)
        assertEquals(a.frei, b.frei, 1e-9)
        assertTrue(b.positions.single().verkauft)
    }

    @Test
    fun zusammenfuehrenBehaeltBindung() {
        val a = csp().account
        val z = Zusammenfuehren.konten(a, konto(1_000.0, aktien(5.0)))
        assertEquals(a.bindungen, z.bindungen)
        assertEquals(-100.0, z.positions.first { it.key == put.id }.qty, 0.0)
        assertTrue(StillhalterDepot.aktienGedeckt(z))
    }
}
