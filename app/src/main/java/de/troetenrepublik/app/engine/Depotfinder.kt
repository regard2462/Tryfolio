package de.troetenrepublik.app.engine

import kotlin.math.ln
import kotlin.math.sqrt

// Waehlt die Werte mit dem besten gewichteten Faktor-Score. Jeder Faktor ist ein Rang von 0 bis 1 im Universum,
// fehlende Angaben zaehlen 0,5. Value aus 1/KGV und 1/KBV, Wachstum aus erwartetem gegen letzten Gewinn je Aktie,
// Qualitaet als Gewinn je Aktie durch Buchwert je Aktie. Kennzahlen von Yahoo, nichts davon wird gespeichert.
object Depotfinder {

    enum class Faktor { VALUE, MOMENTUM, WACHSTUM, VOLATILITAET, QUALITAET, DIVIDENDE }

    // null, wo die Angabe fehlt. kurse: Tagesschlusskurse des letzten Jahres, aelteste zuerst.
    data class Kandidat(
        val symbol: String,
        val name: String,
        val kgv: Double?,
        val kbv: Double?,
        val gewinnJeAktie: Double?,
        val gewinnErwartet: Double?,
        val buchwertJeAktie: Double?,
        val dividendenrendite: Double?,
        val kurse: List<Double>,
    )

    data class Treffer(val symbol: String, val name: String, val score: Double, val raenge: Map<Faktor, Double>, val volatilitaet: Double?, val jahr: Double?)

    data class Ergebnis(val treffer: List<Treffer>, val schnittScore: Double, val depotVolatilitaet: Double?)

    // Jaehrliche Schwankung aus Tageskursen (Standardabweichung der Log-Renditen mal Wurzel 252).
    fun volatilitaet(kurse: List<Double>): Double? {
        val r = renditen(kurse)
        if (r.size < 60) return null
        val m = r.average()
        return sqrt(r.sumOf { (it - m) * (it - m) } / (r.size - 1)) * sqrt(252.0)
    }

    private fun renditen(kurse: List<Double>): List<Double> =
        kurse.zipWithNext().filter { (a, b) -> a > 0 && b > 0 }.map { (a, b) -> ln(b / a) }

    // Kursentwicklung ueber zwoelf Monate ohne den letzten Monat (21 Handelstage).
    fun momentum(kurse: List<Double>): Double? {
        if (kurse.size < 200) return null
        val ende = kurse[kurse.size - 1 - 21]
        val start = kurse.first()
        return if (start > 0) ende / start - 1 else null
    }

    fun rohwerte(k: Kandidat): Map<Faktor, Double?> = mapOf(
        Faktor.VALUE to listOfNotNull(k.kgv?.takeIf { it > 0 }?.let { 1 / it }, k.kbv?.takeIf { it > 0 }?.let { 1 / it }).takeIf { it.isNotEmpty() }?.average(),
        Faktor.MOMENTUM to momentum(k.kurse),
        Faktor.WACHSTUM to if (k.gewinnJeAktie != null && k.gewinnErwartet != null && k.gewinnJeAktie > 0) k.gewinnErwartet / k.gewinnJeAktie - 1 else null,
        Faktor.VOLATILITAET to volatilitaet(k.kurse)?.let { -it },
        Faktor.QUALITAET to if (k.gewinnJeAktie != null && k.buchwertJeAktie != null && k.buchwertJeAktie > 0) k.gewinnJeAktie / k.buchwertJeAktie else null,
        Faktor.DIVIDENDE to k.dividendenrendite,
    )

    // Rang von 0 (schlechtester) bis 1 (bester) je Faktor.
    fun raenge(kandidaten: List<Kandidat>): Map<String, Map<Faktor, Double>> {
        val roh = kandidaten.associate { it.symbol to rohwerte(it) }
        val aus = kandidaten.associate { it.symbol to HashMap<Faktor, Double>() }
        for (f in Faktor.entries) {
            val werte = roh.mapNotNull { (s, m) -> m[f]?.takeIf { it.isFinite() }?.let { s to it } }.sortedBy { it.second }
            val n = werte.size
            werte.forEachIndexed { i, (s, _) -> aus.getValue(s)[f] = if (n <= 1) 0.5 else i.toDouble() / (n - 1) }
            for (k in kandidaten) aus.getValue(k.symbol).putIfAbsent(f, 0.5)
        }
        return aus
    }

    // Score von 0 bis 100, ohne Gewichte zaehlen alle Faktoren gleich. Werte unter mindestScore fallen weg.
    // Die Depotschwankung gilt fuer gleiche Gewichte der Treffer.
    fun finden(kandidaten: List<Kandidat>, gewichte: Map<Faktor, Double>, anzahl: Int, mindestScore: Double = 0.0): Ergebnis {
        if (kandidaten.isEmpty()) return Ergebnis(emptyList(), 0.0, null)
        val g = gewichte.filterValues { it > 0 }.ifEmpty { Faktor.entries.associateWith { 1.0 } }
        val summe = g.values.sum()
        val r = raenge(kandidaten)
        val treffer = kandidaten.map { k ->
            val rk = r.getValue(k.symbol)
            val score = g.entries.sumOf { (f, w) -> w * (rk[f] ?: 0.5) } / summe * 100
            Treffer(k.symbol, k.name, score, rk, volatilitaet(k.kurse), k.kurse.takeIf { it.size > 200 }?.let { it.last() / it.first() - 1 })
        }.filter { it.score >= mindestScore }.sortedByDescending { it.score }.take(anzahl)
        val reihen = treffer.mapNotNull { t -> kandidaten.first { it.symbol == t.symbol }.kurse.takeIf { it.size > 60 }?.let { renditen(it) } }
        val depotVola = if (reihen.size < 2) null else {
            val n = reihen.minOf { it.size }
            val depot = (0 until n).map { i -> reihen.sumOf { it[it.size - n + i] } / reihen.size }
            val m = depot.average()
            sqrt(depot.sumOf { (it - m) * (it - m) } / (depot.size - 1)) * sqrt(252.0)
        }
        return Ergebnis(treffer, if (treffer.isEmpty()) 0.0 else treffer.map { it.score }.average(), depotVola)
    }
}
