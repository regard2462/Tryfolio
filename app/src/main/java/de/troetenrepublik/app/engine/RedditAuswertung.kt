package de.troetenrepublik.app.engine

// Auswertung von Reddit-Beitraegen: genannte Werte, Haeufigkeit in den deutschen Boersen-Subreddits und
// Richtung eines Beitrags. Wortregeln, gerechnet auf dem Geraet, keine Empfehlung.
object RedditAuswertung {

    // Text gekuerzt, Zeit in Millisekunden.
    data class Beitrag(
        val id: String,
        val titel: String,
        val text: String,
        val sub: String,
        val punkte: Int,
        val kommentare: Int,
        val zeit: Long,
        val link: String,
    )

    // symbol ist das Kuerzel in der App.
    data class Kandidat(val symbol: String, val name: String)

    // vorher: die 24 Stunden davor, null wenn die Beitraege nicht so weit reichen.
    data class Zaehlung(val symbol: String, val name: String, val erwaehnungen: Int, val vorher: Int?, val punkte: Int)

    data class Stimmung(val steigend: Int, val fallend: Int, val offen: Int) {
        val mitRichtung: Int get() = steigend + fallend
    }

    // Kennung der Quelle im Verlauf der Trends, wie die Filter von ApeWisdom.
    const val QUELLE = "reddit-de"

    // Fuer die Rangliste und die Suche auf der Seite eines Werts.
    val DEUTSCHE_SUBS = listOf("wallstreetbetsGER", "mauerstrassenwetten", "Aktien", "Finanzen")

    // Kommen bei der Suche nach einem Wert dazu.
    val ENGLISCHE_SUBS = listOf("wallstreetbets", "stocks", "investing")

    private const val TAG = 86_400_000L

    // Zusaetze, die im Text fast nie mitgeschrieben werden.
    private val ZUSATZ = Regex(
        """\b(ag|se|kgaa|gmbh|co|inc|corp|corporation|company|plc|n\.?v|s\.?a|ltd|limited|holding|holdings|group|vz|st|class [a-c]|adr|& co\.?)\b\.?""",
        RegexOption.IGNORE_CASE,
    )

    // „Mercedes-Benz Group“ wird „Mercedes-Benz“, „Volkswagen Vz.“ wird „Volkswagen“.
    fun kurzname(name: String): String =
        name.replace(ZUSATZ, " ").replace(Regex("""\s+"""), " ").trim().trim(',', '.', '-', '&').trim()

    // Kuerzel, die auch gewoehnliche Woerter sind, zaehlen nur mit Dollarzeichen davor, etwa $ALL.
    private val WOERTER = setOf(
        "A", "AI", "ALL", "AN", "ARE", "BE", "BIG", "CAN", "CAR", "CAT", "DD", "DIE", "DAS", "DER", "EUR", "USD", "ETF", "CEO", "CFO",
        "EPS", "FOR", "FUN", "GO", "HAS", "IPO", "IT", "KEY", "LOW", "MAIN", "NEW", "NOW", "ON", "ONE", "OPEN", "OUT", "REAL", "SEE",
        "SO", "THE", "TOP", "TWO", "UP", "USA", "WELL", "YOLO", "ATH", "DAX", "KGV", "API", "APP", "BUY", "SELL", "HOLD", "LOVE",
        "FAST", "TRUE", "PLAY", "RUN", "WAY", "PM", "AM", "EU", "UK", "US", "OK", "TV", "IMO", "LOL", "TLDR", "EDIT", "PUT", "CALL",
        "MSCI",
    )

    // Namen, die auch gewoehnliche Woerter sind. Solche Werte zaehlen nur ueber ihr Kuerzel.
    private val NAMEN_NUR_KUERZEL = setOf(
        "block", "target", "gap", "snap", "unity", "match", "carrier", "progressive", "booking", "ball", "crown", "best", "pool",
        "global", "united", "general", "american", "first", "deutsche", "continental", "real", "energy",
    )

    // Vorbereitete Suchmuster fuer einen Wert, damit viele Beitraege schnell durchsucht werden koennen.
    class Sucher(val k: Kandidat) {
        private val basis = k.symbol.substringBefore('.').substringBefore('-').uppercase()
        private val dollar = basis.takeIf { it.isNotEmpty() }?.let { Regex("""\$${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE) }
        // Kuerzel in Grossbuchstaben nur ab drei Buchstaben und wenn es kein gewoehnliches Wort ist.
        private val kuerzel = basis.takeIf { it.length >= 3 && it !in WOERTER && it.all { c -> c.isLetter() } }
            ?.let { Regex("""(?<![A-Za-z0-9$])${Regex.escape(it)}(?![A-Za-z0-9])""") }
        private val kurz = kurzname(k.name)
        private val name = kurz.takeIf { it.length >= 3 && it.lowercase() !in NAMEN_NUR_KUERZEL }
            ?.let { Regex("""(?<![\p{L}\p{N}])${Regex.escape(it)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) }

        fun findet(text: String): Boolean = when {
            dollar?.containsMatchIn(text) == true -> true
            kuerzel?.containsMatchIn(text) == true -> true
            else -> name?.containsMatchIn(text) == true
        }
    }

    // Mit $-Zeichen immer, als Kuerzel in Grossbuchstaben, sonst ueber den Namen.
    fun nennt(text: String, k: Kandidat): Boolean = Sucher(k).findet(text)

    fun werte(b: Beitrag, sucher: List<Sucher>): List<Kandidat> {
        val t = b.titel + "\n" + b.text
        return sucher.filter { it.findet(t) }.map { it.k }
    }

    private val STEIGEND = listOf(
        "call", "calls", "long", "moon", "to the moon", "rakete", "🚀", "bullish", "bull", "bulle", "kaufen", "gekauft", "nachgekauft",
        "nachkaufen", "aufgestockt", "all in", "yolo", "tendies", "rally", "ausbruch", "breakout", "undervalued", "unterbewertet",
        "buy the dip", "btfd", "kursziel", "squeeze", "📈", "buy", "bought", "hodl",
    )
    private val FALLEND = listOf(
        "put", "puts", "short", "bearish", "bear", "bär", "baer", "verkauft", "verkaufen", "abverkauf", "crash", "crasht", "absturz",
        "überbewertet", "ueberbewertet", "overvalued", "blase", "bubble", "dump", "📉", "sell", "sold", "rip", "verlust", "loss porn",
        "bag holder", "bagholder", "pleite", "insolvenz",
    )

    private fun treffer(text: String, woerter: List<String>): Int = woerter.count { w ->
        if (w.any { !it.isLetterOrDigit() && it != ' ' }) text.contains(w)
        else Regex("""(?<![\p{L}])${Regex.escape(w)}(?![\p{L}])""", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    // 1 eher steigend, -1 eher fallend, 0 ohne klare Richtung.
    fun richtung(b: Beitrag): Int {
        val t = (b.titel + "\n" + b.text).lowercase()
        val auf = treffer(t, STEIGEND)
        val ab = treffer(t, FALLEND)
        return when {
            auf > ab -> 1
            ab > auf -> -1
            else -> 0
        }
    }

    fun stimmung(beitraege: List<Beitrag>): Stimmung {
        val r = beitraege.map(::richtung)
        return Stimmung(r.count { it > 0 }, r.count { it < 0 }, r.count { it == 0 })
    }

    // Ein Beitrag zaehlt je Wert einmal. Gezaehlt werden die 24 Stunden vor jetzt und, sofern die Beitraege
    // so weit reichen, die 24 Stunden davor. Meistgenannte zuerst.
    fun zaehlen(beitraege: List<Beitrag>, kandidaten: List<Kandidat>, jetzt: Long): List<Zaehlung> {
        val reichtZurueck = beitraege.minOfOrNull { it.zeit }?.let { it <= jetzt - 2 * TAG } == true
        val heute = HashMap<String, Int>()
        val davor = HashMap<String, Int>()
        val punkte = HashMap<String, Int>()
        val namen = HashMap<String, String>()
        val sucher = kandidaten.distinctBy { it.symbol }.map(::Sucher)
        for (b in beitraege.distinctBy { it.id }) {
            val alter = jetzt - b.zeit
            if (alter < 0 || alter >= 2 * TAG) continue
            for (k in werte(b, sucher)) {
                namen[k.symbol] = k.name
                if (alter < TAG) {
                    heute[k.symbol] = (heute[k.symbol] ?: 0) + 1
                    punkte[k.symbol] = (punkte[k.symbol] ?: 0) + b.punkte
                } else davor[k.symbol] = (davor[k.symbol] ?: 0) + 1
            }
        }
        return heute.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { punkte[it.key] ?: 0 })
            .map { (s, n) -> Zaehlung(s, namen[s].orEmpty(), n, if (reichtZurueck) davor[s] ?: 0 else null, punkte[s] ?: 0) }
    }
}
