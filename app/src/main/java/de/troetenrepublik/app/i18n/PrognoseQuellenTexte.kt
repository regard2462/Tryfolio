package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der Prognosequellen ohne Echtgeld (Manifold, Metaculus). Ein Schluessel je Text, Platzhalter {0} fuer Werte.
object PrognoseQuellenTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // In der Sprache der App.
    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        // Art der Quelle: Zeile ueber der Liste und unter dem Ereignis
        "art.MANIFOLD" to "",
        "art.METACULUS" to "Gemeinschaftsprognose ohne Geldeinsatz",
        // Kurz neben dem Namen der Quelle
        "art.kurz.MANIFOLD" to "",
        "art.kurz.METACULUS" to "Gemeinschaftsprognose",
        "mana" to "{0} Mana",
        "prognostiker" to "{0} Prognostiker",
        "wahrscheinlichkeit" to "Wahrscheinlichkeit",
        "daten" to "Werte von {0}, möglicherweise verzögert. Keine Übungswetten.",
        "englisch" to "Titel liefert {0} nur auf Englisch. Mit einem eigenen DeepL-Schlüssel übersetzt die App sie ins Deutsche.",
        "mc.ohneToken" to "Metaculus antwortet nur mit dem Token eines eigenen Kontos.",
        "mc.tokenEintragen" to "Token eintragen",
        "mc.verborgen" to "Metaculus gibt die Gemeinschaftsprognose nur an freigeschaltete Konten aus. Fragen ohne Wert: {0}",
        "zg.zweck" to "Gemeinschaftsprognosen von Metaculus bei den Prognosemärkten.",
        // Stichpunkte, je Zeile einer
        "zg.punkte" to "Kostenlos nach Anmeldung bei Metaculus\n" +
            "Das Token geht nur an metaculus.com\n" +
            "Metaculus gibt die Gemeinschaftsprognose nur an Konten aus, die es dafür freigeschaltet hat (Antrag über das Formular bei Metaculus)\n" +
            "Bedingungen von Metaculus: kommerzielle Nutzung nur mit Vertrag, kein Training von KI-Modellen",
        "zg.feld" to "Token",
        "zg.holen" to "Token bei Metaculus holen",
    )

    private val EN = mapOf(
        "art.MANIFOLD" to "",
        "art.METACULUS" to "Community forecast, no money staked",
        "art.kurz.MANIFOLD" to "",
        "art.kurz.METACULUS" to "community forecast",
        "mana" to "{0} mana",
        "prognostiker" to "{0} forecasters",
        "wahrscheinlichkeit" to "Probability",
        "daten" to "Values from {0}, possibly delayed. No practice bets.",
        "englisch" to "{0} provides titles in English only. With your own DeepL key the app translates them into German.",
        "mc.ohneToken" to "Metaculus only answers with the token of your own account.",
        "mc.tokenEintragen" to "Enter token",
        "mc.verborgen" to "Metaculus releases the community forecast only to accounts it has unlocked. Questions without a value: {0}",
        "zg.zweck" to "Community forecasts from Metaculus in prediction markets.",
        "zg.punkte" to "Free after signing up with Metaculus\n" +
            "The token is sent to metaculus.com only\n" +
            "Metaculus releases the community forecast only to accounts it has unlocked (request through the form at Metaculus)\n" +
            "Metaculus terms: commercial use only with an agreement, no training of AI models",
        "zg.feld" to "Token",
        "zg.holen" to "Get a token from Metaculus",
    )
}
