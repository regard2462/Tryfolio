package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Was Investoren schreiben (5.92), Rechenteil: Briefe von Fondsmanagern an ihre Anleger, durchsucht nach einer Aktie.
// Gelesen werden nur Briefe, die die Herausgeber offen und ohne Anmeldung auf ihre Seiten stellen und deren
// Bedingungen den persoenlichen Gebrauch nicht verbieten, dazu mit Kontaktadresse die Fondsberichte bei der SEC
// (N-CSR, N-CSRS). Alles nur zur Laufzeit auf dem Geraet. Je Brief ein kurzer Auszug (hoechstens zwei Saetze,
// etwa 50 Woerter) rund um die Erwaehnung, die Richtung grob nach einer Wortliste und eine Einordnung des
// Eigeninteresses: haelt der Absender die Aktie, und passt die Aussage dazu.
// Sammelseiten wie hfbestideas.com stehen nur als Link da.
object Investorenbriefe {

    // Art des Absenders, fuer die Einordnung.
    enum class Art { HEDGEFONDS, FONDSGESELLSCHAFT, VERMOEGENSVERWALTER, HOLDING }

    // Ein Absender mit Uebersichtsseite (seite + muster, Gruppe gruppe ist der Link) oder festen Adressen. pdf: Muster
    // des PDF-Links, wenn die Briefseite selbst nur auf das PDF verweist. investor: Kennung in Copytrading (13F).
    // kuerzel: Aktie des Absenders selbst (dann schreibt die Firma ueber sich).
    data class Absender(
        val id: String,
        val name: String,
        val art: Art,
        val seite: String? = null,
        val muster: String? = null,
        val gruppe: Int = 1,
        val basis: String = "",
        val anzahl: Int = 1,
        val fest: List<String> = emptyList(),
        val pdf: String? = null,
        val ohne: String? = null,
        val investor: String? = null,
        val kuerzel: Set<String> = emptySet(),
    )

    // Geprueft am 28.10.2026: offen, ohne Anmeldung, ohne Roboterverbot in den Bedingungen, persoenlicher Gebrauch
    // erlaubt oder nicht untersagt.
    val ABSENDER: List<Absender> = listOf(
        Absender(
            "giverny", "Giverny Capital", Art.VERMOEGENSVERWALTER, "https://givernycapital.com/en/letters-to-our-partners/",
            "href=\"(https://givernycapital\\.com/wp-content/uploads/[^\"]*annual-letter-\\d{4}[^\"]*\\.pdf)\"",
        ),
        Absender(
            "tweedy", "Tweedy, Browne", Art.FONDSGESELLSCHAFT, "https://www.tweedyfunds.com/commentary/",
            "href=\"(https://www\\.tweedyfunds\\.com/article/fund-commentary-q[1-4]-\\d{4}/)\"",
            pdf = "https://www\\.tweedyfunds\\.com/wp-content/uploads/[^\"]*FundCommentary[^\"]*\\.pdf",
        ),
        Absender(
            "southeastern", "Southeastern Asset Management (Longleaf)", Art.FONDSGESELLSCHAFT, "https://southeasternasset.com/mutual-fund-commentaries/",
            "href=\"(https://southeasternasset\\.com/commentary/[1-4]q\\d{2}-[a-z-]+-commentary/)\"", anzahl = 3,
        ),
        Absender(
            "weitz", "Weitz Investment Management", Art.FONDSGESELLSCHAFT,
            fest = listOf("large-cap-equity-fund", "multi-cap-equity-fund", "partners-iii-opportunity-fund")
                .map { "https://weitzinvestments.com/sitefiles/live/documents/funds/commentary/$it-commentary.pdf" },
        ),
        Absender(
            "brown", "Brown Advisory", Art.VERMOEGENSVERWALTER,
            fest = listOf("large-cap-growth-strategy", "large-cap-sustainable-growth-strategy", "small-cap-fundamental-strategy")
                .map { "https://info.brownadvisory.com/us-quarterly-commentary-$it" },
        ),
        Absender(
            "montaka", "Montaka Global Investments", Art.FONDSGESELLSCHAFT, "https://montaka.com/blogs/quarterly-letter/",
            "href=\"(https://montaka\\.com/quarterly-update-q[1-4]-\\d{4}/)\"",
        ),
        Absender(
            "generation", "Generation Investment Management", Art.VERMOEGENSVERWALTER, "https://www.generationim.com/our-strategies/global-equity/",
            "href=\"(/media/[a-z0-9]+/generation-im-q[1-4]-\\d{4}-global-equity-investor-letter\\.pdf)\"", basis = "https://www.generationim.com",
        ),
        Absender(
            "polen", "Polen Capital", Art.VERMOEGENSVERWALTER,
            fest = listOf("focus-growth", "global-growth", "international-growth").map { "https://www.polencapital.com/perspectives/$it-commentary" },
        ),
        // Wie unter Nachrichten > Briefe (market/Rss.kt)
        Absender(
            "oaktree", "Oaktree (Howard Marks)", Art.VERMOEGENSVERWALTER, "https://www.oaktreecapital.com/insights/memos",
            "<a class=\"oc-title-link\" href=\"(/insights/memo/[^\"]+)\"", basis = "https://www.oaktreecapital.com", anzahl = 2, investor = "marks",
        ),
        Absender(
            "markel", "Markel (Tom Gayner)", Art.HOLDING, "https://www.mklgroup.com/who-we-are/shareholder-letters",
            "(https://content\\.markel\\.com/api/public/content/shareholder-letter-\\d{4})", investor = "gayner", kuerzel = setOf("MKL"),
        ),
        Absender(
            "fairfax", "Fairfax (Prem Watsa)", Art.HOLDING, "https://www.fairfax.ca/investors/",
            "href=\"([^\"]*Shareholders?-Letter-\\d{4}[^\"]*\\.pdf)\"", basis = "https://www.fairfax.ca", investor = "watsa",
            kuerzel = setOf("FFH", "FFH.TO", "FRFHF"),
        ),
        Absender(
            "himalaya", "Himalaya Capital (Li Lu)", Art.HEDGEFONDS, "https://www.himcap.com/publications",
            "href=\"([^\"]*\\.pdf)\"", basis = "https://www.himcap.com", anzahl = 2, ohne = "Chinese", investor = "lilu",
        ),
    )

    fun absender(id: String): Absender? = ABSENDER.firstOrNull { it.id == id }

    // Ein gelesener Brief auf dem Geraet. text: Absaetze, durch Zeilenumbrueche getrennt.
    @Serializable
    data class Brief(
        val absender: String,
        val link: String,
        val quartal: String = "",
        val abgerufen: Long = 0L,
        val text: String = "",
        // Bei SEC-Berichten: Name des Fonds und Art der Meldung
        val name: String = "",
        val form: String = "",
    )

    // ------------------------------------------------------------ Uebersichtsseite

    private fun absolut(link: String, basis: String, seite: String): String = when {
        link.startsWith("http") -> link
        link.startsWith("//") -> "https:$link"
        link.startsWith("/") -> (basis.ifBlank { Regex("^https?://[^/]+").find(seite)?.value.orEmpty() }) + link
        else -> seite.substringBeforeLast('/') + "/" + link
    }

    // Links der neuesten Briefe auf der Uebersichtsseite: mit Quartal oder Jahr im Link die neuesten zuerst, sonst in
    // der Reihenfolge der Seite.
    fun links(html: String, a: Absender): List<String> {
        val muster = a.muster ?: return a.fest
        val seite = a.seite.orEmpty()
        val alle = Regex(muster, RegexOption.IGNORE_CASE).findAll(html)
            .map { absolut(it.groupValues[a.gruppe].replace("&amp;", "&"), a.basis, seite) }
            .filter { a.ohne == null || !it.contains(a.ohne, true) }
            .distinct().toList()
        val mitZeit = alle.map { it to (quartal(it.trimEnd('/').substringAfterLast('/'))?.schluessel ?: 0) }
        val geordnet = if (mitZeit.any { it.second > 0 }) mitZeit.sortedByDescending { it.second }.map { it.first } else alle
        return geordnet.take(a.anzahl)
    }

    // PDF-Link auf einer Briefseite nach dem Muster des Absenders; null ohne.
    fun pdfLink(html: String, a: Absender): String? {
        val m = a.pdf ?: return null
        return Regex("href=\"($m)\"", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.replace("&amp;", "&")
    }

    // ------------------------------------------------------------ Text

    private val ENTITAETEN = mapOf(
        "nbsp" to " ", "amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "ndash" to "–", "mdash" to "—",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "hellip" to "…", "rsquor" to "’", "bull" to "•",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß", "eacute" to "é",
        "egrave" to "è", "euro" to "€", "pound" to "£", "trade" to "™", "reg" to "®", "copy" to "©",
    )

    fun entitaeten(s: String): String = s
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("&#[xX]([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("&([A-Za-z]{2,8});")) { m -> ENTITAETEN[m.groupValues[1]] ?: m.value }

    // Lesbarer Text einer Seite: ohne Skripte, Stile, Navigation und Kopf- und Fusszeilen; Bloecke werden Zeilen,
    // Tabellenzellen durch Tabulatoren getrennt (fuer die Bestandsliste).
    fun htmlText(html: String): String {
        // Zeilenumbrueche im Quelltext zaehlen nicht, nur die Bloecke
        var t = html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("\\s+"), " ")
        for (tag in listOf("script", "style", "noscript", "svg", "nav", "header", "footer", "form", "select", "button")) {
            t = t.replace(Regex("<$tag\\b.*?</$tag\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        }
        t = t.replace(Regex("</?(td|th)\\b[^>]*>", RegexOption.IGNORE_CASE), "\t")
        t = t.replace(Regex("<(br|/?p|/?div|/?li|/?tr|/?h[1-6]|/?ul|/?ol|/?table|/?section|/?article|/?blockquote)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = leer(entitaeten(t))
        return t.lines().map { it.replace(Regex("[ \\x0B\\f\\r]+"), " ").replace(Regex(" *\t[ \t]*"), "\t").trim(' ', '\t') }
            .filter { it.isNotEmpty() }.joinToString("\n")
    }

    // Zeilen des PDF-Lesers zu Absaetzen: Silbentrennung am Zeilenende faellt weg, ein Absatz endet nach einem Satzende,
    // wenn die naechste Zeile gross beginnt und der Absatz lang genug ist. Seitenzahlen fallen weg.
    fun pdfText(zeilen: List<String>): String {
        val aus = StringBuilder()
        val sb = StringBuilder()
        fun ende() {
            val t = leer(sb.toString()).replace(Regex("\\s+"), " ").replace(Regex(" ([,.;:!?])"), "$1").trim()
            if (t.isNotEmpty()) aus.append(t).append('\n')
            sb.setLength(0)
        }
        for (roh in zeilen) {
            val z = roh.trim()
            if (z.isEmpty() || Regex("^\\d{1,3}$").matches(z)) continue
            if (z.startsWith("•") || (sb.length >= 200 && sb.trimEnd().lastOrNull() in setOf('.', '!', '?') && z.first().isUpperCase())) ende()
            if (sb.isNotEmpty() && sb.endsWith("-") && z.firstOrNull()?.isLowerCase() == true) sb.setLength(sb.length - 1)
            else if (sb.isNotEmpty()) sb.append(' ')
            sb.append(z)
            if (sb.length >= 3000) ende()
        }
        ende()
        return aus.toString().trimEnd()
    }

    // Saetze eines Absatzes. Abkuerzungen wie „Inc.“, „Corp.“, „U.S.“ oder „z. B.“ beenden keinen Satz.
    private val ABKUERZUNGEN = listOf(
        "Inc.", "Corp.", "Co.", "Ltd.", "Cos.", "Bros.", "Hldgs.", "Mr.", "Ms.", "Mrs.", "Dr.", "St.", "No.", "vs.", "e.g.", "i.e.", "etc.",
        "U.S.", "U.K.", "approx.", "z.B.", "z. B.", "bzw.", "ca.", "Nr.", "Mio.", "Mrd.", "inkl.", "u.a.", "Jan.", "Feb.", "Aug.", "Sept.", "Oct.", "Nov.", "Dec.",
    )
    private const val PUNKT = '\u2024'

    // Geschuetzte, schmale und unsichtbare Leerzeichen, Steuerzeichen und Zeichen aus Symbolschriften der PDFs als
    // normales Leerzeichen.
    private fun leer(s: String): String =
        s.replace(Regex("[\\p{Zs}\\u200B-\\u200D\\uFEFF\\u0000-\\u0008\\u000B-\\u001F\\uE000-\\uF8FF]"), " ")

    fun saetze(absatz: String): List<String> {
        var t = leer(absatz)
        for (a in ABKUERZUNGEN) t = t.replace(Regex("(?<![\\p{L}])" + Regex.escape(a)), a.replace('.', PUNKT))
        // Einzelne Grossbuchstaben mit Punkt (Initialen, „S.A.“)
        t = t.replace(Regex("(?<=\\b[A-Z])\\.(?=[A-Z]?\\.?\\s?[A-Z][a-z])"), PUNKT.toString())
        return t.split(Regex("(?<=[.!?…])[\"”’)]?\\s+(?=[\"„“‘(]?[A-ZÄÖÜ0-9])"))
            .map { it.replace(PUNKT, '.').trim() }.filter { it.isNotEmpty() }
    }

    private fun woerter(s: String): List<String> = Regex("[\\p{L}][\\p{L}'’-]*|\\d[\\d.,%]*").findAll(s).map { it.value }.toList()

    // Fliesstext statt Tabellenzeile, Ueberschrift oder Liste: genug Woerter, genug kleingeschriebene, wenige Zahlen.
    fun fliesstext(satz: String): Boolean {
        if (satz.contains('\t') || satz.length > 1500) return false
        val w = woerter(satz)
        if (w.size < 7) return false
        val klein = w.count { it.first().isLowerCase() }
        val zahlen = w.count { it.first().isDigit() }
        if (Regex("^(sources?|notes?|quelle|quellen|hinweis|anmerkung)\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(satz)) return false
        return klein >= 4 && zahlen * 4 < w.size
    }

    // ------------------------------------------------------------ Name der Aktie

    // Rechtsformen und Zusaetze am Ende, „The“ am Anfang.
    private val RECHTSFORM = Regex(
        "(?i)[,.]?\\s+(inc|incorporated|corp|corporation|co|company|cos|companies|ltd|limited|plc|ag|se|sa|s\\.a|nv|n\\.v|kgaa|" +
            "gmbh|aktiengesellschaft|a/s|asa|ab|oyj|spa|s\\.p\\.a|société européenne|societe europeenne|holdings?|group|" +
            "class [a-c]|common stock|ordinary shares|adr|ads|& co|\\.com)\\.?$",
    )

    fun grundname(name: String): String {
        var n = name.replace(Regex("\\(.*?\\)"), " ").replace(Regex("\\s+"), " ").trim().removePrefix("The ").replace(Regex("(?i)\\.com\\b"), "").trim()
        while (true) {
            val k = RECHTSFORM.replace(n, "").trim().trimEnd(',', '.', '-', '&').trim()
            if (k == n || k.isEmpty()) break
            n = k
        }
        return n
    }

    // Erste Woerter, die allein nichts bezeichnen; dann zaehlen die ersten zwei Woerter.
    private val ALLGEMEIN = setOf(
        "deutsche", "american", "general", "united", "first", "national", "international", "bank", "royal", "china", "taiwan", "japan",
        "new", "global", "southern", "northern", "eastern", "western", "pacific", "atlantic", "canadian", "british", "us", "energy",
        "capital", "digital", "advanced", "applied", "texas", "intercontinental", "union", "standard", "central", "home", "west",
        "east", "north", "south", "berkshire", "banco", "the", "air", "old", "public", "real", "dr", "dr.",
    )

    // Namen, die auch gewoehnliche Woerter sind: zaehlen nur, wenn der Text die Firma auch sicher nennt (voller Name,
    // Kuerzel mit Boerse) oder sie mitten im Satz mehrmals gross schreibt.
    private val MEHRDEUTIG = setOf(
        "target", "block", "shell", "gap", "snap", "unity", "match", "carrier", "progressive", "booking", "ball", "crown", "best", "pool",
        "square", "sea", "on", "now", "fair", "box", "zoom", "focus", "core", "prime", "edge", "spirit", "insight", "align", "chase",
        "dollar", "general", "coherent", "elastic", "progress", "service", "southern", "evolution", "puma", "continental", "global",
        "energy", "union", "visa",
    )

    // Abkuerzungen in Klammern, die kein Boersenkuerzel sind.
    private val KEIN_KUERZEL = setOf(
        "USA", "GDP", "ETF", "CEO", "CFO", "EPS", "IPO", "ESG", "USD", "EUR", "GBP", "FCF", "ROE", "ROIC", "EBIT", "EBITDA", "MSCI", "NAV",
        "SEC", "FED", "AI", "AUM", "YTD", "QTD", "LLC", "LP", "PLC", "AG", "SE", "IT", "EU", "UK", "US", "API", "CAGR", "GLP", "FDA",
    )

    // Andere Namen bekannter Firmen, nach Kuerzel ohne Boersenzusatz.
    private val ZUSATZNAMEN = mapOf(
        "GOOGL" to listOf("Google"), "GOOG" to listOf("Google"), "TSM" to listOf("TSMC"), "META" to listOf("Facebook"),
        "BRK-B" to listOf("Berkshire Hathaway"), "BRK-A" to listOf("Berkshire Hathaway"), "BRK.B" to listOf("Berkshire Hathaway"),
        "2330" to listOf("TSMC"), "005930" to listOf("Samsung"), "NOVO-B" to listOf("Novo Nordisk"), "MC" to listOf("LVMH"),
        "LLY" to listOf("Eli Lilly", "Lilly"), "MA" to listOf("Mastercard"), "V" to listOf("Visa"), "JPM" to listOf("JPMorgan", "JP Morgan"),
        "KO" to listOf("Coca-Cola"), "IBM" to listOf("IBM"), "PG" to listOf("Procter & Gamble", "P&G"), "UNH" to listOf("UnitedHealth"), "HD" to listOf("Home Depot"),
    )

    // Suchmuster fuer eine Aktie. sicher: voller Name aus mehreren Woertern, Zusatznamen, Kuerzel mit Boerse oder $;
    // kurz: das erste bezeichnende Wort. Treffer zaehlen nur gross geschrieben, kurze Abkuerzungen nur in Grossbuchstaben.
    class Suche(name: String, symbol: String) {
        val grund: String = grundname(name)
        val basis: String = symbol.uppercase().let { s ->
            if (Regex("\\.[A-Z]{1,3}$").containsMatchIn(s) && !s.startsWith("BRK.")) s.substringBeforeLast('.') else s
        }
        private val woerterGrund = grund.split(' ').filter { it.isNotBlank() }
        private val kurz: String? = when {
            woerterGrund.size <= 1 -> null
            woerterGrund.first().lowercase().trimEnd('.') in ALLGEMEIN -> woerterGrund.take(2).joinToString(" ").takeIf { woerterGrund.size > 2 }
            else -> woerterGrund.first()
        }?.takeIf { it.length >= 3 }
        // Voller Name ohne Rechtsform ist sicher, wenn er mehrteilig ist oder kein gewoehnliches Wort.
        private val grundSicher = woerterGrund.size > 1 || grund.lowercase() !in MEHRDEUTIG
        private val zusatz = ZUSATZNAMEN[basis.substringBefore('.')].orEmpty()
        private val kurzMehrdeutig = kurz != null && (kurz.lowercase() in MEHRDEUTIG || kurz.lowercase() in ALLGEMEIN)

        private fun wortMuster(w: String): Regex =
            Regex("(?<![\\p{L}\\p{N}&])" + Regex.escape(w).replace(" ", "\\E\\s+\\Q") + "(?![\\p{L}\\p{N}]|-\\p{L})", RegexOption.IGNORE_CASE)

        private val musterGrund = grund.takeIf { it.length >= 2 }?.let(::wortMuster)
        private val musterKurz = kurz?.let(::wortMuster)
        private val musterZusatz = zusatz.map(::wortMuster)
        private val kuerzelRein = basis.substringBefore('-').substringBefore('.').takeIf { it.length in 1..6 && it.all { c -> c.isLetter() } }
        private val musterBoerse = kuerzelRein?.let {
            Regex("(?:NASDAQ|NYSE|Nasdaq|NYSE American|TSX|LSE|XETRA|Xetra|OTC)\\s*:\\s*$it(?![A-Za-z])")
        }
        private val musterDollar = kuerzelRein?.let { Regex("\\$$it(?![A-Za-z])") }
        private val musterKlammer = kuerzelRein?.takeIf { it.length >= 2 && it !in KEIN_KUERZEL }?.let { Regex("\\($it\\)") }

        // Gross geschrieben wie im Text: erster Buchstabe gross; Abkuerzungen bis vier Zeichen ganz in Grossbuchstaben.
        private fun schreibweise(treffer: String, wort: String): Boolean {
            if (!treffer.first().isUpperCase()) return false
            val kompakt = wort.replace(" ", "")
            if (kompakt.length <= 4 && kompakt.all { !it.isLetter() || it.isUpperCase() }) return treffer.replace(Regex("\\s+"), "") == kompakt
            return true
        }

        private fun treffer(text: String, muster: Regex?, wort: String?): List<IntRange> {
            if (muster == null || wort == null) return emptyList()
            return muster.findAll(text).filter { schreibweise(it.value, wort) }.map { it.range }.toList()
        }

        // Sichere Nennung irgendwo im Text.
        fun sicher(text: String): Boolean =
            (grundSicher && treffer(text, musterGrund, grund).isNotEmpty()) ||
                zusatz.indices.any { treffer(text, musterZusatz[it], zusatz[it]).isNotEmpty() } ||
                musterBoerse?.containsMatchIn(text) == true || musterDollar?.containsMatchIn(text) == true ||
                musterKlammer?.containsMatchIn(text) == true

        // Mitten im Satz gross geschrieben (vor dem Treffer steht ein kleines Wort).
        private fun mitten(text: String, r: IntRange): Boolean {
            val davor = text.substring(0, r.first).trimEnd()
            val letztes = Regex("([\\p{L}]+)[,;:]?$").find(davor)?.groupValues?.get(1) ?: return false
            return letztes.first().isLowerCase()
        }

        // Kontext eines Dokuments: ob mehrdeutige Namen dort zaehlen.
        fun kontext(text: String): Boolean {
            if (sicher(text)) return true
            val w = if (!grundSicher) grund else kurz ?: return false
            val m = if (!grundSicher) musterGrund else musterKurz
            return treffer(text, m, w).count { mitten(text, it) } >= 2
        }

        // Stellen, an denen ein Satz die Firma nennt; kontextOk aus kontext() fuer das ganze Dokument.
        fun stellen(satz: String, kontextOk: Boolean): List<IntRange> {
            val l = ArrayList<IntRange>()
            if (grundSicher || kontextOk) l += treffer(satz, musterGrund, grund)
            zusatz.indices.forEach { l += treffer(satz, musterZusatz[it], zusatz[it]) }
            if (kurz != null && (!kurzMehrdeutig || kontextOk)) l += treffer(satz, musterKurz, kurz)
            musterBoerse?.findAll(satz)?.forEach { l += it.range }
            musterDollar?.findAll(satz)?.forEach { l += it.range }
            musterKlammer?.findAll(satz)?.forEach { l += it.range }
            return l.sortedBy { it.first }
        }

        fun nennt(satz: String, kontextOk: Boolean): Boolean = stellen(satz, kontextOk).isNotEmpty()
    }

    // ------------------------------------------------------------ Richtung

    // Wortanfaenge (mit *) oder ganze Woerter und Wendungen, englisch und deutsch.
    private val POSITIV = listOf(
        "contribut*", "outperform*", "attractive*", "undervalued", "compelling", "strong*", "robust", "impressive", "excellent", "beat",
        "beats", "exceeded", "upside", "opportunit*", "confident", "conviction", "favorable", "favourable", "accelerat*", "gain", "gains",
        "gained", "rose", "rallied", "surged", "added to", "initiated", "new position", "purchased", "bought", "increased our",
        "high-quality", "high quality", "durable", "dominant", "well positioned", "well-positioned", "cheap", "bargain", "tailwind*",
        "optimistic", "excited", "best-in-class", "leading", "winner*", "compounder*", "mispriced", "upgrade*",
        "beigetragen", "gekauft", "aufgestockt", "attraktiv*", "unterbewertet*", "stark*", "chance*", "überzeugt", "gestiegen", "zugelegt",
        "solide*", "führend*", "günstig*", "rückenwind", "gewinner*", "zuversichtlich",
    )
    private val NEGATIV = listOf(
        "detract*", "underperform*", "disappoint*", "weak*", "declin*", "fell", "falling", "dropped", "slump*", "headwind*", "overvalued",
        "expensive", "concern*", "worr*", "pressure*", "challeng*", "lost", "loss", "losses", "sold", "exited", "trimmed", "reduced",
        "eliminated", "downgrad*", "slowdown", "slowing", "missed", "uncertain*", "struggl*", "short", "shorted", "fraud*", "pessimis*",
        "deteriorat*", "overhang", "write-down", "impairment*", "lawsuit*", "litigation", "cut", "cuts",
        "verkauft", "reduziert", "belastet*", "enttäusch*", "schwach*", "gefallen", "gesunken", "überbewertet*", "teuer*", "sorge*",
        "gegenwind", "verlust*", "druck", "abgestoßen", "trennten",
    )
    private val VERNEINUNG = setOf("not", "no", "never", "nor", "hardly", "nicht", "kein", "keine", "keinen", "nie", "without", "ohne")

    private class Wortliste(woerter: List<String>) {
        val stamm = woerter.filter { it.endsWith("*") }.map { it.dropLast(1) }
        val ganz = woerter.filter { !it.endsWith("*") && !it.contains(' ') }.toSet()
        val wendung = woerter.filter { it.contains(' ') }
    }
    private val POS = Wortliste(POSITIV)
    private val NEG = Wortliste(NEGATIV)

    private fun zaehlen(tokens: List<String>, text: String, l: Wortliste): Int {
        var n = 0
        tokens.forEachIndexed { i, t ->
            if (t in l.ganz || l.stamm.any { t.startsWith(it) }) {
                val davor = tokens.subList(maxOf(0, i - 4), i)
                n += if (davor.any { it in VERNEINUNG }) -1 else 1
            }
        }
        n += l.wendung.count { text.contains(it) }
        return n
    }

    // 1 eher positiv, -1 eher negativ, 0 neutral oder gemischt. Grob geschaetzt aus einer Wortliste.
    fun richtung(text: String): Int {
        val klein = text.lowercase()
        val tokens = Regex("[\\p{L}][\\p{L}-]*").findAll(klein).map { it.value }.toList()
        // Verneintes Positives zaehlt fuer negativ und umgekehrt
        val p = zaehlen(tokens, klein, POS)
        val n = zaehlen(tokens, klein, NEG)
        val wert = p - n
        return when {
            wert > 0 -> 1
            wert < 0 -> -1
            else -> 0
        }
    }

    // ------------------------------------------------------------ Eigene Angaben im Brief

    enum class Eigen { KAUF, HAELT, VERRINGERT, VERKAUF, SHORT }

    private val E_SHORT = Regex("\\b(short position|short positions|we are short|we were short|shorted|our short|sold short|short seller|leerverkauf\\w*|short gegangen)\\b")
    private val E_VERKAUF = Regex("\\b(sold (our|the|all|out|the remainder|the rest|the balance)|we sold|exited|exit(ed)? (our|the) position|eliminated|fully exited|liquidated|verkauft|ausgestiegen|abgestoßen|trennten uns)\\b")
    private val E_VERRINGERT = Regex("\\b(trimmed|reduced (our|the)|we reduced|pared|reduziert|verringert)\\b")
    private val E_KAUF = Regex("\\b(bought|purchased|initiated|new position|new holding|added to|we added|increased (our|the) (position|stake|holding)|gekauft|aufgestockt|neu ins portfolio|eingestiegen)\\b")
    private val E_HAELT = Regex("\\b(we own|we have owned|our position|our holding|our stake|our investment in|we hold|we continue to own|top contributors?|contributor|detractors?|holdings? in|portfolio holding|position in|unsere position|im portfolio|wir halten|unser bestand)\\b")

    private val E_HANDLUNG = listOf(Eigen.SHORT to E_SHORT, Eigen.VERKAUF to E_VERKAUF, Eigen.VERRINGERT to E_VERRINGERT, Eigen.KAUF to E_KAUF)

    // Satzteil um eine Stelle: bis zum naechsten Semikolon, Doppelpunkt oder „but“, „while“ (deutsch „aber“,
    // „waehrend“) davor und danach. „and“ trennt nicht, Aufzaehlungen wie „A, B and C were detractors“ gehoeren zusammen.
    private val TEILGRENZE = Regex("[;:]|\\s[–—]\\s|\\s(but|while|whereas|aber|während|wohingegen)\\s", RegexOption.IGNORE_CASE)

    // Was der Brief in der Naehe der Nennung ueber die eigene Position sagt: nur im selben Satzteil und hoechstens zwoelf
    // Woerter davor und danach. Kauf, Verkauf, Verringern und Short gehen vor dem blossen Halten (der Handlung am
    // naechsten), damit „we sold Intel and bought Microsoft“ fuer Microsoft ein Kauf ist und „we exited our position
    // in Microsoft“ ein Verkauf.
    fun eigeneAngabe(satz: String, stellen: List<IntRange>): Eigen? {
        var handlung: Pair<Eigen, Int>? = null
        var haelt = false
        for (r in stellen) {
            val anfang = TEILGRENZE.findAll(satz.substring(0, r.first)).lastOrNull()?.range?.last?.plus(1) ?: 0
            val ende = TEILGRENZE.find(satz, minOf(satz.length, r.last + 1))?.range?.first ?: satz.length
            val vor = satz.substring(anfang, r.first).split(Regex("\\s+")).takeLast(12).joinToString(" ")
            val nach = satz.substring(minOf(ende, r.last + 1), ende).split(Regex("\\s+")).take(12).joinToString(" ")
            val fenster = "$vor ${satz.substring(r)} $nach".lowercase()
            val a = vor.length + 1
            val b = a + r.last - r.first + 1
            for ((e, rx) in E_HANDLUNG) {
                for (m in rx.findAll(fenster)) {
                    val abstand = when {
                        m.range.last < a -> a - m.range.last
                        m.range.first >= b -> m.range.first - b
                        else -> 0
                    }
                    val bisher = handlung
                    if (bisher == null || abstand < bisher.second) handlung = e to abstand
                }
            }
            if (E_HAELT.containsMatchIn(fenster)) haelt = true
        }
        return handlung?.first ?: if (haelt) Eigen.HAELT else null
    }

    // Die Firma steht in einer Tabelle mit Zahlen (Bestandsliste, Top-10-Liste, Gewichte): eine Tabellenzeile mit
    // Nennung und Zahl oder die Nennung, hoechstens von Rechtsform und Namensteilen gefolgt, dann ein Anteil in Prozent.
    private val NACH_NAME_ANTEIL = Regex("^(?:\\s+(?:Corp|Inc|Co|Ltd|plc|Class|Cl|[A-Z][\\p{L}.&'’-]*)\\.?,?){0,3}\\s+\\(?\\d{1,3}[.,]\\d{1,2}\\s?%")

    fun inBestandsliste(text: String, suche: Suche, kontextOk: Boolean): Boolean {
        val tabelle = text.lineSequence().any { z ->
            z.contains('\t') && z.length <= 300 && Regex("\\d").containsMatchIn(z) && suche.nennt(z, kontextOk)
        }
        if (tabelle) return true
        return text.lineSequence().any { z ->
            suche.stellen(z, kontextOk).any { r -> NACH_NAME_ANTEIL.containsMatchIn(z.substring(r.last + 1).take(60)) }
        }
    }

    // Werbung fuer das eigene Angebot im Brief.
    private val WERBUNG = Regex(
        "\\b(subscribe (to|now|today)|sign up (for|today|now)|open to new investors|accepting new (investors|clients)|invest with us|" +
            "become a (client|partner|subscriber)|schedule a call|book a (call|meeting)|minimum investment|jetzt abonnieren|jetzt investieren|" +
            "newsletter abonnieren|kunde werden)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun werbung(text: String): Boolean = WERBUNG.containsMatchIn(text)

    // ------------------------------------------------------------ Quartal

    data class Quartal(val jahr: Int, val q: Int) {
        // 0 fuer ein ganzes Jahr (Jahresbrief)
        val schluessel: Int get() = jahr * 10 + (if (q == 0) 4 else q)
        val text: String get() = if (q == 0) jahr.toString() else "Q$q $jahr"
    }

    private val MONATE = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val MONATE_KURZ = mapOf("mar" to 1, "jun" to 2, "sep" to 3, "dec" to 4)
    private val ORDNUNG = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "1st" to 1, "2nd" to 2, "3rd" to 3, "4th" to 4)

    // Quartal oder Jahr aus Link oder Text; mitJahr: eine Jahreszahl allein reicht (Links von Jahresbriefen).
    fun quartal(s: String, mitJahr: Boolean = true): Quartal? {
        val t = s.lowercase()
        Regex("\\bq([1-4])[\\s_-]*(20\\d\\d)\\b").find(t)?.let { return Quartal(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        Regex("(?<![a-z0-9])q([1-4])[\\s_-]*(\\d\\d)(?![0-9])").find(t)?.let { return Quartal(2000 + it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        Regex("(?<![a-z0-9])([1-4])q[\\s_-]*(\\d{2}|20\\d\\d)(?![0-9])").find(t)?.let {
            val j = it.groupValues[2].toInt()
            return Quartal(if (j < 100) 2000 + j else j, it.groupValues[1].toInt())
        }
        Regex("\\b(first|second|third|fourth|1st|2nd|3rd|4th) quarter(?: of)?,? (20\\d\\d)\\b").find(t)?.let {
            return Quartal(it.groupValues[2].toInt(), ORDNUNG.getValue(it.groupValues[1]))
        }
        Regex("\\b([1-4])\\.\\s*quartal\\s*(20\\d\\d)\\b").find(t)?.let { return Quartal(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        Regex("\\b(" + MONATE.joinToString("|") + ")\\s+\\d{1,2},?\\s+(20\\d\\d)\\b").find(t)?.let {
            return Quartal(it.groupValues[2].toInt(), MONATE.indexOf(it.groupValues[1]) / 3 + 1)
        }
        Regex("(?<![a-z])(mar|jun|sep|dec)[a-z]*[\\s_-]?(\\d\\d)(?![0-9])").find(t)?.let {
            return Quartal(2000 + it.groupValues[2].toInt(), MONATE_KURZ.getValue(it.groupValues[1]))
        }
        if (mitJahr) Regex("(?<![0-9])(20\\d\\d)(?![0-9])").findAll(t).map { it.groupValues[1].toInt() }.filter { it in 2000..2099 }.maxOrNull()?.let { return Quartal(it, 0) }
        return null
    }

    // Quartal eines Briefs: aus dem Link, sonst aus dem Anfang des Texts.
    fun quartalBrief(link: String, text: String): Quartal? =
        quartal(link.substringAfterLast('/').ifBlank { link }) ?: quartal(text.take(4000), mitJahr = false)

    // ------------------------------------------------------------ Fundstellen

    // Ein Textstueck; gekuerzt: es endet vor dem Ende seiner Vorlage (die Oberflaeche laesst es dann in die Blende auslaufen).
    data class Stueck(val text: String, val gekuerzt: Boolean = false)

    // Auszug rund um die Nennung: der Satz mit der Nennung, dazu der naechste, solange zusammen hoechstens grenze Woerter.
    // Ein zu langer Satz wird um die Nennung herum gekuerzt.
    fun auszug(saetze: List<String>, i: Int, stelle: IntRange?, grenze: Int = 50): String = auszugStueck(saetze, i, stelle, grenze).text

    fun auszugStueck(saetze: List<String>, i: Int, stelle: IntRange?, grenze: Int = 50): Stueck {
        val satz = saetze[i]
        val w = satz.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (w.size > grenze) {
            val vorWoerter = if (stelle != null) satz.substring(0, stelle.first).split(Regex("\\s+")).count { it.isNotEmpty() } else 0
            val start = (vorWoerter - grenze / 3).coerceIn(0, w.size - grenze)
            return Stueck(w.subList(start, start + grenze).joinToString(" "), gekuerzt = start + grenze < w.size)
        }
        val naechster = saetze.getOrNull(i + 1)
        // Der naechste Satz nur, wenn er vollstaendig ist (endet mit einem Satzzeichen)
        if (naechster != null && fliesstext(naechster) && Regex("[.!?…][\"”’)]?$").containsMatchIn(naechster)) {
            val zusammen = w.size + naechster.split(Regex("\\s+")).count { it.isNotEmpty() }
            if (zusammen <= grenze) return Stueck("$satz $naechster")
        }
        return Stueck(satz)
    }

    fun woerterZahl(s: String): Int = s.split(Regex("\\s+")).count { it.isNotEmpty() && it != "…" }

    // Textstelle rund um einen Auszug im ganzen Brief: der Absatz mit dem Auszug, dazu davor und danach je ein
    // Absatz, solange zusammen hoechstens grenze Woerter. null, wenn der Auszug im Text nicht zu finden ist.
    fun umfeld(text: String, auszug: String, grenze: Int = 250): String? = umfeldStueck(text, auszug, grenze)?.text

    fun umfeldStueck(text: String, auszug: String, grenze: Int = 250): Stueck? {
        fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim()
        val schluessel = norm(auszug.removePrefix("… ").removeSuffix(" …")).take(40)
        if (schluessel.isEmpty()) return null
        val absaetze = text.split('\n').map(::norm).filter { it.isNotEmpty() }
        val i = absaetze.indexOfFirst { it.contains(schluessel) }
        if (i < 0) return null
        val w = absaetze[i].split(' ').filter { it.isNotEmpty() }
        if (woerterZahl(absaetze[i]) > grenze) {
            // Ein zu langer Absatz wird um die Fundstelle herum gekuerzt, wie beim Auszug
            val pos = absaetze[i].indexOf(schluessel)
            val vor = absaetze[i].substring(0, pos).split(' ').count { it.isNotEmpty() }
            val start = (vor - grenze / 3).coerceIn(0, w.size - grenze)
            return Stueck(w.subList(start, start + grenze).joinToString(" "), gekuerzt = start + grenze < w.size)
        }
        val teile = ArrayList<String>()
        teile += absaetze[i]
        var zahl = woerterZahl(absaetze[i])
        // Erst der Absatz davor, dann der danach, jeder nur, solange die Wortzahl bleibt
        absaetze.getOrNull(i - 1)?.let { davor ->
            if (zahl + woerterZahl(davor) <= grenze) {
                teile.add(0, davor)
                zahl += woerterZahl(davor)
            }
        }
        absaetze.getOrNull(i + 1)?.let { danach ->
            if (zahl + woerterZahl(danach) <= grenze) teile += danach
        }
        return Stueck(teile.joinToString("\n\n"))
    }

    // Eine Fundstelle je Brief.
    @Serializable
    data class Fundstelle(
        val absender: String,
        val name: String,
        val link: String,
        val quartal: String = "",
        val schluessel: Int = 0,
        val auszug: String,
        val richtung: Int = 0,
        // Eigen als Name, leer ohne Angabe
        val eigen: String = "",
        val bestand: Boolean = false,
        val werbung: Boolean = false,
        val nennungen: Int = 1,
        val sec: Boolean = false,
        // Der Auszug endet vor dem Ende des Satzes; die Oberflaeche zeigt dort die Blende.
        val gekuerzt: Boolean = false,
    )

    // Bester Satz eines Briefs ueber die Aktie: Fliesstext mit Nennung, mit den meisten Wertungs- und
    // Positionswoertern; bei Gleichstand der erste. null, wenn der Brief die Aktie nicht im Fliesstext nennt.
    fun fundstelle(brief: Brief, suche: Suche, name: String): Fundstelle? {
        val text = brief.text
        if (text.isBlank()) return null
        val kontextOk = suche.kontext(text)
        val saetze = text.split('\n').flatMap { saetze(it) }
        var best = -1
        var bestWert = Int.MIN_VALUE
        var nennungen = 0
        saetze.forEachIndexed { i, s ->
            if (!fliesstext(s)) return@forEachIndexed
            val st = suche.stellen(s, kontextOk)
            if (st.isEmpty()) return@forEachIndexed
            nennungen++
            val klein = s.lowercase()
            val tokens = Regex("[\\p{L}][\\p{L}-]*").findAll(klein).map { it.value }.toList()
            val wert = kotlin.math.abs(zaehlen(tokens, klein, POS)) + kotlin.math.abs(zaehlen(tokens, klein, NEG)) +
                (if (eigeneAngabe(s, st) != null) 2 else 0) - (if (s.length > 600) 2 else 0)
            if (wert > bestWert) { bestWert = wert; best = i }
        }
        if (best < 0) return null
        val st = suche.stellen(saetze[best], kontextOk)
        val stueck = auszugStueck(saetze, best, st.firstOrNull())
        val a = stueck.text
        val q = quartal(brief.quartal, mitJahr = true)
        return Fundstelle(
            absender = brief.absender, name = name, link = brief.link, quartal = q?.text ?: "", schluessel = q?.schluessel ?: 0,
            // Richtung aus dem Satz mit der Nennung; nur wenn er keine zeigt, aus dem ganzen Auszug
            auszug = a, gekuerzt = stueck.gekuerzt, richtung = richtung(saetze[best]).takeIf { it != 0 } ?: richtung(a), eigen = eigeneAngabe(saetze[best], st)?.name ?: "",
            bestand = inBestandsliste(text, suche, kontextOk), werbung = werbung(text), nennungen = nennungen, sec = brief.form.isNotBlank(),
        )
    }

    // ------------------------------------------------------------ Einordnung

    // 13F-Stand eines Absenders zur Aktie (aus Copytrading); KEINE: der Absender meldet 13F, die Aktie steht nicht darin.
    enum class F13 { HAELT, NEU, ERHOEHT, VERRINGERT, VERKAUFT, KEINE }

    // Was ueber die Position bekannt ist, in der Reihenfolge der Anzeige.
    enum class Beleg {
        FIRMA_SELBST, F13_HAELT, F13_NEU, F13_ERHOEHT, F13_VERRINGERT, F13_VERKAUFT, F13_KEINE, SCHEDULE13,
        BRIEF_KAUF, BRIEF_HAELT, BRIEF_VERRINGERT, BRIEF_VERKAUF, BRIEF_SHORT, BESTANDSLISTE, UNBEKANNT,
    }

    enum class Lage { LONG, SHORT, VERKAUFT, FIRMA, UNBEKANNT }

    // Passt die Aussage zur Position.
    enum class Passt { EIGENE_POSITIV, EIGENE_KRITISCH, SHORT_NEGATIV, SHORT_POSITIV, NACH_VERKAUF_KRITISCH, POSITIV_TROTZ_VERKAUF, FIRMA_SELBST }

    data class Einordnung(
        val belege: List<Beleg>,
        val lage: Lage,
        val passt: Passt?,
        val art: Art?,
        val werbung: Boolean,
    ) {
        // Wirbt in eigener Sache: positiv ueber eine eigene Position oder negativ ueber eine eigene Short-Position
        val inEigenerSache: Boolean get() = passt == Passt.EIGENE_POSITIV || passt == Passt.SHORT_NEGATIV || passt == Passt.FIRMA_SELBST
    }

    // Einordnung einer Fundstelle. Die Angabe im Brief ist neuer als der 13F-Stand und geht fuer die Lage vor.
    // Ohne jede Angabe bleibt es bei „keine Position bekannt“, ohne Satz zur Passung.
    fun einordnung(f: Fundstelle, art: Art?, f13: F13?, schedule13: Boolean, firma: Boolean): Einordnung {
        val belege = ArrayList<Beleg>()
        if (firma) belege += Beleg.FIRMA_SELBST
        when (f13) {
            F13.HAELT -> belege += Beleg.F13_HAELT
            F13.NEU -> belege += Beleg.F13_NEU
            F13.ERHOEHT -> belege += Beleg.F13_ERHOEHT
            F13.VERRINGERT -> belege += Beleg.F13_VERRINGERT
            F13.VERKAUFT -> belege += Beleg.F13_VERKAUFT
            F13.KEINE -> belege += Beleg.F13_KEINE
            null -> {}
        }
        if (schedule13) belege += Beleg.SCHEDULE13
        val eigen = f.eigen.takeIf { it.isNotBlank() }?.let { runCatching { Eigen.valueOf(it) }.getOrNull() }
        when (eigen) {
            Eigen.KAUF -> belege += Beleg.BRIEF_KAUF
            Eigen.HAELT -> belege += Beleg.BRIEF_HAELT
            Eigen.VERRINGERT -> belege += Beleg.BRIEF_VERRINGERT
            Eigen.VERKAUF -> belege += Beleg.BRIEF_VERKAUF
            Eigen.SHORT -> belege += Beleg.BRIEF_SHORT
            null -> {}
        }
        if (f.bestand && eigen == null && f13 == null && !schedule13 && !firma) belege += Beleg.BESTANDSLISTE
        val lage = when {
            firma -> Lage.FIRMA
            eigen == Eigen.SHORT -> Lage.SHORT
            eigen == Eigen.VERKAUF -> Lage.VERKAUFT
            eigen != null -> Lage.LONG
            f13 == F13.VERKAUFT -> Lage.VERKAUFT
            f13 != null && f13 != F13.KEINE -> Lage.LONG
            schedule13 || f.bestand -> Lage.LONG
            else -> Lage.UNBEKANNT
        }
        if (belege.isEmpty()) belege += Beleg.UNBEKANNT
        val passt = when {
            lage == Lage.FIRMA -> Passt.FIRMA_SELBST
            f.richtung == 0 -> null
            lage == Lage.LONG -> if (f.richtung > 0) Passt.EIGENE_POSITIV else Passt.EIGENE_KRITISCH
            lage == Lage.SHORT -> if (f.richtung < 0) Passt.SHORT_NEGATIV else Passt.SHORT_POSITIV
            lage == Lage.VERKAUFT -> if (f.richtung < 0) Passt.NACH_VERKAUF_KRITISCH else Passt.POSITIV_TROTZ_VERKAUF
            else -> null
        }
        return Einordnung(belege, lage, passt, art, f.werbung)
    }

    // ------------------------------------------------------------ Zusammenfassung

    data class Zusammenfassung(val absender: Int, val positiv: Int, val negativ: Int, val neutral: Int, val eigeneSache: Int)

    // Je Absender zaehlt die neueste Fundstelle.
    fun zusammenfassung(l: List<Pair<Fundstelle, Einordnung>>): Zusammenfassung {
        val je = l.groupBy { it.first.absender + "|" + it.first.name }.map { (_, g) -> g.maxBy { it.first.schluessel } }
        return Zusammenfassung(
            je.size, je.count { it.first.richtung > 0 }, je.count { it.first.richtung < 0 }, je.count { it.first.richtung == 0 },
            je.count { it.second.inEigenerSache },
        )
    }

    // Neueste zuerst; Fundstellen aelter als monate Monate vor dem neuesten Quartal fallen weg (ohne Quartal bleiben sie).
    fun aktuelle(l: List<Fundstelle>, heute: java.time.LocalDate, monate: Int = 15): List<Fundstelle> {
        val grenze = heute.minusMonths(monate.toLong()).let { it.year * 10 + (it.monthValue - 1) / 3 + 1 }
        return l.filter { it.schluessel == 0 || it.schluessel >= grenze }.sortedByDescending { it.schluessel }
    }

    // ------------------------------------------------------------ SEC: Volltextsuche (efts.sec.gov)

    // Treffer der Volltextsuche: Meldung, CIK, Datei, Art, Eingang, Name des Fonds, Stichtag.
    data class SecTreffer(val adsh: String, val cik: String, val datei: String, val form: String, val tag: String, val name: String, val stichtag: String) {
        val adresse: String get() = "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${adsh.replace("-", "")}/$datei"
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // Suche nach Fondsberichten (N-CSR, N-CSRS), die den Namen nahe bei wort nennen. NEAR(15): hoechstens 15 Woerter
    // Abstand; so fallen Berichte weg, die die Aktie nur in der Bestandsliste fuehren.
    fun secAdresse(name: String, wort: String, von: String, bis: String): String =
        "https://efts.sec.gov/LATEST/search-index?q=" + enc("\"$name\" NEAR(15) $wort") +
            "&forms=N-CSR,N-CSRS&dateRange=custom&startdt=$von&enddt=$bis"

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.text(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    private fun JsonObject.erstes(k: String): String = ((this[k] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.content.orEmpty()

    fun secTreffer(antwort: String): List<SecTreffer> = runCatching {
        val o = json.parseToJsonElement(antwort) as JsonObject
        val hits = ((o["hits"] as? JsonObject)?.get("hits") as? JsonArray).orEmpty()
        hits.mapNotNull { h ->
            val ho = h as? JsonObject ?: return@mapNotNull null
            val id = (ho["_id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val src = ho["_source"] as? JsonObject ?: return@mapNotNull null
            val adsh = src.text("adsh").ifBlank { id.substringBefore(':') }
            val datei = id.substringAfter(':', "")
            val cik = src.erstes("ciks")
            if (adsh.isBlank() || datei.isBlank() || cik.isBlank() || !Regex("^[A-Za-z0-9._-]+$").matches(datei)) return@mapNotNull null
            SecTreffer(
                adsh, cik, datei, src.text("form").ifBlank { src.text("file_type") }, src.text("file_date"),
                fondsName(src.erstes("display_names")), src.text("period_ending"),
            )
        }.distinctBy { it.adsh }
    }.getOrDefault(emptyList())

    // „GABELLI GROWTH FUND (CIK 0000806857)“ wird „Gabelli Growth Fund“; Kurzwoerter bis drei Buchstaben bleiben gross.
    fun fondsName(roh: String): String {
        val n = roh.replace(Regex("\\s*\\(CIK [0-9]+\\)\\s*"), " ").replace(Regex("\\s+"), " ").trim()
        if (n.any { it.isLowerCase() }) return n
        val klein = setOf("OF", "THE", "AND", "FOR", "IN", "ON", "AT", "BY", "TO")
        return n.split(' ').mapIndexed { i, w ->
            when {
                i > 0 && w in klein -> w.lowercase()
                w.length <= 3 && w.all { it.isLetter() } -> w
                else -> w.lowercase().replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }
}
