package de.troetenrepublik.app.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// Inhalt der Widgets auf dem Startbildschirm: fertig formatiert und in der Sprache der App, damit
// das Widget nur noch Texte setzt. Mit App-Sperre steht nur hinweis da, keine Betraege.
@Serializable
data class WidgetDaten(
    val stand: String = "",
    val titelDepot: String = "",
    val titelWerte: String = "",
    // Text, der anstelle der Werte erscheint.
    val hinweis: String = "",
    // App-Sperre eingeschaltet: alle Widgets zeigen nur ein Schloss und hinweis.
    val gesperrt: Boolean = false,
    val depotWert: String = "",
    val depotAenderung: String = "",
    val depotHoch: Boolean = true,
    val werte: List<WidgetWert> = emptyList(),
    // Tagesverlauf des Depots fuer die Kurslinie (ausgeduennt); leer, solange er fehlt.
    val depotKurve: List<Double> = emptyList(),
    // Positionen im Depot, groesste zuerst: Quelle „Depot“ des Widgets „Werte“.
    val titelBestand: String = "",
    val bestand: List<WidgetWert> = emptyList(),
    val bestandHinweis: String = "",
    // Einzelwerte je Symbol, mit Tagesverlauf; einzelStandard gilt fuer Widgets ohne eigene Wahl.
    val einzel: Map<String, WidgetWert> = emptyMap(),
    val einzelStandard: String = "",
    // Gewinner und Verlierer des Tages unter den Positionen.
    val titelMover: String = "",
    val gewinner: List<WidgetWert> = emptyList(),
    val verlierer: List<WidgetWert> = emptyList(),
    val moverHinweis: String = "",
    // Dividenden: erhalten im laufenden Jahr, erwartet in zwoelf Monaten, die naechsten Zahlungen.
    val titelDividenden: String = "",
    val divErhaltenText: String = "",
    val divErhalten: String = "",
    val divErwartetText: String = "",
    val divErwartet: String = "",
    val divNaechste: List<WidgetWert> = emptyList(),
    val divHinweis: String = "",
    // Beschriftungen des Schnellzugriffs in der Reihenfolge von SCHNELL_ZIELE.
    val schnell: List<String> = emptyList(),
    // Wichtige Indizes, Gold und Bitcoin mit Tagesaenderung.
    val titelMaerkte: String = "",
    val maerkte: List<WidgetWert> = emptyList(),
    // Seit 5.4: Depot je Zeitraum (ZEIT_HEUTE, ZEIT_JAHR, ZEIT_KAUF) fuer das Depot-Widget.
    val depotZeitraeume: Map<String, WidgetZeitraum> = emptyMap(),
    // Werte fuer Widgets mit eigener Liste, je Symbol.
    val listenWerte: Map<String, WidgetWert> = emptyMap(),
    // Stimmungsbarometer je Schluessel (STIMMUNG_AKTIEN, STIMMUNG_KRYPTO).
    val titelStimmung: String = "",
    val stimmung: Map<String, WidgetStimmung> = emptyMap(),
    // Schlagzeilen zu den Positionen, neueste zuerst.
    val titelNews: String = "",
    val news: List<WidgetNachricht> = emptyList(),
    val newsHinweis: String = "",
) {
    companion object {
        // Ziele der Knoepfe im Schnellzugriff (Routen der App).
        val SCHNELL_ZIELE = listOf("search", "orders", "heatmap", "news")

        const val ZEIT_HEUTE = "heute"
        const val ZEIT_JAHR = "jahr"
        const val ZEIT_KAUF = "kauf"

        const val STIMMUNG_AKTIEN = "aktien"
        const val STIMMUNG_KRYPTO = "krypto"

        // Reihenfolge einer Liste: wie gewaehlt oder nach Tagesaenderung, staerkster Gewinner zuerst.
        fun ordnen(werte: List<WidgetWert>, nachAenderung: Boolean): List<WidgetWert> =
            if (nachAenderung) werte.sortedByDescending { it.pct } else werte
    }
}

// Depot in einem Zeitraum: Aenderung als Betrag (leer bei ausgeblendeten Betraegen) und in Prozent, dazu der Verlauf.
@Serializable
data class WidgetZeitraum(
    val titel: String,
    val betrag: String,
    val prozent: String,
    val hoch: Boolean,
    val kurve: List<Double> = emptyList(),
)

// Stimmungsbarometer: Wert 0 bis 100, Stufe als Text und als Nummer 0 (extreme Angst) bis 4 (extreme Gier).
@Serializable
data class WidgetStimmung(val titel: String, val wert: Int, val stufe: String, val stufeNr: Int)

// Schlagzeile im News-Widget; zeile nennt Wert und Alter, ziel oeffnet den Wert.
@Serializable
data class WidgetNachricht(val titel: String, val zeile: String, val ziel: String)

// Eine Zeile oder ein Einzelwert. ziel ist die Seite beim Antippen (WidgetZiel), kurve der Tagesverlauf (nur bei
// Einzelwerten).
@Serializable
data class WidgetWert(
    val name: String,
    val kurs: String,
    val aenderung: String,
    val hoch: Boolean,
    val ziel: String = "",
    val kurve: List<Double> = emptyList(),
    // Seit 5.4: Tagesaenderung als Betrag (fuer „Betrag statt Prozent“) und als Zahl (zum Sortieren).
    val betrag: String = "",
    val pct: Double = 0.0,
)

// Wohin ein Tipp auf ein Widget fuehrt: nur diese Ziele nimmt die App von aussen an.
object WidgetZiel {
    private val SYMBOL = Regex("[A-Za-z0-9.^=:_-]{1,40}")

    // Seiten, die eine Mitteilung oeffnen darf.
    val MITTEILUNG_ZIELE = setOf("rueckblick")

    fun instrument(symbol: String) = "instrument/$symbol"
    fun derivat(id: String) = "derivative/$id"

    // Gibt ziel nur zurueck, wenn es eine bekannte Seite ist.
    fun pruefen(ziel: String?): String? {
        if (ziel == null) return null
        if (ziel in WidgetDaten.SCHNELL_ZIELE || ziel in MITTEILUNG_ZIELE) return ziel
        val teil = ziel.substringAfter('/', "")
        return when {
            ziel.startsWith("instrument/") && SYMBOL.matches(teil) -> ziel
            ziel.startsWith("derivative/") && SYMBOL.matches(teil) -> ziel
            else -> null
        }
    }

    // Hoechstens max Punkte, gleichmaessig ausgewaehlt; der letzte bleibt.
    fun ausduennen(werte: List<Double>, max: Int = 80): List<Double> {
        val v = werte.filter { it.isFinite() }
        if (v.size <= max) return v
        val schritt = (v.size - 1).toDouble() / (max - 1)
        return List(max) { i -> v[(i * schritt).roundToInt().coerceAtMost(v.size - 1)] }
    }
}
