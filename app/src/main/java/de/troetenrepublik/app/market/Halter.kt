package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Groesste institutionelle Halter einer Aktie (seit 5.93) aus der quoteSummary (Module institutionOwnership,
// fundOwnership und majorHoldersBreakdown). Institute melden vierteljaehrig (Form 13F in den USA) bis zu 45 Tage
// nach Quartalsende, Fonds nach den Regeln ihres Landes; die Angaben sind daher aelter als der Kurs. Reines Lesen
// der Antwort, damit der Aufbau leicht zu testen ist.
object Halter {

    // Ein Halter: Anteil am Grundkapital in Prozent, Stueckzahl, Wert der Position, Meldedatum in ms und
    // Veraenderung der Stueckzahl gegenueber dem Vorquartal in Prozent (null ohne Angabe).
    data class Eintrag(
        val name: String, val anteil: Double, val stueck: Double, val wert: Double,
        val stichtag: Long, val aenderung: Double?,
    )

    // Anteil der Insider und Institutionen gesamt in Prozent und Zahl der meldenden Institute.
    data class Uebersicht(val insider: Double, val institutionen: Double, val anzahl: Int)

    // Institute und Fonds (5.96) in getrennten Listen, je auf zehn Namen begrenzt; bei Nicht-US-Aktien oft kurz.
    data class Daten(val uebersicht: Uebersicht?, val halter: List<Eintrag>, val fonds: List<Eintrag> = emptyList()) {
        val stand: Long get() = (halter + fonds).maxOfOrNull { it.stichtag } ?: 0L
    }

    // Liest die quoteSummary-Antwort; null ohne Ergebnis, ohne brauchbare Angaben oder bei kaputtem JSON.
    fun parse(body: String): Daten? {
        val r = runCatching { wurzel(body) }.getOrNull() ?: return null
        val halter = eintraege(r, "institutionOwnership")
        val fonds = eintraege(r, "fundOwnership")
        val mhb = r["majorHoldersBreakdown"] as? JsonObject
        val insider = raw(mhb, "insidersPercentHeld")?.times(100)
        val institutionen = raw(mhb, "institutionsPercentHeld")?.times(100)
        val uebersicht = if ((insider ?: 0.0) == 0.0 && (institutionen ?: 0.0) == 0.0) null
        else Uebersicht(insider ?: 0.0, institutionen ?: 0.0, raw(mhb, "institutionsCount")?.toInt() ?: 0)
        if (uebersicht == null && halter.isEmpty() && fonds.isEmpty()) return null
        return Daten(uebersicht, halter, fonds)
    }

    // Erstes Ergebnis der quoteSummary als Objekt; null bei kaputtem JSON oder ohne Ergebnis.
    private fun wurzel(body: String): JsonObject? {
        val ganz = Json.parseToJsonElement(body) as? JsonObject ?: return null
        val summary = ganz["quoteSummary"] as? JsonObject ?: return null
        val result = summary["result"] as? JsonArray ?: return null
        return result.firstOrNull() as? JsonObject
    }

    // Die Halter eines Moduls mit Namen und Bestand, groesste Position zuerst (bei Gleichstand der wertvolleren),
    // auf zehn begrenzt. Eintrag ohne Name oder ohne jede Angabe faellt weg; mehrfach gemeldete Namen zaehlen mit
    // der neuesten Meldung, bei Gleichstand mit mehr Stueck.
    private fun eintraege(r: JsonObject, modul: String): List<Eintrag> {
        val besitz = r[modul] as? JsonObject
        val feld = besitz?.get("ownershipList") as? JsonArray
        val alle = feld.orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = (o["organization"] as? JsonPrimitive)?.content?.takeIf { it != "null" }?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val anteil = raw(o, "pctHeld")?.times(100) ?: 0.0
            val stueck = raw(o, "position") ?: 0.0
            val wert = raw(o, "value") ?: 0.0
            if (anteil <= 0 && stueck <= 0 && wert <= 0) return@mapNotNull null
            Eintrag(
                name, anteil, stueck, wert,
                (raw(o, "reportDate") ?: 0.0).toLong() * 1000, raw(o, "pctChange")?.times(100),
            )
        }
        return alle
            .groupBy { it.name.lowercase() }
            .map { (_, meldungen) -> meldungen.maxWith(compareBy({ it.stichtag }, { it.stueck })) }
            .sortedWith(compareByDescending<Eintrag> { it.stueck }.thenByDescending { it.wert })
            .take(10)
    }

    // Eine Zahl der quoteSummary: als {"raw": wert} oder direkt als Primitive. Der Text "null" zaehlt nicht.
    private fun raw(o: JsonObject?, key: String): Double? = when (val v = o?.get(key)) {
        is JsonObject -> (v["raw"] as? JsonPrimitive)?.content?.toDoubleOrNull()
        is JsonPrimitive -> v.content.takeIf { it != "null" }?.toDoubleOrNull()
        else -> null
    }
}
