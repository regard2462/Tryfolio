package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.TradeRepublicImport.Zeile

// CSV-Exporte von Brokern, an der Kopfzeile erkannt und in Zeilen des Trade-Republic-Imports uebersetzt.
// Vorzeichen: stueck bei Kauf positiv, betrag ohne Gebuehr und Steuer bei Kauf negativ, gebuehr und steuer negativ.
// Die Datei wird nur gelesen, nichts davon verlaesst das Geraet.
object BrokerExport {

    enum class Format(val anzeige: String, val praefix: String) {
        TRADE_REPUBLIC("Trade Republic", "tr"),
        SCALABLE("Scalable Capital", "sc"),
        TRADING212("Trading 212", "t212"),
        DEGIRO("DEGIRO", "dg"),
        DEGIRO_KONTO("DEGIRO", "dgk"),
        IBKR("Interactive Brokers", "ib"),
        REVOLUT("Revolut", "rv"),
        ING("ING", "ing"),
        FLATEX("flatex", "fx"),
        BITPANDA("Bitpanda", "bp"),
    }

    val namen: List<String> get() = Format.entries.map { it.anzeige }.distinct()

    // Kopfzeile in Kleinbuchstaben, ohne Byte-Order-Mark und Anfuehrungszeichen.
    internal fun kopf(text: String): String =
        text.trimStart('\uFEFF').lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().lowercase().replace("\"", "")

    // Manche Exporte haben vor der Kopfzeile einige Zeilen Vorspann.
    private fun anfang(text: String): List<String> =
        text.trimStart('\uFEFF').lineSequence().take(20).map { it.lowercase().replace("\"", "") }.toList()

    // null, wenn das Format unbekannt ist.
    fun erkennen(text: String): Format? {
        if (TradeRepublicImport.erkennen(text)) return Format.TRADE_REPUBLIC
        val k = kopf(text)
        fun hat(vararg w: String) = w.all { it in k }
        val a = anfang(text)
        fun zeile(vararg w: String) = a.any { z -> w.all { it in z } }
        return when {
            hat("status", "reference", "assettype", "isin", "shares", "amount") -> Format.SCALABLE
            k.startsWith("action,") && hat("no. of shares", "isin") -> Format.TRADING212
            hat("isin") && (hat("order id") || hat("order-id")) && (hat("local value") || hat("lokalwährung") || hat("lokalwahrung") || hat("lokale waarde")) -> Format.DEGIRO
            (hat("value date") || hat("valutadatum")) && (hat("description") || hat("beschreibung") || hat("omschrijving")) && (hat("change") || hat("änderung") || hat("mutatie")) -> Format.DEGIRO_KONTO
            zeile("currencyprimary") || zeile("clientaccountid") || zeile("buy/sell", "tradeprice") -> Format.IBKR
            hat("ticker", "type", "quantity", "price per share", "total amount") -> Format.REVOLUT
            zeile("orderart", "isin", "ausführungskurs") -> Format.ING
            zeile("buchtag", "isin", "nominal", "buchungsinformationen") -> Format.FLATEX
            zeile("transaction id", "transaction type", "amount fiat", "amount asset") -> Format.BITPANDA
            else -> null
        }
    }

    // Zahl in deutscher (1.234,56) oder englischer (1,234.56) Schreibweise, auch mit Waehrung oder Prozent.
    // Kommt nur ein Trenner vor, entscheidet deutsch, ob er das Komma ist.
    internal fun zahl(roh: String, deutsch: Boolean): Double? {
        var s = roh.trim().replace("\u00A0", "").replace(" ", "").replace("'", "")
        if (s.isEmpty()) return null
        val minus = s.startsWith("-") || s.startsWith("(") && s.endsWith(")") || s.endsWith("-")
        s = s.filter { it.isDigit() || it == '.' || it == ',' }
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val punkt = s.lastIndexOf('.'); val komma = s.lastIndexOf(',')
        s = when {
            punkt >= 0 && komma >= 0 -> if (komma > punkt) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            komma >= 0 -> if (deutsch || s.count { it == ',' } == 1 && s.length - komma - 1 != 3) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            punkt >= 0 && deutsch && s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        val w = s.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return if (minus) -w else w
    }

    // Tage seit dem 1.1.1970 (proleptischer gregorianischer Kalender).
    private fun tage(j: Int, m: Int, t: Int): Long {
        val y = if (m <= 2) j - 1 else j
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + t - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    // Millisekunden aus 2025-01-31, 31.01.2025, 31-01-2025 oder 31/01/2025, mit oder ohne Uhrzeit, auch ISO mit T und Z.
    // Ohne Uhrzeit gilt 12 Uhr, Ortszeit wird wie UTC behandelt. 0, wenn nichts passt.
    internal fun zeit(datum: String, uhrzeit: String = ""): Long {
        val s = datum.trim()
        val iso = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)
        val eu = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](\d{4})""").find(s)
        val (j, m, t) = when {
            iso != null -> iso.groupValues.drop(1).map { it.toInt() }
            eu != null -> eu.groupValues.drop(1).map { it.toInt() }.let { listOf(it[2], it[1], it[0]) }
            else -> return 0L
        }
        if (m !in 1..12 || t !in 1..31) return 0L
        val rest = s.substring((iso ?: eu)!!.range.last + 1) + " " + uhrzeit
        val u = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(rest)
        val ms = if (u == null) 12 * 3_600_000L else
            (u.groupValues[1].toLong() * 3600 + u.groupValues[2].toLong() * 60 + (u.groupValues[3].toLongOrNull() ?: 0L)) * 1000L
        return tage(j, m, t) * 86_400_000L + ms
    }

    // Index der ersten Spalte, deren Kopf einem der namen gleicht, ohne Unterschied in Gross- und Kleinschreibung.
    internal class Spalten(kopf: List<String>) {
        private val k = kopf.map { it.trim().trimStart('\uFEFF').lowercase() }
        fun idx(vararg namen: String): Int = namen.firstNotNullOfOrNull { n -> k.indexOf(n.lowercase()).takeIf { it >= 0 } } ?: -1
        fun enthaelt(vararg namen: String): Int = namen.firstNotNullOfOrNull { n -> k.indexOfFirst { n.lowercase() in it }.takeIf { it >= 0 } } ?: -1
    }

    internal fun List<String>.feld(i: Int): String = if (i in indices) this[i].trim() else ""

    // Semikolon, Tabulator oder Komma, je nachdem, was in der Kopfzeile am haeufigsten vorkommt.
    internal fun trenner(text: String): Char {
        val k = text.trimStart('\uFEFF').lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return listOf(';', '\t', ',').maxBy { c -> k.count { it == c } }
    }

    // Aelteste Zeile zuerst.
    fun lesen(format: Format, text: String): List<Zeile> = when (format) {
        Format.TRADE_REPUBLIC -> TradeRepublicImport.lesen(text)
        Format.SCALABLE -> scalable(text)
        Format.TRADING212 -> trading212(text)
        Format.DEGIRO -> degiro(text)
        Format.DEGIRO_KONTO -> degiroKonto(text)
        Format.IBKR -> ibkr(text)
        Format.REVOLUT -> revolut(text)
        Format.ING -> mitEinzahlung(ing(text))
        Format.FLATEX -> mitEinzahlung(flatex(text))
        Format.BITPANDA -> bitpanda(text)
    }.sortedBy { it.zeit }

    // Kennung aus dem Inhalt: dieselbe Buchung in zwei zusammengefuehrten Dateien zaehlt nur einmal,
    // gleiche Zeilen in einer Datei werden durchgezaehlt.
    private class Kennungen {
        private val gesehen = HashMap<String, Int>()
        operator fun invoke(r: List<String>): String {
            val s = r.joinToString("\u0001") { it.trim() }
            val h = (s.hashCode().toLong() shl 32) xor (s.reversed().hashCode().toLong() and 0xffffffffL)
            val k = h.toULong().toString(16)
            val n = gesehen.getOrPut(k) { 0 }
            gesehen[k] = n + 1
            return if (n == 0) k else "$k-$n"
        }
    }

    private val ISIN = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")

    fun istIsin(s: String): Boolean = ISIN.matches(s.trim().uppercase())

    private fun zeile(
        zeit: Long, typ: String, name: String = "", symbol: String = "", stueck: Double? = null, preis: Double? = null,
        betrag: Double = 0.0, gebuehr: Double = 0.0, steuer: Double = 0.0, id: String, klasse: String = if (symbol.isBlank()) "" else "STOCK",
        text: String = "",
    ) = Zeile(zeit, if (stueck != null) "TRADING" else "CASH", typ, klasse, name, symbol.trim(), stueck, preis, betrag, gebuehr, steuer, text, id)

    // Die erste Zeile, auf die istKopf passt, und alles darunter.
    private fun tabelle(text: String, trenner: Char = trenner(text), istKopf: (List<String>) -> Boolean = { true }): Pair<Spalten, List<List<String>>>? {
        val alle = TradeRepublicImport.csv(text, trenner)
        val i = alle.indexOfFirst { r -> istKopf(r.map { it.trim().lowercase() }) }
        if (i < 0) return null
        return Spalten(alle[i]) to alle.drop(i + 1)
    }

    // Exporte nur mit Orders: vorne kommt eine errechnete Einzahlung in Hoehe des tiefsten Guthabens dazu,
    // damit das Guthaben nicht unter null faellt.
    private fun mitEinzahlung(z: List<Zeile>): List<Zeile> {
        if (z.isEmpty()) return z
        var geld = 0.0
        var tiefst = 0.0
        for (r in z.sortedBy { it.zeit }) { geld += r.betrag + r.gebuehr + r.steuer; tiefst = minOf(tiefst, geld) }
        if (tiefst >= -0.005) return z
        val erst = z.minOf { it.zeit }
        return listOf(zeile(erst - 1, "DEPOSIT", name = "Einzahlung (aus den Orders errechnet)", betrag = Math.round(-tiefst * 100) / 100.0, id = "einzahlung")) + z
    }

    // ------------------------------------------------------------ Scalable Capital

    // Broker > Transaktionen > Export:
    // date;time;status;reference;description;assetType;type;isin;shares;price;amount;fee;tax;currency.
    private fun scalable(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ';') { "assettype" in it } ?: return emptyList()
        val kennung = Kennungen()
        val iDat = s.idx("date"); val iZeit = s.idx("time"); val iStatus = s.idx("status"); val iRef = s.idx("reference")
        val iName = s.idx("description"); val iTyp = s.idx("type"); val iIsin = s.idx("isin"); val iStueck = s.idx("shares")
        val iPreis = s.idx("price"); val iBetrag = s.idx("amount"); val iGeb = s.idx("fee"); val iSteuer = s.idx("tax")
        return daten.mapIndexedNotNull { n, r ->
            if (!r.feld(iStatus).equals("executed", true)) return@mapIndexedNotNull null
            val zeit = zeit(r.feld(iDat), r.feld(iZeit))
            val typ = r.feld(iTyp).lowercase()
            val stueck = zahl(r.feld(iStueck), true) ?: 0.0
            val betrag = zahl(r.feld(iBetrag), true) ?: 0.0
            val gebuehr = -kotlin.math.abs(zahl(r.feld(iGeb), true) ?: 0.0)
            val steuer = -(zahl(r.feld(iSteuer), true) ?: 0.0)
            val id = kennung(r)
            val name = r.feld(iName); val isin = r.feld(iIsin)
            when {
                typ == "buy" || typ == "savings plan" -> if (stueck == 0.0) null else
                    zeile(zeit, "BUY", name, isin, kotlin.math.abs(stueck), zahl(r.feld(iPreis), true), -kotlin.math.abs(betrag), gebuehr, steuer, id)
                typ == "sell" -> if (stueck == 0.0) null else
                    zeile(zeit, "SELL", name, isin, -kotlin.math.abs(stueck), zahl(r.feld(iPreis), true), kotlin.math.abs(betrag), gebuehr, steuer, id)
                typ == "distribution" -> zeile(zeit, "DIVIDEND", name, isin, betrag = betrag, gebuehr = gebuehr, steuer = steuer, id = id)
                typ == "interest" -> zeile(zeit, "INTEREST", name, betrag = betrag, steuer = steuer, id = id)
                typ == "deposit" -> zeile(zeit, "DEPOSIT", betrag = kotlin.math.abs(betrag), id = id)
                typ == "withdrawal" -> zeile(zeit, "WITHDRAWAL", betrag = -kotlin.math.abs(betrag), id = id)
                typ == "fee" -> zeile(zeit, "FEE", name, betrag = -kotlin.math.abs(betrag), id = id)
                typ.startsWith("tax") -> zeile(zeit, "TAX", name, betrag = betrag, id = id)
                else -> null
            }
        }
    }

    // ------------------------------------------------------------ Trading 212

    // Verlauf > Export (CSV, englische Spalten, Spalten je nach Zeitraum): Action, Time, ISIN, Ticker, Name,
    // No. of shares, Price / share, Total (in der Kontowaehrung, bei Kauf und Verkauf ohne Vorzeichen, mit Gebuehren),
    // dazu Gebuehrenspalten mit eigener Waehrungsspalte. Dividenden netto.
    private fun trading212(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ',') { it.firstOrNull() == "action" } ?: return emptyList()
        val kennung = Kennungen()
        val kopf = TradeRepublicImport.csv(text, ',').first().map { it.trim().lowercase() }
        val iAkt = s.idx("action"); val iZeit = s.enthaelt("time"); val iIsin = s.idx("isin"); val iTicker = s.idx("ticker")
        val iName = s.idx("name"); val iStueck = s.idx("no. of shares"); val iTotal = s.idx("total").takeIf { it >= 0 } ?: kopf.indexOfFirst { it.startsWith("total (") }
        val iTotalW = s.idx("currency (total)")
        val gebuehren = listOf("transaction fee", "finra fee", "stamp duty", "stamp duty reserve tax", "french transaction tax", "currency conversion fee", "deposit fee", "charge amount")
        val gebSpalten = kopf.indices.filter { i -> val h = kopf[i]; gebuehren.any { h == it || h.startsWith("$it (") } }
        return daten.mapIndexedNotNull { n, r ->
            val akt = r.feld(iAkt).lowercase()
            val zeit = zeit(r.feld(iZeit))
            val total = zahl(r.feld(iTotal), false) ?: 0.0
            val waehrung = r.feld(iTotalW)
            val geb = gebSpalten.sumOf { i ->
                val w = kopf.indexOf("currency (${kopf[i]})").let { r.feld(it) }
                if (w.isBlank() || waehrung.isBlank() || w.equals(waehrung, true)) zahl(r.feld(i), false)?.let { kotlin.math.abs(it) } ?: 0.0 else 0.0
            }
            val isin = r.feld(iIsin).ifBlank { r.feld(iTicker) }
            val name = r.feld(iName)
            val stueck = zahl(r.feld(iStueck), false)?.let { kotlin.math.abs(it) } ?: 0.0
            val id = kennung(r)
            when {
                akt.endsWith(" buy") || akt == "buy" -> if (stueck == 0.0) null else {
                    val wert = kotlin.math.abs(total) - geb
                    zeile(zeit, "BUY", name, isin, stueck, wert / stueck, -wert, -geb, id = id)
                }
                akt.endsWith(" sell") || akt == "sell" -> if (stueck == 0.0) null else {
                    val wert = kotlin.math.abs(total) + geb
                    zeile(zeit, "SELL", name, isin, -stueck, wert / stueck, wert, -geb, id = id)
                }
                akt.startsWith("dividend") -> zeile(zeit, "DIVIDEND", name, isin, betrag = total, id = id)
                akt == "deposit" -> zeile(zeit, "DEPOSIT", betrag = kotlin.math.abs(total), gebuehr = -geb, id = id)
                akt == "withdrawal" -> zeile(zeit, "WITHDRAWAL", betrag = -kotlin.math.abs(total), id = id)
                "interest" in akt -> zeile(zeit, "INTEREST", "Zinsen", betrag = total, id = id)
                akt == "spending cashback" -> zeile(zeit, "CASHBACK", "Cashback", betrag = total, id = id)
                akt.startsWith("card ") || akt == "result adjustment" -> if (total == 0.0) null else zeile(zeit, if (total > 0) "DEPOSIT" else "WITHDRAWAL", akt, betrag = total, id = id)
                // Aktiensplit: die Zeilen nennen die ganze Position vorher und nachher, der Einstand geht mit.
                akt == "stock split close" -> zeile(zeit, "EXCHANGE", name, isin, -stueck, id = id)
                akt == "stock split open" -> zeile(zeit, "EXCHANGE", name, isin, stueck, id = id)
                akt == "transfer in" || akt == "stock distribution" || akt == "custom stock distribution" || akt == "spin off" ->
                    if (stueck == 0.0) null else zeile(zeit, "TRANSFER_IN", name, isin, stueck, 0.0, id = id)
                akt == "transfer out" -> if (stueck == 0.0) null else zeile(zeit, "TRANSFER_OUT", name, isin, -stueck, 0.0, id = id)
                else -> null
            }
        }
    }

    // ------------------------------------------------------------ DEGIRO

    // Spalte nach dem Kopf i: Bei DEGIRO steht dort ohne eigenen Namen die Waehrung oder der Betrag.
    private fun nach(i: Int) = if (i >= 0) i + 1 else -1

    // Posteingang > Transaktionen (Transactions.csv): Datum, Uhrzeit, Produkt, ISIN, Anzahl (Verkauf negativ),
    // Kurs, Wert EUR, Gebuehren, Gesamt.
    private fun degiro(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ',') ?: return emptyList()
        val kennung = Kennungen()
        val iDat = s.idx("date", "datum"); val iZeit = s.idx("time", "uhrzeit", "tijd"); val iName = s.idx("product", "produkt")
        val iIsin = s.idx("isin"); val iStueck = s.idx("quantity", "anzahl", "aantal")
        val iWert = s.idx("value eur", "wert eur", "waarde eur", "value", "wert", "waarde")
        val iGeb = s.enthaelt("transaction and/or", "transaktionsgebühren", "transaktionsgebuhren", "transactiekosten", "transaction costs")
        val iFx = s.enthaelt("autofx")
        val iGesamt = s.idx("total eur", "gesamt eur", "totaal eur", "total", "gesamt", "totaal")
        val iOrder = s.idx("order id", "order-id")
        val englisch = s.idx("date") >= 0
        return daten.mapIndexedNotNull { n, r ->
            val zeit = zeit(r.feld(iDat), r.feld(iZeit))
            // Lange Produktnamen brechen in eine Folgezeile ohne Datum um
            if (zeit == 0L) return@mapIndexedNotNull null
            fun z(i: Int) = zahl(r.feld(i), !englisch)
            val stueck = z(iStueck) ?: return@mapIndexedNotNull null
            if (stueck == 0.0) return@mapIndexedNotNull null
            val geb = -(kotlin.math.abs(z(iGeb) ?: 0.0) + kotlin.math.abs(z(iFx) ?: 0.0))
            val wert = z(iWert) ?: z(iGesamt)?.let { it - geb } ?: return@mapIndexedNotNull null
            val order = r.feld(iOrder).ifBlank { r.lastOrNull { Regex("^[0-9a-f]{8}-[0-9a-f-]{27}$").matches(it.trim()) }.orEmpty().trim() }
            zeile(zeit, if (stueck > 0) "BUY" else "SELL", r.feld(iName), r.feld(iIsin), stueck, kotlin.math.abs(wert / stueck),
                if (stueck > 0) -kotlin.math.abs(wert) else kotlin.math.abs(wert), geb, id = kennung(r))
        }
    }

    // Posteingang > Kontoauszug (Account.csv): Dividenden, Steuern, Zinsen, Ein- und Auszahlungen, Gebuehren in Euro.
    // Kaeufe und Verkaeufe stehen im Transaktionsexport. Eine Dividende in fremder Waehrung zaehlt mit dem
    // Euro-Betrag des folgenden Waehrungswechsels.
    private fun degiroKonto(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ',') ?: return emptyList()
        val kennung = Kennungen()
        val iDat = s.idx("date", "datum"); val iZeit = s.idx("time", "uhrzeit", "tijd"); val iName = s.idx("product", "produkt")
        val iIsin = s.idx("isin"); val iText = s.idx("description", "beschreibung", "omschrijving")
        val iW = s.idx("change", "änderung", "anderung", "mutatie"); val iBetrag = nach(iW)
        val englisch = s.idx("date") >= 0
        // Dividenden in fremder Waehrung, die auf ihren Waehrungswechsel warten: Name, ISIN, Tag
        val offen = ArrayList<Triple<String, String, Long>>()
        val aus = ArrayList<Zeile>()
        daten.forEachIndexed { n, r ->
            val zeit = zeit(r.feld(iDat), r.feld(iZeit))
            if (zeit == 0L) return@forEachIndexed
            val t = r.feld(iText).lowercase()
            val waehrung = r.feld(iW).uppercase()
            val betrag = zahl(r.feld(iBetrag), !englisch) ?: return@forEachIndexed
            val name = r.feld(iName); val isin = r.feld(iIsin)
            val id = kennung(r)
            fun hat(vararg w: String) = w.any { it in t }
            val steuer = hat("dividend tax", "dividendensteuer", "dividendbelasting", "quellensteuer")
            val dividende = !steuer && hat("dividend", "ausschüttung", "ausschuttung", "distribution", "uitkering")
            val wechsel = hat("fx credit", "fx debit", "fx-gutschrift", "fx-belastung", "währungswechsel", "wahrungswechsel", "valuta creditering", "valuta debitering")
            if (waehrung != "EUR") {
                if (dividende || steuer) {
                    val tag = zeit / 86_400_000L
                    if (offen.none { it.second == isin && it.third == tag }) offen += Triple(name, isin, tag)
                }
                return@forEachIndexed
            }
            when {
                wechsel -> {
                    // Euro-Seite des Wechsels einer Dividende in fremder Waehrung (bis 5 Tage spaeter)
                    val tag = zeit / 86_400_000L
                    val d = offen.firstOrNull { tag - it.third in 0..5 }
                    if (d != null && betrag != 0.0) {
                        offen.remove(d)
                        aus += zeile(zeit, "DIVIDEND", d.first, d.second, betrag = betrag, id = id)
                    }
                }
                hat("buy ", "sell ", "kauf ", "verkauf ", "koop ", "verkoop ", "transaction and/or", "transaktionsgebühr", "transaktionsgebuhr", "transactiekosten",
                    "cash sweep", "geldmarktfonds", "money market", "reservation", "reservierung", "interne", "internal", "aktiensplit", "split") -> Unit
                steuer -> aus += zeile(zeit, "TAX", name, isin, betrag = betrag, id = id)
                dividende -> aus += zeile(zeit, "DIVIDEND", name, isin, betrag = betrag, id = id)
                hat("interest", "zinsen", "rente") -> aus += zeile(zeit, "INTEREST", r.feld(iText), betrag = betrag, id = id)
                hat("withdrawal", "auszahlung", "terugstorting", "opname") -> aus += zeile(zeit, "WITHDRAWAL", betrag = -kotlin.math.abs(betrag), id = id)
                hat("deposit", "einzahlung", "storting") -> aus += zeile(zeit, "DEPOSIT", betrag = kotlin.math.abs(betrag), id = id)
                hat("fee", "gebühr", "gebuhr", "kosten", "einrichtung", "connection") && betrag != 0.0 -> aus += zeile(zeit, "FEE", r.feld(iText), betrag = betrag, id = id)
            }
        }
        return aus
    }

    // ------------------------------------------------------------ Interactive Brokers

    // Flex Query als CSV mit den Abschnitten Trades und Cash Transactions, jeder mit eigener Kopfzeile,
    // wahlweise mit HEADER und DATA davor. Betraege mit FXRateToBase in Euro umgerechnet.
    private fun ibkr(text: String): List<Zeile> {
        val alle = TradeRepublicImport.csv(text, ',')
        val kennung = Kennungen()
        val aus = ArrayList<Zeile>()
        var kopf: Spalten? = null
        var namen: List<String> = emptyList()
        alle.forEachIndexed { n, roh ->
            var r = roh
            val art = r.firstOrNull()?.trim()?.uppercase()
            if (art == "HEADER" || art == "DATA") r = r.drop(2)
            val klein = r.map { it.trim().lowercase() }
            if (art == "HEADER" || art != "DATA" && ("currencyprimary" in klein || "clientaccountid" in klein || "buy/sell" in klein || "type" in klein && "amount" in klein)) {
                kopf = Spalten(r); namen = klein; return@forEachIndexed
            }
            val s = kopf ?: return@forEachIndexed
            fun f(vararg k: String) = r.feld(s.idx(*k))
            fun z(vararg k: String) = zahl(f(*k), false)
            val fx = z("fxratetobase")?.takeIf { it > 0 } ?: 1.0
            val zeit = zeit(f("datetime", "tradedate", "settledate", "date", "reportdate").let(::ibDatum))
            if (zeit == 0L) return@forEachIndexed
            val isin = f("isin").ifBlank { f("symbol") }
            val name = f("description").ifBlank { f("symbol") }
            val id = kennung(r)
            if ("buy/sell" in namen) {
                val seite = f("buy/sell").uppercase()
                if ("(CA" in seite) return@forEachIndexed
                val ebene = f("levelofdetail").uppercase()
                if (ebene.isNotBlank() && ebene != "EXECUTION") return@forEachIndexed
                val klasse = f("assetclass").uppercase()
                if (klasse.isNotBlank() && klasse != "STK" && klasse != "FUND") return@forEachIndexed
                val stueck = z("quantity") ?: return@forEachIndexed
                if (stueck == 0.0) return@forEachIndexed
                val wert = (z("proceeds") ?: (-(z("tradeprice") ?: 0.0) * stueck)) * fx
                val geb = -kotlin.math.abs((z("ibcommission") ?: 0.0) * fx)
                val steuer = -kotlin.math.abs((z("taxes") ?: 0.0) * fx)
                aus += zeile(zeit, if (stueck > 0) "BUY" else "SELL", name, isin, stueck, kotlin.math.abs(wert / stueck), wert, geb, steuer, id)
            } else if ("type" in namen && "amount" in namen) {
                val typ = f("type").lowercase()
                val betrag = (z("amount") ?: return@forEachIndexed) * fx
                if (betrag == 0.0) return@forEachIndexed
                aus += when {
                    "withholding" in typ -> zeile(zeit, "TAX", name, isin, betrag = betrag, id = id)
                    "dividend" in typ -> zeile(zeit, "DIVIDEND", name, isin, betrag = betrag, id = id)
                    "interest" in typ -> zeile(zeit, "INTEREST", name, betrag = betrag, id = id)
                    "deposit" in typ || "withdrawal" in typ -> zeile(zeit, if (betrag > 0) "DEPOSIT" else "WITHDRAWAL", betrag = betrag, id = id)
                    "fee" in typ || "commission" in typ -> zeile(zeit, "FEE", name, betrag = betrag, id = id)
                    else -> return@forEachIndexed
                }
            }
        }
        return aus
    }

    // IBKR schreibt Daten auch als 20240201 und 20240201;041431.
    private fun ibDatum(s: String): String {
        val m = Regex("""^(\d{4})(\d{2})(\d{2})(?:[;, T]+(\d{2}):?(\d{2}):?(\d{2}))?""").find(s.trim()) ?: return s
        val g = m.groupValues
        return "${g[1]}-${g[2]}-${g[3]}" + if (g[4].isNotEmpty()) " ${g[4]}:${g[5]}:${g[6]}" else ""
    }

    // ------------------------------------------------------------ Revolut

    // Invest > Dokumente > Kontoauszug (CSV): Date, Ticker, Type, Quantity, Price per share, Total Amount, Currency,
    // FX Rate. Ohne ISIN, Betraege mit Waehrungszeichen, umgerechnet mit dem Kurs der Zeile in Einheiten je Euro.
    private fun revolut(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ',') ?: return emptyList()
        val kennung = Kennungen()
        val iDat = s.idx("date"); val iTicker = s.idx("ticker"); val iTyp = s.idx("type"); val iStueck = s.idx("quantity")
        val iPreis = s.idx("price per share"); val iTotal = s.idx("total amount"); val iW = s.idx("currency"); val iFx = s.idx("fx rate")
        return daten.mapIndexedNotNull { n, r ->
            val datum = r.feld(iDat)
            // Alte Dateien: 31/01/2023 12:00:00
            val zeit = zeit(datum)
            if (zeit == 0L) return@mapIndexedNotNull null
            val w = r.feld(iW).uppercase()
            val fx = zahl(r.feld(iFx), false)?.takeIf { it > 0 && w != "EUR" } ?: 1.0
            fun eur(i: Int) = zahl(r.feld(i), false)?.let { it / fx }
            val typ = r.feld(iTyp).uppercase()
            val ticker = r.feld(iTicker)
            val stueck = zahl(r.feld(iStueck), false)?.let { kotlin.math.abs(it) } ?: 0.0
            val total = eur(iTotal) ?: 0.0
            val id = kennung(r)
            when {
                typ.startsWith("BUY") -> if (stueck == 0.0) null else zeile(zeit, "BUY", ticker, ticker, stueck, kotlin.math.abs(total) / stueck, -kotlin.math.abs(total), id = id)
                typ.startsWith("SELL") -> if (stueck == 0.0) null else zeile(zeit, "SELL", ticker, ticker, -stueck, kotlin.math.abs(total) / stueck, kotlin.math.abs(total), id = id)
                typ.startsWith("DIVIDEND TAX") -> zeile(zeit, "TAX", ticker, ticker, betrag = total, id = id)
                typ.startsWith("DIVIDEND") -> zeile(zeit, "DIVIDEND", ticker, ticker, betrag = total, id = id)
                typ == "CASH TOP-UP" -> zeile(zeit, "DEPOSIT", betrag = kotlin.math.abs(total), id = id)
                typ == "CASH WITHDRAWAL" -> zeile(zeit, "WITHDRAWAL", betrag = -kotlin.math.abs(total), id = id)
                typ.startsWith("CUSTODY") -> zeile(zeit, "FEE", "Verwahrentgelt", betrag = if ("REVERSAL" in typ) kotlin.math.abs(total) else -kotlin.math.abs(total), id = id)
                typ == "STOCK SPLIT" -> if (stueck == 0.0) null else zeile(zeit, "SPLIT", ticker, ticker, zahl(r.feld(iStueck), false), 0.0, id = id)
                else -> null
            }
        }
    }

    // ------------------------------------------------------------ ING

    // Ordermanager > Ausgefuehrte Orders (CSV): Orderart (Kauf, Verkauf), Ausfuehrungsdatum, ISIN, Wertpapiername,
    // Stueck, Ausfuehrungskurs, Status. Ohne Gebuehren.
    private fun ing(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ';') { "orderart" in it && "isin" in it } ?: return emptyList()
        val kennung = Kennungen()
        val iArt = s.idx("orderart"); val iDat = s.idx("ausführungsdatum", "ausfuhrungsdatum"); val iNr = s.idx("ordernummer")
        val iIsin = s.idx("isin"); val iName = s.idx("wertpapiername"); val iStueck = s.idx("stück/nominale", "stuck/nominale", "stück", "stueck")
        val iKurs = s.idx("ausführungskurs", "ausfuhrungskurs"); val iStatus = s.idx("status")
        return daten.mapIndexedNotNull { n, r ->
            if (iStatus >= 0 && !r.feld(iStatus).lowercase().startsWith("ausgef")) return@mapIndexedNotNull null
            val zeit = zeit(r.feld(iDat)).takeIf { it > 0 } ?: return@mapIndexedNotNull null
            val stueck = zahl(r.feld(iStueck), true)?.let { kotlin.math.abs(it) } ?: return@mapIndexedNotNull null
            val kurs = zahl(r.feld(iKurs), true) ?: return@mapIndexedNotNull null
            val id = kennung(r)
            when (r.feld(iArt).lowercase()) {
                "kauf" -> zeile(zeit, "BUY", r.feld(iName), r.feld(iIsin), stueck, kurs, -stueck * kurs, id = id)
                "verkauf" -> zeile(zeit, "SELL", r.feld(iName), r.feld(iIsin), -stueck, kurs, stueck * kurs, id = id)
                else -> null
            }
        }
    }

    // ------------------------------------------------------------ flatex

    // Depotumsaetze (CSV): Buchtag, ISIN, Bezeichnung, Nominal (Verkauf negativ), Buchungsinformationen, Kurs.
    // Ohne Gebuehren.
    private fun flatex(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ';') { "buchtag" in it && "isin" in it } ?: return emptyList()
        val kennung = Kennungen()
        val iDat = s.idx("buchtag"); val iIsin = s.idx("isin"); val iName = s.idx("bezeichnung"); val iNom = s.idx("nominal", "nominale")
        val iText = s.idx("buchungsinformationen"); val iNr = s.idx("ta-nr.", "ta-nr"); val iKurs = s.idx("kurs")
        return daten.mapIndexedNotNull { n, r ->
            val zeit = zeit(r.feld(iDat)).takeIf { it > 0 } ?: return@mapIndexedNotNull null
            val stueck = zahl(r.feld(iNom), true) ?: return@mapIndexedNotNull null
            if (stueck == 0.0) return@mapIndexedNotNull null
            val kurs = zahl(r.feld(iKurs), true) ?: 0.0
            val t = r.feld(iText).lowercase()
            val id = kennung(r)
            val name = r.feld(iName); val isin = r.feld(iIsin)
            when {
                "kauf" in t && "verkauf" !in t -> zeile(zeit, "BUY", name, isin, kotlin.math.abs(stueck), kurs, -kotlin.math.abs(stueck) * kurs, id = id)
                "verkauf" in t -> zeile(zeit, "SELL", name, isin, -kotlin.math.abs(stueck), kurs, kotlin.math.abs(stueck) * kurs, id = id)
                // Einlieferungen, Lagerstellenwechsel, Kapitalmassnahmen: Stueck ohne Geld
                else -> zeile(zeit, if (stueck > 0) "TRANSFER_IN" else "TRANSFER_OUT", name, isin, stueck, 0.0, id = id)
            }
        }
    }

    // ------------------------------------------------------------ Bitpanda

    // Verlauf > Export (CSV, einige Zeilen Vorspann): Transaction ID, Timestamp, Transaction Type, Amount Fiat, Fiat,
    // Amount Asset, Asset, Asset class, Fee, Fee asset. Gelesen werden Krypto, Ein- und Auszahlungen in Euro.
    private fun bitpanda(text: String): List<Zeile> {
        val (s, daten) = tabelle(text, ',') { "transaction id" in it && "amount fiat" in it } ?: return emptyList()
        val kennung = Kennungen()
        val iId = s.idx("transaction id"); val iZeit = s.idx("timestamp"); val iTyp = s.idx("transaction type")
        val iFiat = s.idx("amount fiat"); val iFiatW = s.idx("fiat"); val iMenge = s.idx("amount asset"); val iAsset = s.idx("asset")
        val iKlasse = s.idx("asset class"); val iGeb = s.idx("fee"); val iGebW = s.idx("fee asset")
        return daten.mapIndexedNotNull { n, r ->
            val zeit = zeit(r.feld(iZeit)).takeIf { it > 0 } ?: return@mapIndexedNotNull null
            if (!r.feld(iFiatW).equals("EUR", true)) return@mapIndexedNotNull null
            val typ = r.feld(iTyp).lowercase()
            val fiat = zahl(r.feld(iFiat), false)?.let { kotlin.math.abs(it) } ?: 0.0
            val menge = zahl(r.feld(iMenge), false)?.let { kotlin.math.abs(it) } ?: 0.0
            val asset = r.feld(iAsset).uppercase()
            val krypto = r.feld(iKlasse).lowercase().startsWith("crypto")
            val geb = if (r.feld(iGebW).equals("EUR", true)) -kotlin.math.abs(zahl(r.feld(iGeb), false) ?: 0.0) else 0.0
            val id = r.feld(iId).ifBlank { kennung(r) }
            when {
                typ == "deposit" && (asset == "EUR" || !krypto) && r.feld(iKlasse).equals("fiat", true) -> zeile(zeit, "DEPOSIT", betrag = fiat, gebuehr = geb, id = id)
                typ == "withdrawal" && r.feld(iKlasse).equals("fiat", true) -> zeile(zeit, "WITHDRAWAL", betrag = -fiat, gebuehr = geb, id = id)
                !krypto || menge == 0.0 -> null
                typ == "buy" -> zeile(zeit, "BUY", asset, asset, menge, fiat / menge, -fiat, geb, id = id, klasse = "CRYPTO")
                typ == "sell" -> zeile(zeit, "SELL", asset, asset, -menge, fiat / menge, fiat, geb, id = id, klasse = "CRYPTO")
                typ == "rewards" || typ == "reward" -> zeile(zeit, "REWARD", asset, asset, menge, 0.0, id = id, klasse = "CRYPTO")
                else -> null
            }
        }
    }
}
