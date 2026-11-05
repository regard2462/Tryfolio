package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.AssetType
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

object En : Strings {
    override val locale: Locale = Locale.UK

    override val appName = "Tryfolio"
    override val back = "Back"
    override val cancel = "Cancel"
    override val save = "Save"
    override val ok = "OK"
    override val next = "Continue"
    override val done = "Done"
    override val delete = "Delete"
    override val close = "Close"
    override val apply = "Apply"
    override val change = "Change"
    override val buy = "Buy"
    override val sell = "Sell"
    override val search = "Search"
    override val transfer = "Transfer"
    override val invest = "Invest"
    override val all = "All"
    override val none = "None"
    override val today = "Today"
    override val tomorrow = "Tomorrow"
    override val pieces = "pcs"
    override val perAnnum = "p.a."
    override val openEnd = "Open end"
    override val loading = "Loading …"
    override val open = "Open"
    override val closed = "Closed"
    override val no = "No"
    override val more = "More"
    override val less = "Less"
    override val copied = "Copied"
    override val error = "Error"

    override fun rangeLabel(r: Range) = when (r) { Range.D1 -> "1D"; Range.W1 -> "1W"; Range.M1 -> "1M"; Range.M3 -> "3M"; Range.M6 -> "6M"; Range.Y1 -> "1Y"; Range.MAX -> "Max" }
    override fun rangeCaption(r: Range) = when (r) { Range.D1 -> "Today"; Range.W1 -> "1 week"; Range.M1 -> "1 month"; Range.M3 -> "3 months"; Range.M6 -> "6 months"; Range.Y1 -> "1 year"; Range.MAX -> "Since start" }
    override val widgetWerte = "Watchlist"
    override val widgetGesperrt = "Locked · tap to open"
    override val widgetLeer = "No stocks in the watchlist"
    override fun widgetStand(zeit: String) = "As of $zeit"
    override val widgetsTitel = "Widgets"
    override val widgetsUntertitel = "Portfolio, watchlist, single stock, top and flop, dividends, markets, heatmap, sentiment, news, shortcuts"
    override val widgetsText = "Ten home screen widgets, overview below. To add one, long-press the home screen, then Widgets › Tryfolio. Choose content, background and text by long-pressing the widget › widget settings. With the app lock on they show no amounts, with “Hide balances” only percentages."
    override val widgetsAuswahl = "Stocks in widgets showing the watchlist (without a choice the whole watchlist)"
    override val widgetsNurAndroid = "Home screen widgets are available in the Android app."
    override val screenshotsTitel = "Screenshots"
    override val screenshotsText = "Whether screenshots and screen recordings of the app are possible."
    override fun screenshotsOption(modus: String) = when (modus) { "erlaubt" -> "Allowed everywhere"; "gesperrt" -> "Blocked everywhere"; else -> "Allowed, except keys" }
    override fun screenshotsHinweis(modus: String) = when (modus) {
        "erlaubt" -> "Also on the page with the API keys, where they can be shown in plain text. Block them again afterwards."
        "gesperrt" -> "No screenshots, no screen recordings, no picture in the recent apps list."
        else -> "Default: screenshots everywhere, except on the page with the API keys."
    }
    override val widgetBestand = "Holdings"
    override val widgetMover = "Top & flop today"
    override val widgetsAkku = "If the widgets do not update while the app is closed, the phone's battery optimisation is usually the cause. On Samsung: in the app settings under Battery choose “Unrestricted” and remove the app from the lists of sleeping apps. A tap on the time at the bottom of a widget reloads at once."
    override val widgetsAkkuOeffnen = "Open app settings"
    override val widgetDividenden = "Dividends"
    override val widgetKeinePositionen = "No holdings in the portfolio"
    override val widgetMoverRuhig = "No moves yet today"
    override val widgetDivKeinBestand = "No stocks or ETFs in the portfolio"
    override val widgetDivKeine = "No dividends expected in the next twelve months"
    override fun widgetDivErhalten(jahr: Int) = "Received $jahr"
    override val widgetDivErwartet = "Expected in 12 months"
    override val widgetsArten = "• Portfolio: value and change today, year to date or since purchase, with the chart (from two rows of height), percent only if you like\n• Watchlist: watchlist, holdings or an own list, scrollable, several widgets can show different lists\n• Single stock: one stock large with price, daily change and intraday line\n• Top & flop today: best and worst holdings\n• Dividends: received, expected, next payments\n• Markets: DAX, S&P 500, Nasdaq 100, Euro Stoxx 50, gold and Bitcoin\n• Heatmap: market, portfolio or watchlist as tiles\n• Sentiment: fear and greed gauge for stocks or crypto\n• News: top stories, all headlines, one of your own feeds or headlines about your largest holdings\n• Shortcuts: search, orders, markets and news\n\nTapping the title or the large value switches between amount and percent."
    override val widgetEinstellung = "Set up widget"
    override val widgetQuelle = "What should the widget show?"
    override val widgetQuelleWatchlist = "Watchlist (choice in the app under Profile › Widgets)"
    override val widgetQuelleDepot = "Holdings in the portfolio, largest first"
    override val widgetEinzelWahl = "Which stock should the widget show?"
    override val widgetEinstellungGesperrt = "The app is locked. Unlock it, then you can set up the widget."
    override val widgetEinstellungLeer = "No stocks in the watchlist or portfolio yet."
    override val widgetZeitraum = "Period"
    override val widgetZeitJahr = "Year to date"
    override val widgetZeitKauf = "Since purchase"
    override val widgetNurProzent = "Percent only"
    override val widgetNurProzentHinweis = "No amounts in the widget, for example when others look at your phone. Tapping the value in the widget switches as well."
    override val widgetAnzeige = "Display"
    override val widgetBetrag = "Change as amount"
    override val widgetBetragHinweis = "Daily change per share instead of percent. Tapping the title or the price in the widget switches as well."
    override val widgetBarometer = "Gauge"
    override val widgetQuelleListe = "Own list"
    override val widgetListenName = "Name of the list"
    override val widgetListeWahl = "Stocks for this list, in the order you tap them:"
    override val widgetSortiert = "Sort by daily change"
    override val widgetDarstellung = "Appearance"
    override val widgetGrund = "Background"
    override val widgetGrundVoll = "Solid"
    override val widgetGrundHalb = "Translucent"
    override val widgetGrundLeer = "None"
    override val widgetSchrift = "Text"
    override val widgetSchriftHell = "Light (on a dark background)"
    override val widgetSchriftDunkel = "Dark (on a light background)"
    override val widgetStimmung = "Sentiment"
    override val widgetNewsTitel = "Portfolio news"
    override val widgetNewsKeine = "No headlines about your holdings right now"
    override val sperreTitel = "App lock"
    override fun sperreStatus(art: String?) = when (art) { "pin" -> "On · PIN"; "passwort" -> "On · password"; else -> "Off" }
    override val sperreText = "Protects the app with a PIN or password. Portfolio, transactions, orders, savings plans, watchlist, alerts and notes are then stored on the device encrypted with AES-256-GCM. The app locks at startup and after one minute in the background. Settings and public data such as prices are not encrypted."
    override val sperreHinweisPin = "A PIN is easier to guess than a password. For real protection choose a password with at least 10 characters."
    override val sperreVergessenHinweis = "Without the PIN or password the encrypted data cannot be opened, not even by us. Back up your portfolio first (Profile › Export portfolio)."
    override val sperrePinFestlegen = "Set a PIN"
    override val sperrePasswortFestlegen = "Set a password"
    override val sperreAendern = "Change PIN or password"
    override val sperreAusschalten = "Turn off the lock"
    override val sperreAlt = "Current PIN or password"
    override fun sperreNeu(pin: Boolean) = if (pin) "New PIN (4 to 8 digits)" else "New password (at least 8 characters)"
    override val sperreWiederholen = "Repeat"
    override val sperreUngleich = "The two entries don't match."
    override fun sperreZuKurz(pin: Boolean) = if (pin) "The PIN needs 4 to 8 digits." else "The password needs at least 8 characters."
    override val sperreEingerichtet = "App lock on, data encrypted"
    override val sperreGeaendert = "Changed"
    override val sperreAusgeschaltet = "App lock off"
    override val sperreGesperrt = "Locked"
    override val sperreEntsperren = "Unlock"
    override fun sperreFalsch(versuch: Int) = if (versuch <= 0) "That's not right." else "That's not right (attempt $versuch). From the fifth wrong attempt the waiting time grows."
    override fun sperreWarten(sekunden: Long) = "Please wait $sekunden more seconds."
    override val sperreSchreibFehler = "Saving failed, the lock stays on. Please check storage space."
    override val sperreVergessen = "Forgot your PIN or password?"
    override val sperreZuruecksetzenTitel = "Delete data and start over?"
    override val sperreZuruecksetzenText = "Resetting deletes the portfolio, transactions and watchlist on this device. It turns the lock off and starts with a new practice portfolio. A backed-up portfolio can be imported afterwards. This can't be undone."
    override val sperreZuruecksetzen = "Delete and start over"
    override val sperreDatenFehlen = "The encrypted data couldn't be read."
    override val sperreNurApp = "The app lock is available in the app for Android, Windows and Linux."
    override val sperreSpeichern = "Turn on"
    override val ppExport = "Export for Portfolio Performance"
    override fun ppExportSub(n: Int) = "$n transactions as a CSV file"
    override val ppHinweis = "Portfolio Performance is a free program for Windows, macOS and Linux. To import there: File › Import › CSV files, type “Account transactions”. Fees are part of the order, imported positions appear as a deposit and a buy."
    override val ppSeite = "Open portfolio-performance.info"
    override fun ppFertig(n: Int) = "$n transactions saved"
    override val chartProfi = "Advanced chart"
    override fun chartArt(art: String) = when (art) { "linie" -> "Line"; "heikin" -> "Heikin-Ashi"; else -> "Candles" }
    override val chartHandelsplatzSuche = "Looking for trading venues"
    override val chartHandelsplatzLeer = "No other trading venues found."
    override val chartVergleich = "Compare"
    override val chartVergleichTitel = "Compare (up to 8)"
    override val chartVergleichSuche = "Search a stock, index or ETF"
    override val chartVergleichHinweis = "When comparing, the chart shows the change in percent since the start of the period. Drawings are hidden then."
    override val chartVergleichVoll = "You can compare at most 8."
    override val chartIndikatorenKurs = "On the price chart"
    override val chartIndikatorenUnten = "Below the chart"
    override fun chartIndikatorName(key: String) = when (key) {
        "sma20" -> "SMA 20"; "sma50" -> "SMA 50"; "sma200" -> "SMA 200"; "ema20" -> "EMA 20"; "ema50" -> "EMA 50"
        "bb" -> "Bollinger Bands"; "keltner" -> "Keltner Channel"; "donchian" -> "Donchian Channel"; "ichimoku" -> "Ichimoku"
        "sar" -> "Parabolic SAR"; "vwap" -> "VWAP"; "volumen" -> "Volume"; "rsi" -> "RSI 14"; "macd" -> "MACD"
        "stoch" -> "Stochastic"; "atr" -> "ATR 14"; "obv" -> "OBV"; "cci" -> "CCI 20"; "williams" -> "Williams %R"
        "mfi" -> "MFI 14"; "adx" -> "ADX 14"; "roc" -> "Momentum (ROC 12)"; "aroon" -> "Aroon 25"; "cmf" -> "Chaikin Money Flow"
        else -> key
    }
    override fun chartIndikatorInfo(key: String) = when (key) {
        "sma20", "sma50", "sma200" -> "Average of the last closing prices. It smooths the line and shows the trend."
        "ema20", "ema50" -> "Average that weights recent prices more and reacts faster."
        "bb" -> "20-period average with two standard deviations above and below: wide = volatile."
        "keltner" -> "EMA 20 with twice the average range (ATR) above and below."
        "donchian" -> "Highest high and lowest low of the last 20 candles. A break counts as a breakout."
        "ichimoku" -> "Japanese trend system: conversion and base lines plus the cloud as support."
        "sar" -> "Dots that trail the trend as a stop. When they switch sides, the trend turns."
        "vwap" -> "Volume-weighted average price of the day, mainly for the daily chart."
        "volumen" -> "Units traded per candle, green on rising, red on falling candles."
        "rsi" -> "Strength of the move from 0 to 100. Above 70 counts as overbought, below 30 as oversold."
        "macd" -> "Gap between two averages (12 and 26) with a signal line. A crossover may signal a turn."
        "stoch" -> "Position of the price in its range of the last 14 candles. Above 80 is high, below 20 low."
        "atr" -> "Average range per candle: how much the price moves."
        "obv" -> "A candle's volume is added when it closes higher than the previous one and subtracted when it closes lower. It confirms trends."
        "cci" -> "Distance from the average. Above +100 is strong, below −100 weak."
        "williams" -> "Position of the price below the high of the last 14 candles. Above −20 is high, below −80 low."
        "mfi" -> "Like the RSI, but weighted by volume. Above 80 is overbought, below 20 oversold."
        "adx" -> "Strength of the trend (above 25 clear). +DI and −DI show the direction."
        "roc" -> "Change in percent versus the price 12 candles ago."
        "aroon" -> "How recent the last high and low are. It shows trends starting and ending."
        "cmf" -> "Buying pressure (above 0) or selling pressure (below 0) from price and volume."
        else -> ""
    }
    override fun chartWerkzeug(key: String) = when (key) {
        "linie" -> "Trendline"; "waagerecht" -> "Horizontal line"; "notiz" -> "Note"; "emoji" -> "Emoji"
        "zurueck" -> "Undo"; else -> "Clear all"
    }
    override fun chartWerkzeugHinweis(key: String, zweiterPunkt: Boolean) = when (key) {
        "linie" -> if (zweiterPunkt) "Tap the end of the line." else "Tap the start of the line."
        "waagerecht" -> "Tap the level of the line."
        "notiz" -> "Tap where the note should go."
        else -> "Tap where the emoji should go."
    }
    override val chartLangDruecken = "Long-press removes a drawing."
    override fun chartAllesLoeschen(name: String) = "Delete all drawings for $name?"
    override val chartKeineDaten = "No price data for this period."
    override val heatmap = "Heatmap"
    override val heatmapDepot = "My portfolio"
    override val heatmapMarkt = "Stock market"
    override val heatmapBranchen = "By sector"
    override val heatmapRegionen = "By region"
    override fun heatmapZaehler(steigen: Int, fallen: Int) = "$steigen up · $fallen down"
    override val heatmapLeer = "No prices reachable right now. Try again later."
    override val heatmapOhneBranche = "Other"
    override val zeitraumEigener = "Custom period"
    override val zeitraumTitel = "Choose period"
    override fun metricLabel(m: Metric) = when (m) {
        Metric.TODAY_PCT -> "Daily trend relative"; Metric.TODAY_ABS -> "Daily trend absolute"
        Metric.SINCE_BUY_PCT -> "Since purchase relative"; Metric.SINCE_BUY_ABS -> "Since purchase absolute"
    }
    override fun metricShort(m: Metric) = if (m.isSinceBuy) "Since purchase" else "Daily trend"
    override fun watchMetricLabel(m: WatchMetric) = when (m) {
        WatchMetric.TODAY_PCT -> "Daily trend relative"; WatchMetric.TODAY_ABS -> "Daily trend absolute"
        WatchMetric.SINCE_ADDED_PCT -> "Since added relative"; WatchMetric.SINCE_ADDED_ABS -> "Since added absolute"
        WatchMetric.SINCE_FIRST_PCT -> "Since first added relative"; WatchMetric.SINCE_FIRST_ABS -> "Since first added absolute"
    }
    override val metricTitle = "Metric"
    override fun typeLabel(t: AssetType) = when (t) { AssetType.STOCK -> "Stock"; AssetType.ETF -> "ETF"; AssetType.CRYPTO -> "Crypto"; AssetType.INDEX -> "Index"; AssetType.COMMODITY -> "Commodity"; AssetType.CURRENCY -> "Currency" }
    override fun inDays(n: Long) = "in $n days"
    override val inOneMonth = "in 1 month"
    override fun inMonths(n: Long) = "in $n months"
    override fun daysAgo(n: Long) = if (n == 1L) "yesterday" else "$n days ago"
    override fun minutesAgo(n: Long) = "$n min ago"
    override fun hoursAgo(n: Long) = "$n h ago"
    override val justNow = "just now"
    override fun weekday(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "Monday"; DayOfWeek.TUESDAY -> "Tuesday"; DayOfWeek.WEDNESDAY -> "Wednesday"; DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"; DayOfWeek.SATURDAY -> "Saturday"; DayOfWeek.SUNDAY -> "Sunday"
    }
    override fun closedOpensToday(time: String) = "Market closed · opens today at $time"
    override fun closedOpensOn(weekday: String, time: String) = "Market closed · opens $weekday at $time"

    override val portfolio = "Portfolio"
    override val cash = "Cash"
    override val openProfile = "Open profile"
    override val brokerage = "Brokerage"
    override val securities = "Securities"
    override val cashAccount = "Cash account"
    override val total = "Total"
    override val reloadPrices = "Reload prices"
    override val provisionalValuation = "Provisionally valued at cost · loading prices"
    override val switchMetric = "Switch metric"
    override val watchlist = "Watchlist"
    override val savingsPlans = "Savings plans"
    override val orders = "Orders"
    override val analytics = "Analytics"
    override val statistics = "Statistics"
    override val investments = "Investments"
    override val discoverInvestments = "Discover investments"
    override val groupStocksEtfs = "Stocks & ETFs"
    override val noWatchlist = "Nothing on the watchlist yet."
    override val knockedOut = "Knocked out"
    override val available = "Available"
    override fun reservedForOrders(amount: String) = "Available · $amount reserved for open orders"
    override val deposit = "Deposit"
    override val withdraw = "Withdraw"
    override val interest = "Interest"
    override val transactions = "Transactions"
    override val noTransactions = "No transactions yet."
    override val incoming = "Incoming"
    override val outgoing = "Outgoing"
    override val since = "since"

    override val year = "Year"
    override val allTime = "All time"
    override val categories = "Categories"
    override fun statNode(id: String) = when (id) {
        "INCOME" -> "Income"; "EXPENSES" -> "Spending"; "INVESTMENTS" -> "Investments"
        "SALES" -> "Sales"; "DEPOSITS" -> "Deposits"; "EARNINGS" -> "Earnings"; "REFUNDS" -> "Refunds"
        "DIVIDENDS" -> "Dividends"; "INTEREST" -> "Interest"
        "FEES" -> "Fees"; "TAXES" -> "Taxes"; "WITHDRAWALS" -> "Withdrawals"
        "PURCHASES" -> "Purchases"; "PLANS" -> "Savings plans"
        else -> id
    }
    override val netCashflow = "Net cash flow"
    override val investRate = "Investment rate"
    override val noStatistics = "There were no bookings in this period."
    override fun bookingsCount(n: Int) = if (n == 1) "1 booking" else "$n bookings"
    override val averagePerMonth = "Avg per month"

    override fun entriesCount(n: Int) = if (n == 1) "1 entry" else "$n entries"
    override val sortByName = "By name"
    override val sortByChange = "By change"
    override val sortByPrice = "By price"
    override val add = "Add"
    override fun addedOn(date: String) = "since $date"
    override val removeFromWatchlist = "Remove from watchlist"
    override val addToWatchlist = "Add to watchlist"

    override val searchPlaceholder = "Search stocks, ETFs and more"
    override val topMovers = "Top movers"
    override val popular = "Popular"
    override val stocks = "Stocks"
    override val etfs = "ETFs"
    override val crypto = "Crypto"
    override val indices = "Indices"
    override val results = "Results"
    override val otherVenues = "Other exchanges"
    override val searching = "Searching securities …"
    override val searchUnavailable = "Search is not reachable right now. Try again later."
    override val noResults = "No matching securities found."
    override val clear = "Clear"

    override val security = "Security"
    override val yourPosition = "Your position"
    override val quantity = "Quantity"
    override val avgPrice = "Average price"
    override val value = "Value"
    override val result = "Result"
    override val information = "Information"
    override val rohstoffTitel = "About this commodity"
    override val etfTitel = "What's inside this ETF"
    override val etfTop = "Largest holdings"
    override val etfBranchen = "Sectors"
    override fun etfKlassen(aktien: String, anleihen: String, bar: String) = listOfNotNull(
        aktien.takeIf { it.isNotBlank() }?.let { "Stocks $it" }, anleihen.takeIf { it.isNotBlank() }?.let { "Bonds $it" },
        bar.takeIf { it.isNotBlank() }?.let { "Cash $it" },
    ).joinToString(" · ")
    override val etfHinweis = "Yahoo lists only the ten largest holdings. An MSCI World ETF and an S&P 500 ETF hold largely the same companies."
    override val etfDepotTitel = "How much is in your portfolio"
    override fun etfDepotZeile(direkt: String, ueber: String, etfs: Int) = listOfNotNull(
        direkt.takeIf { it.isNotBlank() }?.let { "directly $it" },
        "through " + (if (etfs == 1) "1 ETF" else "$etfs ETFs") + " $ueber",
    ).joinToString(", ")
    override val etfDepotHinweis = "Share of your whole portfolio, held directly and through all your ETFs together. Only the ten largest holdings per ETF count, so the true shares are a little higher. German tickers for US stocks (such as APC.DE for Apple) are not added up."
    override fun rohstoffAbschnitte(schluessel: String, termin: Boolean): List<Pair<String, String>> {
        val t = RohstoffTexteEn.TEXTE[schluessel] ?: return emptyList()
        return RohstoffTexteEn.TITEL.zip(t) + if (termin) listOf("About the price shown" to RohstoffTexteEn.TERMIN) else emptyList()
    }
    override val keyStats = "Key statistics"
    override val bid = "Bid"
    override val ask = "Ask"
    override val spread = "Spread"
    override val openPrice = "Open"
    override val dayHigh = "Day high"
    override val dayLow = "Day low"
    override val prevClose = "Previous close"
    override val high52 = "52-week high"
    override val low52 = "52-week low"
    override val marketCap = "Market cap"
    override val enterpriseValue = "Enterprise value"
    override val peRatio = "P/E ratio"
    override val forwardPe = "Forward P/E"
    override val eps = "Earnings per share"
    override val dividendYield = "Dividend yield"
    override val volume = "Volume"
    override fun priceIn(ccy: String) = "Price in $ccy"
    override val volatility1y = "Volatility (1 year)"
    override val beta = "Beta"
    override val isin = "ISIN"
    override val isinCopied = "ISIN copied"
    override val isinKopieren = "Copy ISIN"
    override fun kennzahlErklaerung(label: String): String? = when (label) {
        bid -> "The price at which you can sell the stock right now."
        ask -> "The price at which you can buy the stock right now. It is a little above the bid."
        spread -> "The gap between the buying and the selling price. You lose this much if you buy and sell again straight away. The smaller it is, the cheaper the trade."
        openPrice -> "The first price of today's trading day."
        dayHigh -> "The highest price today."
        dayLow -> "The lowest price today."
        prevClose -> "The closing price of the last trading day. Today's rise or fall is measured against it."
        high52 -> "The highest price in the last twelve months. Together with the low it shows how much the stock moved in a year."
        low52 -> "The lowest price in the last twelve months. Together with the high it shows how much the stock moved in a year."
        marketCap -> "What all of the company's shares are worth together on the stock market: price times number of shares. It is used to compare the size of companies."
        enterpriseValue -> "Market value plus debt minus cash. Roughly what you would have to pay to buy the whole company including its debt."
        peRatio -> "Price divided by the earnings per share of the last twelve months. A P/E of 15 means you pay 15 years of earnings. A low value can mean cheap, a high one usually means that a lot of growth is expected."
        forwardPe -> "Like the P/E ratio, but with the earnings analysts expect for the next twelve months."
        eps -> "The company's profit over the last twelve months divided by the number of its shares. If it is negative, the company made a loss."
        dividendYield -> "The yearly payout relative to the price. 3 % means: for 100 € in the stock you get about 3 € a year, as long as the dividend stays the same."
        volume -> "How many shares were traded today. A high volume shows that a lot is going on."
        volatility1y -> "How much the price moved over the last year. Above 40 % is very bumpy, below 20 % rather calm."
        beta -> "How strongly the stock moves with the market on average. 1 means: when the market rises or falls by 1%, the stock moves the same on average. 2 means twice as strongly, 0.5 half as strongly. A stock can swing a lot and still have a small beta if its moves have little to do with the market."
        isin -> "The international identification number of the security. It is unique worldwide, so any bank can find it."
        kind -> "The type of security, for example stock, ETF or cryptocurrency."
        sector -> "The broad business area of the company, for example technology or health care."
        industry -> "The more specific industry within the sector."
        country -> "The country where the company has its headquarters."
        employees -> "How many people work for the company, according to its latest report."
        exchange -> "The exchange the displayed price comes from."
        bkKuv -> "Market value divided by revenue over the last twelve months. Useful for companies that make no profit yet. A P/S of 2 means the company costs two years of revenue. What counts as high depends a lot on the sector."
        priceToBook -> "Price divided by book value per share, the equity on the balance sheet. Below 1 the share costs less than the equity behind it according to the balance sheet."
        bkEvEbitda -> "Enterprise value (market value plus debt minus cash) divided by earnings before interest, taxes, depreciation and amortisation. Debt is included, so companies with a lot or little debt compare better."
        bkPeg -> "P/E divided by expected earnings growth. Around 1 counts as fairly valued for its growth, well above as expensive. If Yahoo has no value, the stock finder uses the trailing twelve-month P/E and the earnings growth to next year."
        bkFcfRendite -> "Free cash flow of the last twelve months (operating cash flow minus investment) divided by market value. This much money is left over each year, measured against what the whole company costs on the market."
        returnOnEquity -> "Profit divided by equity. Shows how well the company uses its owners' money. Above 15% counts as strong."
        bkGesamtkapitalRendite -> "Profit divided by all of the company's assets. Shows how much it gets out of its plants, equipment and cash."
        grossMargin -> "Share of revenue left after the direct cost of goods. A high gross margin points to pricing power."
        operatingMargin -> "Share of revenue left after all operating costs, before interest and taxes."
        bkEbitdaMarge -> "Share of revenue left before interest, taxes, depreciation and amortisation."
        profitMargin -> "Share of revenue that ends up as profit."
        bkVerschuldung -> "Debt divided by equity. 1 means as much debt as equity. High values make a company vulnerable when rates rise or business weakens."
        bkLiquiditaet -> "Current assets divided by current liabilities. Above 1 the company can pay next year's bills from what it has."
        totalCash -> "Cash and assets the company can sell quickly."
        totalDebt -> "All interest-bearing debt of the company."
        freeCashflow -> "Money left after spending on running the business and on investments. From it a company pays dividends, buys back shares or repays debt."
        dividendPerShare -> "What the company pays out per share each year."
        payoutRatio -> "Share of profit paid out as dividends. Above 100% the company pays out more than it earns."
        bkDividendeFuenf -> "The average dividend yield of the last five years. If today's is well above, the share got cheaper or the dividend rose."
        bkDividendenWachstum -> "How much the dividend per share has grown per year in recent years."
        bkLeerQuote -> "Share of freely traded shares that are sold short. Short sellers bet on falling prices. Above 10% counts as high."
        bkLeerTage -> "How many trading days it would take at normal volume for all short sellers to buy back their shares."
        bkInsider -> "Share of stock held by founders, management and the board."
        bkInstitutionen -> "Share of stock held by funds, insurers, banks and other large investors."
        bkAktien -> "How many shares of the company exist."
        bkAktienVeraenderung -> "How the number of shares changed from the year before. Fewer shares usually means buybacks, which raises your stake in the company. More shares dilute it."
        bkZielSpanne -> "The lowest and highest analyst price target for the next twelve months."
        bkZielMedian -> "Half of the analysts have a higher price target, the other half a lower one."
        bkGewinnErwartet -> "The earnings per share analysts expect for the next twelve months."
        bkGewinnWachstumJahr -> "How much analysts expect earnings per share to grow in the current fiscal year."
        bkGewinnWachstumFolge -> "How much analysts expect earnings per share to grow in the next fiscal year."
        bkUmsatzWachstumFolge -> "How much analysts expect revenue to grow in the next fiscal year."
        bkNaechsteZahlen -> "When the company publishes its next results. The price often moves more around that day."
        bkJahresVeraenderung -> "How the price changed over the last twelve months, in the trading currency."
        revenueGrowth -> "Change in revenue in the last quarter against the same quarter a year earlier."
        kvZeile("KURSZIEL") -> "Gap between the analysts' average price target and the current price. Price targets are often wrong and frequently follow the price."
        bkKursCashflow -> "Market value divided by operating cash flow of the last twelve months. The lower the figure, the less you pay for the cash the business brings in."
        bkZinsdeckung -> "Earnings before interest and taxes divided by the interest expense of the last fiscal year. Shows how many times the company earns its interest. Below 2 it gets tight. Without reported interest expense, the field stays empty."
        bkRoic -> "Operating profit after tax divided by invested capital. Shows how much the company makes from the money owners and lenders have put in. From 15% the value counts as strong. Invested capital here is equity plus debt, cash is not deducted."
        bkOwnerEarnings -> "Simplified after Buffett: operating cash flow minus all investment in equipment, divided by market value. Buffett only deducts the investment needed to maintain the business. For growing companies the value therefore tends to be too low."
        else -> null
    }
    override val kind = "Type"
    override val sector = "Sector"
    override val industry = "Industry"
    override val country = "Country"
    override val employees = "Employees"
    override val website = "Website"
    override val exchange = "Exchange"
    override val priceAlerts = "Price alerts"
    override fun nActive(n: Int) = "$n active"
    override val derivatives = "Derivatives"
    override fun leverageRange(custom: Int, factor: Int) = "Leverage up to $custom×, factors up to $factor×"
    override val news = "News"
    override val events = "Events"
    override val financials = "Financials"
    override val yearly = "Yearly"
    override val quarterly = "Quarterly"
    override val insights = "Insights"
    override val analysts = "Analysts"
    override fun fromEstimates(n: Int) = " from $n estimates"
    override fun targetPrice(price: String) = ", target price $price"
    override val toTarget = "to target"
    override val revenue = "Revenue"
    override val ebitda = "EBITDA"
    override val netIncome = "Net income"
    override val grossProfit = "Gross profit"
    override val operatingIncome = "Operating income"
    override val savingsPlan = "Savings plan"
    override val eventEarnings = "Earnings"
    override val eventExDividend = "Ex-dividend"
    override val eventDividendPayment = "Dividend payment"
    override val pastEvents = "Past events"
    override val upcomingEvents = "Upcoming"
    override val noEvents = "No dates known."
    override fun epsActualVsEstimate(actual: String, estimate: String) = "EPS $actual · expected $estimate"
    override val description = "Description"
    override val sourceWikipedia = "Source: Wikipedia, licence CC BY-SA 4.0"
    override val sourceYahoo = "Source: Yahoo Finance"
    override fun recommendation(key: String) = when (key.lowercase()) {
        "strong_buy" -> "Strong buy"; "buy" -> "Buy"; "hold" -> "Hold"; "sell" -> "Sell"; "strong_sell" -> "Strong sell"; else -> "No clear recommendation"
    }
    override val knockedOutBanner = "Knock-out event"
    override fun knockedOutSince(date: String, residual: String) = "The underlying touched the knock-out barrier on $date. The product has ended and will be repaid at its residual value of $residual per unit."

    override val income = "Income statement"
    override val balance = "Balance sheet"
    override val cashflow = "Cash flow"
    override val margins = "Margins"
    override val grossMargin = "Gross margin"
    override val operatingMargin = "Operating margin"
    override val profitMargin = "Net margin"
    override val totalCash = "Cash"
    override val totalDebt = "Debt"
    override val freeCashflow = "Free cash flow"
    override val operatingCashflow = "Operating cash flow"
    override val returnOnEquity = "Return on equity"
    override val totalAssets = "Total assets"
    override val totalLiabilities = "Liabilities"
    override val equity = "Equity"
    override val revenueGrowth = "Revenue growth"
    override val earningsGrowth = "Earnings growth"
    override val payoutRatio = "Payout ratio"
    override val dividendPerShare = "Dividend per share"
    override val priceToBook = "Price-to-book"
    override val noFinancials = "No financial data available for this security."
    override fun reportingCurrency(ccy: String) = "Figures in $ccy"
    override val epsHistory = "Earnings per share by quarter"
    override val estimate = "Estimate"
    override val actual = "Actual"

    override fun newsSource(source: String) = "Source: $source"
    override val openInBrowser = "Open in browser"
    override val showOriginal = "Show original page"
    override val showText = "Show text only"
    override val articleLoading = "Loading article …"
    override val articleNoText = "The app cannot read the text of this page."

    override val calToggle = "Trading calendar"
    override fun calRealizedIn(month: String) = "Realized in $month"
    override val calPrevMonth = "Previous month"
    override val calNextMonth = "Next month"
    override val calWeekdays = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    override val calRealized = "Realized"
    override val calTrades = "Trades"
    override fun calWinsLosses(wins: Int, losses: Int) = "$wins up · $losses down"
    override fun calExpectedAmount(amount: String) = "expected $amount"
    override val calLegendReceived = "Dividend received"
    override val calLegendExpected = "Dividend expected"
    override val calClosedTrades = "Closed trades"
    override val calDayResult = "Day result"
    override val calFees = "Fees"
    override val calAfterCosts = "After fees and taxes"
    override val calPositions = "Closed positions"
    override val calTradeExpiry = "Expiry"
    override fun calTradeSell(qty: String, price: String) = "Sale · $qty sh. at $price"
    override val calDivExpected = "expected, before tax"
    override fun calDivReceived(qty: String, perShare: String) = "$qty sh. × $perShare · after tax"

    override val overview = "Overview"
    override val amount = "Amount"
    override val shares = "Shares"
    override val everything = "All"
    override val market = "Market"
    override val limit = "Limit"
    override val stop = "Stop"
    override val trailingStop = "Trailing stop"
    override val limitPrice = "Limit price"
    override val stopPrice = "Stop price"
    override val trailDistance = "Distance"
    override val setValue = "set"
    override val validity = "Validity"
    override fun validityLabel(v: Validity) = if (v == Validity.DAY) "Good for day" else "360 days"
    override val limitBuyHint = "Executes as soon as the price reaches or falls below the limit."
    override val limitSellHint = "Executes as soon as the price reaches or exceeds the limit."
    override val stopBuyHint = "Triggers as soon as the price rises above the stop."
    override val stopSellHint = "Triggers as soon as the price falls below the stop."
    override val trailingSellHint = "The stop price follows the price upwards at the chosen distance and stays put when the price falls. Once the price drops below the stop, the position is sold at market."
    override val trailingBuyHint = "The stop price follows the price downwards at the chosen distance. Once the price rises above the stop, the order buys at market."
    override val closedOrderHint = " · You can place limit, stop and trailing orders now."
    override val enterLimit = "Enter limit price"
    override val enterStop = "Enter stop price"
    override val enterTrail = "Enter distance in percent"
    override fun corresponds(qty: String) = "equals $qty pcs"
    override fun correspondsAmount(amount: String) = "equals $amount"
    override val askPrice = "Ask price"
    override val bidPrice = "Bid price"
    override val inDepot = "In portfolio"
    override val notEnoughCash = "Your balance is not sufficient."
    override val notEnoughShares = "You do not hold that many units."
    override val notEnoughForFee = "Balance and proceeds do not cover the fee."
    override val tradeType = "Order"
    override fun tradeTypeValue(side: String, type: OrderType) = "$side · " + when (type) {
        OrderType.MARKET -> "Market"; OrderType.LIMIT -> "Limit"; OrderType.STOP -> "Stop"; OrderType.TRAILING_STOP -> "Trailing stop"
    }
    override val piecesLabel = "Units"
    override val counterValue = "Value"
    override val estCounterValue = "Estimated value"
    override val fee = "External cost fee"
    override val venue = "Venue"
    override val bestPrice = "Best price"
    override val productCosts = "Product costs p.a."
    override val derivativeRisk = "Leveraged products can lead to a total loss of the stake. The ex-ante cost information underlies this overview."
    override val cashAfter = "Balance afterwards"
    override val notePlaceholder = "Note (optional), e.g. your reason for the order"
    override val placeOrder = "Place order"
    override val orderNotExecuted = "The order was not executed. Check the current price and your balance."
    override val initialStop = "Initial stop price"

    override fun ordersOpenHint(n: Int) = "$n open · limit, stop and trailing orders execute during trading hours"
    override val noOpenOrders = "No open orders."
    override val doneOrders = "Completed"
    override val cancelOrder = "Cancel"
    override fun validUntil(ts: String) = "Valid until $ts"
    override fun orderStatus(s: OrderStatus) = when (s) {
        OrderStatus.OPEN -> "Open"; OrderStatus.FILLED -> "Executed"; OrderStatus.CANCELLED -> "Cancelled"
        OrderStatus.REJECTED -> "Rejected"; OrderStatus.EXPIRED -> "Expired"
    }
    override fun trailingInfo(pct: String, stop: String) = "Trailing $pct · stop currently $stop"
    override fun reason(code: String) = when (code) {
        "EXPIRED" -> "Validity expired"; "PRODUCT_GONE" -> "Product no longer tradable"
        else -> error(code, "")
    }

    override val plan = "plan"
    override val plans = "plans"
    override fun planCount(n: Int) = "$n ${if (n == 1) "plan" else "plans"}"
    override val planAmountHint = "per execution · min. €1, max. €10,000"
    override val interval = "Interval"
    override fun intervalLabel(i: PlanInterval) = when (i) {
        PlanInterval.WEEKLY -> "Weekly"; PlanInterval.BIWEEKLY -> "Twice a month"; PlanInterval.MONTHLY -> "Monthly"; PlanInterval.QUARTERLY -> "Quarterly"
    }
    override fun intervalSubtitle(i: PlanInterval) = when (i) {
        PlanInterval.WEEKLY -> "every 7 days"; PlanInterval.BIWEEKLY -> "on the 2nd and 16th"; PlanInterval.MONTHLY -> "once a month"; PlanInterval.QUARTERLY -> "every three months"
    }
    override val execution = "Execution"
    override val startOfMonth = "Start of month"
    override val midOfMonth = "Middle of month"
    override fun onDay(d: Int) = "on the ${ordinal(d)}"
    override val savingsRate = "Amount"
    override val executionDay = "Execution day"
    override val executionDays = "Execution days"
    override val firstExecution = "First execution"
    override val paymentMethod = "Payment method"
    override val planNote = "Savings plans execute as market orders. If the balance is insufficient, execution is retried the next day."
    override val create = "Create"
    override val managePlan = "Manage savings plan"
    override val status = "Status"
    override val paused = "Paused"
    override val active = "Active"
    override val nextExecution = "Next execution"
    override val executions = "Executions"
    override val investedSoFar = "Invested so far"
    override val lastFailure = "Last failure"
    override val changeRate = "Change amount"
    override val changeInterval = "Change interval"
    override val planUhrzeit = "Time"
    override val planUhrzeitAendern = "Change time"
    override val planUhrzeitAuto = "Automatic"
    override val planUhrzeitAutoText = "On the execution day, as soon as a current price is available"
    override val planUhrzeitHinweis = "Plans run while the app is running: at the chosen time or, if it is closed then, the next time you open it. Stocks and ETFs need a current price, i.e. an open trading venue."
    override fun planUhrzeitWert(uhr: String) = uhr
    override val resume = "Resume"
    override val pause = "Pause"
    override val deletePlanTitle = "Delete savings plan?"
    override val deletePlanText = "Units already bought stay in your portfolio."
    override val noPlans = "No savings plan yet"
    override fun perMonthPlans(amount: String, n: Int) = "$amount per month · ${planCount(n)}"
    override val createPlan = "Create savings plan"
    override fun nextOn(date: String) = "next $date"
    override fun investedAmount(amount: String) = "$amount invested"

    override val createAlert = "Create price alert"
    override val enterTargetPrice = "Enter target price"
    override fun alertRises(price: String, pct: String) = "Alert once the price rises to $price (+$pct)"
    override fun alertFalls(price: String, pct: String) = "Alert once the price falls to $price (−$pct)"
    override val targetEqualsPrice = "The target equals the current price"
    override val setAlert = "Set alert"
    override val whenRises = "when the price rises"
    override val whenFalls = "when the price falls"
    override fun setAt(price: String) = "set at $price"
    override fun alertCount(n: Int) = "$n ${if (n == 1) "alert" else "alerts"}"
    override val alertsHint = "Price alerts are created on a security's page."
    override val mpTitel = "Bull or bear market"
    override fun mpGesamt(bullen: Int, alle: Int) = "$bullen of $alle markets in a bull market without correction"
    override fun mpIndex(schluessel: String) = when (schluessel) {
        "sp500" -> "S&P 500"; "nasdaq" -> "Nasdaq 100"; "world" -> "MSCI World (ETF)"; "dax" -> "DAX"; "stoxx" -> "Euro Stoxx 50"
        "nikkei" -> "Nikkei 225"; "bitcoin" -> "Bitcoin"; else -> schluessel
    }
    override fun mpPhase(art: String, korrektur: Boolean, seit: String) = when {
        art == "BAER" -> "Bear market since $seit"
        korrektur -> "Correction in a bull market since $seit"
        else -> "Bull market since $seit"
    }
    override fun mp200(wert: String) = "$wert vs. 200-day line"
    override fun mpDetail(art: String, vomTief: String, golden: Boolean?, kreuzSeit: String?) = listOfNotNull(
        if (art == "BAER") "$vomTief above the low of the bear market. At 20% it is considered over." else "$vomTief above the low before the rise. A drop of 20% from the high would start a bear market.",
        when (golden) {
            true -> "Golden cross" + (kreuzSeit?.let { " on $it" } ?: "") + ": the 50-day line is above the 200-day line."
            false -> "Death cross" + (kreuzSeit?.let { " on $it" } ?: "") + ": the 50-day line is below the 200-day line."
            null -> null
        },
        "The line shows two years, the horizontal line the threshold for a change.",
    ).joinToString(" ")
    override val mpHinweis = "Rules of thumb for the phases:\n\n" +
        "• Bear market: an index closes 20% below its last high\n" +
        "• Bull market: it is 20% above its low again\n" +
        "• Correction: a decline of 10 to 20% within a bull market\n\n" +
        "The light shows bull (green), correction (yellow) and bear (red). The 200-day line shows the long-term trend. When the 50-day line crosses it upwards it is called a golden cross, downwards a death cross. Calculated from ten years of daily closes."
    override val cotTitel = "Futures positioning"
    override fun cotMarkt(schluessel: String) = when (schluessel) {
        "sp500" -> "S&P 500"; "nasdaq" -> "Nasdaq 100"; "gold" -> "Gold"; "oel" -> "Crude oil (WTI)"; "euro" -> "Euro vs. dollar"
        "anleihen" -> "10-year US Treasuries"; "bitcoin" -> "Bitcoin"; else -> schluessel
    }
    override fun cotQuelle(stand: String) = "Source: CFTC, as of $stand"
    override val lpTitel = "Open short positions"
    override fun lpSumme(anteil: String, anzahl: Int) =
        if (anzahl == 1) "One fund is betting on falling prices with $anteil of the share capital" else "$anzahl funds are betting on falling prices with $anteil of the share capital combined"
    override fun lpVeraenderung(aenderung: String) = "$aenderung pts compared with four weeks ago"
    override val lpKeine = "No published position right now."
    override fun lpSeit(tag: String) = "since $tag"
    override fun lpQuelle(land: String, stand: String) = when (land) {
        "SE" -> "Source: Finansinspektionen, Blankningsregistret (fi.se), as of $stand"
        "IT" -> "Source: Consob, net short positions (consob.it), imported by you, as of $stand"
        "DE" -> "Source: Bundesanzeiger, net short positions (bundesanzeiger.de), imported by you, as of $stand"
        "GB" -> "Source: Financial Conduct Authority, aggregated net short positions (fca.org.uk), imported by you, as of $stand"
        else -> "Source: Autorité des marchés financiers via data.gouv.fr (Licence Ouverte 2.0), as of $stand"
    }
    override fun lpGesamt(anteil: String) = "Short sellers are betting on falling prices with $anteil of the share capital combined"
    override fun lpHinweis(land: String) = if (land == "GB") "Selling a stock short is a bet on falling prices. The UK regulator publishes only the total of all notified positions per share (from 0.2% of the share capital), without names. The line shows the total over the last year. If short sellers have to cover, the price can rise." else "Selling a stock short is a bet on falling prices. In the EU every net short position of 0.5% of the share capital or more is published with the holder's name, so the real total is usually higher. The line shows the total of published positions over the last year. If short sellers have to cover, the price can rise."
    override val fdaTitel = "Approvals and recalls (FDA)"
    override val fdaZulassungen = "Approvals, 12 months"
    override val fdaRueckrufe = "Recalls, 12 months"
    override fun fdaArt(art: String, vorrang: Boolean) = when (art) {
        "NEU" -> "New active ingredient"; "ORIGINAL" -> "New approval"; else -> "New indication"
    } + if (vorrang) " · priority review" else ""
    override fun fdaKlasse(klasse: Int, geraet: Boolean, laufend: Boolean) = listOfNotNull(
        when (klasse) { 1 -> "Class I (serious risk)"; 2 -> "Class II"; 3 -> "Class III"; else -> null },
        if (geraet) "Medical device" else "Drug",
        if (laufend) "ongoing" else null,
    ).joinToString(" · ")
    override val fdaKeine = "No approvals or recalls found in the last twelve months."
    override val fdaQuelle = "Source: U.S. Food and Drug Administration via openFDA (api.fda.gov). openFDA is not intended for medical decisions."
    override val fdaHinweis = "The US Food and Drug Administration (FDA) publishes recalls of drugs and medical devices every week. Class I means the product can cause serious harm to health. Entries are matched by company name and known subsidiaries, so single entries may be missing."
    override fun euQuelle(land: String, stand: String) = when (land) {
        "FR" -> "Source: ©Autorité des marchés financiers – droits réservés, BDIF (bdif.amf-france.org)"
        "NL" -> "Source: Autoriteit Financiële Markten (AFM), register of managers' transactions (afm.nl)"
        "GB" -> "Source: Financial Conduct Authority, National Storage Mechanism (data.fca.org.uk)"
        "CH" -> "Source: SIX Exchange Regulation, management transactions without names (ser-ag.com)"
        "IN" -> "Source: BSE, SEBI PIT disclosures (bseindia.com), imported by you"
        "HK" -> "Source: HKEX, Disclosure of Interests (di.hkex.com.hk)"
        "CA" -> "Source: SEDI, weekly summaries of the Canadian Securities Administrators (sedi.ca), imported by you"
        "AU" -> "Source: ASX, Appendix 3Y (asx.com.au), amounts read from the forms"
        "DK", "FI", "IS", "EE", "LV", "LT" -> "Source: Nasdaq Nordic and Baltic, issuer announcements (news.eu.nasdaq.com), amounts read from the forms"
        "CZ" -> "Source: ČNB, central register of regulated information (oam.cnb.cz)"
        "LU" -> "Source: Luxembourg Stock Exchange, OAM (luxse.com), amounts read from the forms"
        "IT" -> "Source: 1Info SDIR (1info.it) or imported by you, amounts read from the forms"
        "CY" -> "Source: Cyprus Stock Exchange, Public OAM (publicoam.cse.com.cy), amounts read from the forms"
        "HU" -> "Source: Magyar Nemzeti Bank, Tőkepiaci közzétételek (kozzetetelek.mnb.hu), amounts read from the forms"
        "SE", "" -> "Source: Finansinspektionen, Insynshandel (fi.se)"
        else -> "Source: notifications under Art. 19 MAR imported by you"
    } + if (stand.isNotBlank()) ", as of $stand" else ""
    override val euSedi = "Canadian insiders report their trades to SEDI. The database can only be searched in a browser."
    override val euSediLink = "Open SEDI"
    override fun euArt(art: String) = when (art) { "K" -> "Buy"; "V" -> "Sell"; else -> "Other" }
    override fun euRolle(r: String) = when (r) { "S" -> "Senior management"; "F" -> "Executive"; "A" -> "Board member"; else -> "Closely associated person" }
    override val bbDafuer = "In favour"
    override val bbDagegen = "Against"
    override val bbHinweis = "Put together automatically from headlines of the last 60 days and key figures, the evidence below shows the reason."
    override val bbWaage = "Balance"
    override fun bbStufe(stufe: Int) = listOf("clearly against", "rather against", "balanced", "rather for", "clearly for").getOrElse(stufe) { "" }
    override fun bbWaageSatz(stufe: Int) = listOf(
        "The points against weigh much more.",
        "The points against weigh somewhat more.",
        "Points for and against roughly balance out.",
        "The points for weigh somewhat more.",
        "The points for weigh much more.",
    ).getOrElse(stufe) { "" }
    override val bbWaageHinweis = "Each point has a weight:\n• three times: serious issues such as accounting fraud or insolvency\n• twice: profit warnings, investigations, losses and raised guidance\n• half: volatility and cyclical sector\n\nSeveral headlines on the same topic count a little more."
    override fun bbAlle(n: Int) = "Show all $n"
    override fun bbWarum(schluessel: String) = when (schluessel) {
        "PLEITE" -> "In an insolvency shareholders usually lose almost everything, because creditors are paid first."
        "BILANZ" -> "If the numbers are wrong, every valuation is worthless. Accounting scandals such as Wirecard often end in a total loss."
        "ERMITTLUNG" -> "Investigations can lead to large fines, conditions or bans on business and often drag on for years."
        "KLAGE" -> "Lawsuits cost money and attention. They become dangerous when large sums or the core business are at stake."
        "WARNUNG" -> "When a company cuts its own forecast, business is worse than planned. Further disappointments often follow."
        "ABSTUFUNG" -> "Analysts adjust their view to new information. Several downgrades show that sentiment is turning."
        "RUECKRUF" -> "Recalls, data leaks and outages cost money and customer trust."
        "PERSONAL" -> "A sudden departure at the top can point to problems. Job cuts often show a weak business but can also lower costs."
        "UEBERNAHME" -> "A takeover target usually gets a premium on its price. If the deal falls through, the price often drops back."
        "PROGNOSE" -> "When a company raises its own forecast, business is better than planned. Management knows the numbers best."
        "ZAHLEN" -> "Beating expectations often lifts the price, because analysts raise their estimates."
        "AUFSTUFUNG" -> "Analysts upgrade when they see more potential. They are often not very accurate, though."
        "ZULASSUNG" -> "An approval opens a new market. For pharma and medtech companies it often decides several years of revenue."
        "AUFTRAG" -> "New orders and partners secure future revenue, especially when they are large relative to total revenue."
        "RUECKKAUF" -> "Buybacks and higher dividends return money to shareholders and show the company earns enough."
        "KI" -> "Companies benefiting from the AI build-out are growing fast. Expectations are often already in the price, though."
        "ANALYSTEN_POSITIV" -> "The price target is the average of the estimates. It is an opinion and often wrong."
        "ANALYSTEN_SKEPTISCH" -> "If the target is below the price, most analysts consider the stock fully valued."
        "WACHSTUM" -> "Growing revenue is the basis for future profits."
        "SCHRUMPFT" -> "Falling revenue means lost customers or lower prices. Profits can then only be held by cutting costs."
        "GEWINNWACHSTUM" -> "In the long run the share price follows earnings."
        "GEWINN_FAELLT" -> "Falling earnings weigh on dividend and price."
        "MARGE_HOCH" -> "A high margin shows pricing power: the company earns a lot on every unit of revenue."
        "VERLUST" -> "A loss-making company needs outside money, through loans or new shares. That dilutes existing holdings."
        "NETTOKASSE" -> "More cash than debt makes a company independent of banks and helps in crises."
        "SCHULDEN" -> "High debt becomes expensive when interest rates rise or business weakens."
        "CASHFLOW_POSITIV" -> "Free cash flow is the money left after all investment. Dividends and buybacks come from it."
        "CASHFLOW_NEGATIV" -> "The company spends more money than it takes in and will need fresh capital over time."
        "UEBERTROFFEN" -> "Regularly beating expectations shows careful planning and reliable delivery."
        "VERFEHLT" -> "Missing expectations repeatedly costs investor trust."
        "GEWINNE_STEIGEN" -> "A lower forward P/E means analysts expect rising earnings."
        "BEWERTUNG_HOCH" -> "A high P/E means a lot of future growth is already priced in. Disappointments then push the price down sharply."
        "SCHWANKUNG" -> "A beta above 1 means the stock moves more than the market, up and down."
        "ZYKLISCH" -> "In a recession the earnings of such companies often drop sharply."
        "DIVIDENDE_UNGEDECKT" -> "Paying out more than it earns puts the dividend at risk of a cut."
        "INSIDER_KAUFEN" -> "Management knows the company best. Buying with their own money is seen as a sign of confidence."
        "INSIDER_VERKAUFEN" -> "Sales often have private reasons. Many insiders selling at once is a warning sign."
        "LEERVERKAUF_HOCH" -> "Short sellers bet on falling prices. Many of them signal doubt but can also trigger a quick rise when they have to cover."
        "FDA_ZULASSUNG" -> "New approvals bring new products to market."
        "FDA_RUECKRUF" -> "Class I recalls involve serious risks to patients and can be costly."
        "NAHE_HOCH" -> "Stocks that did well recently tend to keep going for a while on average (momentum). There is no guarantee."
        "WEIT_UNTER_HOCH" -> "A steep fall shows the market sees problems. It does not automatically make the stock cheap."
        "DIVIDENDE_SOLIDE" -> "A covered dividend brings regular income and is more likely to last when business slows."
        "RENDITE_HOCH" -> "Return on equity shows how much the company makes from shareholders' money. Above 20% is very good."
        "BEWERTUNG_NIEDRIG" -> "A low P/E can be an opportunity. Often the market has good reasons for the discount, though."
        else -> ""
    }
    override fun bbThema(thema: String) = when (thema) {
        "PLEITE" -> "Bankruptcy, going concern doubts or a possible delisting in the news"
        "BILANZ" -> "Accounting allegations, a short seller report or delayed filings"
        "ERMITTLUNG" -> "Investigations by authorities or antitrust proceedings"
        "KLAGE" -> "Lawsuits or class actions against the company"
        "WARNUNG" -> "Profit warning, lower guidance or missed expectations"
        "ABSTUFUNG" -> "Analysts downgrade or cut price targets"
        "RUECKRUF" -> "Recall, data breach, cyber attack or outage"
        "PERSONAL" -> "Departure at the top or job cuts"
        "UEBERNAHME" -> "Takeover under discussion"
        "PROGNOSE" -> "Guidance raised"
        "ZAHLEN" -> "Results above expectations or records"
        "AUFSTUFUNG" -> "Analysts upgrade or raise price targets"
        "ZULASSUNG" -> "Approval received"
        "AUFTRAG" -> "New contracts or partnerships"
        "RUECKKAUF" -> "Share buyback or higher dividend"
        "KI" -> "Demand driven by artificial intelligence"
        else -> thema
    }
    override fun bbKennzahl(kennzahl: String, w: List<String>) = when (kennzahl) {
        "ANALYSTEN_POSITIV" -> "Analysts see upside: target ${w[0]} (${w[1]}), ${w[2]} estimates"
        "ANALYSTEN_SKEPTISCH" -> "Analysts are sceptical: target ${w[0]} (${w[1]}), ${w[2]} estimates"
        "WACHSTUM" -> "Revenue grows by ${w[0]}"
        "SCHRUMPFT" -> "Revenue shrinks by ${w[0]}"
        "GEWINNWACHSTUM" -> "Earnings grow by ${w[0]}"
        "GEWINN_FAELLT" -> "Earnings fall by ${w[0]}"
        "MARGE_HOCH" -> "High profit margin of ${w[0]}"
        "VERLUST" -> "Loss-making (margin −${w[0]})"
        "NETTOKASSE" -> "More cash than debt, net ${w[0]}"
        "SCHULDEN" -> "High net debt of ${w[0]}, ${w[1]} of market value"
        "CASHFLOW_POSITIV" -> "Free cash flow of ${w[0]}, ${w[1]} of market value"
        "CASHFLOW_NEGATIV" -> "Burns cash: free cash flow −${w[0]}"
        "UEBERTROFFEN" -> "Earnings per share above expectations in ${w[0]} of ${w[1]} quarters"
        "VERFEHLT" -> "Earnings per share below expectations in ${w[0]} of ${w[1]} quarters"
        "GEWINNE_STEIGEN" -> "Earnings expected to rise: P/E ${w[0]}, forward ${w[1]}"
        "BEWERTUNG_HOCH" -> "High valuation with a P/E of ${w[0]}"
        "SCHWANKUNG" -> "Moves more than the market (beta ${w[0]})"
        "ZYKLISCH" -> "Cyclical industry: earnings depend strongly on the economy"
        "DIVIDENDE_UNGEDECKT" -> "Dividend not covered by earnings (payout ${w[0]})"
        "INSIDER_KAUFEN" -> "Insiders are buying (insider trend ${w[0]})"
        "LEERVERKAUF_HOCH" -> "Many short sellers: ${w[0]} of the share capital sold short"
        "FDA_ZULASSUNG" -> if (w[0] == "1") "One new product approved by the FDA in the last twelve months" else "${w[0]} new products approved by the FDA in the last twelve months"
        "FDA_RUECKRUF" -> if (w[0] == "1") "One FDA Class I recall (serious risk) in the last twelve months" else "${w[0]} FDA Class I recalls (serious risk) in the last twelve months"
        "NAHE_HOCH" -> "Price near its 52-week high: the uptrend is intact"
        "WEIT_UNTER_HOCH" -> "Price ${w[0]} below its 52-week high: the downtrend continues"
        "DIVIDENDE_SOLIDE" -> "Dividend yield ${w[0]}, covered by earnings (payout ${w[1]})"
        "RENDITE_HOCH" -> "High return on equity of ${w[0]}"
        "BEWERTUNG_NIEDRIG" -> "Low valuation: forward P/E ${w[0]}"
        "INSIDER_VERKAUFEN" -> "Insiders are selling (insider trend ${w[0]})"
        else -> kennzahl
    }
    override val vdKaeufe1 = "Buy"
    override val vdVerkaeufe1 = "Sell"
    override val usHinweis = "Officers, directors and holders of more than 10 % report purchases and sales of their company's shares to the SEC (Form 4). Shown are open market or private purchases and sales of the last 18 months, without grants, option exercises, tax withholding and gifts. Sales under trading plans (Rule 10b5-1) are marked."
    override val usOhneKontakt = "New reports arrive with every version of the app. With a contact address under Access the app fetches them itself."
    override fun usQuelle(stand: String) = "Source: U.S. Securities and Exchange Commission (EDGAR, Form 4)" + if (stand.isNotBlank()) ", as of $stand" else ""
    override fun usRolle(r: String) = when (r) { "S" -> "CEO, CFO, president or chair"; "F" -> "Officer"; "A" -> "Director"; else -> "Major holder or other" }
    override fun usBestandDanach(stueck: String, aenderung: String?) = "Holding after: $stueck shares" + (aenderung?.let { " ($it)" } ?: "")
    override val usBestaendeTitel = "How much insiders hold"
    override fun usStueck(stueck: String) = "$stueck shares"
    override fun usBestaendeSumme(wert: String, personen: Int) =
        "Together about $wert across " + (if (personen == 1) "1 person" else "$personen people") + ", valued at the last reported price."
    override val usBestaendeHinweis = "As of each person's latest filing. Newly loaded filings count only directly held shares; in the bundled data the holding may also be held through family companies or trusts. Unexercised options are missing, so the true stake is often larger."
    override val zgSecTitel = "SEC (US insiders)"
    override val zgSecFeld = "Contact address (e-mail)"
    override val zgSecUngueltig = "Please enter an e-mail address"
    override val itTitel = "Insider trend, 12 months"
    override fun itStufe(stufe: Int) = listOf("clearly selling", "rather selling", "mixed", "rather buying", "clearly buying").getOrElse(stufe) { "" }
    override fun itSatz(stufe: Int) = listOf(
        "Management is selling clearly more of its own shares than it buys. Sales often have private reasons, but many at once can be a warning sign.",
        "Management is selling somewhat more of its own shares than it buys. Sales often have private reasons, such as taxes or buying a house.",
        "Purchases and sales by management roughly balance out.",
        "Management is buying somewhat more of its own shares than it sells. This is often seen as a sign of confidence.",
        "Management is buying clearly more of its own shares than it sells. This is seen as a strong sign of confidence, but it is no guarantee.",
    ).getOrElse(stufe) { "" }
    override fun itAnzahl(kaeufer: Int, verkaeufer: Int) = listOfNotNull(
        if (kaeufer > 0) (if (kaeufer == 1) "1 buyer" else "$kaeufer buyers") else null,
        if (verkaeufer > 0) (if (verkaeufer == 1) "1 seller" else "$verkaeufer sellers") else null,
    ).joinToString(" · ")
    override fun itRolle(rolle: Int) = listOf("CEO, CFO and management board", "Other officers", "Board members", "Close associates and major holders").getOrElse(rolle) { "" }
    override fun itRolleZeile(rolle: String, kaeufer: Int, verkaeufer: Int) = "$rolle: " + listOfNotNull(
        if (kaeufer > 0) "$kaeufer bought" else null,
        if (verkaeufer > 0) "$verkaeufer sold" else null,
    ).joinToString(", ")
    override val itCluster = "Cluster buy: at least three insiders of the same company bought within 30 days."
    override val itClusterKurz = "cluster buy"
    override val itHinweis = "Each person counts once with the direction of their trades in the period, weighted by their relationship to the company:\n• CEO, CFO and management board ×3\n• other officers ×2\n• board members ×1.5\n• close associates and major holders ×1\n\nSales under trading plans count half. For US stocks the size relative to the holding counts: purchases of 20% or more and sales of half or more count one and a half times, additions under 2% and sales under 10% count half. Only purchases and sales on the market are counted."
    override val copyExecutives = "Insiders"
    override val vdMeistgekauft = "Most buying by management, 90 days"
    override val vdMeistverkauft = "Most selling by management, 90 days"
    override val vdNeueste = "Latest reports"
    override val vdKaeufe = "Buys"
    override val vdVerkaeufe = "Sells"
    override val vdLeer = "No reports loaded right now. Open this again later."
    override val vdKarteTitel = "Buying and selling by management"
    override val grTitel = "Major shareholders"
    override val grHinweis = "Reported voting rights from 3 % under the German Securities Trading Act, including instruments, as of each report."
    override fun vdQuelle(stand: String) = "Source: © Bundesanstalt für Finanzdienstleistungsaufsicht / www.bafin.de" + if (stand.isNotBlank()) ", as of $stand" else ""
    override fun vdAnzahl(kaeufe: Int, verkaeufe: Int) = listOfNotNull(
        if (kaeufe > 0) if (kaeufe == 1) "1 buy" else "$kaeufe buys" else null,
        if (verkaeufe > 0) if (verkaeufe == 1) "1 sell" else "$verkaeufe sells" else null,
    ).joinToString(" · ")
    override fun vdZusammenfassung(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String) = "Twelve months: " + listOfNotNull(
        if (kaeufe > 0) (if (kaeufe == 1) "1 buy" else "$kaeufe buys") + " for $kaufBetrag" else null,
        if (verkaeufe > 0) (if (verkaeufe == 1) "1 sell" else "$verkaeufe sells") + " for $verkaufBetrag" else null,
    ).ifEmpty { listOf("no buys or sells") }.joinToString(", ")
    override fun vdArt(art: String) = when (art.trim().lowercase()) {
        "kauf" -> "Buy"
        "verkauf" -> "Sell"
        "zuteilung" -> "Grant"
        "ausübung" -> "Exercise"
        "schenkung" -> "Gift"
        "erbschaft" -> "Inheritance"
        "zeichnung" -> "Subscription"
        "verpfändung" -> "Pledge"
        "sonstiges", "sonstige" -> "Other"
        "" -> "Report"
        else -> art
    }
    override fun vdRolle(rolle: String) = when (rolle.trim().lowercase()) {
        "vorstand" -> "Management board"
        "aufsichtsrat" -> "Supervisory board"
        "in enger beziehung" -> "Closely associated"
        "geschäftsführer", "geschäftsführung" -> "Managing director"
        "verwaltungsrat" -> "Board of directors"
        "sonstige führungsperson" -> "Other executive"
        else -> rolle
    }
    override val alertArtMarke = "Price target"
    override val alertArtBewegung = "Daily move"
    override val enterMovePct = "Enter the move in percent"
    override fun alertMove(pct: String) = "Alert once the price moves at least ±$pct in one trading day. It reports at most once a day and stays until you delete it."
    override fun alertMoveRow(pct: String) = "±$pct in one day"
    override val alertMoveWiederholt = "reports again every trading day"
    override val alertMoveUngueltig = "Please enter between 0.5 and 50 %"
    override fun notifAlertMoveText(pct: String, price: String) = "Today $pct · price $price"
    override val notiz = "Note"
    override val notizHinzufuegen = "Add note"
    override val notizHinweis = "For example why you bought or watch this security. The note stays on this device and is included in the portfolio export."
    override val notizLoeschen = "Delete note"

    override fun availableAmount(amount: String) = "Available: $amount"
    override val depositSubtitle = "Virtual money into the cash account"
    override val withdrawToReference = "To your reference account"
    override val enterAmount = "Enter amount"
    override fun availableForWithdrawal(amount: String) = "Available for withdrawal: $amount"
    override val moreThanAvailable = "More than available. Open buy orders reserve balance."
    override val process = "Type"
    override val from = "From"
    override val to = "To"
    override val virtualFunds = "Practice funds (virtual)"
    override val referenceAccount = "Reference account"
    override val arrival = "Arrival"
    override val bankDays = "1–3 business days"
    override val availableAfter = "Available afterwards"
    override val feeLabel = "Fee"
    override val free = "Free"
    override val send = "Send"
    override val withdrawFailed = "Withdrawal not possible."
    override val depositBooked = "Deposit booked"
    override val withdrawalSent = "Withdrawal on its way"
    override fun depositAvailableNow(amount: String) = "$amount is available immediately."
    override fun withdrawalArrives(amount: String) = "$amount will reach the reference account in 1–3 business days."

    override val yourProfile = "Your profile"
    override fun practiceSince(date: String) = "Practice portfolio · since $date"
    override val totalWealth = "Total wealth"
    override val activity = "Activity"
    override val documents = "Documents"
    override val documentsSubtitle = "Settlements, statements, tax certificate"
    override val notSet = "Not set"
    override val interestOnCash = "Interest on cash"
    override val interestAuto = "Follow the ECB deposit rate"
    override fun ecbRate(rate: String, since: String) = "ECB deposit rate $rate p.a. · since $since"
    override val ecbNotLoaded = "Loading ECB rate, your own rate applies meanwhile"
    override val customRate = "Custom rate"
    override val enterRate = "Interest rate in percent p.a."
    override val accruedThisMonth = "Accrued this month"
    override val deposited = "Deposited"
    override val withdrawn = "Withdrawn"
    override val taxes = "Taxes"
    override val taxOverview = "Tax overview"
    override fun allowanceSet(amount: String) = "Tax allowance $amount"
    override val allowanceNotSet = "Tax allowance not set up"
    override val hideBalances = "Hide balances"
    override val shakeToHide = "Shake to hide amounts"
    override val trading = "Trading"
    override val spreadStocks = "Spread stocks & ETFs"
    override val spreadDerivatives = "Spread leveraged products"
    override val spreadHint = "Buys execute at the ask, sells at the bid. 100 basis points equal one percent. At 0, trades use the mid price."
    override val appearance = "Appearance"
    override val appearanceSystem = "System"
    override val appearanceLight = "Light"
    override val appearanceDark = "Dark"
    override val language = "Language"
    override val languageGerman = "Deutsch"
    override val languageEnglish = "English"
    override val coloredChart = "Chart in green and red"
    override val notifications = "Notifications"
    override val notificationsHintDesktop = "For orders, savings plans, price alerts, dividends, interest, knock-outs, copytrading, bets, news and the daily review, while the app is open."
    override val notificationsHint = "For orders, savings plans, price alerts, dividends, interest, knock-outs, copytrading, bets, news and the daily review."
    override val refreshEvery = "Refresh"
    override fun everySeconds(n: Int) = "every $n seconds"
    override val source = "Source"
    override val statusLimited = "limited"
    override val statusConnected = "connected"
    override val refreshNow = "Refresh now"
    override val exportPortfolio = "Export portfolio"
    override val exportSubtitle = "Save portfolio and settings as a file"
    override val importPortfolio = "Import portfolio"
    override val importSubtitle = "Read a backup or a CSV export from your broker"
    override val backupHint = "The file format is versioned. Later versions of the app read older files and upgrade them."
    override val aboutTitle = "About the app"
    override val kaffeeTitel = "Buy me a coffee"
    override val kaffeeText = "Support the app if you like it"
    override val aboutText = "Tryfolio is a practice portfolio: prices are real and may be delayed, trading uses virtual money. No real money flows, no securities are bought and there is no connection to a bank or broker. The app shows and explains. It gives no investment advice or recommendations, and past performance says nothing about the future.\n\nData from registers and authorities is fetched for your personal use only. Under US law (5 U.S.C. § 13107) filings by members of Congress, senators and the administration may not be used for commercial purposes. Tryfolio is non-commercial.\n\nTryfolio is free software under the GNU Affero General Public License 3.0 or later. The source code is on GitHub: github.com/regard2462/Tryfolio."
    override val version = "Version"
    override val resetDepot = "Reset portfolio"
    override val resetTitle = "Reset portfolio?"
    override fun resetText(amount: String) = "All positions, orders, savings plans, alerts and bookings will be deleted. The balance restarts at $amount."
    override val reset = "Reset"
    override val personalData = "Personal data"
    override val firstName = "First name"
    override val exportTitle = "Export"
    override fun exportDone(kb: Int) = "Portfolio exported ($kb KB)"
    override val exportFailed = "The file could not be written."
    override val importFailedTitle = "Import not possible"
    override val importNotReadable = "The file could not be read."
    override fun importError(code: String, extra: Int) = when (code) {
        "NOT_READABLE" -> "The file does not contain a readable portfolio."
        "FOREIGN" -> "The file was not created by this app."
        "NEWER" -> "The file comes from a newer version (format $extra). Please update the app."
        "INVALID_CASH" -> "Invalid balance in the file."
        "INVALID_POSITION" -> "Invalid position in the file."
        else -> importNotReadable
    }
    override val importTitle = "Import portfolio?"
    override fun exportedAt(ts: String) = "Exported on $ts"
    override fun importSummary(cash: String, positions: Int, plans: Int, tx: Int) = "Balance $cash · $positions positions · $plans savings plans · $tx bookings"
    override val knowledge = "Financial knowledge"
    override val knowledgeSubtitle = "Twenty topics with 206 lessons, reference section and calculators"
    override val knowledgeInBrowser = "Financial knowledge is open in your browser. The page comes from the app itself and needs no internet connection."
    override val knowledgeOpenAgain = "Open in browser again"
    override fun knowledgeBrowserFailed(url: String) = "The browser could not be opened. Enter this address yourself: $url"
    override val knowledgeOeffnenIn = "Open"
    override val knowledgeImBrowser = "In the browser"
    override val knowledgeInApp = "In the app"
    override val knowledgeInAppOffen = "Financial knowledge is open in a separate app window. The page comes from the app itself and needs no internet."
    override val knowledgeInAppNochmal = "Open the window again"
    override fun knowledgeBausteinAngebot(mb: Int) = "To show it in the app, it downloads an embedded browser once (Chromium, about $mb MB) from Maven Central into its data folder."

    override val allowanceTitle = "Tax allowance"
    override val allowanceNotSetTitle = "Not set up"
    override fun allowanceIntro(max: String) = "With a tax allowance (Freistellungsauftrag), up to $max of capital gains per year stay tax-free, twice that for married couples filing jointly."
    override fun allowanceUsed(used: String, year: Int, free: String) = "$used used in $year · $free remaining"
    override val activate = "Activate"
    override val manage = "Manage"
    override fun taxOverviewYear(year: Int) = "Tax overview $year"
    override val taxRate = "Tax rate"
    override fun taxRateValue(pct: String) = "25% + 5.5% solidarity surcharge = $pct"
    override val taxWithheld = "Taxes withheld"
    override val realizedStockGains = "Realised stock gains"
    override val otherIncome = "Other income (ETF, derivatives, interest, dividends)"
    override val lossPotStocks = "Loss pot stocks"
    override val privateGains = "Crypto held under a year (§ 23, not withheld)"
    override val lossPotGeneral = "Loss pot other"
    override val taxNote = "• Losses are first offset against this year's income, overpaid tax is refunded, the rest reduces later gains\n• Stock losses only offset stock gains, all other losses offset all other income\n• Sales follow FIFO, fees reduce the gain\n• Equity ETFs: 30 % tax-free (partial exemption), mixed funds 15 %\n• Crypto: no tax deducted. After more than a year the gain is tax-free, otherwise it belongs in the tax return at the personal rate (exemption limit 1,000 € a year)\n• Not modelled: advance lump sum (Vorabpauschale), foreign withholding tax, church tax\n• At the turn of the year the pots carry over and the allowance starts afresh"
    override val taxData = "Tax data"
    override val taxId = "Tax ID"
    override val editTaxId = "Edit tax ID"
    override val churchTax = "Church tax"
    override val notModelled = "Not modelled"
    override fun taxCertificate(year: Int) = "Show annual tax certificate $year"
    override fun allowanceSheetIntro(max: String) = "Amount up to $max per year, up to twice that for married couples filing jointly. Applies immediately to sales, dividends and interest."
    override val taxIdHint = "Eleven digits · anything goes in a practice portfolio"

    override val filterTrade = "Trades"
    override val filterIncome = "Income"
    override val noActivity = "No activity yet."
    override fun txLabel(t: TxType) = when (t) {
        TxType.DEPOSIT -> "Deposit"; TxType.WITHDRAWAL -> "Withdrawal"; TxType.BUY -> "Buy"; TxType.SELL -> "Sell"
        TxType.FEE -> "Fee"; TxType.INTEREST -> "Interest"; TxType.KNOCKOUT -> "Knock-out"; TxType.EXPIRY -> "Maturity"
        TxType.DIVIDEND -> "Dividend"; TxType.TAX -> "Tax"; TxType.ALERT -> "Price alert"; TxType.PLAN -> "Savings plan"
        TxType.BET -> "Prediction market"; TxType.SPLIT -> "Stock split"
    }
    override val toSecurity = "To security"
    override val showDocument = "Show settlement"
    override fun txTitle(tag: String, storedTitle: String) = when (tag) {
        "OPENING" -> "Opening balance"; "DEPOSIT" -> "Deposit"; "WITHDRAWAL" -> "Withdrawal"
        "FEE" -> "External cost fee"; "INTEREST" -> "Interest"
        else -> storedTitle
    }
    override fun txSubtitle(tag: String, via: String, qty: String, price: String, realized: String, extra: String, stored: String): String {
        val prefix = when (via) { "PLAN" -> "Savings plan · "; "ORDER" -> "Order · "; "DIVIDEND" -> "Dividend reinvested · "; else -> "" }
        return when (tag) {
            "TRANSFER_IN" -> "Imported · $qty at $price"
            "BUY" -> "${prefix}Buy $qty at $price"
            "SELL" -> "${prefix}Sell $qty at $price ($realized)"
            "FEE" -> stored.ifBlank { "" }
            "INTEREST" -> "$extra p.a. on the balance"
            "DIVIDEND" -> "Dividend $qty × $price"
            "KO_SETTLE" -> "Knock-out · refund $price per unit"
            "EXPIRY" -> "Maturity · payout $price per unit"
            "EXPIRY_WORTHLESS" -> "Maturity · expired worthless"
            "ALERT_ABOVE" -> "Price above $extra · now $price"
            "ALERT_BELOW" -> "Price below $extra · now $price"
            "ALERT_MOVE" -> "Daily move $extra · now $price"
            "PLAN_FAILED" -> "Execution failed · balance insufficient for $extra"
            "WITHDRAWAL" -> "To the reference account · arrives in 1–3 business days"
            "OPENING", "DEPOSIT" -> ""
            "BET_BUY" -> "$stored · bought $qty shares"
            "BET_SELL" -> "$stored · sold ($realized)"
            "BET_WIN" -> "$stored · won"
            "BET_LOSS" -> "$stored · lost"
            "BET_SETTLE" -> "$stored · settled ($realized)"
            "SPLIT" -> "Stock split $stored · now $qty units"
            else -> stored
        }
    }

    override val taxSection = "Tax"
    override val statements = "Account statements"
    override val settlements = "Settlements"
    override fun taxCertificateTitle(year: Int) = "Annual tax certificate $year"
    override val taxCertificateSubtitle = "Income, allowance, loss pots"
    override fun statementTitle(month: String) = "Statement $month"
    override val statementSubtitle = "All bookings of the month"
    override val noBookings = "No bookings yet."
    override val settlementsHint = "Settlements appear with the first purchase."
    override fun docTitle(t: TxType) = when (t) {
        TxType.BUY, TxType.SELL -> "Securities settlement"; TxType.DIVIDEND -> "Dividend settlement"; TxType.INTEREST -> "Interest settlement"
        TxType.KNOCKOUT -> "Knock-out settlement"; TxType.EXPIRY -> "Maturity settlement"
        TxType.DEPOSIT -> "Deposit confirmation"; TxType.WITHDRAWAL -> "Withdrawal confirmation"; else -> "Receipt"
    }
    override val docCustomer = "Customer"
    override val docDate = "Date"
    override val docNumber = "Reference"
    override val docTradeType = "Type"
    override val docSymbol = "Symbol"
    override val docExecutionPrice = "Execution price"
    override val docMarketValue = "Market value"
    override val docRealized = "Realised result"
    override val docTax = "Capital income withholding tax incl. solidarity surcharge"
    override val docVenueValue = "Best price (practice)"
    override val docDebit = "Debit"
    override val docCredit = "Credit"
    override val docDividendPerShare = "Dividend per unit"
    override val docGross = "Gross amount"
    override fun docUntil(date: String) = "until $date"
    override val docInterestGross = "Interest gross"
    override val docRefundPerPiece = "Refund per unit"
    override val docRecipient = "Recipient"
    override val docOpening = "Opening balance"
    override val docClosing = "Closing balance"
    override val docTaxYear = "Tax year"
    override val docRealizedGains = "Realised gains/losses"
    override val docDividendsGross = "Dividends gross"
    override val docAllowanceUsed = "of which used"
    override val docLossPotStocks = "Loss pot stocks (balance)"
    override val docLossPotGeneral = "Loss pot other (balance)"
    override val docTaxWithheld = "Capital income withholding tax withheld incl. solidarity surcharge"
    override val docDisclaimer = "This document was generated by a practice app. It has no tax or legal effect."
    override val docPracticeLine = "Practice portfolio · no real money, no real bank"
    override val docNoTaxEffect = "Practice portfolio"

    override fun stepOf(n: Int, total: Int) = "Step $n of $total"
    override val welcome = "Welcome to\nTryfolio"
    override val welcomeText = "A practice portfolio with real prices and virtual money. What's your name?"
    override val startCapital = "Starting balance"
    override val startCapitalText = "How much virtual money do you want to start with? You can deposit more at any time."
    override val letsGo = "Let's go"
    override val noHistory = "No history available"

    override val toolsTitle = "Tools"
    override fun toolTitle(key: String) = when (key) {
        "LUMP_SUM" -> "Lump sum in hindsight"; "SAVINGS_PLAN" -> "Savings plan in hindsight"; "RISK" -> "Volatility and drawdowns"
        "FAIR_VALUE" -> "Fair value"; "POSITION_SIZE" -> "Position size"; "SELL_TAX" -> "Sale with tax"
        else -> key
    }
    override fun toolSubtitle(key: String) = when (key) {
        "LUMP_SUM" -> "What an amount would have become"; "SAVINGS_PLAN" -> "Invested monthly over several years"
        "RISK" -> "Volatility, largest drawdown, best and worst days"
        "FAIR_VALUE" -> "Valuation from earnings growth and P/E"; "POSITION_SIZE" -> "Quantity matching stop-loss and risk"
        "SELL_TAX" -> "What remains after tax and fee"
        else -> ""
    }
    override fun toolYears(n: Int) = if (n == 1) "1 year" else "$n years"
    override val toolMax = "Max"
    override val toolMonthly = "Monthly amount"
    override val toolLoading = "Loading prices …"
    override val toolNoData = "No data is available for this period."
    override fun toolBoughtOn(date: String, price: String) = "Bought on $date at $price"
    override val toolPerYear = "Per year"
    override val toolWorldEtfShort = "World ETF"
    override val toolNoCosts = "Calculated with daily closing prices in euros, without costs and taxes. Dividends are reinvested."
    override val toolWorldNote = "The comparison uses the iShares Core MSCI World, which reinvests its income itself."
    override val toolInvested = "Invested"
    override val toolExecutions = "purchases"
    override val toolAverageCost = "Average purchase price"
    override val toolPerYearMoneyWeighted = "Per year (money-weighted)"
    override val toolVolatility = "Volatility per year"
    override val toolDrawdownPeriod = "Peak to trough"
    override fun toolDrawdownRange(from: String, to: String) = "$from to $to"
    override val toolRecovered = "Back at the previous high"
    override val toolNotRecovered = "not yet"
    override val toolWorstDay = "Worst day"
    override val toolBestDay = "Best day"
    override val toolUpDays = "Days with gains"
    override val toolRiskNote = "Volatility is the annualised standard deviation of daily price changes."
    override val toolRiskWorldNote = "The value in brackets is the world index ETF over the same period."
    override val toolDividendGrowth = "Growth per year"
    override val toolShares = "Quantity"
    override val toolGrowth = "Earnings growth per year"
    override val toolExitPe = "P/E at the end"
    override val toolRequired = "Required return per year"
    override val toolEpsNow = "Earnings per share today"
    override val toolPeNow = "P/E today"
    override fun toolEpsIn(n: Int) = "Earnings per share in $n years"
    override fun toolPriceIn(n: Int) = "Price in $n years"
    override val toolFairToday = "Fair value today"
    override val toolCurrentPrice = "Current price"
    override fun toolAbove(pct: String) = "$pct above"
    override fun toolBelow(pct: String) = "$pct below"
    override val toolImpliedReturn = "Return per year when buying today"
    override val toolFairNote = "Model: earnings per share grow evenly, the chosen P/E applies at the end, and the future price is discounted at the required return. Without dividends."
    override val toolRiskPerTrade = "Risk per purchase"
    override val toolStopDistance = "Distance to stop-loss"
    override val toolDepotValue = "Portfolio value"
    override val toolStopPrice = "Stop price"
    override val toolLossPerUnit = "Loss per unit up to the stop"
    override val toolMaxLoss = "Maximum loss"
    override val toolCapital = "Capital required"
    override fun toolShareOfDepot(pct: String) = "$pct of the portfolio"
    override val toolMoreThanCash = "The capital required is higher than your available cash."
    override val toolPositionNote = "A stop-loss limits the loss as long as the price falls without gaps. With price jumps, for example overnight, the loss can be larger."
    override val toolSellShare = "Share to sell"
    override val toolSellPrice = "Sale price"
    override val toolProceeds = "Proceeds"
    override val toolRealized = "Gain or loss"
    override val toolSellNote = "Calculated like a real sale with FIFO, fees, loss pots and allowance. Nothing is booked."

    override val importPosition = "Import position"
    override val importIntro = "Add a security you bought elsewhere with quantity, purchase price and purchase date. Your cash balance stays the same."
    override val importSearchHint = "Search stock, ETF or crypto"
    override val importDate = "Purchase date"
    override val importPrice = "Purchase price per unit"
    override fun importCloseOn(date: String, price: String) = "Closing price on $date: $price"
    override val importUseClose = "Use"
    override val importNoClose = "No closing price is available for this date."
    override val importCostBasis = "Cost basis"
    override val importValueToday = "Value today"
    override val importResult = "Return since purchase"
    override val importConfirm = "Add to portfolio"
    override fun importDone(qty: String, name: String) = "$qty × $name added to the portfolio"
    override val importDividendNote = "Dividends from before the import are not booked. Future dividends are credited by the app."
    override val transferredIn = "Imported positions"
    override val docTransferIn = "Transfer in (import)"
    override val docNoCashFlow = "No booking on the cash account"

    override val assets = "Assets"
    override val positions = "Positions"
    override val breakdown = "Breakdown"
    override val sortTotal = "Total position"
    override val sortName = "Name"
    override val keyFigures = "Key figures"
    override val resultSinceBuy = "Result since purchase"
    override val maxDrawdown = "Largest drop from peak"
    override val largestPosition = "Largest position"
    override val assetClasses = "Asset classes"
    override fun assetClass(key: String) = when (key) { "STOCKS" -> "Stocks"; "ETFS" -> "ETFs"; "CRYPTO" -> "Crypto"; "INDEX" -> "Indices"; "DERIVATIVES" -> "Derivatives"; else -> key }

    // Tools in Analytics
    override fun ptTitle(key: String) = when (key) {
        "PERFORMANCE" -> "Portfolio Performance"; "CHECK" -> "Portfolio check"; "FINDER" -> "Stock finder"; "BENCHMARK" -> "Compare with the market"; "WEALTH" -> "Wealth builder"; "WITHDRAWAL" -> "Withdrawal plan"
        "CRASH" -> "Crash test"; "DIVIDENDS" -> "Dividend calendar"; "DIVERSIFICATION" -> "Diversification"; "REBALANCE" -> "Rebalancing"
        else -> key
    }
    override val ptCheckRules = "Rules of thumb"
    override val ptCheckRulesSub = "Clusters, costs, reserve and other rules of thumb with traffic lights"
    override fun ptSubtitle(key: String) = when (key) {
        "FINDER" -> "Rank stocks by valuation, quality, analysts, trend and insiders"
        "PERFORMANCE" -> "Returns as in Portfolio Performance: time-weighted, IRR, capital account, months and years"
        "CHECK" -> "Rules of thumb with traffic lights, diversification, rebalancing and crash test"
        "BENCHMARK" -> "Your portfolio against MSCI World, FTSE All-World and more"
        "WEALTH" -> "Where saving and compound growth can take you"
        "WITHDRAWAL" -> "How long the money lasts with monthly withdrawals"
        "CRASH" -> "Your current portfolio in the big crises since 2000"
        "DIVIDENDS" -> "Expected payouts over the next twelve months"
        "DIVERSIFICATION" -> "Regions, industries, currencies and co-movement"
        "REBALANCE" -> "What to buy or sell to reach your target weights"
        else -> ""
    }
    override fun ptBenchName(key: String) = when (key) {
        "MSCI_WORLD" -> "MSCI World"; "FTSE_ALL_WORLD" -> "FTSE All-World"; "MSCI_ACWI" -> "MSCI ACWI"; "SP500" -> "S&P 500"
        "NASDAQ100" -> "Nasdaq 100"; "DAX" -> "DAX"; "STOXX600" -> "Stoxx Europe 600"; "EM" -> "Emerging markets"
        "GOLD" -> "Gold"; "BITCOIN" -> "Bitcoin"; "CASH" -> "Savings account"
        else -> key
    }
    override val ptYourDepot = "Your portfolio"
    override val ptTimeWeightedHint = "Time-weighted: the line follows price changes only. Purchases and sales leave it unchanged."
    override val ptCompareIntro = "Your portfolio today compared with an index that received every buy and sell on the same day."
    override val ptInvested = "Invested"
    override val ptDepotToday = "Your portfolio today"
    override val ptDepotTodayHint = "Securities, sale proceeds and dividends"
    override fun ptWith(name: String) = "With $name"
    override val ptDifference = "Difference"
    override val ptPerYear = "Return per year"
    override fun ptNoPricesBefore(name: String, date: String) = "$name only has prices from $date. Earlier purchases can't be replayed with it."
    override val ptSameMoneyNote = "Every purchase and sale in your portfolio is replayed in the comparison on the same day at the closing price, without fees and taxes. Sale proceeds and dividends count towards your portfolio."
    override fun ptCashNote(rate: String) = "Savings account at today's rate of $rate for the whole period."
    override fun ptSingleTitle(name: String) = "Single holdings vs $name"
    override val ptBeats = "ahead of the comparison"
    override val ptTrails = "behind the comparison"
    override val ptNoPurchases = "No purchases yet: the comparison starts with your first purchase."
    override val ptNoPositions = "No holdings in your portfolio yet."
    override val ptStartValue = "Starting value"
    override fun ptStartSecurities(v: String) = "Securities $v"
    override fun ptStartTotal(v: String) = "Total wealth $v"
    override val ptStartZero = "Start from zero"
    override val ptStartEigen = "Own amount"
    override val ptMonthlySaving = "Monthly saving"
    override val ptHorizon = "Time horizon"
    override val ptRaise = "Saving rate rises each year by"
    override fun ptRealTerms(infl: String) = "In today's money (inflation $infl)"
    override val ptTarget = "Target"
    override val ptMedian = "Middle scenario"
    override val ptWeak = "Weak scenario (10%)"
    override val ptStrong = "Strong scenario (90%)"
    override val ptPaidIn = "Paid in"
    override val ptGrowth = "Of which growth"
    override fun ptTargetChance(pct: String) = "in $pct of scenarios"
    override fun ptTargetReached(amount: String) = "Target $amount reached"
    override val ptTargetMedian = "Reached in the middle scenario"
    override fun ptTargetWhen(years: Int) = if (years <= 1) "within a year" else "after $years years"
    override val ptTargetNever = "not within this period"
    override fun ptInYears(n: Int) = if (n == 0) "Today" else if (n == 1) "In 1 year" else "In $n years"
    override fun ptReadout(median: String, low: String, high: String) = "$median (10%: $low · 90%: $high)"
    override val ptBandLabel = "80% of scenarios"
    override val ptWealthNote = "Monte Carlo simulation with 1,000 random paths: each month the return varies randomly around the chosen value. The light area shows where 80% of the paths end up, the darker one the middle 50%. The calculation rests on assumptions and does not predict the outcome."
    override val ptMonthlyWithdrawal = "Monthly withdrawal"
    override fun ptIndexed(infl: String) = "Withdrawal rises with inflation ($infl)"
    override fun ptLasts(pct: String, years: String) = "The money lasts $years in $pct of scenarios."
    override fun ptLeftAfter(years: Int) = if (years == 1) "Left after 1 year (middle scenario)" else "Left after $years years (middle scenario)"
    override val ptUsedUpAfter = "Used up after (middle scenario)"
    override val ptSafeRate = "Rate that lasts in 90% of scenarios"
    override val ptFourPercent = "4% rule"
    override fun ptPerMonth(v: String) = "$v per month"
    override val ptPlannedTotal = "Planned withdrawals in total"
    override val ptNoStart = "Without a starting value there is nothing to withdraw. Choose total wealth or buy securities first."
    override val ptWithdrawalNote = "The withdrawal is taken at the start of each month, the rest then varies as in the wealth builder (800 random paths). With inflation, amounts are in today's money. The 4% rule comes from William Bengen (1994, 30 years of US data)."
    override fun ptCrashName(key: String) = when (key) {
        "DOTCOM" -> "Dot-com crash"; "FINANCIAL" -> "Financial crisis"; "EURO" -> "Euro debt crisis"
        "COVID" -> "Covid crash"; "RATES" -> "2022 rate shock"; "TARIFFS" -> "2025 tariff shock"
        else -> key
    }
    override val ptCrashIntro = "How your current portfolio would have moved from the S&P 500's peak to its low in each crisis."
    override val ptReference = "S&P 500"
    override fun ptHardestHit(name: String, pct: String) = "Hit hardest: $name $pct"
    override fun ptRecovery(duration: String) = "After the low, the S&P 500 took $duration to get back to its old level."
    override val ptNotRecovered = "The S&P 500 has not yet returned to its old level since then."
    override fun ptDuration(days: Long): String {
        val months = Math.round(days / 30.44).toInt()
        val y = months / 12
        val m = months % 12
        return when {
            days < 45 -> if (days == 1L) "1 day" else "$days days"
            y == 0 -> "$m months"
            m == 0 -> if (y == 1) "1 year" else "$y years"
            else -> (if (y == 1) "1 year" else "$y years") + " and " + (if (m == 1) "1 month" else "$m months")
        }
    }
    override fun ptEstimated(n: Int, total: Int) = "$n of $total holdings didn't exist back then. They are estimated via their beta."
    override val ptCustomScenario = "Your own scenario"
    override val ptMarketFalls = "The S&P 500 falls by"
    override val ptEstimatedDepot = "Your portfolio, estimated"
    override val ptCrashNote = "Holdings with prices from the crisis use their actual move, younger ones their beta to the S&P 500 over the last three years (weekly returns). Derivatives are revalued at the changed price of their underlying, and knock-outs may be knocked out. Exchange rates, dividends and purchases during the crisis are not included."
    override val ptDivExpected = "Expected over the next 12 months"
    override fun ptDivAfterTax(v: String) = "after tax about $v"
    override val ptDivPerMonth = "Per month on average, after tax"
    override val ptDivYield = "Yield on portfolio value"
    override val ptDivYieldOnCost = "Yield on cost"
    override val ptDivReceived = "Received so far in this practice portfolio (net)"
    override val ptDivNext = "Upcoming payments"
    override val ptDivTop = "Largest payers"
    override fun ptDivTimes(n: Int) = if (n == 1) "once a year" else "$n× a year"
    override val ptDivNone = "Your holdings paid nothing in the last twelve months."
    override val ptDivNote = "Projected from the payouts of the last twelve months with the same ex-dates and amounts per share, today's holdings and today's exchange rate. Accumulating ETFs do not appear. The tax takes this year's remaining tax-free allowance into account."
    override val ptTop3 = "Top three combined"
    override val ptEffective = "Diversified like"
    override fun ptEffectiveValue(n: String) = "$n equal-sized holdings"
    override val ptCurrencies = "Currencies"
    override fun ptGroup(key: String) = when (key) {
        "CRYPTO" -> "Crypto"; "DERIVATIVES" -> "Derivatives"; "ETFS" -> "ETFs"; else -> "Unassigned"
    }
    override val ptCorrelation = "Co-movement (correlation, 1 year)"
    override val ptAverage = "Average"
    override val ptCorrelationNote = "Co-movement ranges from −1 to 1:\n• 1: the prices move in lockstep\n• 0: no connection\n• below 0: they move against each other\n\nThe lower the co-movement, the more diversification helps. Calculated from weekly returns over the last year for the largest holdings."
    override val ptEtfCosts = "Ongoing costs of your ETFs"
    override fun ptEtfCostValue(ter: String, perYear: String) = "avg $ter · about $perYear a year"
    override val ptDiversificationNote = "Stocks by country of domicile and industry, ETFs by their region. An ETF counts as a whole."
    override val ptModeRebalance = "Rebalance"
    override val ptModeBuyOnly = "Buy only"
    override val ptNewMoney = "New money"
    override fun ptCash(v: String) = "Cash $v"
    override val ptEqualWeights = "Equal weights"
    override val ptCurrentWeights = "Current weights"
    override val ptTargetWeights = "Target weights"
    override fun ptBuyAmount(v: String) = "Buy $v"
    override fun ptSellAmount(v: String) = "Sell $v"
    override val ptOnTarget = "on target"
    override val ptBuys = "Purchases"
    override val ptSells = "Sales"
    override fun ptOrdersFees(n: Int, fee: String) = if (n == 1) "1 order · $fee fee" else "$n orders · $fee fees"
    override val ptTaxEstimate = "Estimated tax on sales"
    override val ptDeviation = "Deviation from target"
    override val ptNeedTwo = "Rebalancing needs at least two holdings."
    override fun ptPoints(v: String) = "$v pp"
    override val ptOthers = "Others"
    override fun ptFromTo(a: String, b: String) = "$a to $b"
    override val ptRebalanceNote = "The sliders set the weights relative to each other, together they add up to 100%. Calculated with the prices shown and a €1 fee per order. Nothing is executed here."

    override val knockOuts = "Knock-outs"
    override val warrants = "Warrants"
    override val factors = "Factors"
    override val call = "Call"
    override val put = "Put"
    override val long = "Long"
    override val short = "Short"
    override val strikePrice = "Strike price"
    override val maturity = "Maturity"
    override val issuer = "Issuer"
    override val ecbAbbrev = "ECB"
    override val leverage = "Leverage"
    override val factor = "Factor"
    override val delta = "Delta"
    override val knockOut = "Knock-out"
    override val ratio = "Ratio"
    override val product = "Product"
    override val allLeverages = "all leverages"
    override val allFactors = "all factors"
    override val customLeverage = "Custom leverage"
    override val customLeverageHint = "Any leverage, decimals allowed (e.g. 37.5) and beyond the list (e.g. 350). A product is issued for it."
    override fun leverageDialogHint(min: Int, max: Int) = "Leverage from $min to $max. Leave empty to show all."
    override val leverageOwn = "Custom product"
    override val noProductForFilter = "No product for this selection."
    override val noPriceForProduct = "No price available for this security right now."
    override fun productKind(kind: String) = when (kind) { "TURBO" -> "Mini future (turbo)"; "WARRANT" -> "Warrant"; "FACTOR" -> "Factor certificate"; else -> kind }
    override fun distance(pct: String) = "Distance $pct"
    override fun resultsCount(n: Int) = if (n == 1) "1 result" else "${String.format(Locale.UK, "%,d", n)} results"
    override val minStrike = "Minimum strike"
    override val maxStrike = "Maximum strike"
    override val startDateFilter = "Start date"
    override val endDateFilter = "End date"
    override val strategy = "Strategy"
    override val productInfo = "Product information"
    override val underlying = "Underlying"
    override val underlyingPrice = "Underlying price"
    override val distanceToBarrier = "Distance to barrier"
    override val remainingDays = "Time to maturity"
    override fun days(n: Long) = if (n == 1L) "1 day" else "$n days"
    override val impliedVol = "Implied volatility"
    override val intrinsicValue = "Intrinsic value"
    override val timeValue = "Time value"
    override val premium = "Premium"
    override val breakEven = "Break-even"
    override val sensitivities = "Sensitivities"
    override val gamma = "Gamma"
    override val vega = "Vega (per 1% vol)"
    override val theta = "Theta (per day)"
    override val currency = "Currency"
    override val referencePrice = "Reference price"
    override val riskKnockout = "If the underlying touches the knock-out barrier, the certificate ends immediately. The residual value between barrier and strike is repaid, after a gap through the strike almost nothing. The financing costs of real mini futures, which move the strike every day, are not modelled."
    override val riskFactor = "The factor applies to the daily move and resets every day. Over longer periods the result can differ markedly from the multiple of the underlying's performance, especially in volatile markets."
    override val riskWarrant = "Time value decays as maturity approaches. If the underlying is on the wrong side of the strike at maturity, the warrant expires worthless. Settlement uses the first current price from maturity on."
    override val modelledHistory = "The history shown is modelled and starts with the creation of the product."
    override val noFactorHistory = "The chart of factor products is modelled from the daily returns of the underlying (daily leverage, interest and fee), backwards from today's price."
    override val productUnavailable = "Product no longer available"
    override fun leverageValue(v: String) = "Leverage $v"
    override val derivative = "Derivative"
    override val deltaFilter = "Delta from"

    override fun error(code: String, extra: String) = when (code) {
        "INVALID_QTY" -> "Invalid quantity"; "NO_PRICE" -> "No price available"; "INVALID_FEE" -> "Invalid fee"
        "NOT_TRADABLE" -> "Product no longer tradable"; "WHOLE_PIECES" -> "Derivatives trade in whole units only"; "TOO_LARGE" -> "Amount too large"
        "NOT_ENOUGH_CASH" -> "Not enough balance"; "NOT_IN_DEPOT" -> "Position not in portfolio"; "NOT_ENOUGH_PIECES" -> "Not enough units in portfolio"
        "FEE_NOT_COVERED" -> "Balance does not cover the fee"; "INVALID_LIMIT" -> "Invalid limit/stop price"; "INVALID_TRAIL" -> "Distance must be between 0 and 50%"
        "MISSING_SECURITY" -> "Security missing"; "INVALID_AMOUNT" -> "Invalid amount"; "ONLY_AVAILABLE" -> "Only $extra available"
        "ORDER_CLOSED" -> "The order is no longer open"; "INVALID_DATE" -> "Invalid date"; "EXPIRED" -> "Validity expired"; "PRODUCT_GONE" -> "Product no longer tradable"
        "UNREACHABLE" -> "Prices unavailable"; "FX_MISSING" -> "Exchange rate missing for some prices."; "PARTIAL" -> "Some prices are currently unavailable."
        "SAVE_FAILED" -> "The portfolio could not be saved. Please check storage space."
        "LOAD_FAILED" -> "The saved portfolio could not be read. A copy is kept as account.unlesbar in the data folder, the app starts with a new portfolio."
        else -> code
    }
    override fun buyDone(qty: String, price: String) = "Bought: $qty × $price"
    override fun sellDone(qty: String, price: String) = "Sold: $qty × $price"
    override val refreshFirst = "Please refresh the price first."
    override val marketClosedOrders = "Market closed. Limit, stop and trailing orders execute at the next session."
    override val limitPlaced = "Limit order placed"
    override val stopPlaced = "Stop order placed"
    override val trailingPlaced = "Trailing stop placed"
    override val orderCancelled = "Order cancelled"
    override fun depositedToast(amount: String) = "$amount deposited"
    override fun withdrawnToast(amount: String) = "$amount is being transferred to your reference account"
    override val invalidAmount = "Invalid amount"
    override val invalidPlan = "Invalid savings plan"
    override val planCreated = "Savings plan created"
    override val planPaused = "Savings plan paused"
    override val planResumed = "Savings plan resumed"
    override val planChanged = "Savings plan changed"
    override val planDeleted = "Savings plan deleted"
    override val invalidPrice = "Invalid price"
    override val alertSet = "Price alert set"
    override val accountReset = "Portfolio reset"
    override val depotImported = "Portfolio imported"
    override val watchAdded = "Added to watchlist"
    override val watchRemoved = "Removed from watchlist"

    override fun notifDividendText(name: String, amount: String) = "$name: $amount credited"
    override val notifInterest = "Interest credited"
    override fun notifInterestText(amount: String) = "$amount to your balance"
    override fun notifKnockoutText(name: String) = "$name hit the knock-out barrier, the residual value will be repaid"
    override val notifKnockoutSettled = "Knock-out settled"
    override fun notifKnockoutSettledText(name: String, amount: String) = "$name: refund $amount"
    override fun notifExpiryText(name: String, amount: String) = "$name: $amount paid out"
    override fun notifAlert(name: String) = "Price alert: $name"
    override fun notifAlertText(price: String, target: String) = "Price $price · target $target reached"
    override val notifPlanFailed = "Savings plan not executed"
    override fun notifPlanFailedText(name: String) = "$name: balance insufficient"
    override val notifPlanDone = "Savings plan executed"
    override fun notifPlanDoneText(name: String, amount: String) = "$name: $amount"
    override val notifOrder = "Order executed"
    override fun notifOrderText(side: String, qty: String, name: String, price: String) = "$side $qty × $name at $price"
    override val channelTrades = "Orders, savings plans and bets"
    override val channelCash = "Cash and dividends"
    override val channelUpdates = "New versions"
    override val profilEinstellungen = "Settings"
    override val profilSicherheit = "Security"
    override val profilDaten = "Data and accounts"
    override val profilErweitert = "Advanced"
    override val rbTitel = "Daily review"
    override fun rbProfil(anzahl: Int, an: Boolean) = when {
        !an -> "Off" + if (anzahl > 0) " · $anzahl collected" else ""
        anzahl == 0 -> "Every trading day"
        anzahl == 1 -> "1 review · every trading day"
        else -> "$anzahl reviews · every trading day"
    }
    override val rbSchalter = "Create reviews"
    override val rbSchalterHinweis = "A short summary every trading day, on Fridays with the weekly review."
    override val rbLeer = "No review yet. The first one comes on the next trading day at the chosen time."
    override val rbJetzt = "Summarise today now"
    override val rbJetztLaeuft = "Summarising …"
    override val rbJetztFehler = "No prices available right now."
    override fun rbWoche(von: String, bis: String) = "Week of $von to $bis"
    override val rbWocheUeberblick = "The week at a glance"
    override val rbMarkt = "Market"
    override val rbBewegungen = "Moves in your portfolio"
    override val rbNachrichten = "Important news"
    override fun rbDepotZeile(aenderung: String, prozent: String?, wert: String) = aenderung + (prozent?.let { " ($it)" } ?: "") + ", value $wert"
    override fun rbWahrscheinlich(titel: String, quelle: String) = "Likely: \"$titel\"" + if (quelle.isNotBlank()) " ($quelle)" else ""
    override fun rbWahrscheinlichKurz(titel: String) = "likely \"$titel\""
    override fun rbMitMarkt(markt: String, aenderung: String) = "No news of its own found, moved with the market ($markt $aenderung)"
    override val rbOhneGrund = "No matching news found"
    override val rbKeinDepot = "No securities in the portfolio"
    override val rbKeineBewegung = "No security moved by more than 0.5%"
    override val rbKeineNachrichten = "No particularly important news"
    override val rbVorlaeufig = "Preliminary, made before the US market close. The final review comes in the evening."
    override val rbHinweis = "The reasons are guesses: the app looks for the day's headlines that name the security and rates them as on the news page. Without a matching headline it says whether the security moved with its market. The portfolio is compared with the MSCI World, sectors with US sector ETFs, large caps as in the heatmap."
    override fun rbMitteilungTitel(datum: String) = "Daily review $datum"
    override fun rbWochenTitel(von: String, bis: String) = "Weekly review $von to $bis"
    override fun rbFazitTon(stufe: Int, woche: Boolean) = when {
        stufe > 0 -> if (woche) "A strong week" else "A strong day"
        stufe < 0 -> if (woche) "A weak week" else "A weak day"
        else -> if (woche) "A quiet week" else "A quiet day"
    }
    override fun rbFazitDepot(ton: String, hoch: Boolean, prozent: String, betrag: String) = "$ton: your portfolio " + (if (hoch) "rose" else "fell") + " $prozent ($betrag)"
    override fun rbFazitVergleich(markt: String, punkte: String, lage: Int) = when {
        lage > 0 -> " and was $punkte percentage points ahead of the $markt."
        lage < 0 -> " and was $punkte percentage points behind the $markt."
        else -> " and moved roughly in line with the $markt."
    }
    override fun rbFazitBeweger(name: String, prozent: String) = "The biggest mover was $name ($prozent)."
    override fun rbFazitMarkt(markt: String, prozent: String, lage: Int, woche: Boolean) = when {
        lage > 0 -> "The $markt closed " + (if (woche) "the week " else "") + "$prozent higher."
        lage < 0 -> "The $markt closed " + (if (woche) "the week " else "") + "$prozent lower."
        else -> "The $markt was almost unchanged" + (if (woche) " over the week." else ".")
    }
    override fun rbFazitBranchen(beste: String, bp: String, schwaechste: String, sp: String) = "Strongest sector: $beste ($bp), weakest: $schwaechste ($sp)."
    override val rbStaerkste = "Strongest"
    override val rbSchwaechste = "Weakest"
    override val rbGewinner = "Top gainers among large caps"
    override val rbVerlierer = "Top losers among large caps"
    override val rbMerkliste = "Your watchlist"
    override val rbAufgeklappt = "expanded"
    override val rbZugeklappt = "collapsed"
    override fun newsNotifMany(n: Int) = "$n new headlines"
    override fun newsNotifyOption(stufe: String) = when (stufe) { "alle" -> "All headlines"; "aus" -> "Off"; else -> "Important news only" }
    override fun newsNotifyOptionHint(stufe: String) = when (stufe) {
        "alle" -> "Every new headline from the sources that are switched on. More than three at once come as one notification"
        "aus" -> "No notifications for headlines"
        else -> "Default: only the headlines marked on the news page"
    }
    override val newsNotifyOffGlobal = "Notifications are switched off in the profile. Switch them on there for this setting to take effect."
    override val newsNotifyHint = "The app checks every five minutes while it is open and, on Android, about every 30 minutes while it is closed and the device is online."
    override val awTitle = "Analysis"
    override val awLoading = "Calculating the analysis …"
    override val awNone = "There are no trades in stocks or funds with prices to analyse."
    override fun awHorizon(tage: Int) = if (tage >= 365) "1 year" else "$tage days"
    override val awFrequency = "Size and frequency"
    override val awPerMonth = "Trades per month"
    override val awBusiest = "Busiest month"
    override val awTypical = "Typical size (median)"
    override val awOverMillion = "Trades over $1m"
    override val awBuyShare = "Share of buys"
    override val awUnusual = "Unusual months"
    override val awBeforeMoves = "Before large price moves"
    override fun dcSummary(gut: Int, von: Int) = "$gut of $von points in the green"
    override fun dcTitle(regel: String) = when (regel) {
        "FONDSKOSTEN" -> "Ongoing fund costs"
        "TER_GESTIEGEN" -> "Higher fund costs"
        "GROESSTE" -> "Largest single position"; "ANZAHL" -> "Spread across single securities"; "BRANCHE" -> "Largest sector"
        "WAEHRUNG" -> "Foreign currency"; "HEBEL" -> "Leveraged products"; "KRYPTO" -> "Crypto"; "KOSTEN" -> "Order costs"
        "RESERVE" -> "Cash as a reserve"; "SPARPLAN" -> "Savings plan"; "VERLUSTE" -> "Deep losses"; else -> regel
    }
    override fun dcValue(regel: String, anteil: String, name: String, anzahl: Int) = when (regel) {
        "FONDSKOSTEN" -> "$anteil on average, about €$anzahl a year, most expensive: $name"
        "TER_GESTIEGEN" -> (if (anzahl == 1) "More expensive now: $name" else "$anzahl more expensive now: $name") + ", up to $anteil more a year"
        "GROESSTE" -> "$name: $anteil of the portfolio"
        "ANZAHL" -> "$anzahl single stocks, $anteil in ETFs"
        "BRANCHE" -> "$name: $anteil of the portfolio"
        "WAEHRUNG" -> "$anteil traded in a foreign currency"
        "HEBEL" -> "$anteil of the portfolio"
        "KRYPTO" -> "$anteil of the portfolio"
        "KOSTEN" -> "$anteil of the amount traded in twelve months, $anzahl orders"
        "RESERVE" -> "$anteil of total assets"
        "SPARPLAN" -> if (anzahl > 0) "$anzahl active" else "none"
        "VERLUSTE" -> "$anzahl more than 50 % down: $name"
        else -> anteil
    }
    override val terTeurerTitel = "ETF became more expensive"
    override val etfTdHinweis = "The tracking difference shows the real cost of an ETF: how far its yearly return lagged the index."
    override val etfTdLink = "Tracking difference at extraETF"
    override fun terTeurerText(name: String, alt: String, neu: String) = "$name: ongoing charges rose from $alt to $neu a year."
    override fun dcExplain(regel: String) = when (regel) {
        "TER_GESTIEGEN" -> "The app remembers the ongoing charges of every ETF whenever it fetches them. If they rise, this shows here for a year. Fund companies may raise costs after giving notice. A comparable ETF with lower costs can then be worth it, but switching costs fees and, on a gain, taxes."
        "FONDSKOSTEN" -> "The fund deducts its ongoing charges (TER) from its assets every year. Broad index ETFs often cost 0.1 to 0.3%. Over many years higher costs reduce the return noticeably."
        "GROESSTE" -> "If a single security falls sharply, the whole portfolio feels it. A common rule of thumb is at most 10 % per single security. ETFs do not count here because they spread risk themselves."
        "ANZAHL" -> "With few single securities, a lot depends on each one. From about 15 to 20 securities in different sectors, or with a broad ETF, this risk drops markedly."
        "BRANCHE" -> "Securities of one sector often fall together, for example when interest rates or rules change. More than a quarter of the portfolio in one sector is a cluster."
        "WAEHRUNG" -> "For securities in dollars, pounds or francs, the exchange rate moves on top of the price. This often evens out in the long run but can run one way for years. ETFs count with their trading currency, a world ETF in euros is still largely invested in dollars."
        "HEBEL" -> "Leveraged products multiply gains and losses and can expire worthless. Many guides suggest at most 5 to 10 % of the portfolio."
        "KRYPTO" -> "Cryptocurrencies fluctuate far more than stocks. Many guides suggest at most about 5 to 10%."
        "KOSTEN" -> "Fixed fees weigh heavily on small orders: €1 on €50 is 2 %. Trading less often in larger amounts, or a savings plan, lowers the costs."
        "RESERVE" -> "Some cash allows buying more without selling. If most of it sits unused, it adds nothing to the return."
        "SPARPLAN" -> "A savings plan invests regularly without waiting for the right moment and averages the entry price."
        "VERLUSTE" -> "A security that has lost half its value has to double to get back to the purchase price. Check whether the reason for buying still holds."
        else -> ""
    }
    override val dcNote = "Rules of thumb from common guides. More in the learning section under diversification, risk and costs."
    override fun copyPaperRead(meldungen: Int, unlesbar: Int) =
        (if (meldungen == 1) "One filing was submitted on paper and exists only as an image." else "$meldungen filings were submitted on paper and exist only as images.") +
            " The app brings their trades along, read by text recognition." +
            (if (unlesbar == 1) " One row could not be read reliably and is missing." else if (unlesbar > 1) " $unlesbar rows could not be read reliably and are missing." else "")
    override val derivInvestorIntro = "Call and put options according to the 13F. The value is the value of the underlying shares, not the price of the options. They are not part of the portfolio history."
    override val derivStructured = "Structured product"
    override val derivOther = "Option without details"
    override fun derivStrike(preis: String) = "strike \$$preis"
    override fun derivExpiry(datum: String) = "expiry $datum"
    override fun derivContracts(anzahl: String) = "$anzahl contracts"
    override val ctcDays = "Trading days"
    override fun ctcApprox(betrag: String) = "about $betrag"
    override fun ctcCountApprox(anzahl: String, betrag: String) = "$anzahl, about $betrag"
    override fun ctcReported(datum: String) = "reported $datum"
    override val ctcEmpty = "There are no reported trades this month."
    override val ctcHint = "Green: more bought than sold that day, red: more sold, stronger for larger amounts. The number in a cell is the number of trades. Dates are trade dates, not filing dates. Amounts are the middle of the reported range."
    override val updNotifTitle = "New Tryfolio version"
    override fun updNotifText(fassung: String) = "Version $fassung is ready to download."
    override val updSwitchHint = "The app checks GitHub once a day and reports each new version once."
    override fun updRowTitle(fassung: String) = "Version $fassung available"
    override val updRowSubtitle = "Open the download page"

    override val guideTitle = "Quick guide"
    override fun ppfZeitraum(key: String) = when (key) { "YTD" -> "YTD"; "Y1" -> "1 year"; "Y3" -> "3 years"; "Y5" -> "5 years"; else -> "All" }
    override val ppfTtwror = "True time-weighted rate of return (TTWROR)"
    override fun ppfVonBis(von: String, bis: String) = "$von to $bis"
    override val ppfTtwrorJahr = "TTWROR per year"
    override val ppfUnterJahr = "from one year on"
    override val ppfIzf = "Internal rate of return (IRR) per year"
    override val ppfIzfZeitraum = "Internal rate of return (IRR) for the period"
    override val ppfIzfHinweis = "takes into account when how much money was in the portfolio"
    override val ppfAbsolut = "Absolute performance"
    override val ppfMaxRueckgang = "Largest drawdown"
    override val ppfNurWertpapiere = "Securities only"
    override val ppfNurWertpapiereHinweis = "price performance without cash, costs and dividends, like the portfolio chart"
    override val ppfLetzterTag = "Last day"
    override val ppfRisiko = "Risk indicators"
    override val ppfRueckgangDauer = "Longest time below a high"
    override val ppfRueckgangDauerHinweis = "from a high until it was reached again, or until today"
    override fun ppfTage(n: Long) = if (n == 1L) "1 day" else "$n days"
    override val ppfSemivolatilitaet = "Downside volatility"
    override val ppfSemivolatilitaetHinweis = "Semivolatility: counts only the periods below the mean"
    override val ppfHoch = "All-time high"
    override val ppfBilanz = "Performance calculation"
    override fun ppfWertAm(datum: String) = "Value on $datum"
    override val ppfEinlagen = "Deposits"
    override val ppfEntnahmen = "Withdrawals"
    override val ppfKursgewinne = "Capital gains"
    override fun ppfRealisiert(betrag: String) = "realized and unrealized, of which realized $betrag"
    override val ppfDividenden = "Dividends (before tax)"
    override val ppfZinsen = "Interest (before tax)"
    override val ppfMonate = "Return per month and year"
    override val ppfHinweis = "Calculated as in Portfolio Performance: the whole portfolio with securities and cash, after fees and taxes. Money from outside means deposits, withdrawals, bets and positions taken over without money (at cost).\n\n• Time-weighted rate of return (TTWROR): measures the investment regardless of when you paid in or out, suited for comparison with funds and indices\n• Internal rate of return (IRR): your personal return, takes into account when how much money was in the portfolio\n• Capital gains: what remains from the end value, start value, deposits, withdrawals, income and costs\n\nVolatility and downside volatility are shown per year. Below one year the IRR applies to the period and is not annualised.\n\nYou export bookings for Portfolio Performance under Profile › Advanced."
    override val guideAllesTitel = "Everything the app can do"
    override val guideAllesText = "An overview first. The pictures after it show how to use the app. You can open the quick guide again at any time from your profile."
    override fun guideFunktionen(widgets: Boolean, bildschutz: Boolean) = listOfNotNull(
        "Practice portfolio with real prices" to "Trade stocks, ETFs, crypto and derivatives with virtual money, with limit or stop, fees and tax included. Cash earns interest.",
        "Savings plans and transfers" to "Savings plans buy at fixed intervals and can reinvest dividends. You find them in the “Orders” tile under Savings plans. Transfers pay virtual money in or out.",
        "Finding securities" to "Search with a “Recently searched” list, filters by sector, country and region, and derivatives for every underlying.",
        "Security page" to "Chart with advanced chart, valuation and key figures, opportunities and risks, events, news and trends on Reddit and Wikipedia. Financials show revenue, earnings, balance sheet, cash flow, dividend and earnings per share. Background comes before the tools, and for derivatives a bar shows long and short.",
        "Indicators" to "Below the chart, “Indicators” opens the selection: moving averages, Bollinger bands, Ichimoku, parabolic SAR, RSI, stochastic, MACD, ATR and OBV, one at a time or together, with explanations.",
        "Tools and options per security" to "Lump sum and savings plan in hindsight, position size, sale with tax, strategy test and “How far can it fall”. For US stocks, opportunities and risks show the options with put/call, expected move and max pain.",
        "Politics and agencies" to "For US stocks in the “Background” section: lobbying spending, members of Congress who traded, government contracts, campaign contributions, layoff notices, patents and large holders.",
        "Markets and heatmap" to "The “Markets” tile has the tabs Heatmap, Trends, Movers, Sentiment and Outlook. The heatmap shows the stock market or your holdings as tiles by weight and daily change, with two-finger zoom. Movers lists price moves, IPOs and this week's earnings.",
        "Trends and mood" to "Sectors, stocks in demand and the most discussed stocks on Reddit. Sentiment brings Fear & Greed, the AAII survey, put/call and safe havens. The Outlook shows market expectations, Polymarket bets with “If it happens”, early warning signs, inventories and economic data.",
        "Analytics and portfolio tools" to "Portfolio Performance with return, IRR and capital account, portfolio history, portfolio check, investor profile and self-check, diversification, rebalancing, crash test, wealth building, withdrawal plan and dividend calendar. Statistics on buys, sells and income are under Cash.",
        "Stock finder and radar" to "The “Stock finder” tile has the tabs List and Radar. The list ranks stocks by valuation, quality, analysts, price trend, insiders, short selling and sentiment, with weights, reasons and 20 templates based on finviz. The Radar collects early hints such as rising mentions and insider buys.",
        "Copytrading" to "What US representatives, senators, the President and big investors buy and sell, with portfolio history, calendar, positions and strategies to replicate. A bell reports news.",
        "Leaderboard and Congress buys" to "Which representatives and senators beat the market with their purchases, their net worth per annual report, and a strategy built from all reported purchases with a backtest since 2024.",
        "Prediction markets" to "Bet on events at Polymarket and Kalshi with practice money, see the largest bets of the last 24 hours and follow what your open and closed bets have returned.",
        "News and price alerts" to "News by provider, Google News, posts by Trump, shareholder letters and blogs, alerts at price levels. An article opens the original page, “Show text only” shows the plain text.",
        "Financial knowledge" to "A reference on markets, taxes and strategies, with lessons and calculators. It is a tile on the start page.",
        "Daily and weekly review" to "Every trading day at the time you choose a verdict: your portfolio compared with the MSCI World, market, sectors, important news and the likely reason for each bigger move. On Fridays the whole week.",
        "Request access keys" to "With free keys of your own the app becomes more complete: Reddit, the FEC for campaign contributions, the US Patent Office, The Guardian, Finnhub, Alpha Vantage and DeepL. Enter them in the profile under “Access”, which also says where to get them.",
        "Security" to "App lock with PIN or password, data encrypted with AES-256" + (if (bildschutz) ", screenshots allowed or blocked." else "."),
        if (widgets) "Widgets" to "Portfolio, watchlist, single security, top and flop, dividends, markets, heatmap, sentiment, news and shortcuts of your choice on the home screen." else null,
    )
    override val guideSubtitle = "The app's features in pictures"
    override val guideMeldungenTitel = "Load the trades of members of Congress"
    override val guideMeldungenText = "Copytrading, the leaderboard and the pages of US stocks show what members of Congress buy and sell. Filings since 2024 come with the app, newer ones load in the background, even if you close the guide."
    override val guideMeldungenLaden = "Load all filings now"
    override fun guideMeldungenLaedt(gelesen: Int, gesamt: Int) = if (gesamt == 0) "Loading the index" else "$gelesen of $gesamt filings read"
    override fun guideMeldungenFertig(gesamt: Int) = if (gesamt == 0) "All filings are already on the device." else "Done: $gesamt new filings read."
    override val guideSkip = "Skip"
    override val guidePages = listOf(
        "Portfolio and cash" to "The start page shows the value of your securities and how it developed. Swipe left for cash with balance, statistics and bookings, once more for your profile. Below the positions are the tiles, such as Orders, Markets and Analytics.",
        "Finding securities" to "Use “Trade” at the bottom of the start page to find stocks, ETFs, derivatives and crypto. A tap opens the security page, swiping down closes it.",
        "Buying and savings plans" to "“Buy” creates an order, as an amount or a number of shares, right away or with a limit or stop. A savings plan buys at fixed intervals. Both are in the “Orders” tile, the tabs at the top switch between orders and savings plans.",
        "Indicators" to "Tap “Indicators” below the chart and then the names you want. The lines appear in the chart and underneath you see what they say.",
        "Tools and options" to "Further down the security page, the tools work out what a lump sum or a savings plan would have earned. The options are under opportunities and risks, the dividend under financials for stocks and under key figures for ETFs.",
        "Politics and agencies" to "For US stocks the “Background” section before the tools shows lobbying, bills, government contracts, contributions, layoffs, patents and the large holders. Short selling is under opportunities and risks.",
        "Heatmap" to "The “Markets” tile opens the tabs Heatmap, Trends, Movers, Sentiment and Outlook. In the heatmap big tiles are big companies, green rose, red fell. A tap opens the security, zoom with two fingers, a double tap zooms in or back out, zooming in shows the names.",
        "Trends and mood" to "Trends show sectors, popular stocks and what is discussed on Reddit. Movers lists the biggest price moves, IPOs and this week's earnings, Sentiment shows the fear and greed gauges and the safe havens (US).",
        "Markets: outlook" to "The Outlook tab starts with “Market expectations” for interest rates and recession. Each Polymarket bet has an “If it happens” line that says what it usually means for stocks, rates or commodities. “Inventories and economic data” show early how supply and demand are doing.",
        "Portfolio tools" to "The “Analytics” tile on the start page opens the tools for the whole portfolio: portfolio check with rules of thumb and investor profile, portfolio history, simulations and other tools.",
        "Stock finder and radar" to "The “Stock finder” tile has two pills, List and Radar. In the list you pick the market and tap “Rank stocks”, filters, sorting and templates based on finviz sit above the list, a tap on a stock shows points and reasons. The Radar shows hints about stocks before they are widely noticed.",
        "Copytrading" to "Follow representatives, senators and investors, see their trades and history and replicate a portfolio with one tap. The bell reports new trades.",
        "Leaderboard and Congress buys" to "At the top of the House list are the leaderboard of whose purchases beat the market and the strategy built from all reported purchases.",
        "Prediction markets" to "Bet practice money on events at Polymarket and Kalshi. Under “Big bets” you see where a lot of money is going right now.",
        "Request access keys" to "Many areas become more complete with free keys of your own, such as Reddit, campaign contributions, patents and more news. The profile under “Access” says where to request them, and that is where you enter them.",
        "Profile" to "Behind your initial at the top right are documents, price alerts, taxes, access keys and all settings. You can open this guide there again at any time.",
    )

    override val guidePagesDesktop get() = guidePages.toMutableList().apply {
        this[0] = "Portfolio and cash" to "The start page shows the value of your securities and how it developed. Click “Cash” at the top or drag the page left with the mouse button held down for balance, statistics and bookings, once more for your profile. Below the positions are the tiles, such as Orders, Markets and Analytics."
        this[1] = "Finding securities" to "Use “Trade” at the bottom of the start page to find stocks, ETFs, derivatives and crypto. A click opens the security page, Escape or the arrow at the top left closes it."
    }

    // Discover
    override val bestPerformers = "Top gainers"
    override val worstPerformers = "Top losers"
    override val ipos = "IPOs"
    override val iposIntro = "Upcoming IPOs appear here once they are announced."
    override val upcoming = "Upcoming"
    override val completed = "Completed"
    override fun ipoCardTitle(name: String) = "$name IPO"
    override fun ipoListed(date: String) = "Listed $date"
    override fun ipoSinceIssue(pct: String) = "since IPO $pct"
    override fun ipoAsOf(date: String) = "As of $date. You cannot subscribe to IPOs in Tryfolio. Trading starts with the first listing."
    override val filterTitle = "Filter"
    override fun resultCount(n: Int) = if (n == 1) "1 result" else String.format(java.util.Locale.UK, "%,d results", n)
    override val industries = "Industries"
    override val countries = "Countries"
    override val regions = "Regions"
    override val assetClass = "Asset class"
    override val resetFilters = "Reset filters"
    override val filterSearchPlaceholder = "Search stocks, ETFs, categories …"
    override val noFilterResults = "No matches for these filters."
    override fun industryName(key: String) = when (key) {
        "ALKOHOL_TABAK" -> "Alcohol & tobacco"; "BANKEN" -> "Banks & financials"; "BAU" -> "Construction & materials"
        "BERGBAU" -> "Mining & steel"; "HOLDINGS" -> "Holdings & investment companies"; "BIOTECH" -> "Biotechnology"
        "CHEMIE" -> "Chemicals"; "COMPUTER" -> "Computers & networking"; "DIENSTLEISTUNGEN" -> "Diversified services"
        "ELEKTRO" -> "Electrical equipment"; "HALBLEITER" -> "Electronics & semiconductors"; "VERSORGER" -> "Energy & water"
        "FAHRZEUGE" -> "Vehicles"; "LUFTFAHRT" -> "Aerospace & defence"; "FORST" -> "Forestry"
        "GASTGEWERBE" -> "Hospitality & travel"; "GESUNDHEIT" -> "Healthcare & medical"; "HANDEL" -> "Trading companies"
        "IMMOBILIEN" -> "Real estate"; "SOFTWARE" -> "Internet & software"; "EINZELHANDEL" -> "Retail"
        "KONSUM" -> "Consumer goods"; "MASCHINEN" -> "Machinery & equipment"; "NAHRUNG" -> "Food & beverages"
        "PHARMA" -> "Pharma & cosmetics"; "MEDIEN" -> "Media & publishing"; "ROHSTOFFE" -> "Commodities"
        "TELEKOM" -> "Telecommunications"; "TEXTIL" -> "Textiles & apparel"; "UMWELT" -> "Environment & recycling"
        "VERKEHR" -> "Transport & logistics"; "VERSICHERUNG" -> "Insurance"; "OEL_GAS" -> "Oil & gas"
        "SEKTORUEBERGREIFEND" -> "Multi-sector"; "LARGE_CAPS" -> "Large caps"; "MID_CAPS" -> "Mid caps"
        "SMALL_CAPS" -> "Small caps"; "UNTERNEHMENSANLEIHEN" -> "Corporate bonds"; "STAATSANLEIHEN" -> "Government bonds"
        "GELDMARKT" -> "Money market"
        else -> key
    }
    override fun regionName(key: String) = when (key) {
        "ASIEN_PAZIFIK" -> "Asia-Pacific"; "DEUTSCHLAND" -> "Germany"; "EUROPA" -> "Europe"; "GLOBAL" -> "Global"
        "NORDAMERIKA" -> "North America"; "SCHWELLENLAENDER" -> "Emerging markets"
        else -> key
    }
    override fun assetClassName(key: String) = when (key) {
        "INDEX" -> "Indices"; "AKTIE" -> "Stocks"; "ROHSTOFF" -> "Commodities"; "WAEHRUNG" -> "Currencies"; "KRYPTO" -> "Crypto"
        else -> "Other"
    }
    override fun countryLabel(iso: String) = when (iso) {
        "EU" -> "European Union"
        else -> java.util.Locale("", iso).getDisplayCountry(java.util.Locale.UK).ifBlank { iso }
    }

    override fun koRefundOn(date: String) = "Refund $date"
    override fun koAutoSettle(date: String) = "Two days after the knock-out, on $date, the product is removed automatically and the residual value is credited to the cash account. “Settle now” does this immediately."
    override val settleNow = "Settle now"
    override fun settledNow(amount: String) = "Settled, residual value $amount credited"
    override fun firstAddedOn(date: String) = "first $date"

    // News
    override fun newsCategory(k: de.troetenrepublik.app.market.Rss.Kategorie) = when (k) {
        de.troetenrepublik.app.market.Rss.Kategorie.MAERKTE -> "Markets"
        de.troetenrepublik.app.market.Rss.Kategorie.UNTERNEHMEN -> "Companies"
        de.troetenrepublik.app.market.Rss.Kategorie.KONJUNKTUR -> "Economy"
        de.troetenrepublik.app.market.Rss.Kategorie.ZENTRALBANKEN -> "Central banks"
        de.troetenrepublik.app.market.Rss.Kategorie.ROHSTOFFE_DEVISEN -> "Commodities & FX"
        de.troetenrepublik.app.market.Rss.Kategorie.KRYPTO -> "Crypto"
        de.troetenrepublik.app.market.Rss.Kategorie.TRUMP -> "Trump"
        de.troetenrepublik.app.market.Rss.Kategorie.BRIEFE -> "Letters"
        de.troetenrepublik.app.market.Rss.Kategorie.BLOGS -> "Blogs"
        de.troetenrepublik.app.market.Rss.Kategorie.EIGENE -> "Own feeds"
        de.troetenrepublik.app.market.Rss.Kategorie.GEMERKT -> "Saved"
    }
    override fun newsAnbieter(a: de.troetenrepublik.app.market.Rss.Anbieter) = when (a) {
        de.troetenrepublik.app.market.Rss.Anbieter.ALLE -> "All"
        de.troetenrepublik.app.market.Rss.Anbieter.WICHTIG -> "Top stories"
        de.troetenrepublik.app.market.Rss.Anbieter.TRUMP -> "Trump"
        de.troetenrepublik.app.market.Rss.Anbieter.BRIEFE -> "Letters and blogs"
        de.troetenrepublik.app.market.Rss.Anbieter.TAGESSCHAU -> "tagesschau"
        de.troetenrepublik.app.market.Rss.Anbieter.YAHOO -> "Yahoo Finance"
        de.troetenrepublik.app.market.Rss.Anbieter.GOOGLE -> "Google News"
        de.troetenrepublik.app.market.Rss.Anbieter.PRESSE -> "Press"
        de.troetenrepublik.app.market.Rss.Anbieter.CNBC -> "CNBC"
        de.troetenrepublik.app.market.Rss.Anbieter.MARKETWATCH -> "MarketWatch"
        de.troetenrepublik.app.market.Rss.Anbieter.GUARDIAN -> "The Guardian"
        de.troetenrepublik.app.market.Rss.Anbieter.FINNHUB -> "Finnhub"
        de.troetenrepublik.app.market.Rss.Anbieter.ALPHAVANTAGE -> "Alpha Vantage"
        de.troetenrepublik.app.market.Rss.Anbieter.NOTENBANKEN -> "Central banks"
        de.troetenrepublik.app.market.Rss.Anbieter.BEHOERDEN -> "Public bodies"
        de.troetenrepublik.app.market.Rss.Anbieter.EIGENE -> "Own feeds"
        de.troetenrepublik.app.market.Rss.Anbieter.GEMERKT -> "Saved"
    }
    override val newsLoading = "Loading headlines …"
    override val newsEmpty = "There are no headlines here right now."
    override val newsEmptySearch = "No headline matches the search."
    override val newsYesterday = "Yesterday"
    override val newsSearchHint = "Search headlines"
    override val newsSearch = "Search"
    override val newsSources = "Sources and notifications"
    override val newsSourcesHint = "Built in are sources whose terms allow reading in this app with attribution. Own feeds are loaded only on this phone. What a site allows for its feed is set out in its terms of use."
    override val newsOwnFeeds = "Own feeds"
    override val newsAddFeed = "Add feed"
    override val newsFeedUrl = "Feed address (RSS or Atom)"
    override val newsFeedInvalid = "Please enter an address starting with http:// or https://."
    override val newsFeedNone = "No feed was found at this address."
    override fun newsFeedAdded(n: Int) = "Feed added, $n headlines"
    override val newsFeedChecking = "Checking feed …"
    override val newsRemove = "Remove"
    override val newsSave = "Save"
    override val newsUnsave = "Remove from saved"
    override val newsEmptySaved = "Nothing saved yet. The bookmark on a headline keeps it here."

    override fun sectorName(key: String): String = key.replace("—", " - ")
    override fun countryName(key: String): String = key

    private fun ordinal(d: Int): String {
        val suffix = if (d % 100 in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$d$suffix"
    }

    // Copytrading
    override val copyMillion = "m"
    override val copyTitle = "Copytrading"
    override val copyCongress = "House"
    override val copySenate = "Senate"
    override val copyGovernment = "Government"
    override val copySenators = "Senators"
    override val copySenateSource = "Source: U.S. Senate, Electronic Financial Disclosures (efdsearch.senate.gov), Periodic Transaction Reports. Amounts are ranges in US dollars. Paper filings are scanned and are not included in the analyses."
    override val copySenatorOf = "U.S. Senate"
    override val copyGovIntro = "Trades of the US President from his reports to the Office of Government Ethics (Form 278-T), with the amount as a range. Rows without a reliable date, type or amount are left out."
    override fun copyGovSource(stand: String) = "Source: U.S. Office of Government Ethics, OGE Form 278-T and 278e" + (if (stand.isBlank()) "" else ", as of $stand") + ". Amounts are ranges in US dollars."
    override val copyPresident = "President of the United States"
    override val copyVicePresident = "Vice President of the United States"
    override fun copyVanceNote(stichtag: String) = "Holdings according to the annual report (OGE Form 278e) as of $stichtag, with values as ranges. Vance reports hardly any individual trades because funds and ETFs are exempt from the reporting duty for trades. Weights are based on the middle of the ranges."
    override val copyFilingsAll = "Filings"
    override val copyTypeMuniBond = "Municipal bond"
    override fun copyTradesCount(n: Int) = if (n == 1) "1 trade" else "${"%,d".format(java.util.Locale.US, n)} trades"
    override fun copyHoldingsAsOf(date: String) = "Holdings as of $date"
    override val copyWhoTitle = "Who trades it"
    override val copyWhoNone = "No trades by well-known people in the last twelve months"
    override val copyWhoIntro = "Members of the House, senators and the US administration with their reported trades of the last twelve months, investors with the change in their latest reported quarter."
    override val copyTendBuy = "Mostly buying"
    override val copyTendSell = "Mostly selling"
    override val copyTendMixed = "Balanced"
    override fun copyWhoCounts(kaufen: Int, verkaufen: Int, halten: Int) = "$kaufen buying, $verkaufen selling, $halten holding"
    override val copyWhoTendency = "Tendency"
    override val copyWhoPersons = "People"
    override val copyWhoBuyers = "Buying"
    override val copyWhoSellers = "Selling"
    override fun copyWhoHouseOpen(offen: Int, gesamt: Int) = "The app has not read $offen of $gesamt House filings from the last twelve months yet. Their trades are still missing here."
    override val copyWhoHouseLoad = "Read all filings (once, then kept on the device)"
    override fun copyWhoHouseReading(fertig: Int, gesamt: Int) = "Reading filings: $fertig of $gesamt"
    override val copyWhoSource = "Sources: filings with the US House, the Senate, the ethics office and the securities regulator. Amounts are estimates from the reported ranges. In detail: Clerk of the U.S. House of Representatives, U.S. Senate (efdsearch.senate.gov), U.S. Office of Government Ethics, SEC EDGAR (Form 13F-HR)."
    override fun copyWhoShare(pct: String) = "$pct of the portfolio"
    override fun copyWhoTrades(kaeufe: Int, verkaeufe: Int) =
        listOfNotNull(
            if (kaeufe == 0) null else if (kaeufe == 1) "1 buy" else "$kaeufe buys",
            if (verkaeufe == 0) null else if (verkaeufe == 1) "1 sale" else "$verkaeufe sales",
        ).joinToString(", ")
    override val copyWhoMixed = "Mixed"
    override fun copyWhoStueck(stichtag: String, veraenderung: String, bestand: String, wert: String) = "Quarter to $stichtag: $veraenderung shares, holding $bestand shares ($wert)"
    override fun copyWhoStueckNeu(stichtag: String, bestand: String, wert: String) = "Quarter to $stichtag: new position, $bestand shares ($wert)"
    override fun copyWhoStueckWeg(stichtag: String, vorher: String) = "Quarter to $stichtag: all $vorher shares sold"
    override fun copyWhoAlle(n: Int) = "Show all $n trades"
    override fun copyWhoAllePersonen(n: Int) = "Show all $n people"
    override val copyWhoWeniger = "Show fewer"
    override val copyWhoHolds = "Holds"
    override val copyWhoNewPos = "New position"
    override val copyWhoIncreased = "Increased"
    override val copyWhoReduced = "Reduced"
    override val copyWhoSoldOut = "Sold out"
    override val copyWhoUnchanged = "Held unchanged"
    override val copyPhotoCongress = "Photo: U.S. Government Publishing Office, public domain"
    override val copyBellOn = "Notify me"
    override val copyBellOff = "Stop notifications"
    override fun copyBellOnToast(name: String) = "Notifications for $name on"
    override fun copyBellOffToast(name: String) = "Notifications for $name off"
    override val copyBellNotificationsOff = "Notifications are switched off in the profile. You can switch them on there."
    override val copyBellsTitle = "Copytrading notifications"
    override fun copyBellsSubtitle(n: Int) = when (n) { 0 -> "For new filings of people with a bell"; 1 -> "1 person with a bell"; else -> "$n people with a bell" }
    override val copyBellsIntro = "The bell at the top of a person's page switches on notifications for their new filings. The app checks about every six hours, even when it is closed. For senators it notifies you when a new version of the app brings new filings."
    override val copyBellsIntroDesktop = "The bell at the top of a person's page switches on notifications for their new filings. The app checks every six hours while it is open. For senators it notifies you when a new version of the app brings new filings."
    override val copyBellsEmpty = "No one with a bell yet. The bell sits at the top of every person's page."
    override val copyBellsAdd = "Find people in Copytrading"
    override val copyBellHouse = "House · checks the Clerk's index"
    override val copyBellSenate = "Senate · with every new version of the app"
    override val copyBellGovernment = "Administration · checks whitehouse.gov"
    override val copyBellInvestor = "Investor · checks the SEC for new 13F filings"
    override val imTitel = "Import register data"
    override val imUntertitel = "Insider and short-selling lists you downloaded yourself (BSE India, Consob Italy, Bundesanzeiger, converted SEDI reports) and notifications under Art. 19 MAR as PDF. They stay on the device."
    override fun imInsider(n: Int, land: String) = "$n insider transactions (${landName(land)}) imported"
    override fun imLeer(n: Int, land: String) = "Short positions for $n stocks (${landName(land)}) imported"
    override val imPdf = "The app found no readable form under Art. 19 MAR in this PDF. Please have SEDI reports converted first."
    override val imUnbekannt = "The app does not recognise this file. It reads the BSE list (CSV), the Consob table (XLSX), the Bundesanzeiger short-selling list (CSV), notifications under Art. 19 MAR (PDF) and register files of the app (JSON)."
    private fun landName(l: String) = when (l) {
        "IN" -> "India"; "CA" -> "Canada"; "IT" -> "Italy"; "DE" -> "Germany"; "AU" -> "Australia"; "HK" -> "Hong Kong"
        "DK" -> "Denmark"; "FI" -> "Finland"; "IS" -> "Iceland"; "EE" -> "Estonia"; "LV" -> "Latvia"; "LT" -> "Lithuania"; "CZ" -> "Czechia"; "LU" -> "Luxembourg"
        "CY" -> "Cyprus"; "HU" -> "Hungary"; "PL" -> "Poland"; "LI" -> "Liechtenstein"; "MT" -> "Malta"; "HR" -> "Croatia"; "AT" -> "Austria"; "BE" -> "Belgium"
        "ES" -> "Spain"; "PT" -> "Portugal"; "IE" -> "Ireland"; "GR" -> "Greece"; "SI" -> "Slovenia"; "SK" -> "Slovakia"; "RO" -> "Romania"; "BG" -> "Bulgaria"; "NO" -> "Norway"
        else -> l
    }
    override val lwTitel = "Lobbying in Europe"
    override val lwHinweis = "How much the company spends a year, according to the lobby registers, to influence laws and authorities. The registers give ranges."
    override fun lwRegister(l: String) = when (l) { "EU" -> "EU Transparency Register"; "DE" -> "German Bundestag lobby register"; "FR" -> "French lobby register"; else -> l }
    override fun lwSpanne(von: String, bis: String) = "$von to $bis"
    override fun lwAb(von: String) = "from $von"
    override fun lwJahr(von: String, bis: String) = "Financial year $von to $bis"
    override fun lwFte(n: String) = "$n full-time positions for lobbying"
    override fun lwTaetigkeiten(n: Int) = if (n == 1) "1 reported activity" else "$n reported activities"
    override fun lwThemen(t: String) = "Topics: $t"
    override val lwEintrag = "Open the register entry"
    override fun lwQuelle(laender: List<String>) = "Source: " + laender.joinToString(", ") { when (it) {
        "EU" -> "EU Transparency Register (transparency-register.europa.eu)"
        "DE" -> "Lobby register of the German Bundestag (lobbyregister.bundestag.de)"
        "FR" -> "HATVP, Répertoire des représentants d'intérêts (hatvp.fr, Licence Ouverte)"
        else -> it
    } }
    override fun copyNotifOge(n: Int) = (if (n == 1) "One new report" else "$n new reports") + " at the US Office of Government Ethics. The trades arrive, already read, with the next version of the app."
    override val importPortfolioJson = "Import portfolio (backup or broker export)"
    override val exportPortfolioJson = "Export portfolio (JSON)"
    override val importChoice = "Merge adds the file's cash, positions, transactions, savings plans and watchlist to the current portfolio. Replace swaps the current portfolio for the file."
    override val importMerge = "Merge"
    override val importReplace = "Replace"
    override val depotMerged = "Portfolios merged"
    override fun copyPhotoCredit(autor: String, lizenz: String) = "Photo: $autor, $lizenz, via Wikimedia Commons"
    override val copyPhotosCongress = "Photos: U.S. Government Publishing Office (public domain)."
    override val copyPhotosCommons = "Photos: Wikimedia Commons, author and license are shown on each person's page. Without a free photo, the initials are shown."
    override val copyInvestors = "Investors"
    override val copyStrategies = "Strategies"
    override val copyCongressIntro = "Trades by members of the US House of Representatives under the STOCK Act, reported up to 45 days later, with the amount as a range."
    override val copySearchMember = "Name or state (e.g. CA)"
    override val copyLoading = "Loading filings …"
    override val copyUnavailable = "The data is not reachable right now. Try again later."
    override val copyLatestTrades = "Latest trades"
    override val copyMembers = "Members"
    override fun copyFilings(n: Int) = if (n == 1) "1 filing" else "$n filings"
    override fun copyLast(date: String) = "last $date"
    override val copyCongressSource = "Source: Clerk of the U.S. House of Representatives, Financial Disclosure Reports. Amounts are estimates from the ranges."
    override val copyFollowing = "Following"
    override val copySell = "Sale"
    override val copyPartialSale = "Partial sale"
    override val copyExchange = "Exchange"
    override val copyTypeOption = "Option"
    override val copyTypeGovBond = "Government bond"
    override val copyTypeCorpBond = "Bond"
    override val copyTypeFund = "Fund"
    override fun copyOver(v: String) = "over $v"
    override fun copyHouseOf(district: String) = "House of Representatives · $district"
    override val copyFilings12m = "Filings (12 months)"
    override val copyBuysEst = "Purchases (est.)"
    override val copySalesEst = "Sales (est.)"
    override val copyReplicate = "Copy"
    override val copyMostTraded = "Most traded"
    override fun copyBuySellEst(buy: String, sell: String, n: Int) = "Bought ≈ $buy, sold ≈ $sell · " + if (n == 1) "1 trade" else "$n trades"
    override val copyAllTrades = "All trades"
    override fun copyFiledOn(date: String) = "Filed on $date"
    override fun copyPaper(n: Int, dates: String) = if (n == 1) "One filing was submitted on paper ($dates). It exists only as an image, so the app cannot read its content." else "$n filings were submitted on paper ($dates). They exist only as images, so the app cannot read their content."
    override val copyNoFilings = "No filings in the last twelve months."
    override fun copyNote(name: String) = "Copied: $name"
    override val copyFollow = "Follow"
    override val copyUnfollow = "Unfollow"
    override val copyInvestorsIntro = "Holdings of well-known investors from their 13F filings with the SEC, quarterly up to 45 days after the quarter ends, US securities only. The app tells you when a newer filing arrives."
    override fun copyInvestorLine(date: String, n: Int, value: String) = "As of $date · $n positions · $value"
    override val copyInvestorsSource = "Source: SEC EDGAR, Form 13F-HR. Values in US dollars at the reporting date. Options are shown at the value of the underlying shares. Cash, short positions and shares outside the US are not part of these filings."
    override fun copyNewer13f(asOf: String, filed: String, shown: String) = "The SEC has a newer filing as of $asOf (filed on $filed). This page still shows $shown. The new list comes with the next app update."
    override val copyAsOf = "As of"
    override val copyFiled = "Filed"
    override val copyHoldings = "Holdings"
    override val copySoldOut = "Sold since the previous quarter"
    override val copyCall = "Call option"
    override val copyPut = "Put option"
    override val copyNew = "new"
    override fun copyIncreased(pct: String) = "increased by $pct"
    override fun copyReduced(pct: String) = "reduced by $pct"
    override val copySold = "sold"
    override val copyStrategiesIntro = "Investment strategies as model portfolios of ETFs. The app calculates the figures from the prices of the last ten years."
    override val copyDefensive = "Defensive"
    override val copyBalanced = "Balanced"
    override val copyOffensive = "Growth"
    override val copyStrategiesNote = "Past performance without costs and taxes, with the same weights every day. Payouts of distributing ETFs are not included."
    override val copyAllocation = "Allocation"
    override val copyHistory = "Performance"
    override val copyNoHistory = "No prices are available for these ETFs right now."
    override fun copyRange(from: String, to: String) = "$from to $to"
    override val copyMaxDrawdown = "Largest drawdown"
    override val copyYears = "By calendar year"
    override val copyAsPlans = "Set up as savings plans"
    override val copyFollowedIntro = "The app sends a notification when new filings come in, as long as notifications are switched on."
    override fun copyReplicateTitle(name: String) = "Copy $name"
    override val copyReplicateHint = "The amount is split across the holdings by weight. Outside trading hours the app places limit orders 2% above the last price, which execute at the next open."
    override fun copyCash(v: String) = "Available: $v"
    override val copyCount = "Holdings"
    override val copyNothingToCopy = "No shares to copy."
    override fun copyFees(n: Int, v: String) = (if (n == 1) "1 purchase" else "$n purchases") + ", fees $v"
    override val copyWorking = "Working …"
    override val copyReplicateNow = "Copy now"
    override fun copyPlansTitle(name: String) = "Savings plans for $name"
    override val copyPlansHint = "One savings plan per ETF, with the monthly amount split by weight. Savings plans are free of charge."
    override val copyMonthly = "Monthly"
    override val copyPlanDay = "Executes on day"
    override val copyPlansCreate = "Create savings plans"
    override fun copyDone(bought: Int, orders: Int) = "Copied: $bought bought" + if (orders > 0) ", $orders limit orders placed" else ""
    override fun copyPlansDone(n: Int) = if (n == 1) "1 savings plan created" else "$n savings plans created"
    override fun copyNotifTitle(name: String) = "New filing: $name"
    override fun copyNotifTrades(n: Int, list: String) = (if (n == 1) "1 trade: " else "$n trades: ") + list
    override val copyNotifPaper = "Filed on paper, so the content cannot be read."
    override fun copyNotif13f(asOf: String, filed: String) = "New 13F filing as of $asOf, filed on $filed."

    // Amounts in US dollars
    override val switchCurrency = "Switch the display currency"
    override val usdUnavailable = "The dollar rate is not available right now."
    override val regLandTitel = "Where do you live?"
    override val regLandText = "Currency and trading hours follow from it. You can change both later in the profile."
    override val regVorschlag = "Suggested from your device's time zone"
    override val regAndere = "Other countries"
    override val regWaehrungTitel = "Your currency"
    override val regWaehrungText = "The app shows your portfolio, prices and cash in it. Tapping the portfolio value switches on to US dollars and euros. Orders, taxes and documents stay in euros."
    override fun waehrungName(code: String) = when (code) {
        "EUR" -> "Euro"; "USD" -> "US dollar"; "CHF" -> "Swiss franc"; "GBP" -> "British pound"; "SEK" -> "Swedish krona"
        "DKK" -> "Danish krone"; "NOK" -> "Norwegian krone"; "PLN" -> "Polish złoty"; "CZK" -> "Czech koruna"; "HUF" -> "Hungarian forint"
        "CAD" -> "Canadian dollar"; "MXN" -> "Mexican peso"; "BRL" -> "Brazilian real"; "JPY" -> "Japanese yen"; "CNY" -> "Chinese yuan"
        "HKD" -> "Hong Kong dollar"; "TWD" -> "Taiwan dollar"; "KRW" -> "South Korean won"; "INR" -> "Indian rupee"; "SGD" -> "Singapore dollar"
        "ILS" -> "Israeli shekel"; "ZAR" -> "South African rand"; "AUD" -> "Australian dollar"; "NZD" -> "New Zealand dollar"
        else -> code
    }
    override val regZeitenTitel = "Trading hours"
    override val regZeitenText = "When orders are executed, in your local time. Crypto always trades around the clock. The app does not observe public holidays."
    override fun regZeitenLand(land: String) = "Like the stock exchange in $land"
    override fun regZeitenLandHint(boerse: String, zeiten: String, deutschland: Boolean) =
        if (deutschland) "$boerse: stocks and ETFs 7:30–23:00, derivatives 8:00–22:00" else "$boerse, weekdays $zeiten local time"
    override val regZeitenEigene = "Custom hours"
    override val regZeitenEigeneHint = "Weekdays within the period you set"
    override val regZeitenImmer = "Around the clock"
    override val regZeitenImmerHint = "Every day, weekends included"
    override fun regVonBis(von: String, bis: String) = "Weekdays $von–$bis"
    override val regionTitel = "Country, currency and trading hours"
    override val regAnzeige = "Shown now"
    override val regAnzeigeHint = "Tapping the portfolio value, the cash balance or a price switches on. Bookings are in euros."
    override fun regSteuerHinweis(land: String) = "The app calculates taxes under German law (flat-rate withholding tax, saver's allowance). That does not apply in $land, so amounts after tax are then only an example."
    override fun regWissenUs(n: Int) = "In the finance course, $n lessons are adapted for the United States, for example on taxes, retirement and credit. Each says so at the top and can show the German version."
    override fun fxRate(von: String, nach: String, kurs: String): String {
        fun z(c: String) = de.troetenrepublik.app.engine.Derivatives.currencySymbol(c)
        fun betrag(c: String, v: String) = if (z(c).length == 1) z(c) + v else "$v ${z(c)}"
        return "${betrag(von, "1")} = ${betrag(nach, kurs)}"
    }
    override fun tradingHoursFor(aktien: String?, derivate: String?, wochenende: Boolean, ortszeit: Boolean) =
        if (aktien == null) "Everything around the clock, weekends included"
        else listOfNotNull("Stocks & ETFs $aktien", derivate?.takeIf { it != aktien }?.let { "Derivatives $it" }, "Crypto around the clock").joinToString("\n") +
            (if (ortszeit) "\nLocal time, weekdays" else "")
    override val rrTitel = "How far can it fall"
    override val rrLaedt = "Loading ten years of prices …"
    override val rrKeine = "There are too few prices for this security."
    override fun rrRendite(jahre: Int) = if (jahre == 1) "Return over the last year" else "Return per year, $jahre years"
    override val rrLaengster = "Longest drawdown"
    override fun rrDauer(tage: Long): String {
        val j = (tage / 365.25).toInt()
        val m = ((tage - j * 365.25) / 30.44).toInt()
        return when {
            j > 0 && m > 0 -> "$j ${if (j == 1) "year" else "years"} $m ${if (m == 1) "month" else "months"}"
            j > 0 -> "$j ${if (j == 1) "year" else "years"}"
            m > 0 -> "$m ${if (m == 1) "month" else "months"}"
            else -> "$tage ${if (tage == 1L) "day" else "days"}"
        }
    }
    override val rrLaeuft = "(ongoing)"
    override fun rrBeta(index: String) = "Beta to the $index"
    override fun rrKorrelation(index: String) = "Correlation with the $index"
    override fun rrHinweis(ab: String, index: String?, beta: String?) =
        "From closing prices since $ab, without dividends. Largest drawdown: the deepest fall below an earlier high. Longest drawdown: the longest time until an earlier high was reached again." +
            (if (index != null && beta != null) " A beta of $beta means: when the $index moved by 1%, the security moved by $beta% on average. Correlation ranges from 1 (move together) to −1 (move in opposite directions). Both from weekly prices over the last five years." else "") +
            ""
    override val trendsTitel = "Trends"
    override val trBranchenHinweis = "Measured by US sector ETFs, prices without dividends. They only roughly represent the sector worldwide. Tap for the history."
    override val trReddit = "Talked about on Reddit and 4chan"
    override val trRedditHinweis = "Mentions in posts and comments according to ApeWisdom.\n" +
        "• Stocks: all stock subreddits such as r/wallstreetbets, r/stocks and r/investing\n" +
        "• Crypto: crypto subreddits such as r/CryptoCurrency and r/Bitcoin\n" +
        "• 4chan /biz/: the business board of 4chan\n" +
        "• 24 hrs: percent compared with the 24 hours before\n" +
        "• Week, month and year: from the history the app records itself\n" +
        "A lot of attention often comes with sharp price swings."
    override val trFilterDeutsch = "German subs"
    override val sbTitel = "Mood barometer"
    override val sbCnn = "Fear & Greed stocks (US)"
    override val sbQuelleCnn = "Source: CNN"
    override val sbAaii = "AAII retail investors (US)"
    override val sbQuelleAaii = "Source: AAII"
    override fun sbAaiiMehr(bullen: String, neutral: String, baeren: String, datum: String) =
        "Survey of $datum: $bullen% expect rising prices over the next six months, $neutral% no big change, $baeren% falling prices. The barometer shows the share of bulls among bulls and bears. Long-term average about 37% bulls and 31% bears."
    override val sbPutCall = "Put/call ratio"
    override fun sbPutCallMehr(wert: String) = "On the last trading day, $wert puts traded for every call on US stocks. Many puts point to fear, few to greed. A reading of 100 matches a ratio of 0.4 or lower, a reading of 0 a ratio of 1.0 or higher."
    override fun wqPcZeile(gesamt: String?, index: String?, tag: String) =
        listOfNotNull(gesamt?.let { "All options $it" }, index?.let { "Indices $it" }, "Trading day $tag").joinToString(" · ")
    override val wqPcQuelle = "Source: Cboe · © Cboe Exchange, Inc."
    override val sbKrypto = "Fear & Greed crypto"
    override val sbWsb = "Reddit r/wallstreetbets"
    override val sbQuelleKrypto = "Source: alternative.me"
    override val sbQuelleWsb = "Source: Tradestie"
    override fun sbRedditWert(prozent: Int) = "$prozent% of comments with a clear tone are positive. The value does not predict the price. The 5-day price change is shown next to each stock."
    override fun sbStufe(stufe: Int) = listOf("Extreme fear", "Fear", "Neutral", "Greed", "Extreme greed").getOrElse(stufe) { "" }
    override fun sbTeil(schluessel: String) = when (schluessel) {
        "momentum" -> "S&P 500 momentum"
        "breite" -> "Market breadth"
        "vix" -> "Volatility index VIX"
        "stress" -> "Financial stress"
        "sicher" -> "Stocks vs government bonds"
        "market_momentum_sp500" -> "S&P 500 momentum"
        "stock_price_strength" -> "New 52-week highs and lows"
        "stock_price_breadth" -> "Market breadth"
        "put_call_options" -> "Put/call ratio"
        "market_volatility_vix" -> "Volatility (VIX)"
        "junk_bond_demand" -> "Junk bond demand"
        "safe_haven_demand" -> "Safe haven demand"
        else -> schluessel
    }
    override val sbVortag = "Previous day"
    override val sbWoche = "Week"
    override val sbMonat = "Month"
    override val sbWsbSteigend = "Discussed positively"
    override val sbWsbFallend = "Discussed negatively"
    override val sbHinweis = "The barometers range from 0 (extreme fear) to 100 (extreme greed).\n\n" +
        "• US stocks: CNN Fear & Greed from seven measures\n" +
        "• Overall sentiment: all gauges, the early warning signs and further free sentiment measures with weights\n" +
        "• US retail investors: share of optimists in the weekly AAII survey\n" +
        "• Put/call: puts per call on US stocks at Cboe on the last trading day\n" +
        "• Crypto: Fear & Greed by alternative.me\n" +
        "• Worries: how much is read about recession and stock market crashes on Wikipedia\n" +
        "• r/wallstreetbets: share of comments on the 50 most discussed securities that lean bullish\n" +
        "• Safe havens: how much better stocks do than government bonds, compared with the past year, from Yahoo Finance\n" +
        "• Volatility: how calm the VIX is compared with the past year, from Yahoo Finance\n\n" +
        "Tap a tile for history and details."
    override fun sbKachel(schluessel: String) = when (schluessel) {
        "cnn" -> "US stocks"; "aktien" -> "US stocks"; "gesamt" -> sgKachel; "krypto" -> "Crypto"; "aaii" -> "US retail investors"; "wsb" -> "r/wallstreetbets"; "sicher" -> "Safe havens"; "sorgen" -> "Worries"; "vix" -> "Volatility"; "putcall" -> "Put/call"; else -> ""
    }
    override fun sbAnbieter(schluessel: String) = when (schluessel) {
        "cnn" -> "CNN Fear & Greed"; "gesamt" -> sgAnbieter; "aktien" -> "Own index"; "krypto" -> "alternative.me"; "aaii" -> "AAII survey"; "wsb" -> "Tradestie"; "sicher" -> "S&P 500 vs bonds"; "sorgen" -> "Wikipedia"; "vix" -> "VIX via Yahoo"; "putcall" -> "Cboe options"; else -> ""
    }
    override fun sbSteigend(prozent: Int) = "$prozent% positive"
    override val fwTitel = "Early warning signs"
    override fun fwZusammen(rot: Int, gelb: Int, n: Int) = when {
        rot == 0 && gelb == 0 -> "All $n green"
        rot == 0 -> "$gelb of $n yellow, none red"
        gelb == 0 -> "$rot of $n red"
        else -> "$rot of $n red, $gelb yellow"
    }
    override fun fwName(schluessel: String) = when (schluessel) {
        "zinskurve" -> "US yield curve"
        "sahm" -> "Sahm rule"
        "vix" -> "Volatility VIX"
        "vix_struktur" -> "VIX term structure"
        "trend_sp500" -> "Trend S&P 500"
        "trend_dax" -> "Trend DAX"
        "stress" -> "Financial stress"
        "hochzins" -> "US high-yield spread"
        else -> schluessel
    }
    override fun fwLage(schluessel: String, ampel: Int) = when (schluessel) {
        "zinskurve" -> listOf("Normal, longer maturities pay more", "Inverted, short maturities pay more", "Normal again after inversion, often shortly before recessions")[ampel]
        "sahm" -> listOf("Labour market stable", "Unemployment rising noticeably", "Threshold of 0.5 reached, typical for recessions")[ampel]
        "vix" -> listOf("Calm market", "Elevated nervousness", "High fear in the market")[ampel]
        "vix_struktur" -> listOf("Normal, more uncertainty ahead than today", "Almost flat, short-term worry growing", "Inverted, acute market stress")[ampel]
        "trend_sp500", "trend_dax" -> listOf("Well above the 200-day line", "Just above the 200-day line", "Below the 200-day line")[ampel]
        "stress" -> listOf("Less stress than on average", "More stress than on average", "Clearly elevated stress")[ampel]
        "hochzins" -> listOf("Low spread, lenders relaxed", "Spread elevated or rising", "Spread high or risen quickly")[ampel]
        else -> ""
    }
    override fun fwErklaerung(schluessel: String) = when (schluessel) {
        "zinskurve" -> "Yield of 10-year US Treasuries minus the 3-month yield. It was below zero for a while before every US recession since 1968, with a lead of about 6 to 24 months. The recession often starts only when the curve rises again. Weakness: from 2022 to 2024 it was inverted for a long time without a recession so far."
        "sahm" -> "Three-month average of the US unemployment rate minus its low of the previous twelve months. At 0.5 percentage points the rule has flagged every US recession early since 1970. Weakness: in 2024 the threshold was briefly reached mainly because more people were looking for work."
        "vix" -> "Expected 30-day fluctuation of the S&P 500, derived from option prices. Readings above 30 occur almost only in downturns. The VIX warns during a slump rather than before it. Very low readings show complacency."
        "vix_struktur" -> "VIX divided by the three-month VIX3M. Usually uncertainty over three months is higher than over 30 days and the value is below 1. Above 1, investors expect more fluctuation now than later. This happened in almost every major slump."
        "stress" -> "Weekly index of the St. Louis Fed built from 18 market series, among them interest rates and six yield spreads between risky and safe bonds. Zero is the average since 1993, one is a standard deviation above it. Rising spreads show that lenders demand more for risk, which often happened before and during sell-offs. Weakness: it rises with the stress and gives little warning ahead of it."
        "hochzins" -> "Extra yield that US companies with weak credit (below BBB) pay over Treasuries. When it rises quickly, lenders pull back from risky loans. This preceded or accompanied the downturns of 2000, 2007 and 2020. A very low reading shows complacency. Red from 6 points or 2 points above the low of the last twelve months, yellow from 4.5 or 1 point above it. FRED shows only the last three years."
        "trend_sp500", "trend_dax" -> "Distance of the index from its average of the last 200 trading days. Nearly all major bear markets began with a fall below this line. Weakness: many false signals in sideways markets."
        else -> ""
    }
    override val b13107Titel = "US disclosure reports"
    override val b13107Text = "Here the app shows financial reports of US representatives, senators and members of the administration. To do so it loads reports from the House of Representatives, the Senate, the Office of Government Ethics and the White House. Under 5 U.S.C. § 13107 these reports may not be used for any commercial purpose, to determine anyone's credit rating, or to solicit money.\n\nBy tapping “Confirm” you declare that you use them privately only. The app will not ask again."
    override val b13107Ok = "Confirm"
    override val b13107Zurueck = "Back"
    override fun blsHinweis(datum: String) = "BLS data retrieved on $datum. BLS.gov cannot vouch for the data or analyses derived from these data after the data have been retrieved from BLS.gov."
    override fun fwQuelle(schluessel: String) = when (schluessel) {
        "zinskurve" -> "Source: US Department of the Treasury, Daily Treasury Par Yield Curve Rates"
        "sahm" -> "Source: U.S. Bureau of Labor Statistics, unemployment rate (LNS14000000)"
        "vix", "vix_struktur" -> "Source: Cboe indices VIX and VIX3M via Yahoo Finance"
        "trend_sp500" -> "Source: S&P 500 via Yahoo Finance"
        "stress" -> "Source: Federal Reserve Bank of St. Louis, St. Louis Fed Financial Stress Index (STLFSI4), via FRED"
        "hochzins" -> "Source: ICE Data Indices, LLC, ICE BofA US High Yield Index Option-Adjusted Spread (BAMLH0A0HYM2), retrieved from FRED, Federal Reserve Bank of St. Louis"
        "trend_dax" -> "Source: DAX via Yahoo Finance"
        else -> ""
    }
    override val fwPunkte = "pts"
    override val fwHinweis = "None of these measures predicts a crash reliably. They are the ones with the best track record over decades.\n\n" +
        "• The yield curve and the Sahm rule warn of recessions, which often come with falling prices\n" +
        "• VIX, VIX term structure and the 200-day line show whether the market is already under stress\n\n" +
        "Several signals at red at once deserve more attention than one alone."
    override fun redditMeldungTitel(name: String) = "$name is being discussed a lot on Reddit"
    override fun redditMeldungText(jetzt: Int, vorher: Int) = "$jetzt mentions in German stock subreddits in 24 hours, $vorher before."
    override val trRedditDeHinweis = "German subs: r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien and r/Finanzen through your own Reddit access (Profile › Access keys). The app reads the latest 100 posts and counts which securities from the catalogue, your portfolio and your watchlist they mention. A security counts once per post. Percent compared with the 24 hours before only when the posts reach back that far."
    override val trRedditBeitraege = "Reddit posts"
    override fun trRedditAnzahl(n: Int) = if (n == 1) "1 post in the last month" else "$n posts in the last month"
    override val trRedditKeineBeitraege = "No posts in the last month in r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien, r/Finanzen, r/wallstreetbets, r/stocks and r/investing."
    override fun trRedditStimmung(steigend: Int, fallend: Int, offen: Int) = "Mood by simple word rules: $steigend rather rising, $fallend rather falling, $offen without a clear direction."
    override fun trRedditPunkte(punkte: Int, kommentare: Int) = "$punkte points, $kommentare comments"
    override val trRedditKeine = "Reddit data is not reachable right now. Try again later."
    override val trLaedt = "Loading …"
    override val trMehr = "Show more"
    override fun trRang(rang: Int, pfeil: String, ticker: String) = "Rank $rang$pfeil · $ticker"
    override fun trErwaehnungen(n: Int) = if (n == 1) "1 mention" else "${"%,d".format(n)} mentions"
    override val trYahoo = "Trending on Yahoo Finance"
    override val trYahooHinweis = "The most viewed securities on Yahoo Finance in your region and in the US, plus the most actively traded US stocks."
    override val trWiki = "Interest on Wikipedia"
    override fun trWikiProTag(n: String) = "avg. $n views a day"
    override val trWikiVergleich = "last 7 days compared with the 4 weeks before"
    override fun trRedditZeit(tage: Int) = when (tage) { 1 -> "24 hrs"; 7 -> "Week"; 30 -> "Month"; else -> "Year" }
    override fun trSpitze(datum: String) = "peak on $datum"
    override fun trSpitzeWoche(datum: String) = "peak in the week of $datum"
    override fun trSpitzeTag(datum: String, n: String) = "Most discussed on $datum: $n mentions"
    override fun trVerlaufSeit(datum: String?) = if (datum == null)
        "No history yet: ApeWisdom only reports the last 24 hours, so the app records the mentions every day from now on, in the background too. Week, month and year fill up over time."
    else "History since $datum: ApeWisdom only reports the last 24 hours, so the app records the mentions every day, in the background too. Week, month and year fill up over time."
    override val trErwaehnungenKurz = "mentions"
    override fun trWertHeute(rang: Int, pfeil: String, veraenderung: String?) =
        "Today rank $rang$pfeil" + (veraenderung?.let { " · $it compared with the 24 hours before" } ?: " · new")
    override val trWertHeuteNicht = "Not among the 100 most discussed today"
    override fun trWertSummen(woche: String, monat: String, jahr: String) = "Mentions: week $woche · month $monat · year $jahr"
    override val trWertYahooText = "Among the most viewed securities on Yahoo Finance in your region or in the US."
    override val orderGeaendert = "Order changed"
    override val orderAendern = "Edit"
    override val orderAendernSpeichern = "Save changes"
    override fun trImportTitel(quelle: String) = "Import from $quelle?"
    override val trImportLaedt = "Reading transactions and matching them to prices"
    override fun trImportZeitraum(von: String, bis: String, zeilen: Int) = "Export from $von to $bis, $zeilen rows"
    override fun trImportOhneKurs(liste: String) = "No price available, not added to the portfolio (so their value is missing): $liste"
    override fun trImportOhneKauf(liste: String) = "Sold without the purchase being in the file (the export probably starts later), counted as bought at the sale price: $liste"
    override val trImportHinweis = "Real Trade Republic derivatives (knock-outs, warrants, factor certificates) appear with purchases, sales and redemptions in transactions and portfolio history, there are no prices for them. Create the export in the Trade Republic app under Profile > Statements > Transaction export."
    override val brokerImportHinweis = "Imported holdings count as one purchase lot at their average price, later purchases in the app follow FIFO. Securities without a Yahoo price appear with their transactions in the activity list and the portfolio history, but not in the portfolio."
    override fun trImportiert(quelle: String, n: Int) = "$quelle export imported: $n transactions."
    override val importWahlSicherung = "Backup from this app"
    override val importWahlSicherungText = "JSON file you saved with Export portfolio"
    override val importWahlBroker = "Export from your broker"
    override fun importWahlBrokerText(namen: String) = "CSV file with your transactions from $namen"
    override val importWahlAnleitung = "How to get the export:\n• Trade Republic: in the app under Profile > Statements > Transaction export\n• Scalable Capital: in the browser under Broker > Transactions > Export (with PRIME or PRIME+)\n• Trading 212: Menu > History > Export with orders, dividends, transactions and interest, at most 12 months per file (add further files with Merge)\n• DEGIRO: in the browser under Inbox > Transactions and Inbox > Account statement, each as CSV, import the transactions first, then add the account statement with Merge\n• Interactive Brokers: a Flex Query with the sections Trades and Cash Transactions as CSV, under Performance & Reports > Flex Queries\n• Revolut: Invest > More > Documents > Account statement in Excel format\n• ING: Order manager > Executed orders > Export\n• flatex: securities transactions (Depotumsätze) as CSV\n• Bitpanda: History > Export as CSV, crypto plus deposits and withdrawals in euros are read\n\nFor ING and flatex the file only holds orders without fees, so the app books the cash they need as a deposit. The file is only read on this device."
    override fun importUnbekannt(namen: String) = "This file is neither a backup of this app nor a known export. Exports from $namen can be read."

    // Prediction markets
    override val pmTitle = "Prediction Markets"
    override val pmMyBets = "My bets"
    override val pmSearchHint = "Search markets"
    override val pmLoading = "Loading markets …"
    override val pmUnreachable = "The markets are not reachable right now."
    override val pmNoResults = "No markets found."
    override val pmMore = "Load more"
    override fun pmDataNote(quelle: String) = "Prices from $quelle, possibly delayed. Bets use practice money only."
    override fun pmVolume(v: String) = "Volume $v"
    override fun pmEnds(d: String) = "ends $d"
    override val pmIn24h = "in 24h"
    override val pmYes = "Yes"
    override val pmBet = "Bet"
    override val pmOutcomes = "Outcomes"
    override val pmPlaceBet = "Place bet"
    override val pmAmount = "Stake"
    override fun pmSideAt(seite: String, kurs: String) = "“$seite” at $kurs per share"
    override fun pmChanceOf(seite: String) = "chance of “$seite”"
    override val pmSharesLabel = "Shares"
    override val pmPayoutIf = "Payout if it happens"
    override val pmProfitIf = "Profit if it happens"
    override val pmPracticeNote = "Practice money: a share pays €1 if the outcome happens, otherwise nothing. The price equals the probability the market assigns. No fees, no tax."
    override fun pmBought(betrag: String, seite: String) = "$betrag placed on “$seite”"
    override fun pmSold(betrag: String) = "Bet closed, $betrag credited"
    override fun pmFehler(code: String) = when (code) {
        "BETRAG" -> "The minimum stake is €1."
        "KURS" -> "There is no valid price for this market right now."
        "GUTHABEN" -> "Your cash is not sufficient."
        "GESCHLOSSEN" -> "This bet is already closed."
        "NETZ" -> "The market cannot be reached right now. Nothing was sold."
        else -> "The bet could not be placed."
    }
    override val pmSellTitle = "Sell bet"
    override fun pmSellText(erloes: String, geldkurs: String, mitte: String) =
        "All shares are sold at the bid of $geldkurs. That brings $erloes now. At the displayed price they would be worth $mitte."
    override val pmSellLaedt = "Loading the current price …"
    override fun pmSellDuenn(beiEintreten: String) =
        "Buyers are bidding well below the displayed price right now. If you keep the bet, you receive $beiEintreten if it happens, otherwise nothing."
    override val pmClosed = "Closed"
    override val pmOpenBets = "Open bets"
    override val pmValue = "Current value"
    override val pmResultOpen = "Result of open bets"
    override val pmResultClosed = "Result of closed bets"
    override val pmResultTotal = "Total result"
    override fun pmClosedCount(n: Int) = if (n == 1) "1 bet sold or settled" else "$n bets sold or settled"
    override fun pmStatus(status: String) = when (status) {
        "GEWONNEN" -> "Won"; "VERLOREN" -> "Lost"; "VERKAUFT" -> "Sold"; "AUFGELOEST" -> "Settled"; else -> "Open"
    }
    override fun pmSharesAt(n: String, einstand: String, kurs: String) = "$n shares, bought at $einstand, now $kurs"
    override val pmNoBets = "No bets yet. Pick a market and put practice money on an outcome."
    override val pmYourBets = "Your bets"
    override val pmRules = "Rules and description"
    override fun pmOpenAt(quelle: String) = "View on $quelle"
    override val pmNoHistory = "There is no history for this period."
    override fun pmRange(b: de.troetenrepublik.app.market.Prognose.Bereich) = when (b) {
        de.troetenrepublik.app.market.Prognose.Bereich.TAG -> "1D"; de.troetenrepublik.app.market.Prognose.Bereich.WOCHE -> "1W"
        de.troetenrepublik.app.market.Prognose.Bereich.MONAT -> "1M"; de.troetenrepublik.app.market.Prognose.Bereich.MAX -> "Max"
    }
    override fun pmResolvedCount(n: Int) = if (n == 1) "One other outcome has already been decided." else "$n other outcomes have already been decided."
    override fun pmNotifTitle(tag: String) = when (tag) { "BET_WIN" -> "Bet won"; "BET_LOSS" -> "Bet lost"; else -> "Bet settled" }
    override fun pmNotifText(titel: String, betrag: String) = "$titel · payout $betrag"

    override val pvTitle = "Portfolio history"
    override fun pvRange(jahre: Int) = when {
        jahre == 0 -> "Max"
        jahre < 365 -> "${Math.round(jahre / 30.4)}M"
        else -> "${Math.round(jahre / 365.25)}Y"
    }
    override val pvCompare = "Compare"
    override val pvCompareTitle = "Compare with"
    override val pvGroupIndex = "Indices and world ETFs"
    override val pvGroupMembers = "Members you follow"
    override val pvLoading = "Calculating history …"
    override val pvNone = "No history can be calculated for this period."
    override fun pvCurrencyNote(waehrung: String) = "In ${if (waehrung == "Euro") "euros" else "US dollars"}, converted at each day's exchange rate, before costs and taxes."
    override val pvMoreStats = "More figures"
    override val pvNoInvestorHistory = "The app shows no history here: in some quarters the 13F filings cover less than half of the reported value with prices."
    override val pvLessStats = "Fewer figures"
    override val ptClosedSales = "Closed sales"
    override fun ptClosedSalesLine(anzahl: Int, gewinne: Int, quote: String, faktor: String?, mittelGewinn: String?, mittelVerlust: String?, erwartung: String) =
        listOfNotNull(
            "Hit rate $quote ($gewinne of $anzahl with a gain)", faktor?.let { "profit factor $it" },
            mittelGewinn?.let { "avg. gain $it" }, mittelVerlust?.let { "avg. loss $it" }, "avg. result per sale $erwartung",
        ).joinToString(" · ") + ". The profit factor is total gains divided by total losses. Results are before tax."
    override fun pvNoteInvestor(quartale: Int, abdeckung: Double) =
        "From $quartale quarterly filings (Form 13F): holdings at quarter end, held until the next quarter. Buys and sales within a quarter are not visible. The markers show on the report date what was bought and sold compared with the previous quarter. Carried forward after the latest filing with the ten largest positions." +
            (if (abdeckung < 0.9) " On average the history covers only ${Math.round(abdeckung * 100)} percent of the reported value because options, bonds and securities without prices are missing, so it can differ widely from the actual result." else "")
    override fun pvNoteCongress(geschaefte: Int, ohneKurs: Int, ohneBestand: Int) =
        "From $geschaefte reported trades: amounts as the middle of the reported range, options with strike and expiry at their Black-Scholes value, otherwise as the underlying, prices without dividends. Holdings from before the period are not known." +
            (if (ohneKurs > 0) " $ohneKurs trades without prices are left out." else "") +
            (if (ohneBestand > 0) " $ohneBestand sales concern holdings from before the period and are left out." else "")
    override fun pvNotePresident(geschaefte: String, jahr: String, einzel: String, eingerechnet: String, werte: String, anleihen: String, anteil: String,
        ohneKuerzel: String?, ohneKurs: String?, ohneBestand: String?, stichtag: String) =
        "From $geschaefte reported trades ($jahr from the 2025 annual report, $einzel from periodic transaction reports). The chart includes $eingerechnet trades in $werte stocks and ETFs with prices. Amounts are the midpoint of the reported range, options count as the underlying, prices without dividends. $anleihen trades in bonds, preferred shares and deposits have no daily price and are left out. On the cut-off date they made up about $anteil of the portfolio." +
            (ohneKuerzel?.let { " $it trades could not be matched to a ticker." } ?: "") +
            (ohneKurs?.let { " $it concern securities without prices." } ?: "") +
            (ohneBestand?.let { " $it sales without an earlier purchase are left out." } ?: "") +
            " Calculated up to $stichtag, then carried forward with the holdings on that day."
    override val truthTitle = "Truth Social"
    override val truthSubtitle = "His posts from the archive “Trump's Truth”"
    override val truthSource = "Source: Trump's Truth (trumpstruth.org), an archive by Defending Democracy Together, not affiliated with Truth Social or Donald Trump. A tap opens the post in the archive."
    override val truthRemoved = "Deleted"
    override val truthRepost = "Reposted"
    override val truthQuote = "Quote"
    override fun truthImages(n: Int) = if (n == 1) "1 image" else "$n images"
    override val truthVideo = "Video"
    override val truthNone = "The archive is not reachable right now."
    override val truthMore = "Older posts"
    override val pvNoteStrategy = "Model portfolio of euro ETFs with the same weights every day."
    override val pvNoteCompare = "Strategies as model portfolios of euro ETFs, investors from their 13F filings, indices via ETFs on Xetra."
    override val pvCongressNoBuys = "No purchases with prices were reported in this period. Without purchases there is no portfolio to show."
    override fun pvMarkBuys(datum: String, werte: String) = "Buy $datum: $werte"
    override fun pvMarkSells(datum: String, werte: String) = "Sale $datum: $werte"
    override fun pvMarkQBuys(quartal: String, werte: String) = "$quartal, buys: $werte"
    override fun pvMarkQSells(quartal: String, werte: String) = "$quartal, sales: $werte"
    override val pvMoveOut = "sold out"
    override fun pvQuarter(q: Int, jahr: Int) = "Q$q $jahr"
    override val pvMoves = "Buys and sales by quarter"
    override fun pvQuarterHead(quartal: String, gemeldet: String) = "$quartal · filed $gemeldet"
    override fun pvAllQuarters(n: Int) = "All $n quarters"
    override val pvFewerQuarters = "Fewer quarters"
    override val pvCompareStrategies = "Compare strategies"
    override val pvCompareInvestors = "Compare investors"
    override val pvCompareScreen = "Compare portfolios"
    override fun pvSince(monat: String) = "since $monat"
    override val pmHistory = "History"
    override val pmHistoryLoading = "Loading the price history of your bets …"
    override val pmHistoryNone = "There is no history for this period yet."
    override val pmHistoryNote = "Profit and loss of all bets: practice money staked and paid out plus the value of the shares held at the price of the time."
    override val pmHistoryHint = "Drag across the chart: result of the day and bets nearby."
    override fun pmHistoryRange(tage: Int) = when (tage) { 7 -> "1W"; 30 -> "1M"; else -> "Max" }
    override fun pmHistoryMark(tag: String, datum: String, betrag: String, seite: String, titel: String) = when (tag) {
        "BET_BUY" -> "$datum: $betrag on “$seite” · $titel"
        "BET_SELL" -> "$datum: sold for $betrag · $titel"
        "BET_WIN" -> "$datum: won, $betrag · $titel"
        "BET_LOSS" -> "$datum: lost · $titel"
        else -> "$datum: resolved, $betrag · $titel"
    }
    override val languageSystem = "System"
    override val zgTitle = "Access keys"
    override val importFileSection = "Whole portfolio"
    override val pvDepotSub = "Time-weighted over the whole period"
    override val newsImportantTitle = "Important news"
    override val newsWichtigEnglisch = "Top stories in English only"
    override val newsWichtigEnglischHinweis = "Headlines in other languages still show under All and with their source"
    override val newsImportantExplain = "Headlines about big events such as rate decisions, wars, tariffs or strong market moves are marked orange, especially when several outlets report or a security from your portfolio or watchlist is mentioned. Opinions, previews and buying tips are never marked."
    override val ptIndexTitle = "Same money in an index"
    override fun zgTerms(dienst: String) = when (dienst) {
        "guardian" -> "Free developer key for non-commercial use: up to 500 requests a day and one per second. The app uses at most 450, checks the two sections every 15 minutes and keeps Guardian content for at most 24 hours."
        "finnhub" -> "Free key for personal use only: 60 requests per minute, the data must not be passed on. The app checks the four categories every 10 minutes."
        "fec" -> "Key from api.data.gov, free with an email address: 1,000 requests per hour. The app uses at most 900 per hour, with the demo key at most 25 per hour and 45 a day. Only PAC totals and recipients are requested, no names of individual donors."
        "uspto" -> "Key for the US Patent Office Open Data Portal, free with a USPTO account and an identity check via ID.me. Unused keys expire after 90 days. The app asks at most 500 times a day, one request at a time."
        "reddit" -> "Reddit API for non-commercial use: 100 requests per minute. Since late 2025 Reddit grants new access only after an application, and self-service sign-up is closed. With approved access you create an app under reddit.com/prefs/apps and enter its client ID, plus the secret for an app of type “script” or “web app”. The app signs in as an app only, without your Reddit account, and only reads. It uses at most 30 requests per minute and 2,000 a day and shows no posts marked 18+."
        "alphavantage" -> "Free key: 25 requests a day. The app asks at most once an hour and at most 22 times a day. Alpha Vantage labels its news as premium. If the service rejects a free key, the app shows its message."
        "eodhd" -> "EODHD key for personal use only: the data may neither be passed on nor shown to others, and the access may not be shared. 20 calls a day, reset at midnight GMT. One request costs 5 calls, so about 4 requests a day. The app asks only when you expand the section on a security's page and keeps the result for an hour."
        else -> "DeepL API Free: 500,000 characters a month. The app uses at most 450,000 in 31 days and remembers every translation, so the same text counts only once. Only market content is sent, no data about you."
    }
    override val zgKey = "Key"
    override val zgShow = "Show"
    override val zgHide = "Hide"
    override fun zgUsage(dienst: String, verbraucht: String, erlaubt: String) = when (dienst) {
        "deepl" -> "Characters in the last 31 days: $verbraucht of $erlaubt"
        "eodhd" -> "Calls today (UTC): $verbraucht of $erlaubt"
        else -> "Requests in the last 24 hours: $verbraucht of $erlaubt"
    }
    override val zgLimit = "The limit has been reached. The app asks again as soon as the quota allows."
    override val zgKeyRejected = "The provider rejected the key. Please check it."
    override fun zgProviderSays(meldung: String) = "Message from the provider: $meldung"
    override fun zgGetKey(name: String) = "Get a key from $name"
    override fun zgProfileSubtitle(n: Int) = if (n == 0) "The Guardian, Finnhub, Alpha Vantage, DeepL and 9 more" else "$n entered"
    override val zgRedditId = "Client ID"
    override val zgRedditSecret = "Secret, if any"
    override val zgRedditHolen = "Apply for access at Reddit and create an app"
    override val zgFinraSecret = "Client secret"
    override val zgFinraHolen = "Create a FINRA account and a credential"
    override fun newsKeyMissing(name: String) = "$name needs your own free key."
    override val newsKeyEnter = "Enter key"
    override val newsGuardianNote = "Guardian content stays in the app for at most 24 hours, saved articles too."
    override val newsGuardianNoText = "The app shows the text through the Guardian's interface when a key is entered. Otherwise the article is available in the browser."
    override val pmTranslateHint = "Titles and outcomes come from Polymarket and Kalshi in English only. With your own DeepL key the app translates them into German."
    override val languageSystemHint = "Follows the device language: German if the device is set to German, otherwise English."
    override val pvDragHint = "Drag across the chart: values of the day and buys (▲) and sales (▼) nearby."

    override val dvSubtitle = "History, comparison with the market and where gains and losses came from"
    override val dvNone = "No purchases yet. The history starts with the first purchase."
    override val dvMoves = "Biggest moves"
    override fun dvRaster(name: String) = when (name) { "WOCHE" -> "Week"; "MONAT" -> "Month"; else -> "Day" }
    override val dvRises = "Gains"
    override val dvDrops = "Drops"
    override val dvNoMoves = "None in the selected period."
    override fun dvWeek(datum: String) = "Week of $datum"
    override fun dvBy(werte: String) = "From $werte"
    override val dvContrib = "Gain and loss by holding"
    override val dvHeld = "held"
    override val dvEstimated = "sold, result on the sale date"
    override val dvTrades = "Buys and sells"
    override fun dvQtyAt(qty: String, price: String) = "$qty at $price"
    override fun dvAll(n: Int) = "Show all $n"
    override val dvFewer = "Show fewer"
    override fun dvTagKauf(werte: String) = "Buy: $werte"
    override fun dvTagVerkauf(werte: String) = "Sale: $werte"
    override fun dvUndWeitere(n: Int) = "and $n more"
    override val dvDragHint = "Drag across the chart: the day's change, the holdings behind it and the buys (▲) and sales (▼) on that day."
    override val dvNote = "Result of a holding on a day: its change in value without the amounts of buys and sells. Fees, taxes, interest and dividends do not count here."
    override val indButton = "Indicators"
    override fun indName(art: String) = when (art) {
        "sma50" -> "50-day avg"
        "sma200" -> "200-day avg"
        "bb" -> "Bollinger bands"
        "rsi" -> "RSI"
        "stoch" -> "Stochastic"
        "atr" -> "ATR"
        "obv" -> "OBV"
        "ichimoku" -> "Ichimoku"
        "sar" -> "Parabolic SAR"
        "kerzen" -> "Candlestick patterns"
        else -> "MACD"
    }
    override fun indLage(art: String, lage: String) = when ("$art/$lage") {
        "kerzen/hammer" -> "Hammer: after a decline, rebound possible"
        "kerzen/inv_hammer" -> "Inverted hammer: after a decline, rebound possible"
        "kerzen/haengender" -> "Hanging man: after a rise, pullback possible"
        "kerzen/sternschnuppe" -> "Shooting star: after a rise, pullback possible"
        "kerzen/verschlingen_auf" -> "Bullish engulfing: after a decline, rebound possible"
        "kerzen/verschlingen_ab" -> "Bearish engulfing: after a rise, pullback possible"
        "kerzen/durchstossen" -> "Piercing line: after a decline, rebound possible"
        "kerzen/wolke" -> "Dark cloud cover: after a rise, pullback possible"
        "kerzen/morgenstern" -> "Morning star: after a decline, rebound possible"
        "kerzen/abendstern" -> "Evening star: after a rise, pullback possible"
        "kerzen/soldaten" -> "Three white soldiers: after a decline, rebound possible"
        "kerzen/kraehen" -> "Three black crows: after a rise, pullback possible"
        "kerzen/doji" -> "Doji: indecision, no direction"
        "kerzen/keins" -> "No pattern in the last 5 trading days"
        "sma50/ueber" -> "price above the average: short-term up"
        "sma50/unter" -> "price below the average: short-term down"
        "sma200/ueber" -> "price above the average: long-term up"
        "sma200/unter" -> "price below the average: long-term down"
        "bb/oben" -> "above the upper band: unusually strong rise, pullback possible"
        "bb/nahe_oben" -> "near the upper band: recently risen, upwards"
        "bb/unten" -> "below the lower band: unusually strong fall, rebound possible"
        "bb/nahe_unten" -> "near the lower band: recently fallen, downwards"
        "bb/mitte" -> "in the middle of the usual range: no clear direction"
        "rsi/ueberkauft", "stoch/ueberkauft" -> "overbought: strong rise, pullback possible"
        "rsi/ueberverkauft", "stoch/ueberverkauft" -> "oversold: strong fall, rebound possible"
        "rsi/neutral", "stoch/neutral" -> "neither strongly up nor strongly down"
        "atr/unruhig" -> "more volatile than usual, says nothing about direction"
        "atr/ruhig" -> "calmer than usual, says nothing about direction"
        "atr/normal" -> "usual volatility, says nothing about direction"
        "obv/bestaetigt_auf" -> "uptrend, confirmed by volume"
        "obv/bestaetigt_ab" -> "downtrend, confirmed by volume"
        "obv/schwach_auf" -> "uptrend, but without volume support"
        "obv/schwach_ab" -> "downtrend, but without volume support"
        "obv/seitwaerts_auf" -> "no trend, more volume on up days"
        "obv/seitwaerts_ab" -> "no trend, more volume on down days"
        "ichimoku/ueber" -> "above the cloud: uptrend"
        "ichimoku/unter" -> "below the cloud: downtrend"
        "ichimoku/in" -> "inside the cloud: sideways, no clear trend"
        "sar/aufwaerts" -> "stop below the price: uptrend"
        "sar/abwaerts" -> "stop above the price: downtrend"
        "macd/steigt" -> "upward momentum rising"
        else -> "momentum fading, rather downwards"
    }
    override fun indTendenz(tendenz: String) = when (tendenz) {
        "POSITIV" -> "↑ positive"
        "NEGATIV" -> "↓ negative"
        "GEMISCHT" -> "↕ mixed"
        else -> "→ neutral"
    }
    override fun indUeberblick(positiv: Int, negativ: Int, neutral: Int, gemischt: Int) = "Overview: " + listOfNotNull(
        positiv.takeIf { it > 0 }?.let { "$it positive" },
        negativ.takeIf { it > 0 }?.let { "$it negative" },
        gemischt.takeIf { it > 0 }?.let { "$it mixed" },
        neutral.takeIf { it > 0 }?.let { "$it neutral" },
    ).joinToString(" · ")
    override fun indGrund(art: String, lage: String, prozent: String, zahl: String, kreuz: String?, kreuzAuf: Boolean, eng: Boolean): String = when (art) {
        "kerzen" -> if (kreuz == null) "" else "Found on $kreuz" + when (zahl) { "0" -> ", the last trading day"; "1" -> ", one trading day earlier"; else -> ", $zahl trading days earlier" }
        "sma50", "sma200" -> "Price $prozent " + (if (lage == "ueber") "above it" else "below it") +
            if (art == "sma200" && kreuz != null) ". The 50-day average crossed it " + (if (kreuzAuf) "upwards (golden cross)" else "downwards (death cross)") + " on $kreuz" else ""
        "bb" -> listOfNotNull(
            when (lage) {
                "oben" -> "Unusually strong rise compared with the last 20 days"
                "unten" -> "Unusually strong fall compared with the last 20 days"
                "nahe_oben" -> "Price in the top fifth between the bands, still inside"
                "nahe_unten" -> "Price in the bottom fifth between the bands, still inside"
                else -> null
            },
            if (eng) "Bands unusually narrow: a quiet phase, often followed by larger moves" else null,
        ).joinToString(". ")
        "rsi" -> "Value $zahl out of 100. Above 70 counts as overbought, below 30 as oversold"
        "stoch" -> "Value $zahl out of 100: this is how high the price sits between the low (0) and the high (100) of the last 14 days. Above 80 counts as overbought, below 20 as oversold" +
            if (kreuz != null) ". On $kreuz the value crossed its 3-day average " + (if (kreuzAuf) "upwards" else "downwards") else ""
        "atr" -> "A trading day currently moves the price by $prozent on average, compared with the usual level of the last year"
        "obv" -> when (lage) {
            "bestaetigt_auf" -> "Price up $prozent in 20 days, and more was traded on up days than on down days. The rise is broadly carried. On this reading it points to an uptrend"
            "bestaetigt_ab" -> "Price down $prozent in 20 days, and more was traded on down days than on up days. The fall is broadly carried. On this reading it points to a downtrend"
            "schwach_auf" -> "Price up $prozent in 20 days, but more was traded on down days. The rise came on quiet days. On this reading the uptrend is doubtful"
            "schwach_ab" -> "Price down $prozent in 20 days, but more was traded on up days. The fall came on quiet days. On this reading it may be running out"
            "seitwaerts_auf" -> "Price barely moved in 20 days ($prozent), but more was traded on up days. Some read this as a sign that a rise could follow"
            else -> "Price barely moved in 20 days ($prozent), but more was traded on down days. Some read this as a sign that a fall could follow"
        }
        "ichimoku" -> when (lage) {
            "ueber" -> "Price $prozent above the top of the cloud"
            "unter" -> "Price $prozent below the bottom of the cloud"
            else -> "Price between the edges of the cloud: no clear direction"
        } + if (kreuz != null) ". On $kreuz the 9-day midpoint crossed the 26-day midpoint " + (if (kreuzAuf) "upwards" else "downwards") else ""
        "sar" -> "Price $prozent " + (if (lage == "aufwaerts") "above the stop" else "below the stop") +
            if (kreuz != null) ". Direction changed on $kreuz" else ""
        else -> if (kreuz != null) "Crossed the signal line " + (if (kreuzAuf) "upwards" else "downwards") + " on $kreuz" else ""
    }
    override fun indErklaerung(art: String) = when (art) {
        "sma50" -> "Average closing price of the last 50 trading days. A price above it means the recent direction was rather upwards. Trends often last a while, but for single stocks the signal is weak."
        "sma200" -> "Average of the last 200 trading days, a gauge of the long-term trend. When the 50-day average crosses it upwards it is called a golden cross, downwards a death cross. Seen as a trend sign, but it often comes late."
        "bb" -> "Two lines above and below the average of the last 20 days. How far apart they are depends on how much the price moved in those 20 days: in turbulent times they widen, in calm times they narrow. About 9 out of 10 closing prices fall between them. A price outside has moved unusually far. It often returns towards the middle, but in strong trends it can run along the band for a while.\n\nHow to read it: in the upper part of the range the price has recently risen (positive), in the lower part it has fallen (negative), in the middle there is no clear direction. Outside the bands the picture is mixed: the move was strong, a counter-move is possible but not certain."
        "rsi" -> "Compares the gains with the losses of the last 14 days. High values mean the price has almost only risen recently, low values that it has almost only fallen. Strong stocks often stay “overbought” for a long time. Overbought and oversold count as mixed here: the price has moved far, a counter-move is possible but not certain."
        "stoch" -> "Shows where today's close lies between the lowest and the highest price of the last 14 days: 0 at the low, 100 at the high. When the value crosses its own 3-day average, that counts as a turning signal. Reacts fast and gives many false signals."
        "atr" -> "Average true range: how far the price moves on a trading day on average, over 14 days and including overnight gaps, shown here as a percentage of the price. It says nothing about direction, only how turbulent it is. Useful for placing a stop-loss: closer than about two ATR, an ordinary day can trigger it."
        "obv" -> "On-balance volume is a running total: if the price closes higher than the day before, the whole day's volume is added. If it closes lower, it is subtracted. The level of the line means nothing. What counts is its direction compared with the price. If both move the same way, the trend counts as confirmed. If they drift apart, it counts as shaky. If the price barely moves, OBV only shows on which days more was traded. A simplification: every purchase has a seller, so “more volume on up days” does not mean more was bought than sold. Imprecise for thinly traded securities, and indices often have no volume at all."
        "ichimoku" -> "Japanese trend system built from the midpoints of high and low over the last 9, 26 and 52 days. They form a “cloud” shifted 26 days forward. The chart shows its two edges and the 26-day midpoint. Above the cloud the trend counts as up, below as down, inside as unclear. It follows the trend and therefore reacts late. The cloud is drawn up to today, its preview of the next 26 days is missing."
        "kerzen" -> "Candlestick patterns read the open, high, low and close of single days. The app looks for the best-known textbook patterns: hammer, inverted hammer, bullish engulfing, piercing line, morning star and three white soldiers after a decline (read as a sign of a rebound); hanging man, shooting star, bearish engulfing, dark cloud cover, evening star and three black crows after a rise (read as a sign of a pullback). The price path of the five days before decides whether there was a decline or a rise. A doji, where open and close are almost equal, is read as indecision. The latest pattern of the last 5 trading days is shown. Without opening prices (some indices and funds) there is no detection."
        "sar" -> "Parabolic SAR is a trailing stop: below the price in an uptrend, above it in a downtrend. With every new high or low it moves closer faster. When the price breaks through, the direction flips and the stop jumps to the other side. Works in clear trends, flips constantly in sideways phases."
        else -> "Gap between the 12-day and the 26-day average, compared with its own 9-day average (signal line). Above it, momentum is rising. Reacts late and gives many false signals in sideways phases."
    }
    override val indFuss = "On the usual reading the arrows mean:\n• ↑ positive: points rather to rising prices\n• ↓ negative: points rather to falling prices\n• ↕ mixed: moved far, a counter-move is possible\n\nThe predictive value is low, indicators only read the past price."
    override val copyPolitics = "Politics"
    override fun truthRelevant(themen: List<String>) = themen.joinToString(" · ")
    override fun truthThema(k: String) = when (k) {
        "zoelle" -> "tariffs"
        "handel" -> "trade"
        "notenbank" -> "Fed"
        "boerse" -> "stock market"
        "konjunktur" -> "economy"
        "energie" -> "energy"
        "krypto" -> "crypto"
        "steuern" -> "taxes"
        "dollar" -> "dollar"
        "geopolitik" -> "geopolitics"
        else -> k.removePrefix("firma:")
    }
    override val newsOhneLesemodus = "There is no reader mode for this site. “Open in browser” leads to the full article."
    override val newsBrowserOnly = "On a computer MarketWatch shows its articles only in the browser. “Open in browser” leads to the full article."
    override val copyPositionsTitle = "Current positions"
    override fun copyPositionShare(anteil: String) = "$anteil of the portfolio"
    override val copyPositionsNote = "Estimated from the reported trades: purchases at the day's price, amounts as the middle of the range. Holdings from before the first filing are missing."
    override fun copyPositionsAll(n: Int) = "All $n positions"
    override val copyPositionsFewer = "Fewer positions"
    override val copyPartShares = "Shares"
    override fun copyPartOption(call: Boolean, basispreis: String, verfall: String, verfallen: Boolean) =
        (if (call) "Call" else "Put") + " \$$basispreis · " + (if (verfallen) "expired $verfall" else "until $verfall")
    override fun copyPartShare(anteil: String) = "$anteil of the position"
    override fun copyPartsToggle(offen: Boolean) = if (offen) "Hide breakdown" else "Show breakdown"
    override val copyToolsRow = "Portfolio check, diversification, crash test, dividends and other tools with this portfolio"
    override fun copyToolsFor(name: String) = "With the estimated portfolio of $name"
    override fun pvMetric(k: String) = when (k) {
        "gesamt" -> "Total"
        "jahr" -> "Per year"
        "schwankung" -> "Volatility"
        "rueckgang" -> "Max drawdown"
        "sharpe" -> "Sharpe ratio"
        "sortino" -> "Sortino ratio"
        "calmar" -> "Calmar ratio"
        "ulcer" -> "Ulcer index"
        "var" -> "VaR 95%"
        "es" -> "ES 95%"
        "dauer" -> "Days below peak"
        "beta" -> "Beta"
        "alpha" -> "Alpha per year"
        else -> "Correlation"
    }
    override fun pvMetricExplain(k: String) = when (k) {
        "gesamt" -> "Change over the selected period."
        "jahr" -> "Average return per year, only for periods of about a year or more."
        "schwankung" -> "How much the line moves up and down, scaled to a year (volatility). 20% means that in about two out of three years the result is at most 20 percentage points above or below the average. Broad equity ETFs are around 15%, single stocks often 30% or more."
        "rueckgang" -> "Largest loss from a peak to the following low. Shows how much you would have been down at the worst moment."
        "sharpe" -> "How much return the volatility earned: return per year above the interest on cash, divided by volatility. Example: 10% return with 2% interest and 16% volatility gives 0.5. Below 0 the return was below the interest on cash, around 0.5 is typical for stock markets, above 1 is very good. With equal returns, the line with the higher ratio ran more smoothly."
        "sortino" -> "Like the Sharpe ratio, but only downward moves count as risk, since jumps upwards bother nobody. If it is clearly above the Sharpe ratio, the volatility came mostly from rises."
        "calmar" -> "Return per year divided by the largest drawdown in the chosen period. Example: 10% a year with at most 25% drawdown gives 0.4. Higher is better. The usual version uses three years."
        "ulcer" -> "Measures how deep and how long the line stayed below its last peak on average, in percent. 0 means steady without setbacks, below 5 calm, above 15 long and deep losing phases."
        "var" -> "A bad but ordinary stretch: on 95 of 100 days (weeks for long periods) the loss was smaller than this. With daily values about one day a month turns out worse. Shown from 100 values."
        "es" -> "How bad the remaining 5 of 100 days or weeks were on average, the really bad ones. At least as large as the VaR."
        "dauer" -> "Longest time until an earlier peak was reached again. “+” means it is still ongoing. This is how long you would have waited for the old high."
        "beta" -> "How strongly the first line moves with this one. 1 means: if this line moves by 1%, the first moves by 1% on average too. 1.5 means more violently, 0.5 more calmly, below 0 rather in the opposite direction."
        "alpha" -> "Return per year of the first line that moving along (beta) does not explain. Positive means better than this line's moves would suggest, negative worse. Varies a lot with the period."
        else -> "How alike both lines move, from 1 to −1. At 1 they always move the same, at 0 independently, at −1 in opposite directions. For better diversification, anything below about 0.7 helps."
    }
    override fun copyLogoCredit(domain: String) = "Logo: $domain"
    override val copyNameUnreadable = "Name not readable"
    override fun webBausteinAngebot(mb: Int) = "For MarketWatch articles the app downloads an embedded browser once (Chromium, about $mb MB) from Maven Central into its data folder. After that the articles open in a window of the app."
    override val webBausteinLaden = "Download browser"
    override fun webBausteinLaedt(prozent: String) = "Downloading browser: $prozent"
    override val webBausteinFehler = "The browser could not be downloaded. Check the connection and try again. “Open in browser” always works."
    override val webBausteinImFenster = "The article is open in a window. Once the page has loaded, “Show text only” shows its text here."
    override val webBausteinLeer = "The page in the window shows no text the app can display."
    override val webBausteinFenster = "Open page in a window"
    override val newsTrumpHinweis = "Under “All” only his posts that matter for the economy, markets or world affairs appear, such as tariffs, the Fed, wars and attacks. All of them are here."
    override val webBausteinNurAnfang = "Without a subscription MarketWatch shows only the start of the article. The full text is on the site with a subscription."
    override fun dfUniversumName(k: String) = when (k) { "ALLE" -> "All stocks"; "USA" -> "USA"; "DEUTSCHLAND" -> "Germany"; "GROSSBRITANNIEN" -> "United Kingdom"; "SCHWEIZ" -> "Switzerland"; "JAPAN" -> "Japan"; "KANADA" -> "Canada"; "ASIEN" -> "Asia-Pacific"; "SCHWELLEN" -> "Emerging markets"; else -> "Europe" }
    override val opTitel = "Options"
    override val opPcr = "Put/call (volume · open)"
    override fun opBewegung(tag: String) = "Expected move to $tag"
    override fun opMaxPain(tag: String) = "Max pain $tag"
    override fun opHv(wert: String) = "stock $wert"
    override val opAuffaellig = "Unusual activity"
    override fun opAuffaelligZeile(art: String, strike: String, tag: String, umsatz: String, offen: String, praemie: String, seite: String) =
        "$art $strike · $tag · $umsatz contracts, $offen open · $praemie · " + when (seite) { "KAUF" -> "likely bought"; "VERKAUF" -> "likely sold"; else -> "side unclear" }
    override val opQuelle = "Option chain from Yahoo as of the last trading day, open interest from the day before."
    override val opStillhalter = "Option writing calculator"
    override fun opTage(tage: Int) = if (tage == 1) "1 day" else "$tage days"
    override val opStillCalls = "Covered call on shares you own"
    override val opStillPuts = "Cash-secured put"
    override val opBasis = "Strike"
    override val opPraemie = "Premium"
    override val opRenditeJahr = "Yield p.a."
    override val opAusuebung = "Exercise"
    override fun opStillBeispiel(call: List<String>?, put: List<String>?) = listOfNotNull(
        call?.let { "Call at ${it[0]}: the premium of ${it[1]} per share earns ${it[2]} until expiry. If the price rises above ${it[0]}, the shares go at that price, ${it[3]} in total with the price gain. A loss only starts below ${it[4]}." },
        put?.let { "Put at ${it[0]}: the premium of ${it[1]} per share earns ${it[2]} on the cash set aside until expiry. If the price falls below ${it[0]}, you buy the shares at that price, ${it[3]} after the premium." },
    ).joinToString("\n")
    override fun opErklaerung(k: String) = when (k) {
        "stillhalter" -> "Selling (writing) an option pays the premium at once and obliges you to deliver the shares at the strike (call) or to buy them (put). The figures use the bid, the price buyers offer right now, per share and before fees; one contract usually covers 100 shares. The yield is measured against the share price for calls and against the strike for puts, scaled to a year. The exercise chance comes from the contract's implied volatility and is a model estimate. If the stock rises sharply, the call gives up the gain above the strike. If it falls sharply, the put carries the loss, reduced only by the premium. In the practice depot warrants can only be bought, not written. The calculator shows what would be possible with real prices."
        "pcr" -> "Puts traded per call and open puts per open call. Above 1 many are hedging against or betting on falling prices. Below 1 calls dominate, below about 0.7 clearly."
        "bewegung" -> "Price of the at-the-money call and put together. According to the options market, the price moves this far until expiry on average, up or down. In roughly three out of five cases it stays inside."
        "maxpain" -> "Price at which the options of the next expiry expire with the least total value. Shortly before expiry the price sometimes drifts towards it. It is not a forecast."
        "iv" -> "Volatility the options market prices in for the next 30 days, annualised. Shown only when an expiry is 20 to 45 days away. Next to it the stock's actual volatility over the last year. If implied is clearly higher, the market expects turbulence, for example before earnings."
        else -> "Contracts with more volume than open interest and large premium, expiring within 60 days. This points to new, large positions. Whether they were bought or sold the app estimates from the last price."
    }
    override val kaTitel = "Congress buys"
    override val kaNav = "Strategy from all reported purchases, with backtest"
    override val kaFenster = "Portfolio from reported purchases"
    override fun kaFensterKurz(tage: Int) = when {
        tage >= de.troetenrepublik.app.engine.KongressAuswertung.MAX_TAGE -> "Max"
        tage == 365 -> "1 Y"; tage == 30 -> "30 D"
        else -> "${tage / 30} M"
    }
    override fun kaMonate(n: Int) = if (n == 1) "1 month" else "$n months"
    override val kaFensterEigen = "Custom"
    override val kaFensterMonate = "Purchases of the last"
    override val kaLaedt = "Loading trades and prices"
    override val kaVergleich = "S&P 500 per year"
    override val kaHinweisVerlauf = "Rebuilt every week, in dollars."
    override val kaJetzt = "Holdings today"
    override val rgTitel = "Leaderboard"
    override val rgNav = "Whose purchases beat the market"
    override fun rgOrdnung(k: String) = when (k) { "QUOTE" -> "Hit rate"; "KAEUFE" -> "Purchases"; "VERMOEGEN" -> "Net worth"; else -> "Lead" }
    override fun rgVermoegen(s: String) = "net worth $s"
    override fun vmSpanne(von: String, bis: String) = "$von to $bis"
    override fun vmTitel(jahr: Int) = "Net worth per annual report $jahr"
    override val vmHinweis = "Sum of the value ranges in the annual report to the Clerk of the House. Homes used by the member and government pensions are missing, so wealth can be higher."
    override val bhTitel = "Politics and agencies"
    override val bhLobbying = "Lobbying"
    override fun bhJeQuartal(betrag: String) = "$betrag a quarter"
    override fun bhQuartal(q: Int, j: Int, betrag: String, firmen: Int) = "Q$q $j: $betrag" + if (firmen > 0) ", $firmen hired lobbying firms" else ""
    override fun bhThemen(liste: String) = "Issues: $liste"
    override fun bhLobbyingQuelle(stand: String) = "LD-2 reports with the Clerk of the House, as of $stand. If the company reports itself, its amount includes payments to lobbying firms."
    override fun bhGesetzeWert(gesetze: Int, personen: Int) = listOfNotNull(if (gesetze > 0) "$gesetze bills" else null, if (personen > 0) "$personen traded" else null).joinToString(" · ")
    override fun bhGesetz(g: String, n: Int) = "$g, named in $n reports"
    override val bhAuftraege = "Government contracts"
    override fun bhFiskaljahr(betrag: String, jahr: Int) = "$betrag in fiscal year $jahr"
    override fun bhFiskaljahrZeile(jahr: Int, betrag: String, laufend: Boolean) = "Fiscal year $jahr: $betrag" + if (laufend) " (still running)" else ""
    override val bhAuftraegeQuelle = "Obligations from federal contracts per USAspending.gov, fiscal year starting 1 October. Below, the largest contracts of the last twelve months with their total value over the whole term."
    override val bhSpenden = "Campaign contributions"
    override fun bhSpendenWert(betrag: String, zyklus: Int) = "$betrag (${zyklus - 1}/${zyklus % 100})"
    override val bhKeinPac = "No PAC found"
    override fun bhPacSummen(zyklus: Int, einnahmen: String, kandidaten: String) = "Election cycle ${zyklus - 1}/${zyklus % 100}: receipts $einnahmen, to candidates $kandidaten. Largest recipients:"
    override fun bhSpendenQuelle(demo: Boolean) = "Contributions of the company PAC per the FEC. The PAC raises money from employees, not from company funds." + if (demo) " Without your own key (Access) only a few requests per hour." else ""
    override val bhLeer = "Short selling"
    override val bhFinraOhne = "Short selling and dark pool data from FINRA need your own free FINRA access. Enter it under Profile → Access keys. Without access the figures are on FINRA's pages."
    override val bhFinraLeerLink = "Short interest at FINRA"
    override val bhFinraAtsLink = "Dark pool trading at FINRA (OTC Transparency)"
    override val dpTitel = "Dark pools (ATS)"
    override fun dpWoche(woche: String, aktien: String, anteil: String?, schnitt: String?, wochen: Int) =
        "Week of $woche: $aktien shares via dark pools" + (anteil?.let { ", $it of volume" } ?: "") +
            (schnitt?.let { ". Average of the last $wochen weeks: $it" } ?: "")
    override fun dpPlaetze(plaetze: List<Pair<String, String>>) = "Largest venues: " + plaetze.joinToString(", ") { "${it.first} ${it.second}" }
    override fun dpVerzug(stufe: String?) = when (stufe) {
        "T1" -> "FINRA publishes these figures two weeks after the trading week."
        "T2" -> "FINRA publishes these figures four weeks after the trading week."
        else -> "FINRA publishes these figures two to four weeks after the trading week."
    }
    override val dpErklaerung = "Alternative trading systems (ATS) are venues outside the exchanges where orders are not visible beforehand (dark pool). Counted are the shares all ATS report to FINRA for the week, as a share of the week's volume on all venues. A rise shows more block trading and says nothing about buying or selling. Other off-exchange trading is missing."
    override fun bhLeerTage(anteil: String, tage: Int) = "Sold short in the last $tage trading days: $anteil of off-exchange reported volume"
    override fun bhLeerBestand(stichtag: String, aktien: String, aenderung: String?, tage: String?) = "Short interest on $stichtag: $aktien shares" + (aenderung?.let { " ($it vs previous date)" } ?: "") + (tage?.let { ", $it days to cover" } ?: "")
    override fun bhLeerVerlauf(seit: String, tief: String, hoch: String) = "Open short positions since $seit, between $tief and $hoch m shares"
    override val bhLeerErklaerung = "Above, the share of shares sold short in the volume of the last trading days as reported to FINRA by off-exchange venues. Market makers often sell short there, so a high value is normal. More telling is the short interest FINRA publishes twice a month. The line shows it over up to three years. If it rises with the price, many short sellers have to cover on further gains."
    override val bhEntlassungen = "Layoffs"
    override fun bhEntlassungenWert(meldungen: Int, betroffene: String) = "$betroffene in 12 months"
    override fun bhBetroffene(n: Int) = if (n > 0) "$n affected" else ""
    override val bhEntlassungenQuelle = "WARN Act notices to state labour agencies, collected by WARN Feed (github.com/APVentureEngine/warn-act-notices, CC BY 4.0), plus Texas via data.texas.gov. Not every state is included, and smaller layoffs need not be reported."
    override val bhPatente = "Patents"
    override fun bhPatenteWert(n: Int) = "$n in 12 months"
    override val bhPatenteQuelle = "Granted US patents with the company as first applicant per the USPTO Open Data Portal, using your key."
    override fun bhThema(code: String) = when (code) { "DEF" -> "Defense"; "BUD" -> "Budget"; "TAX" -> "Taxes"; "HCR" -> "Health"; "TRD" -> "Trade"; "CPI" -> "Computers and IT"; "SCI" -> "Science"; "ENG" -> "Energy"; "ENV" -> "Environment"; "TRA" -> "Transportation"; "FIN" -> "Finance"; "BAN" -> "Banking"; "LBR" -> "Labor"; "AGR" -> "Agriculture"; "TEC" -> "Telecommunications"; "CSP" -> "Consumer protection"; "IMM" -> "Immigration"; "HOM" -> "Homeland security"; "AVI" -> "Aviation"; "PHA" -> "Pharmacy"; "MMM" -> "Medicare and Medicaid"; "FOR" -> "Foreign relations"; "GOV" -> "Government"; "INS" -> "Insurance"; "BEV" -> "Beverages"; "FOO" -> "Food"; "UTI" -> "Utilities"; "AUT" -> "Automotive"; "CPT" -> "Copyright and patents"; "RES" -> "Real estate"; "TOB" -> "Tobacco"; "ALC" -> "Alcohol"; "CHM" -> "Chemicals"; "MAN" -> "Manufacturing"; "TOU" -> "Tourism"; "RRR" -> "Railroads"; "MAR" -> "Maritime"; "EDU" -> "Education"; "FUE" -> "Fuel"; "NAT" -> "Natural resources"; "SMB" -> "Small business"; "RET" -> "Retirement"; "ECN" -> "Economy"; "LAW" -> "Law enforcement"; "AER" -> "Aerospace"; "MIA" -> "Media"; "CIV" -> "Civil rights"; "VET" -> "Veterans"; "TAR" -> "Tariffs"; "MED" -> "Medical research"; "ACC" -> "Accounting"; "CDT" -> "Commodities"; "TRU" -> "Trucking"; "WAS" -> "Waste"; "GAM" -> "Gaming"; "FIR" -> "Firearms"; "ROD" -> "Roads"; "URB" -> "Urban development"; "TOR" -> "Torts"; "DOC" -> "Washington, D.C."; "IND" -> "Indigenous affairs"; "POS" -> "Postal"; "SPO" -> "Sports"; "UNM" -> "Unemployment"; "WEL" -> "Welfare"; "BNK" -> "Bankruptcy"; "CAW" -> "Clean air"; "ANI" -> "Animals"; "DIS" -> "Disasters"; "FAM" -> "Family"; else -> code }
    override val pmGross = "Big bets"
    override val pmEchtTitel = "Your Polymarket positions"
    override val pmEchtKeine = "There are no positions for this address right now."
    override val pmEchtHinweis = "Read through your public Polymarket address, in dollars. The app can only read: no buying, selling or withdrawing."
    override val pmUebungTitel = "Practice bets"
    override fun pmEchtZeile(ausgang: String, anteile: String, einstand: String, kurs: String) = "“$ausgang” · $anteile shares at $einstand, now $kurs"
    override val pmEchtEinloesbar = "resolved, redeemable"
    override val zgPolyAdresse = "Public address (0x…)"
    override val zgPolyGeheim = "That looked like a private key or recovery words and was deleted. Enter only the public address."
    override val zgPolyUngueltig = "An address starts with 0x followed by 40 characters from 0–9 and a–f."
    override val pmGrossKeine = "No big bets right now"
    override fun pmGrossZeile(ausgang: String, kauf: Boolean, preis: String, wann: String) = (if (kauf) "bought “$ausgang”" else "sold “$ausgang”") + " at $preis · $wann"
    override val bhSpendenLimit = "The shared FEC demo key is exhausted right now. With your own free key under Access it works right away."

    // 5.91 Kurschart und Marktkurven
    override val kcMarken = "My marks"
    override val kcSpanne = "Price range"
    override fun kcLinie(art: String) = when (art) { "EINSTAND" -> "Cost"; "LIMIT" -> "Limit"; "STOPP" -> "Stop"; "ALARM" -> "Alert"; else -> "KO" }
    override fun kcMarke(art: String, stueck: String, betrag: String, kurs: String) = when (art) {
        "KAUF" -> "Bought $stueck at $kurs"
        "VERKAUF" -> "Sold $stueck at $kurs"
        else -> "Dividend $betrag"
    }
    override fun kcSpanneQuelle(implizit: Boolean, sigma: String) =
        if (implizit) "From the implied volatility of the options, $sigma a year" else "From the swings of the last 60 trading days, $sigma a year"
    override val mkTitel = "Market curves"
    override val mkUntertitel = "Yield curve US and euro area, VIX curve"
    override val mkZins = "Yield curve"
    override val mkUsa = "US, Treasuries"
    override val mkEuro = "Euro area, AAA government bonds"
    override val mkDeutschland = "Germany, Federal bonds"
    override val mkQuelleDeutschland = "Source: Deutsche Bundesbank, term structure of listed Federal securities (Svensson method), yield in percent"
    override fun mkStichtag(k: Int, datum: String) = when (k) { 0 -> "Latest · $datum"; 1 -> "1 month ago · $datum"; else -> "1 year ago · $datum" }
    override fun mkLaufzeit(tage: Int): String {
        if (tage >= 365 && tage % 365 == 0) return "${tage / 365}Y"
        if (tage >= 28) {
            val m = tage / 30.4375
            val r = Math.round(m)
            return if (kotlin.math.abs(m - r) < 0.1) "${r}M" else String.format(java.util.Locale.US, "%.1fM", m)
        }
        return "${tage}D"
    }
    override fun mkForm(form: String) = when (form) {
        "INVERS" -> "Inverted: short maturities pay more than 10 years"
        "FLACH" -> "Flat: short and long maturities pay about the same"
        else -> "Normal: long maturities pay more"
    }
    override fun mkInvertiert(kurz: String, zins: String, zehn: String) = "$kurz at $zins is above 10 years at $zehn"
    override fun mkAbstand(kurz: String, wert: String) = "10 years minus $kurz: $wert percentage points"
    override val mkZinsMehr = "The yield curve shows the interest government bonds pay for each maturity.\n• Normal: it rises, because longer commitment pays more\n• Flat: short and long maturities pay about the same\n• Inverted: short maturities pay more than ten years\n\nIn the US an inverted curve came before many recessions, months to years ahead. Not every one was followed by a recession. Red marks show short maturities above the ten-year yield."
    override val mkQuelleUsa = "Source: US Treasury, Daily Treasury Par Yield Curve Rates, yield in percent"
    override val mkQuelleEuro = "Source: European Central Bank, euro area AAA government bond yield curve (spot rates), yield in percent"
    override val mkVix = "VIX curve"
    override fun mkVixForm(form: String) = when (form) {
        "BACKWARDATION" -> "Falling (backwardation): more fear short term than long term"
        "FLACH" -> "Flat: similar expectations short and long term"
        else -> "Rising (contango): the normal case"
    }
    override val mkVixMehr = "The VIX indices show how much investors expect the S&P 500 to swing over the next days up to one year, derived from option prices.\n• Rising curve (contango): the normal case, in calm times smaller swings are expected soon\n• Falling curve (backwardation): bigger swings are expected in the next days, which signals acute fear and showed up mostly in sell-offs"
    override val mkVixQuelle = "Source: Cboe volatility indices via Yahoo Finance (VIX1D, VIX9D, VIX, VIX3M, VIX6M, VIX1Y)"
    override val mkKeineDaten = "No data reachable right now. Try again later."

    // 5.91 Strategy lab, backtest lottery, investment thesis, own rules, monthly deposit
    override val slTitel = "Strategy test"
    override val slUntertitel = "Test signal rules in hindsight against buy and hold"
    override val slErklaerung = "The test shows how a rule would have done in the past, which says little about the future. After costs, most signal rules do worse than buy and hold. It uses daily prices in euros, the signal comes from the close, the trade happens at the next day's open."
    override val slZuWenig = "There are too few daily prices for a test of this security."
    override fun slKosten(spread: String) = "Starting with €10,000. Costs per buy and per sell: $spread spread (from the settings) and a €1 order fee. Taxes and dividends are not included."
    override val slRegeln = "Rules"
    override fun slRegel(art: String, a: Int, b: Int, n: Int) = when (art) {
        "HALTEN" -> "Buy and hold"
        "SMA" -> "Price above $a-day line"
        "KREUZ" -> if (a == 50 && b == 200) "Cross 50/200 (golden/death cross)" else "Cross $a/$b"
        "RSI" -> if (n == 14) "RSI: buy below $a, sell above $b" else "RSI $n days: buy below $a, sell above $b"
        "MACD" -> "MACD crosses its signal line"
        "DONCHIAN" -> "Breakout: buy $a-day high, sell $b-day low"
        "BOLLINGER" -> "Bollinger: buy below lower band, sell above middle"
        else -> art
    }
    override val slDieRegel = "Rule"
    override val slHalten = "Buy and hold"
    override fun slVergleich(regel: String, halten: String) = "$regel · $halten"
    override val slSpalten = "Values: rule · buy and hold"
    override val slEndwert = "End value"
    override val slTreffer = "Hit rate"
    override val slImMarkt = "Time in market"
    override fun slLern(datum: String) = "Learning period until $datum, per year"
    override fun slPruef(datum: String) = "Test period from $datum, per year"
    override val slLernText = "The learning period is the first 70% of the days, the test period the last 30%, each part with a fresh €10,000. If a rule only shines in the learning period, that is probably luck. Hit rate: share of trades with a profit after costs."
    override val slLotterie = "Luck test"
    override val slLotterieText = "Test enough variants of a rule and one will almost always shine in hindsight, often by luck. Steps:\n1. All variants run in the learning period and the one with the best Sharpe ratio (return relative to volatility) is picked\n2. It then runs in the test period, which it has not seen\n3. A percentage says how likely its result is no lucky pick\n\nBelow you choose which variants are tested."
    override fun slFamilie(key: String) = when (key) {
        "DURCHSCHNITT" -> "Averages 5 to 250"
        "KREUZ" -> "Crosses"
        "RSI" -> "RSI thresholds"
        else -> "All"
    }
    override val slLotterieLaeuft = "Testing variants …"
    override val slVarianten = "Variants tested"
    override val slBeste = "Best in learning period"
    override val slBesteLern = "Learning period, best · hold"
    override val slBestePruef = "Test period, best · hold"
    override val slDsr = "Probability: not a lucky pick"
    override fun slDsrUrteil(echt: Boolean) = if (echt) "The best variant stands out clearly from luck." else "With this many attempts, such a result would be expected from pure luck as well."
    override val slDsrText = "The percentage is the estimated probability that the Sharpe ratio of the best variant is more than luck in picking among many attempts (deflated Sharpe ratio, Bailey and López de Prado, 2014). From 95% the variant stands out clearly from chance, below that it can be a lucky hit. It is measured against zero, so after a long rise the number can be high even without an edge over holding. If the variant does not beat buy and hold in the test period, its lead in the learning period helps little. Because the variants are very similar, the number is only a rough guide."
    override val thTitel = "Investment thesis"
    override val thHinzufuegen = "Add an investment thesis (optional)"
    override val thGrund = "Reason to buy"
    override val thAusstieg = "Exit when …"
    override val thObergrenze = "Position limit in the portfolio"
    override val thPruefen = "Review date"
    override val thKeinTermin = "No date"
    override fun thMonate(m: Int) = if (m == 1) "1 month" else "$m months"
    override fun thPruefAm(datum: String) = "Review date: $datum"
    override val thRegelWarnung = "This breaks one of your own rules. The order is still possible."
    override fun thVerstoss(art: String, ist: String, grenze: String, name: String) = when (art) {
        "POSITION" -> (if (name.isNotBlank()) "$name: " else "") + "position $ist of the portfolio, your limit $grenze"
        "THESE" -> "Position $ist of the portfolio, thesis limit $grenze"
        "HEBEL" -> if (name.isBlank()) "Leveraged product, your rule: no leveraged products" else "Leveraged products in the portfolio: $name"
        "BARGELD" -> "Cash $ist of the portfolio, your minimum $grenze"
        else -> art
    }
    override fun thSeit(datum: String) = "Written down on $datum"
    override val thAusstiegLabel = "Exit"
    override fun thObergrenzeWert(grenze: String, ist: String) = "Limit $grenze, now $ist of the portfolio"
    override val thFrage = "Is the reason still valid?"
    override val thVerkaufenErwaegen = "Consider selling"
    override val thEntfernen = "Remove thesis"
    override fun thMitteilung(name: String) = "Review date for $name reached. Tap to check your reason to buy."
    override val thBestaetigtToast = "Next review in three months"
    override val thRegelnTitel = "Your own rules"
    override val thRegelnText = "Your own limits for the portfolio. If a purchase breaks one of them, the order screen shows a warning, but nothing is blocked."
    override val thRegelPosition = "At most in one position"
    override val thRegelHebel = "No leveraged products"
    override val thRegelBargeld = "At least as cash"
    override val thAus = "Off"
    override val thAn = "On"
    override val thRegelnOk = "The portfolio currently keeps all your rules."
    override val thAuswertung = "Theses and rules"
    override val thMitThese = "with a thesis"
    override val thBestaetigt = "Reviews confirmed"
    override val thErwogen = "Selling considered"
    override val thRegelbrueche = "Purchases against your rules"
    override val thOffeneTermine = "Reviews due"
    override val thAuswertungText = "Counted are purchases and buy orders in the app since investment theses were added. A thesis records why you buy and when you want to get out. On the review date the app asks whether the reason still holds. Writing down your reasons makes it easier to tell later whether a gain came from the thesis or from luck."
    override val ezTitel = "Deposit monthly"
    override val ezUntertitel = "Virtual money automatically every month"
    override fun ezAktiv(betrag: String, tag: String) = "$betrag $tag"
    override fun ezTagWert(tag: Int) = if (tag >= 31) "at month end" else "on day $tag"
    override val ezText = "Books a deposit into the practice portfolio every month on the chosen day, before that day's savings plans. Missed months are caught up once. Only in the practice portfolio, not with a connected real account."
    override val ezTag = "Day of month"
    override fun ezNaechste(datum: String) = "Next on $datum"
    override val ezBisher = "So far"
    override fun ezBisherWert(n: Int, summe: String) = "$n× · $summe"
    override val ezLoeschen = "Delete monthly deposit"
    override val ezGespeichert = "Monthly deposit saved"
    override val ezMitteilung = "Monthly deposit"
    override fun ezMitteilungText(betrag: String) = "$betrag is now in your cash account."

    // 5.91 Risikobeitrag, Bilanzqualitaet, Kaufkraft, Kennzahlenvergleich, Reverse DCF
    override val rskTitel = "Risk contribution"
    override fun rskDepotSchwankung(pct: String) = "Portfolio volatility: $pct per year"
    override val rskGewicht = "Weight"
    override val rskRisiko = "Share of volatility"
    override fun rskZeile(gewicht: String, risiko: String) = "$gewicht of the portfolio · $risiko of the volatility"
    override fun rskSatz(name: String, gewicht: String, risiko: String) = "$name is $gewicht of the portfolio but accounts for $risiko of the volatility."
    override val rskAusgeglichen = "No holding adds clearly more to the volatility than its weight suggests."
    override val rskOhneDaten = "Not enough common trading days for an estimate."
    override fun rskNichtEingerechnet(n: Int) = if (n == 1) "One holding without enough prices is not included." else "$n holdings without enough prices are not included."
    override val rskHinweis = "The share of volatility shows how much of the portfolio's ups and downs comes from one holding. It is based on weekly returns over the last twelve months. The calculation is weight times contribution to volatility, divided by the volatility of the whole portfolio. Holdings that swing a lot or move together with many others add more than their weight shows. Together the shares add up to 100%."
    override val rskParitaetTitel = "Equal risk share"
    override val rskParitaetText = "These are the weights at which every holding would add the same amount to the volatility (risk parity)."
    override val rskUebernehmen = "Use in rebalancing"
    override val rskWarnung = "The calculation estimates volatility and co-movement from just one year. Even small estimation errors shift the weights a lot, and volatility and co-movement can turn out differently in future."
    override val rskLaden = "Loading last year's prices"
    override val qsTitel = "Balance sheet quality"
    override val qsPiotroski = "Piotroski F-Score"
    override fun qsScore(score: Int, von: Int) = "$score of $von points"
    override fun qsStufe(stufe: String) = when (stufe) {
        "STARK" -> "strong"
        "MITTEL" -> "medium"
        else -> "weak"
    }
    override fun qsJahre(jahr: String, vorjahr: String) = "Fiscal year to $jahr against the year to $vorjahr"
    override fun qsPunkt(punkt: String) = when (punkt) {
        "RENDITE" -> "Return on assets"
        "CASHFLOW" -> "Operating cash flow positive"
        "RENDITE_STEIGT" -> "Return on assets up"
        "CASHFLOW_UEBER_GEWINN" -> "Cash flow above net income"
        "SCHULDEN_SINKEN" -> "Long-term debt down"
        "LIQUIDITAET_STEIGT" -> "Liquidity up"
        "KEINE_NEUEN_AKTIEN" -> "No new shares issued"
        "MARGE_STEIGT" -> "Gross margin up"
        else -> "Sales per assets up"
    }
    override fun qsBegruendung(punkt: String, jetzt: String, vorher: String?) = when (punkt) {
        "RENDITE" -> "Net income per total assets: $jetzt"
        "CASHFLOW" -> "Cash flow from operations: $jetzt"
        "CASHFLOW_UEBER_GEWINN" -> "Per total assets: cash flow $jetzt, net income ${vorher.orEmpty()}"
        "SCHULDEN_SINKEN" -> "Long-term debt per total assets: $jetzt, prior year ${vorher.orEmpty()}"
        "LIQUIDITAET_STEIGT" -> "Current assets per current liabilities: $jetzt, prior year ${vorher.orEmpty()}"
        "KEINE_NEUEN_AKTIEN" -> "Shares: $jetzt, prior year ${vorher.orEmpty()}"
        else -> "$jetzt, prior year ${vorher.orEmpty()}"
    }
    override val qsNichtPruefbar = "Cannot be checked, data missing"
    override val qsAltman = "Altman Z-Score"
    override fun qsZone(zone: String) = when (zone) {
        "SICHER" -> "safe zone"
        "GRAU" -> "grey zone"
        else -> "distress zone"
    }
    override val qsZonen = "Above 2.99 safe, 1.81 to 2.99 grey zone, below 1.81 distress."
    override val qsZonenAndere = "Version for companies outside manufacturing: above 2.6 safe, 1.1 to 2.6 grey zone, below 1.1 distress."
    override fun qsAltmanJahrAndere(jahr: String) = "Balance sheet to $jahr"
    override fun qsBaustein(k: String) = when (k) {
        "WC" -> "Working capital per total assets"
        "RE" -> "Retained earnings per total assets"
        "EBIT" -> "Operating profit per total assets"
        "MV" -> "Market value per total liabilities"
        "BW" -> "Book value of equity per total liabilities"
        else -> "Sales per total assets"
    }
    override fun qsAltmanJahr(jahr: String) = "Balance sheet to $jahr, today's market value"
    override val qsHinweis = "Both scores read the latest annual reports.\n• Piotroski F-Score: nine points for profit, cash flow, falling debt, better liquidity, no new shares and rising efficiency compared with the prior year. 7 to 9 points count as strong, 0 to 3 as weak\n• Altman Z-Score: combines balance sheet ratios and estimates how close a company is to bankruptcy. Manufacturers get the 1968 version, other companies the later version without revenue and with book value instead of market value\n\nLimits: both only look back, and one-off items distort them. Neither fits banks, insurers or other financials, and Altman does not fit real estate companies. Source: Yahoo Finance."
    override val qsLaden = "Loading annual reports"
    override val qsKeineDaten = "Yahoo has no annual reports for this security."
    override val kkReal = "Real (purchasing power)"
    override fun kkSatz(real: String, nominal: String, inflation: String) = "Real $real, nominal $nominal, inflation $inflation"
    override fun kkHinweis(usd: Boolean) = "Real means adjusted for inflation. The line shows the portfolio's purchasing power: portfolio value divided by the rise in prices since the start of the period. " +
        (if (usd) "Price index: US consumer prices (CPI-U) from the Federal Reserve Bank of St. Louis (FRED), otherwise from the Bureau of Labor Statistics. "
        else "Price index: Harmonised Index of Consumer Prices of the euro area (HICP) from the ECB Data Portal. ") +
        "Monthly values are spread across the days, the last month is carried forward."
    override val kkLaden = "Loading price index"
    override val kkKeineDaten = "The price index cannot be loaded right now."
    override val kvTitel = "Fundamentals compared"
    override val kvEinstieg = "Compare with similar stocks"
    override val kvEinstiegSub = "Valuation, margins, growth and debt side by side"
    override val kvAehnlich = "Similar according to Yahoo"
    override val kvBranche = "Same industry"
    override val kvSuche = "Search for another stock"
    override val kvSucheHinweis = "Name or ticker"
    override val kvMax = "Up to 5 stocks."
    override val kvMin = "Pick at least one more stock."
    override fun kvZeile(k: String) = when (k) {
        "KGV" -> "P/E"
        "KGV_ERWARTET" -> "Forward P/E"
        "KUV" -> "P/S"
        "KBV" -> "P/B"
        "EV_EBITDA" -> "EV/EBITDA"
        "BRUTTOMARGE" -> "Gross margin"
        "OPERATIVE_MARGE" -> "Operating margin"
        "UMSATZWACHSTUM" -> "Revenue growth"
        "VERSCHULDUNG" -> "Debt to equity"
        "DIVIDENDE" -> "Dividend yield"
        "KURS_CASHFLOW" -> "Price/cash flow"
        "ROIC" -> "ROIC"
        "OWNER_EARNINGS" -> "Owner earnings"
        "ZINSDECKUNG" -> "Interest coverage"
        else -> "Price target"
    }
    override val kvBester = "Highlighted: the cheapest or strongest value in each row."
    override val kvHinweis = "Data from Yahoo Finance, mostly the last twelve months. Ratios from different industries are only partly comparable."
    override val kvLaden = "Loading fundamentals"
    override val rdTitel = "What the price implies"
    override fun rdSatz(x: String) = "For today's price to pay off, earnings would have to grow by about $x a year for 10 years."
    override fun rdSatzRand(x: String, mehr: Boolean) = if (mehr) "For today's price to pay off, earnings would have to grow by more than $x a year for 10 years." else "For today's price to pay off, earnings growing by less than $x a year for 10 years is enough."
    override val rdRendite = "Your required return"
    override val rdZielKgv = "P/E after 10 years"
    override fun rdBranche(kgv: String) = "Industry $kgv"
    override fun rdKgvJetzt(kgv: String) = "P/E today: $kgv"
    override fun rdBisher(x: String, jahre: Int) = "So far: $x a year over the last $jahre years"
    override fun rdAnalysten(x: String) = "Analysts expect for the next twelve months: $x"
    override val rdHinweis = "The calculation starts from today's price. Suppose earnings grow evenly for ten years and the stock then trades at the chosen P/E. Shown is the growth needed for the chosen return each year. If it is well above past growth, a lot of optimism is priced in. Dividends, debt and one-off items are left out, and small changes to the assumptions move the result a lot. Nothing can be calculated for loss-making companies."

    // 5.91 Aufraeumen
    override val auStornierenTitel = "Cancel order"
    override fun auStornierenText(name: String) = "The open order for $name will no longer be executed."
    override val auBehalten = "Keep"
    override val auIndikatorenAbMonat = "In the chart from 1 month"

    // 5.91 Insider weltweit und US-Regierung
    override val ogeTitel = "Cabinet, Fed and senior officials"
    override val ogeLeer = "No filings loaded yet. The list comes from the OGE and the White House as soon as the device can fetch it."
    override fun ogeMeldungen(n: Int) = if (n == 1) "1 filing" else "$n filings"
    override val ogeHinweis = "Members of the US government report purchases and sales of securities on form 278-T, with amounts as ranges in US dollars, such as $1,001 to $15,000."
    override val ogeQuelle = "Source: U.S. Office of Government Ethics (extapps2.oge.gov) and the White House (whitehouse.gov/disclosures), OGE Form 278-T."
    override fun ogeUngelesen(n: Int) = if (n == 1) "1 filing has not been read yet. The app reads it the next time you open this page." else "$n filings have not been read yet. The app reads them the next time you open this page."
    override fun ogePdf(datum: String) = "Latest filing from $datum"
    override fun ogeGehandelt(n: Int) = if (n == 1) "A government official traded this security (filing 278-T)." else "$n government officials traded this security (filings 278-T)."
    override val f144Titel = "Announced sales (Form 144)"
    override val f144Hinweis = "Executives and large holders announce bigger sales to the SEC in advance, with number of shares, estimated value and planned date. Form 4 shows whether and when the sale really happened."
    override fun f144Stueck(n: String) = "$n shares"
    override fun f144Geplant(tag: String) = "planned $tag"
    override val f13Titel = "Major shareholders (13D/13G)"
    override val f13Hinweis = "Anyone holding more than 5% of a company reports it to the SEC.\n• 13D: active, the holder wants influence, for example on the board or a sale of the company\n• 13G: passive, the shares are only an investment, like large fund companies hold them\n\nThe latest filing of each holder is shown."
    override val f13Aktiv = "13D, active"
    override val f13Passiv = "13G, passive"
    override val f13Quelle = "Source: U.S. Securities and Exchange Commission (EDGAR, Form 144, Schedule 13D and 13G), fetched with your contact address"
    override val omTitel = "Major shareholders trade"
    override val omHinweis = "Muscat Clearing & Depository shows purchases and sales by holders of 5% or more on the last trading day, so by large shareholders. The date shown is the day of fetching."
    override fun omAnteil(p: String) = if (p.isBlank()) "Major shareholder" else "Stake afterwards $p%"
    override fun weltQuelle(land: String, stand: String) = when (land) {
        "LK" -> "Source: Colombo Stock Exchange, Dealings by Directors (cse.lk)"
        "OM" -> "Source: Muscat Clearing & Depository, Major Investors Trade Activities (mcd.om)"
        "EG" -> "Source: Egyptian Exchange, Trading of Insiders (egx.com.eg), imported by you"
        "NG" -> "Source: Nigerian Exchange, Notification of Share Dealing by Insiders (ngxgroup.com), imported by you"
        "KW" -> "Source: Boursa Kuwait, Insider Disclosures (boursakuwait.com.kw), imported by you"
        "QA" -> "Source: Qatar Stock Exchange, Insider Trades, imported by you, share counts only"
        else -> "Source: $land"
    } + if (stand.isNotBlank()) ", as of $stand" else ""
    override val saTitel = "Sources to fetch yourself"
    override val saUntertitel = "Where to find the files and how to get them"
    override val saEinleitung = "These registers provide their data on their own website. The app opens the page and imports a downloaded file automatically. The data stays on the device."
    override fun saName(id: String) = when (id) {
        "sedi" -> "Canada: SEDI"
        "consob" -> "Italy: Consob"
        "bund" -> "Germany: Bundesanzeiger"
        "fca" -> "United Kingdom: FCA"
        "bse" -> "India: BSE"
        "egx" -> "Egypt: EGX"
        "kw" -> "Kuwait: Boursa Kuwait"
        "qse" -> "Qatar: QSE"
        "pl" -> "Poland"
        "li" -> "Liechtenstein"
        "mt" -> "Malta"
        "asx" -> "Australia: ASX"
        else -> id
    }
    override fun saInhalt(id: String) = when (id) {
        "sedi" -> "Insider trades of Canadian companies as a weekly report (PDF)."
        "consob" -> "Short positions in Italian shares (Excel file PncPubbl.xlsx)."
        "bund" -> "Net short positions in German shares from 0.5 % of share capital (CSV file)."
        "fca" -> "Total of notified net short positions per UK share (two CSV files: current and historic)."
        "bse" -> "Insider trades of Indian companies as a full list (CSV). The app fetches new NSE filings per stock by itself."
        "egx" -> "Daily report on purchases and sales by insiders and major shareholders (English PDF)."
        "kw" -> "An insider's disclosure of a trade (form as a web page)."
        "qse" -> "Purchases and sales by insiders on the last trading day (web page). You open the page yourself in your browser."
        "pl", "li", "mt" -> "Notifications by managers under the EU Market Abuse Regulation (form as PDF)."
        "asx" -> "A director's notice of a change in holdings (Appendix 3Y, PDF)."
        else -> ""
    }
    override fun saSchritte(id: String) = when (id) {
        "sedi" -> listOf(
            "Open in the app and follow the steps on the site.",
            "Choose “View summary reports”, then “Weekly summary” and download the last full week as PDF.",
            "The app imports the file if it can read it. Convert weekly PDF reports to JSON first with tools/kanada/sedi.py.",
        )
        "consob" -> listOf(
            "Tap Open. The file PncPubbl.xlsx downloads directly from consob.it.",
            "The app imports it.",
        )
        "fca" -> listOf(
            "Open in the app and download the current list as CSV from the FCA page.",
            "For the history of the past year, also download the historic list.",
            "The app imports each file and combines both.",
        )
        "bund" -> listOf(
            "Open in the app. The page shows the current positions.",
            "For a longer history, tick “historische Daten” and search.",
            "Tap “Als CSV herunterladen”. The app imports the file.",
        )
        "bse" -> listOf(
            "Open in the app.",
            "Choose “Download All Records”. The app imports the CSV file unchanged.",
        )
        "egx" -> listOf(
            "Open in the app and find the daily report “Trading of Insiders”.",
            "Download the PDF. The app imports it.",
        )
        "kw" -> listOf(
            "Open in the app and open a disclosure under “Insider Disclosures”.",
            "On a phone, tap “Use this page”. On a computer, save the page as HTML and the app imports the file.",
        )
        "qse" -> listOf(
            "In your own browser, go to the Qatar Stock Exchange website (qe.com.qa) and open the “Insider Trades” page.",
            "Wait until the “Trades Details” table is filled, then save the page: on a computer as HTML, on a phone with “Download” in the browser menu.",
            "Choose the file under Profile › Advanced › Import register data.",
        )
        "pl", "li" -> listOf(
            "Open in the app and find a notification under Article 19 MAR.",
            "Download the PDF. The app imports it.",
        )
        "mt" -> listOf(
            "Open in the app and find a notification under Article 19 MAR.",
            "Download the PDF. The app imports it.",
            "The Malta Stock Exchange allows saving and use with the source named, as long as the content stays unchanged.",
        )
        "asx" -> listOf(
            "Open in the app, search for the code and open an “Appendix 3Y” notice.",
            "Download the PDF. The app imports it.",
            "ASX allows the notices for private use only.",
        )
        else -> emptyList()
    }
    override val saOeffnen = "Open page"

    // 5.91 Profil
    override val erwUntertitel = "Spreads, price updates, custom rate, register data, export for Portfolio Performance"
    override val erwHandel = "Trading costs"
    override val erwKurse = "Price updates"
    override val erwZinsAuto = "Interest currently follows the ECB key rate. Pick a rate here to use your own from then on."
    override val erwRegister = "Register data"
    override val erwPp = "Portfolio Performance"
    override val planVorbelegt = "Day and time are preset. Tap a row to change it."

    // 5.91 Wertpapierseite
    override val bwAlleKennzahlen = "All key figures"
    override val bwTer = "Ongoing costs (TER)"
    override val bwPreisalarm = "Price alert"
    override fun bwPreisalarmZahl(n: Int) = "Price alert · $n"
    override val bwOffeneOrders = "Open orders"
    override val bwFuerUndWider = "Opportunities and risks"
    override val bwHebelprodukte = "Leveraged products"
    override val bwHintergrund = "Background"
    override fun bwHintergrundUnter(themen: List<String>) = (if (themen.size == 1) "1 topic: " else "${themen.size} topics: ") + themen.joinToString(", ")
    override fun bwThema(key: String) = when (key) {
        "OPTIONEN" -> "Options"; "TRENDS" -> "Trends"; "HANDELNDE" -> "Who trades it"; "FUEHRUNG" -> "Insiders"
        "LEER" -> "Short selling"; "FDA" -> "FDA"; "BEHOERDEN" -> "Politics and agencies"; "LOBBY" -> "Lobbying"
        "HALTER" -> "Large holders"
        else -> key
    }
    override val bwRrUnter = "Largest decline, time to recover, return"
    override val bwGesetze = "Bills"
    override val bwGesetzeHinweis = "Bills the company reported lobbying on. Members of Congress who trade the stock are listed under “Who trades it”."

    // 5.91 Startseite
    override val maerkteTitel = "Markets"
    override val maerkteBewegungen = "Movers"
    override val maerkteSucheZeile = "Top movers, IPOs, sentiment, outlook, trends and heatmap"
    override val handeln = "Trade"
    override val zeitraumMenue = "Custom range and trade calendar"

    // 5.92 Datenquellen
    override val dqTitel = "Data sources"
    override val dqUntertitel = "Which sources are answering right now"
    override val dqEinleitung = "This page shows which data sources of the app are answering right now.\n\n" +
        "• Green: answering\n" +
        "• Yellow: last request failed, the data loaded last stays visible\n" +
        "• Red: no answer for hours\n" +
        "• Gray: not used yet or switched off"
    override fun dqName(id: String) = when (id) {
        "investorenbriefe" -> "Letters from fund managers"
        "yahoo" -> "Yahoo Finance (prices, metrics, news)"
        "sec" -> "SEC (US filings)"
        "ezb" -> "ECB (rates, exchange rates)"
        "wikipedia" -> "Wikipedia and Wikidata"
        "polymarket" -> "Polymarket"
        "kalshi" -> "Kalshi"
        "erwaehnungen" -> "Mentions (Reddit, ApeWisdom)"
        "kongress" -> "US Congress"
        "usbehoerden" -> "US agencies (contracts, lobbying, FDA)"
        "uswirtschaft" -> "US economic data (Treasury, Fed, CFTC)"
        "bafin" -> "BaFin"
        "europa" -> "European registers"
        "nasdaq" -> "Nasdaq (calendars)"
        "welt" -> "Registers worldwide"
        "stimmung" -> "Sentiment gauges"
        "truth" -> "Truth Social archive"
        "nachrichten" -> "News services with your own key"
        "github" -> "GitHub (new versions, source set)"
        "fruehsignale" -> "Inventories and economic data (EIA, TSA, drought, GIE)"
        else -> id
    }
    override val dqQuellen = "Sources"
    override val dqAus = "Temporarily switched off"
    override val dqNie = "Not requested yet"
    override fun dqAntwortet(seit: String) = "Answering · last $seit"
    override fun dqUmweg(seit: String) = "Answering via a fallback route · last $seit"
    override fun dqFehler(art: String, letzterErfolg: String?) =
        "Last: $art · " + if (letzterErfolg != null) "last success $letzterErfolg" else "no success yet"
    override fun dqArt(art: de.troetenrepublik.app.engine.Quellenbuendel.Fehler?, code: Int) = when (art) {
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.GESPERRT -> if (code > 0) "blocked ($code)" else "blocked"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.LEER -> "empty answer"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.FORM -> "unexpected answer"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.ZEIT -> "timeout"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.NETZ -> "no connection"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.SERVER -> if (code > 0) "server error ($code)" else "server error"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.AUS -> "switched off"
        null -> "error"
    }
    override val dqStandKopf = "Source set"
    override val dqStandLabel = "Version"
    override fun dqStand(version: Long, geladen: Boolean) = if (geladen) "$version (downloaded)" else "$version (built in)"
    override val dqGesucht = "Last checked"
    override val dqNochNicht = "Not yet"
    override fun dqErgebnis(ergebnis: String, http: Int) = when (ergebnis) {
        "NEU" -> "new set applied"
        "AKTUELL" -> "up to date"
        "HTTP" -> "not found ($http)"
        "OHNE_SIGNATUR" -> "signature missing"
        "SIGNATUR" -> "signature invalid"
        "FORM" -> "content unreadable"
        "HOST" -> "unknown address, rejected"
        "VERSION" -> "up to date"
        else -> "not reachable"
    }
    override val dqSuchen = "Check for a new set now"
    override val dqSucht = "Checking"
    override val dqZuruecksetzen = "Reset counters"
    override val dqHinweisLink = "Learn more"
    override val dqAusblenden = "Hide"
    override val dqVerstanden = "Got it"
    override fun dqHinweisTitel(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis) = h.titelEn.ifBlank { h.titel }
    override fun dqHinweisText(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis) = h.textEn.ifBlank { h.text }

    // 5.92 P3 Startseite, Profil, Einstellungen
    override val wfTitel = "Currencies on tap"
    override val wfText = "Tapping an amount or price switches in this order. Bookings stay in euros."
    override val wfReihenfolge = "Order"
    override val wfHoch = "Move up"
    override val wfRunter = "Move down"
    override fun wfVoll(n: Int) = "At most $n entries"
    override val wfOriginal = "Original currency"
    override val wfOriginalKurz = "Original"
    override val wfOriginalHinweis = "Price on the home exchange, amount of a foreign filing"
    override val wfVorgabe = "Restore default"
    override val wlZeile = "Reinvest dividends"
    override val wlWieEinstellung = "As in settings"
    override val wlEigeneWahl = "Own choice for this security"
    override val wlGlobal = "Reinvest dividends automatically"
    override val wlGlobalHinweis = "Free, into fractions of the same stock, changeable per position"
    override val wlZinssatz = "Interest rate"
    override val wlSortSeit = "By performance since added"
    override val wlSortDatum = "By date added"
    override val zgKostenpflichtig = "Paid"
    override val zgIntroPunkte = listOf(
        "Some services only share their data with your own key.",
        "All access keys here are free, you get them from the provider.",
        "The key stays on this device and only goes to that provider.",
        "The app counts every request and stays below the limits.",
    )
    override fun zgZweck(dienst: String) = when (dienst) {
        "guardian" -> "Business and market news in the “The Guardian” tab, with article text."
        "finnhub" -> "Market news in the “Finnhub” tab: markets, mergers, forex and crypto."
        "alphavantage" -> "Market news in the “Alpha Vantage” tab."
        "fec" -> "Campaign contributions of a company's PAC on the page of a US stock."
        "uspto" -> "Patents granted in the last year on the page of a US stock."
        "finra" -> "Short selling and dark pool trading on the page of a US stock."
        "reddit" -> "Latest Reddit posts about a stock with sentiment and an own ranking under Trends."
        "sec_kontakt" -> "New US insider filings directly from the SEC and fund reports for “What investors write”."
        "poly_adresse" -> "Your real Polymarket positions under “My bets”."
        "eodhd" -> "News for the opened stock on its page."
        else -> "Translates titles and outcomes of prediction markets into German."
    }
    override fun zgPunkte(dienst: String) = when (dienst) {
        "guardian" -> listOf(
            "Developer key for non-commercial use",
            "Up to 500 requests a day, the app uses at most 450",
            "Content stays in the app for at most 24 hours",
        )
        "finnhub" -> listOf(
            "Personal use only, the data may not be passed on",
            "60 requests per minute, the app asks every 10 minutes",
        )
        "alphavantage" -> listOf(
            "25 requests a day, the app asks at most once an hour",
            "If the service rejects the key, the app shows its message",
        )
        "fec" -> listOf(
            "Key from api.data.gov with an email address",
            "1,000 requests per hour, the app uses at most 900",
            "Without a key a shared demo key with few requests applies",
            "Only committee totals, no names of individual donors",
        )
        "finra" -> listOf(
            "Free account in the FINRA API Console, create a Public Credential there",
            "Not for commercial purposes according to FINRA",
            "Enter client ID and secret, the app gets a token with them",
            "The app asks at most 20 times a minute and 500 times a day",
        )
        "uspto" -> listOf(
            "Key with a USPTO account and ID.me verification",
            "Unused keys expire after 90 days",
            "The app asks at most 500 times a day",
        )
        "reddit" -> listOf(
            "For non-commercial use, new access only on request",
            "Then create an app at reddit.com/prefs/apps",
            "Enter the client ID, for “script” or “web app” also the secret",
            "The app only reads, without your Reddit account",
        )
        "sec_kontakt" -> listOf(
            "The SEC requires a contact address in every request",
            "The address only goes to the SEC",
            "Without it, new filings arrive with each app version",
        )
        "poly_adresse" -> listOf(
            "Your public address is enough (0x and 40 characters)",
            "It only goes to Polymarket",
            "Never enter a private key or recovery words",
        )
        "eodhd" -> listOf(
            "Personal use only, the data may not be passed on",
            "No display for others, do not share the access",
            "20 calls a day, reset at midnight GMT",
            "One request costs 5 calls, so about 4 requests a day",
            "The app asks only when you expand the section on a security's page and keeps the result for an hour",
        )
        else -> listOf(
            "DeepL API Free with 500,000 characters a month",
            "The app uses at most 450,000 and translates each text once",
            "Only market content is sent, no data about you",
        )
    }
    override val saAnsehenTitel = "View only"
    override val saAnsehenText = "For these sources there is usually no import. The page opens in the app, and the app imports a downloaded file if it can read it."
    override fun saAnsehenName(id: String) = when (id) {
        "bh" -> "Bahrain: Bahrain Bourse"
        "jm" -> "Jamaica: Jamaica Stock Exchange"
        "tt" -> "Trinidad and Tobago: TTSE"
        "il" -> "Israel: Magna"
        "id" -> "Indonesia: IDX"
        "jp" -> "Japan: EDINET"
        "kr" -> "South Korea: DART"
        "pt" -> "Portugal: CMVM"
        "hr" -> "Croatia: Zagreb Stock Exchange"
        "jefs" -> "US: federal judges (JEFS)"
        "ep" -> "Members of the European Parliament"
        "aph" -> "Australia: Parliament"
        "oge" -> "US: executive branch (OGE)"
        else -> id
    }
    override fun saAnsehenInhalt(id: String) = when (id) {
        "bh" -> "Company announcements, including insider trades."
        "jm" -> "Notices of purchases and sales by directors and connected parties."
        "tt" -> "Insider trading notices among the company announcements."
        "il" -> "Mandatory filings of Israeli companies, also on holdings of major shareholders and officers. Mostly in Hebrew."
        "id" -> "Filings on share ownership of directors and major shareholders, mostly in Indonesian."
        "jp" -> "Large shareholding reports (5 % and more) and company reports. The API needs a free key."
        "kr" -> "Filings on holdings of executives and major shareholders. The Open DART API needs a free key."
        "pt", "hr" -> "Managers' transactions under the EU Market Abuse Regulation."
        "jefs" -> "Annual financial disclosure reports of US federal judges with holdings and transactions. Each report after accepting the terms."
        "ep" -> "Declarations of private interests, as a PDF on each member's page."
        "aph" -> "Register of members' interests with shareholdings, as a PDF per person."
        "oge" -> "Financial disclosures of senior officials. Some reports are only available on request with Form 201."
        else -> ""
    }

    // 5.92 P1 Wertpapierseite oben
    override val p1IndWaehlen = "Tap a name to show it in the chart."
    override val p1BilanzLaedt = "Calculating track record"
    override val p1BilanzWenig = "Too few signals for a track record"
    override fun indForschung(art: String): String = when (art) {
        "sma50", "sma200" -> "Worked in the Dow until 1986, little on later data. As a filter the average cuts drawdowns more than it adds return (Brock et al. 1992, Fang et al. 2014, Faber 2007)."
        "bb" -> "Band signals did not reliably beat buy and hold after costs. Band width mainly reflects current volatility (Lento et al. 2007)."
        "ichimoku" -> "Few studies. Default settings profitable only in some periods, never for currencies. Weak evidence (Deng et al. 2021)."
        "sar" -> "Hardly any peer-reviewed tests. Early profits of similar trend systems in futures vanished after 1985. Very weak evidence (Park and Irwin 2007)."
        "rsi" -> "Gains only in single markets without multiple-testing correction. Broad tests find no edge after costs (Chong and Ng 2008, Rink 2023)."
        "stoch" -> "Mostly practitioner studies. No robust evidence of predictive power after costs (Chong et al. 2017)."
        "macd" -> "A trend rule like a moving-average crossover. Early edges mostly vanish on new data and after costs (Chong and Ng 2008, Rink 2023)."
        "atr" -> "High volatility tends to follow high volatility. Well supported as a risk gauge, says nothing about direction (Engle 1982, Mandelbrot 1963)."
        "obv" -> "Few studies, for example Greater China and monthly signals for US indices. Not robustly shown for single stocks (Tsang and Chong 2009, Neely et al. 2014)."
        "kerzen" -> "Some reversal patterns showed signs of working for single US stocks in the 1990s; for the Dow Jones from 1992 to 2002 no value remained once chance was accounted for (Caginalp and Laurent 1998, Marshall et al. 2006)."
        else -> null
    }?.let { "Research: $it" } ?: ""
    override val indForschungAllgemein = "According to research, classic signals show little predictive power after costs and on new data. Their documented use is mainly for risk and volatility (Park and Irwin 2007, Rink 2023)."
    override fun p1BilanzSeit(jahr: Int, signale: Int) = "\nSince $jahr, $signale signals"
    override fun p1BilanzAllgemein(treffer: String, vergleich: String, werte: Int, ab: Int) = "\nIn general ($werte stocks and indices since $ab): $treffer hits, otherwise $vergleich"
    override fun p1BilanzZeile(stufe: String, treffer: String, vergleich: String, unruhe: Boolean): String {
        val wort = when (stufe) { "ERKENNBAR" -> "noticeable"; "UMGEKEHRT" -> "rather reversed"; else -> "low" }
        return "Usefulness here $wort · $treffer hits, otherwise $vergleich"
    }
    override fun p1BilanzDetail(art: String, jahr: Int, signale: Int, richtig: Int, treffer: String?, vergleich: String?, tage: Int): String {
        val regel = when (art) {
            "sma50", "sma200" -> "The price crosses the average upwards (rising) or downwards (falling)."
            "bb" -> "The price drops below the lower band (rebound expected) or rises above the upper band (pullback expected)."
            "rsi" -> "The RSI climbs back above 30 (rising) or drops back below 70 (falling)."
            "stoch" -> "The stochastic climbs back above 20 (rising) or drops back below 80 (falling)."
            "macd" -> "The MACD line crosses its signal line upwards (rising) or downwards (falling)."
            "ichimoku" -> "The price leaves the cloud upwards (rising) or downwards (falling)."
            "sar" -> "The dots switch below the price (rising) or above the price (falling)."
            "obv" -> "The OBV crosses its 20-day average upwards (rising) or downwards (falling)."
            "kerzen" -> "A pattern with a direction appears: one of the six rebound patterns after a decline (rising) or one of the six pullback patterns after a rise (falling). Dojis do not count."
            else -> "The ATR rises above 1.3 times its usual level of the year before."
        }
        if (treffer == null || vergleich == null) return "Since $jahr there were only $signale signals, too few for a track record.\nCounted as a signal: $regel"
        val ergebnis = if (art == "atr")
            "Since $jahr there were $signale warnings. In $richtig cases ($treffer) the price moved more than usual over the next $tage trading days. Without a warning that happened in $vergleich of cases."
        else
            "Since $jahr there were $signale signals. $richtig of them ($treffer) were right $tage trading days later. Without a signal the price moved in that direction in $vergleich of cases over the same time."
        return "$ergebnis\nCounted as a signal: $regel\nNoticeable means at least 5 percentage points above the comparison and, given the number of signals, probably not chance. Only signals whose test periods do not overlap are counted."
    }
    override fun p1Spanne(unten: String, oben: String, tage: Int) = "In $tage days mostly between $unten and $oben"
    override val p1SpanneMehr = "In about 2 out of 3 cases the price ends up inside this range after 30 days, if it keeps swinging as assumed. The range does not predict the direction."
    override val p1SeitKauf = "Held since"
    override fun p1Haltedauer(datum: String, tage: Long) = "$datum · " + if (tage < 1) "today" else ptDuration(tage)
    override val p1HebelLong = "Rising (long)"
    override val p1HebelShort = "Falling (short)"
    override val pcEinfuehrung = "Drag across the chart to see the prices at a point in time. Below you pick the display, add indicators or comparisons and draw your own lines.\n" +
        "A candle stands for one period: its body runs from the first to the last price, the thin lines to the high and low, its colour shows rise or fall. Heikin-Ashi smooths the candles so trends look calmer."
    override val pcDarstellung = "Display"
    override val pcEinblenden = "Show"
    override val pcZeichnen = "Draw"
    override val pcOhlc = listOf("Open", "High", "Low", "Close")
    override val pcEinstieg = "good for beginners"
    override val pcIndikatorenEinfuehrung = "Indicators are calculated from past prices and volumes. Lines in the price chart sit on top of the price, the others get their own panel below."
    override fun pcWerkzeug(key: String) = if (key == "waagerecht") "Horizontal line" else chartWerkzeug(key)

    // 5.92 P2 Wertpapierseite Analyse
    override val bkTitel = "Valuation and key figures"
    override val bkBewertung = "Valuation"
    override val bkWachstum = "Financials"
    override fun bkBewertungSatz(noetig: String, art: Int, bisher: String?) = when (art) {
        1 -> "For today's price to pay off, earnings would have to grow by more than $noetig a year for 10 years."
        -1 -> "Today's price already pays off if earnings grow by less than $noetig a year for 10 years."
        2 -> "Today's price pays off even if earnings shrink by $noetig a year for 10 years."
        else -> "For today's price to pay off, earnings would have to grow by $noetig a year for 10 years."
    } + (bisher?.let { " So far: $it a year." } ?: "")
    override fun bkFairerWert(preis: String, wachstum: String, abstand: String, darueber: Boolean) =
        "Fair value at $wachstum growth a year: $preis. The price is $abstand ${if (darueber) "above" else "below"} it."
    override val bkBewertungVerlust = "The company is not making a profit right now. Without profit there is no fair value to calculate."
    override fun bkBilanzSatz(ampel: Int, gruende: List<String>) =
        listOf("Solid balance sheet", "Mixed balance sheet", "Weak balance sheet").getOrElse(ampel) { "" } + if (gruende.isEmpty()) "" else ": " + gruende.joinToString(", ")
    override fun bkGrund(key: String) = when (key) {
        "PROFITABEL" -> "profitable"; "VERLUST" -> "making a loss"; "SCHULDEN_SINKEN" -> "debt falling"; "SCHULDEN_STEIGEN" -> "debt rising"
        "GEWINNE_STEIGEN" -> "profits rising"; "GEWINNE_SINKEN" -> "profits falling"; "GELD_FLIESST" -> "earns cash from operations"
        "GELD_FEHLT" -> "burns cash in operations"; "PLEITE_RISIKO" -> "higher risk of insolvency"; "NEUE_AKTIEN" -> "issues new shares"
        else -> key
    }
    override fun bkWachstumSatz(umsatz: String, umsatzPct: String?, gewinn: String?, gewinnPct: String?) =
        "Revenue $umsatz" + (umsatzPct?.let { " ($it)" } ?: "") + (gewinn?.let { " · Profit $it" + (gewinnPct?.let { p -> " ($p)" } ?: "") } ?: "")
    override fun bkWachstumUnter(jahr: String) = "Fiscal year $jahr compared with the year before"
    override val bkFairSelbst = "Calculate fair value yourself"
    override fun bkGruppe(key: String) = when (key) {
        "BEWERTUNG" -> "Valuation"; "RENTABILITAET" -> "Profitability"; "FINANZKRAFT" -> "Financial strength"; "DIVIDENDE" -> "Dividend"
        "AKTIONAERE" -> "Shareholders"; "ERWARTUNG" -> "Expectations"; "HANDEL" -> "Trading and basics"
        else -> key
    }
    override fun bkVonBis(von: String, bis: String) = "$von to $bis"
    override val bkKuv = "Price to sales"
    override val bkEvEbitda = "EV/EBITDA"
    override val bkPeg = "PEG"
    override val bkFcfRendite = "Free cash flow yield"
    override val bkGesamtkapitalRendite = "Return on assets"
    override val bkEbitdaMarge = "EBITDA margin"
    override val bkVerschuldung = "Debt to equity"
    override val bkLiquiditaet = "Current ratio"
    override val bkDividendeFuenf = "Dividend yield, 5-year average"
    override val bkDividendenWachstum = "Dividend growth per year"
    override val bkLeerQuote = "Short interest"
    override val bkLeerTage = "Days to cover"
    override val bkInsider = "Held by insiders"
    override val bkInstitutionen = "Held by institutions"
    override val bkAktien = "Shares outstanding"
    override val bkAktienVeraenderung = "Share count vs. prior year"
    override val bkZielSpanne = "Price target range"
    override val bkZielMedian = "Median price target"
    override val bkGewinnErwartet = "Expected earnings per share"
    override val bkGewinnWachstumJahr = "Earnings growth this year"
    override val bkGewinnWachstumFolge = "Earnings growth next year"
    override val bkUmsatzWachstumFolge = "Revenue growth next year"
    override val bkNaechsteZahlen = "Next earnings"
    override val bkJahresVeraenderung = "Price change over 12 months"
    override val anLaedt = "Loading ratings"
    override val anKeineDaten = "No ratings found."
    override val anVerteilung = "Distribution"
    override fun anVergleich(diff: Double) = when {
        diff > 0.1 -> "Friendlier than three months ago."
        diff < -0.1 -> "More sceptical than three months ago."
        else -> "About the same as three months ago."
    }
    override val anNurUs = "The source lists individual firms and their track record for US stocks only."
    override val anBereinigt = "Adjusted view"
    override fun anAlleHaeuser(n: Int) = if (n == 1) "1 firm" else "All $n firms"
    override val anGewichtet = "Weighted by hit rate"
    override val anZiel = "Average price target"
    override fun anZielGewichtet(preis: String) = "weighted $preis"
    override val anTrefferAlle = "Hits after 3, 6 and 12 months"
    override fun anTrefferSatz(quote: String, n: Int, monate: Int, index: String?, wenig: Boolean) =
        "$quote of $n upgrades and downgrades were right after $monate months, measured ${index?.let { "against the $it" } ?: "by the price move"}." +
            if (wenig) " That is hardly better than chance." else ""
    override fun anHinweis(herde: String) =
        "The adjusted view is only a pointer. It gives more weight to firms that were right more often on this stock and to newer ratings than older ones.\n" +
            "• The number next to the rating runs from −2 (strong sell) to +2 (strong buy).\n" +
            "• Few ratings per firm: the hit rate is pulled towards the middle.\n" +
            "• Measured against the index, so a rising market does not count as a hit.\n" +
            "• $herde of the ratings followed other firms in the same direction within three days and count less.\n" +
            "• Conflicts of interest, such as banking business with the company, cannot be seen in the data.\n" +
            "• The hit rate only measures past calls."
    override val anHaeuser = "Firms"
    override fun anStufe(s: Int) = when (s) {
        2 -> "Strong buy"; 1 -> "Buy"; 0 -> "Hold"; -1 -> "Sell"; else -> "Strong sell"
    }
    override fun anKursziel(preis: String) = "Target $preis"
    override val anVeraltet = "older than a year"
    override fun anZuWenige(n: Int) = if (n == 0) "No rating to evaluate yet" else "Only $n ${if (n == 1) "rating" else "ratings"} to evaluate"
    override fun anTreffer(je: List<String>, bereinigt: String) = "Hits after 3, 6, 12 months: ${je.joinToString(" · ")} · adjusted $bereinigt"
    override val anWiderspruch = "Rating and price target do not match"
    override val crInfo = "How the balance is calculated"
    override fun crLeer(anteil: String, trend: Int?, us: Boolean) =
        (if (us) "$anteil of freely traded shares sold short" else "$anteil of shares in reported short positions") +
            when (trend) { 0 -> ", rising"; 1 -> ", falling"; 2 -> ", steady"; else -> "" }
    override val crOptionen = "Options trading"
    override fun crOptionenWert(long: String) = "$long of volume bets on rising prices"
    override fun evErwartet(eps: String?, umsatz: String?) = "expected: " + listOfNotNull(eps?.let { "EPS $it" }, umsatz?.let { "revenue $it" }).joinToString(", ")
    override val evLetzteZahlen = "Latest results"
    override fun evQuartal(ende: String) = "Quarter to $ende"
    override val rrNutzen = "Shows how far the price has fallen before and how long it took to get back to the old high. From this you see what drop and what wait you would have to sit through."
    override val rrErholung = "Recovery took"
    override val rrNochNicht = "not reached yet"
    override val rrZeitraum = "Declines in a period"
    override fun rrVonBis(von: String, bis: String) = "$von to $bis"
    override val wzRueckblick = "Look back"
    override val wzRueckblickUnter = "What a lump sum or a savings plan would have become"
    override val wzEinmal = "Once"

    // 5.92 Maerkte
    override val maerkteAusblick = "Outlook"
    override val infoErklaerung = "Explanation"
    override val mpVomHochKurz = "from high"
    override val cotInfo = "Every Friday the US futures regulator CFTC publishes how large participants are positioned in US futures markets, as of the Tuesday before.\n\n" +
        "• Shown is the net position of large speculators such as hedge funds\n" +
        "• The bar shows where it stands compared with the last three years: far right is as bullish as never before, far left as bearish as never before\n\n" +
        "At extremes many traders are often positioned the same way, and the market can then move against them quickly."
    override fun cotKern(markt: String?, index: Int?) = when {
        markt != null && index != null && index >= 80 -> "Large speculators are as bullish on $markt as rarely in three years."
        markt != null && index != null && index <= 20 -> "Large speculators are as bearish on $markt as rarely in three years."
        else -> "Large speculators are not strikingly one-sided in any of these markets right now."
    }
    override fun cotSatz(index: Int) = when {
        index >= 90 -> "As bullish as almost never"
        index >= 80 -> "Very bullish"
        index >= 60 -> "Rather bullish"
        index > 40 -> "Balanced"
        index > 20 -> "Rather bearish"
        index > 10 -> "Very bearish"
        else -> "As bearish as almost never"
    }
    override fun cotDetail(anteil: String, aenderung: String, index: Int) =
        "Large speculators hold a net $anteil of open contracts, $aenderung pts vs. prior week. Index $index of 100 compared with three years."
    override val sbSorgen = "Worries on Wikipedia"
    override val sbQuelleSorgen = "Source: Wikimedia"
    override val sbSorgenMehr = "How often the English Wikipedia articles on recession and stock market crash are read, compared with the past year. A lot of reading means a lot of worry and thus a low value."
    override fun trDepotSatz(betrag: String) = "€1,000 would have become $betrag"
    override val trZumEtf = "Open the ETF"
    override val rotTitel = "Sector rotation"
    override val rotInfo = "Sector rotation means investors shift money from one sector to another.\n\n" +
        "• Compared is how each sector did last month against the average of all, and whether it improved against the two months before\n" +
        "• Where it does clearly better, money flows in, where it does clearly worse, it flows out\n" +
        "• The reason is a rule of thumb from the kind of sectors, the VIX and interest rates\n\n" +
        "Calculated from the price performance of the sector ETFs in the list."
    override val rotKeine = "No clear shift between sectors right now."
    override fun rotSatz(aus: String?, nach: String?) = when {
        aus != null && nach != null -> "Money is flowing out of $aus into $nach."
        nach != null -> "Money is flowing into $nach."
        aus != null -> "Money is flowing out of $aus."
        else -> ""
    }
    override fun rotGrund(grund: String) = when (grund) {
        "SICHERHEIT" -> "Uncertainty is rising, investors look for safe and steady earnings."
        "RISIKOFREUDE" -> "Risk appetite is rising, investors bet on growth and the economy again."
        "INFLATION" -> "Commodities are in demand, often a sign of inflation worries."
        "ABSICHERUNG" -> "Investors are hedging: the VIX has risen, gold and commodities are in demand."
        "ZINSSENKUNG" -> "Falling or expected lower rates help rate-sensitive sectors such as real estate and utilities."
        "ZINSANSTIEG" -> "Rising rates help banks and insurers."
        else -> ""
    }
    override val rotAus = "Out"
    override val rotNach = "In"
    override val meTitel = "What the market expects"
    override val meInfo = "What investors and bettors currently expect. This is a snapshot, not a forecast.\n\n" +
        "• US policy rate: from Fed funds futures, 100 minus the price gives the expected rate for the month\n" +
        "• The percentages below are Polymarket prices: a share pays 1 dollar if the event happens, so the price is the estimated probability\n\n" +
        "Betting markets can be wrong, especially with little volume."
    override val meQuelle = "Source: Polymarket"
    override fun meThema(schluessel: String) = when (schluessel) {
        "fed" -> "Next Fed rate decision"
        "fed_jahr" -> "Fed rate cuts this year"
        "ezb" -> "Next ECB rate decision"
        "rezession" -> "US recession"
        "inflation" -> "US inflation"
        "oel" -> "Oil price (WTI)"
        "hormus" -> "Strait of Hormuz"
        "iran" -> "Iran"
        "wahlen" -> "Elections"
        else -> schluessel
    }
    override val zeTitel = "US policy rate (Fed funds)"
    override fun zeSatz(schritte: Double?): String {
        val n = schritte?.let { Math.round(kotlin.math.abs(it)).toInt() } ?: return "How the rate should develop according to futures prices."
        return when {
            schritte <= -0.75 -> "The market expects about " + (if (n == 1) "one cut" else "$n cuts") + " of 0.25 percentage points within twelve months."
            schritte >= 0.75 -> "The market expects about " + (if (n == 1) "one hike" else "$n hikes") + " of 0.25 percentage points within twelve months."
            else -> "The market expects hardly any change over the next twelve months."
        }
    }
    override val zeHeute = "Now"
    override fun zeIn(monate: Int) = "in $monate mo."
    override val zeQuelle = "Source: CME Fed funds futures via Yahoo"
    override val blTitel = "Bubble detector"
    override val blInfo = "Bubbles are usually recognised only afterwards. These measures stood high before earlier excesses, for example in 2000 and 2021. None predicts when prices fall.\n\n" +
        "• Red: points to overheating\n" +
        "• Yellow: elevated\n" +
        "• Green: unremarkable or speaks against a bubble\n\n" +
        "Under “Speaks against” are reasons that can justify high prices. For sectors, the gain over twelve months and the distance to the 200-day line count."
    override fun blStufe(stufe: Int) = listOf("Bubble risk low", "Bubble risk elevated", "Bubble risk high").getOrElse(stufe) { "" }
    override fun blSatz(rot: Int, alle: Int, heiss: String?) =
        "$rot of $alle measures red." + (heiss?.let { " Hottest: $it." } ?: "")
    override val blDafuer = "Points to a bubble"
    override val blDagegen = "Speaks against"
    override val blBranchen = "Hottest sectors"
    override fun blSektor(jahr: String, zu200: String) = "$jahr in 12 months · $zu200 vs. 200-day line"
    override val blQuelle = "Sources: Yahoo, World Bank, Wikimedia, CNN, St. Louis Fed (FRED)"
    override fun blName(schluessel: String) = when (schluessel) {
        "kgv" -> "S&P 500 P/E"
        "buffett" -> "Buffett indicator"
        "trend" -> "Distance to 200-day line"
        "konzentration" -> "Weight of the top 10"
        "ipo" -> "IPOs"
        "stimmung" -> "Sentiment"
        "erwaehnung" -> "Bubble talk"
        "gewinn" -> "Expected earnings growth"
        "praemie" -> "Stocks vs. bonds"
        "breite" -> "Breadth of the rally"
        else -> schluessel
    }
    override fun blLage(schluessel: String, ampel: Int) = when (schluessel) {
        "kgv" -> listOf("Valuation within the usual range", "Higher than the long-term average", "Well above the average of about 16")
        "buffett" -> listOf("Stocks not more expensive than usual", "Stocks more expensive than usual", "Higher than in the 2000 dotcom bubble")
        "trend" -> listOf("No unusual lead", "Clearly above the trend", "Far above the trend")
        "konzentration" -> listOf("Broadly spread", "A few giants carry much weight", "As concentrated as rarely")
        "ipo" -> listOf("New stocks in normal demand", "New stocks in demand", "New stocks in high demand")
        "stimmung" -> listOf("No greed", "Greed", "Extreme greed")
        "erwaehnung" -> listOf("Bubbles rarely mentioned", "Bubbles mentioned more than usual", "Bubbles mentioned as often as rarely this year")
        "gewinn" -> listOf("Strong growth supports prices", "Moderate growth", "Hardly any growth expected")
        "praemie" -> listOf("Stocks yield clearly more than bonds", "Hardly ahead of bonds", "Bonds yield more than stock earnings")
        "breite" -> listOf("Rally broadly carried", "Large stocks pull ahead", "Rally rests on a few giants")
        else -> emptyList()
    }.getOrElse(ampel) { "" }
    override fun blErklaerung(schluessel: String, bipJahr: Int?, bipQuartal: String?) = when (schluessel) {
        "kgv" -> "Price divided by earnings of the last twelve months, for the S&P 500 via the SPY ETF. The long-term average is about 16. Yellow from 20, red from 25."
        "buffett" -> "Value of all US stocks (Wilshire 5000) relative to US economic output" + (bipQuartal?.let { " (GDP in $it, annualised)" } ?: bipJahr?.let { " (GDP $it per World Bank)" } ?: "") + ". Warren Buffett called it the best single measure of valuation. Yellow from 110%, red from 150%."
        "trend" -> "How far the S&P 500 stands above its average of the last 200 trading days. Yellow from 8%, red from 15%."
        "konzentration" -> "Share of the ten largest stocks in the market value of all S&P 500 stocks in the catalogue. For a long time it was about 20%, in 2000 a little over 25%. Yellow from 27%, red from 35%."
        "ipo" -> "Gain of the Renaissance IPO ETF over twelve months. It holds stocks after their IPO. Yellow from 20%, red from 50%."
        "stimmung" -> "CNN Fear & Greed Index, otherwise the app's own fear and greed index (Sentiment tab): 0 means extreme fear, 100 extreme greed. Yellow from 60, red above 75."
        "erwaehnung" -> "Views of the English Wikipedia article on economic bubbles. Shown is where the last seven days stand compared with the year, 100 means as many as never before. Yellow from 60, red from 80."
        "gewinn" -> "How much earnings of the S&P 500 stocks in the catalogue are expected to grow in the next fiscal year, according to analyst estimates on Yahoo. Green from 10%, red below 3%."
        "praemie" -> "Earnings yield of stocks (100 divided by the P/E) minus the yield of ten-year US Treasuries. Below zero, safe bonds yield more and much depends on future growth. Yellow below 2 points, red below 0."
        "breite" -> "Lead of the cap-weighted S&P 500 (SPY) over the equal-weighted S&P 500 (RSP) over twelve months. Yellow from 4 points, red from 10."
        else -> ""
    }

    // 5.92 Copytrading und Politik
    override fun copyOgeNeu2(n: Int, datum: String) = if (n == 1) "New filing from $datum" else "$n new filings, latest from $datum"
    override val pdfLaden = "Download the original PDF"
    override val pdfKurz = "PDF"
    override val pdfGestartet = "The PDF is being saved to your downloads folder"
    override val pdfFehler = "The PDF could not be loaded"
    override fun parteiKurz(p: String) = when (p) { "R" -> "Rep."; "D" -> "Dem."; "L" -> "Lib."; else -> "Ind." }
    override fun parteiLang(p: String) = when (p) { "R" -> "Republican"; "D" -> "Democrat"; "L" -> "Libertarian"; else -> "Independent" }
    override fun usStaat(code: String, name: String) = name
    override fun wahlkreisVon(staat: String, nr: Int) = "$staat, district $nr"
    override fun kammerSeit(senat: Boolean, jahr: Int) = (if (senat) "in the Senate since " else "in the House since ") + jahr
    override val ausschuesse = "Committees"
    override fun ausschussFunktion(f: String) = when (f) {
        "Chair", "Chairman" -> "chair"; "Vice Chair", "Vice Chairman" -> "vice chair"; "Cochairman" -> "co-chair"
        "Ranking Member" -> "ranking member"; "Ex Officio" -> "ex officio"; else -> null
    }
    override val vmHinweisOge = "Sum of the value ranges in the annual report to the U.S. Office of Government Ethics.\n" +
        "• Counted: companies, rights, investments and his wife’s assets\n" +
        "• Deducted: loans and liabilities from lawsuits, not loans paid off\n" +
        "• Ranges above $50 million are open-ended, values without a range are missing\n" +
        "• Net worth may therefore be much higher"
    override fun rgStand(wann: String) = "As of $wann"
    override fun rgErklaerung(k: String) = when (k) {
        "QUOTE" -> "Share of purchases that have done better than the S&P 500 so far."
        "KAEUFE" -> "Number of stock purchases in the last twelve months."
        "VERMOEGEN" -> "Net worth from the annual report, House only."
        else -> "How many percentage points the purchases of the last twelve months beat the S&P 500 by."
    }
    override val rgText2 = "How it is calculated:\n" +
        "• stock purchases of the last twelve months, from the weekly close after the trade until today\n" +
        "• compared with the S&P 500 over the same period\n" +
        "• weighted by the midpoint of the reported range\n" +
        "• only people with at least three purchases with prices"
    override fun rgPunkte(p: String) = "$p pts"
    override fun rgAusKaeufen(n: Int) = if (n == 1) "from 1 purchase" else "from $n purchases"
    override fun rgTreffer(besser: Int, von: Int) = "$besser of $von beat the S&P 500"
    override fun rgKaufsumme(betrag: String) = "about $betrag in total"
    override val kaQuelleKurz = "Sources: Clerk of the House, U.S. Senate, Yahoo Finance"
    override val kaText2 = "Each week a portfolio of the stocks whose purchase members of Congress reported in the chosen period.\n" +
        "• weighted by the midpoint of the reported amounts, at most 10% per stock from ten stocks on\n" +
        "• if the same person reports a sale, their share shrinks by the midpoint of the sale range\n" +
        "• calculated from the day of the filing, as you could have copied it\n" +
        "• without fees and taxes"
    override fun awKaeufeDanach(n: Int) = "Bought stocks afterwards ($n purchases)"
    override fun awVerkaeufeDanach(n: Int) = "Sold stocks afterwards ($n sales)"
    override val awImPlus = "up"
    override val awBesserAlsMarkt = "beat S&P 500"
    override val awGefallen = "fell"
    override val awSchwaecherAlsMarkt = "lagged S&P 500"
    override val awMitte = "Median"
    override fun awUnusualKurz(anzahl: String, schnitt: String) = "$anzahl instead of $schnitt"
    override fun awVorAnstieg(n: Int) = "Then +10% within two weeks ($n purchases)"
    override fun awVorRueckgang(n: Int) = "Then −10% within two weeks ($n sales)"
    override fun awMitZufall(anteil: String, zufall: String) = "$anteil, random $zufall"
    override fun awHinweise(bewertet: Int, werte: Int) = "How it is calculated:\n" +
        "• $bewertet purchases and sales in $werte stocks and funds with prices\n" +
        "• from the trade date, prices without dividends\n" +
        "• options, bonds and exchanges do not count\n" +
        "• “random” means: the same stocks on arbitrary days\n" +
        "• a cluster before price moves is no proof of insider knowledge"
    override fun ifFilterZahl(n: Int) = "Filter ($n)"
    override fun ifTreffer(n: Int) = if (n == 1) "1 filing" else "$n filings"
    override val ifKeine = "No filings match these filters"
    override val ifRolle = "Role"
    override val ifGroesse = "Size"
    override val ifMehr = "More"
    override val ifOhnePlan = "Without trading plans"
    override val ifHinweis = "Industry and country come from the app's catalog, which only covers the major indices."
    override fun ifAb(betrag: String) = "from $betrag"
    override fun ifTage(t: Int) = if (t <= 0) "18 months" else "$t days"
    override fun usQuelleKurz(stand: String) = "Source: SEC" + if (stand.isNotBlank()) ", as of $stand" else ""
    override val pmGrossGroesste = "Biggest bets"
    override val pmProfis = "Successful traders"
    override val pmProfisLaedt = "Loading leaderboards and bets"
    override val pmProfisKeine = "No notable bets by successful traders right now"
    override val pmProfisText = "Bets by traders who won a lot on Polymarket with politics, economics or finance.\n" +
        "• only success in these areas counts, not sports betting\n" +
        "• shown are purchases outside sports that are large, back an unlikely outcome or are unusually large for the trader\n" +
        "• hedges on both sides and near-certain outcomes above 85% do not count\n" +
        "• this may point to knowledge or strong conviction, but is no proof"
    override val pmOhneSport = "Without sports"
    override val pmGrossText2 = "The biggest bets on Polymarket in the chosen period.\n" +
        "• from $10,000 in 24 hours, $50,000 in 7 days, $200,000 in 30 days and $500,000 in 60 days\n" +
        "• the topic comes from the event’s tags\n" +
        "• the app does not show who placed the bet"
    override fun pmGrossZeit2(key: String) = when (key) { "TAG" -> "24 hrs"; "WOCHE" -> "7 days"; "MONAT" -> "30 days"; else -> "60 days" }
    override fun pmProfiPlatz(rang: Int, kategorie: String, monat: Boolean) =
        "No. $rang in " + when (kategorie) { "ECONOMICS" -> "economics"; "FINANCE" -> "finance"; else -> "politics" } + if (monat) " this month" else " all-time"
    override fun pmProfiGrund(g: String) = when (g) { "AUSSENSEITER" -> "long shot"; "GROSS" -> "large stake"; else -> "unusually large" }

    // 5.92 Stock finder and radar
    override val afTitel = "Stock finder"
    override val afEinleitung = "Ranks the stocks of a market in seven areas."
    override val afStart = "Rank stocks"
    override val afLaedt = "Loading key figures"
    override val afDetailsLaedt = "Loading more key figures for the top stocks"
    override fun afDetailsMehr(n: Int) = if (n == 1) "Check this stock more closely" else "Check the top $n more closely"
    override val afKeine = "No stock matches these filters."
    override fun afBereich(b: String) = when (b) {
        "BEWERTUNG" -> "Valuation"; "QUALITAET" -> "Quality"; "ANALYSTEN" -> "Analysts"; "TREND" -> "Price trend"
        "INSIDER" -> "Insiders"; "LEERVERKAUF" -> "Short selling"; else -> "Sentiment"
    }
    override fun afEinordnung(e: String) = when (e) {
        "GUENSTIG" -> "cheaply valued"; "FAIR" -> "fairly valued"; "TEUER" -> "expensively valued"; else -> "valuation unclear"
    }
    override fun afGrund(k: String, gut: Boolean, wert: String, vergleich: String?): String {
        val branche = vergleich?.let { " (industry $it)" }.orEmpty()
        return when (k) {
            "KGV" -> "P/E $wert$branche"
            "KGV_ERWARTET" -> "Forward P/E $wert$branche"
            "KBV" -> "Price to book $wert$branche"
            "PEG" -> "PEG $wert, the P/E relative to expected growth"
            "IM_KURS" -> "The price implies $wert earnings growth a year, ${vergleich.orEmpty()} is expected"
            "KUV" -> "Price to sales $wert$branche"
            "EV_EBITDA" -> "EV/EBITDA $wert$branche"
            "RENDITE" -> "Return on equity $wert"
            "WACHSTUM" -> "Expected earnings growth $wert"
            "MARGE" -> "Profit margin $wert"
            "SCHULDEN" -> "Debt $wert of equity"
            "BILANZ" -> if (gut) "Solid balance sheet" else "Weak balance sheet"
            "PIOTROSKI" -> "Piotroski F-Score $wert of 9"
            "ALTMAN" -> if (gut) "Low bankruptcy risk by Altman Z" else "Higher bankruptcy risk by Altman Z"
            "NOTE" -> "Analyst rating $wert (1 = buy, 5 = sell)"
            "KURSZIEL" -> "Analyst price target $wert from the price"
            "BEREINIGT" -> if (gut) "Analysts with a good record lean positive" else "Analysts with a good record lean negative"
            "JAHR" -> "Price over twelve months $wert"
            "LINIE200" -> "$wert ${if (gut) "above" else "below"} the 200-day line"
            "INSIDER" -> if (gut) "Insiders recently bought more than they sold" else "Insiders recently sold more than they bought"
            "LEER" -> "$wert of shares sold short"
            else -> "Reddit: $wert mentions, the day before ${vergleich ?: "none"}"
        }
    }
    override val afGewichte = "Weights"
    override val afGewichteHinweis = "How much an area counts. Without data it counts as 50 points."
    override val afStandard = "Default"
    override fun afSortierung(name: String) = "By $name"
    override val afPunktzahl = "score"
    override val afDividende = "dividend"
    override val afBoersenwert = "market cap"
    override val afAlleBranchen = "All industries"
    override val afAlleRegionen = "All regions"
    override val afAlleLaender = "All countries"
    override val afAlleGroessen = "All sizes"
    override val afJedeDividende = "Any dividend"
    override fun afDividendeAb(p: String) = "Dividend from $p"
    override val afJedeBewertung = "Any valuation"
    override fun afGroesse(g: String) = when (g) {
        "KLEIN" -> "Small (under €2bn)"; "MITTEL" -> "Mid (€2bn to €10bn)"; else -> "Large (over €10bn)"
    }
    override fun afMindest(p: Int) = if (p <= 0) "No minimum score" else "At least $p points"
    override fun afTreffer(n: Int, gesamt: Int) = "$n of $gesamt stocks"
    override fun afDatenlage(p: String) = "Data for $p of the weights"
    override val afZurAktie = "Open stock"
    override val afSoRechnet = "How the stock finder works"
    override val afErklaerung = "Each area gets 0 to 100 points, 50 is unremarkable. The total score is the average by the weights.\n" +
        "• Valuation: P/E, forward P/E, price to book, price to sales and EV/EBITDA compared with the industry, plus PEG and the growth priced in\n" +
        "• Quality: return on equity, expected earnings growth, profit margin, debt and balance sheet (Piotroski, Altman)\n" +
        "• Analysts: average rating from 1 (buy) to 5 (sell), distance to the price target and the rating adjusted by hit rate\n" +
        "• Price trend: twelve months and distance to the 200-day line\n" +
        "• Insiders: buying and selling by management over six months\n" +
        "• Short selling: more shares sold short, fewer points\n" +
        "• Sentiment: mentions on Reddit, US stocks only\n" +
        "Cheap means at least 62 points in valuation, expensive at most 38.\n\n" +
        "Limits\n" +
        "• Without data an area counts as 50 points\n" +
        "• Key figures arrive delayed from Yahoo and can contain errors\n" +
        "• The points rest on the past and on other people’s expectations"
    override val afHinweis = "Not investment advice. Data from Yahoo and app filings, possibly wrong or out of date."
    override fun afNachbilden(n: Int) = if (n == 1) "Replicate this stock as a portfolio" else "Replicate the top $n as a portfolio"
    override fun afStand(zeit: String) = "As of $zeit"
    override val afQuelle = "Sources: Yahoo, SEC, BaFin, European regulators, ApeWisdom"
    override val afWeniger = "Show less"
    override fun afMehrZeigen(n: Int) = "Show $n more"
    override val radTitel = "Radar"
    override val radEinleitung = "Collects signs that often appear before a stock gets wide attention."
    override val radStart = "Start radar"
    override val radNeu = "Reload"
    override val radLaedt = "Querying sources"
    override val radKeine = "No notable signs right now."
    override fun radArt(art: String) = when (art) {
        "ERWAEHNUNGEN" -> "Mentions"; "WIKIPEDIA" -> "Wikipedia"; "INSIDER_GRUPPE" -> "Insider group"; "INSIDER_KAUF" -> "Insider purchase"
        "KONGRESS" -> "Congress"; "REGIERUNG" -> "Government"; "GROSSAKTIONAER" -> "Major holder"; "OPTIONEN" -> "Options"
        "VOLUMEN" -> "Volume"; else -> "Prediction market"
    }
    override fun radSignal(art: String, n: Int, a: String, b: String, text: String) = when (art) {
        "ERWAEHNUNGEN" -> "$a mentions on Reddit in 24 hours, $b before"
        "WIKIPEDIA" -> "Wikipedia article viewed $a more often than in the four weeks before"
        "INSIDER_GRUPPE" -> "$n people from the company bought for $b in total"
        "INSIDER_KAUF" -> "${text.ifBlank { "An executive" }} bought for $a"
        "KONGRESS" -> if (n == 1 && text.isNotBlank()) "$text (Congress) bought for about $b" else "$n members of Congress bought for about $b"
        "REGIERUNG" -> if (n == 1 && text.isNotBlank()) "$text (government) bought for about $b" else "$n government members bought for about $b"
        "GROSSAKTIONAER" -> "${text.ifBlank { "An investor" }} reports ${if (a.isNotBlank()) "$a " else ""}with the intent to exert influence"
        "OPTIONEN" -> "Notable call option purchases for $b"
        "VOLUMEN" -> "Volume $a the average, price $b"
        else -> "Large bet of $a: $text"
    }
    override val radAlle = "All signs"
    override val radBekannt = "already widely watched"
    override fun radArten(n: Int) = if (n == 1) "1 kind of sign" else "$n kinds of signs"
    override val radSoRechnet = "How the radar works"
    override val radErklaerung = "For each stock the signs of the last 90 days count, newer ones weigh more.\n" +
        "• Different signs for the same stock count more together\n" +
        "• The ten most mentioned stocks on Reddit count less because they are already being talked about\n" +
        "• Wikipedia views, major holders and options are only checked for the ten strongest candidates\n" +
        "• Insider filings and purchases by politicians get fresher when you open the insider and politics pages\n\n" +
        "Limits\n" +
        "• Many signs lead nowhere: insiders also buy out of duty, politicians report up to 45 days late, volume also rises with index changes\n" +
        "• The radar says nothing about whether a price will rise"
    override val radHinweis = "Not investment advice. A sign is not a forecast."
    override fun radQuelle(namen: List<String>) = "Sources: " + namen.joinToString(", ")
    override fun radQuelleName(k: String) = when (k) {
        "REDDIT" -> "ApeWisdom"; "INSIDER" -> "SEC, BaFin, regulators"; "POLITIK" -> "Congress, OGE"; "VOLUMEN", "OPTIONEN" -> "Yahoo"
        "PROGNOSE" -> "Polymarket"; "WIKIPEDIA" -> "Wikimedia"; "SEC13D" -> "SEC"; else -> k
    }
    override fun radStand(zeit: String) = "As of $zeit"
    override val radHeute = "today"

    // 5.92 Fruehindikatoren
    override val fiTitel = "Inventories and economic data"
    override val fiInfo = "Data that can show early how supply and demand are doing. Oil inventories, for example, show whether crude is getting scarcer than usual for this time of year.\n\n" +
        "A tap on an entry shows the comparison with previous years.\n\n" +
        "Limits:\n" +
        "• “Priced in?” is an estimate from history, because professionals analyse the same numbers, often seconds after release\n" +
        "• Without an estimate the line shows the move since the release\n" +
        "• Many numbers are revised later\n" +
        "• Data appear some days after the reference date\n" +
        "• A single week is not yet a trend\n\n" +
        "The app shows and explains, it does not recommend anything."
    override fun fiName(schluessel: String) = when (schluessel) {
        "rohoel" -> "US crude inventories"
        "benzin" -> "US gasoline inventories"
        "destillat" -> "US diesel and heating oil"
        "cushing" -> "Crude at Cushing"
        "spr" -> "US Strategic Petroleum Reserve"
        "gas_us" -> "US natural gas storage"
        "gas_eu" -> "EU gas storage"
        "erstantraege" -> "US initial jobless claims"
        "fluggaeste" -> "US air travellers"
        "duerre" -> "US drought"
        else -> schluessel
    }
    private fun fiZahlwort(n: Int) = when (n) { 3 -> "three"; 4 -> "four"; 5 -> "five"; else -> n.toString() }
    private fun fiSchnitt(jahre: Int) = if (jahre == 5) "five-year average" else "average of the ${fiZahlwort(jahre)} previous years"
    override fun fiLagerSatz(lage: Int, abweichung: String, unter: Boolean, jahre: Int) =
        "$abweichung ${if (unter) "below" else "above"} the ${fiSchnitt(jahre)}, " + when (lage) {
            0 -> "tighter than in any of the ${fiZahlwort(jahre)} previous years"
            1 -> "tighter than usual"
            3 -> "more than usual"
            4 -> "more than in any of the ${fiZahlwort(jahre)} previous years"
            else -> "within the usual range"
        }
    override fun fiDuerreSatz(punkte: String, mehr: Boolean, rekord: Boolean, jahre: Int) =
        "$punkte points ${if (mehr) "more" else "less"} area in drought than the ${fiSchnitt(jahre)}" +
            if (rekord) ", more than in any of the ${fiZahlwort(jahre)} previous years" else ""
    override fun fiAntraegeSatz(anstieg: String, ampel: Int) = "Four-week average $anstieg above its low of the past year" + when (ampel) {
        2 -> ", such a rise often preceded a recession in the past"
        1 -> ", the labour market is cooling"
        else -> ""
    }
    override fun fiFlugSatz(veraenderung: String, mehr: Boolean) = "$veraenderung ${if (mehr) "more" else "fewer"} travellers than a year ago (last 28 days)"
    override fun fiErklaerung(schluessel: String) = when (schluessel) {
        "rohoel" -> "Crude oil in US storage excluding the strategic reserve, every Wednesday from the EIA. Tight inventories point to tight supply or strong demand and tend to support the oil price, full ones tend to weigh on it."
        "cushing" -> "Storage hub in Oklahoma where the US oil contract WTI is delivered. Very empty tanks can make the next WTI contract more expensive for a short time, very full ones can push it down as in April 2020."
        "benzin" -> "US gasoline stocks. A lot of gasoline points to weak demand or large supply, little gasoline supports the price. People drive more in summer and stocks usually fall, so the app compares with the same time of year."
        "destillat" -> "Diesel and heating oil, important for trucks, farming and winter heating. Tight stocks often make diesel rise more than crude."
        "gas_us" -> "Working gas in underground storage in the US, every Thursday from the EIA. Gas is injected in summer and withdrawn in winter. Full storage before winter dampens the price, empty storage makes it vulnerable to cold."
        "gas_eu" -> "Fill level of gas storage in the EU, daily from GIE AGSI+. A low level before winter makes the European gas price (TTF) vulnerable. It often moves differently from the US price Henry Hub."
        "erstantraege" -> "How many people in the US file for unemployment benefits for the first time in a week, seasonally adjusted, every Thursday from the US Department of Labor. If the four-week average rises clearly above its low, the labour market is weakening. From 10% the rise counts as clear, from 20% as a warning sign."
        "fluggaeste" -> "Travellers at US airport security checkpoints, daily from the TSA. A hint on travel and consumer spending, important for airlines and travel companies. Holidays such as Easter or Thanksgiving fall differently each year and distort the comparison for a short time."
        "duerre" -> "Share of the area of the US excluding Alaska and Hawaii in at least moderate drought, weekly from the U.S. Drought Monitor (National Drought Mitigation Center, USDA and NOAA). Drought in growing regions can reduce harvests of corn, wheat and soybeans. The number covers the whole country, not only farmland."
        else -> ""
    }
    override fun fiQuelle(schluessel: String) = when (schluessel) {
        "gas_eu" -> "Source: GIE AGSI+"
        "erstantraege" -> "Source: US Department of Labor via FRED"
        "fluggaeste" -> "Source: TSA"
        "duerre" -> "Source: U.S. Drought Monitor"
        else -> "Source: EIA"
    }
    override val fiQuellen = "Sources: EIA, US Department of Labor (FRED), TSA, U.S. Drought Monitor, GIE AGSI+"
    override fun fiStand(datum: String) = "As of $datum"
    override fun fiWoche(aenderung: String) = "Week $aenderung"
    override fun fiBereich(von: String, bis: String, jahre: Int) = "${fiZahlwort(jahre).replaceFirstChar { it.uppercase() }} previous years at the same time: $von to $bis"
    override fun fiMioBarrel(wert: String) = "$wert m barrels"
    override fun fiMrdKubikfuss(wert: String) = "$wert bcf"
    override fun fiMioTag(wert: String) = "$wert m a day"
    override val fiPunkte = "points"
    override fun fiSpr(wert: String, woche: String?) =
        "Strategic Petroleum Reserve: $wert" + (woche?.let { ", week $it" } ?: "") + ". It follows political decisions and therefore does not count towards the light."
    override val fiOhneZugang = "Visible with free access to GIE AGSI+"
    override val zgAgsiName = "GIE AGSI+ (EU gas storage)"
    override val zgAgsiZweck = "Fill level of EU gas storage for the “Inventories and economic data” section in Markets and on the natural gas page."
    override val zgAgsiPunkte = listOf(
        "Free after signing up with an email address",
        "The key is only sent to agsi.gie.eu",
        "Condition: name GIE as the source, the app does that",
    )
    override val zgAgsiHolen = "Get a key from GIE AGSI+"
    override val fiAntraegeTief = "Four-week average near its low of the past year"
    override val fiBrancheTitel = "Data for the industry"

    // 5.92 Vermoegen Regierung
    override fun ogeVmTitel(art: String, jahr: Int) = "Net worth per " + when (art) {
        "A" -> "entry report"; "E" -> "termination report"; else -> "annual report"
    } + if (jahr > 0) " $jahr" else ""
    override val ogeVmHinweis = "Lower bound from the value ranges in the filing with the U.S. Office of Government Ethics.\n" +
        "• The own home and government pensions are not included, loans on that home are therefore not deducted"
    override fun ogeVmPdf(datum: String) = "Filing of $datum"
    // 5.92 Aktienfinder Bilanz
    override val afNurSolide = "Solid balance sheet only"
    override fun afBilanzStand(n: Int, gesamt: Int) = "Balance sheet data $n of $gesamt"
    override val afNeuOrdnen = "Re-sort"
    override val afBilanzWeiter = "Continue loading"
    override val afBilanzFolgt = "Balance sheet data still loading"
    override fun afBilanzZahlen(f: String?, zone: String?) =
        listOfNotNull(f?.let { "Piotroski F-Score $it of 9" }, zone?.let { "Altman Z: $it" }).joinToString(" · ")
    // 5.92 Ersatzquellen
    override fun ersSenatIntro(stand: String) = "Trades by US senators under the STOCK Act, reported up to 45 days later, with the amount as a range. The app brings along three years and fetches newer filings from the open Congress Trading Monitor dataset" + (if (stand.isBlank()) "." else ". Latest filing from $stand.")
    override val ersSenatQuelle = "Newer filings: Congress Trading Monitor (kadoa-org, MIT licence), read from the same Senate filings."
    override val ersSenatGlocke = "New Senate filings arrive through an open dataset, usually a few days after they are filed."
    override fun ersBlName(schluessel: String) = when (schluessel) {
        "cape" -> "Shiller P/E (CAPE)"
        "margin" -> "Stocks bought on credit"
        else -> null
    }
    override fun ersBlLage(schluessel: String, ampel: Int) = when (schluessel) {
        "cape" -> listOf("Valuation within the usual range", "Above the long-term average", "As high as rarely since 1881")
        "margin" -> listOf("Credit grows normally", "Credit grows strongly", "Credit grows as fast as before earlier peaks")
        else -> null
    }?.getOrElse(ampel) { "" }
    override fun ersBlErklaerung(schluessel: String, zusatz: String?, stand: String?) = when (schluessel) {
        "cape" -> "Price of the S&P 500 divided by the inflation-adjusted average earnings of the past ten years, calculated by Robert Shiller. Single good or bad years carry less weight this way." +
            (zusatz?.let { " Higher than in $it of all months since 1881" + (stand?.let { s -> ", as of $s." } ?: ".") } ?: "") +
            " Long-term average about 17, at the end of 1999 a little over 44. Yellow from 22, red from 30."
        "margin" -> "Loans from US securities brokers to their customers, mostly for buying stocks on credit, from the financial accounts of the Fed. Shown is the growth against the previous year" +
            (zusatz?.let { ", latest $it" + (stand?.let { s -> " in $s" } ?: "") } ?: "") +
            ". This series measures a little more broadly than the FINRA margin statistics. Before the peaks of 2000 and 2021 the loans grew by more than half. Yellow from 20 %, red from 40 %."
        else -> null
    }
    override val ersBlQuelle = "Also: Robert Shiller (CAPE), Fed financial accounts via FRED"
    override val ersTitel = "Freight, supply chains, chips"
    override val ersInfo = "Monthly data from open sources. They show whether goods are getting scarce and whether industry and transport are busier or quieter than a year ago.\n\n" +
        "• Red: clearly weaker than a year ago or strong pressure in supply chains\n" +
        "• Yellow: a little weaker or raised pressure\n" +
        "• Green: unremarkable\n\n" +
        "The figures appear a few weeks after the month ends."
    override fun ersName(schluessel: String) = when (schluessel) {
        "lieferketten" -> "Supply chain pressure"
        "fracht" -> "US freight"
        "bahn" -> "US rail carloads"
        "halbleiter" -> "US chip production"
        else -> schluessel
    }
    override fun ersSatz(schluessel: String, ampel: Int) = when (schluessel) {
        "lieferketten" -> listOf("No particular pressure", "Raised pressure", "Strong pressure, goods are getting scarce")
        "fracht" -> listOf("At least as much freight as a year ago", "A little less freight than a year ago", "Clearly less freight than a year ago")
        "bahn" -> listOf("At least as many as a year ago", "A little fewer than a year ago", "Clearly fewer than a year ago")
        "halbleiter" -> listOf("Output above last year", "Output a little below last year", "Output clearly below last year")
        else -> emptyList()
    }.getOrElse(ampel) { "" }
    override fun ersErklaerung(schluessel: String) = when (schluessel) {
        "lieferketten" -> "Global Supply Chain Pressure Index of the New York Fed. It combines shipping and air freight rates with surveys on delivery times and backlogs in seven large economies. 0 is the long-term average, 1 is one standard deviation above it, in 2021 it was a little over 4. Yellow from 0.5, red from 1.5."
        "fracht" -> "Freight index of the US Department of Transportation (BTS) for trucks, rail, waterways, pipelines and air freight, seasonally adjusted. It stands in for freight indices whose data is not free to use. Below the same month last year yellow, 3 % or more below red."
        "bahn" -> "Loaded rail freight cars in the US per month, seasonally adjusted, according to the BTS. The weekly figures of the rail association are only published as PDF. Below the same month last year yellow, 3 % or more below red."
        "halbleiter" -> "Fed production index for semiconductors and electronic components in the US. It stands in for the worldwide sales figures of the SIA and WSTS, which are not free to use. Below the same month last year yellow, 3 % or more below red."
        else -> ""
    }
    override fun ersStand(monat: String) = "As of $monat"
    override fun ersVorjahr(wert: String) = "$wert year on year"
    override val ersQuellen = "Sources: New York Fed, BTS and Fed via FRED"
    override fun ersQuelle(schluessel: String, jahr: Int) =
        if (schluessel == "lieferketten") "Source: " + "© $jahr Federal Reserve Bank of New York. Content from the New York Fed subject to the Terms of Use at newyorkfed.org." else "Source: FRED, St. Louis Fed"
    override val ersHnTitel = "Hacker News"
    override fun ersHnZeile(woche: Int, schnitt: String) = (if (woche == 1) "1 story" else "$woche stories") + " in 7 days · before $schnitt per week"
    override fun ersMastodonTitel(tag: String) = "Mastodon · #$tag"
    override fun ersMastodonZeile(beitraege: Int) = (if (beitraege == 1) "1 post" else "$beitraege posts") + " in 7 days on mastodon.social"
    override val ersNetzHinweis = "The company name is counted. Words with the same spelling count too."
    override fun ersMrdDollar(wert: String) = "$" + wert + " bn"
    override fun ersQuartal(q: Int, jahr: Int) = "Q$q $jahr"
    // 5.92 Eingepreist
    override val epTitel = "Priced in?"
    override fun epTendenz(richtung: Int, was: String?) = when (richtung) {
        1 -> "Leaning: rather long"
        -1 -> "Leaning: rather short"
        else -> "Leaning: no clear direction"
    } + (if (richtung != 0 && was != null) " on $was" else "")
    override val epWasAnleihen = "US Treasuries"
    override val epWasZyklisch = "cyclical versus defensive stocks"
    override val epWasAirlines = "airline stocks"
    override fun epWasKorn(symbol: String) = when (symbol) {
        "ZW=F" -> "wheat"
        "ZS=F" -> "soybeans"
        "TTF=F" -> "gas"
        else -> "corn"
    }
    override val epInfo = "“Priced in?” asks whether the market has already taken a signal into account. The leaning says whether you would rather go long (betting on rising prices) or short (betting on falling prices).\n\n" +
        "• Green: already priced in, no clear direction\n" +
        "• Yellow: the market sees it clearly differently from the signal. The leaning shows where the price would have to move for both to fit again\n" +
        "• Red: like yellow, but the gap is as large as rarely before\n" +
        "• Grey: there was no reliable relationship in the past, so no leaning\n\n" +
        "Oil example: when inventories are tight, oil for early delivery usually costs more than later. If that premium is larger than inventories explain, oil is rather too expensive (rather short). If it is smaller, the tightness is not yet in the price (rather long).\n\n" +
        "“Uncertain” means the relationship was weak in the past."
    override fun epRisiken(mass: String) = "Limits:\n" + when (mass) {
        "KURVE" -> "• The futures curve also depends on storage costs and interest rates\n" +
            "• Expectations act before inventories do: OPEC decisions, wars, sanctions or hurricanes\n" +
            "• Shortly before expiry the nearest contract swings more\n" +
            "• The app removes the season, unusual weather remains\n" +
            "• The EIA futures series end in April 2024, so the estimate ends there. Today's curve comes from Yahoo\n"
        "PREIS" -> "• The price depends on many things: harvests and weather in other countries, exchange rates, politics\n" +
            "• Wars, sanctions or supply stops act at once, inventories and drought only later\n" +
            "• Comparing with the one-year average is rough\n"
        "AIRLINES" -> "• Airline stocks also depend on jet fuel, interest rates, strikes and takeovers\n" +
            "• The passenger numbers cover the US only\n" +
            "• Estimated only from mid-2022, because the years before show just the pandemic and recovery. The period is short\n"
        "BRANCHE" -> "• Sector stocks depend on many things: rates, profits of a few heavyweights, takeovers\n" +
            "• Monthly data arrives one to three months late, the market may already be further along\n" +
            "• Freight and chip production cover the US only, the supply chain index is global\n"
        "SEIT" -> "• Other news moves the price too\n" +
            "• There are often few earlier cases for the same stock, the average varies\n" +
            "• Without its own cases the app uses a rough guide\n"
        else -> "• Rate expectations depend mainly on inflation and the central bank\n" +
            "• Initial claims vary from week to week, holidays and storms distort them\n" +
            "• The app caps exceptional years such as the pandemic, they still shape the estimate\n"
    } + "• A relationship from the past need not hold in future\n\n" +
        "The leaning is derived from past relationships and is not investment advice."
    override fun epLagerSatz(urteil: Int, lage: Int, gas: Boolean, kurve: Boolean): String {
        val lager = if (gas) "storage" else "inventories"
        val sind = if (gas) "is" else "are"
        val zeigt = if (gas) "shows" else "show"
        return when (urteil) {
            0 -> "Fits: the $lager level is already in the price."
            1 -> if (lage == 1) "${lager.replaceFirstChar { it.uppercase() }} $sind full, yet the price expects tightness. Rather too expensive."
                else "The price expects more tightness than the $lager $zeigt. Rather too expensive."
            else -> if (lage == -1) "${lager.replaceFirstChar { it.uppercase() }} $sind tight, the price does not show it yet. Rather too cheap."
                else "The price expects more surplus than the $lager $zeigt. Rather too cheap."
        }
    }
    override fun epDuerreSatz(urteil: Int, viel: Boolean) = when (urteil) {
        0 -> if (viel) "The grain price fits the widespread drought: priced in." else "Grain price and drought fit together."
        1 -> "The grain price is higher than the drought explains."
        else -> if (viel) "The drought is larger than the grain price shows: not fully priced in yet." else "The grain price is lower than the drought suggests."
    }
    override fun epFlugSatz(urteil: Int) = when (urteil) {
        0 -> "Airline stocks move in line with passenger numbers: priced in."
        1 -> "Airline stocks do better than passenger numbers suggest."
        else -> "Airline stocks do worse than passenger numbers suggest."
    }
    override fun epAntraegeSatz(urteil: Int) = when (urteil) {
        0 -> "Rate expectations fit the initial claims: priced in."
        1 -> "Rate expectations price in less slowdown than the initial claims show."
        else -> "Rate expectations price in more slowdown than the initial claims show."
    }
    override fun epZyklischSatz(urteil: Int) = when (urteil) {
        0 -> "Cyclical and defensive stocks move in line with initial claims."
        1 -> "Cyclical stocks do better than initial claims suggest."
        else -> "Cyclical stocks do worse than initial claims suggest."
    }
    override val epKeine = "No reliable assessment possible, the relationship used to be too weak."
    override val epUnsicher = "Uncertain, the relationship used to be weak."
    override fun epKurve(spanne: String, teurer: Boolean) = "Nearest contract $spanne a year ${if (teurer) "dearer" else "cheaper"} than the fourth"
    override fun epPreis(abstand: String, ueber: Boolean, name: String) = "$name $abstand ${if (ueber) "above" else "below"} its one-year average"
    override fun epAirlines(abstand: String, besser: Boolean) = "Airline stocks $abstand ${if (besser) "better" else "worse"} than the S&P 500 over a year"
    override fun epZins(abstand: String) = "Two-year US yield $abstand points below its one-year high"
    override fun epZyklisch(abstand: String, besser: Boolean) = "Cyclical consumer stocks $abstand ${if (besser) "better" else "worse"} than defensive ones over six months"
    override fun epHeute(wert: String) = "Today: $wert"
    override fun epUeblich(wert: String) = "Usual at this level: $wert"
    override fun epSelten(anteil: String, hoeher: Boolean) = "The market was this far ${if (hoeher) "above" else "below"} the usual level in only $anteil of weeks."
    override fun epStaerke(staerke: Int, anteil: String) = "Past relationship: " + when (staerke) {
        3 -> "clear"
        2 -> "moderate"
        1 -> "weak"
        else -> "too weak"
    } + ", the signal explained $anteil of the variation."
    override fun epZeitraum(wochen: Int, von: String, bis: String) = "Estimated from $wochen weeks, $von to $bis"
    override val epKurveTitel = "Futures curve today"
    override val epZusatzTitel = "Cyclical versus defensive stocks"
    override fun epKorn(symbol: String) = when (symbol) {
        "ZW=F" -> "Wheat price"
        "ZS=F" -> "Soybean price"
        "TTF=F" -> "TTF gas price"
        else -> "Corn price"
    }
    override fun epSeit(art: String, mehr: String, index: String?) = "Since the " + when (art) {
        "INSIDER" -> "insider purchase"
        "HOCH" -> "upgrade"
        "AB" -> "downgrade"
        "MELDUNG" -> "filing"
        else -> "signal"
    } + " $mehr" + (index?.let { " against the $it" } ?: "")
    override fun epLauf(lauf: Int) = when (lauf) {
        0 -> "Most of it is probably priced in."
        1 -> "Partly priced in."
        2 -> "Little priced in so far."
        3 -> "The price has since moved against the signal."
        else -> "Such signals used to bring no lead here on average."
    }
    override fun epFrueher(schnitt: String, n: Int) = "Earlier signals of this kind brought $schnitt against the index on average within six months for this stock ($n cases)."
    override fun epRichtwert(wert: String) = "There are too few earlier cases for this stock. A lead of $wert serves as a rough guide."
    override fun epHandelstage(n: Int) = "$n trading days since the signal"
    override val epSeitInfo = "What counts here is how much better or worse the stock has done since the signal than its market (home index).\n\n" +
        "• Green: the usual lead has been reached, the signal is priced in, no clear direction any more\n" +
        "• Yellow: only part has played out. Leaning in the direction of the signal: rather long after purchases and upgrades, rather short after downgrades\n" +
        "• Red: the stock moved against the signal, no clear direction\n" +
        "• Grey: such signals brought no lead for this stock in the past"
    override val epKaeufeTitel = "Priced in? Since the purchases"
    override val epKaeufeHinweis = "Measured from the filing date, when the purchases became public, against the S&P 500. From a lead of about 10 % most of it counts as priced in."
    override fun epKaufZeile(datum: String, mehr: String, index: String) = "Filed $datum · $mehr against the $index"
    override val epAnalystenTitel = "Priced in? Latest upgrade or downgrade"
    override val epInsiderTitel = "Priced in? Latest insider purchase"
    // 5.92 Finder-Test
    override val afRueckblickTitel = "Past results of the scoring"
    override fun afRueckblickText(von: String, bis: String, werte: Int, top20: String, msci: String, treffer: String) =
        "Look back $von to $bis: at every month end the app scored the S&P 500 using only data known then. " +
            "The 20 stocks with the most points returned $top20 on average over the next twelve months, the MSCI World $msci. They were ahead on $treffer of the dates."
    override fun afRueckblickGrenzen(bilanzAb: String?, ganz: Boolean, ic: Double, icSe: Double, bewertung: Pair<Double, Double>?): String {
        val staerke = when {
            ic - 2 * icSe <= 0.0 -> "not reliably distinguishable from chance"
            ic < 0.05 -> "weak"
            else -> "moderate"
        }
        val geprueft = when {
            bilanzAb == null -> "• Tested: price trend and analysts."
            ganz -> "• Tested: price trend, analysts, valuation, quality."
            else -> "• Tested: price trend and analysts, valuation and quality from $bilanzAb."
        }
        val bew = bewertung?.takeIf { (b, se) -> b - 2 * se <= 0.0 }?.let { (b, _) ->
            "\n• A cheap valuation did not rank later returns in this period (rank correlation ${String.format(Locale.US, "%.2f", b)})."
        } ?: ""
        return "Limits\n" + geprueft + bew + "\n" +
            "• Insiders, short selling and sentiment are missing earlier and did not count.\n" +
            "• The points rank later returns $staerke (rank correlation ${String.format(Locale.US, "%.2f", ic)}).\n" +
            "• Missing firms flatter the result. Prices without dividends."
    }
    // 5.92 Weitere Quellen
    override fun wqName(k: String) = when (k) {
        "wci" -> "Container freight"
        "aar" -> "US railroads, weekly"
        else -> "Worldwide semiconductor sales"
    }
    override fun wqSatz(k: String, ampel: Int, steigt: Boolean) = when (k) {
        "wci" -> listOf(
            "Freight rates barely changed",
            if (steigt) "Freight rates rising sharply" else "Freight rates falling sharply",
            if (steigt) "Freight rates are jumping" else "Freight rates are collapsing",
        )
        "aar" -> listOf("At least as much traffic as a year ago", "Slightly less traffic than a year ago", "Clearly less traffic than a year ago")
        else -> listOf("Sales above the same month last year", "Sales slightly below the same month last year", "Sales clearly below the same month last year")
    }.getOrElse(ampel) { "" }
    override fun wqErklaerung(k: String) = when (k) {
        "wci" -> "Drewry World Container Index: price of a 40 ft container on eight major routes from Asia, Europe and the US, updated every Thursday. Sharp jumps point to bottlenecks at sea, sharp drops often to weak demand. Yellow from 5 % week on week, red from 15 %."
        "aar" -> "Weekly figures of the Association of American Railroads (AAR): loaded carloads plus containers and trailers, compared with the same week a year earlier. They come out every Wednesday and show earlier than the monthly data how much freight is moving. Yellow below last year, red from 3 % below."
        else -> "Worldwide monthly sales of chip makers according to WSTS, the industry's statistics body, compared with the same month a year earlier. The figures come out about two months later. Yellow below last year, red from 3 % below."
    }
    override fun wqQuelle(k: String) = when (k) {
        "wci" -> "Source: Drewry"
        "aar" -> "Source: Association of American Railroads"
        else -> "Source: WSTS"
    }
    override fun wqWoche(wert: String) = "Week on week $wert"
    override fun wqBahnTeile(wagen: String?, container: String?) =
        listOfNotNull(wagen?.let { "Carloads $it" }, container?.let { "Containers and trailers $it" }).joinToString(" · ")
    override fun wqWocheBis(tag: String) = "Week ending $tag"
    override fun wqStandTag(tag: String) = "As of $tag"
    override fun wqEinheiten(n: String) = "$n carloads and containers"
    override val wqFbxLink = "Freightos Baltic Index (FBX) in the browser"
    override val wqBalticLink = "Baltic Dry Index in the browser"
    override val wqLmeLink = "LME metal stocks in the browser"
    override val wqComexLink = "COMEX metal stocks in the browser"
    override val wqFinraLink = "FINRA margin statistics in the browser"
    override fun wqBrowserText(land: String) = (if (land == "GB") "The regulator's filings" else "This exchange's filings") + " can be viewed in the browser."
    override fun wqBrowserLink(land: String) = "View filings at " + when (land) {
        "IL" -> "MAYA (Tel Aviv Stock Exchange)"
        "ID" -> "the Indonesia Stock Exchange (IDX)"
        "BH" -> "Bahrain Bourse"
        "JM" -> "the Jamaica Stock Exchange"
        "TT" -> "the Trinidad and Tobago Stock Exchange"
        "PK" -> "the Pakistan Stock Exchange"
        "HK" -> "HKEX (Disclosure of Interests)"
        "GB" -> "the FCA (National Storage Mechanism)"
        "DK", "FI", "IS", "EE", "LV", "LT" -> "Nasdaq Nordic and Baltic"
        else -> land
    }
    override fun wqKwQuelle(stand: String) = "Source: Boursa Kuwait (RSS service and disclosure forms), plus lists you imported" + if (stand.isNotBlank()) ", as of $stand" else ""
    override val wqRichterTitel = "US federal judges"
    override val wqRichterIntro = "Federal judges report their investments and trades once a year, amounts only as ranges. Shown are the trades from the reports CourtListener processed most recently."
    override val wqRichterOhneZugang = "Visible with a free CourtListener token under API access"
    override val wqRichterLeer = "The most recently processed reports contain no trades."
    override val wqRichterLaedt = "Loading reports"
    override val wqRichterName = "Name follows on the next load"
    override fun wqRichterZeile(jahr: Int, tag: String) = listOfNotNull(if (jahr > 0) "Report $jahr" else null, tag.ifBlank { null }).joinToString(" · ")
    override fun wqSpanne(von: String, bis: String?) = if (bis == null) "over $von" else "$von to $bis"
    override val wqRichterQuelle = "Source: CourtListener (Free Law Project)"
    override val wqJefsLink = "All reports at JEFS in the browser"
    override val wqParlamenteTitel = "Other parliaments"
    override val wqParlamenteText = "These registers open in the browser."
    override val wqEuLink = "Members of the European Parliament: declarations of financial interests"
    override val wqAusHausLink = "Australia: House register of interests"
    override val wqAusSenatLink = "Australia: senators' interests"
    override val wqBskyTitel = "Bluesky"
    override fun wqBskyZeile(woche: Int, vorwoche: Int, gekappt: Boolean) =
        (if (gekappt) "More than $woche" else "$woche") + (if (woche == 1 && !gekappt) " post" else " posts") + " in 7 days · previous week $vorwoche"
    override val wqXLink = "View on X"
    override val wqStocktwitsLink = "View on Stocktwits"
    override val wqClName = "CourtListener (US federal judges)"
    override val wqClZweck = "Stock trades of US federal judges from their annual financial disclosures, under Copytrading in Politics."
    override val wqClPunkte = listOf(
        "Free account with the Free Law Project, the token is then in your profile",
        "For personal, non-commercial use only",
        "Without membership 125 requests a day, the app uses at most 100",
        "The token only goes to courtlistener.com",
    )
    override val wqClFeld = "Token"
    override val wqClHolen = "Get a token from CourtListener"
    override val wqBskyZweck = "How often a company is mentioned on Bluesky, under Trends on a stock's page."
    override val wqBskyPunkte = listOf(
        "Bluesky only allows search when signed in",
        "Create an app password for this, never enter your main password",
        "The app only accepts passwords of the form xxxx-xxxx-xxxx-xxxx",
        "It only reads and never posts in your name",
        "Handle and app password only go to Bluesky",
    )
    override val wqBskyHandle = "Handle, e.g. name.bsky.social"
    override val wqBskyPasswort = "App password"
    override val wqBskyUngueltig = "Only an app password of the form xxxx-xxxx-xxxx-xxxx"
    override val wqBskyHandleUngueltig = "Handle without @, e.g. name.bsky.social"
    override val wqBskyHolen = "Create an app password at Bluesky"
    override fun wqMeldung(m: String) = when (m) {
        "abgelehnt" -> "The service rejected the access. Please check your details."
        else -> "The limit has been reached. The app will ask again once the quota allows it."
    }
    override fun wqVerbrauch(verbraucht: String, erlaubt: String) = "Requests in the last 24 hours: $verbraucht of $erlaubt"
    // 5.92 Tendenz-Rueckblick
    override fun trFrueher(rot: Boolean, treffer: String, grund: String, wochen: Int, mittel: String, faelle: Int) =
        "In the past${if (rot) " at red" else ""}: right in $treffer (otherwise $grund), best ${trNach(wochen)}, $mittel on average, $faelle cases"
    override val trOhneVorteil = "No edge in the past: following the leaning was not right more often than usual."
    override fun trNichtGeprueft(zuKurz: Boolean) =
        if (zuKurz) "Cannot be checked yet, the data does not go back five years." else "Not checked against the past."
    override fun trNach(wochen: Int) = when (wochen) {
        1 -> "after one week"
        4 -> "after one month"
        13 -> "after 3 months"
        26 -> "after 6 months"
        52 -> "after one year"
        else -> "after $wochen weeks"
    }
    override val trDetailTitel = "Look back: how the leaning did in the past"
    override fun trDetailZeile(wochen: Int, treffer: String, grund: String, mittel: String, t: String, faelle: Int, belegt: Boolean) =
        trNach(wochen).replaceFirstChar { it.uppercase() } + ": $treffer right (otherwise $grund), $mittel, t $t, $faelle cases" + if (belegt) ", proven" else ""
    override fun trGeprueft(von: String, bis: String) = "Checked week by week from $von to $bis, each time only with the data available then."
    override fun trPunkte(wert: String) = "$wert points"
    override fun trIdeeTitel(call: Boolean, name: String) = "Practice idea: ${if (call) "call" else "put"} on $name"
    override fun trIdeeDaten(basis: String, verfall: String, hebel: String) = "Strike $basis · expiry $verfall · leverage about $hebel"
    override fun trIdeeRisiko(zeitwert: String) =
        "You can lose at most the purchase price. If the price does not move, the warrant loses about $zeitwert of time value per week."
    override val trIdeeHinweis = "Practice suggestion based on past relationships, not investment advice. Tap to open the warrant in the practice depot."
    override fun trBasisName(symbol: String) = when (symbol) {
        "CL=F" -> "WTI crude"
        "RB=F" -> "gasoline (RBOB)"
        "HO=F" -> "heating oil and diesel"
        "NG=F" -> "US natural gas"
        "ZC=F" -> "corn"
        "ZW=F" -> "wheat"
        "ZS=F" -> "soybeans"
        "SHY" -> "short US Treasuries (SHY)"
        "XLY" -> "consumer discretionary (XLY)"
        "XLP" -> "consumer staples (XLP)"
        "JETS" -> "airline stocks (JETS)"
        else -> symbol
    }
    override val trInfo = "Look back: the app recalculated the leaning for every week in the past with the data of that time and measured whether the price moved that way afterwards.\n\n" +
        "• “Right in 61 % (otherwise 50 %)” means: in 61 of 100 cases the price moved in the direction of the leaning, on random days in 50\n" +
        "• A leaning counts as proven only with at least 20 cases that do not overlap in time and with an edge that can hardly be chance\n" +
        "• “No edge in the past” means: following the leaning did not do better. The direction is then shown in grey\n\n" +
        "Markets often stay out of line with the data for a long time.\n\n" +
        "The app shows a practice suggestion with a warrant only for a proven leaning, with a term about one and a half times the period in which it was right."

    // 5.92 Investorenbriefe
    override val ibTitel = "What investors write"
    override val ibKopf = "The assessment shows the senders' own interest as far as it is known."
    override fun ibZusammenfassung(absender: Int, positiv: Int, negativ: Int, neutral: Int) =
        if (absender == 1) "One sender recently wrote about the stock, " + ibRichtung(if (positiv > 0) 1 else if (negativ > 0) -1 else 0) + " (direction roughly estimated)."
        else "$absender senders recently wrote about the stock: $positiv rather positive, $negativ rather negative, $neutral neutral (direction roughly estimated)."
    override fun ibEigeneSache(n: Int) = if (n == 1) "One of them writes about its own position." else "$n of them write about their own position."
    override fun ibRichtung(r: Int) = when {
        r > 0 -> "rather positive"
        r < 0 -> "rather negative"
        else -> "neutral"
    }
    override fun ibArt(art: String) = when (art) {
        "HEDGEFONDS" -> "Hedge fund"
        "FONDSGESELLSCHAFT" -> "Fund company"
        "VERMOEGENSVERWALTER" -> "Asset manager"
        "HOLDING" -> "Holding company"
        else -> art
    }
    override val ibSecBericht = "Fund report at the SEC"
    override fun ibBeleg(beleg: String, stand: String) = when (beleg) {
        "FIRMA_SELBST" -> "Letter from the company to its own shareholders"
        "F13_HAELT" -> "Holds the stock (per 13F, as of $stand)"
        "F13_NEU" -> "Newly bought per 13F (as of $stand)"
        "F13_ERHOEHT" -> "Added per 13F (as of $stand)"
        "F13_VERRINGERT" -> "Reduced per 13F (as of $stand)"
        "F13_VERKAUFT" -> "Sold out per 13F (as of $stand)"
        "F13_KEINE" -> "No 13F holding known (as of $stand)"
        "SCHEDULE13" -> "Major shareholder per SEC filing (13D/13G)"
        "BRIEF_KAUF" -> "Bought or added, by its own account"
        "BRIEF_HAELT" -> "Invested, by its own account"
        "BRIEF_VERRINGERT" -> "Reduced, by its own account"
        "BRIEF_VERKAUF" -> "Sold, by its own account"
        "BRIEF_SHORT" -> "Short, by its own account"
        "BESTANDSLISTE" -> "Listed in a holdings table in the same document"
        else -> "No position known"
    }
    override fun ibPasst(passt: String) = when (passt) {
        "EIGENE_POSITIV" -> "Writes positively about its own position"
        "EIGENE_KRITISCH" -> "Critical despite its own position"
        "SHORT_NEGATIV" -> "Writes negatively about its own short position"
        "SHORT_POSITIV" -> "Positive despite its own short position"
        "NACH_VERKAUF_KRITISCH" -> "Critical after selling"
        "POSITIV_TROTZ_VERKAUF" -> "Positive although it sold"
        else -> "The company writes about itself"
    }
    override val ibWerbung = "The letter advertises the sender's own offer"
    override fun ibQuelle(wo: String) = "Source: $wo"
    override val ibLeer = "The stock does not currently appear in the letters the app reads."
    override val ibSuchen = "Search more letters on the stock (hfbestideas.com)"
    override val ibSecHinweis = "With a contact address under Access, the app also searches fund reports at the SEC."

    // 5.92 Textrunde
    override val indFussTitel = "How to read them and how reliable they are"

    // 5.93 depotverlauf
    override val pvYtd = "YTD"
    override fun ptGapBeats(v: String) = "$v pp ahead of the index"
    override fun ptGapTrails(v: String) = "$v pp behind the index"
    // 5.93 briefe
    override val ibInfoKurz = "Excerpts from letters by fund managers to their investors, with direction and assessment.\n\n• Whoever holds a stock gains when others buy it\n• Managers like to praise what they own\n• The direction is a rough estimate from words\n\nNot investment advice."
    override val ibMehrBrief = "More from the letter"

    // 5.93 nachrichten2
    override val rbMitteilung = "Review notification"
    override val rbMitteilungHinweis = "Off by default. The reviews are still created and appear under News."
    override val notifWerkHinweis = "On by default: orders, savings plans, price alerts, maturities, dividends, interest and important headlines. Off by default: the bells in Copytrading and the notification for the daily review. You can switch each on separately."
    override val copyBellsWerk = "No bells are set by default. You only get notifications for people whose bell you switch on yourself."
    // 5.93 strategielabor
    override val slEigen = "Custom"
    override val slEigenHinweis = "Pick a start and an end. Only this period is simulated, but the rules' indicators start running earlier."
    override fun slLotterieErgebnis(pruefBesser: Boolean, echt: Boolean, anzahl: Int, prozent: String) = when {
        pruefBesser && echt -> "The best of $anzahl variants also beats buy and hold in the test period. At $prozent, there is good reason to think it is not luck."
        pruefBesser -> "The best of $anzahl variants beats buy and hold in the test period. At only $prozent, it can be luck as well."
        echt -> "The best of $anzahl variants stands out from luck at $prozent, but does not beat buy and hold in the test period."
        else -> "The best of $anzahl variants loses to buy and hold in the test period. At $prozent, nothing suggests it is more than luck."
    }
    // 5.93 hintergrund
    override val rrHintMax = "Lowest point below an earlier peak."
    override val rrHintErholung = "Time from the low until the old peak was reached again."
    override val rrHintLaengster = "Longest time the price stayed below an earlier peak."
    override val rrHintRendite = "Gain or loss converted to one year, without dividends."
    override val rrHintBeta = "Shows how strongly the stock moves along on average when the index moves. 1 means equally, above 1 more, below 1 less."
    override val rrHintKorrelation = "Shows how similarly the stock and the index move. 1 means in step, 0 independent, −1 opposite."
    override val lvKurz = "Short sellers borrow shares and sell them, hoping to buy them back cheaper. A high share shows that many bet on falling prices."
    // 5.93 finder-radar
    override val afAnsichtListe = "List"
    override val afAnsichtRadar = "Radar"
    override val afImGespraech = "Trending"
    override val afRadarAlle = "All signals"
    override val afRadarErwaehnungen = "Mentions"
    override val afRadarWikipedia = "Wikipedia"
    override fun afRadarStaerke(n: String) = "Signal strength $n"
    override val afRadarHinweis = "The circle shows the Stock Finder score, a dash means not rated yet. Attention does not raise the score, it is only shown."
    // 5.93 stimmung
    override val sbWsbUnklar = "Tone unclear"
    override val sbWsbNachFall = "Discussed after a price drop"
    override val sbWsbNachAnstieg = "Discussed after a price rise"
    override fun sbWsbAnlass(ticker: String, aenderung: String) = "Heavily discussed after a big move: $ticker $aenderung in 5 trading days."
    override fun sbWsbUnklarMehr(prozent: Int) = "Only $prozent% of comments have a clear tone, so there is no positive or negative reading."
    override val sbWsbOffen = "No clear tone"
    override val sbVix = "Volatility (VIX)"
    override val sbQuelleVix = "Source: Yahoo Finance"
    override fun tdText(richtung: Int, ziel: String) = listOf("Bullish", "Neutral", "Bearish").getOrElse(richtung) { "" } + " for " + when (ziel) {
        "aktien" -> "stocks"; "aktien_eu" -> "European stocks"; "anleihen" -> "bonds"; "oel" -> "oil prices"
        "benzin" -> "gasoline prices"; "diesel" -> "diesel prices"; "gas_us" -> "US natural gas"; "gas_eu" -> "natural gas in Europe"
        "fluglinien" -> "airlines"; "getreide" -> "grain prices"; "gold" -> "gold"; "chips" -> "chip stocks"
        "transport" -> "transport stocks"; "reedereien" -> "shipping companies"; else -> ziel
    }
    override fun sbVixMehr(vix: String) = "The VIX measures expected S&P 500 volatility and stands at $vix. A reading of 100 means as calm as at any time in the past year. A reading of 0 means as turbulent as ever."

    // 5.93 ipo
    override val secIpoTitel = "New SEC filings"
    override val secIpoKontakt = "With a contact address under Access, the app shows new IPO prospectuses and pricings from the SEC here."
    override val secIpoLeer = "No matching filings in the last trading days."
    override val secIpoProspekt = "Prospectus filed"
    override val secIpoAenderung = "Prospectus amended"
    override val secIpoPreis = "Price set"
    override val secIpoHinweis = "Source: SEC daily index of the last five trading days. Form 424B4 also covers other securities offerings, not only IPOs."

    // 5.93 finder-punkte
    override val afHaltenTitel = "Holding long term"
    override fun afHaltenSeit(von: String, jahre: String, top20: String, msci: String, spx: String) =
    "Top 20 bought from $von and held until today ($jahre years): $top20. MSCI World $msci, S&P 500 $spx."
    override fun afHaltenZeile(jahre: String, top20: String, msci: String, spx: String) =
    "Holding $jahre years, average of all dates: top 20 $top20, MSCI World $msci, S&P 500 $spx."
    override fun afHaltenHinweis(fehlt: String) =
    "Equally weighted, never rebalanced, prices without dividends. On average $fehlt of the members of the time are missing, which flatters the result."

    // 5.93 kennzahlen
    override val bkKursCashflow = "Price/cash flow"
    override val bkZinsdeckung = "Interest coverage"
    override val bkRoic = "ROIC"
    override val bkOwnerEarnings = "Owner earnings"

    // 5.93 halter-gewinne
    override val hlTitel = "Largest institutional holders"
    override val hlInstitutionen = "Institutions in total"
    override val hlInsider = "Insiders in total"
    override fun hlAnzahl(n: Int) = "$n institutions report holdings"
    override val hlGroesste = "The ten largest"
    override fun hlStand(datum: String) = "Holdings as of $datum"
    override val hlHinweis = "Institutions report their holdings every quarter, in the US with Form 13F up to 45 days after quarter end, so the figures are older than the price. The arrow shows the change in the number of shares since the previous quarter."
    override fun zuNaechster(datum: String) = "Next results: $datum"
    // 5.93 maerkte
    // 5.93 Markets: interpretations
    override val maerkteBewegungenDeutung = "Big daily moves show where news or strong demand is at work. A jump says nothing about the direction that follows."
    override val meKernTitel = "Market expectations"
    override fun meKern(zins: Int?, rezession: Int?) = listOfNotNull(
        when (zins) {
            -1 -> "The market expects Fed rate cuts within twelve months."
            0 -> "The market expects the Fed to leave rates roughly unchanged within twelve months."
            1 -> "The market expects Fed rate hikes within twelve months."
            else -> null
        },
        when (rezession) {
            0 -> "Polymarket sees a recession as unlikely."
            1 -> "Bettors consider a recession possible."
            2 -> "Bettors consider a recession more likely than not."
            else -> null
        },
    ).joinToString(" ")
    override fun meFalls(schluessel: String) = when (schluessel) {
        "fed" -> "If it happens: Lower rates usually support stocks and bonds, higher rates weigh on them, growth stocks most."
        "fed_jahr" -> "If it happens: More cuts usually ease loans and growth stocks, and the dollar often weakens."
        "ezb" -> "If it happens: Lower euro-area rates usually weaken the euro and help stocks, higher rates weigh on them."
        "rezession" -> "If it happens: Company profits usually fall, stocks often drop, and safe bonds and gold are in more demand."
        "inflation" -> "If it happens: High inflation keeps rates high for longer, which usually weighs on stocks and bonds."
        "oel" -> "If it happens: A high oil price makes fuel and transport more expensive, helps oil firms and weighs on airlines."
        "hormus" -> "If it happens: The strait matters for oil shipping. A closure usually lifts the oil price and unsettles markets."
        "iran" -> "If it happens: Tension in the Middle East usually lifts oil and gold and often pushes stocks down."
        "wahlen" -> "If it happens: New majorities often change taxes, tariffs and rules, and some industries react to that."
        else -> ""
    }
    override fun fiDeutung(schluessel: String) = when (schluessel) {
        "rohoel" -> "High: plenty of oil, which tends to weigh on the price. Low: tight supply, which tends to support the price."
        "cushing" -> "High: a lot of oil at the WTI delivery point, which weighs short term. Low: tight, which can lift WTI."
        "benzin" -> "High: a lot of gasoline, demand is weak or supply is large. Low: gasoline gets scarcer, which supports the price."
        "destillat" -> "High: plenty of diesel and heating oil, which tends to weigh on the price. Low: tighter, which tends to support it."
        "spr" -> "High: a large buffer, the state can step in during shortages. Low: less buffer."
        "gas_us" -> "High: full storage, which tends to weigh on the gas price. Low: tight, which tends to support it."
        "gas_eu" -> "High: well filled for winter, which calms the gas price. Low: shortages may loom, which tends to lift the price."
        "erstantraege" -> "High: more people file for unemployment, a warning sign for the economy. Low: the job market is stable."
        "fluggaeste" -> "High: many travelers, good for airlines. Low: weaker demand for travel."
        "duerre" -> "High: a lot of land in drought, crops suffer and grain prices can rise. Low: good conditions for the harvest."
        else -> ""
    }

    // 5.93 stimmung2
    override val sbSicher = "Safe havens (US)"
    override val sbQuelleSicher = "Source: Yahoo Finance, own calculation"
    override fun sbSicherMehr(unterschied: String) = "Return of the S&P 500 minus return of US government bonds (ETF IEF) over 20 trading days: $unterschied. Plus means stocks did better and safe assets were less in demand. The gauge ranks the value against the past year."

    // 5.93 selbstabruf
    override fun saWartet(ordner: String) = "The page is open in your browser. Download the file there. While this screen is open (up to 15 minutes), the app imports new files from $ordner automatically."
    override val saSeiteUebernehmen = "Use this page"
    override val saFehler = "The file could not be loaded."

    // 5.93 nacharbeit
    override fun trRangAenderung(plaetze: Int, hoeher: Boolean) =
        if (hoeher) (if (plaetze == 1) "1 place higher than 24 hours ago" else "$plaetze places higher than 24 hours ago") else (if (plaetze == 1) "1 place lower than 24 hours ago" else "$plaetze places lower than 24 hours ago")

    // 5.94
    override val anHochgestuft = "upgraded"
    override val anAbgestuft = "downgraded"
    override fun lvStufe(stufe: Int) = when (stufe) { 0 -> "Few bets on falling prices"; 1 -> "Some bets on falling prices"; 2 -> "Many bets on falling prices"; else -> "Very many bets on falling prices" }
    override fun trHeuteKurz(rang: Int, veraenderung: String?) = "Today rank $rang" + (veraenderung?.let { " · $it vs. the day before" } ?: " · new")
    override fun trSummenKurz(woche: String, monat: String?, jahr: String, seit: String?, spitze: Pair<String, String>?) = listOfNotNull("7 days $woche", monat?.let { "30 days $it" }, if (seit != null) "since $seit $jahr" else "Year $jahr", spitze?.let { "peak ${it.first} on ${it.second}" }).joinToString(" · ")
    override fun ersMastodonKurz(beitraege: Int) = (if (beitraege == 1) "1 post" else "$beitraege posts") + " in 7 days"
    override val nwEinordnung = "Keyword-based reading"
    override val nwZurAktie = "Open stock"
    override val ampelGut = "Good"
    override val ampelAchtung = "Caution"
    override val ampelKritisch = "Critical"
    override val ampelHinweis = "Note"
    override val wlSortMarktwert = "By market cap"
    override fun epWasBranche(markt: String) = if (markt == "SOXX") "chip stocks" else "transport stocks"
    override fun epBranche(markt: String, abstand: String, besser: Boolean) = (if (markt == "SOXX") "Chip stocks" else "US transport stocks") + " $abstand ${if (besser) "better" else "worse"} than the S&P 500 over a year"
    override fun epErsatzSatz(schluessel: String, markt: String, urteil: Int): String {
        val werte = if (markt == "SOXX") "Chip stocks" else "Transport stocks"
        val signal = when (schluessel) {
            "lieferketten" -> "supply chain pressure"
            "fracht" -> "freight volumes"
            else -> "chip production"
        }
        return when (urteil) {
            0 -> "$werte are in line with $signal: priced in."
            1 -> "$werte are doing better than $signal suggests."
            else -> "$werte are doing worse than $signal suggests."
        }
    }
    override fun ipoReiter(reiter: Int) = listOf("Upcoming", "Filed", "Newly listed", "Withdrawn", "Well known").getOrElse(reiter) { "" }
    override fun ipoStatusTag(status: Int, datum: String) = listOf("Expected", "Filed", "Listed", "Withdrawn").getOrElse(status) { "" } + " " + datum
    override val ipoReiterLeer = "There are no entries here right now."
    override val ipoEckdaten = "Key facts"
    override val ipoPreis = "Price per share"
    override val ipoAktien = "Shares offered"
    override val ipoVolumen = "Offer amount"
    override val ipoEinschaetzung = "Assessment"
    override fun ipoPunkt(merkmal: Int, ampel: Int, wert: String?): String = when (merkmal) {
        0 -> "Shell company (SPAC): it raises money to buy a company that is not yet known. Many SPAC shares fell sharply after the merger."
        else -> "Small offering ($wert). Such stocks get little attention and often swing a lot."
    }
    override val ipoRitter = "The lights explain risk features and are not a recommendation to buy. Free float, lock-up and sales by existing owners are in the prospectus. Studies by Jay Ritter (University of Florida) show that IPOs on average did worse than comparable listed stocks over three to five years."
    override val ipoBrancheTitel = "Sector and outlook"
    override fun ipoBranche(b: Int) = listOf("Artificial intelligence", "Software and tech", "Biotech and pharma", "Bank", "Energy", "Consumer", "Shell company (SPAC)").getOrElse(b) { "" }
    override fun ipoBrancheText(b: Int) = when (b) {
        0 -> "AI companies often grow very fast and spend a lot on data centers and chips. It counts whether revenue grows faster than compute costs, and how high the valuation is relative to revenue. Expectations are high, and after disappointments the price often falls sharply."
        1 -> "Revenue growth, gross margin and the Rule of 40 count. Under that rule growth and profit margin together should reach at least 40%. Recurring subscription revenue counts more than one-off sales."
        2 -> "Without an approved product, the value depends on trial results and approvals. It counts how long the cash lasts and when the next trial data arrives. One failure can halve the price."
        3 -> "Banks are valued by price to book and return on equity, the P/E ratio says little. Loan quality, a broad deposit base and interest rate risk on the balance sheet count as well."
        4 -> "The value depends on commodity prices, interest rates and politics. Power demand from data centers drives grids, gas and nuclear, and that expectation is often already in the price."
        5 -> "Brand, repeat purchases and margins matter. If growth only comes with high advertising costs, profits often disappoint later."
        else -> "Until the merger the money usually sits in a trust account, about 10 dollars per share. After that only the acquired company counts, and it is still unknown at the IPO."
    }
    override val ipoUeber = "About the company"
    override val ipoDiskussion = "Discussion"
    override fun ipoHnWoche(n: Int) = "Hacker News: " + (if (n == 1) "1 post" else "$n posts") + " in 7 days"
    override val ipoKeineSchlagzeilen = "No headlines in the app's news."
    override val ipoDokumente = "Filed documents"
    override fun ipoFormular(f: String) = when (f) {
        "S-1", "F-1" -> "Prospectus"
        "S-1/A", "F-1/A" -> "Amended prospectus"
        "424B4", "424B1" -> "Final prospectus with price"
        "EFFECT" -> "Declared effective by the SEC"
        "RW" -> "Withdrawal"
        else -> "Additional offering"
    } + " ($f)"
    override val ipoEdgarLink = "All filings at the SEC"
    override val ipoQuelle = "Finnhub IPO calendar"
    override val ipoOhneSchluessel = "The app loads the calendar of all IPOs with your own Finnhub key (Profile, Access). Without a key this page lists the prospectuses filed with the SEC in recent days."
    override val ipoKalenderFehlt = "The Finnhub IPO calendar is not reachable right now. This page lists the prospectuses filed with the SEC in recent days."
    override val ipoKeineEckdaten = "Price and size are not set yet."
    override val ipoEdgarSuche = "Search prospectuses at the SEC"
    override fun ipoKatalogGeplant(stand: String) = "Planned, no US prospectus yet (as of $stand)"
    override fun bkBankSatz(kbv: String, roe: String, angemessen: String?) = if (angemessen != null) "Price to book $kbv with $roe return on equity. About $angemessen would be fair." else "Price to book $kbv. A $roe return on equity does not cover the cost of capital."
    override fun bkBankUnter(kapitalkosten: String, wachstum: String) = "For banks and insurers the P/E ratio says little. Based on $kapitalkosten cost of capital and $wachstum growth."
    override fun bkBankBilanzSatz(roa: String?, ekQuote: String?) = listOfNotNull(roa?.let { "Return on assets $it" }, ekQuote?.let { "equity ratio $it" }).joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    override val bkBankBilanzUnter = "Good from 1% return on assets and an 8% equity ratio, weak below 0.5% or 5%."
    override val heatmapAlle = "All"
    override val heatmapZoomZurueck = "Whole map"
    override fun skKnoten(id: String): String = when (id) {
        "anfang" -> "Value at start"
        "einlagen" -> "Deposits"
        "kursgewinne" -> "Price gains"
        "dividenden" -> "Dividends"
        "zinsen" -> "Interest"
        "depot" -> "Portfolio"
        "ende" -> "Value at end"
        "entnahmen" -> "Withdrawals"
        "kursverluste" -> "Price losses"
        "gebuehren" -> "Fees"
        "steuern" -> "Taxes"
        "erstattungen" -> "Refunds"
        "korrekturen" -> "Corrections"
        "ergebnis" -> "Sales"
        "netto+" -> "Net gain"
        "netto-" -> "Net loss"
        "weitere+", "weitere-" -> "Others"
        "umsatz" -> "Revenue"
        "herstellkosten" -> "Cost of revenue"
        "brutto" -> "Gross profit"
        "forschung" -> "R&D"
        "verwaltung" -> "SG&A"
        "sonstige" -> "Other costs"
        "betrieb" -> "Operating income"
        "betriebsverlust" -> "Operating loss"
        "steuer" -> "Taxes"
        "zinsenSonstiges" -> "Interest, other"
        "finanzertrag" -> "Interest, other"
        "netto" -> "Net income"
        else -> de.troetenrepublik.app.engine.Sankey.symbol(id).let { de.troetenrepublik.app.market.Symbols.find(it)?.name ?: it }
    }
    override val skGeldfluss = "Money flow"
    override val skVerkaeufe = "Result of sales"
    override fun skGuvTitel(jahr: String) = "From revenue to profit ($jahr)"
    override fun afVorlage(id: String): String = when (id) {
        "LEERVERKAUFT" -> "Heavily shorted stocks"
        "SQUEEZE" -> "Short squeeze"
        "ZAHLENLUECKE" -> "Gap up before earnings"
        "INSOLVENZ" -> "Bankruptcy candidates"
        "WOCHENTIEF" -> "Uptrend with a weak week"
        "LINIENSPRUNG" -> "Jump above the 20-day line"
        "UMKEHR" -> "Oversold reversal"
        "UEBERVERKAUFT_ZAHLEN" -> "Oversold before earnings"
        "HOECHSTSTAENDE" -> "New highs"
        "AUSBRUCH" -> "Breakout"
        "KREUZUNG" -> "Moving average cross"
        "GEWINNWACHSTUM" -> "High earnings growth"
        "UMSATZWACHSTUM" -> "High sales growth"
        "REL_VOLUMEN" -> "High relative volume"
        "STETIG" -> "Steady growth in an uptrend"
        "HALTEN" -> "Buy and hold"
        "DIVIDENDENWACHSTUM" -> "Undervalued dividend growth"
        "NIEDRIGES_KGV" -> "Low P/E"
        "CANSLIM" -> "CANSLIM"
        "NEBENWERTE_WACHSTUM" -> "Small caps with growth"
        "LUECKE_AUF" -> "Gap up"
        "LUECKE_AB" -> "Gap down"
        else -> id
    }
    override fun afVorlageIdee(id: String): String = when (id) {
        "LEERVERKAUFT" -> "US stocks with a high short interest and active trading."
        "SQUEEZE" -> "Liquid US stocks with heavy short selling and few institutions, candidates for a short squeeze."
        "ZAHLENLUECKE" -> "Stocks reporting within two days that opened today with a gap up."
        "INSOLVENZ" -> "US stocks below book value with a very high short interest."
        "WOCHENTIEF" -> "Stocks in an uptrend that fell over the last week, weakest week first."
        "LINIENSPRUNG" -> "Stocks that jumped above the 20-day line on high volume and are still below the 50-day line."
        "UMKEHR" -> "Oversold stocks (RSI below 30) up on the day with double volume."
        "UEBERVERKAUFT_ZAHLEN" -> "Smaller stocks with earnings growth and a report due within a month, RSI below 50."
        "HOECHSTSTAENDE" -> "Stocks under €7 per share rated buy that reach a 20-day, 50-day and 52-week high at once."
        "AUSBRUCH" -> "Breakout to a 50-day high above all three moving averages with a solid balance sheet."
        "KREUZUNG" -> "Profitable stocks whose 20-day line crossed above the 50-day line in the last five trading days."
        "GEWINNWACHSTUM" -> "Earnings and sales grow by more than 25%, the price is above the 200-day line."
        "UMSATZWACHSTUM" -> "Growing companies with little debt, high institutional ownership and little short selling."
        "REL_VOLUMEN" -> "Solvent growth stocks in an uptrend with unusually high volume."
        "STETIG" -> "Earnings growth over several periods, the price is at least 90% above the 52-week low."
        "HALTEN" -> "Small stocks with expected growth, a high return on equity and good liquidity."
        "DIVIDENDENWACHSTUM" -> "Large dividend payers with a moderate valuation, growth and a payout below half of earnings."
        "NIEDRIGES_KGV" -> "Small stocks below book value with a low P/E and positive returns."
        "CANSLIM" -> "Filters after William O\'Neil\'s CANSLIM rules, as far as the data allows."
        "NEBENWERTE_WACHSTUM" -> "Small caps between 300m and 2bn with earnings and sales growth above 20%."
        "LUECKE_AUF" -> "Stocks worth at least 300m that opened today at least 3% above the previous close, largest gap first. Before the open the list still shows the gaps of the last trading day."
        "LUECKE_AB" -> "Stocks worth at least 300m that opened today at least 3% below the previous close, largest gap first. Before the open the list still shows the gaps of the last trading day."
        else -> ""
    }
    override fun afVorlageFehlt(id: String): String? = when (id) {
        "LEERVERKAUFT" -> "Without checking whether options and short selling are available, there is no free source for that."
        "ZAHLENLUECKE" -> "Without ATR and without telling apart reports before or after the close."
        "WOCHENTIEF" -> "The app approximates the upward channel with the price above the 200-day line and the 50-day above the 200-day line."
        "UEBERVERKAUFT_ZAHLEN" -> "Earnings date within the next 31 days instead of the current month."
        "HOECHSTSTAENDE" -> "Highs from closing prices, for the 52-week high a gap of at most 1% is enough."
        "AUSBRUCH" -> "50-day high from closing prices."
        "UMSATZWACHSTUM" -> "Without five-year sales growth."
        "STETIG" -> "Without earnings growth over the past five years."
        "CANSLIM" -> "Without five-year earnings growth, a float below 20m shares and the market direction. The market phase is under Discover."
        "NEBENWERTE_WACHSTUM" -> "Without five-year growth."
        else -> null
    }
    override fun afFeld(id: String): String = when (id) {
        "BOERSENWERT" -> "Market cap"
        "KURS" -> "Price"
        "KGV" -> "P/E"
        "KGV_ERWARTET" -> "Forward P/E"
        "KBV" -> "P/B"
        "DIVIDENDE" -> "Dividend"
        "NOTE" -> "Analyst rating"
        "JAHR" -> "1 year"
        "LINIE200" -> "Distance to 200-day line"
        "LINIE50" -> "Distance to 50-day line"
        "SMA50_200" -> "50-day vs 200-day line"
        "TAG" -> "Today"
        "LUECKE" -> "Gap"
        "VOLUMEN" -> "Volume today"
        "VOLUMEN_SCHNITT" -> "Average volume"
        "REL_VOLUMEN" -> "Relative volume"
        "HOCH52" -> "Distance to 52-week high"
        "TIEF52" -> "Above 52-week low"
        "ZAHLEN_TAGE" -> "Days to earnings"
        "PEG" -> "PEG"
        "ROE" -> "Return on equity"
        "ROA" -> "Return on assets"
        "VERSCHULDUNG" -> "Debt/equity"
        "LEER" -> "Short interest"
        "BRUTTOMARGE" -> "Gross margin"
        "GEWINN_QUARTAL" -> "Earnings quarter"
        "UMSATZ_QUARTAL" -> "Sales quarter"
        "GEWINN_JAHR" -> "Earnings growth this year"
        "GEWINN_FOLGEJAHR" -> "Earnings growth next year"
        "GEWINN_FUENF" -> "Earnings growth 5 years"
        "INSTITUTIONEN" -> "Institutions"
        "BETA" -> "Beta"
        "LIQUIDITAET" -> "Current ratio"
        "LIQUIDITAET_SCHNELL" -> "Quick ratio"
        "AUSSCHUETTUNG" -> "Payout ratio"
        "RSI" -> "RSI"
        "LINIE20" -> "Distance to 20-day line"
        "WOCHE" -> "Week"
        "JAHRESANFANG" -> "Year to date"
        "HOCH20" -> "Distance to 20-day high"
        "HOCH50" -> "Distance to 50-day high"
        "KREUZUNG" -> "Moving average cross"
        else -> id
    }
    override val afVorlageKeine = "No template"
    override val afVorlageNurUs = "US stocks only, short interest as a share of the float is freely available only there."
    override val afVorlageHinweis = "Based on the finviz screener templates. Price and market cap in euros using the numbers of the dollar limits."
    override fun afVorlageStand(offen: Int) = if (offen == 1) "1 stock still waits for its profile or daily prices." else "$offen stocks still wait for their profile or daily prices."
    override val afVorlageWeiter = "Check more"
    override val afAlarm = "Notify new matches"
    override val afAlarmHinweis = "You are notified about stocks that newly meet all enabled limits."
    override fun afAlarmTitel(vorlage: String) = "New in “$vorlage”"
    override fun afAlarmText(namen: List<String>, weitere: Int) = namen.joinToString(", ") + if (weitere > 0) " and $weitere more" else ""
    override fun afFeldAb(feld: String, wert: String) = "$feld from $wert"
    override fun afFeldBis(feld: String, wert: String) = "$feld up to $wert"
    override fun afFeldZwischen(feld: String, von: String, bis: String) = "$feld $von to $bis"
    override fun afFeldPositiv(feld: String) = "$feld positive"
    override fun afFeldNegativ(feld: String) = "$feld negative"
    override fun afUeberLinie(id: String) = when (id) {
        "SMA50_200" -> "50-day above 200-day line"
        "LINIE20" -> "Price above 20-day line"
        "LINIE50" -> "Price above 50-day line"
        else -> "Price above 200-day line"
    }
    override fun afUnterLinie(id: String) = when (id) {
        "SMA50_200" -> "50-day below 200-day line"
        "LINIE20" -> "Price below 20-day line"
        "LINIE50" -> "Price below 50-day line"
        else -> "Price below 200-day line"
    }
    override fun afNeuesHoch(tage: Int) = "New $tage-day high"
    override fun afNahHoch52(abstand: String) = "At most $abstand below the 52-week high"
    override fun afZahlenIn(tage: Int) = "Earnings within $tage days"
    override val afKreuzung = "20-day line crosses above the 50-day line"
    override fun afSortFeld(feld: String, aufsteigend: Boolean) = if (aufsteigend) "$feld, ascending" else feld
    override val widgetNewsInhalt = "Content"
    override val widgetNewsDepot = "Headlines on my positions"
    override val widgetNewsWichtig = "Top stories only"
    override val widgetNewsWichtigTitel = "Top stories"
    override val widgetNewsWichtigKeine = "No top stories right now."
    override val widgetNewsAlle = "All headlines"
    override val widgetNewsAlleTitel = "Headlines"
    override fun widgetNewsFeed(name: String) = "Own feed: $name"
    override val widgetNewsListeKeine = "No headlines right now."
    override val widgetNewsFeedsHinweis = "Create your own feeds in News with the plus next to the newspaper icon."
    override val widgetSchnellWahl = "Tiles"
    override val widgetSchnellHinweis = "Choose four pages. A fifth choice replaces the oldest."
    override val widgetSchnellVerlauf = "Portfolio history"
    override val widgetEinzelSuche = "Search stock"
    override fun apTitel(id: String): String = when (id) {
        "HALTEDAUER" -> "Holding period"
        "TREFFER" -> "Hit rate"
        "CHANCE_RISIKO" -> "Gains against losses"
        "DISPOSITION" -> "Letting losers run"
        "HAEUFIGKEIT" -> "Trading frequency"
        "HEBEL" -> "Leveraged products in trading"
        "NACHKAUF" -> "Averaging down"
        else -> id
    }
    override fun apWert(id: String, a: String, b: String, zahl: Int) = when (id) {
        "HALTEDAUER" -> "Median $a days across $zahl sales"
        "TREFFER" -> "$a of $zahl sales in profit"
        "CHANCE_RISIKO" -> "An average gain is $a times an average loss, $b per sale"
        "DISPOSITION" -> "Losers held $a days (median), winners $b days"
        "HAEUFIGKEIT" -> "$a orders a month, $zahl in twelve months"
        "HEBEL" -> "$a of the traded amount"
        "NACHKAUF" -> if (zahl == 1) "1 purchase more than 20% below cost" else "$zahl purchases more than 20% below cost"
        else -> a
    }
    override fun apErklaerung(id: String) = when (id) {
        "HALTEDAUER" -> "Holding positions for only days means trading price moves more than businesses. Costs and taxes arise with every switch."
        "TREFFER" -> "The hit rate alone says little. It also matters whether gains outweigh losses."
        "CHANCE_RISIKO" -> "If the sum of all sales in euros is negative, trading has cost money so far. A ratio below 1 needs a high hit rate."
        "DISPOSITION" -> "Many investors sell winners early and hold losers hoping for a recovery. Fixed selling rules help against this."
        "HAEUFIGKEIT" -> "Frequent trading raises costs and the number of decisions. Studies of retail investors find the weakest results after costs among the most active."
        "HEBEL" -> "Leveraged products lose value quickly and can expire worthless. A high share of trading ties the result to a few highly volatile bets."
        "NACHKAUF" -> "Buying more after a loss lowers the cost basis but raises the weight of a stock that is falling. Check first whether the reason to buy still holds."
        else -> ""
    }
    override val apUeberschrift = "Investor profile"
    override val apZuWenig = "Holding period, hit rate and gains against losses need at least five completed sales."
    override val apHinweis = "Calculated from the purchases and sales in the app, first in first out per position. Savings plans and transferred positions do not count towards frequency."
    override val scTitel = "Self-check"
    override val scFragen = listOf(
        "I have an emergency fund of three to six months of expenses outside the portfolio.",
        "I will not need the money in the portfolio for at least five years.",
        "I can say in one sentence why I hold each position.",
        "I know in advance when I will sell, at a gain as well as at a loss.",
        "I would sit out a 30% drop in the portfolio without selling out of fear.",
        "I know the ongoing costs of my funds and the fees per order.",
        "I have no debts with higher interest than I expect from the portfolio.",
        "I review the weighting once or twice a year and only rebalance then.",
    )
    override fun scStand(ja: Int, alle: Int) = "$ja of $alle apply"
    override val scHinweis = "For you to tick off. The answers stay on this page only and are not saved."
    override fun anHaelt(anteil: String, wert: String, stichtag: String, seit: String?) = "Holds $anteil itself" + (if (wert.isNotBlank()) " ($wert)" else "") + " as reported for $stichtag" + (seit?.let { ", price since $it" } ?: "")
    override val anHalterHinweis = "Holdings from the mandatory filings of institutions (Form 13F in the US), only the ten largest holders are known. Institutions do not report their cost, so whether they are at a loss stays open. The price change since the reporting date only shows how the reported holding has developed since."
    override fun anHalterKeiner(n: Int) = "None of the firms is among the $n largest reported holders. The source does not list smaller holdings."
    override val finJahreTitel = "Balance sheet and cash flow over the years"
    override val finLangfristSchulden = "Long-term debt"
    override val finInvestitionen = "Capital expenditure"
    override fun umsatzSchaetzung(quartal: String) = if (quartal.isBlank()) "Expected revenue" else "Expected revenue $quartal"
    override val qzTitel = "Earnings"
    override val qzIntro = "Who reports in the chosen week, by day and time. On the right the expected earnings per share, after the report the actual figure."
    override val qzBekannte = "Well known"
    override val qzAlle = "All"
    override val qzFehlt = "The earnings calendar is not reachable right now."
    override val qzOhneSchluessel = "Without a key this page shows only your portfolio and watchlist. The app loads the calendar of all US stocks with your own Finnhub or Alpha Vantage key (Profile, Access)."
    override val qzKeineEigenen = "Nothing from your portfolio or watchlist"
    override fun qzUmsatz(wert: String) = "revenue est. $wert"
    override val qzTagFehlt = "not loaded"
    override val qzKeine = "No reports"
    override fun qzKeineBekannten(n: Int) = "No well-known company, $n smaller ones under “All”"
    override fun qzZeit(zeit: Int) = listOf("Before the open", "During trading", "After the close", "Time not given").getOrElse(zeit) { "" }
    override val qzHeute = "today"
    override fun qzVon(gezeigt: Int, gesamt: Int) = "$gezeigt of $gesamt"
    override val qzEigen = "in your portfolio or watchlist"
    override fun qzErwartet(wert: String) = "expected $wert"
    override fun qzVorjahr(wert: String) = "year ago $wert"
    override val qzHinweis = "Well known means: portfolio, watchlist, stocks from the app's directory and companies with expected quarterly revenue of 1 billion dollars or more, the eight largest per time slot. Dates can move, earnings per share are in dollars and mostly exclude one-off items. Source: Alpha Vantage."
    override fun qzQuelle(anbieter: String?) = when (anbieter) {
        "finnhub" -> "Finnhub earnings calendar, portfolio and watchlist dates via Yahoo Finance"
        "alphavantage" -> "Alpha Vantage earnings calendar, portfolio and watchlist dates via Yahoo Finance"
        else -> "Portfolio and watchlist dates via Yahoo Finance"
    }
    // 5.95 Lesemodus
    override val lesemodusLeer = "The loaded page shows no text the app can display. “Show original page” takes you back."
    // 5.95 Gesamtstimmung
    override val sgTitel = "Overall sentiment"
    override val sgKachel = "Overall sentiment"
    override val sgAnbieter = "All gauges"
    override fun sgAnzahl(n: Int, von: Int) = "$n of $von parts"
    override val sgLaedtNoch = "loading more"
    override val sgGewichte = "Weighting of the parts"
    override fun sgGruppe(schluessel: String) = when (schluessel) {
        "schwankung" -> "Volatility"; "kurs" -> "Price trend"; "breite" -> "Market breadth"; "kredit" -> "Credit and stress"
        "cnn" -> "CNN Fear & Greed"; "umfrage" -> "Investor survey"; "sicher" -> "Safe havens"
        "position" -> "Futures positioning"; "netz" -> "Online mood"; "krypto" -> "Crypto"; "konjunktur" -> "Economy"
        "hebel" -> "Leverage"; else -> schluessel
    }
    override fun sgGruppeWarum(schluessel: String) = when (schluessel) {
        "schwankung" -> "Option prices react every day and are considered the most reliable short-term measure of fear. The four values are closely related and share one weight."
        "kurs" -> "How strongly prices run, daily and with a long history. Momentum and trend are similar and count together."
        "breite" -> "Shows whether many stocks rise or only a few large ones."
        "kredit" -> "Lenders often turn cautious before stocks fall."
        "cnn" -> "A finished index of seven parts. It overlaps with several groups here and therefore gets no higher weight."
        "umfrage" -> "The only direct survey of investors, but it swings strongly from week to week."
        "sicher" -> "Whether money flows rather into stocks or into safe bonds."
        "position" -> "Weekly numbers of the large speculators, they change slowly."
        "netz" -> "Loud and easy to distort, therefore little weight."
        "krypto" -> "A market of its own, showing the appetite for risk at the edge."
        "konjunktur" -> "Slow numbers about the economy, rather background than market sentiment."
        "hebel" -> "Only quarterly and with a delay."
        else -> ""
    }
    override fun sgTeil(schluessel: String) = when (schluessel) {
        "vix" -> "VIX volatility index"; "vix_struktur" -> "VIX term structure"; "vvix" -> "VVIX, volatility of the VIX"; "move" -> "MOVE, US bond volatility"
        "momentum" -> "S&P 500 momentum"; "trend_sp500" -> "S&P 500 trend"; "trend_dax" -> "DAX trend"
        "breite200" -> "S&P 500 stocks above 200-day line"; "hochtief" -> "52-week highs vs lows"; "rsp" -> "Equal weight vs cap weight S&P 500"
        "hochzins" -> "US high-yield spread"; "stress" -> "Financial stress (St. Louis Fed)"
        "cnn" -> "CNN Fear & Greed"; "aaii" -> "AAII retail investors"; "sicher" -> "Stocks vs government bonds"
        "cot_sp500" -> "Speculators S&P 500"; "cot_nasdaq" -> "Speculators Nasdaq 100"
        "wsb" -> "r/wallstreetbets"; "sorgen" -> "Worries on Wikipedia"
        "krypto" -> "Crypto Fear & Greed"
        "umich" -> "US consumer sentiment"; "zinskurve" -> "US yield curve"; "sahm" -> "Sahm rule"; "margin" -> "Margin loans"
        else -> schluessel
    }
    override fun sgMethode(schluessel: String) = when (schluessel) {
        "vix" -> "Rank among the closing prices of the past year, a low VIX counts as greed"
        "vix_struktur" -> "Rank over the past year, VIX above VIX3M counts as fear"
        "vvix", "move" -> "Rank among the values of the past year, low counts as greed"
        "momentum" -> "Distance of the S&P 500 from its 125-day line, rank over the past year"
        "trend_sp500", "trend_dax" -> "Distance from the 200-day line, rank over the past year"
        "breite200" -> "Fixed bounds: 20 % of stocks give 0, 80 % give 100"
        "hochtief" -> "Highs minus lows in percent of all stocks, fixed bounds: −30 % gives 0, +30 % gives 100"
        "rsp" -> "Lead over 20 trading days, rank over the past year"
        "hochzins" -> "Rank among the daily values of the past two years, a low spread counts as greed"
        "stress" -> "Rank among the weekly values of the past two years, little stress counts as greed"
        "cnn" -> "Value from CNN unchanged"
        "aaii" -> "Share of bulls among bulls and bears"
        "sicher" -> "Lead of stocks over 20 trading days, rank over the past year"
        "cot_sp500", "cot_nasdaq" -> "Position between the low and the high of the past three years"
        "wsb" -> "Share of positive comments, only with a clear direction"
        "sorgen" -> "Views compared with the whole period, much reading counts as fear"
        "krypto" -> "Value from alternative.me unchanged"
        "umich" -> "Rank among the monthly values of the past ten years"
        "zinskurve" -> "10 years minus 3 months, traffic light: green 70, yellow 40, red 20"
        "sahm" -> "Fixed bounds: 0.5 points give 0, −0.1 give 100"
        "margin" -> "Fixed bounds: −20 % year on year give 0, +30 % give 100"
        else -> ""
    }
    override fun sgRoh(schluessel: String, zahl: String) = when (schluessel) {
        "vix" -> "VIX $zahl"; "vvix" -> "VVIX $zahl"; "move" -> "MOVE $zahl"; "vix_struktur" -> "VIX to VIX3M $zahl"
        "trend_sp500", "trend_dax" -> "$zahl vs 200-day line"; "breite200" -> "$zahl above 200-day line"
        "hochzins" -> "spread $zahl pts"; "stress" -> "stress index $zahl"
        "sicher" -> "stocks vs bonds $zahl"; "cot_sp500", "cot_nasdaq" -> "net $zahl"
        "umich" -> "index $zahl"; "zinskurve" -> "spread $zahl\u00A0pts"
        "sahm" -> "$zahl pts"; "margin" -> "$zahl year on year"; else -> zahl
    }
    override fun sgHochTief(hochs: Int, tiefs: Int) = "$hochs near 52-week high · $tiefs near 52-week low"
    override fun sgGewicht(prozent: String) = "weight $prozent"
    override fun sgStand(datum: String) = "as of $datum"
    override fun sgFehlen(namen: String) = "No data: $namen. Their weight goes to the other parts."
    override val sgQuellen = "Sources: CNN, AAII, Cboe, CFTC, alternative.me, Tradestie, Wikimedia, Yahoo Finance (VIX, VIX3M, VVIX, MOVE, S&P 500, DAX, RSP, SPY, IEF and ratios of the S&P 500 members), FRED of the Federal Reserve Bank of St. Louis (STLFSI4, ICE BofA US High Yield Index Option-Adjusted Spread, margin loans from the Z.1 financial accounts, University of Michigan consumer sentiment (citation below)), US Department of the Treasury and Bureau of Labor Statistics. Weights and calculation: own calculation."
    override fun sgZitatUmich(datum: String) = "Surveys of Consumers, University of Michigan, University of Michigan: Consumer Sentiment © [UMCSENT], retrieved from FRED, Federal Reserve Bank of St. Louis; https://fred.stlouisfed.org/series/UMCSENT, (Accessed on $datum)."

    // 5.95 Lizenzen
    override val lizenzenTitel = "Open source licences"
    override val lizenzenUntertitel = "Libraries in the app and their licences"
    override val lizenzenHinweis = "The app contains libraries from other projects under their own licences. The licence texts are shown here in the original English."
    override val lizenzenFehlt = "The licence file is missing from this package. It is also in the source code as THIRD_PARTY_LICENSES.txt."
    // 5.95 Nasdaq
    override val nkQuelle = "Nasdaq"
    override val nkNichtVerfuegbar = "The calendar is not available right now. The app will try again the next time you open it."
    override val nkIpoFehlt = "The IPO calendar is not available right now. This page lists the prospectuses filed with the SEC in recent days."
    override fun nkIpoQuelle(finnhub: Boolean) = if (finnhub) "Nasdaq IPO calendar, with additions from Finnhub" else "Nasdaq IPO calendar"
    override fun nkQzQuelle(anbieter: String?) = when (anbieter) {
        "finnhub" -> "Nasdaq earnings calendar, with additions from Finnhub, dates from your portfolio and watchlist via Yahoo Finance"
        "alphavantage" -> "Nasdaq earnings calendar, with additions from Alpha Vantage, dates from your portfolio and watchlist via Yahoo Finance"
        else -> "Nasdaq earnings calendar, dates from your portfolio and watchlist via Yahoo Finance"
    }
    override val nkQzHinweis = "Well known means: portfolio, watchlist, stocks from the app's directory and companies with a market value of 10 billion dollars or expected quarterly revenue of 1 billion dollars, the eight largest per time slot. Dates can move, earnings per share are in dollars and mostly exclude one-off items. Sources: Nasdaq and Finnhub."
    override val nkDivTitel = "Dividend dates"
    override val nkDivIntro = "Stocks that trade without their next dividend on this day (ex-date), with the amount per share on the right."
    override fun nkZahltag(datum: String) = "Payment date $datum"
    override fun nkJeJahr(wert: String) = "$wert per year"
    override val nkDivKeine = "No dates"
    override fun nkDivKeineBekannten(n: Int) = "No well known stock, $n more under “All”"
    override val nkDivHinweis = "Well known means: portfolio, watchlist and stocks from the app's directory. The calendar covers stocks on US exchanges including preferred shares and funds, amounts in dollars per share. “Per year” is the sum of one year's payments according to the source. It is sorted by ex-date, the payment date is in the line. For splits, the day is when the stock starts trading at the new ratio."
    override val nkSplits = "Stock splits"
    override val nkSplit = "Split"
    override val nkZusammenlegung = "Reverse split"
    override fun nkSplitText(neu: Int, alt: Int) = (if (alt == 1) "1 share" else "$alt shares") + (if (neu == 1) " becomes " else " become ") + neu
    override val nkWiTitel = "Economic calendar"
    override val nkWiIntro = "These are the days when economic data like inflation, jobs and growth is published. The reported figure is on the right, with the experts' expectation below and the previous period's figure in the line."
    override val nkWichtige = "Key countries"
    override val nkGanztags = "all day"
    override fun nkVorher(wert: String) = "previous $wert"
    override val nkEuroraum = "Euro area"
    override val nkWiKeine = "No dates"
    override val nkWiHinweis = "Key countries are the United States, the euro area, Germany, the United Kingdom, Japan, China, Switzerland and France. Times are shown in the time zone of your device, days follow the calendar in New York. Figures can be revised later."

    // 5.95 Quellen
    override val q5Nicht = "Not available right now. Try again later."
    override val q5VxTitel = "VIX futures curve"
    override fun q5VxVerhaeltnis(q: String) = "First to second monthly contract: $q"
    override fun q5VxQuelle(datum: String) = "Source: Cboe Futures Exchange, settlement prices of $datum. © Cboe Exchange, Inc. VIX® is a registered trademark of Cboe Exchange, Inc."
    override val q5VxLink = "View settlement prices at the Cboe"
    override val q5VxMehr = "A futures contract fixes today the price at which the VIX is settled later. M1 expires on the next expiry date, M2 in the following month.\n• Contango: M2 more expensive than M1, the normal state\n• Backwardation: M1 above M2, investors look for immediate protection, usually during a sudden slump\n\nThe curve compares M1 with M2. A ratio above 1 means backwardation, from 0.95 the curve counts as flat, below that as contango."
    override val q5IgPrivat = "Investegate is intended for private investors only. Confirm that you are a private investor and will use the announcements only personally; only then does the app fetch them."
    override val q5IgJa = "I am a private investor"
    override val q5IgNein = "No"
    override val q5IgKeine = "At the moment Investegate lists no director dealings for this stock."
    override fun q5IgQuelle(quellen: String) = "Source: Investegate. The announcements come from $quellen."
    override val q5IgSeite = "All announcements of the company on Investegate"
    override fun q5IgQuelleMeldung(quelle: String) = "Source: Investegate, announcement via $quelle, reproduced unchanged."
    override val q5IgOeffnen = "Open the announcement on Investegate"
    override val q5IgAlleTitel = "Director dealings in the United Kingdom"
    override val q5IgAlleUnter = "New announcements about own share dealings by directors of British companies, via Investegate"
    override val q5IgQuelleAlle = "Source: Investegate, Directors Dealings category"
    override val q5IgKategorie = "All director dealings on Investegate"
    override val q5NnKeine = "No managers transactions at Nasdaq Nordic in the past year."
    override val q5NnQuelle = "Source: Nasdaq Nordic, company announcements in the Managers Transactions category"
    override val q5MnbKeine = "No managers' transactions at the MNB in the past year."
    override val q5MnbQuelle = "Source: Magyar Nemzeti Bank, Tőkepiaci közzétételek (kozzetetelek.mnb.hu). A tap opens the filing, where you open the PDFs yourself."
    override val q5EodTitel = "News via EODHD"
    override val q5EodKeine = "EODHD currently has no news for this security."
    override val q5EodQuelle = "News via EODHD with your key. One request costs 5 of 20 calls a day, reset at midnight GMT."
    override val q5EodLimit = "Today's EODHD requests are used up. They resume at midnight GMT."
    override val q5IgQuelleOhne = "Source: Investegate, reproduced unchanged."
    override val q5StandardArtikel = "Article from derStandard.at"
    // 5.95 Long/Short
    override fun opUsNotierung(kuerzel: String) = "Options trading of the US listing $kuerzel, prices in US dollars."

    // 5.95 Stimmrechte GB
    override val srTitel = "Major holdings notifications"
    override val srKeine = "Investegate currently lists no major holdings notifications for this share."
    override val srOhne = "Without the confirmation the app does not fetch notifications from Investegate. The major holdings notifications are on the company page at Investegate."
    override fun srNachher(p: String) = "now $p"
    override fun srVorher(p: String) = "before $p"
    override fun srUnter(p: String) = "below $p"
    override fun srAktien(p: String) = "shares $p"
    override fun srInstrumente(p: String) = "instruments $p"
    override fun srSchwelleUeber(p: String) = "reached $p threshold"
    override fun srSchwelleUnter(p: String) = "below $p threshold"
    override fun srStichtag(d: String) = "as of $d"
    override fun srPunkte(z: String) = "$z pts"
    override fun srQuelle(quellen: String) = "Source: Investegate, notifications via $quellen."
    override val srQuelleOhne = "Source: Investegate."
    override val srHinweis = "A TR-1 notification shows when a holder reaches 3% of the voting rights in a UK company and when it crosses each further whole percentage threshold. For companies from other countries the thresholds are 5, 10, 15, 20, 25, 30, 50 and 75%. The holding can consist of shares, of financial instruments such as options, or of both. The app derives the threshold from the holding before and after."

    // 5.96 Labor
    override val slRegelWaehlen = "Choose the rule shown in detail"
    override val slKeineLinie = "All lines are hidden. Tap a rule in the list to show its line again."
    override fun slLinieSchalter(regel: String) = "Show line $regel in the chart"

    // 5.96 Daten
    override fun hlUnter(p: String) = "below $p"
    override val hlUnveraendert = "unchanged"
    override fun hlMitNamen(n: Int, gesamt: Int) = "$n of $gesamt reported by name"
    override val hlGemeldet = "Reported by name"
    override val hlKeineNamen = "The source names no institutions."
    override val hlFonds = "Largest funds"
    override val hlFondsHinweis = "Funds are listed separately. Their stakes can already be part of the institutions above."
    override val vmWerte: String = "Assets"
    override val vmSchulden: String = "Debts"
    override val vmNetto: String = "Net"
    override val vmKeineSchulden: String = "none reported"
    override fun vmImMinus(spanne: String): String = "$spanne below zero"
    override fun vmPosten(werte: Int): String = "From $werte reported assets."
    override fun vmNettoOffen(schlecht: String?, gut: String?): String =
        "The ranges do not narrow down what remains after debts." +
            (gut?.let { " In the best case $it remain." } ?: "") +
            (schlecht?.let { " In the worst case debts exceed assets by $it." } ?: "")
    override val vmRangErklaerung: String =
        "Net worth per annual report, House only. Sorted by the middle of assets minus the middle of debts."

    // 5.96 Vergleich
    override fun vgMax(n: Int) = "Up to $n comparisons at once. Tap a chosen comparison to remove it."
    override val vgSuche = "Search for a stock, ETF, investor or person"
    override val vgAktienEtfs = "Stocks, ETFs and indices"
    override val vgPolitiker = "Politicians"
    override val vgPolitikerMehr = "Find more people with the search."
    override val vgLabor = "Rules from the strategy test"
    override fun vgLaborUnter(name: String) = "applied to $name"
    override val vgHaus = "House of Representatives"
    override val vgSenat = "Senate"
    override val vgRegierung = "US administration"
    override val vgKeineTreffer = "No results."
    override fun vgZeitraum(von: String, bis: String) = "Common period: $von to $bis."
    override val vgHeute = "today"
    override fun vgKuerzer(name: String, ab: String) = "Shorter than chosen because data for $name only starts on $ab."
    override val vgLaedt = "loading"
    override val vgKeineDaten = "no data"
    override val vgZuKurz = "history too short"
    override fun vgGeschaefte(n: Int) = if (n == 1) "only 1 trade" else "only $n trades"
    override val vgWaehrungHinweis = "Each line shows the performance in its own currency, just like the stock itself. Histories of investors and politicians are estimates from their filings."
    override val vgPlanNote = "Executed on the first trading day of each month at the closing price, without costs and taxes. Dividends are reinvested."
    override val vgGleicheRaten = "Each comparison gets the same instalments over the same period. For portfolio histories the first value of the month counts as the purchase price."
    override val vgLaborHinweis = "A rule from the strategy test trades this stock on its signals, with the spread and a €1 fee per order as in the strategy test."
    override fun vgOhneDaten(namen: String) = "No data: $namen."
    override fun vgWenigGeschaefte(namen: String) = "Too few trades for a history: $namen."
    override val vgVergleichIntro = "All lines start at zero at the beginning of the common period. Use “Compare” to add more securities and your portfolio."
    override val vgImZeitraum = "Over the same period"

    // 5.96 Feed
    override val feedErstellen = "Create your own feed"
    override val feedBearbeiten = "Edit feed"
    override val feedHinweis = "An own feed gathers the headlines of the chosen sources in its own category, newest first. Own feed addresses are addresses of RSS or Atom feeds, which the app checks when you add them."
    override val feedName = "Feed name"
    override val feedZugaenge = "Your own feed addresses"
    override val feedQuellenApp = "Sources in the app"
    override val feedPruefen = "Check and add"
    override fun feedZugangOk(titel: String, n: Int) = "Added “$titel”, $n headlines"
    override val feedNichtErreichbar = "The address does not respond or returns an error."
    override val feedKeinFeed = "There is no readable RSS or Atom feed at this address."
    override fun feedFehler(f: de.troetenrepublik.app.engine.EigenerFeed.Fehler) = when (f) {
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.LEER -> "Please enter an address."
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.SCHEMA -> "Only addresses with http:// or https:// are possible."
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.ZUGANGSDATEN -> "The address contains login details such as a name, a password or a key. The app does not store login details."
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.UNGUELTIG -> "This address is not valid."
    }
    override fun feedSchonInApp(name: String) = "This address belongs to the app source $name. It is now ticked."
    override val feedSchonDrin = "This address is already in the list."
    override val feedQuelleAus = "switched off under sources"
    override fun feedAusgeschaltet(namen: String) = "Switched off under sources: $namen"
    override val feedLeer = "The chosen sources have no headlines right now."
    override val feedOhneQuelle = "Choose at least one source or add a feed address."
    override val feedLoeschen = "Delete feed"
    override fun feedLoeschenFrage(name: String) = "Delete “$name”"
    override val feedLoeschenText = "The category disappears from the news page. The sources themselves stay."
    override fun feedZuViele(n: Int) = "At most $n own feeds."
    override fun feedMeldeOption(stufe: String) = when (stufe) {
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE -> "Own feeds and important news"
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE -> "Own feeds only"
        de.troetenrepublik.app.data.MITTEILUNG_QUELLEN -> "Chosen sources only"
        else -> ""
    }
    override fun feedMeldeOptionHint(stufe: String) = when (stufe) {
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE -> "New headlines from your own feeds plus the highlighted important news from all sources"
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE -> "Only new headlines from your own feeds that have notifications switched on"
        de.troetenrepublik.app.data.MITTEILUNG_QUELLEN -> "Only new headlines from the sources ticked below"
        else -> ""
    }
    override val feedMelden = "Notify about new headlines"
    override val feedMeldenHint = "Applies to the notification settings “Own feeds only” and “Own feeds and important news”."
    override val feedMeldeQuellen = "Sources for notifications"
    override val feedOhneFeeds = "No own feed yet. The + symbol on the news page creates one."
    override val feedBuendel = "More than three new headlines at once arrive as one notification."

    // 5.96 Kongress
    override val copyFormer = "Former members"
    override val copyMitgliederQuelle = "Members: unitedstates/congress-legislators (CC0), checked once a day. Former members stay here until 180 days after leaving and after their last filing, then through search."
    override fun kammerZeitraum(senat: Boolean, von: Int, bis: Int) = (if (senat) "former senator, " else "former member of the House, ") + amtZeitraum(von, bis)
    override fun amtZeitraum(von: Int, bis: Int) = if (von == bis) "$von" else "$von to $bis"

    // 5.97 Aufklappen
    override val aufZahlenHerkunft = "Where the numbers come from"

    // 5.97 Finanzen
    override val finDivTitel = "Dividend"
    override val finDivJeAktie = "Per share in 12 months"
    override val finDivExTag = "Next ex-date"
    override val finDivZahltag = "Next payment"
    override fun finDivBestand(stueck: String) = "Your $stueck shares per year, before tax"
    override val finVerlust = "Loss"
    override val trErkReddit = "Reddit: mentions according to ApeWisdom."
    override val trErkRedditDeutsch = "German subs: the app reads the newest posts through your Reddit access and counts how often the security is named."
    override fun trErkVerlauf(seit: String?) = "Week, month and year come from the history the app has recorded " + (if (seit != null) "since $seit." else "from now on.")
    override val trErkBeitraege = "Reddit posts: search for ticker and name in stock market subreddits through your Reddit access, last month."
    override val trErkYahoo = "Yahoo Finance: list of the most viewed securities in your region and in the US."
    override val trErkWiki = "Wikipedia: views of the article over the last 60 days without bots (Wikimedia Foundation, CC0). The percentage compares the last 7 days with the 4 weeks before."
    override val trErkHn = "Hacker News: stories with the company name in the last 7 days, compared with the weekly average of the 4 weeks before. Appears only for technology and communication stocks."
    override fun trErkMastodon(tag: String) = "Mastodon: posts with #$tag on mastodon.social in the last 7 days."
    override val trErkBluesky = "Bluesky: posts with the company name in the last 7 days and in the week before, through your app password."

    // 5.97 Quartalszahlen
    override fun qzUhrKurz(art: Int, von: String?, bis: String?): String? = when (art) {
        0 -> von?.let { "at $it" }
        1 -> bis?.let { "before $it" }
        2 -> if (von != null && bis != null) "$von to $bis" else null
        3 -> von?.let { "from $it" }
        else -> null
    }
    override fun qzUhrLang(art: Int, von: String?, bis: String?): String = when (art) {
        0 -> if (von != null) "at $von" else "time not known yet"
        1 -> "before the open" + (bis?.let { " (before $it)" } ?: "")
        2 -> "during trading" + (if (von != null && bis != null) " ($von to $bis)" else "")
        3 -> "after the close" + (von?.let { " (from $it)" } ?: "")
        else -> "time not known yet"
    }
    override fun qzZeitTag(zeit: String, versatz: Int) = when {
        versatz == 1 -> "$zeit next day"
        versatz == -1 -> "$zeit previous day"
        versatz > 1 -> "$zeit, $versatz days later"
        versatz < -1 -> "$zeit, ${-versatz} days earlier"
        else -> zeit
    }
    override val qzGeschaetzt = "date estimated"
    override fun qzZeitzone(zone: String) = "Times in your device's time zone ($zone)"
    override val qzbZurAktie = "Open stock"
    override fun qzbKalenderNotierung(symbol: String) = "Listing in the calendar: $symbol"
    override val qzbKalenderNotierungText = "The calendar lists the US listing. For foreign companies this is often a depositary receipt (ADR), whose earnings per share can differ from the home listing."
    override fun qzbQuartal(quartal: String) = "Quarter $quartal"
    override val qzbTermin = "Date"
    override val qzbTag = "Day"
    override val qzbUhrzeit = "Time"
    override val qzbKonferenz = "Company conference call"
    override fun qzbTerminSpanne(von: String, bis: String) = "between $von and $bis"
    override val qzbGeschaetztText = "The company has not confirmed the day yet. The sources estimate it from previous years."
    override val qzbUhrText = "Many companies report shortly before the open or shortly after the close. The app converts the time into your device's time zone, the exact moment can differ."
    override val qzbZahlen = "What gets reported"
    override val qzbGewinn = "Earnings per share"
    override val qzbUmsatz = "Revenue"
    override fun qzbErwartetVon(quelle: Int, analysten: Int?) = when (quelle) {
        0 -> "expected per calendar" + (analysten?.let { ", $it estimates" } ?: "")
        else -> "expected per Yahoo Finance" + (analysten?.let { ", $it analysts" } ?: "")
    }
    override val qzbSpanne = "Range of estimates"
    override fun qzbVonBis(von: String, bis: String) = "$von to $bis"
    override val qzbVorjahr = "Same quarter last year"
    override val qzbGemeldet = "reported"
    override val qzbAusblick = "Outlook"
    override val qzbAusblickText = "Many companies also give an expectation for the next quarter or the full year with their results. The app's sources have no figures for it. The outlook is in the company's release."
    override val qzbZahlenText = "Earnings per share are the quarter's profit divided by the number of shares, mostly the adjusted figure without one-off items. Revenue is everything the company took in from its customers in the quarter."
    override val qzbKeineErwartung = "The sources give no estimates for this report."
    override val qzbBewertung = "How the figures are judged"
    override fun qzbGewinnWachstum(a: String, b: String, p: String) = "Analysts expect earnings per share of $a after $b in the same quarter last year, $p more."
    override fun qzbGewinnRueckgang(a: String, b: String, p: String) = "Analysts expect earnings per share of $a after $b in the same quarter last year, $p less."
    override fun qzbGewinnAusVerlust(a: String, b: String) = "Analysts expect earnings per share of $a after a loss of $b per share in the same quarter last year."
    override fun qzbVerlustErwartet(a: String, b: String?) = "Analysts expect a loss of $a per share" + (b?.let { ", after $it in the same quarter last year" } ?: "") + "."
    override fun qzbUmsatzVeraenderung(a: String, b: String, p: String) = "For revenue the expectation is $a after $b in the same quarter last year ($p)."
    override fun qzbSpanneWeit(a: String, b: String, n: Int?) = "The estimates" + (n?.let { " of the $it analysts" } ?: "") + " range from $a to $b. This wide range shows that the analysts disagree."
    override fun qzbSpanneEng(a: String, b: String, n: Int?) = "The estimates" + (n?.let { " of the $it analysts" } ?: "") + " are close together, from $a to $b."
    override fun qzbSchaetzungHoch(a: String, b: String, p: String, n: Int?, m: Int?) = "Over the last 30 days the estimate rose from $b to $a ($p)." + revisionen(n, m)
    override fun qzbSchaetzungRunter(a: String, b: String, p: String, n: Int?, m: Int?) = "Over the last 30 days the estimate fell from $b to $a ($p)." + revisionen(n, m)
    private fun revisionen(n: Int?, m: Int?) = if (n == null || m == null || n + m == 0) "" else " $n analysts raised it, $m lowered it."
    override fun qzbSchaetzungGleich(a: String) = "Over the last 30 days the estimate stayed about the same ($a)."
    override fun qzbTreffer(n: Int, m: Int, p: String) = "In $n of the last $m quarters earnings per share came in above the expectation. The difference averaged $p."
    override fun qzbGemeldetUeber(a: String, b: String, p: String) = "The company reported $a per share against an expected $b, $p above the expectation."
    override fun qzbGemeldetUnter(a: String, b: String, p: String) = "The company reported $a per share against an expected $b, $p below the expectation."
    override fun qzbGemeldetWie(a: String, b: String) = "The company reported $a per share against an expected $b, about as expected."
    override val qzbBewertungText = "On the stock market the comparison with expectations counts most, because prices already contain them beforehand. If earnings per share or revenue come in above, people speak of a positive surprise, below of a disappointment. The comparison with the same quarter last year shows growth. The company's outlook often moves the price more than the figures themselves, so a price can fall even after good results."
    override val qzbKeineBewertung = "The sources lack estimates or prior year figures for a comparison."
    override val qzbVerlauf = "Recent quarters"
    override fun qzbQuartalBis(datum: String) = "Quarter to $datum"
    override val qzbVonWem = "Where the estimates come from"
    override fun qzbQuelleKalender(anbieter: String?, schaetzungen: Int?) = "Nasdaq earnings calendar" + when (anbieter) {
        "finnhub" -> ", with additions from Finnhub"
        "alphavantage" -> ", with additions from Alpha Vantage"
        else -> ""
    } + (schaetzungen?.let { ": consensus of $it estimates" } ?: "")
    override fun qzbQuelleYahoo(gewinn: Int?, umsatz: Int?) = "Yahoo Finance" + listOfNotNull(
        gewinn?.let { "$it analysts on earnings" }, umsatz?.let { "$it on revenue" },
    ).joinToString(" and ").let { if (it.isEmpty()) "" else ": $it" } + ", plus recent quarters"
    override fun qzbHaeuser(namen: String) = "Firms that rated the stock in the last 90 days: $namen"
    override val qzbVonWemText = "The estimates come from analysts at banks and research firms. Data services collect them and form the average, the consensus. The services count different numbers of analysts, so their figures differ slightly."
    override val qzbYahooFehlt = "Yahoo Finance is not providing further estimates right now."
    // 0.68 Finanzen
    override val finTippHinweis = "Tap a term to see what it means."
    override fun finErklaerung(label: String): String? = when (label) {
        revenue -> "All income from goods and services, before costs."
        grossProfit -> "Revenue minus the direct cost of the goods."
        operatingIncome -> "Profit from the day-to-day business, before interest and taxes."
        ebitda -> "Earnings before interest, taxes, depreciation and amortization."
        netIncome -> "What is left after all costs, interest and taxes. Negative means a loss."
        totalAssets -> "Everything the company owns."
        totalLiabilities -> "Everything the company owes to others."
        equity -> "Total assets minus liabilities. Belongs to the owners on paper."
        finLangfristSchulden -> "Loans and bonds due in more than a year."
        operatingCashflow -> "Cash that actually comes in from the day-to-day business."
        finInvestitionen -> "Spending on buildings, machinery and equipment. Usually negative because cash flows out."
        earningsGrowth -> "Earnings in the latest quarter against the same quarter a year earlier."
        finDivJeAktie -> "Dividends per share over the last twelve months."
        toolDividendGrowth -> "Average yearly rise of the dividend per share."
        finDivExTag -> "Holding the share the day before gets you the next dividend."
        finDivZahltag -> "Day the next dividend is paid."
        actual -> "Reported earnings per share for the quarter."
        estimate -> "Earnings per share analysts expected on average."
        finAbweichung -> "Reported against expected, in percent. Triangle up means beaten, down means missed. Up to 2 % without color."
        else -> null
    }
    override val finUmsatzSchaetzungErklaerung = "Revenue analysts expect for the next quarter. Percent against the same quarter a year earlier."
    override val finQuartal = "Quarter"
    override val finJahr = "Year"
    override val finAbweichung = "Surprise"
    // 0.68 Stimmung und Lager
    override val sgKurz = "• Each part from 0 to 100, 100 means a lot of greed\n" +
        "• Below 25 extreme fear, up to 45 fear, up to 55 neutral, up to 75 greed, above that extreme greed\n" +
        "• Extreme values mean one sided sentiment. They do not show when it turns"
    override val sgRechnungTitel = "How it is calculated"
    override val sgRechnung = "• Mostly the rank of today’s value among its own past values counts, otherwise fixed bounds\n" +
        "• Related measures form a group and do not count twice\n" +
        "• Each group has a fixed weight that its parts share\n" +
        "• If a part or group is missing, the others count correspondingly more"
    override val sgBarometerTitel = "What the gauges measure"
    override val sgTeileTitel = "All parts in detail"
    override fun fiRichtung(schluessel: String, mehr: Boolean) =
        if (schluessel == "duerre") (if (mehr) "more drought →" else "← less drought") else if (mehr) "fuller →" else "← tighter"
    override fun fiLegendeSchnitt(wert: String) = "Average of those years: $wert"
    override fun fiLegendeStand(datum: String, wert: String) = "As of $datum: $wert"
    override fun smMarkt(symbol: String) = when (symbol) {
        "CL=F" -> "WTI crude"; "RB=F" -> "Gasoline (RBOB)"; "HO=F" -> "Heating oil"; "NG=F" -> "US gas (Henry Hub)"; "TTF=F" -> "TTF gas"
        "ZC=F" -> "Corn"; "ZW=F" -> "Wheat"; "ZS=F" -> "Soybeans"; "JETS" -> "Airline stocks vs S&P 500"; else -> symbol
    }
    override fun smSeit(datum: String, markt: String, bewegung: String) = "Since the release around $datum: $markt $bewegung"
    override fun smLauf(lauf: Int) = when (lauf) {
        0 -> "Moved with the report, likely partly priced in"
        1 -> "Moved against the report"
        2 -> "Barely moved"
        else -> "Report without clear direction"
    }
    override val smNichtMessbar = "Not measurable, no matching market price"
    override val smKeineKurse = "Not measurable right now, prices missing"
    override fun smNochNicht(datum: String) = "No trading day yet since the release around $datum"

    // 0.68 Strategietest
    override fun slRegelKurz(art: String, a: Int, b: Int, n: Int) = when (art) {
        "HALTEN" -> "Buy and hold"
        "SMA" -> "$a-day line"
        "KREUZ" -> "Cross $a/$b"
        "RSI" -> if (n == 14) "RSI $a/$b" else "RSI $n days $a/$b"
        "MACD" -> "MACD signal"
        "DONCHIAN" -> "Breakout $a/$b"
        "BOLLINGER" -> "Bollinger"
        else -> art
    }

    // 0.68 Suchverlauf
    override val svKuerzlich = "Recently searched"
    override val svLoeschen = "Clear history"
    override fun svEntfernen(name: String) = "Remove $name from history"

    // 0.68 Tagesrueckblick und Mitteilungen
    override val rbUhrzeit = "Time"
    override val rbUhrzeitHinweis = "In the morning it covers the previous day."
    override fun rbEinleitungDepot(ton: String, hoch: Boolean, prozent: String) = "$ton: your portfolio " + (if (hoch) "rose" else "fell") + " $prozent"
    override val rbVeraenderung = "Change"
    override val rbWert = "Value"
    override val mtAlle = "All notifications"
    override fun mtProfil(an: Boolean, aktiv: Int, gesamt: Int) = if (!an) "Off" else "$aktiv of $gesamt types on"
    override val mtAlleAus = "All notifications are off."
    override val mtDepot = "Portfolio and orders"
    override val mtDepotHinweis = "Orders, savings plans, knock-outs, bets, dividends, interest."
    override val mtAlarme = "Prices and alerts"
    override val mtAlarmeHinweis = "Price alerts, finder alerts, pricier funds, investment theses."
    override val mtNewsHinweis = "You choose the sources on the news page."
    override val mtCopy = "Insiders, politics and agencies"
    override val mtCopyHinweis = "People with a bell in Copytrading."

    // 0.68 ipo
    override fun ipoSchritt(i: Int) = listOf("Filed", "Priced", "Trading").getOrElse(i) { "" }
    override val ipoSchrittZurueck = "Withdrawn"
    override val ipoPreisSpanne = "Price range"
    override val ipoSpanneMitte = "Midpoint"
    override val ipoSeitAusgabeTitel = "Since issue"
    override fun ipoKursJetzt(p: String) = "Price $p"
    override fun ipoAusgabe(p: String) = "Issue $p"
    override fun ipoPunktKurz(merkmal: Int) = if (merkmal == 0) "Shell company (SPAC)" else "Small offering"
    override fun ipoPunktErklaerung(merkmal: Int) = if (merkmal == 0) "It raises money to buy a company that is not yet known. Many SPAC shares fell sharply after the merger."
        else "Such stocks get little attention and often swing a lot."
    override val ipoSchlagzeilen = "Headlines"
}
