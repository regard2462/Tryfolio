package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToLong

// Gamma je Kontrakt vom Nasdaq-Endpunkt option-chain/greeks (0.78), ein Abruf je Verfall. Die Antwort fuehrt Kontrakte
// um den Kurs (etwa 90 bis 110 Prozent); die Felder c* gehoeren zum Kontrakt der Zeile, auch bei Puts. Gegen die
// eigene Black-Scholes-Rechnung mit Mittelkurs-IV (AAPL, MSFT, Dezember 2026) weichen die Werte um weniger als ein
// Prozent ab. Die IV-Felder der Antwort sind 0 und bleiben ungenutzt.
object NasdaqGriechen {

    // verfall in Sekunden wie bei Yahoo, strike in Hundertstel
    data class Schluessel(val verfall: Long, val strike: Long, val call: Boolean)

    private val json = Json { ignoreUnknownKeys = true }
    private val KONTRAKT = Regex("--\\d{6}([cp])(\\d{8})$")

    private fun z2(n: Int) = n.toString().padStart(2, '0')

    // Kalendertag (UTC) eines Verfalls als JJJJ-MM-TT, ohne java.time
    fun tagText(verfallS: Long): String {
        val z = verfallS.floorDiv(86_400L) + 719_468L
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + (if (m <= 2) 1 else 0)).toInt()
        return "$y-${z2(m)}-${z2(d)}"
    }

    fun adresse(symbol: String, etf: Boolean, verfallS: Long): String =
        "https://api.nasdaq.com/api/quote/$symbol/option-chain/greeks?assetclass=${if (etf) "etf" else "stocks"}&date=${tagText(verfallS)}"

    // Null bei unlesbarer Antwort oder Fehlerstatus, sonst die Gammas (auch leer)
    fun lesen(text: String, verfallS: Long): Map<Schluessel, Double>? {
        val wurzel = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val daten = wurzel["data"] as? JsonObject ?: return null
        val zeilen = ((daten["table"] as? JsonObject)?.get("rows") as? JsonArray) ?: return emptyMap()
        val aus = HashMap<Schluessel, Double>()
        for (e in zeilen) {
            val z = e as? JsonObject ?: continue
            val url = (z["url"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: continue
            val t = KONTRAKT.find(url) ?: continue
            val strike = t.groupValues[2].toLongOrNull()?.div(10) ?: continue
            val gamma = (z["cGamma"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull() ?: continue
            if (gamma > 0 && gamma.isFinite()) aus[Schluessel(verfallS, strike, t.groupValues[1] == "c")] = gamma
        }
        return aus
    }

    fun schluessel(verfallS: Long, strike: Double, call: Boolean) = Schluessel(verfallS, (strike * 100).roundToLong(), call)
}
