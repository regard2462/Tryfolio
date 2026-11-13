package de.troetenrepublik.app.i18n

// Kurze Texte der Regelprüfung (0.79): Region im Profil, Gewichtsvorschlag. Ein Schlüssel je Text, Platzhalter {0}, {1}.
internal object RegelTexte {
    fun text(deutsch: Boolean, key: String, vararg w: String): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    private val DE = mapOf(
        "steuer" to "Steuern nach deutschem Recht",
        "auf" to "{0} auf {1}",
        "rendite" to "Rendite pro Jahr",
    )

    private val EN = mapOf(
        "steuer" to "Taxes follow German law",
        "auf" to "{0} to {1}",
        "rendite" to "Return per year",
    )
}
