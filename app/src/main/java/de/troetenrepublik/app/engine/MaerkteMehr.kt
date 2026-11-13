package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import kotlin.math.abs

// Rechenlogik fuer Maerkte (0.76, Paket q78f): Themenkoerbe, Beitrag zum Index, Mover mit RVOL und Grund, Art einer
// Schlagzeile, Sektortabelle, Aenderung der Reddit-Erwaehnungen zur Vorperiode und Ergebnis des ersten Handelstags.

// Themenkoerbe: feste Listen von Aktien weltweit. Werte ohne Kurs fallen weg. Je Korb Median der Aenderung, Anteil
// steigender Werte und groesster Mover.
object Themenkoerbe {
    class Korb(val schluessel: String, val symbole: List<String>)

    val KOERBE: List<Korb> = listOf(
        Korb("KI", listOf("NVDA", "MSFT", "GOOGL", "META", "AMZN", "PLTR", "AVGO", "AMD", "TSM", "ORCL", "SNOW", "NOW", "SAP.DE", "ASML.AS", "SMCI", "DELL", "ARM", "MU", "VRT", "CRWV", "ADBE", "CRM", "IBM", "BIDU", "9984.T", "000660.KS")),
        Korb("HALBLEITER", listOf("NVDA", "AMD", "INTC", "TSM", "AVGO", "QCOM", "TXN", "MU", "AMAT", "LRCX", "KLAC", "ASML.AS", "IFX.DE", "STMPA.PA", "ARM", "MRVL", "ON", "NXPI", "ADI", "8035.T", "6857.T", "6146.T", "6723.T", "000660.KS", "005930.KS", "2454.TW", "BESI.AS", "AIXA.DE")),
        Korb("VERTEIDIGUNG", listOf("LMT", "RTX", "NOC", "GD", "LHX", "HII", "BA", "LDOS", "TDG", "HWM", "KTOS", "AVAV", "RHM.DE", "HAG.DE", "R3NK.DE", "TKA.DE", "BA.L", "RR.L", "SAAB-B.ST", "LDO.MI", "HO.PA", "AM.PA", "KOG.OL", "7011.T")),
        Korb("WELTRAUM", listOf("RKLB", "ASTS", "LUNR", "PL", "IRDM", "VSAT", "RDW", "BKSY", "GSAT", "SPCE", "AIR.DE", "MDA.TO", "SES.PA")),
        Korb("CYBER", listOf("CRWD", "PANW", "FTNT", "ZS", "S", "OKTA", "NET", "CHKP", "QLYS", "TENB", "RPD", "GEN", "VRNS")),
        Korb("KERNENERGIE", listOf("CCJ", "SMR", "OKLO", "CEG", "VST", "BWXT", "LEU", "NNE", "UEC", "GEV", "CW", "5020.T")),
        Korb("ROBOTIK", listOf("ISRG", "TER", "ROK", "SYM", "ZBRA", "CGNX", "TSLA", "6954.T", "6506.T", "6861.T", "6645.T", "6273.T", "ABBN.SW", "KGX.DE", "SIE.DE")),
        Korb("SPIELE", listOf("TTWO", "RBLX", "U", "SE", "NTES", "7974.T", "9766.T", "9684.T", "6758.T", "7832.T", "3659.T", "UBI.PA", "CRSR")),
        Korb("KRYPTO", listOf("COIN", "MSTR", "MARA", "RIOT", "CLSK", "HUT", "CORZ", "CIFR", "BTBT", "HOOD", "GLXY", "XYZ", "PYPL")),
        Korb("ABNEHMEN", listOf("LLY", "NVO", "NOVO-B.CO", "VKTX", "AMGN", "PFE", "HIMS", "ZEAL.CO", "ROG.SW", "SAN.PA", "AZN.L", "GSK.L")),
        Korb("WASSERSTOFF", listOf("PLUG", "BE", "BLDP", "FCEL", "LIN", "AI.PA", "NEL.OL", "ITM.L", "ENR.DE", "SFQ.DE", "ENGI.PA")),
        Korb("SOLAR", listOf("FSLR", "ENPH", "SEDG", "RUN", "NXT", "CSIQ", "JKS", "ARRY", "SMA.DE", "SLR.MC", "ORSTED.CO")),
    )

    val ALLE_SYMBOLE: List<String> get() = KOERBE.flatMap { it.symbole }.distinct()

    class Ergebnis(
        val korb: Korb,
        val median: Double?,
        // Anteil steigender Werte 0 bis 1
        val steigend: Double?,
        val anzahl: Int,
        val anzahlSteigend: Int,
        val groesster: Pair<String, Double>?,
        // Alle Werte mit Aenderung, der groesste Gewinner zuerst
        val werte: List<Pair<String, Double>>,
    )

    fun median(l: List<Double>): Double? {
        if (l.isEmpty()) return null
        val s = l.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    fun auswerten(korb: Korb, aenderung: Map<String, Double>): Ergebnis {
        val werte = korb.symbole.distinct().mapNotNull { s -> aenderung[s]?.takeIf { it.isFinite() }?.let { s to it } }
        val steigt = werte.count { it.second > 0 }
        return Ergebnis(
            korb, median(werte.map { it.second }),
            if (werte.isEmpty()) null else steigt.toDouble() / werte.size,
            werte.size, steigt,
            werte.maxByOrNull { abs(it.second) },
            werte.sortedByDescending { it.second },
        )
    }
}

// Beitrag eines Werts zur Bewegung eines Index: Gewicht mal Aenderung, in Prozentpunkten der Indexbewegung. Das
// Gewicht ist der Boersenwert; Werte ohne Aenderung zaehlen nicht mit.
object IndexBeitrag {
    class Eingabe(val symbol: String, val name: String, val gewicht: Double, val aenderung: Double?)
    class Beitrag(val symbol: String, val name: String, val punkte: Double, val aenderung: Double)
    class Ergebnis(val gesamt: Double, val hoch: List<Beitrag>, val runter: List<Beitrag>)

    fun berechnen(werte: List<Eingabe>, anzahl: Int = 3): Ergebnis? {
        val mit = werte.filter { it.gewicht > 0 && it.gewicht.isFinite() && it.aenderung != null && it.aenderung.isFinite() }
        val summe = mit.sumOf { it.gewicht }
        if (mit.size < 2 || summe <= 0) return null
        val alle = mit.map { Beitrag(it.symbol, it.name, it.gewicht / summe * it.aenderung!!, it.aenderung) }
        return Ergebnis(
            alle.sumOf { it.punkte },
            alle.filter { it.punkte > 0 }.sortedByDescending { it.punkte }.take(anzahl),
            alle.filter { it.punkte < 0 }.sortedBy { it.punkte }.take(anzahl),
        )
    }
}

// Mover: relatives Volumen (Tagesumsatz gegen Schnitt der letzten zehn Tage) und ein Etikett, was die Bewegung
// begleitet. Zahlen und Analystenstufe kommen aus Kalender und Schlagzeilen, das Hoch oder Tief aus dem Kurs.
object MoverGrund {
    enum class Grund { ZAHLEN, ANALYST, HOCH, TIEF }

    const val RVOL_FILTER = 2.0

    fun rvol(q: Quote): Double? = RelativesVolumen.wert(q.volume, q.avgVolume10)

    // Nahe am 52-Wochen-Hoch oder -Tief: innerhalb von einem Prozent
    fun grund(q: Quote, zahlen: Boolean, analyst: Boolean): Grund? = when {
        zahlen -> Grund.ZAHLEN
        analyst -> Grund.ANALYST
        q.weekHigh52 > 0 && q.price >= q.weekHigh52 * 0.99 -> Grund.HOCH
        q.weekLow52 > 0 && q.price > 0 && q.price <= q.weekLow52 * 1.01 -> Grund.TIEF
        else -> null
    }
}

// Art einer Schlagzeile nach Stichwortregeln (Deutsch und Englisch). Eine Schlagzeile kann mehrere Arten tragen; die
// erste in der Reihenfolge der Liste steht als Etikett.
object NachrichtArt {
    enum class Art { ZAHLEN, ANALYST, UEBERNAHME, KAPITAL, ZULASSUNG, AUFTRAG, SEKTOR }

    private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    private val REGELN: List<Pair<Art, Regex>> = listOf(
        Art.ZAHLEN to r("\\b(earnings|quarterly\\s+(results|profit|revenue)|q[1-4]\\s+(results|earnings|revenue|profit)|(first|second|third|fourth)[- ]quarter|full[- ]year\\s+(results|profit|revenue)|(beats?|misses?|tops?|trails?)\\s+(\\w+\\s+)?(estimates?|expectations?|forecasts?)|(raises?|lifts?|cuts?|lowers?|reaffirms?|confirms?)\\s+(its\\s+)?(\\w+\\s+)?(guidance|outlook|forecast)|profit\\s+warning|(revenue|profit|earnings|results)\\s+(rises?|falls?|jumps?|drops?|grows?|climbs?|declines?|up|down)|net\\s+(income|loss))\\b|quartalszahlen|quartalsbilanz|quartalsergebnis|gesch(ä|ae)ftszahlen|jahreszahlen|halbjahreszahlen|neunmonatszahlen|q[1-4][- ]zahlen|rekordgewinn|rekordumsatz|rekordergebnis|quartalsgewinn|quartalsumsatz|gewinnsprung|gewinneinbruch|(ü|ue)bertrifft\\s+(die\\s+)?erwartungen|verfehlt\\s+(die\\s+)?erwartungen|gewinnwarnung|prognose\\s+(angehoben|gesenkt|bestätigt|best(ä|ae)tigt)|hebt\\s+(die\\s+)?prognose|senkt\\s+(die\\s+)?prognose|(umsatz|gewinn)\\s+(steigt|sinkt|bricht|w(ä|ae)chst|springt)"),
        Art.ANALYST to r("\\b(upgrades\\s+[A-Z]\\w*|upgraded?\\s+(to|by|at|from)\\s|downgrades?|downgraded|price\\s+targets?|initiates?\\s+coverage|(raises?|cuts?|lowers?|lifts?)\\s+(\\w+\\s+)?target|outperform|underperform|overweight|underweight|buy\\s+rating|sell\\s+rating|top\\s+pick)\\b|kursziel|hochgestuft|abgestuft|stuft\\s+\\w+\\s+(hoch|ab)|analyst(en)?\\s+(sehen|empfehlen)|kaufempfehlung|verkaufsempfehlung|einstufung"),
        Art.UEBERNAHME to r("\\b(acquires?|acquired|acquisition|takeover|take[- ]over|merger|merges?|buyout|buy[- ]out|(bids?|offers?)\\s+(\\$?[\\d.,]+\\s*\\w*\\s+)?(for|to\\s+buy|to\\s+acquire)|agrees?\\s+to\\s+(buy|acquire|sell|be\\s+acquired)|to\\s+buy\\s+[A-Z]|spin[- ]?off)\\b|übernahme|(ü|ue)bernimmt|(ü|ue)bernahmeangebot|fusion|kauft\\s+[A-ZÄÖÜ]|abspaltung|verkauft\\s+sparte"),
        Art.KAPITAL to r("\\b(buybacks?|share\\s+repurchases?|repurchase\\s+program|stock\\s+split|share\\s+split|(public|secondary|stock|share)\\s+offering|offering\\s+of\\s+\\w*\\s*shares|issues?\\s+(new\\s+)?(shares|bonds|notes)|convertible|capital\\s+increase|dividend\\s+(hike|raise|increase|cut|suspend|boost)|(raises?|hikes?|increases?|cuts?|suspends?|initiates?)\\s+(its\\s+)?(quarterly\\s+)?dividend|special\\s+dividend|delist(s|ing)?)\\b|aktienr(ü|ue)ckkauf|r(ü|ue)ckkaufprogramm|kapitalerh(ö|oe)hung|aktiensplit|aktienspaltung|dividendenerh(ö|oe)hung|dividende\\s+(erh(ö|oe)ht|gestrichen|gesenkt|angehoben)|wandelanleihe|anleihe\\s+(platziert|begeben)"),
        Art.ZULASSUNG to r("\\b(fda|ema|ce\\s+mark|phase\\s+[123i]{1,3}\\s+(trial|study|data|results)|clinical\\s+trial|(regulatory\\s+)?(approval|approves?|approved|clearance|clears?|cleared)\\s*(for|of|to)?|breakthrough\\s+designation|fast\\s+track|complete\\s+response\\s+letter)\\b|zulassung|zugelassen|zulassungsantrag|genehmigt|genehmigung|studiendaten|klinische\\s+studie|phase[- ][123]"),
        Art.AUFTRAG to r("\\b(wins?|won|awarded|lands?|secures?|signs?|receives?|gets?|bags?|books?)\\s+(an?\\s+|the\\s+)?(\\$?[\\d.,]+\\s*(m|bn|b|million|billion|mln|mrd)?\\.?\\s+)?(\\w+\\s+){0,3}(contract|order|orders|deal|award|tender)\\b|\\b(contract|order|orders)\\s+(worth|valued|from|win|win)\\b|\\bbacklog\\b|gro(ß|ss)auftrag|auftrag\\s+(\\w+\\s+)?(über|im\\s+wert|von)|erh(ä|ae)lt\\s+(\\w+\\s+)?(auftrag|zuschlag)|gewinnt\\s+(\\w+\\s+)?(auftrag|ausschreibung)|zuschlag\\s+f(ü|ue)r|auftragseingang|auftragsbestand"),
        Art.SEKTOR to r("\\b(sector|industry|industries|chip\\s+stocks|chipmakers|semiconductor\\s+stocks|tech\\s+stocks|bank\\s+stocks|banks\\s+(fall|rise|rally|slide|gain|drop)|energy\\s+stocks|oil\\s+stocks|pharma\\s+stocks|auto\\s+stocks|carmakers|automakers|airlines|miners|defense\\s+stocks|retailers|utilities|homebuilders)\\b|\\bsektor\\b|branche|chipwerte|halbleiterwerte|technologiewerte|techwerte|bankenwerte|bankaktien|energiewerte|ölwerte|oelwerte|pharmawerte|autowerte|autobauer|r(ü|ue)stungswerte|r(ü|ue)stungsaktien|fluggesellschaften|minenwerte|einzelhandelswerte|versorger"),
    )

    fun arten(titel: String): List<Art> = REGELN.filter { it.second.containsMatchIn(titel) }.map { it.first }
}

// Sektortabelle aus den Branchen-ETFs: Kennzahlen je ETF aus der Abfrage, mit den Ausweichfeldern von Yahoo.
object Sektortabelle {
    class Zeile(val symbol: String, val tag: Double?, val kgv: Double?, val rendite: Double?, val marktwert: Double?)

    // forwardPE, sonst trailingPE; Rendite aus yield (Prozent), sonst trailingAnnualDividendYield (Anteil); Marktwert
    // aus marketCap, sonst netAssets
    fun zeile(symbol: String, tag: Double?, k: Map<String, Double>?): Zeile {
        val kgv = (k?.get("forwardPE")?.takeIf { it > 0 } ?: k?.get("trailingPE")?.takeIf { it > 0 })
        val rendite = k?.get("yield")?.takeIf { it > 0 } ?: k?.get("trailingAnnualDividendYield")?.takeIf { it > 0 }?.times(100.0)
        val wert = k?.get("marketCap")?.takeIf { it > 0 } ?: k?.get("netAssets")?.takeIf { it > 0 }
        return Zeile(symbol, tag, kgv, rendite, wert)
    }

    val FELDER = listOf("forwardPE", "trailingPE", "yield", "trailingAnnualDividendYield", "marketCap", "netAssets")
}

// Aenderung der Erwaehnungen gegenueber der Vorperiode gleicher Laenge in Prozent. Null, wenn die Vorperiode nicht
// ganz im Verlauf liegt oder dort nichts erwaehnt wurde.
object RedditVorperiode {
    fun aenderung(a: Trendverlauf.Archiv, quelle: String, kuerzel: String, tage: Int, heute: Long): Double? {
        val seit = Trendverlauf.seit(a, quelle) ?: return null
        if (seit > heute - 2L * tage + 1) return null
        val jetzt = Trendverlauf.reihe(a, quelle, kuerzel, tage, heute).sum()
        val davor = Trendverlauf.reihe(a, quelle, kuerzel, tage, heute - tage).sum()
        if (davor <= 0) return null
        return (jetzt.toDouble() / davor - 1) * 100.0
    }
}

// Ergebnis des ersten Handelstags eines Boersengangs: Schluss des ersten Handelstags gegen den Ausgabepreis.
object ErsterTag {
    private const val TAG_MS = 86_400_000L

    // tag: Tag des Handelsbeginns (Tage seit 1970). Der erste Kurs muss in den ersten vier Tagen danach liegen, sonst
    // fehlt der Tag in den Kursen.
    fun ergebnis(kerzen: List<Candle>, tag: Long, preis: Double?): Double? {
        if (preis == null || preis <= 0) return null
        val erste = kerzen.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }.firstOrNull { it.t >= tag * TAG_MS } ?: return null
        if (erste.t >= (tag + 4) * TAG_MS) return null
        return (erste.close / preis - 1) * 100.0
    }
}
