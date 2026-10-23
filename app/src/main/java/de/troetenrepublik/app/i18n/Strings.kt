package de.troetenrepublik.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Metric
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.data.WatchMetric
import java.time.DayOfWeek
import java.util.Locale

// Sprachschicht: jede sichtbare Zeichenkette hat hier einen Schluessel, De und En liefern die Texte.
// I18n.lang ist ein Compose-Zustand, damit sich die Oberflaeche beim Sprachwechsel neu zeichnet.
// Code ausserhalb von Compose wie Mitteilungen und Buchungstexte liest dieselbe Quelle.
object I18n {
    var lang: Lang by mutableStateOf(Lang.DE)
    val strings: Strings get() = if (lang == Lang.DE) De else En
    val locale: Locale get() = if (lang == Lang.DE) Locale.GERMANY else Locale.UK
}

// Kurzform fuer die Oberflaeche, etwa S.deposit.
val S: Strings get() = I18n.strings

interface Strings {
    val locale: Locale

    // Allgemein
    val appName: String
    val back: String
    val cancel: String
    val save: String
    val ok: String
    val next: String
    val done: String
    val delete: String
    val close: String
    val apply: String
    val change: String
    val buy: String
    val sell: String
    val search: String
    val transfer: String
    val invest: String
    val all: String
    val none: String
    val today: String
    val tomorrow: String
    val pieces: String
    val perAnnum: String
    val openEnd: String
    val loading: String
    val open: String
    val closed: String
    val no: String
    val more: String
    val less: String
    val copied: String
    val error: String

    // Zeitraeume und Kennzahlen
    fun rangeLabel(r: Range): String
    fun rangeCaption(r: Range): String
    // Widgets
    val widgetDepot: String
    val widgetWerte: String
    val widgetGesperrt: String
    val widgetLeer: String
    fun widgetStand(zeit: String): String
    val widgetsTitel: String
    val widgetsUntertitel: String
    val widgetsText: String
    val widgetsAuswahl: String
    val widgetsNurAndroid: String
    val screenshotsTitel: String
    val screenshotsText: String
    fun screenshotsOption(modus: String): String
    fun screenshotsHinweis(modus: String): String
    val widgetBestand: String
    val widgetMover: String
    val widgetDividenden: String
    val widgetMaerkte: String
    val widgetsAkku: String
    val widgetsAkkuOeffnen: String
    val widgetSuche: String
    val widgetOrders: String
    val widgetHeatmap: String
    val widgetNews: String
    val widgetKeinePositionen: String
    val widgetMoverRuhig: String
    val widgetDivKeinBestand: String
    val widgetDivKeine: String
    fun widgetDivErhalten(jahr: Int): String
    val widgetDivErwartet: String
    val widgetsArten: String
    val widgetEinstellung: String
    val widgetQuelle: String
    val widgetQuelleWatchlist: String
    val widgetQuelleDepot: String
    val widgetEinzelWahl: String
    val widgetEinstellungGesperrt: String
    val widgetEinstellungLeer: String
    // Widget-Einstellungen und neue Widgets (seit 5.4)
    val widgetZeitraum: String
    val widgetZeitHeute: String
    val widgetZeitJahr: String
    val widgetZeitKauf: String
    val widgetNurProzent: String
    val widgetNurProzentHinweis: String
    val widgetAnzeige: String
    val widgetBetrag: String
    val widgetBetragHinweis: String
    val widgetBarometer: String
    val widgetQuelleListe: String
    val widgetListenName: String
    val widgetListeWahl: String
    val widgetSortiert: String
    val widgetDarstellung: String
    val widgetGrund: String
    val widgetGrundVoll: String
    val widgetGrundHalb: String
    val widgetGrundLeer: String
    val widgetSchrift: String
    val widgetSchriftHell: String
    val widgetSchriftDunkel: String
    val widgetStimmung: String
    val widgetNewsTitel: String
    val widgetNewsKeine: String
    val sperreTitel: String
    fun sperreStatus(art: String?): String
    val sperreText: String
    val sperreHinweisPin: String
    val sperreVergessenHinweis: String
    val sperrePinFestlegen: String
    val sperrePasswortFestlegen: String
    val sperreAendern: String
    val sperreAusschalten: String
    val sperreAlt: String
    fun sperreNeu(pin: Boolean): String
    val sperreWiederholen: String
    val sperreUngleich: String
    fun sperreZuKurz(pin: Boolean): String
    val sperreEingerichtet: String
    val sperreGeaendert: String
    val sperreAusgeschaltet: String
    val sperreGesperrt: String
    val sperreEntsperren: String
    fun sperreFalsch(versuch: Int): String
    fun sperreWarten(sekunden: Long): String
    val sperreVergessen: String
    val sperreSchreibFehler: String
    val sperreZuruecksetzenTitel: String
    val sperreZuruecksetzenText: String
    val sperreZuruecksetzen: String
    val sperreDatenFehlen: String
    val sperreNurApp: String
    val sperreSpeichern: String
    val ppExport: String                             // Export fuer Portfolio Performance
    fun ppExportSub(n: Int): String
    val ppHinweis: String
    val ppSeite: String
    fun ppFertig(n: Int): String
    val chartProfi: String
    fun chartArt(art: String): String
    val chartHandelsplatz: String
    val chartHandelsplatzSuche: String
    val chartHandelsplatzLeer: String
    val chartVergleich: String
    val chartVergleichTitel: String
    val chartVergleichSuche: String
    val chartVergleichHinweis: String
    val chartVergleichVoll: String
    val chartIndikatorenKurs: String
    val chartIndikatorenUnten: String
    fun chartIndikatorName(key: String): String
    fun chartIndikatorInfo(key: String): String
    fun chartWerkzeug(key: String): String
    fun chartWerkzeugHinweis(key: String, zweiterPunkt: Boolean): String
    val chartLangDruecken: String
    val chartNotiz: String
    fun chartAllesLoeschen(name: String): String
    val chartKeineDaten: String
    val chartOhlc: List<String>
    val heatmap: String
    val heatmapUntertitel: String
    val heatmapDepot: String
    val heatmapMarkt: String
    val heatmapBranchen: String
    fun heatmapGroesse(quelle: String): String
    fun heatmapZaehler(steigen: Int, fallen: Int): String
    val heatmapLeer: String
    val heatmapOhneBranche: String
    val zeitraumEigener: String
    val zeitraumTitel: String
    fun metricLabel(m: Metric): String
    fun metricShort(m: Metric): String
    fun watchMetricLabel(m: WatchMetric): String
    fun watchMetricShort(m: WatchMetric): String
    val metricTitle: String
    fun typeLabel(t: AssetType): String
    fun inDays(n: Long): String
    val inOneMonth: String
    fun inMonths(n: Long): String
    fun daysAgo(n: Long): String
    fun minutesAgo(n: Long): String
    fun hoursAgo(n: Long): String
    val justNow: String
    fun weekday(d: DayOfWeek): String
    fun monthName(m: Int): String

    // Handelszeiten
    fun closedOpensToday(time: String): String
    fun closedOpensOn(weekday: String, time: String): String

    // Depotuebersicht
    val portfolio: String
    val cash: String
    val openProfile: String
    val brokerage: String
    val securities: String
    val cashAccount: String
    val total: String
    val reloadPrices: String
    val provisionalValuation: String
    val switchMetric: String
    val watchlist: String
    val savingsPlans: String
    val orders: String
    val analytics: String
    val statistics: String
    val investments: String
    val discoverInvestments: String
    val groupStocksEtfs: String
    val groupCrypto: String
    val groupDerivatives: String
    val noWatchlist: String
    val knockedOut: String
    val available: String
    fun reservedForOrders(amount: String): String
    val deposit: String
    val withdraw: String
    val interest: String
    fun interestSubtitle(accrued: String): String
    val transactions: String
    val noTransactions: String
    val incoming: String
    val outgoing: String
    val statisticsSubtitle: String
    val since: String

    // Statistik
    val statisticsIntro: String
    val year: String
    val allTime: String
    val categories: String
    fun statNode(id: String): String                 // id wie INCOME oder EXPENSES
    val netCashflow: String
    val investRate: String
    val noStatistics: String
    val tapBubbleHint: String
    fun bookingsCount(n: Int): String
    val averagePerMonth: String

    // Watchlist
    fun entriesCount(n: Int): String
    val sortByName: String
    val sortByChange: String
    val sortByPrice: String
    val sortBySinceAdded: String
    val add: String
    fun addedOn(date: String): String
    val removeFromWatchlist: String
    val addToWatchlist: String

    // Suche
    val searchTitle: String
    val searchPlaceholder: String
    val topMovers: String
    val popular: String
    val stocks: String
    val etfs: String
    val crypto: String
    val indices: String
    val results: String
    val otherVenues: String
    val searching: String
    val searchUnavailable: String
    val noResults: String
    val clear: String

    // Wertpapierseite
    val security: String
    val yourPosition: String
    val quantity: String
    val avgPrice: String
    val value: String
    val result: String
    val information: String
    val keyStats: String
    val bid: String
    val ask: String
    val spread: String
    val openPrice: String
    val dayHigh: String
    val dayLow: String
    val prevClose: String
    val high52: String
    val low52: String
    val marketCap: String
    val enterpriseValue: String
    val peRatio: String
    val forwardPe: String
    val eps: String
    val dividendYield: String
    val volume: String
    fun priceIn(ccy: String): String
    val volatility1y: String
    val beta: String
    val isin: String
    val isinCopied: String
    // Erklaerung einer Kennzahl in einfachen Worten (Tipp auf die Zeile), null fuer Zeilen ohne Erklaerung (seit 5.9)
    fun kennzahlErklaerung(label: String): String?
    val kennzahlTippen: String
    val isinKopieren: String
    val kind: String
    val sector: String
    val industry: String
    val country: String
    val employees: String
    val website: String
    val exchange: String
    val priceAlerts: String
    fun nActive(n: Int): String
    val derivatives: String
    val derivativesSubtitle: String
    val longLabel: String
    val shortLabel: String
    val longShortCaption: String
    fun leverageRange(custom: Int, factor: Int): String
    val news: String
    val events: String
    val financials: String
    val yearly: String
    val quarterly: String
    val insights: String
    val analysts: String
    fun fromEstimates(n: Int): String
    fun targetPrice(price: String): String
    val toTarget: String
    val revenue: String
    val ebitda: String
    val netIncome: String
    val grossProfit: String
    val operatingIncome: String
    val savingsPlan: String
    val eventEarnings: String
    val eventExDividend: String
    val eventDividendPayment: String
    fun expectedEps(v: String): String
    val pastEvents: String
    val upcomingEvents: String
    val noEvents: String
    fun epsActualVsEstimate(actual: String, estimate: String): String
    val description: String
    val sourceWikipedia: String
    val sourceYahoo: String
    fun recommendation(key: String): String
    val knockedOutBanner: String
    fun knockedOutSince(date: String, residual: String): String

    // Finanzen (Detail)
    val financialsTitle: String
    val income: String
    val balance: String
    val cashflow: String
    val margins: String
    val grossMargin: String
    val operatingMargin: String
    val profitMargin: String
    val totalCash: String
    val totalDebt: String
    val freeCashflow: String
    val operatingCashflow: String
    val returnOnEquity: String
    val totalAssets: String
    val totalLiabilities: String
    val equity: String
    val revenueGrowth: String
    val earningsGrowth: String
    val payoutRatio: String
    val dividendPerShare: String
    val priceToBook: String
    val noFinancials: String
    fun reportingCurrency(ccy: String): String
    val epsHistory: String
    val estimate: String
    val actual: String

    // Nachrichten
    val newsTitle: String
    fun newsSource(source: String): String
    val openInBrowser: String
    val showOriginal: String
    val showText: String
    val articleLoading: String
    val articleNoText: String

    // Handelskalender
    val calToggle: String
    fun calRealizedIn(month: String): String
    val calPrevMonth: String
    val calNextMonth: String
    val calWeekdays: List<String>
    val calRealized: String
    val calTrades: String
    fun calWinsLosses(wins: Int, losses: Int): String
    val calDividends: String
    fun calExpectedAmount(amount: String): String
    val calLegendReceived: String
    val calLegendExpected: String
    val calClosedTrades: String
    val calDayResult: String
    val calFees: String
    val calTaxes: String
    val calAfterCosts: String
    val calPositions: String
    val calTradeKnockout: String
    val calTradeExpiry: String
    fun calTradeSell(qty: String, price: String): String
    val calDivExpected: String
    fun calDivReceived(qty: String, perShare: String): String

    // Ordermaske
    val overview: String
    val amount: String
    val shares: String
    val everything: String
    val market: String
    val limit: String
    val stop: String
    val trailingStop: String
    val limitPrice: String
    val stopPrice: String
    val trailDistance: String
    val setValue: String
    val validity: String
    fun validityLabel(v: Validity): String
    val limitBuyHint: String
    val limitSellHint: String
    val stopBuyHint: String
    val stopSellHint: String
    val trailingSellHint: String
    val trailingBuyHint: String
    val closedOrderHint: String
    val enterLimit: String
    val enterStop: String
    val enterTrail: String
    fun corresponds(qty: String): String
    fun correspondsAmount(amount: String): String
    val askPrice: String
    val bidPrice: String
    val availableCash: String
    val inDepot: String
    val notEnoughCash: String
    val notEnoughShares: String
    val notEnoughForFee: String
    val tradeType: String
    fun tradeTypeValue(side: String, type: OrderType): String
    val piecesLabel: String
    val counterValue: String
    val estCounterValue: String
    val fee: String
    val venue: String
    val bestPrice: String
    val productCosts: String
    val derivativeRisk: String
    val cashAfter: String
    val notePlaceholder: String
    val placeOrder: String
    val orderNotExecuted: String
    val initialStop: String

    // Orders
    val ordersTitle: String
    fun ordersOpenHint(n: Int): String
    val openOrders: String
    val noOpenOrders: String
    val doneOrders: String
    val cancelOrder: String
    fun validUntil(ts: String): String
    fun orderStatus(s: OrderStatus): String
    fun orderTypeLabel(t: OrderType): String
    fun trailingInfo(pct: String, stop: String): String
    fun reason(code: String): String

    // Sparplaene
    val plan: String
    val plans: String
    fun planCount(n: Int): String
    val planAmountHint: String
    val interval: String
    fun intervalLabel(i: PlanInterval): String
    fun intervalSubtitle(i: PlanInterval): String
    val execution: String
    val startOfMonth: String
    val midOfMonth: String
    fun onDay(d: Int): String
    val planReview: String
    val savingsRate: String
    val executionDay: String
    val executionDays: String
    val firstExecution: String
    val paymentMethod: String
    val planNote: String
    val create: String
    val managePlan: String
    val status: String
    val paused: String
    val active: String
    val nextExecution: String
    val executions: String
    val investedSoFar: String
    val lastFailure: String
    val changeRate: String
    val changeInterval: String
    val planUhrzeit: String
    val planUhrzeitAendern: String
    val planUhrzeitAuto: String
    val planUhrzeitAutoText: String
    val planUhrzeitHinweis: String
    fun planUhrzeitWert(uhr: String): String
    val resume: String
    val pause: String
    val deletePlanTitle: String
    val deletePlanText: String
    val noPlans: String
    fun perMonthPlans(amount: String, n: Int): String
    val createPlan: String
    fun nextOn(date: String): String
    fun investedAmount(amount: String): String

    // Preisalarme
    val createAlert: String
    val enterTargetPrice: String
    fun alertRises(price: String, pct: String): String
    fun alertFalls(price: String, pct: String): String
    val targetEqualsPrice: String
    val setAlert: String
    val whenRises: String
    val whenFalls: String
    fun setAt(price: String): String
    fun alertCount(n: Int): String
    val alertsHint: String
    // Bullen- oder Baerenmarkt (seit 5.9)
    val mpTitel: String
    fun mpGesamt(bullen: Int, alle: Int): String
    fun mpIndex(schluessel: String): String
    fun mpPhase(art: String, korrektur: Boolean, seit: String): String
    fun mpVomHoch(wert: String): String
    fun mp200(wert: String): String
    fun mpDetail(art: String, vomTief: String, golden: Boolean?, kreuzSeit: String?): String
    val mpHinweis: String
    // Positionierung an den Terminmaerkten (CFTC, seit 5.8)
    val cotTitel: String
    fun cotMarkt(schluessel: String): String
    fun cotLage(index: Int): String
    fun cotVorwoche(aenderung: String): String
    fun cotErklaerung(index: Int): String
    fun cotQuelle(stand: String): String
    val cotHinweis: String
    // Leerverkaufspositionen der AMF (seit 5.8)
    val lpTitel: String
    fun lpSumme(anteil: String, anzahl: Int): String
    fun lpVeraenderung(aenderung: String): String
    val lpKeine: String
    fun lpSeit(tag: String): String
    fun lpQuelle(land: String, stand: String): String
    fun lpHinweis(land: String): String
    fun lpGesamt(anteil: String): String
    // FDA (seit 5.9)
    val fdaTitel: String
    val fdaZulassungen: String
    val fdaRueckrufe: String
    fun fdaArt(art: String, vorrang: Boolean): String
    fun fdaKlasse(klasse: Int, geraet: Boolean, laufend: Boolean): String
    val fdaKeine: String
    val fdaQuelle: String
    val fdaHinweis: String
    // Insider Schweden (seit 5.8)
    fun euQuelle(land: String, stand: String): String
    val euSedi: String
    val euSediLink: String
    fun euArt(art: String): String
    fun euRolle(r: String): String
    // Bull- und Bear-Case (seit 5.7)
    val bbTitel: String
    val bbDafuer: String
    val bbDagegen: String
    val bbHinweis: String
    fun bbThema(thema: String): String
    fun bbKennzahl(kennzahl: String, w: List<String>): String
    // US-Insider (SEC Form 4, seit 5.6)
    val vdDeutschland: String
    val vdUsa: String
    val vdKaeufe1: String
    val vdVerkaeufe1: String
    val usHinweis: String
    val usOhneKontakt: String
    val usPlan: String
    fun usQuelle(stand: String): String
    fun usRolle(r: String): String
    val zgSecTitel: String
    val zgSecZweck: String
    val zgSecFeld: String
    val zgSecUngueltig: String
    // Insider-Trend (seit 5.6)
    val itTitel: String
    fun itStufe(stufe: Int): String
    // Ein Satz zur Stufe fuer Einsteiger (seit 5.9)
    fun itSatz(stufe: Int): String
    fun itAnzahl(kaeufer: Int, verkaeufer: Int): String
    fun itRolle(rolle: Int): String
    fun itRolleZeile(rolle: String, kaeufer: Int, verkaeufer: Int): String
    val itCluster: String
    val itClusterKurz: String
    val itHinweis: String
    // Eigengeschaefte der Fuehrung und Grossaktionaere aus den Datenbanken der BaFin (seit 5.5)
    val copyExecutives: String
    val vdHinweis: String
    val vdMeistgekauft: String
    val vdMeistverkauft: String
    val vdNeueste: String
    val vdAlle: String
    val vdKaeufe: String
    val vdVerkaeufe: String
    val vdLeer: String
    val vdKeinKurs: String
    val vdKarteTitel: String
    val grTitel: String
    val grHinweis: String
    fun vdQuelle(stand: String): String
    fun vdAnzahl(kaeufe: Int, verkaeufe: Int): String
    fun vdZusammenfassung(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String): String
    fun vdArt(art: String): String
    fun vdRolle(rolle: String): String
    // Kursalarme auf die Tagesbewegung und Notizen je Wert (seit 5.4)
    val alertArtMarke: String
    val alertArtBewegung: String
    val enterMovePct: String
    fun alertMove(pct: String): String
    fun alertMoveRow(pct: String): String
    val alertMoveWiederholt: String
    val alertMoveUngueltig: String
    fun notifAlertMoveText(pct: String, price: String): String
    val notiz: String
    val notizHinzufuegen: String
    val notizHinweis: String
    val notizLoeschen: String

    // Ueberweisung
    fun availableAmount(amount: String): String
    val depositSubtitle: String
    val withdrawToReference: String
    val enterAmount: String
    fun availableForWithdrawal(amount: String): String
    val moreThanAvailable: String
    val process: String
    val from: String
    val to: String
    val virtualFunds: String
    val referenceAccount: String
    val arrival: String
    val bankDays: String
    val availableAfter: String
    val feeLabel: String
    val free: String
    val send: String
    val withdrawFailed: String
    val depositBooked: String
    val withdrawalSent: String
    fun depositAvailableNow(amount: String): String
    fun withdrawalArrives(amount: String): String

    // Profil
    val yourProfile: String
    fun practiceSince(date: String): String
    val totalWealth: String
    val depot: String
    val activity: String
    val activitySubtitle: String
    val documents: String
    val documentsSubtitle: String
    fun nOpen(n: Int): String
    val analyticsSubtitle: String
    val statisticsProfileSubtitle: String
    val transferSubtitle: String
    val notSet: String
    val interestOnCash: String
    val interestAuto: String
    fun ecbRate(rate: String, since: String): String
    val ecbNotLoaded: String
    val customRate: String
    val enterRate: String
    val accruedThisMonth: String
    val deposited: String
    val withdrawn: String
    val taxes: String
    val taxOverview: String
    fun allowanceSet(amount: String): String
    val allowanceNotSet: String
    val securityTitle: String
    val hideBalances: String
    val shakeToHide: String
    val trading: String
    val spreadStocks: String
    val spreadDerivatives: String
    val spreadHint: String
    val appearance: String
    val appearanceSystem: String
    val appearanceLight: String
    val appearanceDark: String
    val language: String
    val languageGerman: String
    val languageEnglish: String
    val coloredChart: String
    val kachelnTitel: String
    val kachelnWischen: String
    val kachelnRaster: String
    val kachelnHinweis: String
    val notifications: String
    val notificationsSwitch: String
    val notificationsHint: String
    val notificationsHintDesktop: String
    val marketData: String
    val refreshEvery: String
    fun everySeconds(n: Int): String
    val source: String
    val statusLabel: String
    val statusLimited: String
    val statusLoading: String
    val statusConnected: String
    val refreshNow: String
    val backupTitle: String
    val exportPortfolio: String
    val exportSubtitle: String
    val importPortfolio: String
    val importSubtitle: String
    val backupHint: String
    val aboutTitle: String
    val kaffeeTitel: String
    val kaffeeText: String
    val aboutText: String
    val version: String
    val accountOpened: String
    val resetDepot: String
    val resetTitle: String
    fun resetText(amount: String): String
    val reset: String
    val personalData: String
    val firstName: String
    val exportTitle: String
    fun exportDone(kb: Int): String
    val exportFailed: String
    val importFailedTitle: String
    val importNotReadable: String
    fun importError(code: String, extra: Int): String
    val importTitle: String
    fun exportedAt(ts: String): String
    fun importSummary(cash: String, positions: Int, plans: Int, tx: Int): String
    val knowledge: String
    val knowledgeSubtitle: String
    // Windows und Linux: Finanzwissen oeffnet im Browser.
    val knowledgeInBrowser: String
    val knowledgeOpenAgain: String
    fun knowledgeBrowserFailed(url: String): String

    // Steuern
    val allowanceTitle: String
    val allowanceNotSetTitle: String
    fun allowanceIntro(max: String): String
    fun allowanceUsed(used: String, year: Int, free: String): String
    val activate: String
    val manage: String
    fun taxOverviewYear(year: Int): String
    val taxRate: String
    fun taxRateValue(pct: String): String
    val taxWithheld: String
    val realizedStockGains: String
    val otherIncome: String
    val lossPotStocks: String
    val lossPotGeneral: String
    val taxNote: String
    val taxData: String
    val taxId: String
    val editTaxId: String
    val churchTax: String
    val notModelled: String
    fun taxCertificate(year: Int): String
    fun allowanceSheetIntro(max: String): String
    val taxIdHint: String

    // Aktivitaet und Buchungen
    val activityTitle: String
    val filterAll: String
    val filterTrade: String
    val filterCash: String
    val filterIncome: String
    val noActivity: String
    fun txLabel(t: TxType): String
    val toSecurity: String
    val showDocument: String
    fun txTitle(tag: String, storedTitle: String): String
    fun txSubtitle(tag: String, via: String, qty: String, price: String, realized: String, extra: String, stored: String): String

    // Dokumente
    val documentsTitle: String
    val taxSection: String
    val statements: String
    val settlements: String
    fun taxCertificateTitle(year: Int): String
    val taxCertificateSubtitle: String
    fun statementTitle(month: String): String
    val statementSubtitle: String
    val noBookings: String
    val settlementsHint: String
    fun docTitle(t: TxType): String
    val docCustomer: String
    val docDate: String
    val docNumber: String
    val docTradeType: String
    val docSecurity: String
    val docSymbol: String
    val docPieces: String
    val docExecutionPrice: String
    val docMarketValue: String
    val docFee: String
    val docRealized: String
    val docTax: String
    val docVenue: String
    val docVenueValue: String
    val docDebit: String
    val docCredit: String
    val docDividendPerShare: String
    val docGross: String
    val docPeriod: String
    fun docUntil(date: String): String
    val docInterestGross: String
    val docProduct: String
    val docRefundPerPiece: String
    val docRecipient: String
    val docNote: String
    val docOpening: String
    val docClosing: String
    val docTaxYear: String
    val docRealizedGains: String
    val docDividendsGross: String
    val docInterestGrossYear: String
    val docAllowance: String
    val docAllowanceUsed: String
    val docLossPotStocks: String
    val docLossPotGeneral: String
    val docTaxWithheld: String
    val docDisclaimer: String
    val docPracticeLine: String
    val docNoTaxEffect: String

    // Einrichtung und PIN
    fun stepOf(n: Int, total: Int): String
    val welcome: String
    val welcomeText: String
    val startCapital: String
    val startCapitalText: String
    val letsGo: String

    // Werkzeuge auf der Wertpapierseite
    val toolsTitle: String
    fun toolTitle(key: String): String
    fun toolSubtitle(key: String): String
    val toolAmount: String
    val toolPeriod: String
    fun toolYears(n: Int): String
    val toolMax: String
    val toolMonthly: String
    val toolLoading: String
    val toolNoData: String
    fun toolBoughtOn(date: String, price: String): String
    val toolPerYear: String
    val toolWorldEtf: String
    val toolWorldEtfShort: String
    val toolNoCosts: String
    val toolRiskWorldNote: String
    val toolWorldNote: String
    val toolInvested: String
    val toolExecutions: String
    val toolAverageCost: String
    val toolPerYearMoneyWeighted: String
    val toolPlanNote: String
    val toolVolatility: String
    val toolDrawdownPeriod: String
    fun toolDrawdownRange(from: String, to: String): String
    val toolRecovered: String
    val toolNotRecovered: String
    val toolWorstDay: String
    val toolBestDay: String
    val toolUpDays: String
    val toolRiskNote: String
    val toolDividendTrailing: String
    fun toolPerShare(v: String): String
    val toolDividendYield: String
    val toolPayments: String
    val toolDividendGrowth: String
    val toolShares: String
    fun toolMyHolding(qty: String): String
    val toolGrossPerYear: String
    val toolTaxApprox: String
    val toolNetPerYear: String
    val toolNetPerMonth: String
    val toolNoDividends: String
    val toolDividendNote: String
    val toolGrowth: String
    val toolExitPe: String
    val toolRequired: String
    val toolEpsNow: String
    val toolPeNow: String
    fun toolEpsIn(n: Int): String
    fun toolPriceIn(n: Int): String
    val toolFairToday: String
    val toolCurrentPrice: String
    fun toolAbove(pct: String): String
    fun toolBelow(pct: String): String
    val toolImpliedReturn: String
    val toolFairNote: String
    val toolRiskPerTrade: String
    val toolStopDistance: String
    val toolDepotValue: String
    val toolStopPrice: String
    val toolLossPerUnit: String
    val toolMaxLoss: String
    val toolCapital: String
    fun toolShareOfDepot(pct: String): String
    val toolMoreThanCash: String
    val toolPositionNote: String
    val toolSellShare: String
    val toolSellPrice: String
    val toolProceeds: String
    val toolRealized: String
    val toolTax: String
    val toolFee: String
    val toolCredit: String
    val toolSellNote: String

    // Position importieren
    val importPosition: String
    val importTile: String
    val importPositionSubtitle: String
    val importIntro: String
    val importSearchHint: String
    val importChange: String
    val importDate: String
    val importQty: String
    val importPrice: String
    fun importCloseOn(date: String, price: String): String
    val importUseClose: String
    val importNoClose: String
    val importCostBasis: String
    val importValueToday: String
    val importResult: String
    val importConfirm: String
    fun importDone(qty: String, name: String): String
    val importDividendNote: String
    val transferredIn: String
    val docTransferIn: String
    val docNoCashFlow: String
    val noHistory: String

    // Analytics
    val assets: String
    val positions: String
    val breakdown: String
    val sortTotal: String
    val sortName: String
    val sortSinceBuy: String
    val keyFigures: String
    val positionsCount: String
    val resultSinceBuy: String
    val maxDrawdown: String
    val largestPosition: String
    val assetClasses: String
    fun assetClass(key: String): String

    // Werkzeuge in Analytics
    fun ptTitle(key: String): String
    fun ptSubtitle(key: String): String
    val ptCheckRules: String
    val ptCheckRulesSub: String
    fun ptBenchName(key: String): String
    val ptYourDepot: String
    val ptTimeWeightedHint: String
    val ptCompareIntro: String
    val ptInvested: String
    val ptDepotToday: String
    val ptDepotTodayHint: String
    fun ptWith(name: String): String
    val ptDifference: String
    val ptPerYear: String
    val ptSimpleReturn: String
    fun ptNoPricesBefore(name: String, date: String): String
    val ptSameMoneyNote: String
    fun ptCashNote(rate: String): String
    fun ptSingleTitle(name: String): String
    val ptBeats: String
    val ptTrails: String
    val ptNoPurchases: String
    val ptNoPositions: String
    val ptStartValue: String
    fun ptStartSecurities(v: String): String
    fun ptStartTotal(v: String): String
    val ptStartZero: String
    val ptStartEigen: String
    val ptStartEigenWert: String
    val ptMonthlySaving: String
    val ptHorizon: String
    val ptReturn: String
    val ptVolatility: String
    val ptRaise: String
    fun ptRealTerms(infl: String): String
    val ptTarget: String
    val ptMedian: String
    val ptWeak: String
    val ptStrong: String
    val ptPaidIn: String
    val ptGrowth: String
    fun ptTargetChance(pct: String): String
    fun ptTargetReached(amount: String): String
    val ptTargetMedian: String
    fun ptTargetWhen(years: Int): String
    val ptTargetNever: String
    fun ptInYears(n: Int): String
    val ptToday: String
    fun ptReadout(median: String, low: String, high: String): String
    val ptBandLabel: String
    val ptWealthNote: String
    val ptMonthlyWithdrawal: String
    fun ptIndexed(infl: String): String
    fun ptLasts(pct: String, years: String): String
    fun ptLeftAfter(years: Int): String
    val ptUsedUpAfter: String
    val ptSafeRate: String
    val ptFourPercent: String
    fun ptPerMonth(v: String): String
    val ptPlannedTotal: String
    val ptNoStart: String
    val ptWithdrawalNote: String
    fun ptCrashName(key: String): String
    val ptCrashIntro: String
    val ptReference: String
    fun ptHardestHit(name: String, pct: String): String
    fun ptRecovery(duration: String): String
    val ptNotRecovered: String
    fun ptDuration(days: Long): String
    fun ptEstimated(n: Int, total: Int): String
    val ptCustomScenario: String
    val ptMarketFalls: String
    val ptEstimatedDepot: String
    val ptCrashNote: String
    val ptDivExpected: String
    fun ptDivAfterTax(v: String): String
    val ptDivPerMonth: String
    val ptDivYield: String
    val ptDivYieldOnCost: String
    val ptDivReceived: String
    val ptDivNext: String
    val ptDivTop: String
    fun ptDivTimes(n: Int): String
    val ptDivNone: String
    val ptDivNote: String
    val ptTop3: String
    val ptEffective: String
    fun ptEffectiveValue(n: String): String
    val ptCurrencies: String
    fun ptGroup(key: String): String
    val ptCorrelation: String
    val ptAverage: String
    val ptCorrelationNote: String
    val ptEtfCosts: String
    fun ptEtfCostValue(ter: String, perYear: String): String
    val ptDiversificationNote: String
    val ptModeRebalance: String
    val ptModeBuyOnly: String
    val ptNewMoney: String
    fun ptCash(v: String): String
    val ptEqualWeights: String
    val ptCurrentWeights: String
    val ptTargetWeights: String
    fun ptBuyAmount(v: String): String
    fun ptSellAmount(v: String): String
    val ptOnTarget: String
    val ptBuys: String
    val ptSells: String
    fun ptOrdersFees(n: Int, fee: String): String
    val ptTaxEstimate: String
    val ptDeviation: String
    val ptNeedTwo: String
    val ptRebalanceNote: String
    fun ptPoints(v: String): String
    val ptOthers: String
    fun ptFromTo(a: String, b: String): String

    // Derivate
    val knockOuts: String
    val warrants: String
    val factors: String
    val call: String
    val put: String
    val long: String
    val short: String
    val strikePrice: String
    val maturity: String
    val issuer: String
    val issuerNote: String
    val ecbAbbrev: String
    val leverage: String
    val factor: String
    val delta: String
    val knockOut: String
    val ratio: String
    val product: String
    val allLeverages: String
    val allFactors: String
    val customLeverage: String
    val customLeverageHint: String
    fun leverageDialogHint(min: Int, max: Int): String
    val leverageOwn: String
    val noProductForFilter: String
    val noPriceForProduct: String
    fun productKind(kind: String): String        // TURBO, WARRANT oder FACTOR
    fun distance(pct: String): String
    fun dueOn(date: String): String
    fun resultsCount(n: Int): String
    val minStrike: String
    val maxStrike: String
    val startDateFilter: String
    val endDateFilter: String
    val strategy: String
    val productInfo: String
    val underlying: String
    val underlyingPrice: String
    val distanceToBarrier: String
    val remainingDays: String
    fun days(n: Long): String
    val impliedVol: String
    val intrinsicValue: String
    val timeValue: String
    val premium: String
    val breakEven: String
    val sensitivities: String
    val gamma: String
    val vega: String
    val theta: String
    val currency: String
    val referencePrice: String
    val riskKnockout: String
    val riskFactor: String
    val riskWarrant: String
    val modelledHistory: String
    val noFactorHistory: String
    val productUnavailable: String
    fun leverageValue(v: String): String
    val derivative: String
    val deltaFilter: String

    // Fehler und Meldungen
    fun error(code: String, extra: String): String
    fun buyDone(qty: String, price: String): String
    fun sellDone(qty: String, price: String): String
    val refreshFirst: String
    val marketClosedOrders: String
    val limitPlaced: String
    val stopPlaced: String
    val trailingPlaced: String
    val orderCancelled: String
    fun depositedToast(amount: String): String
    fun withdrawnToast(amount: String): String
    val invalidAmount: String
    val invalidPlan: String
    val planCreated: String
    val planPaused: String
    val planResumed: String
    val planChanged: String
    val planDeleted: String
    val invalidPrice: String
    val alertSet: String
    val accountReset: String
    val depotImported: String
    val watchAdded: String
    val watchRemoved: String

    // Mitteilungen
    val notifDividend: String
    fun notifDividendText(name: String, amount: String): String
    val notifInterest: String
    fun notifInterestText(amount: String): String
    val notifKnockout: String
    fun notifKnockoutText(name: String): String
    val notifKnockoutSettled: String
    fun notifKnockoutSettledText(name: String, amount: String): String
    val notifExpiry: String
    fun notifExpiryText(name: String, amount: String): String
    fun notifAlert(name: String): String
    fun notifAlertText(price: String, target: String): String
    val notifPlanFailed: String
    fun notifPlanFailedText(name: String): String
    val notifPlanDone: String
    fun notifPlanDoneText(name: String, amount: String): String
    val notifOrder: String
    fun notifOrderText(side: String, qty: String, name: String, price: String): String
    val channelTrades: String
    val channelAlerts: String
    val channelCash: String
    val channelUpdates: String
    val channelNews: String
    val channelRueckblick: String
    val channelCopy: String
    val aufklappMehr: String
    val profilEinstellungen: String
    val profilSicherheit: String
    val profilDaten: String
    val profilErweitert: String
    val aufklappWeniger: String
    // Tagesrueckblick
    val rbTitel: String
    fun rbProfil(anzahl: Int, an: Boolean): String
    val rbSchalter: String
    val rbSchalterHinweis: String
    val rbLeer: String
    val rbJetzt: String
    val rbJetztLaeuft: String
    val rbJetztFehler: String
    fun rbWoche(von: String, bis: String): String
    val rbWocheUeberblick: String
    val rbMarkt: String
    val rbDepot: String
    val rbBewegungen: String
    val rbNachrichten: String
    fun rbDepotZeile(aenderung: String, prozent: String?, wert: String): String
    fun rbWahrscheinlich(titel: String, quelle: String): String
    fun rbWahrscheinlichKurz(titel: String): String
    fun rbMitMarkt(markt: String, aenderung: String): String
    val rbOhneGrund: String
    val rbKeinDepot: String
    val rbKeineBewegung: String
    val rbKeineNachrichten: String
    val rbVorlaeufig: String
    val rbHinweis: String
    fun rbMitteilungTitel(datum: String): String
    fun rbWochenTitel(von: String, bis: String): String
    fun rbFazitTon(stufe: Int, woche: Boolean): String
    fun rbFazitDepot(ton: String, hoch: Boolean, prozent: String, betrag: String): String
    fun rbFazitVergleich(markt: String, punkte: String, lage: Int): String
    fun rbFazitBeweger(name: String, prozent: String): String
    fun rbFazitMarkt(markt: String, prozent: String, lage: Int, woche: Boolean): String
    fun rbFazitBranchen(beste: String, bp: String, schwaechste: String, sp: String): String
    val rbBranchen: String
    val rbStaerkste: String
    val rbSchwaechste: String
    val rbGewinner: String
    val rbVerlierer: String
    val rbMerkliste: String
    val rbAufgeklappt: String
    val rbZugeklappt: String
    fun newsNotifImportant(quelle: String): String
    fun newsNotifMany(n: Int): String
    fun newsNotifManyImportant(n: Int): String
    val newsNotifyTitle: String
    fun newsNotifyOption(stufe: String): String
    fun newsNotifyKurz(stufe: String): String
    fun newsNotifyOptionHint(stufe: String): String
    val newsNotifyOffGlobal: String
    val newsNotifyHint: String
    val ctcBuys: String
    val awTitle: String
    val awLoading: String
    val awNone: String
    val awBuysAfter: String
    val awSalesAfter: String
    fun awHorizon(tage: Int): String
    fun awBuyLine(imPlus: String, besser: String, median: String, markt: String?, anzahl: Int): String
    fun awSellLine(gefallen: String, schlechter: String, median: String, anzahl: Int): String
    val awFrequency: String
    val awPerMonth: String
    val awBusiest: String
    val awTypical: String
    val awOverMillion: String
    val awBuyShare: String
    val awUnusual: String
    fun awUnusualLine(anzahl: String, schnitt: String): String
    fun awSectorsHead(grenze: String): String
    fun awSectorCoverage(anteil: String): String
    val awBeforeMoves: String
    fun awBeforeBuy(anteil: String, basis: String, anzahl: Int): String
    fun awBeforeSell(anteil: String, basis: String, anzahl: Int): String
    fun awNote(bewertet: Int, werte: Int, hoechstens: Int): String
    fun dcSummary(gut: Int, von: Int): String
    fun dcTitle(regel: String): String
    fun dcValue(regel: String, anteil: String, name: String, anzahl: Int): String
    fun dcExplain(regel: String): String
    val dcNote: String
    fun copyPaperRead(meldungen: Int, unlesbar: Int): String
    fun derivIntro(anzahl: Int, bewertet: Int): String
    val derivInvestorIntro: String
    val derivCall: String
    val derivPut: String
    val derivStructured: String
    val derivOther: String
    // Basispreis in US-Dollar; preis nur die Zahl.
    fun derivStrike(preis: String): String
    fun derivExpiry(datum: String): String
    fun derivContracts(anzahl: String): String
    fun derivAll(anzahl: Int): String
    val ctcSells: String
    val ctcDays: String
    fun ctcApprox(betrag: String): String
    fun ctcCountApprox(anzahl: String, betrag: String): String
    fun ctcReported(datum: String): String
    val ctcEmpty: String
    val ctcHint: String
    val updNotifTitle: String
    fun updNotifText(fassung: String): String
    val updSwitch: String
    val updSwitchHint: String
    fun updRowTitle(fassung: String): String
    val updRowSubtitle: String

    // Kurzanleitung
    val guideTitle: String
    // Portfolio Performance als Werkzeug
    fun ppfZeitraum(key: String): String
    val ppfTtwror: String
    fun ppfVonBis(von: String, bis: String): String
    val ppfTtwrorJahr: String
    val ppfUnterJahr: String
    val ppfIzf: String
    val ppfIzfHinweis: String
    val ppfAbsolut: String
    val ppfMaxRueckgang: String
    val ppfVolatilitaet: String
    val ppfNurWertpapiere: String
    val ppfNurWertpapiereHinweis: String
    val ppfBilanz: String
    val ppfLetzterTag: String
    val ppfRisiko: String
    val ppfRueckgangDauer: String
    val ppfRueckgangDauerHinweis: String
    fun ppfTage(n: Long): String
    val ppfSemivolatilitaet: String
    val ppfSemivolatilitaetHinweis: String
    val ppfHoch: String
    fun ppfWertAm(datum: String): String
    val ppfEinlagen: String
    val ppfEntnahmen: String
    val ppfKursgewinne: String
    fun ppfRealisiert(betrag: String): String
    val ppfDividenden: String
    val ppfZinsen: String
    val ppfGebuehren: String
    val ppfSteuern: String
    val ppfMonate: String
    val ppfHinweis: String
    val guideAllesTitel: String
    val guideAllesText: String
    // Was die App kann, nach Bereichen (Titel zu Text); widgets, bildschutz: gibt es auf dieser Plattform.
    fun guideFunktionen(widgets: Boolean, bildschutz: Boolean): List<Pair<String, String>>
    val guideSubtitle: String
    val guideSkip: String
    val guideMeldungenTitel: String
    val guideMeldungenText: String
    val guideMeldungenLaden: String
    fun guideMeldungenLaedt(gelesen: Int, gesamt: Int): String
    fun guideMeldungenFertig(gesamt: Int): String
    val guideSettings: String
    // Je Seite Titel und Text, in der Reihenfolge der Bilder.
    val guidePages: List<Pair<String, String>>
    // Windows und Linux: Bedienung mit Maus und Tastatur statt Wischen.
    val guidePagesDesktop: List<Pair<String, String>>

    // Entdecken (Suche)
    val topMoversTitle: String
    val bestPerformers: String
    val worstPerformers: String
    val ipos: String
    val iposIntro: String
    val upcoming: String
    val completed: String
    fun ipoCardTitle(name: String): String
    fun ipoListed(date: String): String
    fun ipoSinceIssue(pct: String): String
    fun ipoAsOf(date: String): String
    val filterTitle: String
    fun resultCount(n: Int): String
    val industries: String
    val countries: String
    val regions: String
    val assetClass: String
    val resetFilters: String
    val filterSearchPlaceholder: String
    val noFilterResults: String
    fun industryName(key: String): String
    fun regionName(key: String): String
    fun assetClassName(key: String): String          // INDEX, AKTIE, ROHSTOFF, WAEHRUNG, KRYPTO oder ANDERE
    fun countryLabel(iso: String): String

    fun koRefundOn(date: String): String              // Depotliste, unter dem Verlust
    fun koAutoSettle(date: String): String            // Automatische Ausbuchung nach zwei Tagen
    val settleNow: String
    fun settledNow(amount: String): String
    fun firstAddedOn(date: String): String

    // Nachrichten
    val newsTile: String                              // Karte auf der Startseite
    val newsPageTitle: String
    fun newsCategory(k: de.troetenrepublik.app.market.Rss.Kategorie): String
    fun newsAnbieter(a: de.troetenrepublik.app.market.Rss.Anbieter): String
    val newsAll: String
    val newsLoading: String
    val newsEmpty: String
    val newsEmptySearch: String
    val newsToday: String
    val newsYesterday: String
    val newsSearchHint: String
    val newsSearch: String
    val newsSources: String
    val newsSourcesTitle: String
    val newsSourcesHint: String
    val newsOwnFeeds: String
    val newsAddFeed: String
    val newsFeedName: String
    val newsFeedUrl: String
    val newsFeedInvalid: String
    val newsFeedNone: String
    fun newsFeedAdded(n: Int): String
    val newsFeedChecking: String
    val newsRemove: String
    val newsSave: String
    val newsUnsave: String
    val newsEmptySaved: String

    // Uebersetzungstabellen
    fun sectorName(key: String): String
    fun countryName(key: String): String

    // Copytrading
    val copyMillion: String
    val copyTile: String
    val copyTitle: String
    val copyCongress: String
    val copySenate: String
    val copyGovernment: String
    fun copySenateIntro(stand: String): String
    val copySearchSenator: String
    val copySenators: String
    val copySenateSource: String
    val copySenatorOf: String
    val copyGovIntro: String
    fun copyGovSource(stand: String): String
    val copyPresident: String
    val copyVicePresident: String
    fun copyVanceNote(stichtag: String): String
    val copyFilingsAll: String
    val copyTypeMuniBond: String
    fun copyTradesCount(n: Int): String
    fun copyHoldingsAsOf(date: String): String
    val copyWhoTitle: String
    val copyWhoLoading: String
    val copyWhoNone: String
    val copyWhoIntro: String
    val copyTendBuy: String
    val copyTendSell: String
    val copyTendMixed: String
    fun copyWhoCounts(kaufen: Int, verkaufen: Int, halten: Int): String
    val copyWhoTendency: String
    val copyWhoPersons: String
    val copyWhoBuyers: String
    val copyWhoSellers: String
    fun copyWhoHouseOpen(offen: Int, gesamt: Int): String
    val copyWhoHouseLoad: String
    fun copyWhoHouseReading(fertig: Int, gesamt: Int): String
    val copyWhoSource: String
    fun copyWhoShare(pct: String): String
    fun copyWhoTrades(kaeufe: Int, verkaeufe: Int): String
    val copyWhoMixed: String
    fun copyWhoStueck(stichtag: String, veraenderung: String, bestand: String, wert: String): String
    fun copyWhoStueckNeu(stichtag: String, bestand: String, wert: String): String
    fun copyWhoStueckWeg(stichtag: String, vorher: String): String
    fun copyWhoAlle(n: Int): String
    fun copyWhoAllePersonen(n: Int): String
    val copyWhoWeniger: String
    val copyWhoHolds: String
    val copyWhoNewPos: String
    val copyWhoIncreased: String
    val copyWhoReduced: String
    val copyWhoSoldOut: String
    val copyWhoUnchanged: String
    val copyPhotoCongress: String
    val copyBellOn: String
    val copyBellOff: String
    fun copyBellOnToast(name: String): String
    fun copyBellOffToast(name: String): String
    val copyBellNotificationsOff: String
    val copyBellSenateNote: String
    val copyBellsTitle: String
    fun copyBellsSubtitle(n: Int): String
    val copyBellsIntro: String
    val copyBellsIntroDesktop: String
    val copyBellsEmpty: String
    val copyBellsAdd: String
    val copyBellHouse: String
    val copyBellSenate: String
    val copyBellGovernment: String
    val copyBellInvestor: String
    fun copyNotifWhiteHouse(n: Int): String
    // Import von Registerdaten (seit 5.9)
    val imTitel: String
    val imUntertitel: String
    fun imInsider(n: Int, land: String): String
    fun imLeer(n: Int, land: String): String
    val imPdf: String
    val imUnbekannt: String
    // Lobbyarbeit in Europa (seit 5.9)
    val lwTitel: String
    val lwHinweis: String
    fun lwRegister(l: String): String
    fun lwSpanne(von: String, bis: String): String
    fun lwAb(von: String): String
    fun lwJahr(von: String, bis: String): String
    fun lwFte(n: String): String
    fun lwTaetigkeiten(n: Int): String
    fun lwThemen(t: String): String
    val lwEintrag: String
    fun lwQuelle(laender: List<String>): String
    // Neue Meldungen bei der OGE (seit 5.9): Mitteilung, Hinweis auf der Seite der Person und Link
    fun copyNotifOge(n: Int): String
    fun copyOgeNeu(n: Int, datum: String): String
    val copyOgePdf: String
    val importPortfolioJson: String
    val exportPortfolioJson: String
    val importChoice: String
    val importMerge: String
    val importReplace: String
    val depotMerged: String
    fun copyPhotoCredit(autor: String, lizenz: String): String
    val copyPhotosCongress: String
    val copyPhotosCommons: String
    val copyInvestors: String
    val copyStrategies: String
    val copyFollowed: String
    val copyCongressIntro: String
    val copySearchMember: String
    val copyLoading: String
    val copyUnavailable: String
    val copyLatestTrades: String
    val copyMembers: String
    fun copyFilings(n: Int): String
    fun copyLast(date: String): String
    val copyCongressSource: String
    val copyFollowing: String
    val copyBuy: String
    val copySell: String
    val copyPartialSale: String
    val copyExchange: String
    val copyTypeOption: String
    val copyTypeGovBond: String
    val copyTypeCorpBond: String
    val copyTypeFund: String
    fun copyOver(v: String): String
    fun copyHouseOf(district: String): String
    val copyFilings12m: String
    val copyTrades: String
    val copyBuysEst: String
    val copySalesEst: String
    val copyReplicate: String
    val copyMostTraded: String
    fun copyBuySellEst(buy: String, sell: String, n: Int): String
    val copyAllTrades: String
    fun copyFiledOn(date: String): String
    fun copyPaper(n: Int, dates: String): String
    val copyNoFilings: String
    fun copyNote(name: String): String
    val copyFollow: String
    val copyUnfollow: String
    val copyInvestorsIntro: String
    fun copyInvestorLine(date: String, n: Int, value: String): String
    val copyInvestorsSource: String
    fun copyNewer13f(asOf: String, filed: String, shown: String): String
    val copyAsOf: String
    val copyFiled: String
    val copyValue: String
    val copyPositions: String
    val copyHoldings: String
    val copySoldOut: String
    val copyCall: String
    val copyPut: String
    val copyNew: String
    fun copyIncreased(pct: String): String
    fun copyReduced(pct: String): String
    val copySold: String
    val copyStrategiesIntro: String
    val copyDefensive: String
    val copyBalanced: String
    val copyOffensive: String
    val copyStrategiesNote: String
    val copyAllocation: String
    val copyHistory: String
    val copyNoHistory: String
    val copyPeriod: String
    fun copyRange(from: String, to: String): String
    val copyReturnPa: String
    val copyVolatility: String
    val copyMaxDrawdown: String
    val copyYears: String
    val copyAsPlans: String
    val copyFollowedIntro: String
    fun copyReplicateTitle(name: String): String
    val copyReplicateHint: String
    val copyAmount: String
    fun copyCash(v: String): String
    val copyCount: String
    val copyNothingToCopy: String
    fun copyFees(n: Int, v: String): String
    val copyWorking: String
    val copyReplicateNow: String
    fun copyPlansTitle(name: String): String
    val copyPlansHint: String
    val copyMonthly: String
    val copyPlanDay: String
    val copyPlansCreate: String
    fun copyDone(bought: Int, orders: Int): String
    fun copyPlansDone(n: Int): String
    fun copyNotifTitle(name: String): String
    fun copyNotifTrades(n: Int, list: String): String
    val copyNotifPaper: String
    fun copyNotif13f(asOf: String, filed: String): String

    // Anzeige in US-Dollar
    val switchCurrency: String
    val usdUnavailable: String
    fun usdRate(rate: String): String
    val regLandTitel: String
    val regLandText: String
    val regVorschlag: String
    val regAndere: String
    val regWaehrungTitel: String
    val regWaehrungText: String
    fun waehrungName(code: String): String
    val regZeitenTitel: String
    val regZeitenText: String
    fun regZeitenLand(land: String): String
    fun regZeitenLandHint(boerse: String, zeiten: String, deutschland: Boolean): String
    val regZeitenEigene: String
    val regZeitenEigeneHint: String
    val regZeitenImmer: String
    val regZeitenImmerHint: String
    fun regVonBis(von: String, bis: String): String
    val regionTitel: String
    val regLand: String
    val regWaehrung: String
    val regZeiten: String
    val regAnzeige: String
    val regAnzeigeHint: String
    fun regSteuerHinweis(land: String): String
    fun regWissenUs(n: Int): String
    fun fxRate(von: String, nach: String, kurs: String): String
    fun tradingHoursFor(aktien: String?, derivate: String?, wochenende: Boolean, ortszeit: Boolean): String
    val rrTitel: String
    val rrLaedt: String
    val rrKeine: String
    fun rrRendite(jahre: Int): String
    val rrSchwankung: String
    val rrMaxRueckgang: String
    val rrLaengster: String
    fun rrDauer(tage: Long): String
    val rrLaeuft: String
    fun rrBeta(index: String): String
    fun rrKorrelation(index: String): String
    fun rrHinweis(ab: String, index: String?, beta: String?): String
    val trendsTitel: String
    val trBranchen: String
    val trBranchenHinweis: String
    val trReddit: String
    val trRedditHinweis: String
    val trFilterDeutsch: String
    val sbTitel: String
    val sbAktien: String
    val sbKrypto: String
    val sbWsb: String
    val sbDeutsch: String
    val sbQuelleCnn: String
    val sbQuelleKrypto: String
    val sbQuelleWsb: String
    val sbQuelleDeutsch: String
    fun sbRedditWert(prozent: Int): String
    fun sbBeitragWert(prozent: Int): String
    fun sbStufe(stufe: Int): String
    fun sbTeil(schluessel: String): String
    val sbVortag: String
    val sbWoche: String
    val sbMonat: String
    val sbJahr: String
    val sbWsbSteigend: String
    val sbWsbFallend: String
    val sbHinweis: String
    val sbAaii: String
    fun sbKachel(schluessel: String): String
    fun sbAnbieter(schluessel: String): String
    fun sbSteigend(prozent: Int): String
    val sbQuelleAaii: String
    fun sbAaiiMehr(bullen: String, neutral: String, baeren: String, datum: String): String
    val fwTitel: String
    fun fwZusammen(rot: Int, gelb: Int, n: Int): String
    fun fwName(schluessel: String): String
    fun fwLage(schluessel: String, ampel: Int): String
    fun fwErklaerung(schluessel: String): String
    fun fwQuelle(schluessel: String): String
    val fwPunkte: String
    val fwHinweis: String
    fun redditMeldungTitel(name: String): String
    fun redditMeldungText(jetzt: Int, vorher: Int): String
    val trRedditDeHinweis: String
    val trRedditBeitraege: String
    fun trRedditAnzahl(n: Int): String
    val trRedditKeineBeitraege: String
    fun trRedditStimmung(steigend: Int, fallend: Int, offen: Int): String
    fun trRedditPunkte(punkte: Int, kommentare: Int): String
    val trRedditKeine: String
    val trLaedt: String
    val trKeineDaten: String
    val trNeu: String
    val trMehr: String
    fun trRang(rang: Int, pfeil: String, ticker: String): String
    fun trErwaehnungen(n: Int): String
    val trYahoo: String
    val trYahooHinweis: String
    val trFilterAktien: String
    val trFilterKrypto: String
    val trWiki: String
    fun trWikiProTag(n: String): String
    val trWikiVergleich: String
    fun trRedditZeit(tage: Int): String
    fun trSpitze(datum: String): String
    fun trSpitzeWoche(datum: String): String
    fun trSpitzeTag(datum: String, n: String): String
    fun trVerlaufSeit(datum: String?): String
    val trErwaehnungenKurz: String
    fun trWertHeute(rang: Int, pfeil: String, veraenderung: String?): String
    val trWertHeuteNicht: String
    fun trWertSummen(woche: String, monat: String, jahr: String): String
    val trWertYahoo: String
    val trWertYahooText: String
    fun trWertHinweis(seit: String?): String
    // Broker
    val brokerStorniert: String
    val brokerGeaendert: String
    val brSchutzTitel: String
    val brSchutzListe: List<Pair<String, String>>
    val ibZertTitel: String
    fun ibZertText(fingerabdruck: String): String
    val ibZertJa: String
    val ibNichtErreichbar: String
    val ibGatewayOeffnen: String
    val frTitel: String
    fun frText(name: String): String
    val frWarnung: String
    fun frVerfahren(key: String?): String
    val frAnfordern: String
    val frNeuAnfordern: String
    val frPush: String
    val frPushBestaetigt: String
    val frPhoto: String
    fun frSms(nummer: String): String
    val frTan: String
    val frEinloesen: String
    fun frZaehler(angefordert: Int, falsch: Int): String
    val frGrenze: String
    val frZweimalFalsch: String
    fun frNoetig(name: String): String
    fun brokerAendernNicht(name: String): String
    val orderGeaendert: String
    val orderAendern: String
    val orderAendernSpeichern: String
    val orderGueltigkeit: String
    fun orderAendernNurTeil(name: String, menge: Boolean, gueltigkeit: Boolean): String
    fun brokerFehlerText(code: String, meldung: String?): String
    val brokerNurUebung: String
    val brokerKeinTresor: String
    val brokerFelderFehlen: String
    fun brokerKeinKurs(waehrung: String): String
    fun brokerVerbundenToast(name: String): String
    val brokerGetrennt: String
    val brokerKeineDerivate: String
    fun brokerFuehrtNicht(name: String): String
    val brokerOrdertypFehlt: String
    val brokerKeineBruchstuecke: String
    val brokerGesendet: String
    val brTitel: String
    val brNurApp: String
    val brKontoArt: String
    val brLive: String
    val brPapier: String
    val brPapierHint: String
    val brLiveHint: String
    val brKonto: String
    val brKaufkraft: String
    val brZuletzt: String
    fun brVerbundenText(name: String): String
    val brAbgleichen: String
    val brTrennen: String
    val brEinleitung: String
    val brWaehlen: String
    fun brAngebot(art: de.troetenrepublik.app.market.broker.BrokerArt): String
    val brNichtMoeglich: String
    val brWeiter: String
    val brHinweiseTitel: String
    fun brHinweise(name: String): List<Pair<String, String>>
    val brGelesen: String
    val brEchtesGeld: String
    val brZustimmen: String
    val brSichernTitel: String
    fun brSichernText(name: String): String
    val brOhneSichern: String
    fun brSchluesselTitel(name: String): String
    fun brAnleitung(art: de.troetenrepublik.app.market.broker.BrokerArt, live: Boolean): String
    fun brSchluesselHolen(name: String): String
    fun brFeld(feld: String): String
    fun brSchluesselSchutz(schutz: String): String
    val brVerbinden: String
    val brTrennenTitel: String
    fun brTrennenText(name: String): String
    fun brProfil(name: String?, live: Boolean): String
    fun brBanner(name: String, live: Boolean): String
    fun trImportTitel(quelle: String): String
    val trImportLaedt: String
    fun trImportZeitraum(von: String, bis: String, zeilen: Int): String
    fun trImportOhneKurs(liste: String): String
    fun trImportOhneKauf(liste: String): String
    val trImportHinweis: String
    val brokerImportHinweis: String
    fun trImportiert(quelle: String, n: Int): String
    val importWahlSicherung: String
    val importWahlSicherungText: String
    val importWahlBroker: String
    fun importWahlBrokerText(namen: String): String
    val importWahlAnleitung: String
    fun importUnbekannt(namen: String): String
    val brokerVerbindet: String
    val brokerSchluesselFehlen: String

    // Prediction Markets
    val pmTile: String
    val pmTitle: String
    val pmMyBets: String
    val pmSearchHint: String
    val pmLoading: String
    val pmUnreachable: String
    val pmNoResults: String
    val pmMore: String
    fun pmDataNote(quelle: String): String
    fun pmVolume(v: String): String
    fun pmEnds(d: String): String
    val pmPoints: String
    val pmIn24h: String
    val pmYes: String
    val pmNo: String
    val pmBet: String
    val pmOutcomes: String
    val pmPlaceBet: String
    val pmAmount: String
    fun pmSideAt(seite: String, kurs: String): String
    fun pmChanceOf(seite: String): String
    val pmSharesLabel: String
    val pmPayoutIf: String
    val pmProfitIf: String
    val pmAvailable: String
    val pmPracticeNote: String
    fun pmBought(betrag: String, seite: String): String
    fun pmSold(betrag: String): String
    fun pmFehler(code: String): String
    val pmSell: String
    val pmSellTitle: String
    fun pmSellText(wert: String): String
    val pmOpen: String
    val pmClosed: String
    val pmOpenBets: String
    val pmStake: String
    val pmValue: String
    val pmResult: String
    val pmResultOpen: String
    val pmResultClosed: String
    val pmResultTotal: String
    fun pmClosedCount(n: Int): String
    fun pmStatus(status: String): String
    fun pmSharesAt(n: String, einstand: String, kurs: String): String
    val pmNoBets: String
    val pmYourBets: String
    val pmRules: String
    fun pmOpenAt(quelle: String): String
    val pmNoHistory: String
    fun pmRange(b: de.troetenrepublik.app.market.Prognose.Bereich): String
    fun pmResolvedCount(n: Int): String
    fun pmNotifTitle(tag: String): String
    fun pmNotifText(titel: String, betrag: String): String

    // Portfolioverlauf im Copytrading
    val pvTitle: String
    fun pvRange(jahre: Int): String
    val pvCompare: String
    val pvCompareTitle: String
    val pvCompareMax: String
    val pvGroupIndex: String
    val pvGroupMembers: String
    fun pvStats(gesamt: String, proJahr: String?, schwankung: String?, verlust: String): String
    val pvLoading: String
    val pvNone: String
    fun pvCurrencyNote(waehrung: String): String
    fun pvNoteInvestor(quartale: Int, abdeckung: Double = 1.0): String
    val pvMoreStats: String
    val pvNoInvestorHistory: String
    val pvLessStats: String
    fun pvRiskLine(sharpe: String?, sortino: String?, calmar: String?, ulcer: String, var95: String?, es95: String?, taeglich: Boolean, rueckgangTage: Long, laufend: Boolean): String
    fun pvBetaLine(haupt: String, beta: String, alpha: String, korrelation: String): String
    val ptClosedSales: String
    fun ptClosedSalesLine(anzahl: Int, gewinne: Int, quote: String, faktor: String?, mittelGewinn: String?, mittelVerlust: String?, erwartung: String): String
    fun pvNoteCongress(geschaefte: Int, ohneKurs: Int, ohneBestand: Int): String
    // Praesident: vorberechneter Verlauf, Zahlen schon formatiert, die letzten drei Teile null bei 0.
    fun pvNotePresident(geschaefte: String, jahr: String, einzel: String, eingerechnet: String, werte: String, anleihen: String, anteil: String,
        ohneKuerzel: String?, ohneKurs: String?, ohneBestand: String?, stichtag: String): String
    // Truth Social ueber das Archiv Trump's Truth
    val truthTitle: String
    val truthSubtitle: String
    val truthSource: String
    val truthRemoved: String
    val truthRepost: String
    val truthQuote: String
    fun truthImages(n: Int): String
    val truthVideo: String
    val truthNone: String
    val truthMore: String
    val pvNoteStrategy: String
    val pvNoteCompare: String
    val pvCongressNoBuys: String
    fun pvMarkBuys(datum: String, werte: String): String
    fun pvMarkSells(datum: String, werte: String): String
    fun pvMarkQBuys(quartal: String, werte: String): String
    fun pvMarkQSells(quartal: String, werte: String): String
    val pvMoveNew: String
    val pvMoveOut: String
    fun pvQuarter(q: Int, jahr: Int): String
    val pvMoves: String
    fun pvQuarterHead(quartal: String, gemeldet: String): String
    fun pvAllQuarters(n: Int): String
    val pvFewerQuarters: String
    val pvCompareStrategies: String
    val pvCompareInvestors: String
    val pvCompareScreen: String
    val pvCompareIntro: String
    val pvDragHint: String

    // Depotverlauf
    val dvTitle: String
    val dvSubtitle: String
    val dvNone: String
    val dvMoves: String
    fun dvRaster(name: String): String
    val dvRises: String
    val dvDrops: String
    val dvNoMoves: String
    fun dvWeek(datum: String): String
    fun dvBy(werte: String): String
    val dvContrib: String
    val dvHeld: String
    val dvSold: String
    val dvEstimated: String
    val dvTrades: String
    fun dvQtyAt(qty: String, price: String): String
    fun dvAll(n: Int): String
    val dvFewer: String
    // Beim Ziehen durch den Depotverlauf, Geschaefte des Tages nur mit Namen
    fun dvTagKauf(werte: String): String
    fun dvTagVerkauf(werte: String): String
    fun dvUndWeitere(n: Int): String
    val dvDragHint: String
    val dvNote: String
    val languageSystem: String
    // Zugaenge und Dienste mit eigenem Schluessel
    val zgTitle: String
    val zgServices: String
    val importFileSection: String
    val pvDepotSub: String
    val newsImportantTitle: String
    val newsImportantExplain: String
    val ptWholeHistory: String
    val ptIndexTitle: String
    val ptWholeHistoryNote: String
    val zgIntro: String
    fun zgPurpose(dienst: String): String
    fun zgTerms(dienst: String): String
    val zgKey: String
    val zgShow: String
    val zgHide: String
    fun zgUsage(dienst: String, verbraucht: String, erlaubt: String): String
    val zgLimit: String
    val zgKeyRejected: String
    fun zgProviderSays(meldung: String): String
    fun zgGetKey(name: String): String
    fun zgProfileSubtitle(n: Int): String
    val zgRedditId: String
    val zgRedditSecret: String
    val zgRedditHolen: String
    fun newsKeyMissing(name: String): String
    val newsKeyEnter: String
    val newsGuardianNote: String
    val newsGuardianNoText: String
    val pmTranslateHint: String
    val languageSystemHint: String
    // Verlauf unter Meine Wetten
    val pmHistory: String
    val pmHistoryLoading: String
    val pmHistoryNone: String
    val pmHistoryNote: String
    val pmHistoryHint: String
    fun pmHistoryRange(tage: Int): String
    fun pmHistoryMark(tag: String, datum: String, betrag: String, seite: String, titel: String): String
    fun pvSince(monat: String): String
    val indButton: String
    fun indName(art: String): String
    fun indLage(art: String, lage: String): String
    fun indTendenz(tendenz: String): String
    fun indUeberblick(positiv: Int, negativ: Int, neutral: Int, gemischt: Int): String
    fun indGrund(art: String, lage: String, prozent: String, zahl: String, kreuz: String?, kreuzAuf: Boolean, eng: Boolean): String
    fun indErklaerung(art: String): String
    val indFuss: String
    val copyPolitics: String
    fun truthRelevant(themen: List<String>): String
    fun truthThema(k: String): String
    val newsBrowserOnly: String
    val copyPositionsTitle: String
    fun copyPositionShare(anteil: String): String
    val copyPositionsNote: String
    fun copyPositionsAll(n: Int): String
    val copyPositionsFewer: String
    val copyPartShares: String
    fun copyPartOption(call: Boolean, basispreis: String, verfall: String, verfallen: Boolean): String
    fun copyPartShare(anteil: String): String
    fun copyPartsToggle(offen: Boolean): String
    val copyToolsTitle: String
    val copyToolsRow: String
    fun copyToolsFor(name: String): String
    fun pvMetric(k: String): String
    fun pvMetricExplain(k: String): String
    val pvMetricHint: String
    fun awSectorDelta(pp: String): String
    fun copyLogoCredit(domain: String): String
    val copyNameUnreadable: String
    fun webBausteinAngebot(mb: Int): String
    val webBausteinLaden: String
    fun webBausteinLaedt(prozent: String): String
    val webBausteinFehler: String
    val webBausteinBestaetigen: String
    val webBausteinFenster: String
    val newsTrumpHinweis: String
    val webBausteinNurAnfang: String
    val dfUniversum: String
    fun dfUniversumName(k: String): String
    val dfAnzahl: String
    val dfMindest: String
    val dfFaktoren: String
    val dfGewichtHinweis: String
    fun dfFaktorErklaerung(k: String): String?
    fun dfFaktor(k: String): String
    val dfStart: String
    val dfLaedt: String
    val dfKeine: String
    val dfSchnitt: String
    val dfDepotVola: String
    fun dfMarktVola(name: String): String
    val dfSpalteScore: String
    val dfSpalteName: String
    val dfSpalteVola: String
    val dfSpalteJahr: String
    val dfNachbilden: String
    val dfHinweis: String
    val opTitel: String
    val opPcr: String
    fun opBewegung(tag: String): String
    fun opMaxPain(tag: String): String
    val opIv: String
    fun opHv(wert: String): String
    val opAuffaellig: String
    fun opAuffaelligZeile(art: String, strike: String, tag: String, umsatz: String, offen: String, praemie: String, seite: String): String
    val opQuelle: String
    fun opErklaerung(k: String): String
    val kaTitel: String
    val kaNav: String
    val kaText: String
    val kaLaedt: String
    val kaVergleich: String
    val kaFenster: String
    fun kaFensterKurz(tage: Int): String
    fun kaMonate(n: Int): String
    val kaFensterEigen: String
    val kaFensterMonate: String
    val kaHinweisVerlauf: String
    val kaJetzt: String
    val kaQuelle: String
    val rgTitel: String
    val rgNav: String
    val rgText: String
    fun rgOrdnung(k: String): String
    fun rgZeile(punkte: String, quote: String, kaeufe: Int): String
    fun rgVermoegen(s: String): String
    fun vmSpanne(von: String, bis: String): String
    fun vmTitel(jahr: Int): String
    fun vmTeile(vermoegen: String, schulden: String, werte: Int): String
    val vmHinweis: String
    val bhTitel: String
    val bhLobbying: String
    fun bhJeQuartal(betrag: String): String
    fun bhQuartal(q: Int, j: Int, betrag: String, firmen: Int): String
    fun bhThemen(liste: String): String
    fun bhLobbyingQuelle(stand: String): String
    val bhGesetze: String
    fun bhGesetzeWert(gesetze: Int, personen: Int): String
    fun bhGesetz(g: String, n: Int): String
    val bhGehandelt: String
    fun bhHaendler(name: String, kaeufe: Int, verkaeufe: Int): String
    val bhGesetzeHinweis: String
    val bhAuftraege: String
    fun bhFiskaljahr(betrag: String, jahr: Int): String
    fun bhFiskaljahrZeile(jahr: Int, betrag: String, laufend: Boolean): String
    val bhAuftraegeQuelle: String
    val bhSpenden: String
    fun bhSpendenWert(betrag: String, zyklus: Int): String
    val bhLaedt: String
    val bhKeinPac: String
    fun bhPacSummen(zyklus: Int, einnahmen: String, kandidaten: String): String
    fun bhSpendenQuelle(demo: Boolean): String
    val bhLeer: String
    fun bhLeerTage(anteil: String, tage: Int): String
    fun bhLeerBestand(stichtag: String, aktien: String, aenderung: String?, tage: String?): String
    val bhLeerErklaerung: String
    val bhEntlassungen: String
    fun bhEntlassungenWert(meldungen: Int, betroffene: String): String
    fun bhBetroffene(n: Int): String
    val bhEntlassungenQuelle: String
    val bhPatente: String
    fun bhPatenteWert(n: Int): String
    val bhPatenteQuelle: String
    fun bhThema(code: String): String
    val pmGross: String
    val pmGrossText: String
    fun pmGrossZeit(key: String): String
    val pmGrossAlle: String
    val pmEchtTitel: String
    val pmEchtKeine: String
    val pmEchtHinweis: String
    val pmUebungTitel: String
    fun pmEchtZeile(ausgang: String, anteile: String, einstand: String, kurs: String): String
    val pmEchtEinloesbar: String
    val zgPolyZweck: String
    val zgPolyAdresse: String
    val zgPolyGeheim: String
    val zgPolyUngueltig: String
    val pmGrossKeine: String
    fun pmGrossZeile(ausgang: String, kauf: Boolean, preis: String, wann: String): String
    val bhSpendenLimit: String
}
