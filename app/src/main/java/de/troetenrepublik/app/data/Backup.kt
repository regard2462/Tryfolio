package de.troetenrepublik.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Export und Import des Depots als JSON. Dank der Formatnummer koennen spaetere Fassungen aeltere Dateien anheben.
// Unbekannte Felder werden ignoriert, fehlende mit Vorgaben belegt.
object Backup {

    const val FORMAT_VERSION = 2

    // Einstellungen, die mit dem Depot umziehen, ohne PIN und Geraetebezug.
    @Serializable
    data class PortableSettings(
        val ownerName: String = "",
        val iban: String = "",
        val interestBps: Int = 200,
        val interestAuto: Boolean = true,
        val spreadStockBps: Int = 20,
        val spreadDerivativeBps: Int = 50,
        val allowance: Double = 0.0,
        val taxId: String = "",
        val metric: String = Metric.TODAY_PCT.name,
        val language: String = Lang.DE.name,
        // Land der Einrichtung; leer in aelteren Dateien.
        val land: String = "",
    )

    @Serializable
    data class Payload(
        val app: String = "troetenrepublik",
        val format: Int = FORMAT_VERSION,
        val exportedAt: Long = 0L,
        val account: Account,
        val settings: PortableSettings = PortableSettings(),
        val companies: Map<String, CompanyInfo> = emptyMap(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        coerceInputValues = true
    }

    fun export(account: Account, settings: Settings, now: Long = System.currentTimeMillis(), companies: Map<String, CompanyInfo> = emptyMap()): String =
        json.encodeToString(
            Payload.serializer(),
            Payload(
                exportedAt = now,
                account = account,
                settings = PortableSettings(
                    ownerName = settings.ownerName,
                    interestBps = settings.interestBps,
                    interestAuto = settings.interestAuto,
                    spreadStockBps = settings.spreadStockBps,
                    spreadDerivativeBps = settings.spreadDerivativeBps,
                    allowance = settings.allowance,
                    taxId = settings.taxId,
                    metric = settings.metric.name,
                    language = settings.language.name,
                    land = settings.land,
                ),
                companies = companies,
            ),
        )

    sealed class Outcome {
        data class Ok(val payload: Payload) : Outcome()
        // Fehlercode: NOT_READABLE, FOREIGN, NEWER (extra = Format), INVALID_CASH, INVALID_POSITION.
        data class Error(val code: String, val extra: Int = 0) : Outcome()
    }

    // Prueft auch, ob die Datei ein Depot dieser App enthaelt.
    fun parse(text: String): Outcome {
        val payload = runCatching { json.decodeFromString(Payload.serializer(), text) }.getOrNull()
            ?: return Outcome.Error("NOT_READABLE")
        if (payload.app != "troetenrepublik") return Outcome.Error("FOREIGN")
        if (payload.format > FORMAT_VERSION) return Outcome.Error("NEWER", payload.format)
        val acc = payload.account
        if (!acc.cash.isFinite() || acc.cash < 0) return Outcome.Error("INVALID_CASH")
        if (acc.positions.any { !it.qty.isFinite() || it.qty <= 0 || !it.avgPrice.isFinite() || it.avgPrice < 0 })
            return Outcome.Error("INVALID_POSITION")
        return Outcome.Ok(upgrade(payload))
    }

    // Format 1 braucht keine Umrechnung, fehlende Felder haben Vorgaben.
    private fun upgrade(p: Payload): Payload = when (p.format) {
        FORMAT_VERSION -> p
        else -> p.copy(format = FORMAT_VERSION)
    }

    fun fileName(now: Long = System.currentTimeMillis()): String {
        val d = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
        return "tryfolio-depot-%04d-%02d-%02d.json".format(d.year, d.monthValue, d.dayOfMonth)
    }
}
