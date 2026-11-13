package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Lot
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import kotlin.math.abs

// Transaktionsexport von Trade Republic als CSV (App > Profil > Abrechnungen > Transaktionsexport).
// Spalten unter anderem: category, type, symbol (ISIN, bei Krypto das Kuerzel), shares mit Vorzeichen, price,
// amount (Geldwirkung ohne Gebuehr und Steuer), fee, tax (negativ = gezahlt), currency.
// Gerechnet wird mit Durchschnittskosten wie im Uebungsdepot. Die Datei verlaesst das Geraet nicht.
object TradeRepublicImport {

    // Vorsilbe fuer Werte ohne Kurs bei Yahoo, etwa echte Derivate. Sie werden zum Handelspreis bewertet.
    const val OHNE_KURS = "~"

    data class Zeile(
        val zeit: Long,
        val kategorie: String,
        val typ: String,
        val klasse: String,
        val name: String,
        val symbol: String,
        val stueck: Double?,
        val preis: Double?,
        val betrag: Double,
        val gebuehr: Double,
        val steuer: Double,
        val text: String,
        val id: String,
    )

    data class Ergebnis(
        val konto: Account,
        // Name des Brokers.
        val quelle: String = "Trade Republic",
        // Nicht ins Depot uebernommen: Name und Stueck.
        val ohneKurs: List<Pair<String, Double>>,
        // Namen der Verkaeufe ohne vorherigen Kauf in der Datei, dann ist der Export unvollstaendig.
        val ohneKauf: Set<String>,
        val zeilen: Int,
        val von: Long,
        val bis: Long,
        // Zeilen, die nicht gelesen werden konnten (unbekannte Buchungsart, kein Datum), mit einigen Beispielen.
        val ungelesen: Int = 0,
        val ungelesenBeispiele: List<String> = emptyList(),
        // Zeilen in Fremdwaehrung ohne Wechselkurs, deren Betrag unveraendert uebernommen ist.
        val ohneWechselkurs: Int = 0,
        // Zeilen der Datei, die als Buchung schon im Depot stehen (derselbe Export ein zweites Mal oder ein laengerer Zeitraum).
        val bekannt: Int = 0,
        // Das Depot nach dem Zusammenfuehren, wenn schon Zeilen der Datei im Depot stehen: nur die neuen Zeilen kommen dazu,
        // Verkaeufe rechnen gegen den Bestand des Depots. Sonst null, dann fuehrt Zusammenfuehren.konten die Konten zusammen.
        val ergaenzung: Account? = null,
        // Buchungen ueber der Hoechstzahl, die die App haelt; die aeltesten entfielen bei der naechsten Buchung.
        val ueberGrenze: Int = 0,
        // Der Aufbau dieser Datei ist nicht an einem echten Export belegt.
        val ungeprueft: Boolean = false,
    )

    fun erkennen(text: String): Boolean {
        val kopf = text.trimStart('\uFEFF').lineSequence().firstOrNull().orEmpty().lowercase()
        return "datetime" in kopf && "category" in kopf && "asset_class" in kopf && "shares" in kopf && "amount" in kopf
    }

    // Felder koennen doppelte Anfuehrungszeichen, Kommas und Zeilenumbrueche enthalten.
    fun csv(text: String, trenner: Char = ','): List<List<String>> {
        val zeilen = ArrayList<List<String>>()
        var feld = StringBuilder()
        var zeile = ArrayList<String>()
        var inZitat = false
        var i = 0
        val t = text.trimStart('\uFEFF')
        while (i < t.length) {
            val c = t[i]
            when {
                inZitat && c == '"' && i + 1 < t.length && t[i + 1] == '"' -> { feld.append('"'); i++ }
                c == '"' -> inZitat = !inZitat
                !inZitat && c == trenner -> { zeile += feld.toString(); feld = StringBuilder() }
                !inZitat && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < t.length && t[i + 1] == '\n') i++
                    zeile += feld.toString(); feld = StringBuilder()
                    if (zeile.any { it.isNotEmpty() }) zeilen += zeile
                    zeile = ArrayList()
                }
                else -> feld.append(c)
            }
            i++
        }
        if (feld.isNotEmpty() || zeile.isNotEmpty()) { zeile += feld.toString(); if (zeile.any { it.isNotEmpty() }) zeilen += zeile }
        return zeilen
    }

    private fun zeit(s: String, datum: String): Long =
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(datum).toEpochDay() * 86_400_000L + 12 * 3_600_000L }.getOrDefault(0L)

    // Aelteste zuerst.
    fun lesen(text: String): List<Zeile> {
        val alle = csv(text)
        if (alle.isEmpty()) return emptyList()
        val kopf = alle[0].map { it.trim().lowercase() }
        fun idx(n: String) = kopf.indexOf(n)
        val iZeit = idx("datetime"); val iDatum = idx("date"); val iKat = idx("category"); val iTyp = idx("type")
        val iKlasse = idx("asset_class"); val iName = idx("name"); val iSym = idx("symbol"); val iStueck = idx("shares")
        val iPreis = idx("price"); val iBetrag = idx("amount"); val iGeb = idx("fee"); val iSteuer = idx("tax")
        val iText = idx("description"); val iId = idx("transaction_id")
        fun List<String>.f(i: Int) = if (i in indices) this[i].trim() else ""
        fun zahl(s: String) = s.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        return alle.drop(1).mapIndexed { n, r ->
            Zeile(
                zeit = zeit(r.f(iZeit), r.f(iDatum)), kategorie = r.f(iKat).uppercase(), typ = r.f(iTyp).uppercase(),
                klasse = r.f(iKlasse).uppercase(), name = r.f(iName), symbol = r.f(iSym),
                stueck = zahl(r.f(iStueck)), preis = zahl(r.f(iPreis)), betrag = zahl(r.f(iBetrag)) ?: 0.0,
                gebuehr = zahl(r.f(iGeb)) ?: 0.0, steuer = zahl(r.f(iSteuer)) ?: 0.0, text = r.f(iText),
                id = r.f(iId).ifBlank { "z$n" },
            )
        }.sortedBy { it.zeit }
    }

    // „ETH“ bleibt, „XF000ETH0019“ wird „ETH“, „XF00MELANIA2“ wird „MELANIA“.
    fun kryptoKuerzel(symbol: String): String =
        Regex("^XF0*([A-Z]+)\\d*$").matchEntire(symbol.uppercase())?.groupValues?.get(1) ?: symbol.uppercase()

    // ISIN von Aktien und Fonds, Kuerzel von Krypto.
    fun zuzuordnen(zeilen: List<Zeile>): Map<String, String> =
        zeilen.filter { it.symbol.isNotBlank() && (it.klasse == "STOCK" || it.klasse == "FUND" || it.klasse == "CRYPTO") }
            .associate { (if (it.klasse == "CRYPTO") kryptoKuerzel(it.symbol) else it.symbol) to it.klasse }

    // Vorschau fuer das Depot konto: das Ergebnis der ganzen Datei und, wenn Zeilen der Datei als Buchung schon im Depot
    // stehen, das Depot mit nur den neuen Zeilen (ergaenzung).
    fun vorschau(zeilen: List<Zeile>, kuerzel: Map<String, String>, depot: Account, jetzt: Long, quelle: String = "Trade Republic", praefix: String = "tr"): Ergebnis {
        val voll = konto(zeilen, kuerzel, depot, jetzt, quelle, praefix)
        val ids = depot.transactions.mapTo(HashSet()) { it.id }
        fun bekannt(z: Zeile) = "$praefix-${z.id}" in ids || "$praefix-${z.id}-g" in ids
        val schon = zeilen.count { bekannt(it) }
        val ergaenzung = if (schon == 0) null else konto(zeilen.filterNot { bekannt(it) }, kuerzel, depot, jetzt, quelle, praefix, vorhanden = depot).konto
        // Groesste Buchungszahl, die entstehen kann: das ersetzte oder das zusammengefuehrte Depot
        val groesste = maxOf(voll.konto.transactions.size, ergaenzung?.transactions?.size ?: (depot.transactions.size + voll.konto.transactions.size))
        return voll.copy(bekannt = schon, ergaenzung = ergaenzung, ueberGrenze = (groesste - Trading.MAX_BUCHUNGEN).coerceAtLeast(0))
    }

    // Steuertoepfe aus den uebernommenen Vorgaengen: Gewinne und Verluste der Verkaeufe (nach FIFO, nach Gebuehren und
    // Teilfreistellung) und Ertraege laufen in der Reihenfolge ihrer Zeit durch die Verrechnung der App (Aktienverluste
    // nur gegen Aktiengewinne, alles andere in den allgemeinen Topf). Der Freistellungsauftrag ist aus der Datei nicht
    // bekannt und bleibt unberuehrt. Die einbehaltenen Steuern des laufenden Jahres stammen aus den Buchungen selbst.
    // ereignisse: ((Zeit, Betrag, Art), Krypto-Gewinn innerhalb eines Jahres oder null)
    internal fun steuertoepfe(ereignisse: List<Pair<Triple<Long, Double, AssetType?>, Double?>>, tx: List<Transaction>, jetzt: Long): TaxState {
        var st = TaxState(year = Tax.yearOf(ereignisse.firstOrNull()?.first?.first ?: jetzt))
        for ((e, privat) in ereignisse) {
            val (ts, betrag, art) = e
            if (privat != null) {
                st = Tax.forYear(st, ts)
                st = st.copy(privateGains = st.privateGains + privat)
                continue
            }
            val kind = if (art == AssetType.STOCK) Tax.Kind.STOCK else Tax.Kind.GENERAL
            st = Tax.apply(st, betrag, kind, 0.0, ts).state
        }
        st = Tax.forYear(st, jetzt)
        val jahr = Tax.yearOf(jetzt)
        // Einbehaltene Steuern: bei Verkaeufen, Dividenden und Zinsen im Feld tax, sonst als Steuerbuchung
        val gezahlt = tx.filter { Tax.yearOf(it.ts) == jahr }.sumOf { if (it.type == TxType.TAX) -it.amount else it.tax }
        return st.copy(taxPaid = maxOf(0.0, gezahlt))
    }

    // kuerzel: Yahoo-Kuerzel je ISIN bzw. Kryptokuerzel, ohne Eintrag gilt der Wert als ohne Kurs.
    // behalten: Watchlist, Alarme und Merkwerte des aktuellen Depots.
    // vorhanden: das Depot, zu dem die Zeilen kommen; dann ist das Ergebnis das fertig zusammengefuehrte Depot (Bestand,
    // Guthaben und Buchungen des Depots plus die Zeilen).
    fun konto(zeilen: List<Zeile>, kuerzel: Map<String, String>, behalten: Account, jetzt: Long, quelle: String = "Trade Republic", praefix: String = "tr", vorhanden: Account? = null): Ergebnis {
        val via = "IMPORT_" + praefix.uppercase()
        data class Bestand(var stueck: Double = 0.0, var kosten: Double = 0.0, var name: String = "", var symbol: String = "", var seit: Long = 0L, val lots: MutableList<Lot> = ArrayList())
        val bestand = LinkedHashMap<String, Bestand>()
        val tx = ArrayList<Transaction>()
        var cash = 0.0
        var eingezahlt = 0.0
        var ausgezahlt = 0.0
        val ohneKauf = LinkedHashSet<String>()
        // Ausuebung (Stueck raus) und Rueckzahlung (Geld rein) eines Derivats stehen in zwei Zeilen.
        val ausuebung = HashMap<String, Int>()

        fun appSymbol(z: Zeile): String {
            if (z.symbol.isBlank()) return ""
            val k = if (z.klasse == "CRYPTO") kryptoKuerzel(z.symbol) else z.symbol
            return kuerzel[k] ?: (OHNE_KURS + z.symbol)
        }
        fun schluessel(z: Zeile) = z.symbol.ifBlank { z.name }

        // Bestand des Depots als Ausgangspunkt, damit Verkaeufe gegen den echten Einstand rechnen.
        val vorBestand = HashSet<String>()
        if (vorhanden != null) {
            val schluesselJeSymbol = zeilen.filter { it.symbol.isNotBlank() }.associate { appSymbol(it) to schluessel(it) }
            for (p in vorhanden.positions) {
                if (p.derivative != null) continue
                val k = schluesselJeSymbol[p.key] ?: continue
                bestand[k] = Bestand(p.qty, p.invested, p.name, p.symbol, p.openedAt, ArrayList(Posten.von(p)))
                vorBestand += k
            }
        }

        // Ereignisse fuer die Steuertoepfe: Zeit, Betrag nach Gebuehren, Art des Topfes, Teilfreistellung; Krypto gesondert.
        class Ereignis(val ts: Long, val betrag: Double, val art: AssetType?, val quote: Double, val privat: Double? = null)
        val ereignisse = ArrayList<Ereignis>()
        fun artVon(z: Zeile): AssetType? {
            if (z.klasse == "DERIVATIVE") return null
            if (z.klasse == "CRYPTO") return AssetType.CRYPTO
            if (z.klasse == "FUND") return AssetType.ETF
            val sym = appSymbol(z)
            return if (sym.startsWith(OHNE_KURS) || sym.isBlank()) { if (z.klasse == "FUND") AssetType.ETF else AssetType.STOCK } else Symbols.typeOf(sym)
        }
        val tauschTeile = HashMap<String, List<Lot>>()

        fun kauf(z: Zeile, stueck: Double, preis: Double, betrag: Double, tag: String, eigene: List<Lot>? = null) {
            val b = bestand.getOrPut(schluessel(z)) { Bestand() }
            if (b.stueck <= 1e-12) { b.seit = z.zeit; b.lots.clear() }
            b.stueck += stueck; b.kosten += stueck * preis; b.name = z.name.ifBlank { b.name }; b.symbol = appSymbol(z)
            if (eigene != null) b.lots += eigene else b.lots += Lot(stueck, preis, z.zeit, -z.gebuehr)
            b.lots.sortBy { it.ts }
            tx += Transaction(
                id = "$praefix-" + z.id, ts = z.zeit, type = TxType.BUY, title = z.name, amount = betrag, qty = stueck, price = preis,
                symbol = b.symbol, key = schluessel(z), fee = -z.gebuehr, tag = tag, via = via,
            )
        }

        // zumEinstand: Uebertrag und Tausch, die Stuecke gehen zum Einstand der aeltesten Posten ohne Gewinn oder Verlust.
        fun verkauf(z: Zeile, stueck: Double, preis0: Double, betrag: Double, typ: TxType, tag: String, zumEinstand: Boolean = false) {
            val b = bestand.getOrPut(schluessel(z)) { Bestand(name = z.name, symbol = appSymbol(z), seit = z.zeit) }
            // Fehlt der Kauf in der Datei, gilt der fehlende Teil als zum Verkaufspreis eingebucht.
            if (stueck > b.stueck + 1e-9) {
                ohneKauf += z.name.ifBlank { z.symbol }
                val fehlt = stueck - b.stueck
                b.kosten += fehlt * preis0; b.stueck += fehlt
                b.lots += Lot(fehlt, preis0, 0L)
                b.lots.sortBy { it.ts }
            }
            // Verkauf nach FIFO: die zuerst gekauften Stuecke gehen zuerst (wichtig fuer die Steuer)
            val ab = Posten.abgang(b.lots, stueck)
            val preis = if (zumEinstand) (if (stueck > 0) ab.kosten / stueck else 0.0) else preis0
            b.stueck -= stueck; b.kosten -= ab.kosten
            b.lots.clear(); b.lots += ab.rest
            if (b.stueck < 1e-9) { b.stueck = 0.0; b.kosten = 0.0; b.lots.clear() }
            tauschTeile[z.id] = ab.teile
            val ergebnis = stueck * preis - ab.kosten
            tx += Transaction(
                id = "$praefix-" + z.id, ts = z.zeit, type = typ, title = z.name.ifBlank { b.name }, amount = betrag + z.steuer, qty = stueck, price = preis,
                symbol = b.symbol.ifBlank { appSymbol(z) }, key = schluessel(z), tax = -z.steuer, realized = ergebnis,
                fee = -z.gebuehr, tag = tag, via = via, closedAt = if (typ == TxType.SELL) 0L else z.zeit,
            )
            if (tag == "SELL") {
                val art = artVon(z)
                if (art == AssetType.CRYPTO) {
                    val kurz = ab.teile.filter { !Trading.ueberEinJahr(it.ts, z.zeit) }
                    ereignisse += Ereignis(z.zeit, 0.0, art, 0.0, kurz.sumOf { it.qty * (preis - it.price) - it.fee } + z.gebuehr * (if (stueck > 0) kurz.sumOf { it.qty } / stueck else 0.0))
                } else {
                    val quote = if (art == AssetType.ETF) Trading.teilfreistellung(b.symbol, z.name, art) else 0.0
                    ereignisse += Ereignis(z.zeit, ergebnis - ab.gebuehren + z.gebuehr, art, quote)
                }
            }
        }

        fun gebuehr(z: Zeile) {
            if (z.gebuehr == 0.0) return
            tx += Transaction(id = "$praefix-" + z.id + "-g", ts = z.zeit, type = TxType.FEE, title = z.name.ifBlank { z.typ }, amount = z.gebuehr, tag = "FEE", note = z.symbol, via = via)
        }

        // Tausch (EXCHANGE): Einstand der abgehenden auf die neuen Stuecke uebertragen. Die neuen Stuecke kommen nach
        // allen abgehenden desselben Tages an die Reihe, auch wenn sie in der Datei weiter oben stehen.
        val tausch = zeilen.filter { it.typ == "EXCHANGE" }.groupBy { it.zeit / 86_400_000L }
        val tauschEnde = tausch.mapValues { (_, g) -> g.filter { (it.stueck ?: 0.0) < 0 }.maxOfOrNull { it.zeit } ?: 0L }
        val geordnet = zeilen.sortedWith(compareBy({ z ->
            if (z.typ == "EXCHANGE" && (z.stueck ?: 0.0) > 0) maxOf(z.zeit, tauschEnde[z.zeit / 86_400_000L] ?: 0L) else z.zeit
        }, { z -> if (z.typ == "EXCHANGE" && (z.stueck ?: 0.0) > 0) 1 else 0 }))

        for (z in geordnet) {
            cash += z.betrag + z.gebuehr + z.steuer
            // Dividenden, Ausschuettungen und Buchungen der Kategorie CASH nennen die Stueckzahl, auf die sie sich beziehen,
            // bewegen aber keine Stuecke.
            val ertrag = "DIVIDEND" in z.typ || "DISTRIBUTION" in z.typ || z.kategorie == "DIVIDEND" || z.kategorie == "CASH"
            val s = if (ertrag) 0.0 else z.stueck ?: 0.0
            when {
                // Auch Sparplan und Saveback.
                s > 0 && z.preis != null && z.betrag < 0 -> { kauf(z, s, z.preis, z.betrag, "BUY"); gebuehr(z) }
                s < 0 && z.preis != null && z.betrag >= 0 -> { verkauf(z, -s, z.preis, z.betrag, TxType.SELL, "SELL"); gebuehr(z) }
                // Ausuebung bzw. Faelligkeit eines Derivats: Stueck raus, das Geld folgt mit TILG.
                z.typ == "WARRANT_EXERCISE" && s < 0 -> {
                    verkauf(z, -s, 0.0, 0.0, TxType.EXPIRY, "EXPIRY_WORTHLESS")
                    ausuebung[schluessel(z)] = tx.lastIndex
                }
                z.typ == "TILG" -> {
                    val i = ausuebung.remove(schluessel(z))
                    if (i != null) {
                        val alt = tx[i]
                        val preis = if (alt.qty > 0) z.betrag / alt.qty else 0.0
                        tx[i] = alt.copy(amount = alt.amount + z.betrag + z.steuer, price = preis, realized = alt.realized + z.betrag, tag = if (z.betrag > 0) "EXPIRY" else alt.tag)
                    } else tx += Transaction(id = "$praefix-" + z.id, ts = z.zeit, type = TxType.EXPIRY, title = z.name, amount = z.betrag + z.steuer,
                        symbol = appSymbol(z), key = schluessel(z), tag = "EXPIRY", via = via, tax = -z.steuer)
                    gebuehr(z)
                }
                z.typ == "EXCHANGE" && s != 0.0 -> {
                    val gruppe = tausch[z.zeit / 86_400_000L].orEmpty()
                    if (s < 0) {
                        verkauf(z, -s, 0.0, 0.0, TxType.SELL, "EXCHANGE_OUT", zumEinstand = true)
                    } else {
                        // Einstand der abgehenden Stuecke derselben Gruppe, verteilt auf die eingehenden.
                        val ab = gruppe.filter { (it.stueck ?: 0.0) < 0 }
                        val kosten = tx.filter { t -> t.tag == "EXCHANGE_OUT" && ab.any { "$praefix-" + it.id == t.id } }.sumOf { it.qty * it.price }
                        val zu = gruppe.filter { (it.stueck ?: 0.0) > 0 }.sumOf { it.stueck ?: 0.0 }
                        val preis = if (zu > 0) kosten * (s / zu) / s else 0.0
                        // Die Kaufposten der abgehenden Stuecke gehen mit ihrem Datum auf die neuen Stuecke ueber
                        val teile = ab.flatMap { tauschTeile[it.id].orEmpty() }
                        val abMenge = teile.sumOf { it.qty }
                        val lots = if (abMenge > 0 && zu > 0) teile.map { Lot(it.qty * s / abMenge, it.price * abMenge / zu, it.ts, it.fee * s / zu) } else null
                        kauf(z, s, preis, 0.0, "EXCHANGE_IN", lots)
                    }
                }
                // Uebertrag in ein anderes Depot: Stueck gehen zum Einstand, ohne Gewinn oder Verlust.
                z.typ == "TRANSFER_OUT" && s < 0 -> {
                    verkauf(z, -s, 0.0, 0.0, TxType.SELL, "TRANSFER_OUT", zumEinstand = true)
                }
                // Andere Kapitalmassnahmen wie Split oder Bonusaktien: Stueck ohne Geld.
                s > 0 -> kauf(z, s, if (z.betrag < 0) -z.betrag / s else 0.0, z.betrag, "CORPORATE")
                s < 0 -> verkauf(z, -s, if (z.betrag > 0) z.betrag / -s else 0.0, z.betrag, TxType.SELL, "CORPORATE")
                else -> {
                    val typ = when {
                        "DIVIDEND" in z.typ || "DISTRIBUTION" in z.typ || z.kategorie == "DIVIDEND" -> TxType.DIVIDEND
                        // Cashback und Praemien sind Ertrag, keine Einzahlung des Nutzers
                        "INTEREST" in z.typ || "CASHBACK" in z.typ || "REFERRAL" in z.typ -> TxType.INTEREST
                        "TAX" in z.typ -> TxType.TAX
                        // Nur Steuer ohne Betrag (Vorabpauschale): das Geld ist schon abgebucht, die Buchung muss dazu stehen
                        z.betrag == 0.0 && z.steuer != 0.0 && "FEE" !in z.typ -> TxType.TAX
                        "FEE" in z.typ && z.betrag == 0.0 -> null
                        "FEE" in z.typ -> TxType.FEE
                        z.betrag > 0 -> TxType.DEPOSIT
                        z.betrag < 0 -> TxType.WITHDRAWAL
                        else -> null
                    }
                    val wirkung = z.betrag + z.steuer
                    if (typ != null && (wirkung != 0.0 || typ == TxType.TAX && z.steuer != 0.0)) {
                        if (typ == TxType.DIVIDEND || typ == TxType.INTEREST) {
                            val art = if (typ == TxType.DIVIDEND) artVon(z) else null
                            val quote = if (art == AssetType.ETF) Trading.teilfreistellung(appSymbol(z), z.name, art) else 0.0
                            if (z.betrag > 0 && art != AssetType.CRYPTO) ereignisse += Ereignis(z.zeit, z.betrag, null, quote)
                        }
                        if (typ == TxType.DEPOSIT) eingezahlt += z.betrag
                        if (typ == TxType.WITHDRAWAL) ausgezahlt += -z.betrag
                        tx += Transaction(
                            id = "$praefix-" + z.id, ts = z.zeit, type = typ, title = z.name.ifBlank { z.typ }, amount = wirkung,
                            symbol = if (typ == TxType.DIVIDEND) appSymbol(z) else "", key = if (typ == TxType.DIVIDEND) schluessel(z) else "",
                            tax = -z.steuer, qty = if (typ == TxType.DIVIDEND) (z.stueck ?: 0.0) else 0.0,
                            tag = when (typ) { TxType.DEPOSIT -> "DEPOSIT"; TxType.WITHDRAWAL -> "WITHDRAWAL"; TxType.INTEREST -> "INTEREST"; TxType.DIVIDEND -> "DIVIDEND"; TxType.FEE -> "FEE"; else -> "" },
                            via = via,
                        )
                    }
                    gebuehr(z)
                }
            }
        }

        val positionen = ArrayList<Position>()
        val ohneKurs = ArrayList<Pair<String, Double>>()
        for ((k, b) in bestand) {
            if (b.stueck <= 1e-9) continue
            if (b.symbol.isBlank() || b.symbol.startsWith(OHNE_KURS)) { ohneKurs += b.name.ifBlank { k } to b.stueck; continue }
            // Nach einem Tausch koennen mehrere ISIN auf dasselbe Kuerzel fallen.
            val schonDa = positionen.indexOfFirst { it.key == b.symbol }
            if (schonDa >= 0) {
                val p = positionen[schonDa]
                val menge = p.qty + b.stueck
                positionen[schonDa] = p.copy(qty = menge, avgPrice = (p.invested + b.kosten) / menge, openedAt = minOf(p.openedAt, b.seit),
                    lots = (p.lots + b.lots).sortedBy { it.ts })
            } else positionen += Position(b.symbol, b.name, b.symbol, b.stueck, b.kosten / b.stueck, b.seit, importedAt = jetzt, lots = b.lots.toList())
        }
        // Ohne Kurs bleibt die ISIN der Schluessel.
        val buchungen = tx.map { t -> if (t.symbol.isNotBlank() && !t.symbol.startsWith(OHNE_KURS)) t.copy(key = t.symbol) else t }
        val konto = Account(
            cash = Math.round(cash * 100) / 100.0, deposited = eingezahlt, withdrawn = ausgezahlt, positions = positionen,
            transactions = buchungen.sortedBy { it.ts }, createdAt = zeilen.firstOrNull()?.zeit ?: jetzt,
            lastMaintainDay = jetzt / 86_400_000L, lastInterestDay = jetzt / 86_400_000L,
            tax = steuertoepfe(ereignisse.sortedBy { it.ts }.map { Triple(it.ts, it.betrag * (1.0 - it.quote), it.art) to it.privat }, tx, jetzt),
            ersteListe = behalten.ersteListe, ersteListeName = behalten.ersteListeName, weitereListen = behalten.weitereListen, watchMeta = behalten.watchMeta, alerts = behalten.alerts,
        )
        val ergebnis = Ergebnis(konto, quelle, ohneKurs, ohneKauf, zeilen.size, zeilen.firstOrNull()?.zeit ?: 0L, zeilen.lastOrNull()?.zeit ?: 0L)
        if (vorhanden == null) return ergebnis
        // Zum Depot dazu: Positionen, deren Bestand die Zeilen veraendern, ersetzen den alten Stand; sonst bleibt alles stehen.
        val pos = ArrayList(vorhanden.positions)
        for (np in positionen) {
            val i = pos.indexOfFirst { it.key == np.key }
            if (i < 0) { pos += np; continue }
            val alt = pos[i]
            if (abs(alt.qty - np.qty) < 1e-9 && abs(alt.avgPrice - np.avgPrice) < 1e-9) continue
            pos[i] = alt.copy(qty = np.qty, avgPrice = np.avgPrice, openedAt = minOf(alt.openedAt, np.openedAt), lots = np.lots)
        }
        // Ganz verkaufte Positionen des Depots verschwinden
        for (k in vorBestand) {
            val b = bestand[k] ?: continue
            if (b.stueck <= 1e-9) pos.removeAll { it.key == b.symbol && it.derivative == null }
        }
        val gesamt = vorhanden.copy(
            cash = Math.round((vorhanden.cash + cash) * 100) / 100.0, deposited = vorhanden.deposited + eingezahlt,
            withdrawn = vorhanden.withdrawn + ausgezahlt, positions = pos,
            transactions = (vorhanden.transactions + buchungen).sortedBy { it.ts },
            createdAt = listOf(vorhanden.createdAt, konto.createdAt).filter { it > 0 }.minOrNull() ?: 0L,
            tax = Zusammenfuehren.steuer(vorhanden.tax, konto.tax),
        )
        return ergebnis.copy(konto = gesamt)
    }
}
