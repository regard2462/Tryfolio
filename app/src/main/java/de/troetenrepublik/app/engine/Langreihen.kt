package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.pow

// Sehr lange Jahresreihen im Laenderblatt [0.78]: Verbraucherpreise, kurzfristiger Zins und Rendite von Staatsanleihen
// aus den historischen Statistiken der Notenbanken (Schweden ab 1290, Norwegen ab 1516). Die Datei globus/langreihen.json
// (tools/daten/langreihen.py) traegt die Werte der Quellen; alle Aussagen der Anzeige werden hier gerechnet.
//
// Schnittstelle fuer andere Auswertungen: lesen(text).laender[ISO3].reihen[Art] liefert die Reihe mit erstem Jahr und
// einem Wert je Jahr (null bei Luecke); punkte(reihe) die Paare aus Jahr und Wert.
object Langreihen {

    const val DATEI = "globus/langreihen.json"

    // PREISE: Index der Verbraucherpreise in der Basis der Quelle. ZINS, ANLEIHE: Prozent im Jahresmittel.
    enum class Art(val schluessel: String) { PREISE("preise"), ZINS("zins"), ANLEIHE("anleihe") }

    class Reihe(val von: Int, val werte: List<Double?>) {
        val bis: Int get() = von + werte.size - 1
        fun wert(jahr: Int): Double? = werte.getOrNull(jahr - von)
    }

    class Land(val a3: String, val quelle: String, val adresse: String, val zitat: List<String>, val reihen: Map<Art, Reihe>)

    class Datei(val stand: String, val laender: Map<String, Land>)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val l = w["l"] as? JsonObject ?: return null
        val aus = LinkedHashMap<String, Land>()
        for ((k, v) in l) {
            val o = v.jsonObject
            val quelle = (o["quelle"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
            val adresse = (o["adresse"] as? JsonPrimitive)?.content?.takeIf { it.startsWith("https://") } ?: return null
            val zitat = (o["zitat"] as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty()
            val reihen = LinkedHashMap<Art, Reihe>()
            for (a in Art.entries) {
                val r = o[a.schluessel] as? JsonObject ?: continue
                val von = (r["a"] as? JsonPrimitive)?.intOrNull ?: return null
                val werte = (r["w"] as? JsonArray)?.map { x -> if (x is JsonNull) null else ((x as? JsonPrimitive)?.doubleOrNull ?: return null) } ?: return null
                // Preise muessen positiv sein (Verhaeltnisse und logarithmische Achse)
                if (werte.isEmpty() || (a == Art.PREISE && werte.any { it != null && it <= 0.0 })) return null
                reihen[a] = Reihe(von, werte)
            }
            if (reihen.isEmpty()) return null
            aus[k] = Land(k, quelle, adresse, zitat, reihen)
        }
        if (aus.isEmpty()) null else Datei(stand, aus)
    }.getOrNull()

    // Jahr und Wert aller Jahre mit Wert
    fun punkte(r: Reihe): List<Pair<Int, Double>> = r.werte.mapIndexedNotNull { i, v -> v?.let { (r.von + i) to it } }

    // Teuerung zwischen dem ersten und letzten Jahr mit Wert in [ab, bis]: proJahr als geometrisches Mittel in Prozent,
    // faktor = Preis am Ende durch Preis am Anfang.
    class Teuerung(val proJahr: Double, val erstes: Int, val letztes: Int, val faktor: Double)

    fun teuerung(r: Reihe, ab: Int = Int.MIN_VALUE, bis: Int = Int.MAX_VALUE): Teuerung? {
        val p = punkte(r).filter { it.first in ab..bis }
        if (p.size < 2) return null
        val (j0, v0) = p.first()
        val (j1, v1) = p.last()
        if (v0 <= 0.0 || v1 <= 0.0 || j1 <= j0) return null
        val faktor = v1 / v0
        return Teuerung((faktor.pow(1.0 / (j1 - j0)) - 1.0) * 100.0, j0, j1, faktor)
    }

    // Teuerung je Jahrhundert: vom letzten Jahr des vorigen Jahrhunderts (oder dem ersten Jahr der Reihe) bis zum letzten
    // Jahr des Jahrhunderts (oder der Reihe). Abschnitte mit weniger als mindestJahre Jahren entfallen.
    fun jahrhunderte(r: Reihe, mindestJahre: Int = 10): List<Teuerung> {
        val aus = ArrayList<Teuerung>()
        var anfang = Math.floorDiv(r.von, 100) * 100
        while (anfang <= r.bis) {
            val t = teuerung(r, maxOf(anfang - 1, r.von), minOf(anfang + 99, r.bis))
            if (t != null && t.letztes - t.erstes >= mindestJahre) aus += t
            anfang += 100
        }
        return aus
    }

    // Jahr mit dem staerksten Preisanstieg gegen das Vorjahr, Prozent
    fun staerkstesJahr(r: Reihe): Pair<Int, Double>? {
        var best: Pair<Int, Double>? = null
        for (i in 1 until r.werte.size) {
            val a = r.werte[i - 1] ?: continue
            val b = r.werte[i] ?: continue
            if (a <= 0.0) continue
            val x = (b / a - 1.0) * 100.0
            if (best == null || x > best.second) best = (r.von + i) to x
        }
        return best
    }

    // Mittel einer Zinsreihe ueber alle Jahre mit Wert, dazu hoechster und tiefster Jahreswert
    class Mittel(val wert: Double, val erstes: Int, val letztes: Int, val jahre: Int, val hoch: Pair<Int, Double>, val tief: Pair<Int, Double>)

    fun mittel(r: Reihe): Mittel? {
        val p = punkte(r)
        if (p.isEmpty()) return null
        return Mittel(p.sumOf { it.second } / p.size, p.first().first, p.last().first, p.size, p.maxByOrNull { it.second }!!, p.minByOrNull { it.second }!!)
    }

    // Preisverlauf auf 1 im ersten Jahr bezogen, fuer die logarithmische Achse
    fun preisVerlauf(r: Reihe): List<Pair<Int, Double>> {
        val p = punkte(r)
        val basis = p.firstOrNull()?.second?.takeIf { it > 0.0 } ?: return emptyList()
        return p.map { it.first to it.second / basis }
    }
}
