package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

// Pressemitteilung zu den Zahlen (0.78): das letzte 8-K mit Item 2.02 und dessen Anhang Exhibit 99.1. Gelesen werden
// die Kennzahlen aus den Tabellen der Mitteilung (Umsatz, Gewinn je Aktie und weitere) und der Fliesstext; ohne
// Zusammenfassung. Der Vergleich zum Vorquartal gilt nur fuer Tabellenzeilen, die in beiden Mitteilungen erkannt wurden.
object Zahlenmitteilung {

    // Ein 8-K mit Item 2.02
    data class Einreichung(val acc: String, val tag: String, val datei: String)

    enum class Art { UMSATZ, BRUTTO, BETRIEB, GEWINN, EPS, CASH }

    // wert: Zelle wie in der Mitteilung (negativ in Klammern), zahl: dazu der Wert fuer den Vergleich
    data class Kennzahl(val art: Art, val label: String, val wert: String, val zahl: Double)

    // Zeile der Tabelle ohne Zahl, zum Beispiel aus einem Ausblick
    data class Ausblick(val label: String, val werte: String)

    data class Mitteilung(val kennzahlen: List<Kennzahl>, val ausblick: List<Ausblick>, val absaetze: List<String>)

    data class Vergleich(val kennzahl: Kennzahl, val vorher: Kennzahl?, val aenderung: Double?)

    private val json = Json { ignoreUnknownKeys = true }

    // Letzte Einreichungen mit Item 2.02 (neueste zuerst) aus der Liste submissions der SEC
    fun einreichungen(text: String, max: Int = 2): List<Einreichung> {
        val r = runCatching { ((json.parseToJsonElement(text) as? JsonObject)?.get("filings") as? JsonObject)?.get("recent") as? JsonObject }.getOrNull()
            ?: return emptyList()
        fun spalte(k: String) = (r[k] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.content.orEmpty() }
        val form = spalte("form")
        val items = spalte("items")
        val tag = spalte("filingDate")
        val nr = spalte("accessionNumber")
        val datei = spalte("primaryDocument")
        return form.indices.mapNotNull { i ->
            if (form[i] != "8-K") return@mapNotNull null
            if ("2.02" !in items.getOrNull(i).orEmpty().split(',').map { it.trim() }) return@mapNotNull null
            val n = nr.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Einreichung(n, tag.getOrNull(i).orEmpty(), datei.getOrNull(i).orEmpty())
        }.sortedByDescending { it.tag }.take(max)
    }

    private val EXHIBIT = Regex("(?i)^.*(ex|exhibit)[-_ ]?0?99[-_.]?0?1.*\\.html?$")
    private val EXHIBIT_LOSE = Regex("(?i)^.*(ex|exhibit)[-_ ]?0?99.*\\.html?$")

    // Datei des Exhibits 99.1 im Verzeichnis der Einreichung (index.json); sonst ein anderer Anhang 99
    fun exhibitDatei(indexJson: String): String? {
        val namen = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(indexJson).map { it.groupValues[1] }.toList()
        return namen.firstOrNull { EXHIBIT.matches(it) } ?: namen.firstOrNull { EXHIBIT_LOSE.matches(it) }
    }

    // ------------------------------------------------------------ HTML lesen

    private val ENTITAET = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    internal fun entitaeten(s: String): String = ENTITAET.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { zeichen(it) } ?: " "
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { zeichen(it) } ?: " "
            else -> when (e.lowercase()) {
                "nbsp", "ensp", "emsp", "thinsp" -> " "
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
                "rsquo", "lsquo" -> "'"; "ldquo", "rdquo" -> "\""
                "ndash" -> "-"; "mdash" -> "-"
                else -> " "
            }
        }
    }

    private fun zeichen(code: Int): String = when (code) {
        160, 8194, 8195, 8201, 8203 -> " "
        8216, 8217 -> "'"
        8220, 8221 -> "\""
        8211, 8212, 8722 -> "-"
        in 32..0xFFFF -> code.toChar().toString()
        else -> " "
    }

    private fun zelle(html: String): String =
        entitaeten(html.replace(Regex("(?is)<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim()

    private val TABELLE = Regex("(?is)<table\\b.*?</table>")
    private val ZEILE = Regex("(?is)<tr\\b.*?</tr>")
    private val ZELLE = Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>")

    // Alle Tabellen als Zeilen mit bereinigten, nichtleeren Zellen
    internal fun tabellen(html: String): List<List<List<String>>> =
        TABELLE.findAll(html).map { t ->
            ZEILE.findAll(t.value).map { z -> ZELLE.findAll(z.value).map { zelle(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList() }
                .filter { it.isNotEmpty() }.toList()
        }.toList()

    // Fliesstext ohne Tabellen, Skripte und Formatvorlagen; Absaetze mit mindestens 40 Zeichen
    internal fun absaetze(html: String): List<String> {
        val ohne = html
            .replace(Regex("(?is)<(script|style|head)\\b.*?</\\1>"), " ")
            .replace(TABELLE, "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|tr)>|<br\\s*/?>"), "\n")
        return entitaeten(ohne.replace(Regex("(?s)<[^>]*>"), " ")).lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 40 }
    }

    // ------------------------------------------------------------ Kennzahlen

    private val ZAHL = Regex("^\\(?-?[0-9][0-9,]*(\\.[0-9]+)?\\)?%?$")

    // Zelle als Zahl: Dollarzeichen und Leerraum weg, Klammern oder Minus ergeben negativ; null, wenn keine Zahl
    internal fun zahl(zelle: String): Pair<String, Double>? {
        val s = zelle.replace("$", "").replace(" ", "")
        if (!ZAHL.matches(s)) return null
        val negativ = s.startsWith("(") || s.startsWith("-")
        val wert = s.trim('(', ')', '-', '%').replace(",", "").toDoubleOrNull() ?: return null
        return s to (if (negativ) -wert else wert)
    }

    private fun label(s: String): String =
        s.lowercase().replace(Regex("\\(\\d+\\)|\\*+"), "").replace("$", "").trim().trimEnd(':', ',').trim().replace(Regex("\\s+"), " ")

    private val UMSATZ = Regex("^(total )?(net )?(operating )?(sales|revenues?)(, net)?$")
    private val GEWINN = Regex("^net (income|earnings|loss|\\(loss\\) income|income \\(loss\\)|earnings \\(loss\\))( attributable to [a-z .,&'-]+)?$")
    private val BETRIEB = Regex("^(total )?(income from operations|operating income( \\(loss\\))?|operating profit|operating \\(loss\\) income)$")
    private val BRUTTO = Regex("^gross (profit|margin)$")
    private val CASH = Regex("^(net )?cash (provided by|generated by|used in|from|flows? from)( \\(used in\\))? operating activities( \\(.*\\))?$")
    private val AUSBLICK = Regex("guidance|outlook|expects?|forecast")

    private fun art(l: String, kontext: String, token: String): Art? = when {
        UMSATZ.matches(l) -> Art.UMSATZ
        GEWINN.matches(l) -> Art.GEWINN
        BETRIEB.matches(l) -> Art.BETRIEB
        BRUTTO.matches(l) -> Art.BRUTTO
        CASH.matches(l) -> Art.CASH
        // Gewinn je Aktie: Zeile mit Zusatz diluted und Dezimalstelle, nicht die Aktienzahl
        "." in token && "diluted" in l && "shares" !in l && ("per" in l || "eps" in l || "earnings" in l || "income" in l) -> Art.EPS
        "." in token && l == "diluted" && "per share" in kontext && "shares used" !in kontext && "weighted" !in kontext && "computing" !in kontext -> Art.EPS
        else -> null
    }

    // Erste erkannte Zeile je Art in der Reihenfolge der Mitteilung; Ausblickzeilen mit Text in den Zellen
    internal fun kennzahlen(tabellen: List<List<List<String>>>): Pair<List<Kennzahl>, List<Ausblick>> {
        val gefunden = LinkedHashMap<Art, Kennzahl>()
        val ausblick = ArrayList<Ausblick>()
        for (t in tabellen) {
            var kontext = ""
            for (z in t) {
                val l = label(z[0])
                val werte = z.drop(1).mapNotNull { zahl(it) }
                if (werte.isEmpty()) {
                    if (z.size == 1) kontext = l
                    if (z.size >= 2 && AUSBLICK.containsMatchIn(l) && ausblick.size < 6) ausblick += Ausblick(z[0], z.drop(1).joinToString(" · "))
                    continue
                }
                if (!l.any { it.isLetter() } || l.length > 90) continue
                val a = art(l, kontext, werte[0].first) ?: continue
                if (a !in gefunden) gefunden[a] = Kennzahl(a, z[0].trimEnd(':').trim(), werte[0].first, werte[0].second)
            }
        }
        return gefunden.values.toList() to ausblick
    }

    private const val MAX_ZEICHEN = 4000
    private val SCHLUSS = Regex("(?i)^(forward[- ]looking statements?|safe harbor|cautionary (note|statement))")

    fun lesen(html: String): Mitteilung {
        val (kz, aus) = kennzahlen(tabellen(html))
        val text = ArrayList<String>()
        var summe = 0
        for (a in absaetze(html)) {
            if (SCHLUSS.containsMatchIn(a)) break
            if (text.isNotEmpty() && summe + a.length > MAX_ZEICHEN) break
            text += a
            summe += a.length
        }
        return Mitteilung(kz, aus, text)
    }

    // Aenderung zum Vorquartal in Prozent je erkannter Zeile; ohne Gegenstueck oder bei Vorwert 0 und unplausiblen
    // Spruengen (andere Einheit) null
    fun vergleichen(jetzt: List<Kennzahl>, vorher: List<Kennzahl>?): List<Vergleich> = jetzt.map { k ->
        val v = vorher?.firstOrNull { it.art == k.art }
        val aenderung = v?.takeIf { it.zahl != 0.0 }?.let { (k.zahl - it.zahl) / abs(it.zahl) * 100 }?.takeIf { abs(it) <= 1000.0 }
        Vergleich(k, v, aenderung)
    }
}
