package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

// Hintergrund zu Kryptowerten (0.78): Finanzierungssatz und offene Kontrakte der unbefristeten Kontrakte, Umlauf der
// Stablecoins und in DeFi gebundenes Kapital. Hier stehen Adressen und Leser, die Abrufe in market/KryptoAbrufe.kt.
// Die Bedingungen der Quellen lassen den Abruf fuer den eigenen, nicht kommerziellen Gebrauch zu: Die App laedt auf dem
// Geraet, legt nichts bei und speichert nichts dauerhaft.
object KryptoHintergrund {

    private const val TAG_MS = 86_400_000L
    private const val STUNDEN_JE_JAHR = 24 * 365

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.zahl(k: String): Double? = text(k)?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun objekt(json: String): JsonObject? = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull()

    // Kuerzel der Waehrung aus dem Symbol der App: „BTC-EUR“ wird BTC, „UNI7083-EUR“ wird UNI. Null bei anderem Aufbau.
    fun basis(symbol: String): String? =
        Regex("^([A-Z]{2,10})\\d*-[A-Z]{3,4}$").find(symbol.trim().uppercase())?.groupValues?.get(1)

    // ------------------------------------------------------------ Finanzierungssatz

    // Satz je acht Stunden und aufs Jahr gerechnet (beide in %), offene Kontrakte in Dollar, Tagesmittel des Satzes
    // aufs Jahr gerechnet (Zeit in ms, %), Name der Boerse.
    data class Finanzierung(val quelle: String, val satz8h: Double, val jahr: Double, val offenDollar: Double?, val verlauf: List<Pair<Long, Double>>)

    // Aus dem Satz je Stunde (als Anteil) der Satz je acht Stunden und je Jahr in %.
    fun satz8h(jeStunde: Double): Double = jeStunde * 8 * 100
    fun jahr(jeStunde: Double): Double = jeStunde * STUNDEN_JE_JAHR * 100

    // Tagesmittel der Stundensaetze, aufs Jahr gerechnet, fuer die letzten tage Tage bis zum juengsten Wert.
    fun tagesmittel(stunden: List<Pair<Long, Double>>, tage: Int = 42): List<Pair<Long, Double>> {
        if (stunden.isEmpty()) return emptyList()
        val ab = stunden.maxOf { it.first } / TAG_MS - tage + 1
        return stunden.filter { it.first / TAG_MS >= ab && it.second.isFinite() }.groupBy { it.first / TAG_MS }
            .map { (tag, werte) -> tag * TAG_MS to jahr(werte.map { it.second }.average()) }.sortedBy { it.first }
    }

    // Erste Boerse: unbefristete Kontrakte in Dollar, Bitcoin heisst dort XBT.
    const val KRAKEN = "Kraken Futures"
    const val KRAKEN_SEITE = "https://www.kraken.com/features/futures"
    private const val KRAKEN_API = "https://futures.kraken.com/derivatives/api"

    fun krakenKontrakt(basis: String): String = "PF_" + (if (basis == "BTC") "XBT" else basis) + "USD"
    fun krakenStandAdresse(basis: String) = "$KRAKEN_API/v3/tickers/${krakenKontrakt(basis)}"
    fun krakenVerlaufAdresse(basis: String) = "$KRAKEN_API/v4/historicalfundingrates?symbol=${krakenKontrakt(basis)}"

    // Satz je Stunde (Anteil) und offene Kontrakte in Dollar (Stueck mal Bewertungskurs).
    data class Stand(val jeStunde: Double, val offenDollar: Double?)

    fun krakenStand(json: String): Stand? {
        val t = objekt(json)?.get("ticker") as? JsonObject ?: return null
        if (t.text("tag") != "perpetual") return null
        val satz = t.zahl("relativeFundingRate") ?: return null
        val kurs = t.zahl("markPrice")?.takeIf { it > 0 }
        val offen = t.zahl("openInterest")?.takeIf { it >= 0 }
        return Stand(satz, if (kurs != null && offen != null) offen * kurs else null)
    }

    // Stundensaetze (Zeit in ms, Anteil je Stunde); die Boerse liefert ein Jahr, gebraucht werden die letzten Wochen.
    fun krakenVerlauf(json: String): List<Pair<Long, Double>> =
        (objekt(json)?.get("rates") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val zeit = o.text("timestamp")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
            val satz = o.zahl("relativeFundingRate") ?: return@mapNotNull null
            zeit to satz
        }

    // Zweite Boerse als Rueckfall: Bitcoin und Ether als Kontrakt in der Waehrung selbst, die uebrigen in USDC.
    const val DERIBIT = "Deribit"
    const val DERIBIT_SEITE = "https://www.deribit.com"
    private const val DERIBIT_API = "https://www.deribit.com/api/v2/public"

    fun deribitKontrakt(basis: String): String = if (basis == "BTC" || basis == "ETH") "$basis-PERPETUAL" else "${basis}_USDC-PERPETUAL"
    fun deribitStandAdresse(basis: String) = "$DERIBIT_API/ticker?instrument_name=${deribitKontrakt(basis)}"
    fun deribitVerlaufAdresse(basis: String, von: Long, bis: Long) =
        "$DERIBIT_API/get_funding_rate_history?instrument_name=${deribitKontrakt(basis)}&start_timestamp=$von&end_timestamp=$bis"

    // funding_8h ist der Satz der letzten acht Stunden. Offene Kontrakte: bei den Kontrakten in der Waehrung selbst
    // schon in Dollar, bei USDC-Kontrakten in Stueck.
    fun deribitStand(json: String, basis: String): Stand? {
        val r = objekt(json)?.get("result") as? JsonObject ?: return null
        val satz = r.zahl("funding_8h") ?: return null
        val offen = r.zahl("open_interest")?.takeIf { it >= 0 }
        val dollar = if (basis == "BTC" || basis == "ETH") offen else r.zahl("mark_price")?.takeIf { it > 0 }?.let { k -> offen?.times(k) }
        return Stand(satz / 8, dollar)
    }

    fun deribitVerlauf(json: String): List<Pair<Long, Double>> =
        (objekt(json)?.get("result") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val zeit = o.text("timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val satz = o.zahl("interest_1h") ?: return@mapNotNull null
            zeit to satz
        }

    fun finanzierung(quelle: String, stand: Stand, stunden: List<Pair<Long, Double>>): Finanzierung =
        Finanzierung(quelle, satz8h(stand.jeStunde), jahr(stand.jeStunde), stand.offenDollar, tagesmittel(stunden))

    // ------------------------------------------------------------ DefiLlama: Stablecoins und gebundenes Kapital

    const val LLAMA = "DefiLlama"
    const val LLAMA_SEITE = "https://defillama.com"
    const val STABLE_ADRESSE = "https://stablecoins.llama.fi/stablecoincharts/all"
    const val TVL_ADRESSE = "https://api.llama.fi/v2/historicalChainTvl"

    fun tvlAdresse(kette: String) = "$TVL_ADRESSE/$kette"

    // Kette eines Werts unter dem Namen, den die Quelle fuehrt. Nur Werte, die selbst eine Kette sind; Token auf fremden
    // Ketten (etwa Chainlink, Uniswap) haben keine.
    private val KETTEN = mapOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "SOL" to "Solana", "BNB" to "BSC", "TRX" to "Tron", "AVAX" to "Avalanche",
        "ADA" to "Cardano", "ATOM" to "CosmosHub", "ALGO" to "Algorand", "HBAR" to "Hedera", "ICP" to "ICP", "XTZ" to "Tezos",
        "VET" to "VeChain", "CRO" to "Cronos", "POL" to "Polygon", "XLM" to "Stellar", "LTC" to "Litecoin", "XRP" to "XRPL",
        "NEAR" to "Near", "SUI" to "Sui", "APT" to "Aptos", "ETC" to "EthereumClassic", "BCH" to "Bitcoincash", "FIL" to "Filecoin",
        "DOGE" to "Doge", "ARB" to "Arbitrum",
    )

    fun kette(basis: String?): String? = KETTEN[basis]

    private fun sekunden(e: JsonElement?): Long? = (e as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }

    // Umlauf aller Stablecoins in Dollar je Tag (Zeit in ms): Summe ueber alle Bindungen (Dollar, Euro und weitere).
    fun stableLesen(json: String, tage: Int = 365): List<Pair<Long, Double>> = runCatching {
        val liste = (Json.parseToJsonElement(json) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val zeit = sekunden(o["date"]) ?: return@mapNotNull null
            val summe = (o["totalCirculatingUSD"] as? JsonObject)?.values
                ?.sumOf { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { v -> v.isFinite() && v > 0 } ?: 0.0 } ?: return@mapNotNull null
            if (summe > 0) zeit * 1000 to summe else null
        }
        liste.sortedBy { it.first }.takeLast(tage)
    }.getOrDefault(emptyList())

    // In DeFi gebundenes Kapital in Dollar je Tag (Zeit in ms), gesamt oder fuer eine Kette.
    fun tvlLesen(json: String, tage: Int = 365): List<Pair<Long, Double>> = runCatching {
        val liste = (Json.parseToJsonElement(json) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val zeit = sekunden(o["date"]) ?: return@mapNotNull null
            val wert = o.zahl("tvl")?.takeIf { it > 0 } ?: return@mapNotNull null
            zeit * 1000 to wert
        }
        liste.sortedBy { it.first }.takeLast(tage)
    }.getOrDefault(emptyList())

    // Veraenderung in % gegen den Wert vor tage Tagen (der letzte Wert an oder vor diesem Tag). Null ohne Vergleichswert.
    fun veraenderung(reihe: List<Pair<Long, Double>>, tage: Int = 30): Double? {
        val letzter = reihe.lastOrNull() ?: return null
        val frueher = reihe.lastOrNull { it.first <= letzter.first - tage * TAG_MS }?.second?.takeIf { it > 0 } ?: return null
        return (letzter.second / frueher - 1) * 100
    }
}
