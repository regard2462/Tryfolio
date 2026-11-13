package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs

// Daten von Aswath Damodaran (NYU Stern, freie Nutzung): KGV der US-Branchen je Jahresende und die implizite Aktien-
// Risikopraemie des S&P 500. welt/damodaran.json (tools/daten/damodaran.py).
//
// Schnittstelle fuer andere Auswertungen: lesen(text), branche(d, yahooBranche) und vergleich(kgv, branche).
object Damodaran {

    const val DATEI = "welt/damodaran.json"
    const val SEITE = "https://pages.stern.nyu.edu/~adamodar/New_Home_Page/datahistory.html"

    class Branche(val name: String, val werte: List<Double?>, val firmen: Int)

    // erp: implizite Risikopraemie in Prozent je Jahr ab von; rendite: Zins 10-jaehriger US-Staatsanleihen
    class Praemie(val von: Int, val erp: List<Double?>, val rendite: List<Double?>) {
        val bis: Int get() = von + erp.size - 1
        fun letzte(): Pair<Int, Double>? {
            for (i in erp.indices.reversed()) erp[i]?.let { return (von + i) to it }
            return null
        }
    }

    class Datei(
        val stand: String, val jahre: List<Int>, val praemie: Praemie, val gesamt: Branche,
        val branchen: Map<String, Branche>, val zuordnung: Map<String, String>,
    )

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val jahre = (w["jahre"] as? JsonArray)?.map { (it as JsonPrimitive).intOrNull ?: return null } ?: return null
        if (jahre.size < 3 || jahre.zipWithNext().any { (a, b) -> b != a + 1 }) return null
        val e = w["erp"] as? JsonObject ?: return null
        val von = (e["a"] as? JsonPrimitive)?.intOrNull ?: return null
        fun liste(k: String): List<Double?>? =
            (e[k] as? JsonArray)?.map { x -> if (x is JsonNull) null else ((x as? JsonPrimitive)?.doubleOrNull ?: return null) }
        val erp = liste("w") ?: return null
        val rendite = liste("r") ?: return null
        if (erp.isEmpty() || rendite.size != erp.size) return null
        val b = LinkedHashMap<String, Branche>()
        (w["b"] as? JsonObject)?.forEach { (name, v) ->
            val o = v as? JsonObject ?: return null
            val ws = (o["w"] as? JsonArray)?.map { x -> if (x is JsonNull) null else ((x as? JsonPrimitive)?.doubleOrNull ?: return null) } ?: return null
            if (ws.size != jahre.size) return null
            b[name] = Branche(name, ws, (o["n"] as? JsonPrimitive)?.intOrNull ?: 0)
        }
        val gesamtName = (w["gesamt"] as? JsonPrimitive)?.content ?: return null
        val gesamt = b[gesamtName] ?: return null
        val z = LinkedHashMap<String, String>()
        (w["z"] as? JsonObject)?.forEach { (k, v) ->
            val ziel = (v as? JsonPrimitive)?.content ?: return null
            if (ziel !in b) return null
            z[k] = ziel
        }
        if (b.isEmpty() || z.isEmpty()) null else Datei(stand, jahre, Praemie(von, erp, rendite), gesamt, b, z)
    }.getOrNull()

    // Branche einer Aktie nach der Branche der Wertpapierseite (Yahoo); null ohne Zuordnung oder ohne aktuellen Wert
    fun branche(d: Datei, yahoo: String): Branche? {
        val b = d.branchen[d.zuordnung[yahoo.trim()] ?: return null] ?: return null
        return if (b.werte.lastOrNull() == null) null else b
    }

    enum class Lage { GUENSTIGER, VERGLEICHBAR, TEURER }

    // Abstand des KGV der Aktie zum Branchen-KGV in Prozent (positiv: teurer) und Lage
    class Vergleich(val kgv: Double, val branche: Double, val abstand: Double, val lage: Lage)

    // Bis zu diesem Abstand in Prozent gilt das KGV als vergleichbar
    const val VERGLEICHBAR_PROZENT = 10.0

    fun vergleich(kgv: Double, branche: Double): Vergleich? {
        if (!(kgv > 0.0) || !(branche > 0.0) || !kgv.isFinite() || !branche.isFinite()) return null
        val abstand = (kgv / branche - 1.0) * 100.0
        val lage = when {
            abs(abstand) <= VERGLEICHBAR_PROZENT -> Lage.VERGLEICHBAR
            abstand > 0 -> Lage.TEURER
            else -> Lage.GUENSTIGER
        }
        return Vergleich(kgv, branche, abstand, lage)
    }

    // Mittel der Jahreswerte der Branche ohne den letzten (der verglichen wird); null bei weniger als fuenf Werten
    fun mittelBisher(b: Branche): Double? {
        val w = b.werte.dropLast(1).filterNotNull()
        return if (w.size < 5) null else w.average()
    }

    // Anteil der Jahre (0 bis 100), in denen die Branche guenstiger war als im letzten Jahr
    fun rang(b: Branche): Double? {
        val w = b.werte.filterNotNull()
        val letzter = b.werte.lastOrNull() ?: return null
        return if (w.size < 5) null else w.count { it < letzter } * 100.0 / w.size
    }

    // Mittel der Risikopraemie ueber alle Jahre der Reihe
    fun praemieMittel(p: Praemie): Double? {
        val w = p.erp.filterNotNull()
        return if (w.size < 10) null else w.average()
    }

    // Anteil der Jahre mit niedrigerer Praemie als zuletzt (niedrig: Anleger verlangen wenig Aufschlag fuer das Aktienrisiko)
    fun praemieRang(p: Praemie): Double? {
        val letzte = p.letzte()?.second ?: return null
        val w = p.erp.filterNotNull()
        return if (w.size < 10) null else w.count { it < letzte } * 100.0 / w.size
    }
}
