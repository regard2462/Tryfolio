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
    override val loading = "Wird geladen …"
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
    override val widgetGesperrt = "Gesperrt"
    override val widgetLeer = "Keine Werte in der Watchlist"
    override fun widgetStand(zeit: String) = "Stand $zeit"
    override val widgetsTitel = "Widgets"
    override val widgetsText = "• Hinzufügen: lange auf eine Kategorie drücken › Als Widget, oder lange auf den Startbildschirm drücken › Widgets › Tryfolio.\n• Einstellen: lange auf das Widget drücken › Widget einstellen."
    override val widgetsAuswahl = "Werte im Widget mit der Quelle Watchlist (ohne Auswahl die ganze Watchlist)"
    override val widgetsNurAndroid = "Widgets für den Startbildschirm gibt es in der App für Android."
    override val screenshotsTitel = "Screenshots"
    override fun screenshotsOption(modus: String) = when (modus) { "erlaubt" -> "Überall erlaubt"; "gesperrt" -> "Überall gesperrt"; else -> "Erlaubt, außer bei Schlüsseln" }
    override fun screenshotsHinweis(modus: String) = when (modus) {
        "erlaubt" -> "Auch auf der Seite mit den API-Schlüsseln, die dort sichtbar sind. Danach wieder sperren."
        "gesperrt" -> "Keine Screenshots, keine Bildschirmaufnahmen, kein Bild in der App-Übersicht."
        else -> "Vorgabe: Screenshots überall, nur nicht auf der Seite mit den API-Schlüsseln."
    }
    override val widgetBestand = "Depot-Positionen"
    override val widgetMover = "Top & Flop heute"
    override val widgetsAkku = "Aktualisieren sich Widgets bei geschlossener App nicht, bremst die Akku-Optimierung. In den App-Einstellungen unter Akku „Nicht eingeschränkt“ wählen, bei Samsung die App auch aus den Listen schlafender Apps nehmen."
    override val widgetsAkkuOeffnen = "App-Einstellungen öffnen"
    override val widgetDividenden = "Dividenden"
    override val widgetKeinePositionen = "Keine Positionen im Depot"
    override val widgetMoverRuhig = "Heute noch keine Bewegung"
    override val widgetDivKeinBestand = "Keine Aktien oder ETFs im Depot"
    override val widgetDivKeine = "Keine Dividenden in den nächsten zwölf Monaten erwartet"
    override fun widgetDivErhalten(jahr: Int) = "Erhalten $jahr"
    override val widgetDivErwartet = "Erwartet in 12 Monaten"
    override val widgetGrundDafuer = "dafür"
    override val widgetGrundDagegen = "dagegen"
    override val widgetFinderLeer = "Im Aktienfinder einmal einordnen"
    override val widgetCopyLeer = "Niemandem gefolgt, Rangliste noch nicht berechnet"
    override val widgetOrdersLeer = "Keine offenen Orders und keine Sparpläne"
    // Seit 0.77: Widgets zu Unterseiten und Werkzeugen
    override val widgetBlasenTitel = "Blasen-Gefahr"
    override fun widgetBlasenStufe(stufe: Int) = listOf("Gering", "Erhöht", "Hoch").getOrElse(stufe) { "" }
    override fun widgetBlasenRot(rot: Int, gesamt: Int) = "$rot von $gesamt Anzeichen rot"
    override val widgetTermineTitel = "Termine"
    override val widgetTermineLeer = "Keine Termine in den nächsten Tagen"
    override fun widgetFwHaupt(rot: Int, gelb: Int, n: Int) = when {
        rot == 0 && gelb == 0 -> "Alle $n grün"
        rot == 0 -> "$gelb gelb"
        gelb == 0 -> "$rot rot"
        else -> "$rot rot · $gelb gelb"
    }
    override fun widgetPhase(art: String, korrektur: Boolean) = when {
        art == "BAER" -> "Bärenmarkt"
        korrektur -> "Korrektur"
        else -> "Bullenmarkt"
    }
    override fun widgetLageAuffaellig(n: Int) = if (n == 0) "Nichts auffällig" else "$n auffällig"
    override val widgetEinJahr = "1 Jahr"
    override val widgetZinsfuss = "Zinsfuß pro Jahr"
    override val widgetStufeKritisch = "Kritisch"
    override val widgetStufeAchtung = "Achtung"
    override val widgetCheckGruen = "im grünen Bereich"
    override val widgetKoerbePlus = "Themen im Plus"
    override val widgetSektorenPlus = "Sektoren im Plus"
    override fun widgetFlussZaehlung(kaeufe: Int, verkaeufe: Int) = (if (kaeufe == 1) "1 Kauf" else "$kaeufe Käufe") + ", " + (if (verkaeufe == 1) "1 Verkauf" else "$verkaeufe Verkäufe")
    override val widgetFlussNetto = "Netto, 90 Tage"
    override val widgetFlussLeer = "Keine Geschäfte"
    override val widgetStillhalterLeer = "Erst „Optionen verkaufen“ in der Suche öffnen"
    override val widgetSignaleLeer = "Keine Marker"
    override val widgetVolaLeer = "Keine Optionsdaten"
    override val widgetWertWaehlen = "Wert in den Einstellungen wählen"
    override val widgetGroessteAbweichung = "Größte Abweichung"
    override val widgetPlaeneLeer = "Keine Sparpläne"
    override val widgetWettenLeer = "Keine offenen Wetten"
    override val widgetCopyNiemand = "Noch niemand gefolgt"
    override val widgetCopyRegierung = "Regierung in der App öffnen"
    override val widgetSeite = "Seite"
    override val widgetAktuellesUniversum = "Zuletzt geladenes Universum"
    override val widgetDurchbruchLeer = "Keine neuen Durchbrüche"
    override val widgetRendite = "Rendite"
    override val widgetRueckgang = "Größter Rückgang"
    override val widgetWissenFertig = "Alle Lektionen abgeschlossen"
    override fun widgetWissenMinuten(n: Int) = "$n Min."
    override fun widgetWissenModul(nr: Int, titel: String) = "Modul $nr · $titel"
    override fun widgetWissenStand(fertig: Int, gesamt: Int) = "$fertig von $gesamt Lektionen"
    override fun widgetManuell(name: String) = "Dein Startbildschirm legt keine Widgets an. Lange auf den Startbildschirm drücken, dann Widgets › Tryfolio › $name."
    override val widgetsHinzufuegen = "Widget hinzufügen"
    override val widgetEinzelName = "Einzelwert"
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
    override val widgetUntertitel = "Untertitel"
    override val widgetAnzeige = "Anzeige"
    override val widgetBetrag = "Änderung als Betrag"
    override val widgetBarometer = "Barometer"
    override val widgetAlle = "Alle"
    override val widgetGlobusEinfaerbung = "Einfärbung"
    override val widgetGlobusMitte = "Blickmitte"
    override val widgetMitteEuropa = "Europa"
    override val widgetMitteAmerika = "Amerika"
    override val widgetMitteAsien = "Asien"
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
    override val sperreText = "Schützt die App mit PIN oder Passwort. Depot, Buchungen, Orders, Sparpläne, Watchlist, Preismarken und Notizen liegen dann mit AES-256-GCM verschlüsselt auf dem Gerät. Die App sperrt sich beim Start und nach einer Minute im Hintergrund. Einstellungen und öffentliche Daten wie Kurse sind nicht verschlüsselt."
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
    override val chartVergleichVoll = "Es lassen sich höchstens 8 Werte vergleichen."
    override val chartIndikatorenKurs = "Im Kurschart"
    override val chartIndikatorenUnten = "Unter dem Chart"
    override fun chartIndikatorName(key: String) = when (key) {
        "trend" -> "Trend aus Hochs und Tiefs"
        "sma20" -> "Ø 20 (SMA)"; "sma50" -> "Ø 50 (SMA)"; "sma200" -> "Ø 200 (SMA)"; "ema20" -> "EMA 20"; "ema50" -> "EMA 50"
        "bb" -> "Bollinger-Bänder"; "keltner" -> "Keltner-Kanal"; "donchian" -> "Donchian-Kanal"; "ichimoku" -> "Ichimoku"
        "sar" -> "Parabolic SAR"; "vwap" -> "VWAP"; "volumen" -> "Volumen"; "rsi" -> "RSI 14"; "macd" -> "MACD"
        "stoch" -> "Stochastik"; "atr" -> "ATR 14"; "obv" -> "OBV"; "cci" -> "CCI 20"; "williams" -> "Williams %R"
        "mfi" -> "MFI 14"; "adx" -> "ADX 14"; "roc" -> "Momentum (ROC 12)"; "aroon" -> "Aroon 25"; "cmf" -> "Chaikin Money Flow"
        "squeeze" -> MarktzeichenTexte.text(true, "sq.name", emptyList())
        else -> key
    }
    override fun chartIndikatorInfo(key: String) = when (key) {
        "trend" -> "Hochs und Tiefs ab zwei mittleren Spannen (ATR). Steigen beide, läuft ein Aufwärtstrend. Die Linie zeigt, wo er bricht."
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
        "squeeze" -> MarktzeichenTexte.text(true, "sq.info", emptyList())
        else -> ""
    }
    override fun chartWerkzeug(key: String) = when (key) {
        "linie" -> "Trendlinie"; "waagerecht" -> "Waagerechte"; "notiz" -> "Notiz"; "emoji" -> "Marke"
        "zurueck" -> "Rückgängig"; else -> "Alle löschen"
    }
    override fun chartAllesLoeschen(name: String) = "Alle Zeichnungen für $name löschen?"
    override val chartKeineDaten = "Keine Kursdaten für diesen Zeitraum."
    override val heatmap = "Heatmap"
    override val heatmapDepot = "Mein Depot"
    override val heatmapMarkt = "Aktienmarkt"
    override val heatmapBranchen = "Nach Branche"
    override val heatmapRegionen = "Nach Region"
    override fun heatmapSteigen(n: Int) = "$n steigen"
    override fun heatmapFallen(n: Int) = "$n fallen"
    override val heatmapLeer = "Gerade keine Kurse erreichbar. Später noch einmal versuchen."
    override val globusTitel = "Globus"
    // [Globus Gesamtbild 0.70]
    override val globusGesamt = "Gesamtbild"
    override val ggTitel = "Gesamtbild"
    override fun ggPunkteVon(punkte: Int) = "$punkte von 100"
    override fun ggPlatz(platz: Int, von: Int) = "Platz $platz von $von"
    override fun ggBereich(b: de.troetenrepublik.app.engine.GlobusGesamt.Bereich) = when (b) {
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.WOHLSTAND -> "Wohlstand"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.WACHSTUM -> "Wachstum"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.STABILITAET -> "Stabilität"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.REGIERUNG -> "Regierungsführung"
        de.troetenrepublik.app.engine.GlobusGesamt.Bereich.SICHERHEIT -> "Sicherheit"
    }
    override fun ggZutat(z: de.troetenrepublik.app.engine.GlobusGesamt.Zutat) = when (z.art) {
        de.troetenrepublik.app.engine.GlobusGesamt.Art.BIP_KOPF -> "BIP je Kopf"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.LEBENSERWARTUNG -> "Lebenserwartung"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.WACHSTUM -> "Ø Wachstum ${z.von} bis ${z.bis}"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.INFLATION -> "Inflation"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.SCHULDEN -> "Staatsschulden"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.HAUSHALT -> "Haushaltssaldo"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.ARBEITSLOS -> "Arbeitslosigkeit"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.LEISTUNG -> "Leistungsbilanz"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.WGI -> rkWgiName(z.kennung)
        de.troetenrepublik.app.engine.GlobusGesamt.Art.KRIEG -> "Krieg im Land"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.KONFLIKT -> "Bewaffneter Konflikt im Land"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.SANKTION_EU -> "Sanktionsregime der EU"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.SANKTION_US -> "Sanktionsprogramme der USA"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.REISE -> "Reisehinweis der USA, Stufe ${z.wert.toInt()}"
        de.troetenrepublik.app.engine.GlobusGesamt.Art.KEINE_ABZUEGE -> "Keine Abzüge"
    }
    override fun ggDesBip(wert: String) = "$wert des BIP"
    override val ggInfo = "Punkte von 0 bis 100, gewichtetes Mittel aus fünf Bereichen: Wohlstand und Regierungsführung je 25 Prozent, Stabilität und Sicherheit je 20, Wachstum 10.\n• Wohlstand: BIP je Kopf in Kaufkraft, Lebenserwartung\n• Wachstum: reales Wachstum, Mittel von zwei Jahren davor bis zwei Jahre danach\n• Stabilität: Inflation, Staatsschulden, Haushaltssaldo, Arbeitslosigkeit, Leistungsbilanz\n• Regierungsführung: sechs Weltbank-Indikatoren\n• Sicherheit: Abzüge für Krieg oder Konflikt im Land, Sanktionen der EU und der USA, Reisehinweis der USA\n\nFehlt ein Bereich, zählt er mit dem Mittel der anderen, höchstens mit 70 Punkten. Ein Land braucht mindestens drei Bereiche, darunter einen der ersten drei, sonst gibt es keine Punkte. Mit genau drei Bereichen zählen höchstens 70 Punkte.\n\nFehlen IWF-Zahlen, gelten die jüngsten Werte von Weltbank, UN oder CIA World Factbook, höchstens fünf Jahre alt und mit Jahr.\n\nDer Jahresregler wirkt auf die IWF-Zahlen. Regierungsführung, Sicherheit und Lebenserwartung zeigen immer den letzten Stand."
    override val globusBoerse = "Börse heute"
    override val globusWachstum = "Wachstum"
    override val globusAusblick = "Ausblick"
    override val globusInflation = "Inflation"
    override val globusKeineDaten = "Keine Daten"
    override val globusFehlt = "IWF-Daten nicht geladen."
    override val globusFehltTeil = "IWF-Daten unvollständig geladen."
    override val globusNeuLaden = "Erneut laden"
    override val boerseMoskau = "Moskauer Börse"
    override fun globusWachstumJahr(jahr: Int) = "Wachstum $jahr"
    override fun globusInflationJahr(jahr: Int) = "Inflation $jahr"
    override val globusArbeitslos = "Arbeitslosigkeit"
    override val globusSchulden = "Staatsschulden"
    override val globusLeistung = "Leistungsbilanz"
    override fun globusHinweis(art: de.troetenrepublik.app.engine.Imf.Art, a: String, b: String) = when (art) {
        de.troetenrepublik.app.engine.Imf.Art.REZESSION -> "Rezession erwartet: $a"
        de.troetenrepublik.app.engine.Imf.Art.REZESSION_FOLGEJAHR -> "Rezession im nächsten Jahr erwartet: $a"
        de.troetenrepublik.app.engine.Imf.Art.ZIEHT_AN -> "Wachstum zieht an: $a auf $b"
        de.troetenrepublik.app.engine.Imf.Art.SCHWAECHT_AB -> "Wachstum schwächt ab: $a auf $b"
        de.troetenrepublik.app.engine.Imf.Art.STAGNATION -> "Stagnation: $a Wachstum"
        de.troetenrepublik.app.engine.Imf.Art.DEFLATION -> "Fallende Preise: $a"
        de.troetenrepublik.app.engine.Imf.Art.HOHE_INFLATION -> "Hohe Inflation: $a"
        de.troetenrepublik.app.engine.Imf.Art.ERHOEHTE_INFLATION -> "Erhöhte Inflation: $a"
        de.troetenrepublik.app.engine.Imf.Art.HOHE_ARBEITSLOSIGKEIT -> "Hohe Arbeitslosigkeit: $a"
        de.troetenrepublik.app.engine.Imf.Art.HOHE_SCHULDEN -> "Hohe Staatsschulden: $a des BIP"
        de.troetenrepublik.app.engine.Imf.Art.UEBERSCHUSS -> "Leistungsbilanzüberschuss: $a des BIP"
        de.troetenrepublik.app.engine.Imf.Art.DEFIZIT -> "Leistungsbilanzdefizit: $a des BIP"
    }
    override val globusWelt = "Weltwirtschaft"
    override val wwBalkenTitel = "Weltwachstum je Jahr"
    override val wwPrognose = "Prognose"
    override fun wwSchnittLabel(bis: Int) = "Ø pro Jahr bis $bis"
    override fun wwGesamtLabel(bis: Int) = "Zuwachs bis $bis"
    override fun wwSchnittInfo(von: Int, bis: Int) = "Durchschnittliches Wachstum der Weltwirtschaft pro Jahr von ${von + 1} bis $bis laut IWF-Prognose (reale Wirtschaftsleistung ohne Preisanstieg)."
    override fun wwGesamtInfo(von: Int, bis: Int) = "Um so viel Prozent wächst die reale Weltwirtschaft von $von bis $bis, wenn die Prognose eintrifft. Die Jahreswerte werden aufeinander aufgesetzt."
    override val wwTreiberTitel = "Begründung des IWF"
    override val wwStand = "IWF-Ausblick Oktober 2026"
    override val wwTragenTitel = "Wer das Wachstum trägt"
    override fun wwTragenInfo(von: Int, bis: Int, welt: String) =
        "Beitrag eines Raums zum Weltwachstum: sein Wachstum mal sein Anteil an der Weltwirtschaftsleistung (Kaufkraft), im Mittel der Jahre ${von + 1} bis $bis, in Prozentpunkten pro Jahr.\n\nZusammen ergibt das den Schnitt der Welt von $welt pro Jahr."
    override val wwUebrige = "Übrige"
    override fun wwBeschleunigt(raum: String, nun: String, frueher: String) = "$raum beschleunigt: Ø $nun statt $frueher in den letzten fünf Jahren"
    override fun wwSchwaecht(raum: String, nun: String, frueher: String) = "$raum schwächt sich ab: Ø $nun statt $frueher in den letzten fünf Jahren"
    override val wwAufsteigerTitel = "Aufsteiger"
    override val wwRezTitel = "Rezession"
    override val wwTreiber = listOf(
        "Krieg in Westasien: Energie bleibt teuer (Öl im Schnitt 96 Dollar je Barrel 2026), Hormus gilt in der Prognose als wieder offen",
        "KI-Investitionen und Produktivitätsgewinne stützen das Wachstum, Länder in der Tech-Lieferkette profitieren",
        "Energieimporteure außerhalb der Tech-Kette haben die schwächsten Aussichten",
        "Risiken: weitere Eskalation, Neubewertung der KI, neue Handelsstreitigkeiten, hohe Staatsschulden",
    )
    override val wwQuelle = "IWF World Economic Outlook, Oktober 2026"
    override val wwAnteileTitel = "Anteil am Welt-BIP"
    override val wwAnteileInfo = "Anteil an der weltweiten Wirtschaftsleistung in Kaufkraft (IWF), je Raum für 2000, heute und das letzte Prognosejahr. Der Euroraum fasst die heutigen Mitglieder zusammen, auch für frühere Jahre."
    override val wwEuroraum = "Euroraum"
    override fun wwRaum(code: String, sonst: String) = when (code) {
        "CHN" -> "China"; "USA" -> "USA"; "IND" -> "Indien"; "JPN" -> "Japan"; else -> sonst
    }
    override fun wwVerschiebung(gewinner: String, gewinn: String, verlierer: String?, verlust: String?, bis: Int) =
        "Bis $bis gewinnt $gewinner am meisten Gewicht ($gewinn Punkte)" + (if (verlierer != null && verlust != null) ", am meisten verliert $verlierer ($verlust Punkte)." else ".")
    override fun wwSchnellsteTitel(bis: Int) = "Schnellstes Wachstum bis $bis (Ø pro Jahr)"
    override fun wwPlaetzeTitel(bis: Int) = "Steigen in der Rangliste bis $bis"
    override fun wwPlatz(von: Int, bis: Int) = "Platz $von → $bis"
    override val globusAbschwaecher = "Schwächen sich ab"
    override val globusRisiken = "Risiken"
    override fun globusHoheInflation(n: Int, namen: String) = "Inflation über 10 % in $n Ländern, am höchsten: $namen"
    override fun globusSchuldenSchwach(n: Int, namen: String) = "Schulden über 100 % des BIP bei Wachstum unter 1,5 % in $n Ländern: $namen"
    override val globusFruehwarn = "Frühwarnzeichen (USA)"
    // [Globus Wirtschaft]
    override val globusWirtschaft = "Wirtschaft"
    override val globusStruktur = "Wirtschaftsstruktur"
    override val globusMarkt = "Markt"
    override val globusEtf = "Länder-ETF"
    override val globusEtfHinweis = "Länder-ETFs notieren in US-Dollar, die Änderungen sind Dollar-Änderungen."
    override val globusHeute = "Heute"
    override val globusEinJahr = "1 Jahr"
    override fun globusMio(zahl: String) = "$zahl Mio."
    override fun globusVorjahr(jahr: Int, wert: String) = "Vorjahr ($jahr): $wert"
    override fun globusKurs(code: String, kurs: String) = "1 € = $kurs $code"
    override fun globusWaehrungJahrLabel(code: String) = "$code zum Euro, 1 Jahr"
    override fun globusWirtLabel(k: de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl) = when (k) {
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WACHSTUM -> "Wachstum"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INFLATION -> "Inflation"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ARBEITSLOS -> "Arbeitslosigkeit"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.SCHULDEN -> "Staatsschulden"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEISTUNG -> "Leistungsbilanz"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HAUSHALT -> "Haushaltssaldo"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF -> "BIP je Einwohner"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF_KKP -> "BIP je Einwohner, KKP"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WELT_ANTEIL -> "Anteil am Welt-BIP"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BEVOELKERUNG -> "Bevölkerung"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.AGRAR -> "Landwirtschaft"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INDUSTRIE -> "Industrie"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.VERARBEITUNG -> "davon Verarbeitung"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIENSTE -> "Dienstleistungen"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.EXPORTE -> "Exportquote"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.TREIBSTOFF -> "Brennstoffexporte"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ROHSTOFF -> "Rohstoffrenten"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HIGHTECH -> "Hightech-Exporte"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIREKTINVEST -> "Direktinvestitionen"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BOERSENWERT -> "Börsenwert zum BIP"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.FIRMEN -> "Börsennotierte Firmen"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.GINI -> "Gini-Index"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEITZINS -> "Leitzins"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_EZB -> "Rendite 10 Jahre"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_USA -> "Rendite 10 Jahre"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_FRED -> "Rendite 10 Jahre"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG -> "Währung"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_JAHR -> "Währung, 1 Jahr"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_EURO -> "Währung"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.KREDITZINS -> "Kreditzins"
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.REALZINS -> "Realzins"
    }
    override fun globusWirtInfo(k: de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl) = when (k) {
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WACHSTUM -> "Veränderung der preisbereinigten Wirtschaftsleistung (reales BIP) zum Vorjahr in Prozent.\n\n• Höheres Wachstum stützt tendenziell die Gewinne der Unternehmen im Land.\n• Zahlen ab dem laufenden Jahr sind Prognosen."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INFLATION -> "Anstieg der Verbraucherpreise zum Vorjahr in Prozent, Jahresdurchschnitt.\n\n• Hohe Inflation entwertet Anleihen und Bargeld und treibt die Zinsen.\n• Die EZB und die US-Notenbank streben 2 % an."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ARBEITSLOS -> "Anteil der Arbeitslosen an den Erwerbspersonen in Prozent.\n\n• Hohe Quoten dämpfen den Konsum und senken die Steuereinnahmen.\n• Im EU-Verfahren bei makroökonomischen Ungleichgewichten gilt ein Dreijahresschnitt über 10 % als auffällig."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.SCHULDEN -> "Bruttoschulden des Staates in Prozent der Wirtschaftsleistung.\n\n• Hohe Schulden binden künftige Steuereinnahmen für Zinsen und machen den Staat anfälliger, wenn die Zinsen steigen.\n• Der EU-Vertrag (Maastricht) nennt 60 % als Referenzwert."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEISTUNG -> "Saldo der Geschäfte mit dem Ausland (Waren, Dienste, Einkommen) in Prozent der Wirtschaftsleistung.\n\n• Ein dauerhaftes Defizit heißt Abhängigkeit von ausländischem Kapital und macht die Währung anfälliger.\n• Das EU-Verfahren bei Ungleichgewichten nennt −4 % und +6 % im Dreijahresschnitt als auffällig."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HAUSHALT -> "Überschuss oder Defizit des Staatshaushalts in Prozent der Wirtschaftsleistung.\n\n• Negativ heißt: Der Staat leiht sich Geld.\n• Im Euroraum gilt ein Defizit über 3 % als übermäßig (Maastricht)."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF -> "Wirtschaftsleistung je Einwohner in US-Dollar, zu laufenden Preisen und Wechselkursen. Vergleicht Länder aus Sicht eines Dollar-Anlegers und schwankt mit dem Wechselkurs."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BIP_KOPF_KKP -> "Wirtschaftsleistung je Einwohner nach Kaufkraftparität (KKP): Ein internationaler Dollar kauft in jedem Land dieselbe Menge an Waren und Diensten.\n\nVergleicht den Lebensstandard ohne Wechselkursschwankungen."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WELT_ANTEIL -> "Anteil des Landes an der weltweiten Wirtschaftsleistung in Prozent, nach Kaufkraftparität gerechnet. Zeigt das wirtschaftliche Gewicht des Landes."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BEVOELKERUNG -> "Einwohner in Millionen.\n\n• Eine wachsende Bevölkerung erhöht Arbeitskräfte und Konsum, eine schrumpfende belastet Renten und Wachstum.\n• Werte ab dem laufenden Jahr sind Schätzungen."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.AGRAR -> "Anteil von Land-, Forst- und Fischereiwirtschaft an der Wertschöpfung in Prozent des BIP.\n\n• Hohe Anteile finden sich meist in Ländern mit niedrigem Einkommen.\n• Die drei Anteile ergeben zusammen nicht genau 100 %, weil Gütersteuern fehlen."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.INDUSTRIE -> "Anteil von Bergbau, Verarbeitendem Gewerbe, Bau sowie Strom-, Gas- und Wasserversorgung an der Wertschöpfung in Prozent des BIP.\n\nZeigt, welche Börsensektoren zum Land passen, etwa Industrie und Rohstoffe."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.VERARBEITUNG -> "Anteil der Fabrikproduktion (Verarbeitendes Gewerbe) am BIP in Prozent. Der Wert ist in der Industrie enthalten."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIENSTE -> "Anteil von Handel, Finanzen, Verwaltung, Tourismus und übrigen Dienstleistungen an der Wertschöpfung in Prozent des BIP. Hohe Anteile haben meist reife Volkswirtschaften."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.EXPORTE -> "Ausfuhr von Waren und Diensten in Prozent des BIP. Hohe Werte heißen Abhängigkeit von der Weltkonjunktur, niedrige Werte Ausrichtung auf den Binnenmarkt. Kleine Länder haben strukturell hohe Werte."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.TREIBSTOFF -> "Anteil von Brennstoffen (Öl, Gas, Kohle) an den Warenexporten in Prozent. Hohe Werte machen Wirtschaft und Staatshaushalt vom Rohstoffpreis abhängig."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.ROHSTOFF -> "Renten aus Öl, Gas, Kohle, Mineralien und Wald in Prozent des BIP, also Verkaufserlös abzüglich Förderkosten. Zeigt die Abhängigkeit des Landes von Rohstoffen."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.HIGHTECH -> "Anteil forschungsintensiver Güter (Luft- und Raumfahrt, Computer, Pharma, Messgeräte, Elektrotechnik) an den Industrieexporten in Prozent. Hinweis auf die Innovationskraft der Industrie."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.DIREKTINVEST -> "Netto-Zufluss langfristigen Kapitals ausländischer Unternehmen in Prozent des BIP.\n\n• Negative Werte heißen Nettoabfluss.\n• Kleine Finanzplätze wie Irland oder Luxemburg zeigen verzerrte Werte, weil Kapital nur durchgeleitet wird."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.BOERSENWERT -> "Börsenwert der im Land gelisteten inländischen Firmen in Prozent des BIP. Zeigt die Tiefe des Aktienmarkts. Sinnvoll nur im Vergleich zum eigenen Verlauf und zu ähnlichen Ländern."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.FIRMEN -> "Zahl der an inländischen Börsen gelisteten inländischen Firmen am Jahresende. Zeigt die Breite des Aktienmarkts."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.GINI -> "Ungleichheit der Einkommen oder Ausgaben von 0 (alle gleich) bis 100 (eine Person hat alles).\n\n• Hohe Ungleichheit erhöht das Risiko sozialer Spannungen.\n• Je nach Land stammt der Wert aus Einkommens- oder Konsumerhebungen, deshalb sind Länder nur bedingt vergleichbar.\n• Erhebungen finden nicht jährlich statt."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.LEITZINS -> "Zinssatz der Zentralbank für kurzfristige Geschäfte mit Banken. Sinkende Zinsen stützen Aktien und Kredite, steigende belasten Anleihen. Für Euroländer gilt der Satz der EZB."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_EZB -> "Jährliche Rendite einer zehnjährigen Staatsanleihe des Landes, Monatsdurchschnitt. Ein Aufschlag auf Deutschland ist der Risikoaufschlag, den der Markt verlangt."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_USA -> "Jährliche Rendite einer zehnjährigen US-Staatsanleihe, aktueller Stand. Steigende Renditen belasten Anleihekurse und Aktien mit hoher Bewertung."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_FRED -> "Jährliche Rendite einer zehnjährigen Staatsanleihe des Landes, Monatswert. Ein Aufschlag auf die USA oder Deutschland ist der Risikoaufschlag, den der Markt verlangt."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG -> "Wechselkurs der Landeswährung in Einheiten je Euro."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_JAHR -> "Wertveränderung der Landeswährung gegenüber dem Euro in den letzten zwölf Monaten.\n\n• Positiv heißt: Die Währung hat zum Euro zugelegt.\n• Eine schwache Währung verteuert Importe und Auslandsschulden und hilft Exporteuren."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.WAEHRUNG_EURO -> "Das Land nutzt den Euro. Für Mitglieder des Euroraums setzt die EZB den Leitzins, die Anleiherendite gilt je Land."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.KREDITZINS -> "Zinssatz, den Banken ihren Kunden für Kredite berechnen, in Prozent. Er liegt über dem Leitzins und zeigt die Kreditkosten der Wirtschaft."
        de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.REALZINS -> "Kreditzins der Banken abzüglich Inflation (BIP-Deflator) in Prozent. Negativ heißt: Die Inflation übersteigt den Zins, Kredite sind real günstig und Sparer verlieren."
    }
    // [Globus Trend und Ebenen]
    override val globusPhase = "Konjunkturphase"
    override fun globusIndexEtf(name: String) = "Länder-ETF $name (über ETF, in US-Dollar)"
    override val globusLegBoerseHeute = "Tagesänderung des Leitindex, sonst des Länder-ETFs. Grau: kein Kurs oder älter als vier Handelstage."
    override val globusBoerseZeit = "Börsenzeiten"
    override val globusDepot = "Mein Depot"
    override fun globusJahr(jahr: Int, prognose: Boolean) = if (prognose) "$jahr (Prognose)" else "$jahr"
    override val globusJahrRegler = "Jahr wählen"
    override fun globusJahrZurueck(jahr: Int) = "Zurück auf $jahr"
    override fun trendPhase(p: de.troetenrepublik.app.engine.Fruehindikator.Phase) = when (p) {
        de.troetenrepublik.app.engine.Fruehindikator.Phase.AUFSCHWUNG -> "Aufschwung"
        de.troetenrepublik.app.engine.Fruehindikator.Phase.ABSCHWAECHUNG -> "Abschwächung"
        de.troetenrepublik.app.engine.Fruehindikator.Phase.ABSCHWUNG -> "Abschwung"
        de.troetenrepublik.app.engine.Fruehindikator.Phase.ERHOLUNG -> "Erholung"
    }
    override val globusOffen = "Offen"
    override val globusGeschlossen = "Geschlossen"
    override val globusLegArbeitslos = "Grau bei 10 %, der Schwelle des EU-Verfahrens bei makroökonomischen Ungleichgewichten. Darunter grün, darüber rot. Ohne IWF-Wert: letzte Schätzung der Weltbank (ILO)."
    override val globusLegSchulden = "Grau bei 60 % des BIP, dem Referenzwert von Maastricht. Darunter grün, darüber rot."
    override val globusLegLeistung = "Grün: Überschuss, rot: Defizit. Im EU-Verfahren gelten unter −4 % und über +6 % des BIP als auffällig."
    override val globusLegPhase = "Wo es den OECD-Frühindikator gibt, nach ihm, sonst nach IWF-Wachstum gegenüber dem eigenen Trend und der Prognose für das nächste Jahr. Grau: keine Daten."
    override val globusLegBoerse = "Haupthandelszeit der Leitbörse, jetzt gerechnet. Wochenende geschlossen, Börsenfeiertage nur für USA, Deutschland, Großbritannien, Japan, Frankreich, Niederlande, Belgien und Portugal berücksichtigt. Dunkle Seite: Nacht."
    override val globusLegDepot = "Anteil am Depotwert nach Tätigkeitsland, volle Farbe ab 50 %."
    override fun globusDepotNichtZuordenbar(anteil: String) = "Nicht zuordenbar (ETFs, Krypto u. a.): $anteil"
    override val globusDepotLeer = "Im Depot liegen keine Positionen."
    override fun globusKlumpen(land: String, anteil: String) = "Mehr als die Hälfte des Depotwerts entfällt auf ein Land: $land ($anteil). Konjunktur, Börse und Währung dieses Landes bestimmen dann den größten Teil der Entwicklung."
    // [Globus Depot 0.70]
    override val globusDepotEtfs = "ETFs einrechnen"
    override val globusDepotEtfsInfo = "Länderanteile der ETFs nach ihrem Index, gerundet. Branchen-, Anleihe- und Rohstoff-ETFs bleiben ohne Land."
    override val globusBilanzTitel = "Streuung"
    override val globusBilanzLaender = "Länder"
    override val globusBilanzGroesstes = "Größtes Land"
    override val globusBilanzHeim = "Heimat"
    override val globusBilanzIndustrie = "Industrieländer"
    override val globusBilanzSchwellen = "Schwellenländer"
    override val globusBilanzKonz = "Konzentration"
    override val globusBilanzWelt = "Strich: Weltindex (MSCI ACWI)"
    override fun globusBilanzZugeordnet(anteil: String) = "Bilanz über $anteil des Depots, der Rest hat kein Land."
    override fun globusBilanzRegion(r: de.troetenrepublik.app.engine.DepotStreuung.Region) = when (r) {
        de.troetenrepublik.app.engine.DepotStreuung.Region.NORDAMERIKA -> "Nordamerika"
        de.troetenrepublik.app.engine.DepotStreuung.Region.EUROPA -> "Europa"
        de.troetenrepublik.app.engine.DepotStreuung.Region.ASIEN_PAZIFIK -> "Asien-Pazifik"
        de.troetenrepublik.app.engine.DepotStreuung.Region.LATEINAMERIKA -> "Lateinamerika"
        de.troetenrepublik.app.engine.DepotStreuung.Region.NAHOST_AFRIKA -> "Nahost, Afrika"
        de.troetenrepublik.app.engine.DepotStreuung.Region.UEBRIGE -> "Übrige"
    }
    override fun globusBilanzStufe(k: de.troetenrepublik.app.engine.DepotStreuung.Konzentration) = when (k) {
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.GERING -> "gering"
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.MAESSIG -> "mäßig"
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.HOCH -> "hoch"
        de.troetenrepublik.app.engine.DepotStreuung.Konzentration.SEHR_HOCH -> "sehr hoch"
    }
    override fun globusBilanzEffektiv(n: String) = "Wie $n gleich große Länder. Der Weltindex liegt bei etwa 2,2, weil die USA über 60 % haben."
    override val globusHerkunftTitel = "Zuordnung nach Tätigkeit"
    override val globusHerkunftInfo = "Gezählt wird das Land, in dem eine Firma tätig ist.\n\nEin Sitz in einem Holding- oder Registerland (etwa Mauritius, Kaimaninseln, Bermuda, Luxemburg, Zypern) zählt nicht, dann gilt das Tätigkeitsland, sonst das Land der Hauptbörse."
    override fun globusHerkunft(sitz: String, taetig: String, boerse: String?) = "Tätig in $taetig, Sitz $sitz" + (boerse?.let { ", Börse $it" } ?: "")
    override val globusAlleAktien = "Alle Aktien"
    override val kursquelleAntwortetNicht = "Die Kursquelle antwortet nicht. Ein VPN oder ein Netzfilter kann der Grund sein."
    override fun globusWeltweit(zentrale: String) = "Weltweit tätig, Zentrale in $zentrale"
    override val globusWenigerAktien = "Weniger Aktien"
    override fun globusLandFuer(name: String) = "Land für $name"
    override val globusLandAutomatisch = "Automatisch zuordnen"
    override val globusLandGewaehlt = "von dir festgelegt"
    override val globusLandUnbekannt = "Land unbekannt"

    override fun globusRangliste(ebene: String) = "Rangliste: $ebene"
    override val globusHoechste = "Höchste Werte"
    override val globusNiedrigste = "Niedrigste Werte"
    override val globusRangGesamt = "Stärkste Länder insgesamt"
    override val globusRangGesamtInfo get() = ggInfo + "\n\nRangfolge nach dem Gesamtbild des gewählten Jahres."
    override val globusRangGebiete = "mit Gebieten"
    override fun globusRangAlle(n: Int) = "Alle Länder ($n)"
    override val rezTitel = "Rezessionszeichen"
    override val rezTitelWachstum = "Länder mit Rezession"
    override val rezAbschwung = "Abschwung laut OECD"
    override fun rezAbschwungWert(n: Int, von: Int, anteil: String) = "$n von $von ($anteil)"
    override val rezZins = "Zinskurve USA"
    override val rezSahm = "Sahm-Regel USA"
    override val rezWahrsch = "Rezession jetzt (USA)"
    override val rezInfoWachstum = "Zählt die Länder, deren reales BIP im laufenden Jahr laut IWF-Prognose schrumpft.\n\n• Der Anteil an der Wirtschaftsleistung gewichtet nach BIP in Dollar.\n• Je mehr Länder und je größere, desto breiter die Schwäche.\n• Schwelle ist null Prozent Wachstum."
    override val rezInfoAbschwung = "Anteil der Länder, deren OECD-Frühindikator unter 100 liegt und gegenüber dem Vormonat fällt (Phase Abschwung).\n\n• Gezählt werden nur die Länder, die die OECD erfasst (große Volkswirtschaften), mit Werten der letzten sechs Monate.\n• Alle Länder zeigt die Zeile darunter.\n• Der Frühindikator nimmt die Konjunktur etwa sechs bis neun Monate vorweg."
    override val rezInfoZins = "Rendite zehnjähriger minus Rendite dreimonatiger US-Staatsanleihen in Prozentpunkten, täglich.\n\n• Unter null ist die Kurve invers.\n• Vor jeder US-Rezession seit 1968 lag sie eine Zeit lang unter null, mit einem Vorlauf von etwa 6 bis 24 Monaten.\n• Auf nicht jede Inversion folgte eine Rezession."
    override val rezInfoWahrsch = "Geschätzte Wahrscheinlichkeit, dass sich die US-Wirtschaft in einer Rezession befindet, in Prozent, monatlich.\n\n• Ein Modell von Chauvet und Piger rechnet sie aus Beschäftigung, Industrieproduktion, Realeinkommen und Umsätzen.\n• Der Wert liegt mehrere Wochen zurück.\n• Eine feste Schwelle gibt es nicht."
    override val trendTitel = "Trend"
    override val trendFrueh = "Frühindikator"
    override val trendPhaseLabel = "Konjunkturphase"
    override val trendFruehInfo = "Composite Leading Indicator (CLI) der OECD, amplitudenbereinigt, monatlich.\n\n• Der Index aus Umfragen und Marktdaten nimmt die Konjunktur etwa sechs bis neun Monate vorweg.\n• Der Langzeitdurchschnitt liegt bei 100: Darüber wird eine Wirtschaft über ihrem Trend erwartet, darunter unter ihrem Trend.\n• Die Richtung gegenüber dem Vormonat zählt mehr als die Höhe."
    override val trendPhaseInfo = "Phase des Konjunkturzyklus, auf zwei Wegen mit denselben vier Phasen.\n\nVorrang hat der OECD-Frühindikator (monatlich, aktueller), nach Höhe über oder unter 100 und Richtung zum Vormonat.\n\nFür Länder ohne OECD-Wert gilt das IWF-Wachstum: Wachstum des laufenden Jahres über oder unter dem Median der letzten zehn Jahre des Landes (ohne 2020), Richtung nach der IWF-Prognose für das nächste Jahr.\n\nDie Phasen:\n• Aufschwung (Expansion): über 100, steigend\n• Abschwächung (Slowdown): über 100, fallend\n• Abschwung (Downturn): unter 100, fallend\n• Erholung (Recovery): unter 100, steigend"
    override val trendEsi = "Wirtschaftsstimmung (EU)"
    override val trendEsiInfo = "Economic Sentiment Indicator der EU-Kommission: Index aus Umfragen bei Industrie, Dienstleistern, Handel, Bau und Verbrauchern.\n\n• Der langjährige Durchschnitt liegt bei 100. Darüber ist die Stimmung überdurchschnittlich, darunter unterdurchschnittlich.\n• Steigt der Wert mehrere Monate, zieht die Stimmung an."
    override val trendBoerse = "Börse"
    override fun trendOffenBis(zeit: String) = "offen bis $zeit"
    override fun trendOeffnet(zeit: String) = "öffnet $zeit"
    override val trendVerkuerzt = "Verkürzter Handelstag"
    override fun trendBoerseInfo(name: String, zeiten: String, zone: String) = "$name, Haupthandelszeit $zeiten in Ortszeit der Börse ($zone).\n\n• Gezeigt wird der Zeitpunkt in deiner Zeitzone.\n• Am Wochenende ist geschlossen.\n• Feiertage: USA, Deutschland, Großbritannien, Japan, Paris, Amsterdam, Brüssel, Lissabon.\n• Verkürzte Handelstage: USA."
    override val trendVergleichen = "Vergleichen mit"
    override val trendLandWaehlen = "Land wählen"
    override val trendSuche = "Land suchen"
    override val trendVergleich = "Vergleich"
    override fun trendPhaseCli(monat: String) = "aus OECD-Frühindikator, $monat"
    override fun trendPhaseImf(jahr: Int) = "aus IWF-Wachstum $jahr/${jahr + 1}"
    override val rezPhaseWelt = "Abschwung weltweit"
    override fun rezPhaseWeltWert(n: Int, von: Int, anteil: String, bip: String?) = "$n von $von ($anteil)"
    override val rezInfoPhaseWelt = "Anteil aller Länder mit Phase Abschwung (unter Trend und fallend) und ihr Anteil am Welt-BIP in Dollar.\n\nDie Phase kommt vom OECD-Frühindikator, wo es ihn gibt, sonst aus dem IWF-Wachstum des laufenden Jahres gegenüber dem Median der letzten zehn Jahre des Landes (ohne 2020) und der Prognose für das nächste Jahr."
    // [Globus Risiken]
    override val rkRegierung = "Regierungsführung"
    override fun rkWgiName(k: String) = when (k) {
        "PV" -> "Politische Stabilität"; "GE" -> "Regierungseffektivität"; "RQ" -> "Regulierungsqualität"
        "RL" -> "Rechtsstaatlichkeit"; "CC" -> "Korruptionskontrolle"; else -> "Mitsprache, Freiheit"
    }
    override fun rkWgiInfo(k: String) = when (k) {
        "PV" -> "Wie wahrscheinlich politische Instabilität und politisch motivierte Gewalt einschließlich Terrorismus sind."
        "GE" -> "Qualität der öffentlichen Dienste und der Verwaltung, ihre Unabhängigkeit von politischem Druck und die Glaubwürdigkeit der Regierungspolitik."
        "RQ" -> "Fähigkeit der Regierung, Politik und Vorschriften zu gestalten, die den Privatsektor fördern."
        "RL" -> "Vertrauen in die Regeln der Gesellschaft und ihre Einhaltung, vor allem bei Verträgen, Eigentumsrechten, Polizei und Gerichten."
        "CC" -> "Ausmaß, in dem öffentliche Macht zum privaten Vorteil genutzt wird, von kleiner bis großer Korruption."
        else -> "Ausmaß, in dem Bürger ihre Regierung mitwählen können, sowie Meinungs-, Versammlungs- und Medienfreiheit."
    } + "\n\nDas Perzentil nennt den Anteil der Länder, die schlechter abschneiden (0 bis 100, höher ist besser).\n\nDie Werte sind Schätzungen aus Umfragen und Expertenurteilen mit Unsicherheitsband, kleine Abstände zwischen Ländern sind nicht belastbar."
    override val rkReise = "Reisehinweise"
    override val rkUsLabel = "US-Reisehinweis"
    override fun rkUsStufe(n: Int) = "Stufe $n"
    override val rkUsInfo = "Einstufung des US-Außenministeriums für Reisen von US-Bürgern.\n• Stufe 1: übliche Vorsicht\n• Stufe 2: erhöhte Vorsicht\n• Stufe 3: Reise überdenken\n• Stufe 4: nicht reisen\n\nFür Teilgebiete können höhere Stufen gelten. Die Einstufung gibt die Sicht der US-Regierung wieder."
    override val rkAaLabel = "Auswärtiges Amt"
    override val rkAaLink = "Reisehinweise"
    override val rkAaWarnung = "Reisewarnung"
    override val rkAaTeil = "Teilreisewarnung"
    override val rkSanktionen = "Sanktionen"
    override val rkEuLabel = "EU-Sanktionen"
    override fun rkRegimeAnzahl(n: Int) = "$n Regime"
    override fun rkEuInfo(regime: List<String>, stand: String) = "Sanktionsregime der EU gegen das Land (Stand $stand).\n\nJe nach Regime gelten Waffenembargos, Einreiseverbote, Vermögenssperren gegen Personen oder Wirtschaftsmaßnahmen. Thematische Regime ohne Länderbezug fehlen.\n" +
        regime.joinToString("\n") { "• $it" }
    override val rkUsProgrammLabel = "US-Sanktionsprogramm"
    override fun rkProgrammeAnzahl(n: Int) = "$n Programme"
    override fun rkUsProgrammInfo(programme: List<String>, stand: String) = "Länderprogramme der US-Behörde OFAC (Stand $stand).\n\nSie verbieten US-Personen Geschäfte mit gelisteten Personen und Firmen oder mit dem Land, je nach Programm. Thematische Programme fehlen.\n" +
        programme.joinToString("\n") { "• $it" }
    override val rkOsLabel = "Gelistete Personen, Firmen"
    override val rkOsInfo = "Anzahl der Personen und Organisationen mit Bezug zum Land, etwa durch Staatsangehörigkeit oder Sitz, die auf Sanktionslisten stehen (EU, USA, UN und weitere).\n\nDie Zahl sagt nichts über Sanktionen gegen den Staat, dafür stehen die Zeilen zu EU und USA."
    override val rkRatings = "Kreditratings"
    override fun rkAusblick(a: String) = when (a) { "Stable" -> "stabil"; "Positive" -> "positiv"; "Negative" -> "negativ"; "Developing", "Evolving" -> "offen"; else -> a }
    override val rkRatingInfo = "Langfristige Note einer Ratingagentur für Staatsanleihen in Fremdwährung, dazu der Ausblick auf die erwartete Richtung.\n\nSkala bei S&P und Fitch:\n[skala] AAA | AA | A | BBB | BB | B | CCC | CC | C | D ; grenze=BBB ; links=beste Note ; rechts=Zahlungsausfall\n• Ab BBB− (Moody's Baa3, DBRS BBB (low)) gilt Investment Grade, darunter spekulativ.\n• Eine Jahreszahl in Klammern nennt das Jahr des Eintrags, wenn er älter ist als das Vorjahr."
    override val rkKonflikte = "Konflikte"
    override fun rkKonfliktArt(stufe: Int) = if (stufe >= 2) "Krieg" else "Bewaffneter Konflikt"
    override fun rkKonfliktInfo(stufe: Int, jahr: Int) = (if (stufe >= 2) "Krieg: mindestens 1.000 Gefechtstote im Jahr $jahr." else "Bewaffneter Konflikt: 25 bis 999 Gefechtstote im Jahr $jahr.") +
        " Gezählt werden Konflikte um Regierung oder Gebiet, an denen mindestens eine Konfliktpartei staatlich ist, und Gefechtstote eines Kalenderjahres."
    override val rkKatastrophen = "Naturkatastrophen"
    override fun rkKatArt(typ: String) = when (typ) {
        "EQ" -> "Erdbeben"; "TC" -> "Zyklon"; "FL" -> "Flut"; "WF" -> "Waldbrand"; "VO" -> "Vulkanausbruch"; "DR" -> "Dürre"; else -> typ
    }
    override fun rkKatStufe(rot: Boolean, von: String) = (if (rot) "Rot" else "Orange") + " ($von)"
    override val rkKatInfo = "Ereignis der letzten 30 Tage mit Alarmstufe Orange oder Rot des Katastrophenwarnsystems GDACS.\n\n• Die Stufe schätzt die Auswirkung, Orange mittel und Rot hoch.\n• Das Datum nennt den Beginn.\n• Die Angaben sind indikativ."
    override val rkWahlen = "Wahlen"
    override val rkWahlParlament = "Parlamentswahl"
    override val rkWahlPraesident = "Präsidentschaftswahl"
    override val rkWahlBeide = "Parlament und Präsident"
    override val rkWahlInfo = "Nächste reguläre nationale Wahl laut Wikipedia-Liste.\n\n• Bei Monatsangabe steht der Tag noch nicht fest.\n• Termine können sich verschieben, etwa durch vorgezogene Wahlen."
    override val rkStruktur = "Strukturelle Risiken"
    override fun rkStrukturLabel(art: de.troetenrepublik.app.engine.Laenderrisiken.Art) = when (art) {
        de.troetenrepublik.app.engine.Laenderrisiken.Art.RESERVEN -> "Devisenreserven"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ROHSTOFF -> "Rohstoffabhängigkeit"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.JUGEND -> "Jugendarbeitslosigkeit"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ALTER -> "Anteil über 65"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.AUSLAND -> "Auslandsschulden"
    }
    override fun rkStrukturWert(art: de.troetenrepublik.app.engine.Laenderrisiken.Art, zahl: String) = when (art) {
        de.troetenrepublik.app.engine.Laenderrisiken.Art.RESERVEN -> "$zahl Monate"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ROHSTOFF -> "$zahl %"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.JUGEND -> "$zahl %"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ALTER -> "$zahl %"
        de.troetenrepublik.app.engine.Laenderrisiken.Art.AUSLAND -> "$zahl % des BNE"
    }
    override fun rkStrukturInfo(art: de.troetenrepublik.app.engine.Laenderrisiken.Art, extra: String?) = when (art) {
        de.troetenrepublik.app.engine.Laenderrisiken.Art.RESERVEN -> "Währungsreserven in Monaten der Importe.\n\nDie IWF-Faustregel nennt drei Monate als Untergrenze. Sie gilt vor allem für Länder mit flexiblem Wechselkurs und ist für Länder mit starken Kapitalströmen umstritten."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ROHSTOFF -> "Anteil von Brennstoffen sowie Erzen und Metallen an der Warenausfuhr" + (extra?.let { " ($it)" } ?: "") + ". Der IWF nennt ein Land rohstoffreich, wenn nicht erneuerbare Ressourcen mindestens 20 % der Exporte oder der Staatseinnahmen ausmachen.\n\nRohstoffpreise schlagen dann stark auf Handelsbilanz und Staatshaushalt durch."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.JUGEND -> "Anteil der Erwerbspersonen von 15 bis 24 Jahren ohne Arbeit, Schätzung der ILO." + (extra?.let { " Weltweit: $it." } ?: "") + " Für diese Kennzahl gibt es keine feste Schwelle."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.ALTER -> "Anteil der Bevölkerung ab 65 Jahren.\n\n• Nach gängiger Einteilung gilt eine Gesellschaft ab 14 % als „aged“ und ab etwa 20 % als „super-aged“.\n• Eine alternde Bevölkerung belastet Renten- und Gesundheitssysteme."
        de.troetenrepublik.app.engine.Laenderrisiken.Art.AUSLAND -> "Auslandsschulden von Staat und Privaten in Prozent des Bruttonationaleinkommens (BNE), nur für Länder mit Daten zur Auslandsverschuldung. Für diese Kennzahl gibt es keine feste Schwelle."
    }
    override fun rkRohstoffAufteilung(brennstoff: String, erze: String) = "Brennstoffe $brennstoff %, Erze und Metalle $erze %"
    override val lsTitel = "Strukturelle Probleme"
    override val lsProgramme = "Gegenmaßnahmen"
    override val lsKeinProgramm = "Kein Programm belegt"
    override fun lsProgramm(name: String, jahr: Int) = if (jahr > 0) "$name ($jahr)" else name
    // [Globus Unternehmen]
    // [Laenderblatt 0.70]
    override val lbKopfArbeitslos = "Arbeitslos"
    override val lbKopfSchulden = "Schulden"
    override val lbKopfBipKopf = "BIP/Kopf"
    override val lbKopfEinwohner = "Einwohner"
    override val lbKopfLeben = "Lebenserwartung"
    override val lbKopfWaehrung = "Währung"
    override fun lbKopfZumEuro(code: String) = "$code zum Euro"
    override val lbKopfLeitzins = "Leitzins"
    override fun lbMrd(zahl: String) = "$zahl Mrd."
    override fun lbJahreKurz(zahl: String) = "$zahl J."
    override val lbVerlaufWachstum = "Wachstum je Jahr"
    override val lbVerlaufInflation = "Inflation je Jahr"
    override val lbPrognose = "Prognose"
    override val lbUebrige = "Übrige"
    override val lbTreemapHinweis = "Fläche: Börsenwert, Farbe: Änderung heute"
    override val lbRezLaender = "Länder in Rezession"
    override fun lbAnteilVon(n: Int, von: Int, anteil: String) = "$n von $von ($anteil)"
    override val lbRezBip = "Anteil am Welt-BIP"
    override fun lbKonfliktZeile(stufe: Int, a: String, b: String) = (if (stufe >= 2) "Krieg" else "Bewaffneter Konflikt") + ": $a gegen $b"
    override val lbRisikoWeitere = "Weitere Risiken"
    override val lbWirtWeitere = "Weitere Kennzahlen"
    override fun lbIndexLabel(name: String) = "Leitindex $name"
    override fun lbEtfLabel(symbol: String) = "Länder-ETF $symbol"
    override fun lbIndexInfo(name: String) = "Tagesänderung des Leitindex $name."
    override fun lbLaenderIndexLabel(name: String) = "Index $name"
    override fun lbLaenderIndexInfo(name: String) = "Tagesänderung des Länderindex $name. Er ersetzt den Leitindex der Börse, den es bei Yahoo nicht gibt."
    override val lbStandVom = "Stand vom"
    override val lbIndexQuelleStumm = "Die Börse antwortet nicht. Ein VPN oder Netzfilter kann sie sperren."
    override val lbBoerseLabel = "Börse"
    override val lbKeineBoerse = "Keine Börse"
    override val lbKeinIndexLabel = "Leitindex"
    override val lbKeinIndex = "Kein frei abrufbarer Index"
    override fun lbIndexSelbstLabel(name: String) = "Index $name"
    override fun lbIndexSelbstInfo(name: String) = "Der Index $name kommt über den Selbstabruf: Seite öffnen, eine Prüfung der Seite selbst lösen, übernehmen. Der Wert gilt vier Handelstage."
    override val lbIndexSelbstOeffnen = "Index über Selbstabruf laden"
    override val globusIndexSelbstFertig = "Indexwert übernommen"
    override val globusIndexSelbstLeer = "Auf der Seite steht kein Indexwert"
    override val saIndexTitel = "Börsenindizes"
    override val saIndexText = "Der Wert erscheint auf dem Globus unter „Börse heute“."
    override val lbWgiHinweis = "Rang unter allen Ländern, 100 = bestes Land."
    override val globusLandTitel = "Land"
    override val globusHauptstadt = "Hauptstadt"
    override val globusWaehrung = "Währung"
    override val globusOberhaupt = "Staatsoberhaupt"
    override val globusRegierungschef = "Regierungschef"
    override val globusOberhauptChef = "Staatsoberhaupt und Regierungschef"
    override val globusRegierungsform = "Regierungsform"
    override val globusMitgliedschaften = "Mitgliedschaften"
    override fun globusFormName(code: String, bundesstaat: Boolean) = (when (code) {
        "PARL_REP" -> "Parlamentarische Republik"; "PRAES" -> "Präsidialrepublik"; "HALBPRAES" -> "Halbpräsidentielle Republik"
        "VERF_MON" -> "Konstitutionelle Monarchie"; "ABS_MON" -> "Absolute Monarchie"; "EINPARTEI" -> "Einparteienstaat"
        "THEOKRATIE" -> "Theokratie"; "MILITAER" -> "Militärregierung"; "DIREKTORIUM" -> "Direktorialrepublik"
        "SVZ" -> "Sonderverwaltungszone Chinas"; else -> "Republik"
    }) + if (bundesstaat) ", Bundesstaat" else ""
    override fun globusOrgName(code: String) = when (code) {
        "EURO" -> "Euroraum"; "GOLF" -> "Golfrat"; "AU" -> "Afrikanische Union"; "MERCOSUR" -> "Mercosur"; else -> code
    }
    override val globusInfoOberhaupt = "Vertritt den Staat nach außen, als Präsident, Monarch oder Kollegium. In Parlamentsdemokratien und Monarchien sind die Aufgaben meist repräsentativ."
    override val globusInfoRegierungschef = "Leitet die Regierung und verantwortet Haushalt und Wirtschaftspolitik. In Präsidialsystemen ist es der Präsident, sonst meist ein Premierminister oder Kanzler."
    override val globusInfoOberhauptChef = "Dieselbe Person ist Staatsoberhaupt und leitet die Regierung, wie in Präsidialsystemen üblich. Sie verantwortet auch Haushalt und Wirtschaftspolitik."
    override val globusInfoRegierungsform = "Grobe Einordnung des politischen Systems. Bundesstaat heißt: Teilstaaten haben eigene Gesetzgebung."
    override val globusInfoMitgliedschaften = "Bündnisse und Wirtschaftsräume des Landes:\n" +
        "• EU: Binnenmarkt der Europäischen Union\n• Euroraum: Länder mit dem Euro als Währung\n• NATO: Verteidigungsbündnis\n" +
        "• G7 und G20: Gruppen großer Volkswirtschaften\n• OPEC: Ölförderländer mit abgestimmten Fördermengen\n" +
        "• BRICS, OECD, ASEAN, Mercosur, Golfrat und Afrikanische Union: weitere Staatenbündnisse und Handelsräume"
    override val globusWikiTitel = "Wirtschaft laut Wikipedia"
    override val globusWeiterlesen = "Weiterlesen auf Wikipedia"
    override fun globusLaenderartikel(name: String) = "Länderartikel: $name"
    override val globusUnternehmenTitel = "Größte Unternehmen"
    override val globusBoersennotiert = "Börsennotiert"
    override val globusNichtBoersennotiert = "Nicht börsennotiert"
    override val globusStaatsfirma = "Staatsfirma"
    override val globusFirmenOhneWerte = "Ohne aktuelle Börsenwerte, Reihenfolge ungefähr"
    override val globusInfoGelistet = "Die zehn Firmen mit dem höchsten Börsenwert unter den erfassten Firmen des Landes, in Euro umgerechnet.\n\n• Der Börsenwert ist Aktienkurs mal Aktienzahl.\n• Erfasst sind Katalogwerte und Firmen aus Wikidata mit Kürzel bei Yahoo Finance, nicht jede Notierung.\n• Staatsfirma heißt: Der Staat ist laut Wikidata Eigner.\n• Börsenwerte: Yahoo Finance. Für russische Firmen die Moskauer Börse (ISS-Schnittstelle, 15 Minuten verzögert). Für Firmen ohne Kurs Wikidata oder die Infobox des Wikipedia-Artikels (CC BY-SA 4.0), dann steht das Jahr der Angabe in Klammern und der Wert ist nicht aktuell.\n• Bei Colombo, Karatschi, Casablanca, BRVM, Uganda, Dhaka, Chittagong, Malta, Zagreb, Ljubljana, Ho-Chi-Minh-Stadt, Hanoi, UPCoM, Zypern, Trinidad und Tobago, Baku, Lima und Kasachstan (KASE) stammt der Wert von der Seite der Börse, abgerufen für die persönliche, nicht kommerzielle Nutzung."
    override val globusInfoNichtGelistet = "Große Firmen ohne Börsennotierung, von Hand ausgewählt und geprüft, grob nach Größe gereiht. Sie gehören Familien, Stiftungen, Genossenschaften oder dem Staat. Aktien gibt es nicht, ein Kauf im Übungsdepot ist nicht möglich."
    // [Laender Unternehmen 0.71] Angaben einer Firma ohne Kursverlauf als Info-Blatt
    override val globusFirmaBranche = "Branche"
    override val globusFirmaSitz = "Sitz"
    override val globusFirmaBoerse = "Börse"
    override val globusFirmaEigner = "Eigentümer"
    override val globusFirmaStaat = "Staat"
    override val globusFirmaWert = "Börsenwert"
    override val globusFirmaMitarbeiter = "Mitarbeiter"
    override val globusFirmaGegruendet = "Gegründet"
    override val globusFirmaMutter = "Muttergesellschaft"
    override val globusFirmaWebsite = "Website"
    override val globusFirmaKeinVerlauf = "Kein Kursverlauf: Für die Firma gibt es keine freie Kursquelle. Steht beim Wert ein Jahr in Klammern, ist er nicht aktuell."
    override val globusBranchenTitel = "Branchen an der Börse"
    override fun globusInfoBranchen(n: Int) = "Anteil der Branche am Börsenwert der erfassten gelisteten Firmen des Landes ($n Firmen mit Börsenwert).\n\n• Die Anteile gelten nur für diese Firmen, nicht für die ganze Börse.\n• Branchen nach der Einteilung der App."
    override val globusWeitereFirmen = "Weitere große Unternehmen"
    override val globusInfoWeitere = "Firmen mit über 10.000 Beschäftigten, für die Wikidata keinen Börsenplatz kennt. Die Angaben sind nicht von Hand geprüft, die Firmen können dennoch notiert sein. Reihenfolge nach Beschäftigten."
    override fun globusBoersenKurse(name: String) = "Kurse an der $name"
    override val globusSelbstHinweis = "Seite laden, eine Prüfung der Seite selbst lösen, dann „Seite übernehmen“ tippen. Die App liest nur die angezeigten Werte."
    override fun globusSelbstFertig(n: Int) = if (n > 0) "$n Börsenwerte übernommen" else "Börsenwert gesamt übernommen"
    override val globusSelbstLeer = "Auf der Seite stehen keine Börsenwerte"
    override fun globusBoersenwertGesamt(wert: String, quelle: String) = "Börsenwert gesamt: $wert ($quelle)"
    override fun globusBoersenwertJahr(jahr: Int) = "Börsenwert $jahr"
    override val kkpDollar = "Kaufkraft-$"
    // [Globus Mehr]
    // [Engpaesse]
    override val globusEngpaesse = "Engpässe"
    override val globusLegEngpaesse = "Durchfahrten pro Tag, Schnitt der letzten sieben Tage gegen den Schnitt desselben Zeitraums im Vorjahr. Volle Farbe bei 40 % Abweichung."
    override val engOhneVergleich = "Ohne Vorjahreswert"
    override val engListe = "Engpässe nach Veränderung"
    override fun engStand(datum: String) = "Stand $datum"
    override fun engName(id: String) = when (id) {
        "chokepoint1" -> "Suezkanal"
        "chokepoint2" -> "Panamakanal"
        "chokepoint3" -> "Bosporus"
        "chokepoint4" -> "Bab el-Mandeb"
        "chokepoint5" -> "Straße von Malakka"
        "chokepoint6" -> "Straße von Hormus"
        "chokepoint7" -> "Kap der Guten Hoffnung"
        "chokepoint8" -> "Straße von Gibraltar"
        "chokepoint9" -> "Straße von Dover"
        "chokepoint10" -> "Öresund"
        "chokepoint11" -> "Taiwanstraße"
        "chokepoint12" -> "Koreastraße"
        "chokepoint13" -> "Tsugaru-Straße"
        "chokepoint14" -> "Luzonstraße"
        "chokepoint15" -> "Lombokstraße"
        "chokepoint16" -> "Ombaistraße"
        "chokepoint17" -> "Bohai-Straße"
        "chokepoint18" -> "Torres-Straße"
        "chokepoint19" -> "Sundastraße"
        "chokepoint20" -> "Makassarstraße"
        "chokepoint21" -> "Magellanstraße"
        "chokepoint22" -> "Yucatánstraße"
        "chokepoint23" -> "Windwardpassage"
        "chokepoint24" -> "Mona-Passage"
        "chokepoint25" -> "Balabac-Straße"
        "chokepoint26" -> "Beringstraße"
        "chokepoint27" -> "Mindorostraße"
        "chokepoint28" -> "Straße von Kertsch"
        else -> id
    }
    override fun engSuche(id: String) = "\"" + engName(id) + "\" " + "Schifffahrt"
    override fun engGegenVorjahr(prozent: String) = "$prozent gegen Vorjahr"
    override val engKeineDaten = "Keine Daten"
    override val engProTag = "Durchfahrten pro Tag"
    override val engVorjahr = "Vorjahr"
    override val engTanker = "Tanker pro Tag"
    override val engContainer = "Containerschiffe pro Tag"
    override val engInfoProTag = "Schiffe, die den Engpass pro Tag durchfahren, im Schnitt der letzten sieben Tage.\n\nGezählt wird jedes Schiff einmal, wenn es die Grenze des Engpasses quert. Dieselbe Durchfahrt zählt innerhalb von 48 Stunden nicht doppelt."
    override val engInfoVorjahr = "Schnitt pro Tag im Zeitraum von 28 Tagen um denselben Tag des Vorjahres. Ein Rückgang gegen diesen Wert zeigt, dass weniger Schiffe fahren als zur gleichen Jahreszeit davor."
    override val engInfoSchiffsart = "Schnitt pro Tag der letzten sieben Tage, in Klammern die Veränderung gegen den Vorjahreszeitraum. Die Schiffsart folgt der Einteilung von PortWatch (Tanker, Container, Massengut, Stückgut, Ro-Ro)."
    override val engVerlauf = "Verlauf über ein Jahr"
    override val engBedeutung = "Bedeutung"
    override val engWarenTitel = "Waren"
    override fun engInfo(id: String) = when (id) {
        "chokepoint6" -> "Einzige Seeverbindung zwischen dem Persischen Golf und dem offenen Meer, zwischen Iran und Oman.\n\nDie EIA beziffert den Durchfluss auf rund 20 Mio. Barrel Öl und Ölprodukte pro Tag (2024), etwa ein Fünftel des weltweiten Ölverbrauchs, dazu rund ein Fünftel des weltweiten LNG-Handels."
        "chokepoint4" -> "Zugang vom Roten Meer zum Golf von Aden, zwischen Jemen und Dschibuti.\n\nSeit den Angriffen der Huthi auf Handelsschiffe ab Ende 2023 meiden viele Reedereien die Route und fahren um das Kap der Guten Hoffnung."
        "chokepoint1" -> "Verbindet Mittelmeer und Rotes Meer und verkürzt den Weg zwischen Asien und Europa.\n\nFällt die Zahl der Durchfahrten, weichen Schiffe über das Kap der Guten Hoffnung aus. Das verlängert die Fahrt und bindet Schiffskapazität."
        "chokepoint2" -> "Verbindet Atlantik und Pazifik. Der Wasserstand des Gatunsees begrenzt die Zahl der Schleusungen. 2023 und 2024 senkte eine Dürre die Durchfahrten deutlich."
        "chokepoint5" -> "Verbindet Indischen Ozean und Südchinesisches Meer zwischen Malaysia, Indonesien und Singapur.\n\nHauptroute für Öl und Container nach Ostasien, mit den meisten Durchfahrten unter den großen Engpässen für Energie."
        "chokepoint11" -> "Zwischen Taiwan und China, Teil der Hauptroute des ostasiatischen Containerverkehrs.\n\n• Taiwan fertigt den Großteil der modernsten Halbleiter.\n• Eine Blockade oder Sperrung träfe Chip- und Elektroniklieferungen."
        "chokepoint3" -> "Einzige Ausfahrt des Schwarzen Meeres.\n\n• Der Vertrag von Montreux (1936) regelt die Durchfahrt von Kriegsschiffen.\n• Getreide, Öl und Dünger aus Russland, der Ukraine und Kasachstan laufen hier durch."
        "chokepoint7" -> "Ausweichroute um Afrika. Mehr Durchfahrten bei gleichzeitig weniger im Suezkanal und im Roten Meer zeigen eine Umleitung des Verkehrs."
        "chokepoint8" -> "Zugang zwischen Atlantik und Mittelmeer, auch Weg von und zum Suezkanal."
        "chokepoint9" -> "Engste Stelle des Ärmelkanals, Zufahrt zu den Nordseehäfen von Rotterdam, Antwerpen und Hamburg."
        "chokepoint10" -> "Einer der Zugänge zur Ostsee, zwischen Dänemark und Schweden."
        "chokepoint28" -> "Verbindet das Asowsche Meer mit dem Schwarzen Meer."
        "chokepoint12" -> "Zwischen Südkorea und Japan, Zufahrt zu den Häfen Busan, Kitakyushu und dem Japanischen Meer."
        "chokepoint14" -> "Zwischen Taiwan und den Philippinen, Verbindung vom Südchinesischen Meer in den Pazifik."
        else -> "Wasserstraße, deren Durchfahrten PortWatch aus Schiffssignalen zählt."
    }
    override fun engWaren(id: String) = when (id) {
        "chokepoint6" -> "Rohöl, Ölprodukte, Flüssigerdgas (LNG), Chemikalien."
        "chokepoint4" -> "Container zwischen Asien und Europa, Rohöl und Ölprodukte, Getreide."
        "chokepoint1" -> "Container zwischen Asien und Europa, Rohöl, Ölprodukte, Flüssiggas, Getreide."
        "chokepoint2" -> "Container zwischen Ostasien und der US-Ostküste, US-Flüssiggas, Getreide und Soja aus den USA."
        "chokepoint5" -> "Rohöl nach Ostasien, Container zwischen Asien und Europa, Kohle, Palmöl."
        "chokepoint11" -> "Halbleiter und Elektronik, Container zwischen China, Taiwan, Korea und Japan."
        "chokepoint3" -> "Getreide, Rohöl, Dünger, Metalle."
        "chokepoint7" -> "Wie Suezkanal und Bab el-Mandeb: Container, Öl, Massengut."
        "chokepoint8" -> "Container, Öl, Massengut, Stückgut zwischen Europa, Nordafrika, Asien und Amerika."
        "chokepoint9" -> "Container, Stückgut, Tanker."
        "chokepoint10" -> "Stückgut, Tanker, Holz und Massengut."
        "chokepoint28" -> "Getreide, Metalle, Öl."
        "chokepoint12" -> "Container, Massengut, Tanker."
        "chokepoint14" -> "Container, Massengut, Tanker."
        else -> "Regionaler Seeverkehr und Zubringer zu großen Häfen."
    }
    override val engSanktionen = "Sanktionen bei Anrainern"
    override val engUsa = "USA"
    override fun engSanktionInfo(eu: String, us: String) = "Sanktionsregime gegen den Anrainer. EU: " + eu.ifBlank { "keine" } + ". USA (OFAC): " + us.ifBlank { "keine" } + "."
    override val engWerte = "Werte"
    override val engSchlagzeilen = "Schlagzeilen"
    override fun engLieferZeile(name: String, prozent: String) = "$name: Durchfahrten $prozent gegen Vorjahr"
    // [Grosses Ganzes 0.73]
    override val abhTitel = "Abhängigkeiten und Risiken"
    override fun abhZusammen(land: Boolean, rohstoffe: Int, einfluesse: Int, politik: Boolean, empfindlichkeit: Boolean) = listOfNotNull(
        if (rohstoffe > 0) (if (rohstoffe == 1) "1 Rohstoff" else "$rohstoffe Rohstoffe") else null,
        if (einfluesse > 0) (if (einfluesse == 1) "1 Einfluss" else "$einfluesse Einflüsse") else null,
    ).ifEmpty {
        // ohne Zahlen: die Themen der Karte
        listOfNotNull(if (land) "Land" else null, if (politik) "Politik" else null, if (empfindlichkeit) "Kursempfindlichkeit" else null)
    }.joinToString(" · ")
    override fun abhAbschnitt(key: String) = when (key) {
        "HERKUNFT" -> "Land und Sitz"; "ROHSTOFFE" -> "Rohstoffe der Branche"; "ROHSTOFFE_FIRMA" -> "Rohstoffe des Unternehmens"; "EINFLUESSE" -> "Einflüsse"
        "EINFLUESSE_BRANCHE" -> "Einflüsse auf die Branche"; "POLITIK" -> "Politik und Recht"; "EMPFINDLICHKEIT" -> "Kursempfindlichkeit"; "LUECKEN" -> "Lücken"; else -> key
    }
    override fun abhArt(key: String) = when (key) { "GEMELDET" -> "gemeldet"; "GEMESSEN" -> "gemessen"; "ABGELEITET" -> "abgeleitet"; else -> key }
    override fun abhStand(monat: String) = "Stand $monat"
    override val abhSitz = "Sitz"
    override val abhTaetigkeit = "Tätigkeit"
    override val abhMutter = "Konzernmutter"
    override val abhGesamtbild = "Gesamtbild des Landes"
    override val abhLandrisiko = "Landrisiko"
    override fun abhVon100(punkte: String) = "$punkte von 100"
    override val abhKatastrophen = "Katastrophen im Land"
    override fun abhKatastrophenWert(n: Int, rot: Int) = if (rot > 0) "$n aktuell, $rot rot" else "$n aktuell"
    override fun abhTop3(p: String) = "Top 3: $p"
    override val abhAnteileOffen = "Anteile nicht veröffentlicht"
    override val abhKritisch = "kritisch in der EU"
    override fun abhFoerderung(laender: String) = "Förderung: $laender"
    override val abhBetroffen = "betroffen"
    override fun abhRichtung(richtung: Int) = if (richtung > 0) "stützt" else "belastet"
    override fun abhAnstieg(richtung: Int) = if (richtung > 0) "Anstieg stützt" else "Anstieg belastet"
    override val abhWegPraefix = "Weg"
    override val abhStaerkeRoute = "Durchfahrten der Route gegen Vorjahr, für die Firma nicht gemessen"
    override val abhStaerkeGebiet = "Meldungen aus dem Gebiet gegen die Wochen davor, für die Firma nicht gemessen"
    override fun abhGiltFuer(branche: String, gebiet: String?) = "Gilt für die Branche $branche" + (gebiet?.let { " ($it)" } ?: "")
    override fun abhFaktor(key: String) = when (key) {
        "MARKT" -> "Markt (S&P 500)"; "HALBLEITER" -> "Halbleiterindex"; "OEL" -> "Öl"; "DOLLAR" -> "Dollar"
        "ZINS10J" -> "Zinsen (10 Jahre)"; "KUPFER" -> "Kupfer"; "GOLD" -> "Gold"; "CHINA" -> "China (Shanghai)"; else -> key
    }
    override fun abhAussage(key: String) = when (key) { "LAEUFT_MIT" -> "läuft mit"; "LAEUFT_GEGEN" -> "läuft gegen"; "WECHSELT" -> "wechselt"; else -> "kein Beleg" }
    override fun abhEmpfHinweis(r2: String, jahre: Int) = "R² $r2 · $jahre Jahre"
    override val abhEmpfOhne = "Weitere Größen"
    override val abhKeinBeleg = "kein Beleg"
    override val abhEmpfZuKurz = "zu wenig Kurshistorie"
    override val abhEmpfLabel = "Kursempfindlichkeit"
    override val abhEtfErfasst = "Erfasste Positionen"
    override fun abhEtfBetrifft(n: Int) = "betrifft $n Positionen"
    override val abhWirkungUnter = "Einflüsse, Länder und deine Werte"
    override val abhInfo = "• Gemeldet: Angabe der Firma.\n• Gemessen: aus Kursen gerechnet.\n• Abgeleitet: aus Branche, Beschreibung oder Land.\n\nR² gibt an, welchen Anteil der Kursschwankung die Größen erklären. Ein Zusammenhang gilt als belegt, wenn er in beiden Hälften des Zeitraums gleich verläuft.\n\nBeschreibt die Vergangenheit und ist keine Anlageberatung."
    // [0.76] Kernaussagen und ruhigere Darstellung der Karte
    override val abhKernTitel = "Auf einen Blick"
    override fun abhKernSegment(name: String, anteil: String) = "Größtes Segment: $name, $anteil"
    override fun abhKernRegion(name: String, anteil: String) = "Größte Region: $name, $anteil"
    override fun abhKernKunde(anteil: String) = "Größter Kunde: $anteil des Umsatzes"
    override fun abhKernKurs(liste: String) = "Kurs läuft $liste"
    override fun abhKernMit(name: String, wert: String) = "mit $name ($wert)"
    override fun abhKernGegen(name: String, wert: String) = "gegen $name ($wert)"
    override fun abhKernRohstoffe(land: String, liste: String) = "Rohstoffe der Branche, größtes Förderland $land: $liste"
    override fun abhKernKritisch(n: Int) = "Branche braucht $n Rohstoffe, die in der EU als kritisch gelten"
    // [0.76] Rohstoffe je Unternehmen
    override fun abhVerwendung(key: String) = when (key) {
        "WAFER" -> "Wafer"
        "WAFERGAS" -> "Gas in der Waferfertigung"
        "AETZ" -> "Ätzen und Reinigen der Wafer"
        "LEITER" -> "Leiterbahnen und Kontakte"
        "GAN" -> "GaN- und GaAs-Chips"
        "FUNKCHIP" -> "Funkchips"
        "SUBSTRAT" -> "Substrate für Chips"
        "OPTIK" -> "Laser und Optik"
        "FASER" -> "Glasfaser"
        "AKKU" -> "Batteriezellen"
        "KATHODE" -> "Kathodenmaterial"
        "ANODE" -> "Anodenmaterial"
        "MAGNET" -> "Magnete und Motoren"
        "KATALYSATOR" -> "Abgaskatalysatoren"
        "KAROSSERIE" -> "Karosserie und Stahlteile"
        "LEICHTBAU" -> "Leichtbauteile"
        "GEHAEUSE" -> "Gehäuse"
        "LOT" -> "Lot und Kontakte"
        "KONDENSATOR" -> "Kondensatoren"
        "SPEICHER" -> "Festplatten"
        "KABEL" -> "Kabel und Wicklungen"
        "LEITUNG" -> "Leitungen und Netze"
        "TRIEBWERK" -> "Triebwerke und Hochtemperaturteile"
        "STRUKTUR" -> "Rumpf und Struktur"
        "MUNITION" -> "Munition und Panzerung"
        "STAHL" -> "Stahl und Legierungen"
        "HARTMETALL" -> "Werkzeuge und Hartmetall"
        "VERZINKUNG" -> "Verzinkung"
        "GUSS" -> "Zinkguss"
        "MESSING" -> "Messing"
        "ADDITIV" -> "Zusatzstoff"
        "DUENGER" -> "Dünger"
        "FLUOR" -> "Fluorchemie und Kältemittel"
        "SOLAR" -> "Solarzellen"
        "WIND" -> "Windturbinen"
        "MRT" -> "MRT-Magnete"
        "ELEKTROLYSE" -> "Elektrolyseure und Brennstoffzellen"
        "FOERDERUNG" -> "eigene Förderung"
        "ERKUNDUNG" -> "eigene Suche oder Förderung"
        "ERZEUGUNG" -> "eigenes Erzeugnis"
        "LIZENZ" -> "Lizenzen und Abnahmerechte"
        "EINSATZ" -> "Einsatzstoff"
        "HUETTE" -> "Verhüttung"
        "RECYCLING" -> "Recycling und Verarbeitung"
        "SCHMUCK" -> "Schmuck und Uhren"
        "KONFLIKT" -> "Konfliktmineral in Zulieferteilen"
        "IMPLANTAT" -> "Implantate"
        "PIGMENT" -> "Pigment in Farben"
        "ELEKTRODE" -> "Elektroden"
        "GASHANDEL" -> "Industriegas"
        "POLY" -> "Polysilizium"
        "SILIKON" -> "Silikone"
        "VERPACKUNG" -> "Verpackung und Dosen"
        "SUPRALEITER" -> "supraleitende Chips"
        "RADAR" -> "Radar und Sensorik"
        "ANREICHERUNG" -> "Anreicherung und Brennstoffherstellung"
        "BRENNSTOFF" -> "Brennstoff für Kernkraftwerke"
        else -> key
    }
    override fun abhWeg(key: String) = when (key) { "EIGEN" -> "selbst verarbeitet"; "ZULIEFERER" -> "über Zulieferer"; "FERTIGER" -> "über Auftragsfertiger"; "PRODUKT" -> "im Produkt"; else -> key }
    override fun abhRohQuelle(key: String) = when (key) {
        "SEC" -> "bei der SEC gemeldet"; "BESCHREIBUNG" -> "laut Beschreibung"; "FIRMA" -> "laut Geschäftsmodell"
        "INDUSTRIE" -> "laut Industrie"; "BRANCHE" -> "laut Branche"; else -> key
    }
    override fun abhBrancheUeblich(verwendung: String) = "$verwendung in der Branche üblich"
    override val abhRohSd = "Form SD bei der SEC eingereicht: Konfliktmineralien Zinn, Tantal, Wolfram, Gold"
    override val abhRohBericht = "Im Rohstoffabschnitt des Jahresberichts genannt"
    override fun abhRohWelt(jahr: Int) = "Weltförderung $jahr"
    override fun abhRohWert(jahr: Int) = "Wert der Weltförderung $jahr"
    override fun abhRohFirma(jahr: Int) = "Förderung des Unternehmens $jahr"
    override fun abhRohKapazitaet(jahr: Int) = "Kapazität des Unternehmens $jahr, anteilig"
    override val abhRohKapKopf = "Kapazität"
    override val abhRohAnlagen = "Anlagen mit Kapazitätsangabe"
    override fun abhRohAnlagenWert(verwertbar: Int, gesamt: Int) = "$verwertbar von $gesamt"
    override val abhRohLand = "Land"
    override val abhRohFoerderland = "Förderland"
    override val abhRohMenge = "Menge"
    override val abhRohAnteil = "Anteil"
    override val abhRohWertKopf = "Wert"
    override val abhRohUebrige = "Übrige Länder"
    override fun abhRohEinheit(key: String) = if (key == "m3") "m³" else key
    override fun abhKernRohstoffeFirma(land: String, liste: String) = "Rohstoffe des Unternehmens, größtes Förderland $land: $liste"
    override fun abhKernFoerdert(liste: String) = "Sucht oder fördert selbst: $liste"
    override fun abhKernErzeugt(liste: String) = "Eigenes Erzeugnis: $liste"
    override fun abhKernLizenz(liste: String) = "Hält Lizenzen und Abnahmerechte: $liste"
    override val abhRohGenannt = "in der Beschreibung genannt"
    override val abhRohZuordnung = "Zuordnung"
    override fun abhKernKritischFirma(n: Int) = "Unternehmen braucht $n Rohstoffe, die in der EU als kritisch gelten"
    override val wnUeberProfil = "über das Unternehmensprofil"
    override fun abhKernLand(sitz: String, land: String, stufe: String) = when {
        sitz.isEmpty() -> "Land $land, Landrisiko $stufe"
        sitz == land -> "Sitz $sitz, Landrisiko $stufe"
        else -> "Sitz $sitz, Land $land, Landrisiko $stufe"
    }
    override fun abhStufe(key: String) = when (key) { "NIEDRIG" -> "niedrig"; "MITTEL" -> "mittel"; else -> "hoch" }
    override fun abhKernAuffaellig(liste: String) = "Aktuell auffällig: $liste"
    override fun abhKernEinfluesse(liste: String) = "Einflüsse der Branche: $liste"
    override fun abhMessung(text: String) = "Am Kurs gemessen: $text"
    override fun abhBaustein(key: String) = when (key) {
        "BASIS" -> "Staatsführung und Stabilität"; "SANKTIONEN_EU" -> "Sanktionen der EU"; "SANKTIONEN_US" -> "Sanktionen der USA"
        "REGISTER" -> "Treffer im Sanktionsregister"; "KONFLIKT" -> "Bewaffneter Konflikt"; "KRIEG" -> "Krieg"
        "REISE" -> "Reisewarnung der USA"; else -> "Strukturprobleme"
    }
    override fun wsLuecke(grund: String) = "Segmente und Regionen: $grund"
    // [Grosses Ganzes 0.74] Wovon sie lebt
    override val wsTitel = "Umsatzquellen"
    override fun wsGruppe(key: String) = when (key) { "SEGMENTE" -> "Segmente"; "REGIONEN" -> "Regionen"; else -> "Großkunden" }
    override fun wsStand(jahr: Int) = "Geschäftsjahr $jahr"
    override val wsUebrige = "Übrige"
    override fun wsKunde(nr: String) = "Kunde $nr"
    override fun wsKundenGruppe(n: String) = "$n Kunden"
    override val wsSitzland = "Sitzland"
    override val wsNichtUsa = "Außerhalb der USA"
    // Gebietsnamen aus den Berichten (englische Gliednamen) in deutscher Fassung; Eigennamen von Sparten bleiben, wie sie sind
    override fun wsGebiet(name: String): String {
        var t = " $name "
        for ((von, zu) in WS_GEBIETE) t = t.replace(von, zu)
        return t.trim()
    }
    override val wsLaedt = "Wird geladen …"
    override val wsFehler = "Bericht nicht abrufbar"
    override val wsZuGross = "Bericht zu groß"
    override val wsNichtLesbar = "Bericht nicht lesbar"
    override val wsNichtEindeutig = "nicht eindeutig gemeldet"
    override val wsNichtVerfuegbar = "nicht verfügbar"
    override val wsOhneKontakt = "Kontakt fehlt"
    override fun wsInfo(key: String) = when (key) {
        "SEGMENTE" -> "Umsatz je Geschäftsbereich aus dem jüngsten Jahresbericht der Firma.\n\n• Anteile an der Summe der gemeldeten Bereiche.\n• Summen, Eliminierungen und Überleitungen sind herausgerechnet.\n• Die Bezeichnungen stehen so im Bericht.\n• Weicht die Summe mehr als 10 % vom Umsatz ab, steht der Abschnitt als nicht eindeutig."
        "REGIONEN" -> "Umsatz je Region oder Land aus dem jüngsten Jahresbericht der Firma.\n\n• Anteile an der Summe der gemeldeten Regionen, Oberbegriffe sind herausgerechnet.\n• Ob nach Kundensitz oder Sitz der Gesellschaft zugeordnet wird, bestimmt die Firma."
        "KUNDEN" -> "Kunden mit Anteil am Umsatz, wie die Firma sie meldet, meist ab 10 %.\n\n• Anteile an Forderungen zählen nicht.\n• Namen nennen Firmen selten, Kunde A ist die Bezeichnung im Bericht."
        "KEIN_SEC" -> "Segmente, Regionen und Großkunden stehen in den Jahresberichten der Firmen.\n\n• Frei und strukturiert abrufbar sind sie für US-Firmen und viele große Firmen weltweit, die in den USA gelistet sind.\n• Für diese Firma liegt keine solche Quelle vor."
        "KEIN_BERICHT" -> "Die SEC führt die Firma, aber ohne Jahresbericht mit XBRL-Daten (10-K, 20-F, 40-F)."
        "LEER" -> "Der jüngste Jahresbericht enthält in den XBRL-Daten keine Gliederung des Umsatzes nach Segment, Region oder Großkunde."
        else -> "Die SEC verlangt eine Kontaktadresse in der Kennung der Abrufe. Eintragen unter Zugänge."
    }
    override val wnTitel = "Auswirkung auf meine Werte"
    override val wnLeer = "Keine deiner Positionen hängt an einer erfassten Einflussgröße."
    override fun wnBetrifft(depot: Int, watch: Int, anteil: String) = listOfNotNull(
        if (depot > 0) "$depot im Depot ($anteil)" else null,
        if (watch > 0) "$watch auf der Watchlist" else null,
    ).joinToString(" · ").ifEmpty { "keine Werte betroffen" }
    override val wnWirkung = "Wirkung"
    override val wnLaender = "Länder"
    override val wnBranchen = "Branchen"
    override val wnDepot = "Im Depot"
    override val wnWatchlist = "Auf der Watchlist"
    override val wnBelege = "Belege"
    override val wnOhneTreffer = "Ohne Treffer in deinen Werten"
    override val wnUeberBranche = "über die Branche"
    override val wnGenannt = "genannt"
    override val wnLaedt = "Berechnet Verbindungen …"
    override fun wnTyp(key: String) = when (key) {
        "ZINS" -> "Zinsen"; "ENERGIE" -> "Energie"; "WAEHRUNG" -> "Währung"; "NACHFRAGE" -> "Nachfrage"; "ZYKLUS" -> "Zyklen"
        "ROHSTOFF" -> "Rohstoffe"; "ENGPASS" -> "Engpässe"; "SPANNUNG" -> "Spannungsgebiete"; "KLIMA" -> "Klima"
        "PROGRAMM" -> "Programme und Zölle"; "DURCHBRUCH" -> "Durchbrüche"; "LAND" -> "Land"; else -> key
    }
    override fun wnZur(key: String) = when (key) {
        "ENGPASS", "SPANNUNG", "KLIMA" -> "Auf dem Globus ansehen"; "DURCHBRUCH" -> "Durchbrüche ansehen"
        "ZINS" -> "Zinserwartung ansehen"; else -> ""
    }
    override val wnInfo = "Eine Richtung steht nur, wo sie belegt oder aus Kursen gemessen ist, sonst heißt es betroffen. Die Größe der Wirkung ist nicht bezifferbar."
    override val lbTitel = "Lage"
    override fun lbThema(key: String) = when (key) {
        "KONJUNKTUR" -> "Konjunktur und Inflation"; "ZINSEN" -> "Zinsen"; "GEOPOLITIK" -> "Geopolitik"
        "ROHSTOFFE" -> "Rohstoffe und Energie"; "KLIMA" -> "Klima"; "TECHNIK" -> "Technik"; else -> key
    }
    override fun lbName(key: String, zusatz: String) = when (key) {
        "WACHSTUM" -> "Wachstum Welt $zusatz"; "INFLATION" -> "Inflation Welt $zusatz"; "ZINS_HEUTE" -> "Fed Funds laut Futures"
        "ZINS_SCHRITTE" -> "Erwartet in 12 Monaten"; "KRIEGE" -> "Kriege $zusatz"; "SPANNUNG" -> "Gebiete mit mehr Berichten"
        "ENGPASS_GESTOERT" -> "Engpässe mit Rückgang über 20 %"; "ENSO_ZUSTAND" -> "ENSO"; "ENSO_INDEX" -> "ENSO-Index"
        "KATASTROPHEN" -> "Wetterkatastrophen, 30 Tage"; "DURCHBRUCH_NEU" -> "Neue Durchbruchmeldungen, 7 Tage"
        "DURCHBRUCH_THEMEN" -> "Themen mit Meldungen"; else -> key
    }
    override fun lbSchritte(n: String) = "$n Schritte"
    override fun lbVonGesamt(n: String, gesamt: String) = "$n von $gesamt"
    override fun lbRot(n: String, rot: String) = "$n, davon $rot rot"
    override fun lbVorjahr(wert: String) = "Vorjahr $wert"
    override fun lbErhoehtAb(wert: String) = "Erhöht ab $wert"
    override fun lbVorher(wert: String) = "Vorher $wert"
    override fun lbStand(datum: String) = "Stand $datum"
    override val lbOffen = "keine Daten"
    override val lbSeit = "Seit dem letzten Öffnen"
    override fun lbKeineAenderung(datum: String) = "Keine Änderung seit $datum"
    override fun lbThemaKurz(key: String) = when (key) {
        "KONJUNKTUR" -> "Konjunktur"; "ZINSEN" -> "Zinsen"; "GEOPOLITIK" -> "Geopolitik"
        "ROHSTOFFE" -> "Rohstoffe"; "KLIMA" -> "Klima"; "TECHNIK" -> "Technik"; else -> key
    }
    override fun lbAuffaellig(n: String, gesamt: String) = "$n von $gesamt auffällig"
    override fun lbAenderungen(n: Int) = if (n == 1) "1 Änderung" else "$n Änderungen"
    override fun lbTab(key: String) = when (key) {
        "WACHSTUM_LAND" -> "Wachstum je Land"; "INFLATION_LAND" -> "Inflation je Land"; "ZINS_PFAD" -> "Erwarteter Leitzins je Monat"
        "KRIEGE" -> "Kriege"; "KONFLIKTE" -> "Weitere bewaffnete Konflikte"; "SPANNUNG" -> spTitel; "ENGPAESSE" -> "Engpässe im Seehandel"
        "ENSO" -> ensoTitel; "KATASTROPHEN_LAND" -> "Wetterkatastrophen je Land"; "DURCHBRUCH_THEMA" -> "Durchbrüche je Thema"
        "BESCHLEUNIGT" -> dbBeschleunigt; else -> key
    }
    override fun lbErk(key: String, zusatz: String) = when (key) {
        "WACHSTUM_LAND" -> "Wachstum: Veränderung der Wirtschaftsleistung zum Vorjahr, Schätzung des IWF. Der Weltwert gewichtet die Länder nach Kaufkraft."
        "INFLATION_LAND" -> "Inflation: Anstieg der Verbraucherpreise zum Vorjahr im Jahresschnitt, Schätzung des IWF. Erhöht ab $zusatz: Ab hier gilt die Teuerung als Belastung."
        "ZINS_PFAD" -> "Fed Funds: Tagesgeldsatz, den die Notenbank Fed steuert. Je Monat der Satz, den die Terminkontrakte erwarten. Ein Schritt sind 0,25 Prozentpunkte."
        "KRIEGE" -> "Krieg: bewaffneter Konflikt mit mindestens 1.000 Gefechtstoten im Jahr $zusatz, gezählt von UCDP."
        "KONFLIKTE" -> "Bewaffneter Konflikt: 25 bis 999 Gefechtstote im Jahr $zusatz, mindestens eine Seite ist ein Staat."
        "SPANNUNG" -> "Wie oft Medien über Truppen, Manöver und Angriffe berichten: letzte sieben Tage gegen die Wochen davor. Ab $zusatz gilt ein Gebiet als auffällig."
        "ENGPAESSE" -> "Schiffsdurchfahrten pro Tag, letzte sieben Tage gegen denselben Zeitraum im Vorjahr. Ab $zusatz Rückgang gilt ein Engpass als gestört."
        "ENSO" -> ensoKurz
        "KATASTROPHEN_LAND" -> "Stürme, Fluten, Waldbrände und Dürren der letzten 30 Tage mit Alarmstufe Orange oder Rot des Warnsystems GDACS. Rot heißt hohe Auswirkung."
        "DURCHBRUCH_THEMA" -> "Streng gefilterte Meldungen über Durchbrüche. Die Zahl zählt die letzten sieben Tage, darunter steht die jüngste Meldung."
        "BESCHLEUNIGT" -> "Kennzahlen, die sich in festen Abständen halbieren oder verdoppeln, weil die Entwicklung ihren eigenen Fortschritt antreibt."
        else -> ""
    }
    override fun lbWeg(key: String) = when (key) {
        "ZINSEN" -> "Zum Ausblick"; "TECHNIK" -> "Zu den Durchbrüchen"; else -> "Im Globus ansehen"
    }
    override fun lbGegen(a: String, b: String) = "$a gegen $b"
    override val lbUnd = " und "
    override fun lbImMinus(n: Int) = "$n im Minus"
    override fun lbAbSchwelle(n: Int, schwelle: String) = "$n ab $schwelle"
    override fun lbGestoert(n: Int) = "$n gestört"
    override val mtWelt = "Weltereignisse im Depot"
    override val mtWeltHinweis = "Höchstens eine am Tag, wenn ein Ereignis einen Wert in deinem Depot betrifft."
    override fun mtWeltText(was: String, werte: String) = "$was. Betrifft $werte."
    override fun mtWeltEngpass(name: String, prozent: String) = "$name: Durchfahrten $prozent gegen Vorjahr"
    override fun mtWeltSpannung(name: String, faktor: String) = "$name: $faktor-fach mehr Berichte als zuvor"
    override fun mtWeltWerte(namen: List<String>, rest: Int) = namen.joinToString(", ") + (if (rest > 0) " und $rest weitere" else "")
    override val weltlageTitel = "Weltlage"
    override val ensoTitel = "El Niño und La Niña"
    override val ensoStatus = "Status"
    override val ensoIndex = "Index (RONI)"
    override val ensoStaerke = "Stärke"
    override fun ensoZustand(z: String) = when (z) {
        "EL_NINO_ADVISORY" -> "El Niño läuft"
        "EL_NINO_WATCH" -> "El Niño möglich"
        "LA_NINA_ADVISORY" -> "La Niña läuft"
        "LA_NINA_WATCH" -> "La Niña möglich"
        "NEUTRAL" -> "Neutral"
        else -> "Unbekannt"
    }
    override fun ensoStaerkeText(n: Int) = when (n) {
        4 -> "El Niño sehr stark"
        3 -> "El Niño stark"
        2 -> "El Niño mäßig"
        1 -> "El Niño schwach"
        0 -> "Neutral"
        -1 -> "La Niña schwach"
        -2 -> "La Niña mäßig"
        -3 -> "La Niña stark"
        -4 -> "La Niña sehr stark"
        else -> ""
    }
    override val ensoStatusInfo = "Alarmsystem der NOAA. Watch: Die Bedingungen für El Niño oder La Niña sind in den nächsten sechs Monaten günstig. Advisory: Das Ereignis ist da und soll anhalten."
    override val ensoIndexInfo = "Relativer Oceanic Niño Index (RONI): Abweichung der Meerestemperatur in der Niño-3.4-Region am Äquator im Pazifik, bereinigt um die Erwärmung der tropischen Ozeane insgesamt, als Dreimonatsmittel in Grad Celsius.\n\n• Ab +0,5 gilt El Niño, unter −0,5 La Niña (NOAA).\n• Die Linie zeigt die letzten 24 Dreimonatswerte, gestrichelt die Schwellen."
    override val ensoStaerkeInfo = "Stufen der NOAA nach dem Dreimonatswert: schwach ab 0,5, mäßig ab 1,0, stark ab 1,5, sehr stark ab 2,0 Grad, bei La Niña entsprechend im Minus."
    override val ensoFolgenNino = "Folgen von El Niño"
    override val ensoFolgenNinoInfo = "• Typisch: trockener in Indonesien, Malaysia, Australien, Indien (Monsun) und im südlichen Afrika, nasser an der Küste Perus und im Süden der USA (NOAA).\n• Betroffene Anbaugebiete: Palmöl (Indonesien, Malaysia), Zucker (Indien, Thailand), Weizen (Australien), Robusta-Kaffee (Vietnam, Indonesien), Reis.\n• Vor Peru fällt der Fang der Sardellen, aus denen Fischmehl entsteht.\n\nWie stark Preise reagieren, schwankt je Ereignis."
    override val ensoFolgenNina = "Folgen von La Niña"
    override val ensoFolgenNinaInfo = "• Typisch: trockener im Süden der USA und in Argentinien, nasser in Indonesien, Malaysia und Australien (NOAA).\n• Betroffene Anbaugebiete: Sojabohnen, Mais und Weizen in Argentinien, Südbrasilien und den USA.\n\nWie stark Preise reagieren, schwankt je Ereignis."
    override val spTitel = "Spannungsgebiete"
    override val spErklaerung = "Berichte über Truppen, Manöver, Mobilmachung und Angriffe: letzte sieben Tage gegen die Wochen davor."
    override fun spName(id: String) = when (id) {
        "UKR" -> "Ukraine und Russland"
        "TWN" -> "Taiwan"
        "NAO" -> "Iran und Israel"
        "GOL" -> "Persischer Golf"
        "ROT" -> "Rotes Meer und Jemen"
        "KOR" -> "Korea"
        "SCS" -> "Südchinesisches Meer"
        "KAS" -> "Indien und Pakistan"
        "BAL" -> "Ostsee und Belarus"
        "VEN" -> "Venezuela"
        "SDN" -> "Sudan"
        "SAH" -> "Sahel"
        else -> id
    }
    override fun spSuche(id: String) = when (id) {
        "UKR" -> "Ukraine Russland Truppen"
        "TWN" -> "Taiwan Militär Manöver"
        "NAO" -> "Iran Israel Militär Angriff"
        "GOL" -> "Persischer Golf Tanker Marine"
        "ROT" -> "Rotes Meer Huthi Angriff"
        "KOR" -> "Nordkorea Raketen Militär"
        "SCS" -> "Südchinesisches Meer Philippinen Küstenwache"
        "KAS" -> "Indien Pakistan Grenze Militär"
        "BAL" -> "Belarus Polen Litauen Truppen"
        "VEN" -> "Venezuela Militär USA"
        "SDN" -> "Sudan Kämpfe"
        "SAH" -> "Sahel Mali Burkina Faso Angriffe"
        else -> id
    }
    override fun spGegenDavor(faktor: String) = "$faktor gegen die Wochen davor"
    override val spFaktor = "Berichtsdichte"
    override val spFaktorInfo = "Anteil der Artikel zu dieser Region (Truppen, Manöver, Mobilmachung, Angriffe) an allen erfassten englischsprachigen Artikeln: letzte sieben Tage geteilt durch den Schnitt der Tage davor im selben 30-Tage-Fenster.\n\n• 1,0× heißt unverändert, 2,0× doppelt so dichte Berichterstattung.\n• Ein Anstieg zeigt mehr Berichterstattung. Über den Ausgang sagt er nichts."
    override val spArtikel = "Artikel in 7 Tagen"
    override val spArtikelInfo = "Treffer der Suche in den letzten sieben Tagen. Fehlen im Index Tage, liegt die Zahl unter der tatsächlichen."
    override fun spKonflikte(jahr: Int) = "Konflikte ($jahr)"
    override fun spKonfliktZeile(a: String, b: String, stufe: Int) = "$a gegen $b (Stufe $stufe)"
    override fun spKonfliktAnzahl(n: Int) = if (n == 1) "1 Konflikt" else "$n Konflikte"
    override val spKonfliktInfo = "Stufe 2: ab 1000 Gefechtstote im Jahr, Stufe 1: 25 bis 999."
    // [Wirkungskette]
    override val wkTitel = "Was die Zahlen bedeuten"
    override fun wkLage(thema: String, lage: String): String {
        val n = when (thema) {
            "ARBEIT" -> "Arbeitsmarkt"
            "WACHSTUM" -> "Konjunkturdaten"
            "KONSUM" -> "Konsum"
            "STIMMUNG" -> "Stimmung"
            "IMMOBILIEN" -> "Immobilienmarkt"
            "PREISE" -> "Inflation"
            "LOEHNE" -> "Löhne"
            else -> thema
        }
        val a = when (lage) {
            "STAERKER" -> when (thema) { "PREISE", "LOEHNE" -> "höher"; "STIMMUNG" -> "besser"; else -> "stärker" }
            "SCHWAECHER" -> when (thema) { "PREISE", "LOEHNE" -> "niedriger"; "STIMMUNG" -> "schlechter"; else -> "schwächer" }
            else -> return "$n wie erwartet"
        }
        return "$n $a als erwartet"
    }
    override fun wkZinsKopf(zentralbank: String, fall: String) = "$zentralbank-Entscheid: " + when (fall) {
        "ZINS_STRAFFER" -> "Zins höher als erwartet"
        "ZINS_LOCKERER" -> "Zins niedriger als erwartet"
        else -> "Zins wie erwartet"
    }
    override fun wkFallKopf(fall: String) = when (fall) {
        "GOLDILOCKS" -> "Wachstum stärker, Preise niedriger als erwartet"
        "HEISS" -> "Wachstum und Preise höher als erwartet"
        "STAGFLATION" -> "Wachstum schwächer, Preise höher als erwartet"
        "SCHWACH_ENTSPANNT" -> "Wachstum und Preise niedriger als erwartet"
        "GEMISCHT" -> "Meldungen gegenläufig"
        else -> "Meldungen im Rahmen der Erwartung"
    }
    override fun wkArt(art: String) = when (art) {
        "STELLEN" -> "Beschäftigung"
        "STELLEN_ADP" -> "Beschäftigung (ADP)"
        "ARBEITSLOSENQUOTE" -> "Arbeitslosenquote"
        "ERSTANTRAEGE" -> "Arbeitslosenanträge"
        "ARBEITSLOSE_AENDERUNG" -> "Arbeitslosenzahl"
        "OFFENE_STELLEN" -> "Offene Stellen"
        "LOEHNE" -> "Löhne"
        "BIP" -> "Wirtschaftsleistung"
        "EINKAUFSMANAGER_ISM" -> "Einkaufsmanagerindex (ISM)"
        "EINKAUFSMANAGER" -> "Einkaufsmanagerindex"
        "PRODUKTION" -> "Industrieproduktion"
        "AUFTRAEGE" -> "Auftragseingang"
        "EINZELHANDEL" -> "Einzelhandelsumsatz"
        "KONSUMAUSGABEN" -> "Konsumausgaben"
        "VERTRAUEN" -> "Stimmungsindex"
        "ZEW" -> "ZEW-Erwartungen"
        "IMMOBILIEN" -> "Immobiliendaten"
        "VERBRAUCHERPREISE" -> "Verbraucherpreise"
        "VERBRAUCHERPREISE_TOKIO" -> "Verbraucherpreise Tokio"
        "ERZEUGERPREISE" -> "Erzeugerpreise"
        "PREISINDEX_UMFRAGE" -> "Preisindex der Einkaufsmanager"
        "INFLATIONSERWARTUNG" -> "Inflationserwartung"
        "ZINSENTSCHEID" -> "Leitzins"
        else -> art
    }
    override fun wkWertStatt(art: String, ist: String, referenz: String) = "${wkArt(art)} $ist statt $referenz"
    override fun wkWertGleich(art: String, ist: String) = "${wkArt(art)} $ist"
    override val wkVorwertZusatz = " (ohne Erwartung, gegen Vorwert)"
    override fun wkZentralbank(region: String) = when (region) {
        "US" -> "Fed"
        "EURO" -> "EZB"
        "UK" -> "BoE"
        "JP" -> "BoJ"
        "CH" -> "SNB"
        else -> "PBoC"
    }
    override fun wkWaehrungName(region: String) = when (region) {
        "US" -> "Dollar"
        "EURO" -> "Euro"
        "UK" -> "Pfund"
        "JP" -> "Yen"
        "CH" -> "Franken"
        else -> "Yuan"
    }
    override fun wkZins(folge: String, zentralbank: String) = when (folge) {
        "SENKUNG_WENIGER" -> "Zinssenkungen der $zentralbank unwahrscheinlicher"
        "ERHOEHUNG_MEHR" -> "Zinserhöhung der $zentralbank wahrscheinlicher"
        "SENKUNG_MEHR" -> "Zinssenkungen der $zentralbank wahrscheinlicher"
        "KEIN_DRUCK" -> "kaum Zinsdruck"
        "ZIELKONFLIKT" -> "$zentralbank im Zielkonflikt"
        "STRAFFER" -> "$zentralbank straffer als erwartet"
        "LOCKERER" -> "$zentralbank lockerer als erwartet"
        "LOCKERUNG_WENIGER" -> "weniger Lockerung der $zentralbank nötig"
        else -> "Lockerung der $zentralbank wahrscheinlicher"
    }
    override fun wkRendite(r: String) = when (r) {
        "HOEHER" -> "Renditen steigen eher"
        "TIEFER" -> "Renditen sinken eher"
        else -> "Renditen ohne klare Richtung"
    }
    override fun wkWaehrung(r: String, name: String) = when (r) {
        "HOEHER" -> "$name eher stärker"
        "TIEFER" -> "$name eher schwächer"
        else -> "$name ohne klare Richtung"
    }
    override fun wkBelastet(liste: String) = "$liste belastet"
    override fun wkGestuetzt(liste: String) = "$liste gestützt"
    override fun wkSektor(sektor: String) = when (sektor) {
        "TECH" -> "Tech"
        "IMMOBILIEN" -> "Immobilien"
        "VERSORGER" -> "Versorger"
        "KLEINWERTE" -> "Small Caps"
        "BANKEN" -> "Banken"
        "ZYKLIKER" -> "Zykliker"
        "ENERGIE" -> "Energie"
        "ROHSTOFFE" -> "Grundstoffe"
        "DEFENSIV" -> "Defensive"
        "EXPORTEURE" -> "Exporteure"
        else -> "Luxus"
    }
    override val wkUnd = "und"
    override fun wkOhneFolge(fall: String) = when (fall) {
        "GEMISCHT" -> "Die Meldungen weisen in verschiedene Richtungen. Keine eindeutige Folge."
        "ZINS_ERWARTET" -> "Der Entscheid war eingepreist. Die Reaktion hängt am Ausblick der Notenbank."
        else -> "Die Zahlen lagen im Rahmen der Erwartung. Neues für Zinsen und Märkte folgt daraus kaum."
    }
    override val wkReaktion = "Reaktion heute"
    override fun wkMarkt(schluessel: String) = when (schluessel) {
        "^TNX" -> "US-Rendite 10 Jahre"
        "DX-Y.NYB" -> "Dollar-Index"
        "EURUSD=X" -> "Euro"
        "GBPUSD=X" -> "Pfund"
        "JPY=X" -> "Yen"
        "CHF=X" -> "Franken"
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
        "PASST" -> "passt zur typischen Kette"
        "ANDERS" -> "anders als üblich"
        "GEMISCHT" -> "teils wie üblich, teils anders"
        else -> "zu wenig Bewegung für ein Urteil"
    }
    override fun wkZinsFutures(vorher: String, nachher: String) = "Leitzins in zwölf Monaten laut Futures: $nachher (Vortag $vorher)"
    override fun wkBedeutung(folge: String) = "Bedeutung: $folge"
    override fun wkRueckblick(kopf: String, folge: String?) = "Konjunkturdaten: $kopf" + (folge?.let { ", $it" } ?: "")
    override val wkBlattMeldungen = "Meldungen"
    override val wkBlattHerleitung = "Herleitung"
    override val wkBlattReaktion = "Reaktion am Tag"
    override val wkBlattSektoren = "Sektoren am Tag"
    override val wkBlattGrenzen = "Grenzen"
    override val wkBlattBelege = "Belege"
    override fun wkWarumZins(folge: String) = when (folge) {
        "SENKUNG_WENIGER" -> "Starkes Wachstum oder hohe Inflation lassen die Notenbank Senkungen aufschieben. Ihre Reaktionsfunktion folgt Inflation und Auslastung (Taylor-Regel)."
        "ERHOEHUNG_MEHR" -> "Wachstum und Preise liegen beide über der Erwartung. Beides spricht in der Reaktionsfunktion der Notenbank für höhere Zinsen."
        "SENKUNG_MEHR" -> "Schwächeres Wachstum oder niedrigere Inflation geben der Notenbank Raum für Senkungen."
        "KEIN_DRUCK" -> "Starkes Wachstum bei niedriger Inflation. Die Notenbank hat weder Grund zu straffen noch zu lockern."
        "ZIELKONFLIKT" -> "Schwaches Wachstum spricht für Senkungen, hohe Inflation dagegen. Die Richtung der Notenbank ist offen."
        "STRAFFER" -> "Der Zins liegt über der Erwartung. Der Markt setzt den künftigen Zinspfad höher an."
        "LOCKERER" -> "Der Zins liegt unter der Erwartung. Der Markt setzt den künftigen Zinspfad niedriger an."
        "LOCKERUNG_WENIGER" -> "Stärkere Aktivität verringert den Bedarf an Stützung durch die Zentralbank."
        else -> "Schwächere Aktivität oder niedrige Preise erhöhen den Bedarf an Lockerung."
    }
    override fun wkWarumRendite(r: String) = when (r) {
        "HOEHER" -> "Höhere Zinserwartungen und stärkeres Wachstum heben die Renditen von Staatsanleihen."
        "TIEFER" -> "Niedrigere Zinserwartungen und schwächeres Wachstum senken die Renditen von Staatsanleihen."
        else -> "Wachstumssorgen drücken die Renditen, Inflation hebt sie. Die Richtung ist offen."
    }
    override fun wkWarumWaehrung(r: String) = when (r) {
        "HOEHER" -> "Höhere Zinserwartungen und günstige Wachstumsnachrichten stärken die Währung. Eine stärkere Währung drückt die Auslandsumsätze der Exporteure in Heimatwährung."
        "TIEFER" -> "Niedrigere Zinserwartungen und schwächere Wachstumsnachrichten schwächen die Währung. Eine schwächere Währung stützt die Auslandsumsätze der Exporteure."
        else -> "Gegenläufige Kräfte, die Richtung ist offen."
    }
    override fun wkWarumSektor(sektor: String) = when (sektor) {
        "TECH" -> "Tech und Wachstumswerte: Die Gewinne liegen weit in der Zukunft, ihr Barwert reagiert stark auf Zinsen (lange Equity Duration). Steigende Zinsen belasten, sinkende stützen."
        "IMMOBILIEN" -> "Immobilien: Finanzierungskosten und der Renditevergleich mit Anleihen. Steigende Zinsen belasten, sinkende stützen."
        "VERSORGER" -> "Versorger: Hohe Dividenden bei hoher Verschuldung, die Aktien werden oft wie Anleihen gehandelt. Steigende Zinsen belasten, sinkende stützen. Der Zusammenhang ist in Studien schwächer als bei Immobilien."
        "KLEINWERTE" -> "Small Caps: Deutlich höherer Anteil variabel verzinster Schulden als bei Großkonzernen. Steigende Zinsen belasten, sinkende stützen."
        "BANKEN" -> "Banken: Wachstum stützt Kreditqualität und Zinsmargen, ein Abschwung belastet sie. Überraschend höhere Zinsen drückten Bankaktien in einer Studie, darum fehlen Banken bei Zinsentscheiden."
        "ZYKLIKER" -> "Zykliker: Die Gewinne folgen dem Konjunkturzyklus. Industrie und zyklischer Konsum reagieren früh auf Wachstumsdaten."
        "ENERGIE" -> "Energie: Höhere Inflation geht häufig mit höheren Energiepreisen einher. Aktivität in China und den USA treibt die Nachfrage nach Öl."
        "ROHSTOFFE" -> "Grundstoffe: Metallpreise reagieren auf die Aktivität in China und den USA."
        "DEFENSIV" -> "Defensive (Basiskonsum, Gesundheit): Die Gewinne schwanken wenig. In Schwächephasen sind sie gefragt."
        "EXPORTEURE" -> "Exporteure: Eine stärkere Heimatwährung drückt die Auslandsumsätze in Heimatwährung, eine schwächere stützt sie."
        else -> "Luxus: Der Absatz hängt stark an der Nachfrage aus China."
    }
    override val wkGrenzen = "Die Kette beschreibt die typische Reaktion. Der Markt kann anders reagieren, wenn die Zahl schon eingepreist war, andere Nachrichten überwiegen oder die Konjunkturlage den Zusammenhang dreht. Nach Studien gelten steigende Arbeitslosenzahlen in Aufschwüngen oft als gute Nachricht für Aktien und in Abschwüngen als schlechte. Die Tagesveränderung enthält auch alles andere, was an dem Tag passierte."
    override val wkReaktionErklaerung = "Tagesveränderung gegen den Schluss des Vortags. Als Treffer zählt: Rendite mehr als 2 Basispunkte, Währung mehr als 0,1 %, Sektor mehr als 0,2 Prozentpunkte gegenüber dem Index in der Richtung der Kette. Passt, wenn mindestens doppelt so viele Treffer wie Abweichungen vorliegen."
    override val wkBelege = "• Reaktionsfunktion: Clarida, Galí, Gertler (2000)\n• Zinsüberraschungen und Aktien: Bernanke, Kuttner (2005)\n• Arbeitsmarktdaten und Aktien je nach Konjunkturlage: Boyd, Hu, Jagannathan (2005)\n• Währung: Andersen, Bollerslev, Diebold, Vega (2003)\n• Equity Duration: Dechow, Sloan, Soliman (2004)\n• Banken: English, Van den Heuvel, Zakrajšek (2018)\n• China und Rohstoffe: Roache, IWF (2012)"
    override val wkBp = "Bp"
    override val globusMehrGesellschaft = "Gesellschaft"
    override val globusMehrStaat = "Staat"
    override val globusMehrEnergie = "Energie und Klima"
    override val globusMehrHandel = "Handel"
    override val globusMehrFeiertage = "Feiertage"
    override val globusMehrSchlagzeilen = "Schlagzeilen"
    override fun globusMehrLabel(k: de.troetenrepublik.app.engine.LaenderZusatz.Kennzahl) = when (k.name) {
        "LEBENSERWARTUNG" -> "Lebenserwartung"
        "MEDIANALTER" -> "Medianalter"
        "STADT" -> "Stadtbevölkerung"
        "WACHSTUM" -> "Einwohnerwachstum"
        "GEBURTEN" -> "Kinder je Frau"
        "ERWERB" -> "Erwerbsquote"
        "FRAUEN" -> "Erwerbsquote Frauen"
        "ARMUT" -> "Extreme Armut"
        "INTERNET" -> "Internetnutzung"
        "HOCHSCHULE" -> "Hochschulbesuch"
        "ALPHA" -> "Alphabetisierung"
        "STEUER" -> "Steuerquote"
        "MILITAER" -> "Militärausgaben"
        "FORSCHUNG" -> "Forschung (F&E)"
        "UEBERWEISUNG" -> "Überweisungen"
        "TOURISMUS" -> "Tourismus"
        "ERNEUERBAR" -> "Erneuerbare"
        "ENERGIEIMPORT" -> "Energieimporte netto"
        "STROM" -> "Stromzugang"
        "TREIBHAUS" -> "Treibhausgase/Kopf"
        "ALTER65" -> "Anteil über 65"
        "GESUNDHEIT" -> "Gesundheitsausgaben"
        "BILDUNG" -> "Bildungsausgaben"
        else -> k.name
    }
    override fun globusMehrInfo(k: de.troetenrepublik.app.engine.LaenderZusatz.Kennzahl) = when (k.name) {
        "LEBENSERWARTUNG" -> "Erwartete Lebensdauer eines Neugeborenen in Jahren, bei den heutigen Sterberaten.\n\n• Hohe Werte gehen mit besserer Gesundheitsversorgung und höherem Einkommen einher.\n• Niedrige Werte bremsen Arbeitskräfte und Konsum."
        "MEDIANALTER" -> "Alter, das die Bevölkerung in eine jüngere und eine ältere Hälfte teilt.\n\n• Niedrige Werte stehen für viele nachwachsende Arbeitskräfte, hohe für Alterung mit steigenden Renten- und Gesundheitskosten.\n• Schätzung der UN für 2025."
        "ALTER65" -> "Anteil der Bevölkerung ab 65 Jahren in Prozent. Hohe Werte zeigen eine alternde Gesellschaft mit steigenden Renten- und Pflegekosten und immer weniger Erwerbstätigen je Rentner."
        "STADT" -> "Anteil der Bevölkerung, der in Städten lebt, in Prozent. Hohe Werte gehen meist mit mehr Industrie und Dienstleistungen einher, niedrige mit viel Landwirtschaft."
        "WACHSTUM" -> "Jährliche Veränderung der Einwohnerzahl in Prozent.\n\n• Positiv heißt: mehr Arbeitskräfte und Konsumenten.\n• Negativ heißt: Die Bevölkerung schrumpft, meist durch niedrige Geburtenraten oder Abwanderung."
        "GEBURTEN" -> "Durchschnittliche Zahl der Kinder, die eine Frau im Lauf ihres Lebens bekäme.\n\n• Etwa 2,1 hält die Bevölkerung ohne Zuwanderung stabil (Ersatzniveau).\n• Darunter schrumpft sie langfristig, darüber wächst sie."
        "ERWERB" -> "Anteil der Menschen ab 15 Jahren, die arbeiten oder Arbeit suchen, in Prozent.\n\n• Niedrige Werte zeigen viele Rentner, Menschen in Ausbildung oder Nichterwerbstätige. Sie begrenzen das Wachstumspotenzial.\n• ILO-Schätzung."
        "FRAUEN" -> "Anteil der Frauen ab 15 Jahren, die arbeiten oder Arbeit suchen, in Prozent.\n\n• Der Abstand zur Erwerbsbeteiligung insgesamt zeigt ungenutztes Arbeitskräftepotenzial.\n• ILO-Schätzung."
        "ARMUT" -> "Anteil der Bevölkerung, der von weniger als 3,00 US-Dollar am Tag lebt (internationale Armutsgrenze der Weltbank, Preise von 2021).\n\nDie Werte stammen aus Haushaltserhebungen, deshalb steht das Erhebungsjahr dabei."
        "INTERNET" -> "Anteil der Bevölkerung, der in den letzten drei Monaten das Internet genutzt hat, in Prozent.\n\n• Hohe Werte erleichtern digitalen Handel und Dienstleistungen.\n• Niedrige Werte zeigen Aufholbedarf."
        "HOCHSCHULE" -> "Studierende in Prozent der Altersgruppe, die auf die Oberstufe folgt.\n\n• Der Wert kann über 100 % liegen, weil auch ältere Studierende zählen.\n• Hohe Werte zeigen viele Akademiker, niedrige wenig Hochschulbildung."
        "ALPHA" -> "Anteil der Erwachsenen ab 15 Jahren, die lesen und schreiben können, in Prozent. Niedrige Werte bremsen Ausbildung und Produktivität."
        "STEUER" -> "Steuereinnahmen des Zentralstaats in Prozent des BIP, ohne Sozialbeiträge.\n\n• Hohe Werte zeigen einen großen Staat oder hohe Abgaben.\n• Niedrige Werte lassen wenig Spielraum für Investitionen und führen oft zu Schulden."
        "GESUNDHEIT" -> "Ausgaben des Staates für Gesundheit in Prozent des BIP. Hohe Werte zeigen eine breite Versorgung, können den Haushalt aber stark belasten. Der OECD-Schnitt liegt bei etwa 6 %."
        "BILDUNG" -> "Ausgaben des Staates für Bildung in Prozent des BIP. Hohe Werte gelten langfristig als Investition in Fachkräfte und Wachstum. Die UNESCO empfiehlt mindestens 4 bis 6 %."
        "MILITAER" -> "Militärausgaben in Prozent des BIP.\n\n• Hohe Werte stehen für Konflikte oder Aufrüstung und binden Haushaltsmittel.\n• Die NATO-Staaten haben 2025 vereinbart, bis 2035 3,5 % des BIP für Verteidigung im engeren Sinn auszugeben."
        "FORSCHUNG" -> "Ausgaben von Staat, Unternehmen und Hochschulen für Forschung und Entwicklung in Prozent des BIP. Hohe Werte zeigen eine wissensbasierte Wirtschaft. Das EU-Ziel liegt bei 3 %."
        "UEBERWEISUNG" -> "Geld, das Auslandsarbeiter an Angehörige im Land schicken, in Prozent des BIP. Hohe Werte machen die Wirtschaft abhängig von der Konjunktur der Gastländer. Sie stützen aber Konsum und Währung."
        "TOURISMUS" -> "Einnahmen aus dem Reiseverkehr (Ausgaben ausländischer Besucher) in Prozent der Exporte von Waren und Dienstleistungen.\n\n• Hohe Werte zeigen Abhängigkeit vom Tourismus, der in Krisen schnell einbricht.\n• Eigene Berechnung aus Zahlungsbilanzdaten."
        "ERNEUERBAR" -> "Anteil erneuerbarer Energien am gesamten Endenergieverbrauch in Prozent, also nicht nur am Strom. Hohe Werte verringern die Abhängigkeit von fossilen Importen und von CO2-Preisen."
        "ENERGIEIMPORT" -> "Netto-Energieimporte in Prozent des Energieverbrauchs.\n\n• Positiv heißt: Das Land importiert Energie und hängt an Preisen und Lieferanten.\n• Negativ heißt: Das Land ist Netto-Exporteur und verbraucht weniger, als es fördert."
        "STROM" -> "Anteil der Bevölkerung mit Zugang zu Strom in Prozent. Werte unter 100 % zeigen Investitionsbedarf in Netze und Kraftwerke."
        "TREIBHAUS" -> "Treibhausgase ohne Landnutzung in Tonnen CO2-Äquivalent je Einwohner und Jahr (alle Kyoto-Gase).\n\nHohe Werte bedeuten höhere Kosten durch CO2-Preise und Klimaregeln, vor allem für Industrie und Energie."
        else -> ""
    }
    override val globusMehrFlaeche = "Fläche"
    override val globusMehrFlaecheInfo = "Gesamtfläche des Landes in Quadratkilometern, mit Binnengewässern."
    override fun globusMehrSprachen(n: Int) = if (n == 1) "Amtssprache" else "Amtssprachen"
    override val globusMehrSprachenInfo = "Sprachen mit amtlichem Status auf Landesebene."
    override fun globusMehrZeitzone(n: Int) = if (n == 1) "Zeitzone" else "Zeitzonen"
    override val globusMehrZeitzoneInfo = "Aktueller Versatz zur koordinierten Weltzeit (UTC), mit Sommerzeit.\n\n• Bei mehreren Zonen stehen der früheste und der späteste Versatz.\n• Börsen handeln nach Ortszeit."
    override val globusMehrBis = "bis"
    override fun globusMehrWeitere(n: Int) = "und $n weitere"
    override fun globusMehrExporte(jahr: Int) = "Warenexporte ($jahr)"
    override val globusMehrExporteInfo = "Wert aller Warenausfuhren des Landes in einem Jahr, in US-Dollar. Dienstleistungen zählen nicht mit."
    override val globusMehrGueter = "Wichtigste Exportgüter"
    override val globusMehrPartner = "Wichtigste Abnehmer"
    override fun globusMehrGruppe(g: de.troetenrepublik.app.engine.LaenderZusatz.Warengruppe) = when (g.name) {
        "AGRAR" -> "Agrar und Lebensmittel"
        "BRENNSTOFF" -> "Brennstoffe und Energie"
        "ERZ" -> "Erze und Metalle"
        "EDEL" -> "Edelmetalle und Edelsteine"
        "PHARMA" -> "Arzneimittel"
        "CHEMIE" -> "Chemie"
        "KUNSTSTOFF" -> "Kunststoffe und Gummi"
        "TEXTIL" -> "Textilien, Bekleidung, Leder"
        "HOLZ" -> "Holz und Papier"
        "MASCHINEN" -> "Maschinen"
        "ELEKTRO" -> "Elektronik und Elektrotechnik"
        "FAHRZEUG" -> "Fahrzeuge, Schiffe, Flugzeuge"
        "INSTRUMENT" -> "Instrumente und Uhren"
        "SONSTIGE" -> "Sonstige Waren"
        else -> g.name
    }
    override fun globusMehrGruppeInfo(g: de.troetenrepublik.app.engine.LaenderZusatz.Warengruppe, jahr: Int): String {
        val inhalt = when (g.name) {
            "AGRAR" -> "Tiere, Fleisch, Fisch, Milch, Gemüse, Obst, Getreide, Öle, Zucker, Kakao, Getränke und Tabak"
            "BRENNSTOFF" -> "Erdöl, Erdgas, Kohle und Mineralölprodukte"
            "ERZ" -> "Erze, Eisen, Stahl, Kupfer, Aluminium und andere Metalle samt Waren daraus"
            "EDEL" -> "Gold, Silber, Platin, Perlen und Edelsteine"
            "PHARMA" -> "Arzneimittel"
            "CHEMIE" -> "chemische Grundstoffe, Düngemittel, Farben, Kosmetik und Reinigungsmittel"
            "KUNSTSTOFF" -> "Kunststoffe, Kautschuk und Waren daraus"
            "TEXTIL" -> "Textilien, Bekleidung, Leder, Schuhe"
            "HOLZ" -> "Holz, Kork, Zellstoff, Papier und Bücher"
            "MASCHINEN" -> "Maschinen, Kessel, Turbinen, Motoren und Computer"
            "ELEKTRO" -> "elektrische Geräte, Telefone, Halbleiter und Batterien"
            "FAHRZEUG" -> "Autos, Lastwagen, Schienenfahrzeuge, Schiffe und Flugzeuge"
            "INSTRUMENT" -> "optische, medizinische und Messgeräte, Uhren"
            else -> "Salz, Zement, Glas, Keramik, Möbel, Spielzeug, Waffen und nicht zugeordnete Waren"
        }
        return "Anteil dieser Warengruppe an den Warenexporten ($jahr), Zolltarifkapitel ${g.kapitel}: $inhalt. Eine starke Konzentration auf eine Gruppe macht die Exporte abhängig von deren Preisen und Nachfrage."
    }
    override fun globusMehrPartnerInfo(land: String, jahr: Int) = "Anteil der Warenexporte, der nach $land geht ($jahr). Ein großer Abnehmer macht die Exporte abhängig von dessen Konjunktur und Zöllen."
    override val globusMehrFeiertagInfo = "Landesweiter gesetzlicher Feiertag. Die Börse legt ihre Handelstage selbst fest, an vielen Feiertagen bleibt sie geschlossen."
    override val globusMehrSpiegel = "Das Land meldet keine eigenen Handelsdaten. Die Zahlen stammen aus den Importen der acht größten Abnehmer und gelten nur für deren Anteil."
    // [Durchbrueche]
    override val dbTitel = "Durchbrüche"
    override val dbInfo = "Je Thema: neue Durchbrüche der letzten 14 Tage (nur eingetretene Ereignisse wie Zulassungen, Erstes, bestätigte Rekorde, Phase-3-Erfolge, Fachjournale), erwartete Meilensteine mit Termin, Ansätze in Entwicklung und Rückblick. Rückblick, Erwartetes und Kennzahlen sind von Hand gepflegt und nicht live. Keine Kaufempfehlung."
    // [Durchbrueche 0.70]
    override val dbNeu = "Neu"
    override val dbErwartet = "Erwartet"
    override val dbEntwicklung = "In Entwicklung"
    override val dbRueckblick = "Rückblick"
    override val dbBeschleunigt = "Beschleunigt sich selbst"
    override val dbKeineNeuen = "Keine neuen Durchbrüche in den letzten 14 Tagen"
    override val dbLog = "Logarithmisch: gleiche Höhe, gleicher Faktor"
    override fun dbNeuAnzahl(n: Int) = "$n neu"
    override val dbZuletzt = "Zuletzt"
    override fun dbZuletztDatum(jahr: Int, monat: Int, tag: Int, mitTag: Boolean): String {
        val name = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")[(monat - 1).coerceIn(0, 11)]
        return if (mitTag) "$tag. $name" else "$name $jahr"
    }
    override val dbNichtGeladen = "Nicht geladen"
    override fun dbArt(art: String) = when (art) {
        "angekuendigt" -> "angekündigt"
        "hersteller" -> "laut Hersteller geplant"
        "betreiber" -> "laut Betreiber geplant"
        "register" -> "Studienende laut Register"
        else -> ""
    }
    override fun dbBis(jahr: Int) = "bis $jahr"
    override fun dbStand(jahr: Int, monat: Int) = "Rückblick und Erwartetes: Stand " + listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")[(monat - 1).coerceIn(0, 11)] + " $jahr"
    override fun dbRate(faellt: Boolean, monate: Int): String {
        val zeit = if (monate < 18) "$monate Monate" else (kotlin.math.round(monate / 12.0 * 10) / 10.0).toString().replace('.', ',').removeSuffix(",0") + " Jahre"
        return (if (faellt) "Halbiert sich etwa alle " else "Verdoppelt sich etwa alle ") + zeit
    }
    override fun dbAlter(n: Long, einheit: de.troetenrepublik.app.engine.Durchbrueche.Einheit) = when (einheit) {
        de.troetenrepublik.app.engine.Durchbrueche.Einheit.MINUTEN -> "$n Min."
        de.troetenrepublik.app.engine.Durchbrueche.Einheit.STUNDEN -> "$n Std."
        de.troetenrepublik.app.engine.Durchbrueche.Einheit.TAGE -> "$n T."
    }
    override val heatmapOhneBranche = "Sonstige"
    override val zeitraumEigener = "Eigener Zeitraum"
    override val zeitraumTitel = "Zeitraum wählen"
    override val zeitraumStart = "Startdatum"
    override val zeitraumEnde = "Enddatum"
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
    override fun kurzerHandelstag(time: String) = "Verkürzter Handelstag · Schluss $time Uhr"

    override val portfolio = "Depot"
    override val cash = "Cash"
    override val openProfile = "Profil öffnen"
    override val brokerage = "Brokerage"
    override val securities = "Wertpapiere"
    override val cashAccount = "Verrechnungskonto"
    override val total = "Gesamt"
    override val reloadPrices = "Kurse erneut laden"
    override val switchMetric = "Kennzahl wechseln"
    override val watchlist = "Watchlist"
    override val savingsPlans = "Sparpläne"
    override val orders = "Orders"
    override val analytics = "Analytics"
    override val statistics = "Statistik"
    override val investments = "Anlagen"
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
        "SALES" -> "Verkäufe"; "DEPOSITS" -> "Einzahlungen"; "EARNINGS" -> "Erträge"; "REFUNDS" -> "Rückzahlungen"; "TAX_REFUNDS" -> "Steuererstattungen"
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
    override val alsWidget = "Als Widget"
    override val addToWatchlist = "Zur Watchlist"
    override val wllNeu = "Neue Liste"
    override val wllName = "Name"
    override val wllUmbenennen = "Umbenennen"
    override val wllLoeschenTitel = "Liste löschen"
    override fun wllLoeschenText(name: String) = "Liste „$name“ wird gelöscht. Die Werte bleiben auf anderen Listen."
    override val wllWaehlen = "Watchlists wählen"

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
    override val isinPruefziffer = "Die Prüfziffer dieser ISIN stimmt nicht. Eingabe prüfen."
    override val isinUnvollstaendig = "Eine ISIN hat 12 Zeichen."
    override val clear = "Leeren"

    override val security = "Wertpapier"
    override val yourPosition = "Deine Position"
    override val quantity = "Anzahl"
    override val avgPrice = "Einstandskurs"
    override val value = "Wert"
    override val result = "Ergebnis"
    override val information = "Informationen"
    override val uebersetzen = "Übersetzen"
    override val uebersetzenOriginal = "Original"
    override val uebersetzenFehler = "Übersetzung nicht möglich"
    override val sourceWikipedia = "Wikipedia (CC BY-SA 4.0)"
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
    override val marketCap = "Börsenwert"
    override val enterpriseValue = "Unternehmenswert"
    override val peRatio = "Kurs-Gewinn-Verhältnis"
    override val forwardPe = "KGV (erwartet)"
    override val eps = "Gewinn je Aktie"
    override val epsZwoelf = "Gewinn je Aktie (12 Monate)"
    override val dividendYield = "Dividendenrendite"
    override val volume = "Volumen"
    override fun priceIn(ccy: String) = "Kurs in $ccy"
    override val volatility1y = "Schwankung pro Jahr"
    override val beta = "Beta"
    override val isin = "ISIN"
    override val isinCopied = "ISIN kopiert"
    override val isinKopieren = "ISIN kopieren"
    override fun kennzahlErklaerung(label: String): String? = when (label) {
        bid -> "Der Preis, zu dem du die Aktie gerade verkaufen kannst."
        ask -> "Der Preis, zu dem du die Aktie gerade kaufen kannst. Er liegt etwas über dem Geldkurs."
        spread -> "Der Abstand zwischen Kauf- und Verkaufspreis.\n\n• So viel verlierst du, wenn du sofort kaufst und gleich wieder verkaufst.\n• Je kleiner, desto günstiger ist der Handel."
        openPrice -> "Der erste Kurs des heutigen Handelstags."
        dayHigh -> "Der höchste Kurs heute."
        dayLow -> "Der niedrigste Kurs heute."
        prevClose -> "Der Schlusskurs des letzten Handelstags. Daran wird gemessen, wie viel die Aktie heute gestiegen oder gefallen ist."
        high52 -> "Der höchste Kurs der letzten zwölf Monate. Zusammen mit dem Tief zeigt er, wie stark die Aktie in einem Jahr geschwankt ist."
        low52 -> "Der niedrigste Kurs der letzten zwölf Monate. Zusammen mit dem Hoch zeigt er, wie stark die Aktie in einem Jahr geschwankt ist."
        marketCap -> "Was alle Aktien der Firma zusammen an der Börse wert sind: Kurs mal Zahl der Aktien. Damit vergleicht man, wie groß Firmen sind."
        enterpriseValue -> "Börsenwert plus Schulden minus Bargeld. Ungefähr so viel müsste man zahlen, um die ganze Firma samt Schulden zu kaufen."
        peRatio -> "Kurs geteilt durch den Gewinn je Aktie der letzten zwölf Monate.\n\n• Ein KGV von 15 heißt: Du zahlst 15 Jahresgewinne.\n• Ein niedriger Wert kann günstig bedeuten, ein hoher heißt meist, dass viel Wachstum erwartet wird."
        forwardPe -> "Kurs geteilt durch den Gewinn je Aktie, den Analysten für die nächsten zwölf Monate schätzen.\n\n• Liegt es deutlich unter dem KGV, erwarten sie steigenden Gewinn, liegt es darüber, fallenden.\n• Schätzungen können danebenliegen."
        eps -> "Der Gewinn der Firma in den letzten zwölf Monaten, geteilt durch die Zahl ihrer Aktien. Ist er negativ, hat die Firma Verlust gemacht."
        dividendYield -> "Die jährliche Ausschüttung im Verhältnis zum Kurs. 3 % heißt: Für 100 € in der Aktie bekommst du etwa 3 € im Jahr, solange die Dividende gleich bleibt."
        volume -> "Wie viele Aktien heute gehandelt wurden. Ein hohes Volumen zeigt, dass gerade viel los ist."
        volatility1y -> "Wie stark der Kurs im letzten Jahr geschwankt hat. Über 40 % ist sehr unruhig, unter 20 % eher ruhig."
        beta -> "Wie stark die Aktie im Mittel mit dem Markt mitgeht.\n\n• 1 heißt: Steigt oder fällt der Markt um 1 %, bewegt sich die Aktie im Mittel ebenso.\n• 2 heißt doppelt so stark, 0,5 halb so stark.\n• Eine Aktie kann stark schwanken und trotzdem ein kleines Beta haben, wenn ihre Schwankungen wenig mit dem Markt zu tun haben."
        isin -> "Die internationale Kennnummer des Wertpapiers. Sie ist weltweit eindeutig, damit findest du es bei jeder Bank."
        kind -> "Die Art des Wertpapiers, zum Beispiel Aktie, ETF oder Kryptowert."
        sector -> "Der grobe Wirtschaftsbereich der Firma, zum Beispiel Technologie oder Gesundheit."
        industry -> "Die genauere Branche innerhalb des Sektors."
        country -> "Das Land, in dem die Firma ihren Sitz hat."
        employees -> "Wie viele Menschen bei der Firma arbeiten, laut dem letzten Bericht."
        exchange -> "Die Börse, von der der angezeigte Kurs stammt."
        bkKuv -> "Börsenwert geteilt durch den Umsatz der letzten zwölf Monate.\n\n• Hilft bei Firmen, die noch keinen Gewinn machen.\n• Ein KUV von 2 heißt: Die Firma kostet zwei Jahresumsätze.\n• Was als hoch gilt, hängt stark von der Branche ab."
        priceToBook -> "Kurs geteilt durch den Buchwert je Aktie, also das Eigenkapital laut Bilanz. Unter 1 kostet die Aktie weniger, als laut Bilanz an Eigenkapital dahintersteht."
        bkEvEbitda -> "Unternehmenswert (Börsenwert plus Schulden minus Bargeld) geteilt durch den Gewinn vor Zinsen, Steuern und Abschreibungen.\n\nDie Schulden zählen mit, deshalb lassen sich Firmen mit vielen und wenigen Schulden besser vergleichen."
        bkPeg -> "KGV geteilt durch das erwartete Gewinnwachstum.\n\n• Um 1 gilt die Aktie für ihr Wachstum als fair bewertet, deutlich darüber als teuer.\n• Fehlt der Wert bei Yahoo, rechnet der Aktienfinder mit dem KGV der letzten zwölf Monate und dem Gewinnwachstum zum nächsten Jahr."
        bkFcfRendite -> "Freier Cashflow der letzten zwölf Monate (operativer Cashflow minus Investitionen) geteilt durch den Börsenwert.\n\nSo viel Geld bleibt im Jahr übrig, gemessen an dem, was die ganze Firma an der Börse kostet."
        returnOnEquity -> "Gewinn geteilt durch das Eigenkapital.\n\n• Zeigt, wie gut die Firma mit dem Geld ihrer Eigentümer wirtschaftet.\n• Über 20 % gilt als stark."
        bkGesamtkapitalRendite -> "Gewinn geteilt durch das ganze Vermögen der Firma. Zeigt, wie viel sie aus Fabriken, Geräten und Geldmitteln herausholt."
        grossMargin -> "Anteil vom Umsatz, der nach den direkten Kosten der Ware übrig bleibt. Eine hohe Bruttomarge spricht für Preismacht."
        operatingMargin -> "Anteil vom Umsatz, der nach allen Kosten des laufenden Geschäfts übrig bleibt, vor Zinsen und Steuern."
        bkEbitdaMarge -> "Anteil vom Umsatz, der vor Zinsen, Steuern und Abschreibungen übrig bleibt."
        profitMargin -> "Anteil vom Umsatz, der am Ende als Gewinn bleibt."
        bkVerschuldung -> "Schulden geteilt durch das Eigenkapital.\n\n• 1 heißt: so viele Schulden wie Eigenkapital.\n• Hohe Werte machen eine Firma anfällig, wenn die Zinsen steigen oder das Geschäft schwächelt."
        bkLiquiditaet -> "Kurzfristiges Vermögen geteilt durch kurzfristige Schulden. Über 1 kann die Firma die Rechnungen des nächsten Jahres aus vorhandenen Mitteln bezahlen."
        totalCash -> "Bargeld und schnell verkaufbare Anlagen der Firma."
        totalDebt -> "Alle verzinsten Schulden der Firma."
        freeCashflow -> "Geld, das nach den Ausgaben für das laufende Geschäft und für Investitionen übrig bleibt. Daraus zahlt eine Firma Dividenden, kauft Aktien zurück oder tilgt Schulden."
        dividendPerShare -> "Was die Firma im Jahr je Aktie ausschüttet."
        payoutRatio -> "Anteil vom Gewinn, der als Dividende ausgezahlt wird. Über 100 % zahlt die Firma mehr aus, als sie verdient."
        bkDividendeFuenf -> "Die mittlere Dividendenrendite der letzten fünf Jahre. Liegt die heutige deutlich darüber, ist die Aktie billiger geworden oder die Dividende gestiegen."
        bkDividendenWachstum -> "Wie stark die Dividende je Aktie in den letzten Jahren pro Jahr gestiegen ist."
        bkLeerQuote -> "Leer verkaufte Aktien als Anteil am Streubesitz.\n\n• Leerverkäufer wetten auf fallende Kurse.\n• Ab 10 % viele, ab 20 % sehr viele."
        bkLeerTage -> "Wie viele Handelstage es bei normalem Umsatz dauern würde, bis alle Leerverkäufer ihre Aktien zurückgekauft hätten."
        bkInsider -> "Anteil der Aktien, die Gründer, Vorstand und Aufsichtsrat halten."
        bkInstitutionen -> "Anteil der Aktien bei Fonds, Versicherern, Banken und anderen großen Anlegern."
        bkAktien -> "Wie viele Aktien es von der Firma gibt."
        bkAktienVeraenderung -> "Wie sich die Zahl der Aktien zum Vorjahr verändert hat.\n\n• Weniger Aktien heißt meist Rückkäufe, das erhöht deinen Anteil an der Firma.\n• Mehr Aktien verwässern ihn."
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
        bkZinsdeckung -> "Gewinn vor Zinsen und Steuern geteilt durch die Zinskosten des letzten Geschäftsjahres.\n\n• Zeigt, wie oft die Firma ihre Zinsen verdient.\n• Unter 2 wird es knapp.\n• Ohne gemeldete Zinskosten bleibt das Feld leer."
        bkRoic -> "Gewinn vor Zinsen nach Steuern geteilt durch das investierte Kapital.\n\n• Zeigt, wie viel die Firma aus dem Geld macht, das Eigentümer und Kreditgeber eingesetzt haben.\n• Ab 15 % gilt der Wert als stark.\n• Das investierte Kapital ist hier Eigenkapital plus Schulden, Bargeld ist nicht abgezogen."
        bkOwnerEarnings -> "Vereinfacht nach Buffett: operativer Cashflow minus alle Investitionen in Anlagen, geteilt durch den Börsenwert.\n\nBuffett zieht nur die Investitionen ab, die das Geschäft erhalten. Bei wachsenden Firmen liegt der Wert deshalb eher zu niedrig."
        kgvSchnitt -> "Kurs geteilt durch den mittleren Gewinn je Aktie der letzten Geschäftsjahre, meist vier. Glättet Gewinnspitzen und Verlustjahre, die das normale KGV verzerren, vor allem bei Zyklikern."
        else -> null
    }
    override val kind = "Gattung"
    override val sector = "Sektor"
    override val industry = "Branche"
    override val country = "Land"
    override val employees = "Mitarbeitende"
    override val website = "Website"
    override val exchange = "Handelsplatz"
    override val priceAlerts = "Preismarken"
    override val derivatives = "Derivate"
    override val news = "News"
    override val events = "Ereignisse"
    override val financials = "Finanzen"
    override val yearly = "Jährlich"
    override val quarterly = "Quartal"
    override val insights = "Einschätzungen"
    override val analysts = "Analysten"
    override fun fromEstimates(n: Int) = " aus $n Einschätzungen"
    override fun targetPrice(price: String) = ", Kursziel $price"
    override fun toTargetLage(prozent: String, richtung: Int) = if (richtung == 0) "Kurs am Kursziel" else if (richtung > 0) "$prozent bis zum Kursziel" else "Kurs $prozent über dem Kursziel"
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
    override fun epsStatt(actual: String, estimate: String) = "$actual, erwartet $estimate"
    override val description = "Beschreibung"
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
    override val revenueGrowth = "Umsatz Quartal"
    override val earningsGrowth = "Gewinn Quartal"
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
    override val articleLoading = "Artikel wird geladen …"
    override val articleNoText = "Den Text dieser Seite kann die App nicht auslesen."

    override val calToggle = "Handelskalender"
    override fun calRealizedIn(month: String) = "Realisiert im $month"
    override val calPrevMonth = "Voriger Monat"
    override val calNextMonth = "Nächster Monat"
    override val calWeekdays = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    override val calRealized = "Realisiert"
    override val calTrades = "Geschäfte"
    override fun calWins(n: Int) = "$n Plus"
    override fun calLosses(n: Int) = "$n Minus"
    override fun calExpectedAmount(amount: String) = "erwartet $amount"
    override val calLegendReceived = "Dividende erhalten"
    override val calLegendExpected = "Dividende erwartet"
    override val calClosedTrades = "Abgeschlossene Geschäfte"
    override val calDayResult = "Tagesergebnis"
    override val calFees = "Gebühren"
    override val calAfterCosts = "Nach Gebühren und Steuern"
    override val calPositions = "Geschlossene Positionen"
    override val calLegendReview = "Rückblick"
    override val calReviewLaedt = "Wird geladen …"
    override val calReviewKeineKurse = "Für diesen Tag liegen keine Kurse vor."
    override val calTradeExpiry = "Verfall"
    override fun calTradeSell(qty: String, price: String) = "Verkauf · $qty Stk. zu $price"
    override val calDivExpected = "erwartet, nach Steuern"
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
    override val productCosts = "Produktkosten pro Jahr"
    override val derivativeRisk = "Hebelprodukte können zum Totalverlust des Einsatzes führen. Die Ex-ante-Kosteninformation liegt dieser Übersicht zugrunde."
    override val cashAfter = "Guthaben danach"
    override val notePlaceholder = "Notiz (optional), etwa dein Grund für die Order"
    override val placeOrder = "Order aufgeben"
    override val orderNotExecuted = "Die Order wurde nicht ausgeführt. Prüfe den aktuellen Kurs und dein Guthaben."
    override val initialStop = "Stoppreis zu Beginn"

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
        "LINKED" -> StrategieTexte.text(true, "zl.grund", emptyList())
        else -> error(code, "")
    }

    override val plan = "Plan"
    override val plans = "Pläne"
    override fun planCount(n: Int) = "$n ${if (n == 1) "Plan" else "Pläne"}"
    override val interval = "Intervall"
    override fun intervalLabel(i: PlanInterval) = when (i) {
        PlanInterval.WEEKLY -> "Wöchentlich"; PlanInterval.BIWEEKLY -> "Zweimal im Monat"; PlanInterval.MONTHLY -> "Monatlich"; PlanInterval.QUARTERLY -> "Vierteljährlich"
    }
    override val intervalBiweeklyDays = "am 2. und 16."
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

    override val enterTargetPrice = "Preismarke eingeben"
    override fun alertRises(price: String, pct: String) = "Mitteilung, sobald der Kurs auf $price steigt (+$pct)"
    override fun alertFalls(price: String, pct: String) = "Mitteilung, sobald der Kurs auf $price fällt (−$pct)"
    override val targetEqualsPrice = "Die Marke entspricht dem aktuellen Kurs"
    override val setAlert = "Preismarke setzen"
    override val whenRises = "wenn der Kurs steigt"
    override val whenFalls = "wenn der Kurs fällt"
    override fun setAt(price: String) = "gesetzt bei $price"
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
        if (art == "BAER") "$vomTief über dem Tief des Bärenmarkts. Ab 20 % gilt er als beendet." else "$vomTief über dem Tief vor dem Anstieg. Fällt der Index 20 % unter sein Hoch, beginnt ein Bärenmarkt.",
        when (golden) {
            true -> "Golden Cross" + (kreuzSeit?.let { " am $it" } ?: "") + ": die 50-Tage-Linie liegt über der 200-Tage-Linie."
            false -> "Death Cross" + (kreuzSeit?.let { " am $it" } ?: "") + ": die 50-Tage-Linie liegt unter der 200-Tage-Linie."
            null -> null
        },
        "Die Linie zeigt zwei Jahre, die waagrechte Linie die Schwelle zum Wechsel.",
    ).joinToString(" ")
    override val mpHinweis = "Faustregeln für die Phasen:\n\n" +
        "• Bärenmarkt: ein Index schließt 20 % unter seinem letzten Hoch\n" +
        "• Bullenmarkt: er steht wieder 20 % über seinem Tief\n" +
        "• Korrektur: ein Rückgang um 10 bis 20 % im Bullenmarkt\n\n" +
        "• Die Ampel zeigt Bulle (grün), Korrektur (gelb) und Bär (rot).\n• Die 200-Tage-Linie zeigt den langfristigen Trend.\n• Kreuzt die 50-Tage-Linie sie nach oben, heißt das Golden Cross, nach unten Death Cross.\n• Gerechnet aus zehn Jahren Tagesschlusskursen."
    override val cotTitel = "Terminmärkte"
    override fun cotMarkt(schluessel: String) = when (schluessel) {
        "sp500" -> "S&P 500"; "nasdaq" -> "Nasdaq 100"; "gold" -> "Gold"; "oel" -> "Rohöl (WTI)"; "euro" -> "Euro gegen Dollar"
        "anleihen" -> "US-Staatsanleihen 10 Jahre"; "bitcoin" -> "Bitcoin"
        "anleihen2" -> "US-Staatsanleihen 2 Jahre"; "anleihen30" -> "US-Staatsanleihen 30 Jahre"
        "yen" -> "Yen gegen Dollar"; "pfund" -> "Pfund gegen Dollar"; "franken" -> "Franken gegen Dollar"
        "kanada" -> "Kanadischer Dollar"; "australien" -> "Australischer Dollar"
        "silber" -> "Silber"; "platin" -> "Platin"; "palladium" -> "Palladium"; "kupfer" -> "Kupfer"
        "gas" -> "Erdgas (USA)"; "weizen" -> "Weizen"; "mais" -> "Mais"; "kaffee" -> "Kaffee"; "kakao" -> "Kakao"; "zucker" -> "Zucker"
        else -> schluessel
    }
    override val lpTitel = "Offene Leerverkäufe"
    override fun lpSumme(anteil: String, anzahl: Int) =
        if (anzahl == 1) "Ein Fonds setzt mit $anteil des Grundkapitals auf fallende Kurse" else "$anzahl Fonds setzen mit zusammen $anteil des Grundkapitals auf fallende Kurse"
    override fun lpVeraenderung(aenderung: String) = "$aenderung Prozentpunkte gegenüber vor vier Wochen"
    override val lpKeine = "Gerade keine veröffentlichte Position."
    override fun lpSeit(tag: String) = "seit $tag"
    override fun lpNotiz(land: String, stand: String) = "Stand $stand" + if (land in listOf("IT", "DE", "GB")) " · selbst importiert" else ""
    override fun lpGesamt(anteil: String) = "Leerverkäufer setzen zusammen mit $anteil des Grundkapitals auf fallende Kurse"
    override fun lpHinweis(land: String) = if (land == "GB") "Die britische Finanzaufsicht veröffentlicht nur die Summe aller Positionen je Aktie, ohne Namen. Gemeldet wird ab 0,2 % des Grundkapitals. Die Linie zeigt die Summe im letzten Jahr. Müssen sich Leerverkäufer eindecken, kann das den Kurs treiben." else "Die EU veröffentlicht Positionen ab 0,5 % des Grundkapitals mit Namen, die Summe liegt daher meist höher. Die Linie zeigt die Summe der veröffentlichten Netto-Positionen im letzten Jahr. Müssen sich Leerverkäufer eindecken, kann das den Kurs treiben."
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
    override val fdaHinweis = "Die US-Arzneimittelbehörde FDA veröffentlicht jede Woche Rückrufe von Arzneimitteln und Medizinprodukten. Klasse I heißt, dass das Produkt ernste Gesundheitsschäden verursachen kann. Die Zuordnung läuft über den Firmennamen und bekannte Tochterfirmen, einzelne Einträge können fehlen."
    override fun insiderNotiz(land: String, stand: String) = listOfNotNull(
        if (stand.isNotBlank()) "Stand $stand" else null,
        when (land) {
            "AU", "DK", "FI", "IS", "EE", "LV", "LT", "LU", "CY", "HU", "IT" -> "Beträge aus den Formularen gelesen"
            "CH" -> "Ohne Namen"
            "QA" -> "Selbst importiert, nur Stückzahlen"
            "FR", "NL", "GB", "HK", "CZ", "SE", "", "LK", "OM" -> null
            else -> "Selbst importiert"
        },
    ).joinToString(" · ")
    override val euSedi = "Kanadische Insider melden ihre Geschäfte an SEDI. Die Datenbank lässt sich nur im Browser abfragen."
    override val euSediLink = "SEDI öffnen"
    override fun euArt(art: String) = when (art) { "K" -> "Kauf"; "V" -> "Verkauf"; else -> "Sonstiges" }
    override fun euRolle(r: String) = when (r) { "S" -> "Geschäftsleitung"; "F" -> "Führungskraft"; "A" -> "Verwaltungsrat"; else -> "Nahestehende Person" }
    override val bbDafuer = "Positive Punkte"
    override val bbDagegen = "Negative Punkte"
    override val bbHinweis = "Automatisch aus Schlagzeilen der letzten 60 Tage und Kennzahlen zusammengestellt, der Beleg darunter zeigt den Anlass. Keine Anlageberatung."
    override val bbWaage = "Saldo"
    override fun bbStufe(stufe: Int) = listOf("Deutlich mehr Negatives", "Etwas mehr Negatives", "Ausgeglichen", "Etwas mehr Positives", "Deutlich mehr Positives").getOrElse(stufe) { "" }
    override fun bbWaageSatz(stufe: Int) = listOf(
        "Die negativen Punkte haben deutlich mehr Gewicht.",
        "Die negativen Punkte haben etwas mehr Gewicht.",
        "Positive und negative Punkte haben ungefähr gleiches Gewicht.",
        "Die positiven Punkte haben etwas mehr Gewicht.",
        "Die positiven Punkte haben deutlich mehr Gewicht.",
    ).getOrElse(stufe) { "" }
    override val bbGewichtKopf = "Gewicht" to "Beispiele"
    override val bbGewichte = listOf(
        "dreifach" to "Bilanzbetrug, Insolvenz",
        "doppelt" to "Gewinnwarnung, Ermittlung, Verlust, angehobene Prognose",
        "halb" to "Schwankung, zyklische Branche",
    )
    override val bbMehrfach = "Mehrere Schlagzeilen zum selben Thema zählen etwas mehr."
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
        "ANALYSTEN_VERHALTEN" -> "Die Note ist der Mittelwert der Empfehlungen (1 Kaufen bis 5 Verkaufen). Ab 3 raten die Analysten eher zum Halten, auch wenn das Kursziel über dem Kurs liegt."
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
        "GEWINNE_STEIGEN" -> "Ein niedrigeres erwartetes KGV heißt: Analysten rechnen mit steigenden Gewinnen. Ein hohes KGV heute lässt sich so erklären."
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
        "KONGRESS_KAUFT", "KONGRESS_VERKAUFT" -> "Abgeordnete und Senatoren melden Geschäfte mit Verzögerung. Gezählt werden Geschäfte nach dem Tag der Meldung, keine Beträge."
        "STUFEN_HOCH", "STUFEN_AB" -> "Gezählt werden Hoch- und Abstufungen der Analysten. Bestätigungen zählen nicht."
        "WARNZEICHEN" -> "Korrektur von Abschlüssen, Delisting-Hinweis oder Insolvenz meldet die Firma selbst bei der SEC. Das sind die schwersten Warnzeichen."
        "DIVIDENDE_SOLIDE" -> "Eine gedeckte Dividende bringt laufende Erträge und bleibt eher bestehen, wenn es schlechter läuft."
        "RENDITE_HOCH" -> "Die Eigenkapitalrendite zeigt, wie viel die Firma aus dem Geld der Aktionäre macht. Über 20 % gilt als hoch."
        "BEWERTUNG_NIEDRIG" -> "Ein niedriges KGV kann auf einen Kursabschlag hindeuten. Oft hat der Markt dafür Gründe."
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
        "ANALYSTEN_VERHALTEN" -> "Analysten sind verhalten: Note ${w[3]}, Kursziel ${w[0]} (${w[1]}), ${w[2]} Einschätzungen"
        "WACHSTUM" -> "Umsatz im Quartal um ${w[0]} gewachsen"
        "SCHRUMPFT" -> "Umsatz im Quartal um ${w[0]} geschrumpft"
        "GEWINNWACHSTUM" -> "Gewinn im Quartal um ${w[0]} gewachsen"
        "GEWINN_FAELLT" -> "Gewinn im Quartal um ${w[0]} gefallen"
        "MARGE_HOCH" -> "Hohe Gewinnmarge von ${w[0]}"
        "VERLUST" -> "Schreibt Verluste (Marge −${w[0]})"
        "NETTOKASSE" -> "Mehr Barmittel als Schulden, netto ${w[0]}"
        "SCHULDEN" -> "Hohe Nettoschulden von ${w[0]}, ${w[1]} des Börsenwerts"
        "CASHFLOW_POSITIV" -> "Freier Cashflow von ${w[0]}, ${w[1]} des Börsenwerts"
        "CASHFLOW_NEGATIV" -> "Verbraucht Geld: freier Cashflow −${w[0]}"
        "UEBERTROFFEN" -> "Gewinn je Aktie ${w[0]} von ${w[1]} Quartalen über den Erwartungen"
        "VERFEHLT" -> "Gewinn je Aktie ${w[0]} von ${w[1]} Quartalen unter den Erwartungen"
        "GEWINNE_STEIGEN" -> if (w[2].isEmpty()) "Analysten erwarten einen mehr als verdreifachten Gewinn in zwölf Monaten (KGV ${w[0]} → erwartet ${w[1]})" else "Analysten erwarten ${w[2]} Gewinn in zwölf Monaten (KGV ${w[0]} → erwartet ${w[1]})"
        "BEWERTUNG_HOCH" -> "Hohe Bewertung mit einem KGV von ${w[0]}"
        "SCHWANKUNG" -> "Schwankt stärker als der Markt (Beta ${w[0]})"
        "ZYKLISCH" -> "Zyklische Branche: Gewinne hängen stark an der Konjunktur"
        "DIVIDENDE_UNGEDECKT" -> "Dividende nicht vom Gewinn gedeckt (Ausschüttung ${w[0]})"
        "INSIDER_KAUFEN" -> "Insider kaufen, 90 Tage (Insider-Trend ${w[0]})"
        "INSIDER_VERKAUFEN" -> "Insider verkaufen, 90 Tage (Insider-Trend ${w[0]})"
        "LEERVERKAUF_HOCH" -> if (w.getOrNull(1) == "US") "Viele Leerverkäufer: ${w[0]} des Streubesitzes leer verkauft" else "Viele Leerverkäufer: ${w[0]} des Grundkapitals in gemeldeten Leerverkäufen"
        "FDA_ZULASSUNG" -> if (w[0] == "1") "Ein neues Mittel in den letzten zwölf Monaten von der FDA zugelassen" else "${w[0]} neue Mittel in den letzten zwölf Monaten von der FDA zugelassen"
        "FDA_RUECKRUF" -> if (w[0] == "1") "Ein Rückruf der FDA-Klasse I (ernste Gefahr) in den letzten zwölf Monaten" else "${w[0]} Rückrufe der FDA-Klasse I (ernste Gefahr) in den letzten zwölf Monaten"
        "NAHE_HOCH" -> "Kurs nahe am Jahreshoch: der Aufwärtstrend ist intakt"
        "WEIT_UNTER_HOCH" -> "Kurs ${w[0]} unter dem Jahreshoch: der Abwärtstrend läuft"
        "KONGRESS_KAUFT" -> "Kongress kauft, 90 Tage: ${sgKaeufeVerkaeufe(w[0].toInt(), w[1].toInt())}"
        "KONGRESS_VERKAUFT" -> "Kongress verkauft, 90 Tage: ${sgKaeufeVerkaeufe(w[0].toInt(), w[1].toInt())}"
        "STUFEN_HOCH" -> "Mehr Hoch- als Abstufungen, 30 Tage: ${sgHochAb(w[0].toInt(), w[1].toInt())}"
        "STUFEN_AB" -> "Mehr Ab- als Hochstufungen, 30 Tage: ${sgHochAb(w[0].toInt(), w[1].toInt())}"
        "WARNZEICHEN" -> if (w[0] == "1") "Ein schweres Warnzeichen in 24 Monaten bei der SEC gemeldet" else "${w[0]} schwere Warnzeichen in 24 Monaten bei der SEC gemeldet"
        "DIVIDENDE_SOLIDE" -> "Dividendenrendite ${w[0]}, gedeckt vom Gewinn (Ausschüttung ${w[1]})"
        "RENDITE_HOCH" -> "Hohe Eigenkapitalrendite von ${w[0]}"
        "BEWERTUNG_NIEDRIG" -> "Niedrig bewertet: erwartetes KGV ${w[0]}"
        else -> kennzahl
    }
    override val vdKaeufe1 = "Kauf"
    override val usHinweis = "Insider melden ihre Käufe und Verkäufe der SEC (Form 4): Führungskräfte, Aufsichtsräte, Aktionäre ab 10 %.\n\n• Gezeigt werden Käufe und Verkäufe der letzten 18 Monate an der Börse oder unter Privatleuten, ohne Zuteilungen, Optionsausübungen, Steuereinbehalte und Schenkungen.\n• Verkäufe aus Verkaufsplänen (Rule 10b5-1) sind gekennzeichnet."
    override val usOhneKontakt = "Neue Meldungen kommen mit jeder Fassung der App. Ohne Kontaktadresse unter Zugänge holt die App sie nicht selbst nach."
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
    override val erkTitel = "Erklär-Assistent"
    override val erkZweck = "Knopf „Erklären“ auf der Wertpapierseite, im Finanzblatt, bei den Zahlen zum Quartal und in „Abhängigkeiten und Risiken“."
    override val erkPunkte = listOf(
        "Eigener Schlüssel, Kosten nach dem Tarif des Anbieters",
        "Die Anfrage geht direkt an den Anbieter, ohne Server der App dazwischen",
        "Gesendet werden nur die Zahlen der Seite, kein Depot und keine persönlichen Angaben",
        "Keine Kauf- oder Verkaufsempfehlung, kein Kursziel, keine Prognose",
    )
    override val erkOpenai = "OpenAI-kompatibel"
    override val erkAdresse = "Basisadresse"
    override val erkAdresseUngueltig = "Adresse mit https:// beginnen, ohne Zugangsdaten"
    override val erkModell = "Modell"
    override val erkModellname = "Modellname"
    override val erkKnopf = "Erklären"
    override val erkBlatt = "Erklärung"
    override val erkNachfrage = "Nachfrage"
    override val erkSenden = "Senden"
    override val erkNochmal = "Erneut"
    override val erkSchluessel = "Schlüssel abgelehnt"
    override val erkLimit = "Limit des Anbieters erreicht"
    override val erkUeberlastet = "Anbieter überlastet"
    override val erkNetz = "Keine Verbindung"
    override val erkLeer = "Keine Antwort erhalten"
    override fun erkAnbieterSagt(m: String) = if (m.isBlank()) "Anbieter lehnt die Anfrage ab" else "Anbieter: $m"
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
    override val itClusterKurz = "Cluster-Kauf, 30 Tage"
    override val itHinweis = "Jede Person zählt einmal mit der Richtung ihrer Geschäfte im Zeitraum, gewichtet nach ihrer Beziehung zum Unternehmen:\n• Vorstand und Spitze ×3\n• weitere Führungskräfte ×2\n• Aufsichtsrat ×1,5\n• Nahestehende und Großaktionäre ×1\n\nVerkäufe aus Verkaufsplänen zählen halb.\n\nBei US-Werten zählt der Umfang im Verhältnis zum Bestand: Käufe ab 20 % und Verkäufe ab der Hälfte zählen anderthalbfach, Zukäufe unter 2 % und Verkäufe unter 10 % halb.\n\nGezählt werden nur Käufe und Verkäufe an der Börse."
    override val copyExecutives = "Insider"
    override val vdMeistgekauft = "Meiste Käufe der Führung, 90 Tage"
    override val vdMeistverkauft = "Meiste Verkäufe der Führung, 90 Tage"
    override val vdNeueste = "Neueste Meldungen"
    override val vdKaeufe = "Käufe"
    override val vdVerkaeufe = "Verkäufe"
    override val vdLeer = "Gerade keine Meldungen geladen. Später noch einmal öffnen."
    override fun insStand(bis: String) = "Daten bis $bis"
    override fun insLuecke(von: String, bis: String) = "Keine Daten zwischen $von und $bis"
    override val vdKarteTitel = "Käufe und Verkäufe der Führung"
    override val kernMeldungen = "Meldungen"
    override fun kernKaeufe(n: Int) = if (n == 1) "1 Kauf" else "$n Käufe"
    override fun kernVerkaeufe(n: Int) = if (n == 1) "1 Verkauf" else "$n Verkäufe"
    override fun kernNetto(betrag: String) = "netto $betrag"
    override fun kernGross(n: Int, groesster: String) = "$n · größter $groesster"
    override fun kernHalter(anteil: String) = "Institutionen $anteil"
    override fun kernHalterUnten(insider: String, anzahl: Int) = listOfNotNull(if (insider.isNotBlank()) "Insider $insider" else null, if (anzahl > 0) "${"%,d".format(anzahl).replace(',', '.')} Institute" else null).joinToString(" · ")
    override fun kernKaufen(n: Int) = "$n kaufen"
    override fun kernVerkaufen(n: Int) = "$n verkaufen"
    override fun kernHalten(n: Int) = "$n halten"
    override fun kernTrend(prozent: String, einheit: Int) = "$prozent " + (if (einheit == 1) "Aufrufe" else "Erwähnungen") + " zur Vorwoche"
    override fun kernTrendQuellen(n: Int) = if (n == 1) "1 Quelle" else "$n Quellen"
    override fun kernPositionen(n: Int) = if (n == 1) "1 Position" else "$n Positionen"
    override fun kernPersonen(n: Int) = if (n == 1) "1 Person" else "$n Personen"
    override val lpLeerverkaeufer = "Leerverkäufer"
    override fun trKennErw(n: Int) = (if (n == 1) "1 Erwähnung" else "${"%,d".format(n).replace(',', '.')} Erwähnungen") + " in 7 Tagen"
    override fun trKennBeitraege(n: Int) = (if (n == 1) "1 Beitrag" else "${"%,d".format(n).replace(',', '.')} Beiträge") + " in 7 Tagen"
    override val trYahooKurz = "gerade gefragt"
    override val grTitel = "Großaktionäre"
    override val grHinweis = "Gemeldete Stimmrechtsanteile ab 3 % nach dem Wertpapierhandelsgesetz, mit Instrumenten, zum Tag der jeweiligen Meldung."
    override fun vdAnzahl(kaeufe: Int, verkaeufe: Int) = listOfNotNull(
        if (kaeufe > 0) if (kaeufe == 1) "1 Kauf" else "$kaeufe Käufe" else null,
        if (verkaeufe > 0) if (verkaeufe == 1) "1 Verkauf" else "$verkaeufe Verkäufe" else null,
    ).joinToString(" · ")
    override fun vdOhneKurs(n: Int) = "(${if (n == 1) "1 Meldung" else "$n Meldungen"} in Fremdwährung nicht eingerechnet)"
    override fun vdZusammenfassung(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String) = "Zwölf Monate: " + listOfNotNull(
        if (kaeufe > 0) (if (kaeufe == 1) "1 Kauf" else "$kaeufe Käufe") + " für $kaufBetrag" else null,
        if (verkaeufe > 0) (if (verkaeufe == 1) "1 Verkauf" else "$verkaeufe Verkäufe") + " für $verkaufBetrag" else null,
    ).ifEmpty { listOf("keine Käufe oder Verkäufe") }.joinToString(", ")
    override fun vdArt(art: String) = art.ifBlank { "Meldung" }
    override fun vdRolle(rolle: String) = rolle
    override val alertMoveUngueltig = "Bitte zwischen 0,5 und 50 % eingeben"
    override fun notifAlertMoveText(pct: String, price: String) = "Heute $pct · Kurs $price"
    override val notiz = "Notiz"
    override val notizHinzufuegen = "Notiz hinzufügen"
    override val exportNotizen = "Notizen sind enthalten"
    override val notizLoeschen = "Notiz löschen"

    override fun availableAmount(amount: String) = "Verfügbar: $amount"
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
    override val spreadHint = "100 Basispunkte entsprechen einem Prozent. Bei 0 wird zum Mittelkurs gehandelt."
    override val appearance = "Erscheinungsbild"
    override val appearanceSystem = "System"
    override val appearanceLight = "Hell"
    override val appearanceDark = "Dunkel"
    override val appearanceSepia = "Sepia"
    override val logos = "Logos"
    override val logosFarbig = "Farbig"
    override val logosSchwarzWeiss = "Schwarz-weiß"
    override val logoForm = "Logoform"
    override val logoRund = "Rund"
    override val logoEckig = "Eckig"
    override val logoHintergrund = "Logohintergrund"
    override val hintergrundBehalten = "Behalten"
    override val hintergrundEntfernen = "Entfernen"
    override val trennlinien = "Trennlinien"
    override val trennAn = "An"
    override val trennAus = "Aus"
    override fun beispielName(k: Int) = "Beispiel ${'A' + k}"
    override val language = "Sprache"
    override val languageGerman = "Deutsch"
    override val languageEnglish = "English"
    override val coloredChart = "Chart in Grün und Rot"
    override val esDesign = "Design"
    override val esNeutral = "Neutral"
    override val esWichtigKategorie = "Kategorie bei Wichtigste"
    override fun esUntertitel(design: String, logosFarbig: Boolean, rund: Boolean) = "$design · Logos ${if (logosFarbig) "farbig" else "schwarz-weiß"}, ${if (rund) "rund" else "eckig"}"
    override val notifications = "Mitteilungen"
    override val refreshEvery = "Aktualisierung"
    override fun everySeconds(n: Int) = "alle $n Sekunden"
    override val statusLimited = "eingeschränkt"
    override val statusConnected = "verbunden"
    override val refreshNow = "Jetzt aktualisieren"
    override val exportPortfolio = "Depot exportieren"
    override val importPortfolio = "Depot importieren"
    override val aboutTitle = "Über die App"
    override val kaffeeTitel = "Buy me a coffee"
    override val aboutText = "Tryfolio ist ein Übungsdepot: Die Kurse sind echt und können verzögert eintreffen, gehandelt wird mit virtuellem Guthaben. Es fließt kein echtes Geld, es werden keine Wertpapiere erworben und es besteht keine Verbindung zu einer Bank oder einem Broker. Die App zeigt und erklärt, sie gibt keine Anlageberatung und keine Empfehlungen. Vergangene Entwicklungen sagen nichts über die Zukunft.\n\nDaten aus Registern und von Behörden ruft sie nur für deinen eigenen Gebrauch ab. Meldungen von US-Abgeordneten, Senatoren und Regierung dürfen nach US-Recht (5 U.S.C. § 13107) nicht kommerziell genutzt werden. Tryfolio ist nicht kommerziell.\n\nTryfolio ist freie Software unter der GNU Affero General Public License 3.0 oder neuer. Den Quelltext gibt es auf GitHub: github.com/regard2462/Tryfolio."
    override val version = "Version"
    override val resetDepot = "Depot zurücksetzen"
    override val resetTitle = "Depot zurücksetzen?"
    override fun resetText(amount: String) = "Alle Positionen, Orders, Sparpläne, Preismarken und Buchungen werden gelöscht. Das Guthaben startet wieder bei $amount."
    override val reset = "Zurücksetzen"
    override val personalData = "Persönliche Daten"
    override val firstName = "Dein Name"
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
    override val allowanceSingle = "Einzeln"
    override val allowanceJoint = "Paar"
    override val allowanceNotSetTitle = "Nicht eingerichtet"
    override fun allowanceUsed(used: String, year: Int, free: String) = "davon $used in $year genutzt · $free frei"
    override val activate = "Aktivieren"
    override val manage = "Verwalten"
    override fun taxOverviewYear(year: Int) = "Steuerübersicht $year"
    override val taxRate = "Steuersatz"
    override fun taxRateValue(pct: String) = "25 % + 5,5 % Soli = $pct"
    override fun taxWerteGeschaetzt(jahr: Int, mit: Int) = "Für $jahr liegen keine Steuerwerte vor, die App rechnet mit denen von $mit."
    override val taxWithheld = "Einbehaltene Steuern"
    override val realizedStockGains = "Realisierte Aktiengewinne"
    override val otherIncome = "Sonstige Erträge (ETF, Derivate, Zinsen, Dividenden)"
    override val lossPotStocks = "Verlusttopf Aktien"
    override val lossPotGeneral = "Verlusttopf Sonstige"
    override val privateGains = "Krypto im ersten Jahr (§ 23, ohne Abzug)"
    override val taxNote = "• Verluste werden zuerst mit den Erträgen des laufenden Jahres verrechnet, zu viel gezahlte Steuer kommt zurück, der Rest mindert spätere Gewinne\n• Aktienverluste nur mit Aktiengewinnen, alle anderen Verluste mit allen übrigen Erträgen\n• Verkauf nach FIFO, Gebühren mindern den Gewinn\n• Aktien-ETFs: 30 % steuerfrei (Teilfreistellung), Mischfonds 15 %\n• Krypto: kein Steuerabzug. Nach über einem Jahr ist der Gewinn steuerfrei, sonst gehört er mit dem persönlichen Steuersatz in die Steuererklärung (Freigrenze 1.000 € im Jahr)\n• Nicht enthalten: Vorabpauschale, ausländische Quellensteuer, Kirchensteuer\n• Zum Jahreswechsel wandern die Töpfe ins neue Jahr, der Freibetrag beginnt von vorn"
    override val taxData = "Steuerdaten"
    override val taxId = "Steuer-ID"
    override val editTaxId = "Steuer-ID bearbeiten"
    override val churchTax = "Kirchensteuer"
    override val notModelled = "nicht enthalten"
    override fun taxCertificate(year: Int) = "Jahressteuerbescheinigung $year anzeigen"

    override val filterTrade = "Handel"
    override val filterIncome = "Erträge"
    override val noActivity = "Noch keine Aktivität."
    override fun txLabel(t: TxType) = when (t) {
        TxType.DEPOSIT -> "Einzahlung"; TxType.WITHDRAWAL -> "Auszahlung"; TxType.BUY -> "Kauf"; TxType.SELL -> "Verkauf"
        TxType.FEE -> "Gebühr"; TxType.INTEREST -> "Zinsen"; TxType.KNOCKOUT -> "Knock-out"; TxType.EXPIRY -> "Fälligkeit"
        TxType.DIVIDEND -> "Dividende"; TxType.TAX -> "Steuer"; TxType.ALERT -> "Preismarke"; TxType.PLAN -> "Sparplan"
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
        val prefix = when (via) { "PLAN" -> "Sparplan · "; "ORDER" -> "Order · "; "DIVIDEND" -> "Dividende wiederangelegt · "; "SETTLE_COVER" -> "Ausgleich · "; else -> "" }
        return when (tag) {
            "TRANSFER_IN" -> "Übernommen · $qty zu $price"
            "BUY" -> "${prefix}Kauf $qty zu $price"
            "SELL" -> "${prefix}Verkauf $qty zu $price ($realized)"
            "FEE" -> stored.ifBlank { "" }
            "INTEREST" -> "$extra p. a. auf das Guthaben"
            "DIVIDEND" -> "Dividende $qty × $price"
            "KO_SETTLE" -> "Knock-out · Rückzahlung $price je Stück"
            "EXPIRY" -> "Fälligkeit · Auszahlung $price je Stück"
            "EXPIRY_WORTHLESS", "EXPIRY_SOLD_WORTHLESS" -> "Fälligkeit · wertlos verfallen"
            "SELL_OPEN" -> "${prefix}Verkauf zur Eröffnung $qty zu $price"
            "BUY_CLOSE" -> "${prefix}Rückkauf $qty zu $price ($realized)"
            "EXPIRY_SOLD" -> "Fälligkeit · Ausgleich $price je Stück"
            "ALERT_ABOVE" -> "Kurs über $extra · jetzt $price"
            "ALERT_BELOW" -> "Kurs unter $extra · jetzt $price"
            "ALERT_MOVE" -> "Tagesbewegung $extra · jetzt $price"
            "ALERT_SMA_UP", "ALERT_SMA_DOWN", "ALERT_VOLUME", "ALERT_EXDIV", "ALERT_INSIDER" -> alertArtText(tag, extra, price)
            "ALERT_INSIDER_SELL", "ALERT_HIGH52", "ALERT_LOW52", "ALERT_EARNINGS", "ALERT_8K" -> alertNeuText(tag, extra, price)
            "ALERT_ANALYST_UP", "ALERT_ANALYST_DOWN", "ALERT_RULE_BUY", "ALERT_RULE_SELL" -> alertNeuText(tag, stored, price)
            "ALERT_NEWS" -> stored
            "PLAN_FAILED" -> "Ausführung fehlgeschlagen · Guthaben reicht nicht für $extra"
            "PLAN_SKIPPED" -> if (extra == "1") "1 Termin ohne App übersprungen" else "$extra Termine ohne App übersprungen"
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
    override fun statementTitle(month: String) = "Kontoauszug $month"
    override val noBookings = "Noch keine Buchungen."
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
    override val welcome = "Willkommen"
    override val welcomeText = "Übungsdepot mit echten Kursen und virtuellem Geld."
    override val startCapital = "Startguthaben"
    override val startCapitalText = "Virtuelles Geld zum Start, später jederzeit aufstockbar."
    override val letsGo = "Los geht’s"
    override val noHistory = "Kein Verlauf verfügbar"

    override val toolsTitle = "Werkzeuge"
    override fun toolTitle(key: String) = when (key) {
        "POSITION_SIZE" -> "Positionsgröße"; "SELL_TAX" -> "Verkauf mit Steuer"; "HEBEL" -> "Hebel-Simulator"
        else -> key
    }
    override fun toolSubtitle(key: String) = when (key) {
        "LUMP_SUM" -> "Was aus einem Betrag geworden wäre"; "POSITION_SIZE" -> "Stückzahl passend zu Stopp und Risiko"
        "SELL_TAX" -> "Was nach Steuer und Gebühr bleibt"; "HEBEL" -> "Täglich gehebelter Kurs im Rückblick"
        else -> ""
    }
    override fun toolYears(n: Int) = if (n == 1) "1 Jahr" else "$n Jahre"
    override fun toolYearsDativ(n: Int) = if (n == 1) "1 Jahr" else "$n Jahren"
    override fun toolYearsKurz(n: Int) = "${n}J"
    override val toolMax = "Max"
    override val toolMonthly = "Monatliche Rate"
    override val toolLoading = "Kurse werden geladen …"
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
    override val toolStopDistance = "Abstand zum Stopp"
    override val toolDepotValue = "Depotwert"
    override val toolStopPrice = "Stoppkurs"
    override val toolLossPerUnit = "Verlust je Stück bis zum Stopp"
    override val toolMaxLoss = "Höchstverlust"
    override val toolCapital = "Kapitaleinsatz"
    override fun toolShareOfDepot(pct: String) = "$pct des Depots"
    override val toolDepotShare = "Anteil am Depot"
    override val toolMoreThanCash = "Der Kapitaleinsatz ist höher als das verfügbare Guthaben."
    override val toolPositionNote = "Der Stopp begrenzt den Verlust, solange der Kurs ohne Lücke fällt. Bei Kurssprüngen, etwa über Nacht, kann der Verlust größer sein."
    override val toolSellShare = "Anteil verkaufen"
    override val toolSellPrice = "Verkaufskurs"
    override val toolProceeds = "Verkaufserlös"
    override val toolRealized = "Gewinn oder Verlust"
    override val toolSellNote = "Gerechnet wie ein echter Verkauf nach FIFO mit Gebühren, Verlusttöpfen und Freistellungsauftrag. Es wird nichts gebucht."

    override val importPosition = "Position importieren"
    override val importIntro = "Dein Guthaben bleibt unverändert."
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
    override val maxDrawdown = "Größter Rückgang"
    override fun maxDrawdownAb(datum: String) = "Gerechnet ab $datum"
    override val largestPosition = "Größte Position"
    override val assetClasses = "Anlageklassen"
    override fun assetClass(key: String) = when (key) { "STOCKS" -> "Aktien"; "ETFS" -> "ETFs"; "CRYPTO" -> "Krypto"; "INDEX" -> "Indizes"; "DERIVATIVES" -> "Derivate"; else -> key }

    // Werkzeuge in Analytics
    override fun ptTitle(key: String) = when (key) {
        "PERFORMANCE" -> "Portfolio Performance"; "CHECK" -> "Depot-Check"; "FINDER" -> "Aktienfinder"; "WEALTH" -> "Vermögensaufbau"; "SPARPLAN" -> "Sparplan-Rückblick"; "WITHDRAWAL" -> "Entnahmeplan"
        "CRASH" -> "Crash-Test"; "SCENARIO" -> "Was wäre, wenn"; "DIVIDENDS" -> "Dividendenkalender"; "DIVERSIFICATION" -> "Streuung"; "REBALANCE" -> "Umschichten"; "OPTIONS" -> "Optionsrechner"; "STILLHALTER" -> "Optionen verkaufen"; "AUFFAELLIG" -> "Auffällige Optionen"; "STUFEN" -> "Neue Analystenstufen"
        "HAEUSER" -> "Haus-Rangliste"; "MARKT" -> "Markt schlagen"; "HEBEL" -> "Hebel-Simulator"; "GRENZE" -> "Effizienzgrenze"
        "BACKTEST" -> "Portfolio-Backtest"
        "LANGSICHT" -> "Börsengeschichte"
        "ROTATION" -> "Momentum-Rotation"
        "FAKTOREN" -> "Faktoren"
        else -> key
    }
    override val wzGruppeDepot = "Mein Depot"
    override val wzGruppePlanen = "Planen"
    override val wzGruppeFinden = "Aktien finden"
    override val ptCheckRules = "Faustregeln"
    override val ptCheckRulesSub = "Klumpen, Kosten, Reserve und weitere Faustregeln mit Ampel"
    override fun ptSubtitle(key: String) = when (key) {
        "PERFORMANCE" -> "Rendite, Kapitalbilanz"
        "CHECK" -> "Faustregeln mit Ampel"
        "FINDER" -> "Filter nach Bewertung, Qualität, Trend"
        "OPTIONS" -> "Strategien aus der Optionskette"
        "WEALTH" -> "Sparrate und Zinseszins über die Jahre"
        "SPARPLAN" -> "Dieselbe Rate seit damals"
        "WITHDRAWAL" -> "Zielvermögen und Entnahme in historischen Märkten"
        "CRASH" -> "Dein Depot oder ein Testdepot in den großen Krisen seit 2000"
        "SCENARIO" -> "Rechenbeispiele zu Rohstoffpreisen, Zinsen, Dollar und Ereignissen"
        "DIVIDENDS" -> "Ausschüttungen der nächsten zwölf Monate"
        "DIVERSIFICATION" -> "Regionen, Branchen, Währungen und Gleichlauf"
        "REBALANCE" -> "Was du für deine Zielgewichte kaufen oder verkaufen müsstest"
        "STILLHALTER" -> "Prämien für Calls und Puts"
        "AUFFAELLIG" -> "Volumen gegen offenes Interesse"
        "STUFEN" -> "Hoch- und Abstufungen je Unternehmen"
        "HAEUSER" -> "Trefferanteil der Analystenhäuser"
        "MARKT" -> "Was Studien über Fonds und Privatanleger zeigen"
        "HEBEL" -> "Täglich gehebelter Kurs im Rückblick"
        "GRENZE" -> "Schwankung und Rendite deiner Werte in anderer Gewichtung"
        "BACKTEST" -> "Feste Mischung aus Wertpapieren, Tageskurse seit Auflage"
        "LANGSICHT" -> "Anlageklassen seit 1871, Jahreswerte, real"
        "ROTATION" -> "Monatlich die stärksten Werte halten, im Rückblick getestet"
        "FAKTOREN" -> "Markt, Größe, Wert, Qualität und Momentum im Depot"
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
    override val ptInvested = "Eingesetzt"
    override val ptDepotToday = "Dein Depot heute"
    override val ptDepotTodayHint = "Wertpapiere, Verkaufserlöse und Dividenden"
    override fun ptWith(name: String) = "Wert mit $name"
    override val ptDifference = "Unterschied"
    override val ptPerYear = "Rendite pro Jahr"
    override fun ptNoPricesBefore(name: String, date: String) = "Für $name gibt es erst ab $date Kurse. Käufe davor lassen sich damit nicht nachspielen."
    override val ptSameMoneyNote = "Dieselben Käufe und Verkäufe am selben Tag und mit demselben Betrag im Vergleich, ohne Gebühren und Steuern."
    override fun ptCashNote(rate: String) = "Tagesgeld mit dem heutigen Zinssatz von $rate über die ganze Zeit."
    override fun ptSingleTitle(name: String) = "Einzelwerte gegen $name"
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
    override val ptWeak = "Schlechtere 10 % enden unter"
    override val ptStrong = "Beste 10 % enden über"
    override val ptPaidIn = "Eingezahlt"
    override val ptGrowth = "Davon Wertzuwachs"
    override fun ptTargetChance(pct: String) = "in $pct der Verläufe"
    override fun ptTargetReached(amount: String) = "Ziel $amount erreicht"
    override val ptTargetMedian = "Im mittleren Verlauf erreicht"
    override fun ptTargetWhen(years: Int) = if (years <= 1) "innerhalb eines Jahres" else "nach $years Jahren"
    override val ptTargetNever = "nicht in dieser Zeit"
    override fun ptInYears(n: Int) = if (n == 0) "Heute" else if (n == 1) "In 1 Jahr" else "In $n Jahren"
    override val ptBandLabel = "80 % der Verläufe"
    override val ptWealthNote = "Monte-Carlo-Simulation mit 1.000 zufälligen Verläufen: Jeden Monat schwankt die Rendite zufällig um den gewählten Wert. Der helle Bereich zeigt, wo 80 % der Verläufe landen, der dunklere die mittleren 50 %. Die Rechnung beruht auf Annahmen und sagt die Entwicklung nicht voraus."
    override val ptMonthlyWithdrawal = "Monatliche Entnahme"
    override fun ptIndexed(infl: String) = "Entnahme steigt mit der Inflation ($infl)"
    override fun ptLasts(pct: String, years: String) = "Das Geld reicht in $pct der Verläufe $years lang."
    override fun ptLeftAfter(years: Int) = if (years == 1) "Übrig nach 1 Jahr (mittlerer Verlauf)" else "Übrig nach $years Jahren (mittlerer Verlauf)"
    override val ptUsedUpAfter = "Aufgebraucht nach (mittlerer Verlauf)"
    override val ptSafeRate = "Rate, die in 90 % der Verläufe reicht"
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
    override fun ptDurationKurz(days: Long): String {
        val months = Math.round(days / 30.44).toInt()
        val y = months / 12
        val m = months % 12
        return when {
            days < 45 -> "$days T."
            y == 0 -> "$m Mon."
            m == 0 -> "$y J."
            else -> "$y J. $m Mon."
        }
    }
    override val ptCrashVerlust = "Verlust"
    override val ptCrashAmStaerksten = "Am stärksten"
    override val ptCrashErholung = "Erholung S&P 500"
    override val ptCrashErholungOffen = "offen"
    override fun ptEstimated(n: Int, total: Int) = "$n von $total Werten gab es damals noch nicht. Sie sind über ihr Beta geschätzt."
    override val ptCustomScenario = "Eigenes Szenario"
    override val ptMarketFalls = "Der S&P 500 fällt um"
    override val ptEstimatedDepot = "Dein Depot, geschätzt"
    override val ptCrashNote = "Wechselkurse, Dividenden und Käufe in der Krise sind nicht eingerechnet."
    override val ptCrashMein = "Mein Depot"
    override val ptCrashTest = "Testdepot"
    override val ptTestSuche = "Aktie oder ETF suchen"
    override val ptTestUebernehmen = "Aus meinem Depot übernehmen"
    override val ptTestLeer = "Noch keine Werte im Testdepot."
    override val ptTestBetrag = "Betrag in Euro"
    override val ptTestEntfernen = "Entfernen"
    override val ptTestSumme = "Summe"

    // [0.75] Werkzeuge
    override val ohneVerbindung = "Keine Verbindung. Die Daten ließen sich nicht laden."
    override val erneutVersuchen = "Erneut versuchen"
    override fun ptBetaRichtwert(n: Int, total: Int) =
        (if (n >= total) "Für keinen Wert gibt es ein gemessenes Beta. Alle folgen" else "Für $n von $total Werten fehlt ein gemessenes Beta. Sie folgen") +
            " dem Markt mit einem Richtwert: Aktien und Fonds 1,0, Krypto 1,5, Rohstoffe 0,3."
    override fun ptVarNoteBestand(anzahl: Int, schwankung: String) =
        "Aus $anzahl simulierten Verläufen. Rendite wie oben, Schwankung aus den Kursen des heutigen Bestands der letzten drei Jahre ($schwankung pro Jahr)."
    override val ptDivBrutto = "brutto, ohne deutsche Steuer"
    override val ptDivPerMonthBrutto = "Im Monat im Schnitt, vor Steuern"
    override val ptDivNoteBrutto = "Fortgeschrieben aus den Ausschüttungen der letzten zwölf Monate mit denselben Ex-Tagen und Beträgen je Stück, dem geschätzten Bestand und heutigem Wechselkurs. Thesaurierende ETFs erscheinen nicht. Brutto: Die Person zahlt die Steuern ihres Landes, die deutsche Abgeltungsteuer gilt für sie nicht."
    override fun toolEinStueckZuViel(jeStueck: String, erlaubt: String) = "Schon ein Stück riskiert $jeStueck und damit mehr als die erlaubten $erlaubt."
    override val afOhneAntwort = "Keine Antwort, meist fehlt die Verbindung. Der letzte Stand bleibt."
    override val abhAbgelaufen = "Abgelaufene Einordnungen"
    override fun abhAbgelaufenHinweis(datum: String) = "Galten bis $datum. Ausgeblendet, bis neue Daten kommen."
    override val ptCrashInfoTitel = "So wird gerechnet"
    override val ptCrashInfo = "• Werte mit Kursen aus der Krisenzeit: echter Verlauf\n• Jüngere Werte: Beta zum S&P 500 der letzten drei Jahre, aus Wochenrenditen\n• Derivate: neu bewertet zum veränderten Kurs des Basiswerts, Knock-outs fallen gegebenenfalls aus"
    override val ptFxTitel = "Währungsschock"
    override val ptFxInfo = "Die Heimatwährung des Depots steigt oder fällt gegenüber jeder anderen Währung.\n\nPositionen in Fremdwährung werden danach in Heimatwährung umgerechnet, ETFs nach der Währung ihrer Länder.\n\nGerechnet wird nur die Umrechnung, nicht die Reaktion der Unternehmen auf den Wechselkurs."
    override fun ptFxSteigt(waehrung: String) = "$waehrung steigt"
    override fun ptFxFaellt(waehrung: String) = "$waehrung fällt"
    override val ptFxUm = "Gegenüber jeder anderen Währung um"
    override val ptFxFremd = "In Fremdwährung"
    override fun ptFxZeile(anteil: String) = "In Fremdwährung: $anteil"
    override fun ptFxKeine(waehrung: String) = "Alle Positionen sind in $waehrung. Ein Währungsschock ändert nichts."
    override val szTitel = "Rechenbeispiele"
    override val szInfo = "Rechenbeispiel aus vergangenen Kursbewegungen, bei unverändertem Gesamtmarkt und Wechselkurs. Die Zusammenhänge sind schwach und ändern sich.\n\n• Gerechnet wird aus Vierwochenrenditen der letzten sechs Jahre. Es zählen nur belegte Zusammenhänge, alle anderen Positionen zählen 0.\n• Die Spanne reicht vom kleinsten bis zum größten gemessenen Zusammenhang, um die doppelte Messunsicherheit geweitet.\n• An neun Preisbewegungen von Öl, Gold und Kupfer seit 2013 stimmte die Richtung in allen 25 belegten Fällen, in 18 lag die Bewegung in der Spanne.\n• Bei Zinsen (Rendite zehnjähriger US-Anleihen) und Dollar war die Richtung in 4 von 29 Fällen falsch, deshalb stehen dort keine Zahlen aus der Messung. Gezeigt werden die Positionen mit bisherigem Zusammenhang und die mit Dollar-Anteil. Beim Dollar steht die reine Umrechnung des Dollar-Anteils.\n• Ereignisse haben keine Zahl. Gezeigt werden die betroffenen Positionen. Das Rohstoffembargo trifft Branchen, die Rohstoffe aus wenigen Förderländern brauchen.\n• Bargeld und Derivate sind nicht eingerechnet."
    override fun szName(key: String, schock: String) = when (key) {
        "OEL" -> "Ölpreis $schock"; "ZINS" -> "Zinsen $schock"; "DOLLAR" -> "Dollar $schock"
        "KUPFER" -> "Kupfer $schock"; "GOLD" -> "Gold $schock"; else -> key
    }
    override fun szPunkte(n: Int) = (if (n < 0) "−" else "+") + kotlin.math.abs(n) + (if (kotlin.math.abs(n) == 1) " Punkt" else " Punkte")
    override val szEreignisse = "Ereignisse"
    override val szEigenerWert = "Eigener Wert"
    override fun szEreignis(key: String) = when (key) {
        "TAIWAN" -> "Taiwan-Blockade"; "HORMUS" -> "Sperrung von Hormus"; "EMBARGO" -> "Rohstoffembargo"; else -> key
    }
    override fun szSpanne(a: String, b: String) = "$a bis $b"
    override fun szBelegt(anteil: String) = "$anteil des Depots mit Messwert"
    override val szKeinBeleg = "ohne Messwert"
    override val szNichtBerechenbar = "ohne Zahl"
    override fun szBetrifft(anteil: String) = "betrifft $anteil des Depots"
    override val szKeinTreffer = "keine Position betroffen"
    override val szKeineBelegten = "Keine Position mit belegtem Zusammenhang."
    override val szGroessteBeitraege = "Größte Beiträge"
    override val szBetroffenePositionen = "Betroffene Positionen"
    override fun szAnteil(anteil: String) = "Anteil $anteil"
    override fun szBraucht(rohstoffe: String) = "braucht $rohstoffe"
    override fun szFortschritt(erledigt: Int, gesamt: Int) = "Kurse $erledigt von $gesamt geladen"
    override fun szDerivate(anteil: String) = "Derivate nicht eingerechnet: $anteil des Depots"
    override fun szRichtung(richtung: Int) = if (richtung > 0) "lief bisher mit" else if (richtung < 0) "lief bisher gegen" else ""
    override fun szDollarAnteil(anteil: String) = "In Dollar: $anteil"
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
    override val ptDivNote = "Fortgeschrieben aus den Ausschüttungen der letzten zwölf Monate mit denselben Ex-Tagen und Beträgen je Stück, heutigem Bestand und heutigem Wechselkurs. Thesaurierende ETFs erscheinen nicht. Zahltag ist der angekündigte Termin, sonst drei Tage nach dem Ex-Tag. Beträge nach Steuern, mit dem verbleibenden Freistellungsauftrag dieses Jahres."
    override val ptTop3 = "Die drei größten zusammen"
    override val ptTop10 = "Top 10 zusammen"
    override val ptHhi = "Herfindahl-Index"
    override val ptHhiHint = "0 = gleichmäßig verteilt, 10.000 = alles in einer Position. Faustregel aus der Marktaufsicht: unter 1.500 gestreut, bis 2.500 mäßig konzentriert, darüber stark konzentriert."
    override val ptCountriesBip = "Länder gegen Welt-BIP"
    override val ptCountriesBipNote = "Balken: dein Anteil je Land, ETFs nach Bestandteilen aufgelöst. Strich: Anteil des Landes an der Weltwirtschaftsleistung in laufenden US-Dollar (IWF, laufendes Jahr). Rechts der Abstand in Prozentpunkten. Aktienmärkte sind anders verteilt als die Wirtschaftsleistung."
    override val ptOhneLand = "Ohne Landzuordnung"
    override val ptOhneBip = "Ohne IWF-Wert"
    override val ptVarTitle = "Schwankungsrisiko in einem Jahr"
    override fun ptVar(niveau: String) = "VaR $niveau (1 Jahr)"
    override fun ptEs(niveau: String) = "ES $niveau (1 Jahr)"
    override val ptVarHint = "Value at Risk: In 95 von 100 simulierten Jahren bleibt der Verlust darunter."
    override val ptEsHint = "Expected Shortfall: mittlerer Verlust der schlechtesten 5 von 100 simulierten Jahren."
    override fun ptVarNote(anzahl: Int, schwankung: String?) = "Aus $anzahl simulierten Verläufen. " +
        if (schwankung != null) "Rendite wie oben, Schwankung aus dem Verlauf des Depots ($schwankung pro Jahr)." else "Rendite und Schwankung wie oben."
    override val ptSparAnlage = "Anlage"
    override val ptSparSeit = "Beginn"
    override val ptSparValue = "Wert heute"
    override fun ptSparMonths(n: Int, name: String, seit: String) = "nach $n Monatsraten in $name seit $seit"
    override val ptSparIrr = "Jahresrendite (Zinsfuß)"
    override val ptSparLump = "Als Einmalanlage am Anfang"
    override val ptSparLumpHint = "Der ganze Betrag einmal zum ersten Kurs."
    override fun ptSparLumpSparplan(euro: String) = "Der Sparplan liegt um $euro vorn."
    override fun ptSparLumpEinmal(euro: String) = "Die Einmalanlage liegt um $euro vorn."
    override val ptSparKeineDaten = "Für den gewählten Zeitraum liegen keine Kurse vor."
    override val ptSparNote = "Gekauft am ersten Handelstag des Monats, bewertet mit dem heutigen Kurs. Ausschüttungen sind wieder angelegt. Der Zinsfuß rechnet jede Rate ab ihrem Kauftag."
    override fun afWaage(wert: Int) = "Saldo " + (if (wert >= 0) "+$wert" else "−${-wert}")
    override fun afRsi(wert: String) = "RSI $wert"
    override fun afLinieKurz(tage: Int, abstand: String) = "$tage-Tage-Linie $abstand"
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
    override val ptDiversificationNote = "Regionen wie im Globus: Aktien nach Tätigkeitsland, ETFs nach den Ländern ihres Index. Bei den Branchen zählt ein ETF als Ganzes."
    override val ptModeRebalance = "Umschichten"
    override val ptModeBuyOnly = "Nur zukaufen"
    override val ptNewMoney = "Neues Geld"
    override fun ptCash(v: String) = "Guthaben $v"
    override val ptEqualWeights = "Gleich gewichten"
    override val ptCurrentWeights = "Heutige Gewichte"
    override val katJeWert = "Je Wert"
    override val katJeKategorie = "Je Kategorie"
    override val katOhne = "Ohne Kategorie"
    override val katEine = "Kategorie"
    override val katTitel = "Kategorien"
    override val katBearbeiten = "Kategorien bearbeiten"
    override val katNeu = "Kategorie hinzufügen"
    override val katName = "Name"
    override val katZiel = "Ziel in %"
    override val katSumme = "Summe der Ziele"
    override val katPositionen = "Positionen"
    override val ptSaveTargets = "Ziele speichern"
    override val ptSavedTargets = "Gespeicherte Ziele"
    override val divTitel = "Diversifizieren"
    override val divGleich = "Gleich verteilt"
    override val divRisiko = "Gleicher Risikoanteil"
    override val divLaenderBip = "Länder nach Welt-BIP"
    override val divBranchen = "Branchen ausgleichen"
    override fun divHoechstens(prozent: Int) = "Höchstens $prozent % je Wert"
    override val divLaedt = "Daten werden geladen …"
    override val divKeineDaten = "Dafür liegen nicht genug Daten vor."
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
    override val ptOthers = "Weitere"
    override fun ptFromTo(a: String, b: String) = "$a bis $b"

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
    override val vega = "Vega (je 1 % Vol)"
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
        "FEE_NOT_COVERED" -> "Guthaben reicht nicht für die Gebühr"; "INVALID_LIMIT" -> "Ungültiger Limit-/Stoppreis"; "INVALID_TRAIL" -> "Abstand muss zwischen 0 und 50 % liegen"
        "INVALID_STOP" -> "Stopp-Verkauf nur unter, Stopp-Kauf nur über dem Kurs"
        "MISSING_SECURITY" -> "Wertpapier fehlt"; "INVALID_AMOUNT" -> "Ungültiger Betrag"; "ONLY_AVAILABLE" -> "Nur $extra verfügbar"
        "ORDER_CLOSED" -> "Die Order ist nicht mehr offen"; "INVALID_DATE" -> "Ungültiges Datum"; "EXPIRED" -> "Gültigkeit abgelaufen"; "PRODUCT_GONE" -> "Produkt nicht mehr handelbar"
        "UNCOVERED" -> "Deckung fehlt"; "SHARES_BOUND" -> "Aktien decken einen verkauften Call"; "NO_SHORT_STOCK" -> "Aktien lassen sich nicht leerverkaufen"
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
    override val alertSet = "Preismarke gesetzt"
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
    override fun notifAlert(name: String) = "Preismarke: $name"
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
    override val rbLeer = "Noch kein Rückblick. Der erste kommt am nächsten Handelstag zur gewählten Uhrzeit."
    override val rbJetzt = "Heute jetzt zusammenfassen"
    override val rbJetztLaeuft = "Wird zusammengefasst"
    override val rbJetztFehler = "Gerade keine Kurse erreichbar."
    override fun rbWoche(von: String, bis: String) = "Woche vom $von bis $bis"
    override val rbWocheUeberblick = "Die Woche im Überblick"
    override val rbMarkt = "Markt"
    override val rbBewegungen = "Bewegungen im Depot"
    override val rbNachrichten = "Wichtige News"
    override fun rbWahrscheinlich(titel: String, quelle: String) = "Wahrscheinlich: „$titel“" + if (quelle.isNotBlank()) " ($quelle)" else ""
    override fun rbWahrscheinlichKurz(titel: String) = "wahrscheinlich „$titel“"
    override fun rbMitMarkt(markt: String, aenderung: String) = "Keine eigene Nachricht gefunden, lief mit dem Markt ($markt $aenderung)"
    override val rbOhneGrund = "Keine passende Nachricht gefunden"
    override val rbKeinDepot = "Keine Wertpapiere im Depot"
    override val rbKeineBewegung = "Kein Wert hat sich um mehr als 0,5 % bewegt"
    override val rbKeineNachrichten = "Keine besonders wichtigen Nachrichten"
    override val rbVorlaeufig = "Vorläufig, vor Börsenschluss erstellt. Nach Schluss der Märkte ersetzt ihn der fertige Rückblick."
    override val rbVorlaeufigAlt = "Vorläufig, vor Börsenschluss erstellt."
    override val rbNachtraeglich = "Nachträglich aus Buchungen und Tageskursen erstellt."
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
        "alle" -> "Jede neue Schlagzeile der eingeschalteten Quellen"
        "aus" -> ""
        else -> "Vorgabe: nur die Meldungen, die auf der Nachrichtenseite markiert sind"
    }
    override val awTitle = "Auswertung"
    override val awLoading = "Auswertung wird berechnet …"
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
        "FONDSKOSTEN" -> "$anteil im Schnitt, etwa $anzahl € im Jahr"
        "TER_GESTIEGEN" -> (if (anzahl == 1) "Teurer geworden" else "$anzahl teurer geworden") + ", bis zu $anteil mehr im Jahr"
        "GROESSTE" -> "$anteil des Depots"
        "ANZAHL" -> "$anzahl Einzelaktien, $anteil in ETFs"
        "BRANCHE" -> "$anteil des Depots"
        "WAEHRUNG" -> "$anteil in fremder Währung"
        "HEBEL" -> "$anteil des Depots"
        "KRYPTO" -> "$anteil des Depots"
        "KOSTEN" -> "$anteil des gehandelten Betrags in zwölf Monaten, $anzahl Orders"
        "RESERVE" -> "$anteil des Vermögens"
        "SPARPLAN" -> if (anzahl > 0) "$anzahl aktiv" else "keiner"
        "VERLUSTE" -> "$anzahl über 50 % im Minus"
        else -> anteil
    }
    override val terTeurerTitel = "ETF teurer geworden"
    override val etfTdHinweis = "Die Tracking Difference zeigt, wie weit die Jahresrendite hinter dem Index lag."
    override val etfTdLink = "Tracking Difference bei extraETF"
    override fun terTeurerText(name: String, alt: String, neu: String) = "$name: laufende Kosten von $alt auf $neu im Jahr gestiegen."
    override fun dcExplain(regel: String) = when (regel) {
        "TER_GESTIEGEN" -> "Die App merkt sich die laufenden Kosten jedes ETFs, sobald sie sie abruft.\n\n• Steigen sie, steht das hier für ein Jahr.\n• Fondsgesellschaften dürfen die Kosten mit Ankündigung erhöhen.\n• Ein vergleichbarer ETF mit niedrigeren Kosten kann sich dann lohnen, ein Wechsel kostet aber Gebühren und bei Gewinn Steuern."
        "FONDSKOSTEN" -> "Die laufenden Kosten (TER) zieht der Fonds jedes Jahr vom Vermögen ab.\n\n• Breite Index-ETFs kosten oft 0,1 bis 0,3 %.\n• Über viele Jahre schmälern höhere Kosten die Rendite deutlich."
        "GROESSTE" -> "Fällt ein einzelner Wert stark, trifft es das ganze Depot.\n\n• Als Faustregel gelten höchstens 5 bis 10 % je Einzelwert.\n• ETFs zählen hier nicht, weil sie selbst streuen."
        "ANZAHL" -> "Mit wenigen Einzelwerten hängt viel an jedem davon. Ab etwa 15 bis 20 Werten aus verschiedenen Branchen oder mit einem breiten ETF sinkt dieses Risiko deutlich."
        "BRANCHE" -> "Werte einer Branche fallen oft gemeinsam, etwa bei Zinsänderungen oder neuen Regeln. Mehr als ein Viertel des Depots in einer Branche ist ein Klumpen."
        "WAEHRUNG" -> "Bei Werten in Dollar, Pfund oder Franken kommt zur Kursbewegung die des Wechselkurses dazu. Das gleicht sich langfristig oft aus, kann aber Jahre in eine Richtung laufen.\n\nETFs zählen nach den Währungen der Länder ihres Index, ein Welt-ETF in Euro steckt zum großen Teil in Dollar. Über 95 % gilt das Depot als einseitig."
        "HEBEL" -> "Hebelprodukte vervielfachen Gewinne und Verluste und können ganz verfallen. Viele Ratgeber nennen höchstens 5 bis 10 % des Depots."
        "KRYPTO" -> "Kryptowerte schwanken weit stärker als Aktien. Viele Ratgeber nennen höchstens etwa 5 bis 10 %."
        "KOSTEN" -> "Feste Gebühren wiegen bei kleinen Orders schwer: 1 € auf 50 € sind 2 %. Seltener und in größeren Beträgen zu handeln oder ein Sparplan senkt die Kosten."
        "RESERVE" -> "Etwas Guthaben erlaubt Nachkäufe, ohne zu verkaufen. Liegt dagegen der größte Teil ungenutzt, trägt er nichts zur Rendite bei."
        "SPARPLAN" -> "Ein Sparplan legt regelmäßig an, ohne auf den richtigen Zeitpunkt zu warten, und mittelt den Einstiegskurs."
        "VERLUSTE" -> "Ein Wert, der die Hälfte verloren hat, muss sich verdoppeln, um wieder auf den Einstand zu kommen. Entscheidend ist, ob der Grund für den Kauf noch gilt."
        else -> ""
    }
    override fun dcEinzelwerte(anzahl: Int) = "$anzahl Einzelwerte über 5 %"
    override val dcTeuersten = "Am teuersten"
    override val dcNote = "Faustregeln aus verbreiteten Ratgebern. Keine Anlageberatung."
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
    override val ctcHint = "Es zählt der Tag des Geschäfts, nicht der Tag der Meldung. Beträge sind die Mitte der gemeldeten Spanne."
    override val updNotifTitle = "Neue Fassung von Tryfolio"
    override fun updNotifText(fassung: String) = "Fassung $fassung steht zum Herunterladen bereit."
    override fun updRowTitle(fassung: String) = "Fassung $fassung verfügbar"
    override fun altTitel(monate: Int) = "Diese Fassung ist $monate Monate alt"
    override val altText = "Neuere Fassungen stehen auf der Seite zum Herunterladen."
    override fun altZeile(monate: Int) = "Fassung $monate Monate alt"

    override val guideTitle = "Kurzanleitung"
    override fun ppfZeitraum(key: String) = when (key) { "YTD" -> "Seit 1.1."; "Y1" -> toolYearsKurz(1); "Y3" -> toolYearsKurz(3); "Y5" -> toolYearsKurz(5); else -> "Gesamt" }
    override val ppfTtwror = "Zeitgewichtete Rendite (TTWROR)"
    override fun ppfVonBis(von: String, bis: String) = "$von bis $bis"
    override val ppfTtwrorJahr = "TTWROR pro Jahr"
    override val ppfUnterJahr = "erst ab einem Jahr"
    override val ppfIzf = "Interner Zinsfuß (IZF) pro Jahr"
    override val ppfIzfZeitraum = "Interner Zinsfuß (IZF) im Zeitraum"
    override val ppfIzfHinweis = "berücksichtigt, wann wie viel Geld im Depot war"
    override val ppfAbsolut = "Gewinn in Euro"
    override val ppfMaxRueckgang = "Größter Rückgang"
    override val ppfNurWertpapiere = "Nur Wertpapiere"
    override val ppfNurWertpapiereHinweis = "Kurse ohne Cash, Kosten und Dividenden"
    override val ppfBilanz = "Performance-Berechnung"
    override val ppfLetzterTag = "Letzter Tag"
    override val ppfRisiko = "Risikokennzahlen"
    override val ppfRueckgangDauer = "Längste Zeit unter dem Hoch"
    override val ppfRueckgangDauerHinweis = "bis zum nächsten Höchststand oder heute"
    override fun ppfTage(n: Long) = if (n == 1L) "1 Tag" else "$n Tage"
    override val ppfSemivolatilitaet = "Schwankung nach unten"
    override val ppfSemivolatilitaetHinweis = "Semivolatilität, nur Phasen unter dem Mittel"
    override val ppfHoch = "Höchststand"
    override val rdUnterwasser = "Rückgang vom Höchststand"
    override val rdUnterwasserInfo = "Zeigt, wie weit der Wert unter seinem bisherigen Höchststand lag.\n\n• 0 % heißt neues Hoch.\n• Je tiefer die Fläche, desto größer der Rückgang, je breiter, desto länger dauerte er."
    override val rdLeerUnterwasser = "Ab 20 Handelstagen Verlauf gibt es hier etwas zu sehen."
    override val rdVerteilung = "Renditeverteilung"
    override val rdVerteilungInfo = "Wie oft welche Tagesrendite vorkam, in Klassen.\n\nDie Höhe zeigt den Anteil der Tage, die äußeren Klassen fassen Ausreißer zusammen.\n• VaR 95 % (1 Tag): An 95 von 100 Tagen war der Verlust kleiner.\n• ES 95 % (1 Tag): mittlerer Verlust an den schlechtesten 5 von 100 Tagen.\n\nTagesrenditen, weil sie schon nach 100 Handelstagen eine Verteilung ergeben. Monatswerte bräuchten Jahre."
    override val rdLeerVerteilung = "Ab 100 Handelstagen Verlauf (rund fünf Monate) gibt es hier etwas zu sehen."
    override fun rdTage(n: Int) = if (n == 1) "1 Handelstag" else "$n Handelstage"
    override fun rdKlasse(von: String, bis: String, n: Int, anteil: String) = "$von bis $bis: ${if (n == 1) "1 Tag" else "$n Tage"} ($anteil)"
    override fun rdKlasseBis(bis: String, n: Int, anteil: String) = "Bis $bis: ${if (n == 1) "1 Tag" else "$n Tage"} ($anteil)"
    override fun rdKlasseAb(von: String, n: Int, anteil: String) = "Ab $von: ${if (n == 1) "1 Tag" else "$n Tage"} ($anteil)"
    override val rdRollierend = "Rendite und Schwankung im Verlauf"
    override val rdRollierendInfo = "Rendite über je 12 Monate und Schwankung über je 60 Handelstage, jeweils bis zum Tag des Punkts gerechnet.\n\n• Zeigt, wie stark beide vom Zeitpunkt abhängen.\n• Die Schwankung ist auf ein Jahr hochgerechnet."
    override val rdRoll12 = "Rendite über 12 Monate"
    override val rdRoll60 = "Schwankung über 60 Handelstage"
    override val rdLeerRoll12 = "Die Rendite über 12 Monate gibt es ab einem Jahr Verlauf."
    override val rdLeerRoll60 = "Die Schwankung über 60 Handelstage gibt es ab etwa drei Monaten Verlauf."
    override fun ppfWertAm(datum: String) = "Wert am $datum"
    override val ppfEinlagen = "Einlagen"
    override val ppfEntnahmen = "Entnahmen"
    override val ppfKursgewinne = "Kursgewinne"
    override fun ppfRealisiert(betrag: String) = "davon realisiert $betrag"
    override val ppfDividenden = "Dividenden (vor Steuern)"
    override val ppfZinsen = "Zinsen (vor Steuern)"
    override val ppfMonate = "Rendite je Monat und Jahr"
    override val wbTitel = "Je Wertpapier"
    override val wbInfo = "• TTWROR: zeitgewichtete Rendite, die Kursrendite ohne Einfluss von Ein- und Auszahlungen\n• IZF: interner Zinsfuß, die Rendite auf das eingesetzte Geld"
    override val wbUnrealisiert = "Unrealisiert"
    override val wbOhneWertpapier = "Ohne Wertpapier"
    override val wbIzf = "IZF"
    override val wbTtwror = "TTWROR"
    override val ppfHinweis = "Gerechnet für das ganze Depot mit Wertpapieren und Cash, nach Gebühren und Steuern. Geld von außen sind Ein- und Auszahlungen, Wetten und ohne Geld übernommene Positionen (zum Einstand).\n\n• Zeitgewichtete Rendite (TTWROR): misst die Anlage unabhängig vom Zeitpunkt der Ein- und Auszahlungen, geeignet zum Vergleich mit Fonds und Indizes\n• Interner Zinsfuß (IZF): deine persönliche Rendite unter Berücksichtigung, wann wie viel Geld im Depot war\n• Kursgewinne: Rest aus Endwert, Anfangswert, Einlagen, Entnahmen, Erträgen und Kosten\n\nSchwankung und Schwankung nach unten stehen pro Jahr. Unter einem Jahr gilt der Zinsfuß für den Zeitraum und wird nicht hochgerechnet.\n\nBuchungen für Portfolio Performance exportierst du unter Profil › Erweitert."
    override val guideAllesTitel = "Alles, was die App kann"
    override val guideAllesText = ""
    override fun guideFunktionen(widgets: Boolean, bildschutz: Boolean) = listOfNotNull(
        "Übungsdepot mit echten Kursen" to "Aktien, ETFs, Kryptowerte und Derivate mit virtuellem Geld handeln, mit Limit oder Stopp, Gebühr und Steuer eingerechnet. Was bei geschlossener App fällig wurde, holt der nächste Start mit den Kursen von damals nach.",
        "Sparpläne und Überweisungen" to "Sparpläne kaufen in festen Abständen nach und legen auf Wunsch Dividenden wieder an, zu finden in der Kachel „Orders“. Virtuelles Geld zahlst du per Überweisung ein oder aus.",
        "Wertpapiere finden" to "Suche mit Verlauf, größten Bewegungen, Filter nach Branche, Land und Region sowie eigene Listen für Indizes, Rohstoffe, Währungen und Krypto.",
        "Wertpapierseite" to "Chart, Bewertung mit fairem Wert zum Selbstrechnen, Kennzahlen, Chancen und Risiken, Termine, Nachrichten und Finanzen. Bei Derivaten zeigt ein Balken Long und Short.",
        "Indikatoren" to "Unter dem Chart wählst du Indikatoren wie gleitende Durchschnitte, Bollinger-Bänder, RSI oder MACD, einzeln oder zusammen. Darunter steht, was sie sagen.",
        "Werkzeuge und Optionen je Wert" to "Unter dem Chart klappt „Rückblick“ auf: Einmalanlage und Sparplan gegen Depot, Indizes und andere Werte. Dazu Positionsgröße mit Stopp-Vorschlag, Verkauf mit Steuer, Strategietest und „Wie tief kann es fallen“, bei US-Aktien außerdem Optionen, Leerverkäufe und die Bewegung zu den Zahlen.",
        "Politik und Behörden" to "Bei US-Aktien im Abschnitt „Hintergrund“: Lobbyausgaben, Abgeordnete mit Handel, Wahlkampfspenden, große Halter, Vergütung, Übernahmen und Warnzeichen aus Behördenmeldungen. Je nach Branche kommen klinische Studien und Rückrufe hinzu.",
        "Märkte und Heatmap" to "Die Kachel „Märkte“ hat die Reiter Heatmap, Globus, Trends, Bewegungen, Stimmung, Ausblick und Lage. Die Heatmap zeigt den Aktienmarkt oder deine Werte als Kacheln nach Größe und Tagesveränderung, Bewegungen die größten Kursausschläge sowie Börsengänge, Quartalszahlen und Dividendentermine des nächsten Handelstags.",
        "Trends und Stimmung" to "Branchen und Themen mit Kurs, KGV, Dividendenrendite und Durchbrüchen, dazu die meistbesprochenen Werte auf Reddit. Die Stimmung zeigt Fear & Greed, AAII-Umfrage und Put/Call, der Ausblick Markterwartungen, Polymarket-Wetten und Frühwarnzeichen.",
        "Globus und Länder" to "Der Globus färbt jedes Land nach Gesamtbild, Börse, Konjunktur und weiteren Ebenen. Das Länderblatt zeigt Kennzahlen, Unternehmen und Risiken, „Mein Depot“ die Länder deiner Werte.",
        "Lage und Abhängigkeiten" to "Der Reiter Lage fasst die Welt in sechs Themen zusammen. „Abhängigkeiten und Risiken“ auf der Wertpapierseite zeigt, wovon eine Firma lebt und woran sie hängt, „Auswirkung auf meine Werte“ verbindet ein Ereignis mit deinen Werten.",
        "Durchbrüche" to "Im Reiter Trends je Thema Neu, Erwartet, In Entwicklung und Rückblick.",
        "Analytics und Werkzeuge fürs Depot" to "Portfolio Performance, Depotverlauf, Depot-Check, Streuung, Crash-Test, Vermögensaufbau, Entnahmeplan, Optionen verkaufen, auffällige Optionen, Optionsrechner und weitere Werkzeuge, gruppiert nach Mein Depot, Planen und Aktien finden. Statistiken zu Käufen, Verkäufen und Erträgen stehen unter Cash.",
        "Aktienfinder und Radar" to "Die Kachel „Aktienfinder“ hat die Reiter Liste, Radar, Branchen und Vorschlagsdepot. Die Liste ordnet Aktien nach Bewertung, Qualität, Analysten, Kurstrend und weiteren Kriterien ein, das Radar sammelt frühe Hinweise wie steigende Erwähnungen und Insiderkäufe.",
        "Copytrading" to "Was US-Abgeordnete, Senatoren, der Präsident und große Investoren kaufen und verkaufen, mit Depotverlauf, Positionen und Strategien zum Nachbilden. Eine Glocke meldet Neues.",
        "Rangliste und Kongress kauft" to "Welche Abgeordneten und Senatoren mit ihren Käufen den Markt geschlagen haben, dazu ihr Vermögen und eine Strategie aus allen gemeldeten Käufen. Beides steht im Copytrading unter Politik.",
        "Prognosemärkte" to "Auf Ereignisse bei Polymarket und Kalshi mit Übungsgeld wetten und verfolgen, was die Wetten gebracht haben.",
        "News und Glocken" to "Nachrichten nach Anbietern, Google News, Beiträge von Trump und Aktionärsbriefe. Die Glocke auf der Wertpapierseite schaltet Mitteilungen je Wert ein, etwa Preismarken, Nachrichten und Insider.",
        "Finanzwissen" to "Ein Nachschlagewerk zu Börse, Steuern und Strategien mit Lektionen, Prüffragen und Rechnern, als Kachel auf der Startseite.",
        "Tages- und Wochenrückblick" to "Jeden Handelstag zur gewählten Uhrzeit ein Fazit: Depot im Vergleich zum MSCI World, Markt, Branchen und wichtige Nachrichten. Freitags die ganze Woche.",
        "Zugänge beantragen" to "Mit kostenlosen eigenen Schlüsseln wird die App vollständiger, etwa für Reddit, Wahlkampfspenden, Patente, Strompreise und weitere US-Kurse. Eintragen im Profil unter „Zugänge“, dort steht auch, wo es sie gibt.",
        "Erklär-Assistent" to "Mit eigenem Schlüssel aus Profil › Zugänge erscheint „Erklären“ bei Kennzahlen, Finanzen und Quartalszahlen. Die Zahlen der Seite gehen an deinen Anbieter, ohne Depot und ohne Empfehlung.",
        "Sicherheit" to "App-Sperre mit PIN oder Passwort, Daten mit AES-256 verschlüsselt, Zugangsschlüssel verschlüsselt im Tresor des Geräts" + (if (bildschutz) ", Screenshots erlauben oder sperren." else "."),
        if (widgets) "Widgets" to "Depot, Watchlist, Einzelwert, Top & Flop, Dividenden, Märkte, Heatmap, Stimmung, News und frei belegbare Schnellzugriffe auf dem Startbildschirm. Langes Drücken auf eine Überschrift in der App legt das passende Widget an." else null,
    )
    override val guideMeldungenTitel = "Geschäfte der Abgeordneten vorab laden"
    override val guideMeldungenText = "Copytrading, Rangliste und die Seiten der US-Aktien zeigen, was Abgeordnete kaufen und verkaufen. Die Meldungen seit 2024 sind in der App enthalten, neuere lädt die App im Hintergrund, auch wenn du die Anleitung schließt."
    override val guideMeldungenLaden = "Alle Meldungen jetzt laden"
    override fun guideMeldungenLaedt(gelesen: Int, gesamt: Int) = if (gesamt == 0) "Verzeichnis wird geladen …" else "$gelesen von $gesamt Meldungen gelesen"
    override fun guideMeldungenFertig(gesamt: Int) = if (gesamt == 0) "Alle Meldungen sind schon auf dem Gerät." else "Fertig: $gesamt neue Meldungen gelesen."
    override val guideSkip = "Überspringen"
    override val guidePages = listOf(
        "Depot und Cash" to "Die Startseite zeigt den Wert deiner Wertpapiere und seinen Verlauf. Wisch nach links für Cash mit Guthaben, Statistik und Aktivität, noch einmal für dein Profil. Unter den Positionen liegen die Kacheln, etwa Orders, Märkte und Analytics.",
        "Wertpapiere finden" to "Über „Handeln“ unten auf der Startseite findest du Aktien, ETFs, Derivate und Kryptowerte. Die Auswahl öffnet die Wertpapierseite, ein Wisch nach unten schließt sie.",
        "Kaufen und Sparplan" to "„Kaufen“ legt eine Order an, als Betrag oder Stückzahl, sofort oder mit Limit oder Stopp. Ein Sparplan kauft in festen Abständen nach. Beides liegt in der Kachel „Orders“, die Reiter oben wechseln zwischen Orders und Sparplänen. War die App geschlossen, holt sie beides beim nächsten Start nach, mit Kurs und Zeitpunkt von damals.",
        "Profil" to "Hinter deiner Initiale oben rechts liegen Dokumente, Steuern, Mitteilungen, Zugänge und alle Einstellungen. Die Kurzanleitung findest du dort jederzeit wieder.",
    )

    override val guidePagesDesktop get() = guidePages.toMutableList().apply {
        this[0] = "Depot und Cash" to "Die Startseite zeigt den Wert deiner Wertpapiere und seinen Verlauf. Klick oben auf „Cash“ oder zieh die Seite mit gedrückter Maustaste nach links für Guthaben, Statistik und Aktivität, noch einmal für dein Profil. Unter den Positionen liegen die Kacheln, etwa Orders, Märkte und Analytics."
        this[1] = "Wertpapiere finden" to "Über „Handeln“ unten auf der Startseite findest du Aktien, ETFs, Derivate und Kryptowerte. Die Auswahl öffnet die Wertpapierseite, Escape oder der Pfeil oben links schließt sie."
    }

    // Entdecken
    override val bestPerformers = "Gewinner"
    override val worstPerformers = "Verlierer"
    override val ipos = "Börsengänge"
    override val upcoming = "Bevorstehend"
    override val completed = "Abgeschlossen"
    override fun ipoCardTitle(name: String) = "Börsengang $name"
    override fun ipoListed(date: String) = "Erstnotiz $date"
    override fun ipoSinceIssue(pct: String) = "seit Ausgabe $pct"
    override fun ipoAsOf(date: String) = "Stand der Angaben: $date"
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
        "SEKTORUEBERGREIFEND" -> "Über verschiedene Sektoren"; "LARGE_CAPS" -> "Large Caps"; "MID_CAPS" -> "Mid Caps"
        "SMALL_CAPS" -> "Small Caps"; "UNTERNEHMENSANLEIHEN" -> "Unternehmensanleihen"; "STAATSANLEIHEN" -> "Staatsanleihen"
        "GELDMARKT" -> "Geldmarkt"
        else -> key
    }
    override fun industryShort(key: String) = when (key) {
        "ALKOHOL_TABAK" -> "Genussmittel"; "BANKEN" -> "Banken"; "BAU" -> "Bau"; "BERGBAU" -> "Bergbau"; "HOLDINGS" -> "Holdings"
        "BIOTECH" -> "Biotech"; "CHEMIE" -> "Chemie"; "COMPUTER" -> "Computer"; "DIENSTLEISTUNGEN" -> "Dienste"
        "ELEKTRO" -> "Elektro"; "HALBLEITER" -> "Halbleiter"; "VERSORGER" -> "Versorger"; "FAHRZEUGE" -> "Fahrzeuge"
        "LUFTFAHRT" -> "Luftfahrt"; "FORST" -> "Forst"; "GASTGEWERBE" -> "Tourismus"; "GESUNDHEIT" -> "Gesundheit"
        "HANDEL" -> "Handel"; "IMMOBILIEN" -> "Immobilien"; "SOFTWARE" -> "Software"; "EINZELHANDEL" -> "Einzelhandel"
        "KONSUM" -> "Konsum"; "MASCHINEN" -> "Maschinen"; "NAHRUNG" -> "Nahrung"; "PHARMA" -> "Pharma"; "MEDIEN" -> "Medien"
        "ROHSTOFFE" -> "Rohstoffe"; "TELEKOM" -> "Telekom"; "TEXTIL" -> "Textil"; "UMWELT" -> "Umwelt"; "VERKEHR" -> "Verkehr"
        "VERSICHERUNG" -> "Versicherung"; "OEL_GAS" -> "Öl & Gas"; "SEKTORUEBERGREIFEND" -> "Gemischt"
        "LARGE_CAPS" -> "Large Caps"; "MID_CAPS" -> "Mid Caps"; "SMALL_CAPS" -> "Small Caps"
        "UNTERNEHMENSANLEIHEN" -> "Firmenanleihen"; "STAATSANLEIHEN" -> "Staatsanleihen"; "GELDMARKT" -> "Geldmarkt"
        else -> industryName(key)
    }
    override fun regionShort(key: String) = when (key) {
        "ASIEN_PAZIFIK" -> "Asien"; "NORDAMERIKA" -> "Amerika"; "SCHWELLENLAENDER" -> "Emerging"
        else -> regionName(key)
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
        de.troetenrepublik.app.market.Rss.Anbieter.DEPOT -> "Depot"
        de.troetenrepublik.app.market.Rss.Anbieter.TRUMP -> "Trump"
        de.troetenrepublik.app.market.Rss.Anbieter.BRIEFE -> "Briefe und Blogs"
        de.troetenrepublik.app.market.Rss.Anbieter.TAGESSCHAU -> "tagesschau"
        de.troetenrepublik.app.market.Rss.Anbieter.YAHOO -> "Yahoo Finance"
        de.troetenrepublik.app.market.Rss.Anbieter.GOOGLE -> "Google News"
        de.troetenrepublik.app.market.Rss.Anbieter.PRESSE -> "Presse"
        de.troetenrepublik.app.market.Rss.Anbieter.CNBC -> "CNBC"
        de.troetenrepublik.app.market.Rss.Anbieter.REUTERS -> "Reuters"
        de.troetenrepublik.app.market.Rss.Anbieter.MARKETWATCH -> "MarketWatch"
        de.troetenrepublik.app.market.Rss.Anbieter.GUARDIAN -> "The Guardian"
        de.troetenrepublik.app.market.Rss.Anbieter.FINNHUB -> "Finnhub"
        de.troetenrepublik.app.market.Rss.Anbieter.ALPHAVANTAGE -> "Alpha Vantage"
        de.troetenrepublik.app.market.Rss.Anbieter.NOTENBANKEN -> "Notenbanken"
        de.troetenrepublik.app.market.Rss.Anbieter.BEHOERDEN -> "Behörden"
        de.troetenrepublik.app.market.Rss.Anbieter.EIGENE -> "Eigene Feeds"
        de.troetenrepublik.app.market.Rss.Anbieter.GEMERKT -> "Gemerkt"
    }
    override val newsLoading = "Schlagzeilen werden geladen …"
    override val newsEmpty = "Hier gibt es gerade keine Schlagzeilen."
    override val newsDepotLeer = "Keine Werte im Depot"
    override val newsDepotStufen = "Neue Stufen, 7 Tage"
    override val newsEmptySearch = "Keine Schlagzeile passt zur Suche."
    override val newsYesterday = "Gestern"
    override val newsSearchHint = "In den Schlagzeilen suchen"
    override val newsSearch = "Suchen"
    override val newsSources = "Quellen und Mitteilungen"
    override val newsSourcesHint = "Fest eingebaut sind Quellen, deren Bedingungen das Lesen in dieser App mit Quellenangabe erlauben.\n\n• Eigene Feeds lädt die App nur auf diesem Gerät.\n• Was eine Seite mit ihrem Feed erlaubt, steht in ihren Nutzungsbedingungen."
    override val newsOwnFeeds = "Eigene Feeds"
    override val newsFeedUrl = "Adresse des Feeds (RSS oder Atom)"
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
    override val copySenateNote = "Beträge sind Spannen in US-Dollar. Papiermeldungen sind eingescannt und fehlen in den Auswertungen."
    override val copySenatorOf = "US-Senat"
    override val copyGovIntro = "Geschäfte des US-Präsidenten aus seinen Meldungen an das Office of Government Ethics (Formular 278-T), der Betrag als Spanne. Zeilen ohne sicheres Datum, Art oder Betrag fehlen."
    override fun copyGovNote(stand: String) = (if (stand.isBlank()) "" else "Stand $stand. ") + "Beträge sind Spannen in US-Dollar."
    override val copyPresident = "Präsident der USA"
    override val copyVicePresident = "Vizepräsident der USA"
    override fun copyAmtBis(amt: String, bis: String) = "$amt bis $bis"
    override fun copyVanceNote(stichtag: String) = "Bestand laut Jahresbericht (OGE Form 278e) zum $stichtag, mit Werten als Spanne. Einzelgeschäfte meldet Vance kaum, weil Fonds und ETFs von der Meldepflicht für Geschäfte ausgenommen sind. Die Anteile richten sich nach der Mitte der Spannen."
    override val copyFilingsAll = "Meldungen"
    override val copyTypeMuniBond = "Kommunalanleihe"
    override fun copyTradesCount(n: Int) = if (n == 1) "1 Geschäft" else "${"%,d".format(java.util.Locale.GERMANY, n)} Geschäfte"
    override fun copyHoldingsAsOf(date: String) = "Bestand vom $date"
    override val copyWhoTitle = "Wer handelt den Wert"
    override val copyWhoNone = "Keine Geschäfte bekannter Personen in den letzten zwölf Monaten"
    override val copyTendBuy = "Eher Käufe"
    override val copyTendSell = "Eher Verkäufe"
    override val copyTendMixed = "Ausgeglichen"
    override fun copyWhoHouseOpen(offen: Int, gesamt: Int) = "$offen von $gesamt Meldungen des Repräsentantenhauses aus den letzten zwölf Monaten hat die App noch nicht gelesen. Ihre Geschäfte fehlen hier noch."
    override val copyWhoHouseLoad = "Alle Meldungen lesen (einmalig, danach auf dem Gerät)"
    override fun copyWhoHouseReading(fertig: Int, gesamt: Int) = "Meldungen werden gelesen: $fertig von $gesamt"
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
    override val copyBellsAdd = "Personen in Copytrading suchen"
    override val copyBellHouse = "Repräsentantenhaus · prüft das Verzeichnis des Clerk"
    override val copyBellSenate = "Senat · mit jeder neuen Fassung der App"
    override val copyBellGovernment = "Regierung · prüft whitehouse.gov"
    override val copyBellInvestor = "Investor · prüft die SEC auf neue 13F-Meldungen"
    override val imTitel = "Registerdaten importieren"
    override val imUntertitel = "Insider- und Leerverkaufslisten aus Dateien"
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
    override val lwTitel = "Lobbyarbeit Europa"
    override val lwHinweis = "So viel gibt die Firma laut den Lobbyregistern im Jahr aus, um auf Gesetze und Behörden Einfluss zu nehmen. Die Register nennen Spannen."
    override fun lwRegister(l: String) = when (l) { "EU" -> "EU-Transparenzregister"; "DE" -> "Lobbyregister Bundestag"; "FR" -> "Lobbyregister Frankreich"; else -> l }
    override fun lwSpanne(von: String, bis: String) = "$von bis $bis"
    override fun lwAb(von: String) = "ab $von"
    override fun lwJahr(von: String, bis: String) = "Geschäftsjahr $von bis $bis"
    override fun lwFte(n: String) = "$n Vollzeitstellen für Lobbyarbeit"
    override fun lwTaetigkeiten(n: Int) = if (n == 1) "1 gemeldete Tätigkeit" else "$n gemeldete Tätigkeiten"
    override fun lwThemen(t: String) = "Themen: $t"
    override val lwEintrag = "Eintrag im Register öffnen"
    override fun copyNotifOge(n: Int) = (if (n == 1) "Eine neue Meldung" else "$n neue Meldungen") + " bei der US-Ethikbehörde OGE. Die Geschäfte kommen ausgelesen mit der nächsten Fassung der App."
    override val importPortfolioJson = "Depot importieren (Sicherung oder Broker-Export)"
    override val exportPortfolioJson = "Depot exportieren (JSON)"
    override val importChoice = "Zusammenführen nimmt Guthaben, Positionen, Buchungen, Sparpläne und Watchlist der Datei zum aktuellen Depot dazu. Ersetzen tauscht das aktuelle Depot gegen die Datei."
    override val importMerge = "Zusammenführen"
    override val importReplace = "Ersetzen"
    override val depotMerged = "Depots zusammengeführt"
    override fun copyPhotoCredit(autor: String, lizenz: String) = "Foto: $autor, $lizenz, über Wikimedia Commons"
    override val copyPhotosCommons = "Urheber und Lizenz der Fotos stehen auf der Seite der Person. Ohne freies Foto stehen die Initialen."
    override val copyInvestors = "Investoren"
    override val copyStrategies = "Strategien"
    override val copyCongressIntro = "Geschäfte von Abgeordneten des US-Repräsentantenhauses nach dem STOCK Act, gemeldet bis zu 45 Tage danach, der Betrag als Spanne."
    override val copySearchMember = "Name oder Bundesstaat (etwa CA)"
    override val copyLoading = "Meldungen werden geladen …"
    override val copyUnavailable = "Die Daten sind gerade nicht erreichbar. Später noch einmal versuchen."
    override val copyLatestTrades = "Neueste Geschäfte"
    override val copyMembers = "Abgeordnete"
    override fun copyFilings(n: Int) = if (n == 1) "1 Meldung" else "$n Meldungen"
    override fun copyLast(date: String) = "zuletzt $date"
    override val copyCongressNote = "Die Beträge sind Schätzwerte aus den Spannen."
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
    override fun copyInvestorLine(date: String, n: String, value: String) = "Stand $date · $n Positionen · $value"
    override val copyInvestorsNote = "Werte in US-Dollar zum Stichtag. Optionen stehen mit dem Wert der Aktien dahinter. Barmittel, Leerverkäufe und Aktien außerhalb der USA fehlen in diesen Meldungen."
    override val copy13fSuche = "Anderen Fonds suchen"
    override val copy13fEigene = "Gesucht"
    override val copy13fNichts = "Keine Treffer"
    override val copy13fFehler = "SEC antwortet nicht"
    override val copy13fOhneKontakt = "SEC-Kontakt fehlt unter Zugänge"
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
    override val copyStrategiesNote = "Vergangene Wertentwicklung ohne Kosten und Steuern, bei jährlicher Umschichtung auf die Anteile. Ausschüttungen ausschüttender ETFs sind nicht eingerechnet."
    override val copyAllocation = "Aufteilung"
    override val copyHistory = "Entwicklung"
    override val copyNoHistory = "Für diese ETFs liegen gerade keine Kurse vor."
    override fun copyRange(from: String, to: String) = "$from bis $to"
    override val copyMaxDrawdown = "Größter Rückgang"
    override val copyYears = "Je Kalenderjahr"
    override val copyAsPlans = "Als Sparpläne anlegen"
    override fun copyReplicateTitle(name: String) = "$name nachbilden"
    override val copyReplicateHint = "Der Betrag wird nach den Anteilen auf die Werte verteilt. Außerhalb der Handelszeit legt die App Limit-Orders 2 % über dem letzten Kurs an, die zur nächsten Eröffnung ausgeführt werden."
    override fun copyCash(v: String) = "Verfügbar: $v"
    override val copyCount = "Werte"
    override val copyNothingToCopy = "Keine Aktien zum Nachbilden."
    override fun copyFees(n: Int, v: String) = (if (n == 1) "1 Kauf" else "$n Käufe") + ", Gebühren $v"
    override val copyWorking = "Wird ausgeführt"
    override val copyReplicateNow = "Jetzt nachbilden"
    override fun copyPlansTitle(name: String) = "Sparpläne für $name"
    override val copyPlansHint = "Ein Sparplan je ETF, der Monatsbetrag nach den Anteilen aufgeteilt. Sparpläne kosten keine Gebühr."
    override fun copyPlansMin(betrag: String) = "Mindestens $betrag im Monat, damit jeder Sparplan mindestens 1 € bekommt."
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
    override val regLandTitel = "Dein Land"
    override val regLandText = "Bestimmt Währung und Handelszeiten, im Profil änderbar."
    override val regVorschlag = "Vorschlag nach der Zeitzone deines Geräts"
    override val regAndere = "Weitere Länder"
    override val regWaehrungTitel = "Deine Währung"
    override val regWaehrungText = "Darin zeigt die App Depot, Kurse und Guthaben. Orders, Steuern und Belege bleiben in Euro."
    override fun waehrungName(code: String) = when (code) {
        "EUR" -> "Euro"; "USD" -> "US-Dollar"; "CHF" -> "Schweizer Franken"; "GBP" -> "Britisches Pfund"; "SEK" -> "Schwedische Krone"
        "DKK" -> "Dänische Krone"; "NOK" -> "Norwegische Krone"; "PLN" -> "Polnischer Złoty"; "CZK" -> "Tschechische Krone"; "HUF" -> "Ungarischer Forint"
        "CAD" -> "Kanadischer Dollar"; "MXN" -> "Mexikanischer Peso"; "BRL" -> "Brasilianischer Real"; "JPY" -> "Japanischer Yen"; "CNY" -> "Chinesischer Yuan"
        "HKD" -> "Hongkong-Dollar"; "TWD" -> "Taiwan-Dollar"; "KRW" -> "Südkoreanischer Won"; "INR" -> "Indische Rupie"; "SGD" -> "Singapur-Dollar"
        "ILS" -> "Israelischer Schekel"; "ZAR" -> "Südafrikanischer Rand"; "AUD" -> "Australischer Dollar"; "NZD" -> "Neuseeland-Dollar"
        else -> code
    }
    override val regZeitenTitel = "Handelszeiten"
    override val regZeitenText = "Wann Orders ausgeführt werden, in deiner Ortszeit. Krypto handelt immer rund um die Uhr. Feiertage gelten für LS Exchange und die US-Börsen, verkürzte Handelstage für die US-Börsen."
    override fun regZeitenLand(land: String) = "Wie an der Börse in $land"
    override fun regZeitenLandHint(boerse: String, zeiten: String, deutschland: Boolean) =
        if (deutschland) "$boerse: Aktien und ETFs 7:30 bis 23:00, Derivate 8:00 bis 22:00" else "$boerse, werktags $zeiten Ortszeit"
    override val regZeitenEigene = "Eigene Zeiten"
    override val regZeitenImmer = "Rund um die Uhr"
    override fun regVonBis(von: String, bis: String) = "Werktags $von bis $bis Uhr"
    override val regionTitel = "Land, Währung und Handelszeiten"
    override val regAnzeige = "Gerade angezeigt"
    override val regAnzeigeHint = "Gebucht wird in Euro."
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
    override val rrLaedt = "Kurse der letzten zehn Jahre werden geladen …"
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
            (if (index != null && beta != null) " Beta $beta heißt: Bewegte sich der $index um 1 %, bewegte sich der Wert im Mittel um $beta %. Die Korrelation reicht von 1 (laufen gleich) bis −1 (laufen gegeneinander). Beide aus Wochenkursen der letzten fünf Jahre." else "") +
            ""
    override val trendsTitel = "Trends"
    override val trBranchenHinweis = "Gemessen an US-Branchen-ETFs, Kurse ohne Dividenden. Sie stehen für die Branche weltweit nur ungefähr."
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
    override val sbAaii = "AAII Privatanleger (USA)"
    override fun sbAaiiMehr(bullen: String, neutral: String, baeren: String, datum: String) =
        "Umfrage vom $datum: $bullen % erwarten steigende Kurse in den nächsten sechs Monaten, $neutral % keine große Änderung, $baeren % fallende Kurse. Das Barometer zeigt den Anteil der Bullen unter Bullen und Bären. Langjähriger Schnitt etwa 37 % Bullen und 31 % Bären."
    override val sbPutCall = "Put/Call-Verhältnis"
    override fun sbPutCallMehr(wert: String) = "Auf jede gehandelte Kaufoption (Call) auf US-Aktien kamen am letzten Handelstag $wert Verkaufsoptionen (Puts). Viele Puts zeigen Angst, wenige Gier. Üblich sind 0,5 bis 0,8, das entspricht den Werten 75 bis 25. Darunter gilt als sorglos, darüber als vorsichtig."
    override fun wqPcZeile(gesamt: String?, index: String?, tag: String) =
        listOfNotNull(gesamt?.let { "Alle Optionen $it" }, index?.let { "Indizes $it" }, "Handelstag $tag").joinToString(" · ")
    override val sbKrypto = "Fear & Greed Krypto"
    override val sbWsb = "Reddit r/wallstreetbets"
    override fun sbRedditWert(prozent: Int) = "$prozent % der Kommentare mit klarem Ton sind positiv. Der Wert sagt keinen Kurs voraus. Die Kursänderung über 5 Handelstage steht neben jedem Wert."
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
        "• Schwankung: wie ruhig der VIX im Vergleich zum letzten Jahr ist, von Yahoo Finance"
    override fun sbKachel(schluessel: String) = when (schluessel) {
        "cnn" -> "Aktien USA"; "aktien" -> "Aktien USA"; "gesamt" -> sgKachel; "krypto" -> "Krypto"; "aaii" -> "Privatanleger USA"; "wsb" -> "r/wallstreetbets"; "sicher" -> "Sichere Häfen"; "sorgen" -> "Sorgen"; "vix" -> "Schwankung"; "putcall" -> "Put/Call"; else -> ""
    }
    override fun sbAnbieter(schluessel: String) = when (schluessel) {
        "cnn" -> "CNN Fear & Greed"; "gesamt" -> sgAnbieter; "aktien" -> "Eigener Index"; "krypto" -> "alternative.me"; "aaii" -> "AAII-Umfrage"; "wsb" -> "Tradestie"; "sicher" -> "S&P 500 gegen Anleihen"; "sorgen" -> "Wikipedia"; "vix" -> "VIX via Yahoo"; "putcall" -> "Optionen bei Cboe"; else -> ""
    }
    override fun sbSteigend(prozent: Int) = "$prozent % positiv"
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
        "vix_struktur" -> "VIX gegen VIX3M"
        "trend_sp500" -> "Trend S&P 500"
        "trend_dax" -> "Trend DAX"
        "stress" -> "Finanzstress"
        "hochzins" -> "Hochzins-Spread USA"
        "vix_abstand" -> MarktzeichenTexte.text(true, "fw.name", emptyList())
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
        "vix_abstand" -> MarktzeichenTexte.text(true, "fw.lage.$ampel", emptyList())
        else -> ""
    }
    override fun fwErklaerung(schluessel: String) = when (schluessel) {
        "zinskurve" -> "Rendite zehnjähriger US-Staatsanleihen minus Rendite dreimonatiger.\n\n• Vor jeder US-Rezession seit 1968 lag sie eine Zeit lang unter null, mit einem Vorlauf von etwa 6 bis 24 Monaten.\n• Die Rezession beginnt oft erst, wenn die Kurve wieder steigt.\n• Schwäche: 2022 bis 2024 war sie lange invers, ohne dass bisher eine Rezession folgte."
        "sahm" -> "Dreimonatsschnitt der US-Arbeitslosenquote minus sein Tief der vorangegangenen zwölf Monate.\n\n• Ab 0,5 Prozentpunkten hat die Regel seit 1970 jede US-Rezession früh angezeigt.\n• Schwäche: 2024 wurde die Schwelle kurz erreicht, weil vor allem mehr Menschen Arbeit suchten."
        "vix" -> "Erwartete Schwankung des S&P 500 für die nächsten 30 Tage, abgeleitet aus Optionspreisen.\n\n• Werte über 30 gibt es fast nur in Abschwüngen.\n• Der VIX warnt eher während eines Einbruchs als davor.\n• Sehr niedrige Werte zeigen Sorglosigkeit."
        "vix_struktur" -> "VIX geteilt durch den dreimonatigen VIX3M.\n\n• Normalerweise ist die Unsicherheit auf drei Monate größer als für 30 Tage, der Wert liegt unter 1.\n• Steigt er über 1, erwarten Anleger akut mehr Schwankung als später. Das kam bei fast allen großen Einbrüchen vor."
        "stress" -> "Wochenindex der St. Louis Fed aus 18 Marktreihen, darunter Zinsen und sechs Renditeabstände zwischen riskanten und sicheren Anleihen.\n\n• Null ist der Durchschnitt seit 1993, eins eine Standardabweichung darüber.\n• Steigende Abstände zeigen, dass Geldgeber mehr für Risiko verlangen. Das ging Kurseinbrüchen oft voraus oder begleitete sie.\n• Schwäche: Der Index steigt mit dem Stress und warnt selten lange vorher."
        "hochzins" -> "Zinsaufschlag, den US-Unternehmen mit schwacher Bonität (unter BBB) gegenüber Staatsanleihen zahlen.\n\n• Steigt er schnell, ziehen sich Geldgeber aus riskanten Krediten zurück. Das ging den Abschwüngen 2000, 2007 und 2020 voraus oder begleitete sie.\n• Ein sehr niedriger Wert zeigt Sorglosigkeit.\n• Rot ab 6 Punkten oder 2 Punkte über dem Tief der letzten zwölf Monate, Gelb ab 4,5 oder 1 Punkt darüber.\n• FRED zeigt nur die letzten drei Jahre."
        "trend_sp500", "trend_dax" -> "Abstand des Index zu seinem Durchschnitt der letzten 200 Handelstage.\n\n• Große Bärenmärkte begannen fast alle mit einem Fall unter diese Linie.\n• Schwäche: In Seitwärtsphasen gibt es viele Fehlsignale."
        "vix_abstand" -> MarktzeichenTexte.text(true, "fw.erklaerung", emptyList())
        else -> ""
    }
    override val b13107Titel = "Offenlegungen aus den USA"
    override val b13107Text = "Nach 5 U.S.C. § 13107 dürfen Finanzberichte von US-Abgeordneten, Senatoren und Regierungsmitgliedern nicht für kommerzielle Zwecke, zur Bewertung der Kreditwürdigkeit einer Person oder zum Einwerben von Geld genutzt werden. Die App lädt sie vom Repräsentantenhaus, vom Senat, vom Office of Government Ethics und vom Weißen Haus.\n\nMit „Bestätigen“ erklärst du, dass du sie nur privat nutzt. Die App fragt danach nicht mehr."
    override val b13107Ok = "Bestätigen"
    override val b13107Zurueck = "Zurück"
    override fun blsHinweis(datum: String) = "Daten des BLS abgerufen am $datum. BLS.gov cannot vouch for the data or analyses derived from these data after the data have been retrieved from BLS.gov."
    override val fwPunkte = "Pkt."
    override val fwHinweis = "Keine dieser Messgrößen sagt einen Crash zuverlässig voraus. Ausgewählt sind die, die sich über Jahrzehnte am besten bewährt haben.\n\n" +
        "• Zinskurve und Sahm-Regel warnen vor Rezessionen, die oft mit fallenden Kursen einhergehen\n" +
        "• VIX, VIX gegen VIX3M und 200-Tage-Linie zeigen, ob der Markt schon unter Stress steht\n\n" +
        "Mehrere Signale gleichzeitig auf Rot sind ernster zu nehmen als eines allein."
    override val wrTitel = "Warnzeichen im Rückblick"
    override val wrUntertitel = "Bilanz je Zeichen vor früheren Einbrüchen"
    override val wrInfo = "Die App rechnet jedes Warnzeichen für jeden Handelstag der Vergangenheit mit denselben Schwellen wie heute, und zwar nur mit Werten, die an dem Tag schon veröffentlicht waren.\n\nEin Einbruch ist ein Rückgang um mindestens 20 % vom Hoch, ein Rückgang beginnt bei 10 %.\n\n" +
        "• Treffer: Das Zeichen stand in den 12 Monaten bis zum Hoch auf Warnung\n" +
        "• Warnung ohne Einbruch: Warnphase ohne Einbruch in den 12 Monaten danach. Pausen unter 6 Monaten zählen zur selben Phase\n" +
        "• Warnung befolgen: bei Warnung Geldmarkt zum 3-Monats-Zins, sonst Index, ein Tag Verzug, 0,1 % je Wechsel\n" +
        "• Zusammenschau: wie viele der Zeichen gleichzeitig warnen\n\n" +
        "Zinskurve, Arbeitsmarkt, Erstanträge, VIX, Stress, Kreditaufschlag, Margin-Kredite, Bewertung und Öl sind US-Reihen und gelten für alle drei Indizes. Trend und Frühindikator gehören zum jeweiligen Index.\n\nZeichen mit kürzerer Reihe zählen nur, wo sie Daten haben.\n\n" +
        "Arbeitsmarkt, Erstanträge, Frühindikator, Stresswert, Kreditaufschlag, Margin-Kredite, Börsenwert und gewinnabhängige Zeichen wurden später überarbeitet. Mit den heutigen Werten sehen sie besser aus, als sie damals waren.\n\nKursindex ohne Dividenden außer beim S&P 500 (mit Dividenden nach Shiller) und beim DAX (Performance-Index)."
    override val wrFest = "Vergangenes. Kein Zeichen sagt einen Einbruch zuverlässig voraus, und wenige Ereignisse beweisen wenig."
    override fun wrIndex(id: String) = when (id) { "sp500" -> "S&P 500"; "dax" -> "DAX"; "msciworld" -> "MSCI World"; else -> id }
    override fun wrZeichen(id: String) = when (id) {
        "zinskurve" -> "Zinskurve 10 J. minus 3 Mon."
        "zinskurve_2j" -> "Zinskurve 10 J. minus 2 J."
        "sahm" -> "Sahm-Regel"
        "vix" -> "Schwankungsindex VIX"
        "vix_struktur" -> "VIX gegen VIX3M"
        "trend" -> "Trend, 200-Tage-Linie"
        "stress" -> "Finanzstress"
        "cape" -> "Shiller-KGV (CAPE)"
        "kgv" -> "KGV des S&P 500"
        "praemie" -> "Renditeabstand zu Anleihen"
        "hitze" -> "Überdehnung über der 200-Tage-Linie"
        "cli" -> "OECD-Frühindikator"
        "oel" -> "Ölpreisschock"
        "stimmung_vix" -> "Stimmung, VIX-Rang"
        "stimmung_sicher" -> "Stimmung, sichere Häfen"
        "zusammen_2" -> "Zusammenschau ab 2"
        "zusammen_3" -> "Zusammenschau ab 3"
        "zusammen_4" -> "Zusammenschau ab 4"
        "hochzins" -> "Hochzins-Spread"
        "stlfsi4" -> "Finanzstress STLFSI4"
        "margin" -> "Margin-Kredite"
        "buffett" -> "Buffett-Indikator"
        "erstantraege" -> "Erstanträge"
        "breite" -> "Marktbreite"
        "ipo" -> "Börsengang-Fieber"
        "konzentration" -> "Konzentration"
        "erwaehnung" -> "Erwähnungen"
        "gewinn" -> "Gewinnwachstum"
        "aaii" -> "AAII-Umfrage"
        "cnn" -> "CNN Fear & Greed"
        "putcall" -> "Put/Call"
        "wetten" -> "Wetten"
        else -> id
    }
    override val wrZeitleiste = "Einbrüche ab 20 %"
    override val wrRueckgaenge = "Rückgänge von 10 bis 20 %"
    override val wrLegende = "Gefüllt: Warnung. Rand: keine Warnung. Punkt: keine Daten."
    override fun wrEreignis(hoch: String, tief: String, tiefe: String, monate: Int) = "Hoch $hoch, Tief $tief, $tiefe in $monate Monaten"
    override val wrKetteTitel = "Was damals trug"
    override val wrKeineKette = "Keine belegte Kette hinterlegt."
    override val wrTabelle = "Bilanz je Zeichen"
    override fun wrZeile(treffer: Int, von: Int, fehl: Int, vorlauf: Int?) =
        "$treffer von $von erkannt\n$fehl Warnungen ohne Einbruch" + (vorlauf?.let { "\nØ $it Mon. Vorlauf" } ?: "")
    override fun wrHeute(stand: Int) = when (stand) { 2 -> "Warnung"; 1 -> "Keine Warnung"; else -> "Ohne Daten" }
    override val wrKurve = "Warnung befolgen gegen Halten"
    override val wrFolgen = "Warnung befolgen"
    override val wrHalten = "Halten"
    override val wrBefolgen = "Befolgen"
    override val wrWechsel = "Wechsel"
    override fun wrKurveWerte(rendite: String, rueck: String, wechsel: Int) = "$rendite pro Jahr · größter Rückgang $rueck · $wechsel Wechsel"
    override val wrKosten = "Mit 0,1 % je Wechsel, ohne Steuern und Gebühren. Beide Linien beginnen bei 100."
    override fun wrSatz(treffer: Int, von: Int, fehl: Int, phasen: Int, spanneVon: Int, spanneBis: Int, zeit: Int) = when {
        von == 0 -> "Zu wenige Einbrüche mit Daten."
        else -> "Stand vor $treffer von $von Einbrüchen auf Warnung (Spanne $spanneVon bis $spanneBis %). $fehl von $phasen Warnphasen blieben ohne Einbruch. Das Zeichen stand in $zeit % der Zeit auf Warnung."
    }
    override val wrFehlendTitel = "Ohne Rückblick"
    override fun wrFehlend(ids: String) = "Keine freie Reihe über genug Jahre: $ids."
    override fun wrKurzzeile(treffer: Int, von: Int, fehl: Int) = "Rückblick: $treffer von $von Einbrüchen vorher angezeigt, $fehl Warnungen ohne Einbruch"
    override fun wrHinweisZeichen(id: String, ersatz: Boolean, revidiert: Boolean, eigeneRegel: Boolean) = listOfNotNull(
        when (id) {
            "hochzins" -> "Ersatzreihe der Fed: Kreditaufschlag nach Gilchrist und Zakrajšek ab 1973, mal 2,5 auf die Spanne des Hochzins-Spreads gebracht"
            "buffett" -> "Wilshire 5000 ab 1989 wie in der App. Davor Börsenwert der US-Unternehmen aus der Finanzierungsrechnung der Fed, auf den Wilshire 5000 umgerechnet und zwischen den Quartalen mit dem S&P 500 fortgeschrieben"
            else -> "Ersatzreihe des Chicago Fed (NFCI), Schwellen der App übertragen"
        }.takeIf { ersatz },
        "Eigene Regel, die App zeigt dieses Zeichen nicht".takeIf { eigeneRegel },
        "Spätere Korrekturen der Reihe machen das Zeichen im Rückblick besser, als es damals war".takeIf { revidiert },
    ).joinToString(". ")
    override fun wrStand(stand: String, bis: String) = "Stand $stand, Daten bis $bis"
    override val wrBeleg = "Beleg"
    override fun redditMeldungTitel(name: String) = "$name wird auf Reddit viel besprochen"
    override fun redditMeldungText(jetzt: Int, vorher: Int) = "$jetzt Erwähnungen in deutschen Börsen-Subreddits in 24 Stunden, davor $vorher."
    override val trRedditDeHinweis = "Deutsche Subreddits: r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien und r/Finanzen über deinen eigenen Reddit-Zugang (Profil › Zugänge). Die App liest die Beiträge der letzten 24 Stunden je Subreddit und zählt, welche Werte aus dem Katalog, deinem Depot und deiner Watchlist darin genannt werden. Je Beitrag zählt ein Wert einmal. Die Veränderung gegenüber den 24 Stunden davor erscheint, sobald die App einen Stand von gestern gespeichert hat."
    override fun trRedditDeAbdeckung(stunden: Int) = "Gezählt sind nur die letzten $stunden Stunden, weiter zurück reicht die Schnittstelle bei so vielen Beiträgen nicht."
    override fun trRedditDeFehlt(subs: String) = "Nicht erreicht: $subs. Die Zahlen sind unvollständig."
    override val trRedditBeitraege = "Reddit-Beiträge"
    override fun trRedditAnzahl(n: Int) = if (n == 1) "1 Beitrag im letzten Monat" else "$n Beiträge im letzten Monat"
    override val trRedditKeineBeitraege = "Im letzten Monat keine Beiträge in r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien, r/Finanzen, r/wallstreetbets, r/stocks und r/investing."
    override fun trRedditStimmung(steigend: Int, fallend: Int, offen: Int) = "Stimmung nach einfachen Wortregeln: $steigend eher steigend, $fallend eher fallend, $offen ohne klare Richtung."
    override fun trRedditPunkte(punkte: Int, kommentare: Int) = "$punkte Punkte, $kommentare Kommentare"
    override val trRedditKeine = "Reddit-Daten gerade nicht erreichbar. Später noch einmal versuchen."
    override val trLaedt = "Wird geladen …"
    override val trMehr = "Mehr anzeigen"
    override fun trRang(rang: Int, ticker: String) = "Platz $rang · $ticker"
    override fun trErwaehnungen(n: Int) = if (n == 1) "1 Erwähnung" else "${"%,d".format(n).replace(',', '.')} Erwähnungen"
    override val trYahoo = "Bei Yahoo Finance gerade gefragt"
    override val trWiki = "Interesse auf Wikipedia"
    override fun trWikiProTag(n: String) = "Ø $n Aufrufe am Tag"
    override fun trRedditZeit(tage: Int) = when (tage) { 1 -> "24 Std."; 7 -> "Woche"; 30 -> "Monat"; else -> "Jahr" }
    override fun trSpitze(datum: String) = "am meisten am $datum"
    override fun trSpitzeWoche(datum: String) = "am meisten in der Woche ab $datum"
    override fun trVerlaufSeit(datum: String?) = if (datum == null)
        "Noch kein Verlauf: ApeWisdom nennt nur die letzten 24 Stunden, deshalb schreibt die App die Erwähnungen ab jetzt jeden Tag mit, auch im Hintergrund. Woche, Monat und Jahr füllen sich mit der Zeit."
    else "Verlauf seit $datum: ApeWisdom nennt nur die letzten 24 Stunden, deshalb schreibt die App die Erwähnungen jeden Tag mit, auch im Hintergrund. Woche, Monat und Jahr füllen sich mit der Zeit."
    override val trErwaehnungenKurz = "Erwähnungen"
    override val trWertHeuteNicht = "Heute nicht unter den 100 meistbesprochenen"
    override val trWertYahooText = "Unter den meistaufgerufenen Werten bei Yahoo Finance in deiner Region oder in den USA."
    override val orderGeaendert = "Order geändert"
    override val orderAendern = "Ändern"
    override val orderAendernSpeichern = "Änderung speichern"
    override fun trImportTitel(quelle: String) = "$quelle importieren?"
    override val trImportLaedt = "Buchungen werden gelesen und den Kursen zugeordnet …"
    override fun trImportZeitraum(von: String, bis: String, zeilen: Int) = "Export vom $von bis $bis, $zeilen Zeilen"
    override fun trImportOhneKurs(liste: String) = "Ohne Kurs, nicht im Depot (Wert fehlt deshalb): $liste"
    override fun trImportOhneKauf(liste: String) = "Verkauft, ohne dass der Kauf in der Datei steht (der Export beginnt wohl später). Als zum Verkaufskurs gekauft gerechnet: $liste"
    override fun trImportUngelesen(n: Int, beispiele: String) = "Nicht gelesen ($n Zeilen, unbekannte Buchungsart oder fehlende Angaben), nicht im Depot: $beispiele"
    override fun trImportWechselkurs(n: Int) = "$n Zeilen in Fremdwährung ohne Wechselkurs, die Beträge sind unverändert als Euro übernommen."
    override fun trImportBekannt(bekannt: Int, gesamt: Int) = "$bekannt von $gesamt Zeilen stehen schon im Depot. Beim Zusammenführen kommen nur die neuen dazu."
    override fun trImportUeberGrenze(n: Int, max: Int) = "Mit dieser Datei hätte das Depot mehr Buchungen, als die App hält (höchstens $max). Die ältesten $n entfielen bei der nächsten Buchung."
    override fun trImportUngeprueft(quelle: String) = "Der Aufbau dieser Datei ist nicht an einem echten Export von $quelle geprüft. Kontrolliere Bestand und Guthaben nach dem Import."
    override fun trImportGuthabenNegativ(betrag: String, degiro: Boolean) =
        (if (degiro) "Der Kontoauszug mit den Einzahlungen fehlt. " else "Es fehlen Einzahlungen in der Datei. ") + "Das Guthaben würde $betrag, deshalb ist die Übernahme gesperrt."
    override val taxImportOhneSteuer = "Importierte Verkäufe ohne Steuerangaben in der Datei: Gewinne und Verluste stehen in den Töpfen, einbehaltene Steuern sind nicht eingerechnet."
    override val trImportHinweis = "Echte Derivate von Trade Republic (Knock-outs, Optionsscheine, Faktor-Zertifikate) stehen mit Käufen, Verkäufen und Rückzahlungen in den Umsätzen und im Depotverlauf, Kurse gibt es für sie nicht. Den Export erzeugst du in der Trade-Republic-App unter Profil > Abrechnungen > Transaktionsexport."
    override val brokerImportHinweis = "Übernommene Bestände zählen mit ihren einzelnen Kaufposten, Verkäufe werden nach FIFO verrechnet. Werte ohne Yahoo-Kurs stehen mit ihren Buchungen in den Umsätzen und im Depotverlauf, aber nicht im Depot."
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
    override val pmLoading = "Märkte werden geladen …"
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
        "KAUF_NETZ" -> "Der Markt ist gerade nicht erreichbar. Es wurde nichts gesetzt."
        "MARKT_ZU" -> "Der Markt ist geschlossen. Es wurde nichts gesetzt."
        "NETZ" -> "Der Markt ist gerade nicht erreichbar. Es wurde nichts verkauft."
        else -> "Die Wette ließ sich nicht ausführen."
    }
    override val pmSellTitle = "Wette verkaufen"
    override val pmSellErloes = "Erlös jetzt"
    override val pmSellGeldkurs = "Geldkurs"
    override val pmSellWert = "Wert zum angezeigten Kurs"
    override val pmSellAbschlag = "Abschlag"
    override val pmSellLaedt = "Aktueller Kurs wird geladen …"
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
    override val pmNoBets = "Noch keine Wetten."
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
    override val pvLoading = "Verlauf wird berechnet …"
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
        "Aus $geschaefte gemeldeten Geschäften ($jahr aus der Jahresmeldung, $einzel aus den Einzelmeldungen). Im Verlauf stehen $eingerechnet Geschäfte in $werte Aktien und ETFs mit Kurs. Beträge als Mitte der gemeldeten Spanne, Optionen als Basiswert gezählt, Kurse ohne Dividenden. $anleihen Geschäfte mit Anleihen, Vorzugsaktien und Einlagen haben keinen Tageskurs und stehen außerhalb. Am Stichtag machten sie etwa $anteil des Depots aus." +
            (ohneKuerzel?.let { " $it Geschäfte ließen sich keinem Börsenkürzel zuordnen." } ?: "") +
            (ohneKurs?.let { " $it betreffen Werte ohne Kurse." } ?: "") +
            (ohneBestand?.let { " $it Verkäufe ohne vorherigen Kauf bleiben außen vor." } ?: "") +
            " Berechnet bis $stichtag, danach mit dem Bestand an diesem Tag fortgeschrieben."
    override val truthTitle = "Truth Social"
    override val truthHinweis = "trumpstruth.org ist ein Archiv der Organisation Defending Democracy Together, nicht verbunden mit Truth Social oder Donald Trump. Der Beitrag öffnet im Archiv."
    override val truthRemoved = "Gelöscht"
    override val truthRepost = "Weitergeteilt"
    override val truthQuote = "Zitat"
    override fun truthImages(n: Int) = if (n == 1) "1 Bild" else "$n Bilder"
    override val truthVideo = "Video"
    override val truthNone = "Das Archiv ist gerade nicht erreichbar."
    override val truthMore = "Ältere Beiträge"
    override val pvNoteStrategy = "Musterdepot aus Euro-ETFs mit jährlicher Umschichtung auf die Anteile."
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
    override val pmHistoryLoading = "Kursverläufe der Wetten werden geladen …"
    override val pmHistoryNone = "Für diesen Zeitraum gibt es noch keinen Verlauf."
    override val pmHistoryNote = "Gewinn und Verlust aller Wetten: eingesetztes und ausgezahltes Übungsgeld plus der Wert der gehaltenen Anteile zum damaligen Kurs."
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
    override val newsWichtigFarbig = "Kategorien farbig"
    override val newsImportantExplain = "Orange markiert sind Schlagzeilen zu großen Ereignissen wie Zinsentscheiden, Kriegen, Zöllen oder starken Marktbewegungen, besonders bei mehreren Medien oder wenn ein Wert aus Depot oder Watchlist genannt wird.\n\nMeinungen, Vorschauen und Kauftipps werden nie markiert."
    override val ptIndexTitle = "Statt deiner Käufe der Index"
    override fun zgTerms(dienst: String) = when (dienst) {
        "guardian" -> "Kostenloser Entwicklerschlüssel für nicht kommerzielle Nutzung: höchstens 500 Abrufe am Tag und einer je Sekunde. Die App nutzt höchstens 450, fragt die zwei Rubriken alle 15 Minuten ab und behält Inhalte des Guardian höchstens 24 Stunden."
        "finnhub" -> "Kostenloser Schlüssel nur für den persönlichen Gebrauch: 60 Abrufe je Minute, die Daten dürfen nicht weitergegeben werden. Die App fragt die vier Kategorien alle 10 Minuten ab."
        "fec" -> "Schlüssel von api.data.gov, kostenlos mit einer E-Mail-Adresse: 1.000 Abrufe je Stunde. Die App nutzt höchstens 900 je Stunde, mit dem Testschlüssel höchstens 25 je Stunde und 45 am Tag. Abgefragt werden nur die Summen der Komitees und ihre Empfänger, keine Namen einzelner Spender."
        "dol" -> "Schlüssel des Open Data Portal des US-Arbeitsministeriums, kostenlos nach Anmeldung mit kurzem Fragebogen. Der Schlüssel gehört nur dir und darf nicht weitergegeben werden. Die App fragt höchstens 10 Mal je Minute und 400 Mal am Tag und merkt sich Ergebnisse zwölf Stunden."
        "uspto" -> "Schlüssel des Open Data Portal des US-Patentamts, kostenlos mit einem USPTO-Konto und einer Identitätsprüfung über ID.me. Ungenutzte Schlüssel verfallen nach 90 Tagen. Die App fragt höchstens 500 Mal am Tag, einzeln nacheinander."
        "reddit" -> "Reddit-API für nicht kommerzielle Nutzung: 100 Abrufe je Minute.\n\n• Seit Ende 2025 gibt Reddit neue Zugänge nur nach einem Antrag frei. Die Selbstanmeldung ist geschlossen.\n• Mit einem freigegebenen Zugang legst du unter reddit.com/prefs/apps eine App an und trägst ihre Client-ID ein, bei einer App vom Typ „script“ oder „web app“ auch das Secret.\n• Die App meldet sich nur als App an, ohne dein Reddit-Konto, und liest nur.\n• Sie nutzt höchstens 30 Abrufe je Minute und 2.000 am Tag und zeigt keine Beiträge ab 18."
        "alphavantage" -> "Kostenloser Schlüssel: 25 Abrufe am Tag. Die App fragt höchstens einmal je Stunde und höchstens 22-mal am Tag. Alpha Vantage kennzeichnet seine Nachrichten als Premium. Lehnt der Dienst einen kostenlosen Schlüssel ab, zeigt die App seine Meldung."
        "eodhd" -> "EODHD-Schlüssel nur für den persönlichen Gebrauch: Die Daten dürfen weder weitergegeben noch anderen gezeigt werden, der Zugang darf nicht geteilt werden. 20 Calls am Tag, Rücksetzung um Mitternacht GMT. Ein Abruf kostet 5 Calls, also rund 4 Abrufe am Tag. Die App fragt nur beim Aufklappen auf der Seite eines Werts und merkt sich das Ergebnis eine Stunde."
        else -> "DeepL API Free: 500.000 Zeichen im Monat. Die App nutzt höchstens 450.000 in 31 Tagen und merkt sich jede Übersetzung, damit derselbe Text nur einmal zählt. Übertragen werden nur Marktinhalte und Beschreibungstexte, keine Daten von dir."
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

    override val dvSubtitle = "Wert und Gewinne, dein Depot gegen einen Index mit denselben Ein- und Auszahlungen"
    override val dvNone = "Noch keine Käufe."
    override val dvMoves = "Größte Bewegungen"
    override fun dvRaster(name: String) = when (name) { "WOCHE" -> "Woche"; "MONAT" -> "Monat"; "QUARTAL" -> "Quartal"; "JAHR" -> "Jahr"; else -> "Tag" }
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
    override val dvNote = "Ergebnis eines Werts an einem Tag: seine Wertänderung ohne den Betrag von Kauf und Verkauf. Gebühren, Steuern, Zinsen und Dividenden zählen hier nicht."
    override val indButton = "Indikatoren"
    override val gsrTitel = "Gesamtrichtung"
    override fun gsrWort(wort: String) = when (wort) {
        "STEIGEND" -> "steigend"
        "EHER_STEIGEND" -> "eher steigend"
        "EHER_FALLEND" -> "eher fallend"
        "FALLEND" -> "fallend"
        else -> "ohne klare Richtung"
    }
    override fun gsrZaehlung(steigend: Int, fallend: Int, neutral: Int) = "$steigend steigend · $fallend fallend · $neutral neutral"
    override val gsrHinweis = "Eine Zählung der Signale aller Indikatoren, keine Vorhersage. Verwandte Indikatoren zählen als ein Signal."
    override fun gsrFamilie(key: String) = when (key) {
        "durchschnitt" -> "Durchschnitte"
        "baender" -> "Bänder und Kanäle"
        "zone" -> "Überkauft und überverkauft"
        "umsatz" -> "Umsatz"
        "schwung" -> "Momentum"
        else -> "Trendstärke"
    }
    override val gsrEinSignal = "Zählt als ein Signal"
    override fun kzImChart(name: String, datum: String) = "Im erweiterten Chart ansehen: $name am $datum"
    override fun kzName(muster: String) = when (muster) {
        "hammer" -> "Hammer"
        "inv_hammer" -> "Inverted Hammer"
        "haengender" -> "Hanging Man"
        "sternschnuppe" -> "Shooting Star"
        "verschlingen_auf" -> "Bullish Engulfing"
        "verschlingen_ab" -> "Bearish Engulfing"
        "durchstossen" -> "Piercing Line"
        "wolke" -> "Dark Cloud Cover"
        "morgenstern" -> "Morning Star"
        "abendstern" -> "Evening Star"
        "soldaten" -> "Three White Soldiers"
        "kraehen" -> "Three Black Crows"
        else -> "Doji"
    }
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
        "kerzen" -> "Kerzenformationen"
        "kreuz" -> "Golden/Death Cross"
        else -> "MACD"
    }
    override fun indLage(art: String, lage: String) = when ("$art/$lage") {
        "kerzen/hammer" -> "Hammer: nach Rückgang, Erholung möglich"
        "kerzen/inv_hammer" -> "Inverted Hammer: nach Rückgang, Erholung möglich"
        "kerzen/haengender" -> "Hanging Man: nach Anstieg, Rücksetzer möglich"
        "kerzen/sternschnuppe" -> "Shooting Star: nach Anstieg, Rücksetzer möglich"
        "kerzen/verschlingen_auf" -> "Bullish Engulfing: nach Rückgang, Erholung möglich"
        "kerzen/verschlingen_ab" -> "Bearish Engulfing: nach Anstieg, Rücksetzer möglich"
        "kerzen/durchstossen" -> "Piercing Line: nach Rückgang, Erholung möglich"
        "kerzen/wolke" -> "Dark Cloud Cover: nach Anstieg, Rücksetzer möglich"
        "kerzen/morgenstern" -> "Morning Star: nach Rückgang, Erholung möglich"
        "kerzen/abendstern" -> "Evening Star: nach Anstieg, Rücksetzer möglich"
        "kerzen/soldaten" -> "Three White Soldiers: nach Rückgang, Erholung möglich"
        "kerzen/kraehen" -> "Three Black Crows: nach Anstieg, Rücksetzer möglich"
        "kerzen/doji" -> "Doji: Unentschlossenheit, keine Richtung"
        "kerzen/keins" -> "Keine Formation in den letzten 5 Handelstagen"
        "kreuz/golden" -> "Ø 50 Tage über dem Ø 200 Tage seit dem Golden Cross: langfristig aufwärts"
        "kreuz/death" -> "Ø 50 Tage unter dem Ø 200 Tage seit dem Death Cross: langfristig abwärts"
        "kreuz/keins" -> "Kein Kreuzen der beiden Durchschnitte in der Kursreihe"
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
        "POSITIV" -> "positiv"
        "NEGATIV" -> "negativ"
        "GEMISCHT" -> "gemischt"
        else -> "neutral"
    }
    override fun indGrund(art: String, lage: String, prozent: String, zahl: String, kreuz: String?, kreuzAuf: Boolean, eng: Boolean): String = when (art) {
        "kerzen" -> if (kreuz == null) "" else "Erkannt am $kreuz" + when (zahl) { "0" -> ", dem letzten Handelstag"; "1" -> ", einen Handelstag zuvor"; else -> ", $zahl Handelstage zuvor" }
        "kreuz" -> when (lage) {
            "golden" -> "Der Ø 50 Tage kreuzte den Ø 200 Tage am $kreuz nach oben (Golden Cross). Heute liegt er $prozent darüber"
            "death" -> "Der Ø 50 Tage kreuzte den Ø 200 Tage am $kreuz nach unten (Death Cross). Heute liegt er $prozent darunter"
            else -> "Seit Beginn der Kursreihe kreuzten sich die beiden Durchschnitte nicht"
        }
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
        "sma50" -> "Durchschnitt der Schlusskurse der letzten 50 Handelstage.\n\n• Liegt der Kurs darüber, ging es zuletzt eher aufwärts.\n• Trends halten oft eine Weile, für einzelne Aktien ist das Signal aber schwach."
        "sma200" -> "Durchschnitt der letzten 200 Handelstage, ein Maß für den langfristigen Trend.\n\n• Kreuzt der 50-Tage-Durchschnitt ihn nach oben, heißt das Golden Cross, nach unten Death Cross.\n• Gilt als Trendzeichen, kommt aber oft spät."
        "bb" -> "Zwei Linien über und unter dem Durchschnitt der letzten 20 Tage.\n\n• Ihr Abstand richtet sich danach, wie stark der Kurs in diesen 20 Tagen geschwankt hat: In unruhigen Zeiten gehen sie auseinander, in ruhigen rücken sie zusammen.\n• Rund 9 von 10 Schlusskursen liegen zwischen ihnen. Ein Kurs außerhalb ist also ungewöhnlich weit gelaufen.\n• Oft geht es danach wieder zur Mitte, in starken Trends läuft er aber auch länger am Band entlang.\n\nIm oberen Teil der Spanne ist der Kurs zuletzt gestiegen (positiv), im unteren gefallen (negativ), in der Mitte gibt es keine klare Richtung. Außerhalb der Bänder ist das Bild gemischt: Die Bewegung war kräftig, eine Gegenbewegung ist möglich, aber nicht sicher."
        "rsi" -> "Vergleicht die Kursgewinne mit den Kursverlusten der letzten 14 Tage.\n\n• Hohe Werte heißen: zuletzt fast nur gestiegen, niedrige: fast nur gefallen.\n• Starke Aktien bleiben oft lange „überkauft“.\n• Überkauft und überverkauft gelten hier als gemischt: Der Kurs ist weit gelaufen, eine Gegenbewegung ist möglich, aber nicht sicher."
        "stoch" -> "Zeigt, wo der heutige Schlusskurs zwischen dem tiefsten und dem höchsten Kurs der letzten 14 Tage liegt: 0 am Tief, 100 am Hoch.\n\n• Kreuzt der Wert seinen eigenen Durchschnitt der letzten 3 Tage, gilt das als Wendesignal.\n• Reagiert schnell und gibt viele Fehlsignale."
        "atr" -> "Average True Range: wie weit sich der Kurs an einem Handelstag im Mittel bewegt, über 14 Tage und mit Kurslücken über Nacht, hier in Prozent des Kurses.\n\n• Sagt nichts über die Richtung, nur wie unruhig es ist.\n• Hilft beim Abstand eines Stopps: Liegt er näher als etwa zwei ATR, kann ihn schon ein gewöhnlicher Tag auslösen."
        "obv" -> "On-Balance-Volume ist eine laufende Summe: Schließt der Kurs höher als am Vortag, kommt der ganze Tagesumsatz dazu, schließt er tiefer, wird er abgezogen.\n\n• Die Höhe der Linie bedeutet nichts. Es zählt ihre Richtung im Vergleich zum Kurs.\n• Laufen beide in dieselbe Richtung, gilt der Trend als bestätigt.\n• Laufen sie auseinander, gilt er als wackelig.\n• Bewegt sich der Kurs kaum, zeigt OBV nur, an welchen Tagen mehr gehandelt wurde.\n• Vereinfachung: Zu jedem Kauf gehört ein Verkauf. „Mehr Umsatz an Plustagen“ heißt nicht, dass mehr gekauft als verkauft wurde.\n• Bei Werten mit wenig Umsatz ungenau, für Indizes fehlt der Umsatz oft ganz."
        "ichimoku" -> "Japanisches Trendsystem aus den Mitten von Hoch und Tief der letzten 9, 26 und 52 Tage.\n\n• Daraus entsteht eine „Wolke“, die um 26 Tage nach vorn versetzt ist.\n• Im Chart sind ihre beiden Ränder und die 26-Tage-Mitte zu sehen.\n• Über der Wolke gilt der Trend als aufwärts, darunter als abwärts, in der Wolke als unklar.\n• Folgt dem Trend und reagiert deshalb spät.\n• Die Wolke ist bis heute gezeichnet, ihre Vorschau auf die nächsten 26 Tage fehlt."
        "kreuz" -> "Golden Cross heißt: Der Durchschnitt der letzten 50 Handelstage kreuzt den der letzten 200 Handelstage nach oben, Death Cross: nach unten.\n\n• Beide gelten als Trendzeichen. Sie kommen spät, denn beide Linien laufen dem Kurs hinterher, und in Seitwärtsphasen kreuzen sie sich oft mehrfach ohne Folge.\n• Im Chart steht an jedem Kreuzen eine Raute: grün beim Golden Cross, rot beim Death Cross."
        "kerzen" -> "Der Kerzenchart zeigt jeden Tag als Kerze aus Eröffnung, Hoch, Tief und Schluss. Eine Kerzenformation ist eine erkannte Abfolge von ein bis drei Kerzen der letzten Tage, die als Hinweis auf eine Wende gilt. Die Trefferzeile darüber zeigt, wie oft das bei diesem Wert gestimmt hat.\n\n• Nach einem Rückgang, Erholung möglich: Hammer, Inverted Hammer, Bullish Engulfing, Piercing Line, Morning Star, Three White Soldiers.\n• Nach einem Anstieg, Rücksetzer möglich: Hanging Man, Shooting Star, Bearish Engulfing, Dark Cloud Cover, Evening Star, Three Black Crows.\n• Als Rückgang oder Anstieg zählt der Kursverlauf der fünf Tage davor.\n• Doji: Eröffnung und Schluss fast gleich, Unentschlossenheit.\n• Genannt wird die jüngste Formation der letzten 5 Handelstage.\n• Ohne Eröffnungskurse (manche Indizes und Fonds) fehlt die Erkennung."
        "sar" -> "Parabolic SAR ist ein mitlaufender Stopp: im Aufwärtstrend unter dem Kurs, im Abwärtstrend darüber.\n\n• Mit jedem neuen Hoch oder Tief rückt er schneller nach.\n• Durchbricht der Kurs ihn, wechselt die Richtung und der Stopp springt auf die andere Seite.\n• Gut in klaren Trends, in Seitwärtsphasen wechselt er ständig."
        else -> "Abstand zwischen dem 12- und dem 26-Tage-Durchschnitt, verglichen mit seinem eigenen Durchschnitt über 9 Tage (Signallinie).\n\n• Liegt er darüber, nimmt der Schwung zu.\n• Reagiert verzögert und gibt in Seitwärtsphasen viele Fehlsignale."
    }
    override val indFuss = "Die Einordnung bedeutet nach der üblichen Lesart:\n• positiv: spricht eher für steigende Kurse\n• negativ: spricht eher für fallende Kurse\n• gemischt: weit gelaufen, eine Gegenbewegung ist möglich\n\nDie Aussagekraft ist gering, Indikatoren lesen nur den bisherigen Kurs."
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
    override fun newsSeiteAbgelehnt(code: Int) = "Die Seite lehnt den Abruf ab ($code)."
    override fun newsSeiteWeg(code: Int) = "Die Seite gibt es nicht mehr ($code)."
    override fun newsSeiteZuOft(code: Int) = "Die Seite nimmt gerade keine Abrufe an ($code)."
    override fun newsSeiteNichtErreichbar(code: Int) = "Die Seite ist gerade nicht erreichbar ($code)."
    override val newsSeiteVpn = "Mit VPN sperren manche Seiten den Zugriff."
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
        "schwankung.depot" -> "Schwankung (nur Wertpapiere)"
        "rueckgang.depot" -> "Größter Rückgang (nur Wertpapiere)"
        "sharpe" -> "Sharpe-Quote"
        "sortino" -> "Sortino-Quote"
        "calmar" -> "Calmar-Quote"
        "ulcer" -> "Ulcer-Index"
        "var" -> "VaR 95 % (1 Tag)"
        "es" -> "ES 95 % (1 Tag)"
        "var.woche" -> "VaR 95 % (1 Woche)"
        "es.woche" -> "ES 95 % (1 Woche)"
        "dauer" -> "Tage unter Hoch"
        "beta" -> "Beta"
        "alpha" -> "Alpha pro Jahr"
        else -> "Korrelation"
    }
    override fun pvMetricExplain(k: String) = when (k) {
        "gesamt" -> "Veränderung im gewählten Zeitraum."
        "jahr" -> "Durchschnittliche Rendite pro Jahr, erst ab etwa einem Jahr Zeitraum."
        "schwankung" -> "Wie stark die Linie hin- und herschwankt, auf ein Jahr gerechnet (Volatilität).\n\n• 20 % heißt: In etwa zwei von drei Jahren liegt das Ergebnis höchstens 20 Prozentpunkte über oder unter dem Durchschnitt.\n• Breite Aktien-ETFs liegen bei etwa 15 %, einzelne Aktien oft bei 30 % und mehr."
        "rueckgang" -> "Größter Rückgang von einem Hoch bis zum folgenden Tief. Zeigt, wie viel man im schlechtesten Moment zwischendurch verloren hätte."
        "sharpe" -> "Wie viel Rendite die Schwankung eingebracht hat: Rendite pro Jahr über dem Zins auf Bargeld, geteilt durch die Schwankung.\n\n• Beispiel: 10 % Rendite bei 2 % Zins und 16 % Schwankung ergibt 0,5.\n• Unter 0 lag die Rendite unter dem Zins auf Bargeld, um 0,5 ist für Aktienmärkte üblich, über 1 ist sehr gut.\n• Bei gleicher Rendite ist die Linie mit der höheren Quote ruhiger gelaufen."
        "sortino" -> "Wie die Sharpe-Quote, aber nur Schwankungen nach unten zählen als Risiko, denn Sprünge nach oben stören niemanden. Liegt sie deutlich über der Sharpe-Quote, kam die Schwankung vor allem von Anstiegen."
        "calmar" -> "Rendite pro Jahr geteilt durch den größten Rückgang im gewählten Zeitraum.\n\n• Beispiel: 10 % pro Jahr bei höchstens 25 % Rückgang ergibt 0,4.\n• Höher ist besser.\n• Die übliche Fassung rechnet über drei Jahre."
        "ulcer" -> "Misst, wie tief und wie lange die Linie im Mittel unter ihrem letzten Hoch lag, in Prozent. 0 heißt stetig ohne Rückschläge, unter 5 ruhig, über 15 lange und tiefe Verlustphasen."
        "var" -> "Ein schlechter, aber gewöhnlicher Abschnitt: An 95 von 100 Tagen (bei langen Zeiträumen Wochen) war der Verlust kleiner als dieser Wert.\n\n• Bei Tageswerten fällt etwa ein Tag im Monat schlechter aus.\n• Gezeigt ab 100 Werten."
        "es" -> "Wie schlimm die übrigen 5 von 100 Tagen oder Wochen im Mittel waren, also die wirklich schlechten. Mindestens so groß wie der VaR."
        "dauer" -> "Längste Zeit, bis ein früheres Hoch wieder erreicht war.\n\n• „+“ heißt, das dauert noch an.\n• So lange hätte man auf das alte Hoch warten müssen."
        "beta" -> "Wie stark die erste Linie mit dieser mitgeht.\n\n• 1 heißt: Bewegt sich diese Linie um 1 %, bewegt sich die erste im Mittel auch um 1 %.\n• 1,5 heißt heftiger, 0,5 ruhiger, unter 0 eher gegenläufig."
        "alpha" -> "Rendite pro Jahr der ersten Linie, die sich nicht aus dem Mitgehen (Beta) erklärt.\n\n• Positiv heißt: besser, als es die Bewegung dieser Linie erwarten ließ, negativ: schlechter.\n• Schwankt stark mit dem Zeitraum."
        else -> "Wie gleich beide Linien laufen, von 1 bis −1.\n\n• Bei 1 bewegen sie sich immer gleich, bei 0 unabhängig, bei −1 entgegengesetzt.\n• Für eine bessere Streuung im Depot hilft alles unter etwa 0,7."
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
    override fun opHerkunft(h: de.troetenrepublik.app.market.MarketApi.Herkunft, offen: Boolean) = when (h) {
        de.troetenrepublik.app.market.MarketApi.Herkunft.US -> "Anteil Calls am Optionsumsatz"
        de.troetenrepublik.app.market.MarketApi.Herkunft.EURONEXT -> "Anteil Calls am Umsatz der Aktienoptionen an der Euronext"
        de.troetenrepublik.app.market.MarketApi.Herkunft.HKEX -> "Anteil Calls am Umsatz der Aktienoptionen an der HKEX"
        de.troetenrepublik.app.market.MarketApi.Herkunft.EUREX -> if (offen) "Anteil Calls an den offenen Kontrakten der Aktienoptionen an der Eurex" else "Anteil Calls am Umsatz der Aktienoptionen an der Eurex"
        de.troetenrepublik.app.market.MarketApi.Herkunft.TAIFEX -> if (offen) "Anteil Calls an den offenen Kontrakten der Aktienoptionen an der TAIFEX" else "Anteil Calls am Umsatz der Aktienoptionen an der TAIFEX"
        de.troetenrepublik.app.market.MarketApi.Herkunft.JPX -> "Anteil Calls am Umsatz der Aktienoptionen an der Osaka Exchange"
    }
    override val opPcr = "Put/Call-Verhältnis"
    override val opPcrOffen = "Offene Kontrakte"
    override val opHvLabel = "Schwankung der Aktie, 1 Jahr"
    override fun opBewegung(tag: String) = "Erwartete Bewegung bis $tag"
    override fun opMaxPain(tag: String) = "Max Pain $tag"
    override fun opHv(wert: String) = "Aktie $wert"
    override val opAuffaellig = "Auffällige Optionen"
    override val opStand = "Stand letzter Handelstag, offene Kontrakte vom Vortag."
    override val orKarte = "Optionsrechner"
    override val orSymbol = "Symbol"
    override val orLaden = "Laden"
    override val orVerfall = "Verfall"
    override fun orVorlage(key: String) = when (key) {
        "LONG_CALL" -> "Long Call"; "LONG_PUT" -> "Long Put"; "COVERED_CALL" -> "Covered Call"; "CASH_SECURED_PUT" -> "Cash-Secured Put"
        "BULL_CALL_SPREAD" -> "Bull Call Spread"; "BEAR_PUT_SPREAD" -> "Bear Put Spread"; "STRADDLE" -> "Straddle"; "STRANGLE" -> "Strangle"
        "IRON_CONDOR" -> "Iron Condor"; "BUTTERFLY" -> "Butterfly"; "EIGENE" -> "Eigene Optionen"
        "COLLAR" -> "Collar"; "SCHUTZ_PUT" -> "Schutz-Put"
        else -> key
    }
    override val orKauf = "Kauf"
    override val orVerkauf = "Verkauf"
    override fun orArt(key: String) = when (key) { "CALL" -> "Call"; "PUT" -> "Put"; else -> "Aktie" }
    override val orBasispreis = "Basispreis"
    override val orMenge = "Menge"
    override val orBeinNeu = "Option"
    override fun orBeinOption(preis: String, iv: String, berechnet: Boolean) = "Preis $preis · IV $iv" + if (berechnet) " · berechnet" else ""
    override fun orBeinAktie(menge: Int, preis: String) = "${menge * 100} Aktien zu $preis"
    override val orBreakEven = "Gewinnschwelle"
    override val orMaxGewinn = "Maximaler Gewinn"
    override val orMaxVerlust = "Maximaler Verlust"
    override val orUnbegrenzt = "unbegrenzt"
    override val orChance = "Chance auf Gewinn"
    override val orKosten = "Kosten"
    override val orErloes = "Erlös"
    override val orHeute = "heute"
    override fun orAmDatum(datum: String) = if (datum == orHeute) "Heute" else "Am $datum"
    override fun orGriechName(key: String) = key
    override val orBewegungTitel = "Erwartete Bewegung je Verfall"
    override val orKeine = "Keine Optionen zu diesem Symbol"
    override val orNichtMoeglich = "Mit diesem Verfall nicht aufstellbar"
    override fun orLesen(kurs: String, verfall: String, tag: String, datum: String) = "Bei $kurs: $verfall am Verfall, $datum " + if (tag == orHeute) tag else "am $tag"
    override val opStillhalter = "Optionen verkaufen"
    override fun opTage(tage: Int) = if (tage == 1) "1 Tag" else "$tage Tage"
    override val opStillCalls = "Call schreiben auf eigene Aktien"
    override val opStillPuts = "Put schreiben mit bereitgehaltenem Geld"
    override val opBasis = "Basis"
    override val opPraemie = "Prämie"
    override val opRenditeJahr = "Ertrag pro Jahr"
    override fun opStillBeispiel(call: List<String>?, put: List<String>?) = listOfNotNull(
        call?.let { "Call zu ${it[0]}: Die Prämie von ${it[1]} je Aktie bringt bis zum Verfall ${it[2]}. Steigt der Kurs über ${it[0]}, gehen die Aktien zu diesem Preis weg, mit Kursgewinn zusammen ${it[3]}. Ein Verlust entsteht erst unter ${it[4]}." },
        put?.let { "Put zu ${it[0]}: Die Prämie von ${it[1]} je Aktie bringt bis zum Verfall ${it[2]} auf das bereitgehaltene Geld. Fällt der Kurs unter ${it[0]}, kaufst du die Aktien zu diesem Preis, abzüglich Prämie also zu ${it[3]}." },
    ).joinToString("\n")
    override fun opErklaerung(k: String) = when (k) {
        "stillhalter" -> "Wer eine Option verkauft (schreibt), kassiert sofort die Prämie und verpflichtet sich, die Aktie zum Basispreis abzugeben (Call) oder zu kaufen (Put).\n\n• Gerechnet wird mit dem Geldkurs, dem Preis, den Käufer gerade bieten, je Aktie ohne Gebühren. Ein Kontrakt umfasst meist 100 Aktien.\n• Die Rendite bezieht sich beim Call auf den Kurs, beim Put auf den Basispreis, und ist aufs Jahr hochgerechnet.\n• „Verfällt wertlos“ stammt aus der impliziten Volatilität des Kontrakts und ist eine Modellrechnung.\n• Steigt die Aktie stark, entgeht beim Call der Gewinn über dem Basispreis.\n• Fällt sie stark, trägt man beim Put den Verlust selbst, nur gemindert um die Prämie.\n• Im Übungsdepot lassen sich Optionen kaufen und schreiben. Geschrieben wird im Optionsrechner, Aktien oder Cash bleiben als Deckung gebunden."
        "pcr" -> "Gehandelte Puts je Call und offene Puts je offenem Call. Der Umsatz zählt wie beim Long/Short-Balken: die nächsten sechs Verfälle, nur der jüngste Handelstag.\n\n• Über 1 sichern sich viele gegen fallende Kurse ab oder setzen darauf.\n• Unter 1 überwiegen Calls, unter etwa 0,7 deutlich."
        "bewegung" -> "Preis von Call und Put am Geld zusammen.\n\n• So weit bewegt sich der Kurs nach Einschätzung des Optionsmarkts bis zum Verfall im Mittel, nach oben oder unten.\n• In etwa drei von fünf Fällen bleibt er innerhalb."
        "maxpain" -> "Kurs, bei dem die Optionen des nächsten Verfalls insgesamt am wenigsten wert verfallen.\n\n• Kurz vor dem Verfall nähert sich der Kurs dem manchmal an.\n• Eine Vorhersage ist es nicht."
        "iv" -> "Schwankung, die der Optionsmarkt für die nächsten 30 Tage einpreist, auf ein Jahr gerechnet (IV30).\n\n• Aus den beiden Verfällen um 30 Tage gemittelt. Liegt nur einer in der Nähe, zählt er bis 15 Tage Abstand.\n• Daneben steht die tatsächliche Schwankung der Aktie im letzten Jahr.\n• Liegt die implizite deutlich darüber, erwartet der Markt Unruhe, etwa vor Quartalszahlen."
        "wand" -> "Basispreise mit den meisten offenen Puts (Put-Wand) und den meisten offenen Calls (Call-Wand).\n\n• Dort liegen viele Positionen, der Kurs stockt dort manchmal.\n• Eine Vorhersage ist es nicht."
        "gex" -> "Gamma-Exposure (GEX): wie stark Händler, die Optionen verkauft haben, bei Kursbewegungen gegensteuern müssen.\n\n• Positiv dämpft das die Schwankung.\n• Negativ verstärkt es sie.\n• Die Linie summiert die Werte über die Basispreise."
        "nullpunkt" -> "Kurs, an dem das Netto-GEX das Vorzeichen wechselt.\n\n• Auf der einen Seite dämpft das Absichern der Händler die Bewegung, auf der anderen verstärkt es sie."
        "ivrv" -> "Implizite Volatilität (IV30) geteilt durch die tatsächliche Schwankung der letzten 30 Tage.\n\n• Über 1 preist der Markt mehr Unruhe ein, als die Aktie zuletzt zeigte.\n• Unter 1 weniger."
        "skew" -> "Implizite Volatilität eines Puts mit 25 Delta minus die am Geld, bei Verfall um 30 Tage. 25 Delta ist ein Put etwas außerhalb des Geldes.\n\n• Positiv kostet Absicherung nach unten mehr als nach der Mitte."
        "ivrang" -> "Wo die heutige IV30 zwischen dem tiefsten (0 %) und höchsten Wert (100 %) der gespeicherten Tageswerte liegt.\n\n• Hoch heißt: Optionen sind im Vergleich zur eigenen Geschichte teuer."
        "ivperz" -> "Anteil der gespeicherten Tage, an denen die IV30 niedriger war als heute.\n\n• 90 % heißt: Nur an jedem zehnten Tag waren Optionen teurer."
        "iv" -> "Schwankung, die der Optionsmarkt für die nächsten 30 Tage einpreist, auf ein Jahr gerechnet (IV30).\n\n• Aus den beiden Verfällen um 30 Tage gemittelt. Liegt nur einer in der Nähe, zählt er bis 15 Tage Abstand.\n• Daneben steht die tatsächliche Schwankung der Aktie im letzten Jahr.\n• Liegt die implizite deutlich darüber, erwartet der Markt Unruhe, etwa vor Quartalszahlen."
        else -> "Kontrakte mit mindestens so viel Umsatz wie offenen Kontrakten, gehandelter Prämie ab 50.000 $ und Verfall in den nächsten 60 Tagen.\n\n• Das deutet auf neue, große Positionen hin.\n• Ob gekauft oder verkauft wurde, schätzt die App aus dem letzten Kurs."
    }
    override val opEbenen = "Offene Kontrakte je Basispreis"
    override val opLegendeCalls = "Calls"
    override val opLegendePuts = "Puts"
    override val opCallWand = "Call-Wand"
    override val opPutWand = "Put-Wand"
    override val opNettoGex = "Netto-GEX"
    override val opNullpunkt = "Nullpunkt"
    override val opNaeherung = "Gamma: eigene Rechnung"
    override val opKurs = "Kurs"
    override val opVola = "Volatilität"
    override val opIv30 = "IV30"
    override val opRv30 = "Realisiert 30 Tage"
    override val opIvZuRv = "IV zu realisiert"
    override val opPutAufschlag = "Put-Aufschlag 25 Delta"
    override val opLaufzeit = "Laufzeitstruktur"
    override val opIvRang = "IV-Rang"
    override val opIvPerzentil = "IV-Perzentil"
    override fun opIvSeit(tag: String) = "IV-Verlauf seit $tag"
    override val slAbstand = "Abstand"
    override val slLaufzeit = "Laufzeit"
    override val slCall = "Call"
    override val slPut = "Put"
    override val slPraemie = "Prämie"
    override val slRenditeJahr = "pro Jahr"
    override val slAusserhalb = "Verfällt wertlos"
    override val slSpread = "Geld-Brief-Spanne"
    override fun slZahlen(tag: String) = "Quartalszahlen $tag, vor Fälligkeit"
    override val slLeer = "Keine Optionen für diese Werte"
    override val slLaedt = "Lädt"
    override fun slFortschritt(n: Int, m: Int) = "$n von $m"
    override val akMinVolumen = "Mindestvolumen"
    override val akVolumen = "Volumen"
    override val akOffen = "Offen"
    override val akVerhaeltnis = "Volumen/Offen"
    override val akLeer = "Keine auffälligen Optionen"
    override val stLeer = "Keine neuen Stufen in den letzten 7 Tagen"
    override val stErst = "Erstbewertung"
    override val opNasdaqGamma = "Nasdaq-Gamma"
    override fun opGexMischung(echt: Int, naeher: Int) = "Gamma: $echt von Nasdaq, $naeher eigene Rechnung"
    override val zmTitel = "Pressemitteilung zu den Zahlen"
    override val zmInhalt = "Kennzahlen und Text"
    override val zmOhneKontakt = "Kontaktadresse unter Zugänge eintragen, dann erscheint hier die Mitteilung der Firma"
    override val zmKeine = "Keine Mitteilung gefunden"
    override val zmOriginal = "Original bei der SEC"
    override val zmAusblick = "Ausblick"
    override fun zmVom(tag: String) = "Mitteilung vom $tag"
    override fun zmVorquartal(wert: String) = "Vorquartal $wert"
    override fun zmArt(key: String) = when (key) {
        "UMSATZ" -> "Umsatz"; "BRUTTO" -> "Bruttogewinn"; "BETRIEB" -> "Betriebsergebnis"; "GEWINN" -> "Nettogewinn"
        "EPS" -> "Gewinn je Aktie, verwässert"; else -> "Operativer Cashflow"
    }
    override val bhLeihgebuehr = "Leihgebühr bei iBorrowDesk"
    override val vgTitel = "Vergütung der Führung"
    override fun vgCeo(jahr: Int) = "CEO-Vergütung $jahr"
    override val vgZugeflossen = "Tatsächlich zugeflossen"
    override fun vgUebrige(jahr: Int) = "Übrige Vorstände Ø $jahr"
    override val vgRendite = "Aktienrendite pro Jahr"
    override fun vgMio(zahl: String) = "$zahl Mio."
    override fun vgMrd(zahl: String) = "$zahl Mrd."
    override fun vgTsd(zahl: String) = "$zahl Tsd."
    override val vgInfo = "Vergütungstabelle der Proxy-Meldung, Werte in Dollar je Geschäftsjahr.\n• Tatsächlich zugeflossen: Aktien und Optionen zählen mit dem Wert nach der Kursentwicklung statt zum Zusagetag. Steigt der Kurs, liegt der Betrag meist über der zugesagten Vergütung.\n• Übrige Vorstände: Durchschnitt der weiteren Vorstände, die die Firma nennt.\n• Rendite: Aktie samt Dividenden im Geschäftsjahr."
    override val pdTitel = "Präsidialdokumente"
    override fun pdArt(art: String) = when (art) { "ERLASS" -> "Executive Order"; "PROKLAMATION" -> "Proklamation"; else -> "Memorandum" }
    override fun pdThema(id: String) = when (id) {
        "STAHL" -> "Stahl"; "ALUMINIUM" -> "Aluminium"; "KUPFER" -> "Kupfer"; "HALBLEITER" -> "Halbleiter"; "PHARMA" -> "Arzneimittel"
        "FLUGZEUGE" -> "Flugzeuge"; "DROHNEN" -> "Drohnen"; "SOLAR" -> "Polysilizium"; "HOLZ" -> "Holz"; "DUENGER" -> "Dünger"
        "KOHLE" -> "Kohle"; "OEL_GAS" -> "Öl"; else -> id
    }
    override fun pdImTitel(was: String) = "Im Titel: $was"
    override fun pdNeu(anzahl: Int) = if (anzahl == 0) "keine in 30 Tagen" else "$anzahl in 30 Tagen"
    override fun pdZuletzt(datum: String) = "zuletzt $datum"
    override fun pdAnzahl(n: Int) = if (n == 1) "1 Dokument" else "$n Dokumente"
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
    override val kaLaedt = "Geschäfte und Kurse werden geladen …"
    override val kaVergleich = "S&P 500 pro Jahr"
    override val kaHinweisVerlauf = "Wöchentlich neu zusammengesetzt, in Dollar."
    override val kaJetzt = "Zusammensetzung heute"
    override val cuTitel = "Übersicht"
    override val cuNavUnter = "Größte Positionen, Meistgehandeltes, Käufe unter Einstiegskurs"
    override fun cuSeite(k: String) = when (k) { "GEHANDELT" -> "Meistgehandelt"; "EINSTIEG" -> "Unter Einstiegskurs"; else -> "Größte Positionen" }
    override fun cuErklaerung(k: String) = when (k) {
        "POSITIONEN_BESTAND" -> "Gemeldete Bestände zum Stichtag, bewertet mit den Angaben der Meldung. Je Aktie die Summe über die Gruppe."
        "EINSTIEG_PERSON" -> "Einstieg: Schlusskurs am Tag des Geschäfts, bei mehreren Käufen nach Betrag gewichtet. Verkäufe mindern den Bestand, nach vollständigem Verkauf beginnt der Einstieg neu. Unter Verkauft: ganz verkaufte Positionen und Verkäufe ohne Kauf davor."
        "EINSTIEG_INSIDER" -> "Einstieg je Person: gemeldeter Kaufpreis, bei mehreren Käufen nach Betrag gewichtet. Nur Käufe am Markt, ohne Zuteilungen und Optionen. Verkäufe mindern den Bestand, nach vollständigem Verkauf beginnt der Einstieg neu. Unter Verkauft: ganz verkaufte Positionen und Verkäufe am Markt ohne Kauf davor aus zwölf Monaten."
        "EINSTIEG_VERKAUFT_PERSON" -> "Verkauft: zuerst Werte, die nach einem Kauf ganz verkauft wurden, mit dem Kurs heute gegen den letzten Einstiegskurs. Danach Verkäufe ohne Kauf davor in den Meldungen."
        "EINSTIEG_VERKAUFT" -> "Zuerst Personen, die am Markt gekauft und danach alles verkauft haben, mit dem Kurs heute gegen ihren letzten Einstiegskurs. Danach Verkäufe am Markt ohne Kauf davor aus zwölf Monaten, etwa Aktien aus Zuteilungen: Summe, Verkaufskurs und der Kurs heute dagegen. Die Minderung des Bestands steht da, wenn die Meldung den Bestand nennt. Abgaben für Steuern, Schenkungen und Ausübungen zählen nicht."
        "EINSTIEG_INVESTOR" -> "Einstieg: Kurs am Quartalsende, in dem der Kauf gemeldet wurde, bei mehreren Quartalen nach Betrag gewichtet. Der Kauf lag irgendwann im Quartal. Gezählt wird, was laut Meldung noch gehalten wird."
        "GEHANDELT" -> "Zahl der gemeldeten Käufe und Verkäufe je Aktie im Zeitraum und ihr geschätztes Volumen."
        "EINSTIEG" -> "Aktien, die unter dem durchschnittlichen Einstiegskurs der Gruppe stehen. Einstieg je Person: Schlusskurs am Tag des Geschäfts, bei mehreren Käufen nach Betrag gewichtet. Bei Investoren der Kurs zum Quartalsende, bei Insidern der gemeldete Preis. Weicht er stark vom Schlusskurs des Tages ab, gilt der Schlusskurs. Verkäufe mindern den Bestand, nach vollständigem Verkauf beginnt der Einstieg neu. Unter Verkauft: ganz verkaufte Positionen und Verkäufe am Markt ohne Kauf davor aus zwölf Monaten."
        else -> "Geschätzter Wert: gemeldete Käufe minus Verkäufe je Person, Spannen mit ihrer Mitte."
    }
    override fun cuKurz(k: String) = when (k) {
        "POSITIONEN_BESTAND" -> "Gemeldete Bestände, je Aktie die Summe"
        "GEHANDELT" -> "Gemeldete Käufe und Verkäufe im Zeitraum"
        "EINSTIEG" -> "Kurs unter dem mittleren Einstieg der Gruppe"
        else -> "Geschätzt aus Käufen minus Verkäufen"
    }
    override fun cuPersonen(n: Int) = if (n == 1) "1 Person" else "$n Personen"
    override fun cuKaeufeAnzahl(n: Int) = if (n == 1) "1 Kauf" else "$n Käufe"
    override fun cuVerkaeufeAnzahl(n: Int) = if (n == 1) "1 Verkauf" else "$n Verkäufe"
    override val cuNachAnzahl = "Nach Zahl"
    override val cuNachVolumen = "Nach Volumen"
    override fun cuKaeufer(n: Int) = if (n == 1) "1 Käufer" else "$n Käufer"
    override fun cuKaufAm(datum: String, betrag: String) = "Kauf am $datum · $betrag"
    override fun cuKaeufeBis(n: Int, datum: String, betrag: String) = "$n Käufe, zuletzt am $datum · $betrag"
    override fun cuQuartalBis(datum: String, betrag: String) = "Quartal bis $datum · $betrag"
    override fun cuQuartaleBis(n: Int, datum: String, betrag: String) = "$n Quartale, zuletzt bis $datum · $betrag"
    override val cuLeer = "Keine Daten."
    override val cuLeerZeitraum = "Keine Geschäfte im Zeitraum."
    override val cuKeineUnter = "Keine Werte unter dem Einstiegskurs."
    override fun ieKern(unter: Int, alle: Int) = "$unter von $alle Käufern"
    override fun ieKeiner(alle: Int) = if (alle == 1) "1 Käufer, nicht unter Einstiegskurs." else "Keiner von $alle Käufern unter Einstiegskurs."
    override fun ieEinstieg(preis: String) = "Einstieg $preis"
    override fun ieReduziert(anteil: String) = "Bestand um $anteil reduziert"
    override val ieVerkauft = "Verkauft"
    override fun ieVerkauftAm(datum: String) = "Verkauft am $datum"
    override fun ieVerkaufskurs(preis: String) = "Verkauf $preis"
    override fun ieVerkaeufe(betrag: String) = "Verkäufe $betrag"
    override fun ieWerte(n: Int) = if (n == 1) "1 Wert" else "$n Werte"
    override fun cuStand(wann: String) = "Kurse nicht abrufbar, Stand: $wann"
    override val cuKeineKurse = "Kurse nicht abrufbar."
    override val rgTitel = "Rangliste"
    override val rgNav = "Wessen Käufe den Markt geschlagen haben"
    override fun rgOrdnung(k: String) = when (k) { "QUOTE" -> "Trefferquote"; "KAEUFE" -> "Käufe"; "VERMOEGEN" -> "Vermögen"; "MELDEVERZUG" -> "Meldeverzug"; else -> "Vorsprung" }
    override fun rgVermoegen(s: String) = "Vermögen $s"
    override fun vmSpanne(von: String, bis: String) = "$von bis $bis"
    override fun vmTitel(jahr: Int) = "Vermögen laut Jahresmeldung $jahr"
    override val vmHinweis = "Summe der Wertspannen aus der Jahresmeldung an den Clerk of the House. Selbst genutzte Wohnungen und staatliche Renten fehlen, das Vermögen kann höher sein."
    override val bhTitel = "Politik und Behörden"
    override val bhLobbying = "Lobbying"
    override fun bhJeQuartal(betrag: String) = "$betrag im Quartal"
    override fun bhQuartal(q: Int, j: Int, betrag: String, firmen: Int) = "Q$q $j: $betrag" + if (firmen > 0) ", $firmen beauftragte Lobbyfirmen" else ""
    override fun bhThemen(liste: String) = "Themen: $liste"
    override fun bhLobbyingHinweis(stand: String) = "Stand $stand. Meldet die Firma selbst, enthält ihr Betrag auch die Zahlungen an Lobbyfirmen."
    override fun bhGesetzeWert(gesetze: Int, personen: Int) = listOfNotNull(if (gesetze > 0) "$gesetze Gesetze" else null, if (personen > 0) "$personen handelten" else null).joinToString(" · ")
    override fun bhGesetz(g: String, n: Int) = "$g, in $n Meldungen genannt"
    override val bhAuftraege = "Regierungsaufträge"
    override fun bhFiskaljahr(betrag: String, jahr: Int) = "$betrag im Fiskaljahr $jahr"
    override fun bhFiskaljahrZeile(jahr: Int, betrag: String, laufend: Boolean) = "Fiskaljahr $jahr: $betrag" + if (laufend) " (läuft noch)" else ""
    override fun bhZitat(text: String) = "„$text“"
    override val bhAuftraegeHinweis = "Verpflichtungen aus Bundesaufträgen, Fiskaljahr ab 1. Oktober. Darunter die größten Aufträge der letzten zwölf Monate mit ihrem Gesamtwert über die ganze Laufzeit."
    override val bhSpenden = "Wahlkampfspenden"
    override fun bhSpendenWert(betrag: String, zyklus: Int) = "$betrag (${zyklus - 1}/${zyklus % 100})"
    override val bhKeinPac = "Kein Wahlkampfkomitee (PAC) gefunden"
    override fun bhPacSummen(zyklus: Int, einnahmen: String, kandidaten: String) = "Wahlzyklus ${zyklus - 1}/${zyklus % 100}: Einnahmen $einnahmen, an Kandidaten $kandidaten. Größte Empfänger:"
    override fun bhSpendenHinweis(demo: Boolean) = "Spenden des Wahlkampfkomitees der Firma (PAC). Das Komitee sammelt Geld bei den Beschäftigten, das Firmenvermögen bleibt außen vor." + if (demo) " Ohne eigenen Schlüssel (Zugänge) nur wenige Abrufe je Stunde." else ""
    override val bhLeer = "Leerverkäufe"
    override val bhFinraOhne = "FINRA-Zugang eintragen"
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
    override val dpErklaerung = "Alternative Handelssysteme (ATS) sind Handelsplätze außerhalb der Börsen, an denen Aufträge vorab nicht sichtbar sind (Dark Pool).\n\n• Gezählt sind die Aktien, die alle ATS für die Woche an FINRA melden, als Anteil am Wochenumsatz aller Handelsplätze.\n• Ein Anstieg zeigt mehr Handel großer Pakete und sagt nichts über Kauf oder Verkauf.\n• Übriger außerbörslicher Handel fehlt."
    override fun bhLeerTage(anteil: String, tage: Int) = "Leer verkauft in den letzten $tage Handelstagen: $anteil des außerbörslich gemeldeten Umsatzes"
    override fun bhLeerBestand(stichtag: String, aktien: String, aenderung: String?, tage: String?) = "Offene Leerverkäufe am $stichtag: $aktien Aktien" + (aenderung?.let { " ($it zum Stichtag davor)" } ?: "") + (tage?.let { ", $it Tage zum Eindecken" } ?: "")
    override fun bhLeerAnteil(tage: Int) = "Leer verkauft, $tage Handelstage"
    override val bhLeerAnteilHinweis = "Anteil am außerbörslich gemeldeten Umsatz"
    override fun bhLeerOffen(stichtag: String) = "Offene Leerverkäufe am $stichtag"
    override val bhLeerAenderung = "Änderung zum Stichtag davor"
    override val bhLeerEindecken = "Tage zum Eindecken"
    override val dpPlattformen = "Größte Plattformen"
    override fun bhLeerVerlauf(seit: String, tief: String, hoch: String) = "Offene Leerverkäufe seit $seit, zwischen $tief und $hoch Mio. Aktien"
    // [q78e]
    override val hqTitel = "Bewertung gegen die eigene Geschichte"
    override fun hqArt(art: Int) = when (art) { 0 -> "KGV"; 1 -> "KUV"; 2 -> "KCV"; else -> "EV/EBITDA" }
    override fun hqSatz(art: String, heute: String, median: String, abstand: String, ueber: Boolean) =
        "$abstand " + (if (ueber) "über" else "unter") + " dem Median von $median"
    override fun hqBand(unten: String, oben: String) = "Mittlere 80 %: $unten bis $oben"
    override fun hqBereich(seit: String, n: Int) = "Seit $seit, $n Stichtage"
    override val hqLeerTitel = "Short Interest (Nasdaq)"
    override fun hqLeerStand(datum: String, aktien: String, anteil: String?) =
        "Stand $datum: $aktien Aktien leer verkauft" + (anteil?.let { ", $it des Streubesitzes" } ?: "")
    override fun hqLeerTage(tage: String, vorher: String?) = tage + (vorher?.let { " (vorher $it)" } ?: "")
    override val hqLeerAnteil = "Leerverkäufe in % des Streubesitzes"
    override val hqLeerAktien = "Leerverkäufe in Mio. Aktien"
    override val hqLeerTageReihe = "Tage bis Deckung"
    override val hqKurs = "Kurs"
    override val hqFtdTitel = "Fail-to-Deliver (SEC)"
    override fun hqFtdStand(datum: String, menge: String) = "Zuletzt $datum: $menge Aktien nicht geliefert"
    override fun hqFtdHoch(menge: String, datum: String) = "Höchststand im Zeitraum: $menge am $datum"
    override val hqFtdKeine = "Keine Meldungen in den letzten Monaten"
    override fun hqMitarbeiter(seit: Int) = "Mitarbeitende seit $seit"
    override val hqFtdReihe = "Nicht gelieferte Aktien je Tag"
    override fun hqRegSho(ja: Boolean) = if (ja) "Auf der Reg-SHO-Schwellenliste der Nasdaq" else "Nicht auf der Reg-SHO-Schwellenliste der Nasdaq"
    override val hqVerlaeufe = "Verläufe"
    override val hqUmsatzTtm = "Umsatz, letzte zwölf Monate"
    override val hqMarktwert = "Börsenwert"
    override val hqExportFinanzen = "Finanzen als CSV speichern"
    override val hqExportKurse = "Kurse als CSV speichern (OHLCV)"
    override fun hqExportFertig(n: Int) = "$n Zeilen gespeichert"
    override val bhLeerErklaerung = "Oben der Anteil leer verkaufter Aktien am Umsatz der letzten Handelstage laut FINRA-Meldung der Handelsplätze außerhalb der Börse.\n\n• Marktmacher verkaufen dort oft leer, ein hoher Wert ist normal.\n• Aussagekräftiger ist der Bestand offener Leerverkäufe, den FINRA zweimal im Monat veröffentlicht. Die Linie zeigt ihn bis zu drei Jahre.\n• Steigt er mit dem Kurs, müssen sich viele Leerverkäufer bei weiteren Gewinnen eindecken."
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
    override fun pmGrossZeile(ausgang: String, preis: String, wann: String) = "„$ausgang“ zu $preis · $wann"
    override val bhSpendenLimit = "Der gemeinsame Testschlüssel der FEC ist gerade ausgeschöpft. Mit einem eigenen kostenlosen Schlüssel unter Zugänge geht es sofort."

    // 5.91 Kurschart und Marktkurven
    override val kcSpanne = "Kursspanne"
    override fun kcLinie(art: String) = when (art) { "EINSTAND" -> "Einstand"; "LIMIT" -> "Limit"; "STOPP" -> "Stopp"; "ALARM" -> "Preismarke"; else -> "KO" }
    override fun kcMarke(art: String, stueck: String, betrag: String, kurs: String) = when (art) {
        "KAUF" -> "Kauf $stueck Stk. zu $kurs"
        "VERKAUF" -> "Verkauf $stueck Stk. zu $kurs"
        else -> "Dividende $betrag"
    }
    override fun kcSpanneQuelle(implizit: Boolean, sigma: String) =
        if (implizit) "Aus der impliziten Volatilität der Optionen, $sigma pro Jahr" else "Aus der Schwankung der letzten 60 Handelstage, $sigma pro Jahr"
    override val mkTitel = "Marktkurven"
    override val mkUntertitel = "Zinskurve USA und Euroraum, VIX-Kurve"
    override val mkZins = "Zinskurve"
    override val mkUsa = "USA, Staatsanleihen"
    override val mkEuro = "Euroraum, AAA-Staatsanleihen"
    override val mkDeutschland = "Deutschland, Bundesanleihen"
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
    override val mkZinsMehr = "• Normal: sie steigt, weil längere Bindung mehr Zins bringt\n• Flach: kurze und lange Laufzeiten bringen fast gleich viel\n• Invers: kurze Laufzeiten bringen mehr als zehn Jahre\n\nEine inverse Kurve ging in den USA vielen Rezessionen voraus, mit Monaten bis Jahren Abstand. Nicht auf jede folgte eine Rezession. Rote Marken zeigen kurze Laufzeiten über dem Zins für zehn Jahre."
    override val mkVix = "VIX-Kurve"
    override fun mkVixForm(form: String) = when (form) {
        "BACKWARDATION" -> "Fallend (Backwardation): kurzfristig mehr Angst als langfristig"
        "FLACH" -> "Flach: kurz- und langfristig ähnliche Erwartung"
        else -> "Steigend (Contango): der Normalfall"
    }
    override val mkVixMehr = "Die VIX-Indizes zeigen, wie stark Anleger den S&P 500 in den nächsten Tagen bis zu einem Jahr schwanken sehen, abgeleitet aus Optionspreisen.\n• Steigende Kurve (Contango): Normalfall, in ruhigen Zeiten wird kurzfristig weniger Schwankung erwartet\n• Fallende Kurve (Backwardation): in den nächsten Tagen wird mehr Schwankung erwartet, das zeigt akute Angst und kam vor allem in Ausverkäufen vor"
    override val mkKeineDaten = "Gerade keine Daten erreichbar. Später noch einmal versuchen."

    // 5.91 Strategie-Labor, Backtest-Lotterie, Anlage-These, eigene Regeln, monatliche Einzahlung
    override val slTitel = "Strategietest"
    override val slUntertitel = "Signalregeln im Rückblick gegen Kaufen und Halten testen"
    override val slErklaerung = "Historisch und hypothetisch, nicht tatsächlich erzielt: Der Test zeigt, wie eine Regel in der Vergangenheit abgeschnitten hätte, für die Zukunft heißt das wenig.\n\n• Nach Kosten schneiden die meisten Signalregeln schlechter ab als Kaufen und Halten.\n• Gerechnet wird mit Tageskursen in Euro über die ganze Kursgeschichte, das Signal entsteht am Schlusskurs, gehandelt wird zur Eröffnung des nächsten Tags."
    override val slZuWenig = "Für diesen Wert gibt es zu wenige Tageskurse für einen Test."
    override fun slKosten(spread: String, dividenden: Boolean) = "Start mit 10.000 €. Kosten je Kauf und je Verkauf: $spread Spread (aus den Einstellungen) und 1 € Ordergebühr. " +
        if (dividenden) "Dividenden sind wieder angelegt, Steuern nicht eingerechnet." else "Dividenden und Steuern sind nicht eingerechnet."
    override val slRegeln = "Regeln"
    override fun slRegel(art: String, a: Int, b: Int, n: Int) = when (art) {
        "HALTEN" -> "Kaufen und Halten"
        "SMA" -> "Kurs über $a-Tage-Linie"
        "KREUZ" -> if (a == 50 && b == 200) "Kreuz 50/200 (Golden/Death Cross)" else "Kreuz $a/$b"
        "RSI" -> if (n == 14) "RSI: unter $a kaufen, über $b verkaufen" else "RSI $n Tage: unter $a kaufen, über $b verkaufen"
        "MACD" -> "MACD kreuzt die Signallinie"
        "DONCHIAN" -> "Ausbruch: $a-Tage-Hoch kaufen, $b-Tage-Tief verkaufen"
        "BOLLINGER" -> "Bollinger: unter unterem Band kaufen, über Mittellinie verkaufen"
        "RHYTHMUS" -> "16-Wochen-Rhythmus: nur Wochen 13 bis 15"
        "MOMENTUM" -> "12-Monats-Momentum: am Monatsende über dem Kurs vor einem Jahr"
        "RSI_KURZ" -> "Kurzer RSI im Aufwärtstrend: RSI $n unter $a kaufen"
        "MONATSWECHSEL" -> "Monatswechsel: letzter bis $a. Handelstag"
        "ZIELVOLA" -> "Zielvolatilität $a %: Positionsgröße nach Schwankung"
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
    override val slLernText = "Lernzeit: erste 70 % der Tage. Prüfzeit: letzte 30 %, mit frischen 10.000 €. Hält die Regel nur in der Lernzeit, war es vermutlich Zufall."
    override val slLotterie = "Zufallstest"
    override val slLotterieText = "Wer viele Varianten einer Regel testet, findet fast immer eine, die im Rückblick glänzt, oft durch Glück.\n\nAblauf:\n1. Alle Varianten laufen im Lernzeitraum, die mit der besten Sharpe-Quote (Rendite im Verhältnis zur Schwankung) wird gewählt\n2. Sie läuft dann im Prüfzeitraum, den sie nicht kannte\n3. Eine Prozentzahl sagt, wie wahrscheinlich ihr Ergebnis kein Glückstreffer ist\n\nDie Reiter wählen, welche Varianten getestet werden."
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
    override val slDsrText = "Die Prozentzahl ist die geschätzte Wahrscheinlichkeit, dass die Sharpe-Quote der besten Variante mehr ist als Glück bei der Auswahl unter vielen Versuchen (Deflated Sharpe Ratio, Bailey und López de Prado, 2014).\n\n• Ab 95 % hebt sich die Variante klar vom Zufall ab, darunter kann es ein Glückstreffer sein.\n• Gemessen wird gegen null, bei langem Kursanstieg kann die Zahl also auch ohne Vorteil gegenüber Halten hoch sein.\n• Schlägt die Variante im Prüfzeitraum Kaufen und Halten nicht, hilft ihr Vorsprung im Lernzeitraum wenig.\n• Weil sich die Varianten stark ähneln, ist die Zahl ein grober Anhaltspunkt."
    override val slAussage = "Aussagekraft"
    override val slAussagePruef = "Aussagekraft Prüfzeit"
    override fun slAussageStufe(key: String) = when (key) {
        "ZU_WENIG" -> "zu gering"
        "BEGRENZT" -> "begrenzt"
        else -> "brauchbar"
    }
    override fun slAussageText(trades: Int, jahre: String) = "$trades Geschäfte in $jahre Jahren.\n• Unter 10 Geschäften oder 2 Jahren sagt das Ergebnis fast nichts\n• Unter 30 Geschäften oder 5 Jahren kann ein einziges Geschäft es umdrehen\n• Die Grenzen sind Faustwerte"
    override fun slAussageHinweis(zuWenig: Boolean, trades: Int, jahre: String) =
        if (zuWenig) "Mit $trades Geschäften in $jahre Jahren sagt das Ergebnis fast nichts."
        else "Mit $trades Geschäften in $jahre Jahren kann ein einziges Geschäft das Ergebnis umdrehen."
    override val slParam = "Parameter-Landschaft"
    override val slParamText = "Jede Zelle ist eine Variante, die Farbe zeigt ihre Rendite pro Jahr gegen Kaufen und Halten: die Farbe für Gewinn bei Vorsprung, die für Verlust bei Rückstand, kräftiger bei größerem Abstand.\n• Eine einzelne kräftige Zelle zwischen blassen ist meist Zufall\n• Eine Fläche ähnlicher Farben zeigt eine Regel, die nicht an einer Zahl hängt\n• Der Ring markiert die Variante, die der Zufallstest im Lernzeitraum gewählt hat, der Punkt die angetippte Zelle\n• Zwischen Lern- und Prüfzeitraum wechseln: Was dort verschwindet, war Anpassung\n• Nachbarn sind die bis zu acht Varianten neben der gewählten. Gezeigt wird ihr mittlerer Vorsprung vor Kaufen und Halten in Prozentpunkten pro Jahr. Fällt er im Prüfzeitraum stark ab, hing das Ergebnis an einer einzigen Zahl"
    override val slParamKurz = "kurzer Durchschnitt, Tage"
    override val slParamLang = "langer Durchschnitt, Tage"
    override val slParamLaenge = "Länge des Durchschnitts, Tage"
    override fun slParamTage(n: Int) = "$n Tage"
    override val slParamKauf = "Kauf unter"
    override val slParamVerkauf = "Verkauf über"
    override val slParamLern = "Lernzeitraum"
    override val slParamPruef = "Prüfzeitraum"
    override val slNachbarnLern = "Nachbarn im Lernzeitraum"
    override val slNachbarnPruef = "Nachbarn im Prüfzeitraum"
    override val slZufall = "Gegen den Zufall"
    override val slZufallText = "Dieselbe Regel, um zufällige Tage verschoben: gleich viele Geschäfte, gleich lange im Markt, aber ohne Bezug zu den Kursen. So sieht man, was Zufall bei gleichem Muster schafft.\n• Die Zahl ist der Anteil der 1.000 verschobenen Varianten mit mindestens der Rendite der Regel\n• Unter 5 % spricht für mehr als Zufall\n• Sie gilt für diese eine, vorher gewählte Regel, für die beste von vielen Varianten gilt der Zufallstest"
    override val slZufallAnteil = "Zufall mindestens so gut"
    override fun slZufallErgebnis(besserAls: Int) = "Die Regel liegt vor $besserAls von 100 zufällig verschobenen Varianten."
    override val slZufallRegelWaehlen = "Ohne Verkaufssignal gibt es nichts zu verschieben. Wähle eine andere Regel in der Einzelansicht."
    override val slHaltenKurz = "Halten"
    override val slWf = "Walk-Forward"
    override val slWfText = "Statt einer festen Teilung:\n1. Aus den letzten 5 Jahren wird die beste Variante gewählt\n2. Sie handelt das nächste Jahr, das sie nicht kannte\n3. Dann rückt das Fenster ein Jahr vor\nAlle Prüfjahre zusammen ergeben die Kurve, mit durchgehendem Kapital und echten Wechseln. Gerechnet wird mit der ganzen Kursgeschichte, ab 12 Jahren.\n• Tabelle: Prüfbeginn, gewählte Regel, Rendite pro Jahr im Lernen, Ergebnis im Prüfjahr und Ergebnis von Kaufen und Halten im selben Jahr\n• Prüfen zu Lernen: mittlere Sharpe-Quote im Prüfen im Verhältnis zum Lernen, unter 100 % trägt das Gelernte nicht voll\n• Die Auswahl unter vielen Varianten wird in jedem Fenster neu getroffen, eine Prozentzahl wie beim Zufallstest gibt es dafür nicht"
    override val slWfStart = "Walk-Forward rechnen"
    override fun slWfLaeuft(prozent: String) = "Walk-Forward läuft, $prozent"
    override val slWfZuWenig = "Walk-Forward braucht mindestens 12 Jahre Kursgeschichte."
    override val slWfFenster = "Prüfjahre"
    override val slWfZusammen = "Alle Prüfjahre pro Jahr"
    override val slWfBesser = "Vor Kaufen und Halten"
    override fun slWfBesserWert(besser: Int, gesamt: Int) = "$besser von $gesamt"
    override val slWfAbfall = "Prüfen zu Lernen"
    override fun slWfErgebnis(besser: Int, gesamt: Int, regel: String, halten: String) = "In $besser von $gesamt Prüfjahren lag die gewählte Regel vor Kaufen und Halten. Zusammen: $regel pro Jahr gegen $halten."
    override fun slWfRegel(art: String, a: Int, b: Int, n: Int) = when (art) {
        "SMA" -> "Linie $a"
        "KREUZ" -> "Kreuz $a/$b"
        "RSI" -> "RSI $n $a/$b"
        else -> art
    }
    override val slWfKopfBeginn = "Beginn"
    override val slWfKopfRegel = "Regel"
    override val slWfKopfLern = "Lernen"
    override val slWfKopfPruef = "Prüfen"
    override val slWfKopfHalten = "Halten"
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
    override val thFrage = "Kaufgrund noch gültig"
    override val thVerkaufenErwaegen = "Verkaufen erwägen"
    override val thEntfernen = "These entfernen"
    override fun thMitteilung(name: String) = "Prüftermin für $name erreicht. Kaufgrund prüfen."
    override val thBestaetigtToast = "Nächster Prüftermin in drei Monaten"
    override val thRegelnTitel = "Eigene Regeln"
    override val thRegelnText = "Warnung beim Kauf über der Grenze"
    override val thRegelPosition = "Höchstens in einer Position"
    override val thRegelHebel = "Keine Hebelprodukte"
    override val thRegelBargeld = "Mindestens als Guthaben"
    override val thAus = "Aus"
    override val thAn = "An"
    override val thRegelnOk = "Das Depot hält gerade alle eigenen Regeln ein."
    override val thAuswertung = "Thesen und Regeln"
    override val thMitThese = "davon mit These"
    override val thBestaetigt = "Prüftermine bestätigt"
    override fun thZaehlTeile(bestaetigt: Int, erwogen: Int, regelbrueche: Int, offen: Int) = listOf("$bestaetigt bestätigt", "$erwogen erwogen", "$regelbrueche Regelbrüche", "$offen offene Termine")
    override val thErwogen = "Verkauf erwogen"
    override val thRegelbrueche = "Käufe gegen eigene Regeln"
    override val thOffeneTermine = "Fällige Prüftermine"
    override val thAuswertungText = "Gezählt werden Käufe und Kauforders in der App, seit es Anlage-Thesen gibt. Eine These hält fest, warum du kaufst und wann du aussteigen willst. Am Prüftermin fragt die App, ob der Grund noch gilt. Wer seine Gründe aufschreibt, erkennt später leichter, ob ein Gewinn aus der These kam oder aus Glück."
    override val ezTitel = "Monatlich einzahlen"
    override fun ezAktiv(betrag: String, tag: String) = "$betrag $tag"
    override fun ezTagWert(tag: Int) = if (tag >= 31) "am Monatsende" else "am $tag."
    override val ezText = "Bucht jeden Monat am gewählten Tag eine Einzahlung ins Übungsdepot, vor den Sparplänen dieses Tags. Verpasste Monate holt die App einmal nach. Nur im Übungsdepot, nicht bei einem verbundenen echten Konto."
    override val ezTag = "Tag im Monat"
    override fun ezNaechste(datum: String) = "Nächste am $datum"
    override val ezBisher = "Bisher eingezahlt"
    override val ezAnzahl = "Einzahlungen"
    override fun ezBisherWert(n: Int, summe: String) = "$n× · $summe"
    override val ezLoeschen = "Monatliche Einzahlung löschen"
    override val ezGespeichert = "Monatliche Einzahlung gespeichert"
    override val ezMitteilung = "Monatliche Einzahlung"
    override fun ezMitteilungText(betrag: String) = "$betrag sind auf deinem Verrechnungskonto."

    // 5.91 Risikobeitrag, Bilanzqualitaet, Kaufkraft, Kennzahlenvergleich, Reverse DCF
    override val rskTitel = "Risikobeitrag"
    override fun rskDepotSchwankung(pct: String) = "Schwankung der heutigen Mischung (12 Monate): $pct pro Jahr"
    override val rskGewicht = "Gewicht"
    override val rskRisiko = "Anteil an der Schwankung"
    override fun rskZeile(gewicht: String, risiko: String) = "$gewicht des Depots · $risiko der Schwankung"
    override fun rskSatz(name: String, gewicht: String, risiko: String) = "$name ist $gewicht des Depots, macht aber $risiko der Schwankung aus."
    override val rskAusgeglichen = "Keine Position trägt deutlich mehr zur Schwankung bei, als ihr Gewicht erwarten lässt."
    override val rskOhneDaten = "Zu wenig gemeinsame Kurstage für eine Schätzung."
    override fun rskNichtEingerechnet(n: Int) = if (n == 1) "Eine Position ohne ausreichende Kurse ist nicht eingerechnet." else "$n Positionen ohne ausreichende Kurse sind nicht eingerechnet."
    override val rskHinweis = "Der Anteil an der Schwankung zeigt, wie viel vom Auf und Ab des Depots auf eine Position entfällt.\n\n• Grundlage sind die Wochenrenditen der letzten zwölf Monate.\n• Gerechnet wird Gewicht mal Beitrag zur Schwankung, geteilt durch die Schwankung des ganzen Depots.\n• Werte, die stark schwanken oder sich mit vielen anderen gleichzeitig bewegen, tragen mehr bei, als ihr Gewicht zeigt.\n• Zusammen ergeben die Anteile 100 %."
    override val rsTitel = "Rendite und Risiko der Positionen"
    override fun rsInfo(index: String) = "Jeder Punkt ist eine Position: waagrecht die Schwankung pro Jahr, senkrecht die Rendite pro Jahr, die Größe zeigt das Gewicht im Depot.\n\n• Das Depot und der Index $index stehen als eigene Marken im Bild.\n• Gerechnet mit Tageskursen über den gewählten Zeitraum, ohne Dividenden.\n• Antippen zeigt Name und Werte.\n• Bei vielen Positionen tragen nur die größten eine Beschriftung.\n• Positionen mit kürzerem Kursverlauf fehlen."
    override val rsLeer = "Ab zwei Positionen mit Kursverlauf über den gewählten Zeitraum gibt es hier etwas zu sehen."
    override val rsRendite = "Rendite pro Jahr"
    override fun rsOhneVerlauf(n: Int) = if (n == 1) "1 Position hat zu wenig Kursverlauf und fehlt." else "$n Positionen haben zu wenig Kursverlauf und fehlen."
    override val rskParitaetTitel = "Gleicher Risikoanteil"
    override val rskParitaetText = "So müssten die Gewichte aussehen, damit jede Position gleich viel zur Schwankung beiträgt (Risikoparität)."
    override val rskUebernehmen = "Im Umschichten übernehmen"
    override val rskWarnung = "Die Rechnung schätzt Schwankung und Gleichlauf aus nur einem Jahr. Schon kleine Schätzfehler verschieben die Gewichte deutlich, und künftig können Schwankung und Gleichlauf anders ausfallen."
    override val rskLaden = "Kurse des letzten Jahres werden geladen …"
    override val qsTitel = "Bilanzqualität"
    override val qsPiotroski = "Piotroski-F-Score"
    override fun qsScore(score: Int, von: Int) = "$score von $von Punkten"
    override fun qsStufe(stufe: String) = when (stufe) {
        "STARK" -> "stark"
        "MITTEL" -> "mittel"
        else -> "schwach"
    }
    override fun qsJahre(jahr: String, vorjahr: String) = "Geschäftsjahr bis\u00A0$jahr gegen\u00A0$vorjahr"
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
    override val qsAktienSpielraum = "Bis 0,5\u00A0% mehr zählt als unverändert"
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
    override val qsHinweis = "Beide Werte lesen die letzten Jahresabschlüsse.\n• Piotroski-F-Score: neun Punkte für Gewinn, Cashflow, sinkende Schulden, bessere Liquidität, keine neuen Aktien und steigende Effizienz gegenüber dem Vorjahr. 7 bis 9 Punkte gelten als stark, 0 bis 3 als schwach\n• Altman-Z-Score: fasst Bilanzkennzahlen zusammen und schätzt, wie nah eine Firma einer Pleite ist. Hersteller nutzen die Fassung von 1968, andere Firmen die spätere ohne Umsatz und mit Buchwert statt Börsenwert\n\nGrenzen: Beide blicken nur zurück, Sondereffekte verzerren sie. Für Banken, Versicherer und andere Finanzwerte passen beide nicht, für Immobiliengesellschaften passt Altman nicht."
    override val qsLaden = "Jahresabschlüsse werden geladen …"
    override val qsKeineDaten = "Für diesen Wert liefert Yahoo keine Jahresabschlüsse."
    override val kvTitel = "Kennzahlen im Vergleich"
    override val kvEinstieg = "Mit ähnlichen vergleichen"
    override val kvAehnlich = "Ähnlich laut Yahoo"
    override val kvBranche = "Gleiche Branche"
    override val kvSuche = "Weitere Aktie suchen"
    override val kvSucheHinweis = "Name oder Kürzel"
    override fun kvZeile(k: String) = when (k) {
        "KGV" -> "KGV"
        "KGV_ERWARTET" -> "KGV erwartet"
        "KUV" -> "KUV"
        "KBV" -> "Kurs-Buchwert"
        "EV_EBITDA" -> "EV/EBITDA"
        "BRUTTOMARGE" -> "Bruttomarge"
        "OPERATIVE_MARGE" -> "Operative Marge"
        "UMSATZWACHSTUM" -> "Umsatz Quartal"
        "VERSCHULDUNG" -> "Schulden je Eigenkapital"
        "DIVIDENDE" -> "Dividendenrendite"
        "KURS_CASHFLOW" -> "Kurs/Cashflow"
        "ROIC" -> "ROIC"
        "OWNER_EARNINGS" -> "Owner Earnings"
        "ZINSDECKUNG" -> "Zinsdeckung"
        "BETA" -> "Beta"
        "VOLATILITAET" -> "Schwankung pro Jahr"
        "MAX_RUECKGANG" -> "Größter Rückgang 1 Jahr"
        else -> "Kursziel"
    }
    override val kvHinweis = "Meist die letzten zwölf Monate. Kennzahlen verschiedener Branchen sind nur bedingt vergleichbar."
    override val kvLaden = "Kennzahlen werden geladen …"
    override val rdTitel = "Was im Kurs steckt"
    override fun rdSatz(x: String) = "Der heutige Preis setzt voraus, dass der Gewinn 10 Jahre lang um rund $x pro Jahr wächst."
    override fun rdSatzRand(x: String, mehr: Boolean) = if (mehr) "Der heutige Preis setzt voraus, dass der Gewinn 10 Jahre lang um mehr als $x pro Jahr wächst." else "Der heutige Preis setzt ein Gewinnwachstum von weniger als $x pro Jahr über 10 Jahre voraus."
    override val rdRendite = "Deine Renditeerwartung"
    override val rdZielKgv = "KGV nach 10 Jahren"
    override fun rdBranche(kgv: String) = "Branche $kgv"
    override fun rdKgvJetzt(kgv: String) = "Heutiges KGV: $kgv"
    override fun rdBisher(x: String, jahre: Int) = "Bisher: $x pro Jahr in den letzten $jahre Jahren"
    override fun rdAnalysten(x: String) = "Analysten erwarten für die nächsten zwölf Monate: $x"
    override val rdHinweis = "Gerechnet wird vom heutigen Kurs aus. Angenommen, der Gewinn wächst zehn Jahre gleichmäßig und die Aktie wird danach zum gewählten KGV gehandelt. Gezeigt wird das Wachstum, das für die gewählte Rendite pro Jahr nötig ist.\n\nLiegt es deutlich über dem bisherigen Wachstum, ist viel Optimismus eingepreist.\n\nDividenden, Schulden und Sondereffekte fehlen, kleine Änderungen der Annahmen verschieben das Ergebnis stark. Bei Verlusten lässt sich nichts rechnen."

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
    override fun ogeUngelesen(n: Int) = if (n == 1) "1 Meldung ist noch nicht gelesen. Die App liest sie beim nächsten Öffnen." else "$n Meldungen sind noch nicht gelesen. Die App liest sie beim nächsten Öffnen."
    override fun ogePdf(datum: String) = "Neueste Meldung vom $datum"
    override fun ogeGehandelt(n: Int) = if (n == 1) "Ein Regierungsmitglied hat diesen Wert gehandelt (Meldung 278-T)." else "$n Regierungsmitglieder haben diesen Wert gehandelt (Meldungen 278-T)."
    override val f144Titel = "Angekündigte Verkäufe"
    override val f144Hinweis = "Führungskräfte und Großaktionäre kündigen größere Verkäufe vorher bei der SEC an, mit Stückzahl, geschätztem Wert und geplantem Tag. Ob und wann wirklich verkauft wird, zeigt erst Form 4."
    override fun f144Stueck(n: String) = "$n Stück"
    override fun f144Geplant(tag: String) = "geplant $tag"
    override val f13Titel = "Großaktionäre (13D/13G)"
    override val f13Hinweis = "Wer mehr als 5 % einer Firma hält, meldet das der SEC.\n• 13D: aktiv, der Halter will Einfluss nehmen, etwa auf den Vorstand oder einen Verkauf der Firma\n• 13G: passiv, die Aktien sind nur Anlage, wie bei großen Fondsgesellschaften\n\nGezeigt wird je Halter die jüngste Meldung."
    override val f13Aktiv = "13D, aktiv"
    override val f13Passiv = "13G, passiv"
    override val omTitel = "Großaktionäre handeln"
    override val omHinweis = "Muscat Clearing & Depository zeigt die Käufe und Verkäufe von Haltern ab 5 % am letzten Handelstag, also großer Aktionäre. Gezeigt wird der Tag des Abrufs."
    override fun omAnteil(p: String) = if (p.isBlank()) "Großaktionär" else "Anteil danach $p %"
    override val saTitel = "Quellen zum Selbstabrufen"
    override val saUntertitel = "Wo es die Dateien gibt und wie du sie holst"
    override val saEinleitung = "Die Daten bleiben auf dem Gerät."
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
        "bund" -> "Netto-Leerverkaufspositionen in deutschen Aktien ab 0,5 % des Grundkapitals (CSV-Datei)."
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
    override fun saRegion(code: String) = when (code) {
        "AM" -> "Amerika"
        "EU" -> "Europa"
        "AS" -> "Asien und Ozeanien"
        "AF" -> "Naher Osten und Afrika"
        else -> "Märkte und Rohstoffe"
    }
    override fun saTyp(typ: String) = when (typ) {
        "import" -> "Import"
        "index" -> "Index"
        else -> "Ansehen"
    }

    // 5.91 Profil
    override val erwUntertitel = "Kursabruf, Zinssatz, Registerdaten, Export"
    override val erwHandel = "Handelskosten"
    override val erwKurse = "Kursabruf"
    override val erwZinsAuto = "Gerade folgt der Zins dem Leitzins der EZB. Wählst du hier einen Satz, gilt ab dann dein eigener."
    override val erwRegister = "Registerdaten"
    override val erwPp = "Portfolio Performance"

    // 5.91 Wertpapierseite
    override val bwAlleKennzahlen = "Alle Kennzahlen"
    override val bwTer = "Laufende Kosten (TER)"
    override val bwPreisalarm = "Mitteilungen"
    override fun bwPreisalarmZahl(n: Int) = "Mitteilungen · $n"
    override val bwOffeneOrders = "Offene Orders"
    override val bwFuerUndWider = "Chancen und Risiken"
    override val bwHintergrund = "Hintergrund"
    override fun bwHintergrundUnter(themen: List<String>) = (if (themen.size == 1) "1 Thema: " else "${themen.size} Themen: ") + themen.joinToString(", ")
    override fun bwThema(key: String) = when (key) {
        "OPTIONEN" -> "Optionen"; "TRENDS" -> "Trends"; "HANDELNDE" -> "Wer handelt"; "FUEHRUNG" -> "Insider"
        "LEER" -> "Leerverkäufe"; "FDA" -> "FDA"; "BEHOERDEN" -> "Politik und Behörden"; "LOBBY" -> "Lobbyarbeit Europa"
        "HALTER" -> "Große Halter"; "STRUKTUR" -> "Eigentümer und Rechte"; "STUDIEN" -> "Klinische Studien"; "FAHRZEUGE" -> "Fahrzeugrückrufe"; "PRODUKTE" -> "Produktrückrufe"; "BESCHWERDEN" -> "Verbraucherbeschwerden"
        "ACHTK_WARN" -> "Warnzeichen"; "ACHTK_DEAL" -> "Übernahmen"
        "FINANZIERUNG" -> "Finanzierungssatz"; "KETTE" -> "Gebundenes Kapital"
        else -> key
    }
    override val bwRrUnter = "Größter Rückgang, Dauer bis zur Erholung, Rendite"
    override val bwGesetze = "Gesetze"

    // 5.91 Startseite
    override val maerkteTitel = "Märkte"
    override val maerkteBewegungen = "Bewegungen"
    override val handeln = "Handeln"
    override val zeitraumMenue = "Eigener Zeitraum und Handelskalender"

    // 5.92 Datenquellen
    override fun awHaeufTeile(monat: String?, anzahl: String, ueberMillion: String) = listOfNotNull(monat?.let { "Stärkster Monat $it ($anzahl)" }, "$ueberMillion Geschäfte über 1 Mio. $")
    override fun bkFairerWertKurz(preis: String, abstand: String, darueber: Boolean) = "Fairer Wert $preis, Kurs $abstand ${if (darueber) "darüber" else "darunter"}"
    override val dqTitel = "Datenquellen"
    override val chartBeschreibung = "Kurschart"
    override val dqEinleitung = "• Grün: antwortet\n" +
        "• Gelb: zuletzt ein Fehler, die zuletzt geladenen Daten bleiben sichtbar\n" +
        "• Rot: antwortet seit Stunden nicht\n" +
        "• Grau: noch nicht genutzt oder abgeschaltet"
    override fun dqName(id: String) = when (id) {
        "investorenbriefe" -> "Briefe von Fondsmanagern"
        "yahoo" -> "Yahoo Finance (Kurse, Kennzahlen, News)"
        "sec" -> "SEC (US-Meldungen)"
        "gleif" -> "GLEIF (Konzernstruktur)"
        "ezb" -> "EZB (Zinsen, Wechselkurse)"
        "eurat" -> "Rat der EU (Steuergebiete)"
        "wikipedia" -> "Wikipedia und Wikidata"
        "gdelt" -> "GDELT (Berichtsdichte, Ton der Berichte)"
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
        "kursrueckfall" -> "Massive, Tiingo, Twelve Data (US-Kurse als Rückfall)"
        "github" -> "GitHub (neue Fassungen, Quellen-Stand)"
        "strom" -> "Strom Europa (ENTSO-E, SMARD)"
        "fruehsignale" -> "Lager und Wirtschaftsdaten (EIA, TSA, Dürre, GIE)"
        "pcaob" -> "PCAOB (Wirtschaftsprüfer)"
        "epa" -> "EPA ECHO (Umweltstrafen)"
        "emissionshandel" -> "EU-Emissionshandel (Unionsregister)"
        "staatsgeld" -> "Staatsgeld (TED, Contracts Finder, EU-Haushalt)"
        "osha" -> "OSHA (Arbeitsschutzstrafen, Arbeitsministerium)"
        else -> id
    }
    override val dqQuellen = "Quellen"
    override val dqLizenzLabel = "Lizenz"
    override val dqBedingungenLabel = "Bedingungen"
    override val dqAusfallLabel = "Bei Ausfall"
    override fun dqLizenz(l: de.troetenrepublik.app.engine.Quellenbuendel.Lizenz) = when (l) {
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.GEMEINFREI -> "Gemeinfrei"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.QUELLENANGABE -> "Nutzung mit Quellenangabe"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.CC_BY_SA_CC0 -> "CC BY-SA 4.0, Wikidata CC0"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.ANBIETER -> "Nutzung nach Bedingungen des Anbieters"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.GEMISCHT -> "Je Anbieter eigene Bedingungen"
        de.troetenrepublik.app.engine.Quellenbuendel.Lizenz.NUTZER -> "Eigener Schlüssel, Bedingungen des Anbieters"
    }
    override fun dqAusfall(ersatz: List<String>, schluessel: Boolean) = when {
        ersatz.isEmpty() -> "Letzter gespeicherter Stand"
        schluessel -> "${ersatz.joinToString(", ")} (eigener Schlüssel), sonst letzter gespeicherter Stand"
        else -> "${ersatz.joinToString(", ")}, sonst letzter gespeicherter Stand"
    }
    override val quellenAnhang = "Quellen"
    override val quelleCboe = "© Cboe Exchange, Inc. (VIX® ist eine eingetragene Marke)"
    override val quelleVixYahoo = "Quelle: Cboe, über Yahoo Finance"
    override val quelleIwfPortWatch = "IWF PortWatch (CC BY 4.0)"
    override val quelleWeltbank = "Weltbank (CC BY 4.0)"
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
    override val dqStandLabel = "Datenbündel, Stand"
    override val dqQuellenStandLabel = "Quellenliste, Stand"
    override val dqQuellenGesucht = "Quellenliste, zuletzt gesucht"
    override val dqDatenKopf = "Gepflegte Daten"
    override val dqDatenEingebaut = "eingebaut"
    override fun dqDatenNichtGepflegt(marke: String) = "Das Datenbündel wird nicht mehr gepflegt, die App bleibt auf dem Stand $marke."
    override val dqInhalteKopf = "Zuletzt gültig gelesen"
    override fun dqInhalt(name: String) = when (name) {
        "wahlen" -> "Wahlen"
        "kreditratings" -> "Kreditratings"
        "gdacs" -> "Katastrophen"
        "us-reisehinweise" -> "Reisehinweise USA"
        "aa-reisewarnungen" -> "Reisewarnungen Deutschland"
        "opensanctions" -> "Sanktionen"
        "datenbuendel" -> "Datenbündel"
        "bafin" -> "BaFin Eigengeschäfte"
        "sec" -> "SEC Insidergeschäfte"
        "insider-se" -> "Insider Schweden"
        "insider-fr" -> "Insider Frankreich"
        "insider-nl" -> "Insider Niederlande"
        "insider-ch" -> "Insider Schweiz"
        else -> name
    }
    override fun dqStand(version: Long, geladen: Boolean) = if (geladen) "$version (nachgeladen)" else "$version (eingebaut)"
    override val dqGesucht = "Datenbündel, zuletzt gesucht"
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
        "FASSUNG" -> "braucht eine neuere Fassung der App"
        "ABGELAUFEN" -> "nicht mehr gepflegt"
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
    override val zgZustandNein = "Nicht gesetzt"
    override val zgZustandJa = "Aktiv"
    override val zgZustandFehler = "Fehler"
    override val zgIntroPunkte = listOf(
        "Schlüssel bleiben auf diesem Gerät und gehen nur an den jeweiligen Anbieter.",
    )
    override fun zgZweck(dienst: String) = when (dienst) {
        "guardian" -> "Wirtschafts- und Börsennachrichten im Reiter „The Guardian“, mit Artikeltext."
        "finnhub" -> "Marktnachrichten im Reiter „Finnhub“: Märkte, Übernahmen, Devisen und Krypto."
        "alphavantage" -> "Marktnachrichten im Reiter „Alpha Vantage“."
        "fec" -> "Wahlkampfspenden des Komitees (PAC) einer Firma auf der Seite einer US-Aktie."
        "dol" -> "Strafen aus Arbeitsschutz-Inspektionen (OSHA) der letzten fünf Jahre bei Rechtsstreit und Strafen auf der Seite einer US-Aktie."
        "uspto" -> "Erteilte Patente je Jahr, Technikfelder und Patente je Forschungsdollar auf der Seite einer Aktie."
        "finra" -> "Leerverkäufe und Dark-Pool-Handel auf der Seite einer US-Aktie."
        "reddit" -> "Neue Reddit-Beiträge zu einer Aktie mit Stimmung und eine eigene Rangliste unter Trends."
        "sec_kontakt" -> "Neue US-Insidermeldungen direkt von der SEC und Fondsberichte für „Was Investoren schreiben“."
        "poly_adresse" -> "Deine echten Positionen bei Polymarket unter „Meine Wetten“."
        "eodhd" -> "Nachrichten zur geöffneten Aktie auf ihrer Seite."
        else -> "Übersetzt Prognosemärkte und fremdsprachige Beschreibungen von Werten."
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
        "dol" -> listOf(
            "Schlüssel nach Anmeldung auf dataportal.dol.gov",
            "Nur für dich, nicht weitergeben",
            "Die App fragt höchstens 10 Mal je Minute und 400 Mal am Tag",
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
            "Vorbelegt ist eine zufällig erzeugte Adresse, die niemandem gehört",
            "Eine eigene Adresse geht nur an die SEC",
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
            "Übertragen werden nur Marktinhalte und Beschreibungstexte, keine Daten von dir",
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
        "lme" -> "Metalllager der LME"
        "comex" -> "Metalllager der COMEX"
        "fbx" -> "Freightos Baltic Index (FBX)"
        "baltic" -> "Baltic Dry Index"
        "finra" -> "FINRA: Margin-Statistik"
        else -> id
    }
    override fun saAnsehenInhalt(id: String) = when (id) {
        "bh" -> "Mitteilungen der Firmen, darunter Geschäfte von Insidern."
        "jm" -> "Mitteilungen über Käufe und Verkäufe von Direktoren und verbundenen Personen."
        "tt" -> "Meldungen über Geschäfte von Insidern unter den Mitteilungen der Firmen."
        "il" -> "Pflichtmeldungen israelischer Firmen, auch zu Beständen von Großaktionären und Führungskräften. Meist auf Hebräisch."
        "id" -> "Meldungen über Aktienbesitz von Direktoren und Großaktionären, meist auf Indonesisch."
        "jp" -> "Meldungen über Beteiligungen ab 5 % und Berichte der Firmen. Die Schnittstelle braucht einen kostenlosen Schlüssel."
        "kr" -> "Meldungen über Bestände von Führungskräften und Großaktionären. Die Schnittstelle Open DART braucht einen kostenlosen Schlüssel."
        "pt", "hr" -> "Geschäfte von Führungskräften nach der EU-Marktmissbrauchsverordnung."
        "jefs" -> "Jährliche Finanzberichte der US-Bundesrichter mit Anlagen und Geschäften. Jeder Bericht erst nach Zustimmung zu den Bedingungen."
        "ep" -> "Erklärungen zu privaten Interessen, als PDF auf der Seite jeder Person."
        "aph" -> "Register der Interessen der Abgeordneten mit Aktienbesitz, als PDF je Person."
        "oge" -> "Vermögensmeldungen hoher Regierungsmitarbeiter. Manche Berichte gibt die OGE nur auf Antrag mit Formular 201 heraus."
        "lme" -> "Monatsdatei der Lagerbestände. Die geladene Datei liest die App unter „Selbst anstoßen“ ein."
        "comex" -> "Berichte der Lagerbestände. Die geladene Datei liest die App unter „Selbst anstoßen“ ein."
        "fbx" -> "Frachtraten für Container."
        "baltic" -> "Frachtraten für Schüttgut."
        "finra" -> "Wertpapierkredite in den USA je Monat."
        else -> ""
    }

    // 5.92 P1 Wertpapierseite oben
    override val p1BilanzLaedt = "Trefferbilanz wird berechnet …"
    override val p1BilanzWenig = "Zu wenige Signale für eine Trefferbilanz"
    override fun indForschung(art: String): String = when (art) {
        "sma50", "sma200", "kreuz" -> "Wirkte im Dow Jones bis 1986, auf neueren Daten kaum. Als Filter senkt der Durchschnitt vor allem Rückgänge, kaum die Rendite (Brock u. a. 1992, Fang u. a. 2014, Faber 2007)."
        "bb" -> "Bandsignale schlugen Kaufen und Halten nach Kosten nicht verlässlich. Die Bandbreite zeigt vor allem die aktuelle Schwankung (Lento u. a. 2007)."
        "ichimoku" -> "Nur wenige Studien. Mit Standardwerten zeitweise profitabel, bei Devisen gar nicht. Schwache Belege (Deng u. a. 2021)."
        "sar" -> "Kaum begutachtete Studien. Frühe Gewinne ähnlicher Trendsysteme bei Futures verschwanden nach 1985. Sehr schwache Belege (Park und Irwin 2007)."
        "rsi" -> "Gewinne nur in einzelnen Märkten ohne Korrektur für Mehrfachtests. Breite Tests finden nach Kosten keinen Vorteil (Chong und Ng 2008, Rink 2023)."
        "stoch" -> "Fast nur Studien aus der Praxis. Keine belastbaren Belege für Vorhersagekraft nach Kosten (Chong u. a. 2017)."
        "macd" -> "Eine Trendregel wie die Kreuzung zweier Durchschnitte. Frühe Vorteile verschwinden meist auf neuen Daten und nach Kosten (Chong und Ng 2008, Rink 2023)."
        "atr" -> "Auf hohe Schwankung folgt oft weitere hohe Schwankung. Als Risikomaß gut belegt, für die Kursrichtung ohne Aussage (Engle 1982, Mandelbrot 1963)."
        "obv" -> "Wenige Studien, etwa für Großchina und als Monatssignal für US-Indizes. Für Einzelaktien nicht belastbar belegt (Tsang und Chong 2009, Neely u. a. 2014)."
        "kerzen" -> "Für einzelne US-Aktien der 1990er fanden sich bei manchen Umkehrformationen Hinweise, für den Dow Jones 1992 bis 2002 nach Berücksichtigung des Zufalls kein Nutzen (Caginalp und Laurent 1998, Marshall u. a. 2006)."
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
            "kreuz" -> "Der Ø 50 Tage kreuzt den Ø 200 Tage nach oben (Golden Cross, steigend) oder nach unten (Death Cross, fallend)."
            "bb" -> "Der Kurs fällt unter das untere Band (Erholung erwartet) oder steigt über das obere (Rücksetzer erwartet)."
            "rsi" -> "Der RSI steigt wieder über 30 (steigend) oder fällt wieder unter 70 (fallend)."
            "stoch" -> "Die Stochastik steigt wieder über 20 (steigend) oder fällt wieder unter 80 (fallend)."
            "macd" -> "Die MACD-Linie kreuzt ihre Signallinie nach oben (steigend) oder nach unten (fallend)."
            "ichimoku" -> "Der Kurs verlässt die Wolke nach oben (steigend) oder nach unten (fallend)."
            "sar" -> "Die Punkte wechseln unter den Kurs (steigend) oder über den Kurs (fallend)."
            "obv" -> "Das OBV kreuzt seinen Durchschnitt über 20 Tage nach oben (steigend) oder nach unten (fallend)."
            "kerzen" -> "Eine Formation mit Richtung erscheint: eine der sechs nach einem Rückgang (steigend) oder eine der sechs nach einem Anstieg (fallend). Doji zählen nicht."
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
    override val pcDarstellung = "Darstellung"
    override val pcEinblenden = "Einblenden"
    override val pcZeichnen = "Zeichnen"
    override val pcOhlc = listOf("Anfang", "Hoch", "Tief", "Ende")
    override val pcEinstieg = "gut für den Einstieg"
    override fun pcWerkzeug(key: String) = if (key == "waagerecht") "Waagerechte Linie" else chartWerkzeug(key)

    // 5.92 P2 Wertpapierseite Analyse
    override val bkTitel = "Bewertung und Kennzahlen"
    override val bkBewertung = "Bewertung"
    override val bkWachstum = "Finanzen"
    override fun bkBewertungSatz(noetig: String, art: Int, bisher: String?) = when (art) {
        1 -> "Der heutige Preis setzt voraus, dass der Gewinn 10 Jahre lang um mehr als $noetig pro Jahr wächst."
        -1 -> "Der heutige Preis setzt ein Gewinnwachstum von weniger als $noetig pro Jahr über 10 Jahre voraus."
        2 -> "Der heutige Preis ist auch erreicht, wenn der Gewinn 10 Jahre lang um $noetig pro Jahr schrumpft."
        else -> "Der heutige Preis setzt voraus, dass der Gewinn 10 Jahre lang um $noetig pro Jahr wächst."
    } + (bisher?.let { " Bisher: $it pro Jahr." } ?: "")
    override fun bkFairerWert(preis: String, wachstum: String, abstand: String, darueber: Boolean, zielKgv: String) =
        "Fairer Wert bei $wachstum Wachstum pro Jahr, KGV $zielKgv nach 10 Jahren und 8 % Rendite: $preis. Der Kurs liegt $abstand ${if (darueber) "darüber" else "darunter"}."
    override val bkBewertungVerlust = "Die Firma macht derzeit keinen Gewinn. Ohne Gewinn lässt sich kein fairer Wert rechnen."
    override fun bkBilanzSatz(ampel: Int, gruende: List<String>) =
        listOf("Solide Bilanz", "Gemischte Bilanz", "Schwache Bilanz").getOrElse(ampel) { "" } + if (gruende.isEmpty()) "" else ": " + gruende.joinToString(", ")
    override fun bkGrund(key: String) = when (key) {
        "PROFITABEL" -> "profitabel"; "VERLUST" -> "macht Verlust"; "SCHULDEN_SINKEN" -> "Schulden sinken"; "SCHULDEN_STEIGEN" -> "Schulden steigen"
        "GEWINNE_STEIGEN" -> "Gewinne steigen"; "GEWINNE_SINKEN" -> "Gewinne sinken"; "GELD_FLIESST" -> "verdient Geld im Tagesgeschäft"
        "GELD_FEHLT" -> "verbraucht Geld im Tagesgeschäft"; "PLEITE_RISIKO" -> "erhöhtes Pleiterisiko"; "NEUE_AKTIEN" -> "gibt neue Aktien aus"
        else -> key
    }
    override val bkGewinn = "Gewinn"
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
    override val bkZielMedian = "Kursziel (Median)"
    override val bkGewinnErwartet = "Gewinn je Aktie erwartet"
    override val bkGewinnWachstumJahr = "Gewinnwachstum dieses Jahr"
    override val bkGewinnWachstumFolge = "Gewinnwachstum nächstes Jahr"
    override val bkUmsatzWachstumFolge = "Umsatzwachstum nächstes Jahr"
    override val bkNaechsteZahlen = "Nächste Geschäftszahlen"
    override val bkJahresVeraenderung = "Kursänderung in 12 Monaten"
    override val anLaedt = "Einschätzungen werden geladen …"
    override val anKeineDaten = "Keine Einschätzungen gefunden."
    override val anVerteilung = "Verteilung"
    override fun anVergleich(diff: Double) = when {
        diff > 0.1 -> "Freundlicher als vor drei Monaten."
        diff < -0.1 -> "Skeptischer als vor drei Monaten."
        else -> "Ungefähr wie vor drei Monaten."
    }
    override val anNurUs = "Einzelne Häuser und ihre Trefferbilanz führt die Quelle nur bei US-Aktien."
    override val anBereinigt = "Analystenurteile, bereinigt"
    override fun anAlleHaeuser(n: Int) = if (n == 1) "1 Haus" else "Alle $n Häuser"
    override val anGewichtet = "Nach Trefferanteil gewichtet"
    override val anSpalteGewichtet = "Gewichtet"
    override val anStufeZeile = "Stufe"
    override val anMittelwert = "Mittelwert"
    override val anTrefferZeile = "Trefferquote"
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
            "• Der Trefferanteil misst nur vergangene Urteile.\n" +
            "• Keine Anlageberatung."
    override val anHaeuser = "Häuser"
    override fun anStufe(s: Int) = when (s) {
        2 -> "Stark kaufen"; 1 -> "Kaufen"; 0 -> "Halten"; -1 -> "Verkaufen"; else -> "Stark verkaufen"
    }
    override fun anKursziel(preis: String) = "Kursziel $preis"
    override val anVeraltet = "älter als ein Jahr"
    override fun anZuWenige(n: Int) = if (n == 0) "Noch kein Urteil auswertbar" else "Erst $n ${if (n == 1) "Urteil" else "Urteile"} auswertbar"
    override fun anTreffer(je: List<String>, bereinigt: String) = "Treffer nach 3, 6, 12 Monaten: ${je.joinToString(" · ")} · bereinigt $bereinigt"
    override val anWiderspruch = "Einstufung und Kursziel passen nicht zusammen"
    override val crInfo = "Wie der Saldo rechnet"
    override fun crLeer(anteil: String, trend: Int?, us: Boolean) =
        (if (us) "$anteil des Streubesitzes leer verkauft" else "$anteil des Grundkapitals in gemeldeten Leerverkäufen") +
            when (trend) { 0 -> ", steigend"; 1 -> ", fallend"; 2 -> ", gleichbleibend"; else -> "" }
    override val crOptionen = "Optionshandel"
    override fun crOptionenWert(long: String) = "Anteil Calls am Optionsumsatz: $long"
    override fun evErwartet(eps: String?, umsatz: String?) = "erwartet: " + listOfNotNull(eps?.let { "Gewinn je Aktie $it" }, umsatz?.let { "Umsatz $it" }).joinToString(", ")
    override val evLetzteZahlen = "Letzte Geschäftszahlen"
    override fun evQuartal(ende: String) = "Quartal bis $ende"
    override val rrErholung = "Erholung dauerte"
    override val rrNochNicht = "noch nicht erreicht"
    override fun rrVonBis(von: String, bis: String) = "$von bis $bis"
    override val wzRueckblick = "Rückblick und Vergleich"
    override val rvKnopf = "Rückblick"
    override val wzRueckblickUnter = "Was aus einem Betrag oder einem Sparplan geworden wäre"
    override val wzEinmal = "Einmal"

    // 5.92 Maerkte
    override val maerkteAusblick = "Ausblick"
    override val infoErklaerung = "Erklärung"
    override val mpVomHochKurz = "vom Hoch"
    override val cotInfo = "Die US-Terminmarktaufsicht CFTC veröffentlicht jeden Freitag, wie große Marktteilnehmer an den US-Terminbörsen positioniert sind, mit Stand vom Dienstag.\n\n" +
        "• Gezeigt ist die Nettoposition großer Spekulanten wie Hedgefonds\n" +
        "• Der Balken zeigt, wo sie im Vergleich zu den letzten drei Jahren steht: ganz rechts so optimistisch wie nie, ganz links so pessimistisch wie nie\n" +
        "• Die graue Zeile vergleicht mit allen Wochen seit Beginn der Meldungen (je Markt frühestens 1986). Netto long: mehr Wetten auf steigende als auf fallende Preise, netto short umgekehrt\n\n" +
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
    override val sbSorgenMehr = "Wie oft die englischen Wikipedia-Artikel über Rezession und Börsencrash gelesen werden, verglichen mit dem letzten Jahr. Viel Lesen heißt viel Sorge und damit ein niedriger Wert."
    override val trZumEtf = "Zum ETF"
    override val trZumWert = "Zum Wert"
    override fun trKursLese(zeit: String, kurs: String, erwaehnungen: Int) = "$zeit · $kurs · " + (if (erwaehnungen == 1) "1 Erwähnung" else "$erwaehnungen Erwähnungen")
    override val rotTitel = "Sektorrotation"
    override val rotInfo = "Sektorrotation heißt: Anleger schichten Geld von einer Branche in eine andere um.\n\n" +
        "• Verglichen wird, wie jede Branche im letzten Monat gegenüber dem Schnitt aller lief, und ob sie sich gegenüber den zwei Monaten davor verbessert hat\n" +
        "• Wo es deutlich besser läuft, fließt Geld hinein, wo es deutlich schlechter läuft, heraus\n" +
        "• Der Grund ist eine Faustregel aus der Art der Branchen, dem VIX und den Zinsen\n\n" +
        "Gerechnet wird mit der Kursentwicklung der Branchen-ETFs aus der Liste."
    override val rotKeine = "Gerade keine deutliche Umschichtung zwischen den Branchen."
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
    override val zeTitel = "Fed Funds laut Futures"
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
    override val blTitel = "Blasen-Detektor"
    override val blInfo = "Blasen erkennt man meist erst hinterher.\n\nDiese Messgrößen standen vor früheren Übertreibungen hoch, etwa im Jahr 2000 und 2021. Keine sagt voraus, wann Kurse fallen.\n\n" +
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
    override fun blFarben(rot: Int, gelb: Int, gruen: Int) = "$rot Rot · $gelb Gelb · $gruen Grün"
    override fun blGezaehlt(n: Int) = "Gezählt werden nur diese $n Blasenzeichen. Branchen, Lager und Wirtschaftsdaten und die Frühwarnzeichen zählen nicht mit."
    override fun blGruppe(titel: String, rot: Int, n: Int) = "$titel · $rot von $n rot"
    override val engUebersichtTitel = "Engpässe im Seehandel"
    override fun engUebersichtUnter(datum: String) = "Durchfahrten gegen Vorjahr, Stand $datum"
    override val engAlleGlobus = "Alle Engpässe auf dem Globus"
    override fun blSektor(jahr: String, zu200: String) = "$jahr in 12 Monaten · $zu200 zur 200-Tage-Linie"
    override fun blName(schluessel: String) = when (schluessel) {
        "kgv" -> "KGV des S&P 500"
        "buffett" -> "Buffett-Indikator"
        "trend" -> "Überdehnung über der 200-Tage-Linie"
        "konzentration" -> "Gewicht der 10 Größten"
        "ipo" -> "Börsengänge"
        "stimmung" -> "Fear & Greed Aktien"
        "erwaehnung" -> "Gerede über Blasen"
        "gewinn" -> "Erwartetes Gewinnwachstum"
        "praemie" -> "Renditeabstand zu Anleihen"
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
    override fun blkKern(schluessel: String) = when (schluessel) {
        "cape" -> "Kurs des S&P 500 geteilt durch den inflationsbereinigten Durchschnittsgewinn der letzten zehn Jahre, berechnet von Robert Shiller."
        "margin" -> "Wachstum der Kredite, die US-Wertpapierhändler ihren Kunden vor allem für Aktienkäufe geben, gegen das Vorjahr, aus der Finanzierungsrechnung der Fed."
        "kgv" -> "Kurs des S&P 500 geteilt durch den Gewinn der letzten zwölf Monate, gemessen über den ETF SPY."
        "buffett" -> "Wert aller US-Aktien (Wilshire 5000) im Verhältnis zur Wirtschaftsleistung der USA."
        "trend" -> "Abstand des S&P 500 zu seinem Schnitt der letzten 200 Handelstage."
        "konzentration" -> "Anteil der zehn größten Aktien am Börsenwert aller S&P-500-Werte im Katalog."
        "ipo" -> "Kursplus des Renaissance-IPO-ETF in zwölf Monaten, der Aktien nach ihrem Börsengang hält."
        "stimmung" -> "Fear & Greed von CNN, sonst der eigene Index der App: 0 heißt extreme Angst, 100 extreme Gier."
        "erwaehnung" -> "Aufrufe des englischen Wikipedia-Artikels über Wirtschaftsblasen: Rang der letzten sieben Tage im Jahr, 100 heißt so viele wie nie."
        "gewinn" -> "Erwartetes Gewinnwachstum der S&P-500-Werte im Katalog im nächsten Geschäftsjahr, nach den Schätzungen der Analysten bei Yahoo."
        "praemie" -> "Gewinnrendite der Aktien (100 geteilt durch das KGV) minus Rendite zehnjähriger US-Staatsanleihen."
        "breite" -> "Vorsprung des S&P 500 nach Börsenwert (SPY) vor dem gleich gewichteten S&P 500 (RSP) in zwölf Monaten."
        else -> ""
    }
    override fun blkWarum(schluessel: String) = when (schluessel) {
        "cape" -> "Vor den Einbrüchen von 1929 und 2000 stand er über 30. Je höher er stand, desto schwächer liefen Aktien bisher in den folgenden zehn Jahren."
        "margin" -> "Käufe auf Kredit treiben Kurse. Fallen sie, verlangen die Händler Nachschuss, erzwungene Verkäufe verstärken den Rückgang."
        "kgv" -> "Ein hohes KGV heißt: viel künftiges Wachstum ist schon im Kurs. Bleibt es aus, fehlt dem Kurs die Grundlage."
        "buffett" -> "Steigen Aktien viel schneller als die Wirtschaft, eilen die Kurse den Gewinnen voraus. Im Jahr 2000 stand er bei rund 140 %."
        "trend" -> "Ein großer Abstand heißt: die Kurse sind dem langfristigen Trend weit vorausgelaufen."
        "konzentration" -> "Hängt der Index an wenigen Riesen, trifft ein Rückschlag bei ihnen den ganzen Markt. Im Jahr 2000 lag der Anteil bei gut 25 %."
        "ipo" -> "Steigen frische Börsengänge stark, nehmen Anleger viel Risiko auf sich, und Firmen bringen gern neue Aktien an die Börse."
        "stimmung" -> "Bei extremer Gier haben schon viele gekauft, es bleiben wenige neue Käufer."
        "erwaehnung" -> "Lesen viele über Blasen, ist das Thema in der breiten Öffentlichkeit angekommen."
        "gewinn" -> "Kräftig steigende Gewinne können hohe Kurse rechtfertigen. Wenig Wachstum nimmt hohen Bewertungen diese Stütze."
        "praemie" -> "Bringen sichere Anleihen so viel wie die Gewinne der Aktien, hängt der Kurs fast ganz an künftigem Wachstum."
        "breite" -> "Zieht der Index nach Börsenwert dem gleich gewichteten davon, tragen wenige große Werte den Anstieg."
        else -> ""
    }
    override fun blkSchwaeche(schluessel: String) = when (schluessel) {
        "cape" -> "Kann jahrelang rot bleiben, ohne dass Kurse fallen. Über die nächsten ein bis zwei Jahre sagt er wenig."
        "margin" -> "Quartalswerte, sie erscheinen rund zehn Wochen nach Quartalsende. Die Reihe misst breiter als die Margin-Statistik der FINRA."
        "kgv" -> "Folgt dem Gewinnzyklus: brechen Gewinne in einer Rezession ein, steigt das KGV, obwohl die Kurse fallen."
        "buffett" -> "Große US-Konzerne verdienen einen wachsenden Teil im Ausland, der im BIP fehlt. Der Wert steigt deshalb auch ohne Blase."
        "trend" -> "Steht auch nach jeder kräftigen Erholung hoch, etwa einige Monate nach einem Einbruch."
        "konzentration" -> "Misst nur die Verteilung. Verdienen die Größten auch die größten Gewinne, ist ein hoher Anteil keine Überbewertung."
        "ipo" -> "Schwankt stark. Nach einem Einbruch ergibt schon die Erholung ein hohes Plus in zwölf Monaten."
        "stimmung" -> "Wechselt binnen Tagen und kann wochenlang auf Gier stehen. Zeigt die Lage, keinen Zeitpunkt."
        "erwaehnung" -> "Auch ein Absturz oder eine Blase in einem anderen Markt, etwa bei Kryptowerten, treibt die Aufrufe."
        "gewinn" -> "Beruht auf Schätzungen der Analysten. Werte mit Verlust zählen nicht mit."
        "praemie" -> "Die Gewinnrendite enthält keine Inflation, der Anleihezins schon. Bei hoher Inflation sieht der Wert deshalb schlechter aus."
        "breite" -> "Kann lange anhalten, solange die großen Werte auch beim Gewinn vorn liegen."
        else -> ""
    }
    override fun blkSchwellen(gelb: String, rot: String, faellt: Boolean, rotUeber: Boolean) =
        if (faellt) "Gelb unter $gelb, Rot unter $rot" else "Gelb ab $gelb, Rot " + (if (rotUeber) "über" else "ab") + " $rot"
    override val blkSchnitt = "Schnitt"
    override val blkVerlauf = "Verlauf"
    override fun blkStand(datum: String) = "Stand $datum"
    override val blkStaerke = "Stärke"
    override fun blkStaerkeWert(stufe: Int, gemessen: Boolean) = "$stufe von 4, " + if (gemessen) "im Rückblick gemessen" else "von Hand gesetzt"
    override fun blkRang(anteil: String) = "Höher als in $anteil aller Monate seit 1881"
    override val blkKredite = "Kredite insgesamt"
    override val blkBip = "BIP-Stand"
    override fun blkBegriff(schluessel: String) = when (schluessel) {
        "cape" -> "Shiller-CAPE"
        "kgv" -> "KGV"
        "buffett" -> "Buffett-Indikator"
        "margin" -> "Wertpapierkredit"
        "ipo" -> "IPO"
        "konzentration" -> "Konzentrationsrisiko"
        "breite" -> "Gleichgewichtung"
        "praemie" -> "Implizite Aktienrisikoprämie"
        else -> null
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
        "MELDEVERZUG" -> "Mittlere Zahl der Tage vom Geschäft bis zur Meldung, Geschäfte der letzten zwölf Monate."
        else -> "So viele Prozentpunkte liefen die Käufe der letzten zwölf Monate besser als der S&P 500."
    }
    override val rgText2 = "So wird gerechnet:\n" +
        "• Aktienkäufe der letzten zwölf Monate, vom Schlusskurs am Tag des Geschäfts bis zum letzten Schlusskurs\n" +
        "• verglichen mit dem S&P 500 im selben Zeitraum\n" +
        "• gewichtet nach der Mitte der gemeldeten Spanne\n" +
        "• nur Personen mit mindestens drei Käufen mit Kurs"
    override fun rgPunkte(p: String) = "$p Pkt."
    override fun rgAusKaeufen(n: Int) = if (n == 1) "aus 1 Kauf" else "aus $n Käufen"
    override fun rgTreffer(besser: Int, von: Int) = "$besser von $von schlagen den S&P 500"
    override fun rgKaufsumme(betrag: String) = "zusammen etwa $betrag"
    override val kaText2 = "Jede Woche ein Depot aus den Aktien, deren Kauf Abgeordnete und Senatoren im gewählten Zeitraum gemeldet haben.\n" +
        "• gewichtet nach der Mitte der gemeldeten Beträge, ab zehn Aktien höchstens 10 % je Aktie\n" +
        "• meldet dieselbe Person einen Verkauf, sinkt ihr Anteil um die Mitte der Verkaufsspanne\n" +
        "• gekauft zum Schlusskurs des ersten Handelstags nach der Meldung, so wie man es hätte nachbilden können\n" +
        "• ohne Gebühren und Steuern"
    override fun awKaeufeDanach(n: Int) = "Gekaufte Werte danach ($n Käufe)"
    override fun awVerkaeufeDanach(n: Int) = "Verkaufte Werte danach ($n Verkäufe)"
    override val awImPlus = "im Plus"
    override val awBesserAlsMarkt = "besser als S&P 500"
    override val awGefallen = "gefallen"
    override val awSchwaecherAlsMarkt = "schwächer als S&P 500"
    override val awMitte = "Median"
    override fun awUnusualKurz(anzahl: String, schnitt: String) = "$anzahl statt sonst $schnitt"
    override fun awVorAnstieg(n: Int) = "Danach +10 % in zwei Wochen ($n Käufe)"
    override fun awVorRueckgang(n: Int) = "Danach −10 % in zwei Wochen ($n Verkäufe)"
    override fun awMitZufall(anteil: String, zufall: String) = "$anteil (an einem beliebigen Tag: $zufall)"
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
    override val pmGrossGroesste = "Größte Wetten"
    override val pmProfis = "Erfolgreiche Händler"
    override val pmProfisLaedt = "Ranglisten und Wetten werden geladen …"
    override val pmProfisKeine = "Gerade keine auffälligen Wetten erfolgreicher Händler"
    override val pmProfisText = "Wetten von Händlern, die bei Polymarket mit Politik, Wirtschaft oder Finanzen besonders viel gewonnen haben.\n" +
        "• Erfolg zählt nur in diesen Bereichen, Sportwetten nicht\n" +
        "• Gezeigt werden Käufe außerhalb des Sports, die groß sind, auf einen unwahrscheinlichen Ausgang setzen oder für den Händler ungewöhnlich groß sind\n" +
        "• Absicherungen auf beiden Seiten und fast sichere Ausgänge über 85 % zählen nicht\n" +
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
    override val afStart = "Aktien einordnen"
    override val afLaedt = "Kennzahlen werden geladen …"
    override val afDetailsLaedt = "Weitere Kennzahlen für die ersten Werte werden geladen …"
    override fun afDetailsMehr(n: Int) = if (n == 1) "Diese Aktie genauer prüfen" else "Die ersten $n genauer prüfen"
    override val afKeine = "Keine Aktie passt zu diesen Filtern."
    override fun afBereich(b: String) = when (b) {
        "BEWERTUNG" -> "Bewertung"; "QUALITAET" -> "Qualität"; "MARKTMACHT" -> "Moat"; "LAND" -> "Land"; "WELT" -> "Rohstoffbindung"; "ANALYSTEN" -> "Analysten"; "TREND" -> "Kurstrend"
        "INSIDER" -> "Insider"; "LEERVERKAUF" -> "Leerverkäufe"; else -> "Stimmung"
    }
    override fun afEinordnung(e: String) = when (e) {
        "GUENSTIG" -> "niedrig bewertet"; "FAIR" -> "mittel bewertet"; "TEUER" -> "hoch bewertet"; else -> "Bewertung unklar"
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
            "LAND" -> "Landrisiko $wert von 100 Punkten"
            "ROHSTOFFE" -> "Rohstoffbindung der Branche $wert von 100 Punkten (mehr heißt weniger Rohstoffe aus wenigen Ländern)"
            "NOTE" -> "Analystennote $wert (1 = kaufen, 5 = verkaufen)"
            "KURSZIEL" -> "Kursziel der Analysten $wert vom Kurs entfernt"
            "BEREINIGT" -> if (gut) "Treffsichere Analysten eher zuversichtlich" else "Treffsichere Analysten eher skeptisch"
            "JAHR" -> "Kurs in zwölf Monaten $wert"
            "LINIE200" -> "$wert ${if (gut) "über" else "unter"} der 200-Tage-Linie"
            "INSIDER" -> if (gut) "Insider kauften zuletzt mehr, als sie verkauften" else "Insider verkauften zuletzt mehr, als sie kauften"
            "LEER" -> "$wert der Aktien leer verkauft"
            "STELLUNG", "PREIS_BRUTTO", "PREIS_BETRIEB", "BESTAENDIG" -> mmGrund(k, gut, wert, vergleich.orEmpty(), "")
            else -> "Reddit: $wert Erwähnungen, am Vortag ${vergleich ?: "keine"}"
        }
    }
    override val afGewichte = "Gewichte"
    override val afGewichteHinweis = "Land und Rohstoffbindung stehen auf 0 und zählen erst mit mehr Gewicht. Ohne Daten zählt ein Bereich mit 50 Punkten."
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
    override fun afTreffer(n: Int, gesamt: Int) = "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} von ${de.troetenrepublik.app.ui.Fmt.num(gesamt.toDouble(), 0)} Aktien"
    override fun afDatenlage(p: String) = "Daten für $p der Gewichte"
    override val afZurAktie = "Zur Aktie"
    override val afSoRechnet = "So rechnet der Aktienfinder"
    override val afErklaerung = "Jeder Bereich bekommt 0 bis 100 Punkte, 50 ist unauffällig. Die Gesamtpunktzahl ist der Schnitt nach den Gewichten.\n" +
        "• Bewertung: KGV, erwartetes KGV, Kurs-Buchwert, Kurs-Umsatz und EV/EBITDA im Vergleich zur Branche, dazu PEG und das im Kurs steckende Wachstum\n" +
        "• Qualität: Eigenkapitalrendite, erwartetes Gewinnwachstum, Gewinnmarge, Schulden und Bilanz (Piotroski, Altman)\n" +
        "• Moat (Wettbewerbsvorteil): Umsatzanteil an der Branche des Katalogs, Marge gegen die Branche und niedrigste Kapitalrendite der letzten Jahre. Aus Kennzahlen nur genähert und mit der Qualität verwandt, deshalb 15. Ab 62 Punkten hoch, ab 42 mittel\n" +
        "• Land: politisches und strukturelles Risiko des Tätigkeitslands, ohne Angabe des Sitzlands. Grundlage sind die Governance-Indikatoren der Weltbank (Perzentil unter allen Ländern), dazu Abzüge für EU- und US-Sanktionen, OpenSanctions-Listungen, Konflikte im Land oder Angriffe auf das Land (UCDP), US-Reisehinweise und strukturelle Probleme. Der Bereich zählt nur, wenn man ihm bei den Gewichten einen Wert über 0 gibt\n" +
        "• Analysten: Durchschnittsnote von 1 (kaufen) bis 5 (verkaufen), Abstand zum Kursziel und die nach Trefferanteil bereinigte Einschätzung\n" +
        "• Kurstrend: zwölf Monate und Abstand zur 200-Tage-Linie. RSI und Abstand zur 50-Tage-Linie stehen in der aufgeklappten Zeile, der Rückblick-Test hat sie gemessen, sie gehen nicht in die Punkte ein\n" +
        "• Insider: Käufe und Verkäufe der Führung in sechs Monaten\n" +
        "• Leerverkäufe: mehr leer verkaufte Aktien, weniger Punkte\n" +
        "• Stimmung: Erwähnungen auf Reddit, nur US-Aktien\n" +
        "Niedrig bewertet heißt mindestens 62 Punkte in der Bewertung, hoch bewertet höchstens 38.\n\n" +
        "Saldo\n" +
        "Der Saldo steht in der aufgeklappten Zeile und als Sortierung. Er rechnet wie der Saldo auf der Wertpapierseite aus Wachstum, Nettomarge, Bewertung, Kasse und Schulden, Cashflow, Analystenurteil und Kursabstand, ohne Schlagzeilen. Es gibt sie nur für Werte mit geladenem Profil, sie geht nicht in die Punktzahl ein.\n\n" +
        "Grenzen\n" +
        "• Ohne Daten zählt ein Bereich mit 50 Punkten\n" +
        "• Kennzahlen kommen verzögert und können Fehler enthalten\n" +
        "• Kurs-Umsatz, EV/EBITDA, Marge, Schulden und Kursziel kommen aus Einzelabrufen und zählen erst, wenn sie für die Hälfte der Werte vorliegen\n" +
        "• Der Umsatzanteil im Moat zählt erst, wenn die Abschlüsse für 60 % der Katalogwerte der Branche geladen sind. Für Banken und Versicherungen gibt es keine Moat-Punkte\n" +
        "• Das Landrisiko trifft alle Aktien eines Landes gleich, auch solide Firmen\n" +
        "• Die Punkte beruhen auf Vergangenem und auf Erwartungen anderer"
    override val afHinweis = "Keine Anlageberatung. Die Daten können falsch oder veraltet sein."
    override fun afNachbilden(n: Int) = if (n == 1) "Depot aus dieser Aktie nachbilden" else "Depot aus den ersten $n nachbilden"
    override fun afStand(zeit: String) = "Stand $zeit"
    override val afCsv = "Treffer als CSV speichern"
    override val afCsvSpalten = listOf("Symbol","Name","Land","Branche","Punktzahl")
    override fun afCsvFertig(n: Int) = "$n Treffer gespeichert"
    override fun afCsvDatei(name: String) = "tryfolio-$name.csv"
    override val wdTitel = "Bilanz im Rückblick"
    override val wdNav = "Insider-Cluster, Puts, Kongress und Börsengänge gegen den Markt"
    override val wdKeineDaten = "Keine Daten"
    override val wdClusterTitel = "Insider-Cluster-Käufe"
    override fun wdClusterRendite(halten: Int) = "Ø nach $halten Handelstagen"
    override val wdClusterIndex = "S&P 500 zeitgleich"
    override val wdClusterTreffer = "Besser als der S&P 500"
    override val wdClusterMedian = "Median"
    override val wdClusterSchlechtester = "Schlechtester Cluster"
    override val wdClusterZahl = "Cluster"
    override fun wdClusterRegel(mindest: Int, fenster: Int, halten: Int, ab: String) =
        (if (mindest == 1) "Cluster: 1 Führungskraft einer Firma kauft" else "Cluster: mindestens $mindest Führungskräfte derselben Firma kaufen") +
            " binnen $fenster Tagen eigene Aktien, gerechnet ab der Meldung, seit ${ab.split('-').reversed().joinToString(".")}"
    override val wdClusterName = "Cluster"
    override val wdClusterIndexName = "S&P 500"
    override fun wdClusterAchse(halten: Int) = "Handelstage nach dem Kauf, 0 bis $halten"
    override val wdKopfJahr = "Jahr"
    override val wdKopfCluster = "Cluster"
    override val wdKopfClusterRendite = "Ø\nCluster"
    override val wdKopfIndexRendite = "Ø\nS&P 500"
    override val wdKopfBesser = "Besser"
    override fun wdClusterAusgenommen(ohne: Int, offen: Int) = "$ohne ohne Kurse, $offen noch offen"
    override val wdPutTitel = "Verkaufte Puts mit höchster Rendite"
    override val wdPutLaedt = "Protokoll wird angelegt …"
    override val wdPutLeer = "Noch keine Einträge"
    override val wdPutProtokoll = "Protokolliert"
    override val wdPutAbgeschlossen = "Abgelaufen"
    override val wdPutNominal = "Rendite nominal pro Jahr"
    override val wdPutRealisiert = "Rendite realisiert pro Jahr"
    override val wdPutVerlust = "Mit Verlust"
    override val wdPutSchlechtester = "Schlechtester Put"
    override val wdPutNominalKurz = "Nominal"
    override val wdPutRealisiertKurz = "Realisiert"
    override fun wdPutSeit(datum: String, offen: Int) = "Seit $datum, $offen offen"
    override val wdKopfAktie = "Aktie"
    override val wdKopfVerfall = "Verfall"
    override val wdKopfStrike = "Strike"
    override val wdKopfNominal = "Nominal"
    override val wdKopfRealisiert = "Realisiert"
    override val wdPutOffen = "offen"
    override val wdKongressTitel = "Kongress kopieren"
    override val wdKongressAbstand = "Unterschied pro Jahr"
    override val wdKongressRechnen = "Mit heutigen Daten rechnen"
    override val wdIpoTitel = "Börsengänge in den USA"
    override val wdIpoTag1 = "Ø Rendite am ersten Tag"
    override val wdIpoMinus = "Verlust am ersten Tag"
    override val wdIpoDrei = "Ø 3 Jahre gegen Markt"
    override val wdIpoUnterMarkt = "Jahrgänge unter Markt"
    override fun wdIpoRegel(n: Int, von: Int, bis: Int) = "${"%,d".format(java.util.Locale.GERMANY, n)} Börsengänge, $von bis $bis"
    override val wdKopfIpos = "IPOs"
    override val wdKopfTag1 = "Erster\nTag"
    override val wdKopfMinus = "Verlust\nTag 1"
    override val wdKopfDrei = "3 Jahre\nvs. Markt"
    override val wdAlleJahre = "Alle Jahre zeigen"
    override val wdWenigerJahre = "Weniger zeigen"
    override val afWeniger = "Weniger zeigen"
    override fun afMehrZeigen(n: Int) = "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} weitere zeigen"
    override val radStart = "Radar starten"
    override val radNeu = "Neu laden"
    override val radLaedt = "Quellen werden abgefragt …"
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
    override val radBekannt = "schon viel beachtet"
    override fun radArten(n: Int) = if (n == 1) "1 Art von Hinweis" else "$n Arten von Hinweisen"
    override val radSoRechnet = "So arbeitet das Radar"
    override val radErklaerung = "Je Aktie zählen die Hinweise der letzten 90 Tage, neuere wiegen mehr.\n" +
        "• Verschiedene Hinweise beim selben Wert zählen zusammen mehr\n" +
        "• Die zehn meisterwähnten Werte auf Reddit zählen weniger, weil sie schon im Gespräch sind\n" +
        "• Wikipedia-Aufrufe prüft die App nur für die zehn stärksten Kandidaten, Großaktionäre und Optionen für bis zu sechs davon (US-Werte ohne Punkt im Kürzel)\n" +
        "• Insidermeldungen und Käufe aus der Politik werden frischer, wenn du die Insider- und Politikseiten öffnest\n\n" +
        "Grenzen\n" +
        "• Viele Hinweise führen zu nichts: Insider kaufen auch aus Pflicht, Politiker melden mit bis zu 45 Tagen Verspätung, Umsätze steigen auch durch Indexänderungen\n" +
        "• Das Radar sagt nichts darüber, ob ein Kurs steigt"
    override fun radStand(zeit: String) = "Stand $zeit"
    override val radHeute = "heute"

    // 5.92 Fruehindikatoren
    override val fiTitel = "Lager und Wirtschaftsdaten"
    override val fiInfo = "Daten, die früh zeigen können, wie es um Angebot und Nachfrage steht. An den Öllagern sieht man zum Beispiel, ob Rohöl knapper wird als sonst um diese Jahreszeit.\n\n" +
        "Grenzen:\n" +
        "• „Schon eingepreist“ ist eine Schätzung aus der Geschichte, denn Profis werten dieselben Zahlen oft Sekunden nach der Veröffentlichung aus\n" +
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
        "benzin" -> "Benzinvorräte der USA.\n\n• Viel Benzin deutet auf schwache Nachfrage oder großes Angebot, wenig Benzin stützt den Preis.\n• Im Sommer wird mehr gefahren und die Lager sinken üblicherweise, deshalb vergleicht die App mit derselben Jahreszeit."
        "destillat" -> "Diesel und Heizöl, wichtig für Lkw, Landwirtschaft und Heizung im Winter. Knappe Bestände verteuern Diesel oft stärker als Rohöl."
        "gas_us" -> "Arbeitsgas in unterirdischen Speichern der USA, jeden Donnerstag von der EIA.\n\n• Im Sommer wird eingespeichert, im Winter entnommen.\n• Volle Speicher vor dem Winter dämpfen den Preis, leere machen ihn anfällig für Kälte."
        "gas_eu" -> "Füllstand der Gasspeicher in der EU, täglich von GIE AGSI+. Ein niedriger Stand vor dem Winter macht den europäischen Gaspreis (TTF) anfällig. Der läuft oft anders als der US-Preis Henry Hub."
        "erstantraege" -> "Wie viele Menschen in den USA in einer Woche erstmals Arbeitslosenhilfe beantragen, saisonbereinigt, jeden Donnerstag vom US-Arbeitsministerium.\n\n• Steigt der Vierwochenschnitt deutlich über sein Tief, schwächt sich der Arbeitsmarkt ab.\n• Ab 10 % gilt der Anstieg als deutlich, ab 20 % als Warnzeichen."
        "fluggaeste" -> "Reisende an den Sicherheitskontrollen der US-Flughäfen, täglich von der TSA.\n\n• Ein Hinweis auf Reiselust und Konsum, wichtig für Fluglinien und Reiseanbieter.\n• Feiertage wie Ostern oder Thanksgiving fallen jedes Jahr anders und verzerren den Vergleich für kurze Zeit."
        "duerre" -> "Anteil der Fläche der USA ohne Alaska und Hawaii mit mindestens mäßiger Dürre, jede Woche vom U.S. Drought Monitor (National Drought Mitigation Center, USDA und NOAA).\n\n• Dürre in den Anbaugebieten kann Ernten von Mais, Weizen und Soja schmälern.\n• Die Zahl gilt für das ganze Land, nicht nur für Felder."
        else -> ""
    }
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
    override fun afBilanzStand(n: Int, gesamt: Int) = "Bilanzdaten ${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} von ${de.troetenrepublik.app.ui.Fmt.num(gesamt.toDouble(), 0)}"
    override val afNeuOrdnen = "Neu ordnen"
    override val afBilanzWeiter = "Weiter laden"
    override val afBilanzFolgt = "Bilanzdaten folgen"
    override fun afBilanzZahlen(f: String?, zone: String?) =
        listOfNotNull(f?.let { "Piotroski-F-Score $it von 9" }, zone?.let { "Altman-Z: $it" }).joinToString(" · ")
    // 5.92 Ersatzquellen
    override fun ersSenatIntro(stand: String) = "Geschäfte von US-Senatoren.\n• Der STOCK Act (Meldepflicht für Abgeordnete) schreibt die Meldung binnen 45 Tagen vor\n• Der Betrag steht als Spanne\n• Drei Jahre sind eingebaut, neuere Meldungen kommen per Abruf" + (if (stand.isBlank()) "" else "\n• Neueste Meldung vom $stand")
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
        "lieferketten" -> "Global Supply Chain Pressure Index der New York Fed.\n\n• Er fasst Frachtraten für Schiffe und Luftfracht und Umfragen zu Lieferzeiten und Rückständen aus sieben großen Volkswirtschaften zusammen.\n• 0 ist der langjährige Schnitt, 1 eine Standardabweichung darüber, 2021 lag er bei gut 4.\n• Ab 0,5 gelb, ab 1,5 rot."
        "fracht" -> "Frachtindex des US-Verkehrsministeriums (BTS) für Lkw, Bahn, Binnenschiff, Pipeline und Luftfracht, saisonbereinigt.\n\n• Er steht hier für die Frachtindizes, deren Daten nicht frei verwendbar sind.\n• Weniger als im Vorjahresmonat gelb, ab 3 % weniger rot."
        "bahn" -> "Beladene Güterwagen der US-Bahnen im Monat, saisonbereinigt, laut BTS. Die Wochenzahlen des Bahnverbands gibt es nur als PDF. Weniger als im Vorjahresmonat gelb, ab 3 % weniger rot."
        "halbleiter" -> "Produktionsindex der Fed für Halbleiter und elektronische Bauteile in den USA.\n\n• Er steht hier für die weltweiten Umsatzzahlen von SIA und WSTS, die nicht frei verwendbar sind.\n• Weniger als im Vorjahresmonat gelb, ab 3 % weniger rot."
        else -> ""
    }
    override fun ersStand(monat: String) = "Stand: $monat"
    override fun ersVorjahr(wert: String) = "$wert zum Vorjahr"
    override val ersHnTitel = "Hacker News"
    override fun ersHnZeile(woche: Int, schnitt: String) = (if (woche == 1) "1 Beitrag" else "$woche Beiträge") + " in 7 Tagen · vorher $schnitt pro Woche"
    override fun ersMastodonTitel(tag: String) = "Mastodon · #$tag"
    override val ersNetzHinweis = "Gezählt wird der Firmenname. Gleichnamige Wörter zählen mit."
    override fun ersMrdDollar(wert: String) = "$wert Mrd. $"
    override fun ersQuartal(q: Int, jahr: Int) = "$q. Quartal $jahr"
    // 5.92 Eingepreist
    override val epTitel = "Schon eingepreist"
    override fun epTendenz(richtung: Int, was: String?) = when (richtung) {
        1 -> "Tendenz: Preis müsste steigen"
        -1 -> "Tendenz: Preis müsste fallen"
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
    override val epInfo = "„Schon eingepreist“ zeigt, ob der Markt ein Signal schon berücksichtigt hat. Die Tendenz sagt, ob der Preis dafür eher steigen oder eher fallen müsste.\n\n" +
        "• Grün: schon im Preis, keine klare Richtung\n" +
        "• Gelb: Der Markt sieht es deutlich anders als das Signal. Die Tendenz zeigt, wohin sich der Preis bewegen müsste, damit beides wieder zusammenpasst\n" +
        "• Rot: wie Gelb, die Lücke ist aber so groß wie selten\n" +
        "• Grau: Früher gab es keinen verlässlichen Zusammenhang, also keine Tendenz\n\n" +
        "Beispiel Öl: Ist das Lager knapp, kostet Öl zur baldigen Lieferung meist mehr als später. Ist dieser Aufschlag größer, als das Lager erklärt, müsste der Preis fallen. Ist er kleiner, ist die Knappheit noch nicht im Preis und der Preis müsste steigen.\n\n" +
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
            0 -> "Passt: Der Stand der $lager ist schon eingepreist."
            1 -> if (lage == 1) "Die $lager sind voll, der Preis rechnet trotzdem mit Knappheit. Der Preis liegt über dem, was die $lager erklären."
                else "Der Preis rechnet mit mehr Knappheit, als die $lager zeigen. Der Preis liegt über dem, was die $lager erklären."
            else -> if (lage == -1) "Die $lager sind knapp, der Preis zeigt das noch nicht. Der Preis liegt unter dem, was die $lager erklären."
                else "Der Preis rechnet mit mehr Überfluss, als die $lager zeigen. Der Preis liegt unter dem, was die $lager erklären."
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
    override fun epKurve(spanne: String, teurer: Boolean) = "Naher Kontrakt $spanne pro Jahr ${if (teurer) "teurer" else "billiger"} als der vierte"
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
        "• Gelb: Erst ein Teil ist gelaufen. Tendenz in Richtung des Signals: Preis eher steigend nach Käufen und Hochstufungen, eher fallend nach Abstufungen\n" +
        "• Rot: Der Kurs lief gegen das Signal, keine klare Richtung\n" +
        "• Grau: Solche Signale brachten bei diesem Wert früher keinen Vorsprung"
    override val epKaeufeTitel = "Schon eingepreist\nSeit den Käufen"
    override val epKaeufeHinweis = "Gemessen ab dem Tag der Meldung, als die Käufe öffentlich wurden, gegen den S&P 500. Ab etwa 10 % Vorsprung gilt ein Großteil als eingepreist."
    override fun epKaufZeile(datum: String, mehr: String, index: String) = "Gemeldet $datum · $mehr gegen den $index"
    override val epAnalystenTitel = "Schon eingepreist\nLetzte Hoch- oder Abstufung"
    override val epInsiderTitel = "Schon eingepreist\nLetzter Insiderkauf"
    // 5.92 Finder-Test
    override val afRueckblickTitel = "Frühere Ergebnisse des Punktesystems"
    override val afRueckblickAlt = "Mit früherer Gewichtung"
    override fun afRueckblickText(von: String, bis: String, werte: Int, top20: String, msci: String, spx: String, treffer: String) =
        "Rückblick $von bis $bis: Jedes Monatsende bewertete die App den S&P 500 nur mit Daten von damals. " +
            "Die 20 Aktien mit den meisten Punkten brachten in den folgenden zwölf Monaten im Schnitt $top20, der MSCI World $msci, der S&P 500 $spx. Vor dem MSCI World lagen sie an $treffer der Stichtage."
    override fun afRueckblickGrenzen(bilanzAb: String?, ganz: Boolean, ic: Double, icSe: Double, bewertung: Pair<Double, Double>?, abdeckung: String?): String {
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
            "\n• Eine günstige Bewertung ordnete die spätere Rendite in dieser Zeit nicht (Rangkorrelation ${String.format(Locale.GERMANY, "%.2f", b).let { z -> if (z.all { c -> c == '-' || c == '0' || c == ',' || c == '.' }) z.trimStart('-') else z.replace('-', '−') }})."
        } ?: ""
        return "Grenzen\n" + geprueft + bew + "\n" +
            "• Insider, Leerverkäufe und Stimmung fehlen früher und zählten nicht.\n" +
            "• Die Punkte ordnen die spätere Rendite $staerke (Rangkorrelation ${String.format(Locale.GERMANY, "%.2f", ic).let { z -> if (z.all { c -> c == '-' || c == '0' || c == ',' || c == '.' }) z.trimStart('-') else z.replace('-', '−') }}).\n" +
            (abdeckung?.let { "$it\n• Kurse ohne Dividenden." } ?: "• Fehlende Firmen schönen das Ergebnis. Kurse ohne Dividenden.")
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
        "wci" -> "World Container Index von Drewry: Preis für einen 40-Fuß-Container auf acht großen Routen ab Asien, Europa und den USA, jeden Donnerstag neu.\n\n• Starke Sprünge zeigen Engpässe auf See, starke Einbrüche oft eine schwache Nachfrage.\n• Ab 5 % zur Vorwoche gelb, ab 15 % rot."
        "aar" -> "Wochenzahlen des Verbands der US-Bahnen (AAR): beladene Güterwagen sowie Container und Auflieger, verglichen mit derselben Woche im Vorjahr.\n\n• Sie erscheinen jeden Mittwoch und zeigen früher als die Monatszahlen, wie viel Ware unterwegs ist.\n• Weniger als im Vorjahr gelb, ab 3 % weniger rot."
        else -> "Weltweiter Umsatz der Halbleiterhersteller im Monat laut WSTS, der Statistikstelle der Branche, verglichen mit dem Vorjahresmonat.\n\n• Die Zahlen erscheinen etwa zwei Monate später.\n• Weniger als im Vorjahr gelb, ab 3 % weniger rot."
    }
    override fun wqWoche(wert: String) = "Zur Vorwoche $wert"
    override fun wqBahnTeile(wagen: String?, container: String?) =
        listOfNotNull(wagen?.let { "Güterwagen $it" }, container?.let { "Container und Auflieger $it" }).joinToString(" · ")
    override fun wqWocheBis(tag: String) = "Woche bis $tag"
    override fun wqStandTag(tag: String) = "Stand: $tag"
    override fun wqEinheiten(n: String) = "$n Wagen und Container"
    override val wqFbxLink = "Freightos Baltic Index (FBX) im Browser"
    override val wqBalticLink = "Baltic Dry Index im Browser"
    override val wqComexLink = "COMEX-Berichte im Browser laden"
    override val lmeTitel = "Metalllager der LME"
    override fun lmeStand(tag: String) = "Stand $tag"
    override fun lmeTonnen(zahl: String) = "$zahl t"
    override val lmeVormonat = "zum Vormonatsschluss"
    override fun lmeName(kuerzel: String) = when (kuerzel) {
        "CA" -> "Kupfer"; "AH" -> "Aluminium"; "ZS" -> "Zink"; "NI" -> "Nickel"; "PB" -> "Blei"; "SN" -> "Zinn"
        "CO" -> "Kobalt"; "AA" -> "Aluminiumlegierung"; "NA" -> "Aluminiumlegierung Nordamerika"; else -> kuerzel
    }
    override fun lmeVerlauf(monate: Int) = "Schlussbestand je Handelstag, $monate Monate"
    override val lmeBrowser = "LME-Monatsdatei im Browser laden"
    override val lmeEinlesen = "Geladene Datei einlesen"
    override val lmeUnbekannt = "Datei nicht erkannt"
    override val comexTitel = "Metalllager der COMEX"
    override fun comexName(schluessel: String) = when (schluessel) {
        "GOLD" -> "Gold"; "SILBER" -> "Silber"; "KUPFER" -> "Kupfer"; "PLATIN" -> "Platin"; "PALLADIUM" -> "Palladium"
        "ALUMINIUM" -> "Aluminium"; "ZINK" -> "Zink"; "BLEI" -> "Blei"; else -> schluessel
    }
    override fun comexMenge(zahl: String, einheit: String) = zahl + " " + (if (einheit == "OZ") "oz" else if (einheit == "SHT") "sh tn" else "t")
    override val comexVorigerTag = "zum vorigen eingelesenen Tag"
    override val comexLieferbar = "Lieferbar gemeldet"
    override val comexEingelagert = "Eingelagert"
    override fun comexVerlauf(tage: Int) = "Gesamtbestand je eingelesenem Tag, $tage Tage"
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
    override val wqRichterTitel = "US-Bundesrichter"
    override val wqRichterIntro = "Bundesrichter melden einmal im Jahr ihre Anlagen und Geschäfte, die Beträge nur als Spanne. Gezeigt sind die Geschäfte aus den Erklärungen, die CourtListener zuletzt erfasst hat."
    override val wqRichterOhneZugang = "Mit kostenlosem CourtListener-Token unter Zugänge sichtbar"
    override val wqRichterLeer = "In den zuletzt erfassten Erklärungen steht kein Geschäft."
    override val wqRichterLaedt = "Erklärungen werden geladen …"
    override val wqRichterName = "Name folgt beim nächsten Laden"
    override fun wqRichterZeile(jahr: Int, tag: String) = listOfNotNull(if (jahr > 0) "Erklärung $jahr" else null, tag.ifBlank { null }).joinToString(" · ")
    override fun wqSpanne(von: String, bis: String?) = if (bis == null) "über $von" else "$von bis $bis"
    override val wqJefsLink = "Alle Erklärungen bei JEFS im Browser"
    override val wqParlamenteTitel = "Weitere Parlamente"
    override val wqRegisterBrowser = "Weitere Register im Browser"
    override val wqEuLink = "EU-Abgeordnete: Erklärungen über finanzielle Interessen"
    override val wqAusHausLink = "Australien: Register des Repräsentantenhauses"
    override val wqAusSenatLink = "Australien: Interessen der Senatoren"
    override val wqBskyTitel = "Bluesky"
    override fun wqBskyZeile(woche: Int, vorwoche: Int, gekappt: Boolean) =
        (if (gekappt) "Mehr als $woche" else "$woche") + (if (woche == 1 && !gekappt) " Beitrag" else " Beiträge") + " in 7 Tagen · Vorwoche $vorwoche"
    override val wqXLink = "Auf X ansehen"
    override val wqStocktwitsLink = "Auf Stocktwits ansehen"
    override val wqClName = "CourtListener (US-Bundesrichter)"
    override val wqClZweck = "Aktiengeschäfte von US-Bundesrichtern aus ihren jährlichen Finanzerklärungen, unter Copytrading bei Politik, dazu Akten und Urteile der Firma unter Rechtsstreit und Strafen."
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
        else -> zgLimit
    }
    // 5.92 Tendenz-Rueckblick
    override fun trFrueher(rot: Boolean, treffer: String, grund: String, wochen: Int, mittel: String, faelle: Int) =
        "Früher${if (rot) " bei Rot" else ""}: traf in $treffer (ohne Signal $grund), am besten ${trNach(wochen)}, $mittel im Schnitt, $faelle Fälle"
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
    override fun trDetailZeile(wochen: Int, treffer: String, grund: String, mittel: String, faelle: Int, belegt: Boolean) =
        trNach(wochen).replaceFirstChar { it.uppercase() } + ": $treffer Treffer (ohne Signal $grund), $mittel, $faelle Fälle" + if (belegt) ", belegt" else ""
    override fun trGeprueft(von: String, bis: String) = "Geprüft Woche für Woche von $von bis $bis, jeweils nur mit den Daten von damals."
    override fun trPunkte(wert: String) = "$wert Punkte"
    override fun trIdeeTitel(call: Boolean, name: String) = "Übungsidee: ${if (call) "Call" else "Put"} auf $name"
    override fun trIdeeDaten(basis: String, verfall: String, hebel: String) = "Basispreis $basis · Verfall $verfall · Hebel etwa $hebel"
    override fun trIdeeRisiko(zeitwert: String) =
        "Verlieren kann man höchstens den Kaufpreis. Bewegt sich der Kurs nicht, verliert der Schein etwa $zeitwert pro Woche an Zeitwert."
    override val trIdeeHinweis = "Übungsvorschlag aus früheren Zusammenhängen, keine Anlageberatung."
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
        "• „Traf in 61 % (ohne Signal 50 %)“ heißt: In 61 von 100 Fällen lief der Preis in Richtung der Tendenz, an beliebigen Tagen in 50\n" +
        "• Belegt ist eine Tendenz nur mit mindestens 20 Fällen, die sich zeitlich nicht überschneiden, und mit einem Vorsprung, der kaum Zufall sein kann\n" +
        "• „Früher ohne Vorteil“ heißt: Wer der Tendenz folgte, schnitt nicht besser ab. Die Richtung steht dann grau da\n\n" +
        "Märkte bleiben oft lange anders, als die Daten nahelegen.\n\n" +
        "Einen Übungsvorschlag mit Optionsschein zeigt die App nur bei belegter Tendenz, mit einer Laufzeit etwa anderthalbmal so lang wie der Zeitraum, in dem sie traf."

    // 5.92 Investorenbriefe
    override val ibTitel = "Was Investoren schreiben"
    override fun ibRichtung(r: Int) = when {
        r > 0 -> "eher positiv"
        r < 0 -> "eher negativ"
        else -> "neutral"
    }
    override fun ibRichtungKurz(r: Int) = when {
        r > 0 -> "positiv"
        r < 0 -> "negativ"
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
    override val ibSecHinweis = "Ohne Kontaktadresse unter Zugänge sucht die App nicht in Fondsberichten bei der SEC."

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
    override val notifWerkHinweis = "Ab Werk an: Orders, Sparpläne, Preismarken, Fälligkeiten, Dividenden, Zinsen und wichtige Schlagzeilen. Aus sind die Glocken im Copytrading und die Mitteilung zum Tagesrückblick. Beides lässt sich einzeln einschalten."
    // 5.93 strategielabor
    override val slEigen = "Eigener"
    override val slEigenHinweis = "Gerechnet wird nur in diesem Zeitraum, die Kennzahlen der Regeln laufen aber schon vorher an."
    override fun slLotterieErgebnis(pruefBesser: Boolean, echt: Boolean, anzahl: Int, prozent: String) = when {
        pruefBesser && echt -> "Die beste von $anzahl Varianten schlägt auch in der Prüfzeit Kaufen und Halten. Mit $prozent wohl kein Zufall."
        pruefBesser -> "Die beste von $anzahl Varianten schlägt in der Prüfzeit Kaufen und Halten. Mit nur $prozent kann es Zufall sein."
        echt -> "Die beste von $anzahl Varianten hebt sich mit $prozent vom Zufall ab, schlägt aber in der Prüfzeit Kaufen und Halten nicht."
        else -> "Die beste von $anzahl Varianten verliert in der Prüfzeit gegen Kaufen und Halten. Mit $prozent wohl nur Glück."
    }
    // 5.93 hintergrund
    override val rrHintRendite = "Gewinn oder Verlust, auf ein Jahr umgerechnet, ohne Dividenden."
    override val rrHintBeta = "Ausschlag gegen den Index, 1 heißt gleich stark"
    override val rrHintKorrelation = "Zeigt, wie ähnlich sich Wert und Index bewegen. 1 heißt im Gleichschritt, 0 unabhängig, −1 gegenläufig."
    override val lvKurz = "Ein hoher Anteil zeigt, dass viele auf fallende Kurse setzen."
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
    override fun sbWsbUnklarMehr(prozent: Int) = "Nur $prozent % der Kommentare haben einen klaren Ton. Darum gibt es keinen Wert für positiv oder negativ."
    override val sbWsbOffen = "Ohne klaren Ton"
    override val sbVix = "Schwankung (VIX)"
    override fun tdText(richtung: Int, ziel: String) = listOf("Bullish", "Neutral", "Bearish").getOrElse(richtung) { "" } + " für " + when (ziel) {
        "aktien" -> "Aktien"; "aktien_eu" -> "europäische Aktien"; "anleihen" -> "Anleihen"; "oel" -> "den Ölpreis"
        "benzin" -> "den Benzinpreis"; "diesel" -> "den Dieselpreis"; "gas_us" -> "den US-Gaspreis"; "gas_eu" -> "den Gaspreis in Europa"
        "fluglinien" -> "Fluglinien"; "getreide" -> "Getreidepreise"; "gold" -> "Gold"; "chips" -> "Chipwerte"
        "transport" -> "Transportwerte"; "reedereien" -> "Reedereien"; else -> ziel
    }
    override fun sbVixMehr(vix: String) = "Der VIX misst die erwartete Schwankung des S&P 500 und liegt bei $vix. Der Wert 100 heißt: so ruhig wie nie im letzten Jahr. Der Wert 0 heißt: so unruhig wie nie."

    // 5.93 ipo
    override val secIpoTitel = "Neue Meldungen bei der SEC"
    override val secIpoKontakt = "Ohne Kontaktadresse unter Zugänge zeigt die App hier keine neuen Börsenprospekte und Preisfestsetzungen der SEC."
    override val secIpoLeer = "In den letzten Handelstagen gibt es keine passenden Meldungen."
    override val secIpoProspekt = "Prospekt eingereicht"
    override val secIpoAenderung = "Prospekt geändert"
    override val secIpoPreis = "Preis festgesetzt"

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
    override fun erTitel(n: Int) = if (n == 1) "In 1 ETF" else "In $n ETFs"
    override fun erEtikett(tiefe: Int, datum: String) = "Je ETF die $tiefe größten Positionen, Stand $datum"
    override val hlHinweis = "Institute melden ihre Bestände jedes Quartal, in den USA mit Form 13F bis zu 45 Tage nach Quartalsende, die Zahlen sind daher älter als der Kurs."
    override fun zuNaechster(datum: String) = "Nächste Zahlen: $datum"
    // 5.93 maerkte
    // 5.93 Maerkte: Deutungen
    override val korbTitel = "Themen"
    override fun korbName(i: Int) = listOf("Künstliche Intelligenz", "Halbleiter", "Verteidigung", "Weltraum", "Cybersicherheit", "Kernenergie", "Robotik", "Spiele", "Krypto-Aktien", "Abnehmmittel", "Wasserstoff", "Solar")[i]
    override fun korbSteigen(n: Int, m: Int) = "$n von $m steigen"
    override val indexBeitragTitel = "Bewegt den Index"
    override fun indexPunkte(zahl: String) = "$zahl Pkt."
    override val moverRvolFilter = "Relatives Volumen ab 2"
    override fun moverRvol(v: String) = "Relatives Volumen $v"
    override fun moverGrund(i: Int) = listOf("Zahlen", "Analyst", "52-Wochen-Hoch", "52-Wochen-Tief")[i]
    override fun moverZeit(i: Int) = listOf("1T", "1W", "1M", "1J", "3J", "5J")[i]
    override fun nwArt(i: Int) = listOf("Zahlen", "Analyst", "Übernahme", "Kapitalmaßnahme", "Zulassung", "Auftrag", "Sektor")[i]
    override fun nwKursAb(x: Int) = "Kurs ab $x %"
    override val stTitel = "Sektoren"
    override val stKgv = "KGV"
    override val stRendite = "Dividende"
    override val stMarktwert = "Fondsgröße"
    override fun ipoErsterTag(p: String) = "Erster Tag $p"
    // [Ausblick 0.70] Gesamtaussage
    override val agTitel = "Gesamtausblick"
    override val agInfo = "Fasst die Einträge dieses Reiters je Markt zu einer Richtung für Wochen und für Monate zusammen. Die Richtung ist eine Gewichtung der Einträge.\n\n" +
        "• Jeder Eintrag zählt positiv, neutral oder negativ, so wie es in seinem Eintrag steht\n" +
        "• Der Satz unter der Richtung nennt Anteile am Gesamtgewicht\n" +
        "• Uneinheitlich: Beide Seiten haben mindestens ein Viertel des Gewichts, und die Punktzahl bleibt klein\n" +
        "• Frische Daten zählen voll, ältere verlieren bis zu 60 % ihres Gewichts\n" +
        "• Einträge mit derselben Grundlage zählen einmal: Zins-Futures und Zinswetten, Bahn und Halbleiter aus je zwei Quellen\n" +
        "• Wochen: nur Einträge, die sich schnell ändern. Monate: alles, was über einige Monate wirkt\n" +
        "• Branchenindikatoren (Chips, Transport, Fluglinien, Reedereien, Europa) zählen für Aktien mit kleinem Gewicht\n" +
        "• Bei wenigen aktuellen Einträgen steht offen\n" +
        "• Öl, Gas, Anleihen, Gold und Getreide haben eigene Zeilen\n" +
        "• Blasenzeichen zählen nicht mit, sie stehen als eigener Hinweis darunter\n\n" +
        "Die Gewichte sind gesetzt und nicht gemessen. Gewicht und Takt je Quelle:\n"
    override fun agQuelleZeile(name: String, gewicht: String, woechentlich: Boolean) = "• $name: $gewicht, " + if (woechentlich) "wöchentlich" else "monatlich"
    override fun agInfoRest(standard: String) = "• Übrige Einträge: $standard"
    override val agBlasenTitel = "Blasenzeichen auf Rot"
    override fun agBlasenZahl(rot: Int, gesamt: Int) = "$rot von $gesamt"
    override fun agBlasenStand(schlaegtAn: Boolean) = if (schlaegtAn) "schlägt an" else "schlägt nicht an"
    override fun agZustand(schluessel: String, positiv: Boolean) = when (schluessel) {
        "zins" -> if (positiv) "Fed-Senkungen erwartet" else "Fed-Erhöhungen erwartet"
        "fed" -> if (positiv) "Fed-Senkung wahrscheinlich" else "Fed-Erhöhung wahrscheinlich"
        "fed_jahr" -> if (positiv) "Mehrere Fed-Senkungen erwartet" else "Keine Fed-Senkung erwartet"
        "ezb" -> if (positiv) "EZB-Senkung wahrscheinlich" else "EZB-Erhöhung wahrscheinlich"
        "rezession" -> if (positiv) "US-Rezession unwahrscheinlich" else "US-Rezession wahrscheinlich"
        "nyfed" -> if (positiv) "Zinskurve ohne Rezessionswarnung" else "Zinskurve warnt vor US-Rezession"
        "erstantraege" -> if (positiv) "Erstanträge niedrig" else "Erstanträge gestiegen"
        "fluggaeste" -> if (positiv) "Fluggäste über Vorjahr" else "Fluggäste unter Vorjahr"
        "lieferketten" -> if (positiv) "Lieferketten entspannt" else "Lieferketten unter Druck"
        "fracht" -> if (positiv) "Güterverkehr über Vorjahr" else "Güterverkehr unter Vorjahr"
        "bahn", "aar" -> if (positiv) "Bahnfracht über Vorjahr" else "Bahnfracht unter Vorjahr"
        "halbleiter", "wsts" -> if (positiv) "Halbleiterumsatz über Vorjahr" else "Halbleiterumsatz unter Vorjahr"
        "wci" -> if (positiv) "Containerfracht steigt" else "Containerfracht fällt"
        else -> ""
    }
    override val agStaerkeInfo = "Stärke in vier Stufen: Vorsprung der Trefferquote vor der Grundquote im Rückblick der App. Umriss statt Füllung: kein Rückblick vorhanden, die Stufe ist gesetzt."
    override fun agRichtung(urteil: Int) = listOf("Eher aufwärts", "Eher seitwärts", "Eher abwärts", "Uneinheitlich", "Zu wenig Daten").getOrElse(urteil) { "" }
    override fun agZeile(monate: Boolean) = if (monate) "Nächste Monate" else "Nächste Wochen"
    override fun agStaerke(staerke: Int) = "Stärke " + listOf("schwach", "mäßig", "deutlich").getOrElse(staerke) { "" }
    override fun agMarkt(markt: Int) = listOf("Aktien", "Öl", "Gas", "Anleihen", "Gold", "Getreide").getOrElse(markt) { "" }
    override fun agWort(urteil: Int) = listOf("aufwärts", "seitwärts", "abwärts", "gemischt", "offen").getOrElse(urteil) { "" }
    override val agWochen = "Wochen"
    override val agMonate = "Monate"
    override val agWeitere = "Weitere Märkte"
    override val agZuWenig = "Ohne Angabe: zu wenige Einträge"
    override val agWaageTitel = "Richtung für Aktien"
    override fun agPositiv(n: Int) = "$n % positiv"
    override fun agNeutral(n: Int) = "$n % neutral"
    override fun agNegativ(n: Int) = "$n % negativ"
    override val agDafuer = "Stärkste Gründe dafür"
    override val agDagegen = "Stärkste Gründe dagegen"
    override val agHinweis = "Keine Anlageberatung."
    override val agEinzelTitel = "Einzelne Indikatoren"
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
    override fun meErreicht(schwellen: String) = "Schon eingetreten: $schwellen"
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
    override val nwErwaehnt = "Erwähnte Aktien"
    override val ampelGut = "Gut"
    override val ampelAchtung = "Achtung"
    override val ampelKritisch = "Kritisch"
    override val ampelHinweis = "Hinweis"
    override val wlSortMarktwert = "Nach Börsenwert"
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
    override fun ipoReiter(reiter: Int) = listOf("Übersicht", "Demnächst", "Eingereicht", "Neu gelistet", "Zurückgezogen", "Bekannte", "Sperrfristen").getOrElse(reiter) { "" }
    override fun ipoStatusTag(status: Int, datum: String) = listOf("Erwartet", "Prospekt", "Gestartet", "Zurückgezogen").getOrElse(status) { "" } + " " + datum
    override val ipoReiterLeer = "Zurzeit gibt es hier keine Einträge."
    override val ipoEckdaten = "Eckdaten"
    override val ipoPreis = "Preis je Aktie"
    override val ipoAktien = "Angebotene Aktien"
    override val ipoVolumen = "Volumen"
    override val ipoEinschaetzung = "Einschätzung"
    override val ipoRitter = "Studien von Jay Ritter (University of Florida) zeigen, dass Börsengänge über drei bis fünf Jahre im Schnitt schwächer liefen als vergleichbare notierte Aktien."
    override val ipoBrancheTitel = "Branche und Ausblick"
    override fun ipoBranche(b: Int) = listOf("Künstliche Intelligenz", "Software und Technik", "Biotech und Pharma", "Bank", "Energie", "Konsum", "SPAC").getOrElse(b) { "" }
    override fun ipoBrancheText(b: Int) = when (b) {
        0 -> "KI-Firmen wachsen oft sehr schnell und geben viel Geld für Rechenzentren und Chips aus. Es zählt, ob der Umsatz schneller wächst als die Kosten für Rechenleistung, und wie hoch die Bewertung im Verhältnis zum Umsatz ist. Die Erwartungen sind hoch, nach Enttäuschungen fällt der Kurs oft stark."
        1 -> "Es kommt auf Umsatzwachstum, Bruttomarge und die Rule of 40 an. Nach ihr sollten Wachstum und Gewinnmarge zusammen mindestens 40 % ergeben. Wiederkehrende Umsätze aus Abos zählen mehr als einmalige."
        2 -> "Ohne zugelassenes Produkt hängt der Wert an Studienergebnissen und Zulassungen. Es zählt, wie lange das Geld reicht und wann die nächsten Studiendaten kommen. Ein Fehlschlag kann den Kurs halbieren."
        3 -> "Banken bewertet man nach Kurs-Buchwert-Verhältnis und Eigenkapitalrendite, das KGV sagt wenig. Dazu zählen die Qualität der Kredite, eine breite Einlagenbasis und das Zinsrisiko in der Bilanz."
        4 -> "Der Wert hängt an Rohstoffpreisen, Zinsen und Politik. Der Strombedarf von Rechenzentren treibt Netze, Gas und Kernkraft, diese Erwartung steckt oft schon im Preis."
        5 -> "Es kommt auf Marke, Wiederkäufe und Margen an. Wächst die Firma nur mit hohen Werbekosten, fällt der Gewinn später oft enttäuschend aus."
        else -> "Bis zur Übernahme liegt das Geld meist auf einem Treuhandkonto, rund 10 Dollar je Aktie. Danach zählt allein die gekaufte Firma, und die ist beim Börsengang noch unbekannt."
    }
    override val ipoUeber = "Über die Firma"
    override val ipoDiskussion = "Diskussion"
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
    override val ipoKeineEckdaten = "Preis und Umfang stehen noch nicht fest."
    override val ipoEdgarSuche = "Prospekte bei der SEC suchen"
    override fun ipoKatalogGeplant(stand: String) = "Geplant, noch ohne US-Prospekt (Stand $stand)"
    override fun bkBankSatz(kbv: String, roe: String, angemessen: String?) = if (angemessen != null) "Kurs-Buchwert $kbv bei $roe Eigenkapitalrendite. Angemessen wäre etwa $angemessen." else "Kurs-Buchwert $kbv. Die Eigenkapitalrendite von $roe deckt die Kapitalkosten nicht."
    override fun bkBankUnter(kapitalkosten: String, wachstum: String) = "Gerechnet mit $kapitalkosten Kapitalkosten und $wachstum Wachstum"
    override fun bkBankBilanzSatz(roa: String?, ekQuote: String?) = listOfNotNull(roa?.let { "Gesamtkapitalrendite $it" }, ekQuote?.let { "Eigenkapitalquote $it" }).joinToString(", ") + "."
    override val bkBankBilanzUnter = "Gut ab 1 % und 8 %, schwach unter 0,5 % und 5 %"
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
        "UMKEHR" -> "Erholung nach Überverkauf"
        "UEBERVERKAUFT_ZAHLEN" -> "Überverkauft vor Zahlen"
        "HOECHSTSTAENDE" -> "Neue Höchststände"
        "AUSBRUCH" -> "Ausbruch"
        "KREUZUNG" -> "Linienkreuzung"
        "GEWINNWACHSTUM" -> "Hohes Gewinnwachstum"
        "UMSATZWACHSTUM" -> "Hohes Umsatzwachstum"
        "REL_VOLUMEN" -> "Ungewöhnlich hohes Volumen"
        "STETIG" -> "Stetiges Wachstum im Aufwärtstrend"
        "HALTEN" -> "Wachstum zum fairen Preis"
        "DIVIDENDENWACHSTUM" -> "Unterbewertetes Dividendenwachstum"
        "NIEDRIGES_KGV" -> "Niedriges KGV"
        "CANSLIM" -> "CANSLIM (O'Neil)"
        "NEBENWERTE_WACHSTUM" -> "Nebenwerte mit Wachstum"
        "LUECKE_AUF" -> "Kurslücke nach oben"
        "LUECKE_AB" -> "Kurslücke nach unten"
        "NETTOKASSE" -> "Nettokasse"
        "RUECKKAEUFE" -> "Aktienrückkäufe"
        "FCF_RENDITE" -> "Hohe Rendite des freien Cashflows"
        "KGV_ROE" -> "Niedriges KGV, hohe Eigenkapitalrendite"
        "SOLIDE_DIVIDENDE" -> "Solide Dividende"
        "RSI_UEBERKAUFT" -> "Überkauft"
        "RSI_UEBERVERKAUFT" -> "Überverkauft"
        "MEISTGESHORTET" -> "Meiste Leerverkäufe"
        "GRAHAM" -> "Graham defensiv"
        "NETNETS" -> "Net-Nets (Graham)"
        "ZAUBERFORMEL" -> "Zauberformel (Greenblatt)"
        "GALLEA" -> "Insider und Antizyklik (Gallea)"
        "NEFF" -> "Niedriges KGV mit Dividende (Neff)"
        "FISHER" -> "Niedriges KUV mit Wachstum (Fisher)"
        "DREMAN" -> "Antizyklisch (Dreman)"
        "OSHAUGHNESSY" -> "Niedriges KUV (O'Shaughnessy)"
        "DIVIDENDEN_HUNDE" -> "Dividenden-Hunde"
        "RUECKSETZER" -> "Rücksetzer im Aufwärtstrend"
        "NAH_AM_HOCH" -> "Nah am 52-Wochen-Hoch"
        "STAERKSTE_12_MONATE" -> "Stärkste 12 Monate"
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
        "GEWINNWACHSTUM" -> "Gewinn und Umsatz wachsen um mehr als 25 %, der Kurs liegt über der 200-Tage-Linie."
        "UMSATZWACHSTUM" -> "Wachsende Unternehmen mit wenig Schulden, hohem Institutionenanteil und wenig Leerverkäufen."
        "REL_VOLUMEN" -> "Solvente Wachstumswerte im Aufwärtstrend mit ungewöhnlich hohem Volumen."
        "STETIG" -> "Gewinnwachstum über mehrere Zeiträume, der Kurs liegt mindestens 90 % über dem Jahrestief."
        "HALTEN" -> "Kleine Werte mit erwartetem Wachstum, hoher Eigenkapitalrendite, guter Liquidität, PEG bis 1 und Beta bis 1,5, das niedrigste erwartete KGV zuerst."
        "DIVIDENDENWACHSTUM" -> "Große Dividendenzahler mit moderater Bewertung, Wachstum und einer Ausschüttung unter der Hälfte des Gewinns."
        "NIEDRIGES_KGV" -> "Kleine Werte unter Buchwert mit niedrigem KGV und positiven Renditen."
        "CANSLIM" -> "Filter nach den CANSLIM-Regeln von William O\'Neil, so weit die Daten reichen."
        "NEBENWERTE_WACHSTUM" -> "Nebenwerte zwischen 300 Mio. und 2 Mrd. mit Gewinn- und Umsatzwachstum über 20 %."
        "LUECKE_AUF" -> "Werte ab 300 Mio. Börsenwert, die heute mindestens 3 % über dem Vortagesschluss eröffnet haben, die größte Lücke zuerst. Vor Handelsbeginn zeigt die Liste noch die Lücken des letzten Handelstags."
        "LUECKE_AB" -> "Werte ab 300 Mio. Börsenwert, die heute mindestens 3 % unter dem Vortagesschluss eröffnet haben, die größte Lücke zuerst. Vor Handelsbeginn zeigt die Liste noch die Lücken des letzten Handelstags."
        "NETTOKASSE" -> "Werte ab 300 Mio. Börsenwert, deren Kasse die Schulden um mindestens 10 % des Börsenwerts übersteigt."
        "RUECKKAEUFE" -> "Werte ab 1 Mrd. Börsenwert, die in den letzten zwölf Monaten Aktien für mindestens 2 % des Börsenwerts zurückgekauft haben."
        "FCF_RENDITE" -> "Werte ab 1 Mrd. Börsenwert, deren freier Cashflow mindestens 5 % des Börsenwerts beträgt."
        "KGV_ROE" -> "Werte ab 300 Mio. Börsenwert mit KGV bis 15 und Eigenkapitalrendite ab 15 %, das niedrigste KGV zuerst."
        "SOLIDE_DIVIDENDE" -> "Große Werte mit Dividendenrendite ab 3 %, Ausschüttung bis 60 % des Gewinns und Verschuldung bis 2."
        "RSI_UEBERKAUFT" -> "Liquide Werte mit RSI ab 70, der höchste RSI zuerst."
        "RSI_UEBERVERKAUFT" -> "Liquide Werte mit RSI bis 30, der niedrigste RSI zuerst."
        "MEISTGESHORTET" -> "Liquide US-Werte mit mindestens 10 % Leerverkaufsquote am Streubesitz, die höchste zuerst."
        "GRAHAM" -> "Firmen ab 1 Mrd. Umsatz, günstig auf den mittleren Gewinn dreier Jahre und auf den Buchwert: KGV bis 15, KBV bis 1,5, beides multipliziert höchstens 22,5. Das kleinste Produkt zuerst."
        "NETNETS" -> "Werte, deren Umlaufvermögen nach Abzug aller Verbindlichkeiten über dem Börsenwert liegt, der größte Überschuss zuerst."
        "ZAUBERFORMEL" -> "Rang nach Gewinnrendite (EBIT durch Unternehmenswert) plus Rang nach Kapitalrendite (EBIT durch Nettoumlaufvermögen und Sachanlagen), die kleinste Rangsumme zuerst. Ohne Banken, Versicherungen und Versorger."
        "GALLEA" -> "Werte ab 150 Mio. Börsenwert mit Insiderkäufen, deren Kurs mindestens 50 % unter dem Jahreshoch liegt und die bei mindestens zwei von vier Kennzahlen günstig sind."
        "NEFF" -> "KGV unter den niedrigsten 40 % des gewählten Markts, erwartetes Gewinnwachstum ab 7 % pro Jahr und Dividendenrendite ab 2 %, das niedrigste KGV zuerst."
        "FISHER" -> "Börsenwert höchstens das 1,5-Fache des Umsatzes, Nettomarge ab 5 %, erwartetes Gewinnwachstum ab 15 % pro Jahr und positiver freier Cashflow, das niedrigste KUV zuerst."
        "DREMAN" -> "KGV von 8 bis 14, dazu KBV und Kurs-Cashflow-Verhältnis unter den niedrigsten 40 % des gewählten Markts."
        "OSHAUGHNESSY" -> "Werte ab 1 Mrd. Börsenwert, deren KUV zu den niedrigsten 30 % des gewählten Markts gehört, das niedrigste KUV zuerst."
        "DIVIDENDEN_HUNDE" -> "Die zehn höchsten Dividendenrenditen des gewählten Markts, davon die fünf Aktien mit dem niedrigsten Kurs. Gedacht für einen Index wie Dow Jones oder DAX."
        "RUECKSETZER" -> "Liquide Werte über der 200-Tage-Linie, die 50- über der 200-Tage-Linie, RSI bis 35, der niedrigste RSI zuerst."
        "NAH_AM_HOCH" -> "Liquide Werte ab 300 Mio. Börsenwert, höchstens 5 % unter dem 52-Wochen-Hoch, die höchste Jahresrendite zuerst."
        "STAERKSTE_12_MONATE" -> "Liquide Werte ab 300 Mio. Börsenwert, deren Jahresrendite zum obersten Zehntel des gewählten Markts gehört, die höchste zuerst."
        else -> ""
    }
    override fun afVorlageFehlt(id: String): String? = when (id) {
        "LEERVERKAUFT" -> "Ohne Prüfung, ob Optionen und Leerverkauf möglich sind, dafür gibt es keine freie Quelle."
        "ZAHLENLUECKE" -> "Ohne ATR und ohne Unterscheidung vor oder nach Börsenschluss."
        "WOCHENTIEF" -> "Den Aufwärtskanal nähert die App über den Kurs über der 200-Tage-Linie und die 50- über der 200-Tage-Linie an."
        "UEBERVERKAUFT_ZAHLEN" -> "Zahlentermin in den nächsten 31 Tagen statt im laufenden Monat."
        "HOECHSTSTAENDE" -> "Hochs aus Schlusskursen, beim Jahreshoch genügt höchstens 1 % Abstand."
        "AUSBRUCH" -> "50-Tage-Hoch aus Schlusskursen."
        "UMSATZWACHSTUM" -> "Ohne Umsatzwachstum über fünf Jahre."
        "STETIG" -> "Ohne Gewinnwachstum der letzten fünf Jahre."
        "CANSLIM" -> "Ohne Gewinnwachstum über fünf Jahre, Streubesitz unter 20 Mio. Aktien und Marktlage. Die Marktphase steht unter Entdecken."
        "NEBENWERTE_WACHSTUM" -> "Ohne Wachstum über fünf Jahre."
        "GRAHAM" -> "KGV aus Börsenwert und mittlerem Nettogewinn der letzten drei Geschäftsjahre. Ohne Banken und Versicherungen, für sie lädt der Finder keine Jahresabschlüsse."
        "NETNETS" -> "Ohne Banken und Versicherungen, für sie lädt der Finder keine Jahresabschlüsse."
        "ZAUBERFORMEL" -> "Nettoumlaufvermögen wie ausgewiesen, ohne Abzug überschüssiger Kasse. Führt der Abschluss keine Schulden, zählen sie als null."
        "GALLEA" -> "Insider-Trend der letzten 180 Tage aus den Meldungen auf dem Gerät (USA, Deutschland, weitere europäische Länder). Ohne die Verkaufsregeln des Rasters. KUV und freier Cashflow fehlen bei Banken und Versicherungen."
        "NEFF" -> "Die KGV-Grenze und das Wachstum ab 7 % sind Festlegungen der App."
        "FISHER" -> "Wachstum als erwartetes Gewinnwachstum über fünf Jahre. Ohne die strengere KUV-Grenze für Zykliker, ohne Banken und Versicherungen."
        "DREMAN" -> "Ohne Banken und Versicherungen, für sie lädt der Finder keine Jahresabschlüsse."
        "OSHAUGHNESSY" -> "Ohne Banken und Versicherungen, für sie lädt der Finder keine Jahresabschlüsse."
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
        "WAAGE" -> "Saldo (geladene Werte)"
        "NETTOKASSE" -> "Nettokasse"
        "FCF_RENDITE" -> "Rendite freier Cashflow"
        "RUECKKAUF_RENDITE" -> "Rückkaufrendite"
        "RUECKKAUF_BETRAG" -> "Rückkauf"
        "INSIDER" -> "Insider-Trend, 180 Tage"
        "UMSATZ" -> "Umsatz"
        "KGV_SCHNITT3" -> "KGV auf 3-Jahres-Gewinn"
        "GRAHAM_PRODUKT" -> "KGV mal KBV"
        "NETTO_UMLAUF" -> "Netto-Umlaufvermögen zum Börsenwert"
        "GEWINNRENDITE" -> "Gewinnrendite"
        "KAPITALRENDITE" -> "Kapitalrendite"
        "KUV" -> "KUV"
        "KCF" -> "Kurs-Cashflow"
        "KFCF" -> "Kurs je freiem Cashflow"
        "MARGE" -> "Nettomarge"
        else -> id
    }
    override val afVorlageKeine = "Keine Vorlage"
    override val afVorlageNurUs = "Nur US-Werte, die Leerverkaufsquote am Streubesitz gibt es frei nur dort."
    override val afVorlageHinweis = "Kurs und Börsenwert in Euro mit den Zahlen der Dollargrenzen."
    override fun afVorlageStand(offen: Int) = if (offen == 1) "1 Wert wartet noch auf Profil oder Tageskurse." else "${de.troetenrepublik.app.ui.Fmt.num(offen.toDouble(), 0)} Werte warten noch auf Profil oder Tageskurse."
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
        "NACHKAUF" -> if (zahl == 1) "1 Nachkauf mehr als 20 % unter dem Einstand" else "$zahl Nachkäufe mehr als 20 % unter dem Einstand"
        else -> a
    }
    override fun apErklaerung(id: String) = when (id) {
        "HALTEDAUER" -> "Wer Positionen nur Tage hält, handelt eher Kursbewegungen als Unternehmen. Kosten und Steuern fallen dann bei jedem Wechsel an."
        "TREFFER" -> "Die Trefferquote allein sagt wenig. Wichtig ist auch, ob die Gewinne die Verluste übertreffen."
        "CHANCE_RISIKO" -> "Liegt die Summe aller Verkäufe in Euro im Minus, hat das Handeln bisher Geld gekostet. Ein Verhältnis unter 1 braucht eine hohe Trefferquote."
        "DISPOSITION" -> "Viele Anleger verkaufen Gewinner früh und halten Verlierer in der Hoffnung auf Erholung. Feste Verkaufsregeln helfen dagegen."
        "HAEUFIGKEIT" -> "Häufiges Handeln erhöht Kosten und die Zahl der Entscheidungen. Studien zu Privatanlegern finden bei den aktivsten die schwächsten Ergebnisse nach Kosten."
        "HEBEL" -> "Hebelprodukte verlieren schnell an Wert und können ganz verfallen. Ein hoher Anteil am Handel bindet das Ergebnis an wenige, stark schwankende Wetten."
        "NACHKAUF" -> "Nachkaufen nach einem Verlust senkt den Einstand, erhöht aber das Gewicht eines Werts, der gerade fällt. Entscheidend ist, ob der Kaufgrund noch gilt."
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
        "Einen Rückgang des Depots um 30 % würde ich aussitzen, ohne aus Angst zu verkaufen.",
        "Ich kenne die laufenden Kosten meiner Fonds und die Gebühren je Order.",
        "Ich habe keine Schulden mit höheren Zinsen, als ich vom Depot erwarte.",
        "Ich prüfe die Gewichtung ein- bis zweimal im Jahr und schichte nur dann um.",
    )
    override fun scStand(ja: Int, alle: Int) = "$ja von $alle trifft zu"
    override fun anHaelt(anteil: String, wert: String, stichtag: String, seit: String?) = "Hält selbst $anteil" + (if (wert.isNotBlank()) " ($wert)" else "") + " laut Meldung zum $stichtag" + (seit?.let { ", Kurs seitdem $it" } ?: "")
    override val anHalterHinweis = "Bestände aus den Pflichtmeldungen der Institute (in den USA Form 13F), bekannt sind nur die zehn größten Halter. Den Einstand melden Institute nicht, ob sie im Verlust sind, bleibt offen. Die Kursänderung seit dem Stichtag zeigt nur, wie sich der gemeldete Bestand seitdem entwickelt hat."
    override fun anHalterKeiner(n: Int) = "Keines der Häuser steht unter den $n größten gemeldeten Haltern. Kleinere Bestände nennt die Quelle nicht."
    override val hrTitel = "Haus-Rangliste"
    override val hrTrefferTitel = "Trefferanteil je Haus"
    override val hrVorsprung = "Vorsprung"
    override val hrHausLabel = "Trefferanteil des Hauses"
    override val hrKeine = "Keine Rangliste verfügbar"
    override fun hrUnter(n: Int, von: String, bis: String) = "$n Häuser · Trefferanteil $von bis $bis"
    override fun hrUrteile(n: String) = "$n Urteile"
    override fun hrStand(stand: String, von: String, bis: String, aktien: Int, urteile: Int) = "Stand $stand · Urteile $von bis $bis · $aktien Aktien"
    override fun hrInfo(min: Int) = "• Treffer: Die Aktie lief 3, 6 oder 12 Monate nach einer Hochstufung besser als ihr Index, nach einer Abstufung schlechter. Der Trefferanteil zählt alle verstrichenen Zeiträume zusammen. Der Strich im Balken markiert 50 %." +
        "\n• Gesichert: der Trefferanteil, den das Haus mit dieser Zahl an Urteilen sehr wahrscheinlich mindestens hat. Wenige Urteile senken ihn." +
        "\n• Vorsprung: mittlere Mehrrendite gegen den Index in Richtung des Urteils." +
        "\n• Gezählt werden Hoch- und Abstufungen bei Aktien aus S&P 500, Nasdaq 100, DAX und Euro Stoxx 50, nur Häuser ab $min Urteilen. Yahoo führt Urteile fast nur für US-Aktien."
    override fun hrSortierung(name: String) = when (name) { "GESICHERT" -> "Nach gesichertem Anteil"; "VORSPRUNG" -> "Nach Vorsprung"; "URTEILE" -> "Nach Urteilen"; else -> "Nach Trefferanteil" }
    override fun hrAnzahl(n: Int) = if (n == 1) "1 Haus" else "$n Häuser"
    override fun hrUrteileTreffer(urteile: String, treffer: String) = "$urteile · Treffer $treffer"
    override val finJahreTitel = "Bilanz und Cashflow über die Jahre"
    override val finLangfristSchulden = "Langfristige Schulden"
    override val finInvestitionen = "Investitionen in Anlagen"
    override fun umsatzSchaetzung(quartal: String) = if (quartal.isBlank()) "Umsatz erwartet" else "Umsatz erwartet $quartal"
    override val qzTitel = "Quartalszahlen"
    override val qzBekannte = "Bekannte"
    override val qzAlle = "Alle"
    override val qzFehlt = "Der Ergebniskalender ist gerade nicht erreichbar."
    override fun qzUmsatz(wert: String) = "Umsatz erw. $wert"
    override val qzTagFehlt = "nicht geladen"
    override val qzKeine = "Keine Termine"
    override fun qzKeineBekannten(n: Int) = "Kein bekannter Wert, $n kleinere unter „Alle“"
    override fun qzZeit(zeit: Int) = listOf("Vor Börsenstart", "Während des Handels", "Nach Börsenschluss", "Ohne Uhrzeit").getOrElse(zeit) { "" }
    override val qzHeute = "heute"
    override fun qzVon(gezeigt: Int, gesamt: Int) = "$gezeigt von $gesamt"
    override val qzEigen = "im Depot oder auf der Watchlist"
    override fun qzErwartet(wert: String) = "erwartet $wert"
    override fun qzVorjahr(wert: String) = "Vorjahr $wert"
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
        "vix" -> "Schwankungsindex VIX"; "vix_struktur" -> "VIX gegen VIX3M"; "vvix" -> "VVIX, Schwankung des VIX"; "move" -> "MOVE, Schwankung der US-Anleihen"
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
        "breite200" -> "Feste Grenzen: 20 % der Werte ergeben 0, 80 % ergeben 100"
        "hochtief" -> "Hochs minus Tiefs in Prozent aller Werte, feste Grenzen: −30 % ergeben 0, +30 % ergeben 100"
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
        "margin" -> "Feste Grenzen: −20 % zum Vorjahr ergeben 0, +30 % ergeben 100"
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

    // 5.95 Lizenzen
    override val lizenzenTitel = "Open-Source-Lizenzen"
    override val lizenzenHinweis = "Die App enthält Bibliotheken anderer Projekte, die unter eigenen Lizenzen stehen. Die Lizenztexte stehen hier im englischen Original."
    override val lizenzenFehlt = "Die Lizenzdatei fehlt in diesem Paket. Sie liegt auch im Quellcode als THIRD_PARTY_LICENSES.txt."
    // 5.95 Nasdaq
    override val nkNichtVerfuegbar = "Der Kalender ist gerade nicht verfügbar. Die App versucht es beim nächsten Öffnen wieder."
    override val nkIpoFehlt = "Der IPO-Kalender ist gerade nicht verfügbar. Hier stehen die Prospekte der letzten Tage bei der SEC."
    override val nkQzHinweis = "Bekannt heißt: Depot, Watchlist, Werte aus dem Verzeichnis der App und Firmen ab 10 Mrd. Dollar Börsenwert oder 1 Mrd. Dollar erwartetem Quartalsumsatz, je Zeitpunkt die acht größten. Termine können sich verschieben, Gewinne je Aktie stehen in Dollar und meist ohne Sondereffekte."
    override val nkDivTitel = "Dividendentermine"
    override fun nkZahltag(datum: String) = "Zahltag $datum"
    override fun nkJeJahr(wert: String) = "im Jahr $wert"
    override val nkDivKeine = "Keine Termine"
    override fun nkDivKeineBekannten(n: Int) = "Kein bekannter Wert, $n weitere unter „Alle“"
    override val nkDivHinweis = "Bekannt heißt: Depot, Watchlist und Werte aus dem Verzeichnis der App.\n\n• Der Kalender führt Werte an US-Börsen samt Vorzugsaktien und Fonds, Beträge in Dollar je Aktie.\n• „Im Jahr“ ist die Summe der Zahlungen eines Jahres laut Quelle.\n• Sortiert wird nach Ex-Tag, der Zahltag steht in der Zeile.\n• Bei Splits gilt der Tag, ab dem im neuen Verhältnis gehandelt wird."
    override val nkSplits = "Aktiensplits"
    override val nkSplit = "Split"
    override val nkZusammenlegung = "Zusammenlegung"
    override fun nkSplitText(neu: Int, alt: Int) = "aus " + (if (alt == 1) "1 Aktie" else "$alt Aktien") + (if (neu == 1) " wird " else " werden ") + neu
    override val nkWiTitel = "Wirtschaftstermine"
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
    override val q5VxMehr = "Ein Terminkontrakt legt heute den Preis fest, zu dem der VIX später abgerechnet wird. M1 verfällt am nächsten Verfallstag, M2 im Folgemonat.\n• Contango: M2 teurer als M1, der Normalzustand\n• Backwardation: M1 über M2, Anleger suchen sofort Schutz, meist bei einem plötzlichen Einbruch\n\nDie Kurve vergleicht M1 mit M2. Ein Verhältnis über 1 heißt Backwardation, ab 0,95 gilt die Kurve als flach, darunter als Contango."
    override val q5IgPrivat = "Investegate ist nur für Privatanleger bestimmt. Bestätige, dass du Privatanleger bist und die Meldungen nur persönlich nutzt. Erst danach ruft die App sie ab."
    override val q5IgJa = "Ich bin Privatanleger"
    override val q5IgNein = "Nein"
    override val q5IgKeine = "Im Moment führt Investegate keine Director Dealings zu dieser Aktie."
    override val q5IgSeite = "Alle Meldungen der Firma bei Investegate"
    override fun q5IgQuelleMeldung(quelle: String) = "Quelle: Investegate, Meldung über $quelle, unverändert wiedergegeben."
    override val q5IgOeffnen = "Meldung bei Investegate öffnen"
    override val q5IgAlleTitel = "Director Dealings Großbritannien"
    override val q5IgAlleUnter = "Neue Meldungen über Eigengeschäfte von Vorständen britischer Firmen, über Investegate"
    override val q5IgKategorie = "Alle Director Dealings bei Investegate"
    override val q5NnKeine = "Im letzten Jahr keine Managers Transactions bei Nasdaq Nordic."
    override val q5MnbKeine = "Im letzten Jahr keine Meldungen von Führungskräften bei der MNB."
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
    override val srHinweis = "Eine TR-1-Meldung zeigt, wann ein Halter eine Stimmrechtsschwelle kreuzt.\n\n• Bei britischen Firmen gilt ab 3 % jede volle Prozentschwelle darüber und darunter.\n• Bei Firmen aus anderen Ländern gelten die Schwellen 5, 10, 15, 20, 25, 30, 50 und 75 %.\n• Der Anteil kann aus Aktien, Finanzinstrumenten wie Optionen oder beidem bestehen.\n• Die Schwelle leitet die App aus dem Anteil vorher und nachher ab."

    // 5.96 Labor
    override val slRegelWaehlen = "Regel für die Einzelansicht wählen"
    override val slKeineLinie = "Alle Linien sind ausgeblendet."
    override fun slLinieSchalter(regel: String) = "Linie $regel im Chart zeigen"

    // 5.96 Daten
    override fun hlUnter(p: String) = "unter $p"
    override val hlUnveraendert = "unverändert"
    override fun hlMitNamen(n: Int, gesamt: Int) = "$n von $gesamt mit Namen gemeldet"
    override val hlGemeldet = "Mit Namen gemeldet"
    override val hlKeineNamen = "Die Quelle nennt keine Institute mit Namen."
    override val hlFonds = "Größte Fonds"
    override val hlFondsHinweis = "Fonds stehen getrennt. Ihre Anteile können in denen der Institute oben schon enthalten sein."
    // 0.77 13F-Gesamtbild
    override fun g13Kopf(quartal: String) = "Alle 13F-Melder · $quartal"
    override val g13Halter = "13F-Halter"
    override fun g13Netto(kaeufer: Boolean) = if (kaeufer) "Netto-Käufer" else "Netto-Verkäufer"
    override val g13Stueck = "Stückzahl seit Vorquartal"
    override val g13Bestand = "Bestand"
    override val g13Groesste = "Größte 13F-Halter"
    override fun g13KernHalter(n: String) = "$n 13F-Halter"
    override fun g13KernNetto(n: String, kaeufer: Boolean) = if (kaeufer) "$n Netto-Käufer" else "$n Netto-Verkäufer"
    override fun g13Stand(melder: String, bis: String) = "13F: $melder Melder, Meldungen bis $bis"
    override val g13Hinweis = "13F-Zeilen: Netto-Käufer sind Aufstocker und neue Halter minus Abbauer und Ausgestiegene. Verglichen werden nur Melder, die in beiden Quartalen gemeldet haben."
    override val g13Titel = "Alle Fonds im Quartal"
    override fun g13KernMelder(n: String, quartal: String) = "$n Melder · $quartal"
    override val g13Gekauft = "Meistgekauft"
    override val g13Verkauft = "Meistverkauft"
    override fun g13Weitere(n: Int) = "$n weitere"
    override val g13Weniger = "Weniger"
    override fun g13Unten(zufluss: Boolean, betrag: String, halter: String) = (if (zufluss) "Zufluss " else "Abfluss ") + "$betrag · $halter Halter"
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
    override fun vgMax(n: Int) = "Bis zu $n Vergleiche gleichzeitig."
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
    override val vgVergleichIntro = "Alle Linien beginnen am Anfang des gemeinsamen Zeitraums bei null."

    // 5.96 Feed
    override val feedErstellen = "Eigenen Feed erstellen"
    override val feedBearbeiten = "Feed bearbeiten"
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
    override val feedMeldeQuellen = "Quellen für Mitteilungen"
    override val feedOhneFeeds = "Noch kein eigener Feed."
    override val feedBuendel = "Mehr als drei neue Schlagzeilen auf einmal kommen als eine Mitteilung."

    // 5.96 Kongress
    override val copyFormer = "Ehemalige"
    override val copyMitgliederNote = "Mitglieder einmal am Tag geprüft. Ehemalige stehen hier bis 180 Tage nach dem Ausscheiden und nach ihrer letzten Meldung, danach über die Suche."
    override fun kammerZeitraum(senat: Boolean, von: Int, bis: Int) = (if (senat) "ehemals im Senat, " else "ehemals im Repräsentantenhaus, ") + amtZeitraum(von, bis)
    override fun amtZeitraum(von: Int, bis: Int) = if (von == bis) "$von" else "$von bis $bis"

    // 5.97 Aufklappen
    override val aufZahlenHerkunft = "Herkunft der Zahlen"

    // 5.97 Finanzen
    override val finDivTitel = "Dividende"
    override val finDivJeAktie = "Je Stück in 12 Monaten"
    override val finDivExTag = "Nächster Ex-Tag"
    override val finDivZahltag = "Nächste Zahlung"
    override val finDivBestand = "Dein Bestand im Jahr, vor Steuer"
    override val finVerlust = "Verlust"
    override fun finWechsel(art: String) = if (art == "VERLUST_ZU_GEWINN") "Von Verlust zu Gewinn" else "Von Gewinn zu Verlust"
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
    override val qzbUhrText = "Der genaue Zeitpunkt kann abweichen."
    override val qzbZahlen = "Welche Zahlen kommen"
    override val qzbGewinn = "Gewinn je Aktie"
    override val qzbUmsatz = "Umsatz"
    override fun qzbErwartetVon(quelle: Int, analysten: Int?) = when (quelle) {
        0 -> "Kalender" + (analysten?.let { ", $it Schätzungen" } ?: "")
        else -> "Yahoo Finance" + (analysten?.let { ", $it Analysten" } ?: "")
    }
    override val qzbErwartet = "erwartet"
    override val qzbSpanne = "Spanne"
    override fun qzbVonBis(von: String, bis: String) = "$von bis $bis"
    override val qzbVorjahr = "Vorjahresquartal"
    override val qzbGemeldet = "gemeldet"
    override val qzbAusblick = "Ausblick"
    override val qzbAusblickText = "Viele Firmen nennen mit den Zahlen auch eine Erwartung für das nächste Quartal oder das ganze Jahr. Dafür führen die Quellen der App keine Zahlen. Der Ausblick steht in der Mitteilung der Firma."
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
    override val qzbKeineBewertung = "Für eine Einordnung fehlen den Quellen Schätzungen oder Vorjahreswerte."
    override val qzbVerlauf = "Die letzten Quartale"
    override fun qzbQuartalBis(datum: String) = "Quartal bis $datum"
    override val qzbVonWem = "Woher die Schätzungen kommen"
    override fun qzbKalenderZeile(schaetzungen: Int?) = "Ergebniskalender" + (schaetzungen?.let { ": Konsens aus $it Schätzungen" } ?: "")
    override fun qzbAnalystenZeile(gewinn: Int?, umsatz: Int?) = "Analysten" + listOfNotNull(
        gewinn?.let { "$it zum Gewinn" }, umsatz?.let { "$it zum Umsatz" },
    ).joinToString(" und ").let { if (it.isEmpty()) "" else ": $it" } + ", dazu die letzten Quartale"
    override fun qzbHaeuser(namen: String) = "Häuser, die die Aktie in den letzten 90 Tagen eingestuft haben: $namen"
    override val qzbVonWemText = "Die Dienste zählen unterschiedlich viele Analysten, darum weichen ihre Werte leicht voneinander ab."
    override val qzbYahooFehlt = "Yahoo Finance liefert gerade keine weiteren Schätzungen."
    // 0.68 Finanzen
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
        finAbweichung -> "Gemeldet gegen erwartet in Prozent.\n\n• Dreieck nach oben heißt übertroffen, nach unten verfehlt.\n• Bis 2 % ohne Farbe."
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
        "RHYTHMUS" -> "16-Wochen-Rhythmus"
        "MOMENTUM" -> "12-Monats-Momentum"
        "RSI_KURZ" -> "Kurzer RSI im Trend"
        "MONATSWECHSEL" -> "Monatswechsel"
        "ZIELVOLA" -> "Zielvolatilität $a %"
        else -> art
    }

    // 0.68 Suchverlauf
    override val svKuerzlich = "Kürzlich gesucht"
    override val svLoeschen = "Verlauf löschen"
    override val svVerlauf = "Verlauf"
    override val svAus = "Verlauf ausschalten"
    override val svAusEinstellung = "Suchverlauf speichern"
    override val kgvVerlust = "Verlust erwartet"
    override fun wdReicht(jahre: String) = "Geld reicht $jahre"
    override fun wdReichtFolgen(n: Int, von: Int) = "bei $n von $von Startjahren"
    override fun wdReichtVerlaeufe(pct: String) = "in $pct der Verläufe"
    override fun svEntfernen(name: String) = "$name aus dem Verlauf entfernen"

    // 0.68 Tagesrueckblick und Mitteilungen
    override val rbUhrzeit = "Uhrzeit"
    override fun rbEinleitungDepot(ton: String, hoch: Boolean, prozent: String) = "$ton: Dein Depot " + (if (hoch) "stieg" else "fiel") + " um $prozent"
    override val rbVeraenderung = "Veränderung"
    override val rbWert = "Wert"
    override val mtAlle = "Alle Mitteilungen"
    override fun mtProfil(an: Boolean, aktiv: Int, gesamt: Int) = if (!an) "Aus" else "$aktiv von $gesamt Arten an"
    override val mtAlleAus = "Alle Mitteilungen sind aus."
    override val mtDepot = "Depot und Orders"
    override val mtDepotHinweis = "Orders, Sparpläne, Knock-outs, Wetten, Dividenden, Zinsen."
    override val mtAlarme = "Kurse und Preismarken"
    override val mtAlarmeHinweis = "Preismarken, Finder-Treffer, teurere Fonds, Anlagethesen."
    override val mtCopy = "Insider, Politik und Behörden"

    // 0.68 ipo
    override fun ipoSchritt(i: Int) = listOf("Eingereicht", "Preis fest", "Handel").getOrElse(i) { "" }
    override val ipoSchrittZurueck = "Zurückgezogen"
    override val ipoPreisSpanne = "Preisspanne"
    override val ipoSpanneMitte = "Mitte"
    override val ipoSeitAusgabeTitel = "Seit Ausgabe"
    override fun ipoKursJetzt(p: String) = "Kurs $p"
    override fun ipoAusgabe(p: String) = "Ausgabe $p"
    override fun ipoPunktKurz(merkmal: Int) = if (merkmal == 0) "SPAC" else "Kleines Angebot"
    override fun ipoPunktErklaerung(merkmal: Int) = if (merkmal == 0) "Sie sammelt Geld, um später eine noch unbekannte Firma zu kaufen. Viele SPAC-Aktien fielen nach der Übernahme deutlich."
        else "Solche Werte finden wenig Beachtung und schwanken oft stark."
    override val ipoSchlagzeilen = "Schlagzeilen"
    override fun ipoKopfLabel(i: Int) = listOf("Anstehend", "Eingereicht", "Gestartet").getOrElse(i) { "" }
    override val ipoKopfTage = "30 Tage"
    override val ipoGrosse = "Große Börsengänge"
    override val ipoBericht = "Bericht"
    override fun ipoLautProspekt(betrag: String) = "laut Prospekt $betrag"
    override val ipoLautProspektKurz = "laut Prospekt"
    override val ipoVolumenProspekt = "Volumen laut Prospekt"
    override fun ipoWeitere(n: Int) = "Weitere $n"
    override fun ipoDavonSpac(n: Int) = "davon $n SPAC"
    override fun alertArtText(tag: String, extra: String, price: String) = when (tag) {
        "ALERT_SMA_UP" -> "Kurs über dem 50-Tage-Schnitt $extra · jetzt $price"
        "ALERT_SMA_DOWN" -> "Kurs unter dem 50-Tage-Schnitt $extra · jetzt $price"
        "ALERT_VOLUME" -> "Volumen $extra des Normalen · Kurs $price"
        "ALERT_EXDIV" -> "Letzter Kauftag $extra · Kurs $price"
        else -> "Insider-Kauf über $extra · Kurs $price"
    }
    override val zrTitel = "Bewegung zu den Zahlen"
    override fun zrErwartet(straddle: Boolean) = if (straddle) "Eingepreist (Straddle)" else "Eingepreist (aus IV)"
    override val zrKursNach = "Kurs nach den Zahlen"
    override fun zrUeblichZeile(pct: String) = "üblich $pct"
    override val zrUeblichBand = "üblich"
    override val zrEingepreistBand = "eingepreist"
    override val zrInfo = "• Üblich: Median der Kursbewegungen (Betrag) nach den letzten Zahlenterminen\n• Bewegung: Schlusskurs vor den Zahlen gegen Schlusskurs am ersten Handelstag danach"
    override val zrEpsReihe = "Gewinn je Aktie"
    override val zrEpsUeberraschung = "Gewinn gegen erwartet"
    override val zrUmsatzUeberraschung = "Umsatz gegen erwartet"
    override fun zrEpsStatt(ist: String, schaetzung: String) = "$ist, erwartet $schaetzung"
    override fun zrSaeule(ist: String, schaetzung: String) = "$ist, erwartet $schaetzung"
    override val finDivJahreErhoeht = "Jahre in Folge erhöht"
    override val finDivKauftag = "Letzter Kauftag"
    override val finDiv5J = "5-Jahres-Wachstum"
    override fun nkErhoeht(pct: String) = "Erhöht um $pct"

    // 0.76 q78a
    override val sigTitel = "Marker"
    override val sigKeine = "Keine Marker"
    override val sgInsider = "Insider, 90 Tage"
    override val sgKongress = "Kongress, 90 Tage"
    override val sgStufungen = "Hoch- und Abstufungen, 30 Tage"
    override val sgStufungenKurz = "Analysten, 30 Tage"
    override val sgTrends = "Reddit, 7 Tage"
    override fun sgTrendWert(rang: Int, aenderung: String?) = "Rang $rang" + (aenderung?.let { " · $it" } ?: "")
    override val sgOptionen = "Auffällige Optionen"
    override fun sgInsiderWert(kaeufer: Int, verkaeufer: Int) = (if (kaeufer == 1) "1 Käufer" else "$kaeufer Käufer") + " · " + (if (verkaeufer == 1) "1 Verkäufer" else "$verkaeufer Verkäufer")
    override fun sgKaeufeVerkaeufe(kaeufe: Int, verkaeufe: Int) = (if (kaeufe == 1) "1 Kauf" else "$kaeufe Käufe") + " · " + (if (verkaeufe == 1) "1 Verkauf" else "$verkaeufe Verkäufe")
    override fun sgHochAb(hoch: Int, ab: Int) = (if (hoch == 1) "1 Hochstufung" else "$hoch Hochstufungen") + " · " + (if (ab == 1) "1 Abstufung" else "$ab Abstufungen")
    override fun sgHochAbKurz(hoch: Int, ab: Int) = "$hoch hoch · $ab ab"
    override fun sgOptionenWert(anzahl: Int, calls: Int, puts: Int) = "$anzahl · " + (if (calls == 1) "1 Call" else "$calls Calls") + " · " + (if (puts == 1) "1 Put" else "$puts Puts")
    override fun sgNetto(betrag: String) = "Netto $betrag"
    override fun sgPraemie(betrag: String) = "Prämie $betrag"
    override val mvMedian = "Tage bis zur Meldung"
    override val mvSpaet = "Verspätet, über 45 Tage"
    override val mvGeschaefte90 = "Geschäfte, 90 Tage"
    override val mvVolumen90 = "Volumen, 90 Tage"
    override fun mvTage(n: Int) = if (n == 1) "1 Tag" else "$n Tage"
    override fun mvUnter(spaet: String, geschaefte: Int, volumen: String) = "$spaet verspätet · ${if (geschaefte == 1) "1 Geschäft" else "$geschaefte Geschäfte"} in 90 Tagen · $volumen"
    override val flTitel = "Kongress nach Sektor"
    override val flNav = "Käufe und Verkäufe der letzten 90 Tage, auch nach Kammer und Familie"
    override val flSektoren = "Nach Sektor"
    override val flKammern = "Nach Kammer"
    override val flEigeneFamilie = "Selbst und Familie"
    override val flHaus = "Repräsentantenhaus"
    override val flSenat = "Senat"
    override val flEigene = "Selbst"
    override val flFamilie = "Ehepartner, Kinder, gemeinsam"
    override fun flKaeufe(n: Int) = (if (n == 1) "1 Kauf" else "$n Käufe")
    override fun flVerkaeufe(n: Int) = (if (n == 1) "1 Verkauf" else "$n Verkäufe")
    override fun flOhneSektor(n: Int) = if (n == 1) "1 Geschäft ohne Sektor" else "$n Geschäfte ohne Sektor"
    override fun usSeitKauf(p: String) = "seit Geschäft $p"
    override fun usVomMarktwert(p: String) = "$p vom Börsenwert"
    override val usPlanLabel = "Planverkäufe, Anteil der Verkäufe"
    override fun poStunden(h: Int) = "+$h Std."
    override val vbKomponenten = "Komponenten"
    override val vbKursziele = "Kursziele"
    override val vbTief = "Tief"
    override val vbMedian = "Median"
    override val vbHoch = "Hoch"
    override val vbKurs = "Kurs"
    override fun vbStufen(tage: Int, hoch: Int, ab: Int) = "Letzte $tage Tage: $hoch hochgestuft, $ab abgestuft"
    override fun vbAbweichung(wert: String) = "Abweichung $wert"
    override fun vbKursMedian(prozent: String, richtung: Int) = if (richtung == 0) "Kurs auf Median" else "Kurs $prozent " + (if (richtung < 0) "unter" else "über") + " Median"
    override fun vbImPlus(n: Int) = "$n im Plus"
    override fun vbImMinus(n: Int) = "$n im Minus"
    override val vbPaare = "Paare"
    override val ifNurFuehrung = "Nur Führung"
    override val kgvSchnitt = "KGV (Ø-Gewinn)"
    override fun kgvSchnittHinweis(jahr: Int, prozent: String, jahre: Int, ueber: Boolean) =
        "Gewinn $jahr liegt $prozent ${if (ueber) "über" else "unter"} dem Schnitt der letzten $jahre Jahre, das KGV wirkt dadurch ${if (ueber) "niedrig" else "hoch"}."
    // Marktmacht, Branchen, Vorschlagsdepot (0.77)
    override val mmTitel = "Moat"
    override fun mmStufe(s: String) = when (s) { "HOCH" -> "hoch"; "MITTEL" -> "mittel"; else -> "gering" }
    override val mmErklaerung = "Wie schwer Wettbewerber die Stellung der Firma angreifen können: Marktanteil, Preismacht, beständig hohe Rendite."
    override fun mmGrund(k: String, gut: Boolean, wert: String, vergleich: String, jahre: String): String {
        val zusatz = if (jahre.isBlank()) "" else " · $jahre"
        return when (k) {
            "STELLUNG" -> if (vergleich == "1") "Größter der Branche · $wert Umsatzanteil" else if (gut) "Rang $vergleich der Branche · $wert Umsatzanteil" else "Kleiner Umsatzanteil: $wert, Rang $vergleich der Branche"
            "PREIS_BRUTTO" -> if (gut) "Bruttomarge $wert (Branche $vergleich)$zusatz" else "Bruttomarge $wert unter der Branche ($vergleich)$zusatz"
            "PREIS_BETRIEB" -> if (gut) "Operative Marge $wert (Branche $vergleich)$zusatz" else "Operative Marge $wert unter der Branche ($vergleich)$zusatz"
            else -> if (gut) "Kapitalrendite in $vergleich Jahren nie unter $wert" else "Kapitalrendite oder Marge schwankt, niedrigste Rendite $wert"
        }
    }
    override fun mmJahre(ueber: Int, alle: Int) = "$ueber von $alle Jahren darüber"
    override fun mmKurz(stufe: String) = "Moat $stufe"
    override val afAnsichtBranchen = "Branchen"
    override val afAnsichtDepot = "Vorschlagsdepot"
    override fun fbrZahl(n: Int) = if (n == 1) "1 Branche" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} Branchen"
    override val fbrSammel = "Weitere Branchen"
    override fun fbrWerte(n: Int) = if (n == 1) "1 Wert" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} Werte"
    override val fbrAlle = "Alle Branchen"
    override val vsdTitel = "Vorschlagsdepot"
    override fun vsdWerte(n: Int) = if (n == 1) "1 Wert" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} Werte"
    override fun vsdSektoren(n: Int) = if (n == 1) "1 Sektor" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} Sektoren"
    override fun vsdLaender(n: Int) = if (n == 1) "1 Land" else "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} Länder"
    override fun vsdAuswahl(punkte: Int, bereich: Int, jeBranche: Int, jeSektor: Int, jeLand: Int, deckel: Int) = listOf(
        "Mindestens $punkte Punkte, Moat mindestens mittel", "Saldo nicht im Minus", "Ohne Saldo kein Bereich unter $bereich Punkten",
        "Höchstens $jeBranche je Branche, $jeSektor je Sektor, $jeLand je Land", "Gewicht nach Punkten, höchstens $deckel % je Wert",
    )
    override val vsdAuswahlKopf = "Auswahl"
    override val vsdPositionen = "Positionen"
    override val vsdGewicht = "Gewicht"
    override val vsdLeer = "Kein Wert erfüllt alle Bedingungen, oder die Jahresabschlüsse sind noch nicht geladen."
    override fun vsdSektor(key: String) = when (key) {
        "TECHNOLOGIE" -> "Technologie"; "GESUNDHEIT" -> "Gesundheit"; "FINANZEN" -> "Finanzen"; "INDUSTRIE" -> "Industrie"
        "KONSUM_ZYKLISCH" -> "Konsum zyklisch"; "KONSUM_BASIS" -> "Basiskonsum"; "ENERGIE" -> "Energie"; "ROHSTOFFE" -> "Rohstoffe"
        "VERSORGER" -> "Versorger"; "IMMOBILIEN" -> "Immobilien"; else -> "Kommunikation"
    }
    override val vsdSektorKopf = "Nach Sektor"
    override val vsdLandKopf = "Nach Land"
    override val vsdNachbilden = "Im Übungsdepot nachbilden"

    // Offene US-Register: Studien, Fahrzeug- und Produktrückrufe (0.77)
    override val rgStudienTitel = "Klinische Studien (ClinicalTrials.gov)"
    override fun rgStudienLaufend(n: Int) = if (n == 1) "1 laufende Studie" else "$n laufende Studien"
    override fun rgPhase(n: Int) = "Phase $n"
    override val rgStudienBald = "Nächste Abschlüsse"
    override fun rgStudienBaldKern(datum: String) = "nächstes Ende $datum"
    override val rgStudienFertig = "Zuletzt beendet"
    override fun rgStudienFertigKern(fertig: Int, abgebrochen: Int) =
        listOfNotNull(if (fertig > 0) "$fertig abgeschlossen" else null, if (abgebrochen > 0) "$abgebrochen abgebrochen" else null).joinToString(" · ")
    override fun rgStudieEnde(phase: Int, datum: String) = (if (phase > 0) "Phase $phase · " else "") + "Ende $datum"
    override fun rgStudieBeendet(abgebrochen: Boolean, phase: Int, datum: String) =
        (if (phase > 0) "Phase $phase · " else "") + (if (abgebrochen) "Abgebrochen " else "Abgeschlossen ") + datum
    override val rgStudienInfo = "[skala] Phase 1 | Phase 2 | Phase 3 | Zulassung\n• Phase 1: Verträglichkeit\n• Phase 2: Wirkung\n• Phase 3: Wirkung an vielen Patienten, letzte Stufe vor der Zulassung\n\nGezählt wird die höchste Phase einer Studie, bei Phase 2/3 also 3.\n\nZugeordnet über den Sponsor, Studien von Töchtern unter anderem Namen können fehlen."
    override val rgFahrzeugTitel = "Fahrzeugrückrufe (NHTSA)"
    override val rgRueckrufeTitel = "Rückrufe, 24 Monate"
    override fun rgRueckrufeKern(n: Int) = if (n == 1) "1 Rückruf" else "$n Rückrufe"
    override fun rgFahrzeuge(zahl: String) = "$zahl Fahrzeuge"
    override val rgUntersuchungenTitel = "Offene Untersuchungen"
    override fun rgUntersuchungenKern(n: Int) = "$n offen"
    override fun rgUntersuchungSeit(art: String, datum: String) = when (art) {
        "PE" -> "Vorprüfung"; "EA" -> "Detailanalyse"; "RQ" -> "Prüfung eines Rückrufs"; "DP" -> "Antrag auf Untersuchung"
        "AQ" -> "Prüfung der Meldepflicht"; else -> art
    } + " · seit $datum"
    override val rgFahrzeugInfo = "Eine Untersuchung der US-Verkehrssicherheitsbehörde prüft einen vermuteten Mangel und kann zu einem Rückruf führen.\n\n• Die Fahrzeugzahl addiert alle Rückrufe, ein Fahrzeug kann mehrfach zählen.\n• Untersuchungen sind über Hersteller oder Marke im Text zugeordnet, einzelne können fehlen."
    override val rgProdukteTitel = "Produktrückrufe (CPSC)"
    override fun rgProdukteKern(n: Int) = if (n == 1) "1 Rückruf, 24 Monate" else "$n Rückrufe, 24 Monate"
    override val rgProdukteInfo = "Die US-Verbraucherschutzbehörde CPSC veröffentlicht Rückrufe von Konsumgütern. Zugeordnet über Hersteller und Titel, Handelsketten zählen nicht."
    override val rgBeschwerdenTitel = "Verbraucherbeschwerden (CFPB)"
    // 0.77 Paket AD5: Begriffe, seltene Kaeufe, Konvergenz, Insider-Simulator
    override val wdClusterMedianInfo = "Median: Die Hälfte der Cluster lag darüber, die Hälfte darunter."
    override val wdPutErklaerung = "Beim verkauften Put kassiert man eine Prämie. Fällt der Kurs unter den Basispreis (Strike), nimmt man die Aktie dazu ab. Nominal zählt nur die Prämie, realisiert zieht den Verlust am Verfall ab."
    override val skTitel = "Seltene Käufe"
    override fun skAussage(art: Int, monate: Int, beginn: Boolean, zahl: Int, faktor: String, jahr: Int) = when (art) {
        0 -> if (beginn && jahr > 0) "Erstmals $zahl Käufer in einer Woche" else "$zahl Käufer in einer Woche" + if (monate >= 24) ", ${monate / 12} J. Pause" else ""
        1 -> "Größter Kauf, ${faktor}× der bisherige Rekord"
        else -> if (beginn && jahr > 0) "Erster Kauf seit $jahr" else if (beginn) "Erster Kauf in $monate Monaten" else if (monate >= 24) "Erster Kauf seit ${monate / 12} Jahren" else "Erster Kauf seit $monate Monaten"
    }
    override fun skKopf(bis: String, seit: String) = "Letzte 30 Tage bis $bis, verglichen mit den Käufen der Firma seit $seit"
    override val konTitel = "Gleichzeitige Käufe"
    override val kvNav = "Wo Insider, Kongress und Fonds zugleich kaufen"
    override fun kvErklaerung(bis: String, quartal: String) = "Aktien, bei denen mindestens zwei von drei Gruppen kaufen: Insider und Kongress in den 90 Tagen bis $bis, Fonds netto im Quartal $quartal (13F)."
    override val kvDrei = "Alle drei Gruppen"
    override val kvZwei = "Zwei Gruppen"
    override val kvInsider = "Insider"
    override val kvKongress = "Kongress"
    override val kvFonds = "Fonds"
    override fun kvInsiderZahl(n: Int) = "$n Insider"
    override fun kvKongressZahl(n: Int) = if (n == 1) "1 Abgeordneter" else "$n Abgeordnete"
    override fun kvFondsZahl(n: String) = "$n Netto-Käufer"
    override fun skKern(n: Int) = if (n == 1) "1 Wert" else "$n Werte"
    override val kvLeer = "Gerade keine Aktie mit zwei Gruppen"
    override fun kvWert(insider: Boolean, kongress: Boolean, fonds: Boolean) =
        listOfNotNull("Insider".takeIf { insider }, "Kongress".takeIf { kongress }, "Fonds".takeIf { fonds }).let { g -> g.dropLast(1).joinToString(", ") + (if (g.size > 1) " und " else "") + g.last() } + " kaufen zugleich"
    override fun wsMindest(n: Int) = "Mind. $n Insider"
    override fun wsFenster(tage: Int) = "In $tage Tagen"
    override fun wsBetrag(je: Int, dollar: String) = when (je) { 0 -> "Jeder Betrag"; 1 -> "Kauf ab $dollar"; else -> "Cluster ab $dollar" }
    override fun wsRolle(nurFuehrung: Boolean) = if (nurFuehrung) "Nur Führung" else "Alle Rollen"
    override fun wsHalten(tage: Int) = "$tage Handelstage halten"
    override fun wsKosten(prozent: String) = if (prozent == "0") "Ohne Kosten" else "$prozent % Kosten"
    override val wsStandard = "Standardregel"
    override val wsKeine = "Mit diesen Regeln gibt es keine auswertbaren Cluster"
    override fun wsHinweis(seit: String, kosten: Boolean) = "Gerechnet aus den Meldungen seit $seit" + if (kosten) ", Kosten nur bei den Aktien" else ""
    override val guideKonvergenz = "Unter „Bilanz im Rückblick“ prüft der Insider-Rückblick mit eigenen Regeln, ob Cluster-Käufe (mehrere Führungskräfte einer Firma kaufen binnen weniger Tage eigene Aktien) den S&P 500 geschlagen haben. Seltene Käufe und die Liste „Überschneidungen“ zeigen, wo Insider, Kongress und Fonds zugleich kaufen."

    // 0.77 Warnzeichen und Übernahmen aus den 8-K-Meldungen
    override val achtInfoWarn = "Meldungen der Firma bei der SEC (Form 8-K) aus den letzten 24 Monaten."
    override val achtInfoDeal = "Meldungen und Fusionsunterlagen der Firma bei der SEC aus den letzten 24 Monaten.\n\n• Gegenseite und Preis stehen nur, wenn das Dokument sie eindeutig nennt.\n• Kurs: Veränderung zum Schlusskurs des Vortags."
    override fun achtWarnTitel(n: Int) = if (n == 1) "1 Warnzeichen in 24 Monaten" else "$n Warnzeichen in 24 Monaten"
    override fun achtDealTitel(n: Int) = if (n == 1) "1 Übernahme in 24 Monaten" else "$n Übernahmen in 24 Monaten"
    override fun achtArt(key: String) = when (key) {
        "PRUEFER" -> "Wechsel des Abschlussprüfers"
        "KORREKTUR" -> "Abschlüsse nicht mehr verlässlich"
        "FUEHRUNG" -> "Wechsel in der Führung"
        "DELISTING" -> "Delisting-Hinweis oder Regelverstoß"
        "INSOLVENZ" -> "Insolvenz"
        "ABSCHREIBUNG" -> "Wesentliche Abschreibung"
        "VERBINDLICHKEIT" -> "Vorzeitig fällige Verbindlichkeit"
        "ANGEKUENDIGT" -> "Übernahme angekündigt"
        "ANGEBOT" -> "Übernahmeangebot"
        "ABGESCHLOSSEN" -> "Übernahme abgeschlossen"
        "ANGEDROHT" -> "Delisting angedroht"
        "REGEL" -> "Börsenregel nicht erfüllt"
        "BESCHLOSSEN" -> "Delisting beschlossen"
        "WECHSEL" -> "Börsenwechsel"
        "ENTWARNUNG" -> "Wieder regelkonform"
        else -> key
    }
    override fun achtErkl(key: String) = when (key) {
        "PRUEFER" -> "Die Firma hat den Prüfer ihrer Bilanz gewechselt. Meist Routine, nach Streit oder Zweifeln ein Warnzeichen."
        "KORREKTUR" -> "Frühere Abschlüsse waren fehlerhaft und dürfen nicht mehr verwendet werden. Die Firma muss sie korrigieren."
        "FUEHRUNG" -> "CEO oder CFO verlässt die Firma. Plötzliche Abgänge gelten als Warnzeichen."
        "DELISTING" -> "Die Börse meldet einen Verstoß gegen ihre Regeln oder das Ende der Notierung (Delisting)."
        "INSOLVENZ" -> "Die Firma hat Insolvenz oder Gläubigerschutz beantragt."
        "ABSCHREIBUNG" -> "Ein wichtiger Vermögenswert ist weniger wert als gedacht und wird abgeschrieben."
        "VERBINDLICHKEIT" -> "Eine Schuld wird früher fällig als geplant, etwa nach einem Verstoß gegen Kreditbedingungen."
        "ANGEKUENDIGT" -> "Die Firma hat einen Vertrag über eine Übernahme oder Fusion geschlossen. Der Vollzug steht noch aus."
        "ANGEBOT" -> "Ein Käufer bietet den Aktionären an, ihre Aktien direkt zu einem festen Preis zu kaufen."
        "ABGESCHLOSSEN" -> "Der Kauf oder die Fusion ist vollzogen."
        "ANGEDROHT" -> "Die Börse droht mit der Streichung oder setzt eine Frist, um die Regeln wieder zu erfüllen."
        "REGEL" -> "Die Firma erfüllt eine Regel der Börse nicht. Die Börse hat weder gestrichen noch eine Frist genannt."
        "BESCHLOSSEN" -> "Die Börse hat die Streichung der Aktie beschlossen."
        "WECHSEL" -> "Die Firma wechselt freiwillig den Handelsplatz oder das Börsensegment. Kein Warnzeichen, zählt nicht mit."
        "ENTWARNUNG" -> "Die Firma erfüllt die Regel wieder. Kein Warnzeichen, zählt nicht mit."
        else -> ""
    }
    override fun achtRolle(rolle: String) = when (rolle) {
        "CEO" -> "Abgang des CEO"
        "CFO" -> "Abgang des CFO"
        "CEO_CFO" -> "Abgang von CEO und CFO"
        else -> "Wechsel in der Führung"
    }
    override fun achtInsider(n: Int) = if (n == 1) "1 Insider verkaufte in den 60 Tagen davor" else "$n Insider verkauften in den 60 Tagen davor"
    override fun achtGegenseite(name: String) = "Gegenseite: $name"
    override fun achtPreis(preis: String) = "$preis je Aktie"
    override val achtKursTag = "Kurs am Meldetag"
    override val achtKurs5 = "nach 5 Handelstagen"
    override fun achtGrund(key: String, zusatz: String) = when (key) {
        "BERICHT" -> "Bericht nicht fristgerecht eingereicht" + (if (zusatz.isEmpty()) "" else ": $zusatz")
        "KURS" -> "Kurs 30 Handelstage unter 1 $"
        "EIGENKAPITAL" -> "Eigenkapital oder Börsenwert unter der Mindestgrenze"
        "AUSSCHUSS" -> "Zu wenige unabhängige Mitglieder in Prüfungsausschuss oder Verwaltungsrat"
        "VERSAMMLUNG" -> "Hauptversammlung nicht abgehalten"
        else -> ""
    }
    override fun achtBericht(form: String, zeitraum: String) = if (zeitraum.isEmpty()) form else "$form für $zeitraum"
    override fun achtFristDatum(datum: String) = "Frist bis $datum"
    override fun achtFristTage(tage: Int) = "Frist: $tage Tage"
    override fun achtFuehrungDetail(person: String, ereignis: String, sofort: Boolean, wirksam: String, nachfolge: Boolean?): String {
        val art = when (ereignis) { "RUECKTRITT" -> "Rücktritt"; "ENTLASSUNG" -> "Entlassung"; else -> "Abgang" }
        val teile = listOfNotNull(
            art, if (sofort) "mit sofortiger Wirkung" else if (wirksam.isNotEmpty()) "zum $wirksam" else null,
            when (nachfolge) { true -> "Nachfolge genannt"; false -> "keine Nachfolge genannt"; null -> null },
        )
        return (if (person.isEmpty()) "" else "$person: ") + teile.joinToString(", ")
    }
    override fun achtPrueferDetail(pruefer: String, neu: String, ereignis: String, streit: Boolean, maengel: Boolean): String {
        val kopf = when (ereignis) {
            "PRUEFER_RUECKTRITT" -> if (pruefer.isEmpty()) "Prüfer ist zurückgetreten" else "$pruefer ist als Prüfer zurückgetreten"
            "PRUEFER_ENTLASSUNG" -> if (pruefer.isEmpty()) "Firma hat den Prüfer entlassen" else "Firma hat $pruefer als Prüfer entlassen"
            else -> if (pruefer.isEmpty()) "Neuer Prüfer bestellt" else "$pruefer als neuer Prüfer bestellt"
        }
        val teile = listOfNotNull(
            kopf, if (neu.isNotEmpty()) "neuer Prüfer: $neu" else null,
            if (streit) "Meinungsverschiedenheiten genannt" else null, if (maengel) "Mängel der Kontrollen genannt" else null,
        )
        return teile.joinToString(", ")
    }
    override fun achtKorrekturDetail(von: Int, bis: Int) = if (von == bis) "Betrifft Abschlüsse von $von" else "Betrifft Abschlüsse von $von bis $bis"
    override fun achtInsolvenzDetail(kapitel: Int) = when (kapitel) {
        11 -> "Antrag nach Chapter 11 (Sanierung unter Gläubigerschutz)"
        7 -> "Antrag nach Chapter 7 (Liquidation)"
        else -> "Antrag nach Chapter $kapitel"
    }
    override fun achtAbschreibungDetail(betrag: String) = "Abschreibung von $betrag"
    override fun widgetZufluss(betrag: String) = "Zufluss $betrag"
    override fun widgetGekauftStand(quartal: String) = "Quartal bis $quartal"
    override val widgetGekauftLeer = "Keine 13F-Daten"
    override val widgetTrefferanteil = "Trefferanteil"
    override fun widgetKonvergenzStand(bis: String) = "90 Tage bis $bis"
    override fun widgetSeltenStand(bis: String) = "30 Tage bis $bis"
    override val widgetSeltenLeer = "Keine seltenen Käufe"
    override fun widgetSimStand(n: String, jahr: String) = "$n Cluster seit $jahr"
    override val widgetSimLeer = "Keine Rückschau"
    override val widgetFinderUngeladen = "Aktienfinder nicht geladen"
    override val widgetVorschlagLeer = "Keine Aktie erfüllt die Bedingungen"
    override val widgetBrancheLeer = "Zu wenige Werte in der Branche"
    override val widgetWarnTitel = "Warnzeichen"
    override val widgetWarnUnter = "Warnzeichen, 24 Monate"
    override val widgetWarnKeine = "Keine Warnzeichen"
    override val widgetWarnOhneKennung = "Kontaktadresse unter Zugänge fehlt"
    override val widgetWarnNurUs = "Nur US-Aktien"
    override val widgetWarnFehler = "Meldungen nicht abrufbar"
    override val widgetPraesidialLeer = "Keine Dokumente"
    override val widgetBrancheBeste = "Stärkste Branche"
    override fun widgetFonds(n: String) = "$n Fonds"
    override fun widgetPunkte(wert: String) = "$wert Pkt."
    override val widgetSimVorsprung = "Vorsprung"
    override val widgetSimFaelle = "Fälle"
    override fun widgetSimSeit(jahr: String) = "seit $jahr"
    override val widgetSimSchlechtester = "Schlechtester"
    override val orErwartung = "Erwartung"
    override fun orErw(key: String) = when (key) {
        "STEIGT" -> "Steigt"; "FAELLT" -> "Fällt"; "SEITWAERTS" -> "Seitwärts"; "ABSICHERN" -> "Absichern"; else -> "Starke Bewegung"
    }
    override val orAnpassen = "Anpassen"
    override val orGekauft = "Strategie im Depot"
    override val orWertAm = "Stichtag"
    override val orBerechnet = "berechnet"
    override val orDatumWahl = "Datum"
    override val orSumme = "Summe"
    override fun orKaufHint(stueck: String, preis: String, tag: String) = "$stueck Stück zu $preis" + if (tag.isBlank()) "" else " · $tag"
    override fun orKlar(key: String, a: String, b: String, c: String) = when (key) {
        "LONG_CALL" -> "Call kaufen: Gewinn über $a"
        "LONG_PUT" -> "Put kaufen: Gewinn unter $a"
        "COVERED_CALL" -> "Aktie halten und Call verkaufen: $a Einnahme, Gewinn begrenzt ab $b"
        "CASH_SECURED_PUT" -> "Put verkaufen: $a Einnahme, Verlust unter $b"
        "BULL_CALL_SPREAD" -> "Call kaufen und höheren Call verkaufen: Gewinn über $a, höchstens $b"
        "BEAR_PUT_SPREAD" -> "Put kaufen und tieferen Put verkaufen: Gewinn unter $a, höchstens $b"
        "STRADDLE" -> "Call und Put kaufen: Gewinn über $a oder unter $b"
        "STRANGLE" -> "Call und Put kaufen, beide mit Abstand zum Kurs: Gewinn über $a oder unter $b"
        "IRON_CONDOR" -> "Calls und Puts verkaufen, außen abgesichert: $a Einnahme, Gewinn zwischen $b und $c"
        "BUTTERFLY" -> "Calls um die Mitte des Kurses: Gewinn zwischen $a und $b, höchstens $c"
        "COLLAR" -> "Aktie halten, Put kaufen, Call verkaufen: Verlust begrenzt unter $a, Gewinn begrenzt ab $b"
        "SCHUTZ_PUT" -> "Aktie halten und Put kaufen: Verlust begrenzt unter $a, Schutz kostet $b"
        else -> ""
    }
    override fun orKernKosten(b: String) = "Einsatz $b."
    override fun orKernEinnahme(b: String) = "Einnahme $b."
    override fun orKernGewinnUeber(name: String, tag: String, a: String, pct: String) = "Gewinn, wenn $name am $tag über $a steht ($pct)."
    override fun orKernGewinnUnter(name: String, tag: String, a: String, pct: String) = "Gewinn, wenn $name am $tag unter $a steht ($pct)."
    override fun orKernGewinnZwischen(name: String, tag: String, a: String, b: String) = "Gewinn, wenn $name am $tag zwischen $a und $b steht."
    override fun orKernGewinnAusserhalb(name: String, tag: String, a: String, b: String) = "Gewinn, wenn $name am $tag außerhalb von $a und $b steht."
    override val orKernGewinnImmer = "Gewinn bei jedem Kurs."
    override val orKernGewinnNie = "Kein Gewinn möglich."
    override fun orKernMaxGewinn(b: String) = "Größter Gewinn $b."
    override fun orKernMaxVerlust(b: String) = "Größter Verlust $b."
    override val orKernVerlustUnbegrenzt = "Verlust unbegrenzt."
    override fun orErkl(key: String) = when (key) {
        "basispreis" -> "Preis, zu dem die Option die Aktie kaufen (Call) oder verkaufen (Put) lässt. Er legt fest, ab welchem Kurs die Option etwas wert ist."
        "gewinnschwelle" -> "Kurs am Verfallstag, bei dem Gewinn und Verlust genau null sind."
        "chance" -> "Geschätzte Wahrscheinlichkeit, dass die Strategie bei Verfall im Gewinn liegt. Grundlage ist die Schwankung, die der Markt in den Optionspreisen erwartet."
        "delta" -> "So viele Dollar gewinnt oder verliert die Position, wenn die Aktie um 1 Dollar steigt."
        "gamma" -> "So stark ändert sich das Delta, wenn die Aktie um 1 Dollar steigt."
        "theta" -> "So viele Dollar verliert oder gewinnt die Position an einem Tag allein durch den Zeitablauf."
        "vega" -> "So viele Dollar ändert sich der Wert der Position, wenn die erwartete Schwankung um 1 Prozentpunkt steigt."
        else -> "Implizite Volatilität: wie stark der Markt den Kurs in einem Jahr schwanken sieht, in Prozent. Höhere Werte machen Optionen teurer."
    }
    override fun widgetGrundName(schluessel: String, name: String) = when (schluessel) {
        "zins" -> "Fed Funds laut Futures"
        "fed" -> "Fed-Zinsentscheid"
        "fed_jahr" -> "Fed-Senkungen im Jahr"
        "ezb" -> "EZB-Zinsentscheid"
        "spr" -> "Ölreserve USA"
        "erstantraege" -> "Arbeitslosenanträge USA"
        "lieferketten" -> "Lieferketten-Druck"
        "bahn" -> "Güterwagen US-Bahnen"
        "halbleiter" -> "Chipproduktion USA"
        "wsts" -> "Chipumsatz weltweit"
        else -> name
    }
    override fun widgetLageName(key: String, zusatz: String) = when (key) {
        "ENGPASS_GESTOERT" -> "Gestörte Engpässe"
        "SPANNUNG" -> "Spannungsgebiete"
        "DURCHBRUCH_NEU" -> "Neue Durchbrüche, 7 Tage"
        "DURCHBRUCH_THEMEN" -> "Themen mit Durchbrüchen"
        else -> lbName(key, zusatz).trim()
    }
    override fun widgetCheckName(regel: String) = when (regel) {
        "FONDSKOSTEN" -> "Fondskosten"
        "TER_GESTIEGEN" -> "Teurere Fonds"
        "GROESSTE" -> "Größte Position"
        "ANZAHL" -> "Streuung"
        "BRANCHE" -> "Größte Branche"
        "WAEHRUNG" -> "Fremdwährung"
        "HEBEL" -> "Hebelprodukte"
        "KRYPTO" -> "Krypto"
        "KOSTEN" -> "Orderkosten"
        "RESERVE" -> "Reserve"
        "SPARPLAN" -> "Sparplan"
        "VERLUSTE" -> "Tiefe Verluste"
        else -> regel
    }
    override fun widgetBlasenName(schluessel: String, name: String) = when (schluessel) {
        "trend" -> "Überdehnung 200 Tage"
        "konzentration" -> "Top-10-Gewicht"
        "gewinn" -> "Gewinnwachstum"
        else -> name
    }
    override val widgetStufeGut = "Gut"
    override val widgetKeineDaten = "Gerade keine Daten"
    override val widgetZuWenigPositionen = "Ab zwei Positionen"

    // Glocke je Wertpapier (0.77)
    override fun glockeGruppe(gruppe: String) = when (gruppe) {
        "KURS" -> "Kurs"; "NACHRICHTEN" -> "Nachrichten"; "INSIDER" -> "Insider"; "SIGNALE" -> "Analysten und Regeln"; "TERMINE" -> "Termine"; else -> "Warnzeichen"
    }
    override fun glockeArt(art: String) = when (art) {
        "SMA50" -> "50-Tage-Schnitt gekreuzt"; "VOL2" -> "Doppeltes Volumen"; "W52" -> "52-Wochen-Hoch oder -Tief"
        "NEWS" -> "Wichtige Meldungen"; "INSIDER" -> "Insiderkauf"; "INSIDERV" -> "Insiderverkauf"
        "ANALYST" -> "Hoch- oder Herabstufung"; "QUARTAL" -> "Quartalszahlen"; "EXDIV" -> "Ex-Tag der Dividende"; else -> StrategieTexte.glocke(this, true, art) ?: "Neue 8-K-Meldung"
    }
    override fun glockeArtKurz(art: String) = when (art) {
        "SMA50" -> "50-Tage-Schnitt"; "VOL2" -> "Volumen"; "W52" -> "52-Wochen-Marke"
        "NEWS" -> "Nachrichten"; "INSIDER" -> "Insiderkauf"; "INSIDERV" -> "Insiderverkauf"
        "ANALYST" -> "Analysten"; "QUARTAL" -> "Quartalszahlen"; "EXDIV" -> "Ex-Tag"; else -> StrategieTexte.glocke(this, true, art) ?: "8-K"
    }
    override val glockeAlle = "Alle"
    override val glockePreismarken = "Preismarken"
    override val glockeNeueMarke = "Neue Preismarke"
    override fun glockeBewegung(pct: String?) = if (pct == null) "Tagesbewegung" else "Tagesbewegung ab $pct"
    override fun glockeMarkeKurz(preis: String) = "Preismarke $preis"
    override fun glockeMarkenKurz(n: Int) = "$n Preismarken"
    override fun glockeBewegungKurz(pct: String) = "Bewegung ab $pct"
    override val glockeListe = "Glocken"
    override val glockeLeer = "Keine Glocken gesetzt"
    override val glockeAus = "Glocke ausschalten"
    override fun alertNeuText(tag: String, extra: String, price: String) = when (tag) {
        "ALERT_NEWS" -> extra
        "ALERT_INSIDER_SELL" -> "Insiderverkauf über $extra · Kurs $price"
        "ALERT_HIGH52" -> "52-Wochen-Hoch $extra · Kurs $price"
        "ALERT_LOW52" -> "52-Wochen-Tief $extra · Kurs $price"
        "ALERT_ANALYST_UP" -> "Hochgestuft: $extra · Kurs $price"
        "ALERT_ANALYST_DOWN" -> "Herabgestuft: $extra · Kurs $price"
        "ALERT_EARNINGS" -> "Quartalszahlen am $extra · Kurs $price"
        "ALERT_RULE_BUY", "ALERT_RULE_SELL" -> StrategieTexte.signal(this, true, tag, extra, price)
        else -> "Neue 8-K-Warnzeichen: $extra · Kurs $price"
    }
    override fun glockeWeitere(n: Int) = if (n == 1) "1 weitere" else "$n weitere"
    override val shVerkauft = "Verkauft"
    override fun shGeschaeft(eroeffnung: Boolean) = if (eroeffnung) "Verkauf zur Eröffnung" else "Rückkauf"
    override val shAusgleichJeStueck = "Ausgleich je Stück"
    override val shStillhalterErtraege = "Verkaufte Optionen"
    override fun shRueckkaufDone(qty: String, price: String) = "Rückkauf ausgeführt: $qty × $price"
    override val shGruppeGeschlossen = "Strategie geschlossen"
    override fun shAktienGebunden(n: String) = "$n Stück decken verkaufte Calls"
    override fun shVerfallText(name: String, betrag: String, wertlos: Boolean) =
        if (wertlos) "$name wertlos verfallen, Bindung frei" else "$name: $betrag Ausgleich gezahlt"
    override fun shGebundenZeile(betrag: String) = "$betrag für verkaufte Optionen gebunden"
    override fun shFreiZeile(betrag: String) = "$betrag verfügbar"
    override val shPraemie = "Erhaltene Prämie"
    override fun shJeStueck(preis: String) = "$preis je Stück"
    override val shRueckkaufpreis = "Rückkaufpreis"
    override val shBindung = "Gebundenes Guthaben"
    override val shGebundeneAktien = "Gebundene Aktien"
    override val shStrategie = "Strategie"
    override val shGruppeSchliessen = "Strategie schließen"
    override val shZurueckkaufen = "Zurückkaufen"
    override fun shNurGebunden(n: String) = "$n aus dem Depot"
    override val orAusfuehren = "Ausführen"
    override val anTitel = "Selbst anstoßen"
    override val anUntertitel = "Meldungen, Aktienfinder, Lager, Importe"
    override val anGruppeLaden = "Vollständig laden"
    override val anGruppeDateien = "Dateien einlesen"
    override val anGruppeBrowser = "Nur im Browser"
    override val anMeldungen = "Meldungen der Abgeordneten"
    override val anKongress = "Kongress-Käufe mit Kursen"
    override val anFinder = "Aktienfinder-Kennzahlen"
    override val anRadar = "Aktien-Radar"
    override val anNie = "noch nicht geladen"
    override val anKeineDatei = "noch keine Datei eingelesen"
    override val anAlle = "alle gelesen"
    override val anHeute = "heute erstellt"
    override val anHeuteNie = "heute noch nicht erstellt"
    override fun anLaeuft(prozent: Int?) = if (prozent == null) "läuft" else "läuft $prozent %"
    override fun anAm(datum: String) = "geladen am $datum"
    override fun anBis(datum: String) = "geladen bis $datum"
    override fun anMonate(n: Int) = if (n == 1) "1 Monat eingelesen" else "$n Monate eingelesen"
    override fun anOffen(n: Int) = "${de.troetenrepublik.app.ui.Fmt.num(n.toDouble(), 0)} offen"
    override fun afRueckblickAbdeckung(schnitt: String, jahre: List<Pair<String, String>>, voll: Boolean) =
        "• Gemessen je Stichtag: im Schnitt $schnitt der damaligen Mitglieder" +
            (if (jahre.isEmpty()) "." else " (" + jahre.joinToString(", ") { (j, a) -> "$j: $a" } + ").") +
            (if (voll) "" else " Zu den übrigen fehlen Kurse, das schönt das Ergebnis.")
    // [Laender Historie 0.78]
    override val histTitel = "Seit 1870"
    override val histAktien = "Aktien"
    override val histAnleihen = "Anleihen"
    override val histImmobilien = "Immobilien"
    override val histZins = "Zins kurzfristig"
    override fun histReal(titel: String) = "$titel real"
    override fun histJeJahrAb(jahr: Int) = "pro Jahr ab $jahr"
    override val histKernInfo = "Durchschnittliche Rendite pro Jahr nach Abzug der Inflation über alle Jahre mit Daten, mit Dividenden, Zinsen und Mieten. Die Reihen beginnen je nach Anlageklasse in verschiedenen Jahren."
    override val histOhneRenditen = "Die Quelle hat für dieses Land keine Renditen"
    override fun histLueckeInfo(von: Int, bis: Int) = "Für $von bis $bis stehen keine Werte da (Hyperinflation)."
    override val histVermoegen = "Reales Vermögen, logarithmisch"
    override fun histKrisen(n: Int) = "Finanzkrisen ($n)"
    override val histKrisenInfo = "Krisenjahre laut Quelle, im Verlauf als gestrichelte Linien."
    override val histSchlecht = "Schlechteste Zehnjahre, Aktien"
    override val histSchlechtInfo = "Reale Rendite pro Jahr über zehn aufeinanderfolgende Jahre. Gezeigt sind die drei schlechtesten Zeiträume ohne gemeinsame Jahre."
    override fun histZeitraum(von: Int, bis: Int) = "$von bis $bis"
    override fun histJe(wert: String) = "$wert pro Jahr"
    override val histInflation = "Inflation pro Jahr"
    override fun histInflationInfo(ab: Int) = "Durchschnittliche Preissteigerung pro Jahr seit $ab."
    override fun histKredit(jahr: Int) = "Kredit zu BIP $jahr"
    override val histKreditInfo = "Kredite an den privaten Sektor ohne Finanzfirmen im Verhältnis zur Wirtschaftsleistung, letztes Jahr mit Wert."
    override val histZitatKopf = "Quelle: Jordà-Schularick-Taylor Macrohistory Database, www.macrohistory.net/database. Zu zitieren als:"
    override val histLizenzJst = "Lizenz CC BY-NC-SA 4.0: nur nicht kommerzielle Nutzung, Namensnennung, Weitergabe nur unter gleichen Bedingungen. Die Zahlen sind aus der Datenbank umgerechnet (reale Renditen)."
    override fun histAbruf(datum: String) = "Abgerufen am $datum."
    override fun hpTitel(ab: Int) = "Hauspreise real seit $ab"
    override val hpPreise = "Preise real"
    override val hpVerhaeltnis = "Preis zu Einkommen"
    override fun hpSeit(ab: Int) = "seit $ab"
    override fun hpSchnitt(abweichung: Int, ab: Int) = (if (abweichung > 0) "über" else if (abweichung < 0) "unter" else "auf") + " dem Schnitt seit $ab"
    override fun hpInfo(bis: String) = "Reale Hauspreise und reale verfügbare Einkommen je Land, mit dem Deflator der Konsumausgaben (PCE) bereinigt.\n\n• Preis zu Einkommen: reale Hauspreise geteilt durch reale Einkommen, letzter Stand gegen den Mittelwert aller Quartale seit Beginn der Reihe.\n• Stand bis $bis. Wo Beobachtungen fehlen, ergänzt die Quelle mit eigenen Schätzungen."
    override fun hpQuartal(jahr: Int, quartal: Int) = "$jahr Q$quartal"
    override fun hpVerlauf(ab: Int) = "Preise und Einkommen real, $ab = 100"
    override val hpPreisLinie = "Preise"
    override val hpEinkommenLinie = "Einkommen"
    override val hpZitatKopf = "Quelle: Federal Reserve Bank of Dallas, International House Price Database, www.dallasfed.org/research/international/houseprice. Zu zitieren als:"
    override val hpNutzung = "Nutzung nicht zu privatem Gewinn, mit Nennung der Bank."
    override val globusHaus = "Hauspreise zu Einkommen"
    override val globusAktien1870 = "Aktien real seit 1870"
    override val globusAktien1950 = "Aktien real seit 1950"
    override val globusLegHaus = "Verhältnis Preis zu Einkommen gegen den eigenen Schnitt seit 1975. Blau darunter, ocker darüber."
    override val globusLegAktien1870 = "Reale Rendite pro Jahr, Länder der Quelle. Jedes Land ab seinem ersten Jahr mit Daten."
    override val globusLegAktien1950 = "Reale Rendite pro Jahr seit 1950, Länder mit Renditen in der Quelle."
    override val rzBand = "Rezession USA"
    override fun rzBandAb(jahr: Int) = "Rezessionen USA ab $jahr"
    override val nyName = "Rezession in 12 Monaten"
    override fun nySatz(stufe: Int, monat: String) =
        "Rezession in den USA im $monat " + when (stufe) { 0 -> "unwahrscheinlich"; 1 -> "möglich"; else -> "wahrscheinlich" }
    override fun nyBilanz(schwelle: String, treffer: Int, von: Int, fehlalarme: Int, abJahr: Int) =
        "Über $schwelle % lag sie vor $treffer von $von Rezessionen seit $abJahr, $fehlalarme-mal ohne anschließende Rezession"
    override fun nyStand(monat: String) = "Zinskurve bis $monat"
    override val nyErklaerung = "Die New York Fed rechnet aus dem Abstand zwischen zehnjährigen und dreimonatigen US-Staatsanleihen, " +
        "wie wahrscheinlich die USA in zwölf Monaten in einer Rezession sind. Liegt der kurze Zins über dem langen, steigt der Wert. " +
        "Die Linie zeigt den Wert, den man jeweils sah, die grauen Flächen die Rezessionen nach dem NBER."
    override val ensoKernNeutral = "Keine Phase aktiv, kein typischer Einfluss auf Rohstoffpreise"
    override fun ensoKern(hoch: String, tief: String) = "Bullish für " + hoch + if (tief.isEmpty()) "" else "\nBearish für " + tief
    override val ensoKurz = "El Niño: Der Pazifik am Äquator ist wärmer als üblich, La Niña: kälter. Beide verschieben über Monate Regen und Dürre und damit Ernten und Heizbedarf."
    override val ensoBeiNino = "Bei El Niño"
    override val ensoBeiNina = "Bei La Niña"
    override val ensoMuster = "Typische Muster früherer Ereignisse, bezogen auf den Preis. Jedes Ereignis verläuft anders."
    override val ensoBullish = "bullish"
    override val ensoBearish = "bearish"
    override fun ensoWirkName(k: String) = when (k) {
        "palmoel" -> "Palmöl"; "zucker" -> "Zucker"; "kakao" -> "Kakao"; "kaffee" -> "Kaffee"; "reis" -> "Reis"; "weizen" -> "Weizen"
        "soja" -> "Soja"; "mais" -> "Mais"; "erdgas" -> "US-Erdgas"; "kohle" -> "Kohle"; else -> k
    }
    override fun ensoWirkGrund(phase: Int, k: String) = if (phase > 0) when (k) {
        "palmoel" -> "Dürre in Indonesien und Malaysia senkt die Erträge"
        "zucker" -> "Schwacher Monsun in Indien, Trockenheit in Thailand"
        "kakao" -> "Trockenheit in Westafrika"
        "kaffee" -> "Dürre in Vietnam und Indonesien trifft Robusta"
        "reis" -> "Schwacher Monsun in Süd- und Südostasien"
        "weizen" -> "Dürre in Australien"
        "soja" -> "Mehr Regen in Argentinien und Südbrasilien, größere Ernten"
        "erdgas" -> "Milderer Winter im Norden der USA, weniger Heizbedarf"
        else -> ""
    } else when (k) {
        "soja" -> "Dürre in Argentinien und Südbrasilien"
        "mais" -> "Dürre in Argentinien und Südbrasilien"
        "weizen" -> "Trockenheit im Süden der USA und in Argentinien"
        "erdgas" -> "Kälterer Winter im Norden der USA, mehr Heizbedarf"
        "kohle" -> "Starkregen in Ostaustralien stört Förderung und Verladung"
        else -> ""
    }
    override val tlTitel = "Branchen und Themen"
    override val tlMedian = "Median der Aktien"
    override val tlBeispiele = "Beispiele"
    override fun tlAktien(n: Int) = if (n == 1) "1 Aktie" else "$n Aktien"
    override fun tlNeu(n: Int) = if (n == 1) "1 neuer Durchbruch" else "$n neue Durchbrüche"
    override fun cotSeit(hoch: Boolean, long: Boolean, seit: String, rekord: Boolean): String {
        val was = when {
            hoch && long -> "So stark netto long"
            hoch -> "So wenig netto short"
            long -> "So wenig netto long"
            else -> "So stark netto short"
        }
        return if (rekord) "$was wie nie seit $seit" else "$was wie seit $seit nicht"
    }
    override fun cotRang(rang: Int, abJahr: Int) = "Seit $abJahr lagen $rang % der Wochen gleich oder tiefer."
    override fun cotWeitere(n: Int) = "$n weitere Märkte"
    override fun cotWeitereUnter(auffaellig: Int) = "Anleihen, Währungen, Metalle, Energie, Agrar" + if (auffaellig > 0) " · $auffaellig einseitig" else ""
    override fun standSeit(hoch: Boolean, seit: String, rekord: Boolean) =
        (if (hoch) "Höchster" else "Tiefster") + " Stand seit " + (if (rekord) "Beginn der Reihe $seit" else seit)
    override val vmTitel = "Muster der Vergangenheit"
    override fun vmIndex(id: String) = when (id) { "sp500" -> "S&P 500"; "dax" -> "DAX"; else -> id }
    override fun vmZyklusName(jahr: Int) = when (jahr) {
        1 -> "Jahr nach der US-Wahl"; 2 -> "Zwischenwahljahr der USA"; 3 -> "Jahr vor der US-Wahl"; else -> "US-Wahljahr"
    }
    override fun vmZyklusKurz(jahr: Int) = if (jahr == 4) "Wahljahr" else "Jahr $jahr"
    override fun vmZyklusSatz(positiv: Int, anzahl: Int, jahre: Int, von: Int) =
        "S&P 500 im Mittel · im Plus in $positiv von $anzahl Jahren · $jahre Jahre seit $von"
    override fun vmBalkenKopf(name: String, mittel: String, positiv: Int, anzahl: Int) = "$name · $mittel · $positiv von $anzahl im Plus"
    override fun vmGrundlage(id: String, von: Int, bis: Int) = (if (id == "dax") "DAX mit Dividenden" else "S&P 500 ohne Dividenden") + " · $von bis $bis"
    override fun vmMonat(monat: Int) = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember",
    ).getOrElse(monat - 1) { "" }
    override fun vmSaisonName(index: String, monat: String) = "$index im $monat"
    override fun vmSaisonSatz(positiv: Int, anzahl: Int, von: Int) = "Im Mittel · im Plus in $positiv von $anzahl Jahren seit $von"
    override val vmBaerName = "S&P 500 unter dem Hoch"
    override fun vmBaerSatz(n: Int, von: Int, tiefe: String, monate: Int) =
        "$n Rückgänge ab 20 % seit $von · im Mittel −$tiefe %, $monate Monate bis zum Tief"
    override val vmBaerBeginn = "Beginn"
    override val vmBaerTiefe = "Tiefe"
    override val vmBaerTief = "Tief"
    override val vmBaerErholt = "Erholt"
    override val vmBaerJetzt = "Jetzt"
    override val vmBaerOffen = "offen"
    override val vmBaerEinheit = "Tief und Erholt: Monate seit dem Hoch"
    // [0.78] Historische Annahmen der Rechner, Messung des Rezessionssignals und des Kreditanstiegs
    override val hrAnnahmen = "Annahmen"
    override val hrEigene = "Eigene"
    override val hrWelt = "Historisch Welt"
    override fun hrLand(name: String) = "Historisch $name"
    override val hrWeltName = "Welt"
    override val hrRendite = "Rendite real pro Jahr"
    override fun hrSpanne(name: String, von: Int, bis: Int) = "Aktien $name, $von bis $bis"
    override fun hrSchlecht(jahre: Int) = if (jahre == 1) "Schlechtestes Jahr" else "Schlechteste $jahre Jahre"
    override fun hrBest(jahre: Int) = if (jahre == 1) "Bestes Jahr" else "Beste $jahre Jahre"
    override fun hrZeitraumReal(von: Int, bis: Int) = if (von == bis) "$von, real" else "$von bis $bis, real pro Jahr"
    override val hrVerlaeufe = "Verläufe"
    override val hrZufall = "Zufall"
    override val hrFolgen = "Historische Folgen"
    override val hrStartjahre = "Startjahre"
    override fun hrSchlechtStart(jahr: Int) = "Schlechtester Start $jahr"
    override fun hrBestStart(jahr: Int) = "Bester Start $jahr"
    override fun hrVon(n: Int, von: Int) = "in $n von $von"
    override fun hrReichte(n: Int, von: Int, jahre: String) = "Das Geld reichte bei $n von $von Startjahren $jahre lang."
    override fun hrLeerNach(jahre: Int) = if (jahre == 1) "leer nach 1 Jahr" else "leer nach $jahre Jahren"
    override fun hrNoteZufall(name: String, von: Int, bis: Int) =
        "Rendite und Schwankung aus den realen Jahresrenditen von Aktien, $name, $von bis $bis. Die Verläufe sind zufällig, die Beträge stehen in heutiger Kaufkraft."
    override fun hrNoteFolgen(name: String, von: Int, bis: Int, jahre: Int) =
        "Jedes Startjahr von $von bis $bis läuft einmal mit den realen Jahresrenditen der folgenden $jahre Jahre durch (Aktien, $name). " +
            "Die Zeiträume überlappen sich und sind nicht unabhängig. Die Beträge stehen in heutiger Kaufkraft."
    override fun hrWeltInfo(laender: Int) = "Welt: je Jahr das Mittel aller Länder mit Wert, bis zu $laender."
    override fun hrRateInflation(infl: String) = "Die Sparrate verliert mit $infl Inflation pro Jahr an Kaufkraft."
    override val hrNoteEnde = "Ohne Kosten und Steuern. Vergangene Renditen sagen künftige nicht voraus."
    override fun nyEinbrueche(schwelle: String, treffer: Int, von: Int, abJahr: Int, grund: String) =
        "Vor $treffer von $von Einbrüchen des S&P 500 ab 20 % seit $abJahr lag sie im Jahr davor über $schwelle %. Das galt auch vor $grund % aller Monate."
    override fun hrKreditAnstieg(jahre: Int) = "Änderung in $jahre Jahren"
    override fun hrPunkte(wert: String) = "$wert Punkte"
    override fun hrKreditInfo(schwelle: String, jahre: Int, mitKrise: Int, faelle: Int, quote: String, grund: String, laender: Int, von: Int, bis: Int) =
        "Änderung von Kredit zu BIP in Prozentpunkten gegen den Wert $jahre Jahre davor.\n\n" +
            "Gemessen an $laender Ländern, $von bis $bis: Nach einem Anstieg ab $schwelle Punkten folgte in $mitKrise von $faelle Länderjahren " +
            "binnen $jahre Jahren eine Finanzkrise ($quote %). Über alle Länderjahre waren es $grund %. Aufeinanderfolgende Jahre überlappen sich."
    override fun lbStandJahr(jahr: Int) = "Stand $jahr"
    override fun lbUmgerechnet(waehrung: String) = "Angezeigt in $waehrung, aus US-Dollar zum heutigen Wechselkurs umgerechnet."
    override fun heatmapKgv(wert: String) = "KGV $wert"
    override val lbTreemapHinweisKgv = "Fläche: Börsenwert, Farbe: Änderung heute, dritte Zeile: KGV"
    override val kursOhneLauf = "Kein laufender Kurs"
    override fun kursLetzterStand(datum: String) = "Letzter Stand $datum"
    override fun kursKennzahlenStand(datum: String) = "Kennzahlen und Bewertung zum Stand $datum"
    // [Waehrung und Kaufkraft 0.78]
    override val kkTitel = "Währung und Kaufkraft"
    override val kkKaufkraft = "Kaufkraft"
    override val kkPreisniveau = "Preisniveau"
    override val kkDeutschland = "Deutschland"
    override val kkDollar = "Dollar"
    override val kkEuro = "Euro"
    override fun kkVon(land: String) = "von $land"
    override fun kkZum(code: String) = if (code == "EUR") "Währung zum Euro" else "Währung zum Dollar"
    override val kkUnterKkp = "unter Kaufkraftparität"
    override val kkUeberKkp = "über Kaufkraftparität"
    override val kkAufKkp = "auf Kaufkraftparität"
    override fun kkBasisJahr(land: String, jahr: Int) = "$land = 100 %, $jahr"
    override fun kkKorb(betrag: String) = "Warenkorb für $betrag"
    override fun kkKorbHinweis(land: String, betrag: String) = "Preis hier für das, was in $land $betrag kostet"
    override fun kkReise(betrag: String) = "$betrag Reisekasse"
    override fun kkReiseHinweis(land: String) = "Kaufkraft hier, gemessen an Preisen in $land"
    override val kkBipKopf = "BIP je Kopf"
    override fun kkBipKopfHinweis(jahr: Int) = "zum Wechselkurs, $jahr"
    override val kkBipKaufkraft = "BIP je Kopf nach Kaufkraft"
    override fun kkBipKaufkraftHinweis(land: String) = "in Preisen von $land"
    override fun kkNiveauVerlauf(ab: Int, land: String) = "Preisniveau seit $ab ($land = 100)"
    override fun kkStaerke(waehrung: String) = "Wert der Währung in $waehrung"
    override val kkZeitraum = "Zeitraum"
    override val kkNominal = "nominal"
    override val kkReal = "real"
    override fun kkJahre(n: Int) = if (n == 1) "1 Jahr" else "$n Jahre"
    override fun kkSchnitt(jahr: Int) = "Jahresschnitte bis $jahr."
    override val kkBindung = "Kursbindung"
    override val kkFestDollar = "fest zum Dollar"
    override val kkFestEuro = "fest zum Euro"
    override val kkBindungHinweis = "aus den Kursen von zehn Jahren"
    override val kkReer = "Realer effektiver Kurs"
    override fun kkReerHinweis(jahr: Int) = "gegen den eigenen Schnitt von zehn Jahren, $jahr"
    override val kkReerVerlauf = "Realer effektiver Wechselkurs (2010 = 100)"
    override fun kkInfoNiveau(land: String) = "So teuer ist ein gleicher Warenkorb im Land, gemessen an $land. 58 % heißt: Was in $land 100 kostet, kostet dort 58.\n\n" +
        "Gerechnet aus dem BIP je Kopf des IWF: Wert zum Wechselkurs geteilt durch den kaufkraftbereinigten Wert.\n\n" +
        "Der Warenkorb umfasst die ganze Wirtschaft. Mieten, Restaurants oder Importwaren können stark abweichen."
    override val kkInfoKkp = "Kaufkraftparität ist der Wechselkurs, bei dem ein gleicher Warenkorb in beiden Ländern gleich viel kostet.\n\n" +
        "Minus 31 % heißt: Zum heutigen Kurs ist das Land 31 % billiger als das Vergleichsland. Die Währung wirkt schwächer, als ihre Kaufkraft im Land ist.\n\n" +
        "Ärmere Länder liegen fast immer darunter, weil Dienstleistungen dort billiger sind. Ein Kursziel ist das nicht."
    override fun kkInfoKorb(land: String) = "Beide Zahlen folgen aus dem Preisniveau gegen $land.\n\n" +
        "Warenkorb: der Preis im Land für Waren und Dienste, die in $land den genannten Betrag kosten.\n\n" +
        "Reisekasse: so viel müsste man in $land ausgeben, um dasselbe zu kaufen wie mit dem genannten Betrag im Land.\n\n" +
        "Grundlage ist der Durchschnitt der ganzen Wirtschaft. Touristenorte sind oft teurer."
    override fun kkInfoBip(land: String) = "Zum Wechselkurs: Wirtschaftsleistung je Einwohner, in die Anzeigewährung umgerechnet.\n\n" +
        "Nach Kaufkraft: derselbe Wert in Preisen von $land. Er zeigt, wie viel sich mit der Wirtschaftsleistung je Kopf im Land kaufen lässt, gemessen an $land.\n\n" +
        "Ein Land mit schwacher Währung und niedrigen Preisen steht nach Kaufkraft höher als zum Wechselkurs."
    override val kkInfoStaerke = "Veränderung des Jahresschnitts des Wechselkurses. Plus heißt: Die Währung des Landes hat an Wert gewonnen.\n\n" +
        "Nominal: nur der Kurs.\n\n" +
        "Real: Kurs und Unterschied der Inflation (IWF). Steigen die Preise im Land schneller, wird es für Ausländer teurer, auch wenn der Kurs steht.\n\n" +
        "Über einen Währungsschnitt oder eine neue Währung hinweg steht kein Wert."
    override val kkInfoBindung = "Der Jahresschnitt des Kurses lag zehn Jahre lang in einem Band von 3 %. Das spricht für eine feste Bindung oder eine gemeinsame Währung.\n\n" +
        "Abgeleitet aus den Kursen, keine amtliche Einstufung."
    override val kkInfoReer = "Der reale effektive Wechselkurs misst die Währung gegen die Währungen der Handelspartner, bereinigt um die Preise.\n\n" +
        "Über dem eigenen Schnitt: Waren des Landes sind im Ausland teurer geworden. Darunter: Sie sind billiger geworden.\n\n" +
        "Die Weltbank führt die Reihe für rund 90 Länder."
    override val kkVglBip = "BIP/Kopf"
    override val kkVglKaufkraft = "nach Kaufkraft"
    override fun kkVglUmrechnung(betrag: String, a: String, b: String, wert: String) = "$betrag aus $a haben in $b die Kaufkraft von $wert."
    override val bmTitel = "Big-Mac-Index"
    override val bmPreis = "Preis eines Big Mac"
    override fun bmStandEuroraum(monat: String) = "Euroraum, $monat"
    override val bmUmgerechnet = "Umgerechnet"
    override val bmKursStichtag = "zum Kurs des Stichtags"
    override val bmGegenEuro = "Gegen den Euro"
    override val bmGegenDollar = "Gegen den Dollar"
    override val bmRoh = "roh: Preis gegen Preis"
    override val bmBereinigt = "bereinigt um das BIP je Kopf"
    override fun bmVerlauf(ab: Int) = "Big Mac gegen den Dollar seit $ab"
    override val bmInfo = "The Economist vergleicht halbjährlich den Preis eines Big Mac in gut 50 Ländern und im Euroraum.\n\n" +
        "Roh: Plus heißt, der Big Mac ist umgerechnet teurer als im Vergleichsgebiet. Die Währung gilt dann als überbewertet.\n\n" +
        "Bereinigt: In ärmeren Ländern sind Löhne und damit Burger billiger. Die bereinigte Zahl rechnet das BIP je Kopf heraus.\n\n" +
        "Ein einzelnes Produkt, kein Warenkorb. Länder mit dem Euro stehen gemeinsam als Euroraum.\n\n" +
        "Daten: The Economist, The Big Mac index, Lizenz CC BY 4.0 (creativecommons.org/licenses/by/4.0). Der Preis in Anzeigewährung ist umgerechnet."
    override val rlTitel = "Ranglisten"
    override fun rlName(x: de.troetenrepublik.app.engine.Ranglisten.Index) = when (x) {
        de.troetenrepublik.app.engine.Ranglisten.Index.HDI -> "Menschliche Entwicklung (HDI)"
        de.troetenrepublik.app.engine.Ranglisten.Index.IHDI -> "HDI nach Ungleichheit"
        de.troetenrepublik.app.engine.Ranglisten.Index.GESCHLECHTER -> "Ungleichheit der Geschlechter"
        de.troetenrepublik.app.engine.Ranglisten.Index.GINI -> "Einkommensungleichheit (Gini)"
        de.troetenrepublik.app.engine.Ranglisten.Index.LOGISTIK -> "Logistik (LPI)"
        de.troetenrepublik.app.engine.Ranglisten.Index.HUMANKAPITAL -> "Humankapital"
    }
    override fun rlKurz(x: de.troetenrepublik.app.engine.Ranglisten.Index) = when (x) {
        de.troetenrepublik.app.engine.Ranglisten.Index.HDI -> "Entwicklung"
        de.troetenrepublik.app.engine.Ranglisten.Index.IHDI -> "HDI, Ungleichheit"
        de.troetenrepublik.app.engine.Ranglisten.Index.GESCHLECHTER -> "Geschlechter"
        de.troetenrepublik.app.engine.Ranglisten.Index.GINI -> "Gleichheit"
        de.troetenrepublik.app.engine.Ranglisten.Index.LOGISTIK -> "Logistik"
        de.troetenrepublik.app.engine.Ranglisten.Index.HUMANKAPITAL -> "Humankapital"
    }
    private val undpLizenz = "Daten: UNDP, Human Development Report, Lizenz CC BY 3.0 IGO (creativecommons.org/licenses/by/3.0/igo). Werte unverändert, Rang hier gezählt."
    override fun rlInfo(x: de.troetenrepublik.app.engine.Ranglisten.Index) = when (x) {
        de.troetenrepublik.app.engine.Ranglisten.Index.HDI -> "Index der menschlichen Entwicklung der Vereinten Nationen, von 0 bis 1.\n\n" +
            "Er fasst Lebenserwartung, Schuljahre und Einkommen je Kopf zusammen. Ab 0,8 gilt die Entwicklung als sehr hoch.\n\n" +
            undpLizenz
        de.troetenrepublik.app.engine.Ranglisten.Index.IHDI -> "Der HDI, gekürzt um die Ungleichheit bei Gesundheit, Bildung und Einkommen.\n\n" +
            "Je ungleicher ein Land, desto weiter liegt der Wert unter dem HDI.\n\n" +
            undpLizenz
        de.troetenrepublik.app.engine.Ranglisten.Index.GESCHLECHTER -> "Index der Vereinten Nationen von 0 bis 1 für Nachteile von Frauen bei Gesundheit, Bildung, Parlamentssitzen und Arbeit.\n\n" +
            "0 heißt gleichgestellt. Platz 1 hat das Land mit dem niedrigsten Wert.\n\n" +
            undpLizenz
        de.troetenrepublik.app.engine.Ranglisten.Index.GINI -> "Der Gini-Wert misst, wie ungleich Einkommen verteilt sind: 0 heißt alle gleich, 100 heißt einer hat alles.\n\n" +
            "Platz 1 hat das Land mit der gleichmäßigsten Verteilung. Die Erhebungen stammen aus verschiedenen Jahren, das Jahr steht dabei.\n\n" +
            "Daten: Weltbank, Lizenz CC BY 4.0."
        de.troetenrepublik.app.engine.Ranglisten.Index.LOGISTIK -> "Logistics Performance Index der Weltbank von 1 bis 5.\n\n" +
            "Befragte Spediteure bewerten Zoll, Infrastruktur, Sendungsverfolgung und Pünktlichkeit.\n\n" +
            "Daten: Weltbank, Lizenz CC BY 4.0."
        de.troetenrepublik.app.engine.Ranglisten.Index.HUMANKAPITAL -> "Human Capital Index der Weltbank von 0 bis 1.\n\n" +
            "Er schätzt, wie produktiv ein heute geborenes Kind als Erwachsener wird, gemessen an voller Gesundheit und Bildung. 0,7 heißt 70 % des Möglichen.\n\n" +
            "Daten: Weltbank, Lizenz CC BY 4.0."
    }
    override fun rlPlatz(rang: Int) = "Platz $rang"
    override fun rlVon(n: Int) = "von $n"
    override fun rlRang(rang: Int, von: Int, jahr: Int) = "Platz $rang von $von, $jahr"
    override fun rlHdiVerlauf(ab: Int) = "Menschliche Entwicklung seit $ab"
    override val globusKkp = "Währung gegen Kaufkraft"
    override val globusHdi = "Entwicklung (HDI)"
    override val globusGini = "Ungleichheit (Gini)"
    override val globusLegPreisniveau = "Preisniveau in Prozent des eigenen Landes. Blau billiger, ocker teurer."
    override val globusLegKkp = "Wechselkurs gegen die Kaufkraftparität zum Dollar. Blau darunter, ocker darüber."
    override val globusLegHdi = "Index der menschlichen Entwicklung der Vereinten Nationen von 0 bis 1."
    override val globusLegGini = "Gini-Wert der Einkommen, jüngste Erhebung. Blau gleichmäßiger, ocker ungleicher."
    override val ozErklaerung = "• Du verkaufst eine Option und erhältst sofort die Prämie (Fachwort: Stillhalter)\n" +
        "• Call: Du lieferst die Aktie zum Basispreis, wenn der Kurs darüber steigt\n" +
        "• Put: Du kaufst die Aktie zum Basispreis, wenn der Kurs darunter fällt"
    override fun ozFaellig(tag: String) = "fällig $tag"
    override val ozErtragJahr = "Ertrag pro Jahr"
    override val ozAbstandKurs = "Abstand zum Kurs"
    override val ozGehandelt = "Gehandelter Wert"
    override fun ozSeite(seite: String) = when (seite) { "KAUF" -> "eher gekauft"; "VERKAUF" -> "eher verkauft"; else -> "Seite unklar" }
    override fun suGruppe(key: String) = when (key) {
        "EDELMETALLE" -> "Edelmetalle"; "ENERGIE" -> "Energie"; "INDUSTRIEMETALLE" -> "Industriemetalle"; "AGRAR" -> "Agrar"
        "HAUPT" -> "Hauptpaare"; "NEBEN" -> "Nebenpaare"; "SCHWELLEN" -> "Schwellenländer"; "OHNE_EURO" -> "Ohne Euro"
        "LAYER1" -> "Layer 1"; "ZAHLUNG" -> "Zahlung"; "STABLECOIN" -> "Stablecoins"; "DEFI" -> "DeFi"
        "INFRASTRUKTUR" -> "Infrastruktur"; "MEME" -> "Meme"
        else -> key
    }
    override val suGruppeTitel = "Gruppe"
    override fun kyListe(key: String) = when (key) {
        "MARKTWERT" -> "Börsenwert"; "GEWINNER" -> "Gewinner"; "VERLIERER" -> "Verlierer"; "UMSATZ" -> "Umsatz"; "HOCH" -> "Abstand zum Hoch"
        else -> key
    }
    override fun kyListeTitel(key: String, woche: Boolean) = when (key) {
        "MARKTWERT" -> "Größte nach Börsenwert"
        "GEWINNER" -> if (woche) "Gewinner der Woche" else "Gewinner heute"
        "VERLIERER" -> if (woche) "Verlierer der Woche" else "Verlierer heute"
        "UMSATZ" -> "Umsatz in 24 Stunden"
        "HOCH" -> "Abstand zum 52-Wochen-Hoch"
        else -> key
    }
    override val kyTag = "Tag"
    override val kyWoche = "Woche"
    override val kyUnterHoch = "unter Hoch"
    override val kyBitcoinAnteil = "Bitcoin-Anteil"
    override fun kyBitcoinAnteilHinweis(anzahl: Int) = "am Börsenwert der $anzahl Coins dieser Liste"
    override fun roh(key: String, w: List<String>) = RohstoffLageTexte.text(true, key, w)
    override val umJede = "Jede Abweichung"
    override val umAbSchwelle = "Ab 10 Punkten"
    override val umNoetig = "Umschichten nötig"
    override val umAllesImRahmen = "Alles im Rahmen"
    override val umRahmen = "im Rahmen"
    override val umKeinZiel = "kein Ziel"
    override val umNochKeine = "Noch keine Zielgewichte"
    override val umJetzt = "Jetzt"
    override val umZiel = "Ziel"
    override val ppfZeitUnterHoch = "Zeit unter dem Hoch"
    override val ppfMittlererRueckgang = "Mittlerer Rückgang"
    override val ppfMittlererRueckgangHinweis = "im Schnitt unter dem Höchststand"
    override val stabTitel = "Stabilität"
    override val stabKeine = "Keine Nettoschulden"
    override fun stabName(key: String) = when (key) {
        "GEARING" -> "Nettoschulden zu Eigenkapital"; "TILGUNG" -> "Nettoschulden durch freien Cashflow"; else -> "Nettoschulden zu EBITDA"
    }
    override fun stabSatz(key: String, wert: String) = when (key) {
        "GEARING" -> wert; "TILGUNG" -> "$wert Jahre"; else -> "$wert×"
    }
    override fun stabNegativ(key: String) = when (key) {
        "GEARING" -> "Eigenkapital negativ"; "TILGUNG" -> "Freier Cashflow negativ"; else -> "EBITDA negativ"
    }
    override fun stabSchwelle(key: String) = when (key) {
        "GEARING" -> "bis 20 % sehr gut, über 100 % kritisch"; "TILGUNG" -> "unter 2 Jahren sehr gut, ab 5 kritisch"; else -> "ab 3 kritisch"
    }
    override val afRangsumme = "Rangsumme"
    override fun afMindestens(n: Int, m: Int) = "Mindestens $n von $m"
    override fun afFeldAnteil(feld: String, anteil: String, oben: Boolean) = if (oben) "$feld unter den höchsten $anteil" else "$feld unter den niedrigsten $anteil"
    override fun afFeldSpitze(feld: String, n: Int, oben: Boolean) = if (oben) "$feld: die $n höchsten" else "$feld: die $n niedrigsten"
    override fun afUnterHoch52(abstand: String) = "Mindestens $abstand unter dem Jahreshoch"
    override fun mz(key: String, w: List<String>) = MarktzeichenTexte.text(true, key, w)
    override val levTitel = "Levermann-Punkte"
    override fun levSatz(punkte: String, von: Int) = "$punkte von $von Punkten"
    override fun levKaufAb(ab: Int, gross: Boolean) = if (gross) "Schwelle der Methode: $ab Punkte bei großen Werten" else "Schwelle der Methode: $ab Punkte bei mittleren und kleinen Werten"
    override fun levKaufAbKurz(ab: Int) = "Kauf ab $ab Punkten"
    override fun levOhne(n: Int) = "$n ohne Angabe"
    override val levFinanz = "Finanzwert ohne EBIT-Marge"
    override val levKeine = "keine Angabe"
    override fun levName(key: String) = when (key) {
        "EK_RENDITE" -> "Eigenkapitalrendite"; "EBIT_MARGE" -> "EBIT-Marge"; "EK_QUOTE" -> "Eigenkapitalquote"; "KGV" -> "KGV"
        "KGV_SCHNITT" -> "KGV (Ø-Gewinn)"; "ANALYSTEN" -> "Analystenmeinung"; "ZAHLEN" -> "Reaktion auf Quartalszahlen"
        "REVISIONEN" -> "Gewinnrevisionen"; "KURS6" -> "Kursänderung 6 Monate"; "KURS12" -> "Kursänderung 12 Monate"
        "MOMENTUM" -> "Kursmomentum"; "UMKEHR" -> "Dreimonats-Umkehr zum Index"; else -> "Gewinnwachstum"
    }
    override fun levVon(n: Int) = "von $n Punkten"
    override fun levGruppe(key: String) = when (key) {
        "QUALITAET" -> "Qualität"; "BEWERTUNG" -> "Bewertung"; "STIMMUNG" -> "Stimmung"; else -> "Kurs"
    }
    override fun levKurz(key: String, variante: Boolean) = when (key) {
        "MOMENTUM" -> "Dreht der Kurs nach oben: +1, nach unten: −1"
        "UMKEHR" -> if (variante) "Zählt nur bei großen Werten" else "Drei Monate in Folge schwächer als der Index: +1, stärker: −1"
        else -> ""
    }
    override fun levWert(key: String, punkte: Int, variante: Boolean) = when (key) {
        "MOMENTUM" -> if (punkte > 0) "dreht nach oben" else if (punkte < 0) "dreht nach unten" else "keine Wende"
        else -> if (variante) "zählt nicht" else if (punkte > 0) "3 Monate schwächer" else if (punkte < 0) "3 Monate stärker" else "gemischt"
    }
    override val lyTitel = "Typ nach Lynch"
    override fun lyTyp(key: String) = when (key) {
        "LANGSAM" -> "Slow Grower"; "STETIG" -> "Stalwart"; "SCHNELL" -> "Fast Grower"; "ZYKLIKER" -> "Zykliker"
        "TURNAROUND" -> "Turnaround"; else -> "Asset Play"
    }
    override fun lyGrund(key: String, wert: String) = when (key) {
        "VERLUST" -> "Verlust im letzten Geschäftsjahr"; "VERLUST_VORJAHR" -> "Gewinn nach Verlust im Vorjahr"
        "BRANCHE" -> "Branche schwankt mit der Konjunktur"; "KBV" -> "Kurs unter Buchwert (KBV $wert)"
        "GEWINNWACHSTUM" -> "Gewinn $wert pro Jahr"; else -> "Umsatz $wert pro Jahr"
    }
    override fun lyDividende(wert: String) = "Dividende $wert"
    override fun lySchulden(wert: String, ok: Boolean) = if (ok) "Schulden $wert des Eigenkapitals (höchstens 80 %)" else "Schulden $wert des Eigenkapitals, mehr als 80 %"
    override val lyWachstumWarnung = "Wachstum über 50 % hält selten an"
    override val fkTitel = "Faires KGV"
    override fun fkSatz(fair: String, aktuell: String?) = if (aktuell != null) "fair $fair, aktuell $aktuell" else "fair $fair"
    override fun fkAbstand(prozent: String, ueber: Boolean) = if (ueber) "aktuelles KGV $prozent über dem fairen" else "aktuelles KGV $prozent unter dem fairen"
    override val fkSockel = "Basis-KGV"
    override val fkSockelInfo = "Ausgangs-KGV eines durchschnittlichen Unternehmens, bevor Aufschläge dazukommen."
    override val fkStabilInfo = "Aufschlag in KGV-Punkten: stabile Gewinne rechtfertigen ein höheres KGV."
    override val fkMarktInfo = "Aufschlag in KGV-Punkten: Firmen mit starker Marktstellung oder hoher Marge verdienen ein höheres KGV."
    override val fkRenditeInfo = "Faktor auf das KGV: Eigenkapitalrendite über 12 % erhöht es, darunter senkt sie es."
    override val fkStabil = "Aufschlag Gewinnstabilität"
    override fun fkStabilHinweis(schwankung: String?, jahre: Int) = if (schwankung != null) "Schwankung $schwankung über $jahre Jahre" else "Verlustjahr in $jahre Jahren"
    override val fkMarkt = "Aufschlag Moat"
    override val fkRendite = "Faktor Eigenkapitalrendite"
    override fun fkMarktHinweis(marktmacht: Boolean, wert: String) = if (marktmacht) "Moat $wert von 100" else "Umsatzrendite $wert"
    override fun fkRenditeHinweis(rendite: String) = "Eigenkapitalrendite $rendite, 12 % geben Faktor 1"
    override val ewTitel = "Ertragswert ohne Wachstum"
    override fun ewSatz(prozent: String, ueber: Boolean) = if (ueber) "$prozent über dem Ertragswert" else "$prozent unter dem Ertragswert"
    override fun ewUnter(ueber: Boolean) = if (ueber) "Der Aufpreis bezahlt künftiges Wachstum" else "Der heutige Gewinn deckt den Kurs ohne Wachstum"
    override val ewNegativ = "Kein positiver Ertragswert"
    override val ewJeAktie = "Ertragswert je Aktie"
    override fun ewJeAktieHinweis(kosten: String) = "Gewinn durch $kosten Kapitalkosten, plus Kasse, minus Schulden"
    override val ewGewinn = "Nachhaltiger Gewinn"
    override fun ewGewinnHinweis(marge: String, jahre: Int, steuer: String) = "EBIT-Marge $marge im Schnitt von $jahre Jahren, Steuersatz $steuer"
    override val ewKurs = "Kurs"
    override val ewBuchwert = "Buchwert je Aktie"
    override fun ewBuchwertHinweis(prozent: String, ueber: Boolean) = if (ueber) "Ertragswert $prozent darüber: Wettbewerbsvorteil" else "Ertragswert $prozent darunter"
    override fun mtZustand(zustand: String) = when (zustand) {
        "AUF" -> "Aufwärtstrend intakt"; "AB" -> "Abwärtstrend intakt"
        "AUF_GEBROCHEN" -> "Aufwärtstrend gebrochen"; "AB_GEBROCHEN" -> "Abwärtstrend gebrochen"
        else -> "Kein Trend aus Hochs und Tiefs"
    }
    override fun mtMarke(zustand: String, kurs: String) = when (zustand) {
        "AUF" -> "Bruch unter $kurs"; "AB" -> "Bruch über $kurs"; "AUF_GEBROCHEN" -> "unter $kurs"; else -> "über $kurs"
    }
    override val mtStopp = "Stopp aus dem Trend"
    override fun mtStoppHinweis(abstand: String) = "letztes Korrekturtief, $abstand unter dem Kurs"
    override val fmTitel = "Chartformation"
    override fun fmName(art: String) = when (art) {
        "doppeltop" -> "Doppeltop"; "doppelboden" -> "Doppelboden"
        "sks" -> "Schulter-Kopf-Schulter"; "sks_umgekehrt" -> "Umgekehrte Schulter-Kopf-Schulter"
        "dreieck_auf" -> "Aufsteigendes Dreieck"; "dreieck_ab" -> "Absteigendes Dreieck"
        else -> "Symmetrisches Dreieck"
    }
    override fun fmBedeutung(art: String, auf: Boolean) = when (art) {
        "doppeltop" -> "Zwei Hochs auf gleicher Höhe, Tief dazwischen unterschritten: Rücksetzer möglich"
        "doppelboden" -> "Zwei Tiefs auf gleicher Höhe, Hoch dazwischen überschritten: Erholung möglich"
        "sks" -> "Kopf über zwei Schultern, Nackenlinie unterschritten: Rücksetzer möglich"
        "sks_umgekehrt" -> "Kopf unter zwei Schultern, Nackenlinie überschritten: Erholung möglich"
        "dreieck_auf" -> "Hochs auf gleicher Höhe, Tiefs steigen: Ausbruch nach " + (if (auf) "oben" else "unten")
        "dreieck_ab" -> "Tiefs auf gleicher Höhe, Hochs fallen: Ausbruch nach " + (if (auf) "oben" else "unten")
        else -> "Hochs fallen, Tiefs steigen: Ausbruch nach " + (if (auf) "oben" else "unten")
    }
    override fun fmDurchbruch(datum: String) = "Ausbruch am $datum"
    override val rhTitel = "Rollen"
    override fun rhDruck(imGeld: Boolean) = if (imGeld) "Kurs unter dem Basispreis" else "Kurs nahe am Basispreis"
    override val rhGutschrift = "Gutschrift je Stück"
    override val rhKosten = "Kosten je Stück"
    override val rhNeuePraemie = "Neue Prämie"
    override val rhKeine = "Kein Put mit 45 bis 90 Tagen Laufzeit in der Optionskette."
    override val rhRollen = "Rollen"
    override val rhGerollt = "Gerollt"
    override val rhNachAndienung = "Nach Andienung: gedeckter Call"
    override fun rhFrage(alt: String, neu: String, datum: String) = "Put $alt zurückkaufen, Put $neu mit Fälligkeit $datum verkaufen."
    override fun ml(key: String, w: List<String>) = MarktLaenderTexte.text(true, key, w)
    override fun nhAm(zeit: String) = "am $zeit"
    override fun itTitelTage(tage: Int) = "Insider-Trend, $tage Tage"
    override fun vdZusammenfassungAelter(kaeufe: Int, kaufBetrag: String, verkaeufe: Int, verkaufBetrag: String) =
        "Letzte Meldungen: " + vdZusammenfassung(kaeufe, kaufBetrag, verkaeufe, verkaufBetrag).substringAfter(": ")
    override fun kvFensterBis(tage: Int, bis: String) = "Käufer nach Meldetag, $tage Tage bis $bis"
    override val whZwoelfMonate = "12 Monate"
    override val trumpMarktreaktion = "Beiträge mit Marktreaktion"
    override val abhWasWaereDepot = "Rechenbeispiele fürs ganze Depot"
    override val glockeTermin = "Glocke zum Termin"
    override fun crOptionenOffen(long: String) = "Anteil Calls an den offenen Kontrakten: $long"
    override val modellVol = "Modell-Volatilität"
    override val gsrTrend = "Trend aus Hochs und Tiefs"
    override fun stabGrund(key: String) = when (key) {
        "GEARING" -> "Nettoschulden über dem Eigenkapital"; "TILGUNG" -> "Tilgung aus freiem Cashflow ab 5 Jahren"; else -> "Nettoschulden ab 3× EBITDA"
    }
    override fun p1BilanzGemessen(art: String) = when (art) {
        "bb" -> "\nGemessen: Schluss unter dem unteren Band (Erholung) oder über dem oberen (Rücksetzer)"
        "rsi" -> "\nGemessen: RSI steigt wieder über 30 (steigend) oder fällt wieder unter 70 (fallend)"
        else -> ""
    }
    override fun dp(key: String, w: List<String>) = DepotTexte.text(true, key, w)
    override val newsNurEnglisch = "Nur englische Quellen"
    // 0.78 Strategietest: Ausstieg, Kennzahlen je Geschaeft, weitere Regeln, andere Werte
    override fun slRegelInfo(art: String) = when (art) {
        "MOMENTUM" -> "Geprüft wird nur am letzten Handelstag des Monats: investiert, wenn der Schluss über dem vor 252 Handelstagen liegt."
        "RSI_KURZ" -> "Kauf nur über der 200-Tage-Linie, Verkauf bei Schluss über der 5-Tage-Linie oder nach 10 Handelstagen."
        "MONATSWECHSEL" -> "Investiert vom letzten Handelstag des Monats bis zum dritten des Folgemonats, also 12 Käufe und 12 Verkäufe pro Jahr."
        "ZIELVOLA" -> "Immer investiert. Der Anteil am Depot ist 15 % Zielschwankung geteilt durch die Schwankung der letzten 20 Handelstage, höchstens 100 %, neu gesetzt ab 10 Prozentpunkten Abstand. Trades zählt Käufe und Verkäufe. Nach Moreira und Muir (2017), dort mit Hebel."
        else -> ""
    }
    override val slAusstieg = "Ausstieg"
    override fun slAusstiegName(art: String, wert: Int, stopp: Int) = when (art) {
        "STOPP" -> "Stopp $wert %"
        "TRAILING" -> "Trailing-Stopp $wert ATR"
        "ZEIT" -> "Nach $wert Handelstagen"
        "ZIEL" -> "Ziel ${wert}R, Stopp $stopp %"
        else -> "Wie die Regel"
    }
    override fun slAusstiegHinweis(art: String, wert: Int, stopp: Int) =
        "Der Ausstieg gilt zusätzlich zum Verkaufssignal. Danach wird erst beim nächsten Kaufsignal wieder gekauft. Die Prüfungen darunter rechnen ohne ihn." + when (art) {
            "TRAILING" -> " ATR ist die mittlere Tagesspanne der letzten 20 Tage. Der Stopp liegt $wert ATR unter dem höchsten Schluss seit dem Kauf und steigt nur."
            "ZIEL" -> " R ist der Abstand zum Stopp: ${wert}R sind ${wert * stopp} % Gewinn."
            else -> ""
        }
    override val slTrendfilter = "Kauf nur über der 200-Tage-Linie"
    override val slMonatsende = "Nur am Monatsende prüfen"
    override fun slMehrRegeln(n: Int) = "$n weitere Regeln"
    override val slGv = "Gewinn/Verlust-Verhältnis"
    override val slErwartung = "Erwartung je Geschäft"
    override val slGewinnfaktor = "Gewinnfaktor"
    override val slHaltedauer = "Haltedauer in Handelstagen"
    override val slVerlustserie = "Längste Verlustserie"
    override val slKennzahlen = "Kennzahlen je Geschäft"
    override val slKennzahlenText = "Ein Geschäft ist ein Kauf mit seinem Verkauf, das Ergebnis zählt nach Kosten in Prozent des Einsatzes.\n• Gewinn/Verlust-Verhältnis: mittlerer Gewinn geteilt durch mittleren Verlust\n• Erwartung je Geschäft: mittleres Ergebnis aller Geschäfte\n• Gewinnfaktor: Summe der Gewinne geteilt durch Summe der Verluste\n• Haltedauer: Mittel über alle Geschäfte\n• Längste Verlustserie: meiste Verlustgeschäfte in Folge"
    override val slAndere = "Andere Werte"
    override val slAndereText = "Gleicher Ausstieg und Zeitraum, einzeln gerechnet für bis zu 20 Werte. Der Vorsprung ist die Rendite pro Jahr der Regel abzüglich Kaufen und Halten.\n\nDepot und Watchlist sind keine neutrale Auswahl: Dort stehen oft Werte, die schon gut gelaufen sind."
    override fun slAndereKern(besser: Int, alle: Int) = "Schlägt Kaufen und Halten bei $besser von $alle"
    override val slAndereSpalte = "Vorsprung pro Jahr vor Kaufen und Halten"
    override val slAndereKeine = "Keine weiteren Werte mit genug Kursen in Depot und Watchlist."
    override fun st(key: String, w: List<String>) = StrategieTexte.text(true, key, w)
    override val pmWichtigste = "Wichtigste"
    override val pmWichtigsteAus = "• „Wichtigste“ lässt aus: Sport, Unterhaltung, Wetter des Tages, Zähler für Beiträge und Erwähnungen, Kurswetten auf Minuten, Stunden oder den Tag, Preise unter 2 % oder über 98 %"
    override fun su(key: String, w: List<String>) = StufenTexte.text(true, key, w)
    override fun km(key: String, w: List<String>) = KursmusterTexte.text(true, key, w)
    override fun ri(key: String, w: List<String>) = RisikoTexte.text(true, key, w)
    override fun flex(key: String, w: List<String>) = FlexTexte.text(true, key, w)
}
private val WS_GEBIETE = listOf(
    " Europe Middle East And Africa " to " Europa, Naher Osten und Afrika ", " Other Countries " to " Übrige Länder ", " Rest Of World " to " Rest der Welt ",
    " Rest Of The World " to " Rest der Welt ", " All Other " to " Übrige ", " Rest Of " to " Übriges ", " Excluding " to " ohne ", " Including " to " mit ",
    " Greater China " to " Großchina ", " Asia Pacific " to " Asien-Pazifik ", " North America " to " Nordamerika ", " South America " to " Südamerika ",
    " Latin America " to " Lateinamerika ", " Middle East " to " Naher Osten ", " Americas " to " Amerika ", " Europe " to " Europa ", " Asia " to " Asien ",
    " Africa " to " Afrika ", " United States " to " USA ", " Germany " to " Deutschland ", " Hong Kong " to " Hongkong ", " And " to " und ",
    " Other " to " Übrige ", " International " to " International ",
)
