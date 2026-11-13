package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.TradeRepublicImport.Zeile

// Flex-Bericht von Interactive Brokers als XML (Activity Flex Query, Flex Web Service Version 3): offene Positionen,
// Geschaefte, Geldbewegungen, Bargeld und Wechselkurse. Reine Funktionen ohne Netz. Kontonummern werden nicht gelesen.
// Der Aufbau folgt der Beschreibung des Dienstes; an einem echten Bericht ist er nicht belegt.
object FlexBericht {

    // ------------------------------------------------------------ XML

    // Ein Element mit seinen Attributen (Namen in Kleinbuchstaben). Der Bericht traegt alle Werte in Attributen.
    class Element(val name: String, private val attribute: Map<String, String>) {
        operator fun get(vararg namen: String): String = namen.firstNotNullOfOrNull { attribute[it.lowercase()]?.takeIf { v -> v.isNotBlank() } }?.trim().orEmpty()
        fun zahl(vararg namen: String): Double? = get(*namen).replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private val TAG = Regex("""<([A-Za-z][A-Za-z0-9_.-]*)((?:\s+[A-Za-z_:][A-Za-z0-9_.:-]*\s*=\s*(?:"[^"]*"|'[^']*'))*)\s*/?>""")
    private val ATTRIBUT = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val ZEICHEN = Regex("""&(#x[0-9A-Fa-f]{1,6}|#[0-9]{1,7}|[a-z]{2,4});""")

    // Zeichenverweise aufloesen: die fuenf benannten und Nummern.
    internal fun klartext(s: String): String = if ('&' !in s) s else ZEICHEN.replace(s) { m ->
        when (val v = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val code = if (v.startsWith("#x")) v.drop(2).toIntOrNull(16) else if (v.startsWith("#")) v.drop(1).toIntOrNull() else null
                if (code != null && code in 0x20..0xFFFF) code.toChar().toString() else m.value
            }
        }
    }

    // Alle oeffnenden Elemente in der Reihenfolge des Textes.
    internal fun elemente(xml: String): Sequence<Element> = TAG.findAll(xml).map { m ->
        val attribute = HashMap<String, String>()
        for (a in ATTRIBUT.findAll(m.groupValues[2])) attribute[a.groupValues[1].lowercase()] = klartext(a.groupValues[2].ifEmpty { a.groupValues[3] })
        Element(m.groupValues[1], attribute)
    }

    // Text eines einfachen Elements wie <Status>Success</Status>.
    internal fun inhalt(xml: String, name: String): String =
        Regex("<$name(?:\\s[^>]*)?>([^<]*)</$name>").find(xml)?.groupValues?.get(1)?.let(::klartext)?.trim().orEmpty()

    // ------------------------------------------------------------ Antwort des Dienstes

    // Antwort auf die Anforderung eines Berichts oder Fehlermeldung beim Abholen.
    data class Antwort(val ok: Boolean, val referenz: String, val adresse: String, val fehler: Int)

    // null, wenn der Text keine solche Antwort ist (dann ist er der Bericht selbst oder unbrauchbar).
    fun antwort(xml: String): Antwort? {
        if ("<FlexStatementResponse" !in xml) return null
        val status = inhalt(xml, "Status")
        val code = inhalt(xml, "ErrorCode").toIntOrNull() ?: 0
        val referenz = inhalt(xml, "ReferenceCode")
        return Antwort(status.equals("Success", true) && code == 0 && referenz.isNotBlank(), referenz, inhalt(xml, "Url"), code)
    }

    enum class Fehlerart { WARTEN, TOKEN_ABGELAUFEN, TOKEN_UNGUELTIG, ABFRAGE, ADRESSE_GESPERRT, ZU_VIELE, DIENST_AUS, SONST }

    // Fehlernummern des Dienstes (Version 3). WARTEN: derselbe Abruf gelingt kurz darauf.
    fun fehlerart(code: Int): Fehlerart = when (code) {
        1001, 1004, 1005, 1006, 1007, 1008, 1009, 1019, 1021 -> Fehlerart.WARTEN
        1012 -> Fehlerart.TOKEN_ABGELAUFEN
        1015 -> Fehlerart.TOKEN_UNGUELTIG
        1010, 1014 -> Fehlerart.ABFRAGE
        1013 -> Fehlerart.ADRESSE_GESPERRT
        1018 -> Fehlerart.ZU_VIELE
        1011 -> Fehlerart.DIENST_AUS
        else -> Fehlerart.SONST
    }

    // ------------------------------------------------------------ Inhalt

    // klasse wie im Bericht: STK, FUND, OPT, FOP, FUT, WAR, IOPT, BOND, CASH, CRYPTO, CFD.
    data class Papier(
        val klasse: String,
        val symbol: String,
        val isin: String,
        val name: String,
        val waehrung: String,
        val kennung: String = "",
        val multiplikator: Double = 1.0,
        // Nur Optionen: Basispreis, Verfall (ms), "P" oder "C", Kuerzel des Basiswerts.
        val basispreis: Double? = null,
        val verfall: Long = 0L,
        val putCall: String = "",
        val basiswert: String = "",
    ) {
        val option: Boolean get() = klasse == "OPT" || klasse == "FOP"
        // Unter diesem Schluessel sucht die App das Kuerzel: ISIN, sonst das Boersenkuerzel.
        val schluessel: String get() = if (BrokerExport.istIsin(isin)) isin.uppercase() else symbol
        // Aktien, Fonds und Krypto bildet das Depot ab.
        val abbildbar: Boolean get() = klasse == "STK" || klasse == "FUND" || klasse == "CRYPTO"
        val zeilenKlasse: String get() = when (klasse) { "FUND" -> "FUND"; "CRYPTO" -> "CRYPTO"; "WAR", "IOPT" -> "DERIVATIVE"; else -> "STOCK" }
        val anzeige: String get() = name.ifBlank { symbol }
        // Kennung des Papiers in den Zeilen des Imports.
        val zeilenSymbol: String get() = if (klasse == "CRYPTO") symbol.substringBefore('.') else schluessel
    }

    // stueck negativ: verkaufte Position. einstand und kurs in der Waehrung des Papiers, fx rechnet in die Basiswaehrung.
    data class Position(val papier: Papier, val stueck: Double, val einstandJeStueck: Double?, val einstand: Double?, val kurs: Double?, val fx: Double?, val eroeffnet: Long)

    // stueck positiv bei Kauf; erloes mit Vorzeichen (Kauf negativ), gebuehr und steuer wie im Bericht.
    data class Geschaeft(
        val papier: Papier, val zeit: Long, val stueck: Double, val preis: Double?, val erloes: Double?, val gebuehr: Double, val gebuehrWaehrung: String,
        val steuer: Double, val fx: Double?, val kennung: String,
    )

    data class Geld(val art: String, val zeit: Long, val betrag: Double, val waehrung: String, val fx: Double?, val papier: Papier?, val text: String, val kennung: String)

    // Bargeld je Waehrung zum Ende des Zeitraums; summe = true ist die Zeile in Basiswaehrung ueber alle Waehrungen.
    data class Bargeld(val waehrung: String, val anfang: Double?, val ende: Double, val summe: Boolean)

    // Einheiten von nach je Einheit von am Tag.
    data class Kurs(val von: String, val nach: String, val kurs: Double, val tag: Long)

    // Abschnitte, die in der Abfrage fehlen, sind null; ein leerer Abschnitt ist eine leere Liste.
    data class Bericht(
        val basis: String,
        val von: Long,
        val bis: Long,
        val erzeugt: Long,
        val positionen: List<Position>?,
        val geschaefte: List<Geschaeft>?,
        val geld: List<Geld>?,
        val bargeld: List<Bargeld>?,
        val kurse: List<Kurs>,
    ) {
        // Euro je Einheit der Basiswaehrung; null, wenn der Bericht den Kurs nicht nennt.
        val basisInEuro: Double? get() = when {
            basis == "EUR" -> 1.0
            // Ohne den Abschnitt mit der Basiswaehrung bleibt der Kurs offen
            basis.isBlank() -> null
            else -> kurse.filter { it.von == "EUR" && it.nach == basis && it.kurs > 0 }.maxByOrNull { it.tag }?.let { 1.0 / it.kurs }
                ?: kurse.filter { it.von == basis && it.nach == "EUR" && it.kurs > 0 }.maxByOrNull { it.tag }?.kurs
        }

        // Euro je Einheit der Waehrung einer Zeile mit ihrem Kurs zur Basiswaehrung.
        fun inEuro(waehrung: String, fx: Double?): Double? = when {
            waehrung == "EUR" -> 1.0
            waehrung == basis -> basisInEuro
            fx != null && fx > 0 -> basisInEuro?.let { it * fx }
            else -> null
        }

        // Bargeld ueber alle Waehrungen in Euro; null ohne den Abschnitt oder ohne Kurs.
        val bargeldEuro: Double? get() {
            val b = bargeld ?: return null
            b.firstOrNull { it.summe }?.let { s -> return basisInEuro?.let { s.ende * it } }
            if (b.isEmpty()) return null
            return b.sumOf { z -> z.ende * (inEuro(z.waehrung, null) ?: return null) }
        }
    }

    // Datum und Zeit des Berichts: 20240201, 20240201;041431, 2024-02-01 04:14:31. 0, wenn nichts passt.
    internal fun zeit(s: String): Long {
        val m = Regex("""^(\d{4})-?(\d{2})-?(\d{2})(?:[;, T]+(\d{2}):?(\d{2}):?(\d{2}))?""").find(s.trim()) ?: return 0L
        val g = m.groupValues
        return BrokerExport.zeit("${g[1]}-${g[2]}-${g[3]}", if (g[4].isNotEmpty()) "${g[4]}:${g[5]}:${g[6]}" else "")
    }

    private fun papier(e: Element): Papier {
        val klasse = e["assetCategory", "assetClass"].uppercase()
        return Papier(
            klasse = klasse, symbol = e["symbol"], isin = e["isin"].ifBlank { if (e["securityIDType"].equals("ISIN", true)) e["securityID"] else "" },
            name = e["description"], waehrung = e["currency", "currencyPrimary"].uppercase(), kennung = e["conid"],
            multiplikator = e.zahl("multiplier")?.takeIf { it > 0 } ?: 1.0,
            basispreis = e.zahl("strike"), verfall = zeit(e["expiry"]), putCall = e["putCall"].uppercase().take(1), basiswert = e["underlyingSymbol"],
        )
    }

    // null, wenn der Text kein Flex-Bericht ist.
    fun lesen(xml: String): Bericht? {
        if ("<FlexQueryResponse" !in xml && "<FlexStatement " !in xml) return null
        var basis = ""
        var von = 0L
        var bis = 0L
        var erzeugt = 0L
        var positionen: ArrayList<Position>? = null
        val zusammen = ArrayList<Position>()
        val posten = ArrayList<Position>()
        var geschaefte: ArrayList<Geschaeft>? = null
        val auftraege = ArrayList<Geschaeft>()
        var geld: ArrayList<Geld>? = null
        var bargeld: ArrayList<Bargeld>? = null
        val kurse = ArrayList<Kurs>()

        fun geschaeft(e: Element, kennung: String): Geschaeft? {
            val stueck = e.zahl("quantity")?.takeIf { it != 0.0 } ?: return null
            val t = zeit(e["dateTime", "tradeDate", "reportDate"])
            if (t == 0L) return null
            val seite = e["buySell", "buy/sell"].uppercase()
            val menge = if (seite.startsWith("SELL")) -kotlin.math.abs(stueck) else if (seite.startsWith("BUY")) kotlin.math.abs(stueck) else stueck
            return Geschaeft(papier(e), t, menge, e.zahl("tradePrice"), e.zahl("proceeds"), e.zahl("ibCommission") ?: 0.0, e["ibCommissionCurrency"].uppercase(),
                e.zahl("taxes") ?: 0.0, e.zahl("fxRateToBase"), kennung)
        }

        for (e in elemente(xml)) {
            when (e.name) {
                "FlexStatement" -> {
                    zeit(e["fromDate"]).takeIf { it > 0 }?.let { von = if (von == 0L) it else minOf(von, it) }
                    bis = maxOf(bis, zeit(e["toDate"]))
                    erzeugt = maxOf(erzeugt, zeit(e["whenGenerated"]))
                }
                "AccountInformation" -> if (basis.isBlank()) basis = e["currency"].uppercase()
                "OpenPositions" -> if (positionen == null) positionen = ArrayList()
                "OpenPosition" -> {
                    val stueck = e.zahl("position") ?: continue
                    if (stueck == 0.0) continue
                    val p = Position(papier(e), stueck, e.zahl("costBasisPrice", "openPrice"), e.zahl("costBasisMoney"), e.zahl("markPrice"), e.zahl("fxRateToBase"), zeit(e["openDateTime"]))
                    if (e["levelOfDetail"].uppercase() == "LOT") posten += p else zusammen += p
                }
                "Trades" -> if (geschaefte == null) geschaefte = ArrayList()
                "Trade" -> {
                    val ebene = e["levelOfDetail"].uppercase()
                    if (ebene.isNotBlank() && ebene != "EXECUTION") continue
                    val id = e["tradeID"].ifBlank { e["transactionID"] }.ifBlank { e["ibExecID"] }
                    geschaeft(e, if (id.isBlank()) "" else "t$id")?.let { (geschaefte ?: ArrayList<Geschaeft>().also { l -> geschaefte = l }) += it }
                }
                "Order" -> geschaeft(e, e["ibOrderID"].let { if (it.isBlank()) "" else "o$it" })?.let { auftraege += it }
                "CashTransactions" -> if (geld == null) geld = ArrayList()
                "CashTransaction" -> {
                    if (e["levelOfDetail"].uppercase() == "SUMMARY") continue
                    val betrag = e.zahl("amount") ?: continue
                    val t = zeit(e["dateTime", "settleDate", "reportDate"])
                    if (t == 0L || betrag == 0.0) continue
                    val p = papier(e).takeIf { it.symbol.isNotBlank() || it.isin.isNotBlank() }
                    val id = e["transactionID"]
                    (geld ?: ArrayList<Geld>().also { l -> geld = l }) += Geld(e["type"], t, betrag, e["currency", "currencyPrimary"].uppercase(), e.zahl("fxRateToBase"), p, e["description"], if (id.isBlank()) "" else "c$id")
                }
                "CashReport" -> if (bargeld == null) bargeld = ArrayList()
                "CashReportCurrency" -> {
                    val ende = e.zahl("endingCash") ?: continue
                    val w = e["currency", "currencyPrimary"].uppercase()
                    (bargeld ?: ArrayList<Bargeld>().also { l -> bargeld = l }) += Bargeld(w, e.zahl("startingCash"), ende, w == "BASE_SUMMARY")
                }
                "ConversionRate" -> {
                    val k = e.zahl("rate")?.takeIf { it > 0 } ?: continue
                    kurse += Kurs(e["fromCurrency"].uppercase(), e["toCurrency"].uppercase(), k, zeit(e["reportDate"]))
                }
            }
        }
        // Positionen: die zusammengefasste Zeile je Papier; gibt es nur einzelne Kaufposten, deren Summe.
        val pos = positionen?.also { ziel ->
            val gesehen = HashSet<String>()
            fun k(p: Papier) = p.kennung.ifBlank { p.klasse + "|" + p.schluessel + "|" + p.name }
            zusammen.groupBy { k(it.papier) }.forEach { (key, gruppe) -> gesehen += key; ziel += summe(gruppe) }
            posten.groupBy { k(it.papier) }.forEach { (key, gruppe) -> if (key !in gesehen) ziel += summe(gruppe) }
        }
        // Geschaefte: einzelne Ausfuehrungen; nennt die Abfrage nur Auftraege, diese.
        val trades = geschaefte?.let { g -> if (g.isEmpty() && auftraege.isNotEmpty()) auftraege else g }
        if (pos == null && trades == null && geld == null && bargeld == null && bis == 0L) return null
        return Bericht(basis, von, bis, erzeugt, pos, trades?.sortedBy { it.zeit }, geld?.sortedBy { it.zeit }, bargeld, kurse)
    }

    private fun summe(g: List<Position>): Position {
        if (g.size == 1) return g[0]
        val stueck = g.sumOf { it.stueck }
        val einstand = if (g.all { it.einstand != null }) g.sumOf { it.einstand ?: 0.0 }
            else if (g.all { it.einstandJeStueck != null }) g.sumOf { (it.einstandJeStueck ?: 0.0) * it.stueck * it.papier.multiplikator } else null
        val je = if (einstand != null && stueck != 0.0) einstand / (stueck * g[0].papier.multiplikator) else null
        return g[0].copy(stueck = stueck, einstand = einstand, einstandJeStueck = je, eroeffnet = g.map { it.eroeffnet }.filter { it > 0 }.minOrNull() ?: 0L)
    }

    // ------------------------------------------------------------ Buchungen

    // Was der Bericht nennt, das Depot aber nicht fuehrt (Optionen, Futures, Anleihen): Name und Stueck.
    data class Offen(val name: String, val stueck: Double)

    // Geschaefte und Geldbewegungen als Zeilen des Imports, Betraege in Euro. Vorzeichen wie in BrokerExport.
    // nicht: Geschaefte in Papieren, die das Depot nicht fuehrt. Devisengeschaefte entfallen ohne Meldung.
    fun zeilen(b: Bericht, bericht: BrokerExport.Bericht, nicht: MutableList<Offen> = ArrayList()): List<Zeile> {
        val aus = ArrayList<Zeile>()
        val gesehen = HashMap<String, Int>()
        // Ohne Kennung im Bericht: Kennung aus dem Inhalt, gleiche Zeilen durchgezaehlt.
        fun kennung(eigene: String, vararg inhalt: Any?): String {
            val k = eigene.ifBlank { "h" + inhalt.joinToString("|").let { s -> ((s.hashCode().toLong() shl 32) xor (s.reversed().hashCode().toLong() and 0xffffffffL)).toULong().toString(16) } }
            val n = gesehen.getOrPut(k) { 0 }
            gesehen[k] = n + 1
            return if (n == 0) k else "$k-$n"
        }
        fun euro(waehrung: String, fx: Double?): Double = b.inEuro(waehrung, fx) ?: run { bericht.ohneWechselkurs++; 1.0 }

        for (g in b.geschaefte.orEmpty()) {
            val p = g.papier
            if (p.klasse == "CASH") continue
            if (!p.abbildbar && p.zeilenKlasse != "DERIVATIVE") { nicht += Offen(p.anzeige, g.stueck); continue }
            if (p.schluessel.isBlank()) { bericht.nicht(listOf(p.anzeige, g.stueck.toString())); continue }
            val k = euro(p.waehrung, g.fx)
            val wert = (g.erloes ?: (-(g.preis ?: 0.0) * g.stueck * p.multiplikator)) * k
            if (wert == 0.0 && g.preis == null) { bericht.nicht(listOf(p.anzeige, g.stueck.toString())); continue }
            val kg = if (g.gebuehrWaehrung.isBlank() || g.gebuehrWaehrung == p.waehrung) k else b.inEuro(g.gebuehrWaehrung, null) ?: k
            val id = kennung(g.kennung, g.zeit, p.schluessel, g.stueck, g.preis, g.erloes)
            aus += Zeile(g.zeit, "TRADING", if (g.stueck > 0) "BUY" else "SELL", p.zeilenKlasse, p.anzeige, p.zeilenSymbol,
                g.stueck, kotlin.math.abs(wert / g.stueck), wert, -kotlin.math.abs(g.gebuehr * kg), -kotlin.math.abs(g.steuer * k), "", id)
        }
        for (c in b.geld.orEmpty()) {
            val art = c.art.lowercase()
            val betrag = c.betrag * euro(c.waehrung, c.fx)
            val p = c.papier
            val name = p?.anzeige ?: c.text
            val symbol = p?.zeilenSymbol.orEmpty()
            val klasse = if (p == null) "" else p.zeilenKlasse
            val typ = when {
                "withholding" in art -> "TAX"
                "dividend" in art -> "DIVIDEND"
                "interest" in art -> "INTEREST"
                "deposit" in art || "withdrawal" in art -> if (betrag > 0) "DEPOSIT" else "WITHDRAWAL"
                "fee" in art || "commission" in art -> "FEE"
                else -> { bericht.nicht(listOf(c.art, c.text)); continue }
            }
            val id = kennung(c.kennung, c.zeit, c.art, symbol, c.betrag)
            val mitPapier = typ == "TAX" || typ == "DIVIDEND"
            aus += Zeile(c.zeit, "CASH", typ, if (mitPapier) klasse else "", if (typ == "DEPOSIT" || typ == "WITHDRAWAL") "" else name, if (mitPapier) symbol else "",
                null, null, betrag, 0.0, 0.0, "", id)
        }
        return aus.sortedBy { it.zeit }
    }
}
