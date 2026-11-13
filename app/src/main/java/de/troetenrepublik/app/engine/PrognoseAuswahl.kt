package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Prognose

// Auswahl „Wichtigste“ der Prognosemaerkte und der grossen Wetten: was fuer Maerkte und Anleger zaehlt. Eine Regel
// fuer beide Listen. Raus faellt Sport (auch E-Sport und Spielwetten unter anderem Schlagwort), Unterhaltung,
// Wetter-Tageswetten, Zaehler fuer Beitraege und Erwaehnungen, Kurswetten im Minuten-, Stunden- und Tagestakt,
// Ereignisse mit sehr wenig Umsatz und praktisch entschiedene Maerkte.
object PrognoseAuswahl {
    // Schluessel der Auswahl in den Kategorie-Chips; keine Kategorie der Boersen.
    const val SCHLUESSEL = "wichtigste"

    // Mindestumsatz eines Ereignisses seit Beginn (Polymarket in Dollar, Kalshi in Kontrakten zu hoechstens 1 Dollar).
    const val MIN_VOLUMEN = 50_000.0

    // Mindestumsatz bei Spielgeld (Manifold, in Mana); dort sind die Betraege kleiner.
    const val MIN_VOLUMEN_SPIELGELD = 10_000.0

    // Mindestzahl der Prognostiker bei einer Gemeinschaftsprognose ohne Umsatz (Metaculus).
    const val MIN_TEILNEHMER = 20

    // Praktisch entschieden: jede Wahrscheinlichkeit hoechstens so weit von 0 oder 100 % entfernt.
    const val ENTSCHIEDEN_RAND = 0.02

    // Krypto-Ereignisse, die frueher enden, sind Kurswetten auf Minuten, Stunden oder den Tag.
    const val KRYPTO_MINDESTFRIST_STUNDEN = 48.0

    enum class Grund { SPORT, UNTERHALTUNG, WETTER, ZAEHLER, KURZFRIST, VOLUMEN, ENTSCHIEDEN }

    // Was die Regel von einem Ereignis oder einer Wette braucht. text: Titel samt Kennung der Quelle; schlagworte
    // klein geschrieben; volumen und restStunden null, wenn die Quelle sie nicht liefert.
    data class Merkmale(
        val text: String,
        val schlagworte: Set<String> = emptySet(),
        val chancen: List<Double> = emptyList(),
        val volumen: Double? = null,
        val restStunden: Double? = null,
        val minVolumen: Double = MIN_VOLUMEN,
    )

    private val SPORT_WORTE = setOf(
        "sports", "sport", "esports", "games", "soccer", "football", "basketball", "baseball", "hockey", "tennis", "golf", "mma", "ufc",
        "boxing", "cricket", "rugby", "nba", "nfl", "mlb", "nhl", "ncaa", "f1", "formula-1", "formula 1", "chess",
    )
    private val UNTERHALTUNG_WORTE = setOf(
        "culture", "pop-culture", "pop culture", "entertainment", "movies", "music", "awards", "celebrities", "tv", "gaming",
    )
    private val ZAEHLER_WORTE = setOf("mentions", "tweet-markets", "tweets-markets")
    private val KRYPTO_WORTE = setOf("crypto", "bitcoin", "ethereum", "solana", "xrp", "crypto-prices")

    // Schlagworte, die ein Ereignis als Thema fuer Anleger ausweisen; damit gilt „A vs. B“ nicht als Spielpaarung.
    private val ANLEGER_WORTE = setOf(
        "economy", "economics", "finance", "financials", "business", "companies", "stocks", "geopolitics", "politics", "us-politics",
        "elections", "tech", "science and technology", "ai", "world", "crypto", "fed", "tariffs", "earnings", "middle-east", "ukraine",
        "health", "climate and weather",
    )

    private fun muster(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    // Ligen, Turniere und Sportarten im Titel oder in der Kennung.
    private val SPORT_NAMEN = muster(
        "\\b(nfl|nba|wnba|mlb|nhl|mls|ncaa[a-z]*|ufc|mma|epl|premier league|la liga|laliga|bundesliga|serie a|ligue 1|eredivisie|" +
            "champions league|europa league|conference league|world cup|super bowl|stanley cup|world series|grand prix|formula 1|f1|" +
            "nascar|indycar|motogp|wimbledon|french open|roland garros|australian open|atp|wta|pga|liv golf|ryder cup|" +
            "the masters|olympics?|copa america|copa del rey|fa cup|fifa|uefa|ballon d.or|heisman|mvp|playoffs?|tour de france|ipl|" +
            "t20|cricket|boxing|tennis|league of legends|lol|counter-strike|cs2|dota( 2)?|valorant|lck|lpl|lec|esports?|" +
            "call of duty|overwatch|rocket league)\\b",
    )

    // Maerkte auf ein Spielergebnis.
    private val SPIEL = muster(
        "\\bspread:|\\b(moneyline|over/under|o/u|both teams to score|btts|handicap|total (kills|points|goals|runs|games|sets|maps)|" +
            "map \\d|game \\d|set \\d|touchdowns?|to win the match|match winner|wins? by (ko|tko|submission|decision)|" +
            "anytime (goal ?)?scorer|draw no bet)\\b",
    )

    // Paarung „A vs. B“ oder „A @ B“.
    private val PAARUNG = muster("\\bvs?\\.?\\s|\\s@\\s")

    private val UNTERHALTUNG = muster(
        "\\b(oscars?|academy awards?|grammys?|emmys?|golden globes?|box office|opening weekend|rotten tomatoes|billboard|eurovision|" +
            "album|engaged|pregnan(t|cy)|divorce|wedding|married|marry|dating|baby|celebrity|reality show|bachelor(ette)?|met gala|" +
            "person of the year)\\b",
    )

    private val WETTER = muster(
        "\\b(highest|lowest|high|low|average|max|min)\\s+temp(erature)?s?\\b|\\btemperatures?\\b|\\b(rain|rainfall|snow|snowfall|precipitation)\\b|" +
            "°\\s?[fc]\\b|\\b(hottest|coldest)\\b",
    )

    private val ZAEHLER = muster(
        "\\b(tweets?|retweets?)\\b|(\\bnumber|#) of (posts|tweets|times)\\b|\\bhow many (posts|tweets|times)\\b|\\btruth social posts?\\b|" +
            "\\b\\d+[-–+]?\\d*\\+? posts\\b|\\bwhat will .+ say\\b|\\bmentions?\\b|\\bfollowers\\b",
    )

    // Kurswetten auf kurze Sicht, auch auf Aktien und Indizes.
    private val KURZFRIST = muster("\\bup or down\\b|\\bupdown\\b|\\bhourly\\b|\\b\\d+[- ]?(min|minutes?)\\b")
    private val UHRZEIT = muster("\\b\\d{1,2}(:\\d{2})?\\s?(am|pm)\\b")
    private val KRYPTO = muster("\\b(bitcoin|btc|ethereum|eth|solana|sol|xrp|dogecoin|doge|crypto)\\b")

    // Grund, aus dem ein Eintrag nicht zur Auswahl gehoert; null, wenn er bleibt.
    fun grund(m: Merkmale): Grund? {
        val worte = m.schlagworte
        val t = m.text
        val krypto = worte.any { it in KRYPTO_WORTE } || KRYPTO.containsMatchIn(t)
        return when {
            worte.any { it in SPORT_WORTE } || SPORT_NAMEN.containsMatchIn(t) || SPIEL.containsMatchIn(t) -> Grund.SPORT
            worte.none { it in ANLEGER_WORTE } && PAARUNG.containsMatchIn(t) -> Grund.SPORT
            worte.any { it in UNTERHALTUNG_WORTE } || UNTERHALTUNG.containsMatchIn(t) -> Grund.UNTERHALTUNG
            WETTER.containsMatchIn(t) -> Grund.WETTER
            worte.any { it in ZAEHLER_WORTE } || ZAEHLER.containsMatchIn(t) -> Grund.ZAEHLER
            KURZFRIST.containsMatchIn(t) -> Grund.KURZFRIST
            krypto && (UHRZEIT.containsMatchIn(t) || (m.restStunden != null && m.restStunden < KRYPTO_MINDESTFRIST_STUNDEN)) -> Grund.KURZFRIST
            m.volumen != null && m.volumen < m.minVolumen -> Grund.VOLUMEN
            m.chancen.isNotEmpty() && m.chancen.all { it <= ENTSCHIEDEN_RAND + 1e-9 || it >= 1.0 - ENTSCHIEDEN_RAND - 1e-9 } -> Grund.ENTSCHIEDEN
            else -> null
        }
    }

    fun merkmale(e: Prognose.Ereignis, jetzt: Long): Merkmale = Merkmale(
        // Bei Polymarket ist die Kennung lesbar (Liga und Mannschaften stehen oft nur dort)
        text = e.titel + " " + e.untertitel + if (e.quelle == Prognose.Quelle.POLYMARKET) " " + e.id.replace('-', ' ') else "",
        schlagworte = (e.schlagworte + e.kategorie).map { it.lowercase() }.filter { it.isNotBlank() }.toSet(),
        chancen = e.maerkte.map { it.chance },
        // Ohne Umsatz (Metaculus) zaehlen die Prognostiker, umgerechnet auf dieselbe Schwelle
        volumen = if (e.quelle == Prognose.Quelle.METACULUS) e.teilnehmer.toDouble() else e.volumen,
        restStunden = if (e.ende > 0) (e.ende - jetzt) / 3_600_000.0 else null,
        minVolumen = when (e.quelle) {
            Prognose.Quelle.MANIFOLD -> MIN_VOLUMEN_SPIELGELD
            Prognose.Quelle.METACULUS -> MIN_TEILNEHMER.toDouble()
            else -> MIN_VOLUMEN
        },
    )

    // Eine grosse Wette: Titel des Marktes, Kennung des Ereignisses, Thema und der Preis der gehandelten Seite.
    fun merkmale(w: Prognose.GrosseWette): Merkmale = Merkmale(
        text = w.titel + " " + w.ereignis.replace('-', ' '),
        schlagworte = setOfNotNull(w.thema.lowercase().takeIf { it.isNotBlank() }),
        chancen = listOf(w.preis),
    )

    fun wichtig(e: Prognose.Ereignis, jetzt: Long): Boolean = grund(merkmale(e, jetzt)) == null
    fun wichtig(w: Prognose.GrosseWette): Boolean = grund(merkmale(w)) == null

    // Ereignisse mehrerer Kategorien zusammen: jedes einmal, nach Umsatz der letzten 24 Stunden, dann nach Gesamtumsatz.
    fun ereignisse(alle: List<Prognose.Ereignis>, jetzt: Long): List<Prognose.Ereignis> =
        alle.distinctBy { it.quelle to it.id }.filter { wichtig(it, jetzt) }
            .sortedWith(compareByDescending<Prognose.Ereignis> { it.volumen24h }.thenByDescending { it.volumen }.thenByDescending { it.teilnehmer })

    // Grosse Wetten in der gelieferten Reihenfolge (nach Einsatz).
    fun grosse(alle: List<Prognose.GrosseWette>): List<Prognose.GrosseWette> = alle.filter { wichtig(it) }

    // Kategorien einer Boerse, aus deren Listen die Auswahl entsteht: alle ausser Trends, Sport und Unterhaltung.
    fun sammelKategorien(kategorien: List<Prognose.Kategorie>): List<String> =
        kategorien.map { it.schluessel }.filter { k -> k.isNotBlank() && k.lowercase().removeSuffix("-default").let { it !in SPORT_WORTE && it !in UNTERHALTUNG_WORTE } }
}
