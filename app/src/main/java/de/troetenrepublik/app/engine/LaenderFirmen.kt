package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Groesste Unternehmen eines Landes (Globus-Laenderblatt, 0.69). Gelistete Firmen: Kandidaten aus dem Katalog und aus
// assets/globus/firmen.json (Wikidata, CC0, mit Yahoo-Kuerzel), die Reihenfolge ergibt der Boersenwert aus dem Kursabruf.
// Nicht gelistete Firmen: assets/globus/firmen-privat.json, von Hand geprueft.
object LaenderFirmen {
    // symbol leer: Yahoo kennt die Firma nicht, dann steht boerse (Name der Boerse) und es gibt weder Tippziel noch Boersenwert
    // wert, waehrung, jahr: Boersenwert aus Wikidata (Betrag in Landeswaehrung laut ISO-Code, Jahr der Angabe); kennung: Kuerzel bei der
    // Heimatboerse fuer deren Schnittstelle (Moskauer Boerse)
    data class Kandidat(
        val symbol: String, val name: String, val branche: String, val staat: Boolean, val boerse: String = "",
        val wert: Double = 0.0, val waehrung: String = "", val jahr: Int = 0, val kennung: String = "",
    ) {
        // Schluessel in der Kursliste: das Yahoo-Kuerzel, sonst der Name mit Raute
        val schluessel: String get() = symbol.ifBlank { "#$name" }
    }

    // geprueft: von Hand geprueft (nicht boersennotiert); sonst Vorschlag aus Wikidata (grosse Firma ohne Boersenplatz in Wikidata)
    data class Privat(val name: String, val branche: String, val staat: Boolean, val geprueft: Boolean = true)

    // Boersenwert in Euro und Tagesaenderung in Prozent eines Kandidaten (null: Yahoo kennt das Kuerzel nicht oder ohne Wert).
    // jahr: null = aktuell, sonst Jahr der Angabe (Wikidata)
    // kgv: Kurs-Gewinn-Verhaeltnis aus der Kursabfrage, null ohne Angabe
    data class Kurs(val boersenwert: Double, val aenderung: Double?, val jahr: Int? = null, val kgv: Double? = null)

    data class Zeile(val kandidat: Kandidat, val boersenwert: Double?, val aenderung: Double?, val jahr: Int? = null, val kgv: Double? = null)

    data class Anteil(val branche: String, val anteil: Double)

    const val ZEILEN = 10
    const val BRANCHEN = 5
    const val MIN_FIRMEN_BRANCHEN = 5

    private val json = Json { ignoreUnknownKeys = true }

    // firmen.json: {"stand":..,"l":{"IN":[["RELIANCE.NS","Reliance Industries","OEL_GAS",0], ...]}}
    fun buendel(text: String): Map<String, List<Kandidat>> = runCatching {
        json.parseToJsonElement(text).jsonObject["l"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonArray.mapNotNull { e ->
                val a = e as? JsonArray ?: return@mapNotNull null
                val s = a.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val n = a.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val w = a.getOrNull(5) as? JsonArray
                Kandidat(
                    s, n, a.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty(), a.getOrNull(3)?.jsonPrimitive?.contentOrNull == "1",
                    a.getOrNull(4)?.jsonPrimitive?.contentOrNull.orEmpty(),
                    w?.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0, w?.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty(),
                    w?.getOrNull(2)?.jsonPrimitive?.intOrNull ?: 0, a.getOrNull(6)?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }
    }.getOrDefault(emptyMap())

    // Staatsfirmen unter den Gelisteten: Liste der Kuerzel im Feld "s" von firmen.json (gilt auch fuer Katalogwerte).
    fun staatSymbole(text: String): Set<String> = runCatching {
        json.parseToJsonElement(text).jsonObject["s"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
    }.getOrDefault(emptySet())

    // firmen-privat.json: {"stand":..,"l":{"DE":[["Schwarz Gruppe","EINZELHANDEL",0,1], ...]}}, Reihenfolge = Rang, letztes Feld 1 = geprueft, 0 = Vorschlag
    fun privat(text: String): Map<String, List<Privat>> = runCatching {
        json.parseToJsonElement(text).jsonObject["l"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonArray.mapNotNull { e ->
                val a = e as? JsonArray ?: return@mapNotNull null
                val n = a.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Privat(n, a.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty(), a.getOrNull(2)?.jsonPrimitive?.contentOrNull == "1", a.getOrNull(3)?.jsonPrimitive?.contentOrNull != "0")
            }
        }
    }.getOrDefault(emptyMap())

    // Kandidaten eines Landes: erst die Katalogwerte (mit Katalogbranche), dann die Firmen des Buendels, die der Katalog nicht kennt.
    fun kandidaten(a2: String, buendel: List<Kandidat>, katalog: List<de.troetenrepublik.app.market.Catalog.Stock>, staat: Set<String> = emptySet()): List<Kandidat> {
        val aus = ArrayList<Kandidat>()
        val schon = HashSet<String>()
        for (k in katalog) if (k.c == a2 && schon.add(k.s)) aus += Kandidat(k.s, k.n, k.i, k.s in staat)
        for (b in buendel) if (schon.add(b.symbol.ifBlank { "#" + b.name })) aus += b.copy(staat = b.staat || b.symbol in staat)
        return aus
    }

    // [0.78] Notierungen eines Landes zu Firmen zusammengefuehrt (GleicheFirma.zusammenfuehren): Heimatboerse und
    // Hinterlegungsschein, Stamm- und Vorzugsaktie, Kurzname und voller Name zaehlen einmal. isin: ISIN je Kuerzel, soweit bekannt.
    private fun firmen(a2: String, mit: List<Kandidat>, kurse: Map<String, Kurs>, isin: Map<String, String>): List<GleicheFirma.Firma> =
        GleicheFirma.zusammenfuehren(mit.map {
            GleicheFirma.Notierung(it.schluessel, it.name, a2, kurse.getValue(it.schluessel).boersenwert, null, it.kennung, isin[it.symbol].orEmpty())
        })

    // Die groessten Firmen nach Boersenwert. Mehrere Notierungen derselben Firma zaehlen einmal: Es bleibt die Hauptnotierung
    // (Heimatboerse, sonst die groessere) mit ihrem Boersenwert und ihrer Tagesaenderung. Ohne jeden Kurs bleibt die
    // Reihenfolge der Kandidaten (Buendel), dann ohne Werte.
    fun rangliste(a2: String, kandidaten: List<Kandidat>, kurse: Map<String, Kurs>, n: Int = ZEILEN, isin: Map<String, String> = emptyMap()): List<Zeile> {
        val mit = kandidaten.filter { (kurse[it.schluessel]?.boersenwert ?: 0.0) > 0.0 }
        if (mit.isEmpty()) return kandidaten.take(n).map { Zeile(it, null, null) }
        val sortiert = mit.sortedByDescending { kurse.getValue(it.schluessel).boersenwert }
        val mitWert = firmen(a2, sortiert, kurse, isin).sortedByDescending { it.boersenwert ?: 0.0 }.take(n).map { f ->
            val k = kurse.getValue(sortiert[f.haupt].schluessel)
            Zeile(sortiert[f.haupt], f.boersenwert ?: k.boersenwert, k.aenderung, k.jahr, k.kgv)
        }
        // Rest der Zeilen mit Firmen ohne Kuerzel und ohne Wert (Reihenfolge des Buendels)
        val ohne = kandidaten.filter { it.symbol.isBlank() && it.schluessel !in kurse }.take(n - mitWert.size).map { Zeile(it, null, null) }
        return mitWert + ohne
    }

    // Anteil jeder Branche am Boersenwert der gelisteten Firmen des Landes, die groessten BRANCHEN. null, wenn weniger als
    // MIN_FIRMEN_BRANCHEN Firmen einen Boersenwert haben. Firmen ohne Branche zaehlen zur Gesamtsumme, erscheinen aber
    // nicht als eigene Branche.
    fun branchen(a2: String, kandidaten: List<Kandidat>, kurse: Map<String, Kurs>, isin: Map<String, String> = emptyMap()): List<Anteil>? {
        val mit = kandidaten.filter { (kurse[it.schluessel]?.boersenwert ?: 0.0) > 0.0 }
        val einzeln = firmen(a2, mit, kurse, isin).map { mit[it.haupt] }
        if (einzeln.size < MIN_FIRMEN_BRANCHEN) return null
        val summe = einzeln.sumOf { kurse.getValue(it.schluessel).boersenwert }
        if (summe <= 0.0) return null
        return einzeln.filter { it.branche.isNotBlank() }.groupBy { it.branche }
            .map { (b, l) -> Anteil(b, l.sumOf { kurse.getValue(it.schluessel).boersenwert } / summe) }
            .sortedByDescending { it.anteil }.take(BRANCHEN)
    }
}
