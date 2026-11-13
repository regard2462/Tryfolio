package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// Wirtschaftspolitische Unsicherheit (Economic Policy Uncertainty, Baker, Bloom und Davis, CC BY 4.0): Anteil der
// Zeitungsartikel eines Monats, die Wirtschaft, Politik und Unsicherheit nennen. Dazu der Index der Nachrichten ueber
// Schwankungen am Aktienmarkt (EMV). indikatoren/epu.json (tools/daten/epu.py).
//
// Schnittstelle fuer andere Auswertungen: lesen(text) liefert us (ab 1900), gl (global, ab 1997) und emv (ab 1985).
object Epu {

    const val DATEI = "indikatoren/epu.json"
    const val SEITE = "https://www.policyuncertainty.com/"

    // Verlauf und Rang der Anzeige beginnen im Januar 1985 (ab dann gibt es auch den Index der Aktienmarkt-Nachrichten)
    val AB: Int = Monatsreihe.monat(1985, 1)

    class Datei(val stand: String, val us: Monatsreihe, val gl: Monatsreihe, val emv: Monatsreihe, val zitat: List<String>)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val z = w["zitat"] as? JsonObject
        val zitat = listOf("us", "gl", "emv").map { k -> (z?.get(k) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null }
        val us = Monatsreihe.lesen(w["us"] as? JsonObject) ?: return null
        val gl = Monatsreihe.lesen(w["gl"] as? JsonObject) ?: return null
        val emv = Monatsreihe.lesen(w["emv"] as? JsonObject) ?: return null
        if (us.werte.size < 120 || gl.werte.size < 60 || emv.werte.size < 60) null else Datei(stand, us, gl, emv, zitat)
    }.getOrNull()

    // Rang des letzten Werts gegen die Reihe seit `ab` (Monat); fuer die USA ab 1985, wie der Verlauf der Anzeige
    fun lage(r: Monatsreihe, ab: Int = r.von): Gpr.Lage? {
        val (m, v) = r.letzter() ?: return null
        val p = MonatsreiheRechnung.perzentil(r, v, ab) ?: return null
        val seit = run {
            var x = m - 1
            var gefunden: Int? = null
            while (x >= maxOf(ab, r.von)) {
                val y = r.wert(x)
                if (y != null && y >= v) { gefunden = x; break }
                x--
            }
            gefunden
        }
        return Gpr.Lage(m, v, p, MonatsreiheRechnung.stufe(p), seit)
    }
}
