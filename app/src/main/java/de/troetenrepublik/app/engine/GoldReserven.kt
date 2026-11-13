package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Goldreserven der Zentralbanken je Land aus den Monatsmeldungen an den Internationalen Waehrungsfonds (IWF, International
// Reserves and Foreign Currency Liquidity, IRFCL), Goldbestand in Feinunzen. Mitgeliefert als assets/welt/gold.json
// (tools/daten/goldreserven.py). Je Land der letzte Monat mit Wert (m), die Unzen dort (u) und die Unzen zwoelf Monate davor
// (u12, falls gemeldet). Nur Laender, die melden; jedes Land hat seinen eigenen letzten Monat.
object GoldReserven {

    const val DATEI = "welt/gold.json"
    const val IWF_SEITE = "https://data.imf.org/en/datasets/IMF.STA:IRFCL"

    // Feinunzen je Tonne
    const val UNZEN_JE_TONNE = 32150.7466

    // Kleinste Aenderung in Tonnen, die als Kauf oder Verkauf zaehlt (darunter zeigt die Anzeige 0,0)
    const val MIN_AENDERUNG = 0.1

    // c: Landescode (ISO-3), m: letzter Monat (JJJJ-MM), u: Unzen im Monat m, u12: Unzen zwoelf Monate davor
    @Serializable
    class Land(val c: String, val en: String, val de: String = "", val m: String, val u: Double, val u12: Double? = null) {
        fun name(deutsch: Boolean) = if (deutsch && de.isNotEmpty()) de else en
        val tonnen: Double get() = u / UNZEN_JE_TONNE
        val tonnenVor: Double? get() = u12?.let { it / UNZEN_JE_TONNE }
    }

    @Serializable
    class Datei(val stand: String, val quelle: String = "", val laender: List<Land> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    // Datei lesen; null, wenn sie nicht passt (kein JSON, unsinniges Datum, Land ohne Code oder Monat, Bestand von 0 oder
    // darunter). Eine Datei ohne Laender ist gueltig, ihr Abschnitt bleibt unsichtbar.
    fun lesen(text: String): Datei? = runCatching {
        val d = json.decodeFromString(Datei.serializer(), text)
        if (Alter.tag(d.stand) == null) return null
        if (d.laender.any { it.c.isEmpty() || Alter.tag(it.m) == null || !(it.u > 0) || (it.u12 != null && !(it.u12 > 0)) }) return null
        d
    }.getOrNull()

    val datei: Datei? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    // Gibt es Daten zum Anzeigen?
    fun hat(d: Datei?): Boolean = d != null && d.laender.isNotEmpty()

    // Ein Land in der Rangfolge: Tonnen und Anteil an der Summe aller Laender in Prozent
    class Eintrag(val land: Land, val tonnen: Double, val anteil: Double)

    // Rangfolge absteigend nach Tonnen; summe: Tonnen aller Laender der Datei
    class Rang(val summe: Double, val eintraege: List<Eintrag>) {
        // Anteil der ersten n Laender an der Summe in Prozent
        fun spitze(n: Int): Double = eintraege.take(n).sumOf { it.anteil }

        // Tonnen der ersten n Laender
        fun spitzeTonnen(n: Int): Double = eintraege.take(n).sumOf { it.tonnen }
    }

    fun rang(d: Datei): Rang? {
        val summe = d.laender.sumOf { it.tonnen }
        if (summe <= 0) return null
        val liste = d.laender.map { Eintrag(it, it.tonnen, it.tonnen / summe * 100) }.sortedByDescending { it.tonnen }
        return Rang(summe, liste)
    }

    // Aenderung in Tonnen gegenueber zwoelf Monaten davor; Laender ohne beide Werte fehlen
    class Aenderung(val land: Land, val tonnen: Double)

    fun aenderungen(d: Datei): List<Aenderung> = d.laender.mapNotNull { l ->
        val vor = l.tonnenVor ?: return@mapNotNull null
        Aenderung(l, l.tonnen - vor)
    }

    // Groesste Kaeufer und Verkaeufer, je n; kleiner als MIN_AENDERUNG zaehlt nicht
    fun kaeufer(a: List<Aenderung>, n: Int): List<Aenderung> = a.filter { it.tonnen >= MIN_AENDERUNG }.sortedByDescending { it.tonnen }.take(n)
    fun verkaeufer(a: List<Aenderung>, n: Int): List<Aenderung> = a.filter { it.tonnen <= -MIN_AENDERUNG }.sortedBy { it.tonnen }.take(n)

    // Der Monat, in dem die meisten Laender ihren letzten Wert haben (bei Gleichstand der juengere); null ohne Laender
    fun haeufigsterMonat(d: Datei): String? =
        d.laender.groupingBy { it.m }.eachCount().entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key
}
