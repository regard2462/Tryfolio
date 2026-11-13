package de.troetenrepublik.app.engine

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.roundToLong

// Heutige Werte einer echten Aktie fuer die Beispielzeile unter den Kennzahl-Lektionen des Finanzwissens. Die Web-App
// liest sie unter WEB_PFAD; solange nichts berechnet ist (kein Netz), liefert die App eine leere Antwort und die Lektion
// zeigt keine Zeile. Je Kennzahl zaehlt die erste Aktie, die den Wert hat (Depot, dann Watchlist, dann grosse Indexwerte).
object Lernbeispiele {

    const val WEB_PFAD = "finanzwissen/data/beispiele.json"

    // Grosse Indexwerte, wenn Depot und Watchlist nichts hergeben
    val ERSATZ = listOf("AAPL", "MSFT", "KO")

    // Kennzahlen einer Aktie; fehlende Werte sind null. Anteile in Prozent.
    class Aktie(
        val name: String, val kgv: Double?, val dividende: Double?, val schwankung: Double?, val beta: Double?, val marge: Double?,
    )

    @Volatile private var stand: ByteArray? = null

    fun setzen(b: ByteArray?) { stand = b }

    // Antwort fuer die Web-Ansicht; ohne Werte ein leeres Objekt
    fun fuerWebansicht(): ByteArray = stand ?: "{}".encodeToByteArray()

    private fun rund(v: Double, stellen: Int): Double {
        var f = 1.0
        repeat(stellen) { f *= 10.0 }
        return (v * f).roundToLong() / f
    }

    private fun gut(v: Double?, auchNegativ: Boolean = false): Double? =
        v?.takeIf { it.isFinite() && (auchNegativ || it > 0.0) && abs(it) < 1e6 }

    // JSON mit je Kennzahl {name, wert} aus der ersten Aktie der Reihenfolge, die den Wert hat; null, wenn keine Kennzahl vorliegt
    fun zusammenstellen(aktien: List<Aktie>): ByteArray? {
        val felder = listOf(
            Triple("pe", 1) { a: Aktie -> gut(a.kgv) },
            Triple("div", 2) { a: Aktie -> gut(a.dividende) },
            Triple("vol", 1) { a: Aktie -> gut(a.schwankung) },
            Triple("beta", 2) { a: Aktie -> gut(a.beta, auchNegativ = true) },
            Triple("marge", 1) { a: Aktie -> gut(a.marge, auchNegativ = true) },
        )
        var treffer = 0
        val o = buildJsonObject {
            for ((schluessel, stellen, wert) in felder) {
                val a = aktien.firstOrNull { it.name.isNotBlank() && wert(it) != null } ?: continue
                treffer++
                put(schluessel, buildJsonObject {
                    put("name", a.name)
                    put("wert", rund(wert(a)!!, stellen))
                })
            }
        }
        return if (treffer == 0) null else o.toString().encodeToByteArray()
    }
}
