package de.troetenrepublik.app.i18n

// Texte zum Abgleich mit dem Flex-Bericht von Interactive Brokers, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1}.
internal object FlexTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        // Zugaenge
        "name" to "Interactive Brokers (Flex-Bericht)",
        "zweck" to "Gleicht das Depot mit deinem Konto ab: offene Positionen, Geschäfte, Dividenden, Zinsen, Steuern.",
        "punkte" to "• Token und Abfragenummer liegen verschlüsselt auf dem Gerät und gehen nur an Interactive Brokers\n• Die App liest nur, handeln kann sie damit nicht\n• Grenze des Dienstes: 10 Abrufe je Minute, ein Abgleich braucht 2 bis 8",
        "token" to "Token",
        "abfrage" to "Abfragenummer (Query ID)",
        "formFalsch" to "Nur Ziffern und Buchstaben",
        "beimStart" to "Beim Start abgleichen",
        "jetzt" to "Jetzt abgleichen",
        // Anleitung hinter dem Info-Symbol
        "anleitung.titel" to "Flex-Bericht einrichten",
        "anleitung" to "Im Kundenportal von Interactive Brokers:\n" +
            "• Performance & Berichte › Flex-Abfragen (Performance & Reports › Flex Queries): Aktivitäts-Flex-Abfrage anlegen\n" +
            "• Abschnitte: Kontoinformationen (Account Information), Offene Positionen (Open Positions), Trades, Bargeldtransaktionen (Cash Transactions), Bargeldbericht (Cash Report). Je Abschnitt alle Felder\n" +
            "• Andere Basiswährung als Euro: zusätzlich Wechselkurse (Conversion Rates)\n" +
            "• Format XML, Zeitraum höchstens 365 Tage\n" +
            "• Die Abfragenummer steht bei der gespeicherten Abfrage\n" +
            "• Flex Web Service einschalten, dort entsteht das Token. Gültigkeit wählbar, Vorgabe 6 Stunden\n\n" +
            "In der App:\n" +
            "• Token und Abfragenummer unter Profil › Zugänge\n" +
            "• Abgleich unter Profil › Depot importieren › Aus Flex-Bericht übernehmen\n" +
            "• Ohne ISIN im Bericht bleibt ein Papier unzugeordnet\n" +
            "• Optionen, Futures und Anleihen führt die App aus dem Bericht nicht, sie stehen in der Vorschau unter „Nicht übernommen“",
        // Import
        "zeile" to "Aus Flex-Bericht übernehmen",
        "zeile.unter" to "Interactive Brokers, Abgleich mit Vorschau",
        // Vorschau
        "titel" to "Abgleich mit Flex-Bericht",
        "laedt" to "Bericht wird geholt",
        "gleich" to "Depot stimmt mit dem Bericht überein",
        "n.NEU" to "{0} neu",
        "n.GEAENDERT" to "{0} geändert",
        "n.ENTFAELLT" to "{0} entfällt",
        "n.buchungen" to "{0} neue Buchungen",
        "zeitraum" to "Bericht {0} bis {1}",
        "art.NEU" to "Neu",
        "art.GEAENDERT" to "Geändert",
        "art.ENTFAELLT" to "Entfällt",
        "vorher" to "vorher {0}",
        "buchungen" to "Neue Buchungen",
        "bekannt" to "Schon im Depot",
        "einzahlung" to "Errechnete Einzahlung",
        "bargeld" to "Bargeld laut Bericht",
        "offen" to "Nicht übernommen (ohne Zuordnung oder im Depot nicht abbildbar): {0}",
        "ungelesen" to "{0} Zeilen des Berichts nicht gelesen",
        "ohnePositionen" to "Abschnitt „Offene Positionen“ fehlt im Bericht, der Bestand ist nicht abgeglichen",
        "uebernehmen" to "Übernehmen",
        "fertig" to "Abgleich übernommen",
        "depotGeaendert" to "Das Depot hat sich geändert, die Vorschau ist neu gerechnet",
        "ohneZugang" to "Token und Abfragenummer fehlen",
        "zugaenge" to "Zugänge öffnen",
        // Fehler des Dienstes
        "f.WARTEN" to "Der Bericht ist noch nicht fertig. Später erneut abgleichen.",
        "f.TOKEN_ABGELAUFEN" to "Token abgelaufen. Im Kundenportal ein neues erzeugen.",
        "f.TOKEN_UNGUELTIG" to "Token ungültig",
        "f.ABFRAGE" to "Abfragenummer ungültig oder keine Aktivitäts-Flex-Abfrage",
        "f.ADRESSE_GESPERRT" to "Das Token gilt nur für eine andere IP-Adresse",
        "f.ZU_VIELE" to "Zu viele Abrufe. Nach einer Minute erneut abgleichen.",
        "f.DIENST_AUS" to "Der Flex Web Service ist für das Konto nicht aktiv",
        "f.SONST" to "Der Dienst meldet Fehler {0}",
        "f.netz" to "Keine Antwort vom Dienst",
        "f.eingabe" to "Token oder Abfragenummer haben eine ungültige Form",
        "f.unlesbar" to "Die Antwort ist kein Flex-Bericht im Format XML",
    )

    private val EN = mapOf(
        "name" to "Interactive Brokers (Flex report)",
        "zweck" to "Reconciles the portfolio with your account: open positions, trades, dividends, interest, taxes.",
        "punkte" to "• Token and query ID are stored encrypted on the device and go to Interactive Brokers only\n• The app only reads, it cannot trade with them\n• Service limit: 10 requests per minute, one reconciliation needs 2 to 8",
        "token" to "Token",
        "abfrage" to "Query ID",
        "formFalsch" to "Digits and letters only",
        "beimStart" to "Reconcile at start",
        "jetzt" to "Reconcile now",
        "anleitung.titel" to "Set up the Flex report",
        "anleitung" to "In the Interactive Brokers client portal:\n" +
            "• Performance & Reports › Flex Queries: create an Activity Flex Query\n" +
            "• Sections: Account Information, Open Positions, Trades, Cash Transactions, Cash Report. All fields in each section\n" +
            "• Base currency other than euro: also Conversion Rates\n" +
            "• Format XML, period at most 365 days\n" +
            "• The query ID is shown next to the saved query\n" +
            "• Switch on the Flex Web Service, which creates the token. Validity selectable, 6 hours by default\n\n" +
            "In the app:\n" +
            "• Token and query ID under Profile › Access keys\n" +
            "• Reconcile under Profile › Import portfolio › Take over from Flex report\n" +
            "• Without an ISIN in the report a security stays unassigned\n" +
            "• Options, futures and bonds from the report are not held in the app, the preview lists them under “Not taken over”",
        "zeile" to "Take over from Flex report",
        "zeile.unter" to "Interactive Brokers, reconciliation with preview",
        "titel" to "Reconcile with Flex report",
        "laedt" to "Fetching the report",
        "gleich" to "Portfolio matches the report",
        "n.NEU" to "{0} new",
        "n.GEAENDERT" to "{0} changed",
        "n.ENTFAELLT" to "{0} removed",
        "n.buchungen" to "{0} new transactions",
        "zeitraum" to "Report {0} to {1}",
        "art.NEU" to "New",
        "art.GEAENDERT" to "Changed",
        "art.ENTFAELLT" to "Removed",
        "vorher" to "before {0}",
        "buchungen" to "New transactions",
        "bekannt" to "Already in the portfolio",
        "einzahlung" to "Computed deposit",
        "bargeld" to "Cash per report",
        "offen" to "Not taken over (unassigned or not representable in the portfolio): {0}",
        "ungelesen" to "{0} rows of the report not read",
        "ohnePositionen" to "The “Open Positions” section is missing in the report, holdings are not reconciled",
        "uebernehmen" to "Take over",
        "fertig" to "Reconciliation taken over",
        "depotGeaendert" to "The portfolio has changed, the preview is recalculated",
        "ohneZugang" to "Token and query ID are missing",
        "zugaenge" to "Open access keys",
        "f.WARTEN" to "The report is not ready yet. Reconcile again later.",
        "f.TOKEN_ABGELAUFEN" to "Token expired. Create a new one in the client portal.",
        "f.TOKEN_UNGUELTIG" to "Token invalid",
        "f.ABFRAGE" to "Query ID invalid or not an Activity Flex Query",
        "f.ADRESSE_GESPERRT" to "The token is restricted to another IP address",
        "f.ZU_VIELE" to "Too many requests. Reconcile again after a minute.",
        "f.DIENST_AUS" to "The Flex Web Service is not active for the account",
        "f.SONST" to "The service reports error {0}",
        "f.netz" to "No response from the service",
        "f.eingabe" to "Token or query ID have an invalid form",
        "f.unlesbar" to "The response is not a Flex report in XML format",
    )
}
