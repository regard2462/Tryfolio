package de.troetenrepublik.app.engine

// Was die Derivate-Karte zeigt: Balken bei Optionsumsatz, sonst nur die antippbare Zeile; waehrend des
// Ladens ein Platzhalter nur bei Werten mit eigenem US-Optionskuerzel (5.96).
object DerivateAnzeige {
    enum class Zeigen { NUR_ZEILE, PLATZHALTER, BALKEN }

    // geladen: Ergebnis liegt vor; mitDaten: Optionsumsatz ueber null; erwartet: der Wert hat direkt ein US-Optionskuerzel
    fun zeigen(geladen: Boolean, mitDaten: Boolean, erwartet: Boolean): Zeigen = when {
        mitDaten -> Zeigen.BALKEN
        geladen -> Zeigen.NUR_ZEILE
        erwartet -> Zeigen.PLATZHALTER
        else -> Zeigen.NUR_ZEILE
    }
}
