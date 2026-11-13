package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Direkte Treibhausgas-Emissionen je Firma aus zwei mitgelieferten Dateien: firmen/emissionen.json (Greenhouse Gas Reporting Program
// der US-Umweltbehoerde EPA, tools/daten/emissionen.py) und firmen/emissionen-eu.json (geprueften Emissionen des EU-Emissionshandels
// aus dem Unionsregister der EU-Kommission, tools/daten/emissionen_eu.py). Enthalten sind nur meldepflichtige Anlagen, kein
// Gesamtfussabdruck. Zuordnung ueber das Kuerzel im Katalog (Feld y, vom Erzeuger nach dem Namen des Betreibers oder der
// Muttergesellschaft gesetzt) oder ueber den Firmennamen, nur bei eindeutigem Treffer. Reines Lesen und Rechnen, ohne Netz.
object FirmaEmissionen {

    const val DATEI = "firmen/emissionen.json"
    const val DATEI_EU = "firmen/emissionen-eu.json"

    // gebiet: "US" oder "EU" (bei EU-Anlagen steht im Feld staat das Land); fruehere: Tonnen der Jahre vor dem letzten, neueste zuerst
    class Standort(
        val name: String,
        val staat: String,
        val ort: String,
        val tonnen: Double,
        val anteil: Int,
        val gebiet: String = "US",
        val fruehere: List<Double> = emptyList(),
    )

    class Firma(val name: String, val tonnen: List<Double>, val anlagen: List<Int>, val standorte: List<Standort>)

    // firmen: Schluessel "y:KUERZEL" oder zusammengeschriebener Firmenname
    class Datei(val stand: String, val jahr0: Int, val firmen: Map<String, Firma>, val gebiet: String = "US")

    class Teil(val datei: Datei, val firma: Firma)

    private fun JsonObject.text(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    private fun JsonObject.ganz(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: 0

    private fun addiert(a: Firma, b: Firma) = Firma(
        a.name, a.tonnen.zip(b.tonnen) { x, y -> x + y }, a.anlagen.zip(b.anlagen) { x, y -> x + y },
        (a.standorte + b.standorte).sortedByDescending { maxOf(it.tonnen, it.fruehere.maxOrNull() ?: 0.0) }.take(6),
    )

    // Liest die Datei; null bei kaputtem JSON oder ohne Firmen. Gleiche Schluessel (ExxonMobil, Exxon Mobil) und gleiche Kuerzel
    // werden addiert.
    fun lesen(text: String): Datei? = runCatching {
        val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
        if (o.ganz("format") != 1) return null
        val jahre = o.ganz("jahre").takeIf { it > 0 } ?: return null
        val gebiet = o.text("gebiet").ifEmpty { "US" }
        val aus = LinkedHashMap<String, Firma>()
        for (e in o["firmen"] as? JsonArray ?: return null) {
            val f = e as? JsonObject ?: continue
            val name = f.text("n")
            val kuerzel = (f["y"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf { c -> c.isNotEmpty() } }
            val schluessel = if (kuerzel.isEmpty()) listOf(FirmaName.schluessel(name)) else kuerzel.map { "y:$it" }
            val t = (f["e"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
            if (t.size != jahre) continue
            val z = (f["z"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.intOrNull ?: 0 }.let { if (it.size == jahre) it else List(jahre) { 0 } }
            val s = (f["s"] as? JsonArray).orEmpty().mapNotNull { x ->
                val a = x as? JsonArray ?: return@mapNotNull null
                fun str(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
                fun num(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: 0.0
                if (gebiet == "US") Standort(str(0), str(1), str(2), num(3), num(4).toInt())
                else Standort(str(0), str(1), str(2), num(3), 100, gebiet, listOf(num(4), num(5)))
            }
            val neu = Firma(name, t, z, s)
            for (k in schluessel) {
                if (k.isEmpty()) continue
                val alt = aus[k]
                aus[k] = if (alt == null) neu else addiert(alt, neu)
            }
        }
        if (aus.isEmpty()) null else Datei(o.text("stand"), o.ganz("jahr0"), aus, gebiet)
    }.getOrNull()

    // Eingebaute oder geprueft nachgeladene Dateien, einmal je Prozess gelesen.
    val aktuell: Datei? by lazy { runCatching { lesen(Daten.text(DATEI)) }.getOrNull() }
    val europa: Datei? by lazy { runCatching { lesen(Daten.text(DATEI_EU)) }.getOrNull() }

    // Die Firma, wenn das Kuerzel im Katalog zugeordnet ist oder einer ihrer Namen genau dem einer Muttergesellschaft entspricht;
    // sonst null.
    fun fuer(d: Datei?, namen: List<String>, symbol: String? = null): Firma? {
        d ?: return null
        if (symbol != null) d.firmen["y:$symbol"]?.let { return it }
        val treffer = FirmaName.schluessel(namen).mapNotNull { d.firmen[it] }.distinct()
        return treffer.singleOrNull()
    }

    // Treffer in allen Dateien (USA, EU)
    fun teile(symbol: String, namen: List<String>, dateien: List<Datei?> = listOf(aktuell, europa)): List<Teil> =
        dateien.mapNotNull { d -> if (d == null) null else fuer(d, namen, symbol)?.let { f -> Teil(d, f) } }

    // Jahre der Reihe ab dem ersten mit Emissionen, hoechstens die letzten zehn
    class Stand(
        val jahr: Int,
        val tonnen: Double,
        // Aenderung gegen das Vorjahr in Prozent; null ohne Vorjahr
        val aenderung: Double?,
        val reihe: List<Pair<Int, Double>>,
        val anlagen: Int,
        val standorte: List<Standort>,
        // Gebiete mit Emissionen im Jahr: "US", "EU"
        val gebiete: List<String> = listOf("US"),
    )

    // Stand einer einzelnen Datei; null, wenn die Firma im letzten Jahr nichts gemeldet hat.
    fun auswerten(d: Datei, f: Firma): Stand? = auswerten(listOf(Teil(d, f)))

    // Stand aus allen Dateien der Firma. Gerechnet wird im letzten Jahr, das alle Dateien haben, damit USA und EU gleich weit
    // reichen (die EPA-Daten kommen mit mehr Verzug als die EU-Daten). Standorte zeigen die Tonnen dieses Jahres, Standorte ohne
    // Angabe fuer dieses Jahr entfallen. Null, wenn die Firma in diesem Jahr nichts gemeldet hat.
    fun auswerten(teile: List<Teil>): Stand? {
        if (teile.isEmpty()) return null
        val letzt = teile.minOf { it.datei.jahr0 + it.firma.tonnen.lastIndex }
        fun wert(t: Teil, jahr: Int) = t.firma.tonnen.getOrNull(jahr - t.datei.jahr0) ?: 0.0
        fun summe(jahr: Int) = teile.sumOf { wert(it, jahr) }
        val t = summe(letzt).takeIf { it > 0 } ?: return null
        // Vorjahr nur, wenn alle Dateien es abdecken (sonst fehlt ein Gebiet in der Summe)
        val vor = if (teile.all { letzt - 1 >= it.datei.jahr0 }) summe(letzt - 1).takeIf { it > 0 } else null
        val erstes = maxOf(
            teile.minOf { tl -> tl.datei.jahr0 + tl.firma.tonnen.indexOfFirst { it > 0 }.let { i -> if (i < 0) Int.MAX_VALUE / 2 else i } },
            teile.maxOf { it.datei.jahr0 },
            letzt - 9,
        )
        val reihe = (erstes..letzt).map { it to summe(it) }
        val standorte = teile.flatMap { tl ->
            val abstand = tl.datei.jahr0 + tl.firma.tonnen.lastIndex - letzt
            tl.firma.standorte.mapNotNull { s ->
                val ton = if (abstand == 0) s.tonnen else s.fruehere.getOrNull(abstand - 1)
                if (ton != null && ton > 0) Standort(s.name, s.staat, s.ort, ton, s.anteil, s.gebiet) else null
            }
        }.sortedByDescending { it.tonnen }.take(6)
        val anlagen = teile.sumOf { tl -> tl.firma.anlagen.getOrElse(letzt - tl.datei.jahr0) { 0 } }
        val gebiete = teile.filter { wert(it, letzt) > 0 }.map { it.datei.gebiet }.distinct().sortedBy { if (it == "US") 0 else 1 }
        return Stand(letzt, t, vor?.let { (t / it - 1) * 100 }, reihe, anlagen, standorte, gebiete)
    }
}
