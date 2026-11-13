package de.troetenrepublik.app.i18n

// Texte zu den Zinsprojektionen der Fed im Ausblick, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object FedPunkteTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Zinsprojektionen der Fed",
        "kern" to "Median Ende {0}: {1}",
        "unter" to "Sitzung vom {0}, {1} Teilnehmer",
        "aktuell" to "Aktuell {0}",
        "vorher" to "Median Ende {0} in der Sitzung vom {1}",
        "markt" to "Markt für Dezember {0}",
        "markt.kurz" to "Markt",
        "lang" to "Langfristig",
        "bild" to "Punktdiagramm: Zinssatz, den die Teilnehmer zum Jahresende für angemessen halten, mit Median je Jahr",
        "info" to "Viermal im Jahr nennt jedes Mitglied des Offenmarktausschusses der Fed den Leitzins, den es zum Ende der nächsten Jahre und auf lange Sicht für angemessen hält. Ein Punkt steht für ein Mitglied.\n\n" +
            "• Strich mit Zahl: Median, die Hälfte der Mitglieder liegt darüber, die Hälfte darunter.\n" +
            "• Raute: Satz, den der Markt laut Fed-Funds-Futures für den Dezember erwartet.\n" +
            "• Liegt der Markt unter dem Median, rechnet er mit mehr Senkungen als die Fed.\n\n" +
            "Die Projektionen sind Einschätzungen einzelner Mitglieder, kein Beschluss und keine Zusage.\n\n" +
            "Quelle: Board of Governors of the Federal Reserve System, Summary of Economic Projections. Stand {0}.",
        "quelle" to "Federal Reserve, Summary of Economic Projections",
    )

    private val EN = mapOf(
        "titel" to "Fed rate projections",
        "kern" to "Median end of {0}: {1}",
        "unter" to "Meeting of {0}, {1} participants",
        "aktuell" to "Currently {0}",
        "vorher" to "Median end of {0} at the meeting of {1}",
        "markt" to "Market for December {0}",
        "markt.kurz" to "Market",
        "lang" to "Longer run",
        "bild" to "Dot plot: rate the participants consider appropriate at year end, with the median per year",
        "info" to "Four times a year each member of the Fed's Open Market Committee states the policy rate they consider appropriate at the end of the coming years and in the longer run. One dot stands for one member.\n\n" +
            "• Line with number: median, half of the members are above it and half below.\n" +
            "• Diamond: rate the market expects for December according to fed funds futures.\n" +
            "• A market below the median expects more cuts than the Fed.\n\n" +
            "The projections are assessments of individual members, neither a decision nor a commitment.\n\n" +
            "Source: Board of Governors of the Federal Reserve System, Summary of Economic Projections. As of {0}.",
        "quelle" to "Federal Reserve, Summary of Economic Projections",
    )
}
