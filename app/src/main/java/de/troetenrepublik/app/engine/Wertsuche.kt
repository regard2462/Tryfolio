package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Instrument
import de.troetenrepublik.app.market.MarketApi

// Wertpapiersuche [0.73]: Erkennung von ISIN und WKN, Pruefziffer, Falten von Umlauten, Rangfolge der Vorschlaege
// (eigene Werte vor Katalog vor Netz) und Ordnung der Handelsplaetze. Alles ohne Netz und ohne Oberflaeche.
object Wertsuche {

    enum class Art {
        // Freitext: Name oder Kuerzel
        TEXT,
        // Vollstaendige ISIN mit stimmender Pruefziffer
        ISIN,
        // Zwoelf Zeichen im ISIN-Muster, Pruefziffer stimmt nicht
        ISIN_PRUEFZIFFER,
        // Anfang einer ISIN (acht bis elf Zeichen)
        ISIN_TEIL,
        // Sechs Zeichen mit mindestens einer Ziffer
        WKN,
    }

    // begriff: Eingabe ohne Rand und mit einfachen Leerzeichen; kennung: bei ISIN und WKN in Grossbuchstaben ohne
    // Leer- und Trennzeichen, sonst leer.
    data class Eingabe(val begriff: String, val art: Art, val kennung: String)

    private val ERLAUBT = Regex("[A-Za-z0-9][A-Za-z0-9 \\t\\r\\n\\u00a0-]*")

    // Eingabe lesen: Zwischenablage mit Umbruch, Leerzeichen in der ISIN und Kleinbuchstaben werden verziehen.
    fun auswerten(roh: String): Eingabe {
        val begriff = roh.replace(Regex("[\\s\\u00a0]+"), " ").trim()
        if (begriff.isEmpty() || !ERLAUBT.matches(begriff)) return Eingabe(begriff, Art.TEXT, "")
        val kennung = begriff.filter { it.isLetterOrDigit() }.uppercase()
        val ziffernAbDritter = kennung.drop(2).count { it in '0'..'9' }
        val zweiBuchstaben = kennung.length >= 2 && kennung[0] in 'A'..'Z' && kennung[1] in 'A'..'Z'
        return when {
            zweiBuchstaben && kennung.length == 12 && kennung.last() in '0'..'9' ->
                Eingabe(begriff, if (pruefzifferOk(kennung)) Art.ISIN else Art.ISIN_PRUEFZIFFER, kennung)
            zweiBuchstaben && kennung.length in 8..11 && ziffernAbDritter >= 3 -> Eingabe(begriff, Art.ISIN_TEIL, kennung)
            // WKN ohne Leerzeichen eingegeben, sonst waeren Namen wie „Ab 12“ betroffen
            ' ' !in begriff && '-' !in begriff && kennung.length == 6 && kennung.any { it in '0'..'9' } -> Eingabe(begriff, Art.WKN, kennung)
            else -> Eingabe(begriff, Art.TEXT, "")
        }
    }

    // Pruefziffer nach ISO 6166: Buchstaben werden zu 10 bis 35, danach Luhn ueber alle Ziffern.
    fun pruefzifferOk(isin: String): Boolean {
        if (isin.length != 12) return false
        val ziffern = StringBuilder()
        for (c in isin) {
            when (c) {
                in '0'..'9' -> ziffern.append(c)
                in 'A'..'Z' -> ziffern.append(c - 'A' + 10)
                else -> return false
            }
        }
        if (isin[0] !in 'A'..'Z' || isin[1] !in 'A'..'Z' || isin.last() !in '0'..'9') return false
        var summe = 0
        for ((i, c) in ziffern.reversed().withIndex()) {
            var n = c - '0'
            if (i % 2 == 1) { n *= 2; if (n > 9) n -= 9 }
            summe += n
        }
        return summe % 10 == 0
    }

    // ------------------------------------------------------------ Falten

    // Erste Fassung: ae, oe, ue, ss; so schreibt Yahoo deutsche Namen („MUENCHENER RUECKVERS.“).
    fun faltenAe(s: String): String {
        val b = StringBuilder(s.length + 4)
        for (c in s.lowercase()) {
            when (c) {
                'ä' -> b.append("ae"); 'ö' -> b.append("oe"); 'ü' -> b.append("ue"); 'ß' -> b.append("ss")
                else -> b.append(ohneAkzent(c))
            }
        }
        return b.toString()
    }

    // Zweite Fassung: nur Akzente und Umlautpunkte weg (ä wird a, ß wird ss).
    fun faltenOhne(s: String): String {
        val b = StringBuilder(s.length + 2)
        for (c in s.lowercase()) { if (c == 'ß') b.append("ss") else b.append(ohneAkzent(c)) }
        return b.toString()
    }

    private fun ohneAkzent(c: Char): Char = when (c) {
        'ä', 'à', 'á', 'â', 'ã', 'å' -> 'a'
        'ö', 'ò', 'ó', 'ô', 'õ', 'ø' -> 'o'
        'ü', 'ù', 'ú', 'û' -> 'u'
        'è', 'é', 'ê', 'ë' -> 'e'
        'ì', 'í', 'î', 'ï' -> 'i'
        'ç' -> 'c'; 'ñ' -> 'n'; 'ý', 'ÿ' -> 'y'
        else -> c
    }

    // Buchstaben und Ziffern, alles andere wird ein einzelnes Leerzeichen.
    private fun wortform(s: String): String = buildString {
        var luecke = true
        for (c in s) {
            if (c.isLetterOrDigit()) { append(c); luecke = false }
            else if (!luecke) { append(' '); luecke = true }
        }
    }.trim()

    // Suchbegriff fuer Yahoo: Umlaute als ae, oe, ue, ss (Yahoo findet „Boerse“, aber nicht „Börse“).
    fun netzBegriff(begriff: String): String = faltenAe(begriff.trim())

    // Zweite Abfrage, wenn die Eingabe wie Kuerzel und Boersenzusatz aussieht: aus „SAP DE“ wird „SAP.DE“, sonst null.
    // Beides wird abgefragt, weil „deutsche f“ auch der Anfang eines Namens sein kann.
    fun kuerzelVariante(begriff: String): String? {
        val teile = begriff.trim().split(' ').filter { it.isNotEmpty() }
        if (teile.size == 2 && teile[1].uppercase() in BOERSENZUSAETZE && teile[0].none { it == '.' }) {
            return teile[0].uppercase() + "." + teile[1].uppercase()
        }
        return null
    }

    // ------------------------------------------------------------ Treffer und Stufen

    // 0: Name oder Kuerzel beginnt mit der Eingabe, 1: ein Wort des Namens beginnt damit, 2: kommt vor (nur ab zwei
    // Zeichen), null: passt nicht.
    fun stufe(frage: String, symbol: String, name: String, mitEnthalten: Boolean = true): Int? {
        val f1 = wortform(faltenAe(frage))
        val f2 = wortform(faltenOhne(frage))
        if (f1.isEmpty()) return null
        val sym = symbol.lowercase()
        val symFrage = frage.trim().lowercase()
        val symFrageMitPunkt = symFrage.replace(' ', '.')
        var beste: Int? = null
        fun merke(s: Int) { if (beste == null || s < beste!!) beste = s }
        if (sym.startsWith(symFrage) || sym.startsWith(symFrageMitPunkt)) merke(0)
        for ((f, n) in listOf(f1 to wortform(faltenAe(name)), f2 to wortform(faltenOhne(name)))) {
            if (f.isEmpty()) continue
            when {
                n.startsWith(f) -> merke(0)
                n.contains(" $f") -> merke(1)
                mitEnthalten && f.length >= 2 && n.contains(f) -> merke(2)
            }
        }
        if (mitEnthalten && symFrage.length >= 2 && sym.contains(symFrage)) merke(2)
        return beste
    }

    // 0: Kuerzel ist genau die Eingabe, 1: gleich ohne Boersenzusatz, 2: sonst.
    private fun kuerzelRang(frage: String, symbol: String): Int {
        val f = frage.trim().lowercase().replace(' ', '.')
        val s = symbol.lowercase()
        return when {
            s == f -> 0
            s.substringBefore('.') == f -> 1
            else -> 2
        }
    }

    enum class Herkunft { VERLAUF, DEPOT, WATCHLIST, KATALOG, NETZ }

    data class Vorschlag(val symbol: String, val name: String, val herkunft: Herkunft, val stufe: Int, val unterzeile: String = "")

    // Eigene Werte, die zur Eingabe passen: Verlauf (juengste zuerst), dann Depot, dann Watchlist; Kuerzel oder Name
    // beginnt mit der Eingabe (Stufe 0) oder enthaelt sie als Wortanfang (Stufe 1), schon ab dem ersten Zeichen.
    // Je Gruppe gilt: Praefix vor Wortanfang. Ohne Doppelte.
    fun eigene(
        frage: String,
        verlauf: List<Suchverlauf.Eintrag>,
        depot: List<Pair<String, String>>,
        watchlist: List<Pair<String, String>>,
        max: Int = 8,
    ): List<Vorschlag> {
        if (wortform(faltenAe(frage)).isEmpty()) return emptyList()
        val roh = ArrayList<Vorschlag>()
        verlauf.sortedByDescending { it.zuletzt }.forEach { e ->
            stufe(frage, e.symbol, e.name, mitEnthalten = false)?.let { roh += Vorschlag(e.symbol, e.name, Herkunft.VERLAUF, it) }
        }
        depot.forEach { (s, n) -> stufe(frage, s, n, mitEnthalten = false)?.let { roh += Vorschlag(s, n, Herkunft.DEPOT, it) } }
        watchlist.forEach { (s, n) -> stufe(frage, s, n, mitEnthalten = false)?.let { roh += Vorschlag(s, n, Herkunft.WATCHLIST, it) } }
        // Stabil sortiert: Stufe, dann Herkunft (Verlauf, Depot, Watchlist) in der Reihenfolge der Eingabe
        return roh.distinctBy { it.symbol }.sortedWith(compareBy({ it.stufe }, { it.herkunft.ordinal })).take(max)
    }

    // Katalogtreffer nach Stufe; ein genaues Kuerzel steht vor anderen Praefixen, dann Kuerzel- vor Namenspraefix.
    // ISIN-Eingaben (ganz oder Anfang) finden nur ueber die ISIN.
    fun katalog(eingabe: Eingabe, katalog: List<Instrument>, max: Int = 60): List<Instrument> {
        val isinSuche = eingabe.art == Art.ISIN || eingabe.art == Art.ISIN_TEIL || eingabe.art == Art.ISIN_PRUEFZIFFER
        if (isinSuche) return katalog.filter { it.isin.isNotEmpty() && it.isin.uppercase().startsWith(eingabe.kennung) }.take(max)
        val frage = eingabe.begriff
        if (frage.isEmpty()) return emptyList()
        val kennungFrage = eingabe.kennung.takeIf { eingabe.art == Art.WKN }
        val q = frage.lowercase()
        val treffer = ArrayList<Pair<Instrument, Int>>()
        for (i in katalog) {
            var s = stufe(frage, i.symbol, i.name)
            if (s == null && i.isin.isNotEmpty() && q.length >= 4 && i.isin.lowercase().startsWith(q)) s = 1
            if (s == null && kennungFrage == null && q.length >= 3 && i.sector.lowercase().contains(q)) s = 3
            if (s != null) treffer += i to s
        }
        return treffer.sortedWith(
            compareBy<Pair<Instrument, Int>>(
                { it.second },
                { kuerzelRang(frage, it.first.symbol) },
                { if (it.first.symbol.lowercase().startsWith(q)) 0 else 1 },
            ),
        ).map { it.first }.take(max)
    }

    // Gesamtreihenfolge der Vorschlaege: eigene, Katalog, Netz; jedes Kuerzel nur einmal.
    fun mischen(eigene: List<Vorschlag>, katalog: List<Vorschlag>, netz: List<Vorschlag>): List<Vorschlag> =
        (eigene + katalog + netz).distinctBy { it.symbol }

    // ------------------------------------------------------------ Handelsplaetze

    // Yahoo-Zusatz der Hauptboerse je Land (ISO-Kuerzel); leer steht fuer US-Werte ohne Zusatz, null fuer unbekannt.
    private val HAUPTBOERSE = mapOf(
        "DE" to ".DE", "US" to "", "GB" to ".L", "FR" to ".PA", "NL" to ".AS", "BE" to ".BR", "IT" to ".MI", "ES" to ".MC",
        "PT" to ".LS", "CH" to ".SW", "AT" to ".VI", "SE" to ".ST", "DK" to ".CO", "NO" to ".OL", "FI" to ".HE", "IE" to ".IR",
        "PL" to ".WA", "CA" to ".TO", "AU" to ".AX", "NZ" to ".NZ", "JP" to ".T", "HK" to ".HK", "KR" to ".KS", "TW" to ".TW",
        "SG" to ".SI", "IN" to ".NS", "BR" to ".SA", "MX" to ".MX", "ZA" to ".JO", "TR" to ".IS", "CN" to ".SS",
    )

    private val BOERSENZUSAETZE = (HAUPTBOERSE.values.filter { it.isNotEmpty() }.map { it.removePrefix(".") } +
        listOf("F", "SG", "MU", "HM", "DU", "BE", "HA", "V", "CN", "BO", "SZ")).toSet()

    // Boersenzusatz eines Yahoo-Symbols (mit Punkt) oder leer; Kryptopaare und Indizes haben keinen.
    fun zusatz(symbol: String): String {
        if (symbol.startsWith("^") || '=' in symbol) return "?"
        val i = symbol.lastIndexOf('.')
        return if (i > 0 && symbol.length - i <= 4 && symbol.substring(i + 1).all { it.isLetter() }) symbol.substring(i).uppercase() else ""
    }

    // Zusatz der Hauptboerse zum Land, null wenn unbekannt. Leeres Land heisst Deutschland (wie in den Einstellungen).
    fun heimatZusatz(land: String): String? = HAUPTBOERSE[land.trim().uppercase().ifEmpty { "DE" }]

    // Ordnet die Plaetze einer Kennung: erst die Heimatboerse des Nutzers, dann die Hauptboerse des ISIN-Landes,
    // dann US-Notierungen, dann der Rest in der Reihenfolge der Quelle.
    fun plaetzeOrdnen(treffer: List<MarketApi.SearchHit>, kennung: String, land: String): List<MarketApi.SearchHit> {
        val heimat = heimatZusatz(land)
        val haupt = if (kennung.length >= 2) HAUPTBOERSE[kennung.substring(0, 2)] else null
        fun rang(h: MarketApi.SearchHit): Int {
            val z = zusatz(h.symbol)
            return when {
                // Fonds mit der ISIN als Kuerzel (Stuttgart) ganz hinten
                h.symbol.startsWith(kennung) && kennung.isNotEmpty() -> 5
                heimat != null && z == heimat -> 0
                haupt != null && z == haupt -> 1
                z.isEmpty() -> 2
                z == ".DE" || z == ".L" || z == ".PA" || z == ".SW" -> 3
                else -> 4
            }
        }
        return treffer.sortedBy { rang(it) }
    }

    // ------------------------------------------------------------ Abrufe

    // Nummeriert Abrufe, damit eine verspaetete Antwort die neuere nicht ueberschreibt: jeder Abruf holt sich beim Start
    // eine Nummer und schreibt sein Ergebnis nur, wenn sie noch die juengste ist.
    class Abruffolge {
        private var zaehler = 0L
        fun neu(): Long { zaehler += 1; return zaehler }
        fun aktuell(nummer: Long): Boolean = zaehler == nummer
    }
}
