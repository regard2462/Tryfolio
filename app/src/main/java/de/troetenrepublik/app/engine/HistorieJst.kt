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

// Laenderrenditen und Krisen seit 1870 [0.78]: 18 Laender, jaehrlich, aus der Jorda-Schularick-Taylor Macrohistory Database
// (R6, CC BY-NC-SA 4.0). Die Datei globus/jst.json (tools/daten/historie.py) traegt reale Renditen als Anteile (0,05 = 5 %),
// Kredit zu BIP in Prozent und die Krisenjahre. Alle Aussagen der Anzeige werden hier aus den Zahlen gerechnet.
object HistorieJst {

    const val DATEI = "globus/jst.json"

    enum class Art(val schluessel: String) { AKTIEN("akt"), ANLEIHEN("anl"), IMMOBILIEN("imm"), ZINS("zins"), INFLATION("infl") }

    // reihen: je Art ein Wert je Jahr ab `von`, null bei Luecke. Fehlt eine Art ganz (Kanada, Irland: keine Renditen), fehlt der Schluessel.
    class Land(
        val a3: String, val von: Int, val reihen: Map<Art, List<Double?>>, val kredit: List<Double?>, val krisen: List<Int>,
        // Jahre ohne Werte wegen Hyperinflation (erstes und letztes), sonst null
        val leer: Pair<Int, Int>? = null,
    )

    class Datei(val stand: String, val ende: Int, val laender: Map<String, Land>, val zitat: List<String>, val lizenz: String)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val ende = (w["ende"] as? JsonPrimitive)?.intOrNull ?: return null
        val zitat = (w["zitat"] as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty()
        val lizenz = (w["lizenz"] as? JsonPrimitive)?.content.orEmpty()
        val l = w["l"] as? JsonObject ?: return null
        val aus = LinkedHashMap<String, Land>()
        for ((k, v) in l) {
            val o = v.jsonObject
            val von = (o["a"] as? JsonPrimitive)?.intOrNull ?: return null
            val reihen = HashMap<Art, List<Double?>>()
            for (a in Art.entries) (o[a.schluessel] as? JsonArray)?.let { r -> reihen[a] = r.map { x -> zahl(x)?.div(10000.0) } }
            val kredit = (o["kredit"] as? JsonArray)?.map { zahl(it) }.orEmpty()
            val krisen = (o["krise"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.orEmpty()
            val leer = (o["leer"] as? JsonArray)?.let { a -> ((a[0] as JsonPrimitive).intOrNull ?: return null) to ((a[1] as JsonPrimitive).intOrNull ?: return null) }
            aus[k] = Land(k, von, reihen, kredit, krisen, leer)
        }
        if (aus.isEmpty()) null else Datei(stand, ende, aus, zitat, lizenz)
    }.getOrNull()

    private fun zahl(e: kotlinx.serialization.json.JsonElement): Double? = if (e is JsonNull) null else (e as? JsonPrimitive)?.doubleOrNull

    // Durchschnittliche Rendite je Jahr (geometrisch, Anteil) ueber alle Jahre mit Wert zwischen ab und bis; jahre = Zahl dieser Jahre.
    class Spanne(val rendite: Double, val erstes: Int, val letztes: Int, val jahre: Int)

    fun spanne(land: Land, art: Art, ab: Int = 0, bis: Int = Int.MAX_VALUE): Spanne? {
        val r = land.reihen[art] ?: return null
        var wachstum = 1.0
        var n = 0
        var erstes = 0
        var letztes = 0
        for (i in r.indices) {
            val j = land.von + i
            val x = r[i] ?: continue
            if (j < ab || j > bis) continue
            wachstum *= 1.0 + x
            if (n == 0) erstes = j
            letztes = j
            n++
        }
        if (n == 0 || wachstum <= 0.0) return null
        return Spanne(wachstum.pow(1.0 / n) - 1.0, erstes, letztes, n)
    }

    // Verlauf des realen Vermoegens: 1 im Jahr vor dem ersten Wert, danach je Jahr mit Wert der Vorjahreswert mal (1 + Rendite);
    // Luecken lassen den Stand unveraendert. Das Jahr eines Punkts ist das Jahr, an dessen Ende der Stand gilt.
    fun vermoegen(land: Land, art: Art): List<Pair<Int, Double>> {
        val r = land.reihen[art] ?: return emptyList()
        val erster = r.indexOfFirst { it != null }
        if (erster < 0) return emptyList()
        val aus = ArrayList<Pair<Int, Double>>(r.size)
        var stand = 1.0
        aus += (land.von + erster - 1) to stand
        for (i in erster until r.size) {
            val x = r[i] ?: continue
            stand *= 1.0 + x
            aus += (land.von + i) to stand
        }
        return aus
    }

    // Ein Zehnjahreszeitraum mit Rendite je Jahr; von und bis einschliesslich.
    class Jahrzehnt(val von: Int, val bis: Int, val rendite: Double)

    // Die n schlechtesten Zehnjahreszeitraeume der Art, ohne gemeinsame Jahre (zuerst der schlechteste). Ein Zeitraum zaehlt nur,
    // wenn alle zehn Jahre einen Wert haben.
    fun schlechteste(land: Land, art: Art = Art.AKTIEN, n: Int = 3): List<Jahrzehnt> {
        val r = land.reihen[art] ?: return emptyList()
        val alle = ArrayList<Jahrzehnt>()
        for (i in 0..r.size - 10) {
            var w = 1.0
            var voll = true
            for (k in i until i + 10) {
                val x = r[k]
                if (x == null) { voll = false; break }
                w *= 1.0 + x
            }
            if (voll && w > 0.0) alle += Jahrzehnt(land.von + i, land.von + i + 9, w.pow(0.1) - 1.0)
        }
        val gewaehlt = ArrayList<Jahrzehnt>()
        for (j in alle.sortedBy { it.rendite }) {
            if (gewaehlt.size >= n) break
            if (gewaehlt.none { j.von <= it.bis && it.von <= j.bis }) gewaehlt += j
        }
        return gewaehlt
    }

    // Reale Aktienrendite je Jahr in Prozent seit `ab` (0: ab dem ersten Jahr der Reihe); null ohne Aktienreihe oder bei weniger
    // als minJahre Jahren mit Wert.
    fun aktienRendite(land: Land, ab: Int = 0, minJahre: Int = 30): Double? =
        spanne(land, Art.AKTIEN, ab)?.takeIf { it.jahre >= minJahre }?.let { it.rendite * 100.0 }

    // Rangliste der Laender nach realer Aktienrendite je Jahr seit `ab`, die beste zuerst. Laender ohne Aktienreihe fehlen.
    fun rangliste(d: Datei, ab: Int = 0, minJahre: Int = 30): List<GlobusEbenen.Rang> =
        d.laender.values.mapNotNull { l -> aktienRendite(l, ab, minJahre)?.let { GlobusEbenen.Rang(l.a3, it) } }.sortedByDescending { it.wert }

    // Kredit zu BIP (Prozent) im letzten Jahr mit Wert
    fun letzterKredit(land: Land): Pair<Int, Double>? {
        val i = land.kredit.indexOfLast { it != null }
        return if (i < 0) null else (land.von + i) to land.kredit[i]!!
    }
}
