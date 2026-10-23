package de.troetenrepublik.app.market

// Beitraege von Donald Trump auf Truth Social als Schlagzeilen: alle im Reiter Trump, unter Alle nur die mit Themen.
// Ueberschrift ist der erste Satz, der ganze Beitrag steht im Blatt. Genannte Unternehmen kommen aus dem Katalog
// der App (nur grosse US-Werte mit eindeutigem Namen).
object TruthNews {

    val THEMEN: List<Pair<String, Regex>> = listOf(
        "zoelle" to Regex("(?i)\\btariffs?\\b|\\bimport (tax|duties)\\b|\\bcustoms duties\\b"),
        "handel" to Regex("(?i)\\btrade (deal|war|deficit|agreement|talks)s?\\b|\\btrading partners?\\b"),
        "notenbank" to Regex("(?i)\\bfederal reserve\\b|\\bthe fed\\b|\\bpowell\\b|\\binterest rates?\\b|\\brate cuts?\\b"),
        "boerse" to Regex("(?i)\\bstock market\\b|\\bstocks?\\b|\\bdow( jones)?\\b|\\bs&p\\b|\\bnasdaq\\b|\\bwall street\\b|\\b401\\(?k\\)?s?\\b"),
        "konjunktur" to Regex("(?i)\\beconomy\\b|\\bgdp\\b|\\brecession\\b|\\binflation\\b|\\bjobs (report|numbers)\\b|\\bunemployment\\b|\\bmanufacturing\\b"),
        "energie" to Regex("(?i)\\boil\\b|\\bgas(oline)? prices\\b|\\bopec\\b|\\bdrill\\b|\\benergy prices\\b"),
        "krypto" to Regex("(?i)\\bcrypto\\b|\\bbitcoin\\b|\\bstablecoins?\\b|\\bdigital assets\\b"),
        "steuern" to Regex("(?i)\\btax cuts?\\b|\\btaxes\\b|\\birs\\b|\\bno tax on\\b"),
        "dollar" to Regex("(?i)\\bus dollar\\b|\\bthe dollar\\b|\\bcurrenc(y|ies)\\b"),
        "geopolitik" to Regex(
            "(?i)\\bwars?\\b|\\battacks?\\b|\\bmilitary\\b|\\btroops\\b|\\bmissiles?\\b|\\bnuclear\\b|\\bceasefire\\b|\\binvasion\\b|" +
                "\\bsanctions?\\b|\\bnato\\b|\\beuropean union\\b|\\bthe eu\\b|\\biran\\b|\\bisrael\\b|\\bgaza\\b|\\bhamas\\b|\\brussia\\b|" +
                "\\bputin\\b|\\bukraine\\b|\\bzelensky\\b|\\bnorth korea\\b|\\bvenezuela\\b|\\bairstrikes?\\b|\\bbomb(ing|ed|s)?\\b"
        ),
    )

    // Namen, die zugleich gewoehnliche Woerter oder Orte sind; sie zaehlen nicht als Unternehmen.
    private val KEINE_FIRMA = setOf(
        "Target", "Visa", "Block", "Match", "Shell", "Gap", "Booking", "Progressive", "Travelers", "Allstate", "Southern", "Dominion",
        "Republic", "American", "General", "United", "Union", "First", "Public", "Global", "National", "Trade", "Energy", "Caterpillar",
        "Delta", "Chase", "Fox", "Sherwin", "Moody", "Marathon", "Constellation", "Ball", "Masco", "Mosaic", "Equity", "Realty", "Prudential",
    )

    // Unternehmensnamen fuer die Erkennung: Aktien der grossen US-Indizes aus dem Katalog, ein Wort ab vier Buchstaben.
    fun firmen(): List<String> {
        val k = Catalog.loaded() ?: return emptyList()
        return k.aktien.filter { a -> a.x.any { it == "SP500" || it == "NDX" || it == "DJIA" } }
            .map { it.n.trim() }.filter { n -> n.length >= 4 && ' ' !in n && n !in KEINE_FIRMA && n.first().isUpperCase() }
            .distinct()
    }

    // Starke Stichworte machen einen Beitrag allein wichtig. Schwache wie economy, war oder Firmennamen kommen in
    // politischen Beitraegen staendig vor; sie zaehlen erst zu zweit (verschiedene Themen).
    private val STARK = Regex(
        "(?i)\\btariffs?\\b|\\bimport (tax|duties)\\b|\\bfederal reserve\\b|\\bpowell\\b|\\binterest rates?\\b|\\brate cuts?\\b|" +
            "\\bstock market\\b|\\bdow jones\\b|\\bs&p\\b|\\bnasdaq\\b|\\bopec\\b|\\boil prices?\\b|\\bgas(oline)? prices\\b|\\bbitcoin\\b|" +
            "\\bcrypto\\b|\\bstablecoins?\\b|\\bsanctions?\\b|\\bceasefire\\b|\\bmissiles?\\b|\\bairstrikes?\\b|\\binvasion\\b|" +
            "\\bnuclear\\b|\\btrade (deal|war|deficit|agreement)s?\\b|\\brecession\\b|\\binflation\\b|\\bgdp\\b|\\bjobs report\\b|" +
            "\\btroops\\b|\\bdeclared? war\\b|\\bwar with\\b"
    )

    private val EMPFEHLUNG = Regex("(?i)\\bendorsement\\b|\\bendorse\\b|\\bhas my complete\\b|\\bvote for\\b|\\bprimary\\b|\\bre-?election\\b")

    // Schluessel aus THEMEN und firma:Name fuer genannte Unternehmen; leer, wenn der Beitrag nicht wichtig ist.
    fun themen(text: String, firmen: List<String>): List<String> {
        val aus = ArrayList<String>()
        for ((k, rx) in THEMEN) if (rx.containsMatchIn(text)) aus += k
        for (f in firmen) if (Regex("(?<![A-Za-z])" + Regex.escape(f) + "(?![A-Za-z])").containsMatchIn(text)) aus += "firma:$f"
        Regex("\\$([A-Z]{1,5})\\b").findAll(text).forEach { aus += "firma:" + it.groupValues[1] }
        val themen = aus.distinct()
        // Wahlempfehlungen zaehlen immer dieselben Punkte auf (Strong Military, Lower Taxes): nur mit starkem Stichwort
        val empfehlung = EMPFEHLUNG.containsMatchIn(text)
        val wichtig = STARK.containsMatchIn(text) || (!empfehlung && themen.size >= 2)
        return if (wichtig) themen.take(6) else emptyList()
    }

    // Erster Satz des ersten Absatzes, ungekuerzt.
    fun ueberschrift(text: String): String {
        val erste = text.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return Regex("^(.{25,}?[.!?])(\\s|$)").find(erste)?.groupValues?.get(1)?.trim() ?: erste
    }

    // Schlagzeilen aus dem Feed des Archivs; Beitraege ohne Text (nur Bild oder Video) und geloeschte entfallen.
    fun eintraege(xml: String, q: Rss.Quelle): List<Rss.Eintrag> {
        val firmen = firmen()
        return TruthArchiv.lesen(xml).filter { !it.geloescht && it.text.isNotBlank() }.map { b ->
            Rss.Eintrag(
                titel = ueberschrift(b.text), link = b.link, zeit = b.zeit, zusammenfassung = b.text,
                herausgeber = "Donald J. Trump", quelle = q.name, kategorien = setOf(q.kategorie), quellen = setOf(q.schluessel),
                themen = themen(b.text, firmen),
            )
        }
    }
}
