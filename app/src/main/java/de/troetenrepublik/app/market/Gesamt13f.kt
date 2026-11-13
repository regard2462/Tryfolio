package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Gesamtbild aller 13F-Melder (0.77): je Wertpapier Zahl der Halter, Zu- und Abgaenge gegenueber dem Vorquartal, Bestand
// und die fuenf groessten Halter, gerechnet aus den Form 13F Data Sets der SEC (tools/sec/form13f_gesamt.py) und als
// Datei copytrading/gesamt13f.json mitgeliefert. Zu- und Abgaenge zaehlen nur Melder, die in beiden Quartalen
// gemeldet haben. Reines Lesen und Ordnen, ohne Netz.
object Gesamt13f {

    const val DATEI = "copytrading/gesamt13f.json"

    // Einer der groessten Halter: Stueckzahl, Wert in Dollar und Aenderung der Stueckzahl in Prozent (null: im Vorquartal nicht dabei).
    class Halter(val name: String, val stueck: Double, val wert: Double, val aenderung: Double?)

    class Wert(
        val kuerzel: String,
        val name: String,
        val halter: Int,
        val halterVorher: Int,
        val neu: Int,
        val weg: Int,
        val auf: Int,
        val ab: Int,
        val wert: Double,
        val stueck: Double,
        // Stueckzahl der Vergleichsgruppe jetzt und im Vorquartal (auf die heutige Stueckelung umgerechnet)
        val stueckGruppe: Double,
        val stueckVorher: Double,
        // Wert der Aenderungen der Stueckzahl in Dollar (Kauf positiv)
        val fluss: Double,
        val top: List<Halter>,
        // Neunotierung oder Umbau der Stueckzahl (Abspaltung): gehoert nicht in die Ranglisten
        val sonder: Boolean,
    ) {
        // Aufstocker und neue Halter minus Abbauer und Ausgestiegene
        val netto: Int get() = auf + neu - ab - weg

        // Aenderung der Stueckzahl in der Vergleichsgruppe in Prozent; null ohne Vorquartal
        val aenderung: Double? get() = if (stueckVorher > 0) (stueckGruppe / stueckVorher - 1) * 100 else null
    }

    class Datei(
        val stichtag: String,
        val vorher: String,
        val bis: String,
        val melder: Int,
        val melderVorher: Int,
        val gruppe: Int,
        val werte: Map<String, Wert>,
        // CUSIP zu Kuerzel der Wertpapiere dieser Datei (fuer Bestaende gesuchter Melder)
        val cusip: Map<String, String> = emptyMap(),
    )

    // Kleinste Haelterzahl fuer die Ranglisten (kleine Werte schwanken zu stark)
    private const val MIN_HALTER = 50

    private fun JsonObject.zahl(k: String): Double = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    private fun JsonObject.ganz(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.text(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    // Liest die Datei; null bei kaputtem JSON oder ohne Wertpapiere.
    fun lesen(text: String): Datei? = runCatching {
        val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
        val aktien = o["aktien"] as? JsonObject ?: return null
        val werte = LinkedHashMap<String, Wert>()
        for ((k, e) in aktien) {
            val w = e as? JsonObject ?: continue
            val top = (w["t"] as? JsonArray).orEmpty().mapNotNull { h ->
                val a = h as? JsonArray ?: return@mapNotNull null
                val name = (a.getOrNull(0) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
                Halter(
                    name, (a.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: 0.0, (a.getOrNull(2) as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                    (a.getOrNull(3) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull,
                )
            }
            werte[k] = Wert(
                k, w.text("n").ifBlank { k }, w.ganz("h"), w.ganz("hv"), w.ganz("neu"), w.ganz("weg"), w.ganz("auf"), w.ganz("ab"),
                w.zahl("w"), w.zahl("s"), w.zahl("sg"), w.zahl("sv"), w.zahl("f"), top, w.ganz("x") == 1,
            )
        }
        if (werte.isEmpty()) return null
        val cusip = (o["cusip"] as? JsonObject)?.mapNotNull { (c, k) -> (k as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { c to it } }?.toMap().orEmpty()
        Datei(o.text("stichtag"), o.text("vorher"), o.text("bis"), o.ganz("melder"), o.ganz("melderVorher"), o.ganz("gruppe"), werte, cusip)
    }.getOrNull()

    // Eingebaute oder geprueft nachgeladene Datei, einmal je Prozess gelesen.
    val aktuell: Datei? by lazy { runCatching { lesen(Daten.text(DATEI)) }.getOrNull() }

    // Wert zu einem Kuerzel der App (Aktienklassen wie BRK.B heissen hier BRK-B).
    fun fuer(d: Datei?, symbol: String): Wert? {
        d ?: return null
        val s = symbol.trim()
        return d.werte[s] ?: d.werte[s.replace(Regex("^([A-Z0-9]+)[./]([A-KM-Z])$"), "$1-$2")]
    }

    private fun ranglistenfaehig(d: Datei) = d.werte.values.filter { !it.sonder && it.halter >= MIN_HALTER }

    // Meistgekaufte Werte: die meisten Netto-Kaeufer, bei Gleichstand der groessere Wertfluss. Nur Werte, denen auch netto
    // Geld zugeflossen ist: Viele kleine Kaeufer bei einem groesseren Abfluss weniger grosser Fonds waeren sonst "meistgekauft".
    fun meistgekauft(d: Datei, anzahl: Int = 15): List<Wert> =
        ranglistenfaehig(d).filter { it.netto > 0 && it.fluss > 0 }.sortedWith(compareByDescending<Wert> { it.netto }.thenByDescending { it.fluss }).take(anzahl)

    // Meistverkaufte Werte: die meisten Netto-Verkaeufer, bei Gleichstand der groessere Wertabfluss; nur mit Abfluss.
    fun meistverkauft(d: Datei, anzahl: Int = 15): List<Wert> =
        ranglistenfaehig(d).filter { it.netto < 0 && it.fluss < 0 }.sortedWith(compareBy<Wert> { it.netto }.thenBy { it.fluss }).take(anzahl)
}
