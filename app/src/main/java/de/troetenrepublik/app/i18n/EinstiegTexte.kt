package de.troetenrepublik.app.i18n

// Texte der Abschnitte „Einmalanlage gegen Raten“ und „Rücksetzer kaufen“ im Sparplan-Rückblick, 0.78. Ein Schluessel
// je Text, Platzhalter {0}, {1} fuer Werte. Der Satz setzt die Kernzahl fort, die gross darueber steht.
internal object EinstiegTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return HinweisTexte.einsetzen(deutsch, t)
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "einmal.titel" to "Einmalanlage gegen Raten",
        "einmal.satz" to "Einmalanlage lag in {0} von {1} Zeiträumen vorn",
        "einmal.aufbau" to "{0} sofort oder in {1} Monatsraten, {2}, Stand nach {3}",
        "einmal.raten" to "Monatsraten",
        "einmal.stand" to "Stand nach",
        "einmal.ende" to "Ende der Raten",
        "einmal.kurz" to "Median {0}, Mittel {1}",
        "einmal.mittel" to "Vorsprung im Mittel",
        "einmal.median" to "Vorsprung im Median",
        "einmal.bester" to "Bester Fall",
        "einmal.schlechtester" to "Schlechtester Fall",
        "einmal.start" to "Start {0}",
        "einmal.rueckgang" to "Größter Rückgang im Median",
        "einmal.rueckgangHint" to "Einmalanlage, Raten",
        "achse.raten" to "Raten vorn",
        "achse.einmal" to "Einmalanlage vorn",
        "paar" to "{0} / {1}",
        "monate" to "{0} Monate",
        "monaten" to "{0} Monaten",
        "jahren" to "{0} Jahren",
        "details" to "Einzelheiten",
        "kurz" to "Kursgeschichte zu kurz für diesen Vergleich.",
        "dip.titel" to "Rücksetzer kaufen gegen stur sparen",
        "dip.satz" to "Rücksetzer kaufen lag in {0} von {1} Zeiträumen über {2} Jahre vorn",
        "dip.satzGanz" to "Endwert von Rücksetzer kaufen gegen stur sparen seit {0}",
        "dip.seit" to "Eingezahlt seit {0}",
        "dip.stur" to "Stur sparen",
        "dip.warten" to "Rücksetzer kaufen",
        "dip.schwelle" to "Abstand zum Hoch",
        "dip.hoechst" to "Kauf spätestens nach Monaten",
        "dip.nie" to "nie",
        "dip.liegen" to "Rate lag im Mittel auf dem Konto",
        "dip.laengste" to "Längstens",
        "dip.kurz" to "{0} Käufe, Vorsprung im Median {1}",
        "dip.kurzGanz" to "{0} Käufe",
        "dip.kaeufe" to "Käufe",
        "dip.rest" to "Am Ende nicht angelegt",
        "dip.median" to "Vorsprung im Median",
        "dip.mittel" to "Vorsprung im Mittel",
        "dip.ganz" to "Vorsprung seit {0}",
        "hinweis" to "{hist}\n\n• Monatsende-Kurse in Euro, Ausschüttungen wieder angelegt.\n• Wartendes Geld ist unverzinst.\n• Jeder Kauf zum Briefkurs. Die einzelne Order kostet {0}, die Sparplanrate nichts.\n• Das Hoch ist der höchste Monatsende-Kurs der geladenen Reihe bis zum jeweiligen Monat.\n• Die Zeiträume überlappen sich, benachbarte Startmonate ähneln sich.",
    )

    private val EN = mapOf(
        "einmal.titel" to "Lump sum against installments",
        "einmal.satz" to "Lump sum was ahead in {0} of {1} periods",
        "einmal.aufbau" to "{0} at once or in {1} monthly installments, {2}, value after {3}",
        "einmal.raten" to "Monthly installments",
        "einmal.stand" to "Value after",
        "einmal.ende" to "End of installments",
        "einmal.kurz" to "Median {0}, mean {1}",
        "einmal.mittel" to "Mean lead",
        "einmal.median" to "Median lead",
        "einmal.bester" to "Best case",
        "einmal.schlechtester" to "Worst case",
        "einmal.start" to "Start {0}",
        "einmal.rueckgang" to "Median maximum drawdown",
        "einmal.rueckgangHint" to "Lump sum, installments",
        "achse.raten" to "Installments ahead",
        "achse.einmal" to "Lump sum ahead",
        "paar" to "{0} / {1}",
        "monate" to "{0} months",
        "monaten" to "{0} months",
        "jahren" to "{0} years",
        "details" to "Details",
        "kurz" to "Price history too short for this comparison.",
        "dip.titel" to "Buying dips against steady saving",
        "dip.satz" to "Buying dips was ahead in {0} of {1} periods of {2} years",
        "dip.satzGanz" to "Final value of buying dips against steady saving since {0}",
        "dip.seit" to "Paid in since {0}",
        "dip.stur" to "Steady saving",
        "dip.warten" to "Buying dips",
        "dip.schwelle" to "Distance from the high",
        "dip.hoechst" to "Buy at the latest after months",
        "dip.nie" to "never",
        "dip.liegen" to "Mean time an installment sat in cash",
        "dip.laengste" to "Longest",
        "dip.kurz" to "{0} purchases, median lead {1}",
        "dip.kurzGanz" to "{0} purchases",
        "dip.kaeufe" to "Purchases",
        "dip.rest" to "Not invested at the end",
        "dip.median" to "Median lead",
        "dip.mittel" to "Mean lead",
        "dip.ganz" to "Lead since {0}",
        "hinweis" to "{hist}\n\n• Month-end prices in euro, distributions reinvested.\n• Waiting cash earns no interest.\n• Every purchase at the ask price. A single order costs {0}, a savings plan installment nothing.\n• The high is the highest month-end price of the loaded series up to each month.\n• The periods overlap, neighboring start months are similar.",
    )
}
