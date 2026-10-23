package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.market.MarketApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Kleine Helfer fuer die Antworten der Broker: Zahlen kommen dort oft als Text.
internal object BrokerJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun lesen(text: String?): JsonElement? = text?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

    fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    fun JsonElement?.arr(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

    fun JsonObject?.text(k: String): String? =
        (this?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotEmpty() && it != "null" }

    fun JsonObject?.zahl(k: String): Double? = text(k)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun JsonObject?.wahr(k: String): Boolean = text(k)?.equals("true", ignoreCase = true) == true

    // ISO-8601-Zeit (2026-10-19T14:30:00.123456Z oder mit Versatz) in Millisekunden; null wenn unlesbar.
    fun zeit(iso: String?): Long? = iso?.let { s ->
        runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(s.take(10)).toEpochDay() * 86_400_000L }.getOrNull()
    }

    // Fehlermeldung aus message oder error einer Antwort als BrokerFehler.
    fun fehler(a: MarketApi.Antwort): BrokerFehler {
        val o = lesen(a.text).obj()
        val text = o.text("message") ?: o.text("error_description") ?: o.text("error") ?: o.text("detail") ?: a.text?.take(200)
        return when {
            a.code == de.troetenrepublik.app.plattform.BrokerNetz.UNSICHER -> BrokerFehler("UNSICHER")
            a.code == 0 -> BrokerFehler("NETZ")
            a.code == 401 || a.code == 403 -> BrokerFehler("SCHLUESSEL", text)
            else -> BrokerFehler("ABGELEHNT", text ?: "HTTP ${a.code}")
        }
    }
}
