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

    // Gattung in Klammern: „Alphabet (A)“ wird „Alphabet“.
    private val KLASSE = Regex("""\s*\([A-Za-z]\)""")

    // „Mercedes-Benz Group“ wird „Mercedes-Benz“, „Volkswagen Vz.“ wird „Volkswagen“.
    fun kurzname(name: String): String =
        name.replace(KLASSE, " ").replace(ZUSATZ, " ").replace(Regex("""\s+"""), " ").trim().trim(',', '.', '-', '&').trim()

    // Kuerzel, die auch gewoehnliche Woerter sind, zaehlen nur mit Dollarzeichen davor, etwa $ALL.
    private val WOERTER = setOf(
        "A", "AI", "ALL", "AN", "ARE", "BE", "BIG", "CAN", "CAR", "CAT", "DD", "DIE", "DAS", "DER", "EUR", "USD", "ETF", "CEO", "CFO",
        "EPS", "FOR", "FUN", "GO", "HAS", "IPO", "IT", "KEY", "LOW", "MAIN", "NEW", "NOW", "ON", "ONE", "OPEN", "OUT", "REAL", "SEE",
        "SO", "THE", "TOP", "TWO", "UP", "USA", "WELL", "YOLO", "ATH", "DAX", "KGV", "API", "APP", "BUY", "SELL", "HOLD", "LOVE",
        "FAST", "TRUE", "PLAY", "RUN", "WAY", "PM", "AM", "EU", "UK", "US", "OK", "TV", "IMO", "LOL", "TLDR", "EDIT", "PUT", "CALL",
        "MSCI", "DOW", "DTE", "SPX", "NDX", "VIX", "RUT", "DJIA", "DJI",
    )

    // Kuerzel von Indizes, die bei einem Wert zufaellig gleich lauten (SPX bei Spirax); zaehlen nie, auch nicht mit $.
    private val KUERZEL_INDEX = setOf("SPX", "NDX", "VIX", "RUT", "DJIA", "DJI")

    // Namen, die auch gewoehnliche Woerter sind. Solche Werte zaehlen nur ueber ihr Kuerzel.
    private val NAMEN_NUR_KUERZEL = setOf(
        "block", "target", "gap", "snap", "unity", "match", "carrier", "progressive", "booking", "ball", "crown", "best", "pool",
        "global", "united", "general", "american", "first", "deutsche", "continental", "real", "energy",
        "news", "investor",
    )

    // Namen von Indizes, Boersen und Plattformen. Ein Treffer zaehlt nur, wenn Aktie, Kursziel oder Aehnliches
    // direkt folgt (etwa „Dow Aktie“), sonst ueber $-Kuerzel oder Kuerzel in Grossbuchstaben.
    private val NAMEN_INDEX = setOf(
        "dow", "nasdaq", "msci", "dax", "mdax", "sdax", "tecdax", "nikkei", "ftse", "russell", "euronext", "reddit", "stoxx", "imi",
    )

    // Namen, die auch gewoehnliche Woerter oder Ortsnamen sind. Ein Treffer zaehlt nur gross geschrieben mitten im
    // Satz (nicht in Titeln, in denen jedes Wort gross beginnt) oder mit Aktie, Kursziel, Dividende, Earnings dahinter.
    private val NAMEN_ALLTAG = setOf(
        "aberdeen", "affirm", "chime", "coherent", "compass", "diploma", "douglas", "dover", "everest", "flex", "ford", "fox", "frontline",
        "gemini", "hera", "indus", "init", "lucid", "mara", "mosaic", "nel", "next", "norma", "orion", "pandora", "partners",
        "rational", "recruit", "screen", "sea", "shell", "shift", "southern", "tapestry", "vale", "waters", "weir",
    )

    // Wie NAMEN_ALLTAG, aber auch im Deutschen Hauptwoerter oder sonst gross geschrieben: Grossschreibung allein
    // reicht nicht, es braucht Aktie, Kursziel, Dividende oder Earnings dahinter.
    private val NAMEN_ALLTAG_GROSS = setOf(
        "admiral", "arm", "disco", "erste", "evolution", "orange", "safran", "sage", "verbund", "visa",
    )

    // Woerter hinter einem Namen, die ihn eindeutig als Wertpapier ausweisen.
    private val WERTPAPIER_NACH = setOf("aktie", "aktien", "aktienkurs", "kursziel", "dividende", "quartalszahlen", "earnings", "ticker")
    private val WERTPAPIER_NACH_ENGLISCH = setOf("stock", "shares")
    private val WERTPAPIER_ENGLISCH_FOLGE = setOf("price", "in", "is", "was", "has", "dropped", "jumped", "soars")
    private val WERTPAPIER_VOR = setOf("aktie", "aktien", "kurs", "kursziel")

    private val WORT = Regex("""[\p{L}\p{N}]+""")

    private enum class Stufe { FREI, ALLTAG, ALLTAG_GROSS, INDEX }

    private fun stufeVon(kurz: String): Stufe {
        val k = kurz.lowercase()
        return when {
            k in NAMEN_INDEX -> Stufe.INDEX
            k in NAMEN_ALLTAG -> Stufe.ALLTAG
            k in NAMEN_ALLTAG_GROSS -> Stufe.ALLTAG_GROSS
            else -> Stufe.FREI
        }
    }

    // Beginnt fast jedes laengere Wort gross (Schlagzeile in Title Case oder Grossbuchstaben)? Dann sagt die
    // Grossschreibung nichts.
    private fun alleGross(text: String): Boolean {
        val w = WORT.findAll(text).map { it.value }.filter { it.length >= 3 && it.any(Char::isLetter) }.toList()
        return w.size >= 4 && w.count { it[0].isUpperCase() } * 5 >= w.size * 3
    }

    // Steht das Wort am Satz- oder Zeilenanfang?
    private fun amAnfang(text: String, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        if (i < 0) return true
        return text[i] in ".!?:;\n\r\"'„“”‘’(*#>-–—…"
    }

    private fun eindeutig(text: String, m: MatchResult, stufe: Stufe): Boolean {
        val nach = WORT.findAll(text.substring(m.range.last + 1, minOf(text.length, m.range.last + 1 + 40))).map { it.value.lowercase() }.take(2).toList()
        val davor = WORT.findAll(text.substring(maxOf(0, m.range.first - 40), m.range.first)).map { it.value.lowercase() }.toList().takeLast(2)
        val n1 = nach.getOrNull(0)
        if (stufe == Stufe.INDEX) return n1 in WERTPAPIER_NACH || (n1 in WERTPAPIER_NACH_ENGLISCH && nach.getOrNull(1) == "price")
        if (m.value.first().isLowerCase()) return false
        if (n1 in WERTPAPIER_NACH) return true
        if (n1 in WERTPAPIER_NACH_ENGLISCH && nach.getOrNull(1) in WERTPAPIER_ENGLISCH_FOLGE) return true
        if (davor.size == 2 && davor[0] in WERTPAPIER_VOR && davor[1] in setOf("von", "der", "des")) return true
        return stufe == Stufe.ALLTAG && !amAnfang(text, m.range.first) && !alleGross(text)
    }

    // Vorbereitete Suchmuster fuer einen Wert, damit viele Beitraege schnell durchsucht werden koennen.
    class Sucher(val k: Kandidat) {
        private val basis = k.symbol.substringBefore('.').substringBefore('-').uppercase()
        private val dollar = basis.takeIf { it.isNotEmpty() && it !in KUERZEL_INDEX }?.let { Regex("""\$${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE) }
        // Kuerzel in Grossbuchstaben nur ab drei Buchstaben und wenn es kein gewoehnliches Wort ist.
        private val kuerzel = basis.takeIf { it.length >= 3 && it !in WOERTER && it.all { c -> c.isLetter() } }
            ?.let { Regex("""(?<![A-Za-z0-9$])${Regex.escape(it)}(?![A-Za-z0-9])""") }
        private val kurz = kurzname(k.name)
        // Nur einwortige Namen sind mehrdeutig; „Dow Jones“ oder „Orange Belgium“ nennen mehr als das Wort.
        private val stufe = if (kurz.any { it.isWhitespace() }) Stufe.FREI else stufeVon(kurz)
        private val name = kurz.takeIf { it.length >= 3 && it.lowercase() !in NAMEN_NUR_KUERZEL }
            ?.let { Regex("""(?<![\p{L}\p{N}])${Regex.escape(it)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) }
        // Voller Name („Lucid Group Inc“), wenn das Kurzwort mehrdeutig ist: er ist eindeutig.
        private val voll = k.name.trim().trimEnd('.').takeIf { stufe != Stufe.FREI && it.any(Char::isWhitespace) }
            ?.let { Regex("""(?<![\p{L}\p{N}])${Regex.escape(it)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) }

        // Laenge des Kurznamens, um zu erkennen, dass ein Name in einem laengeren steckt.
        val laenge: Int get() = kurz.length

        // Mit $-Zeichen oder in Grossbuchstaben genanntes Kuerzel.
        fun kennung(text: String): Boolean = dollar?.containsMatchIn(text) == true || kuerzel?.containsMatchIn(text) == true

        // Stellen, an denen der Name den Wert eindeutig nennt.
        fun nameSpannen(text: String): List<IntRange> {
            val re = name ?: return emptyList()
            if (stufe == Stufe.FREI) return re.findAll(text).map { it.range }.toList()
            val eindeutige = re.findAll(text).filter { m ->
                // „Dow Jones“ ist der Index, nicht Dow Inc.
                !(stufe == Stufe.INDEX && text.substring(m.range.last + 1).trimStart().startsWith("Jones", true)) && eindeutig(text, m, stufe)
            }.map { it.range }.toList()
            return eindeutige + (voll?.findAll(text)?.map { it.range }?.toList() ?: emptyList())
        }

        fun findet(text: String): Boolean = kennung(text) || nameSpannen(text).isNotEmpty()
    }

    // Mit $-Zeichen immer, als Kuerzel in Grossbuchstaben, sonst ueber den Namen.
    fun nennt(text: String, k: Kandidat): Boolean = Sucher(k).findet(text)

    // Genannte Werte; steckt ein Name in einem laengeren genannten („Siemens“ in „Siemens Energy“), zaehlt nur der laengere.
    fun werte(b: Beitrag, sucher: List<Sucher>): List<Kandidat> {
        val t = b.titel + "\n" + b.text
        val treffer = sucher.filter { it.findet(t) }
        if (treffer.size < 2) return treffer.map { it.k }
        val spannen = treffer.associateWith { it.nameSpannen(t) }
        return treffer.filter { s ->
            s.kennung(t) || spannen.getValue(s).any { sp ->
                treffer.none { o -> o !== s && o.laenge > s.laenge && spannen.getValue(o).any { it.first <= sp.first && sp.last <= it.last } }
            }
        }.map { it.k }
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

    // Zweitgattungen derselben Firma (Alphabet A und C, Berkshire A und B) zaehlen zusammen unter dem ersten Kandidaten
    // der Gruppe; Kuerzel ohne Boersenzusatz gelten als US-Notierungen.
    fun vertreter(kandidaten: List<Kandidat>): Map<String, Kandidat> {
        val gruppen = GleicheFirma.gruppen(kandidaten.map { GleicheFirma.Eintrag(it.symbol, it.name, it.symbol.substringAfter('.', "US"), null) })
        val aus = HashMap<String, Kandidat>()
        for (g in gruppen) if (g.size > 1) for (i in g) aus[kandidaten[i].symbol] = kandidaten[g.first()]
        return aus
    }

    // Ein Beitrag zaehlt je Wert einmal. Gezaehlt werden die 24 Stunden vor jetzt. Vorher: die 24 Stunden davor,
    // aus den Beitraegen selbst, wenn diese zwei Tage zurueckreichen (direkt), sonst aus dem gespeicherten Stand von
    // vor etwa 24 Stunden (vorstand: Zahl je Kuerzel), sonst null. Meistgenannte zuerst.
    fun zaehlen(
        beitraege: List<Beitrag>, kandidaten: List<Kandidat>, jetzt: Long, vorstand: Map<String, Int>? = null,
        direkt: Boolean = beitraege.minOfOrNull { it.zeit }?.let { it <= jetzt - 2 * TAG } == true,
    ): List<Zaehlung> {
        val heute = HashMap<String, Int>()
        val davor = HashMap<String, Int>()
        val punkte = HashMap<String, Int>()
        val namen = HashMap<String, String>()
        val einzeln = kandidaten.distinctBy { it.symbol }
        val sucher = einzeln.map(::Sucher)
        val vertreter = vertreter(einzeln)
        for (b in beitraege.distinctBy { it.id }) {
            val alter = jetzt - b.zeit
            if (alter < 0 || alter >= 2 * TAG) continue
            if (alter >= TAG && !direkt) continue
            for (k in werte(b, sucher).map { vertreter[it.symbol] ?: it }.distinctBy { it.symbol }) {
                namen[k.symbol] = k.name
                if (alter < TAG) {
                    heute[k.symbol] = (heute[k.symbol] ?: 0) + 1
                    punkte[k.symbol] = (punkte[k.symbol] ?: 0) + b.punkte
                } else davor[k.symbol] = (davor[k.symbol] ?: 0) + 1
            }
        }
        val frueher: (String) -> Int? = { s -> if (direkt) davor[s] ?: 0 else vorstand?.let { it[s] ?: 0 } }
        return heute.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { punkte[it.key] ?: 0 })
            .map { (s, n) -> Zaehlung(s, namen[s].orEmpty(), n, frueher(s), punkte[s] ?: 0) }
    }
}
