package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

// Tonlage der Berichterstattung ueber eine Firma (GDELT 2.0 DOC API, Modus timelinetone, englischsprachige Medien, frei mit
// Quellenangabe): durchschnittlicher Ton aller Artikel eines Tages, in dem die Firma und ein Begriff aus dem Aktienumfeld
// vorkommen. GDELT bewertet jeden Artikel auf einer Skala um null (negativ unter null, positiv darueber); Nachrichten
// liegen im Schnitt unter null, darum zaehlt der Vergleich mit den Monaten davor, nicht der Wert allein.
// GDELT erlaubt eine Anfrage je fuenf Sekunden (siehe Spannungen.ABSTAND_MS).
object Ton {
    const val GUELTIG_MS = 6 * 3_600_000L
    // Der Index der DOC API reicht drei Monate zurueck
    const val TAGE = 92
    // Letzte Wochen, die gegen den Rest der Reihe verglichen werden
    const val NEU_TAGE = 30
    // Mindestzahl Tageswerte im neuen Teil und im Teil davor
    const val MIND_NEU = 15
    const val MIND_DAVOR = 30
    // Kleinster Abstand der Mittelwerte (Tonpunkte), ab dem eine Aenderung gemeldet wird, und ab dem sie deutlich ist
    const val SCHWELLE = 0.3
    const val DEUTLICH = 0.8

    // Begriffe aus dem Aktienumfeld: ohne sie meldet der Firmenname allein auch Obst, Staedte und Personen
    private const val UMFELD = "(shares OR stock OR earnings OR \"stock price\" OR CEO OR investors)"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // Suchausdruck der Firma in GDELT-Schreibweise aus den Namen der Seite; null bei zu kurzem Namen (unter vier Buchstaben
    // lehnt GDELT den Ausdruck ab oder findet zu viel)
    fun suche(namen: List<String>): String? {
        for (n in namen) {
            val w = de.troetenrepublik.app.market.FirmaName.woerter(n).filter { it.all { c -> c.isLetterOrDigit() } }
            val phrase = w.joinToString(" ")
            if (phrase.length >= 4 && w.isNotEmpty()) return "\"$phrase\" $UMFELD"
        }
        return null
    }

    fun adresse(suche: String): String =
        Spannungen.ADRESSE + "?query=" + enc("$suche sourcelang:eng") + "&mode=timelinetone&format=json&timespan=3months"

    // Tageston: Tag (Tage seit 1970) und Ton
    class Tag(val t: Int, val ton: Double)

    // Antwort: {"timeline":[{"series":"Average Tone","data":[{"date":"20261010T000000Z","value":-1.52}, ...]}]}.
    // Leer ({}) oder die Bitte um Abstand (Text) ergeben null.
    fun lesen(text: String): List<Tag>? = runCatching {
        val w = Laenderrisiken.json.parseToJsonElement(text).jsonObject
        val daten = ((w["timeline"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("data") as? JsonArray ?: return null
        val aus = ArrayList<Tag>(daten.size)
        for (e in daten) {
            val o = e as? JsonObject ?: continue
            val d = o["date"]?.jsonPrimitive?.contentOrNull ?: continue
            val v = o["value"]?.jsonPrimitive?.doubleOrNull ?: continue
            if (!v.isFinite() || abs(v) > 30.0) continue
            val tag = LocalDate.of(d.substring(0, 4).toInt(), d.substring(4, 6).toInt(), d.substring(6, 8).toInt()).toEpochDay().toInt()
            aus += Tag(tag, v)
        }
        aus.sortBy { it.t }
        if (aus.isEmpty()) null else aus
    }.getOrNull()

    enum class Richtung { DEUTLICH_NEGATIVER, NEGATIVER, UNVERAENDERT, POSITIVER, DEUTLICH_POSITIVER }

    // neu: Mittel der letzten NEU_TAGE Tage; davor: Mittel der Tage davor; abstand = neu - davor in Tonpunkten
    @Serializable
    class Wert(val abgerufen: Long, val tage: List<Int>, val ton: List<Double>)

    class Ergebnis(val neu: Double, val davor: Double, val abstand: Double, val richtung: Richtung, val bis: Int, val tage: List<Tag>)

    fun wert(tage: List<Tag>, jetzt: Long): Wert = Wert(jetzt, tage.map { it.t }, tage.map { it.ton })

    fun tage(w: Wert): List<Tag> = if (w.tage.size != w.ton.size) emptyList() else w.tage.indices.map { Tag(w.tage[it], w.ton[it]) }

    private fun mittel(w: List<Double>) = w.average()

    private fun streuung(w: List<Double>, m: Double): Double =
        if (w.size < 2) 0.0 else sqrt(w.sumOf { (it - m) * (it - m) } / (w.size - 1))

    // Vergleich der letzten Tage mit den Tagen davor. Eine Aenderung gilt nur, wenn sie ueber der Schwelle liegt und sich
    // vom Rauschen der Tageswerte abhebt (Abstand mindestens doppelt so gross wie sein Standardfehler).
    fun bewerten(tage: List<Tag>): Ergebnis? {
        if (tage.isEmpty()) return null
        val bis = tage.maxOf { it.t }
        val neu = tage.filter { it.t > bis - NEU_TAGE }.map { it.ton }
        val davor = tage.filter { it.t <= bis - NEU_TAGE }.map { it.ton }
        if (neu.size < MIND_NEU || davor.size < MIND_DAVOR) return null
        val mn = mittel(neu)
        val md = mittel(davor)
        val abstand = mn - md
        val fehler = sqrt(streuung(neu, mn).let { it * it } / neu.size + streuung(davor, md).let { it * it } / davor.size)
        val echt = abs(abstand) >= SCHWELLE && (fehler <= 0.0 || abs(abstand) >= 2.0 * fehler)
        val richtung = when {
            !echt -> Richtung.UNVERAENDERT
            abstand < 0 -> if (abs(abstand) >= DEUTLICH) Richtung.DEUTLICH_NEGATIVER else Richtung.NEGATIVER
            else -> if (abstand >= DEUTLICH) Richtung.DEUTLICH_POSITIVER else Richtung.POSITIVER
        }
        return Ergebnis(mn, md, abstand, richtung, bis, tage)
    }

    // Wochenmittel fuer die Linie (Tageswerte schwanken zu stark): je sieben Tage ein Punkt (Tag in der Mitte, Mittel), nur
    // mit mindestens drei Tageswerten in der Woche; die letzte Woche endet am letzten Tag.
    fun wochen(tage: List<Tag>): List<Pair<Int, Double>> {
        if (tage.isEmpty()) return emptyList()
        val bis = tage.maxOf { it.t }
        val aus = ArrayList<Pair<Int, Double>>()
        var ende = bis
        while (ende - 6 >= tage.minOf { it.t }) {
            val w = tage.filter { it.t in (ende - 6)..ende }
            if (w.size >= 3) aus += (ende - 3) to w.map { it.ton }.average()
            ende -= 7
        }
        return aus.reversed()
    }
}
