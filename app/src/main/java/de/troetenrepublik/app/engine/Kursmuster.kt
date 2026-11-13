package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Muster der Vergangenheit aus langen Tagesreihen (S&P 500 ab 1928, DAX ab 1988): mittlere Jahresrendite und Anteil
// positiver Jahre je Jahr des US-Praesidentschaftszyklus, mittlere Rendite und Anteil positiver Monate je Kalendermonat.
// Gerechnet wird hier; die mitgelieferte Datei assets/indikatoren/kursmuster.json traegt nur die Ergebnisse
// (gebaut mit gradlew :desktop:kursmuster aus den Rohreihen von tools/warnrueckblick/holen.py).
object Kursmuster {

    const val DATEI = "indikatoren/kursmuster.json"

    // mittel: mittlere Rendite in Prozent; positiv von anzahl Jahren (Monaten) endeten im Plus
    @Serializable
    data class Wert(val mittel: Double, val positiv: Int, val anzahl: Int)

    // zyklus: Jahr 1 bis 4 nach einer US-Wahl (leer bei Indizes ausserhalb der USA), monate: Januar bis Dezember.
    // Jahre: erstes und letztes volles Kalenderjahr, Monate: erster und letzter voller Monat (JJJJ-MM).
    @Serializable
    data class Index(
        val id: String, val vonJahr: Int, val bisJahr: Int, val zyklus: List<Wert> = emptyList(),
        val monate: List<Wert>, val monatVon: String, val monatBis: String,
    )

    @Serializable
    data class Datei(val stand: String, val indizes: List<Index>)

    // Jahr im Zyklus: 1 ist das Jahr nach einer Praesidentschaftswahl, 4 das Wahljahr (Wahlen alle vier Jahre: 2024, 2028 …)
    fun zyklusJahr(jahr: Int): Int = ((jahr - 1) % 4 + 4) % 4 + 1

    // Letzter Schlusskurs je Monat als (Jahr * 12 + Monat - 1, Kurs), aelteste zuerst. Der Monat des letzten Tages der Reihe
    // gilt als angebrochen und faellt weg. tage: Tage seit 1970.
    fun monatsEnden(tage: LongArray, kurs: DoubleArray): List<Pair<Int, Double>> {
        val aus = ArrayList<Pair<Int, Double>>()
        for (i in tage.indices) {
            val d = java.time.LocalDate.ofEpochDay(tage[i])
            val m = d.year * 12 + d.monthValue - 1
            if (aus.isNotEmpty() && aus.last().first == m) aus[aus.size - 1] = m to kurs[i] else aus += m to kurs[i]
        }
        if (aus.isNotEmpty()) aus.removeAt(aus.size - 1)
        return aus
    }

    // Rendite je Monat in Prozent (Monatsende gegen das Ende des Vormonats); Monate ohne Vormonat fallen weg
    fun monatsRenditen(enden: List<Pair<Int, Double>>): List<Pair<Int, Double>> =
        enden.zipWithNext().mapNotNull { (a, b) -> if (b.first == a.first + 1 && a.second > 0) b.first to (b.second / a.second - 1) * 100 else null }

    // Rendite je Kalenderjahr in Prozent (Dezember gegen den Dezember davor); nur volle Jahre
    fun jahresRenditen(enden: List<Pair<Int, Double>>): List<Pair<Int, Double>> {
        val dez = enden.filter { it.first % 12 == 11 }
        return dez.zipWithNext().mapNotNull { (a, b) -> if (b.first == a.first + 12 && a.second > 0) b.first / 12 to (b.second / a.second - 1) * 100 else null }
    }

    private fun wert(renditen: List<Double>): Wert =
        Wert(if (renditen.isEmpty()) 0.0 else runden(renditen.average()), renditen.count { it > 0 }, renditen.size)

    private fun runden(v: Double): Double = Math.round(v * 100) / 100.0

    // Je Jahr des Zyklus (1 bis 4) aus Jahresrenditen
    fun zyklus(jahre: List<Pair<Int, Double>>): List<Wert> = (1..4).map { z -> wert(jahre.filter { zyklusJahr(it.first) == z }.map { it.second }) }

    // Je Kalendermonat (Januar bis Dezember) aus Monatsrenditen
    fun saison(monate: List<Pair<Int, Double>>): List<Wert> = (0..11).map { m -> wert(monate.filter { it.first % 12 == m }.map { it.second }) }

    private fun monatText(m: Int): String = "%04d-%02d".format(m / 12, m % 12 + 1)

    // Auswertung einer Tagesreihe; null, wenn sie kein volles Jahr traegt
    fun rechnen(id: String, tage: LongArray, kurs: DoubleArray, mitZyklus: Boolean): Index? {
        val enden = monatsEnden(tage, kurs)
        val monate = monatsRenditen(enden)
        val jahre = jahresRenditen(enden)
        if (monate.isEmpty() || jahre.isEmpty()) return null
        return Index(
            id, jahre.first().first, jahre.last().first, if (mitZyklus) zyklus(jahre) else emptyList(), saison(monate),
            monatText(monate.first().first), monatText(monate.last().first),
        )
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()
        ?.takeIf { d -> d.indizes.isNotEmpty() && d.indizes.all { it.monate.size == 12 && (it.zyklus.isEmpty() || it.zyklus.size == 4) } }

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    val datei: Datei? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    fun index(d: Datei?, id: String): Index? = d?.indizes?.firstOrNull { it.id == id }
}
