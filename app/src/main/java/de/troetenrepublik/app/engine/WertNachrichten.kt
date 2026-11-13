package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss

// Schlagzeilen zu einem Wert: Wertpapierseite, News-Reiter „Depot“ und das Depot-Widget holen sie ueber dieselbe
// Funktion (Yahoo nach Kuerzel, mit Namen dazu Google News). Hier stehen das Zusammenfuehren und die Staffelung.
object WertNachrichten {
    const val JE_WERT = 20

    // Abrufe, die zugleich laufen duerfen. Je Wert sind es zwei (Yahoo und Google).
    const val GLEICHZEITIG = 4

    // So viele Werte werden zusammen geholt, damit hoechstens GLEICHZEITIG Abrufe laufen.
    fun werteJeGruppe(gleichzeitig: Int = GLEICHZEITIG): Int = (gleichzeitig / 2).coerceAtLeast(1)

    // Neueste zuerst, gleiche Schlagzeile (ohne Gross- und Kleinschreibung) nur einmal; bei Gleichstand bleibt Yahoo.
    fun <T> zusammen(yahoo: List<T>, google: List<T>, titel: (T) -> String, zeit: (T) -> Long, max: Int = JE_WERT): List<T> =
        (yahoo + google).distinctBy { titel(it).trim().lowercase() }.sortedByDescending(zeit).take(max)

    // Feed, aus dem eine Schlagzeile stammt (fuer die Quellenangabe der Liste)
    fun quelle(link: String): String = if ("news.google." in link) "Google News" else "Yahoo Finance"

    // Schlagzeile zum Wert als Eintrag der Nachrichtenseite: fuer den Reiter „Depot“, das Blatt und das Merken.
    fun eintrag(n: MarketApi.NewsItem): Rss.Eintrag = Rss.Eintrag(
        titel = n.title, link = n.link, zeit = n.published, zusammenfassung = n.summary, herausgeber = n.source,
        quelle = quelle(n.link), kategorien = setOf(Rss.Kategorie.UNTERNEHMEN), zusammenfassungGekuerzt = n.summaryGekuerzt,
    )
}
