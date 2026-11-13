package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.NachrichtArt.Art
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Wichtig
import java.time.LocalDate

// Nachrichtenliste (0.77): Reiter „Depot“ und aeltere Schlagzeilen fuer Zahlen, Analyst, Uebernahme und Kapitalmassnahme.
object NachrichtenListe {
    const val ARCHIV_TAGE = 30

    // Arten, fuer die aeltere Schlagzeilen nachgeladen werden
    val ARCHIV_ARTEN: Set<Art> = setOf(Art.ZAHLEN, Art.ANALYST, Art.UEBERNAHME, Art.KAPITAL)

    private val STICHWORTE_DE = mapOf(
        Art.ZAHLEN to "Quartalszahlen OR Geschäftszahlen OR Jahreszahlen OR Gewinnwarnung OR \"hebt Prognose\" OR \"senkt Prognose\"",
        Art.ANALYST to "Kursziel OR hochgestuft OR abgestuft OR Kaufempfehlung OR Analyst",
        Art.UEBERNAHME to "Übernahme OR Fusion OR Übernahmeangebot OR übernimmt",
        Art.KAPITAL to "Aktienrückkauf OR Kapitalerhöhung OR Aktiensplit OR Dividendenerhöhung OR Wandelanleihe",
    )

    private val STICHWORTE_EN = mapOf(
        Art.ZAHLEN to "earnings OR \"quarterly results\" OR \"beats estimates\" OR \"misses estimates\" OR \"profit warning\" OR \"raises guidance\" OR \"cuts guidance\"",
        Art.ANALYST to "upgrade OR downgrade OR \"price target\" OR \"initiates coverage\" OR outperform OR underperform",
        Art.UEBERNAHME to "acquires OR acquisition OR takeover OR merger OR buyout OR \"agrees to buy\"",
        Art.KAPITAL to "buyback OR \"stock split\" OR \"share offering\" OR \"capital increase\" OR \"dividend hike\" OR \"special dividend\"",
    )

    // Abschnitte in Tagen vor heute: eine Suche liefert nur etwa hundert Treffer, vier Abschnitte decken die 30 Tage.
    private val ABSCHNITTE = listOf(0 to 7, 7 to 14, 14 to 22, 22 to ARCHIV_TAGE)

    // Adressen der Google-News-Suche je Abschnitt, neuester zuerst; leer fuer Arten ohne Nachladen.
    fun archivAdressen(art: Art, deutsch: Boolean, heute: LocalDate): List<String> {
        val worte = (if (deutsch) STICHWORTE_DE else STICHWORTE_EN)[art] ?: return emptyList()
        val grund = "($worte) " + if (deutsch) "Aktie" else "stock"
        return ABSCHNITTE.map { (von, bis) ->
            val zeit = if (von == 0) "when:${bis}d" else "after:${heute.minusDays(bis.toLong())} before:${heute.minusDays(von.toLong())}"
            Rss.googleSuche("$grund $zeit", deutsch)
        }
    }

    // Gespeicherte Treffer als Schlagzeilen: nur die der gewaehlten Art und der letzten 30 Tage, ohne Doppelte.
    fun archivEintraege(meldungen: List<LaenderZusatz.Meldung>, art: Art, jetzt: Long): List<Rss.Eintrag> {
        val roh = meldungen.filter { it.zeit > 0 && art in NachrichtArt.arten(it.titel) }.map {
            Rss.Eintrag(
                titel = it.titel, link = it.link, zeit = it.zeit, herausgeber = it.quelle, quelle = "Google News",
                kategorien = setOf(Rss.Kategorie.UNTERNEHMEN),
            )
        }
        return Rss.zusammen(listOf(roh), jetzt, ARCHIV_TAGE)
    }

    fun alsMeldung(e: Rss.Eintrag) = LaenderZusatz.Meldung(e.titel, e.link, e.zeit, e.herausgeber)

    // Der Titel nennt einen Wert aus dem Depot (Begriffe aus Wichtig.depotBegriffe).
    fun imDepot(titel: String, begriffe: List<String>): Boolean = begriffe.any { Wichtig.trifft(titel, it) }

    // Schlagzeilen zum Depot: die der Liste, die einen Depotwert nennen, dazu die Nachrichten je Wert; neueste zuerst.
    fun depotListe(alle: List<Rss.Eintrag>, jeWert: List<Rss.Eintrag>, begriffe: List<String>, jetzt: Long, tage: Int = ARCHIV_TAGE): List<Rss.Eintrag> =
        Rss.zusammen(listOf(alle.filter { imDepot(it.titel, begriffe) }, jeWert), jetzt, tage)
}
