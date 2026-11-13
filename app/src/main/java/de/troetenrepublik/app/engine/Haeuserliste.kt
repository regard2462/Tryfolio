package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// [0.77] Rangliste der Analystenhaeuser aus der gebuendelten Datei analysten/haeuser.json (Erzeuger:
// tools/daten/analystenhaeuser.py). Je Haus: Zahl der Hoch- und Abstufungen, Trefferanteil (Aktie lief nach einer
// Hochstufung besser als ihr Index, nach einer Abstufung schlechter, 3, 6 und 12 Monate spaeter, ueber alle
// verstrichenen Horizonte gepoolt) und mittlerer Vorsprung in Richtung des Urteils. Nur Haeuser ab minUrteilen.
object Haeuserliste {

    class Haus(
        val name: String,
        val urteile: Int,
        val hoch: Int,
        val ab: Int,
        // Anteil 0 bis 1
        val quote: Double,
        val q3: Double?,
        val q6: Double?,
        val q12: Double?,
        // mittlere Mehrrendite gegen den Index in Richtung des Urteils, in Prozent
        val vorsprung: Double,
        val aktien: Int,
        // Monat des juengsten gewerteten Urteils (JJJJ-MM)
        val zuletzt: String,
    )

    class Liste(
        val stand: String,
        val von: String,
        val bis: String,
        val aktien: Int,
        val urteile: Int,
        val minUrteile: Int,
        val haeuser: List<Haus>,
    ) {
        private val karte: Map<String, Haus> = haeuser.associateBy { schluessel(it.name) }

        fun finden(name: String): Haus? = karte[schluessel(name)]
    }

    // Reihenfolge der Rangliste
    enum class Sortierung {
        TREFFER, GESICHERT, VORSPRUNG, URTEILE;

        companion object {
            // Gemerkter Name; unbekannt oder leer gilt als Trefferanteil
            fun von(name: String): Sortierung = entries.firstOrNull { it.name == name } ?: TREFFER
        }
    }

    // Untere Grenze des Trefferanteils nach Wilson (95 %, z = 1,96): der Anteil, den das Haus bei dieser Zahl an Urteilen
    // sehr wahrscheinlich mindestens hat. Wenige Urteile senken ihn; ohne Urteile 0.
    fun gesichert(quote: Double, urteile: Int): Double {
        if (urteile <= 0) return 0.0
        val z2 = 1.96 * 1.96
        val n = urteile.toDouble()
        val p = quote.coerceIn(0.0, 1.0)
        val mitte = p + z2 / (2 * n)
        val rand = 1.96 * kotlin.math.sqrt(p * (1 - p) / n + z2 / (4 * n * n))
        return ((mitte - rand) / (1 + z2 / n)).coerceIn(0.0, 1.0)
    }

    fun gesichert(h: Haus): Double = gesichert(h.quote, h.urteile)

    // Haeuser in der gewaehlten Reihenfolge (absteigend); bei Gleichstand Trefferanteil, Urteile, Name
    fun sortiert(haeuser: List<Haus>, s: Sortierung): List<Haus> {
        val erste: Comparator<Haus> = when (s) {
            Sortierung.TREFFER -> compareByDescending<Haus> { it.quote }
            Sortierung.GESICHERT -> compareByDescending<Haus> { gesichert(it) }
            Sortierung.VORSPRUNG -> compareByDescending<Haus> { it.vorsprung }
            Sortierung.URTEILE -> compareByDescending<Haus> { it.urteile }
        }
        return haeuser.sortedWith(erste.thenByDescending { it.quote }.thenByDescending { it.urteile }.thenBy { it.name })
    }

    // Namen vergleichbar machen: Kleinbuchstaben, nur Buchstaben und Ziffern.
    fun schluessel(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.zahl(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // Liste aus dem Text der Datei; null bei falscher Form oder ohne Haeuser. Die Haeuser stehen nach Trefferanteil
    // absteigend, bei Gleichstand nach Zahl der Urteile.
    fun lesen(text: String): Liste? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val haeuser = (o["haeuser"] as? JsonArray).orEmpty().mapNotNull { e ->
            val h = e as? JsonObject ?: return@mapNotNull null
            val name = h.text("h")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val q = h.zahl("q")?.takeIf { it in 0.0..1.0 } ?: return@mapNotNull null
            val u = h.zahl("u")?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            Haus(
                name, u, h.zahl("hoch")?.toInt() ?: 0, h.zahl("ab")?.toInt() ?: 0, q,
                h.zahl("q3"), h.zahl("q6"), h.zahl("q12"), h.zahl("v") ?: 0.0, h.zahl("a")?.toInt() ?: 0, h.text("z").orEmpty(),
            )
        }.sortedWith(compareByDescending<Haus> { it.quote }.thenByDescending { it.urteile }.thenBy { it.name })
        if (haeuser.isEmpty()) return null
        Liste(
            o.text("stand").orEmpty(), o.text("von").orEmpty(), o.text("bis").orEmpty(),
            (o["aktien"] as? JsonPrimitive)?.intOrNull ?: 0, (o["urteile"] as? JsonPrimitive)?.intOrNull ?: 0,
            (o["minUrteile"] as? JsonPrimitive)?.intOrNull ?: 20, haeuser,
        )
    }.getOrNull()

    // JJJJ-MM-TT oder JJJJ-MM als Zeitpunkt in Millisekunden (UTC); null, wenn unlesbar.
    fun zeit(datum: String): Long? = Alter.tag(datum)?.let { it * 86_400_000L }
}
