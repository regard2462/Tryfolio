package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Boersenwerte von den Webseiten kleinerer Boersen (Globus-Laenderblatt): ein Abruf je Boerse und Tag, der App-Nutzer ruft fuer
// sich selbst ab (persoenliche, nicht kommerzielle Nutzung), die Quelle steht im Laenderblatt. Je Boerse ein Leser fuer die echte
// Antwort; die Zuordnung zu den Firmen des Buendels laeuft ueber den Namen (namenGleich).
object Boersenfeeds {
    // wert in der Waehrung der Boerse (waehrung ISO-Code), land = Kuerzel alpha-2
    data class Notierung(val name: String, val symbol: String, val wert: Double, val waehrung: String, val land: String, val branche: String = "")

    // Eine Boerse mit Abruf: id, Name fuer die Quellenzeile, Laender, Adresse, ob POST, Waehrung
    class Quelle(val id: String, val name: String, val laender: Set<String>, val adresse: String, val post: Boolean, val html: Boolean, val mehrstufig: Boolean = false)

    val COLOMBO = Quelle("cse-lk", "Colombo Stock Exchange", setOf("LK"), "https://www.cse.lk/api/tradeSummary", true, false)
    val KARATSCHI = Quelle("psx", "Pakistan Stock Exchange", setOf("PK"), "https://dps.psx.com.pk/screener", false, true)
    val CASABLANCA = Quelle("casa", "Bourse de Casablanca", setOf("MA"), "https://www.casablanca-bourse.com/en/market-data/capitalisation", false, true)
    val BRVM = Quelle("brvm", "BRVM", setOf("CI", "SN", "BF", "ML", "NE", "TG", "BJ", "GW"), "https://www.brvm.org/en/capitalisations/0", false, true)
    val UGANDA = Quelle("use", "Uganda Securities Exchange", setOf("UG"), "https://www.use.or.ug/api/delayed-data", false, false)
    val ALLE = listOf(COLOMBO, KARATSCHI, CASABLANCA, BRVM, UGANDA)

    // Dhaka und Chittagong: je Firmenseite ein Abruf, Betrag in Millionen Taka
    const val DHAKA_NAME = "Dhaka Stock Exchange"
    const val CHITTAGONG_NAME = "Chittagong Stock Exchange"
    fun dhakaAdresse(kuerzel: String) = "https://www.dse.com.bd/company/" + kuerzel
    fun chittagongAdresse(kuerzel: String) = "https://www.cse.com.bd/company/companydetails/" + kuerzel
    const val WAEHRUNG_BD = "BDT"

    fun quelleFuer(a2: String): Quelle? = ALLE.firstOrNull { a2 in it.laender }

    // Alle Quellen eines Landes, auch die mit mehreren Abrufen (BoersenfeedsMehr); Vietnam hat drei Boersen
    fun quellenFuer(a2: String): List<Quelle> = (ALLE + BoersenfeedsMehr.ALLE).filter { a2 in it.laender }

    // Kandidat und Notierung gleich: Kennung des Kandidaten gleich dem Symbol der Notierung ("ZSE:HT"), sonst gleicher Name
    fun passt(kennung: String, kandidatName: String, n: Notierung): Boolean =
        (kennung.isNotBlank() && kennung == n.symbol) || namenGleich(kandidatName, n.name)

    private val json = Json { ignoreUnknownKeys = true }

    private fun zahl(s: String): Double? = s.trim().replace(Regex("[\\s\\u00a0,]"), "").toDoubleOrNull()

    // Colombo: {"reqTradeSummery":[{"name":"ABANS ELECTRICALS PLC","symbol":"ABAN.N0000","price":1011.25,"marketCap":5.1680538E9,...}]}.
    // Nur Stammaktien (.N0000), die Endung .X0000 steht fuer stimmrechtslose Aktien.
    fun colombo(text: String): List<Notierung> = runCatching {
        json.parseToJsonElement(text).jsonObject["reqTradeSummery"]!!.jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val sym = o["symbol"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val v = o["marketCap"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (!sym.endsWith(".N0000") || v <= 0) return@mapNotNull null
            Notierung(o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null, sym, v, "LKR", "LK")
        }
    }.getOrDefault(emptyList())

    // Karatschi: Tabelle #screenerTable, je Zeile <a class="tbl__symbol" ... data-title="Firmenname"> und danach die Zelle des
    // Boersenwerts mit data-order in Rupien (4. Zelle der Zeile). Bezugsrechte "(Right)" fallen weg.
    fun karatschi(html: String): List<Notierung> {
        val aus = ArrayList<Notierung>()
        for (zeile in html.substringAfter("<tbody", "").split("<tr>").drop(1)) {
            val m = Regex("""class="tbl__symbol"[^>]*data-title="([^"]*)"[^>]*><strong>([^<]*)</strong>""").find(zeile) ?: continue
            val name = m.groupValues[1]
            if (name.contains("(Right)", ignoreCase = true)) continue
            val zellen = Regex("""<td[^>]*data-order="([^"]*)"""").findAll(zeile).map { it.groupValues[1] }.toList()
            // Zellen mit data-order: Symbol, Boersenwert, Kurs, ... (Sektor und Handelsplatz haben keins)
            val v = zellen.getOrNull(1)?.toDoubleOrNull() ?: continue
            if (v > 0) aus += Notierung(name, m.groupValues[2], v, "PKR", "PK")
        }
        return aus
    }

    private val CASA_BRANCHEN = listOf(
        "BANKEN" to "bank", "VERSICHERUNG" to "insur", "BERGBAU" to "min", "TELEKOM" to "telecom", "BAU" to "build|construction|cement",
        "OEL_GAS" to "oil|gas|energy|distribut", "IMMOBILIEN" to "real estate|property", "NAHRUNG" to "food|beverage|agri",
        "PHARMA" to "pharma|health", "SOFTWARE" to "software|information|technolog", "VERKEHR" to "transport|logist|airline",
    )

    private fun brancheAus(text: String): String {
        val t = text.lowercase()
        return CASA_BRANCHEN.firstOrNull { (_, rx) -> Regex(rx).containsMatchIn(t) }?.first.orEmpty()
    }

    // Casablanca: im HTML ein <script type="application/json" data-drupal-selector="drupal-settings-json"> mit
    // boursenova.capitalisation.palmares[{label:{fr},sector:{en},cap}] (Betrag in Dirham).
    fun casablanca(html: String): List<Notierung> = runCatching {
        val s = html.substringAfter("data-drupal-selector=\"drupal-settings-json\">").substringBefore("</script>")
        json.parseToJsonElement(s).jsonObject["boursenova"]!!.jsonObject["capitalisation"]!!.jsonObject["palmares"]!!.jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = o["label"]?.jsonObject?.get("fr")?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            val v = o["cap"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            Notierung(name, name, v, "MAD", "MA", brancheAus(o["sector"]?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull.orEmpty()))
        }
    }.getOrDefault(emptyList())

    // BRVM: Tabelle mit Symbol, Name, Aktienzahl, Kurs, Floating Cap, "Global capitalization" (FCFA, Leerzeichen als Tausendertrenner).
    // Das Land folgt aus dem letzten Buchstaben des Symbols (C Elfenbeinkueste, B Benin, F Burkina Faso, M Mali, N Niger, S Senegal, T Togo).
    private val BRVM_LAND = mapOf('C' to "CI", 'B' to "BJ", 'F' to "BF", 'M' to "ML", 'N' to "NE", 'S' to "SN", 'T' to "TG")

    fun brvm(html: String): List<Notierung> {
        val aus = ArrayList<Notierung>()
        for (zeile in Regex("<tr><td>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html)) {
            val z = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll("<td>" + zeile.groupValues[1]).map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }.toList()
            if (z.size < 7) continue
            val land = BRVM_LAND[z[0].lastOrNull() ?: continue] ?: continue
            val v = zahl(z[5]) ?: continue
            if (v > 0) aus += Notierung(z[1].replace("&#039;", "'").replace("&amp;", "&"), z[0], v, "XOF", land)
        }
        return aus
    }

    // Uganda: [{"type":"EQUITY","stock":"AIRTEL UGANDA","price":188,"market_cap":7520000000000,...}], Betrag in Schilling.
    fun uganda(text: String): List<Notierung> = runCatching {
        json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if (o["type"]?.jsonPrimitive?.contentOrNull != "EQUITY") return@mapNotNull null
            val name = o["stock"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val v = o["market_cap"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (v > 0) Notierung(name, name, v, "UGX", "UG") else null
        }
    }.getOrDefault(emptyList())

    // Dhaka: auf der Firmenseite "Market cap" und im nachfolgenden Feld "BDT 3,851.487 mn"; Ergebnis in Taka.
    fun dhaka(html: String): Double? =
        Regex("""Market cap</span></div><div[^>]*>\s*BDT\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*mn""").find(html)?.groupValues?.get(1)?.let { zahl(it) }?.times(1e6)?.takeIf { it > 0 }

    // Chittagong: Tabellenzeile "Market Capital in BDT (mn)" und in der naechsten Zelle der Betrag; Ergebnis in Taka.
    fun chittagong(html: String): Double? =
        Regex("""Market Capital in BDT \(mn\)\s*</td>\s*<td[^>]*>\s*([0-9][0-9,]*(?:\.[0-9]+)?)""").find(html)?.groupValues?.get(1)?.let { zahl(it) }?.times(1e6)?.takeIf { it > 0 }

    // Name der Firma fuer den Vergleich: ohne Akzente, Gross/Klein, Rechtsformen und Allerweltswoerter
    private val FUELL = setOf("plc", "limited", "ltd", "pcl", "company", "co", "the", "sa", "s", "a", "inc", "corporation", "corp", "group", "holdings", "holding", "de", "du", "des", "of", "and", "&", "cote", "d'ivoire", "ci", "senegal", "sn", "benin", "bf", "mali", "niger", "togo")

    // Erste Woerter, die allein keine Firma bezeichnen
    private val GENERISCH = setOf("bank", "banque", "national", "international", "united", "first", "royal", "general", "central", "new", "south", "north", "east", "west", "african", "africa", "ceylon", "lanka", "commercial")

    fun stamm(name: String): List<String> {
        val n = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        return n.replace(Regex("[^a-z0-9& ]+"), " ").split(' ').filter { it.isNotBlank() && it !in FUELL }
    }

    // Gleiche Firma: gleiche Woerter oder das erste (mindestens vierbuchstabige) Wort gleich und das zweite, falls beide eins haben,
    // ebenfalls gleich. "Dialog" passt auf "Dialog Axiata", "Commercial Bank of Ceylon" auf "Commercial Bank of Ceylon PLC".
    fun namenGleich(a: String, b: String): Boolean {
        val x = stamm(a); val y = stamm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        if (x[0] != y[0] || x[0].length < 4) return false
        if (x.size == 1 || y.size == 1) return x[0] !in GENERISCH
        return x[1] == y[1]
    }

    // Anzeigename: Namen in Grossbuchstaben bekommen Anfangsbuchstaben gross, einzelne Kuerzel bis vier Buchstaben (ABB, NLB, INA) bleiben
    fun schoen(name: String): String =
        if (name == name.uppercase() && name.any { it.isLetter() } && !(name.length <= 4 && ' ' !in name)) name.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } else name

    // Zwischenspeicher als Text: eine Zeile je Notierung, Felder durch Tabulator
    fun speichern(l: List<Notierung>): String = l.joinToString("\n") { listOf(it.name, it.symbol, it.wert.toLong().toString(), it.waehrung, it.land, it.branche).joinToString("\t") }

    fun laden(text: String): List<Notierung> = text.lines().mapNotNull { z ->
        val f = z.split('\t'); if (f.size < 5) return@mapNotNull null
        Notierung(f[0], f[1], f[2].toDoubleOrNull() ?: return@mapNotNull null, f[3], f[4], f.getOrElse(5) { "" })
    }
}
