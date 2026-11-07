package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.NasdaqKalender.Wirtschaftstermin
import kotlin.math.abs

// [Wirkungskette] Schlussfolgerungen aus Wirtschaftsmeldungen (0.69): Welche Meldung kam, wie weit lag sie von der
// Erwartung, was folgt typischerweise fuer Zinsen, Renditen, Waehrung und Sektoren, und was der Markt am selben Tag
// wirklich getan hat. Reine Funktionen ohne Netz.
//
// Die Regeln sind fest und stehen mit Belegen in der Datei wirkungsketten-belege.md; kurz:
// - Zinsen: Reaktionsfunktion der Notenbank (Taylor 1993, Clarida/Gali/Gertler 2000): staerkeres Wachstum und hoehere
//   Inflation sprechen fuer spaetere Senkungen oder Erhoehungen, schwaechere fuer fruehere Senkungen.
// - Renditen und Waehrung: Neuigkeiten ueber Wachstum heben in der Regel Renditen und die Waehrung (Andersen u. a. 2003).
// - Sektoren: Werte mit langer Equity Duration (Wachstum, Tech) reagieren staerker auf Zinsen (Dechow/Sloan/Soliman 2004).
// - Grenze: Ob Daten gut oder schlecht fuer Aktien sind, haengt von der Konjunkturlage ab (Boyd/Hu/Jagannathan 2005).
// Es gibt keine Wahrscheinlichkeiten und keine Kursziele, nur die Richtung der typischen Folge.
object Wirkungskette {

    // ------------------------------------------------------------ Regionen

    enum class Region { US, EURO, UK, JP, CH, CN }

    // Land der Nasdaq-Liste; Deutschland und Frankreich haengen an der EZB und dem Euro
    fun region(land: String): Region? = when (land) {
        "United States" -> Region.US
        "Euro Zone", "Germany", "France" -> Region.EURO
        "United Kingdom" -> Region.UK
        "Japan" -> Region.JP
        "Switzerland" -> Region.CH
        "China" -> Region.CN
        else -> null
    }

    // ------------------------------------------------------------ Ereignisarten

    enum class Thema { ARBEIT, WACHSTUM, KONSUM, STIMMUNG, IMMOBILIEN, PREISE, LOEHNE, ZINS }

    // Toleranz fuer "wie erwartet": Abweichungen bis max(absolut, relativ zur Erwartung) zaehlen als im Rahmen.
    // Absolut in den Einheiten des geparsten Werts (K, M, B sind schon ausmultipliziert, Prozentwerte in Punkten).
    // vorzeichen: +1 wenn ein hoeherer Wert staerkeres Wachstum bzw. hoehere Preise bedeutet, -1 wenn schwaecheres.
    // gewicht: wie stark die Meldung in die Gesamtaussage eingeht.
    enum class Art(val thema: Thema, val vorzeichen: Int, val gewicht: Int, val absolut: Double, val relativ: Double) {
        STELLEN(Thema.ARBEIT, 1, 2, 15_000.0, 0.10),
        STELLEN_ADP(Thema.ARBEIT, 1, 1, 15_000.0, 0.15),
        ARBEITSLOSENQUOTE(Thema.ARBEIT, -1, 2, 0.05, 0.0),
        ERSTANTRAEGE(Thema.ARBEIT, -1, 1, 3_000.0, 0.04),
        ARBEITSLOSE_AENDERUNG(Thema.ARBEIT, -1, 1, 5_000.0, 0.0),
        OFFENE_STELLEN(Thema.ARBEIT, 1, 1, 100_000.0, 0.03),
        LOEHNE(Thema.LOEHNE, 1, 1, 0.05, 0.0),
        BIP(Thema.WACHSTUM, 1, 2, 0.1, 0.0),
        EINKAUFSMANAGER_ISM(Thema.WACHSTUM, 1, 2, 0.5, 0.0),
        EINKAUFSMANAGER(Thema.WACHSTUM, 1, 1, 0.5, 0.0),
        PRODUKTION(Thema.WACHSTUM, 1, 1, 0.3, 0.0),
        AUFTRAEGE(Thema.WACHSTUM, 1, 1, 0.5, 0.0),
        EINZELHANDEL(Thema.KONSUM, 1, 2, 0.3, 0.0),
        KONSUMAUSGABEN(Thema.KONSUM, 1, 1, 0.2, 0.0),
        VERTRAUEN(Thema.STIMMUNG, 1, 1, 1.0, 0.02),
        ZEW(Thema.STIMMUNG, 1, 1, 3.0, 0.0),
        IMMOBILIEN(Thema.IMMOBILIEN, 1, 1, 0.1, 0.04),
        VERBRAUCHERPREISE(Thema.PREISE, 1, 2, 0.05, 0.0),
        VERBRAUCHERPREISE_TOKIO(Thema.PREISE, 1, 1, 0.05, 0.0),
        ERZEUGERPREISE(Thema.PREISE, 1, 1, 0.1, 0.0),
        PREISINDEX_UMFRAGE(Thema.PREISE, 1, 1, 1.0, 0.0),
        INFLATIONSERWARTUNG(Thema.PREISE, 1, 1, 0.2, 0.0),
        ZINSENTSCHEID(Thema.ZINS, 1, 3, 0.01, 0.0),
    }

    private fun m(regex: String, art: Art) = Regex(regex, RegexOption.IGNORE_CASE) to art

    // Namen der Nasdaq-Liste (englisch, wie geliefert); die erste passende Zeile gilt. Regionale Zahlen
    // (Bayern, Hessen), Importe, Salden, CFTC und Auktionen zaehlen nicht.
    private val MUSTER: List<Pair<Regex, Art>> = listOf(
        m("^(Fed|ECB|BoE|BoJ|SNB|PBoC) Interest Rate Decision$|^Loan Prime Rate.*", Art.ZINSENTSCHEID),
        m("^(Nonfarm Payrolls|Private Nonfarm Payrolls)$", Art.STELLEN),
        m("^(ADP Nonfarm Employment Change|Manufacturing Payrolls)$", Art.STELLEN_ADP),
        m("^(U6 )?Unemployment Rate( s\\.a\\.)?$|^German Unemployment Rate$", Art.ARBEITSLOSENQUOTE),
        m("^(Initial|Continuing) Jobless Claims$", Art.ERSTANTRAEGE),
        m("^German Unemployment Change$", Art.ARBEITSLOSE_AENDERUNG),
        m("^JOLTS Job Openings$", Art.OFFENE_STELLEN),
        m("^(Average Hourly Earnings( \\(YoY\\))?|Employment Cost Index|Average Cash Earnings|Overall wage income of employees)$", Art.LOEHNE),
        m("^((French|German) )?GDP$", Art.BIP),
        m("^ISM (Manufacturing|Non-Manufacturing) PMI$", Art.EINKAUFSMANAGER_ISM),
        m("^(?!.*Construction).*PMI$", Art.EINKAUFSMANAGER),
        m("^((French|German) )?Industrial Production$", Art.PRODUKTION),
        m("^((German) )?Factory Orders$|^Durable Goods Orders$|^Core Durable Goods Orders$|^Core Machinery Orders$", Art.AUFTRAEGE),
        m("^((German) )?Retail Sales$|^Core Retail Sales.*$|^Retail Sales Ex.*$", Art.EINZELHANDEL),
        m("^(Personal Spending|French Consumer Spending|Real Consumer Spending)$", Art.KONSUMAUSGABEN),
        m("^ZEW .*$", Art.ZEW),
        m("^(CB Consumer Confidence|Consumer Confidence|Michigan Consumer Sentiment|Michigan Consumer Expectations|French Consumer Confidence|GfK German Consumer Climate|SECO Consumer Climate|Ifo .*|Business Climate|Business and Consumer Survey|Industrial Sentiment|Services Sentiment|KOF Leading Indicators|Household Confidence)$", Art.VERTRAUEN),
        m("^(Housing Starts|Building Permits|New Home Sales|Existing Home Sales|Pending Home Sales|Nationwide HPI|Lloyds House Price Index|House Price Index|Mortgage Approvals)$", Art.IMMOBILIEN),
        m("^(Tokyo CPI|Tokyo Core CPI|BoJ Core CPI)$", Art.VERBRAUCHERPREISE_TOKIO),
        m("^(CPI|Core CPI|French CPI|French HICP|German CPI|German HICP|HICP ex Energy & Food|Core PCE Price Index|Core PCE Prices|PCE Price index|PCE Prices)$", Art.VERBRAUCHERPREISE),
        m("^ISM (Manufacturing|Non-Manufacturing) Prices$", Art.PREISINDEX_UMFRAGE),
        m("^(PPI|French PPI|German WPI|German Import Price Index)$", Art.ERZEUGERPREISE),
        m("^(Michigan 1-Year Inflation Expectations|NY Fed 1-Year Consumer Inflation Expectations)$", Art.INFLATIONSERWARTUNG),
    )

    fun art(name: String): Art? = MUSTER.firstOrNull { it.first.containsMatchIn(name.trim()) }?.second

    // ------------------------------------------------------------ Werte lesen

    // „165K“, „1,701K“, „7.523M“, „4.1%“, „-132.60B“: Zahl mit ausmultipliziertem Suffix; null bei Unlesbarem
    fun zahl(s: String?): Double? {
        var t = s?.trim()?.removeSuffix("%")?.trim() ?: return null
        if (t.isEmpty()) return null
        val faktor = when (t.last()) { 'K' -> 1e3; 'M' -> 1e6; 'B' -> 1e9; 'T' -> 1e12; else -> 1.0 }
        if (faktor != 1.0) t = t.dropLast(1)
        t = t.replace(",", "").replace("$", "").replace("€", "").replace("£", "").replace("¥", "")
        return t.toDoubleOrNull()?.times(faktor)
    }

    // ------------------------------------------------------------ Ueberraschung

    // STAERKER: hoeher in Richtung Wachstum bzw. Preise (bei Arbeitslosenquote und Erstantraegen also niedriger)
    enum class Lage { STAERKER, SCHWAECHER, ERWARTET }

    // Bezug: gegen die Erwartung (Konsens) oder, wenn es keine gibt, gegen den Vorwert
    enum class Bezug { ERWARTUNG, VORWERT }

    data class Posten(
        val art: Art,
        val name: String,
        val land: String,
        val region: Region,
        val ist: String,
        val referenz: String,
        val bezug: Bezug,
        val lage: Lage,
    )

    // Ueberraschung einer veroeffentlichten Meldung; null bei unbekannter Art, ohne Istwert oder ohne Vergleichswert
    fun ueberraschung(t: Wirtschaftstermin): Posten? {
        val art = art(t.name) ?: return null
        val region = region(t.land) ?: return null
        val istText = t.ist ?: return null
        val ist = zahl(istText) ?: return null
        val bezug = if (t.erwartet != null && zahl(t.erwartet) != null) Bezug.ERWARTUNG else Bezug.VORWERT
        val refText = (if (bezug == Bezug.ERWARTUNG) t.erwartet else t.vorher) ?: return null
        val ref = zahl(refText) ?: return null
        val d = ist - ref
        val grenze = maxOf(art.absolut, art.relativ * abs(ref))
        val lage = when {
            abs(d) <= grenze + 1e-9 -> Lage.ERWARTET
            d * art.vorzeichen > 0 -> Lage.STAERKER
            else -> Lage.SCHWAECHER
        }
        return Posten(art, t.name, t.land, region, istText, refText, bezug, lage)
    }

    // ------------------------------------------------------------ Kette

    enum class Fall {
        ERWARTET, GEMISCHT, STARK, SCHWACH, HEISS, GOLDILOCKS, STAGFLATION, SCHWACH_ENTSPANNT, INFLATION_HOCH, INFLATION_NIEDRIG,
        ZINS_STRAFFER, ZINS_LOCKERER, ZINS_ERWARTET,
    }

    enum class ZinsFolge { SENKUNG_WENIGER, ERHOEHUNG_MEHR, SENKUNG_MEHR, KEIN_DRUCK, ZIELKONFLIKT, STRAFFER, LOCKERER, LOCKERUNG_WENIGER, LOCKERUNG_MEHR }

    enum class Rich { HOEHER, TIEFER, OFFEN }

    enum class Sektor { TECH, IMMOBILIEN, VERSORGER, KLEINWERTE, BANKEN, ZYKLIKER, ENERGIE, ROHSTOFFE, DEFENSIV, EXPORTEURE, LUXUS }

    data class Kette(
        val zins: ZinsFolge?,
        val rendite: Rich?,
        val waehrung: Rich?,
        val gestuetzt: List<Sektor>,
        val belastet: List<Sektor>,
    )

    // Richtung einer Achse aus den Meldungen: null ohne Meldung, ERWARTET wenn alle im Rahmen, null-Wert GEMISCHT als
    // Lage nicht darstellbar, darum Paar (Lage, gemischt)
    private data class Achse(val lage: Lage?, val gemischt: Boolean)

    private fun achse(posten: List<Posten>): Achse {
        if (posten.isEmpty()) return Achse(null, false)
        val plus = posten.filter { it.lage == Lage.STAERKER }.sumOf { it.art.gewicht }
        val minus = posten.filter { it.lage == Lage.SCHWAECHER }.sumOf { it.art.gewicht }
        return when {
            plus == 0 && minus == 0 -> Achse(Lage.ERWARTET, false)
            minus == 0 -> Achse(Lage.STAERKER, false)
            plus == 0 -> Achse(Lage.SCHWAECHER, false)
            plus >= 2 * minus -> Achse(Lage.STAERKER, false)
            minus >= 2 * plus -> Achse(Lage.SCHWAECHER, false)
            else -> Achse(null, true)
        }
    }

    private fun wachstumsPosten(p: List<Posten>) = p.filter { it.art.thema != Thema.PREISE && it.art.thema != Thema.LOEHNE && it.art.thema != Thema.ZINS }
    private fun preisPosten(p: List<Posten>) = p.filter { it.art.thema == Thema.PREISE || it.art.thema == Thema.LOEHNE }

    // Fall aus Wachstums- und Preisachse; Zinsentscheide gehen vor
    fun fall(posten: List<Posten>): Fall {
        posten.filter { it.art == Art.ZINSENTSCHEID }.let { z ->
            if (z.isNotEmpty()) return when {
                z.any { it.lage == Lage.STAERKER } -> Fall.ZINS_STRAFFER
                z.any { it.lage == Lage.SCHWAECHER } -> Fall.ZINS_LOCKERER
                else -> Fall.ZINS_ERWARTET
            }
        }
        val w = achse(wachstumsPosten(posten))
        val p = achse(preisPosten(posten))
        val wl = w.lage?.takeIf { it != Lage.ERWARTET }
        val pl = p.lage?.takeIf { it != Lage.ERWARTET }
        return when {
            wl == Lage.STAERKER && pl == Lage.SCHWAECHER -> Fall.GOLDILOCKS
            wl == Lage.STAERKER && pl == Lage.STAERKER -> Fall.HEISS
            wl == Lage.STAERKER -> Fall.STARK
            wl == Lage.SCHWAECHER && pl == Lage.STAERKER -> Fall.STAGFLATION
            wl == Lage.SCHWAECHER && pl == Lage.SCHWAECHER -> Fall.SCHWACH_ENTSPANNT
            wl == Lage.SCHWAECHER -> Fall.SCHWACH
            pl == Lage.STAERKER -> Fall.INFLATION_HOCH
            pl == Lage.SCHWAECHER -> Fall.INFLATION_NIEDRIG
            w.gemischt || p.gemischt -> Fall.GEMISCHT
            else -> Fall.ERWARTET
        }
    }

    // Kette zu einem Fall; null ohne eindeutige Folge (wie erwartet, gemischt, Zinsentscheid wie erwartet)
    fun kette(fall: Fall, region: Region): Kette? {
        if (region == Region.CN) return ketteChina(fall)
        val k = when (fall) {
            Fall.STARK -> Kette(ZinsFolge.SENKUNG_WENIGER, Rich.HOEHER, Rich.HOEHER,
                listOf(Sektor.BANKEN, Sektor.ZYKLIKER), listOf(Sektor.TECH, Sektor.IMMOBILIEN, Sektor.VERSORGER, Sektor.KLEINWERTE))
            Fall.HEISS -> Kette(ZinsFolge.ERHOEHUNG_MEHR, Rich.HOEHER, Rich.HOEHER,
                listOf(Sektor.BANKEN, Sektor.ZYKLIKER, Sektor.ENERGIE), listOf(Sektor.TECH, Sektor.IMMOBILIEN, Sektor.VERSORGER, Sektor.KLEINWERTE))
            Fall.INFLATION_HOCH -> Kette(ZinsFolge.SENKUNG_WENIGER, Rich.HOEHER, Rich.HOEHER,
                listOf(Sektor.ENERGIE, Sektor.ROHSTOFFE, Sektor.BANKEN), listOf(Sektor.TECH, Sektor.IMMOBILIEN, Sektor.VERSORGER, Sektor.KLEINWERTE))
            Fall.INFLATION_NIEDRIG -> Kette(ZinsFolge.SENKUNG_MEHR, Rich.TIEFER, Rich.TIEFER,
                listOf(Sektor.TECH, Sektor.IMMOBILIEN, Sektor.VERSORGER, Sektor.KLEINWERTE), emptyList())
            Fall.SCHWACH, Fall.SCHWACH_ENTSPANNT -> Kette(ZinsFolge.SENKUNG_MEHR, Rich.TIEFER, Rich.TIEFER,
                listOf(Sektor.TECH, Sektor.IMMOBILIEN, Sektor.VERSORGER, Sektor.DEFENSIV), listOf(Sektor.BANKEN, Sektor.ZYKLIKER))
            Fall.GOLDILOCKS -> Kette(ZinsFolge.KEIN_DRUCK, null, null,
                listOf(Sektor.TECH, Sektor.ZYKLIKER, Sektor.KLEINWERTE), emptyList())
            Fall.STAGFLATION -> Kette(ZinsFolge.ZIELKONFLIKT, Rich.OFFEN, Rich.OFFEN,
                listOf(Sektor.ENERGIE, Sektor.ROHSTOFFE, Sektor.DEFENSIV), listOf(Sektor.ZYKLIKER, Sektor.TECH, Sektor.KLEINWERTE))
            // Ueberraschender Zinsentscheid: Banken fehlen, weil ihre Reaktion auf Zinsueberraschungen negativ gemessen wurde
            Fall.ZINS_STRAFFER -> Kette(ZinsFolge.STRAFFER, Rich.HOEHER, Rich.HOEHER,
                emptyList(), listOf(Sektor.TECH, Sektor.IMMOBILIEN, Sektor.VERSORGER, Sektor.KLEINWERTE))
            Fall.ZINS_LOCKERER -> Kette(ZinsFolge.LOCKERER, Rich.TIEFER, Rich.TIEFER,
                listOf(Sektor.TECH, Sektor.IMMOBILIEN, Sektor.VERSORGER, Sektor.KLEINWERTE), emptyList())
            Fall.ERWARTET, Fall.GEMISCHT, Fall.ZINS_ERWARTET -> return null
        }
        // Staerkere Waehrung belastet Exporteure (Auslandsumsaetze schrumpfen in Heimatwaehrung), schwaechere stuetzt sie
        return when (k.waehrung) {
            Rich.HOEHER -> k.copy(belastet = k.belastet + Sektor.EXPORTEURE)
            Rich.TIEFER -> k.copy(gestuetzt = k.gestuetzt + Sektor.EXPORTEURE)
            else -> k
        }
    }

    // China: Aktivitaet wirkt ueber Rohstoffnachfrage; die Zentralbank lockert bei Schwaeche
    private fun ketteChina(fall: Fall): Kette? = when (fall) {
        Fall.STARK, Fall.HEISS, Fall.GOLDILOCKS -> Kette(ZinsFolge.LOCKERUNG_WENIGER, null, Rich.HOEHER,
            listOf(Sektor.ROHSTOFFE, Sektor.ENERGIE, Sektor.LUXUS, Sektor.ZYKLIKER), emptyList())
        Fall.SCHWACH, Fall.SCHWACH_ENTSPANNT -> Kette(ZinsFolge.LOCKERUNG_MEHR, null, Rich.TIEFER,
            emptyList(), listOf(Sektor.ROHSTOFFE, Sektor.ENERGIE, Sektor.LUXUS, Sektor.ZYKLIKER))
        Fall.INFLATION_NIEDRIG -> Kette(ZinsFolge.LOCKERUNG_MEHR, null, null, emptyList(), emptyList())
        Fall.INFLATION_HOCH -> Kette(ZinsFolge.LOCKERUNG_WENIGER, null, null, emptyList(), emptyList())
        Fall.STAGFLATION -> Kette(ZinsFolge.ZIELKONFLIKT, null, Rich.OFFEN, emptyList(), emptyList())
        Fall.ZINS_STRAFFER -> Kette(ZinsFolge.STRAFFER, null, Rich.HOEHER, emptyList(), emptyList())
        Fall.ZINS_LOCKERER -> Kette(ZinsFolge.LOCKERER, null, Rich.TIEFER, emptyList(), emptyList())
        Fall.ERWARTET, Fall.GEMISCHT, Fall.ZINS_ERWARTET -> null
    }

    // ------------------------------------------------------------ Gesamtaussage

    data class Aussage(
        val tag: Long,
        val region: Region,
        val fall: Fall,
        // nach Gewicht absteigend, Reihenfolge der Meldungen sonst erhalten
        val posten: List<Posten>,
        // Thema der gewichtigsten Meldung, die den Fall traegt; fuer die Ueberschrift
        val kopf: Posten?,
        val kette: Kette?,
    )

    // Aussage zu den Meldungen einer Region an einem Tag. mindestGewicht: bei der Tagesuebersicht 2, damit eine
    // einzelne Randzahl keine Karte bekommt; null ohne verwertbare Meldung.
    fun aussage(tag: Long, region: Region, termine: List<Wirtschaftstermin>, mindestGewicht: Int = 0): Aussage? {
        // gleicher Name mehrfach (Monats- und Jahreswert): die erste Zeile mit Erwartung
        val posten = termine.filter { region(it.land) == region }
            .sortedBy { it.erwartet == null }
            .mapNotNull { ueberraschung(it) }
            .distinctBy { it.name }
            .sortedByDescending { it.art.gewicht }
        if (posten.isEmpty() || posten.sumOf { it.art.gewicht } < mindestGewicht) return null
        val fall = fall(posten)
        val traegt: (Posten) -> Boolean = when (fall) {
            Fall.ZINS_STRAFFER, Fall.ZINS_LOCKERER, Fall.ZINS_ERWARTET -> { p -> p.art == Art.ZINSENTSCHEID }
            else -> { p -> p.lage != Lage.ERWARTET }
        }
        return Aussage(tag, region, fall, posten, posten.firstOrNull(traegt) ?: posten.first(), kette(fall, region))
    }

    // Aussagen aller Regionen je Tag, neueste Tage zuerst, innerhalb eines Tages US, Euroraum, Grossbritannien, Japan,
    // Schweiz, China; Aussagen ohne Gewicht fehlen
    fun aussagen(termineJeTag: Map<Long, List<Wirtschaftstermin>>): List<Aussage> =
        termineJeTag.keys.sortedDescending().flatMap { tag ->
            Region.entries.mapNotNull { r -> aussage(tag, r, termineJeTag[tag].orEmpty(), mindestGewicht = 2) }
        }

    // Aussage zu einer einzelnen Meldung (fuer den Kurzverweis in der Terminliste); null ohne Folge oder wie erwartet
    fun einzel(t: Wirtschaftstermin): Aussage? {
        val p = ueberraschung(t) ?: return null
        if (p.lage == Lage.ERWARTET && p.art != Art.ZINSENTSCHEID) return null
        return aussage(t.tag, p.region, listOf(t))?.takeIf { it.kette != null }
    }

    // ------------------------------------------------------------ Gegenprobe mit Kursen

    enum class Urteil { PASST, ANDERS, GEMISCHT, OFFEN }

    enum class ZeilenArt { RENDITE, WAEHRUNG, INDEX, SEKTOR }

    // erwartung: +1 steigt, -1 faellt laut Kette, 0 ohne Aussage; passt null ohne Aussage oder bei Seitwaertsbewegung.
    // aenderung: Basispunkte bei der Rendite, sonst Prozent. schluessel: Kuerzel des Marktes bzw. Name des Sektors.
    data class Zeile(val art: ZeilenArt, val schluessel: String, val aenderung: Double, val bp: Boolean, val erwartung: Int, val passt: Boolean?)

    // Erwarteter Leitzins in zwoelf Monaten laut Fed-Funds-Futures, am Vortag und am Tag der Meldung
    data class ZinsPfad(val vorher: Double, val nachher: Double)

    data class Reaktion(val zeilen: List<Zeile>, val sektoren: List<Zeile>, val urteil: Urteil, val zins: ZinsPfad?)

    // Kurse je Region: Yahoo-Kuerzel
    data class Satz(
        val rendite: String?,
        val waehrung: String?,
        val waehrungInvertiert: Boolean,
        val vergleich: String,
        val indizes: List<String>,
        val sektoren: Map<Sektor, List<String>>,
    )

    private val SEKTOREN_US: Map<Sektor, List<String>> = mapOf(
        Sektor.TECH to listOf("XLK"), Sektor.IMMOBILIEN to listOf("XLRE"), Sektor.VERSORGER to listOf("XLU"),
        Sektor.KLEINWERTE to listOf("^RUT"), Sektor.BANKEN to listOf("XLF"), Sektor.ZYKLIKER to listOf("XLY", "XLI"),
        Sektor.ENERGIE to listOf("XLE"), Sektor.ROHSTOFFE to listOf("XLB"), Sektor.DEFENSIV to listOf("XLP", "XLV"),
    )

    // iShares STOXX Europe 600 Sektor-ETFs (Xetra), am 07.11.2026 mit Yahoo geprueft
    private val SEKTOREN_EU: Map<Sektor, List<String>> = mapOf(
        Sektor.TECH to listOf("EXV3.DE"), Sektor.IMMOBILIEN to listOf("EXI5.DE"), Sektor.VERSORGER to listOf("EXH9.DE"),
        Sektor.BANKEN to listOf("EXV1.DE"), Sektor.ZYKLIKER to listOf("EXH4.DE"), Sektor.ENERGIE to listOf("EXH1.DE"),
        Sektor.ROHSTOFFE to listOf("EXV6.DE"), Sektor.DEFENSIV to listOf("EXH3.DE", "EXV4.DE"), Sektor.LUXUS to listOf("EXH7.DE"),
    )

    fun satz(region: Region): Satz = when (region) {
        Region.US -> Satz("^TNX", "DX-Y.NYB", false, "^GSPC", listOf("^GSPC", "^NDX", "^RUT"), SEKTOREN_US)
        Region.EURO -> Satz(null, "EURUSD=X", false, "EXSA.DE", listOf("EXSA.DE", "^STOXX50E"), SEKTOREN_EU)
        Region.UK -> Satz(null, "GBPUSD=X", false, "^FTSE", listOf("^FTSE"), emptyMap())
        Region.JP -> Satz(null, "JPY=X", true, "^N225", listOf("^N225"), emptyMap())
        Region.CH -> Satz(null, "CHF=X", true, "^SSMI", listOf("^SSMI"), emptyMap())
        Region.CN -> Satz(null, "CNY=X", true, "000001.SS", listOf("000001.SS"), emptyMap())
    }

    // alle Kuerzel, die die Gegenprobe der Regionen braucht (hoechstens 20 je Abfrage, darum nach Regionen getrennt)
    fun symbole(region: Region): List<String> {
        val s = satz(region)
        return (listOfNotNull(s.rendite, s.waehrung) + s.indizes + s.sektoren.values.flatten()).distinct()
    }

    // Kontrakt der Fed-Funds-Futures fuer den Zins in zwoelf Monaten (ersatzweise elf)
    fun zinsKontrakte(jahr: Int, monat: Int): List<String> =
        Zinserwartung.kontrakte(jahr, monat, 14).let { k -> listOfNotNull(k.getOrNull(12), k.getOrNull(11)).map { it.symbol } }

    // Tage seit 1970 eines Kurspunkts; zwei Stunden Versatz, damit Waehrungskerzen vom Vorabend am richtigen Tag landen
    private fun tagVon(t: Long): Long = Math.floorDiv(t + 2 * 3_600_000L, 86_400_000L)

    // Schluss am Tag und Schluss davor; null, wenn der Tag oder ein Vortag fehlt
    fun schluesse(kerzen: List<Candle>?, tag: Long): Pair<Double, Double>? {
        val liste = kerzen?.filter { it.close.isFinite() && it.close > 0 } ?: return null
        val i = liste.indexOfLast { tagVon(it.t) == tag }
        if (i < 0) return null
        val davor = liste.take(i).lastOrNull { tagVon(it.t) < tag } ?: return null
        return liste[i].close to davor.close
    }

    private fun prozent(kerzen: List<Candle>?, tag: Long): Double? = schluesse(kerzen, tag)?.let { (a, b) -> (a / b - 1) * 100 }

    // Schwellen, unter denen eine Bewegung als seitwaerts gilt
    const val SCHWELLE_BP = 2.0
    const val SCHWELLE_WAEHRUNG = 0.1
    const val SCHWELLE_SEKTOR = 0.2

    private fun richtung(r: Rich?): Int = when (r) { Rich.HOEHER -> 1; Rich.TIEFER -> -1; else -> 0 }

    private fun passt(erwartung: Int, v: Double, schwelle: Double): Boolean? =
        if (erwartung == 0 || abs(v) < schwelle) null else (v > 0) == (erwartung > 0)

    // Reaktion am Tag der Aussage. kurse: Yahoo-Kuerzel zu Tagesschluessen.
    fun reaktion(a: Aussage, kurse: Map<String, List<Candle>>): Reaktion {
        val s = satz(a.region)
        val zeilen = ArrayList<Zeile>()
        s.rendite?.let { sym ->
            schluesse(kurse[sym], a.tag)?.let { (c, v) ->
                val bp = (c - v) * 100
                val e = richtung(a.kette?.rendite)
                zeilen += Zeile(ZeilenArt.RENDITE, sym, bp, true, e, passt(e, bp, SCHWELLE_BP))
            }
        }
        s.waehrung?.let { sym ->
            prozent(kurse[sym], a.tag)?.let { p ->
                // Dollar je Fremdwaehrung (Yen, Franken, Yuan): faellt der Kurs, wird die Fremdwaehrung staerker
                val v = if (s.waehrungInvertiert) -p else p
                val e = richtung(a.kette?.waehrung)
                zeilen += Zeile(ZeilenArt.WAEHRUNG, sym, v, false, e, passt(e, v, SCHWELLE_WAEHRUNG))
            }
        }
        val vergleich = prozent(kurse[s.vergleich], a.tag)
        for (sym in s.indizes) prozent(kurse[sym], a.tag)?.let { zeilen += Zeile(ZeilenArt.INDEX, sym, it, false, 0, null) }
        // Sektoren: Tagesveraenderung (Mittel der Kuerzel), Urteil gegen den Vergleichsindex
        val kette = a.kette
        val sektoren = s.sektoren.mapNotNull { (sektor, symbole) ->
            val p = symbole.mapNotNull { prozent(kurse[it], a.tag) }.takeIf { it.isNotEmpty() }?.average() ?: return@mapNotNull null
            val e = when {
                kette == null -> 0
                sektor in kette.gestuetzt -> 1
                sektor in kette.belastet -> -1
                else -> 0
            }
            Zeile(ZeilenArt.SEKTOR, sektor.name, p, false, e, vergleich?.let { passt(e, p - it, SCHWELLE_SEKTOR) })
        }.sortedByDescending { it.aenderung }
        val pruefungen = (zeilen.filter { it.art != ZeilenArt.INDEX } + sektoren).mapNotNull { it.passt }
        val ja = pruefungen.count { it }
        val nein = pruefungen.size - ja
        val urteil = when {
            kette == null || pruefungen.isEmpty() -> Urteil.OFFEN
            ja >= 2 * nein -> Urteil.PASST
            nein >= 2 * ja -> Urteil.ANDERS
            else -> Urteil.GEMISCHT
        }
        // Zeilen der Karte: Rendite, Waehrung, Indizes und die Sektoren der Kette
        val kartenSektoren = sektoren.filter { it.erwartung != 0 }
        return Reaktion(zeilen + kartenSektoren, sektoren, urteil, null)
    }

    // Zinspfad aus den Futures: erster Kontrakt mit Kursen am Tag der Aussage und davor
    fun zinsPfad(kurse: Map<String, List<Candle>>, kontrakte: List<String>, tag: Long): ZinsPfad? {
        for (sym in kontrakte) {
            val (c, v) = schluesse(kurse[sym], tag) ?: continue
            if (c !in 80.0..100.0 || v !in 80.0..100.0) continue
            return ZinsPfad(100 - v, 100 - c)
        }
        return null
    }
}
