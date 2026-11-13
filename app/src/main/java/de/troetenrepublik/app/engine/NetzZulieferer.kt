package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Firmenname
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Zulieferer rueckwaerts: Firmen, die in ihrem Jahresbericht einen Grosskunden mit Namen nennen (XBRL-Angaben der SEC, Datei
// netz/zulieferer.json, Erzeuger tools/daten/zulieferer.py). Fuer eine Firma X liefert die Datei alle Zulieferer, die X als Kunden
// melden, mit Anteil am Umsatz des Zulieferers, Stichtag und Einreichung. Der Abgleich des Kundennamens ist streng: voller
// normierter Name, keine Teilwoerter, Mehrdeutiges faellt weg. Reine Rechnung, ohne Netz.
object NetzZulieferer {
    const val DATEI = "netz/zulieferer.json"

    // Eine Meldung: zulieferer (cik, name, symbol) nennt kunde mit prozent seines Umsatzes zum stichtag (Jahresbericht jahr, form,
    // Einreichung adsh)
    class Zeile(
        val cik: String, val name: String, val symbol: String, val kunde: String, val prozent: Double, val stichtag: String,
        val adsh: String, val form: String, val jahr: Int,
    )

    class Daten(val stand: String, val zeilen: List<Zeile>) {
        // Zeilen je Schluessel des Kundennamens
        val nachKunde: Map<String, List<Zeile>> by lazy { zeilen.groupBy { schluessel(it.kunde) }.filterKeys { it.isNotEmpty() } }
        val nachCik: Map<String, List<Zeile>> by lazy { zeilen.groupBy { it.cik } }
    }

    // Gleiche Firma unter verschiedenen Namen (Marke, frueherer oder Konzernname). Der erste Eintrag einer Gruppe gilt.
    private val ALIAS: List<List<String>> = listOf(
        listOf("alphabet", "google"),
        listOf("meta platforms", "facebook"),
        listOf("walmart", "wal mart", "walmart stores", "wal mart stores"),
        listOf("foxconn", "hon hai precision industry", "hon hai", "foxconn technology group", "foxconn technology"),
        listOf("samsung electronics", "samsung"),
        listOf("taiwan semiconductor manufacturing", "tsmc", "taiwan semiconductor manufacturing company"),
        listOf("at and t", "att"),
        listOf("amazon com", "amazon", "amazon web services"),
    ).map { g -> g.map { compakt(it) } }

    private val NACH_ALIAS: Map<String, String> = ALIAS.flatMap { g -> g.map { it to g.first() } }.toMap()

    private fun compakt(s: String): String = s.replace(" ", "")

    // Schluessel eines Firmennamens: voller normierter Name ohne Leerzeichen, Namensgruppen auf den ersten Namen gefuehrt; leer, wenn
    // nur ein allgemeines Wort bleibt
    fun schluessel(name: String): String {
        val k = compakt(Netz.nameVoll(name))
        if (k.isEmpty()) return ""
        return NACH_ALIAS[k] ?: k
    }

    // Schluessel einer Firma des Netzes: voller Name und der Name ohne Gattung, Zusaetze und Rechtsform
    fun schluesselVon(name: String): Set<String> = setOf(schluessel(name), schluessel(Firmenname.bereinigt(name))).filter { it.isNotEmpty() }.toSet()

    private val json = Json { ignoreUnknownKeys = true }
    private val CIK = Regex("^[0-9]{1,10}$")
    private val TAG = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val ADSH = Regex("^[0-9]{10}-[0-9]{2}-[0-9]{6}$")

    private fun JsonArray.text(i: Int): String? = (getOrNull(i) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonArray.zahl(i: Int): Double? = (getOrNull(i) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonArray.ganz(i: Int): Int? = (getOrNull(i) as? JsonPrimitive)?.let { p -> if (p.isString) p.contentOrNull?.toIntOrNull() else p.intOrNull }

    // Zeile: [cik, name, symbol, kunde, prozent, stichtag, adsh, form, jahr]. Fehlerhafte Zeilen fallen weg; null bei kaputtem JSON
    // oder ohne eine brauchbare Zeile.
    fun lesen(text: String): Daten? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val zeilen = (o["zeilen"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            val cik = a.text(0)?.takeIf { CIK.matches(it) }?.trimStart('0')?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = a.text(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Kunden, deren Name nach dem Normieren leer ist (nur allgemeine Woerter), lassen sich keiner Firma zuordnen
            val kunde = a.text(3)?.takeIf { it.isNotBlank() && schluessel(it).isNotEmpty() } ?: return@mapNotNull null
            val prozent = a.zahl(4)?.takeIf { it > 0.0 && it <= 100.0 } ?: return@mapNotNull null
            val tag = a.text(5)?.takeIf { TAG.matches(it) } ?: return@mapNotNull null
            val adsh = a.text(6)?.takeIf { ADSH.matches(it) } ?: return@mapNotNull null
            val form = a.text(7)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val jahr = a.ganz(8) ?: tag.take(4).toInt()
            Zeile(cik, name, a.text(2).orEmpty(), kunde, prozent, tag, adsh, form, jahr)
        }
        if (zeilen.isEmpty()) return null
        Daten(o["stand"].let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(), zeilen)
    }.getOrNull()

    // Eingebaute oder geprueft nachgeladene Datei, einmal je Prozess gelesen
    val eingebaut: Daten? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }
}
