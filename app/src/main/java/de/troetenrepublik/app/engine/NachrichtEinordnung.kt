package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Tendenz.Lage
import de.troetenrepublik.app.engine.Tendenz.Richtung

// Einordnung einer Schlagzeile (5.94): fuer welche Aktie oder welchen Markt sie eher bullish oder bearish ist.
// Firmen erkennt der Katalog (Namensstamm oder Kuerzel im Titel), ihre Richtung kommt aus den Themen von BullBear
// (Gewinnwarnung, Hochstufung, Rueckkauf ...). Fuer allgemeine Meldungen gelten Stichwortregeln wie im Ausblick.
// Grobe Faustregeln, keine Empfehlung; ohne klares Signal bleibt die Liste leer.
object NachrichtEinordnung {

    // symbol: die genannte Aktie (fuer den Link zur Seite), null bei einem ganzen Markt.
    data class Einordnung(val lage: Lage, val symbol: String?)

    private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    // Allgemeine Meldungen: Muster und die Tendenz, die es nahelegt.
    private val MARKT: List<Pair<Regex, Lage>> = listOf(
        r("\\b(cuts?|lowers?|slashes)\\s+(interest\\s+)?rates?\\b|\\brate\\s+cuts?\\b|zinssenkung|senkt\\s+(den\\s+)?(leit)?zins") to Lage(Richtung.BULLISH, Tendenz.AKTIEN),
        r("\\b(raises?|hikes?|lifts?)\\s+(interest\\s+)?rates?\\b|\\brate\\s+hikes?\\b|zinserh(ö|oe)hung|erh(ö|oe)ht\\s+(den\\s+)?(leit)?zins") to Lage(Richtung.BEARISH, Tendenz.AKTIEN),
        r("inflation\\s+(rises|rose|jumps|jumped|accelerates|heats|hotter|higher|steigt|zieht\\s+an)|teuerung\\s+steigt") to Lage(Richtung.BEARISH, Tendenz.ANLEIHEN),
        r("inflation\\s+(eases|eased|cools|cooled|slows|slowed|falls|fell|lower|sinkt|geht\\s+zur(ü|ue)ck|schw(ä|ae)cht)") to Lage(Richtung.BULLISH, Tendenz.ANLEIHEN),
        r("\\boil(\\s+prices?)?\\s+(jumps?|surges?|soars?|rises?|spikes?|rall(y|ies)|climbs?)|(ö|oe)lpreis(e)?\\s+(steigt|steigen|springt|zieht\\s+an|klettert)|opec\\+?\\s+(cuts?|k(ü|ue)rzt)") to Lage(Richtung.BULLISH, Tendenz.OEL),
        r("\\boil(\\s+prices?)?\\s+(falls?|drops?|slumps?|plunges?|tumbles?|sinks?|slides?)|(ö|oe)lpreis(e)?\\s+(f(ä|ae)llt|fallen|sinkt|sinken|bricht\\s+ein|rutscht)") to Lage(Richtung.BEARISH, Tendenz.OEL),
        r("\\b(imposes?|new|raises?|hikes?|threatens?)\\s+(\\w+\\s+)?tariffs?|zoll(e|ä|ae)?(rh(ö|oe)hung)?\\s+(auf|gegen)|verh(ä|ae)ngt\\s+z(ö|oe)lle|droht\\s+mit\\s+z(ö|oe)llen|neue\\s+z(ö|oe)lle") to Lage(Richtung.BEARISH, Tendenz.AKTIEN),
        r("\\b(trade|tariff)\\s+(deal|truce|pause|agreement)|handelsabkommen|zollpause|einigung\\s+im\\s+zollstreit|z(ö|oe)lle\\s+(ausgesetzt|gesenkt)") to Lage(Richtung.BULLISH, Tendenz.AKTIEN),
        r("\\brecession\\s+(fears?\\s+(grow|rise|mount)|risks?\\s+(rise|grow)|looms?|warning|deepens?)|\\b(enters?|slides?\\s+into|falls?\\s+into|tips?\\s+into)\\s+(a\\s+)?recession|rezession\\w*\\s+(droht|befürchtet)|in\\s+die\\s+rezession") to Lage(Richtung.BEARISH, Tendenz.AKTIEN),
        r("\\bgold\\s+(hits|record|jumps?|surges?|soars?|rall(y|ies))|goldpreis\\s+(steigt|springt|auf\\s+rekord)|gold\\s+auf\\s+rekord") to Lage(Richtung.BULLISH, Tendenz.GOLD),
        r("(chip|semiconductor)\\s+export\\s+(ban|curbs?|controls?|restrictions?)|exportverbot\\w*\\s+f(ü|ue)r\\s+(chips|halbleiter)|exportbeschr(ä|ae)nkung\\w*\\s+f(ü|ue)r\\s+(chips|halbleiter)") to Lage(Richtung.BEARISH, Tendenz.CHIPS),
        r("(closes?|closure\\s+of|blocks?|blockade\\s+of|threatens?\\s+to\\s+close|sperrt|blockiert|sperrung\\s+der)\\s+(the\\s+)?(strait\\s+of\\s+hormuz|stra(ß|ss)e\\s+von\\s+hormus)|houthis?\\s+(attack|strike|target)|red\\s+sea\\s+attack|angriff\\w*\\s+im\\s+roten\\s+meer") to Lage(Richtung.BULLISH, Tendenz.OEL),
    )

    // Firmen aus dem Katalog als (Kuerzel, Name); einmal je Katalog vorbereitet.
    private class Firma(val symbol: String, val name: String, val muster: Regex?, val kuerzel: String?) {
        fun passt(text: String): Boolean = muster?.containsMatchIn(text) == true || (kuerzel != null && kuerzelGenannt(text, kuerzel))
    }

    // Firmen, die der Text nennt, in der Reihenfolge des Katalogs. Steht ein Name nur innerhalb des laengeren Namens
    // einer anderen genannten Firma („Siemens“ in „Siemens Energy“), zaehlt die kuerzere nicht; ihr Kuerzel zaehlt weiter.
    private fun genannt(text: String, katalog: List<Pair<String, String>>): List<Firma> {
        val treffer = firmen(katalog).filter { it.passt(text) }
        if (treffer.size < 2) return treffer
        val stellen = treffer.associateWith { f -> f.muster?.findAll(text)?.map { it.range }?.toList().orEmpty() }
        return treffer.filter { f ->
            val eigene = stellen[f].orEmpty()
            if (eigene.isEmpty() || (f.kuerzel != null && kuerzelGenannt(text, f.kuerzel))) return@filter true
            eigene.any { e ->
                stellen.none { (g, andere) -> g !== f && andere.any { a -> a.first <= e.first && a.last >= e.last && (a.last - a.first) > (e.last - e.first) } }
            }
        }
    }

    // Kuerzel, die zugleich ein gewoehnliches Wort, eine Behoerde oder eine gaengige Abkuerzung sind: sie zaehlen nur mit
    // Kennzeichnung ($NOW, „NYSE: NOW“, „(NOW)“), nie als blosses Wort.
    private val MEHRDEUTIG = setOf(
        "NOW", "ICE", "ALL", "ARE", "FOR", "CAR", "KEY", "TRUE", "REAL", "FAST", "COST", "LOW", "WELL", "OPEN", "PLAY", "LOVE", "GOOD",
        "BIG", "ONE", "TWO", "NEW", "OUT", "HAS", "CAN", "MAN", "EAT", "RUN", "FUN", "SEE", "AIR", "ANY", "DAY", "PAY", "BILL", "CASH",
        "EDIT", "LIFE", "LIVE", "MAIN", "NICE", "POST", "SAVE", "TECH", "USA", "CEO", "CFO", "FBI", "CIA", "DOJ", "SEC", "FED", "GDP",
        "WHO", "NATO", "IRS", "EPA", "FDA", "IPO", "ETF", "EPS", "ESG", "NET", "TOP", "HOT", "WIN", "WAR", "TAX", "JOB", "JOBS", "GAS",
        "OIL", "GOLD", "SUN", "SKY", "SEA", "ARM", "BOX", "CUT", "DIG", "FLY", "GAP", "HUT", "JAN", "MAR", "MAY", "DEC", "MET", "NUE",
        "ODD", "OLD", "PEN", "RED", "SIX", "TEN", "USE", "VIA", "WAY", "YOU", "HIGH", "HOME", "HOPE", "JACK", "KIDS", "LAND", "LEAD",
        "LINE", "LOAN", "MASS", "MIND", "NEXT", "ONLY", "PEAK", "PLAN", "RACE", "RIDE", "ROAD", "ROCK", "SAFE", "SELF", "SNOW", "STAR",
        "STEP", "TEAM", "TELL", "TIME", "TREE", "UNIT", "VERY", "WEST", "WISH", "WOLF", "WORK", "ZEAL", "BEST", "BLUE", "CARE", "CITY",
        "CORE", "DOOR", "EVER", "FIVE", "FORM", "FUND", "GAIN", "GOOD", "GROW", "HEAR", "HERE",
    )

    private fun grosswort(w: String): Boolean = w.count { it.isLetter() } >= 2 && w.none { it.isLowerCase() } && w.any { it.isLetter() }

    // Steht das Kuerzel als Boersenkuerzel im Text. Mit Kennzeichnung ($, Boersenplatz mit Doppelpunkt davor, Klammern)
    // immer; als blosses Wort nur, wenn es nicht mehrdeutig ist und kein Nachbarwort ganz in Grossbuchstaben steht
    // (durchgehend gross geschriebene Saetze nennen keine Kuerzel).
    fun kuerzelGenannt(text: String, kuerzel: String): Boolean {
        var von = 0
        while (true) {
            val i = text.indexOf(kuerzel, von)
            if (i < 0) return false
            von = i + 1
            val ende = i + kuerzel.length
            val davor = text.getOrNull(i - 1)
            val danach = text.getOrNull(ende)
            if (davor != null && (davor.isLetterOrDigit() || davor == '.' || davor == '-')) continue
            if (danach != null && (danach.isLetterOrDigit() || danach == '-')) continue
            if (davor == '$') return true
            val kopf = text.substring(0, i).trimEnd()
            if (kopf.endsWith(":") && kopf.dropLast(1).takeLastWhile { it.isLetter() }.let { it.length in 3..8 && grosswort(it) }) return true
            if (davor == '(' && danach == ')') return true
            if (kuerzel in MEHRDEUTIG) continue
            val vor = kopf.takeLastWhile { !it.isWhitespace() }.trim { !it.isLetterOrDigit() }
            val nach = text.substring(ende).trimStart().takeWhile { !it.isWhitespace() }.trim { !it.isLetterOrDigit() }
            if (grosswort(vor) || grosswort(nach)) continue
            return true
        }
    }

    @Volatile private var firmenCache: Pair<List<Pair<String, String>>, List<Firma>>? = null

    private fun firmen(katalog: List<Pair<String, String>>): List<Firma> {
        firmenCache?.let { (k, f) -> if (k === katalog) return f }
        val f = katalog.mapNotNull { (s, n) ->
            val stamm = GleicheFirma.stamm(n)
            // Kurze oder allgemeine Staemme (etwa „Sea“, „Arm“) nur als Kuerzel in Grossbuchstaben
            val kuerzel = s.substringBefore('.').substringBefore('-')
            // Je Wort einzeln maskieren: \Q...\E um den ganzen Stamm machte aus \s+ reinen Text
            val muster = stamm.takeIf { it.length >= 5 }
                ?.let { Regex("\\b" + it.split(' ').filter { w -> w.isNotEmpty() }.joinToString("\\s+") { w -> Regex.escape(w) } + "\\b", RegexOption.IGNORE_CASE) }
            val kurz = kuerzel.takeIf { it.length >= 3 && it.all { c -> c.isUpperCase() } }
            if (muster == null && kurz == null) null else Firma(s, n, muster, kurz)
        }
        firmenCache = katalog to f
        return f
    }

    // Genannte Firmen in der Reihenfolge des Katalogs, hoechstens max (Vorgabe zwei).
    fun firmenIn(titel: String, katalog: List<Pair<String, String>>, max: Int = 2): List<Pair<String, String>> =
        genannt(titel, katalog).take(max).map { it.symbol to it.name }

    // Alle Aktien, die ein Artikel erwaehnt (Artikelende): zuerst die schon bekannten Kuerzel, dann Firmen, deren Name
    // oder Kuerzel in den Texten steht. Gleiche Firmen (mehrere Gattungen) zaehlen einmal.
    fun erwaehnt(texte: List<String>, katalog: List<Pair<String, String>>, bekannt: List<String> = emptyList(), hoechstens: Int = 8): List<Pair<String, String>> {
        val aus = LinkedHashMap<String, String>()
        val gesehen = HashSet<String>()
        fun nimm(s: String, n: String) {
            if (aus.size < hoechstens && s !in aus && gesehen.add(GleicheFirma.stamm(n).ifBlank { n.lowercase() })) aus[s] = n
        }
        bekannt.forEach { s -> katalog.firstOrNull { it.first == s }?.let { nimm(it.first, it.second) } }
        val text = texte.filter { it.isNotBlank() }.joinToString("\n")
        if (text.isNotEmpty()) genannt(text, katalog).forEach { f -> if (aus.size < hoechstens) nimm(f.symbol, f.name) }
        return aus.map { it.key to it.value }
    }

    fun einordnen(titel: String, katalog: List<Pair<String, String>>): List<Einordnung> {
        val aus = ArrayList<Einordnung>()
        val themen = BullBear.themen(titel)
        val bull = themen.filter { it.seite == BullBear.Seite.BULL }.sumOf { it.gewicht }
        val bear = themen.filter { it.seite == BullBear.Seite.BEAR }.sumOf { it.gewicht }
        val richtung = when {
            bull > bear -> Richtung.BULLISH
            bear > bull -> Richtung.BEARISH
            else -> null
        }
        if (richtung != null) firmenIn(titel, katalog).forEach { (s, n) -> aus += Einordnung(Lage(richtung, n), s) }
        MARKT.filter { it.first.containsMatchIn(titel) }.map { it.second }.distinct().forEach { aus += Einordnung(it, null) }
        return aus.distinctBy { it.lage }.take(3)
    }
}
