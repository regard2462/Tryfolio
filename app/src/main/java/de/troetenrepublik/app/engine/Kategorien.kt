package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.roundToLong

// Eigene Kategorien mit Zielgewichten fuers Umschichten (wie Klassifizierungen in Portfolio Performance).
@Serializable
data class Kategorie(val id: String, val name: String, val ziel: Double)

// Gespeicherter Stand auf dem Geraet: Kategorien, Zuordnung Positionsschluessel zu Kategorie, Zielgewichte je Position
// in Prozent (beliebige Summe) und ob der Startvorschlag schon angelegt wurde.
@Serializable
data class KategorienStand(
    val kategorien: List<Kategorie> = emptyList(),
    val zuordnung: Map<String, String> = emptyMap(),
    val positionsziele: Map<String, Double> = emptyMap(),
    val vorgeschlagen: Boolean = false,
    // Ob Kategorieziele selbst gesetzt wurden (sonst entsprechen sie dem Startvorschlag, also dem heutigen Stand)
    val zieleGesetzt: Boolean = false,
)

object Kategorien {

    private val json = Json { ignoreUnknownKeys = true }

    // Ein unlesbarer Stand gilt als leer.
    fun lesen(text: String): KategorienStand =
        if (text.isBlank()) KategorienStand() else runCatching { json.decodeFromString<KategorienStand>(text) }.getOrDefault(KategorienStand())

    fun schreiben(stand: KategorienStand): String =
        if (stand == KategorienStand()) "" else json.encodeToString(stand)

    // Eine Position mit Wert und Klasse (Name der Anlageklasse) fuer den Startvorschlag.
    class Wert(val key: String, val wert: Double, val klasse: String)

    // Startvorschlag: eine Kategorie je Anlageklasse mit dem heutigen Anteil in Prozent (eine Nachkommastelle, Summe 100).
    fun vorschlag(werte: List<Wert>): KategorienStand {
        val w = werte.filter { it.wert > 0 && it.wert.isFinite() }
        if (w.isEmpty()) return KategorienStand(vorgeschlagen = true)
        val summe = w.sumOf { it.wert }
        val gruppen = w.groupBy { it.klasse }.map { (k, v) -> k to v }.sortedByDescending { (_, v) -> v.sumOf { it.wert } }
        val zehntel = gruppen.map { (_, v) -> (v.sumOf { it.wert } / summe * 1000.0).roundToLong() }.toMutableList()
        zehntel[0] += 1000L - zehntel.sum()
        val kats = gruppen.mapIndexed { i, (k, _) -> Kategorie("k${i + 1}", k, max(0L, zehntel[i]) / 10.0) }
        val zu = HashMap<String, String>()
        gruppen.forEachIndexed { i, (_, v) -> v.forEach { zu[it.key] = "k${i + 1}" } }
        return KategorienStand(kats, zu, vorgeschlagen = true)
    }

    fun neu(stand: KategorienStand, name: String, ziel: Double): KategorienStand {
        val nr = (stand.kategorien.mapNotNull { it.id.removePrefix("k").toIntOrNull() }.maxOrNull() ?: 0) + 1
        return stand.copy(kategorien = stand.kategorien + Kategorie("k$nr", name.trim(), sauber(ziel)), vorgeschlagen = true)
    }

    fun umbenennen(stand: KategorienStand, id: String, name: String): KategorienStand =
        stand.copy(kategorien = stand.kategorien.map { if (it.id == id) it.copy(name = name) else it })

    fun zielSetzen(stand: KategorienStand, id: String, ziel: Double): KategorienStand =
        stand.copy(kategorien = stand.kategorien.map { if (it.id == id) it.copy(ziel = sauber(ziel)) else it }, zieleGesetzt = true)

    // Setzt das Ziel einer Kategorie und skaliert die uebrigen im bisherigen Verhaeltnis, sodass die Summe 100 bleibt.
    fun zielVerteilen(stand: KategorienStand, id: String, ziel: Double): KategorienStand {
        val neu = verteilen(stand.kategorien.associate { it.id to it.ziel }, id, ziel)
        return stand.copy(kategorien = stand.kategorien.map { it.copy(ziel = neu[it.id] ?: it.ziel) }, zieleGesetzt = true)
    }

    // Setzt einen Wert (Prozent) und verteilt den Rest bis 100 im bisherigen Verhaeltnis auf die anderen; sind alle
    // anderen null, zu gleichen Teilen. Bei nur einem Eintrag gilt der Wert, wie er ist.
    fun verteilen(ziele: Map<String, Double>, key: String, wert: Double): Map<String, Double> {
        val v = if (wert.isFinite()) wert.coerceIn(0.0, 100.0) else 0.0
        val rest = ziele.filterKeys { it != key }
        if (rest.isEmpty()) return mapOf(key to v)
        val summe = rest.values.sumOf { max(0.0, it) }
        val frei = 100.0 - v
        return rest.mapValues { (_, z) -> if (summe > 0) max(0.0, z) / summe * frei else frei / rest.size } + (key to v)
    }

    // Beim Loeschen fallen die Positionen der Kategorie auf Ohne Kategorie.
    fun loeschen(stand: KategorienStand, id: String): KategorienStand =
        stand.copy(kategorien = stand.kategorien.filter { it.id != id }, zuordnung = stand.zuordnung.filterValues { it != id })

    // id = null: ohne Kategorie.
    fun zuordnen(stand: KategorienStand, key: String, id: String?): KategorienStand =
        stand.copy(zuordnung = if (id == null || stand.kategorien.none { it.id == id }) stand.zuordnung - key else stand.zuordnung + (key to id))

    fun zieleSpeichern(stand: KategorienStand, ziele: Map<String, Double>): KategorienStand =
        stand.copy(positionsziele = ziele.filterValues { it.isFinite() && it >= 0 })

    fun summeZiele(stand: KategorienStand): Double = stand.kategorien.sumOf { it.ziel }

    private fun sauber(z: Double): Double = if (z.isFinite()) max(0.0, z) else 0.0

    // Eine Zeile je Kategorie mit Positionen (id null = Positionen ohne Kategorie): Anteile am Depot (0 bis 1),
    // Werte in Euro. abweichung = Ist minus Ziel.
    class Zeile(val id: String?, val name: String, val wert: Double, val zielWert: Double, val ist: Double, val ziel: Double) {
        val abweichung: Double get() = ist - ziel
        val abweichungEuro: Double get() = wert - zielWert
    }

    class Plan(val zeilen: List<Zeile>, val orders: List<PortfolioTools.Trade>)

    // Orders, die die Kategorieziele erreichen. Die Ziele (beliebige Summe) gelten im Verhaeltnis. Innerhalb einer
    // Kategorie verteilt sich Ziel und Auftrag im heutigen Verhaeltnis der Positionen. Kategorien ohne Position zaehlen
    // nicht mit. Positionen ohne Kategorie bleiben unveraendert. Mit sell wird umgeschichtet; ohne wird nur mit
    // neuesGeld zugekauft, dort wo am meisten fehlt (reicht das Geld nicht, anteilig).
    fun plan(stand: KategorienStand, werte: List<Pair<String, Double>>, neuesGeld: Double, sell: Boolean, ohneName: String = ""): Plan {
        val ids = stand.kategorien.map { it.id }.toSet()
        fun kat(key: String): String? = stand.zuordnung[key]?.takeIf { it in ids }
        val geld = if (sell) 0.0 else max(0.0, if (neuesGeld.isFinite()) neuesGeld else 0.0)
        val summe = werte.sumOf { it.second }
        val gesamt = summe + geld
        val aktiv = stand.kategorien.filter { k -> werte.any { kat(it.first) == k.id } }
        val sumZiel = aktiv.sumOf { it.ziel }
        val fest = werte.filter { kat(it.first) == null }.sumOf { it.second }
        val pool = summe - fest + geld
        if (werte.isEmpty() || sumZiel <= 0 || gesamt <= 0) {
            return Plan(zeilenOhneZiel(stand, werte, ::kat, gesamt, ohneName), werte.map { (k, v) -> PortfolioTools.Trade(k, v, v, 0.0) })
        }
        val zielJe = aktiv.associate { it.id to it.ziel / sumZiel * pool }
        val istJe = aktiv.associate { k -> k.id to werte.filter { kat(it.first) == k.id }.sumOf { it.second } }
        val bedarf = aktiv.associate { k -> k.id to max(0.0, zielJe.getValue(k.id) - istJe.getValue(k.id)) }
        val sumBedarf = bedarf.values.sum()
        val skala = if (sumBedarf > geld && sumBedarf > 0) geld / sumBedarf else 1.0
        val orders = werte.map { (key, v) ->
            val id = kat(key) ?: return@map PortfolioTools.Trade(key, v, v, 0.0)
            val anteil = if (istJe.getValue(id) > 0) v / istJe.getValue(id) else 0.0
            val ziel = zielJe.getValue(id) * anteil
            val betrag = if (sell) ziel - v else bedarf.getValue(id) * skala * anteil
            PortfolioTools.Trade(key, v, ziel, betrag)
        }
        val zeilen = aktiv.map { k -> Zeile(k.id, k.name, istJe.getValue(k.id), zielJe.getValue(k.id), istJe.getValue(k.id) / gesamt, zielJe.getValue(k.id) / gesamt) } +
            (if (fest > 0) listOf(Zeile(null, ohneName, fest, fest, fest / gesamt, fest / gesamt)) else emptyList())
        return Plan(zeilen, orders)
    }

    private fun zeilenOhneZiel(stand: KategorienStand, werte: List<Pair<String, Double>>, kat: (String) -> String?, gesamt: Double, ohneName: String): List<Zeile> {
        if (gesamt <= 0) return emptyList()
        val z = stand.kategorien.mapNotNull { k ->
            val v = werte.filter { kat(it.first) == k.id }.sumOf { it.second }
            if (v > 0) Zeile(k.id, k.name, v, v, v / gesamt, v / gesamt) else null
        }
        val fest = werte.filter { kat(it.first) == null }.sumOf { it.second }
        return z + (if (fest > 0) listOf(Zeile(null, ohneName, fest, fest, fest / gesamt, fest / gesamt)) else emptyList())
    }

    // Anfangsgewichte der Regler je Position: gespeichertes Ziel, sonst der heutige Anteil in Prozent.
    fun startGewichte(stand: KategorienStand, werte: List<Pair<String, Double>>): Map<String, Double> {
        val summe = werte.sumOf { it.second }
        return werte.associate { (k, v) -> k to (stand.positionsziele[k] ?: if (summe > 0) v / summe * 100.0 else 0.0) }
    }
}
