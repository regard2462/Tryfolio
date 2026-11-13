package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// [Globus Depot 0.70] Laenderanteile von ETFs nach dem Index, den sie abbilden (assets/globus/etf-laender.json, MSCI-
// Factsheets und Indexanbieter, gerundet). Yahoo nennt nur die zehn groessten Positionen ohne Land, daher der Indexweg.
object EtfLaender {
    // w: Index-Schluessel zu Land (alpha-2) und Prozent; welt: Gewichte des Weltindex (MSCI ACWI) fuer den Vergleich
    class Daten(val w: Map<String, Map<String, Double>>, val welt: Map<String, Double>) {
        // Anteile (Summe 1) des Index; null ohne Eintrag
        fun verteilung(schluessel: String?): Map<String, Double>? =
            w[schluessel]?.takeIf { it.isNotEmpty() }?.let { m -> val s = m.values.sum(); m.mapValues { it.value / s } }
        val weltAnteile: Map<String, Double> get() = welt.mapValues { it.value / welt.values.sum().coerceAtLeast(1e-9) }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(text: String): Daten = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        fun karte(e: kotlinx.serialization.json.JsonElement?) =
            e?.jsonObject?.mapValues { (_, v) -> v.jsonPrimitive.doubleOrNull ?: 0.0 } ?: emptyMap()
        Daten(o["w"]!!.jsonObject.mapValues { (_, v) -> karte(v) }, karte(o["welt"]))
    }.getOrDefault(Daten(emptyMap(), emptyMap()))

    private val OHNE_LAND = setOf("STAATSANLEIHEN", "UNTERNEHMENSANLEIHEN", "GELDMARKT", "ROHSTOFFE")
    private val BREIT = setOf("SEKTORUEBERGREIFEND", "SMALL_CAPS", "MID_CAPS", "")
    private val OHNE_EIN_LAND = Regex("\\bex[ -]?(us|usa|u\\.s\\.|japan|china|uk|europe|emu|eurozone)\\b|excluding|ohne usa")

    // Index-Schluessel eines ETFs: Katalogindex, sonst nach Name, Region und Branche; null, wenn kein Land ableitbar ist
    // (Branchenfonds weltweit, Gold, Geldmarkt, Anleihen).
    fun schluessel(name: String, index: String?, region: String?, branche: String?, daten: Daten): String? {
        if (index != null && index in daten.w) return index
        val n = name.lowercase()
        val b = branche.orEmpty()
        // Fonds "ex US", "ex Japan" und aehnlich bilden einen Index ohne dieses Land ab: kein passender Schluessel
        if (OHNE_EIN_LAND.containsMatchIn(n)) return null
        if (b in OHNE_LAND) return when {
            region == "NORDAMERIKA" && n.contains("treasury") -> "SP500"
            region == "DEUTSCHLAND" -> "DAX"
            else -> null
        }
        return when {
            "pacific ex japan" in n -> "PACXJP"
            "emerging" in n || Regex("\\bem\\b").containsMatchIn(n) -> if (b in BREIT) "MSCIEM" else null
            "japan" in n -> "JP"
            "china" in n -> "CN"
            "india" in n -> "IN"
            "swiss" in n -> "CH"
            ("world" in n || "acwi" in n || "all country" in n || "all-world" in n) && b in BREIT ->
                if ("acwi" in n || "all-world" in n || "all country" in n) "MSCIACWI" else "MSCIWORLD"
            // Kanada und Mexiko liegen in der Region Nordamerika, bilden aber nicht den US-Index ab
            region == "NORDAMERIKA" -> if (Regex("canad|mexic").containsMatchIn(n)) null else "SP500"
            region == "DEUTSCHLAND" -> "DAX"
            region == "EUROPA" && b in BREIT -> "MSCIEUROPE"
            region == "SCHWELLENLAENDER" && b in BREIT -> "MSCIEM"
            else -> null
        }
    }
}
