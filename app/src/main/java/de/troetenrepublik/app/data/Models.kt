package de.troetenrepublik.app.data

import kotlinx.serialization.Serializable

// Instrumente

enum class AssetType {
    STOCK, ETF, CRYPTO, INDEX,
    // Rohstoff (Future) und Waehrungspaar: nur als Basiswert fuer Derivate, nicht direkt handelbar.
    COMMODITY, CURRENCY;

    val directlyTradable: Boolean get() = this == STOCK || this == ETF || this == CRYPTO
}

@Serializable
data class Instrument(
    val symbol: String,
    val name: String,
    val type: AssetType = AssetType.STOCK,
    val currency: String = "EUR",
    val isin: String = "",
    val sector: String = "",
    // Unternehmensdomain, daraus wird notfalls das Seitensymbol geladen.
    val domain: String = "",
)

// Live-Kurs eines Basiswerts. Alle Preise sind in EUR; der Originalkurs steht in nativePrice.
@Serializable
data class Quote(
    val symbol: String,
    val name: String = "",
    val price: Double = 0.0,
    val prevClose: Double = 0.0,
    val open: Double = 0.0,
    val dayHigh: Double = 0.0,
    val dayLow: Double = 0.0,
    val weekHigh52: Double = 0.0,
    val weekLow52: Double = 0.0,
    val marketCap: Double = 0.0,
    val peRatio: Double = 0.0,
    val volume: Double = 0.0,
    // Durchschnittliches Tagesvolumen der letzten zehn Handelstage (Stueck), wenn Yahoo es liefert.
    val avgVolume10: Double = 0.0,
    val currency: String = "EUR",
    // Handelswaehrung an der Boerse; die Preise oben sind schon in EUR.
    val nativeCurrency: String = "EUR",
    val nativePrice: Double = 0.0,
    val exchange: String = "",
    val marketState: String = "",
    val updatedAt: Long = 0L,
    // Letzter Kurs der regulaeren Sitzung, falls price aus dem vor- oder nachboerslichen Handel stammt.
    val regularPrice: Double = 0.0,
    // Boersenzeit des Kurses price in ms; 0 = unbekannt.
    val marketTime: Long = 0L,
    // Handelsplatz der deutschen Zweitnotiz, wenn price von dort stammt (siehe Zweitnotiz); sonst leer.
    val secondaryExchange: String = "",
    // Kennzahlen aus der erweiterten Abfrage
    val eps: Double = 0.0,
    val dividendYield: Double = 0.0,
    val enterpriseValue: Double = 0.0,
    val targetPrice: Double = 0.0,
    val recommendation: String = "",
    val analystCount: Int = 0,
    val revenue: Double = 0.0,
    val ebitda: Double = 0.0,
    val netIncome: Double = 0.0,
    val revenueGrowth: Double = 0.0,
    val earningsDate: Long = 0L,
    val description: String = "",
) {
    val change: Double get() = if (prevClose > 0) price - prevClose else 0.0
    val changePct: Double get() = if (prevClose > 0) (price / prevClose - 1.0) * 100.0 else 0.0
    val isOpen: Boolean get() = marketState.equals("REGULAR", true)
    val isExtendedHours: Boolean get() = marketState.equals("PRE", true) || marketState.equals("POST", true)

    // EUR je Einheit der Handelswaehrung, um Basispreise in Handelswaehrung zu bewerten.
    val fx: Double get() = if (nativePrice > 0 && price > 0) price / nativePrice else 1.0
}

@Serializable
data class Candle(val t: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double = 0.0)

// Kursreihe mit dem Handelsfenster des Tages. dayStart und dayEnd spannen im Tageschart die Zeitachse auf
// (mit vor- und nachboerslichem Handel), damit die Linie dort endet, wo der Tag steht.
data class Series(val candles: List<Candle>, val dayStart: Long = 0L, val dayEnd: Long = 0L) {
    // true, wenn sich Handelsfenster und Kurse ueberschneiden. Nach Handelsschluss meldet Yahoo teils schon das
    // Fenster des naechsten Handelstags, waehrend die Kurse noch vom letzten stammen.
    val fensterPasst: Boolean
        get() = dayStart > 0L && dayEnd > dayStart && candles.isNotEmpty() && candles.last().t >= dayStart && candles.first().t <= dayEnd
}

// Zeitraeume des Charts. Bis zu einem Monat werden Intraday-Kurse geladen, damit alle Handelszeiten sichtbar sind.
enum class Range(val yahooRange: String, val yahooInterval: String, val intraday: Boolean) {
    D1("1d", "2m", true),
    W1("5d", "15m", true),
    M1("1mo", "30m", true),
    M3("3mo", "1d", false),
    M6("6mo", "1d", false),
    Y1("1y", "1d", false),
    MAX("10y", "1wk", false),
}

// Derivate

enum class DerivativeKind {
    KO_LONG, KO_SHORT,          // Knock-out-Zertifikate (Turbo)
    FACTOR_LONG, FACTOR_SHORT,  // Faktor-Zertifikate (taegliche Hebelwirkung)
    CALL, PUT,                  // Optionsscheine
}

val DerivativeKind.isLong: Boolean
    get() = this == DerivativeKind.KO_LONG || this == DerivativeKind.FACTOR_LONG || this == DerivativeKind.CALL

val DerivativeKind.isKnockout: Boolean
    get() = this == DerivativeKind.KO_LONG || this == DerivativeKind.KO_SHORT

val DerivativeKind.isFactor: Boolean
    get() = this == DerivativeKind.FACTOR_LONG || this == DerivativeKind.FACTOR_SHORT

val DerivativeKind.isOption: Boolean
    get() = this == DerivativeKind.CALL || this == DerivativeKind.PUT

// Hebelprodukt auf einen Basiswert. Pricing berechnet alle Preise aus dem Live-Kurs des Basiswerts, einen eigenen
// Markt gibt es nicht. Basispreis und Schwelle stehen wie bei echten Produkten in der Handelswaehrung (ccy).
@Serializable
data class Derivative(
    val id: String,
    val underlying: String,           // Symbol des Basiswerts
    val underlyingName: String,
    val kind: DerivativeKind,
    val strike: Double = 0.0,         // Basispreis (KO, Optionsschein) in ccy
    val barrier: Double = 0.0,        // Knock-out-Schwelle in ccy
    val ratio: Double = 1.0,          // Bezugsverhaeltnis: wie viele Zertifikate = 1 Basiswert
    val factor: Double = 0.0,         // Faktor-Zertifikat: taeglicher Hebel (z. B. 3, -5)
    val expiry: Long = 0L,            // 0 = open end
    val vol: Double = 0.30,           // implizite Volatilitaet fuer Optionsscheine
    val createdAt: Long = 0L,
    // Faktor-Zertifikate sind pfadabhaengig: letzter Stand wird fortgeschrieben.
    val factorPrice: Double = 0.0,
    val factorRef: Double = 0.0,
    // Zeitpunkt des Knock-outs; das Produkt bleibt bis zur Rueckzahlung sichtbar im Depot.
    val knockedOutAt: Long = 0L,
    // Waehrung von Basispreis und Schwelle.
    val ccy: String = "EUR",
    // Emittent: bei Optionsscheinen eines der Haeuser aus der Liste, sonst der Hausemittent.
    val issuer: String = "",
    // Nennhebel bei Begebung, frei waehlbar, auch mit Nachkommastellen.
    val nominalLeverage: Double = 0.0,
) {
    val isKnockedOut: Boolean get() = knockedOutAt > 0L
}

// Depot

// Ein Posten: entweder ein Basiswert (key = Symbol) oder ein Derivat (key = dessen id).
@Serializable
data class Position(
    val key: String,
    val name: String,
    val symbol: String,               // Basiswert-Symbol (bei Aktien identisch mit key)
    val qty: Double,
    val avgPrice: Double,             // Einstand je Stueck in EUR
    val openedAt: Long,
    val derivative: Derivative? = null,
    // Zeitpunkt der Uebernahme ins Depot beim Import; 0 = in der App gekauft. Dividenden zaehlen erst ab dann.
    val importedAt: Long = 0L,
) {
    val invested: Double get() = qty * avgPrice
    val isDerivative: Boolean get() = derivative != null
    val isKnockedOut: Boolean get() = derivative?.isKnockedOut == true
}

// Welche Kennzahl in der Depotliste rechts steht. Ein Tipp auf die Zahl schaltet in dieser
// Reihenfolge weiter: Tagestrend relativ, Tagestrend absolut, seit Kauf relativ, seit Kauf absolut.
enum class Metric {
    TODAY_PCT, TODAY_ABS, SINCE_BUY_PCT, SINCE_BUY_ABS;

    val next: Metric get() = entries[(ordinal + 1) % entries.size]
    val isSinceBuy: Boolean get() = this == SINCE_BUY_PCT || this == SINCE_BUY_ABS
    val isAbsolute: Boolean get() = this == TODAY_ABS || this == SINCE_BUY_ABS
}

// Kennzahl in der Watchlist: Tagestrend, Entwicklung seit dem Hinzufuegen oder seit dem ersten
// Hinzufuegen. Die letzte gibt es nur, wenn ein Wert von der Watchlist genommen und wieder aufgenommen wurde;
// sonst waere sie dieselbe Zahl wie „seit Hinzufuegen“.
enum class WatchMetric {
    TODAY_PCT, TODAY_ABS, SINCE_ADDED_PCT, SINCE_ADDED_ABS, SINCE_FIRST_PCT, SINCE_FIRST_ABS;

    val next: WatchMetric get() = next(true)

    // Naechste Kennzahl; ohne mitErstem werden die beiden „seit erstem Hinzufuegen“ uebersprungen.
    fun next(mitErstem: Boolean): WatchMetric {
        var n = entries[(ordinal + 1) % entries.size]
        if (!mitErstem && n.isSinceFirst) n = TODAY_PCT
        return n
    }

    val isSinceAdded: Boolean get() = this == SINCE_ADDED_PCT || this == SINCE_ADDED_ABS
    val isSinceFirst: Boolean get() = this == SINCE_FIRST_PCT || this == SINCE_FIRST_ABS
    val isAbsolute: Boolean get() = this == TODAY_ABS || this == SINCE_ADDED_ABS || this == SINCE_FIRST_ABS
}

// Kurs und Zeitpunkt, als ein Wert auf die Watchlist kam. Der Eintrag bleibt beim Entfernen erhalten. firstAddedAt
// und firstPrice gelten fuer das erste Hinzufuegen, addedAt und price fuer das letzte. Fehlen die ersten, gilt das
// letzte auch als erstes.
@Serializable
data class WatchMeta(val addedAt: Long, val price: Double, val firstAddedAt: Long = 0L, val firstPrice: Double = 0.0) {
    val ersterZeitpunkt: Long get() = if (firstAddedAt > 0L) firstAddedAt else addedAt
    val ersterKurs: Double get() = if (firstPrice > 0.0) firstPrice else price

    val wiederAufgenommen: Boolean get() = firstAddedAt > 0L && firstAddedAt < addedAt
}

enum class Side { BUY, SELL }
enum class OrderType { MARKET, LIMIT, STOP, TRAILING_STOP }
enum class OrderStatus { OPEN, FILLED, CANCELLED, REJECTED, EXPIRED }

// Gueltigkeit einer Limit-/Stop-Order: bis Handelsschluss oder 360 Tage.
enum class Validity { DAY, GTC }

@Serializable
data class Order(
    val id: String,
    val key: String,
    val name: String,
    val symbol: String,
    val side: Side,
    val type: OrderType,
    val qty: Double,
    // Limit- oder Stoppreis; bei Trailing-Stops der nachgezogene Stoppreis.
    val limitPrice: Double = 0.0,
    val status: OrderStatus = OrderStatus.OPEN,
    val createdAt: Long = 0L,
    val executedAt: Long = 0L,
    val executedPrice: Double = 0.0,
    val note: String = "",
    val derivative: Derivative? = null,
    val validity: Validity = Validity.GTC,
    // Zeitpunkt, ab dem die Order verfaellt; 0 = wird aus validity abgeleitet.
    val expiresAt: Long = 0L,
    // Trailing-Stop: Abstand in Prozent zum Hoechst- (Verkauf) bzw. Tiefstkurs (Kauf).
    val trailPct: Double = 0.0,
    // Trailing-Stop: Hoechst- bzw. Tiefstkurs seit Aufgabe der Order.
    val trailRef: Double = 0.0,
    // Maschinenlesbarer Grund fuer Storno/Ablehnung, wird lokalisiert angezeigt.
    val reason: String = "",
)

enum class PlanInterval { WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY }

// Sparplan mit festem Betrag. dayOfMonth ist der Ausfuehrungstag (2 = Anfang, 16 = Mitte des Monats);
// zweiwoechentliche Plaene laufen am 2. und 16., woechentliche alle sieben Tage ab createdAt.
@Serializable
data class SavingsPlan(
    val id: String,
    val symbol: String,
    val name: String,
    val amount: Double,
    val dayOfMonth: Int,
    val lastRun: Long = 0L,
    val createdAt: Long = 0L,
    val interval: PlanInterval = PlanInterval.MONTHLY,
    val paused: Boolean = false,
    // Summe aller Ausfuehrungen.
    val invested: Double = 0.0,
    val executions: Int = 0,
    // Letzter Fehlschlag (fehlende Deckung); 0 = keiner.
    val lastFailed: Long = 0L,
    // Uhrzeit der Ausfuehrung in Minuten nach Mitternacht (Ortszeit); -1 = am Ausfuehrungstag, sobald ein Kurs da ist.
    val uhrzeit: Int = -1,
)

// Hoechstlaenge einer Notiz je Wertpapier (Zeichen).
const val NOTIZ_MAX = 2000

// Preisalarm: meldet, sobald der Kurs die Marke ueber- oder unterschreitet, und wird danach geloescht.
@Serializable
data class PriceAlert(
    val id: String,
    val symbol: String,
    val name: String,
    val target: Double,
    // true = Alarm, wenn der Kurs auf oder ueber die Marke steigt; false = auf oder unter.
    val above: Boolean,
    val createdAt: Long = 0L,
    val referencePrice: Double = 0.0,
    // Seit 5.4: Alarm auf die Tagesbewegung. Ist der Wert groesser 0, meldet der Alarm, sobald sich der Kurs an einem
    // Handelstag um mindestens so viele Prozent bewegt (nach oben oder unten), hoechstens einmal je Tag, und bleibt
    // bestehen; target und above gelten dann nicht.
    val tagesProzent: Double = 0.0,
    // Handelstag (Tage seit 1970 in der Boersenzeit) der letzten Meldung eines Tagesalarms.
    val gemeldetAm: Long = 0L,
)

enum class TxType { DEPOSIT, WITHDRAWAL, BUY, SELL, FEE, INTEREST, KNOCKOUT, EXPIRY, DIVIDEND, TAX, ALERT, PLAN, BET }

// Buchung. Die Anzeige erzeugt Titel und Zweitzeile aus tag, via und key in der gewaehlten Sprache.
// Aeltere Buchungen ohne diese Felder behalten ihre Texte in title und subtitle.
@Serializable
data class Transaction(
    val id: String,
    val ts: Long,
    val type: TxType,
    val title: String,
    val subtitle: String = "",
    // Wirkung auf das Verrechnungskonto: negativ = Abfluss.
    val amount: Double = 0.0,
    val qty: Double = 0.0,
    val price: Double = 0.0,
    val symbol: String = "",
    // Einbehaltene Steuer, bereits in amount beruecksichtigt.
    val tax: Double = 0.0,
    // Realisiertes Ergebnis bei Verkaeufen (vor Steuern).
    val realized: Double = 0.0,
    // Freitext des Nutzers (Handelstagebuch).
    val note: String = "",
    // Gebuehr, die zu dieser Buchung gehoert (nur informativ).
    val fee: Double = 0.0,
    // Depotschluessel (bei Derivaten die Produktkennung), sonst wie symbol.
    val key: String = "",
    // Art der Buchung fuer die lokalisierte Anzeige: OPENING, DEPOSIT, WITHDRAWAL, FEE, INTEREST, KO_SETTLE, EXPIRY,
    // EXPIRY_WORTHLESS, DIVIDEND, ALERT_ABOVE, ALERT_BELOW, ALERT_MOVE, PLAN_FAILED, BUY, SELL, TRANSFER_IN (Import).
    val tag: String = "",
    // Ausloeser: "" (manuell), PLAN (Sparplan), IMPORT (uebernommene Position), ORDER (Limit/Stop).
    val via: String = "",
    // Zusatzwert je Art, etwa Zinssatz in Prozent oder Alarmmarke.
    val extra: Double = 0.0,
    // Ende der Position, wenn es vor der Buchung liegt: Knock-out (Rueckzahlung zwei Tage spaeter) oder Verfall
    // eines Optionsscheins. 0 = wie ts.
    val closedAt: Long = 0L,
    // Das Produkt bei Kauf, Verkauf, Knock-out und Verfall, damit es sich im Depotverlauf auch nach dem Verkauf
    // fuer die Haltezeit bewerten laesst.
    val derivative: Derivative? = null,
) {
    val posKey: String get() = key.ifBlank { symbol }
}

// Steuerstand des laufenden Jahres: verbrauchter Freistellungsauftrag, Verlustverrechnungstoepfe
// und einbehaltene Steuern. Beim Jahreswechsel werden Freibetrag und Steuersumme zurueckgesetzt,
// die Toepfe wandern ins neue Jahr.
@Serializable
data class TaxState(
    val year: Int = 0,
    val allowanceUsed: Double = 0.0,
    val lossPotStocks: Double = 0.0,
    val lossPotGeneral: Double = 0.0,
    val taxPaid: Double = 0.0,
    val gainsStocks: Double = 0.0,
    val gainsGeneral: Double = 0.0,
)

@Serializable
data class Snapshot(
    val ts: Long,
    val value: Double,
    // Nullable, weil alte Eintraege nur das Gesamtvermoegen enthalten.
    val securities: Double? = null,
    val profit: Double? = null,
)

@Serializable
data class Account(
    val cash: Double = 0.0,
    val deposited: Double = 0.0,
    val positions: List<Position> = emptyList(),
    val orders: List<Order> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val history: List<Snapshot> = emptyList(),
    val watchlist: List<String> = emptyList(),
    val plans: List<SavingsPlan> = emptyList(),
    val createdAt: Long = 0L,
    val lastMaintainDay: Long = 0L,
    val lastInterestDay: Long = 0L,
    val alerts: List<PriceAlert> = emptyList(),
    val tax: TaxState = TaxState(),
    // Seit der letzten Gutschrift aufgelaufene Zinsen (taeglich berechnet, monatlich gebucht).
    val interestAccrued: Double = 0.0,
    val withdrawn: Double = 0.0,
    // Kurs und Zeitpunkt je Watchlist-Eintrag, fuer "seit Hinzufuegen".
    val watchMeta: Map<String, WatchMeta> = emptyMap(),
    // Einstandswert aller uebernommenen Positionen (Import ohne Geldfluss).
    val transferredIn: Double = 0.0,
    // Uebungswetten auf Prognosemaerkte, offene und abgeschlossene.
    val wetten: List<Wette> = emptyList(),
    // Eigene Notizen je Wertpapier (Symbol), seit 5.4.
    val notizen: Map<String, String> = emptyMap(),
) {
    // Kapital, an dem das Ergebnis gemessen wird: Einzahlungen plus Einstandswert importierter Positionen.
    val capitalBase: Double get() = deposited + transferredIn

    // Guthaben, das offene Kauforders bereits beanspruchen.
    val reserved: Double
        get() = orders.filter { it.status == OrderStatus.OPEN && it.side == Side.BUY }
            .sumOf { it.qty * it.limitPrice + 1.0 }
}

// Stammdaten eines Unternehmens aus oeffentlichen Quellen (Wikidata/Wikipedia): ISIN, Logo,
// Website und die Beschreibung in beiden Sprachen. Wird je Symbol einmal geladen und gespeichert.
@Serializable
data class CompanyInfo(
    val symbol: String,
    val isin: String = "",
    val logoUrl: String = "",
    val website: String = "",
    val descriptionDe: String = "",
    val descriptionEn: String = "",
    val fetchedAt: Long = 0L,
    // true, wenn die Suche nichts fand; dann wird nicht staendig neu gefragt.
    val missing: Boolean = false,
    // Adressen der Wikipedia-Artikel, aus denen die Beschreibungen stammen; leer, wenn es keinen gibt.
    val articleDe: String = "",
    val articleEn: String = "",
)

// Uebungswette auf einen Prognosemarkt: ein Markt bei Polymarket oder Kalshi, eine Seite (Ja oder Nein bzw.
// eine von zwei Mannschaften). Ein Anteil zahlt bei Eintreten 1 Euro, der Kurs ist die Wahrscheinlichkeit.
@Serializable
data class Wette(
    val id: String,
    // POLYMARKET oder KALSHI.
    val quelle: String,
    // Polymarket: Kennung des Marktes; Kalshi: Ticker des Marktes.
    val markt: String,
    // Polymarket: Slug des Ereignisses; Kalshi: Ticker des Ereignisses.
    val ereignis: String,
    val titel: String,
    // Ausgang bzw. Frage des Marktes (bei Ereignissen mit einem Markt die Frage).
    val frage: String,
    // 0 = erste Seite (Ja), 1 = zweite Seite (Nein).
    val seite: Int,
    val seitenName: String,
    val anteile: Double,
    // Durchschnittlicher Kaufkurs je Anteil in Euro (0 bis 1).
    val einstand: Double,
    // Zuletzt bekannter Kurs der Seite.
    val kurs: Double,
    val kursAm: Long,
    val eroeffnet: Long,
    val ende: Long = 0L,
    // Polymarket: Token der Seite fuer den Verlauf.
    val token: String = "",
    // Kalshi: Serie fuer den Verlauf.
    val serie: String = "",
    val bild: String = "",
    val status: String = OFFEN,
    val geschlossen: Long = 0L,
    // Erloes beim Verkauf oder Auszahlung bei Aufloesung.
    val erloes: Double = 0.0,
) {
    val offen: Boolean get() = status == OFFEN
    val einsatz: Double get() = anteile * einstand
    val wert: Double get() = if (offen) anteile * kurs else 0.0

    companion object {
        const val OFFEN = "OFFEN"
        const val GEWONNEN = "GEWONNEN"
        const val VERLOREN = "VERLOREN"
        const val VERKAUFT = "VERKAUFT"
        // Aufgeloest mit einer Teilauszahlung (etwa 50/50).
        const val AUFGELOEST = "AUFGELOEST"
    }
}

// Bewertetes Depot, neu berechnet mit aktuellen Kursen.
data class Valuation(
    val positions: List<ValuedPosition> = emptyList(),
    val cash: Double = 0.0,
    val deposited: Double = 0.0,
    // Aktueller Wert der offenen Wetten; zaehlt zum Gesamtvermoegen, nicht zu den Wertpapieren.
    val wetten: Double = 0.0,
) {
    val securities: Double get() = positions.sumOf { it.value }
    val total: Double get() = securities + cash + wetten
    val invested: Double get() = positions.sumOf { it.invested }
    val profit: Double get() = total - deposited
    val profitPct: Double get() = if (deposited > 0) profit / deposited * 100.0 else 0.0
    val dayChange: Double get() = positions.sumOf { it.dayChange }
    val dayChangePct: Double
        get() {
            val base = securities - dayChange
            return if (base > 0) dayChange / base * 100.0 else 0.0
        }
}

data class ValuedPosition(
    val position: Position,
    val price: Double,
    val prevPrice: Double,
) {
    val key: String get() = position.key
    val qty: Double get() = position.qty
    val value: Double get() = qty * price
    val invested: Double get() = position.invested
    val profit: Double get() = value - invested
    val profitPct: Double get() = if (invested > 0) profit / invested * 100.0 else 0.0
    val dayChange: Double get() = if (prevPrice > 0) qty * (price - prevPrice) else 0.0
    val dayChangePct: Double get() = if (prevPrice > 0) (price / prevPrice - 1.0) * 100.0 else 0.0
}
