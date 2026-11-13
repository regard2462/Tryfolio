package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Crypto Fear & Greed Index von alternative.me ueber die ganze Zeit (taeglich ab 01.02.2018): Archiv aus dem Datenbuendel
// (assets/indikatoren/krypto_fng.json, tools/historie/holen.py krypto), beim Nutzer kommen die Tage danach dazu. Daraus
// der Verlauf ueber alle Jahre und die Einordnung „tiefster Stand seit …“.
object KryptoArchiv {

    const val DATEI = "indikatoren/krypto_fng.json"

    private const val TAG_MS = 86_400_000L

    // Mehr Tage laedt die App nicht nach; ist das Archiv aelter, gibt es keine Einordnung
    const val MAX_TAGE = 1500

    // Archiv als (Zeit in ms, Wert), aelteste zuerst. Die Datei fuehrt den ersten Tag (ab) und je Tag einen Wert (null: fehlt).
    fun lesen(text: String): List<Pair<Long, Double>> = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val ab = java.time.LocalDate.parse(o["ab"]!!.jsonPrimitive.content).toEpochDay()
        o["w"]!!.jsonArray.mapIndexedNotNull { i, e -> e.jsonPrimitive.contentOrNull?.toDoubleOrNull()?.let { (ab + i) * TAG_MS to it } }
    }.getOrDefault(emptyList())

    val datei: List<Pair<Long, Double>> by lazy {
        runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrDefault(emptyList())
    }

    // So viele Tage muss die Abfrage tragen, damit sie an das Archiv anschliesst (mindestens 31 fuer den Monatsvergleich)
    fun limit(jetzt: Long, archiv: List<Pair<Long, Double>> = datei): Int {
        val ende = archiv.lastOrNull()?.first ?: return 31
        return ((jetzt - ende) / TAG_MS + 3).toInt().coerceIn(31, MAX_TAGE)
    }

    // Archiv und geladene Tage als eine Reihe (je Tag ein Wert, geladene gelten); null, wenn zwischen beiden Tage fehlen
    // oder es kein Archiv gibt.
    fun reihe(archiv: List<Pair<Long, Double>>, geladen: List<Pair<Long, Double>>): List<Pair<Long, Double>>? {
        if (archiv.isEmpty()) return null
        if (geladen.isEmpty()) return archiv
        val neu = geladen.map { it.first / TAG_MS to it.second }.sortedBy { it.first }
        val ende = archiv.last().first / TAG_MS
        if (neu.first().first > ende + 1) return null
        val erster = neu.first().first
        return archiv.filter { it.first / TAG_MS < erster } + neu.distinctBy { it.first }.map { it.first * TAG_MS to it.second }
    }

    // „Hoechster (tiefster) Stand seit …“ ueber die ganze Reihe
    fun seit(reihe: List<Pair<Long, Double>>?): StandSeit.Ergebnis? = reihe?.let { StandSeit.pruefen(it, vollstaendig = true) }
}
