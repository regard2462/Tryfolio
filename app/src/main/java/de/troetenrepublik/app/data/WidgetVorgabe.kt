package de.troetenrepublik.app.data

// Abbildung der Leisten und Kategorien der App auf Widget-Art und Voreinstellung (0.77): ein langes Antippen legt das Widget an,
// das zu dieser Stelle gehoert, mit der Wahl vorbelegt, die dort gerade gezeigt wird. Die Einstellung nennt dieselben
// Schluessel, die die Einstellungsseite des Widgets schreibt (siehe Widgets.anheften).
object WidgetVorgabe {
    class Vorgabe(val art: String, val einstellung: Map<String, String> = emptyMap())

    // Schluessel fuer die Wahl einer Unterseite (Reiter, Markt, Terminart); gilt fuer Copytrading, Orders, Prognose,
    // Aktienfinder und Termine.
    const val VARIANTE = "variante"

    const val COPY_GEFOLGT = "gefolgt"
    const val COPY_POLITIK = "politik"
    const val COPY_VORSTAENDE = "vorstaende"
    const val COPY_INVESTOREN = "investoren"
    const val COPY_STRATEGIEN = "strategien"
    const val COPY_SENAT = "politik-senat"
    const val COPY_REGIERUNG = "politik-regierung"
    const val ORDERS_ORDERS = "orders"
    const val ORDERS_PLAENE = "plaene"
    const val PM_POLYMARKET = "polymarket"
    const val PM_KALSHI = "kalshi"
    const val PM_MANIFOLD = "manifold"
    const val PM_METACULUS = "metaculus"
    const val PM_GROSS = "gross"
    const val PM_WETTEN = "wetten"
    const val TERMIN_DIVIDENDEN = "dividenden"
    const val TERMIN_WIRTSCHAFT = "wirtschaft"
    const val TERMIN_QUARTALE = "quartale"
    const val TERMIN_IPO = "ipo"

    val COPY = listOf(COPY_POLITIK, COPY_SENAT, COPY_REGIERUNG, COPY_VORSTAENDE, COPY_INVESTOREN, COPY_STRATEGIEN, COPY_GEFOLGT)
    val ORDERS = listOf(ORDERS_ORDERS, ORDERS_PLAENE)
    val PM = listOf(PM_POLYMARKET, PM_KALSHI, PM_MANIFOLD, PM_METACULUS, PM_GROSS, PM_WETTEN)
    val TERMINE = listOf(TERMIN_DIVIDENDEN, TERMIN_WIRTSCHAFT, TERMIN_QUARTALE, TERMIN_IPO)

    private val UNIVERSUM = Regex("[A-Z0-9]{2,12}")
    private val SYMBOL = Regex("[A-Za-z0-9.^=:_-]{1,40}")
    // Schluessel einer Katalogbranche (FinderBranchen), etwa SOFTWARE oder OEL_GAS
    private val BRANCHE = Regex("[A-Z0-9_]{2,30}")

    // Gueltige Varianten je Art; leer: die Art kennt keine. Aktienfinder nimmt jedes Universum.
    fun gueltig(art: String, variante: String?): String? {
        if (variante.isNullOrBlank()) return null
        return when (art) {
            WidgetKarten.COPYTRADING -> variante.takeIf { it in COPY }
            WidgetKarten.ORDERS -> variante.takeIf { it in ORDERS }
            WidgetKarten.PROGNOSE -> variante.takeIf { it in PM }
            WidgetKarten.TERMINE -> variante.takeIf { it in TERMINE }
            WidgetKarten.AKTIENFINDER, WidgetKarten.INDEXBEWEGER -> variante.takeIf { UNIVERSUM.matches(it) }
            // Seit 0.78: der Wert, dessen Signale oder Volatilitaet das Widget zeigt
            WidgetKarten.SIGNALE, WidgetKarten.VOLATILITAET, WidgetKarten.WARNZEICHEN -> variante.takeIf { SYMBOL.matches(it) }
            // Seit 0.77: die Branche, deren beste Aktien das Widget zeigt
            WidgetKarten.BRANCHE -> variante.takeIf { BRANCHE.matches(it) }
            else -> null
        }
    }

    // Schluessel der Karte in WidgetDaten.karten: die Art, bei einer Variante mit ihr dahinter.
    fun kartenSchluessel(art: String, variante: String?): String = gueltig(art, variante)?.let { "$art:$it" } ?: art

    fun art(schluessel: String): String = schluessel.substringBefore(':')
    fun variante(schluessel: String): String? = schluessel.substringAfter(':', "").ifBlank { null }

    private fun mit(art: String, variante: String? = null) =
        Vorgabe(art, gueltig(art, variante)?.let { mapOf(VARIANTE to it) } ?: emptyMap())

    // Seite Maerkte: Reiter (Name des Reiters, klein geschrieben).
    fun maerkte(reiter: String): Vorgabe? = when (reiter.lowercase()) {
        "heatmap" -> heatmap("MARKT")
        "globus" -> Vorgabe(WidgetKarten.GLOBUS)
        "trends" -> Vorgabe(WidgetKarten.TRENDS)
        "bewegungen" -> Vorgabe(WidgetKarten.BEWEGUNGEN)
        "stimmung" -> Vorgabe(WidgetKarten.STIMMUNG)
        "ausblick" -> Vorgabe(WidgetKarten.AUSBLICK)
        "lage" -> Vorgabe(WidgetKarten.LAGE)
        else -> null
    }

    // Abschnitte innerhalb der Maerkte-Reiter mit eigener Ueberschrift.
    fun abschnitt(name: String): Vorgabe? = when (name) {
        "durchbrueche" -> Vorgabe(WidgetKarten.DURCHBRUECHE)
        "fruehwarnung" -> Vorgabe(WidgetKarten.FRUEHWARNUNG)
        "marktphase" -> Vorgabe(WidgetKarten.MARKTPHASE)
        "blasen" -> Vorgabe(WidgetKarten.BLASEN)
        else -> null
    }

    // Copytrading: Reiter POLITIK, VORSTAENDE, INVESTOREN, STRATEGIEN, GEFOLGT; bei Politik der Teil KONGRESS, SENAT, REGIERUNG.
    fun copytrading(reiter: String, politikTeil: String? = null): Vorgabe = when (reiter.uppercase()) {
        "VORSTAENDE" -> mit(WidgetKarten.COPYTRADING, COPY_VORSTAENDE)
        "INVESTOREN" -> mit(WidgetKarten.COPYTRADING, COPY_INVESTOREN)
        "STRATEGIEN" -> mit(WidgetKarten.COPYTRADING, COPY_STRATEGIEN)
        "GEFOLGT" -> mit(WidgetKarten.COPYTRADING, COPY_GEFOLGT)
        else -> mit(
            WidgetKarten.COPYTRADING,
            when (politikTeil?.uppercase()) { "SENAT" -> COPY_SENAT; "REGIERUNG" -> COPY_REGIERUNG; else -> COPY_POLITIK },
        )
    }

    // News: Anbieter-Reiter (Name aus Rss.Anbieter) oder eigener Feed (Kennung).
    fun news(anbieter: String?, feedKennung: String? = null): Vorgabe = when {
        feedKennung != null -> Vorgabe(WidgetKarten.NEWS, mapOf("news" to WidgetDaten.newsFeed(feedKennung)))
        anbieter.equals("ALLE", true) -> Vorgabe(WidgetKarten.NEWS, mapOf("news" to WidgetDaten.NEWS_ALLE))
        anbieter.equals("DEPOT", true) -> Vorgabe(WidgetKarten.NEWS, mapOf("news" to WidgetDaten.NEWS_DEPOT))
        anbieter.equals("GEMERKT", true) -> Vorgabe(WidgetKarten.NEWS, mapOf("news" to WidgetDaten.NEWS_GEMERKT))
        anbieter.equals("WICHTIG", true) || anbieter == null -> Vorgabe(WidgetKarten.NEWS, mapOf("news" to WidgetDaten.NEWS_WICHTIG))
        else -> Vorgabe(WidgetKarten.NEWS, mapOf("news" to WidgetDaten.newsAnbieter(anbieter)))
    }

    // Orders: Reiter 0 Orders, 1 Sparplaene.
    fun orders(reiter: Int): Vorgabe = mit(WidgetKarten.ORDERS, if (reiter == 1) ORDERS_PLAENE else ORDERS_ORDERS)

    // Prognosemaerkte: Reiter POLYMARKET, KALSHI, MANIFOLD, METACULUS, GROSS, WETTEN.
    fun prognose(reiter: String): Vorgabe = mit(WidgetKarten.PROGNOSE, reiter.lowercase())

    // Aktienfinder: Universum (Code), das Radar zeigt die Punkte desselben Universums.
    fun aktienfinder(universum: String): Vorgabe = mit(WidgetKarten.AKTIENFINDER, universum)

    // Seit 0.78 (Widgets zu Abschnitten): Themenkoerbe und Sektoren im Reiter Trends, Beitrag zum Index unter der Heatmap
    // (Quelle: Markt oder Index; Depot und Watchlist haben kein Widget), Kongress-Flow, Signale-Kasten und Volatilitaet
    // der Optionenkarte (je mit dem Wert).
    fun themen(): Vorgabe = Vorgabe(WidgetKarten.THEMEN)
    fun sektoren(): Vorgabe = Vorgabe(WidgetKarten.SEKTOREN)
    fun indexBeweger(quelle: String): Vorgabe? = if (quelle == "DEPOT" || quelle == "WATCHLIST") null else mit(WidgetKarten.INDEXBEWEGER, quelle)
    fun kongressFlow(): Vorgabe = Vorgabe(WidgetKarten.KONGRESSFLOW)
    fun signale(symbol: String): Vorgabe? = gueltig(WidgetKarten.SIGNALE, symbol)?.let { mit(WidgetKarten.SIGNALE, it) }
    fun volatilitaet(symbol: String): Vorgabe? = gueltig(WidgetKarten.VOLATILITAET, symbol)?.let { mit(WidgetKarten.VOLATILITAET, it) }

    // Seit 0.77: Meistgekauft (Alle Fonds im Quartal) und Haus-Rangliste der Analysten, beide ohne Einstellung.
    fun meistgekauft(): Vorgabe = Vorgabe(WidgetKarten.MEISTGEKAUFT)
    fun haeuser(): Vorgabe = Vorgabe(WidgetKarten.HAEUSER)

    // Seit 0.77: Konvergenz, seltene Insiderkaeufe, Insider-Simulator (Standardregel), Vorschlagsdepot und Praesidialdokumente ohne
    // Einstellung, Branche (der Katalogschluessel) und Warnzeichen (der Wert).
    fun konvergenz(): Vorgabe = Vorgabe(WidgetKarten.KONVERGENZ)
    fun insiderSelten(): Vorgabe = Vorgabe(WidgetKarten.INSIDERSELTEN)
    fun insiderSimulator(): Vorgabe = Vorgabe(WidgetKarten.INSIDERSIM)
    fun vorschlagsdepot(): Vorgabe = Vorgabe(WidgetKarten.VORSCHLAG)
    fun praesidial(): Vorgabe = Vorgabe(WidgetKarten.PRAESIDIAL)
    fun branche(schluessel: String): Vorgabe? = gueltig(WidgetKarten.BRANCHE, schluessel)?.let { mit(WidgetKarten.BRANCHE, it) }
    fun warnzeichen(symbol: String): Vorgabe? = gueltig(WidgetKarten.WARNZEICHEN, symbol)?.let { mit(WidgetKarten.WARNZEICHEN, it) }

    // Heatmap: Quelle (HeatmapQuelle oder Index-Schluessel).
    fun heatmap(quelle: String, zeitraum: Int = 0): Vorgabe =
        Vorgabe(WidgetKarten.HEATMAP, mapOf("heatmapQuelle" to quelle, "heatmapZeit" to zeitraum.coerceIn(0, 5).toString()))

    // Watchlist-Reiter: die gewaehlte Liste mit Name und Symbolen als eigene Liste des Widgets Werte.
    fun watchliste(name: String, symbole: List<String>): Vorgabe =
        Vorgabe(WidgetKarten.WERTE, mapOf("quelle" to "liste", "name" to name.trim().take(40), "liste" to symbole.distinct().joinToString(",")))

    // Kalender: TERMIN_*.
    fun termine(art: String): Vorgabe = mit(WidgetKarten.TERMINE, art)

    // Depot-Check-Seiten und Werkzeuge in Analytics (Name aus PortfolioTool); null ohne eigenes Widget.
    fun werkzeug(tool: String): Vorgabe? = when (tool.uppercase()) {
        "PERFORMANCE" -> Vorgabe(WidgetKarten.PERFORMANCE)
        "CHECK" -> Vorgabe(WidgetKarten.DEPOTCHECK)
        "DIVERSIFICATION" -> Vorgabe(WidgetKarten.STREUUNG)
        "REBALANCE" -> Vorgabe(WidgetKarten.UMSCHICHTEN)
        "CRASH" -> Vorgabe(WidgetKarten.CRASHTEST)
        "DIVIDENDS" -> Vorgabe(WidgetKarten.DIVIDENDEN)
        "SPARPLAN" -> orders(1)
        "FINDER" -> Vorgabe(WidgetKarten.AKTIENFINDER)
        "STILLHALTER" -> Vorgabe(WidgetKarten.STILLHALTER)
        "HAEUSER" -> Vorgabe(WidgetKarten.HAEUSER)
        else -> null
    }

    // Depotverlauf: das Depot-Widget mit dem Verlauf seit Jahresbeginn.
    fun depotverlauf(): Vorgabe = Vorgabe(WidgetKarten.DEPOT, mapOf("zeitraum" to WidgetDaten.ZEIT_JAHR))
}
