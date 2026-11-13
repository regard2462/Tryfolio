package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der zweiten Kursquelle mit eigenem Schluessel. Ein Schluessel je Text, Platzhalter {0} fuer Werte.
object KursRueckfallTexte {

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
        "name" to "Massive (früher Polygon.io)",
        "zweck" to "Schlusskurse und Tagesverlauf für US-Aktien, wenn die Hauptquelle nichts liefert.",
        "punkte" to "Nur für den persönlichen, nicht geschäftlichen Gebrauch, keine Weitergabe der Daten\n" +
            "Kostenloser Tarif: Kurse nach Handelsende, 2 Jahre Verlauf, 5 Abrufe je Minute\n" +
            "Die App nutzt höchstens 4 je Minute und merkt sich Antworten 6 Stunden",
        "stand" to "Abrufe in der letzten Minute: {0} von {1}",
        "schluss" to "Schlusskurs vom {0}",
        "name.tiingo" to "Tiingo",
        "zweck.tiingo" to "US-Tageskurse bis 20 Jahre zurück, Splits, Dividenden",
        "name.twelve" to "Twelve Data",
        "zweck.twelve" to "US-Tageskurse bis 20 Jahre zurück, letzter Kurs",
        "stand.tag" to "Abrufe in den letzten 24 Stunden: {0} von {1}",
    )

    private val EN = mapOf(
        "name" to "Massive (formerly Polygon.io)",
        "zweck" to "Closing prices and daily history for US stocks when the main source returns nothing.",
        "punkte" to "For personal, non-business use only, no passing on of the data\n" +
            "Free plan: prices after the close, 2 years of history, 5 requests a minute\n" +
            "The app uses at most 4 a minute and keeps answers for 6 hours",
        "stand" to "Requests in the last minute: {0} of {1}",
        "schluss" to "Closing price of {0}",
        "name.tiingo" to "Tiingo",
        "zweck.tiingo" to "US daily prices up to 20 years back, splits, dividends",
        "name.twelve" to "Twelve Data",
        "zweck.twelve" to "US daily prices up to 20 years back, latest quote",
        "stand.tag" to "Requests in the last 24 hours: {0} of {1}",
    )
}
