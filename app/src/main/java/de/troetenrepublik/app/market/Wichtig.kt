package de.troetenrepublik.app.market

import kotlin.math.abs
import kotlin.math.floor

// Wichtige Schlagzeilen. Punkte nach Art des Ereignisses, Zuschlaege fuer Bezug zum Depot und breite Berichterstattung,
// Abzuege fuer Vermutungen, Vorschauen und Erklaerstuecke. Markiert ab SCHWELLE Punkten, je Geschichte hoechstens zwei,
// insgesamt hoechstens ANTEIL der Liste, ohne Mindestanteil. Bei den eingebauten Feeds sind es an einem gewoehnlichen
// Tag 10 bis 20 Prozent.
object Wichtig {

    const val SCHWELLE = 5
    const val ANTEIL = 0.20
    private const val JE_GESCHICHTE = 2

    data class Bewertung(val punkte: Int, val gruende: List<String>)

    private val I = setOf(RegexOption.IGNORE_CASE)

    // Seiten mit Kauflisten, Kurszielen und Krypto-Werbung sind nie wichtig.
    private val SCHWACHE_HERAUSGEBER = listOf(
        "fool.com", "247wallst.com", "beincrypto.com", "coinspeaker.com", "ccn.com", "cryptonews.com", "unchainedcrypto.com",
        "investorplace.com", "investorshub.advfn.com", "insidermonkey.com", "zacks.com", "gurufocus.com", "simplywall.st",
        "u.today", "coingape.com", "benzinga.com",
    )

    // Ratgeber, Kauflisten, Reden, Interviews, Tagesuebersichten, Kursziele.
    private val RAMSCH = Regex(
        "(\\bto buy\\b|buy right now|better buy|\\bi'd buy\\b|stocks? to (own|watch|hold)|could be worth|hand over fist|watch these|" +
            "buy, sell,? or hold|\\bmillionaire|passive income|\\bparabolic\\b|\\bshould i\\b|\\bi'm\\b|\\bi am\\b|\\bmy\\b|^i |" +
            "here's (why|how|what|the|where|a few)|what to know|how to\\b|\\btips\\b|in charts|in one chart|explained|\\bby the numbers\\b|" +
            "jetzt kaufen|so reagiert|droht jetzt|warnsignale|was das für .* bedeutet|^darum|das steckt dahinter|tipps|so funktioniert|" +
            "was bedeutet|lists .* (estate|home|mansion)|\\bestate for \\$|questionnaire|keynote|\\bspeech\\b|\\brede\\b|remarks by|interview|" +
            "daily news|tägliche nachrichten|data portal|datenportal|^update wirtschaft|wirtschaft vor acht|discount rate meetings|" +
            "great recession|große rezession|analyse-flash|price target|kursziel|upgrades?\\b|downgrades? .* to (buy|sell|hold|neutral))", I,
    )
    private val PRESSEMITTEILUNG = Regex(
        "\\bg/t\\b|\\bdrill|exploration|\\bcommences\\b|\\bidentifies\\b|\\bdefines\\b|\\bpea\\b|preliminary production|" +
            "workover|bohranlagen|^\\d+ [A-Za-z ]+ stocks\\b|\\bprospect\\b|\\bdeposit\\b|\\bclaims?\\b.*\\bproject\\b", I,
    )
    private val ERKLAERSTUECK = Regex("(^analysis:|^analyse:|^kommentar|^opinion|^meinung|^column|\\bcommentary\\b)", I)
    private val VORSCHAU = Regex(
        "(\\bexpected to\\b|\\bahead of\\b|\\bawait|\\bpreview\\b|what to expect|vor den zahlen|\\bdürfte\\b|" +
            "options market expects|set to report|is said to|sollen? .* werden)", I,
    )
    // May als Monat zaehlt nicht, etwa in May oder May 2026.
    private val VERMUTUNG = Regex("(\\bcould\\b|\\bmight\\b|(?<!\\bin )\\bmay\\b(?!\\s*\\d)|könnte|würde|\\bwould\\b|\\bif\\b|reportedly|\\brumou?r|gerücht)", I)
    private val ERWOGEN = Regex("(\\bconsiders?\\b|erwägt|\\bweighs\\b|\\bplans? to\\b|\\bplant\\b)", I)

    private const val ZB = "(fed\\b|federal reserve|fomc|ecb|ezb|bank of england|\\bboe\\b|snb|nationalbank|bank of japan|\\bboj\\b|central bank|notenbank|zentralbank|pboc)"
    private const val MARKT = "(dow\\b|s&p|nasdaq|dax\\b|mdax|stoxx|nikkei|kospi|hang seng|ftse|wall street|börsen|stock markets?|aktienm[aä]rkt|" +
        "global stocks|bitcoin|ether|krypto|crypto|gold\\b|silber|silver|\\boil\\b|ölpreis|öl\\b|brent|crude|dollar|euro\\b|yen\\b|anleihen|" +
        "bonds?|yields?|renditen|treasur|tech\\b|techwerte|technolog|chip|halbleiter|banks|banken|sector|sektor)"
    private const val GROSSFIRMA = "(apple|microsoft|nvidia|amazon|alphabet|google|meta\\b|tesla|tsmc|taiwan semiconductor|broadcom|berkshire|" +
        "jpmorgan|exxon|walmart|eli lilly|novo nordisk|asml|\\bsap\\b|siemens|allianz|lvmh|samsung|aramco|openai|anthropic|spacex|lufthansa|" +
        "volkswagen|mercedes|\\bbmw\\b|deutsche bank|bayer|basf|airbus|rheinmetall|telekom|kroger|intel\\b|amd\\b|oracle|netflix|coreweave|" +
        "palantir|unitedhealth|visa\\b|mastercard|goldman|morgan stanley|nestlé|nestle|roche|novartis|ubs\\b|shell\\b|bp\\b|totalenergies)"
    private val STARKE_BEWEGUNG = Regex(
        "(sell-?off|selloff|\\brout\\b|ausverkauf|abverkauf|\\bcrash|kurssturz|kurseinbruch|bricht ein|stürzt|plung|plummet|tumbl|" +
            "\\bsinks?\\b|slump|\\bsoar|record high|all-time high|rekordhoch|allzeithoch|correction territory|bear market|bärenmarkt|" +
            "absacken|sackt ab|liquidation|milestone)", I,
    )
    private val BEWEGUNG = Regex("(rise|rises|rose|fall|falls|fell|drop|drops|gain|gains|climb|jump|slide|slides|steig|fäll|fiel|sink|legt zu|verliert|gewinnt)", I)
    private val LAENDER_GROSS = Regex("(u\\.?s\\.?\\b|us-|america|euro|germany|deutschland|deutsche|german|uk\\b|britain|british|china|chines|japan|swiss|schweiz)", I)
    private val LAENDER_KLEIN = Regex(
        "(india|indien|indonesia|philippin|brazil|brasilien|mexico|mexiko|turkey|türkei|argentin|south africa|südafrika|nigeria|vietnam|" +
            "thailand|malaysia|pakistan|egypt|ägypten|kenya|colombia|chile|peru)", I,
    )
    private const val KRIEG_KONTEXT = "(iran|israel|russ|ukrain|china|taiwan|korea|hormuz|hormus|huthi|houthi|aramco|saudi|tanker|warship|" +
        "kriegsschiff|refiner|raffiner|pipeline|military|militär|missile|rakete|drone|drohne|nato|oil|öl)"

    private class Ereignis(val punkte: Int, val name: String, val rx: Regex)

    private val EREIGNISSE = listOf(
        Ereignis(7, "Eilmeldung", Regex("(eilmeldung|\\bbreaking\\b)", I)),
        Ereignis(7, "Zinsentscheid", Regex(
            "(zinsentscheid|monetary policy decision|geldpolitische beschlüsse|fomc statement|bank rate (maintained|reduced|increased|cut|raised)|" +
                "geldpolitische lagebeurteilung|monetary policy assessment|" + ZB + ".{0,40}\\b(cuts?|raises?|hikes?|holds?|keeps?|leaves?|lowers?|" +
                "senkt|erhöht|belässt|hebt)\\b.{0,30}\\b(rates?|zins|zinsen|leitzins|interest)|leitzins.{0,30}\\b(gesenkt|erhöht|unverändert))", I,
        )),
        Ereignis(6, "Konjunkturdaten", Regex(
            "(inflationsrate|verbraucherpreise|consumer prices?|\\bcpi\\b|inflation (rate|rises|rose|falls|fell|jumps|eases|cools|accelerates|hits)|" +
                "retail inflation|payrolls|jobs report|unemployment rate|arbeitslos|\\bgdp\\b|\\bbip\\b|economy (grew|shrank|contracted|expanded)|" +
                "wirtschaft (wächst|schrumpft)|recession|rezession|gross domestic product|personal income and outlays|\\bpce\\b|ifo-|ifo |einkaufsmanager|\\bpmi\\b)", I,
        )),
        Ereignis(5, "Konjunkturdaten", Regex(
            "(producer prices|\\bppi\\b|erzeugerpreise gewerblicher|großhandelspreise|retail sales|einzelhandelsumsatz|" +
                "unternehmensinsolvenzen|handelsüberschuss|trade (surplus|deficit))", I,
        )),
        Ereignis(6, "Krieg und Konflikt", Regex(
            "(\\bwar\\b|krieg|ceasefire|waffenstillstand|invasion|blockade|airstrike|luftangriff|missile|rakete|" +
                "(attack|angriff|strike|beschuss)\\w*.{0,60}" + KRIEG_KONTEXT + "|" + KRIEG_KONTEXT + ".{0,60}(attack|angriff|strike|beschuss))", I,
        )),
        Ereignis(6, "Handelspolitik", Regex(
            "((impos|announc|rais|hik|lift|suspend|verhängt|erhöht|kündigt|setzt|verschärft|lockert|eas)\\w*.{0,40}(tariff|zoll|zölle|sanction|sanktion|embargo|export controls)|" +
                "(tariff|zoll|zölle|sanction|sanktion)\\w*.{0,30}(take effect|in kraft|imposed|verhängt|lifted|aufgehoben)|rare earths|seltene erden|export ban|exportverbot|opec)", I,
        )),
        Ereignis(3, "Handelspolitik", Regex("(tariff|\\bzoll|zölle|sanction|sanktion|embargo)", I)),
        Ereignis(5, "Finanzsystem", Regex(
            "(bank run|bank failure|bankenpleite|credit concerns?|kreditsorgen|bonds? tumble|anleihen.{0,20}(einbruch|fallen)|" +
                "yields? (spike|surge|near|jump|hit|soar)|renditen (steigen|springen)|junk bonds|sovereign (rating|downgrade)|herabstuf|" +
                "\\bdefault\\b|zahlungsausfall|shutdown|debt ceiling|schuldenobergrenze|staatsschulden|debt crisis|trapped by debt)", I,
        )),
        Ereignis(4, "Institution", Regex("((imf|iwf|world bank|weltbank|oecd|\\bbis\\b|iea)\\b.{0,50}(warn|cuts?|raises?|senkt|hebt|sees|forecast|prognose))", I)),
    )
    private val UNTERNEHMEN = Regex(
        "(record (profit|revenue|quarter)|rekordgewinn|rekordumsatz|profit warning|gewinnwarnung|(cuts|raises|lowers|slashes) (its )?(guidance|forecast|outlook)|" +
            "(senkt|hebt|kappt) (die |seine |ihre )?prognose|bankrupt|chapter 11|insolvenz|\\bpleite\\b|to acquire|acquires|acquisition|merger|fusion|" +
            "übernahme|übernimmt|takeover|\\bipo\\b|börsengang|steps down|resigns|tritt zurück|layoffs|job cuts|losing their jobs|stellenabbau|" +
            "streicht .*stellen|\\bfined?\\b|geldbuße|antitrust|kartell|\\bsued\\b|verklagt|recall|rückruf|stake sale|einstieg|tender offer|" +
            "beats? estimates|misses estimates|übertrifft|verfehlt|\\$\\d[\\d.,]* (trillion|billion)|\\d[\\d.,]* (billionen|milliarden|mrd))", I,
    )
    private val BETRAG = Regex("(\\$\\s?\\d[\\d.,]*\\s?(trillion|billion|bn)|\\d[\\d.,]*\\s?(billionen|milliarden|mrd))", I)
    private val PROZENT = Regex("(\\d+(?:[.,]\\d+)?)\\s?%")

    // Feeds von Notenbanken und Behoerden: zaehlen nur mit eigenen Entscheidungen und Daten.
    private val AMTLICH = setOf("ezb", "fed", "boe", "snb", "destatis", "eu-kommission", "bea")

    // Steht vorn eine Aussage und erst am Ende eine Frage, zaehlt die Aussage mit Abzug, sonst null.
    private fun ohneFrage(t: String): Pair<String, Int>? {
        if ('?' !in t) return t to 0
        val teile = t.split(Regex("(?<=[.:!])\\s+"))
        return if (teile.size >= 2 && '?' !in teile[0] && teile[0].trim().split(Regex("\\s+")).size >= 4) teile[0] to -1 else null
    }

    // Grund, warum eine Schlagzeile nie wichtig ist, sonst null.
    fun ausgeschlossen(e: Rss.Eintrag): String? {
        val t = e.titel
        val her = e.herausgeber.lowercase()
        if (SCHWACHE_HERAUSGEBER.any { her.endsWith(it) }) return "Herausgeber"
        if (ohneFrage(t) == null) return "Frage"
        val buchstaben = t.filter { it.isLetter() }
        if (buchstaben.length > 25 && buchstaben.count { it.isUpperCase() } > 0.6 * buchstaben.length) return "Pressemitteilung"
        if (PRESSEMITTEILUNG.containsMatchIn(t)) return "Pressemitteilung"
        if (RAMSCH.containsMatchIn(t)) return "Ratgeber"
        return null
    }

    private fun prozente(t: String): List<Double> = PROZENT.findAll(t).mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.toList()

    private fun imDepot(t: String, depot: List<String>): Boolean =
        depot.any { d -> d.isNotBlank() && alsGanzesWort(t, d) }

    // Punkte einer Schlagzeile ohne die Berichterstattung anderer Herausgeber.
    fun bewerten(e: Rss.Eintrag, depot: List<String> = emptyList()): Bewertung {
        ausgeschlossen(e)?.let { return Bewertung(0, listOf(it)) }
        val (t, abzug) = ohneFrage(e.titel)!!
        val depotTreffer = imDepot(t, depot)
        val gross = Regex(GROSSFIRMA, I).containsMatchIn(t)
        val markt = Regex(MARKT, I).containsMatchIn(t)
        val treffer = EREIGNISSE.filter { it.rx.containsMatchIn(t) }.map { it.punkte to it.name }.toMutableList()
        val pz = prozente(t)
        if (STARKE_BEWEGUNG.containsMatchIn(t) && (markt || gross || depotTreffer)) treffer += 5 to "Marktbewegung"
        else if (BEWEGUNG.containsMatchIn(t) && pz.isNotEmpty() && (markt && pz.max() >= 2 || (gross || depotTreffer) && pz.max() >= 7)) treffer += 5 to "Marktbewegung"
        if (UNTERNEHMEN.containsMatchIn(t)) treffer += (if (gross || depotTreffer || BETRAG.containsMatchIn(t)) 5 else 2) to "Unternehmen"
        if (treffer.isEmpty()) return Bewertung(0, listOf("kein Ereignis"))
        treffer.sortByDescending { it.first }
        var p = treffer[0].first
        val gruende = treffer.map { it.second }.distinct().toMutableList()
        if (treffer.size >= 2 && treffer[1].first >= 4) p += 1
        if (depotTreffer) { p += 2; gruende += "im Depot" }
        if ("Konjunkturdaten" in gruende && LAENDER_KLEIN.containsMatchIn(t) && !LAENDER_GROSS.containsMatchIn(t)) { p -= 2; gruende += "kleine Volkswirtschaft" }
        if (VERMUTUNG.containsMatchIn(t)) { p -= 2; gruende += "Vermutung" }
        if (ERWOGEN.containsMatchIn(t)) { p -= 1; gruende += "erwogen" }
        if (VORSCHAU.containsMatchIn(t)) { p -= 2; gruende += "Vorschau" }
        if (ERKLAERSTUECK.containsMatchIn(t)) { p -= 2; gruende += "Erklärstück" }
        p += abzug
        if (e.quellen.any { it in AMTLICH } && gruende.none { it == "Zinsentscheid" || it == "Konjunkturdaten" || it == "Eilmeldung" }) p = minOf(p, 3)
        return Bewertung(p, gruende)
    }

    // Dieselbe Geschichte

    private val KANON = listOf(
        "tech" to "(\\btech|technolog|techwerte|\\bchip|halbleiter|semiconductor|\\bai\\b|\\bki\\b|neocloud|software)",
        "abwaerts" to "(sell-?off|selloff|\\brout\\b|ausverkauf|abverkauf|tumbl|plung|slump|crash|absack|rutsch|\\bfall|fäll|\\bdown\\b|\\bsink|drop|verlust|belast)",
        "aufwaerts" to "(rally|soar|surg|\\bjump|record high|allzeithoch|rekordhoch|\\bgain|steig|zuleg|\\brise)",
        "inflation" to "(inflation|\\bcpi\\b|verbraucherpreise|consumer prices|teuerung)",
        "zinsen" to "(rate (cut|hike)|zins|\\bfed\\b|\\becb\\b|\\bezb\\b|leitzins)",
        "oel" to "(\\boil\\b|\\böl|crude|brent|\\bwti\\b|diesel|sprit|fuel)",
        "nahost" to "(iran|hormuz|hormus|huthi|houthi|aramco|saudi)",
        "russland" to "(russ|ukrain|putin|kreml)",
        "anleihen" to "(bond|anleihe|yield|rendite|treasur)",
        "krypto" to "(bitcoin|crypto|krypto|\\bether)",
        "gewinn" to "(profit|gewinn|revenue|umsatz|earnings|quartal|quarter)",
        "asien" to "(asia|asien|nikkei|kospi|hang seng)",
    ).map { (n, rx) -> n to Regex(rx, I) }
    private val FIRMEN = Regex(GROSSFIRMA, I)
    private val STOPP = setOf(
        "eine", "einer", "einem", "einen", "eines", "nicht", "noch", "mehr", "auch", "aber", "wegen", "gegen", "unter", "seit",
        "beim", "dass", "diese", "dieser", "dieses", "heute", "neue", "neuer", "neues", "werden", "wird", "sind", "haben", "nach",
        "über", "sich", "fuer", "für", "with", "from", "that", "this", "have", "will", "over", "after", "about", "into", "their",
        "what", "when", "amid", "says", "said", "than", "just", "your", "report", "reports", "update", "today", "stock", "stocks",
        "aktie", "aktien", "markets", "market", "börse", "börsen", "shares", "price", "prices", "preise", "week", "woche", "sagt",
        "bericht", "roundup", "schluss",
    )

    private class Merkmal(val woerter: Set<String>, val themen: Set<String>)

    private fun stamm(w: String): String {
        for (s in listOf("ungen", "ung", "en", "es", "er", "s", "e")) if (w.length > 5 && w.endsWith(s)) return w.dropLast(s.length)
        return w
    }

    private fun merkmal(t: String): Merkmal = Merkmal(
        wortfolgen(t.lowercase()).filter { it.length >= 4 && it !in STOPP }.map { stamm(it) }.toSet(),
        KANON.filter { it.second.containsMatchIn(t) }.map { it.first }.toSet() + FIRMEN.findAll(t).map { "firma:" + it.value.lowercase() },
    )

    // Gleiche Geschichte: drei gemeinsame Woerter (halber kuerzerer Titel) oder gemeinsame Themen, davon eins eine Firma.
    private fun gleich(a: Merkmal, b: Merkmal): Boolean {
        val g = a.woerter.count { it in b.woerter }
        if (g >= 3 && g >= 0.5 * minOf(a.woerter.size, b.woerter.size)) return true
        val gk = a.themen.intersect(b.themen)
        if (gk.any { it.startsWith("firma:") } && gk.size >= 2) return true
        return gk.size >= 3 || (gk.size >= 2 && g >= 1)
    }

    private fun herkunft(e: Rss.Eintrag): String = e.herausgeber.ifBlank { e.quelle }.lowercase()

    // Einschliesslich der Berichterstattung anderer Herausgeber binnen 24 Stunden.
    fun punkte(liste: List<Rss.Eintrag>, depot: List<String> = emptyList()): List<Bewertung> {
        val merk = liste.map { merkmal(it.titel) }
        val basis = liste.map { bewerten(it, depot) }
        return liste.indices.map { i ->
            val b = basis[i]
            if (b.punkte <= 0 && b.gruende.firstOrNull() != "kein Ereignis") return@map b
            val her = hashSetOf(herkunft(liste[i]))
            for (j in liste.indices) {
                if (i == j || abs(liste[i].zeit - liste[j].zeit) > 86_400_000L || basis[j].gruende.firstOrNull() == "Herausgeber") continue
                if (gleich(merk[i], merk[j])) her += herkunft(liste[j])
            }
            val n = her.size
            when {
                b.punkte > 0 && n >= 2 -> Bewertung(b.punkte + if (n == 2) 1 else if (n <= 4) 2 else 3, b.gruende + "$n Herausgeber")
                // Kein erkanntes Ereignis, aber viele Herausgeber zugleich: breite Berichterstattung
                b.punkte <= 0 && n >= 4 -> Bewertung(SCHWELLE, listOf("$n Herausgeber"))
                else -> b
            }
        }
    }

    // depot: Namen und Kuerzel aus Depot und Merkliste.
    fun wichtige(liste: List<Rss.Eintrag>, depot: List<String> = emptyList()): Set<String> {
        if (liste.isEmpty()) return emptySet()
        val p = punkte(liste, depot)
        val merk = liste.map { merkmal(it.titel) }
        val kandidaten = liste.indices.filter { p[it].punkte >= SCHWELLE }
            .sortedWith(compareByDescending<Int> { p[it].punkte }.thenByDescending { liste[it].zeit })
        val grenze = maxOf(1, floor(ANTEIL * liste.size).toInt())
        val gewaehlt = ArrayList<Int>()
        for (i in kandidaten) {
            if (gewaehlt.size >= grenze) break
            if (gewaehlt.count { gleich(merk[i], merk[it]) } >= JE_GESCHICHTE) continue
            gewaehlt += i
        }
        return gewaehlt.mapTo(HashSet()) { liste[it].id }
    }

    // Namen ohne Rechtsform und Kuerzel ab drei Zeichen ohne Boersenendung.
    fun depotBegriffe(namen: List<String>, kuerzel: List<String>): List<String> {
        val rechtsform = Regex("(?i)[,.]?\\s+(inc|corp|corporation|co|company|ag|se|plc|ltd|limited|n\\.?v|s\\.?a|holding|holdings|group|gruppe|class [a-c]|cl [a-c]|adr|etf|ucits)\\.?\\b.*$")
        val n = namen.map { it.replace(rechtsform, "").trim() }.filter { it.length >= 3 }
        val k = kuerzel.filter { '=' !in it && '^' !in it }.map { it.substringBefore('.').substringBefore('-').substringBefore('=').uppercase() }
            .filter { it.length >= 3 && it.all { c -> c.isLetter() } }
        return (n + k).distinct()
    }
}
