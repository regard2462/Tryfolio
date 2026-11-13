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
        // Steuer-ID: wird nicht mehr exportiert, aber aus aelteren Dateien weiter gelesen
        val taxId: String = "",
        val metric: String = Metric.TODAY_PCT.name,
        val language: String = Lang.DE.name,
        // Land der Einrichtung; leer in aelteren Dateien.
        val land: String = "",
        // Kategorien und Zielgewichte im Umschichten (JSON, 0.76); leer in aelteren Dateien.
        val kategorien: String = "",
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
                    metric = settings.metric.name,
                    language = settings.language.name,
                    land = settings.land,
                    kategorien = settings.kategorien,
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
        // Manche Editoren schreiben eine Byte-Order-Mark an den Anfang
        val payload = runCatching { json.decodeFromString(Payload.serializer(), text.trimStart(Char(0xFEFF))) }.getOrNull()
            ?: return Outcome.Error("NOT_READABLE")
        if (payload.app != "troetenrepublik") return Outcome.Error("FOREIGN")
        if (payload.format > FORMAT_VERSION) return Outcome.Error("NEWER", payload.format)
        val acc = payload.account
        // Rundungsreste knapp unter null (etwa -1e-12 nach einem Kauf des ganzen Guthabens) sind kein Fehler. Ein echter
        // Fehlbetrag entsteht nur, wenn der Ausgleich einer verkauften Option bei Verfall das Guthaben uebersteigt (0.77).
        val fehlbetrag = acc.transactions.any { it.verkaufteOption }
        if (!acc.cash.isFinite() || (acc.cash < -0.005 && !fehlbetrag)) return Outcome.Error("INVALID_CASH")
        // Verkaufte Optionen stehen mit negativer Stueckzahl im Depot, alles andere mit positiver
        if (acc.positions.any { !it.qty.isFinite() || (if (it.verkauft) it.qty >= 0 else it.qty <= 0) || !it.avgPrice.isFinite() || it.avgPrice < 0 })
            return Outcome.Error("INVALID_POSITION")
        return Outcome.Ok(upgrade(if (acc.cash < 0 && acc.cash >= -0.005) payload.copy(account = acc.copy(cash = 0.0)) else payload))
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
