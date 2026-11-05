package de.troetenrepublik.app.engine

// Erklaerung unter den Trends eines Werts (5.97): ein Teilsatz je sichtbarer Quelle, in der Reihenfolge der Zeilen.
// Dazu, wann Hacker News erscheint.
object TrendsErklaerung {
    enum class Teil { REDDIT, REDDIT_DEUTSCH, VERLAUF, BEITRAEGE, YAHOO, WIKIPEDIA, HACKER_NEWS, MASTODON, BLUESKY, NAMEN }

    // Welche Zeilen der Trendskarte gerade Daten haben
    data class Sichtbar(
        val redditApewisdom: Boolean = false, val redditDeutsch: Boolean = false, val redditBeitraege: Boolean = false,
        val yahoo: Boolean = false, val wikipedia: Boolean = false, val hackerNews: Boolean = false,
        val mastodon: Boolean = false, val bluesky: Boolean = false,
    )

    // Nur die sichtbaren Teile, in der Reihenfolge der Zeilen auf der Karte
    fun teile(s: Sichtbar): List<Teil> = buildList {
        if (s.redditApewisdom) add(Teil.REDDIT)
        if (s.redditDeutsch) add(Teil.REDDIT_DEUTSCH)
        if (s.redditApewisdom || s.redditDeutsch) add(Teil.VERLAUF)
        if (s.redditBeitraege) add(Teil.BEITRAEGE)
        if (s.yahoo) add(Teil.YAHOO)
        if (s.wikipedia) add(Teil.WIKIPEDIA)
        if (s.hackerNews) add(Teil.HACKER_NEWS)
        if (s.mastodon) add(Teil.MASTODON)
        if (s.bluesky) add(Teil.BLUESKY)
        if (s.hackerNews || s.mastodon || s.bluesky) add(Teil.NAMEN)
    }

    // Saetze je Teil, durch Leerzeichen verbunden; leere bleiben weg
    fun text(teile: List<Teil>, satz: (Teil) -> String): String = teile.map(satz).filter { it.isNotBlank() }.joinToString(" ")

    val TECHNIK_SEKTOREN = setOf("Technology", "Communication Services")
    const val HN_MINDESTENS = 3

    // Beitraege der letzten fuenf Wochen: diese Woche plus vier Wochen Schnitt zuvor
    // Hacker News ist ein Forum ueber Technik; bei anderen Werten zaehlt es fast nur gleichnamige Woerter
    fun hackerNewsZeigen(sektor: String?, woche: Int, schnittVorher: Double): Boolean =
        sektor in TECHNIK_SEKTOREN && (woche + schnittVorher * 4) >= HN_MINDESTENS

    // Auf der Boersengang-Seite nur bei KI- und Software-Firmen
    fun hackerNewsBeiIpo(branche: IpoBewertung.Branche?): Boolean =
        branche == IpoBewertung.Branche.KI || branche == IpoBewertung.Branche.SOFTWARE
}
