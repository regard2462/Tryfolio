package de.troetenrepublik.app.engine

// Schwellen fuer die Kurzaussage im Reiter Ausblick (5.93), als reine Funktionen testbar.
object Ausblickdeutung {
    // Richtung der Fed-Zinsen in zwoelf Monaten: -1 Senkung, 0 kaum Aenderung, 1 Erhoehung, null ohne Daten.
    fun zinsRichtung(schritte: Double?): Int? = when {
        schritte == null -> null
        schritte <= -0.75 -> -1
        schritte >= 0.75 -> 1
        else -> 0
    }
    // Stufe der Rezessionswahrscheinlichkeit: 0 unter 25 %, 1 von 25 bis unter 50 %, 2 ab 50 %, null ohne Wert.
    fun rezessionStufe(chance: Double?): Int? = when {
        chance == null || !chance.isFinite() -> null
        chance < 0.25 -> 0
        chance < 0.5 -> 1
        else -> 2
    }
}
