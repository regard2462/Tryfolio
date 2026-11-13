package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// Ersatz fuer Aktien ohne US-Optionen [0.76]: Umsatz der Aktienoptionen an der Euronext (Amsterdam, Bruessel, Lissabon,
// Mailand, Oslo, Paris), frei abrufbar auf live.euronext.com. Die Klasse der Aktienoptionen kommt ueber die ISIN,
// der Tagesumsatz je Call und Put aus den Preistabellen der Faelligkeiten. Hier wird nur gelesen und gerechnet.
// Der Umsatz steht je Fall nur fuer den laufenden Handelstag; die Spalte der offenen Kontrakte ist bei Puts
// fehlerhaft (sie zeigt den Abrechnungskurs) und wird nicht verwendet.
object EuronextOptionen {

    data class Klasse(val symbol: String, val boerse: String)

    // Mindestumsatz (Calls plus Puts), ab dem die Verteilung gezeigt wird; darunter ist sie Zufall.
    const val MINDESTUMSATZ = 10L

    // Faelligkeiten, die abgefragt werden: der laufende Monat und die fuenf folgenden
    const val FAELLIGKEITEN = 6

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val LINK = Regex("/product/stock-options/([A-Z0-9]+)-([A-Z]+)")
    private val SONDER = Regex("weekly|mini|european|cash|daily", RegexOption.IGNORE_CASE)

    // Suchanfrage nach der ISIN
    fun suchUrl(isin: String): String = "https://live.euronext.com/en/instrumentSearch/searchJSON?q=" + isin.filter { it.isLetterOrDigit() }

    // Klasse der Standard-Aktienoptionen aus der Antwort der Suche; null, wenn die Euronext keine hat oder die
    // Antwort unlesbar ist. Wochen-, Mini-, europaeische und Cash-Serien zaehlen nicht, solange es eine normale gibt.
    fun klasse(antwort: String): Klasse? {
        val liste = runCatching { json.parseToJsonElement(antwort).jsonArray }.getOrNull() ?: return null
        val treffer = liste.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val link = o["link"]?.runCatching { jsonPrimitive.contentOrNull }?.getOrNull().orEmpty()
            val name = o["name"]?.runCatching { jsonPrimitive.contentOrNull }?.getOrNull().orEmpty()
            val m = LINK.find(link) ?: return@mapNotNull null
            Klasse(m.groupValues[1], m.groupValues[2]) to name
        }
        val normal = treffer.firstOrNull { !SONDER.containsMatchIn(it.second) && it.first.symbol.firstOrNull()?.isLetter() == true }
        return (normal ?: treffer.firstOrNull())?.first
    }

    // Adresse der Preistabellen und Formularfelder: je Faelligkeit ein Feld md[] im Format 01-MM-JJJJ.
    fun tabellenUrl(k: Klasse): String = "https://live.euronext.com/en/ajax/submitOptionsForm/stock-options/${k.symbol}/${k.boerse}"

    fun formular(jahr: Int, monat: Int, anzahl: Int = FAELLIGKEITEN): List<Pair<String, String>> {
        val felder = ArrayList<Pair<String, String>>()
        var j = jahr
        var m = monat
        repeat(anzahl) {
            felder += "md[]" to (if (m < 10) "01-0$m-$j" else "01-$m-$j")
            m++
            if (m > 12) { m = 1; j++ }
        }
        felder += "ps" to "extended"
        return felder
    }

    private val TABELLE = Regex("<table[^>]*class=\"(calls|puts)_table[^>]*>([\\s\\S]*?)</table>")
    private val ZEILE = Regex("<tr[^>]*>([\\s\\S]*?)</tr>")
    private val KOPF = Regex("<th[^>]*>([\\s\\S]*?)</th>")
    private val ZELLE = Regex("<td[^>]*>([\\s\\S]*?)</td>")
    private val TAG = Regex("<[^>]*>")

    private fun text(s: String): String = s.replace(TAG, "").replace("&nbsp;", " ").trim()

    // Umsatz des Tages (Spalte Day Vol) aller Calls und aller Puts ueber alle gelieferten Faelligkeiten als
    // Paar (Calls, Puts); null, wenn die Antwort keine Tabelle dieser Art enthaelt.
    fun summen(html: String): Pair<Long, Long>? {
        var calls = 0L
        var puts = 0L
        var gefunden = false
        for (t in TABELLE.findAll(html)) {
            val art = t.groupValues[1]
            val koerper = t.groupValues[2]
            val spalte = KOPF.findAll(koerper).map { text(it.groupValues[1]) }.toList().indexOf("Day Vol")
            if (spalte < 0) continue
            gefunden = true
            var summe = 0L
            for (z in ZEILE.findAll(koerper)) {
                val zellen = ZELLE.findAll(z.groupValues[1]).map { text(it.groupValues[1]) }.toList()
                if (spalte >= zellen.size) continue
                summe += (zellen[spalte].replace(",", "").toDoubleOrNull() ?: 0.0).toLong()
            }
            if (art == "calls") calls += summe else puts += summe
        }
        return if (gefunden) calls to puts else null
    }
}
