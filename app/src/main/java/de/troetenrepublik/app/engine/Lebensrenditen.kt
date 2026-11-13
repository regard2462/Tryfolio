package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// Lebensrenditen der US-Stammaktien seit 1926 [0.78]: Verteilung ueber die ganze Boersenzeit jeder Aktie und Verteilung
// des geschaffenen Vermoegens je Unternehmen (gemessen gegen einmonatige US-Schatzwechsel). Die Datei
// indikatoren/lebensrenditen.json (tools/daten/lebensrenditen.py) traegt nur die Auszaehlung, keine Einzelwerte; alle
// Anteile rechnet dieses Objekt aus den Zahlen der Datei.
object Lebensrenditen {

    const val DATEI = "indikatoren/lebensrenditen.json"
    const val QUELLE_NAME = "Bessembinder: Compound returns and wealth creation of U.S. common stocks, 1926 to 2025 (2026)"
    const val QUELLE_ADRESSE = "https://wpcarey.asu.edu/department-finance/faculty-research/do-stocks-outperform-treasury-bills"

    // Lebensrendite von `von` (eingeschlossen) bis `bis` Prozent; bis = null: nach oben offen
    class Klasse(val von: Int, val bis: Int?, val anzahl: Int)

    class Datei(
        val stand: String, val von: Int, val bis: Int, val aktien: Int, val negativ: Int, val median: Double, val klassen: List<Klasse>,
        val firmen: Int, val mehrwert: Int, val haelfte: Int, val alles: Int,
    )

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        fun zahl(k: String) = (w[k] as? JsonPrimitive)?.intOrNull
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val klassen = (w["klassen"] as? JsonArray)?.map { e ->
            val o = e as? JsonObject ?: return null
            Klasse(
                (o["von"] as? JsonPrimitive)?.intOrNull ?: return null, (o["bis"] as? JsonPrimitive)?.intOrNull,
                (o["n"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 } ?: return null,
            )
        } ?: return null
        val d = Datei(
            stand, zahl("von") ?: return null, zahl("bis") ?: return null, zahl("aktien") ?: return null, zahl("negativ") ?: return null,
            (w["median"] as? JsonPrimitive)?.doubleOrNull ?: return null, klassen,
            zahl("firmen") ?: return null, zahl("mehrwert") ?: return null, zahl("haelfte") ?: return null, zahl("alles") ?: return null,
        )
        d.takeIf { gueltig(it) }
    }.getOrNull()

    // Klassen lueckenlos und aufsteigend, nur die letzte offen, Summe gleich der Zahl der Aktien, Teilmengen nicht groesser
    // als das Ganze
    private fun gueltig(d: Datei): Boolean {
        if (d.aktien <= 0 || d.firmen <= 0 || d.klassen.size < 2 || d.von > d.bis) return false
        if (d.klassen.sumOf { it.anzahl } != d.aktien) return false
        for ((i, k) in d.klassen.withIndex()) {
            val letzte = i == d.klassen.size - 1
            if (letzte != (k.bis == null)) return false
            if (k.bis != null && (k.bis <= k.von || d.klassen[i + 1].von != k.bis)) return false
        }
        return d.negativ in 0..d.aktien && d.mehrwert in 0..d.firmen && d.haelfte in 1..d.alles && d.alles <= d.firmen
    }

    private fun prozent(teil: Int, ganzes: Int): Double = teil * 100.0 / ganzes

    // Anteil der Aktien mit Verlust ueber die ganze Boersenzeit, Prozent
    fun anteilNegativ(d: Datei): Double = prozent(d.negativ, d.aktien)

    // Anteil der Aktien je Klasse, Prozent, in der Reihenfolge der Klassen
    fun anteile(d: Datei): List<Double> = d.klassen.map { prozent(it.anzahl, d.aktien) }

    // Anteil der Unternehmen, die kein Vermoegen ueber Schatzwechsel hinaus geschaffen haben, Prozent
    fun anteilUnterSchatz(d: Datei): Double = prozent(d.firmen - d.mehrwert, d.firmen)

    // Anteil der Unternehmen, die zusammen den gesamten Nettozuwachs ergeben, Prozent (die besten zuerst gezaehlt)
    fun anteilAlles(d: Datei): Double = prozent(d.alles, d.firmen)

    // Anteil der Unternehmen, die zusammen die Haelfte des Nettozuwachses ergeben, Prozent
    fun anteilHaelfte(d: Datei): Double = prozent(d.haelfte, d.firmen)
}
