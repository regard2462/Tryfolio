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
    // Seit 5.94: Beschriftung je Ziel aus SCHNELL_AUSWAHL, die Kacheln sind je Widget waehlbar.
    val schnellNamen: Map<String, String> = emptyMap(),
    // Wichtige Indizes, Gold und Bitcoin mit Tagesaenderung.
    val titelMaerkte: String = "",
    val maerkte: List<WidgetWert> = emptyList(),
    // Seit 5.4: Depot je Zeitraum (ZEIT_HEUTE, ZEIT_JAHR, ZEIT_KAUF) fuer das Depot-Widget.
    val depotZeitraeume: Map<String, WidgetZeitraum> = emptyMap(),
    // Werte fuer Widgets mit eigener Liste, je Symbol.
    val listenWerte: Map<String, WidgetWert> = emptyMap(),
    // Stimmungsbarometer je Schluessel (STIMMUNG_AKTIEN, STIMMUNG_KRYPTO, seit 5.95 STIMMUNG_GESAMT).
    val titelStimmung: String = "",
    val stimmung: Map<String, WidgetStimmung> = emptyMap(),
    // Schlagzeilen zu den Positionen, neueste zuerst.
    val titelNews: String = "",
    val news: List<WidgetNachricht> = emptyList(),
    val newsHinweis: String = "",
    // Seit 5.94: die wichtigsten Schlagzeilen aller Quellen (wie der Reiter „Wichtigste“), neueste zuerst.
    val titelNewsWichtig: String = "",
    val newsWichtig: List<WidgetNachricht> = emptyList(),
    val newsWichtigHinweis: String = "",
    // Seit 0.68: alle Schlagzeilen (NEWS_ALLE) und je eigenem Feed (newsFeed(kennung)), neueste zuerst, mit Titel je
    // Inhalt; newsListenLaedt, solange eine gewaehlte Liste noch fehlt, newsListenKeine bei leerer Liste.
    val newsListen: Map<String, List<WidgetNachricht>> = emptyMap(),
    val newsListenTitel: Map<String, String> = emptyMap(),
    val newsListenLaedt: String = "",
    val newsListenKeine: String = "",
    // Seit 0.68: Heatmaps je Auswahl (Schluessel heatmapSchluessel), Kacheln wie in der App; heatmapHinweis, solange eine fehlt.
    val heatmaps: Map<String, WidgetHeatmap> = emptyMap(),
    val heatmapHinweis: String = "",
    // Seit 0.69: Werte fuer das Widget „Globus“ je Einfaerbung (GlobusModus.name), darin je Land (alpha-3) der Wert in Prozent.
    val globus: Map<String, Map<String, Double>> = emptyMap(),
    val globusTitel: Map<String, String> = emptyMap(),
) {
    companion object {
        // Ziele der Knoepfe im Schnellzugriff (Routen der App).
        val SCHNELL_ZIELE = listOf("search", "orders", "maerkte", "news")

        // Alle Ziele, aus denen der Schnellzugriff vier waehlen kann (5.94).
        val SCHNELL_AUSWAHL = SCHNELL_ZIELE + listOf("heatmap", "activity", "plans", "rueckblick", "depotverlauf")

        // Inhalt des News-Widgets: Schlagzeilen zu den Positionen oder nur die wichtigsten (5.94).
        const val NEWS_DEPOT = "depot"
        const val NEWS_WICHTIG = "wichtig"
        // Seit 0.68: alle Schlagzeilen und ein eigener Feed (Praefix plus Kennung des Feeds)
        const val NEWS_ALLE = "alle"
        const val NEWS_FEED = "feed:"
        fun newsFeed(kennung: String): String = NEWS_FEED + kennung

        const val ZEIT_HEUTE = "heute"
        const val ZEIT_JAHR = "jahr"
        const val ZEIT_KAUF = "kauf"

        const val STIMMUNG_AKTIEN = "aktien"
        const val STIMMUNG_KRYPTO = "krypto"
        // Gesamtstimmung aus allen Barometern (5.95)
        const val STIMMUNG_GESAMT = "gesamt"
        // Alle Barometer untereinander (0.69)
        const val STIMMUNG_ALLE = "alle"
        val STIMMUNG_REIHE = listOf(STIMMUNG_GESAMT, STIMMUNG_AKTIEN, STIMMUNG_KRYPTO)

        // Reihenfolge einer Liste: wie gewaehlt oder nach Tagesaenderung, staerkster Gewinner zuerst.
        fun ordnen(werte: List<WidgetWert>, nachAenderung: Boolean): List<WidgetWert> =
            if (nachAenderung) werte.sortedByDescending { it.pct } else werte

        // Schluessel einer Heatmap im Widget: Quelle (HeatmapQuelle oder Index) und Zeitraum (Index in HEATMAP_ZEITRAEUME).
        fun heatmapSchluessel(quelle: String, zeitraum: Int): String = "$quelle|$zeitraum"
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
data class WidgetStimmung(val titel: String, val wert: Int, val stufe: String, val stufeNr: Int, val quelle: String = "")

// Schlagzeile im News-Widget; zeile nennt Wert und Alter, ziel oeffnet den Wert.
@Serializable
data class WidgetNachricht(val titel: String, val zeile: String, val ziel: String)

// Heatmap im Widget (0.68): Titel, Kacheln in der Reihenfolge der App, voll = Aenderung fuer volle Farbe, ziel oeffnet
// die Heatmap mit derselben Auswahl; hinweis steht da, wenn keine Kachel da ist.
@Serializable
data class WidgetHeatmap(val titel: String, val kacheln: List<WidgetKachel>, val voll: Double, val ziel: String = "", val hinweis: String = "")

// Kachel der Heatmap: Kuerzel, Anteil an der Flaeche, Aenderung in Prozent (null ohne Kurs), fertig formatiert mit zwei
// und mit einer Nachkommastelle.
@Serializable
data class WidgetKachel(val kuerzel: String, val gewicht: Double, val aenderung: Double? = null, val prozent: String = "", val prozentKurz: String = "")

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
    val MITTEILUNG_ZIELE = setOf("rueckblick", "aktienfinder")

    // Ziele aelterer Widgets, die erst beim naechsten Auffrischen die neuen bekommen.
    val ALTE_ZIELE = setOf("heatmap")

    // Seiten der Widgets „Stimmung“ (Reiter Stimmung der Märkte) und „Globus“ (Reiter Globus der Märkte), seit 0.69.
    const val STIMMUNG = "marktphase"
    const val GLOBUS = "globus"

    fun instrument(symbol: String) = "instrument/$symbol"

    // Heatmap mit Quelle und Zeitraum (0.68), Ziel des Heatmap-Widgets.
    fun heatmap(quelle: String, zeitraum: Int) = "heatmap/$quelle/$zeitraum"
    private val HEATMAP = Regex("[A-Z0-9]{1,12}/[0-5]")

    // Eine Schlagzeile der Nachrichtenseite (5.94): Kennung aus dem Link, damit das Ziel kurz und harmlos bleibt.
    fun nachrichtKennung(id: String): String =
        id.fold(1125899906842597L) { h, c -> 31 * h + c.code }.toULong().toString(36)
    fun nachricht(id: String) = "nachricht/" + nachrichtKennung(id)
    private val KENNUNG = Regex("[0-9a-z]{1,14}")
    fun derivat(id: String) = "derivative/$id"

    // Gibt ziel nur zurueck, wenn es eine bekannte Seite ist.
    fun pruefen(ziel: String?): String? {
        if (ziel == null) return null
        if (ziel in WidgetDaten.SCHNELL_AUSWAHL || ziel in MITTEILUNG_ZIELE || ziel in ALTE_ZIELE || ziel == "news" || ziel == STIMMUNG || ziel == GLOBUS) return ziel
        val teil = ziel.substringAfter('/', "")
        return when {
            ziel.startsWith("instrument/") && SYMBOL.matches(teil) -> ziel
            ziel.startsWith("derivative/") && SYMBOL.matches(teil) -> ziel
            ziel.startsWith("nachricht/") && KENNUNG.matches(teil) -> ziel
            ziel.startsWith("heatmap/") && HEATMAP.matches(teil) -> ziel
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
