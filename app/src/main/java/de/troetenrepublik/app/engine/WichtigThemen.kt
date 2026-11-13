package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Wichtig

// Reiter „Wichtigste“ (0.79): Schlagzeilen zu Themen gebuendelt, jedes Thema einer Kategorie zugeordnet, die Kategorien
// im Wechsel. Zuerst das wichtigste Thema jeder Kategorie, dann das zweitwichtigste jeder Kategorie und so fort. So
// steht ein grosses politisches Thema oben, auch wenn Marktmeldungen zahlreicher sind. Reine Rechnung ohne Netz.
object WichtigThemen {

    // Reihenfolge der Aufzaehlung entscheidet bei gleichen Punkten und bei der Zuordnung.
    enum class Kat { POLITIK, ZENTRALBANKEN, KONJUNKTUR, MAERKTE, UNTERNEHMEN, ROHSTOFFE, KRYPTO }

    // kopf: die staerkste Schlagzeile des Themas, weitere: die uebrigen Schlagzeilen derselben Geschichte, neueste zuerst.
    class Thema(val kopf: Rss.Eintrag, val weitere: List<Rss.Eintrag>, val kat: Kat, val punkte: Int)

    private val I = setOf(RegexOption.IGNORE_CASE)

    private val KRYPTO = Regex(
        "(bitcoin|\\bbtc\\b|ethereum|\\bether\\b|krypto|crypto|stablecoin|solana|\\bxrp\\b|binance|coinbase|blockchain|\\bdefi\\b|" +
            "memecoin|dogecoin|\\bnfts?\\b|digital assets?|digitale vermögenswerte|tether|\\busdt\\b|\\busdc\\b|tokeni[sz]|\\bperps?\\b|\\bweb3\\b)", I,
    )
    private val ZENTRALBANK = Regex(
        "(\\bfed\\b|federal reserve|fomc|\\becb\\b|\\bezb\\b|bank of england|\\bboe\\b|\\bsnb\\b|nationalbank|bank of japan|\\bboj\\b|" +
            "central banks?|notenbank|zentralbank|\\bpboc\\b|bank of canada|\\brba\\b|\\brbi\\b|reserve bank|bundesbank|powell|lagarde|warsh|\\bueda\\b|" +
            "leitzins|interest rates?|\\brate (cuts?|hikes?|decision)|zinsentscheid|zinssenkung|zinserhöhung|zinsen\\b|zinswende|geldpolitik|monetary policy)", I,
    )
    private val POLITIK = Regex(
        "(\\biran(ian|isch\\w*)?\\b|israel|\\bgaza|hamas|hisbollah|hezbollah|huthi|houthi|\\bruss(ia|ian|isch\\w*|land)|ukrain|putin|kreml|" +
            "zelensk|\\btaiwan|nordkorea|north korea|\\bnato\\b|\\btrump(?! media| jr)|\\bbiden|\\bharris\\b|white house|weißes haus|\\bcongress|kongress|" +
            "\\bsenate\\b|\\bsenat\\b|parliament|parlament|bundestag|bundesregierung|regierung|government|shutdown|\\belections?\\b|" +
            "\\bwahl(en|ergebnis|kampf|sieg)?\\b|tariffs?|\\bzoll|zölle|sanction|sanktion|embargo|\\bkrieg|ceasefire|waffenstillstand|" +
            "missile|rakete|\\bdrones?\\b|drohne|military|militär|invasion|diplomat|summit|gipfel|\\bminister|präsident (trump|biden|xi|putin|macron)|" +
            "president (trump|biden|xi|putin|macron|lula|erdogan|zelensky)|kanzler|\\bmerz\\b|scholz|macron|starmer|xi jinping|\\bmodi\\b|erdogan|netanyahu|" +
            "hormuz|hormus|supreme court|\\bg7\\b|\\bg20\\b|\\bg-7\\b|\\beu\\b|\\beu-|handelsabkommen|trade deal|trade war|handelskrieg|" +
            "\\bpolitic|\\bpolitik|\\bvenezuela|\\bsyria|syrien|\\bkorea|\\bfdp\\b|\\bspd\\b|\\bcdu\\b|\\bafd\\b|\\bcsu\\b|wealth tax|steuer|\\bbans?\\b|\\bbanned\\b|verbot|" +
            "\\btaxes\\b|tax (cuts?|hikes?|bill|plan|reform)|\\bcop\\d\\d|klimaschutz|klimagipfel|climate (summit|deal|policy)|subsid(y|ies)|subvention|\\bgesetz|\\blaw\\b|legislation|" +
            "export (bans?|curbs|controls)|exportverbot|bans? (the )?export)", I,
    )
    private val ROHSTOFFE = Regex(
        "(\\boil\\b|crude|brent|\\bwti\\b|opec|ölpreis|\\böl\\b|erdöl|\\bgas\\b|erdgas|\\blng\\b|natural gas|\\bgold\\b|goldpreis|silber|" +
            "\\bsilver|kupfer|copper|platin|palladium|lithium|\\buran|nickel|\\bzinc|aluminium|aluminum|weizen|wheat|\\bcorn\\b|\\bmais\\b|" +
            "kaffee|coffee|cocoa|kakao|zucker|\\bsugar|rohstoff|commodit|iron ore|eisenerz|rare earths?|seltene erden|energiepreis|strompreis|" +
            "benzin|diesel|spritpreis|gasoline|\\bsprit\\b|energy prices?|kohle\\b|\\bcoal\\b|fossil|erneuerbar|renewables?)", I,
    )
    // Marktbericht und Indizes: Nennt die Schlagzeile zuerst den Markt, ist sie eine Marktmeldung.
    private val MARKT = Regex(
        "(marktbericht|\\bdax\\b|\\bdow\\b|s&p ?500|nasdaq|stoxx|nikkei|sensex|nifty|kospi|hang seng|aktienm[aä]rkt|" +
            "börsentag|yields?\\b|renditen|anleihen|\\bbonds?\\b|treasur|devisen|currency|forex|wechselkurs|\\bfx\\b)", I,
    )
    // Eindeutige Konjunkturbegriffe: stehen sie vorn, geht die Meldung um Daten und nicht um Zinsen oder Politik.
    private val KONJ_KERN = Regex(
        "(inflation|konjunktur|\\bgdp\\b|\\bbip\\b|payrolls?|arbeitslos|unemployment|recession|rezession|verbraucherpreise|consumer prices|\\bcpi\\b)", I,
    )
    private val KONJUNKTUR = Regex(
        "(inflation|konjunktur|\\beconom(y|ic|ies|ists?)\\b|wirtschaftswachstum|wirtschaftsdaten|wirtschaftsleistung|\\bgdp\\b|\\bbip\\b|\\bjobs?\\b|" +
            "payrolls?|arbeitslos|unemployment|arbeitsmarkt|jobless|recession|rezession|retail sales|einzelhandel|\\bifo\\b|\\bzew\\b|\\bpmi\\b|" +
            "einkaufsmanager|verbraucher(preise|vertrauen|stimmung)|consumer (prices|confidence|sentiment|spending)|producer prices|erzeugerpreise|" +
            "\\bcpi\\b|\\bppi\\b|handelsbilanz|trade (deficit|surplus)|industrieproduktion|industrial production|auftragseingang|insolvenzen|" +
            "löhne|\\bwages\\b|staatsverschuldung|staatsschulden|national debt|streik|tarif(kampf|runde|verhandlung)|gewerkschaft|\\bstrikes?\\b|world bank|weltbank|\\bimf\\b|\\biwf\\b|\\boecd\\b|wachstumsprognose|wirtschaftsweise|growth forecast|economic outlook)", I,
    )
    private val UNTERNEHMEN = Regex(
        "(earnings|quartals|quarterly|geschäftszahlen|jahreszahlen|gewinn|umsatz|revenue|\\bprofits?\\b|\\bceo\\b|\\bcfo\\b|übernahme|übernimmt|" +
            "acquisition|acquires|\\bto (buy|acquire|sell)\\b|\\bmerger|fusion|takeover|\\bipo\\b|börsengang|layoffs|job cuts|stellenabbau|insolvenz|" +
            "bankrupt|chapter 11|recall|rückruf|dividend|aktienrückkauf|buyback|guidance|\\bprognose|\\bq[1-4]\\b|\\bresults\\b|ergebnis|" +
            "automaker|carmaker|autobauer|autoindustrie|plant closure|werksschließ|werkschließ|\\bfined?\\b|geldbuße|antitrust|kartell|lawsuit|klage|" +
            "\\bsues?\\b|verklagt|steps down|tritt zurück|\\bcompany\\b|unternehmen|konzern|\\bwerke?\\b|\\bfabrik|\\bfactory|\\bplant\\b|" +
            "\\bairlines?\\b|fluggesellschaft|\\bairport|-zahlen\\b|\\bzahlen\\b)", I,
    )

    // Notenbank- und Behoerdenfeeds legen die Kategorie fest, wenn der Text nichts Genaueres sagt.
    private val ZB_QUELLEN = setOf("ezb", "fed", "boe", "snb", "bbk", "boc", "bis", "ezb-blog", "fed-reden")
    private val AMT_QUELLEN = setOf("destatis", "bea", "eu-kommission", "bafin")
    // Feeds, in denen es nur um einzelne Firmen geht (die allgemeinen Wirtschaftsfeeds sagen nichts ueber die Art).
    private val UNT_QUELLEN = setOf("fn-adhoc", "yahoo-unternehmen", "aktionaer", "finnhub-uebernahmen", "bafin-massnahmen")

    // Kategorie einer Schlagzeile: Ereignisart aus der Bewertung (Krieg, Zinsentscheid) zuerst, sonst gilt das Stichwort,
    // das am weitesten vorn steht („Gold steigt, weil die Fed ...“ ist eine Rohstoffmeldung). Ohne Stichwort der Feed.
    fun kategorie(e: Rss.Eintrag, gruende: List<String>): Kat {
        val t = e.titel
        if (Rss.Kategorie.TRUMP in e.kategorien) return Kat.POLITIK
        if ("Zinsentscheid" in gruende) return Kat.ZENTRALBANKEN
        if ("Krieg und Konflikt" in gruende) return Kat.POLITIK
        val vorn = listOf(Kat.KRYPTO to KRYPTO, Kat.ZENTRALBANKEN to ZENTRALBANK, Kat.POLITIK to POLITIK, Kat.KONJUNKTUR to KONJ_KERN, Kat.ROHSTOFFE to ROHSTOFFE, Kat.MAERKTE to MARKT)
            .mapNotNull { (k, rx) -> rx.find(t)?.let { k to it.range.first } }
            .minByOrNull { it.second }?.first
        if (vorn != null) return vorn
        if (e.quellen.any { it in ZB_QUELLEN }) return Kat.ZENTRALBANKEN
        if ("Handelspolitik" in gruende) return Kat.POLITIK
        if ("Konjunkturdaten" in gruende || KONJUNKTUR.containsMatchIn(t) || e.quellen.any { it in AMT_QUELLEN }) return Kat.KONJUNKTUR
        if ("Unternehmen" in gruende || Wichtig.nenntGrossfirma(t) || UNTERNEHMEN.containsMatchIn(t)) return Kat.UNTERNEHMEN
        return when {
            Rss.Kategorie.KRYPTO in e.kategorien -> Kat.KRYPTO
            e.quellen.any { it in UNT_QUELLEN } -> Kat.UNTERNEHMEN
            else -> Kat.MAERKTE
        }
    }

    // Durchbrueche als Unterkategorie von „Wichtigste“ (0.79): die Meldungen der letzten 14 Tage aus den Themen der Trendliste,
    // je Thema eine Gruppe (hoechstens fuenf Meldungen, neueste zuerst), das Thema mit der frischesten Meldung vorn.
    class DurchbruchTeil(val gruppen: List<Durchbrueche.Gruppe>) {
        val anzahl: Int get() = gruppen.sumOf { it.meldungen.size }
        val themen: Int get() = gruppen.size
    }

    // null ohne Meldung: dann steht die Unterkategorie nicht da
    fun durchbrueche(meldungen: List<Durchbrueche.Meldung>?, jetzt: Long): DurchbruchTeil? =
        Durchbrueche.gruppieren(meldungen.orEmpty(), jetzt).takeIf { it.isNotEmpty() }?.let { DurchbruchTeil(it) }

    // Die Meldung als Schlagzeile, damit ein Tipp den Artikel im Blatt der News oeffnet
    fun alsEintrag(m: Durchbrueche.Meldung): Rss.Eintrag = Rss.Eintrag(titel = m.titel, link = m.link, zeit = m.zeit, herausgeber = m.quelle, quelle = m.quelle)

    // Weg zur Zeile des Themas in Maerkte, Trends
    fun wegZumThema(thema: String): String = "maerkte/trends/durchbrueche:$thema"

    // Die Themen im Wechsel der Kategorien. Runde 1: das wichtigste Thema jeder Kategorie, nach Punkten geordnet
    // (bei Gleichstand das neuere), Runde 2: das zweitwichtigste jeder Kategorie, und so weiter.
    fun abwechselnd(themen: List<Thema>): List<Thema> {
        val vergleich = compareByDescending<Thema> { it.punkte }.thenByDescending { it.kopf.zeit }.thenBy { it.kat.ordinal }
        val jeKat = themen.groupBy { it.kat }.mapValues { (_, l) -> l.sortedWith(vergleich) }
        val runden = jeKat.values.maxOfOrNull { it.size } ?: 0
        val aus = ArrayList<Thema>(themen.size)
        for (r in 0 until runden) aus += jeKat.values.mapNotNull { it.getOrNull(r) }.sortedWith(vergleich)
        return aus
    }

    // Themen aus den Schlagzeilen: dieselbe Auswahl wie Wichtig.wichtige, je Geschichte ein Thema, in der Reihenfolge von
    // abwechselnd(). depot: Begriffe aus Depot und Merkliste (Wichtig.depotBegriffe).
    fun themen(liste: List<Rss.Eintrag>, depot: List<String> = emptyList(), nurEnglisch: Boolean = false): List<Thema> {
        val gruppen = Wichtig.gruppen(liste, depot, nurEnglisch)
        if (gruppen.isEmpty()) return emptyList()
        val nachId = HashMap<String, Rss.Eintrag>(liste.size * 2)
        for (e in liste) nachId.putIfAbsent(e.id, e)
        val themen = gruppen.mapNotNull { g ->
            val kopf = nachId[g.kopf] ?: return@mapNotNull null
            val weitere = g.weitere.mapNotNull { nachId[it] }.sortedByDescending { it.zeit }
            Thema(kopf, weitere, kategorie(kopf, Wichtig.bewerten(kopf, depot).gruende), g.punkte)
        }
        return abwechselnd(themen)
    }
}
