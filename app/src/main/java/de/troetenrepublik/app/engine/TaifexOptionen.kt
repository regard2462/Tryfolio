package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Ersatz fuer Aktien aus Taiwan ohne US-Optionen [0.76]: offene Daten der TAIFEX (ohne Anmeldung). Die Liste der
// Aktienoptionen ordnet Aktiencode und Vertragskuerzel zu, der Tagesbericht der Optionen fuehrt je Kontrakt Umsatz
// und offene Kontrakte. Der Bericht ist gross (alle Optionen, 4 MB); die Aktienoptionen stehen am Anfang.
object TaifexOptionen {

    const val LISTE_URL = "https://openapi.taifex.com.tw/v1/SSOLists"
    const val BERICHT_URL = "https://openapi.taifex.com.tw/v1/DailyMarketReportOpt"

    // Die Aktienoptionen belegen die ersten 1,5 MB des Berichts, danach folgen die Indexoptionen
    const val BERICHT_MAX = 1_700_000

    private val SYMBOL = Regex("^([0-9]{4,6}[A-Z]?)\\.TWO?$")

    // Aktiencode der TAIFEX zum Yahoo-Kuerzel (2317.TW wird 2317); null fuer andere Kuerzel.
    fun code(yahooSymbol: String): String? = SYMBOL.matchEntire(yahooSymbol.uppercase())?.groupValues?.get(1)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Vertragskuerzel je Aktiencode aus der Liste der Aktienoptionen; leer bei unlesbarer Antwort.
    fun vertraege(liste: String): Map<String, String> {
        val a = runCatching { json.parseToJsonElement(liste) as JsonArray }.getOrNull() ?: return emptyMap()
        val aus = HashMap<String, String>()
        for (e in a) {
            val o = e as? JsonObject ?: continue
            val v = o["Contract"]?.runCatching { jsonPrimitive.contentOrNull }?.getOrNull() ?: continue
            val c = o["StockCode"]?.runCatching { jsonPrimitive.contentOrNull }?.getOrNull() ?: continue
            aus[c] = v
        }
        return aus
    }

    // Spalte CallPut: Kaufoption (Call) heisst auf Chinesisch 買權; die Nachtsitzung heisst 盤後
    private const val CALL = "買權"
    private const val NACHT = "盤後"
    private val ZEILE = Regex("\"Contract\":\"([A-Z0-9]+)\"[^}]*?\"CallPut\":\"([^\"]*)\"[^}]*?\"Volume\":\"([^\"]*)\"[^}]*?\"OpenInterest\":\"([^\"]*)\"[^}]*?\"TradingSession\":\"([^\"]*)\"")

    // Summen je Vertrag aus dem Bericht (auch abgeschnitten); nur die gewuenschten Vertraege. Offene Kontrakte
    // zaehlen nur im regulaeren Handel, damit die Nachtsitzung sie nicht doppelt bringt.
    fun lesen(bericht: String, vertraege: Set<String>): Map<String, OptionsZahlen> {
        val summen = HashMap<String, LongArray>()
        for (m in ZEILE.findAll(bericht)) {
            val v = m.groupValues[1]
            if (v !in vertraege) continue
            val s = summen.getOrPut(v) { LongArray(4) }
            val c = if (m.groupValues[2] == CALL) 0 else 1
            s[c] += m.groupValues[3].toLongOrNull() ?: 0L
            if (m.groupValues[5] != NACHT) s[2 + c] += m.groupValues[4].toLongOrNull() ?: 0L
        }
        return summen.mapValues { (_, s) -> OptionsZahlen(s[0], s[1], s[2], s[3]) }
    }
}
