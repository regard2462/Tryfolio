package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.data.WidgetStimmung

// Reine Logik der Widgets: Standardwahl des Stimmungs-Widgets, Drehung des Globus und Kachelzahl des Schnellzugriffs.
object WidgetWahl {
    // Ein neues Stimmungs-Widget zeigt die Gesamtstimmung; eine gespeicherte, bekannte Wahl bleibt.
    fun stimmung(gespeichert: String?): String =
        gespeichert?.takeIf { it == WidgetDaten.STIMMUNG_ALLE || it in WidgetDaten.STIMMUNG_REIHE } ?: WidgetDaten.STIMMUNG_GESAMT

    // Einmalige Korrektur beim ersten Start von 0.77: die Fassung 0.76 hat Stimmungs-Widgets ohne gespeicherte Wahl fest auf
    // Aktien USA gestellt. Liefert die neue Wahl, die zu speichern ist, oder null, wenn alles bleibt.
    fun uebernahme077(gespeichert: String?): String? =
        if (gespeichert == WidgetDaten.STIMMUNG_AKTIEN) WidgetDaten.STIMMUNG_GESAMT else null

    // Auswahl in der Widget-Einstellung: alle Barometer in der Reihenfolge der App, zuletzt "Alle".
    val STIMMUNG_WAHL: List<String> = WidgetDaten.STIMMUNG_REIHE + WidgetDaten.STIMMUNG_ALLE

    // Hoechstens so viele Zeilen hat die Ansicht "Alle".
    const val ALLE_MAX = 9

    // Zeilen der Ansicht "Alle" bei dieser Hoehe in dp: Rand 28 dp, je Zeile 28 dp, mit Untertitel 18 dp fuer den Stand; mindestens eine.
    fun alleAnzahl(hoeheDp: Int, mitStand: Boolean): Int = ((hoeheDp - 28 - if (mitStand) 18 else 0) / 28).coerceIn(1, ALLE_MAX)

    // Die Stufe steht neben Name und Wert nur, wenn die Breite in dp reicht; sonst bleibt der Name ganz.
    fun alleMitStufe(breiteDp: Int): Boolean = breiteDp >= 250

    // Zeilen der Ansicht "Alle": vorhandene Barometer in der Reihenfolge der App (Gesamtstimmung zuerst), ein Name nur einmal,
    // so viele wie in die Hoehe passen.
    fun alleZeilen(daten: Map<String, WidgetStimmung>, hoeheDp: Int, mitStand: Boolean): List<WidgetStimmung> =
        WidgetDaten.STIMMUNG_REIHE.mapNotNull { daten[it] }.distinctBy { it.titel }.take(alleAnzahl(hoeheDp, mitStand))

    // Schritt der Globusdrehung je Tipp in Grad.
    const val DREH_SCHRITT = 30

    // Gespeicherte Drehung in Grad, auf ein Vielfaches des Schritts zwischen -150 und 180 gebracht; ohne Wert 0.
    fun dreh(gespeichert: String?): Int = normal((gespeichert?.toIntOrNull() ?: 0) / DREH_SCHRITT * DREH_SCHRITT)

    // Nach dem Tipp: richtung -1 dreht nach Westen, +1 nach Osten.
    fun weiterdrehen(aktuell: Int, richtung: Int): Int = normal(aktuell + richtung.coerceIn(-1, 1) * DREH_SCHRITT)

    // Blicklaenge in Grad aus der Mitte der Wahl und der Drehung, zwischen -180 und 180.
    fun laenge(mitte: Double, dreh: Int): Double {
        var l = (mitte + dreh) % 360.0
        if (l > 180.0) l -= 360.0
        if (l <= -180.0) l += 360.0
        return l
    }

    private fun normal(grad: Int): Int {
        var g = grad % 360
        if (g > 180) g -= 360
        if (g <= -180) g += 360
        return g
    }

    // Tippraster des Globus-Widgets: Spalten und Zeilen zwischen den Raendern (je 15 Prozent der Breite), die die Kugel drehen.
    const val GLOBUS_SPALTEN = 6
    const val GLOBUS_ZEILEN = 8
    const val GLOBUS_RAND = 0.15

    // Mitte der Zelle als Anteil an Breite und Hoehe des ganzen Widgets (x, y).
    fun zellenMitte(spalte: Int, zeile: Int): Pair<Double, Double> =
        (GLOBUS_RAND + (1 - 2 * GLOBUS_RAND) * (spalte + 0.5) / GLOBUS_SPALTEN) to ((zeile + 0.5) / GLOBUS_ZEILEN)

    // Kacheln je Zeile des Schnellzugriffs nach der Breite in dp: mindestens 60 dp je Kachel, hoechstens vier.
    fun spalten(breiteDp: Int): Int = ((breiteDp - 8) / 60).coerceIn(1, 4)

    // Zeilen, die bei dieser Hoehe in dp Platz haben (mindestens 44 dp je Zeile), hoechstens zwei; mindestens eine.
    fun zeilen(hoeheDp: Int): Int = ((hoeheDp - 12) / 44).coerceIn(1, 2)

    // Wie viele der vier Kacheln sichtbar sind.
    fun kacheln(breiteDp: Int, hoeheDp: Int): Int = minOf(4, spalten(breiteDp) * zeilen(hoeheDp))
}
