package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// Abstand zum Index [0.78]: US-Indexfonds melden der SEC im Jahresbericht Form N-CEN (Item C.3.b), um wie viel ihre
// Rendite im Geschaeftsjahr von der des Index abwich, vor und nach den Kosten des Fonds. Die Datei etf-abstand.json
// (tools/daten/index_abstand.py) traegt die Werte je Fonds mit Boersenkuerzel und je Jahr die Verteilung ueber alle
// Indexfonds. Alle Abstaende in Prozentpunkten (Fonds minus Index); die Datei speichert Hundertstel.
// Den Namen des Index nennt das Formular nicht. Die Zahlen sind Angaben der Fonds und ungeprueft.
object IndexAbstand {

    const val DATEI = "etf-abstand.json"
    const val ADRESSE = "https://www.sec.gov/data-research/sec-markets-data/form-n-cen-data-sets"

    // jahr: Kalenderjahr, in dem das Geschaeftsjahr endet. vor: vor Kosten (kann fehlen), nach: nach Kosten.
    class Jahr(val jahr: Int, val vor: Double?, val nach: Double)

    // monat: Monat des Geschaeftsjahresendes (1 bis 12)
    class Fonds(val kuerzel: List<String>, val name: String, val monat: Int, val jahre: List<Jahr>)

    // klassen: Zahl der Fonds je Klasse des Abstands nach Kosten; Klasse i reicht bis grenzen[i] (ausschliesslich), die
    // letzte liegt darueber. median und medianVor in Prozentpunkten.
    class Verteilung(val jahr: Int, val anzahl: Int, val median: Double, val medianVor: Double?, val klassen: List<Int>)

    class Datei(val stand: String, val grenzen: List<Double>, val verteilung: List<Verteilung>, val fonds: Map<String, Fonds>)

    private fun zahl(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull?.div(100.0)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val grenzen = (w["grenzen"] as? JsonArray)?.map { zahl(it) ?: return null } ?: return null
        if (grenzen.isEmpty() || grenzen.zipWithNext().any { (a, b) -> a >= b }) return null
        val verteilung = (w["verteilung"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val k = (o["k"] as? JsonArray)?.map { (it as JsonPrimitive).intOrNull ?: return@mapNotNull null } ?: return@mapNotNull null
            val n = (o["n"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            if (k.size != grenzen.size + 1 || k.any { it < 0 } || k.sum() != n || n <= 0) return@mapNotNull null
            Verteilung((o["j"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null, n, zahl(o["m"]) ?: return@mapNotNull null, zahl(o["mv"]), k)
        }.sortedBy { it.jahr }
        val fonds = LinkedHashMap<String, Fonds>()
        for (e in (w["fonds"] as? JsonArray).orEmpty()) {
            val o = e as? JsonObject ?: continue
            val t = (o["t"] as? JsonArray)?.map { (it as JsonPrimitive).content.uppercase() }?.filter { it.isNotBlank() }.orEmpty()
            val ab = (o["ab"] as? JsonPrimitive)?.intOrNull ?: continue
            val vor = o["v"] as? JsonArray ?: continue
            val nach = o["a"] as? JsonArray ?: continue
            if (t.isEmpty() || vor.size != nach.size) continue
            val jahre = nach.indices.mapNotNull { i -> zahl(nach[i])?.let { Jahr(ab + i, zahl(vor[i]), it) } }
            if (jahre.isEmpty()) continue
            val f = Fonds(t, (o["n"] as? JsonPrimitive)?.content.orEmpty(), (o["m"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..12 } ?: 12, jahre)
            for (k in t) fonds[k] = f
        }
        if (fonds.isEmpty() || verteilung.isEmpty()) null else Datei(stand, grenzen, verteilung, fonds)
    }.getOrNull()

    // Fonds zu einem Kuerzel der App. Nur Kuerzel ohne Boersenzusatz (US-Notierung) passen: "VOO", nicht "VUSA.L".
    fun fuer(d: Datei, symbol: String): Fonds? {
        val s = symbol.trim().uppercase()
        return if (s.isEmpty() || '.' in s || '=' in s || '^' in s) null else d.fonds[s]
    }

    // mittel: Mittelwert des Abstands nach Kosten ueber die gemeldeten Jahre. mittelVor und kosten nur aus den Jahren,
    // die beide Werte nennen; kosten = vor minus nach, also was die Kosten des Fonds im Schnitt wegnahmen.
    class Kern(val mittel: Double, val mittelVor: Double?, val kosten: Double?, val von: Int, val bis: Int, val jahre: Int)

    fun kern(f: Fonds): Kern {
        val beide = f.jahre.filter { it.vor != null }
        return Kern(
            mittel = f.jahre.sumOf { it.nach } / f.jahre.size,
            mittelVor = if (beide.isEmpty()) null else beide.sumOf { it.vor!! } / beide.size,
            kosten = if (beide.isEmpty()) null else beide.sumOf { it.vor!! - it.nach } / beide.size,
            von = f.jahre.first().jahr, bis = f.jahre.last().jahr, jahre = f.jahre.size,
        )
    }

    // Juengstes Jahr, in dem fast alle Fonds gemeldet haben: das letzte mit mindestens drei Vierteln der groessten Zahl.
    // Das laufende Jahr ist bis zum Fruehjahr des Folgejahres unvollstaendig.
    fun vollesJahr(d: Datei): Verteilung {
        val max = d.verteilung.maxOf { it.anzahl }
        return d.verteilung.last { it.anzahl * 4 >= max * 3 }
    }

    // Anteil der Fonds (0 bis 1), die nach Kosten hinter ihrem Index lagen (Abstand unter null)
    fun anteilDahinter(d: Datei, v: Verteilung): Double {
        val bis = d.grenzen.indexOfFirst { it >= 0.0 }.let { if (it < 0) d.grenzen.size else if (d.grenzen[it] == 0.0) it else it - 1 }
        return v.klassen.take(bis + 1).sum().toDouble() / v.anzahl
    }

    // Klasse eines Abstands in der Verteilung (0 bis grenzen.size)
    fun klasse(d: Datei, abstand: Double): Int = d.grenzen.indexOfFirst { abstand < it }.let { if (it < 0) d.grenzen.size else it }
}
