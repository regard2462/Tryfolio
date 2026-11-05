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

object De : Strings {
    override val locale: Locale = Locale.GERMANY

    override val appName = "Tryfolio"
    override val back = "Zurück"
    override val cancel = "Abbrechen"
    override val save = "Speichern"
    override val ok = "OK"
    override val next = "Weiter"
    override val done = "Fertig"
    override val delete = "Löschen"
    override val close = "Schließen"
    override val apply = "Anwenden"
    override val change = "Ändern"
    override val buy = "Kaufen"
    override val sell = "Verkaufen"
    override val search = "Suche"
    override val transfer = "Überweisung"
    override val invest = "Investieren"
    override val all = "Alle"
    override val none = "Keine"
    override val today = "Heute"
    override val tomorrow = "Morgen"
    override val pieces = "Stk."
    override val perAnnum = "p. a."
    override val openEnd = "Open End"
    override val loading = "Wird geladen"
    override val open = "Offen"
    override val closed = "Geschlossen"
    override val no = "Nein"
    override val more = "Mehr"
    override val less = "Weniger"
    override val copied = "Kopiert"
    override val error = "Fehler"

    override fun rangeLabel(r: Range) = when (r) { Range.D1 -> "1T"; Range.W1 -> "1W"; Range.M1 -> "1M"; Range.M3 -> "3M"; Range.M6 -> "6M"; Range.Y1 -> "1J"; Range.MAX -> "Max" }
    override fun rangeCaption(r: Range) = when (r) { Range.D1 -> "Heute"; Range.W1 -> "1 Woche"; Range.M1 -> "1 Monat"; Range.M3 -> "3 Monate"; Range.M6 -> "6 Monate"; Range.Y1 -> "1 Jahr"; Range.MAX -> "Seit Beginn" }
    override val widgetWerte = "Werte"
    override val widgetGesperrt = "Gesperrt · zum Öffnen tippen"
    override val widgetLeer = "Keine Werte in der Watchlist"
    override fun widgetStand(zeit: String) = "Stand $zeit"
    override val widgetsTitel = "Widgets"
    override val widgetsUntertitel = "Depot, Werte, Einzelwert, Top & Flop, Dividenden, Märkte, Heatmap, Stimmung, News, Schnellzugriff"
    override val widgetsText = "Zehn Widgets für den Startbildschirm, Übersicht unten. Hinzufügen: lange auf den Startbildschirm drücken, dann Widgets › Tryfolio. Inhalt, Grund und Schrift wählst du über langes Drücken auf das Widget › Widget einstellen. Mit App-Sperre zeigen sie keine Beträge, mit „Beträge ausblenden“ nur Prozent."
    override val widgetsAuswahl = "Werte im Widget mit der Quelle Watchlist (ohne Auswahl die ganze Watchlist)"
    override val widgetsNurAndroid = "Widgets für den Startbildschirm gibt es in der App für Android."
    override val screenshotsTitel = "Screenshots"
    override val screenshotsText = "Ob Screenshots und Bildschirmaufnahmen der App möglich sind."
    override fun screenshotsOption(modus: String) = when (modus) { "erlaubt" -> "Überall erlaubt"; "gesperrt" -> "Überall gesperrt"; else -> "Erlaubt, außer bei Schlüsseln" }
    override fun screenshotsHinweis(modus: String) = when (modus) {
        "erlaubt" -> "Auch auf der Seite mit den API-Schlüsseln, die dort im Klartext stehen können. Danach wieder sperren."
        "gesperrt" -> "Keine Screenshots, keine Bildschirmaufnahmen, kein Bild in der App-Übersicht."
        else -> "Vorgabe: Screenshots überall, nur nicht auf der Seite mit den API-Schlüsseln."
    }
    override val widgetBestand = "Depot-Positionen"
    override val widgetMover = "Top & Flop heute"
    override val widgetsAkku = "Aktualisieren sich die Widgets bei geschlossener App nicht, bremst meist die Akku-Optimierung des Geräts. Bei Samsung: in den App-Einstellungen unter Akku „Nicht eingeschränkt“ wählen und die App aus den Listen der schlafenden Apps nehmen. Ein Tipp auf die Uhrzeit unten im Widget lädt sofort neu."
    override val widgetsAkkuOeffnen = "App-Einstellungen öffnen"
    override val widgetDividenden = "Dividenden"
    override val widgetKeinePositionen = "Keine Positionen im Depot"
    override val widgetMoverRuhig = "Heute noch keine Bewegung"
    override val widgetDivKeinBestand = "Keine Aktien oder ETFs im Depot"
    override val widgetDivKeine = "Keine Dividenden in den nächsten zwölf Monaten erwartet"
    override fun widgetDivErhalten(jahr: Int) = "Erhalten $jahr"
    override val widgetDivErwartet = "Erwartet in 12 Monaten"
    override val widgetsArten = "• Depot: Wert und Änderung heute, seit Jahresbeginn oder seit Kauf, dazu der Verlauf (ab zwei Zeilen Höhe), auf Wunsch nur in Prozent\n• Werte: Watchlist, Positionen oder eigene Liste, rollbar, mehrere Widgets mit verschiedenen Listen möglich\n• Einzelwert: ein Wert groß mit Kurs, Tagesänderung und Tagesverlauf\n• Top & Flop heute: beste und schlechteste Positionen\n• Dividenden: erhalten, erwartet, nächste Zahlungen\n• Märkte: DAX, S&P 500, Nasdaq 100, Euro Stoxx 50, Gold und Bitcoin\n• Heatmap: Markt, Depot oder Watchlist als Kacheln\n• Stimmung: Angst-und-Gier-Barometer für Aktien oder Krypto\n• News: wichtigste Nachrichten, alle Schlagzeilen, ein eigener Feed oder Schlagzeilen zu deinen größten Positionen\n• Schnellzugriff: Suche, Orders, Märkte und News\n\nEin Tipp auf den Titel oder den großen Wert schaltet zwischen Betrag und Prozent um."
    override val widgetEinstellung = "Widget einstellen"
    override val widgetQuelle = "Was soll das Widget zeigen?"
    override val widgetQuelleWatchlist = "Watchlist (Auswahl in der App unter Profil › Widgets)"
    override val widgetQuelleDepot = "Positionen im Depot, größte zuerst"
    override val widgetEinzelWahl = "Welchen Wert soll das Widget zeigen?"
    override val widgetEinstellungGesperrt = "Die App ist gesperrt. Entsperre sie, dann kannst du das Widget einstellen."
    override val widgetEinstellungLeer = "Noch keine Werte in Watchlist oder Depot."
    override val widgetZeitraum = "Zeitraum"
    override val widgetZeitJahr = "Seit Jahresbeginn"
    override val widgetZeitKauf = "Seit Kauf"
    override val widgetNurProzent = "Nur Prozent"
    override val widgetNurProzentHinweis = "Keine Beträge im Widget, etwa wenn andere auf das Gerät schauen. Ein Tipp auf den Wert im Widget schaltet ebenfalls um."
    override val widgetAnzeige = "Anzeige"
    override val widgetBetrag = "Änderung als Betrag"
    override val widgetBetragHinweis = "Tagesänderung je Stück statt in Prozent. Ein Tipp auf den Titel oder den Kurs im Widget schaltet ebenfalls um."
    override val widgetBarometer = "Barometer"
    override val widgetQuelleListe = "Eigene Liste"
    override val widgetListenName = "Name der Liste"
    override val widgetListeWahl = "Werte für diese Liste, in der Reihenfolge des Antippens:"
    override val widgetSortiert = "Nach Tagesänderung ordnen"
    override val widgetDarstellung = "Darstellung"
    override val widgetGrund = "Grund"
    override val widgetGrundVoll = "Deckend"
    override val widgetGrundHalb = "Durchscheinend"
    override val widgetGrundLeer = "Ohne Grund"
    override val widgetSchrift = "Schrift"
    override val widgetSchriftHell = "Hell (auf dunklem Grund)"
    override val widgetSchriftDunkel = "Dunkel (auf hellem Grund)"
    override val widgetStimmung = "Stimmung"
    override val widgetNewsTitel = "News zum Depot"
    override val widgetNewsKeine = "Gerade keine Schlagzeilen zu deinen Positionen"
    override val sperreTitel = "App-Sperre"
    override fun sperreStatus(art: String?) = when (art) { "pin" -> "Ein · PIN"; "passwort" -> "Ein · Passwort"; else -> "Aus" }
    override val sperreText = "Schützt die App mit PIN oder Passwort. Depot, Buchungen, Orders, Sparpläne, Watchlist, Alarme und Notizen liegen dann mit AES-256-GCM verschlüsselt auf dem Gerät. Die App sperrt sich beim Start und nach einer Minute im Hintergrund. Einstellungen und öffentliche Daten wie Kurse sind nicht verschlüsselt."
    override val sperreHinweisPin = "Eine PIN lässt sich leichter erraten als ein Passwort. Für echten Schutz ein Passwort mit mindestens 10 Zeichen wählen."
    override val sperreVergessenHinweis = "Ohne PIN oder Passwort lassen sich die verschlüsselten Daten nicht öffnen, auch nicht von uns. Sichere das Depot vorher (Profil › Depot exportieren)."
    override val sperrePinFestlegen = "PIN festlegen"
    override val sperrePasswortFestlegen = "Passwort festlegen"
    override val sperreAendern = "PIN oder Passwort ändern"
    override val sperreAusschalten = "Sperre ausschalten"
    override val sperreAlt = "Bisherige PIN bzw. bisheriges Passwort"
    override fun sperreNeu(pin: Boolean) = if (pin) "Neue PIN (4 bis 8 Ziffern)" else "Neues Passwort (mindestens 8 Zeichen)"
    override val sperreWiederholen = "Wiederholen"
    override val sperreUngleich = "Die beiden Eingaben stimmen nicht überein."
    override fun sperreZuKurz(pin: Boolean) = if (pin) "Die PIN braucht 4 bis 8 Ziffern." else "Das Passwort braucht mindestens 8 Zeichen."
    override val sperreEingerichtet = "App-Sperre eingeschaltet, Daten verschlüsselt"
    override val sperreGeaendert = "Geändert"
    override val sperreAusgeschaltet = "App-Sperre ausgeschaltet"
    override val sperreGesperrt = "Gesperrt"
    override val sperreEntsperren = "Entsperren"
    override fun sperreFalsch(versuch: Int) = if (versuch <= 0) "Das stimmt nicht." else "Das stimmt nicht (Versuch $versuch). Ab dem fünften Fehlversuch wächst die Wartezeit."
    override fun sperreWarten(sekunden: Long) = "Bitte noch $sekunden Sekunden warten."
    override val sperreSchreibFehler = "Speichern fehlgeschlagen, die Sperre bleibt an. Bitte Speicherplatz prüfen."
    override val sperreVergessen = "PIN oder Passwort vergessen?"
    override val sperreZuruecksetzenTitel = "Daten löschen und neu beginnen?"
    override val sperreZuruecksetzenText = "Zurücksetzen löscht Depot, Buchungen und Watchlist auf diesem Gerät. Die Sperre wird ausgeschaltet und die App beginnt mit einem neuen Übungsdepot. Ein gesichertes Depot lässt sich danach importieren. Das lässt sich nicht rückgängig machen."
    override val sperreZuruecksetzen = "Löschen und neu beginnen"
    override val sperreDatenFehlen = "Die verschlüsselten Daten ließen sich nicht lesen."
    override val sperreNurApp = "Die App-Sperre gibt es in der App für Android, Windows und Linux."
    override val sperreSpeichern = "Einschalten"
    override val ppExport = "Für Portfolio Performance exportieren"
    override fun ppExportSub(n: Int) = "$n Buchungen als CSV-Datei"
    override val ppHinweis = "Portfolio Performance ist ein kostenloses Programm für Windows, macOS und Linux. Import dort: Datei › Importieren › CSV-Dateien, Art „Kontoumsätze“. Gebühren stehen bei der Order, übernommene Positionen als Einlage und Kauf."
    override val ppSeite = "portfolio-performance.info öffnen"
    override fun ppFertig(n: Int) = "$n Buchungen gesichert"
    override val chartProfi = "Erweiterter Chart"
    override fun chartArt(art: String) = when (art) { "linie" -> "Linie"; "heikin" -> "Heikin-Ashi"; else -> "Kerzen" }
    override val chartHandelsplatzSuche = "Handelsplätze werden gesucht"
    override val chartHandelsplatzLeer = "Keine weiteren Handelsplätze gefunden."
    override val chartVergleich = "Vergleich"
    override val chartVergleichTitel = "Vergleichen (bis zu 8 Werte)"
    override val chartVergleichSuche = "Wert, Index oder ETF suchen"
    override val chartVergleichHinweis = "Im Vergleich zeigt der Chart die Veränderung in Prozent seit Beginn des Zeitraums. Zeichnungen sind dann ausgeblendet."
    override val chartVergleichVoll = "Es lassen sich höchstens 8 Werte vergleichen."
    override val chartIndikatorenKurs = "Im Kurschart"
    override val chartIndikatorenUnten = "Unter dem Chart"
    override fun chartIndikatorName(key: String) = when (key) {
        "sma20" -> "Ø 20 (SMA)"; "sma50" -> "Ø 50 (SMA)"; "sma200" -> "Ø 200 (SMA)"; "ema20" -> "EMA 20"; "ema50" -> "EMA 50"
        "bb" -> "Bollinger-Bänder"; "keltner" -> "Keltner-Kanal"; "donchian" -> "Donchian-Kanal"; "ichimoku" -> "Ichimoku"
        "sar" -> "Parabolic SAR"; "vwap" -> "VWAP"; "volumen" -> "Volumen"; "rsi" -> "RSI 14"; "macd" -> "MACD"
        "stoch" -> "Stochastik"; "atr" -> "ATR 14"; "obv" -> "OBV"; "cci" -> "CCI 20"; "williams" -> "Williams %R"
        "mfi" -> "MFI 14"; "adx" -> "ADX 14"; "roc" -> "Momentum (ROC 12)"; "aroon" -> "Aroon 25"; "cmf" -> "Chaikin Money Flow"
        else -> key
    }
    override fun chartIndikatorInfo(key: String) = when (key) {
        "sma20", "sma50", "sma200" -> "Durchschnitt der letzten Schlusskurse. Glättet den Verlauf und zeigt den Trend."
        "ema20", "ema50" -> "Durchschnitt, der jüngere Kurse stärker gewichtet und schneller reagiert."
        "bb" -> "Durchschnitt über 20 mit zwei Standardabweichungen darüber und darunter: weit = unruhig."
        "keltner" -> "EMA 20 mit zweimal der mittleren Spanne (ATR) darüber und darunter."
        "donchian" -> "Höchstes Hoch und tiefstes Tief der letzten 20 Kerzen. Ein Bruch gilt als Ausbruch."
        "ichimoku" -> "Japanisches Trendsystem: Umkehr- und Basislinie, dazu die Wolke als Unterstützung."
        "sar" -> "Punkte, die dem Trend als Stopp folgen. Wechseln sie die Seite, dreht der Trend."
        "vwap" -> "Mit dem Umsatz gewichteter Durchschnittskurs des Tages, vor allem im Tageschart."
        "volumen" -> "Gehandelte Stück je Kerze, grün an steigenden, rot an fallenden."
        "rsi" -> "Stärke der Bewegung von 0 bis 100. Über 70 gilt als überkauft, unter 30 als überverkauft."
        "macd" -> "Abstand zweier Durchschnitte (12 und 26) mit Signallinie. Kreuzen sie sich, kann der Trend drehen."
        "stoch" -> "Lage des Kurses in seiner Spanne der letzten 14 Kerzen: über 80 hoch, unter 20 tief."
        "atr" -> "Mittlere Spanne je Kerze: wie stark der Kurs schwankt."
        "obv" -> "Umsatz einer Kerze kommt dazu, wenn sie höher schließt als die vorige, und wird abgezogen, wenn sie tiefer schließt. Bestätigt Trends."
        "cci" -> "Abstand vom Durchschnitt: über +100 stark, unter −100 schwach."
        "williams" -> "Lage des Kurses unter dem Hoch der letzten 14 Kerzen: über −20 hoch, unter −80 tief."
        "mfi" -> "Wie der RSI, aber mit dem Umsatz gewichtet. Über 80 überkauft, unter 20 überverkauft."
        "adx" -> "Stärke des Trends (über 25 deutlich), +DI und −DI zeigen die Richtung."
        "roc" -> "Veränderung in Prozent gegen den Kurs vor 12 Kerzen."
        "aroon" -> "Wie frisch das letzte Hoch und Tief sind. Zeigt Beginn und Ende von Trends."
        "cmf" -> "Kaufdruck (über 0) oder Verkaufsdruck (unter 0) aus Kurs und Umsatz."
        else -> ""
    }
    override fun chartWerkzeug(key: String) = when (key) {
        "linie" -> "Trendlinie"; "waagerecht" -> "Waagerechte"; "notiz" -> "Notiz"; "emoji" -> "Emoji"
        "zurueck" -> "Rückgängig"; else -> "Alle löschen"
    }
    override fun chartWerkzeugHinweis(key: String, zweiterPunkt: Boolean) = when (key) {
        "linie" -> if (zweiterPunkt) "Tippe auf das Ende der Linie." else "Tippe auf den Anfang der Linie."
        "waagerecht" -> "Tippe auf die Höhe der Linie."
        "notiz" -> "Tippe auf die Stelle für die Notiz."
        else -> "Tippe auf die Stelle für das Emoji."
    }
    override val chartLangDruecken = "Lange drücken entfernt eine Zeichnung."
    override fun chartAllesLoeschen(name: String) = "Alle Zeichnungen für $name löschen?"
    override val chartKeineDaten = "Keine Kursdaten für diesen Zeitraum."
    override val heatmap = "Heatmap"
    override val heatmapDepot = "Mein Depot"
    override val heatmapMarkt = "Aktienmarkt"
    override val heatmapBranchen = "Nach Branche"
    override val heatmapRegionen = "Nach Region"
    override fun heatmapZaehler(steigen: Int, fallen: Int) = "$steigen steigen · $fallen fallen"
    override val heatmapLeer = "Gerade keine Kurse erreichbar. Später noch einmal versuchen."
    override val heatmapOhneBranche = "Sonstige"
    override val zeitraumEigener = "Eigener Zeitraum"
    override val zeitraumTitel = "Zeitraum wählen"
    override fun metricLabel(m: Metric) = when (m) {
        Metric.TODAY_PCT -> "Tagestrend relativ"; Metric.TODAY_ABS -> "Tagestrend absolut"
        Metric.SINCE_BUY_PCT -> "Seit Kauf relativ"; Metric.SINCE_BUY_ABS -> "Seit Kauf absolut"
    }
    override fun metricShort(m: Metric) = if (m.isSinceBuy) "Seit Kauf" else "Tagestrend"
    override fun watchMetricLabel(m: WatchMetric) = when (m) {
        WatchMetric.TODAY_PCT -> "Tagestrend relativ"; WatchMetric.TODAY_ABS -> "Tagestrend absolut"
        WatchMetric.SINCE_ADDED_PCT -> "Seit Hinzufügen relativ"; WatchMetric.SINCE_ADDED_ABS -> "Seit Hinzufügen absolut"
        WatchMetric.SINCE_FIRST_PCT -> "Seit erstem Hinzufügen relativ"; WatchMetric.SINCE_FIRST_ABS -> "Seit erstem Hinzufügen absolut"
    }
    override val metricTitle = "Kennzahl"
    override fun typeLabel(t: AssetType) = when (t) { AssetType.STOCK -> "Aktie"; AssetType.ETF -> "ETF"; AssetType.CRYPTO -> "Kryptowert"; AssetType.INDEX -> "Index"; AssetType.COMMODITY -> "Rohstoff"; AssetType.CURRENCY -> "Währung" }
    override fun inDays(n: Long) = "in $n Tagen"
    override val inOneMonth = "in 1 Monat"
    override fun inMonths(n: Long) = "in $n Monaten"
    override fun daysAgo(n: Long) = if (n == 1L) "gestern" else "vor $n Tagen"
    override fun minutesAgo(n: Long) = "vor $n Min."
    override fun hoursAgo(n: Long) = "vor $n Std."
    override val justNow = "gerade eben"
    override fun weekday(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "Montag"; DayOfWeek.TUESDAY -> "Dienstag"; DayOfWeek.WEDNESDAY -> "Mittwoch"; DayOfWeek.THURSDAY -> "Donnerstag"
        DayOfWeek.FRIDAY -> "Freitag"; DayOfWeek.SATURDAY -> "Samstag"; DayOfWeek.SUNDAY -> "Sonntag"
    }
    override fun closedOpensToday(time: String) = "Börse geschlossen · öffnet heute $time Uhr"
    override fun closedOpensOn(weekday: String, time: String) = "Börse geschlossen · öffnet $weekday $time Uhr"

    override val portfolio = "Depot"
    override val cash = "Cash"
    override val openProfile = "Profil öffnen"
    override val brokerage = "Brokerage"
    override val securities = "Wertpapiere"
    override val cashAccount = "Verrechnungskonto"
    override val total = "Gesamt"
    override val reloadPrices = "Kurse erneut laden"
    override val provisionalValuation = "Vorläufig zum Einstand bewertet · Kurse werden geladen"
    override val switchMetric = "Kennzahl wechseln"
    override val watchlist = "Watchlist"
    override val savingsPlans = "Sparpläne"
    override val orders = "Orders"
    override val analytics = "Analytics"
    override val statistics = "Statistik"
    override val investments = "Anlagen"
    override val discoverInvestments = "Anlagen entdecken"
    override val groupStocksEtfs = "Aktien & ETFs"
    override val noWatchlist = "Noch keine Werte auf der Watchlist."
    override val knockedOut = "Ausgeknockt"
    override val available = "Verfügbar"
    override fun reservedForOrders(amount: String) = "Verfügbar · $amount für offene Orders reserviert"
    override val deposit = "Einzahlen"
    override val withdraw = "Auszahlen"
    override val interest = "Zinsen"
    override val transactions = "Transaktionen"
    override val noTransactions = "Noch keine Transaktionen."
    override val incoming = "Eingehend"
    override val outgoing = "Ausgehend"
    override val since = "seit"

    override val year = "Jahr"
    override val allTime = "Gesamt"
    override val categories = "Kategorien"
    override fun statNode(id: String) = when (id) {
        "INCOME" -> "Einnahmen"; "EXPENSES" -> "Ausgaben"; "INVESTMENTS" -> "Anlagen"
        "SALES" -> "Verkäufe"; "DEPOSITS" -> "Einzahlungen"; "EARNINGS" -> "Erträge"; "REFUNDS" -> "Rückzahlungen"
        "DIVIDENDS" -> "Dividenden"; "INTEREST" -> "Zinsen"
        "FEES" -> "Gebühren"; "TAXES" -> "Steuern"; "WITHDRAWALS" -> "Auszahlungen"
        "PURCHASES" -> "Käufe"; "PLANS" -> "Sparpläne"
        else -> id
    }
    override val netCashflow = "Netto-Cashflow"
    override val investRate = "Investitionsquote"
    override val noStatistics = "In diesem Zeitraum gab es keine Buchungen."
    override fun bookingsCount(n: Int) = if (n == 1) "1 Buchung" else "$n Buchungen"
    override val averagePerMonth = "Ø je Monat"

    override fun entriesCount(n: Int) = if (n == 1) "1 Eintrag" else "$n Einträge"
    override val sortByName = "Nach Name"
    override val sortByChange = "Nach Veränderung"
    override val sortByPrice = "Nach Kurs"
    override val add = "Hinzufügen"
    override fun addedOn(date: String) = "seit $date"
    override val removeFromWatchlist = "Von der Watchlist entfernen"
    override val addToWatchlist = "Zur Watchlist"

    override val searchPlaceholder = "Suche nach Aktien, ETFs und mehr"
    override val topMovers = "Größte Bewegungen"
    override val popular = "Beliebt"
    override val stocks = "Aktien"
    override val etfs = "ETFs"
    override val crypto = "Krypto"
    override val indices = "Indizes"
    override val results = "Treffer"
    override val otherVenues = "Weitere Börsenplätze"
    override val searching = "Wertpapiere werden gesucht"
    override val searchUnavailable = "Suche gerade nicht erreichbar. Später noch einmal versuchen."
    override val noResults = "Keine passenden Wertpapiere gefunden."
    override val clear = "Leeren"

    override val security = "Wertpapier"
    override val yourPosition = "Deine Position"
    override val quantity = "Anzahl"
    override val avgPrice = "Einstandskurs"
    override val value = "Wert"
    override val result = "Ergebnis"
    override val information = "Informationen"
    override val rohstoffTitel = "Rohstoff erklärt"
    override val etfTitel = "Was im ETF steckt"
    override val etfTop = "Größte Positionen"
    override val etfBranchen = "Branchen"
    override fun etfKlassen(aktien: String, anleihen: String, bar: String) = listOfNotNull(
        aktien.takeIf { it.isNotBlank() }?.let { "Aktien $it" }, anleihen.takeIf { it.isNotBlank() }?.let { "Anleihen $it" },
        bar.takeIf { it.isNotBlank() }?.let { "Bargeld $it" },
    ).joinToString(" · ")
    override val etfHinweis = "Yahoo nennt nur die zehn größten Positionen. Ein MSCI-World-ETF und ein S&P-500-ETF enthalten zu großen Teilen dieselben Firmen."
    override val etfDepotTitel = "So viel steckt in deinem Depot"
    override fun etfDepotZeile(direkt: String, ueber: String, etfs: Int) = listOfNotNull(
        direkt.takeIf { it.isNotBlank() }?.let { "direkt $it" },
        "über " + (if (etfs == 1) "1 ETF" else "$etfs ETFs") + " $ueber",
    ).joinToString(", ")
    override val etfDepotHinweis = "Anteil am ganzen Depot, direkt gehalten und über alle gehaltenen ETFs zusammen. Gezählt sind nur die zehn größten Positionen je ETF, die wahren Anteile sind etwas höher. Deutsche Kürzel für US-Aktien (etwa APC.DE für Apple) werden nicht zusammengezählt."
    override fun rohstoffAbschnitte(schluessel: String, termin: Boolean): List<Pair<String, String>> {
        val t = RohstoffTexteDe.TEXTE[schluessel] ?: return emptyList()
        return RohstoffTexteDe.TITEL.zip(t) + if (termin) listOf("Zum angezeigten Kurs" to RohstoffTexteDe.TERMIN) else emptyList()
    }
    override val keyStats = "Wichtigste Statistiken"
    override val bid = "Geld"
    override val ask = "Brief"
    override val spread = "Spanne"
    override val openPrice = "Eröffnung"
    override val dayHigh = "Tageshoch"
    override val dayLow = "Tagestief"
    override val prevClose = "Vortag"
    override val high52 = "52-Wochen-Hoch"
    override val low52 = "52-Wochen-Tief"
    override val marketCap = "Marktkapitalisierung"
    override val enterpriseValue = "Unternehmenswert"
    override val peRatio = "Kurs-Gewinn-Verhältnis"
    override val forwardPe = "KGV (erwartet)"
    override val eps = "Gewinn je Aktie"
    override val dividendYield = "Dividendenrendite"
    override val volume = "Volumen"
    override fun priceIn(ccy: String) = "Kurs in $ccy"
    override val volatility1y = "Schwankung (1 Jahr)"
    override val beta = "Beta"
    override val isin = "ISIN"
    override val isinCopied = "ISIN kopiert"
    override val isinKopieren = "ISIN kopieren"
    override fun kennzahlErklaerung(label: String): String? = when (label) {
        bid -> "Der Preis, zu dem du die Aktie gerade verkaufen kannst."
        ask -> "Der Preis, zu dem du die Aktie gerade kaufen kannst. Er liegt etwas über dem Geldkurs."
        spread -> "Der Abstand zwischen Kauf- und Verkaufspreis. So viel verlierst du, wenn du sofort kaufst und gleich wieder verkaufst. Je kleiner, desto günstiger ist der Handel."
        openPrice -> "Der erste Kurs des heutigen Handelstags."
        dayHigh -> "Der höchste Kurs heute."
        dayLow -> "Der niedrigste Kurs heute."
        prevClose -> "Der Schlusskurs des letzten Handelstags. Daran wird gemessen, wie viel die Aktie heute gestiegen oder gefallen ist."
        high52 -> "Der höchste Kurs der letzten zwölf Monate. Zusammen mit dem Tief zeigt er, wie stark die Aktie in einem Jahr geschwankt ist."
        low52 -> "Der niedrigste Kurs der letzten zwölf Monate. Zusammen mit dem Hoch zeigt er, wie stark die Aktie in einem Jahr geschwankt ist."
        marketCap -> "Was alle Aktien der Firma zusammen an der Börse wert sind: Kurs mal Zahl der Aktien. Damit vergleicht man, wie groß Firmen sind."
        enterpriseValue -> "Börsenwert plus Schulden minus Bargeld. Ungefähr so viel müsste man zahlen, um die ganze Firma samt Schulden zu kaufen."
        peRatio -> "Kurs geteilt durch den Gewinn je Aktie der letzten zwölf Monate. Ein KGV von 15 heißt: Du zahlst 15 Jahresgewinne. Ein niedriger Wert kann günstig bedeuten, ein hoher heißt meist, dass viel Wachstum erwartet wird."
        forwardPe -> "Wie das KGV, aber mit dem Gewinn, den Analysten für die nächsten zwölf Monate erwarten."
        eps -> "Der Gewinn der Firma in den letzten zwölf Monaten, geteilt durch die Zahl ihrer Aktien. Ist er negativ, hat die Firma Verlust gemacht."
        dividendYield -> "Die jährliche Ausschüttung im Verhältnis zum Kurs. 3 % heißt: Für 100 € in der Aktie bekommst du etwa 3 € im Jahr, solange die Dividende gleich bleibt."
        volume -> "Wie viele Aktien heute gehandelt wurden. Ein hohes Volumen zeigt, dass gerade viel los ist."
        volatility1y -> "Wie stark der Kurs im letzten Jahr geschwankt hat. Über 40 % ist sehr unruhig, unter 20 % eher ruhig."
        beta -> "Wie stark die Aktie im Mittel mit dem Markt mitgeht. 1 heißt: Steigt oder fällt der Markt um 1 %, bewegt sich die Aktie im Mittel ebenso. 2 heißt doppelt so stark, 0,5 halb so stark. Eine Aktie kann stark schwanken und trotzdem ein kleines Beta haben, wenn ihre Schwankungen wenig mit dem Markt zu tun haben."
        isin -> "Die internationale Kennnummer des Wertpapiers. Sie ist weltweit eindeutig, damit findest du es bei jeder Bank."
        kind -> "Die Art des Wertpapiers, zum Beispiel Aktie, ETF oder Kryptowert."
        sector -> "Der grobe Wirtschaftsbereich der Firma, zum Beispiel Technologie oder Gesundheit."
        industry -> "Die genauere Branche innerhalb des Sektors."
        country -> "Das Land, in dem die Firma ihren Sitz hat."
        employees -> "Wie viele Menschen bei der Firma arbeiten, laut dem letzten Bericht."
        exchange -> "Die Börse, von der der angezeigte Kurs stammt."
        bkKuv -> "Börsenwert geteilt durch den Umsatz der letzten zwölf Monate. Hilft bei Firmen, die noch keinen Gewinn machen. Ein KUV von 2 heißt: Die Firma kostet zwei Jahresumsätze. Was als hoch gilt, hängt stark von der Branche ab."
        priceToBook -> "Kurs geteilt durch den Buchwert je Aktie, also das Eigenkapital laut Bilanz. Unter 1 kostet die Aktie weniger, als laut Bilanz an Eigenkapital dahintersteht."
        bkEvEbitda -> "Unternehmenswert (Börsenwert plus Schulden minus Bargeld) geteilt durch den Gewinn vor Zinsen, Steuern und Abschreibungen. Die Schulden zählen mit, deshalb lassen sich Firmen mit vielen und wenigen Schulden besser vergleichen."
        bkPeg -> "KGV geteilt durch das erwartete Gewinnwachstum. Um 1 gilt die Aktie für ihr Wachstum als fair bewertet, deutlich darüber als teuer. Fehlt der Wert bei Yahoo, rechnet der Aktienfinder mit dem KGV der letzten zwölf Monate und dem Gewinnwachstum zum nächsten Jahr."
        bkFcfRendite -> "Freier Cashflow der letzten zwölf Monate (operativer Cashflow minus Investitionen) geteilt durch den Börsenwert. So viel Geld bleibt im Jahr übrig, gemessen an dem, was die ganze Firma an der Börse kostet."
        returnOnEquity -> "Gewinn geteilt durch das Eigenkapital. Zeigt, wie gut die Firma mit dem Geld ihrer Eigentümer wirtschaftet. Über 15 % gilt als stark."
        bkGesamtkapitalRendite -> "Gewinn geteilt durch das ganze Vermögen der Firma. Zeigt, wie viel sie aus Fabriken, Geräten und Geldmitteln herausholt."
        grossMargin -> "Anteil vom Umsatz, der nach den direkten Kosten der Ware übrig bleibt. Eine hohe Bruttomarge spricht für Preismacht."
        operatingMargin -> "Anteil vom Umsatz, der nach allen Kosten des laufenden Geschäfts übrig bleibt, vor Zinsen und Steuern."
        bkEbitdaMarge -> "Anteil vom Umsatz, der vor Zinsen, Steuern und Abschreibungen übrig bleibt."
        profitMargin -> "Anteil vom Umsatz, der am Ende als Gewinn bleibt."
        bkVerschuldung -> "Schulden geteilt durch das Eigenkapital. 1 heißt: so viele Schulden wie Eigenkapital. Hohe Werte machen eine Firma anfällig, wenn die Zinsen steigen oder das Geschäft schwächelt."
        bkLiquiditaet -> "Kurzfristiges Vermögen geteilt durch kurzfristige Schulden. Über 1 kann die Firma die Rechnungen des nächsten Jahres aus vorhandenen Mitteln bezahlen."
        totalCash -> "Bargeld und schnell verkaufbare Anlagen der Firma."
        totalDebt -> "Alle verzinsten Schulden der Firma."
        freeCashflow -> "Geld, das nach den Ausgaben für das laufende Geschäft und für Investitionen übrig bleibt. Daraus zahlt eine Firma Dividenden, kauft Aktien zurück oder tilgt Schulden."
        dividendPerShare -> "Was die Firma im Jahr je Aktie ausschüttet."
        payoutRatio -> "Anteil vom Gewinn, der als Dividende ausgezahlt wird. Über 100 % zahlt die Firma mehr aus, als sie verdient."
        bkDividendeFuenf -> "Die mittlere Dividendenrendite der letzten fünf Jahre. Liegt die heutige deutlich darüber, ist die Aktie billiger geworden oder die Dividende gestiegen."
        bkDividendenWachstum -> "Wie stark die Dividende je Aktie in den letzten Jahren pro Jahr gestiegen ist."
        bkLeerQuote -> "Anteil der frei handelbaren Aktien, die leer verkauft sind. Leerverkäufer wetten auf fallende Kurse. Über 10 % gilt als hoch."
        bkLeerTage -> "Wie viele Handelstage es bei normalem Umsatz dauern würde, bis alle Leerverkäufer ihre Aktien zurückgekauft hätten."
        bkInsider -> "Anteil der Aktien, die Gründer, Vorstand und Aufsichtsrat halten."
        bkInstitutionen -> "Anteil der Aktien bei Fonds, Versicherern, Banken und anderen großen Anlegern."
        bkAktien -> "Wie viele Aktien es von der Firma gibt."
        bkAktienVeraenderung -> "Wie sich die Zahl der Aktien zum Vorjahr verändert hat. Weniger Aktien heißt meist Rückkäufe, das erhöht deinen Anteil an der Firma. Mehr Aktien verwässern ihn."
        bkZielSpanne -> "Das niedrigste und das höchste Kursziel der Analysten für die nächsten zwölf Monate."
        bkZielMedian -> "Die Hälfte der Analysten hat ein höheres Kursziel, die andere Hälfte ein niedrigeres."
        bkGewinnErwartet -> "Der Gewinn je Aktie, den Analysten für die nächsten zwölf Monate erwarten."
        bkGewinnWachstumJahr -> "Wie stark Analysten den Gewinn je Aktie im laufenden Geschäftsjahr wachsen sehen."
        bkGewinnWachstumFolge -> "Wie stark Analysten den Gewinn je Aktie im nächsten Geschäftsjahr wachsen sehen."
        bkUmsatzWachstumFolge -> "Wie stark Analysten den Umsatz im nächsten Geschäftsjahr wachsen sehen."
        bkNaechsteZahlen -> "Wann die Firma ihre nächsten Geschäftszahlen veröffentlicht. Rund um diesen Tag schwankt der Kurs oft stärker."
        bkJahresVeraenderung -> "Wie sich der Kurs in den letzten zwölf Monaten verändert hat, in der Handelswährung."
        revenueGrowth -> "Veränderung des Umsatzes im letzten Quartal gegenüber dem Vorjahresquartal."
        kvZeile("KURSZIEL") -> "Abstand des mittleren Kursziels der Analysten zum aktuellen Kurs. Kursziele liegen oft daneben und werden dem Kurs häufig nachgezogen."
        bkKursCashflow -> "Börsenwert geteilt durch den operativen Cashflow der letzten zwölf Monate. Je niedriger der Wert, desto weniger kostet das Geld, das das Geschäft einbringt."
        bkZinsdeckung -> "Gewinn vor Zinsen und Steuern geteilt durch die Zinskosten des letzten Geschäftsjahres. Zeigt, wie oft die Firma ihre Zinsen verdient. Unter 2 wird es knapp. Ohne gemeldete Zinskosten bleibt das Feld leer."
        bkRoic -> "Gewinn vor Zinsen nach Steuern geteilt durch das investierte Kapital. Zeigt, wie viel die Firma aus dem Geld macht, das Eigentümer und Kreditgeber eingesetzt haben. Ab 15 % gilt der Wert als stark. Das investierte Kapital ist hier Eigenkapital plus Schulden, Bargeld ist nicht abgezogen."
        bkOwnerEarnings -> "Vereinfacht nach Buffett: operativer Cashflow minus alle Investitionen in Anlagen, geteilt durch den Börsenwert. Buffett zieht nur die Investitionen ab, die das Geschäft erhalten. Bei wachsenden Firmen liegt der Wert deshalb eher zu niedrig."
        else -> null
    }
    override val kind = "Gattung"
    override val sector = "Sektor"
    override val industry = "Branche"
    override val country = "Land"
    override val employees = "Mitarbeitende"
    override val website = "Website"
    override val exchange = "Handelsplatz"
    override val priceAlerts = "Preisalarme"
    override fun nActive(n: Int) = "$n aktiv"
    override val derivatives = "Derivate"
    override fun leverageRange(custom: Int, factor: Int) = "Hebel bis $custom×, Faktoren bis $factor×"
    override val news = "News"
    override val events = "Ereignisse"
    override val financials = "Finanzen"
    override val yearly = "Jährlich"
    override val quarterly = "Quartal"
    override val insights = "Einschätzungen"
    override val analysts = "Analysten"
    override fun fromEstimates(n: Int) = " aus $n Einschätzungen"
    override fun targetPrice(price: String) = ", Kursziel $price"
    override val toTarget = "zum Kursziel"
    override val revenue = "Umsatz"
    override val ebitda = "EBITDA"
    override val netIncome = "Nettogewinn"
    override val grossProfit = "Bruttogewinn"
    override val operatingIncome = "Operatives Ergebnis"
    override val savingsPlan = "Sparplan"
    override val eventEarnings = "Geschäftszahlen"
    override val eventExDividend = "Ex-Dividende"
    override val eventDividendPayment = "Dividendenzahlung"
    override val pastEvents = "Vergangene Ereignisse"
    override val upcomingEvents = "Anstehend"
    override val noEvents = "Keine Termine bekannt."
    override fun epsActualVsEstimate(actual: String, estimate: String) = "Gewinn je Aktie $actual · erwartet $estimate"
    override val description = "Beschreibung"
    override val sourceWikipedia = "Quelle: Wikipedia, Lizenz CC BY-SA 4.0"
    override val sourceYahoo = "Quelle: Yahoo Finance"
    override fun recommendation(key: String) = when (key.lowercase()) {
        "strong_buy" -> "Klare Kaufempfehlung"; "buy" -> "Kaufempfehlung"; "hold" -> "Halteempfehlung"
        "sell" -> "Verkaufsempfehlung"; "strong_sell" -> "Klare Verkaufsempfehlung"; else -> "Keine klare Empfehlung"
    }
    override val knockedOutBanner = "Knock-out-Ereignis"
    override fun knockedOutSince(date: String, residual: String) = "Der Basiswert hat am $date die Knock-out-Schwelle berührt. Das Produkt ist beendet und wird mit dem Restwert von $residual je Stück zurückgezahlt."

    override val income = "Gewinn und Verlust"
    override val balance = "Bilanz"
    override val cashflow = "Cashflow"
    override val margins = "Margen"
    override val grossMargin = "Bruttomarge"
    override val operatingMargin = "Operative Marge"
    override val profitMargin = "Nettomarge"
    override val totalCash = "Liquide Mittel"
    override val totalDebt = "Schulden"
    override val freeCashflow = "Freier Cashflow"
    override val operatingCashflow = "Operativer Cashflow"
    override val returnOnEquity = "Eigenkapitalrendite"
    override val totalAssets = "Bilanzsumme"
    override val totalLiabilities = "Verbindlichkeiten"
    override val equity = "Eigenkapital"
    override val revenueGrowth = "Umsatzwachstum"
    override val earningsGrowth = "Gewinnwachstum"
    override val payoutRatio = "Ausschüttungsquote"
    override val dividendPerShare = "Dividende je Aktie"
    override val priceToBook = "Kurs-Buchwert-Verhältnis"
    override val noFinancials = "Für diesen Wert liegen keine Finanzdaten vor."
    override fun reportingCurrency(ccy: String) = "Angaben in $ccy"
    override val epsHistory = "Gewinn je Aktie je Quartal"
    override val estimate = "Erwartet"
    override val actual = "Tatsächlich"

    override fun newsSource(source: String) = "Quelle: $source"
    override val openInBrowser = "Im Browser öffnen"
    override val showOriginal = "Originalseite anzeigen"
    override val showText = "Nur Text anzeigen"
    override val articleLoading = "Artikel wird geladen"
    override val articleNoText = "Den Text dieser Seite kann die App nicht auslesen."

    override val calToggle = "Handelskalender"
    override fun calRealizedIn(month: String) = "Realisiert im $month"
    override val calPrevMonth = "Voriger Monat"
    override val calNextMonth = "Nächster Monat"
    override val calWeekdays = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    override val calRealized = "Realisiert"
    override val calTrades = "Geschäfte"
    override fun calWinsLosses(wins: Int, losses: Int) = "$wins Plus · $losses Minus"
    override fun calExpectedAmount(amount: String) = "erwartet $amount"
    override val calLegendReceived = "Dividende erhalten"
    override val calLegendExpected = "Dividende erwartet"
    override val calClosedTrades = "Abgeschlossene Geschäfte"
    override val calDayResult = "Tagesergebnis"
    override val calFees = "Gebühren"
    override val calAfterCosts = "Nach Gebühren und Steuern"
    override val calPositions = "Geschlossene Positionen"
    override val calTradeExpiry = "Verfall"
    override fun calTradeSell(qty: String, price: String) = "Verkauf · $qty Stk. zu $price"
    override val calDivExpected = "erwartet, vor Steuern"
    override fun calDivReceived(qty: String, perShare: String) = "$qty Stk. × $perShare · nach Steuern"

    override val overview = "Übersicht"
    override val amount = "Betrag"
    override val shares = "Stück"
    override val everything = "Alles"
    override val market = "Bestens"
    override val limit = "Limit"
    override val stop = "Stopp"
    override val trailingStop = "Trailing-Stopp"
    override val limitPrice = "Limitpreis"
    override val stopPrice = "Stoppreis"
    override val trailDistance = "Abstand"
    override val setValue = "festlegen"
    override val validity = "Gültigkeit"
    override fun validityLabel(v: Validity) = if (v == Validity.DAY) "Tagesgültig" else "360 Tage"
    override val limitBuyHint = "Wird ausgeführt, sobald der Kurs das Limit erreicht oder unterschreitet."
    override val limitSellHint = "Wird ausgeführt, sobald der Kurs das Limit erreicht oder überschreitet."
    override val stopBuyHint = "Wird ausgelöst, sobald der Kurs die Marke überschreitet."
    override val stopSellHint = "Wird ausgelöst, sobald der Kurs die Marke unterschreitet."
    override val trailingSellHint = "Der Stoppreis folgt dem Kurs nach oben im gewählten Abstand und bleibt stehen, wenn der Kurs fällt. Unterschreitet der Kurs den Stoppreis, wird zum Marktkurs verkauft."
    override val trailingBuyHint = "Der Stoppreis folgt dem Kurs nach unten im gewählten Abstand. Überschreitet der Kurs den Stoppreis, wird zum Marktkurs gekauft."
    override val closedOrderHint = " · Limit-, Stopp- und Trailing-Orders kannst du jetzt anlegen."
    override val enterLimit = "Limitpreis eingeben"
    override val enterStop = "Stoppreis eingeben"
    override val enterTrail = "Abstand in Prozent eingeben"
    override fun corresponds(qty: String) = "entspricht $qty Stk."
    override fun correspondsAmount(amount: String) = "entspricht $amount"
    override val askPrice = "Briefkurs"
    override val bidPrice = "Geldkurs"
    override val inDepot = "Im Depot"
    override val notEnoughCash = "Das Guthaben reicht dafür nicht."
    override val notEnoughShares = "So viele Stücke liegen nicht im Depot."
    override val notEnoughForFee = "Das Guthaben und der Verkaufserlös reichen nicht für die Gebühr."
    override val tradeType = "Geschäftsart"
    override fun tradeTypeValue(side: String, type: OrderType) = "$side · " + when (type) {
        OrderType.MARKET -> "Bestens"; OrderType.LIMIT -> "Limit"; OrderType.STOP -> "Stopp"; OrderType.TRAILING_STOP -> "Trailing-Stopp"
    }
    override val piecesLabel = "Stück"
    override val counterValue = "Gegenwert"
    override val estCounterValue = "Geschätzter Gegenwert"
    override val fee = "Fremdkostenpauschale"
    override val venue = "Handelsplatz"
    override val bestPrice = "Bestpreis"
    override val productCosts = "Produktkosten p. a."
    override val derivativeRisk = "Hebelprodukte können zum Totalverlust des Einsatzes führen. Die Ex-ante-Kosteninformation liegt dieser Übersicht zugrunde."
    override val cashAfter = "Guthaben danach"
    override val notePlaceholder = "Notiz (optional), etwa dein Grund für die Order"
    override val placeOrder = "Order aufgeben"
    override val orderNotExecuted = "Die Order wurde nicht ausgeführt. Prüfe den aktuellen Kurs und dein Guthaben."
    override val initialStop = "Stoppreis zu Beginn"

    override fun ordersOpenHint(n: Int) = "$n offen · Limit-, Stopp- und Trailing-Orders werden während der Handelszeiten ausgeführt"
    override val noOpenOrders = "Keine offenen Orders."
    override val doneOrders = "Erledigt"
    override val cancelOrder = "Stornieren"
    override fun validUntil(ts: String) = "Gültig bis $ts"
    override fun orderStatus(s: OrderStatus) = when (s) {
        OrderStatus.OPEN -> "Offen"; OrderStatus.FILLED -> "Ausgeführt"; OrderStatus.CANCELLED -> "Storniert"
        OrderStatus.REJECTED -> "Abgelehnt"; OrderStatus.EXPIRED -> "Abgelaufen"
    }
    override fun trailingInfo(pct: String, stop: String) = "Trailing $pct · Stopp aktuell $stop"
    override fun reason(code: String) = when (code) {
        "EXPIRED" -> "Gültigkeit abgelaufen"; "PRODUCT_GONE" -> "Produkt nicht mehr handelbar"
        else -> error(code, "")
    }

    override val plan = "Plan"
    override val plans = "Pläne"
    override fun planCount(n: Int) = "$n ${if (n == 1) "Plan" else "Pläne"}"
    override val planAmountHint = "je Ausführung · mindestens 1 €, höchstens 10.000 €"
    override val interval = "Intervall"
    override fun intervalLabel(i: PlanInterval) = when (i) {
        PlanInterval.WEEKLY -> "Wöchentlich"; PlanInterval.BIWEEKLY -> "Zweimal im Monat"; PlanInterval.MONTHLY -> "Monatlich"; PlanInterval.QUARTERLY -> "Vierteljährlich"
    }
    override fun intervalSubtitle(i: PlanInterval) = when (i) {
        PlanInterval.WEEKLY -> "alle 7 Tage"; PlanInterval.BIWEEKLY -> "am 2. und 16."; PlanInterval.MONTHLY -> "einmal im Monat"; PlanInterval.QUARTERLY -> "alle drei Monate"
    }
    override val execution = "Ausführung"
    override val startOfMonth = "Anfang des Monats"
    override val midOfMonth = "Mitte des Monats"
    override fun onDay(d: Int) = "am $d."
    override val savingsRate = "Sparrate"
    override val executionDay = "Ausführungstag"
    override val executionDays = "Ausführungstage"
    override val firstExecution = "Erste Ausführung"
    override val paymentMethod = "Zahlungsmethode"
    override val planNote = "Sparpläne werden als Bestens-Order ausgeführt. Reicht das Guthaben nicht, wird die Ausführung am nächsten Tag wiederholt."
    override val create = "Erstellen"
    override val managePlan = "Sparplan verwalten"
    override val status = "Status"
    override val paused = "Pausiert"
    override val active = "Aktiv"
    override val nextExecution = "Nächste Ausführung"
    override val executions = "Ausführungen"
    override val investedSoFar = "Bisher investiert"
    override val lastFailure = "Letzter Fehlschlag"
    override val changeRate = "Sparrate ändern"
    override val changeInterval = "Intervall ändern"
    override val planUhrzeit = "Uhrzeit"
    override val planUhrzeitAendern = "Uhrzeit ändern"
    override val planUhrzeitAuto = "Automatisch"
    override val planUhrzeitAutoText = "Am Ausführungstag, sobald ein aktueller Kurs da ist"
    override val planUhrzeitHinweis = "Ausgeführt wird, während die App läuft: zur gewählten Uhrzeit oder, wenn sie da geschlossen ist, beim nächsten Öffnen. Aktien und ETFs brauchen einen aktuellen Kurs, also einen offenen Handelsplatz."
    override fun planUhrzeitWert(uhr: String) = "$uhr Uhr"
    override val resume = "Fortsetzen"
    override val pause = "Pausieren"
    override val deletePlanTitle = "Sparplan löschen?"
    override val deletePlanText = "Bereits gekaufte Anteile bleiben im Depot."
    override val noPlans = "Noch kein Sparplan"
    override fun perMonthPlans(amount: String, n: Int) = "$amount im Monat · ${planCount(n)}"
    override val createPlan = "Sparplan anlegen"
    override fun nextOn(date: String) = "nächste $date"
    override fun investedAmount(amount: String) = "$amount investiert"

    override val createAlert = "Preisalarm erstellen"
    override val enterTargetPrice = "Kursmarke eingeben"
    override fun alertRises(price: String, pct: String) = "Alarm, sobald der Kurs auf $price steigt (+$pct)"
    override fun alertFalls(price: String, pct: String) = "Alarm, sobald der Kurs auf $price fällt (−$pct)"
    override val targetEqualsPrice = "Die Marke entspricht dem aktuellen Kurs"
    override val setAlert = "Alarm setzen"
    override val whenRises = "wenn der Kurs steigt"
    override val whenFalls = "wenn der Kurs fällt"
    override fun setAt(price: String) = "gesetzt bei $price"
    override fun alertCount(n: Int) = "$n ${if (n == 1) "Alarm" else "Alarme"}"
    override val alertsHint = "Preisalarme legst du auf der Seite eines Wertpapiers an."
    override val mpTitel = "Bullen- oder Bärenmarkt"
    override fun mpGesamt(bullen: Int, alle: Int) = "$bullen von $alle Märkten im Bullenmarkt ohne Korrektur"
    override fun mpIndex(schluessel: String) = when (schluessel) {
        "sp500" -> "S&P 500"; "nasdaq" -> "Nasdaq 100"; "world" -> "MSCI World (ETF)"; "dax" -> "DAX"; "stoxx" -> "Euro Stoxx 50"
        "nikkei" -> "Nikkei 225"; "bitcoin" -> "Bitcoin"; else -> schluessel
    }
    override fun mpPhase(art: String, korrektur: Boolean, seit: String) = when {
        art == "BAER" -> "Bärenmarkt seit $seit"
        korrektur -> "Korrektur im Bullenmarkt seit $seit"
        else -> "Bullenmarkt seit $seit"
    }
    override fun mp200(wert: String) = "$wert zur 200-Tage-Linie"
    override fun mpDetail(art: String, vomTief: String, golden: Boolean?, kreuzSeit: String?) = listOfNotNull(
        if (art == "BAER") "$vomTief über dem Tief des Bärenmarkts. Ab 20 % gilt er als beendet." else "$vomTief über dem Tief vor dem Anstieg. Fällt der Index 20 % unter sein Hoch, beginnt ein Bärenmarkt.",
        when (golden) {
            true -> "Golden Cross" + (kreuzSeit?.let { " am $it" } ?: "") + ": die 50-Tage-Linie liegt über der 200-Tage-Linie."
            false -> "Death Cross" + (kreuzSeit?.let { " am $it" } ?: "") + ": die 50-Tage-Linie liegt unter der 200-Tage-Linie."
            null -> null
        },
        "Die Linie zeigt zwei Jahre, die waagrechte Linie die Schwelle zum Wechsel.",
    ).joinToString(" ")
    override val mpHinweis = "Faustregeln für die Phasen:\n\n" +
        "• Bärenmarkt: ein Index schließt 20 % unter seinem letzten Hoch\n" +
        "• Bullenmarkt: er steht wieder 20 % über seinem Tief\n" +
        "• Korrektur: ein Rückgang um 10 bis 20 % im Bullenmarkt\n\n" +
        "Die Ampel zeigt Bulle (grün), Korrektur (gelb) und Bär (rot). Die 200-Tage-Linie zeigt den langfristigen Trend. Kreuzt die 50-Tage-Linie sie nach oben, heißt das Golden Cross, nach unten Death Cross. Gerechnet aus zehn Jahren Tagesschlusskursen."
    override val cotTitel = "Terminmärkte"
    override fun cotMarkt(schluessel: String) = when (schluessel) {
        "sp500" -> "S&P 500"; "nasdaq" -> "Nasdaq 100"; "gold" -> "Gold"; "oel" -> "Rohöl (WTI)"; "euro" -> "Euro gegen Dollar"
        "anleihen" -> "US-Staatsanleihen 10 Jahre"; "bitcoin" -> "Bitcoin"; else -> schluessel
    }
    override fun cotQuelle(stand: String) = "Quelle: CFTC, Stand $stand"
    override val lpTitel = "Offene Leerverkäufe"
    override fun lpSumme(anteil: String, anzahl: Int) =
        if (anzahl == 1) "Ein Fonds setzt mit $anteil des Grundkapitals auf fallende Kurse" else "$anzahl Fonds setzen mit zusammen $anteil des Grundkapitals auf fallende Kurse"
    override fun lpVeraenderung(aenderung: String) = "$aenderung Prozentpunkte gegenüber vor vier Wochen"
    override val lpKeine = "Gerade keine veröffentlichte Position."
    override fun lpSeit(tag: String) = "seit $tag"
    override fun lpQuelle(land: String, stand: String) = when (land) {
        "SE" -> "Quelle: Finansinspektionen, Blankningsregistret (fi.se), Stand $stand"
        "IT" -> "Quelle: Consob, Netto-Leerverkaufspositionen (consob.it), selbst importiert, Stand $stand"
        "DE" -> "Quelle: Bundesanzeiger, Netto-Leerverkaufspositionen (bundesanzeiger.de), selbst importiert, Stand $stand"
        "GB" -> "Quelle: Financial Conduct Authority, aggregierte Netto-Leerverkaufspositionen (fca.org.uk), selbst importiert, Stand $stand"
        else -> "Quelle: Autorité des marchés financiers über data.gouv.fr (Licence Ouverte 2.0), Stand $stand"
    }
    override fun lpGesamt(anteil: String) = "Leerverkäufer setzen zusammen mit $anteil des Grundkapitals auf fallende Kurse"
    override fun lpHinweis(land: String) = if (land == "GB") "Leerverkauf ist eine Wette auf fallende Kurse. Die britische Finanzaufsicht veröffentlicht nur die Summe aller gemeldeten Positionen je Aktie (ab 0,2 % des Grundkapitals), ohne Namen. Die Linie zeigt die Summe im letzten Jahr. Müssen sich Leerverkäufer eindecken, kann das den Kurs treiben." else "Leerverkauf ist eine Wette auf fallende Kurse. In der EU wird jede Netto-Leerverkaufsposition ab 0,5 % des Grundkapitals mit dem Namen des Halters veröffentlicht, die Summe liegt daher meist höher. Die Linie zeigt die Summe der veröffentlichten Positionen im letzten Jahr. Müssen sich Leerverkäufer eindecken, kann das den Kurs treiben."
    override val fdaTitel = "Zulassungen und Rückrufe (FDA)"
    override val fdaZulassungen = "Zulassungen, 12 Monate"
    override val fdaRueckrufe = "Rückrufe, 12 Monate"
    override fun fdaArt(art: String, vorrang: Boolean) = when (art) {
        "NEU" -> "Neuer Wirkstoff"; "ORIGINAL" -> "Neue Zulassung"; else -> "Neue Anwendung"
    } + if (vorrang) " · beschleunigte Prüfung" else ""
    override fun fdaKlasse(klasse: Int, geraet: Boolean, laufend: Boolean) = listOfNotNull(
        when (klasse) { 1 -> "Klasse I (ernste Gefahr)"; 2 -> "Klasse II"; 3 -> "Klasse III"; else -> null },
        if (geraet) "Medizinprodukt" else "Arzneimittel",
        if (laufend) "läuft" else null,
    ).joinToString(" · ")
    override val fdaKeine = "Keine Zulassungen oder Rückrufe in den letzten zwölf Monaten gefunden."
    override val fdaQuelle = "Quelle: U.S. Food and Drug Administration über openFDA (api.fda.gov). openFDA ist nicht für medizinische Entscheidungen gedacht."
    override val fdaHinweis = "Die US-Arzneimittelbehörde FDA veröffentlicht jede Woche Rückrufe von Arzneimitteln und Medizinprodukten. Klasse I heißt, dass das Produkt ernste Gesundheitsschäden verursachen kann. Die Zuordnung läuft über den Firmennamen und bekannte Tochterfirmen, einzelne Einträge können fehlen."
    override fun euQuelle(land: String, stand: String) = when (land) {
        "FR" -> "Quelle: ©Autorité des marchés financiers – droits réservés, BDIF (bdif.amf-france.org)"
        "NL" -> "Quelle: Autoriteit Financiële Markten (AFM), Register Transacties leidinggevenden (afm.nl)"
        "GB" -> "Quelle: Financial Conduct Authority, National Storage Mechanism (data.fca.org.uk)"
        "CH" -> "Quelle: SIX Exchange Regulation, Management-Transaktionen ohne Namen (ser-ag.com)"
        "IN" -> "Quelle: BSE, Meldungen nach SEBI PIT (bseindia.com), selbst importiert"
        "HK" -> "Quelle: HKEX, Disclosure of Interests (di.hkex.com.hk)"
        "CA" -> "Quelle: SEDI, Wochenberichte der kanadischen Wertpapieraufsichten (sedi.ca), selbst importiert"
        "AU" -> "Quelle: ASX, Appendix 3Y (asx.com.au) · Beträge aus den Formularen gelesen"
        "DK", "FI", "IS", "EE", "LV", "LT" -> "Quelle: Nasdaq Nordic und Baltic, Mitteilungen der Emittenten (news.eu.nasdaq.com) · Beträge aus den Formularen gelesen"
        "CZ" -> "Quelle: ČNB, zentrales Register regulierter Informationen (oam.cnb.cz)"
        "LU" -> "Quelle: Luxembourg Stock Exchange, OAM (luxse.com) · Beträge aus den Formularen gelesen"
        "IT" -> "Quelle: 1Info SDIR (1info.it) oder selbst importiert · Beträge aus den Formularen gelesen"
        "CY" -> "Quelle: Cyprus Stock Exchange, Public OAM (publicoam.cse.com.cy) · Beträge aus den Formularen gelesen"
        "HU" -> "Quelle: Magyar Nemzeti Bank, Tőkepiaci közzétételek (kozzetetelek.mnb.hu) · Beträge aus den Formularen gelesen"
        "SE", "" -> "Quelle: Finansinspektionen, Insynshandel (fi.se)"
        else -> "Quelle: selbst importierte Meldungen nach Art. 19 MAR"
    } + if (stand.isNotBlank()) ", Stand $stand" else ""
    override val euSedi = "Kanadische Insider melden ihre Geschäfte an SEDI. Die Datenbank lässt sich nur im Browser abfragen."
    override val euSediLink = "SEDI öffnen"
    override fun euArt(art: String) = when (art) { "K" -> "Kauf"; "V" -> "Verkauf"; else -> "Sonstiges" }
    override fun euRolle(r: String) = when (r) { "S" -> "Geschäftsleitung"; "F" -> "Führungskraft"; "A" -> "Verwaltungsrat"; else -> "Nahestehende Person" }
    override val bbDafuer = "Dafür spricht"
    override val bbDagegen = "Dagegen spricht"
    override val bbHinweis = "Automatisch aus Schlagzeilen der letzten 60 Tage und Kennzahlen zusammengestellt, der Beleg darunter zeigt den Anlass."
    override val bbWaage = "Waage"
    override fun bbStufe(stufe: Int) = listOf("klar dagegen", "eher dagegen", "ausgewogen", "eher dafür", "klar dafür").getOrElse(stufe) { "" }
    override fun bbWaageSatz(stufe: Int) = listOf(
        "Die Punkte dagegen wiegen deutlich schwerer.",
        "Die Punkte dagegen wiegen etwas schwerer.",
        "Punkte dafür und dagegen halten sich ungefähr die Waage.",
        "Die Punkte dafür wiegen etwas schwerer.",
        "Die Punkte dafür wiegen deutlich schwerer.",
    ).getOrElse(stufe) { "" }
    override val bbWaageHinweis = "Jeder Punkt hat ein Gewicht:\n• dreifach: schwere Vorwürfe wie Bilanzbetrug oder Insolvenz\n• doppelt: Gewinnwarnungen, Ermittlungen, Verluste und angehobene Prognosen\n• halb: Schwankung und zyklische Branche\n\nMehrere Schlagzeilen zum selben Thema zählen etwas mehr."
    override fun bbAlle(n: Int) = "Alle $n anzeigen"
    override fun bbWarum(schluessel: String) = when (schluessel) {
        "PLEITE" -> "Bei einer Insolvenz verlieren Aktionäre meist fast alles, weil zuerst die Gläubiger bedient werden."
        "BILANZ" -> "Stimmen die Zahlen nicht, ist jede Bewertung wertlos. Bilanzskandale wie bei Wirecard enden oft mit Totalverlust."
        "ERMITTLUNG" -> "Ermittlungen können zu hohen Strafen, Auflagen oder einem Verbot von Geschäften führen und ziehen sich oft über Jahre."
        "KLAGE" -> "Klagen kosten Geld und Aufmerksamkeit. Gefährlich werden sie, wenn es um viel Geld oder das Kerngeschäft geht."
        "WARNUNG" -> "Senkt eine Firma die eigene Prognose, laufen die Geschäfte schlechter als geplant. Oft folgen weitere Enttäuschungen."
        "ABSTUFUNG" -> "Analysten passen ihre Einschätzung an neue Informationen an. Mehrere Abstufungen zeigen, dass die Stimmung kippt."
        "RUECKRUF" -> "Rückrufe, Datenlecks und Ausfälle kosten Geld und Vertrauen der Kunden."
        "PERSONAL" -> "Ein plötzlicher Abgang an der Spitze kann auf Probleme hindeuten. Stellenabbau zeigt oft, dass das Geschäft schwächelt, kann aber auch die Kosten senken."
        "UEBERNAHME" -> "Wer übernommen wird, bekommt meist einen Aufschlag auf den Kurs. Platzt die Übernahme, fällt der Kurs oft zurück."
        "PROGNOSE" -> "Hebt eine Firma die eigene Prognose, laufen die Geschäfte besser als geplant. Das Management kennt die Zahlen am besten."
        "ZAHLEN" -> "Übertrifft eine Firma die Erwartungen, steigt oft der Kurs, weil Analysten ihre Schätzungen nach oben anpassen."
        "AUFSTUFUNG" -> "Analysten heben ihre Einschätzung, wenn sie mehr Potenzial sehen. Treffsicher sind sie aber oft nicht."
        "ZULASSUNG" -> "Eine Zulassung öffnet einen neuen Markt. Bei Pharma- und Medizintechnik-Firmen entscheidet sie oft über den Umsatz mehrerer Jahre."
        "AUFTRAG" -> "Neue Aufträge und Partner sichern künftigen Umsatz, vor allem wenn sie im Verhältnis zum Gesamtumsatz groß sind."
        "RUECKKAUF" -> "Rückkäufe und höhere Dividenden geben Geld an die Aktionäre zurück und zeigen, dass die Firma genug verdient."
        "KI" -> "Firmen, die vom Ausbau künstlicher Intelligenz profitieren, wachsen gerade schnell. Die Erwartungen sind aber oft schon im Kurs."
        "ANALYSTEN_POSITIV" -> "Das Kursziel ist der Mittelwert der Schätzungen. Es ist eine Einschätzung und liegt oft daneben."
        "ANALYSTEN_SKEPTISCH" -> "Liegt das Kursziel unter dem Kurs, halten die meisten Analysten die Aktie für ausgereizt."
        "WACHSTUM" -> "Wachsender Umsatz ist die Grundlage für künftige Gewinne."
        "SCHRUMPFT" -> "Sinkt der Umsatz, verliert die Firma Kunden oder verkauft zu niedrigeren Preisen. Gewinne lassen sich dann nur über Sparen halten."
        "GEWINNWACHSTUM" -> "Langfristig folgt der Aktienkurs dem Gewinn."
        "GEWINN_FAELLT" -> "Sinkende Gewinne drücken auf Dividende und Kurs."
        "MARGE_HOCH" -> "Eine hohe Marge zeigt Preismacht: Die Firma verdient an jedem Euro Umsatz viel."
        "VERLUST" -> "Wer Verluste schreibt, braucht Geld von außen, über Kredite oder neue Aktien. Das verwässert die Anteile."
        "NETTOKASSE" -> "Mehr Geld als Schulden macht unabhängig von Banken und hilft in Krisen."
        "SCHULDEN" -> "Hohe Schulden werden teuer, wenn die Zinsen steigen oder das Geschäft schwächelt."
        "CASHFLOW_POSITIV" -> "Freier Cashflow ist das Geld, das nach allen Investitionen übrig bleibt. Daraus kommen Dividenden und Rückkäufe."
        "CASHFLOW_NEGATIV" -> "Die Firma verbraucht mehr Geld, als sie einnimmt, und braucht auf Dauer frisches Kapital."
        "UEBERTROFFEN" -> "Wer die Erwartungen regelmäßig übertrifft, plant vorsichtig und liefert verlässlich."
        "VERFEHLT" -> "Wer die Erwartungen öfter verfehlt, verliert das Vertrauen der Anleger."
        "GEWINNE_STEIGEN" -> "Ein niedrigeres erwartetes KGV heißt: Analysten rechnen mit steigenden Gewinnen."
        "BEWERTUNG_HOCH" -> "Ein hohes KGV heißt, dass viel künftiges Wachstum schon im Kurs steckt. Enttäuschungen drücken den Kurs dann stark."
        "SCHWANKUNG" -> "Ein Beta über 1 heißt: Die Aktie bewegt sich stärker als der Markt, nach oben wie nach unten."
        "ZYKLISCH" -> "In einer Rezession brechen die Gewinne solcher Firmen oft stark ein."
        "DIVIDENDE_UNGEDECKT" -> "Wird mehr ausgeschüttet als verdient, droht eine Kürzung der Dividende."
        "INSIDER_KAUFEN" -> "Die Führung kennt die Firma am besten. Kauft sie mit eigenem Geld, gilt das als Zeichen von Zuversicht."
        "INSIDER_VERKAUFEN" -> "Verkäufe haben oft private Gründe. Verkaufen viele aus der Führung gleichzeitig, ist das ein Warnzeichen."
        "LEERVERKAUF_HOCH" -> "Leerverkäufer wetten auf fallende Kurse. Viele davon zeigen Zweifel, können aber auch einen schnellen Anstieg auslösen, wenn sie sich eindecken müssen."
        "FDA_ZULASSUNG" -> "Neue Zulassungen bringen neue Produkte auf den Markt."
        "FDA_RUECKRUF" -> "Rückrufe der Klasse I betreffen ernste Gefahren für Patienten und können teuer werden."
        "NAHE_HOCH" -> "Aktien, die zuletzt gut gelaufen sind, laufen im Schnitt eine Weile weiter (Momentum). Eine Garantie ist das nicht."
        "WEIT_UNTER_HOCH" -> "Ein starker Rückgang zeigt, dass der Markt Probleme sieht. Billig ist die Aktie dadurch nicht automatisch."
        "DIVIDENDE_SOLIDE" -> "Eine gedeckte Dividende bringt laufende Erträge und bleibt eher bestehen, wenn es schlechter läuft."
        "RENDITE_HOCH" -> "Die Eigenkapitalrendite zeigt, wie viel die Firma aus dem Geld der Aktionäre macht. Über 20 % ist sehr gut."
        "BEWERTUNG_NIEDRIG" -> "Ein niedriges KGV kann eine Chance sein. Oft hat der Markt aber gute Gründe für den Abschlag."
        else -> ""
    }
    override fun bbThema(thema: String) = when (thema) {
        "PLEITE" -> "Insolvenz, Zweifel am Fortbestand oder drohender Börsenrückzug in den Nachrichten"
        "BILANZ" -> "Vorwürfe zur Bilanz, Leerverkäufer-Bericht oder verspätete Abschlüsse"
        "ERMITTLUNG" -> "Ermittlungen von Behörden oder Kartellverfahren"
        "KLAGE" -> "Klagen oder Sammelklagen gegen das Unternehmen"
        "WARNUNG" -> "Gewinnwarnung, gesenkte Prognose oder verfehlte Erwartungen"
        "ABSTUFUNG" -> "Analysten stufen ab oder senken Kursziele"
        "RUECKRUF" -> "Rückruf, Datenleck, Cyberangriff oder Ausfall"
        "PERSONAL" -> "Rücktritt an der Spitze oder Stellenabbau"
        "UEBERNAHME" -> "Übernahme im Gespräch"
        "PROGNOSE" -> "Prognose angehoben"
        "ZAHLEN" -> "Zahlen über den Erwartungen oder Rekordwerte"
        "AUFSTUFUNG" -> "Analysten stufen hoch oder heben Kursziele"
        "ZULASSUNG" -> "Zulassung erhalten"
        "AUFTRAG" -> "Neue Aufträge oder Partnerschaften"
        "RUECKKAUF" -> "Aktienrückkauf oder höhere Dividende"
        "KI" -> "Nachfrage durch künstliche Intelligenz"
        else -> thema
    }
    override fun bbKennzahl(kennzahl: String, w: List<String>) = when (kennzahl) {
        "ANALYSTEN_POSITIV" -> "Analysten sehen Luft nach oben: Kursziel ${w[0]} (${w[1]}), ${w[2]} Einschätzungen"
        "ANALYSTEN_SKEPTISCH" -> "Analysten sind skeptisch: Kursziel ${w[0]} (${w[1]}), ${w[2]} Einschätzungen"
        "WACHSTUM" -> "Umsatz wächst um ${w[0]}"
        "SCHRUMPFT" -> "Umsatz schrumpft um ${w[0]}"
        "GEWINNWACHSTUM" -> "Gewinn wächst um ${w[0]}"
        "GEWINN_FAELLT" -> "Gewinn fällt um ${w[0]}"
        "MARGE_HOCH" -> "Hohe Gewinnmarge von ${w[0]}"
        "VERLUST" -> "Schreibt Verluste (Marge −${w[0]})"
        "NETTOKASSE" -> "Mehr Barmittel als Schulden, netto ${w[0]}"
        "SCHULDEN" -> "Hohe Nettoschulden von ${w[0]}, ${w[1]} des Börsenwerts"
        "CASHFLOW_POSITIV" -> "Freier Cashflow von ${w[0]}, ${w[1]} des Börsenwerts"
        "CASHFLOW_NEGATIV" -> "Verbraucht Geld: freier Cashflow −${w[0]}"
        "UEBERTROFFEN" -> "Gewinn je Aktie ${w[0]} von ${w[1]} Quartalen über den Erwartungen"
        "VERFEHLT" -> "Gewinn je Aktie ${w[0]} von ${w[1]} Quartalen unter den Erwartungen"
        "GEWINNE_STEIGEN" -> "Gewinne sollen steigen: KGV ${w[0]}, erwartet ${w[1]}"
        "BEWERTUNG_HOCH" -> "Hohe Bewertung mit einem KGV von ${w[0]}"
        "SCHWANKUNG" -> "Schwankt stärker als der Markt (Beta ${w[0]})"
        "ZYKLISCH" -> "Zyklische Branche: Gewinne hängen stark an der Konjunktur"
        "DIVIDENDE_UNGEDECKT" -> "Dividende nicht vom Gewinn gedeckt (Ausschüttung ${w[0]})"
        "INSIDER_KAUFEN" -> "Insider kaufen (Insider-Trend ${w[0]})"
        "INSIDER_VERKAUFEN" -> "Insider verkaufen (Insider-Trend ${w[0]})"
        "LEERVERKAUF_HOCH" -> "Viele Leerverkäufer: ${w[0]} des Grundkapitals leer verkauft"
        "FDA_ZULASSUNG" -> if (w[0] == "1") "Ein neues Mittel in den letzten zwölf Monaten von der FDA zugelassen" else "${w[0]} neue Mittel in den letzten zwölf Monaten von der FDA zugelassen"
        "FDA_RUECKRUF" -> if (w[0] == "1") "Ein Rückruf der FDA-Klasse I (ernste Gefahr) in den letzten zwölf Monaten" else "${w[0]} Rückrufe der FDA-Klasse I (ernste Gefahr) in den letzten zwölf Monaten"
        "NAHE_HOCH" -> "Kurs nahe am Jahreshoch: der Aufwärtstrend ist intakt"
        "WEIT_UNTER_HOCH" -> "Kurs ${w[0]} unter dem Jahreshoch: der Abwärtstrend läuft"
        "DIVIDENDE_SOLIDE" -> "Dividendenrendite ${w[0]}, gedeckt vom Gewinn (Ausschüttung ${w[1]})"
        "RENDITE_HOCH" -> "Hohe Eigenkapitalrendite von ${w[0]}"
        "BEWERTUNG_NIEDRIG" -> "Niedrig bewertet: erwartetes KGV ${w[0]}"
        else -> kennzahl
    }
    override val vdKaeufe1 = "Kauf"
    override val vdVerkaeufe1 = "Verkauf"
    override val usHinweis = "Officers, Directors und Aktionäre mit mehr als 10 % melden Käufe und Verkäufe ihres Unternehmens der SEC (Form 4). Gezeigt werden Käufe und Verkäufe der letzten 18 Monate an der Börse oder unter Privatleuten, ohne Zuteilungen, Optionsausübungen, Steuereinbehalte und Schenkungen. Verkäufe aus Verkaufsplänen (Rule 10b5-1) sind gekennzeichnet."
    override val usOhneKontakt = "Neue Meldungen kommen mit jeder Fassung der App. Mit einer Kontaktadresse unter Zugänge holt die App sie selbst nach."
    override fun usQuelle(stand: String) = "Quelle: U.S. Securities and Exchange Commission (EDGAR, Form 4)" + if (stand.isNotBlank()) ", Stand $stand" else ""
    override fun usRolle(r: String) = when (r) { "S" -> "CEO, CFO, President oder Vorsitz"; "F" -> "Führungskraft"; "A" -> "Director"; else -> "Großaktionär oder andere" }
    override fun usBestandDanach(stueck: String, aenderung: String?) = "Bestand danach $stueck Stück" + (aenderung?.let { " ($it)" } ?: "")
    override val usBestaendeTitel = "Wie viel die Insider halten"
    override fun usStueck(stueck: String) = "$stueck Stück"
    override fun usBestaendeSumme(wert: String, personen: Int) =
        "Zusammen rund $wert, verteilt auf " + (if (personen == 1) "1 Person" else "$personen Personen") + ", bewertet zum zuletzt gemeldeten Kurs."
    override val usBestaendeHinweis = "Stand der jeweils letzten Meldung. Neu geladene Meldungen zählen nur direkt gehaltene Aktien, beim mitgelieferten Stand kann der Bestand auch über Familienfirmen oder Stiftungen gehalten sein. Noch nicht ausgeübte Optionen fehlen, der wahre Bestand ist oft größer."
    override val zgSecTitel = "SEC (US-Insider)"
    override val zgSecFeld = "Kontaktadresse (E-Mail)"
    override val zgSecUngueltig = "Bitte eine E-Mail-Adresse eingeben"
    override val itTitel = "Insider-Trend, 12 Monate"
    override fun itStufe(stufe: Int) = listOf("klar verkaufend", "eher verkaufend", "gemischt", "eher kaufend", "klar kaufend").getOrElse(stufe) { "" }
    override fun itSatz(stufe: Int) = listOf(
        "Die Führung verkauft deutlich mehr eigene Aktien, als sie kauft. Verkäufe haben oft private Gründe, viele auf einmal können aber ein Warnzeichen sein.",
        "Die Führung verkauft etwas mehr eigene Aktien, als sie kauft. Verkäufe haben oft private Gründe, etwa Steuern oder ein Hauskauf.",
        "Käufe und Verkäufe der Führung halten sich ungefähr die Waage.",
        "Die Führung kauft etwas mehr eigene Aktien, als sie verkauft. Das gilt oft als Zeichen von Zuversicht.",
        "Die Führung kauft deutlich mehr eigene Aktien, als sie verkauft. Das gilt als starkes Zeichen von Zuversicht, ist aber keine Garantie.",
    ).getOrElse(stufe) { "" }
    override fun itAnzahl(kaeufer: Int, verkaeufer: Int) = listOfNotNull(
        if (kaeufer > 0) (if (kaeufer == 1) "1 Käufer" else "$kaeufer Käufer") else null,
        if (verkaeufer > 0) (if (verkaeufer == 1) "1 Verkäufer" else "$verkaeufer Verkäufer") else null,
    ).joinToString(" · ")
    override fun itRolle(rolle: Int) = listOf("Vorstand und Spitze", "Weitere Führungskräfte", "Aufsichtsrat", "Nahestehende und Großaktionäre").getOrElse(rolle) { "" }
    override fun itRolleZeile(rolle: String, kaeufer: Int, verkaeufer: Int) = "$rolle: " + listOfNotNull(
        if (kaeufer > 0) "$kaeufer kauf" + (if (kaeufer == 1) "t" else "en") else null,
        if (verkaeufer > 0) "$verkaeufer verkauf" + (if (verkaeufer == 1) "t" else "en") else null,
    ).joinToString(", ")
    override val itCluster = "Cluster-Kauf: mindestens drei Insider derselben Firma haben innerhalb von 30 Tagen gekauft."
    override val itClusterKurz = "Cluster-Kauf"
    override val itHinweis = "Jede Person zählt einmal mit der Richtung ihrer Geschäfte im Zeitraum, gewichtet nach ihrer Beziehung zum Unternehmen:\n• Vorstand und Spitze ×3\n• weitere Führungskräfte ×2\n• Aufsichtsrat ×1,5\n• Nahestehende und Großaktionäre ×1\n\nVerkäufe aus Verkaufsplänen zählen halb. Bei US-Werten zählt der Umfang im Verhältnis zum Bestand: Käufe ab 20 % und Verkäufe ab der Hälfte zählen anderthalbfach, Zukäufe unter 2 % und Verkäufe unter 10 % halb. Gezählt werden nur Käufe und Verkäufe an der Börse."
    override val copyExecutives = "Insider"
    override val vdMeistgekauft = "Meiste Käufe der Führung, 90 Tage"
    override val vdMeistverkauft = "Meiste Verkäufe der Führung, 90 Tage"
    override val vdNeueste = "Neueste Meldungen"
    override val vdKaeufe = "Käufe"
    override val vdVerkaeufe = "Verkäufe"
    override val vdLeer = "Gerade keine Meldungen geladen. Später noch einmal öffnen."
    override val vdKarteTitel = "Käufe und Verkäufe der Führung"
    override val grTitel = "Großaktionäre"
    override val grHinweis = "Gemeldete Stimmrechtsanteile ab 3 % nach dem Wertpapierhandelsgesetz, mit Instrumenten, zum Tag der jeweiligen Meldung."
    override fun vdQuelle(stand: String) = "Quelle: © Bundesanstalt für Finanzdienstleistungsaufsicht / www.bafin.de" + if (stand.isNotBlank()) ", Stand $stand" else ""
    override fun vdAnzahl(kaeufe: Int, verkaeufe: Int) = listOfNotNull(
        if (kaeufe > 0) if (kaeufe == 1) "1 Kauf" else "$kaeufe Käufe" else null,
        if (verkaeufe > 0) if (verkaeufe == 1) "1 Verkauf" else "$verkaeufe Verkäufe" else null,
    ).joinToString(" · ")
    override fun vdZusammenfassung(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String) = "Zwölf Monate: " + listOfNotNull(
        if (kaeufe > 0) (if (kaeufe == 1) "1 Kauf" else "$kaeufe Käufe") + " für $kaufBetrag" else null,
        if (verkaeufe > 0) (if (verkaeufe == 1) "1 Verkauf" else "$verkaeufe Verkäufe") + " für $verkaufBetrag" else null,
    ).ifEmpty { listOf("keine Käufe oder Verkäufe") }.joinToString(", ")
    override fun vdArt(art: String) = art.ifBlank { "Meldung" }
    override fun vdRolle(rolle: String) = rolle
    override val alertArtMarke = "Kursmarke"
    override val alertArtBewegung = "Tagesbewegung"
    override val enterMovePct = "Bewegung in Prozent eingeben"
    override fun alertMove(pct: String) = "Alarm, sobald sich der Kurs an einem Handelstag um mindestens ±$pct bewegt. Er meldet sich höchstens einmal am Tag und bleibt bestehen, bis du ihn löschst."
    override fun alertMoveRow(pct: String) = "±$pct an einem Tag"
    override val alertMoveWiederholt = "meldet sich jeden Handelstag neu"
    override val alertMoveUngueltig = "Bitte zwischen 0,5 und 50 % eingeben"
    override fun notifAlertMoveText(pct: String, price: String) = "Heute $pct · Kurs $price"
    override val notiz = "Notiz"
    override val notizHinzufuegen = "Notiz hinzufügen"
    override val notizHinweis = "Etwa warum du den Wert gekauft hast oder beobachtest. Die Notiz bleibt auf diesem Gerät und ist im Depot-Export enthalten."
    override val notizLoeschen = "Notiz löschen"

    override fun availableAmount(amount: String) = "Verfügbar: $amount"
    override val depositSubtitle = "Virtuelles Geld auf das Verrechnungskonto"
    override val withdrawToReference = "Auf dein Referenzkonto"
    override val enterAmount = "Betrag eingeben"
    override fun availableForWithdrawal(amount: String) = "Verfügbar für Auszahlungen: $amount"
    override val moreThanAvailable = "Mehr als verfügbar. Offene Kauforders reservieren Guthaben."
    override val process = "Vorgang"
    override val from = "Von"
    override val to = "Auf"
    override val virtualFunds = "Übungsguthaben (virtuell)"
    override val referenceAccount = "Referenzkonto"
    override val arrival = "Ankunft"
    override val bankDays = "1–3 Bankarbeitstage"
    override val availableAfter = "Verfügbar danach"
    override val feeLabel = "Gebühr"
    override val free = "Kostenlos"
    override val send = "Senden"
    override val withdrawFailed = "Auszahlung nicht möglich."
    override val depositBooked = "Einzahlung gebucht"
    override val withdrawalSent = "Auszahlung unterwegs"
    override fun depositAvailableNow(amount: String) = "$amount stehen sofort zur Verfügung."
    override fun withdrawalArrives(amount: String) = "$amount werden in 1–3 Bankarbeitstagen auf dem Referenzkonto gutgeschrieben."

    override val yourProfile = "Dein Profil"
    override fun practiceSince(date: String) = "Übungsdepot · seit $date"
    override val totalWealth = "Gesamtvermögen"
    override val activity = "Aktivität"
    override val documents = "Dokumente"
    override val documentsSubtitle = "Abrechnungen, Kontoauszüge, Steuerbescheinigung"
    override val notSet = "Nicht hinterlegt"
    override val interestOnCash = "Zinsen auf Cash"
    override val interestAuto = "Automatisch nach EZB-Einlagesatz"
    override fun ecbRate(rate: String, since: String) = "EZB-Einlagesatz $rate p. a. · gilt seit $since"
    override val ecbNotLoaded = "EZB-Satz wird geladen, solange gilt der eigene Satz"
    override val customRate = "Eigener Zinssatz"
    override val enterRate = "Zinssatz in Prozent p. a."
    override val accruedThisMonth = "Aufgelaufen diesen Monat"
    override val deposited = "Eingezahlt"
    override val withdrawn = "Ausgezahlt"
    override val taxes = "Steuern"
    override val taxOverview = "Steuerübersicht"
    override fun allowanceSet(amount: String) = "Freistellungsauftrag $amount"
    override val allowanceNotSet = "Freistellungsauftrag nicht eingerichtet"
    override val hideBalances = "Kontostand ausblenden"
    override val shakeToHide = "Schütteln blendet Beträge aus"
    override val trading = "Handel"
    override val spreadStocks = "Spanne Aktien & ETFs"
    override val spreadDerivatives = "Spanne Hebelprodukte"
    override val spreadHint = "Gekauft wird zum Brief-, verkauft zum Geldkurs. 100 Basispunkte entsprechen einem Prozent. Bei 0 wird zum Mittelkurs gehandelt."
    override val appearance = "Erscheinungsbild"
    override val appearanceSystem = "System"
    override val appearanceLight = "Hell"
    override val appearanceDark = "Dunkel"
    override val language = "Sprache"
    override val languageGerman = "Deutsch"
    override val languageEnglish = "English"
    override val coloredChart = "Chart in Grün und Rot"
    override val notifications = "Mitteilungen"
    override val notificationsHintDesktop = "Für Orders, Sparpläne, Preisalarme, Dividenden, Zinsen, Knock-outs, Copytrading, Wetten, Nachrichten und den Tagesrückblick, solange die App geöffnet ist."
    override val notificationsHint = "Für Orders, Sparpläne, Preisalarme, Dividenden, Zinsen, Knock-outs, Copytrading, Wetten, Nachrichten und den Tagesrückblick."
    override val refreshEvery = "Aktualisierung"
    override fun everySeconds(n: Int) = "alle $n Sekunden"
    override val source = "Quelle"
    override val statusLimited = "eingeschränkt"
    override val statusConnected = "verbunden"
    override val refreshNow = "Jetzt aktualisieren"
    override val exportPortfolio = "Depot exportieren"
    override val exportSubtitle = "Depot und Einstellungen als Datei sichern"
    override val importPortfolio = "Depot importieren"
    override val importSubtitle = "Sicherung oder CSV-Export deines Brokers einlesen"
    override val backupHint = "Das Dateiformat ist versioniert. Spätere Fassungen der App lesen ältere Dateien und heben sie an."
    override val aboutTitle = "Über die App"
    override val kaffeeTitel = "Buy me a coffee"
    override val kaffeeText = "Die App unterstützen, wenn sie dir gefällt"
    override val aboutText = "Tryfolio ist ein Übungsdepot: Die Kurse sind echt und können verzögert eintreffen, gehandelt wird mit virtuellem Guthaben. Es fließt kein echtes Geld, es werden keine Wertpapiere erworben und es besteht keine Verbindung zu einer Bank oder einem Broker. Die App zeigt und erklärt, sie gibt keine Anlageberatung und keine Empfehlungen. Vergangene Entwicklungen sagen nichts über die Zukunft.\n\nDaten aus Registern und von Behörden ruft sie nur für deinen eigenen Gebrauch ab. Meldungen von US-Abgeordneten, Senatoren und Regierung dürfen nach US-Recht (5 U.S.C. § 13107) nicht kommerziell genutzt werden. Tryfolio ist nicht kommerziell.\n\nTryfolio ist freie Software unter der GNU Affero General Public License 3.0 oder neuer. Den Quelltext gibt es auf GitHub: github.com/regard2462/Tryfolio."
    override val version = "Version"
    override val resetDepot = "Depot zurücksetzen"
    override val resetTitle = "Depot zurücksetzen?"
    override fun resetText(amount: String) = "Alle Positionen, Orders, Sparpläne, Alarme und Buchungen werden gelöscht. Das Guthaben startet wieder bei $amount."
    override val reset = "Zurücksetzen"
    override val personalData = "Persönliche Daten"
    override val firstName = "Vorname"
    override val exportTitle = "Export"
    override fun exportDone(kb: Int) = "Depot exportiert ($kb KB)"
    override val exportFailed = "Die Datei konnte nicht geschrieben werden."
    override val importFailedTitle = "Import nicht möglich"
    override val importNotReadable = "Die Datei konnte nicht gelesen werden."
    override fun importError(code: String, extra: Int) = when (code) {
        "NOT_READABLE" -> "Die Datei enthält kein lesbares Depot."
        "FOREIGN" -> "Die Datei stammt nicht aus dieser App."
        "NEWER" -> "Die Datei stammt aus einer neueren Fassung (Format $extra). Bitte die App aktualisieren."
        "INVALID_CASH" -> "Ungültiges Guthaben in der Datei."
        "INVALID_POSITION" -> "Ungültige Position in der Datei."
        else -> importNotReadable
    }
    override val importTitle = "Depot importieren?"
    override fun exportedAt(ts: String) = "Exportiert am $ts"
    override fun importSummary(cash: String, positions: Int, plans: Int, tx: Int) = "Guthaben $cash · $positions Positionen · $plans Sparpläne · $tx Buchungen"
    override val knowledge = "Finanzwissen"
    override val knowledgeSubtitle = "Zwanzig Themen mit 206 Lektionen, Nachschlagewerk und Rechner"
    override val knowledgeInBrowser = "Finanzwissen ist in deinem Browser geöffnet. Die Seite kommt von der App selbst und braucht kein Internet."
    override val knowledgeOpenAgain = "Im Browser erneut öffnen"
    override fun knowledgeBrowserFailed(url: String) = "Der Browser ließ sich nicht öffnen. Gib diese Adresse selbst ein: $url"
    override val knowledgeOeffnenIn = "Öffnen"
    override val knowledgeImBrowser = "Im Browser"
    override val knowledgeInApp = "In der App"
    override val knowledgeInAppOffen = "Finanzwissen ist in einem eigenen Fenster der App geöffnet. Die Seite kommt von der App selbst und braucht kein Internet."
    override val knowledgeInAppNochmal = "Fenster erneut öffnen"
    override fun knowledgeBausteinAngebot(mb: Int) = "Für die Anzeige in der App lädt sie einmalig einen eingebetteten Browser (Chromium, etwa $mb MB) von Maven Central in ihren Datenordner."

    override val allowanceTitle = "Freistellungsauftrag"
    override val allowanceNotSetTitle = "Nicht eingerichtet"
    override fun allowanceIntro(max: String) = "Mit einem Freistellungsauftrag bleiben bis zu $max Kapitalerträge im Jahr steuerfrei, bei zusammen veranlagten Ehepaaren das Doppelte."
    override fun allowanceUsed(used: String, year: Int, free: String) = "davon $used in $year genutzt · $free frei"
    override val activate = "Aktivieren"
    override val manage = "Verwalten"
    override fun taxOverviewYear(year: Int) = "Steuerübersicht $year"
    override val taxRate = "Steuersatz"
    override fun taxRateValue(pct: String) = "25 % + 5,5 % Soli = $pct"
    override val taxWithheld = "Einbehaltene Steuern"
    override val realizedStockGains = "Realisierte Aktiengewinne"
    override val otherIncome = "Sonstige Erträge (ETF, Derivate, Zinsen, Dividenden)"
    override val lossPotStocks = "Verlusttopf Aktien"
    override val lossPotGeneral = "Verlusttopf Sonstige"
    override val privateGains = "Krypto im ersten Jahr (§ 23, ohne Abzug)"
    override val taxNote = "• Verluste werden zuerst mit den Erträgen des laufenden Jahres verrechnet, zu viel gezahlte Steuer kommt zurück, der Rest mindert spätere Gewinne\n• Aktienverluste nur mit Aktiengewinnen, alle anderen Verluste mit allen übrigen Erträgen\n• Verkauf nach FIFO, Gebühren mindern den Gewinn\n• Aktien-ETFs: 30 % steuerfrei (Teilfreistellung), Mischfonds 15 %\n• Krypto: kein Steuerabzug. Nach über einem Jahr ist der Gewinn steuerfrei, sonst gehört er mit dem persönlichen Steuersatz in die Steuererklärung (Freigrenze 1.000 € im Jahr)\n• Nicht nachgebildet: Vorabpauschale, ausländische Quellensteuer, Kirchensteuer\n• Zum Jahreswechsel wandern die Töpfe ins neue Jahr, der Freibetrag beginnt von vorn"
    override val taxData = "Steuerdaten"
    override val taxId = "Steuer-ID"
    override val editTaxId = "Steuer-ID bearbeiten"
    override val churchTax = "Kirchensteuer"
    override val notModelled = "Nicht nachgebildet"
    override fun taxCertificate(year: Int) = "Jahressteuerbescheinigung $year anzeigen"
    override fun allowanceSheetIntro(max: String) = "Betrag bis $max im Jahr, für zusammen veranlagte Ehepaare bis zum Doppelten. Gilt ab sofort für Verkäufe, Dividenden und Zinsen."
    override val taxIdHint = "Elf Ziffern · in einem Übungsdepot beliebig"

    override val filterTrade = "Handel"
    override val filterIncome = "Erträge"
    override val noActivity = "Noch keine Aktivität."
    override fun txLabel(t: TxType) = when (t) {
        TxType.DEPOSIT -> "Einzahlung"; TxType.WITHDRAWAL -> "Auszahlung"; TxType.BUY -> "Kauf"; TxType.SELL -> "Verkauf"
        TxType.FEE -> "Gebühr"; TxType.INTEREST -> "Zinsen"; TxType.KNOCKOUT -> "Knock-out"; TxType.EXPIRY -> "Fälligkeit"
        TxType.DIVIDEND -> "Dividende"; TxType.TAX -> "Steuer"; TxType.ALERT -> "Preisalarm"; TxType.PLAN -> "Sparplan"
        TxType.BET -> "Prognosemarkt"; TxType.SPLIT -> "Aktiensplit"
    }
    override val toSecurity = "Zum Wertpapier"
    override val showDocument = "Abrechnung anzeigen"
    override fun txTitle(tag: String, storedTitle: String) = when (tag) {
        "OPENING" -> "Eröffnungsguthaben"; "DEPOSIT" -> "Einzahlung"; "WITHDRAWAL" -> "Auszahlung"
        "FEE" -> "Fremdkostenpauschale"; "INTEREST" -> "Zinsen"
        else -> storedTitle
    }
    override fun txSubtitle(tag: String, via: String, qty: String, price: String, realized: String, extra: String, stored: String): String {
        val prefix = when (via) { "PLAN" -> "Sparplan · "; "ORDER" -> "Order · "; "DIVIDEND" -> "Dividende wiederangelegt · "; else -> "" }
        return when (tag) {
            "TRANSFER_IN" -> "Übernommen · $qty zu $price"
            "BUY" -> "${prefix}Kauf $qty zu $price"
            "SELL" -> "${prefix}Verkauf $qty zu $price ($realized)"
            "FEE" -> stored.ifBlank { "" }
            "INTEREST" -> "$extra p. a. auf das Guthaben"
            "DIVIDEND" -> "Dividende $qty × $price"
            "KO_SETTLE" -> "Knock-out · Rückzahlung $price je Stück"
            "EXPIRY" -> "Fälligkeit · Auszahlung $price je Stück"
            "EXPIRY_WORTHLESS" -> "Fälligkeit · wertlos verfallen"
            "ALERT_ABOVE" -> "Kurs über $extra · jetzt $price"
            "ALERT_BELOW" -> "Kurs unter $extra · jetzt $price"
            "ALERT_MOVE" -> "Tagesbewegung $extra · jetzt $price"
            "PLAN_FAILED" -> "Ausführung fehlgeschlagen · Guthaben reicht nicht für $extra"
            "WITHDRAWAL" -> "Auf das Referenzkonto · Ankunft in 1–3 Bankarbeitstagen"
            "OPENING", "DEPOSIT" -> ""
            "BET_BUY" -> "$stored · Kauf $qty Anteile"
            "BET_SELL" -> "$stored · Verkauf ($realized)"
            "BET_WIN" -> "$stored · gewonnen"
            "BET_LOSS" -> "$stored · verloren"
            "BET_SETTLE" -> "$stored · aufgelöst ($realized)"
            "SPLIT" -> "Aktiensplit $stored · jetzt $qty Stück"
            else -> stored
        }
    }

    override val taxSection = "Steuer"
    override val statements = "Kontoauszüge"
    override val settlements = "Abrechnungen"
    override fun taxCertificateTitle(year: Int) = "Jahressteuerbescheinigung $year"
    override val taxCertificateSubtitle = "Erträge, Freibetrag, Verlusttöpfe"
    override fun statementTitle(month: String) = "Kontoauszug $month"
    override val statementSubtitle = "Alle Buchungen des Monats"
    override val noBookings = "Noch keine Buchungen."
    override val settlementsHint = "Abrechnungen entstehen mit dem ersten Kauf."
    override fun docTitle(t: TxType) = when (t) {
        TxType.BUY, TxType.SELL -> "Wertpapierabrechnung"; TxType.DIVIDEND -> "Dividendenabrechnung"; TxType.INTEREST -> "Zinsabrechnung"
        TxType.KNOCKOUT -> "Abrechnung Knock-out"; TxType.EXPIRY -> "Abrechnung Fälligkeit"
        TxType.DEPOSIT -> "Einzahlungsbestätigung"; TxType.WITHDRAWAL -> "Auszahlungsbestätigung"; else -> "Beleg"
    }
    override val docCustomer = "Kunde"
    override val docDate = "Datum"
    override val docNumber = "Belegnummer"
    override val docTradeType = "Geschäftsart"
    override val docSymbol = "Kennung"
    override val docExecutionPrice = "Ausführungskurs"
    override val docMarketValue = "Kurswert"
    override val docRealized = "Realisiertes Ergebnis"
    override val docTax = "Kapitalertragsteuer inkl. Soli"
    override val docVenueValue = "Bestpreis (Übungsdepot)"
    override val docDebit = "Belastung"
    override val docCredit = "Gutschrift"
    override val docDividendPerShare = "Dividende je Stück"
    override val docGross = "Bruttobetrag"
    override fun docUntil(date: String) = "bis $date"
    override val docInterestGross = "Zinsen brutto"
    override val docRefundPerPiece = "Rückzahlung je Stück"
    override val docRecipient = "Empfänger"
    override val docOpening = "Anfangssaldo"
    override val docClosing = "Endsaldo"
    override val docTaxYear = "Steuerjahr"
    override val docRealizedGains = "Realisierte Kursgewinne/-verluste"
    override val docDividendsGross = "Dividenden brutto"
    override val docAllowanceUsed = "davon in Anspruch genommen"
    override val docLossPotStocks = "Verlusttopf Aktien (Stand)"
    override val docLossPotGeneral = "Verlusttopf Sonstige (Stand)"
    override val docTaxWithheld = "Einbehaltene Kapitalertragsteuer inkl. Soli"
    override val docDisclaimer = "Dieses Dokument wurde von einer Übungs-App erzeugt. Es hat keine steuerliche oder rechtliche Wirkung."
    override val docPracticeLine = "Übungsdepot · kein echtes Geld, keine echte Bank"
    override val docNoTaxEffect = "Übungsdepot"

    override fun stepOf(n: Int, total: Int) = "Schritt $n von $total"
    override val welcome = "Willkommen bei\nTryfolio"
    override val welcomeText = "Ein Übungsdepot mit echten Kursen und virtuellem Geld. Wie heißt du?"
    override val startCapital = "Startguthaben"
    override val startCapitalText = "Mit wie viel virtuellem Geld möchtest du beginnen? Du kannst jederzeit weiteres einzahlen."
    override val letsGo = "Los geht’s"
    override val noHistory = "Kein Verlauf verfügbar"

    override val toolsTitle = "Werkzeuge"
    override fun toolTitle(key: String) = when (key) {
        "LUMP_SUM" -> "Einmalanlage im Rückblick"; "SAVINGS_PLAN" -> "Sparplan im Rückblick"; "RISK" -> "Schwankung und Rückgänge"
        "FAIR_VALUE" -> "Fairer Wert"; "POSITION_SIZE" -> "Positionsgröße"; "SELL_TAX" -> "Verkauf mit Steuer"
        else -> key
    }
    override fun toolSubtitle(key: String) = when (key) {
        "LUMP_SUM" -> "Was aus einem Betrag geworden wäre"; "SAVINGS_PLAN" -> "Monatlich investiert über mehrere Jahre"
        "RISK" -> "Schwankung, größter Rückgang, beste und schlechteste Tage"
        "FAIR_VALUE" -> "Bewertung über Gewinnwachstum und KGV"; "POSITION_SIZE" -> "Stückzahl passend zu Stop-Loss und Risiko"
        "SELL_TAX" -> "Was nach Steuer und Gebühr bleibt"
        else -> ""
    }
    override fun toolYears(n: Int) = if (n == 1) "1 Jahr" else "$n Jahre"
    override val toolMax = "Max"
    override val toolMonthly = "Monatliche Rate"
    override val toolLoading = "Kurse werden geladen"
    override val toolNoData = "Für diesen Zeitraum liegen keine Daten vor."
    override fun toolBoughtOn(date: String, price: String) = "Kauf am $date zu $price"
    override val toolPerYear = "Pro Jahr"
    override val toolWorldEtfShort = "Weltindex-ETF"
    override val toolNoCosts = "Berechnet mit Tagesschlusskursen in Euro, ohne Kosten und Steuern. Dividenden sind wieder angelegt."
    override val toolWorldNote = "Zum Vergleich dient der iShares Core MSCI World, der seine Erträge selbst wieder anlegt."
    override val toolInvested = "Eingezahlt"
    override val toolExecutions = "Käufe"
    override val toolAverageCost = "Durchschnittlicher Kaufkurs"
    override val toolPerYearMoneyWeighted = "Pro Jahr (geldgewichtet)"
    override val toolVolatility = "Schwankung pro Jahr"
    override val toolDrawdownPeriod = "Vom Hoch zum Tief"
    override fun toolDrawdownRange(from: String, to: String) = "$from bis $to"
    override val toolRecovered = "Wieder auf dem alten Hoch"
    override val toolNotRecovered = "noch nicht"
    override val toolWorstDay = "Schlechtester Tag"
    override val toolBestDay = "Bester Tag"
    override val toolUpDays = "Tage mit Kursplus"
    override val toolRiskNote = "Die Schwankung ist die auf ein Jahr hochgerechnete Standardabweichung der täglichen Kursänderungen."
    override val toolRiskWorldNote = "In Klammern steht der Wert des Weltindex-ETF im selben Zeitraum."
    override val toolDividendGrowth = "Wachstum pro Jahr"
    override val toolShares = "Stückzahl"
    override val toolGrowth = "Gewinnwachstum pro Jahr"
    override val toolExitPe = "KGV am Ende"
    override val toolRequired = "Renditeanspruch pro Jahr"
    override val toolEpsNow = "Gewinn je Aktie heute"
    override val toolPeNow = "KGV heute"
    override fun toolEpsIn(n: Int) = "Gewinn je Aktie in $n Jahren"
    override fun toolPriceIn(n: Int) = "Kurs in $n Jahren"
    override val toolFairToday = "Fairer Wert heute"
    override val toolCurrentPrice = "Aktueller Kurs"
    override fun toolAbove(pct: String) = "$pct darüber"
    override fun toolBelow(pct: String) = "$pct darunter"
    override val toolImpliedReturn = "Rendite pro Jahr bei Kauf heute"
    override val toolFairNote = "Modellrechnung: Der Gewinn je Aktie wächst gleichmäßig, am Ende gilt das gewählte KGV, der Zukunftskurs wird mit dem Renditeanspruch abgezinst. Ohne Dividenden."
    override val toolRiskPerTrade = "Risiko je Kauf"
    override val toolStopDistance = "Abstand zum Stop-Loss"
    override val toolDepotValue = "Depotwert"
    override val toolStopPrice = "Stoppkurs"
    override val toolLossPerUnit = "Verlust je Stück bis zum Stopp"
    override val toolMaxLoss = "Höchstverlust"
    override val toolCapital = "Kapitaleinsatz"
    override fun toolShareOfDepot(pct: String) = "$pct des Depots"
    override val toolMoreThanCash = "Der Kapitaleinsatz ist höher als das verfügbare Guthaben."
    override val toolPositionNote = "Der Stop-Loss begrenzt den Verlust, solange der Kurs ohne Lücke fällt. Bei Kurssprüngen, etwa über Nacht, kann der Verlust größer sein."
    override val toolSellShare = "Anteil verkaufen"
    override val toolSellPrice = "Verkaufskurs"
    override val toolProceeds = "Verkaufserlös"
    override val toolRealized = "Gewinn oder Verlust"
    override val toolSellNote = "Gerechnet wie ein echter Verkauf nach FIFO mit Gebühren, Verlusttöpfen und Freistellungsauftrag. Es wird nichts gebucht."

    override val importPosition = "Position importieren"
    override val importIntro = "Ein anderswo gekauftes Wertpapier mit Stückzahl, Kaufkurs und Kaufdatum eintragen. Dein Guthaben bleibt unverändert."
    override val importSearchHint = "Aktie, ETF oder Krypto suchen"
    override val importDate = "Kaufdatum"
    override val importPrice = "Kaufkurs je Stück"
    override fun importCloseOn(date: String, price: String) = "Schlusskurs am $date: $price"
    override val importUseClose = "Übernehmen"
    override val importNoClose = "Für dieses Datum liegt kein Schlusskurs vor."
    override val importCostBasis = "Einstandswert"
    override val importValueToday = "Wert heute"
    override val importResult = "Ergebnis seit Kauf"
    override val importConfirm = "Ins Depot übernehmen"
    override fun importDone(qty: String, name: String) = "$qty × $name ins Depot übernommen"
    override val importDividendNote = "Dividenden aus der Zeit vor der Übernahme werden nicht nachgebucht. Künftige Dividenden schreibt die App gut."
    override val transferredIn = "Übernommene Positionen"
    override val docTransferIn = "Einbuchung (Import)"
    override val docNoCashFlow = "Keine Buchung auf dem Verrechnungskonto"

    override val assets = "Anlagen"
    override val positions = "Positionen"
    override val breakdown = "Aufschlüsselung"
    override val sortTotal = "Gesamtposition"
    override val sortName = "Name"
    override val keyFigures = "Kennzahlen"
    override val resultSinceBuy = "Ergebnis seit Kauf"
    override val maxDrawdown = "Größter Rückgang vom Hoch"
    override val largestPosition = "Größte Position"
    override val assetClasses = "Anlageklassen"
    override fun assetClass(key: String) = when (key) { "STOCKS" -> "Aktien"; "ETFS" -> "ETFs"; "CRYPTO" -> "Krypto"; "INDEX" -> "Indizes"; "DERIVATIVES" -> "Derivate"; else -> key }

    // Werkzeuge in Analytics
    override fun ptTitle(key: String) = when (key) {
        "PERFORMANCE" -> "Portfolio Performance"; "CHECK" -> "Depot-Check"; "FINDER" -> "Aktienfinder"; "BENCHMARK" -> "Vergleich mit dem Markt"; "WEALTH" -> "Vermögensaufbau"; "WITHDRAWAL" -> "Entnahmeplan"
        "CRASH" -> "Crash-Test"; "DIVIDENDS" -> "Dividendenkalender"; "DIVERSIFICATION" -> "Streuung"; "REBALANCE" -> "Umschichten"
        else -> key
    }
    override val ptCheckRules = "Faustregeln"
    override val ptCheckRulesSub = "Klumpen, Kosten, Reserve und weitere Faustregeln mit Ampel"
    override fun ptSubtitle(key: String) = when (key) {
        "PERFORMANCE" -> "Rendite wie in Portfolio Performance: zeitgewichtet, Zinsfuß, Kapitalbilanz, Monate und Jahre"
        "CHECK" -> "Faustregeln mit Ampel, Streuung, Umschichten und Crash-Test"
        "FINDER" -> "Aktien nach Bewertung, Qualität, Analysten, Trend und Insidern einordnen"
        "BENCHMARK" -> "Dein Depot gegen MSCI World, FTSE All-World und weitere"
        "WEALTH" -> "Wohin Sparrate und Zinseszins führen können"
        "WITHDRAWAL" -> "Wie lange das Geld bei monatlicher Entnahme reicht"
        "CRASH" -> "Dein heutiges Depot in den großen Krisen seit 2000"
        "DIVIDENDS" -> "Erwartete Ausschüttungen der nächsten zwölf Monate"
        "DIVERSIFICATION" -> "Regionen, Branchen, Währungen und Gleichlauf"
        "REBALANCE" -> "Was du für deine Zielgewichte kaufen oder verkaufen müsstest"
        else -> ""
    }
    override fun ptBenchName(key: String) = when (key) {
        "MSCI_WORLD" -> "MSCI World"; "FTSE_ALL_WORLD" -> "FTSE All-World"; "MSCI_ACWI" -> "MSCI ACWI"; "SP500" -> "S&P 500"
        "NASDAQ100" -> "Nasdaq 100"; "DAX" -> "DAX"; "STOXX600" -> "Stoxx Europe 600"; "EM" -> "Schwellenländer"
        "GOLD" -> "Gold"; "BITCOIN" -> "Bitcoin"; "CASH" -> "Tagesgeld"
        else -> key
    }
    override val ptYourDepot = "Dein Depot"
    override val ptTimeWeightedHint = "Zeitgewichtet: Die Kurve folgt allein der Kursbewegung. Käufe und Verkäufe lassen sie unverändert."
    override val ptCompareIntro = "Dein Depot heute im Vergleich zu einem Index, in den jeder Kauf und Verkauf am selben Tag geflossen wäre."
    override val ptInvested = "Eingesetzt"
    override val ptDepotToday = "Dein Depot heute"
    override val ptDepotTodayHint = "Wertpapiere, Verkaufserlöse und Dividenden"
    override fun ptWith(name: String) = "Mit $name"
    override val ptDifference = "Unterschied"
    override val ptPerYear = "Rendite pro Jahr"
    override fun ptNoPricesBefore(name: String, date: String) = "Für $name gibt es erst ab $date Kurse. Käufe davor lassen sich damit nicht nachspielen."
    override val ptSameMoneyNote = "Jeder Kauf und Verkauf deines Depots wird am selben Tag zum Schlusskurs im Vergleichswert nachgespielt, ohne Gebühren und Steuern. Verkaufserlöse und Dividenden zählen zum Depot."
    override fun ptCashNote(rate: String) = "Tagesgeld mit dem heutigen Zinssatz von $rate über die ganze Zeit."
    override fun ptSingleTitle(name: String) = "Einzelwerte gegen $name"
    override val ptBeats = "vor dem Vergleich"
    override val ptTrails = "hinter dem Vergleich"
    override val ptNoPurchases = "Noch keine Käufe: Der Vergleich beginnt mit dem ersten Kauf."
    override val ptNoPositions = "Noch keine Positionen im Depot."
    override val ptStartValue = "Startwert"
    override fun ptStartSecurities(v: String) = "Wertpapiere $v"
    override fun ptStartTotal(v: String) = "Gesamtvermögen $v"
    override val ptStartZero = "Bei null beginnen"
    override val ptStartEigen = "Eigener Betrag"
    override val ptMonthlySaving = "Monatliche Sparrate"
    override val ptHorizon = "Anlagedauer"
    override val ptRaise = "Sparrate steigt jedes Jahr um"
    override fun ptRealTerms(infl: String) = "In heutiger Kaufkraft (Inflation $infl)"
    override val ptTarget = "Ziel"
    override val ptMedian = "Mittlerer Verlauf"
    override val ptWeak = "Schwacher Verlauf (10 %)"
    override val ptStrong = "Starker Verlauf (90 %)"
    override val ptPaidIn = "Eingezahlt"
    override val ptGrowth = "Davon Wertzuwachs"
    override fun ptTargetChance(pct: String) = "in $pct der Verläufe"
    override fun ptTargetReached(amount: String) = "Ziel $amount erreicht"
    override val ptTargetMedian = "Im mittleren Verlauf erreicht"
    override fun ptTargetWhen(years: Int) = if (years <= 1) "innerhalb eines Jahres" else "nach $years Jahren"
    override val ptTargetNever = "nicht in dieser Zeit"
    override fun ptInYears(n: Int) = if (n == 0) "Heute" else if (n == 1) "In 1 Jahr" else "In $n Jahren"
    override fun ptReadout(median: String, low: String, high: String) = "$median (10 %: $low · 90 %: $high)"
    override val ptBandLabel = "80 % der Verläufe"
    override val ptWealthNote = "Monte-Carlo-Simulation mit 1.000 zufälligen Verläufen: Jeden Monat schwankt die Rendite zufällig um den gewählten Wert. Der helle Bereich zeigt, wo 80 % der Verläufe landen, der dunklere die mittleren 50 %. Die Rechnung beruht auf Annahmen und sagt die Entwicklung nicht voraus."
    override val ptMonthlyWithdrawal = "Monatliche Entnahme"
    override fun ptIndexed(infl: String) = "Entnahme steigt mit der Inflation ($infl)"
    override fun ptLasts(pct: String, years: String) = "Das Geld reicht in $pct der Verläufe $years lang."
    override fun ptLeftAfter(years: Int) = if (years == 1) "Übrig nach 1 Jahr (mittlerer Verlauf)" else "Übrig nach $years Jahren (mittlerer Verlauf)"
    override val ptUsedUpAfter = "Aufgebraucht nach (mittlerer Verlauf)"
    override val ptSafeRate = "Rate, die in 90 % der Verläufe reicht"
    override val ptFourPercent = "4-%-Regel"
    override fun ptPerMonth(v: String) = "$v im Monat"
    override val ptPlannedTotal = "Geplante Entnahmen zusammen"
    override val ptNoStart = "Ohne Startwert gibt es nichts zu entnehmen. Wähle das Gesamtvermögen oder kaufe zuerst Wertpapiere."
    override val ptWithdrawalNote = "Die Entnahme erfolgt zu Monatsbeginn, danach schwankt der Rest wie beim Vermögensaufbau (800 zufällige Verläufe). Mit Inflation stehen die Beträge in heutiger Kaufkraft. Die 4-%-Regel stammt von William Bengen (1994, US-Daten über 30 Jahre)."
    override fun ptCrashName(key: String) = when (key) {
        "DOTCOM" -> "Dotcom-Crash"; "FINANCIAL" -> "Finanzkrise"; "EURO" -> "Euro-Schuldenkrise"
        "COVID" -> "Corona-Crash"; "RATES" -> "Zinswende 2022"; "TARIFFS" -> "Zollschock 2025"
        else -> key
    }
    override val ptCrashIntro = "So hätte sich dein heutiges Depot entwickelt, vom Hoch bis zum Tief des S&P 500 in jeder Krise."
    override val ptReference = "S&P 500"
    override fun ptHardestHit(name: String, pct: String) = "Am stärksten getroffen: $name $pct"
    override fun ptRecovery(duration: String) = "Nach dem Tief brauchte der S&P 500 $duration bis zum alten Stand."
    override val ptNotRecovered = "Der S&P 500 hat den alten Stand danach noch nicht wieder erreicht."
    override fun ptDuration(days: Long): String {
        val months = Math.round(days / 30.44).toInt()
        val y = months / 12
        val m = months % 12
        return when {
            days < 45 -> if (days == 1L) "1 Tag" else "$days Tage"
            y == 0 -> "$m Monate"
            m == 0 -> if (y == 1) "1 Jahr" else "$y Jahre"
            else -> (if (y == 1) "1 Jahr" else "$y Jahre") + " und " + (if (m == 1) "1 Monat" else "$m Monate")
        }
    }
    override fun ptEstimated(n: Int, total: Int) = "$n von $total Werten gab es damals noch nicht. Sie sind über ihr Beta geschätzt."
    override val ptCustomScenario = "Eigenes Szenario"
    override val ptMarketFalls = "Der S&P 500 fällt um"
    override val ptEstimatedDepot = "Dein Depot, geschätzt"
    override val ptCrashNote = "Werte mit Kursen aus der Krisenzeit rechnen mit ihrem echten Verlauf, jüngere über ihr Beta zum S&P 500 der letzten drei Jahre (Wochenrenditen). Derivate werden zum veränderten Kurs ihres Basiswerts neu bewertet, Knock-outs fallen gegebenenfalls aus. Wechselkurse, Dividenden und Käufe in der Krise sind nicht eingerechnet."
    override val ptDivExpected = "Erwartet in den nächsten 12 Monaten"
    override fun ptDivAfterTax(v: String) = "nach Steuern etwa $v"
    override val ptDivPerMonth = "Im Monat im Schnitt, nach Steuern"
    override val ptDivYield = "Rendite auf den Depotwert"
    override val ptDivYieldOnCost = "Rendite auf den Einstand"
    override val ptDivReceived = "Im Übungsdepot bisher erhalten (netto)"
    override val ptDivNext = "Nächste Zahlungen"
    override val ptDivTop = "Größte Zahler"
    override fun ptDivTimes(n: Int) = if (n == 1) "1× im Jahr" else "$n× im Jahr"
    override val ptDivNone = "Deine Werte haben in den letzten zwölf Monaten nichts ausgeschüttet."
    override val ptDivNote = "Fortgeschrieben aus den Ausschüttungen der letzten zwölf Monate mit denselben Ex-Tagen und Beträgen je Stück, heutigem Bestand und heutigem Wechselkurs. Thesaurierende ETFs erscheinen nicht. Die Steuer berücksichtigt den verbleibenden Freistellungsauftrag dieses Jahres."
    override val ptTop3 = "Die drei größten zusammen"
    override val ptEffective = "Streut wie"
    override fun ptEffectiveValue(n: String) = "$n gleich große Positionen"
    override val ptCurrencies = "Währungen"
    override fun ptGroup(key: String) = when (key) {
        "CRYPTO" -> "Krypto"; "DERIVATIVES" -> "Derivate"; "ETFS" -> "ETFs"; else -> "Ohne Zuordnung"
    }
    override val ptCorrelation = "Gleichlauf (Korrelation, 1 Jahr)"
    override val ptAverage = "Im Schnitt"
    override val ptCorrelationNote = "Der Gleichlauf liegt zwischen −1 und 1:\n• 1: die Kurse bewegen sich im Gleichschritt\n• 0: kein Zusammenhang\n• unter 0: sie laufen gegeneinander\n\nJe niedriger der Gleichlauf, desto mehr bringt die Streuung. Gerechnet mit Wochenrenditen des letzten Jahres für die größten Positionen."
    override val ptEtfCosts = "Laufende Kosten deiner ETFs"
    override fun ptEtfCostValue(ter: String, perYear: String) = "Ø $ter · etwa $perYear im Jahr"
    override val ptDiversificationNote = "Aktien nach Sitzland und Branche, ETFs nach ihrer Region. Ein ETF zählt als Ganzes."
    override val ptModeRebalance = "Umschichten"
    override val ptModeBuyOnly = "Nur zukaufen"
    override val ptNewMoney = "Neues Geld"
    override fun ptCash(v: String) = "Guthaben $v"
    override val ptEqualWeights = "Gleich gewichten"
    override val ptCurrentWeights = "Heutige Gewichte"
    override val ptTargetWeights = "Zielgewichte"
    override fun ptBuyAmount(v: String) = "Kaufen $v"
    override fun ptSellAmount(v: String) = "Verkaufen $v"
    override val ptOnTarget = "passt"
    override val ptBuys = "Käufe"
    override val ptSells = "Verkäufe"
    override fun ptOrdersFees(n: Int, fee: String) = if (n == 1) "1 Order · $fee Gebühr" else "$n Orders · $fee Gebühr"
    override val ptTaxEstimate = "Geschätzte Steuer auf die Verkäufe"
    override val ptDeviation = "Abweichung vom Ziel"
    override val ptNeedTwo = "Zum Umschichten braucht es mindestens zwei Positionen."
    override fun ptPoints(v: String) = "$v Prozentpunkte"
    override val ptOthers = "Weitere"
    override fun ptFromTo(a: String, b: String) = "$a bis $b"
    override val ptRebalanceNote = "Die Regler setzen die Gewichte im Verhältnis zueinander, zusammen ergeben sie 100 %. Gerechnet mit den angezeigten Kursen und 1 € Gebühr je Order. Hier wird nichts ausgeführt."

    override val knockOuts = "Knock-outs"
    override val warrants = "Optionsscheine"
    override val factors = "Faktoren"
    override val call = "Call"
    override val put = "Put"
    override val long = "Long"
    override val short = "Short"
    override val strikePrice = "Basispreis"
    override val maturity = "Fälligkeit"
    override val issuer = "Emittent"
    override val ecbAbbrev = "EZB"
    override val leverage = "Hebel"
    override val factor = "Faktor"
    override val delta = "Delta"
    override val knockOut = "Knock-out"
    override val ratio = "Verhältnis"
    override val product = "Produkt"
    override val allLeverages = "alle Hebel"
    override val allFactors = "alle Faktoren"
    override val customLeverage = "Eigener Hebel"
    override val customLeverageHint = "Beliebiger Hebel, auch mit Nachkommastellen (z. B. 37,5) und über die Liste hinaus (z. B. 350). Das Produkt wird dazu neu begeben."
    override fun leverageDialogHint(min: Int, max: Int) = "Hebel von $min bis $max. Leer lassen zeigt alle Hebel."
    override val leverageOwn = "Eigenes Produkt"
    override val noProductForFilter = "Kein Produkt für diese Auswahl."
    override val noPriceForProduct = "Für diesen Wert liegt gerade kein Kurs vor."
    override fun productKind(kind: String) = when (kind) { "TURBO" -> "Mini Future (Turbo)"; "WARRANT" -> "Optionsschein"; "FACTOR" -> "Faktor-Zertifikat"; else -> kind }
    override fun distance(pct: String) = "Abstand $pct"
    override fun resultsCount(n: Int) = if (n == 1) "1 Ergebnis" else "${String.format(Locale.GERMANY, "%,d", n)} Ergebnisse"
    override val minStrike = "Basispreis ab"
    override val maxStrike = "Basispreis bis"
    override val startDateFilter = "Startdatum"
    override val endDateFilter = "Endtermin"
    override val strategy = "Strategie"
    override val productInfo = "Produktinformation"
    override val underlying = "Basiswert"
    override val underlyingPrice = "Kurs des Basiswerts"
    override val distanceToBarrier = "Abstand zur Schwelle"
    override val remainingDays = "Restlaufzeit"
    override fun days(n: Long) = if (n == 1L) "1 Tag" else "$n Tage"
    override val impliedVol = "Implizite Volatilität"
    override val intrinsicValue = "Innerer Wert"
    override val timeValue = "Zeitwert"
    override val premium = "Aufgeld"
    override val breakEven = "Gewinnschwelle"
    override val sensitivities = "Sensitivitäten"
    override val gamma = "Gamma"
    override val vega = "Vega (je 1 % Vol)"
    override val theta = "Theta (je Tag)"
    override val currency = "Währung"
    override val referencePrice = "Referenzkurs"
    override val riskKnockout = "Berührt der Basiswert die Knock-out-Schwelle, endet das Zertifikat sofort. Zurückgezahlt wird der Restwert zwischen Schwelle und Basispreis, bei einer Kurslücke durch den Basispreis fast nichts. Die Finanzierungskosten echter Mini Futures, die den Basispreis täglich verschieben, sind nicht nachgebildet."
    override val riskFactor = "Der Faktor wirkt auf die Tagesbewegung und wird täglich neu angesetzt. Über längere Zeiträume kann das Ergebnis deutlich vom Vielfachen der Basiswertentwicklung abweichen, besonders in schwankenden Märkten."
    override val riskWarrant = "Der Zeitwert nimmt mit näher rückender Fälligkeit ab. Liegt der Basiswert am Fälligkeitstag auf der falschen Seite des Basispreises, verfällt der Schein wertlos. Abgerechnet wird mit dem ersten aktuellen Kurs ab der Fälligkeit."
    override val modelledHistory = "Der gezeigte Verlauf ist modelliert und beginnt mit der Erstellung des Produkts."
    override val noFactorHistory = "Der Verlauf von Faktorprodukten ist aus den Tagesrenditen des Basiswerts modelliert (täglicher Hebel, Zinsen und Gebühr), rückwärts vom heutigen Preis."
    override val productUnavailable = "Produkt nicht mehr verfügbar"
    override fun leverageValue(v: String) = "Hebel $v"
    override val derivative = "Derivat"
    override val deltaFilter = "Delta ab"

    override fun error(code: String, extra: String) = when (code) {
        "INVALID_QTY" -> "Ungültige Stückzahl"; "NO_PRICE" -> "Kein Kurs verfügbar"; "INVALID_FEE" -> "Ungültige Gebühr"
        "NOT_TRADABLE" -> "Produkt nicht mehr handelbar"; "WHOLE_PIECES" -> "Nur ganze Stücke bei Derivaten"; "TOO_LARGE" -> "Betrag zu groß"
        "NOT_ENOUGH_CASH" -> "Nicht genug Guthaben"; "NOT_IN_DEPOT" -> "Position nicht im Depot"; "NOT_ENOUGH_PIECES" -> "Nicht genug Stücke im Depot"
        "FEE_NOT_COVERED" -> "Guthaben reicht nicht für die Gebühr"; "INVALID_LIMIT" -> "Ungültiger Limit-/Stoppreis"; "INVALID_TRAIL" -> "Abstand muss zwischen 0 und 50 % liegen"
        "MISSING_SECURITY" -> "Wertpapier fehlt"; "INVALID_AMOUNT" -> "Ungültiger Betrag"; "ONLY_AVAILABLE" -> "Nur $extra verfügbar"
        "ORDER_CLOSED" -> "Die Order ist nicht mehr offen"; "INVALID_DATE" -> "Ungültiges Datum"; "EXPIRED" -> "Gültigkeit abgelaufen"; "PRODUCT_GONE" -> "Produkt nicht mehr handelbar"
        "UNREACHABLE" -> "Kurse nicht erreichbar"; "FX_MISSING" -> "Für einzelne Kurse fehlt der Wechselkurs."; "PARTIAL" -> "Einzelne Kurse sind derzeit nicht erreichbar."
        "SAVE_FAILED" -> "Das Depot konnte nicht gespeichert werden. Bitte Speicherplatz prüfen."
        "LOAD_FAILED" -> "Das gespeicherte Depot war nicht lesbar. Eine Kopie liegt als account.unlesbar im Datenordner, die App beginnt mit einem neuen Depot."
        else -> code
    }
    override fun buyDone(qty: String, price: String) = "Kauf ausgeführt: $qty × $price"
    override fun sellDone(qty: String, price: String) = "Verkauf ausgeführt: $qty × $price"
    override val refreshFirst = "Bitte zuerst den Kurs aktualisieren."
    override val marketClosedOrders = "Börse geschlossen. Limit-, Stopp- und Trailing-Orders werden beim nächsten Handel ausgeführt."
    override val limitPlaced = "Limit-Order angelegt"
    override val stopPlaced = "Stopp-Order angelegt"
    override val trailingPlaced = "Trailing-Stopp angelegt"
    override val orderCancelled = "Order storniert"
    override fun depositedToast(amount: String) = "$amount eingezahlt"
    override fun withdrawnToast(amount: String) = "$amount werden auf dein Referenzkonto überwiesen"
    override val invalidAmount = "Ungültiger Betrag"
    override val invalidPlan = "Ungültiger Sparplan"
    override val planCreated = "Sparplan angelegt"
    override val planPaused = "Sparplan pausiert"
    override val planResumed = "Sparplan fortgesetzt"
    override val planChanged = "Sparplan geändert"
    override val planDeleted = "Sparplan gelöscht"
    override val invalidPrice = "Ungültiger Kurs"
    override val alertSet = "Preisalarm gesetzt"
    override val accountReset = "Depot zurückgesetzt"
    override val depotImported = "Depot importiert"
    override val watchAdded = "Zur Watchlist hinzugefügt"
    override val watchRemoved = "Von der Watchlist entfernt"

    override fun notifDividendText(name: String, amount: String) = "$name: $amount gutgeschrieben"
    override val notifInterest = "Zinsen gutgeschrieben"
    override fun notifInterestText(amount: String) = "$amount auf dein Guthaben"
    override fun notifKnockoutText(name: String) = "$name hat die Knock-out-Schwelle erreicht, zurückgezahlt wird der Restwert"
    override val notifKnockoutSettled = "Knock-out abgerechnet"
    override fun notifKnockoutSettledText(name: String, amount: String) = "$name: Rückzahlung $amount"
    override fun notifExpiryText(name: String, amount: String) = "$name: $amount ausgezahlt"
    override fun notifAlert(name: String) = "Preisalarm: $name"
    override fun notifAlertText(price: String, target: String) = "Kurs $price · Marke $target erreicht"
    override val notifPlanFailed = "Sparplan nicht ausgeführt"
    override fun notifPlanFailedText(name: String) = "$name: Guthaben reicht nicht"
    override val notifPlanDone = "Sparplan ausgeführt"
    override fun notifPlanDoneText(name: String, amount: String) = "$name: $amount"
    override val notifOrder = "Order ausgeführt"
    override fun notifOrderText(side: String, qty: String, name: String, price: String) = "$side $qty × $name zu $price"
    override val channelTrades = "Orders, Sparpläne und Wetten"
    override val channelCash = "Cash und Dividenden"
    override val channelUpdates = "Neue Fassungen"
    override val profilEinstellungen = "Einstellungen"
    override val profilSicherheit = "Sicherheit"
    override val profilDaten = "Daten und Konten"
    override val profilErweitert = "Erweitert"
    override val rbTitel = "Tagesrückblick"
    override fun rbProfil(anzahl: Int, an: Boolean) = when {
        !an -> "Aus" + if (anzahl > 0) " · $anzahl gesammelt" else ""
        anzahl == 0 -> "Jeden Handelstag"
        anzahl == 1 -> "1 Rückblick · jeden Handelstag"
        else -> "$anzahl Rückblicke · jeden Handelstag"
    }
    override val rbSchalter = "Rückblick erstellen"
    override val rbSchalterHinweis = "Jeden Handelstag ein kurzes Fazit, freitags mit Wochenrückblick."
    override val rbLeer = "Noch kein Rückblick. Der erste kommt am nächsten Handelstag zur gewählten Uhrzeit."
    override val rbJetzt = "Heute jetzt zusammenfassen"
    override val rbJetztLaeuft = "Wird zusammengefasst"
    override val rbJetztFehler = "Gerade keine Kurse erreichbar."
    override fun rbWoche(von: String, bis: String) = "Woche vom $von bis $bis"
    override val rbWocheUeberblick = "Die Woche im Überblick"
    override val rbMarkt = "Markt"
    override val rbBewegungen = "Bewegungen im Depot"
    override val rbNachrichten = "Wichtige News"
    override fun rbDepotZeile(aenderung: String, prozent: String?, wert: String) = aenderung + (prozent?.let { " ($it)" } ?: "") + ", Wert $wert"
    override fun rbWahrscheinlich(titel: String, quelle: String) = "Wahrscheinlich: „$titel“" + if (quelle.isNotBlank()) " ($quelle)" else ""
    override fun rbWahrscheinlichKurz(titel: String) = "wahrscheinlich „$titel“"
    override fun rbMitMarkt(markt: String, aenderung: String) = "Keine eigene Nachricht gefunden, lief mit dem Markt ($markt $aenderung)"
    override val rbOhneGrund = "Keine passende Nachricht gefunden"
    override val rbKeinDepot = "Keine Wertpapiere im Depot"
    override val rbKeineBewegung = "Kein Wert hat sich um mehr als 0,5 % bewegt"
    override val rbKeineNachrichten = "Keine besonders wichtigen Nachrichten"
    override val rbVorlaeufig = "Vorläufig, vor dem US-Börsenschluss erstellt. Am Abend kommt der fertige Rückblick."
    override val rbHinweis = "Die Gründe sind Vermutungen: Die App sucht Schlagzeilen des Tages, die den Wert nennen, und bewertet sie wie auf der Nachrichtenseite. Ohne passende Schlagzeile steht da, ob der Wert mit seinem Markt lief. Verglichen wird das Depot mit dem MSCI World, Branchen mit US-Branchen-ETFs, große Aktien wie in der Heatmap."
    override fun rbMitteilungTitel(datum: String) = "Tagesrückblick $datum"
    override fun rbWochenTitel(von: String, bis: String) = "Wochenrückblick $von bis $bis"
    override fun rbFazitTon(stufe: Int, woche: Boolean) = when {
        stufe > 0 -> if (woche) "Eine starke Woche" else "Ein starker Tag"
        stufe < 0 -> if (woche) "Eine schwache Woche" else "Ein schwacher Tag"
        else -> if (woche) "Eine ruhige Woche" else "Ein ruhiger Tag"
    }
    override fun rbFazitDepot(ton: String, hoch: Boolean, prozent: String, betrag: String) = "$ton: Dein Depot " + (if (hoch) "stieg" else "fiel") + " um $prozent ($betrag)"
    override fun rbFazitVergleich(markt: String, punkte: String, lage: Int) = when {
        lage > 0 -> " und lag damit $punkte Prozentpunkte vor dem $markt."
        lage < 0 -> " und lag damit $punkte Prozentpunkte hinter dem $markt."
        else -> " und lief etwa wie der $markt."
    }
    override fun rbFazitBeweger(name: String, prozent: String) = "Am stärksten bewegte sich $name ($prozent)."
    override fun rbFazitMarkt(markt: String, prozent: String, lage: Int, woche: Boolean) = when {
        lage > 0 -> "Der $markt schloss " + (if (woche) "die Woche " else "") + "$prozent höher."
        lage < 0 -> "Der $markt schloss " + (if (woche) "die Woche " else "") + "$prozent tiefer."
        else -> "Der $markt blieb " + (if (woche) "in der Woche " else "") + "fast unverändert."
    }
    override fun rbFazitBranchen(beste: String, bp: String, schwaechste: String, sp: String) = "Stärkste Branche: $beste ($bp), schwächste: $schwaechste ($sp)."
    override val rbStaerkste = "Stärkste"
    override val rbSchwaechste = "Schwächste"
    override val rbGewinner = "Gewinner unter den großen Aktien"
    override val rbVerlierer = "Verlierer unter den großen Aktien"
    override val rbMerkliste = "Deine Watchlist"
    override val rbAufgeklappt = "aufgeklappt"
    override val rbZugeklappt = "zugeklappt"
    override fun newsNotifMany(n: Int) = "$n neue Schlagzeilen"
    override fun newsNotifyOption(stufe: String) = when (stufe) { "alle" -> "Alle Schlagzeilen"; "aus" -> "Aus"; else -> "Nur wichtige Meldungen" }
    override fun newsNotifyOptionHint(stufe: String) = when (stufe) {
        "alle" -> "Jede neue Schlagzeile der eingeschalteten Quellen, mehr als drei auf einmal kommen als eine Mitteilung"
        "aus" -> "Keine Mitteilungen zu Schlagzeilen"
        else -> "Vorgabe: nur die Meldungen, die auf der Nachrichtenseite markiert sind"
    }
    override val newsNotifyOffGlobal = "Mitteilungen sind im Profil ausgeschaltet. Dort wieder einschalten, damit diese Einstellung wirkt."
    override val newsNotifyHint = "Die App sieht bei geöffneter App alle fünf Minuten nach, auf Android bei geschlossener App etwa alle 30 Minuten, sobald das Gerät online ist."
    override val awTitle = "Auswertung"
    override val awLoading = "Auswertung wird berechnet"
    override val awNone = "Für eine Auswertung fehlen Geschäfte in Aktien oder Fonds mit Kursen."
    override fun awHorizon(tage: Int) = if (tage >= 365) "1 Jahr" else "$tage Tage"
    override val awFrequency = "Größe und Häufigkeit"
    override val awPerMonth = "Geschäfte pro Monat"
    override val awBusiest = "Stärkster Monat"
    override val awTypical = "Typische Größe (Median)"
    override val awOverMillion = "Geschäfte über 1 Mio. $"
    override val awBuyShare = "Anteil Käufe"
    override val awUnusual = "Auffällige Monate"
    override val awBeforeMoves = "Vor großen Kursbewegungen"
    override fun dcSummary(gut: Int, von: Int) = "$gut von $von Punkten im grünen Bereich"
    override fun dcTitle(regel: String) = when (regel) {
        "FONDSKOSTEN" -> "Laufende Fondskosten"
        "TER_GESTIEGEN" -> "Gestiegene Fondskosten"
        "GROESSTE" -> "Größte Einzelposition"; "ANZAHL" -> "Streuung über Einzelwerte"; "BRANCHE" -> "Größte Branche"
        "WAEHRUNG" -> "Fremdwährung"; "HEBEL" -> "Hebelprodukte"; "KRYPTO" -> "Krypto"; "KOSTEN" -> "Orderkosten"
        "RESERVE" -> "Guthaben als Reserve"; "SPARPLAN" -> "Sparplan"; "VERLUSTE" -> "Tiefe Verluste"; else -> regel
    }
    override fun dcValue(regel: String, anteil: String, name: String, anzahl: Int) = when (regel) {
        "FONDSKOSTEN" -> "$anteil im Schnitt, etwa $anzahl € im Jahr, am teuersten: $name"
        "TER_GESTIEGEN" -> (if (anzahl == 1) "Teurer geworden: $name" else "$anzahl teurer geworden: $name") + ", bis zu $anteil mehr im Jahr"
        "GROESSTE" -> "$name: $anteil des Depots"
        "ANZAHL" -> "$anzahl Einzelaktien, $anteil in ETFs"
        "BRANCHE" -> "$name: $anteil des Depots"
        "WAEHRUNG" -> "$anteil in fremder Währung gehandelt"
        "HEBEL" -> "$anteil des Depots"
        "KRYPTO" -> "$anteil des Depots"
        "KOSTEN" -> "$anteil des gehandelten Betrags in zwölf Monaten, $anzahl Orders"
        "RESERVE" -> "$anteil des Vermögens"
        "SPARPLAN" -> if (anzahl > 0) "$anzahl aktiv" else "keiner"
        "VERLUSTE" -> "$anzahl über 50 % im Minus: $name"
        else -> anteil
    }
    override val terTeurerTitel = "ETF teurer geworden"
    override val etfTdHinweis = "Die Tracking Difference zeigt die echten Kosten eines ETF: wie weit seine Rendite im Jahr hinter dem Index lag."
    override val etfTdLink = "Tracking Difference bei extraETF"
    override fun terTeurerText(name: String, alt: String, neu: String) = "$name: laufende Kosten von $alt auf $neu im Jahr gestiegen."
    override fun dcExplain(regel: String) = when (regel) {
        "TER_GESTIEGEN" -> "Die App merkt sich die laufenden Kosten jedes ETFs, sobald sie sie abruft. Steigen sie, steht das hier für ein Jahr. Fondsgesellschaften dürfen die Kosten mit Ankündigung erhöhen. Ein vergleichbarer ETF mit niedrigeren Kosten kann sich dann lohnen, ein Wechsel kostet aber Gebühren und bei Gewinn Steuern."
        "FONDSKOSTEN" -> "Die laufenden Kosten (TER) zieht der Fonds jedes Jahr vom Vermögen ab. Breite Index-ETFs kosten oft 0,1 bis 0,3 %. Über viele Jahre schmälern höhere Kosten die Rendite deutlich."
        "GROESSTE" -> "Fällt ein einzelner Wert stark, trifft es das ganze Depot. Als Faustregel gelten höchstens 10 % je Einzelwert. ETFs zählen hier nicht, weil sie selbst streuen."
        "ANZAHL" -> "Mit wenigen Einzelwerten hängt viel an jedem davon. Ab etwa 15 bis 20 Werten aus verschiedenen Branchen oder mit einem breiten ETF sinkt dieses Risiko deutlich."
        "BRANCHE" -> "Werte einer Branche fallen oft gemeinsam, etwa bei Zinsänderungen oder neuen Regeln. Mehr als ein Viertel des Depots in einer Branche ist ein Klumpen."
        "WAEHRUNG" -> "Bei Werten in Dollar, Pfund oder Franken kommt zur Kursbewegung die des Wechselkurses dazu. Das gleicht sich langfristig oft aus, kann aber Jahre in eine Richtung laufen. ETFs zählen mit ihrer Handelswährung, ein Welt-ETF in Euro steckt trotzdem zum großen Teil in Dollar."
        "HEBEL" -> "Hebelprodukte vervielfachen Gewinne und Verluste und können ganz verfallen. Viele Ratgeber nennen höchstens 5 bis 10 % des Depots."
        "KRYPTO" -> "Kryptowerte schwanken weit stärker als Aktien. Viele Ratgeber nennen höchstens etwa 5 bis 10 %."
        "KOSTEN" -> "Feste Gebühren wiegen bei kleinen Orders schwer: 1 € auf 50 € sind 2 %. Seltener und in größeren Beträgen zu handeln oder ein Sparplan senkt die Kosten."
        "RESERVE" -> "Etwas Guthaben erlaubt Nachkäufe, ohne zu verkaufen. Liegt dagegen der größte Teil ungenutzt, trägt er nichts zur Rendite bei."
        "SPARPLAN" -> "Ein Sparplan legt regelmäßig an, ohne auf den richtigen Zeitpunkt zu warten, und mittelt den Einstiegskurs."
        "VERLUSTE" -> "Ein Wert, der die Hälfte verloren hat, muss sich verdoppeln, um wieder auf den Einstand zu kommen. Prüfe, ob der Grund für den Kauf noch gilt."
        else -> ""
    }
    override val dcNote = "Faustregeln aus verbreiteten Ratgebern. Mehr dazu im Finanzwissen unter Streuung, Risiko und Kosten."
    override fun copyPaperRead(meldungen: Int, unlesbar: Int) =
        (if (meldungen == 1) "Eine Meldung wurde auf Papier eingereicht und liegt nur als Bild vor." else "$meldungen Meldungen wurden auf Papier eingereicht und liegen nur als Bild vor.") +
            " Die App bringt ihre Geschäfte per Texterkennung ausgelesen mit." +
            (if (unlesbar == 1) " Eine Zeile ließ sich nicht sicher lesen und fehlt." else if (unlesbar > 1) " $unlesbar Zeilen ließen sich nicht sicher lesen und fehlen." else "")
    override val derivInvestorIntro = "Call- und Put-Optionen laut 13F. Der Wert ist der Gegenwert der Basiswerte, nicht der Preis der Optionen. Im Depotverlauf zählen sie nicht mit."
    override val derivStructured = "Strukturiertes Produkt"
    override val derivOther = "Option ohne nähere Angaben"
    override fun derivStrike(preis: String) = "Basispreis $preis \$"
    override fun derivExpiry(datum: String) = "Verfall $datum"
    override fun derivContracts(anzahl: String) = "$anzahl Kontrakte"
    override val ctcDays = "Handelstage"
    override fun ctcApprox(betrag: String) = "etwa $betrag"
    override fun ctcCountApprox(anzahl: String, betrag: String) = "$anzahl, etwa $betrag"
    override fun ctcReported(datum: String) = "gemeldet am $datum"
    override val ctcEmpty = "In diesem Monat gibt es keine gemeldeten Geschäfte."
    override val ctcHint = "Grün: an dem Tag mehr gekauft als verkauft, rot: mehr verkauft, kräftiger bei größeren Beträgen. Die Zahl im Feld ist die Zahl der Geschäfte. Es zählt der Tag des Geschäfts, nicht der Tag der Meldung. Beträge sind die Mitte der gemeldeten Spanne."
    override val updNotifTitle = "Neue Fassung von Tryfolio"
    override fun updNotifText(fassung: String) = "Fassung $fassung steht zum Herunterladen bereit."
    override val updSwitchHint = "Die App sieht einmal am Tag bei GitHub nach und meldet jede neue Fassung einmal."
    override fun updRowTitle(fassung: String) = "Fassung $fassung verfügbar"
    override val updRowSubtitle = "Seite zum Herunterladen öffnen"

    override val guideTitle = "Kurzanleitung"
    override fun ppfZeitraum(key: String) = when (key) { "YTD" -> "Seit 1.1."; "Y1" -> "1 Jahr"; "Y3" -> "3 Jahre"; "Y5" -> "5 Jahre"; else -> "Gesamt" }
    override val ppfTtwror = "Zeitgewichtete Rendite (TTWROR)"
    override fun ppfVonBis(von: String, bis: String) = "$von bis $bis"
    override val ppfTtwrorJahr = "TTWROR pro Jahr"
    override val ppfUnterJahr = "erst ab einem Jahr"
    override val ppfIzf = "Interner Zinsfuß (IZF) pro Jahr"
    override val ppfIzfZeitraum = "Interner Zinsfuß (IZF) im Zeitraum"
    override val ppfIzfHinweis = "berücksichtigt, wann wie viel Geld im Depot war"
    override val ppfAbsolut = "Gewinn in Euro"
    override val ppfMaxRueckgang = "Größter Rückgang vom Hoch"
    override val ppfNurWertpapiere = "Nur Wertpapiere"
    override val ppfNurWertpapiereHinweis = "Kursentwicklung ohne Cash, Kosten und Dividenden, wie die Depotkurve"
    override val ppfBilanz = "Performance-Berechnung"
    override val ppfLetzterTag = "Letzter Tag"
    override val ppfRisiko = "Risikokennzahlen"
    override val ppfRueckgangDauer = "Längste Zeit unter dem Hoch"
    override val ppfRueckgangDauerHinweis = "von einem Höchststand, bis er wieder erreicht war oder bis heute"
    override fun ppfTage(n: Long) = if (n == 1L) "1 Tag" else "$n Tage"
    override val ppfSemivolatilitaet = "Schwankung nach unten"
    override val ppfSemivolatilitaetHinweis = "Semivolatilität: zählt nur die Abschnitte unter dem Mittel"
    override val ppfHoch = "Höchststand"
    override fun ppfWertAm(datum: String) = "Wert am $datum"
    override val ppfEinlagen = "Einlagen"
    override val ppfEntnahmen = "Entnahmen"
    override val ppfKursgewinne = "Kursgewinne"
    override fun ppfRealisiert(betrag: String) = "realisiert und nicht realisiert, davon realisiert $betrag"
    override val ppfDividenden = "Dividenden (vor Steuern)"
    override val ppfZinsen = "Zinsen (vor Steuern)"
    override val ppfMonate = "Rendite je Monat und Jahr"
    override val ppfHinweis = "Gerechnet wie in Portfolio Performance: ganzes Depot mit Wertpapieren und Cash, nach Gebühren und Steuern. Geld von außen sind Ein- und Auszahlungen, Wetten und ohne Geld übernommene Positionen (zum Einstand).\n\n• Zeitgewichtete Rendite (TTWROR): misst die Anlage unabhängig vom Zeitpunkt der Ein- und Auszahlungen, geeignet zum Vergleich mit Fonds und Indizes\n• Interner Zinsfuß (IZF): deine persönliche Rendite unter Berücksichtigung, wann wie viel Geld im Depot war\n• Kursgewinne: Rest aus Endwert, Anfangswert, Einlagen, Entnahmen, Erträgen und Kosten\n\nSchwankung und Schwankung nach unten stehen pro Jahr. Unter einem Jahr gilt der Zinsfuß für den Zeitraum und wird nicht hochgerechnet.\n\nBuchungen für Portfolio Performance exportierst du unter Profil › Erweitert."
    override val guideAllesTitel = "Alles, was die App kann"
    override val guideAllesText = "Ein Überblick vorweg. Die Bilder danach zeigen, wie du die App bedienst. Die Kurzanleitung findest du jederzeit wieder im Profil."
    override fun guideFunktionen(widgets: Boolean, bildschutz: Boolean) = listOfNotNull(
        "Übungsdepot mit echten Kursen" to "Aktien, ETFs, Kryptowerte und Derivate mit virtuellem Geld handeln, mit Limit oder Stopp, Gebühr und Steuer eingerechnet. Cash bringt Zinsen.",
        "Sparpläne und Überweisungen" to "Sparpläne kaufen in festen Abständen nach und können Dividenden wiederanlegen. Du findest sie in der Kachel „Orders“ unter Sparpläne. Virtuelles Geld zahlst du per Überweisung ein oder aus.",
        "Wertpapiere finden" to "Suche mit Verlauf „Kürzlich gesucht“, Filter nach Branche, Land und Region und Derivate zu jedem Basiswert.",
        "Wertpapierseite" to "Chart mit erweitertem Chart, Bewertung und Kennzahlen, Chancen und Risiken, Termine, Nachrichten und Trends auf Reddit und Wikipedia. Die Finanzen zeigen Umsatz, Gewinn, Bilanz, Cashflow, Dividende und Gewinn je Aktie. Hintergrund steht vor den Werkzeugen, bei Derivaten zeigt ein Balken Long und Short.",
        "Indikatoren" to "Unter dem Chart öffnet „Indikatoren“ die Auswahl: gleitende Durchschnitte, Bollinger-Bänder, Ichimoku, Parabolic SAR, RSI, Stochastik, MACD, ATR und OBV, einzeln oder zusammen, mit Erklärung.",
        "Werkzeuge und Optionen je Wert" to "Einmalanlage und Sparplan im Rückblick, Positionsgröße, Verkauf mit Steuer, Strategietest und „Wie tief kann es fallen“. Bei US-Aktien zeigen Chancen und Risiken die Optionen mit Put/Call, erwarteter Bewegung und Max Pain.",
        "Politik und Behörden" to "Bei US-Aktien im Abschnitt „Hintergrund“: Lobbyausgaben, Abgeordnete mit Handel, Regierungsaufträge, Wahlkampfspenden, Entlassungsmeldungen, Patente und große Halter.",
        "Märkte und Heatmap" to "Die Kachel „Märkte“ hat die Reiter Heatmap, Trends, Bewegungen, Stimmung und Ausblick. Die Heatmap zeigt den Aktienmarkt oder deine Werte als Kacheln nach Größe und Tagesveränderung, mit zwei Fingern zoombar. Bewegungen zeigt Kursausschläge, Börsengänge und Quartalszahlen der Woche.",
        "Trends und Stimmung" to "Branchen, gefragte Werte und meistbesprochene Werte auf Reddit. Die Stimmung bringt Fear & Greed, AAII-Umfrage, Put/Call und Sichere Häfen. Der Ausblick zeigt Markterwartungen, Polymarket-Wetten mit „Falls es eintritt“, Frühwarnzeichen, Lager und Wirtschaftsdaten.",
        "Analytics und Werkzeuge fürs Depot" to "Portfolio Performance mit Rendite, Zinsfuß und Kapitalbilanz, Depotverlauf, Depot-Check, Anlegerprofil und Selbstcheck, Streuung, Umschichten, Crash-Test, Vermögensaufbau, Entnahmeplan und Dividendenkalender. Statistiken zu Käufen, Verkäufen und Erträgen stehen unter Cash.",
        "Aktienfinder und Radar" to "Die Kachel „Aktienfinder“ hat die Pillen Liste und Radar. Die Liste ordnet Aktien nach Bewertung, Qualität, Analysten, Kurstrend, Insidern, Leerverkäufen und Stimmung ein, mit Gewichten, Gründen und 20 Vorlagen nach finviz. Das Radar sammelt frühe Hinweise wie steigende Erwähnungen und Insiderkäufe.",
        "Copytrading" to "Was US-Abgeordnete, Senatoren, der Präsident und große Investoren kaufen und verkaufen, mit Depotverlauf, Kalender, Positionen und Strategien zum Nachbilden. Eine Glocke meldet Neues.",
        "Rangliste und Kongress kauft" to "Welche Abgeordneten und Senatoren mit ihren Käufen den Markt geschlagen haben, ihr Vermögen laut Jahresmeldung und eine Strategie aus allen gemeldeten Käufen mit Rückblick seit 2024.",
        "Prognosemärkte" to "Auf Ereignisse bei Polymarket und Kalshi wetten, mit Übungsgeld, die größten Wetten der letzten 24 Stunden sehen und verfolgen, was offene und abgeschlossene Wetten gebracht haben.",
        "News und Preisalarme" to "Nachrichten nach Anbietern, Google News, Beiträge von Trump, Aktionärsbriefe und Blogs, Alarme bei Kursmarken. Ein Artikel öffnet die Originalseite, „Nur Text anzeigen“ zeigt den reinen Text.",
        "Finanzwissen" to "Ein Nachschlagewerk zu Börse, Steuern und Strategien, mit Lektionen und Rechnern. Es liegt als Kachel auf der Startseite.",
        "Tages- und Wochenrückblick" to "Jeden Handelstag zur gewählten Uhrzeit ein Fazit: Depot im Vergleich zum MSCI World, Markt, Branchen, wichtige Nachrichten und der wahrscheinliche Grund jeder größeren Bewegung. Freitags die ganze Woche.",
        "Zugänge beantragen" to "Mit kostenlosen eigenen Schlüsseln wird die App vollständiger: Reddit, FEC für Wahlkampfspenden, US-Patentamt, The Guardian, Finnhub, Alpha Vantage und DeepL. Eintragen im Profil unter „Zugänge“, dort steht auch, wo es sie gibt.",
        "Sicherheit" to "App-Sperre mit PIN oder Passwort, Daten mit AES-256 verschlüsselt" + (if (bildschutz) ", Screenshots erlauben oder sperren." else "."),
        if (widgets) "Widgets" to "Depot, Watchlist, Einzelwert, Top & Flop, Dividenden, Märkte, Heatmap, Stimmung, News und frei belegbare Schnellzugriffe auf dem Startbildschirm." else null,
    )
    override val guideSubtitle = "Die Funktionen der App in Bildern"
    override val guideMeldungenTitel = "Geschäfte der Abgeordneten vorab laden"
    override val guideMeldungenText = "Copytrading, Rangliste und die Seiten der US-Aktien zeigen, was Abgeordnete kaufen und verkaufen. Die Meldungen seit 2024 sind in der App enthalten, neuere lädt die App im Hintergrund, auch wenn du die Anleitung schließt."
    override val guideMeldungenLaden = "Alle Meldungen jetzt laden"
    override fun guideMeldungenLaedt(gelesen: Int, gesamt: Int) = if (gesamt == 0) "Verzeichnis wird geladen" else "$gelesen von $gesamt Meldungen gelesen"
    override fun guideMeldungenFertig(gesamt: Int) = if (gesamt == 0) "Alle Meldungen sind schon auf dem Gerät." else "Fertig: $gesamt neue Meldungen gelesen."
    override val guideSkip = "Überspringen"
    override val guidePages = listOf(
        "Depot und Cash" to "Die Startseite zeigt den Wert deiner Wertpapiere und seinen Verlauf. Wisch nach links für Cash mit Guthaben, Statistik und Buchungen, noch einmal für dein Profil. Unter den Positionen liegen die Kacheln, etwa Orders, Märkte und Analytics.",
        "Wertpapiere finden" to "Über „Handeln“ unten auf der Startseite findest du Aktien, ETFs, Derivate und Kryptowerte. Ein Tipp öffnet die Wertpapierseite, ein Wisch nach unten schließt sie.",
        "Kaufen und Sparplan" to "„Kaufen“ legt eine Order an, als Betrag oder Stückzahl, sofort oder mit Limit oder Stopp. Ein Sparplan kauft in festen Abständen nach. Beides liegt in der Kachel „Orders“, die Reiter oben wechseln zwischen Orders und Sparplänen.",
        "Indikatoren" to "Tipp unter dem Chart auf „Indikatoren“ und dann auf die gewünschten Namen. Die Linien erscheinen im Chart, darunter steht, was sie sagen.",
        "Werkzeuge und Optionen" to "Weiter unten auf der Wertpapierseite rechnest du mit den Werkzeugen nach, was eine Einmalanlage oder ein Sparplan gebracht hätte. Die Optionen stehen bei Chancen und Risiken, die Dividende bei Aktien unter Finanzen und bei ETFs unter den Kennzahlen.",
        "Politik und Behörden" to "Bei US-Aktien zeigt der Abschnitt „Hintergrund“ vor den Werkzeugen Lobbying, Gesetze, Regierungsaufträge, Spenden, Entlassungen, Patente und die großen Halter. Leerverkäufe stehen bei Chancen und Risiken.",
        "Heatmap" to "Die Kachel „Märkte“ öffnet die Reiter Heatmap, Trends, Bewegungen, Stimmung und Ausblick. In der Heatmap sind große Kacheln große Firmen, grün stieg, rot fiel. Ein Tipp öffnet den Wert, mit zwei Fingern zoomst du, doppeltes Tippen vergrößert oder setzt zurück, Hineinzoomen zeigt die Namen.",
        "Trends und Stimmung" to "Trends zeigen Branchen, gefragte Werte und was auf Reddit besprochen wird. Bewegungen zeigt die größten Kursausschläge, Börsengänge und Quartalszahlen der Woche, Stimmung die Angst-Gier-Barometer und die „Sicheren Häfen (USA)“.",
        "Märkte: Ausblick" to "„Ausblick“ beginnt mit „Markterwartungen“ zu Zinsen und Rezession. Bei jeder Polymarket-Wette steht „Falls es eintritt“, was das meist für Aktien, Zinsen oder Rohstoffe bedeutet. „Lager und Wirtschaftsdaten“ zeigen früh, wie es um Angebot und Nachfrage steht.",
        "Werkzeuge fürs Depot" to "Die Kachel „Analytics“ auf der Startseite öffnet die Werkzeuge fürs ganze Depot: Depot-Check mit Faustregeln und Anlegerprofil, Depotverlauf, Simulationen und weitere Werkzeuge.",
        "Aktienfinder und Radar" to "Die Kachel „Aktienfinder“ hat die Pillen Liste und Radar. In der Liste wählst du den Markt und tippst auf „Aktien einordnen“, Filter, Sortierung und Vorlagen nach finviz stehen darüber, ein Tipp auf eine Aktie zeigt Punkte und Gründe. Das Radar zeigt Hinweise auf Werte, bevor sie breit beachtet werden.",
        "Copytrading" to "Folge Abgeordneten, Senatoren und Investoren, sieh ihre Geschäfte und ihren Verlauf und bilde ein Depot mit einem Tipp nach. Die Glocke meldet neue Geschäfte.",
        "Rangliste und Kongress kauft" to "Oben in der Liste des Repräsentantenhauses stehen die Rangliste, wessen Käufe den Markt geschlagen haben, und die Strategie aus allen gemeldeten Käufen.",
        "Prognosemärkte" to "Wette mit Übungsgeld auf Ereignisse bei Polymarket und Kalshi. Unter „Große Wetten“ siehst du, worauf gerade viel Geld gesetzt wird.",
        "Zugänge beantragen" to "Viele Bereiche werden mit kostenlosen eigenen Schlüsseln vollständiger, etwa Reddit, Wahlkampfspenden, Patente und weitere Nachrichten. Im Profil unter „Zugänge“ steht, wo du sie beantragst, und dort trägst du sie ein.",
        "Profil" to "Hinter deiner Initiale oben rechts liegen Dokumente, Preisalarme, Steuern, Zugänge und alle Einstellungen. Die Kurzanleitung findest du dort jederzeit wieder.",
    )

    override val guidePagesDesktop get() = guidePages.toMutableList().apply {
        this[0] = "Depot und Cash" to "Die Startseite zeigt den Wert deiner Wertpapiere und seinen Verlauf. Klick oben auf „Cash“ oder zieh die Seite mit gedrückter Maustaste nach links für Guthaben, Statistik und Buchungen, noch einmal für dein Profil. Unter den Positionen liegen die Kacheln, etwa Orders, Märkte und Analytics."
        this[1] = "Wertpapiere finden" to "Über „Handeln“ unten auf der Startseite findest du Aktien, ETFs, Derivate und Kryptowerte. Ein Klick öffnet die Wertpapierseite, Escape oder der Pfeil oben links schließt sie."
    }

    // Entdecken
    override val bestPerformers = "Gewinner"
    override val worstPerformers = "Verlierer"
    override val ipos = "Börsengänge"
    override val iposIntro = "Anstehende Börsengänge erscheinen hier, sobald sie angekündigt sind."
    override val upcoming = "Bevorstehend"
    override val completed = "Abgeschlossen"
    override fun ipoCardTitle(name: String) = "Börsengang $name"
    override fun ipoListed(date: String) = "Erstnotiz $date"
    override fun ipoSinceIssue(pct: String) = "seit Ausgabe $pct"
    override fun ipoAsOf(date: String) = "Stand der Angaben: $date. In Tryfolio lassen sich Börsengänge nicht zeichnen. Gehandelt wird ab der Erstnotiz."
    override val filterTitle = "Filter"
    override fun resultCount(n: Int) = if (n == 1) "1 Ergebnis" else String.format(java.util.Locale.GERMANY, "%,d Ergebnisse", n)
    override val industries = "Branchen"
    override val countries = "Länder"
    override val regions = "Regionen"
    override val assetClass = "Anlageklasse"
    override val resetFilters = "Filter zurücksetzen"
    override val filterSearchPlaceholder = "Suche nach Aktien, ETFs, Kategorien"
    override val noFilterResults = "Keine Treffer für diese Filter."
    override fun industryName(key: String) = when (key) {
        "ALKOHOL_TABAK" -> "Alkohol & Tabak"; "BANKEN" -> "Banken & Finanzen"; "BAU" -> "Baugewerbe & Baumaterial"
        "BERGBAU" -> "Bergbau & Stahl"; "HOLDINGS" -> "Beteiligungen & Holdings"; "BIOTECH" -> "Biotechnologie"
        "CHEMIE" -> "Chemie"; "COMPUTER" -> "Computer & Netzwerk"; "DIENSTLEISTUNGEN" -> "Sonstige Dienstleistungen"
        "ELEKTRO" -> "Elektrische Geräte"; "HALBLEITER" -> "Elektronik & Halbleiter"; "VERSORGER" -> "Energie & Wasser"
        "FAHRZEUGE" -> "Fahrzeuge"; "LUFTFAHRT" -> "Flugzeug & Raumfahrt"; "FORST" -> "Forstwirtschaft"
        "GASTGEWERBE" -> "Gastgewerbe & Touristik"; "GESUNDHEIT" -> "Gesundheit & Medizin"; "HANDEL" -> "Sonstige Handelsfirmen"
        "IMMOBILIEN" -> "Immobilien"; "SOFTWARE" -> "Internet & Software"; "EINZELHANDEL" -> "Kaufhäuser & Einzelhandel"
        "KONSUM" -> "Sonstige Konsumgüter"; "MASCHINEN" -> "Maschinen & Apparate"; "NAHRUNG" -> "Nahrungsmittel"
        "PHARMA" -> "Pharma & Kosmetik"; "MEDIEN" -> "Printmedien"; "ROHSTOFFE" -> "Rohstoffe"
        "TELEKOM" -> "Telekommunikation"; "TEXTIL" -> "Textilien & Bekleidung"; "UMWELT" -> "Umwelt & Recycling"
        "VERKEHR" -> "Verkehr & Transport"; "VERSICHERUNG" -> "Versicherungen"; "OEL_GAS" -> "Öl & Gas"
        "SEKTORUEBERGREIFEND" -> "Über verschiedene Sektoren"; "LARGE_CAPS" -> "Große Firmen"; "MID_CAPS" -> "Mittlere Firmen"
        "SMALL_CAPS" -> "Kleine Firmen"; "UNTERNEHMENSANLEIHEN" -> "Unternehmensanleihen"; "STAATSANLEIHEN" -> "Staatsanleihen"
        "GELDMARKT" -> "Geldmarkt"
        else -> key
    }
    override fun regionName(key: String) = when (key) {
        "ASIEN_PAZIFIK" -> "Asien-Pazifik"; "DEUTSCHLAND" -> "Deutschland"; "EUROPA" -> "Europa"; "GLOBAL" -> "Global"
        "NORDAMERIKA" -> "Nordamerika"; "SCHWELLENLAENDER" -> "Schwellenländer"
        else -> key
    }
    override fun assetClassName(key: String) = when (key) {
        "INDEX" -> "Indizes"; "AKTIE" -> "Aktien"; "ROHSTOFF" -> "Rohstoffe"; "WAEHRUNG" -> "Währungen"; "KRYPTO" -> "Krypto"
        else -> "Andere"
    }
    override fun countryLabel(iso: String) = when (iso) {
        "US" -> "USA"; "GB" -> "Großbritannien"; "EU" -> "Europäische Union"
        else -> java.util.Locale("", iso).getDisplayCountry(java.util.Locale.GERMANY).ifBlank { iso }
    }

    override fun koRefundOn(date: String) = "Rückzahlung $date"
    override fun koAutoSettle(date: String) = "Zwei Tage nach dem Knock-out, am $date, wird das Produkt automatisch ausgebucht und der Restwert dem Verrechnungskonto gutgeschrieben. Mit „Jetzt ausbuchen“ geschieht das sofort."
    override val settleNow = "Jetzt ausbuchen"
    override fun settledNow(amount: String) = "Ausgebucht, Restwert $amount gutgeschrieben"
    override fun firstAddedOn(date: String) = "erstmals $date"

    // Nachrichten
    override fun newsCategory(k: de.troetenrepublik.app.market.Rss.Kategorie) = when (k) {
        de.troetenrepublik.app.market.Rss.Kategorie.MAERKTE -> "Märkte"
        de.troetenrepublik.app.market.Rss.Kategorie.UNTERNEHMEN -> "Unternehmen"
        de.troetenrepublik.app.market.Rss.Kategorie.KONJUNKTUR -> "Wirtschaft"
        de.troetenrepublik.app.market.Rss.Kategorie.ZENTRALBANKEN -> "Zentralbanken"
        de.troetenrepublik.app.market.Rss.Kategorie.ROHSTOFFE_DEVISEN -> "Rohstoffe & Devisen"
        de.troetenrepublik.app.market.Rss.Kategorie.KRYPTO -> "Krypto"
        de.troetenrepublik.app.market.Rss.Kategorie.TRUMP -> "Trump"
        de.troetenrepublik.app.market.Rss.Kategorie.BRIEFE -> "Briefe"
        de.troetenrepublik.app.market.Rss.Kategorie.BLOGS -> "Blogs"
        de.troetenrepublik.app.market.Rss.Kategorie.EIGENE -> "Eigene Feeds"
        de.troetenrepublik.app.market.Rss.Kategorie.GEMERKT -> "Gemerkt"
    }
    override fun newsAnbieter(a: de.troetenrepublik.app.market.Rss.Anbieter) = when (a) {
        de.troetenrepublik.app.market.Rss.Anbieter.ALLE -> "Alle"
        de.troetenrepublik.app.market.Rss.Anbieter.WICHTIG -> "Wichtigste"
        de.troetenrepublik.app.market.Rss.Anbieter.TRUMP -> "Trump"
        de.troetenrepublik.app.market.Rss.Anbieter.BRIEFE -> "Briefe und Blogs"
        de.troetenrepublik.app.market.Rss.Anbieter.TAGESSCHAU -> "tagesschau"
        de.troetenrepublik.app.market.Rss.Anbieter.YAHOO -> "Yahoo Finance"
        de.troetenrepublik.app.market.Rss.Anbieter.GOOGLE -> "Google News"
        de.troetenrepublik.app.market.Rss.Anbieter.PRESSE -> "Presse"
        de.troetenrepublik.app.market.Rss.Anbieter.CNBC -> "CNBC"
        de.troetenrepublik.app.market.Rss.Anbieter.MARKETWATCH -> "MarketWatch"
        de.troetenrepublik.app.market.Rss.Anbieter.GUARDIAN -> "The Guardian"
        de.troetenrepublik.app.market.Rss.Anbieter.FINNHUB -> "Finnhub"
        de.troetenrepublik.app.market.Rss.Anbieter.ALPHAVANTAGE -> "Alpha Vantage"
        de.troetenrepublik.app.market.Rss.Anbieter.NOTENBANKEN -> "Notenbanken"
        de.troetenrepublik.app.market.Rss.Anbieter.BEHOERDEN -> "Behörden"
        de.troetenrepublik.app.market.Rss.Anbieter.EIGENE -> "Eigene Feeds"
        de.troetenrepublik.app.market.Rss.Anbieter.GEMERKT -> "Gemerkt"
    }
    override val newsLoading = "Schlagzeilen werden geladen"
    override val newsEmpty = "Hier gibt es gerade keine Schlagzeilen."
    override val newsEmptySearch = "Keine Schlagzeile passt zur Suche."
    override val newsYesterday = "Gestern"
    override val newsSearchHint = "In den Schlagzeilen suchen"
    override val newsSearch = "Suchen"
    override val newsSources = "Quellen und Mitteilungen"
    override val newsSourcesHint = "Fest eingebaut sind Quellen, deren Bedingungen das Lesen in dieser App mit Quellenangabe erlauben. Eigene Feeds lädt die App nur auf diesem Gerät. Was eine Seite mit ihrem Feed erlaubt, steht in ihren Nutzungsbedingungen."
    override val newsOwnFeeds = "Eigene Feeds"
    override val newsAddFeed = "Feed hinzufügen"
    override val newsFeedUrl = "Adresse des Feeds (RSS oder Atom)"
    override val newsFeedInvalid = "Bitte eine Adresse mit http:// oder https:// eintragen."
    override val newsFeedNone = "Unter dieser Adresse wurde kein Feed gefunden."
    override fun newsFeedAdded(n: Int) = "Feed hinzugefügt, $n Schlagzeilen"
    override val newsFeedChecking = "Feed wird geprüft"
    override val newsRemove = "Entfernen"
    override val newsSave = "Merken"
    override val newsUnsave = "Nicht mehr merken"
    override val newsEmptySaved = "Noch nichts gemerkt. Das Lesezeichen an einer Schlagzeile legt sie hier ab."

    override fun sectorName(key: String): String = Sectors.de[key] ?: Sectors.de[key.replace("—", " - ").replace("–", " - ")] ?: key
    override fun countryName(key: String): String = Sectors.countriesDe[key] ?: key

    // Copytrading
    override val copyMillion = " Mio."
    override val copyTitle = "Copytrading"
    override val copyCongress = "Repräsentantenhaus"
    override val copySenate = "Senat"
    override val copyGovernment = "Regierung"
    override val copySenators = "Senatoren"
    override val copySenateSource = "Quelle: U.S. Senate, Electronic Financial Disclosures (efdsearch.senate.gov), Periodic Transaction Reports. Beträge sind Spannen in US-Dollar. Papiermeldungen sind eingescannt und fehlen in den Auswertungen."
    override val copySenatorOf = "US-Senat"
    override val copyGovIntro = "Geschäfte des US-Präsidenten aus seinen Meldungen an das Office of Government Ethics (Formular 278-T), der Betrag als Spanne. Zeilen ohne sicheres Datum, Art oder Betrag fehlen."
    override fun copyGovSource(stand: String) = "Quelle: U.S. Office of Government Ethics, OGE Form 278-T und 278e" + (if (stand.isBlank()) "" else ", Stand $stand") + ". Beträge sind Spannen in US-Dollar."
    override val copyPresident = "Präsident der USA"
    override val copyVicePresident = "Vizepräsident der USA"
    override fun copyVanceNote(stichtag: String) = "Bestand laut Jahresbericht (OGE Form 278e) zum $stichtag, mit Werten als Spanne. Einzelgeschäfte meldet Vance kaum, weil Fonds und ETFs von der Meldepflicht für Geschäfte ausgenommen sind. Die Anteile richten sich nach der Mitte der Spannen."
    override val copyFilingsAll = "Meldungen"
    override val copyTypeMuniBond = "Kommunalanleihe"
    override fun copyTradesCount(n: Int) = if (n == 1) "1 Geschäft" else "${"%,d".format(java.util.Locale.GERMANY, n)} Geschäfte"
    override fun copyHoldingsAsOf(date: String) = "Bestand vom $date"
    override val copyWhoTitle = "Wer handelt den Wert"
    override val copyWhoNone = "Keine Geschäfte bekannter Personen in den letzten zwölf Monaten"
    override val copyWhoIntro = "Abgeordnete, Senatoren und die US-Regierung mit ihren gemeldeten Geschäften der letzten zwölf Monate, Investoren mit der Veränderung im letzten gemeldeten Quartal."
    override val copyTendBuy = "Eher Käufe"
    override val copyTendSell = "Eher Verkäufe"
    override val copyTendMixed = "Ausgeglichen"
    override fun copyWhoCounts(kaufen: Int, verkaufen: Int, halten: Int) = "$kaufen kaufen, $verkaufen verkaufen, $halten halten"
    override val copyWhoTendency = "Tendenz"
    override val copyWhoPersons = "Personen"
    override val copyWhoBuyers = "Kaufen"
    override val copyWhoSellers = "Verkaufen"
    override fun copyWhoHouseOpen(offen: Int, gesamt: Int) = "$offen von $gesamt Meldungen des Repräsentantenhauses aus den letzten zwölf Monaten hat die App noch nicht gelesen. Ihre Geschäfte fehlen hier noch."
    override val copyWhoHouseLoad = "Alle Meldungen lesen (einmalig, danach auf dem Gerät)"
    override fun copyWhoHouseReading(fertig: Int, gesamt: Int) = "Meldungen werden gelesen: $fertig von $gesamt"
    override val copyWhoSource = "Quellen: Meldungen an das US-Repräsentantenhaus, den Senat, die Ethikbehörde und die Börsenaufsicht. Beträge sind Schätzwerte aus den gemeldeten Spannen. Im Einzelnen: Clerk of the U.S. House of Representatives, U.S. Senate (efdsearch.senate.gov), U.S. Office of Government Ethics, SEC EDGAR (Form 13F-HR)."
    override fun copyWhoShare(pct: String) = "$pct des Depots"
    override fun copyWhoTrades(kaeufe: Int, verkaeufe: Int) =
        listOfNotNull(
            if (kaeufe == 0) null else if (kaeufe == 1) "1 Kauf" else "$kaeufe Käufe",
            if (verkaeufe == 0) null else if (verkaeufe == 1) "1 Verkauf" else "$verkaeufe Verkäufe",
        ).joinToString(", ")
    override val copyWhoMixed = "Gemischt"
    override fun copyWhoStueck(stichtag: String, veraenderung: String, bestand: String, wert: String) = "Quartal bis $stichtag: $veraenderung Stück, Bestand $bestand Stück ($wert)"
    override fun copyWhoStueckNeu(stichtag: String, bestand: String, wert: String) = "Quartal bis $stichtag: neu gekauft, $bestand Stück ($wert)"
    override fun copyWhoStueckWeg(stichtag: String, vorher: String) = "Quartal bis $stichtag: alle $vorher Stück verkauft"
    override fun copyWhoAlle(n: Int) = "Alle $n Geschäfte zeigen"
    override fun copyWhoAllePersonen(n: Int) = "Alle $n Personen zeigen"
    override val copyWhoWeniger = "Weniger zeigen"
    override val copyWhoHolds = "Hält"
    override val copyWhoNewPos = "Neu gekauft"
    override val copyWhoIncreased = "Aufgestockt"
    override val copyWhoReduced = "Reduziert"
    override val copyWhoSoldOut = "Ganz verkauft"
    override val copyWhoUnchanged = "Unverändert gehalten"
    override val copyPhotoCongress = "Foto: U.S. Government Publishing Office, gemeinfrei"
    override val copyBellOn = "Benachrichtigen"
    override val copyBellOff = "Nicht mehr benachrichtigen"
    override fun copyBellOnToast(name: String) = "Mitteilungen für $name an"
    override fun copyBellOffToast(name: String) = "Mitteilungen für $name aus"
    override val copyBellNotificationsOff = "Mitteilungen sind im Profil ausgeschaltet. Dort lassen sie sich einschalten."
    override val copyBellsTitle = "Mitteilungen bei Copytrading"
    override fun copyBellsSubtitle(n: Int) = when (n) { 0 -> "Bei neuen Meldungen von Personen mit Glocke"; 1 -> "1 Person mit Glocke"; else -> "$n Personen mit Glocke" }
    override val copyBellsIntro = "Die Glocke oben auf der Seite einer Person schaltet Mitteilungen für ihre neuen Meldungen ein. Die App prüft etwa alle sechs Stunden, auch wenn sie geschlossen ist. Für Senatoren meldet sie sich, wenn eine neue Fassung der App neue Meldungen mitbringt."
    override val copyBellsIntroDesktop = "Die Glocke oben auf der Seite einer Person schaltet Mitteilungen für ihre neuen Meldungen ein. Die App prüft alle sechs Stunden, solange sie geöffnet ist. Für Senatoren meldet sie sich, wenn eine neue Fassung der App neue Meldungen mitbringt."
    override val copyBellsEmpty = "Noch keine Person mit Glocke. Die Glocke sitzt oben auf der Seite jeder Person."
    override val copyBellsAdd = "Personen in Copytrading suchen"
    override val copyBellHouse = "Repräsentantenhaus · prüft das Verzeichnis des Clerk"
    override val copyBellSenate = "Senat · mit jeder neuen Fassung der App"
    override val copyBellGovernment = "Regierung · prüft whitehouse.gov"
    override val copyBellInvestor = "Investor · prüft die SEC auf neue 13F-Meldungen"
    override val imTitel = "Registerdaten importieren"
    override val imUntertitel = "Selbst heruntergeladene Insider- und Leerverkaufslisten (BSE Indien, Consob Italien, Bundesanzeiger, umgewandelte SEDI-Berichte) und Meldungen nach Art. 19 MAR als PDF. Sie bleiben auf dem Gerät."
    override fun imInsider(n: Int, land: String) = "$n Insidergeschäfte (${landName(land)}) übernommen"
    override fun imLeer(n: Int, land: String) = "Leerverkaufspositionen für $n Aktien (${landName(land)}) übernommen"
    override val imPdf = "In dieser PDF fand die App kein lesbares Formular nach Art. 19 MAR. SEDI-Berichte bitte erst umwandeln lassen."
    override val imUnbekannt = "Diese Datei kennt die App nicht. Erkannt werden die BSE-Gesamtliste (CSV), die Consob-Tabelle (XLSX), die Leerverkaufsliste des Bundesanzeigers (CSV), Meldungen nach Art. 19 MAR (PDF) und Registerdateien der App (JSON)."
    private fun landName(l: String) = when (l) {
        "IN" -> "Indien"; "CA" -> "Kanada"; "IT" -> "Italien"; "DE" -> "Deutschland"; "AU" -> "Australien"; "HK" -> "Hongkong"
        "DK" -> "Dänemark"; "FI" -> "Finnland"; "IS" -> "Island"; "EE" -> "Estland"; "LV" -> "Lettland"; "LT" -> "Litauen"; "CZ" -> "Tschechien"; "LU" -> "Luxemburg"
        "CY" -> "Zypern"; "HU" -> "Ungarn"; "PL" -> "Polen"; "LI" -> "Liechtenstein"; "MT" -> "Malta"; "HR" -> "Kroatien"; "AT" -> "Österreich"; "BE" -> "Belgien"
        "ES" -> "Spanien"; "PT" -> "Portugal"; "IE" -> "Irland"; "GR" -> "Griechenland"; "SI" -> "Slowenien"; "SK" -> "Slowakei"; "RO" -> "Rumänien"; "BG" -> "Bulgarien"; "NO" -> "Norwegen"
        else -> l
    }
    override val lwTitel = "Lobbyarbeit in Europa"
    override val lwHinweis = "So viel gibt die Firma laut den Lobbyregistern im Jahr aus, um auf Gesetze und Behörden Einfluss zu nehmen. Die Register nennen Spannen."
    override fun lwRegister(l: String) = when (l) { "EU" -> "EU-Transparenzregister"; "DE" -> "Lobbyregister des Bundestages"; "FR" -> "Lobbyregister Frankreich"; else -> l }
    override fun lwSpanne(von: String, bis: String) = "$von bis $bis"
    override fun lwAb(von: String) = "ab $von"
    override fun lwJahr(von: String, bis: String) = "Geschäftsjahr $von bis $bis"
    override fun lwFte(n: String) = "$n Vollzeitstellen für Lobbyarbeit"
    override fun lwTaetigkeiten(n: Int) = if (n == 1) "1 gemeldete Tätigkeit" else "$n gemeldete Tätigkeiten"
    override fun lwThemen(t: String) = "Themen: $t"
    override val lwEintrag = "Eintrag im Register öffnen"
    override fun lwQuelle(laender: List<String>) = "Quelle: " + laender.joinToString(", ") { when (it) {
        "EU" -> "EU-Transparenzregister (transparency-register.europa.eu)"
        "DE" -> "Lobbyregister des Deutschen Bundestages (lobbyregister.bundestag.de)"
        "FR" -> "HATVP, Répertoire des représentants d'intérêts (hatvp.fr, Licence Ouverte)"
        else -> it
    } }
    override fun copyNotifOge(n: Int) = (if (n == 1) "Eine neue Meldung" else "$n neue Meldungen") + " bei der US-Ethikbehörde OGE. Die Geschäfte kommen ausgelesen mit der nächsten Fassung der App."
    override val importPortfolioJson = "Depot importieren (Sicherung oder Broker-Export)"
    override val exportPortfolioJson = "Depot exportieren (JSON)"
    override val importChoice = "Zusammenführen nimmt Guthaben, Positionen, Buchungen, Sparpläne und Merkliste der Datei zum aktuellen Depot dazu. Ersetzen tauscht das aktuelle Depot gegen die Datei."
    override val importMerge = "Zusammenführen"
    override val importReplace = "Ersetzen"
    override val depotMerged = "Depots zusammengeführt"
    override fun copyPhotoCredit(autor: String, lizenz: String) = "Foto: $autor, $lizenz, über Wikimedia Commons"
    override val copyPhotosCongress = "Fotos: U.S. Government Publishing Office (gemeinfrei)."
    override val copyPhotosCommons = "Fotos: Wikimedia Commons, Urheber und Lizenz stehen auf der Seite der Person. Ohne freies Foto stehen die Initialen."
    override val copyInvestors = "Investoren"
    override val copyStrategies = "Strategien"
    override val copyCongressIntro = "Geschäfte von Abgeordneten des US-Repräsentantenhauses nach dem STOCK Act, gemeldet bis zu 45 Tage danach, der Betrag als Spanne."
    override val copySearchMember = "Name oder Bundesstaat (etwa CA)"
    override val copyLoading = "Meldungen werden geladen"
    override val copyUnavailable = "Die Daten sind gerade nicht erreichbar. Später noch einmal versuchen."
    override val copyLatestTrades = "Neueste Geschäfte"
    override val copyMembers = "Abgeordnete"
    override fun copyFilings(n: Int) = if (n == 1) "1 Meldung" else "$n Meldungen"
    override fun copyLast(date: String) = "zuletzt $date"
    override val copyCongressSource = "Quelle: Clerk of the U.S. House of Representatives, Financial Disclosure Reports. Die Beträge sind Schätzwerte aus den Spannen."
    override val copyFollowing = "Gefolgt"
    override val copySell = "Verkauf"
    override val copyPartialSale = "Teilverkauf"
    override val copyExchange = "Tausch"
    override val copyTypeOption = "Option"
    override val copyTypeGovBond = "Staatsanleihe"
    override val copyTypeCorpBond = "Anleihe"
    override val copyTypeFund = "Fonds"
    override fun copyOver(v: String) = "über $v"
    override fun copyHouseOf(district: String) = "Repräsentantenhaus · $district"
    override val copyFilings12m = "Meldungen (12 Monate)"
    override val copyBuysEst = "Käufe (geschätzt)"
    override val copySalesEst = "Verkäufe (geschätzt)"
    override val copyReplicate = "Nachbilden"
    override val copyMostTraded = "Meistgehandelt"
    override fun copyBuySellEst(buy: String, sell: String, n: Int) = "Käufe ≈ $buy, Verkäufe ≈ $sell · " + if (n == 1) "1 Geschäft" else "$n Geschäfte"
    override val copyAllTrades = "Alle Geschäfte"
    override fun copyFiledOn(date: String) = "Gemeldet am $date"
    override fun copyPaper(n: Int, dates: String) = if (n == 1) "Eine Meldung wurde auf Papier eingereicht ($dates). Sie liegt nur als Bild vor, die App kann ihren Inhalt nicht auslesen." else "$n Meldungen wurden auf Papier eingereicht ($dates). Sie liegen nur als Bild vor, die App kann ihren Inhalt nicht auslesen."
    override val copyNoFilings = "Keine Meldungen in den letzten zwölf Monaten."
    override fun copyNote(name: String) = "Nachgebildet: $name"
    override val copyFollow = "Folgen"
    override val copyUnfollow = "Nicht mehr folgen"
    override val copyInvestorsIntro = "Bestände bekannter Investoren aus ihren 13F-Meldungen an die SEC, vierteljährlich bis zu 45 Tage nach Quartalsende, nur US-Werte. Die App meldet, wenn eine neuere Meldung eingeht."
    override fun copyInvestorLine(date: String, n: Int, value: String) = "Stand $date · $n Positionen · $value"
    override val copyInvestorsSource = "Quelle: SEC EDGAR, Form 13F-HR. Werte in US-Dollar zum Stichtag. Optionen stehen mit dem Wert der Aktien dahinter. Barmittel, Leerverkäufe und Aktien außerhalb der USA fehlen in diesen Meldungen."
    override fun copyNewer13f(asOf: String, filed: String, shown: String) = "Bei der SEC liegt eine neuere Meldung zum $asOf vor (eingereicht am $filed). Hier steht noch der Stand vom $shown. Die neue Liste kommt mit dem nächsten Update der App."
    override val copyAsOf = "Stichtag"
    override val copyFiled = "Gemeldet"
    override val copyHoldings = "Positionen"
    override val copySoldOut = "Seit dem Vorquartal verkauft"
    override val copyCall = "Call-Option"
    override val copyPut = "Put-Option"
    override val copyNew = "neu"
    override fun copyIncreased(pct: String) = "aufgestockt um $pct"
    override fun copyReduced(pct: String) = "verringert um $pct"
    override val copySold = "verkauft"
    override val copyStrategiesIntro = "Anlagestrategien als Musterdepots aus ETFs. Die Kennzahlen rechnet die App aus den Kursen der letzten zehn Jahre."
    override val copyDefensive = "Defensiv"
    override val copyBalanced = "Ausgewogen"
    override val copyOffensive = "Wachstum"
    override val copyStrategiesNote = "Vergangene Wertentwicklung ohne Kosten und Steuern, bei täglich gleicher Gewichtung. Ausschüttungen ausschüttender ETFs sind nicht eingerechnet."
    override val copyAllocation = "Aufteilung"
    override val copyHistory = "Entwicklung"
    override val copyNoHistory = "Für diese ETFs liegen gerade keine Kurse vor."
    override fun copyRange(from: String, to: String) = "$from bis $to"
    override val copyMaxDrawdown = "Größter Rückgang"
    override val copyYears = "Je Kalenderjahr"
    override val copyAsPlans = "Als Sparpläne anlegen"
    override val copyFollowedIntro = "Bei neuen Meldungen schickt die App eine Mitteilung, solange Mitteilungen eingeschaltet sind."
    override fun copyReplicateTitle(name: String) = "$name nachbilden"
    override val copyReplicateHint = "Der Betrag wird nach den Anteilen auf die Werte verteilt. Außerhalb der Handelszeit legt die App Limit-Orders 2 % über dem letzten Kurs an, die zur nächsten Eröffnung ausgeführt werden."
    override fun copyCash(v: String) = "Verfügbar: $v"
    override val copyCount = "Werte"
    override val copyNothingToCopy = "Keine Aktien zum Nachbilden."
    override fun copyFees(n: Int, v: String) = (if (n == 1) "1 Kauf" else "$n Käufe") + ", Gebühren $v"
    override val copyWorking = "Wird ausgeführt"
    override val copyReplicateNow = "Jetzt nachbilden"
    override fun copyPlansTitle(name: String) = "Sparpläne für $name"
    override val copyPlansHint = "Ein Sparplan je ETF, der Monatsbetrag nach den Anteilen aufgeteilt. Sparpläne kosten keine Gebühr."
    override val copyMonthly = "Monatlich"
    override val copyPlanDay = "Ausführung am"
    override val copyPlansCreate = "Sparpläne anlegen"
    override fun copyDone(bought: Int, orders: Int) = "Nachgebildet: $bought gekauft" + if (orders > 0) ", $orders Limit-Orders angelegt" else ""
    override fun copyPlansDone(n: Int) = if (n == 1) "1 Sparplan angelegt" else "$n Sparpläne angelegt"
    override fun copyNotifTitle(name: String) = "Neue Meldung: $name"
    override fun copyNotifTrades(n: Int, list: String) = (if (n == 1) "1 Geschäft: " else "$n Geschäfte: ") + list
    override val copyNotifPaper = "Auf Papier eingereicht, der Inhalt lässt sich nicht auslesen."
    override fun copyNotif13f(asOf: String, filed: String) = "Neue 13F-Meldung zum $asOf, eingereicht am $filed."

    // Anzeige in US-Dollar
    override val switchCurrency = "Währung der Anzeige wechseln"
    override val usdUnavailable = "Der Dollarkurs ist gerade nicht verfügbar."
    override val regLandTitel = "Wo lebst du?"
    override val regLandText = "Danach richten sich Währung und Handelszeiten. Beides kannst du später im Profil ändern."
    override val regVorschlag = "Vorschlag nach der Zeitzone deines Geräts"
    override val regAndere = "Weitere Länder"
    override val regWaehrungTitel = "Deine Währung"
    override val regWaehrungText = "Darin zeigt die App Depot, Kurse und Guthaben. Ein Tipp auf den Depotwert schaltet weiter auf US-Dollar und Euro. Orders, Steuern und Belege bleiben in Euro."
    override fun waehrungName(code: String) = when (code) {
        "EUR" -> "Euro"; "USD" -> "US-Dollar"; "CHF" -> "Schweizer Franken"; "GBP" -> "Britisches Pfund"; "SEK" -> "Schwedische Krone"
        "DKK" -> "Dänische Krone"; "NOK" -> "Norwegische Krone"; "PLN" -> "Polnischer Złoty"; "CZK" -> "Tschechische Krone"; "HUF" -> "Ungarischer Forint"
        "CAD" -> "Kanadischer Dollar"; "MXN" -> "Mexikanischer Peso"; "BRL" -> "Brasilianischer Real"; "JPY" -> "Japanischer Yen"; "CNY" -> "Chinesischer Yuan"
        "HKD" -> "Hongkong-Dollar"; "TWD" -> "Taiwan-Dollar"; "KRW" -> "Südkoreanischer Won"; "INR" -> "Indische Rupie"; "SGD" -> "Singapur-Dollar"
        "ILS" -> "Israelischer Schekel"; "ZAR" -> "Südafrikanischer Rand"; "AUD" -> "Australischer Dollar"; "NZD" -> "Neuseeland-Dollar"
        else -> code
    }
    override val regZeitenTitel = "Handelszeiten"
    override val regZeitenText = "Wann Orders ausgeführt werden, in deiner Ortszeit. Krypto handelt immer rund um die Uhr. Feiertage berücksichtigt die App nicht."
    override fun regZeitenLand(land: String) = "Wie an der Börse in $land"
    override fun regZeitenLandHint(boerse: String, zeiten: String, deutschland: Boolean) =
        if (deutschland) "$boerse: Aktien und ETFs 7:30–23:00, Derivate 8:00–22:00" else "$boerse, werktags $zeiten Ortszeit"
    override val regZeitenEigene = "Eigene Zeiten"
    override val regZeitenEigeneHint = "Werktags in dem Zeitraum, den du festlegst"
    override val regZeitenImmer = "Rund um die Uhr"
    override val regZeitenImmerHint = "Jeden Tag, auch am Wochenende"
    override fun regVonBis(von: String, bis: String) = "Werktags $von–$bis Uhr"
    override val regionTitel = "Land, Währung und Handelszeiten"
    override val regAnzeige = "Gerade angezeigt"
    override val regAnzeigeHint = "Ein Tipp auf den Depotwert, das Guthaben oder einen Kurs schaltet weiter. Gebucht wird in Euro."
    override fun regSteuerHinweis(land: String) = "Steuern rechnet die App nach deutschem Recht (Abgeltungsteuer, Freistellungsauftrag). Für $land gilt das nicht. Die Beträge nach Steuern sind dann nur ein Beispiel."
    override fun regWissenUs(n: Int) = "Im Finanzwissen sind für die USA $n Lektionen angepasst, etwa zu Steuern, Altersvorsorge und Krediten. Jede sagt das oben und zeigt auf Wunsch die deutsche Fassung."
    override fun fxRate(von: String, nach: String, kurs: String): String {
        fun z(c: String) = de.troetenrepublik.app.engine.Derivatives.currencySymbol(c)
        return "1 ${z(von)} = $kurs ${z(nach)}"
    }
    override fun tradingHoursFor(aktien: String?, derivate: String?, wochenende: Boolean, ortszeit: Boolean) =
        if (aktien == null) "Alles rund um die Uhr, auch am Wochenende"
        else listOfNotNull("Aktien & ETFs $aktien", derivate?.takeIf { it != aktien }?.let { "Derivate $it" }, "Krypto rund um die Uhr").joinToString("\n") +
            (if (ortszeit) "\nOrtszeit, werktags" else "")
    override val rrTitel = "Wie tief kann es fallen"
    override val rrLaedt = "Kurse der letzten zehn Jahre werden geladen"
    override val rrKeine = "Für diesen Wert gibt es zu wenige Kurse."
    override fun rrRendite(jahre: Int) = if (jahre == 1) "Rendite im letzten Jahr" else "Rendite pro Jahr, $jahre Jahre"
    override val rrLaengster = "Längster Rückgang"
    override fun rrDauer(tage: Long): String {
        val j = (tage / 365.25).toInt()
        val m = ((tage - j * 365.25) / 30.44).toInt()
        return when {
            j > 0 && m > 0 -> "$j ${if (j == 1) "Jahr" else "Jahre"} $m ${if (m == 1) "Monat" else "Monate"}"
            j > 0 -> "$j ${if (j == 1) "Jahr" else "Jahre"}"
            m > 0 -> "$m ${if (m == 1) "Monat" else "Monate"}"
            else -> "$tage ${if (tage == 1L) "Tag" else "Tage"}"
        }
    }
    override val rrLaeuft = "(läuft noch)"
    override fun rrBeta(index: String) = "Beta zum $index"
    override fun rrKorrelation(index: String) = "Korrelation mit dem $index"
    override fun rrHinweis(ab: String, index: String?, beta: String?) =
        "Aus den Schlusskursen seit $ab, ohne Dividenden. Größter Rückgang: tiefster Stand unter einem früheren Hoch. Längster Rückgang: die längste Zeit, bis ein früheres Hoch wieder erreicht war." +
            (if (index != null && beta != null) " Beta $beta heißt: Bewegte sich der $index um 1 %, bewegte sich der Wert im Mittel um $beta %. Die Korrelation reicht von 1 (laufen gleich) bis −1 (laufen gegeneinander). Beide aus Wochenkursen der letzten fünf Jahre." else "") +
            ""
    override val trendsTitel = "Trends"
    override val trBranchenHinweis = "Gemessen an US-Branchen-ETFs, Kurse ohne Dividenden. Sie stehen für die Branche weltweit nur ungefähr. Ein Tipp zeigt den Verlauf."
    override val trReddit = "Auf Reddit und 4chan viel besprochen"
    override val trRedditHinweis = "Erwähnungen in Beiträgen und Kommentaren laut ApeWisdom.\n" +
        "• Aktien: alle Aktien-Subreddits wie r/wallstreetbets, r/stocks und r/investing\n" +
        "• Krypto: Krypto-Subreddits wie r/CryptoCurrency und r/Bitcoin\n" +
        "• 4chan /biz/: das Wirtschaftsforum von 4chan\n" +
        "• 24 Std.: Prozent gegenüber den 24 Stunden davor\n" +
        "• Woche, Monat und Jahr: aus dem Verlauf, den die App selbst mitschreibt\n" +
        "Viel Aufmerksamkeit geht oft mit starken Kursschwankungen einher."
    override val trFilterDeutsch = "Deutsche Subreddits"
    override val sbTitel = "Stimmungsbarometer"
    override val sbCnn = "Fear & Greed Aktien (USA)"
    override val sbQuelleCnn = "Quelle: CNN"
    override val sbAaii = "AAII Privatanleger (USA)"
    override val sbQuelleAaii = "Quelle: AAII"
    override fun sbAaiiMehr(bullen: String, neutral: String, baeren: String, datum: String) =
        "Umfrage vom $datum: $bullen % erwarten steigende Kurse in den nächsten sechs Monaten, $neutral % keine große Änderung, $baeren % fallende Kurse. Das Barometer zeigt den Anteil der Bullen unter Bullen und Bären. Langjähriger Schnitt etwa 37 % Bullen und 31 % Bären."
    override val sbPutCall = "Put/Call-Verhältnis"
    override fun sbPutCallMehr(wert: String) = "Auf jede gehandelte Kaufoption (Call) auf US-Aktien kamen am letzten Handelstag $wert Verkaufsoptionen (Puts). Viele Puts zeigen Angst, wenige Gier. Der Wert 100 entspricht einem Verhältnis von 0,4 oder weniger, der Wert 0 einem von 1,0 oder mehr."
    override fun wqPcZeile(gesamt: String?, index: String?, tag: String) =
        listOfNotNull(gesamt?.let { "Alle Optionen $it" }, index?.let { "Indizes $it" }, "Handelstag $tag").joinToString(" · ")
    override val wqPcQuelle = "Quelle: Cboe · © Cboe Exchange, Inc."
    override val sbKrypto = "Fear & Greed Krypto"
    override val sbWsb = "Reddit r/wallstreetbets"
    override val sbQuelleKrypto = "Quelle: alternative.me"
    override val sbQuelleWsb = "Quelle: Tradestie"
    override fun sbRedditWert(prozent: Int) = "$prozent % der Kommentare mit klarem Ton sind positiv. Der Wert sagt keinen Kurs voraus. Die Kursänderung über 5 Handelstage steht neben jedem Wert."
    override fun sbStufe(stufe: Int) = listOf("Extreme Angst", "Angst", "Neutral", "Gier", "Extreme Gier").getOrElse(stufe) { "" }
    override fun sbTeil(schluessel: String) = when (schluessel) {
        "momentum" -> "Momentum des S&P 500"
        "breite" -> "Marktbreite"
        "vix" -> "Schwankungsindex VIX"
        "stress" -> "Finanzstress"
        "sicher" -> "Aktien gegen Staatsanleihen"
        "market_momentum_sp500" -> "Momentum des S&P 500"
        "stock_price_strength" -> "Neue 52-Wochen-Hochs und -Tiefs"
        "stock_price_breadth" -> "Marktbreite"
        "put_call_options" -> "Put/Call-Verhältnis"
        "market_volatility_vix" -> "Schwankungsindex (VIX)"
        "junk_bond_demand" -> "Nachfrage nach Hochzinsanleihen"
        "safe_haven_demand" -> "Nachfrage nach sicheren Häfen"
        else -> schluessel
    }
    override val sbVortag = "Vortag"
    override val sbWoche = "Woche"
    override val sbMonat = "Monat"
    override val sbWsbSteigend = "Positiv besprochen"
    override val sbWsbFallend = "Negativ besprochen"
    override val sbHinweis = "Die Barometer reichen von 0 (extreme Angst) bis 100 (extreme Gier).\n\n" +
        "• Aktien USA: Fear & Greed von CNN aus sieben Messgrößen\n" +
        "• Gesamtstimmung: alle Barometer, die Frühwarnzeichen und weitere freie Stimmungsmaße gewichtet\n" +
        "• Krypto: Fear & Greed von alternative.me\n" +
        "• Privatanleger USA: Anteil der Optimisten in der wöchentlichen AAII-Umfrage\n" +
        "• Put/Call: Verkaufs- je Kaufoption auf US-Aktien an der Cboe am letzten Handelstag\n" +
        "• Sorgen: wie viel über Rezession und Börsencrash auf Wikipedia gelesen wird\n" +
        "• r/wallstreetbets: Anteil der Kommentare zu den 50 meistbesprochenen Werten, die auf steigende Kurse setzen\n" +
        "• Sichere Häfen: wie viel besser Aktien als Staatsanleihen laufen, im Vergleich zum letzten Jahr, von Yahoo Finance\n" +
        "• Schwankung: wie ruhig der VIX im Vergleich zum letzten Jahr ist, von Yahoo Finance\n\n" +
        "Ein Tipp auf eine Kachel zeigt Verlauf und Einzelheiten."
    override fun sbKachel(schluessel: String) = when (schluessel) {
        "cnn" -> "Aktien USA"; "aktien" -> "Aktien USA"; "gesamt" -> sgKachel; "krypto" -> "Krypto"; "aaii" -> "Privatanleger USA"; "wsb" -> "r/wallstreetbets"; "sicher" -> "Sichere Häfen"; "sorgen" -> "Sorgen"; "vix" -> "Schwankung"; "putcall" -> "Put/Call"; else -> ""
    }
    override fun sbAnbieter(schluessel: String) = when (schluessel) {
        "cnn" -> "CNN Fear & Greed"; "gesamt" -> sgAnbieter; "aktien" -> "Eigener Index"; "krypto" -> "alternative.me"; "aaii" -> "AAII-Umfrage"; "wsb" -> "Tradestie"; "sicher" -> "S&P 500 gegen Anleihen"; "sorgen" -> "Wikipedia"; "vix" -> "VIX via Yahoo"; "putcall" -> "Optionen bei Cboe"; else -> ""
    }
    override fun sbSteigend(prozent: Int) = "$prozent % positiv"
    override val fwTitel = "Frühwarnzeichen"
    override fun fwZusammen(rot: Int, gelb: Int, n: Int) = when {
        rot == 0 && gelb == 0 -> "Alle $n auf Grün"
        rot == 0 -> "$gelb von $n auf Gelb, keines auf Rot"
        gelb == 0 -> "$rot von $n auf Rot"
        else -> "$rot von $n auf Rot, $gelb auf Gelb"
    }
    override fun fwName(schluessel: String) = when (schluessel) {
        "zinskurve" -> "Zinskurve USA"
        "sahm" -> "Sahm-Regel"
        "vix" -> "Schwankungsindex VIX"
        "vix_struktur" -> "VIX-Terminstruktur"
        "trend_sp500" -> "Trend S&P 500"
        "trend_dax" -> "Trend DAX"
        "stress" -> "Finanzstress"
        "hochzins" -> "Hochzins-Spread USA"
        else -> schluessel
    }
    override fun fwLage(schluessel: String, ampel: Int) = when (schluessel) {
        "zinskurve" -> listOf("Normal, längere Laufzeiten bringen mehr Zins", "Invers, kurze Laufzeiten bringen mehr Zins", "Nach Inversion wieder normal, oft kurz vor Rezessionen")[ampel]
        "sahm" -> listOf("Arbeitsmarkt stabil", "Arbeitslosigkeit steigt merklich", "Schwelle von 0,5 erreicht, typisch für Rezessionen")[ampel]
        "vix" -> listOf("Ruhiger Markt", "Erhöhte Nervosität", "Hohe Angst am Markt")[ampel]
        "vix_struktur" -> listOf("Normal, auf Sicht mehr Unsicherheit als heute", "Fast flach, kurzfristige Sorge wächst", "Invers, akuter Stress am Markt")[ampel]
        "trend_sp500", "trend_dax" -> listOf("Deutlich über der 200-Tage-Linie", "Knapp über der 200-Tage-Linie", "Unter der 200-Tage-Linie")[ampel]
        "stress" -> listOf("Weniger Stress als im Schnitt", "Mehr Stress als im Schnitt", "Deutlich erhöhter Stress")[ampel]
        "hochzins" -> listOf("Geringer Aufschlag, Geldgeber gelassen", "Aufschlag erhöht oder steigend", "Aufschlag hoch oder schnell gestiegen")[ampel]
        else -> ""
    }
    override fun fwErklaerung(schluessel: String) = when (schluessel) {
        "zinskurve" -> "Rendite zehnjähriger US-Staatsanleihen minus Rendite dreimonatiger. Vor jeder US-Rezession seit 1968 lag sie eine Zeit lang unter null, mit einem Vorlauf von etwa 6 bis 24 Monaten. Die Rezession beginnt oft erst, wenn die Kurve wieder steigt. Schwäche: 2022 bis 2024 war sie lange invers, ohne dass bisher eine Rezession folgte."
        "sahm" -> "Dreimonatsschnitt der US-Arbeitslosenquote minus sein Tief der vorangegangenen zwölf Monate. Ab 0,5 Prozentpunkten hat die Regel seit 1970 jede US-Rezession früh angezeigt. Schwäche: 2024 wurde die Schwelle kurz erreicht, weil vor allem mehr Menschen Arbeit suchten."
        "vix" -> "Erwartete Schwankung des S&P 500 für die nächsten 30 Tage, abgeleitet aus Optionspreisen. Werte über 30 gibt es fast nur in Abschwüngen. Der VIX warnt eher während eines Einbruchs als davor. Sehr niedrige Werte zeigen Sorglosigkeit."
        "vix_struktur" -> "VIX geteilt durch den dreimonatigen VIX3M. Normalerweise ist die Unsicherheit auf drei Monate größer als für 30 Tage, der Wert liegt unter 1. Steigt er über 1, erwarten Anleger akut mehr Schwankung als später. Das kam bei fast allen großen Einbrüchen vor."
        "stress" -> "Wochenindex der St. Louis Fed aus 18 Marktreihen, darunter Zinsen und sechs Renditeabstände zwischen riskanten und sicheren Anleihen. Null ist der Durchschnitt seit 1993, eins eine Standardabweichung darüber. Steigende Abstände zeigen, dass Geldgeber mehr für Risiko verlangen. Das ging Kurseinbrüchen oft voraus oder begleitete sie. Schwäche: Der Index steigt mit dem Stress und warnt selten lange vorher."
        "hochzins" -> "Zinsaufschlag, den US-Unternehmen mit schwacher Bonität (unter BBB) gegenüber Staatsanleihen zahlen. Steigt er schnell, ziehen sich Geldgeber aus riskanten Krediten zurück. Das ging den Abschwüngen 2000, 2007 und 2020 voraus oder begleitete sie. Ein sehr niedriger Wert zeigt Sorglosigkeit. Rot ab 6 Punkten oder 2 Punkte über dem Tief der letzten zwölf Monate, Gelb ab 4,5 oder 1 Punkt darüber. FRED zeigt nur die letzten drei Jahre."
        "trend_sp500", "trend_dax" -> "Abstand des Index zu seinem Durchschnitt der letzten 200 Handelstage. Große Bärenmärkte begannen fast alle mit einem Fall unter diese Linie. Schwäche: In Seitwärtsphasen gibt es viele Fehlsignale."
        else -> ""
    }
    override val b13107Titel = "Offenlegungen aus den USA"
    override val b13107Text = "Hier zeigt die App Finanzberichte von US-Abgeordneten, Senatoren und Regierungsmitgliedern. Sie lädt dafür Berichte des Repräsentantenhauses, des Senats, des Office of Government Ethics und des Weißen Hauses. Nach 5 U.S.C. § 13107 dürfen diese Berichte nicht für kommerzielle Zwecke, zur Bewertung der Kreditwürdigkeit einer Person oder zum Einwerben von Geld genutzt werden.\n\nMit „Bestätigen“ erklärst du, dass du sie nur privat nutzt. Die App fragt danach nicht mehr."
    override val b13107Ok = "Bestätigen"
    override val b13107Zurueck = "Zurück"
    override fun blsHinweis(datum: String) = "Daten des BLS abgerufen am $datum. BLS.gov cannot vouch for the data or analyses derived from these data after the data have been retrieved from BLS.gov."
    override fun fwQuelle(schluessel: String) = when (schluessel) {
        "zinskurve" -> "Quelle: US-Finanzministerium, Daily Treasury Par Yield Curve Rates"
        "sahm" -> "Quelle: U.S. Bureau of Labor Statistics, Arbeitslosenquote (LNS14000000)"
        "vix", "vix_struktur" -> "Quelle: Cboe Indizes VIX und VIX3M über Yahoo Finance"
        "trend_sp500" -> "Quelle: S&P 500 über Yahoo Finance"
        "stress" -> "Quelle: Federal Reserve Bank of St. Louis, St. Louis Fed Financial Stress Index (STLFSI4), über FRED"
        "hochzins" -> "Quelle: ICE Data Indices, LLC, ICE BofA US High Yield Index Option-Adjusted Spread (BAMLH0A0HYM2), abgerufen über FRED, Federal Reserve Bank of St. Louis"
        "trend_dax" -> "Quelle: DAX über Yahoo Finance"
        else -> ""
    }
    override val fwPunkte = "Pkt."
    override val fwHinweis = "Keine dieser Messgrößen sagt einen Crash zuverlässig voraus. Ausgewählt sind die, die sich über Jahrzehnte am besten bewährt haben.\n\n" +
        "• Zinskurve und Sahm-Regel warnen vor Rezessionen, die oft mit fallenden Kursen einhergehen\n" +
        "• VIX, VIX-Terminstruktur und 200-Tage-Linie zeigen, ob der Markt schon unter Stress steht\n\n" +
        "Mehrere Signale gleichzeitig auf Rot sind ernster zu nehmen als eines allein."
    override fun redditMeldungTitel(name: String) = "$name wird auf Reddit viel besprochen"
    override fun redditMeldungText(jetzt: Int, vorher: Int) = "$jetzt Erwähnungen in deutschen Börsen-Subreddits in 24 Stunden, davor $vorher."
    override val trRedditDeHinweis = "Deutsche Subreddits: r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien und r/Finanzen über deinen eigenen Reddit-Zugang (Profil › Zugänge). Die App liest die neuesten 100 Beiträge und zählt, welche Werte aus dem Katalog, deinem Depot und deiner Watchlist darin genannt werden. Je Beitrag zählt ein Wert einmal. Prozent gegenüber den 24 Stunden davor nur, wenn die Beiträge so weit zurückreichen."
    override val trRedditBeitraege = "Reddit-Beiträge"
    override fun trRedditAnzahl(n: Int) = if (n == 1) "1 Beitrag im letzten Monat" else "$n Beiträge im letzten Monat"
    override val trRedditKeineBeitraege = "Im letzten Monat keine Beiträge in r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien, r/Finanzen, r/wallstreetbets, r/stocks und r/investing."
    override fun trRedditStimmung(steigend: Int, fallend: Int, offen: Int) = "Stimmung nach einfachen Wortregeln: $steigend eher steigend, $fallend eher fallend, $offen ohne klare Richtung."
    override fun trRedditPunkte(punkte: Int, kommentare: Int) = "$punkte Punkte, $kommentare Kommentare"
    override val trRedditKeine = "Reddit-Daten gerade nicht erreichbar. Später noch einmal versuchen."
    override val trLaedt = "Lädt"
    override val trMehr = "Mehr anzeigen"
    override fun trRang(rang: Int, pfeil: String, ticker: String) = "Platz $rang$pfeil · $ticker"
    override fun trErwaehnungen(n: Int) = if (n == 1) "1 Erwähnung" else "${"%,d".format(n).replace(',', '.')} Erwähnungen"
    override val trYahoo = "Bei Yahoo Finance gerade gefragt"
    override val trYahooHinweis = "Die am häufigsten aufgerufenen Werte bei Yahoo Finance in deiner Region und in den USA, dazu die meistgehandelten US-Aktien."
    override val trWiki = "Interesse auf Wikipedia"
    override fun trWikiProTag(n: String) = "Ø $n Aufrufe am Tag"
    override val trWikiVergleich = "letzte 7 Tage gegenüber den 4 Wochen davor"
    override fun trRedditZeit(tage: Int) = when (tage) { 1 -> "24 Std."; 7 -> "Woche"; 30 -> "Monat"; else -> "Jahr" }
    override fun trSpitze(datum: String) = "am meisten am $datum"
    override fun trSpitzeWoche(datum: String) = "am meisten in der Woche ab $datum"
    override fun trSpitzeTag(datum: String, n: String) = "Am meisten besprochen am $datum: $n Erwähnungen"
    override fun trVerlaufSeit(datum: String?) = if (datum == null)
        "Noch kein Verlauf: ApeWisdom nennt nur die letzten 24 Stunden, deshalb schreibt die App die Erwähnungen ab jetzt jeden Tag mit, auch im Hintergrund. Woche, Monat und Jahr füllen sich mit der Zeit."
    else "Verlauf seit $datum: ApeWisdom nennt nur die letzten 24 Stunden, deshalb schreibt die App die Erwähnungen jeden Tag mit, auch im Hintergrund. Woche, Monat und Jahr füllen sich mit der Zeit."
    override val trErwaehnungenKurz = "Erwähnungen"
    override fun trWertHeute(rang: Int, pfeil: String, veraenderung: String?) =
        "Heute Platz $rang$pfeil" + (veraenderung?.let { " · $it gegenüber den 24 Stunden davor" } ?: " · neu dabei")
    override val trWertHeuteNicht = "Heute nicht unter den 100 meistbesprochenen"
    override fun trWertSummen(woche: String, monat: String, jahr: String) = "Erwähnungen: Woche $woche · Monat $monat · Jahr $jahr"
    override val trWertYahooText = "Unter den meistaufgerufenen Werten bei Yahoo Finance in deiner Region oder in den USA."
    override val orderGeaendert = "Order geändert"
    override val orderAendern = "Ändern"
    override val orderAendernSpeichern = "Änderung speichern"
    override fun trImportTitel(quelle: String) = "$quelle importieren?"
    override val trImportLaedt = "Buchungen werden gelesen und den Kursen zugeordnet"
    override fun trImportZeitraum(von: String, bis: String, zeilen: Int) = "Export vom $von bis $bis, $zeilen Zeilen"
    override fun trImportOhneKurs(liste: String) = "Ohne Kurs, nicht im Depot (Wert fehlt deshalb): $liste"
    override fun trImportOhneKauf(liste: String) = "Verkauft, ohne dass der Kauf in der Datei steht (der Export beginnt wohl später). Als zum Verkaufskurs gekauft gerechnet: $liste"
    override val trImportHinweis = "Echte Derivate von Trade Republic (Knock-outs, Optionsscheine, Faktor-Zertifikate) stehen mit Käufen, Verkäufen und Rückzahlungen in den Umsätzen und im Depotverlauf, Kurse gibt es für sie nicht. Den Export erzeugst du in der Trade-Republic-App unter Profil > Abrechnungen > Transaktionsexport."
    override val brokerImportHinweis = "Übernommene Bestände zählen mit ihrem Durchschnittskurs als ein Kaufposten, spätere Käufe in der App werden nach FIFO verrechnet. Werte ohne Yahoo-Kurs stehen mit ihren Buchungen in den Umsätzen und im Depotverlauf, aber nicht im Depot."
    override fun trImportiert(quelle: String, n: Int) = "Export von $quelle übernommen: $n Buchungen."
    override val importWahlSicherung = "Sicherung aus dieser App"
    override val importWahlSicherungText = "JSON-Datei, die du unter „Depot exportieren“ gespeichert hast"
    override val importWahlBroker = "Export vom Broker"
    override fun importWahlBrokerText(namen: String) = "CSV-Datei mit deinen Buchungen von $namen"
    override val importWahlAnleitung = "So bekommst du den Export:\n• Trade Republic: in der App unter Profil > Abrechnungen > Transaktionsexport\n• Scalable Capital: im Browser unter Broker > Transaktionen > Exportieren (mit PRIME oder PRIME+)\n• Trading 212: Menü > Verlauf > Exportieren mit Orders, Dividenden, Transaktionen und Zinsen, je Datei höchstens 12 Monate (weitere Dateien mit Zusammenführen dazunehmen)\n• DEGIRO: im Browser unter Posteingang > Transaktionen und Posteingang > Kontoauszug, jeweils als CSV, erst die Transaktionen einlesen, dann den Kontoauszug mit Zusammenführen dazunehmen\n• Interactive Brokers: eine Flex Query mit den Abschnitten Trades und Cash Transactions als CSV, unter Performance & Berichte > Flex Queries\n• Revolut: Invest > Mehr > Dokumente > Kontoauszug im Format Excel\n• ING: Ordermanager > Ausgeführte Orders > Exportieren\n• flatex: Depotumsätze als CSV\n• Bitpanda: Verlauf > Exportieren als CSV, gelesen werden Kryptowerte sowie Ein- und Auszahlungen in Euro\n\nBei ING und flatex enthält die Datei nur Orders ohne Gebühren, das nötige Guthaben setzt die App als Einzahlung an. Die Datei wird nur auf diesem Gerät gelesen."
    override fun importUnbekannt(namen: String) = "Die Datei ist weder eine Sicherung dieser App noch ein bekannter Export. Gelesen werden Exporte von $namen."

    // Prediction Markets
    override val pmTitle = "Prognosemärkte"
    override val pmMyBets = "Meine Wetten"
    override val pmSearchHint = "Märkte durchsuchen"
    override val pmLoading = "Märkte werden geladen"
    override val pmUnreachable = "Die Märkte sind gerade nicht erreichbar."
    override val pmNoResults = "Keine Märkte gefunden."
    override val pmMore = "Mehr laden"
    override fun pmDataNote(quelle: String) = "Kurse von $quelle, möglicherweise verzögert. Gewettet wird nur mit Übungsgeld."
    override fun pmVolume(v: String) = "Umsatz $v"
    override fun pmEnds(d: String) = "endet $d"
    override val pmIn24h = "in 24 Std."
    override val pmYes = "Ja"
    override val pmBet = "Wetten"
    override val pmOutcomes = "Ausgänge"
    override val pmPlaceBet = "Wette platzieren"
    override val pmAmount = "Einsatz"
    override fun pmSideAt(seite: String, kurs: String) = "„$seite“ zu $kurs je Anteil"
    override fun pmChanceOf(seite: String) = "Chance für „$seite“"
    override val pmSharesLabel = "Anteile"
    override val pmPayoutIf = "Auszahlung bei Eintreten"
    override val pmProfitIf = "Gewinn bei Eintreten"
    override val pmPracticeNote = "Übungsgeld: Ein Anteil zahlt bei Eintreten 1 €, sonst nichts. Der Kurs entspricht der Wahrscheinlichkeit, die der Markt nennt. Keine Gebühren, keine Steuer."
    override fun pmBought(betrag: String, seite: String) = "$betrag auf „$seite“ gesetzt"
    override fun pmSold(betrag: String) = "Wette beendet, $betrag gutgeschrieben"
    override fun pmFehler(code: String) = when (code) {
        "BETRAG" -> "Der Mindesteinsatz ist 1 €."
        "KURS" -> "Für diesen Markt gibt es gerade keinen gültigen Kurs."
        "GUTHABEN" -> "Das Guthaben reicht nicht."
        "GESCHLOSSEN" -> "Die Wette ist schon abgeschlossen."
        "NETZ" -> "Der Markt ist gerade nicht erreichbar. Es wurde nichts verkauft."
        else -> "Die Wette ließ sich nicht ausführen."
    }
    override val pmSellTitle = "Wette verkaufen"
    override fun pmSellText(erloes: String, geldkurs: String, mitte: String) =
        "Alle Anteile gehen zum Geldkurs von $geldkurs weg. Das bringt jetzt $erloes. Zum angezeigten Kurs wären die Anteile $mitte wert."
    override val pmSellLaedt = "Aktueller Kurs wird geladen"
    override fun pmSellDuenn(beiEintreten: String) =
        "Käufer bieten gerade deutlich weniger als den angezeigten Kurs. Wer die Wette behält, bekommt bei Eintreten $beiEintreten, sonst nichts."
    override val pmClosed = "Abgeschlossen"
    override val pmOpenBets = "Offene Wetten"
    override val pmValue = "Aktueller Wert"
    override val pmResultOpen = "Ergebnis offener Wetten"
    override val pmResultClosed = "Ergebnis abgeschlossener Wetten"
    override val pmResultTotal = "Gesamtergebnis"
    override fun pmClosedCount(n: Int) = if (n == 1) "1 Wette verkauft oder aufgelöst" else "$n Wetten verkauft oder aufgelöst"
    override fun pmStatus(status: String) = when (status) {
        "GEWONNEN" -> "Gewonnen"; "VERLOREN" -> "Verloren"; "VERKAUFT" -> "Verkauft"; "AUFGELOEST" -> "Aufgelöst"; else -> "Offen"
    }
    override fun pmSharesAt(n: String, einstand: String, kurs: String) = "$n Anteile, gekauft zu $einstand, jetzt $kurs"
    override val pmNoBets = "Noch keine Wetten. Wähle einen Markt und setze Übungsgeld auf einen Ausgang."
    override val pmYourBets = "Deine Wetten"
    override val pmRules = "Regeln und Beschreibung"
    override fun pmOpenAt(quelle: String) = "Auf $quelle ansehen"
    override val pmNoHistory = "Für diesen Zeitraum gibt es keinen Verlauf."
    override fun pmRange(b: de.troetenrepublik.app.market.Prognose.Bereich) = when (b) {
        de.troetenrepublik.app.market.Prognose.Bereich.TAG -> "1T"; de.troetenrepublik.app.market.Prognose.Bereich.WOCHE -> "1W"
        de.troetenrepublik.app.market.Prognose.Bereich.MONAT -> "1M"; de.troetenrepublik.app.market.Prognose.Bereich.MAX -> "Max"
    }
    override fun pmResolvedCount(n: Int) = if (n == 1) "Ein weiterer Ausgang ist bereits entschieden." else "$n weitere Ausgänge sind bereits entschieden."
    override fun pmNotifTitle(tag: String) = when (tag) { "BET_WIN" -> "Wette gewonnen"; "BET_LOSS" -> "Wette verloren"; else -> "Wette aufgelöst" }
    override fun pmNotifText(titel: String, betrag: String) = "$titel · Auszahlung $betrag"

    override val pvTitle = "Depotverlauf"
    override fun pvRange(jahre: Int) = when {
        jahre == 0 -> "Max"
        jahre < 365 -> "${Math.round(jahre / 30.4)} M"
        else -> "${Math.round(jahre / 365.25)} J"
    }
    override val pvCompare = "Vergleichen"
    override val pvCompareTitle = "Vergleichen mit"
    override val pvGroupIndex = "Indizes und Welt-ETFs"
    override val pvGroupMembers = "Abgeordnete, denen du folgst"
    override val pvLoading = "Verlauf wird berechnet"
    override val pvNone = "Für diesen Zeitraum lässt sich kein Verlauf berechnen."
    override fun pvCurrencyNote(waehrung: String) = "In $waehrung, mit dem Wechselkurs des jeweiligen Tages umgerechnet, ohne Kosten und Steuern."
    override val pvMoreStats = "Weitere Kennzahlen"
    override val pvNoInvestorHistory = "Einen Verlauf zeigt die App hier nicht: Die 13F-Meldungen erfassen in manchen Quartalen weniger als die Hälfte des gemeldeten Werts mit Kursen."
    override val pvLessStats = "Weniger Kennzahlen"
    override val ptClosedSales = "Abgeschlossene Verkäufe"
    override fun ptClosedSalesLine(anzahl: Int, gewinne: Int, quote: String, faktor: String?, mittelGewinn: String?, mittelVerlust: String?, erwartung: String) =
        listOfNotNull(
            "Trefferquote $quote ($gewinne von $anzahl mit Gewinn)", faktor?.let { "Gewinnfaktor $it" },
            mittelGewinn?.let { "Ø Gewinn $it" }, mittelVerlust?.let { "Ø Verlust $it" }, "Ø Ergebnis je Verkauf $erwartung",
        ).joinToString(" · ") + ". Der Gewinnfaktor ist die Summe der Gewinne geteilt durch die Summe der Verluste. Ergebnisse vor Steuern."
    override fun pvNoteInvestor(quartale: Int, abdeckung: Double) =
        "Aus $quartale Quartalsmeldungen (Form 13F): Bestand zum Quartalsende, bis zum nächsten Quartal gehalten. Käufe und Verkäufe innerhalb eines Quartals sind nicht sichtbar. Die Marken zeigen am Stichtag, was gegenüber dem Vorquartal gekauft und verkauft wurde. Nach der letzten Meldung mit den zehn größten Positionen fortgeschrieben." +
            (if (abdeckung < 0.9) " Im Mittel erfasst der Verlauf nur ${Math.round(abdeckung * 100)} Prozent des gemeldeten Werts, weil Optionen, Anleihen und Werte ohne Kurs fehlen. Er kann deshalb stark vom tatsächlichen Ergebnis abweichen." else "")
    override fun pvNoteCongress(geschaefte: Int, ohneKurs: Int, ohneBestand: Int) =
        "Aus $geschaefte gemeldeten Geschäften: Beträge als Mitte der gemeldeten Spanne, Optionen mit Basispreis und Verfall nach Black-Scholes, sonst als Basiswert, Kurse ohne Dividenden. Bestände von vor dem Zeitraum sind nicht bekannt." +
            (if (ohneKurs > 0) " $ohneKurs Geschäfte ohne Kurse bleiben außen vor." else "") +
            (if (ohneBestand > 0) " $ohneBestand Verkäufe betreffen Bestände von vor dem Zeitraum und bleiben außen vor." else "")
    override fun pvNotePresident(geschaefte: String, jahr: String, einzel: String, eingerechnet: String, werte: String, anleihen: String, anteil: String,
        ohneKuerzel: String?, ohneKurs: String?, ohneBestand: String?, stichtag: String) =
        "Aus $geschaefte gemeldeten Geschäften ($jahr aus der Jahresmeldung 2025, $einzel aus den Einzelmeldungen). Im Verlauf stehen $eingerechnet Geschäfte in $werte Aktien und ETFs mit Kurs. Beträge als Mitte der gemeldeten Spanne, Optionen als Basiswert gezählt, Kurse ohne Dividenden. $anleihen Geschäfte mit Anleihen, Vorzugsaktien und Einlagen haben keinen Tageskurs und stehen außerhalb. Am Stichtag machten sie etwa $anteil des Depots aus." +
            (ohneKuerzel?.let { " $it Geschäfte ließen sich keinem Börsenkürzel zuordnen." } ?: "") +
            (ohneKurs?.let { " $it betreffen Werte ohne Kurse." } ?: "") +
            (ohneBestand?.let { " $it Verkäufe ohne vorherigen Kauf bleiben außen vor." } ?: "") +
            " Berechnet bis $stichtag, danach mit dem Bestand an diesem Tag fortgeschrieben."
    override val truthTitle = "Truth Social"
    override val truthSubtitle = "Seine Beiträge aus dem Archiv „Trump's Truth“"
    override val truthSource = "Quelle: Trump's Truth (trumpstruth.org), ein Archiv der Organisation Defending Democracy Together, nicht verbunden mit Truth Social oder Donald Trump. Ein Tipp öffnet den Beitrag im Archiv."
    override val truthRemoved = "Gelöscht"
    override val truthRepost = "Weitergeteilt"
    override val truthQuote = "Zitat"
    override fun truthImages(n: Int) = if (n == 1) "1 Bild" else "$n Bilder"
    override val truthVideo = "Video"
    override val truthNone = "Das Archiv ist gerade nicht erreichbar."
    override val truthMore = "Ältere Beiträge"
    override val pvNoteStrategy = "Musterdepot aus Euro-ETFs mit täglich gleichen Gewichten."
    override val pvNoteCompare = "Strategien als Musterdepots aus Euro-ETFs, Investoren aus ihren 13F-Meldungen, Indizes über ETFs an der Xetra."
    override val pvCongressNoBuys = "Im Zeitraum sind keine Käufe mit Kursen gemeldet. Ohne Käufe lässt sich kein Depot bilden."
    override fun pvMarkBuys(datum: String, werte: String) = "Kauf $datum: $werte"
    override fun pvMarkSells(datum: String, werte: String) = "Verkauf $datum: $werte"
    override fun pvMarkQBuys(quartal: String, werte: String) = "$quartal, Käufe: $werte"
    override fun pvMarkQSells(quartal: String, werte: String) = "$quartal, Verkäufe: $werte"
    override val pvMoveOut = "ganz verkauft"
    override fun pvQuarter(q: Int, jahr: Int) = "Q$q $jahr"
    override val pvMoves = "Käufe und Verkäufe je Quartal"
    override fun pvQuarterHead(quartal: String, gemeldet: String) = "$quartal · gemeldet am $gemeldet"
    override fun pvAllQuarters(n: Int) = "Alle $n Quartale"
    override val pvFewerQuarters = "Weniger Quartale"
    override val pvCompareStrategies = "Strategien vergleichen"
    override val pvCompareInvestors = "Investoren vergleichen"
    override val pvCompareScreen = "Depots vergleichen"
    override fun pvSince(monat: String) = "seit $monat"
    override val pmHistory = "Verlauf"
    override val pmHistoryLoading = "Kursverläufe der Wetten werden geladen"
    override val pmHistoryNone = "Für diesen Zeitraum gibt es noch keinen Verlauf."
    override val pmHistoryNote = "Gewinn und Verlust aller Wetten: eingesetztes und ausgezahltes Übungsgeld plus der Wert der gehaltenen Anteile zum damaligen Kurs."
    override val pmHistoryHint = "Über den Chart ziehen: Ergebnis des Tages und Wetten in der Nähe."
    override fun pmHistoryRange(tage: Int) = when (tage) { 7 -> "1W"; 30 -> "1M"; else -> "Max" }
    override fun pmHistoryMark(tag: String, datum: String, betrag: String, seite: String, titel: String) = when (tag) {
        "BET_BUY" -> "$datum: $betrag auf „$seite“ · $titel"
        "BET_SELL" -> "$datum: verkauft für $betrag · $titel"
        "BET_WIN" -> "$datum: gewonnen, $betrag · $titel"
        "BET_LOSS" -> "$datum: verloren · $titel"
        else -> "$datum: aufgelöst, $betrag · $titel"
    }
    override val languageSystem = "System"
    override val zgTitle = "Zugänge"
    override val importFileSection = "Ganzes Depot"
    override val pvDepotSub = "Zeitgewichtet über die ganze Zeit"
    override val newsImportantTitle = "Wichtige Meldungen"
    override val newsWichtigEnglisch = "Wichtigste nur auf Englisch"
    override val newsWichtigEnglischHinweis = "Schlagzeilen in anderen Sprachen stehen weiter unter Alle und bei ihrer Quelle"
    override val newsImportantExplain = "Orange markiert sind Schlagzeilen zu großen Ereignissen wie Zinsentscheiden, Kriegen, Zöllen oder starken Marktbewegungen, besonders bei mehreren Medien oder wenn ein Wert aus Depot oder Watchlist genannt wird. Meinungen, Vorschauen und Kauftipps werden nie markiert."
    override val ptIndexTitle = "Dasselbe Geld im Index"
    override fun zgTerms(dienst: String) = when (dienst) {
        "guardian" -> "Kostenloser Entwicklerschlüssel für nicht kommerzielle Nutzung: höchstens 500 Abrufe am Tag und einer je Sekunde. Die App nutzt höchstens 450, fragt die zwei Rubriken alle 15 Minuten ab und behält Inhalte des Guardian höchstens 24 Stunden."
        "finnhub" -> "Kostenloser Schlüssel nur für den persönlichen Gebrauch: 60 Abrufe je Minute, die Daten dürfen nicht weitergegeben werden. Die App fragt die vier Kategorien alle 10 Minuten ab."
        "fec" -> "Schlüssel von api.data.gov, kostenlos mit einer E-Mail-Adresse: 1.000 Abrufe je Stunde. Die App nutzt höchstens 900 je Stunde, mit dem Testschlüssel höchstens 25 je Stunde und 45 am Tag. Abgefragt werden nur die Summen der Komitees und ihre Empfänger, keine Namen einzelner Spender."
        "uspto" -> "Schlüssel des Open Data Portal des US-Patentamts, kostenlos mit einem USPTO-Konto und einer Identitätsprüfung über ID.me. Ungenutzte Schlüssel verfallen nach 90 Tagen. Die App fragt höchstens 500 Mal am Tag, einzeln nacheinander."
        "reddit" -> "Reddit-API für nicht kommerzielle Nutzung: 100 Abrufe je Minute. Seit Ende 2025 gibt Reddit neue Zugänge nur nach einem Antrag frei. Die Selbstanmeldung ist geschlossen. Mit einem freigegebenen Zugang legst du unter reddit.com/prefs/apps eine App an und trägst ihre Client-ID ein, bei einer App vom Typ „script“ oder „web app“ auch das Secret. Die App meldet sich nur als App an, ohne dein Reddit-Konto, und liest nur. Sie nutzt höchstens 30 Abrufe je Minute und 2.000 am Tag und zeigt keine Beiträge ab 18."
        "alphavantage" -> "Kostenloser Schlüssel: 25 Abrufe am Tag. Die App fragt höchstens einmal je Stunde und höchstens 22-mal am Tag. Alpha Vantage kennzeichnet seine Nachrichten als Premium. Lehnt der Dienst einen kostenlosen Schlüssel ab, zeigt die App seine Meldung."
        "eodhd" -> "EODHD-Schlüssel nur für den persönlichen Gebrauch: Die Daten dürfen weder weitergegeben noch anderen gezeigt werden, der Zugang darf nicht geteilt werden. 20 Calls am Tag, Rücksetzung um Mitternacht GMT. Ein Abruf kostet 5 Calls, also rund 4 Abrufe am Tag. Die App fragt nur beim Aufklappen auf der Seite eines Werts und merkt sich das Ergebnis eine Stunde."
        else -> "DeepL API Free: 500.000 Zeichen im Monat. Die App nutzt höchstens 450.000 in 31 Tagen und merkt sich jede Übersetzung, damit derselbe Text nur einmal zählt. Übertragen werden nur die Marktinhalte, keine Daten von dir."
    }
    override val zgKey = "Schlüssel"
    override val zgShow = "Zeigen"
    override val zgHide = "Verbergen"
    override fun zgUsage(dienst: String, verbraucht: String, erlaubt: String) = when (dienst) {
        "deepl" -> "Zeichen in den letzten 31 Tagen: $verbraucht von $erlaubt"
        "eodhd" -> "Calls heute (UTC): $verbraucht von $erlaubt"
        else -> "Abrufe in den letzten 24 Stunden: $verbraucht von $erlaubt"
    }
    override val zgLimit = "Das Limit ist erreicht. Die App fragt wieder, sobald das Kontingent es erlaubt."
    override val zgKeyRejected = "Der Anbieter hat den Schlüssel abgelehnt. Bitte den Schlüssel prüfen."
    override fun zgProviderSays(meldung: String) = "Meldung des Anbieters: $meldung"
    override fun zgGetKey(name: String) = "Schlüssel bei $name holen"
    override fun zgProfileSubtitle(n: Int) = if (n == 0) "The Guardian, Finnhub, Alpha Vantage, DeepL und 9 weitere" else "$n eingetragen"
    override val zgRedditId = "Client-ID"
    override val zgRedditSecret = "Secret, falls vorhanden"
    override val zgRedditHolen = "Zugang bei Reddit beantragen und App anlegen"
    override val zgFinraSecret = "Client-Secret"
    override val zgFinraHolen = "Konto bei FINRA anlegen und Zugang erstellen"
    override fun newsKeyMissing(name: String) = "Für $name braucht die App einen eigenen, kostenlosen Schlüssel."
    override val newsKeyEnter = "Schlüssel eintragen"
    override val newsGuardianNote = "Inhalte des Guardian bleiben höchstens 24 Stunden in der App, gemerkte Artikel ebenso."
    override val newsGuardianNoText = "Den Text zeigt die App über die Schnittstelle des Guardian, wenn ein Schlüssel eingetragen ist. Sonst steht der Artikel im Browser."
    override val pmTranslateHint = "Titel und Ausgänge kommen von Polymarket und Kalshi nur auf Englisch. Mit einem eigenen DeepL-Schlüssel übersetzt die App sie ins Deutsche."
    override val languageSystemHint = "Folgt der Sprache des Geräts: Deutsch, wenn es auf Deutsch eingestellt ist, sonst Englisch."
    override val pvDragHint = "Über den Chart ziehen: Werte des Tages und Käufe (▲) und Verkäufe (▼) in der Nähe."

    override val dvSubtitle = "Verlauf, Vergleich mit dem Markt und woher Gewinne und Verluste kommen"
    override val dvNone = "Noch keine Käufe. Der Verlauf beginnt mit dem ersten Kauf."
    override val dvMoves = "Größte Bewegungen"
    override fun dvRaster(name: String) = when (name) { "WOCHE" -> "Woche"; "MONAT" -> "Monat"; else -> "Tag" }
    override val dvRises = "Anstiege"
    override val dvDrops = "Rückgänge"
    override val dvNoMoves = "Keine im gewählten Zeitraum."
    override fun dvWeek(datum: String) = "Woche ab $datum"
    override fun dvBy(werte: String) = "Durch $werte"
    override val dvContrib = "Gewinn und Verlust je Wert"
    override val dvHeld = "im Depot"
    override val dvEstimated = "verkauft, Ergebnis am Verkaufstag"
    override val dvTrades = "Käufe und Verkäufe"
    override fun dvQtyAt(qty: String, price: String) = "$qty Stk. zu $price"
    override fun dvAll(n: Int) = "Alle $n anzeigen"
    override val dvFewer = "Weniger anzeigen"
    override fun dvTagKauf(werte: String) = "Kauf: $werte"
    override fun dvTagVerkauf(werte: String) = "Verkauf: $werte"
    override fun dvUndWeitere(n: Int) = "und $n weitere"
    override val dvDragHint = "Über den Chart ziehen: Veränderung des Tages, die Werte dahinter und die Käufe (▲) und Verkäufe (▼) an diesem Tag."
    override val dvNote = "Ergebnis eines Werts an einem Tag: seine Wertänderung ohne den Betrag von Kauf und Verkauf. Gebühren, Steuern, Zinsen und Dividenden zählen hier nicht."
    override val indButton = "Indikatoren"
    override fun indName(art: String) = when (art) {
        "sma50" -> "Ø 50 Tage"
        "sma200" -> "Ø 200 Tage"
        "bb" -> "Bollinger-Bänder"
        "rsi" -> "RSI"
        "stoch" -> "Stochastik"
        "atr" -> "ATR"
        "obv" -> "OBV"
        "ichimoku" -> "Ichimoku"
        "sar" -> "Parabolic SAR"
        "kerzen" -> "Kerzenmuster"
        else -> "MACD"
    }
    override fun indLage(art: String, lage: String) = when ("$art/$lage") {
        "kerzen/hammer" -> "Hammer: nach Rückgang, Erholung möglich"
        "kerzen/inv_hammer" -> "Umgekehrter Hammer: nach Rückgang, Erholung möglich"
        "kerzen/haengender" -> "Hängender Mann: nach Anstieg, Rücksetzer möglich"
        "kerzen/sternschnuppe" -> "Sternschnuppe: nach Anstieg, Rücksetzer möglich"
        "kerzen/verschlingen_auf" -> "Bullisches Verschlingen: nach Rückgang, Erholung möglich"
        "kerzen/verschlingen_ab" -> "Bärisches Verschlingen: nach Anstieg, Rücksetzer möglich"
        "kerzen/durchstossen" -> "Durchstoßendes Muster: nach Rückgang, Erholung möglich"
        "kerzen/wolke" -> "Dunkle Wolke: nach Anstieg, Rücksetzer möglich"
        "kerzen/morgenstern" -> "Morgenstern: nach Rückgang, Erholung möglich"
        "kerzen/abendstern" -> "Abendstern: nach Anstieg, Rücksetzer möglich"
        "kerzen/soldaten" -> "Drei weiße Soldaten: nach Rückgang, Erholung möglich"
        "kerzen/kraehen" -> "Drei schwarze Krähen: nach Anstieg, Rücksetzer möglich"
        "kerzen/doji" -> "Doji: Unentschlossenheit, keine Richtung"
        "kerzen/keins" -> "Kein Muster in den letzten 5 Handelstagen"
        "sma50/ueber" -> "Kurs über dem Schnitt: kurzfristig aufwärts"
        "sma50/unter" -> "Kurs unter dem Schnitt: kurzfristig abwärts"
        "sma200/ueber" -> "Kurs über dem Schnitt: langfristig aufwärts"
        "sma200/unter" -> "Kurs unter dem Schnitt: langfristig abwärts"
        "bb/oben" -> "über dem oberen Band: ungewöhnlich stark gestiegen, Rücksetzer möglich"
        "bb/nahe_oben" -> "nahe am oberen Band: zuletzt gestiegen, aufwärts"
        "bb/unten" -> "unter dem unteren Band: ungewöhnlich stark gefallen, Erholung möglich"
        "bb/nahe_unten" -> "nahe am unteren Band: zuletzt gefallen, abwärts"
        "bb/mitte" -> "in der Mitte der üblichen Spanne: keine klare Richtung"
        "rsi/ueberkauft", "stoch/ueberkauft" -> "überkauft: stark gestiegen, Rücksetzer möglich"
        "rsi/ueberverkauft", "stoch/ueberverkauft" -> "überverkauft: stark gefallen, Erholung möglich"
        "rsi/neutral", "stoch/neutral" -> "weder stark gestiegen noch stark gefallen"
        "atr/unruhig" -> "unruhiger als sonst, sagt nichts über die Richtung"
        "atr/ruhig" -> "ruhiger als sonst, sagt nichts über die Richtung"
        "atr/normal" -> "übliche Schwankung, sagt nichts über die Richtung"
        "obv/bestaetigt_auf" -> "Aufwärtstrend, vom Umsatz bestätigt"
        "obv/bestaetigt_ab" -> "Abwärtstrend, vom Umsatz bestätigt"
        "obv/schwach_auf" -> "Aufwärtstrend, aber ohne Rückhalt im Umsatz"
        "obv/schwach_ab" -> "Abwärtstrend, aber ohne Rückhalt im Umsatz"
        "obv/seitwaerts_auf" -> "kein Trend, mehr Umsatz an Plustagen"
        "obv/seitwaerts_ab" -> "kein Trend, mehr Umsatz an Minustagen"
        "ichimoku/ueber" -> "über der Wolke: Aufwärtstrend"
        "ichimoku/unter" -> "unter der Wolke: Abwärtstrend"
        "ichimoku/in" -> "in der Wolke: seitwärts, kein klarer Trend"
        "sar/aufwaerts" -> "Stopp unter dem Kurs: Aufwärtstrend"
        "sar/abwaerts" -> "Stopp über dem Kurs: Abwärtstrend"
        "macd/steigt" -> "Schwung nach oben nimmt zu"
        else -> "Schwung lässt nach, eher abwärts"
    }
    override fun indTendenz(tendenz: String) = when (tendenz) {
        "POSITIV" -> "↑ positiv"
        "NEGATIV" -> "↓ negativ"
        "GEMISCHT" -> "↕ gemischt"
        else -> "→ neutral"
    }
    override fun indUeberblick(positiv: Int, negativ: Int, neutral: Int, gemischt: Int) = "Überblick: " + listOfNotNull(
        positiv.takeIf { it > 0 }?.let { "$it positiv" },
        negativ.takeIf { it > 0 }?.let { "$it negativ" },
        gemischt.takeIf { it > 0 }?.let { "$it gemischt" },
        neutral.takeIf { it > 0 }?.let { "$it neutral" },
    ).joinToString(" · ")
    override fun indGrund(art: String, lage: String, prozent: String, zahl: String, kreuz: String?, kreuzAuf: Boolean, eng: Boolean): String = when (art) {
        "kerzen" -> if (kreuz == null) "" else "Erkannt am $kreuz" + when (zahl) { "0" -> ", dem letzten Handelstag"; "1" -> ", einen Handelstag zuvor"; else -> ", $zahl Handelstage zuvor" }
        "sma50", "sma200" -> "Kurs $prozent " + (if (lage == "ueber") "darüber" else "darunter") +
            if (art == "sma200" && kreuz != null) ". Der Ø 50 Tage kreuzte ihn am $kreuz nach " + (if (kreuzAuf) "oben (Golden Cross)" else "unten (Death Cross)") else ""
        "bb" -> listOfNotNull(
            when (lage) {
                "oben" -> "Ungewöhnlich stark gestiegen, gemessen an den letzten 20 Tagen"
                "unten" -> "Ungewöhnlich stark gefallen, gemessen an den letzten 20 Tagen"
                "nahe_oben" -> "Kurs im oberen Fünftel zwischen den Bändern, noch innerhalb"
                "nahe_unten" -> "Kurs im unteren Fünftel zwischen den Bändern, noch innerhalb"
                else -> null
            },
            if (eng) "Bänder ungewöhnlich eng: ruhige Phase, danach folgen oft größere Bewegungen" else null,
        ).joinToString(". ")
        "rsi" -> "Wert $zahl von 100. Über 70 gilt als überkauft, unter 30 als überverkauft"
        "stoch" -> "Wert $zahl von 100: So weit oben liegt der Kurs zwischen dem Tief (0) und dem Hoch (100) der letzten 14 Tage. Über 80 gilt als überkauft, unter 20 als überverkauft" +
            if (kreuz != null) ". Am $kreuz kreuzte der Wert seinen Durchschnitt der letzten 3 Tage nach " + (if (kreuzAuf) "oben" else "unten") else ""
        "atr" -> "Ein Handelstag bewegt den Kurs zurzeit im Mittel um $prozent, verglichen mit dem üblichen Wert des letzten Jahres"
        "obv" -> when (lage) {
            "bestaetigt_auf" -> "Kurs in 20 Tagen $prozent gestiegen, und an Plustagen wurde mehr gehandelt als an Minustagen. Der Anstieg ist breit getragen. Nach dieser Lesart spricht das für einen Aufwärtstrend"
            "bestaetigt_ab" -> "Kurs in 20 Tagen $prozent gefallen, und an Minustagen wurde mehr gehandelt als an Plustagen. Der Rückgang ist breit getragen. Nach dieser Lesart spricht das für einen Abwärtstrend"
            "schwach_auf" -> "Kurs in 20 Tagen $prozent gestiegen, aber an Minustagen wurde mehr gehandelt. Der Anstieg kam an ruhigen Tagen. Nach dieser Lesart ist der Aufwärtstrend fraglich"
            "schwach_ab" -> "Kurs in 20 Tagen $prozent gefallen, aber an Plustagen wurde mehr gehandelt. Der Rückgang kam an ruhigen Tagen. Nach dieser Lesart könnte er auslaufen"
            "seitwaerts_auf" -> "Kurs in 20 Tagen kaum bewegt ($prozent), an Plustagen wurde aber mehr gehandelt. Manche lesen das als Zeichen, dass ein Anstieg folgen könnte"
            else -> "Kurs in 20 Tagen kaum bewegt ($prozent), an Minustagen wurde aber mehr gehandelt. Manche lesen das als Zeichen, dass ein Rückgang folgen könnte"
        }
        "ichimoku" -> when (lage) {
            "ueber" -> "Kurs $prozent über dem oberen Rand der Wolke"
            "unter" -> "Kurs $prozent unter dem unteren Rand der Wolke"
            else -> "Kurs zwischen den Rändern der Wolke: keine klare Richtung"
        } + if (kreuz != null) ". Am $kreuz kreuzte die 9-Tage-Mitte die 26-Tage-Mitte nach " + (if (kreuzAuf) "oben" else "unten") else ""
        "sar" -> "Kurs $prozent " + (if (lage == "aufwaerts") "über dem Stopp" else "unter dem Stopp") +
            if (kreuz != null) ". Richtungswechsel am $kreuz" else ""
        else -> if (kreuz != null) "Kreuzte am $kreuz die Signallinie nach " + (if (kreuzAuf) "oben" else "unten") else ""
    }
    override fun indErklaerung(art: String) = when (art) {
        "sma50" -> "Durchschnitt der Schlusskurse der letzten 50 Handelstage. Liegt der Kurs darüber, ging es zuletzt eher aufwärts. Trends halten oft eine Weile, für einzelne Aktien ist das Signal aber schwach."
        "sma200" -> "Durchschnitt der letzten 200 Handelstage, ein Maß für den langfristigen Trend. Kreuzt der 50-Tage-Durchschnitt ihn nach oben, heißt das Golden Cross, nach unten Death Cross. Gilt als Trendzeichen, kommt aber oft spät."
        "bb" -> "Zwei Linien über und unter dem Durchschnitt der letzten 20 Tage. Ihr Abstand richtet sich danach, wie stark der Kurs in diesen 20 Tagen geschwankt hat: In unruhigen Zeiten gehen sie auseinander, in ruhigen rücken sie zusammen. Rund 9 von 10 Schlusskursen liegen zwischen ihnen. Ein Kurs außerhalb ist also ungewöhnlich weit gelaufen. Oft geht es danach wieder zur Mitte, in starken Trends läuft er aber auch länger am Band entlang.\n\nIm oberen Teil der Spanne ist der Kurs zuletzt gestiegen (positiv), im unteren gefallen (negativ), in der Mitte gibt es keine klare Richtung. Außerhalb der Bänder ist das Bild gemischt: Die Bewegung war kräftig, eine Gegenbewegung ist möglich, aber nicht sicher."
        "rsi" -> "Vergleicht die Kursgewinne mit den Kursverlusten der letzten 14 Tage. Hohe Werte heißen: zuletzt fast nur gestiegen, niedrige: fast nur gefallen. Starke Aktien bleiben oft lange „überkauft“. Überkauft und überverkauft gelten hier als gemischt: Der Kurs ist weit gelaufen, eine Gegenbewegung ist möglich, aber nicht sicher."
        "stoch" -> "Zeigt, wo der heutige Schlusskurs zwischen dem tiefsten und dem höchsten Kurs der letzten 14 Tage liegt: 0 am Tief, 100 am Hoch. Kreuzt der Wert seinen eigenen Durchschnitt der letzten 3 Tage, gilt das als Wendesignal. Reagiert schnell und gibt viele Fehlsignale."
        "atr" -> "Average True Range: wie weit sich der Kurs an einem Handelstag im Mittel bewegt, über 14 Tage und mit Kurslücken über Nacht, hier in Prozent des Kurses. Sagt nichts über die Richtung, nur wie unruhig es ist. Hilft beim Abstand eines Stop-Loss: Liegt er näher als etwa zwei ATR, kann ihn schon ein gewöhnlicher Tag auslösen."
        "obv" -> "On-Balance-Volume ist eine laufende Summe: Schließt der Kurs höher als am Vortag, kommt der ganze Tagesumsatz dazu, schließt er tiefer, wird er abgezogen. Die Höhe der Linie bedeutet nichts. Es zählt ihre Richtung im Vergleich zum Kurs. Laufen beide in dieselbe Richtung, gilt der Trend als bestätigt. Laufen sie auseinander, gilt er als wackelig. Bewegt sich der Kurs kaum, zeigt OBV nur, an welchen Tagen mehr gehandelt wurde. Vereinfachung: Zu jedem Kauf gehört ein Verkauf. „Mehr Umsatz an Plustagen“ heißt nicht, dass mehr gekauft als verkauft wurde. Bei Werten mit wenig Umsatz ungenau, für Indizes fehlt der Umsatz oft ganz."
        "ichimoku" -> "Japanisches Trendsystem aus den Mitten von Hoch und Tief der letzten 9, 26 und 52 Tage. Daraus entsteht eine „Wolke“, die um 26 Tage nach vorn versetzt ist. Im Chart sind ihre beiden Ränder und die 26-Tage-Mitte zu sehen. Über der Wolke gilt der Trend als aufwärts, darunter als abwärts, in der Wolke als unklar. Folgt dem Trend und reagiert deshalb spät. Die Wolke ist bis heute gezeichnet, ihre Vorschau auf die nächsten 26 Tage fehlt."
        "kerzen" -> "Kerzenmuster lesen Eröffnung, Hoch, Tief und Schluss einzelner Tage. Gesucht werden die bekanntesten Muster aus dem Lehrbuch: Hammer, umgekehrter Hammer, bullisches Verschlingen, durchstoßendes Muster, Morgenstern und drei weiße Soldaten nach einem Rückgang (gelten als Zeichen einer Erholung); hängender Mann, Sternschnuppe, bärisches Verschlingen, dunkle Wolke, Abendstern und drei schwarze Krähen nach einem Anstieg (gelten als Zeichen eines Rücksetzers). Als Rückgang oder Anstieg zählt der Kursverlauf der fünf Tage davor. Ein Doji, bei dem Eröffnung und Schluss fast gleich sind, gilt als Zeichen der Unentschlossenheit. Genannt wird das jüngste Muster der letzten 5 Handelstage. Ohne Eröffnungskurse (manche Indizes und Fonds) fehlt die Erkennung."
        "sar" -> "Parabolic SAR ist ein mitlaufender Stopp: im Aufwärtstrend unter dem Kurs, im Abwärtstrend darüber. Mit jedem neuen Hoch oder Tief rückt er schneller nach. Durchbricht der Kurs ihn, wechselt die Richtung und der Stopp springt auf die andere Seite. Gut in klaren Trends, in Seitwärtsphasen wechselt er ständig."
        else -> "Abstand zwischen dem 12- und dem 26-Tage-Durchschnitt, verglichen mit seinem eigenen Durchschnitt über 9 Tage (Signallinie). Liegt er darüber, nimmt der Schwung zu. Reagiert verzögert und gibt in Seitwärtsphasen viele Fehlsignale."
    }
    override val indFuss = "Die Pfeile bedeuten nach der üblichen Lesart:\n• ↑ positiv: spricht eher für steigende Kurse\n• ↓ negativ: spricht eher für fallende Kurse\n• ↕ gemischt: weit gelaufen, eine Gegenbewegung ist möglich\n\nDie Aussagekraft ist gering, Indikatoren lesen nur den bisherigen Kurs."
    override val copyPolitics = "Politik"
    override fun truthRelevant(themen: List<String>) = themen.joinToString(" · ")
    override fun truthThema(k: String) = when (k) {
        "zoelle" -> "Zölle"
        "handel" -> "Handel"
        "notenbank" -> "Notenbank"
        "boerse" -> "Börse"
        "konjunktur" -> "Konjunktur"
        "energie" -> "Energie"
        "krypto" -> "Krypto"
        "steuern" -> "Steuern"
        "dollar" -> "Dollar"
        "geopolitik" -> "Geopolitik"
        else -> k.removePrefix("firma:")
    }
    override val newsOhneLesemodus = "Für diese Seite gibt es keinen Lesemodus. „Im Browser öffnen“ führt zum ganzen Artikel."
    override val newsBrowserOnly = "MarketWatch zeigt seine Artikel am Rechner nur im Browser. „Im Browser öffnen“ führt zum ganzen Artikel."
    override val copyPositionsTitle = "Aktuelle Positionen"
    override fun copyPositionShare(anteil: String) = "$anteil des Depots"
    override val copyPositionsNote = "Geschätzt aus den gemeldeten Geschäften: Käufe zum Kurs des Tages, Beträge als Mitte der Spanne. Bestände von vor der ersten Meldung fehlen."
    override fun copyPositionsAll(n: Int) = "Alle $n Positionen"
    override val copyPositionsFewer = "Weniger Positionen"
    override val copyPartShares = "Aktien"
    override fun copyPartOption(call: Boolean, basispreis: String, verfall: String, verfallen: Boolean) =
        (if (call) "Call" else "Put") + " $basispreis \$ · " + (if (verfallen) "verfallen am $verfall" else "bis $verfall")
    override fun copyPartShare(anteil: String) = "$anteil der Position"
    override fun copyPartsToggle(offen: Boolean) = if (offen) "Aufteilung zuklappen" else "Aufteilung zeigen"
    override val copyToolsRow = "Depot-Check, Diversifikation, Crashtest, Dividenden und weitere Werkzeuge mit diesem Depot"
    override fun copyToolsFor(name: String) = "Mit dem geschätzten Depot von $name"
    override fun pvMetric(k: String) = when (k) {
        "gesamt" -> "Gesamt"
        "jahr" -> "Pro Jahr"
        "schwankung" -> "Schwankung"
        "rueckgang" -> "Größter Rückgang"
        "sharpe" -> "Sharpe-Quote"
        "sortino" -> "Sortino-Quote"
        "calmar" -> "Calmar-Quote"
        "ulcer" -> "Ulcer-Index"
        "var" -> "VaR 95 %"
        "es" -> "ES 95 %"
        "dauer" -> "Tage unter Hoch"
        "beta" -> "Beta"
        "alpha" -> "Alpha pro Jahr"
        else -> "Korrelation"
    }
    override fun pvMetricExplain(k: String) = when (k) {
        "gesamt" -> "Veränderung im gewählten Zeitraum."
        "jahr" -> "Durchschnittliche Rendite pro Jahr, erst ab etwa einem Jahr Zeitraum."
        "schwankung" -> "Wie stark die Linie hin- und herschwankt, auf ein Jahr gerechnet (Volatilität). 20 % heißt: In etwa zwei von drei Jahren liegt das Ergebnis höchstens 20 Prozentpunkte über oder unter dem Durchschnitt. Breite Aktien-ETFs liegen bei etwa 15 %, einzelne Aktien oft bei 30 % und mehr."
        "rueckgang" -> "Größter Rückgang von einem Hoch bis zum folgenden Tief. Zeigt, wie viel man im schlechtesten Moment zwischendurch verloren hätte."
        "sharpe" -> "Wie viel Rendite die Schwankung eingebracht hat: Rendite pro Jahr über dem Zins auf Bargeld, geteilt durch die Schwankung. Beispiel: 10 % Rendite bei 2 % Zins und 16 % Schwankung ergibt 0,5. Unter 0 lag die Rendite unter dem Zins auf Bargeld, um 0,5 ist für Aktienmärkte üblich, über 1 ist sehr gut. Bei gleicher Rendite ist die Linie mit der höheren Quote ruhiger gelaufen."
        "sortino" -> "Wie die Sharpe-Quote, aber nur Schwankungen nach unten zählen als Risiko, denn Sprünge nach oben stören niemanden. Liegt sie deutlich über der Sharpe-Quote, kam die Schwankung vor allem von Anstiegen."
        "calmar" -> "Rendite pro Jahr geteilt durch den größten Rückgang im gewählten Zeitraum. Beispiel: 10 % pro Jahr bei höchstens 25 % Rückgang ergibt 0,4. Höher ist besser. Die übliche Fassung rechnet über drei Jahre."
        "ulcer" -> "Misst, wie tief und wie lange die Linie im Mittel unter ihrem letzten Hoch lag, in Prozent. 0 heißt stetig ohne Rückschläge, unter 5 ruhig, über 15 lange und tiefe Verlustphasen."
        "var" -> "Ein schlechter, aber gewöhnlicher Abschnitt: An 95 von 100 Tagen (bei langen Zeiträumen Wochen) war der Verlust kleiner als dieser Wert. Bei Tageswerten fällt etwa ein Tag im Monat schlechter aus. Gezeigt ab 100 Werten."
        "es" -> "Wie schlimm die übrigen 5 von 100 Tagen oder Wochen im Mittel waren, also die wirklich schlechten. Mindestens so groß wie der VaR."
        "dauer" -> "Längste Zeit, bis ein früheres Hoch wieder erreicht war. „+“ heißt, das dauert noch an. So lange hätte man auf das alte Hoch warten müssen."
        "beta" -> "Wie stark die erste Linie mit dieser mitgeht. 1 heißt: Bewegt sich diese Linie um 1 %, bewegt sich die erste im Mittel auch um 1 %. 1,5 heißt heftiger, 0,5 ruhiger, unter 0 eher gegenläufig."
        "alpha" -> "Rendite pro Jahr der ersten Linie, die sich nicht aus dem Mitgehen (Beta) erklärt. Positiv heißt: besser, als es die Bewegung dieser Linie erwarten ließ, negativ: schlechter. Schwankt stark mit dem Zeitraum."
        else -> "Wie gleich beide Linien laufen, von 1 bis −1. Bei 1 bewegen sie sich immer gleich, bei 0 unabhängig, bei −1 entgegengesetzt. Für eine bessere Streuung im Depot hilft alles unter etwa 0,7."
    }
    override fun copyLogoCredit(domain: String) = "Logo: $domain"
    override val copyNameUnreadable = "Name nicht lesbar"
    override fun webBausteinAngebot(mb: Int) = "Für Artikel von MarketWatch lädt die App einmalig einen eingebetteten Browser (Chromium, etwa $mb MB) von Maven Central in ihren Datenordner. Danach öffnen die Artikel in einem Fenster der App."
    override val webBausteinLaden = "Browser laden"
    override fun webBausteinLaedt(prozent: String) = "Browser wird geladen: $prozent"
    override val webBausteinFehler = "Der Browser ließ sich nicht laden. Prüfe die Verbindung und versuche es noch einmal. „Im Browser öffnen“ geht immer."
    override val webBausteinImFenster = "Der Artikel ist im Fenster geöffnet. Ist die Seite geladen, zeigt „Nur Text anzeigen“ ihren Text hier."
    override val webBausteinLeer = "Auf der Seite im Fenster ist kein Text zu sehen, den die App anzeigen kann."
    override val webBausteinFenster = "Seite im Fenster öffnen"
    override val newsTrumpHinweis = "Unter „Alle“ stehen nur seine Beiträge, die für Wirtschaft, Märkte oder die Weltlage wichtig sind, etwa zu Zöllen, Notenbank, Kriegen und Angriffen. Hier stehen alle."
    override val webBausteinNurAnfang = "MarketWatch zeigt ohne Abo nur den Anfang des Artikels. Den ganzen Text gibt es mit Abo auf der Seite."
    override fun dfUniversumName(k: String) = when (k) { "ALLE" -> "Alle Aktien"; "USA" -> "USA"; "DEUTSCHLAND" -> "Deutschland"; "GROSSBRITANNIEN" -> "Großbritannien"; "SCHWEIZ" -> "Schweiz"; "JAPAN" -> "Japan"; "KANADA" -> "Kanada"; "ASIEN" -> "Asien-Pazifik"; "SCHWELLEN" -> "Schwellenländer"; else -> "Europa" }
    override val opTitel = "Optionen"
    override val opPcr = "Put/Call (Umsatz · offen)"
    override fun opBewegung(tag: String) = "Erwartete Bewegung bis $tag"
    override fun opMaxPain(tag: String) = "Max Pain $tag"
    override fun opHv(wert: String) = "Aktie $wert"
    override val opAuffaellig = "Auffällige Umsätze"
    override fun opAuffaelligZeile(art: String, strike: String, tag: String, umsatz: String, offen: String, praemie: String, seite: String) =
        "$art $strike · $tag · $umsatz Kontrakte, $offen offen · $praemie · " + when (seite) { "KAUF" -> "eher gekauft"; "VERKAUF" -> "eher verkauft"; else -> "Seite unklar" }
    override val opQuelle = "Optionskette von Yahoo, Stand letzter Handelstag, offene Kontrakte vom Vortag."
    override val opStillhalter = "Stillhalter-Rechner"
    override fun opTage(tage: Int) = if (tage == 1) "1 Tag" else "$tage Tage"
    override val opStillCalls = "Call schreiben auf eigene Aktien"
    override val opStillPuts = "Put schreiben mit bereitgehaltenem Geld"
    override val opBasis = "Basis"
    override val opPraemie = "Prämie"
    override val opRenditeJahr = "Rendite p. a."
    override val opAusuebung = "Ausübung"
    override fun opStillBeispiel(call: List<String>?, put: List<String>?) = listOfNotNull(
        call?.let { "Call zu ${it[0]}: Die Prämie von ${it[1]} je Aktie bringt bis zum Verfall ${it[2]}. Steigt der Kurs über ${it[0]}, gehen die Aktien zu diesem Preis weg, mit Kursgewinn zusammen ${it[3]}. Ein Verlust entsteht erst unter ${it[4]}." },
        put?.let { "Put zu ${it[0]}: Die Prämie von ${it[1]} je Aktie bringt bis zum Verfall ${it[2]} auf das bereitgehaltene Geld. Fällt der Kurs unter ${it[0]}, kaufst du die Aktien zu diesem Preis, abzüglich Prämie also zu ${it[3]}." },
    ).joinToString("\n")
    override fun opErklaerung(k: String) = when (k) {
        "stillhalter" -> "Wer eine Option verkauft (schreibt), kassiert sofort die Prämie und verpflichtet sich, die Aktie zum Basispreis abzugeben (Call) oder zu kaufen (Put). Gerechnet wird mit dem Geldkurs, dem Preis, den Käufer gerade bieten, je Aktie ohne Gebühren; ein Kontrakt umfasst meist 100 Aktien. Die Rendite bezieht sich beim Call auf den Kurs, beim Put auf den Basispreis, und ist aufs Jahr hochgerechnet. Die Ausübungs-Chance stammt aus der impliziten Volatilität des Kontrakts und ist eine Modellrechnung. Steigt die Aktie stark, entgeht beim Call der Gewinn über dem Basispreis. Fällt sie stark, trägt man beim Put den Verlust selbst, nur gemindert um die Prämie. Im Übungsdepot lassen sich Optionsscheine nur kaufen, nicht schreiben. Der Rechner zeigt, was mit echten Kursen möglich wäre."
        "pcr" -> "Gehandelte Puts je Call und offene Puts je offenem Call. Über 1 sichern sich viele gegen fallende Kurse ab oder setzen darauf. Unter 1 überwiegen Calls, unter etwa 0,7 deutlich."
        "bewegung" -> "Preis von Call und Put am Geld zusammen. So weit bewegt sich der Kurs nach Einschätzung des Optionsmarkts bis zum Verfall im Mittel, nach oben oder unten. In etwa drei von fünf Fällen bleibt er innerhalb."
        "maxpain" -> "Kurs, bei dem die Optionen des nächsten Verfalls insgesamt am wenigsten wert verfallen. Kurz vor dem Verfall nähert sich der Kurs dem manchmal an. Eine Vorhersage ist es nicht."
        "iv" -> "Schwankung, die der Optionsmarkt für die nächsten 30 Tage einpreist, auf ein Jahr gerechnet. Gezeigt nur, wenn ein Verfall 20 bis 45 Tage entfernt liegt. Daneben steht die tatsächliche Schwankung der Aktie im letzten Jahr. Liegt die implizite deutlich darüber, erwartet der Markt Unruhe, etwa vor Quartalszahlen."
        else -> "Kontrakte mit mehr Umsatz als offenen Kontrakten und großem Prämienvolumen, Verfall in den nächsten 60 Tagen. Das deutet auf neue, große Positionen hin. Ob gekauft oder verkauft wurde, schätzt die App aus dem letzten Kurs."
    }
    override val kaTitel = "Kongress kauft"
    override val kaNav = "Strategie aus allen gemeldeten Käufen, mit Rückblick"
    override val kaFenster = "Depot aus den gemeldeten Käufen"
    override fun kaFensterKurz(tage: Int) = when {
        tage >= de.troetenrepublik.app.engine.KongressAuswertung.MAX_TAGE -> "Max"
        tage == 365 -> "1 J"; tage == 30 -> "30 T"
        else -> "${tage / 30} M"
    }
    override fun kaMonate(n: Int) = if (n == 1) "1 Monat" else "$n Monate"
    override val kaFensterEigen = "Eigener"
    override val kaFensterMonate = "Käufe der letzten"
    override val kaLaedt = "Geschäfte und Kurse werden geladen"
    override val kaVergleich = "S&P 500 pro Jahr"
    override val kaHinweisVerlauf = "Wöchentlich neu zusammengesetzt, in Dollar."
    override val kaJetzt = "Zusammensetzung heute"
    override val rgTitel = "Rangliste"
    override val rgNav = "Wessen Käufe den Markt geschlagen haben"
    override fun rgOrdnung(k: String) = when (k) { "QUOTE" -> "Trefferquote"; "KAEUFE" -> "Käufe"; "VERMOEGEN" -> "Vermögen"; else -> "Vorsprung" }
    override fun rgVermoegen(s: String) = "Vermögen $s"
    override fun vmSpanne(von: String, bis: String) = "$von bis $bis"
    override fun vmTitel(jahr: Int) = "Vermögen laut Jahresmeldung $jahr"
    override val vmHinweis = "Summe der Wertspannen aus der Jahresmeldung an den Clerk of the House. Selbst genutzte Wohnungen und staatliche Renten fehlen, das Vermögen kann höher sein."
    override val bhTitel = "Politik und Behörden"
    override val bhLobbying = "Lobbying"
    override fun bhJeQuartal(betrag: String) = "$betrag im Quartal"
    override fun bhQuartal(q: Int, j: Int, betrag: String, firmen: Int) = "Q$q $j: $betrag" + if (firmen > 0) ", $firmen beauftragte Lobbyfirmen" else ""
    override fun bhThemen(liste: String) = "Themen: $liste"
    override fun bhLobbyingQuelle(stand: String) = "LD-2-Meldungen beim Clerk of the House, Stand $stand. Meldet die Firma selbst, enthält ihr Betrag auch die Zahlungen an Lobbyfirmen."
    override fun bhGesetzeWert(gesetze: Int, personen: Int) = listOfNotNull(if (gesetze > 0) "$gesetze Gesetze" else null, if (personen > 0) "$personen handelten" else null).joinToString(" · ")
    override fun bhGesetz(g: String, n: Int) = "$g, in $n Meldungen genannt"
    override val bhAuftraege = "Regierungsaufträge"
    override fun bhFiskaljahr(betrag: String, jahr: Int) = "$betrag im Fiskaljahr $jahr"
    override fun bhFiskaljahrZeile(jahr: Int, betrag: String, laufend: Boolean) = "Fiskaljahr $jahr: $betrag" + if (laufend) " (läuft noch)" else ""
    override val bhAuftraegeQuelle = "Verpflichtungen aus Bundesaufträgen laut USAspending.gov, Fiskaljahr ab 1. Oktober. Darunter die größten Aufträge der letzten zwölf Monate mit ihrem Gesamtwert über die ganze Laufzeit."
    override val bhSpenden = "Wahlkampfspenden"
    override fun bhSpendenWert(betrag: String, zyklus: Int) = "$betrag (${zyklus - 1}/${zyklus % 100})"
    override val bhKeinPac = "Kein Wahlkampfkomitee (PAC) gefunden"
    override fun bhPacSummen(zyklus: Int, einnahmen: String, kandidaten: String) = "Wahlzyklus ${zyklus - 1}/${zyklus % 100}: Einnahmen $einnahmen, an Kandidaten $kandidaten. Größte Empfänger:"
    override fun bhSpendenQuelle(demo: Boolean) = "Spenden des Wahlkampfkomitees der Firma (PAC) laut FEC. Das Komitee sammelt Geld bei den Beschäftigten, das Firmenvermögen bleibt außen vor." + if (demo) " Ohne eigenen Schlüssel (Zugänge) nur wenige Abrufe je Stunde." else ""
    override val bhLeer = "Leerverkäufe"
    override val bhFinraOhne = "Leerverkäufe und Dark Pools von FINRA brauchen einen eigenen, kostenlosen FINRA-Zugang. Eintragen unter Profil → Zugänge. Ohne Zugang stehen die Zahlen auf den Seiten von FINRA."
    override val bhFinraLeerLink = "Offene Leerverkäufe bei FINRA"
    override val bhFinraAtsLink = "Dark-Pool-Handel bei FINRA (OTC Transparency)"
    override val dpTitel = "Dark Pools (ATS)"
    override fun dpWoche(woche: String, aktien: String, anteil: String?, schnitt: String?, wochen: Int) =
        "Woche ab $woche: $aktien Aktien über Dark Pools" + (anteil?.let { ", $it des Umsatzes" } ?: "") +
            (schnitt?.let { ". Schnitt der letzten $wochen Wochen: $it" } ?: "")
    override fun dpPlaetze(plaetze: List<Pair<String, String>>) = "Größte Plattformen: " + plaetze.joinToString(", ") { "${it.first} ${it.second}" }
    override fun dpVerzug(stufe: String?) = when (stufe) {
        "T1" -> "FINRA veröffentlicht diese Zahlen zwei Wochen nach der Handelswoche."
        "T2" -> "FINRA veröffentlicht diese Zahlen vier Wochen nach der Handelswoche."
        else -> "FINRA veröffentlicht diese Zahlen zwei bis vier Wochen nach der Handelswoche."
    }
    override val dpErklaerung = "Alternative Handelssysteme (ATS) sind Handelsplätze außerhalb der Börsen, an denen Aufträge vorab nicht sichtbar sind (Dark Pool). Gezählt sind die Aktien, die alle ATS für die Woche an FINRA melden, als Anteil am Wochenumsatz aller Handelsplätze. Ein Anstieg zeigt mehr Handel großer Pakete und sagt nichts über Kauf oder Verkauf. Übriger außerbörslicher Handel fehlt."
    override fun bhLeerTage(anteil: String, tage: Int) = "Leer verkauft in den letzten $tage Handelstagen: $anteil des außerbörslich gemeldeten Umsatzes"
    override fun bhLeerBestand(stichtag: String, aktien: String, aenderung: String?, tage: String?) = "Offene Leerverkäufe am $stichtag: $aktien Aktien" + (aenderung?.let { " ($it zum Stichtag davor)" } ?: "") + (tage?.let { ", $it Tage zum Eindecken" } ?: "")
    override fun bhLeerVerlauf(seit: String, tief: String, hoch: String) = "Offene Leerverkäufe seit $seit, zwischen $tief und $hoch Mio. Aktien"
    override val bhLeerErklaerung = "Oben der Anteil leer verkaufter Aktien am Umsatz der letzten Handelstage laut FINRA-Meldung der Handelsplätze außerhalb der Börse. Marktmacher verkaufen dort oft leer, ein hoher Wert ist normal. Aussagekräftiger ist der Bestand offener Leerverkäufe, den FINRA zweimal im Monat veröffentlicht. Die Linie zeigt ihn bis zu drei Jahre. Steigt er mit dem Kurs, müssen sich viele Leerverkäufer bei weiteren Gewinnen eindecken."
    override val bhEntlassungen = "Entlassungen"
    override fun bhEntlassungenWert(meldungen: Int, betroffene: String) = "$betroffene in 12 Monaten"
    override fun bhBetroffene(n: Int) = if (n > 0) "$n Betroffene" else ""
    override val bhEntlassungenQuelle = "Meldungen nach dem WARN Act an die Arbeitsbehörden der Bundesstaaten, gesammelt vom WARN Feed (github.com/APVentureEngine/warn-act-notices, CC BY 4.0), dazu Texas über data.texas.gov. Nicht jeder Staat ist dabei, und kleinere Entlassungen müssen nicht gemeldet werden."
    override val bhPatente = "Patente"
    override fun bhPatenteWert(n: Int) = "$n in 12 Monaten"
    override val bhPatenteQuelle = "Erteilte US-Patente mit der Firma als erster Anmelderin laut Open Data Portal des USPTO, mit deinem Schlüssel."
    override fun bhThema(code: String) = when (code) { "DEF" -> "Verteidigung"; "BUD" -> "Haushalt"; "TAX" -> "Steuern"; "HCR" -> "Gesundheit"; "TRD" -> "Handel"; "CPI" -> "Computer und IT"; "SCI" -> "Wissenschaft"; "ENG" -> "Energie"; "ENV" -> "Umwelt"; "TRA" -> "Verkehr"; "FIN" -> "Finanzen"; "BAN" -> "Banken"; "LBR" -> "Arbeit"; "AGR" -> "Landwirtschaft"; "TEC" -> "Telekommunikation"; "CSP" -> "Verbraucherschutz"; "IMM" -> "Einwanderung"; "HOM" -> "Heimatschutz"; "AVI" -> "Luftfahrt"; "PHA" -> "Arzneimittel"; "MMM" -> "Medicare und Medicaid"; "FOR" -> "Außenpolitik"; "GOV" -> "Regierung"; "INS" -> "Versicherungen"; "BEV" -> "Getränke"; "FOO" -> "Lebensmittel"; "UTI" -> "Versorger"; "AUT" -> "Autos"; "CPT" -> "Urheberrecht und Patente"; "RES" -> "Immobilien"; "TOB" -> "Tabak"; "ALC" -> "Alkohol"; "CHM" -> "Chemie"; "MAN" -> "Industrie"; "TOU" -> "Tourismus"; "RRR" -> "Bahn"; "MAR" -> "Seefahrt"; "EDU" -> "Bildung"; "FUE" -> "Kraftstoffe"; "NAT" -> "Rohstoffe"; "SMB" -> "Kleinunternehmen"; "RET" -> "Altersvorsorge"; "ECN" -> "Wirtschaft"; "LAW" -> "Strafverfolgung"; "AER" -> "Raumfahrt"; "MIA" -> "Medien"; "CIV" -> "Bürgerrechte"; "VET" -> "Veteranen"; "TAR" -> "Zölle"; "MED" -> "Medizinforschung"; "ACC" -> "Rechnungslegung"; "CDT" -> "Rohstoffhandel"; "TRU" -> "Lkw-Verkehr"; "WAS" -> "Abfall"; "GAM" -> "Glücksspiel"; "FIR" -> "Waffen"; "ROD" -> "Straßenbau"; "URB" -> "Stadtentwicklung"; "TOR" -> "Haftungsrecht"; "DOC" -> "Washington, D.C."; "IND" -> "Indigene Völker"; "POS" -> "Post"; "SPO" -> "Sport"; "UNM" -> "Arbeitslosigkeit"; "WEL" -> "Sozialleistungen"; "BNK" -> "Insolvenz"; "CAW" -> "Luftreinhaltung"; "ANI" -> "Tiere"; "DIS" -> "Katastrophenschutz"; "FAM" -> "Familie"; else -> code }
    override val pmGross = "Große Wetten"
    override val pmEchtTitel = "Deine Positionen bei Polymarket"
    override val pmEchtKeine = "Zu dieser Adresse gibt es gerade keine Positionen."
    override val pmEchtHinweis = "Gelesen über deine öffentliche Adresse bei Polymarket, in Dollar. Die App kann damit nur lesen: kein Kaufen, Verkaufen oder Abheben."
    override val pmUebungTitel = "Übungswetten"
    override fun pmEchtZeile(ausgang: String, anteile: String, einstand: String, kurs: String) = "„$ausgang“ · $anteile Anteile zu $einstand, jetzt $kurs"
    override val pmEchtEinloesbar = "entschieden, einlösbar"
    override val zgPolyAdresse = "Öffentliche Adresse (0x…)"
    override val zgPolyGeheim = "Das sah nach einem privaten Schlüssel oder Wiederherstellungswörtern aus und wurde gelöscht. Nur die öffentliche Adresse eingeben."
    override val zgPolyUngueltig = "Eine Adresse beginnt mit 0x und hat danach 40 Zeichen aus 0–9 und a–f."
    override val pmGrossKeine = "Gerade keine großen Wetten"
    override fun pmGrossZeile(ausgang: String, kauf: Boolean, preis: String, wann: String) = (if (kauf) "„$ausgang“ gekauft" else "„$ausgang“ verkauft") + " zu $preis · $wann"
    override val bhSpendenLimit = "Der gemeinsame Testschlüssel der FEC ist gerade ausgeschöpft. Mit einem eigenen kostenlosen Schlüssel unter Zugänge geht es sofort."

    // 5.91 Kurschart und Marktkurven
    override val kcMarken = "Meine Marken"
    override val kcSpanne = "Kursspanne"
    override fun kcLinie(art: String) = when (art) { "EINSTAND" -> "Einstand"; "LIMIT" -> "Limit"; "STOPP" -> "Stopp"; "ALARM" -> "Alarm"; else -> "KO" }
    override fun kcMarke(art: String, stueck: String, betrag: String, kurs: String) = when (art) {
        "KAUF" -> "Kauf $stueck Stk. zu $kurs"
        "VERKAUF" -> "Verkauf $stueck Stk. zu $kurs"
        else -> "Dividende $betrag"
    }
    override fun kcSpanneQuelle(implizit: Boolean, sigma: String) =
        if (implizit) "Aus der impliziten Volatilität der Optionen, $sigma im Jahr" else "Aus der Schwankung der letzten 60 Handelstage, $sigma im Jahr"
    override val mkTitel = "Marktkurven"
    override val mkUntertitel = "Zinskurve USA und Euroraum, VIX-Kurve"
    override val mkZins = "Zinskurve"
    override val mkUsa = "USA, Staatsanleihen"
    override val mkEuro = "Euroraum, AAA-Staatsanleihen"
    override val mkDeutschland = "Deutschland, Bundesanleihen"
    override val mkQuelleDeutschland = "Quelle: Deutsche Bundesbank, Zinsstruktur börsennotierter Bundeswertpapiere (Svensson-Methode), Rendite in Prozent"
    override fun mkStichtag(k: Int, datum: String) = when (k) { 0 -> "Aktuell · $datum"; 1 -> "Vor 1 Monat · $datum"; else -> "Vor 1 Jahr · $datum" }
    override fun mkLaufzeit(tage: Int): String {
        if (tage >= 365 && tage % 365 == 0) return "${tage / 365} J"
        if (tage >= 28) {
            val m = tage / 30.4375
            val r = Math.round(m)
            return if (kotlin.math.abs(m - r) < 0.1) "$r M" else String.format(java.util.Locale.GERMANY, "%.1f M", m)
        }
        return "$tage T"
    }
    override fun mkForm(form: String) = when (form) {
        "INVERS" -> "Invers: kurze Laufzeiten bringen mehr als 10 Jahre"
        "FLACH" -> "Flach: kurze und lange Laufzeiten bringen fast gleich viel"
        else -> "Normal: lange Laufzeiten bringen mehr"
    }
    override fun mkInvertiert(kurz: String, zins: String, zehn: String) = "$kurz mit $zins liegt über 10 Jahren mit $zehn"
    override fun mkAbstand(kurz: String, wert: String) = "10 Jahre minus $kurz: $wert Prozentpunkte"
    override val mkZinsMehr = "Die Zinskurve zeigt den Zins von Staatsanleihen je Laufzeit.\n• Normal: sie steigt, weil längere Bindung mehr Zins bringt\n• Flach: kurze und lange Laufzeiten bringen fast gleich viel\n• Invers: kurze Laufzeiten bringen mehr als zehn Jahre\n\nEine inverse Kurve ging in den USA vielen Rezessionen voraus, mit Monaten bis Jahren Abstand. Nicht auf jede folgte eine Rezession. Rote Marken zeigen kurze Laufzeiten über dem Zins für zehn Jahre."
    override val mkQuelleUsa = "Quelle: US-Finanzministerium, Daily Treasury Par Yield Curve Rates, Rendite in Prozent"
    override val mkQuelleEuro = "Quelle: Europäische Zentralbank, Zinskurve der AAA-Staatsanleihen im Euroraum (Spot-Rates), Rendite in Prozent"
    override val mkVix = "VIX-Kurve"
    override fun mkVixForm(form: String) = when (form) {
        "BACKWARDATION" -> "Fallend (Backwardation): kurzfristig mehr Angst als langfristig"
        "FLACH" -> "Flach: kurz- und langfristig ähnliche Erwartung"
        else -> "Steigend (Contango): der Normalfall"
    }
    override val mkVixMehr = "Die VIX-Indizes zeigen, wie stark Anleger den S&P 500 in den nächsten Tagen bis zu einem Jahr schwanken sehen, abgeleitet aus Optionspreisen.\n• Steigende Kurve (Contango): Normalfall, in ruhigen Zeiten wird kurzfristig weniger Schwankung erwartet\n• Fallende Kurve (Backwardation): in den nächsten Tagen wird mehr Schwankung erwartet, das zeigt akute Angst und kam vor allem in Ausverkäufen vor"
    override val mkVixQuelle = "Quelle: Schwankungsindizes der Cboe über Yahoo Finance (VIX1D, VIX9D, VIX, VIX3M, VIX6M, VIX1Y)"
    override val mkKeineDaten = "Gerade keine Daten erreichbar. Später noch einmal versuchen."

    // 5.91 Strategie-Labor, Backtest-Lotterie, Anlage-These, eigene Regeln, monatliche Einzahlung
    override val slTitel = "Strategietest"
    override val slUntertitel = "Signalregeln im Rückblick gegen Kaufen und Halten testen"
    override val slErklaerung = "Der Test zeigt, wie eine Regel in der Vergangenheit abgeschnitten hätte, für die Zukunft heißt das wenig. Nach Kosten schneiden die meisten Signalregeln schlechter ab als Kaufen und Halten. Gerechnet wird mit Tageskursen in Euro, das Signal entsteht am Schlusskurs, gehandelt wird zur Eröffnung des nächsten Tags."
    override val slZuWenig = "Für diesen Wert gibt es zu wenige Tageskurse für einen Test."
    override fun slKosten(spread: String) = "Start mit 10.000 €. Kosten je Kauf und je Verkauf: $spread Spread (aus den Einstellungen) und 1 € Ordergebühr. Steuern und Dividenden sind nicht eingerechnet."
    override val slRegeln = "Regeln"
    override fun slRegel(art: String, a: Int, b: Int, n: Int) = when (art) {
        "HALTEN" -> "Kaufen und Halten"
        "SMA" -> "Kurs über $a-Tage-Linie"
        "KREUZ" -> if (a == 50 && b == 200) "Kreuz 50/200 (Golden/Death Cross)" else "Kreuz $a/$b"
        "RSI" -> if (n == 14) "RSI: unter $a kaufen, über $b verkaufen" else "RSI $n Tage: unter $a kaufen, über $b verkaufen"
        "MACD" -> "MACD kreuzt die Signallinie"
        "DONCHIAN" -> "Ausbruch: $a-Tage-Hoch kaufen, $b-Tage-Tief verkaufen"
        "BOLLINGER" -> "Bollinger: unter unterem Band kaufen, über Mittellinie verkaufen"
        else -> art
    }
    override val slDieRegel = "Regel"
    override val slHalten = "Kaufen und Halten"
    override fun slVergleich(regel: String, halten: String) = "$regel · $halten"
    override val slSpalten = "Werte: Regel · Kaufen und Halten"
    override val slEndwert = "Endwert"
    override val slTreffer = "Trefferquote"
    override val slImMarkt = "Zeit im Markt"
    override fun slLern(datum: String) = "Lernzeitraum bis $datum, pro Jahr"
    override fun slPruef(datum: String) = "Prüfzeitraum ab $datum, pro Jahr"
    override val slLernText = "Lernzeitraum sind die ersten 70 % der Tage, Prüfzeitraum die letzten 30 %, jeder Teil mit frischen 10.000 €. Glänzt eine Regel nur im Lernzeitraum, ist das vermutlich Zufall. Trefferquote: Anteil der Geschäfte mit Gewinn nach Kosten."
    override val slLotterie = "Zufallstest"
    override val slLotterieText = "Wer viele Varianten einer Regel testet, findet fast immer eine, die im Rückblick glänzt, oft durch Glück. Ablauf:\n1. Alle Varianten laufen im Lernzeitraum, die mit der besten Sharpe-Quote (Rendite im Verhältnis zur Schwankung) wird gewählt\n2. Sie läuft dann im Prüfzeitraum, den sie nicht kannte\n3. Eine Prozentzahl sagt, wie wahrscheinlich ihr Ergebnis kein Glückstreffer ist\n\nUnten wählst du, welche Varianten getestet werden."
    override fun slFamilie(key: String) = when (key) {
        "DURCHSCHNITT" -> "Durchschnitte 5 bis 250"
        "KREUZ" -> "Kreuze"
        "RSI" -> "RSI-Schwellen"
        else -> "Alle"
    }
    override val slLotterieLaeuft = "Varianten werden getestet"
    override val slVarianten = "Getestete Varianten"
    override val slBeste = "Beste im Lernzeitraum"
    override val slBesteLern = "Lernzeitraum, beste · Halten"
    override val slBestePruef = "Prüfzeitraum, beste · Halten"
    override val slDsr = "Wahrscheinlichkeit: kein Glückstreffer"
    override fun slDsrUrteil(echt: Boolean) = if (echt) "Die beste Variante hebt sich deutlich vom Zufall ab." else "Bei so vielen Versuchen wäre ein solches Ergebnis auch durch reinen Zufall zu erwarten."
    override val slDsrText = "Die Prozentzahl ist die geschätzte Wahrscheinlichkeit, dass die Sharpe-Quote der besten Variante mehr ist als Glück bei der Auswahl unter vielen Versuchen (Deflated Sharpe Ratio, Bailey und López de Prado, 2014). Ab 95 % hebt sich die Variante klar vom Zufall ab, darunter kann es ein Glückstreffer sein. Gemessen wird gegen null, bei langem Kursanstieg kann die Zahl also auch ohne Vorteil gegenüber Halten hoch sein. Schlägt die Variante im Prüfzeitraum Kaufen und Halten nicht, hilft ihr Vorsprung im Lernzeitraum wenig. Weil sich die Varianten stark ähneln, ist die Zahl ein grober Anhaltspunkt."
    override val thTitel = "Anlage-These"
    override val thHinzufuegen = "Anlage-These hinzufügen (freiwillig)"
    override val thGrund = "Kaufgrund"
    override val thAusstieg = "Ausstieg, wenn …"
    override val thObergrenze = "Obergrenze der Position im Depot"
    override val thPruefen = "Prüftermin"
    override val thKeinTermin = "Kein Termin"
    override fun thMonate(m: Int) = if (m == 1) "1 Monat" else "$m Monate"
    override fun thPruefAm(datum: String) = "Prüftermin: $datum"
    override val thRegelWarnung = "Eigene Regel verletzt. Die Order ist trotzdem möglich."
    override fun thVerstoss(art: String, ist: String, grenze: String, name: String) = when (art) {
        "POSITION" -> (if (name.isNotBlank()) "$name: " else "") + "Position $ist des Depots, eigene Grenze $grenze"
        "THESE" -> "Position $ist des Depots, Obergrenze der These $grenze"
        "HEBEL" -> if (name.isBlank()) "Hebelprodukt, eigene Regel: keine Hebelprodukte" else "Hebelprodukte im Depot: $name"
        "BARGELD" -> "Guthaben $ist des Depots, eigene Untergrenze $grenze"
        else -> art
    }
    override fun thSeit(datum: String) = "Festgehalten am $datum"
    override val thAusstiegLabel = "Ausstieg"
    override fun thObergrenzeWert(grenze: String, ist: String) = "Obergrenze $grenze, jetzt $ist des Depots"
    override val thFrage = "Grund noch gültig?"
    override val thVerkaufenErwaegen = "Verkaufen erwägen"
    override val thEntfernen = "These entfernen"
    override fun thMitteilung(name: String) = "Prüftermin für $name erreicht. Tippen, um den Kaufgrund zu prüfen."
    override val thBestaetigtToast = "Nächster Prüftermin in drei Monaten"
    override val thRegelnTitel = "Eigene Regeln"
    override val thRegelnText = "Eigene Grenzen für dein Depot. Verletzt ein Kauf eine davon, zeigt die Ordermaske eine Warnung, verboten wird nichts."
    override val thRegelPosition = "Höchstens in einer Position"
    override val thRegelHebel = "Keine Hebelprodukte"
    override val thRegelBargeld = "Mindestens als Guthaben"
    override val thAus = "Aus"
    override val thAn = "An"
    override val thRegelnOk = "Das Depot hält gerade alle eigenen Regeln ein."
    override val thAuswertung = "Thesen und Regeln"
    override val thMitThese = "davon mit These"
    override val thBestaetigt = "Prüftermine bestätigt"
    override val thErwogen = "Verkauf erwogen"
    override val thRegelbrueche = "Käufe gegen eigene Regeln"
    override val thOffeneTermine = "Fällige Prüftermine"
    override val thAuswertungText = "Gezählt werden Käufe und Kauforders in der App, seit es Anlage-Thesen gibt. Eine These hält fest, warum du kaufst und wann du aussteigen willst. Am Prüftermin fragt die App, ob der Grund noch gilt. Wer seine Gründe aufschreibt, erkennt später leichter, ob ein Gewinn aus der These kam oder aus Glück."
    override val ezTitel = "Monatlich einzahlen"
    override val ezUntertitel = "Jeden Monat automatisch virtuelles Geld"
    override fun ezAktiv(betrag: String, tag: String) = "$betrag $tag"
    override fun ezTagWert(tag: Int) = if (tag >= 31) "am Monatsende" else "am $tag."
    override val ezText = "Bucht jeden Monat am gewählten Tag eine Einzahlung ins Übungsdepot, vor den Sparplänen dieses Tags. Verpasste Monate holt die App einmal nach. Nur im Übungsdepot, nicht bei einem verbundenen echten Konto."
    override val ezTag = "Tag im Monat"
    override fun ezNaechste(datum: String) = "Nächste am $datum"
    override val ezBisher = "Bisher"
    override fun ezBisherWert(n: Int, summe: String) = "$n× · $summe"
    override val ezLoeschen = "Monatliche Einzahlung löschen"
    override val ezGespeichert = "Monatliche Einzahlung gespeichert"
    override val ezMitteilung = "Monatliche Einzahlung"
    override fun ezMitteilungText(betrag: String) = "$betrag sind auf deinem Verrechnungskonto."

    // 5.91 Risikobeitrag, Bilanzqualitaet, Kaufkraft, Kennzahlenvergleich, Reverse DCF
    override val rskTitel = "Risikobeitrag"
    override fun rskDepotSchwankung(pct: String) = "Schwankung des Depots: $pct pro Jahr"
    override val rskGewicht = "Gewicht"
    override val rskRisiko = "Anteil an der Schwankung"
    override fun rskZeile(gewicht: String, risiko: String) = "$gewicht des Depots · $risiko der Schwankung"
    override fun rskSatz(name: String, gewicht: String, risiko: String) = "$name ist $gewicht des Depots, macht aber $risiko der Schwankung aus."
    override val rskAusgeglichen = "Keine Position trägt deutlich mehr zur Schwankung bei, als ihr Gewicht erwarten lässt."
    override val rskOhneDaten = "Zu wenig gemeinsame Kurstage für eine Schätzung."
    override fun rskNichtEingerechnet(n: Int) = if (n == 1) "Eine Position ohne ausreichende Kurse ist nicht eingerechnet." else "$n Positionen ohne ausreichende Kurse sind nicht eingerechnet."
    override val rskHinweis = "Der Anteil an der Schwankung zeigt, wie viel vom Auf und Ab des Depots auf eine Position entfällt. Grundlage sind die Wochenrenditen der letzten zwölf Monate. Gerechnet wird Gewicht mal Beitrag zur Schwankung, geteilt durch die Schwankung des ganzen Depots. Werte, die stark schwanken oder sich mit vielen anderen gleichzeitig bewegen, tragen mehr bei, als ihr Gewicht zeigt. Zusammen ergeben die Anteile 100 %."
    override val rskParitaetTitel = "Gleicher Risikoanteil"
    override val rskParitaetText = "So müssten die Gewichte aussehen, damit jede Position gleich viel zur Schwankung beiträgt (Risikoparität)."
    override val rskUebernehmen = "Im Umschichten übernehmen"
    override val rskWarnung = "Die Rechnung schätzt Schwankung und Gleichlauf aus nur einem Jahr. Schon kleine Schätzfehler verschieben die Gewichte deutlich, und künftig können Schwankung und Gleichlauf anders ausfallen."
    override val rskLaden = "Kurse des letzten Jahres werden geladen"
    override val qsTitel = "Bilanzqualität"
    override val qsPiotroski = "Piotroski-F-Score"
    override fun qsScore(score: Int, von: Int) = "$score von $von Punkten"
    override fun qsStufe(stufe: String) = when (stufe) {
        "STARK" -> "stark"
        "MITTEL" -> "mittel"
        else -> "schwach"
    }
    override fun qsJahre(jahr: String, vorjahr: String) = "Geschäftsjahr bis $jahr gegen das Jahr bis $vorjahr"
    override fun qsPunkt(punkt: String) = when (punkt) {
        "RENDITE" -> "Gewinn auf das Vermögen"
        "CASHFLOW" -> "Operativer Cashflow positiv"
        "RENDITE_STEIGT" -> "Kapitalrendite gestiegen"
        "CASHFLOW_UEBER_GEWINN" -> "Cashflow höher als der Gewinn"
        "SCHULDEN_SINKEN" -> "Langfristige Schulden gesunken"
        "LIQUIDITAET_STEIGT" -> "Liquidität gestiegen"
        "KEINE_NEUEN_AKTIEN" -> "Keine neuen Aktien ausgegeben"
        "MARGE_STEIGT" -> "Bruttomarge gestiegen"
        else -> "Umsatz je Vermögen gestiegen"
    }
    override fun qsBegruendung(punkt: String, jetzt: String, vorher: String?) = when (punkt) {
        "RENDITE" -> "Nettogewinn je Bilanzsumme: $jetzt"
        "CASHFLOW" -> "Cashflow aus dem Geschäft: $jetzt"
        "CASHFLOW_UEBER_GEWINN" -> "Je Bilanzsumme: Cashflow $jetzt, Gewinn ${vorher.orEmpty()}"
        "SCHULDEN_SINKEN" -> "Langfristige Schulden je Bilanzsumme: $jetzt, im Vorjahr ${vorher.orEmpty()}"
        "LIQUIDITAET_STEIGT" -> "Umlaufvermögen je kurzfristige Schulden: $jetzt, im Vorjahr ${vorher.orEmpty()}"
        "KEINE_NEUEN_AKTIEN" -> "Aktien: $jetzt, im Vorjahr ${vorher.orEmpty()}"
        else -> "$jetzt, im Vorjahr ${vorher.orEmpty()}"
    }
    override val qsNichtPruefbar = "Nicht prüfbar, Angaben fehlen"
    override val qsAltman = "Altman-Z-Score"
    override fun qsZone(zone: String) = when (zone) {
        "SICHER" -> "sichere Zone"
        "GRAU" -> "Grauzone"
        else -> "gefährdete Zone"
    }
    override val qsZonen = "Über 2,99 sicher, 1,81 bis 2,99 Grauzone, unter 1,81 gefährdet."
    override val qsZonenAndere = "Fassung für Firmen außerhalb der Herstellung: über 2,6 sicher, 1,1 bis 2,6 Grauzone, unter 1,1 gefährdet."
    override fun qsAltmanJahrAndere(jahr: String) = "Bilanz bis $jahr"
    override fun qsBaustein(k: String) = when (k) {
        "WC" -> "Betriebskapital je Bilanzsumme"
        "RE" -> "Gewinnrücklagen je Bilanzsumme"
        "EBIT" -> "Betriebsgewinn je Bilanzsumme"
        "MV" -> "Börsenwert je Verbindlichkeiten"
        "BW" -> "Buchwert des Eigenkapitals je Verbindlichkeiten"
        else -> "Umsatz je Bilanzsumme"
    }
    override fun qsAltmanJahr(jahr: String) = "Bilanz bis $jahr, Börsenwert von heute"
    override val qsHinweis = "Beide Werte lesen die letzten Jahresabschlüsse.\n• Piotroski-F-Score: neun Punkte für Gewinn, Cashflow, sinkende Schulden, bessere Liquidität, keine neuen Aktien und steigende Effizienz gegenüber dem Vorjahr. 7 bis 9 Punkte gelten als stark, 0 bis 3 als schwach\n• Altman-Z-Score: fasst Bilanzkennzahlen zusammen und schätzt, wie nah eine Firma einer Pleite ist. Hersteller nutzen die Fassung von 1968, andere Firmen die spätere ohne Umsatz und mit Buchwert statt Börsenwert\n\nGrenzen: Beide blicken nur zurück, Sondereffekte verzerren sie. Für Banken, Versicherer und andere Finanzwerte passen beide nicht, für Immobiliengesellschaften passt Altman nicht. Quelle: Yahoo Finance."
    override val qsLaden = "Jahresabschlüsse werden geladen"
    override val qsKeineDaten = "Für diesen Wert liefert Yahoo keine Jahresabschlüsse."
    override val kkReal = "Real (Kaufkraft)"
    override fun kkSatz(real: String, nominal: String, inflation: String) = "Real $real, nominal $nominal, Inflation $inflation"
    override fun kkHinweis(usd: Boolean) = "Real heißt: um die Inflation bereinigt. Die Linie zeigt die Kaufkraft des Depots: Depotwert geteilt durch die Preissteigerung seit Beginn des Zeitraums. " +
        (if (usd) "Preisindex: US-Verbraucherpreise (CPI-U) der Federal Reserve Bank of St. Louis (FRED), sonst des Bureau of Labor Statistics. "
        else "Preisindex: Harmonisierter Verbraucherpreisindex des Euroraums (HICP) vom Datenportal der EZB. ") +
        "Die Monatswerte sind auf die Tage verteilt, der letzte Monat ist fortgeschrieben."
    override val kkLaden = "Preisindex wird geladen"
    override val kkKeineDaten = "Der Preisindex lässt sich gerade nicht laden."
    override val kvTitel = "Kennzahlen im Vergleich"
    override val kvEinstieg = "Mit ähnlichen vergleichen"
    override val kvEinstiegSub = "Bewertung, Margen, Wachstum und Schulden nebeneinander"
    override val kvAehnlich = "Ähnlich laut Yahoo"
    override val kvBranche = "Gleiche Branche"
    override val kvSuche = "Weitere Aktie suchen"
    override val kvSucheHinweis = "Name oder Kürzel"
    override val kvMax = "Bis zu 5 Aktien."
    override val kvMin = "Wähle mindestens eine weitere Aktie."
    override fun kvZeile(k: String) = when (k) {
        "KGV" -> "KGV"
        "KGV_ERWARTET" -> "KGV erwartet"
        "KUV" -> "KUV"
        "KBV" -> "Kurs-Buchwert"
        "EV_EBITDA" -> "EV/EBITDA"
        "BRUTTOMARGE" -> "Bruttomarge"
        "OPERATIVE_MARGE" -> "Operative Marge"
        "UMSATZWACHSTUM" -> "Umsatzwachstum"
        "VERSCHULDUNG" -> "Schulden je Eigenkapital"
        "DIVIDENDE" -> "Dividendenrendite"
        "KURS_CASHFLOW" -> "Kurs/Cashflow"
        "ROIC" -> "ROIC"
        "OWNER_EARNINGS" -> "Owner Earnings"
        "ZINSDECKUNG" -> "Zinsdeckung"
        else -> "Kursziel"
    }
    override val kvBester = "Hervorgehoben: der günstigste beziehungsweise stärkste Wert jeder Zeile."
    override val kvHinweis = "Daten von Yahoo Finance, meist die letzten zwölf Monate. Kennzahlen verschiedener Branchen sind nur bedingt vergleichbar."
    override val kvLaden = "Kennzahlen werden geladen"
    override val rdTitel = "Was im Kurs steckt"
    override fun rdSatz(x: String) = "Damit sich der heutige Preis lohnt, müsste der Gewinn 10 Jahre lang um rund $x pro Jahr wachsen."
    override fun rdSatzRand(x: String, mehr: Boolean) = if (mehr) "Damit sich der heutige Preis lohnt, müsste der Gewinn 10 Jahre lang um mehr als $x pro Jahr wachsen." else "Damit sich der heutige Preis lohnt, reicht es, wenn der Gewinn 10 Jahre lang um weniger als $x pro Jahr wächst."
    override val rdRendite = "Deine Renditeerwartung"
    override val rdZielKgv = "KGV nach 10 Jahren"
    override fun rdBranche(kgv: String) = "Branche $kgv"
    override fun rdKgvJetzt(kgv: String) = "Heutiges KGV: $kgv"
    override fun rdBisher(x: String, jahre: Int) = "Bisher: $x pro Jahr in den letzten $jahre Jahren"
    override fun rdAnalysten(x: String) = "Analysten erwarten für die nächsten zwölf Monate: $x"
    override val rdHinweis = "Gerechnet wird vom heutigen Kurs aus. Angenommen, der Gewinn wächst zehn Jahre gleichmäßig und die Aktie wird danach zum gewählten KGV gehandelt. Gezeigt wird das Wachstum, das für die gewählte Rendite pro Jahr nötig ist. Liegt es deutlich über dem bisherigen Wachstum, ist viel Optimismus eingepreist. Dividenden, Schulden und Sondereffekte fehlen, kleine Änderungen der Annahmen verschieben das Ergebnis stark. Bei Verlusten lässt sich nichts rechnen."

    // 5.91 Aufraeumen
    override val auStornierenTitel = "Order stornieren"
    override fun auStornierenText(name: String) = "Die offene Order für $name wird nicht mehr ausgeführt."
    override val auBehalten = "Behalten"
    override val auIndikatorenAbMonat = "Im Chart ab 1 Monat"

    // 5.91 Insider weltweit und US-Regierung
    override val ogeTitel = "Minister, Fed und Spitzenbeamte"
    override val ogeLeer = "Noch keine Meldungen geladen. Die Liste kommt von der OGE und vom Weißen Haus, sobald das Gerät sie abrufen kann."
    override fun ogeMeldungen(n: Int) = if (n == 1) "1 Meldung" else "$n Meldungen"
    override val ogeHinweis = "Mitglieder der US-Regierung melden Käufe und Verkäufe von Wertpapieren auf dem Formular 278-T, die Beträge als Spannen in US-Dollar, etwa 1.001 bis 15.000 $."
    override val ogeQuelle = "Quelle: U.S. Office of Government Ethics (extapps2.oge.gov) und Weißes Haus (whitehouse.gov/disclosures), OGE Form 278-T."
    override fun ogeUngelesen(n: Int) = if (n == 1) "1 Meldung ist noch nicht gelesen. Die App liest sie beim nächsten Öffnen." else "$n Meldungen sind noch nicht gelesen. Die App liest sie beim nächsten Öffnen."
    override fun ogePdf(datum: String) = "Neueste Meldung vom $datum"
    override fun ogeGehandelt(n: Int) = if (n == 1) "Ein Regierungsmitglied hat diesen Wert gehandelt (Meldung 278-T)." else "$n Regierungsmitglieder haben diesen Wert gehandelt (Meldungen 278-T)."
    override val f144Titel = "Angekündigte Verkäufe (Form 144)"
    override val f144Hinweis = "Führungskräfte und Großaktionäre kündigen größere Verkäufe vorher bei der SEC an, mit Stückzahl, geschätztem Wert und geplantem Tag. Ob und wann wirklich verkauft wird, zeigt erst Form 4."
    override fun f144Stueck(n: String) = "$n Stück"
    override fun f144Geplant(tag: String) = "geplant $tag"
    override val f13Titel = "Großaktionäre (13D/13G)"
    override val f13Hinweis = "Wer mehr als 5 % einer Firma hält, meldet das der SEC.\n• 13D: aktiv, der Halter will Einfluss nehmen, etwa auf den Vorstand oder einen Verkauf der Firma\n• 13G: passiv, die Aktien sind nur Anlage, wie bei großen Fondsgesellschaften\n\nGezeigt wird je Halter die jüngste Meldung."
    override val f13Aktiv = "13D, aktiv"
    override val f13Passiv = "13G, passiv"
    override val f13Quelle = "Quelle: U.S. Securities and Exchange Commission (EDGAR, Form 144, Schedule 13D und 13G), mit deiner Kontaktadresse abgerufen"
    override val omTitel = "Großaktionäre handeln"
    override val omHinweis = "Muscat Clearing & Depository zeigt die Käufe und Verkäufe von Haltern ab 5 % am letzten Handelstag, also großer Aktionäre. Gezeigt wird der Tag des Abrufs."
    override fun omAnteil(p: String) = if (p.isBlank()) "Großaktionär" else "Anteil danach $p %"
    override fun weltQuelle(land: String, stand: String) = when (land) {
        "LK" -> "Quelle: Colombo Stock Exchange, Dealings by Directors (cse.lk)"
        "OM" -> "Quelle: Muscat Clearing & Depository, Major Investors Trade Activities (mcd.om)"
        "EG" -> "Quelle: Egyptian Exchange, Trading of Insiders (egx.com.eg), selbst importiert"
        "NG" -> "Quelle: Nigerian Exchange, Notification of Share Dealing by Insiders (ngxgroup.com), selbst importiert"
        "KW" -> "Quelle: Boursa Kuwait, Insider Disclosures (boursakuwait.com.kw), selbst importiert"
        "QA" -> "Quelle: Qatar Stock Exchange, Insider Trades, selbst importiert · nur Stückzahlen"
        else -> "Quelle: $land"
    } + if (stand.isNotBlank()) ", Stand $stand" else ""
    override val saTitel = "Quellen zum Selbstabrufen"
    override val saUntertitel = "Wo es die Dateien gibt und wie du sie holst"
    override val saEinleitung = "Diese Register stellen ihre Daten auf der eigenen Seite bereit. Die App öffnet die Seite und übernimmt eine geladene Datei automatisch. Die Daten bleiben auf dem Gerät."
    override fun saName(id: String) = when (id) {
        "sedi" -> "Kanada: SEDI"
        "consob" -> "Italien: Consob"
        "bund" -> "Deutschland: Bundesanzeiger"
        "fca" -> "Großbritannien: FCA"
        "bse" -> "Indien: BSE"
        "egx" -> "Ägypten: EGX"
        "kw" -> "Kuwait: Boursa Kuwait"
        "qse" -> "Katar: QSE"
        "pl" -> "Polen"
        "li" -> "Liechtenstein"
        "mt" -> "Malta"
        "asx" -> "Australien: ASX"
        else -> id
    }
    override fun saInhalt(id: String) = when (id) {
        "sedi" -> "Insidergeschäfte kanadischer Firmen als Wochenbericht (PDF)."
        "consob" -> "Leerverkaufspositionen in italienischen Aktien (Excel-Datei PncPubbl.xlsx)."
        "bund" -> "Netto-Leerverkaufspositionen in deutschen Aktien ab 0,5 % des Grundkapitals (CSV-Datei)."
        "fca" -> "Summe der gemeldeten Netto-Leerverkaufspositionen je britischer Aktie (zwei CSV-Dateien: aktuell und früher)."
        "bse" -> "Insidergeschäfte indischer Firmen als Gesamtliste (CSV). Neue Meldungen der NSE holt die App je Aktie selbst."
        "egx" -> "Tagesbericht über Käufe und Verkäufe von Insidern und Großaktionären (englisches PDF)."
        "kw" -> "Meldung eines Insiders zu einem Geschäft (Formular als Webseite)."
        "qse" -> "Käufe und Verkäufe von Insidern am letzten Handelstag (Webseite). Die Seite rufst du selbst im Browser auf."
        "pl", "li", "mt" -> "Meldungen von Führungskräften nach der EU-Marktmissbrauchsverordnung (Formular als PDF)."
        "asx" -> "Meldung eines Direktors über eine Änderung seines Bestands (Appendix 3Y, PDF)."
        else -> ""
    }
    override fun saSchritte(id: String) = when (id) {
        "sedi" -> listOf(
            "In der App öffnen und den Schritten der Seite folgen.",
            "„View summary reports“, dann „Weekly summary“ wählen und die letzte abgeschlossene Woche als PDF laden.",
            "Die App übernimmt die Datei, wenn sie sie lesen kann. Wochenberichte als PDF wandelst du vorher mit tools/kanada/sedi.py in JSON um.",
        )
        "consob" -> listOf(
            "Öffnen tippen. Die Datei PncPubbl.xlsx lädt direkt von consob.it.",
            "Die App übernimmt sie.",
        )
        "fca" -> listOf(
            "In der App öffnen und auf der Seite der FCA die aktuelle Liste („current“) als CSV laden.",
            "Für den Verlauf des letzten Jahres zusätzlich die frühere Liste („historic“) laden.",
            "Die App übernimmt jede Datei und rechnet beide zusammen.",
        )
        "bund" -> listOf(
            "In der App öffnen. Die Seite zeigt die aktuellen Positionen.",
            "Für einen längeren Verlauf „historische Daten“ anhaken und suchen.",
            "„Als CSV herunterladen“ tippen. Die App übernimmt die Datei.",
        )
        "bse" -> listOf(
            "In der App öffnen.",
            "„Download All Records“ wählen. Die App übernimmt die CSV-Datei unverändert.",
        )
        "egx" -> listOf(
            "In der App öffnen und den Tagesbericht „Trading of Insiders“ suchen.",
            "Die PDF laden. Die App übernimmt sie.",
        )
        "kw" -> listOf(
            "In der App öffnen und unter „Insider Disclosures“ eine Meldung öffnen.",
            "Am Handy „Seite übernehmen“ tippen. Am Computer die Seite als HTML speichern, die App übernimmt die Datei.",
        )
        "qse" -> listOf(
            "Im eigenen Browser die Webseite der Qatar Stock Exchange (qe.com.qa) aufrufen und dort die Seite „Insider Trades“ öffnen.",
            "Warten, bis die Tabelle „Trades Details“ gefüllt ist, dann die Seite speichern: am Computer als HTML, am Handy über „Herunterladen“ im Browsermenü.",
            "Die Datei unter Profil › Erweitert › Registerdaten importieren wählen.",
        )
        "pl", "li" -> listOf(
            "In der App öffnen und eine Meldung nach Art. 19 MAR suchen.",
            "Die PDF laden. Die App übernimmt sie.",
        )
        "mt" -> listOf(
            "In der App öffnen und eine Meldung nach Art. 19 MAR suchen.",
            "Die PDF laden. Die App übernimmt sie.",
            "Die Börse Malta erlaubt das Speichern und Nutzen mit Quellenangabe, solange der Inhalt unverändert bleibt.",
        )
        "asx" -> listOf(
            "In der App öffnen, das Kürzel suchen und eine Meldung „Appendix 3Y“ öffnen.",
            "Die PDF laden. Die App übernimmt sie.",
            "Die ASX erlaubt die Meldungen nur für den privaten Gebrauch.",
        )
        else -> emptyList()
    }
    override val saOeffnen = "Seite öffnen"

    // 5.91 Profil
    override val erwUntertitel = "Spannen, Kursabruf, eigener Zinssatz, Registerdaten, Export für Portfolio Performance"
    override val erwHandel = "Handelskosten"
    override val erwKurse = "Kursabruf"
    override val erwZinsAuto = "Gerade folgt der Zins dem Leitzins der EZB. Wählst du hier einen Satz, gilt ab dann dein eigener."
    override val erwRegister = "Registerdaten"
    override val erwPp = "Portfolio Performance"
    override val planVorbelegt = "Tag und Uhrzeit sind vorbelegt. Tippe auf eine Zeile, um sie zu ändern."

    // 5.91 Wertpapierseite
    override val bwAlleKennzahlen = "Alle Kennzahlen"
    override val bwTer = "Laufende Kosten (TER)"
    override val bwPreisalarm = "Preisalarm"
    override fun bwPreisalarmZahl(n: Int) = "Preisalarm · $n"
    override val bwOffeneOrders = "Offene Orders"
    override val bwFuerUndWider = "Chancen und Risiken"
    override val bwHebelprodukte = "Hebelprodukte"
    override val bwHintergrund = "Hintergrund"
    override fun bwHintergrundUnter(themen: List<String>) = (if (themen.size == 1) "1 Thema: " else "${themen.size} Themen: ") + themen.joinToString(", ")
    override fun bwThema(key: String) = when (key) {
        "OPTIONEN" -> "Optionen"; "TRENDS" -> "Trends"; "HANDELNDE" -> "Wer handelt"; "FUEHRUNG" -> "Insider"
        "LEER" -> "Leerverkäufe"; "FDA" -> "FDA"; "BEHOERDEN" -> "Politik und Behörden"; "LOBBY" -> "Lobbyarbeit"
        "HALTER" -> "Große Halter"
        else -> key
    }
    override val bwRrUnter = "Größter Rückgang, Dauer bis zur Erholung, Rendite"
    override val bwGesetze = "Gesetze"
    override val bwGesetzeHinweis = "Gesetze, zu denen die Firma Lobbyarbeit gemeldet hat. Wer aus dem Kongress die Aktie handelt, steht unter „Wer handelt den Wert“."

    // 5.91 Startseite
    override val maerkteTitel = "Märkte"
    override val maerkteBewegungen = "Bewegungen"
    override val maerkteSucheZeile = "Größte Bewegungen, Börsengänge, Stimmung, Ausblick, Trends und Heatmap"
    override val handeln = "Handeln"
    override val zeitraumMenue = "Eigener Zeitraum und Handelskalender"

    // 5.92 Datenquellen
    override val dqTitel = "Datenquellen"
    override val dqUntertitel = "Welche Quellen gerade antworten"
    override val dqEinleitung = "Hier siehst du, welche Datenquellen der App gerade antworten.\n\n" +
        "• Grün: antwortet\n" +
        "• Gelb: zuletzt ein Fehler, die zuletzt geladenen Daten bleiben sichtbar\n" +
        "• Rot: antwortet seit Stunden nicht\n" +
        "• Grau: noch nicht genutzt oder abgeschaltet"
    override fun dqName(id: String) = when (id) {
        "investorenbriefe" -> "Briefe von Fondsmanagern"
        "yahoo" -> "Yahoo Finance (Kurse, Kennzahlen, News)"
        "sec" -> "SEC (US-Meldungen)"
        "ezb" -> "EZB (Zinsen, Wechselkurse)"
        "wikipedia" -> "Wikipedia und Wikidata"
        "polymarket" -> "Polymarket"
        "kalshi" -> "Kalshi"
        "erwaehnungen" -> "Erwähnungen (Reddit, ApeWisdom)"
        "kongress" -> "US-Kongress"
        "usbehoerden" -> "US-Behörden (Aufträge, Lobbying, FDA)"
        "uswirtschaft" -> "US-Wirtschaftsdaten (Treasury, Fed, CFTC)"
        "bafin" -> "BaFin"
        "europa" -> "Europäische Register"
        "nasdaq" -> "Nasdaq (Kalender)"
        "welt" -> "Register weltweit"
        "stimmung" -> "Stimmungsbarometer"
        "truth" -> "Truth Social Archiv"
        "nachrichten" -> "Nachrichtendienste mit eigenem Schlüssel"
        "github" -> "GitHub (neue Fassungen, Quellen-Stand)"
        "fruehsignale" -> "Lager und Wirtschaftsdaten (EIA, TSA, Dürre, GIE)"
        else -> id
    }
    override val dqQuellen = "Quellen"
    override val dqAus = "Vorübergehend abgeschaltet"
    override val dqNie = "Noch nicht abgerufen"
    override fun dqAntwortet(seit: String) = "Antwortet · zuletzt $seit"
    override fun dqUmweg(seit: String) = "Antwortet über einen Ersatzweg · zuletzt $seit"
    override fun dqFehler(art: String, letzterErfolg: String?) =
        "Zuletzt $art · " + if (letzterErfolg != null) "letzter Erfolg $letzterErfolg" else "noch kein Erfolg"
    override fun dqArt(art: de.troetenrepublik.app.engine.Quellenbuendel.Fehler?, code: Int) = when (art) {
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.GESPERRT -> if (code > 0) "gesperrt ($code)" else "gesperrt"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.LEER -> "leere Antwort"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.FORM -> "unerwartete Antwort"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.ZEIT -> "Zeitüberschreitung"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.NETZ -> "keine Verbindung"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.SERVER -> if (code > 0) "Serverfehler ($code)" else "Serverfehler"
        de.troetenrepublik.app.engine.Quellenbuendel.Fehler.AUS -> "abgeschaltet"
        null -> "Fehler"
    }
    override val dqStandKopf = "Quellen-Stand"
    override val dqStandLabel = "Stand"
    override fun dqStand(version: Long, geladen: Boolean) = if (geladen) "$version (nachgeladen)" else "$version (eingebaut)"
    override val dqGesucht = "Zuletzt gesucht"
    override val dqNochNicht = "Noch nicht"
    override fun dqErgebnis(ergebnis: String, http: Int) = when (ergebnis) {
        "NEU" -> "neuer Stand übernommen"
        "AKTUELL" -> "aktuell"
        "HTTP" -> "nicht gefunden ($http)"
        "OHNE_SIGNATUR" -> "Signatur fehlt"
        "SIGNATUR" -> "Signatur ungültig"
        "FORM" -> "Inhalt unlesbar"
        "HOST" -> "unbekannte Adresse, abgelehnt"
        "VERSION" -> "aktuell"
        else -> "nicht erreichbar"
    }
    override val dqSuchen = "Jetzt nach neuem Stand suchen"
    override val dqSucht = "Suche läuft"
    override val dqZuruecksetzen = "Zähler zurücksetzen"
    override val dqHinweisLink = "Mehr dazu"
    override val dqAusblenden = "Ausblenden"
    override val dqVerstanden = "Verstanden"
    override fun dqHinweisTitel(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis) = h.titel
    override fun dqHinweisText(h: de.troetenrepublik.app.engine.Quellenbuendel.Hinweis) = h.text

    // 5.92 P3 Startseite, Profil, Einstellungen
    override val wfTitel = "Währungen beim Antippen"
    override val wfText = "Ein Tipp auf einen Betrag oder Kurs wechselt in dieser Reihenfolge. Gebucht wird weiter in Euro."
    override val wfReihenfolge = "Reihenfolge"
    override val wfHoch = "Nach oben"
    override val wfRunter = "Nach unten"
    override fun wfVoll(n: Int) = "Höchstens $n Einträge"
    override val wfOriginal = "Originalwährung"
    override val wfOriginalKurz = "Original"
    override val wfOriginalHinweis = "Kurs an der Heimatbörse, Betrag einer fremden Meldung"
    override val wfVorgabe = "Vorgabe wiederherstellen"
    override val wlZeile = "Dividenden wiederanlegen"
    override val wlWieEinstellung = "Wie in den Einstellungen"
    override val wlEigeneWahl = "Eigene Wahl für diesen Wert"
    override val wlGlobal = "Dividenden automatisch wiederanlegen"
    override val wlGlobalHinweis = "Kostenlos in Bruchstücke derselben Aktie, je Position änderbar"
    override val wlZinssatz = "Zinssatz"
    override val wlSortSeit = "Nach Entwicklung seit Hinzufügen"
    override val wlSortDatum = "Nach Hinzufügedatum"
    override val zgKostenpflichtig = "Kostenpflichtig"
    override val zgIntroPunkte = listOf(
        "Einige Dienste geben ihre Daten nur mit eigenem Schlüssel heraus.",
        "Alle Zugänge hier sind kostenlos, den Schlüssel holst du beim Anbieter.",
        "Er bleibt auf diesem Gerät und geht nur an diesen Anbieter.",
        "Die App zählt jeden Abruf und bleibt unter den Limits.",
    )
    override fun zgZweck(dienst: String) = when (dienst) {
        "guardian" -> "Wirtschafts- und Börsennachrichten im Reiter „The Guardian“, mit Artikeltext."
        "finnhub" -> "Marktnachrichten im Reiter „Finnhub“: Märkte, Übernahmen, Devisen und Krypto."
        "alphavantage" -> "Marktnachrichten im Reiter „Alpha Vantage“."
        "fec" -> "Wahlkampfspenden des Komitees (PAC) einer Firma auf der Seite einer US-Aktie."
        "uspto" -> "Erteilte Patente des letzten Jahres auf der Seite einer US-Aktie."
        "finra" -> "Leerverkäufe und Dark-Pool-Handel auf der Seite einer US-Aktie."
        "reddit" -> "Neue Reddit-Beiträge zu einer Aktie mit Stimmung und eine eigene Rangliste unter Trends."
        "sec_kontakt" -> "Neue US-Insidermeldungen direkt von der SEC und Fondsberichte für „Was Investoren schreiben“."
        "poly_adresse" -> "Deine echten Positionen bei Polymarket unter „Meine Wetten“."
        "eodhd" -> "Nachrichten zur geöffneten Aktie auf ihrer Seite."
        else -> "Übersetzt Titel und Ausgänge der Prognosemärkte ins Deutsche."
    }
    override fun zgPunkte(dienst: String) = when (dienst) {
        "guardian" -> listOf(
            "Entwicklerschlüssel für nicht kommerzielle Nutzung",
            "Bis 500 Abrufe am Tag, die App nutzt höchstens 450",
            "Inhalte bleiben höchstens 24 Stunden in der App",
        )
        "finnhub" -> listOf(
            "Nur für den persönlichen Gebrauch, keine Weitergabe der Daten",
            "60 Abrufe je Minute, die App fragt alle 10 Minuten",
        )
        "alphavantage" -> listOf(
            "25 Abrufe am Tag, die App fragt höchstens einmal je Stunde",
            "Lehnt der Dienst den Schlüssel ab, zeigt die App seine Meldung",
        )
        "fec" -> listOf(
            "Schlüssel von api.data.gov mit einer E-Mail-Adresse",
            "1.000 Abrufe je Stunde, die App nutzt höchstens 900",
            "Ohne Schlüssel gilt ein Testschlüssel mit wenigen Abrufen",
            "Nur Summen der Komitees, keine Namen einzelner Spender",
        )
        "finra" -> listOf(
            "Kostenloses Konto in der FINRA API Console, dort einen Zugang vom Typ Public Credential anlegen",
            "Laut FINRA nicht für gewerbliche Zwecke",
            "Client-ID und Secret eintragen, die App holt damit ein Token",
            "Die App fragt höchstens 20 Mal je Minute und 500 Mal am Tag",
        )
        "uspto" -> listOf(
            "Schlüssel mit USPTO-Konto und Prüfung über ID.me",
            "Ungenutzte Schlüssel verfallen nach 90 Tagen",
            "Die App fragt höchstens 500 Mal am Tag",
        )
        "reddit" -> listOf(
            "Für nicht kommerzielle Nutzung, neue Zugänge nur auf Antrag",
            "Danach unter reddit.com/prefs/apps eine App anlegen",
            "Client-ID eintragen, bei „script“ oder „web app“ auch das Secret",
            "Die App liest nur, ohne dein Reddit-Konto",
        )
        "sec_kontakt" -> listOf(
            "Die SEC verlangt eine Kontaktadresse in jedem Abruf",
            "Die Adresse geht nur an die SEC",
            "Ohne Eintrag kommen neue Meldungen mit jeder Fassung der App",
        )
        "poly_adresse" -> listOf(
            "Es reicht die öffentliche Adresse (0x und 40 Zeichen)",
            "Sie geht nur an Polymarket",
            "Nie einen privaten Schlüssel oder Wiederherstellungswörter eingeben",
        )
        "eodhd" -> listOf(
            "Nur für den persönlichen Gebrauch, keine Weitergabe der Daten",
            "Keine Anzeige für andere, den Zugang nicht teilen",
            "20 Calls am Tag, Rücksetzung um Mitternacht GMT",
            "Ein Abruf kostet 5 Calls, also rund 4 Abrufe am Tag",
            "Die App fragt nur beim Aufklappen auf der Seite eines Werts und merkt sich das Ergebnis eine Stunde",
        )
        else -> listOf(
            "DeepL API Free mit 500.000 Zeichen im Monat",
            "Die App nutzt höchstens 450.000 und übersetzt jeden Text nur einmal",
            "Übertragen werden nur Marktinhalte, keine Daten von dir",
        )
    }
    override val saAnsehenTitel = "Nur zum Ansehen"
    override val saAnsehenText = "Für diese Quellen gibt es meist keinen Import. Die Seite öffnet sich in der App, eine geladene Datei übernimmt die App, wenn sie sie lesen kann."
    override fun saAnsehenName(id: String) = when (id) {
        "bh" -> "Bahrain: Bahrain Bourse"
        "jm" -> "Jamaika: Jamaica Stock Exchange"
        "tt" -> "Trinidad und Tobago: TTSE"
        "il" -> "Israel: Magna"
        "id" -> "Indonesien: IDX"
        "jp" -> "Japan: EDINET"
        "kr" -> "Südkorea: DART"
        "pt" -> "Portugal: CMVM"
        "hr" -> "Kroatien: Zagreber Börse"
        "jefs" -> "USA: Bundesrichter (JEFS)"
        "ep" -> "Abgeordnete des Europäischen Parlaments"
        "aph" -> "Australien: Parlament"
        "oge" -> "USA: Regierung (OGE)"
        else -> id
    }
    override fun saAnsehenInhalt(id: String) = when (id) {
        "bh" -> "Mitteilungen der Firmen, darunter Geschäfte von Insidern."
        "jm" -> "Mitteilungen über Käufe und Verkäufe von Direktoren und verbundenen Personen."
        "tt" -> "Meldungen über Geschäfte von Insidern unter den Mitteilungen der Firmen."
        "il" -> "Pflichtmeldungen israelischer Firmen, auch zu Beständen von Großaktionären und Führungskräften. Meist auf Hebräisch."
        "id" -> "Meldungen über Aktienbesitz von Direktoren und Großaktionären, meist auf Indonesisch."
        "jp" -> "Meldungen über Beteiligungen ab 5 % und Berichte der Firmen. Die Schnittstelle braucht einen kostenlosen Schlüssel."
        "kr" -> "Meldungen über Bestände von Führungskräften und Großaktionären. Die Schnittstelle Open DART braucht einen kostenlosen Schlüssel."
        "pt", "hr" -> "Geschäfte von Führungskräften nach der EU-Marktmissbrauchsverordnung."
        "jefs" -> "Jährliche Finanzberichte der US-Bundesrichter mit Anlagen und Geschäften. Jeder Bericht erst nach Zustimmung zu den Bedingungen."
        "ep" -> "Erklärungen zu privaten Interessen, als PDF auf der Seite jeder Person."
        "aph" -> "Register der Interessen der Abgeordneten mit Aktienbesitz, als PDF je Person."
        "oge" -> "Vermögensmeldungen hoher Regierungsmitarbeiter. Manche Berichte gibt die OGE nur auf Antrag mit Formular 201 heraus."
        else -> ""
    }

    // 5.92 P1 Wertpapierseite oben
    override val p1IndWaehlen = "Tippe auf einen Namen, um ihn im Chart zu zeigen."
    override val p1BilanzLaedt = "Trefferbilanz wird berechnet"
    override val p1BilanzWenig = "Zu wenige Signale für eine Trefferbilanz"
    override fun indForschung(art: String): String = when (art) {
        "sma50", "sma200" -> "Wirkte im Dow Jones bis 1986, auf neueren Daten kaum. Als Filter senkt der Durchschnitt vor allem Rückgänge, kaum die Rendite (Brock u. a. 1992, Fang u. a. 2014, Faber 2007)."
        "bb" -> "Bandsignale schlugen Kaufen und Halten nach Kosten nicht verlässlich. Die Bandbreite zeigt vor allem die aktuelle Schwankung (Lento u. a. 2007)."
        "ichimoku" -> "Nur wenige Studien. Mit Standardwerten zeitweise profitabel, bei Devisen gar nicht. Schwache Belege (Deng u. a. 2021)."
        "sar" -> "Kaum begutachtete Studien. Frühe Gewinne ähnlicher Trendsysteme bei Futures verschwanden nach 1985. Sehr schwache Belege (Park und Irwin 2007)."
        "rsi" -> "Gewinne nur in einzelnen Märkten ohne Korrektur für Mehrfachtests. Breite Tests finden nach Kosten keinen Vorteil (Chong und Ng 2008, Rink 2023)."
        "stoch" -> "Fast nur Studien aus der Praxis. Keine belastbaren Belege für Vorhersagekraft nach Kosten (Chong u. a. 2017)."
        "macd" -> "Eine Trendregel wie die Kreuzung zweier Durchschnitte. Frühe Vorteile verschwinden meist auf neuen Daten und nach Kosten (Chong und Ng 2008, Rink 2023)."
        "atr" -> "Auf hohe Schwankung folgt oft weitere hohe Schwankung. Als Risikomaß gut belegt, für die Kursrichtung ohne Aussage (Engle 1982, Mandelbrot 1963)."
        "obv" -> "Wenige Studien, etwa für Großchina und als Monatssignal für US-Indizes. Für Einzelaktien nicht belastbar belegt (Tsang und Chong 2009, Neely u. a. 2014)."
        "kerzen" -> "Für einzelne US-Aktien der 1990er fanden sich bei manchen Umkehrmustern Hinweise, für den Dow Jones 1992 bis 2002 nach Berücksichtigung des Zufalls kein Nutzen (Caginalp und Laurent 1998, Marshall u. a. 2006)."
        else -> null
    }?.let { "Forschung: $it" } ?: ""
    override val indForschungAllgemein = "Laut Forschung zeigen klassische Signale nach Kosten und auf neuen Daten kaum Vorhersagekraft. Belegt ist vor allem ihr Nutzen für Risiko und Schwankung (Park und Irwin 2007, Rink 2023)."
    override fun p1BilanzSeit(jahr: Int, signale: Int) = "\nSeit $jahr, $signale Signale"
    override fun p1BilanzAllgemein(treffer: String, vergleich: String, werte: Int, ab: Int) = "\nAllgemein ($werte Aktien und Indizes seit $ab): $treffer Treffer, sonst $vergleich"
    override fun p1BilanzZeile(stufe: String, treffer: String, vergleich: String, unruhe: Boolean): String {
        val wort = when (stufe) { "ERKENNBAR" -> "erkennbar"; "UMGEKEHRT" -> "eher umgekehrt"; else -> "gering" }
        return "Aussagekraft hier $wort · $treffer Treffer, sonst $vergleich"
    }
    override fun p1BilanzDetail(art: String, jahr: Int, signale: Int, richtig: Int, treffer: String?, vergleich: String?, tage: Int): String {
        val regel = when (art) {
            "sma50", "sma200" -> "Der Kurs kreuzt den Durchschnitt nach oben (steigend) oder nach unten (fallend)."
            "bb" -> "Der Kurs fällt unter das untere Band (Erholung erwartet) oder steigt über das obere (Rücksetzer erwartet)."
            "rsi" -> "Der RSI steigt wieder über 30 (steigend) oder fällt wieder unter 70 (fallend)."
            "stoch" -> "Die Stochastik steigt wieder über 20 (steigend) oder fällt wieder unter 80 (fallend)."
            "macd" -> "Die MACD-Linie kreuzt ihre Signallinie nach oben (steigend) oder nach unten (fallend)."
            "ichimoku" -> "Der Kurs verlässt die Wolke nach oben (steigend) oder nach unten (fallend)."
            "sar" -> "Die Punkte wechseln unter den Kurs (steigend) oder über den Kurs (fallend)."
            "obv" -> "Das OBV kreuzt seinen Durchschnitt über 20 Tage nach oben (steigend) oder nach unten (fallend)."
            "kerzen" -> "Ein Muster mit Richtung erscheint: eines der sechs Erholungsmuster nach einem Rückgang (steigend) oder eines der sechs Rücksetzermuster nach einem Anstieg (fallend). Doji zählen nicht."
            else -> "Die ATR steigt über das 1,3-Fache ihres üblichen Werts im Jahr davor."
        }
        if (treffer == null || vergleich == null) return "Seit $jahr gab es nur $signale Signale, zu wenige für eine Bilanz.\nAls Signal zählt: $regel"
        val ergebnis = if (art == "atr")
            "Seit $jahr gab es $signale Warnungen. In $richtig Fällen ($treffer) bewegte sich der Kurs in den $tage Handelstagen danach stärker als üblich. Ohne Warnung war das in $vergleich der Fälle so."
        else
            "Seit $jahr gab es $signale Signale. $richtig davon ($treffer) lagen $tage Handelstage später richtig. Ohne Signal lief der Kurs in derselben Zeit in $vergleich der Fälle in diese Richtung."
        return "$ergebnis\nAls Signal zählt: $regel\nErkennbar heißt: mindestens 5 Prozentpunkte über dem Vergleich und bei dieser Zahl von Signalen eher kein Zufall. Gezählt werden nur Signale, deren Prüfzeiträume sich nicht überschneiden."
    }
    override fun p1Spanne(unten: String, oben: String, tage: Int) = "In $tage Tagen meist zwischen $unten und $oben"
    override val p1SpanneMehr = "In etwa 2 von 3 Fällen landet der Kurs nach 30 Tagen in dieser Spanne, wenn er so schwankt wie angenommen. Die Richtung sagt sie nicht voraus."
    override val p1SeitKauf = "Im Depot seit"
    override fun p1Haltedauer(datum: String, tage: Long) = "$datum · " + if (tage < 1) "heute" else ptDuration(tage)
    override val p1HebelLong = "Steigend (Long)"
    override val p1HebelShort = "Fallend (Short)"
    override val pcEinfuehrung = "Ziehe über den Chart, um die Kurse eines Zeitpunkts zu sehen. Darunter wählst du die Darstellung, blendest Indikatoren oder Vergleichswerte ein und zeichnest eigene Linien.\n" +
        "Eine Kerze steht für einen Zeitabschnitt: Ihr Körper reicht vom ersten bis zum letzten Kurs, die dünnen Striche bis zum Hoch und Tief, die Farbe zeigt Anstieg oder Fall. Heikin-Ashi glättet die Kerzen, damit Trends ruhiger aussehen."
    override val pcDarstellung = "Darstellung"
    override val pcEinblenden = "Einblenden"
    override val pcZeichnen = "Zeichnen"
    override val pcOhlc = listOf("Anfang", "Hoch", "Tief", "Ende")
    override val pcEinstieg = "gut für den Einstieg"
    override val pcIndikatorenEinfuehrung = "Indikatoren rechnen aus vergangenen Kursen und Umsätzen. Linien im Kurschart liegen über dem Kurs, die übrigen bekommen ein eigenes Feld darunter."
    override fun pcWerkzeug(key: String) = if (key == "waagerecht") "Waagerechte Linie" else chartWerkzeug(key)

    // 5.92 P2 Wertpapierseite Analyse
    override val bkTitel = "Bewertung und Kennzahlen"
    override val bkBewertung = "Bewertung"
    override val bkWachstum = "Finanzen"
    override fun bkBewertungSatz(noetig: String, art: Int, bisher: String?) = when (art) {
        1 -> "Damit sich der heutige Preis lohnt, müsste der Gewinn 10 Jahre lang um mehr als $noetig pro Jahr wachsen."
        -1 -> "Der heutige Preis lohnt sich schon, wenn der Gewinn 10 Jahre lang um weniger als $noetig pro Jahr wächst."
        2 -> "Der heutige Preis lohnt sich sogar, wenn der Gewinn 10 Jahre lang um $noetig pro Jahr schrumpft."
        else -> "Damit sich der heutige Preis lohnt, müsste der Gewinn 10 Jahre lang um $noetig pro Jahr wachsen."
    } + (bisher?.let { " Bisher: $it pro Jahr." } ?: "")
    override fun bkFairerWert(preis: String, wachstum: String, abstand: String, darueber: Boolean) =
        "Fairer Wert bei $wachstum Wachstum pro Jahr: $preis. Der Kurs liegt $abstand ${if (darueber) "darüber" else "darunter"}."
    override val bkBewertungVerlust = "Die Firma macht derzeit keinen Gewinn. Ohne Gewinn lässt sich kein fairer Wert rechnen."
    override fun bkBilanzSatz(ampel: Int, gruende: List<String>) =
        listOf("Solide Bilanz", "Gemischte Bilanz", "Schwache Bilanz").getOrElse(ampel) { "" } + if (gruende.isEmpty()) "" else ": " + gruende.joinToString(", ")
    override fun bkGrund(key: String) = when (key) {
        "PROFITABEL" -> "profitabel"; "VERLUST" -> "macht Verlust"; "SCHULDEN_SINKEN" -> "Schulden sinken"; "SCHULDEN_STEIGEN" -> "Schulden steigen"
        "GEWINNE_STEIGEN" -> "Gewinne steigen"; "GEWINNE_SINKEN" -> "Gewinne sinken"; "GELD_FLIESST" -> "verdient Geld im Tagesgeschäft"
        "GELD_FEHLT" -> "verbraucht Geld im Tagesgeschäft"; "PLEITE_RISIKO" -> "erhöhtes Pleiterisiko"; "NEUE_AKTIEN" -> "gibt neue Aktien aus"
        else -> key
    }
    override fun bkWachstumSatz(umsatz: String, umsatzPct: String?, gewinn: String?, gewinnPct: String?) =
        "Umsatz $umsatz" + (umsatzPct?.let { " ($it)" } ?: "") + (gewinn?.let { " · Gewinn $it" + (gewinnPct?.let { p -> " ($p)" } ?: "") } ?: "")
    override fun bkWachstumUnter(jahr: String) = "Geschäftsjahr $jahr im Vergleich zum Vorjahr"
    override val bkFairSelbst = "Fairen Wert selbst rechnen"
    override fun bkGruppe(key: String) = when (key) {
        "BEWERTUNG" -> "Bewertung"; "RENTABILITAET" -> "Rentabilität"; "FINANZKRAFT" -> "Finanzkraft"; "DIVIDENDE" -> "Dividende"
        "AKTIONAERE" -> "Aktionäre"; "ERWARTUNG" -> "Erwartungen"; "HANDEL" -> "Handel und Stammdaten"
        else -> key
    }
    override fun bkVonBis(von: String, bis: String) = "$von bis $bis"
    override val bkKuv = "Kurs-Umsatz-Verhältnis"
    override val bkEvEbitda = "EV/EBITDA"
    override val bkPeg = "PEG"
    override val bkFcfRendite = "Free-Cashflow-Rendite"
    override val bkGesamtkapitalRendite = "Gesamtkapitalrendite"
    override val bkEbitdaMarge = "EBITDA-Marge"
    override val bkVerschuldung = "Schulden je Eigenkapital"
    override val bkLiquiditaet = "Liquiditätsgrad"
    override val bkDividendeFuenf = "Dividendenrendite im Schnitt von 5 Jahren"
    override val bkDividendenWachstum = "Dividendenwachstum pro Jahr"
    override val bkLeerQuote = "Leerverkaufsquote"
    override val bkLeerTage = "Tage zum Eindecken"
    override val bkInsider = "Anteil der Insider"
    override val bkInstitutionen = "Anteil der Institutionen"
    override val bkAktien = "Aktien im Umlauf"
    override val bkAktienVeraenderung = "Aktienzahl zum Vorjahr"
    override val bkZielSpanne = "Kursziele von bis"
    override val bkZielMedian = "Mittleres Kursziel"
    override val bkGewinnErwartet = "Gewinn je Aktie erwartet"
    override val bkGewinnWachstumJahr = "Gewinnwachstum dieses Jahr"
    override val bkGewinnWachstumFolge = "Gewinnwachstum nächstes Jahr"
    override val bkUmsatzWachstumFolge = "Umsatzwachstum nächstes Jahr"
    override val bkNaechsteZahlen = "Nächste Geschäftszahlen"
    override val bkJahresVeraenderung = "Kursänderung in 12 Monaten"
    override val anLaedt = "Einschätzungen werden geladen"
    override val anKeineDaten = "Keine Einschätzungen gefunden."
    override val anVerteilung = "Verteilung"
    override fun anVergleich(diff: Double) = when {
        diff > 0.1 -> "Freundlicher als vor drei Monaten."
        diff < -0.1 -> "Skeptischer als vor drei Monaten."
        else -> "Ungefähr wie vor drei Monaten."
    }
    override val anNurUs = "Einzelne Häuser und ihre Trefferbilanz führt die Quelle nur bei US-Aktien."
    override val anBereinigt = "Bereinigte Einschätzung"
    override fun anAlleHaeuser(n: Int) = if (n == 1) "1 Haus" else "Alle $n Häuser"
    override val anGewichtet = "Nach Trefferquote gewichtet"
    override val anZiel = "Kursziel im Schnitt"
    override fun anZielGewichtet(preis: String) = "gewichtet $preis"
    override val anTrefferAlle = "Treffer nach 3, 6 und 12 Monaten"
    override fun anTrefferSatz(quote: String, n: Int, monate: Int, index: String?, wenig: Boolean) =
        "$quote von $n Hoch- und Abstufungen lagen nach $monate Monaten richtig, gemessen ${index?.let { "gegen den $it" } ?: "an der Kursentwicklung"}." +
            if (wenig) " Das ist kaum mehr als Zufall." else ""
    override fun anHinweis(herde: String) =
        "Die bereinigte Einschätzung ist nur ein Anhaltspunkt. Sie gewichtet Häuser stärker, die bei dieser Aktie öfter richtig lagen, und neuere Urteile mehr als alte.\n" +
            "• Die Zahl neben der Stufe reicht von −2 (stark verkaufen) bis +2 (stark kaufen).\n" +
            "• Wenige Urteile je Haus: Die Quote wird zur Mitte gezogen.\n" +
            "• Gemessen gegen den Index, damit ein steigender Markt nicht als Treffer zählt.\n" +
            "• $herde der Urteile folgten binnen drei Tagen anderen Häusern in dieselbe Richtung und zählen weniger.\n" +
            "• Interessenkonflikte, etwa Bankgeschäfte mit der Firma, sind aus den Daten nicht erkennbar.\n" +
            "• Die Trefferquote misst nur vergangene Urteile."
    override val anHaeuser = "Häuser"
    override fun anStufe(s: Int) = when (s) {
        2 -> "Stark kaufen"; 1 -> "Kaufen"; 0 -> "Halten"; -1 -> "Verkaufen"; else -> "Stark verkaufen"
    }
    override fun anKursziel(preis: String) = "Kursziel $preis"
    override val anVeraltet = "älter als ein Jahr"
    override fun anZuWenige(n: Int) = if (n == 0) "Noch kein Urteil auswertbar" else "Erst $n ${if (n == 1) "Urteil" else "Urteile"} auswertbar"
    override fun anTreffer(je: List<String>, bereinigt: String) = "Treffer nach 3, 6, 12 Monaten: ${je.joinToString(" · ")} · bereinigt $bereinigt"
    override val anWiderspruch = "Einstufung und Kursziel passen nicht zusammen"
    override val crInfo = "Wie die Waage rechnet"
    override fun crLeer(anteil: String, trend: Int?, us: Boolean) =
        (if (us) "$anteil der frei handelbaren Aktien leer verkauft" else "$anteil der Aktien in gemeldeten Leerverkäufen") +
            when (trend) { 0 -> ", steigend"; 1 -> ", fallend"; 2 -> ", gleichbleibend"; else -> "" }
    override val crOptionen = "Optionshandel"
    override fun crOptionenWert(long: String) = "$long der Umsätze setzen auf steigende Kurse"
    override fun evErwartet(eps: String?, umsatz: String?) = "erwartet: " + listOfNotNull(eps?.let { "Gewinn je Aktie $it" }, umsatz?.let { "Umsatz $it" }).joinToString(", ")
    override val evLetzteZahlen = "Letzte Geschäftszahlen"
    override fun evQuartal(ende: String) = "Quartal bis $ende"
    override val rrNutzen = "Zeigt, wie tief der Kurs schon gefallen ist und wie lange es zurück zum alten Hoch dauerte. Daran siehst du, welchen Rückgang und welche Wartezeit du aushalten müsstest."
    override val rrErholung = "Erholung dauerte"
    override val rrNochNicht = "noch nicht erreicht"
    override val rrZeitraum = "Rückgänge in einem Zeitraum"
    override fun rrVonBis(von: String, bis: String) = "$von bis $bis"
    override val wzRueckblick = "Rückblick"
    override val wzRueckblickUnter = "Was aus einem Betrag oder einem Sparplan geworden wäre"
    override val wzEinmal = "Einmal"

    // 5.92 Maerkte
    override val maerkteAusblick = "Ausblick"
    override val infoErklaerung = "Erklärung"
    override val mpVomHochKurz = "vom Hoch"
    override val cotInfo = "Die US-Terminmarktaufsicht CFTC veröffentlicht jeden Freitag, wie große Marktteilnehmer an den US-Terminbörsen positioniert sind, mit Stand vom Dienstag.\n\n" +
        "• Gezeigt ist die Nettoposition großer Spekulanten wie Hedgefonds\n" +
        "• Der Balken zeigt, wo sie im Vergleich zu den letzten drei Jahren steht: ganz rechts so optimistisch wie nie, ganz links so pessimistisch wie nie\n\n" +
        "Bei Extremen sind oft sehr viele gleich positioniert, und der Markt kann sich schnell gegen sie bewegen."
    override fun cotKern(markt: String?, index: Int?) = when {
        markt != null && index != null && index >= 80 -> "Große Spekulanten sind bei $markt so optimistisch wie selten in drei Jahren."
        markt != null && index != null && index <= 20 -> "Große Spekulanten sind bei $markt so pessimistisch wie selten in drei Jahren."
        else -> "Große Spekulanten sind gerade bei keinem dieser Märkte auffällig einseitig positioniert."
    }
    override fun cotSatz(index: Int) = when {
        index >= 90 -> "So optimistisch wie fast nie"
        index >= 80 -> "Sehr optimistisch"
        index >= 60 -> "Eher optimistisch"
        index > 40 -> "Ausgeglichen"
        index > 20 -> "Eher pessimistisch"
        index > 10 -> "Sehr pessimistisch"
        else -> "So pessimistisch wie fast nie"
    }
    override fun cotDetail(anteil: String, aenderung: String, index: Int) =
        "Große Spekulanten halten netto $anteil der offenen Kontrakte, $aenderung Prozentpunkte zur Vorwoche. Index $index von 100 im Vergleich zu drei Jahren."
    override val sbSorgen = "Sorgen auf Wikipedia"
    override val sbQuelleSorgen = "Quelle: Wikimedia"
    override val sbSorgenMehr = "Wie oft die englischen Wikipedia-Artikel über Rezession und Börsencrash gelesen werden, verglichen mit dem letzten Jahr. Viel Lesen heißt viel Sorge und damit ein niedriger Wert."
    override fun trDepotSatz(betrag: String) = "Aus 1.000 € wären $betrag geworden"
    override val trZumEtf = "Zum ETF"
    override val rotTitel = "Sektorrotation"
    override val rotInfo = "Sektorrotation heißt: Anleger schichten Geld von einer Branche in eine andere um.\n\n" +
        "• Verglichen wird, wie jede Branche im letzten Monat gegenüber dem Schnitt aller lief, und ob sie sich gegenüber den zwei Monaten davor verbessert hat\n" +
        "• Wo es deutlich besser läuft, fließt Geld hinein, wo es deutlich schlechter läuft, heraus\n" +
        "• Der Grund ist eine Faustregel aus der Art der Branchen, dem VIX und den Zinsen\n\n" +
        "Gerechnet wird mit der Kursentwicklung der Branchen-ETFs aus der Liste."
    override val rotKeine = "Gerade keine deutliche Umschichtung zwischen den Branchen."
    override fun rotSatz(aus: String?, nach: String?) = when {
        aus != null && nach != null -> "Geld fließt aus $aus in $nach."
        nach != null -> "Geld fließt in $nach."
        aus != null -> "Geld fließt aus $aus ab."
        else -> ""
    }
    override fun rotGrund(grund: String) = when (grund) {
        "SICHERHEIT" -> "Die Unsicherheit steigt, Anleger suchen sichere und stetige Gewinne."
        "RISIKOFREUDE" -> "Die Risikofreude steigt, Anleger setzen wieder auf Wachstum und Konjunktur."
        "INFLATION" -> "Rohstoffe sind gefragt, oft ein Zeichen für Sorgen um die Inflation."
        "ABSICHERUNG" -> "Anleger sichern sich ab: Der VIX ist gestiegen, Gold und Rohstoffe sind gefragt."
        "ZINSSENKUNG" -> "Fallende oder erwartet sinkende Zinsen helfen zinsabhängigen Branchen wie Immobilien und Versorgern."
        "ZINSANSTIEG" -> "Steigende Zinsen helfen Banken und Versicherern."
        else -> ""
    }
    override val rotAus = "Heraus"
    override val rotNach = "Hinein"
    override val meTitel = "Was der Markt erwartet"
    override val meInfo = "Womit Anleger und Wettende gerade rechnen. Das ist eine Momentaufnahme und keine Vorhersage.\n\n" +
        "• US-Leitzins: aus den Fed-Funds-Futures, 100 minus Kurs ergibt den erwarteten Zins des Monats\n" +
        "• Die Prozentzahlen darunter sind Kurse von Polymarket: Ein Anteil zahlt 1 Dollar, wenn das Ereignis eintritt, der Kurs ist also die geschätzte Wahrscheinlichkeit\n\n" +
        "Wettmärkte können sich irren, besonders bei wenig Umsatz."
    override val meQuelle = "Quelle: Polymarket"
    override fun meThema(schluessel: String) = when (schluessel) {
        "fed" -> "Nächste Zinsentscheidung der Fed"
        "fed_jahr" -> "Zinssenkungen der Fed im Jahr"
        "ezb" -> "Nächste Zinsentscheidung der EZB"
        "rezession" -> "Rezession in den USA"
        "inflation" -> "Inflation in den USA"
        "oel" -> "Ölpreis (WTI)"
        "hormus" -> "Straße von Hormus"
        "iran" -> "Iran"
        "wahlen" -> "Wahlen"
        else -> schluessel
    }
    override val zeTitel = "US-Leitzins (Fed Funds)"
    override fun zeSatz(schritte: Double?): String {
        val n = schritte?.let { Math.round(kotlin.math.abs(it)).toInt() } ?: return "So soll sich der Zins nach den Terminkursen entwickeln."
        return when {
            schritte <= -0.75 -> "Der Markt rechnet in zwölf Monaten mit etwa " + (if (n == 1) "einer Senkung" else "$n Senkungen") + " um je 0,25 Prozentpunkte."
            schritte >= 0.75 -> "Der Markt rechnet in zwölf Monaten mit etwa " + (if (n == 1) "einer Erhöhung" else "$n Erhöhungen") + " um je 0,25 Prozentpunkte."
            else -> "Der Markt rechnet in den nächsten zwölf Monaten mit kaum einer Änderung."
        }
    }
    override val zeHeute = "Jetzt"
    override fun zeIn(monate: Int) = "in $monate Mon."
    override val zeQuelle = "Quelle: CME Fed-Funds-Futures über Yahoo"
    override val blTitel = "Blasen-Detektor"
    override val blInfo = "Blasen erkennt man meist erst hinterher. Diese Messgrößen standen vor früheren Übertreibungen hoch, etwa im Jahr 2000 und 2021. Keine sagt voraus, wann Kurse fallen.\n\n" +
        "• Rot: spricht für eine Überhitzung\n" +
        "• Gelb: erhöht\n" +
        "• Grün: unauffällig oder spricht gegen eine Blase\n\n" +
        "Unter „Spricht dagegen“ stehen Gründe, die hohe Kurse rechtfertigen können. Bei den Branchen zählen das Kursplus in zwölf Monaten und der Abstand zur 200-Tage-Linie."
    override fun blStufe(stufe: Int) = listOf("Blasengefahr gering", "Blasengefahr erhöht", "Blasengefahr hoch").getOrElse(stufe) { "" }
    override fun blSatz(rot: Int, alle: Int, heiss: String?) =
        "$rot von $alle Messgrößen auf Rot." + (heiss?.let { " Am heißesten gelaufen: $it." } ?: "")
    override val blDafuer = "Spricht für eine Blase"
    override val blDagegen = "Spricht dagegen"
    override val blBranchen = "Heißeste Branchen"
    override fun blSektor(jahr: String, zu200: String) = "$jahr in 12 Monaten · $zu200 zur 200-Tage-Linie"
    override val blQuelle = "Quellen: Yahoo, Weltbank, Wikimedia, CNN, St. Louis Fed (FRED)"
    override fun blName(schluessel: String) = when (schluessel) {
        "kgv" -> "KGV des S&P 500"
        "buffett" -> "Buffett-Indikator"
        "trend" -> "Abstand zur 200-Tage-Linie"
        "konzentration" -> "Gewicht der 10 Größten"
        "ipo" -> "Börsengänge"
        "stimmung" -> "Stimmung"
        "erwaehnung" -> "Gerede über Blasen"
        "gewinn" -> "Erwartetes Gewinnwachstum"
        "praemie" -> "Aktien gegen Anleihen"
        "breite" -> "Breite des Anstiegs"
        else -> schluessel
    }
    override fun blLage(schluessel: String, ampel: Int) = when (schluessel) {
        "kgv" -> listOf("Bewertung im üblichen Rahmen", "Höher als im langjährigen Schnitt", "Deutlich über dem Schnitt von etwa 16")
        "buffett" -> listOf("Aktien nicht teurer als üblich", "Aktien teurer als üblich", "Höher als zur Dotcom-Blase im Jahr 2000")
        "trend" -> listOf("Kein ungewöhnlicher Vorsprung", "Deutlich über dem Trend", "Weit über dem Trend")
        "konzentration" -> listOf("Breit verteilt", "Wenige Riesen mit viel Gewicht", "So konzentriert wie selten")
        "ipo" -> listOf("Neue Aktien normal gefragt", "Neue Aktien gefragt", "Neue Aktien sehr gefragt")
        "stimmung" -> listOf("Keine Gier", "Gier", "Extreme Gier")
        "erwaehnung" -> listOf("Blasen selten erwähnt", "Blasen öfter erwähnt als üblich", "Blasen so oft erwähnt wie selten im Jahr")
        "gewinn" -> listOf("Kräftiges Wachstum stützt die Kurse", "Mäßiges Wachstum", "Kaum Wachstum erwartet")
        "praemie" -> listOf("Aktien bringen deutlich mehr als Anleihen", "Kaum Vorsprung vor Anleihen", "Anleihen bringen mehr als die Aktiengewinne")
        "breite" -> listOf("Anstieg breit getragen", "Große Werte ziehen davon", "Anstieg hängt an wenigen Riesen")
        else -> emptyList()
    }.getOrElse(ampel) { "" }
    override fun blErklaerung(schluessel: String, bipJahr: Int?, bipQuartal: String?) = when (schluessel) {
        "kgv" -> "Kurs geteilt durch den Gewinn der letzten zwölf Monate, für den S&P 500 über den ETF SPY. Der langjährige Schnitt liegt bei etwa 16. Ab 20 gelb, ab 25 rot."
        "buffett" -> "Wert aller US-Aktien (Wilshire 5000) im Verhältnis zur Wirtschaftsleistung der USA" + (bipQuartal?.let { " (BIP im $it, aufs Jahr gerechnet)" } ?: bipJahr?.let { " (BIP $it laut Weltbank)" } ?: "") + ". Warren Buffett nannte ihn das beste Einzelmaß für die Bewertung. Ab 110 % gelb, ab 150 % rot."
        "trend" -> "Wie weit der S&P 500 über seinem Schnitt der letzten 200 Handelstage steht. Ab 8 % gelb, ab 15 % rot."
        "konzentration" -> "Anteil der zehn größten Aktien am Börsenwert aller S&P-500-Werte im Katalog. Lange lag er bei etwa 20 %, im Jahr 2000 bei gut 25 %. Ab 27 % gelb, ab 35 % rot."
        "ipo" -> "Kursplus des Renaissance-IPO-ETF in zwölf Monaten. Er enthält Aktien nach ihrem Börsengang. Ab 20 % gelb, ab 50 % rot."
        "stimmung" -> "Fear & Greed Index von CNN, sonst der eigene Angst-Gier-Index der App (Reiter Stimmung): 0 heißt extreme Angst, 100 extreme Gier. Ab 60 gelb, über 75 rot."
        "erwaehnung" -> "Aufrufe des englischen Wikipedia-Artikels über Wirtschaftsblasen. Gezeigt ist, wo die letzten sieben Tage im Vergleich zum Jahr liegen, 100 heißt so viele wie nie. Ab 60 gelb, ab 80 rot."
        "gewinn" -> "Um wie viel die Gewinne der S&P-500-Werte im Katalog im nächsten Geschäftsjahr steigen sollen, nach den Schätzungen der Analysten bei Yahoo. Ab 10 % grün, unter 3 % rot."
        "praemie" -> "Gewinnrendite der Aktien (100 geteilt durch das KGV) minus Rendite zehnjähriger US-Staatsanleihen. Unter null bringen sichere Anleihen mehr, dann hängt viel an künftigem Wachstum. Unter 2 Punkten gelb, unter 0 rot."
        "breite" -> "Vorsprung des S&P 500 nach Börsenwert (SPY) vor dem gleich gewichteten S&P 500 (RSP) in zwölf Monaten. Ab 4 Punkten gelb, ab 10 rot."
        else -> ""
    }

    // 5.92 Copytrading und Politik
    override fun copyOgeNeu2(n: Int, datum: String) = if (n == 1) "Neue Meldung vom $datum" else "$n neue Meldungen, zuletzt vom $datum"
    override val pdfLaden = "Original als PDF herunterladen"
    override val pdfKurz = "PDF"
    override val pdfGestartet = "Das PDF wird in den Download-Ordner geladen"
    override val pdfFehler = "Das PDF ließ sich nicht laden"
    override fun parteiKurz(p: String) = when (p) { "R" -> "Rep."; "D" -> "Dem."; "L" -> "Libertär"; else -> "Parteilos" }
    override fun parteiLang(p: String) = when (p) { "R" -> "Republikaner"; "D" -> "Demokraten"; "L" -> "Libertäre Partei"; else -> "Parteilos" }
    override fun usStaat(code: String, name: String) = if (code == "CA") "Kalifornien" else name
    override fun wahlkreisVon(staat: String, nr: Int) = "$staat, Wahlkreis $nr"
    override fun kammerSeit(senat: Boolean, jahr: Int) = (if (senat) "im Senat seit " else "im Repräsentantenhaus seit ") + jahr
    override val ausschuesse = "Ausschüsse"
    override fun ausschussFunktion(f: String) = when (f) {
        "Chair", "Chairman" -> "Vorsitz"; "Vice Chair", "Vice Chairman" -> "stellvertretender Vorsitz"; "Cochairman" -> "Kovorsitz"
        "Ranking Member" -> "Sprecher der Minderheit"; "Ex Officio" -> "kraft Amtes"; else -> null
    }
    override val vmHinweisOge = "Summe der Wertspannen aus der Jahresmeldung an die US-Ethikbehörde OGE.\n" +
        "• Zählt: Firmen, Rechte, Anlagen und das Vermögen der Ehefrau\n" +
        "• Abgezogen: Kredite und Forderungen aus Prozessen, ohne abbezahlte Kredite\n" +
        "• Spannen über 50 Mio. $ sind nach oben offen, Werte ohne Spanne fehlen\n" +
        "• Das Vermögen kann deshalb deutlich höher sein"
    override fun rgStand(wann: String) = "Stand: $wann"
    override fun rgErklaerung(k: String) = when (k) {
        "QUOTE" -> "Anteil der Käufe, die bis heute besser liefen als der S&P 500."
        "KAEUFE" -> "Zahl der Aktienkäufe in den letzten zwölf Monaten."
        "VERMOEGEN" -> "Vermögen laut Jahresmeldung, nur Repräsentantenhaus."
        else -> "So viele Prozentpunkte liefen die Käufe der letzten zwölf Monate besser als der S&P 500."
    }
    override val rgText2 = "So wird gerechnet:\n" +
        "• Aktienkäufe der letzten zwölf Monate, ab dem Wochenschluss nach dem Geschäft bis heute\n" +
        "• verglichen mit dem S&P 500 im selben Zeitraum\n" +
        "• gewichtet nach der Mitte der gemeldeten Spanne\n" +
        "• nur Personen mit mindestens drei Käufen mit Kurs"
    override fun rgPunkte(p: String) = "$p Pkt."
    override fun rgAusKaeufen(n: Int) = if (n == 1) "aus 1 Kauf" else "aus $n Käufen"
    override fun rgTreffer(besser: Int, von: Int) = "$besser von $von schlagen den S&P 500"
    override fun rgKaufsumme(betrag: String) = "zusammen etwa $betrag"
    override val kaQuelleKurz = "Quellen: Clerk of the House, US-Senat, Yahoo Finance"
    override val kaText2 = "Jede Woche ein Depot aus den Aktien, deren Kauf Abgeordnete und Senatoren im gewählten Zeitraum gemeldet haben.\n" +
        "• gewichtet nach der Mitte der gemeldeten Beträge, ab zehn Aktien höchstens 10 % je Aktie\n" +
        "• meldet dieselbe Person einen Verkauf, sinkt ihr Anteil um die Mitte der Verkaufsspanne\n" +
        "• gerechnet ab dem Tag der Meldung, so wie man es hätte nachbilden können\n" +
        "• ohne Gebühren und Steuern"
    override fun awKaeufeDanach(n: Int) = "Gekaufte Werte danach ($n Käufe)"
    override fun awVerkaeufeDanach(n: Int) = "Verkaufte Werte danach ($n Verkäufe)"
    override val awImPlus = "im Plus"
    override val awBesserAlsMarkt = "besser als S&P 500"
    override val awGefallen = "gefallen"
    override val awSchwaecherAlsMarkt = "schwächer als S&P 500"
    override val awMitte = "Median"
    override fun awUnusualKurz(anzahl: String, schnitt: String) = "$anzahl statt sonst $schnitt"
    override fun awVorAnstieg(n: Int) = "Danach +10 % in zwei Wochen ($n Käufe)"
    override fun awVorRueckgang(n: Int) = "Danach −10 % in zwei Wochen ($n Verkäufe)"
    override fun awMitZufall(anteil: String, zufall: String) = "$anteil, zufällig $zufall"
    override fun awHinweise(bewertet: Int, werte: Int) = "So wird gerechnet:\n" +
        "• $bewertet Käufe und Verkäufe in $werte Aktien und Fonds mit Kursen\n" +
        "• ab dem Tag des Geschäfts, Kurse ohne Dividenden\n" +
        "• Optionen, Anleihen und Tausch zählen nicht\n" +
        "• „zufällig“ heißt: dieselben Werte an beliebigen Tagen\n" +
        "• Eine Häufung vor Kursbewegungen ist kein Beleg für Insiderwissen"
    override fun ifFilterZahl(n: Int) = "Filter ($n)"
    override fun ifTreffer(n: Int) = if (n == 1) "1 Meldung" else "$n Meldungen"
    override val ifKeine = "Keine Meldungen zu diesen Filtern"
    override val ifRolle = "Rolle"
    override val ifGroesse = "Größe"
    override val ifMehr = "Weitere"
    override val ifOhnePlan = "Ohne Verkaufspläne"
    override val ifHinweis = "Branche und Land stammen aus dem Katalog der App, Werte außerhalb der großen Indizes fehlen dort."
    override fun ifAb(betrag: String) = "ab $betrag"
    override fun ifTage(t: Int) = if (t <= 0) "18 Monate" else "$t Tage"
    override fun usQuelleKurz(stand: String) = "Quelle: SEC" + if (stand.isNotBlank()) ", Stand $stand" else ""
    override val pmGrossGroesste = "Größte Wetten"
    override val pmProfis = "Erfolgreiche Händler"
    override val pmProfisLaedt = "Ranglisten und Wetten werden geladen"
    override val pmProfisKeine = "Gerade keine auffälligen Wetten erfolgreicher Händler"
    override val pmProfisText = "Wetten von Händlern, die bei Polymarket mit Politik, Wirtschaft oder Finanzen besonders viel gewonnen haben.\n" +
        "• Erfolg zählt nur in diesen Bereichen, Sportwetten nicht\n" +
        "• Gezeigt werden Käufe außerhalb des Sports, die groß sind, auf einen unwahrscheinlichen Ausgang setzen oder für den Händler ungewöhnlich groß sind\n" +
        "• Absicherungen auf beiden Seiten und fast sichere Ausgänge über 85 % zählen nicht\n" +
        "• Das kann auf Wissen oder starke Überzeugung deuten, ist aber kein Beleg"
    override val pmOhneSport = "Ohne Sport"
    override val pmGrossText2 = "Die größten Wetten bei Polymarket im gewählten Zeitraum.\n" +
        "• ab 10.000 $ in 24 Stunden, 50.000 $ in 7 Tagen, 200.000 $ in 30 Tagen und 500.000 $ in 60 Tagen\n" +
        "• das Thema stammt aus den Schlagworten des Ereignisses\n" +
        "• wer gewettet hat, zeigt die App nicht"
    override fun pmGrossZeit2(key: String) = when (key) { "TAG" -> "24 Std."; "WOCHE" -> "7 Tage"; "MONAT" -> "30 Tage"; else -> "60 Tage" }
    override fun pmProfiPlatz(rang: Int, kategorie: String, monat: Boolean) =
        "Platz $rang " + when (kategorie) { "ECONOMICS" -> "Wirtschaft"; "FINANCE" -> "Finanzen"; else -> "Politik" } + if (monat) " im Monat" else " gesamt"
    override fun pmProfiGrund(g: String) = when (g) { "AUSSENSEITER" -> "Außenseiter"; "GROSS" -> "großer Einsatz"; else -> "ungewöhnlich groß" }

    // 5.92 Aktienfinder und Radar
    override val afTitel = "Aktienfinder"
    override val afEinleitung = "Ordnet die Aktien eines Markts nach sieben Bereichen ein."
    override val afStart = "Aktien einordnen"
    override val afLaedt = "Kennzahlen werden geladen"
    override val afDetailsLaedt = "Weitere Kennzahlen für die ersten Werte werden geladen"
    override fun afDetailsMehr(n: Int) = if (n == 1) "Diese Aktie genauer prüfen" else "Die ersten $n genauer prüfen"
    override val afKeine = "Keine Aktie passt zu diesen Filtern."
    override fun afBereich(b: String) = when (b) {
        "BEWERTUNG" -> "Bewertung"; "QUALITAET" -> "Qualität"; "ANALYSTEN" -> "Analysten"; "TREND" -> "Kurstrend"
        "INSIDER" -> "Insider"; "LEERVERKAUF" -> "Leerverkäufe"; else -> "Stimmung"
    }
    override fun afEinordnung(e: String) = when (e) {
        "GUENSTIG" -> "günstig bewertet"; "FAIR" -> "fair bewertet"; "TEUER" -> "teuer bewertet"; else -> "Bewertung unklar"
    }
    override fun afGrund(k: String, gut: Boolean, wert: String, vergleich: String?): String {
        val branche = vergleich?.let { " (Branche $it)" }.orEmpty()
        return when (k) {
            "KGV" -> "KGV $wert$branche"
            "KGV_ERWARTET" -> "Erwartetes KGV $wert$branche"
            "KBV" -> "Kurs-Buchwert-Verhältnis $wert$branche"
            "PEG" -> "PEG $wert, also KGV im Verhältnis zum erwarteten Wachstum"
            "IM_KURS" -> "Im Kurs stecken $wert Gewinnwachstum pro Jahr, erwartet sind ${vergleich.orEmpty()}"
            "KUV" -> "Kurs-Umsatz-Verhältnis $wert$branche"
            "EV_EBITDA" -> "EV/EBITDA $wert$branche"
            "RENDITE" -> "Eigenkapitalrendite $wert"
            "WACHSTUM" -> "Erwartetes Gewinnwachstum $wert"
            "MARGE" -> "Gewinnmarge $wert"
            "SCHULDEN" -> "Schulden $wert des Eigenkapitals"
            "BILANZ" -> if (gut) "Solide Bilanz" else "Schwache Bilanz"
            "PIOTROSKI" -> "Piotroski-F-Score $wert von 9"
            "ALTMAN" -> if (gut) "Geringes Pleiterisiko nach Altman-Z" else "Erhöhtes Pleiterisiko nach Altman-Z"
            "NOTE" -> "Analystennote $wert (1 = kaufen, 5 = verkaufen)"
            "KURSZIEL" -> "Kursziel der Analysten $wert vom Kurs entfernt"
            "BEREINIGT" -> if (gut) "Treffsichere Analysten eher zuversichtlich" else "Treffsichere Analysten eher skeptisch"
            "JAHR" -> "Kurs in zwölf Monaten $wert"
            "LINIE200" -> "$wert ${if (gut) "über" else "unter"} der 200-Tage-Linie"
            "INSIDER" -> if (gut) "Insider kauften zuletzt mehr, als sie verkauften" else "Insider verkauften zuletzt mehr, als sie kauften"
            "LEER" -> "$wert der Aktien leer verkauft"
            else -> "Reddit: $wert Erwähnungen, am Vortag ${vergleich ?: "keine"}"
        }
    }
    override val afGewichte = "Gewichte"
    override val afGewichteHinweis = "Wie stark ein Bereich zählt. Ohne Daten zählt er mit 50 Punkten."
    override val afStandard = "Standard"
    override fun afSortierung(name: String) = "Nach $name"
    override val afPunktzahl = "Punktzahl"
    override val afDividende = "Dividende"
    override val afBoersenwert = "Börsenwert"
    override val afAlleBranchen = "Alle Branchen"
    override val afAlleRegionen = "Alle Regionen"
    override val afAlleLaender = "Alle Länder"
    override val afAlleGroessen = "Alle Größen"
    override val afJedeDividende = "Jede Dividende"
    override fun afDividendeAb(p: String) = "Dividende ab $p"
    override val afJedeBewertung = "Jede Bewertung"
    override fun afGroesse(g: String) = when (g) {
        "KLEIN" -> "Klein (unter 2 Mrd. €)"; "MITTEL" -> "Mittel (2 bis 10 Mrd. €)"; else -> "Groß (über 10 Mrd. €)"
    }
    override fun afMindest(p: Int) = if (p <= 0) "Ohne Mindestpunktzahl" else "Mindestens $p Punkte"
    override fun afTreffer(n: Int, gesamt: Int) = "$n von $gesamt Aktien"
    override fun afDatenlage(p: String) = "Daten für $p der Gewichte"
    override val afZurAktie = "Zur Aktie"
    override val afSoRechnet = "So rechnet der Aktienfinder"
    override val afErklaerung = "Jeder Bereich bekommt 0 bis 100 Punkte, 50 ist unauffällig. Die Gesamtpunktzahl ist der Schnitt nach den Gewichten.\n" +
        "• Bewertung: KGV, erwartetes KGV, Kurs-Buchwert, Kurs-Umsatz und EV/EBITDA im Vergleich zur Branche, dazu PEG und das im Kurs steckende Wachstum\n" +
        "• Qualität: Eigenkapitalrendite, erwartetes Gewinnwachstum, Gewinnmarge, Schulden und Bilanz (Piotroski, Altman)\n" +
        "• Analysten: Durchschnittsnote von 1 (kaufen) bis 5 (verkaufen), Abstand zum Kursziel und die nach Trefferquote bereinigte Einschätzung\n" +
        "• Kurstrend: zwölf Monate und Abstand zur 200-Tage-Linie\n" +
        "• Insider: Käufe und Verkäufe der Führung in sechs Monaten\n" +
        "• Leerverkäufe: mehr leer verkaufte Aktien, weniger Punkte\n" +
        "• Stimmung: Erwähnungen auf Reddit, nur US-Aktien\n" +
        "Günstig heißt mindestens 62 Punkte in der Bewertung, teuer höchstens 38.\n\n" +
        "Grenzen\n" +
        "• Ohne Daten zählt ein Bereich mit 50 Punkten\n" +
        "• Kennzahlen kommen verzögert von Yahoo und können Fehler enthalten\n" +
        "• Die Punkte beruhen auf Vergangenem und auf Erwartungen anderer"
    override val afHinweis = "Keine Anlageberatung. Daten von Yahoo und App-Meldungen, möglicherweise falsch oder veraltet."
    override fun afNachbilden(n: Int) = if (n == 1) "Depot aus dieser Aktie nachbilden" else "Depot aus den ersten $n nachbilden"
    override fun afStand(zeit: String) = "Stand $zeit"
    override val afQuelle = "Quellen: Yahoo, SEC, BaFin, Aufsichtsbehörden in Europa, ApeWisdom"
    override val afWeniger = "Weniger zeigen"
    override fun afMehrZeigen(n: Int) = "$n weitere zeigen"
    override val radTitel = "Radar"
    override val radEinleitung = "Sammelt Hinweise, die oft auftauchen, bevor eine Aktie breit beachtet wird."
    override val radStart = "Radar starten"
    override val radNeu = "Neu laden"
    override val radLaedt = "Quellen werden abgefragt"
    override val radKeine = "Gerade keine auffälligen Hinweise."
    override fun radArt(art: String) = when (art) {
        "ERWAEHNUNGEN" -> "Erwähnungen"; "WIKIPEDIA" -> "Wikipedia"; "INSIDER_GRUPPE" -> "Insidergruppe"; "INSIDER_KAUF" -> "Insiderkauf"
        "KONGRESS" -> "Kongress"; "REGIERUNG" -> "Regierung"; "GROSSAKTIONAER" -> "Großaktionär"; "OPTIONEN" -> "Optionen"
        "VOLUMEN" -> "Umsatz"; else -> "Prognosemarkt"
    }
    override fun radSignal(art: String, n: Int, a: String, b: String, text: String) = when (art) {
        "ERWAEHNUNGEN" -> "$a Erwähnungen auf Reddit in 24 Stunden, vorher $b"
        "WIKIPEDIA" -> "Wikipedia-Artikel $a häufiger aufgerufen als in den vier Wochen davor"
        "INSIDER_GRUPPE" -> "$n Personen aus dem Unternehmen kauften für zusammen $b"
        "INSIDER_KAUF" -> "${text.ifBlank { "Eine Führungskraft" }} kaufte für $a"
        "KONGRESS" -> if (n == 1 && text.isNotBlank()) "$text (Kongress) kaufte für etwa $b" else "$n Mitglieder des Kongresses kauften für etwa $b"
        "REGIERUNG" -> if (n == 1 && text.isNotBlank()) "$text (Regierung) kaufte für etwa $b" else "$n Regierungsmitglieder kauften für etwa $b"
        "GROSSAKTIONAER" -> "${text.ifBlank { "Ein Investor" }} meldet ${if (a.isNotBlank()) "$a " else ""}mit Absicht, Einfluss zu nehmen"
        "OPTIONEN" -> "Auffällige Käufe von Call-Optionen für $b"
        "VOLUMEN" -> "Umsatz $a so hoch wie im Schnitt, Kurs $b"
        else -> "Große Wette über $a: $text"
    }
    override val radAlle = "Alle Hinweise"
    override val radBekannt = "schon viel beachtet"
    override fun radArten(n: Int) = if (n == 1) "1 Art von Hinweis" else "$n Arten von Hinweisen"
    override val radSoRechnet = "So arbeitet das Radar"
    override val radErklaerung = "Je Aktie zählen die Hinweise der letzten 90 Tage, neuere wiegen mehr.\n" +
        "• Verschiedene Hinweise beim selben Wert zählen zusammen mehr\n" +
        "• Die zehn meisterwähnten Werte auf Reddit zählen weniger, weil sie schon im Gespräch sind\n" +
        "• Wikipedia-Aufrufe, Großaktionäre und Optionen prüft die App nur für die zehn stärksten Kandidaten\n" +
        "• Insidermeldungen und Käufe aus der Politik werden frischer, wenn du die Insider- und Politikseiten öffnest\n\n" +
        "Grenzen\n" +
        "• Viele Hinweise führen zu nichts: Insider kaufen auch aus Pflicht, Politiker melden mit bis zu 45 Tagen Verspätung, Umsätze steigen auch durch Indexänderungen\n" +
        "• Das Radar sagt nichts darüber, ob ein Kurs steigt"
    override val radHinweis = "Keine Anlageberatung. Ein Hinweis ist keine Vorhersage."
    override fun radQuelle(namen: List<String>) = "Quellen: " + namen.joinToString(", ")
    override fun radQuelleName(k: String) = when (k) {
        "REDDIT" -> "ApeWisdom"; "INSIDER" -> "SEC, BaFin, Aufsichtsbehörden"; "POLITIK" -> "Kongress, OGE"; "VOLUMEN", "OPTIONEN" -> "Yahoo"
        "PROGNOSE" -> "Polymarket"; "WIKIPEDIA" -> "Wikimedia"; "SEC13D" -> "SEC"; else -> k
    }
    override fun radStand(zeit: String) = "Stand $zeit"
    override val radHeute = "heute"

    // 5.92 Fruehindikatoren
    override val fiTitel = "Lager und Wirtschaftsdaten"
    override val fiInfo = "Daten, die früh zeigen können, wie es um Angebot und Nachfrage steht. An den Öllagern sieht man zum Beispiel, ob Rohöl knapper wird als sonst um diese Jahreszeit.\n\n" +
        "Ein Tipp auf einen Eintrag zeigt den Vergleich mit den Vorjahren.\n\n" +
        "Grenzen:\n" +
        "• „Im Preis?“ ist eine Schätzung aus der Geschichte, denn Profis werten dieselben Zahlen oft Sekunden nach der Veröffentlichung aus\n" +
        "• Ohne Schätzung zeigt die Zeile die Bewegung seit der Meldung\n" +
        "• Viele Zahlen werden später korrigiert\n" +
        "• Die Daten erscheinen einige Tage nach dem Stichtag\n" +
        "• Eine einzelne Woche ist noch kein Trend\n\n" +
        "Die App zeigt und erklärt, sie empfiehlt nichts."
    override fun fiName(schluessel: String) = when (schluessel) {
        "rohoel" -> "Rohöl-Lager USA"
        "benzin" -> "Benzin-Lager USA"
        "destillat" -> "Diesel und Heizöl USA"
        "cushing" -> "Rohöl in Cushing"
        "spr" -> "Staatliche Ölreserve USA"
        "gas_us" -> "Erdgasspeicher USA"
        "gas_eu" -> "Gasspeicher EU"
        "erstantraege" -> "Erstanträge auf Arbeitslosenhilfe USA"
        "fluggaeste" -> "Fluggäste USA"
        "duerre" -> "Dürre USA"
        else -> schluessel
    }
    private fun fiZahlwort(n: Int) = when (n) { 3 -> "drei"; 4 -> "vier"; 5 -> "fünf"; else -> n.toString() }
    private fun fiSchnitt(jahre: Int) = if (jahre == 5) "Fünfjahresschnitt" else "Schnitt der ${fiZahlwort(jahre)} Vorjahre"
    override fun fiLagerSatz(lage: Int, abweichung: String, unter: Boolean, jahre: Int) =
        "$abweichung ${if (unter) "unter" else "über"} dem ${fiSchnitt(jahre)}, " + when (lage) {
            0 -> "knapper als in jedem der ${fiZahlwort(jahre)} Vorjahre"
            1 -> "knapper als üblich"
            3 -> "mehr als üblich"
            4 -> "mehr als in jedem der ${fiZahlwort(jahre)} Vorjahre"
            else -> "im üblichen Bereich"
        }
    override fun fiDuerreSatz(punkte: String, mehr: Boolean, rekord: Boolean, jahre: Int) =
        "$punkte Punkte ${if (mehr) "mehr" else "weniger"} Fläche in Dürre als im ${fiSchnitt(jahre)}" +
            if (rekord) ", so viel wie in keinem der ${fiZahlwort(jahre)} Vorjahre" else ""
    override fun fiAntraegeSatz(anstieg: String, ampel: Int) = "Vierwochenschnitt $anstieg über dem Tief des letzten Jahres" + when (ampel) {
        2 -> ", ein solcher Anstieg ging früher oft einer Rezession voraus"
        1 -> ", der Arbeitsmarkt kühlt ab"
        else -> ""
    }
    override fun fiFlugSatz(veraenderung: String, mehr: Boolean) = "$veraenderung ${if (mehr) "mehr" else "weniger"} Reisende als vor einem Jahr (letzte 28 Tage)"
    override fun fiErklaerung(schluessel: String) = when (schluessel) {
        "rohoel" -> "Rohöl in Lagern der USA ohne die staatliche Reserve, jeden Mittwoch von der EIA. Knappe Lager deuten auf knappes Angebot oder starke Nachfrage und stützen eher den Ölpreis, volle Lager drücken eher."
        "cushing" -> "Lagerort in Oklahoma, an dem der US-Ölkontrakt WTI geliefert wird. Sehr leere Tanks können den nächsten WTI-Kontrakt kurzfristig verteuern, sehr volle ihn drücken wie im April 2020."
        "benzin" -> "Benzinvorräte der USA. Viel Benzin deutet auf schwache Nachfrage oder großes Angebot, wenig Benzin stützt den Preis. Im Sommer wird mehr gefahren und die Lager sinken üblicherweise, deshalb vergleicht die App mit derselben Jahreszeit."
        "destillat" -> "Diesel und Heizöl, wichtig für Lkw, Landwirtschaft und Heizung im Winter. Knappe Bestände verteuern Diesel oft stärker als Rohöl."
        "gas_us" -> "Arbeitsgas in unterirdischen Speichern der USA, jeden Donnerstag von der EIA. Im Sommer wird eingespeichert, im Winter entnommen. Volle Speicher vor dem Winter dämpfen den Preis, leere machen ihn anfällig für Kälte."
        "gas_eu" -> "Füllstand der Gasspeicher in der EU, täglich von GIE AGSI+. Ein niedriger Stand vor dem Winter macht den europäischen Gaspreis (TTF) anfällig. Der läuft oft anders als der US-Preis Henry Hub."
        "erstantraege" -> "Wie viele Menschen in den USA in einer Woche erstmals Arbeitslosenhilfe beantragen, saisonbereinigt, jeden Donnerstag vom US-Arbeitsministerium. Steigt der Vierwochenschnitt deutlich über sein Tief, schwächt sich der Arbeitsmarkt ab. Ab 10 % gilt der Anstieg als deutlich, ab 20 % als Warnzeichen."
        "fluggaeste" -> "Reisende an den Sicherheitskontrollen der US-Flughäfen, täglich von der TSA. Ein Hinweis auf Reiselust und Konsum, wichtig für Fluglinien und Reiseanbieter. Feiertage wie Ostern oder Thanksgiving fallen jedes Jahr anders und verzerren den Vergleich für kurze Zeit."
        "duerre" -> "Anteil der Fläche der USA ohne Alaska und Hawaii mit mindestens mäßiger Dürre, jede Woche vom U.S. Drought Monitor (National Drought Mitigation Center, USDA und NOAA). Dürre in den Anbaugebieten kann Ernten von Mais, Weizen und Soja schmälern. Die Zahl gilt für das ganze Land, nicht nur für Felder."
        else -> ""
    }
    override fun fiQuelle(schluessel: String) = when (schluessel) {
        "gas_eu" -> "Quelle: GIE AGSI+"
        "erstantraege" -> "Quelle: US-Arbeitsministerium über FRED"
        "fluggaeste" -> "Quelle: TSA"
        "duerre" -> "Quelle: U.S. Drought Monitor"
        else -> "Quelle: EIA"
    }
    override val fiQuellen = "Quellen: EIA, US-Arbeitsministerium (FRED), TSA, U.S. Drought Monitor, GIE AGSI+"
    override fun fiStand(datum: String) = "Stand $datum"
    override fun fiWoche(aenderung: String) = "Woche $aenderung"
    override fun fiBereich(von: String, bis: String, jahre: Int) = "${fiZahlwort(jahre).replaceFirstChar { it.uppercase() }} Vorjahre zur selben Zeit: $von bis $bis"
    override fun fiMioBarrel(wert: String) = "$wert Mio. Barrel"
    override fun fiMrdKubikfuss(wert: String) = "$wert Mrd. Kubikfuß"
    override fun fiMioTag(wert: String) = "$wert Mio. am Tag"
    override val fiPunkte = "Punkte"
    override fun fiSpr(wert: String, woche: String?) =
        "Staatliche Ölreserve: $wert" + (woche?.let { ", Woche $it" } ?: "") + ". Sie folgt politischen Entscheidungen und zählt deshalb nicht zur Ampel."
    override val fiOhneZugang = "Mit kostenlosem Zugang zu GIE AGSI+ sichtbar"
    override val zgAgsiName = "GIE AGSI+ (Gasspeicher EU)"
    override val zgAgsiZweck = "Füllstand der Gasspeicher in der EU für den Abschnitt „Lager und Wirtschaftsdaten“ bei den Märkten und auf der Seite von Erdgas."
    override val zgAgsiPunkte = listOf(
        "Kostenlos nach Anmeldung mit E-Mail-Adresse",
        "Der Schlüssel geht nur an agsi.gie.eu",
        "Bedingung: GIE als Quelle nennen, das übernimmt die App",
    )
    override val zgAgsiHolen = "Schlüssel bei GIE AGSI+ holen"
    override val fiAntraegeTief = "Vierwochenschnitt nahe am Tief des letzten Jahres"
    override val fiBrancheTitel = "Daten zur Branche"

    // 5.92 Vermoegen Regierung
    override fun ogeVmTitel(art: String, jahr: Int) = "Vermögen laut " + when (art) {
        "A" -> "Antrittsmeldung"; "E" -> "Abschlussmeldung"; else -> "Jahresmeldung"
    } + if (jahr > 0) " $jahr" else ""
    override val ogeVmHinweis = "Untergrenze aus den Wertspannen der Meldung an die US-Ethikbehörde OGE.\n" +
        "• Das selbst genutzte Haus und staatliche Renten fehlen, Kredite auf dieses Haus sind deshalb nicht abgezogen"
    override fun ogeVmPdf(datum: String) = "Meldung vom $datum"
    // 5.92 Aktienfinder Bilanz
    override val afNurSolide = "Nur solide Bilanz"
    override fun afBilanzStand(n: Int, gesamt: Int) = "Bilanzdaten $n von $gesamt"
    override val afNeuOrdnen = "Neu ordnen"
    override val afBilanzWeiter = "Weiter laden"
    override val afBilanzFolgt = "Bilanzdaten folgen"
    override fun afBilanzZahlen(f: String?, zone: String?) =
        listOfNotNull(f?.let { "Piotroski-F-Score $it von 9" }, zone?.let { "Altman-Z: $it" }).joinToString(" · ")
    // 5.92 Ersatzquellen
    override fun ersSenatIntro(stand: String) = "Geschäfte von US-Senatoren nach dem STOCK Act, gemeldet bis zu 45 Tage danach, der Betrag als Spanne. Die App bringt drei Jahre mit und holt neuere Meldungen aus dem offenen Datensatz Congress Trading Monitor" + (if (stand.isBlank()) "." else ". Neueste Meldung vom $stand.")
    override val ersSenatQuelle = "Neuere Meldungen: Congress Trading Monitor (kadoa-org, MIT-Lizenz), ausgelesen aus denselben Senatsmeldungen."
    override val ersSenatGlocke = "Neue Meldungen des Senats kommen über einen offenen Datensatz, meist wenige Tage nach der Meldung."
    override fun ersBlName(schluessel: String) = when (schluessel) {
        "cape" -> "Shiller-KGV (CAPE)"
        "margin" -> "Aktien auf Kredit"
        else -> null
    }
    override fun ersBlLage(schluessel: String, ampel: Int) = when (schluessel) {
        "cape" -> listOf("Bewertung im üblichen Rahmen", "Höher als im langjährigen Schnitt", "So hoch wie selten seit 1881")
        "margin" -> listOf("Kredite wachsen normal", "Kredite wachsen kräftig", "Kredite wachsen so schnell wie vor früheren Hochs")
        else -> null
    }?.getOrElse(ampel) { "" }
    override fun ersBlErklaerung(schluessel: String, zusatz: String?, stand: String?) = when (schluessel) {
        "cape" -> "Kurs des S&P 500 geteilt durch den inflationsbereinigten Durchschnittsgewinn der letzten zehn Jahre, berechnet von Robert Shiller. Einzelne gute oder schlechte Jahre fallen so weniger ins Gewicht." +
            (zusatz?.let { " Höher als in $it aller Monate seit 1881" + (stand?.let { s -> ", Stand $s." } ?: ".") } ?: "") +
            " Langjähriger Schnitt etwa 17, Ende 1999 gut 44. Ab 22 gelb, ab 30 rot."
        "margin" -> "Kredite der US-Wertpapierhändler an ihre Kunden, vor allem für Aktienkäufe auf Kredit, aus der Finanzierungsrechnung der Fed. Gezeigt ist das Wachstum gegen das Vorjahr" +
            (zusatz?.let { ", zuletzt $it" + (stand?.let { s -> " im $s" } ?: "") } ?: "") +
            ". Die Reihe misst etwas breiter als die Margin-Statistik der FINRA. Vor den Hochs von 2000 und 2021 wuchsen die Kredite um mehr als die Hälfte. Ab 20 % gelb, ab 40 % rot."
        else -> null
    }
    override val ersBlQuelle = "Dazu: Robert Shiller (CAPE), Finanzierungsrechnung der Fed über FRED"
    override val ersTitel = "Fracht, Lieferketten, Halbleiter"
    override val ersInfo = "Monatsdaten aus offenen Quellen. Sie zeigen, ob Waren knapp werden und ob Industrie und Verkehr mehr oder weniger zu tun haben als vor einem Jahr.\n\n" +
        "• Rot: deutlich schwächer als vor einem Jahr oder starker Druck in den Lieferketten\n" +
        "• Gelb: etwas schwächer oder erhöhter Druck\n" +
        "• Grün: unauffällig\n\n" +
        "Die Zahlen erscheinen einige Wochen nach dem Monatsende."
    override fun ersName(schluessel: String) = when (schluessel) {
        "lieferketten" -> "Druck in den Lieferketten"
        "fracht" -> "Güterverkehr USA"
        "bahn" -> "Güterwagen der US-Bahnen"
        "halbleiter" -> "Halbleiterproduktion USA"
        else -> schluessel
    }
    override fun ersSatz(schluessel: String, ampel: Int) = when (schluessel) {
        "lieferketten" -> listOf("Kein besonderer Druck", "Erhöhter Druck", "Starker Druck, Waren werden knapp")
        "fracht" -> listOf("Mindestens so viel Fracht wie vor einem Jahr", "Etwas weniger Fracht als vor einem Jahr", "Deutlich weniger Fracht als vor einem Jahr")
        "bahn" -> listOf("Mindestens so viele wie vor einem Jahr", "Etwas weniger als vor einem Jahr", "Deutlich weniger als vor einem Jahr")
        "halbleiter" -> listOf("Produktion über dem Vorjahr", "Produktion etwas unter dem Vorjahr", "Produktion deutlich unter dem Vorjahr")
        else -> emptyList()
    }.getOrElse(ampel) { "" }
    override fun ersErklaerung(schluessel: String) = when (schluessel) {
        "lieferketten" -> "Global Supply Chain Pressure Index der New York Fed. Er fasst Frachtraten für Schiffe und Luftfracht und Umfragen zu Lieferzeiten und Rückständen aus sieben großen Volkswirtschaften zusammen. 0 ist der langjährige Schnitt, 1 eine Standardabweichung darüber, 2021 lag er bei gut 4. Ab 0,5 gelb, ab 1,5 rot."
        "fracht" -> "Frachtindex des US-Verkehrsministeriums (BTS) für Lkw, Bahn, Binnenschiff, Pipeline und Luftfracht, saisonbereinigt. Er steht hier für die Frachtindizes, deren Daten nicht frei verwendbar sind. Weniger als im Vorjahresmonat gelb, ab 3 % weniger rot."
        "bahn" -> "Beladene Güterwagen der US-Bahnen im Monat, saisonbereinigt, laut BTS. Die Wochenzahlen des Bahnverbands gibt es nur als PDF. Weniger als im Vorjahresmonat gelb, ab 3 % weniger rot."
        "halbleiter" -> "Produktionsindex der Fed für Halbleiter und elektronische Bauteile in den USA. Er steht hier für die weltweiten Umsatzzahlen von SIA und WSTS, die nicht frei verwendbar sind. Weniger als im Vorjahresmonat gelb, ab 3 % weniger rot."
        else -> ""
    }
    override fun ersStand(monat: String) = "Stand: $monat"
    override fun ersVorjahr(wert: String) = "$wert zum Vorjahr"
    override val ersQuellen = "Quellen: New York Fed, BTS und Fed über FRED"
    override fun ersQuelle(schluessel: String, jahr: Int) =
        if (schluessel == "lieferketten") "Quelle: " + "© $jahr Federal Reserve Bank of New York. Content from the New York Fed subject to the Terms of Use at newyorkfed.org." else "Quelle: FRED, St. Louis Fed"
    override val ersHnTitel = "Hacker News"
    override fun ersHnZeile(woche: Int, schnitt: String) = (if (woche == 1) "1 Beitrag" else "$woche Beiträge") + " in 7 Tagen · vorher $schnitt pro Woche"
    override fun ersMastodonTitel(tag: String) = "Mastodon · #$tag"
    override fun ersMastodonZeile(beitraege: Int) = (if (beitraege == 1) "1 Beitrag" else "$beitraege Beiträge") + " in 7 Tagen auf mastodon.social"
    override val ersNetzHinweis = "Gezählt wird der Firmenname. Gleichnamige Wörter zählen mit."
    override fun ersMrdDollar(wert: String) = "$wert Mrd. $"
    override fun ersQuartal(q: Int, jahr: Int) = "$q. Quartal $jahr"
    // 5.92 Eingepreist
    override val epTitel = "Im Preis?"
    override fun epTendenz(richtung: Int, was: String?) = when (richtung) {
        1 -> "Tendenz: eher long"
        -1 -> "Tendenz: eher short"
        else -> "Tendenz: keine klare Richtung"
    } + (if (richtung != 0 && was != null) " bei $was" else "")
    override val epWasAnleihen = "US-Staatsanleihen"
    override val epWasZyklisch = "zyklischen gegen defensive Aktien"
    override val epWasAirlines = "Airline-Aktien"
    override fun epWasKorn(symbol: String) = when (symbol) {
        "ZW=F" -> "Weizen"
        "ZS=F" -> "Soja"
        "TTF=F" -> "Gas"
        else -> "Mais"
    }
    override val epInfo = "„Im Preis?“ fragt, ob der Markt ein Signal schon berücksichtigt hat. Die Tendenz sagt, ob man daraus eher long (auf steigende Kurse) oder eher short (auf fallende Kurse) gehen würde.\n\n" +
        "• Grün: schon im Preis, keine klare Richtung\n" +
        "• Gelb: Der Markt sieht es deutlich anders als das Signal. Die Tendenz zeigt, wohin sich der Preis bewegen müsste, damit beides wieder zusammenpasst\n" +
        "• Rot: wie Gelb, die Lücke ist aber so groß wie selten\n" +
        "• Grau: Früher gab es keinen verlässlichen Zusammenhang, also keine Tendenz\n\n" +
        "Beispiel Öl: Ist das Lager knapp, kostet Öl zur baldigen Lieferung meist mehr als später. Ist dieser Aufschlag größer, als das Lager erklärt, ist Öl eher zu teuer (eher short). Ist er kleiner, ist die Knappheit noch nicht im Preis (eher long).\n\n" +
        "„Unsicher“ heißt: Der Zusammenhang war früher nur schwach."
    override fun epRisiken(mass: String) = "Grenzen:\n" + when (mass) {
        "KURVE" -> "• Die Terminkurve hängt auch an Lagerkosten und Zinsen\n" +
            "• Erwartungen wirken früher als das Lager: OPEC-Beschlüsse, Kriege, Sanktionen oder Wirbelstürme\n" +
            "• Kurz vor dem Verfall schwankt der nächste Kontrakt stärker\n" +
            "• Die Jahreszeit rechnet die App heraus, ungewöhnliches Wetter bleibt\n" +
            "• Die Futures-Reihen der EIA enden im April 2024, die Schätzung endet dort. Die heutige Kurve kommt von Yahoo\n"
        "PREIS" -> "• Der Preis hängt an vielen Dingen: Ernten und Wetter in anderen Ländern, Wechselkurse, Politik\n" +
            "• Kriege, Sanktionen oder Lieferstopps wirken sofort, Lager und Dürre erst später\n" +
            "• Der Vergleich mit dem Jahresschnitt ist grob\n"
        "AIRLINES" -> "• Airline-Aktien hängen auch an Kerosinpreis, Zinsen, Streiks und Übernahmen\n" +
            "• Die Fluggastzahlen gelten nur für die USA\n" +
            "• Geschätzt erst ab Mitte 2022, weil die Jahre davor nur Pandemie und Erholung zeigen. Die Zeit ist kurz\n"
        "BRANCHE" -> "• Branchenaktien hängen an vielem: Zinsen, Gewinnen einzelner Schwergewichte, Übernahmen\n" +
            "• Monatsdaten erscheinen ein bis drei Monate später, der Markt kann schon weiter sein\n" +
            "• Fracht und Chipproduktion gelten nur für die USA, der Lieferkettenindex weltweit\n"
        "SEIT" -> "• Auch andere Nachrichten bewegen den Kurs\n" +
            "• Frühere Fälle beim selben Wert sind oft wenige, der Schnitt schwankt\n" +
            "• Ohne eigene Fälle nutzt die App einen groben Richtwert\n"
        else -> "• Die Zinserwartung hängt vor allem an Inflation und Notenbank\n" +
            "• Erstanträge schwanken von Woche zu Woche, Feiertage und Stürme verzerren\n" +
            "• Ausnahmejahre wie die Pandemie kappt die App, sie prägen die Schätzung trotzdem\n"
    } + "• Ein Zusammenhang der Vergangenheit muss nicht weiter gelten\n\n" +
        "Die Tendenz ist eine Ableitung aus früheren Zusammenhängen und keine Anlageberatung."
    override fun epLagerSatz(urteil: Int, lage: Int, gas: Boolean, kurve: Boolean): String {
        val lager = if (gas) "Speicher" else "Lager"
        return when (urteil) {
            0 -> "Passt: Der Stand der $lager ist schon im Preis."
            1 -> if (lage == 1) "Die $lager sind voll, der Preis rechnet trotzdem mit Knappheit. Eher zu teuer."
                else "Der Preis rechnet mit mehr Knappheit, als die $lager zeigen. Eher zu teuer."
            else -> if (lage == -1) "Die $lager sind knapp, der Preis zeigt das noch nicht. Eher zu billig."
                else "Der Preis rechnet mit mehr Überfluss, als die $lager zeigen. Eher zu billig."
        }
    }
    override fun epDuerreSatz(urteil: Int, viel: Boolean) = when (urteil) {
        0 -> if (viel) "Der Getreidepreis passt zur großen Dürre: eingepreist." else "Getreidepreis und Dürre passen zusammen."
        1 -> "Der Getreidepreis liegt höher, als die Dürre erklärt."
        else -> if (viel) "Die Dürre ist größer, als der Getreidepreis zeigt: noch nicht voll eingepreist." else "Der Getreidepreis liegt tiefer, als die Dürre erwarten lässt."
    }
    override fun epFlugSatz(urteil: Int) = when (urteil) {
        0 -> "Airline-Aktien laufen passend zu den Fluggastzahlen: eingepreist."
        1 -> "Airline-Aktien laufen besser, als die Fluggastzahlen erwarten lassen."
        else -> "Airline-Aktien laufen schwächer, als die Fluggastzahlen erwarten lassen."
    }
    override fun epAntraegeSatz(urteil: Int) = when (urteil) {
        0 -> "Die Zinserwartung passt zu den Erstanträgen: eingepreist."
        1 -> "Die Zinserwartung preist weniger Abschwung ein, als die Erstanträge zeigen."
        else -> "Die Zinserwartung preist mehr Abschwung ein, als die Erstanträge zeigen."
    }
    override fun epZyklischSatz(urteil: Int) = when (urteil) {
        0 -> "Zyklische und defensive Aktien laufen passend zu den Erstanträgen."
        1 -> "Zyklische Aktien laufen besser, als die Erstanträge erwarten lassen."
        else -> "Zyklische Aktien laufen schwächer, als die Erstanträge erwarten lassen."
    }
    override val epKeine = "Keine verlässliche Einordnung möglich, der Zusammenhang war früher zu schwach."
    override val epUnsicher = "Unsicher, der Zusammenhang war früher schwach."
    override fun epKurve(spanne: String, teurer: Boolean) = "Naher Kontrakt $spanne im Jahr ${if (teurer) "teurer" else "billiger"} als der vierte"
    override fun epPreis(abstand: String, ueber: Boolean, name: String) = "$name $abstand ${if (ueber) "über" else "unter"} dem Jahresschnitt"
    override fun epAirlines(abstand: String, besser: Boolean) = "Airline-Aktien im Jahr $abstand ${if (besser) "besser" else "schwächer"} als der S&P 500"
    override fun epZins(abstand: String) = "Zweijährige US-Rendite $abstand Punkte unter ihrem Jahreshoch"
    override fun epZyklisch(abstand: String, besser: Boolean) = "Zyklische Konsumwerte im halben Jahr $abstand ${if (besser) "besser" else "schwächer"} als defensive"
    override fun epHeute(wert: String) = "Heute: $wert"
    override fun epUeblich(wert: String) = "Üblich bei diesem Stand: $wert"
    override fun epSelten(anteil: String, hoeher: Boolean) = "So weit ${if (hoeher) "über" else "unter"} dem Üblichen lag der Markt nur in $anteil der Wochen."
    override fun epStaerke(staerke: Int, anteil: String) = "Zusammenhang früher: " + when (staerke) {
        3 -> "deutlich"
        2 -> "mittel"
        1 -> "schwach"
        else -> "zu schwach"
    } + ", das Signal erklärte $anteil der Schwankung."
    override fun epZeitraum(wochen: Int, von: String, bis: String) = "Geschätzt aus $wochen Wochen, $von bis $bis"
    override val epKurveTitel = "Terminkurve heute"
    override val epZusatzTitel = "Zyklische gegen defensive Aktien"
    override fun epKorn(symbol: String) = when (symbol) {
        "ZW=F" -> "Weizenpreis"
        "ZS=F" -> "Sojapreis"
        "TTF=F" -> "Gaspreis TTF"
        else -> "Maispreis"
    }
    override fun epSeit(art: String, mehr: String, index: String?) = "Seit " + when (art) {
        "INSIDER" -> "dem Insiderkauf"
        "HOCH" -> "der Hochstufung"
        "AB" -> "der Abstufung"
        "MELDUNG" -> "der Meldung"
        else -> "dem Hinweis"
    } + " $mehr" + (index?.let { " gegen den $it" } ?: "")
    override fun epLauf(lauf: Int) = when (lauf) {
        0 -> "Ein Großteil ist wahrscheinlich eingepreist."
        1 -> "Teilweise eingepreist."
        2 -> "Bisher wenig eingepreist."
        3 -> "Der Kurs lief seitdem gegen das Signal."
        else -> "Solche Signale brachten hier früher im Schnitt keinen Vorsprung."
    }
    override fun epFrueher(schnitt: String, n: Int) = "Frühere Signale dieser Art brachten bei diesem Wert in sechs Monaten im Schnitt $schnitt gegen den Index ($n Fälle)."
    override fun epRichtwert(wert: String) = "Für diesen Wert gibt es zu wenige frühere Fälle. Als grober Richtwert gilt ein Vorsprung von $wert."
    override fun epHandelstage(n: Int) = "$n Handelstage seit dem Signal"
    override val epSeitInfo = "Hier zählt, wie viel der Kurs seit dem Signal schon besser oder schlechter lief als sein Markt (Heimatindex).\n\n" +
        "• Grün: Der übliche Vorsprung ist erreicht, das Signal ist eingepreist, keine klare Richtung mehr\n" +
        "• Gelb: Erst ein Teil ist gelaufen. Tendenz in Richtung des Signals: eher long nach Käufen und Hochstufungen, eher short nach Abstufungen\n" +
        "• Rot: Der Kurs lief gegen das Signal, keine klare Richtung\n" +
        "• Grau: Solche Signale brachten bei diesem Wert früher keinen Vorsprung"
    override val epKaeufeTitel = "Im Preis? Seit den Käufen"
    override val epKaeufeHinweis = "Gemessen ab dem Tag der Meldung, als die Käufe öffentlich wurden, gegen den S&P 500. Ab etwa 10 % Vorsprung gilt ein Großteil als eingepreist."
    override fun epKaufZeile(datum: String, mehr: String, index: String) = "Gemeldet $datum · $mehr gegen den $index"
    override val epAnalystenTitel = "Im Preis? Letzte Hoch- oder Abstufung"
    override val epInsiderTitel = "Im Preis? Letzter Insiderkauf"
    // 5.92 Finder-Test
    override val afRueckblickTitel = "Frühere Ergebnisse des Punktesystems"
    override fun afRueckblickText(von: String, bis: String, werte: Int, top20: String, msci: String, treffer: String) =
        "Rückblick $von bis $bis: Jedes Monatsende bewertete die App den S&P 500 nur mit Daten von damals. " +
            "Die 20 Aktien mit den meisten Punkten brachten in den folgenden zwölf Monaten im Schnitt $top20, der MSCI World $msci. Vorn lagen sie an $treffer der Stichtage."
    override fun afRueckblickGrenzen(bilanzAb: String?, ganz: Boolean, ic: Double, icSe: Double, bewertung: Pair<Double, Double>?): String {
        val staerke = when {
            ic - 2 * icSe <= 0.0 -> "nicht sicher von Zufall zu unterscheiden"
            ic < 0.05 -> "schwach"
            else -> "mäßig"
        }
        val geprueft = when {
            bilanzAb == null -> "• Geprüft: Kurstrend und Analysten."
            ganz -> "• Geprüft: Kurstrend, Analysten, Bewertung, Qualität."
            else -> "• Geprüft: Kurstrend und Analysten, Bewertung und Qualität ab $bilanzAb."
        }
        val bew = bewertung?.takeIf { (b, se) -> b - 2 * se <= 0.0 }?.let { (b, _) ->
            "\n• Eine günstige Bewertung ordnete die spätere Rendite in dieser Zeit nicht (Rangkorrelation ${String.format(Locale.GERMANY, "%.2f", b)})."
        } ?: ""
        return "Grenzen\n" + geprueft + bew + "\n" +
            "• Insider, Leerverkäufe und Stimmung fehlen früher und zählten nicht.\n" +
            "• Die Punkte ordnen die spätere Rendite $staerke (Rangkorrelation ${String.format(Locale.GERMANY, "%.2f", ic)}).\n" +
            "• Fehlende Firmen schönen das Ergebnis. Kurse ohne Dividenden."
    }
    // 5.92 Weitere Quellen
    override fun wqName(k: String) = when (k) {
        "wci" -> "Containerfracht"
        "aar" -> "US-Bahnen, Woche"
        else -> "Halbleiterumsatz weltweit"
    }
    override fun wqSatz(k: String, ampel: Int, steigt: Boolean) = when (k) {
        "wci" -> listOf(
            "Frachtraten kaum verändert",
            if (steigt) "Frachtraten steigen deutlich" else "Frachtraten fallen deutlich",
            if (steigt) "Frachtraten springen nach oben" else "Frachtraten brechen ein",
        )
        "aar" -> listOf("Mindestens so viel Verkehr wie vor einem Jahr", "Etwas weniger Verkehr als vor einem Jahr", "Deutlich weniger Verkehr als vor einem Jahr")
        else -> listOf("Umsatz über dem Vorjahresmonat", "Umsatz etwas unter dem Vorjahresmonat", "Umsatz deutlich unter dem Vorjahresmonat")
    }.getOrElse(ampel) { "" }
    override fun wqErklaerung(k: String) = when (k) {
        "wci" -> "World Container Index von Drewry: Preis für einen 40-Fuß-Container auf acht großen Routen ab Asien, Europa und den USA, jeden Donnerstag neu. Starke Sprünge zeigen Engpässe auf See, starke Einbrüche oft eine schwache Nachfrage. Ab 5 % zur Vorwoche gelb, ab 15 % rot."
        "aar" -> "Wochenzahlen des Verbands der US-Bahnen (AAR): beladene Güterwagen sowie Container und Auflieger, verglichen mit derselben Woche im Vorjahr. Sie erscheinen jeden Mittwoch und zeigen früher als die Monatszahlen, wie viel Ware unterwegs ist. Weniger als im Vorjahr gelb, ab 3 % weniger rot."
        else -> "Weltweiter Umsatz der Halbleiterhersteller im Monat laut WSTS, der Statistikstelle der Branche, verglichen mit dem Vorjahresmonat. Die Zahlen erscheinen etwa zwei Monate später. Weniger als im Vorjahr gelb, ab 3 % weniger rot."
    }
    override fun wqQuelle(k: String) = when (k) {
        "wci" -> "Quelle: Drewry"
        "aar" -> "Quelle: Association of American Railroads"
        else -> "Quelle: WSTS"
    }
    override fun wqWoche(wert: String) = "Zur Vorwoche $wert"
    override fun wqBahnTeile(wagen: String?, container: String?) =
        listOfNotNull(wagen?.let { "Güterwagen $it" }, container?.let { "Container und Auflieger $it" }).joinToString(" · ")
    override fun wqWocheBis(tag: String) = "Woche bis $tag"
    override fun wqStandTag(tag: String) = "Stand: $tag"
    override fun wqEinheiten(n: String) = "$n Wagen und Container"
    override val wqFbxLink = "Freightos Baltic Index (FBX) im Browser"
    override val wqBalticLink = "Baltic Dry Index im Browser"
    override val wqLmeLink = "Metalllager der LME im Browser"
    override val wqComexLink = "Metalllager der COMEX im Browser"
    override val wqFinraLink = "Margin-Statistik der FINRA im Browser"
    override fun wqBrowserText(land: String) = (if (land == "GB") "Die Meldungen der Finanzaufsicht" else "Die Meldungen der Börse") + " lassen sich im Browser ansehen."
    override fun wqBrowserLink(land: String) = "Meldungen bei " + when (land) {
        "IL" -> "MAYA (Börse Tel Aviv)"
        "ID" -> "der Börse Indonesien (IDX)"
        "BH" -> "der Bahrain Bourse"
        "JM" -> "der Jamaica Stock Exchange"
        "TT" -> "der Börse Trinidad und Tobago"
        "PK" -> "der Pakistan Stock Exchange"
        "HK" -> "der HKEX (Disclosure of Interests)"
        "GB" -> "der FCA (National Storage Mechanism)"
        "DK", "FI", "IS", "EE", "LV", "LT" -> "Nasdaq Nordic und Baltic"
        else -> land
    } + " ansehen"
    override fun wqKwQuelle(stand: String) = "Quelle: Boursa Kuwait (RSS-Dienst und Meldeformulare), dazu selbst importierte Listen" + if (stand.isNotBlank()) ", Stand $stand" else ""
    override val wqRichterTitel = "US-Bundesrichter"
    override val wqRichterIntro = "Bundesrichter melden einmal im Jahr ihre Anlagen und Geschäfte, die Beträge nur als Spanne. Gezeigt sind die Geschäfte aus den Erklärungen, die CourtListener zuletzt erfasst hat."
    override val wqRichterOhneZugang = "Mit kostenlosem CourtListener-Token unter Zugänge sichtbar"
    override val wqRichterLeer = "In den zuletzt erfassten Erklärungen steht kein Geschäft."
    override val wqRichterLaedt = "Erklärungen werden geladen"
    override val wqRichterName = "Name folgt beim nächsten Laden"
    override fun wqRichterZeile(jahr: Int, tag: String) = listOfNotNull(if (jahr > 0) "Erklärung $jahr" else null, tag.ifBlank { null }).joinToString(" · ")
    override fun wqSpanne(von: String, bis: String?) = if (bis == null) "über $von" else "$von bis $bis"
    override val wqRichterQuelle = "Quelle: CourtListener (Free Law Project)"
    override val wqJefsLink = "Alle Erklärungen bei JEFS im Browser"
    override val wqParlamenteTitel = "Weitere Parlamente"
    override val wqParlamenteText = "Diese Register öffnen sich im Browser."
    override val wqEuLink = "EU-Abgeordnete: Erklärungen über finanzielle Interessen"
    override val wqAusHausLink = "Australien: Register des Repräsentantenhauses"
    override val wqAusSenatLink = "Australien: Interessen der Senatoren"
    override val wqBskyTitel = "Bluesky"
    override fun wqBskyZeile(woche: Int, vorwoche: Int, gekappt: Boolean) =
        (if (gekappt) "Mehr als $woche" else "$woche") + (if (woche == 1 && !gekappt) " Beitrag" else " Beiträge") + " in 7 Tagen · Vorwoche $vorwoche"
    override val wqXLink = "Auf X ansehen"
    override val wqStocktwitsLink = "Auf Stocktwits ansehen"
    override val wqClName = "CourtListener (US-Bundesrichter)"
    override val wqClZweck = "Aktiengeschäfte von US-Bundesrichtern aus ihren jährlichen Finanzerklärungen, unter Copytrading bei Politik."
    override val wqClPunkte = listOf(
        "Kostenloses Konto beim Free Law Project, der Token steht danach in deinem Profil",
        "Nur für persönliche, nicht kommerzielle Nutzung",
        "Ohne Mitgliedschaft 125 Abrufe am Tag, die App nutzt höchstens 100",
        "Der Token geht nur an courtlistener.com",
    )
    override val wqClFeld = "Token"
    override val wqClHolen = "Token bei CourtListener holen"
    override val wqBskyZweck = "Wie oft ein Unternehmen auf Bluesky erwähnt wird, unter Trends auf der Seite einer Aktie."
    override val wqBskyPunkte = listOf(
        "Bluesky lässt die Suche nur angemeldet zu",
        "Lege dafür ein App-Passwort an, nie das Hauptpasswort eingeben",
        "Die App nimmt nur Passwörter der Form xxxx-xxxx-xxxx-xxxx an",
        "Sie liest nur und schreibt nichts in deinem Namen",
        "Handle und App-Passwort gehen nur an Bluesky",
    )
    override val wqBskyHandle = "Handle, etwa name.bsky.social"
    override val wqBskyPasswort = "App-Passwort"
    override val wqBskyUngueltig = "Nur ein App-Passwort der Form xxxx-xxxx-xxxx-xxxx"
    override val wqBskyHandleUngueltig = "Handle ohne @, etwa name.bsky.social"
    override val wqBskyHolen = "App-Passwort bei Bluesky anlegen"
    override fun wqMeldung(m: String) = when (m) {
        "abgelehnt" -> "Der Dienst hat den Zugang abgelehnt. Bitte die Angaben prüfen."
        else -> "Das Limit ist erreicht. Die App fragt wieder, sobald das Kontingent es erlaubt."
    }
    override fun wqVerbrauch(verbraucht: String, erlaubt: String) = "Abrufe in den letzten 24 Stunden: $verbraucht von $erlaubt"
    // 5.92 Tendenz-Rueckblick
    override fun trFrueher(rot: Boolean, treffer: String, grund: String, wochen: Int, mittel: String, faelle: Int) =
        "Früher${if (rot) " bei Rot" else ""}: traf in $treffer (sonst $grund), am besten ${trNach(wochen)}, $mittel im Schnitt, $faelle Fälle"
    override val trOhneVorteil = "Früher ohne Vorteil: Wer der Tendenz folgte, lag nicht öfter richtig als sonst."
    override fun trNichtGeprueft(zuKurz: Boolean) =
        if (zuKurz) "Früher nicht prüfbar, die Daten reichen noch nicht fünf Jahre zurück." else "Früher nicht geprüft."
    override fun trNach(wochen: Int) = when (wochen) {
        1 -> "nach einer Woche"
        4 -> "nach einem Monat"
        13 -> "nach 3 Monaten"
        26 -> "nach 6 Monaten"
        52 -> "nach einem Jahr"
        else -> "nach $wochen Wochen"
    }
    override val trDetailTitel = "Rückblick: So traf die Tendenz früher"
    override fun trDetailZeile(wochen: Int, treffer: String, grund: String, mittel: String, t: String, faelle: Int, belegt: Boolean) =
        trNach(wochen).replaceFirstChar { it.uppercase() } + ": $treffer Treffer (sonst $grund), $mittel, t $t, $faelle Fälle" + if (belegt) ", belegt" else ""
    override fun trGeprueft(von: String, bis: String) = "Geprüft Woche für Woche von $von bis $bis, jeweils nur mit den Daten von damals."
    override fun trPunkte(wert: String) = "$wert Punkte"
    override fun trIdeeTitel(call: Boolean, name: String) = "Übungsidee: ${if (call) "Call" else "Put"} auf $name"
    override fun trIdeeDaten(basis: String, verfall: String, hebel: String) = "Basispreis $basis · Verfall $verfall · Hebel etwa $hebel"
    override fun trIdeeRisiko(zeitwert: String) =
        "Verlieren kann man höchstens den Kaufpreis. Bewegt sich der Kurs nicht, verliert der Schein etwa $zeitwert pro Woche an Zeitwert."
    override val trIdeeHinweis = "Übungsvorschlag aus früheren Zusammenhängen, keine Anlageberatung. Ein Tipp öffnet den Schein im Übungsdepot."
    override fun trBasisName(symbol: String) = when (symbol) {
        "CL=F" -> "WTI-Öl"
        "RB=F" -> "Benzin (RBOB)"
        "HO=F" -> "Heizöl und Diesel"
        "NG=F" -> "US-Erdgas"
        "ZC=F" -> "Mais"
        "ZW=F" -> "Weizen"
        "ZS=F" -> "Soja"
        "SHY" -> "kurze US-Anleihen (SHY)"
        "XLY" -> "zyklische Konsumwerte (XLY)"
        "XLP" -> "defensive Konsumwerte (XLP)"
        "JETS" -> "Airline-Aktien (JETS)"
        else -> symbol
    }
    override val trInfo = "Rückblick: Die App hat die Tendenz für jede Woche der Vergangenheit mit den Daten von damals neu berechnet und gemessen, ob der Preis danach in diese Richtung lief.\n\n" +
        "• „Traf in 61 % (sonst 50 %)“ heißt: In 61 von 100 Fällen lief der Preis in Richtung der Tendenz, an beliebigen Tagen in 50\n" +
        "• Belegt ist eine Tendenz nur mit mindestens 20 Fällen, die sich zeitlich nicht überschneiden, und mit einem Vorsprung, der kaum Zufall sein kann\n" +
        "• „Früher ohne Vorteil“ heißt: Wer der Tendenz folgte, schnitt nicht besser ab. Die Richtung steht dann grau da\n\n" +
        "Märkte bleiben oft lange anders, als die Daten nahelegen.\n\n" +
        "Einen Übungsvorschlag mit Optionsschein zeigt die App nur bei belegter Tendenz, mit einer Laufzeit etwa anderthalbmal so lang wie der Zeitraum, in dem sie traf."

    // 5.92 Investorenbriefe
    override val ibTitel = "Was Investoren schreiben"
    override val ibKopf = "Die Einordnung zeigt das Eigeninteresse der Absender, soweit es bekannt ist."
    override fun ibZusammenfassung(absender: Int, positiv: Int, negativ: Int, neutral: Int) =
        if (absender == 1) "Ein Absender schrieb zuletzt über die Aktie, " + ibRichtung(if (positiv > 0) 1 else if (negativ > 0) -1 else 0) + " (Richtung grob geschätzt)."
        else "$absender Absender schrieben zuletzt über die Aktie: $positiv eher positiv, $negativ eher negativ, $neutral neutral (Richtung grob geschätzt)."
    override fun ibEigeneSache(n: Int) = if (n == 1) "Einer davon schreibt über eine eigene Position." else "$n davon schreiben über eine eigene Position."
    override fun ibRichtung(r: Int) = when {
        r > 0 -> "eher positiv"
        r < 0 -> "eher negativ"
        else -> "neutral"
    }
    override fun ibArt(art: String) = when (art) {
        "HEDGEFONDS" -> "Hedgefonds"
        "FONDSGESELLSCHAFT" -> "Fondsgesellschaft"
        "VERMOEGENSVERWALTER" -> "Vermögensverwalter"
        "HOLDING" -> "Beteiligungsholding"
        else -> art
    }
    override val ibSecBericht = "Fondsbericht bei der SEC"
    override fun ibBeleg(beleg: String, stand: String) = when (beleg) {
        "FIRMA_SELBST" -> "Brief der Firma an die eigenen Aktionäre"
        "F13_HAELT" -> "Hält die Aktie (laut 13F, Stand $stand)"
        "F13_NEU" -> "Laut 13F neu gekauft (Stand $stand)"
        "F13_ERHOEHT" -> "Laut 13F aufgestockt (Stand $stand)"
        "F13_VERRINGERT" -> "Laut 13F verringert (Stand $stand)"
        "F13_VERKAUFT" -> "Laut 13F ganz verkauft (Stand $stand)"
        "F13_KEINE" -> "Kein 13F-Bestand bekannt (Stand $stand)"
        "SCHEDULE13" -> "Großaktionär laut Meldung bei der SEC (13D/13G)"
        "BRIEF_KAUF" -> "Nach eigener Angabe gekauft oder aufgestockt"
        "BRIEF_HAELT" -> "Nach eigener Angabe investiert"
        "BRIEF_VERRINGERT" -> "Nach eigener Angabe verringert"
        "BRIEF_VERKAUF" -> "Nach eigener Angabe verkauft"
        "BRIEF_SHORT" -> "Nach eigener Angabe short"
        "BESTANDSLISTE" -> "Steht in einer Bestandsliste im selben Dokument"
        else -> "Keine Position bekannt"
    }
    override fun ibPasst(passt: String) = when (passt) {
        "EIGENE_POSITIV" -> "Schreibt positiv über eine eigene Position"
        "EIGENE_KRITISCH" -> "Kritisch trotz eigener Position"
        "SHORT_NEGATIV" -> "Schreibt negativ über eine eigene Short-Position"
        "SHORT_POSITIV" -> "Positiv trotz eigener Short-Position"
        "NACH_VERKAUF_KRITISCH" -> "Kritisch nach eigenem Verkauf"
        "POSITIV_TROTZ_VERKAUF" -> "Positiv, obwohl selbst verkauft"
        else -> "Die Firma schreibt über sich selbst"
    }
    override val ibWerbung = "Der Brief wirbt für das eigene Angebot"
    override fun ibQuelle(wo: String) = "Quelle: $wo"
    override val ibLeer = "In den Briefen, die die App liest, kommt die Aktie gerade nicht vor."
    override val ibSuchen = "Weitere Briefe zur Aktie suchen (hfbestideas.com)"
    override val ibSecHinweis = "Mit einer Kontaktadresse unter Zugänge sucht die App auch in Fondsberichten bei der SEC."

    // 5.92 Textrunde
    override val indFussTitel = "Lesart und Aussagekraft"

    // 5.93 depotverlauf
    override val pvYtd = "YTD"
    override fun ptGapBeats(v: String) = "$v Pp. vor dem Index"
    override fun ptGapTrails(v: String) = "$v Pp. hinter dem Index"
    // 5.93 briefe
    override val ibInfoKurz = "Auszüge aus Briefen von Fondsmanagern an ihre Anleger, mit Richtung und Einordnung.\n\n• Wer eine Aktie hält, verdient, wenn andere sie kaufen\n• Manager loben gern, was sie besitzen\n• Die Richtung ist grob aus Wörtern geschätzt\n\nKeine Anlageberatung."
    override val ibMehrBrief = "Mehr aus dem Brief"

    // 5.93 nachrichten2
    override val rbMitteilung = "Mitteilung zum Rückblick"
    override val rbMitteilungHinweis = "Ab Werk aus. Die Rückblicke werden trotzdem erstellt und stehen unter News."
    override val notifWerkHinweis = "Ab Werk an: Orders, Sparpläne, Preisalarme, Fälligkeiten, Dividenden, Zinsen und wichtige Schlagzeilen. Aus sind die Glocken im Copytrading und die Mitteilung zum Tagesrückblick. Beides lässt sich einzeln einschalten."
    override val copyBellsWerk = "Ab Werk hat niemand eine Glocke. Du bekommst nur Mitteilungen von Personen, bei denen du sie selbst einschaltest."
    // 5.93 strategielabor
    override val slEigen = "Eigener"
    override val slEigenHinweis = "Wähle Start und Ende. Gerechnet wird nur in diesem Zeitraum, die Kennzahlen der Regeln laufen aber schon vorher an."
    override fun slLotterieErgebnis(pruefBesser: Boolean, echt: Boolean, anzahl: Int, prozent: String) = when {
        pruefBesser && echt -> "Die beste von $anzahl Varianten schlägt auch im Prüfzeitraum Kaufen und Halten. Mit $prozent spricht viel dafür, dass es kein Zufall ist."
        pruefBesser -> "Die beste von $anzahl Varianten schlägt im Prüfzeitraum Kaufen und Halten. Mit nur $prozent kann das auch Zufall sein."
        echt -> "Die beste von $anzahl Varianten hebt sich mit $prozent vom Zufall ab, schlägt im Prüfzeitraum aber Kaufen und Halten nicht."
        else -> "Die beste von $anzahl Varianten verliert im Prüfzeitraum gegen Kaufen und Halten. Mit $prozent spricht nichts dafür, dass sie mehr kann als Glück."
    }
    // 5.93 hintergrund
    override val rrHintMax = "Tiefster Stand unter einem früheren Höchststand."
    override val rrHintErholung = "Zeit vom Tiefpunkt, bis der alte Höchststand wieder erreicht war."
    override val rrHintLaengster = "Längste Zeit, in der der Kurs unter einem früheren Höchststand blieb."
    override val rrHintRendite = "Gewinn oder Verlust, auf ein Jahr umgerechnet, ohne Dividenden."
    override val rrHintBeta = "Zeigt, wie stark der Wert im Mittel mitgeht, wenn sich der Index bewegt. 1 heißt gleich stark, über 1 stärker, unter 1 schwächer."
    override val rrHintKorrelation = "Zeigt, wie ähnlich sich Wert und Index bewegen. 1 heißt im Gleichschritt, 0 unabhängig, −1 gegenläufig."
    override val lvKurz = "Leerverkäufer leihen sich Aktien und verkaufen sie in der Hoffnung, sie billiger zurückzukaufen. Ein hoher Anteil zeigt, dass viele auf fallende Kurse setzen."
    // 5.93 finder-radar
    override val afAnsichtListe = "Liste"
    override val afAnsichtRadar = "Radar"
    override val afImGespraech = "Im Gespräch"
    override val afRadarAlle = "Alle Hinweise"
    override val afRadarErwaehnungen = "Erwähnungen"
    override val afRadarWikipedia = "Wikipedia"
    override fun afRadarStaerke(n: String) = "Hinweisstärke $n"
    override val afRadarHinweis = "Der Kreis zeigt die Punkte des Aktienfinders, ein Strich heißt noch nicht bewertet. Aufmerksamkeit erhöht diese Punkte nicht, sie wird nur angezeigt."
    // 5.93 stimmung
    override val sbWsbUnklar = "Ton unklar"
    override val sbWsbNachFall = "Nach Kursrückgang besprochen"
    override val sbWsbNachAnstieg = "Nach Kursanstieg besprochen"
    override fun sbWsbAnlass(ticker: String, aenderung: String) = "Viel diskutiert bei starker Kursbewegung: $ticker $aenderung in 5 Handelstagen."
    override fun sbWsbUnklarMehr(prozent: Int) = "Nur $prozent % der Kommentare haben einen klaren Ton. Darum gibt es keinen Wert für positiv oder negativ."
    override val sbWsbOffen = "Ohne klaren Ton"
    override val sbVix = "Schwankung (VIX)"
    override val sbQuelleVix = "Quelle: Yahoo Finance"
    override fun tdText(richtung: Int, ziel: String) = listOf("Bullish", "Neutral", "Bearish").getOrElse(richtung) { "" } + " für " + when (ziel) {
        "aktien" -> "Aktien"; "aktien_eu" -> "europäische Aktien"; "anleihen" -> "Anleihen"; "oel" -> "den Ölpreis"
        "benzin" -> "den Benzinpreis"; "diesel" -> "den Dieselpreis"; "gas_us" -> "den US-Gaspreis"; "gas_eu" -> "den Gaspreis in Europa"
        "fluglinien" -> "Fluglinien"; "getreide" -> "Getreidepreise"; "gold" -> "Gold"; "chips" -> "Chipwerte"
        "transport" -> "Transportwerte"; "reedereien" -> "Reedereien"; else -> ziel
    }
    override fun sbVixMehr(vix: String) = "Der VIX misst die erwartete Schwankung des S&P 500 und liegt bei $vix. Der Wert 100 heißt: so ruhig wie nie im letzten Jahr. Der Wert 0 heißt: so unruhig wie nie."

    // 5.93 ipo
    override val secIpoTitel = "Neue Meldungen bei der SEC"
    override val secIpoKontakt = "Mit einer Kontaktadresse unter Zugänge zeigt die App hier neue Börsenprospekte und Preisfestsetzungen der SEC."
    override val secIpoLeer = "In den letzten Handelstagen gibt es keine passenden Meldungen."
    override val secIpoProspekt = "Prospekt eingereicht"
    override val secIpoAenderung = "Prospekt geändert"
    override val secIpoPreis = "Preis festgesetzt"
    override val secIpoHinweis = "Quelle: SEC, Tagesindex der letzten fünf Handelstage. Formular 424B4 betrifft auch andere Wertpapierangebote, nicht nur Börsengänge."

    // 5.93 finder-punkte
    override val afHaltenTitel = "Lange halten"
    override fun afHaltenSeit(von: String, jahre: String, top20: String, msci: String, spx: String) =
    "Top 20 von $von bis heute gehalten ($jahre Jahre): $top20. MSCI World $msci, S&P 500 $spx."
    override fun afHaltenZeile(jahre: String, top20: String, msci: String, spx: String) =
    "$jahre Jahre halten, Schnitt aller Stichtage: Top 20 $top20, MSCI World $msci, S&P 500 $spx."
    override fun afHaltenHinweis(fehlt: String) =
    "Gleich gewichtet gekauft, nie umgeschichtet, Kurse ohne Dividenden. Es fehlen im Schnitt $fehlt der damaligen Mitglieder, das schönt das Ergebnis."

    // 5.93 kennzahlen
    override val bkKursCashflow = "Kurs/Cashflow"
    override val bkZinsdeckung = "Zinsdeckung"
    override val bkRoic = "ROIC"
    override val bkOwnerEarnings = "Owner Earnings"

    // 5.93 halter-gewinne
    override val hlTitel = "Größte institutionelle Halter"
    override val hlInstitutionen = "Institutionen gesamt"
    override val hlInsider = "Insider gesamt"
    override fun hlAnzahl(n: Int) = "$n Institute melden Bestände"
    override val hlGroesste = "Die zehn größten"
    override fun hlStand(datum: String) = "Stand der Meldungen: $datum"
    override val hlHinweis = "Institute melden ihre Bestände jedes Quartal, in den USA mit Form 13F bis zu 45 Tage nach Quartalsende, die Zahlen sind daher älter als der Kurs. Der Pfeil zeigt die Veränderung der Stückzahl seit dem Vorquartal."
    override fun zuNaechster(datum: String) = "Nächste Zahlen: $datum"
    // 5.93 maerkte
    // 5.93 Maerkte: Deutungen
    override val maerkteBewegungenDeutung = "Große Tagesbewegungen zeigen, wo gerade Nachrichten oder starke Nachfrage wirken. Ein Sprung sagt nichts über die weitere Richtung."
    override val meKernTitel = "Markterwartungen"
    override fun meKern(zins: Int?, rezession: Int?) = listOfNotNull(
        when (zins) {
            -1 -> "Der Markt erwartet in den nächsten zwölf Monaten Zinssenkungen der Fed."
            0 -> "Der Markt erwartet in den nächsten zwölf Monaten kaum veränderte Zinsen der Fed."
            1 -> "Der Markt erwartet in den nächsten zwölf Monaten Zinserhöhungen der Fed."
            else -> null
        },
        when (rezession) {
            0 -> "Eine Rezession gilt bei Polymarket als unwahrscheinlich."
            1 -> "Eine Rezession halten die Wettenden für möglich."
            2 -> "Eine Rezession halten die Wettenden eher für wahrscheinlich."
            else -> null
        },
    ).joinToString(" ")
    override fun meFalls(schluessel: String) = when (schluessel) {
        "fed" -> "Falls es eintritt: Niedrigere Zinsen stützen meist Aktien und Anleihen, höhere belasten sie, vor allem Wachstumswerte."
        "fed_jahr" -> "Falls es eintritt: Mehr Senkungen entlasten meist Kredite und Wachstumsaktien, der Dollar gibt oft nach."
        "ezb" -> "Falls es eintritt: Niedrigere Zinsen im Euroraum schwächen meist den Euro und helfen Aktien, höhere belasten sie."
        "rezession" -> "Falls es eintritt: Unternehmensgewinne sinken meist, Aktien fallen oft, sichere Anleihen und Gold sind dann gefragter."
        "inflation" -> "Falls es eintritt: Hohe Inflation hält die Zinsen länger hoch, das belastet meist Aktien und Anleihen."
        "oel" -> "Falls es eintritt: Ein hoher Ölpreis verteuert Benzin und Transport, hilft Ölfirmen und belastet Fluglinien."
        "hormus" -> "Falls es eintritt: Die Meerenge ist wichtig für den Öltransport. Eine Sperre treibt meist den Ölpreis und verunsichert Börsen."
        "iran" -> "Falls es eintritt: Spannungen im Nahen Osten treiben meist Öl und Gold und drücken oft Aktien."
        "wahlen" -> "Falls es eintritt: Neue Mehrheiten ändern oft Steuern, Zölle und Regeln, einzelne Branchen reagieren darauf."
        else -> ""
    }
    override fun fiDeutung(schluessel: String) = when (schluessel) {
        "rohoel" -> "Hoch: reichlich Öl, das drückt eher den Preis. Niedrig: knappes Angebot, das stützt eher den Preis."
        "cushing" -> "Hoch: viel Öl am Lieferort von WTI, das drückt kurzfristig. Niedrig: knapp, das kann WTI verteuern."
        "benzin" -> "Hoch: viel Benzin, die Nachfrage ist schwach oder das Angebot groß. Niedrig: Benzin wird knapper, das stützt den Preis."
        "destillat" -> "Hoch: reichlich Diesel und Heizöl, das drückt eher den Preis. Niedrig: knapper, das stützt eher."
        "spr" -> "Hoch: großer Puffer, der Staat kann bei Engpässen einspringen. Niedrig: weniger Puffer."
        "gas_us" -> "Hoch: volle Lager, das drückt eher den Gaspreis. Niedrig: knapp, das stützt eher."
        "gas_eu" -> "Hoch: gut für den Winter gefüllt, das beruhigt den Gaspreis. Niedrig: Engpässe drohen, das treibt eher den Preis."
        "erstantraege" -> "Hoch: mehr Menschen melden sich arbeitslos, ein Warnzeichen für die Wirtschaft. Niedrig: der Arbeitsmarkt ist stabil."
        "fluggaeste" -> "Hoch: viele Reisende, gut für Fluglinien. Niedrig: schwächere Nachfrage nach Reisen."
        "duerre" -> "Hoch: viel Land in Dürre, Ernten leiden und Getreidepreise können steigen. Niedrig: gute Bedingungen für die Ernte."
        else -> ""
    }

    // 5.93 stimmung2
    override val sbSicher = "Sichere Häfen (USA)"
    override val sbQuelleSicher = "Quelle: Yahoo Finance, eigene Rechnung"
    override fun sbSicherMehr(unterschied: String) = "Rendite des S&P 500 minus Rendite von US-Staatsanleihen (ETF IEF) über 20 Handelstage: $unterschied. Plus heißt: Aktien liefen besser, sichere Anlagen waren weniger gefragt. Die Anzeige ordnet den Wert im Vergleich zum letzten Jahr ein."

    // 5.93 selbstabruf
    override fun saWartet(ordner: String) = "Die Seite ist im Browser geöffnet. Lade dort die Datei. Neue Dateien im Ordner $ordner übernimmt die App automatisch, solange dieser Bildschirm offen ist (bis 15 Minuten)."
    override val saSeiteUebernehmen = "Seite übernehmen"
    override val saFehler = "Die Datei konnte nicht geladen werden."

    // 5.93 nacharbeit
    override fun trRangAenderung(plaetze: Int, hoeher: Boolean) =
        if (hoeher) (if (plaetze == 1) "1 Platz höher als vor 24 Stunden" else "$plaetze Plätze höher als vor 24 Stunden") else (if (plaetze == 1) "1 Platz tiefer als vor 24 Stunden" else "$plaetze Plätze tiefer als vor 24 Stunden")

    // 5.94
    override val anHochgestuft = "hochgestuft"
    override val anAbgestuft = "abgestuft"
    override fun lvStufe(stufe: Int) = when (stufe) { 0 -> "Wenige Wetten auf fallende Kurse"; 1 -> "Einige Wetten auf fallende Kurse"; 2 -> "Viele Wetten auf fallende Kurse"; else -> "Sehr viele Wetten auf fallende Kurse" }
    override fun trHeuteKurz(rang: Int, veraenderung: String?) = "Heute Platz $rang" + (veraenderung?.let { " · $it zum Vortag" } ?: " · neu dabei")
    override fun trSummenKurz(woche: String, monat: String?, jahr: String, seit: String?, spitze: Pair<String, String>?) = listOfNotNull("7 Tage $woche", monat?.let { "30 Tage $it" }, if (seit != null) "seit $seit $jahr" else "Jahr $jahr", spitze?.let { "Spitze ${it.first} am ${it.second}" }).joinToString(" · ")
    override fun ersMastodonKurz(beitraege: Int) = (if (beitraege == 1) "1 Beitrag" else "$beitraege Beiträge") + " in 7 Tagen"
    override val nwEinordnung = "Einordnung nach Stichworten"
    override val nwZurAktie = "Zur Aktie"
    override val ampelGut = "Gut"
    override val ampelAchtung = "Achtung"
    override val ampelKritisch = "Kritisch"
    override val ampelHinweis = "Hinweis"
    override val wlSortMarktwert = "Nach Marktkapitalisierung"
    override fun epWasBranche(markt: String) = if (markt == "SOXX") "Chipwerte" else "Transportwerte"
    override fun epBranche(markt: String, abstand: String, besser: Boolean) = (if (markt == "SOXX") "Chipwerte" else "US-Transportwerte") + " im Jahr $abstand ${if (besser) "besser" else "schwächer"} als der S&P 500"
    override fun epErsatzSatz(schluessel: String, markt: String, urteil: Int): String {
        val werte = if (markt == "SOXX") "Chipwerte" else "Transportwerte"
        val signal = when (schluessel) {
            "lieferketten" -> "der Lieferkettendruck"
            "fracht" -> "das Frachtaufkommen"
            else -> "die Chipproduktion"
        }
        return when (urteil) {
            0 -> "$werte laufen passend dazu, was ${signal} zeigt: eingepreist."
            1 -> "$werte laufen besser, als ${signal} erwarten lässt."
            else -> "$werte laufen schwächer, als ${signal} erwarten lässt."
        }
    }
    override fun ipoReiter(reiter: Int) = listOf("Demnächst", "Eingereicht", "Neu gelistet", "Zurückgezogen", "Bekannte").getOrElse(reiter) { "" }
    override fun ipoStatusTag(status: Int, datum: String) = listOf("Erwartet", "Prospekt", "Erstnotiz", "Zurückgezogen").getOrElse(status) { "" } + " " + datum
    override val ipoReiterLeer = "Zurzeit gibt es hier keine Einträge."
    override val ipoEckdaten = "Eckdaten"
    override val ipoPreis = "Preis je Aktie"
    override val ipoAktien = "Angebotene Aktien"
    override val ipoVolumen = "Volumen"
    override val ipoEinschaetzung = "Einschätzung"
    override fun ipoPunkt(merkmal: Int, ampel: Int, wert: String?): String = when (merkmal) {
        0 -> "Mantelgesellschaft (SPAC): Sie sammelt Geld, um später eine noch unbekannte Firma zu kaufen. Viele SPAC-Aktien fielen nach der Übernahme deutlich."
        else -> "Kleines Angebot ($wert). Solche Werte finden wenig Beachtung und schwanken oft stark."
    }
    override val ipoRitter = "Die Ampeln erklären Risikomerkmale und sind keine Kaufempfehlung. Streubesitz, Haltefrist und Verkäufe der Alteigentümer stehen im Prospekt. Studien von Jay Ritter (University of Florida) zeigen, dass Börsengänge über drei bis fünf Jahre im Schnitt schwächer liefen als vergleichbare notierte Aktien."
    override val ipoBrancheTitel = "Branche und Ausblick"
    override fun ipoBranche(b: Int) = listOf("Künstliche Intelligenz", "Software und Technik", "Biotech und Pharma", "Bank", "Energie", "Konsum", "Mantelgesellschaft (SPAC)").getOrElse(b) { "" }
    override fun ipoBrancheText(b: Int) = when (b) {
        0 -> "KI-Firmen wachsen oft sehr schnell und geben viel Geld für Rechenzentren und Chips aus. Es zählt, ob der Umsatz schneller wächst als die Kosten für Rechenleistung, und wie hoch die Bewertung im Verhältnis zum Umsatz ist. Die Erwartungen sind hoch, nach Enttäuschungen fällt der Kurs oft stark."
        1 -> "Es kommt auf Umsatzwachstum, Bruttomarge und die Rule of 40 an. Nach ihr sollten Wachstum und Gewinnmarge zusammen mindestens 40 % ergeben. Wiederkehrende Umsätze aus Abos zählen mehr als einmalige."
        2 -> "Ohne zugelassenes Produkt hängt der Wert an Studienergebnissen und Zulassungen. Es zählt, wie lange das Geld reicht und wann die nächsten Studiendaten kommen. Ein Fehlschlag kann den Kurs halbieren."
        3 -> "Banken bewertet man nach Kurs-Buchwert-Verhältnis und Eigenkapitalrendite, das KGV sagt wenig. Dazu zählen die Qualität der Kredite, eine breite Einlagenbasis und das Zinsrisiko in der Bilanz."
        4 -> "Der Wert hängt an Rohstoffpreisen, Zinsen und Politik. Der Strombedarf von Rechenzentren treibt Netze, Gas und Kernkraft, diese Erwartung steckt oft schon im Preis."
        5 -> "Es kommt auf Marke, Wiederkäufe und Margen an. Wächst die Firma nur mit hohen Werbekosten, fällt der Gewinn später oft enttäuschend aus."
        else -> "Bis zur Übernahme liegt das Geld meist auf einem Treuhandkonto, rund 10 Dollar je Aktie. Danach zählt allein die gekaufte Firma, und die ist beim Börsengang noch unbekannt."
    }
    override val ipoUeber = "Über die Firma"
    override val ipoDiskussion = "Diskussion"
    override fun ipoHnWoche(n: Int) = "Hacker News: " + (if (n == 1) "1 Beitrag" else "$n Beiträge") + " in 7 Tagen"
    override val ipoKeineSchlagzeilen = "Keine Schlagzeilen in den Nachrichten der App."
    override val ipoDokumente = "Eingereichte Dokumente"
    override fun ipoFormular(f: String) = when (f) {
        "S-1", "F-1" -> "Prospekt"
        "S-1/A", "F-1/A" -> "Geänderter Prospekt"
        "424B4", "424B1" -> "Endgültiger Prospekt mit Preis"
        "EFFECT" -> "Von der SEC wirksam erklärt"
        "RW" -> "Rücknahme"
        else -> "Weiteres Angebot"
    } + " ($f)"
    override val ipoEdgarLink = "Alle Unterlagen bei der SEC"
    override val ipoQuelle = "IPO-Kalender von Finnhub"
    override val ipoOhneSchluessel = "Den Kalender aller Börsengänge lädt die App mit einem eigenen Schlüssel von Finnhub (Profil, Zugänge). Ohne Schlüssel stehen hier die Prospekte der letzten Tage bei der SEC."
    override val ipoKalenderFehlt = "Der IPO-Kalender von Finnhub ist gerade nicht erreichbar. Hier stehen die Prospekte der letzten Tage bei der SEC."
    override val ipoKeineEckdaten = "Preis und Umfang stehen noch nicht fest."
    override val ipoEdgarSuche = "Prospekte bei der SEC suchen"
    override fun ipoKatalogGeplant(stand: String) = "Geplant, noch ohne US-Prospekt (Stand $stand)"
    override fun bkBankSatz(kbv: String, roe: String, angemessen: String?) = if (angemessen != null) "Kurs-Buchwert $kbv bei $roe Eigenkapitalrendite. Angemessen wäre etwa $angemessen." else "Kurs-Buchwert $kbv. Die Eigenkapitalrendite von $roe deckt die Kapitalkosten nicht."
    override fun bkBankUnter(kapitalkosten: String, wachstum: String) = "Bei Banken und Versicherern sagt das KGV wenig. Gerechnet mit $kapitalkosten Kapitalkosten und $wachstum Wachstum."
    override fun bkBankBilanzSatz(roa: String?, ekQuote: String?) = listOfNotNull(roa?.let { "Gesamtkapitalrendite $it" }, ekQuote?.let { "Eigenkapitalquote $it" }).joinToString(", ") + "."
    override val bkBankBilanzUnter = "Gut ab 1 % Gesamtkapitalrendite und 8 % Eigenkapitalquote, schwach unter 0,5 % bzw. 5 %."
    override val heatmapAlle = "Alle"
    override val heatmapZoomZurueck = "Ganze Karte"
    override fun skKnoten(id: String): String = when (id) {
        "anfang" -> "Wert am Anfang"
        "einlagen" -> "Einlagen"
        "kursgewinne" -> "Kursgewinne"
        "dividenden" -> "Dividenden"
        "zinsen" -> "Zinsen"
        "depot" -> "Depot"
        "ende" -> "Wert am Ende"
        "entnahmen" -> "Entnahmen"
        "kursverluste" -> "Kursverluste"
        "gebuehren" -> "Gebühren"
        "steuern" -> "Steuern"
        "erstattungen" -> "Erstattungen"
        "korrekturen" -> "Korrekturen"
        "ergebnis" -> "Verkäufe"
        "netto+" -> "Gewinn netto"
        "netto-" -> "Verlust netto"
        "weitere+", "weitere-" -> "Weitere"
        "umsatz" -> "Umsatz"
        "herstellkosten" -> "Herstellkosten"
        "brutto" -> "Bruttogewinn"
        "forschung" -> "Forschung"
        "verwaltung" -> "Vertrieb, Verwaltung"
        "sonstige" -> "Sonstige Kosten"
        "betrieb" -> "Betriebsergebnis"
        "betriebsverlust" -> "Betriebsverlust"
        "steuer" -> "Steuern"
        "zinsenSonstiges" -> "Zinsen, Sonstiges"
        "finanzertrag" -> "Zinsen, Sonstiges"
        "netto" -> "Nettogewinn"
        else -> de.troetenrepublik.app.engine.Sankey.symbol(id).let { de.troetenrepublik.app.market.Symbols.find(it)?.name ?: it }
    }
    override val skGeldfluss = "Geldfluss"
    override val skVerkaeufe = "Ergebnis der Verkäufe"
    override fun skGuvTitel(jahr: String) = "Vom Umsatz zum Gewinn ($jahr)"
    override fun afVorlage(id: String): String = when (id) {
        "LEERVERKAUFT" -> "Leerverkaufte Aktien"
        "SQUEEZE" -> "Shortsqueeze"
        "ZAHLENLUECKE" -> "Kurslücke nach oben vor Zahlen"
        "INSOLVENZ" -> "Insolvenzkandidaten"
        "WOCHENTIEF" -> "Aufwärtstrend mit schwacher Woche"
        "LINIENSPRUNG" -> "Sprung über die 20-Tage-Linie"
        "UMKEHR" -> "Überverkaufte Umkehr"
        "UEBERVERKAUFT_ZAHLEN" -> "Überverkauft vor Zahlen"
        "HOECHSTSTAENDE" -> "Neue Höchststände"
        "AUSBRUCH" -> "Ausbruch"
        "KREUZUNG" -> "Linienkreuzung"
        "GEWINNWACHSTUM" -> "Hohes Gewinnwachstum"
        "UMSATZWACHSTUM" -> "Hohes Umsatzwachstum"
        "REL_VOLUMEN" -> "Hohes relatives Volumen"
        "STETIG" -> "Stetiges Wachstum im Aufwärtstrend"
        "HALTEN" -> "Kaufen und halten"
        "DIVIDENDENWACHSTUM" -> "Unterbewertetes Dividendenwachstum"
        "NIEDRIGES_KGV" -> "Niedriges KGV"
        "CANSLIM" -> "CANSLIM"
        "NEBENWERTE_WACHSTUM" -> "Nebenwerte mit Wachstum"
        "LUECKE_AUF" -> "Kurslücke nach oben"
        "LUECKE_AB" -> "Kurslücke nach unten"
        else -> id
    }
    override fun afVorlageIdee(id: String): String = when (id) {
        "LEERVERKAUFT" -> "US-Werte mit hoher Leerverkaufsquote und regem Handel."
        "SQUEEZE" -> "Liquide US-Werte mit vielen Leerverkäufen und wenig Institutionen, Kandidaten für einen Shortsqueeze."
        "ZAHLENLUECKE" -> "Werte, die in den nächsten zwei Tagen Zahlen melden und heute mit einer Kurslücke nach oben eröffnet haben."
        "INSOLVENZ" -> "US-Werte unter Buchwert mit sehr hoher Leerverkaufsquote."
        "WOCHENTIEF" -> "Werte im Aufwärtstrend, die in der letzten Woche nachgegeben haben, die schwächste Woche zuerst."
        "LINIENSPRUNG" -> "Werte, die bei hohem Volumen über die 20-Tage-Linie gesprungen sind und noch unter der 50-Tage-Linie liegen."
        "UMKEHR" -> "Überverkaufte Werte (RSI unter 30) mit Tagesplus bei doppeltem Volumen."
        "UEBERVERKAUFT_ZAHLEN" -> "Nebenwerte mit Gewinnwachstum und Zahlen im kommenden Monat, deren RSI unter 50 liegt."
        "HOECHSTSTAENDE" -> "Werte unter 7 € je Aktie mit Kaufempfehlung, die gleichzeitig ein 20-Tage-, 50-Tage- und Jahreshoch erreichen."
        "AUSBRUCH" -> "Ausbruch auf ein 50-Tage-Hoch über allen drei Linien bei solider Bilanz."
        "KREUZUNG" -> "Profitable Werte, deren 20-Tage-Linie in den letzten fünf Handelstagen die 50-Tage-Linie nach oben gekreuzt hat."
        "GEWINNWACHSTUM" -> "Gewinn und Umsatz wachsen um mehr als 25 %, der Kurs liegt über der 200-Tage-Linie."
        "UMSATZWACHSTUM" -> "Wachsende Unternehmen mit wenig Schulden, hohem Institutionenanteil und wenig Leerverkäufen."
        "REL_VOLUMEN" -> "Solvente Wachstumswerte im Aufwärtstrend mit ungewöhnlich hohem Volumen."
        "STETIG" -> "Gewinnwachstum über mehrere Zeiträume, der Kurs liegt mindestens 90 % über dem Jahrestief."
        "HALTEN" -> "Kleine Werte mit erwartetem Wachstum, hoher Eigenkapitalrendite und guter Liquidität."
        "DIVIDENDENWACHSTUM" -> "Große Dividendenzahler mit moderater Bewertung, Wachstum und einer Ausschüttung unter der Hälfte des Gewinns."
        "NIEDRIGES_KGV" -> "Kleine Werte unter Buchwert mit niedrigem KGV und positiven Renditen."
        "CANSLIM" -> "Filter nach den CANSLIM-Regeln von William O\'Neil, so weit die Daten reichen."
        "NEBENWERTE_WACHSTUM" -> "Nebenwerte zwischen 300 Mio. und 2 Mrd. mit Gewinn- und Umsatzwachstum über 20 %."
        "LUECKE_AUF" -> "Werte ab 300 Mio. Börsenwert, die heute mindestens 3 % über dem Vortagesschluss eröffnet haben, die größte Lücke zuerst. Vor Handelsbeginn zeigt die Liste noch die Lücken des letzten Handelstags."
        "LUECKE_AB" -> "Werte ab 300 Mio. Börsenwert, die heute mindestens 3 % unter dem Vortagesschluss eröffnet haben, die größte Lücke zuerst. Vor Handelsbeginn zeigt die Liste noch die Lücken des letzten Handelstags."
        else -> ""
    }
    override fun afVorlageFehlt(id: String): String? = when (id) {
        "LEERVERKAUFT" -> "Ohne Prüfung, ob Optionen und Leerverkauf möglich sind, dafür gibt es keine freie Quelle."
        "ZAHLENLUECKE" -> "Ohne ATR und ohne Unterscheidung vor oder nach Börsenschluss."
        "WOCHENTIEF" -> "Den Aufwärtskanal nähert die App über den Kurs über der 200-Tage-Linie und die 50- über der 200-Tage-Linie an."
        "UEBERVERKAUFT_ZAHLEN" -> "Zahlentermin in den nächsten 31 Tagen statt im laufenden Monat."
        "HOECHSTSTAENDE" -> "Hochs aus Schlusskursen, beim Jahreshoch genügt höchstens 1 % Abstand."
        "AUSBRUCH" -> "50-Tage-Hoch aus Schlusskursen."
        "UMSATZWACHSTUM" -> "Ohne Umsatzwachstum über fünf Jahre."
        "STETIG" -> "Ohne Gewinnwachstum der letzten fünf Jahre."
        "CANSLIM" -> "Ohne Gewinnwachstum über fünf Jahre, Streubesitz unter 20 Mio. Aktien und Marktlage. Die Marktphase steht unter Entdecken."
        "NEBENWERTE_WACHSTUM" -> "Ohne Wachstum über fünf Jahre."
        else -> null
    }
    override fun afFeld(id: String): String = when (id) {
        "BOERSENWERT" -> "Börsenwert"
        "KURS" -> "Kurs"
        "KGV" -> "KGV"
        "KGV_ERWARTET" -> "KGV erwartet"
        "KBV" -> "KBV"
        "DIVIDENDE" -> "Dividende"
        "NOTE" -> "Analystennote"
        "JAHR" -> "1 Jahr"
        "LINIE200" -> "Abstand 200-Tage-Linie"
        "LINIE50" -> "Abstand 50-Tage-Linie"
        "SMA50_200" -> "50- zu 200-Tage-Linie"
        "TAG" -> "Heute"
        "LUECKE" -> "Kurslücke"
        "VOLUMEN" -> "Volumen heute"
        "VOLUMEN_SCHNITT" -> "Volumen im Schnitt"
        "REL_VOLUMEN" -> "Relatives Volumen"
        "HOCH52" -> "Abstand Jahreshoch"
        "TIEF52" -> "Über Jahrestief"
        "ZAHLEN_TAGE" -> "Tage bis Zahlen"
        "PEG" -> "PEG"
        "ROE" -> "Eigenkapitalrendite"
        "ROA" -> "Gesamtkapitalrendite"
        "VERSCHULDUNG" -> "Verschuldung"
        "LEER" -> "Leerverkaufsquote"
        "BRUTTOMARGE" -> "Bruttomarge"
        "GEWINN_QUARTAL" -> "Gewinn Quartal"
        "UMSATZ_QUARTAL" -> "Umsatz Quartal"
        "GEWINN_JAHR" -> "Gewinnwachstum dieses Jahr"
        "GEWINN_FOLGEJAHR" -> "Gewinnwachstum nächstes Jahr"
        "GEWINN_FUENF" -> "Gewinnwachstum 5 Jahre"
        "INSTITUTIONEN" -> "Institutionen"
        "BETA" -> "Beta"
        "LIQUIDITAET" -> "Liquidität 3. Grades"
        "LIQUIDITAET_SCHNELL" -> "Liquidität 2. Grades"
        "AUSSCHUETTUNG" -> "Ausschüttungsquote"
        "RSI" -> "RSI"
        "LINIE20" -> "Abstand 20-Tage-Linie"
        "WOCHE" -> "Woche"
        "JAHRESANFANG" -> "Seit Jahresanfang"
        "HOCH20" -> "Abstand 20-Tage-Hoch"
        "HOCH50" -> "Abstand 50-Tage-Hoch"
        "KREUZUNG" -> "Linienkreuzung"
        else -> id
    }
    override val afVorlageKeine = "Keine Vorlage"
    override val afVorlageNurUs = "Nur US-Werte, die Leerverkaufsquote am Streubesitz gibt es frei nur dort."
    override val afVorlageHinweis = "Nach den Screener-Vorlagen von finviz. Kurs und Börsenwert in Euro mit den Zahlen der Dollargrenzen."
    override fun afVorlageStand(offen: Int) = if (offen == 1) "1 Wert wartet noch auf Profil oder Tageskurse." else "$offen Werte warten noch auf Profil oder Tageskurse."
    override val afVorlageWeiter = "Weiter prüfen"
    override val afAlarm = "Neue Treffer melden"
    override val afAlarmHinweis = "Gemeldet werden Werte, die neu alle eingeschalteten Grenzen erfüllen."
    override fun afAlarmTitel(vorlage: String) = "Neu in „$vorlage“"
    override fun afAlarmText(namen: List<String>, weitere: Int) = namen.joinToString(", ") + if (weitere > 0) " und $weitere weitere" else ""
    override fun afFeldAb(feld: String, wert: String) = "$feld ab $wert"
    override fun afFeldBis(feld: String, wert: String) = "$feld bis $wert"
    override fun afFeldZwischen(feld: String, von: String, bis: String) = "$feld $von bis $bis"
    override fun afFeldPositiv(feld: String) = "$feld positiv"
    override fun afFeldNegativ(feld: String) = "$feld negativ"
    override fun afUeberLinie(id: String) = when (id) {
        "SMA50_200" -> "50- über 200-Tage-Linie"
        "LINIE20" -> "Kurs über 20-Tage-Linie"
        "LINIE50" -> "Kurs über 50-Tage-Linie"
        else -> "Kurs über 200-Tage-Linie"
    }
    override fun afUnterLinie(id: String) = when (id) {
        "SMA50_200" -> "50- unter 200-Tage-Linie"
        "LINIE20" -> "Kurs unter 20-Tage-Linie"
        "LINIE50" -> "Kurs unter 50-Tage-Linie"
        else -> "Kurs unter 200-Tage-Linie"
    }
    override fun afNeuesHoch(tage: Int) = "Neues $tage-Tage-Hoch"
    override fun afNahHoch52(abstand: String) = "Höchstens $abstand unter dem Jahreshoch"
    override fun afZahlenIn(tage: Int) = "Zahlen in den nächsten $tage Tagen"
    override val afKreuzung = "20-Tage-Linie kreuzt 50-Tage-Linie nach oben"
    override fun afSortFeld(feld: String, aufsteigend: Boolean) = if (aufsteigend) "$feld, aufsteigend" else feld
    override val widgetNewsInhalt = "Inhalt"
    override val widgetNewsDepot = "Schlagzeilen zu meinen Positionen"
    override val widgetNewsWichtig = "Nur die wichtigsten Nachrichten"
    override val widgetNewsWichtigTitel = "Wichtigste Nachrichten"
    override val widgetNewsWichtigKeine = "Gerade keine wichtigen Nachrichten."
    override val widgetNewsAlle = "Alle Schlagzeilen"
    override val widgetNewsAlleTitel = "Schlagzeilen"
    override fun widgetNewsFeed(name: String) = "Eigener Feed: $name"
    override val widgetNewsListeKeine = "Gerade keine Schlagzeilen."
    override val widgetNewsFeedsHinweis = "Eigene Feeds legst du in den News mit dem Plus neben dem Zeitungssymbol an."
    override val widgetSchnellWahl = "Kacheln"
    override val widgetSchnellHinweis = "Vier Seiten wählen. Eine fünfte Wahl ersetzt die älteste."
    override val widgetSchnellVerlauf = "Depotverlauf"
    override val widgetEinzelSuche = "Aktie suchen"
    override fun apTitel(id: String): String = when (id) {
        "HALTEDAUER" -> "Haltedauer"
        "TREFFER" -> "Trefferquote"
        "CHANCE_RISIKO" -> "Gewinne gegen Verluste"
        "DISPOSITION" -> "Verlierer laufen lassen"
        "HAEUFIGKEIT" -> "Handelshäufigkeit"
        "HEBEL" -> "Hebelprodukte beim Handeln"
        "NACHKAUF" -> "Verbilligen"
        else -> id
    }
    override fun apWert(id: String, a: String, b: String, zahl: Int) = when (id) {
        "HALTEDAUER" -> "Im Median $a Tage bei $zahl Verkäufen"
        "TREFFER" -> "$a der $zahl Verkäufe im Plus"
        "CHANCE_RISIKO" -> "Ein Gewinn ist im Schnitt $a-mal so groß wie ein Verlust, je Verkauf $b"
        "DISPOSITION" -> "Verlierer im Median $a Tage gehalten, Gewinner $b Tage"
        "HAEUFIGKEIT" -> "$a Orders im Monat, $zahl in zwölf Monaten"
        "HEBEL" -> "$a des gehandelten Betrags"
        "NACHKAUF" -> if (zahl == 1) "1 Nachkauf mehr als 20 % unter dem Einstand" else "$zahl Nachkäufe mehr als 20 % unter dem Einstand"
        else -> a
    }
    override fun apErklaerung(id: String) = when (id) {
        "HALTEDAUER" -> "Wer Positionen nur Tage hält, handelt eher Kursbewegungen als Unternehmen. Kosten und Steuern fallen dann bei jedem Wechsel an."
        "TREFFER" -> "Die Trefferquote allein sagt wenig. Wichtig ist auch, ob die Gewinne die Verluste übertreffen."
        "CHANCE_RISIKO" -> "Liegt die Summe aller Verkäufe in Euro im Minus, hat das Handeln bisher Geld gekostet. Ein Verhältnis unter 1 braucht eine hohe Trefferquote."
        "DISPOSITION" -> "Viele Anleger verkaufen Gewinner früh und halten Verlierer in der Hoffnung auf Erholung. Feste Verkaufsregeln helfen dagegen."
        "HAEUFIGKEIT" -> "Häufiges Handeln erhöht Kosten und die Zahl der Entscheidungen. Studien zu Privatanlegern finden bei den aktivsten die schwächsten Ergebnisse nach Kosten."
        "HEBEL" -> "Hebelprodukte verlieren schnell an Wert und können ganz verfallen. Ein hoher Anteil am Handel bindet das Ergebnis an wenige, stark schwankende Wetten."
        "NACHKAUF" -> "Nachkaufen nach einem Verlust senkt den Einstand, erhöht aber das Gewicht eines Werts, der gerade fällt. Prüfe vorher, ob der Kaufgrund noch gilt."
        else -> ""
    }
    override val apUeberschrift = "Anlegerprofil"
    override val apZuWenig = "Für Haltedauer, Trefferquote und Gewinne gegen Verluste braucht es mindestens fünf abgeschlossene Verkäufe."
    override val apHinweis = "Aus den Käufen und Verkäufen in der App gerechnet, je Position nach FIFO. Sparpläne und übernommene Positionen zählen bei der Häufigkeit nicht."
    override val scTitel = "Selbstcheck"
    override val scFragen = listOf(
        "Ich habe einen Notgroschen von drei bis sechs Monatsausgaben außerhalb des Depots.",
        "Ich brauche das Geld im Depot frühestens in fünf Jahren.",
        "Ich kann für jede Position in einem Satz sagen, warum ich sie halte.",
        "Ich weiß vorher, wann ich verkaufe, bei Gewinn wie bei Verlust.",
        "Einen Rückgang des Depots um 30 % würde ich aussitzen, ohne aus Angst zu verkaufen.",
        "Ich kenne die laufenden Kosten meiner Fonds und die Gebühren je Order.",
        "Ich habe keine Schulden mit höheren Zinsen, als ich vom Depot erwarte.",
        "Ich prüfe die Gewichtung ein- bis zweimal im Jahr und schichte nur dann um.",
    )
    override fun scStand(ja: Int, alle: Int) = "$ja von $alle trifft zu"
    override val scHinweis = "Zum Abhaken für dich. Die Antworten bleiben nur auf dieser Seite und werden nicht gespeichert."
    override fun anHaelt(anteil: String, wert: String, stichtag: String, seit: String?) = "Hält selbst $anteil" + (if (wert.isNotBlank()) " ($wert)" else "") + " laut Meldung zum $stichtag" + (seit?.let { ", Kurs seitdem $it" } ?: "")
    override val anHalterHinweis = "Bestände aus den Pflichtmeldungen der Institute (in den USA Form 13F), bekannt sind nur die zehn größten Halter. Den Einstand melden Institute nicht, ob sie im Verlust sind, bleibt offen. Die Kursänderung seit dem Stichtag zeigt nur, wie sich der gemeldete Bestand seitdem entwickelt hat."
    override fun anHalterKeiner(n: Int) = "Keines der Häuser steht unter den $n größten gemeldeten Haltern. Kleinere Bestände nennt die Quelle nicht."
    override val finJahreTitel = "Bilanz und Cashflow über die Jahre"
    override val finLangfristSchulden = "Langfristige Schulden"
    override val finInvestitionen = "Investitionen in Anlagen"
    override fun umsatzSchaetzung(quartal: String) = if (quartal.isBlank()) "Umsatz erwartet" else "Umsatz erwartet $quartal"
    override val qzTitel = "Quartalszahlen"
    override val qzIntro = "Wer in der gewählten Woche Zahlen vorlegt, je Tag und Zeitpunkt. Rechts der erwartete Gewinn je Aktie, nach den Zahlen der tatsächliche."
    override val qzBekannte = "Bekannte"
    override val qzAlle = "Alle"
    override val qzFehlt = "Der Ergebniskalender ist gerade nicht erreichbar."
    override val qzOhneSchluessel = "Ohne Schlüssel stehen hier nur Werte aus Depot und Merkliste. Den Kalender aller US-Werte lädt die App mit einem eigenen Schlüssel von Finnhub oder Alpha Vantage (Profil, Zugänge)."
    override val qzKeineEigenen = "Kein Wert aus Depot oder Merkliste"
    override fun qzUmsatz(wert: String) = "Umsatz erw. $wert"
    override val qzTagFehlt = "nicht geladen"
    override val qzKeine = "Keine Termine"
    override fun qzKeineBekannten(n: Int) = "Kein bekannter Wert, $n kleinere unter „Alle“"
    override fun qzZeit(zeit: Int) = listOf("Vor Börsenstart", "Während des Handels", "Nach Börsenschluss", "Ohne Uhrzeit").getOrElse(zeit) { "" }
    override val qzHeute = "heute"
    override fun qzVon(gezeigt: Int, gesamt: Int) = "$gezeigt von $gesamt"
    override val qzEigen = "im Depot oder auf der Merkliste"
    override fun qzErwartet(wert: String) = "erwartet $wert"
    override fun qzVorjahr(wert: String) = "Vorjahr $wert"
    override val qzHinweis = "Bekannt heißt: Depot, Merkliste, Werte aus dem Verzeichnis der App und Firmen ab 1 Mrd. Dollar erwartetem Quartalsumsatz, je Zeitpunkt die acht größten. Termine können sich verschieben, Gewinne je Aktie stehen in Dollar und meist ohne Sondereffekte. Quelle: Alpha Vantage."
    override fun qzQuelle(anbieter: String?) = when (anbieter) {
        "finnhub" -> "Ergebniskalender von Finnhub, Termine aus Depot und Merkliste über Yahoo Finance"
        "alphavantage" -> "Ergebniskalender von Alpha Vantage, Termine aus Depot und Merkliste über Yahoo Finance"
        else -> "Termine aus Depot und Merkliste über Yahoo Finance"
    }
    // 5.95 Lesemodus
    override val lesemodusLeer = "Auf der geladenen Seite ist kein Text zu sehen, den die App anzeigen kann. „Originalseite anzeigen“ führt zurück."
    // 5.95 Gesamtstimmung
    override val sgTitel = "Gesamtstimmung"
    override val sgKachel = "Gesamtstimmung"
    override val sgAnbieter = "Alle Barometer"
    override fun sgAnzahl(n: Int, von: Int) = "$n von $von Bestandteilen"
    override val sgLaedtNoch = "lädt weitere"
    override val sgGewichte = "Gewichtung der Teile"
    override fun sgGruppe(schluessel: String) = when (schluessel) {
        "schwankung" -> "Schwankung"; "kurs" -> "Kurstrend"; "breite" -> "Marktbreite"; "kredit" -> "Kredit und Stress"
        "cnn" -> "CNN Fear & Greed"; "umfrage" -> "Anlegerumfrage"; "sicher" -> "Sichere Häfen"
        "position" -> "Terminmärkte"; "netz" -> "Stimmung im Netz"; "krypto" -> "Krypto"; "konjunktur" -> "Konjunktur"
        "hebel" -> "Kredithebel"; else -> schluessel
    }
    override fun sgGruppeWarum(schluessel: String) = when (schluessel) {
        "schwankung" -> "Optionspreise reagieren jeden Tag und gelten als das verlässlichste kurzfristige Maß für Angst. Die vier Werte hängen eng zusammen und teilen sich darum ein Gewicht."
        "kurs" -> "Wie stark die Kurse laufen, täglich und mit langer Geschichte. Momentum und Trend ähneln sich und zählen gemeinsam."
        "breite" -> "Zeigt, ob viele Aktien steigen oder nur wenige große."
        "kredit" -> "Geldgeber werden oft vorsichtig, bevor Aktien fallen."
        "cnn" -> "Fertiger Index aus sieben Teilen. Er überschneidet sich mit mehreren Gruppen hier und bekommt darum kein höheres Gewicht."
        "umfrage" -> "Die einzige direkte Befragung von Anlegern, schwankt aber stark von Woche zu Woche."
        "sicher" -> "Ob Geld eher in Aktien oder in sichere Anleihen fließt."
        "position" -> "Wochenzahlen der großen Spekulanten, sie ändern sich langsam."
        "netz" -> "Laut und leicht zu verzerren, darum wenig Gewicht."
        "krypto" -> "Eigener Markt, zeigt die Lust auf Risiko am Rand."
        "konjunktur" -> "Langsame Zahlen zur Wirtschaft, eher Hintergrund als Börsenstimmung."
        "hebel" -> "Nur vierteljährlich und mit Verzögerung."
        else -> ""
    }
    override fun sgTeil(schluessel: String) = when (schluessel) {
        "vix" -> "Schwankungsindex VIX"; "vix_struktur" -> "VIX-Terminstruktur"; "vvix" -> "VVIX, Schwankung des VIX"; "move" -> "MOVE, Schwankung der US-Anleihen"
        "momentum" -> "Momentum des S&P 500"; "trend_sp500" -> "Trend S&P 500"; "trend_dax" -> "Trend DAX"
        "breite200" -> "S&P-500-Werte über der 200-Tage-Linie"; "hochtief" -> "Jahreshochs gegen Jahrestiefs"; "rsp" -> "Gleichgewichteter gegen gewichteten S&P 500"
        "hochzins" -> "US-Hochzinsaufschlag"; "stress" -> "Finanzstress (St. Louis Fed)"
        "cnn" -> "CNN Fear & Greed"; "aaii" -> "AAII Privatanleger"; "sicher" -> "Aktien gegen Staatsanleihen"
        "cot_sp500" -> "Spekulanten S&P 500"; "cot_nasdaq" -> "Spekulanten Nasdaq 100"
        "wsb" -> "r/wallstreetbets"; "sorgen" -> "Sorgen auf Wikipedia"
        "krypto" -> "Fear & Greed Krypto"
        "umich" -> "Verbraucherstimmung USA"; "zinskurve" -> "US-Zinskurve"; "sahm" -> "Sahm-Regel"; "margin" -> "Margin-Kredite"
        else -> schluessel
    }
    override fun sgMethode(schluessel: String) = when (schluessel) {
        "vix" -> "Rang unter den Schlusskursen des letzten Jahres, ein niedriger VIX zählt als Gier"
        "vix_struktur" -> "Rang im letzten Jahr, liegt der VIX über dem VIX3M, zählt das als Angst"
        "vvix", "move" -> "Rang unter den Werten des letzten Jahres, niedrig zählt als Gier"
        "momentum" -> "Abstand des S&P 500 zu seiner 125-Tage-Linie, Rang im letzten Jahr"
        "trend_sp500", "trend_dax" -> "Abstand zur 200-Tage-Linie, Rang im letzten Jahr"
        "breite200" -> "Feste Grenzen: 20 % der Werte ergeben 0, 80 % ergeben 100"
        "hochtief" -> "Hochs minus Tiefs in Prozent aller Werte, feste Grenzen: −30 % ergeben 0, +30 % ergeben 100"
        "rsp" -> "Vorsprung über 20 Handelstage, Rang im letzten Jahr"
        "hochzins" -> "Rang unter den Tageswerten der letzten zwei Jahre, ein niedriger Aufschlag zählt als Gier"
        "stress" -> "Rang unter den Wochenwerten der letzten zwei Jahre, wenig Stress zählt als Gier"
        "cnn" -> "Wert von CNN unverändert"
        "aaii" -> "Anteil der Bullen unter Bullen und Bären"
        "sicher" -> "Vorsprung der Aktien über 20 Handelstage, Rang im letzten Jahr"
        "cot_sp500", "cot_nasdaq" -> "Lage zwischen Tief und Hoch der letzten drei Jahre"
        "wsb" -> "Anteil positiver Kommentare, nur bei klarer Richtung"
        "sorgen" -> "Aufrufe im Vergleich zum ganzen Zeitraum, viel Lesen zählt als Angst"
        "krypto" -> "Wert von alternative.me unverändert"
        "umich" -> "Rang unter den Monatswerten der letzten zehn Jahre"
        "zinskurve" -> "10 Jahre minus 3 Monate, Ampel: grün 70, gelb 40, rot 20"
        "sahm" -> "Feste Grenzen: 0,5 Punkte ergeben 0, −0,1 ergeben 100"
        "margin" -> "Feste Grenzen: −20 % zum Vorjahr ergeben 0, +30 % ergeben 100"
        else -> ""
    }
    override fun sgRoh(schluessel: String, zahl: String) = when (schluessel) {
        "vix" -> "VIX $zahl"; "vvix" -> "VVIX $zahl"; "move" -> "MOVE $zahl"; "vix_struktur" -> "VIX zu VIX3M $zahl"
        "trend_sp500", "trend_dax" -> "$zahl zur 200-Tage-Linie"; "breite200" -> "$zahl über der 200-Tage-Linie"
        "hochzins" -> "Aufschlag $zahl Pkt."; "stress" -> "Stressindex $zahl"
        "sicher" -> "Aktien gegen Anleihen $zahl"; "cot_sp500", "cot_nasdaq" -> "netto $zahl"
        "umich" -> "Index $zahl"; "zinskurve" -> "Abstand $zahl\u00A0Pkt."
        "sahm" -> "$zahl Pkt."; "margin" -> "$zahl zum Vorjahr"; else -> zahl
    }
    override fun sgHochTief(hochs: Int, tiefs: Int) = "$hochs nahe Jahreshoch · $tiefs nahe Jahrestief"
    override fun sgGewicht(prozent: String) = "Gewicht $prozent"
    override fun sgStand(datum: String) = "Stand $datum"
    override fun sgFehlen(namen: String) = "Ohne Daten: $namen. Ihr Gewicht geht an die übrigen Teile."
    override val sgQuellen = "Quellen: CNN, AAII, Cboe, CFTC, alternative.me, Tradestie, Wikimedia, Yahoo Finance (VIX, VIX3M, VVIX, MOVE, S&P 500, DAX, RSP, SPY, IEF und Kennzahlen der S&P-500-Werte), FRED der Federal Reserve Bank of St. Louis (STLFSI4, ICE BofA US High Yield Index Option-Adjusted Spread, Margin-Kredite aus der Finanzierungsrechnung Z.1, Verbraucherstimmung der University of Michigan (Zitat unten)), US-Finanzministerium und Bureau of Labor Statistics. Gewichtung und Rechnung: eigene Berechnung."
    override fun sgZitatUmich(datum: String) = "Surveys of Consumers, University of Michigan, University of Michigan: Consumer Sentiment © [UMCSENT], retrieved from FRED, Federal Reserve Bank of St. Louis; https://fred.stlouisfed.org/series/UMCSENT, (Accessed on $datum)."

    // 5.95 Lizenzen
    override val lizenzenTitel = "Open-Source-Lizenzen"
    override val lizenzenUntertitel = "Bibliotheken in der App und ihre Lizenzen"
    override val lizenzenHinweis = "Die App enthält Bibliotheken anderer Projekte, die unter eigenen Lizenzen stehen. Die Lizenztexte stehen hier im englischen Original."
    override val lizenzenFehlt = "Die Lizenzdatei fehlt in diesem Paket. Sie liegt auch im Quellcode als THIRD_PARTY_LICENSES.txt."
    // 5.95 Nasdaq
    override val nkQuelle = "Nasdaq"
    override val nkNichtVerfuegbar = "Der Kalender ist gerade nicht verfügbar. Die App versucht es beim nächsten Öffnen wieder."
    override val nkIpoFehlt = "Der IPO-Kalender ist gerade nicht verfügbar. Hier stehen die Prospekte der letzten Tage bei der SEC."
    override fun nkIpoQuelle(finnhub: Boolean) = if (finnhub) "IPO-Kalender der Nasdaq, ergänzt von Finnhub" else "IPO-Kalender der Nasdaq"
    override fun nkQzQuelle(anbieter: String?) = when (anbieter) {
        "finnhub" -> "Ergebniskalender der Nasdaq, ergänzt um Finnhub, Termine aus Depot und Merkliste über Yahoo Finance"
        "alphavantage" -> "Ergebniskalender der Nasdaq, ergänzt um Alpha Vantage, Termine aus Depot und Merkliste über Yahoo Finance"
        else -> "Ergebniskalender der Nasdaq, Termine aus Depot und Merkliste über Yahoo Finance"
    }
    override val nkQzHinweis = "Bekannt heißt: Depot, Merkliste, Werte aus dem Verzeichnis der App und Firmen ab 10 Mrd. Dollar Börsenwert oder 1 Mrd. Dollar erwartetem Quartalsumsatz, je Zeitpunkt die acht größten. Termine können sich verschieben, Gewinne je Aktie stehen in Dollar und meist ohne Sondereffekte. Quellen: Nasdaq und Finnhub."
    override val nkDivTitel = "Dividendentermine"
    override val nkDivIntro = "Werte, die an diesem Tag ohne die nächste Dividende gehandelt werden (Ex-Tag), rechts der Betrag je Aktie."
    override fun nkZahltag(datum: String) = "Zahltag $datum"
    override fun nkJeJahr(wert: String) = "im Jahr $wert"
    override val nkDivKeine = "Keine Termine"
    override fun nkDivKeineBekannten(n: Int) = "Kein bekannter Wert, $n weitere unter „Alle“"
    override val nkDivHinweis = "Bekannt heißt: Depot, Merkliste und Werte aus dem Verzeichnis der App. Der Kalender führt Werte an US-Börsen samt Vorzugsaktien und Fonds, Beträge in Dollar je Aktie. „Im Jahr“ ist die Summe der Zahlungen eines Jahres laut Quelle. Sortiert wird nach Ex-Tag, der Zahltag steht in der Zeile. Bei Splits gilt der Tag, ab dem im neuen Verhältnis gehandelt wird."
    override val nkSplits = "Aktiensplits"
    override val nkSplit = "Split"
    override val nkZusammenlegung = "Zusammenlegung"
    override fun nkSplitText(neu: Int, alt: Int) = "aus " + (if (alt == 1) "1 Aktie" else "$alt Aktien") + (if (neu == 1) " wird " else " werden ") + neu
    override val nkWiTitel = "Wirtschaftstermine"
    override val nkWiIntro = "An diesen Tagen erscheinen Wirtschaftsdaten wie Inflation, Beschäftigung und Wachstum. Rechts steht der gemeldete Wert, darunter die Erwartung der Fachleute, in der Zeile der Wert der Vorperiode."
    override val nkWichtige = "Wichtige Länder"
    override val nkGanztags = "ganztägig"
    override fun nkVorher(wert: String) = "zuvor $wert"
    override val nkEuroraum = "Euroraum"
    override val nkWiKeine = "Keine Termine"
    override val nkWiHinweis = "Wichtige Länder sind die USA, der Euroraum, Deutschland, Großbritannien, Japan, China, die Schweiz und Frankreich. Uhrzeiten gelten in der Zeitzone des Geräts, die Tage nach dem Kalender in New York. Werte können später überarbeitet werden."

    // 5.95 Quellen
    override val q5Nicht = "Gerade nicht verfügbar. Später noch einmal versuchen."
    override val q5VxTitel = "VIX-Terminkurve"
    override fun q5VxVerhaeltnis(q: String) = "Erster zu zweitem Monatskontrakt: $q"
    override fun q5VxQuelle(datum: String) = "Quelle: Cboe Futures Exchange, Abrechnungspreise vom $datum. © Cboe Exchange, Inc. VIX® ist eine eingetragene Marke der Cboe Exchange, Inc."
    override val q5VxLink = "Abrechnungspreise bei der Cboe ansehen"
    override val q5VxMehr = "Ein Terminkontrakt legt heute den Preis fest, zu dem der VIX später abgerechnet wird. M1 verfällt am nächsten Verfallstag, M2 im Folgemonat.\n• Contango: M2 teurer als M1, der Normalzustand\n• Backwardation: M1 über M2, Anleger suchen sofort Schutz, meist bei einem plötzlichen Einbruch\n\nDie Kurve vergleicht M1 mit M2. Ein Verhältnis über 1 heißt Backwardation, ab 0,95 gilt die Kurve als flach, darunter als Contango."
    override val q5IgPrivat = "Investegate ist nur für Privatanleger bestimmt. Bestätige, dass du Privatanleger bist und die Meldungen nur persönlich nutzt; erst danach ruft die App sie ab."
    override val q5IgJa = "Ich bin Privatanleger"
    override val q5IgNein = "Nein"
    override val q5IgKeine = "Im Moment führt Investegate keine Director Dealings zu dieser Aktie."
    override fun q5IgQuelle(quellen: String) = "Quelle: Investegate. Die Meldungen stammen von $quellen."
    override val q5IgSeite = "Alle Meldungen der Firma bei Investegate"
    override fun q5IgQuelleMeldung(quelle: String) = "Quelle: Investegate, Meldung über $quelle, unverändert wiedergegeben."
    override val q5IgOeffnen = "Meldung bei Investegate öffnen"
    override val q5IgAlleTitel = "Director Dealings Großbritannien"
    override val q5IgAlleUnter = "Neue Meldungen über Eigengeschäfte von Vorständen britischer Firmen, über Investegate"
    override val q5IgQuelleAlle = "Quelle: Investegate, Kategorie Directors Dealings"
    override val q5IgKategorie = "Alle Director Dealings bei Investegate"
    override val q5NnKeine = "Im letzten Jahr keine Managers Transactions bei Nasdaq Nordic."
    override val q5NnQuelle = "Quelle: Nasdaq Nordic, Unternehmensmeldungen der Kategorie Managers Transactions"
    override val q5MnbKeine = "Im letzten Jahr keine Meldungen von Führungskräften bei der MNB."
    override val q5MnbQuelle = "Quelle: Magyar Nemzeti Bank, Tőkepiaci közzétételek (kozzetetelek.mnb.hu). Ein Tipp öffnet die Meldung, die PDFs öffnest du dort selbst."
    override val q5EodTitel = "Nachrichten über EODHD"
    override val q5EodKeine = "EODHD hat gerade keine Nachrichten zu diesem Wert."
    override val q5EodQuelle = "Nachrichten über EODHD mit deinem Schlüssel. Ein Abruf kostet 5 von 20 Calls am Tag, Rücksetzung um Mitternacht GMT."
    override val q5EodLimit = "Für heute sind die EODHD-Abrufe aufgebraucht. Ab Mitternacht GMT geht es wieder."
    override val q5IgQuelleOhne = "Quelle: Investegate, unverändert wiedergegeben."
    override val q5StandardArtikel = "Artikel von derStandard.at"
    // 5.95 Long/Short
    override fun opUsNotierung(kuerzel: String) = "Optionshandel der US-Notierung $kuerzel, Preise in US-Dollar."

    // 5.95 Stimmrechte GB
    override val srTitel = "Stimmrechtsmeldungen"
    override val srKeine = "Im Moment führt Investegate keine Stimmrechtsmeldungen zu dieser Aktie."
    override val srOhne = "Ohne die Bestätigung ruft die App keine Meldungen von Investegate ab. Die Stimmrechtsmeldungen stehen auf der Seite der Firma bei Investegate."
    override fun srNachher(p: String) = "jetzt $p"
    override fun srVorher(p: String) = "vorher $p"
    override fun srUnter(p: String) = "unter $p"
    override fun srAktien(p: String) = "Aktien $p"
    override fun srInstrumente(p: String) = "Instrumente $p"
    override fun srSchwelleUeber(p: String) = "Schwelle $p erreicht"
    override fun srSchwelleUnter(p: String) = "unter Schwelle $p"
    override fun srStichtag(d: String) = "Stichtag $d"
    override fun srPunkte(z: String) = "$z Pkt."
    override fun srQuelle(quellen: String) = "Quelle: Investegate, Meldungen über $quellen."
    override val srQuelleOhne = "Quelle: Investegate."
    override val srHinweis = "Eine TR-1-Meldung zeigt, wann ein Halter 3 % der Stimmrechte einer britischen Firma erreicht und jede weitere volle Prozentschwelle über- oder unterschreitet. Bei Firmen aus anderen Ländern gelten die Schwellen 5, 10, 15, 20, 25, 30, 50 und 75 %. Der Anteil kann aus Aktien, Finanzinstrumenten wie Optionen oder beidem bestehen. Die Schwelle leitet die App aus dem Anteil vorher und nachher ab."

    // 5.96 Labor
    override val slRegelWaehlen = "Regel für die Einzelansicht wählen"
    override val slKeineLinie = "Alle Linien sind ausgeblendet. Ein Tipp auf eine Regel in der Liste zeigt ihre Linie wieder."
    override fun slLinieSchalter(regel: String) = "Linie $regel im Chart zeigen"

    // 5.96 Daten
    override fun hlUnter(p: String) = "unter $p"
    override val hlUnveraendert = "unverändert"
    override fun hlMitNamen(n: Int, gesamt: Int) = "$n von $gesamt mit Namen gemeldet"
    override val hlGemeldet = "Mit Namen gemeldet"
    override val hlKeineNamen = "Die Quelle nennt keine Institute mit Namen."
    override val hlFonds = "Größte Fonds"
    override val hlFondsHinweis = "Fonds stehen getrennt. Ihre Anteile können in denen der Institute oben schon enthalten sein."
    override val vmWerte: String = "Vermögenswerte"
    override val vmSchulden: String = "Schulden"
    override val vmNetto: String = "Netto"
    override val vmKeineSchulden: String = "keine gemeldet"
    override fun vmImMinus(spanne: String): String = "$spanne im Minus"
    override fun vmPosten(werte: Int): String = "Aus $werte gemeldeten Vermögenswerten."
    override fun vmNettoOffen(schlecht: String?, gut: String?): String =
        "Wie viel nach Abzug der Schulden bleibt, lässt sich aus den Spannen nicht eingrenzen." +
            (gut?.let { " Im günstigsten Fall bleiben $it." } ?: "") +
            (schlecht?.let { " Im ungünstigsten Fall sind die Schulden um $it höher als die Vermögenswerte." } ?: "")
    override val vmRangErklaerung: String =
        "Vermögen laut Jahresmeldung, nur Repräsentantenhaus. Sortiert nach der Mitte der Vermögenswerte abzüglich der Mitte der Schulden."

    // 5.96 Vergleich
    override fun vgMax(n: Int) = "Bis zu $n Vergleiche gleichzeitig. Ein Tipp auf einen gewählten Vergleich entfernt ihn."
    override val vgSuche = "Aktie, ETF, Investor oder Person suchen"
    override val vgAktienEtfs = "Aktien, ETFs und Indizes"
    override val vgPolitiker = "Politiker"
    override val vgPolitikerMehr = "Weitere Personen findest du über die Suche."
    override val vgLabor = "Regeln aus dem Strategietest"
    override fun vgLaborUnter(name: String) = "auf $name angewendet"
    override val vgHaus = "Repräsentantenhaus"
    override val vgSenat = "Senat"
    override val vgRegierung = "US-Regierung"
    override val vgKeineTreffer = "Keine Treffer."
    override fun vgZeitraum(von: String, bis: String) = "Gemeinsamer Zeitraum: $von bis $bis."
    override val vgHeute = "heute"
    override fun vgKuerzer(name: String, ab: String) = "Kürzer als gewählt, weil es für $name erst ab $ab Daten gibt."
    override val vgLaedt = "wird geladen"
    override val vgKeineDaten = "keine Daten"
    override val vgZuKurz = "zu kurzer Verlauf"
    override fun vgGeschaefte(n: Int) = if (n == 1) "nur 1 Geschäft" else "nur $n Geschäfte"
    override val vgWaehrungHinweis = "Jede Linie zeigt die Wertentwicklung in ihrer eigenen Währung, so wie die Aktie selbst. Verläufe von Investoren und Politikern sind Schätzungen aus ihren Meldungen."
    override val vgPlanNote = "Ausführung am ersten Handelstag jedes Monats zum Schlusskurs, ohne Kosten und Steuern. Dividenden sind wieder angelegt."
    override val vgGleicheRaten = "Jeder Vergleich bekommt dieselben Raten im selben Zeitraum. Bei Depotverläufen gilt der erste Wert im Monat als Kaufkurs."
    override val vgLaborHinweis = "Eine Regel aus dem Strategietest handelt diese Aktie nach ihren Signalen, mit Spread und 1 € Gebühr je Order wie im Strategietest."
    override fun vgOhneDaten(namen: String) = "Ohne Daten: $namen."
    override fun vgWenigGeschaefte(namen: String) = "Zu wenige Geschäfte für einen Verlauf: $namen."
    override val vgVergleichIntro = "Alle Linien beginnen am Anfang des gemeinsamen Zeitraums bei null. Über „Vergleichen“ kommen weitere Werte und dein Depot dazu."
    override val vgImZeitraum = "Im selben Zeitraum"

    // 5.96 Feed
    override val feedErstellen = "Eigenen Feed erstellen"
    override val feedBearbeiten = "Feed bearbeiten"
    override val feedHinweis = "Ein eigener Feed sammelt die Schlagzeilen der gewählten Quellen in einer eigenen Kategorie, neueste zuerst. Eigene Zugänge sind Adressen von RSS- oder Atom-Feeds, die die App beim Hinzufügen prüft."
    override val feedName = "Name des Feeds"
    override val feedZugaenge = "Eigene Zugänge"
    override val feedQuellenApp = "Quellen der App"
    override val feedPruefen = "Prüfen und hinzufügen"
    override fun feedZugangOk(titel: String, n: Int) = "„$titel“ hinzugefügt, $n Schlagzeilen"
    override val feedNichtErreichbar = "Die Adresse antwortet nicht oder meldet einen Fehler."
    override val feedKeinFeed = "Unter dieser Adresse liegt kein lesbarer RSS- oder Atom-Feed."
    override fun feedFehler(f: de.troetenrepublik.app.engine.EigenerFeed.Fehler) = when (f) {
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.LEER -> "Bitte eine Adresse eintragen."
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.SCHEMA -> "Möglich sind nur Adressen mit http:// oder https://."
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.ZUGANGSDATEN -> "Die Adresse enthält Zugangsdaten wie Name, Passwort oder Schlüssel. Die App speichert keine Zugangsdaten."
        de.troetenrepublik.app.engine.EigenerFeed.Fehler.UNGUELTIG -> "Diese Adresse ist nicht gültig."
    }
    override fun feedSchonInApp(name: String) = "Diese Adresse gehört zur Quelle $name der App. Sie ist jetzt angehakt."
    override val feedSchonDrin = "Diese Adresse steht schon in der Liste."
    override val feedQuelleAus = "unter Quellen ausgeschaltet"
    override fun feedAusgeschaltet(namen: String) = "Unter Quellen ausgeschaltet: $namen"
    override val feedLeer = "Die gewählten Quellen haben gerade keine Schlagzeilen."
    override val feedOhneQuelle = "Wähle mindestens eine Quelle oder trage einen Zugang ein."
    override val feedLoeschen = "Feed löschen"
    override fun feedLoeschenFrage(name: String) = "„$name“ löschen"
    override val feedLoeschenText = "Die Kategorie verschwindet von der Nachrichtenseite. Die Quellen selbst bleiben."
    override fun feedZuViele(n: Int) = "Höchstens $n eigene Feeds."
    override fun feedMeldeOption(stufe: String) = when (stufe) {
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE -> "Eigene Feeds und wichtige Meldungen"
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE -> "Nur eigene Feeds"
        de.troetenrepublik.app.data.MITTEILUNG_QUELLEN -> "Nur gewählte Quellen"
        else -> ""
    }
    override fun feedMeldeOptionHint(stufe: String) = when (stufe) {
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE -> "Neue Schlagzeilen aus den eigenen Feeds, dazu die markierten wichtigen Meldungen aller Quellen"
        de.troetenrepublik.app.data.MITTEILUNG_EIGENE -> "Nur neue Schlagzeilen aus den eigenen Feeds, bei denen Mitteilungen eingeschaltet sind"
        de.troetenrepublik.app.data.MITTEILUNG_QUELLEN -> "Nur neue Schlagzeilen der Quellen, die unten angehakt sind"
        else -> ""
    }
    override val feedMelden = "Bei neuen Schlagzeilen benachrichtigen"
    override val feedMeldenHint = "Wirkt bei den Mitteilungen „Nur eigene Feeds“ und „Eigene Feeds und wichtige Meldungen“."
    override val feedMeldeQuellen = "Quellen für Mitteilungen"
    override val feedOhneFeeds = "Noch kein eigener Feed. Das +-Symbol auf der Nachrichtenseite legt einen an."
    override val feedBuendel = "Mehr als drei neue Schlagzeilen auf einmal kommen als eine Mitteilung."

    // 5.96 Kongress
    override val copyFormer = "Ehemalige"
    override val copyMitgliederQuelle = "Mitglieder: unitedstates/congress-legislators (CC0), einmal am Tag geprüft. Ehemalige stehen hier bis 180 Tage nach dem Ausscheiden und nach ihrer letzten Meldung, danach über die Suche."
    override fun kammerZeitraum(senat: Boolean, von: Int, bis: Int) = (if (senat) "ehemals im Senat, " else "ehemals im Repräsentantenhaus, ") + amtZeitraum(von, bis)
    override fun amtZeitraum(von: Int, bis: Int) = if (von == bis) "$von" else "$von bis $bis"

    // 5.97 Aufklappen
    override val aufZahlenHerkunft = "Herkunft der Zahlen"

    // 5.97 Finanzen
    override val finDivTitel = "Dividende"
    override val finDivJeAktie = "Je Stück in 12 Monaten"
    override val finDivExTag = "Nächster Ex-Tag"
    override val finDivZahltag = "Nächste Zahlung"
    override fun finDivBestand(stueck: String) = "Deine $stueck Stück im Jahr, vor Steuer"
    override val finVerlust = "Verlust"
    override val trErkReddit = "Reddit: Erwähnungen laut ApeWisdom."
    override val trErkRedditDeutsch = "Deutsche Subreddits: Die App liest über deinen Reddit-Zugang die neuesten Beiträge und zählt, wie oft der Wert darin genannt wird."
    override fun trErkVerlauf(seit: String?) = "Woche, Monat und Jahr kommen aus dem Verlauf, den die App " + (if (seit != null) "seit $seit " else "ab jetzt ") + "mitschreibt."
    override val trErkBeitraege = "Reddit-Beiträge: Suche nach Kürzel und Name in Börsen-Subreddits über deinen Reddit-Zugang, letzter Monat."
    override val trErkYahoo = "Yahoo Finance: Liste der meistaufgerufenen Werte in deiner Region und in den USA."
    override val trErkWiki = "Wikipedia: Aufrufe des Artikels der letzten 60 Tage ohne Bots (Wikimedia Foundation, CC0). Die Prozentzahl vergleicht die letzten 7 Tage mit den 4 Wochen davor."
    override val trErkHn = "Hacker News: Beiträge mit dem Firmennamen in den letzten 7 Tagen, verglichen mit dem Wochenschnitt der 4 Wochen davor. Erscheint nur bei Technologie- und Kommunikationswerten."
    override fun trErkMastodon(tag: String) = "Mastodon: Beiträge mit #$tag auf mastodon.social in den letzten 7 Tagen."
    override val trErkBluesky = "Bluesky: Beiträge mit dem Firmennamen in den letzten 7 Tagen und in der Woche davor, über dein App-Passwort."

    // 5.97 Quartalszahlen
    override fun qzUhrKurz(art: Int, von: String?, bis: String?): String? = when (art) {
        0 -> von?.let { "um $it" }
        1 -> bis?.let { "vor $it" }
        2 -> if (von != null && bis != null) "$von bis $bis" else null
        3 -> von?.let { "ab $it" }
        else -> null
    }
    override fun qzUhrLang(art: Int, von: String?, bis: String?): String = when (art) {
        0 -> if (von != null) "um $von" else "Uhrzeit noch nicht bekannt"
        1 -> "vor Börsenbeginn" + (bis?.let { " (vor $it)" } ?: "")
        2 -> "während des Handels" + (if (von != null && bis != null) " ($von bis $bis)" else "")
        3 -> "nach Börsenschluss" + (von?.let { " (ab $it)" } ?: "")
        else -> "Uhrzeit noch nicht bekannt"
    }
    override fun qzZeitTag(zeit: String, versatz: Int) = when {
        versatz == 1 -> "$zeit am Folgetag"
        versatz == -1 -> "$zeit am Vortag"
        versatz > 1 -> "$zeit, $versatz Tage später"
        versatz < -1 -> "$zeit, ${-versatz} Tage vorher"
        else -> zeit
    }
    override val qzGeschaetzt = "Termin geschätzt"
    override fun qzZeitzone(zone: String) = "Uhrzeiten in der Zeitzone des Geräts ($zone)"
    override val qzbZurAktie = "Zur Aktie"
    override fun qzbKalenderNotierung(symbol: String) = "Notierung im Kalender: $symbol"
    override val qzbKalenderNotierungText = "Der Kalender führt die Notierung an der US-Börse. Bei Auslandswerten ist das oft ein Hinterlegungsschein (ADR), sein Gewinn je Aktie kann sich vom Heimatwert unterscheiden."
    override fun qzbQuartal(quartal: String) = "Quartal $quartal"
    override val qzbTermin = "Termin"
    override val qzbTag = "Tag"
    override val qzbUhrzeit = "Uhrzeit"
    override val qzbKonferenz = "Telefonkonferenz der Firma"
    override fun qzbTerminSpanne(von: String, bis: String) = "zwischen $von und $bis"
    override val qzbGeschaetztText = "Die Firma hat den Tag noch nicht bestätigt. Die Quellen schätzen ihn nach den Terminen der Vorjahre."
    override val qzbUhrText = "Viele Firmen melden ihre Zahlen kurz vor Börsenbeginn oder kurz nach Börsenschluss. Die Uhrzeit rechnet die App in die Zeitzone des Geräts um, der genaue Zeitpunkt kann abweichen."
    override val qzbZahlen = "Welche Zahlen kommen"
    override val qzbGewinn = "Gewinn je Aktie"
    override val qzbUmsatz = "Umsatz"
    override fun qzbErwartetVon(quelle: Int, analysten: Int?) = when (quelle) {
        0 -> "erwartet laut Kalender" + (analysten?.let { ", $it Schätzungen" } ?: "")
        else -> "erwartet laut Yahoo Finance" + (analysten?.let { ", $it Analysten" } ?: "")
    }
    override val qzbSpanne = "Spanne der Schätzungen"
    override fun qzbVonBis(von: String, bis: String) = "$von bis $bis"
    override val qzbVorjahr = "Vorjahresquartal"
    override val qzbGemeldet = "gemeldet"
    override val qzbAusblick = "Ausblick"
    override val qzbAusblickText = "Viele Firmen nennen mit den Zahlen auch eine Erwartung für das nächste Quartal oder das ganze Jahr. Dafür führen die Quellen der App keine Zahlen. Der Ausblick steht in der Mitteilung der Firma."
    override val qzbZahlenText = "Gewinn je Aktie ist der Gewinn des Quartals geteilt durch die Zahl der Aktien, meist der bereinigte Wert ohne Sondereffekte. Umsatz ist alles, was die Firma im Quartal mit ihren Kunden eingenommen hat."
    override val qzbKeineErwartung = "Zu diesem Termin nennen die Quellen keine Schätzungen."
    override val qzbBewertung = "Wie die Zahlen eingeordnet werden"
    override fun qzbGewinnWachstum(a: String, b: String, p: String) = "Erwartet wird ein Gewinn je Aktie von $a nach $b im Vorjahresquartal, also $p mehr."
    override fun qzbGewinnRueckgang(a: String, b: String, p: String) = "Erwartet wird ein Gewinn je Aktie von $a nach $b im Vorjahresquartal, also $p weniger."
    override fun qzbGewinnAusVerlust(a: String, b: String) = "Erwartet wird ein Gewinn je Aktie von $a nach einem Verlust von $b je Aktie im Vorjahresquartal."
    override fun qzbVerlustErwartet(a: String, b: String?) = "Erwartet wird ein Verlust von $a je Aktie" + (b?.let { ", im Vorjahresquartal waren es $it" } ?: "") + "."
    override fun qzbUmsatzVeraenderung(a: String, b: String, p: String) = "Beim Umsatz liegt die Erwartung bei $a nach $b im Vorjahresquartal ($p)."
    override fun qzbSpanneWeit(a: String, b: String, n: Int?) = "Die Schätzungen" + (n?.let { " der $it Analysten" } ?: "") + " reichen von $a bis $b. Diese weite Spanne zeigt, dass die Analysten uneinig sind."
    override fun qzbSpanneEng(a: String, b: String, n: Int?) = "Die Schätzungen" + (n?.let { " der $it Analysten" } ?: "") + " liegen eng beieinander, von $a bis $b."
    override fun qzbSchaetzungHoch(a: String, b: String, p: String, n: Int?, m: Int?) = "In den letzten 30 Tagen ist die Schätzung von $b auf $a gestiegen ($p)." + revisionen(n, m)
    override fun qzbSchaetzungRunter(a: String, b: String, p: String, n: Int?, m: Int?) = "In den letzten 30 Tagen ist die Schätzung von $b auf $a gesunken ($p)." + revisionen(n, m)
    private fun revisionen(n: Int?, m: Int?) = if (n == null || m == null || n + m == 0) "" else " $n Analysten haben erhöht, $m gesenkt."
    override fun qzbSchaetzungGleich(a: String) = "In den letzten 30 Tagen ist die Schätzung etwa gleich geblieben ($a)."
    override fun qzbTreffer(n: Int, m: Int, p: String) = "In $n der letzten $m Quartale lag der Gewinn je Aktie über der Erwartung. Die Abweichung betrug im Mittel $p."
    override fun qzbGemeldetUeber(a: String, b: String, p: String) = "Gemeldet wurden $a je Aktie bei erwarteten $b, also $p über der Erwartung."
    override fun qzbGemeldetUnter(a: String, b: String, p: String) = "Gemeldet wurden $a je Aktie bei erwarteten $b, also $p unter der Erwartung."
    override fun qzbGemeldetWie(a: String, b: String) = "Gemeldet wurden $a je Aktie bei erwarteten $b, also etwa wie erwartet."
    override val qzbBewertungText = "An der Börse zählt vor allem der Vergleich mit den Erwartungen, weil die Kurse sie schon vorher enthalten. Liegen Gewinn je Aktie oder Umsatz darüber, spricht man von einer positiven Überraschung, darunter von einer Enttäuschung. Der Vergleich mit dem Vorjahresquartal zeigt das Wachstum. Oft bewegt der Ausblick der Firma den Kurs stärker als die Zahlen selbst, ein Kurs kann also auch nach guten Zahlen fallen."
    override val qzbKeineBewertung = "Für eine Einordnung fehlen den Quellen Schätzungen oder Vorjahreswerte."
    override val qzbVerlauf = "Die letzten Quartale"
    override fun qzbQuartalBis(datum: String) = "Quartal bis $datum"
    override val qzbVonWem = "Woher die Schätzungen kommen"
    override fun qzbQuelleKalender(anbieter: String?, schaetzungen: Int?) = "Ergebniskalender der Nasdaq" + when (anbieter) {
        "finnhub" -> ", ergänzt von Finnhub"
        "alphavantage" -> ", ergänzt von Alpha Vantage"
        else -> ""
    } + (schaetzungen?.let { ": Konsens aus $it Schätzungen" } ?: "")
    override fun qzbQuelleYahoo(gewinn: Int?, umsatz: Int?) = "Yahoo Finance" + listOfNotNull(
        gewinn?.let { "$it Analysten zum Gewinn" }, umsatz?.let { "$it zum Umsatz" },
    ).joinToString(" und ").let { if (it.isEmpty()) "" else ": $it" } + ", dazu die letzten Quartale"
    override fun qzbHaeuser(namen: String) = "Häuser, die die Aktie in den letzten 90 Tagen eingestuft haben: $namen"
    override val qzbVonWemText = "Die Schätzungen stammen von Analysten der Banken und Researchhäuser. Datendienste sammeln sie und bilden den Mittelwert, den Konsens. Die Dienste zählen unterschiedlich viele Analysten, darum weichen ihre Werte leicht voneinander ab."
    override val qzbYahooFehlt = "Yahoo Finance liefert gerade keine weiteren Schätzungen."
    // 0.68 Finanzen
    override val finTippHinweis = "Tipp auf einen Begriff erklärt ihn."
    override fun finErklaerung(label: String): String? = when (label) {
        revenue -> "Alle Einnahmen aus Waren und Diensten, vor Kosten."
        grossProfit -> "Umsatz minus die direkten Kosten der Ware."
        operatingIncome -> "Gewinn aus dem laufenden Geschäft, vor Zinsen und Steuern."
        ebitda -> "Gewinn vor Zinsen, Steuern und Abschreibungen."
        netIncome -> "Was nach allen Kosten, Zinsen und Steuern bleibt. Negativ heißt Verlust."
        totalAssets -> "Alles, was der Firma gehört."
        totalLiabilities -> "Alles, was die Firma anderen schuldet."
        equity -> "Bilanzsumme minus Verbindlichkeiten. Gehört rechnerisch den Eigentümern."
        finLangfristSchulden -> "Kredite und Anleihen mit mehr als einem Jahr Laufzeit."
        operatingCashflow -> "Geld, das aus dem laufenden Geschäft wirklich hereinkommt."
        finInvestitionen -> "Ausgaben für Gebäude, Maschinen und Geräte. Meist negativ, weil Geld abfließt."
        earningsGrowth -> "Gewinn im letzten Quartal gegenüber dem Vorjahresquartal."
        finDivJeAktie -> "Dividenden je Aktie der letzten zwölf Monate."
        toolDividendGrowth -> "Mittlerer jährlicher Anstieg der Dividende je Aktie."
        finDivExTag -> "Wer die Aktie am Tag davor hält, bekommt die nächste Dividende."
        finDivZahltag -> "Tag, an dem die nächste Dividende ausgezahlt wird."
        actual -> "Gemeldeter Gewinn je Aktie im Quartal."
        estimate -> "Gewinn je Aktie, den Analysten im Mittel erwartet hatten."
        finAbweichung -> "Gemeldet gegen erwartet in Prozent. Dreieck nach oben heißt übertroffen, nach unten verfehlt. Bis 2 % ohne Farbe."
        else -> null
    }
    override val finUmsatzSchaetzungErklaerung = "Von Analysten erwarteter Umsatz im nächsten Quartal. Prozent gegenüber dem Vorjahresquartal."
    override val finQuartal = "Quartal"
    override val finJahr = "Jahr"
    override val finAbweichung = "Abweichung"
    // 0.68 Stimmung und Lager
    override val sgKurz = "• Jeder Teil von 0 bis 100, 100 heißt viel Gier\n" +
        "• Unter 25 extreme Angst, bis 45 Angst, bis 55 neutral, bis 75 Gier, darüber extreme Gier\n" +
        "• Extreme Werte heißen: Stimmung einseitig. Wann sie kippt, zeigen sie nicht"
    override val sgRechnungTitel = "So wird gerechnet"
    override val sgRechnung = "• Meist zählt der Rang des heutigen Werts unter seinen eigenen Werten der Vergangenheit, sonst feste Grenzen\n" +
        "• Verwandte Maße bilden eine Gruppe und zählen nicht doppelt\n" +
        "• Jede Gruppe hat ein festes Gewicht, das sich ihre Teile teilen\n" +
        "• Fehlt ein Teil oder eine Gruppe, zählen die übrigen entsprechend mehr"
    override val sgBarometerTitel = "Was die Barometer messen"
    override val sgTeileTitel = "Alle Teile im Einzelnen"
    override fun fiRichtung(schluessel: String, mehr: Boolean) =
        if (schluessel == "duerre") (if (mehr) "mehr Dürre →" else "← weniger Dürre") else if (mehr) "voller →" else "← knapper"
    override fun fiLegendeSchnitt(wert: String) = "Schnitt der Vorjahre: $wert"
    override fun fiLegendeStand(datum: String, wert: String) = "Stand $datum: $wert"
    override fun smMarkt(symbol: String) = when (symbol) {
        "CL=F" -> "WTI-Öl"; "RB=F" -> "Benzin (RBOB)"; "HO=F" -> "Heizöl"; "NG=F" -> "US-Gas (Henry Hub)"; "TTF=F" -> "Gas TTF"
        "ZC=F" -> "Mais"; "ZW=F" -> "Weizen"; "ZS=F" -> "Soja"; "JETS" -> "Airline-Aktien gegen S&P 500"; else -> symbol
    }
    override fun smSeit(datum: String, markt: String, bewegung: String) = "Seit der Meldung um den $datum: $markt $bewegung"
    override fun smLauf(lauf: Int) = when (lauf) {
        0 -> "In Richtung der Meldung gelaufen, wohl teils eingepreist"
        1 -> "Gegen die Meldung gelaufen"
        2 -> "Kaum bewegt"
        else -> "Meldung ohne klare Richtung"
    }
    override val smNichtMessbar = "Nicht messbar, kein passender Marktpreis"
    override val smKeineKurse = "Gerade nicht messbar, Kurse fehlen"
    override fun smNochNicht(datum: String) = "Noch kein Handelstag seit der Meldung um den $datum"

    // 0.68 Strategietest
    override fun slRegelKurz(art: String, a: Int, b: Int, n: Int) = when (art) {
        "HALTEN" -> "Kaufen und Halten"
        "SMA" -> "$a-Tage-Linie"
        "KREUZ" -> "Kreuz $a/$b"
        "RSI" -> if (n == 14) "RSI $a/$b" else "RSI $n Tage $a/$b"
        "MACD" -> "MACD-Signal"
        "DONCHIAN" -> "Ausbruch $a/$b"
        "BOLLINGER" -> "Bollinger"
        else -> art
    }

    // 0.68 Suchverlauf
    override val svKuerzlich = "Kürzlich gesucht"
    override val svLoeschen = "Verlauf löschen"
    override fun svEntfernen(name: String) = "$name aus dem Verlauf entfernen"

    // 0.68 Tagesrueckblick und Mitteilungen
    override val rbUhrzeit = "Uhrzeit"
    override val rbUhrzeitHinweis = "Am Morgen fasst er den Vortag zusammen."
    override fun rbEinleitungDepot(ton: String, hoch: Boolean, prozent: String) = "$ton: Dein Depot " + (if (hoch) "stieg" else "fiel") + " um $prozent"
    override val rbVeraenderung = "Veränderung"
    override val rbWert = "Wert"
    override val mtAlle = "Alle Mitteilungen"
    override fun mtProfil(an: Boolean, aktiv: Int, gesamt: Int) = if (!an) "Aus" else "$aktiv von $gesamt Arten an"
    override val mtAlleAus = "Alle Mitteilungen sind aus."
    override val mtDepot = "Depot und Orders"
    override val mtDepotHinweis = "Orders, Sparpläne, Knock-outs, Wetten, Dividenden, Zinsen."
    override val mtAlarme = "Kurse und Alarme"
    override val mtAlarmeHinweis = "Preisalarme, Finder-Alarme, teurere Fonds, Anlagethesen."
    override val mtNewsHinweis = "Quellen wählst du auf der Nachrichtenseite."
    override val mtCopy = "Insider, Politik und Behörden"
    override val mtCopyHinweis = "Personen mit Glocke im Copytrading."

    // 0.68 ipo
    override fun ipoSchritt(i: Int) = listOf("Eingereicht", "Preis fest", "Handel").getOrElse(i) { "" }
    override val ipoSchrittZurueck = "Zurückgezogen"
    override val ipoPreisSpanne = "Preisspanne"
    override val ipoSpanneMitte = "Mitte"
    override val ipoSeitAusgabeTitel = "Seit Ausgabe"
    override fun ipoKursJetzt(p: String) = "Kurs $p"
    override fun ipoAusgabe(p: String) = "Ausgabe $p"
    override fun ipoPunktKurz(merkmal: Int) = if (merkmal == 0) "Mantelgesellschaft (SPAC)" else "Kleines Angebot"
    override fun ipoPunktErklaerung(merkmal: Int) = if (merkmal == 0) "Sie sammelt Geld, um später eine noch unbekannte Firma zu kaufen. Viele SPAC-Aktien fielen nach der Übernahme deutlich."
        else "Solche Werte finden wenig Beachtung und schwanken oft stark."
    override val ipoSchlagzeilen = "Schlagzeilen"
}
