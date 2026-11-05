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
    val widgetsAkku: String
    val widgetsAkkuOeffnen: String
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
    fun chartAllesLoeschen(name: String): String
    val chartKeineDaten: String
    val heatmap: String
    val heatmapDepot: String
    val heatmapMarkt: String
    val heatmapBranchen: String
    val heatmapRegionen: String
    fun heatmapZaehler(steigen: Int, fallen: Int): String
    val heatmapLeer: String
    val heatmapOhneBranche: String
    val zeitraumEigener: String
    val zeitraumTitel: String
    fun metricLabel(m: Metric): String
    fun metricShort(m: Metric): String
    fun watchMetricLabel(m: WatchMetric): String
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
    val noWatchlist: String
    val knockedOut: String
    val available: String
    fun reservedForOrders(amount: String): String
    val deposit: String
    val withdraw: String
    val interest: String
    val transactions: String
    val noTransactions: String
    val incoming: String
    val outgoing: String
    val since: String

    // Statistik
    val year: String
    val allTime: String
    val categories: String
    fun statNode(id: String): String                 // id wie INCOME oder EXPENSES
    val netCashflow: String
    val investRate: String
    val noStatistics: String
    fun bookingsCount(n: Int): String
    val averagePerMonth: String

    // Watchlist
    fun entriesCount(n: Int): String
    val sortByName: String
    val sortByChange: String
    val sortByPrice: String
    val add: String
    fun addedOn(date: String): String
    val removeFromWatchlist: String
    val addToWatchlist: String

    // Suche
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
    val rohstoffTitel: String
    val etfTitel: String
    val etfTop: String
    val etfBranchen: String
    fun etfKlassen(aktien: String, anleihen: String, bar: String): String
    val etfHinweis: String
    val etfDepotTitel: String
    fun etfDepotZeile(direkt: String, ueber: String, etfs: Int): String
    val etfDepotHinweis: String
    fun rohstoffAbschnitte(schluessel: String, termin: Boolean): List<Pair<String, String>>
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
    fun calExpectedAmount(amount: String): String
    val calLegendReceived: String
    val calLegendExpected: String
    val calClosedTrades: String
    val calDayResult: String
    val calFees: String
    val calAfterCosts: String
    val calPositions: String
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
    fun ordersOpenHint(n: Int): String
    val noOpenOrders: String
    val doneOrders: String
    val cancelOrder: String
    fun validUntil(ts: String): String
    fun orderStatus(s: OrderStatus): String
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
    fun mp200(wert: String): String
    fun mpDetail(art: String, vomTief: String, golden: Boolean?, kreuzSeit: String?): String
    val mpHinweis: String
    // Positionierung an den Terminmaerkten (CFTC, seit 5.8)
    val cotTitel: String
    fun cotMarkt(schluessel: String): String
    fun cotQuelle(stand: String): String
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
    val bbDafuer: String
    val bbDagegen: String
    val bbHinweis: String
    val bbWaage: String
    fun bbStufe(stufe: Int): String
    fun bbWaageSatz(stufe: Int): String
    val bbWaageHinweis: String
    fun bbWarum(schluessel: String): String
    fun bbAlle(n: Int): String
    fun bbThema(thema: String): String
    fun bbKennzahl(kennzahl: String, w: List<String>): String
    // US-Insider (SEC Form 4, seit 5.6)
    val vdKaeufe1: String
    val vdVerkaeufe1: String
    val usHinweis: String
    val usOhneKontakt: String
    fun usQuelle(stand: String): String
    fun usRolle(r: String): String
    fun usBestandDanach(stueck: String, aenderung: String?): String
    val usBestaendeTitel: String
    fun usStueck(stueck: String): String
    fun usBestaendeSumme(wert: String, personen: Int): String
    val usBestaendeHinweis: String
    val zgSecTitel: String
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
    val vdMeistgekauft: String
    val vdMeistverkauft: String
    val vdNeueste: String
    val vdKaeufe: String
    val vdVerkaeufe: String
    val vdLeer: String
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
    val activity: String
    val documents: String
    val documentsSubtitle: String
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
    val notifications: String
    val notificationsHint: String
    val notificationsHintDesktop: String
    val refreshEvery: String
    fun everySeconds(n: Int): String
    val source: String
    val statusLimited: String
    val statusConnected: String
    val refreshNow: String
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
    val knowledgeOeffnenIn: String
    val knowledgeImBrowser: String
    val knowledgeInApp: String
    val knowledgeInAppOffen: String
    val knowledgeInAppNochmal: String
    fun knowledgeBausteinAngebot(mb: Int): String

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
    val privateGains: String
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
    val filterTrade: String
    val filterIncome: String
    val noActivity: String
    fun txLabel(t: TxType): String
    val toSecurity: String
    val showDocument: String
    fun txTitle(tag: String, storedTitle: String): String
    fun txSubtitle(tag: String, via: String, qty: String, price: String, realized: String, extra: String, stored: String): String

    // Dokumente
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
    val docSymbol: String
    val docExecutionPrice: String
    val docMarketValue: String
    val docRealized: String
    val docTax: String
    val docVenueValue: String
    val docDebit: String
    val docCredit: String
    val docDividendPerShare: String
    val docGross: String
    fun docUntil(date: String): String
    val docInterestGross: String
    val docRefundPerPiece: String
    val docRecipient: String
    val docOpening: String
    val docClosing: String
    val docTaxYear: String
    val docRealizedGains: String
    val docDividendsGross: String
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
    fun toolYears(n: Int): String
    val toolMax: String
    val toolMonthly: String
    val toolLoading: String
    val toolNoData: String
    fun toolBoughtOn(date: String, price: String): String
    val toolPerYear: String
    val toolWorldEtfShort: String
    val toolNoCosts: String
    val toolRiskWorldNote: String
    val toolWorldNote: String
    val toolInvested: String
    val toolExecutions: String
    val toolAverageCost: String
    val toolPerYearMoneyWeighted: String
    val toolVolatility: String
    val toolDrawdownPeriod: String
    fun toolDrawdownRange(from: String, to: String): String
    val toolRecovered: String
    val toolNotRecovered: String
    val toolWorstDay: String
    val toolBestDay: String
    val toolUpDays: String
    val toolRiskNote: String
    val toolDividendGrowth: String
    val toolShares: String
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
    val toolSellNote: String

    // Position importieren
    val importPosition: String
    val importIntro: String
    val importSearchHint: String
    val importDate: String
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
    val keyFigures: String
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
    val ptMonthlySaving: String
    val ptHorizon: String
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
    fun notifDividendText(name: String, amount: String): String
    val notifInterest: String
    fun notifInterestText(amount: String): String
    fun notifKnockoutText(name: String): String
    val notifKnockoutSettled: String
    fun notifKnockoutSettledText(name: String, amount: String): String
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
    val channelCash: String
    val channelUpdates: String
    val profilEinstellungen: String
    val profilSicherheit: String
    val profilDaten: String
    val profilErweitert: String
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
    val rbStaerkste: String
    val rbSchwaechste: String
    val rbGewinner: String
    val rbVerlierer: String
    val rbMerkliste: String
    val rbAufgeklappt: String
    val rbZugeklappt: String
    fun newsNotifMany(n: Int): String
    fun newsNotifyOption(stufe: String): String
    fun newsNotifyOptionHint(stufe: String): String
    val newsNotifyOffGlobal: String
    val newsNotifyHint: String
    val awTitle: String
    val awLoading: String
    val awNone: String
    fun awHorizon(tage: Int): String
    val awFrequency: String
    val awPerMonth: String
    val awBusiest: String
    val awTypical: String
    val awOverMillion: String
    val awBuyShare: String
    val awUnusual: String
    val awBeforeMoves: String
    fun dcSummary(gut: Int, von: Int): String
    fun dcTitle(regel: String): String
    fun dcValue(regel: String, anteil: String, name: String, anzahl: Int): String
    fun dcExplain(regel: String): String
    val terTeurerTitel: String
    val etfTdHinweis: String
    val etfTdLink: String
    fun terTeurerText(name: String, alt: String, neu: String): String
    val dcNote: String
    fun copyPaperRead(meldungen: Int, unlesbar: Int): String
    val derivInvestorIntro: String
    val derivStructured: String
    val derivOther: String
    // Basispreis in US-Dollar; preis nur die Zahl.
    fun derivStrike(preis: String): String
    fun derivExpiry(datum: String): String
    fun derivContracts(anzahl: String): String
    val ctcDays: String
    fun ctcApprox(betrag: String): String
    fun ctcCountApprox(anzahl: String, betrag: String): String
    fun ctcReported(datum: String): String
    val ctcEmpty: String
    val ctcHint: String
    val updNotifTitle: String
    fun updNotifText(fassung: String): String
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
    val ppfIzfZeitraum: String
    val ppfIzfHinweis: String
    val ppfAbsolut: String
    val ppfMaxRueckgang: String
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
    // Je Seite Titel und Text, in der Reihenfolge der Bilder.
    val guidePages: List<Pair<String, String>>
    // Windows und Linux: Bedienung mit Maus und Tastatur statt Wischen.
    val guidePagesDesktop: List<Pair<String, String>>

    // Entdecken (Suche)
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
    fun newsCategory(k: de.troetenrepublik.app.market.Rss.Kategorie): String
    fun newsAnbieter(a: de.troetenrepublik.app.market.Rss.Anbieter): String
    val newsLoading: String
    val newsEmpty: String
    val newsEmptySearch: String
    val newsYesterday: String
    val newsSearchHint: String
    val newsSearch: String
    val newsSources: String
    val newsSourcesHint: String
    val newsOwnFeeds: String
    val newsAddFeed: String
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
    val copyTitle: String
    val copyCongress: String
    val copySenate: String
    val copyGovernment: String
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
    fun copyRange(from: String, to: String): String
    val copyMaxDrawdown: String
    val copyYears: String
    val copyAsPlans: String
    val copyFollowedIntro: String
    fun copyReplicateTitle(name: String): String
    val copyReplicateHint: String
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
    val rrLaengster: String
    fun rrDauer(tage: Long): String
    val rrLaeuft: String
    fun rrBeta(index: String): String
    fun rrKorrelation(index: String): String
    fun rrHinweis(ab: String, index: String?, beta: String?): String
    val trendsTitel: String
    val trBranchenHinweis: String
    val trReddit: String
    val trRedditHinweis: String
    val trFilterDeutsch: String
    val sbTitel: String
    val sbKrypto: String
    val sbCnn: String
    val sbQuelleCnn: String
    val sbAaii: String
    val sbQuelleAaii: String
    fun sbAaiiMehr(bullen: String, neutral: String, baeren: String, datum: String): String
    val sbPutCall: String
    fun sbPutCallMehr(wert: String): String
    fun wqPcZeile(gesamt: String?, index: String?, tag: String): String
    val wqPcQuelle: String
    val sbWsb: String
    val sbQuelleKrypto: String
    val sbQuelleWsb: String
    fun sbRedditWert(prozent: Int): String
    fun sbStufe(stufe: Int): String
    fun sbTeil(schluessel: String): String
    val sbVortag: String
    val sbWoche: String
    val sbMonat: String
    val sbWsbSteigend: String
    val sbWsbFallend: String
    val sbHinweis: String
    fun sbKachel(schluessel: String): String
    fun sbAnbieter(schluessel: String): String
    fun sbSteigend(prozent: Int): String
    val fwTitel: String
    fun fwZusammen(rot: Int, gelb: Int, n: Int): String
    fun fwName(schluessel: String): String
    fun fwLage(schluessel: String, ampel: Int): String
    fun fwErklaerung(schluessel: String): String
    fun fwQuelle(schluessel: String): String
    fun blsHinweis(datum: String): String
    val b13107Titel: String
    val b13107Text: String
    val b13107Ok: String
    val b13107Zurueck: String
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
    val trMehr: String
    fun trRang(rang: Int, pfeil: String, ticker: String): String
    fun trErwaehnungen(n: Int): String
    val trYahoo: String
    val trYahooHinweis: String
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
    val trWertYahooText: String
    // Orders und Import
    val orderGeaendert: String
    val orderAendern: String
    val orderAendernSpeichern: String
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

    // Prediction Markets
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
    val pmIn24h: String
    val pmYes: String
    val pmBet: String
    val pmOutcomes: String
    val pmPlaceBet: String
    val pmAmount: String
    fun pmSideAt(seite: String, kurs: String): String
    fun pmChanceOf(seite: String): String
    val pmSharesLabel: String
    val pmPayoutIf: String
    val pmProfitIf: String
    val pmPracticeNote: String
    fun pmBought(betrag: String, seite: String): String
    fun pmSold(betrag: String): String
    fun pmFehler(code: String): String
    val pmSellTitle: String
    fun pmSellText(erloes: String, geldkurs: String, mitte: String): String
    val pmSellLaedt: String
    fun pmSellDuenn(beiEintreten: String): String
    val pmClosed: String
    val pmOpenBets: String
    val pmValue: String
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
    val pvGroupIndex: String
    val pvGroupMembers: String
    val pvLoading: String
    val pvNone: String
    fun pvCurrencyNote(waehrung: String): String
    fun pvNoteInvestor(quartale: Int, abdeckung: Double = 1.0): String
    val pvMoreStats: String
    val pvNoInvestorHistory: String
    val pvLessStats: String
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
    val pvMoveOut: String
    fun pvQuarter(q: Int, jahr: Int): String
    val pvMoves: String
    fun pvQuarterHead(quartal: String, gemeldet: String): String
    fun pvAllQuarters(n: Int): String
    val pvFewerQuarters: String
    val pvCompareStrategies: String
    val pvCompareInvestors: String
    val pvCompareScreen: String
    val pvDragHint: String

    // Depotverlauf
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
    val importFileSection: String
    val pvDepotSub: String
    val newsImportantTitle: String
    val newsImportantExplain: String
    val newsWichtigEnglisch: String
    val newsWichtigEnglischHinweis: String
    val ptIndexTitle: String
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
    val newsOhneLesemodus: String
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
    val copyToolsRow: String
    fun copyToolsFor(name: String): String
    fun pvMetric(k: String): String
    fun pvMetricExplain(k: String): String
    fun copyLogoCredit(domain: String): String
    val copyNameUnreadable: String
    fun webBausteinAngebot(mb: Int): String
    val webBausteinLaden: String
    fun webBausteinLaedt(prozent: String): String
    val webBausteinFehler: String
    val webBausteinImFenster: String
    val webBausteinLeer: String
    val webBausteinFenster: String
    val newsTrumpHinweis: String
    val webBausteinNurAnfang: String
    fun dfUniversumName(k: String): String
    val opTitel: String
    val opPcr: String
    fun opBewegung(tag: String): String
    fun opMaxPain(tag: String): String
    fun opHv(wert: String): String
    val opAuffaellig: String
    fun opAuffaelligZeile(art: String, strike: String, tag: String, umsatz: String, offen: String, praemie: String, seite: String): String
    val opQuelle: String
    fun opErklaerung(k: String): String
    val opStillhalter: String
    fun opTage(tage: Int): String
    val opStillCalls: String
    val opStillPuts: String
    val opBasis: String
    val opPraemie: String
    val opRenditeJahr: String
    val opAusuebung: String
    // call: Basis, Praemie, Rendite, Rendite bei Ausuebung, Schwelle; put: Basis, Praemie, Rendite, Einstand
    fun opStillBeispiel(call: List<String>?, put: List<String>?): String
    val kaTitel: String
    val kaNav: String
    val kaLaedt: String
    val kaVergleich: String
    val kaFenster: String
    fun kaFensterKurz(tage: Int): String
    fun kaMonate(n: Int): String
    val kaFensterEigen: String
    val kaFensterMonate: String
    val kaHinweisVerlauf: String
    val kaJetzt: String
    val rgTitel: String
    val rgNav: String
    fun rgOrdnung(k: String): String
    fun rgVermoegen(s: String): String
    fun vmSpanne(von: String, bis: String): String
    fun vmTitel(jahr: Int): String
    val vmHinweis: String
    val bhTitel: String
    val bhLobbying: String
    fun bhJeQuartal(betrag: String): String
    fun bhQuartal(q: Int, j: Int, betrag: String, firmen: Int): String
    fun bhThemen(liste: String): String
    fun bhLobbyingQuelle(stand: String): String
    fun bhGesetzeWert(gesetze: Int, personen: Int): String
    fun bhGesetz(g: String, n: Int): String
    val bhAuftraege: String
    fun bhFiskaljahr(betrag: String, jahr: Int): String
    fun bhFiskaljahrZeile(jahr: Int, betrag: String, laufend: Boolean): String
    val bhAuftraegeQuelle: String
    val bhSpenden: String
    fun bhSpendenWert(betrag: String, zyklus: Int): String
    val bhKeinPac: String
    fun bhPacSummen(zyklus: Int, einnahmen: String, kandidaten: String): String
    fun bhSpendenQuelle(demo: Boolean): String
    val bhLeer: String
    fun bhLeerTage(anteil: String, tage: Int): String
    fun bhLeerBestand(stichtag: String, aktien: String, aenderung: String?, tage: String?): String
    val bhLeerErklaerung: String
    val bhFinraOhne: String
    val bhFinraLeerLink: String
    val bhFinraAtsLink: String
    val dpTitel: String
    fun dpWoche(woche: String, aktien: String, anteil: String?, schnitt: String?, wochen: Int): String
    fun dpPlaetze(plaetze: List<Pair<String, String>>): String
    fun dpVerzug(stufe: String?): String
    val dpErklaerung: String
    val zgFinraSecret: String
    val zgFinraHolen: String
    fun bhLeerVerlauf(seit: String, tief: String, hoch: String): String
    val bhEntlassungen: String
    fun bhEntlassungenWert(meldungen: Int, betroffene: String): String
    fun bhBetroffene(n: Int): String
    val bhEntlassungenQuelle: String
    val bhPatente: String
    fun bhPatenteWert(n: Int): String
    val bhPatenteQuelle: String
    fun bhThema(code: String): String
    val pmGross: String
    val pmEchtTitel: String
    val pmEchtKeine: String
    val pmEchtHinweis: String
    val pmUebungTitel: String
    fun pmEchtZeile(ausgang: String, anteile: String, einstand: String, kurs: String): String
    val pmEchtEinloesbar: String
    val zgPolyAdresse: String
    val zgPolyGeheim: String
    val zgPolyUngueltig: String
    val pmGrossKeine: String
    fun pmGrossZeile(ausgang: String, kauf: Boolean, preis: String, wann: String): String
    val bhSpendenLimit: String

    // 5.91 Kurschart und Marktkurven
    val kcMarken: String
    val kcSpanne: String
    fun kcLinie(art: String): String
    fun kcMarke(art: String, stueck: String, betrag: String, kurs: String): String
    fun kcSpanneQuelle(implizit: Boolean, sigma: String): String
    val mkTitel: String
    val mkUntertitel: String
    val mkZins: String
    val mkUsa: String
    val mkEuro: String
    val mkDeutschland: String
    val mkQuelleDeutschland: String
    fun mkStichtag(k: Int, datum: String): String
    fun mkLaufzeit(tage: Int): String
    fun mkForm(form: String): String
    fun mkInvertiert(kurz: String, zins: String, zehn: String): String
    fun mkAbstand(kurz: String, wert: String): String
    val mkZinsMehr: String
    val mkQuelleUsa: String
    val mkQuelleEuro: String
    val mkVix: String
    fun mkVixForm(form: String): String
    val mkVixMehr: String
    val mkVixQuelle: String
    val mkKeineDaten: String

    // 5.91 Strategie-Labor, Backtest-Lotterie, Anlage-These, eigene Regeln, monatliche Einzahlung
    val slTitel: String
    val slUntertitel: String
    val slErklaerung: String
    val slZuWenig: String
    fun slKosten(spread: String): String
    val slRegeln: String
    fun slRegel(art: String, a: Int, b: Int, n: Int): String
    val slDieRegel: String
    val slHalten: String
    fun slVergleich(regel: String, halten: String): String
    val slSpalten: String
    val slEndwert: String
    val slTreffer: String
    val slImMarkt: String
    fun slLern(datum: String): String
    fun slPruef(datum: String): String
    val slLernText: String
    val slLotterie: String
    val slLotterieText: String
    fun slFamilie(key: String): String
    val slLotterieLaeuft: String
    val slVarianten: String
    val slBeste: String
    val slBesteLern: String
    val slBestePruef: String
    val slDsr: String
    fun slDsrUrteil(echt: Boolean): String
    val slDsrText: String
    val thTitel: String
    val thHinzufuegen: String
    val thGrund: String
    val thAusstieg: String
    val thObergrenze: String
    val thPruefen: String
    val thKeinTermin: String
    fun thMonate(m: Int): String
    fun thPruefAm(datum: String): String
    val thRegelWarnung: String
    fun thVerstoss(art: String, ist: String, grenze: String, name: String): String
    fun thSeit(datum: String): String
    val thAusstiegLabel: String
    fun thObergrenzeWert(grenze: String, ist: String): String
    val thFrage: String
    val thVerkaufenErwaegen: String
    val thEntfernen: String
    fun thMitteilung(name: String): String
    val thBestaetigtToast: String
    val thRegelnTitel: String
    val thRegelnText: String
    val thRegelPosition: String
    val thRegelHebel: String
    val thRegelBargeld: String
    val thAus: String
    val thAn: String
    val thRegelnOk: String
    val thAuswertung: String
    val thMitThese: String
    val thBestaetigt: String
    val thErwogen: String
    val thRegelbrueche: String
    val thOffeneTermine: String
    val thAuswertungText: String
    val ezTitel: String
    val ezUntertitel: String
    fun ezAktiv(betrag: String, tag: String): String
    fun ezTagWert(tag: Int): String
    val ezText: String
    val ezTag: String
    fun ezNaechste(datum: String): String
    val ezBisher: String
    fun ezBisherWert(n: Int, summe: String): String
    val ezLoeschen: String
    val ezGespeichert: String
    val ezMitteilung: String
    fun ezMitteilungText(betrag: String): String

    // 5.91 Risikobeitrag, Bilanzqualitaet, Kaufkraft, Kennzahlenvergleich, Reverse DCF
    val rskTitel: String
    fun rskDepotSchwankung(pct: String): String
    val rskGewicht: String
    val rskRisiko: String
    fun rskZeile(gewicht: String, risiko: String): String
    fun rskSatz(name: String, gewicht: String, risiko: String): String
    val rskAusgeglichen: String
    val rskOhneDaten: String
    fun rskNichtEingerechnet(n: Int): String
    val rskHinweis: String
    val rskParitaetTitel: String
    val rskParitaetText: String
    val rskUebernehmen: String
    val rskWarnung: String
    val rskLaden: String
    val qsTitel: String
    val qsPiotroski: String
    fun qsScore(score: Int, von: Int): String
    fun qsStufe(stufe: String): String
    fun qsJahre(jahr: String, vorjahr: String): String
    fun qsPunkt(punkt: String): String
    fun qsBegruendung(punkt: String, jetzt: String, vorher: String?): String
    val qsNichtPruefbar: String
    val qsAltman: String
    fun qsZone(zone: String): String
    val qsZonen: String
    val qsZonenAndere: String
    fun qsAltmanJahrAndere(jahr: String): String
    fun qsBaustein(k: String): String
    fun qsAltmanJahr(jahr: String): String
    val qsHinweis: String
    val qsLaden: String
    val qsKeineDaten: String
    val kkReal: String
    fun kkSatz(real: String, nominal: String, inflation: String): String
    fun kkHinweis(usd: Boolean): String
    val kkLaden: String
    val kkKeineDaten: String
    val kvTitel: String
    val kvEinstieg: String
    val kvEinstiegSub: String
    val kvAehnlich: String
    val kvBranche: String
    val kvSuche: String
    val kvSucheHinweis: String
    val kvMax: String
    val kvMin: String
    fun kvZeile(k: String): String
    val kvBester: String
    val kvHinweis: String
    val kvLaden: String
    val rdTitel: String
    fun rdSatz(x: String): String
    fun rdSatzRand(x: String, mehr: Boolean): String
    val rdRendite: String
    val rdZielKgv: String
    fun rdBranche(kgv: String): String
    fun rdKgvJetzt(kgv: String): String
    fun rdBisher(x: String, jahre: Int): String
    fun rdAnalysten(x: String): String
    val rdHinweis: String

    // 5.91 Aufraeumen
    val auStornierenTitel: String
    fun auStornierenText(name: String): String
    val auBehalten: String
    val auIndikatorenAbMonat: String

    // 5.91 Insider weltweit und US-Regierung
    val ogeTitel: String
    val ogeLeer: String
    fun ogeMeldungen(n: Int): String
    val ogeHinweis: String
    val ogeQuelle: String
    fun ogeUngelesen(n: Int): String
    fun ogePdf(datum: String): String
    fun ogeGehandelt(n: Int): String
    val f144Titel: String
    val f144Hinweis: String
    fun f144Stueck(n: String): String
    fun f144Geplant(tag: String): String
    val f13Titel: String
    val f13Hinweis: String
    val f13Aktiv: String
    val f13Passiv: String
    val f13Quelle: String
    val omTitel: String
    val omHinweis: String
    fun omAnteil(p: String): String
    fun weltQuelle(land: String, stand: String): String
    val saTitel: String
    val saUntertitel: String
    val saEinleitung: String
    fun saName(id: String): String
    fun saInhalt(id: String): String
    fun saSchritte(id: String): List<String>
    val saOeffnen: String

    // 5.91 Profil
    val erwUntertitel: String
    val erwHandel: String
    val erwKurse: String
    val erwZinsAuto: String
    val erwRegister: String
    val erwPp: String
    val planVorbelegt: String

    // 5.91 Wertpapierseite
    val bwAlleKennzahlen: String
    val bwTer: String
    val bwPreisalarm: String
    fun bwPreisalarmZahl(n: Int): String
    val bwOffeneOrders: String
    val bwFuerUndWider: String
    val bwHebelprodukte: String
    val bwHintergrund: String
    fun bwHintergrundUnter(themen: List<String>): String
    fun bwThema(key: String): String
    val bwRrUnter: String
    val bwGesetze: String
    val bwGesetzeHinweis: String

    // 5.91 Startseite
    val maerkteTitel: String
    val maerkteBewegungen: String
    val maerkteSucheZeile: String
    val handeln: String
    val zeitraumMenue: String

    // 5.92 Datenquellen
    val dqTitel: String
    val dqUntertitel: String
    val dqEinleitung: String
    fun dqName(id: String): String
    val dqQuellen: String
    val dqAus: String
    val dqNie: String
    fun dqAntwortet(seit: String): String
    fun dqUmweg(seit: String): String
    fun dqFehler(art: String, letzterErfolg: String?): String
    fun dqArt(art: de.troetenrepublik.app.engine.Quellenbuendel.Fehler?, code: Int): String
    val dqStandKopf: String
    val dqStandLabel: String
    fun dqStand(version: Long, geladen: Boolean): String
    val dqGesucht: String
    val dqNochNicht: String
    fun dqErgebnis(ergebnis: String, http: Int): String
    val dqSuchen: String
    val dqSucht: String
    val dqZuruecksetzen: String
    val dqHinweisLink: String
    val dqAusblenden: String
    val dqVerstanden: String
    fun dqHinweisTitel(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis): String
    fun dqHinweisText(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis): String

    // 5.92 P3 Startseite, Profil, Einstellungen
    val wfTitel: String
    val wfText: String
    val wfReihenfolge: String
    val wfHoch: String
    val wfRunter: String
    fun wfVoll(n: Int): String
    val wfOriginal: String
    val wfOriginalKurz: String
    val wfOriginalHinweis: String
    val wfVorgabe: String
    val wlZeile: String
    val wlWieEinstellung: String
    val wlEigeneWahl: String
    val wlGlobal: String
    val wlGlobalHinweis: String
    val wlZinssatz: String
    val wlSortSeit: String
    val wlSortDatum: String
    val zgKostenpflichtig: String
    val zgIntroPunkte: List<String>
    fun zgZweck(dienst: String): String
    fun zgPunkte(dienst: String): List<String>
    val saAnsehenTitel: String
    val saAnsehenText: String
    fun saAnsehenName(id: String): String
    fun saAnsehenInhalt(id: String): String

    // 5.92 P1 Wertpapierseite oben
    val p1IndWaehlen: String
    val p1BilanzLaedt: String
    val p1BilanzWenig: String
    fun p1BilanzZeile(stufe: String, treffer: String, vergleich: String, unruhe: Boolean): String
    fun p1BilanzSeit(jahr: Int, signale: Int): String
    fun indForschung(art: String): String
    val indForschungAllgemein: String
    fun p1BilanzAllgemein(treffer: String, vergleich: String, werte: Int, ab: Int): String
    fun p1BilanzDetail(art: String, jahr: Int, signale: Int, richtig: Int, treffer: String?, vergleich: String?, tage: Int): String
    fun p1Spanne(unten: String, oben: String, tage: Int): String
    val p1SpanneMehr: String
    val p1SeitKauf: String
    fun p1Haltedauer(datum: String, tage: Long): String
    val p1HebelLong: String
    val p1HebelShort: String
    val pcEinfuehrung: String
    val pcDarstellung: String
    val pcEinblenden: String
    val pcZeichnen: String
    val pcOhlc: List<String>
    val pcEinstieg: String
    val pcIndikatorenEinfuehrung: String
    fun pcWerkzeug(key: String): String

    // 5.92 P2 Wertpapierseite Analyse
    val bkTitel: String
    val bkBewertung: String
    val bkWachstum: String
    fun bkBewertungSatz(noetig: String, art: Int, bisher: String?): String
    fun bkFairerWert(preis: String, wachstum: String, abstand: String, darueber: Boolean): String
    val bkBewertungVerlust: String
    fun bkBilanzSatz(ampel: Int, gruende: List<String>): String
    fun bkGrund(key: String): String
    fun bkWachstumSatz(umsatz: String, umsatzPct: String?, gewinn: String?, gewinnPct: String?): String
    fun bkWachstumUnter(jahr: String): String
    val bkFairSelbst: String
    fun bkGruppe(key: String): String
    fun bkVonBis(von: String, bis: String): String
    val bkKuv: String
    val bkEvEbitda: String
    val bkPeg: String
    val bkFcfRendite: String
    val bkGesamtkapitalRendite: String
    val bkEbitdaMarge: String
    val bkVerschuldung: String
    val bkLiquiditaet: String
    val bkDividendeFuenf: String
    val bkDividendenWachstum: String
    val bkLeerQuote: String
    val bkLeerTage: String
    val bkInsider: String
    val bkInstitutionen: String
    val bkAktien: String
    val bkAktienVeraenderung: String
    val bkZielSpanne: String
    val bkZielMedian: String
    val bkGewinnErwartet: String
    val bkGewinnWachstumJahr: String
    val bkGewinnWachstumFolge: String
    val bkUmsatzWachstumFolge: String
    val bkNaechsteZahlen: String
    val bkJahresVeraenderung: String
    val anLaedt: String
    val anKeineDaten: String
    val anVerteilung: String
    fun anVergleich(diff: Double): String
    val anNurUs: String
    val anBereinigt: String
    fun anAlleHaeuser(n: Int): String
    val anGewichtet: String
    val anZiel: String
    fun anZielGewichtet(preis: String): String
    val anTrefferAlle: String
    fun anTrefferSatz(quote: String, n: Int, monate: Int, index: String?, wenig: Boolean): String
    fun anHinweis(herde: String): String
    val anHaeuser: String
    fun anStufe(s: Int): String
    fun anKursziel(preis: String): String
    val anVeraltet: String
    fun anZuWenige(n: Int): String
    fun anTreffer(je: List<String>, bereinigt: String): String
    val anWiderspruch: String
    val crInfo: String
    fun crLeer(anteil: String, trend: Int?, us: Boolean): String
    val crOptionen: String
    fun crOptionenWert(long: String): String
    fun evErwartet(eps: String?, umsatz: String?): String
    val evLetzteZahlen: String
    fun evQuartal(ende: String): String
    val rrNutzen: String
    val rrErholung: String
    val rrNochNicht: String
    val rrZeitraum: String
    fun rrVonBis(von: String, bis: String): String
    val wzRueckblick: String
    val wzRueckblickUnter: String
    val wzEinmal: String

    // 5.92 Maerkte
    val maerkteAusblick: String
    val infoErklaerung: String
    val mpVomHochKurz: String
    val cotInfo: String
    fun cotKern(markt: String?, index: Int?): String
    fun cotSatz(index: Int): String
    fun cotDetail(anteil: String, aenderung: String, index: Int): String
    val sbSorgen: String
    val sbQuelleSorgen: String
    val sbSorgenMehr: String
    fun trDepotSatz(betrag: String): String
    val trZumEtf: String
    val rotTitel: String
    val rotInfo: String
    val rotKeine: String
    fun rotSatz(aus: String?, nach: String?): String
    fun rotGrund(grund: String): String
    val rotAus: String
    val rotNach: String
    val meTitel: String
    val meInfo: String
    val meQuelle: String
    fun meThema(schluessel: String): String
    val zeTitel: String
    fun zeSatz(schritte: Double?): String
    val zeHeute: String
    fun zeIn(monate: Int): String
    val zeQuelle: String
    val blTitel: String
    val blInfo: String
    fun blStufe(stufe: Int): String
    fun blSatz(rot: Int, alle: Int, heiss: String?): String
    val blDafuer: String
    val blDagegen: String
    val blBranchen: String
    fun blSektor(jahr: String, zu200: String): String
    val blQuelle: String
    fun blName(schluessel: String): String
    fun blLage(schluessel: String, ampel: Int): String
    // bipQuartal: Quartal des BIP von FRED (dann aufs Jahr gerechnet), sonst bipJahr der Weltbank
    fun blErklaerung(schluessel: String, bipJahr: Int?, bipQuartal: String? = null): String

    // 5.92 Copytrading und Politik
    fun copyOgeNeu2(n: Int, datum: String): String
    val pdfLaden: String
    val pdfKurz: String
    val pdfGestartet: String
    val pdfFehler: String
    fun parteiKurz(p: String): String
    fun parteiLang(p: String): String
    fun usStaat(code: String, name: String): String
    fun wahlkreisVon(staat: String, nr: Int): String
    fun kammerSeit(senat: Boolean, jahr: Int): String
    val ausschuesse: String
    fun ausschussFunktion(f: String): String?
    val vmHinweisOge: String
    fun rgStand(wann: String): String
    fun rgErklaerung(k: String): String
    val rgText2: String
    fun rgPunkte(p: String): String
    fun rgAusKaeufen(n: Int): String
    fun rgTreffer(besser: Int, von: Int): String
    fun rgKaufsumme(betrag: String): String
    val kaQuelleKurz: String
    val kaText2: String
    fun awKaeufeDanach(n: Int): String
    fun awVerkaeufeDanach(n: Int): String
    val awImPlus: String
    val awBesserAlsMarkt: String
    val awGefallen: String
    val awSchwaecherAlsMarkt: String
    val awMitte: String
    fun awUnusualKurz(anzahl: String, schnitt: String): String
    fun awVorAnstieg(n: Int): String
    fun awVorRueckgang(n: Int): String
    fun awMitZufall(anteil: String, zufall: String): String
    fun awHinweise(bewertet: Int, werte: Int): String
    fun ifFilterZahl(n: Int): String
    fun ifTreffer(n: Int): String
    val ifKeine: String
    val ifRolle: String
    val ifGroesse: String
    val ifMehr: String
    val ifOhnePlan: String
    val ifHinweis: String
    fun ifAb(betrag: String): String
    fun ifTage(t: Int): String
    fun usQuelleKurz(stand: String): String
    val pmGrossGroesste: String
    val pmProfis: String
    val pmProfisLaedt: String
    val pmProfisKeine: String
    val pmProfisText: String
    val pmOhneSport: String
    val pmGrossText2: String
    fun pmGrossZeit2(key: String): String
    fun pmProfiPlatz(rang: Int, kategorie: String, monat: Boolean): String
    fun pmProfiGrund(g: String): String

    // 5.92 Aktienfinder und Radar
    val afTitel: String
    val afEinleitung: String
    val afStart: String
    val afLaedt: String
    val afDetailsLaedt: String
    fun afDetailsMehr(n: Int): String
    val afKeine: String
    fun afBereich(b: String): String
    fun afEinordnung(e: String): String
    fun afGrund(k: String, gut: Boolean, wert: String, vergleich: String?): String
    val afGewichte: String
    val afGewichteHinweis: String
    val afStandard: String
    fun afSortierung(name: String): String
    val afPunktzahl: String
    val afDividende: String
    val afBoersenwert: String
    val afAlleBranchen: String
    val afAlleRegionen: String
    val afAlleLaender: String
    val afAlleGroessen: String
    val afJedeDividende: String
    fun afDividendeAb(p: String): String
    val afJedeBewertung: String
    fun afGroesse(g: String): String
    fun afMindest(p: Int): String
    fun afTreffer(n: Int, gesamt: Int): String
    fun afDatenlage(p: String): String
    val afZurAktie: String
    val afSoRechnet: String
    val afErklaerung: String
    val afHinweis: String
    fun afNachbilden(n: Int): String
    fun afStand(zeit: String): String
    val afQuelle: String
    val afWeniger: String
    fun afMehrZeigen(n: Int): String
    val radTitel: String
    val radEinleitung: String
    val radStart: String
    val radNeu: String
    val radLaedt: String
    val radKeine: String
    fun radArt(art: String): String
    fun radSignal(art: String, n: Int, a: String, b: String, text: String): String
    val radAlle: String
    val radBekannt: String
    fun radArten(n: Int): String
    val radSoRechnet: String
    val radErklaerung: String
    val radHinweis: String
    fun radQuelle(namen: List<String>): String
    fun radQuelleName(k: String): String
    fun radStand(zeit: String): String
    val radHeute: String

    // 5.92 Fruehindikatoren
    val fiTitel: String
    val fiInfo: String
    fun fiName(schluessel: String): String
    fun fiLagerSatz(lage: Int, abweichung: String, unter: Boolean, jahre: Int): String
    fun fiDuerreSatz(punkte: String, mehr: Boolean, rekord: Boolean, jahre: Int): String
    fun fiAntraegeSatz(anstieg: String, ampel: Int): String
    fun fiFlugSatz(veraenderung: String, mehr: Boolean): String
    fun fiErklaerung(schluessel: String): String
    fun fiQuelle(schluessel: String): String
    val fiQuellen: String
    fun fiStand(datum: String): String
    fun fiWoche(aenderung: String): String
    fun fiBereich(von: String, bis: String, jahre: Int): String
    fun fiMioBarrel(wert: String): String
    fun fiMrdKubikfuss(wert: String): String
    fun fiMioTag(wert: String): String
    val fiPunkte: String
    fun fiSpr(wert: String, woche: String?): String
    val fiOhneZugang: String
    val zgAgsiName: String
    val zgAgsiZweck: String
    val zgAgsiPunkte: List<String>
    val zgAgsiHolen: String
    val fiAntraegeTief: String
    val fiBrancheTitel: String

    // 5.92 Vermoegen Regierung
    fun ogeVmTitel(art: String, jahr: Int): String
    val ogeVmHinweis: String
    fun ogeVmPdf(datum: String): String
    // 5.92 Aktienfinder Bilanz
    val afNurSolide: String
    fun afBilanzStand(n: Int, gesamt: Int): String
    val afNeuOrdnen: String
    val afBilanzWeiter: String
    val afBilanzFolgt: String
    fun afBilanzZahlen(f: String?, zone: String?): String
    // 5.92 Ersatzquellen
    fun ersSenatIntro(stand: String): String
    val ersSenatQuelle: String
    val ersSenatGlocke: String
    fun ersBlName(schluessel: String): String?
    fun ersBlLage(schluessel: String, ampel: Int): String?
    fun ersBlErklaerung(schluessel: String, zusatz: String?, stand: String?): String?
    val ersBlQuelle: String
    val ersTitel: String
    val ersInfo: String
    fun ersName(schluessel: String): String
    fun ersSatz(schluessel: String, ampel: Int): String
    fun ersErklaerung(schluessel: String): String
    fun ersStand(monat: String): String
    fun ersVorjahr(wert: String): String
    val ersQuellen: String
    fun ersQuelle(schluessel: String, jahr: Int): String
    val ersHnTitel: String
    fun ersHnZeile(woche: Int, schnitt: String): String
    fun ersMastodonTitel(tag: String): String
    fun ersMastodonZeile(beitraege: Int): String
    val ersNetzHinweis: String
    fun ersMrdDollar(wert: String): String
    fun ersQuartal(q: Int, jahr: Int): String
    // 5.92 Eingepreist
    val epTitel: String
    // richtung +1 eher long, -1 eher short, 0 keine klare Richtung; was: worauf, falls nicht aus der Seite klar
    fun epTendenz(richtung: Int, was: String?): String
    val epWasAnleihen: String
    val epWasZyklisch: String
    val epWasAirlines: String
    fun epWasKorn(symbol: String): String
    val epInfo: String
    fun epRisiken(mass: String): String
    // urteil: 0 passt, 1 Markt hoeher, 2 Markt tiefer; lage: -1 knapp, 0 ueblich, 1 reichlich
    fun epLagerSatz(urteil: Int, lage: Int, gas: Boolean, kurve: Boolean): String
    fun epDuerreSatz(urteil: Int, viel: Boolean): String
    fun epFlugSatz(urteil: Int): String
    fun epAntraegeSatz(urteil: Int): String
    fun epZyklischSatz(urteil: Int): String
    val epKeine: String
    val epUnsicher: String
    fun epKurve(spanne: String, teurer: Boolean): String
    fun epPreis(abstand: String, ueber: Boolean, name: String): String
    fun epAirlines(abstand: String, besser: Boolean): String
    fun epZins(abstand: String): String
    fun epZyklisch(abstand: String, besser: Boolean): String
    fun epHeute(wert: String): String
    fun epUeblich(wert: String): String
    fun epSelten(anteil: String, hoeher: Boolean): String
    fun epStaerke(staerke: Int, anteil: String): String
    fun epZeitraum(wochen: Int, von: String, bis: String): String
    val epKurveTitel: String
    val epZusatzTitel: String
    fun epKorn(symbol: String): String
    // Datierte Signale
    fun epSeit(art: String, mehr: String, index: String?): String
    fun epLauf(lauf: Int): String
    fun epFrueher(schnitt: String, n: Int): String
    fun epRichtwert(wert: String): String
    fun epHandelstage(n: Int): String
    val epSeitInfo: String
    val epKaeufeTitel: String
    val epKaeufeHinweis: String
    fun epKaufZeile(datum: String, mehr: String, index: String): String
    val epAnalystenTitel: String
    val epInsiderTitel: String
    // 5.92 Finder-Test
    val afRueckblickTitel: String
    fun afRueckblickText(von: String, bis: String, werte: Int, top20: String, msci: String, treffer: String): String
    fun afRueckblickGrenzen(bilanzAb: String?, ganz: Boolean, ic: Double, icSe: Double, bewertung: Pair<Double, Double>?): String
    // 5.92 Weitere Quellen
    fun wqName(k: String): String
    fun wqSatz(k: String, ampel: Int, steigt: Boolean): String
    fun wqErklaerung(k: String): String
    fun wqQuelle(k: String): String
    fun wqWoche(wert: String): String
    fun wqBahnTeile(wagen: String?, container: String?): String
    fun wqWocheBis(tag: String): String
    fun wqStandTag(tag: String): String
    fun wqEinheiten(n: String): String
    val wqFbxLink: String
    val wqBalticLink: String
    val wqLmeLink: String
    val wqComexLink: String
    val wqFinraLink: String
    fun wqBrowserText(land: String): String
    fun wqBrowserLink(land: String): String
    fun wqKwQuelle(stand: String): String
    val wqRichterTitel: String
    val wqRichterIntro: String
    val wqRichterOhneZugang: String
    val wqRichterLeer: String
    val wqRichterLaedt: String
    val wqRichterName: String
    fun wqRichterZeile(jahr: Int, tag: String): String
    fun wqSpanne(von: String, bis: String?): String
    val wqRichterQuelle: String
    val wqJefsLink: String
    val wqParlamenteTitel: String
    val wqParlamenteText: String
    val wqEuLink: String
    val wqAusHausLink: String
    val wqAusSenatLink: String
    val wqBskyTitel: String
    fun wqBskyZeile(woche: Int, vorwoche: Int, gekappt: Boolean): String
    val wqXLink: String
    val wqStocktwitsLink: String
    val wqClName: String
    val wqClZweck: String
    val wqClPunkte: List<String>
    val wqClFeld: String
    val wqClHolen: String
    val wqBskyZweck: String
    val wqBskyPunkte: List<String>
    val wqBskyHandle: String
    val wqBskyPasswort: String
    val wqBskyUngueltig: String
    val wqBskyHandleUngueltig: String
    val wqBskyHolen: String
    fun wqMeldung(m: String): String
    fun wqVerbrauch(verbraucht: String, erlaubt: String): String
    // 5.92 Tendenz-Rueckblick
    fun trFrueher(rot: Boolean, treffer: String, grund: String, wochen: Int, mittel: String, faelle: Int): String
    val trOhneVorteil: String
    fun trNichtGeprueft(zuKurz: Boolean): String
    fun trNach(wochen: Int): String
    val trDetailTitel: String
    fun trDetailZeile(wochen: Int, treffer: String, grund: String, mittel: String, t: String, faelle: Int, belegt: Boolean): String
    fun trGeprueft(von: String, bis: String): String
    fun trPunkte(wert: String): String
    fun trIdeeTitel(call: Boolean, name: String): String
    fun trIdeeDaten(basis: String, verfall: String, hebel: String): String
    fun trIdeeRisiko(zeitwert: String): String
    val trIdeeHinweis: String
    fun trBasisName(symbol: String): String
    val trInfo: String

    // 5.92 Investorenbriefe
    val ibTitel: String
    val ibKopf: String
    fun ibZusammenfassung(absender: Int, positiv: Int, negativ: Int, neutral: Int): String
    fun ibEigeneSache(n: Int): String
    fun ibRichtung(r: Int): String
    fun ibArt(art: String): String
    val ibSecBericht: String
    fun ibBeleg(beleg: String, stand: String): String
    fun ibPasst(passt: String): String
    val ibWerbung: String
    fun ibQuelle(wo: String): String
    val ibLeer: String
    val ibSuchen: String
    val ibSecHinweis: String

    // 5.92 Textrunde
    val indFussTitel: String

    // 5.93 depotverlauf
    val pvYtd: String
    fun ptGapBeats(v: String): String
    fun ptGapTrails(v: String): String
    // 5.93 briefe
    // 5.93 Investorenbriefe: kurzer Infotext, Mehr aus dem Brief
    val ibInfoKurz: String
    val ibMehrBrief: String

    // 5.93 nachrichten2
    val rbMitteilung: String
    val rbMitteilungHinweis: String
    val notifWerkHinweis: String
    val copyBellsWerk: String
    // 5.93 strategielabor
    val slEigen: String
    val slEigenHinweis: String
    fun slLotterieErgebnis(pruefBesser: Boolean, echt: Boolean, anzahl: Int, prozent: String): String
    // 5.93 hintergrund
    val rrHintMax: String
    val rrHintErholung: String
    val rrHintLaengster: String
    val rrHintRendite: String
    val rrHintBeta: String
    val rrHintKorrelation: String
    val lvKurz: String
    // 5.93 finder-radar
    val afAnsichtListe: String
    val afAnsichtRadar: String
    val afImGespraech: String
    val afRadarAlle: String
    val afRadarErwaehnungen: String
    val afRadarWikipedia: String
    fun afRadarStaerke(n: String): String
    val afRadarHinweis: String
    // 5.93 stimmung
    val sbWsbUnklar: String
    val sbWsbNachFall: String
    val sbWsbNachAnstieg: String
    fun sbWsbAnlass(ticker: String, aenderung: String): String
    fun sbWsbUnklarMehr(prozent: Int): String
    val sbWsbOffen: String
    val sbVix: String
    val sbQuelleVix: String
    fun sbVixMehr(vix: String): String
    fun tdText(richtung: Int, ziel: String): String

    // 5.93 ipo
    val secIpoTitel: String
    val secIpoKontakt: String
    val secIpoLeer: String
    val secIpoProspekt: String
    val secIpoAenderung: String
    val secIpoPreis: String
    val secIpoHinweis: String

    // 5.93 finder-punkte
    // 5.93 Aktienfinder: kaufen und halten im Rueckblick
    val afHaltenTitel: String
    fun afHaltenSeit(von: String, jahre: String, top20: String, msci: String, spx: String): String
    fun afHaltenZeile(jahre: String, top20: String, msci: String, spx: String): String
    fun afHaltenHinweis(fehlt: String): String

    // 5.93 kennzahlen
    val bkKursCashflow: String
    val bkZinsdeckung: String
    val bkRoic: String
    val bkOwnerEarnings: String

    // 5.93 halter-gewinne
    val hlTitel: String
    val hlInstitutionen: String
    val hlInsider: String
    fun hlAnzahl(n: Int): String
    val hlGroesste: String
    fun hlStand(datum: String): String
    val hlHinweis: String
    fun zuNaechster(datum: String): String
    // 5.93 maerkte
    val maerkteBewegungenDeutung: String
    val meKernTitel: String
    fun meKern(zins: Int?, rezession: Int?): String
    fun meFalls(schluessel: String): String
    fun fiDeutung(schluessel: String): String

    // 5.93 stimmung2
    val sbSicher: String
    val sbQuelleSicher: String
    fun sbSicherMehr(unterschied: String): String

    // 5.93 selbstabruf
    fun saWartet(ordner: String): String
    val saSeiteUebernehmen: String
    val saFehler: String

    // 5.93 nacharbeit
    fun trRangAenderung(plaetze: Int, hoeher: Boolean): String

    // 5.94
    val anHochgestuft: String
    val anAbgestuft: String
    fun lvStufe(stufe: Int): String
    fun trHeuteKurz(rang: Int, veraenderung: String?): String
    fun trSummenKurz(woche: String, monat: String?, jahr: String, seit: String?, spitze: Pair<String, String>?): String
    fun ersMastodonKurz(beitraege: Int): String
    val nwEinordnung: String
    val nwZurAktie: String
    val ampelGut: String
    val ampelAchtung: String
    val ampelKritisch: String
    val ampelHinweis: String
    val wlSortMarktwert: String
    fun epWasBranche(markt: String): String
    fun epBranche(markt: String, abstand: String, besser: Boolean): String
    fun epErsatzSatz(schluessel: String, markt: String, urteil: Int): String
    fun ipoReiter(reiter: Int): String
    fun ipoStatusTag(status: Int, datum: String): String
    val ipoReiterLeer: String
    val ipoEckdaten: String
    val ipoPreis: String
    val ipoAktien: String
    val ipoVolumen: String
    val ipoEinschaetzung: String
    fun ipoPunkt(merkmal: Int, ampel: Int, wert: String?): String
    val ipoRitter: String
    val ipoBrancheTitel: String
    fun ipoBranche(b: Int): String
    fun ipoBrancheText(b: Int): String
    val ipoUeber: String
    val ipoDiskussion: String
    fun ipoHnWoche(n: Int): String
    val ipoKeineSchlagzeilen: String
    val ipoDokumente: String
    fun ipoFormular(f: String): String
    val ipoEdgarLink: String
    val ipoQuelle: String
    val ipoOhneSchluessel: String
    val ipoKalenderFehlt: String
    val ipoKeineEckdaten: String
    val ipoEdgarSuche: String
    fun ipoKatalogGeplant(stand: String): String
    fun bkBankSatz(kbv: String, roe: String, angemessen: String?): String
    fun bkBankUnter(kapitalkosten: String, wachstum: String): String
    fun bkBankBilanzSatz(roa: String?, ekQuote: String?): String
    val bkBankBilanzUnter: String
    val heatmapAlle: String
    val heatmapZoomZurueck: String
    fun skKnoten(id: String): String
    val skGeldfluss: String
    val skVerkaeufe: String
    fun skGuvTitel(jahr: String): String
    fun afVorlage(id: String): String
    fun afVorlageIdee(id: String): String
    fun afVorlageFehlt(id: String): String?
    fun afFeld(id: String): String
    val afVorlageKeine: String
    val afVorlageNurUs: String
    val afVorlageHinweis: String
    fun afVorlageStand(offen: Int): String
    val afVorlageWeiter: String
    val afAlarm: String
    val afAlarmHinweis: String
    fun afAlarmTitel(vorlage: String): String
    fun afAlarmText(namen: List<String>, weitere: Int): String
    fun afFeldAb(feld: String, wert: String): String
    fun afFeldBis(feld: String, wert: String): String
    fun afFeldZwischen(feld: String, von: String, bis: String): String
    fun afFeldPositiv(feld: String): String
    fun afFeldNegativ(feld: String): String
    fun afUeberLinie(id: String): String
    fun afUnterLinie(id: String): String
    fun afNeuesHoch(tage: Int): String
    fun afNahHoch52(abstand: String): String
    fun afZahlenIn(tage: Int): String
    val afKreuzung: String
    fun afSortFeld(feld: String, aufsteigend: Boolean): String
    val widgetNewsInhalt: String
    val widgetNewsDepot: String
    val widgetNewsWichtig: String
    val widgetNewsWichtigTitel: String
    val widgetNewsWichtigKeine: String
    val widgetNewsAlle: String
    val widgetNewsAlleTitel: String
    fun widgetNewsFeed(name: String): String
    val widgetNewsListeKeine: String
    val widgetNewsFeedsHinweis: String
    val widgetSchnellWahl: String
    val widgetSchnellHinweis: String
    val widgetSchnellVerlauf: String
    val widgetEinzelSuche: String
    fun apTitel(id: String): String
    fun apWert(id: String, a: String, b: String, zahl: Int): String
    fun apErklaerung(id: String): String
    val apUeberschrift: String
    val apZuWenig: String
    val apHinweis: String
    val scTitel: String
    val scFragen: List<String>
    fun scStand(ja: Int, alle: Int): String
    val scHinweis: String
    fun anHaelt(anteil: String, wert: String, stichtag: String, seit: String?): String
    val anHalterHinweis: String
    fun anHalterKeiner(n: Int): String
    val finJahreTitel: String
    val finLangfristSchulden: String
    val finInvestitionen: String
    fun umsatzSchaetzung(quartal: String): String
    val qzTitel: String
    val qzIntro: String
    val qzBekannte: String
    val qzAlle: String
    val qzFehlt: String
    val qzTagFehlt: String
    val qzKeine: String
    fun qzKeineBekannten(n: Int): String
    fun qzZeit(zeit: Int): String
    val qzHeute: String
    fun qzVon(gezeigt: Int, gesamt: Int): String
    val qzEigen: String
    fun qzErwartet(wert: String): String
    fun qzVorjahr(wert: String): String
    val qzHinweis: String
    fun qzQuelle(anbieter: String?): String
    val qzOhneSchluessel: String
    val qzKeineEigenen: String
    fun qzUmsatz(wert: String): String
    // 5.95 Lesemodus
    val lesemodusLeer: String
    // 5.95 Gesamtstimmung
    val sgTitel: String
    val sgKachel: String
    val sgAnbieter: String
    fun sgAnzahl(n: Int, von: Int): String
    val sgLaedtNoch: String
    val sgGewichte: String
    fun sgGruppe(schluessel: String): String
    fun sgGruppeWarum(schluessel: String): String
    fun sgTeil(schluessel: String): String
    fun sgMethode(schluessel: String): String
    fun sgRoh(schluessel: String, zahl: String): String
    fun sgHochTief(hochs: Int, tiefs: Int): String
    fun sgGewicht(prozent: String): String
    fun sgStand(datum: String): String
    fun sgFehlen(namen: String): String
    val sgQuellen: String
    fun sgZitatUmich(datum: String): String

    // 5.95 Lizenzen
    val lizenzenTitel: String
    val lizenzenUntertitel: String
    val lizenzenHinweis: String
    val lizenzenFehlt: String
    // 5.95 Nasdaq
    val nkQuelle: String
    val nkNichtVerfuegbar: String
    val nkIpoFehlt: String
    fun nkIpoQuelle(finnhub: Boolean): String
    fun nkQzQuelle(anbieter: String?): String
    val nkQzHinweis: String
    val nkDivTitel: String
    val nkDivIntro: String
    fun nkZahltag(datum: String): String
    fun nkJeJahr(wert: String): String
    val nkDivKeine: String
    fun nkDivKeineBekannten(n: Int): String
    val nkDivHinweis: String
    val nkSplits: String
    val nkSplit: String
    val nkZusammenlegung: String
    fun nkSplitText(neu: Int, alt: Int): String
    val nkWiTitel: String
    val nkWiIntro: String
    val nkWichtige: String
    val nkGanztags: String
    fun nkVorher(wert: String): String
    val nkEuroraum: String
    val nkWiKeine: String
    val nkWiHinweis: String

    // 5.95 Quellen
    val q5Nicht: String
    val q5VxTitel: String
    fun q5VxVerhaeltnis(q: String): String
    fun q5VxQuelle(datum: String): String
    val q5VxLink: String
    val q5VxMehr: String
    val q5IgPrivat: String
    val q5IgJa: String
    val q5IgNein: String
    val q5IgKeine: String
    fun q5IgQuelle(quellen: String): String
    val q5IgSeite: String
    fun q5IgQuelleMeldung(quelle: String): String
    val q5IgOeffnen: String
    val q5IgAlleTitel: String
    val q5IgAlleUnter: String
    val q5IgQuelleAlle: String
    val q5IgKategorie: String
    val q5NnKeine: String
    val q5NnQuelle: String
    val q5MnbKeine: String
    val q5MnbQuelle: String
    val q5EodTitel: String
    val q5EodKeine: String
    val q5EodQuelle: String
    val q5EodLimit: String
    val q5IgQuelleOhne: String
    val q5StandardArtikel: String
    // 5.95 Long/Short
    fun opUsNotierung(kuerzel: String): String

    // 5.95 Stimmrechte GB
    val srTitel: String
    val srKeine: String
    val srOhne: String
    fun srNachher(p: String): String
    fun srVorher(p: String): String
    fun srUnter(p: String): String
    fun srAktien(p: String): String
    fun srInstrumente(p: String): String
    fun srSchwelleUeber(p: String): String
    fun srSchwelleUnter(p: String): String
    fun srStichtag(d: String): String
    fun srPunkte(z: String): String
    fun srQuelle(quellen: String): String
    val srQuelleOhne: String
    val srHinweis: String

    // 5.96 Labor
    val slRegelWaehlen: String
    val slKeineLinie: String
    fun slLinieSchalter(regel: String): String

    // 5.96 Daten
    fun hlUnter(p: String): String
    val hlUnveraendert: String
    fun hlMitNamen(n: Int, gesamt: Int): String
    val hlGemeldet: String
    val hlKeineNamen: String
    val hlFonds: String
    val hlFondsHinweis: String
    val vmWerte: String
    val vmSchulden: String
    val vmNetto: String
    val vmKeineSchulden: String
    fun vmImMinus(spanne: String): String
    fun vmPosten(werte: Int): String
    fun vmNettoOffen(schlecht: String?, gut: String?): String
    val vmRangErklaerung: String

    // 5.96 Vergleich
    fun vgMax(n: Int): String
    val vgSuche: String
    val vgAktienEtfs: String
    val vgPolitiker: String
    val vgPolitikerMehr: String
    val vgLabor: String
    fun vgLaborUnter(name: String): String
    val vgHaus: String
    val vgSenat: String
    val vgRegierung: String
    val vgKeineTreffer: String
    fun vgZeitraum(von: String, bis: String): String
    val vgHeute: String
    fun vgKuerzer(name: String, ab: String): String
    val vgLaedt: String
    val vgKeineDaten: String
    val vgZuKurz: String
    fun vgGeschaefte(n: Int): String
    val vgWaehrungHinweis: String
    val vgPlanNote: String
    val vgGleicheRaten: String
    val vgLaborHinweis: String
    fun vgOhneDaten(namen: String): String
    fun vgWenigGeschaefte(namen: String): String
    val vgVergleichIntro: String
    val vgImZeitraum: String

    // 5.96 Feed
    val feedErstellen: String
    val feedBearbeiten: String
    val feedHinweis: String
    val feedName: String
    val feedZugaenge: String
    val feedQuellenApp: String
    val feedPruefen: String
    fun feedZugangOk(titel: String, n: Int): String
    val feedNichtErreichbar: String
    val feedKeinFeed: String
    fun feedFehler(f: de.troetenrepublik.app.engine.EigenerFeed.Fehler): String
    fun feedSchonInApp(name: String): String
    val feedSchonDrin: String
    val feedQuelleAus: String
    fun feedAusgeschaltet(namen: String): String
    val feedLeer: String
    val feedOhneQuelle: String
    val feedLoeschen: String
    fun feedLoeschenFrage(name: String): String
    val feedLoeschenText: String
    fun feedZuViele(n: Int): String
    fun feedMeldeOption(stufe: String): String
    fun feedMeldeOptionHint(stufe: String): String
    val feedMelden: String
    val feedMeldenHint: String
    val feedMeldeQuellen: String
    val feedOhneFeeds: String
    val feedBuendel: String

    // 5.96 Kongress
    val copyFormer: String
    val copyMitgliederQuelle: String
    fun kammerZeitraum(senat: Boolean, von: Int, bis: Int): String
    fun amtZeitraum(von: Int, bis: Int): String

    // 5.97 Aufklappen
    val aufZahlenHerkunft: String

    // 5.97 Finanzen
    val finDivTitel: String
    val finDivJeAktie: String
    val finDivExTag: String
    val finDivZahltag: String
    fun finDivBestand(stueck: String): String
    val finVerlust: String
    val trErkReddit: String
    val trErkRedditDeutsch: String
    fun trErkVerlauf(seit: String?): String
    val trErkBeitraege: String
    val trErkYahoo: String
    val trErkWiki: String
    val trErkHn: String
    fun trErkMastodon(tag: String): String
    val trErkBluesky: String

    // 5.97 Quartalszahlen
    fun qzUhrKurz(art: Int, von: String?, bis: String?): String?
    fun qzUhrLang(art: Int, von: String?, bis: String?): String
    fun qzZeitTag(zeit: String, versatz: Int): String
    val qzGeschaetzt: String
    fun qzZeitzone(zone: String): String
    val qzbZurAktie: String
    fun qzbKalenderNotierung(symbol: String): String
    val qzbKalenderNotierungText: String
    fun qzbQuartal(quartal: String): String
    val qzbTermin: String
    val qzbTag: String
    val qzbUhrzeit: String
    val qzbKonferenz: String
    fun qzbTerminSpanne(von: String, bis: String): String
    val qzbGeschaetztText: String
    val qzbUhrText: String
    val qzbZahlen: String
    val qzbGewinn: String
    val qzbUmsatz: String
    fun qzbErwartetVon(quelle: Int, analysten: Int?): String
    val qzbSpanne: String
    fun qzbVonBis(von: String, bis: String): String
    val qzbVorjahr: String
    val qzbGemeldet: String
    val qzbAusblick: String
    val qzbAusblickText: String
    val qzbZahlenText: String
    val qzbKeineErwartung: String
    val qzbBewertung: String
    fun qzbGewinnWachstum(a: String, b: String, p: String): String
    fun qzbGewinnRueckgang(a: String, b: String, p: String): String
    fun qzbGewinnAusVerlust(a: String, b: String): String
    fun qzbVerlustErwartet(a: String, b: String?): String
    fun qzbUmsatzVeraenderung(a: String, b: String, p: String): String
    fun qzbSpanneWeit(a: String, b: String, n: Int?): String
    fun qzbSpanneEng(a: String, b: String, n: Int?): String
    fun qzbSchaetzungHoch(a: String, b: String, p: String, n: Int?, m: Int?): String
    fun qzbSchaetzungRunter(a: String, b: String, p: String, n: Int?, m: Int?): String
    fun qzbSchaetzungGleich(a: String): String
    fun qzbTreffer(n: Int, m: Int, p: String): String
    fun qzbGemeldetUeber(a: String, b: String, p: String): String
    fun qzbGemeldetUnter(a: String, b: String, p: String): String
    fun qzbGemeldetWie(a: String, b: String): String
    val qzbBewertungText: String
    val qzbKeineBewertung: String
    val qzbVerlauf: String
    fun qzbQuartalBis(datum: String): String
    val qzbVonWem: String
    fun qzbQuelleKalender(anbieter: String?, schaetzungen: Int?): String
    fun qzbQuelleYahoo(gewinn: Int?, umsatz: Int?): String
    fun qzbHaeuser(namen: String): String
    val qzbVonWemText: String
    val qzbYahooFehlt: String
    // 0.68 Finanzen
    val finTippHinweis: String
    fun finErklaerung(label: String): String?
    val finUmsatzSchaetzungErklaerung: String
    val finQuartal: String
    val finJahr: String
    val finAbweichung: String
    // 0.68 Stimmung und Lager
    val sgKurz: String
    val sgRechnungTitel: String
    val sgRechnung: String
    val sgBarometerTitel: String
    val sgTeileTitel: String
    fun fiRichtung(schluessel: String, mehr: Boolean): String
    fun fiLegendeSchnitt(wert: String): String
    fun fiLegendeStand(datum: String, wert: String): String
    fun smMarkt(symbol: String): String
    fun smSeit(datum: String, markt: String, bewegung: String): String
    fun smLauf(lauf: Int): String
    val smNichtMessbar: String
    val smKeineKurse: String
    fun smNochNicht(datum: String): String

    // 0.68 Strategietest
    fun slRegelKurz(art: String, a: Int, b: Int, n: Int): String

    // 0.68 Suchverlauf
    val svKuerzlich: String
    val svLoeschen: String
    fun svEntfernen(name: String): String

    // 0.68 Tagesrueckblick und Mitteilungen
    val rbUhrzeit: String
    val rbUhrzeitHinweis: String
    fun rbEinleitungDepot(ton: String, hoch: Boolean, prozent: String): String
    val rbVeraenderung: String
    val rbWert: String
    val mtAlle: String
    fun mtProfil(an: Boolean, aktiv: Int, gesamt: Int): String
    val mtAlleAus: String
    val mtDepot: String
    val mtDepotHinweis: String
    val mtAlarme: String
    val mtAlarmeHinweis: String
    val mtNewsHinweis: String
    val mtCopy: String
    val mtCopyHinweis: String

    // 0.68 ipo
    fun ipoSchritt(i: Int): String
    val ipoSchrittZurueck: String
    val ipoPreisSpanne: String
    val ipoSpanneMitte: String
    val ipoSeitAusgabeTitel: String
    fun ipoKursJetzt(p: String): String
    fun ipoAusgabe(p: String): String
    fun ipoPunktKurz(merkmal: Int): String
    fun ipoPunktErklaerung(merkmal: Int): String
    val ipoSchlagzeilen: String
}
