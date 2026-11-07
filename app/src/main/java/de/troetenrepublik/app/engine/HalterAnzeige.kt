package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.pow

// Rechnungen fuer die Anzeige der Halterkarte (5.96): Anteile unter der kleinsten Nachkommastelle, auf null
// gerundete Veraenderungen und die Groesse der Namensliste. Reine Funktionen, damit die Anzeige leicht zu testen ist.
object HalterAnzeige {

    // Anteil in Prozent fuer die Anzeige mit stellen Nachkommastellen. Ein Anteil ueber null, der auf null runden
    // wuerde, wird als "unter" der kleinsten Stelle gezeigt (wert = 10^-stellen, unter = true).
    data class Anteil(val wert: Double, val unter: Boolean)

    fun anteil(p: Double, stellen: Int): Anteil {
        val stufe = 10.0.pow(stellen)
        return if (p > 0 && p * stufe < 0.5) Anteil(1 / stufe, true) else Anteil(p, false)
    }

    // Veraenderung der Stueckzahl zum Vorquartal in Prozent: null ohne Angabe oder bei NaN/Unendlich; 0.0, wenn sie
    // auf eine Nachkommastelle gerundet null ist (abs < 0.05), sonst der Wert.
    fun aenderung(a: Double?): Double? =
        if (a == null || !a.isFinite()) null else if (abs(a) < 0.05) 0.0 else a

    // Art der Liste: KEINE ohne Eintrag, ZEHN bei zehn Eintraegen (die Quelle nennt hoechstens zehn), sonst TEIL.
    enum class Liste { KEINE, TEIL, ZEHN }

    fun liste(gezeigt: Int): Liste = when (gezeigt) {
        0 -> Liste.KEINE
        10 -> Liste.ZEHN
        else -> Liste.TEIL
    }
}
