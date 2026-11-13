package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Snapshot
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.BrokerExport.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Grenze der Buchungen, Kaufposten bei laengerem Zeitraum, Steuertoepfe, Zusammenfuehren bei Ueberschneidung,
// groesster Rueckgang und Guthaben bei DEGIRO.
class ImportFolgenZweiTest {

    private val jetzt = Instant.parse("2025-06-01T12:00:00Z").toEpochMilli()
    private val kuerzel = mapOf("DE0000000001" to "BSP.DE", "IE0000000003" to "ETF.DE", "BTC" to "BTC-EUR", "US0000000002" to "USB")

    private fun res(name: String) = javaClass.getResourceAsStream("/import/$name")!!.readBytes().toString(Charsets.UTF_8)

    private val kopf = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\""
    private fun z(t: String, kat: String, typ: String, kl: String, name: String, sym: String, sh: String, pr: String, am: String, fee: String = "", tax: String = "", i: String) =
        "\"$t\",\"${t.take(10)}\",\"DEFAULT\",\"$kat\",\"$typ\",\"$kl\",\"$name\",\"$sym\",\"$sh\",\"$pr\",\"$am\",\"$fee\",\"$tax\",\"EUR\",\"\",\"\",\"\",\"\",\"$i\",\"\",\"\",\"\",\"\""
    private fun datei(vararg zeilen: String) = (listOf(kopf) + zeilen).joinToString("\n")
    private fun einzahlung(i: String = "e") = z("2025-01-02T09:00:00Z", "CASH", "CUSTOMER_INPAYMENT", "", "", "", "", "", "5000", i = i)

    // ------------------------------------------------------------ Grenze der Buchungen

    private fun fuellung(n: Int) = (0 until n).map {
        Transaction("x$it", 1_000L + it, TxType.BUY, "X", amount = -1.0, qty = 1.0, price = 1.0, symbol = "X", key = "X")
    }

    @Test(timeout = 20_000)
    fun grenzeHaeltVieleBuchungenUndKapptErstDarueber() {
        // 20.000 Buchungen (ein langer Import) bleiben vollstaendig
        assertEquals(20_000, Trading.kuerzen(fuellung(20_000)).size)
        assertEquals(Trading.MAX_BUCHUNGEN, Trading.kuerzen(fuellung(Trading.MAX_BUCHUNGEN)).size)
        // Darueber fallen die aeltesten, Dividenden bleiben
        val mit = fuellung(Trading.MAX_BUCHUNGEN + 5) + Transaction("dv", 1L, TxType.DIVIDEND, "D", amount = 1.0)
        val k = Trading.kuerzen(mit)
        assertEquals(Trading.MAX_BUCHUNGEN, k.size)
        assertTrue(k.any { it.id == "dv" })
    }

    @Test(timeout = 30_000)
    fun vorschauWarntVorDerGrenze() {
        val depot = Account(transactions = fuellung(Trading.MAX_BUCHUNGEN - 3))
        val zeilen = TradeRepublicImport.lesen(res("tr_export.csv"))
        val e = TradeRepublicImport.vorschau(zeilen, kuerzel, depot, jetzt)
        // 15 Buchungen kommen dazu: 12 zu viel beim Zusammenfuehren, beim Ersetzen nichts
        assertEquals(15 - 3, e.ueberGrenze)
        val leer = TradeRepublicImport.vorschau(zeilen, kuerzel, Account(), jetzt)
        assertEquals(0, leer.ueberGrenze)
    }

    @Test(timeout = 60_000)
    fun zwanzigtausendBuchungenLaufenDurch() {
        val t0 = 1_400_000_000_000L
        val tx = ArrayList<Transaction>()
        tx += Transaction("d0", t0, TxType.DEPOSIT, "E", amount = 1_000_000.0, tag = "DEPOSIT")
        var cash = 1_000_000.0
        var i = 1
        while (tx.size < 20_000) {
            cash -= 100.0
            tx += Transaction("b$i", t0 + i * 10_800_000L, TxType.BUY, "W", amount = -100.0, qty = 1.0, price = 100.0, symbol = "W${i % 60}", key = "W${i % 60}")
            i++
        }
        val a = Account(cash = cash, deposited = 1_000_000.0, transactions = tx)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val text = json.encodeToString(Account.serializer(), a)
        assertEquals(20_000, json.decodeFromString(Account.serializer(), text).transactions.size)
        val reihe = (0 until 3000).map { (t0 + it * 72_000_000L) to (100_000.0 + it) }
        assertTrue(Performanceanalyse.berechnen(reihe, a.transactions, a.cash, 0L, reihe.last().first) != null)
        assertEquals(20_000, Trading.kuerzen(a.transactions).size)
    }

    // ------------------------------------------------------------ Kaufposten bei laengerem Zeitraum

    @Test(timeout = 20_000)
    fun kaufpostenBleibenBeiLaengeremZeitraum() {
        val k1 = z("2025-01-03T10:00:00Z", "TRADING", "BUY", "STOCK", "Beispiel AG", "DE0000000001", "10", "50", "-500", i = "a")
        val k2 = z("2025-02-03T10:00:00Z", "TRADING", "BUY", "STOCK", "Beispiel AG", "DE0000000001", "10", "70", "-700", i = "b")
        val vk = z("2025-03-03T10:00:00Z", "TRADING", "SELL", "STOCK", "Beispiel AG", "DE0000000001", "-5", "80", "400", i = "c")
        val frueh = TradeRepublicImport.konto(TradeRepublicImport.lesen(datei(einzahlung(), k1, k2)), kuerzel, Account(), jetzt).konto
        val voll = TradeRepublicImport.konto(TradeRepublicImport.lesen(datei(einzahlung(), k1, k2, vk)), kuerzel, Account(), jetzt).konto
        // FIFO: die ersten fuenf Stuecke zu 50 gehen, 5 zu 50 und 10 zu 70 bleiben
        val p = voll.positions.single()
        assertEquals(15.0, p.qty, 1e-9)
        assertEquals(listOf(5.0 to 50.0, 10.0 to 70.0), p.lots.map { it.qty to it.price })
        assertEquals(950.0 / 15, p.avgPrice, 1e-9)
        assertEquals(150.0, voll.transactions.single { it.type == TxType.SELL }.realized, 1e-9)
        // Der zweite, laengere Export auf das Depot des ersten
        val e = TradeRepublicImport.vorschau(TradeRepublicImport.lesen(datei(einzahlung(), k1, k2, vk)), kuerzel, frueh, jetzt)
        val q = e.ergaenzung!!.positions.single()
        assertEquals(p.lots.map { it.qty to it.price }, q.lots.map { it.qty to it.price })
        assertEquals(voll.cash, e.ergaenzung!!.cash, 1e-9)
        assertEquals(voll.transactions.size, e.ergaenzung!!.transactions.size)
        assertEquals(150.0, e.ergaenzung!!.transactions.single { it.type == TxType.SELL }.realized, 1e-9)
    }

    // ------------------------------------------------------------ Steuertoepfe

    @Test(timeout = 20_000)
    fun steuertoepfeAusDenVerkaeufen() {
        val csv = datei(
            einzahlung(),
            z("2025-01-03T10:00:00Z", "TRADING", "BUY", "STOCK", "Beispiel AG", "DE0000000001", "10", "100", "-1000", i = "a"),
            z("2025-01-04T10:00:00Z", "TRADING", "BUY", "FUND", "Welt ETF", "IE0000000003", "10", "50", "-500", i = "b"),
            // Aktie mit 300 Verlust, ETF mit 100 Gewinn und 26,38 einbehaltener Steuer
            z("2025-02-03T10:00:00Z", "TRADING", "SELL", "STOCK", "Beispiel AG", "DE0000000001", "-10", "70", "700", i = "c"),
            z("2025-02-04T10:00:00Z", "TRADING", "SELL", "FUND", "Welt ETF", "IE0000000003", "-10", "60", "600", tax = "-20", i = "d"),
        )
        val k = TradeRepublicImport.konto(TradeRepublicImport.lesen(csv), kuerzel, Account(), jetzt).konto
        // Der Aktienverlust steht im Aktien-Verlusttopf und mindert den ETF-Gewinn nicht
        assertEquals(300.0, k.tax.lossPotStocks, 1e-9)
        assertEquals(0.0, k.tax.lossPotGeneral, 1e-9)
        assertEquals(2025, k.tax.year)
        assertEquals(20.0, k.tax.taxPaid, 1e-9)
    }

    @Test(timeout = 20_000)
    fun steuertoepfeNehmenVerlustInsNaechsteJahr() {
        val csv = datei(
            einzahlung(),
            z("2024-01-03T10:00:00Z", "TRADING", "BUY", "STOCK", "Beispiel AG", "DE0000000001", "10", "100", "-1000", i = "a"),
            z("2024-06-03T10:00:00Z", "TRADING", "SELL", "STOCK", "Beispiel AG", "DE0000000001", "-10", "80", "800", i = "b"),
        )
        val k = TradeRepublicImport.konto(TradeRepublicImport.lesen(csv), kuerzel, Account(), jetzt).konto
        assertEquals(200.0, k.tax.lossPotStocks, 1e-9)
        assertEquals(2025, k.tax.year)
    }

    // ------------------------------------------------------------ Zusammenfuehren bei Ueberschneidung

    @Test(timeout = 20_000)
    fun zusammenfuehrenZweierSicherungenMitUeberschneidung() {
        val zeilen = TradeRepublicImport.lesen(res("tr_export.csv"))
        val ref = TradeRepublicImport.konto(zeilen, kuerzel, Account(), jetzt).konto
        // Sicherung A bis Mitte, Sicherung B ab Mitte; die Mitte steht in beiden
        val a = TradeRepublicImport.konto(zeilen.take(8), kuerzel, Account(), jetzt).konto
        val b = TradeRepublicImport.konto(zeilen.drop(5), kuerzel, Account(), jetzt).konto
        val z = Zusammenfuehren.konten(a, b)
        assertEquals(ref.transactions.size, z.transactions.size)
        assertEquals(ref.cash, z.cash, 1e-6)
        assertEquals(ref.deposited, z.deposited, 1e-9)
        assertEquals(ref.withdrawn, z.withdrawn, 1e-9)
        assertEquals(ref.positions.associate { it.key to it.qty }, z.positions.associate { it.key to it.qty })
        // Die andere Reihenfolge ergibt dasselbe
        val y = Zusammenfuehren.konten(b, a)
        assertEquals(ref.cash, y.cash, 1e-6)
        assertEquals(ref.deposited, y.deposited, 1e-9)
        // Noch einmal mit dem Ergebnis ist ein Nichts
        val w = Zusammenfuehren.konten(z, a)
        assertEquals(z.cash, w.cash, 1e-9)
        assertEquals(z.positions, w.positions)
    }

    // ------------------------------------------------------------ groesster Rueckgang

    @Test(timeout = 20_000)
    fun rueckgangAusKurveFuerUebernommenesDepot() {
        val t = { s: String -> Instant.parse(s).toEpochMilli() }
        val tx = listOf(
            Transaction("d", t("2025-01-02T09:00:00Z"), TxType.DEPOSIT, "E", amount = 1000.0),
            Transaction("k", t("2025-01-03T10:00:00Z"), TxType.BUY, "W", amount = -501.0, qty = 10.0, price = 50.0, symbol = "W", key = "W"),
        )
        // Verlauf erst seit dem 1.5. aufgezeichnet: Luecke
        val acc = Account(cash = 499.0, transactions = tx, positions = listOf(Position("W", "W", "W", 10.0, 50.0, tx[1].ts)),
            history = listOf(Snapshot(t("2025-05-01T12:00:00Z"), 999.0, 500.0, 0.0)))
        assertTrue(Rueckgang.luecke(acc))
        assertTrue(!Rueckgang.luecke(acc.copy(history = listOf(Snapshot(t("2025-01-03T12:00:00Z"), 999.0, 500.0, 0.0)))))
        // Wert der Wertpapiere 500, 550, 440, 520; Gesamtwert mit 499 Guthaben 999, 1049, 939, 1019
        val reihe = listOf(
            t("2025-01-03T12:00:00Z") to 500.0, t("2025-02-03T12:00:00Z") to 550.0,
            t("2025-03-03T12:00:00Z") to 440.0, t("2025-04-03T12:00:00Z") to 520.0,
        )
        val r = Rueckgang.ausKurve(reihe, acc, t("2025-04-03T12:00:00Z"))!!
        assertEquals((939.0 / 1049.0 - 1) * 100, r, 1e-9)
        // Derselbe Wert fuer Analytics und Widget: beide lesen ihn aus AppState.maxRueckgang, die Rechnung ist diese
        assertTrue(r < 0)
    }

    // ------------------------------------------------------------ DEGIRO: Guthaben

    @Test(timeout = 20_000)
    fun degiroTransaktionenAlleinGebenNegativesGuthabenMitKontoauszugNicht() {
        fun konto(f: Format, t: String) = TradeRepublicImport.konto(BrokerExport.lesen(f, t), kuerzel, Account(), jetzt, f.anzeige, f.praefix).konto
        val tx = konto(Format.DEGIRO, res("degiro_transactions.csv"))
        val kontoauszug = konto(Format.DEGIRO_KONTO, res("degiro_konto.csv"))
        // Allein: negativ, die Vorschau sperrt die Uebernahme
        assertTrue(tx.cash < 0)
        // Mit dem Kontoauszug zusammen: die Einzahlungen decken die Kaeufe
        val zusammen = Zusammenfuehren.konten(kontoauszug, tx)
        assertTrue(zusammen.cash >= 0)
        assertEquals(kontoauszug.cash + tx.cash, zusammen.cash, 1e-6)
        assertEquals(1000.0, zusammen.deposited, 1e-9)
        // Die Vorschau meldet den Aufbau von DEGIRO als nicht an einem echten Export belegt
        assertTrue(Format.DEGIRO.ungeprueft && Format.ING.ungeprueft && Format.FLATEX.ungeprueft && !Format.TRADING212.ungeprueft)
    }

    @Test(timeout = 20_000)
    fun zeitzonenversatzWirdUmgerechnet() {
        assertEquals(Instant.parse("2022-07-02T06:53:13Z").toEpochMilli(), BrokerExport.zeit("2022-07-02T08:53:13+02:00"))
        assertEquals(Instant.parse("2022-07-02T13:53:13Z").toEpochMilli(), BrokerExport.zeit("2022-07-02T08:53:13-0500"))
        assertEquals(Instant.parse("2022-07-02T08:53:13Z").toEpochMilli(), BrokerExport.zeit("2022-07-02T08:53:13Z"))
    }
}
