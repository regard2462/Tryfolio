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
    override val widgetGesperrt = "Locked"
    override val widgetLeer = "No stocks in the watchlist"
    override fun widgetStand(zeit: String) = "As of $zeit"
    override val widgetsTitel = "Widgets"
    override val widgetsText = "• Add: long-press a category › As widget, or long-press the home screen › Widgets › Tryfolio.\n• Adjust: long-press the widget › widget settings."
    override val widgetsAuswahl = "Stocks in widgets showing the watchlist (without a choice the whole watchlist)"
    override val widgetsNurAndroid = "Home screen widgets are available in the Android app."
    override val screenshotsTitel = "Screenshots"
    override fun screenshotsOption(modus: String) = when (modus) { "erlaubt" -> "Allowed everywhere"; "gesperrt" -> "Blocked everywhere"; else -> "Allowed, except keys" }
    override fun screenshotsHinweis(modus: String) = when (modus) {
        "erlaubt" -> "Also on the page with the API keys, where they are visible. Block them again afterwards."
        "gesperrt" -> "No screenshots, no screen recordings, no picture in the recent apps list."
        else -> "Default: screenshots everywhere, except on the page with the API keys."
    }
    override val widgetBestand = "Holdings"
    override val widgetMover = "Top & flop today"
    override val widgetsAkku = "If widgets do not update while the app is closed, battery optimization is the cause. In the app settings under Battery choose “Unrestricted”. On Samsung, also remove the app from the lists of sleeping apps."
    override val widgetsAkkuOeffnen = "Open app settings"
    override val widgetDividenden = "Dividends"
    override val widgetKeinePositionen = "No holdings in the portfolio"
    override val widgetMoverRuhig = "No moves yet today"
    override val widgetDivKeinBestand = "No stocks or ETFs in the portfolio"
    override val widgetDivKeine = "No dividends expected in the next twelve months"
    override fun widgetDivErhalten(jahr: Int) = "Received $jahr"
    override val widgetDivErwartet = "Expected in 12 months"
    override val widgetGrundDafuer = "for"
    override val widgetGrundDagegen = "against"
    override val widgetFinderLeer = "Rank once in the stock finder"
    override val widgetCopyLeer = "Not following anyone, ranking not calculated yet"
    override val widgetOrdersLeer = "No open orders and no savings plans"
    // Seit 0.77: Widgets zu Unterseiten und Werkzeugen
    override val widgetBlasenTitel = "Bubble risk"
    override fun widgetBlasenStufe(stufe: Int) = listOf("Low", "Elevated", "High").getOrElse(stufe) { "" }
    override fun widgetBlasenRot(rot: Int, gesamt: Int) = "$rot of $gesamt signs red"
    override val widgetTermineTitel = "Dates"
    override val widgetTermineLeer = "No dates in the next days"
    override fun widgetFwHaupt(rot: Int, gelb: Int, n: Int) = when {
        rot == 0 && gelb == 0 -> "All $n green"
        rot == 0 -> "$gelb yellow"
        gelb == 0 -> "$rot red"
        else -> "$rot red · $gelb yellow"
    }
    override fun widgetPhase(art: String, korrektur: Boolean) = when {
        art == "BAER" -> "Bear market"
        korrektur -> "Correction"
        else -> "Bull market"
    }
    override fun widgetLageAuffaellig(n: Int) = if (n == 0) "Nothing notable" else "$n notable"
    override val widgetEinJahr = "1 year"
    override val widgetZinsfuss = "IRR per year"
    override val widgetStufeKritisch = "Critical"
    override val widgetStufeAchtung = "Caution"
    override val widgetCheckGruen = "in the green"
    override val widgetKoerbePlus = "Themes up"
    override val widgetSektorenPlus = "Sectors up"
    override fun widgetFlussZaehlung(kaeufe: Int, verkaeufe: Int) = (if (kaeufe == 1) "1 buy" else "$kaeufe buys") + ", " + (if (verkaeufe == 1) "1 sell" else "$verkaeufe sells")
    override val widgetFlussNetto = "Net, 90 days"
    override val widgetFlussLeer = "No trades"
    override val widgetStillhalterLeer = "Open “Sell options” in the search first"
    override val widgetSignaleLeer = "No markers"
    override val widgetVolaLeer = "No options data"
    override val widgetWertWaehlen = "Choose a stock in settings"
    override val widgetGroessteAbweichung = "Largest deviation"
    override val widgetPlaeneLeer = "No savings plans"
    override val widgetWettenLeer = "No open bets"
    override val widgetCopyNiemand = "Not following anyone yet"
    override val widgetCopyRegierung = "Open government in the app"
    override val widgetSeite = "Page"
    override val widgetAktuellesUniversum = "Last loaded universe"
    override val widgetDurchbruchLeer = "No new breakthroughs"
    override val widgetRendite = "Return"
    override val widgetRueckgang = "Largest drawdown"
    override val widgetWissenFertig = "All lessons completed"
    override fun widgetWissenMinuten(n: Int) = "$n min"
    override fun widgetWissenModul(nr: Int, titel: String) = "Module $nr · $titel"
    override fun widgetWissenStand(fertig: Int, gesamt: Int) = "$fertig of $gesamt lessons"
    override fun widgetManuell(name: String) = "Your home screen does not add widgets directly. Long-press the home screen, then Widgets › Tryfolio › $name."
    override val widgetsHinzufuegen = "Add widget"
    override val widgetEinzelName = "Single stock"
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
    override val widgetUntertitel = "Subtitle"
    override val widgetAnzeige = "Display"
    override val widgetBetrag = "Change as amount"
    override val widgetBarometer = "Gauge"
    override val widgetAlle = "All"
    override val widgetGlobusEinfaerbung = "Coloring"
    override val widgetGlobusMitte = "View center"
    override val widgetMitteEuropa = "Europe"
    override val widgetMitteAmerika = "Americas"
    override val widgetMitteAsien = "Asia"
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
    override val sperreText = "Protects the app with a PIN or password. Portfolio, transactions, orders, savings plans, watchlist, price alerts and notes are then stored on the device encrypted with AES-256-GCM. The app locks at startup and after one minute in the background. Settings and public data such as prices are not encrypted."
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
    override val chartVergleichVoll = "You can compare at most 8."
    override val chartIndikatorenKurs = "On the price chart"
    override val chartIndikatorenUnten = "Below the chart"
    override fun chartIndikatorName(key: String) = when (key) {
        "trend" -> "Trend from highs and lows"
        "sma20" -> "SMA 20"; "sma50" -> "SMA 50"; "sma200" -> "SMA 200"; "ema20" -> "EMA 20"; "ema50" -> "EMA 50"
        "bb" -> "Bollinger Bands"; "keltner" -> "Keltner Channel"; "donchian" -> "Donchian Channel"; "ichimoku" -> "Ichimoku"
        "sar" -> "Parabolic SAR"; "vwap" -> "VWAP"; "volumen" -> "Volume"; "rsi" -> "RSI 14"; "macd" -> "MACD"
        "stoch" -> "Stochastic"; "atr" -> "ATR 14"; "obv" -> "OBV"; "cci" -> "CCI 20"; "williams" -> "Williams %R"
        "mfi" -> "MFI 14"; "adx" -> "ADX 14"; "roc" -> "Momentum (ROC 12)"; "aroon" -> "Aroon 25"; "cmf" -> "Chaikin Money Flow"
        "squeeze" -> MarktzeichenTexte.text(false, "sq.name", emptyList())
        else -> key
    }
    override fun chartIndikatorInfo(key: String) = when (key) {
        "trend" -> "Highs and lows of at least two average ranges (ATR). Both rising means an uptrend. The line shows where it breaks."
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
        "squeeze" -> MarktzeichenTexte.text(false, "sq.info", emptyList())
        else -> ""
    }
    override fun chartWerkzeug(key: String) = when (key) {
        "linie" -> "Trendline"; "waagerecht" -> "Horizontal line"; "notiz" -> "Note"; "emoji" -> "Mark"
        "zurueck" -> "Undo"; else -> "Clear all"
    }
    override fun chartAllesLoeschen(name: String) = "Delete all drawings for $name?"
    override val chartKeineDaten = "No price data for this period."
    override val heatmap = "Heatmap"
    override val heatmapDepot = "My portfolio"
    override val heatmapMarkt = "Stock market"
    override val heatmapBranchen = "By sector"
    override val heatmapRegionen = "By region"
    override fun heatmapSteigen(n: Int) = "$n up"
    override fun heatmapFallen(n: Int) = "$n down"
    override val heatmapLeer = "No prices reachable right now. Try again later."
    override val globusTitel = "Globe"
    // [Globus Gesamtbild 0.70]
    override val globusGesamt = "Overall"
    override val ggTitel = "Overall picture"
    override fun ggPunkteVon(punkte: Int) = "$punkte of 100"
    override fun ggPlatz(platz: Int, von: Int) = "Rank $platz of $von"
    override fun ggBereich(b: de.troetenrepublik.app.engine.GlobusGesamt.Bereich) = when (b) {
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.WOHLSTAND -> "Prosperity"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.WACHSTUM -> "Growth"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.STABILITAET -> "Stability"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.REGIERUNG -> "Governance"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.SICHERHEIT -> "Security"
    }
    override fun ggZutat(z: de.troetenrepublik.app.engine.GlobusGesamt.Zutat) = when (z.art) {
        de.troetenrepublik.app.engine.GlobusGesamt.Art.BIP_KOPF -> "GDP per person"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.LEBENSERWARTUNG -> "Life expectancy"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.WACHSTUM -> "Avg. growth ${z.von} to ${z.bis}"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.INFLATION -> "Inflation"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.SCHULDEN -> "Government debt"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.HAUSHALT -> "Budget balance"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.ARBEITSLOS -> "Unemployment"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.LEISTUNG -> "Current account"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.WGI -> rkWgiName(z.kennung)
        de.troetenrepublik.app.engine.GlobusGesamt.Art.KRIEG -> "War in the country"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.KONFLIKT -> "Armed conflict in the country"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.SANKTION_EU -> "EU sanctions regimes"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.SANKTION_US -> "US sanctions programs"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.REISE -> "US travel advisory, level ${z.wert.toInt()}"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.KEINE_ABZUEGE -> "No deductions"
    }
    override fun ggDesBip(wert: String) = "$wert of GDP"
    override val ggInfo = "Points from 0 to 100, a weighted average of five areas: prosperity and governance 25 percent each, stability and security 20 each, growth 10.\n• Prosperity: GDP per person at purchasing power, life expectancy\n• Growth: real growth, averaged from two years before to two years after\n• Stability: inflation, government debt, budget balance, unemployment, current account\n• Governance: six World Bank indicators\n• Security: deductions for war or conflict in the country, EU and US sanctions, US travel advisory\n\nA missing area counts with the average of the others, at most 70 points. A country needs at least three areas, including one of the first three, otherwise it gets no points. With exactly three areas at most 70 points count.\n\nWithout IMF figures, the latest values from the World Bank, the UN or the CIA World Factbook apply, at most five years old and shown with their year.\n\nThe year slider moves the IMF figures. Governance, security and life expectancy always show the latest data."
    override val globusBoerse = "Markets today"
    override val globusWachstum = "Growth"
    override val globusAusblick = "Outlook"
    override val globusInflation = "Inflation"
    override val globusKeineDaten = "No data"
    override val globusFehltTeil = "IMF data only partly loaded."
    override val globusNeuLaden = "Reload"
    override val globusFehlt = "IMF data not loaded."
    override val boerseMoskau = "Moscow Exchange"
    override fun globusWachstumJahr(jahr: Int) = "Growth $jahr"
    override fun globusInflationJahr(jahr: Int) = "Inflation $jahr"
    override val globusArbeitslos = "Unemployment"
    override val globusSchulden = "Government debt"
    override val globusLeistung = "Current account"
    override fun globusHinweis(art: de.troetenrepublik.app.engine.Imf.Art, a: String, b: String) = when (art) {
        de.troetenrepublik.app.engine.Imf.Art.REZESSION -> "Recession expected: $a"
        de.troetenrepublik.app.engine.Imf.Art.REZESSION_FOLGEJAHR -> "Recession expected next year: $a"
        de.troetenrepublik.app.engine.Imf.Art.ZIEHT_AN -> "Growth picks up: $a to $b"
        de.troetenrepublik.app.engine.Imf.Art.SCHWAECHT_AB -> "Growth slows: $a to $b"
        de.troetenrepublik.app.engine.Imf.Art.STAGNATION -> "Stagnation: $a growth"
        de.troetenrepublik.app.engine.Imf.Art.DEFLATION -> "Falling prices: $a"
        de.troetenrepublik.app.engine.Imf.Art.HOHE_INFLATION -> "High inflation: $a"
        de.troetenrepublik.app.engine.Imf.Art.ERHOEHTE_INFLATION -> "Elevated inflation: $a"
        de.troetenrepublik.app.engine.Imf.Art.HOHE_ARBEITSLOSIGKEIT -> "High unemployment: $a"
        de.troetenrepublik.app.engine.Imf.Art.HOHE_SCHULDEN -> "High government debt: $a of GDP"
        de.troetenrepublik.app.engine.Imf.Art.UEBERSCHUSS -> "Current account surplus: $a of GDP"
        de.troetenrepublik.app.engine.Imf.Art.DEFIZIT -> "Current account deficit: $a of GDP"
    }
    override val globusWelt = "World economy"
    override val wwBalkenTitel = "World growth per year"
    override val wwPrognose = "Forecast"
    override fun wwSchnittLabel(bis: Int) = "Avg. per year to $bis"
    override fun wwGesamtLabel(bis: Int) = "Growth to $bis"
    override fun wwSchnittInfo(von: Int, bis: Int) = "Average yearly growth of the world economy from ${von + 1} to $bis in the IMF forecast (real output, without price increases)."
    override fun wwGesamtInfo(von: Int, bis: Int) = "How much the real world economy grows from $von to $bis if the forecast holds. The yearly rates compound."
    override val wwTreiberTitel = "IMF rationale"
    override val wwStand = "IMF outlook October 2026"
    override val wwTragenTitel = "Who carries the growth"
    override fun wwTragenInfo(von: Int, bis: Int, welt: String) =
        "Contribution of a region to world growth: its growth times its share of world output (purchasing power), averaged over ${von + 1} to $bis, in percentage points per year.\n\nTogether they make up the world average of $welt per year."
    override val wwUebrige = "Rest"
    override fun wwBeschleunigt(raum: String, nun: String, frueher: String) = "$raum speeds up: avg. $nun instead of $frueher over the last five years"
    override fun wwSchwaecht(raum: String, nun: String, frueher: String) = "$raum slows: avg. $nun instead of $frueher over the last five years"
    override val wwAufsteigerTitel = "Risers"
    override val wwRezTitel = "Recession"
    override val wwTreiber = listOf(
        "War in West Asia: energy stays expensive (oil averaging 96 dollars a barrel in 2026), Hormuz assumed to be open again in the forecast",
        "AI investment and productivity gains support growth, countries in the tech supply chain benefit",
        "Energy importers outside the tech chain have the weakest outlook",
        "Risks: further escalation, a reassessment of AI, renewed trade disputes, high public debt",
    )
    override val wwQuelle = "IMF World Economic Outlook, October 2026"
    override val wwAnteileTitel = "Share of world GDP"
    override val wwAnteileInfo = "Share of global output in purchasing power (IMF) per region for 2000, today and the last forecast year. The euro area adds up today's members, also for earlier years."
    override val wwEuroraum = "Euro area"
    override fun wwRaum(code: String, sonst: String) = when (code) {
        "CHN" -> "China"; "USA" -> "USA"; "IND" -> "India"; "JPN" -> "Japan"; else -> sonst
    }
    override fun wwVerschiebung(gewinner: String, gewinn: String, verlierer: String?, verlust: String?, bis: Int) =
        "By $bis $gewinner gains the most weight ($gewinn points)" + (if (verlierer != null && verlust != null) ", $verlierer loses the most ($verlust points)." else ".")
    override fun wwSchnellsteTitel(bis: Int) = "Fastest growth to $bis (avg. per year)"
    override fun wwPlaetzeTitel(bis: Int) = "Climbing the ranking by $bis"
    override fun wwPlatz(von: Int, bis: Int) = "Rank $von → $bis"
    override val globusAbschwaecher = "Slowing"
    override val globusRisiken = "Risks"
    override fun globusHoheInflation(n: Int, namen: String) = "Inflation above 10% in $n countries, highest: $namen"
    override fun globusSchuldenSchwach(n: Int, namen: String) = "Debt above 100% of GDP with growth below 1.5% in $n countries: $namen"
    override val globusFruehwarn = "Early warning signs (US)"
    // [Globus Wirtschaft]
    override val globusWirtschaft = "Economy"
    override val globusStruktur = "Economic structure"
    override val globusMarkt = "Market"
    override val globusEtf = "Country ETFs"
    override val globusEtfHinweis = "Country ETFs trade in US dollars, the changes are dollar changes."
    override val globusHeute = "Today"
    override val globusEinJahr = "1 year"
    override fun globusMio(zahl: String) = "$zahl m"
    override fun globusVorjahr(jahr: Int, wert: String) = "Previous year ($jahr): $wert"
    override fun globusKurs(code: String, kurs: String) = "1 € = $kurs $code"
    override fun globusWaehrungJahrLabel(code: String) = "$code against the euro, 1 year"
    override fun globusWirtLabel(k: de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl) = when (k) {
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WACHSTUM -> "Growth"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INFLATION -> "Inflation"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ARBEITSLOS -> "Unemployment"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.SCHULDEN -> "Government debt"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEISTUNG -> "Current account"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HAUSHALT -> "Budget balance"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF -> "GDP per capita"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF_KKP -> "GDP per capita, PPP"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WELT_ANTEIL -> "Share of world GDP"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BEVOELKERUNG -> "Population"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.AGRAR -> "Agriculture"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INDUSTRIE -> "Industry"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.VERARBEITUNG -> "of which manufacturing"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIENSTE -> "Services"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.EXPORTE -> "Export ratio"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.TREIBSTOFF -> "Fuel exports"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ROHSTOFF -> "Resource rents"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HIGHTECH -> "High-tech exports"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIREKTINVEST -> "Direct investment"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BOERSENWERT -> "Market cap to GDP"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.FIRMEN -> "Listed companies"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.GINI -> "Gini index"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEITZINS -> "Policy rate"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_EZB -> "10-year yield"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_USA -> "10-year yield"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_FRED -> "10-year yield"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG -> "Currency"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_JAHR -> "Currency, 1 year"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_EURO -> "Currency"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.KREDITZINS -> "Lending rate"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.REALZINS -> "Real interest rate"
    }
    override fun globusWirtInfo(k: de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl) = when (k) {
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WACHSTUM -> "Change in inflation-adjusted economic output (real GDP) from the previous year, in percent.\n\n• Higher growth tends to support corporate earnings in the country.\n• Figures from the current year on are forecasts."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INFLATION -> "Rise in consumer prices from the previous year, in percent, annual average.\n\n• High inflation erodes bonds and cash and pushes interest rates up.\n• The ECB and the US Federal Reserve aim for 2%."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ARBEITSLOS -> "Share of unemployed people in the labor force, in percent.\n\n• High rates weigh on consumption and tax revenue.\n• Under the EU procedure for macroeconomic imbalances, a three-year average above 10% is flagged."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.SCHULDEN -> "Gross government debt in percent of economic output.\n\n• High debt ties up future tax revenue for interest and makes the state more vulnerable when rates rise.\n• The EU treaty (Maastricht) sets 60% as the reference value."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEISTUNG -> "Balance of transactions with the rest of the world (goods, services, income) in percent of economic output.\n\n• A lasting deficit means dependence on foreign capital and makes the currency more vulnerable.\n• The EU imbalance procedure flags −4% and +6% as a three-year average."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HAUSHALT -> "Surplus or deficit of the government budget in percent of economic output.\n\n• Negative means the state is borrowing.\n• In the euro area a deficit above 3% counts as excessive (Maastricht)."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF -> "Economic output per resident in US dollars at current prices and exchange rates. Compares countries from the view of a dollar investor and moves with the exchange rate."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF_KKP -> "Economic output per resident adjusted for purchasing power parity (PPP): one international dollar buys the same amount of goods and services in every country.\n\nCompares living standards without exchange rate swings."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WELT_ANTEIL -> "Share of the country in world economic output in percent, measured at purchasing power parity. Shows the economic weight of the country."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BEVOELKERUNG -> "Residents in millions.\n\n• A growing population adds workers and consumption, a shrinking one weighs on pensions and growth.\n• Values from the current year on are estimates."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.AGRAR -> "Share of agriculture, forestry and fishing in value added, in percent of GDP.\n\n• High shares are mostly found in low-income countries.\n• The three shares do not add up to exactly 100% because product taxes are missing."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INDUSTRIE -> "Share of mining, manufacturing, construction and electricity, gas and water supply in value added, in percent of GDP.\n\nShows which stock market sectors fit the country, such as industrials and resources."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.VERARBEITUNG -> "Share of factory production (manufacturing) in GDP, in percent. The value is part of industry."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIENSTE -> "Share of trade, finance, public administration, tourism and other services in value added, in percent of GDP. High shares are typical of mature economies."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.EXPORTE -> "Exports of goods and services in percent of GDP. High values mean dependence on the world economy, low values a focus on the domestic market. Small countries have structurally high values."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.TREIBSTOFF -> "Share of fuels (oil, gas, coal) in goods exports, in percent. High values make the economy and the state budget dependent on commodity prices."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ROHSTOFF -> "Rents from oil, gas, coal, minerals and forests in percent of GDP, meaning sales revenue minus extraction costs. Shows how dependent the country is on natural resources."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HIGHTECH -> "Share of research-intensive goods (aerospace, computers, pharmaceuticals, instruments, electrical machinery) in manufactured exports, in percent. Hints at the innovative strength of industry."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIREKTINVEST -> "Net inflow of long-term capital from foreign companies in percent of GDP.\n\n• Negative values mean a net outflow.\n• Small financial centers such as Ireland or Luxembourg show distorted values because capital is only passed through."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BOERSENWERT -> "Market cap of the country's listed domestic companies in percent of GDP. Shows the depth of the stock market. Useful only against the country's own history and similar countries."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.FIRMEN -> "Number of domestic companies listed on domestic exchanges at year end. Shows the breadth of the stock market."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.GINI -> "Inequality of income or spending from 0 (everyone equal) to 100 (one person has everything).\n\n• High inequality raises the risk of social tension.\n• Depending on the country the value comes from income or consumption surveys, so countries are only partly comparable.\n• Surveys are not annual."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEITZINS -> "Interest rate of the central bank for short-term business with banks. Falling rates support stocks and credit, rising rates weigh on bonds. For euro countries the ECB rate applies."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_EZB -> "Annual yield of a ten-year government bond of the country, monthly average. A spread over Germany is the risk premium the market demands."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_USA -> "Annual yield of a ten-year US government bond, current level. Rising yields weigh on bond prices and on stocks with high valuations."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_FRED -> "Annual yield of a ten-year government bond of the country, monthly value. A spread over the US or Germany is the risk premium the market demands."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG -> "Exchange rate of the national currency in units per euro."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_JAHR -> "Change in value of the national currency against the euro over the last twelve months.\n\n• Positive means the currency has gained against the euro.\n• A weak currency makes imports and foreign debt dearer and helps exporters."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_EURO -> "The country uses the euro. For members of the euro area the ECB sets the policy rate, the bond yield applies per country."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.KREDITZINS -> "Interest rate banks charge their customers for loans, in percent. It sits above the policy rate and shows the borrowing cost of the economy."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.REALZINS -> "Bank lending rate minus inflation (GDP deflator), in percent. Negative means inflation exceeds the rate, loans are cheap in real terms and savers lose."
    }
    // [Globus Trend und Ebenen]
    override val globusPhase = "Business cycle"
    override fun globusIndexEtf(name: String) = "Country ETF $name (via ETF, in US dollars)"
    override val globusLegBoerseHeute = "Daily change of the main index, otherwise of the country ETF. Gray: no price or older than four trading days."
    override val globusBoerseZeit = "Trading hours"
    override val globusDepot = "My portfolio"
    override fun globusJahr(jahr: Int, prognose: Boolean) = if (prognose) "$jahr (forecast)" else "$jahr"
    override val globusJahrRegler = "Choose year"
    override fun globusJahrZurueck(jahr: Int) = "Back to $jahr"
    override fun trendPhase(p: de.troetenrepublik.app.engine.Fruehindikator.Phase) = when (p) {
        de.troetenrepublik.app.engine.Fruehindikator.Phase.AUFSCHWUNG -> "Expansion"
        de.troetenrepublik.app.engine.Fruehindikator.Phase.ABSCHWAECHUNG -> "Slowdown"
        de.troetenrepublik.app.engine.Fruehindikator.Phase.ABSCHWUNG -> "Downturn"
        de.troetenrepublik.app.engine.Fruehindikator.Phase.ERHOLUNG -> "Recovery"
    }
    override val globusOffen = "Open"
    override val globusGeschlossen = "Closed"
    override val globusLegArbeitslos = "Gray at 10%, the threshold of the EU procedure on macroeconomic imbalances. Below is green, above is red. Without an IMF value: latest World Bank (ILO) estimate."
    override val globusLegSchulden = "Gray at 60% of GDP, the Maastricht reference value. Below is green, above is red."
    override val globusLegLeistung = "Green: surplus, red: deficit. The EU procedure flags below −4% and above +6% of GDP."
    override val globusLegPhase = "By the OECD leading indicator where it exists, otherwise by IMF growth against the country's own trend and the forecast for next year. Gray: no data."
    override val globusLegBoerse = "Main trading session of the leading exchange, calculated for now. Closed at weekends, exchange holidays only considered for the US, Germany, the UK, Japan, France, the Netherlands, Belgium and Portugal. Dark side: night."
    override val globusLegDepot = "Share of portfolio value by country of operations, full color from 50%."
    override fun globusDepotNichtZuordenbar(anteil: String) = "Not assignable (ETFs, crypto etc.): $anteil"
    override val globusDepotLeer = "The portfolio has no positions."
    override fun globusKlumpen(land: String, anteil: String) = "More than half of the portfolio value is in $land ($anteil). The economy, stock market and currency of this country then drive most of the result."
    // [Globus Depot 0.70]
    override val globusDepotEtfs = "Include ETFs"
    override val globusDepotEtfsInfo = "Country shares of ETFs by their index, rounded. Sector, bond and commodity ETFs stay without a country."
    override val globusBilanzTitel = "Diversification"
    override val globusBilanzLaender = "Countries"
    override val globusBilanzGroesstes = "Largest country"
    override val globusBilanzHeim = "Home"
    override val globusBilanzIndustrie = "Developed"
    override val globusBilanzSchwellen = "Emerging"
    override val globusBilanzKonz = "Concentration"
    override val globusBilanzWelt = "Tick: world index (MSCI ACWI)"
    override fun globusBilanzZugeordnet(anteil: String) = "Based on $anteil of the portfolio, the rest has no country."
    override fun globusBilanzRegion(r: de.troetenrepublik.app.engine.DepotStreuung.Region) = when (r) {
        de.troetenrepublik.app.engine.DepotStreuung.Region.NORDAMERIKA -> "North America"
        de.troetenrepublik.app.engine.DepotStreuung.Region.EUROPA -> "Europe"
        de.troetenrepublik.app.engine.DepotStreuung.Region.ASIEN_PAZIFIK -> "Asia-Pacific"
        de.troetenrepublik.app.engine.DepotStreuung.Region.LATEINAMERIKA -> "Latin America"
        de.troetenrepublik.app.engine.DepotStreuung.Region.NAHOST_AFRIKA -> "Middle East, Africa"
        de.troetenrepublik.app.engine.DepotStreuung.Region.UEBRIGE -> "Other"
    }
    override fun globusBilanzStufe(k: de.troetenrepublik.app.engine.DepotStreuung.Konzentration) = when (k) {
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.GERING -> "low"
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.MAESSIG -> "moderate"
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.HOCH -> "high"
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.SEHR_HOCH -> "very high"
    }
    override fun globusBilanzEffektiv(n: String) = "Like $n equally sized countries. The world index is at about 2.2 because the US has over 60%."
    override val globusHerkunftTitel = "Assigned by operations"
    override val globusHerkunftInfo = "The country counted is the one where a company operates.\n\nA seat in a holding or registry country (such as Mauritius, Cayman Islands, Bermuda, Luxembourg, Cyprus) does not count. Then the country of operations applies, otherwise the country of the main exchange."
    override fun globusHerkunft(sitz: String, taetig: String, boerse: String?) = "Operating in $taetig, seat $sitz" + (boerse?.let { ", exchange $it" } ?: "")
    override val globusAlleAktien = "All stocks"
    override val kursquelleAntwortetNicht = "The price source does not respond. A VPN or a network filter may be the cause."
    override fun globusWeltweit(zentrale: String) = "Operating worldwide, headquarters in $zentrale"
    override val globusWenigerAktien = "Fewer stocks"
    override fun globusLandFuer(name: String) = "Country for $name"
    override val globusLandAutomatisch = "Assign automatically"
    override val globusLandGewaehlt = "set by you"
    override val globusLandUnbekannt = "Country unknown"

    override fun globusRangliste(ebene: String) = "Ranking: $ebene"
    override val globusHoechste = "Highest values"
    override val globusNiedrigste = "Lowest values"
    override val globusRangGesamt = "Strongest countries overall"
    override val globusRangGesamtInfo get() = ggInfo + "\n\nRanked by the overall picture of the selected year."
    override val globusRangGebiete = "with territories"
    override fun globusRangAlle(n: Int) = "All countries ($n)"
    override val rezTitel = "Recession signs"
    override val rezTitelWachstum = "Countries in recession"
    override val rezAbschwung = "Downturn per OECD"
    override fun rezAbschwungWert(n: Int, von: Int, anteil: String) = "$n of $von ($anteil)"
    override val rezZins = "US yield curve"
    override val rezSahm = "Sahm rule US"
    override val rezWahrsch = "Recession now (US)"
    override val rezInfoWachstum = "Counts the countries whose real GDP shrinks this year according to the IMF forecast.\n\n• The share of output is weighted by GDP in dollars.\n• The more countries and the larger they are, the broader the weakness.\n• The threshold is zero percent growth."
    override val rezInfoAbschwung = "Share of countries whose OECD leading indicator is below 100 and falling against the previous month (phase downturn).\n\n• Only countries the OECD covers (large economies) are counted, with values from the last six months.\n• The line below covers all countries.\n• The leading indicator anticipates the economy by about six to nine months."
    override val rezInfoZins = "Yield of ten-year minus yield of three-month US Treasuries in percentage points, daily.\n\n• Below zero the curve is inverted.\n• Before every US recession since 1968 it was below zero for a time, with a lead of about 6 to 24 months.\n• Not every inversion was followed by a recession."
    override val rezInfoWahrsch = "Estimated probability that the US economy is in a recession, in percent, monthly.\n\n• A model by Chauvet and Piger calculates it from employment, industrial production, real income and sales.\n• The value lags by several weeks.\n• There is no fixed threshold."
    override val trendTitel = "Trend"
    override val trendFrueh = "Leading indicator"
    override val trendPhaseLabel = "Business cycle phase"
    override val trendFruehInfo = "OECD Composite Leading Indicator (CLI), amplitude adjusted, monthly.\n\n• The index from surveys and market data anticipates the economy by about six to nine months.\n• The long-term average is 100: above it an economy is expected above its trend, below it under its trend.\n• The direction against the previous month counts more than the level."
    override val trendPhaseInfo = "Phase of the business cycle, by two routes with the same four phases.\n\nThe OECD leading indicator takes precedence (monthly, more current), by level above or below 100 and direction against the previous month.\n\nFor countries without an OECD value, IMF growth applies: growth of the current year above or below the country's median of the last ten years (without 2020), direction by the IMF forecast for next year.\n\nThe phases:\n• Expansion: above 100, rising\n• Slowdown: above 100, falling\n• Downturn: below 100, falling\n• Recovery: below 100, rising"
    override val trendEsi = "Economic sentiment (EU)"
    override val trendEsiInfo = "Economic Sentiment Indicator of the European Commission: index from surveys of industry, services, retail, construction and consumers.\n\n• The long-term average is 100. Above it sentiment is above average, below it below average.\n• If the value rises for several months, sentiment is picking up."
    override val trendBoerse = "Exchange"
    override fun trendOffenBis(zeit: String) = "open until $zeit"
    override fun trendOeffnet(zeit: String) = "opens $zeit"
    override val trendVerkuerzt = "Shortened trading day"
    override fun trendBoerseInfo(name: String, zeiten: String, zone: String) = "$name, main session $zeiten in local exchange time ($zone).\n\n• The time shown is in your time zone.\n• Closed at weekends.\n• Holidays: USA, Germany, United Kingdom, Japan, Paris, Amsterdam, Brussels, Lisbon.\n• Shortened trading days: USA."
    override val trendVergleichen = "Compare with"
    override val trendLandWaehlen = "Choose country"
    override val trendSuche = "Search country"
    override val trendVergleich = "Comparison"
    override fun trendPhaseCli(monat: String) = "from OECD leading indicator, $monat"
    override fun trendPhaseImf(jahr: Int) = "from IMF growth $jahr/${jahr + 1}"
    override val rezPhaseWelt = "Downturn worldwide"
    override fun rezPhaseWeltWert(n: Int, von: Int, anteil: String, bip: String?) = "$n of $von ($anteil)"
    override val rezInfoPhaseWelt = "Share of all countries in the downturn phase (below trend and falling) and their share of world GDP in dollars.\n\nThe phase comes from the OECD leading indicator where it exists, otherwise from IMF growth of the current year against the country's median of the last ten years (without 2020) and the forecast for next year."
    // [Globus Risiken]
    override val rkRegierung = "Governance"
    override fun rkWgiName(k: String) = when (k) {
        "PV" -> "Political stability"; "GE" -> "Gov. effectiveness"; "RQ" -> "Regulatory quality"
        "RL" -> "Rule of law"; "CC" -> "Corruption control"; else -> "Voice, accountability"
    }
    override fun rkWgiInfo(k: String) = when (k) {
        "PV" -> "How likely political instability and politically motivated violence, including terrorism, are."
        "GE" -> "Quality of public services and administration, their independence from political pressure and the credibility of government policy."
        "RQ" -> "Ability of the government to design policies and regulations that support the private sector."
        "RL" -> "Confidence in the rules of society and compliance with them, especially contract enforcement, property rights, police and courts."
        "CC" -> "Extent to which public power is used for private gain, from petty to grand corruption."
        else -> "Extent to which citizens can take part in choosing their government, plus freedom of expression, assembly and media."
    } + "\n\nThe percentile is the share of countries that score worse (0 to 100, higher is better).\n\nThe values are estimates from surveys and expert views with a margin of error, so small gaps between countries are not reliable."
    override val rkReise = "Travel advisories"
    override val rkUsLabel = "US travel advisory"
    override fun rkUsStufe(n: Int) = "Level $n"
    override val rkUsInfo = "Rating by the US State Department for travel by US citizens.\n• Level 1: exercise normal precautions\n• Level 2: exercise increased caution\n• Level 3: reconsider travel\n• Level 4: do not travel\n\nHigher levels can apply to parts of a country. The rating reflects the view of the US government."
    override val rkAaLabel = "German Foreign Office"
    override val rkAaLink = "Travel advice"
    override val rkAaWarnung = "Travel warning"
    override val rkAaTeil = "Partial travel warning"
    override val rkSanktionen = "Sanctions"
    override val rkEuLabel = "EU sanctions"
    override fun rkRegimeAnzahl(n: Int) = "$n regimes"
    override fun rkEuInfo(regime: List<String>, stand: String) = "EU sanctions regimes against the country (as of $stand).\n\nDepending on the regime: arms embargoes, entry bans, asset freezes on individuals or economic measures. Thematic regimes without a country link are left out.\n" +
        regime.joinToString("\n") { "• $it" }
    override val rkUsProgrammLabel = "US sanctions program"
    override fun rkProgrammeAnzahl(n: Int) = "$n programs"
    override fun rkUsProgrammInfo(programme: List<String>, stand: String) = "Country programs of the US agency OFAC (as of $stand).\n\nDepending on the program, US persons may not deal with listed persons and companies or with the country. Thematic programs are left out.\n" +
        programme.joinToString("\n") { "• $it" }
    override val rkOsLabel = "Listed persons, firms"
    override val rkOsInfo = "Number of persons and organizations linked to the country, for example by nationality or seat, that appear on sanctions lists (EU, US, UN and others).\n\nThe number says nothing about sanctions against the state. The EU and US rows cover that."
    override val rkRatings = "Credit ratings"
    override fun rkAusblick(a: String) = when (a) { "Stable" -> "stable"; "Positive" -> "positive"; "Negative" -> "negative"; "Developing", "Evolving" -> "developing"; else -> a }
    override val rkRatingInfo = "Long-term rating of a rating agency for government bonds in foreign currency, with the outlook for the expected direction.\n\nScale at S&P and Fitch:\n[skala] AAA | AA | A | BBB | BB | B | CCC | CC | C | D ; grenze=BBB ; links=best ; rechts=default\n• From BBB− (Moody's Baa3, DBRS BBB (low)) a rating is investment grade, below that speculative.\n• A year in brackets gives the year of the entry when it is older than last year."
    override val rkKonflikte = "Conflicts"
    override fun rkKonfliktArt(stufe: Int) = if (stufe >= 2) "War" else "Armed conflict"
    override fun rkKonfliktInfo(stufe: Int, jahr: Int) = (if (stufe >= 2) "War: at least 1,000 battle deaths in $jahr." else "Armed conflict: 25 to 999 battle deaths in $jahr.") +
        " Counted are conflicts over government or territory with at least one state party, and battle deaths in one calendar year."
    override val rkKatastrophen = "Natural disasters"
    override fun rkKatArt(typ: String) = when (typ) {
        "EQ" -> "Earthquake"; "TC" -> "Cyclone"; "FL" -> "Flood"; "WF" -> "Wildfire"; "VO" -> "Volcanic eruption"; "DR" -> "Drought"; else -> typ
    }
    override fun rkKatStufe(rot: Boolean, von: String) = (if (rot) "Red" else "Orange") + " ($von)"
    override val rkKatInfo = "Event of the last 30 days with alert level orange or red in the disaster alert system GDACS.\n\n• The level estimates the impact, orange medium and red high.\n• The date is the start.\n• The data are indicative."
    override val rkWahlen = "Elections"
    override val rkWahlParlament = "Parliamentary election"
    override val rkWahlPraesident = "Presidential election"
    override val rkWahlBeide = "Parliament and president"
    override val rkWahlInfo = "Next regular national election according to the Wikipedia list.\n\n• With a month only, the day is not set yet.\n• Dates can shift, for example through early elections."
    override val rkStruktur = "Structural risks"
    override fun rkStrukturLabel(art: de.troetenrepublik.app.engine.Laenderrisiken.Art) = when (art) {
        de.troetenrepublik.app.engine.Laenderrisiken.Art.RESERVEN -> "Foreign exchange reserves"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ROHSTOFF -> "Commodity dependence"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.JUGEND -> "Youth unemployment"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ALTER -> "Share aged 65+"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.AUSLAND -> "External debt"
    }
    override fun rkStrukturWert(art: de.troetenrepublik.app.engine.Laenderrisiken.Art, zahl: String) = when (art) {
        de.troetenrepublik.app.engine.Laenderrisiken.Art.RESERVEN -> "$zahl months"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ROHSTOFF -> "$zahl%"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.JUGEND -> "$zahl%"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ALTER -> "$zahl%"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.AUSLAND -> "$zahl% of GNI"
    }
    override fun rkStrukturInfo(art: de.troetenrepublik.app.engine.Laenderrisiken.Art, extra: String?) = when (art) {
        de.troetenrepublik.app.engine.Laenderrisiken.Art.RESERVEN -> "Foreign currency reserves in months of imports.\n\nThe IMF rule of thumb names three months as the lower bound. It applies mainly to countries with a flexible exchange rate and is disputed for countries with large capital flows."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ROHSTOFF -> "Share of fuels plus ores and metals in goods exports" + (extra?.let { " ($it)" } ?: "") + ". The IMF calls a country resource-rich when non-renewable resources make up at least 20% of exports or government revenue.\n\nCommodity prices then feed strongly into the trade balance and the budget."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.JUGEND -> "Share of the labor force aged 15 to 24 without work, ILO estimate." + (extra?.let { " World: $it." } ?: "") + " No fixed threshold exists for this figure."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ALTER -> "Share of the population aged 65 and over.\n\n• By a common classification a society is \"aged\" from 14% and \"super-aged\" from about 20%.\n• An aging population strains pension and health systems."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.AUSLAND -> "External debt of government and private borrowers in percent of gross national income (GNI), only for countries with external debt data. No fixed threshold exists for this figure."
    }
    override fun rkRohstoffAufteilung(brennstoff: String, erze: String) = "fuels $brennstoff%, ores and metals $erze%"
    override val lsTitel = "Structural problems"
    override val lsProgramme = "Countermeasures"
    override val lsKeinProgramm = "No program documented"
    override fun lsProgramm(name: String, jahr: Int) = if (jahr > 0) "$name ($jahr)" else name
    // [Globus Unternehmen]
    // [Laenderblatt 0.70]
    override val lbKopfArbeitslos = "Unemployed"
    override val lbKopfSchulden = "Debt"
    override val lbKopfBipKopf = "GDP/capita"
    override val lbKopfEinwohner = "Population"
    override val lbKopfLeben = "Life expectancy"
    override val lbKopfWaehrung = "Currency"
    override fun lbKopfZumEuro(code: String) = "$code vs. euro"
    override val lbKopfLeitzins = "Policy rate"
    override fun lbMrd(zahl: String) = "$zahl bn"
    override fun lbJahreKurz(zahl: String) = "$zahl y"
    override val lbVerlaufWachstum = "Growth by year"
    override val lbVerlaufInflation = "Inflation by year"
    override val lbPrognose = "Forecast"
    override val lbUebrige = "Other"
    override val lbTreemapHinweis = "Area: market cap, color: change today"
    override val lbRezLaender = "Countries in recession"
    override fun lbAnteilVon(n: Int, von: Int, anteil: String) = "$n of $von ($anteil)"
    override val lbRezBip = "Share of world GDP"
    override fun lbKonfliktZeile(stufe: Int, a: String, b: String) = (if (stufe >= 2) "War" else "Armed conflict") + ": $a vs. $b"
    override val lbRisikoWeitere = "More risks"
    override val lbWirtWeitere = "More figures"
    override fun lbIndexLabel(name: String) = "Index $name"
    override fun lbEtfLabel(symbol: String) = "Country ETF $symbol"
    override fun lbIndexInfo(name: String) = "Daily change of the main index $name."
    override fun lbLaenderIndexLabel(name: String) = "Index $name"
    override fun lbLaenderIndexInfo(name: String) = "Daily change of the country index $name. It replaces the exchange's main index, which Yahoo does not carry."
    override val lbStandVom = "As of"
    override val lbIndexQuelleStumm = "The exchange does not respond. A VPN or network filter may block it."
    override val lbBoerseLabel = "Exchange"
    override val lbKeineBoerse = "No exchange"
    override val lbKeinIndexLabel = "Main index"
    override val lbKeinIndex = "No freely available index"
    override fun lbIndexSelbstLabel(name: String) = "Index $name"
    override fun lbIndexSelbstInfo(name: String) = "The index $name comes from the self-fetch: open the page, solve any check on the page yourself, then take it over. The value counts for four trading days."
    override val lbIndexSelbstOeffnen = "Load index via self-fetch"
    override val globusIndexSelbstFertig = "Index value taken over"
    override val globusIndexSelbstLeer = "The page shows no index value"
    override val saIndexTitel = "Stock indices"
    override val saIndexText = "The value appears on the globe under \"Markets today\"."
    override val lbWgiHinweis = "Rank among all countries, 100 = best country."
    override val globusLandTitel = "Country"
    override val globusHauptstadt = "Capital"
    override val globusWaehrung = "Currency"
    override val globusOberhaupt = "Head of state"
    override val globusRegierungschef = "Head of government"
    override val globusOberhauptChef = "Head of state and government"
    override val globusRegierungsform = "Form of government"
    override val globusMitgliedschaften = "Memberships"
    override fun globusFormName(code: String, bundesstaat: Boolean) = (when (code) {
        "PARL_REP" -> "Parliamentary republic"; "PRAES" -> "Presidential republic"; "HALBPRAES" -> "Semi-presidential republic"
        "VERF_MON" -> "Constitutional monarchy"; "ABS_MON" -> "Absolute monarchy"; "EINPARTEI" -> "One-party state"
        "THEOKRATIE" -> "Theocracy"; "MILITAER" -> "Military government"; "DIREKTORIUM" -> "Directorial republic"
        "SVZ" -> "Special administrative region of China"; else -> "Republic"
    }) + if (bundesstaat) ", federal state" else ""
    override fun globusOrgName(code: String) = when (code) {
        "EURO" -> "Eurozone"; "GOLF" -> "Gulf Cooperation Council"; "AU" -> "African Union"; "MERCOSUR" -> "Mercosur"; else -> code
    }
    override val globusInfoOberhaupt = "Represents the state abroad, as president, monarch or council. In parliamentary democracies and monarchies the duties are mostly ceremonial."
    override val globusInfoRegierungschef = "Leads the government and is responsible for the budget and economic policy. In presidential systems this is the president, elsewhere usually a prime minister or chancellor."
    override val globusInfoOberhauptChef = "The same person is head of state and leads the government, as is usual in presidential systems. The office is also responsible for the budget and economic policy."
    override val globusInfoRegierungsform = "Rough classification of the political system. Federal state means: constituent states have their own legislation."
    override val globusInfoMitgliedschaften = "Alliances and economic areas of the country:\n" +
        "• EU: single market of the European Union\n• Eurozone: countries using the euro as currency\n• NATO: defense alliance\n" +
        "• G7 and G20: groups of large economies\n• OPEC: oil producers with coordinated output\n" +
        "• BRICS, OECD, ASEAN, Mercosur, Gulf Cooperation Council and African Union: further alliances and trade areas"
    override val globusWikiTitel = "Economy according to Wikipedia"
    override val globusWeiterlesen = "Read more on Wikipedia"
    override fun globusLaenderartikel(name: String) = "Country article: $name"
    override val globusUnternehmenTitel = "Largest companies"
    override val globusBoersennotiert = "Listed"
    override val globusNichtBoersennotiert = "Not listed"
    override val globusStaatsfirma = "State-owned"
    override val globusFirmenOhneWerte = "No current market caps, order approximate"
    override val globusInfoGelistet = "The ten companies with the highest market cap among the country's covered companies, converted to euro.\n\n• Market cap is share price times number of shares.\n• Covered are catalog stocks and companies from Wikidata with a Yahoo Finance symbol, not every listing.\n• State-owned means: the state is an owner according to Wikidata.\n• Market caps: Yahoo Finance. For Russian companies the Moscow Exchange (ISS interface, delayed 15 minutes). For companies without a quote Wikidata or the infobox of the Wikipedia article (CC BY-SA 4.0), then the year of the figure is shown in brackets and the value is not current.\n• For Colombo, Karachi, Casablanca, BRVM, Uganda, Dhaka, Chittagong, Malta, Zagreb, Ljubljana, Ho Chi Minh City, Hanoi, UPCoM, Cyprus, Trinidad and Tobago, Baku, Lima and Kazakhstan (KASE) the value comes from the exchange website, retrieved for personal, non-commercial use."
    override val globusInfoNichtGelistet = "Large companies without a stock market listing, selected and checked by hand, roughly ordered by size. They belong to families, foundations, cooperatives or the state. There are no shares, buying in the practice depot is not possible."
    // [Laender Unternehmen 0.71] Details of a company without price history as an info sheet
    override val globusFirmaBranche = "Industry"
    override val globusFirmaSitz = "Headquarters"
    override val globusFirmaBoerse = "Exchange"
    override val globusFirmaEigner = "Owner"
    override val globusFirmaStaat = "State"
    override val globusFirmaWert = "Market cap"
    override val globusFirmaMitarbeiter = "Employees"
    override val globusFirmaGegruendet = "Founded"
    override val globusFirmaMutter = "Parent company"
    override val globusFirmaWebsite = "Website"
    override val globusFirmaKeinVerlauf = "No price history: there is no free price source for this company. A year in brackets means the value is not current."
    override val globusBranchenTitel = "Industries on the stock market"
    override fun globusInfoBranchen(n: Int) = "Share of the industry in the market cap of the country's covered listed companies ($n companies with a market cap).\n\n• The shares apply only to these companies, not to the whole market.\n• Industries follow the app's classification."
    override val globusWeitereFirmen = "More large companies"
    override val globusInfoWeitere = "Companies with more than 10,000 employees for which Wikidata knows no stock exchange. The entries are not checked by hand, the companies may still be listed. Ordered by employees."
    override fun globusBoersenKurse(name: String) = "Prices at the $name"
    override val globusSelbstHinweis = "Load the page, solve any check on the page yourself, then tap \"Use this page\". The app only reads the values shown."
    override fun globusSelbstFertig(n: Int) = if (n > 0) "$n market caps taken over" else "Total market cap taken over"
    override val globusSelbstLeer = "The page shows no market caps"
    override fun globusBoersenwertGesamt(wert: String, quelle: String) = "Total market cap: $wert ($quelle)"
    override fun globusBoersenwertJahr(jahr: Int) = "Market cap $jahr"
    override val kkpDollar = "PPP $"
    // [Globus Mehr]
    // [Engpaesse]
    override val globusEngpaesse = "Chokepoints"
    override val globusLegEngpaesse = "Transits per day, average of the last seven days against the average of the same period a year earlier. Full color at 40% deviation."
    override val engOhneVergleich = "No prior-year value"
    override val engListe = "Chokepoints by change"
    override fun engStand(datum: String) = "As of $datum"
    override fun engName(id: String) = when (id) {
        "chokepoint1" -> "Suez Canal"
        "chokepoint2" -> "Panama Canal"
        "chokepoint3" -> "Bosporus"
        "chokepoint4" -> "Bab el-Mandeb"
        "chokepoint5" -> "Strait of Malacca"
        "chokepoint6" -> "Strait of Hormuz"
        "chokepoint7" -> "Cape of Good Hope"
        "chokepoint8" -> "Strait of Gibraltar"
        "chokepoint9" -> "Dover Strait"
        "chokepoint10" -> "Øresund"
        "chokepoint11" -> "Taiwan Strait"
        "chokepoint12" -> "Korea Strait"
        "chokepoint13" -> "Tsugaru Strait"
        "chokepoint14" -> "Luzon Strait"
        "chokepoint15" -> "Lombok Strait"
        "chokepoint16" -> "Ombai Strait"
        "chokepoint17" -> "Bohai Strait"
        "chokepoint18" -> "Torres Strait"
        "chokepoint19" -> "Sunda Strait"
        "chokepoint20" -> "Makassar Strait"
        "chokepoint21" -> "Strait of Magellan"
        "chokepoint22" -> "Yucatán Channel"
        "chokepoint23" -> "Windward Passage"
        "chokepoint24" -> "Mona Passage"
        "chokepoint25" -> "Balabac Strait"
        "chokepoint26" -> "Bering Strait"
        "chokepoint27" -> "Mindoro Strait"
        "chokepoint28" -> "Kerch Strait"
        else -> id
    }
    override fun engSuche(id: String) = "\"" + engName(id) + "\" " + "shipping"
    override fun engGegenVorjahr(prozent: String) = "$prozent vs prior year"
    override val engKeineDaten = "No data"
    override val engProTag = "Transits per day"
    override val engVorjahr = "Prior year"
    override val engTanker = "Tankers per day"
    override val engContainer = "Container ships per day"
    override val engInfoProTag = "Ships passing the chokepoint per day, averaged over the last seven days.\n\nEach ship counts once when it crosses the boundary of the chokepoint. The same transit does not count twice within 48 hours."
    override val engInfoVorjahr = "Daily average over the 28 days around the same date of the previous year. A drop against this value shows that fewer ships sail than at the same time of year before."
    override val engInfoSchiffsart = "Daily average of the last seven days, change against the prior-year period in brackets. Ship types follow the PortWatch classes (tanker, container, dry bulk, general cargo, ro-ro)."
    override val engVerlauf = "One-year trend"
    override val engBedeutung = "Significance"
    override val engWarenTitel = "Goods"
    override fun engInfo(id: String) = when (id) {
        "chokepoint6" -> "Only sea route between the Persian Gulf and the open ocean, between Iran and Oman.\n\nThe EIA puts the flow at about 20 million barrels of oil and oil products per day (2024), around a fifth of world oil consumption, plus about a fifth of world LNG trade."
        "chokepoint4" -> "Gateway from the Red Sea to the Gulf of Aden, between Yemen and Djibouti.\n\nSince the Houthi attacks on merchant ships from late 2023, many carriers avoid the route and sail around the Cape of Good Hope."
        "chokepoint1" -> "Links the Mediterranean and the Red Sea and shortens the route between Asia and Europe.\n\nWhen transits fall, ships divert around the Cape of Good Hope. That lengthens voyages and ties up shipping capacity."
        "chokepoint2" -> "Links the Atlantic and the Pacific. The water level of Gatun Lake limits the number of lockages. In 2023 and 2024 a drought cut transits sharply."
        "chokepoint5" -> "Links the Indian Ocean and the South China Sea between Malaysia, Indonesia and Singapore.\n\nMain route for oil and containers to East Asia, with the most transits among the large energy chokepoints."
        "chokepoint11" -> "Between Taiwan and China, part of the main route of East Asian container traffic.\n\n• Taiwan makes most of the most advanced semiconductors.\n• A blockade or closure would hit chip and electronics shipments."
        "chokepoint3" -> "Only exit of the Black Sea.\n\n• The Montreux Convention (1936) governs passage of warships.\n• Grain, oil and fertilizer from Russia, Ukraine and Kazakhstan pass here."
        "chokepoint7" -> "Diversion route around Africa. More transits while Suez Canal and Red Sea transits fall indicate rerouted traffic."
        "chokepoint8" -> "Gateway between the Atlantic and the Mediterranean, also the way to and from the Suez Canal."
        "chokepoint9" -> "Narrowest point of the English Channel, approach to the North Sea ports of Rotterdam, Antwerp and Hamburg."
        "chokepoint10" -> "One of the entrances to the Baltic Sea, between Denmark and Sweden."
        "chokepoint28" -> "Links the Sea of Azov with the Black Sea."
        "chokepoint12" -> "Between South Korea and Japan, approach to the ports of Busan, Kitakyushu and the Sea of Japan."
        "chokepoint14" -> "Between Taiwan and the Philippines, link from the South China Sea into the Pacific."
        else -> "Waterway whose transits PortWatch counts from ship signals."
    }
    override fun engWaren(id: String) = when (id) {
        "chokepoint6" -> "Crude oil, oil products, liquefied natural gas (LNG), chemicals."
        "chokepoint4" -> "Containers between Asia and Europe, crude oil and oil products, grain."
        "chokepoint1" -> "Containers between Asia and Europe, crude oil, oil products, liquefied gas, grain."
        "chokepoint2" -> "Containers between East Asia and the US East Coast, US liquefied gas, grain and soybeans from the US."
        "chokepoint5" -> "Crude oil to East Asia, containers between Asia and Europe, coal, palm oil."
        "chokepoint11" -> "Semiconductors and electronics, containers between China, Taiwan, Korea and Japan."
        "chokepoint3" -> "Grain, crude oil, fertilizer, metals."
        "chokepoint7" -> "Like Suez Canal and Bab el-Mandeb: containers, oil, bulk."
        "chokepoint8" -> "Containers, oil, bulk, general cargo between Europe, North Africa, Asia and the Americas."
        "chokepoint9" -> "Containers, general cargo, tankers."
        "chokepoint10" -> "General cargo, tankers, timber and bulk."
        "chokepoint28" -> "Grain, metals, oil."
        "chokepoint12" -> "Containers, bulk, tankers."
        "chokepoint14" -> "Containers, bulk, tankers."
        else -> "Regional shipping and feeder traffic to large ports."
    }
    override val engSanktionen = "Sanctions on neighbors"
    override val engUsa = "USA"
    override fun engSanktionInfo(eu: String, us: String) = "Sanctions regimes against the neighboring state. EU: " + eu.ifBlank { "none" } + ". USA (OFAC): " + us.ifBlank { "none" } + "."
    override val engWerte = "Securities"
    override val engSchlagzeilen = "Headlines"
    override fun engLieferZeile(name: String, prozent: String) = "$name: transits $prozent vs prior year"
    // [Grosses Ganzes 0.73]
    override val abhTitel = "Dependencies and risks"
    override fun abhZusammen(land: Boolean, rohstoffe: Int, einfluesse: Int, politik: Boolean, empfindlichkeit: Boolean) = listOfNotNull(
        if (rohstoffe > 0) (if (rohstoffe == 1) "1 raw material" else "$rohstoffe raw materials") else null,
        if (einfluesse > 0) (if (einfluesse == 1) "1 influence" else "$einfluesse influences") else null,
    ).ifEmpty {
        // ohne Zahlen: die Themen der Karte
        listOfNotNull(if (land) "Country" else null, if (politik) "Policy" else null, if (empfindlichkeit) "Price sensitivity" else null)
    }.joinToString(" · ")
    override fun abhAbschnitt(key: String) = when (key) {
        "HERKUNFT" -> "Country and domicile"; "ROHSTOFFE" -> "Raw materials of the industry"; "ROHSTOFFE_FIRMA" -> "Raw materials of the company"; "EINFLUESSE" -> "Influences"
        "EINFLUESSE_BRANCHE" -> "Influences on the industry"; "POLITIK" -> "Policy and law"; "EMPFINDLICHKEIT" -> "Price sensitivity"; "LUECKEN" -> "Gaps"; else -> key
    }
    override fun abhArt(key: String) = when (key) { "GEMELDET" -> "reported"; "GEMESSEN" -> "measured"; "ABGELEITET" -> "derived"; else -> key }
    override fun abhStand(monat: String) = "As of $monat"
    override val abhSitz = "Domicile"
    override val abhTaetigkeit = "Operations"
    override val abhMutter = "Parent company"
    override val abhGesamtbild = "Country overview"
    override val abhLandrisiko = "Country risk"
    override fun abhVon100(punkte: String) = "$punkte of 100"
    override val abhKatastrophen = "Disasters in the country"
    override fun abhKatastrophenWert(n: Int, rot: Int) = if (rot > 0) "$n current, $rot red" else "$n current"
    override fun abhTop3(p: String) = "Top 3: $p"
    override val abhAnteileOffen = "shares not published"
    override val abhKritisch = "critical in the EU"
    override fun abhFoerderung(laender: String) = "Production: $laender"
    override val abhBetroffen = "affected"
    override fun abhRichtung(richtung: Int) = if (richtung > 0) "supports" else "weighs"
    override fun abhAnstieg(richtung: Int) = if (richtung > 0) "A rise supports" else "A rise weighs"
    override val abhWegPraefix = "Path"
    override val abhStaerkeRoute = "Transits on the route vs prior year, not measured for the company"
    override val abhStaerkeGebiet = "Reports from the area vs the weeks before, not measured for the company"
    override fun abhGiltFuer(branche: String, gebiet: String?) = "Applies to the industry $branche" + (gebiet?.let { " ($it)" } ?: "")
    override fun abhFaktor(key: String) = when (key) {
        "MARKT" -> "Market (S&P 500)"; "HALBLEITER" -> "Semiconductor index"; "OEL" -> "Oil"; "DOLLAR" -> "Dollar"
        "ZINS10J" -> "Interest rates (10-year)"; "KUPFER" -> "Copper"; "GOLD" -> "Gold"; "CHINA" -> "China (Shanghai)"; else -> key
    }
    override fun abhAussage(key: String) = when (key) { "LAEUFT_MIT" -> "moves with"; "LAEUFT_GEGEN" -> "moves against"; "WECHSELT" -> "flips"; else -> "no evidence" }
    override fun abhEmpfHinweis(r2: String, jahre: Int) = "R² $r2 · $jahre years"
    override val abhEmpfOhne = "Other drivers"
    override val abhKeinBeleg = "no evidence"
    override val abhEmpfZuKurz = "not enough price history"
    override val abhEmpfLabel = "Price sensitivity"
    override val abhEtfErfasst = "Holdings covered"
    override fun abhEtfBetrifft(n: Int) = "affects $n holdings"
    override val abhWirkungUnter = "Drivers, countries and your holdings"
    override val abhInfo = "• Reported: stated by the company.\n• Measured: calculated from prices.\n• Derived: from industry, description or country.\n\nR² shows how much of the price movement the drivers explain. A link counts as established when it runs the same way in both halves of the period.\n\nDescribes the past and is not investment advice."
    // [0.76] Key statements and calmer layout of the card
    override val abhKernTitel = "At a glance"
    override fun abhKernSegment(name: String, anteil: String) = "Largest segment: $name, $anteil"
    override fun abhKernRegion(name: String, anteil: String) = "Largest region: $name, $anteil"
    override fun abhKernKunde(anteil: String) = "Largest customer: $anteil of revenue"
    override fun abhKernKurs(liste: String) = "Price moves $liste"
    override fun abhKernMit(name: String, wert: String) = "with $name ($wert)"
    override fun abhKernGegen(name: String, wert: String) = "against $name ($wert)"
    override fun abhKernRohstoffe(land: String, liste: String) = "Raw materials of the industry, largest producer $land: $liste"
    override fun abhKernKritisch(n: Int) = "Industry needs $n raw materials that count as critical in the EU"
    // [0.76] Raw materials per company
    override fun abhVerwendung(key: String) = when (key) {
        "WAFER" -> "Wafers"
        "WAFERGAS" -> "Gas in wafer fabrication"
        "AETZ" -> "Etching and cleaning wafers"
        "LEITER" -> "Conductors and contacts"
        "GAN" -> "GaN and GaAs chips"
        "FUNKCHIP" -> "Radio chips"
        "SUBSTRAT" -> "Chip substrates"
        "OPTIK" -> "Lasers and optics"
        "FASER" -> "Optical fiber"
        "AKKU" -> "Battery cells"
        "KATHODE" -> "Cathode material"
        "ANODE" -> "Anode material"
        "MAGNET" -> "Magnets and motors"
        "KATALYSATOR" -> "Catalytic converters"
        "KAROSSERIE" -> "Body and steel parts"
        "LEICHTBAU" -> "Lightweight parts"
        "GEHAEUSE" -> "Casings"
        "LOT" -> "Solder and contacts"
        "KONDENSATOR" -> "Capacitors"
        "SPEICHER" -> "Hard drives"
        "KABEL" -> "Cables and windings"
        "LEITUNG" -> "Lines and grids"
        "TRIEBWERK" -> "Engines and high-temperature parts"
        "STRUKTUR" -> "Airframe and structure"
        "MUNITION" -> "Ammunition and armor"
        "STAHL" -> "Steel and alloys"
        "HARTMETALL" -> "Tools and cemented carbide"
        "VERZINKUNG" -> "Galvanizing"
        "GUSS" -> "Die-cast parts"
        "MESSING" -> "Brass"
        "ADDITIV" -> "Additive"
        "DUENGER" -> "Fertilizer"
        "FLUOR" -> "Fluorochemicals and refrigerants"
        "SOLAR" -> "Solar cells"
        "WIND" -> "Wind turbines"
        "MRT" -> "MRI magnets"
        "ELEKTROLYSE" -> "Electrolyzers and fuel cells"
        "FOERDERUNG" -> "own mining"
        "ERKUNDUNG" -> "own exploration or mining"
        "ERZEUGUNG" -> "own product"
        "LIZENZ" -> "royalties and offtake rights"
        "EINSATZ" -> "input material"
        "HUETTE" -> "smelting"
        "RECYCLING" -> "recycling and processing"
        "SCHMUCK" -> "jewelry and watches"
        "KONFLIKT" -> "conflict mineral in supplied parts"
        "IMPLANTAT" -> "Implants"
        "PIGMENT" -> "Pigment in paints"
        "ELEKTRODE" -> "Electrodes"
        "GASHANDEL" -> "Industrial gas"
        "POLY" -> "Polysilicon"
        "SILIKON" -> "Silicones"
        "VERPACKUNG" -> "Packaging and cans"
        "SUPRALEITER" -> "superconducting chips"
        "RADAR" -> "Radar and sensors"
        "ANREICHERUNG" -> "Enrichment and fuel fabrication"
        "BRENNSTOFF" -> "Fuel for nuclear power plants"
        else -> key
    }
    override fun abhWeg(key: String) = when (key) { "EIGEN" -> "processed in house"; "ZULIEFERER" -> "via suppliers"; "FERTIGER" -> "via contract manufacturers"; "PRODUKT" -> "in the product"; else -> key }
    override fun abhRohQuelle(key: String) = when (key) {
        "SEC" -> "reported to the SEC"; "BESCHREIBUNG" -> "from the description"; "FIRMA" -> "from the business model"
        "INDUSTRIE" -> "from the industry"; "BRANCHE" -> "from the sector"; else -> key
    }
    override fun abhBrancheUeblich(verwendung: String) = "$verwendung common in the sector"
    override val abhRohSd = "Form SD filed with the SEC: conflict minerals tin, tantalum, tungsten, gold"
    override val abhRohBericht = "Named in the raw materials section of the annual report"
    override fun abhRohWelt(jahr: Int) = "World production $jahr"
    override fun abhRohWert(jahr: Int) = "Value of world production $jahr"
    override fun abhRohFirma(jahr: Int) = "Company production $jahr"
    override fun abhRohKapazitaet(jahr: Int) = "Company capacity $jahr, by ownership share"
    override val abhRohKapKopf = "Capacity"
    override val abhRohAnlagen = "Plants with capacity data"
    override fun abhRohAnlagenWert(verwertbar: Int, gesamt: Int) = "$verwertbar of $gesamt"
    override val abhRohLand = "Country"
    override val abhRohFoerderland = "Producer"
    override val abhRohMenge = "Quantity"
    override val abhRohAnteil = "Share"
    override val abhRohWertKopf = "Value"
    override val abhRohUebrige = "Other countries"
    override fun abhRohEinheit(key: String) = if (key == "m3") "m³" else key
    override fun abhKernRohstoffeFirma(land: String, liste: String) = "Raw materials of the company, largest producer $land: $liste"
    override fun abhKernFoerdert(liste: String) = "Explores for or mines: $liste"
    override fun abhKernErzeugt(liste: String) = "Own product: $liste"
    override fun abhKernLizenz(liste: String) = "Holds royalties and offtake rights: $liste"
    override val abhRohGenannt = "named in the description"
    override val abhRohZuordnung = "Assignment"
    override fun abhKernKritischFirma(n: Int) = "Company needs $n raw materials that count as critical in the EU"
    override val wnUeberProfil = "via company profile"
    override fun abhKernLand(sitz: String, land: String, stufe: String) = when {
        sitz.isEmpty() -> "Country $land, country risk $stufe"
        sitz == land -> "Domicile $sitz, country risk $stufe"
        else -> "Domicile $sitz, country $land, country risk $stufe"
    }
    override fun abhStufe(key: String) = when (key) { "NIEDRIG" -> "low"; "MITTEL" -> "medium"; else -> "high" }
    override fun abhKernAuffaellig(liste: String) = "Currently notable: $liste"
    override fun abhKernEinfluesse(liste: String) = "Influences on the industry: $liste"
    override fun abhMessung(text: String) = "Measured on the price: $text"
    override fun abhBaustein(key: String) = when (key) {
        "BASIS" -> "Governance and stability"; "SANKTIONEN_EU" -> "EU sanctions"; "SANKTIONEN_US" -> "US sanctions"
        "REGISTER" -> "Hits in the sanctions register"; "KONFLIKT" -> "Armed conflict"; "KRIEG" -> "War"
        "REISE" -> "US travel warning"; else -> "Structural problems"
    }
    override fun wsLuecke(grund: String) = "Segments and regions: $grund"
    // [Grosses Ganzes 0.74] What it lives on
    override val wsTitel = "Revenue sources"
    override fun wsGruppe(key: String) = when (key) { "SEGMENTE" -> "Segments"; "REGIONEN" -> "Regions"; else -> "Major customers" }
    override fun wsStand(jahr: Int) = "Fiscal year $jahr"
    override val wsUebrige = "Other"
    override fun wsKunde(nr: String) = "Customer $nr"
    override fun wsKundenGruppe(n: String) = "$n customers"
    override val wsSitzland = "Home country"
    override val wsNichtUsa = "Outside the US"
    override fun wsGebiet(name: String) = name
    override val wsLaedt = "Loading …"
    override val wsFehler = "Report not available"
    override val wsZuGross = "Report too large"
    override val wsNichtLesbar = "Report not readable"
    override val wsNichtEindeutig = "not clearly reported"
    override val wsNichtVerfuegbar = "not available"
    override val wsOhneKontakt = "Contact missing"
    override fun wsInfo(key: String) = when (key) {
        "SEGMENTE" -> "Revenue by business segment from the latest annual report of the company.\n\n• Shares of the sum of the reported segments.\n• Totals, eliminations and reconciling items are removed.\n• Names are as written in the report.\n• If the sum differs from revenue by more than 10%, the section reads not clearly reported."
        "REGIONEN" -> "Revenue by region or country from the latest annual report of the company.\n\n• Shares of the sum of the reported regions, umbrella terms are removed.\n• The company decides whether revenue is assigned by customer location or by the location of the entity."
        "KUNDEN" -> "Customers with their share of revenue as the company reports them, usually from 10%.\n\n• Shares of receivables do not count.\n• Companies rarely name customers, Customer A is the label used in the report."
        "KEIN_SEC" -> "Segments, regions and major customers are in the annual reports of companies.\n\n• They are freely available in structured form for US companies and many large companies worldwide that are listed in the US.\n• There is no such source for this company."
        "KEIN_BERICHT" -> "The SEC lists the company, but without an annual report with XBRL data (10-K, 20-F, 40-F)."
        "LEER" -> "The latest annual report has no breakdown of revenue by segment, region or major customer in its XBRL data."
        else -> "The SEC requires a contact address in the identification of requests. Enter it under Access keys."
    }
    override val wnTitel = "Impact on my holdings"
    override val wnLeer = "None of your holdings is tied to a tracked driver."
    override fun wnBetrifft(depot: Int, watch: Int, anteil: String) = listOfNotNull(
        if (depot > 0) "$depot in portfolio ($anteil)" else null,
        if (watch > 0) "$watch on watchlist" else null,
    ).joinToString(" · ").ifEmpty { "no holdings affected" }
    override val wnWirkung = "Effect"
    override val wnLaender = "Countries"
    override val wnBranchen = "Industries"
    override val wnDepot = "In the portfolio"
    override val wnWatchlist = "On the watchlist"
    override val wnBelege = "Evidence"
    override val wnOhneTreffer = "No match in your holdings"
    override val wnUeberBranche = "via industry"
    override val wnGenannt = "named"
    override val wnLaedt = "Working out links …"
    override fun wnTyp(key: String) = when (key) {
        "ZINS" -> "Interest rates"; "ENERGIE" -> "Energy"; "WAEHRUNG" -> "Currency"; "NACHFRAGE" -> "Demand"; "ZYKLUS" -> "Cycles"
        "ROHSTOFF" -> "Raw materials"; "ENGPASS" -> "Chokepoints"; "SPANNUNG" -> "Tension areas"; "KLIMA" -> "Climate"
        "PROGRAMM" -> "Programs and tariffs"; "DURCHBRUCH" -> "Breakthroughs"; "LAND" -> "Country"; else -> key
    }
    override fun wnZur(key: String) = when (key) {
        "ENGPASS", "SPANNUNG", "KLIMA" -> "View on the globe"; "DURCHBRUCH" -> "View breakthroughs"
        "ZINS" -> "View rate expectations"; else -> ""
    }
    override val wnInfo = "A direction is shown only where it is established or measured from prices, otherwise the holding is listed as affected. The size of the effect cannot be quantified."
    override val lbTitel = "Situation"
    override fun lbThema(key: String) = when (key) {
        "KONJUNKTUR" -> "Growth and inflation"; "ZINSEN" -> "Interest rates"; "GEOPOLITIK" -> "Geopolitics"
        "ROHSTOFFE" -> "Raw materials and energy"; "KLIMA" -> "Climate"; "TECHNIK" -> "Technology"; else -> key
    }
    override fun lbName(key: String, zusatz: String) = when (key) {
        "WACHSTUM" -> "World growth $zusatz"; "INFLATION" -> "World inflation $zusatz"; "ZINS_HEUTE" -> "Fed funds per futures"
        "ZINS_SCHRITTE" -> "Expected in 12 months"; "KRIEGE" -> "Wars $zusatz"; "SPANNUNG" -> "Areas with more reports"
        "ENGPASS_GESTOERT" -> "Chokepoints down over 20%"; "ENSO_ZUSTAND" -> "ENSO"; "ENSO_INDEX" -> "ENSO index"
        "KATASTROPHEN" -> "Weather disasters, 30 days"; "DURCHBRUCH_NEU" -> "New breakthrough reports, 7 days"
        "DURCHBRUCH_THEMEN" -> "Topics with reports"; else -> key
    }
    override fun lbSchritte(n: String) = "$n steps"
    override fun lbVonGesamt(n: String, gesamt: String) = "$n of $gesamt"
    override fun lbRot(n: String, rot: String) = "$n, $rot red"
    override fun lbVorjahr(wert: String) = "Prior year $wert"
    override fun lbErhoehtAb(wert: String) = "Elevated from $wert"
    override fun lbVorher(wert: String) = "Before $wert"
    override fun lbStand(datum: String) = "As of $datum"
    override val lbOffen = "no data"
    override val lbSeit = "Since you last opened this"
    override fun lbKeineAenderung(datum: String) = "No change since $datum"
    override fun lbThemaKurz(key: String) = when (key) {
        "KONJUNKTUR" -> "Growth"; "ZINSEN" -> "Rates"; "GEOPOLITIK" -> "Geopolitics"
        "ROHSTOFFE" -> "Raw materials"; "KLIMA" -> "Climate"; "TECHNIK" -> "Technology"; else -> key
    }
    override fun lbAuffaellig(n: String, gesamt: String) = "$n of $gesamt flagged"
    override fun lbAenderungen(n: Int) = if (n == 1) "1 change" else "$n changes"
    override fun lbTab(key: String) = when (key) {
        "WACHSTUM_LAND" -> "Growth by country"; "INFLATION_LAND" -> "Inflation by country"; "ZINS_PFAD" -> "Expected policy rate by month"
        "KRIEGE" -> "Wars"; "KONFLIKTE" -> "Other armed conflicts"; "SPANNUNG" -> spTitel; "ENGPAESSE" -> "Shipping chokepoints"
        "ENSO" -> ensoTitel; "KATASTROPHEN_LAND" -> "Weather disasters by country"; "DURCHBRUCH_THEMA" -> "Breakthroughs by topic"
        "BESCHLEUNIGT" -> dbBeschleunigt; else -> key
    }
    override fun lbErk(key: String, zusatz: String) = when (key) {
        "WACHSTUM_LAND" -> "Growth: change in economic output on the prior year, IMF estimate. The world figure weights countries by purchasing power."
        "INFLATION_LAND" -> "Inflation: rise in consumer prices on the prior year, annual average, IMF estimate. Elevated from $zusatz: above this, price rises count as a burden."
        "ZINS_PFAD" -> "Fed funds: overnight rate steered by the Federal Reserve. For each month, the rate that futures expect. One step is 0.25 percentage points."
        "KRIEGE" -> "War: armed conflict with at least 1,000 battle deaths in $zusatz, as counted by UCDP."
        "KONFLIKTE" -> "Armed conflict: 25 to 999 battle deaths in $zusatz, with a state on at least one side."
        "SPANNUNG" -> "How often media report on troops, maneuvers and attacks: last seven days against the weeks before. From $zusatz an area counts as notable."
        "ENGPAESSE" -> "Ship transits per day, last seven days against the same period a year earlier. From a $zusatz drop a chokepoint counts as disrupted."
        "ENSO" -> ensoKurz
        "KATASTROPHEN_LAND" -> "Storms, floods, wildfires and droughts of the last 30 days with an orange or red alert from the GDACS warning system. Red means high impact."
        "DURCHBRUCH_THEMA" -> "Strictly filtered breakthrough reports. The number counts the last seven days. Below is the latest report."
        "BESCHLEUNIGT" -> "Measures that halve or double at regular intervals because progress drives itself."
        else -> ""
    }
    override fun lbWeg(key: String) = when (key) {
        "ZINSEN" -> "Go to outlook"; "TECHNIK" -> "Go to breakthroughs"; else -> "View on the globe"
    }
    override fun lbGegen(a: String, b: String) = "$a vs. $b"
    override val lbUnd = " and "
    override fun lbImMinus(n: Int) = "$n below zero"
    override fun lbAbSchwelle(n: Int, schwelle: String) = "$n at $schwelle or more"
    override fun lbGestoert(n: Int) = "$n disrupted"
    override val mtWelt = "World events in your portfolio"
    override val mtWeltHinweis = "At most one per day, when an event affects a holding in your portfolio."
    override fun mtWeltText(was: String, werte: String) = "$was. Affects $werte."
    override fun mtWeltEngpass(name: String, prozent: String) = "$name: transits $prozent vs prior year"
    override fun mtWeltSpannung(name: String, faktor: String) = "$name: $faktor times more reports than before"
    override fun mtWeltWerte(namen: List<String>, rest: Int) = namen.joinToString(", ") + (if (rest > 0) " and $rest more" else "")
    override val weltlageTitel = "World situation"
    override val ensoTitel = "El Niño and La Niña"
    override val ensoStatus = "Status"
    override val ensoIndex = "Index (RONI)"
    override val ensoStaerke = "Strength"
    override fun ensoZustand(z: String) = when (z) {
        "EL_NINO_ADVISORY" -> "El Niño under way"
        "EL_NINO_WATCH" -> "El Niño possible"
        "LA_NINA_ADVISORY" -> "La Niña under way"
        "LA_NINA_WATCH" -> "La Niña possible"
        "NEUTRAL" -> "Neutral"
        else -> "Unknown"
    }
    override fun ensoStaerkeText(n: Int) = when (n) {
        4 -> "El Niño very strong"
        3 -> "El Niño strong"
        2 -> "El Niño moderate"
        1 -> "El Niño weak"
        0 -> "Neutral"
        -1 -> "La Niña weak"
        -2 -> "La Niña moderate"
        -3 -> "La Niña strong"
        -4 -> "La Niña very strong"
        else -> ""
    }
    override val ensoStatusInfo = "NOAA alert system. Watch: conditions are favorable for El Niño or La Niña within the next six months. Advisory: the event is present and expected to continue."
    override val ensoIndexInfo = "Relative Oceanic Niño Index (RONI): deviation of sea surface temperature in the Niño 3.4 region on the equator in the Pacific, adjusted for the warming of the tropical oceans as a whole, as a three-month mean in degrees Celsius.\n\n• From +0.5 it counts as El Niño, below −0.5 as La Niña (NOAA).\n• The line shows the last 24 three-month values, dashed the thresholds."
    override val ensoStaerkeInfo = "NOAA classes by the three-month value: weak from 0.5, moderate from 1.0, strong from 1.5, very strong from 2.0 degrees, correspondingly negative for La Niña."
    override val ensoFolgenNino = "Effects of El Niño"
    override val ensoFolgenNinoInfo = "• Typical: drier in Indonesia, Malaysia, Australia, India (monsoon) and southern Africa, wetter on the coast of Peru and in the southern US (NOAA).\n• Affected growing regions: palm oil (Indonesia, Malaysia), sugar (India, Thailand), wheat (Australia), robusta coffee (Vietnam, Indonesia), rice.\n• Off Peru the catch of anchovies, the raw material of fishmeal, falls.\n\nHow strongly prices react varies by event."
    override val ensoFolgenNina = "Effects of La Niña"
    override val ensoFolgenNinaInfo = "• Typical: drier in the southern US and Argentina, wetter in Indonesia, Malaysia and Australia (NOAA).\n• Affected growing regions: soybeans, corn and wheat in Argentina, southern Brazil and the US.\n\nHow strongly prices react varies by event."
    override val spTitel = "Areas of tension"
    override val spErklaerung = "Reports on troops, drills, mobilization and attacks: last seven days against the weeks before."
    override fun spName(id: String) = when (id) {
        "UKR" -> "Ukraine and Russia"
        "TWN" -> "Taiwan"
        "NAO" -> "Iran and Israel"
        "GOL" -> "Persian Gulf"
        "ROT" -> "Red Sea and Yemen"
        "KOR" -> "Korea"
        "SCS" -> "South China Sea"
        "KAS" -> "India and Pakistan"
        "BAL" -> "Baltic and Belarus"
        "VEN" -> "Venezuela"
        "SDN" -> "Sudan"
        "SAH" -> "Sahel"
        else -> id
    }
    override fun spSuche(id: String) = when (id) {
        "UKR" -> "Ukraine Russia troops"
        "TWN" -> "Taiwan military drills"
        "NAO" -> "Iran Israel military strike"
        "GOL" -> "Persian Gulf tanker navy"
        "ROT" -> "Red Sea Houthi attack"
        "KOR" -> "North Korea missile military"
        "SCS" -> "South China Sea Philippines coast guard"
        "KAS" -> "India Pakistan border military"
        "BAL" -> "Belarus Poland Lithuania troops"
        "VEN" -> "Venezuela military US"
        "SDN" -> "Sudan fighting"
        "SAH" -> "Sahel Mali Burkina Faso attacks"
        else -> id
    }
    override fun spGegenDavor(faktor: String) = "$faktor vs the weeks before"
    override val spFaktor = "Coverage density"
    override val spFaktorInfo = "Share of articles on this region (troops, drills, mobilization, attacks) among all English-language articles recorded: last seven days divided by the average of the days before within the same 30-day window.\n\n• 1.0× means unchanged, 2.0× twice the coverage density.\n• A rise shows more coverage. It says nothing about the outcome."
    override val spArtikel = "Articles in 7 days"
    override val spArtikelInfo = "Matches of the search in the last seven days. If days are missing in the index, the figure is below the actual one."
    override fun spKonflikte(jahr: Int) = "Conflicts ($jahr)"
    override fun spKonfliktZeile(a: String, b: String, stufe: Int) = "$a vs $b (level $stufe)"
    override fun spKonfliktAnzahl(n: Int) = if (n == 1) "1 conflict" else "$n conflicts"
    override val spKonfliktInfo = "Level 2: 1000 or more battle deaths in the year, level 1: 25 to 999."
    // [Wirkungskette]
    override val wkTitel = "What the numbers mean"
    override fun wkLage(thema: String, lage: String): String {
        val n = when (thema) {
            "ARBEIT" -> "Labor market"
            "WACHSTUM" -> "Economic data"
            "KONSUM" -> "Consumer spending"
            "STIMMUNG" -> "Sentiment"
            "IMMOBILIEN" -> "Housing market"
            "PREISE" -> "Inflation"
            "LOEHNE" -> "Wages"
            else -> thema
        }
        val a = when (lage) {
            "STAERKER" -> when (thema) { "PREISE", "LOEHNE" -> "higher"; "STIMMUNG" -> "better"; else -> "stronger" }
            "SCHWAECHER" -> when (thema) { "PREISE", "LOEHNE" -> "lower"; "STIMMUNG" -> "worse"; else -> "weaker" }
            else -> return "$n as expected"
        }
        return "$n $a than expected"
    }
    override fun wkZinsKopf(zentralbank: String, fall: String) = "$zentralbank decision: " + when (fall) {
        "ZINS_STRAFFER" -> "rate higher than expected"
        "ZINS_LOCKERER" -> "rate lower than expected"
        else -> "rate as expected"
    }
    override fun wkFallKopf(fall: String) = when (fall) {
        "GOLDILOCKS" -> "Growth stronger, prices lower than expected"
        "HEISS" -> "Growth and prices higher than expected"
        "STAGFLATION" -> "Growth weaker, prices higher than expected"
        "SCHWACH_ENTSPANNT" -> "Growth and prices lower than expected"
        "GEMISCHT" -> "Releases point in different directions"
        else -> "Releases in line with expectations"
    }
    override fun wkArt(art: String) = when (art) {
        "STELLEN" -> "Employment"
        "STELLEN_ADP" -> "Employment (ADP)"
        "ARBEITSLOSENQUOTE" -> "Unemployment rate"
        "ERSTANTRAEGE" -> "Jobless claims"
        "ARBEITSLOSE_AENDERUNG" -> "Unemployed persons"
        "OFFENE_STELLEN" -> "Job openings"
        "LOEHNE" -> "Wages"
        "BIP" -> "GDP"
        "EINKAUFSMANAGER_ISM" -> "Purchasing managers index (ISM)"
        "EINKAUFSMANAGER" -> "Purchasing managers index"
        "PRODUKTION" -> "Industrial production"
        "AUFTRAEGE" -> "Orders"
        "EINZELHANDEL" -> "Retail sales"
        "KONSUMAUSGABEN" -> "Consumer spending"
        "VERTRAUEN" -> "Sentiment index"
        "ZEW" -> "ZEW expectations"
        "IMMOBILIEN" -> "Housing data"
        "VERBRAUCHERPREISE" -> "Consumer prices"
        "VERBRAUCHERPREISE_TOKIO" -> "Tokyo consumer prices"
        "ERZEUGERPREISE" -> "Producer prices"
        "PREISINDEX_UMFRAGE" -> "Purchasing managers price index"
        "INFLATIONSERWARTUNG" -> "Inflation expectations"
        "ZINSENTSCHEID" -> "Policy rate"
        else -> art
    }
    override fun wkWertStatt(art: String, ist: String, referenz: String) = "${wkArt(art)} $ist vs $referenz"
    override fun wkWertGleich(art: String, ist: String) = "${wkArt(art)} $ist"
    override val wkVorwertZusatz = " (no forecast, vs previous)"
    override fun wkZentralbank(region: String) = when (region) {
        "US" -> "Fed"
        "EURO" -> "ECB"
        "UK" -> "BoE"
        "JP" -> "BoJ"
        "CH" -> "SNB"
        else -> "PBoC"
    }
    override fun wkWaehrungName(region: String) = when (region) {
        "US" -> "Dollar"
        "EURO" -> "Euro"
        "UK" -> "Pound"
        "JP" -> "Yen"
        "CH" -> "Franc"
        else -> "Yuan"
    }
    override fun wkZins(folge: String, zentralbank: String) = when (folge) {
        "SENKUNG_WENIGER" -> "$zentralbank rate cuts less likely"
        "ERHOEHUNG_MEHR" -> "$zentralbank rate hike more likely"
        "SENKUNG_MEHR" -> "$zentralbank rate cuts more likely"
        "KEIN_DRUCK" -> "little rate pressure"
        "ZIELKONFLIKT" -> "$zentralbank facing a trade-off"
        "STRAFFER" -> "$zentralbank tighter than expected"
        "LOCKERER" -> "$zentralbank looser than expected"
        "LOCKERUNG_WENIGER" -> "less need for $zentralbank easing"
        else -> "$zentralbank easing more likely"
    }
    override fun wkRendite(r: String) = when (r) {
        "HOEHER" -> "yields tend to rise"
        "TIEFER" -> "yields tend to fall"
        else -> "yields without clear direction"
    }
    override fun wkWaehrung(r: String, name: String) = when (r) {
        "HOEHER" -> "$name tends to strengthen"
        "TIEFER" -> "$name tends to weaken"
        else -> "$name without clear direction"
    }
    override fun wkBelastet(liste: String) = "$liste under pressure"
    override fun wkGestuetzt(liste: String) = "$liste supported"
    override fun wkSektor(sektor: String) = when (sektor) {
        "TECH" -> "tech"
        "IMMOBILIEN" -> "real estate"
        "VERSORGER" -> "utilities"
        "KLEINWERTE" -> "small caps"
        "BANKEN" -> "banks"
        "ZYKLIKER" -> "cyclicals"
        "ENERGIE" -> "energy"
        "ROHSTOFFE" -> "materials"
        "DEFENSIV" -> "defensives"
        "EXPORTEURE" -> "exporters"
        else -> "luxury"
    }
    override val wkUnd = "and"
    override fun wkOhneFolge(fall: String) = when (fall) {
        "GEMISCHT" -> "The releases point in different directions. No clear consequence."
        "ZINS_ERWARTET" -> "The decision was priced in. The reaction depends on the central bank's outlook."
        else -> "The numbers were in line with expectations. Little new for rates and markets follows."
    }
    override val wkReaktion = "Reaction today"
    override fun wkMarkt(schluessel: String) = when (schluessel) {
        "^TNX" -> "US 10-year yield"
        "DX-Y.NYB" -> "Dollar index"
        "EURUSD=X" -> "Euro"
        "GBPUSD=X" -> "Pound"
        "JPY=X" -> "Yen"
        "CHF=X" -> "Franc"
        "CNY=X" -> "Yuan"
        "^GSPC" -> "S&P 500"
        "^NDX" -> "Nasdaq 100"
        "^RUT" -> "Russell 2000"
        "EXSA.DE" -> "STOXX Europe 600"
        "^STOXX50E" -> "Euro Stoxx 50"
        "^FTSE" -> "FTSE 100"
        "^N225" -> "Nikkei 225"
        "^SSMI" -> "SMI"
        "000001.SS" -> "Shanghai Composite"
        else -> schluessel
    }
    override fun wkUrteil(urteil: String) = when (urteil) {
        "PASST" -> "fits the typical chain"
        "ANDERS" -> "different from usual"
        "GEMISCHT" -> "partly as usual, partly different"
        else -> "too little movement to judge"
    }
    override fun wkZinsFutures(vorher: String, nachher: String) = "Policy rate in twelve months per futures: $nachher (previous day $vorher)"
    override fun wkBedeutung(folge: String) = "Meaning: $folge"
    override fun wkRueckblick(kopf: String, folge: String?) = "Economic data: $kopf" + (folge?.let { ", $it" } ?: "")
    override val wkBlattMeldungen = "Releases"
    override val wkBlattHerleitung = "Derivation"
    override val wkBlattReaktion = "Reaction on the day"
    override val wkBlattSektoren = "Sectors on the day"
    override val wkBlattGrenzen = "Limits"
    override val wkBlattBelege = "Evidence"
    override fun wkWarumZins(folge: String) = when (folge) {
        "SENKUNG_WENIGER" -> "Strong growth or high inflation make the central bank postpone cuts. Its reaction function follows inflation and slack (Taylor rule)."
        "ERHOEHUNG_MEHR" -> "Growth and prices are both above expectations. Both point to higher rates in the central bank's reaction function."
        "SENKUNG_MEHR" -> "Weaker growth or lower inflation give the central bank room to cut."
        "KEIN_DRUCK" -> "Strong growth with low inflation. The central bank has no reason to tighten or ease."
        "ZIELKONFLIKT" -> "Weak growth argues for cuts, high inflation against. The central bank's direction is open."
        "STRAFFER" -> "The rate is above expectations. The market prices a higher future rate path."
        "LOCKERER" -> "The rate is below expectations. The market prices a lower future rate path."
        "LOCKERUNG_WENIGER" -> "Stronger activity reduces the need for central bank support."
        else -> "Weaker activity or low prices raise the need for easing."
    }
    override fun wkWarumRendite(r: String) = when (r) {
        "HOEHER" -> "Higher rate expectations and stronger growth lift government bond yields."
        "TIEFER" -> "Lower rate expectations and weaker growth push government bond yields down."
        else -> "Growth worries push yields down, inflation lifts them. The direction is open."
    }
    override fun wkWarumWaehrung(r: String) = when (r) {
        "HOEHER" -> "Higher rate expectations and favorable growth news strengthen the currency. A stronger currency lowers exporters' foreign sales in home currency."
        "TIEFER" -> "Lower rate expectations and weaker growth news weaken the currency. A weaker currency supports exporters' foreign sales."
        else -> "Opposing forces, the direction is open."
    }
    override fun wkWarumSektor(sektor: String) = when (sektor) {
        "TECH" -> "Tech and growth stocks: Earnings lie far in the future, so their present value reacts strongly to rates (long equity duration). Rising rates weigh, falling rates help."
        "IMMOBILIEN" -> "Real estate: Financing costs and the yield comparison with bonds. Rising rates weigh, falling rates help."
        "VERSORGER" -> "Utilities: High dividends with high debt, the shares often trade like bonds. Rising rates weigh, falling rates help. The link is weaker in studies than for real estate."
        "KLEINWERTE" -> "Small caps: A much higher share of floating-rate debt than large groups. Rising rates weigh, falling rates help."
        "BANKEN" -> "Banks: Growth supports credit quality and margins, a downturn weighs on them. Surprise rate increases pushed bank shares down in one study, so banks are left out for policy decisions."
        "ZYKLIKER" -> "Cyclicals: Earnings follow the business cycle. Industrials and consumer discretionary react early to growth data."
        "ENERGIE" -> "Energy: Higher inflation often comes with higher energy prices. Activity in China and the US drives oil demand."
        "ROHSTOFFE" -> "Materials: Metal prices react to activity in China and the US."
        "DEFENSIV" -> "Defensives (staples, health care): Earnings fluctuate little. They are sought in weak phases."
        "EXPORTEURE" -> "Exporters: A stronger home currency lowers foreign sales in home currency, a weaker one supports them."
        else -> "Luxury: Sales depend strongly on demand from China."
    }
    override val wkGrenzen = "The chain describes the typical reaction. The market can react differently when the number was already priced in, other news dominates or the economic situation reverses the link. According to studies, rising unemployment is often good news for stocks in expansions and bad news in contractions. The daily move also contains everything else that happened that day."
    override val wkReaktionErklaerung = "Daily change against the previous close. A hit counts when yields move more than 2 basis points, a currency more than 0.1% or a sector more than 0.2 percentage points against the index in the direction of the chain. Fits when there are at least twice as many hits as deviations."
    override val wkBelege = "• Reaction function: Clarida, Galí, Gertler (2000)\n• Rate surprises and stocks: Bernanke, Kuttner (2005)\n• Labor data and stocks depending on the cycle: Boyd, Hu, Jagannathan (2005)\n• Currency: Andersen, Bollerslev, Diebold, Vega (2003)\n• Equity duration: Dechow, Sloan, Soliman (2004)\n• Banks: English, Van den Heuvel, Zakrajšek (2018)\n• China and commodities: Roache, IMF (2012)"
    override val wkBp = "bp"
    override val globusMehrGesellschaft = "Society"
    override val globusMehrStaat = "Government"
    override val globusMehrEnergie = "Energy and climate"
    override val globusMehrHandel = "Trade"
    override val globusMehrFeiertage = "Public holidays"
    override val globusMehrSchlagzeilen = "Headlines"
    override fun globusMehrLabel(k: de.troetenrepublik.app.engine.LaenderZusatz.Kennzahl) = when (k.name) {
        "LEBENSERWARTUNG" -> "Life expectancy"
        "MEDIANALTER" -> "Median age"
        "STADT" -> "Urban population"
        "WACHSTUM" -> "Population growth"
        "GEBURTEN" -> "Children per woman"
        "ERWERB" -> "Labor force"
        "FRAUEN" -> "Female labor force"
        "ARMUT" -> "Extreme poverty"
        "INTERNET" -> "Internet use"
        "HOCHSCHULE" -> "Tertiary enrollment"
        "ALPHA" -> "Literacy"
        "STEUER" -> "Tax revenue"
        "MILITAER" -> "Military spending"
        "FORSCHUNG" -> "R&D spending"
        "UEBERWEISUNG" -> "Remittances"
        "TOURISMUS" -> "Tourism receipts"
        "ERNEUERBAR" -> "Renewable energy"
        "ENERGIEIMPORT" -> "Net energy imports"
        "STROM" -> "Electricity access"
        "TREIBHAUS" -> "GHG per capita"
        "ALTER65" -> "Share aged 65+"
        "GESUNDHEIT" -> "Health spending"
        "BILDUNG" -> "Education spending"
        else -> k.name
    }
    override fun globusMehrInfo(k: de.troetenrepublik.app.engine.LaenderZusatz.Kennzahl) = when (k.name) {
        "LEBENSERWARTUNG" -> "Expected lifespan of a newborn in years at today's death rates.\n\n• High values go with better health care and higher income.\n• Low values hold back labor supply and consumption."
        "MEDIANALTER" -> "The age that splits the population into a younger and an older half.\n\n• Low values mean many young workers coming up, high values mean an aging society with rising pension and health costs.\n• UN estimate for 2025."
        "ALTER65" -> "Share of the population aged 65 and over, in percent. High values point to an aging society with rising pension and care costs and fewer workers per retiree."
        "STADT" -> "Share of the population living in cities, in percent. High values usually go with more industry and services, low values with a large farm sector."
        "WACHSTUM" -> "Yearly change in the number of inhabitants, in percent.\n\n• Positive means more workers and consumers.\n• Negative means the population is shrinking, mostly from low birth rates or emigration."
        "GEBURTEN" -> "Average number of children a woman would have over her lifetime.\n\n• About 2.1 keeps a population stable without immigration (replacement level).\n• Below that it shrinks in the long run, above it grows."
        "ERWERB" -> "Share of people aged 15 and over who work or look for work, in percent.\n\n• Low values reflect many retirees, people in education or people outside the labor market. They limit growth potential.\n• ILO estimate."
        "FRAUEN" -> "Share of women aged 15 and over who work or look for work, in percent.\n\n• The gap to overall participation shows unused labor potential.\n• ILO estimate."
        "ARMUT" -> "Share of the population living on less than 3.00 US dollars a day (World Bank international poverty line, 2021 prices).\n\nValues come from household surveys, so the survey year is shown."
        "INTERNET" -> "Share of the population that used the internet in the last three months, in percent.\n\n• High values ease digital trade and services.\n• Low values show room to catch up."
        "HOCHSCHULE" -> "Students enrolled in tertiary education as a percent of the age group that follows upper secondary school.\n\n• The value can exceed 100% because older students count too.\n• High values mean many graduates, low values little higher education."
        "ALPHA" -> "Share of adults aged 15 and over who can read and write, in percent. Low values hold back education and productivity."
        "STEUER" -> "Central government tax revenue as a percent of GDP, excluding social contributions.\n\n• High values mean a large state or high taxes.\n• Low values leave little room for investment and often lead to debt."
        "GESUNDHEIT" -> "Public spending on health as a percent of GDP. High values mean broad coverage but can weigh heavily on the budget. The OECD average sits near 6%."
        "BILDUNG" -> "Public spending on education as a percent of GDP. High values count as a long-term investment in skills and growth. UNESCO recommends at least 4 to 6%."
        "MILITAER" -> "Military spending as a percent of GDP.\n\n• High values point to conflict or rearmament and tie up budget funds.\n• NATO members agreed in 2025 to spend 3.5% of GDP on core defense by 2035."
        "FORSCHUNG" -> "Spending by government, firms and universities on research and development as a percent of GDP. High values point to a knowledge-based economy. The EU target is 3%."
        "UEBERWEISUNG" -> "Money that workers abroad send to relatives at home, as a percent of GDP. High values make the economy depend on the economic cycle of host countries. They also support consumption and the currency."
        "TOURISMUS" -> "Income from travel services (spending by foreign visitors) as a percent of exports of goods and services.\n\n• High values show dependence on tourism, which collapses quickly in a crisis.\n• Own calculation from balance-of-payments data."
        "ERNEUERBAR" -> "Share of renewable energy in total final energy consumption, in percent, so not only electricity. High values reduce dependence on fossil imports and on carbon prices."
        "ENERGIEIMPORT" -> "Net energy imports as a percent of energy use.\n\n• Positive means the country imports energy and depends on prices and suppliers.\n• Negative means it is a net exporter and uses less than it produces."
        "STROM" -> "Share of the population with access to electricity, in percent. Values below 100% show a need for investment in grids and power plants."
        "TREIBHAUS" -> "Greenhouse gases excluding land use, in metric tons of CO2 equivalent per person and year (all Kyoto gases).\n\nHigh values mean higher costs from carbon prices and climate rules, mainly for industry and energy."
        else -> ""
    }
    override val globusMehrFlaeche = "Area"
    override val globusMehrFlaecheInfo = "Total area of the country in square kilometers, including inland water."
    override fun globusMehrSprachen(n: Int) = if (n == 1) "Official language" else "Official languages"
    override val globusMehrSprachenInfo = "Languages with official status at national level."
    override fun globusMehrZeitzone(n: Int) = if (n == 1) "Time zone" else "Time zones"
    override val globusMehrZeitzoneInfo = "Current offset from Coordinated Universal Time (UTC), including daylight saving.\n\n• With several zones the earliest and the latest offset are shown.\n• Exchanges trade on local time."
    override val globusMehrBis = "to"
    override fun globusMehrWeitere(n: Int) = "and $n more"
    override fun globusMehrExporte(jahr: Int) = "Goods exports ($jahr)"
    override val globusMehrExporteInfo = "Value of all goods exported by the country in one year, in US dollars. Services are not included."
    override val globusMehrGueter = "Main export goods"
    override val globusMehrPartner = "Main buyers"
    override fun globusMehrGruppe(g: de.troetenrepublik.app.engine.LaenderZusatz.Warengruppe) = when (g.name) {
        "AGRAR" -> "Agriculture and food"
        "BRENNSTOFF" -> "Fuels and energy"
        "ERZ" -> "Ores and metals"
        "EDEL" -> "Precious metals and gems"
        "PHARMA" -> "Pharmaceuticals"
        "CHEMIE" -> "Chemicals"
        "KUNSTSTOFF" -> "Plastics and rubber"
        "TEXTIL" -> "Textiles, clothing, leather"
        "HOLZ" -> "Wood and paper"
        "MASCHINEN" -> "Machinery"
        "ELEKTRO" -> "Electronics and electrical equipment"
        "FAHRZEUG" -> "Vehicles, ships, aircraft"
        "INSTRUMENT" -> "Instruments and watches"
        "SONSTIGE" -> "Other goods"
        else -> g.name
    }
    override fun globusMehrGruppeInfo(g: de.troetenrepublik.app.engine.LaenderZusatz.Warengruppe, jahr: Int): String {
        val inhalt = when (g.name) {
            "AGRAR" -> "animals, meat, fish, dairy, vegetables, fruit, grain, oils, sugar, cocoa, beverages and tobacco"
            "BRENNSTOFF" -> "crude oil, natural gas, coal and oil products"
            "ERZ" -> "ores, iron, steel, copper, aluminum and other metals and articles made of them"
            "EDEL" -> "gold, silver, platinum, pearls and gemstones"
            "PHARMA" -> "medicines"
            "CHEMIE" -> "basic chemicals, fertilizers, paints, cosmetics and cleaning agents"
            "KUNSTSTOFF" -> "plastics, rubber and articles made of them"
            "TEXTIL" -> "textiles, clothing, leather, footwear"
            "HOLZ" -> "wood, cork, pulp, paper and books"
            "MASCHINEN" -> "machinery, boilers, turbines, engines and computers"
            "ELEKTRO" -> "electrical equipment, phones, semiconductors and batteries"
            "FAHRZEUG" -> "cars, trucks, rail vehicles, ships and aircraft"
            "INSTRUMENT" -> "optical, medical and measuring instruments, watches"
            else -> "salt, cement, glass, ceramics, furniture, toys, weapons and unclassified goods"
        }
        return "Share of this product group in goods exports ($jahr), tariff chapters ${g.kapitel}: $inhalt. Heavy reliance on one group makes exports depend on its prices and demand."
    }
    override fun globusMehrPartnerInfo(land: String, jahr: Int) = "Share of goods exports that go to $land ($jahr). A large buyer makes exports depend on its economic cycle and tariffs."
    override val globusMehrFeiertagInfo = "Nationwide public holiday. The exchange sets its own trading days, and on many holidays it stays closed."
    override val globusMehrSpiegel = "The country reports no trade data of its own. The figures come from the imports of its eight largest buyers and cover only their share."
    // [Durchbrueche]
    override val dbTitel = "Breakthroughs"
    override val dbInfo = "Per topic: new breakthroughs of the past 14 days (events that happened: approvals, firsts, confirmed records, phase 3 successes, journals), expected milestones with dates, approaches in development and a look back. The look back, expected items and series are curated by hand, not live. Not a buy recommendation."
    // [Durchbrueche 0.70]
    override val dbNeu = "New"
    override val dbErwartet = "Expected"
    override val dbEntwicklung = "In development"
    override val dbRueckblick = "Looking back"
    override val dbBeschleunigt = "Self-accelerating"
    override val dbKeineNeuen = "No new breakthroughs in the past 14 days"
    override val dbLog = "Logarithmic: equal height, equal factor"
    override fun dbNeuAnzahl(n: Int) = "$n new"
    override val dbZuletzt = "Recent"
    override fun dbZuletztDatum(jahr: Int, monat: Int, tag: Int, mitTag: Boolean): String {
        val name = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[(monat - 1).coerceIn(0, 11)]
        return if (mitTag) "$tag $name" else "$name $jahr"
    }
    override val dbNichtGeladen = "Not loaded"
    override fun dbArt(art: String) = when (art) {
        "angekuendigt" -> "announced"
        "hersteller" -> "planned per manufacturer"
        "betreiber" -> "planned per operator"
        "register" -> "study end per registry"
        else -> ""
    }
    override fun dbBis(jahr: Int) = "data to $jahr"
    override fun dbStand(jahr: Int, monat: Int) = "Look back and expected items: as of " + listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[(monat - 1).coerceIn(0, 11)] + " $jahr"
    override fun dbRate(faellt: Boolean, monate: Int): String {
        val zeit = if (monate < 18) "$monate months" else (kotlin.math.round(monate / 12.0 * 10) / 10.0).toString().removeSuffix(".0") + " years"
        return (if (faellt) "Halves about every " else "Doubles about every ") + zeit
    }
    override fun dbAlter(n: Long, einheit: de.troetenrepublik.app.engine.Durchbrueche.Einheit) = when (einheit) {
        de.troetenrepublik.app.engine.Durchbrueche.Einheit.MINUTEN -> "$n min"
        de.troetenrepublik.app.engine.Durchbrueche.Einheit.STUNDEN -> "$n h"
        de.troetenrepublik.app.engine.Durchbrueche.Einheit.TAGE -> "$n d"
    }
    override val heatmapOhneBranche = "Other"
    override val zeitraumEigener = "Custom period"
    override val zeitraumTitel = "Choose period"
    override val zeitraumStart = "Start date"
    override val zeitraumEnde = "End date"
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
    override fun kurzerHandelstag(time: String) = "Shortened trading day · closes at $time"

    override val portfolio = "Portfolio"
    override val cash = "Cash"
    override val openProfile = "Open profile"
    override val brokerage = "Brokerage"
    override val securities = "Securities"
    override val cashAccount = "Cash account"
    override val total = "Total"
    override val reloadPrices = "Reload prices"
    override val switchMetric = "Switch metric"
    override val watchlist = "Watchlist"
    override val savingsPlans = "Savings plans"
    override val orders = "Orders"
    override val analytics = "Analytics"
    override val statistics = "Statistics"
    override val investments = "Investments"
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
        "SALES" -> "Sales"; "DEPOSITS" -> "Deposits"; "EARNINGS" -> "Earnings"; "REFUNDS" -> "Refunds"; "TAX_REFUNDS" -> "Tax refunds"
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
    override val alsWidget = "As widget"
    override val addToWatchlist = "Add to watchlist"
    override val wllNeu = "New list"
    override val wllName = "Name"
    override val wllUmbenennen = "Rename"
    override val wllLoeschenTitel = "Delete list"
    override fun wllLoeschenText(name: String) = "List \"$name\" will be deleted. Its stocks stay on other lists."
    override val wllWaehlen = "Choose watchlists"

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
    override val isinPruefziffer = "The check digit of this ISIN is wrong. Check the entry."
    override val isinUnvollstaendig = "An ISIN has 12 characters."
    override val clear = "Clear"

    override val security = "Security"
    override val yourPosition = "Your position"
    override val quantity = "Quantity"
    override val avgPrice = "Average price"
    override val value = "Value"
    override val result = "Result"
    override val information = "Information"
    override val uebersetzen = "Translate"
    override val uebersetzenOriginal = "Original"
    override val uebersetzenFehler = "Translation failed"
    override val sourceWikipedia = "Wikipedia (CC BY-SA 4.0)"
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
    override val epsZwoelf = "Earnings per share (12 months)"
    override val dividendYield = "Dividend yield"
    override val volume = "Volume"
    override fun priceIn(ccy: String) = "Price in $ccy"
    override val volatility1y = "Volatility per year"
    override val beta = "Beta"
    override val isin = "ISIN"
    override val isinCopied = "ISIN copied"
    override val isinKopieren = "Copy ISIN"
    override fun kennzahlErklaerung(label: String): String? = when (label) {
        bid -> "The price at which you can sell the stock right now."
        ask -> "The price at which you can buy the stock right now. It is a little above the bid."
        spread -> "The gap between the buying and the selling price.\n\n• You lose this much if you buy and sell again straight away.\n• The smaller it is, the cheaper the trade."
        openPrice -> "The first price of today's trading day."
        dayHigh -> "The highest price today."
        dayLow -> "The lowest price today."
        prevClose -> "The closing price of the last trading day. Today's rise or fall is measured against it."
        high52 -> "The highest price in the last twelve months. Together with the low it shows how much the stock moved in a year."
        low52 -> "The lowest price in the last twelve months. Together with the high it shows how much the stock moved in a year."
        marketCap -> "What all of the company's shares are worth together on the stock market: price times number of shares. It is used to compare the size of companies."
        enterpriseValue -> "Market cap plus debt minus cash. Roughly what you would have to pay to buy the whole company including its debt."
        peRatio -> "Price divided by the earnings per share of the last twelve months.\n\n• A P/E of 15 means you pay 15 years of earnings.\n• A low value can mean cheap, a high one usually means that a lot of growth is expected."
        forwardPe -> "Price divided by the earnings per share analysts estimate for the next twelve months.\n\n• Well below the P/E means they expect earnings to rise, above it to fall.\n• Estimates can be wrong."
        eps -> "The company's profit over the last twelve months divided by the number of its shares. If it is negative, the company made a loss."
        dividendYield -> "The yearly payout relative to the price. 3% means: for 100 € in the stock you get about 3 € a year, as long as the dividend stays the same."
        volume -> "How many shares were traded today. A high volume shows that a lot is going on."
        volatility1y -> "How much the price moved over the last year. Above 40% is very bumpy, below 20% rather calm."
        beta -> "How strongly the stock moves with the market on average.\n\n• 1 means: when the market rises or falls by 1%, the stock moves the same on average.\n• 2 means twice as strongly, 0.5 half as strongly.\n• A stock can swing a lot and still have a small beta if its moves have little to do with the market."
        isin -> "The international identification number of the security. It is unique worldwide, so any bank can find it."
        kind -> "The type of security, for example stock, ETF or cryptocurrency."
        sector -> "The broad business area of the company, for example technology or health care."
        industry -> "The more specific industry within the sector."
        country -> "The country where the company has its headquarters."
        employees -> "How many people work for the company, according to its latest report."
        exchange -> "The exchange the displayed price comes from."
        bkKuv -> "Market cap divided by revenue over the last twelve months.\n\n• Useful for companies that make no profit yet.\n• A P/S of 2 means the company costs two years of revenue.\n• What counts as high depends a lot on the sector."
        priceToBook -> "Price divided by book value per share, the equity on the balance sheet. Below 1 the share costs less than the equity behind it according to the balance sheet."
        bkEvEbitda -> "Enterprise value (market cap plus debt minus cash) divided by earnings before interest, taxes, depreciation and amortization.\n\nDebt is included, so companies with a lot or little debt compare better."
        bkPeg -> "P/E divided by expected earnings growth.\n\n• Around 1 counts as fairly valued for its growth, well above as expensive.\n• If Yahoo has no value, the stock finder uses the trailing twelve-month P/E and the earnings growth to next year."
        bkFcfRendite -> "Free cash flow of the last twelve months (operating cash flow minus investment) divided by market cap.\n\nThis much money is left over each year, measured against what the whole company costs on the market."
        returnOnEquity -> "Profit divided by equity.\n\n• Shows how well the company uses its owners' money.\n• Above 20% counts as strong."
        bkGesamtkapitalRendite -> "Profit divided by all of the company's assets. Shows how much it gets out of its plants, equipment and cash."
        grossMargin -> "Share of revenue left after the direct cost of goods. A high gross margin points to pricing power."
        operatingMargin -> "Share of revenue left after all operating costs, before interest and taxes."
        bkEbitdaMarge -> "Share of revenue left before interest, taxes, depreciation and amortization."
        profitMargin -> "Share of revenue that ends up as profit."
        bkVerschuldung -> "Debt divided by equity.\n\n• 1 means as much debt as equity.\n• High values make a company vulnerable when rates rise or business weakens."
        bkLiquiditaet -> "Current assets divided by current liabilities. Above 1 the company can pay next year's bills from what it has."
        totalCash -> "Cash and assets the company can sell quickly."
        totalDebt -> "All interest-bearing debt of the company."
        freeCashflow -> "Money left after spending on running the business and on investments. From it a company pays dividends, buys back shares or repays debt."
        dividendPerShare -> "What the company pays out per share each year."
        payoutRatio -> "Share of profit paid out as dividends. Above 100% the company pays out more than it earns."
        bkDividendeFuenf -> "The average dividend yield of the last five years. If today's is well above, the share got cheaper or the dividend rose."
        bkDividendenWachstum -> "How much the dividend per share has grown per year in recent years."
        bkLeerQuote -> "Shares sold short as a share of the free float.\n\n• Short sellers bet on falling prices.\n• From 10% many, from 20% very many."
        bkLeerTage -> "How many trading days it would take at normal volume for all short sellers to buy back their shares."
        bkInsider -> "Share of stock held by founders, management and the board."
        bkInstitutionen -> "Share of stock held by funds, insurers, banks and other large investors."
        bkAktien -> "How many shares of the company exist."
        bkAktienVeraenderung -> "How the number of shares changed from the year before.\n\n• Fewer shares usually means buybacks, which raises your stake in the company.\n• More shares dilute it."
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
        bkKursCashflow -> "Market cap divided by operating cash flow of the last twelve months. The lower the figure, the less you pay for the cash the business brings in."
        bkZinsdeckung -> "Earnings before interest and taxes divided by the interest expense of the last fiscal year.\n\n• Shows how many times the company earns its interest.\n• Below 2 it gets tight.\n• Without reported interest expense, the field stays empty."
        bkRoic -> "Operating profit after tax divided by invested capital.\n\n• Shows how much the company makes from the money owners and lenders have put in.\n• From 15% the value counts as strong.\n• Invested capital here is equity plus debt, cash is not deducted."
        bkOwnerEarnings -> "Simplified after Buffett: operating cash flow minus all investment in equipment, divided by market cap.\n\nBuffett only deducts the investment needed to maintain the business. For growing companies the value therefore tends to be too low."
        kgvSchnitt -> "Price divided by the average earnings per share of the last fiscal years, usually four. Smooths earnings peaks and loss years that distort the normal P/E, especially for cyclical companies."
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
    override val derivatives = "Derivatives"
    override val news = "News"
    override val events = "Events"
    override val financials = "Financials"
    override val yearly = "Yearly"
    override val quarterly = "Quarterly"
    override val insights = "Insights"
    override val analysts = "Analysts"
    override fun fromEstimates(n: Int) = " from $n estimates"
    override fun targetPrice(price: String) = ", target price $price"
    override fun toTargetLage(prozent: String, richtung: Int) = if (richtung == 0) "Price at target" else if (richtung > 0) "$prozent to target" else "Price $prozent above target"
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
    override fun epsStatt(actual: String, estimate: String) = "$actual, expected $estimate"
    override val description = "Description"
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
    override val revenueGrowth = "Revenue quarter"
    override val earningsGrowth = "Earnings quarter"
    override val payoutRatio = "Payout ratio"
    override val dividendPerShare = "Dividend per share"
    override val priceToBook = "Price-to-book"
    override val noFinancials = "No financial data available for this security."
    override fun reportingCurrency(ccy: String) = "Figures in $ccy"
    override val epsHistory = "Earnings per share by quarter"
    override val estimate = "Expected"
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
    override fun calWins(n: Int) = "$n up"
    override fun calLosses(n: Int) = "$n down"
    override fun calExpectedAmount(amount: String) = "expected $amount"
    override val calLegendReceived = "Dividend received"
    override val calLegendExpected = "Dividend expected"
    override val calClosedTrades = "Closed trades"
    override val calDayResult = "Day result"
    override val calFees = "Fees"
    override val calAfterCosts = "After fees and taxes"
    override val calPositions = "Closed positions"
    override val calLegendReview = "Review"
    override val calReviewLaedt = "Loading …"
    override val calReviewKeineKurse = "No prices available for this day."
    override val calTradeExpiry = "Expiry"
    override fun calTradeSell(qty: String, price: String) = "Sale · $qty sh. at $price"
    override val calDivExpected = "expected, after tax"
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
    override val productCosts = "Product costs per year"
    override val derivativeRisk = "Leveraged products can lead to a total loss of the stake. The ex-ante cost information underlies this overview."
    override val cashAfter = "Balance afterwards"
    override val notePlaceholder = "Note (optional), e.g. your reason for the order"
    override val placeOrder = "Place order"
    override val orderNotExecuted = "The order was not executed. Check the current price and your balance."
    override val initialStop = "Initial stop price"

    override val noOpenOrders = "No open orders."
    override val doneOrders = "Completed"
    override val cancelOrder = "Cancel"
    override fun validUntil(ts: String) = "Valid until $ts"
    override fun orderStatus(s: OrderStatus) = when (s) {
        OrderStatus.OPEN -> "Open"; OrderStatus.FILLED -> "Executed"; OrderStatus.CANCELLED -> "Canceled"
        OrderStatus.REJECTED -> "Rejected"; OrderStatus.EXPIRED -> "Expired"
    }
    override fun trailingInfo(pct: String, stop: String) = "Trailing $pct · stop currently $stop"
    override fun reason(code: String) = when (code) {
        "EXPIRED" -> "Validity expired"; "PRODUCT_GONE" -> "Product no longer tradable"
        "LINKED" -> StrategieTexte.text(false, "zl.grund", emptyList())
        else -> error(code, "")
    }

    override val plan = "plan"
    override val plans = "plans"
    override fun planCount(n: Int) = "$n ${if (n == 1) "plan" else "plans"}"
    override val interval = "Interval"
    override fun intervalLabel(i: PlanInterval) = when (i) {
        PlanInterval.WEEKLY -> "Weekly"; PlanInterval.BIWEEKLY -> "Twice a month"; PlanInterval.MONTHLY -> "Monthly"; PlanInterval.QUARTERLY -> "Quarterly"
    }
    override val intervalBiweeklyDays = "on the 2nd and 16th"
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

    override val enterTargetPrice = "Enter price alert"
    override fun alertRises(price: String, pct: String) = "Notification once the price rises to $price (+$pct)"
    override fun alertFalls(price: String, pct: String) = "Notification once the price falls to $price (−$pct)"
    override val targetEqualsPrice = "The target equals the current price"
    override val setAlert = "Set price alert"
    override val whenRises = "when the price rises"
    override val whenFalls = "when the price falls"
    override fun setAt(price: String) = "set at $price"
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
        "• The light shows bull (green), correction (yellow) and bear (red).\n• The 200-day line shows the long-term trend.\n• When the 50-day line crosses it upwards it is called a golden cross, downwards a death cross.\n• Calculated from ten years of daily closes."
    override val cotTitel = "Futures positioning"
    override fun cotMarkt(schluessel: String) = when (schluessel) {
        "sp500" -> "S&P 500"; "nasdaq" -> "Nasdaq 100"; "gold" -> "Gold"; "oel" -> "Crude oil (WTI)"; "euro" -> "Euro vs. dollar"
        "anleihen" -> "10-year US Treasuries"; "bitcoin" -> "Bitcoin"
        "anleihen2" -> "2-year US Treasuries"; "anleihen30" -> "30-year US Treasuries"
        "yen" -> "Yen vs. dollar"; "pfund" -> "Pound vs. dollar"; "franken" -> "Swiss franc vs. dollar"
        "kanada" -> "Canadian dollar"; "australien" -> "Australian dollar"
        "silber" -> "Silver"; "platin" -> "Platinum"; "palladium" -> "Palladium"; "kupfer" -> "Copper"
        "gas" -> "Natural gas (US)"; "weizen" -> "Wheat"; "mais" -> "Corn"; "kaffee" -> "Coffee"; "kakao" -> "Cocoa"; "zucker" -> "Sugar"
        else -> schluessel
    }
    override val lpTitel = "Open short positions"
    override fun lpSumme(anteil: String, anzahl: Int) =
        if (anzahl == 1) "One fund is betting on falling prices with $anteil of the share capital" else "$anzahl funds are betting on falling prices with $anteil of the share capital combined"
    override fun lpVeraenderung(aenderung: String) = "$aenderung pts compared with four weeks ago"
    override val lpKeine = "No published position right now."
    override fun lpSeit(tag: String) = "since $tag"
    override fun lpNotiz(land: String, stand: String) = "As of $stand" + if (land in listOf("IT", "DE", "GB")) " · imported by you" else ""
    override fun lpGesamt(anteil: String) = "Short sellers are betting on falling prices with $anteil of the share capital combined"
    override fun lpHinweis(land: String) = if (land == "GB") "The UK regulator publishes only the total of all positions per share, without names. Reported from 0.2% of the share capital. The line shows the total over the last year. If short sellers have to cover, the price can rise." else "The EU publishes positions of 0.5% of the share capital or more with names, so the total is usually higher. The line shows the total of published net positions over the last year. If short sellers have to cover, the price can rise."
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
    override val fdaHinweis = "The US Food and Drug Administration (FDA) publishes recalls of drugs and medical devices every week. Class I means the product can cause serious harm to health. Entries are matched by company name and known subsidiaries, so single entries may be missing."
    override fun insiderNotiz(land: String, stand: String) = listOfNotNull(
        if (stand.isNotBlank()) "As of $stand" else null,
        when (land) {
            "AU", "DK", "FI", "IS", "EE", "LV", "LT", "LU", "CY", "HU", "IT" -> "Amounts read from the forms"
            "CH" -> "Without names"
            "QA" -> "Imported by you, share counts only"
            "FR", "NL", "GB", "HK", "CZ", "SE", "", "LK", "OM" -> null
            else -> "Imported by you"
        },
    ).joinToString(" · ")
    override val euSedi = "Canadian insiders report their trades to SEDI. The database can only be searched in a browser."
    override val euSediLink = "Open SEDI"
    override fun euArt(art: String) = when (art) { "K" -> "Buy"; "V" -> "Sell"; else -> "Other" }
    override fun euRolle(r: String) = when (r) { "S" -> "Senior management"; "F" -> "Executive"; "A" -> "Board member"; else -> "Closely associated person" }
    override val bbDafuer = "Positive points"
    override val bbDagegen = "Negative points"
    override val bbHinweis = "Put together automatically from headlines of the last 60 days and key figures, the evidence below shows the reason. Not investment advice."
    override val bbWaage = "Net score"
    override fun bbStufe(stufe: Int) = listOf("Clearly more negative", "Somewhat more negative", "Balanced", "Somewhat more positive", "Clearly more positive").getOrElse(stufe) { "" }
    override fun bbWaageSatz(stufe: Int) = listOf(
        "The negative points carry clearly more weight.",
        "The negative points carry somewhat more weight.",
        "Positive and negative points carry about equal weight.",
        "The positive points carry somewhat more weight.",
        "The positive points carry clearly more weight.",
    ).getOrElse(stufe) { "" }
    override val bbGewichtKopf = "Weight" to "Examples"
    override val bbGewichte = listOf(
        "three times" to "Accounting fraud, insolvency",
        "twice" to "Profit warning, investigation, loss, raised guidance",
        "half" to "Volatility, cyclical sector",
    )
    override val bbMehrfach = "Several headlines on the same topic count a little more."
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
        "ANALYSTEN_VERHALTEN" -> "The grade is the mean of the recommendations (1 buy to 5 sell). From 3 on, analysts lean toward hold, even if the target is above the price."
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
        "GEWINNE_STEIGEN" -> "A lower forward P/E means analysts expect rising earnings. A high P/E today can be explained this way."
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
        "KONGRESS_KAUFT", "KONGRESS_VERKAUFT" -> "Representatives and senators report trades with a delay. Trades are counted by filing date, not amounts."
        "STUFEN_HOCH", "STUFEN_AB" -> "Analyst upgrades and downgrades are counted. Reiterations are not."
        "WARNZEICHEN" -> "Restated financial statements, a delisting notice or bankruptcy are reported by the company itself at the SEC. These are the most serious warning signs."
        "DIVIDENDE_SOLIDE" -> "A covered dividend brings regular income and is more likely to last when business slows."
        "RENDITE_HOCH" -> "Return on equity shows how much the company makes from shareholders' money. Above 20% counts as high."
        "BEWERTUNG_NIEDRIG" -> "A low P/E can point to a price discount. Often the market has reasons for it."
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
        "ANALYSTEN_SKEPTISCH" -> "Analysts are skeptical: target ${w[0]} (${w[1]}), ${w[2]} estimates"
        "ANALYSTEN_VERHALTEN" -> "Analysts are reserved: grade ${w[3]}, target ${w[0]} (${w[1]}), ${w[2]} estimates"
        "WACHSTUM" -> "Revenue up ${w[0]} in the quarter"
        "SCHRUMPFT" -> "Revenue down ${w[0]} in the quarter"
        "GEWINNWACHSTUM" -> "Earnings up ${w[0]} in the quarter"
        "GEWINN_FAELLT" -> "Earnings down ${w[0]} in the quarter"
        "MARGE_HOCH" -> "High profit margin of ${w[0]}"
        "VERLUST" -> "Loss-making (margin −${w[0]})"
        "NETTOKASSE" -> "More cash than debt, net ${w[0]}"
        "SCHULDEN" -> "High net debt of ${w[0]}, ${w[1]} of market cap"
        "CASHFLOW_POSITIV" -> "Free cash flow of ${w[0]}, ${w[1]} of market cap"
        "CASHFLOW_NEGATIV" -> "Burns cash: free cash flow −${w[0]}"
        "UEBERTROFFEN" -> "Earnings per share above expectations in ${w[0]} of ${w[1]} quarters"
        "VERFEHLT" -> "Earnings per share below expectations in ${w[0]} of ${w[1]} quarters"
        "GEWINNE_STEIGEN" -> if (w[2].isEmpty()) "Analysts expect earnings to more than triple in twelve months (P/E ${w[0]} → forward ${w[1]})" else "Analysts expect ${w[2]} earnings in twelve months (P/E ${w[0]} → forward ${w[1]})"
        "BEWERTUNG_HOCH" -> "High valuation with a P/E of ${w[0]}"
        "SCHWANKUNG" -> "Moves more than the market (beta ${w[0]})"
        "ZYKLISCH" -> "Cyclical industry: earnings depend strongly on the economy"
        "DIVIDENDE_UNGEDECKT" -> "Dividend not covered by earnings (payout ${w[0]})"
        "INSIDER_KAUFEN" -> "Insiders are buying, 90 days (insider trend ${w[0]})"
        "LEERVERKAUF_HOCH" -> if (w.getOrNull(1) == "US") "Many short sellers: ${w[0]} of the float sold short" else "Many short sellers: ${w[0]} of the share capital in disclosed short positions"
        "FDA_ZULASSUNG" -> if (w[0] == "1") "One new product approved by the FDA in the last twelve months" else "${w[0]} new products approved by the FDA in the last twelve months"
        "FDA_RUECKRUF" -> if (w[0] == "1") "One FDA Class I recall (serious risk) in the last twelve months" else "${w[0]} FDA Class I recalls (serious risk) in the last twelve months"
        "NAHE_HOCH" -> "Price near its 52-week high: the uptrend is intact"
        "WEIT_UNTER_HOCH" -> "Price ${w[0]} below its 52-week high: the downtrend continues"
        "KONGRESS_KAUFT" -> "Congress buying, 90 days: ${sgKaeufeVerkaeufe(w[0].toInt(), w[1].toInt())}"
        "KONGRESS_VERKAUFT" -> "Congress selling, 90 days: ${sgKaeufeVerkaeufe(w[0].toInt(), w[1].toInt())}"
        "STUFEN_HOCH" -> "More upgrades than downgrades, 30 days: ${sgHochAb(w[0].toInt(), w[1].toInt())}"
        "STUFEN_AB" -> "More downgrades than upgrades, 30 days: ${sgHochAb(w[0].toInt(), w[1].toInt())}"
        "WARNZEICHEN" -> if (w[0] == "1") "One serious warning sign reported at the SEC in 24 months" else "${w[0]} serious warning signs reported at the SEC in 24 months"
        "DIVIDENDE_SOLIDE" -> "Dividend yield ${w[0]}, covered by earnings (payout ${w[1]})"
        "RENDITE_HOCH" -> "High return on equity of ${w[0]}"
        "BEWERTUNG_NIEDRIG" -> "Low valuation: forward P/E ${w[0]}"
        "INSIDER_VERKAUFEN" -> "Insiders are selling, 90 days (insider trend ${w[0]})"
        else -> kennzahl
    }
    override val vdKaeufe1 = "Buy"
    override val usHinweis = "Insiders report their purchases and sales to the SEC (Form 4): officers, directors, holders of 10% or more.\n\n• Shown are open market or private purchases and sales of the last 18 months, without grants, option exercises, tax withholding and gifts.\n• Sales under trading plans (Rule 10b5-1) are marked."
    override val usOhneKontakt = "New reports arrive with every version of the app. Without a contact address under Access keys the app does not fetch them itself."
    override fun usRolle(r: String) = when (r) { "S" -> "CEO, CFO, president or chair"; "F" -> "Officer"; "A" -> "Director"; else -> "Major holder or other" }
    override fun usBestandDanach(stueck: String, aenderung: String?) = "Holding after: $stueck shares" + (aenderung?.let { " ($it)" } ?: "")
    override val usBestaendeTitel = "How much insiders hold"
    override fun usStueck(stueck: String) = "$stueck shares"
    override fun usBestaendeSumme(wert: String, personen: Int) =
        "Together about $wert across " + (if (personen == 1) "1 person" else "$personen people") + ", valued at the last reported price."
    override val usBestaendeHinweis = "As of each person's latest filing. Newly loaded filings count only directly held shares. In the bundled data the holding may also be held through family companies or trusts. Unexercised options are missing, so the true stake is often larger."
    override val zgSecTitel = "SEC (US insiders)"
    override val zgSecFeld = "Contact address (e-mail)"
    override val zgSecUngueltig = "Please enter an e-mail address"
    override val erkTitel = "Explainer"
    override val erkZweck = "“Explain” button on the security page, in the financials sheet, at quarterly figures and in “Dependencies and risks”."
    override val erkPunkte = listOf(
        "Your own key, costs follow the provider's pricing",
        "The request goes straight to the provider, no app server in between",
        "Only the figures on the page are sent, no portfolio and no personal details",
        "No buy or sell advice, no price target, no forecast",
    )
    override val erkOpenai = "OpenAI-compatible"
    override val erkAdresse = "Base address"
    override val erkAdresseUngueltig = "Address must start with https:// and carry no credentials"
    override val erkModell = "Model"
    override val erkModellname = "Model name"
    override val erkKnopf = "Explain"
    override val erkBlatt = "Explanation"
    override val erkNachfrage = "Follow-up"
    override val erkSenden = "Send"
    override val erkNochmal = "Retry"
    override val erkSchluessel = "Key rejected"
    override val erkLimit = "Provider limit reached"
    override val erkUeberlastet = "Provider overloaded"
    override val erkNetz = "No connection"
    override val erkLeer = "No answer received"
    override fun erkAnbieterSagt(m: String) = if (m.isBlank()) "Provider rejects the request" else "Provider: $m"
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
    override val itClusterKurz = "cluster buy, 30 days"
    override val itHinweis = "Each person counts once with the direction of their trades in the period, weighted by their relationship to the company:\n• CEO, CFO and management board ×3\n• other officers ×2\n• board members ×1.5\n• close associates and major holders ×1\n\nSales under trading plans count half.\n\nFor US stocks the size relative to the holding counts: purchases of 20% or more and sales of half or more count one and a half times, additions under 2% and sales under 10% count half.\n\nOnly purchases and sales on the market are counted."
    override val copyExecutives = "Insiders"
    override val vdMeistgekauft = "Most buying by management, 90 days"
    override val vdMeistverkauft = "Most selling by management, 90 days"
    override val vdNeueste = "Latest reports"
    override val vdKaeufe = "Buys"
    override val vdVerkaeufe = "Sells"
    override val vdLeer = "No reports loaded right now. Open this again later."
    override fun insStand(bis: String) = "Data up to $bis"
    override fun insLuecke(von: String, bis: String) = "No data between $von and $bis"
    override val vdKarteTitel = "Buying and selling by management"
    override val kernMeldungen = "Filings"
    override fun kernKaeufe(n: Int) = if (n == 1) "1 buy" else "$n buys"
    override fun kernVerkaeufe(n: Int) = if (n == 1) "1 sale" else "$n sales"
    override fun kernNetto(betrag: String) = "net $betrag"
    override fun kernGross(n: Int, groesster: String) = "$n · largest $groesster"
    override fun kernHalter(anteil: String) = "Institutions $anteil"
    override fun kernHalterUnten(insider: String, anzahl: Int) = listOfNotNull(if (insider.isNotBlank()) "Insiders $insider" else null, if (anzahl > 0) "${"%,d".format(java.util.Locale.US, anzahl)} institutions" else null).joinToString(" · ")
    override fun kernKaufen(n: Int) = "$n buying"
    override fun kernVerkaufen(n: Int) = "$n selling"
    override fun kernHalten(n: Int) = "$n holding"
    override fun kernTrend(prozent: String, einheit: Int) = "$prozent " + (if (einheit == 1) "views" else "mentions") + " vs. last week"
    override fun kernTrendQuellen(n: Int) = if (n == 1) "1 source" else "$n sources"
    override fun kernPositionen(n: Int) = if (n == 1) "1 position" else "$n positions"
    override fun kernPersonen(n: Int) = if (n == 1) "1 person" else "$n people"
    override val lpLeerverkaeufer = "Short sellers"
    override fun trKennErw(n: Int) = (if (n == 1) "1 mention" else "${"%,d".format(java.util.Locale.US, n)} mentions") + " in 7 days"
    override fun trKennBeitraege(n: Int) = (if (n == 1) "1 post" else "${"%,d".format(java.util.Locale.US, n)} posts") + " in 7 days"
    override val trYahooKurz = "trending now"
    override val grTitel = "Major shareholders"
    override val grHinweis = "Reported voting rights from 3% under the German Securities Trading Act, including instruments, as of each report."
    override fun vdAnzahl(kaeufe: Int, verkaeufe: Int) = listOfNotNull(
        if (kaeufe > 0) if (kaeufe == 1) "1 buy" else "$kaeufe buys" else null,
        if (verkaeufe > 0) if (verkaeufe == 1) "1 sell" else "$verkaeufe sells" else null,
    ).joinToString(" · ")
    override fun vdOhneKurs(n: Int) = "(${if (n == 1) "1 filing" else "$n filings"} in other currencies not included)"
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
    override val alertMoveUngueltig = "Please enter between 0.5 and 50%"
    override fun notifAlertMoveText(pct: String, price: String) = "Today $pct · price $price"
    override val notiz = "Note"
    override val notizHinzufuegen = "Add note"
    override val exportNotizen = "Notes are included"
    override val notizLoeschen = "Delete note"

    override fun availableAmount(amount: String) = "Available: $amount"
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
    override val spreadHint = "100 basis points equal one percent. At 0, trades use the mid price."
    override val appearance = "Appearance"
    override val appearanceSystem = "System"
    override val appearanceLight = "Light"
    override val appearanceDark = "Dark"
    override val appearanceSepia = "Sepia"
    override val logos = "Logos"
    override val logosFarbig = "Color"
    override val logosSchwarzWeiss = "Black and white"
    override val logoForm = "Logo shape"
    override val logoRund = "Round"
    override val logoEckig = "Square"
    override val logoHintergrund = "Logo background"
    override val hintergrundBehalten = "Keep"
    override val hintergrundEntfernen = "Remove"
    override val trennlinien = "Dividers"
    override val trennAn = "On"
    override val trennAus = "Off"
    override fun beispielName(k: Int) = "Example ${'A' + k}"
    override val language = "Language"
    override val languageGerman = "Deutsch"
    override val languageEnglish = "English"
    override val coloredChart = "Chart in green and red"
    override val esDesign = "Theme"
    override val esNeutral = "Neutral"
    override val esWichtigKategorie = "Category in Top stories"
    override fun esUntertitel(design: String, logosFarbig: Boolean, rund: Boolean) = "$design · ${if (logosFarbig) "Color" else "Black and white"} logos, ${if (rund) "round" else "square"}"
    override val notifications = "Notifications"
    override val refreshEvery = "Refresh"
    override fun everySeconds(n: Int) = "every $n seconds"
    override val statusLimited = "limited"
    override val statusConnected = "connected"
    override val refreshNow = "Refresh now"
    override val exportPortfolio = "Export portfolio"
    override val importPortfolio = "Import portfolio"
    override val aboutTitle = "About the app"
    override val kaffeeTitel = "Buy me a coffee"
    override val aboutText = "Tryfolio is a practice portfolio: prices are real and may be delayed, trading uses virtual money. No real money flows, no securities are bought and there is no connection to a bank or broker. The app shows and explains. It gives no investment advice or recommendations, and past performance says nothing about the future.\n\nData from registers and authorities is fetched for your personal use only. Under US law (5 U.S.C. § 13107) filings by members of Congress, senators and the administration may not be used for commercial purposes. Tryfolio is non-commercial.\n\nTryfolio is free software under the GNU Affero General Public License 3.0 or later. The source code is on GitHub: github.com/regard2462/Tryfolio."
    override val version = "Version"
    override val resetDepot = "Reset portfolio"
    override val resetTitle = "Reset portfolio?"
    override fun resetText(amount: String) = "All positions, orders, savings plans, price alerts and bookings will be deleted. The balance restarts at $amount."
    override val reset = "Reset"
    override val personalData = "Personal data"
    override val firstName = "Your name"
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
    override val allowanceSingle = "Single"
    override val allowanceJoint = "Joint"
    override val allowanceNotSetTitle = "Not set up"
    override fun allowanceUsed(used: String, year: Int, free: String) = "$used used in $year · $free remaining"
    override val activate = "Activate"
    override val manage = "Manage"
    override fun taxOverviewYear(year: Int) = "Tax overview $year"
    override val taxRate = "Tax rate"
    override fun taxRateValue(pct: String) = "25% + 5.5% solidarity surcharge = $pct"
    override fun taxWerteGeschaetzt(jahr: Int, mit: Int) = "There are no tax values for $jahr, the app uses those of $mit."
    override val taxWithheld = "Taxes withheld"
    override val realizedStockGains = "Realized stock gains"
    override val otherIncome = "Other income (ETF, derivatives, interest, dividends)"
    override val lossPotStocks = "Loss pot stocks"
    override val privateGains = "Crypto held under a year (§ 23, not withheld)"
    override val lossPotGeneral = "Loss pot other"
    override val taxNote = "• Losses are first offset against this year's income, overpaid tax is refunded, the rest reduces later gains\n• Stock losses only offset stock gains, all other losses offset all other income\n• Sales follow FIFO, fees reduce the gain\n• Equity ETFs: 30% tax-free (partial exemption), mixed funds 15%\n• Crypto: no tax deducted. After more than a year the gain is tax-free, otherwise it belongs in the tax return at the personal rate (exemption limit 1,000 € a year)\n• Not included: advance lump sum (Vorabpauschale), foreign withholding tax, church tax\n• At the turn of the year the pots carry over and the allowance starts afresh"
    override val taxData = "Tax data"
    override val taxId = "Tax ID"
    override val editTaxId = "Edit tax ID"
    override val churchTax = "Church tax"
    override val notModelled = "not included"
    override fun taxCertificate(year: Int) = "Show annual tax certificate $year"

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
        val prefix = when (via) { "PLAN" -> "Savings plan · "; "ORDER" -> "Order · "; "DIVIDEND" -> "Dividend reinvested · "; "SETTLE_COVER" -> "Settlement · "; else -> "" }
        return when (tag) {
            "TRANSFER_IN" -> "Imported · $qty at $price"
            "BUY" -> "${prefix}Buy $qty at $price"
            "SELL" -> "${prefix}Sell $qty at $price ($realized)"
            "FEE" -> stored.ifBlank { "" }
            "INTEREST" -> "$extra p.a. on the balance"
            "DIVIDEND" -> "Dividend $qty × $price"
            "KO_SETTLE" -> "Knock-out · refund $price per unit"
            "EXPIRY" -> "Maturity · payout $price per unit"
            "EXPIRY_WORTHLESS", "EXPIRY_SOLD_WORTHLESS" -> "Maturity · expired worthless"
            "SELL_OPEN" -> "${prefix}Sell to open $qty at $price"
            "BUY_CLOSE" -> "${prefix}Buy to close $qty at $price ($realized)"
            "EXPIRY_SOLD" -> "Maturity · settlement $price per unit"
            "ALERT_ABOVE" -> "Price above $extra · now $price"
            "ALERT_BELOW" -> "Price below $extra · now $price"
            "ALERT_MOVE" -> "Daily move $extra · now $price"
            "ALERT_SMA_UP", "ALERT_SMA_DOWN", "ALERT_VOLUME", "ALERT_EXDIV", "ALERT_INSIDER" -> alertArtText(tag, extra, price)
            "ALERT_INSIDER_SELL", "ALERT_HIGH52", "ALERT_LOW52", "ALERT_EARNINGS", "ALERT_8K" -> alertNeuText(tag, extra, price)
            "ALERT_ANALYST_UP", "ALERT_ANALYST_DOWN", "ALERT_RULE_BUY", "ALERT_RULE_SELL" -> alertNeuText(tag, stored, price)
            "ALERT_NEWS" -> stored
            "PLAN_FAILED" -> "Execution failed · balance insufficient for $extra"
            "PLAN_SKIPPED" -> if (extra == "1") "1 date skipped while the app was closed" else "$extra dates skipped while the app was closed"
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
    override fun statementTitle(month: String) = "Statement $month"
    override val noBookings = "No bookings yet."
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
    override val docRealized = "Realized result"
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
    override val docRealizedGains = "Realized gains/losses"
    override val docDividendsGross = "Dividends gross"
    override val docAllowanceUsed = "of which used"
    override val docLossPotStocks = "Loss pot stocks (balance)"
    override val docLossPotGeneral = "Loss pot other (balance)"
    override val docTaxWithheld = "Capital income withholding tax withheld incl. solidarity surcharge"
    override val docDisclaimer = "This document was generated by a practice app. It has no tax or legal effect."
    override val docPracticeLine = "Practice portfolio · no real money, no real bank"
    override val docNoTaxEffect = "Practice portfolio"

    override fun stepOf(n: Int, total: Int) = "Step $n of $total"
    override val welcome = "Welcome"
    override val welcomeText = "Practice portfolio with real prices and virtual money."
    override val startCapital = "Starting balance"
    override val startCapitalText = "Virtual money to start with, top up any time."
    override val letsGo = "Let's go"
    override val noHistory = "No history available"

    override val toolsTitle = "Tools"
    override fun toolTitle(key: String) = when (key) {
        "POSITION_SIZE" -> "Position size"; "SELL_TAX" -> "Sale with tax"; "HEBEL" -> "Leverage simulator"
        else -> key
    }
    override fun toolSubtitle(key: String) = when (key) {
        "LUMP_SUM" -> "What an amount would have become"; "POSITION_SIZE" -> "Quantity matching stop and risk"
        "SELL_TAX" -> "What remains after tax and fee"; "HEBEL" -> "Daily leveraged price in hindsight"
        else -> ""
    }
    override fun toolYears(n: Int) = if (n == 1) "1 year" else "$n years"
    override fun toolYearsDativ(n: Int) = toolYears(n)
    override fun toolYearsKurz(n: Int) = "${n}Y"
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
    override val toolExecutions = "Purchases"
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
    override val toolRiskNote = "Volatility is the annualized standard deviation of daily price changes."
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
    override val toolStopDistance = "Distance to stop"
    override val toolDepotValue = "Portfolio value"
    override val toolStopPrice = "Stop price"
    override val toolLossPerUnit = "Loss per unit up to the stop"
    override val toolMaxLoss = "Maximum loss"
    override val toolCapital = "Capital required"
    override fun toolShareOfDepot(pct: String) = "$pct of the portfolio"
    override val toolDepotShare = "Share of portfolio"
    override val toolMoreThanCash = "The capital required is higher than your available cash."
    override val toolPositionNote = "A stop limits the loss as long as the price falls without gaps. With price jumps, for example overnight, the loss can be larger."
    override val toolSellShare = "Share to sell"
    override val toolSellPrice = "Sale price"
    override val toolProceeds = "Proceeds"
    override val toolRealized = "Gain or loss"
    override val toolSellNote = "Calculated like a real sale with FIFO, fees, loss pots and allowance. Nothing is booked."

    override val importPosition = "Import position"
    override val importIntro = "Your cash balance stays the same."
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
    override val maxDrawdown = "Largest drawdown"
    override fun maxDrawdownAb(datum: String) = "Calculated from $datum"
    override val largestPosition = "Largest position"
    override val assetClasses = "Asset classes"
    override fun assetClass(key: String) = when (key) { "STOCKS" -> "Stocks"; "ETFS" -> "ETFs"; "CRYPTO" -> "Crypto"; "INDEX" -> "Indices"; "DERIVATIVES" -> "Derivatives"; else -> key }

    // Tools in Analytics
    override fun ptTitle(key: String) = when (key) {
        "PERFORMANCE" -> "Portfolio Performance"; "CHECK" -> "Portfolio check"; "FINDER" -> "Stock finder"; "WEALTH" -> "Wealth builder"; "SPARPLAN" -> "Savings plan lookback"; "WITHDRAWAL" -> "Withdrawal plan"
        "CRASH" -> "Crash test"; "SCENARIO" -> "What if"; "DIVIDENDS" -> "Dividend calendar"; "DIVERSIFICATION" -> "Diversification"; "REBALANCE" -> "Rebalancing"; "OPTIONS" -> "Options calculator"; "STILLHALTER" -> "Sell options"; "AUFFAELLIG" -> "Unusual options"; "STUFEN" -> "New analyst ratings"
        "HAEUSER" -> "House ranking"; "MARKT" -> "Beating the market"; "HEBEL" -> "Leverage simulator"; "GRENZE" -> "Efficient frontier"
        "BACKTEST" -> "Portfolio backtest"
        "LANGSICHT" -> "Market history"
        "ROTATION" -> "Momentum rotation"
        "FAKTOREN" -> "Factors"
        else -> key
    }
    override val wzGruppeDepot = "My portfolio"
    override val wzGruppePlanen = "Plan"
    override val wzGruppeFinden = "Find stocks"
    override val ptCheckRules = "Rules of thumb"
    override val ptCheckRulesSub = "Clusters, costs, reserve and other rules of thumb with traffic lights"
    override fun ptSubtitle(key: String) = when (key) {
        "FINDER" -> "Filters for valuation, quality, trend"
        "OPTIONS" -> "Strategies from the option chain"
        "PERFORMANCE" -> "Return, capital account"
        "CHECK" -> "Rules of thumb with traffic lights"
        "WEALTH" -> "Savings rate and compounding over the years"
        "SPARPLAN" -> "The same rate since back then"
        "WITHDRAWAL" -> "Target wealth and withdrawals in historical markets"
        "CRASH" -> "Your portfolio or a test portfolio in the big crises since 2000"
        "SCENARIO" -> "Worked examples for commodity prices, rates, the dollar and events"
        "DIVIDENDS" -> "Payouts of the next twelve months"
        "DIVERSIFICATION" -> "Regions, industries, currencies and co-movement"
        "REBALANCE" -> "What to buy or sell to reach your target weights"
        "STILLHALTER" -> "Premiums for calls and puts"
        "AUFFAELLIG" -> "Volume against open interest"
        "STUFEN" -> "Upgrades and downgrades per company"
        "HAEUSER" -> "Hit rate of analyst houses"
        "MARKT" -> "What studies show about funds and retail investors"
        "HEBEL" -> "Daily leveraged price in hindsight"
        "GRENZE" -> "Volatility and return of your holdings with other weights"
        "BACKTEST" -> "Fixed mix of securities, daily prices since launch"
        "LANGSICHT" -> "Asset classes since 1871, yearly values, real"
        "ROTATION" -> "Hold the strongest securities each month, tested on past prices"
        "FAKTOREN" -> "Market, size, value, quality and momentum in the portfolio"
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
    override val ptInvested = "Invested"
    override val ptDepotToday = "Your portfolio today"
    override val ptDepotTodayHint = "Securities, sale proceeds and dividends"
    override fun ptWith(name: String) = "Value with $name"
    override val ptDifference = "Difference"
    override val ptPerYear = "Return per year"
    override fun ptNoPricesBefore(name: String, date: String) = "$name only has prices from $date. Earlier purchases can't be replayed with it."
    override val ptSameMoneyNote = "The same purchases and sales on the same day and with the same amount in the comparison, without fees and taxes."
    override fun ptCashNote(rate: String) = "Savings account at today's rate of $rate for the whole period."
    override fun ptSingleTitle(name: String) = "Single holdings vs $name"
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
    override val ptWeak = "Worst 10% end below"
    override val ptStrong = "Best 10% end above"
    override val ptPaidIn = "Paid in"
    override val ptGrowth = "Of which growth"
    override fun ptTargetChance(pct: String) = "in $pct of scenarios"
    override fun ptTargetReached(amount: String) = "Target $amount reached"
    override val ptTargetMedian = "Reached in the middle scenario"
    override fun ptTargetWhen(years: Int) = if (years <= 1) "within a year" else "after $years years"
    override val ptTargetNever = "not within this period"
    override fun ptInYears(n: Int) = if (n == 0) "Today" else if (n == 1) "In 1 year" else "In $n years"
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
    override fun ptDurationKurz(days: Long): String {
        val months = Math.round(days / 30.44).toInt()
        val y = months / 12
        val m = months % 12
        return when {
            days < 45 -> "$days d"
            y == 0 -> "$m mo"
            m == 0 -> "$y y"
            else -> "$y y $m mo"
        }
    }
    override val ptCrashVerlust = "Loss"
    override val ptCrashAmStaerksten = "Hit hardest"
    override val ptCrashErholung = "S&P 500 recovery"
    override val ptCrashErholungOffen = "open"
    override fun ptEstimated(n: Int, total: Int) = "$n of $total holdings didn't exist back then. They are estimated via their beta."
    override val ptCustomScenario = "Your own scenario"
    override val ptMarketFalls = "The S&P 500 falls by"
    override val ptEstimatedDepot = "Your portfolio, estimated"
    override val ptCrashNote = "Exchange rates, dividends and purchases during the crisis are not included."
    override val ptCrashMein = "My portfolio"
    override val ptCrashTest = "Test portfolio"
    override val ptTestSuche = "Search for a stock or ETF"
    override val ptTestUebernehmen = "Copy from my portfolio"
    override val ptTestLeer = "No holdings in the test portfolio yet."
    override val ptTestBetrag = "Amount in euros"
    override val ptTestEntfernen = "Remove"
    override val ptTestSumme = "Total"

    // [0.75] Tools
    override val ohneVerbindung = "No connection. The data could not be loaded."
    override val erneutVersuchen = "Try again"
    override fun ptBetaRichtwert(n: Int, total: Int) =
        (if (n >= total) "No holding has a measured beta. All follow" else "$n of $total holdings have no measured beta. They follow") +
            " the market with a standard value: stocks and funds 1.0, crypto 1.5, commodities 0.3."
    override fun ptVarNoteBestand(anzahl: Int, schwankung: String) =
        "From $anzahl simulated paths. Return as above, volatility from the prices of today's holdings over the last three years ($schwankung per year)."
    override val ptDivBrutto = "gross, without German tax"
    override val ptDivPerMonthBrutto = "Per month on average, before tax"
    override val ptDivNoteBrutto = "Projected from the payouts of the last twelve months with the same ex-dates and amounts per share, the estimated holdings and today's exchange rate. Accumulating ETFs do not appear. Gross: the person pays the taxes of their own country, German withholding tax does not apply to them."
    override fun toolEinStueckZuViel(jeStueck: String, erlaubt: String) = "A single unit already risks $jeStueck, more than the allowed $erlaubt."
    override val afOhneAntwort = "No response, usually the connection is missing. The last data stays."
    override val abhAbgelaufen = "Expired classifications"
    override fun abhAbgelaufenHinweis(datum: String) = "Valid until $datum. Hidden until new data arrives."
    override val ptCrashInfoTitel = "How it is calculated"
    override val ptCrashInfo = "• Holdings with prices from the crisis: actual move\n• Younger holdings: beta to the S&P 500 over the last three years, from weekly returns\n• Derivatives: revalued at the changed price of the underlying, knock-outs may be knocked out"
    override val ptFxTitel = "Currency shock"
    override val ptFxInfo = "The home currency of the portfolio rises or falls against every other currency.\n\nHoldings in foreign currency are then converted into the home currency, ETFs by the currency of their countries.\n\nOnly the conversion is calculated, not how companies react to the exchange rate."
    override fun ptFxSteigt(waehrung: String) = "$waehrung rises"
    override fun ptFxFaellt(waehrung: String) = "$waehrung falls"
    override val ptFxUm = "Against every other currency by"
    override val ptFxFremd = "In foreign currency"
    override fun ptFxZeile(anteil: String) = "In foreign currency: $anteil"
    override fun ptFxKeine(waehrung: String) = "All holdings are in $waehrung. A currency shock changes nothing."
    override val szTitel = "Worked examples"
    override val szInfo = "Worked example from past price moves, with the overall market and the exchange rate unchanged. The relationships are weak and change over time.\n\n• It is calculated from four-week returns of the last six years. Only supported relationships count, all other holdings count as 0.\n• The range runs from the smallest to the largest measured relationship, widened by twice the measurement uncertainty.\n• In nine price moves of oil, gold and copper since 2013 the direction was right in all 25 supported cases, in 18 the move was inside the range.\n• For rates (yield of ten-year US government bonds) and the dollar the direction was wrong in 4 of 29 cases, so there are no measured numbers. The holdings with a past relationship and those with a dollar share are shown. For the dollar the pure conversion of the dollar share is shown.\n• Events have no number. The affected holdings are shown. The raw material embargo hits industries that need raw materials from few producing countries.\n• Cash and derivatives are not included."
    override fun szName(key: String, schock: String) = when (key) {
        "OEL" -> "Oil price $schock"; "ZINS" -> "Rates $schock"; "DOLLAR" -> "Dollar $schock"
        "KUPFER" -> "Copper $schock"; "GOLD" -> "Gold $schock"; else -> key
    }
    override fun szPunkte(n: Int) = (if (n < 0) "−" else "+") + kotlin.math.abs(n) + (if (kotlin.math.abs(n) == 1) " point" else " points")
    override val szEigenerWert = "Own value"
    override val szEreignisse = "Events"
    override fun szEreignis(key: String) = when (key) {
        "TAIWAN" -> "Taiwan blockade"; "HORMUS" -> "Closure of Hormuz"; "EMBARGO" -> "Raw material embargo"; else -> key
    }
    override fun szSpanne(a: String, b: String) = "$a to $b"
    override fun szBelegt(anteil: String) = "$anteil of the portfolio with data"
    override val szKeinBeleg = "no data"
    override val szNichtBerechenbar = "no figure"
    override fun szBetrifft(anteil: String) = "affects $anteil of the portfolio"
    override val szKeinTreffer = "no holding affected"
    override val szKeineBelegten = "No holding with a supported relationship."
    override val szGroessteBeitraege = "Largest contributions"
    override val szBetroffenePositionen = "Affected holdings"
    override fun szAnteil(anteil: String) = "Share $anteil"
    override fun szBraucht(rohstoffe: String) = "needs $rohstoffe"
    override fun szFortschritt(erledigt: Int, gesamt: Int) = "Prices $erledigt of $gesamt loaded"
    override fun szDerivate(anteil: String) = "Derivatives not included: $anteil of the portfolio"
    override fun szRichtung(richtung: Int) = if (richtung > 0) "has moved with" else if (richtung < 0) "has moved against" else ""
    override fun szDollarAnteil(anteil: String) = "In dollars: $anteil"
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
    override val ptDivNote = "Projected from the payouts of the last twelve months with the same ex-dates and amounts per share, today's holdings and today's exchange rate. Accumulating ETFs do not appear. The payment day is the announced date, otherwise three days after the ex-date. Amounts after tax, with this year's remaining tax-free allowance."
    override val ptTop3 = "Top three combined"
    override val ptTop10 = "Top 10 combined"
    override val ptHhi = "Herfindahl index"
    override val ptHhiHint = "0 = evenly spread, 10,000 = everything in one position. Rule of thumb from market supervision: below 1,500 diversified, up to 2,500 moderately concentrated, above that heavily concentrated."
    override val ptCountriesBip = "Countries vs world GDP"
    override val ptCountriesBipNote = "Bar: your share per country, ETFs resolved into their holdings. Mark: the country's share of world economic output in current US dollars (IMF, current year). Right: the gap in percentage points. Stock markets are distributed differently from economic output."
    override val ptOhneLand = "Without a country"
    override val ptOhneBip = "Without IMF value"
    override val ptVarTitle = "One-year swing risk"
    override fun ptVar(niveau: String) = "VaR $niveau (1 year)"
    override fun ptEs(niveau: String) = "ES $niveau (1 year)"
    override val ptVarHint = "Value at risk: in 95 of 100 simulated years the loss stays below this."
    override val ptEsHint = "Expected shortfall: average loss of the worst 5 of 100 simulated years."
    override fun ptVarNote(anzahl: Int, schwankung: String?) = "From $anzahl simulated paths. " +
        if (schwankung != null) "Return as above, volatility from the portfolio's own history ($schwankung per year)." else "Return and volatility as above."
    override val ptSparAnlage = "Investment"
    override val ptSparSeit = "Start"
    override val ptSparValue = "Value today"
    override fun ptSparMonths(n: Int, name: String, seit: String) = "after $n monthly payments into $name since $seit"
    override val ptSparIrr = "Annual return (IRR)"
    override val ptSparLump = "As a single lump sum"
    override val ptSparLumpHint = "The whole amount once at the first price."
    override fun ptSparLumpSparplan(euro: String) = "The savings plan is $euro ahead."
    override fun ptSparLumpEinmal(euro: String) = "The lump sum is $euro ahead."
    override val ptSparKeineDaten = "No prices for the chosen period."
    override fun afWaage(wert: Int) = "Net score " + (if (wert >= 0) "+$wert" else "−${-wert}")
    override val ptSparNote = "Bought on the first trading day of the month, valued at today's price. Distributions are reinvested. The IRR counts each installment from its purchase day."
    override fun afRsi(wert: String) = "RSI $wert"
    override fun afLinieKurz(tage: Int, abstand: String) = "${tage}-day line $abstand"
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
    override val ptDiversificationNote = "Regions as on the globe: stocks by country of activity, ETFs by the countries of their index. For industries an ETF counts as a whole."
    override val ptModeRebalance = "Rebalance"
    override val ptModeBuyOnly = "Buy only"
    override val ptNewMoney = "New money"
    override fun ptCash(v: String) = "Cash $v"
    override val ptEqualWeights = "Equal weights"
    override val ptCurrentWeights = "Current weights"
    override val katJeWert = "By holding"
    override val katJeKategorie = "By category"
    override val katOhne = "No category"
    override val katEine = "Category"
    override val katTitel = "Categories"
    override val katBearbeiten = "Edit categories"
    override val katNeu = "Add category"
    override val katName = "Name"
    override val katZiel = "Target in %"
    override val katSumme = "Sum of targets"
    override val katPositionen = "Holdings"
    override val ptSaveTargets = "Save targets"
    override val ptSavedTargets = "Saved targets"
    override val divTitel = "Diversify"
    override val divGleich = "Equal weights"
    override val divRisiko = "Equal risk share"
    override val divLaenderBip = "Countries by world GDP"
    override val divBranchen = "Balance industries"
    override fun divHoechstens(prozent: Int) = "At most $prozent% per holding"
    override val divLaedt = "Loading data …"
    override val divKeineDaten = "There is not enough data for this."
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
    override val ptOthers = "Others"
    override fun ptFromTo(a: String, b: String) = "$a to $b"

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
    override val riskKnockout = "If the underlying touches the knock-out barrier, the certificate ends immediately. The residual value between barrier and strike is repaid, after a gap through the strike almost nothing. The financing costs of real mini futures, which move the strike every day, are not modeled."
    override val riskFactor = "The factor applies to the daily move and resets every day. Over longer periods the result can differ markedly from the multiple of the underlying's performance, especially in volatile markets."
    override val riskWarrant = "Time value decays as maturity approaches. If the underlying is on the wrong side of the strike at maturity, the warrant expires worthless. Settlement uses the first current price from maturity on."
    override val modelledHistory = "The history shown is modeled and starts with the creation of the product."
    override val noFactorHistory = "The chart of factor products is modeled from the daily returns of the underlying (daily leverage, interest and fee), backwards from today's price."
    override val productUnavailable = "Product no longer available"
    override fun leverageValue(v: String) = "Leverage $v"
    override val derivative = "Derivative"
    override val deltaFilter = "Delta from"

    override fun error(code: String, extra: String) = when (code) {
        "INVALID_QTY" -> "Invalid quantity"; "NO_PRICE" -> "No price available"; "INVALID_FEE" -> "Invalid fee"
        "NOT_TRADABLE" -> "Product no longer tradable"; "WHOLE_PIECES" -> "Derivatives trade in whole units only"; "TOO_LARGE" -> "Amount too large"
        "NOT_ENOUGH_CASH" -> "Not enough balance"; "NOT_IN_DEPOT" -> "Position not in portfolio"; "NOT_ENOUGH_PIECES" -> "Not enough units in portfolio"
        "FEE_NOT_COVERED" -> "Balance does not cover the fee"; "INVALID_LIMIT" -> "Invalid limit/stop price"; "INVALID_TRAIL" -> "Distance must be between 0 and 50%"
        "INVALID_STOP" -> "A stop sell must lie below, a stop buy above the price"
        "MISSING_SECURITY" -> "Security missing"; "INVALID_AMOUNT" -> "Invalid amount"; "ONLY_AVAILABLE" -> "Only $extra available"
        "ORDER_CLOSED" -> "The order is no longer open"; "INVALID_DATE" -> "Invalid date"; "EXPIRED" -> "Validity expired"; "PRODUCT_GONE" -> "Product no longer tradable"
        "UNCOVERED" -> "Not covered"; "SHARES_BOUND" -> "Shares cover a sold call"; "NO_SHORT_STOCK" -> "Stocks cannot be sold short"
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
    override val orderCancelled = "Order canceled"
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
    override val rbLeer = "No review yet. The first one comes on the next trading day at the chosen time."
    override val rbJetzt = "Summarize today now"
    override val rbJetztLaeuft = "Summarizing …"
    override val rbJetztFehler = "No prices available right now."
    override fun rbWoche(von: String, bis: String) = "Week of $von to $bis"
    override val rbWocheUeberblick = "The week at a glance"
    override val rbMarkt = "Market"
    override val rbBewegungen = "Moves in your portfolio"
    override val rbNachrichten = "Important news"
    override fun rbWahrscheinlich(titel: String, quelle: String) = "Likely: \"$titel\"" + if (quelle.isNotBlank()) " ($quelle)" else ""
    override fun rbWahrscheinlichKurz(titel: String) = "likely \"$titel\""
    override fun rbMitMarkt(markt: String, aenderung: String) = "No news of its own found, moved with the market ($markt $aenderung)"
    override val rbOhneGrund = "No matching news found"
    override val rbKeinDepot = "No securities in the portfolio"
    override val rbKeineBewegung = "No security moved by more than 0.5%"
    override val rbKeineNachrichten = "No particularly important news"
    override val rbVorlaeufig = "Preliminary, made before the market close. The final review replaces it once the markets have closed."
    override val rbVorlaeufigAlt = "Preliminary, made before the market close."
    override val rbNachtraeglich = "Created afterwards from bookings and daily prices."
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
        "alle" -> "Every new headline from the sources that are switched on"
        "aus" -> ""
        else -> "Default: only the headlines marked on the news page"
    }
    override val awTitle = "Analysis"
    override val awLoading = "Calculating the analysis …"
    override val awNone = "There are no trades in stocks or funds with prices to analyze."
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
        "FONDSKOSTEN" -> "$anteil on average, about €$anzahl a year"
        "TER_GESTIEGEN" -> (if (anzahl == 1) "More expensive now" else "$anzahl more expensive now") + ", up to $anteil more a year"
        "GROESSTE" -> "$anteil of the portfolio"
        "ANZAHL" -> "$anzahl single stocks, $anteil in ETFs"
        "BRANCHE" -> "$anteil of the portfolio"
        "WAEHRUNG" -> "$anteil in a foreign currency"
        "HEBEL" -> "$anteil of the portfolio"
        "KRYPTO" -> "$anteil of the portfolio"
        "KOSTEN" -> "$anteil of the amount traded in twelve months, $anzahl orders"
        "RESERVE" -> "$anteil of total assets"
        "SPARPLAN" -> if (anzahl > 0) "$anzahl active" else "none"
        "VERLUSTE" -> "$anzahl more than 50% down"
        else -> anteil
    }
    override val terTeurerTitel = "ETF became more expensive"
    override val etfTdHinweis = "The tracking difference shows how far the annual return lagged the index."
    override val etfTdLink = "Tracking difference at extraETF"
    override fun terTeurerText(name: String, alt: String, neu: String) = "$name: ongoing charges rose from $alt to $neu a year."
    override fun dcExplain(regel: String) = when (regel) {
        "TER_GESTIEGEN" -> "The app remembers the ongoing charges of every ETF whenever it fetches them.\n\n• If they rise, this shows here for a year.\n• Fund companies may raise costs after giving notice.\n• A comparable ETF with lower costs can then be worth it, but switching costs fees and, on a gain, taxes."
        "FONDSKOSTEN" -> "The fund deducts its ongoing charges (TER) from its assets every year.\n\n• Broad index ETFs often cost 0.1 to 0.3%.\n• Over many years higher costs reduce the return noticeably."
        "GROESSTE" -> "If a single security falls sharply, the whole portfolio feels it.\n\n• A common rule of thumb is at most 5 to 10% per single security.\n• ETFs do not count here because they spread risk themselves."
        "ANZAHL" -> "With few single securities, a lot depends on each one. From about 15 to 20 securities in different sectors, or with a broad ETF, this risk drops markedly."
        "BRANCHE" -> "Securities of one sector often fall together, for example when interest rates or rules change. More than a quarter of the portfolio in one sector is a cluster."
        "WAEHRUNG" -> "For securities in dollars, pounds or francs, the exchange rate moves on top of the price. This often evens out in the long run but can run one way for years.\n\nETFs count by the currencies of the countries in their index, a world ETF in euros is largely invested in dollars. Above 95% the portfolio counts as one-sided."
        "HEBEL" -> "Leveraged products multiply gains and losses and can expire worthless. Many guides suggest at most 5 to 10% of the portfolio."
        "KRYPTO" -> "Cryptocurrencies fluctuate far more than stocks. Many guides suggest at most about 5 to 10%."
        "KOSTEN" -> "Fixed fees weigh heavily on small orders: €1 on €50 is 2%. Trading less often in larger amounts, or a savings plan, lowers the costs."
        "RESERVE" -> "Some cash allows buying more without selling. If most of it sits unused, it adds nothing to the return."
        "SPARPLAN" -> "A savings plan invests regularly without waiting for the right moment and averages the entry price."
        "VERLUSTE" -> "A security that has lost half its value has to double to get back to the purchase price. What matters is whether the reason for buying still holds."
        else -> ""
    }
    override fun dcEinzelwerte(anzahl: Int) = "$anzahl single securities above 5%"
    override val dcTeuersten = "Most expensive"
    override val dcNote = "Rules of thumb from common guides. Not investment advice."
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
    override val ctcHint = "Dates are trade dates, not filing dates. Amounts are the middle of the reported range."
    override val updNotifTitle = "New Tryfolio version"
    override fun updNotifText(fassung: String) = "Version $fassung is ready to download."
    override fun updRowTitle(fassung: String) = "Version $fassung available"
    override fun altTitel(monate: Int) = "This version is $monate months old"
    override val altText = "Newer versions are on the download page."
    override fun altZeile(monate: Int) = "Version $monate months old"

    override val guideTitle = "Quick guide"
    override fun ppfZeitraum(key: String) = when (key) { "YTD" -> "YTD"; "Y1" -> toolYearsKurz(1); "Y3" -> toolYearsKurz(3); "Y5" -> toolYearsKurz(5); else -> "All" }
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
    override val ppfNurWertpapiereHinweis = "prices without cash, costs and dividends"
    override val ppfLetzterTag = "Last day"
    override val ppfRisiko = "Risk indicators"
    override val ppfRueckgangDauer = "Longest time below a high"
    override val ppfRueckgangDauerHinweis = "until the next high or today"
    override fun ppfTage(n: Long) = if (n == 1L) "1 day" else "$n days"
    override val ppfSemivolatilitaet = "Downside volatility"
    override val ppfSemivolatilitaetHinweis = "semivolatility, only periods below the mean"
    override val ppfHoch = "All-time high"
    override val rdUnterwasser = "Drawdown"
    override val rdUnterwasserInfo = "Shows how far the value was below its previous high.\n\n• 0% means a new high.\n• The deeper the area, the larger the drop, the wider, the longer it lasted."
    override val rdLeerUnterwasser = "From 20 trading days of history there is something to see here."
    override val rdVerteilung = "Return distribution"
    override val rdVerteilungInfo = "How often each daily return occurred, in classes.\n\nThe height shows the share of days, the outer classes collect outliers.\n• VaR 95% (1 day): on 95 of 100 days the loss was smaller.\n• ES 95% (1 day): average loss on the worst 5 of 100 days.\n\nDaily returns, because they give a distribution after only 100 trading days. Monthly values would need years."
    override val rdLeerVerteilung = "From 100 trading days of history (about five months) there is something to see here."
    override fun rdTage(n: Int) = if (n == 1) "1 trading day" else "$n trading days"
    override fun rdKlasse(von: String, bis: String, n: Int, anteil: String) = "$von to $bis: ${if (n == 1) "1 day" else "$n days"} ($anteil)"
    override fun rdKlasseBis(bis: String, n: Int, anteil: String) = "Up to $bis: ${if (n == 1) "1 day" else "$n days"} ($anteil)"
    override fun rdKlasseAb(von: String, n: Int, anteil: String) = "From $von: ${if (n == 1) "1 day" else "$n days"} ($anteil)"
    override val rdRollierend = "Return and volatility over time"
    override val rdRollierendInfo = "Return over each 12 months and volatility over each 60 trading days, both calculated up to the day of the point.\n\n• Shows how much both depend on the date.\n• Volatility is scaled to one year."
    override val rdRoll12 = "Return over 12 months"
    override val rdRoll60 = "Volatility over 60 trading days"
    override val rdLeerRoll12 = "The return over 12 months is available from one year of history."
    override val rdLeerRoll60 = "The volatility over 60 trading days is available from about three months of history."
    override val ppfBilanz = "Performance calculation"
    override fun ppfWertAm(datum: String) = "Value on $datum"
    override val ppfEinlagen = "Deposits"
    override val ppfEntnahmen = "Withdrawals"
    override val ppfKursgewinne = "Capital gains"
    override fun ppfRealisiert(betrag: String) = "of which realized $betrag"
    override val ppfDividenden = "Dividends (before tax)"
    override val ppfZinsen = "Interest (before tax)"
    override val ppfMonate = "Return per month and year"
    override val wbTitel = "By security"
    override val wbInfo = "• TTWROR: time-weighted return, the price return without the effect of deposits and withdrawals\n• IRR: internal rate of return, the return on the money invested"
    override val wbUnrealisiert = "Unrealized"
    override val wbOhneWertpapier = "No security"
    override val wbIzf = "IRR"
    override val wbTtwror = "TTWROR"
    override val ppfHinweis = "Calculated for the whole portfolio with securities and cash, after fees and taxes. Money from outside means deposits, withdrawals, bets and positions taken over without money (at cost).\n\n• Time-weighted rate of return (TTWROR): measures the investment regardless of when you paid in or out, suited for comparison with funds and indices\n• Internal rate of return (IRR): your personal return, takes into account when how much money was in the portfolio\n• Capital gains: what remains from the end value, start value, deposits, withdrawals, income and costs\n\nVolatility and downside volatility are shown per year. Below one year the IRR applies to the period and is not annualized.\n\nYou export bookings for Portfolio Performance under Profile › Advanced."
    override val guideAllesTitel = "Everything the app can do"
    override val guideAllesText = ""
    override fun guideFunktionen(widgets: Boolean, bildschutz: Boolean) = listOfNotNull(
        "Practice portfolio with real prices" to "Trade stocks, ETFs, crypto and derivatives with virtual money, with limit or stop, fees and tax included. What came due while the app was closed is made up on the next start at the prices of that time.",
        "Savings plans and transfers" to "Savings plans buy at fixed intervals and can reinvest dividends, found in the “Orders” tile. Transfers pay virtual money in or out.",
        "Finding securities" to "Search with history, top movers, filters by sector, country and region, and separate lists for indices, commodities, currencies and crypto.",
        "Security page" to "Chart, valuation with a fair value to calculate yourself, key figures, opportunities and risks, events, news and financials. For derivatives a bar shows long and short.",
        "Indicators" to "Below the chart you pick indicators such as moving averages, Bollinger bands, RSI or MACD, one at a time or together. Underneath you see what they say.",
        "Tools and options per security" to "Below the chart, “Review” opens: lump sum and savings plan against your portfolio, indices and other securities. Also position size with a stop suggestion, sale with tax, strategy test and “How far can it fall”, for US stocks also options, short selling and the move around earnings.",
        "Politics and agencies" to "For US stocks in the “Background” section: lobbying spending, members of Congress who traded, campaign contributions, large holders, executive pay, takeovers and warning signs from agency filings. Depending on the industry, clinical trials and recalls are added.",
        "Markets and heatmap" to "The “Markets” tile has the tabs Heatmap, Globe, Trends, Movers, Sentiment, Outlook and Situation. The heatmap shows the stock market or your holdings as tiles by weight and daily change, Movers the biggest price moves plus IPOs, earnings and dividend dates of the next trading day.",
        "Trends and mood" to "Sectors and themes with price change, P/E, dividend yield and breakthroughs, plus the most discussed stocks on Reddit. Sentiment shows Fear & Greed, the AAII survey and put/call, the Outlook market expectations, Polymarket bets and early warning signs.",
        "Globe and countries" to "The globe colors every country by overall picture, stock market, economy and other layers. The country sheet shows key figures, companies and risks, “My portfolio” the countries of your holdings.",
        "Situation and dependencies" to "The Situation tab sums up the world in six topics. “Dependencies and risks” on the security page shows what a company lives on and what it depends on, “Impact on my holdings” links an event to your holdings.",
        "Breakthroughs" to "In the Trends tab per topic New, Expected, In development and Review.",
        "Analytics and portfolio tools" to "Portfolio Performance, portfolio history, portfolio check, diversification, crash test, wealth building, withdrawal plan, selling options, unusual options, the options calculator and more tools, grouped into My portfolio, Plan and Find stocks. Statistics on buys, sells and income are under Cash.",
        "Stock finder and radar" to "The “Stock finder” tile has the tabs List, Radar, Industries and Suggested portfolio. The list ranks stocks by valuation, quality, analysts, price trend and more, the Radar collects early hints such as rising mentions and insider buys.",
        "Copytrading" to "What US representatives, senators, the President and big investors buy and sell, with portfolio history, positions and strategies to replicate. A bell reports news.",
        "Leaderboard and Congress buys" to "Which representatives and senators beat the market with their purchases, plus their net worth and a strategy built from all reported purchases. Both are in Copytrading under Politics.",
        "Prediction markets" to "Bet on events at Polymarket and Kalshi with practice money and follow what the bets have returned.",
        "News and bells" to "News by provider, Google News, posts by Trump and shareholder letters. The bell on a security page turns on notifications per security, such as price alerts, news and insiders.",
        "Financial knowledge" to "A reference on markets, taxes and strategies with lessons, test questions and calculators, as a tile on the start page.",
        "Daily and weekly review" to "Every trading day at the time you choose a verdict: your portfolio compared with the MSCI World, market, sectors and important news. On Fridays the whole week.",
        "Request access keys" to "With free keys of your own the app becomes more complete, for example for Reddit, campaign contributions, patents, power prices and more US prices. Enter them in the profile under “Access keys”, which also says where to get them.",
        "Explainer" to "With your own key from Profile › Access keys, “Explain” appears at key figures, financials and quarterly figures. The page figures go to your provider, without portfolio and without advice.",
        "Security" to "App lock with PIN or password, data encrypted with AES-256, access keys encrypted in the device's secure storage" + (if (bildschutz) ", screenshots allowed or blocked." else "."),
        if (widgets) "Widgets" to "Portfolio, watchlist, single security, top and flop, dividends, markets, heatmap, sentiment, news and shortcuts of your choice on the home screen. A long press on a heading in the app adds the matching widget." else null,
    )
    override val guideMeldungenTitel = "Load the trades of members of Congress"
    override val guideMeldungenText = "Copytrading, the leaderboard and the pages of US stocks show what members of Congress buy and sell. Filings since 2024 come with the app, newer ones load in the background, even if you close the guide."
    override val guideMeldungenLaden = "Load all filings now"
    override fun guideMeldungenLaedt(gelesen: Int, gesamt: Int) = if (gesamt == 0) "Loading the index …" else "$gelesen of $gesamt filings read"
    override fun guideMeldungenFertig(gesamt: Int) = if (gesamt == 0) "All filings are already on the device." else "Done: $gesamt new filings read."
    override val guideSkip = "Skip"
    override val guidePages = listOf(
        "Portfolio and cash" to "The start page shows the value of your securities and how it developed. Swipe left for cash with balance, statistics and activity, once more for your profile. Below the positions are the tiles, such as Orders, Markets and Analytics.",
        "Finding securities" to "Use “Trade” at the bottom of the start page to find stocks, ETFs, derivatives and crypto. The selection opens the security page, swiping down closes it.",
        "Buying and savings plans" to "“Buy” creates an order, as an amount or a number of shares, right away or with a limit or stop. A savings plan buys at fixed intervals. Both are in the “Orders” tile, the tabs at the top switch between orders and savings plans. If the app was closed, the next start makes up for both, at the price and time of that moment.",
        "Profile" to "Behind your initial at the top right are documents, taxes, notifications, access keys and all settings. You can open this guide there again at any time.",
    )

    override val guidePagesDesktop get() = guidePages.toMutableList().apply {
        this[0] = "Portfolio and cash" to "The start page shows the value of your securities and how it developed. Click “Cash” at the top or drag the page left with the mouse button held down for balance, statistics and activity, once more for your profile. Below the positions are the tiles, such as Orders, Markets and Analytics."
        this[1] = "Finding securities" to "Use “Trade” at the bottom of the start page to find stocks, ETFs, derivatives and crypto. The selection opens the security page, Escape or the arrow at the top left closes it."
    }

    // Discover
    override val bestPerformers = "Top gainers"
    override val worstPerformers = "Top losers"
    override val ipos = "IPOs"
    override val upcoming = "Upcoming"
    override val completed = "Completed"
    override fun ipoCardTitle(name: String) = "$name IPO"
    override fun ipoListed(date: String) = "Listed $date"
    override fun ipoSinceIssue(pct: String) = "since IPO $pct"
    override fun ipoAsOf(date: String) = "As of $date"
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
        "FAHRZEUGE" -> "Vehicles"; "LUFTFAHRT" -> "Aerospace & defense"; "FORST" -> "Forestry"
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
    override fun industryShort(key: String) = when (key) {
        "ALKOHOL_TABAK" -> "Alcohol"; "BANKEN" -> "Banks"; "BAU" -> "Building"; "BERGBAU" -> "Mining"; "HOLDINGS" -> "Holdings"
        "BIOTECH" -> "Biotech"; "CHEMIE" -> "Chemicals"; "COMPUTER" -> "Computers"; "DIENSTLEISTUNGEN" -> "Services"
        "ELEKTRO" -> "Electrical"; "HALBLEITER" -> "Semiconductors"; "VERSORGER" -> "Utilities"; "FAHRZEUGE" -> "Vehicles"
        "LUFTFAHRT" -> "Aerospace"; "FORST" -> "Forestry"; "GASTGEWERBE" -> "Hospitality"; "GESUNDHEIT" -> "Healthcare"
        "HANDEL" -> "Trading"; "IMMOBILIEN" -> "Real estate"; "SOFTWARE" -> "Software"; "EINZELHANDEL" -> "Retail"
        "KONSUM" -> "Consumer"; "MASCHINEN" -> "Machinery"; "NAHRUNG" -> "Food"; "PHARMA" -> "Pharma"; "MEDIEN" -> "Media"
        "ROHSTOFFE" -> "Commodities"; "TELEKOM" -> "Telecom"; "TEXTIL" -> "Textiles"; "UMWELT" -> "Environment"
        "VERKEHR" -> "Transport"; "VERSICHERUNG" -> "Insurance"; "OEL_GAS" -> "Oil & gas"; "SEKTORUEBERGREIFEND" -> "Mixed"
        "LARGE_CAPS" -> "Large caps"; "MID_CAPS" -> "Mid caps"; "SMALL_CAPS" -> "Small caps"
        "UNTERNEHMENSANLEIHEN" -> "Corporates"; "STAATSANLEIHEN" -> "Sovereigns"; "GELDMARKT" -> "Cash"
        else -> industryName(key)
    }
    override fun regionShort(key: String) = when (key) {
        "ASIEN_PAZIFIK" -> "Asia"; "NORDAMERIKA" -> "America"; "SCHWELLENLAENDER" -> "Emerging"
        else -> regionName(key)
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
        de.troetenrepublik.app.market.Rss.Anbieter.DEPOT -> "Portfolio"
        de.troetenrepublik.app.market.Rss.Anbieter.TRUMP -> "Trump"
        de.troetenrepublik.app.market.Rss.Anbieter.BRIEFE -> "Letters and blogs"
        de.troetenrepublik.app.market.Rss.Anbieter.TAGESSCHAU -> "tagesschau"
        de.troetenrepublik.app.market.Rss.Anbieter.YAHOO -> "Yahoo Finance"
        de.troetenrepublik.app.market.Rss.Anbieter.GOOGLE -> "Google News"
        de.troetenrepublik.app.market.Rss.Anbieter.PRESSE -> "Press"
        de.troetenrepublik.app.market.Rss.Anbieter.CNBC -> "CNBC"
        de.troetenrepublik.app.market.Rss.Anbieter.REUTERS -> "Reuters"
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
    override val newsDepotLeer = "No holdings in the portfolio"
    override val newsDepotStufen = "New ratings, 7 days"
    override val newsEmptySearch = "No headline matches the search."
    override val newsYesterday = "Yesterday"
    override val newsSearchHint = "Search headlines"
    override val newsSearch = "Search"
    override val newsSources = "Sources and notifications"
    override val newsSourcesHint = "Built in are sources whose terms allow reading in this app with attribution.\n\n• Own feeds are loaded only on this phone.\n• What a site allows for its feed is set out in its terms of use."
    override val newsOwnFeeds = "Own feeds"
    override val newsFeedUrl = "Feed address (RSS or Atom)"
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
    override val copySenateNote = "Amounts are ranges in US dollars. Paper filings are scanned and are not included in the analyses."
    override val copySenatorOf = "U.S. Senate"
    override val copyGovIntro = "Trades of the US President from his reports to the Office of Government Ethics (Form 278-T), with the amount as a range. Rows without a reliable date, type or amount are left out."
    override fun copyGovNote(stand: String) = (if (stand.isBlank()) "" else "As of $stand. ") + "Amounts are ranges in US dollars."
    override val copyPresident = "President of the United States"
    override val copyVicePresident = "Vice President of the United States"
    override fun copyAmtBis(amt: String, bis: String) = "$amt until $bis"
    override fun copyVanceNote(stichtag: String) = "Holdings according to the annual report (OGE Form 278e) as of $stichtag, with values as ranges. Vance reports hardly any individual trades because funds and ETFs are exempt from the reporting duty for trades. Weights are based on the middle of the ranges."
    override val copyFilingsAll = "Filings"
    override val copyTypeMuniBond = "Municipal bond"
    override fun copyTradesCount(n: Int) = if (n == 1) "1 trade" else "${"%,d".format(java.util.Locale.US, n)} trades"
    override fun copyHoldingsAsOf(date: String) = "Holdings as of $date"
    override val copyWhoTitle = "Who trades it"
    override val copyWhoNone = "No trades by well-known people in the last twelve months"
    override val copyTendBuy = "Mostly buying"
    override val copyTendSell = "Mostly selling"
    override val copyTendMixed = "Balanced"
    override fun copyWhoHouseOpen(offen: Int, gesamt: Int) = "The app has not read $offen of $gesamt House filings from the last twelve months yet. Their trades are still missing here."
    override val copyWhoHouseLoad = "Read all filings (once, then kept on the device)"
    override fun copyWhoHouseReading(fertig: Int, gesamt: Int) = "Reading filings: $fertig of $gesamt"
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
    override val copyBellsAdd = "Find people in Copytrading"
    override val copyBellHouse = "House · checks the Clerk's index"
    override val copyBellSenate = "Senate · with every new version of the app"
    override val copyBellGovernment = "Administration · checks whitehouse.gov"
    override val copyBellInvestor = "Investor · checks the SEC for new 13F filings"
    override val imTitel = "Import register data"
    override val imUntertitel = "Insider and short-selling lists from files"
    override fun imInsider(n: Int, land: String) = "$n insider transactions (${landName(land)}) imported"
    override fun imLeer(n: Int, land: String) = "Short positions for $n stocks (${landName(land)}) imported"
    override val imPdf = "The app found no readable form under Art. 19 MAR in this PDF. Please have SEDI reports converted first."
    override val imUnbekannt = "The app does not recognize this file. It reads the BSE list (CSV), the Consob table (XLSX), the Bundesanzeiger short-selling list (CSV), notifications under Art. 19 MAR (PDF) and register files of the app (JSON)."
    private fun landName(l: String) = when (l) {
        "IN" -> "India"; "CA" -> "Canada"; "IT" -> "Italy"; "DE" -> "Germany"; "AU" -> "Australia"; "HK" -> "Hong Kong"
        "DK" -> "Denmark"; "FI" -> "Finland"; "IS" -> "Iceland"; "EE" -> "Estonia"; "LV" -> "Latvia"; "LT" -> "Lithuania"; "CZ" -> "Czechia"; "LU" -> "Luxembourg"
        "CY" -> "Cyprus"; "HU" -> "Hungary"; "PL" -> "Poland"; "LI" -> "Liechtenstein"; "MT" -> "Malta"; "HR" -> "Croatia"; "AT" -> "Austria"; "BE" -> "Belgium"
        "ES" -> "Spain"; "PT" -> "Portugal"; "IE" -> "Ireland"; "GR" -> "Greece"; "SI" -> "Slovenia"; "SK" -> "Slovakia"; "RO" -> "Romania"; "BG" -> "Bulgaria"; "NO" -> "Norway"
        else -> l
    }
    override val lwTitel = "Lobbying Europe"
    override val lwHinweis = "How much the company spends a year, according to the lobby registers, to influence laws and authorities. The registers give ranges."
    override fun lwRegister(l: String) = when (l) { "EU" -> "EU Transparency Register"; "DE" -> "Bundestag lobby register"; "FR" -> "French lobby register"; else -> l }
    override fun lwSpanne(von: String, bis: String) = "$von to $bis"
    override fun lwAb(von: String) = "from $von"
    override fun lwJahr(von: String, bis: String) = "Financial year $von to $bis"
    override fun lwFte(n: String) = "$n full-time positions for lobbying"
    override fun lwTaetigkeiten(n: Int) = if (n == 1) "1 reported activity" else "$n reported activities"
    override fun lwThemen(t: String) = "Topics: $t"
    override val lwEintrag = "Open the register entry"
    override fun copyNotifOge(n: Int) = (if (n == 1) "One new report" else "$n new reports") + " at the US Office of Government Ethics. The trades arrive, already read, with the next version of the app."
    override val importPortfolioJson = "Import portfolio (backup or broker export)"
    override val exportPortfolioJson = "Export portfolio (JSON)"
    override val importChoice = "Merge adds the file's cash, positions, transactions, savings plans and watchlist to the current portfolio. Replace swaps the current portfolio for the file."
    override val importMerge = "Merge"
    override val importReplace = "Replace"
    override val depotMerged = "Portfolios merged"
    override fun copyPhotoCredit(autor: String, lizenz: String) = "Photo: $autor, $lizenz, via Wikimedia Commons"
    override val copyPhotosCommons = "Author and license of the photos are shown on each person's page. Without a free photo, the initials are shown."
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
    override val copyCongressNote = "Amounts are estimates from the ranges."
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
    override fun copyInvestorLine(date: String, n: String, value: String) = "As of $date · $n positions · $value"
    override val copyInvestorsNote = "Values in US dollars at the reporting date. Options are shown at the value of the underlying shares. Cash, short positions and shares outside the US are not part of these filings."
    override val copy13fSuche = "Search another fund"
    override val copy13fEigene = "Searched"
    override val copy13fNichts = "No results"
    override val copy13fFehler = "SEC not responding"
    override val copy13fOhneKontakt = "SEC contact missing under Access keys"
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
    override val copyStrategiesNote = "Past performance without costs and taxes, rebalanced to the weights once a year. Payouts of distributing ETFs are not included."
    override val copyAllocation = "Allocation"
    override val copyHistory = "Performance"
    override val copyNoHistory = "No prices are available for these ETFs right now."
    override fun copyRange(from: String, to: String) = "$from to $to"
    override val copyMaxDrawdown = "Largest drawdown"
    override val copyYears = "By calendar year"
    override val copyAsPlans = "Set up as savings plans"
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
    override fun copyPlansMin(betrag: String) = "At least $betrag a month, so that each savings plan gets at least €1."
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
    override val regLandTitel = "Your country"
    override val regLandText = "Sets currency and trading hours, changeable in the profile."
    override val regVorschlag = "Suggested from your device's time zone"
    override val regAndere = "Other countries"
    override val regWaehrungTitel = "Your currency"
    override val regWaehrungText = "The app shows your portfolio, prices and cash in it. Orders, taxes and documents stay in euros."
    override fun waehrungName(code: String) = when (code) {
        "EUR" -> "Euro"; "USD" -> "US dollar"; "CHF" -> "Swiss franc"; "GBP" -> "British pound"; "SEK" -> "Swedish krona"
        "DKK" -> "Danish krone"; "NOK" -> "Norwegian krone"; "PLN" -> "Polish złoty"; "CZK" -> "Czech koruna"; "HUF" -> "Hungarian forint"
        "CAD" -> "Canadian dollar"; "MXN" -> "Mexican peso"; "BRL" -> "Brazilian real"; "JPY" -> "Japanese yen"; "CNY" -> "Chinese yuan"
        "HKD" -> "Hong Kong dollar"; "TWD" -> "Taiwan dollar"; "KRW" -> "South Korean won"; "INR" -> "Indian rupee"; "SGD" -> "Singapore dollar"
        "ILS" -> "Israeli shekel"; "ZAR" -> "South African rand"; "AUD" -> "Australian dollar"; "NZD" -> "New Zealand dollar"
        else -> code
    }
    override val regZeitenTitel = "Trading hours"
    override val regZeitenText = "When orders are executed, in your local time. Crypto always trades around the clock. Holidays apply to LS Exchange and the US exchanges, shortened trading days to the US exchanges."
    override fun regZeitenLand(land: String) = "Like the stock exchange in $land"
    override fun regZeitenLandHint(boerse: String, zeiten: String, deutschland: Boolean) =
        if (deutschland) "$boerse: stocks and ETFs 7:30 to 23:00, derivatives 8:00 to 22:00" else "$boerse, weekdays $zeiten local time"
    override val regZeitenEigene = "Custom hours"
    override val regZeitenImmer = "Around the clock"
    override fun regVonBis(von: String, bis: String) = "Weekdays $von to $bis"
    override val regionTitel = "Country, currency and trading hours"
    override val regAnzeige = "Shown now"
    override val regAnzeigeHint = "Bookings are in euros."
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
    override val trBranchenHinweis = "Measured by US sector ETFs, prices without dividends. They only roughly represent the sector worldwide."
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
    override val sbAaii = "AAII retail investors (US)"
    override fun sbAaiiMehr(bullen: String, neutral: String, baeren: String, datum: String) =
        "Survey of $datum: $bullen% expect rising prices over the next six months, $neutral% no big change, $baeren% falling prices. The barometer shows the share of bulls among bulls and bears. Long-term average about 37% bulls and 31% bears."
    override val sbPutCall = "Put/call ratio"
    override fun sbPutCallMehr(wert: String) = "On the last trading day, $wert puts traded for every call on US stocks. Many puts point to fear, few to greed. The usual range is 0.5 to 0.8, matching readings of 75 to 25. Below that counts as carefree, above as cautious."
    override fun wqPcZeile(gesamt: String?, index: String?, tag: String) =
        listOfNotNull(gesamt?.let { "All options $it" }, index?.let { "Indices $it" }, "Trading day $tag").joinToString(" · ")
    override val sbKrypto = "Fear & Greed crypto"
    override val sbWsb = "Reddit r/wallstreetbets"
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
        "• Volatility: how calm the VIX is compared with the past year, from Yahoo Finance"
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
        "vix_struktur" -> "VIX against VIX3M"
        "trend_sp500" -> "Trend S&P 500"
        "trend_dax" -> "Trend DAX"
        "stress" -> "Financial stress"
        "hochzins" -> "US high-yield spread"
        "vix_abstand" -> MarktzeichenTexte.text(false, "fw.name", emptyList())
        else -> schluessel
    }
    override fun fwLage(schluessel: String, ampel: Int) = when (schluessel) {
        "zinskurve" -> listOf("Normal, longer maturities pay more", "Inverted, short maturities pay more", "Normal again after inversion, often shortly before recessions")[ampel]
        "sahm" -> listOf("Labor market stable", "Unemployment rising noticeably", "Threshold of 0.5 reached, typical for recessions")[ampel]
        "vix" -> listOf("Calm market", "Elevated nervousness", "High fear in the market")[ampel]
        "vix_struktur" -> listOf("Normal, more uncertainty ahead than today", "Almost flat, short-term worry growing", "Inverted, acute market stress")[ampel]
        "trend_sp500", "trend_dax" -> listOf("Well above the 200-day line", "Just above the 200-day line", "Below the 200-day line")[ampel]
        "stress" -> listOf("Less stress than on average", "More stress than on average", "Clearly elevated stress")[ampel]
        "hochzins" -> listOf("Low spread, lenders relaxed", "Spread elevated or rising", "Spread high or risen quickly")[ampel]
        "vix_abstand" -> MarktzeichenTexte.text(false, "fw.lage.$ampel", emptyList())
        else -> ""
    }
    override fun fwErklaerung(schluessel: String) = when (schluessel) {
        "zinskurve" -> "Yield of 10-year US Treasuries minus the 3-month yield.\n\n• It was below zero for a while before every US recession since 1968, with a lead of about 6 to 24 months.\n• The recession often starts only when the curve rises again.\n• Weakness: from 2022 to 2024 it was inverted for a long time without a recession so far."
        "sahm" -> "Three-month average of the US unemployment rate minus its low of the previous twelve months.\n\n• At 0.5 percentage points the rule has flagged every US recession early since 1970.\n• Weakness: in 2024 the threshold was briefly reached mainly because more people were looking for work."
        "vix" -> "Expected 30-day fluctuation of the S&P 500, derived from option prices.\n\n• Readings above 30 occur almost only in downturns.\n• The VIX warns during a slump rather than before it.\n• Very low readings show complacency."
        "vix_struktur" -> "VIX divided by the three-month VIX3M.\n\n• Usually uncertainty over three months is higher than over 30 days and the value is below 1.\n• Above 1, investors expect more fluctuation now than later. This happened in almost every major slump."
        "stress" -> "Weekly index of the St. Louis Fed built from 18 market series, among them interest rates and six yield spreads between risky and safe bonds.\n\n• Zero is the average since 1993, one is a standard deviation above it.\n• Rising spreads show that lenders demand more for risk, which often happened before and during sell-offs.\n• Weakness: it rises with the stress and gives little warning ahead of it."
        "hochzins" -> "Extra yield that US companies with weak credit (below BBB) pay over Treasuries.\n\n• When it rises quickly, lenders pull back from risky loans. This preceded or accompanied the downturns of 2000, 2007 and 2020.\n• A very low reading shows complacency.\n• Red from 6 points or 2 points above the low of the last twelve months, yellow from 4.5 or 1 point above it.\n• FRED shows only the last three years."
        "trend_sp500", "trend_dax" -> "Distance of the index from its average of the last 200 trading days.\n\n• Nearly all major bear markets began with a fall below this line.\n• Weakness: many false signals in sideways markets."
        "vix_abstand" -> MarktzeichenTexte.text(false, "fw.erklaerung", emptyList())
        else -> ""
    }
    override val b13107Titel = "US disclosure reports"
    override val b13107Text = "Under 5 U.S.C. § 13107, financial reports of US representatives, senators and members of the administration may not be used for any commercial purpose, to determine anyone's credit rating, or to solicit money. The app loads them from the House of Representatives, the Senate, the Office of Government Ethics and the White House.\n\nBy tapping “Confirm” you declare that you use them privately only. The app will not ask again."
    override val b13107Ok = "Confirm"
    override val b13107Zurueck = "Back"
    override fun blsHinweis(datum: String) = "BLS data retrieved on $datum. BLS.gov cannot vouch for the data or analyses derived from these data after the data have been retrieved from BLS.gov."
    override val fwPunkte = "pts"
    override val fwHinweis = "None of these measures predicts a crash reliably. They are the ones with the best track record over decades.\n\n" +
        "• The yield curve and the Sahm rule warn of recessions, which often come with falling prices\n" +
        "• VIX, VIX against VIX3M and the 200-day line show whether the market is already under stress\n\n" +
        "Several signals at red at once deserve more attention than one alone."
    override val wrTitel = "Warning signs in hindsight"
    override val wrUntertitel = "Record per sign before past crashes"
    override val wrInfo = "The app computes every warning sign for every trading day in the past with the same thresholds as today, using only values that had already been published on that day.\n\nA crash is a drop of at least 20% from the high, a decline starts at 10%.\n\n" +
        "• Hit: the sign was on warning in the 12 months up to the high\n" +
        "• Warning without a crash: warning phase with no crash in the 12 months after it. Gaps under 6 months belong to the same phase\n" +
        "• Follow the warning: money market at the 3-month rate during a warning, otherwise the index, one day delay, 0.1% per switch\n" +
        "• Combined view: how many signs warn at the same time\n\n" +
        "Yield curve, labor market, jobless claims, VIX, stress, credit spread, margin debt, valuation and oil are US series and apply to all three indices. Trend and leading indicator belong to the index in question.\n\nSigns with a shorter series count only where they have data.\n\n" +
        "Labor market, jobless claims, leading indicator, stress, credit spread, margin debt, market cap and earnings-based signs were revised later. With today's values they look better than they were then.\n\nPrice index without dividends, except for the S&P 500 (with dividends from Shiller) and the DAX (total return index)."
    override val wrFest = "The past. No sign predicts a crash reliably, and a few events prove little."
    override fun wrIndex(id: String) = when (id) { "sp500" -> "S&P 500"; "dax" -> "DAX"; "msciworld" -> "MSCI World"; else -> id }
    override fun wrZeichen(id: String) = when (id) {
        "zinskurve" -> "Yield curve 10 y minus 3 m"
        "zinskurve_2j" -> "Yield curve 10 y minus 2 y"
        "sahm" -> "Sahm rule"
        "vix" -> "Volatility index VIX"
        "vix_struktur" -> "VIX against VIX3M"
        "trend" -> "Trend, 200-day line"
        "stress" -> "Financial stress"
        "cape" -> "Shiller P/E (CAPE)"
        "kgv" -> "S&P 500 P/E"
        "praemie" -> "Yield gap to bonds"
        "hitze" -> "Stretch above the 200-day line"
        "cli" -> "OECD leading indicator"
        "oel" -> "Oil price shock"
        "stimmung_vix" -> "Sentiment, VIX rank"
        "stimmung_sicher" -> "Sentiment, safe havens"
        "zusammen_2" -> "Combined, 2 or more"
        "zusammen_3" -> "Combined, 3 or more"
        "zusammen_4" -> "Combined, 4 or more"
        "hochzins" -> "High-yield spread"
        "stlfsi4" -> "Financial stress STLFSI4"
        "margin" -> "Margin debt"
        "buffett" -> "Buffett indicator"
        "erstantraege" -> "Jobless claims"
        "breite" -> "Market breadth"
        "ipo" -> "IPO fever"
        "konzentration" -> "Concentration"
        "erwaehnung" -> "Mentions"
        "gewinn" -> "Earnings growth"
        "aaii" -> "AAII survey"
        "cnn" -> "CNN Fear & Greed"
        "putcall" -> "Put/call"
        "wetten" -> "Betting markets"
        else -> id
    }
    override val wrZeitleiste = "Crashes of 20% or more"
    override val wrRueckgaenge = "Declines of 10 to 20%"
    override val wrLegende = "Filled: warning. Outline: no warning. Dot: no data."
    override fun wrEreignis(hoch: String, tief: String, tiefe: String, monate: Int) = "High $hoch, low $tief, $tiefe in $monate months"
    override val wrKetteTitel = "What carried it then"
    override val wrKeineKette = "No documented chain on file."
    override val wrTabelle = "Record per sign"
    override fun wrZeile(treffer: Int, von: Int, fehl: Int, vorlauf: Int?) =
        "$treffer of $von caught\n$fehl warnings without a crash" + (vorlauf?.let { "\navg $it mo. lead" } ?: "")
    override fun wrHeute(stand: Int) = when (stand) { 2 -> "Warning"; 1 -> "No warning"; else -> "No data" }
    override val wrKurve = "Following the warning against holding"
    override val wrFolgen = "Follow warning"
    override val wrHalten = "Hold"
    override val wrBefolgen = "Follow"
    override val wrWechsel = "Switches"
    override fun wrKurveWerte(rendite: String, rueck: String, wechsel: Int) = "$rendite per year · largest drawdown $rueck · $wechsel switches"
    override val wrKosten = "With 0.1% per switch, without taxes and fees. Both lines start at 100."
    override fun wrSatz(treffer: Int, von: Int, fehl: Int, phasen: Int, spanneVon: Int, spanneBis: Int, zeit: Int) = when {
        von == 0 -> "Too few crashes with data."
        else -> "On warning before $treffer of $von crashes (range $spanneVon to $spanneBis%). $fehl of $phasen warning phases ended without a crash. The sign was on warning $zeit% of the time."
    }
    override val wrFehlendTitel = "No look-back"
    override fun wrFehlend(ids: String) = "No free series over enough years: $ids."
    override fun wrKurzzeile(treffer: Int, von: Int, fehl: Int) = "Look-back: flagged $treffer of $von crashes, $fehl warnings without a crash"
    override fun wrHinweisZeichen(id: String, ersatz: Boolean, revidiert: Boolean, eigeneRegel: Boolean) = listOfNotNull(
        when (id) {
            "hochzins" -> "Substitute series from the Fed: Gilchrist-Zakrajšek credit spread since 1973, times 2.5 to match the range of the high-yield spread"
            "buffett" -> "Wilshire 5000 from 1989 as in the app. Before that, market cap of US companies from the Fed's Financial Accounts, converted to the Wilshire 5000 and carried forward with the S&P 500 between quarters"
            else -> "Substitute series from the Chicago Fed (NFCI), thresholds of the app carried over"
        }.takeIf { ersatz },
        "Own rule, the app does not show this sign".takeIf { eigeneRegel },
        "Later revisions of the series make the sign look better in hindsight than it was".takeIf { revidiert },
    ).joinToString(". ")
    override fun wrStand(stand: String, bis: String) = "As of $stand, data until $bis"
    override val wrBeleg = "Source"
    override fun redditMeldungTitel(name: String) = "$name is being discussed a lot on Reddit"
    override fun redditMeldungText(jetzt: Int, vorher: Int) = "$jetzt mentions in German stock subreddits in 24 hours, $vorher before."
    override val trRedditDeHinweis = "German subs: r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien and r/Finanzen through your own Reddit access (Profile › Access keys). The app reads the posts of the last 24 hours in each sub and counts which securities from the catalog, your portfolio and your watchlist they mention. A security counts once per post. The change against the 24 hours before appears once the app has stored a reading from yesterday."
    override fun trRedditDeAbdeckung(stunden: Int) = "Only the last $stunden hours are counted, the interface does not reach further back with so many posts."
    override fun trRedditDeFehlt(subs: String) = "Not reached: $subs. The counts are incomplete."
    override val trRedditBeitraege = "Reddit posts"
    override fun trRedditAnzahl(n: Int) = if (n == 1) "1 post in the last month" else "$n posts in the last month"
    override val trRedditKeineBeitraege = "No posts in the last month in r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien, r/Finanzen, r/wallstreetbets, r/stocks and r/investing."
    override fun trRedditStimmung(steigend: Int, fallend: Int, offen: Int) = "Mood by simple word rules: $steigend rather rising, $fallend rather falling, $offen without a clear direction."
    override fun trRedditPunkte(punkte: Int, kommentare: Int) = "$punkte points, $kommentare comments"
    override val trRedditKeine = "Reddit data is not reachable right now. Try again later."
    override val trLaedt = "Loading …"
    override val trMehr = "Show more"
    override fun trRang(rang: Int, ticker: String) = "Rank $rang · $ticker"
    override fun trErwaehnungen(n: Int) = if (n == 1) "1 mention" else "${"%,d".format(n)} mentions"
    override val trYahoo = "Trending on Yahoo Finance"
    override val trWiki = "Interest on Wikipedia"
    override fun trWikiProTag(n: String) = "avg. $n views a day"
    override fun trRedditZeit(tage: Int) = when (tage) { 1 -> "24 hrs"; 7 -> "Week"; 30 -> "Month"; else -> "Year" }
    override fun trSpitze(datum: String) = "peak on $datum"
    override fun trSpitzeWoche(datum: String) = "peak in the week of $datum"
    override fun trVerlaufSeit(datum: String?) = if (datum == null)
        "No history yet: ApeWisdom only reports the last 24 hours, so the app records the mentions every day from now on, in the background too. Week, month and year fill up over time."
    else "History since $datum: ApeWisdom only reports the last 24 hours, so the app records the mentions every day, in the background too. Week, month and year fill up over time."
    override val trErwaehnungenKurz = "mentions"
    override val trWertHeuteNicht = "Not among the 100 most discussed today"
    override val trWertYahooText = "Among the most viewed securities on Yahoo Finance in your region or in the US."
    override val orderGeaendert = "Order changed"
    override val orderAendern = "Edit"
    override val orderAendernSpeichern = "Save changes"
    override fun trImportTitel(quelle: String) = "Import from $quelle?"
    override val trImportLaedt = "Reading transactions and matching them to prices …"
    override fun trImportZeitraum(von: String, bis: String, zeilen: Int) = "Export from $von to $bis, $zeilen rows"
    override fun trImportOhneKurs(liste: String) = "No price available, not added to the portfolio (so their value is missing): $liste"
    override fun trImportOhneKauf(liste: String) = "Sold without the purchase being in the file (the export probably starts later), counted as bought at the sale price: $liste"
    override fun trImportUngelesen(n: Int, beispiele: String) = "Not read ($n rows, unknown type or missing values), not in the portfolio: $beispiele"
    override fun trImportWechselkurs(n: Int) = "$n rows in a foreign currency without an exchange rate, the amounts are taken over unchanged as euro."
    override fun trImportBekannt(bekannt: Int, gesamt: Int) = "$bekannt of $gesamt rows are already in the portfolio. Merging adds only the new ones."
    override fun trImportUeberGrenze(n: Int, max: Int) = "With this file the portfolio would hold more transactions than the app keeps (at most $max). The oldest $n would be dropped with the next transaction."
    override fun trImportUngeprueft(quelle: String) = "The layout of this file is not checked against a real export from $quelle. Check holdings and cash after the import."
    override fun trImportGuthabenNegativ(betrag: String, degiro: Boolean) =
        (if (degiro) "The account statement with the deposits is missing. " else "The file lacks deposits. ") + "Cash would be $betrag, so the import is blocked."
    override val taxImportOhneSteuer = "Imported sales without tax details in the file: gains and losses are in the loss pots, withheld taxes are not included."
    override val trImportHinweis = "Real Trade Republic derivatives (knock-outs, warrants, factor certificates) appear with purchases, sales and redemptions in transactions and portfolio history, there are no prices for them. Create the export in the Trade Republic app under Profile > Statements > Transaction export."
    override val brokerImportHinweis = "Imported holdings count with their individual purchase lots, sales follow FIFO. Securities without a Yahoo price appear with their transactions in the activity list and the portfolio history, but not in the portfolio."
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
        "KAUF_NETZ" -> "The market cannot be reached right now. Nothing was placed."
        "MARKT_ZU" -> "The market is closed. Nothing was placed."
        "NETZ" -> "The market cannot be reached right now. Nothing was sold."
        else -> "The bet could not be placed."
    }
    override val pmSellTitle = "Sell bet"
    override val pmSellErloes = "Proceeds now"
    override val pmSellGeldkurs = "Bid"
    override val pmSellWert = "Value at displayed price"
    override val pmSellAbschlag = "Discount"
    override val pmSellLaedt = "Loading the current price …"
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
    override val pmNoBets = "No bets yet."
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
        "From $geschaefte reported trades ($jahr from the annual report, $einzel from periodic transaction reports). The chart includes $eingerechnet trades in $werte stocks and ETFs with prices. Amounts are the midpoint of the reported range, options count as the underlying, prices without dividends. $anleihen trades in bonds, preferred shares and deposits have no daily price and are left out. On the cut-off date they made up about $anteil of the portfolio." +
            (ohneKuerzel?.let { " $it trades could not be matched to a ticker." } ?: "") +
            (ohneKurs?.let { " $it concern securities without prices." } ?: "") +
            (ohneBestand?.let { " $it sales without an earlier purchase are left out." } ?: "") +
            " Calculated up to $stichtag, then carried forward with the holdings on that day."
    override val truthTitle = "Truth Social"
    override val truthHinweis = "trumpstruth.org is an archive by Defending Democracy Together, not affiliated with Truth Social or Donald Trump. The post opens in the archive."
    override val truthRemoved = "Deleted"
    override val truthRepost = "Reposted"
    override val truthQuote = "Quote"
    override fun truthImages(n: Int) = if (n == 1) "1 image" else "$n images"
    override val truthVideo = "Video"
    override val truthNone = "The archive is not reachable right now."
    override val truthMore = "Older posts"
    override val pvNoteStrategy = "Model portfolio of euro ETFs, rebalanced to the weights once a year."
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
    override val newsWichtigFarbig = "Colored categories"
    override val newsImportantExplain = "Headlines about big events such as rate decisions, wars, tariffs or strong market moves are marked orange, especially when several outlets report or a security from your portfolio or watchlist is mentioned.\n\nOpinions, previews and buying tips are never marked."
    override val ptIndexTitle = "The index instead of your trades"
    override fun zgTerms(dienst: String) = when (dienst) {
        "guardian" -> "Free developer key for non-commercial use: up to 500 requests a day and one per second. The app uses at most 450, checks the two sections every 15 minutes and keeps Guardian content for at most 24 hours."
        "finnhub" -> "Free key for personal use only: 60 requests per minute, the data must not be passed on. The app checks the four categories every 10 minutes."
        "fec" -> "Key from api.data.gov, free with an email address: 1,000 requests per hour. The app uses at most 900 per hour, with the demo key at most 25 per hour and 45 a day. Only PAC totals and recipients are requested, no names of individual donors."
        "dol" -> "Key for the Open Data Portal of the US Department of Labor, free after signing up with a short questionnaire. The key belongs to you only and must not be shared. The app asks at most 10 times a minute and 400 times a day and keeps results for twelve hours."
        "uspto" -> "Key for the US Patent Office Open Data Portal, free with a USPTO account and an identity check via ID.me. Unused keys expire after 90 days. The app asks at most 500 times a day, one request at a time."
        "reddit" -> "Reddit API for non-commercial use: 100 requests per minute.\n\n• Since late 2025 Reddit grants new access only after an application, and self-service sign-up is closed.\n• With approved access you create an app under reddit.com/prefs/apps and enter its client ID, plus the secret for an app of type “script” or “web app”.\n• The app signs in as an app only, without your Reddit account, and only reads.\n• It uses at most 30 requests per minute and 2,000 a day and shows no posts marked 18+."
        "alphavantage" -> "Free key: 25 requests a day. The app asks at most once an hour and at most 22 times a day. Alpha Vantage labels its news as premium. If the service rejects a free key, the app shows its message."
        "eodhd" -> "EODHD key for personal use only: the data may neither be passed on nor shown to others, and the access may not be shared. 20 calls a day, reset at midnight GMT. One request costs 5 calls, so about 4 requests a day. The app asks only when you expand the section on a security's page and keeps the result for an hour."
        else -> "DeepL API Free: 500,000 characters a month. The app uses at most 450,000 in 31 days and remembers every translation, so the same text counts only once. Only market content and description texts are sent, no data about you."
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

    override val dvSubtitle = "Value and gains, your portfolio against an index with the same deposits and withdrawals"
    override val dvNone = "No purchases yet."
    override val dvMoves = "Biggest moves"
    override fun dvRaster(name: String) = when (name) { "WOCHE" -> "Week"; "MONAT" -> "Month"; "QUARTAL" -> "Quarter"; "JAHR" -> "Year"; else -> "Day" }
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
    override val dvNote = "Result of a holding on a day: its change in value without the amounts of buys and sells. Fees, taxes, interest and dividends do not count here."
    override val indButton = "Indicators"
    override val gsrTitel = "Overall direction"
    override fun gsrWort(wort: String) = when (wort) {
        "STEIGEND" -> "rising"
        "EHER_STEIGEND" -> "leaning rising"
        "EHER_FALLEND" -> "leaning falling"
        "FALLEND" -> "falling"
        else -> "no clear direction"
    }
    override fun gsrZaehlung(steigend: Int, fallend: Int, neutral: Int) = "$steigend rising · $fallend falling · $neutral neutral"
    override val gsrHinweis = "A count of the signals of all indicators, not a forecast. Related indicators count as one signal."
    override fun gsrFamilie(key: String) = when (key) {
        "durchschnitt" -> "Averages"
        "baender" -> "Bands and channels"
        "zone" -> "Overbought and oversold"
        "umsatz" -> "Volume"
        "schwung" -> "Momentum"
        else -> "Trend strength"
    }
    override val gsrEinSignal = "Counts as one signal"
    override fun kzImChart(name: String, datum: String) = "See in the advanced chart: $name on $datum"
    override fun kzName(muster: String) = when (muster) {
        "hammer" -> "Hammer"
        "inv_hammer" -> "Inverted hammer"
        "haengender" -> "Hanging man"
        "sternschnuppe" -> "Shooting star"
        "verschlingen_auf" -> "Bullish engulfing"
        "verschlingen_ab" -> "Bearish engulfing"
        "durchstossen" -> "Piercing line"
        "wolke" -> "Dark cloud cover"
        "morgenstern" -> "Morning star"
        "abendstern" -> "Evening star"
        "soldaten" -> "Three white soldiers"
        "kraehen" -> "Three black crows"
        else -> "Doji"
    }
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
        "kreuz" -> "Golden/Death Cross"
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
        "kreuz/golden" -> "50-day avg above the 200-day avg since the golden cross: long-term upward"
        "kreuz/death" -> "50-day avg below the 200-day avg since the death cross: long-term downward"
        "kreuz/keins" -> "The two averages have not crossed in the price series"
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
        "POSITIV" -> "positive"
        "NEGATIV" -> "negative"
        "GEMISCHT" -> "mixed"
        else -> "neutral"
    }
    override fun indGrund(art: String, lage: String, prozent: String, zahl: String, kreuz: String?, kreuzAuf: Boolean, eng: Boolean): String = when (art) {
        "kerzen" -> if (kreuz == null) "" else "Found on $kreuz" + when (zahl) { "0" -> ", the last trading day"; "1" -> ", one trading day earlier"; else -> ", $zahl trading days earlier" }
        "kreuz" -> when (lage) {
            "golden" -> "The 50-day avg crossed the 200-day avg upwards on $kreuz (golden cross). Today it is $prozent above it"
            "death" -> "The 50-day avg crossed the 200-day avg downwards on $kreuz (death cross). Today it is $prozent below it"
            else -> "The two averages have not crossed since the start of the price series"
        }
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
        "sma50" -> "Average closing price of the last 50 trading days.\n\n• A price above it means the recent direction was rather upwards.\n• Trends often last a while, but for single stocks the signal is weak."
        "sma200" -> "Average of the last 200 trading days, a gauge of the long-term trend.\n\n• When the 50-day average crosses it upwards it is called a golden cross, downwards a death cross.\n• Seen as a trend sign, but it often comes late."
        "bb" -> "Two lines above and below the average of the last 20 days.\n\n• How far apart they are depends on how much the price moved in those 20 days: in turbulent times they widen, in calm times they narrow.\n• About 9 out of 10 closing prices fall between them. A price outside has moved unusually far.\n• It often returns toward the middle, but in strong trends it can run along the band for a while.\n\nHow to read it: in the upper part of the range the price has recently risen (positive), in the lower part it has fallen (negative), in the middle there is no clear direction. Outside the bands the picture is mixed: the move was strong, a counter-move is possible but not certain."
        "rsi" -> "Compares the gains with the losses of the last 14 days.\n\n• High values mean the price has almost only risen recently, low values that it has almost only fallen.\n• Strong stocks often stay “overbought” for a long time.\n• Overbought and oversold count as mixed here: the price has moved far, a counter-move is possible but not certain."
        "stoch" -> "Shows where today's close lies between the lowest and the highest price of the last 14 days: 0 at the low, 100 at the high.\n\n• When the value crosses its own 3-day average, that counts as a turning signal.\n• Reacts fast and gives many false signals."
        "atr" -> "Average true range: how far the price moves on a trading day on average, over 14 days and including overnight gaps, shown here as a percentage of the price.\n\n• It says nothing about direction, only how turbulent it is.\n• Useful for placing a stop: closer than about two ATR, an ordinary day can trigger it."
        "obv" -> "On-balance volume is a running total: if the price closes higher than the day before, the whole day's volume is added.\n\n• If it closes lower, it is subtracted. The level of the line means nothing.\n• What counts is its direction compared with the price.\n• If both move the same way, the trend counts as confirmed.\n• If they drift apart, it counts as shaky.\n• If the price barely moves, OBV only shows on which days more was traded. A simplification: every purchase has a seller, so “more volume on up days” does not mean more was bought than sold.\n• Imprecise for thinly traded securities, and indices often have no volume at all."
        "ichimoku" -> "Japanese trend system built from the midpoints of high and low over the last 9, 26 and 52 days.\n\n• They form a “cloud” shifted 26 days forward.\n• The chart shows its two edges and the 26-day midpoint.\n• Above the cloud the trend counts as up, below as down, inside as unclear.\n• It follows the trend and therefore reacts late.\n• The cloud is drawn up to today, its preview of the next 26 days is missing."
        "kreuz" -> "A golden cross is when the average of the last 50 trading days crosses the average of the last 200 upwards, a death cross when it crosses downwards.\n\n• Both are read as trend signs. They come late because both lines trail the price, and in sideways phases the averages often cross several times with no follow-through.\n• The chart shows a diamond at every crossing: green for a golden cross, red for a death cross."
        "kerzen" -> "The candlestick chart shows each day as a candle made of open, high, low and close. A candlestick pattern is a recognized sequence of one to three candles from the last days that is read as a sign of a turn. The hit line above shows how often that held for this security.\n\n• After a decline, rebound possible: hammer, inverted hammer, bullish engulfing, piercing line, morning star, three white soldiers.\n• After a rise, pullback possible: hanging man, shooting star, bearish engulfing, dark cloud cover, evening star, three black crows.\n• The price path of the five days before decides whether there was a decline or a rise.\n• Doji: open and close almost equal, indecision.\n• The latest pattern of the last 5 trading days is shown.\n• Without opening prices (some indices and funds) there is no detection."
        "sar" -> "Parabolic SAR is a trailing stop: below the price in an uptrend, above it in a downtrend.\n\n• With every new high or low it moves closer faster.\n• When the price breaks through, the direction flips and the stop jumps to the other side.\n• Works in clear trends, flips constantly in sideways phases."
        else -> "Gap between the 12-day and the 26-day average, compared with its own 9-day average (signal line).\n\n• Above it, momentum is rising.\n• Reacts late and gives many false signals in sideways phases."
    }
    override val indFuss = "On the usual reading the labels mean:\n• positive: points rather to rising prices\n• negative: points rather to falling prices\n• mixed: moved far, a counter-move is possible\n\nThe predictive value is low, indicators only read the past price."
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
    override fun newsSeiteAbgelehnt(code: Int) = "The site refuses the request ($code)."
    override fun newsSeiteWeg(code: Int) = "The page no longer exists ($code)."
    override fun newsSeiteZuOft(code: Int) = "The site is not accepting requests right now ($code)."
    override fun newsSeiteNichtErreichbar(code: Int) = "The site is unreachable right now ($code)."
    override val newsSeiteVpn = "Some sites block access when using a VPN."
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
        "rueckgang" -> "Largest drawdown"
        "schwankung.depot" -> "Volatility (securities only)"
        "rueckgang.depot" -> "Largest drawdown (securities only)"
        "sharpe" -> "Sharpe ratio"
        "sortino" -> "Sortino ratio"
        "calmar" -> "Calmar ratio"
        "ulcer" -> "Ulcer index"
        "var" -> "VaR 95% (1 day)"
        "es" -> "ES 95% (1 day)"
        "var.woche" -> "VaR 95% (1 week)"
        "es.woche" -> "ES 95% (1 week)"
        "dauer" -> "Days below peak"
        "beta" -> "Beta"
        "alpha" -> "Alpha per year"
        else -> "Correlation"
    }
    override fun pvMetricExplain(k: String) = when (k) {
        "gesamt" -> "Change over the selected period."
        "jahr" -> "Average return per year, only for periods of about a year or more."
        "schwankung" -> "How much the line moves up and down, scaled to a year (volatility).\n\n• 20% means that in about two out of three years the result is at most 20 percentage points above or below the average.\n• Broad equity ETFs are around 15%, single stocks often 30% or more."
        "rueckgang" -> "Largest loss from a peak to the following low. Shows how much you would have been down at the worst moment."
        "sharpe" -> "How much return the volatility earned: return per year above the interest on cash, divided by volatility.\n\n• Example: 10% return with 2% interest and 16% volatility gives 0.5.\n• Below 0 the return was below the interest on cash, around 0.5 is typical for stock markets, above 1 is very good.\n• With equal returns, the line with the higher ratio ran more smoothly."
        "sortino" -> "Like the Sharpe ratio, but only downward moves count as risk, since jumps upwards bother nobody. If it is clearly above the Sharpe ratio, the volatility came mostly from rises."
        "calmar" -> "Return per year divided by the largest drawdown in the chosen period.\n\n• Example: 10% per year with at most 25% drawdown gives 0.4.\n• Higher is better.\n• The usual version uses three years."
        "ulcer" -> "Measures how deep and how long the line stayed below its last peak on average, in percent. 0 means steady without setbacks, below 5 calm, above 15 long and deep losing phases."
        "var" -> "A bad but ordinary stretch: on 95 of 100 days (weeks for long periods) the loss was smaller than this.\n\n• With daily values about one day a month turns out worse.\n• Shown from 100 values."
        "es" -> "How bad the remaining 5 of 100 days or weeks were on average, the really bad ones. At least as large as the VaR."
        "dauer" -> "Longest time until an earlier peak was reached again.\n\n• “+” means it is still ongoing.\n• This is how long you would have waited for the old high."
        "beta" -> "How strongly the first line moves with this one.\n\n• 1 means: if this line moves by 1%, the first moves by 1% on average too.\n• 1.5 means more violently, 0.5 more calmly, below 0 rather in the opposite direction."
        "alpha" -> "Return per year of the first line that moving along (beta) does not explain.\n\n• Positive means better than this line's moves would suggest, negative worse.\n• Varies a lot with the period."
        else -> "How alike both lines move, from 1 to −1.\n\n• At 1 they always move the same, at 0 independently, at −1 in opposite directions.\n• For better diversification, anything below about 0.7 helps."
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
    override fun opHerkunft(h: de.troetenrepublik.app.market.MarketApi.Herkunft, offen: Boolean) = when (h) {
        de.troetenrepublik.app.market.MarketApi.Herkunft.US -> "Calls as share of options volume"
        de.troetenrepublik.app.market.MarketApi.Herkunft.EURONEXT -> "Calls as share of stock option volume on Euronext"
        de.troetenrepublik.app.market.MarketApi.Herkunft.HKEX -> "Calls as share of stock option volume on HKEX"
        de.troetenrepublik.app.market.MarketApi.Herkunft.EUREX -> if (offen) "Calls as share of open interest in stock options on Eurex" else "Calls as share of stock option volume on Eurex"
        de.troetenrepublik.app.market.MarketApi.Herkunft.TAIFEX -> if (offen) "Calls as share of open interest in stock options on TAIFEX" else "Calls as share of stock option volume on TAIFEX"
        de.troetenrepublik.app.market.MarketApi.Herkunft.JPX -> "Calls as share of stock option volume on Osaka Exchange"
    }
    override val opPcr = "Put/call ratio"
    override val opPcrOffen = "Open contracts"
    override val opHvLabel = "Share price swings, 1 year"
    override fun opBewegung(tag: String) = "Expected move to $tag"
    override fun opMaxPain(tag: String) = "Max pain $tag"
    override fun opHv(wert: String) = "stock $wert"
    override val opAuffaellig = "Unusual options"
    override val opStand = "As of the last trading day, open interest from the day before."
    override val orKarte = "Options calculator"
    override val orSymbol = "Symbol"
    override val orLaden = "Load"
    override val orVerfall = "Expiry"
    override fun orVorlage(key: String) = when (key) {
        "LONG_CALL" -> "Long Call"; "LONG_PUT" -> "Long Put"; "COVERED_CALL" -> "Covered Call"; "CASH_SECURED_PUT" -> "Cash-Secured Put"
        "BULL_CALL_SPREAD" -> "Bull Call Spread"; "BEAR_PUT_SPREAD" -> "Bear Put Spread"; "STRADDLE" -> "Straddle"; "STRANGLE" -> "Strangle"
        "IRON_CONDOR" -> "Iron Condor"; "BUTTERFLY" -> "Butterfly"; "EIGENE" -> "Custom options"
        "COLLAR" -> "Collar"; "SCHUTZ_PUT" -> "Protective Put"
        else -> key
    }
    override val orKauf = "Buy"
    override val orVerkauf = "Sell"
    override fun orArt(key: String) = when (key) { "CALL" -> "Call"; "PUT" -> "Put"; else -> "Stock" }
    override val orBasispreis = "Strike"
    override val orMenge = "Quantity"
    override val orBeinNeu = "Option"
    override fun orBeinOption(preis: String, iv: String, berechnet: Boolean) = "Price $preis · IV $iv" + if (berechnet) " · calculated" else ""
    override fun orBeinAktie(menge: Int, preis: String) = "${menge * 100} shares at $preis"
    override val orBreakEven = "Break-even"
    override val orMaxGewinn = "Maximum profit"
    override val orMaxVerlust = "Maximum loss"
    override val orUnbegrenzt = "unlimited"
    override val orChance = "Probability of profit"
    override val orKosten = "Cost"
    override val orErloes = "Credit"
    override val orHeute = "today"
    override fun orAmDatum(datum: String) = if (datum == orHeute) "Today" else "On $datum"
    override fun orGriechName(key: String) = key
    override val orBewegungTitel = "Expected move by expiry"
    override val orKeine = "No options for this symbol"
    override val orNichtMoeglich = "Not available for this expiry"
    override fun orLesen(kurs: String, verfall: String, tag: String, datum: String) = "At $kurs: $verfall at expiry, $datum " + if (tag == orHeute) tag else "on $tag"
    override val opStillhalter = "Sell options"
    override fun opTage(tage: Int) = if (tage == 1) "1 day" else "$tage days"
    override val opStillCalls = "Covered call on shares you own"
    override val opStillPuts = "Cash-secured put"
    override val opBasis = "Strike"
    override val opPraemie = "Premium"
    override val opRenditeJahr = "Yield per year"
    override fun opStillBeispiel(call: List<String>?, put: List<String>?) = listOfNotNull(
        call?.let { "Call at ${it[0]}: the premium of ${it[1]} per share earns ${it[2]} until expiry. If the price rises above ${it[0]}, the shares go at that price, ${it[3]} in total with the price gain. A loss only starts below ${it[4]}." },
        put?.let { "Put at ${it[0]}: the premium of ${it[1]} per share earns ${it[2]} on the cash set aside until expiry. If the price falls below ${it[0]}, you buy the shares at that price, ${it[3]} after the premium." },
    ).joinToString("\n")
    override fun opErklaerung(k: String) = when (k) {
        "stillhalter" -> "Selling (writing) an option pays the premium at once and obliges you to deliver the shares at the strike (call) or to buy them (put).\n\n• The figures use the bid, the price buyers offer right now, per share and before fees. One contract usually covers 100 shares.\n• The yield is measured against the share price for calls and against the strike for puts, scaled to a year.\n• “Expires worthless” comes from the contract's implied volatility and is a model estimate.\n• If the stock rises sharply, the call gives up the gain above the strike.\n• If it falls sharply, the put carries the loss, reduced only by the premium.\n• In the practice portfolio options can be bought and written. Writing is done in the options calculator, shares or cash stay bound as cover."
        "pcr" -> "Puts traded per call and open puts per open call. Volume counts as in the long/short bar: the next six expiries, latest trading day only.\n\n• Above 1 many are hedging against or betting on falling prices.\n• Below 1 calls dominate, below about 0.7 clearly."
        "bewegung" -> "Price of the at-the-money call and put together.\n\n• According to the options market, the price moves this far until expiry on average, up or down.\n• In roughly three out of five cases it stays inside."
        "maxpain" -> "Price at which the options of the next expiry expire with the least total value.\n\n• Shortly before expiry the price sometimes drifts toward it.\n• It is not a forecast."
        "iv" -> "Volatility the options market prices in for the next 30 days, annualized (IV30).\n\n• Interpolated between the two expiries around 30 days. A single nearby expiry counts up to 15 days away.\n• Next to it the stock's actual volatility over the last year.\n• If implied is clearly higher, the market expects turbulence, for example before earnings."
        "wand" -> "Strikes with the most open puts (put wall) and the most open calls (call wall).\n\n• Many positions sit there, the price sometimes stalls at them.\n• It is not a forecast."
        "gex" -> "Gamma exposure (GEX): how strongly dealers who sold options have to hedge against price moves.\n\n• Positive dampens the swings.\n• Negative amplifies them.\n• The line adds up the values across strikes."
        "nullpunkt" -> "Price at which net GEX changes sign.\n\n• On one side dealer hedging dampens moves, on the other it amplifies them."
        "ivrv" -> "Implied volatility (IV30) divided by the stock's actual volatility over the last 30 days.\n\n• Above 1 the market prices in more turbulence than the stock recently showed.\n• Below 1 less."
        "skew" -> "Implied volatility of a 25 delta put minus the at-the-money one, at the expiry around 30 days. A 25 delta put is slightly out of the money.\n\n• Positive means protection against falls costs more than the middle."
        "ivrang" -> "Where today's IV30 sits between the lowest (0%) and highest value (100%) of the stored daily values.\n\n• High means options are expensive compared with their own history."
        "ivperz" -> "Share of stored days on which IV30 was lower than today.\n\n• 90% means options were dearer on only one day in ten."
        "iv" -> "Volatility the options market prices in for the next 30 days, annualized (IV30).\n\n• Interpolated between the two expiries around 30 days. A single nearby expiry counts up to 15 days away.\n• Next to it the stock's actual volatility over the last year.\n• If implied is clearly higher, the market expects turbulence, for example before earnings."
        else -> "Contracts with volume at least equal to open interest, traded premium from $50,000 and expiry within 60 days.\n\n• This points to new, large positions.\n• Whether they were bought or sold the app estimates from the last price."
    }
    override val opEbenen = "Open interest by strike"
    override val opLegendeCalls = "Calls"
    override val opLegendePuts = "Puts"
    override val opCallWand = "Call wall"
    override val opPutWand = "Put wall"
    override val opNettoGex = "Net GEX"
    override val opNullpunkt = "Zero point"
    override val opNaeherung = "Gamma: own calculation"
    override val opKurs = "Price"
    override val opVola = "Volatility"
    override val opIv30 = "IV30"
    override val opRv30 = "Realized 30 days"
    override val opIvZuRv = "IV to realized"
    override val opPutAufschlag = "Put skew 25 delta"
    override val opLaufzeit = "Term structure"
    override val opIvRang = "IV rank"
    override val opIvPerzentil = "IV percentile"
    override fun opIvSeit(tag: String) = "IV history since $tag"
    override val slAbstand = "Distance"
    override val slLaufzeit = "Term"
    override val slCall = "Call"
    override val slPut = "Put"
    override val slPraemie = "Premium"
    override val slRenditeJahr = "per year"
    override val slAusserhalb = "Expires worthless"
    override val slSpread = "Bid-ask spread"
    override fun slZahlen(tag: String) = "Earnings $tag, before expiry"
    override val slLeer = "No options for these holdings"
    override val slLaedt = "Loading"
    override fun slFortschritt(n: Int, m: Int) = "$n of $m"
    override val akMinVolumen = "Minimum volume"
    override val akVolumen = "Volume"
    override val akOffen = "Open"
    override val akVerhaeltnis = "Volume/Open"
    override val akLeer = "No unusual options"
    override val stLeer = "No new ratings in the last 7 days"
    override val stErst = "Initiated"
    override val opNasdaqGamma = "Nasdaq gamma"
    override fun opGexMischung(echt: Int, naeher: Int) = "Gamma: $echt from Nasdaq, $naeher own calculation"
    override val zmTitel = "Earnings press release"
    override val zmInhalt = "Figures and text"
    override val zmOhneKontakt = "Enter a contact address under Access keys and the company release appears here"
    override val zmKeine = "No release found"
    override val zmOriginal = "Original at the SEC"
    override val zmAusblick = "Outlook"
    override fun zmVom(tag: String) = "Release of $tag"
    override fun zmVorquartal(wert: String) = "Prior quarter $wert"
    override fun zmArt(key: String) = when (key) {
        "UMSATZ" -> "Revenue"; "BRUTTO" -> "Gross profit"; "BETRIEB" -> "Operating income"; "GEWINN" -> "Net income"
        "EPS" -> "Diluted earnings per share"; else -> "Operating cash flow"
    }
    override val bhLeihgebuehr = "Borrow fee at iBorrowDesk"
    override val vgTitel = "Executive pay"
    override fun vgCeo(jahr: Int) = "CEO pay $jahr"
    override val vgZugeflossen = "Actually paid"
    override fun vgUebrige(jahr: Int) = "Other executives avg $jahr"
    override val vgRendite = "Share return per year"
    override fun vgMio(zahl: String) = "${zahl}m"
    override fun vgMrd(zahl: String) = "${zahl}bn"
    override fun vgTsd(zahl: String) = "${zahl}k"
    override val vgInfo = "Pay table of the proxy statement, values in dollars per fiscal year.\n• Actually paid: shares and options count at their value after the price move instead of the grant date. When the price rises, the amount is usually above the granted pay.\n• Other executives: average of the further executives the company names.\n• Return: share price plus dividends in the fiscal year."
    override val pdTitel = "Presidential documents"
    override fun pdArt(art: String) = when (art) { "ERLASS" -> "Executive Order"; "PROKLAMATION" -> "Proclamation"; else -> "Memorandum" }
    override fun pdThema(id: String) = when (id) {
        "STAHL" -> "steel"; "ALUMINIUM" -> "aluminum"; "KUPFER" -> "copper"; "HALBLEITER" -> "semiconductors"; "PHARMA" -> "pharmaceuticals"
        "FLUGZEUGE" -> "aircraft"; "DROHNEN" -> "drones"; "SOLAR" -> "polysilicon"; "HOLZ" -> "timber"; "DUENGER" -> "fertilizer"
        "KOHLE" -> "coal"; "OEL_GAS" -> "oil"; else -> id
    }
    override fun pdImTitel(was: String) = "In the title: $was"
    override fun pdNeu(anzahl: Int) = if (anzahl == 0) "none in 30 days" else "$anzahl in 30 days"
    override fun pdZuletzt(datum: String) = "latest $datum"
    override fun pdAnzahl(n: Int) = if (n == 1) "1 document" else "$n documents"
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
    override val kaLaedt = "Loading trades and prices …"
    override val kaVergleich = "S&P 500 per year"
    override val kaHinweisVerlauf = "Rebuilt every week, in dollars."
    override val kaJetzt = "Holdings today"
    override val cuTitel = "Overview"
    override val cuNavUnter = "Largest positions, most traded, buys below entry price"
    override fun cuSeite(k: String) = when (k) { "GEHANDELT" -> "Most traded"; "EINSTIEG" -> "Below entry price"; else -> "Largest positions" }
    override fun cuErklaerung(k: String) = when (k) {
        "POSITIONEN_BESTAND" -> "Reported holdings at the cut-off date, valued with the figures in the filing. Per stock the sum across the group."
        "EINSTIEG_PERSON" -> "Entry: closing price on the day of the trade, weighted by amount when there are several buys. Sells reduce the holding, after a full sale the entry starts again. Under Sold: fully sold positions and sells without an earlier buy."
        "EINSTIEG_INSIDER" -> "Entry per person: reported purchase price, weighted by amount when there are several buys. Open-market buys only, no grants or options. Sells reduce the holding, after a full sale the entry starts again. Under Sold: fully sold positions and open-market sells without an earlier buy from twelve months."
        "EINSTIEG_VERKAUFT_PERSON" -> "Sold: first stocks fully sold after a buy, with today's price against the last entry price. Then sells without an earlier buy in the filings."
        "EINSTIEG_VERKAUFT" -> "First people who bought on the market and later sold everything, with today's price against their last entry price. Then open-market sells without an earlier buy from twelve months, such as shares from grants: total, sale price and today's price against it. The reduction of the holding is shown when the filing states the holding. Shares withheld for taxes, gifts and exercises do not count."
        "EINSTIEG_INVESTOR" -> "Entry: price at the end of the quarter in which the buy was reported, weighted by amount across several quarters. The buy happened at some point in the quarter. Counted is what the filing shows as still held."
        "GEHANDELT" -> "Number of reported buys and sells per stock in the period and their estimated volume."
        "EINSTIEG" -> "Stocks trading below the group's average entry price. Entry per person: closing price on the day of the trade, weighted by amount when there are several buys. For investors the price at quarter end, for insiders the reported price. If it is far from the day's close, the close counts. Sells reduce the holding, after a full sale the entry starts again. Under Sold: fully sold positions and open-market sells without an earlier buy from twelve months."
        else -> "Estimated value: reported buys minus sells per person, ranges at their midpoint."
    }
    override fun cuKurz(k: String) = when (k) {
        "POSITIONEN_BESTAND" -> "Reported holdings, summed per stock"
        "GEHANDELT" -> "Reported buys and sells in the period"
        "EINSTIEG" -> "Price below the group's average entry"
        else -> "Estimated from buys minus sells"
    }
    override fun cuPersonen(n: Int) = if (n == 1) "1 person" else "$n people"
    override fun cuKaeufeAnzahl(n: Int) = if (n == 1) "1 buy" else "$n buys"
    override fun cuVerkaeufeAnzahl(n: Int) = if (n == 1) "1 sell" else "$n sells"
    override val cuNachAnzahl = "By count"
    override val cuNachVolumen = "By volume"
    override fun cuKaeufer(n: Int) = if (n == 1) "1 buyer" else "$n buyers"
    override fun cuKaufAm(datum: String, betrag: String) = "Bought on $datum · $betrag"
    override fun cuKaeufeBis(n: Int, datum: String, betrag: String) = "$n buys, latest on $datum · $betrag"
    override fun cuQuartalBis(datum: String, betrag: String) = "Quarter to $datum · $betrag"
    override fun cuQuartaleBis(n: Int, datum: String, betrag: String) = "$n quarters, latest to $datum · $betrag"
    override val cuLeer = "No data."
    override val cuLeerZeitraum = "No trades in this period."
    override val cuKeineUnter = "No stocks below the entry price."
    override fun ieKern(unter: Int, alle: Int) = "$unter of $alle buyers"
    override fun ieKeiner(alle: Int) = if (alle == 1) "1 buyer, not below entry price." else "None of $alle buyers below entry price."
    override fun ieEinstieg(preis: String) = "Entry $preis"
    override fun ieReduziert(anteil: String) = "Holding reduced by $anteil"
    override val ieVerkauft = "Sold"
    override fun ieVerkauftAm(datum: String) = "Sold on $datum"
    override fun ieVerkaufskurs(preis: String) = "Sale $preis"
    override fun ieVerkaeufe(betrag: String) = "Sells $betrag"
    override fun ieWerte(n: Int) = if (n == 1) "1 stock" else "$n stocks"
    override fun cuStand(wann: String) = "Prices unavailable, as of: $wann"
    override val cuKeineKurse = "Prices unavailable."
    override val rgTitel = "Leaderboard"
    override val rgNav = "Whose purchases beat the market"
    override fun rgOrdnung(k: String) = when (k) { "QUOTE" -> "Hit rate"; "KAEUFE" -> "Purchases"; "VERMOEGEN" -> "Net worth"; "MELDEVERZUG" -> "Filing delay"; else -> "Lead" }
    override fun rgVermoegen(s: String) = "net worth $s"
    override fun vmSpanne(von: String, bis: String) = "$von to $bis"
    override fun vmTitel(jahr: Int) = "Net worth per annual report $jahr"
    override val vmHinweis = "Sum of the value ranges in the annual report to the Clerk of the House. Homes used by the member and government pensions are missing, so wealth can be higher."
    override val bhTitel = "Politics and agencies"
    override val bhLobbying = "Lobbying"
    override fun bhJeQuartal(betrag: String) = "$betrag a quarter"
    override fun bhQuartal(q: Int, j: Int, betrag: String, firmen: Int) = "Q$q $j: $betrag" + if (firmen > 0) ", $firmen hired lobbying firms" else ""
    override fun bhThemen(liste: String) = "Issues: $liste"
    override fun bhLobbyingHinweis(stand: String) = "As of $stand. If the company reports itself, its amount includes payments to lobbying firms."
    override fun bhGesetzeWert(gesetze: Int, personen: Int) = listOfNotNull(if (gesetze > 0) "$gesetze bills" else null, if (personen > 0) "$personen traded" else null).joinToString(" · ")
    override fun bhGesetz(g: String, n: Int) = "$g, named in $n reports"
    override val bhAuftraege = "Government contracts"
    override fun bhFiskaljahr(betrag: String, jahr: Int) = "$betrag in fiscal year $jahr"
    override fun bhFiskaljahrZeile(jahr: Int, betrag: String, laufend: Boolean) = "Fiscal year $jahr: $betrag" + if (laufend) " (still running)" else ""
    override fun bhZitat(text: String) = "“$text”"
    override val bhAuftraegeHinweis = "Obligations from federal contracts, fiscal year starting 1 October. Below, the largest contracts of the last twelve months with their total value over the whole term."
    override val bhSpenden = "Campaign contributions"
    override fun bhSpendenWert(betrag: String, zyklus: Int) = "$betrag (${zyklus - 1}/${zyklus % 100})"
    override val bhKeinPac = "No PAC found"
    override fun bhPacSummen(zyklus: Int, einnahmen: String, kandidaten: String) = "Election cycle ${zyklus - 1}/${zyklus % 100}: receipts $einnahmen, to candidates $kandidaten. Largest recipients:"
    override fun bhSpendenHinweis(demo: Boolean) = "Contributions of the company PAC. The PAC raises money from employees, not from company funds." + if (demo) " Without your own key (Access keys) only a few requests per hour." else ""
    override val bhLeer = "Short selling"
    override val bhFinraOhne = "Add FINRA access"
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
    override val dpErklaerung = "Alternative trading systems (ATS) are venues outside the exchanges where orders are not visible beforehand (dark pool).\n\n• Counted are the shares all ATS report to FINRA for the week, as a share of the week's volume on all venues.\n• A rise shows more block trading and says nothing about buying or selling.\n• Other off-exchange trading is missing."
    override fun bhLeerTage(anteil: String, tage: Int) = "Sold short in the last $tage trading days: $anteil of off-exchange reported volume"
    override fun bhLeerBestand(stichtag: String, aktien: String, aenderung: String?, tage: String?) = "Short interest on $stichtag: $aktien shares" + (aenderung?.let { " ($it vs previous date)" } ?: "") + (tage?.let { ", $it days to cover" } ?: "")
    override fun bhLeerAnteil(tage: Int) = "Sold short, $tage trading days"
    override val bhLeerAnteilHinweis = "Share of off-exchange reported volume"
    override fun bhLeerOffen(stichtag: String) = "Short interest on $stichtag"
    override val bhLeerAenderung = "Change vs previous date"
    override val bhLeerEindecken = "Days to cover"
    override val dpPlattformen = "Largest venues"
    override fun bhLeerVerlauf(seit: String, tief: String, hoch: String) = "Open short positions since $seit, between $tief and $hoch m shares"
    // [q78e]
    override val hqTitel = "Valuation against its own history"
    override fun hqArt(art: Int) = when (art) { 0 -> "P/E"; 1 -> "P/S"; 2 -> "P/CF"; else -> "EV/EBITDA" }
    override fun hqSatz(art: String, heute: String, median: String, abstand: String, ueber: Boolean) =
        "$abstand " + (if (ueber) "above" else "below") + " the median of $median"
    override fun hqBand(unten: String, oben: String) = "Middle 80%: $unten to $oben"
    override fun hqBereich(seit: String, n: Int) = "Since $seit, $n data points"
    override val hqLeerTitel = "Short interest (Nasdaq)"
    override fun hqLeerStand(datum: String, aktien: String, anteil: String?) =
        "As of $datum: $aktien shares sold short" + (anteil?.let { ", $it of the free float" } ?: "")
    override fun hqLeerTage(tage: String, vorher: String?) = tage + (vorher?.let { " (before $it)" } ?: "")
    override val hqLeerAnteil = "Short interest in % of free float"
    override val hqLeerAktien = "Short interest in m shares"
    override val hqLeerTageReihe = "Days to cover"
    override val hqKurs = "Price"
    override val hqFtdTitel = "Fails to deliver (SEC)"
    override fun hqFtdStand(datum: String, menge: String) = "Latest $datum: $menge shares failed to deliver"
    override fun hqFtdHoch(menge: String, datum: String) = "Peak in the period: $menge on $datum"
    override val hqFtdKeine = "No reports in the last months"
    override fun hqMitarbeiter(seit: Int) = "Employees since $seit"
    override val hqFtdReihe = "Shares failed to deliver per day"
    override fun hqRegSho(ja: Boolean) = if (ja) "On the Nasdaq Reg SHO threshold list" else "Not on the Nasdaq Reg SHO threshold list"
    override val hqVerlaeufe = "Trends"
    override val hqUmsatzTtm = "Revenue, trailing twelve months"
    override val hqMarktwert = "Market cap"
    override val hqExportFinanzen = "Save financials as CSV"
    override val hqExportKurse = "Save prices as CSV (OHLCV)"
    override fun hqExportFertig(n: Int) = "$n rows saved"
    override val bhLeerErklaerung = "Above, the share of shares sold short in the volume of the last trading days as reported to FINRA by off-exchange venues.\n\n• Market makers often sell short there, so a high value is normal.\n• More telling is the short interest FINRA publishes twice a month. The line shows it over up to three years.\n• If it rises with the price, many short sellers have to cover on further gains."
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
    override val bhSpendenLimit = "The shared FEC demo key is exhausted right now. With your own free key under Access keys it works right away."
    override fun pmGrossZeile(ausgang: String, preis: String, wann: String) = "“$ausgang” at $preis · $wann"

    // 5.91 Kurschart und Marktkurven
    override val kcSpanne = "Price range"
    override fun kcLinie(art: String) = when (art) { "EINSTAND" -> "Cost"; "LIMIT" -> "Limit"; "STOPP" -> "Stop"; "ALARM" -> "Price alert"; else -> "KO" }
    override fun kcMarke(art: String, stueck: String, betrag: String, kurs: String) = when (art) {
        "KAUF" -> "Bought $stueck at $kurs"
        "VERKAUF" -> "Sold $stueck at $kurs"
        else -> "Dividend $betrag"
    }
    override fun kcSpanneQuelle(implizit: Boolean, sigma: String) =
        if (implizit) "From the implied volatility of the options, $sigma per year" else "From the volatility of the last 60 trading days, $sigma per year"
    override val mkTitel = "Market curves"
    override val mkUntertitel = "Yield curve US and euro area, VIX curve"
    override val mkZins = "Yield curve"
    override val mkUsa = "US, Treasuries"
    override val mkEuro = "Euro area, AAA government bonds"
    override val mkDeutschland = "Germany, Federal bonds"
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
    override val mkZinsMehr = "• Normal: it rises, because longer commitment pays more\n• Flat: short and long maturities pay about the same\n• Inverted: short maturities pay more than ten years\n\nIn the US an inverted curve came before many recessions, months to years ahead. Not every one was followed by a recession. Red marks show short maturities above the ten-year yield."
    override val mkVix = "VIX curve"
    override fun mkVixForm(form: String) = when (form) {
        "BACKWARDATION" -> "Falling (backwardation): more fear short term than long term"
        "FLACH" -> "Flat: similar expectations short and long term"
        else -> "Rising (contango): the normal case"
    }
    override val mkVixMehr = "The VIX indices show how much investors expect the S&P 500 to swing over the next days up to one year, derived from option prices.\n• Rising curve (contango): the normal case, in calm times smaller swings are expected soon\n• Falling curve (backwardation): bigger swings are expected in the next days, which signals acute fear and showed up mostly in sell-offs"
    override val mkKeineDaten = "No data reachable right now. Try again later."

    // 5.91 Strategy lab, backtest lottery, investment thesis, own rules, monthly deposit
    override val slTitel = "Strategy test"
    override val slUntertitel = "Test signal rules in hindsight against buy and hold"
    override val slErklaerung = "Historical and hypothetical, not actually achieved: The test shows how a rule would have done in the past, which says little about the future.\n\n• After costs, most signal rules do worse than buy and hold.\n• It uses daily prices in euros over the whole price history, the signal comes from the close, the trade happens at the next day's open."
    override val slZuWenig = "There are too few daily prices for a test of this security."
    override fun slKosten(spread: String, dividenden: Boolean) = "Starting with €10,000. Costs per buy and per sell: $spread spread (from the settings) and a €1 order fee. " +
        if (dividenden) "Dividends are reinvested, taxes are not included." else "Dividends and taxes are not included."
    override val slRegeln = "Rules"
    override fun slRegel(art: String, a: Int, b: Int, n: Int) = when (art) {
        "HALTEN" -> "Buy and hold"
        "SMA" -> "Price above $a-day line"
        "KREUZ" -> if (a == 50 && b == 200) "Cross 50/200 (golden/death cross)" else "Cross $a/$b"
        "RSI" -> if (n == 14) "RSI: buy below $a, sell above $b" else "RSI $n days: buy below $a, sell above $b"
        "MACD" -> "MACD crosses its signal line"
        "DONCHIAN" -> "Breakout: buy $a-day high, sell $b-day low"
        "BOLLINGER" -> "Bollinger: buy below lower band, sell above middle"
        "RHYTHMUS" -> "16-week rhythm: weeks 13 to 15 only"
        "MOMENTUM" -> "12-month momentum: above the price a year ago at month end"
        "RSI_KURZ" -> "Short RSI in an uptrend: buy RSI $n below $a"
        "MONATSWECHSEL" -> "Turn of the month: last to trading day $a"
        "ZIELVOLA" -> "Target volatility $a%: position size by volatility"
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
    override val slLernText = "Learning period: first 70% of the days. Test period: last 30%, with a fresh €10,000. If the rule only holds in the learning period, it was probably luck."
    override val slLotterie = "Luck test"
    override val slLotterieText = "Test enough variants of a rule and one will almost always shine in hindsight, often by luck.\n\nSteps:\n1. All variants run in the learning period and the one with the best Sharpe ratio (return relative to volatility) is picked\n2. It then runs in the test period, which it has not seen\n3. A percentage says how likely its result is no lucky pick\n\nThe tabs choose which variants are tested."
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
    override val slDsrText = "The percentage is the estimated probability that the Sharpe ratio of the best variant is more than luck in picking among many attempts (deflated Sharpe ratio, Bailey and López de Prado, 2014).\n\n• From 95% the variant stands out clearly from chance, below that it can be a lucky hit.\n• It is measured against zero, so after a long rise the number can be high even without an edge over holding.\n• If the variant does not beat buy and hold in the test period, its lead in the learning period helps little.\n• Because the variants are very similar, the number is only a rough guide."
    override val slAussage = "Reliability"
    override val slAussagePruef = "Reliability, test period"
    override fun slAussageStufe(key: String) = when (key) {
        "ZU_WENIG" -> "too low"
        "BEGRENZT" -> "limited"
        else -> "usable"
    }
    override fun slAussageText(trades: Int, jahre: String) = "$trades trades in $jahre years.\n• Under 10 trades or 2 years the result says almost nothing\n• Under 30 trades or 5 years a single trade can flip it\n• The limits are rules of thumb"
    override fun slAussageHinweis(zuWenig: Boolean, trades: Int, jahre: String) =
        if (zuWenig) "With $trades trades in $jahre years the result says almost nothing."
        else "With $trades trades in $jahre years a single trade can flip the result."
    override val slParam = "Parameter landscape"
    override val slParamText = "Each cell is a variant, the color shows its return per year against buy and hold: the gain color for a lead, the loss color for a deficit, stronger for a bigger gap.\n• A single strong cell between pale ones is usually luck\n• An area of similar colors shows a rule that does not hang on one number\n• The ring marks the variant the luck test picked in the learning period, the dot the cell you tapped\n• Switch between learning and test period: whatever vanishes there was fitting\n• Neighbors are the up to eight variants next to the picked one. Shown is their mean lead over buy and hold in percentage points per year. If it drops sharply in the test period, the result hung on a single number"
    override val slParamKurz = "short average, days"
    override val slParamLang = "long average, days"
    override val slParamLaenge = "Length of the average, days"
    override fun slParamTage(n: Int) = "$n days"
    override val slParamKauf = "Buy below"
    override val slParamVerkauf = "Sell above"
    override val slParamLern = "Learning period"
    override val slParamPruef = "Test period"
    override val slNachbarnLern = "Neighbors, learning period"
    override val slNachbarnPruef = "Neighbors, test period"
    override val slZufall = "Against chance"
    override val slZufallText = "The same rule, shifted by random days: the same number of trades, the same time in the market, but no link to the prices. This shows what chance achieves with the same pattern.\n• The number is the share of the 1,000 shifted variants with at least the rule's return\n• Under 5% points to more than chance\n• It applies to this one rule chosen beforehand, for the best of many variants the luck test applies"
    override val slZufallAnteil = "Chance at least as good"
    override fun slZufallErgebnis(besserAls: Int) = "The rule is ahead of $besserAls of 100 randomly shifted variants."
    override val slZufallRegelWaehlen = "Without a sell signal there is nothing to shift. Pick another rule in the single view."
    override val slHaltenKurz = "Hold"
    override val slWf = "Walk-forward"
    override val slWfText = "Instead of one fixed split:\n1. The best variant is picked from the last 5 years\n2. It trades the next year, which it did not know\n3. Then the window moves one year ahead\nAll test years together form the curve, with continuous capital and real switches. It uses the whole price history, from 12 years.\n• Table: test start, picked rule, return per year in learning, result in the test year and the result of buy and hold in the same year\n• Test to learning: mean Sharpe ratio in testing relative to learning, under 100% what was learned does not fully hold\n• The pick among many variants is made anew in each window, so there is no percentage like in the luck test"
    override val slWfStart = "Run walk-forward"
    override fun slWfLaeuft(prozent: String) = "Walk-forward running, $prozent"
    override val slWfZuWenig = "Walk-forward needs at least 12 years of price history."
    override val slWfFenster = "Test years"
    override val slWfZusammen = "All test years per year"
    override val slWfBesser = "Ahead of buy and hold"
    override fun slWfBesserWert(besser: Int, gesamt: Int) = "$besser of $gesamt"
    override val slWfAbfall = "Test to learning"
    override fun slWfErgebnis(besser: Int, gesamt: Int, regel: String, halten: String) = "In $besser of $gesamt test years the picked rule was ahead of buy and hold. Together: $regel per year against $halten."
    override fun slWfRegel(art: String, a: Int, b: Int, n: Int) = when (art) {
        "SMA" -> "Line $a"
        "KREUZ" -> "Cross $a/$b"
        "RSI" -> "RSI $n $a/$b"
        else -> art
    }
    override val slWfKopfBeginn = "Start"
    override val slWfKopfRegel = "Rule"
    override val slWfKopfLern = "Learn"
    override val slWfKopfPruef = "Test"
    override val slWfKopfHalten = "Hold"
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
    override val thFrage = "Buy reason still valid"
    override val thVerkaufenErwaegen = "Consider selling"
    override val thEntfernen = "Remove thesis"
    override fun thMitteilung(name: String) = "Review date for $name reached. Check your reason to buy."
    override val thBestaetigtToast = "Next review in three months"
    override val thRegelnTitel = "Your own rules"
    override val thRegelnText = "Warning on purchases above the limit"
    override val thRegelPosition = "At most in one position"
    override val thRegelHebel = "No leveraged products"
    override val thRegelBargeld = "At least as cash"
    override val thAus = "Off"
    override val thAn = "On"
    override val thRegelnOk = "The portfolio currently keeps all your rules."
    override val thAuswertung = "Theses and rules"
    override val thMitThese = "with a thesis"
    override val thBestaetigt = "Reviews confirmed"
    override fun thZaehlTeile(bestaetigt: Int, erwogen: Int, regelbrueche: Int, offen: Int) = listOf("$bestaetigt confirmed", "$erwogen considered", "$regelbrueche rule breaches", "$offen open reviews")
    override val thErwogen = "Selling considered"
    override val thRegelbrueche = "Purchases against your rules"
    override val thOffeneTermine = "Reviews due"
    override val thAuswertungText = "Counted are purchases and buy orders in the app since investment theses were added. A thesis records why you buy and when you want to get out. On the review date the app asks whether the reason still holds. Writing down your reasons makes it easier to tell later whether a gain came from the thesis or from luck."
    override val ezTitel = "Deposit monthly"
    override fun ezAktiv(betrag: String, tag: String) = "$betrag $tag"
    override fun ezTagWert(tag: Int) = if (tag >= 31) "at month end" else "on day $tag"
    override val ezText = "Books a deposit into the practice portfolio every month on the chosen day, before that day's savings plans. Missed months are caught up once. Only in the practice portfolio, not with a connected real account."
    override val ezTag = "Day of month"
    override fun ezNaechste(datum: String) = "Next on $datum"
    override val ezBisher = "Paid in so far"
    override val ezAnzahl = "Deposits"
    override fun ezBisherWert(n: Int, summe: String) = "$n× · $summe"
    override val ezLoeschen = "Delete monthly deposit"
    override val ezGespeichert = "Monthly deposit saved"
    override val ezMitteilung = "Monthly deposit"
    override fun ezMitteilungText(betrag: String) = "$betrag is now in your cash account."

    // 5.91 Risikobeitrag, Bilanzqualitaet, Kaufkraft, Kennzahlenvergleich, Reverse DCF
    override val rskTitel = "Risk contribution"
    override fun rskDepotSchwankung(pct: String) = "Volatility of today's mix (12 months): $pct per year"
    override val rskGewicht = "Weight"
    override val rskRisiko = "Share of volatility"
    override fun rskZeile(gewicht: String, risiko: String) = "$gewicht of the portfolio · $risiko of the volatility"
    override fun rskSatz(name: String, gewicht: String, risiko: String) = "$name is $gewicht of the portfolio but accounts for $risiko of the volatility."
    override val rskAusgeglichen = "No holding adds clearly more to the volatility than its weight suggests."
    override val rskOhneDaten = "Not enough common trading days for an estimate."
    override fun rskNichtEingerechnet(n: Int) = if (n == 1) "One holding without enough prices is not included." else "$n holdings without enough prices are not included."
    override val rskHinweis = "The share of volatility shows how much of the portfolio's ups and downs comes from one holding.\n\n• It is based on weekly returns over the last twelve months.\n• The calculation is weight times contribution to volatility, divided by the volatility of the whole portfolio.\n• Holdings that swing a lot or move together with many others add more than their weight shows.\n• Together the shares add up to 100%."
    override val rsTitel = "Return and risk of the holdings"
    override fun rsInfo(index: String) = "Each dot is a holding: volatility per year across, return per year up, the size shows the weight in the portfolio.\n\n• The portfolio and the $index index appear as their own marks.\n• Calculated from daily prices over the chosen period, without dividends.\n• Tapping shows name and values.\n• With many holdings only the largest are labeled.\n• Holdings with a shorter price history are missing."
    override val rsLeer = "From two holdings with price history over the chosen period there is something to see here."
    override val rsRendite = "Return per year"
    override fun rsOhneVerlauf(n: Int) = if (n == 1) "1 holding has too little price history and is missing." else "$n holdings have too little price history and are missing."
    override val rskParitaetTitel = "Equal risk share"
    override val rskParitaetText = "These are the weights at which every holding would add the same amount to the volatility (risk parity)."
    override val rskUebernehmen = "Use in rebalancing"
    override val rskWarnung = "The calculation estimates volatility and co-movement from just one year. Even small estimation errors shift the weights a lot, and volatility and co-movement can turn out differently in future."
    override val rskLaden = "Loading last year's prices …"
    override val qsTitel = "Balance sheet quality"
    override val qsPiotroski = "Piotroski F-Score"
    override fun qsScore(score: Int, von: Int) = "$score of $von points"
    override fun qsStufe(stufe: String) = when (stufe) {
        "STARK" -> "strong"
        "MITTEL" -> "medium"
        else -> "weak"
    }
    override fun qsJahre(jahr: String, vorjahr: String) = "Fiscal year to\u00A0$jahr against\u00A0$vorjahr"
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
    override val qsAktienSpielraum = "Up to 0.5% more counts as unchanged"
    override val qsAltman = "Altman Z-Score"
    override fun qsZone(zone: String) = when (zone) {
        "SICHER" -> "safe zone"
        "GRAU" -> "gray zone"
        else -> "distress zone"
    }
    override val qsZonen = "Above 2.99 safe, 1.81 to 2.99 gray zone, below 1.81 distress."
    override val qsZonenAndere = "Version for companies outside manufacturing: above 2.6 safe, 1.1 to 2.6 gray zone, below 1.1 distress."
    override fun qsAltmanJahrAndere(jahr: String) = "Balance sheet to $jahr"
    override fun qsBaustein(k: String) = when (k) {
        "WC" -> "Working capital per total assets"
        "RE" -> "Retained earnings per total assets"
        "EBIT" -> "Operating profit per total assets"
        "MV" -> "Market cap per total liabilities"
        "BW" -> "Book value of equity per total liabilities"
        else -> "Sales per total assets"
    }
    override fun qsAltmanJahr(jahr: String) = "Balance sheet to $jahr, today's market cap"
    override val qsHinweis = "Both scores read the latest annual reports.\n• Piotroski F-Score: nine points for profit, cash flow, falling debt, better liquidity, no new shares and rising efficiency compared with the prior year. 7 to 9 points count as strong, 0 to 3 as weak\n• Altman Z-Score: combines balance sheet ratios and estimates how close a company is to bankruptcy. Manufacturers get the 1968 version, other companies the later version without revenue and with book value instead of market cap\n\nLimits: both only look back, and one-off items distort them. Neither fits banks, insurers or other financials, and Altman does not fit real estate companies."
    override val qsLaden = "Loading annual reports …"
    override val qsKeineDaten = "Yahoo has no annual reports for this security."
    override val kvTitel = "Fundamentals compared"
    override val kvEinstieg = "Compare with similar stocks"
    override val kvAehnlich = "Similar according to Yahoo"
    override val kvBranche = "Same industry"
    override val kvSuche = "Search for another stock"
    override val kvSucheHinweis = "Name or ticker"
    override fun kvZeile(k: String) = when (k) {
        "KGV" -> "P/E"
        "KGV_ERWARTET" -> "Forward P/E"
        "KUV" -> "P/S"
        "KBV" -> "P/B"
        "EV_EBITDA" -> "EV/EBITDA"
        "BRUTTOMARGE" -> "Gross margin"
        "OPERATIVE_MARGE" -> "Operating margin"
        "UMSATZWACHSTUM" -> "Revenue quarter"
        "VERSCHULDUNG" -> "Debt to equity"
        "DIVIDENDE" -> "Dividend yield"
        "KURS_CASHFLOW" -> "Price/cash flow"
        "ROIC" -> "ROIC"
        "OWNER_EARNINGS" -> "Owner earnings"
        "ZINSDECKUNG" -> "Interest coverage"
        "BETA" -> "Beta"
        "VOLATILITAET" -> "Volatility per year"
        "MAX_RUECKGANG" -> "Largest drawdown 1y"
        else -> "Price target"
    }
    override val kvHinweis = "Mostly the last twelve months. Ratios from different industries are only partly comparable."
    override val kvLaden = "Loading fundamentals …"
    override val rdTitel = "What the price implies"
    override fun rdSatz(x: String) = "Today's price assumes earnings grow by about $x per year for 10 years."
    override fun rdSatzRand(x: String, mehr: Boolean) = if (mehr) "Today's price assumes earnings grow by more than $x per year for 10 years." else "Today's price assumes earnings growth of less than $x a year for 10 years."
    override val rdRendite = "Your required return"
    override val rdZielKgv = "P/E after 10 years"
    override fun rdBranche(kgv: String) = "Industry $kgv"
    override fun rdKgvJetzt(kgv: String) = "P/E today: $kgv"
    override fun rdBisher(x: String, jahre: Int) = "So far: $x per year over the last $jahre years"
    override fun rdAnalysten(x: String) = "Analysts expect for the next twelve months: $x"
    override val rdHinweis = "The calculation starts from today's price. Suppose earnings grow evenly for ten years and the stock then trades at the chosen P/E. Shown is the growth needed for the chosen return each year.\n\nIf it is well above past growth, a lot of optimism is priced in.\n\nDividends, debt and one-off items are left out, and small changes to the assumptions move the result a lot. Nothing can be calculated for loss-making companies."

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
    override fun ogeUngelesen(n: Int) = if (n == 1) "1 filing has not been read yet. The app reads it the next time you open this page." else "$n filings have not been read yet. The app reads them the next time you open this page."
    override fun ogePdf(datum: String) = "Latest filing from $datum"
    override fun ogeGehandelt(n: Int) = if (n == 1) "A government official traded this security (filing 278-T)." else "$n government officials traded this security (filings 278-T)."
    override val f144Titel = "Announced sales"
    override val f144Hinweis = "Executives and large holders announce bigger sales to the SEC in advance, with number of shares, estimated value and planned date. Form 4 shows whether and when the sale really happened."
    override fun f144Stueck(n: String) = "$n shares"
    override fun f144Geplant(tag: String) = "planned $tag"
    override val f13Titel = "Major shareholders (13D/13G)"
    override val f13Hinweis = "Anyone holding more than 5% of a company reports it to the SEC.\n• 13D: active, the holder wants influence, for example on the board or a sale of the company\n• 13G: passive, the shares are only an investment, like large fund companies hold them\n\nThe latest filing of each holder is shown."
    override val f13Aktiv = "13D, active"
    override val f13Passiv = "13G, passive"
    override val omTitel = "Major shareholders trade"
    override val omHinweis = "Muscat Clearing & Depository shows purchases and sales by holders of 5% or more on the last trading day, so by large shareholders. The date shown is the day of fetching."
    override fun omAnteil(p: String) = if (p.isBlank()) "Major shareholder" else "Stake afterwards $p%"
    override val saTitel = "Sources to fetch yourself"
    override val saUntertitel = "Where to find the files and how to get them"
    override val saEinleitung = "The data stays on the device."
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
        "bund" -> "Net short positions in German shares from 0.5% of share capital (CSV file)."
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
    override fun saRegion(code: String) = when (code) {
        "AM" -> "Americas"
        "EU" -> "Europe"
        "AS" -> "Asia and Oceania"
        "AF" -> "Middle East and Africa"
        else -> "Markets and commodities"
    }
    override fun saTyp(typ: String) = when (typ) {
        "import" -> "Import"
        "index" -> "Index"
        else -> "View"
    }

    // 5.91 Profil
    override val erwUntertitel = "Price updates, interest rate, register data, export"
    override val erwHandel = "Trading costs"
    override val erwKurse = "Price updates"
    override val erwZinsAuto = "Interest currently follows the ECB key rate. Pick a rate here to use your own from then on."
    override val erwRegister = "Register data"
    override val erwPp = "Portfolio Performance"

    // 5.91 Wertpapierseite
    override val bwAlleKennzahlen = "All key figures"
    override val bwTer = "Ongoing costs (TER)"
    override val bwPreisalarm = "Notifications"
    override fun bwPreisalarmZahl(n: Int) = "Notifications · $n"
    override val bwOffeneOrders = "Open orders"
    override val bwFuerUndWider = "Opportunities and risks"
    override val bwHintergrund = "Background"
    override fun bwHintergrundUnter(themen: List<String>) = (if (themen.size == 1) "1 topic: " else "${themen.size} topics: ") + themen.joinToString(", ")
    override fun bwThema(key: String) = when (key) {
        "OPTIONEN" -> "Options"; "TRENDS" -> "Trends"; "HANDELNDE" -> "Who trades it"; "FUEHRUNG" -> "Insiders"
        "LEER" -> "Short selling"; "FDA" -> "FDA"; "BEHOERDEN" -> "Politics and agencies"; "LOBBY" -> "Lobbying Europe"
        "HALTER" -> "Large holders"; "STRUKTUR" -> "Ownership and rights"; "STUDIEN" -> "Clinical trials"; "FAHRZEUGE" -> "Vehicle recalls"; "PRODUKTE" -> "Product recalls"; "BESCHWERDEN" -> "Consumer complaints"
        "ACHTK_WARN" -> "Warning signs"; "ACHTK_DEAL" -> "Takeovers"
        "FINANZIERUNG" -> "Funding rate"; "KETTE" -> "Value locked"
        else -> key
    }
    override val bwRrUnter = "Largest drawdown, time to recover, return"
    override val bwGesetze = "Bills"

    // 5.91 Startseite
    override val maerkteTitel = "Markets"
    override val maerkteBewegungen = "Movers"
    override val handeln = "Trade"
    override val zeitraumMenue = "Custom range and trade calendar"

    // 5.92 Datenquellen
    override fun awHaeufTeile(monat: String?, anzahl: String, ueberMillion: String) = listOfNotNull(monat?.let { "Busiest month $it ($anzahl)" }, "$ueberMillion trades above $1M")
    override fun bkFairerWertKurz(preis: String, abstand: String, darueber: Boolean) = "Fair value $preis, price $abstand ${if (darueber) "above" else "below"}"
    override val dqTitel = "Data sources"
    override val chartBeschreibung = "Price chart"
    override val dqEinleitung = "• Green: answering\n" +
        "• Yellow: last request failed, the data loaded last stays visible\n" +
        "• Red: no answer for hours\n" +
        "• Gray: not used yet or switched off"
    override fun dqName(id: String) = when (id) {
        "investorenbriefe" -> "Letters from fund managers"
        "yahoo" -> "Yahoo Finance (prices, metrics, news)"
        "sec" -> "SEC (US filings)"
        "gleif" -> "GLEIF (corporate structure)"
        "ezb" -> "ECB (rates, exchange rates)"
        "eurat" -> "Council of the EU (tax jurisdictions)"
        "gdelt" -> "GDELT (coverage volume, tone of coverage)"
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
        "kursrueckfall" -> "Massive, Tiingo, Twelve Data (US prices as fallback)"
        "github" -> "GitHub (new versions, source set)"
        "strom" -> "Power in Europe (ENTSO-E, SMARD)"
        "fruehsignale" -> "Inventories and economic data (EIA, TSA, drought, GIE)"
        "pcaob" -> "PCAOB (auditors)"
        "epa" -> "EPA ECHO (environmental penalties)"
        "emissionshandel" -> "EU Emissions Trading System (Union Registry)"
        "staatsgeld" -> "Government money (TED, Contracts Finder, EU budget)"
        "osha" -> "OSHA (workplace safety penalties, Department of Labor)"
        else -> id
    }
    override val dqQuellen = "Sources"
    override val dqLizenzLabel = "License"
    override val dqBedingungenLabel = "Terms"
    override val dqAusfallLabel = "If it fails"
    override fun dqLizenz(l: de.troetenrepublik.app.engine.Quellenbuendel.Lizenz) = when (l) {
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.GEMEINFREI -> "Public domain"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.QUELLENANGABE -> "Use with attribution"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.CC_BY_SA_CC0 -> "CC BY-SA 4.0, Wikidata CC0"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.ANBIETER -> "Use under the provider's terms"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.GEMISCHT -> "Own terms per provider"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.NUTZER -> "Own key, provider's terms"
    }
    override fun dqAusfall(ersatz: List<String>, schluessel: Boolean) = when {
        ersatz.isEmpty() -> "Last saved data"
        schluessel -> "${ersatz.joinToString(", ")} (own key), otherwise last saved data"
        else -> "${ersatz.joinToString(", ")}, otherwise last saved data"
    }
    override val quellenAnhang = "Sources"
    override val quelleCboe = "© Cboe Exchange, Inc. (VIX® is a registered trademark)"
    override val quelleVixYahoo = "Source: Cboe, via Yahoo Finance"
    override val quelleIwfPortWatch = "IMF PortWatch (CC BY 4.0)"
    override val quelleWeltbank = "World Bank (CC BY 4.0)"
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
    override val dqStandLabel = "Data bundle, version"
    override val dqQuellenStandLabel = "Source list, version"
    override val dqQuellenGesucht = "Source list, last checked"
    override val dqDatenKopf = "Maintained data"
    override val dqDatenEingebaut = "built in"
    override fun dqDatenNichtGepflegt(marke: String) = "The data bundle is no longer maintained, the app stays at the state of $marke."
    override val dqInhalteKopf = "Last read successfully"
    override fun dqInhalt(name: String) = when (name) {
        "wahlen" -> "Elections"
        "kreditratings" -> "Credit ratings"
        "gdacs" -> "Disasters"
        "us-reisehinweise" -> "US travel advisories"
        "aa-reisewarnungen" -> "German travel warnings"
        "opensanctions" -> "Sanctions"
        "datenbuendel" -> "Data bundle"
        "bafin" -> "BaFin director dealings"
        "sec" -> "SEC insider trades"
        "insider-se" -> "Insiders Sweden"
        "insider-fr" -> "Insiders France"
        "insider-nl" -> "Insiders Netherlands"
        "insider-ch" -> "Insiders Switzerland"
        else -> name
    }
    override fun dqStand(version: Long, geladen: Boolean) = if (geladen) "$version (downloaded)" else "$version (built in)"
    override val dqGesucht = "Data bundle, last checked"
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
        "FASSUNG" -> "needs a newer version of the app"
        "ABGELAUFEN" -> "no longer maintained"
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
    override val zgZustandNein = "Not set"
    override val zgZustandJa = "Active"
    override val zgZustandFehler = "Error"
    override val zgIntroPunkte = listOf(
        "Keys stay on this device and only go to the respective provider.",
    )
    override fun zgZweck(dienst: String) = when (dienst) {
        "guardian" -> "Business and market news in the “The Guardian” tab, with article text."
        "finnhub" -> "Market news in the “Finnhub” tab: markets, mergers, forex and crypto."
        "alphavantage" -> "Market news in the “Alpha Vantage” tab."
        "fec" -> "Campaign contributions of a company's PAC on the page of a US stock."
        "dol" -> "Penalties from workplace safety inspections (OSHA) of the last five years under litigation and penalties on the page of a US stock."
        "uspto" -> "Patents granted per year, technology fields and patents per R&D dollar on a stock's page."
        "finra" -> "Short selling and dark pool trading on the page of a US stock."
        "reddit" -> "Latest Reddit posts about a stock with sentiment and an own ranking under Trends."
        "sec_kontakt" -> "New US insider filings directly from the SEC and fund reports for “What investors write”."
        "poly_adresse" -> "Your real Polymarket positions under “My bets”."
        "eodhd" -> "News for the opened stock on its page."
        else -> "Translates prediction markets and foreign-language descriptions of securities."
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
        "dol" -> listOf(
            "Key after signing up at dataportal.dol.gov",
            "For you only, do not share",
            "The app asks at most 10 times a minute and 400 times a day",
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
            "A randomly generated address that belongs to no one is filled in",
            "Your own address only goes to the SEC",
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
            "Only market content and description texts are sent, no data about you",
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
        "lme" -> "LME metal stocks"
        "comex" -> "COMEX metal stocks"
        "fbx" -> "Freightos Baltic Index (FBX)"
        "baltic" -> "Baltic Dry Index"
        "finra" -> "FINRA: margin statistics"
        else -> id
    }
    override fun saAnsehenInhalt(id: String) = when (id) {
        "bh" -> "Company announcements, including insider trades."
        "jm" -> "Notices of purchases and sales by directors and connected parties."
        "tt" -> "Insider trading notices among the company announcements."
        "il" -> "Mandatory filings of Israeli companies, also on holdings of major shareholders and officers. Mostly in Hebrew."
        "id" -> "Filings on share ownership of directors and major shareholders, mostly in Indonesian."
        "jp" -> "Large shareholding reports (5% and more) and company reports. The API needs a free key."
        "kr" -> "Filings on holdings of executives and major shareholders. The Open DART API needs a free key."
        "pt", "hr" -> "Managers' transactions under the EU Market Abuse Regulation."
        "jefs" -> "Annual financial disclosure reports of US federal judges with holdings and transactions. Each report after accepting the terms."
        "ep" -> "Declarations of private interests, as a PDF on each member's page."
        "aph" -> "Register of members' interests with shareholdings, as a PDF per person."
        "oge" -> "Financial disclosures of senior officials. Some reports are only available on request with Form 201."
        "lme" -> "Monthly file of warehouse stocks. The app reads the downloaded file under “Start yourself”."
        "comex" -> "Warehouse stock reports. The app reads the downloaded file under “Start yourself”."
        "fbx" -> "Container freight rates."
        "baltic" -> "Dry bulk freight rates."
        "finra" -> "Margin debt in the US by month."
        else -> ""
    }

    // 5.92 P1 Wertpapierseite oben
    override val p1BilanzLaedt = "Calculating track record …"
    override val p1BilanzWenig = "Too few signals for a track record"
    override fun indForschung(art: String): String = when (art) {
        "sma50", "sma200", "kreuz" -> "Worked in the Dow until 1986, little on later data. As a filter the average cuts drawdowns more than it adds return (Brock et al. 1992, Fang et al. 2014, Faber 2007)."
        "bb" -> "Band signals did not reliably beat buy and hold after costs. Band width mainly reflects current volatility (Lento et al. 2007)."
        "ichimoku" -> "Few studies. Default settings profitable only in some periods, never for currencies. Weak evidence (Deng et al. 2021)."
        "sar" -> "Hardly any peer-reviewed tests. Early profits of similar trend systems in futures vanished after 1985. Very weak evidence (Park and Irwin 2007)."
        "rsi" -> "Gains only in single markets without multiple-testing correction. Broad tests find no edge after costs (Chong and Ng 2008, Rink 2023)."
        "stoch" -> "Mostly practitioner studies. No robust evidence of predictive power after costs (Chong et al. 2017)."
        "macd" -> "A trend rule like a moving-average crossover. Early edges mostly vanish on new data and after costs (Chong and Ng 2008, Rink 2023)."
        "atr" -> "High volatility tends to follow high volatility. Well supported as a risk gauge, says nothing about direction (Engle 1982, Mandelbrot 1963)."
        "obv" -> "Few studies, for example Greater China and monthly signals for US indices. Not robustly shown for single stocks (Tsang and Chong 2009, Neely et al. 2014)."
        "kerzen" -> "Some reversal patterns showed signs of working for single US stocks in the 1990s. For the Dow Jones from 1992 to 2002 no value remained once chance was accounted for (Caginalp and Laurent 1998, Marshall et al. 2006)."
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
            "kreuz" -> "The 50-day avg crosses the 200-day avg upwards (golden cross, rising) or downwards (death cross, falling)."
            "bb" -> "The price drops below the lower band (rebound expected) or rises above the upper band (pullback expected)."
            "rsi" -> "The RSI climbs back above 30 (rising) or drops back below 70 (falling)."
            "stoch" -> "The stochastic climbs back above 20 (rising) or drops back below 80 (falling)."
            "macd" -> "The MACD line crosses its signal line upwards (rising) or downwards (falling)."
            "ichimoku" -> "The price leaves the cloud upwards (rising) or downwards (falling)."
            "sar" -> "The dots switch below the price (rising) or above the price (falling)."
            "obv" -> "The OBV crosses its 20-day average upwards (rising) or downwards (falling)."
            "kerzen" -> "A pattern with a direction appears: one of the six after a decline (rising) or one of the six after a rise (falling). Dojis do not count."
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
    override val pcDarstellung = "Display"
    override val pcEinblenden = "Show"
    override val pcZeichnen = "Draw"
    override val pcOhlc = listOf("Open", "High", "Low", "Close")
    override val pcEinstieg = "good for beginners"
    override fun pcWerkzeug(key: String) = if (key == "waagerecht") "Horizontal line" else chartWerkzeug(key)

    // 5.92 P2 Wertpapierseite Analyse
    override val bkTitel = "Valuation and key figures"
    override val bkBewertung = "Valuation"
    override val bkWachstum = "Financials"
    override fun bkBewertungSatz(noetig: String, art: Int, bisher: String?) = when (art) {
        1 -> "Today's price assumes earnings grow by more than $noetig per year for 10 years."
        -1 -> "Today's price assumes earnings growth of less than $noetig per year for 10 years."
        2 -> "Today's price is also reached if earnings shrink by $noetig per year for 10 years."
        else -> "Today's price assumes earnings grow by $noetig per year for 10 years."
    } + (bisher?.let { " So far: $it per year." } ?: "")
    override fun bkFairerWert(preis: String, wachstum: String, abstand: String, darueber: Boolean, zielKgv: String) =
        "Fair value at $wachstum growth per year, P/E $zielKgv after 10 years and an 8% return: $preis. The price is $abstand ${if (darueber) "above" else "below"} it."
    override val bkBewertungVerlust = "The company is not making a profit right now. Without profit there is no fair value to calculate."
    override fun bkBilanzSatz(ampel: Int, gruende: List<String>) =
        listOf("Solid balance sheet", "Mixed balance sheet", "Weak balance sheet").getOrElse(ampel) { "" } + if (gruende.isEmpty()) "" else ": " + gruende.joinToString(", ")
    override fun bkGrund(key: String) = when (key) {
        "PROFITABEL" -> "profitable"; "VERLUST" -> "making a loss"; "SCHULDEN_SINKEN" -> "debt falling"; "SCHULDEN_STEIGEN" -> "debt rising"
        "GEWINNE_STEIGEN" -> "profits rising"; "GEWINNE_SINKEN" -> "profits falling"; "GELD_FLIESST" -> "earns cash from operations"
        "GELD_FEHLT" -> "burns cash in operations"; "PLEITE_RISIKO" -> "higher risk of insolvency"; "NEUE_AKTIEN" -> "issues new shares"
        else -> key
    }
    override val bkGewinn = "Profit"
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
    override val bkZielMedian = "Price target (median)"
    override val bkGewinnErwartet = "Expected earnings per share"
    override val bkGewinnWachstumJahr = "Earnings growth this year"
    override val bkGewinnWachstumFolge = "Earnings growth next year"
    override val bkUmsatzWachstumFolge = "Revenue growth next year"
    override val bkNaechsteZahlen = "Next earnings"
    override val bkJahresVeraenderung = "Price change over 12 months"
    override val anLaedt = "Loading ratings …"
    override val anKeineDaten = "No ratings found."
    override val anVerteilung = "Distribution"
    override fun anVergleich(diff: Double) = when {
        diff > 0.1 -> "Friendlier than three months ago."
        diff < -0.1 -> "More skeptical than three months ago."
        else -> "About the same as three months ago."
    }
    override val anNurUs = "The source lists individual firms and their track record for US stocks only."
    override val anBereinigt = "Analyst ratings, adjusted"
    override fun anAlleHaeuser(n: Int) = if (n == 1) "1 firm" else "All $n firms"
    override val anGewichtet = "Weighted by hit rate"
    override val anSpalteGewichtet = "Weighted"
    override val anStufeZeile = "Rating"
    override val anMittelwert = "Average score"
    override val anTrefferZeile = "Hit rate"
    override val anZiel = "Average price target"
    override fun anZielGewichtet(preis: String) = "weighted $preis"
    override val anTrefferAlle = "Hits after 3, 6 and 12 months"
    override fun anTrefferSatz(quote: String, n: Int, monate: Int, index: String?, wenig: Boolean) =
        "$quote of $n upgrades and downgrades were right after $monate months, measured ${index?.let { "against the $it" } ?: "by the price move"}." +
            if (wenig) " That is hardly better than chance." else ""
    override fun anHinweis(herde: String) =
        "The adjusted view is only a pointer. It gives more weight to firms that were right more often on this stock and to newer ratings than older ones.\n" +
            "• The number next to the rating runs from −2 (strong sell) to +2 (strong buy).\n" +
            "• Few ratings per firm: the hit rate is pulled toward the middle.\n" +
            "• Measured against the index, so a rising market does not count as a hit.\n" +
            "• $herde of the ratings followed other firms in the same direction within three days and count less.\n" +
            "• Conflicts of interest, such as banking business with the company, cannot be seen in the data.\n" +
            "• The hit rate only measures past calls.\n" +
            "• Not investment advice."
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
        (if (us) "$anteil of the free float sold short" else "$anteil of the share capital in reported short positions") +
            when (trend) { 0 -> ", rising"; 1 -> ", falling"; 2 -> ", steady"; else -> "" }
    override val crOptionen = "Options trading"
    override fun crOptionenWert(long: String) = "Calls as share of options volume: $long"
    override fun evErwartet(eps: String?, umsatz: String?) = "expected: " + listOfNotNull(eps?.let { "EPS $it" }, umsatz?.let { "revenue $it" }).joinToString(", ")
    override val evLetzteZahlen = "Latest results"
    override fun evQuartal(ende: String) = "Quarter to $ende"
    override val rrErholung = "Recovery took"
    override val rrNochNicht = "not reached yet"
    override fun rrVonBis(von: String, bis: String) = "$von to $bis"
    override val wzRueckblick = "Review and comparison"
    override val rvKnopf = "Review"
    override val wzRueckblickUnter = "What a lump sum or a savings plan would have become"
    override val wzEinmal = "Once"

    // 5.92 Maerkte
    override val maerkteAusblick = "Outlook"
    override val infoErklaerung = "Explanation"
    override val mpVomHochKurz = "from high"
    override val cotInfo = "Every Friday the US futures regulator CFTC publishes how large participants are positioned in US futures markets, as of the Tuesday before.\n\n" +
        "• Shown is the net position of large speculators such as hedge funds\n" +
        "• The bar shows where it stands compared with the last three years: far right is as bullish as never before, far left as bearish as never before\n" +
        "• The gray line compares with every week since reporting began (1986 at the earliest, depending on the market). Net long: more bets on rising than on falling prices, net short the reverse\n\n" +
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
    override val sbSorgenMehr = "How often the English Wikipedia articles on recession and stock market crash are read, compared with the past year. A lot of reading means a lot of worry and thus a low value."
    override val trZumEtf = "Open the ETF"
    override val trZumWert = "Open security"
    override fun trKursLese(zeit: String, kurs: String, erwaehnungen: Int) = "$zeit · $kurs · " + (if (erwaehnungen == 1) "1 mention" else "$erwaehnungen mentions")
    override val rotTitel = "Sector rotation"
    override val rotInfo = "Sector rotation means investors shift money from one sector to another.\n\n" +
        "• Compared is how each sector did last month against the average of all, and whether it improved against the two months before\n" +
        "• Where it does clearly better, money flows in, where it does clearly worse, it flows out\n" +
        "• The reason is a rule of thumb from the kind of sectors, the VIX and interest rates\n\n" +
        "Calculated from the price performance of the sector ETFs in the list."
    override val rotKeine = "No clear shift between sectors right now."
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
    override val zeTitel = "Fed funds per futures"
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
    override val blTitel = "Bubble detector"
    override val blInfo = "Bubbles are usually recognized only afterwards.\n\nThese measures stood high before earlier excesses, for example in 2000 and 2021. None predicts when prices fall.\n\n" +
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
    override fun blFarben(rot: Int, gelb: Int, gruen: Int) = "$rot red · $gelb yellow · $gruen green"
    override fun blGezaehlt(n: Int) = "Only these $n bubble signs are counted. Sectors, inventories and economic data and the early warning signs are not part of the count."
    override fun blGruppe(titel: String, rot: Int, n: Int) = "$titel · $rot of $n red"
    override val engUebersichtTitel = "Sea chokepoints"
    override fun engUebersichtUnter(datum: String) = "Transits against last year, as of $datum"
    override val engAlleGlobus = "All chokepoints on the globe"
    override fun blSektor(jahr: String, zu200: String) = "$jahr in 12 months · $zu200 vs. 200-day line"
    override fun blName(schluessel: String) = when (schluessel) {
        "kgv" -> "S&P 500 P/E"
        "buffett" -> "Buffett indicator"
        "trend" -> "Stretch above the 200-day line"
        "konzentration" -> "Weight of the top 10"
        "ipo" -> "IPOs"
        "stimmung" -> "Fear & Greed stocks"
        "erwaehnung" -> "Bubble talk"
        "gewinn" -> "Expected earnings growth"
        "praemie" -> "Yield gap to bonds"
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
    override fun blkKern(schluessel: String) = when (schluessel) {
        "cape" -> "Price of the S&P 500 divided by the inflation-adjusted average earnings of the past ten years, calculated by Robert Shiller."
        "margin" -> "Growth against the previous year of loans that US securities brokers give their customers, mostly for buying stocks, from the Fed financial accounts."
        "kgv" -> "Price of the S&P 500 divided by earnings of the last twelve months, measured via the SPY ETF."
        "buffett" -> "Value of all US stocks (Wilshire 5000) relative to US economic output."
        "trend" -> "Distance of the S&P 500 from its average of the last 200 trading days."
        "konzentration" -> "Share of the ten largest stocks in the market cap of all S&P 500 stocks in the catalog."
        "ipo" -> "Gain over twelve months of the Renaissance IPO ETF, which holds stocks after their IPO."
        "stimmung" -> "CNN Fear & Greed, otherwise the app's own index: 0 means extreme fear, 100 extreme greed."
        "erwaehnung" -> "Views of the English Wikipedia article on economic bubbles: rank of the last seven days within the year, 100 means as many as never before."
        "gewinn" -> "Expected earnings growth of the S&P 500 stocks in the catalog in the next fiscal year, according to analyst estimates on Yahoo."
        "praemie" -> "Earnings yield of stocks (100 divided by the P/E) minus the yield of ten-year US Treasuries."
        "breite" -> "Lead of the cap-weighted S&P 500 (SPY) over the equal-weighted S&P 500 (RSP) over twelve months."
        else -> ""
    }
    override fun blkWarum(schluessel: String) = when (schluessel) {
        "cape" -> "Before the crashes of 1929 and 2000 it stood above 30. The higher it stood, the weaker stocks did over the following ten years so far."
        "margin" -> "Buying on credit drives prices. When they fall, brokers call for more collateral and forced sales deepen the decline."
        "kgv" -> "A high P/E means much future growth is already in the price. If it fails to come, the price loses its footing."
        "buffett" -> "When stocks rise much faster than the economy, prices run ahead of earnings. In 2000 it stood at about 140%."
        "trend" -> "A wide gap means prices have run far ahead of the long-term trend."
        "konzentration" -> "When the index rests on a few giants, a setback in them hits the whole market. In 2000 the share was a little over 25%."
        "ipo" -> "When fresh IPOs rise strongly, investors take on a lot of risk and companies like to bring new shares to market."
        "stimmung" -> "At extreme greed many have already bought, few new buyers remain."
        "erwaehnung" -> "When many read about bubbles, the topic has reached the wider public."
        "gewinn" -> "Strongly rising earnings can justify high prices. Little growth takes this support away from high valuations."
        "praemie" -> "When safe bonds yield as much as stock earnings, the price depends almost entirely on future growth."
        "breite" -> "When the cap-weighted index pulls away from the equal-weighted one, a few large stocks carry the rally."
        else -> ""
    }
    override fun blkSchwaeche(schluessel: String) = when (schluessel) {
        "cape" -> "Can stay red for years without prices falling. It says little about the next one to two years."
        "margin" -> "Quarterly values, published about ten weeks after quarter end. The series measures more broadly than the FINRA margin statistics."
        "kgv" -> "Follows the earnings cycle: when earnings collapse in a recession, the P/E rises although prices fall."
        "buffett" -> "Large US companies earn a growing share abroad that is missing from GDP. The value therefore rises even without a bubble."
        "trend" -> "Also stands high after every strong recovery, for example a few months after a crash."
        "konzentration" -> "Measures only the distribution. If the largest also earn the largest profits, a high share is no overvaluation."
        "ipo" -> "Swings strongly. After a crash the recovery alone produces a large gain over twelve months."
        "stimmung" -> "Changes within days and can show greed for weeks. Shows the mood, no timing."
        "erwaehnung" -> "A crash or a bubble in another market, such as crypto, also drives the views."
        "gewinn" -> "Based on analyst estimates. Loss-making stocks are not counted."
        "praemie" -> "The earnings yield contains no inflation, the bond yield does. With high inflation the value therefore looks worse."
        "breite" -> "Can last a long time as long as the large stocks also lead on earnings."
        else -> ""
    }
    override fun blkSchwellen(gelb: String, rot: String, faellt: Boolean, rotUeber: Boolean) =
        if (faellt) "Yellow below $gelb, red below $rot" else "Yellow from $gelb, red " + (if (rotUeber) "above" else "from") + " $rot"
    override val blkSchnitt = "Average"
    override val blkVerlauf = "History"
    override fun blkStand(datum: String) = "As of $datum"
    override val blkStaerke = "Strength"
    override fun blkStaerkeWert(stufe: Int, gemessen: Boolean) = "$stufe of 4, " + if (gemessen) "measured in the review" else "set by hand"
    override fun blkRang(anteil: String) = "Higher than in $anteil of all months since 1881"
    override val blkKredite = "Total loans"
    override val blkBip = "GDP as of"
    override fun blkBegriff(schluessel: String) = when (schluessel) {
        "cape" -> "Shiller CAPE"
        "kgv" -> "P/E ratio"
        "buffett" -> "Buffett indicator"
        "margin" -> "Securities-backed loan"
        "ipo" -> "Initial public offering (IPO)"
        "konzentration" -> "Concentration risk"
        "breite" -> "Equal weighting"
        "praemie" -> "Implied equity risk premium"
        else -> null
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
        "MELDEVERZUG" -> "Median number of days from the trade to the filing, trades of the last twelve months."
        else -> "How many percentage points the purchases of the last twelve months beat the S&P 500 by."
    }
    override val rgText2 = "How it is calculated:\n" +
        "• stock purchases of the last twelve months, from the closing price on the day of the trade to the latest close\n" +
        "• compared with the S&P 500 over the same period\n" +
        "• weighted by the midpoint of the reported range\n" +
        "• only people with at least three purchases with prices"
    override fun rgPunkte(p: String) = "$p pts"
    override fun rgAusKaeufen(n: Int) = if (n == 1) "from 1 purchase" else "from $n purchases"
    override fun rgTreffer(besser: Int, von: Int) = "$besser of $von beat the S&P 500"
    override fun rgKaufsumme(betrag: String) = "about $betrag in total"
    override val kaText2 = "Each week a portfolio of the stocks whose purchase members of Congress reported in the chosen period.\n" +
        "• weighted by the midpoint of the reported amounts, at most 10% per stock from ten stocks on\n" +
        "• if the same person reports a sale, their share shrinks by the midpoint of the sale range\n" +
        "• bought at the close of the first trading day after the filing, as you could have copied it\n" +
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
    override fun awMitZufall(anteil: String, zufall: String) = "$anteil (on any random day: $zufall)"
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
    override val pmGrossGroesste = "Biggest bets"
    override val pmProfis = "Successful traders"
    override val pmProfisLaedt = "Loading leaderboards and bets …"
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
    override val afStart = "Rank stocks"
    override val afLaedt = "Loading key figures …"
    override val afDetailsLaedt = "Loading more key figures for the top stocks …"
    override fun afDetailsMehr(n: Int) = if (n == 1) "Check this stock more closely" else "Check the top $n more closely"
    override val afKeine = "No stock matches these filters."
    override fun afBereich(b: String) = when (b) {
        "BEWERTUNG" -> "Valuation"; "QUALITAET" -> "Quality"; "MARKTMACHT" -> "Moat"; "LAND" -> "Country"; "WELT" -> "Raw material exposure"; "ANALYSTEN" -> "Analysts"; "TREND" -> "Price trend"
        "INSIDER" -> "Insiders"; "LEERVERKAUF" -> "Short selling"; else -> "Sentiment"
    }
    override fun afEinordnung(e: String) = when (e) {
        "GUENSTIG" -> "low valuation"; "FAIR" -> "medium valuation"; "TEUER" -> "high valuation"; else -> "valuation unclear"
    }
    override fun afGrund(k: String, gut: Boolean, wert: String, vergleich: String?): String {
        val branche = vergleich?.let { " (industry $it)" }.orEmpty()
        return when (k) {
            "KGV" -> "P/E $wert$branche"
            "KGV_ERWARTET" -> "Forward P/E $wert$branche"
            "KBV" -> "Price to book $wert$branche"
            "PEG" -> "PEG $wert, the P/E relative to expected growth"
            "IM_KURS" -> "The price implies $wert earnings growth per year, ${vergleich.orEmpty()} is expected"
            "KUV" -> "Price to sales $wert$branche"
            "EV_EBITDA" -> "EV/EBITDA $wert$branche"
            "RENDITE" -> "Return on equity $wert"
            "WACHSTUM" -> "Expected earnings growth $wert"
            "MARGE" -> "Profit margin $wert"
            "LAND" -> "Country risk $wert of 100 points"
            "SCHULDEN" -> "Debt $wert of equity"
            "BILANZ" -> if (gut) "Solid balance sheet" else "Weak balance sheet"
            "PIOTROSKI" -> "Piotroski F-Score $wert of 9"
            "ALTMAN" -> if (gut) "Low bankruptcy risk by Altman Z" else "Higher bankruptcy risk by Altman Z"
            "ROHSTOFFE" -> "Raw material exposure of the industry $wert of 100 points (more means fewer raw materials from few countries)"
            "NOTE" -> "Analyst rating $wert (1 = buy, 5 = sell)"
            "KURSZIEL" -> "Analyst price target $wert from the price"
            "BEREINIGT" -> if (gut) "Analysts with a good record lean positive" else "Analysts with a good record lean negative"
            "JAHR" -> "Price over twelve months $wert"
            "LINIE200" -> "$wert ${if (gut) "above" else "below"} the 200-day line"
            "INSIDER" -> if (gut) "Insiders recently bought more than they sold" else "Insiders recently sold more than they bought"
            "LEER" -> "$wert of shares sold short"
            "STELLUNG", "PREIS_BRUTTO", "PREIS_BETRIEB", "BESTAENDIG" -> mmGrund(k, gut, wert, vergleich.orEmpty(), "")
            else -> "Reddit: $wert mentions, the day before ${vergleich ?: "none"}"
        }
    }
    override val afGewichte = "Weights"
    override val afGewichteHinweis = "Country and raw material exposure are at 0 and count only with more weight. Without data an area counts as 50 points."
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
    override fun afTreffer(n: Int, gesamt: Int) = "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} of ${de.troetenrepublik.app.ui.Fmt.num(gesamt.toDouble(), 0)} stocks"
    override fun afDatenlage(p: String) = "Data for $p of the weights"
    override val afZurAktie = "Open stock"
    override val afSoRechnet = "How the stock finder works"
    override val afErklaerung = "Each area gets 0 to 100 points, 50 is unremarkable. The total score is the average by the weights.\n" +
        "• Valuation: P/E, forward P/E, price to book, price to sales and EV/EBITDA compared with the industry, plus PEG and the growth priced in\n" +
        "• Quality: return on equity, expected earnings growth, profit margin, debt and balance sheet (Piotroski, Altman)\n" +
        "• Moat (competitive advantage): share of industry sales in the catalog, margin against the industry and the lowest return on capital of recent years. Only estimated from key figures and related to quality, hence 15. From 62 points high, from 42 medium\n" +
        "• Country: political and structural risk of the country of operation, the home country if none is given. Based on World Bank governance indicators (percentile across all countries), with deductions for EU and US sanctions, OpenSanctions listings, conflicts in the country or attacks on it (UCDP), US State Department travel advisories and structural problems. The area only counts if you give it a weight above 0\n" +
        "• Analysts: average rating from 1 (buy) to 5 (sell), distance to the price target and the rating adjusted by hit rate\n" +
        "• Price trend: twelve months and distance to the 200-day line. RSI and distance to the 50-day line appear in the expanded row, the look-back test measured them, they do not feed into the points\n" +
        "• Insiders: buying and selling by management over six months\n" +
        "• Short selling: more shares sold short, fewer points\n" +
        "• Sentiment: mentions on Reddit, US stocks only\n" +
        "Low valuation means at least 62 points in the valuation area, high valuation at most 38.\n\n" +
        "Net score\n" +
        "The net score appears in the expanded row and as a sort option. It calculates like the net score on the security page from growth, net margin, valuation, cash and debt, cash flow, analyst view and price distance, without headlines. It exists only for stocks with a loaded profile and does not feed into the score.\n\n" +
        "Limits\n" +
        "• Without data an area counts as 50 points\n" +
        "• Key figures arrive delayed and can contain errors\n" +
        "• Price to sales, EV/EBITDA, margin, debt and price target come from single lookups and only count once half of the stocks have them\n" +
        "• The sales share in the moat only counts once the statements are loaded for 60% of the industry's catalog stocks. Banks and insurers have no moat score\n" +
        "• Country risk hits all stocks of a country equally, solid companies too\n" +
        "• The points rest on the past and on other people’s expectations"
    override val afHinweis = "Not investment advice. The data can be wrong or out of date."
    override fun afNachbilden(n: Int) = if (n == 1) "Replicate this stock as a portfolio" else "Replicate the top $n as a portfolio"
    override fun afStand(zeit: String) = "As of $zeit"
    override val afCsv = "Save hits as CSV"
    override val afCsvSpalten = listOf("Symbol","Name","Country","Industry","Score")
    override fun afCsvFertig(n: Int) = "$n hits saved"
    override fun afCsvDatei(name: String) = "tryfolio-$name.csv"
    override val wdTitel = "Track record"
    override val wdNav = "Insider clusters, puts, Congress and IPOs against the market"
    override val wdKeineDaten = "No data"
    override val wdClusterTitel = "Insider cluster buys"
    override fun wdClusterRendite(halten: Int) = "Average after $halten trading days"
    override val wdClusterIndex = "S&P 500, same period"
    override val wdClusterTreffer = "Beat the S&P 500"
    override val wdClusterMedian = "Median"
    override val wdClusterSchlechtester = "Worst cluster"
    override val wdClusterZahl = "Clusters"
    override fun wdClusterRegel(mindest: Int, fenster: Int, halten: Int, ab: String) =
        (if (mindest == 1) "Cluster: 1 executive of a company buys" else "Cluster: at least $mindest executives of one company buy") +
            " their own shares within $fenster days, counted from the filing, since $ab"
    override val wdClusterName = "Clusters"
    override val wdClusterIndexName = "S&P 500"
    override fun wdClusterAchse(halten: Int) = "Trading days after the buy, 0 to $halten"
    override val wdKopfJahr = "Year"
    override val wdKopfCluster = "Clusters"
    override val wdKopfClusterRendite = "Avg\nclusters"
    override val wdKopfIndexRendite = "Avg\nS&P 500"
    override val wdKopfBesser = "Beat"
    override fun wdClusterAusgenommen(ohne: Int, offen: Int) = "$ohne without prices, $offen still open"
    override val wdPutTitel = "Highest-yield sold puts"
    override val wdPutLaedt = "Setting up the log …"
    override val wdPutLeer = "No entries yet"
    override val wdPutProtokoll = "Logged"
    override val wdPutAbgeschlossen = "Expired"
    override val wdPutNominal = "Nominal yield per year"
    override val wdPutRealisiert = "Realized yield per year"
    override val wdPutVerlust = "With a loss"
    override val wdPutSchlechtester = "Worst put"
    override val wdPutNominalKurz = "Nominal"
    override val wdPutRealisiertKurz = "Realized"
    override fun wdPutSeit(datum: String, offen: Int) = "Since $datum, $offen open"
    override val wdKopfAktie = "Stock"
    override val wdKopfVerfall = "Expiry"
    override val wdKopfStrike = "Strike"
    override val wdKopfNominal = "Nominal"
    override val wdKopfRealisiert = "Realized"
    override val wdPutOffen = "open"
    override val wdKongressTitel = "Copying Congress"
    override val wdKongressAbstand = "Difference per year"
    override val wdKongressRechnen = "Calculate with today's data"
    override val wdIpoTitel = "US IPOs"
    override val wdIpoTag1 = "Average first-day return"
    override val wdIpoMinus = "Loss on the first day"
    override val wdIpoDrei = "Average 3 years vs market"
    override val wdIpoUnterMarkt = "Years below market"
    override fun wdIpoRegel(n: Int, von: Int, bis: Int) = "${"%,d".format(java.util.Locale.US, n)} IPOs, $von to $bis"
    override val wdKopfIpos = "IPOs"
    override val wdKopfTag1 = "First\nday"
    override val wdKopfMinus = "Loss\nday 1"
    override val wdKopfDrei = "3 years\nvs market"
    override val wdAlleJahre = "Show all years"
    override val wdWenigerJahre = "Show less"
    override val afWeniger = "Show less"
    override fun afMehrZeigen(n: Int) = "Show ${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} more"
    override val radStart = "Start radar"
    override val radNeu = "Reload"
    override val radLaedt = "Querying sources …"
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
    override val radBekannt = "already widely watched"
    override fun radArten(n: Int) = if (n == 1) "1 kind of sign" else "$n kinds of signs"
    override val radSoRechnet = "How the radar works"
    override val radErklaerung = "For each stock the signs of the last 90 days count, newer ones weigh more.\n" +
        "• Different signs for the same stock count more together\n" +
        "• The ten most mentioned stocks on Reddit count less because they are already being talked about\n" +
        "• Wikipedia views are only checked for the ten strongest candidates, major holders and options for up to six of them (US stocks without a dot in the ticker)\n" +
        "• Insider filings and purchases by politicians get fresher when you open the insider and politics pages\n\n" +
        "Limits\n" +
        "• Many signs lead nowhere: insiders also buy out of duty, politicians report up to 45 days late, volume also rises with index changes\n" +
        "• The radar says nothing about whether a price will rise"
    override fun radStand(zeit: String) = "As of $zeit"
    override val radHeute = "today"

    // 5.92 Fruehindikatoren
    override val fiTitel = "Inventories and economic data"
    override val fiInfo = "Data that can show early how supply and demand are doing. Oil inventories, for example, show whether crude is getting scarcer than usual for this time of year.\n\n" +
        "Limits:\n" +
        "• “Already priced in” is an estimate from history, because professionals analyze the same numbers, often seconds after release\n" +
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
        "fluggaeste" -> "US air travelers"
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
        1 -> ", the labor market is cooling"
        else -> ""
    }
    override fun fiFlugSatz(veraenderung: String, mehr: Boolean) = "$veraenderung ${if (mehr) "more" else "fewer"} travelers than a year ago (last 28 days)"
    override fun fiErklaerung(schluessel: String) = when (schluessel) {
        "rohoel" -> "Crude oil in US storage excluding the strategic reserve, every Wednesday from the EIA. Tight inventories point to tight supply or strong demand and tend to support the oil price, full ones tend to weigh on it."
        "cushing" -> "Storage hub in Oklahoma where the US oil contract WTI is delivered. Very empty tanks can make the next WTI contract more expensive for a short time, very full ones can push it down as in April 2020."
        "benzin" -> "US gasoline stocks.\n\n• A lot of gasoline points to weak demand or large supply, little gasoline supports the price.\n• People drive more in summer and stocks usually fall, so the app compares with the same time of year."
        "destillat" -> "Diesel and heating oil, important for trucks, farming and winter heating. Tight stocks often make diesel rise more than crude."
        "gas_us" -> "Working gas in underground storage in the US, every Thursday from the EIA.\n\n• Gas is injected in summer and withdrawn in winter.\n• Full storage before winter dampens the price, empty storage makes it vulnerable to cold."
        "gas_eu" -> "Fill level of gas storage in the EU, daily from GIE AGSI+. A low level before winter makes the European gas price (TTF) vulnerable. It often moves differently from the US price Henry Hub."
        "erstantraege" -> "How many people in the US file for unemployment benefits for the first time in a week, seasonally adjusted, every Thursday from the US Department of Labor.\n\n• If the four-week average rises clearly above its low, the labor market is weakening.\n• From 10% the rise counts as clear, from 20% as a warning sign."
        "fluggaeste" -> "Travelers at US airport security checkpoints, daily from the TSA.\n\n• A hint on travel and consumer spending, important for airlines and travel companies.\n• Holidays such as Easter or Thanksgiving fall differently each year and distort the comparison for a short time."
        "duerre" -> "Share of the area of the US excluding Alaska and Hawaii in at least moderate drought, weekly from the U.S. Drought Monitor (National Drought Mitigation Center, USDA and NOAA).\n\n• Drought in growing regions can reduce harvests of corn, wheat and soybeans.\n• The number covers the whole country, not only farmland."
        else -> ""
    }
    override fun fiStand(datum: String) = "As of $datum"
    override fun fiWoche(aenderung: String) = "Week $aenderung"
    override fun fiBereich(von: String, bis: String, jahre: Int) = "${fiZahlwort(jahre).replaceFirstChar { it.uppercase() }} previous years at the same time: $von to $bis"
    override fun fiMioBarrel(wert: String) = "$wert m barrels"
    override fun fiMrdKubikfuss(wert: String) = "$wert bcf"
    override fun fiMioTag(wert: String) = "$wert m a day"
    override val fiPunkte = "points"
    override fun fiSpr(wert: String, woche: String?) =
        "Strategic Petroleum Reserve: $wert" + (woche?.let { ", week $it" } ?: "") + ". It follows political decisions and therefore does not count toward the light."
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
    override fun afBilanzStand(n: Int, gesamt: Int) = "Balance sheet data ${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} of ${de.troetenrepublik.app.ui.Fmt.num(gesamt.toDouble(), 0)}"
    override val afNeuOrdnen = "Re-sort"
    override val afBilanzWeiter = "Continue loading"
    override val afBilanzFolgt = "Balance sheet data still loading"
    override fun afBilanzZahlen(f: String?, zone: String?) =
        listOfNotNull(f?.let { "Piotroski F-Score $it of 9" }, zone?.let { "Altman Z: $it" }).joinToString(" · ")
    // 5.92 Ersatzquellen
    override fun ersSenatIntro(stand: String) = "Trades by US senators.\n• The STOCK Act (disclosure duty for lawmakers) requires filing within 45 days\n• The amount is a range\n• Three years are built in, newer filings are fetched" + (if (stand.isBlank()) "" else "\n• Latest filing from $stand")
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
        "lieferketten" -> "Global Supply Chain Pressure Index of the New York Fed.\n\n• It combines shipping and air freight rates with surveys on delivery times and backlogs in seven large economies.\n• 0 is the long-term average, 1 is one standard deviation above it, in 2021 it was a little over 4.\n• Yellow from 0.5, red from 1.5."
        "fracht" -> "Freight index of the US Department of Transportation (BTS) for trucks, rail, waterways, pipelines and air freight, seasonally adjusted.\n\n• It stands in for freight indices whose data is not free to use.\n• Below the same month last year yellow, 3% or more below red."
        "bahn" -> "Loaded rail freight cars in the US per month, seasonally adjusted, according to the BTS. The weekly figures of the rail association are only published as PDF. Below the same month last year yellow, 3% or more below red."
        "halbleiter" -> "Fed production index for semiconductors and electronic components in the US.\n\n• It stands in for the worldwide sales figures of the SIA and WSTS, which are not free to use.\n• Below the same month last year yellow, 3% or more below red."
        else -> ""
    }
    override fun ersStand(monat: String) = "As of $monat"
    override fun ersVorjahr(wert: String) = "$wert year on year"
    override val ersHnTitel = "Hacker News"
    override fun ersHnZeile(woche: Int, schnitt: String) = (if (woche == 1) "1 story" else "$woche stories") + " in 7 days · before $schnitt per week"
    override fun ersMastodonTitel(tag: String) = "Mastodon · #$tag"
    override val ersNetzHinweis = "The company name is counted. Words with the same spelling count too."
    override fun ersMrdDollar(wert: String) = "$" + wert + " bn"
    override fun ersQuartal(q: Int, jahr: Int) = "Q$q $jahr"
    // 5.92 Eingepreist
    override val epTitel = "Already priced in"
    override fun epTendenz(richtung: Int, was: String?) = when (richtung) {
        1 -> "Leaning: price would have to rise"
        -1 -> "Leaning: price would have to fall"
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
    override val epInfo = "“Already priced in” shows whether the market has already taken a signal into account. The leaning says whether the price would have to rise or fall for this.\n\n" +
        "• Green: already priced in, no clear direction\n" +
        "• Yellow: the market sees it clearly differently from the signal. The leaning shows where the price would have to move for both to fit again\n" +
        "• Red: like yellow, but the gap is as large as rarely before\n" +
        "• Gray: there was no reliable relationship in the past, so no leaning\n\n" +
        "Oil example: when inventories are tight, oil for early delivery usually costs more than later. If that premium is larger than inventories explain, the price would have to fall. If it is smaller, the tightness is not yet in the price and the price would have to rise.\n\n" +
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
            1 -> if (lage == 1) "${lager.replaceFirstChar { it.uppercase() }} $sind full, yet the price expects tightness. The price is above what the $lager ${if (gas) "explains" else "explain"}."
                else "The price expects more tightness than the $lager $zeigt. The price is above what the $lager ${if (gas) "explains" else "explain"}."
            else -> if (lage == -1) "${lager.replaceFirstChar { it.uppercase() }} $sind tight, the price does not show it yet. The price is below what the $lager ${if (gas) "explains" else "explain"}."
                else "The price expects more surplus than the $lager $zeigt. The price is below what the $lager ${if (gas) "explains" else "explain"}."
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
    override fun epKurve(spanne: String, teurer: Boolean) = "Nearest contract $spanne per year ${if (teurer) "dearer" else "cheaper"} than the fourth"
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
        "• Yellow: only part has played out. Leaning in the direction of the signal: price rather rising after purchases and upgrades, rather falling after downgrades\n" +
        "• Red: the stock moved against the signal, no clear direction\n" +
        "• Gray: such signals brought no lead for this stock in the past"
    override val epKaeufeTitel = "Already priced in\nSince the purchases"
    override val epKaeufeHinweis = "Measured from the filing date, when the purchases became public, against the S&P 500. From a lead of about 10% most of it counts as priced in."
    override fun epKaufZeile(datum: String, mehr: String, index: String) = "Filed $datum · $mehr against the $index"
    override val epAnalystenTitel = "Already priced in\nLatest upgrade or downgrade"
    override val epInsiderTitel = "Already priced in\nLatest insider purchase"
    // 5.92 Finder-Test
    override val afRueckblickTitel = "Past results of the scoring"
    override val afRueckblickAlt = "With earlier weighting"
    override fun afRueckblickText(von: String, bis: String, werte: Int, top20: String, msci: String, spx: String, treffer: String) =
        "Look back $von to $bis: at every month end the app scored the S&P 500 using only data known then. " +
            "The 20 stocks with the most points returned $top20 on average over the next twelve months, the MSCI World $msci, the S&P 500 $spx. They beat the MSCI World on $treffer of the dates."
    override fun afRueckblickGrenzen(bilanzAb: String?, ganz: Boolean, ic: Double, icSe: Double, bewertung: Pair<Double, Double>?, abdeckung: String?): String {
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
            "\n• A cheap valuation did not rank later returns in this period (rank correlation ${String.format(Locale.US, "%.2f", b).let { z -> if (z.all { c -> c == '-' || c == '0' || c == ',' || c == '.' }) z.trimStart('-') else z.replace('-', '−') }})."
        } ?: ""
        return "Limits\n" + geprueft + bew + "\n" +
            "• Insiders, short selling and sentiment are missing earlier and did not count.\n" +
            "• The points rank later returns $staerke (rank correlation ${String.format(Locale.US, "%.2f", ic).let { z -> if (z.all { c -> c == '-' || c == '0' || c == ',' || c == '.' }) z.trimStart('-') else z.replace('-', '−') }}).\n" +
            (abdeckung?.let { "$it\n• Prices without dividends." } ?: "• Missing firms flatter the result. Prices without dividends.")
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
        "wci" -> "Drewry World Container Index: price of a 40 ft container on eight major routes from Asia, Europe and the US, updated every Thursday.\n\n• Sharp jumps point to bottlenecks at sea, sharp drops often to weak demand.\n• Yellow from 5% week on week, red from 15%."
        "aar" -> "Weekly figures of the Association of American Railroads (AAR): loaded carloads plus containers and trailers, compared with the same week a year earlier.\n\n• They come out every Wednesday and show earlier than the monthly data how much freight is moving.\n• Yellow below last year, red from 3% below."
        else -> "Worldwide monthly sales of chip makers according to WSTS, the industry's statistics body, compared with the same month a year earlier.\n\n• The figures come out about two months later.\n• Yellow below last year, red from 3% below."
    }
    override fun wqWoche(wert: String) = "Week on week $wert"
    override fun wqBahnTeile(wagen: String?, container: String?) =
        listOfNotNull(wagen?.let { "Carloads $it" }, container?.let { "Containers and trailers $it" }).joinToString(" · ")
    override fun wqWocheBis(tag: String) = "Week ending $tag"
    override fun wqStandTag(tag: String) = "As of $tag"
    override fun wqEinheiten(n: String) = "$n carloads and containers"
    override val wqFbxLink = "Freightos Baltic Index (FBX) in the browser"
    override val wqBalticLink = "Baltic Dry Index in the browser"
    override val wqComexLink = "Download COMEX reports in the browser"
    override val lmeTitel = "LME metal stocks"
    override fun lmeStand(tag: String) = "As of $tag"
    override fun lmeTonnen(zahl: String) = "$zahl t"
    override val lmeVormonat = "vs. previous month-end"
    override fun lmeName(kuerzel: String) = when (kuerzel) {
        "CA" -> "Copper"; "AH" -> "Aluminum"; "ZS" -> "Zinc"; "NI" -> "Nickel"; "PB" -> "Lead"; "SN" -> "Tin"
        "CO" -> "Cobalt"; "AA" -> "Aluminum alloy"; "NA" -> "Aluminum alloy North America"; else -> kuerzel
    }
    override fun lmeVerlauf(monate: Int) = "Closing stock per trading day, $monate months"
    override val lmeBrowser = "Download LME monthly file in the browser"
    override val lmeEinlesen = "Read downloaded file"
    override val lmeUnbekannt = "File not recognized"
    override val comexTitel = "COMEX metal stocks"
    override fun comexName(schluessel: String) = when (schluessel) {
        "GOLD" -> "Gold"; "SILBER" -> "Silver"; "KUPFER" -> "Copper"; "PLATIN" -> "Platinum"; "PALLADIUM" -> "Palladium"
        "ALUMINIUM" -> "Aluminum"; "ZINK" -> "Zinc"; "BLEI" -> "Lead"; else -> schluessel
    }
    override fun comexMenge(zahl: String, einheit: String) = zahl + " " + (if (einheit == "OZ") "oz" else if (einheit == "SHT") "sh tn" else "t")
    override val comexVorigerTag = "vs. previously loaded day"
    override val comexLieferbar = "Registered"
    override val comexEingelagert = "Eligible"
    override fun comexVerlauf(tage: Int) = "Total stock per loaded day, $tage days"
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
    override val wqRichterTitel = "US federal judges"
    override val wqRichterIntro = "Federal judges report their investments and trades once a year, amounts only as ranges. Shown are the trades from the reports CourtListener processed most recently."
    override val wqRichterOhneZugang = "Visible with a free CourtListener token under Access keys"
    override val wqRichterLeer = "The most recently processed reports contain no trades."
    override val wqRichterLaedt = "Loading reports …"
    override val wqRichterName = "Name follows on the next load"
    override fun wqRichterZeile(jahr: Int, tag: String) = listOfNotNull(if (jahr > 0) "Report $jahr" else null, tag.ifBlank { null }).joinToString(" · ")
    override fun wqSpanne(von: String, bis: String?) = if (bis == null) "over $von" else "$von to $bis"
    override val wqJefsLink = "All reports at JEFS in the browser"
    override val wqParlamenteTitel = "Other parliaments"
    override val wqRegisterBrowser = "More registers in the browser"
    override val wqEuLink = "Members of the European Parliament: declarations of financial interests"
    override val wqAusHausLink = "Australia: House register of interests"
    override val wqAusSenatLink = "Australia: senators' interests"
    override val wqBskyTitel = "Bluesky"
    override fun wqBskyZeile(woche: Int, vorwoche: Int, gekappt: Boolean) =
        (if (gekappt) "More than $woche" else "$woche") + (if (woche == 1 && !gekappt) " post" else " posts") + " in 7 days · previous week $vorwoche"
    override val wqXLink = "View on X"
    override val wqStocktwitsLink = "View on Stocktwits"
    override val wqClName = "CourtListener (US federal judges)"
    override val wqClZweck = "Stock trades of US federal judges from their annual financial disclosures, under Copytrading in Politics, plus dockets and judgments of the company under litigation and penalties."
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
        else -> zgLimit
    }
    // 5.92 Tendenz-Rueckblick
    override fun trFrueher(rot: Boolean, treffer: String, grund: String, wochen: Int, mittel: String, faelle: Int) =
        "In the past${if (rot) " at red" else ""}: right in $treffer (without signal $grund), best ${trNach(wochen)}, $mittel on average, $faelle cases"
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
    override fun trDetailZeile(wochen: Int, treffer: String, grund: String, mittel: String, faelle: Int, belegt: Boolean) =
        trNach(wochen).replaceFirstChar { it.uppercase() } + ": $treffer right (without signal $grund), $mittel, $faelle cases" + if (belegt) ", proven" else ""
    override fun trGeprueft(von: String, bis: String) = "Checked week by week from $von to $bis, each time only with the data available then."
    override fun trPunkte(wert: String) = "$wert points"
    override fun trIdeeTitel(call: Boolean, name: String) = "Practice idea: ${if (call) "call" else "put"} on $name"
    override fun trIdeeDaten(basis: String, verfall: String, hebel: String) = "Strike $basis · expiry $verfall · leverage about $hebel"
    override fun trIdeeRisiko(zeitwert: String) =
        "You can lose at most the purchase price. If the price does not move, the warrant loses about $zeitwert of time value per week."
    override val trIdeeHinweis = "Practice suggestion based on past relationships, not investment advice."
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
        "• “Right in 61% (without signal 50%)” means: in 61 of 100 cases the price moved in the direction of the leaning, on random days in 50\n" +
        "• A leaning counts as proven only with at least 20 cases that do not overlap in time and with an edge that can hardly be chance\n" +
        "• “No edge in the past” means: following the leaning did not do better. The direction is then shown in gray\n\n" +
        "Markets often stay out of line with the data for a long time.\n\n" +
        "The app shows a practice suggestion with a warrant only for a proven leaning, with a term about one and a half times the period in which it was right."

    // 5.92 Investorenbriefe
    override val ibTitel = "What investors write"
    override fun ibRichtung(r: Int) = when {
        r > 0 -> "rather positive"
        r < 0 -> "rather negative"
        else -> "neutral"
    }
    override fun ibRichtungKurz(r: Int) = when {
        r > 0 -> "positive"
        r < 0 -> "negative"
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
    override val ibSecHinweis = "Without a contact address under Access keys, the app does not search fund reports at the SEC."

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
    // 5.93 strategielabor
    override val slEigen = "Custom"
    override val slEigenHinweis = "Only this period is simulated, but the rules' indicators start running earlier."
    override fun slLotterieErgebnis(pruefBesser: Boolean, echt: Boolean, anzahl: Int, prozent: String) = when {
        pruefBesser && echt -> "The best of $anzahl variants also beats buy and hold in the test period. At $prozent, probably not luck."
        pruefBesser -> "The best of $anzahl variants beats buy and hold in the test period. At only $prozent, it can be luck."
        echt -> "The best of $anzahl variants stands out from luck at $prozent, but does not beat buy and hold in the test period."
        else -> "The best of $anzahl variants loses to buy and hold in the test period. At $prozent, probably only luck."
    }
    // 5.93 hintergrund
    override val rrHintRendite = "Gain or loss converted to one year, without dividends."
    override val rrHintBeta = "Move against the index, 1 means equally strong"
    override val rrHintKorrelation = "Shows how similarly the stock and the index move. 1 means in step, 0 independent, −1 opposite."
    override val lvKurz = "A high share shows that many bet on falling prices."
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
    override fun tdText(richtung: Int, ziel: String) = listOf("Bullish", "Neutral", "Bearish").getOrElse(richtung) { "" } + " for " + when (ziel) {
        "aktien" -> "stocks"; "aktien_eu" -> "European stocks"; "anleihen" -> "bonds"; "oel" -> "oil prices"
        "benzin" -> "gasoline prices"; "diesel" -> "diesel prices"; "gas_us" -> "US natural gas"; "gas_eu" -> "natural gas in Europe"
        "fluglinien" -> "airlines"; "getreide" -> "grain prices"; "gold" -> "gold"; "chips" -> "chip stocks"
        "transport" -> "transport stocks"; "reedereien" -> "shipping companies"; else -> ziel
    }
    override fun sbVixMehr(vix: String) = "The VIX measures expected S&P 500 volatility and stands at $vix. A reading of 100 means as calm as at any time in the past year. A reading of 0 means as turbulent as ever."

    // 5.93 ipo
    override val secIpoTitel = "New SEC filings"
    override val secIpoKontakt = "Without a contact address under Access keys, the app shows no new IPO prospectuses and pricings from the SEC here."
    override val secIpoLeer = "No matching filings in the last trading days."
    override val secIpoProspekt = "Prospectus filed"
    override val secIpoAenderung = "Prospectus amended"
    override val secIpoPreis = "Price set"

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
    override fun erTitel(n: Int) = if (n == 1) "In 1 ETF" else "In $n ETFs"
    override fun erEtikett(tiefe: Int, datum: String) = "Top $tiefe positions per ETF, as of $datum"
    override val hlHinweis = "Institutions report their holdings every quarter, in the US with Form 13F up to 45 days after quarter end, so the figures are older than the price."
    override fun zuNaechster(datum: String) = "Next results: $datum"
    // 5.93 maerkte
    // 5.93 Markets: interpretations
    override val korbTitel = "Themes"
    override fun korbName(i: Int) = listOf("Artificial intelligence", "Semiconductors", "Defense", "Space", "Cybersecurity", "Nuclear energy", "Robotics", "Gaming", "Crypto stocks", "Weight-loss drugs", "Hydrogen", "Solar")[i]
    override fun korbSteigen(n: Int, m: Int) = "$n of $m rising"
    override val indexBeitragTitel = "Moves the index"
    override fun indexPunkte(zahl: String) = "$zahl pts"
    override val moverRvolFilter = "Relative volume 2 or more"
    override fun moverRvol(v: String) = "Relative volume $v"
    override fun moverGrund(i: Int) = listOf("Earnings", "Analyst", "52-week high", "52-week low")[i]
    override fun moverZeit(i: Int) = listOf("1D", "1W", "1M", "1Y", "3Y", "5Y")[i]
    override fun nwArt(i: Int) = listOf("Earnings", "Analyst", "Takeover", "Capital action", "Approval", "Contract", "Sector")[i]
    override fun nwKursAb(x: Int) = "Move over $x%"
    override val stTitel = "Sectors"
    override val stKgv = "P/E"
    override val stRendite = "Dividend"
    override val stMarktwert = "Fund size"
    override fun ipoErsterTag(p: String) = "First day $p"
    // [Ausblick 0.70] Overall outlook
    override val agTitel = "Overall outlook"
    override val agInfo = "Combines the entries of this tab into one direction per market for weeks and for months. The direction is a weighting of the entries.\n\n" +
        "• Each entry counts as positive, neutral or negative, as shown in its entry\n" +
        "• The sentence under the direction gives shares of the total weight\n" +
        "• Mixed: both sides have at least a quarter of the weight, and the score stays small\n" +
        "• Fresh data counts fully, older data loses up to 60% of its weight\n" +
        "• Entries with the same basis count once: rate futures and rate bets, rail and semiconductors from two sources each\n" +
        "• Weeks: only entries that change quickly. Months: everything that works over a few months\n" +
        "• Industry indicators (chips, transport, airlines, shipping, Europe) count for stocks with a small weight\n" +
        "• With few current entries it says open\n" +
        "• Oil, gas, bonds, gold and grain have their own rows\n" +
        "• Bubble signs do not count, they appear as a separate note below\n\n" +
        "The weights are set and not measured. Weight and rhythm per source:\n"
    override fun agQuelleZeile(name: String, gewicht: String, woechentlich: Boolean) = "• $name: $gewicht, " + if (woechentlich) "weekly" else "monthly"
    override fun agInfoRest(standard: String) = "• Other entries: $standard"
    override val agBlasenTitel = "Bubble signs on red"
    override fun agBlasenZahl(rot: Int, gesamt: Int) = "$rot of $gesamt"
    override fun agBlasenStand(schlaegtAn: Boolean) = if (schlaegtAn) "triggered" else "not triggered"
    override fun agZustand(schluessel: String, positiv: Boolean) = when (schluessel) {
        "zins" -> if (positiv) "Fed cuts expected" else "Fed hikes expected"
        "fed" -> if (positiv) "Fed cut likely" else "Fed hike likely"
        "fed_jahr" -> if (positiv) "Several Fed cuts expected" else "No Fed cut expected"
        "ezb" -> if (positiv) "ECB cut likely" else "ECB hike likely"
        "rezession" -> if (positiv) "US recession unlikely" else "US recession likely"
        "nyfed" -> if (positiv) "Yield curve without recession warning" else "Yield curve warns of US recession"
        "erstantraege" -> if (positiv) "Jobless claims low" else "Jobless claims up"
        "fluggaeste" -> if (positiv) "Air travel above last year" else "Air travel below last year"
        "lieferketten" -> if (positiv) "Supply chains relaxed" else "Supply chains strained"
        "fracht" -> if (positiv) "Freight above last year" else "Freight below last year"
        "bahn", "aar" -> if (positiv) "Rail freight above last year" else "Rail freight below last year"
        "halbleiter", "wsts" -> if (positiv) "Chip sales above last year" else "Chip sales below last year"
        "wci" -> if (positiv) "Container rates rising" else "Container rates falling"
        else -> ""
    }
    override val agStaerkeInfo = "Strength in four steps: lead of the hit rate over the base rate in the app's look back. Outline instead of fill: no look back available, the step is set."
    override fun agRichtung(urteil: Int) = listOf("Leaning up", "Leaning sideways", "Leaning down", "Mixed", "Too little data").getOrElse(urteil) { "" }
    override fun agZeile(monate: Boolean) = if (monate) "Next months" else "Next weeks"
    override fun agStaerke(staerke: Int) = "Strength " + listOf("weak", "moderate", "clear").getOrElse(staerke) { "" }
    override fun agMarkt(markt: Int) = listOf("Stocks", "Oil", "Gas", "Bonds", "Gold", "Grain").getOrElse(markt) { "" }
    override fun agWort(urteil: Int) = listOf("up", "sideways", "down", "mixed", "open").getOrElse(urteil) { "" }
    override val agWochen = "Weeks"
    override val agMonate = "Months"
    override val agWeitere = "Other markets"
    override val agZuWenig = "No entry: too few entries"
    override val agWaageTitel = "Direction for stocks"
    override fun agPositiv(n: Int) = "$n% positive"
    override fun agNeutral(n: Int) = "$n% neutral"
    override fun agNegativ(n: Int) = "$n% negative"
    override val agDafuer = "Strongest reasons for"
    override val agDagegen = "Strongest reasons against"
    override val agHinweis = "Not investment advice."
    override val agEinzelTitel = "Individual indicators"
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
    override fun meErreicht(schwellen: String) = "Already reached: $schwellen"
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
    override val nwErwaehnt = "Mentioned stocks"
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
    override fun ipoReiter(reiter: Int) = listOf("Overview", "Upcoming", "Filed", "Newly listed", "Withdrawn", "Well known", "Lock-ups").getOrElse(reiter) { "" }
    override fun ipoStatusTag(status: Int, datum: String) = listOf("Expected", "Filed", "Started", "Withdrawn").getOrElse(status) { "" } + " " + datum
    override val ipoReiterLeer = "There are no entries here right now."
    override val ipoEckdaten = "Key facts"
    override val ipoPreis = "Price per share"
    override val ipoAktien = "Shares offered"
    override val ipoVolumen = "Offer amount"
    override val ipoEinschaetzung = "Assessment"
    override val ipoRitter = "Studies by Jay Ritter (University of Florida) show that IPOs on average did worse than comparable listed stocks over three to five years."
    override val ipoBrancheTitel = "Sector and outlook"
    override fun ipoBranche(b: Int) = listOf("Artificial intelligence", "Software and tech", "Biotech and pharma", "Bank", "Energy", "Consumer", "SPAC").getOrElse(b) { "" }
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
    override val ipoKeineEckdaten = "Price and size are not set yet."
    override val ipoEdgarSuche = "Search prospectuses at the SEC"
    override fun ipoKatalogGeplant(stand: String) = "Planned, no US prospectus yet (as of $stand)"
    override fun bkBankSatz(kbv: String, roe: String, angemessen: String?) = if (angemessen != null) "Price to book $kbv with $roe return on equity. About $angemessen would be fair." else "Price to book $kbv. A $roe return on equity does not cover the cost of capital."
    override fun bkBankUnter(kapitalkosten: String, wachstum: String) = "Based on $kapitalkosten cost of capital and $wachstum growth"
    override fun bkBankBilanzSatz(roa: String?, ekQuote: String?) = listOfNotNull(roa?.let { "Return on assets $it" }, ekQuote?.let { "equity ratio $it" }).joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    override val bkBankBilanzUnter = "Good from 1% and 8%, weak below 0.5% and 5%"
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
        "REL_VOLUMEN" -> "Unusually high volume"
        "STETIG" -> "Steady growth in an uptrend"
        "HALTEN" -> "Growth at a reasonable price"
        "DIVIDENDENWACHSTUM" -> "Undervalued dividend growth"
        "NIEDRIGES_KGV" -> "Low P/E"
        "CANSLIM" -> "CANSLIM (O'Neil)"
        "NEBENWERTE_WACHSTUM" -> "Small caps with growth"
        "LUECKE_AUF" -> "Gap up"
        "LUECKE_AB" -> "Gap down"
        "NETTOKASSE" -> "Net cash"
        "RUECKKAEUFE" -> "Share buybacks"
        "FCF_RENDITE" -> "High free cash flow yield"
        "KGV_ROE" -> "Low P/E, high return on equity"
        "SOLIDE_DIVIDENDE" -> "Solid dividend"
        "RSI_UEBERKAUFT" -> "Overbought"
        "RSI_UEBERVERKAUFT" -> "Oversold"
        "MEISTGESHORTET" -> "Most shorted"
        "GRAHAM" -> "Graham defensive"
        "NETNETS" -> "Net-nets (Graham)"
        "ZAUBERFORMEL" -> "Magic formula (Greenblatt)"
        "GALLEA" -> "Insiders and contrarian (Gallea)"
        "NEFF" -> "Low P/E with dividend (Neff)"
        "FISHER" -> "Low P/S with growth (Fisher)"
        "DREMAN" -> "Contrarian (Dreman)"
        "OSHAUGHNESSY" -> "Low P/S (O'Shaughnessy)"
        "DIVIDENDEN_HUNDE" -> "Dividend dogs"
        "RUECKSETZER" -> "Pullback in an uptrend"
        "NAH_AM_HOCH" -> "Near the 52-week high"
        "STAERKSTE_12_MONATE" -> "Strongest 12 months"
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
        "HALTEN" -> "Small stocks with expected growth, a high return on equity, good liquidity, PEG up to 1 and beta up to 1.5, lowest expected P/E first."
        "DIVIDENDENWACHSTUM" -> "Large dividend payers with a moderate valuation, growth and a payout below half of earnings."
        "NIEDRIGES_KGV" -> "Small stocks below book value with a low P/E and positive returns."
        "CANSLIM" -> "Filters after William O\'Neil\'s CANSLIM rules, as far as the data allows."
        "NEBENWERTE_WACHSTUM" -> "Small caps between 300m and 2bn with earnings and sales growth above 20%."
        "LUECKE_AUF" -> "Stocks worth at least 300m that opened today at least 3% above the previous close, largest gap first. Before the open the list still shows the gaps of the last trading day."
        "LUECKE_AB" -> "Stocks worth at least 300m that opened today at least 3% below the previous close, largest gap first. Before the open the list still shows the gaps of the last trading day."
        "NETTOKASSE" -> "Stocks worth at least 300m whose cash exceeds debt by at least 10% of market cap."
        "RUECKKAEUFE" -> "Stocks worth at least 1bn that bought back shares worth at least 2% of market cap in the last twelve months."
        "FCF_RENDITE" -> "Stocks worth at least 1bn whose free cash flow is at least 5% of market cap."
        "KGV_ROE" -> "Stocks worth at least 300m with a P/E up to 15 and a return on equity of at least 15%, lowest P/E first."
        "SOLIDE_DIVIDENDE" -> "Large stocks with a dividend yield of at least 3%, a payout up to 60% of earnings and debt to equity up to 2."
        "RSI_UEBERKAUFT" -> "Liquid stocks with an RSI of 70 or more, highest RSI first."
        "RSI_UEBERVERKAUFT" -> "Liquid stocks with an RSI of 30 or less, lowest RSI first."
        "MEISTGESHORTET" -> "Liquid US stocks with a short interest of at least 10% of the free float, highest first."
        "GRAHAM" -> "Companies with sales of at least 1bn, cheap on three-year average earnings and on book value: P/E up to 15, P/B up to 1.5, both multiplied at most 22.5. Smallest product first."
        "NETNETS" -> "Stocks whose current assets minus all liabilities exceed the market cap, largest surplus first."
        "ZAUBERFORMEL" -> "Rank by earnings yield (EBIT over enterprise value) plus rank by return on capital (EBIT over net working capital and fixed assets), smallest rank sum first. Without banks, insurers and utilities."
        "GALLEA" -> "Stocks worth at least 150m with insider buying, priced at least 50% below the 52-week high and cheap on at least two of four ratios."
        "NEFF" -> "P/E in the lowest 40% of the selected market, expected earnings growth of at least 7% per year and a dividend yield of at least 2%, lowest P/E first."
        "FISHER" -> "Market cap at most 1.5 times sales, net margin of at least 5%, expected earnings growth of at least 15% per year and positive free cash flow, lowest P/S first."
        "DREMAN" -> "P/E from 8 to 14, with P/B and price to cash flow in the lowest 40% of the selected market."
        "OSHAUGHNESSY" -> "Stocks worth at least 1bn whose P/S is in the lowest 30% of the selected market, lowest P/S first."
        "DIVIDENDEN_HUNDE" -> "The ten highest dividend yields of the selected market, of those the five stocks with the lowest price. Meant for an index such as the Dow Jones or the DAX."
        "RUECKSETZER" -> "Liquid stocks above the 200-day line, the 50-day above the 200-day line, RSI of 35 or less, lowest RSI first."
        "NAH_AM_HOCH" -> "Liquid stocks from 300m market cap, at most 5% below the 52-week high, highest one-year return first."
        "STAERKSTE_12_MONATE" -> "Liquid stocks from 300m market cap whose one-year return is in the top tenth of the selected market, highest first."
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
        "GRAHAM" -> "P/E from market cap and average net income of the last three fiscal years. Without banks and insurers, the finder loads no annual reports for them."
        "NETNETS" -> "Without banks and insurers, the finder loads no annual reports for them."
        "ZAUBERFORMEL" -> "Net working capital as reported, without deducting excess cash. If the report lists no debt, it counts as zero."
        "GALLEA" -> "Insider trend of the last 180 days from the filings on the device (USA, Germany, further European countries). Without the selling rules of the screen. P/S and free cash flow are missing for banks and insurers."
        "NEFF" -> "The P/E limit and growth of at least 7% are choices of the app."
        "FISHER" -> "Growth as expected earnings growth over five years. Without the stricter P/S limit for cyclicals, without banks and insurers."
        "DREMAN" -> "Without banks and insurers, the finder loads no annual reports for them."
        "OSHAUGHNESSY" -> "Without banks and insurers, the finder loads no annual reports for them."
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
        "UMSATZ_QUARTAL" -> "Revenue quarter"
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
        "WAAGE" -> "Net score (loaded stocks)"
        "NETTOKASSE" -> "Net cash"
        "FCF_RENDITE" -> "Free cash flow yield"
        "RUECKKAUF_RENDITE" -> "Buyback yield"
        "RUECKKAUF_BETRAG" -> "Buyback"
        "INSIDER" -> "Insider trend, 180 days"
        "UMSATZ" -> "Sales"
        "KGV_SCHNITT3" -> "P/E on 3-year earnings"
        "GRAHAM_PRODUKT" -> "P/E times P/B"
        "NETTO_UMLAUF" -> "Net current assets to market cap"
        "GEWINNRENDITE" -> "Earnings yield"
        "KAPITALRENDITE" -> "Return on capital"
        "KUV" -> "P/S"
        "KCF" -> "Price to cash flow"
        "KFCF" -> "Price to free cash flow"
        "MARGE" -> "Net margin"
        else -> id
    }
    override val afVorlageKeine = "No template"
    override val afVorlageNurUs = "US stocks only, short interest as a share of the float is freely available only there."
    override val afVorlageHinweis = "Price and market cap in euros using the numbers of the dollar limits."
    override fun afVorlageStand(offen: Int) = if (offen == 1) "1 stock still waits for its profile or daily prices." else "${de.troetenrepublik.app.ui.Fmt.num(offen.toDouble(), 0)} stocks still wait for their profile or daily prices."
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
        "NACHKAUF" -> "Buying more after a loss lowers the cost basis but raises the weight of a stock that is falling. What matters is whether the reason to buy still holds."
        else -> ""
    }
    override val apUeberschrift = "Investor profile"
    override val apZuWenig = "Holding period, hit rate and gains against losses need at least five completed sales."
    override val apHinweis = "Calculated from the purchases and sales in the app, first in first out per position. Savings plans and transferred positions do not count toward frequency."
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
    override fun anHaelt(anteil: String, wert: String, stichtag: String, seit: String?) = "Holds $anteil itself" + (if (wert.isNotBlank()) " ($wert)" else "") + " as reported for $stichtag" + (seit?.let { ", price since $it" } ?: "")
    override val anHalterHinweis = "Holdings from the mandatory filings of institutions (Form 13F in the US), only the ten largest holders are known. Institutions do not report their cost, so whether they are at a loss stays open. The price change since the reporting date only shows how the reported holding has developed since."
    override fun anHalterKeiner(n: Int) = "None of the firms is among the $n largest reported holders. The source does not list smaller holdings."
    override val hrTitel = "House ranking"
    override val hrTrefferTitel = "Hit rate by house"
    override val hrVorsprung = "Lead"
    override val hrHausLabel = "House hit rate"
    override val hrKeine = "No ranking available"
    override fun hrUnter(n: Int, von: String, bis: String) = "$n houses · hit rate $von to $bis"
    override fun hrUrteile(n: String) = "$n ratings"
    override fun hrStand(stand: String, von: String, bis: String, aktien: Int, urteile: Int) = "As of $stand · ratings $von to $bis · $aktien stocks"
    override fun hrInfo(min: Int) = "• Hit: the stock beat its index 3, 6 or 12 months after an upgrade, or lagged it after a downgrade. The hit rate pools all elapsed periods. The line in the bar marks 50%." +
        "\n• Secured: the hit rate the house very likely reaches at least, given its number of ratings. Few ratings lower it." +
        "\n• Lead: average excess return over the index in the direction of the rating." +
        "\n• Upgrades and downgrades of stocks in the S&P 500, Nasdaq 100, DAX and Euro Stoxx 50 are counted, only houses with at least $min ratings. Yahoo lists ratings almost only for US stocks."
    override fun hrSortierung(name: String) = when (name) { "GESICHERT" -> "By secured rate"; "VORSPRUNG" -> "By lead"; "URTEILE" -> "By ratings"; else -> "By hit rate" }
    override fun hrAnzahl(n: Int) = if (n == 1) "1 house" else "$n houses"
    override fun hrUrteileTreffer(urteile: String, treffer: String) = "$urteile · hit $treffer"
    override val finJahreTitel = "Balance sheet and cash flow over the years"
    override val finLangfristSchulden = "Long-term debt"
    override val finInvestitionen = "Capital expenditure"
    override fun umsatzSchaetzung(quartal: String) = if (quartal.isBlank()) "Expected revenue" else "Expected revenue $quartal"
    override val qzTitel = "Earnings"
    override val qzBekannte = "Well known"
    override val qzAlle = "All"
    override val qzFehlt = "The earnings calendar is not reachable right now."
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
        "vix" -> "VIX volatility index"; "vix_struktur" -> "VIX against VIX3M"; "vvix" -> "VVIX, volatility of the VIX"; "move" -> "MOVE, US bond volatility"
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
        "breite200" -> "Fixed bounds: 20% of stocks give 0, 80% give 100"
        "hochtief" -> "Highs minus lows in percent of all stocks, fixed bounds: −30% gives 0, +30% gives 100"
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
        "margin" -> "Fixed bounds: −20% year on year give 0, +30% give 100"
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

    // 5.95 Lizenzen
    override val lizenzenTitel = "Open source licenses"
    override val lizenzenHinweis = "The app contains libraries from other projects under their own licenses. The license texts are shown here in the original English."
    override val lizenzenFehlt = "The license file is missing from this package. It is also in the source code as THIRD_PARTY_LICENSES.txt."
    // 5.95 Nasdaq
    override val nkNichtVerfuegbar = "The calendar is not available right now. The app will try again the next time you open it."
    override val nkIpoFehlt = "The IPO calendar is not available right now. This page lists the prospectuses filed with the SEC in recent days."
    override val nkQzHinweis = "Well known means: portfolio, watchlist, stocks from the app's directory and companies with a market cap of 10 billion dollars or expected quarterly revenue of 1 billion dollars, the eight largest per time slot. Dates can move, earnings per share are in dollars and mostly exclude one-off items."
    override val nkDivTitel = "Dividend dates"
    override fun nkZahltag(datum: String) = "Payment date $datum"
    override fun nkJeJahr(wert: String) = "$wert per year"
    override val nkDivKeine = "No dates"
    override fun nkDivKeineBekannten(n: Int) = "No well known stock, $n more under “All”"
    override val nkDivHinweis = "Well known means: portfolio, watchlist and stocks from the app's directory.\n\n• The calendar covers stocks on US exchanges including preferred shares and funds, amounts in dollars per share.\n• “Per year” is the sum of one year's payments according to the source.\n• It is sorted by ex-date, the payment date is in the line.\n• For splits, the day is when the stock starts trading at the new ratio."
    override val nkSplits = "Stock splits"
    override val nkSplit = "Split"
    override val nkZusammenlegung = "Reverse split"
    override fun nkSplitText(neu: Int, alt: Int) = (if (alt == 1) "1 share" else "$alt shares") + (if (neu == 1) " becomes " else " become ") + neu
    override val nkWiTitel = "Economic calendar"
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
    override val q5VxMehr = "A futures contract fixes today the price at which the VIX is settled later. M1 expires on the next expiry date, M2 in the following month.\n• Contango: M2 more expensive than M1, the normal state\n• Backwardation: M1 above M2, investors look for immediate protection, usually during a sudden slump\n\nThe curve compares M1 with M2. A ratio above 1 means backwardation, from 0.95 the curve counts as flat, below that as contango."
    override val q5IgPrivat = "Investegate is intended for private investors only. Confirm that you are a private investor and will use the announcements only personally. Only then does the app fetch them."
    override val q5IgJa = "I am a private investor"
    override val q5IgNein = "No"
    override val q5IgKeine = "At the moment Investegate lists no director dealings for this stock."
    override val q5IgSeite = "All announcements of the company on Investegate"
    override fun q5IgQuelleMeldung(quelle: String) = "Source: Investegate, announcement via $quelle, reproduced unchanged."
    override val q5IgOeffnen = "Open the announcement on Investegate"
    override val q5IgAlleTitel = "Director dealings in the United Kingdom"
    override val q5IgAlleUnter = "New announcements about own share dealings by directors of British companies, via Investegate"
    override val q5IgKategorie = "All director dealings on Investegate"
    override val q5NnKeine = "No managers transactions at Nasdaq Nordic in the past year."
    override val q5MnbKeine = "No managers' transactions at the MNB in the past year."
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
    override val srHinweis = "A TR-1 notification shows when a holder crosses a voting rights threshold.\n\n• For UK companies, from 3% every further whole percentage threshold up and down counts.\n• For companies from other countries the thresholds are 5, 10, 15, 20, 25, 30, 50 and 75%.\n• The holding can consist of shares, of financial instruments such as options, or of both.\n• The app derives the threshold from the holding before and after."

    // 5.96 Labor
    override val slRegelWaehlen = "Choose the rule shown in detail"
    override val slKeineLinie = "All lines are hidden."
    override fun slLinieSchalter(regel: String) = "Show line $regel in the chart"

    // 5.96 Daten
    override fun hlUnter(p: String) = "below $p"
    override val hlUnveraendert = "unchanged"
    override fun hlMitNamen(n: Int, gesamt: Int) = "$n of $gesamt reported by name"
    override val hlGemeldet = "Reported by name"
    override val hlKeineNamen = "The source names no institutions."
    override val hlFonds = "Largest funds"
    override val hlFondsHinweis = "Funds are listed separately. Their stakes can already be part of the institutions above."
    // 0.77 13F overview
    override fun g13Kopf(quartal: String) = "All 13F filers · $quartal"
    override val g13Halter = "13F holders"
    override fun g13Netto(kaeufer: Boolean) = if (kaeufer) "Net buyers" else "Net sellers"
    override val g13Stueck = "Shares since last quarter"
    override val g13Bestand = "Holdings"
    override val g13Groesste = "Largest 13F holders"
    override fun g13KernHalter(n: String) = "$n 13F holders"
    override fun g13KernNetto(n: String, kaeufer: Boolean) = if (kaeufer) "$n net buyers" else "$n net sellers"
    override fun g13Stand(melder: String, bis: String) = "13F: $melder filers, filings until $bis"
    override val g13Hinweis = "13F rows: net buyers are holders who increased plus new holders, minus holders who reduced or exited. Only filers that reported in both quarters are compared."
    override val g13Titel = "All funds this quarter"
    override fun g13KernMelder(n: String, quartal: String) = "$n filers · $quartal"
    override val g13Gekauft = "Most bought"
    override val g13Verkauft = "Most sold"
    override fun g13Weitere(n: Int) = "$n more"
    override val g13Weniger = "Fewer"
    override fun g13Unten(zufluss: Boolean, betrag: String, halter: String) = (if (zufluss) "Inflow " else "Outflow ") + "$betrag · $halter holders"
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
    override fun vgMax(n: Int) = "Up to $n comparisons at once."
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
    override val vgGleicheRaten = "Each comparison gets the same installments over the same period. For portfolio histories the first value of the month counts as the purchase price."
    override val vgLaborHinweis = "A rule from the strategy test trades this stock on its signals, with the spread and a €1 fee per order as in the strategy test."
    override fun vgOhneDaten(namen: String) = "No data: $namen."
    override fun vgWenigGeschaefte(namen: String) = "Too few trades for a history: $namen."
    override val vgVergleichIntro = "All lines start at zero at the beginning of the common period."

    // 5.96 Feed
    override val feedErstellen = "Create your own feed"
    override val feedBearbeiten = "Edit feed"
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
    override val feedMeldeQuellen = "Sources for notifications"
    override val feedOhneFeeds = "No own feed yet."
    override val feedBuendel = "More than three new headlines at once arrive as one notification."

    // 5.96 Kongress
    override val copyFormer = "Former members"
    override val copyMitgliederNote = "Members checked once a day. Former members stay here until 180 days after leaving and after their last filing, then through search."
    override fun kammerZeitraum(senat: Boolean, von: Int, bis: Int) = (if (senat) "former senator, " else "former member of the House, ") + amtZeitraum(von, bis)
    override fun amtZeitraum(von: Int, bis: Int) = if (von == bis) "$von" else "$von to $bis"

    // 5.97 Aufklappen
    override val aufZahlenHerkunft = "Where the numbers come from"

    // 5.97 Finanzen
    override val finDivTitel = "Dividend"
    override val finDivJeAktie = "Per share in 12 months"
    override val finDivExTag = "Next ex-date"
    override val finDivZahltag = "Next payment"
    override val finDivBestand = "Your holding per year, before tax"
    override val finVerlust = "Loss"
    override fun finWechsel(art: String) = if (art == "VERLUST_ZU_GEWINN") "From loss to profit" else "From profit to loss"
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
    override val qzbUhrText = "The exact moment can differ."
    override val qzbZahlen = "What gets reported"
    override val qzbGewinn = "Earnings per share"
    override val qzbUmsatz = "Revenue"
    override fun qzbErwartetVon(quelle: Int, analysten: Int?) = when (quelle) {
        0 -> "Calendar" + (analysten?.let { ", $it estimates" } ?: "")
        else -> "Yahoo Finance" + (analysten?.let { ", $it analysts" } ?: "")
    }
    override val qzbErwartet = "expected"
    override val qzbSpanne = "Range"
    override fun qzbVonBis(von: String, bis: String) = "$von to $bis"
    override val qzbVorjahr = "Same quarter last year"
    override val qzbGemeldet = "reported"
    override val qzbAusblick = "Outlook"
    override val qzbAusblickText = "Many companies also give an expectation for the next quarter or the full year with their results. The app's sources have no figures for it. The outlook is in the company's release."
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
    override val qzbKeineBewertung = "The sources lack estimates or prior year figures for a comparison."
    override val qzbVerlauf = "Recent quarters"
    override fun qzbQuartalBis(datum: String) = "Quarter to $datum"
    override val qzbVonWem = "Where the estimates come from"
    override fun qzbKalenderZeile(schaetzungen: Int?) = "Earnings calendar" + (schaetzungen?.let { ": consensus of $it estimates" } ?: "")
    override fun qzbAnalystenZeile(gewinn: Int?, umsatz: Int?) = "Analysts" + listOfNotNull(
        gewinn?.let { "$it on earnings" }, umsatz?.let { "$it on revenue" },
    ).joinToString(" and ").let { if (it.isEmpty()) "" else ": $it" } + ", plus recent quarters"
    override fun qzbHaeuser(namen: String) = "Firms that rated the stock in the last 90 days: $namen"
    override val qzbVonWemText = "The services count different numbers of analysts, so their figures differ slightly."
    override val qzbYahooFehlt = "Yahoo Finance is not providing further estimates right now."
    // 0.68 Finanzen
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
        finAbweichung -> "Reported against expected, in percent.\n\n• Triangle up means beaten, down means missed.\n• Up to 2% without color."
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
        "RHYTHMUS" -> "16-week rhythm"
        "MOMENTUM" -> "12-month momentum"
        "RSI_KURZ" -> "Short RSI in trend"
        "MONATSWECHSEL" -> "Turn of the month"
        "ZIELVOLA" -> "Target volatility $a%"
        else -> art
    }

    // 0.68 Suchverlauf
    override val svKuerzlich = "Recently searched"
    override val svLoeschen = "Clear history"
    override val svVerlauf = "History"
    override val svAus = "Turn history off"
    override val svAusEinstellung = "Save search history"
    override val kgvVerlust = "Loss expected"
    override fun wdReicht(jahre: String) = "Money lasts $jahre"
    override fun wdReichtFolgen(n: Int, von: Int) = "in $n of $von start years"
    override fun wdReichtVerlaeufe(pct: String) = "in $pct of scenarios"
    override fun svEntfernen(name: String) = "Remove $name from history"

    // 0.68 Tagesrueckblick und Mitteilungen
    override val rbUhrzeit = "Time"
    override fun rbEinleitungDepot(ton: String, hoch: Boolean, prozent: String) = "$ton: your portfolio " + (if (hoch) "rose" else "fell") + " $prozent"
    override val rbVeraenderung = "Change"
    override val rbWert = "Value"
    override val mtAlle = "All notifications"
    override fun mtProfil(an: Boolean, aktiv: Int, gesamt: Int) = if (!an) "Off" else "$aktiv of $gesamt types on"
    override val mtAlleAus = "All notifications are off."
    override val mtDepot = "Portfolio and orders"
    override val mtDepotHinweis = "Orders, savings plans, knock-outs, bets, dividends, interest."
    override val mtAlarme = "Prices and price alerts"
    override val mtAlarmeHinweis = "Price alerts, finder hits, pricier funds, investment theses."
    override val mtCopy = "Insiders, politics and agencies"

    // 0.68 ipo
    override fun ipoSchritt(i: Int) = listOf("Filed", "Priced", "Trading").getOrElse(i) { "" }
    override val ipoSchrittZurueck = "Withdrawn"
    override val ipoPreisSpanne = "Price range"
    override val ipoSpanneMitte = "Midpoint"
    override val ipoSeitAusgabeTitel = "Since issue"
    override fun ipoKursJetzt(p: String) = "Price $p"
    override fun ipoAusgabe(p: String) = "Issue $p"
    override fun ipoPunktKurz(merkmal: Int) = if (merkmal == 0) "SPAC" else "Small offering"
    override fun ipoPunktErklaerung(merkmal: Int) = if (merkmal == 0) "It raises money to buy a company that is not yet known. Many SPAC shares fell sharply after the merger."
        else "Such stocks get little attention and often swing a lot."
    override val ipoSchlagzeilen = "Headlines"
    override fun ipoKopfLabel(i: Int) = listOf("Upcoming", "Filed", "Started").getOrElse(i) { "" }
    override val ipoKopfTage = "30 days"
    override val ipoGrosse = "Biggest IPOs"
    override val ipoBericht = "Report"
    override fun ipoLautProspekt(betrag: String) = "per prospectus $betrag"
    override val ipoLautProspektKurz = "per prospectus"
    override val ipoVolumenProspekt = "Size per prospectus"
    override fun ipoWeitere(n: Int) = "$n more"
    override fun ipoDavonSpac(n: Int) = "$n of them SPACs"
    override fun alertArtText(tag: String, extra: String, price: String) = when (tag) {
        "ALERT_SMA_UP" -> "Price above the 50-day average $extra · now $price"
        "ALERT_SMA_DOWN" -> "Price below the 50-day average $extra · now $price"
        "ALERT_VOLUME" -> "Volume at $extra of normal · price $price"
        "ALERT_EXDIV" -> "Last day to buy $extra · price $price"
        else -> "Insider buy of $extra · price $price"
    }
    override val zrTitel = "Move around earnings"
    override fun zrErwartet(straddle: Boolean) = if (straddle) "Priced in (straddle)" else "Priced in (from IV)"
    override val zrKursNach = "Price after results"
    override fun zrUeblichZeile(pct: String) = "typical $pct"
    override val zrUeblichBand = "typical"
    override val zrEingepreistBand = "priced in"
    override val zrInfo = "• Typical: median price move (absolute) after the last earnings dates\n• Move: close before the results against close on the first trading day after"
    override val zrEpsReihe = "Earnings per share"
    override val zrEpsUeberraschung = "Earnings against expected"
    override val zrUmsatzUeberraschung = "Revenue against expected"
    override fun zrEpsStatt(ist: String, schaetzung: String) = "$ist, expected $schaetzung"
    override fun zrSaeule(ist: String, schaetzung: String) = "$ist, expected $schaetzung"
    override val finDivJahreErhoeht = "Years of increases in a row"
    override val finDivKauftag = "Last day to buy"
    override val finDiv5J = "5-year growth"
    override fun nkErhoeht(pct: String) = "Raised by $pct"

    // 0.76 q78a
    override val sigTitel = "Markers"
    override val sigKeine = "No markers"
    override val sgInsider = "Insiders, 90 days"
    override val sgKongress = "Congress, 90 days"
    override val sgStufungen = "Upgrades and downgrades, 30 days"
    override val sgStufungenKurz = "Analysts, 30 days"
    override val sgTrends = "Reddit, 7 days"
    override fun sgTrendWert(rang: Int, aenderung: String?) = "Rank $rang" + (aenderung?.let { " · $it" } ?: "")
    override val sgOptionen = "Unusual options"
    override fun sgInsiderWert(kaeufer: Int, verkaeufer: Int) = (if (kaeufer == 1) "1 buyer" else "$kaeufer buyers") + " · " + (if (verkaeufer == 1) "1 seller" else "$verkaeufer sellers")
    override fun sgKaeufeVerkaeufe(kaeufe: Int, verkaeufe: Int) = (if (kaeufe == 1) "1 purchase" else "$kaeufe purchases") + " · " + (if (verkaeufe == 1) "1 sale" else "$verkaeufe sales")
    override fun sgHochAb(hoch: Int, ab: Int) = (if (hoch == 1) "1 upgrade" else "$hoch upgrades") + " · " + (if (ab == 1) "1 downgrade" else "$ab downgrades")
    override fun sgHochAbKurz(hoch: Int, ab: Int) = "$hoch up · $ab down"
    override fun sgOptionenWert(anzahl: Int, calls: Int, puts: Int) = "$anzahl · " + (if (calls == 1) "1 call" else "$calls calls") + " · " + (if (puts == 1) "1 put" else "$puts puts")
    override fun sgNetto(betrag: String) = "Net $betrag"
    override fun sgPraemie(betrag: String) = "Premium $betrag"
    override val mvMedian = "Days to filing"
    override val mvSpaet = "Late, over 45 days"
    override val mvGeschaefte90 = "Trades, 90 days"
    override val mvVolumen90 = "Volume, 90 days"
    override fun mvTage(n: Int) = if (n == 1) "1 day" else "$n days"
    override fun mvUnter(spaet: String, geschaefte: Int, volumen: String) = "$spaet late · ${if (geschaefte == 1) "1 trade" else "$geschaefte trades"} in 90 days · $volumen"
    override val flTitel = "Congress by sector"
    override val flNav = "Purchases and sales of the last 90 days, also by chamber and family"
    override val flSektoren = "By sector"
    override val flKammern = "By chamber"
    override val flEigeneFamilie = "Own and family"
    override val flHaus = "House of Representatives"
    override val flSenat = "Senate"
    override val flEigene = "Own"
    override val flFamilie = "Spouse, children, joint"
    override fun flKaeufe(n: Int) = (if (n == 1) "1 purchase" else "$n purchases")
    override fun flVerkaeufe(n: Int) = (if (n == 1) "1 sale" else "$n sales")
    override fun flOhneSektor(n: Int) = if (n == 1) "1 trade without sector" else "$n trades without sector"
    override fun usSeitKauf(p: String) = "since trade $p"
    override fun usVomMarktwert(p: String) = "$p of market cap"
    override val usPlanLabel = "Planned sales, share of sales"
    override fun poStunden(h: Int) = "+$h h"
    override val vbKomponenten = "Components"
    override val vbKursziele = "Price targets"
    override val vbTief = "Low"
    override val vbMedian = "Median"
    override val vbHoch = "High"
    override val vbKurs = "Price"
    override fun vbStufen(tage: Int, hoch: Int, ab: Int) = "Last $tage days: $hoch upgrades, $ab downgrades"
    override fun vbAbweichung(wert: String) = "Versus forecast $wert"
    override fun vbKursMedian(prozent: String, richtung: Int) = if (richtung == 0) "Price at median" else "Price $prozent " + (if (richtung < 0) "below" else "above") + " median"
    override fun vbImPlus(n: Int) = "$n up"
    override fun vbImMinus(n: Int) = "$n down"
    override val vbPaare = "Pairs"
    override val ifNurFuehrung = "Leadership only"
    override val kgvSchnitt = "P/E (avg. earnings)"
    override fun kgvSchnittHinweis(jahr: Int, prozent: String, jahre: Int, ueber: Boolean) =
        "Earnings in $jahr are $prozent ${if (ueber) "above" else "below"} the $jahre-year average, so the P/E looks ${if (ueber) "low" else "high"}."
    // Market power, industries, suggested portfolio (0.77)
    override val mmTitel = "Moat"
    override fun mmStufe(s: String) = when (s) { "HOCH" -> "high"; "MITTEL" -> "medium"; else -> "low" }
    override val mmErklaerung = "How hard it is for competitors to attack the company's position: market share, pricing power, consistently high returns."
    override fun mmGrund(k: String, gut: Boolean, wert: String, vergleich: String, jahre: String): String {
        val zusatz = if (jahre.isBlank()) "" else " · $jahre"
        return when (k) {
            "STELLUNG" -> if (vergleich == "1") "Largest in its industry · $wert of industry sales" else if (gut) "Rank $vergleich in its industry · $wert of industry sales" else "Small share of industry sales: $wert, rank $vergleich in its industry"
            "PREIS_BRUTTO" -> if (gut) "Gross margin $wert (industry $vergleich)$zusatz" else "Gross margin $wert below the industry ($vergleich)$zusatz"
            "PREIS_BETRIEB" -> if (gut) "Operating margin $wert (industry $vergleich)$zusatz" else "Operating margin $wert below the industry ($vergleich)$zusatz"
            else -> if (gut) "Return on capital never below $wert in $vergleich years" else "Return on capital or margin swings, lowest return $wert"
        }
    }
    override fun mmJahre(ueber: Int, alle: Int) = "$ueber of $alle years above"
    override fun mmKurz(stufe: String) = "Moat $stufe"
    override val afAnsichtBranchen = "Industries"
    override val afAnsichtDepot = "Suggested portfolio"
    override fun fbrZahl(n: Int) = if (n == 1) "1 industry" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} industries"
    override val fbrSammel = "Other industries"
    override fun fbrWerte(n: Int) = if (n == 1) "1 stock" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} stocks"
    override val fbrAlle = "All industries"
    override val vsdTitel = "Suggested portfolio"
    override fun vsdWerte(n: Int) = if (n == 1) "1 stock" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} stocks"
    override fun vsdSektoren(n: Int) = if (n == 1) "1 sector" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} sectors"
    override fun vsdLaender(n: Int) = if (n == 1) "1 country" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} countries"
    override fun vsdAuswahl(punkte: Int, bereich: Int, jeBranche: Int, jeSektor: Int, jeLand: Int, deckel: Int) = listOf(
        "At least $punkte points, moat at least medium", "Net score not negative", "Without a net score no area under $bereich points",
        "At most $jeBranche per industry, $jeSektor per sector, $jeLand per country", "Weighted by score, at most $deckel% per stock",
    )
    override val vsdAuswahlKopf = "Selection"
    override val vsdPositionen = "Positions"
    override val vsdGewicht = "Weight"
    override val vsdLeer = "No stock meets all conditions, or the annual statements are not loaded yet."
    override fun vsdSektor(key: String) = when (key) {
        "TECHNOLOGIE" -> "Technology"; "GESUNDHEIT" -> "Health care"; "FINANZEN" -> "Financials"; "INDUSTRIE" -> "Industrials"
        "KONSUM_ZYKLISCH" -> "Consumer cyclical"; "KONSUM_BASIS" -> "Consumer staples"; "ENERGIE" -> "Energy"; "ROHSTOFFE" -> "Materials"
        "VERSORGER" -> "Utilities"; "IMMOBILIEN" -> "Real estate"; else -> "Communication"
    }
    override val vsdSektorKopf = "By sector"
    override val vsdLandKopf = "By country"
    override val vsdNachbilden = "Replicate in the practice portfolio"

    // Open US registers: trials, vehicle and product recalls (0.77)
    override val rgStudienTitel = "Clinical trials (ClinicalTrials.gov)"
    override fun rgStudienLaufend(n: Int) = if (n == 1) "1 ongoing trial" else "$n ongoing trials"
    override fun rgPhase(n: Int) = "Phase $n"
    override val rgStudienBald = "Next completions"
    override fun rgStudienBaldKern(datum: String) = "next end $datum"
    override val rgStudienFertig = "Recently ended"
    override fun rgStudienFertigKern(fertig: Int, abgebrochen: Int) =
        listOfNotNull(if (fertig > 0) "$fertig completed" else null, if (abgebrochen > 0) "$abgebrochen terminated" else null).joinToString(" · ")
    override fun rgStudieEnde(phase: Int, datum: String) = (if (phase > 0) "Phase $phase · " else "") + "Ends $datum"
    override fun rgStudieBeendet(abgebrochen: Boolean, phase: Int, datum: String) =
        (if (phase > 0) "Phase $phase · " else "") + (if (abgebrochen) "Terminated " else "Completed ") + datum
    override val rgStudienInfo = "[skala] Phase 1 | Phase 2 | Phase 3 | Approval\n• Phase 1: safety\n• Phase 2: effect\n• Phase 3: effect in many patients, the last step before approval\n\nEach trial counts once at its highest phase, so phase 2/3 counts as 3.\n\nMatched by sponsor, trials of subsidiaries under another name can be missing."
    override val rgFahrzeugTitel = "Vehicle recalls (NHTSA)"
    override val rgRueckrufeTitel = "Recalls, 24 months"
    override fun rgRueckrufeKern(n: Int) = if (n == 1) "1 recall" else "$n recalls"
    override fun rgFahrzeuge(zahl: String) = "$zahl vehicles"
    override val rgUntersuchungenTitel = "Open investigations"
    override fun rgUntersuchungenKern(n: Int) = "$n open"
    override fun rgUntersuchungSeit(art: String, datum: String) = when (art) {
        "PE" -> "Preliminary evaluation"; "EA" -> "Engineering analysis"; "RQ" -> "Recall query"; "DP" -> "Defect petition"
        "AQ" -> "Audit query"; else -> art
    } + " · since $datum"
    override val rgFahrzeugInfo = "An investigation by the US traffic safety agency examines a suspected defect and can lead to a recall.\n\n• The vehicle count adds up all recalls, one vehicle can count more than once.\n• Investigations are matched by manufacturer or brand in the text, single ones can be missing."
    override val rgProdukteTitel = "Product recalls (CPSC)"
    override fun rgProdukteKern(n: Int) = if (n == 1) "1 recall, 24 months" else "$n recalls, 24 months"
    override val rgProdukteInfo = "The US Consumer Product Safety Commission (CPSC) publishes recalls of consumer products. Matched by manufacturer and title, retailers do not count."
    override val rgBeschwerdenTitel = "Consumer complaints (CFPB)"
    // 0.77 Paket AD5: Begriffe, seltene Kaeufe, Konvergenz, Insider-Simulator
    override val wdClusterMedianInfo = "Median: half of the clusters were above it, half below."
    override val wdPutErklaerung = "Selling a put earns a premium. If the price falls below the strike, you take the shares at the strike. Nominal counts only the premium, realized subtracts the loss at expiry."
    override val skTitel = "Rare buys"
    override fun skAussage(art: Int, monate: Int, beginn: Boolean, zahl: Int, faktor: String, jahr: Int) = when (art) {
        0 -> if (beginn && jahr > 0) "First week with $zahl buyers" else "$zahl buyers in one week" + if (monate >= 24) ", ${monate / 12}y gap" else ""
        1 -> "Largest buy, ${faktor}× the previous record"
        else -> if (beginn && jahr > 0) "First buy since $jahr" else if (monate >= 24) "First buy in ${monate / 12} years" else "First buy in $monate months"
    }
    override fun skKopf(bis: String, seit: String) = "Last 30 days to $bis, compared with the company's buys since $seit"
    override val konTitel = "Simultaneous buys"
    override val kvNav = "Where insiders, Congress and funds buy at once"
    override fun kvErklaerung(bis: String, quartal: String) = "Stocks where at least two of three groups buy: insiders and Congress in the 90 days to $bis, funds net in the quarter $quartal (13F)."
    override val kvDrei = "All three groups"
    override val kvZwei = "Two groups"
    override val kvInsider = "Insiders"
    override val kvKongress = "Congress"
    override val kvFonds = "Funds"
    override fun kvInsiderZahl(n: Int) = if (n == 1) "1 insider" else "$n insiders"
    override fun kvKongressZahl(n: Int) = if (n == 1) "1 member" else "$n members"
    override fun kvFondsZahl(n: String) = "$n net buyers"
    override fun skKern(n: Int) = if (n == 1) "1 stock" else "$n stocks"
    override val kvLeer = "No stock with two groups right now"
    override fun kvWert(insider: Boolean, kongress: Boolean, fonds: Boolean) =
        listOfNotNull("insiders".takeIf { insider }, "Congress".takeIf { kongress }, "funds".takeIf { fonds }).let { g -> g.dropLast(1).joinToString(", ") + (if (g.size > 1) " and " else "") + g.last() }.replaceFirstChar { it.uppercase() } + " buy at once"
    override fun wsMindest(n: Int) = "At least $n " + (if (n == 1) "insider" else "insiders")
    override fun wsFenster(tage: Int) = "Within $tage days"
    override fun wsBetrag(je: Int, dollar: String) = when (je) { 0 -> "Any amount"; 1 -> "Buy from $dollar"; else -> "Cluster from $dollar" }
    override fun wsRolle(nurFuehrung: Boolean) = if (nurFuehrung) "Leadership only" else "All roles"
    override fun wsHalten(tage: Int) = "Hold $tage trading days"
    override fun wsKosten(prozent: String) = if (prozent == "0") "No costs" else "$prozent% costs"
    override val wsStandard = "Default rule"
    override val wsKeine = "These rules leave no clusters to evaluate"
    override fun wsHinweis(seit: String, kosten: Boolean) = "Calculated from the filings since $seit" + if (kosten) ", costs on the stocks only" else ""
    override val guideKonvergenz = "Under “Track record” the insider look-back tests with your own rules whether cluster buys (several executives of one company buy their own shares within a few days) beat the S&P 500. Rare buys and the “Overlaps” list show where insiders, Congress and funds buy at once."

    // 0.77 Warning signs and takeovers from 8-K filings
    override val achtInfoWarn = "Filings by the company at the SEC (Form 8-K) from the last 24 months."
    override val achtInfoDeal = "Filings and merger documents by the company at the SEC from the last 24 months.\n\n• Counterparty and price appear only when the document states them clearly.\n• Price move: change from the previous close."
    override fun achtWarnTitel(n: Int) = if (n == 1) "1 warning sign in 24 months" else "$n warning signs in 24 months"
    override fun achtDealTitel(n: Int) = if (n == 1) "1 takeover in 24 months" else "$n takeovers in 24 months"
    override fun achtArt(key: String) = when (key) {
        "PRUEFER" -> "Change of auditor"
        "KORREKTUR" -> "Financial statements no longer reliable"
        "FUEHRUNG" -> "Change in leadership"
        "DELISTING" -> "Delisting notice or rule violation"
        "INSOLVENZ" -> "Bankruptcy"
        "ABSCHREIBUNG" -> "Material impairment"
        "VERBINDLICHKEIT" -> "Debt due early"
        "ANGEKUENDIGT" -> "Takeover announced"
        "ANGEBOT" -> "Tender offer"
        "ABGESCHLOSSEN" -> "Takeover completed"
        "ANGEDROHT" -> "Delisting threatened"
        "REGEL" -> "Exchange rule not met"
        "BESCHLOSSEN" -> "Delisting decided"
        "WECHSEL" -> "Exchange transfer"
        "ENTWARNUNG" -> "Compliant again"
        else -> key
    }
    override fun achtErkl(key: String) = when (key) {
        "PRUEFER" -> "The company changed the auditor of its accounts. Usually routine, a warning sign after a dispute or doubts."
        "KORREKTUR" -> "Earlier financial statements contained errors and may no longer be relied on. The company has to restate them."
        "FUEHRUNG" -> "The CEO or CFO is leaving. Sudden departures count as a warning sign."
        "DELISTING" -> "The exchange reports a breach of its rules or the end of the listing (delisting)."
        "INSOLVENZ" -> "The company has filed for bankruptcy or creditor protection."
        "ABSCHREIBUNG" -> "An important asset is worth less than assumed and is written down."
        "VERBINDLICHKEIT" -> "A debt falls due earlier than planned, for example after a breach of loan terms."
        "ANGEKUENDIGT" -> "The company signed a contract for a takeover or merger. Closing is still pending."
        "ANGEBOT" -> "A buyer offers shareholders to buy their shares directly at a fixed price."
        "ABGESCHLOSSEN" -> "The purchase or merger has closed."
        "ANGEDROHT" -> "The exchange threatens removal or sets a deadline to meet its rules again."
        "REGEL" -> "The company does not meet an exchange rule. The exchange has neither removed it nor named a deadline."
        "BESCHLOSSEN" -> "The exchange has decided to remove the stock."
        "WECHSEL" -> "The company voluntarily moves to another venue or market tier. Not a warning sign, not counted."
        "ENTWARNUNG" -> "The company meets the rule again. Not a warning sign, not counted."
        else -> ""
    }
    override fun achtRolle(rolle: String) = when (rolle) {
        "CEO" -> "CEO leaves"
        "CFO" -> "CFO leaves"
        "CEO_CFO" -> "CEO and CFO leave"
        else -> "Change in leadership"
    }
    override fun achtInsider(n: Int) = if (n == 1) "1 insider sold in the 60 days before" else "$n insiders sold in the 60 days before"
    override fun achtGegenseite(name: String) = "Counterparty: $name"
    override fun achtPreis(preis: String) = "$preis per share"
    override val achtKursTag = "Price on filing day"
    override val achtKurs5 = "after 5 trading days"
    override fun achtGrund(key: String, zusatz: String) = when (key) {
        "BERICHT" -> "Report not filed on time" + (if (zusatz.isEmpty()) "" else ": $zusatz")
        "KURS" -> "Price below $1 for 30 trading days"
        "EIGENKAPITAL" -> "Equity or market cap below the minimum"
        "AUSSCHUSS" -> "Too few independent members on audit committee or board"
        "VERSAMMLUNG" -> "Annual meeting not held"
        else -> ""
    }
    override fun achtBericht(form: String, zeitraum: String) = if (zeitraum.isEmpty()) form else "$form for $zeitraum"
    override fun achtFristDatum(datum: String) = "Deadline $datum"
    override fun achtFristTage(tage: Int) = "Deadline: $tage days"
    override fun achtFuehrungDetail(person: String, ereignis: String, sofort: Boolean, wirksam: String, nachfolge: Boolean?): String {
        val art = when (ereignis) { "RUECKTRITT" -> "resignation"; "ENTLASSUNG" -> "dismissal"; else -> "departure" }
        val teile = listOfNotNull(
            art, if (sofort) "effective immediately" else if (wirksam.isNotEmpty()) "effective $wirksam" else null,
            when (nachfolge) { true -> "successor named"; false -> "no successor named"; null -> null },
        )
        return (if (person.isEmpty()) "" else "$person: ") + teile.joinToString(", ")
    }
    override fun achtPrueferDetail(pruefer: String, neu: String, ereignis: String, streit: Boolean, maengel: Boolean): String {
        val kopf = when (ereignis) {
            "PRUEFER_RUECKTRITT" -> if (pruefer.isEmpty()) "Auditor resigned" else "$pruefer resigned as auditor"
            "PRUEFER_ENTLASSUNG" -> if (pruefer.isEmpty()) "Company dismissed the auditor" else "Company dismissed $pruefer as auditor"
            else -> if (pruefer.isEmpty()) "New auditor appointed" else "$pruefer appointed as new auditor"
        }
        val teile = listOfNotNull(
            kopf, if (neu.isNotEmpty()) "new auditor: $neu" else null,
            if (streit) "disagreements stated" else null, if (maengel) "control weaknesses stated" else null,
        )
        return teile.joinToString(", ")
    }
    override fun achtKorrekturDetail(von: Int, bis: Int) = if (von == bis) "Affects statements from $von" else "Affects statements from $von to $bis"
    override fun achtInsolvenzDetail(kapitel: Int) = when (kapitel) {
        11 -> "Filing under Chapter 11 (reorganization under creditor protection)"
        7 -> "Filing under Chapter 7 (liquidation)"
        else -> "Filing under Chapter $kapitel"
    }
    override fun achtAbschreibungDetail(betrag: String) = "Impairment of $betrag"
    override fun widgetZufluss(betrag: String) = "Inflow $betrag"
    override fun widgetGekauftStand(quartal: String) = "Quarter to $quartal"
    override val widgetGekauftLeer = "No 13F data"
    override val widgetTrefferanteil = "Hit rate"
    override fun widgetKonvergenzStand(bis: String) = "90 days to $bis"
    override fun widgetSeltenStand(bis: String) = "30 days to $bis"
    override val widgetSeltenLeer = "No rare buys"
    override fun widgetSimStand(n: String, jahr: String) = "$n clusters since $jahr"
    override val widgetSimLeer = "No backtest"
    override val widgetFinderUngeladen = "Stock finder not loaded"
    override val widgetVorschlagLeer = "No stock meets the rules"
    override val widgetBrancheLeer = "Too few stocks in the industry"
    override val widgetWarnTitel = "Warning signs"
    override val widgetWarnUnter = "Warning signs, 24 months"
    override val widgetWarnKeine = "No warning signs"
    override val widgetWarnOhneKennung = "Contact address under Access keys missing"
    override val widgetWarnNurUs = "US stocks only"
    override val widgetWarnFehler = "Filings not available"
    override val widgetPraesidialLeer = "No documents"
    override val widgetBrancheBeste = "Strongest industry"
    override fun widgetFonds(n: String) = "$n funds"
    override fun widgetPunkte(wert: String) = "$wert pts"
    override val widgetSimVorsprung = "Lead"
    override val widgetSimFaelle = "Cases"
    override fun widgetSimSeit(jahr: String) = "since $jahr"
    override val widgetSimSchlechtester = "Worst"

    override val orErwartung = "Outlook"
    override fun orErw(key: String) = when (key) {
        "STEIGT" -> "Rises"; "FAELLT" -> "Falls"; "SEITWAERTS" -> "Sideways"; "ABSICHERN" -> "Hedge"; else -> "Big move"
    }
    override val orAnpassen = "Adjust"
    override val orGekauft = "Strategy in portfolio"
    override val orWertAm = "As of"
    override val orBerechnet = "calculated"
    override val orDatumWahl = "Date"
    override val orSumme = "Total"
    override fun orKaufHint(stueck: String, preis: String, tag: String) = "$stueck pieces at $preis" + if (tag.isBlank()) "" else " · $tag"
    override fun orKlar(key: String, a: String, b: String, c: String) = when (key) {
        "LONG_CALL" -> "Buy a call: profit above $a"
        "LONG_PUT" -> "Buy a put: profit below $a"
        "COVERED_CALL" -> "Hold the stock and sell a call: $a income, profit capped above $b"
        "CASH_SECURED_PUT" -> "Sell a put: $a income, loss below $b"
        "BULL_CALL_SPREAD" -> "Buy a call and sell a higher call: profit above $a, at most $b"
        "BEAR_PUT_SPREAD" -> "Buy a put and sell a lower put: profit below $a, at most $b"
        "STRADDLE" -> "Buy a call and a put: profit above $a or below $b"
        "STRANGLE" -> "Buy a call and a put, both away from the price: profit above $a or below $b"
        "IRON_CONDOR" -> "Sell calls and puts, hedged further out: $a income, profit between $b and $c"
        "BUTTERFLY" -> "Calls around the middle price: profit between $a and $b, at most $c"
        "COLLAR" -> "Hold the stock, buy a put, sell a call: loss capped below $a, profit capped above $b"
        "SCHUTZ_PUT" -> "Hold the stock and buy a put: loss capped below $a, protection costs $b"
        else -> ""
    }
    override fun orKernKosten(b: String) = "Cost $b."
    override fun orKernEinnahme(b: String) = "Income $b."
    override fun orKernGewinnUeber(name: String, tag: String, a: String, pct: String) = "Profit if $name is above $a on $tag ($pct)."
    override fun orKernGewinnUnter(name: String, tag: String, a: String, pct: String) = "Profit if $name is below $a on $tag ($pct)."
    override fun orKernGewinnZwischen(name: String, tag: String, a: String, b: String) = "Profit if $name is between $a and $b on $tag."
    override fun orKernGewinnAusserhalb(name: String, tag: String, a: String, b: String) = "Profit if $name is outside $a and $b on $tag."
    override val orKernGewinnImmer = "Profit at any price."
    override val orKernGewinnNie = "No profit possible."
    override fun orKernMaxGewinn(b: String) = "Largest profit $b."
    override fun orKernMaxVerlust(b: String) = "Largest loss $b."
    override val orKernVerlustUnbegrenzt = "Loss unlimited."
    override fun orErkl(key: String) = when (key) {
        "basispreis" -> "Price at which the option lets you buy (call) or sell (put) the stock. It sets the price from which the option is worth anything."
        "gewinnschwelle" -> "Price on the expiry day at which profit and loss are exactly zero."
        "chance" -> "Estimated probability that the strategy is in profit at expiry. Based on the movement the market expects in option prices."
        "delta" -> "Dollars the position gains or loses when the stock rises by 1 dollar."
        "gamma" -> "How much the delta changes when the stock rises by 1 dollar."
        "theta" -> "Dollars the position loses or gains in one day from the passing of time alone."
        "vega" -> "Dollars the position value changes when expected volatility rises by 1 percentage point."
        else -> "Implied volatility: how much the market expects the price to swing over a year, in percent. Higher values make options more expensive."
    }

    // Alert bell per security (0.77)
    override fun glockeGruppe(gruppe: String) = when (gruppe) {
        "KURS" -> "Price"; "NACHRICHTEN" -> "News"; "INSIDER" -> "Insiders"; "SIGNALE" -> "Analysts and rules"; "TERMINE" -> "Dates"; else -> "Warning signs"
    }
    override fun glockeArt(art: String) = when (art) {
        "SMA50" -> "50-day average crossed"; "VOL2" -> "Double volume"; "W52" -> "52-week high or low"
        "NEWS" -> "Important headlines"; "INSIDER" -> "Insider buy"; "INSIDERV" -> "Insider sale"
        "ANALYST" -> "Upgrade or downgrade"; "QUARTAL" -> "Quarterly results"; "EXDIV" -> "Ex-dividend date"; else -> StrategieTexte.glocke(this, false, art) ?: "New 8-K filing"
    }
    override fun glockeArtKurz(art: String) = when (art) {
        "SMA50" -> "50-day average"; "VOL2" -> "Volume"; "W52" -> "52-week mark"
        "NEWS" -> "News"; "INSIDER" -> "Insider buy"; "INSIDERV" -> "Insider sale"
        "ANALYST" -> "Analysts"; "QUARTAL" -> "Results"; "EXDIV" -> "Ex-date"; else -> StrategieTexte.glocke(this, false, art) ?: "8-K"
    }
    override val glockeAlle = "All"
    override val glockePreismarken = "Price alerts"
    override val glockeNeueMarke = "New price alert"
    override fun glockeBewegung(pct: String?) = if (pct == null) "Daily move" else "Daily move from $pct"
    override fun glockeMarkeKurz(preis: String) = "Price alert $preis"
    override fun glockeMarkenKurz(n: Int) = "$n price alerts"
    override fun glockeBewegungKurz(pct: String) = "Move from $pct"
    override val glockeListe = "Bells"
    override val glockeLeer = "No bells set"
    override val glockeAus = "Turn bell off"
    override fun alertNeuText(tag: String, extra: String, price: String) = when (tag) {
        "ALERT_NEWS" -> extra
        "ALERT_INSIDER_SELL" -> "Insider sale over $extra · price $price"
        "ALERT_HIGH52" -> "52-week high $extra · price $price"
        "ALERT_LOW52" -> "52-week low $extra · price $price"
        "ALERT_ANALYST_UP" -> "Upgraded: $extra · price $price"
        "ALERT_ANALYST_DOWN" -> "Downgraded: $extra · price $price"
        "ALERT_EARNINGS" -> "Quarterly results on $extra · price $price"
        "ALERT_RULE_BUY", "ALERT_RULE_SELL" -> StrategieTexte.signal(this, false, tag, extra, price)
        else -> "New 8-K warning signs: $extra · price $price"
    }
    override fun glockeWeitere(n: Int) = "$n more"
    override fun widgetGrundName(schluessel: String, name: String) = when (schluessel) {
        "zins" -> "Fed funds per futures"
        "fed" -> "Next Fed decision"
        "fed_jahr" -> "Fed cuts this year"
        "ezb" -> "Next ECB decision"
        "spr" -> "US oil reserve"
        "erstantraege" -> "US jobless claims"
        "lieferketten" -> "Supply chain stress"
        "bahn" -> "US rail carloads"
        "halbleiter" -> "US chip output"
        "wsts" -> "Chip sales worldwide"
        "rohoel" -> "US crude stocks"
        "benzin" -> "US gasoline stocks"
        "destillat" -> "US diesel, heating oil"
        "gas_us" -> "US gas storage"
        else -> name
    }
    override fun widgetLageName(key: String, zusatz: String) = when (key) {
        "ENGPASS_GESTOERT" -> "Disrupted chokepoints"
        "SPANNUNG" -> "Areas under tension"
        "DURCHBRUCH_NEU" -> "New breakthroughs, 7 days"
        "DURCHBRUCH_THEMEN" -> "Topics with breakthroughs"
        else -> lbName(key, zusatz).trim()
    }
    override fun widgetCheckName(regel: String) = when (regel) {
        "FONDSKOSTEN" -> "Fund costs"
        "TER_GESTIEGEN" -> "Pricier funds"
        "GROESSTE" -> "Largest position"
        "ANZAHL" -> "Diversification"
        "BRANCHE" -> "Largest sector"
        "WAEHRUNG" -> "Foreign currency"
        "HEBEL" -> "Leveraged products"
        "KRYPTO" -> "Crypto"
        "KOSTEN" -> "Order costs"
        "RESERVE" -> "Reserve"
        "SPARPLAN" -> "Savings plan"
        "VERLUSTE" -> "Deep losses"
        else -> regel
    }
    override fun widgetBlasenName(schluessel: String, name: String) = when (schluessel) {
        "trend" -> "Stretch, 200-day line"
        "konzentration" -> "Top-10 weight"
        "gewinn" -> "Earnings growth"
        "margin" -> "Stocks on credit"
        else -> name
    }
    override val widgetStufeGut = "Good"
    override val widgetKeineDaten = "No data right now"
    override val widgetZuWenigPositionen = "From two positions"
    override val shVerkauft = "Sold"
    override fun shGeschaeft(eroeffnung: Boolean) = if (eroeffnung) "Sell to open" else "Buy to close"
    override val shAusgleichJeStueck = "Settlement per unit"
    override val shStillhalterErtraege = "Sold options"
    override fun shRueckkaufDone(qty: String, price: String) = "Bought back: $qty × $price"
    override val shGruppeGeschlossen = "Strategy closed"
    override fun shAktienGebunden(n: String) = "$n shares cover sold calls"
    override fun shVerfallText(name: String, betrag: String, wertlos: Boolean) =
        if (wertlos) "$name expired worthless, cover released" else "$name: $betrag settlement paid"
    override fun shGebundenZeile(betrag: String) = "$betrag held for sold options"
    override fun shFreiZeile(betrag: String) = "$betrag available"
    override val shPraemie = "Premium received"
    override fun shJeStueck(preis: String) = "$preis per unit"
    override val shRueckkaufpreis = "Buyback price"
    override val shBindung = "Cash held"
    override val shGebundeneAktien = "Shares held"
    override val shStrategie = "Strategy"
    override val shGruppeSchliessen = "Close strategy"
    override val shZurueckkaufen = "Buy back"
    override fun shNurGebunden(n: String) = "$n held, bound"
    override val orAusfuehren = "Place"
    override val anTitel = "Start yourself"
    override val anUntertitel = "Filings, stock finder, stockpiles, imports"
    override val anGruppeLaden = "Load completely"
    override val anGruppeDateien = "Read files"
    override val anGruppeBrowser = "Browser only"
    override val anMeldungen = "Filings of members of Congress"
    override val anKongress = "Congress buys with prices"
    override val anFinder = "Stock finder figures"
    override val anRadar = "Stock radar"
    override val anNie = "not loaded yet"
    override val anKeineDatei = "no file read yet"
    override val anAlle = "all read"
    override val anHeute = "created today"
    override val anHeuteNie = "not created today yet"
    override fun anLaeuft(prozent: Int?) = if (prozent == null) "running" else "running $prozent %"
    override fun anAm(datum: String) = "loaded on $datum"
    override fun anBis(datum: String) = "loaded up to $datum"
    override fun anMonate(n: Int) = if (n == 1) "1 month read" else "$n months read"
    override fun anOffen(n: Int) = "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} open"
    override fun afRueckblickAbdeckung(schnitt: String, jahre: List<Pair<String, String>>, voll: Boolean) =
        "• Measured per date: on average $schnitt of the members at the time" +
            (if (jahre.isEmpty()) "." else " (" + jahre.joinToString(", ") { (j, a) -> "$j: $a" } + ").") +
            (if (voll) "" else " Prices are missing for the rest, which flatters the result.")
    // [Laender Historie 0.78]
    override val histTitel = "Since 1870"
    override val histAktien = "Stocks"
    override val histAnleihen = "Bonds"
    override val histImmobilien = "Housing"
    override val histZins = "Short-term rate"
    override fun histReal(titel: String) = "$titel, real"
    override fun histJeJahrAb(jahr: Int) = "per year from $jahr"
    override val histKernInfo = "Average return per year after inflation over all years with data, including dividends, interest and rents. The series start in different years depending on the asset class."
    override val histOhneRenditen = "The source has no returns for this country"
    override fun histLueckeInfo(von: Int, bis: Int) = "No values for $von to $bis (hyperinflation)."
    override val histVermoegen = "Real wealth, logarithmic"
    override fun histKrisen(n: Int) = "Financial crises ($n)"
    override val histKrisenInfo = "Crisis years according to the source, shown as dashed lines in the chart."
    override val histSchlecht = "Worst ten years, stocks"
    override val histSchlechtInfo = "Real return per year over ten consecutive years. The three worst periods without shared years are shown."
    override fun histZeitraum(von: Int, bis: Int) = "$von to $bis"
    override fun histJe(wert: String) = "$wert per year"
    override val histInflation = "Inflation per year"
    override fun histInflationInfo(ab: Int) = "Average price increase per year since $ab."
    override fun histKredit(jahr: Int) = "Credit to GDP $jahr"
    override val histKreditInfo = "Loans to the private sector excluding financial firms relative to economic output, last year with a value."
    override val histZitatKopf = "Source: Jordà-Schularick-Taylor Macrohistory Database, www.macrohistory.net/database. To be cited as:"
    override val histLizenzJst = "License CC BY-NC-SA 4.0: non-commercial use only, attribution, sharing only under the same terms. The figures are converted from the database (real returns)."
    override fun histAbruf(datum: String) = "Consulted on $datum."
    override fun hpTitel(ab: Int) = "Real house prices since $ab"
    override val hpPreise = "Prices, real"
    override val hpVerhaeltnis = "Price to income"
    override fun hpSeit(ab: Int) = "since $ab"
    override fun hpSchnitt(abweichung: Int, ab: Int) = (if (abweichung > 0) "above" else if (abweichung < 0) "below" else "at") + " average since $ab"
    override fun hpInfo(bis: String) = "Real house prices and real disposable income per country, deflated with the consumption expenditure deflator (PCE).\n\n• Price to income: real house prices divided by real income, latest value against the mean of all quarters since the series began.\n• Data up to $bis. Where observations are missing the source fills in with its own forecasts."
    override fun hpQuartal(jahr: Int, quartal: Int) = "$jahr Q$quartal"
    override fun hpVerlauf(ab: Int) = "Real prices and income, $ab = 100"
    override val hpPreisLinie = "Prices"
    override val hpEinkommenLinie = "Income"
    override val hpZitatKopf = "Source: Federal Reserve Bank of Dallas, International House Price Database, www.dallasfed.org/research/international/houseprice. To be cited as:"
    override val hpNutzung = "Used not for private gain, with the bank credited."
    override val globusHaus = "House prices to income"
    override val globusAktien1870 = "Stocks, real, since 1870"
    override val globusAktien1950 = "Stocks, real, since 1950"
    override val globusLegHaus = "Price to income against the own average of each country since 1975. Blue below, ochre above."
    override val globusLegAktien1870 = "Real return per year, countries of the source. Each country from its first year with data."
    override val globusLegAktien1950 = "Real return per year since 1950, countries with returns in the source."
    override val rzBand = "US recession"
    override fun rzBandAb(jahr: Int) = "US recessions from $jahr"
    override val nyName = "Recession in 12 months"
    override fun nySatz(stufe: Int, monat: String) =
        "US recession in $monat " + when (stufe) { 0 -> "unlikely"; 1 -> "possible"; else -> "likely" }
    override fun nyBilanz(schwelle: String, treffer: Int, von: Int, fehlalarme: Int, abJahr: Int) =
        "Above $schwelle % before $treffer of $von recessions since $abJahr, $fehlalarme times with no recession following"
    override fun nyStand(monat: String) = "Yield curve up to $monat"
    override val nyErklaerung = "The New York Fed derives from the gap between ten-year and three-month US Treasury yields " +
        "how likely the US is to be in a recession in twelve months. When the short rate is above the long one, the value rises. " +
        "The line shows the value as it was seen at the time, the gray areas the recessions dated by the NBER."
    override val ensoKernNeutral = "No phase active, no typical effect on commodity prices"
    override fun ensoKern(hoch: String, tief: String) = "Bullish for " + hoch + if (tief.isEmpty()) "" else "\nBearish for " + tief
    override val ensoKurz = "El Niño: the equatorial Pacific is warmer than usual, La Niña: colder. Both shift rain and drought for months, and with them harvests and heating demand."
    override val ensoBeiNino = "During El Niño"
    override val ensoBeiNina = "During La Niña"
    override val ensoMuster = "Typical patterns of past events, referring to the price. Every event unfolds differently."
    override val ensoBullish = "bullish"
    override val ensoBearish = "bearish"
    override fun ensoWirkName(k: String) = when (k) {
        "palmoel" -> "Palm oil"; "zucker" -> "Sugar"; "kakao" -> "Cocoa"; "kaffee" -> "Coffee"; "reis" -> "Rice"; "weizen" -> "Wheat"
        "soja" -> "Soybeans"; "mais" -> "Corn"; "erdgas" -> "US natural gas"; "kohle" -> "Coal"; else -> k
    }
    override fun ensoWirkGrund(phase: Int, k: String) = if (phase > 0) when (k) {
        "palmoel" -> "Drought in Indonesia and Malaysia lowers yields"
        "zucker" -> "Weak monsoon in India, dry weather in Thailand"
        "kakao" -> "Dry weather in West Africa"
        "kaffee" -> "Drought in Vietnam and Indonesia hits robusta"
        "reis" -> "Weak monsoon in South and Southeast Asia"
        "weizen" -> "Drought in Australia"
        "soja" -> "More rain in Argentina and southern Brazil, larger harvests"
        "erdgas" -> "Milder winter in the northern US, less heating demand"
        else -> ""
    } else when (k) {
        "soja" -> "Drought in Argentina and southern Brazil"
        "mais" -> "Drought in Argentina and southern Brazil"
        "weizen" -> "Dry weather in the southern US and Argentina"
        "erdgas" -> "Colder winter in the northern US, more heating demand"
        "kohle" -> "Heavy rain in eastern Australia disrupts mining and loading"
        else -> ""
    }
    override val tlTitel = "Sectors and themes"
    override val tlMedian = "Stock median"
    override val tlBeispiele = "Examples"
    override fun tlAktien(n: Int) = if (n == 1) "1 stock" else "$n stocks"
    override fun tlNeu(n: Int) = if (n == 1) "1 new breakthrough" else "$n new breakthroughs"
    override fun cotSeit(hoch: Boolean, long: Boolean, seit: String, rekord: Boolean): String {
        val was = when {
            hoch && long -> "Most net long"
            hoch -> "Least net short"
            long -> "Least net long"
            else -> "Most net short"
        }
        return if (rekord) "$was on record since $seit" else "$was since $seit"
    }
    override fun cotRang(rang: Int, abJahr: Int) = "Since $abJahr, $rang % of weeks were at or below this level."
    override fun cotWeitere(n: Int) = "$n more markets"
    override fun cotWeitereUnter(auffaellig: Int) = "Bonds, currencies, metals, energy, crops" + if (auffaellig > 0) " · $auffaellig one-sided" else ""
    override fun standSeit(hoch: Boolean, seit: String, rekord: Boolean) =
        (if (hoch) "Highest" else "Lowest") + " since " + (if (rekord) "the series began in $seit" else seit)
    override val vmTitel = "Patterns of the past"
    override fun vmIndex(id: String) = when (id) { "sp500" -> "S&P 500"; "dax" -> "DAX"; else -> id }
    override fun vmZyklusName(jahr: Int) = when (jahr) {
        1 -> "Year after the US election"; 2 -> "US midterm year"; 3 -> "Year before the US election"; else -> "US election year"
    }
    override fun vmZyklusKurz(jahr: Int) = if (jahr == 4) "Election year" else "Year $jahr"
    override fun vmZyklusSatz(positiv: Int, anzahl: Int, jahre: Int, von: Int) =
        "S&P 500 on average · up in $positiv of $anzahl years · $jahre years since $von"
    override fun vmBalkenKopf(name: String, mittel: String, positiv: Int, anzahl: Int) = "$name · $mittel · up in $positiv of $anzahl"
    override fun vmGrundlage(id: String, von: Int, bis: Int) = (if (id == "dax") "DAX with dividends" else "S&P 500 without dividends") + " · $von to $bis"
    override fun vmMonat(monat: Int) = listOf(
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
    ).getOrElse(monat - 1) { "" }
    override fun vmSaisonName(index: String, monat: String) = "$index in $monat"
    override fun vmSaisonSatz(positiv: Int, anzahl: Int, von: Int) = "On average · up in $positiv of $anzahl years since $von"
    override val vmBaerName = "S&P 500 below its high"
    override fun vmBaerSatz(n: Int, von: Int, tiefe: String, monate: Int) =
        "$n declines of 20% or more since $von · on average −$tiefe%, $monate months to the low"
    override val vmBaerBeginn = "Start"
    override val vmBaerTiefe = "Depth"
    override val vmBaerTief = "Low"
    override val vmBaerErholt = "Recovered"
    override val vmBaerJetzt = "Now"
    override val vmBaerOffen = "open"
    override val vmBaerEinheit = "Low and Recovered: months since the high"
    // [0.78] Historische Annahmen der Rechner, Messung des Rezessionssignals und des Kreditanstiegs
    override val hrAnnahmen = "Assumptions"
    override val hrEigene = "Own"
    override val hrWelt = "Historical world"
    override fun hrLand(name: String) = "Historical $name"
    override val hrWeltName = "world"
    override val hrRendite = "Real return per year"
    override fun hrSpanne(name: String, von: Int, bis: Int) = "Stocks $name, $von to $bis"
    override fun hrSchlecht(jahre: Int) = if (jahre == 1) "Worst year" else "Worst $jahre years"
    override fun hrBest(jahre: Int) = if (jahre == 1) "Best year" else "Best $jahre years"
    override fun hrZeitraumReal(von: Int, bis: Int) = if (von == bis) "$von, real" else "$von to $bis, real per year"
    override val hrVerlaeufe = "Paths"
    override val hrZufall = "Random"
    override val hrFolgen = "Historical sequences"
    override val hrStartjahre = "Start years"
    override fun hrSchlechtStart(jahr: Int) = "Worst start $jahr"
    override fun hrBestStart(jahr: Int) = "Best start $jahr"
    override fun hrVon(n: Int, von: Int) = "in $n of $von"
    override fun hrReichte(n: Int, von: Int, jahre: String) = "The money lasted $jahre for $n of $von start years."
    override fun hrLeerNach(jahre: Int) = if (jahre == 1) "empty after 1 year" else "empty after $jahre years"
    override fun hrNoteZufall(name: String, von: Int, bis: Int) =
        "Return and volatility from real annual stock returns, $name, $von to $bis. The paths are random, amounts are in today's money."
    override fun hrNoteFolgen(name: String, von: Int, bis: Int, jahre: Int) =
        "Each start year from $von to $bis runs once with the real annual returns of the following $jahre years (stocks, $name). " +
            "The periods overlap and are not independent. Amounts are in today's money."
    override fun hrWeltInfo(laender: Int) = "World: each year the mean of all countries with a value, up to $laender."
    override fun hrRateInflation(infl: String) = "The savings rate loses purchasing power with $infl inflation per year."
    override val hrNoteEnde = "Without costs and taxes. Past returns do not predict future ones."
    override fun nyEinbrueche(schwelle: String, treffer: Int, von: Int, abJahr: Int, grund: String) =
        "Before $treffer of $von S&P 500 drops of 20% or more since $abJahr it was above $schwelle% in the year before. The same held before $grund% of all months."
    override fun hrKreditAnstieg(jahre: Int) = "Change in $jahre years"
    override fun hrPunkte(wert: String) = "$wert points"
    override fun hrKreditInfo(schwelle: String, jahre: Int, mitKrise: Int, faelle: Int, quote: String, grund: String, laender: Int, von: Int, bis: Int) =
        "Change of credit to GDP in percentage points against the value $jahre years earlier.\n\n" +
            "Measured on $laender countries, $von to $bis: after a rise of $schwelle points or more, a financial crisis followed within $jahre years " +
            "in $mitKrise of $faelle country-years ($quote%). Across all country-years it was $grund%. Consecutive years overlap."
    override fun lbStandJahr(jahr: Int) = "As of $jahr"
    override fun lbUmgerechnet(waehrung: String) = "Shown in $waehrung, converted from US dollars at today's exchange rate."
    override fun heatmapKgv(wert: String) = "P/E $wert"
    override val lbTreemapHinweisKgv = "Area: market cap, color: change today, third line: P/E"
    override val kursOhneLauf = "No current price"
    override fun kursLetzterStand(datum: String) = "Last price $datum"
    override fun kursKennzahlenStand(datum: String) = "Figures and valuation as of $datum"
    // [Waehrung und Kaufkraft 0.78]
    override val kkTitel = "Currency and purchasing power"
    override val kkKaufkraft = "Purchasing power"
    override val kkPreisniveau = "Price level"
    override val kkDeutschland = "Germany"
    override val kkDollar = "dollars"
    override val kkEuro = "euros"
    override fun kkVon(land: String) = "of $land"
    override fun kkZum(code: String) = if (code == "EUR") "Currency vs euro" else "Currency vs dollar"
    override val kkUnterKkp = "below purchasing power parity"
    override val kkUeberKkp = "above purchasing power parity"
    override val kkAufKkp = "at purchasing power parity"
    override fun kkBasisJahr(land: String, jahr: Int) = "$land = 100%, $jahr"
    override fun kkKorb(betrag: String) = "Basket worth $betrag"
    override fun kkKorbHinweis(land: String, betrag: String) = "Price here for what costs $betrag in $land"
    override fun kkReise(betrag: String) = "$betrag travel money"
    override fun kkReiseHinweis(land: String) = "Purchasing power here, measured at prices in $land"
    override val kkBipKopf = "GDP per capita"
    override fun kkBipKopfHinweis(jahr: Int) = "at the exchange rate, $jahr"
    override val kkBipKaufkraft = "GDP per capita by purchasing power"
    override fun kkBipKaufkraftHinweis(land: String) = "at prices of $land"
    override fun kkNiveauVerlauf(ab: Int, land: String) = "Price level since $ab ($land = 100)"
    override fun kkStaerke(waehrung: String) = "Value of the currency in $waehrung"
    override val kkZeitraum = "Period"
    override val kkNominal = "nominal"
    override val kkReal = "real"
    override fun kkJahre(n: Int) = if (n == 1) "1 year" else "$n years"
    override fun kkSchnitt(jahr: Int) = "Annual averages up to $jahr."
    override val kkBindung = "Exchange rate peg"
    override val kkFestDollar = "fixed to the dollar"
    override val kkFestEuro = "fixed to the euro"
    override val kkBindungHinweis = "from ten years of rates"
    override val kkReer = "Real effective rate"
    override fun kkReerHinweis(jahr: Int) = "against its own ten-year average, $jahr"
    override val kkReerVerlauf = "Real effective exchange rate (2010 = 100)"
    override fun kkInfoNiveau(land: String) = "How expensive the same basket of goods is in the country, measured against $land. 58% means: what costs 100 in $land costs 58 there.\n\n" +
        "Calculated from IMF GDP per capita: value at the exchange rate divided by the value at purchasing power parity.\n\n" +
        "The basket covers the whole economy. Rents, restaurants or imported goods can differ a lot."
    override val kkInfoKkp = "Purchasing power parity is the exchange rate at which the same basket costs the same in both countries.\n\n" +
        "Minus 31% means: at today's rate the country is 31% cheaper than the comparison country. The currency looks weaker than its purchasing power at home.\n\n" +
        "Poorer countries are almost always below it because services are cheaper there. It is no price target."
    override fun kkInfoKorb(land: String) = "Both figures follow from the price level against $land.\n\n" +
        "Basket: the price in the country for goods and services that cost the stated amount in $land.\n\n" +
        "Travel money: what you would have to spend in $land to buy the same as with the stated amount in the country.\n\n" +
        "Based on the average of the whole economy. Tourist areas are often more expensive."
    override fun kkInfoBip(land: String) = "At the exchange rate: economic output per inhabitant, converted into the display currency.\n\n" +
        "By purchasing power: the same value at prices of $land. It shows how much output per capita buys in the country, measured against $land.\n\n" +
        "A country with a weak currency and low prices ranks higher by purchasing power than at the exchange rate."
    override val kkInfoStaerke = "Change in the annual average exchange rate. Plus means: the country's currency has gained value.\n\n" +
        "Nominal: the rate alone.\n\n" +
        "Real: rate and inflation difference (IMF). If prices in the country rise faster, it gets more expensive for foreigners even when the rate stands still.\n\n" +
        "No value is shown across a redenomination or a new currency."
    override val kkInfoBindung = "The annual average rate stayed within a band of 3% for ten years. That points to a peg or a shared currency.\n\n" +
        "Derived from the rates, no official classification."
    override val kkInfoReer = "The real effective exchange rate measures the currency against the currencies of trading partners, adjusted for prices.\n\n" +
        "Above its own average: the country's goods have become more expensive abroad. Below: they have become cheaper.\n\n" +
        "The World Bank carries the series for about 90 countries."
    override val kkVglBip = "GDP/capita"
    override val kkVglKaufkraft = "by purchasing power"
    override fun kkVglUmrechnung(betrag: String, a: String, b: String, wert: String) = "$betrag from $a has the purchasing power of $wert in $b."
    override val bmTitel = "Big Mac index"
    override val bmPreis = "Price of a Big Mac"
    override fun bmStandEuroraum(monat: String) = "Euro area, $monat"
    override val bmUmgerechnet = "Converted"
    override val bmKursStichtag = "at the rate of the survey date"
    override val bmGegenEuro = "Against the euro"
    override val bmGegenDollar = "Against the dollar"
    override val bmRoh = "raw: price against price"
    override val bmBereinigt = "adjusted for GDP per capita"
    override fun bmVerlauf(ab: Int) = "Big Mac against the dollar since $ab"
    override val bmInfo = "Twice a year The Economist compares the price of a Big Mac in about 50 countries and the euro area.\n\n" +
        "Raw: plus means the Big Mac costs more after conversion than in the comparison area. The currency then counts as overvalued.\n\n" +
        "Adjusted: in poorer countries wages and therefore burgers are cheaper. The adjusted figure removes GDP per capita.\n\n" +
        "A single product, no basket. Countries using the euro appear together as the euro area.\n\n" +
        "Data: The Economist, The Big Mac index, license CC BY 4.0 (creativecommons.org/licenses/by/4.0). The price in the display currency is converted."
    override val rlTitel = "Rankings"
    override fun rlName(x: de.troetenrepublik.app.engine.Ranglisten.Index) = when (x) {
        de.troetenrepublik.app.engine.Ranglisten.Index.HDI -> "Human development (HDI)"
        de.troetenrepublik.app.engine.Ranglisten.Index.IHDI -> "Inequality-adjusted HDI"
        de.troetenrepublik.app.engine.Ranglisten.Index.GESCHLECHTER -> "Gender inequality"
        de.troetenrepublik.app.engine.Ranglisten.Index.GINI -> "Income inequality (Gini)"
        de.troetenrepublik.app.engine.Ranglisten.Index.LOGISTIK -> "Logistics (LPI)"
        de.troetenrepublik.app.engine.Ranglisten.Index.HUMANKAPITAL -> "Human capital"
    }
    override fun rlKurz(x: de.troetenrepublik.app.engine.Ranglisten.Index) = when (x) {
        de.troetenrepublik.app.engine.Ranglisten.Index.HDI -> "Development"
        de.troetenrepublik.app.engine.Ranglisten.Index.IHDI -> "HDI, inequality"
        de.troetenrepublik.app.engine.Ranglisten.Index.GESCHLECHTER -> "Gender"
        de.troetenrepublik.app.engine.Ranglisten.Index.GINI -> "Equality"
        de.troetenrepublik.app.engine.Ranglisten.Index.LOGISTIK -> "Logistics"
        de.troetenrepublik.app.engine.Ranglisten.Index.HUMANKAPITAL -> "Human capital"
    }
    private val undpLizenz = "Data: UNDP, Human Development Report, license CC BY 3.0 IGO (creativecommons.org/licenses/by/3.0/igo). Values unchanged, rank counted here."
    override fun rlInfo(x: de.troetenrepublik.app.engine.Ranglisten.Index) = when (x) {
        de.troetenrepublik.app.engine.Ranglisten.Index.HDI -> "Human Development Index of the United Nations, from 0 to 1.\n\n" +
            "It combines life expectancy, years of schooling and income per capita. From 0.8 development counts as very high.\n\n" +
            undpLizenz
        de.troetenrepublik.app.engine.Ranglisten.Index.IHDI -> "The HDI, reduced by inequality in health, education and income.\n\n" +
            "The more unequal a country, the further the value lies below the HDI.\n\n" +
            undpLizenz
        de.troetenrepublik.app.engine.Ranglisten.Index.GESCHLECHTER -> "United Nations index from 0 to 1 for disadvantages of women in health, education, seats in parliament and work.\n\n" +
            "0 means equal. Rank 1 goes to the country with the lowest value.\n\n" +
            undpLizenz
        de.troetenrepublik.app.engine.Ranglisten.Index.GINI -> "The Gini value measures how unequally incomes are spread: 0 means all equal, 100 means one person has everything.\n\n" +
            "Rank 1 goes to the country with the most even spread. Surveys come from different years, the year is shown.\n\n" +
            "Data: World Bank, license CC BY 4.0."
        de.troetenrepublik.app.engine.Ranglisten.Index.LOGISTIK -> "Logistics Performance Index of the World Bank from 1 to 5.\n\n" +
            "Surveyed freight forwarders rate customs, infrastructure, tracking and timeliness.\n\n" +
            "Data: World Bank, license CC BY 4.0."
        de.troetenrepublik.app.engine.Ranglisten.Index.HUMANKAPITAL -> "Human Capital Index of the World Bank from 0 to 1.\n\n" +
            "It estimates how productive a child born today will be as an adult, measured against full health and education. 0.7 means 70% of what is possible.\n\n" +
            "Data: World Bank, license CC BY 4.0."
    }
    override fun rlPlatz(rang: Int) = "Rank $rang"
    override fun rlVon(n: Int) = "of $n"
    override fun rlRang(rang: Int, von: Int, jahr: Int) = "Rank $rang of $von, $jahr"
    override fun rlHdiVerlauf(ab: Int) = "Human development since $ab"
    override val globusKkp = "Currency vs purchasing power"
    override val globusHdi = "Development (HDI)"
    override val globusGini = "Inequality (Gini)"
    override val globusLegPreisniveau = "Price level in percent of your own country. Blue cheaper, ochre more expensive."
    override val globusLegKkp = "Exchange rate against purchasing power parity to the dollar. Blue below, ochre above."
    override val globusLegHdi = "Human Development Index of the United Nations from 0 to 1."
    override val globusLegGini = "Gini value of incomes, latest survey. Blue more even, ochre more unequal."
    override val ozErklaerung = "• You sell an option and receive the premium at once (also called option writing)\n" +
        "• Call: you deliver the shares at the strike if the price rises above it\n" +
        "• Put: you buy the shares at the strike if the price falls below it"
    override fun ozFaellig(tag: String) = "expires $tag"
    override val ozErtragJahr = "Yield per year"
    override val ozAbstandKurs = "Distance to price"
    override val ozGehandelt = "Traded value"
    override fun ozSeite(seite: String) = when (seite) { "KAUF" -> "likely bought"; "VERKAUF" -> "likely sold"; else -> "side unclear" }
    override fun suGruppe(key: String) = when (key) {
        "EDELMETALLE" -> "Precious metals"; "ENERGIE" -> "Energy"; "INDUSTRIEMETALLE" -> "Industrial metals"; "AGRAR" -> "Agriculture"
        "HAUPT" -> "Majors"; "NEBEN" -> "Minors"; "SCHWELLEN" -> "Emerging markets"; "OHNE_EURO" -> "Without euro"
        "LAYER1" -> "Layer 1"; "ZAHLUNG" -> "Payments"; "STABLECOIN" -> "Stablecoins"; "DEFI" -> "DeFi"
        "INFRASTRUKTUR" -> "Infrastructure"; "MEME" -> "Meme"
        else -> key
    }
    override val suGruppeTitel = "Group"
    override fun kyListe(key: String) = when (key) {
        "MARKTWERT" -> "Market cap"; "GEWINNER" -> "Gainers"; "VERLIERER" -> "Losers"; "UMSATZ" -> "Volume"; "HOCH" -> "Distance to high"
        else -> key
    }
    override fun kyListeTitel(key: String, woche: Boolean) = when (key) {
        "MARKTWERT" -> "Largest by market cap"
        "GEWINNER" -> if (woche) "Gainers this week" else "Gainers today"
        "VERLIERER" -> if (woche) "Losers this week" else "Losers today"
        "UMSATZ" -> "Volume in 24 hours"
        "HOCH" -> "Distance to 52-week high"
        else -> key
    }
    override val kyTag = "Day"
    override val kyWoche = "Week"
    override val kyUnterHoch = "below high"
    override val kyBitcoinAnteil = "Bitcoin share"
    override fun kyBitcoinAnteilHinweis(anzahl: Int) = "of the market cap of the $anzahl coins in this list"
    override fun roh(key: String, w: List<String>) = RohstoffLageTexte.text(false, key, w)
    override val umJede = "Any deviation"
    override val umAbSchwelle = "From 10 points"
    override val umNoetig = "Rebalancing needed"
    override val umAllesImRahmen = "All within range"
    override val umRahmen = "within range"
    override val umKeinZiel = "no target"
    override val umNochKeine = "No target weights yet"
    override val umJetzt = "Now"
    override val umZiel = "Target"
    override val ppfZeitUnterHoch = "Time below the high"
    override val ppfMittlererRueckgang = "Average drawdown"
    override val ppfMittlererRueckgangHinweis = "on average below the high"
    override val stabTitel = "Stability"
    override val stabKeine = "No net debt"
    override fun stabName(key: String) = when (key) {
        "GEARING" -> "Net debt to equity"; "TILGUNG" -> "Net debt to free cash flow"; else -> "Net debt to EBITDA"
    }
    override fun stabSatz(key: String, wert: String) = when (key) {
        "GEARING" -> wert; "TILGUNG" -> "$wert years"; else -> "$wert×"
    }
    override fun stabNegativ(key: String) = when (key) {
        "GEARING" -> "Negative equity"; "TILGUNG" -> "Negative free cash flow"; else -> "Negative EBITDA"
    }
    override fun stabSchwelle(key: String) = when (key) {
        "GEARING" -> "up to 20% very good, above 100% critical"; "TILGUNG" -> "under 2 years very good, 5 or more critical"; else -> "3 or more critical"
    }
    override val afRangsumme = "Rank sum"
    override fun afMindestens(n: Int, m: Int) = "At least $n of $m"
    override fun afFeldAnteil(feld: String, anteil: String, oben: Boolean) = if (oben) "$feld in the highest $anteil" else "$feld in the lowest $anteil"
    override fun afFeldSpitze(feld: String, n: Int, oben: Boolean) = if (oben) "$feld: $n highest" else "$feld: $n lowest"
    override fun afUnterHoch52(abstand: String) = "At least $abstand below the 52-week high"
    override fun mz(key: String, w: List<String>) = MarktzeichenTexte.text(false, key, w)
    override val levTitel = "Levermann score"
    override fun levSatz(punkte: String, von: Int) = "$punkte of $von points"
    override fun levKaufAb(ab: Int, gross: Boolean) = if (gross) "Method threshold: $ab points for large caps" else "Method threshold: $ab points for mid and small caps"
    override fun levKaufAbKurz(ab: Int) = "Buy from $ab points"
    override fun levOhne(n: Int) = "$n without data"
    override val levFinanz = "Financial stock, no EBIT margin"
    override val levKeine = "no data"
    override fun levName(key: String) = when (key) {
        "EK_RENDITE" -> "Return on equity"; "EBIT_MARGE" -> "EBIT margin"; "EK_QUOTE" -> "Equity ratio"; "KGV" -> "P/E"
        "KGV_SCHNITT" -> "P/E (avg. earnings)"; "ANALYSTEN" -> "Analyst opinion"; "ZAHLEN" -> "Reaction to earnings"
        "REVISIONEN" -> "Earnings revisions"; "KURS6" -> "Price change 6 months"; "KURS12" -> "Price change 12 months"
        "MOMENTUM" -> "Price momentum"; "UMKEHR" -> "Three-month reversal"; else -> "Earnings growth"
    }
    override fun levVon(n: Int) = "of $n points"
    override fun levGruppe(key: String) = when (key) {
        "QUALITAET" -> "Quality"; "BEWERTUNG" -> "Valuation"; "STIMMUNG" -> "Sentiment"; else -> "Price"
    }
    override fun levKurz(key: String, variante: Boolean) = when (key) {
        "MOMENTUM" -> "Price trend turns up: +1, turns down: −1"
        "UMKEHR" -> if (variante) "Counts for large caps only" else "Three months in a row weaker than the index: +1, stronger: −1"
        else -> ""
    }
    override fun levWert(key: String, punkte: Int, variante: Boolean) = when (key) {
        "MOMENTUM" -> if (punkte > 0) "turning up" else if (punkte < 0) "turning down" else "no turn"
        else -> if (variante) "not counted" else if (punkte > 0) "3 months weaker" else if (punkte < 0) "3 months stronger" else "mixed"
    }
    override val lyTitel = "Lynch category"
    override fun lyTyp(key: String) = when (key) {
        "LANGSAM" -> "Slow Grower"; "STETIG" -> "Stalwart"; "SCHNELL" -> "Fast Grower"; "ZYKLIKER" -> "Cyclical"
        "TURNAROUND" -> "Turnaround"; else -> "Asset Play"
    }
    override fun lyGrund(key: String, wert: String) = when (key) {
        "VERLUST" -> "Loss in the last fiscal year"; "VERLUST_VORJAHR" -> "Profit after a loss the year before"
        "BRANCHE" -> "Industry moves with the economy"; "KBV" -> "Price below book value (P/B $wert)"
        "GEWINNWACHSTUM" -> "Earnings $wert per year"; else -> "Revenue $wert per year"
    }
    override fun lyDividende(wert: String) = "Dividend $wert"
    override fun lySchulden(wert: String, ok: Boolean) = if (ok) "Debt $wert of equity (80% at most)" else "Debt $wert of equity, more than 80%"
    override val lyWachstumWarnung = "Growth above 50% rarely lasts"
    override val fkTitel = "Fair P/E"
    override fun fkSatz(fair: String, aktuell: String?) = if (aktuell != null) "fair $fair, now $aktuell" else "fair $fair"
    override fun fkAbstand(prozent: String, ueber: Boolean) = if (ueber) "current P/E $prozent above fair" else "current P/E $prozent below fair"
    override val fkSockel = "Base P/E"
    override val fkSockelInfo = "Starting P/E of an average company before premiums are added."
    override val fkStabilInfo = "Premium in P/E points: stable earnings justify a higher P/E."
    override val fkMarktInfo = "Premium in P/E points: companies with a strong market position or high margin deserve a higher P/E."
    override val fkRenditeInfo = "Factor on the P/E: a return on equity above 12% raises it, below that it lowers it."
    override val fkStabil = "Earnings stability premium"
    override fun fkStabilHinweis(schwankung: String?, jahre: Int) = if (schwankung != null) "Variation $schwankung over $jahre years" else "A loss year within $jahre years"
    override val fkMarkt = "Moat premium"
    override val fkRendite = "Return on equity factor"
    override fun fkMarktHinweis(marktmacht: Boolean, wert: String) = if (marktmacht) "Moat $wert of 100" else "Net margin $wert"
    override fun fkRenditeHinweis(rendite: String) = "Return on equity $rendite, 12% gives factor 1"
    override val ewTitel = "Earnings power value"
    override fun ewSatz(prozent: String, ueber: Boolean) = if (ueber) "$prozent above earnings power value" else "$prozent below earnings power value"
    override fun ewUnter(ueber: Boolean) = if (ueber) "The premium pays for future growth" else "Today's earnings cover the price without growth"
    override val ewNegativ = "No positive earnings power value"
    override val ewJeAktie = "Value per share"
    override fun ewJeAktieHinweis(kosten: String) = "Earnings divided by $kosten cost of capital, plus cash, minus debt"
    override val ewGewinn = "Sustainable earnings"
    override fun ewGewinnHinweis(marge: String, jahre: Int, steuer: String) = "EBIT margin $marge averaged over $jahre years, tax rate $steuer"
    override val ewKurs = "Price"
    override val ewBuchwert = "Book value per share"
    override fun ewBuchwertHinweis(prozent: String, ueber: Boolean) = if (ueber) "Earnings power value $prozent above: competitive advantage" else "Earnings power value $prozent below"
    override fun mtZustand(zustand: String) = when (zustand) {
        "AUF" -> "Uptrend intact"; "AB" -> "Downtrend intact"
        "AUF_GEBROCHEN" -> "Uptrend broken"; "AB_GEBROCHEN" -> "Downtrend broken"
        else -> "No trend from highs and lows"
    }
    override fun mtMarke(zustand: String, kurs: String) = when (zustand) {
        "AUF" -> "breaks below $kurs"; "AB" -> "breaks above $kurs"; "AUF_GEBROCHEN" -> "below $kurs"; else -> "above $kurs"
    }
    override val mtStopp = "Stop from the trend"
    override fun mtStoppHinweis(abstand: String) = "last correction low, $abstand below the price"
    override val fmTitel = "Chart pattern"
    override fun fmName(art: String) = when (art) {
        "doppeltop" -> "Double top"; "doppelboden" -> "Double bottom"
        "sks" -> "Head and shoulders"; "sks_umgekehrt" -> "Inverse head and shoulders"
        "dreieck_auf" -> "Ascending triangle"; "dreieck_ab" -> "Descending triangle"
        else -> "Symmetrical triangle"
    }
    override fun fmBedeutung(art: String, auf: Boolean) = when (art) {
        "doppeltop" -> "Two highs at the same level, low between them broken: pullback possible"
        "doppelboden" -> "Two lows at the same level, high between them broken: rebound possible"
        "sks" -> "Head above two shoulders, neckline broken: pullback possible"
        "sks_umgekehrt" -> "Head below two shoulders, neckline broken: rebound possible"
        "dreieck_auf" -> "Highs at the same level, lows rising: breakout " + (if (auf) "upwards" else "downwards")
        "dreieck_ab" -> "Lows at the same level, highs falling: breakout " + (if (auf) "upwards" else "downwards")
        else -> "Highs falling, lows rising: breakout " + (if (auf) "upwards" else "downwards")
    }
    override fun fmDurchbruch(datum: String) = "Breakout on $datum"
    override val rhTitel = "Roll"
    override fun rhDruck(imGeld: Boolean) = if (imGeld) "Price below the strike" else "Price close to the strike"
    override val rhGutschrift = "Credit per unit"
    override val rhKosten = "Cost per unit"
    override val rhNeuePraemie = "New premium"
    override val rhKeine = "No put with 45 to 90 days to expiry in the option chain."
    override val rhRollen = "Roll"
    override val rhGerollt = "Rolled"
    override val rhNachAndienung = "After assignment: covered call"
    override fun rhFrage(alt: String, neu: String, datum: String) = "Buy back put $alt, sell put $neu expiring $datum."
    override fun ml(key: String, w: List<String>) = MarktLaenderTexte.text(false, key, w)
    override fun nhAm(zeit: String) = "on $zeit"
    override fun itTitelTage(tage: Int) = "Insider trend, $tage days"
    override fun vdZusammenfassungAelter(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String) =
        "Latest filings: " + vdZusammenfassung(kaeufe, kaufBetrag, verkaeufe, verkaufBetrag).substringAfter(": ")
    override fun kvFensterBis(tage: Int, bis: String) = "Buyers by filing date, $tage days to $bis"
    override val whZwoelfMonate = "12 months"
    override val trumpMarktreaktion = "Posts with market reaction"
    override val abhWasWaereDepot = "Worked examples for the whole portfolio"
    override val glockeTermin = "Bell for this date"
    override fun crOptionenOffen(long: String) = "Calls as share of open interest: $long"
    override val modellVol = "Model volatility"
    override val gsrTrend = "Trend from highs and lows"
    override fun stabGrund(key: String) = when (key) {
        "GEARING" -> "net debt above equity"; "TILGUNG" -> "paying off debt from free cash flow takes 5 years or more"; else -> "net debt at 3× EBITDA or more"
    }
    override fun p1BilanzGemessen(art: String) = when (art) {
        "bb" -> "\nMeasured: close below the lower band (rebound) or above the upper band (pullback)"
        "rsi" -> "\nMeasured: RSI rises back above 30 (rising) or falls back below 70 (falling)"
        else -> ""
    }
    override fun dp(key: String, w: List<String>) = DepotTexte.text(false, key, w)
    override val newsNurEnglisch = "English sources only"
    // 0.78 Strategietest: Ausstieg, Kennzahlen je Geschaeft, weitere Regeln, andere Werte
    override fun slRegelInfo(art: String) = when (art) {
        "MOMENTUM" -> "Checked only on the last trading day of the month: invested when the close is above the close 252 trading days earlier."
        "RSI_KURZ" -> "Buys only above the 200-day line, sells on a close above the 5-day line or after 10 trading days."
        "MONATSWECHSEL" -> "Invested from the last trading day of the month to the third of the next, so 12 buys and 12 sells per year."
        "ZIELVOLA" -> "Always invested. The share of the portfolio is 15% target volatility divided by the volatility of the last 20 trading days, at most 100%, reset at a gap of 10 percentage points. Trades counts buys and sells. After Moreira and Muir (2017), there with leverage."
        else -> ""
    }
    override val slAusstieg = "Exit"
    override fun slAusstiegName(art: String, wert: Int, stopp: Int) = when (art) {
        "STOPP" -> "Stop $wert%"
        "TRAILING" -> "Trailing stop $wert ATR"
        "ZEIT" -> "After $wert trading days"
        "ZIEL" -> "Target ${wert}R, stop $stopp%"
        else -> "As the rule"
    }
    override fun slAusstiegHinweis(art: String, wert: Int, stopp: Int) =
        "The exit applies on top of the sell signal. After it, the test buys again only on the next buy signal. The checks below run without it." + when (art) {
            "TRAILING" -> " ATR is the average daily range of the last 20 days. The stop sits $wert ATR below the highest close since the buy and only rises."
            "ZIEL" -> " R is the distance to the stop: ${wert}R is a ${wert * stopp}% gain."
            else -> ""
        }
    override val slTrendfilter = "Buy only above the 200-day line"
    override val slMonatsende = "Check at month end only"
    override fun slMehrRegeln(n: Int) = "$n more rules"
    override val slGv = "Win/loss ratio"
    override val slErwartung = "Expectancy per trade"
    override val slGewinnfaktor = "Profit factor"
    override val slHaltedauer = "Holding period in trading days"
    override val slVerlustserie = "Longest losing streak"
    override val slKennzahlen = "Figures per trade"
    override val slKennzahlenText = "A trade is a buy with its sell, the result counts after costs as a percentage of the stake.\n• Win/loss ratio: average win divided by average loss\n• Expectancy per trade: average result of all trades\n• Profit factor: sum of the wins divided by sum of the losses\n• Holding period: average over all trades\n• Longest losing streak: most losing trades in a row"
    override val slAndere = "Other securities"
    override val slAndereText = "Same exit and period, calculated separately for up to 20 securities. The lead is the rule's return per year minus buy and hold.\n\nPortfolio and watchlist are not a neutral selection: they often hold securities that have already done well."
    override fun slAndereKern(besser: Int, alle: Int) = "Beats buy and hold in $besser of $alle"
    override val slAndereSpalte = "Lead per year over buy and hold"
    override val slAndereKeine = "No other securities with enough prices in portfolio and watchlist."
    override fun st(key: String, w: List<String>) = StrategieTexte.text(false, key, w)
    override val pmWichtigste = "Top"
    override val pmWichtigsteAus = "• “Top” leaves out: sports, entertainment, daily weather, counts of posts and mentions, price bets on minutes, hours or the day, prices below 2% or above 98%"
    override fun su(key: String, w: List<String>) = StufenTexte.text(false, key, w)
    override fun km(key: String, w: List<String>) = KursmusterTexte.text(false, key, w)
    override fun ri(key: String, w: List<String>) = RisikoTexte.text(false, key, w)
    override fun flex(key: String, w: List<String>) = FlexTexte.text(false, key, w)
}
